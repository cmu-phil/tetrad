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
 * <p><b>Legendre BIC score</b></p>
 *
 * <p>
 * Local BIC-style score for structure learning with mixed continuous and discrete variables.
 * Continuous children use a Student-t location model fit via IRLS with ridge regularization;
 * discrete children use multinomial logistic regression fit via IRLS with ridge.
 * </p>
 *
 * <p>
 * Continuous parents enter through additive Legendre basis expansions:
 * for each continuous parent X (globally z-scored), we map to [-1,1] and include P1(x)..Pt(x),
 * where t = legendreDegree. (Intercept handled separately.)
 * Discrete parents enter via baseline-dropped one-hot blocks.
 * </p>
 *
 * <p><b>Missing data:</b> Rows with missing in {Y} ∪ Pa(Y) are dropped locally.</p>
 *
 * <p><b>Score:</b> score = logLik_hat - 0.5 * penaltyDiscount * edf * log(n)</p>
 *
 * <p>
 * Key fixes vs your pasted version:
 * <ul>
 *   <li><b>No hard NaN cutoff at n&lt;10</b>. We only require n>=5; otherwise return a finite intercept-only fallback.</li>
 *   <li><b>Robust mapping to [-1,1]</b> defaults to quantile-based min/max (1%..99%) rather than raw global min/max.</li>
 *   <li><b>Jitter-on-Cholesky-failure</b> for both Student-t and multinomial IRLS normal equations.</li>
 *   <li><b>Stable cache key includes knob signature</b>; any knob change resets caches.</li>
 * </ul>
 * </p>
 */
public final class LegendreBicScore implements Score, EffectiveSampleSizeSettable {

    // -------------------- data --------------------
    private final DataSet dataSet;
    private final List<Node> variables;
    private final int sampleSize;
    private final boolean calculateRowSubsets;

    /**
     * Continuous columns z-scored globally (NaNs preserved). Discrete cols are all NaN.
     */
    private final double[][] zCols;
    // -------------------- caches --------------------
    private final AtomicReference<ConcurrentHashMap<Long, LocalFit>> localFitCacheRef =
            new AtomicReference<>(new ConcurrentHashMap<>());
    // per-variable raw min/max of zCols (continuous vars only; NaNs ignored)
    private final double[] zMin;

