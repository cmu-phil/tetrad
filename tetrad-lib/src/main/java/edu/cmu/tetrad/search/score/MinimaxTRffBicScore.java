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
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static java.lang.Math.*;

/**
 * <p><b>Minimax-t RFF BIC score (mixed)</b></p>
 *
 * <p>
 * Local Bayesian Information Criterion (BIC)–style score for structure learning with
 * mixed continuous and discrete variables. The score is designed to be robust to
 * heavy-tailed noise, nonlinear effects, and heterogeneous parent sets, while remaining
 * computationally stable and fully local.
 * </p>
 *
 * <p><b>Local conditional models.</b>
 * For each candidate parent set Pa(Y), the conditional distribution of Y is modeled as:
 * <ul>
 *   <li><b>Continuous child Y</b>:
 *     Student-t location model with additive structure.
 *     Continuous parents enter through Random Fourier Features (RFF);
 *     discrete parents enter via one-hot encoding.
 *     Parameters are estimated by iteratively reweighted ridge regression (IRLS),
 *     yielding robustness to heavy-tailed residuals.</li>
 *   <li><b>Discrete child Y</b>:
 *     Multinomial logistic (softmax) regression with ridge regularization.
 *     Continuous parents are represented via RFF; discrete parents via one-hot encoding.
 *     Fitting is performed using IRLS.</li>
 * </ul>
 * </p>
 *
 * <p><b>Score definition.</b>
 * The local score takes the BIC form
 * <pre>
 *   score(Y | Pa(Y)) = logLik_hat − 0.5 · edf · log(n),
 * </pre>
 * where {@code logLik_hat} is the maximized (penalized) log-likelihood,
 * {@code n} is the effective sample size for the local family, and {@code edf}
 * is the effective degrees of freedom induced by ridge regularization.
 * </p>
 *
 * <p>
 * For multinomial logistic models, the effective degrees of freedom are approximated
 * by summing the ridge edf contributions of the {@code K − 1} one-vs-reference
 * logistic blocks using the final IRLS weights.
 * </p>
 *
 * <p><b>Minimax-t robustness.</b>
 * The Student-t likelihood induces a reweighting of residuals that downweights extreme
 * observations, yielding a conservative, worst-case–oriented local score that is
 * less sensitive to outliers and model misspecification than Gaussian BIC variants.
 * </p>
 *
 * <p><b>Missing data handling.</b>
 * Missing values are represented as:
 * <ul>
 *   <li>continuous variables: {@code NaN}</li>
 *   <li>discrete variables: {@code DiscreteVariable.MISSING_VALUE}</li>
 * </ul>
 * Rows with missing values in the local family
 * {@code {Y} ∪ Pa(Y)} are excluded on a per-score basis.
 * </p>
 *
 * <p><b>Intended use.</b>
 * This score is intended for robust causal structure learning in mixed-type data,
 * particularly as a conservative alternative to Gaussian or purely kernel-based
 * scores when noise distributions are heavy-tailed or nonlinear effects are present.
 * </p>
 */
public final class MinimaxTRffBicScore implements Score, EffectiveSampleSizeSettable {

    // -------------------- config knobs --------------------

    /**
     * If true, compute row subsets when missing exists.
     */
    private final boolean calculateRowSubsets;
    private final DataSet dataSet;
    private final List<Node> variables;
    private final int sampleSize;
    /**
     * Continuous columns z-scored globally, NaNs preserved. For discrete vars, column is all NaN.
     */
    private final double[][] zCols;
    /**
     * Cache key -> score.
     */
    private final AtomicReference<ConcurrentHashMap<Long, Double>> localScoreCacheRef =
            new AtomicReference<>(new ConcurrentHashMap<>());
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

    // -------------------- data --------------------
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
    private volatile int nEff;

