package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradLogger;
import org.ejml.simple.SimpleMatrix;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

/**
 * GIN (Generalized Independent Noise) conditional independence test. Reduces CI X ⟂ Y | S to unconditional independence
 * of regression residuals:
 * <ul>
 *  <li>rX := X - E[X | S \ {X}]</li
 *  <li>rY := Y - E[Y | S \ {Y}]</li>
 * </ul>
 * <p>
 * Then tests rX ⟂ rY using an unconditional backend (dCor by default).
 * <p>
 * Assumes continuous data. For mixed data, provide a different Regressor (e.g., RF).
 */
public class IndTestGin implements IndependenceTest {

    private static final int N_MAX_DCOR = 4000; // soft guard for O(n^2) memory

    private final DataSet data;
    // Cache: key=(targetIdx, sortedPredictorIdxCsv) -> residual vector
    private final Map<String, double[]> residCache = new ConcurrentHashMap<>();
    private boolean verbose = false;

    // ---- Public configuration ------------------------------------------------
    private double alpha = 0.05;
    private Regressor regressor;
    private UncondIndTest backend;
    private int numPerm = 0;  // 0 = analytic/approx only for dCor; >0 enables permutation
    private double lastP = Double.NaN;

    public IndTestGin(DataSet data) {
        this(data, new OlsRidge(1e-8), new DistanceCorrTest());
    }

    public IndTestGin(DataSet data, Regressor regressor, UncondIndTest backend) {
        this.data = Objects.requireNonNull(data, "data");
        this.regressor = Objects.requireNonNull(regressor, "regressor");
        this.backend = Objects.requireNonNull(backend, "backend");
    }

    private static boolean isFiniteVec(double[] a) {
        for (double v : a) if (!Double.isFinite(v)) return false;
        return true;
    }

    public String getDescription() {
        String base = "GIN (residual-independence) with " + regressor.name() + " + " + backend.name();
        if (backend instanceof DistanceCorrTest && numPerm > 0) base += " (perm=" + numPerm + ")";
        if (backend instanceof DistanceCorrTest && numPerm == 0) base += " (approx p)";
        return base;
    }

    public void setNumPermutations(int n) {
        this.numPerm = Math.max(0, n);
    }

    public void setRegressor(Regressor r) {
        this.regressor = Objects.requireNonNull(r);
        residCache.clear();
    }

    public void setBackend(UncondIndTest b) {
        this.backend = Objects.requireNonNull(b);
    }

    @Override
    public DataSet getData() {
        return data;
    }

    @Override
    public boolean isVerbose() {
        return verbose;
    }

    @Override
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    @Override
    public List<Node> getVariables() {
        return data.getVariables();
    }

    @Override
    public double getAlpha() {
        return alpha;
    }

    // ---- IndependenceTest API ------------------------------------------------

    public void setAlpha(double alpha) {
        this.alpha = alpha;
    }