    // -------------------- knobs --------------------
    private final double[] zMax;
    // per-variable robust quantile lo/hi (continuous vars only; NaNs ignored)
    private final double[] zQlo;
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
    private volatile boolean useInteractions = false;
    /**
     * Only the first K continuous parents (in parentIdx order) participate in interactions.
     */
    private volatile int interactionMaxParents = 5;
    /**
     * Minimum n to attempt a nontrivial fit.
     */
    private volatile int minN = 5;
    private volatile LegendreMapMode legendreMapMode = LegendreMapMode.ROBUST_MINMAX_Z;
    // mapping quantiles (only used for ROBUST_MINMAX_Z)
    private volatile double mapLoQ = 0.01;
    private volatile double mapHiQ = 0.99;
    // -------------------- ctor --------------------
    public LegendreBicScore(DataSet dataSet) {
        if (dataSet == null) throw new NullPointerException("dataSet");
        this.dataSet = dataSet;
        this.variables = new ArrayList<>(dataSet.getVariables());
        this.sampleSize = dataSet.getNumRows();
        this.calculateRowSubsets = dataSet.existsMissingValue();

        setEffectiveSampleSize(-1);

        // reasonable default ridge ~ 1/n
        this.ridge = 1.0 / Math.max(1, this.sampleSize);

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

    // -------------------- Score interface --------------------

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

    // -------------------- EffectiveSampleSizeSettable --------------------

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

    // -------------------- public knob setters --------------------

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
    private static CholeskyDecomposition_F64<DMatrixRMaj> cholWithJitter(DMatrixRMaj G, double base) {
        CholeskyDecomposition_F64<DMatrixRMaj> chol = DecompositionFactory_DDRM.chol(true);
        if (chol.decompose(G)) return chol;

        double jitter = Math.max(base, 1e-12);
        for (int attempt = 0; attempt < 5; attempt++) {
            jitter *= 10.0;
            for (int a = 1; a < G.numRows; a++) G.add(a, a, jitter); // keep intercept unpenalized
            chol = DecompositionFactory_DDRM.chol(true);
            if (chol.decompose(G)) return chol;
        }
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

    // small helper (put near other helpers)
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

    @Override
    public double localScore(int i, int... parents) {
        LocalFit fit = localFit(i, parents);
        if (!Double.isFinite(fit.logLik) || !Double.isFinite(fit.edf) || fit.nUsed < 2) return Double.NaN;
        return fit.logLik - 0.5 * penaltyDiscount * fit.edf * Math.log(Math.max(2, fit.nUsed));
    }

    @Override
    public double localScoreDiff(int x, int y, int[] z) {
        // standard definition: localScore(y | z, x) - localScore(y | z)
        return localScore(y, append(z, x)) - localScore(y, z);
    }

    @Override
    public List<Node> getVariables() {
        return new ArrayList<>(variables);
    }

    // -------------------- core scoring: localFit --------------------

    @Override
    public int getSampleSize() {
        return dataSet.getNumRows();
    }

    // ============================================================================================
    // Continuous child: Student-t IRLS ridge on features [Legendre(cont parents), OneHot(disc parents)]
    // ============================================================================================

    @Override
    public String toString() {
        return "Legendre BIC score";
    }

    // ============================================================================================
    // Discrete child: multinomial logistic ridge on features [Legendre(cont parents), OneHot(disc parents)]
    // ============================================================================================

    public DataModel getDataModel() {
        return dataSet;
    }

    // ============================================================================================
    // Feature map: intercept + Legendre blocks + (optional) pairwise x interactions + one-hot blocks
    // ============================================================================================

    @Override
    public int getEffectiveSampleSize() {
        return nEff;
    }

    // ============================================================================================
    // Missing rows, extraction, mapping, utilities
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

    public void setPenaltyDiscount(double penaltyDiscount) {
        if (!(penaltyDiscount > 0.0) || !Double.isFinite(penaltyDiscount))
            throw new IllegalArgumentException("Penalty discount must be finite and > 0");
        this.penaltyDiscount = penaltyDiscount;
        resetCache();
    }

    public void setUseInteractions(boolean useInteractions) {
        this.useInteractions = useInteractions;
        resetCache();
    }

    public void setInteractionMaxParents(int k) {
        this.interactionMaxParents = Math.max(0, k);
        resetCache();
    }

    public void setMinN(int minN) {
        this.minN = Math.max(2, minN);
        resetCache();
    }

    public void setLegendreMapMode(String mode) {
        this.legendreMapMode = LegendreMapMode.valueOf(mode);
        resetCache();
    }

    // -------------------- multinomial helpers --------------------

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

        final int kInt = (useInteractions ? Math.min(cont.length, interactionMaxParents) : 0);
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
        if (Math.abs(scaleHat - 1.0) < 1e-12) scaleHat = warmStartScale(yCentered, n);

        double[] beta = new double[M];
        double prevObj = Double.POSITIVE_INFINITY;

        // Scratch buffers (reuse)
        final double[] xRow = new double[M];
        final double[] v = new double[M];

        for (int iter = 0; iter < irlsIters; iter++) {

            final DMatrixRMaj G = new DMatrixRMaj(M, M);
            Arrays.fill(v, 0.0);

            // Build normal equations (weighted ridge)
            for (int i = 0; i < n; i++) {
                buildXRow_Intercept_Legendre(xRow, i, Xmap, cont.length, t, oh, disc, rows);

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

            final CholeskyDecomposition_F64<DMatrixRMaj> chol = cholWithJitter(G, ridge);
            if (chol == null) return new FitResult(Double.NaN, Double.NaN);
            final DMatrixRMaj L = chol.getT(null);

            beta = solveFromCholeskyLower(L, v);

            // Update weights + profiled scale
            double obj = 0.0;
            double wsum = 0.0;
            double wrss = 0.0;

            for (int i = 0; i < n; i++) {
                buildXRow_Intercept_Legendre(xRow, i, Xmap, cont.length, t, oh, disc, rows);

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
            double mu = 0.0;
            for (int a = 0; a < M; a++) mu += xRow[a] * beta[a];
            yhat[i] = mu;
        }

        final double ll = studentTLogLik(yCentered, yhat, nu, scaleHat);

        // EDF: approximate ridge smoother trace using last weights
        final DMatrixRMaj Gfinal = new DMatrixRMaj(M, M);
        for (int i = 0; i < n; i++) {
            buildXRow_Intercept_Legendre(xRow, i, Xmap, cont.length, legendreDegree, oh, disc, rows);
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
        double edf = 1.0 + (Mp - ridge * trInvPen);
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

        final int kInt = (useInteractions ? Math.min(cont.length, interactionMaxParents) : 0);
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

                symmetrizeLowerToFull(G);
                addRidgeToDiagonal(G, ridge, /*skipIntercept=*/true);

                final CholeskyDecomposition_F64<DMatrixRMaj> chol = cholWithJitter(G, ridge);
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
                wc = Math.max(wc, 1e-10);

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

        final int kInt = (useInteractions ? Math.min(dCont, interactionMaxParents) : 0);
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
            case SCALE_DOWN:
                final double lo = zQlo[varIndex];
                final double hi = zQhi[varIndex];
                final double max = Math.max(Math.abs(lo), Math.abs(hi));
                return clamp(z / max);
//                if (!Double.isFinite(lo) || !Double.isFinite(hi) || !(hi > lo)) {
//                    // fall back to raw min/max then clip
//                    final double lo2 = zMin[varIndex];
//                    final double hi2 = zMax[varIndex];
//                    if (Double.isFinite(lo2) && Double.isFinite(hi2) && hi2 > lo2) {
//                        double x = (2.0 * (z - lo2) / (hi2 - lo2)) - 1.0;
//                        return clamp(x);
//                    }
//                    return clamp(z / legendreClip);
//                }
//                double x = (2.0 * (z - lo) / (hi - lo)) - 1.0;
//                return clamp(x);
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
            off += Math.max(0, K - 1);
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
                    double rms = Math.sqrt(Math.max(1e-12, s2 / Math.max(1, n)));
                    if (Math.abs(scaleHat - 1.0) < 1e-12) scaleHat = rms;

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
                        if (wsum > 0.0) scaleHat = Math.sqrt(Math.max(1e-12, wrss / wsum));
                    }

                    double ll0 = studentTLogLik(y, new double[n], nu, scaleHat);
                    return new LocalFit(ll0, 1.0, n);
                }

                FitResult fit = fitStudentTLegendreRidgeMixed(y, parents, rows, n);
                if (!Double.isFinite(fit.logLik()) || !Double.isFinite(fit.edf())) {
                    // fallback to intercept-only (finite)
                    double scaleHat = Math.sqrt(Math.max(1e-12, sumsq(y) / Math.max(1, n)));
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

    // keep an explicit append to avoid relying on Score default in old codepaths
    public int[] append(int[] z, int x) {
        int[] out = Arrays.copyOf(z, z.length + 1);
        out[z.length] = x;
        return out;
    }

    // ---- Legendre domain mapping mode ----
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

    public record LocalFit(double logLik, double edf, int nUsed) {
    }

    private record FitResult(double logLik, double edf) {
    }
}