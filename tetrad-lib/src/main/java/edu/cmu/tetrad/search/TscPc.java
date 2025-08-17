package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.CorrelationMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.search.score.BlocksBicScore;
import edu.cmu.tetrad.search.test.IndTestBlocksLemma10;
import edu.cmu.tetrad.util.RankTests;
import org.ejml.simple.SimpleMatrix;

import java.util.*;

/**
 * Runs TSC to find clusters and then uses these as blocks of variables (plus optional handling of unclustered
 * singletons) to infer a graph over the blocks.
 * <p>
 * New in this version: - Separate alpha for clustering vs. PC (alphaCluster, alphaPc) - Separate PC depth (pcDepth) -
 * Singleton policy: INCLUDE, EXCLUDE, ATTACH_TO_NEAREST, COLLECT_AS_NOISE_LATENT - attachTau threshold for
 * ATTACH_TO_NEAREST - Optional noise latent name - (NEW) Optional hierarchical latent edges among clusters via
 * HierarchyFinder
 */
public class TscPc implements IGraphSearch {

    // ---------- Existing fields ----------
    private final DataSet dataSet;
    private int effectiveSampleSize; // -1 => use from data
    private double penaltyDiscount = 2;
    private int numStarts = 1;
    private boolean verbose = false;
    private double ridge = 1e-8;
    private double ebicGamma = 0;
    private boolean useBoss = false;

    // ---------- New knobs ----------
    private double alphaCluster = 0.01;
    private double alphaPc = 0.01;
    /**
     * If not Integer.MIN_VALUE, overrides depth for PC over blocks.
     */
    private int pcDepth = Integer.MIN_VALUE;
    private SingletonPolicy singletonPolicy = SingletonPolicy.INCLUDE;
    /**
     * Threshold for ATTACH_TO_NEAREST (attach if max canonical corr^2 >= attachTau).
     */
    private double attachTau = 0.15;
    /**
     * Name to use for the pooled "Noise" latent (COLLECT_AS_NOISE_LATENT).
     */
    private String noiseLatentName = "Noise";

    /**
     * If true, add latent->latent hierarchy edges after PC/BOSS.
     */
    private boolean enableHierarchy = true;
    /**
     * Require at least this Wilks rank drop to add La->Lb.
     */
    private int minRankDrop = 1;

    // ---------- HierarchyFinder controls ----------
    private HierarchyFinder.Strategy hierarchyStrategy = HierarchyFinder.Strategy.PC1;
    /**
     * Only used for PC1; 0 disables confounder control.
     */
    private int topKConf = 1;
    /**
     * Use alphaCluster for hierarchy tests unless overridden.
     */
    private Double alphaHierarchy = null;  // null => use alphaCluster
    /**
     * Optional PC1 improvements.
     */
    private boolean hierarchyOrthogonalize = true;
    private boolean hierarchySpecificityGate = true;
    private double hierarchyRidge = 1e-6;
    // Default: strict (the classic FOFC/TSC assumption)
    private EdgePolicy edgePolicy = EdgePolicy.STRICT;

    public TscPc(DataSet dataSet) {
        this.dataSet = dataSet;
    }

    /**
     * Ensure the noise latent name is unique among metaVars.
     */
    private static String makeUniqueName(List<Node> existing, String base) {
        Set<String> names = new HashSet<>();
        for (Node n : existing) names.add(n.getName());
        if (!names.contains(base)) return base;
        int k = 1;
        while (names.contains(base + "_" + k)) k++;
        return base + "_" + k;
    }

