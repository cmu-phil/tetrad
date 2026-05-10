package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataTransforms;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.math3.distribution.GammaDistribution;
import edu.cmu.tetrad.util.TMath;
import org.ejml.simple.SimpleEVD;
import org.ejml.simple.SimpleMatrix;

import java.util.*;

import static java.lang.Double.NaN;

/**
 * A Java implementation of the RCIT (Randomized Conditional Independence Test) and its
 * variant RCoT (Randomized Conditional correlation Test), following the architecture of
 * {@link FfCiContinuous} but with the feature-block construction described in:
 *
 * <blockquote>
 * Strobl, Zhang, and Visweswaran (2019). "Approximate kernel-based conditional
 * independence tests for fast non-parametric causal discovery."
 * <em>Journal of Causal Inference</em> 7(1).
 * </blockquote>
 *
 * <h2>Hypotheses</h2>
 * Tests X ⊥ Y | Z nonparametrically by mapping variables into finite-dimensional
 * feature spaces and measuring cross-covariance between the feature representations
 * of X and Y after regressing out the influence of Z.
 *
 * <h2>Feature construction</h2>
 * All variables are assumed to be continuous. Each variable or block of variables
 * is mapped to a feature matrix using either:
 * <ul>
 *   <li><b>RFF</b> (Random Fourier Features) — i.i.d. Gaussian frequency vectors,
 *       approximating an RBF kernel via {@code sqrt(2/m) cos(Wx + b)}.</li>
 *   <li><b>ORF</b> (Orthogonal Random Features) — block-orthogonalized frequency
 *       vectors with chi-distributed norms, typically giving a better kernel
 *       approximation for the same feature budget.</li>
 * </ul>
 * The RBF bandwidth is estimated from the data using the median pairwise squared
 * distance heuristic, optionally scaled by {@code bandwidthMultiplier}.
 *
 * <h2>RCIT vs. RCoT</h2>
 * The two modes differ only in how the Y feature block is constructed when Z is
 * non-empty:
 * <ul>
 *   <li><b>RCIT</b> ({@code doRcit=true}, default) — the Y feature block is built
 *       from the joint variable set [Y, Z], using {@code numFeatYAug} features.
 *       This augmentation allows the test to be more sensitive to conditional
 *       dependence mediated through Z.</li>
 *   <li><b>RCoT</b> ({@code doRcit=false}) — the Y feature block is built from Y
 *       alone, using {@code numFeatXY} features. This is the simpler variant and
 *       matches unconditional RIT when Z is empty.</li>
 * </ul>
 *
 * <h2>Test statistic</h2>
 * Given feature matrices FX, FY, and FZ:
 * <ol>
 *   <li>Residualize: RX = FX − FZ (FZ'FZ + λI)⁻¹ FZ'FX, and similarly RY.</li>
 *   <li>Center residual columns.</li>
 *   <li>Compute: stat = n · ‖cov(RX, RY)‖²_F</li>
 * </ol>
 * Under the null, this statistic follows an asymptotic weighted sum of chi-squared(1)
 * variables, with weights given by the eigenvalues of the Khatri-Rao residual
 * covariance matrix.
 *
 * <h2>P-value approximation</h2>
 * Four methods are available via {@link Approx}:
 * <ul>
 *   <li><b>GAMMA</b> (default) — Satterthwaite gamma approximation via moment
 *       matching. Moments are computed without forming the full Khatri-Rao matrix,
 *       using the identity tr(Cov) = ‖Z‖²_F/(n−1) and
 *       tr(Cov²) = ‖ZZᵀ‖²_F/(n−1)², making this substantially faster than
 *       eigendecomposition-based methods.</li>
 *   <li><b>SADDLEPOINT</b> — Lugannani-Rice saddlepoint approximation (requires
 *       eigendecomposition of the Khatri-Rao covariance).</li>
 *   <li><b>DAVIES_IMHOF</b> — Davies/Imhof numerical integration (requires
 *       eigendecomposition).</li>
 *   <li><b>PERMUTATION</b> — permutation test over row shuffles of RY; exact
 *       but slow. The number of permutations is controlled by
 *       {@link #setPermutations}.</li>
 * </ul>
 *
 * <h2>Caching</h2>
 * Feature matrices are cached by a key that encodes the variable names, active row
 * set, feature count, feature type, bandwidth settings, seed, and RCIT/RCoT mode.
 * The cache is invalidated whenever any hyperparameter that affects feature
 * construction is changed, or when the active row set is updated via
 * {@link #setRows}.
 *
 * <h2>Key hyperparameters</h2>
 * <ul>
 *   <li>{@code numFeatXY} — feature dimension for X (and Y in RCoT mode); default 10.</li>
 *   <li>{@code numFeatZ} — feature dimension for Z; default 100.</li>
 *   <li>{@code numFeatYAug} — feature dimension for the augmented [Y,Z] block in
 *       RCIT mode; default 50.</li>
 *   <li>{@code lambda} — ridge penalty in the residualization step; default 0.001.</li>
 *   <li>{@code bandwidthMultiplier} — scales the median-distance bandwidth
 *       estimate; default 1.0.</li>
 *   <li>{@code bwMaxRows} — maximum rows used in the bandwidth estimation
 *       subsample; default 500.</li>
 * </ul>
 *
 * @see FfCiContinuous
 * @see IndependenceTest
 * @see RowsSettable
 */
