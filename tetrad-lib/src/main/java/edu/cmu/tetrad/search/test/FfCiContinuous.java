package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.RawMarginalIndependenceTest;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TMath;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.rng.UniformRandomProvider;
import org.apache.commons.rng.sampling.distribution.NormalizedGaussianSampler;
import org.apache.commons.rng.sampling.distribution.ZigguratSampler;
import org.apache.commons.rng.simple.RandomSource;
import org.ejml.simple.SimpleEVD;
import org.ejml.simple.SimpleMatrix;

import java.util.*;

import static java.lang.Double.NaN;

/**
 * FF-CI (Fast Fourier / Feature-based Conditional Independence Test) for continuous data.
 * <p>
 * This implementation fixes ridge-scaling and covariance inconsistencies so that analytic null approximations (Gamma /
 * HBE / LPB4) are calibrated.
 * <p>
 * External behavior and API are unchanged.
 *
 * @author Joseph Ramsey
 * @see FfCi
 */
public final class FfCiContinuous implements IndependenceTest, RowsSettable, RawMarginalIndependenceTest {

    // --------------------------------------------------------------------
    // Fields
    // --------------------------------------------------------------------

    /**
     * The dataset to be analyzed.
     */
    private final DataSet data;
    /**
     * The variables in the dataset.
     */
    private final List<Node> vars;
    /**
     * Optional but recommended cache for feature matrices.
     */
    private final Map<String, SimpleMatrix> featCache = new HashMap<>();
    /**
     * The bandwidth multiplier.
     */
    private double bandwidthMultiplier = 1.0;
    /**
     * Active rows state.
     */
    private List<Integer> rows = null;
    /**
     * The number of active rows.
     */
    private int n;
    /**
     * The ridge lambda.
     */
    private double lambda = .001;
    /**
     * The approximation method.
     */
    private Approx approx = Approx.GAMMA;
    /**
     * The alpha level.
     */
    private double alpha = 0.05;
    /**
     * The last computed p-value.
     */
    private double lastP = NaN;
    /**
     * The number of permutations.
     */
    private int permutations = 200;
    /**
     * The number of features for X and Y.
     */
    private int numFeatXY = 10;
    /**
     * The number of features for Z.
     */
    private int numFeatZ = 100;
    /**
     * The feature type (RFF or ORF).
     */
    private FeatureType featureType = FeatureType.RFF;
    /**
     * The maximum number of rows to use for bandwidth estimation.
     */
    private int bwMaxRows = 500;
    /**
     * The random seed.
     */
    private long seed = 1729L;
    /**
     * Whether to log verbose output.
     */
    private boolean verbose = false;

    // --------------------------------------------------------------------
    // Constructors
    // --------------------------------------------------------------------

    /**
     * Constructs FF-CI with parameters.
     *
     * @param dataSet the dataset to be analyzed; must not be null
     */
    public FfCiContinuous(DataSet dataSet) {
        DataSet _data = Objects.requireNonNull(dataSet, "data");
//        this.data = DataTransforms.standardizeData(_data);
        this.data = _data;
        this.vars = Collections.unmodifiableList(new ArrayList<>(this.data.getVariables()));
        this.n = getActiveRowCount();
    }

    // --------------------------------------------------------------------
    // Public Methods (IndependenceTest)
    // --------------------------------------------------------------------

    /**
     * z-score columns, ddof=1.
     */
    private static void zscoreInPlace(SimpleMatrix M) {
        int n = M.getNumRows(), d = M.getNumCols();
        if (n < 2 || d == 0) {
            return;
        }

        for (int j = 0; j < d; j++) {
            double sum = 0, sumsq = 0;
            for (int i = 0; i < n; i++) {
                double v = M.get(i, j);
                sum += v;
                sumsq += v * v;
            }
            double mean = sum / n;
            double var = (sumsq - n * mean * mean) / (n - 1);
            double sd = (var > 0) ? TMath.sqrt(var) : 1.0;
            for (int i = 0; i < n; i++) {
                M.set(i, j, (M.get(i, j) - mean) / sd);
            }
        }
    }

    /**
     * z-score raw columns, ddof=1 (for double[][] blocks).
     */
    private static void zscoreInPlace(double[][] M) {
        int n = M.length;
        if (n < 2) {
            return;
        }
        int d = M[0].length;
        if (d == 0) {
            return;
        }

        for (int j = 0; j < d; j++) {
            double sum = 0.0, sumsq = 0.0;
            for (int i = 0; i < n; i++) {
                double v = M[i][j];
                sum += v;
                sumsq += v * v;
            }
            double mean = sum / n;
            double var = (sumsq - n * mean * mean) / (n - 1);
            double sd = (var > 0) ? TMath.sqrt(var) : 1.0;
            for (int i = 0; i < n; i++) {
                M[i][j] = (M[i][j] - mean) / sd;
            }
        }
    }

