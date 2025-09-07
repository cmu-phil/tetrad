package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.score.BdeuScore;
import edu.cmu.tetrad.search.score.GraphScore;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.search.utils.*;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.math3.util.FastMath;

import java.util.*;

/**
 * SVAR-*GFCI* variant that uses BOSS to obtain the CPDAG (BOSS-FCI),
 * then runs the FCI orientation phase on a SvarEdgeListGraph so that
 * all edge mutations mirror across time lags automatically.
 *
 * - Stage 1 (score): BOSS (PermutationSearch over Boss(score))
 * - Stage 2 (test):  R0 + FCI orientation rules (test-based)
 * - SVAR: all orientations/removals/additions happen on SvarEdgeListGraph
 *
 * Background knowledge (including temporal tiers) is respected.
 */
public final class SvarBossFci implements IGraphSearch {

    /* ---------- Inputs ---------- */

    private final IndependenceTest independenceTest;
    private Score score;
    private Knowledge knowledge = new Knowledge();
    private boolean completeRuleSetUsed = false;
    private int maxDiscriminatingPathLength = -1;
    private boolean verbose = false;
    private boolean resolveAlmostCyclicPaths = false;

    /* BOSS params */
    private int numStarts = 1;
    private boolean bossUseBes = false;
    private int numThreads = 1;

    /* ---------- Internals ---------- */

    private Graph graph;                 // working PAG (SvarEdgeListGraph after CPDAG)
    private SepsetProducer sepsets;      // MinP sepsets for test-based orientations
    private ICovarianceMatrix covarianceMatrix;

    public SvarBossFci(IndependenceTest test, Score score) {
        if (test == null) throw new NullPointerException("IndependenceTest is null");
        if (score == null) throw new NullPointerException("Score is null");
        this.independenceTest = test;
        this.score = score;
    }

    @Override
    public Graph search() throws InterruptedException {
        independenceTest.setVerbose(verbose);

        if (verbose) {
            TetradLogger.getInstance().log("Starting SVAR BOSS-FCI.");
            TetradLogger.getInstance().log("Independence test = " + this.independenceTest + ".");
        }

        if (this.score == null) {
            chooseScore(); // fallback if constructed with null score in future use
        }

        // -------- Stage 1: BOSS to get CPDAG --------
        if (verbose) TetradLogger.getInstance().log("Running BOSS (permutation search)...");
        Boss boss = new Boss(this.score);
        boss.setUseBes(bossUseBes);
        boss.setNumStarts(numStarts);
        boss.setNumThreads(numThreads);
        boss.setVerbose(verbose);

        PermutationSearch perm = new PermutationSearch(boss);
        perm.setKnowledge(this.knowledge);
        Graph cpdag = perm.search();

        if (verbose) TetradLogger.getInstance().log("BOSS complete.");

        // -------- Switch to SVAR-mirroring graph --------
        // Copy CPDAG into a SvarEdgeListGraph so all future mutations are mirrored across lags.
        this.graph = new SvarEdgeListGraph(cpdag);
        Graph bossGraph = new EdgeListGraph(cpdag); // frozen view for def-collider checks below

        // -------- Build Min-P sepsets from the BOSS graph --------
        int maxIndegree = -1;
        this.sepsets = new SepsetsMinP(bossGraph, this.independenceTest, maxIndegree);

        // -------- R0 bootstrap (same spirit as SvarGfci) --------
        modifiedR0(bossGraph);

        // -------- FCI orientation (test-based), using SVAR endpoint strategy --------
        R0R4StrategyTestBased strategy = (R0R4StrategyTestBased)
                R0R4StrategyTestBased.specialConfiguration(independenceTest, knowledge, verbose);
        strategy.setDepth(-1);
        strategy.setMaxLength(-1);

        FciOrient fciOrient = new FciOrient(strategy);
        fciOrient.setKnowledge(this.knowledge);
        fciOrient.setEndpointStrategy(new SvarSetEndpointStrategy(this.independenceTest, this.knowledge));
        fciOrient.setCompleteRuleSetUsed(this.completeRuleSetUsed);
        fciOrient.setMaxDiscriminatingPathLength(this.maxDiscriminatingPathLength);
        fciOrient.setVerbose(this.verbose);

        fciOrient.finalOrientation(this.graph);

        // Optional cleanup for almost cyclic paths
        if (resolveAlmostCyclicPaths) {
            for (Edge edge : new ArrayList<>(graph.getEdges())) {
                if (Edges.isBidirectedEdge(edge)) {
                    Node x = edge.getNode1();
                    Node y = edge.getNode2();
                    if (graph.paths().existsDirectedPath(x, y)) {
                        graph.setEndpoint(y, x, Endpoint.TAIL);
                    } else if (graph.paths().existsDirectedPath(y, x)) {
                        graph.setEndpoint(x, y, Endpoint.TAIL);
                    }
                }
            }
        }

        GraphUtils.replaceNodes(this.graph, this.independenceTest.getVariables());

        if (verbose) TetradLogger.getInstance().log("Finished SVAR BOSS-FCI.");
        return this.graph;
    }