    @Override
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> cond) {
        int ix = data.getColumn(x);
        int iy = data.getColumn(y);

        // Build S\{X} and S\{Y}
        List<Node> sMinusX = new ArrayList<>(cond);
        sMinusX.remove(x);
        List<Node> sMinusY = new ArrayList<>(cond);
        sMinusY.remove(y);

        double[] rX = residualFor(ix, sMinusX);
        double[] rY = residualFor(iy, sMinusY);

        // Finite checks to avoid NaN propagation
        if (!isFiniteVec(rX) || !isFiniteVec(rY)) {
            lastP = 1.0; // fail-to-independence if bad numerics
            boolean ind = lastP > alpha;
            return new IndependenceResult(new IndependenceFact(x, y, cond), ind, lastP, alpha - lastP);
        }

        double p;
        if (backend instanceof DistanceCorrTest) {
            DistanceCorrTest d = (DistanceCorrTest) backend;
            // Memory guard for very large n
            if (rX.length > N_MAX_DCOR) {
                p = new PearsonCorrTest().pValue(rX, rY);
            } else {
                p = (numPerm > 0) ? d.pValuePermuted(rX, rY, numPerm) : d.pValue(rX, rY);
            }
        } else {
            p = backend.pValue(rX, rY);
        }

        lastP = p;
        boolean ind = p > alpha;

        if (verbose) {
            TetradLogger.getInstance().log(new IndependenceFact(x, y, cond) + " p = " + p);
        }

        // Keep the 4th argument exactly as in your version (alpha - p) to preserve downstream behavior.
        return new IndependenceResult(new IndependenceFact(x, y, cond), ind, p, alpha - p);
    }

    // ---- Residualization with caching ---------------------------------------

    public double getPValue() {
        return lastP;
    }

    private double[] residualFor(int targetIdx, List<Node> predictors) {
        String key = cacheKey(targetIdx, predictors);
        return residCache.computeIfAbsent(key, k -> {
            double[] y = column(targetIdx);
            if (predictors == null || predictors.isEmpty()) {
                double mean = Arrays.stream(y).average().orElse(0.0);
                double[] r = new double[y.length];
                for (int i = 0; i < y.length; i++) r[i] = y[i] - mean;
                return r;
            }
            double[][] X = columns(predictors);
            return regressor.residuals(y, X);
        });
    }

    private String cacheKey(int targetIdx, List<Node> preds) {
        int[] idxs = (preds == null || preds.isEmpty())
                ? new int[0]
                : preds.stream().mapToInt(data::getColumn).sorted().toArray();
        StringBuilder sb = new StringBuilder(8 + 3 * idxs.length);
        sb.append(targetIdx).append('|');
        for (int k = 0; k < idxs.length; k++) {
            if (k > 0) sb.append(',');
            sb.append(idxs[k]);
        }
        return sb.toString();
    }

    private double[] column(int j) {
        int n = data.getNumRows();
        double[] col = new double[n];
        for (int i = 0; i < n; i++) col[i] = data.getDouble(i, j);
        return col;
    }

    private double[][] columns(List<Node> vars) {
        int p = vars.size();
        int n = data.getNumRows();
        double[][] X = new double[n][p];
        for (int k = 0; k < p; k++) {
            int j = data.getColumn(vars.get(k));
            for (int i = 0; i < n; i++) X[i][k] = data.getDouble(i, j);
        }
        return X;
    }

    // ---- Residual regressor API ---------------------------------------------

    /**
     * How we compute residuals.
     */
    public interface Regressor {
        /**
         * Fit f: target ~ predictors; return residuals (target - fitted).
         */
        double[] residuals(double[] target, double[][] predictors);

        String name();
    }

    // ---- Unconditional backend API ------------------------------------------

    /**
     * Unconditional independence backend rX ⟂ rY → p-value
     */
    public interface UncondIndTest {
        double pValue(double[] x, double[] y);

        String name();
    }

    // ---- Default Regressor: OLS (ridge-stabilized), standardized ------------

    public static final class OlsRidge implements Regressor {
        private final double ridge;

        public OlsRidge(double ridge) {
            this.ridge = ridge;
        }

        @Override
        public String name() {
            return "OLS(ridge=" + ridge + ")";
        }

        @Override
        public double[] residuals(double[] target, double[][] predictors) {
            // Empty/degenerate guard
            if (predictors == null || predictors.length == 0 || predictors[0].length == 0) {
                double mean = Arrays.stream(target).average().orElse(0.0);
                double[] r = new double[target.length];
                for (int i = 0; i < target.length; i++) r[i] = target[i] - mean;
                return r;
            }

            int n = target.length, p = predictors[0].length;

            // Build design matrix with intercept; standardize predictors for stability.
            double[][] Z = new double[n][p + 1];
            for (int i = 0; i < n; i++) Z[i][0] = 1.0;

            for (int j = 0; j < p; j++) {
                double mean = 0, var = 0;
                for (int i = 0; i < n; i++) mean += predictors[i][j];
                mean /= n;
                for (int i = 0; i < n; i++) {
                    double v = predictors[i][j] - mean;
                    Z[i][j + 1] = v;
                    var += v * v;
                }
                double sd = Math.sqrt(var / Math.max(1, n - 1));
                if (sd > 0) for (int i = 0; i < n; i++) Z[i][j + 1] /= sd;
            }

            SimpleMatrix X = new SimpleMatrix(Z);
            SimpleMatrix y = new SimpleMatrix(n, 1, true, target.clone());

            // Prefer stable least-squares solve; EJML will choose QR/SVD internally.
            // If you want explicit ridge, add it to the normal matrix before solve.
            SimpleMatrix beta;
            try {
                // Explicit ridge on normal equations for a tiny stabilization (skip intercept col)
                SimpleMatrix Xt = X.transpose();
                SimpleMatrix XtX = Xt.mult(X);
                for (int j = 1; j < p + 1; j++) XtX.set(j, j, XtX.get(j, j) + ridge);
                beta = XtX.pseudoInverse().mult(Xt).mult(y);
            } catch (Exception ignored) {
                // Fallback to X.solve if pseudoInverse runs into trouble
                beta = X.solve(y);
            }

            SimpleMatrix yhat = X.mult(beta);

            double[] resid = new double[n];
            for (int i = 0; i < n; i++) resid[i] = y.get(i, 0) - yhat.get(i, 0);

            // Center residuals
            double mean = Arrays.stream(resid).average().orElse(0.0);
            for (int i = 0; i < n; i++) resid[i] -= mean;

            return resid;
        }
    }

    // ---- Unconditional backends ---------------------------------------------

    /**
     * Fast Pearson correlation t-test (linear).
     */
    public static final class PearsonCorrTest implements UncondIndTest {
        private static double mean(double[] a) {
            return Arrays.stream(a).average().orElse(0.0);
        }

        private static double clamp01(double p) {
            return Math.max(0.0, Math.min(1.0, p));
        }

        // Simple t CDF complement using regularized incomplete beta (compact, dependency-free)
        private static double studentTCdfComplement(double t, int df) {
            double x = df / (df + t * t);
            double ib = regIncompleteBeta(x, 0.5 * df, 0.5);
            // Two-sided: survival(|t|) = 0.5 * I_x(df/2, 1/2)
            return 0.5 * ib;
        }

        private static double regIncompleteBeta(double x, double a, double b) {
            x = Math.max(0.0, Math.min(1.0, x));
            boolean flip = x > (a + 1) / (a + b + 2);
            if (flip) x = 1 - x;
            double cf = betaContinuedFraction(x, a, b) / a;
            double front = Math.exp(logGamma(a + b) - logGamma(a) - logGamma(b)
                                    + a * Math.log(Math.max(1e-16, x)) + b * Math.log(Math.max(1e-16, 1 - x)));
            double result = front * cf;
            if (flip) result = 1 - result;
            return Math.max(0.0, Math.min(1.0, result));
        }

        private static double betaContinuedFraction(double x, double a, double b) {
            final int MAX_IT = 200;
            final double EPS = 1e-10;
            double am = 1, bm = 1, az = 1, qab = a + b, qap = a + 1, qam = a - 1, bz = 1 - qab * x / qap;
            for (int m = 1, em = 1; m <= MAX_IT; m++, em++) {
                double tem = em * (b - em) * x / ((qam + em) * (a + em));
                double ap = az + tem * am;
                double bp = bz + tem * bm;
                tem = -(a + em) * (qab + em) * x / ((a + 2 * em) * (qap + em));
                double app = ap + tem * az;
                double bpp = bp + tem * bz;
                am = ap / bpp;
                bm = bp / bpp;
                az = app / bpp;
                bz = 1.0;
                if (Math.abs(app - ap) < EPS * Math.abs(app)) break;
            }
            return az;
        }

        private static double logGamma(double z) {
            double[] c = {676.5203681218851, -1259.1392167224028, 771.32342877765313,
                    -176.61502916214059, 12.507343278686905, -0.13857109526572012,
                    9.9843695780195716e-6, 1.5056327351493116e-7};
            int g = 7;
            if (z < 0.5) return Math.log(Math.PI) - Math.log(Math.sin(Math.PI * z)) - logGamma(1 - z);
            z -= 1;
            double x = 0.99999999999980993;
            for (int i = 0; i < c.length; i++) x += c[i] / (z + i + 1);
            double t = z + g + 0.5;
            return 0.5 * Math.log(2 * Math.PI) + (z + 0.5) * Math.log(t) - t + Math.log(x);
        }

        @Override
        public String name() {
            return "Pearson-t";
        }

        @Override
        public double pValue(double[] x, double[] y) {
            int n = x.length;
            double rx = mean(x), ry = mean(y);
            double sxx = 0, syy = 0, sxy = 0;
            for (int i = 0; i < n; i++) {
                double dx = x[i] - rx, dy = y[i] - ry;
                sxx += dx * dx;
                syy += dy * dy;
                sxy += dx * dy;
            }
            double r = sxy / Math.sqrt(Math.max(1e-16, sxx * syy));
            double t = r * Math.sqrt(Math.max(1, n - 2) / Math.max(1e-16, (1 - r * r)));
            double p = 2.0 * studentTCdfComplement(Math.abs(t), Math.max(1, n - 2));
            return clamp01(p);
        }
    }

    /**
     * Distance correlation (biased) with optional permutations. Note: analytic p-value here is an approximation; prefer
     * permutations when feasible.
     */
    public static final class DistanceCorrTest implements UncondIndTest {
        private static DcorrStats dcorr(double[] x, double[] y) {
            int n = x.length;
            double[][] ax = centeredDistance(x);
            double[][] ay = centeredDistance(y);
            double A = 0, B = 0, C = 0;
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    double axij = ax[i][j];
                    double ayij = ay[i][j];
                    A += axij * ayij;
                    B += axij * axij;
                    C += ayij * ayij;
                }
            }
            double dvarx = Math.max(1e-16, B / (n * (double) n));
            double dvary = Math.max(1e-16, C / (n * (double) n));
            double dcov = A / (n * (double) n);
            double dcor = dcov / Math.sqrt(dvarx * dvary);
            dcor = Math.max(0.0, Math.min(1.0, dcor));
            return new DcorrStats(n, dcor);
        }

        // O(n^2) centered distance matrix in 1D
        private static double[][] centeredDistance(double[] a) {
            int n = a.length;
            double[][] D = new double[n][n];
            double[] rowSum = new double[n];
            double[] colSum = new double[n];
            double colSumTotal = 0.0;

            for (int i = 0; i < n; i++) {
                double ai = a[i];
                for (int j = 0; j < n; j++) {
                    double d = Math.abs(ai - a[j]);
                    D[i][j] = d;
                    rowSum[i] += d;
                }
            }
            for (int j = 0; j < n; j++) {
                double s = 0;
                for (int i = 0; i < n; i++) s += D[i][j];
                colSum[j] = s;
                colSumTotal += s;
            }
            double invN = 1.0 / n;
            double invN2 = invN * invN;
            for (int i = 0; i < n; i++) {
                double ri = rowSum[i] * invN;
                for (int j = 0; j < n; j++) {
                    D[i][j] = D[i][j] - ri - colSum[j] * invN + colSumTotal * invN2;
                }
            }
            return D;
        }

        @Override
        public String name() {
            return "dCor";
        }

        @Override
        public double pValue(double[] x, double[] y) {
            DcorrStats s = dcorr(x, y);
            // Crude tail approximation; GUI can expose permutations to improve this.
            double z = s.n * s.dcor * s.dcor;
            double p = Math.exp(-z);
            return Math.max(0.0, Math.min(1.0, p));
        }

        /**
         * Permutation p-value that doesn't mutate inputs.
         */
        public double pValuePermuted(double[] x, double[] y, int perms) {
            DcorrStats obs = dcorr(x, y);
            double stat = obs.dcor;
            int n = x.length;

            int[] idx = IntStream.range(0, n).toArray();
            double[] ybuf = y.clone();
            int exceed = 0;

            for (int b = 0; b < perms; b++) {
                // Fisher–Yates shuffle on idx
                for (int i = n - 1; i > 0; i--) {
                    int j = RandomUtil.getInstance().nextInt(i + 1);
                    int tmp = idx[i];
                    idx[i] = idx[j];
                    idx[j] = tmp;
                }
                // y_perm = ybuf[idx]
                double[] yperm = new double[n];
                for (int i = 0; i < n; i++) yperm[i] = ybuf[idx[i]];

                double s = dcorr(x, yperm).dcor;
                if (s >= stat) exceed++;
            }
            return (exceed + 1.0) / (perms + 1.0);
        }

        private static class DcorrStats {
            final int n;
            final double dcor;

            DcorrStats(int n, double d) {
                this.n = n;
                this.dcor = d;
            }
        }
    }
}