    /**
     * Center columns in-place (for feature matrices).
     */
    private static void subtractColumnMeansInPlace(SimpleMatrix M) {
        int n = M.getNumRows(), d = M.getNumCols();
        if (n == 0 || d == 0) {
            return;
        }

        for (int j = 0; j < d; j++) {
            double s = 0.0;
            for (int i = 0; i < n; i++) {
                s += M.get(i, j);
            }
            double mean = s / n;
            for (int i = 0; i < n; i++) {
                M.set(i, j, M.get(i, j) - mean);
            }
        }
    }

    /**
     * cov(A,B) = A^T B / (n-1), assumes column-centered.
     */
    private static SimpleMatrix cov(SimpleMatrix A, SimpleMatrix B) {
        int n = A.getNumRows();
        return A.transpose().mult(B).scale(1.0 / (n - 1));
    }

    /**
     * Frobenius norm squared.
     */
    private static double frobeniusNormSquared(SimpleMatrix M) {
        double s = 0.0;
        double[] a = M.getDDRM().data;
        for (double v : a) {
            s += v * v;
        }
        return s;
    }

    /**
     * Ridge residualization on covariance scale:
     * <p>
     * X - Z (Czz + λI)^{-1} Czx
     */
    private static SimpleMatrix ridgeResidual(SimpleMatrix X, SimpleMatrix Z, double lambda) {
        if (Z == null || Z.getNumCols() == 0) {
            return X;
        }

        int n = Z.getNumRows();
        double denom = TMath.max(1.0, n - 1.0);

        SimpleMatrix Czz = Z.transpose().mult(Z).scale(1.0 / denom);
        SimpleMatrix Czx = Z.transpose().mult(X).scale(1.0 / denom);

        SimpleMatrix A = Czz.plus(SimpleMatrix.identity(Czz.getNumRows())
                .scale(TMath.max(1e-18, lambda)));

        SimpleMatrix B = A.solve(Czx);  // (Czz + λI)^{-1} Czx
        return X.minus(Z.mult(B));
    }

    /**
     * Covariance of elementwise residual products.
     */
    private static SimpleMatrix kronResCov(SimpleMatrix RX, SimpleMatrix RY) {
        int n = RX.getNumRows();
        int fx = RX.getNumCols();
        int fy = RY.getNumCols();
        SimpleMatrix Z = new SimpleMatrix(n, fx * fy);

        int idx = 0;
        for (int i = 0; i < fx; i++) {
            for (int j = 0; j < fy; j++) {
                for (int r = 0; r < n; r++) {
                    Z.set(r, idx, RX.get(r, i) * RY.get(r, j));
                }
                idx++;
            }
        }

        return Z.transpose().mult(Z).scale(1.0 / (n - 1));
    }

    // --------------------------------------------------------------------
    // Public Methods (RowsSettable)
    // --------------------------------------------------------------------

    /**
     * Positive eigenvalues only.
     */
    private static double[] positiveEigs(SimpleMatrix Cov) {
        SimpleEVD<SimpleMatrix> evd = Cov.eig();
        List<Double> out = new ArrayList<>();

        for (int i = 0; i < evd.getNumberOfEigenvalues(); i++) {
            double v = evd.getEigenvalue(i).getReal();
            if (v > 1e-12 && Double.isFinite(v)) {
                out.add(v);
            }
        }

        double[] e = new double[out.size()];
        for (int i = 0; i < e.length; i++) {
            e[i] = out.get(i);
        }
        return e;
    }

    /**
     * cov(A, permuted(B)) = A^T * B_perm / (n-1), assumes centered
     */
    private static SimpleMatrix covWithPermutedB(SimpleMatrix A, SimpleMatrix B, int[] perm) {
        int n = A.getNumRows();
        int p = A.getNumCols();
        int q = B.getNumCols();
        SimpleMatrix C = new SimpleMatrix(p, q);

        for (int i = 0; i < n; i++) {
            int bi = perm[i];
            for (int a = 0; a < p; a++) {
                double av = A.get(i, a);
                for (int b = 0; b < q; b++) {
                    C.set(a, b, C.get(a, b) + av * B.get(bi, b));
                }
            }
        }
        return C.scale(1.0 / (n - 1));
    }