    /* ---------- Public knobs (mirrors SvarGfci/BossFci) ---------- */

    public void setKnowledge(Knowledge knowledge) {
        if (knowledge == null) throw new NullPointerException();
        this.knowledge = knowledge;
    }

    public void setCompleteRuleSetUsed(boolean completeRuleSetUsed) {
        this.completeRuleSetUsed = completeRuleSetUsed;
    }

    public void setMaxDiscriminatingPathLength(int maxDiscriminatingPathLength) {
        if (maxDiscriminatingPathLength < -1) {
            throw new IllegalArgumentException("Max path length must be -1 or >= 0: " + maxDiscriminatingPathLength);
        }
        this.maxDiscriminatingPathLength = maxDiscriminatingPathLength;
    }

    public void setVerbose(boolean verbose) { this.verbose = verbose; }

    public void setResolveAlmostCyclicPaths(boolean resolveAlmostCyclicPaths) {
        this.resolveAlmostCyclicPaths = resolveAlmostCyclicPaths;
    }

    public void setNumStarts(int numStarts) { this.numStarts = numStarts; }

    public void setBossUseBes(boolean useBes) { this.bossUseBes = useBes; }

    public void setNumThreads(int numThreads) {
        if (numThreads < 1) throw new IllegalArgumentException("numThreads must be >= 1");
        this.numThreads = numThreads;
    }

    /* ---------- Helpers ---------- */

    private void chooseScore() {
        double penaltyDiscount = 2.0;

        DataSet dataSet = (DataSet) this.independenceTest.getData();
        ICovarianceMatrix cov = this.independenceTest.getCov();

        if (this.independenceTest instanceof MsepTest msep) {
            this.score = new GraphScore(msep.getGraph());
            return;
        }
        if (cov != null) {
            this.covarianceMatrix = cov;
            SemBicScore s = new SemBicScore(cov);
            s.setPenaltyDiscount(penaltyDiscount);
            this.score = s;
            return;
        }
        if (dataSet.isContinuous()) {
            this.covarianceMatrix = new CovarianceMatrix(dataSet);
            SemBicScore s = new SemBicScore(this.covarianceMatrix);
            s.setPenaltyDiscount(penaltyDiscount);
            this.score = s;
            return;
        }
        if (dataSet.isDiscrete()) {
            BdeuScore s = new BdeuScore(dataSet);
            s.setSamplePrior(10.0);
            s.setStructurePrior(1.0);
            this.score = s;
            return;
        }
        throw new IllegalArgumentException("Mixed data not supported.");
    }

