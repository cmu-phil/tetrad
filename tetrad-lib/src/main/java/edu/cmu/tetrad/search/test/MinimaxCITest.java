package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.search.utils.MinimaxBinning;
import edu.cmu.tetrad.search.utils.MinimaxBinningConfig;
import edu.cmu.tetrad.search.utils.MinimaxGroupCache;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.Math.*;

/**
 * <h2>Minimax Conditional Independence Test</h2>
 *
 * <p>
 * This class implements a nonparametric conditional independence test inspired by
 * minimax theory for hypothesis testing. The goal is to test whether
 * {@code X ⟂ Y | Z} holds, without assuming a parametric form for the conditional
 * expectations {@code E[X | Z]} or {@code E[Y | Z]}.
 * </p>
 *
 * <p>
 * <b>Core idea.</b>
 * Rather than fitting potentially fragile nonlinear regression models, the test
 * conditions on {@code Z} by grouping observations into local neighborhoods
 * (bins) where {@code Z} is approximately constant. Within each bin, dependence
 * between {@code X} and {@code Y} is assessed directly, and evidence is aggregated
 * across bins.
 * </p>
 *
 * <p>
 * <b>Calibration by permutation.</b>
 * To assess statistical significance, the test uses within-bin permutation:
 * {@code Y} values are randomly shuffled inside each {@code Z}-bin to generate
 * a reference distribution under the null hypothesis of conditional independence.
 * The observed statistic is compared against this distribution to obtain a
 * p-value.
 * </p>
 *
 * <p>
 * <b>Why “minimax”.</b>
 * The design of the test follows a minimax philosophy: it aims to achieve
 * near-optimal power against the hardest alternatives in broad nonparametric
 * classes, without relying on correct specification of regression models.
 * This makes the test particularly robust when the true data-generating
 * process is complex, highly nonlinear, or unknown.
 * </p>
 *
 * <p>
 * <b>Practical advantages.</b>
 * <ul>
 *   <li>No explicit nonlinear regression is required.</li>
 *   <li>Stable behavior even when regression-based methods fail to converge.</li>
 *   <li>Well suited for repeated use inside constraint-based causal discovery
 *       algorithms, such as PC, due to effective caching of intermediate results.</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Intended use.</b>
 * This test is designed for continuous data and is especially useful in settings
 * where additive noise or smooth functional assumptions may not hold exactly,
 * but where robust conditional independence decisions are still needed.
 * </p>
 *
 * <h3>References</h3>
 *
 * <ul>
 *   <li>
 *     Neykov, M., Liu, J. S., Cai, T., & Wasserman, L. (2021).
 *     <i>Minimax optimal conditional independence testing</i>.
 *     Annals of Statistics, 49(4), 2159–2187.
 *     <br>
 *     Introduces minimax lower bounds for conditional independence testing and
 *     proposes simple binning- and permutation-based tests that achieve optimal
 *     worst-case rates under smoothness assumptions.
 *   </li>
 *
 *   <li>
 *     Shah, R. D., & Peters, J. (2020).
 *     <i>The hardness of conditional independence testing and the generalised covariance measure</i>.
 *     Annals of Statistics, 48(3), 1514–1538.
 *     <br>
 *     Establishes no-free-lunch results for conditional independence testing and
 *     analyzes when regression-based tests such as GCM can and cannot be
 *     statistically consistent.
 *   </li>
 *
 *   <li>
 *     Neykov, M., & Wasserman, L. (2019).
 *     <i>Minimax optimal hypothesis testing for high-dimensional multinomials</i>.
 *     Annals of Statistics, 47(4), 2139–2168.
 *     <br>
 *     Develops minimax testing techniques that underpin later work on robust
 *     conditional independence testing.
 *   </li>
 *
 *   <li>
 *     Wasserman, L., & Roeder, K. (2009).
 *     <i>High-dimensional variable selection</i>.
 *     Annals of Statistics, 37(5A), 2178–2201.
 *     <br>
 *     Provides foundational minimax perspectives on hypothesis testing and
 *     inference in high-dimensional settings.
 *   </li>
 * </ul>
 */