@Deprecated(since = "7.9", forRemoval = true)
public final class Rcit implements IndependenceTest, RowsSettable {

    // ---------------- core data ----------------
    private final DataSet data;
    private final List<Node> vars;

    // Feature cache (same style as FfCiContinuous)
    private final Map<String, SimpleMatrix> featCache = new HashMap<>();

    // Active rows state
    private List<Integer> rows = null;
    private int n;

    // ---------------- knobs ----------------
    private double alpha = 0.05;
    private double lastP = NaN;
    private boolean verbose = false;

    // Ridge (absolute, like FfCiContinuous; RCIT paper uses ridge in regression step)
    private double lambda = .001;

    // Feature-space approximation
    private Approx approx = Approx.GAMMA;
    private int permutations = 200;

    // RCIT-specific feature sizing
    private int numFeatXY = 10;     // features for X and (usually) Y
    private int numFeatZ = 100;     // features for Z
    private int numFeatYAug = 50;   // features for augmented [Y,Z] block (RCIT mode)
    private boolean doRcit = true;  // true=RCIT (augment Y with Z when Z nonempty), false=RCoT (no augmentation)

    // RFF/ORF machinery (copied from FfCiContinuous)
    private FeatureType featureType = FeatureType.RFF;
    private int bwMaxRows = 500;
    private double bandwidthMultiplier = 1.0;
    private long seed = 1729L;

    // --------------------------------------------------------------------
    // Construction
    // --------------------------------------------------------------------

    /**
     * Constructs an instance of the Rcit class using the provided DataSet and default parameters.
     *
     * @param dataSet the dataset to be used for initializing the Rcit instance; must not be null.
     */
    public Rcit(DataSet dataSet) {
        this(dataSet, new Parameters());
    }

    /**
     * Constructs an instance of the Rcit class using the provided DataSet and Parameters.
     *
     * @param dataSet the dataset to be used for initializing the Rcit instance; must not be null.
     * @param params the set of parameters to configure the behavior of the Rcit instance; must not be null.
     */
    public Rcit(DataSet dataSet, Parameters params) {
        this.data = DataTransforms.standardizeData(dataSet);
        this.vars = Collections.unmodifiableList(new ArrayList<>(dataSet.getVariables()));
        this.n = getActiveRowCount();

        // Optional: read some params if you want parity with other tests
        this.seed = params.getLong("rcit.seed", this.seed);
        this.lambda = params.getDouble("rcit.lambda", this.lambda);
        this.numFeatXY = params.getInt("rcit.numFeatXY", this.numFeatXY);
        this.numFeatZ = params.getInt("rcit.numFeatZ", this.numFeatZ);
        this.numFeatYAug = params.getInt("rcit.numFeatYAug", this.numFeatYAug);
        this.doRcit = params.getBoolean("rcit.doRcit", this.doRcit);
        this.bwMaxRows = params.getInt("rcit.bwMaxRows", this.bwMaxRows);
        this.bandwidthMultiplier = params.getDouble("rcit.bandwidthMultiplier", this.bandwidthMultiplier);

        invalidateFeatureCache();
    }

    // --------------------------------------------------------------------
    // Core math utilities (kept identical in spirit to FfCiContinuous)
    // --------------------------------------------------------------------

