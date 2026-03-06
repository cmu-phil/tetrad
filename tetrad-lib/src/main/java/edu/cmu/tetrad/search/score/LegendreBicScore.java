package edu.cmu.tetrad.search.score;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.EffectiveSampleSizeSettable;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TMath;
import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.factory.DecompositionFactory_DDRM;
import org.ejml.interfaces.decomposition.CholeskyDecomposition_F64;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static edu.cmu.tetrad.util.TMath.*;

/**
 * <p><b>Legendre BIC score</b></p>
 *
 * <p>
 * Local BIC-style score for structure learning with mixed continuous and discrete variables.
 * Continuous children are modeled using a Student-t location model of the form
 * Y = f(Pa) + ε, fit via IRLS with ridge regularization.
 * Discrete children are modeled using multinomial logistic regression
 * (softmax) with ridge regularization, also fit via IRLS.
 * </p>
 *
 * <p>
 * Continuous parents enter through additive Legendre basis expansions.
 * For each continuous parent X (globally z-scored), values are mapped to [-1,1]
 * and polynomial terms P1(x)..Pt(x) are included, where t = legendreDegree.
 * (The intercept is handled separately.)
 * Discrete parents enter via baseline-dropped one-hot blocks.
 * </p>
 *
 * <p>
 * This yields a nonlinear additive location-model representation in which
 * the conditional mean lies in the span of the chosen Legendre basis.
 * </p>
 *
 * <p><b>Missing data:</b>
 * Rows with missing values in {Y} ∪ Pa(Y) are excluded locally (testwise deletion).
 * </p>
 *
 * <p><b>Score:</b>
 * <pre>
 *   score(Y | Pa(Y)) = logLik_hat − 0.5 · penaltyDiscount · edf · log(n)
 * </pre>
 * where {@code logLik_hat} is the maximized penalized log-likelihood,
 * {@code n} is the effective local sample size, and {@code edf}
 * is the ridge-based effective degrees of freedom.
 * </p>
 *
 * <p><b>Implementation notes:</b></p>
 * <ul>
 *   <li>No hard NaN cutoff at small n; if n is very small, a stable
 *       intercept-only fallback is used.</li>
 *   <li>Mapping to [-1,1] defaults to quantile-based bounds (e.g., 1%..99%)
 *       for robustness rather than raw global extrema.</li>
 *   <li>Cholesky failures during IRLS are handled via adaptive jitter.</li>
 *   <li>Cache keys include all tuning parameters to ensure correctness
 *       under configuration changes.</li>
 * </ul>
 */
public final class LegendreBicScore implements Score, EffectiveSampleSizeSettable {

    /**
     * Represents the dataset used to calculate statistical scores and perform
     * various operations within the LegendreBicScore class. This dataset serves
     * as the primary source of data input for all computations, including fitting
     * models, evaluating scores, and determining effective sample sizes.
     */
    private final DataSet dataSet;
    /**
     * Stores the list of variables (nodes) used in the LegendreBicScore.
     * Represents the set of nodes that will be considered in scoring and
     * fitting operations within the context of the current dataset.
     * The list is immutable once initialized to prevent accidental modification.
     */
    private final List<Node> variables;
    /**
     * The sample size used for statistical computations in the class.
     * This variable represents the total number of observations available
     * in the dataset and is utilized in various scoring and fitting methods.
     * It is a fixed value that remains constant throughout the lifetime
     * of the class instance.
     */
    private final int sampleSize;
    /**
     * A flag indicating whether row subsets should be calculated as part of
     * the scoring procedure.
     */
    private final boolean calculateRowSubsets;
    /**
     * Continuous columns z-scored globally (NaNs preserved). Discrete cols are all NaN.
     */
    private final double[][] zCols;
    /**
     * An atomic reference holding a thread-safe cache for storing and retrieving
     * {@link LocalFit} objects. The cache is implemented as a {@link ConcurrentHashMap},
     * keyed by a unique {@code Long} identifier and used to optimize repeated local fitting
     * calculations in the {@code LegendreBicScore} class.
     */
    private final AtomicReference<ConcurrentHashMap<Long, LocalFit>> localFitCacheRef =
            new AtomicReference<>(new ConcurrentHashMap<>());
    /**
     * Represents the minimum values for the z-coordinate in the mapped Legendre domain
     * for each column of interest. These values are used during transformations and
     * calculations involving Legendre polynomials.
     */
    private final double[] zMin;
    /**
     * Represents the maximum values for the z-coordinate in the mapped Legendre domain
     * for each column of interest. These values are used during transformations and
     * calculations involving Legendre polynomials.
     */
    private final double[] zMax;
    /**
     * Represents the quantiles
     */
    private final double[] zQlo;
    /**
     * Represents the quantiles
     */
    private final double[] zQhi;
    /**
     * Effective sample size.
     */
    private volatile int nEff;
    /**
     * Student-t df for continuous child. Must be > 2.
     */
    private volatile double nu = 5.0;
    /**
     * Initial scale for Student-t IRLS; used as warm-start.
     */
    private volatile double scale = 1.0;
    /**
     * Ridge penalty (>0). Intercept is not penalized.
     */
    private volatile double ridge;
    /**
     * Legendre truncation t (>=1). Features per continuous parent = t.
     */
    private volatile int legendreDegree = 8;
    /**
     * Map z to [-1,1] by x = clamp(z/clip) if CLIP_Z mode is used.
     */
    private volatile double legendreClip = 3.0;
    /**
     * IRLS iterations.
     */
    private volatile int irlsIters = 8;
    /**
     * IRLS stopping tolerance.
     */
    private volatile double irlsTol = 1e-6;
    /**
     * Discount multiplier on the BIC penalty.
     */
    private volatile double penaltyDiscount = 1.0;
    /**
     * Add pairwise interactions using only P1(x)=x for continuous parents.
     */
    private volatile boolean useInteractions = true;
    /**
     * Only the first K continuous parents (in parentIdx order) participate in interactions.
     */
    private volatile int interactionMaxParents = 6;
    /**
     * Minimum n to attempt a nontrivial fit.
     */
    private volatile int minN = 5;
    /**
     * Represents the mapping mode used to transform variables into the Legendre domain.
     * Possible mapping modes are defined in the {@link LegendreMapMode} enum and include
     * options such as value clipping, robust rescaling based on quantile ranges,
     * simple min-max scaling, and scale-down transformations.
     */
    private volatile LegendreMapMode legendreMapMode = LegendreMapMode.ROBUST_MINMAX_Z;
    /**
     * Represents the lower quantile threshold used in mapping numeric values
     * to the Legendre domain. The value is expected to be between 0 and 1,
     * where it determines the proportion of data considered in the lower bound
     * during the mapping process.
     * <p>
     * This variable is primarily used for pre-processing numeric data by transforming
     * continuous variables into a bounded interval within the Legendre domain for
     * model-fitting and score calculations.
     * <p>
     * The `mapLo
     */
    private volatile double mapLoQ = 0.01;
    /**
     * Represents the upper quantile threshold for mapping data to
     * the Legendre polynomial domain. This threshold is used to scale or map
     * data values within a specified range for numerical stability and improved
     * accuracy during calculations.
     * <p>
     * The value is volatile to ensure visibility across threads, as the mapping
     * procedure may involve concurrent computations in a multi-threaded environment.
     * <p>
     * A higher value of this threshold widens the range of data values mapped
     * to the upper end of the [−1, 1] interval in the Legendre domain.
     */
    private volatile double mapHiQ = 0.99;
    /**
     * Controls the scaling factor applied to features during specific computations
     * in the Legendre-based scoring framework.
     * <p>
     * This variable is used to modify the magnitude of feature values to ensure numerical
     * stability or to regularize feature contributions in fitting procedures.
     * <p>
     * A value of 1.0 signifies no scaling, preserving the original feature magnitudes,
     * whereas values less than 1.0 reduce the influence of features, effectively applying
     * a division-like effect (e.g., 0.2 corresponds approximately to a divide-by-5 scaling).
     * <p>
     * The `volatile` modifier ensures visibility of changes to this variable across
     * multiple threads, supporting concurrent operations in multi-threaded environments.
     */
    private volatile double featureScale = 1.0; // 1.0 = no scaling; 0.2 ≈ divide-by-5 effect