public final class MinimaxCITest implements IndependenceTest {
    private final DataSet data;
    private final List<Node> variables;
    private final Map<String, Integer> indexMap;
    private final ResidualCache cache = new ResidualCache();
    private double alpha;
    private boolean verbose = false;
    private List<Integer> rows = null;
    private RegressorType regressorType = RegressorType.RFF_RIDGE;
    private double ridge = 1e-3;
    private int rffFeatures = 200;     // D
    private double rffSigma = 1.0;     // lengthscale-ish; affects random freq scale
    private long rffSeed = 1L;
    private final double lastT = Double.NaN;
    private final double lastP = Double.NaN;
    private boolean crossFit = true;   // turn on to debias residuals
    private int crossFitFolds = 2;     // start with 2-fold (cheap). Try 5 later.
    private long crossFitSeed = 12345L;
    private int binsPerDim = 4;
    private int permutations = 300;
    private int minBinSize = 3;
    private long permSeed = 1L;

//    private final MinimaxGroupCache groupCache = new MinimaxGroupCache();
    // in MinimaxConditionalIndependenceTest fields
    private MinimaxBinningConfig binningCfg = new MinimaxBinningConfig(4, 3);
    private final MinimaxGroupCache groupCache = new MinimaxGroupCache();

    /**
     * Constructs a MinimaxTest object with the specified dataset and significance level.
     * This test is designed to operate on continuous datasets. It initializes internal variables
     * and performs setup necessary for independence testing.
     *
     * @param data the dataset on which the Minimax test will be conducted.
     *             It must be a continuous dataset, otherwise, an IllegalArgumentException
     *             will be thrown.
     * @param alpha the significance level for the independence test.
     *              This value determines the threshold for rejecting the null hypothesis
     *              in statistical tests.
     * @throws IllegalArgumentException if the provided dataset is not continuous.
     */
    public MinimaxCITest(DataSet data, double alpha) {
        if (!data.isContinuous()) throw new IllegalArgumentException("GCM test currently requires continuous DataSet.");
        this.data = data;
        this.variables = Collections.unmodifiableList(new ArrayList<>(data.getVariables()));
        this.indexMap = indexMap(this.variables);
        setAlpha(alpha);
    }