    /**
     * Bootstrap orientations (R0-style) using BK and Min-P structure, matching the pattern used in SvarGfci.
     * All setEndpoint calls operate on {@link SvarEdgeListGraph}, so orientations mirror across lags.
     */
    private void modifiedR0(Graph bossGraph) throws InterruptedException {
        this.graph.reorientAllWith(Endpoint.CIRCLE);
        fciOrientbk(this.knowledge, this.graph, this.graph.getNodes());

        List<Node> nodes = this.graph.getNodes();
        for (Node b : nodes) {
            List<Node> adj = new ArrayList<>(this.graph.getAdjacentNodes(b));
            if (adj.size() < 2) continue;

            ChoiceGenerator cg = new ChoiceGenerator(adj.size(), 2);
            int[] comb;
            while ((comb = cg.next()) != null) {
                Node a = adj.get(comb[0]);
                Node c = adj.get(comb[1]);

                if (bossGraph.isDefCollider(a, b, c)) {
                    this.graph.setEndpoint(a, b, Endpoint.ARROW);
                    this.graph.setEndpoint(c, b, Endpoint.ARROW);
                    orientSimilarPairs(this.graph, this.knowledge, a, b);
                    orientSimilarPairs(this.graph, this.knowledge, c, b);

                } else if (bossGraph.isAdjacentTo(a, c) && !this.graph.isAdjacentTo(a, c)) {
                    Set<Node> sepset = this.sepsets.getSepset(a, c, -1, null);
                    if (sepset != null && !sepset.contains(b)) {
                        this.graph.setEndpoint(a, b, Endpoint.ARROW);
                        this.graph.setEndpoint(c, b, Endpoint.ARROW);
                        orientSimilarPairs(this.graph, this.knowledge, a, b);
                        orientSimilarPairs(this.graph, this.knowledge, c, b);
                    }
                }
            }
        }
    }

    private void fciOrientbk(Knowledge knowledge, Graph graph, List<Node> variables) {
        if (verbose) TetradLogger.getInstance().log("Starting BK Orientation.");
        for (Iterator<KnowledgeEdge> it = knowledge.forbiddenEdgesIterator(); it.hasNext(); ) {
            KnowledgeEdge edge = it.next();
            Node from = GraphSearchUtils.translate(edge.getFrom(), variables);
            Node to = GraphSearchUtils.translate(edge.getTo(), variables);
            if (from == null || to == null) continue;
            if (graph.getEdge(from, to) == null) continue;

            // Orient to*->from
            graph.setEndpoint(to, from, Endpoint.ARROW);
            graph.setEndpoint(from, to, Endpoint.CIRCLE);

            if (verbose) TetradLogger.getInstance().log(
                    LogUtilsSearch.edgeOrientedMsg("Knowledge", graph.getEdge(from, to)));
        }

        for (Iterator<KnowledgeEdge> it = knowledge.requiredEdgesIterator(); it.hasNext(); ) {
            KnowledgeEdge edge = it.next();
            Node from = GraphSearchUtils.translate(edge.getFrom(), variables);
            Node to = GraphSearchUtils.translate(edge.getTo(), variables);
            if (from == null || to == null) continue;
            if (graph.getEdge(from, to) == null) continue;

            graph.setEndpoint(to, from, Endpoint.TAIL);
            graph.setEndpoint(from, to, Endpoint.ARROW);

            if (verbose) TetradLogger.getInstance().log(
                    LogUtilsSearch.edgeOrientedMsg("Knowledge", graph.getEdge(from, to)));
        }

        if (verbose) TetradLogger.getInstance().log("Finishing BK Orientation.");
    }

    private String getNameNoLag(Object obj) {
        String s = obj.toString();
        int i = s.indexOf(':');
        return (i == -1) ? s : s.substring(0, i);
    }

    private void removeSimilarEdges(Node x, Node y) {
        List<List<Node>> sim = returnSimilarPairs(x, y);
        if (sim.isEmpty()) return;
        List<Node> xs = sim.get(0);
        List<Node> ys = sim.get(1);
        Iterator<Node> itx = xs.iterator();
        Iterator<Node> ity = ys.iterator();
        while (itx.hasNext() && ity.hasNext()) {
            Node x1 = itx.next();
            Node y1 = ity.next();
            Edge e = this.graph.getEdge(x1, y1);
            if (e != null) this.graph.removeEdge(e);
        }
    }

