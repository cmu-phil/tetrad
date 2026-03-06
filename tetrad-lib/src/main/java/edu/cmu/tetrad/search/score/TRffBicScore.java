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

import java.io.Serial;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static edu.cmu.tetrad.util.TMath.*;

/**
 * <p><b>T-RFF BIC score (mixed)</b></p>
 *
 * <p>
 * Local Bayesian Information Criterion (BIC)–style score for structure learning with
 * mixed continuous and discrete variables. The score combines nonlinear mean modeling
 * via Random Fourier Features (RFF) with Student-t likelihoods for robustness to
 * heavy-tailed residuals, while remaining computationally stable and fully local.
 * </p>
 *
 * <p><b>Local conditional models.</b>
 * For each candidate parent set Pa(Y), the conditional distribution of Y is modeled as:
 * </p>
 * <ul>
 *   <li><b>Continuous child Y</b>:
 *     Student-t location model of the form Y = f(Pa) + ε.
 *     Continuous parents enter through RFF; discrete parents enter via one-hot encoding.
 *     Parameters are estimated by iteratively reweighted ridge regression (IRLS),
 *     yielding robustness to heavy-tailed residuals.</li>
 *   <li><b>Discrete child Y</b>:
 *     Multinomial logistic (softmax) regression with ridge regularization.
 *     Continuous parents are represented via RFF; discrete parents via one-hot encoding.
 *     Fitting is performed using IRLS.</li>
 * </ul>
 *
 * <p><b>Score definition.</b>
 * The local score takes a generalized BIC form
 * </p>
 * <pre>
 *   score(Y | Pa(Y)) = logLik_hat − 0.5 · edf · log(n),
 * </pre>
 * where {@code logLik_hat} is the maximized penalized log-likelihood,
 * {@code n} is the effective sample size for the local family, and {@code edf}
 * is the ridge-based effective degrees of freedom.
 *
 * <p>
 * For multinomial logistic models, the effective degrees of freedom are approximated
 * by summing the ridge edf contributions of the {@code K − 1} one-vs-reference
 * logistic blocks using the final IRLS weights.
 * </p>
 *
 * <p><b>Student-t robustness.</b>
 * The Student-t likelihood induces residual-dependent reweighting that downweights
 * extreme observations, reducing sensitivity to outliers relative to Gaussian BIC
 * variants while preserving a location-model structure.
 * </p>
 *
 * <p><b>Missing data handling.</b>
 * Rows with missing values in the local family {@code {Y} ∪ Pa(Y)} are excluded
 * on a per-score basis (testwise deletion).
 * </p>
 *
 * <p><b>Intended use.</b>
 * Intended for robust causal structure learning in mixed-type data when nonlinear
 * mean effects are expected and residual distributions may be heavy-tailed.
 * </p>
 */
public final class TRffBicScore implements Score, EffectiveSampleSizeSettable {
    /**
     * If true, compute row subsets when missing exists.
     */
    private final boolean calculateRowSubsets;
    /**
     * Represents an immutable instance of the DataSet being used in the application.
     * This variable is declared as {@code final}, indicating that its reference cannot be changed
     * once assigned. It encapsulates and manages a structured set of data, typically used for processing,
     * analysis, or storage within the application's domain.
     */
    private final DataSet dataSet;
    /**
     * A list that holds Node instances representing variables.
     * This list is immutable and cannot be modified after initialization.
     */
    private final List<Node> variables;
    /**
     * Represents the size of the sample to be used in a specific context.
     * This value is immutable and initialized at the time of object creation.
     * It typically denotes the number of elements or observations
     * considered for processing or analysis in a dataset or operation.
     */
    private final int sampleSize;
    /**
     * Continuous columns z-scored globally, NaNs preserved. For discrete vars, column is all NaN.
     */
    private final double[][] zCols;
    /**
     * Cache key -> score.
     */
    private transient AtomicReference<ConcurrentHashMap<Long, Double>> localScoreCacheRef =
            new AtomicReference<>(new ConcurrentHashMap<>());
    /**
     * A transient concurrent map used to cache omega values associated with a unique identifier.
     * The map's keys are of type Long, representing unique IDs, and the values are arrays of doubles
     * containing precomputed omega-related data.
     *
     * This cache is designed to improve performance by avoiding repeated calculations of omega-related
     * data during runtime. Due to its transient nature, the cache will not be serialized if the containing
     * object is serialized.
     */
    private transient ConcurrentHashMap<Long, double[]> omegaCache = new ConcurrentHashMap<>();
    /**
     * A transient cache that maps an integer key to a double array, intended
     * for storing precomputed phase values or related data. The use of a
     * {@code ConcurrentHashMap} ensures thread-safe access in concurrent
     * environments.
     *
     * Being marked as transient, this variable will not be serialized during
     * the object's serialization process.
     */
    private transient ConcurrentHashMap<Integer, double[]> phaseCache = new ConcurrentHashMap<>();
    /**
     * Student-t degrees of freedom (continuous child).
     */
    private volatile double nu = 5.0;
    /**
     * Student-t scale (continuous child). If globally z-scored, 1.0 is reasonable.
     */
    private volatile double scale = 1.0;
    /**
     * Ridge penalty (>0). Applies to both continuous and discrete child fits.
     */
    private volatile double ridge = 1e-3;
    /**
     * Number of RFF features (D) for continuous-parent subvector.
     */
    private volatile int rffFeatures = 256;
    /**
     * RFF sigma (lengthscale-ish). Frequencies ~ N(0, 1/sigma^2).
     */
    private volatile double rffSigma = 1.0;
    /**
     * Deterministic base seed for features.
     */
    private volatile long rffSeed = 1L;
    /**
     * IRLS iterations (both models).
     */
    private volatile int irlsIters = 8;
    /**
     * IRLS stopping tolerance.
     */
    private volatile double irlsTol = 1e-6;
    /**
     * Effective sample size (n_eff).
     */
    private volatile int nEff;
    /**
     * Penalty discount factor for BIC score.
     */
    private double penaltyDiscount = 1.0;

