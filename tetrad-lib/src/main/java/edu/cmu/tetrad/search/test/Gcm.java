package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataTransforms;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.util.FastMath;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.apache.commons.math3.util.FastMath.*;

/**
 * Generalized Covariance Measure (GCM) conditional independence test.
 *
 * <p>
 * Tests conditional independence of X and Y given Z by measuring residual
 * dependence after regressing out the effect of Z from each variable.
 * The test follows the generalized covariance framework of Shah and Peters
 * and is suitable for nonlinear regression models when good predictors
 * of conditional means are available.
 * </p>
 *
 * <p><b>Test construction.</b>
 * To test {@code X ⟂⟂ Y | Z}:
 * </p>
 * <ol>
 *   <li>Fit regression models
 *       {@code x̂(Z) ≈ E[X | Z]} and {@code ŷ(Z) ≈ E[Y | Z]}.</li>
 *   <li>Compute residuals
 *       {@code rX = X − x̂(Z)} and {@code rY = Y − ŷ(Z)}.</li>
 *   <li>Form the elementwise product {@code u = rX · rY}.</li>
 *   <li>Compute the test statistic
 *       <pre>
 *         T = sqrt(n) · mean(u) / sd(u),
 *       </pre>
 *       which is asymptotically standard normal under the null hypothesis,
 *       given mild regularity conditions.</li>
 * </ol>
 *
 * <p><b>Interpretation.</b>
 * Under {@code X ⟂⟂ Y | Z}, the residuals {@code rX} and {@code rY} are
 * uncorrelated, so {@code E[rX · rY] = 0}. Systematic deviation from zero
 * indicates conditional dependence.</p>
 *
 * <p><b>Regression models.</b>
 * The power and validity of the test depend critically on the quality of the
 * regressors used to approximate {@code E[X | Z]} and {@code E[Y | Z]}.
 * In this implementation, regression options include linear and nonlinear
 * models (e.g., ridge regression with random Fourier features).</p>
 *
 * <p><b>Practical considerations.</b>
 * <ul>
 *   <li>The test is only as reliable as the underlying regression fits.</li>
 *   <li>Performance in causal search depends strongly on caching:
 *       residuals should be cached by {@code (target, conditioning set Z, rows)}
 *       to avoid repeated regression during search.</li>
 *   <li>The test is fast and scalable when regressions are reused,
 *       making it suitable for constraint-based structure learning.</li>
 * </ul>
 *
 * <p><b>Intended use.</b>
 * GCM is well-suited for conditional independence testing in nonlinear settings
 * when approximate conditional mean models are available, and serves as a
 * computationally efficient alternative to fully nonparametric kernel-based tests.
 * </p>
 */
public final class Gcm implements IndependenceTest {

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

    // -------------------- cross-fitting config --------------------
    private boolean crossFit = true;   // turn on to debias residuals
    private int crossFitFolds = 2;     // start with 2-fold (cheap). Try 5 later.
    private long crossFitSeed = 12345L;

