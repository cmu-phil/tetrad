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
 *  <li>rX := X - E[X | S \ {X}]
 *  <li>rY := Y - E[Y | S \ {Y}]
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

    /**
     * Constructs an instance of IndTestGin with the specified dataset. Initializes default values for regressor and
     * backend components.
     *
     * @param data the dataset to be used for independence testing
     */
    public IndTestGin(DataSet data) {
        this(data, new OlsRidge(1e-8), new DistanceCorrTest());
    }

    /**
     * Constructs an instance of IndTestGin with the specified dataset, regressor, and backend components. This
     * constructor allows customization of the regressor and backend used in the independence tests.
     *
     * @param data      the dataset to be used for independence testing
     * @param regressor the regressor implementation to be used for regression analysis
     * @param backend   the unconditional independence test to be used as the backend
     */
    public IndTestGin(DataSet data, Regressor regressor, UncondIndTest backend) {
        this.data = Objects.requireNonNull(data, "data");
        this.regressor = Objects.requireNonNull(regressor, "regressor");
        this.backend = Objects.requireNonNull(backend, "backend");
    }

    private static boolean isFiniteVec(double[] a) {
        for (double v : a) if (!Double.isFinite(v)) return false;
        return true;
    }

    /**
     * Provides a string description of the current configuration of the independence test, including details about the
     * regressor and backend used, and additional information related to permutations if applicable.
     *
     * @return a descriptive string summarizing the configuration of the independence test
     */
    public String getDescription() {
        String base = "GIN (residual-independence) with " + regressor.name() + " + " + backend.name();
        if (backend instanceof DistanceCorrTest && numPerm > 0) base += " (perm=" + numPerm + ")";
        if (backend instanceof DistanceCorrTest && numPerm == 0) base += " (approx p)";
        return base;
    }

    /**
     * Sets the number of permutations to be used for the independence test. If n is negative, it will be set to 0.
     *
     * @param n the number of permutations to set
     */
    public void setNumPermutations(int n) {
        this.numPerm = Math.max(0, n);
    }

    /**
     * Sets the regressor to be used for the independence test. If r is null, it will throw a NullPointerException.
     *
     * @param r the regressor to set
     */
    public void setRegressor(Regressor r) {
        this.regressor = Objects.requireNonNull(r);
        residCache.clear();
    }

    /**
     * Sets the backend to be used for the independence test. If b is null, it will throw a NullPointerException.
     *
     * @param b the backend to set
     */
    public void setBackend(UncondIndTest b) {
        this.backend = Objects.requireNonNull(b);
    }

    /**
     * Returns the current regressor being used for the independence test.
     *
     * @return the current regressor
     */
    @Override
    public DataSet getData() {
        return data;
    }

    /**
     * Returns the current backend being used for the independence test.
     *
     * @return the current backend
     */
    @Override
    public boolean isVerbose() {
        return verbose;
    }

    /**
     * Sets the verbosity of the independence test.
     *
     * @param verbose True, if so.
     */
    @Override
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Returns the variables involved in the independence test.
     *
     * @return the variables involved in the independence test
     */
    @Override
    public List<Node> getVariables() {
        return data.getVariables();
    }

    /**
     * Returns the significance level for the independence test.
     *
     * @return the significance level
     */
    @Override
    public double getAlpha() {
        return alpha;
    }

    // ---- IndependenceTest API ------------------------------------------------

    /**
     * Sets the significance level for the independence test.
     *
     * @param alpha the significance level to set
     */
    @Override
    public void setAlpha(double alpha) {
        this.alpha = alpha;
    }

    /**
     * Checks the independence of two given nodes, conditioned on a set of other nodes. It computes residuals for the
     * nodes and uses the backend independence test to determine the independence status along with the p-value.
     *
     * @param x    the first node for the independence test
     * @param y    the second node for the independence test
     * @param cond the set of conditioning nodes
     * @return the result of the independence test containing details such as whether the nodes are independent, the
     * p-value, and additional test information
     */
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

    /**
     * Returns the p-value from the most recent independence test.
     *
     * @return the p-value of the last independence test
     */
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
         * Computes the residuals, which are the differences between the target values and the predicted values obtained
         * using the given predictors.
         *
         * @param target     an array of target values that represent the observations.
         * @param predictors a 2D array where each row represents a set of predictor variables for a corresponding
         *                   observation.
         * @return an array of residuals for each observation, calculated as the difference between the target value and
         * the predicted value for that observation.
         */
        double[] residuals(double[] target, double[][] predictors);

        /**
         * Returns the name of the regressor.
         *
         * @return the name representing this regressor.
         */
        String name();
    }

    // ---- Unconditional backend API ------------------------------------------

    /**
     * Unconditional independence backend rX ⟂ rY → p-value
     */
    public interface UncondIndTest {
        /**
         * Calculates the p-value to test the unconditional independence between the variables represented by the input
         * arrays.
         *
         * @param x an array of doubles representing the first variable in the independence test
         * @param y an array of doubles representing the second variable in the independence test
         * @return the calculated p-value indicating the strength of evidence against the null hypothesis of
         * independence
         */
        double pValue(double[] x, double[] y);

        /**
         * Returns the name of the independence test.
         *
         * @return a string representing the name of the independence test
         */
        String name();
    }

    // ---- Default Regressor: OLS (ridge-stabilized), standardized ------------

    /**
     * Represents a ridge-regularized ordinary least squares (OLS) regressor. This implementation combines standard
     * least-squares regression with a ridge penalty to enhance numerical stability and address multicollinearity when
     * solving the regression problem.
     * <p>
     * This class implements the {@code Regressor} interface, providing methods to compute residuals and return a
     * descriptive name for the regressor.
     */
    public static final class OlsRidge implements Regressor {
        private final double ridge;

        /**
         * Constructs an instance of OlsRidge with the specified ridge parameter.
         *
         * @param ridge The ridge regression parameter, which is used to control the regularization strength in ridge
         *              regression. A higher value indicates stronger regularization to prevent overfitting.
         */
        public OlsRidge(double ridge) {
            this.ridge = ridge;
        }

        /**
         * Returns the name of the regression model, including the ridge parameter value.
         *
         * @return A string representing the name of the model in the format "OLS(ridge={value})", where {value} is the
         * ridge parameter used in the model.
         */
        @Override
        public String name() {
            return "OLS(ridge=" + ridge + ")";
        }

        /**
         * Computes the residuals of a regression model by fitting the target values to the predictors using a
         * least-squares approach with ridge stabilization. The predictors are standardized, and an intercept is
         * included in the model. If no predictors are provided, the residuals are calculated based on the mean of the
         * target values.
         *
         * @param target     The array of target values (dependent variable) for the model.
         * @param predictors The 2D array of predictors (independent variables) used in the regression. Each row
         *                   corresponds to a data point, and each column corresponds to a predictor variable.
         * @return An array of residuals, calculated as the difference between the target values and the predicted
         * values, centered around zero.
         */
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
        /**
         * Default constructor for the PearsonCorrTest class. This class implements a statistical test for assessing the
         * null hypothesis that two datasets are uncorrelated using the Pearson correlation coefficient.
         */
        public PearsonCorrTest() {
        }

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

        /**
         * Returns the name of the test represented by this class.
         *
         * @return the name of the test, which is "Pearson-t".
         */
        @Override
        public String name() {
            return "Pearson-t";
        }

        /**
         * Computes the p-value for testing the null hypothesis that two datasets are uncorrelated, based on the Pearson
         * correlation coefficient. The p-value is derived from the t-distribution with the given sample sizes.
         *
         * @param x the first dataset, represented as an array of doubles
         * @param y the second dataset, represented as an array of doubles
         * @return the p-value for the hypothesis test, clamped to the range [0, 1]
         */
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
        /**
         * Constructs an instance of the DistanceCorrTest. The DistanceCorrTest class provides methods for computing
         * distance correlation-based statistical tests, including the calculation of p-values and permutation-based
         * p-values for input data arrays.
         */
        public DistanceCorrTest() {
        }

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

        /**
         * Returns the name of the test.
         *
         * @return the name "dCor"
         */
        @Override
        public String name() {
            return "dCor";
        }

        /**
         * Computes the p-value for the given input arrays using a distance correlation test. The method relies on a
         * crude tail approximation for calculating the p-value. The result is constrained to the range [0.0, 1.0].
         *
         * @param x the first array of data values
         * @param y the second array of data values
         * @return the computed p-value based on the distance correlation test
         */
        @Override
        public double pValue(double[] x, double[] y) {
            DcorrStats s = dcorr(x, y);
            // Crude tail approximation; GUI can expose permutations to improve this.
            double z = s.n * s.dcor * s.dcor;
            double p = Math.exp(-z);
            return Math.max(0.0, Math.min(1.0, p));
        }

        /**
         * Computes the permutation-based p-value for the distance correlation statistic. The method performs a
         * specified number of permutations of the second data array to compute the proportion of permuted statistics
         * that are greater than or equal to the observed statistic. The result is constrained to the range [0.0, 1.0].
         *
         * @param x     the first array of data values
         * @param y     the second array of data values
         * @param perms the number of permutations to use in the computation
         * @return the computed permutation-based p-value
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