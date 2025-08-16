package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.CorrelationMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.search.score.BlocksBicScore;
import edu.cmu.tetrad.search.test.IndTestBlocksWilksRankCachedTS;
import edu.cmu.tetrad.util.RankTests;

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
 *
 * Backward compatible: if you don't call the new setters, behavior
 * defaults to the original (INCLUDE, same alpha/depth for both stages).
 *
 * @author josephramsey (+ tweaks)
 */
public class TscPc implements IGraphSearch {

    // ---------- Existing fields ----------
    private final DataSet dataSet;
    private double alpha = 0.01;     // legacy single alpha (used if the stage-specific ones are not set)
    private int depth = 2;           // legacy single depth (used if pcDepth not set)
    private int effectiveSampleSize; // -1 => use from data
    private double penaltyDiscount = 2;
    private int numStarts = 1;
    private boolean verbose = false;
    private double ridge = 1e-8;
    private double ebicGamma = 0;
    private boolean useBoss = false;

    // ---------- New knobs ----------
    /** If set (non-NaN), overrides alpha for clustering (TSC). */
    private double alphaCluster = Double.NaN;
    /** If set (non-NaN), overrides alpha for PC over blocks. */
    private double alphaPc = Double.NaN;
    /** If not Integer.MIN_VALUE, overrides depth for PC over blocks. */
    private int pcDepth = Integer.MIN_VALUE;

    /** Policy for handling unclustered singletons. */
    public enum SingletonPolicy { INCLUDE, EXCLUDE, ATTACH_TO_NEAREST, COLLECT_AS_NOISE_LATENT }
    private SingletonPolicy singletonPolicy = SingletonPolicy.INCLUDE;

    /** Threshold for ATTACH_TO_NEAREST (attach if max canonical corr^2 >= attachTau). */
    private double attachTau = 0.15;

    /** Name to use for the pooled "Noise" latent (COLLECT_AS_NOISE_LATENT). */
    private String noiseLatentName = "Noise";

    public TscPc(DataSet dataSet) {
        this.dataSet = dataSet;
    }

    @Override
    public Graph search() throws InterruptedException {
        CorrelationMatrix corr = new CorrelationMatrix(dataSet);

        // Resolve effective alpha/depth per stage (fallback to legacy)
        final double aCluster = Double.isNaN(alphaCluster) ? alpha : alphaCluster;
        final double aPc      = Double.isNaN(alphaPc)      ? alpha : alphaPc;
        final int dPc         = (pcDepth == Integer.MIN_VALUE) ? depth : pcDepth;

        // --- TSC clustering ---
        TrekSeparationClusters tsc = new TrekSeparationClusters(
                corr.getVariables(),
                corr,
                effectiveSampleSize == -1 ? corr.getSampleSize() : effectiveSampleSize
        );
        tsc.setVerbose(verbose);
        tsc.setDepth(depth);       // clustering depth (keep legacy)
        tsc.setAlpha(aCluster);    // cluster alpha (stage-specific)
        tsc.setIncludeStructureModel(false);

        tsc.search(new int[][]{{2, 1}}, TrekSeparationClusters.Mode.METALOOP);

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
            IndTestBlocksWilksRankCachedTS test = new IndTestBlocksWilksRankCachedTS(dataSet, blocks, metaVars);
            test.setAlpha(aPc);

            Pc pc = new Pc(test);
            pc.setDepth(dPc);
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
            // Build correlation matrix once for maxCanonicalCorrSq
            var S = new CorrelationMatrix(dataSet).getMatrix().getSimpleMatrix();

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

        return cpdag;
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

    public void setAlpha(double alpha) {
        if (alpha < 0.0 || alpha > 1.0) throw new IllegalArgumentException("alpha must be between 0.0 and 1.0");
        this.alpha = alpha;
    }

    public void setDepth(int depth) {
        if (!(depth == -1 || depth >= 0)) throw new IllegalArgumentException("depth must be non-negative or -1");
        this.depth = depth;
    }

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

    // ---------- New setters (stage-specific α/depth + singleton handling) ----------

    /** Alpha for clustering (TSC). If unset, falls back to {@link #setAlpha(double)} value. */
    public void setAlphaCluster(double alphaCluster) {
        if (alphaCluster < 0.0 || alphaCluster > 1.0)
            throw new IllegalArgumentException("alphaCluster must be between 0.0 and 1.0");
        this.alphaCluster = alphaCluster;
    }

    /** Alpha for PC over blocks. If unset, falls back to {@link #setAlpha(double)} value. */
    public void setAlphaPc(double alphaPc) {
        if (alphaPc < 0.0 || alphaPc > 1.0)
            throw new IllegalArgumentException("alphaPc must be between 0.0 and 1.0");
        this.alphaPc = alphaPc;
    }

    /** Depth for PC over blocks. If unset, falls back to {@link #setDepth(int)} value. */
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
}