    private void orientSimilarPairs(Graph graph, Knowledge knowledge, Node x, Node y) {
        if ("time".equals(x.getName()) || "time".equals(y.getName())) return;

        int ntiers = knowledge.getNumTiers();
        int tx = knowledge.isInWhichTier(x);
        int ty = knowledge.isInWhichTier(y);
        int diff = FastMath.max(tx, ty) - FastMath.min(tx, ty);

        List<String> tierX = knowledge.getTier(tx);
        List<String> tierY = knowledge.getTier(ty);

        int ix = -1, iy = -1;
        for (int i = 0; i < tierX.size(); ++i) {
            if (getNameNoLag(x.getName()).equals(getNameNoLag(tierX.get(i)))) { ix = i; break; }
        }
        for (int i = 0; i < tierY.size(); ++i) {
            if (getNameNoLag(y.getName()).equals(getNameNoLag(tierY.get(i)))) { iy = i; break; }
        }

        for (int i = 0; i < ntiers - diff; ++i) {
            if (knowledge.getTier(i).size() == 1) continue;

            List<String> t1 = (tx >= ty) ? knowledge.getTier(i + diff) : knowledge.getTier(i);
            List<String> t2 = (tx >= ty) ? knowledge.getTier(i)       : knowledge.getTier(i + diff);

            String A = t1.get(ix);
            String B = t2.get(iy);
            if (A.equals(B)) continue;
            if (A.equals(tierX.get(ix)) && B.equals(tierY.get(iy))) continue;
            if (B.equals(tierX.get(ix)) && A.equals(tierY.get(iy))) continue;

            Node x1 = this.independenceTest.getVariable(A);
            Node y1 = this.independenceTest.getVariable(B);

            if (graph.isAdjacentTo(x1, y1) && graph.getEndpoint(x1, y1) == Endpoint.CIRCLE) {
                graph.setEndpoint(x1, y1, Endpoint.ARROW);
                if (verbose) {
                    TetradLogger.getInstance().log("Orient edge " + graph.getEdge(x1, y1));
                    TetradLogger.getInstance().log(" by structure knowledge as: " + graph.getEdge(x1, y1));
                }
            }
        }
    }

    private List<List<Node>> returnSimilarPairs(Node x, Node y) {
        if ("time".equals(x.getName()) || "time".equals(y.getName())) {
            return new ArrayList<>();
        }

        int ntiers = this.knowledge.getNumTiers();
        int tx = this.knowledge.isInWhichTier(x);
        int ty = this.knowledge.isInWhichTier(y);
        int diff = FastMath.max(tx, ty) - FastMath.min(tx, ty);

        List<String> tierX = this.knowledge.getTier(tx);
        List<String> tierY = this.knowledge.getTier(ty);

        int ix = -1, iy = -1;
        for (int i = 0; i < tierX.size(); ++i) {
            if (getNameNoLag(x.getName()).equals(getNameNoLag(tierX.get(i)))) { ix = i; break; }
        }
        for (int i = 0; i < tierY.size(); ++i) {
            if (getNameNoLag(y.getName()).equals(getNameNoLag(tierY.get(i)))) { iy = i; break; }
        }

        List<Node> simX = new ArrayList<>();
        List<Node> simY = new ArrayList<>();

        for (int i = 0; i < ntiers - diff; ++i) {
            if (this.knowledge.getTier(i).size() == 1) continue;

            List<String> t1, t2;
            if (tx >= ty) {
                t1 = this.knowledge.getTier(i + diff);
                t2 = this.knowledge.getTier(i);
            } else {
                t1 = this.knowledge.getTier(i);
                t2 = this.knowledge.getTier(i + diff);
            }

            String A = t1.get(ix);
            String B = t2.get(iy);
            if (A.equals(B)) continue;
            if (A.equals(tierX.get(ix)) && B.equals(tierY.get(iy))) continue;
            if (B.equals(tierX.get(ix)) && A.equals(tierY.get(iy))) continue;

            Node x1 = this.graph.getNode(A);
            Node y1 = this.graph.getNode(B);
            simX.add(x1);
            simY.add(y1);
        }

        List<List<Node>> out = new ArrayList<>();
        out.add(simX);
        out.add(simY);
        return out;
    }
}