    /**
     * Constructs an instance of MinimaxTRffBicScore using the provided dataset.
     * This constructor initializes various internal fields, processes the dataset to evaluate
     * the presence of missing values, and computes scaled and z-scored versions of continuous
     * variables.
     *
     * @param dataSet The dataset to be used for constructing this instance.
     *                Must be non-null. If null, a {@code NullPointerException} will be thrown.
     */
    public TRffBicScore(DataSet dataSet) {
        if (dataSet == null) throw new NullPointerException("dataSet");

        this.dataSet = dataSet;
        this.variables = new ArrayList<>(dataSet.getVariables());
        this.sampleSize = dataSet.getNumRows();
        setEffectiveSampleSize(-1);

        initCaches();

        this.calculateRowSubsets = dataSet.existsMissingValue();

        int p = variables.size();
        double[][] raw = new double[p][sampleSize];
        for (int j = 0; j < p; j++) {
            if (isDiscrete(j)) {
                // fill with NaN sentinel in zCols; we will read discrete via getInt
                Arrays.fill(raw[j], Double.NaN);
            } else {
                for (int r = 0; r < sampleSize; r++) raw[j][r] = dataSet.getDouble(r, j);
            }
        }

        this.zCols = new double[p][sampleSize];
        for (int j = 0; j < p; j++) {
            if (isDiscrete(j)) {
                Arrays.fill(zCols[j], Double.NaN);
            } else {
                zscoreColumnPreserveNaN(raw[j], zCols[j]);
            }
        }
    }