    /**
     * Constructs a new instance of the Gcm class using the provided continuous data set
     * and the specified alpha value. The alpha value is used as the significance level
     * for statistical tests. This constructor validates that the input data set is
     * continuous; otherwise, an exception is thrown.
     *
     * @param data the {@code DataSet} object representing the data to be analyzed.
     *             Must be continuous; otherwise, an {@code IllegalArgumentException}
     *             is thrown.
     * @param alpha the significance level for statistical tests, expressed as a
     *              double. This value must be in the range (0, 1).
     * @throws IllegalArgumentException if the provided data set is not continuous.
     */
    public Gcm(DataSet data, double alpha) {
        if (!data.isContinuous()) throw new IllegalArgumentException("GCM test currently requires continuous DataSet.");
        this.data = DataTransforms.standardizeData(data);
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

    /**
     * Generates a subset independence test that is restricted to the provided variables.
     * The dataset remains the same, but the list of variables is restricted to the subset.
     * This ensures low overhead while allowing for operations on the desired subset.
     *
     * @param vars the list of variables defining the subset for the independence test
     * @return an IndependenceTest instance configured to operate on the specified subset of variables
     */
    @Override
    public IndependenceTest indTestSubset(List<Node> vars) {
        // Simple (safe) implementation: keep the same dataset, just restrict variable list.
        // If you prefer true sub-DataSet, you can build one, but this keeps overhead low.
        Gcm t = new Gcm(this.data, this.alpha);
        t.setVerbose(this.verbose);
        t.setRows(this.rows);
        t.setRegressorType(this.regressorType);
        t.setRidge(this.ridge);
        t.setRffFeatures(autoRffFeatures(this.data.getNumRows(), vars.size()));
        t.setRffSigma(this.rffSigma);
        t.setRffSeed(this.rffSeed);
        return t;
    }

    private int autoRffFeatures(int n, int p) {
        int d = (int) FastMath.ceil(4.0 * FastMath.sqrt(n)); // tune 3–8
        d = FastMath.max(d, 200);
        d = FastMath.min(d, 2000); // cap for sanity
        // optionally scale a bit with p:
        d = FastMath.min(2000, d + 50 * p);
        return d;
    }

    /**
     * Checks the independence between two nodes given a conditioning set of nodes.
     *
     * @param x the first node whose independence is being evaluated
     * @param y the second node whose independence is being evaluated
     * @param z the set of nodes conditioned upon during the independence test
     * @return an IndependenceResult containing the independence fact, the determination of independence,
     *         the p-value of the test, and the difference between the test threshold and the p-value
     */
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

    /**
     * Calculates the p-value for testing conditional independence between two variables
     * given a set of conditioning variables in the context of a statistical model.
     *
     * @param x the first node (variable) being tested for independence.
     * @param y the second node (variable) being tested for independence.
     * @param z the set of nodes (variables) that condition the independence test.
     * @return the p-value indicating the likelihood that the null hypothesis of independence holds.
     *         A smaller value suggests a stronger evidence of dependence.
     * @throws NullPointerException if either x, y, or z is null.
     */
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

        int zx = z.size();

        // Basic df sanity: the CLT/studentization needs "enough" samples.
        // This is not Fisher-Z df; it’s just a guard.
        if (n <= max(10, zx + 5)) {
            // not enough to be meaningful; be conservative: call dependent
            lastT = Double.NaN;
            lastP = 0.0;
            return 0.0;
        }

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

    /**
     * Enables or disables cross-fitting in the model. Cross-fitting is a
     * statistical technique used to mitigate overfitting by splitting
     * the data into folds for training and testing. When this method
     * is called, the internal cache is cleared to ensure consistency
     * with the updated setting.
     *
     * @param crossFit a boolean value indicating whether cross-fitting
     *                 should be enabled ({@code true}) or disabled ({@code false}).
     */
    public void setCrossFit(boolean crossFit) {
        this.crossFit = crossFit;
        cache.clear();
    }

    /**
     * Sets the number of folds to be used in the cross-fitting process. Cross-fitting is a
     * statistical technique for reducing overfitting by splitting data into multiple folds
     * for training and testing. The value must be at least 2, as fewer folds are not valid
     * for this method.
     *
     * Changing the number of folds will clear the internal cache to ensure that the settings
     * are applied consistently.
     *
     * @param k the number of folds for cross-fitting; must be greater than or equal to 2.
     * @throws IllegalArgumentException if the provided value is less than 2.
     */
    public void setCrossFitFolds(int k) {
        if (k < 2) throw new IllegalArgumentException("crossFitFolds must be >= 2");
        this.crossFitFolds = k;
        cache.clear();
    }

    // -------------------- rows --------------------

    /**
     * Retrieves a list of data sets associated with the current instance.
     *
     * @return a {@code List} of {@code DataSet} objects representing the data sets.
     */
    @Override
    public List<DataSet> getDataSets() {
        return List.of(data);
    }

    /**
     * Sets the list of row indices and clears the associated cache to ensure consistency.
     *
     * @param rows a list of integers representing the row indices to be set
     */
    public void setRows(List<Integer> rows) {
        this.rows = rows;
        cache.clear(); // rows changes => invalidate cache
    }

    /**
     * Sets the seed for the cross-fitting process used in the model. The seed ensures
     * reproducibility in the random number generation process associated with cross-fitting.
     * Changing the seed will clear the internal cache to ensure consistency with the updated
     * settings.
     *
     * @param seed the seed value for random number generation in cross-fitting; must be
     *             a valid long value.
     */
    public void setCrossFitSeed(long seed) {
        this.crossFitSeed = seed;
        cache.clear();
    }


    // -------------------- regressor config --------------------

    private List<Integer> listRows() {
        if (rows != null) return rows;
        int n = data.getNumRows();
        List<Integer> r = new ArrayList<>(n);
        for (int i = 0; i < n; i++) r.add(i);
        return r;
    }

    /**
     * Sets the type of regressor to be used in the model. If the provided regressor type
     * is null, it defaults to {@code RegressorType.LINEAR_RIDGE}. The method also clears
     * the associated cache to ensure that the changes take effect immediately.
     *
     * @param t the {@code RegressorType} to be applied. Acceptable values include:
     *          {@code RegressorType.LINEAR_RIDGE} for regularized linear ridge regression, or
     *          {@code RegressorType.RFF_RIDGE} for random Fourier features ridge regression.
     */
    public void setRegressorType(RegressorType t) {
        this.regressorType = (t == null ? RegressorType.LINEAR_RIDGE : t);
        cache.clear();
    }

    /**
     * Sets the ridge parameter for regularization in the regression model.
     * The ridge parameter must be a non-negative value. Setting this parameter
     * modifies the model's behavior by applying ridge regularization, which helps
     * prevent overfitting. The method also clears the cache to ensure that the changes
     * take effect immediately.
     *
     * @param ridge the ridge regularization parameter to be set; must be greater than or equal to 0.
     *              A value of 0 corresponds to no regularization, while larger values increase
     *              the regularization strength.
     * @throws IllegalArgumentException if the provided ridge parameter is negative.
     */
    public void setRidge(double ridge) {
        if (ridge < 0) throw new IllegalArgumentException("ridge must be >= 0");
        this.ridge = ridge;
        cache.clear();
    }

    /**
     * Sets the number of random Fourier features (RFF) to be used in the model.
     * The number of features must be a positive integer greater than or equal to 1.
     * This parameter controls the dimensionality of the random feature space and
     * directly influences the model's capacity and accuracy.
     *
     * Updates to this parameter clear the internal cache to ensure consistency
     * with the new settings.
     *
     * @param d the number of random Fourier features to be set; must be >= 1.
     * @throws IllegalArgumentException if the provided value is less than 1.
     */
    public void setRffFeatures(int d) {
        if (d < 1) throw new IllegalArgumentException("rffFeatures must be >= 1");
        this.rffFeatures = d;
        cache.clear();
    }

    /**
     * Sets the bandwidth parameter (sigma) for random Fourier features (RFF).
     * The sigma parameter defines the scale of the RFF Gaussian kernel and must be a positive value.
     * Updating this parameter also clears the associated cache to ensure consistency with the new settings.
     *
     * @param sigma the bandwidth parameter for RFF; must be greater than 0.
     * @throws IllegalArgumentException if the provided sigma value is not greater than 0.
     */
    public void setRffSigma(double sigma) {
        if (!(sigma > 0)) throw new IllegalArgumentException("rffSigma must be > 0");
        this.rffSigma = sigma;
        cache.clear();
    }

    /**
     * Sets the seed value for the random Fourier features (RFF) used in the model.
     * The seed ensures reproducibility in the random number generation process
     * associated with RFF. Changing this seed modifies the randomness underlying
     * the RFF transformations and clears the associated cache to ensure consistency
     * of the updated model behavior.
     *
     * @param seed the seed value for random number generation in RFF; must be a valid long value.
     */
    public void setRffSeed(long seed) {
        this.rffSeed = seed;
        cache.clear();
    }

    /**
     * Retrieves the value of the last computed test statistic (T) in the model.
     *
     * @return the last computed test statistic as a double value.
     */
    public double getLastT() {
        return lastT;
    }

    // -------------------- internals --------------------

    /**
     * Retrieves the p-value from the most recently performed independence test.
     *
     * @return the last computed p-value as a double.
     */
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
            // mean residuals only
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
            for (int j = 0; j < p; j++) Z[i][j] = data.getDouble(row, zIdx[j]);
        }