    @Override
    public Graph search() throws InterruptedException {
        CorrelationMatrix corr = new CorrelationMatrix(dataSet);
        SimpleMatrix S = corr.getMatrix().getSimpleMatrix();
        int N = (effectiveSampleSize == -1 || effectiveSampleSize == 0)
                ? corr.getSampleSize()
                : effectiveSampleSize;

        // --- TSC clustering ---
        TrekSeparationClusters tsc = new TrekSeparationClusters(
                corr.getVariables(),
                corr,
                N
        );
        tsc.setVerbose(verbose);
        tsc.setAlpha(alphaCluster); // cluster alpha
        tsc.setEnforceObservedLeaves(true);
//        tsc.setEnforceObservedLeaves(true);
//        tsc.setAntiProxyDrop(1);  // try 1; 2 is stricter

        tsc.search();

        List<List<Integer>> clusters = tsc.getClusters();
        List<String> latentNames = tsc.getLatentNames();

        // Build singleton set (unclustered variable indices)
        Set<Integer> clustered = new HashSet<>();
        for (List<Integer> c : clusters) clustered.addAll(c);
        List<Integer> singletons = new ArrayList<>();
        for (int j = 0; j < dataSet.getNumColumns(); j++) if (!clustered.contains(j)) singletons.add(j);

        // If there are no true clusters, fallback so we still have something to learn over.
        boolean hasTrueCluster = clusters.stream().anyMatch(c -> c != null && c.size() > 1);
        SingletonPolicy policy = (!hasTrueCluster && (singletonPolicy == SingletonPolicy.EXCLUDE
                                                      || singletonPolicy == SingletonPolicy.ATTACH_TO_NEAREST
                                                      || singletonPolicy == SingletonPolicy.COLLECT_AS_NOISE_LATENT))
                ? SingletonPolicy.INCLUDE
                : singletonPolicy;

        // --- Build blocks + meta variables according to policy ---
        List<List<Integer>> blocks = new ArrayList<>();
        List<Node> metaVars = new ArrayList<>();

        // Keep track of excluded singletons for post-attach (ATTACH_TO_NEAREST)
        List<Integer> excludedSingletons = new ArrayList<>();

        switch (policy) {
            case INCLUDE -> {
                // Singletons as their own blocks
                for (int s : singletons) {
                    blocks.add(Collections.singletonList(s));
                    metaVars.add(dataSet.getVariable(s));
                }
                // Add clusters as latents
                for (int i = 0; i < clusters.size(); i++) {
                    blocks.add(clusters.get(i));
                    metaVars.add(new ContinuousVariable(latentNames.get(i)));
                }
            }
            case EXCLUDE, ATTACH_TO_NEAREST -> {
                excludedSingletons.addAll(singletons);
                for (int i = 0; i < clusters.size(); i++) {
                    blocks.add(clusters.get(i));
                    metaVars.add(new ContinuousVariable(latentNames.get(i)));
                }
            }
            case COLLECT_AS_NOISE_LATENT -> {
                for (int i = 0; i < clusters.size(); i++) {
                    blocks.add(clusters.get(i));
                    metaVars.add(new ContinuousVariable(latentNames.get(i)));
                }
                if (!singletons.isEmpty()) {
                    List<Integer> noiseCols = new ArrayList<>(singletons);
                    blocks.add(noiseCols);
                    metaVars.add(new ContinuousVariable(makeUniqueName(metaVars, noiseLatentName)));
                }
            }
        }

        // Your tiers
        Knowledge knowledge = new Knowledge();

        for (int i = 0; i < blocks.size(); i++) {
            boolean isSingleton = blocks.get(i).size() == 1;
            Node meta = metaVars.get(i);

            switch (edgePolicy) {
                case STRICT -> {
                    if (isSingleton) {
                        // measures in their own tier, but not blocked from each other
                        knowledge.addToTier(2, meta.getName());
                    } else {
                        knowledge.addToTier(1, meta.getName());
                    }

                    knowledge.setTierForbiddenWithin(2, true);
                }
                case ALLOW_MEASURE_TO_MEASURE -> {
                    if (isSingleton) {
                        knowledge.addToTier(2, meta.getName()); // measures in tier 2
                    } else {
                        knowledge.addToTier(1, meta.getName()); // latents in tier 1
                    }
                }
                case FULL -> {
                    // no restrictions at all
                }
            }
        }

        System.out.println("Knowledge" + knowledge);

        // --- Learn meta-graph (PC or BOSS) on blocks/metaVars ---
        Graph cpdag;
        if (useBoss) {
            BlocksBicScore score = new BlocksBicScore(dataSet, blocks, metaVars);
            score.setPenaltyDiscount(penaltyDiscount);
            score.setRidge(ridge);
            score.setEbicGamma(ebicGamma);

            Boss suborderSearch = new Boss(score);
            suborderSearch.setVerbose(verbose);
            suborderSearch.setNumStarts(numStarts);

            PermutationSearch permutationSearch = new PermutationSearch(suborderSearch);
            permutationSearch.setKnowledge(knowledge);
            cpdag = permutationSearch.search();
        } else {
//            IndTestBlocks test = new IndTestBlocks(dataSet, blocks, metaVars);
            IndTestBlocksLemma10 test = new IndTestBlocksLemma10(dataSet, blocks, metaVars);

            test.setAlpha(alphaPc);

            Pc pc = new Pc(test);
            pc.setDepth(pcDepth);
            pc.setKnowledge(knowledge);
            cpdag = pc.search();
        }

        // --- Add latent→member edges for true clusters (measurement model edges) ---
        for (int i = 0; i < blocks.size(); i++) {
            List<Integer> block = blocks.get(i);
            Node meta = metaVars.get(i);
            if (block.size() > 1) {
                meta.setNodeType(NodeType.LATENT);
                for (int col : block) {
                    Node child = dataSet.getVariable(col);
                    if (!cpdag.containsNode(child)) cpdag.addNode(child);
                    if (!cpdag.isAdjacentTo(meta, child)) cpdag.addDirectedEdge(meta, child);
                }
            }
        }

        // --- Post-attach excluded singletons to their nearest latent (if requested) ---
        if (policy == SingletonPolicy.ATTACH_TO_NEAREST && !excludedSingletons.isEmpty()) {
            // Build list of latent blocks (indices in 'blocks' where size > 1)
            List<Integer> latentBlockIdx = new ArrayList<>();
            for (int i = 0; i < blocks.size(); i++) if (blocks.get(i).size() > 1) latentBlockIdx.add(i);

            for (int s : excludedSingletons) {
                int[] sCols = new int[]{s};
                double bestR2 = 0.0;
                int bestLatent = -1;

                for (int L : latentBlockIdx) {
                    int[] Lcols = blocks.get(L).stream().mapToInt(Integer::intValue).toArray();
                    double r2 = RankTests.maxCanonicalCorrSq(S, sCols, Lcols);
                    if (r2 > bestR2) {
                        bestR2 = r2;
                        bestLatent = L;
                    }
                }

                if (bestLatent >= 0 && bestR2 >= attachTau) {
                    Node latent = metaVars.get(bestLatent);
                    Node child = dataSet.getVariable(s);
                    if (!cpdag.containsNode(child)) cpdag.addNode(child);
                    // Attach as latent → singleton
                    if (!cpdag.isAdjacentTo(latent, child)) cpdag.addDirectedEdge(latent, child);
                }
            }
        }

        // --- Add hierarchical latent edges among latent blocks (via HierarchyFinder) ---
        if (enableHierarchy) {
            addHierarchyEdges(cpdag, blocks, metaVars, S, N);
        }

        return cpdag;
    }