    private void initCaches() {
        localScoreCacheRef = new AtomicReference<>(new ConcurrentHashMap<>());
        omegaCache = new ConcurrentHashMap<>();
        phaseCache = new ConcurrentHashMap<>();
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

    private static int[] concat(int i, int[] parents) {
        int[] all = new int[parents.length + 1];
        all[0] = i;
        System.arraycopy(parents, 0, all, 1, parents.length);
        return all;
    }

    // -------------------- public knobs --------------------

    private static long cacheKey(int i, int[] parents, long knobsSig) {
        long h = 1469598103934665603L;
        h = (h ^ i) * 1099511628211L;
        for (int p : parents) h = (h ^ p) * 1099511628211L;
        h = (h ^ knobsSig) * 1099511628211L;
        return h;
    }

    // 64-bit mix for stable keys/seeds.
    private static long mix64(long z) {
        z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
        z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return z ^ (z >>> 33);
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
                final double e = TMath.exp(logitsScratch[k] - maxLog);
                outProbs[i][k] = e;
                sum += e;
            }

            final double inv = 1.0 / sum;
            for (int k = 0; k < K; k++) outProbs[i][k] *= inv;
        }
    }

    private static double[][] softmaxProbsFromPhi(int[] y, int K, int n,
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
                final double e = TMath.exp(logitsScratch[k] - maxLog);
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
            for (int k = 0; k < K; k++) sum += TMath.exp(logitsScratch[k] - maxLog);

            ll += (logitsScratch[y[i]] - maxLog) - TMath.log(sum);
        }

        return ll;
    }

    private static double traceInvPenalizedBlockFromG(DMatrixRMaj Gfull) {
        // Gfull is MxM. Intercept is at 0 and is NOT ridge-penalized.
        // We want trace(inv(Gpen)), where Gpen is (M-1)x(M-1) block on indices 1..M-1.
        final int M = Gfull.numRows;
        if (M <= 1) return 0.0;

        final int Mp = M - 1;
        final DMatrixRMaj Gp = new DMatrixRMaj(Mp, Mp);

        for (int a = 0; a < Mp; a++) {
            for (int b = 0; b <= a; b++) {
                Gp.set(a, b, Gfull.get(a + 1, b + 1));
            }
        }
        // mirror
        for (int a = 0; a < Mp; a++) for (int b = 0; b < a; b++) Gp.set(b, a, Gp.get(a, b));

        final CholeskyDecomposition_F64<DMatrixRMaj> chol = DecompositionFactory_DDRM.chol(true);
        if (!chol.decompose(Gp)) return Double.NaN;
        final DMatrixRMaj L = chol.getT(null);

        return traceInvFromCholeskyLower(L);
    }

    private long omegaKey(int child, int parent) {
        // pack child+parent into a stable key
        return (((long) child) << 32) ^ (parent & 0xffffffffL);
    }

    private double[] getOmega(int child, int parent, int D) {
        // omega_k ~ N(0, 1/sigma^2). Return length D.
        long key = omegaKey(child, parent);
        return omegaCache.computeIfAbsent(key, kk -> {
            SplittableRandom rng = new SplittableRandom(mix64(rffSeed ^ kk));
            double[] w = new double[D];
            double invSigma = 1.0 / rffSigma;
            for (int k = 0; k < D; k++) w[k] = rng.nextGaussian() * invSigma;
            return w;
        });
    }

    private double[] getPhase(int child, int D) {
        return phaseCache.computeIfAbsent(child, cc -> {
            SplittableRandom rng = new SplittableRandom(mix64(rffSeed ^ (long) cc * 0x9E3779B97F4A7C15L));
            double[] phase = new double[D];
            for (int k = 0; k < D; k++) phase[k] = 2.0 * TMath.PI * rng.nextDouble();
            return phase;
        });
    }

    /**
     * Computes the local score for a given variable and its parents based on specific scoring criteria.
     *
     * @param i       The index of the target variable for which the local score is being computed.
     * @param parents An array representing the indices of the parent variables of the target variable.
     *                This array may be empty if the target variable has no parents.
     * @return The computed local score for the given variable and its parents. Returns
     * {@code Double.NaN} if the computation is invalid or cannot be performed.
     */
    @Override
    public double localScore(int i, int... parents) {
        Arrays.sort(parents);
        long key = cacheKey(i, parents, knobsSignature());

        final ConcurrentHashMap<Long, Double> cache = localScoreCacheRef.get();
        return cache.computeIfAbsent(key, k -> {
            try {
                if (!(ridge > 0) || !Double.isFinite(ridge)) return Double.NaN;

                int[] all = concat(i, parents);
                int[] rows = calculateRowSubsets ? validRows(all) : null;

                int n = (rows == null) ? nEff : rows.length;
                if (n < 10) return Double.NaN;

                // child type dispatch
                if (isDiscrete(i)) {
                    // -------- discrete child: multinomial logistic ridge --------
                    int[] y = extractDiscreteChild(i, rows, n);
                    int K = numCategories(i);
                    if (K < 2) return Double.NaN;

                    if (parents.length == 0) {
                        // intercept-only multinomial (just class proportions)
                        double ll = multinomialInterceptOnlyLogLik(y, K);
                        // edf = K-1 intercepts
                        double bic = ll - 0.5 * (K - 1.0) * log(n);
                        return bic;
                    }

                    long seed = rffSeed ^ (long) i * 0x9E3779B97F4A7C15L ^ Arrays.hashCode(parents);

                    FitResult fit = fitMultinomialLogitMixed(i, y, K, parents, rows, n);
                    if (!Double.isFinite(fit.logLik)) return Double.NaN;

                    return fit.logLik - 0.5 * penaltyDiscount * fit.edf * log(n);

                } else {
                    // -------- continuous child: Student-t RFF ridge (+ one-hot discrete parents) --------
                    if (!(nu > 2) || !Double.isFinite(nu)) return Double.NaN;
                    if (!(scale > 0) || !Double.isFinite(scale)) return Double.NaN;

                    double[] y = extractContinuousChild(i, rows, n);
                    centerInPlace(y);

                    if (parents.length == 0) {
                        // y is already centered in your code.
                        double scaleHat = profileStudentTScale(y, nu, this.scale, irlsIters, irlsTol);
                        double ll = studentTLogLik(y, new double[n], nu, scaleHat);
                        return ll; // edf = 0 after centering, as you already intend
                    }

                    long seed = rffSeed ^ (long) i * 0x9E3779B97F4A7C15L ^ Arrays.hashCode(parents);

                    FitResult fit = fitStudentTRffRidgeMixed(i, y, parents, rows, n);
                    if (!Double.isFinite(fit.logLik)) return Double.NaN;

                    return fit.logLik - 0.5 * penaltyDiscount * fit.edf * log(n);
                }

            } catch (RuntimeException e) {
                TetradLogger.getInstance().log(e.getMessage());
                return Double.NaN;
            }
        });
    }

    private static double profileStudentTScale(double[] yCentered,
                                               double nu,
                                               double initScale,
                                               int maxIters,
                                               double tol) {
        final int n = yCentered.length;
        double scaleHat = initScale;
        double prevObj = Double.POSITIVE_INFINITY;

        for (int iter = 0; iter < maxIters; iter++) {
            double obj = 0.0;
            double wsum = 0.0;
            double wrss = 0.0;

            for (int i = 0; i < n; i++) {
                double r = yCentered[i]; // intercept-only => yhat=0 after centering
                double u2 = (r / scaleHat) * (r / scaleHat);

                double wi = (nu + 1.0) / (nu + u2);

                obj += 0.5 * (nu + 1.0) * TMath.log1p(u2 / nu);
                wsum += wi;
                wrss += wi * r * r;
            }

            if (wsum > 0.0) {
                double s2 = wrss / wsum;
                scaleHat = TMath.sqrt(TMath.max(1e-12, s2));
            }

            if (TMath.abs(prevObj - obj) <= tol * (1.0 + TMath.abs(prevObj))) break;
            prevObj = obj;
        }

        return scaleHat;
    }

    /**
     * Computes the difference in local scores when a variable is added to the set of parent variables
     * for a target variable. The method evaluates how the local score of a target variable changes
     * by appending a given variable to its parent set.
     *
     * @param x The index of the variable being added to the parent set of the target variable.
     * @param y The index of the target variable for which the score difference is being computed.
     * @param z An array representing the indices of the parent variables of the target variable before the addition of {@code x}.
     * @return The difference in local scores after adding {@code x} to the parent set of {@code y}.
     * Returns {@code Double.NaN} if the computation is invalid or cannot be performed.
     */
    @Override
    public double localScoreDiff(int x, int y, int[] z) {
        return localScore(y, append(z, x)) - localScore(y, z);
    }

    /**
     * Retrieves a list of nodes representing the variables used in the context
     * of this scoring method. The returned list contains copies of the internal
     * variable data to ensure immutability of the original dataset.
     *
     * @return A list of {@code Node} objects representing the variables. If no
     * variables are present, an empty list is returned.
     */
    @Override
    public List<Node> getVariables() {
        return new ArrayList<>(variables);
    }

    /**
     * Retrieves the sample size of the dataset associated with this instance.
     *
     * @return The number of rows in the dataset, representing the sample size.
     */
    @Override
    public int getSampleSize() {
        return dataSet.getNumRows();
    }

    // ============================================================================================
    // Continuous child: Student-t IRLS ridge on features [RFF(Z_cont), OneHot(Z_disc)]
    // ============================================================================================

    /**
     * Returns a string representation of this MinimaxTRffBicScore instance.
     * The returned string provides a concise description of the scoring method.
     *
     * @return A string that indicates the scoring method used, specifically
     * "Minimax-t RFF BIC score (mixed)".
     */
    @Override
    public String toString() {
        return "Minimax-t RFF BIC score (mixed)";
    }

    /**
     * Retrieves the dataset associated with this instance.
     *
     * @return The {@code DataModel} object representing the dataset used in the context
     * of this instance. The returned dataset provides access to the underlying
     * data used for computations and analyses.
     */
    public DataModel getDataModel() {
        return dataSet;
    }

    // ============================================================================================
    // Discrete child: multinomial logistic ridge on features [RFF(Z_cont), OneHot(Z_disc)]
    // Reference class 0, parameters for classes 1..K-1
    // ============================================================================================

    /**
     * Retrieves the effective sample size used in the context of this instance.
     * The effective sample size represents a statistical measure that accounts for
     * the influence of data characteristics such as weighting or dependencies within the dataset.
     *
     * @return The effective sample size as an integer value.
     */
    @Override
    public int getEffectiveSampleSize() {
        return nEff;
    }

    /**
     * Sets the effective sample size for the current instance. If the provided value is
     * negative, the effective sample size will be set to the actual sample size of the dataset.
     * This method also resets any cached values that depend on the effective sample size.
     *
     * @param nEff The effective sample size to be set. If this value is negative, the sample
     *             size of the dataset will be used instead.
     */
    @Override
    public void setEffectiveSampleSize(int nEff) {
        this.nEff = (nEff < 0) ? this.sampleSize : nEff;
        resetCache();
    }

    /**
     * Sets the value of nu, which must be a finite number greater than 2.
     * If the provided value does not meet these criteria, an IllegalArgumentException is thrown.
     * This method also resets the associated cache after updating the value.
     *
     * @param nu the new value to set for nu; must be finite and greater than 2
     * @throws IllegalArgumentException if nu is not finite or less than or equal to 2
     */
    public void setNu(double nu) {
        if (!(nu > 2) || !Double.isFinite(nu)) throw new IllegalArgumentException("nu must be finite and > 2");
        this.nu = nu;
        resetCache();
    }

    /**
     * Sets the scale factor for the object. The scale determines the proportion
     * by which the object's size or measurement is adjusted.
     *
     * @param scale the new scale factor; it must be a positive finite value greater than 0
     * @throws IllegalArgumentException if the scale is not greater than 0 or is not a finite value
     */
    public void setScale(double scale) {
        if (!(scale > 0) || !Double.isFinite(scale)) throw new IllegalArgumentException("scale must be finite and > 0");
        this.scale = scale;
        resetCache();
    }

    /**
     * Sets the ridge parameter used in the computation. The ridge parameter must
     * be a positive, finite value. An exception will be thrown if the provided
     * value does not meet these criteria.
     *
     * @param ridge the ridge parameter to set; must be a positive and finite value
     * @throws IllegalArgumentException if the ridge value is not greater than 0
     *                                  or is not finite
     */
    public void setRidge(double ridge) {
        if (!(ridge > 0) || !Double.isFinite(ridge)) throw new IllegalArgumentException("ridge must be finite and > 0");
        this.ridge = ridge;
        resetCache();
    }

    // -------------------- one-hot spec --------------------

    /**
     * Sets the number of Random Fourier Features (RFF) to be used.
     * This value determines the dimensionality of the transformed feature space utilized for approximation.
     *
     * @param d the number of RFF to set, must be greater than or equal to 16
     * @throws IllegalArgumentException if the provided value is less than 16
     */
    public void setRffFeatures(int d) {
        if (d < 16) throw new IllegalArgumentException("rffFeatures should be >= 16");
        this.rffFeatures = d;
        resetCache();
    }

    /**
     * Sets the value of the RFF (Random Fourier Features) sigma parameter.
     * This parameter must be a positive finite number. If an invalid value is provided,
     * an {@link IllegalArgumentException} is thrown.
     *
     * @param sigma the sigma value for Random Fourier Features. Must be greater than zero and finite.
     * @throws IllegalArgumentException if sigma is not greater than zero or is not finite.
     */
    public void setRffSigma(double sigma) {
        if (!(sigma > 0) || !Double.isFinite(sigma))
            throw new IllegalArgumentException("rffSigma must be finite and > 0");
        this.rffSigma = sigma;
        resetCache();
    }

    /**
     * Sets the random Fourier feature (RFF) seed used for generating random projections.
     * This seed ensures reproducibility of the random projections generated internally.
     * Changing the seed will reset the internal cache.
     *
     * @param seed the seed value to set for the random Fourier feature generator.
     */
    public void setRffSeed(long seed) {
        this.rffSeed = seed;
        resetCache();
    }

    /**
     * Sets the number of iterations for the IRLS (Iteratively Reweighted Least Squares) algorithm.
     * The value is constrained to a minimum of 1.
     *
     * @param iters the desired number of IRLS iterations; if less than 1, it will be set to 1
     */
    public void setIrlsIters(int iters) {
        this.irlsIters = TMath.max(1, iters);
        resetCache();
    }

    /**
     * Sets the tolerance value for the IRLS (Iterative Reweighted Least Squares) algorithm.
     * The tolerance must be non-negative, and values less than 0 will be clamped to 0.
     * This method also resets any cached data related to IRLS computations.
     *
     * @param tol the tolerance value to be used for the IRLS algorithm. Must be a non-negative value.
     */
    public void setIrlsTol(double tol) {
        this.irlsTol = TMath.max(0.0, tol);
        resetCache();
    }

    // Continuous child: Student-t IRLS ridge on features [RFF(cont parents), OneHot(disc parents)]