    // --------------------------------------------------------------------
    // Other Public Methods
    // --------------------------------------------------------------------

    /**
     * ORF: block-orthogonal rows in blocks of size d.
     */
    private static double[][] sampleOrthogonalW(int mFeatures, int d, double wStd, NormalizedGaussianSampler localGaussian) {
        double[][] W = new double[mFeatures][d];
        if (d <= 0) return W;

        int filled = 0;
        while (filled < mFeatures) {
            int block = TMath.min(d, mFeatures - filled);

            double[][] Q = new double[block][d];
            for (int i = 0; i < block; i++)
                for (int j = 0; j < d; j++)
                    Q[i][j] = localGaussian.sample();

            for (int i = 0; i < block; i++) {
                for (int k = 0; k < i; k++) {
                    double dot = 0.0;
                    for (int j = 0; j < d; j++) dot += Q[i][j] * Q[k][j];
                    for (int j = 0; j < d; j++) Q[i][j] -= dot * Q[k][j];
                }
                double norm2 = 0.0;
                for (int j = 0; j < d; j++) norm2 += Q[i][j] * Q[i][j];
                double norm = TMath.sqrt(TMath.max(1e-18, norm2));
                for (int j = 0; j < d; j++) Q[i][j] /= norm;
            }

            for (int i = 0; i < block; i++) {
                double r = chiRadius(d, localGaussian);
                double s = wStd * r;
                int outRow = filled + i;
                for (int j = 0; j < d; j++) W[outRow][j] = s * Q[i][j];
            }

            filled += block;
        }
        return W;
    }

    private static double chiRadius(int d, NormalizedGaussianSampler localGaussian) {
        double ss = 0.0;
        for (int k = 0; k < d; k++) {
            double g = localGaussian.sample();
            ss += g * g;
        }
        return TMath.sqrt(TMath.max(1e-18, ss));
    }

    /**
     * Median of pairwise squared distances for up to maxRows points. Deterministic subsampling via evenly spaced
     * indices.
     */
    private static double medianDistanceSquaredND(double[][] Z, int maxRows) {
        int n = Z.length;
        int d = (n == 0) ? 0 : Z[0].length;
        if (n < 3 || d == 0) {
            return 1.0;
        }

        int m = TMath.min(n, TMath.max(3, maxRows));

        int[] idx = new int[m];
        if (m == n) {
            for (int i = 0; i < m; i++) {
                idx[i] = i;
            }
        } else {
            for (int i = 0; i < m; i++) {
                idx[i] = (int) TMath.floor((i * (long) (n - 1)) / (double) (m - 1));
            }
        }

        int cnt = m * (m - 1) / 2;
        double[] d2 = new double[cnt];
        int t = 0;

        for (int a = 1; a < m; a++) {
            int i = idx[a];
            for (int b = 0; b < a; b++) {
                int j = idx[b];
                double dist2 = 0.0;
                for (int k = 0; k < d; k++) {
                    double diff = Z[i][k] - Z[j][k];
                    dist2 += diff * diff;
                }
                d2[t++] = dist2;
            }
        }

        Arrays.sort(d2, 0, t);
        int firstPos = 0;
        while (firstPos < t && d2[firstPos] <= 0) {
            firstPos++;
        }
        if (firstPos >= t) {
            return 1.0;
        }
        int mid = firstPos + (t - firstPos) / 2;
        return d2[mid];
    }