    /**
     * Add La->Lb based on HierarchyFinder proposals; prevents directed cycles.
     */
    private void addHierarchyEdges(Graph g,
                                   List<List<Integer>> blocks,
                                   List<Node> metaVars,
                                   SimpleMatrix S,
                                   int sampleSize) {

        double aH = (alphaHierarchy == null) ? alphaCluster : alphaHierarchy;

        List<HierarchyFinder.Proposal> props = HierarchyFinder.propose(
                S, blocks, metaVars, sampleSize,
                /*alpha=*/aH,
                /*minRankDrop=*/minRankDrop,
                hierarchyStrategy,
                /*topKConf=*/topKConf,
                /*orthogonalizeScores=*/hierarchyOrthogonalize,
                /*specificityGate=*/hierarchySpecificityGate,
                /*ridgeScore=*/hierarchyRidge,
                /*verbose=*/verbose
        );

        for (HierarchyFinder.Proposal pr : props) {
            Node from = metaVars.get(pr.fromBlock);
            Node to = metaVars.get(pr.toBlock);
            if (createsDirectedCycle(g, from, to)) continue;
            if (!g.containsNode(from)) g.addNode(from);
            if (!g.containsNode(to)) g.addNode(to);
            if (!g.isAdjacentTo(from, to)) g.addDirectedEdge(from, to);
        }
    }

    // Cycle check: adding from->to must not create a directed cycle
    private boolean createsDirectedCycle(Graph g, Node from, Node to) {
        Set<Node> seen = new HashSet<>();
        return dfsChildrenReach(g, to, from, seen);
    }

    // ---------- Helpers / setters ----------

