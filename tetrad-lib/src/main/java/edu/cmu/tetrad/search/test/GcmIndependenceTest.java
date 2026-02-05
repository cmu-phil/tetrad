package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.math3.distribution.NormalDistribution;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.Math.*;

/**
 * Generalized Covariance Measure (GCM) conditional independence test:
 * <p>
 * Test X ⟂ Y | Z by:
 * - fit xhat(z) ~ E[X|Z], yhat(z) ~ E[Y|Z]
 * - residuals rx = x - xhat, ry = y - yhat
 * - u = rx * ry
 * - T = sqrt(n) * mean(u) / sd(u)  ~ approx N(0,1) under H0 (with mild conditions)
 * <p>
 * Practical notes:
 * - This is only as good as your regressors for E[·|Z].
 * - For speed in search, MUST cache residuals by (target, Z, rows).
 */
public final class GcmIndependenceTest implements IndependenceTest {

    private final DataSet data;
    private final List<Node> variables;
    private final Map<String, Integer> indexMap;
    private final NormalDistribution normal = new NormalDistribution(0, 1);
    // caching: residuals for target given Z and rows
    private final ResidualCache cache = new ResidualCache();
    private double alpha;
    private boolean verbose = false;

    // rows restriction (optional; null => all rows)
    private List<Integer> rows = null;

    // regression config
    private RegressorType regressorType = RegressorType.RFF_RIDGE;
    private double ridge = 1e-3;

    // RFF config (only used for RFF_RIDGE)
    private int rffFeatures = 200;     // D
    private double rffSigma = 1.0;     // lengthscale-ish; affects random freq scale
    private long rffSeed = 1L;
    // last p / last stat (handy for debugging)
    private double lastT = Double.NaN;
    private double lastP = Double.NaN;
    public GcmIndependenceTest(DataSet data, double alpha) {
        if (!data.isContinuous()) throw new IllegalArgumentException("GCM test currently requires continuous DataSet.");
        this.data = data;
        this.variables = Collections.unmodifiableList(new ArrayList<>(data.getVariables()));
        this.indexMap = indexMap(this.variables);
        setAlpha(alpha);
    }

    private static Map<String, Integer> indexMap(List<Node> vars) {
        Map<String, Integer> m = new HashMap<>();
        for (int i = 0; i < vars.size(); i++) m.put(vars.get(i).getName(), i);
        return m;
    }

    // -------------------- IndependenceTest --------------------