    /**
     * Constructs an instance of the LegendreBicScore class.
     * This class computes Bayesian information criterion (BIC) scores for variables
     * in a given dataset, focusing on a mixture of discrete and continuous data. It
     * standardizes the continuous variables using z-scores, applies robust quantile
     * normalization, and initializes parameters for further computations.
     *
     * @param dataSet the input dataset containing data variables. Must not be null.
     *                The dataset is expected to provide methods for retrieving variables,
     *                checking for missing values, and accessing data values.
     *                Throws a NullPointerException if the dataSet is null.
     */
    public LegendreBicScore(DataSet dataSet) {
        if (dataSet == null) throw new NullPointerException("dataSet");
        this.dataSet = dataSet;
        this.variables = new ArrayList<>(dataSet.getVariables());
        this.sampleSize = dataSet.getNumRows();
        this.calculateRowSubsets = dataSet.existsMissingValue();

        setEffectiveSampleSize(-1);

        // reasonable default ridge ~ 1/n
        this.ridge = 1.0 / TMath.max(1, this.sampleSize);

        int p = variables.size();

        // raw (continuous only)
        double[][] raw = new double[p][sampleSize];
        for (int j = 0; j < p; j++) {
            if (isDiscrete(j)) {
                Arrays.fill(raw[j], Double.NaN);
            } else {
                for (int r = 0; r < sampleSize; r++) raw[j][r] = dataSet.getDouble(r, j);
            }
        }

        // z-score continuous
        this.zCols = new double[p][sampleSize];
        for (int j = 0; j < p; j++) {
            if (isDiscrete(j)) {
                Arrays.fill(zCols[j], Double.NaN);
            } else {
                zscoreColumnPreserveNaN(raw[j], zCols[j]);
            }
        }

        this.zMin = new double[p];
        this.zMax = new double[p];
        this.zQlo = new double[p];
        this.zQhi = new double[p];
        Arrays.fill(zMin, Double.NaN);
        Arrays.fill(zMax, Double.NaN);
        Arrays.fill(zQlo, Double.NaN);
        Arrays.fill(zQhi, Double.NaN);

        for (int j = 0; j < p; j++) {
            if (isDiscrete(j)) continue;

            double lo = Double.POSITIVE_INFINITY;
            double hi = Double.NEGATIVE_INFINITY;

            int nFinite = 0;
            for (int r = 0; r < sampleSize; r++) {
                double z = zCols[j][r];
                if (Double.isNaN(z)) continue;
                nFinite++;
                if (z < lo) lo = z;
                if (z > hi) hi = z;
            }

            if (nFinite >= 2 && Double.isFinite(lo) && Double.isFinite(hi) && hi > lo) {
                zMin[j] = lo;
                zMax[j] = hi;
                // robust defaults using the current default quantiles
                zQlo[j] = quantileOfFinite(zCols[j], mapLoQ);
                zQhi[j] = quantileOfFinite(zCols[j], mapHiQ);
                // if quantiles collapse (tiny n), fall back to min/max
                if (!(Double.isFinite(zQlo[j]) && Double.isFinite(zQhi[j]) && zQhi[j] > zQlo[j])) {
                    zQlo[j] = lo;
                    zQhi[j] = hi;
                }
            }
        }
    }

    private static double clamp(double x) {
        if (x > 1.0) return 1.0;
        if (x < -1.0) return -1.0;
        return x;
    }

    private static void centerInPlace(double[] y) {
        double m = 0.0;
        for (double v : y) m += v;
        m /= y.length;
        for (int i = 0; i < y.length; i++) y[i] -= m;
    }

    private static double warmStartScale(double[] yCentered, int n) {
        double s2 = 0.0;
        for (int i = 0; i < n; i++) s2 += yCentered[i] * yCentered[i];
        double rms = sqrt(max(1e-12, s2 / max(1, n)));
        if (!Double.isFinite(rms) || rms <= 0) rms = 1.0;
        return rms;
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

    private static double quantileOfFinite(double[] col, double q) {
        int n = 0;
        for (double v : col) if (!Double.isNaN(v)) n++;
        if (n == 0) return Double.NaN;

        double[] a = new double[n];
        int k = 0;
        for (double v : col) if (!Double.isNaN(v)) a[k++] = v;
        Arrays.sort(a);

        double pos = q * (a.length - 1);
        int lo = (int) floor(pos);
        int hi = (int) ceil(pos);
        if (lo == hi) return a[lo];
        double t = pos - lo;
        return (1.0 - t) * a[lo] + t * a[hi];
    }

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

    private static void fillSoftmaxProbsFromPhi(int K, int n,
                                                double[][] beta,
                                                double[][] Phi,
                                                double[] logitsScratch,
                                                double[][] outProbs) {
        final int C = K - 1;

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
                outProbs[i][k] = e;
                sum += e;
            }

            final double inv = 1.0 / sum;
            for (int k = 0; k < K; k++) outProbs[i][k] *= inv;
        }
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

            ll += (logitsScratch[y[i]] - maxLog) - log(sum);
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

    private static void symmetrizeLowerToFull(DMatrixRMaj G) {
        for (int a = 0; a < G.numRows; a++) {
            for (int b = 0; b < a; b++) {
                G.set(b, a, G.get(a, b));
            }
        }
    }

    private static void addRidgeToDiagonal(DMatrixRMaj G, double ridge, boolean skipIntercept) {
        int start = skipIntercept ? 1 : 0;
        for (int a = start; a < G.numRows; a++) G.add(a, a, ridge);
    }