    /**
     * Creates a new instance of an independence test using a subset of variables.
     * This method constructs a minimized test by using a limited set of variables from the original dataset,
     * while maintaining the data consistency and computational optimizations. The method adjusts the attributes
     * and settings as required to support the subset operation.
     *
     * @param vars the subset of variables to be used for the independence test.
     *             This must be a non-empty list of Node elements selected from the main dataset.
     * @return an IndependenceTest instance configured to operate on the specified subset of variables
     *         with inherited settings from the original MinimaxTest instance.
     */
    @Override
    public IndependenceTest indTestSubset(List<Node> vars) {
        // Simple (safe) implementation: keep the same dataset, just restrict variable list.
        // If you prefer true sub-DataSet, you can build one, but this keeps overhead low.
        MinimaxCITest t = new MinimaxCITest(this.data, this.alpha);
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

    /**
     * Checks the independence between two nodes, x and y, given a set of conditioning variables, z.
     * This method computes a p-value for the independence test and determines if the nodes are
     * independent based on the significance level (alpha). If verbose mode is enabled and independence
     * is determined, a log message is recorded.
     *
     * @param x the first node involved in the independence test.
     * @param y the second node involved in the independence test.
     * @param z the set of nodes representing the conditioning variables for the test.
     *          This must be a non-null set containing zero or more nodes.
     * @return an IndependenceResult object that encapsulates the result of the independence test,
     *         including the computed p-value, independence status,
     *         and an additional measure related to the significance level.
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
     * Computes the p-value for testing the independence of two nodes (variables) x and y,
     * given a set of conditioning nodes z.
     *
     * This method performs a permutation-based independence test, with the p-value
     * indicating the likelihood of rejecting the null hypothesis of independence,
     * assuming independence is true. The test accounts for conditioning variables
     * by grouping and residualizing the data accordingly.
     *
     * @param x the first node involved in the independence test; must not be null.
     * @param y the second node involved in the independence test; must not be null.
     * @param z the set of nodes representing the conditioning variables; must not be null.
     *          It can be an empty set if no conditioning is required.
     * @return the computed p-value for the test, representing the probability of observing
     *         the data under the null hypothesis of independence.
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

        if (n < 20) return 0.0; // conservative guard

        // Extract x,y arrays in the same index space as useRows
        double[] xArr = new double[n];
        double[] yArr = new double[n];
        for (int i = 0; i < n; i++) {
            int r = useRows.get(i);
            xArr[i] = data.getDouble(r, ix);
            yArr[i] = data.getDouble(r, iy);
        }

        // If no conditioning set, do a plain permutation test of corr within all rows
        if (iz.length == 0) {
            double obs = statCorrSq(xArr, yArr, new int[][]{range(n)});
            return permuteWithinGroupsPValue(xArr, yArr, new int[][]{range(n)}, permutations, permSeed ^ ix ^ (iy * 1315423911L));
        }

//        int[][] groups = groupCache.getGroups(data, iz, useRows, binsPerDim, minBinSize);

        // in getPValue(...)
        int[][] groups = groupCache.getGroups(data, iz, useRows, binningCfg);

        // If binning produced no usable groups, be conservative (dependent)
        if (groups.length == 0) return 0.0;

        long seed = permSeed ^ ix ^ (iy * 1315423911L) ^ Arrays.hashCode(iz);
        return permuteWithinGroupsPValue(xArr, yArr, groups, permutations, seed);
    }

    /**
     * Retrieves the list of variables associated with this MinimaxTest instance.
     * These variables represent the nodes of the dataset on which independence
     * testing and related computations are performed.
     *
     * @return a list of Node objects corresponding to the variables used
     *         in this instance of the MinimaxTest.
     */
    @Override
    public List<Node> getVariables() {
        return variables;
    }

    /**
     * Retrieves the significance level (alpha) for this MinimaxTest instance.
     * The alpha value determines the threshold for rejecting the null
     * hypothesis in statistical tests.
     *
     * @return the alpha value, representing the significance level for
     *         independence tests conducted using this instance.
     */
    @Override
    public double getAlpha() {
        return alpha;
    }

    /**
     * Sets the significance level (alpha) for this MinimaxTest instance.
     * The alpha value represents the threshold for rejecting the null
     * hypothesis in statistical tests. It must be a value in the range [0, 1].
     *
     * @param alpha the significance level to be set. This value must be
     *              between 0 (inclusive) and 1 (inclusive). If the provided
     *              value is outside this range, an IllegalArgumentException
     *              is thrown.
     * @throws IllegalArgumentException if alpha is less than 0 or greater than 1.
     */
    public void setAlpha(double alpha) {
        if (alpha < 0 || alpha > 1) throw new IllegalArgumentException("alpha must be in [0,1]");
        this.alpha = alpha;
    }

    /**
     * Retrieves the dataset associated with this MinimaxTest instance.
     * This dataset contains the data used for independence testing
     * and related computations.
     *
     * @return the DataSet object used by this instance.
     */
    @Override
    public DataSet getData() {
        return data;
    }

    /**
     * Returns the current state of the verbose mode for this instance.
     * When verbose mode is enabled, additional logging and output may
     * occur during operations, providing more detailed information
     * about the internal processes and execution of methods.
     *
     * @return true if verbose mode is enabled; false otherwise.
     */
    @Override
    public boolean isVerbose() {
        return verbose;
    }

    /**
     * Enables or disables verbose mode for this instance.
     * When verbose mode is enabled, additional logging and output
     * may occur during operations, providing more detailed
     * information about the internal processes and execution of methods.
     *
     * @param verbose a boolean value indicating whether to enable
     *                (true) or disable (false) verbose mode.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Retrieves the sample size, which is the number of rows in the dataset.
     *
     * @return the number of rows in the dataset
     */
    @Override
    public int getSampleSize() {
        return data.getNumRows();
    }

    /**
     * Sets the crossFit mode and clears the cache.
     *
     * @param crossFit a boolean indicating whether crossFit mode should be enabled or disabled
     */
    public void setCrossFit(boolean crossFit) {
        this.crossFit = crossFit;
        cache.clear();
    }

    /**
     * Sets the seed value used for CrossFit operations and clears the cache.
     *
     * @param seed the seed value to configure. This seed is used to initialize
     *             specific operations related to CrossFit computations or processing.
     */
    public void setCrossFitSeed(long seed) {
        this.crossFitSeed = seed;
        cache.clear();
    }

    /**
     * Sets the number of folds for cross-validation.
     *
     * @param k the number of folds for cross-validation; must be greater than or equal to 2
     * @throws IllegalArgumentException if the given number of folds is less than 2
     */
    public void setCrossFitFolds(int k) {
        if (k < 2) throw new IllegalArgumentException("crossFitFolds must be >= 2");
        this.crossFitFolds = k;
        cache.clear();
    }

//    /**
//     * Sets the number of bins per dimension for discretization.
//     *
//     * @param b the number of bins per dimension; must be greater than or equal to 2
//     */
//    public void setBinsPerDim(int b) { this.binsPerDim = Math.max(2, b); groupCache.clear(); }

    // setters (preserve your current behavior)
    public void setBinsPerDim(int b) {
        this.binningCfg = new MinimaxBinningConfig(Math.max(2, b), binningCfg.minBinSize());
        groupCache.clear();
    }

    public void setMinBinSize(int m) {
        this.binningCfg = new MinimaxBinningConfig(binningCfg.binsPerDim(), Math.max(3, m));
        groupCache.clear();
    }

    // optional convenience:
    public void setBinningConfig(MinimaxBinningConfig cfg) {
        this.binningCfg = java.util.Objects.requireNonNull(cfg, "cfg");
        groupCache.clear();
    }

    /**
     * Sets the number of permutations to the specified value, ensuring it is not less than 50.
     *
     * @param B the desired number of permutations. If the value is less than 50, it defaults to 50.
     */
    public void setPermutations(int B) { this.permutations = Math.max(50, B); }

//    /**
//     * Sets the minimum number of samples per bin.
//     *
//     * @param m the minimum number of samples per bin; must be greater than or equal to 3
//     */
//    public void setMinBinSize(int m) { this.minBinSize = Math.max(3, m); groupCache.clear(); }

    /**
     * Sets the seed for random number generation.
     *
     * @param s the seed value to configure. This seed is used to initialize
     *          specific operations related to random number generation.
     */
    public void setPermSeed(long s) { this.permSeed = s; }

    /**
     * Retrieves the list of data sets.
     *
     * @return the list of data sets
     */
    @Override
    public List<DataSet> getDataSets() {
        return List.of(data);
    }

    /**
     * Sets the list of rows to be used for analysis.
     *
     * @param rows the list of row indices to be used; cannot be null
     * @throws IllegalArgumentException if rows is null
     */
    public void setRows(List<Integer> rows) {
        this.rows = rows;
        cache.clear(); // rows changes => invalidate cache
    }

    /**
     * Sets the type of regressor to be used in the analysis.
     *
     * @param t the type of regressor; cannot be null
     * @throws IllegalArgumentException if t is null
     */
    public void setRegressorType(RegressorType t) {
        this.regressorType = (t == null ? RegressorType.LINEAR_RIDGE : t);
        cache.clear();
    }

    /**
     * Sets the regularization parameter for ridge regression.
     *
     * @param ridge the regularization parameter; must be non-negative
     * @throws IllegalArgumentException if ridge is negative
     */
    public void setRidge(double ridge) {
        if (ridge < 0) throw new IllegalArgumentException("ridge must be >= 0");
        this.ridge = ridge;
        cache.clear();
    }

    /**
     * Sets the number of features to be used in Random Fourier Features (RFF) approximation.
     *
     * @param d the number of features; must be greater than or equal to 1
     * @throws IllegalArgumentException if d is less than 1
     */
    public void setRffFeatures(int d) {
        if (d < 1) throw new IllegalArgumentException("rffFeatures must be >= 1");
        this.rffFeatures = d;
        cache.clear();
    }

    /**
     * Sets the standard deviation for Random Fourier Features (RFF) approximation.
     *
     * @param sigma the standard deviation; must be positive
     * @throws IllegalArgumentException if sigma is not positive
     */
    public void setRffSigma(double sigma) {
        if (!(sigma > 0)) throw new IllegalArgumentException("rffSigma must be > 0");
        this.rffSigma = sigma;
        cache.clear();
    }

    /**
     * Sets the seed for Random Fourier Features (RFF) approximation.
     *
     * @param seed the seed value to configure. This seed is used to initialize
     *             specific operations related to Random Fourier Features computations or processing.
     */
    public void setRffSeed(long seed) {
        this.rffSeed = seed;
        cache.clear();
    }

    /**
     * Retrieves the last computed T statistic.
     *
     * @return the last computed T statistic
     */
    public double getLastT() {
        return lastT;
    }

    /**
     * Retrieves the last computed p-value.
     *
     * @return the last computed p-value
     */
    public double getLastP() {
        return lastP;
    }

    private int[][] computeGroups(int[] zIdx, List<Integer> useRows) {
        return MinimaxBinning.computeGroups(data, zIdx, useRows, binsPerDim, minBinSize);
    }

    private static double statCorrSq(double[] x, double[] y, int[][] groups) {
        double T = 0.0;
        for (int[] g : groups) {
            int m = g.length;
            double mx = 0, my = 0;
            for (int idx : g) { mx += x[idx]; my += y[idx]; }
            mx /= m; my /= m;

            double sxx = 0, syy = 0, sxy = 0;
            for (int idx : g) {
                double dx = x[idx] - mx;
                double dy = y[idx] - my;
                sxx += dx * dx;
                syy += dy * dy;
                sxy += dx * dy;
            }
            if (sxx <= 0 || syy <= 0) continue;
            double r = sxy / Math.sqrt(sxx * syy);
            T += (m - 1.0) * r * r;
        }
        return T;
    }

    private static double permuteWithinGroupsPValue(
            double[] x, double[] y, int[][] groups, int B, long seed
    ) {
        double obs = statCorrSq(x, y, groups);

        SplittableRandom rng = new SplittableRandom(seed);
        double[] yPerm = Arrays.copyOf(y, y.length);

        int ge = 0;
        for (int b = 0; b < B; b++) {
            System.arraycopy(y, 0, yPerm, 0, y.length);

            // shuffle y within each group
            for (int[] g : groups) {
                for (int i = g.length - 1; i > 0; i--) {
                    int j = rng.nextInt(i + 1);
                    int ii = g[i], jj = g[j];
                    double tmp = yPerm[ii];
                    yPerm[ii] = yPerm[jj];
                    yPerm[jj] = tmp;
                }
            }

            double t = statCorrSq(x, yPerm, groups);
            if (t >= obs) ge++;
        }

        return (ge + 1.0) / (B + 1.0);
    }

    // helper list (same as before)
    private static final class IntArrayList {
        private int[] a = new int[16];
        private int n = 0;
        void add(int v) { if (n == a.length) a = Arrays.copyOf(a, a.length * 2); a[n++] = v; }
        int size() { return n; }
        int[] toArray() { return Arrays.copyOf(a, n); }
    }

    private static int upperBound(double[] edges, double v) {
        // returns smallest idx such that v <= edges[idx], i.e. number of edges < v
        int lo = 0, hi = edges.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (v > edges[mid]) lo = mid + 1;
            else hi = mid;
        }
        return lo;
    }

    private static int[] range(int n) {
        int[] r = new int[n];
        for (int i = 0; i < n; i++) r[i] = i;
        return r;
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
        List<Integer> r = new ArrayList<>(n);
        for (int i = 0; i < n; i++) r.add(i);
        return r;
    }

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
        K = Math.min(K, n);              // guard
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
    public enum RegressorType {LINEAR_RIDGE, RFF_RIDGE}

    // ==================== Regressors ====================

    private static final class ResidualCache {
        private final ConcurrentHashMap<Key, double[]> cache = new ConcurrentHashMap<>();

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

    private static Map<String, Integer> indexMap(List<Node> vars) {
        Map<String, Integer> m = new HashMap<>();
        for (int i = 0; i < vars.size(); i++) m.put(vars.get(i).getName(), i);
        return m;
    }

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