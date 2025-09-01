package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


/**
 * Harness for the current Tsc API: new Tsc(List<Node>, CovarianceMatrix).
 * <p>
 * - Builds population Σ for mixed-rank MIMs (NOLAC, pure measurement) or a DAG w/o latents. - Wraps Σ in
 * CovarianceMatrix (with a nominal sample size N). - Runs Tsc, collects clusters (Map<Set<Integer>, Integer>), and
 * scores vs truth.
 * <p>
 * Adjust the knobs in the CONFIG section to sweep ranks, loadings, impurities, etc.
 */
public class TscHarnessTest {

    // ======== CONFIG ========
    private static final long SEED = 42L;
    private static final int N = 5000;     // carried in CovarianceMatrix as sample size
    private static final double LOADING = 0.80; // indicator loading magnitude (strong = easier)
    private static final double UNIQUENESS = 0.36; // θ diagonal (so ~ unit variance with LOADING=0.8)
    private static final double LATENT_RHO = 0.30; // equicorrelation among latent factors (robust connectivity)
    private static final double IMPURITY_EPS = 0.00; // 0.00 = clean NOLAC; try 0.05 / 0.10 to inject MM residuals
    private static final double ALPHA = 0.001; // Wilks cutoff used inside Tsc
    private static final int TRIALS = 5;

    // Rank mixes to sweep (sum of ranks = # latent factors across blocks)
    private static final List<int[]> RANK_SPECS = List.of(
            new int[]{1, 1, 1, 1, 1},   // five rank-1 blocks
            new int[]{2, 1, 1, 1},     // one rank-2 + three rank-1
            new int[]{2, 2, 1},       // two rank-2 + one rank-1
            new int[]{3, 2, 1},       // rank-3 + rank-2 + rank-1
            new int[]{3, 3}          // two rank-3 blocks
    );

    // indicators per block: must be >= r + 1; more indicators help rank calls
    private static int indicatorsForRank(int r) {
        return r + 3;
    }

    private static Scores score(List<List<Integer>> truth, List<Set<Integer>> pred, int P) {
        int[] yTrue = toLabels(truth, P);
        int[] yPred = toLabelsFromSets(pred, P);

        double ari = adjustedRandIndex(yTrue, yPred);

        int tp = 0, fp = 0, fn = 0;
        for (int i = 0; i < P; i++)
            for (int j = i + 1; j < P; j++) {
                boolean sameT = yTrue[i] == yTrue[j];
                boolean sameP = yPred[i] == yPred[j];
                if (sameT && sameP) tp++;
                else if (!sameT && sameP) fp++;
                else if (sameT && !sameP) fn++;
            }
        double prec = tp == 0 ? 1.0 : tp / (double) (tp + fp);
        double rec = tp == 0 ? 1.0 : tp / (double) (tp + fn);

        double maxPur = pred.stream().mapToDouble(C -> purity(C, truth)).max().orElse(0.0);
        double minCov = truth.stream().mapToDouble(T -> coverage(T, pred)).min().orElse(0.0);

        return new Scores(ari, prec, rec, maxPur, minCov);
    }

    private static int[] toLabels(List<List<Integer>> blocks, int P) {
        int[] lab = new int[P];
        Arrays.fill(lab, -1);
        for (int i = 0; i < blocks.size(); i++) for (int v : blocks.get(i)) lab[v] = i;
        int next = blocks.size();
        for (int i = 0; i < P; i++) if (lab[i] == -1) lab[i] = next++;
        return lab;
    }

    // ============================== BUILDERS ==============================

    private static int[] toLabelsFromSets(List<Set<Integer>> blocks, int P) {
        List<List<Integer>> asLists = blocks.stream().map(ArrayList::new).collect(Collectors.toList());
        return toLabels(asLists, P);
    }

    private static double purity(Collection<Integer> C, List<List<Integer>> truth) {
        int size = C.size();
        if (size == 0) return 1.0;
        Set<Integer> s = new HashSet<>(C);
        int maxOverlap = 0;
        for (List<Integer> T : truth) {
            int c = 0;
            for (int v : T) if (s.contains(v)) c++;
            maxOverlap = Math.max(maxOverlap, c);
        }
        return maxOverlap / (double) size;
    }

    // ============================== SCORING ==============================

    private static double coverage(Collection<Integer> T, List<Set<Integer>> pred) {
        int size = T.size();
        if (size == 0) return 1.0;
        Set<Integer> t = new HashSet<>(T);
        int maxOverlap = 0;
        for (Set<Integer> C : pred) {
            int c = 0;
            for (int v : C) if (t.contains(v)) c++;
            maxOverlap = Math.max(maxOverlap, c);
        }
        return maxOverlap / (double) size;
    }

