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

    // -------------------- ctor --------------------

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

    public DataModel getDataModel() {
        return dataSet;
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

                if (isDiscrete(i)) {
                    // -------- discrete child: multinomial logistic ridge --------
                    int[] y = extractDiscreteChild(i, rows, n);
                    int K = numCategories(i);
                    if (K < 2) return Double.NaN;

                    if (parents.length == 0) {
                        double ll = multinomialInterceptOnlyLogLik(y, K);
                        double bic = ll - 0.5 * (K - 1.0) * log(n);
                        return bic;
                    }

                    FitResult fit = fitMultinomialLogitMixed(i, y, K, parents, rows, n);
                    if (!Double.isFinite(fit.logLik)) return Double.NaN;
                    return fit.logLik - 0.5 * fit.edf * log(n);

                } else {
                    // -------- continuous child: Student-t Legendre ridge --------
                    if (!(nu > 2) || !Double.isFinite(nu)) return Double.NaN;
                    if (!(scale > 0) || !Double.isFinite(scale)) return Double.NaN;

                    double[] y = extractContinuousChild(i, rows, n);
//                    centerInPlace(y); // let's turn this centering of y off...

                    if (parents.length == 0) {
                        // profile scale for intercept-only so it’s comparable with parent models
                        double scaleHat = this.scale;

                        // good initial guess: RMS of y (centered already)
                        double s2 = 0.0;
                        for (double v : y) s2 += v * v;
                        scaleHat = Math.sqrt(Math.max(1e-12, s2 / n));

                        // 1–2 fixed-point refinements using t-weights (cheap, stabilizes)
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
                        double edf0 = 1.0; // if you're counting intercept (even though y is centered)
                        return ll0 - 0.5 * penaltyDiscount * edf0 * log(n);
                    }

                    FitResult fit = fitStudentTLegendreRidgeMixed(y, parents, rows, n);
                    if (!Double.isFinite(fit.logLik)) return Double.NaN;
                    double v = fit.logLik - 0.5 * penaltyDiscount * fit.edf * log(n);
//                    double parentBonus = 1e-4 * log(n); // Try?
//                    v += parentBonus * parents.length;
                    return v;
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

    // ============================================================================================
    // Continuous child: Student-t IRLS ridge on features [Legendre(cont parents), OneHot(disc parents)]
    // ============================================================================================

    @Override
    public List<Node> getVariables() {
        return new ArrayList<>(variables);
    }

    @Override
    public int getSampleSize() {
        return dataSet.getNumRows();
    }

    // ============================================================================================
    // Discrete child: multinomial logistic ridge on features [Legendre(cont parents), OneHot(disc parents)]
    // ============================================================================================

    @Override
    public String toString() {
        return "Minimax-t Legendre BIC score (mixed)";
    }

    @Override
    public int getEffectiveSampleSize() {
        return nEff;
    }

    // ============================================================================================
    // Helpers
    // ============================================================================================

    @Override
    public void setEffectiveSampleSize(int nEff) {
        this.nEff = (nEff < 0) ? this.sampleSize : nEff;
        resetCache();
    }

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

    public void setRidge(double ridge) {
        if (!(ridge > 0) || !Double.isFinite(ridge)) throw new IllegalArgumentException("ridge must be finite and > 0");
        this.ridge = ridge;
        resetCache();
    }

    public void setLegendreDegree(int t) {
        if (t < 1) throw new IllegalArgumentException("legendreDegree must be >= 1");
        this.legendreDegree = t;
        resetCache();
    }

    public void setLegendreClip(double clip) {
        if (!(clip > 0) || !Double.isFinite(clip))
            throw new IllegalArgumentException("legendreClip must be finite and > 0");
        this.legendreClip = clip;
        resetCache();
    }

    public void setIrlsIters(int iters) {
        this.irlsIters = Math.max(1, iters);
        resetCache();
    }

    public void setIrlsTol(double tol) {
        this.irlsTol = Math.max(0.0, tol);
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
        final int D = cont.length * t;          // additive Legendre: t per cont parent
        final int Q = oh.totalCols;
        final int M = 1 + D + Q;               // intercept + legendre + one-hot

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

        // Legendre block starts at 1
        final int legOff = 1;

        // Fill Legendre block
        int pos = legOff;
        final double invClip = 1.0 / legendreClip;

        for (int j = 0; j < dCont; j++) {
            double z = Zc[i][j];
            // map to [-1,1]
            double x = z * invClip;
            if (x > 1.0) x = 1.0;
            else if (x < -1.0) x = -1.0;

            // Legendre recurrence:
            // P0=1, P1=x, Pn = ((2n-1)x P_{n-1} - (n-1)P_{n-2})/n
            double Pnm2 = 1.0;   // P0
            double Pnm1 = x;     // P1

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

        // one-hot block
        final int ohOff = 1 + dCont * t;
        Arrays.fill(out, ohOff, out.length, 0.0);

        if (discParents.length == 0) return;

        final int row = (rows == null) ? i : rows[i];
        for (int parentPos = 0; parentPos < discParents.length; parentPos++) {
            final int var = discParents[parentPos];
            final int lev = dataSet.getInt(row, var);
            if (lev == DiscreteVariable.MISSING_VALUE) continue;
            if (lev <= 0) continue; // baseline dropped

            final int col = oh.offsets[parentPos] + (lev - 1);
            if (col >= oh.offsets[parentPos] && col < oh.offsets[parentPos] + oh.sizes[parentPos] - 1) {
                out[ohOff + col] = 1.0;
            }
        }
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
        final int D = cont.length * t;
        final int Q = oh.totalCols;
        final int M = 1 + D + Q;
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

    public void setPenaltyDiscount(double penaltyDiscount) {
        if (!(penaltyDiscount > 0.0) || !Double.isFinite(penaltyDiscount))
            throw new IllegalArgumentException("Penalty discount must be finite and > 0");

        this.penaltyDiscount = penaltyDiscount;
        resetCache();
    }

    private void resetCache() {
        localScoreCacheRef.set(new ConcurrentHashMap<>());
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
        return h;
    }

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

    private record FitResult(double logLik, double edf) {
    }
}