    private boolean dfsChildrenReach(Graph g, Node cur, Node target, Set<Node> seen) {
        if (cur.equals(target)) return true;
        if (!seen.add(cur)) return false;
        for (Node child : g.getChildren(cur)) {
            if (dfsChildrenReach(g, child, target, seen)) return true;
        }
        return false;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public void setEffectiveSampleSize(int effectiveSampleSize) {
        if (effectiveSampleSize < -1 || effectiveSampleSize == 0)
            throw new IllegalArgumentException("effectiveSampleSize must be -1 (auto) or > 0");
        this.effectiveSampleSize = effectiveSampleSize;
    }

    public void setPenaltyDiscount(double penaltyDiscount) {
        this.penaltyDiscount = penaltyDiscount;
    }

    public void setNumStarts(int numStarts) {
        if (numStarts < 1) throw new IllegalArgumentException("numStarts must be > 0");
        this.numStarts = numStarts;
    }

    public void setRidge(double ridge) {
        if (ridge < 0.0) throw new IllegalArgumentException("ridge must be >= 0");
        this.ridge = ridge;
    }

    public void setEbicGamma(double ebicGamma) {
        this.ebicGamma = ebicGamma;
    }

    public void setUseBoss(boolean useBoss) {
        this.useBoss = useBoss;
    }

    /**
     * Alpha for clustering (TSC).
     */
    public void setAlphaCluster(double alphaCluster) {
        if (alphaCluster < 0.0 || alphaCluster > 1.0)
            throw new IllegalArgumentException("alphaCluster must be between 0.0 and 1.0");
        this.alphaCluster = alphaCluster;
    }

    /**
     * Alpha for PC over blocks.
     */
    public void setAlphaPc(double alphaPc) {
        if (alphaPc < 0.0 || alphaPc > 1.0)
            throw new IllegalArgumentException("alphaPc must be between 0.0 and 1.0");
        this.alphaPc = alphaPc;
    }

    /**
     * Depth for PC over blocks.
     */
    public void setPcDepth(int pcDepth) {
        if (!(pcDepth == -1 || pcDepth >= 0))
            throw new IllegalArgumentException("pcDepth must be non-negative or -1");
        this.pcDepth = pcDepth;
    }

    /**
     * Policy for handling unclustered singletons.
     */
    public void setSingletonPolicy(SingletonPolicy policy) {
        this.singletonPolicy = Objects.requireNonNull(policy, "policy");
    }

    /**
     * Set the name used for the pooled 'Noise' latent.
     */
    public void setNoiseLatentName(String noiseLatentName) {
        if (noiseLatentName == null || noiseLatentName.isEmpty())
            throw new IllegalArgumentException("noiseLatentName must be non-empty");
        this.noiseLatentName = noiseLatentName;
    }

    /**
     * Enable/disable adding hierarchical latent edges after PC/BOSS over blocks.
     */
    public void setEnableHierarchy(boolean enableHierarchy) {
        this.enableHierarchy = enableHierarchy;
    }

    /**
     * Require at least this drop in Wilks rank to add La -> Lb (default 1).
     */
    public void setMinRankDrop(int minRankDrop) {
        if (minRankDrop < 1) throw new IllegalArgumentException("minRankDrop must be >= 1");
        this.minRankDrop = minRankDrop;
    }

    /**
     * Strategy for hierarchy detection (PC1 or INDICATORS).
     */
    public void setHierarchyStrategy(HierarchyFinder.Strategy strategy) {
        this.hierarchyStrategy = Objects.requireNonNull(strategy, "strategy");
    }

    /**
     * Number of top confounder scores to condition on (PC1 only).
     */
    public void setTopKConf(int topKConf) {
        if (topKConf < 0) throw new IllegalArgumentException("topKConf must be >= 0");
        this.topKConf = topKConf;
    }

    /**
     * Optional separate alpha for hierarchy tests; null => use alphaCluster.
     */
    public void setAlphaHierarchy(Double alphaHierarchy) {
        if (alphaHierarchy != null && (alphaHierarchy < 0.0 || alphaHierarchy > 1.0))
            throw new IllegalArgumentException("alphaHierarchy must be in [0,1]");
        this.alphaHierarchy = alphaHierarchy;
    }

    /**
     * PC1 orthogonalization toggle.
     */
    public void setHierarchyOrthogonalize(boolean b) {
        this.hierarchyOrthogonalize = b;
    }

    /**
     * Specificity gate toggle.
     */
    public void setHierarchySpecificityGate(boolean b) {
        this.hierarchySpecificityGate = b;
    }

    /**
     * Ridge added when inverting confounder score correlation.
     */
    public void setHierarchyRidge(double r) {
        if (r < 0.0) throw new IllegalArgumentException("ridge must be >= 0");
        this.hierarchyRidge = r;
    }

    public void setEdgePolicy(EdgePolicy policy) {
        this.edgePolicy = Objects.requireNonNull(policy, "edgePolicy");
    }

    /**
     * Policy for handling unclustered singletons.
     */
    public enum SingletonPolicy {INCLUDE, EXCLUDE, ATTACH_TO_NEAREST, COLLECT_AS_NOISE_LATENT}

    /**
     * Policy for which cross-block edges are allowed in the block graph. - STRICT: No observed→latent, no
     * observed→observed. - ALLOW_MEASURE_TO_MEASURE: Observed→observed edges allowed, but not observed→latent. - FULL:
     * Observed→observed and observed→latent edges allowed.
     */
    public enum EdgePolicy {
        STRICT,
        ALLOW_MEASURE_TO_MEASURE,
        FULL
    }
}