    /**
     * Evaluates the independence between two nodes given a set of conditioning nodes.
     * <p>
     * This method tests whether two variables (nodes) are statistically independent,
     * possibly conditional on a set of other variables. It employs Random Fourier Features
     * (RFF) or Orthogonal Random Features (ORF) for kernel approximation. The independence
     * test uses either permutation-based calibration for p-value computation or
     * analytic approximations such as Gamma, Saddlepoint, or Davies-Imhof methods.
     *
     * @param x the first node to be tested; must not be null
     * @param y the second node to be tested; must not be null
     * @param z the set of conditioning nodes; can be null or empty if testing marginal independence
     * @return an {@code IndependenceResult} containing the independence test outcome,
     * including the p-value, whether the null hypothesis of independence is rejected,
     * and the difference between the p-value and the significance level
     * @throws InterruptedException if the current thread is interrupted during computation
     */
    @Override
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z)
            throws InterruptedException {

        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException();
        }

        List<Node> Z = (z == null) ? List.of() : new ArrayList<>(z);

        SimpleMatrix X = rffOrOrfFeaturesFor(List.of(x), numFeatXY, "X");
        SimpleMatrix Y = rffOrOrfFeaturesFor(List.of(y), numFeatXY, "Y");
        SimpleMatrix Zm = Z.isEmpty() ? null : rffOrOrfFeaturesFor(Z, numFeatZ, "Z");

        zscoreInPlace(X);
        zscoreInPlace(Y);
        if (Zm != null) {
            zscoreInPlace(Zm);
        }

        SimpleMatrix RX = ridgeResidual(X, Zm, lambda);
        SimpleMatrix RY = ridgeResidual(Y, Zm, lambda);

        // Ensure centered residuals before covariance/statistic (matches cov() assumption).
        subtractColumnMeansInPlace(RX);
        subtractColumnMeansInPlace(RY);

        SimpleMatrix Cxy = cov(RX, RY);
        double stat = n * frobeniusNormSquared(Cxy);

        double p;

        // --------------------------------------------------------------------
        // Permutation option (reference / diagnostic calibration)
        // --------------------------------------------------------------------
        if (approx == Approx.PERMUTATION && permutations > 0) {
            int greater = 0;

            // Fisher–Yates permutation of row indices.
            int[] perm = new int[n];
            for (int i = 0; i < n; i++) {
                perm[i] = i;
            }

            for (int b = 0; b < permutations; b++) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException();
                }

                // Shuffle perm in-place (uniform random permutation)
                for (int i = n - 1; i > 0; i--) {
                    int j = RandomUtil.getInstance().nextInt(i + 1);
                    int t = perm[i];
                    perm[i] = perm[j];
                    perm[j] = t;
                }

                // General (works even if X/Y later become multi-column):
                SimpleMatrix Cb = covWithPermutedB(RX, RY, perm);
                double statB = n * frobeniusNormSquared(Cb);

                if (statB >= stat) {
                    greater++;
                }
            }

            p = (greater + 1.0) / (permutations + 1.0);

        } else {
            // ----------------------------------------------------------------
            // Analytic approximations (Gamma / HBE / LPB4)
            // ----------------------------------------------------------------
            SimpleMatrix Cov = kronResCov(RX, RY);
            double[] eig = positiveEigs(Cov);

            p = switch (approx) {
                case GAMMA -> QuadraticFormPValues.gammaSatterthwaiteP(stat, eig);
                case SADDLEPOINT -> QuadraticFormPValues.saddlepointLugannaniRiceP(stat, eig);
                case DAVIES_IMHOF -> QuadraticFormPValues.daviesP(stat, eig);
                default -> QuadraticFormPValues.gammaSatterthwaiteP(stat, eig);
            };
        }

        p = TMath.min(1.0, TMath.max(0.0, p));
        lastP = p;

        IndependenceFact fact = new IndependenceFact(x, y, new HashSet<>(Z));

        if (verbose) {
            TetradLogger.getInstance().log(fact + " p=" + p
                    + " stat=" + stat
                    + " approx=" + approx
                    + (approx == Approx.PERMUTATION ? (" perms=" + permutations) : ""));
        }

        return new IndependenceResult(
                fact,
                p > alpha, p, alpha - p
        );
    }

    /**
     * Retrieves the list of variables (nodes) associated with this instance.
     *
     * @return a {@code List} of {@code Node} objects representing the variables
     */
    @Override
    public List<Node> getVariables() {
        return vars;
    }

    /**
     * Retrieves the dataset associated with this instance.
     *
     * @return the {@code DataSet} object representing the data analyzed by this instance
     */
    @Override
    public DataSet getData() {
        return data;
    }

    /**
     * Retrieves the alpha level used for statistical significance testing.
     * The alpha level represents the threshold below which the null hypothesis
     * of independence is rejected.
     *
     * @return the alpha value as a {@code double}, typically in the range [0.0, 1.0].
     */
    @Override
    public double getAlpha() {
        return alpha;
    }

    /**
     * Sets the significance level (alpha) used for statistical testing.
     * The alpha level determines the threshold below which the null hypothesis
     * of independence is rejected. This method updates the alpha field and
     * invalidates any cached features to ensure consistency with the new value.
     *
     * @param alpha the statistical significance level to set, represented as a
     *              double typically in the range [0.0, 1.0]
     */
    @Override
    public void setAlpha(double alpha) {
        this.alpha = alpha;
        invalidateFeatureCache();
    }

    /**
     * Checks whether verbose output is enabled for the current instance.
     * Verbose output typically provides additional diagnostic or detailed information.
     *
     * @return {@code true} if verbose output is enabled, {@code false} otherwise.
     */
    @Override
    public boolean isVerbose() {
        return verbose;
    }

    /**
     * Configures whether verbose output is enabled for this instance.
     * Verbose output typically provides additional diagnostic or detailed information.
     *
     * @param verbose {@code true} to enable verbose output, {@code false} to disable it
     */
    @Override
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Retrieves the list of row indices currently associated with this instance.
     *
     * @return a {@code List} of {@code Integer} objects, representing the row indices
     * used in computations or analyses for this instance.
     */
    @Override
    public List<Integer> getRows() {
        return rows;
    }

    /**
     * Sets the subset of row indices to be used in computations or analyses.
     * The provided list must contain non-null, non-negative integers, each
     * referring to a valid row index in the dataset. If the input is null,
     * the method resets to using all available rows in the dataset.
     * <p>
     * This method validates each row in the input list to ensure:
     * 1. It is not null.
     * 2. It is non-negative.
     * 3. It is less than the total number of rows in the dataset.
     * <p>
     * Any invalid entries will result in a {@code NullPointerException}
     * or {@code IllegalArgumentException} with an appropriate error message.
     *
     * @param rows a list of integers representing the row indices to set;
     *             may be null to reset to all rows.
     * @throws NullPointerException     if any row in the list is null.
     * @throws IllegalArgumentException if any row is negative or out of bounds.
     */
    @Override
    public void setRows(List<Integer> rows) {
        if (rows == null) {
            this.rows = null;
            this.n = data.getNumRows();
            invalidateFeatureCache();
            return;
        }

        for (int i = 0; i < rows.size(); i++) {
            Integer r = rows.get(i);
            if (r == null) {
                throw new NullPointerException("Row " + i + " is null.");
            }
            if (r < 0) {
                throw new IllegalArgumentException("Row " + i + " is negative.");
            }
            if (r >= data.getNumRows()) {
                throw new IllegalArgumentException("Row " + i + " out of bounds: " + r);
            }
        }

        rows.sort(Integer::compareTo);
        this.rows = new ArrayList<>(rows);

        this.n = this.rows.size();
        invalidateFeatureCache();
    }

    // --------------------------------------------------------------------
    // Private Methods
    // --------------------------------------------------------------------

    /**
     * Retrieves the previously calculated p-value.
     * <p>
     * The p-value is a statistical measure that helps determine the significance of a result.
     *
     * @return the last computed p-value as a double
     */
    public double getPValue() {
        return lastP;
    }

    /**
     * Sets the value of the lambda parameter and invalidates the feature cache.
     *
     * @param lambda the new value for the lambda parameter
     */
    public void setLambda(double lambda) {
        this.lambda = lambda;
        invalidateFeatureCache();
    }

    /**
     * Sets the approximation object and ensures it is not null.
     * Invalidates the feature cache after setting the value.
     *
     * @param approx the approximation object to set; must not be null
     */
    public void setApprox(Approx approx) {
        this.approx = Objects.requireNonNull(approx, "approx");
        invalidateFeatureCache();
    }

    /**
     * Sets the number of permutations to be used or processed.
     *
     * @param permutations the number of permutations to set; must be a non-negative integer
     */
    public void setPermutations(int permutations) {
        this.permutations = permutations;
    }

    /**
     * Sets the number of features along the XY axes. The value is constrained to a
     * minimum of 1 to ensure valid input.
     *
     * @param numFeatXY The number of features along the XY axes. If the provided value
     *                  is less than 1, it will be automatically set to 1.
     */
    public void setNumFeatXY(int numFeatXY) {
        this.numFeatXY = TMath.max(1, numFeatXY);
        invalidateFeatureCache();
    }

    /**
     * Sets the value of the numFeatZ property. Ensures the value is at least 1
     * and updates the internal feature cache to reflect the new value.
     *
     * @param numFeatZ the number of features to set; must be a non-negative integer.
     *                 Values less than 1 will be automatically set to 1.
     */
    public void setNumFeatZ(int numFeatZ) {
        this.numFeatZ = TMath.max(1, numFeatZ);
        invalidateFeatureCache();
    }

    /**
     * Sets the feature type for the current instance and invalidates the feature cache.
     *
     * @param featureType the feature type to be assigned; must not be null
     */
    public void setFeatureType(FeatureType featureType) {
        this.featureType = Objects.requireNonNull(featureType, "featureType");
        invalidateFeatureCache();
    }

    /**
     * Sets the maximum number of rows for the bandwidth resource.
     * This value defines the upper limit for rows that can be handled.
     * Invalidates the feature cache to ensure updated configurations are applied.
     *
     * @param bwMaxRows the maximum number of rows to set
     */
    public void setBwMaxRows(int bwMaxRows) {
        this.bwMaxRows = bwMaxRows;
        invalidateFeatureCache();
    }

    // --------------------------------------------------------------------
    // Static Core Math Utilities
    // --------------------------------------------------------------------

    /**
     * Sets the bandwidth multiplier that is used to adjust the bandwidth during calculations.
     * The value must be greater than 0 and finite.
     *
     * @param bandwidthMultiplier the new bandwidth multiplier value
     * @throws IllegalArgumentException if the provided bandwidthMultiplier is not greater than 0 or is not finite
     */
    public void setBandwidthMultiplier(double bandwidthMultiplier) {
        if (!(bandwidthMultiplier > 0) || !Double.isFinite(bandwidthMultiplier)) {
            throw new IllegalArgumentException("bandwidthMultiplier must be > 0 and finite");
        }
        this.bandwidthMultiplier = bandwidthMultiplier;
        invalidateFeatureCache();
    }

    /**
     * Sets the number of features along the XY direction, ensuring it is at least 1.
     * This method will update the internal configuration and invalidate the feature cache
     * to reflect the change.
     *
     * @param d the desired number of features along the XY direction. If the value is
     *          less than 1, it will default to 1.
     */
    public void setNumFeaturesXY(int d) {
        this.numFeatXY = TMath.max(1, d);
        invalidateFeatureCache();
    }

    /**
     * Sets the number of features in the Z dimension.
     * Ensures the value is at least 1 to avoid invalid configurations
     * and updates any dependent feature caches.
     *
     * @param d the desired number of features in the Z dimension
     */
    public void setNumFeaturesZ(int d) {
        this.numFeatZ = TMath.max(1, d);
        invalidateFeatureCache();
    }

    /**
     * Sets the seed value to be used, updating the internal state and invalidating the feature cache.
     *
     * @param seed the seed value used to initialize or modify the current state
     */
    public void setSeed(long seed) {
        this.seed = seed;
        invalidateFeatureCache();
    }

    /**
     * Clears the feature cache by removing all stored entries.
     * This method is typically used to reset or invalidate
     * cached feature data to ensure it is refreshed or recalculated
     * when accessed next.
     */
    public void invalidateFeatureCache() {
        featCache.clear();
    }

    /**
     * Builds RFF/ORF features for the block of variables 'vs'.
     */
    private SimpleMatrix rffOrOrfFeaturesFor(List<Node> vs, int mFeatures, String tag) {
        Objects.requireNonNull(vs, "vs");
        if (mFeatures <= 0) {
            return new SimpleMatrix(getActiveRowCount(), 0);
        }

        // Cache key: depends on vars, active rows, m, bw knobs, feature type, seed, tag
        final String key = featKey(tag, vs, mFeatures);

        SimpleMatrix cached = featCache.get(key);
        if (cached != null) {
            return cached;
        }

        final int nRows = getActiveRowCount();
        final double[][] Zraw = extractRawBlock(vs);   // nRows x d
        zscoreInPlace(Zraw);                           // standardize raw columns first

        double bw2 = 1.0;
        if (Zraw.length > 0 && Zraw[0].length > 0) {
            bw2 = medianDistanceSquaredND(Zraw, TMath.min(nRows, bwMaxRows));
            if (!(bw2 > 0) || !Double.isFinite(bw2)) {
                bw2 = 1.0;
            }
            bw2 *= (bandwidthMultiplier * bandwidthMultiplier);
            if (bw2 < 1e-12) {
                bw2 = 1e-12;
            }
        }

        long localSeed = seedForBlock(tag, vs) ^ seed;
        double[][] Phi = rffFeatures(Zraw, mFeatures, bw2, localSeed);

        SimpleMatrix M = new SimpleMatrix(Phi);

        // Center features (your cov() assumes centered).
        subtractColumnMeansInPlace(M);

        featCache.put(key, M);
        return M;
    }

    /**
     * Build cache key for a feature block.
     */
    private String featKey(String tag, List<Node> vs, int mFeatures) {
        ArrayList<String> names = new ArrayList<>(vs.size());
        for (Node v : vs) {
            names.add(v.getName());
        }
        names.sort(String::compareTo);

        StringBuilder sb = new StringBuilder(160);
        sb.append("RFFORF|tag=").append(tag)
                .append("|n=").append(getActiveRowCount())
                .append("|rowsHash=").append(activeRowsHash())
                .append("|m=").append(mFeatures)
                .append("|ft=").append(featureType.name())
                .append("|bwMult=").append(Double.doubleToLongBits(bandwidthMultiplier))
                .append("|bwMax=").append(bwMaxRows)
                .append("|seed=").append(seed)
                .append("|vars=");
        for (String s : names) {
            sb.append(s).append(",");
        }
        return sb.toString();
    }

    /**
     * Deterministic seed for a block; makes results stable across runs.
     */
    private long seedForBlock(String tag, List<Node> block) {
        long h = 1469598103934665603L; // FNV-ish

        h = 1099511628211L * (h ^ tag.hashCode());

        ArrayList<String> names = new ArrayList<>(block.size());
        for (Node v : block) {
            names.add(v.getName());
        }
//        names.sort(String::compareTo);
        for (String s : names) {
            h = 1099511628211L * (h ^ s.hashCode());
        }

        h = 1099511628211L * (h ^ block.hashCode());

        h = 1099511628211L * (h ^ getActiveRowCount());
        h = 1099511628211L * (h ^ activeRowsHash());
        return h;
    }

