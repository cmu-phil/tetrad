package edu.cmu.tetrad.search.score;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.EffectiveSampleSizeSettable;
import edu.cmu.tetrad.util.TetradLogger;
import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.factory.DecompositionFactory_DDRM;
import org.ejml.interfaces.decomposition.CholeskyDecomposition_F64;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static java.lang.Math.*;

/**
 * <p><b>Minimax-t Legendre BIC score (mixed)</b></p>
 *
 * <p>
 * Local BIC-style score for structure learning with mixed continuous and discrete variables.
 * Continuous children use a Student-t location model fit via IRLS with ridge regularization;
 * discrete children use multinomial logistic regression fit via IRLS with ridge.
 * </p>
 *
 * <p>
 * Continuous parents enter through additive Legendre basis expansions:
 * for each continuous parent X (globally z-scored), we map to [-1,1] using x = clamp(z/clip),
 * then include P1(x)..Pt(x) where t = legendreDegree. (Intercept handled separately.)
 * Discrete parents enter via baseline-dropped one-hot blocks.
 * </p>
 *
 * <p><b>Missing data:</b> Rows with missing in {Y} ∪ Pa(Y) are dropped locally.</p>
 *
 * <p><b>Score:</b> score = logLik_hat - 0.5 * edf * log(n)</p>
 * <p>
 * Notes:
 * - This is additive in continuous parents (no cross terms) to control feature growth.
 * - Legendre polynomials are evaluated by stable recurrence.
 */
public final class MinimaxLegendreScore implements Score, EffectiveSampleSizeSettable {

    // -------------------- data --------------------

    private final boolean calculateRowSubsets;
    private final DataSet dataSet;
    private final List<Node> variables;
    private final int sampleSize;

    /**
     * Continuous columns z-scored globally (NaNs preserved). Discrete cols are all NaN.
     */
    private final double[][] zCols;

    /**
     * Cache key -> score.
     */
    private final AtomicReference<ConcurrentHashMap<Long, Double>> localScoreCacheRef =
            new AtomicReference<>(new ConcurrentHashMap<>());

    // -------------------- knobs --------------------

    /**
     * Student-t df for continuous child. Must be > 2.
     */
    private volatile double nu = 5.0;

    /**
     * Initial scale for Student-t IRLS; also used if profiled scale can't be estimated.
     */
    private volatile double scale = 1.0;

    /**
     * Ridge penalty (>0). Intercept is not penalized.
     */
    private volatile double ridge = 1e-3;

    /**
     * Legendre truncation t (>=1). Features per continuous parent = t.
     */
    private volatile int legendreDegree = 8;

    /**
     * Map z to [-1,1] by x = clamp(z/clip). Typical clip ~ 2.5..4.0.
     * Larger clip keeps more of z in linear region; smaller clip saturates more.
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
     * Effective sample size.
     */
    private volatile int nEff;
    private double penaltyDiscount = 1.0;

    /** Add pairwise interactions using only P1(x)=x for continuous parents. */
    private volatile boolean useInteractions = true;

    /** Only the first K continuous parents (in parentIdx order) participate in interactions. */
    private volatile int interactionMaxParents = 4;  // 0/1 => none; 4 => up to 6 interaction cols

    // -------------------- ctor --------------------