    /**
     * Cholesky with a few jitter attempts; returns null if it still fails.
     */
    private static CholeskyDecomposition_F64<DMatrixRMaj> cholWithJitter(DMatrixRMaj G, double ridge, double featureScale) {
        CholeskyDecomposition_F64<DMatrixRMaj> chol = DecompositionFactory_DDRM.chol(true);
        if (chol.decompose(G)) return chol;

        double ridgeEff = ridge * (featureScale * featureScale);
        addRidgeToDiagonal(G, ridgeEff, true);

        chol = DecompositionFactory_DDRM.chol(true);
        if (chol.decompose(G)) return chol;

        return null;
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

    private static double traceInvFromCholeskyLower(DMatrixRMaj L) {
        int n = L.numRows;
        double tr = 0.0;
        double[] v = new double[n];

        for (int col = 0; col < n; col++) {
            Arrays.fill(v, 0.0);
            v[col] = 1.0;

            // solve L u = e_col
            for (int i = 0; i < n; i++) {
                double sum = v[i];
                for (int j = 0; j < i; j++) sum -= L.get(i, j) * v[j];
                v[i] = sum / L.get(i, i);
            }

            double ss = 0.0;
            for (int i = 0; i < n; i++) ss += v[i] * v[i];
            tr += ss;
        }
        return tr;
    }

    /**
     * Trace of (G_p)^{-1} where G_p is the penalized block excluding intercept.
     */
    private static double traceInvPenalizedBlockFromG(DMatrixRMaj Gfull) {
        final int M = Gfull.numRows;
        if (M <= 1) return 0.0;

        final int Mp = M - 1;
        final DMatrixRMaj Gp = new DMatrixRMaj(Mp, Mp);

        for (int a = 0; a < Mp; a++) {
            for (int b = 0; b <= a; b++) {
                Gp.set(a, b, Gfull.get(a + 1, b + 1));
            }
        }
        for (int a = 0; a < Mp; a++) for (int b = 0; b < a; b++) Gp.set(b, a, Gp.get(a, b));

        final CholeskyDecomposition_F64<DMatrixRMaj> chol = DecompositionFactory_DDRM.chol(true);
        if (!chol.decompose(Gp)) return Double.NaN;
        final DMatrixRMaj L = chol.getT(null);

        return traceInvFromCholeskyLower(L);
    }

    private static long cacheKey(int i, int[] parents, long knobsSig) {
        long h = 1469598103934665603L;
        h = (h ^ i) * 1099511628211L;
        for (int p : parents) h = (h ^ p) * 1099511628211L;
        h = (h ^ knobsSig) * 1099511628211L;
        return h;
    }

    private static double sumsq(double[] a) {
        double s = 0.0;
        for (double v : a) s += v * v;
        return s;
    }

    private static int[] concat(int i, int[] parents) {
        int[] all = new int[parents.length + 1];
        all[0] = i;
        System.arraycopy(parents, 0, all, 1, parents.length);
        return all;
    }

    /**
     * Computes the local score for a given node and its parent nodes based on the
     * log-likelihood, effective degrees of freedom, and penalty discount.
     *
     * @param i       Index of the current node for which the score is being calculated.
     * @param parents Indices of the parent nodes of the current node.
     * @return The computed local score as a double. Returns {@code Double.NaN} if
     * log-likelihood, effective degrees of freedom, or the number of data
     * points used are invalid.
     */
    @Override
    public double localScore(int i, int... parents) {
        LocalFit fit = localFit(i, parents);
        if (!Double.isFinite(fit.logLik) || !Double.isFinite(fit.edf) || fit.nUsed < 2) return Double.NaN;
        return fit.logLik - 0.5 * penaltyDiscount * fit.edf * TMath.log(TMath.max(2, fit.nUsed));
    }

    /**
     * Calculates the difference in local scores based on the given variables.
     * The method computes the difference between the local score of `y` given
     * the array `z` with `x` appended and the local score of `y` given only the array `z`.
     *
     * @param x the variable to be appended to the array `z` for the local score calculation
     * @param y the target variable for which the local scores are calculated
     * @param z an array of context variables for the local score calculation
     * @return the difference in local scores between `y | z, x` and `y | z`
     */
    @Override
    public double localScoreDiff(int x, int y, int[] z) {
        // standard definition: localScore(y | z, x) - localScore(y | z)
        return localScore(y, append(z, x)) - localScore(y, z);
    }

    /**
     * Retrieves a list of variables as Node objects.
     *
     * @return a new list containing the Node objects stored in the variables collection
     */
    @Override
    public List<Node> getVariables() {
        return new ArrayList<>(variables);
    }

    // -------------------- core scoring: localFit --------------------

    /**
     * Retrieves the sample size of the dataset used for scoring.
     *
     * @return the number of rows in the dataset
     */
    @Override
    public int getSampleSize() {
        return dataSet.getNumRows();
    }

    /**
     * Returns a string representation of the object.
     *
     * @return the string "Legendre BIC score"
     */
    @Override
    public String toString() {
        return "Legendre BIC score";
    }

    /**
     * Retrieves the current data model instance.
     *
     * @return the DataModel object representing the current state of the dataset
     */
    public DataModel getDataModel() {
        return dataSet;
    }

    /**
     * Returns the effective sample size, which represents the adjusted number
     * of observations in the dataset after accounting for factors such as
     * correlation or weighting.
     *
     * @return the effective sample size as an integer.
     */
    @Override
    public int getEffectiveSampleSize() {
        return nEff;
    }

    /**
     * Sets the effective sample size. If the provided sample size is negative,
     * the default sample size will be used instead.
     *
     * @param nEff the effective sample size to set; if negative, the default
     *             sample size will be used.
     */
    @Override
    public void setEffectiveSampleSize(int nEff) {
        this.nEff = (nEff < 0) ? this.sampleSize : nEff;
        resetCache();
    }

    /**
     * Sets the value of nu. The provided value must be finite and greater than 2.
     * Throws an IllegalArgumentException if the value does not meet these conditions.
     *
     * @param nu the new value for nu; must be a finite number greater than 2
     */
    public void setNu(double nu) {
        if (!(nu > 2) || !Double.isFinite(nu)) throw new IllegalArgumentException("nu must be finite and > 2");
        this.nu = nu;
        resetCache();
    }

    /**
     * Sets the scale value for this object. The scale determines the proportion or factor
     * by which certain properties or behaviors of the object are adjusted.
     * The scale value must be greater than 0 and finite.
     *
     * @param scale the new scale value to be set; must be greater than 0 and finite
     * @throws IllegalArgumentException if the scale value is not greater than 0 or is not a finite value
     */
    public void setScale(double scale) {
        if (!(scale > 0) || !Double.isFinite(scale)) throw new IllegalArgumentException("scale must be finite and > 0");
        this.scale = scale;
        resetCache();
    }

    /**
     * Sets the ridge parameter used in the computation. The ridge value must be a positive finite number.
     *
     * @param ridge the ridge parameter to set; must be greater than 0 and finite
     * @throws IllegalArgumentException if the ridge value is not greater than 0 or is not a finite number
     */
    public void setRidge(double ridge) {
        if (!(ridge > 0) || !Double.isFinite(ridge)) throw new IllegalArgumentException("ridge must be finite and > 0");
        this.ridge = ridge;
        resetCache();
    }

    /**
     * Sets the degree for the Legendre polynomial calculations.
     * The degree must be a positive integer (greater than or equal to 1).
     * If the provided value is invalid, an {@link IllegalArgumentException} is thrown.
     * Updates the cached results after setting a new degree.
     *
     * @param t The degree of the Legendre polynomial. Must be >= 1.
     * @throws IllegalArgumentException if t is less than 1.
     */
    public void setLegendreDegree(int t) {
        if (t < 1) throw new IllegalArgumentException("legendreDegree must be >= 1");
        this.legendreDegree = t;
        resetCache();
    }

    /**
     * Sets the Legendre clip value used to configure the computation or process.
     * The provided value must be finite and greater than 0.
     *
     * @param clip the positive, finite value to set as the Legendre clip
     * @throws IllegalArgumentException if the provided value is not finite or not greater than 0
     */
    public void setLegendreClip(double clip) {
        if (!(clip > 0) || !Double.isFinite(clip))
            throw new IllegalArgumentException("legendreClip must be finite and > 0");
        this.legendreClip = clip;
        resetCache();
    }

    /**
     * Sets the number of iterations for the IRLS (Iteratively Reweighted Least Squares) process.
     * Ensures the minimum number of iterations is 1. Updates internal state by resetting the cache.
     *
     * @param iters the desired number of IRLS iterations; if less than 1, it will be automatically set to 1.
     */
    public void setIrlsIters(int iters) {
        this.irlsIters = TMath.max(1, iters);
        resetCache();
    }

    /**
     * Sets the tolerance value used for the Iterative Reweighted Least Squares (IRLS) algorithm.
     * The provided value is constrained to be non-negative. If a negative value is passed, it
     * will be replaced with 0.0. Changing this value will reset any cached computations.
     *
     * @param tol the tolerance value for the IRLS algorithm; must be a non-negative number
     */
    public void setIrlsTol(double tol) {
        this.irlsTol = TMath.max(0.0, tol);
        resetCache();
    }

    /**
     * Sets the penalty discount value. This value must be a finite number greater than 0.
     * If the provided value does not meet the criteria, an IllegalArgumentException is thrown.
     *
     * @param penaltyDiscount the penalty discount to be applied; must be a finite value > 0
     * @throws IllegalArgumentException if the penaltyDiscount is not a finite value or is ≤ 0
     */
    public void setPenaltyDiscount(double penaltyDiscount) {
        if (!(penaltyDiscount > 0.0) || !Double.isFinite(penaltyDiscount))
            throw new IllegalArgumentException("Penalty discount must be finite and > 0");
        this.penaltyDiscount = penaltyDiscount;
        resetCache();
    }

    /**
     * Sets whether the system should use interactions and updates the internal state accordingly.
     *
     * @param useInteractions a boolean indicating if interaction usage should be enabled (true) or disabled (false)
     */
    public void setUseInteractions(boolean useInteractions) {
        this.useInteractions = useInteractions;
        resetCache();
    }

    /**
     * Sets the maximum number of parent interactions allowed for an entity.
     * This value determines the limit on parent relationships an entity can have.
     * If a negative value is provided, it will be treated as 0.
     *
     * @param k the number specifying the maximum parent interactions;
     *          must be a non-negative integer.
     */
    public void setInteractionMaxParents(int k) {
        this.interactionMaxParents = TMath.max(0, k);
        resetCache();
    }

    /**
     * Sets the minimum value of 'minN' and ensures it is not less than 2.
     * This method also clears any cached data by calling resetCache().
     *
     * @param minN the new minimum value to set. If the provided value is less than 2, it will default to 2.
     */
    public void setMinN(int minN) {
        this.minN = TMath.max(2, minN);
        resetCache();
    }

    /**
     * Configures the Legendre map mode for the system.
     * This method sets the mode of operation for the Legendre map
     * by using the specified string value and resets the associated cache.
     *
     * @param mode The string representation of the desired Legendre map mode.
     *             It must match one of the predefined enum values in LegendreMapMode.
     */
    public void setLegendreMapMode(String mode) {
        this.legendreMapMode = LegendreMapMode.valueOf(mode);
        resetCache();
    }

    /**
     * Sets the lower and upper quantile thresholds for mapping. The specified quantiles are used
     * to calculate the robust bounds for the data variables. This method validates the input quantiles
     * to ensure they satisfy the conditions: 0 <= loQ < hiQ <= 1.
     *
     * @param loQ The lower quantile threshold, a value between 0 (inclusive) and 1 (exclusive).
     * @param hiQ The upper quantile threshold, a value between 0 (exclusive) and 1 (inclusive),
     *            and greater than the specified loQ.
     * @throws IllegalArgumentException If the quantiles do not satisfy 0 <= loQ < hiQ <= 1.
     */
    public void setMapQuantiles(double loQ, double hiQ) {
        if (!(loQ >= 0 && loQ < hiQ && hiQ <= 1)) {
            throw new IllegalArgumentException("Quantiles must satisfy 0 <= loQ < hiQ <= 1");
        }
        this.mapLoQ = loQ;
        this.mapHiQ = hiQ;

        // recompute robust bounds (cheap enough)
        for (int j = 0; j < variables.size(); j++) {
            if (isDiscrete(j)) continue;
            double lo = quantileOfFinite(zCols[j], mapLoQ);
            double hi = quantileOfFinite(zCols[j], mapHiQ);
            if (Double.isFinite(lo) && Double.isFinite(hi) && hi > lo) {
                zQlo[j] = lo;
                zQhi[j] = hi;
            }
        }
        resetCache();
    }

    /**
     * Computes and returns a {@code LocalFit} object representing the fit of a model with the specified
     * child node and parent nodes. The fitting process may involve discrete or continuous variables
     * depending on the data, and includes error handling and fallback mechanisms.
     *
     * @param i       The index of the child variable for which the local fit is calculated.
     * @param parents The indices of parent variables that form the predictors for the child variable.
     *                The order of the parent indices will be sorted internally.
     * @return A {@code LocalFit} object containing log-likelihood, effective degrees of freedom,
     * and the number of valid rows used in the fitting process. If the fit is invalid or
     * fails, the returned object contains fallback values.
     */
    public LocalFit localFit(int i, int... parents) {
        Arrays.sort(parents);
        final long key = cacheKey(i, parents, knobsSignature());
        final ConcurrentHashMap<Long, LocalFit> cache = localFitCacheRef.get();

        return cache.computeIfAbsent(key, k -> {
            try {
                if (!(ridge > 0) || !Double.isFinite(ridge)) return new LocalFit(Double.NaN, Double.NaN, 0);

                final int[] all = concat(i, parents);
                final int[] rows = calculateRowSubsets ? validRows(all) : null;

                final int n = (rows == null) ? nEff : rows.length;

                // do not bail out with NaN just because n is small:
                // - if n < 2 it's hopeless
                // - if n < minN we use intercept-only (finite) as a fallback
                if (n < 2) return new LocalFit(Double.NaN, Double.NaN, n);

                if (isDiscrete(i)) {
                    final int K = numCategories(i);
                    if (K < 2) return new LocalFit(Double.NaN, Double.NaN, n);

                    final int[] y = extractDiscreteChild(i, rows, n);

                    if (parents.length == 0 || n < minN) {
                        double ll = multinomialInterceptOnlyLogLik(y, K);
                        double edf = (K - 1.0);
                        return new LocalFit(ll, edf, n);
                    }

                    FitResult fit = fitMultinomialLogitMixed(i, y, K, parents, rows, n);
                    if (!Double.isFinite(fit.logLik) || !Double.isFinite(fit.edf)) {
                        // fallback to intercept-only rather than NaN
                        double ll = multinomialInterceptOnlyLogLik(y, K);
                        double edf = (K - 1.0);
                        return new LocalFit(ll, edf, n);
                    }
                    return new LocalFit(fit.logLik, fit.edf, n);

                } else {
                    if (!(nu > 2) || !Double.isFinite(nu)) return new LocalFit(Double.NaN, Double.NaN, n);

                    final double[] y = extractContinuousChild(i, rows, n);
                    centerInPlace(y);

                    if (parents.length == 0 || n < minN) {
                        // intercept-only t model (mean 0 after centering)
                        double scaleHat = warmStartScale(y, n);
                        double ll0 = studentTLogLik(y, new double[n], nu, scaleHat);
                        return new LocalFit(ll0, 1.0, n);
                    }

                    FitResult fit = fitStudentTLegendreRidgeMixed(y, parents, rows, n);
                    if (!Double.isFinite(fit.logLik) || !Double.isFinite(fit.edf)) {
                        // fallback to intercept-only rather than NaN
                        double scaleHat = warmStartScale(y, n);
                        double ll0 = studentTLogLik(y, new double[n], nu, scaleHat);
                        return new LocalFit(ll0, 1.0, n);
                    }
                    return new LocalFit(fit.logLik, fit.edf, n);
                }

            } catch (RuntimeException e) {
                TetradLogger.getInstance().log(e.getMessage());
                return new LocalFit(Double.NaN, Double.NaN, 0);
            }
        });
    }

    private FitResult fitStudentTLegendreRidgeMixed(double[] yCentered,
                                                    int[] parentIdx,
                                                    int[] rows,
                                                    int n) {

        final int[] cont = filterContinuous(parentIdx);
        final int[] disc = filterDiscrete(parentIdx);
        final OneHotSpec oh = buildOneHotSpec(disc);

        final int t = legendreDegree;
        final int D = cont.length * t;

        final int kInt = (useInteractions ? TMath.min(cont.length, interactionMaxParents) : 0);
        final int I = (kInt >= 2) ? (kInt * (kInt - 1)) / 2 : 0;

        final int Q = oh.totalCols;
        final int M = 1 + D + I + Q;

        // Extract continuous parents mapped to [-1,1] into dense n x dCont
        final double[][] Xmap = new double[n][cont.length];
        for (int i = 0; i < n; i++) {
            final int row = (rows == null) ? i : rows[i];
            for (int j = 0; j < cont.length; j++) {
                final int var = cont[j];
                final double z = zCols[var][row];
                Xmap[i][j] = mapToLegendreDomain(var, z);
            }
        }

        // IRLS weights for Student-t
        final double[] w = new double[n];
        Arrays.fill(w, 1.0);

        // Warm-start scale to avoid pathological early weights
        double scaleHat = (Double.isFinite(scale) && scale > 0) ? scale : 1.0;
        // If user left default-ish scale, warm-start from y
        if (TMath.abs(scaleHat - 1.0) < 1e-12) scaleHat = warmStartScale(yCentered, n);

        double[] beta = new double[M];
        double prevObj = Double.POSITIVE_INFINITY;

        // Scratch buffers (reuse)
        final double[] xRow = new double[M];
        final double[] v = new double[M];

        for (int iter = 0; iter < irlsIters; iter++) {

            final DMatrixRMaj G = new DMatrixRMaj(M, M);
            Arrays.fill(v, 0.0);

            for (int i = 0; i < n; i++) {
                buildXRow_Intercept_Legendre(xRow, i, Xmap, cont.length, t, oh, disc, rows);
                applyFeatureScaleInPlace(xRow);   // <-- ADD THIS

                final double wi = w[i];
                final double yi = yCentered[i];

                for (int a = 0; a < M; a++) v[a] += wi * xRow[a] * yi;
                for (int a = 0; a < M; a++) {
                    final double pa = wi * xRow[a];
                    for (int b = 0; b <= a; b++) G.add(a, b, pa * xRow[b]);
                }
            }

            symmetrizeLowerToFull(G);
            addRidgeToDiagonal(G, ridge, /*skipIntercept=*/true);

            final CholeskyDecomposition_F64<DMatrixRMaj> chol = cholWithJitter(G, ridge, featureScale);
            if (chol == null) return new FitResult(Double.NaN, Double.NaN);
            final DMatrixRMaj L = chol.getT(null);

            beta = solveFromCholeskyLower(L, v);

            // Update weights + profiled scale
            double obj = 0.0;
            double wsum = 0.0;
            double wrss = 0.0;

            for (int i = 0; i < n; i++) {
                buildXRow_Intercept_Legendre(xRow, i, Xmap, cont.length, t, oh, disc, rows);
                applyFeatureScaleInPlace(xRow);

                double mu = 0.0;
                for (int a = 0; a < M; a++) mu += xRow[a] * beta[a];

                final double r = yCentered[i] - mu;

                final double u = r / scaleHat;
                final double u2 = u * u;

                final double wi = (nu + 1.0) / (nu + u2);
                w[i] = wi;

                obj += 0.5 * (nu + 1.0) * log1p(u2 / nu);

                wsum += wi;
                wrss += wi * r * r;
            }

            if (wsum > 0.0) {
                final double s2 = wrss / wsum;
                if (Double.isFinite(s2) && s2 > 0.0) scaleHat = sqrt(max(1e-12, s2));
            }

            if (abs(prevObj - obj) <= irlsTol * (1.0 + abs(prevObj))) break;
            prevObj = obj;
        }

        // Final predictions
        final double[] yhat = new double[n];
        for (int i = 0; i < n; i++) {
            buildXRow_Intercept_Legendre(xRow, i, Xmap, cont.length, legendreDegree, oh, disc, rows);
            applyFeatureScaleInPlace(xRow);

            double mu = 0.0;
            for (int a = 0; a < M; a++) mu += xRow[a] * beta[a];

            yhat[i] = mu;
        }

        final double ll = studentTLogLik(yCentered, yhat, nu, scaleHat);

        // EDF: approximate ridge smoother trace using last weights
        final DMatrixRMaj Gfinal = new DMatrixRMaj(M, M);
        for (int i = 0; i < n; i++) {
            buildXRow_Intercept_Legendre(xRow, i, Xmap, cont.length, legendreDegree, oh, disc, rows);
            applyFeatureScaleInPlace(xRow);

            final double wi = w[i];
            for (int a = 0; a < M; a++) {
                final double pa = wi * xRow[a];
                for (int b = 0; b <= a; b++) Gfinal.add(a, b, pa * xRow[b]);
            }
        }
        symmetrizeLowerToFull(Gfinal);
        addRidgeToDiagonal(Gfinal, ridge, /*skipIntercept=*/true);

        final double trInvPen = traceInvPenalizedBlockFromG(Gfinal);
        if (!Double.isFinite(trInvPen)) return new FitResult(Double.NaN, Double.NaN);

        final int Mp = M - 1;
        double ridgeEff = ridge * (featureScale * featureScale);
        double edf = 1.0 + (Mp - ridgeEff * trInvPen);
        if (!(edf >= 0) || !Double.isFinite(edf)) edf = 1.0 + Mp;

        return new FitResult(ll, edf);
    }

    // -------------------- Student-t likelihood + logGamma --------------------

    private FitResult fitMultinomialLogitMixed(int child,
                                               int[] y, int K,
                                               int[] parentIdx,
                                               int[] rows, int n) {

        final int[] cont = filterContinuous(parentIdx);
        final int[] disc = filterDiscrete(parentIdx);
        final OneHotSpec oh = buildOneHotSpec(disc);

        final int t = legendreDegree;
        final int D = cont.length * t;

        final int kInt = (useInteractions ? TMath.min(cont.length, interactionMaxParents) : 0);
        final int I = (kInt >= 2) ? (kInt * (kInt - 1)) / 2 : 0;

        final int Q = oh.totalCols;
        final int M = 1 + D + I + Q;

        final int C = K - 1;

        // Extract continuous parents mapped into [-1,1]
        final double[][] Xmap = new double[n][cont.length];
        for (int r = 0; r < n; r++) {
            final int row = (rows == null) ? r : rows[r];
            for (int j = 0; j < cont.length; j++) {
                final int var = cont[j];
                Xmap[r][j] = mapToLegendreDomain(var, zCols[var][row]);
            }
        }

        // Precompute Phi
        final double[][] Phi = new double[n][M];
        final double[] xRow = new double[M];

        for (int i = 0; i < n; i++) {
            buildXRow_Intercept_Legendre(xRow, i, Xmap, cont.length, t, oh, disc, rows);
            applyFeatureScaleInPlace(xRow);
            System.arraycopy(xRow, 0, Phi[i], 0, M);
        }

        // beta: M x C
        final double[][] beta = new double[M][C];

        double prevObj = Double.POSITIVE_INFINITY;

        // Scratch
        final double[] logits = new double[K];
        final double[][] probs = new double[n][K];

        for (int iter = 0; iter < irlsIters; iter++) {

            fillSoftmaxProbsFromPhi(K, n, beta, Phi, logits, probs);

            for (int c = 0; c < C; c++) {
                final DMatrixRMaj G = new DMatrixRMaj(M, M);
                final double[] v = new double[M];

                for (int i = 0; i < n; i++) {
                    final double[] phi = Phi[i];

                    final double pc = probs[i][c + 1];
                    double wc = pc * (1.0 - pc);
                    wc = TMath.max(wc, 1e-10);

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

                symmetrizeLowerToFull(G);
                addRidgeToDiagonal(G, ridge, /*skipIntercept=*/true);

                final CholeskyDecomposition_F64<DMatrixRMaj> chol = cholWithJitter(G, ridge, featureScale);
                if (chol == null) return new FitResult(Double.NaN, Double.NaN);
                final DMatrixRMaj L = chol.getT(null);

                final double[] bc = solveFromCholeskyLower(L, v);
                for (int a = 0; a < M; a++) beta[a][c] = bc[a];
            }

            final double llIter = multinomialLogLikFromPhi(y, K, n, beta, Phi, logits);
            final double obj = -llIter;

            if (abs(prevObj - obj) <= irlsTol * (1.0 + abs(prevObj))) break;
            prevObj = obj;
        }

        final double ll = multinomialLogLikFromPhi(y, K, n, beta, Phi, logits);

        // EDF approximation (sum over C one-vs-baseline fits)
        fillSoftmaxProbsFromPhi(K, n, beta, Phi, logits, probs);

        double edf = 0.0;
        final int Mp = M - 1;

        for (int c = 0; c < C; c++) {
            final DMatrixRMaj G = new DMatrixRMaj(M, M);

            for (int i = 0; i < n; i++) {
                final double[] phi = Phi[i];

                final double pc = probs[i][c + 1];
                double wc = pc * (1.0 - pc);
                wc = TMath.max(wc, 1e-10);

                for (int a = 0; a < M; a++) {
                    final double pa = wc * phi[a];
                    for (int b = 0; b <= a; b++) G.add(a, b, pa * phi[b]);
                }
            }

            symmetrizeLowerToFull(G);
            addRidgeToDiagonal(G, ridge, /*skipIntercept=*/true);

            double trInvPen = traceInvPenalizedBlockFromG(G);
            if (!Double.isFinite(trInvPen)) return new FitResult(Double.NaN, Double.NaN);

            double edfC = 1.0 + (Mp - ridge * trInvPen);
            if (!(edfC >= 0) || !Double.isFinite(edfC)) edfC = 1.0 + Mp;

            edf += edfC;
        }

        return new FitResult(ll, edf);
    }

    /**
     * Sets the scaling factor to be applied to features in computations.
     * The provided value must be a positive, finite number.
     *
     * @param s the scaling factor for features; must be greater than 0 and finite
     * @throws IllegalArgumentException if the provided scaling factor is not greater than 0 or is not finite
     */
    public void setFeatureScale(double s) {
        if (!(s > 0.0) || !Double.isFinite(s))
            throw new IllegalArgumentException("featureScale must be finite and > 0");
        this.featureScale = s;
        resetCache();
    }

    /**
     * Design row:
     * - intercept
     * - Legendre block: for each continuous parent j, P1..Pt of mapped value in [-1,1]
     * - (optional) interaction block: x_a * x_b for first-degree mapped values
     * - one-hot block for discrete parents (baseline dropped)
     */
    private void buildXRow_Intercept_Legendre(double[] out,
                                              int i,
                                              double[][] Xmap, // n x dCont values already in [-1,1]
                                              int dCont,
                                              int t,
                                              OneHotSpec oh,
                                              int[] discParents,
                                              int[] rows) {

        out[0] = 1.0;
        Arrays.fill(out, 1, out.length, 0.0);

        final int legOff = 1;

        final int kInt = (useInteractions ? TMath.min(dCont, interactionMaxParents) : 0);
        final int nInt = (kInt >= 2) ? (kInt * (kInt - 1)) / 2 : 0;

        final int intOff = legOff + dCont * t;
        final int ohOff = intOff + nInt;

        // need x's for interaction block
        final double[] xInt = (kInt > 0) ? new double[kInt] : null;

        int pos = legOff;
        for (int j = 0; j < dCont; j++) {
            double x = Xmap[i][j];
            if (Double.isNaN(x)) x = 0.0; // should not occur if validRows filtered, but safe

            if (j < kInt) xInt[j] = x;

            // P0 = 1, P1 = x
            double Pnm2 = 1.0;
            double Pnm1 = x;

            for (int deg = 1; deg <= t; deg++) {
                final double Pd;
                if (deg == 1) {
                    Pd = Pnm1;
                } else {
                    Pd = ((2.0 * deg - 1.0) * x * Pnm1 - (deg - 1.0) * Pnm2) / deg;
                    Pnm2 = Pnm1;
                    Pnm1 = Pd;
                }
                out[pos++] = Pd;
            }
        }

        if (nInt > 0) {
            int ipos = intOff;
            for (int a = 0; a < kInt; a++) {
                final double xa = xInt[a];
                for (int b = 0; b < a; b++) {
                    out[ipos++] = xa * xInt[b];
                }
            }
        }

        if (discParents.length == 0) return;

        final int row = (rows == null) ? i : rows[i];
        for (int parentPos = 0; parentPos < discParents.length; parentPos++) {
            final int var = discParents[parentPos];
            final int lev = dataSet.getInt(row, var);
            if (lev == DiscreteVariable.MISSING_VALUE) continue;

            // baseline is 0 -> dropped
            if (lev <= 0) continue;

            final int local = lev - 1;
            final int size = oh.sizes[parentPos];
            if (size <= 1) continue;

            // columns are 0..(K-2) for levels 1..(K-1)
            if (local >= (size - 1)) continue;

            final int col = oh.offsets[parentPos] + local;
            out[ohOff + col] = 1.0;
        }
    }

    private void applyFeatureScaleInPlace(double[] xRow) {
        final double s = this.featureScale;
        if (s == 1.0) return;
        for (int a = 1; a < xRow.length; a++) xRow[a] *= s; // keep intercept unscaled
    }

    // -------------------- linear algebra helpers --------------------

    private int[] validRows(int[] vars) {
        int[] tmp = new int[sampleSize];
        int m = 0;

        outer:
        for (int r = 0; r < sampleSize; r++) {
            for (int v : vars) {
                if (isDiscrete(v)) {
                    int val = dataSet.getInt(r, v);
                    if (val == DiscreteVariable.MISSING_VALUE) continue outer;
                } else {
                    double val = zCols[v][r];
                    if (Double.isNaN(val)) continue outer;
                }
            }
            tmp[m++] = r;
        }
        return Arrays.copyOf(tmp, m);
    }

    private double[] extractContinuousChild(int varIndex, int[] rows, int n) {
        double[] y = new double[n];
        if (rows == null) {
            for (int r = 0; r < n; r++) y[r] = zCols[varIndex][r];
        } else {
            for (int r = 0; r < n; r++) y[r] = zCols[varIndex][rows[r]];
        }
        return y;
    }

    private int[] extractDiscreteChild(int varIndex, int[] rows, int n) {
        int[] y = new int[n];
        if (rows == null) {
            for (int r = 0; r < n; r++) y[r] = dataSet.getInt(r, varIndex);
        } else {
            for (int r = 0; r < n; r++) y[r] = dataSet.getInt(rows[r], varIndex);
        }
        return y;
    }

    private boolean isDiscrete(int col) {
        return variables.get(col) instanceof DiscreteVariable;
    }

    private int numCategories(int varIndex) {
        Node v = variables.get(varIndex);
        if (!(v instanceof DiscreteVariable dv)) return 0;
        return dv.getNumCategories();
    }

    private int[] filterContinuous(int[] cols) {
        int c = 0;
        for (int v : cols) if (!isDiscrete(v)) c++;
        int[] out = new int[c];
        int k = 0;
        for (int v : cols) if (!isDiscrete(v)) out[k++] = v;
        return out;
    }

    // -------------------- one-hot spec --------------------

    private int[] filterDiscrete(int[] cols) {
        int c = 0;
        for (int v : cols) if (isDiscrete(v)) c++;
        int[] out = new int[c];
        int k = 0;
        for (int v : cols) if (isDiscrete(v)) out[k++] = v;
        return out;
    }

    private double mapToLegendreDomain(int varIndex, double z) {
        if (Double.isNaN(z)) return Double.NaN;

        switch (legendreMapMode) {
            case MINMAX_Z: {
                final double lo = zMin[varIndex];
                final double hi = zMax[varIndex];
                if (!Double.isFinite(lo) || !Double.isFinite(hi) || !(hi > lo)) {
                    return clamp(z / legendreClip);
                }
                double x = (2.0 * (z - lo) / (hi - lo)) - 1.0;
                return clamp(x);
            }
            case ROBUST_MINMAX_Z: {
                final double lo = zQlo[varIndex];
                final double hi = zQhi[varIndex];
                if (!Double.isFinite(lo) || !Double.isFinite(hi) || !(hi > lo)) {
                    // fall back to raw min/max then clip
                    final double lo2 = zMin[varIndex];
                    final double hi2 = zMax[varIndex];
                    if (Double.isFinite(lo2) && Double.isFinite(hi2) && hi2 > lo2) {
                        double x = (2.0 * (z - lo2) / (hi2 - lo2)) - 1.0;
                        return clamp(x);
                    }
                    return clamp(z / legendreClip);
                }
                double x = (2.0 * (z - lo) / (hi - lo)) - 1.0;
                return clamp(x);
            }
            case SCALE_DOWN: {
                final double lo = zQlo[varIndex];
                final double hi = zQhi[varIndex];
                final double m = TMath.max(TMath.abs(lo), TMath.abs(hi));
                if (!(m > 0.0) || !Double.isFinite(m)) {
                    return clamp(z / legendreClip);
                }
                return clamp(z / m);
            }
            case CLIP_Z:
            default:
                return clamp(z / legendreClip);
        }
    }

    // -------------------- cache + hashing --------------------

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
            off += TMath.max(0, K - 1);
        }
        return new OneHotSpec(sizes, offsets, off);
    }

