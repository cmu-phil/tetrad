package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.CorrelationMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.search.score.BlocksBicScore;
import edu.cmu.tetrad.search.test.IndTestBlocks;
import edu.cmu.tetrad.util.RankTests;
import org.ejml.simple.SimpleMatrix;

import java.util.*;

/**
 * Runs TSC to find clusters and then uses these as blocks of variables (plus optional
 * handling of unclustered singletons) to infer a graph over the blocks.
 *
 * New in this version:
 *  - Separate alpha for clustering vs. PC (alphaCluster, alphaPc)
 *  - Separate PC depth (pcDepth)
 *  - Singleton policy: INCLUDE, EXCLUDE, ATTACH_TO_NEAREST, COLLECT_AS_NOISE_LATENT
 *  - attachTau threshold for ATTACH_TO_NEAREST
 *  - Optional noise latent name
 *  - (NEW) Optional hierarchical latent edges among clusters via Wilks rank-drop
 *
 * Backward compatible: if you don't call the new setters, behavior
 * defaults to the original (INCLUDE, same alpha/depth for both stages).
 *
 * @author josephramsey (+ tweaks)
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
    /** If not Integer.MIN_VALUE, overrides depth for PC over blocks. */
    private int pcDepth = Integer.MIN_VALUE;

    /** Policy for handling unclustered singletons. */
    public enum SingletonPolicy { INCLUDE, EXCLUDE, ATTACH_TO_NEAREST, COLLECT_AS_NOISE_LATENT }
    private SingletonPolicy singletonPolicy = SingletonPolicy.INCLUDE;

    /** Threshold for ATTACH_TO_NEAREST (attach if max canonical corr^2 >= attachTau). */
    private double attachTau = 0.15;

    /** Name to use for the pooled "Noise" latent (COLLECT_AS_NOISE_LATENT). */
    private String noiseLatentName = "Noise";

    // ---------- NEW: hierarchy controls ----------
    /** If true, add latent->latent hierarchy edges using Wilks rank-drop. */
    private boolean enableHierarchy = true;
    /** Require at least this much drop to add La -> Lb: rank(Cb,D) - rank(Cb,D|Ca) >= minRankDrop. */
    private int minRankDrop = 1;

    public TscPc(DataSet dataSet) {
        this.dataSet = dataSet;
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
        tsc.setAlpha(alphaCluster);    // cluster alpha (stage-specific)

        tsc.search();

        List<List<Integer>> clusters = tsc.getClusters();
        List<String> latentNames = tsc.getLatentNames();

        // Build singleton set (unclustered variable indices)
        Set<Integer> clustered = new HashSet<>();
        for (List<Integer> c : clusters) clustered.addAll(c);
        List<Integer> singletons = new ArrayList<>();
        for (int j = 0; j < dataSet.getNumColumns(); j++) if (!clustered.contains(j)) singletons.add(j);

        // If there are no true clusters, fallback to INCLUDE so we still have something to learn over.
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
                // Exclude singletons from latent PC; remember them for optional post-attach
                excludedSingletons.addAll(singletons);
                for (int i = 0; i < clusters.size(); i++) {
                    blocks.add(clusters.get(i));
                    metaVars.add(new ContinuousVariable(latentNames.get(i)));
                }
            }
            case COLLECT_AS_NOISE_LATENT -> {
                // Add clusters
                for (int i = 0; i < clusters.size(); i++) {
                    blocks.add(clusters.get(i));
                    metaVars.add(new ContinuousVariable(latentNames.get(i)));
                }
                // Pool all singletons into one "Noise" latent (if any)
                if (!singletons.isEmpty()) {
                    List<Integer> noiseCols = new ArrayList<>(singletons);
                    blocks.add(noiseCols);
                    metaVars.add(new ContinuousVariable(makeUniqueName(metaVars, noiseLatentName)));
                }
            }
        }

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
            cpdag = permutationSearch.search();
        } else {
            IndTestBlocks test = new IndTestBlocks(dataSet, blocks, metaVars);
            test.setAlpha(alphaPc);

            Pc pc = new Pc(test);
            pc.setDepth(pcDepth);
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

        // --- NEW: Add hierarchical latent edges among latent blocks --------------
        if (enableHierarchy) {
            addHierarchyEdges(cpdag, blocks, metaVars, S, N, alphaPc);
        }

        return cpdag;
    }

    // ---------- NEW: Hierarchy helper -------------------------------------------

    /**
     * Add latent->latent edges when conditioning on parent indicators lowers the Wilks rank
     * between child indicators and the rest of the observed variables by at least minRankDrop.
     *
     * For each ordered pair (La, Lb), let Ca, Cb be their indicator sets and D = V \ Cb.
     * If rank(Cb, D | Ca) <= rank(Cb, D) - minRankDrop, add La -> Lb, avoiding directed cycles.
     */
    private void addHierarchyEdges(Graph g,
                                   List<List<Integer>> blocks,
                                   List<Node> metaVars,
                                   SimpleMatrix S,
                                   int sampleSize,
                                   double alpha) {
        // Consider only latent blocks (size > 1) as candidates
        List<Integer> latentIdx = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i++) if (blocks.get(i).size() > 1) latentIdx.add(i);
        final int m = latentIdx.size();
        if (m <= 1) return;

        // Universe of observed variable indices
        int p = dataSet.getNumColumns();
        int[] all = new int[p];
        for (int j = 0; j < p; j++) all[j] = j;

        // Candidate edges with rank drops
        class Cand {
            final int ia, ib; // indices in 'blocks' / 'metaVars'
            final int r0, r1, drop;
            Cand(int ia, int ib, int r0, int r1) { this.ia = ia; this.ib = ib; this.r0 = r0; this.r1 = r1; this.drop = r0 - r1; }
        }
        List<Cand> cands = new ArrayList<>();

        for (int aPos = 0; aPos < m; aPos++) {
            int ia = latentIdx.get(aPos);
            int[] Ca = blocks.get(ia).stream().mapToInt(Integer::intValue).toArray();

            for (int bPos = 0; bPos < m; bPos++) {
                int ib = latentIdx.get(bPos);
                if (ia == ib) continue;

                int[] Cb = blocks.get(ib).stream().mapToInt(Integer::intValue).toArray();
                if (Cb.length == 0) continue;

                int[] D = minus(all, Cb);
                if (D.length == 0) continue;

                int r0 = RankTests.estimateWilksRank(S, Cb, D, sampleSize, alpha);
                if (r0 <= 0) continue;

                int r1 = RankTests.estimateWilksRankConditioned(S, Cb, D, Ca, sampleSize, alpha);

                if (r0 - r1 >= minRankDrop) {
                    cands.add(new Cand(ia, ib, r0, r1));
                }
            }
        }

        // Greedy: biggest rank drop first; tiebreak by names to be deterministic
        cands.sort(Comparator.<Cand>comparingInt(c -> c.drop).reversed()
                .thenComparing(c -> metaVars.get(c.ia).getName())
                .thenComparing(c -> metaVars.get(c.ib).getName()));

        for (Cand c : cands) {
            Node from = metaVars.get(c.ia);
            Node to   = metaVars.get(c.ib);
            if (createsDirectedCycle(g, from, to)) continue;
            if (!g.containsNode(from)) g.addNode(from);
            if (!g.containsNode(to))   g.addNode(to);
            if (!g.isAdjacentTo(from, to)) {
                g.addDirectedEdge(from, to);
                if (verbose) {
                    System.out.printf("Hierarchy: %s -> %s (drop=%d; r0=%d, r1=%d)%n",
                            from.getName(), to.getName(), (c.r0 - c.r1), c.r0, c.r1);
                }
            }
        }
    }

    private static int[] minus(int[] universe, int[] remove) {
        BitSet rm = new BitSet();
        for (int v : remove) rm.set(v);
        int cnt = 0;
        for (int v : universe) if (!rm.get(v)) cnt++;
        int[] out = new int[cnt];
        int i = 0;
        for (int v : universe) if (!rm.get(v)) out[i++] = v;
        return out;
    }

    // Cycle check: adding from->to must not create a directed cycle
    private boolean createsDirectedCycle(Graph g, Node from, Node to) {
        Set<Node> seen = new HashSet<>();
        return dfsChildrenReach(g, to, from, seen);
    }
    private boolean dfsChildrenReach(Graph g, Node cur, Node target, Set<Node> seen) {
        if (cur.equals(target)) return true;
        if (!seen.add(cur)) return false;
        for (Node child : g.getChildren(cur)) {
            if (dfsChildrenReach(g, child, target, seen)) return true;
        }
        return false;
    }

    // ---------- Helpers ----------

    /** Ensure the noise latent name is unique among metaVars. */
    private static String makeUniqueName(List<Node> existing, String base) {
        Set<String> names = new HashSet<>();
        for (Node n : existing) names.add(n.getName());
        if (!names.contains(base)) return base;
        int k = 1;
        while (names.contains(base + "_" + k)) k++;
        return base + "_" + k;
    }

    // ---------- Setters (existing) ----------

    public void setVerbose(boolean verbose) { this.verbose = verbose; }

    public void setEffectiveSampleSize(int effectiveSampleSize) {
        if (effectiveSampleSize < -1 || effectiveSampleSize == 0)
            throw new IllegalArgumentException("effectiveSampleSize must be -1 (auto) or > 0");
        this.effectiveSampleSize = effectiveSampleSize;
    }

    public void setPenaltyDiscount(double penaltyDiscount) { this.penaltyDiscount = penaltyDiscount; }

    public void setNumStarts(int numStarts) {
        if (numStarts < 1) throw new IllegalArgumentException("numStarts must be > 0");
        this.numStarts = numStarts;
    }

    public void setRidge(double ridge) {
        if (ridge < 0.0) throw new IllegalArgumentException("ridge must be >= 0");
        this.ridge = ridge;
    }

    public void setEbicGamma(double ebicGamma) { this.ebicGamma = ebicGamma; }

    public void setUseBoss(boolean useBoss) { this.useBoss = useBoss; }

    /** Alpha for clustering (TSC). */
    public void setAlphaCluster(double alphaCluster) {
        if (alphaCluster < 0.0 || alphaCluster > 1.0)
            throw new IllegalArgumentException("alphaCluster must be between 0.0 and 1.0");
        this.alphaCluster = alphaCluster;
    }

    /** Alpha for PC over blocks. */
    public void setAlphaPc(double alphaPc) {
        if (alphaPc < 0.0 || alphaPc > 1.0)
            throw new IllegalArgumentException("alphaPc must be between 0.0 and 1.0");
        this.alphaPc = alphaPc;
    }

    /** Depth for PC over blocks. */
    public void setPcDepth(int pcDepth) {
        if (!(pcDepth == -1 || pcDepth >= 0))
            throw new IllegalArgumentException("pcDepth must be non-negative or -1");
        this.pcDepth = pcDepth;
    }

    /** Policy for handling unclustered singletons. */
    public void setSingletonPolicy(SingletonPolicy policy) {
        this.singletonPolicy = Objects.requireNonNull(policy, "policy");
    }

    /** Threshold for ATTACH_TO_NEAREST (attach when max canonical corr^2 >= attachTau). */
    public void setAttachTau(double attachTau) {
        if (attachTau < 0.0 || attachTau > 1.0)
            throw new IllegalArgumentException("attachTau must be in [0,1]");
        this.attachTau = attachTau;
    }

    /** Set the name used for the pooled 'Noise' latent. */
    public void setNoiseLatentName(String noiseLatentName) {
        if (noiseLatentName == null || noiseLatentName.isEmpty())
            throw new IllegalArgumentException("noiseLatentName must be non-empty");
        this.noiseLatentName = noiseLatentName;
    }

    // ---------- Setters (new) ----------

    /** Enable/disable adding hierarchical latent edges after PC/BOSS over blocks. */
    public void setEnableHierarchy(boolean enableHierarchy) {
        this.enableHierarchy = enableHierarchy;
    }

    /** Require at least this drop in Wilks rank to add La -> Lb (default 1). */
    public void setMinRankDrop(int minRankDrop) {
        if (minRankDrop < 1) throw new IllegalArgumentException("minRankDrop must be >= 1");
        this.minRankDrop = minRankDrop;
    }
}