//    private long seedForBlock(String tag, List<Node> block) {
//        long h = 1469598103934665603L;
//        h = 1099511628211L * (h ^ tag.hashCode());
//
//        ArrayList<String> names = new ArrayList<>(block.size());
//        for (Node v : block) names.add(v.getName());
//        names.sort(String::compareTo);
//        for (String s : names) h = 1099511628211L * (h ^ s.hashCode());
//
//        return h;
//    }

    /**
     * Extract raw data for vars in vs over the active rows.
     */
    private double[][] extractRawBlock(List<Node> vs) {
        final int nRows = getActiveRowCount();
        final int d = vs.size();
        double[][] Z = new double[nRows][d];

        for (int j = 0; j < d; j++) {
            Node v = vs.get(j);
            int col = data.getColumnIndex(v);
            if (col < 0) {
                throw new IllegalArgumentException("Variable not found: " + v.getName());
            }

            for (int i = 0; i < nRows; i++) {
                int row = activeRowIndex(i);
                Z[i][j] = data.getDouble(row, col);
            }
        }
        return Z;
    }

    /**
     * RFF/ORF features for RBF kernel k(x,x') = exp(-||x-x'||^2 / bw2).
     */
    private double[][] rffFeatures(double[][] Z, int mFeatures, double bw2, long seed) {
        final int nRows = Z.length;
        final int d = (nRows == 0) ? 0 : Z[0].length;
        double[][] Phi = new double[nRows][mFeatures];
        if (nRows == 0) return Phi;

        // Use a local RNG seeded deterministically — don't touch the global one
        UniformRandomProvider localRng = RandomSource.XO_RO_SHI_RO_128_PP.create(seed);
        NormalizedGaussianSampler localGaussian = ZigguratSampler.NormalizedGaussian.of(localRng);

        if (d == 0) {
            double scale0 = TMath.sqrt(2.0 / mFeatures);
            double[] b0 = new double[mFeatures];
            for (int j = 0; j < mFeatures; j++) b0[j] = 2.0 * TMath.PI * localRng.nextDouble();
            for (int i = 0; i < nRows; i++)
                for (int j = 0; j < mFeatures; j++)
                    Phi[i][j] = scale0 * TMath.cos(b0[j]);
            return Phi;
        }

        if (!(bw2 > 0) || !Double.isFinite(bw2)) bw2 = 1.0;

        final double wStd = TMath.sqrt(2.0 / bw2);
        final double scale = TMath.sqrt(2.0 / mFeatures);

        double[][] W;
        double[] b = new double[mFeatures];

        if (featureType == FeatureType.RFF) {
            W = new double[mFeatures][d];
            for (int j = 0; j < mFeatures; j++) {
                for (int k = 0; k < d; k++) W[j][k] = wStd * localGaussian.sample();
                b[j] = 2.0 * TMath.PI * localRng.nextDouble();
            }
        } else if (featureType == FeatureType.ORF) {
            W = sampleOrthogonalW(mFeatures, d, wStd, localGaussian);
            for (int j = 0; j < mFeatures; j++) b[j] = 2.0 * TMath.PI * localRng.nextDouble();
        } else {
            throw new IllegalArgumentException("featureType must be RFF or ORF");
        }

        for (int i = 0; i < nRows; i++) {
            double[] Zi = Z[i];
            for (int j = 0; j < mFeatures; j++) {
                double dot = 0.0;
                double[] wj = W[j];
                for (int k = 0; k < d; k++) dot += wj[k] * Zi[k];
                Phi[i][j] = scale * TMath.cos(dot + b[j]);
            }
        }
        return Phi;
    }

    private int getActiveRowCount() {
        return (rows == null) ? data.getNumRows() : rows.size();
    }

    private int activeRowIndex(int i) {
        return (rows == null) ? i : rows.get(i);
    }

    private int activeRowsHash() {
        if (rows == null) {
            return 0;
        }
        int h = 1;
        for (int r : rows) {
            h = 31 * h + r;
        }
        return h;
    }

    /**
     * Computes the p-value for the statistical test of independence between two variables.
     *
     * @param x the array of values representing the first variable
     * @param y the array of values representing the second variable
     * @return the computed p-value indicating the strength of independence between the two variables
     */
    @Override
    public double computePValue(double[] x, double[] y) throws InterruptedException {
        double[][] combined = new double[x.length][2];
        for (int i = 0; i < x.length; i++) {
            combined[i][0] = x[i];
            combined[i][1] = y[i];
        }
        Node _x = new ContinuousVariable("X_computePValue");
        Node _y = new ContinuousVariable("Y_computePValue");
        List<Node> nodes = new ArrayList<>();
        nodes.add(_x);
        nodes.add(_y);
        DataSet dataSet = new BoxDataSet(new DoubleDataBox(combined), nodes);

        edu.cmu.tetrad.search.test.FfCi test = new edu.cmu.tetrad.search.test.FfCi(dataSet, new Parameters());
        test.setAlpha(this.alpha);
        test.setLambda(this.lambda);
        test.setNumFeaturesXY(this.numFeatXY);
        test.setNumFeaturesZ(this.numFeatZ);
        test.setPermutations(this.permutations);

        return test.checkIndependence(_x, _y).getPValue();
    }

    // --------------------------------------------------------------------
    // Inner Classes / Enums
    // --------------------------------------------------------------------

    /**
     * Enum representing the feature generation methods for random Fourier features (RFF) and orthogonal random features
     * (ORF).
     * <p>
     * The feature type determines how random projections are designed for approximating the RBF kernel.
     */
    public enum FeatureType {

        /**
         * Represents the Random Fourier Features (RFF) feature generation method.
         * <p>
         * RFF is a technique used to approximate the Radial Basis Function (RBF) kernel by applying random projections.
         * This method provides an efficient way to compute kernel features for large-scale machine learning tasks.
         */
        RFF,

        /**
         * Represents the Orthogonal Random Features (ORF) feature generation method.
         * <p>
         * ORF is a technique used to approximate the Radial Basis Function (RBF) kernel by applying random projections
         * with orthogonality constraints. This method ensures more structured and efficient projections, improving the
         * quality of the kernel approximation while maintaining computational efficiency.
         */
        ORF
    }

    /**
     * Represents different approximation methods that can be used for statistical or mathematical computations.
     *
     * @see QuadraticFormPValues
     */
    public enum Approx {

        /**
         * Represents the gamma approximation method.
         * <p>
         * This method is used in statistical or mathematical computations where an approximation based on the gamma
         * distribution is appropriate.
         */
        GAMMA,

        /**
         * Represents the saddlepoint approximation method.
         * <p>
         * This method is used in statistical or mathematical computations to provide an accurate approximation of
         * probability distributions, particularly for small sample sizes or in scenarios where traditional methods may
         * lack precision.
         */
        SADDLEPOINT,

        /**
         * Represents the Davies-Imhof approximation method.
         * <p>
         * This method is used in statistical or mathematical computations to approximate the distribution of quadratic
         * forms in normal variables. It is particularly useful in scenarios requiring precise evaluation of tail
         * probabilities in statistical tests or other related calculations.
         */
        DAVIES_IMHOF,

        /**
         * Represents the permutation-based approximation method.
         * <p>
         * This method is commonly used in statistical and mathematical computations that involve resampling techniques.
         * It relies on generating all possible rearrangements (or permutations) of a dataset to assess the statistical
         * significance or to estimate a probability distribution. Permutation methods are often employed in
         * non-parametric tests and other scenarios where traditional parametric approaches may not be suitable.
         */
        PERMUTATION
    }
}