    private static double[] solveSymmetric(double[][] A, double[] b) {
        int n = b.length;

        // Cholesky factorization A = L L^T
        double[][] L = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j <= i; j++) {
                double sum = A[i][j];
                for (int k = 0; k < j; k++) sum -= L[i][k] * L[j][k];

                if (i == j) {
                    if (sum <= 1e-14) sum = 1e-14; // tiny floor
                    L[i][j] = sqrt(sum);
                } else {
                    L[i][j] = sum / L[j][j];
                }
            }
        }

        // Solve L y = b
        double[] y = new double[n];
        for (int i = 0; i < n; i++) {
            double sum = b[i];
            for (int k = 0; k < i; k++) sum -= L[i][k] * y[k];
            y[i] = sum / L[i][i];
        }

        // Solve L^T x = y
        double[] x = new double[n];
        for (int i = n - 1; i >= 0; i--) {
            double sum = y[i];
            for (int k = i + 1; k < n; k++) sum -= L[k][i] * x[k];
            x[i] = sum / L[i][i];
        }
        return x;
    }

    @Override
    public IndependenceTest indTestSubset(List<Node> vars) {
        // Simple (safe) implementation: keep the same dataset, just restrict variable list.
        // If you prefer true sub-DataSet, you can build one, but this keeps overhead low.
        GcmIndependenceTest t = new GcmIndependenceTest(this.data, this.alpha);
        t.setVerbose(this.verbose);
        t.setRows(this.rows);
        t.setRegressorType(this.regressorType);
        t.setRidge(this.ridge);
//        t.setRffFeatures(this.rffFeatures);
        t.setRffFeatures(autoRffFeatures(this.data.getNumRows(), vars.size()));
        t.setRffSigma(this.rffSigma);
        t.setRffSeed(this.rffSeed);
        return t;
    }

    private int autoRffFeatures(int n, int p) {
        int d = (int) Math.ceil(4.0 * Math.sqrt(n)); // tune 3–8
        d = Math.max(d, 200);
        d = Math.min(d, 2000); // cap for sanity
        // optionally scale a bit with p:
        d = Math.min(2000, d + 50 * p);
        return d;
    }

    @Override
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z) {
        double p = getPValue(x, y, z);
        boolean indep = p > alpha;

        IndependenceResult result = new IndependenceResult(
                new IndependenceFact(x, y, z),
                indep,
                p,
                alpha - p
        );

        if (verbose && indep) {
            TetradLogger.getInstance().log(LogUtilsSearch.independenceFactMsg(x, y, z, p));
        }
        return result;
    }

    public double getPValue(Node x, Node y, Set<Node> z) {
        Objects.requireNonNull(x);
        Objects.requireNonNull(y);
        Objects.requireNonNull(z);

        if (x.equals(y)) return 1.0;

//        List<Integer> useRows = listRows();
//        int n = useRows.size();

        int ix = idx(x);
        int iy = idx(y);
        int[] iz = idxSorted(z);

        List<Integer> baseRows = listRows();
        List<Integer> useRows = rowsCompleteFor(ix, iy, iz, baseRows);
        int n = useRows.size();

        int zx = z.size();

        // Basic df sanity: the CLT/studentization needs "enough" samples.
        // This is not Fisher-Z df; it’s just a guard.
        if (n <= max(10, zx + 5)) {
            // not enough to be meaningful; be conservative: call dependent
            lastT = Double.NaN;
            lastP = 0.0;
            return 0.0;
        }

//        int ix = idx(x);
//        int iy = idx(y);
//        int[] iz = idxSorted(z);

        double[] rx = cache.getResiduals(this, ix, iz, useRows);
        double[] ry = cache.getResiduals(this, iy, iz, useRows);

        // u_i = rx_i * ry_i
        double mean = 0.0;
        for (int i = 0; i < n; i++) mean += rx[i] * ry[i];
        mean /= n;

        double var = 0.0;
        for (int i = 0; i < n; i++) {
            double u = rx[i] * ry[i] - mean;
            var += u * u;
        }
        var /= (n - 1.0);

        if (!(var > 0)) {
            // If var is zero, u is (almost) constant; treat as dependent unless mean is ~0.
            double eps = 1e-12;
            lastT = mean / eps;
            lastP = (abs(mean) < 1e-12 ? 1.0 : 0.0);
            return lastP;
        }

        double t = sqrt(n) * mean / sqrt(var);
        lastT = t;

        double p = 2.0 * (1.0 - normal.cumulativeProbability(abs(t)));
        lastP = p;
        return p;
    }

    private List<Integer> rowsCompleteFor(int ix, int iy, int[] iz, List<Integer> baseRows) {
        List<Integer> out = new ArrayList<>(baseRows.size());
        for (int r : baseRows) {
            double vx = data.getDouble(r, ix);
            double vy = data.getDouble(r, iy);
            if (Double.isNaN(vx) || Double.isNaN(vy)) continue;

            boolean ok = true;
            for (int j : iz) {
                double vz = data.getDouble(r, j);
                if (Double.isNaN(vz)) {
                    ok = false;
                    break;
                }
            }
            if (ok) out.add(r);
        }
        return out;
    }

    @Override
    public List<Node> getVariables() {
        return variables;
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
    public DataSet getData() {
        return data;
    }

    @Override
    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    @Override
    public int getSampleSize() {
        return data.getNumRows();
    }

    // -------------------- rows --------------------

    @Override
    public List<DataSet> getDataSets() {
        return List.of(data);
    }

    public void setRows(List<Integer> rows) {
        this.rows = rows;
        cache.clear(); // rows changes => invalidate cache
    }

    // -------------------- regressor config --------------------

    private List<Integer> listRows() {
        if (rows != null) return rows;
        int n = data.getNumRows();
        List<Integer> r = new ArrayList<>(n);
        for (int i = 0; i < n; i++) r.add(i);
        return r;
    }

    public void setRegressorType(RegressorType t) {
        this.regressorType = (t == null ? RegressorType.LINEAR_RIDGE : t);
        cache.clear();
    }

    public void setRidge(double ridge) {
        if (ridge < 0) throw new IllegalArgumentException("ridge must be >= 0");
        this.ridge = ridge;
        cache.clear();
    }

    public void setRffFeatures(int d) {
        if (d < 1) throw new IllegalArgumentException("rffFeatures must be >= 1");
        this.rffFeatures = d;
        cache.clear();
    }

    public void setRffSigma(double sigma) {
        if (!(sigma > 0)) throw new IllegalArgumentException("rffSigma must be > 0");
        this.rffSigma = sigma;
        cache.clear();
    }

    public void setRffSeed(long seed) {
        this.rffSeed = seed;
        cache.clear();
    }

    public double getLastT() {
        return lastT;
    }

    // -------------------- internals --------------------

    public double getLastP() {
        return lastP;
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

    // ==================== Residual Cache ====================

    private double[] fitAndResidualize(int targetIdx, int[] zIdx, List<Integer> rows) {
        int n = rows.size();
        double[] y = new double[n];
        for (int i = 0; i < n; i++) y[i] = data.getDouble(rows.get(i), targetIdx);

        if (zIdx.length == 0) {
            // residualize by mean only
            double mean = 0.0;
            for (double v : y) mean += v;
            mean /= n;

            double[] r = new double[n];
            for (int i = 0; i < n; i++) r[i] = y[i] - mean;
            return r;
        }

        // Build Z matrix (n x p)
        int p = zIdx.length;
        double[][] Z = new double[n][p];
        for (int i = 0; i < n; i++) {
            int row = rows.get(i);
            for (int j = 0; j < p; j++) {
                Z[i][j] = data.getDouble(row, zIdx[j]);
            }
        }

        Regressor reg = switch (regressorType) {
            case LINEAR_RIDGE -> new LinearRidgeRegressor(ridge);
            case RFF_RIDGE ->
                    new RffRidgeRegressor(ridge, rffFeatures, rffSigma, rffSeed ^ targetIdx ^ Arrays.hashCode(zIdx));
        };

        double[] yhat = reg.fitPredict(Z, y);
        double[] r = new double[n];
        for (int i = 0; i < n; i++) r[i] = y[i] - yhat[i];
        return r;
    }

    // ==================== regression + residualization ====================

    // -------------------- public API knobs --------------------
    public enum RegressorType {LINEAR_RIDGE, RFF_RIDGE}

    // ==================== Regressors ====================

    private interface Regressor {
        /**
         * Fit on (Z,y) and return in-sample predictions yhat (same length as y).
         * For speed we keep it in-sample; if you later want cross-fitting, wrap at a higher level.
         */
        double[] fitPredict(double[][] Z, double[] y);
    }

    private static final class ResidualCache {
        private final ConcurrentHashMap<Key, double[]> cache = new ConcurrentHashMap<>();

        private static long rowsSignature(List<Integer> rows) {
            long h = 1469598103934665603L; // FNV-1a 64
            for (int r : rows) {
                h ^= (r * 0x9E3779B97F4A7C15L);
                h *= 1099511628211L;
            }
            h ^= rows.size();
            h *= 1099511628211L;
            return h;
        }

        double[] getResiduals(GcmIndependenceTest owner, int targetIdx, int[] zIdx, List<Integer> rows) {
            Key key = new Key(targetIdx, zIdx, rowsSignature(rows));
            return cache.computeIfAbsent(key, k -> owner.fitAndResidualize(targetIdx, zIdx, rows));
        }

        void clear() {
            cache.clear();
        }

        private record Key(int target, int[] z, long rowsSig) {
            Key {
                // defensive copy so key is immutable
                z = (z == null ? new int[0] : Arrays.copyOf(z, z.length));
            }

            @Override
            public int hashCode() {
                int h = Integer.hashCode(target);
                h = 31 * h + Arrays.hashCode(z);
                h = 31 * h + Long.hashCode(rowsSig);
                return h;
            }

            @Override
            public boolean equals(Object o) {
                if (!(o instanceof Key k)) return false;
                return target == k.target && rowsSig == k.rowsSig && Arrays.equals(z, k.z);
            }
        }
    }

    /**
     * Linear ridge regression with intercept:
     * minimize ||y - (b0 + Zb)||^2 + ridge * ||b||^2
     * <p>
     * Uses normal equations; OK for small-ish |Z|.
     */
    private static final class LinearRidgeRegressor implements Regressor {
        private final double ridge;

        LinearRidgeRegressor(double ridge) {
            this.ridge = ridge;
        }

        @Override
        public double[] fitPredict(double[][] Z, double[] y) {
            int n = y.length;
            int p = Z[0].length;

            // center columns and y (intercept handled by centering)
            double[] meanZ = new double[p];
            for (int j = 0; j < p; j++) {
                double s = 0;
                for (int i = 0; i < n; i++) s += Z[i][j];
                meanZ[j] = s / n;
            }
            double meany = 0;
            for (double v : y) meany += v;
            meany /= n;

            double[][] X = new double[n][p];
            double[] yc = new double[n];
            for (int i = 0; i < n; i++) {
                yc[i] = y[i] - meany;
                for (int j = 0; j < p; j++) X[i][j] = Z[i][j] - meanZ[j];
            }

            // Compute XtX + ridge*I and Xty
            double[][] A = new double[p][p];
            double[] b = new double[p];

            for (int i = 0; i < n; i++) {
                double yi = yc[i];
                for (int j = 0; j < p; j++) {
                    double xij = X[i][j];
                    b[j] += xij * yi;
                    for (int k = j; k < p; k++) {
                        A[j][k] += xij * X[i][k];
                    }
                }
            }
            for (int j = 0; j < p; j++) {
                A[j][j] += ridge;
                for (int k = j + 1; k < p; k++) A[k][j] = A[j][k];
            }

            double[] beta = solveSymmetric(A, b);

            // predict: yhat = meany + (Z-meanZ)beta
            double[] yhat = new double[n];
            for (int i = 0; i < n; i++) {
                double s = meany;
                for (int j = 0; j < p; j++) s += (Z[i][j] - meanZ[j]) * beta[j];
                yhat[i] = s;
            }
            return yhat;
        }
    }

    // ==================== tiny symmetric solver ====================
    // Cholesky for SPD; ridge makes it SPD in practice unless you have NaNs/inf or extreme collinearity.

    /**
     * Random Fourier Features + ridge regression.
     * Features: phi(z) = sqrt(2/D) * cos(W z + b)
     * Then ridge on phi(z) (with intercept handled by centering).
     * <p>
     * This is a cheap nonlinear conditional mean model.
     */
    private static final class RffRidgeRegressor implements Regressor {
        private final double ridge;
        private final int D;
        private final double sigma;
        private final long seed;

        RffRidgeRegressor(double ridge, int D, double sigma, long seed) {
            this.ridge = ridge;
            this.D = D;
            this.sigma = sigma;
            this.seed = seed;
        }

        private static double[] ridgeFitPredictCentered(double[][] X, double[] y, double ridge) {
            int n = y.length;
            int d = X[0].length;

            // center X columns and y
            double[] meanX = new double[d];
            for (int j = 0; j < d; j++) {
                double s = 0;
                for (int i = 0; i < n; i++) s += X[i][j];
                meanX[j] = s / n;
            }
            double meany = 0;
            for (double v : y) meany += v;
            meany /= n;

            double[][] Xc = new double[n][d];
            double[] yc = new double[n];
            for (int i = 0; i < n; i++) {
                yc[i] = y[i] - meany;
                for (int j = 0; j < d; j++) Xc[i][j] = X[i][j] - meanX[j];
            }

            double[][] A = new double[d][d];
            double[] b = new double[d];

            for (int i = 0; i < n; i++) {
                double yi = yc[i];
                for (int j = 0; j < d; j++) {
                    double xij = Xc[i][j];
                    b[j] += xij * yi;
                    for (int k = j; k < d; k++) {
                        A[j][k] += xij * Xc[i][k];
                    }
                }
            }
            for (int j = 0; j < d; j++) {
                A[j][j] += ridge;
                for (int k = j + 1; k < d; k++) A[k][j] = A[j][k];
            }

            double[] beta = solveSymmetric(A, b);

            double[] yhat = new double[n];
            for (int i = 0; i < n; i++) {
                double s = meany;
                for (int j = 0; j < d; j++) s += (X[i][j] - meanX[j]) * beta[j];
                yhat[i] = s;
            }
            return yhat;
        }

        @Override
        public double[] fitPredict(double[][] Z, double[] y) {
            int n = y.length;
            int p = Z[0].length;

            // standardize Z columns (important for RFF stability)
            double[] mean = new double[p];
            double[] sd = new double[p];
            for (int j = 0; j < p; j++) {
                double s = 0;
                for (int i = 0; i < n; i++) s += Z[i][j];
                mean[j] = s / n;
                double v = 0;
                for (int i = 0; i < n; i++) {
                    double d = Z[i][j] - mean[j];
                    v += d * d;
                }
                sd[j] = sqrt(v / max(1, n - 1));
                if (!(sd[j] > 0)) sd[j] = 1.0;
            }

            Random rng = new Random(seed);
            // W: D x p with N(0, 1/sigma^2)
            double[][] W = new double[D][p];
            for (int k = 0; k < D; k++) {
                for (int j = 0; j < p; j++) {
                    W[k][j] = rng.nextGaussian() / sigma;
                }
            }
            // b: D uniform [0, 2pi)
            double[] phase = new double[D];
            for (int k = 0; k < D; k++) phase[k] = 2.0 * PI * rng.nextDouble();

            double scale = sqrt(2.0 / D);

            // Build Phi (n x D)
            double[][] Phi = new double[n][D];
            for (int i = 0; i < n; i++) {
                for (int k = 0; k < D; k++) {
                    double dot = 0;
                    for (int j = 0; j < p; j++) {
                        dot += W[k][j] * ((Z[i][j] - mean[j]) / sd[j]);
                    }
                    Phi[i][k] = scale * cos(dot + phase[k]);
                }
            }

            // Now ridge regression Phi -> y (reuse linear ridge solver)
            return ridgeFitPredictCentered(Phi, y, ridge);
        }
    }
}