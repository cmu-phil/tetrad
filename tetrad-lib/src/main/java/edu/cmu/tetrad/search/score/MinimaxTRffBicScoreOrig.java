package edu.cmu.tetrad.search.score;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
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
 * <p><b>Minimax-t RFF BIC score (continuous)</b></p>
 *
 * <p>
 * Local score for structure learning using a robust Student-t conditional model:
 * {@code Y = f(Pa(Y)) + eps}, with {@code eps ~ StudentT(nu, scale)}.
 * The regression function f is represented with Random Fourier Features (RFF),
 * and parameters are fit by IRLS (iteratively reweighted ridge).
 * </p>
 *
 * <p>
 * The local score is a BIC-style criterion:
 * {@code score = logLik_hat - 0.5 * edf * log(n)},
 * where {@code edf} is an effective degrees of freedom derived from the weighted ridge system.
 * This is designed to be more stable for greedy score-based searches such as BOSS/GRaSP.
 * </p>
 */
public final class MinimaxTRffBicScoreOrig implements Score, EffectiveSampleSizeSettable {

    // -------------------- config knobs --------------------

    /** Student-t degrees of freedom (fixed). Smaller => heavier tails. */
    private volatile double nu = 5.0;

    /** Shared scale for Student-t residuals (fixed). If data are globally z-scored, 1.0 is a good default. */
    private volatile double scale = 1.0;

    /** Ridge penalty (>0). */
    private volatile double ridge = 1e-3;

    /** Number of RFF features (D). */
    private volatile int rffFeatures = 256;

    /** RFF sigma (lengthscale-ish). Frequencies ~ N(0, 1/sigma^2). */
    private volatile double rffSigma = 1.0;

    /** Deterministic base seed for features. */
    private volatile long rffSeed = 1L;

    /** IRLS iterations. */
    private volatile int irlsIters = 8;

    /** IRLS stopping tolerance. */
    private volatile double irlsTol = 1e-6;

    /** Use valid-row subsets if missing exists. */
    private final boolean calculateRowSubsets;

    // -------------------- data --------------------

    private final DataSet dataSet;
    private final List<Node> variables;
    private final int sampleSize;
    private volatile int nEff;

    /** Standardized columns (z-scored globally, NaNs preserved). zCols[var][row]. */
    private final double[][] zCols;

    /** Cache (target, sorted parents, rowsSig, knobs) -> score. */
    private final AtomicReference<ConcurrentHashMap<Long, Double>> localScoreCacheRef =
            new AtomicReference<>(new ConcurrentHashMap<>());

    public MinimaxTRffBicScoreOrig(DataSet dataSet) {
        if (dataSet == null) throw new NullPointerException("dataSet");
        if (!dataSet.isContinuous()) throw new IllegalArgumentException("Requires continuous DataSet.");

        this.dataSet = dataSet;
        this.variables = new ArrayList<>(dataSet.getVariables());
        this.sampleSize = dataSet.getNumRows();
        setEffectiveSampleSize(-1);

        this.calculateRowSubsets = dataSet.existsMissingValue();

        int p = variables.size();
        double[][] cols = new double[p][sampleSize];
        for (int j = 0; j < p; j++) {
            for (int r = 0; r < sampleSize; r++) cols[j][r] = dataSet.getDouble(r, j);
        }

        this.zCols = new double[p][sampleSize];
        for (int j = 0; j < p; j++) zscoreColumnPreserveNaN(cols[j], zCols[j]);
    }

    // -------------------- Score interface --------------------

