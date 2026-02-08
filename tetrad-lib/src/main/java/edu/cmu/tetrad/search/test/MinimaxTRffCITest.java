package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.util.TetradLogger;
import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.factory.DecompositionFactory_DDRM;
import org.ejml.interfaces.decomposition.CholeskyDecomposition_F64;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.Math.*;

/**
 * <p><b>Minimax-t RFF Conditional Independence Test (mixed)</b></p>
 *
 * <p>
 * Tests X ⟂⟂ Y | Z by comparing two robust conditional models for Y:
 * </p>
 * <ul>
 *   <li>Null:    Y ~ f(Z)</li>
 *   <li>Alt:     Y ~ f(Z, X)</li>
 * </ul>
 *
 * <p>
 * Continuous Y: Student-t location model with RFF for continuous parents + one-hot for discrete parents,
 * fit by IRLS ridge regression (robust to heavy-tailed residuals).
 * </p>
 *
 * <p>
 * Discrete Y: Multinomial logistic ridge regression with the same feature map, fit by IRLS.
 * </p>
 *
 * <p><b>Minimax calibration.</b>
 * P-values are computed by stratified permutation:
 * stratify on Z (quantile bins for continuous Z, exact match for discrete Z),
 * and permute Y within each stratum.
 * </p>
 *
 * <p>
 * Missing rows in any of {X,Y,Z} are dropped per test.
 * </p>
 *
 * <p><b>Speedups (v2).</b></p>
 * <ul>
 *   <li>Reuses Z-strata via cache.</li>
 *   <li>Caches designs (Phi matrices) for (child, parents, rows, RFF knobs).</li>
 *   <li>Continuous-Y path: warm-start IRLS across permutations and optionally "freeze" Student-t weights.</li>
 *   <li>Optional early stopping in permutation loop based on bounds vs alpha.</li>
 * </ul>
 */
public final class MinimaxTRffCITest implements IndependenceTest, RowsSettable {

    // ---------------- data ----------------
    private final DataSet data;
    private final List<Node> variables;
    private final Map<String, Integer> indexMap;

    // global z-scored continuous columns (NaNs preserved). Discrete vars are NaN-filled.
    private final double[][] zCols;

    // optional row restriction
    private List<Integer> rows = null;

    // cache for Z strata: (Z signature + useRows signature + knobs) -> groups of indices in useRows-space
    private final ConcurrentHashMap<StrataKey, int[][]> strataCache = new ConcurrentHashMap<>();

    // cache for design matrices: (child + parents + useRows signature + RFF knobs) -> Phi
    private final ConcurrentHashMap<DesignKey, DesignMap> designCache = new ConcurrentHashMap<>();

    // ---------------- knobs ----------------
    private double alpha = 0.01;

    // permutation
    private int permutations = 200;
    private long permSeed = 1L;

    // stratification on Z
    private int binsPerContZ = 6;
    private int minStratumSize = 6;

    // model knobs
    private double ridge = 1e-3;
    private double nu = 5.0;       // Student-t df (continuous Y)
    private double scale = 1.0;    // Student-t scale (reasonable if globally z-scored)

    // RFF
    private int rffFeatures = 256;
    private double rffSigma = 1.0;
    private long rffSeed = 1L;

    // IRLS
    private int irlsIters = 8;
    private int permIrlsIters = 2;        // warm-start iterations used for permutations (continuous-Y path)
    private double irlsTol = 1e-6;

    // fastest mode for continuous Y: freeze Student-t weights from observed fit for permutations
    private boolean freezeTWeightsInPermutations = true;

    // optional early stopping vs alpha
    private boolean earlyStopPermutations = true;

    // behavior
    private boolean verbose = false;

    public MinimaxTRffCITest(DataSet data, double alpha) {
        if (data == null) throw new NullPointerException("data");
        this.data = data;
        this.variables = Collections.unmodifiableList(new ArrayList<>(data.getVariables()));
        this.indexMap = indexMap(this.variables);
        setAlpha(alpha);

        int p = variables.size();
        int n = data.getNumRows();

        double[][] raw = new double[p][n];
        for (int j = 0; j < p; j++) {
            if (isDiscrete(j)) {
                Arrays.fill(raw[j], Double.NaN);
            } else {
                for (int r = 0; r < n; r++) raw[j][r] = data.getDouble(r, j);
            }
        }

        this.zCols = new double[p][n];
        for (int j = 0; j < p; j++) {
            if (isDiscrete(j)) Arrays.fill(zCols[j], Double.NaN);
            else zscoreColumnPreserveNaN(raw[j], zCols[j]);
        }
    }

    // =========================================================
    // IndependenceTest
    // =========================================================