        Regressor reg = switch (regressorType) {
            case LINEAR_RIDGE -> new LinearRidgeRegressor(ridge);
            case RFF_RIDGE -> new RffRidgeRegressor(
                    ridge, rffFeatures, rffSigma,
                    // keep deterministic but depend on target/z so cache is meaningful
                    rffSeed ^ targetIdx ^ Arrays.hashCode(zIdx)
            );
        };

        double[] yhat;
        if (!crossFit) {
            // in-sample (old behavior)
            yhat = reg.fit(Z, y).predict(Z);
        } else {
            yhat = crossFitPredict(reg, Z, y, crossFitFolds, crossFitSeed ^ targetIdx ^ Arrays.hashCode(zIdx));
        }

        double[] r = new double[n];
        for (int i = 0; i < n; i++) r[i] = y[i] - yhat[i];
        return r;
    }

    /**
     * K-fold cross-fitting:
     *  - split indices into folds deterministically
     *  - for each fold: fit on train, predict on fold
     */
    private static double[] crossFitPredict(Regressor reg, double[][] Z, double[] y, int K, long seed) {
        int n = y.length;
        K = FastMath.min(K, n);              // guard
        if (K < 2) K = 2;

        int[] foldOf = new int[n];
        Random rng = new Random(seed);

        // deterministic-ish fold assignment by shuffling indices once
        int[] idx = new int[n];
        for (int i = 0; i < n; i++) idx[i] = i;
        for (int i = n - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int tmp = idx[i]; idx[i] = idx[j]; idx[j] = tmp;
        }
        for (int t = 0; t < n; t++) foldOf[idx[t]] = (t % K);

        double[] yhat = new double[n];

        for (int k = 0; k < K; k++) {
            // count train/test sizes
            int nTest = 0;
            for (int i = 0; i < n; i++) if (foldOf[i] == k) nTest++;
            int nTrain = n - nTest;
            if (nTrain <= 1 || nTest == 0) {
                // degenerate: fallback to in-sample
                double[] tmp = reg.fit(Z, y).predict(Z);
                System.arraycopy(tmp, 0, yhat, 0, n);
                return yhat;
            }

            // build train arrays
            double[][] Ztr = new double[nTrain][];
            double[] ytr = new double[nTrain];
            double[][] Zte = new double[nTest][];
            int[] teIdx = new int[nTest];

            int it = 0, ie = 0;
            for (int i = 0; i < n; i++) {
                if (foldOf[i] == k) {
                    Zte[ie] = Z[i];
                    teIdx[ie] = i;
                    ie++;
                } else {
                    Ztr[it] = Z[i];
                    ytr[it] = y[i];
                    it++;
                }
            }

            Regressor.Fitted fit = reg.fit(Ztr, ytr);
            double[] pred = fit.predict(Zte);
            for (int i = 0; i < nTest; i++) yhat[teIdx[i]] = pred[i];
        }

        return yhat;
    }

    // ==================== regression + residualization ====================

    // -------------------- public API knobs --------------------

    /**
     * Represents the type of regressor to be used in a statistical or machine learning model.
     * This enum provides options for different regression methods used in data analysis.
     *
     * Available regressor types:
     * - LINEAR_RIDGE: Indicates regularized linear ridge regression.
     * - RFF_RIDGE: Represents random Fourier features ridge regression, which is used
     *   to approximate kernel methods efficiently.
     */
    public enum RegressorType {

        /**
         * Indicates regularized linear ridge regression.
         * This regression method applies L2 regularization to reduce overfitting
         * by penalizing large coefficients, striking a balance between bias and variance.
         */
        LINEAR_RIDGE,

        /**
         * Represents random Fourier features ridge regression.
         * This regression method utilizes random Fourier features to approximate kernel
         * functions, enabling efficient large-scale regression modeling. It combines
         * the benefits of kernel methods and ridge regression while improving scalability.
         */
        RFF_RIDGE}

    // ==================== Regressors ====================

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

        double[] getResiduals(Gcm owner, int targetIdx, int[] zIdx, List<Integer> rows) {
//            Key key = new Key(targetIdx, zIdx, rowsSignature(rows));
            Key key = new Key(
                    targetIdx,
                    zIdx,
                    rowsSignature(rows),
                    owner.crossFit,
                    owner.crossFitFolds,
                    owner.crossFitSeed,
                    owner.regressorType,
                    owner.ridge,
                    owner.rffFeatures,
                    owner.rffSigma,
                    owner.rffSeed
            );

            return cache.computeIfAbsent(key, k -> owner.fitAndResidualize(targetIdx, zIdx, rows));
        }

        void clear() {
            cache.clear();
        }

        private record Key(int target, int[] z, long rowsSig,
                           boolean crossFit, int kFolds, long cfSeed,
                           RegressorType regType, double ridge,
                           int rffFeatures, double rffSigma, long rffSeed) {
            Key {
                z = (z == null ? new int[0] : Arrays.copyOf(z, z.length));
            }

            @Override public int hashCode() {
                int h = Integer.hashCode(target);
                h = 31 * h + Arrays.hashCode(z);
                h = 31 * h + Long.hashCode(rowsSig);

                h = 31 * h + Boolean.hashCode(crossFit);
                h = 31 * h + Integer.hashCode(kFolds);
                h = 31 * h + Long.hashCode(cfSeed);

                h = 31 * h + Objects.hashCode(regType);
                h = 31 * h + Double.hashCode(ridge);
                h = 31 * h + Integer.hashCode(rffFeatures);
                h = 31 * h + Double.hashCode(rffSigma);
                h = 31 * h + Long.hashCode(rffSeed);
                return h;
            }

            @Override public boolean equals(Object o) {
                if (!(o instanceof Key k)) return false;
                return target == k.target
                        && rowsSig == k.rowsSig
                        && crossFit == k.crossFit
                        && kFolds == k.kFolds
                        && cfSeed == k.cfSeed
                        && regType == k.regType
                        && Double.compare(ridge, k.ridge) == 0
                        && rffFeatures == k.rffFeatures
                        && Double.compare(rffSigma, k.rffSigma) == 0
                        && rffSeed == k.rffSeed
                        && Arrays.equals(z, k.z);
            }
        }
    }

    // ==================== Regressors ====================

    private interface Regressor {
        Fitted fit(double[][] Ztrain, double[] yTrain);

        interface Fitted {
            double[] predict(double[][] Ztest);
        }
    }

    /**
     * Linear ridge regression with intercept:
     *  minimize ||y - (b0 + Zb)||^2 + ridge * ||b||^2
     *
     * Returns a fitted model that can predict on new Z.
     */
    private static final class LinearRidgeRegressor implements Regressor {
        private final double ridge;
        LinearRidgeRegressor(double ridge) { this.ridge = ridge; }

        @Override
        public Fitted fit(double[][] Z, double[] y) {
            int n = y.length;
            int p = Z[0].length;

            // means for centering
            double[] meanZ = new double[p];
            for (int j = 0; j < p; j++) {
                double s = 0;
                for (int i = 0; i < n; i++) s += Z[i][j];
                meanZ[j] = s / n;
            }
            double meany = 0;
            for (double v : y) meany += v;
            meany /= n;

            // centered X, y
            double[][] X = new double[n][p];
            double[] yc = new double[n];
            for (int i = 0; i < n; i++) {
                yc[i] = y[i] - meany;
                for (int j = 0; j < p; j++) X[i][j] = Z[i][j] - meanZ[j];
            }

            // XtX + ridge I, Xty
            double[][] A = new double[p][p];
            double[] b = new double[p];

            for (int i = 0; i < n; i++) {
                double yi = yc[i];
                for (int j = 0; j < p; j++) {
                    double xij = X[i][j];
                    b[j] += xij * yi;
                    for (int k = j; k < p; k++) A[j][k] += xij * X[i][k];
                }
            }
            for (int j = 0; j < p; j++) {
                A[j][j] += ridge;
                for (int k = j + 1; k < p; k++) A[k][j] = A[j][k];
            }

            final double[] beta = solveSymmetric(A, b);

            double finalMeany = meany;
            return (double[][] Ztest) -> {
                int nt = Ztest.length;
                double[] yhat = new double[nt];
                for (int i = 0; i < nt; i++) {
                    double s = finalMeany;
                    for (int j = 0; j < p; j++) s += (Ztest[i][j] - meanZ[j]) * beta[j];
                    yhat[i] = s;
                }
                return yhat;
            };
        }
    }

    /**
     * Random Fourier Features + ridge regression:
     *  phi(z) = sqrt(2/D) * cos(W z + b), then ridge on phi(z)
     *
     * Fitted model can predict on new Z.
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

        @Override
        public Fitted fit(double[][] Z, double[] y) {
            int n = y.length;
            int p = Z[0].length;

            // standardize Z using train stats
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

            // draw random features
            Random rng = new Random(seed);
            double[][] W = new double[D][p];
            for (int k = 0; k < D; k++) {
                for (int j = 0; j < p; j++) W[k][j] = rng.nextGaussian() / sigma;
            }
            double[] phase = new double[D];
            for (int k = 0; k < D; k++) phase[k] = 2.0 * PI * rng.nextDouble();
            double scale = sqrt(2.0 / D);

            // build Phi(train)
            double[][] Phi = new double[n][D];
            for (int i = 0; i < n; i++) {
                for (int k = 0; k < D; k++) {
                    double dot = 0.0;
                    for (int j = 0; j < p; j++) dot += W[k][j] * ((Z[i][j] - mean[j]) / sd[j]);
                    Phi[i][k] = scale * cos(dot + phase[k]);
                }
            }

            // fit ridge on Phi -> y
            FittedLinRidge fit = fitCenteredRidge(Phi, y, ridge);

            return (double[][] Ztest) -> {
                int nt = Ztest.length;
                double[][] PhiT = new double[nt][D];
                for (int i = 0; i < nt; i++) {
                    for (int k = 0; k < D; k++) {
                        double dot = 0.0;
                        for (int j = 0; j < p; j++) dot += W[k][j] * ((Ztest[i][j] - mean[j]) / sd[j]);
                        PhiT[i][k] = scale * cos(dot + phase[k]);
                    }
                }
                return fit.predict(PhiT);
            };
        }

        // small helper for ridge with centering that returns a fitted model
        private static final class FittedLinRidge {
            final double[] meanX;
            final double meany;
            final double[] beta;

            FittedLinRidge(double[] meanX, double meany, double[] beta) {
                this.meanX = meanX;
                this.meany = meany;
                this.beta = beta;
            }

            double[] predict(double[][] X) {
                int n = X.length;
                int d = beta.length;
                double[] yhat = new double[n];
                for (int i = 0; i < n; i++) {
                    double s = meany;
                    for (int j = 0; j < d; j++) s += (X[i][j] - meanX[j]) * beta[j];
                    yhat[i] = s;
                }
                return yhat;
            }
        }

        private static FittedLinRidge fitCenteredRidge(double[][] X, double[] y, double ridge) {
            int n = y.length;
            int d = X[0].length;

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
                    for (int k = j; k < d; k++) A[j][k] += xij * Xc[i][k];
                }
            }
            for (int j = 0; j < d; j++) {
                A[j][j] += ridge;
                for (int k = j + 1; k < d; k++) A[k][j] = A[j][k];
            }

            double[] beta = solveSymmetric(A, b);
            return new FittedLinRidge(meanX, meany, beta);
        }
    }
}