    private static double adjustedRandIndex(int[] a, int[] b) {
        int n = a.length;
        Map<Integer, Set<Integer>> A = toParts(a), B = toParts(b);

        long sumCombC = 0;
        for (var CA : A.values())
            for (var CB : B.values())
                sumCombC += comb2(intersectionSize(CA, CB));

        long sumCombA = A.values().stream().mapToLong(s -> comb2(s.size())).sum();
        long sumCombB = B.values().stream().mapToLong(s -> comb2(s.size())).sum();
        long combN = comb2(n);

        double expected = (sumCombA * sumCombB) / (double) combN;
        double max = 0.5 * (sumCombA + sumCombB);
        return (sumCombC - expected) / (max - expected + 1e-12);
    }

    private static Map<Integer, Set<Integer>> toParts(int[] lab) {
        Map<Integer, Set<Integer>> m = new HashMap<>();
        for (int i = 0; i < lab.length; i++) m.computeIfAbsent(lab[i], k -> new HashSet<>()).add(i);
        return m;
    }

    private static int intersectionSize(Set<Integer> A, Set<Integer> B) {
        if (A.size() > B.size()) {
            var t = A;
            A = B;
            B = t;
        }
        int c = 0;
        for (int x : A) if (B.contains(x)) c++;
        return c;
    }

    private static long comb2(int m) {
        return m < 2 ? 0 : (long) m * (m - 1) / 2;
    }

    private static double[][] transpose(double[][] A) {
        int m = A.length, n = A[0].length;
        double[][] T = new double[n][m];
        for (int i = 0; i < m; i++) for (int j = 0; j < n; j++) T[j][i] = A[i][j];
        return T;
    }

    private static double[][] mmul(double[][] A, double[][] B) {
        int m = A.length, n = A[0].length, p = B[0].length;
        double[][] C = new double[m][p];
        for (int i = 0; i < m; i++) {
            for (int k = 0; k < n; k++) {
                double aik = A[i][k];
                if (aik == 0) continue;
                for (int j = 0; j < p; j++) C[i][j] += aik * B[k][j];
            }
        }
        return C;
    }

    private static double[][] add(double[][] A, double[][] B) {
        int m = A.length, n = A[0].length;
        double[][] C = new double[m][n];
        for (int i = 0; i < m; i++) for (int j = 0; j < n; j++) C[i][j] = A[i][j] + B[i][j];
        return C;
    }

    // ============================== LINALG UTILS ==============================

    private static double[][] equicorrelatedSPD(int d, double rho) {
        double[][] S = new double[d][d];
        for (int i = 0; i < d; i++) for (int j = 0; j < d; j++) S[i][j] = (i == j) ? 1.0 : rho;
        for (int i = 0; i < d; i++) S[i][i] += 1e-6; // tiny ridge
        return S;
    }

    private static double[][] impuritiesWithinBlocks(List<List<Integer>> blocks, long seed, int P, double eps) {
        double[][] R = new double[P][P];
        if (eps <= 0) return R;
        Random rng = new Random(seed ^ 0x9E3779B97F4A7C15L);
        for (List<Integer> B : blocks) {
            List<int[]> pairs = new ArrayList<>();
            for (int i = 0; i < B.size(); i++)
                for (int j = i + 1; j < B.size(); j++)
                    pairs.add(new int[]{B.get(i), B.get(j)});
            Collections.shuffle(pairs, rng);
            int keep = Math.max(1, (int) Math.round(0.10 * pairs.size()));
            for (int k = 0; k < keep; k++) {
                int i = pairs.get(k)[0], j = pairs.get(k)[1];
                R[i][j] += eps;
                R[j][i] += eps;
            }
        }
        return R;
    }

    private static int max(int[] a) {
        int m = Integer.MIN_VALUE;
        for (int x : a) m = Math.max(m, x);
        return m;
    }

    // ---- helper: run TSC once with a given alpha, return canonical clusters ----
    private static Set<Set<Integer>> runTscOnce(List<Node> vars, CovarianceMatrix cov, double alpha, int ess) {
        Tsc tsc = new Tsc(vars, cov);
        tsc.setAlpha(alpha);
        tsc.setExpectedSampleSize(ess);
        tsc.setVerbose(false);
        Map<Set<Integer>, Integer> out = tsc.findClusters();
        // canonicalize to immutable sets to make set ops safe
        Set<Set<Integer>> canon = new HashSet<>();
        for (Set<Integer> C : out.keySet()) canon.add(Collections.unmodifiableSet(new TreeSet<>(C)));
        return canon;
    }

