package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.RankTests;
import org.ejml.simple.SimpleEVD;
import org.ejml.simple.SimpleMatrix;

import java.util.*;

/**
 * Utility for proposing hierarchical latent edges among blocks (latent clusters).
 *
 * Strategies:
 *  1) Strategy.PC1: Condition on a 1-D proxy score S_a (PC1 of Ca). Optional top-K confounder
 *     scores can be included to block alternate parents of the child. Optional orthogonalization
 *     makes S_a ⟂ S_conf in the augmented correlation.
 *  2) Strategy.INDICATORS: Condition directly on the parent indicator set Ca (legacy).
 *
 * Returns candidate edges sorted by rank drop (largest first).
 * The caller is responsible for cycle checks and final insertion into the graph.
 */
public final class HierarchyFinder {

    private HierarchyFinder() {}

    public enum Strategy { PC1, INDICATORS }

    /** A proposed directed edge La -> Lb with diagnostics. */
    public static final class Proposal {
        public final int fromBlock;     // index into blocks/metaVars
        public final int toBlock;       // index into blocks/metaVars
        public final String fromName;   // metaVars.get(fromBlock).getName()
        public final String toName;     // metaVars.get(toBlock).getName()
        public final int r0;            // baseline rank
        public final int r1;            // conditioned rank (with Sa [+ conf])
        public final int drop;          // r0 - r1
        public Proposal(int fromBlock, int toBlock, String fromName, String toName, int r0, int r1) {
            this.fromBlock = fromBlock; this.toBlock = toBlock;
            this.fromName = fromName; this.toName = toName;
            this.r0 = r0; this.r1 = r1; this.drop = r0 - r1;
        }
        @Override public String toString() {
            return "Proposal{" + fromName + "->" + toName + ", drop=" + drop + ", r0=" + r0 + ", r1=" + r1 + "}";
        }
    }

    /* ======== NEW: scoring toggles & knobs (defaults keep legacy behavior) ======== */
    private static boolean useScoredRanks = false;
    private static double scoreRidge = 1e-6;          // RCCA ridge
    private static double scorePenaltyDiscount = 1.0; // BIC c
    private static double scoreEbicGamma = 0.0;       // EBIC gamma

    public static void setUseScoredRanks(boolean b) { useScoredRanks = b; }
    public static void setScoreRidge(double r) {
        if (r < 0.0) throw new IllegalArgumentException("ridge >= 0");
        scoreRidge = r;
    }
    public static void setScorePenaltyDiscount(double c) {
        if (c <= 0.0) throw new IllegalArgumentException("penaltyDiscount > 0");
        scorePenaltyDiscount = c;
    }
    public static void setScoreEbicGamma(double gamma) {
        if (gamma < 0.0) throw new IllegalArgumentException("ebicGamma >= 0");
        scoreEbicGamma = gamma;
    }

    /* ============================== PUBLIC API (unchanged) ============================== */

    public static List<Edge> computeEdges(SimpleMatrix S,
                                          List<List<Integer>> blocks,
                                          List<Node> metaVars,
                                          int sampleSize,
                                          double alpha,
                                          int minRankDrop,
                                          Strategy strategy,
                                          int topKConf,
                                          boolean verbose) {
        List<Proposal> props = propose(S, blocks, metaVars, sampleSize, alpha, minRankDrop,
                strategy, topKConf, /*orthogonalizeScores=*/false, /*specificityGate=*/false,
                /*ridgeScore=*/1e-6, verbose);
        List<Edge> edges = new ArrayList<>(props.size());
        for (Proposal p : props) {
            edges.add(Edges.directedEdge(metaVars.get(p.fromBlock), metaVars.get(p.toBlock)));
        }
        return edges;
    }

    public static List<Proposal> propose(SimpleMatrix S,
                                         List<List<Integer>> blocks,
                                         List<Node> metaVars,
                                         int sampleSize,
                                         double alpha,
                                         int minRankDrop,
                                         Strategy strategy,
                                         int topKConf,
                                         boolean verbose) {
        return propose(S, blocks, metaVars, sampleSize, alpha, minRankDrop,
                strategy, topKConf, /*orthogonalizeScores=*/false, /*specificityGate=*/false,
                /*ridgeScore=*/1e-6, verbose);
    }

    /* -------- Overloads with orthogonalization + specificity gate -------- */