    public MinimaxTRffBicScore(DataSet dataSet) {
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

    // -------------------- Score interface --------------------

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

                    FitResult fit = fitMultinomialLogitMixed(y, K, parents, rows, n, seed);
                    if (!Double.isFinite(fit.logLik)) return Double.NaN;

                    return fit.logLik - 0.5 * fit.edf * log(n);

                } else {
                    // -------- continuous child: Student-t RFF ridge (+ one-hot discrete parents) --------
                    if (!(nu > 2) || !Double.isFinite(nu)) return Double.NaN;
                    if (!(scale > 0) || !Double.isFinite(scale)) return Double.NaN;

                    double[] y = extractContinuousChild(i, rows, n);
                    centerInPlace(y);

                    if (parents.length == 0) {
                        double ll = studentTLogLik(y, new double[n], nu, scale);
                        return ll; // edf=0 after centering
                    }

                    long seed = rffSeed ^ (long) i * 0x9E3779B97F4A7C15L ^ Arrays.hashCode(parents);

                    FitResult fit = fitStudentTRffRidgeMixed(y, parents, rows, n, seed);
                    if (!Double.isFinite(fit.logLik)) return Double.NaN;

                    return fit.logLik - 0.5 * fit.edf * log(n);
                }

            } catch (RuntimeException e) {
                TetradLogger.getInstance().log(e.getMessage());
                return Double.NaN;
            }
        });
    }

    @Override
    public double localScoreDiff(int x, int y, int[] z) {
        return localScore(y, append(z, x)) - localScore(y, z);
    }

    @Override
    public List<Node> getVariables() {
        return new ArrayList<>(variables);
    }

    @Override
    public int getSampleSize() {
        return dataSet.getNumRows();
    }

    @Override
    public String toString() {
        return "Minimax-t RFF BIC score (mixed)";
    }

    public DataModel getDataModel() {
        return dataSet;
    }

    @Override
    public int getEffectiveSampleSize() {
        return nEff;
    }

    // -------------------- fitting results --------------------

    @Override
    public void setEffectiveSampleSize(int nEff) {
        this.nEff = (nEff < 0) ? this.sampleSize : nEff;
        resetCache();
    }

    // ============================================================================================
    // Continuous child: Student-t IRLS ridge on features [RFF(Z_cont), OneHot(Z_disc)]
    // ============================================================================================

    public void setNu(double nu) {
        if (!(nu > 2) || !Double.isFinite(nu)) throw new IllegalArgumentException("nu must be finite and > 2");
        this.nu = nu;
        resetCache();
    }

    public void setScale(double scale) {
        if (!(scale > 0) || !Double.isFinite(scale)) throw new IllegalArgumentException("scale must be finite and > 0");
        this.scale = scale;
        resetCache();
    }

    // ============================================================================================
    // Discrete child: multinomial logistic ridge on features [RFF(Z_cont), OneHot(Z_disc)]
    // Reference class 0, parameters for classes 1..K-1
    // ============================================================================================

    public void setRidge(double ridge) {
        if (!(ridge > 0) || !Double.isFinite(ridge)) throw new IllegalArgumentException("ridge must be finite and > 0");
        this.ridge = ridge;
        resetCache();
    }

    public void setRffFeatures(int d) {
        if (d < 16) throw new IllegalArgumentException("rffFeatures should be >= 16");
        this.rffFeatures = d;
        resetCache();
    }

    public void setRffSigma(double sigma) {
        if (!(sigma > 0) || !Double.isFinite(sigma))
            throw new IllegalArgumentException("rffSigma must be finite and > 0");
        this.rffSigma = sigma;
        resetCache();
    }

    public void setRffSeed(long seed) {
        this.rffSeed = seed;
        resetCache();
    }

    public void setIrlsIters(int iters) {
        this.irlsIters = Math.max(1, iters);
        resetCache();
    }

    // -------------------- one-hot spec --------------------

    public void setIrlsTol(double tol) {
        this.irlsTol = Math.max(0.0, tol);
        resetCache();
    }

    private FitResult fitStudentTRffRidgeMixed(double[] yCentered, int[] parentIdx, int[] rows, int n, long seed) {
        // split parents
        int[] cont = filterContinuous(parentIdx);
        int[] disc = filterDiscrete(parentIdx);

        // build one-hot mapping for discrete parents (drop baseline level 0)
        OneHotSpec oh = buildOneHotSpec(disc);

        final int D = rffFeatures;
        final int Q = oh.totalCols;          // total one-hot cols across discrete parents
        final int M = D + Q;                 // total feature dimension

        // extract continuous parent rows (standardized)
        double[][] Zc = new double[n][cont.length];
        for (int r = 0; r < n; r++) {
            int row = (rows == null) ? r : rows[r];
            for (int j = 0; j < cont.length; j++) Zc[r][j] = zCols[cont[j]][row];
        }

        // draw RFF params for cont part only
        Random rng = new Random(seed);
        double[][] W = new double[D][max(1, cont.length)];
        for (int k = 0; k < D; k++) {
            for (int j = 0; j < W[k].length; j++) W[k][j] = rng.nextGaussian() / rffSigma;
        }
        double[] phase = new double[D];
        for (int k = 0; k < D; k++) phase[k] = 2.0 * PI * rng.nextDouble();
        final double phiScale = sqrt(2.0 / D);

        double[] w = new double[n];
        Arrays.fill(w, 1.0);

        double[] beta = new double[M];
        double prevObj = Double.POSITIVE_INFINITY;

        double[] xRow = new double[M];

        for (int iter = 0; iter < irlsIters; iter++) {

            DMatrixRMaj G = new DMatrixRMaj(M, M);
            double[] v = new double[M];

            for (int i = 0; i < n; i++) {
                buildXRowMixedStudentT(xRow, i, Zc, cont.length, W, phase, phiScale, oh, disc, rows);

                double wi = w[i];
                double yi = yCentered[i];

                for (int a = 0; a < M; a++) v[a] += wi * xRow[a] * yi;

                for (int a = 0; a < M; a++) {
                    double pa = wi * xRow[a];
                    for (int b = 0; b <= a; b++) G.add(a, b, pa * xRow[b]);
                }
            }

            // mirror
            for (int a = 0; a < M; a++) for (int b = 0; b < a; b++) G.set(b, a, G.get(a, b));
            for (int a = 0; a < M; a++) G.add(a, a, ridge);

            CholeskyDecomposition_F64<DMatrixRMaj> chol = DecompositionFactory_DDRM.chol(true);
            if (!chol.decompose(G)) return new FitResult(Double.NaN, Double.NaN);

            DMatrixRMaj L = chol.getT(null);
            beta = solveFromCholeskyLower(L, v);

            // update weights and objective
            double obj = 0.0;

            for (int i = 0; i < n; i++) {
                buildXRowMixedStudentT(xRow, i, Zc, cont.length, W, phase, phiScale, oh, disc, rows);

                double yhat = 0.0;
                for (int a = 0; a < M; a++) yhat += xRow[a] * beta[a];

                double r = yCentered[i] - yhat;
                double u2 = (r / scale) * (r / scale);

                w[i] = (nu + 1.0) / (nu + u2);
                obj += 0.5 * (nu + 1.0) * log1p(u2 / nu);
            }

            if (abs(prevObj - obj) <= irlsTol * (1.0 + abs(prevObj))) break;
            prevObj = obj;
        }

        // final predictions
        double[] yhat = new double[n];
        for (int i = 0; i < n; i++) {
            buildXRowMixedStudentT(xRow, i, Zc, cont.length, W, phase, phiScale, oh, disc, rows);
            double yh = 0.0;
            for (int a = 0; a < M; a++) yh += xRow[a] * beta[a];
            yhat[i] = yh;
        }
        double ll = studentTLogLik(yCentered, yhat, nu, scale);

        // edf: D+Q - ridge * tr(inv(G_with_ridge)) using last weights
        DMatrixRMaj Gfinal = new DMatrixRMaj(M, M);
        for (int i = 0; i < n; i++) {
            buildXRowMixedStudentT(xRow, i, Zc, cont.length, W, phase, phiScale, oh, disc, rows);
            double wi = w[i];
            for (int a = 0; a < M; a++) {
                double pa = wi * xRow[a];
                for (int b = 0; b <= a; b++) Gfinal.add(a, b, pa * xRow[b]);
            }
        }
        for (int a = 0; a < M; a++) for (int b = 0; b < a; b++) Gfinal.set(b, a, Gfinal.get(a, b));
        for (int a = 0; a < M; a++) Gfinal.add(a, a, ridge);

        CholeskyDecomposition_F64<DMatrixRMaj> chol2 = DecompositionFactory_DDRM.chol(true);
        if (!chol2.decompose(Gfinal)) return new FitResult(Double.NaN, Double.NaN);
        DMatrixRMaj Lfinal = chol2.getT(null);

        double trInv = traceInvFromCholeskyLower(Lfinal);
        double edf = M - ridge * trInv;
        if (!(edf >= 0) || !Double.isFinite(edf)) edf = M;

        return new FitResult(ll, edf);
    }

    private void buildXRowMixedStudentT(double[] out, int i,
                                        double[][] Zc, int dCont,
                                        double[][] W, double[] phase, double phiScale,
                                        OneHotSpec oh, int[] discParents, int[] rows) {
        // RFF part (or constant 0 if no cont parents)
        if (dCont == 0) {
            Arrays.fill(out, 0, rffFeatures, 0.0);
        } else {
            for (int k = 0; k < rffFeatures; k++) {
                double dot = 0.0;
                double[] wk = W[k];
                for (int j = 0; j < dCont; j++) dot += wk[j] * Zc[i][j];
                out[k] = phiScale * cos(dot + phase[k]);
            }
        }

        // one-hot part
        Arrays.fill(out, rffFeatures, out.length, 0.0);

        if (discParents.length == 0) return;

        int row = (rows == null) ? i : rows[i];
        for (int t = 0; t < discParents.length; t++) {
            int var = discParents[t];
            int lev = dataSet.getInt(row, var);
            if (lev == DiscreteVariable.MISSING_VALUE) continue;
            // baseline level 0 dropped
            if (lev <= 0) continue;
            int col = oh.offsets[t] + (lev - 1);
            if (col >= oh.offsets[t] && col < oh.offsets[t] + oh.sizes[t] - 1) {
                out[rffFeatures + col] = 1.0;
            }
        }
    }

    // -------------------- missingness row selection --------------------

    private FitResult fitMultinomialLogitMixed(int[] y, int K, int[] parentIdx, int[] rows, int n, long seed) {
        int[] cont = filterContinuous(parentIdx);
        int[] disc = filterDiscrete(parentIdx);

        OneHotSpec oh = buildOneHotSpec(disc);

        final int D = rffFeatures;
        final int Q = oh.totalCols;
        final int M = D + Q;
        final int C = K - 1; // number of parameter blocks (reference class 0)

        // Extract cont parents
        double[][] Zc = new double[n][cont.length];
        for (int r = 0; r < n; r++) {
            int row = (rows == null) ? r : rows[r];
            for (int j = 0; j < cont.length; j++) Zc[r][j] = zCols[cont[j]][row];
        }

        // RFF params
        Random rng = new Random(seed);
        double[][] W = new double[D][max(1, cont.length)];
        for (int k = 0; k < D; k++) for (int j = 0; j < W[k].length; j++) W[k][j] = rng.nextGaussian() / rffSigma;
        double[] phase = new double[D];
        for (int k = 0; k < D; k++) phase[k] = 2.0 * PI * rng.nextDouble();
        final double phiScale = sqrt(2.0 / D);

        // beta: M x C
        double[][] beta = new double[M][C]; // init 0
        double prevObj = Double.POSITIVE_INFINITY;

        double[] xRow = new double[M];

        // IRLS
        for (int iter = 0; iter < irlsIters; iter++) {

            // We do blockwise Newton for each class k=1..K-1 using weights p_k*(1-p_k)
            // with the shared softmax probabilities from current beta.
            // This is an approximation to the full multinomial Hessian (which has cross-class coupling),
            // but works well as a practical ridge-IRLS in scoring.

            double[][] probs = softmaxProbs(y, K, n, beta, xRow, Zc, cont.length, W, phase, phiScale, oh, disc, rows);

            double obj = 0.0;

            // update each class block independently
            for (int c = 0; c < C; c++) {
                // Build XtWX and XtWz where z is working response
                DMatrixRMaj G = new DMatrixRMaj(M, M);
                double[] v = new double[M];

                for (int i = 0; i < n; i++) {
                    buildXRowMixed(xRow, i, Zc, cont.length, W, phase, phiScale, oh, disc, rows);

                    double pc = probs[i][c + 1]; // class c+1 prob
                    double wc = pc * (1.0 - pc);
                    wc = max(wc, 1e-10);

                    // linear predictor for class c+1
                    double eta = 0.0;
                    for (int a = 0; a < M; a++) eta += xRow[a] * beta[a][c];

                    // working response
                    double yc = (y[i] == (c + 1)) ? 1.0 : 0.0;
                    double z = eta + (yc - pc) / wc;

                    for (int a = 0; a < M; a++) v[a] += wc * xRow[a] * z;

                    for (int a = 0; a < M; a++) {
                        double pa = wc * xRow[a];
                        for (int b = 0; b <= a; b++) G.add(a, b, pa * xRow[b]);
                    }
                }

                for (int a = 0; a < M; a++) for (int b = 0; b < a; b++) G.set(b, a, G.get(a, b));
                for (int a = 0; a < M; a++) G.add(a, a, ridge);

                CholeskyDecomposition_F64<DMatrixRMaj> chol = DecompositionFactory_DDRM.chol(true);
                if (!chol.decompose(G)) return new FitResult(Double.NaN, Double.NaN);
                DMatrixRMaj L = chol.getT(null);

                double[] bc = solveFromCholeskyLower(L, v);
                for (int a = 0; a < M; a++) beta[a][c] = bc[a];
            }

            // compute (negative) log-lik as convergence monitor
            double ll = multinomialLogLik(y, K, n, beta, xRow, Zc, cont.length, W, phase, phiScale, oh, disc, rows);
            obj = -ll;

            if (abs(prevObj - obj) <= irlsTol * (1.0 + abs(prevObj))) break;
            prevObj = obj;
        }

        // final log-lik
        double ll = multinomialLogLik(y, K, n, beta, xRow, Zc, cont.length, W, phase, phiScale, oh, disc, rows);

        // edf approximation: sum over class blocks of ridge edf using final weights p(1-p)
        double edf = 0.0;
        double[][] probs = softmaxProbs(y, K, n, beta, xRow, Zc, cont.length, W, phase, phiScale, oh, disc, rows);

        for (int c = 0; c < C; c++) {
            DMatrixRMaj G = new DMatrixRMaj(M, M);
            for (int i = 0; i < n; i++) {
                buildXRowMixed(xRow, i, Zc, cont.length, W, phase, phiScale, oh, disc, rows);
                double pc = probs[i][c + 1];
                double wc = pc * (1.0 - pc);
                wc = max(wc, 1e-10);

                for (int a = 0; a < M; a++) {
                    double pa = wc * xRow[a];
                    for (int b = 0; b <= a; b++) G.add(a, b, pa * xRow[b]);
                }
            }
            for (int a = 0; a < M; a++) for (int b = 0; b < a; b++) G.set(b, a, G.get(a, b));
            for (int a = 0; a < M; a++) G.add(a, a, ridge);

            CholeskyDecomposition_F64<DMatrixRMaj> chol = DecompositionFactory_DDRM.chol(true);
            if (!chol.decompose(G)) return new FitResult(Double.NaN, Double.NaN);
            DMatrixRMaj L = chol.getT(null);

            double trInv = traceInvFromCholeskyLower(L);
            double edfC = M - ridge * trInv;
            if (!(edfC >= 0) || !Double.isFinite(edfC)) edfC = M;
            edf += edfC;
        }

        return new FitResult(ll, edf);
    }

    // -------------------- extraction --------------------

    private void buildXRowMixed(double[] out, int i,
                                double[][] Zc, int dCont,
                                double[][] W, double[] phase, double phiScale,
                                OneHotSpec oh, int[] discParents, int[] rows) {
        // RFF part
        if (dCont == 0) {
            Arrays.fill(out, 0, rffFeatures, 0.0);
        } else {
            for (int k = 0; k < rffFeatures; k++) {
                double dot = 0.0;
                double[] wk = W[k];
                for (int j = 0; j < dCont; j++) dot += wk[j] * Zc[i][j];
                out[k] = phiScale * cos(dot + phase[k]);
            }
        }

        // one-hot part
        Arrays.fill(out, rffFeatures, out.length, 0.0);

        if (discParents.length == 0) return;

        int row = (rows == null) ? i : rows[i];
        for (int t = 0; t < discParents.length; t++) {
            int var = discParents[t];
            int lev = dataSet.getInt(row, var);
            if (lev == DiscreteVariable.MISSING_VALUE) continue;
            if (lev <= 0) continue; // baseline dropped
            int col = oh.offsets[t] + (lev - 1);
            if (col >= oh.offsets[t] && col < oh.offsets[t] + oh.sizes[t] - 1) {
                out[rffFeatures + col] = 1.0;
            }
        }
    }

    private double[][] softmaxProbs(int[] y, int K, int n,
                                    double[][] beta, double[] xRow,
                                    double[][] Zc, int dCont,
                                    double[][] W, double[] phase, double phiScale,
                                    OneHotSpec oh, int[] disc, int[] rows) {

        int C = K - 1;
        double[][] p = new double[n][K];

        for (int i = 0; i < n; i++) {
            buildXRowMixed(xRow, i, Zc, dCont, W, phase, phiScale, oh, disc, rows);

            // logits: class0 = 0, class k>0 = x^T beta_k
            double maxLog = 0.0;
            double[] logit = new double[K];
            logit[0] = 0.0;

            for (int c = 0; c < C; c++) {
                double s = 0.0;
                for (int a = 0; a < xRow.length; a++) s += xRow[a] * beta[a][c];
                logit[c + 1] = s;
                if (s > maxLog) maxLog = s;
            }

            double sum = 0.0;
            for (int k = 0; k < K; k++) {
                double e = exp(logit[k] - maxLog);
                p[i][k] = e;
                sum += e;
            }
            double inv = 1.0 / sum;
            for (int k = 0; k < K; k++) p[i][k] *= inv;
        }

        return p;
    }

    private double multinomialLogLik(int[] y, int K, int n,
                                     double[][] beta, double[] xRow,
                                     double[][] Zc, int dCont,
                                     double[][] W, double[] phase, double phiScale,
                                     OneHotSpec oh, int[] disc, int[] rows) {
        double ll = 0.0;
        int C = K - 1;

        for (int i = 0; i < n; i++) {
            buildXRowMixed(xRow, i, Zc, dCont, W, phase, phiScale, oh, disc, rows);

            double[] logit = new double[K];
            logit[0] = 0.0;
            double maxLog = 0.0;

            for (int c = 0; c < C; c++) {
                double s = 0.0;
                for (int a = 0; a < xRow.length; a++) s += xRow[a] * beta[a][c];
                logit[c + 1] = s;
                if (s > maxLog) maxLog = s;
            }

            double sum = 0.0;
            for (int k = 0; k < K; k++) sum += exp(logit[k] - maxLog);

            ll += (logit[y[i]] - maxLog) - log(sum);
        }

        return ll;
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
            // drop baseline => (K-1) cols (if K>=2)
            off += max(0, K - 1);
        }
        return new OneHotSpec(sizes, offsets, off);
    }

    // -------------------- Student-t loglik + gamma --------------------

    private int numCategories(int varIndex) {
        Node v = variables.get(varIndex);
        if (!(v instanceof DiscreteVariable dv)) return 0;
        return dv.getNumCategories();
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

    // -------------------- linear algebra helpers --------------------

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

    // -------------------- type utilities --------------------

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

    private int[] filterDiscrete(int[] cols) {
        int c = 0;
        for (int v : cols) if (isDiscrete(v)) c++;
        int[] out = new int[c];
        int k = 0;
        for (int v : cols) if (isDiscrete(v)) out[k++] = v;
        return out;
    }

    // -------------------- cache utils --------------------

    private void resetCache() {
        localScoreCacheRef.set(new ConcurrentHashMap<>());
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

    public int[] append(int[] z, int x) {
        int[] out = Arrays.copyOf(z, z.length + 1);
        out[z.length] = x;
        return out;
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
}