    private void resetCache() {
        localFitCacheRef.set(new ConcurrentHashMap<>());
    }

    private long knobsSignature() {
        long h = 1469598103934665603L;
        h = (h ^ Double.doubleToLongBits(nu)) * 1099511628211L;
        h = (h ^ Double.doubleToLongBits(scale)) * 1099511628211L;
        h = (h ^ Double.doubleToLongBits(ridge)) * 1099511628211L;
        h = (h ^ legendreDegree) * 1099511628211L;
        h = (h ^ Double.doubleToLongBits(legendreClip)) * 1099511628211L;
        h = (h ^ irlsIters) * 1099511628211L;
        h = (h ^ Double.doubleToLongBits(irlsTol)) * 1099511628211L;
        h = (h ^ nEff) * 1099511628211L;
        h = (h ^ (useInteractions ? 1L : 0L)) * 1099511628211L;
        h = (h ^ interactionMaxParents) * 1099511628211L;
        h = (h ^ Double.doubleToLongBits(penaltyDiscount)) * 1099511628211L;
        h = (h ^ legendreMapMode.ordinal()) * 1099511628211L;
        h = (h ^ Double.doubleToLongBits(mapLoQ)) * 1099511628211L;
        h = (h ^ Double.doubleToLongBits(mapHiQ)) * 1099511628211L;
        h = (h ^ minN) * 1099511628211L;
        return h;
    }