// CHANGE: profile/estimate scale per (child, parent set) via IRLS residuals so variance reduction is rewarded.
    private FitResult fitStudentTRffRidgeMixed(int child,
                                               double[] yCentered,
                                               int[] parentIdx,
                                               int[] rows,
                                               int n) {

        final int[] cont = filterContinuous(parentIdx);
        final int[] disc = filterDiscrete(parentIdx);

        final OneHotSpec oh = buildOneHotSpec(disc);

        final int D = rffFeatures;
        final int Q = oh.totalCols;
        final int M = 1 + D + Q;

        // Extract continuous parents (z-scored) into dense n x dCont
        final double[][] Zc = new double[n][cont.length];
        for (int i = 0; i < n; i++) {
            final int row = (rows == null) ? i : rows[i];
            for (int j = 0; j < cont.length; j++) Zc[i][j] = zCols[cont[j]][row];
        }

        // ----------- Coupled RFF: omega per (child,parent), phase per child -----------
        // omegaByParent[j] is length D for parent cont[j]
        final double[][] omegaByParent = new double[cont.length][];
        for (int j = 0; j < cont.length; j++) {
            omegaByParent[j] = getOmega(child, cont[j], D); // MUST return length D
        }
        final double[] phase = getPhase(child, D);          // MUST return length D
        final double phiScale = TMath.sqrt(2.0 / D);

        // IRLS weights for Student-t
        final double[] w = new double[n];
        Arrays.fill(w, 1.0);

        double[] beta = new double[M];
        double prevObj = Double.POSITIVE_INFINITY;

        // IMPORTANT: profile scale per family (start from knob, update each iter)
        double scaleHat = this.scale;

        // Scratch
        final double[] xRow = new double[M];

        for (int iter = 0; iter < irlsIters; iter++) {

            final DMatrixRMaj G = new DMatrixRMaj(M, M);
            final double[] v = new double[M];

            // Build normal equations (weighted ridge)
            for (int i = 0; i < n; i++) {
                buildXRowMixedStudentT_Intercept_Coupled(
                        xRow, i, Zc, omegaByParent, phase, phiScale, oh, disc, rows);

                final double wi = w[i];
                final double yi = yCentered[i];

                for (int a = 0; a < M; a++) v[a] += wi * xRow[a] * yi;

                for (int a = 0; a < M; a++) {
                    final double pa = wi * xRow[a];
                    for (int b = 0; b <= a; b++) G.add(a, b, pa * xRow[b]);
                }
            }

            // symmetrize
            for (int a = 0; a < M; a++) for (int b = 0; b < a; b++) G.set(b, a, G.get(a, b));
            // ridge (intercept unpenalized)
            for (int a = 1; a < M; a++) G.add(a, a, ridge);

            final CholeskyDecomposition_F64<DMatrixRMaj> chol = DecompositionFactory_DDRM.chol(true);
            if (!chol.decompose(G)) return new FitResult(Double.NaN, Double.NaN);
            final DMatrixRMaj L = chol.getT(null);

            beta = solveFromCholeskyLower(L, v);

            // Update weights + profiled scale
            double obj = 0.0;
            double wsum = 0.0;
            double wrss = 0.0;

            for (int i = 0; i < n; i++) {
                buildXRowMixedStudentT_Intercept_Coupled(
                        xRow, i, Zc, omegaByParent, phase, phiScale, oh, disc, rows);

                double yhat = 0.0;
                for (int a = 0; a < M; a++) yhat += xRow[a] * beta[a];

                final double r = yCentered[i] - yhat;

                // Student-t weight uses current scaleHat
                final double u2 = (r / scaleHat) * (r / scaleHat);
                final double wi = (nu + 1.0) / (nu + u2);
                w[i] = wi;

                obj += 0.5 * (nu + 1.0) * TMath.log1p(u2 / nu);

                wsum += wi;
                wrss += wi * r * r;
            }

            // Profile / estimate scale for this family (clamp away from 0)
            if (wsum > 0.0) {
                final double s2 = wrss / wsum;
                scaleHat = TMath.sqrt(TMath.max(1e-12, s2));
            }

            if (TMath.abs(prevObj - obj) <= irlsTol * (1.0 + TMath.abs(prevObj))) break;
            prevObj = obj;
        }

        // Final predictions
        final double[] yhat = new double[n];
        for (int i = 0; i < n; i++) {
            buildXRowMixedStudentT_Intercept_Coupled(
                    xRow, i, Zc, omegaByParent, phase, phiScale, oh, disc, rows);

            double yh = 0.0;
            for (int a = 0; a < M; a++) yh += xRow[a] * beta[a];
            yhat[i] = yh;
        }

        // Likelihood uses profiled scaleHat (NOT the global knob)
        final double ll = studentTLogLik(yCentered, yhat, nu, scaleHat);

        // EDF using last weights
        final DMatrixRMaj Gfinal = new DMatrixRMaj(M, M);
        for (int i = 0; i < n; i++) {
            buildXRowMixedStudentT_Intercept_Coupled(
                    xRow, i, Zc, omegaByParent, phase, phiScale, oh, disc, rows);

            final double wi = w[i];
            for (int a = 0; a < M; a++) {
                final double pa = wi * xRow[a];
                for (int b = 0; b <= a; b++) Gfinal.add(a, b, pa * xRow[b]);
            }
        }
        for (int a = 0; a < M; a++) for (int b = 0; b < a; b++) Gfinal.set(b, a, Gfinal.get(a, b));
        for (int a = 1; a < M; a++) Gfinal.add(a, a, ridge);

        final double trInvPen = traceInvPenalizedBlockFromG(Gfinal);
        if (!Double.isFinite(trInvPen)) return new FitResult(Double.NaN, Double.NaN);

        final int Mp = M - 1;
        double edf = 1.0 + (Mp - ridge * trInvPen);
        if (!(edf >= 0) || !Double.isFinite(edf)) edf = 1.0 + Mp;

        return new FitResult(ll, edf);
    }

    /**
     * Coupled-feature version of the Student-t design row builder:
     * - intercept
     * - RFF features computed as cos( sum_j omega_j[k] * Zc[i][j] + phase[k] )
     * - one-hot for discrete parents (baseline dropped)
     * <p>
     * Note: omegaByParent is indexed by cont-parent position (0..dCont-1), each array length D.
     * Zc is n x dCont, with columns aligned to cont[].
     */
    private void buildXRowMixedStudentT_Intercept_Coupled(double[] out,
                                                          int i,
                                                          double[][] Zc,
                                                          double[][] omegaByParent,
                                                          double[] phase,
                                                          double phiScale,
                                                          OneHotSpec oh,
                                                          int[] discParents,
                                                          int[] rows) {

        // intercept
        out[0] = 1.0;

        // RFF block [1 .. 1 + D - 1]
        final int D = rffFeatures;
        final int rffOff = 1;

        final int dCont = (Zc == null) ? 0 : Zc[i].length;
        if (dCont == 0) {
            Arrays.fill(out, rffOff, rffOff + D, 0.0);
        } else {
            for (int k = 0; k < D; k++) {
                double dot = 0.0;
                // dot = sum_j omega_j[k] * z_j
                for (int j = 0; j < dCont; j++) dot += omegaByParent[j][k] * Zc[i][j];
                out[rffOff + k] = phiScale * TMath.cos(dot + phase[k]);
            }
        }

        // one-hot block [1 + D .. M-1]
        final int ohOff = 1 + D;
        Arrays.fill(out, ohOff, out.length, 0.0);

        if (discParents.length == 0) return;

        final int row = (rows == null) ? i : rows[i];
        for (int t = 0; t < discParents.length; t++) {
            final int var = discParents[t];
            final int lev = dataSet.getInt(row, var);
            if (lev == DiscreteVariable.MISSING_VALUE) continue;

            // baseline level 0 dropped
            if (lev <= 0) continue;

            final int col = oh.offsets[t] + (lev - 1);
            if (col >= oh.offsets[t] && col < oh.offsets[t] + oh.sizes[t] - 1) {
                out[ohOff + col] = 1.0;
            }
        }
    }

    // -------------------- extraction --------------------

    private FitResult fitMultinomialLogitMixed(int child,
                                               int[] y, int K,
                                               int[] parentIdx,
                                               int[] rows, int n) {
        final int[] cont = filterContinuous(parentIdx);
        final int[] disc = filterDiscrete(parentIdx);
        final OneHotSpec oh = buildOneHotSpec(disc);

        final int D = rffFeatures;
        final int Q = oh.totalCols;
        final int M = 1 + D + Q;     // intercept + features
        final int C = K - 1;         // classes 1..K-1 vs ref 0

        // Extract continuous parents
        final double[][] Zc = new double[n][cont.length];
        for (int r = 0; r < n; r++) {
            final int row = (rows == null) ? r : rows[r];
            for (int j = 0; j < cont.length; j++) Zc[r][j] = zCols[cont[j]][row];
        }

        final double phiScale = TMath.sqrt(2.0 / D);

        // Coupled: omega per (child,parent), and phase per child.
        final double[][] omegaByParent = new double[cont.length][];
        for (int j = 0; j < cont.length; j++) {
            omegaByParent[j] = getOmega(child, cont[j], D); // length D
        }
        final double[] phase = getPhase(child, D); // length D

        // Precompute Phi
        final double[][] Phi = new double[n][M];
        final double[] xRow = new double[M];
        for (int i = 0; i < n; i++) {
            buildXRowMixed_Intercept(
                    xRow, i,
                    Zc, cont.length,
                    omegaByParent, phase, phiScale,
                    oh, disc, rows
            );
            System.arraycopy(xRow, 0, Phi[i], 0, M);
        }

        // beta: M x C
        final double[][] beta = new double[M][C];

        double prevObj = Double.POSITIVE_INFINITY;

        // Scratch
        final double[] logits = new double[K];
        final double[][] probs = new double[n][K];  // allocate once

        // IRLS
        for (int iter = 0; iter < irlsIters; iter++) {

            // fill probs from current beta
            fillSoftmaxProbsFromPhi(K, n, beta, Phi, logits, probs);

            // update each class block using frozen probs
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

                for (int a = 0; a < M; a++) for (int b = 0; b < a; b++) G.set(b, a, G.get(a, b));
                for (int a = 1; a < M; a++) G.add(a, a, ridge); // intercept unpenalized

                final CholeskyDecomposition_F64<DMatrixRMaj> chol = DecompositionFactory_DDRM.chol(true);
                if (!chol.decompose(G)) return new FitResult(Double.NaN, Double.NaN);
                final DMatrixRMaj L = chol.getT(null);

                final double[] bc = solveFromCholeskyLower(L, v);
                for (int a = 0; a < M; a++) beta[a][c] = bc[a];
            }

            final double llIter = multinomialLogLikFromPhi(y, K, n, beta, Phi, logits);
            final double obj = -llIter;

            if (TMath.abs(prevObj - obj) <= irlsTol * (1.0 + TMath.abs(prevObj))) break;
            prevObj = obj;
        }

        final double ll = multinomialLogLikFromPhi(y, K, n, beta, Phi, logits);

        // EDF: recompute probs once from final beta
        fillSoftmaxProbsFromPhi(K, n, beta, Phi, logits, probs);

        double edf = 0.0;
        final int Mp = M - 1; // penalized features per class
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

            for (int a = 0; a < M; a++) for (int b = 0; b < a; b++) G.set(b, a, G.get(a, b));
            for (int a = 1; a < M; a++) G.add(a, a, ridge);

            double trInvPen = traceInvPenalizedBlockFromG(G);
            if (!Double.isFinite(trInvPen)) return new FitResult(Double.NaN, Double.NaN);

            double edfC = 1.0 + (Mp - ridge * trInvPen); // +1 intercept (unpenalized)
            if (!(edfC >= 0) || !Double.isFinite(edfC)) edfC = 1.0 + Mp;

            edf += edfC;
        }

        return new FitResult(ll, edf);
    }

    private void buildXRowMixed_Intercept(double[] out, int i,
                                          double[][] Zc, int dCont,
                                          double[][] omegaByParent, double[] phase, double phiScale,
                                          OneHotSpec oh, int[] discParents, int[] rows) {

        // intercept
        out[0] = 1.0;

        // RFF part at [1 .. 1 + D - 1]
        final int rffOff = 1;
        if (dCont == 0) {
            Arrays.fill(out, rffOff, rffOff + rffFeatures, 0.0);
        } else {
            for (int k = 0; k < rffFeatures; k++) {
                double dot = 0.0;
                for (int j = 0; j < dCont; j++) {
                    dot += omegaByParent[j][k] * Zc[i][j];
                }
                out[rffOff + k] = phiScale * TMath.cos(dot + phase[k]);
            }
        }

        // one-hot part at [1 + D .. M-1]
        final int ohOff = 1 + rffFeatures;
        Arrays.fill(out, ohOff, out.length, 0.0);

        if (discParents.length == 0) return;

        int row = (rows == null) ? i : rows[i];
        for (int t = 0; t < discParents.length; t++) {
            int var = discParents[t];
            int lev = dataSet.getInt(row, var);
            if (lev == DiscreteVariable.MISSING_VALUE) continue;

            if (lev <= 0) continue; // baseline dropped
            int col = oh.offsets[t] + (lev - 1);

            if (col >= oh.offsets[t] && col < oh.offsets[t] + oh.sizes[t] - 1) {
                out[ohOff + col] = 1.0;
            }
        }
    }

    // -------------------- Student-t loglik + gamma --------------------

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
            // drop baseline => (K-1) cols (if K>=2)
            off += max(0, K - 1);
        }
        return new OneHotSpec(sizes, offsets, off);
    }

    private int numCategories(int varIndex) {
        Node v = variables.get(varIndex);
        if (!(v instanceof DiscreteVariable dv)) return 0;
        return dv.getNumCategories();
    }

    // -------------------- linear algebra helpers --------------------

    private int[] validRows(int[] vars) {
        int n = sampleSize;
        int[] tmp = new int[n];
        int m = 0;

        outer:
        for (int r = 0; r < n; r++) {
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

    // -------------------- type utilities --------------------

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

    private int[] filterContinuous(int[] cols) {
        int c = 0;
        for (int v : cols) if (!isDiscrete(v)) c++;
        int[] out = new int[c];
        int k = 0;
        for (int v : cols) if (!isDiscrete(v)) out[k++] = v;
        return out;
    }

    // -------------------- cache utils --------------------

//        private void resetCache() {
//            localScoreCacheRef.set(new ConcurrentHashMap<>());
//        }

    private int[] filterDiscrete(int[] cols) {
        int c = 0;
        for (int v : cols) if (isDiscrete(v)) c++;
        int[] out = new int[c];
        int k = 0;
        for (int v : cols) if (isDiscrete(v)) out[k++] = v;
        return out;
    }

    private void resetCache() {
        localScoreCacheRef.set(new ConcurrentHashMap<>());
        omegaCache.clear();
        phaseCache.clear();
    }

    private long knobsSignature() {
        long h = 1469598103934665603L;
        h = (h ^ Double.doubleToLongBits(nu)) * 1099511628211L;
        h = (h ^ Double.doubleToLongBits(scale)) * 1099511628211L;
        h = (h ^ Double.doubleToLongBits(ridge)) * 1099511628211L;
        h = (h ^ rffFeatures) * 1099511628211L;
        h = (h ^ Double.doubleToLongBits(rffSigma)) * 1099511628211L;
        h = (h ^ rffSeed) * 1099511628211L;
        h = (h ^ irlsIters) * 1099511628211L;
        h = (h ^ Double.doubleToLongBits(irlsTol)) * 1099511628211L;
        return h;
    }

    /**
     * Appends an integer value to the end of an array.
     *
     * @param z the original array to which the value will be appended
     * @param x the integer value to append
     * @return a new array containing the elements of the original array and the appended value
     */
    public int[] append(int[] z, int x) {
        int[] out = Arrays.copyOf(z, z.length + 1);
        out[z.length] = x;
        return out;
    }

    /**
     * A record that encapsulates the results of a local fit operation, consisting of
     * the log-likelihood value, the effective degrees of freedom, and the number
     * of observations used.
     *
     * @param logLik The log-likelihood value from the fit.
     * @param edf The effective degrees of freedom associated with the fit.
     * @param nUsed The number of observations used in the fitting process.
     */
    public record LocalFit(double logLik, double edf, int nUsed) {}

    /**
     * Computes and retrieves the valid rows for a union operation based on the given child variable
     * and an array of parent variables.
     *
     * @param child The child variable that participates in the union.
     * @param parents An array of parent variables included in the union. Can be null if there are no parents.
     * @return An array containing the valid rows for the union, or null if row subset calculations are disabled.
     */
    public int[] validRowsForUnion(int child, int[] parents) {
        if (!calculateRowSubsets) return null;
        int[] vars = new int[(parents == null ? 0 : parents.length) + 1];
        vars[0] = child;
        if (parents != null) System.arraycopy(parents, 0, vars, 1, parents.length);
        // Optional sanity check:
        // if (IntStream.of(vars).distinct().count() != vars.length) throw new IllegalArgumentException("child in parents?");
        return validRows(vars);
    }

    /**
     * Computes the local fit for a specified child variable based on its parent variables
     * and a subset of rows. This method evaluates both discrete and continuous cases,
     * handling data as either multinomial or Student's t-distribution.
     *
     * @param child the index of the child variable for which the local fit is to be computed
     * @param parents an array of indices representing the parent variables of the child;
     *                if null, it is treated as an empty array
     * @param rows an array of indices representing the subset of rows to be considered
     *             for the computation; if null, all rows are considered
     * @return a LocalFit object that contains the log-likelihood, effective degrees of freedom,
     *         and the sample size used in the computation. In case of errors or constraints
     *         not met, the returned object contains NaN values or appropriately reduced sample size
     */
    public LocalFit localFitOnRows(int child, int[] parents, int[] rows) {
        int[] pa = (parents == null) ? new int[0] : Arrays.copyOf(parents, parents.length);
        Arrays.sort(pa);

        try {
            if (!(ridge > 0) || !Double.isFinite(ridge)) return new LocalFit(Double.NaN, Double.NaN, 0);

            final int n = (rows == null) ? nEff : rows.length;
            if (n < 10) return new LocalFit(Double.NaN, Double.NaN, n);

            if (isDiscrete(child)) {
                int[] y = extractDiscreteChild(child, rows, n);
                int K = numCategories(child);
                if (K < 2) return new LocalFit(Double.NaN, Double.NaN, n);

                if (pa.length == 0) {
                    double ll = multinomialInterceptOnlyLogLik(y, K);
                    double edf = (K - 1.0);
                    return new LocalFit(ll, edf, n);
                }

                FitResult fit = fitMultinomialLogitMixed(child, y, K, pa, rows, n);
                return new LocalFit(fit.logLik(), fit.edf(), n);

            } else {
                if (!(nu > 2) || !Double.isFinite(nu)) return new LocalFit(Double.NaN, Double.NaN, n);
                if (!(scale > 0) || !Double.isFinite(scale)) return new LocalFit(Double.NaN, Double.NaN, n);

                double[] y = extractContinuousChild(child, rows, n);
                centerInPlace(y);

                if (pa.length == 0) {
                    double scaleHat = profileStudentTScale(y, nu, this.scale, irlsIters, irlsTol);
                    double ll0 = studentTLogLik(y, new double[n], nu, scaleHat);
                    double edf0 = 0.0;
                    return new LocalFit(ll0, edf0, n);
                }

                FitResult fit = fitStudentTRffRidgeMixed(child, y, pa, rows, n);
                return new LocalFit(fit.logLik(), fit.edf(), n);
            }

        } catch (RuntimeException e) {
            TetradLogger.getInstance().log(e.getMessage());
            return new LocalFit(Double.NaN, Double.NaN, 0);
        }
    }

    /**
     * Sets the penalty discount factor for the score function.
     * The penalty discount factor is used to adjust the penalty term in the score calculation.
     *
     * @param penaltyDiscount The penalty discount factor, typically between 0 and 1.
     */
    public void setPenaltyDiscount(double penaltyDiscount) {
        this.penaltyDiscount = penaltyDiscount;
    }

    private record FitResult(double logLik, double edf) {
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

    @Serial
    private void readObject(java.io.ObjectInputStream in)
            throws java.io.IOException, ClassNotFoundException {
        in.defaultReadObject();
        initCaches(); // important
    }
}