    public static List<Edge> computeEdges(SimpleMatrix S,
                                          List<List<Integer>> blocks,
                                          List<Node> metaVars,
                                          int sampleSize,
                                          double alpha,
                                          int minRankDrop,
                                          Strategy strategy,
                                          int topKConf,
                                          boolean orthogonalizeScores,
                                          boolean specificityGate,
                                          double ridgeScore,
                                          boolean verbose) {
        List<Proposal> props = propose(S, blocks, metaVars, sampleSize, alpha, minRankDrop,
                strategy, topKConf, orthogonalizeScores, specificityGate, ridgeScore, verbose);
        List<Edge> edges = new ArrayList<>(props.size());
        for (Proposal p : props) {
            edges.add(Edges.directedEdge(metaVars.get(p.fromBlock), metaVars.get(p.toBlock)));
        }
        return edges;
    }

    public static List<Proposal> propose(SimpleMatrix S,
                                         List<List<Integer>> blocks,
                                         List<Node> metaVars,
                                         int sampleSize,
                                         double alpha,
                                         int minRankDrop,
                                         Strategy strategy,
                                         int topKConf,
                                         boolean orthogonalizeScores,
                                         boolean specificityGate,
                                         double ridgeScoreProxy,
                                         boolean verbose) {

        Objects.requireNonNull(S, "S");
        Objects.requireNonNull(blocks, "blocks");
        Objects.requireNonNull(metaVars, "metaVars");
        Objects.requireNonNull(strategy, "strategy");
        if (minRankDrop < 1) throw new IllegalArgumentException("minRankDrop must be >= 1");
        if (topKConf < 0) throw new IllegalArgumentException("topKConf must be >= 0");
        if (ridgeScoreProxy < 0.0) throw new IllegalArgumentException("ridgeScore must be >= 0");

        // Candidate latent indices: blocks with |block| > 1
        List<Integer> latentIdx = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i++) if (blocks.get(i).size() > 1) latentIdx.add(i);
        final int L = latentIdx.size();
        if (L <= 1) return Collections.emptyList();

        final int p = S.getNumCols(); // observed
        final int[][] Cblock = new int[L][];
        final BitSet[] Cbit = new BitSet[L];
        for (int pos = 0; pos < L; pos++) {
            int bi = latentIdx.get(pos);
            int[] arr = blocks.get(bi).stream().mapToInt(Integer::intValue).toArray();
            Cblock[pos] = arr;
            BitSet bs = new BitSet();
            for (int v : arr) bs.set(v);
            Cbit[pos] = bs;
        }