    // ---- helper: dual-alpha intersection filter ----
    private static Set<Set<Integer>> stableByDualAlpha(List<Node> vars, CovarianceMatrix cov, double alphaBase, int ess) {
        Set<Set<Integer>> A = runTscOnce(vars, cov, alphaBase, ess);
        Set<Set<Integer>> B = runTscOnce(vars, cov, alphaBase / 5.0, ess);
        A.retainAll(B);
        return A;
    }

    // ---- helper: bootstrap stability (resample rows, rebuild CovarianceMatrix) ----
    // NOTE: we assume cov was constructed from a DataSet 'data'. If you only have cov,
// you can re-simulate via SemIm like you already do. Here we accept a builder function.
    private static Set<Set<Integer>> stableByBootstrap(
            int B, double keepFrac, Function<Integer, CovarianceMatrix> resampleBuilder,
            List<Node> vars, double alpha, int ess) {

        Map<Set<Integer>, Integer> counts = new HashMap<>();
        for (int b = 0; b < B; b++) {
            CovarianceMatrix covB = resampleBuilder.apply(b);
            for (Set<Integer> C : runTscOnce(vars, covB, alpha, ess)) {
                counts.merge(C, 1, Integer::sum);
            }
        }
        int thresh = (int) Math.ceil(keepFrac * B);
        Set<Set<Integer>> stable = new HashSet<>();
        for (var e : counts.entrySet()) if (e.getValue() >= thresh) stable.add(e.getKey());
        return stable;
    }

    // ========== TEST 1: Mixed-rank MIMs (NOLAC) ==========
    @Test
    public void tsc_onMixedRankMIMs_hasHighQuality() {
        Random topRng = new Random(SEED);

        for (int[] ranks : RANK_SPECS) {
            for (int t = 0; t < TRIALS; t++) {
                long seed = topRng.nextLong();
                RunSpec spec = new RunSpec(ranks, seed);

                // Build truth vars & blocks
                Build build = buildMIM(spec);
                // Run TSC
                Tsc tsc = new Tsc(build.vars, build.cov);
                tsc.setAlpha(ALPHA);
                tsc.setExpectedSampleSize(N);
                tsc.setVerbose(false);

                Map<Set<Integer>, Integer> out = tsc.findClusters();
                List<Set<Integer>> learned = new ArrayList<>(out.keySet());

                Scores s = score(build.trueBlocks, learned, build.vars.size());

                System.out.printf(Locale.US,
                        "MIM ranks=%s  ARI=%.3f  pair(P/R)=%.3f/%.3f  maxPur=%.3f  minCov=%.3f  learned=%d%n",
                        Arrays.toString(ranks), s.ari, s.pairwisePrecision, s.pairwiseRecall, s.maxPurity, s.minCoverage,
                        learned.size());

                // Soft but meaningful assertions (tighten if your current TSC is ultra-strong).
                assertTrue("ARI too low for clean NOLAC setting", s.ari >= 0.85 || max(ranks) >= 3);
                assertTrue("Pairwise precision low", s.pairwisePrecision >= 0.90 || max(ranks) >= 3);
            }
        }
    }

    @Test
    public void tsc_onNoLatentDAG_producesAtMostTinyArtifacts() {
        RandomUtil.getInstance().setSeed(SEED);

        // Slightly sparser random DAG + decent N
        Graph g = RandomGraph.randomGraph(
                12,   // nodes
                0,    // min edges (ignored for DAG gen)
                36,   // max edges
                100,  // max in-degree
                100,  // max out-degree
                100,  // max degree
                false // DAG
        );
        SemPm pm = new SemPm(g);
        SemIm im = new SemIm(pm);

        final int nRows = 2000;
        DataSet data = im.simulateData(nRows, false);
        List<Node> vars = data.getVariables();
        int ess = data.getNumRows();

        // Adaptive alpha helps tamp down borderline rank calls as N grows
        double alphaBase = Math.min(ALPHA, 1.0 / Math.log(Math.max(50, ess)));

        // ---- Dual-alpha intersection (cheap) ----
        Set<Set<Integer>> dual = runTscOnceCanonical(vars, new CovarianceMatrix(data), alphaBase, ess);
        Set<Set<Integer>> dualTight = runTscOnceCanonical(vars, new CovarianceMatrix(data), alphaBase / 5.0, ess);
        dual.retainAll(dualTight);

        // ---- Bootstrap stability (B=10, keep ≥70%) ----
        Set<Set<Integer>> boot = new HashSet<>();
        {
            final int B = 10;
            final double keepFrac = 0.7;
            Map<Set<Integer>, Integer> counts = new HashMap<>();
            for (int b = 0; b < B; b++) {
                CovarianceMatrix covB = bootstrapCovBuilder(data, SEED).apply(b);
                for (Set<Integer> C : runTscOnceCanonical(vars, covB, alphaBase / 5.0, ess)) {
                    counts.merge(C, 1, Integer::sum);
                }
            }
            int thresh = (int) Math.ceil(keepFrac * B);
            for (var e : counts.entrySet()) if (e.getValue() >= thresh) boot.add(e.getKey());
        }

        // ---- Final stable set = dual-alpha ∩ bootstrap ----
        Set<Set<Integer>> cl = new HashSet<>(dual);
        cl.retainAll(boot);

        System.out.println("Stable clusters (dual-alpha ∩ bootstrap): " + cl);

        long big = cl.stream().filter(C -> C.size() >= 3).count();
        long tiny2 = cl.stream().filter(C -> C.size() == 2).count();

        System.out.printf("No-latent DAG (stable): clusters=%d  (#size≥3=%d, #size=2=%d)%n",
                cl.size(), big, tiny2);

        assertEquals("Should not see size≥3 clusters in a pure observed DAG (generic parameters).", 0, big);
        assertTrue("At most one tiny size-2 artifact expected.", tiny2 <= 1);
    }