    /**
     * Constructs a MinimaxLegendreScore instance by initializing the dataset and performing
     * preprocessing steps such as handling missing values, calculating z-scores for variables,
     * and preparing the necessary internal data structures.
     *
     * @param dataSet the input dataset containing rows of samples and variables; must not be null.
     *                Throws a NullPointerException if the dataSet is null. The dataset is used to
     *                compute z-scores for continuous variables while preserving NaN values and sets up
     *                necessary metadata such as variables and sample size.
     */
    public MinimaxLegendreScore(DataSet dataSet) {
        if (dataSet == null) throw new NullPointerException("dataSet");

        this.dataSet = dataSet;
        this.variables = new ArrayList<>(dataSet.getVariables());
        this.sampleSize = dataSet.getNumRows();
        setEffectiveSampleSize(-1);

        this.calculateRowSubsets = dataSet.existsMissingValue();

        int p = variables.size();
        double[][] raw = new double[p][sampleSize];
        for (int j = 0; j < p; j++) {
            if (isDiscrete(j)) {
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

    private static void centerInPlace(double[] y) {
        double m = 0.0;
        for (double v : y) m += v;
        m /= y.length;
        for (int i = 0; i < y.length; i++) y[i] -= m;
    }

    // -------------------- Score interface --------------------

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

    // -------------------- EffectiveSampleSizeSettable --------------------

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

    // -------------------- knobs setters --------------------

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
                final double e = Math.exp(logitsScratch[k] - maxLog);
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
            for (int k = 0; k < K; k++) sum += Math.exp(logitsScratch[k] - maxLog);

            ll += (logitsScratch[y[i]] - maxLog) - Math.log(sum);
        }

        return ll;
    }

    private static long cacheKey(int i, int[] parents, long knobsSig) {
        long h = 1469598103934665603L;
        h = (h ^ i) * 1099511628211L;
        for (int p : parents) h = (h ^ p) * 1099511628211L;
        h = (h ^ knobsSig) * 1099511628211L;
        return h;
    }

    private static int[] concat(int i, int[] parents) {
        int[] all = new int[parents.length + 1];
        all[0] = i;
        System.arraycopy(parents, 0, all, 1, parents.length);
        return all;
    }

    /**
     * Retrieves the current DataModel instance.
     *
     * @return the DataModel instance representing the current data set
     */
    public DataModel getDataModel() {
        return dataSet;
    }

    /**
     * Computes the local score for a given variable and its parent variables
     * using a caching mechanism to store the computed scores.
     *
     * @param i The index of the target variable for which the local score is calculated.
     * @param parents An optional variable-length argument representing the indices
     *                of the parent variables of the target variable.
     * @return A double representing the local score, or {@code Double.NaN} if the
     *         score cannot be computed due to invalid conditions or parameters.
     */
//    @Override
//    public double localScore(int i, int... parents) {
//        Arrays.sort(parents);
//        long key = cacheKey(i, parents, knobsSignature());
//        final ConcurrentHashMap<Long, Double> cache = localScoreCacheRef.get();
//
//        return cache.computeIfAbsent(key, k -> {
//            try {
//                if (!(ridge > 0) || !Double.isFinite(ridge)) return Double.NaN;
//
//                int[] all = concat(i, parents);
//                int[] rows = calculateRowSubsets ? validRows(all) : null;
//
//                int n = (rows == null) ? nEff : rows.length;
//                if (n < 10) return Double.NaN;
//
//                if (isDiscrete(i)) {
//                    // -------- discrete child: multinomial logistic ridge --------
//                    int[] y = extractDiscreteChild(i, rows, n);
//                    int K = numCategories(i);
//                    if (K < 2) return Double.NaN;
//
//                    if (parents.length == 0) {
//                        double ll = multinomialInterceptOnlyLogLik(y, K);
//                        double bic = ll - 0.5 * (K - 1.0) * log(n);
//                        return bic;
//                    }
//
//                    FitResult fit = fitMultinomialLogitMixed(i, y, K, parents, rows, n);
//                    if (!Double.isFinite(fit.logLik)) return Double.NaN;
//                    return fit.logLik - 0.5 * fit.edf * log(n);
//
//                } else {
//                    // -------- continuous child: Student-t Legendre ridge --------
//                    if (!(nu > 2) || !Double.isFinite(nu)) return Double.NaN;
//                    if (!(scale > 0) || !Double.isFinite(scale)) return Double.NaN;
//
//                    double[] y = extractContinuousChild(i, rows, n);
////                    centerInPlace(y); // let's turn this centering of y off...
//
//                    if (parents.length == 0) {
//                        // profile scale for intercept-only so it’s comparable with parent models
//                        double scaleHat = this.scale;
//
//                        // good initial guess: RMS of y (centered already)
//                        double s2 = 0.0;
//                        for (double v : y) s2 += v * v;
//                        scaleHat = Math.sqrt(Math.max(1e-12, s2 / n));
//
//                        // 1–2 fixed-point refinements using t-weights (cheap, stabilizes)
//                        for (int it = 0; it < 2; it++) {
//                            double wsum = 0.0, wrss = 0.0;
//                            double invS2 = 1.0 / (scaleHat * scaleHat);
//                            for (int r = 0; r < n; r++) {
//                                double u2 = (y[r] * y[r]) * invS2;
//                                double w = (nu + 1.0) / (nu + u2);
//                                wsum += w;
//                                wrss += w * y[r] * y[r];
//                            }
//                            if (wsum > 0.0) scaleHat = Math.sqrt(Math.max(1e-12, wrss / wsum));
//                        }
//
//                        double ll0 = studentTLogLik(y, new double[n], nu, scaleHat);
//                        double edf0 = 1.0; // if you're counting intercept (even though y is centered)
//                        return ll0 - 0.5 * penaltyDiscount * edf0 * log(n);
//                    }
//
//                    FitResult fit = fitStudentTLegendreRidgeMixed(y, parents, rows, n);
//                    if (!Double.isFinite(fit.logLik)) return Double.NaN;
//                    return fit.logLik - 0.5 * penaltyDiscount * fit.edf * log(n);
//                }
//
//            } catch (RuntimeException e) {
//                TetradLogger.getInstance().log(e.getMessage());
//                return Double.NaN;
//            }
//        });
//    }

    @Override
    public double localScore(int i, int... parents) {
        LocalFit fit = localFit(i, parents);
        if (!Double.isFinite(fit.logLik) || !Double.isFinite(fit.edf) || fit.nUsed < 10) return Double.NaN;
        return fit.logLik - 0.5 * penaltyDiscount * fit.edf * Math.log(fit.nUsed);
    }

    /**
     * Calculates the difference in local scores when a new variable is appended
     * to the conditioning set.
     *
     * @param x The variable being appended to the conditioning set.
     * @param y The target variable for which the local score is computed.
     * @param z The current conditioning set of variables.
     * @return The difference in local scores after appending {@code x} to {@code z}.
     */
    @Override
    public double localScoreDiff(int x, int y, int[] z) {
        return localScore(y, append(z, x)) - localScore(y, z);
    }

    // ============================================================================================
    // Continuous child: Student-t IRLS ridge on features [Legendre(cont parents), OneHot(disc parents)]
    // ============================================================================================

    /**
     * Retrieves a list of variables represented as Node objects.
     *
     * @return a new List containing the current variables. Modifying the returned list does not affect the original list.
     */
    @Override
    public List<Node> getVariables() {
        return new ArrayList<>(variables);
    }

    /**
     * Retrieves the sample size, which corresponds to the number of rows in the dataset.
     *
     * @return the number of rows in the dataset as an integer
     */
    @Override
    public int getSampleSize() {
        return dataSet.getNumRows();
    }

    // ============================================================================================
    // Discrete child: multinomial logistic ridge on features [Legendre(cont parents), OneHot(disc parents)]
    // ============================================================================================

    /**
     * Returns a string representation of the object.
     *
     * @return a string indicating the description "Minimax-t Legendre BIC score (mixed)".
     */
    @Override
    public String toString() {
        return "Minimax-t Legendre BIC score (mixed)";
    }

    /**
     * Computes and returns the effective sample size.
     *
     * The effective sample size is a measure of the amount of independent
     * information in the data, adjusted for autocorrelation or statistical dependencies
     * within the sample. It is useful in statistical analyses where independence
     * of observations is an assumption.
     *
     * @return the effective sample size as an integer
     */
    @Override
    public int getEffectiveSampleSize() {
        return nEff;
    }

    // ============================================================================================
    // Helpers
    // ============================================================================================

    /**
     * Sets the effective sample size for the current instance.
     * If the provided effective sample size is negative, it defaults to the overall sample size.
     * After setting the effective sample size, the internal cache is reset.
     *
     * @param nEff the effective sample size to be set; if negative, the sample size will be used instead
     */
    @Override
    public void setEffectiveSampleSize(int nEff) {
        this.nEff = (nEff < 0) ? this.sampleSize : nEff;
        resetCache();
    }

    /**
     * Sets the value of nu. The value of nu must be a finite number greater than 2.
     *
     * @param nu The new value to assign to nu. It must be a finite number greater than 2.
     * @throws IllegalArgumentException If the provided value for nu is not finite or is less than or equal to 2.
     */
    public void setNu(double nu) {
        if (!(nu > 2) || !Double.isFinite(nu)) throw new IllegalArgumentException("nu must be finite and > 2");
        this.nu = nu;
        resetCache();
    }

    /**
     * Sets the scale factor for this object. The scale must be a finite positive value.
     * Passing an invalid scale value will result in an IllegalArgumentException.
     *
     * @param scale the new scale factor, must be greater than 0 and finite
     */
    public void setScale(double scale) {
        if (!(scale > 0) || !Double.isFinite(scale)) throw new IllegalArgumentException("scale must be finite and > 0");
        this.scale = scale;
        resetCache();
    }

    /**
     * Sets the ridge regularization parameter, which is used to stabilize
     * inverse operations and control overfitting in statistical models.
     * The ridge value must be a finite positive number.
     *
     * @param ridge the ridge regularization parameter; must be greater than 0
     *              and finite
     * @throws IllegalArgumentException if {@code ridge} is not finite or is less
     *                                  than or equal to 0
     */
    public void setRidge(double ridge) {
        if (!(ridge > 0) || !Double.isFinite(ridge)) throw new IllegalArgumentException("ridge must be finite and > 0");
        this.ridge = ridge;
        resetCache();
    }

    /**
     * Sets the degree of the Legendre polynomial used in the model.
     * The Legendre degree must be a positive integer greater than or equal to 1.
     * This parameter impacts the complexity of the polynomial expansion.
     *
     * @param t the degree of the Legendre polynomial; must be >= 1
     * @throws IllegalArgumentException if {@code t} is less than 1
     */
    public void setLegendreDegree(int t) {
        if (t < 1) throw new IllegalArgumentException("legendreDegree must be >= 1");
        this.legendreDegree = t;
        resetCache();
    }

    /**
     * Sets the threshold for Legendre clipping, which is a numerical safeguard used
     * to constrain values within a finite range. This parameter ensures stability
     * during numerical operations involving Legendre polynomials.
     *
     * @param clip the Legendre clip value; must be a finite positive number greater than 0
     * @throws IllegalArgumentException if {@code clip} is not finite or is less than or equal to 0
     */
    public void setLegendreClip(double clip) {
        if (!(clip > 0) || !Double.isFinite(clip))
            throw new IllegalArgumentException("legendreClip must be finite and > 0");
        this.legendreClip = clip;
        resetCache();
    }

    /**
     * Sets the maximum number of iterations to be used in the Iterative Reweighted
     * Least Squares (IRLS) procedure. The IRLS method is often used in optimization
     * algorithms for fitting statistical models.
     *
     * If the provided number of iterations is less than 1, it defaults to 1.
     *
     * This method also triggers a reset of the cached local score data.
     *
     * @param iters the number of iterations for the IRLS procedure; must be
     *              a positive integer
     */
    public void setIrlsIters(int iters) {
        this.irlsIters = Math.max(1, iters);
        resetCache();
    }

    /**
     * Sets the convergence tolerance for the Iterative Reweighted Least Squares (IRLS) procedure.
     * The tolerance specifies the threshold for stopping the IRLS iterations as soon as
     * the updates in the optimization process become sufficiently small.
     *
     * If the provided tolerance is less than 0.0, it is set to 0.0 by default.
     *
     * This method also triggers a reset of the cached local score data.
     *
     * @param tol the convergence tolerance for the IRLS procedure; must be non-negative
     */
    public void setIrlsTol(double tol) {
        this.irlsTol = Math.max(0.0, tol);
        resetCache();
    }

    /**
     * Enables or disables the use of interaction terms in the model. Interaction terms
     * represent combined effects between variables and can be included to capture
     * their joint influence on the outcome. When interactions are enabled, the model
     * considers such terms during calculations.
     *
     * Changing this setting triggers a reset of the cached local score data, ensuring
     * subsequent computations use updated parameters.
     *
     * @param useInteractions a boolean indicating whether to enable (true) or disable
     *                        (false) interaction terms in the model
     */
    public void setUseInteractions(boolean useInteractions) {
        this.useInteractions = useInteractions;
        resetCache();
    }

    /**
     * Sets the maximum number of parents that can be considered for interaction terms in the model.
     * The value is adjusted to ensure it is non-negative, with negative inputs being clamped to zero.
     * This method also triggers a reset of the cached local score data.
     *
     * @param k the maximum number of parents allowed for interactions; must be a non-negative integer
     */
    public void setInteractionMaxParents(int k) {
        this.interactionMaxParents = Math.max(0, k);
        resetCache();
    }

    private FitResult fitStudentTLegendreRidgeMixed(double[] yCentered,
                                                    int[] parentIdx,
                                                    int[] rows,
                                                    int n) {

        final int[] cont = filterContinuous(parentIdx);
        final int[] disc = filterDiscrete(parentIdx);

        final OneHotSpec oh = buildOneHotSpec(disc);

        final int t = legendreDegree;

//        final int D = cont.length * t;          // additive Legendre: t per cont parent
//        final int Q = oh.totalCols;
//        final int M = 1 + D + Q;               // intercept + legendre + one-hot

        final int D = cont.length * t;

        final int kInt = (useInteractions ? Math.min(cont.length, interactionMaxParents) : 0);
        final int I = (kInt >= 2) ? (kInt * (kInt - 1)) / 2 : 0;

        final int Q = oh.totalCols;
        final int M = 1 + D + I + Q;

        // Extract continuous parents (z-scored) into dense n x dCont
        final double[][] Zc = new double[n][cont.length];
        for (int i = 0; i < n; i++) {
            final int row = (rows == null) ? i : rows[i];
            for (int j = 0; j < cont.length; j++) Zc[i][j] = zCols[cont[j]][row];
        }

        // IRLS weights for Student-t
        final double[] w = new double[n];
        Arrays.fill(w, 1.0);

        double[] beta = new double[M];
        double prevObj = Double.POSITIVE_INFINITY;

        // Profile scale per family
        double scaleHat = this.scale;

        // Scratch buffers (reuse to avoid churn)
        final double[] xRow = new double[M];
        final double[] v = new double[M];

        for (int iter = 0; iter < irlsIters; iter++) {

            final DMatrixRMaj G = new DMatrixRMaj(M, M);
            Arrays.fill(v, 0.0);

            // Build normal equations (weighted ridge)
            for (int i = 0; i < n; i++) {
                buildXRowStudentT_Intercept_Legendre(
                        xRow, i, Zc, cont.length, t,
                        oh, disc, rows
                );

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
                buildXRowStudentT_Intercept_Legendre(
                        xRow, i, Zc, cont.length, t,
                        oh, disc, rows
                );

                double mu = 0.0;
                for (int a = 0; a < M; a++) mu += xRow[a] * beta[a];

                final double r = yCentered[i] - mu;

                final double u2 = (r / scaleHat) * (r / scaleHat);
                final double wi = (nu + 1.0) / (nu + u2);
                w[i] = wi;

                obj += 0.5 * (nu + 1.0) * Math.log1p(u2 / nu);

                wsum += wi;
                wrss += wi * r * r;
            }

            if (wsum > 0.0) {
                final double s2 = wrss / wsum;
                scaleHat = Math.sqrt(Math.max(1e-12, s2));
            }

            if (Math.abs(prevObj - obj) <= irlsTol * (1.0 + Math.abs(prevObj))) break;
            prevObj = obj;
        }

        // Final predictions  (FIXED: build into xRow, don’t allocate a throwaway array)
        final double[] yhat = new double[n];
        for (int i = 0; i < n; i++) {
            buildXRowStudentT_Intercept_Legendre(
                    xRow, i, Zc, cont.length, t,
                    oh, disc, rows
            );
            double mu = 0.0;
            for (int a = 0; a < M; a++) mu += xRow[a] * beta[a];
            yhat[i] = mu;
        }

        // Likelihood uses profiled scaleHat
        final double ll = studentTLogLik(yCentered, yhat, nu, scaleHat);

        // EDF using last weights
        final DMatrixRMaj Gfinal = new DMatrixRMaj(M, M);
        for (int i = 0; i < n; i++) {
            buildXRowStudentT_Intercept_Legendre(
                    xRow, i, Zc, cont.length, t,
                    oh, disc, rows
            );

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
     * Student-t design row:
     * - intercept
     * - Legendre block: for each continuous parent j, P1..Pt of mapped value
     * - one-hot block for discrete parents (baseline dropped)
     */
    /**
     * Student-t design row:
     * - intercept
     * - Legendre block: for each continuous parent j, P1..Pt of mapped value
     * - (optional) interaction block: x_a * x_b for first-degree mapped values
     * - one-hot block for discrete parents (baseline dropped)
     */
    private void buildXRowStudentT_Intercept_Legendre(double[] out,
                                                      int i,
                                                      double[][] Zc,
                                                      int dCont,
                                                      int t,
                                                      OneHotSpec oh,
                                                      int[] discParents,
                                                      int[] rows) {

        // intercept
        out[0] = 1.0;

        final int legOff = 1;

        // How many continuous parents participate in interactions?
        final int kInt = (useInteractions ? Math.min(dCont, interactionMaxParents) : 0);
        final int nInt = (kInt >= 2) ? (kInt * (kInt - 1)) / 2 : 0;

        // Interaction block starts right after Legendre block
        final int intOff = legOff + dCont * t;

        // one-hot block starts after interactions
        final int ohOff = intOff + nInt;

        // Defensive: zero out whole row except intercept (cheap + safe)
        Arrays.fill(out, 1, out.length, 0.0);

        // Precompute mapped x values (only need first kInt, but compute all is fine)
        final double invClip = 1.0 / legendreClip;
        final double[] xMap = (kInt > 0) ? new double[kInt] : null;

        // Fill Legendre block
        int pos = legOff;
        for (int j = 0; j < dCont; j++) {
            double z = Zc[i][j];
//            double x = z * invClip;
            double x = Math.tanh(z / legendreClip);
            x = clamp(x);

            if (j < kInt) xMap[j] = x;

            // Legendre recurrence:
            double Pnm2 = 1.0; // P0
            double Pnm1 = x;   // P1

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

        // Fill interaction block (x_a * x_b using mapped x = P1)
        if (nInt > 0) {
            int ipos = intOff;
            for (int a = 0; a < kInt; a++) {
                final double xa = xMap[a];
                for (int b = 0; b < a; b++) {
                    out[ipos++] = xa * xMap[b];
                }
            }
        }

        // one-hot block (baseline dropped)
        if (discParents.length == 0) return;

        final int row = (rows == null) ? i : rows[i];
        for (int parentPos = 0; parentPos < discParents.length; parentPos++) {
            final int var = discParents[parentPos];
            final int lev = dataSet.getInt(row, var);
            if (lev == DiscreteVariable.MISSING_VALUE) continue;
            if (lev <= 0) continue;

            final int col = oh.offsets[parentPos] + (lev - 1);
            if (col >= oh.offsets[parentPos] && col < oh.offsets[parentPos] + oh.sizes[parentPos] - 1) {
                out[ohOff + col] = 1.0;
            }
        }
    }

    private static double clamp(double x) {
        return tanh(x);
//        if (x > 1.0) x = 1.0;
//        else if (x < -1.0) x = -1.0;
//        return x;
    }

    // -------------------- missing rows & extraction --------------------

    private FitResult fitMultinomialLogitMixed(int child,
                                               int[] y, int K,
                                               int[] parentIdx,
                                               int[] rows, int n) {

        final int[] cont = filterContinuous(parentIdx);
        final int[] disc = filterDiscrete(parentIdx);
        final OneHotSpec oh = buildOneHotSpec(disc);

        final int t = legendreDegree;

//        final int D = cont.length * t;
//        final int Q = oh.totalCols;
//        final int M = 1 + D + Q;

        final int D = cont.length * t;

        final int kInt = (useInteractions ? Math.min(cont.length, interactionMaxParents) : 0);
        final int I = (kInt >= 2) ? (kInt * (kInt - 1)) / 2 : 0;

        final int Q = oh.totalCols;
        final int M = 1 + D + I + Q;

        final int C = K - 1;

        // Extract continuous parents
        final double[][] Zc = new double[n][cont.length];
        for (int r = 0; r < n; r++) {
            final int row = (rows == null) ? r : rows[r];
            for (int j = 0; j < cont.length; j++) Zc[r][j] = zCols[cont[j]][row];
        }

        // Precompute Phi
        final double[][] Phi = new double[n][M];
        final double[] xRow = new double[M];
        for (int i = 0; i < n; i++) {
            buildXRowLogit_Intercept_Legendre(xRow, i, Zc, cont.length, t, oh, disc, rows);
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
                    wc = Math.max(wc, 1e-10);

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
                for (int a = 1; a < M; a++) G.add(a, a, ridge);

                final CholeskyDecomposition_F64<DMatrixRMaj> chol = DecompositionFactory_DDRM.chol(true);
                if (!chol.decompose(G)) return new FitResult(Double.NaN, Double.NaN);
                final DMatrixRMaj L = chol.getT(null);

                final double[] bc = solveFromCholeskyLower(L, v);
                for (int a = 0; a < M; a++) beta[a][c] = bc[a];
            }

            final double llIter = multinomialLogLikFromPhi(y, K, n, beta, Phi, logits);
            final double obj = -llIter;

            if (Math.abs(prevObj - obj) <= irlsTol * (1.0 + Math.abs(prevObj))) break;
            prevObj = obj;
        }

        final double ll = multinomialLogLikFromPhi(y, K, n, beta, Phi, logits);

        // EDF
        fillSoftmaxProbsFromPhi(K, n, beta, Phi, logits, probs);

        double edf = 0.0;
        final int Mp = M - 1;
        for (int c = 0; c < C; c++) {
            final DMatrixRMaj G = new DMatrixRMaj(M, M);

            for (int i = 0; i < n; i++) {
                final double[] phi = Phi[i];

                final double pc = probs[i][c + 1];
                double wc = pc * (1.0 - pc);
                wc = Math.max(wc, 1e-10);

                for (int a = 0; a < M; a++) {
                    final double pa = wc * phi[a];
                    for (int b = 0; b <= a; b++) G.add(a, b, pa * phi[b]);
                }
            }

            for (int a = 0; a < M; a++) for (int b = 0; b < a; b++) G.set(b, a, G.get(a, b));
            for (int a = 1; a < M; a++) G.add(a, a, ridge);

            double trInvPen = traceInvPenalizedBlockFromG(G);
            if (!Double.isFinite(trInvPen)) return new FitResult(Double.NaN, Double.NaN);

            double edfC = 1.0 + (Mp - ridge * trInvPen);
            if (!(edfC >= 0) || !Double.isFinite(edfC)) edfC = 1.0 + Mp;

            edf += edfC;
        }

        return new FitResult(ll, edf);
    }

    private void buildXRowLogit_Intercept_Legendre(double[] out,
                                                   int i,
                                                   double[][] Zc,
                                                   int dCont,
                                                   int t,
                                                   OneHotSpec oh,
                                                   int[] discParents,
                                                   int[] rows) {
        // identical feature map as Student-t
        buildXRowStudentT_Intercept_Legendre(out, i, Zc, dCont, t, oh, discParents, rows);
    }

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

    // -------------------- type utils --------------------

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

    private int numCategories(int varIndex) {
        Node v = variables.get(varIndex);
        if (!(v instanceof DiscreteVariable dv)) return 0;
        return dv.getNumCategories();
    }

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
            off += max(0, K - 1);
        }
        return new OneHotSpec(sizes, offsets, off);
    }

    // -------------------- cache & hashing --------------------

    /**
     * Sets the penalty discount to be applied. The value must be a finite positive number.
     *
     * @param penaltyDiscount the penalty discount value to set. Must be greater than 0 and finite.
     * @throws IllegalArgumentException if the provided penaltyDiscount is not finite or not greater than 0.
     */
    public void setPenaltyDiscount(double penaltyDiscount) {
        if (!(penaltyDiscount > 0.0) || !Double.isFinite(penaltyDiscount))
            throw new IllegalArgumentException("Penalty discount must be finite and > 0");

        this.penaltyDiscount = penaltyDiscount;
        resetCache();
    }

//    private void resetCache() {
//        localScoreCacheRef.set(new ConcurrentHashMap<>());
//    }

    private void resetCache() {
        localScoreCacheRef.set(new ConcurrentHashMap<>());
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
        return h;
    }

    /**
     * Appends an integer value to the end of the given array and returns a new array.
     *
     * @param z the original array to which the value is to be appended
     * @param x the integer value to append to the array
     * @return a new array containing all elements of the original array followed by the appended value
     */
    public int[] append(int[] z, int x) {
        int[] out = Arrays.copyOf(z, z.length + 1);
        out[z.length] = x;
        return out;
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

    public LocalFit localFit(int i, int... parents) {
        Arrays.sort(parents);
        long key = cacheKey(i, parents, knobsSignature());
        final ConcurrentHashMap<Long, LocalFit> cache = localFitCacheRef.get();

        return cache.computeIfAbsent(key, k -> {
            try {
                if (!(ridge > 0) || !Double.isFinite(ridge)) return new LocalFit(Double.NaN, Double.NaN, 0);

                int[] all = concat(i, parents);
                int[] rows = calculateRowSubsets ? validRows(all) : null;

                int n = (rows == null) ? nEff : rows.length;
                if (n < 10) return new LocalFit(Double.NaN, Double.NaN, n);

                if (isDiscrete(i)) {
                    int[] y = extractDiscreteChild(i, rows, n);
                    int K = numCategories(i);
                    if (K < 2) return new LocalFit(Double.NaN, Double.NaN, n);

                    if (parents.length == 0) {
                        double ll = multinomialInterceptOnlyLogLik(y, K);
                        double edf = (K - 1.0); // parameters for baseline-dropped intercept-only
                        return new LocalFit(ll, edf, n);
                    }

                    FitResult fit = fitMultinomialLogitMixed(i, y, K, parents, rows, n);
                    return new LocalFit(fit.logLik(), fit.edf(), n);

                } else {
                    if (!(nu > 2) || !Double.isFinite(nu)) return new LocalFit(Double.NaN, Double.NaN, n);
                    if (!(scale > 0) || !Double.isFinite(scale)) return new LocalFit(Double.NaN, Double.NaN, n);

                    double[] y = extractContinuousChild(i, rows, n);

                    if (parents.length == 0) {
                        double scaleHat = this.scale;

                        double s2 = 0.0;
                        for (double v : y) s2 += v * v;
                        scaleHat = Math.sqrt(Math.max(1e-12, s2 / n));

                        for (int it = 0; it < 2; it++) {
                            double wsum = 0.0, wrss = 0.0;
                            double invS2 = 1.0 / (scaleHat * scaleHat);
                            for (int r = 0; r < n; r++) {
                                double u2 = (y[r] * y[r]) * invS2;
                                double w = (nu + 1.0) / (nu + u2);
                                wsum += w;
                                wrss += w * y[r] * y[r];
                            }
                            if (wsum > 0.0) scaleHat = Math.sqrt(Math.max(1e-12, wrss / wsum));
                        }

                        double ll0 = studentTLogLik(y, new double[n], nu, scaleHat);
                        double edf0 = 1.0; // intercept
                        return new LocalFit(ll0, edf0, n);
                    }

                    FitResult fit = fitStudentTLegendreRidgeMixed(y, parents, rows, n);
                    return new LocalFit(fit.logLik(), fit.edf(), n);
                }

            } catch (RuntimeException e) {
                TetradLogger.getInstance().log(e.getMessage());
                return new LocalFit(Double.NaN, Double.NaN, 0);
            }
        });
    }

    /**
     * Computes (logLik, edf) for local model Y=child with given parents,
     * evaluated/fitted on the provided rows (or all rows if rows==null).
     *
     * IMPORTANT: This does NOT choose rows; caller controls row selection.
     * This is what CI tests need to ensure reduced/full use the same sample.
     */
    public LocalFit localFitOnRows(int child, int[] parents, int[] rows) {
        Arrays.sort(parents);

        try {
            if (!(ridge > 0) || !Double.isFinite(ridge)) return new LocalFit(Double.NaN, Double.NaN, 0);

            final int n = (rows == null) ? nEff : rows.length;
            if (n < 10) return new LocalFit(Double.NaN, Double.NaN, n);

            if (isDiscrete(child)) {
                int[] y = extractDiscreteChild(child, rows, n);
                int K = numCategories(child);
                if (K < 2) return new LocalFit(Double.NaN, Double.NaN, n);

                if (parents.length == 0) {
                    double ll = multinomialInterceptOnlyLogLik(y, K);
                    double edf = (K - 1.0);
                    return new LocalFit(ll, edf, n);
                }

                FitResult fit = fitMultinomialLogitMixed(child, y, K, parents, rows, n);
                return new LocalFit(fit.logLik(), fit.edf(), n);

            } else {
                if (!(nu > 2) || !Double.isFinite(nu)) return new LocalFit(Double.NaN, Double.NaN, n);
                if (!(scale > 0) || !Double.isFinite(scale)) return new LocalFit(Double.NaN, Double.NaN, n);

                double[] y = extractContinuousChild(child, rows, n);

                if (parents.length == 0) {
                    // intercept-only: same as your current localFit() code
                    double scaleHat = this.scale;

                    double s2 = 0.0;
                    for (double v : y) s2 += v * v;
                    scaleHat = Math.sqrt(Math.max(1e-12, s2 / n));

                    for (int it = 0; it < 2; it++) {
                        double wsum = 0.0, wrss = 0.0;
                        double invS2 = 1.0 / (scaleHat * scaleHat);
                        for (int r = 0; r < n; r++) {
                            double u2 = (y[r] * y[r]) * invS2;
                            double w = (nu + 1.0) / (nu + u2);
                            wsum += w;
                            wrss += w * y[r] * y[r];
                        }
                        if (wsum > 0.0) scaleHat = Math.sqrt(Math.max(1e-12, wrss / wsum));
                    }

                    double ll0 = studentTLogLik(y, new double[n], nu, scaleHat);
                    double edf0 = 1.0;
                    return new LocalFit(ll0, edf0, n);
                }

                FitResult fit = fitStudentTLegendreRidgeMixed(y, parents, rows, n);
                return new LocalFit(fit.logLik(), fit.edf(), n);
            }

        } catch (RuntimeException e) {
            TetradLogger.getInstance().log(e.getMessage());
            return new LocalFit(Double.NaN, Double.NaN, 0);
        }
    }

    /**
     * Rows valid for {child} ∪ parents (i.e., no missing among these vars).
     * Returned array is strictly increasing row indices.
     */
    public int[] validRowsForUnion(int child, int[] parents) {
        int[] vars = new int[parents.length + 1];
        vars[0] = child;
        System.arraycopy(parents, 0, vars, 1, parents.length);
        Arrays.sort(vars); // <-- add this
        return calculateRowSubsets ? validRows(vars) : null;
    }

    public record LocalFit(double logLik, double edf, int nUsed) {}

    private final AtomicReference<ConcurrentHashMap<Long, LocalFit>> localFitCacheRef =
            new AtomicReference<>(new ConcurrentHashMap<>());

    private record FitResult(double logLik, double edf) {
    }
}