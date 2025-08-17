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
 * (Optional) Specificity gate: accept a->b only if the drop with S_a (+confounders) is strictly
 * larger than the drop with confounders alone.
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
            this.fromBlock = fromBlock;
            this.toBlock = toBlock;
            this.fromName = fromName;
            this.toName = toName;
            this.r0 = r0;
            this.r1 = r1;
            this.drop = r0 - r1;
        }

        @Override public String toString() {
            return "Proposal{" + fromName + "->" + toName + ", drop=" + drop + ", r0=" + r0 + ", r1=" + r1 + "}";
        }
    }

    /* ============================== PUBLIC API ============================== */

    /** Original convenience API (kept for drop-in compatibility). */
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

    /** Original main API (kept for drop-in compatibility). */
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

    /* -------- NEW overloads with orthogonalization + specificity gate -------- */

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
                                         double ridgeScore,
                                         boolean verbose) {

        Objects.requireNonNull(S, "S");
        Objects.requireNonNull(blocks, "blocks");
        Objects.requireNonNull(metaVars, "metaVars");
        Objects.requireNonNull(strategy, "strategy");
        if (minRankDrop < 1) throw new IllegalArgumentException("minRankDrop must be >= 1");
        if (topKConf < 0) throw new IllegalArgumentException("topKConf must be >= 0");
        if (ridgeScore < 0.0) throw new IllegalArgumentException("ridgeScore must be >= 0");

        // Candidate latent indices: blocks with |block| > 1
        List<Integer> latentIdx = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i++) if (blocks.get(i).size() > 1) latentIdx.add(i);
        final int L = latentIdx.size();
        if (L <= 1) return Collections.emptyList();

        final int p = S.numCols(); // observed variables
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
                    sampleSize, alpha, minRankDrop, topKConf, orthogonalizeScores, specificityGate, ridgeScore, verbose);
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
                                                 double ridgeScore,
                                                 boolean verbose) {

        final int L = latentIdx.size();
        final double EPS = 1e-12;

        // Augment S with all scores at once: Splus is (p+L) x (p+L)
        SimpleMatrix Splus = new SimpleMatrix(p + L, p + L);
        // copy base S
        for (int i = 0; i < p; i++) for (int j = 0; j < p; j++) Splus.set(i, j, S.get(i, j));

        int[] scoreIndex = new int[L];                 // index of each score
        // build PC1 score for each block
        for (int pos = 0; pos < L; pos++) {
            int[] Ca = Cblock[pos];
            int si = p + pos;
            scoreIndex[pos] = si;

            if (Ca.length < 2) {
                // trivial score: zero correlation to observed, unit variance
                for (int j = 0; j < p; j++) { Splus.set(si, j, 0.0); Splus.set(j, si, 0.0); }
                Splus.set(si, si, 1.0);
                continue;
            }

            // PCA on S[Ca, Ca] -> first eigenvector (w) and eigenvalue (lambda1)
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
                Splus.set(si, j, r);
                Splus.set(j, si, r);
            }
            Splus.set(si, si, 1.0);
        }

        // Optional: pick top-K confounder scores per child by unconditional rank with Cb
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

                // Baseline rank on augmented S (scores live beyond p)
                int r0 = RankTests.estimateWilksRank(Splus, Cb, Y, sampleSize, alpha);
                if (r0 <= 0) continue;

                // Z_conf = confounder score indices for this child (exclude aPos if present)
                int finalAPos = aPos;
                int[] Zconf = Arrays.stream(confParents[bPos])
                        .filter(cp -> cp != finalAPos)
                        .map(cp -> p + cp)
                        .toArray();

                // (Optional) Specificity baseline: r_confOnly = rank(Cb, Y | Z_conf)
                int rConfOnly = -1;
                if (specificityGate && Zconf.length > 0) {
                    rConfOnly = RankTests.estimateWilksRankConditioned(Splus, Cb, Y, Zconf, sampleSize, alpha);
                }

                // Z = {Sa} ∪ Z_conf, possibly with Sa orthogonalized against Z_conf
                int Sa = p + aPos;

                if (orthogonalizeScores && Zconf.length > 0) {
                    // Make Sa ⟂ Z_conf in the augmented correlation (residualize Sa on Z_conf).
                    SimpleMatrix inv = invR_scsc.get(bPos);
                    if (inv == null) {
                        SimpleMatrix Rscsc = new SimpleMatrix(Zconf.length, Zconf.length);
                        for (int i = 0; i < Zconf.length; i++)
                            for (int j = 0; j < Zconf.length; j++)
                                Rscsc.set(i, j, Splus.get(Zconf[i], Zconf[j]));
//                        double ridgeScore = 1e-6;
                        if (ridgeScore > 0) {
                            for (int i = 0; i < Zconf.length; i++) {
                                Rscsc.set(i, i, Rscsc.get(i, i) + ridgeScore);
                            }
                        }
                        inv = Rscsc.invert();
                        invR_scsc.put(bPos, inv);
                    }

                    // temp = r_{Sa, Zconf} * inv(R_{Zconf,Zconf})   (1 x k)
                    SimpleMatrix rSaZ = new SimpleMatrix(1, Zconf.length);
                    for (int i = 0; i < Zconf.length; i++) rSaZ.set(0, i, Splus.get(Sa, Zconf[i]));
                    SimpleMatrix temp = rSaZ.mult(inv); // 1 x k

                    // For every j in 0..p+L-1: r'_Sa,j = r_Sa,j - temp * r_{Zconf, j}
                    int dim = p + L;
                    for (int j = 0; j < dim; j++) {
                        // gather r_{Zconf, j}
                        SimpleMatrix rZj = new SimpleMatrix(Zconf.length, 1);
                        for (int i = 0; i < Zconf.length; i++) rZj.set(i, 0, Splus.get(Zconf[i], j));
                        double proj = temp.mult(rZj).get(0, 0);
                        double rNew = Splus.get(Sa, j) - proj;
                        Splus.set(Sa, j, rNew);
                        Splus.set(j, Sa, rNew);
                    }
                    // Force Sa ⟂ Z_conf exactly and keep var(Sa)=1
                    for (int idx : Zconf) { Splus.set(Sa, idx, 0.0); Splus.set(idx, Sa, 0.0); }
                    Splus.set(Sa, Sa, 1.0);
                }

                // Build Z = {Sa} ∪ Z_conf
                int[] Z = new int[1 + Zconf.length];
                Z[0] = Sa;
                System.arraycopy(Zconf, 0, Z, 1, Zconf.length);

                int r1 = RankTests.estimateWilksRankConditioned(Splus, Cb, Y, Z, sampleSize, alpha);
                int drop = r0 - r1;

                // (Optional) Specificity gate
                if (specificityGate && Zconf.length > 0) {
                    if (rConfOnly == -1) {
                        rConfOnly = RankTests.estimateWilksRankConditioned(Splus, Cb, Y, Zconf, sampleSize, alpha);
                    }
                    if (!(r1 < rConfOnly)) {
                        if (verbose) {
                            System.out.printf("skip(PC1) %s -> %s : r0=%d, r1=%d, drop=%d, rConf=%d%n",
                                    metaVars.get(latentIdx.get(aPos)).getName(),
                                    metaVars.get(latentIdx.get(bPos)).getName(),
                                    r0, r1, drop, rConfOnly);
                        }
                        continue;
                    }
                }

                if (drop >= minRankDrop) {
                    Proposal pr = new Proposal(
                            latentIdx.get(aPos), latentIdx.get(bPos),
                            metaVars.get(latentIdx.get(aPos)).getName(),
                            metaVars.get(latentIdx.get(bPos)).getName(),
                            r0, r1
                    );
                    props.add(pr);
                    if (verbose) {
                        System.out.printf("hier(PC1) %s -> %s : r0=%d, r1=%d, drop=%d%n",
                                pr.fromName, pr.toName, pr.r0, pr.r1, pr.drop);
                    }
                } else if (verbose) {
                    System.out.printf("skip(PC1) %s -> %s : r0=%d, r1=%d, drop=%d%n",
                            metaVars.get(latentIdx.get(aPos)).getName(),
                            metaVars.get(latentIdx.get(bPos)).getName(),
                            r0, r1, drop);
                }
            }
        }

        // Sort proposals: biggest drop first, then names for determinism
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

                // IMPORTANT: make Y disjoint from Z: use Y = (V \ Cb) \ Ca
                int[] Db = minus(all, Cb);
                int[] Y  = minus(Db, Ca);
                if (Y.length == 0) continue;

                int r0 = RankTests.estimateWilksRank(S, Cb, Y, sampleSize, alpha);
                if (r0 <= 0) continue;

                int r1 = RankTests.estimateWilksRankConditioned(S, Cb, Y, Ca, sampleSize, alpha);
                int drop = r0 - r1;

                if (drop >= minRankDrop) {
                    Proposal pr = new Proposal(ia, ib, metaVars.get(ia).getName(), metaVars.get(ib).getName(), r0, r1);
                    props.add(pr);
                    if (verbose) {
                        System.out.printf("hier(IND) %s -> %s : r0=%d, r1=%d, drop=%d%n",
                                pr.fromName, pr.toName, pr.r0, pr.r1, pr.drop);
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

    /* ==================== NEW: the drop-in wrapper you’re calling ==================== */

    /**
     * Convenience wrapper used by TscPc.search(...).
     * Defaults to Strategy.INDICATORS, topKConf=0, verbose=false.
     *
     * @param S            correlation/covariance of observed variables
     * @param sampleSize   effective N
     * @param alpha        test alpha for Wilks-rank decisions
     * @param minRankDrop  minimum drop r0 - r1 to accept La -> Lb
     * @param pObserved    number of observed variables (redundant with S.numCols(), kept for compatibility)
     * @param blocks       list of blocks (each block is list of observed indices); only blocks with size>1 are treated as latents
     * @param metaVars     meta variables corresponding 1-1 with blocks
     * @return list of directed edges La -> Lb to add
     */
    public static List<Edge> findHierarchyEdges(SimpleMatrix S,
                                                int sampleSize,
                                                double alpha,
                                                int minRankDrop,
                                                int pObserved,
                                                List<List<Integer>> blocks,
                                                List<Node> metaVars) {
        // sanity (no hard failure if mismatch; just rely on S)
        // if (S.numCols() != pObserved) System.err.println("Warning: pObserved != S.numCols()");
        return computeEdges(
                S, blocks, metaVars,
                sampleSize, alpha, minRankDrop,
                Strategy.INDICATORS, /*topKConf=*/0,
                /*verbose=*/false
        );
    }
}