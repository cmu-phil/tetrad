package edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag;

import edu.cmu.tetrad.algcomparison.algorithm.*;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.AcceptsKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesScoreWrapper;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.PermutationSearch;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.search.utils.MeekRules;
import edu.cmu.tetrad.search.utils.TsUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.*;

/**
 * BOSS + score-based CD-NOD context augmentation.
 *
 * Context variables are Knowledge Tier-0. We forbid edges into contexts and then
 * add Context -> Y edges when they improve the local score of Y given its current parents.
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "CD-NOD-BOSS",
        command = "cd-nod-boss",
        algoType = AlgType.forbid_latent_common_causes
)
@Bootstrapping
public class BossCdnodScore extends AbstractBootstrapAlgorithm implements Algorithm, TakesScoreWrapper, AcceptsKnowledge,
        ReturnsBootstrapGraphs, TakesCovarianceMatrix, LatentStructureAlgorithm {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * The score field represents an instance of {@link ScoreWrapper} used to evaluate or
     * measure the performance or accuracy of specific processes in the containing class.
     *
     * This field encapsulates functionality for calculating scores, describing the scoring
     * process, and interacting with the data models and parameters required for scoring.
     * It plays a critical role in driving the behavior of the associated algorithms or methods.
     */
    private ScoreWrapper score;

    /**
     * Represents the knowledge configuration for the BossCdnodScore class.
     *
     * This field holds an instance of the {@code Knowledge} class, which is
     * typically used to define constraints, background information, or domain
     * expertise relevant to the operations performed by the containing class.
     *
     * The {@code Knowledge} object may be utilized during the processing of
     * graphs or algorithms, particularly to enforce specific structural or
     * causal constraints on the data model. Its state can be set or modified
     * via relevant methods in the containing class.
     */
    private Knowledge knowledge = new Knowledge();

    /**
     * Default constructor for the BossCdnodScore class.
     *
     * This constructor initializes an instance of the BossCdnodScore class with
     * no underlying score or knowledge configuration. It serves as a base
     * configuration that can be further populated or modified using additional
     * methods or by setting specific parameters.
     */
    public BossCdnodScore() { }

    /**
     * Constructs a BossCdnodScore instance with a specified ScoreWrapper.
     *
     * @param score The ScoreWrapper instance to be used by this BossCdnodScore.
     *              This score is used to evaluate or measure the performance
     *              or accuracy of certain operations in the containing class.
     */
    public BossCdnodScore(ScoreWrapper score) {
        this.score = score;
    }

    @Override
    protected Graph runSearch(DataModel dataModel, Parameters parameters) {
        long seed = parameters.getLong(Params.SEED);
        parameters.set(Params.NUM_THREADS, 4);

        if (parameters.getInt(Params.TIME_LAG) > 0) {
            if (!(dataModel instanceof DataSet dataSet)) {
                throw new IllegalArgumentException("Expecting a dataset for time lagging.");
            }
            DataSet timeSeries = TsUtils.createLagData(dataSet, parameters.getInt(Params.TIME_LAG));
            if (dataModel.getName() != null) timeSeries.setName(dataModel.getName());
            dataModel = timeSeries;
            knowledge = timeSeries.getKnowledge();
        }

        // Score
        Score myScore = this.score.getScore(dataModel, parameters);

        // Resolve contexts from Knowledge tier 0.
        List<Node> vars = (dataModel instanceof DataSet ds) ? ds.getVariables() : myScore.getVariables();
        Set<Node> contexts = resolveTier0Contexts(vars, this.knowledge);

        // Build knowledge that forbids X -> C (contexts exogenous).
        Knowledge kForBoss = new Knowledge(this.knowledge);
        applyNoIncomingToContexts(kForBoss, contexts, vars);

        // Run BOSS via PermutationSearch (same as Boss.java).
        edu.cmu.tetrad.search.Boss boss = new edu.cmu.tetrad.search.Boss(myScore);
        boss.setUseBes(parameters.getBoolean(Params.USE_BES));
        boss.setNumStarts(parameters.getInt(Params.NUM_STARTS));
        boss.setNumThreads(parameters.getInt(Params.NUM_THREADS));
        boss.setUseDataOrder(parameters.getBoolean(Params.USE_DATA_ORDER));
        boss.setVerbose(parameters.getBoolean(Params.VERBOSE));

        PermutationSearch permutationSearch = new PermutationSearch(boss);
        permutationSearch.setKnowledge(kForBoss);
        permutationSearch.setSeed(seed);
        permutationSearch.setReplicatingGraph(parameters.getBoolean(Params.TIME_LAG_REPLICATING_GRAPH));

        final boolean outputPdag = parameters.getBoolean(Params.OUTPUT_PDAG);

        Graph g;
        try {
            g = permutationSearch.search(false); // force DAG output for augmentation
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        // ---- Score-based CD-NOD augmentation: add context -> Y when it helps local score.
        boolean doAugment = parameters.getBoolean("cdnodScoreContexts", true); // you can replace with Params.*
        if (doAugment && !contexts.isEmpty()) {
            double margin = parameters.getDouble("cdnodContextMargin", 0.1);
            int maxPerNode = parameters.getInt("cdnodMaxContextsPerNode", 1);
            boolean greedy = parameters.getBoolean("cdnodGreedyContexts", true);

            augmentWithContextsByLocalScore(g, myScore, contexts, margin, maxPerNode, greedy);
        }

        // Enforce no edges into contexts (defensive cleanup).
        enforceNoIncomingToContexts(g, contexts);

        // Convert to PDAG if requested.
        if (outputPdag) {
            MeekRules rules = new MeekRules();
            rules.setRevertToUnshieldedColliders(true);
            if (knowledge != null) rules.setKnowledge(knowledge);
            rules.setVerbose(false);
            rules.orientImplied(g);
        }

        LogUtilsSearch.stampWithScore(g, boss.getScore());
        LogUtilsSearch.stampWithBic(g, dataModel);
        return g;
    }

    // ---------------- helpers ----------------

    private static Set<Node> resolveTier0Contexts(List<Node> vars, Knowledge knowledge) {
        if (knowledge == null) return Collections.emptySet();
        Set<Node> out = new LinkedHashSet<>();
        try {
            List<String> tier0 = knowledge.getTier(0);
            if (tier0 == null) return Collections.emptySet();
            Map<String, Node> byName = new HashMap<>();
            for (Node v : vars) byName.put(v.getName(), v);
            for (String name : tier0) {
                Node v = byName.get(name);
                if (v != null) out.add(v);
            }
        } catch (Throwable ignored) { }
        return out;
    }

    private static void applyNoIncomingToContexts(Knowledge k, Set<Node> contexts, List<Node> vars) {
        if (k == null || contexts.isEmpty()) return;

        Set<String> ctxNames = new HashSet<>();
        for (Node c : contexts) ctxNames.add(c.getName());

        for (Node x : vars) {
            String from = x.getName();
            if (ctxNames.contains(from)) continue;
            for (String cName : ctxNames) {
                // forbid X -> C
                try { k.setForbidden(from, cName); } catch (Throwable ignored) { }
            }
        }
    }

    private static void enforceNoIncomingToContexts(Graph g, Set<Node> contexts) {
        if (contexts.isEmpty()) return;
        Set<Node> ctx = new HashSet<>(contexts);

        for (Node c : contexts) {
            for (Node adj : new ArrayList<>(g.getAdjacentNodes(c))) {
                if (g.isDirectedFromTo(adj, c)) {
                    g.removeEdge(g.getEdge(adj, c));
                }
                // if undirected, remove; contexts should be exogenous
                Edge e = g.getEdge(adj, c);
                if (e != null && !Edges.isDirectedEdge(e)) {
                    g.removeEdge(e);
                }
            }
        }
    }

    private static void augmentWithContextsByLocalScore(
            Graph dag,
            Score score,
            Set<Node> contexts,
            double margin,
            int maxPerNode,
            boolean greedy
    ) {
        // We need stable node indexing for Score.localScore(i, parents...)
        List<Node> vars = score.getVariables();
        Map<Node, Integer> index = new HashMap<>();
        for (int i = 0; i < vars.size(); i++) index.put(vars.get(i), i);

        Set<Node> ctx = new HashSet<>(contexts);

        for (Node y : dag.getNodes()) {
            if (ctx.contains(y)) continue;

            Integer yi = index.get(y);
            if (yi == null) continue;

            // get current parents (excluding contexts? I'd keep them; they matter in conditioning)
            List<Node> paNodes = dag.getParents(y);
            List<Integer> pa = new ArrayList<>();
            for (Node p : paNodes) {
                Integer pi = index.get(p);
                if (pi != null) pa.add(pi);
            }
            int[] baseParents = pa.stream().mapToInt(Integer::intValue).toArray();
            double baseScore = score.localScore(yi, baseParents);

            // candidate contexts not already parents
            List<Node> candidates = new ArrayList<>();
            for (Node c : contexts) {
                if (!dag.isAdjacentTo(c, y) && !dag.isParentOf(c, y)) {
                    candidates.add(c);
                } else if (!dag.isParentOf(c, y) && dag.isAdjacentTo(c, y)) {
                    // if adjacent but not oriented, we still treat as candidate
                    candidates.add(c);
                }
            }

            int added = 0;

            while (added < maxPerNode) {
                Node bestC = null;
                double bestDelta = 0.0;

                for (Node c : candidates) {
                    if (dag.isParentOf(c, y)) continue;

                    Integer ci = index.get(c);
                    if (ci == null) continue;

                    int[] trialParents = append(baseParents, ci);
                    double trialScore = score.localScore(yi, trialParents);
                    double delta = trialScore - baseScore;

                    if (delta > bestDelta) {
                        bestDelta = delta;
                        bestC = c;
                    }
                }

                if (bestC == null || bestDelta <= margin) break;

                // add C -> Y
                dag.removeEdges(bestC, y);
                dag.addDirectedEdge(bestC, y);

                // update base
                Integer ci = index.get(bestC);
                baseParents = append(baseParents, ci);
                baseScore += bestDelta;
                added++;

                if (!greedy) break;
            }
        }
    }

    private static int[] append(int[] arr, int x) {
        int[] out = Arrays.copyOf(arr, arr.length + 1);
        out[arr.length] = x;
        return out;
    }

    // ---------------- standard Algorithm plumbing ----------------

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new EdgeListGraph(graph);
    }

    @Override
    public String getDescription() {
        return "BOSS + score-based CD-NOD context augmentation using " + this.score.getDescription();
    }

    @Override
    public DataType getDataType() {
        return this.score.getDataType();
    }

    @Override
    public List<String> getParameters() {
        ArrayList<String> params = new ArrayList<>();
        params.add(Params.USE_BES);
        params.add(Params.NUM_STARTS);
        params.add(Params.TIME_LAG);
        params.add(Params.TIME_LAG_REPLICATING_GRAPH);
        params.add(Params.NUM_THREADS);
        params.add(Params.USE_DATA_ORDER);
        params.add(Params.OUTPUT_PDAG);
        params.add(Params.SEED);
        params.add(Params.VERBOSE);

        // New knobs (string keys for now; promote to Params.* later)
        params.add("cdnodScoreContexts");
        params.add("cdnodContextMargin");
        params.add("cdnodMaxContextsPerNode");
        params.add("cdnodGreedyContexts");

        return params;
    }

    @Override
    public ScoreWrapper getScoreWrapper() {
        return this.score;
    }

    @Override
    public void setScoreWrapper(ScoreWrapper score) {
        this.score = score;
    }

    @Override
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = knowledge;
    }
}