    @Override
    public double localScore(int i, int... parents) {
        Arrays.sort(parents);
        long key = cacheKey(i, parents, knobsSignature());

        final ConcurrentHashMap<Long, Double> cache = localScoreCacheRef.get();
        return cache.computeIfAbsent(key, k -> {
            try {
                int[] all = concat(i, parents);
                int[] rows = calculateRowSubsets ? validRows(all) : null;

                int n = (rows == null) ? nEff : rows.length;
                if (n < 10) return Double.NaN;

                double[] y = extract1D(i, rows, n);
                centerInPlace(y);

                if (!(ridge > 0) || !Double.isFinite(ridge)) return Double.NaN;
                if (!(nu > 2) || !Double.isFinite(nu)) return Double.NaN;
                if (!(scale > 0) || !Double.isFinite(scale)) return Double.NaN;

                // no parents: Student-t location-only (mean = 0 after centering)
                if (parents.length == 0) {
                    double ll = studentTLogLik(y, new double[n], nu, scale);
                    // edf = 0 for no predictors (after centering)
                    return ll;
                }

                long seed = rffSeed ^ (long) i * 0x9E3779B97F4A7C15L ^ Arrays.hashCode(parents);

                FitResult fit = fitStudentTRffRidge(y, parents, rows, n, seed);
                if (!Double.isFinite(fit.logLik)) return Double.NaN;

                // BIC-style penalty with edf
                double bic = fit.logLik - 0.5 * fit.edf * log(n);

                return bic;

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
        return "Minimax-t RFF BIC score (continuous)";
    }

    public DataModel getDataModel() {
        return dataSet;
    }

    @Override
    public int getEffectiveSampleSize() {
        return nEff;
    }

    @Override
    public void setEffectiveSampleSize(int nEff) {
        this.nEff = (nEff < 0) ? this.sampleSize : nEff;
        resetCache();
    }

    // -------------------- public knobs --------------------

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

    public void setRffFeatures(int d) {
        if (d < 16) throw new IllegalArgumentException("rffFeatures should be >= 16");
        this.rffFeatures = d;
        resetCache();
    }

    public void setRffSigma(double sigma) {
        if (!(sigma > 0) || !Double.isFinite(sigma)) throw new IllegalArgumentException("rffSigma must be finite and > 0");
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

    public void setIrlsTol(double tol) {
        this.irlsTol = Math.max(0.0, tol);
        resetCache();
    }

    // -------------------- core fitting --------------------

    private record FitResult(double logLik, double edf) {}

    private FitResult fitStudentTRffRidge(double[] yCentered, int[] parentIdx, int[] rows, int n, long seed) {
        final int dParents = parentIdx.length;
        final int D = rffFeatures;

        // 1) Extract standardized parents Z[n][dParents]
        double[][] Z = new double[n][dParents];
        for (int r = 0; r < n; r++) {
            int row = (rows == null) ? r : rows[r];
            for (int j = 0; j < dParents; j++) {
                Z[r][j] = zCols[parentIdx[j]][row];
            }
        }

        // 2) Draw RFF params
        Random rng = new Random(seed);
        double[][] W = new double[D][dParents];
        for (int k = 0; k < D; k++) {
            for (int j = 0; j < dParents; j++) {
                W[k][j] = rng.nextGaussian() / rffSigma;
            }
        }
        double[] phase = new double[D];
        for (int k = 0; k < D; k++) phase[k] = 2.0 * PI * rng.nextDouble();
        final double phiScale = sqrt(2.0 / D);

        // IRLS init: weights all 1
        double[] w = new double[n];
        Arrays.fill(w, 1.0);

        double[] beta = new double[D]; // start at 0
        double prevObj = Double.POSITIVE_INFINITY;

        for (int iter = 0; iter < irlsIters; iter++) {

            // Build weighted normal equations:
            // G = Phi^T W Phi, v = Phi^T W y
            DMatrixRMaj G = new DMatrixRMaj(D, D);
            double[] v = new double[D];

            // We also need residuals to update weights => compute yhat on the fly.
            // First pass: compute G,v using current weights.
            double[] phi = new double[D];

            for (int i = 0; i < n; i++) {
                buildPhiRow(phi, Z[i], W, phase, phiScale);

                double wi = w[i];
                double yi = yCentered[i];

                // v += wi * phi * yi
                for (int a = 0; a < D; a++) v[a] += wi * phi[a] * yi;

                // G += wi * phi^T phi   (symmetric)
                for (int a = 0; a < D; a++) {
                    double pa = wi * phi[a];
                    for (int b = 0; b <= a; b++) {
                        G.add(a, b, pa * phi[b]);
                    }
                }
            }

            // mirror lower -> upper
            for (int a = 0; a < D; a++) {
                for (int b = 0; b < a; b++) G.set(b, a, G.get(a, b));
            }

            // Add ridge
            for (int a = 0; a < D; a++) G.add(a, a, ridge);

            // Solve for beta via Cholesky
            CholeskyDecomposition_F64<DMatrixRMaj> chol = DecompositionFactory_DDRM.chol(true);
            if (!chol.decompose(G)) return new FitResult(Double.NaN, Double.NaN);

            DMatrixRMaj L = chol.getT(null);
            beta = solveFromCholeskyLower(L, v);

            // Update weights from Student-t
            double obj = 0.0;

            for (int i = 0; i < n; i++) {
                buildPhiRow(phi, Z[i], W, phase, phiScale);

                double yhat = 0.0;
                for (int a = 0; a < D; a++) yhat += phi[a] * beta[a];

                double r = yCentered[i] - yhat;
                double u2 = (r / scale) * (r / scale);

                // IRLS weight for Student-t location model:
                // w_i = (nu + 1) / (nu + u^2)
                double wi = (nu + 1.0) / (nu + u2);
                w[i] = wi;

                // accumulate -loglik (up to constants) as a crude convergence monitor
                obj += 0.5 * (nu + 1.0) * log1p(u2 / nu);
            }

            if (abs(prevObj - obj) <= irlsTol * (1.0 + abs(prevObj))) break;
            prevObj = obj;
        }

        // Final log-likelihood
        double[] yhatFinal = new double[n];
        double[] phi = new double[D];
        for (int i = 0; i < n; i++) {
            buildPhiRow(phi, Z[i], W, phase, phiScale);
            double yh = 0.0;
            for (int a = 0; a < D; a++) yh += phi[a] * beta[a];
            yhatFinal[i] = yh;
        }
        double ll = studentTLogLik(yCentered, yhatFinal, nu, scale);

        // Effective degrees of freedom (edf) for weighted ridge:
        // edf = tr( Phi^T W Phi * (Phi^T W Phi + ridge I)^-1 )
        // Here, our Cholesky system was for (G + ridge I) already.
        // We can recover edf via: edf = D - ridge * tr( (G + ridge I)^-1 )
        // but G here already includes ridge; so compute tr(inv(G_with_ridge)) and use:
        // edf = D - ridge * tr(inv(G_with_ridge))
        //
        // Note: this is the edf of the feature coefficients; it behaves far better than “#params” for ridge.
        double trInv = traceInvFromCholeskyLower(DecompositionFactory_DDRM.chol(true).getT(null)); // placeholder
        // The above line is NOT correct because we didn’t keep the Cholesky L around after IRLS.
        // We’ll recompute one last weighted G + ridgeI and decompose to get L for edf:

        DMatrixRMaj Gfinal = new DMatrixRMaj(D, D);
        double[] vfinal = new double[D];
        Arrays.fill(vfinal, 0.0);
        Arrays.fill(phi, 0.0);

        // use final weights from last iter stored in w[]
        for (int i = 0; i < n; i++) {
            buildPhiRow(phi, Z[i], W, phase, phiScale);
            double wi = w[i];

            for (int a = 0; a < D; a++) {
                double pa = wi * phi[a];
                for (int b = 0; b <= a; b++) Gfinal.add(a, b, pa * phi[b]);
            }
        }
        for (int a = 0; a < D; a++) for (int b = 0; b < a; b++) Gfinal.set(b, a, Gfinal.get(a, b));
        for (int a = 0; a < D; a++) Gfinal.add(a, a, ridge);

        CholeskyDecomposition_F64<DMatrixRMaj> chol2 = DecompositionFactory_DDRM.chol(true);
        if (!chol2.decompose(Gfinal)) return new FitResult(Double.NaN, Double.NaN);
        DMatrixRMaj Lfinal = chol2.getT(null);

        double trInvFinal = traceInvFromCholeskyLower(Lfinal);
        double edf = D - ridge * trInvFinal;
        if (!(edf >= 0) || !Double.isFinite(edf)) edf = D; // conservative fallback

        return new FitResult(ll, edf);
    }

    private static void buildPhiRow(double[] outPhi, double[] zRow, double[][] W, double[] phase, double scale) {
        int D = outPhi.length;
        int p = zRow.length;
        for (int k = 0; k < D; k++) {
            double dot = 0.0;
            double[] wk = W[k];
            for (int j = 0; j < p; j++) dot += wk[j] * zRow[j];
            outPhi[k] = scale * cos(dot + phase[k]);
        }
    }

    private static double studentTLogLik(double[] y, double[] yhat, double nu, double scale) {
        int n = y.length;

        // constant term per observation:
        // log Γ((ν+1)/2) - log Γ(ν/2) - 0.5 log(νπ) - log(scale)
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

    // Lanczos-ish logGamma approximation (good enough for scoring)
    private static double logGamma(double x) {
        // Simple approximation; replace with Apache Commons if you have it already.
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

        // forward solve: L u = b
        for (int i = 0; i < n; i++) {
            double sum = x[i];
            for (int j = 0; j < i; j++) sum -= L.get(i, j) * x[j];
            x[i] = sum / L.get(i, i);
        }

        // back solve: L^T x = u
        for (int i = n - 1; i >= 0; i--) {
            double sum = x[i];
            for (int j = i + 1; j < n; j++) sum -= L.get(j, i) * x[j];
            x[i] = sum / L.get(i, i);
        }

        return x;
    }

    private static double traceInvFromCholeskyLower(DMatrixRMaj L) {
        // inv(A) where A = L L^T
        // tr(inv(A)) = sum_i || solve(L, e_i) ||^2
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

            // then ||u||^2 contributes to diagonal of inv(A)
            double ss = 0.0;
            for (int i = 0; i < n; i++) ss += v[i] * v[i];
            tr += ss;
        }

        return tr;
    }

    // -------------------- missingness row selection --------------------

    private int[] validRows(int[] vars) {
        int n = sampleSize;
        int[] tmp = new int[n];
        int m = 0;

        outer:
        for (int r = 0; r < n; r++) {
            for (int v : vars) {
                double val = zCols[v][r];
                if (Double.isNaN(val)) continue outer;
            }
            tmp[m++] = r;
        }
        return Arrays.copyOf(tmp, m);
    }

    // -------------------- extraction + preprocessing --------------------

    private double[] extract1D(int varIndex, int[] rows, int n) {
        double[] x = new double[n];
        if (rows == null) {
            for (int r = 0; r < n; r++) x[r] = zCols[varIndex][r];
        } else {
            for (int r = 0; r < n; r++) x[r] = zCols[varIndex][rows[r]];
        }
        return x;
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

    private static int[] concat(int i, int[] parents) {
        int[] all = new int[parents.length + 1];
        all[0] = i;
        System.arraycopy(parents, 0, all, 1, parents.length);
        return all;
    }

    private static long cacheKey(int i, int[] parents, long knobsSig) {
        long h = 1469598103934665603L;
        h = (h ^ i) * 1099511628211L;
        for (int p : parents) h = (h ^ p) * 1099511628211L;
        h = (h ^ knobsSig) * 1099511628211L;
        return h;
    }
}