    @Override
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z) {
        double p = getPValue(x, y, z);
        boolean indep = p > alpha;

        IndependenceResult r = new IndependenceResult(
                new IndependenceFact(x, y, z),
                indep,
                p,
                alpha - p
        );

        if (verbose && indep) {
            TetradLogger.getInstance().log(LogUtilsSearch.independenceFactMsg(x, y, z, p));
        }
        return r;
    }

    public double getPValue(Node x, Node y, Set<Node> z) {
        Objects.requireNonNull(x);
        Objects.requireNonNull(y);
        Objects.requireNonNull(z);
        if (x.equals(y)) return 1.0;

        int ix = idx(x);
        int iy = idx(y);
        int[] iz = idxSorted(z);

        // drop rows with missing in {X,Y,Z}
        List<Integer> baseRows = listRows();
        int[] useRows = rowsCompleteFor(ix, iy, iz, baseRows);
        int n = useRows.length;

        if (n < 20) return 0.0; // conservative guard

        int[][] strata = getStrata(iz, useRows);
        if (strata.length == 0) return 0.0;

        long seed = permSeed
                ^ (long) ix * 0x9E3779B97F4A7C15L
                ^ (long) iy * 0xC2B2AE3D27D4EB4FL
                ^ Arrays.hashCode(iz);

        // build fixed design matrices for null/alt (Phi depends only on parents)
        // Null parents: Z
        // Alt parents:  Z plus X
        int[] parentsNull = iz;
        int[] parentsAlt = append(iz, ix);
        Arrays.sort(parentsAlt);

        long rowsSig = signature(useRows);

        DesignMap dmNull = getOrBuildDesign(parentsNull, useRows, iy, rowsSig);
        DesignMap dmAlt  = getOrBuildDesign(parentsAlt,  useRows, iy, rowsSig);
        if (dmNull == null || dmAlt == null) return 0.0;

        // extract Y (observed) in useRows-space
        if (isDiscrete(iy)) {
            int K = numCategories(iy);
            if (K < 2) return 1.0;

            int[] yObs = extractDiscreteY(iy, useRows);

            double ll0 = fitMultinomialLogitLL(yObs, K, dmNull.Phi);
            double ll1 = fitMultinomialLogitLL(yObs, K, dmAlt.Phi);
            if (!Double.isFinite(ll0) || !Double.isFinite(ll1)) return 0.0;

            double tObs = 2.0 * (ll1 - ll0);
            if (!Double.isFinite(tObs)) return 0.0;

            // permutation: shuffle Y within strata
            SplittableRandom rng = new SplittableRandom(seed);
            int B = Math.max(50, permutations);

            int ge = 0;
            int valid = 0;
            int[] yPerm = Arrays.copyOf(yObs, n);

            for (int b = 0; b < B; b++) {
                System.arraycopy(yObs, 0, yPerm, 0, n);
                shuffleWithinStrataInt(yPerm, strata, rng);

                double pll0 = fitMultinomialLogitLL(yPerm, K, dmNull.Phi);
                double pll1 = fitMultinomialLogitLL(yPerm, K, dmAlt.Phi);
                if (!Double.isFinite(pll0) || !Double.isFinite(pll1)) continue;

                double t = 2.0 * (pll1 - pll0);
                if (!Double.isFinite(t)) continue;

                valid++;
                if (t >= tObs) ge++;

                if (earlyStopPermutations && valid >= 10) {
                    double lo = (ge + 1.0) / (valid + 1.0);
                    double hi = (ge + (B - (b + 1)) + 1.0) / (B + 1.0);
                    if (hi < alpha || lo > alpha) break;
                }
            }

            if (valid == 0) return 1.0;
            return (ge + 1.0) / (valid + 1.0);

        } else {
            // continuous Y
            if (!(nu > 2) || !Double.isFinite(nu)) return 0.0;
            if (!(scale > 0) || !Double.isFinite(scale)) return 0.0;

            double[] yObs = extractContinuousY(iy, useRows);
            centerInPlace(yObs);

            final int M0 = dmNull.Phi[0].length;
            final int M1 = dmAlt.Phi[0].length;

            StudentTWorkspace wk0 = new StudentTWorkspace(n, M0);
            StudentTWorkspace wk1 = new StudentTWorkspace(n, M1);

            // observed full IRLS
            StudentTFit fit0 = fitStudentTRidgeLLWarm(yObs, dmNull.Phi, wk0, null, null, irlsIters);
            StudentTFit fit1 = fitStudentTRidgeLLWarm(yObs, dmAlt.Phi,  wk1, null, null, irlsIters);

            if (!Double.isFinite(fit0.ll) || !Double.isFinite(fit1.ll)) return 0.0;

            double tObs = 2.0 * (fit1.ll - fit0.ll);
            if (!Double.isFinite(tObs)) return 0.0;

            // warm starts
            double[] beta0Warm = Arrays.copyOf(fit0.beta, M0);
            double[] beta1Warm = Arrays.copyOf(fit1.beta, M1);
            double[] w0Warm = Arrays.copyOf(fit0.w, n);
            double[] w1Warm = Arrays.copyOf(fit1.w, n);

            SplittableRandom rng = new SplittableRandom(seed);
            int B = Math.max(50, permutations);

            int ge = 0;
            int valid = 0;
            double[] yPerm = Arrays.copyOf(yObs, n);

            for (int b = 0; b < B; b++) {
                System.arraycopy(yObs, 0, yPerm, 0, n);
                shuffleWithinStrataDouble(yPerm, strata, rng);

                int it0 = freezeTWeightsInPermutations ? 0 : permIrlsIters;
                int it1 = freezeTWeightsInPermutations ? 0 : permIrlsIters;

                StudentTFit p0 = fitStudentTRidgeLLWarm(yPerm, dmNull.Phi, wk0, beta0Warm, w0Warm, it0);
                StudentTFit p1 = fitStudentTRidgeLLWarm(yPerm, dmAlt.Phi,  wk1, beta1Warm, w1Warm, it1);
                if (!Double.isFinite(p0.ll) || !Double.isFinite(p1.ll)) continue;

                double t = 2.0 * (p1.ll - p0.ll);
                if (!Double.isFinite(t)) continue;

                valid++;
                if (t >= tObs) ge++;

                if (earlyStopPermutations && valid >= 10) {
                    double lo = (ge + 1.0) / (valid + 1.0);
                    double hi = (ge + (B - (b + 1)) + 1.0) / (B + 1.0);
                    if (hi < alpha || lo > alpha) break;
                }
            }

            if (valid == 0) return 1.0;
            return (ge + 1.0) / (valid + 1.0);
        }
    }

    // =========================================================
    // Design map: Phi matrix for parents (RFF(cont) + OneHot(disc) + intercept)
    // =========================================================

    private DesignMap getOrBuildDesign(int[] parents, int[] useRows, int childIndexForSeed, long rowsSig) {
        DesignKey key = new DesignKey(childIndexForSeed, parents, rowsSig, rffFeatures, rffSigma, rffSeed);
        return designCache.computeIfAbsent(key, k -> buildDesign(parents, useRows, childIndexForSeed));
    }

    private DesignMap buildDesign(int[] parents, int[] useRows, int childIndexForSeed) {
        int[] cont = filterContinuous(parents);
        int[] disc = filterDiscrete(parents);

        OneHotSpec oh = buildOneHotSpec(disc);

        int n = useRows.length;
        int D = rffFeatures;
        int Q = oh.totalCols;
        int M = 1 + D + Q;

        // deterministic seed per (child + parents)
        long seed = rffSeed ^ (long) childIndexForSeed * 0x9E3779B97F4A7C15L ^ Arrays.hashCode(parents);

        // RFF params
        Random rng = new Random(seed);
        int dCont = Math.max(1, cont.length);
        double[][] W = new double[D][dCont];
        for (int k = 0; k < D; k++) for (int j = 0; j < dCont; j++) W[k][j] = rng.nextGaussian() / rffSigma;
        double[] phase = new double[D];
        for (int k = 0; k < D; k++) phase[k] = 2.0 * PI * rng.nextDouble();
        double phiScale = sqrt(2.0 / D);

        double[][] Phi = new double[n][M];
        double[] rowPhi = new double[M];

        for (int i = 0; i < n; i++) {
            int dataRow = useRows[i];
            buildPhiRow(rowPhi, dataRow, cont, W, phase, phiScale, oh, disc);
            System.arraycopy(rowPhi, 0, Phi[i], 0, M);
        }

        return new DesignMap(Phi);
    }

    private void buildPhiRow(double[] out,
                             int dataRow,
                             int[] contParents,
                             double[][] W, double[] phase, double phiScale,
                             OneHotSpec oh, int[] discParents) {

        out[0] = 1.0;

        // RFF block at [1 .. 1 + D - 1]
        int rffOff = 1;
        int dCont = contParents.length;
        if (dCont == 0) {
            Arrays.fill(out, rffOff, rffOff + rffFeatures, 0.0);
        } else {
            for (int k = 0; k < rffFeatures; k++) {
                double dot = 0.0;
                double[] wk = W[k];
                for (int j = 0; j < dCont; j++) dot += wk[j] * zCols[contParents[j]][dataRow];
                out[rffOff + k] = phiScale * cos(dot + phase[k]);
            }
        }

        // One-hot block at [1 + D .. M-1]
        int ohOff = 1 + rffFeatures;
        Arrays.fill(out, ohOff, out.length, 0.0);

        if (discParents.length == 0) return;

        for (int t = 0; t < discParents.length; t++) {
            int var = discParents[t];
            int lev = data.getInt(dataRow, var);
            if (lev == DiscreteVariable.MISSING_VALUE) continue;

            // baseline dropped
            if (lev <= 0) continue;

            int col = oh.offsets[t] + (lev - 1);
            if (col >= oh.offsets[t] && col < oh.offsets[t] + oh.sizes[t] - 1) {
                out[ohOff + col] = 1.0;
            }
        }
    }

    private OneHotSpec buildOneHotSpec(int[] discParents) {
        int m = discParents.length;
        int[] sizes = new int[m];
        int[] offsets = new int[m];
        int off = 0;
        for (int t = 0; t < m; t++) {
            int var = discParents[t];
            int K = numCategories(var);
            sizes[t] = K;
            offsets[t] = off;
            off += max(0, K - 1);
        }
        return new OneHotSpec(sizes, offsets, off);
    }

    private record DesignMap(double[][] Phi) {}

    // =========================================================
    // Robust fitting: Continuous Y (Student-t IRLS ridge), warm-started
    // =========================================================

    private static final class StudentTWorkspace {
        final int n, M;
        final DMatrixRMaj G;
        final double[] v;
        final double[] beta;
        final double[] w;
        final double[] yhat;
        final CholeskyDecomposition_F64<DMatrixRMaj> chol;

        // Reusable L factor to avoid allocating in chol.getT(null)
        final DMatrixRMaj L;

        StudentTWorkspace(int n, int M) {
            this.n = n;
            this.M = M;
            this.G = new DMatrixRMaj(M, M);
            this.v = new double[M];
            this.beta = new double[M];
            this.w = new double[n];
            this.yhat = new double[n];
            this.chol = DecompositionFactory_DDRM.chol(true);
            this.L = new DMatrixRMaj(M, M);
        }

        void resetNormalEq() {
            Arrays.fill(G.data, 0.0);
            Arrays.fill(v, 0.0);
        }
    }

    private static final class StudentTFit {
        final double ll;
        final double[] beta;
        final double[] w;
        StudentTFit(double ll, double[] beta, double[] w) {
            this.ll = ll;
            this.beta = beta;
            this.w = w;
        }
    }

    /**
     * Warm-start Student-t IRLS:
     * - betaInit and wInit may be provided (copied into workspace).
     * - iters = 0 performs a single weighted ridge solve with weights fixed at wInit (frozen-weight mode).
     */
    private StudentTFit fitStudentTRidgeLLWarm(
            double[] yCentered,
            double[][] Phi,
            StudentTWorkspace wk,
            double[] betaInit,
            double[] wInit,
            int iters
    ) {
        if (!(ridge > 0) || !Double.isFinite(ridge)) return new StudentTFit(Double.NaN, null, null);

        final int n = wk.n;
        final int M = wk.M;

        if (betaInit != null) System.arraycopy(betaInit, 0, wk.beta, 0, M);
        else Arrays.fill(wk.beta, 0.0);

        if (wInit != null) System.arraycopy(wInit, 0, wk.w, 0, n);
        else Arrays.fill(wk.w, 1.0);

        // weighted ridge solve given current wk.w
        final Runnable solveOnce = () -> {
            wk.resetNormalEq();

            // Build normal equations (lower triangle)
            for (int i = 0; i < n; i++) {
                final double wi = wk.w[i];
                final double yi = yCentered[i];
                final double[] phi = Phi[i];

                for (int a = 0; a < M; a++) wk.v[a] += wi * phi[a] * yi;

                for (int a = 0; a < M; a++) {
                    final double pa = wi * phi[a];
                    final int rowOff = a * M;
                    for (int b = 0; b <= a; b++) wk.G.data[rowOff + b] += pa * phi[b];
                }
            }

            // Symmetrize + ridge (no intercept penalty)
            for (int a = 0; a < M; a++) {
                final int rowOffA = a * M;
                for (int b = 0; b < a; b++) wk.G.data[b * M + a] = wk.G.data[rowOffA + b];
            }
            for (int a = 1; a < M; a++) wk.G.data[a * M + a] += ridge;

            if (!wk.chol.decompose(wk.G)) {
                Arrays.fill(wk.beta, Double.NaN);
                return;
            }

            wk.chol.getT(wk.L);
            double[] sol = solveFromCholeskyLower(wk.L, wk.v);
            System.arraycopy(sol, 0, wk.beta, 0, M);
        };

        // frozen-weight mode
        if (iters <= 0) {
            solveOnce.run();
            if (!Double.isFinite(wk.beta[0])) return new StudentTFit(Double.NaN, wk.beta, wk.w);
            predictInto(Phi, wk.beta, wk.yhat);
            double ll = studentTLogLik(yCentered, wk.yhat, nu, scale);
            return new StudentTFit(ll, wk.beta, wk.w);
        }

        double prevObj = Double.POSITIVE_INFINITY;

        for (int iter = 0; iter < iters; iter++) {
            solveOnce.run();
            if (!Double.isFinite(wk.beta[0])) return new StudentTFit(Double.NaN, wk.beta, wk.w);

            double obj = 0.0;

            for (int i = 0; i < n; i++) {
                final double[] phi = Phi[i];
                double yhat = 0.0;
                for (int a = 0; a < M; a++) yhat += phi[a] * wk.beta[a];

                final double r = yCentered[i] - yhat;
                final double u2 = (r / scale) * (r / scale);

                wk.w[i] = (nu + 1.0) / (nu + u2);
                obj += 0.5 * (nu + 1.0) * log1p(u2 / nu);
            }

            if (abs(prevObj - obj) <= irlsTol * (1.0 + abs(prevObj))) break;
            prevObj = obj;
        }

        predictInto(Phi, wk.beta, wk.yhat);
        double ll = studentTLogLik(yCentered, wk.yhat, nu, scale);
        return new StudentTFit(ll, wk.beta, wk.w);
    }

    private static void predictInto(double[][] Phi, double[] beta, double[] outYhat) {
        final int n = Phi.length;
        final int M = beta.length;
        for (int i = 0; i < n; i++) {
            final double[] phi = Phi[i];
            double s = 0.0;
            for (int a = 0; a < M; a++) s += phi[a] * beta[a];
            outYhat[i] = s;
        }
    }

    // =========================================================
    // Robust fitting: Discrete Y (Multinomial logistic IRLS ridge)
    // =========================================================

    private double fitMultinomialLogitLL(int[] y, int K, double[][] Phi) {
        if (!(ridge > 0) || !Double.isFinite(ridge)) return Double.NaN;

        final int n = y.length;
        final int M = Phi[0].length;
        final int C = K - 1;

        // beta: M x C
        final double[][] beta = new double[M][C];
        final double[] logits = new double[K];

        double prevObj = Double.POSITIVE_INFINITY;

        for (int iter = 0; iter < irlsIters; iter++) {

            final double[][] probs = softmaxProbsFromPhi(K, n, beta, Phi, logits);

            for (int c = 0; c < C; c++) {
                final DMatrixRMaj G = new DMatrixRMaj(M, M);
                final double[] v = new double[M];

                for (int i = 0; i < n; i++) {
                    final double[] phi = Phi[i];

                    final double pc = probs[i][c + 1];
                    double wc = pc * (1.0 - pc);
                    wc = Math.max(wc, 1e-10);

                    double eta = 0.0;
                    for (int a = 0; a < M; a++) eta += phi[a] * beta[a][c];

                    final double yc = (y[i] == (c + 1)) ? 1.0 : 0.0;
                    final double z = eta + (yc - pc) / wc;

                    final double wz = wc * z;
                    for (int a = 0; a < M; a++) v[a] += wz * phi[a];

                    for (int a = 0; a < M; a++) {
                        final double pa = wc * phi[a];
                        for (int b = 0; b <= a; b++) G.add(a, b, pa * phi[b]);
                    }
                }

                for (int a = 0; a < M; a++) for (int b = 0; b < a; b++) G.set(b, a, G.get(a, b));
                for (int a = 1; a < M; a++) G.add(a, a, ridge);

                final CholeskyDecomposition_F64<DMatrixRMaj> chol = DecompositionFactory_DDRM.chol(true);
                if (!chol.decompose(G)) return Double.NaN;
                final DMatrixRMaj L = chol.getT(null);

                final double[] bc = solveFromCholeskyLower(L, v);
                for (int a = 0; a < M; a++) beta[a][c] = bc[a];
            }

            final double ll = multinomialLogLikFromPhi(y, K, n, beta, Phi, logits);
            final double obj = -ll;

            if (abs(prevObj - obj) <= irlsTol * (1.0 + abs(prevObj))) break;
            prevObj = obj;
        }

        return multinomialLogLikFromPhi(y, K, n, beta, Phi, logits);
    }

    // =========================================================
    // Z strata: quantile bins (cont Z) + exact match (disc Z)
    // groups are indices in useRows-space
    // =========================================================

    private int[][] getStrata(int[] zIdx, int[] useRows) {
        if (zIdx.length == 0) return new int[][]{range(useRows.length)};

        long rowsSig = signature(useRows);
        StrataKey key = new StrataKey(zIdx, rowsSig, binsPerContZ, minStratumSize);
        return strataCache.computeIfAbsent(key, kk -> buildStrata(zIdx, useRows, binsPerContZ, minStratumSize));
    }

    private int[][] buildStrata(int[] zIdx, int[] useRows, int binsPerCont, int minSize) {
        int n = useRows.length;

        int[] zDisc = filterDiscrete(zIdx);
        int[] zCont = filterContinuous(zIdx);

        double[][] edges = new double[zCont.length][];
        for (int j = 0; j < zCont.length; j++) {
            double[] vals = new double[n];
            for (int i = 0; i < n; i++) vals[i] = zCols[zCont[j]][useRows[i]];
            edges[j] = quantileEdges(vals, binsPerCont);
        }

        HashMap<StratumSignature, IntArrayList> buckets = new HashMap<>();

        for (int i = 0; i < n; i++) {
            int row = useRows[i];

            int[] parts = new int[zDisc.length + zCont.length];
            int k = 0;

            for (int v : zDisc) parts[k++] = data.getInt(row, v);
            for (int j = 0; j < zCont.length; j++) parts[k++] = binIndex(edges[j], zCols[zCont[j]][row]);

            StratumSignature sig = new StratumSignature(parts);
            buckets.computeIfAbsent(sig, s -> new IntArrayList()).add(i);
        }

        ArrayList<int[]> groups = new ArrayList<>();
        for (IntArrayList lst : buckets.values()) {
            if (lst.size() >= minSize) groups.add(lst.toArray());
        }

        return groups.toArray(new int[0][]);
    }

    // =========================================================
    // Missingness / row filtering
    // =========================================================

    private int[] rowsCompleteFor(int ix, int iy, int[] iz, List<Integer> baseRows) {
        int[] tmp = new int[baseRows.size()];
        int m = 0;

        outer:
        for (int r : baseRows) {
            if (!isValuePresent(r, ix)) continue;
            if (!isValuePresent(r, iy)) continue;
            for (int j : iz) if (!isValuePresent(r, j)) continue outer;
            tmp[m++] = r;
        }

        return Arrays.copyOf(tmp, m);
    }

    private boolean isValuePresent(int row, int col) {
        if (isDiscrete(col)) {
            return data.getInt(row, col) != DiscreteVariable.MISSING_VALUE;
        } else {
            return !Double.isNaN(data.getDouble(row, col));
        }
    }

    // =========================================================
    // Permutation helpers: shuffle Y within each stratum
    // =========================================================

    private static void shuffleWithinStrataDouble(double[] y, int[][] strata, SplittableRandom rng) {
        for (int[] g : strata) {
            for (int i = g.length - 1; i > 0; i--) {
                int j = rng.nextInt(i + 1);
                int ii = g[i], jj = g[j];
                double tmp = y[ii];
                y[ii] = y[jj];
                y[jj] = tmp;
            }
        }
    }

    private static void shuffleWithinStrataInt(int[] y, int[][] strata, SplittableRandom rng) {
        for (int[] g : strata) {
            for (int i = g.length - 1; i > 0; i--) {
                int j = rng.nextInt(i + 1);
                int ii = g[i], jj = g[j];
                int tmp = y[ii];
                y[ii] = y[jj];
                y[jj] = tmp;
            }
        }
    }

    // =========================================================
    // Extraction
    // =========================================================

    private double[] extractContinuousY(int yIndex, int[] useRows) {
        int n = useRows.length;
        double[] y = new double[n];
        for (int i = 0; i < n; i++) y[i] = zCols[yIndex][useRows[i]];
        return y;
    }

    private int[] extractDiscreteY(int yIndex, int[] useRows) {
        int n = useRows.length;
        int[] y = new int[n];
        for (int i = 0; i < n; i++) y[i] = data.getInt(useRows[i], yIndex);
        return y;
    }

    // =========================================================
    // Public API / setters / interface
    // =========================================================

    @Override
    public List<Node> getVariables() {
        return variables;
    }

    @Override
    public DataSet getData() {
        return data;
    }

    @Override
    public List<DataSet> getDataSets() {
        return List.of(data);
    }

    @Override
    public double getAlpha() {
        return alpha;
    }

    public void setAlpha(double alpha) {
        if (alpha < 0 || alpha > 1) throw new IllegalArgumentException("alpha must be in [0,1]");
        this.alpha = alpha;
    }

    @Override
    public int getSampleSize() {
        return data.getNumRows();
    }

    @Override
    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public void setPermutations(int B) {
        this.permutations = Math.max(50, B);
    }

    public void setPermSeed(long s) {
        this.permSeed = s;
    }

    public void setBinsPerContZ(int b) {
        this.binsPerContZ = Math.max(2, b);
        strataCache.clear();
        // design cache is safe to keep; it depends on rowsSig + rff knobs, not binsPerContZ
    }

    public void setMinStratumSize(int m) {
        this.minStratumSize = Math.max(2, m);
        strataCache.clear();
    }

    public void setRidge(double ridge) {
        if (!(ridge > 0) || !Double.isFinite(ridge)) throw new IllegalArgumentException("ridge must be finite and > 0");
        this.ridge = ridge;
    }

    public void setNu(double nu) {
        if (!(nu > 2) || !Double.isFinite(nu)) throw new IllegalArgumentException("nu must be finite and > 2");
        this.nu = nu;
    }

    public void setScale(double scale) {
        if (!(scale > 0) || !Double.isFinite(scale)) throw new IllegalArgumentException("scale must be finite and > 0");
        this.scale = scale;
    }

    public void setRffFeatures(int d) {
        if (d < 16) throw new IllegalArgumentException("rffFeatures should be >= 16");
        this.rffFeatures = d;
        designCache.clear();
    }

    public void setRffSigma(double sigma) {
        if (!(sigma > 0) || !Double.isFinite(sigma)) throw new IllegalArgumentException("rffSigma must be finite and > 0");
        this.rffSigma = sigma;
        designCache.clear();
    }

    public void setRffSeed(long seed) {
        this.rffSeed = seed;
        designCache.clear();
    }

    public void setIrlsIters(int iters) {
        this.irlsIters = Math.max(1, iters);
    }

    public void setPermIrlsIters(int iters) {
        this.permIrlsIters = Math.max(0, iters);
    }

    public void setFreezeTWeightsInPermutations(boolean freeze) {
        this.freezeTWeightsInPermutations = freeze;
    }

    public void setEarlyStopPermutations(boolean earlyStopPermutations) {
        this.earlyStopPermutations = earlyStopPermutations;
    }

    public void setIrlsTol(double tol) {
        this.irlsTol = Math.max(0.0, tol);
    }

    @Override
    public List<Integer> getRows() {
        return rows;
    }

    @Override
    public void setRows(List<Integer> rows) {
        if (rows == null) {
            this.rows = null;
            strataCache.clear();
            designCache.clear();
            return;
        }
        for (int i = 0; i < rows.size(); i++) {
            Integer r = rows.get(i);
            if (r == null) throw new NullPointerException("Row " + i + " is null.");
            if (r < 0) throw new IllegalArgumentException("Row " + i + " is negative.");
            if (r >= data.getNumRows()) throw new IllegalArgumentException("Row " + i + " out of bounds: " + r);
        }
        this.rows = new ArrayList<>(rows);
        strataCache.clear();
        designCache.clear();
    }

    @Override
    public IndependenceTest indTestSubset(List<Node> vars) {
        return this;
    }

    @Override
    public String toString() {
        return "Minimax-t RFF CI test (mixed)";
    }

    // =========================================================
    // Type utilities
    // =========================================================

    private int idx(Node v) {
        Integer i = indexMap.get(v.getName());
        if (i == null) throw new IllegalArgumentException("Unknown variable: " + v);
        return i;
    }

    private int[] idxSorted(Set<Node> z) {
        int[] out = new int[z.size()];
        int k = 0;
        for (Node v : z) out[k++] = idx(v);
        Arrays.sort(out);
        return out;
    }

    private List<Integer> listRows() {
        if (rows != null) return rows;
        int n = data.getNumRows();
        ArrayList<Integer> r = new ArrayList<>(n);
        for (int i = 0; i < n; i++) r.add(i);
        return r;
    }

    private boolean isDiscrete(int col) {
        return variables.get(col) instanceof DiscreteVariable;
    }

    private int[] filterContinuous(int[] cols) {
        int c = 0;
        for (int v : cols) if (!isDiscrete(v)) c++;
        int[] out = new int[c];
        int k = 0;
        for (int v : cols) if (!isDiscrete(v)) out[k++] = v;
        return out;
    }

    private int[] filterDiscrete(int[] cols) {
        int c = 0;
        for (int v : cols) if (isDiscrete(v)) c++;
        int[] out = new int[c];
        int k = 0;
        for (int v : cols) if (isDiscrete(v)) out[k++] = v;
        return out;
    }

    private int numCategories(int varIndex) {
        Node v = variables.get(varIndex);
        if (!(v instanceof DiscreteVariable dv)) return 0;
        return dv.getNumCategories();
    }

    private static int[] append(int[] z, int x) {
        int[] out = Arrays.copyOf(z, z.length + 1);
        out[z.length] = x;
        return out;
    }

    // =========================================================
    // Basic math + linear algebra helpers
    // =========================================================

    private static void centerInPlace(double[] y) {
        double m = 0.0;
        for (double v : y) m += v;
        m /= y.length;
        for (int i = 0; i < y.length; i++) y[i] -= m;
    }

    private static void zscoreColumnPreserveNaN(double[] in, double[] out) {
        double sum = 0.0, sum2 = 0.0;
        int n = 0;
        for (double v : in) {
            if (Double.isNaN(v)) continue;
            sum += v;
            sum2 += v * v;
            n++;
        }
        if (n < 2) {
            System.arraycopy(in, 0, out, 0, in.length);
            return;
        }
        double mean = sum / n;
        double var = (sum2 - n * mean * mean) / (n - 1.0);
        double sd = sqrt(max(1e-12, var));
        for (int i = 0; i < in.length; i++) {
            double v = in[i];
            out[i] = Double.isNaN(v) ? Double.NaN : (v - mean) / sd;
        }
    }

    private static double studentTLogLik(double[] y, double[] yhat, double nu, double scale) {
        int n = y.length;
        double c = logGamma(0.5 * (nu + 1.0)) - logGamma(0.5 * nu)
                - 0.5 * log(nu * PI) - log(scale);

        double sum = 0.0;
        double inv = 1.0 / (nu * scale * scale);

        for (int i = 0; i < n; i++) {
            double r = y[i] - yhat[i];
            double v = 1.0 + (r * r) * inv;
            sum += c - 0.5 * (nu + 1.0) * log(v);
        }
        return sum;
    }

    private static double logGamma(double x) {
        double[] p = {
                676.5203681218851,
                -1259.1392167224028,
                771.32342877765313,
                -176.61502916214059,
                12.507343278686905,
                -0.13857109526572012,
                9.9843695780195716e-6,
                1.5056327351493116e-7
        };
        int g = 7;

        if (x < 0.5) {
            return log(PI) - log(sin(PI * x)) - logGamma(1.0 - x);
        }

        x -= 1.0;
        double a = 0.99999999999980993;
        for (int i = 0; i < p.length; i++) a += p[i] / (x + i + 1.0);

        double t = x + g + 0.5;
        return 0.5 * log(2.0 * PI) + (x + 0.5) * log(t) - t + log(a);
    }

    private static double[] solveFromCholeskyLower(DMatrixRMaj L, double[] b) {
        int n = b.length;
        double[] x = Arrays.copyOf(b, n);

        // forward solve L u = b
        for (int i = 0; i < n; i++) {
            double sum = x[i];
            for (int j = 0; j < i; j++) sum -= L.get(i, j) * x[j];
            x[i] = sum / L.get(i, i);
        }

        // back solve L^T x = u
        for (int i = n - 1; i >= 0; i--) {
            double sum = x[i];
            for (int j = i + 1; j < n; j++) sum -= L.get(j, i) * x[j];
            x[i] = sum / L.get(i, i);
        }
        return x;
    }

    // =========================================================
    // Multinomial helpers
    // =========================================================

    private static double[][] softmaxProbsFromPhi(int K, int n,
                                                  double[][] beta,
                                                  double[][] Phi,
                                                  double[] logitsScratch) {
        final int C = K - 1;
        final double[][] p = new double[n][K];

        for (int i = 0; i < n; i++) {
            final double[] phi = Phi[i];

            logitsScratch[0] = 0.0;
            double maxLog = 0.0;

            for (int c = 0; c < C; c++) {
                double s = 0.0;
                for (int a = 0; a < phi.length; a++) s += phi[a] * beta[a][c];
                logitsScratch[c + 1] = s;
                if (s > maxLog) maxLog = s;
            }

            double sum = 0.0;
            for (int k = 0; k < K; k++) {
                final double e = exp(logitsScratch[k] - maxLog);
                p[i][k] = e;
                sum += e;
            }

            final double inv = 1.0 / sum;
            for (int k = 0; k < K; k++) p[i][k] *= inv;
        }

        return p;
    }

    private static double multinomialLogLikFromPhi(int[] y, int K, int n,
                                                   double[][] beta,
                                                   double[][] Phi,
                                                   double[] logitsScratch) {
        final int C = K - 1;
        double ll = 0.0;

        for (int i = 0; i < n; i++) {
            final double[] phi = Phi[i];

            logitsScratch[0] = 0.0;
            double maxLog = 0.0;

            for (int c = 0; c < C; c++) {
                double s = 0.0;
                for (int a = 0; a < phi.length; a++) s += phi[a] * beta[a][c];
                logitsScratch[c + 1] = s;
                if (s > maxLog) maxLog = s;
            }

            double sum = 0.0;
            for (int k = 0; k < K; k++) sum += exp(logitsScratch[k] - maxLog);

            int yi = y[i];
            if (yi < 0 || yi >= K) return Double.NaN;

            ll += (logitsScratch[yi] - maxLog) - log(sum);
        }

        return ll;
    }

    // =========================================================
    // Stratification helpers
    // =========================================================

    private static int[] range(int n) {
        int[] r = new int[n];
        for (int i = 0; i < n; i++) r[i] = i;
        return r;
    }

    private static long signature(int[] rows) {
        long h = 1469598103934665603L;
        for (int r : rows) {
            h ^= r;
            h *= 1099511628211L;
        }
        return h;
    }

    private static int binIndex(double[] edges, double v) {
        int lo = 0, hi = edges.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (v > edges[mid]) lo = mid + 1;
            else hi = mid;
        }
        return lo;
    }

    private static double[] quantileEdges(double[] x, int bins) {
        bins = Math.max(2, bins);
        double[] a = Arrays.copyOf(x, x.length);
        Arrays.sort(a);

        int n = a.length;
        double[] edges = new double[bins - 1];

        double last = Double.NEGATIVE_INFINITY;
        for (int b = 1; b < bins; b++) {
            double q = b / (double) bins;
            int idx = (int) Math.floor(q * (n - 1));
            idx = Math.min(Math.max(0, idx), n - 1);

            double e = a[idx];
            if (e <= last) {
                int j = idx;
                while (j + 1 < n && a[j] <= last) j++;
                e = a[j];
            }

            edges[b - 1] = e;
            last = e;
        }

        return edges;
    }

    private static Map<String, Integer> indexMap(List<Node> vars) {
        Map<String, Integer> m = new HashMap<>();
        for (int i = 0; i < vars.size(); i++) m.put(vars.get(i).getName(), i);
        return m;
    }

    // =========================================================
    // Small utility classes (strata cache + one-hot spec + design key)
    // =========================================================

    private record StrataKey(int[] zIdx, long rowsSig, int bins, int minSize) {
        StrataKey {
            zIdx = (zIdx == null) ? new int[0] : Arrays.copyOf(zIdx, zIdx.length);
        }
        @Override
        public int hashCode() {
            int h = Arrays.hashCode(zIdx);
            h = 31 * h + Long.hashCode(rowsSig);
            h = 31 * h + Integer.hashCode(bins);
            h = 31 * h + Integer.hashCode(minSize);
            return h;
        }
        @Override
        public boolean equals(Object o) {
            if (!(o instanceof StrataKey k)) return false;
            return rowsSig == k.rowsSig && bins == k.bins && minSize == k.minSize && Arrays.equals(zIdx, k.zIdx);
        }
    }

    private record DesignKey(int child, int[] parents, long rowsSig,
                             int rffFeatures, double rffSigma, long rffSeed) {
        DesignKey {
            parents = (parents == null) ? new int[0] : Arrays.copyOf(parents, parents.length);
        }
        @Override
        public int hashCode() {
            int h = Integer.hashCode(child);
            h = 31 * h + Arrays.hashCode(parents);
            h = 31 * h + Long.hashCode(rowsSig);
            h = 31 * h + Integer.hashCode(rffFeatures);
            h = 31 * h + Double.hashCode(rffSigma);
            h = 31 * h + Long.hashCode(rffSeed);
            return h;
        }
        @Override
        public boolean equals(Object o) {
            if (!(o instanceof DesignKey k)) return false;
            return child == k.child
                    && rowsSig == k.rowsSig
                    && rffFeatures == k.rffFeatures
                    && Double.compare(rffSigma, k.rffSigma) == 0
                    && rffSeed == k.rffSeed
                    && Arrays.equals(parents, k.parents);
        }
    }

    private static final class StratumSignature {
        final int[] parts;
        final int hash;

        StratumSignature(int[] parts) {
            this.parts = parts;
            this.hash = Arrays.hashCode(parts);
        }

        @Override
        public int hashCode() { return hash; }

        @Override
        public boolean equals(Object o) {
            return (o instanceof StratumSignature s) && Arrays.equals(parts, s.parts);
        }
    }

    private static final class IntArrayList {
        private int[] a = new int[16];
        private int n = 0;

        void add(int v) {
            if (n == a.length) a = Arrays.copyOf(a, a.length * 2);
            a[n++] = v;
        }

        int size() { return n; }

        int[] toArray() { return Arrays.copyOf(a, n); }
    }

    private static final class OneHotSpec {
        final int[] sizes;     // num categories per disc parent
        final int[] offsets;   // offsets into the one-hot block (baseline dropped)
        final int totalCols;

        OneHotSpec(int[] sizes, int[] offsets, int totalCols) {
            this.sizes = sizes;
            this.offsets = offsets;
            this.totalCols = totalCols;
        }
    }
}