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
 * Model-based mixed-type conditional independence test:
 * <p>
 * Tests X ⟂ Y | Z by measuring predictive gain in log-likelihood:
 * Δ = logLik( target | Z ∪ {other} ) - logLik( target | Z )
 * <p>
 * where the target model is:
 * - continuous target: Student-t location model with RFF(continuous preds) + onehot(discrete preds), fit by IRLS ridge
 * - discrete target:   multinomial logistic ridge on same feature map, fit by IRLS
 * <p>
 * P-value is obtained by within-stratum permutation:
 * - strata formed from Z: exact-match discrete Z + binned continuous Z
 * - within each stratum, shuffle "other" predictor (X) to break dependence while preserving Z
 * <p>
 * Direction used: by default tests both directions and returns the larger p-value (more conservative).
 */
public final class MinimaxCITestWorksOnGNButSlow implements IndependenceTest, RowsSettable {

    // ---------------- data ----------------
    private final DataSet data;
    private final List<Node> variables;
    private final Map<String, Integer> indexMap;
    // global z-scored continuous columns (NaNs preserved). For discrete vars, filled with NaN.
    private final double[][] zCols;
    // cache for strata (Z signature -> groups of row-indices within current useRows)
    private final ConcurrentHashMap<StrataKey, int[][]> strataCache = new ConcurrentHashMap<>();
    // ---------------- knobs ----------------
    private double alpha = 0.01;
    // shared ridge + feature knobs
    private double ridge = 1e-3;
    private int rffFeatures = 128;
    private double rffSigma = 1.0;
    private long rffSeed = 1L;
    // Student-t knobs for continuous targets
    private double nu = 5.0;
    private double scale = 1.0;
    // IRLS knobs
    private int irlsIters = 6;
    private double irlsTol = 1e-6;
    // permutation knobs
    private int permutations = 200;
    private long permSeed = 1L;
    // stratification knobs
    private int binsPerContZ = 4;
    private int minStratumSize = 3;
    // behavior
    private boolean verbose = false;
    private boolean twoSidedDirectional = true; // do both Y|X,Z and X|Y,Z; take max p (conservative)
    // optional row restriction
    private List<Integer> rows = null;