    /** z-score columns, ddof=1. */
    private static void zscoreInPlace(SimpleMatrix M) {
        int n = M.getNumRows(), d = M.getNumCols();
        if (n < 2 || d == 0) return;

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

    /** cov(A,B) = A^T B / (n-1), assumes column-centered. */
    private static SimpleMatrix cov(SimpleMatrix A, SimpleMatrix B) {
        int n = A.getNumRows();
        return A.transpose().mult(B).scale(1.0 / (n - 1));
    }

    /** Frobenius norm squared. */
    private static double frob2(SimpleMatrix M) {
        double s = 0.0;
        double[] a = M.getDDRM().data;
        for (double v : a) s += v * v;
        return s;
    }

    /**
     * Ridge residualization on covariance scale (same as FfCiContinuous):
     * X - Z (Czz + λI)^{-1} Czx
     */
    private static SimpleMatrix ridgeResidual(SimpleMatrix X, SimpleMatrix Z, double lambda) {
        if (Z == null || Z.getNumCols() == 0) return X;

        int n = Z.getNumRows();
        double denom = TMath.max(1.0, n - 1.0);

        SimpleMatrix Czz = Z.transpose().mult(Z).scale(1.0 / denom);
        SimpleMatrix Czx = Z.transpose().mult(X).scale(1.0 / denom);

        SimpleMatrix A = Czz.plus(SimpleMatrix.identity(Czz.getNumRows())
                .scale(TMath.max(1e-18, lambda)));

        SimpleMatrix B = A.solve(Czx);  // (Czz + λI)^{-1} Czx
        return X.minus(Z.mult(B));
    }

    /** Covariance of elementwise residual products. */
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

    /** Positive eigenvalues only. */
    private static double[] positiveEigs(SimpleMatrix Cov) {
        SimpleEVD<SimpleMatrix> evd = Cov.eig();
        List<Double> out = new ArrayList<>();

        for (int i = 0; i < evd.getNumberOfEigenvalues(); i++) {
            double v = evd.getEigenvalue(i).getReal();
            if (v > 1e-12 && Double.isFinite(v)) out.add(v);
        }

        double[] e = new double[out.size()];
        for (int i = 0; i < e.length; i++) e[i] = out.get(i);
        return e;
    }

    /** Center columns in-place (for feature matrices). */
    private static void subtractColumnMeansInPlace(SimpleMatrix M) {
        int n = M.getNumRows(), d = M.getNumCols();
        if (n == 0 || d == 0) return;

        for (int j = 0; j < d; j++) {
            double s = 0.0;
            for (int i = 0; i < n; i++) s += M.get(i, j);
            double mean = s / n;
            for (int i = 0; i < n; i++) M.set(i, j, M.get(i, j) - mean);
        }
    }

    /** cov(A, permuted(B)) = A^T * B_perm / (n-1), assumes centered. */
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

    /** ORF: block-orthogonal rows in blocks of size d. */
    private static double[][] sampleOrthogonalW(int mFeatures, int d, double wStd) {
        double[][] W = new double[mFeatures][d];
        if (d <= 0) return W;

        int filled = 0;
        while (filled < mFeatures) {
            int block = TMath.min(d, mFeatures - filled);

            double[][] Q = new double[block][d];
            for (int i = 0; i < block; i++) {
                for (int j = 0; j < d; j++) Q[i][j] = RandomUtil.getInstance().nextGaussian();
            }

            // Gram–Schmidt rows of Q
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
                double r = chiRadius(d);
                double s = wStd * r;
                int outRow = filled + i;
                for (int j = 0; j < d; j++) W[outRow][j] = s * Q[i][j];
            }

            filled += block;
        }

        return W;
    }

    private static double chiRadius(int d) {
        double ss = 0.0;
        for (int k = 0; k < d; k++) {
            double g = RandomUtil.getInstance().nextGaussian();
            ss += g * g;
        }
        return TMath.sqrt(TMath.max(1e-18, ss));
    }

    /**
     * Median of pairwise squared distances for up to maxRows points.
     * Deterministic subsampling via evenly spaced indices.
     */
    private static double medianDistanceSquaredND(double[][] Z, int maxRows) {
        int n = Z.length;
        int d = (n == 0) ? 0 : Z[0].length;
        if (n < 3 || d == 0) return 1.0;

        int m = TMath.min(n, TMath.max(3, maxRows));

        int[] idx = new int[m];
        if (m == n) {
            for (int i = 0; i < m; i++) idx[i] = i;
        } else {
            for (int i = 0; i < m; i++) idx[i] = (int) TMath.floor((i * (long) (n - 1)) / (double) (m - 1));
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
        while (firstPos < t && d2[firstPos] <= 0) firstPos++;
        if (firstPos >= t) return 1.0;
        int mid = firstPos + (t - firstPos) / 2;
        return d2[mid];
    }

    /** z-score raw columns, ddof=1 (for double[][] blocks). */
    private static void zscoreInPlace(double[][] M) {
        int n = M.length;
        if (n < 2) return;
        int d = M[0].length;
        if (d == 0) return;

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
            for (int i = 0; i < n; i++) M[i][j] = (M[i][j] - mean) / sd;
        }
    }

    // --------------------------------------------------------------------
    // IndependenceTest
    // --------------------------------------------------------------------