    /**
     * Performs a local fit on rows for a given child node based on its parent nodes
     * and data rows. Handles both discrete and continuous children, applying
     * different fitting methods depending on the type of the child and the input parameters.
     *
     * @param child   The index of the child node for which the local fit is computed.
     * @param parents An array of indices representing the parent nodes of the child node.
     *                These indices are expected to be sorted internally within the method.
     * @param rows    An array of row indices specifying the subset of data to be used for
     *                fitting. If null, the full effective number of rows (nEff) is used.
     * @return A LocalFit object containing the log-likelihood of the fit, the effective
     * degrees of freedom (edf), and the number of data points (n) used in the fit.
     * Returns NaN values in the LocalFit object if certain criteria are not met
     * (e.g., insufficient data points, invalid parameters).
     */
    public LocalFit localFitOnRows(int child, int[] parents, int[] rows) {
        Arrays.sort(parents);

        try {
            if (!(ridge > 0) || !Double.isFinite(ridge)) {
                return new LocalFit(Double.NaN, Double.NaN, 0);
            }

            final int n = (rows == null) ? nEff : rows.length;
            if (n < 2) {
                return new LocalFit(Double.NaN, Double.NaN, n);
            }

            if (isDiscrete(child)) {
                final int K = numCategories(child);
                if (K < 2) return new LocalFit(Double.NaN, Double.NaN, n);

                final int[] y = extractDiscreteChild(child, rows, n);

                // For LRT stability: always return a finite likelihood if possible.
                if (parents.length == 0 || n < 5) {
                    double ll = multinomialInterceptOnlyLogLik(y, K);
                    double edf = (K - 1.0);
                    return new LocalFit(ll, edf, n);
                }

                FitResult fit = fitMultinomialLogitMixed(child, y, K, parents, rows, n);
                if (!Double.isFinite(fit.logLik()) || !Double.isFinite(fit.edf())) {
                    // fallback to intercept-only (finite)
                    double ll = multinomialInterceptOnlyLogLik(y, K);
                    double edf = (K - 1.0);
                    return new LocalFit(ll, edf, n);
                }
                return new LocalFit(fit.logLik(), fit.edf(), n);

            } else {
                if (!(nu > 2) || !Double.isFinite(nu)) return new LocalFit(Double.NaN, Double.NaN, n);

                double[] y = extractContinuousChild(child, rows, n);

                // IMPORTANT: make this consistent with localFit(): center y in-place
                centerInPlace(y);

                if (parents.length == 0 || n < 5) {
                    // intercept-only Student-t with profiled scale
                    double scaleHat = this.scale;

                    // robust warm start from y if scale is generic/default-ish
                    double s2 = 0.0;
                    for (double v : y) s2 += v * v;
                    double rms = TMath.sqrt(TMath.max(1e-12, s2 / TMath.max(1, n)));
                    if (TMath.abs(scaleHat - 1.0) < 1e-12) scaleHat = rms;

                    // a couple of t-weighted updates for scale
                    for (int it = 0; it < 2; it++) {
                        double wsum = 0.0, wrss = 0.0;
                        double invS2 = 1.0 / (scaleHat * scaleHat);
                        for (int r = 0; r < n; r++) {
                            double u2 = (y[r] * y[r]) * invS2;
                            double w = (nu + 1.0) / (nu + u2);
                            wsum += w;
                            wrss += w * y[r] * y[r];
                        }
                        if (wsum > 0.0) scaleHat = TMath.sqrt(TMath.max(1e-12, wrss / wsum));
                    }

                    double ll0 = studentTLogLik(y, new double[n], nu, scaleHat);
                    return new LocalFit(ll0, 1.0, n);
                }

                FitResult fit = fitStudentTLegendreRidgeMixed(y, parents, rows, n);
                if (!Double.isFinite(fit.logLik()) || !Double.isFinite(fit.edf())) {
                    // fallback to intercept-only (finite)
                    double scaleHat = TMath.sqrt(TMath.max(1e-12, sumsq(y) / TMath.max(1, n)));
                    double ll0 = studentTLogLik(y, new double[n], nu, scaleHat);
                    return new LocalFit(ll0, 1.0, n);
                }
                return new LocalFit(fit.logLik(), fit.edf(), n);
            }

        } catch (RuntimeException e) {
            TetradLogger.getInstance().log(e.getMessage());
            return new LocalFit(Double.NaN, Double.NaN, 0);
        }
    }