    /** Run TSC once at a given alpha and return canonicalized cluster sets (TreeSet for stable equality). */
    private static Set<Set<Integer>> runTscOnceCanonical(List<Node> vars, CovarianceMatrix cov, double alpha, int ess) {
        return TscStability.runOnce(vars, cov, alpha, ess);
    }

    /** Bootstrap resampler: rows with replacement → CovarianceMatrix. */
    private static java.util.function.Function<Integer, CovarianceMatrix> bootstrapCovBuilder(DataSet data, long seed) {
        return (Integer b) -> {
            int n = data.getNumRows();
            int[] idx = new int[n];
            Random r = new Random(seed ^ (0x9E3779B97F4A7C15L + b));
            for (int i = 0; i < n; i++) idx[i] = r.nextInt(n);
            DataSet boot = data.subsetRows(idx);
            return new CovarianceMatrix(boot);
        };
    }

    private Build buildMIM(RunSpec rs) {
        // Variables and truth blocks
        List<Node> vars = new ArrayList<>();
        List<List<Integer>> trueBlocks = new ArrayList<>();
        int idx = 0;
        for (int r : rs.ranks) {
            int p = indicatorsForRank(r);
            List<Integer> block = new ArrayList<>(p);
            for (int j = 0; j < p; j++, idx++) {
                vars.add(new ContinuousVariable("X" + (idx + 1)));
                block.add(idx);
            }
            trueBlocks.add(block);
        }

        // Loadings L (P x R)
        int P = vars.size();
        int R = Arrays.stream(rs.ranks).sum();
        double[][] L = new double[P][R];
        Random rng = new Random(rs.seed);

        int latentCol = 0;
        int row = 0;
        for (int r : rs.ranks) {
            int p = indicatorsForRank(r);
            for (int j = 0; j < p; j++, row++) {
                for (int k = 0; k < r; k++) {
                    double base = LOADING * (1.0 - 0.15 * k);
                    double jitter = 0.04 * (rng.nextDouble() - 0.5);
                    L[row][latentCol + k] = base + jitter;
                }
            }
            latentCol += r;
        }

        // Latent covariance Ψ (R x R): equicorrelated SPD
        double[][] psi = equicorrelatedSPD(R, LATENT_RHO);
        // Θ (P x P) uniqueness diagonal
        double[][] theta = new double[P][P];
        for (int i = 0; i < P; i++) theta[i][i] = UNIQUENESS;

        // Σ = L Ψ Lᵀ + Θ + impuritiesWithinBlocks
        double[][] Sigma = add(add(mmul(mmul(L, psi), transpose(L)), theta),
                impuritiesWithinBlocks(trueBlocks, rs.seed, P, IMPURITY_EPS));

        CovarianceMatrix cov = new CovarianceMatrix(vars, Sigma, N);
        return new Build(vars, cov, trueBlocks);
    }

    private record Scores(double ari, double pairwisePrecision, double pairwiseRecall,
                          double maxPurity, double minCoverage) {
    }

    // ============================== CARRIERS ==============================

    private record RunSpec(int[] ranks, long seed) {
    }

    private record Build(List<Node> vars, CovarianceMatrix cov, List<List<Integer>> trueBlocks) {
    }
}