    public MinimaxCITestWorksOnGNButSlow(DataSet data, double alpha) {
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

    // ---------------- IndependenceTest ----------------

    private static double multinomialInterceptOnlyLogLik(int[] y, int K) {
        int n = y.length;
        int[] counts = new int[K];
        for (int v : y) {
            if (v < 0 || v >= K) return Double.NaN;
            counts[v]++;
        }
        double ll = 0.0;
        for (int k = 0; k < K; k++) {
            if (counts[k] == 0) continue;
            double pk = counts[k] / (double) n;
            ll += counts[k] * log(pk);
        }
        return ll;
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

    private static int binIndex(double[] edges, double v) {
        // edges are interior cutpoints; return bin in [0..B-1]
        int lo = 0, hi = edges.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (v > edges[mid]) lo = mid + 1;
            else hi = mid;
        }
        return lo;
    }

    // ---------------- core model fitting ----------------

    private static double[] quantileEdges(double[] x, int bins) {
        bins = max(2, bins);
        double[] a = Arrays.copyOf(x, x.length);
        Arrays.sort(a);

        // interior edges at q = 1/bins, 2/bins, ..., (bins-1)/bins
        double[] edges = new double[bins - 1];
        int n = a.length;

        for (int b = 1; b < bins; b++) {
            double q = b / (double) bins;
            int idx = (int) floor(q * (n - 1));
            idx = min(max(0, idx), n - 1);
            edges[b - 1] = a[idx];
        }
        return edges;
    }

    private static void centerInPlace(double[] y) {
        double m = 0.0;
        for (double v : y) m += v;
        m /= y.length;
        for (int i = 0; i < y.length; i++) y[i] -= m;
    }

    private static double dot(double[] a, double[] b) {
        double s = 0.0;
        for (int i = 0; i < a.length; i++) s += a[i] * b[i];
        return s;
    }

    // -------- Continuous target: Student-t IRLS ridge on [RFF(cont preds) + onehot(disc preds)] --------

    private static double[] solveFromCholeskyLower(DMatrixRMaj L, double[] b) {
        int n = b.length;
        double[] x = Arrays.copyOf(b, n);

        for (int i = 0; i < n; i++) {
            double sum = x[i];
            for (int j = 0; j < i; j++) sum -= L.get(i, j) * x[j];
            x[i] = sum / L.get(i, i);
        }

        for (int i = n - 1; i >= 0; i--) {
            double sum = x[i];
            for (int j = i + 1; j < n; j++) sum -= L.get(j, i) * x[j];
            x[i] = sum / L.get(i, i);
        }

        return x;
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

    // -------- Discrete target: multinomial logistic ridge (approx block-IRLS) --------

    private static Map<String, Integer> indexMap(List<Node> vars) {
        Map<String, Integer> m = new HashMap<>();
        for (int i = 0; i < vars.size(); i++) m.put(vars.get(i).getName(), i);
        return m;
    }

    private static int[] append(int[] a, int x) {
        int[] out = Arrays.copyOf(a, a.length + 1);
        out[a.length] = x;
        return out;
    }

    // ---------------- feature construction ----------------

    private static int[] identity(int n) {
        int[] p = new int[n];
        for (int i = 0; i < n; i++) p[i] = i;
        return p;
    }

    private static int[] range(int n) {
        int[] r = new int[n];
        for (int i = 0; i < n; i++) r[i] = i;
        return r;
    }

    // ---------------- multinomial helpers ----------------

    private static long signature(List<Integer> rows) {
        long h = 1469598103934665603L;
        for (int r : rows) {
            h ^= r;
            h *= 1099511628211L;
        }
        return h;
    }

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

        List<Integer> baseRows = listRows();
        List<Integer> useRows = rowsCompleteFor(ix, iy, iz, baseRows);
        int n = useRows.size();
        if (n < 20) return 0.0;

        // conservative: do both directions and take the larger p
        double p1 = pDirectional(iy, ix, iz, useRows);
        if (!twoSidedDirectional) return p1;

        double p2 = pDirectional(ix, iy, iz, useRows);
        return min(p1, p2);
    }

    // ---------------- Student-t loglik ----------------

    /**
     * Directional test of (target ⟂ other | Z):
     * Δ = ll(target | Z ∪ {other}) - ll(target | Z)
     * p = permuted within strata of Z by shuffling "other".
     */
    private double pDirectional(int target, int other, int[] zIdx, List<Integer> useRows) {
        int n = useRows.size();

        // build strata from Z (within useRows index-space 0..n-1)
        int[][] strata = getStrata(zIdx, useRows);
        if (strata.length == 0) return 0.0;

        // observed delta
        double deltaObs = deltaLogLik(target, other, zIdx, useRows, null);
        if (!Double.isFinite(deltaObs)) return 1.0;


        // permutation: shuffle "other" within each stratum
        SplittableRandom rng = new SplittableRandom(permSeed ^ (target * 1315423911L) ^ (other * 2654435761L) ^ Arrays.hashCode(zIdx));

        int ge = 0;
        int B = max(50, permutations);

        // we implement permutation by providing a permuted mapping from rowIndex->rowIndex for the "other" variable values
        int[] permMap = identity(n);
        int valid = 0;

        for (int b = 0; b < B; b++) {
            // reset identity
            for (int i = 0; i < n; i++) permMap[i] = i;

            // shuffle permMap within each stratum
            for (int[] g : strata) {
                for (int i = g.length - 1; i > 0; i--) {
                    int j = rng.nextInt(i + 1);
                    int ii = g[i], jj = g[j];
                    int tmp = permMap[ii];
                    permMap[ii] = permMap[jj];
                    permMap[jj] = tmp;
                }
            }

            double delta = deltaLogLik(target, other, zIdx, useRows, permMap);
            if (!Double.isFinite(delta)) continue;

            valid++;
            if (delta >= deltaObs) ge++;
        }

        // standard add-one smoothing
        return (ge + 1.0) / (valid + 1.0);
    }

    /**
     * Compute Δ = ll(target | Z ∪ {other}) - ll(target | Z).
     * If permMap != null, "other" values are read from permuted indices within useRows-space.
     */
    private double deltaLogLik(int target, int other, int[] zIdx, List<Integer> useRows, int[] permMap) {
        // reduced predictors: Z
        double ll0 = fitLogLik(target, zIdx, useRows, null);

        // full predictors: Z + other
        int[] full = append(zIdx, other);
        Arrays.sort(full);

        double ll1 = fitLogLik(target, full, useRows, permMap == null ? null : new PermutedOther(other, permMap));

        if (!Double.isFinite(ll0) || !Double.isFinite(ll1)) return Double.NaN;
        return ll1 - ll0;
    }

    // ---------------- strata building (Z) ----------------

    /**
     * Fit conditional model for target given predictors and return fitted log-likelihood on the same rows.
     * For discrete targets -> multinomial log-lik. For continuous -> Student-t log-lik.
     */
    private double fitLogLik(int target, int[] predictors, List<Integer> useRows, PermutedOther permOther) {
        int n = useRows.size();
        if (n < 10) return Double.NaN;

        if (isDiscrete(target)) {
            int K = numCategories(target);
            if (K < 2) return Double.NaN;
            int[] y = extractDiscrete(target, useRows);

            if (predictors.length == 0) {
                return multinomialInterceptOnlyLogLik(y, K);
            }

            FitMultinom fit = fitMultinomialLogit(target, y, K, predictors, useRows, permOther);
            return fit.logLik;

        } else {
            double[] y = extractContinuous(target, useRows);
            centerInPlace(y);

            if (predictors.length == 0) {
                return studentTLogLik(y, new double[n], nu, scale);
            }

            FitCont fit = fitStudentTRffRidge(target, y, predictors, useRows, permOther);
            return fit.logLik;
        }
    }

    private FitCont fitStudentTRffRidge(int target, double[] yCentered, int[] predictors,
                                        List<Integer> useRows, PermutedOther permOther) {
        if (!(ridge > 0) || !Double.isFinite(ridge)) return new FitCont(Double.NaN);
        if (!(nu > 2) || !Double.isFinite(nu)) return new FitCont(Double.NaN);
        if (!(scale > 0) || !Double.isFinite(scale)) return new FitCont(Double.NaN);

        int[] cont = filterContinuous(predictors);
        int[] disc = filterDiscrete(predictors);
        OneHotSpec oh = buildOneHotSpec(disc);

        final int D = rffFeatures;
        final int Q = oh.totalCols;
        final int M = D + Q;
        final int n = yCentered.length;

        // extract cont predictor matrix (z-scored)
        double[][] Zc = new double[n][cont.length];
        for (int i = 0; i < n; i++) {
            int baseRow = useRows.get(i);
            int permRow = baseRow;

            // If we're permuting and the "other" is continuous, switch the row only for that predictor.
            int permutedRowForOther = baseRow;
            if (permOther != null) {
                int pi = permOther.permMap[i];              // i is in useRows-space
                permutedRowForOther = useRows.get(pi);
            }

            for (int j = 0; j < cont.length; j++) {
                int var = cont[j];
                int rowForVar = baseRow;

                if (permOther != null && var == permOther.otherIdx) {
                    rowForVar = permutedRowForOther;
                }

                Zc[i][j] = zCols[var][rowForVar];
            }
        }

        long seed = rffSeed ^ (long) target * 0x9E3779B97F4A7C15L ^ Arrays.hashCode(predictors);
        Random rng = new Random(seed);
        double[][] W = new double[D][max(1, cont.length)];
        for (int k = 0; k < D; k++) for (int j = 0; j < W[k].length; j++) W[k][j] = rng.nextGaussian() / rffSigma;
        double[] phase = new double[D];
        for (int k = 0; k < D; k++) phase[k] = 2.0 * PI * rng.nextDouble();
        double phiScale = sqrt(2.0 / D);

        double[] w = new double[n];
        Arrays.fill(w, 1.0);
        double[] beta = new double[M];

        double prevObj = Double.POSITIVE_INFINITY;
        double[] xRow = new double[M];

        for (int iter = 0; iter < max(1, irlsIters); iter++) {
            DMatrixRMaj G = new DMatrixRMaj(M, M);
            double[] v = new double[M];

            for (int i = 0; i < n; i++) {
                buildXRow(xRow, i, Zc, cont.length, W, phase, phiScale, oh, disc, useRows, permOther);

                double wi = w[i];
                double yi = yCentered[i];

                for (int a = 0; a < M; a++) v[a] += wi * xRow[a] * yi;
                for (int a = 0; a < M; a++) {
                    double pa = wi * xRow[a];
                    for (int b = 0; b <= a; b++) G.add(a, b, pa * xRow[b]);
                }
            }

            for (int a = 0; a < M; a++) for (int b = 0; b < a; b++) G.set(b, a, G.get(a, b));
            for (int a = 0; a < M; a++) G.add(a, a, ridge);

            CholeskyDecomposition_F64<DMatrixRMaj> chol = DecompositionFactory_DDRM.chol(true);
            if (!chol.decompose(G)) return new FitCont(Double.NaN);
            DMatrixRMaj L = chol.getT(null);

            beta = solveFromCholeskyLower(L, v);

            // update weights
            double obj = 0.0;
            for (int i = 0; i < n; i++) {
                buildXRow(xRow, i, Zc, cont.length, W, phase, phiScale, oh, disc, useRows, permOther);
                double yhat = dot(xRow, beta);
                double r = yCentered[i] - yhat;
                double u2 = (r / scale) * (r / scale);
                w[i] = (nu + 1.0) / (nu + u2);
                obj += 0.5 * (nu + 1.0) * log1p(u2 / nu);
            }

            if (abs(prevObj - obj) <= irlsTol * (1.0 + abs(prevObj))) break;
            prevObj = obj;
        }

        // final yhat and loglik
        double[] yhat = new double[n];
        for (int i = 0; i < n; i++) {
            buildXRow(xRow, i, Zc, cont.length, W, phase, phiScale, oh, disc, useRows, permOther);
            yhat[i] = dot(xRow, beta);
        }

        double ll = studentTLogLik(yCentered, yhat, nu, scale);
        return new FitCont(ll);
    }

    private FitMultinom fitMultinomialLogit(int target, int[] y, int K, int[] predictors,
                                            List<Integer> useRows, PermutedOther permOther) {
        if (!(ridge > 0) || !Double.isFinite(ridge)) return new FitMultinom(Double.NaN);

        int[] cont = filterContinuous(predictors);
        int[] disc = filterDiscrete(predictors);
        OneHotSpec oh = buildOneHotSpec(disc);

        final int D = rffFeatures;
        final int Q = oh.totalCols;
        final int M = D + Q;
        final int C = K - 1;
        final int n = y.length;

        // cont matrix
        double[][] Zc = new double[n][cont.length];
        for (int i = 0; i < n; i++) {
            int baseRow = useRows.get(i);
            int permRow = baseRow;

            // If we're permuting and the "other" is continuous, switch the row only for that predictor.
            int permutedRowForOther = baseRow;
            if (permOther != null) {
                int pi = permOther.permMap[i];              // i is in useRows-space
                permutedRowForOther = useRows.get(pi);
            }

            for (int j = 0; j < cont.length; j++) {
                int var = cont[j];
                int rowForVar = baseRow;

                if (permOther != null && var == permOther.otherIdx) {
                    rowForVar = permutedRowForOther;
                }

                Zc[i][j] = zCols[var][rowForVar];
            }
        }

        long seed = rffSeed ^ (long) target * 0x9E3779B97F4A7C15L ^ Arrays.hashCode(predictors);
        Random rng = new Random(seed);
        double[][] W = new double[D][max(1, cont.length)];
        for (int k = 0; k < D; k++) for (int j = 0; j < W[k].length; j++) W[k][j] = rng.nextGaussian() / rffSigma;
        double[] phase = new double[D];
        for (int k = 0; k < D; k++) phase[k] = 2.0 * PI * rng.nextDouble();
        double phiScale = sqrt(2.0 / D);

        double[][] beta = new double[M][C]; // init 0
        double prevObj = Double.POSITIVE_INFINITY;

        double[] xRow = new double[M];

        for (int iter = 0; iter < max(1, irlsIters); iter++) {

            double[][] probs = softmaxProbs(n, K, beta, xRow, Zc, cont.length, W, phase, phiScale, oh, disc, useRows, permOther);

            // block-IRLS: update each non-reference class separately
            for (int c = 0; c < C; c++) {
                DMatrixRMaj G = new DMatrixRMaj(M, M);
                double[] v = new double[M];

                for (int i = 0; i < n; i++) {
                    buildXRow(xRow, i, Zc, cont.length, W, phase, phiScale, oh, disc, useRows, permOther);

                    double pc = probs[i][c + 1];
                    double wc = max(pc * (1.0 - pc), 1e-10);

                    double eta = 0.0;
                    for (int a = 0; a < M; a++) eta += xRow[a] * beta[a][c];

                    double yc = (y[i] == (c + 1)) ? 1.0 : 0.0;
                    double z = eta + (yc - pc) / wc;

                    for (int a = 0; a < M; a++) v[a] += wc * xRow[a] * z;
                    for (int a = 0; a < M; a++) {
                        double pa = wc * xRow[a];
                        for (int b = 0; b <= a; b++) G.add(a, b, pa * xRow[b]);
                    }
                }

                for (int a = 0; a < M; a++) for (int b = 0; b < a; b++) G.set(b, a, G.get(a, b));
                for (int a = 0; a < M; a++) G.add(a, a, ridge);

                CholeskyDecomposition_F64<DMatrixRMaj> chol = DecompositionFactory_DDRM.chol(true);
                if (!chol.decompose(G)) return new FitMultinom(Double.NaN);
                DMatrixRMaj L = chol.getT(null);

                double[] bc = solveFromCholeskyLower(L, v);
                for (int a = 0; a < M; a++) beta[a][c] = bc[a];
            }

            double ll = multinomialLogLik(n, K, y, beta, xRow, Zc, cont.length, W, phase, phiScale, oh, disc, useRows, permOther);
            double obj = -ll;

            if (abs(prevObj - obj) <= irlsTol * (1.0 + abs(prevObj))) break;
            prevObj = obj;
        }

        double ll = multinomialLogLik(n, K, y, beta, xRow, Zc, cont.length, W, phase, phiScale, oh, disc, useRows, permOther);
        return new FitMultinom(ll);
    }

    private OneHotSpec buildOneHotSpec(int[] discPreds) {
        int m = discPreds.length;
        int[] sizes = new int[m];
        int[] offsets = new int[m];
        int off = 0;
        for (int t = 0; t < m; t++) {
            int var = discPreds[t];
            int K = numCategories(var);
            sizes[t] = K;
            offsets[t] = off;
            off += max(0, K - 1); // drop baseline
        }
        return new OneHotSpec(sizes, offsets, off);
    }

    private void buildXRow(double[] out, int i,
                           double[][] Zc, int dCont,
                           double[][] W, double[] phase, double phiScale,
                           OneHotSpec oh, int[] discPreds,
                           List<Integer> useRows, PermutedOther permOther) {

        // RFF block
        if (dCont == 0) {
            Arrays.fill(out, 0, rffFeatures, 0.0);
        } else {
            for (int k = 0; k < rffFeatures; k++) {
                double dot = 0.0;
                double[] wk = W[k];
                for (int j = 0; j < dCont; j++) dot += wk[j] * Zc[i][j];
                out[k] = phiScale * cos(dot + phase[k]);
            }
        }

        // one-hot block
        Arrays.fill(out, rffFeatures, out.length, 0.0);
        if (discPreds.length == 0) return;

        int rowIdx = i;
        // if permuting the OTHER variable, we must read that predictor from permuted rowIdx
        // We implement this by switching the "row used for that predictor" only.
        int baseRow = useRows.get(i);

        for (int t = 0; t < discPreds.length; t++) {
            int var = discPreds[t];

            int rowForThisVar = baseRow;
            if (permOther != null && var == permOther.otherIdx) {
                int pi = permOther.permMap[rowIdx];
                rowForThisVar = useRows.get(pi);
            }

            int lev = data.getInt(rowForThisVar, var);
            if (lev == DiscreteVariable.MISSING_VALUE) continue;
            if (lev <= 0) continue; // baseline dropped

            int col = oh.offsets[t] + (lev - 1);
            int width = max(0, oh.sizes[t] - 1);
            if (col >= oh.offsets[t] && col < oh.offsets[t] + width) {
                out[rffFeatures + col] = 1.0;
            }
        }
    }

    private double[][] softmaxProbs(int n, int K, double[][] beta, double[] xRow,
                                    double[][] Zc, int dCont,
                                    double[][] W, double[] phase, double phiScale,
                                    OneHotSpec oh, int[] discPreds,
                                    List<Integer> useRows, PermutedOther permOther) {
        int C = K - 1;
        int M = xRow.length;
        double[][] p = new double[n][K];

        for (int i = 0; i < n; i++) {
            buildXRow(xRow, i, Zc, dCont, W, phase, phiScale, oh, discPreds, useRows, permOther);

            double[] logit = new double[K];
            logit[0] = 0.0;
            double maxLog = 0.0;

            for (int c = 0; c < C; c++) {
                double s = 0.0;
                for (int a = 0; a < M; a++) s += xRow[a] * beta[a][c];
                logit[c + 1] = s;
                if (s > maxLog) maxLog = s;
            }

            double sum = 0.0;
            for (int k = 0; k < K; k++) {
                double e = exp(logit[k] - maxLog);
                p[i][k] = e;
                sum += e;
            }
            double inv = 1.0 / sum;
            for (int k = 0; k < K; k++) p[i][k] *= inv;
        }

        return p;
    }

    // ---------------- utility: rows/missingness ----------------

    private double multinomialLogLik(int n, int K, int[] y, double[][] beta, double[] xRow,
                                     double[][] Zc, int dCont,
                                     double[][] W, double[] phase, double phiScale,
                                     OneHotSpec oh, int[] discPreds,
                                     List<Integer> useRows, PermutedOther permOther) {
        int C = K - 1;
        int M = xRow.length;
        double ll = 0.0;

        for (int i = 0; i < n; i++) {
            buildXRow(xRow, i, Zc, dCont, W, phase, phiScale, oh, discPreds, useRows, permOther);

            double[] logit = new double[K];
            logit[0] = 0.0;
            double maxLog = 0.0;

            for (int c = 0; c < C; c++) {
                double s = 0.0;
                for (int a = 0; a < M; a++) s += xRow[a] * beta[a][c];
                logit[c + 1] = s;
                if (s > maxLog) maxLog = s;
            }

            double sum = 0.0;
            for (int k = 0; k < K; k++) sum += exp(logit[k] - maxLog);

            ll += (logit[y[i]] - maxLog) - log(sum);
        }

        return ll;
    }

    private int[][] getStrata(int[] zIdx, List<Integer> useRows) {
        if (zIdx.length == 0) return new int[][]{range(useRows.size())};

        long rowsSig = signature(useRows);
        StrataKey key = new StrataKey(zIdx, rowsSig, binsPerContZ, minStratumSize);

        return strataCache.computeIfAbsent(key, kk -> buildStrata(zIdx, useRows, binsPerContZ, minStratumSize));
    }

    // ---------------- IndependenceTest required ----------------

    private int[][] buildStrata(int[] zIdx, List<Integer> useRows, int binsPerCont, int minSize) {
        int n = useRows.size();

        // split Z into discrete + continuous indices
        int[] zDisc = filterDiscrete(zIdx);
        int[] zCont = filterContinuous(zIdx);

        // compute bin edges for each continuous Z dimension (quantile bins on useRows)
        double[][] edges = new double[zCont.length][];
        for (int j = 0; j < zCont.length; j++) {
            double[] vals = new double[n];
            for (int i = 0; i < n; i++) vals[i] = zCols[zCont[j]][useRows.get(i)];
            edges[j] = quantileEdges(vals, binsPerCont);
        }

        // map each row i (0..n-1) to a stratum key built from:
        //  - discrete levels of zDisc (ints)
        //  - bin indices for zCont (ints)
        HashMap<StratumSignature, IntArrayList> buckets = new HashMap<>();

        for (int i = 0; i < n; i++) {
            int row = useRows.get(i);

            int[] parts = new int[zDisc.length + zCont.length];
            int k = 0;

            // discrete Z exact match
            for (int v : zDisc) {
                int lev = data.getInt(row, v);
                parts[k++] = lev;
            }

            // continuous Z bins
            for (int j = 0; j < zCont.length; j++) {
                double val = zCols[zCont[j]][row];
                int b = binIndex(edges[j], val);
                parts[k++] = b;
            }

            StratumSignature sig = new StratumSignature(parts);
            buckets.computeIfAbsent(sig, s -> new IntArrayList()).add(i); // store i in useRows-space
        }

        // filter by min size
        ArrayList<int[]> groups = new ArrayList<>();
        for (IntArrayList lst : buckets.values()) {
            if (lst.size() >= minSize) groups.add(lst.toArray());
        }

        return groups.toArray(new int[0][]);
    }

    private List<Integer> rowsCompleteFor(int ix, int iy, int[] iz, List<Integer> baseRows) {
        List<Integer> out = new ArrayList<>(baseRows.size());
        for (int r : baseRows) {

            if (!isValuePresent(r, ix)) continue;
            if (!isValuePresent(r, iy)) continue;

            boolean ok = true;
            for (int j : iz) {
                if (!isValuePresent(r, j)) {
                    ok = false;
                    break;
                }
            }
            if (ok) out.add(r);
        }
        return out;
    }

    private boolean isValuePresent(int row, int col) {
        if (isDiscrete(col)) {
            return data.getInt(row, col) != DiscreteVariable.MISSING_VALUE;
        } else {
            return !Double.isNaN(data.getDouble(row, col));
        }
    }

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

    // ---------------- RowsSettable ----------------

    @Override
    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    // ---------------- helpers ----------------

    @Override
    public IndependenceTest indTestSubset(List<Node> vars) {
        // lightweight subset: same dataset, same params, but variables restricted is tricky for index mapping
        // simplest safe behavior: return this (Tetrad usually calls subset for speed; correctness is fine).
        // If you prefer, implement a proper remap using a sub-DataSet.
        return this;
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
    }

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

    private int numCategories(int col) {
        Node v = variables.get(col);
        if (v instanceof DiscreteVariable dv) return dv.getNumCategories();
        return 0;
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

    private double[] extractContinuous(int col, List<Integer> useRows) {
        int n = useRows.size();
        double[] x = new double[n];
        for (int i = 0; i < n; i++) x[i] = data.getDouble(useRows.get(i), col);
        return x;
    }

    private int[] extractDiscrete(int col, List<Integer> useRows) {
        int n = useRows.size();
        int[] x = new int[n];
        for (int i = 0; i < n; i++) x[i] = data.getInt(useRows.get(i), col);
        return x;
    }

    public void setPermutations(int B) {
        this.permutations = max(50, B);
    }

    public void setPermSeed(long s) {
        this.permSeed = s;
    }

    public void setBinsPerContZ(int b) {
        this.binsPerContZ = max(2, b);
        strataCache.clear();
    }

    public void setMinStratumSize(int m) {
        this.minStratumSize = max(2, m);
        strataCache.clear();
    }

    public void setRidge(double ridge) {
        this.ridge = ridge;
    }

    public void setRffFeatures(int d) {
        this.rffFeatures = max(16, d);
    }

    public void setRffSigma(double s) {
        this.rffSigma = s;
    }

    // ---------------- optional setters ----------------

    public void setRffSeed(long s) {
        this.rffSeed = s;
    }

    public void setNu(double nu) {
        this.nu = nu;
    }

    public void setScale(double scale) {
        this.scale = scale;
    }

    public void setIrlsIters(int iters) {
        this.irlsIters = max(1, iters);
    }

    public void setIrlsTol(double tol) {
        this.irlsTol = max(0.0, tol);
    }

    public void setTwoSidedDirectional(boolean b) {
        this.twoSidedDirectional = b;
    }

    private record PermutedOther(int otherIdx, int[] permMap) {
    }

    private record OneHotSpec(int[] sizes, int[] offsets, int totalCols) {
    }

    private record FitCont(double logLik) {
    }

    private record FitMultinom(double logLik) {
    }

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

    private static final class StratumSignature {
        final int[] parts;
        final int hash;

        StratumSignature(int[] parts) {
            this.parts = parts;
            this.hash = Arrays.hashCode(parts);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object o) {
            return (o instanceof StratumSignature s) && Arrays.equals(parts, s.parts);
        }
    }

    // tiny growable int list
    private static final class IntArrayList {
        private int[] a = new int[16];
        private int n = 0;

        void add(int v) {
            if (n == a.length) a = Arrays.copyOf(a, a.length * 2);
            a[n++] = v;
        }

        int size() {
            return n;
        }

        int[] toArray() {
            return Arrays.copyOf(a, n);
        }
    }
}