    /**
     * Determines the valid rows for a union operation based on the given child
     * and parent arrays. The child and parent values are combined, sorted, and
     * processed to compute the valid rows.
     *
     * @param child   the value representing the child element in the union operation
     * @param parents an array of values representing the parent elements in the union operation
     * @return an array of integers representing the valid rows for the union operation,
     * or null if row subsets calculation is disabled
     */
    public int[] validRowsForUnion(int child, int[] parents) {
        int[] vars = new int[parents.length + 1];
        vars[0] = child;
        System.arraycopy(parents, 0, vars, 1, parents.length);
        Arrays.sort(vars); // <-- add this
        return calculateRowSubsets ? validRows(vars) : null;
    }

    /**
     * Appends a given integer to the end of an array, creating a new array.
     *
     * @param z the original array to which the integer will be appended
     * @param x the integer value to be appended to the array
     * @return a new array containing all the elements of the original array,
     * followed by the appended integer
     */
    public int[] append(int[] z, int x) {
        int[] out = Arrays.copyOf(z, z.length + 1);
        out[z.length] = x;
        return out;
    }

    private enum LegendreMapMode {
        CLIP_Z,            // x = clamp(z/clip)
        ROBUST_MINMAX_Z,   // x = rescale z to [-1,1] using per-variable quantile range [qLo,qHi]
        MINMAX_Z,           // x = rescale z to [-1,1] using per-variable raw min/max
        SCALE_DOWN
    }

    private static final class OneHotSpec {
        final int[] sizes;
        final int[] offsets;
        final int totalCols;

        OneHotSpec(int[] sizes, int[] offsets, int totalCols) {
            this.sizes = sizes;
            this.offsets = offsets;
            this.totalCols = totalCols;
        }
    }

    // -------------------- records --------------------

    /**
     * Represents the result of a local fit in a statistical model.
     * Used to capture the fit's log-likelihood, effective degrees of freedom,
     * and the number of data points utilized in the fitting process.
     *
     * @param logLik The log-likelihood of the fit, representing goodness of fit.
     * @param edf    The effective degrees of freedom used in the model.
     * @param nUsed  The number of data points used in the fitting process.
     */
    public record LocalFit(double logLik, double edf, int nUsed) {
    }

    /**
     * A record representing the result of a model fitting process.
     *
     * @param logLik The log-likelihood value of the fitted model.
     * @param edf    The effective degrees of freedom used in the model.
     */
    private record FitResult(double logLik, double edf) {
    }
}