        if (strategy == Strategy.PC1) {
            return proposeWithPC1(S, p, blocks, metaVars, latentIdx, Cblock, Cbit,
                    sampleSize, alpha, minRankDrop, topKConf,
                    orthogonalizeScores, specificityGate, ridgeScoreProxy, verbose);
        } else {
            return proposeWithIndicators(S, p, blocks, metaVars, latentIdx, Cblock,
                    sampleSize, alpha, minRankDrop, verbose);
        }
    }

    /* ==================== Strategy: PC1 proxy (with options) ==================== */

    private static List<Proposal> proposeWithPC1(SimpleMatrix S,
                                                 int p,
                                                 List<List<Integer>> blocks,
                                                 List<Node> metaVars,
                                                 List<Integer> latentIdx,
                                                 int[][] Cblock,
                                                 BitSet[] Cbit,
                                                 int sampleSize,
                                                 double alpha,
                                                 int minRankDrop,
                                                 int topKConf,
                                                 boolean orthogonalizeScores,
                                                 boolean specificityGate,
                                                 double ridgeScoreProxy,
                                                 boolean verbose) {

        final int L = latentIdx.size();
        final double EPS = 1e-12;

        // Augment S with all PC1 scores at once: Splus is (p+L) x (p+L)
        SimpleMatrix Splus = new SimpleMatrix(p + L, p + L);
        for (int i = 0; i < p; i++) for (int j = 0; j < p; j++) Splus.set(i, j, S.get(i, j));

        int[] scoreIndex = new int[L];
        for (int pos = 0; pos < L; pos++) {
            int[] Ca = Cblock[pos];
            int si = p + pos;
            scoreIndex[pos] = si;

            if (Ca.length < 2) { // degenerate score
                for (int j = 0; j < p; j++) { Splus.set(si, j, 0.0); Splus.set(j, si, 0.0); }
                Splus.set(si, si, 1.0);
                continue;
            }

            // PCA on S[Ca, Ca]
            SimpleMatrix S_Ca = new SimpleMatrix(Ca.length, Ca.length);
            for (int u = 0; u < Ca.length; u++)
                for (int v = 0; v < Ca.length; v++)
                    S_Ca.set(u, v, S.get(Ca[u], Ca[v]));

            SimpleEVD<SimpleMatrix> evd = S_Ca.eig();
            int idxMax = 0; double lambda1 = -Double.MAX_VALUE;
            for (int k = 0; k < Ca.length; k++) {
                double lam = evd.getEigenvalue(k).getReal();
                if (lam > lambda1) { lambda1 = lam; idxMax = k; }
            }
            double scale = Math.sqrt(Math.max(lambda1, EPS));
            SimpleMatrix w = evd.getEigenVector(idxMax); // |Ca| x 1

            // r_j = cor(S_a, X_j) = sum_t cor(X_j, Ca_t)*w_t / sqrt(lambda1)
            for (int j = 0; j < p; j++) {
                double acc = 0.0;
                for (int t = 0; t < Ca.length; t++) acc += S.get(j, Ca[t]) * w.get(t, 0);
                double r = acc / scale;
                Splus.set(si, j, r); Splus.set(j, si, r);
            }
            Splus.set(si, si, 1.0);
        }

        // Pick top-K confounder scores per child by unconditional rank with Cb (Wilks for speed)
        int[][] confParents = new int[L][];
        if (topKConf > 0) {
            for (int bPos = 0; bPos < L; bPos++) {
                int[] Cb = Cblock[bPos];
                List<int[]> scored = new ArrayList<>();
                for (int aPos = 0; aPos < L; aPos++) {
                    if (aPos == bPos) continue;
                    int[] Ca = Cblock[aPos];
                    if (Ca.length == 0) continue;
                    int rpar = RankTests.estimateWilksRank(S, Cb, Ca, sampleSize, alpha);
                    scored.add(new int[]{aPos, rpar});
                }
                scored.sort((u, v) -> Integer.compare(v[1], u[1]));
                int k = Math.min(topKConf, scored.size());
                int[] conf = new int[k];
                for (int i = 0; i < k; i++) conf[i] = scored.get(i)[0];
                confParents[bPos] = conf;
            }
        } else {
            for (int bPos = 0; bPos < L; bPos++) confParents[bPos] = new int[0];
        }

        // Precompute inverse for each child's conf set (for orthogonalization)
        Map<Integer, SimpleMatrix> invR_scsc = new HashMap<>();

        List<Proposal> props = new ArrayList<>();
        BitSet universe = new BitSet(); universe.set(0, p); // observed indices

        for (int aPos = 0; aPos < L; aPos++) {
            int ia = latentIdx.get(aPos);
            int[] Ca = Cblock[aPos];
            if (Ca.length == 0) continue;

            for (int bPos = 0; bPos < L; bPos++) {
                if (aPos == bPos) continue;
                int ib = latentIdx.get(bPos);
                int[] Cb = Cblock[bPos];
                if (Cb.length == 0) continue;

                // Y = (V \ Cb) \ Ca \ (indicators of selected confounders)
                BitSet Ybit = (BitSet) universe.clone();
                for (int v : Cb) Ybit.clear(v);
                for (int v : Ca) Ybit.clear(v);
                for (int cp : confParents[bPos]) for (int v : Cblock[cp]) Ybit.clear(v);
                int ySize = Ybit.cardinality();
                if (ySize == 0) continue;
                int[] Y = new int[ySize];
                for (int idx = Ybit.nextSetBit(0), k = 0; idx >= 0; idx = Ybit.nextSetBit(idx + 1)) Y[k++] = idx;

                // Baseline rank r0 on augmented S
                int r0 = rankUncond(Splus, Cb, Y, sampleSize, alpha);

                // Confounder score indices for child (exclude aPos if present)
                int finalAPos = aPos;
                int[] Zconf = Arrays.stream(confParents[bPos]).filter(cp -> cp != finalAPos).map(cp -> p + cp).toArray();

                // (Optional) Specificity baseline: r_confOnly = rank(Cb, Y | Z_conf)
                Integer rConfOnly = null;
                if (specificityGate && Zconf.length > 0) {
                    rConfOnly = rankCond(Splus, Cb, Y, Zconf, sampleSize, alpha);
                }

                // Z = {Sa} ∪ Z_conf (+ optional orthogonalization)
                int Sa = p + aPos;
                if (orthogonalizeScores && Zconf.length > 0) {
                    SimpleMatrix inv = invR_scsc.get(bPos);
                    if (inv == null) {
                        SimpleMatrix Rscsc = new SimpleMatrix(Zconf.length, Zconf.length);
                        for (int i = 0; i < Zconf.length; i++)
                            for (int j = 0; j < Zconf.length; j++)
                                Rscsc.set(i, j, Splus.get(Zconf[i], Zconf[j]));
                        if (ridgeScoreProxy > 0) for (int i = 0; i < Zconf.length; i++) Rscsc.set(i, i, Rscsc.get(i, i) + ridgeScoreProxy);
                        inv = Rscsc.invert();
                        invR_scsc.put(bPos, inv);
                    }
                    SimpleMatrix rSaZ = new SimpleMatrix(1, Zconf.length);
                    for (int i = 0; i < Zconf.length; i++) rSaZ.set(0, i, Splus.get(Sa, Zconf[i]));
                    SimpleMatrix temp = rSaZ.mult(inv);
                    int dim = p + L;
                    for (int j = 0; j < dim; j++) {
                        SimpleMatrix rZj = new SimpleMatrix(Zconf.length, 1);
                        for (int i = 0; i < Zconf.length; i++) rZj.set(i, 0, Splus.get(Zconf[i], j));
                        double proj = temp.mult(rZj).get(0, 0);
                        double rNew = Splus.get(Sa, j) - proj;
                        Splus.set(Sa, j, rNew);
                        Splus.set(j, Sa, rNew);
                    }
                    for (int idx : Zconf) { Splus.set(Sa, idx, 0.0); Splus.set(idx, Sa, 0.0); }
                    Splus.set(Sa, Sa, 1.0);
                }

                int[] Z = (Zconf.length == 0) ? new int[]{ Sa } : concat(new int[]{Sa}, Zconf);

                int r1 = rankCond(Splus, Cb, Y, Z, sampleSize, alpha);

                // Specificity gate
                if (specificityGate && Zconf.length > 0) {
                    int rCOnly = (rConfOnly != null) ? rConfOnly : rankCond(Splus, Cb, Y, Zconf, sampleSize, alpha);
                    if (!(r1 < rCOnly)) {
                        if (verbose) {
                            System.out.printf("skip(PC1) %s -> %s : r0=%d, r1=%d, rConf=%d%n",
                                    metaVars.get(latentIdx.get(aPos)).getName(),
                                    metaVars.get(latentIdx.get(bPos)).getName(), r0, r1, rCOnly);
                        }
                        continue;
                    }
                }

                int drop = r0 - r1;
                if (drop >= minRankDrop) {
                    Proposal pr = new Proposal(latentIdx.get(aPos), latentIdx.get(bPos),
                            metaVars.get(latentIdx.get(aPos)).getName(),
                            metaVars.get(latentIdx.get(bPos)).getName(), r0, r1);
                    props.add(pr);
                    if (verbose) {
                        System.out.printf("hier(PC1) %s -> %s : r0=%d, r1=%d, drop=%d%n",
                                pr.fromName, pr.toName, pr.r0, pr.r1, drop);
                    }
                } else if (verbose) {
                    System.out.printf("skip(PC1) %s -> %s : r0=%d, r1=%d, drop=%d%n",
                            metaVars.get(latentIdx.get(aPos)).getName(),
                            metaVars.get(latentIdx.get(bPos)).getName(), r0, r1, drop);
                }
            }
        }

        props.sort(Comparator.<Proposal>comparingInt(pv -> pv.drop).reversed()
                .thenComparing(pv -> pv.fromName)
                .thenComparing(pv -> pv.toName));
        return props;
    }

    /* ==================== Strategy: indicators (legacy) ==================== */

    private static List<Proposal> proposeWithIndicators(SimpleMatrix S,
                                                        int p,
                                                        List<List<Integer>> blocks,
                                                        List<Node> metaVars,
                                                        List<Integer> latentIdx,
                                                        int[][] Cblock,
                                                        int sampleSize,
                                                        double alpha,
                                                        int minRankDrop,
                                                        boolean verbose) {

        // Universe 0..p-1
        int[] all = new int[p];
        for (int j = 0; j < p; j++) all[j] = j;

        List<Proposal> props = new ArrayList<>();

        for (int aPos = 0; aPos < latentIdx.size(); aPos++) {
            int ia = latentIdx.get(aPos);
            int[] Ca = Cblock[aPos];
            if (Ca.length == 0) continue;

            for (int bPos = 0; bPos < latentIdx.size(); bPos++) {
                int ib = latentIdx.get(bPos);
                if (aPos == bPos) continue;

                int[] Cb = Cblock[bPos];
                if (Cb.length == 0) continue;

                // IMPORTANT: Y disjoint from Z: Y = (V \ Cb) \ Ca
                int[] Db = minus(all, Cb);
                int[] Y  = minus(Db, Ca);
                if (Y.length == 0) continue;

                int r0 = rankUncond(S, Cb, Y, sampleSize, alpha);
                int r1 = rankCond(S, Cb, Y, Ca, sampleSize, alpha);
                int drop = r0 - r1;

                if (drop >= minRankDrop) {
                    Proposal pr = new Proposal(ia, ib, metaVars.get(ia).getName(), metaVars.get(ib).getName(), r0, r1);
                    props.add(pr);
                    if (verbose) {
                        System.out.printf("hier(IND) %s -> %s : r0=%d, r1=%d, drop=%d%n",
                                pr.fromName, pr.toName, pr.r0, pr.r1, drop);
                    }
                } else if (verbose) {
                    System.out.printf("skip(IND) %s -> %s : r0=%d, r1=%d, drop=%d%n",
                            metaVars.get(ia).getName(), metaVars.get(ib).getName(), r0, r1, drop);
                }
            }
        }

        props.sort(Comparator.<Proposal>comparingInt(pv -> pv.drop).reversed()
                .thenComparing(pv -> pv.fromName)
                .thenComparing(pv -> pv.toName));
        return props;
    }

    /* ==================== Rank helpers (Wilks vs Scored) ==================== */

    private static int rankUncond(SimpleMatrix S, int[] C, int[] D, int n, double alpha) {
        if (!useScoredRanks) {
            return RankTests.estimateWilksRank(S, C, D, n, alpha);
        }
        RankTests.RccaEntry ent = RankTests.getRccaEntry(S, C, D, scoreRidge);
        return (ent == null) ? 0 : argmaxRankBIC(ent, C.length, D.length, n);
    }

    private static int rankCond(SimpleMatrix S, int[] C, int[] D, int[] Z, int n, double alpha) {
        if (!useScoredRanks) {
            return RankTests.estimateWilksRankConditioned(S, C, D, Z, n, alpha);
        }
        // Try conditioned RCCA entry if available; otherwise fall back to Wilks.
        RankTests.RccaEntry ent;
        try {
            ent = RankTests.getRccaEntryConditioned(S, C, D, Z, scoreRidge);
        } catch (Throwable t) {
            ent = null;
        }
        if (ent == null) {
            // Fallback: Wilks for conditioned case if RCCA-conditioned is not implemented.
            return RankTests.estimateWilksRankConditioned(S, C, D, Z, n, alpha);
        }
        return argmaxRankBIC(ent, C.length, D.length, n);
    }

    /** BIC/EBIC sweep on RCCA suffix logs; returns argmax r. */
    private static int argmaxRankBIC(RankTests.RccaEntry ent, int p, int q, int n) {
        if (ent == null || ent.suffixLogs == null) return 0;
        int m = Math.min(Math.min(p, q), n - 1);
        m = Math.min(m, ent.suffixLogs.length - 1);
        if (m <= 0) return 0;

        double[] suf = ent.suffixLogs; // suf[0]==0
        double base = suf[0];

        // Bartlett-style effective n (same spirit as BlocksBicScore)
        double nEff = n - 1.0 - 0.5 * (p + q + 1.0);
        if (nEff < 1.0) nEff = 1.0;
        int Ppool = Math.max(q, 2);

        int rStar = 0;
        double best = -1e300;
        for (int r = 0; r <= m; r++) {
            double sumTop = base - suf[r];   // sum_{i=1..r} log(1 - rho_i^2)
            double fit = -nEff * sumTop;
            int kParams = r * (p + q - r);
            double pen = scorePenaltyDiscount * kParams * Math.log(nEff);
            if (scoreEbicGamma > 0.0) pen += 2.0 * scoreEbicGamma * kParams * Math.log(Ppool);
            double sc = fit - pen;
            if (sc > best) { best = sc; rStar = r; }
        }
        return Math.max(0, rStar);
    }

    /* ==================== helpers ==================== */

    /** universe \ remove (both sets of observed indices), returning sorted int[]. */
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

    private static int[] concat(int[] a, int[] b) {
        int[] out = new int[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    /* ==================== Convenience wrapper you already call ==================== */

    public static List<Edge> findHierarchyEdges(SimpleMatrix S,
                                                int sampleSize,
                                                double alpha,
                                                int minRankDrop,
                                                int pObserved,
                                                List<List<Integer>> blocks,
                                                List<Node> metaVars) {
        return computeEdges(
                S, blocks, metaVars,
                sampleSize, alpha, minRankDrop,
                Strategy.INDICATORS, /*topKConf=*/0,
                /*verbose=*/false
        );
    }
}