    /**
     * Checks the statistical independence between two nodes x and y, optionally conditioned on a set of variables z.
     * The method performs calculations based on specified statistical approaches such as RCoT or RCIT.
     * It computes a test statistic and assesses its significance against a specified threshold (alpha).
     *
     * @param x The first variable (node) under test.
     * @param y The second variable (node) under test.
     * @param z An optional set of conditioning variables (nodes). If null, the method assumes no conditioning variables.
     * @return An {@link IndependenceResult} containing the independence fact, test result (true if independent, false otherwise),
     *         calculated p-value, and the difference between the significance level and the p-value.
     * @throws InterruptedException If the thread executing the method is interrupted.
     */
    @Override
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z)
            throws InterruptedException {

        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        Objects.requireNonNull(x, "x");
        Objects.requireNonNull(y, "y");

        List<Node> Z = (z == null) ? List.of() : new ArrayList<>(z);

        // keep consistent determinism
        Z.sort(Comparator.comparing(Node::getName));

        // Update n for current rows
        this.n = getActiveRowCount();

        if (x.equals(y)) {
            lastP = 0.0;
            IndependenceFact fact = new IndependenceFact(x, y, new HashSet<>(Z));
            return new IndependenceResult(fact, false, lastP, alpha - lastP);
        }

        if (n < 5) {
            lastP = 1.0;
            IndependenceFact fact = new IndependenceFact(x, y, new HashSet<>(Z));
            return new IndependenceResult(fact, true, lastP, alpha - lastP);
        }

        // ------------------------------------------------------------
        // 1) Feature blocks (FfCiContinuous architecture)
        // ------------------------------------------------------------
        SimpleMatrix FX = rffOrOrfFeaturesFor(List.of(x), numFeatXY, "X");

        final SimpleMatrix FY;
        if (doRcit && !Z.isEmpty()) {
            // RCIT: augment Y-block with Z block (as in your earlier intent).
            ArrayList<Node> yAug = new ArrayList<>(1 + Z.size());
            yAug.add(y);
            yAug.addAll(Z);
            FY = rffOrOrfFeaturesFor(yAug, numFeatYAug, "Yaug");
        } else {
            // RCoT (or unconditional): plain Y features
            FY = rffOrOrfFeaturesFor(List.of(y), numFeatXY, "Y");
        }

        SimpleMatrix FZ = Z.isEmpty() ? null : rffOrOrfFeaturesFor(Z, numFeatZ, "Z");

        // IMPORTANT: follow FfCiContinuous pattern (zscore after centering)
        zscoreInPlace(FX);
        zscoreInPlace(FY);
        if (FZ != null) zscoreInPlace(FZ);

        // ------------------------------------------------------------
        // 2) Residualize on Z (ridge) — same as FfCiContinuous
        // ------------------------------------------------------------
        SimpleMatrix RX = ridgeResidual(FX, FZ, lambda);
        SimpleMatrix RY = ridgeResidual(FY, FZ, lambda);

        // Ensure centered residuals before covariance/statistic (matches cov() assumption).
        subtractColumnMeansInPlace(RX);
        subtractColumnMeansInPlace(RY);

        // ------------------------------------------------------------
        // 3) Statistic
        // ------------------------------------------------------------
        SimpleMatrix Cxy = cov(RX, RY);
        double stat = n * frob2(Cxy);

        // ------------------------------------------------------------
        // 4) P-value (exactly the same machinery as FfCiContinuous)
        // ------------------------------------------------------------
        double p;

        if (approx == Approx.PERMUTATION && permutations > 0) {
            int greater = 0;

            // Fisher–Yates permutation of row indices.
            int[] perm = new int[n];
            for (int i = 0; i < n; i++) perm[i] = i;

            // Deterministic per-fact permutation RNG (stable across calls)
            long permSeed = seedForPermutation(x, y, Z);

            for (int b = 0; b < permutations; b++) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();

                for (int i = n - 1; i > 0; i--) {
                    int j = RandomUtil.getInstance().nextInt(i + 1);
                    int t = perm[i];
                    perm[i] = perm[j];
                    perm[j] = t;
                }

                SimpleMatrix Cb = covWithPermutedB(RX, RY, perm);
                double statB = n * frob2(Cb);
                if (statB >= stat) greater++;
            }

            p = (greater + 1.0) / (permutations + 1.0);

//        }
//        else {
//            SimpleMatrix Cov = kronResCov(RX, RY);
//            double[] eig = positiveEigs(Cov);
//
//            p = switch (approx) {
//                case GAMMA -> QuadraticFormPValues.gammaSatterthwaiteP(stat, eig);
//                case SADDLEPOINT -> QuadraticFormPValues.saddlepointLugannaniRiceP(stat, eig);
//                case DAVIES_IMHOF -> QuadraticFormPValues.daviesP(stat, eig);
//                default -> QuadraticFormPValues.gammaSatterthwaiteP(stat, eig);
//            };
//        }
        } else {
            p = switch (approx) {
                case GAMMA -> {
                    Moments mv = gammaMomentsNoEig(RX, RY);
                    yield gammaSatterthwaitePFromMoments(stat, mv.mu, mv.var);
                }
                case SADDLEPOINT, DAVIES_IMHOF -> {
                    // Keep the old path for the methods that truly need eigenvalues.
                    SimpleMatrix Cov = kronResCov(RX, RY);
                    double[] eig = positiveEigs(Cov);
                    yield (approx == Approx.SADDLEPOINT)
                            ? QuadraticFormPValues.saddlepointLugannaniRiceP(stat, eig)
                            : QuadraticFormPValues.daviesP(stat, eig);
                }
                default -> {
                    Moments mv = gammaMomentsNoEig(RX, RY);
                    yield gammaSatterthwaitePFromMoments(stat, mv.mu, mv.var);
                }
            };
        }


        if (!Double.isFinite(p)) p = 1.0;
        p = TMath.min(1.0, TMath.max(0.0, p));
        lastP = p;

        IndependenceFact fact = new IndependenceFact(x, y, new HashSet<>(Z));

        if (verbose) {
            TetradLogger.getInstance().log(fact + " p=" + p
                    + " stat=" + stat
                    + " approx=" + approx
                    + (approx == Approx.PERMUTATION ? (" perms=" + permutations) : "")
                    + " doRcit=" + doRcit);
        }

        return new IndependenceResult(fact, p > alpha, p, alpha - p);
    }

    // --------------------------------------------------------------------
    // RCIT / RCoT setters (keep them; don’t drop methods)
    // --------------------------------------------------------------------

    /**
     * Sets the value indicating whether the RCIT (Randomized Conditional Independence Test) functionality
     * should be enabled or disabled.
     *
     * Enabling or disabling the RCIT functionality affects how the class performs calculations related to
     * dependency testing. Additionally, this method invalidates the feature cache to ensure consistency in
     * subsequent calculations.
     *
     * @param doRcit A boolean value indicating whether to enable (true) or disable (false) the RCIT functionality.
     */
    public void setDoRcit(boolean doRcit) {
        this.doRcit = doRcit;
        invalidateFeatureCache();
    }

    /**
     * Returns the current state of the RCIT (Randomized Conditional Independence Test) functionality.
     *
     * This method indicates whether the RCIT functionality is enabled or disabled in the class.
     * It is used to determine whether RCIT-based calculations should be performed.
     *
     * @return true if the RCIT functionality is enabled; false otherwise.
     */
    public boolean isDoRcit() {
        return doRcit;
    }

    /**
     * Sets the number of features corresponding to the XY interaction for this instance.
     * The value is clamped to a minimum of 1 to ensure a valid number of features.
     * This method also invalidates the feature cache to guarantee the results
     * reflect the updated configuration.
     *
     * @param d The desired number of features for the XY interaction. If the value
     *          is less than 1, it will be automatically set to 1.
     */
    public void setNumFeaturesXY(int d) {
        this.numFeatXY = TMath.max(1, d);
        invalidateFeatureCache();
    }

    /**
     * Sets the number of features corresponding to the Z interaction for this instance.
     * The value is clamped to a minimum of 1 to ensure a valid number of features.
     * This method also invalidates the feature cache to guarantee the results
     * reflect the updated configuration.
     *
     * @param d The desired number of features for the Z interaction. If the value
     *          is less than 1, it will be automatically set to 1.
     */
    public void setNumFeaturesZ(int d) {
        this.numFeatZ = TMath.max(1, d);
        invalidateFeatureCache();
    }

    /**
     * Sets the number of augmented Y features for this instance.
     * The value is clamped to a minimum of 1 to ensure a valid number of features.
     * This method also invalidates the feature cache to ensure the updated
     * configuration is applied correctly in subsequent calculations.
     *
     * @param d The desired number of augmented Y features. If the value
     *          is less than 1, it will be automatically set to 1.
     */
    public void setNumFeaturesYAug(int d) {
        this.numFeatYAug = TMath.max(1, d);
        invalidateFeatureCache();
    }

    /**
     * Sets the approximation object to be used. This method assigns the provided
     * {@code Approx} instance to the internal field, ensuring it is not null.
     *
     * @param approx the approximation object to set; must not be null
     * @throws NullPointerException if {@code approx} is null
     */
    public void setApproximation(Approx approx) {
        this.approx = Objects.requireNonNull(approx, "approx");
    }

    /**
     * Sets the number of permutations. The value is constrained to be a non-negative integer.
     *
     * @param permutations the desired number of permutations. If the provided value is negative, it will be set to 0.
     */
    public void setPermutations(int permutations) {
        this.permutations = TMath.max(0, permutations);
    }

    /**
     * Sets the value of the lambda parameter and invalidates the feature cache.
     *
     * @param lambda the new value to set for the lambda parameter
     */
    public void setLambda(double lambda) {
        this.lambda = lambda;
        invalidateFeatureCache();
    }

    /**
     * Sets the feature type for this instance and invalidates the feature cache.
     *
     * @param featureType the feature type to be set; must not be null
     * @throws NullPointerException if the provided featureType is null
     */
    public void setFeatureType(FeatureType featureType) {
        this.featureType = Objects.requireNonNull(featureType, "featureType");
        invalidateFeatureCache();
    }

    /**
     * Sets the maximum number of rows for the bandwidth calculations.
     * This value determines the upper limit of rows to be considered in relevant operations.
     *
     * @param bwMaxRows the maximum number of rows to set, must be a non-negative integer
     */
    public void setBwMaxRows(int bwMaxRows) {
        this.bwMaxRows = bwMaxRows;
        invalidateFeatureCache();
    }

    /**
     * Sets the bandwidth multiplier, which is used to scale the bandwidth allocation.
     * The value must be greater than 0 and finite.
     *
     * @param bandwidthMultiplier the scaling factor for bandwidth allocation.
     *                             Must be a positive, finite double value.
     * @throws IllegalArgumentException if the provided value is not greater than 0
     *                                  or is not finite.
     */
    public void setBandwidthMultiplier(double bandwidthMultiplier) {
        if (!(bandwidthMultiplier > 0) || !Double.isFinite(bandwidthMultiplier)) {
            throw new IllegalArgumentException("bandwidthMultiplier must be > 0 and finite");
        }
        this.bandwidthMultiplier = bandwidthMultiplier;
        invalidateFeatureCache();
    }

    /**
     * Sets the seed value for generating random values or reproducible sequences.
     *
     * @param seed the seed value to be used for initialization
     */
    public void setSeed(long seed) {
        this.seed = seed;
        invalidateFeatureCache();
    }

    /**
     * Retrieves the most recently computed p-value.
     *
     * @return the last computed p-value as a double
     */
    public double getPValue() {
        return lastP;
    }

    // --------------------------------------------------------------------
    // Feature building (copied from FfCiContinuous, only lightly refactored)
    // --------------------------------------------------------------------

    /**
     * Builds RFF/ORF features for the block of variables 'vs'.
     *
     * <p>- Extracts raw continuous columns for vars in vs over the active rows
     * <br>- z-scores raw columns
     * <br>- chooses bw2 using median pairwise distance^2 (continuous only)
     * <br>- maps to RFF or ORF features for an RBF kernel
     * <br>- centers feature columns (so cov(A,B) = A^T B/(n-1) is correct after centering)
     */
    private SimpleMatrix rffOrOrfFeaturesFor(List<Node> vs, int mFeatures, String tag) {
        Objects.requireNonNull(vs, "vs");
        if (mFeatures <= 0) return new SimpleMatrix(getActiveRowCount(), 0);

        final String key = featKey(tag, vs, mFeatures);
        SimpleMatrix cached = featCache.get(key);
        if (cached != null) return cached;

        final int n = getActiveRowCount();
        final double[][] Zraw = extractRawBlock(vs);   // n x d
        zscoreInPlace(Zraw);                           // standardize raw columns first

        double bw2 = 1.0;
        if (Zraw.length > 0 && Zraw[0].length > 0) {
            bw2 = medianDistanceSquaredND(Zraw, TMath.min(n, bwMaxRows));
            if (!(bw2 > 0) || !Double.isFinite(bw2)) bw2 = 1.0;
            bw2 *= (bandwidthMultiplier * bandwidthMultiplier);
            if (bw2 < 1e-12) bw2 = 1e-12;
        }

        long localSeed = seedForBlock(tag, vs) ^ seed;
        double[][] Phi = rffFeatures(Zraw, mFeatures, bw2, localSeed);

        SimpleMatrix M = new SimpleMatrix(Phi);

        // Center features (cov() assumes centered)
        subtractColumnMeansInPlace(M);

        featCache.put(key, M);
        return M;
    }

    private String featKey(String tag, List<Node> vs, int mFeatures) {
        ArrayList<String> names = new ArrayList<>(vs.size());
        for (Node v : vs) names.add(v.getName());
        names.sort(String::compareTo);

        StringBuilder sb = new StringBuilder(220);
        sb.append("RCIT|tag=").append(tag)
                .append("|n=").append(getActiveRowCount())
                .append("|rowsHash=").append(activeRowsHash())
                .append("|m=").append(mFeatures)
                .append("|ft=").append(featureType.name())
                .append("|bwMult=").append(Double.doubleToLongBits(bandwidthMultiplier))
                .append("|bwMax=").append(bwMaxRows)
                .append("|seed=").append(seed)
                .append("|doRcit=").append(doRcit ? 1 : 0)
                .append("|vars=");
        for (String s : names) sb.append(s).append(",");
        return sb.toString();
    }

    /** Deterministic seed for a block; makes results stable across runs. */
    private long seedForBlock(String tag, List<Node> block) {
        long h = 1469598103934665603L; // FNV-ish
        h = 1099511628211L * (h ^ tag.hashCode());

        ArrayList<String> names = new ArrayList<>(block.size());
        for (Node v : block) names.add(v.getName());
        names.sort(String::compareTo);
        for (String s : names) h = 1099511628211L * (h ^ s.hashCode());

        h = 1099511628211L * (h ^ getActiveRowCount());
        h = 1099511628211L * (h ^ activeRowsHash());
        return h;
    }

    /** Deterministic seed for permutation loop (stable per fact). */
    private long seedForPermutation(Node x, Node y, List<Node> Z) {
        long h = 1469598103934665603L;
        h = 1099511628211L * (h ^ x.getName().hashCode());
        h = 1099511628211L * (h ^ y.getName().hashCode());
        for (Node z : Z) h = 1099511628211L * (h ^ z.getName().hashCode());
        h = 1099511628211L * (h ^ getActiveRowCount());
        h = 1099511628211L * (h ^ activeRowsHash());
        h = 1099511628211L * (h ^ (doRcit ? 1 : 0));
        return h ^ seed;
    }

    /**
     * Extract raw data for vars in vs over the active rows.
     * Assumes all variables are continuous.
     */
    private double[][] extractRawBlock(List<Node> vs) {
        final int n = getActiveRowCount();
        final int d = vs.size();
        double[][] Z = new double[n][d];

        for (int j = 0; j < d; j++) {
            Node v = vs.get(j);
            int col = data.getColumnIndex(v);
            if (col < 0) throw new IllegalArgumentException("Variable not found: " + v.getName());

            for (int i = 0; i < n; i++) {
                int row = activeRowIndex(i);
                Z[i][j] = data.getDouble(row, col);
            }
        }
        return Z;
    }

    /**
     * RFF/ORF features for RBF kernel k(x,x') = exp(-||x-x'||^2 / bw2).
     * Uses:
     * wStd = sqrt(2 / bw2), phi = sqrt(2/m) cos(Wx + b)
     */
    private double[][] rffFeatures(double[][] Z, int mFeatures, double bw2, long seed) {
        final int n = Z.length;
        final int d = (n == 0) ? 0 : Z[0].length;

        double[][] Phi = new double[n][mFeatures];
        if (n == 0) return Phi;

        // Handle d=0: constant features (cos(b))
        if (d == 0) {
            double scale0 = TMath.sqrt(2.0 / mFeatures);
            double[] b0 = new double[mFeatures];
            for (int j = 0; j < mFeatures; j++) b0[j] = 2.0 * TMath.PI * RandomUtil.getInstance().nextDouble();
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < mFeatures; j++) Phi[i][j] = scale0 * TMath.cos(b0[j]);
            }
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
                for (int k = 0; k < d; k++) W[j][k] = wStd * RandomUtil.getInstance().nextGaussian();
                b[j] = 2.0 * TMath.PI * RandomUtil.getInstance().nextDouble();
            }
        } else if (featureType == FeatureType.ORF) {
            W = sampleOrthogonalW(mFeatures, d, wStd);
            for (int j = 0; j < mFeatures; j++) b[j] = 2.0 * TMath.PI * RandomUtil.getInstance().nextDouble();
        } else {
            throw new IllegalArgumentException("featureType must be RFF or ORF");
        }

        for (int i = 0; i < n; i++) {
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

    private int activeRowsHash() {
        if (rows == null) return 0;
        int h = 1;
        for (int r : rows) h = 31 * h + r;
        return h;
    }

    private void invalidateFeatureCache() {
        featCache.clear();
    }

    // --------------------------------------------------------------------
    // RowsSettable
    // --------------------------------------------------------------------

    @Override
    public List<Integer> getRows() {
        return rows;
    }

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
            if (r == null) throw new NullPointerException("Row " + i + " is null.");
            if (r < 0) throw new IllegalArgumentException("Row " + i + " is negative.");
            if (r >= data.getNumRows()) throw new IllegalArgumentException("Row " + i + " out of bounds: " + r);
        }

        this.rows = new ArrayList<>(rows);
        this.n = this.rows.size();
        invalidateFeatureCache();
    }

    private int getActiveRowCount() {
        return (rows == null) ? data.getNumRows() : rows.size();
    }

    private int activeRowIndex(int i) {
        return (rows == null) ? i : rows.get(i);
    }

    // ---- Gamma approximation via moments (no eigenvalues needed) ----

    /**
     * Computes the first two moments needed for the Gamma–Satterthwaite approximation
     * of the quadratic form used by RCIT:
     *
     *   Q = n * || cov(RX, RY) ||_F^2
     *
     * Under the usual approximation, Q behaves like sum_i lambda_i * chi^2_1,
     * so we need:
     *   mu  = sum_i lambda_i = tr(Cov)
     *   var = 2 * sum_i lambda_i^2 = 2 * tr(Cov^2)
     *
     * where Cov is the covariance of the vectorized elementwise products RX_i * RY_j.
     *
     * This implementation avoids forming the explicit kron-product feature matrix
     * and avoids eigen-decomposition.
     */
    private static Moments gammaMomentsNoEig(SimpleMatrix RX, SimpleMatrix RY) {
        final int n = RX.getNumRows();
        final int fx = RX.getNumCols();
        final int fy = RY.getNumCols();

        if (n < 2 || fx == 0 || fy == 0) {
            // Degenerate; caller will clamp p anyway.
            return new Moments(0.0, 0.0);
        }

        final double denom = (double) (n - 1);

        // ---- mu = tr(Cov) = (1/(n-1)) * ||Z||_F^2
        // Z has columns RX[:,i] .* RY[:,j], but we never build it.
        // ||Z||_F^2 = sum_r (sum_i RX[r,i]^2) * (sum_j RY[r,j]^2).
        double sumAxAy = 0.0;

        for (int r = 0; r < n; r++) {
            double ax = 0.0;
            for (int i = 0; i < fx; i++) {
                double v = RX.get(r, i);
                ax += v * v;
            }
            double ay = 0.0;
            for (int j = 0; j < fy; j++) {
                double v = RY.get(r, j);
                ay += v * v;
            }
            sumAxAy += ax * ay;
        }

        final double mu = sumAxAy / denom; // tr(Cov)

        // ---- tr(Cov^2) = (1/(n-1)^2) * ||Z^T Z||_F^2
        // Use identity ||Z^T Z||_F^2 = ||Z Z^T||_F^2 and
        // (Z Z^T)[r,s] = (RX RX^T)[r,s] * (RY RY^T)[r,s].
        //
        // Let A = RX RX^T (n×n), B = RY RY^T (n×n).
        // Then ||Z Z^T||_F^2 = sum_{r,s} (A[r,s]^2 * B[r,s]^2).

        // These multiplies are fast in EJML relative to your old kron+eig path.
        SimpleMatrix A = RX.mult(RX.transpose()); // n×n
        SimpleMatrix B = RY.mult(RY.transpose()); // n×n

        double[] Ad = A.getDDRM().data;
        double[] Bd = B.getDDRM().data;

        double sumA2B2 = 0.0;
        final int len = Ad.length; // should be n*n
        for (int t = 0; t < len; t++) {
            double a = Ad[t];
            double b = Bd[t];
            // accumulate (a^2)*(b^2)
            double a2 = a * a;
            double b2 = b * b;
            sumA2B2 += a2 * b2;
        }

        final double trCov2 = sumA2B2 / (denom * denom);
        final double var = 2.0 * trCov2;

        return new Moments(mu, var);
    }

    /**
     * Gamma–Satterthwaite p-value using moments (mu, var) of the quadratic form.
     * Matches the standard moment-matching:
     *   shape k = mu^2 / var
     *   scale θ = var / mu
     *
     * Returns upper-tail probability P(Q >= stat).
     */
    private static double gammaSatterthwaitePFromMoments(double stat, double mu, double var) {
        if (!(stat >= 0.0) || !Double.isFinite(stat)) return Double.NaN;
        if (!(mu > 0.0) || !Double.isFinite(mu)) return Double.NaN;
        if (!(var > 0.0) || !Double.isFinite(var)) return Double.NaN;

        double shape = (mu * mu) / var;
        double scale = var / mu;

        if (!(shape > 0.0) || !(scale > 0.0) || !Double.isFinite(shape) || !Double.isFinite(scale)) {
            return Double.NaN;
        }

        // Commons-Math GammaDistribution uses (shape, scale).
        GammaDistribution gd = new GammaDistribution(shape, scale);

        double cdf = gd.cumulativeProbability(stat);
        if (!Double.isFinite(cdf)) return Double.NaN;

        double p = 1.0 - cdf;
        // clamp
        if (p < 0.0) p = 0.0;
        if (p > 1.0) p = 1.0;
        return p;
    }

    /** Tiny value object for moments. */
    private static final class Moments {
        final double mu;
        final double var;
        Moments(double mu, double var) {
            this.mu = mu;
            this.var = var;
        }
    }

    // --------------------------------------------------------------------
    // IndependenceTest boilerplate
    // --------------------------------------------------------------------

    /**
     * Retrieves the list of variables represented as Node objects.
     *
     * @return a list of Node objects representing the variables.
     */
    @Override
    public List<Node> getVariables() {
        return vars;
    }

    /**
     * Retrieves the value of the alpha parameter.
     *
     * @return the alpha value as a double
     */
    @Override
    public double getAlpha() {
        return alpha;
    }

    /**
     * Sets the value of alpha, which must be within the range (0, 1).
     * Throws an IllegalArgumentException if the provided value is outside the valid range.
     *
     * @param a the new alpha value to be set; must be greater than 0 and less than 1
     */
    @Override
    public void setAlpha(double a) {
        if (!(a > 0 && a < 1)) throw new IllegalArgumentException("alpha in (0,1)");
        alpha = a;
    }

    /**
     * Retrieves the dataset associated with this instance.
     *
     * @return the dataset (DataSet) associated with this instance
     */
    @Override
    public DataSet getData() {
        return data;
    }

    /**
     * Determines whether verbose mode is enabled.
     *
     * @return true if verbose mode is enabled, false otherwise
     */
    @Override
    public boolean isVerbose() {
        return verbose;
    }

    /**
     * Sets the verbosity level for the current instance.
     *
     * @param v a boolean indicating whether verbose mode should be enabled (true) or disabled (false)
     */
    @Override
    public void setVerbose(boolean v) {
        verbose = v;
    }

    // --------------------------------------------------------------------
    // Enums
    // --------------------------------------------------------------------

    /**
     * An enumeration representing the types of features that can be used for
     * generating random feature mappings for RBF kernels. The different feature
     * types are:
     *
     * - RFF (Random Fourier Features): A method to approximate shift-invariant
     *   kernel functions like the RBF kernel using random projections based on
     *   Fourier transforms.
     * - ORF (Orthogonal Random Features): A variant of RFF that incorporates
     *   orthogonality constraints to potentially improve approximation quality.
     *
     * This enumeration is used to configure the feature mapping strategy for
     * algorithms that rely on such kernel approximations.
     */
    public enum FeatureType {

        /**
         * Represents the Random Fourier Features (RFF) method for approximating
         * shift-invariant kernel functions such as the Radial Basis Function (RBF) kernel.
         * RFF generates random feature mappings based on Fourier transforms to enable
         * efficient computation of kernel-based algorithms in high-dimensional spaces.
         * It allows for scalable and efficient approximation of non-linear kernels.
         */
        RFF,

        /**
         * Represents the Orthogonal Random Features (ORF) method for approximating
         * shift-invariant kernel functions, such as the Radial Basis Function (RBF) kernel.
         * ORF extends the Random Fourier Features (RFF) approach by incorporating orthogonality
         * constraints on the generated random features. This can potentially improve the
         * quality of kernel approximation, leading to better performance in kernel-based
         * machine learning algorithms.
         */
        ORF
    }

    /**
     * The Approx enumeration defines the types of approximation methods that can be used
     * for statistical computations or tests within the Rcit class.
     *
     * GAMMA:
     * Represents the use of the gamma distribution approximation.
     *
     * SADDLEPOINT:
     * Represents the use of the saddlepoint approximation method.
     *
     * DAVIES_IMHOF:
     * Represents the use of the Davies-Imhof algorithm for approximation.
     *
     * PERMUTATION:
     * Represents the use of permutation-based approximation, which typically involves
     * resampling techniques to generate null distributions.
     */
    public enum Approx {

        /**
         * Represents the gamma
         */
        GAMMA,

        /**
         * Represents the saddlepoint
         */
        SADDLEPOINT,

        /**
         * Represents the Davies-Imhof approximation
         */
        DAVIES_IMHOF,

        /**
         * Represents the permutation-based approximation
         */
        PERMUTATION
    }
}