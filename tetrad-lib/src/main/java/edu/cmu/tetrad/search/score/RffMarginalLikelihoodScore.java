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
import java.util.SplittableRandom;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * RFF-ML Score (Random Fourier Features Maximum-Likelihood score).
 * <p>
 * This score is a kernel-based, likelihood-style score for structure learning that approximates
 * nonlinear dependence using Random Fourier Features (RFF). Conceptually, it replaces an explicit
 * kernel representation (e.g., a Gaussian/RBF kernel matrix) with a finite-dimensional feature map
 * so that the resulting computations can be performed using standard linear-algebra operations.
 * <p>
 * At a high level, for a target variable {@code Y} and a candidate parent set {@code Pa(Y)}, the score:
 * <ul>
 *   <li>Constructs RFF embeddings for {@code Y} and for {@code Pa(Y)} (and any additional variables used by the test/score),
 *       where the embedding approximates an RBF kernel via {@code cos(Wx + b)} features.</li>
 *   <li>Fits a regularized linear model in the embedded space (typically ridge-regularized) to capture
 *       conditional mean structure of {@code Y} given {@code Pa(Y)} in a flexible nonlinear way.</li>
 *   <li>Computes a maximum-likelihood-style objective (or an equivalent quadratic form) from the residual
 *       covariance in the embedded space.</li>
 *   <li>Adds a complexity penalty that depends on sample size and the effective degrees of freedom
 *       (analogous in spirit to BIC-type penalties), so that larger parent sets are penalized.</li>
 * </ul>
 *
 * <h2>Why Random Fourier Features?</h2>
 * RFF provides a scalable approximation to shift-invariant kernels. Instead of forming and factoring
 * an {@code n × n} kernel matrix (which can be expensive in both time and memory), RFF uses an
 * {@code n × m} feature matrix where {@code m} is the number of random features. This allows the
 * score to scale more gracefully with sample size while still capturing nonlinear relationships.
 *
 * <h2>Intended use</h2>
 * This score is designed for use in score-based causal discovery procedures (e.g., FGES/BOSS-style
 * searches), where the score must be evaluated repeatedly for many candidate parent sets. The RFF
 * approximation and internal caching (if enabled) are particularly helpful in that setting.
 *
 * <h2>Important hyperparameters</h2>
 * Typical configuration parameters include:
 * <ul>
 *   <li>{@code numFeatures}: number of random Fourier features (tradeoff between accuracy and speed).</li>
 *   <li>{@code bandwidth / sigma}: kernel bandwidth (often chosen using a median-distance heuristic).</li>
 *   <li>{@code lambda}: ridge regularization strength used for stability in embedded regression/covariance operations.</li>
 *   <li>{@code seed}: random seed controlling the RFF projection (recommended for reproducibility).</li>
 * </ul>
 *
 * <h2>Notes</h2>
 * <ul>
 *   <li>This score is not restricted to linear-Gaussian relationships; it is intended to capture
 *       nonlinear structure through the kernel approximation.</li>
 *   <li>Like other kernel/RFF methods, performance can be sensitive to bandwidth and feature count.</li>
 *   <li>Reproducibility requires fixing the random seed (and, if caching is used, ensuring cache keys
 *       incorporate relevant aspects such as variable sets and active rows).</li>
 * </ul>
 *
 * @see edu.cmu.tetrad.search.score.Score
 */
public final class RffMarginalLikelihoodScore implements Score, EffectiveSampleSizeSettable {

    // -------------------- configuration knobs --------------------

    /**
     * Base ridge/noise knob. Used to form sigma^2. Must be > 0.
     */
    private volatile double lambda = 1.0;

    /**
     * Jitter escalation base for Cholesky stabilization. Must be > 0.
     */
    private volatile double jitter = 1e-10;

    /**
     * If true, use valid row subsets when missing exists.
     */
    private final boolean calculateRowSubsets;

    /**
     * Bandwidth multiplier on the median heuristic.
     * 1.0 is default; try 0.5 or 2.0 if things feel too smooth/too spiky.
     */
    private volatile double bandwidthMultiplier = 1.0;

    /**
     * Max rows used to estimate median bandwidth (subsample for speed).
     * Kernel itself still uses all rows.
     */
    private volatile int bwMaxRows = 400;

    // -------------------- data --------------------

    private final DataSet dataSet;
    private final List<Node> variables;
    private final int sampleSize;
    private volatile int nEff;

    /**
     * Standardized columns (z-scored globally, NaNs preserved). zCols[p][n].
     */
    private final double[][] zCols;

    /**
     * Cache: (target i, sorted parents) -> score.
     */
    private final AtomicReference<ConcurrentHashMap<Long, Double>> localScoreCacheRef =
            new AtomicReference<>(new ConcurrentHashMap<>());

    public RffMarginalLikelihoodScore(DataSet dataSet) {
        if (dataSet == null) throw new NullPointerException("dataSet");
        this.dataSet = dataSet;
        this.variables = dataSet.getVariables();
        this.sampleSize = dataSet.getNumRows();
        setEffectiveSampleSize(-1);

        this.calculateRowSubsets = dataSet.existsMissingValue();

        // Extract + z-score each column once (globally). Keeps kernels sane.
        int p = variables.size();
        double[][] cols = new double[p][sampleSize];
        for (int j = 0; j < p; j++) {
            for (int r = 0; r < sampleSize; r++) cols[j][r] = dataSet.getDouble(r, j);
        }

        this.zCols = new double[p][sampleSize];
        for (int j = 0; j < p; j++) {
            zscoreColumnPreserveNaN(cols[j], zCols[j]);
        }
    }

    // -------------------- Score interface --------------------

    @Override
    public double localScoreDiff(int x, int y, int[] z) {
        return localScore(y, append(z, x)) - localScore(y, z);
    }

    @Override
    public double localScore(int i, int... parents) {
        Arrays.sort(parents);
        long key = cacheKey(i, parents);

        final ConcurrentHashMap<Long, Double> cache = localScoreCacheRef.get();

        return cache.computeIfAbsent(key, k -> {
            try {
                int[] all = concat(i, parents);
                int[] rows = calculateRowSubsets ? validRows(all) : null;

                int n = (rows == null) ? nEff : rows.length;
                if (n < 5) return Double.NaN;

                double[] y = extract1D(i, rows, n);
                centerInPlace(y);

                double sigma2 = lambda;
                if (!(sigma2 > 0) || !Double.isFinite(sigma2)) return Double.NaN;

                // Exact closed-form for no parents: C = sigma2 I
                if (parents.length == 0) {
                    return gpLogMarginalLikelihoodSigmaOnly(y, sigma2);
                }

                // Bandwidth (median heuristic on standardized parents)
                double[][] Z = new double[n][parents.length];
                for (int r = 0; r < n; r++) {
                    int row = (rows == null) ? r : rows[r];
                    for (int j = 0; j < parents.length; j++) {
                        Z[r][j] = zCols[parents[j]][row];
                    }
                }

                double bw2 = medianDistanceSquaredND(Z, Math.min(n, bwMaxRows));
                if (!(bw2 > 0) || !Double.isFinite(bw2)) bw2 = 1.0;
                bw2 *= (bandwidthMultiplier * bandwidthMultiplier);

                // Deterministic seed per (target, parent set).
                long seed = key ^ 0x9E3779B97F4A7C15L;

                int mFeatures = 256; // TODO: make this a knob

                return gpLogMarginalLikelihoodRFF(
                        y, parents, rows, n, mFeatures, bw2, sigma2, seed
                );

            } catch (RuntimeException e) {
                TetradLogger.getInstance().log(e.getMessage());
                return Double.NaN;
            }
        });
    }

    /** Exact sigma-only case: C = sigma2 I. */
    private static double gpLogMarginalLikelihoodSigmaOnly(double[] yCentered, double sigma2) {
        int n = yCentered.length;
        if (n == 0) return Double.NaN;
        if (!(sigma2 > 0) || !Double.isFinite(sigma2)) return Double.NaN;

        double yTy = 0.0;
        for (double v : yCentered) yTy += v * v;

        double quad = yTy / sigma2;
        double logDet = n * Math.log(sigma2);

        return -0.5 * quad - 0.5 * logDet;
    }

    private static DMatrixRMaj buildSigmaOnlyC(int n, double sigma2) {
        if (n <= 0) throw new IllegalArgumentException("n must be > 0");
        if (!(sigma2 > 0) || !Double.isFinite(sigma2)) {
            throw new IllegalArgumentException("sigma2 must be finite and > 0");
        }

        DMatrixRMaj C = new DMatrixRMaj(n, n);
        for (int i = 0; i < n; i++) {
            C.set(i, i, sigma2);
        }
        return C;
    }

    private void resetCache() {
        localScoreCacheRef.set(new ConcurrentHashMap<>());
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
    public int getMaxDegree() {
        return (int) Math.ceil(Math.log(Math.max(5, nEff)));
    }

    @Override
    public boolean determines(List<Node> z, Node y) {
        int i = variables.indexOf(y);
        int[] parents = new int[z.size()];
        for (int t = 0; t < z.size(); t++) parents[t] = variables.indexOf(z.get(t));

        double s = localScore(i, parents);
        return Double.isNaN(s) || Double.isInfinite(s);
    }

    @Override
    public boolean isEffectEdge(double bump) {
        return bump > 0;
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

    @Override
    public String toString() {
        return "Huang Kernel Marginal Score (GP form, continuous)";
    }

    // -------------------- public tuning knobs --------------------

    public double getLambda() {
        return lambda;
    }

    public void setLambda(double lambda) {
        if (lambda <= 0) throw new IllegalArgumentException("lambda must be > 0");
        this.lambda = lambda;
        resetCache();
    }

    public double getJitter() {
        return jitter;
    }

    public void setJitter(double jitter) {
        if (jitter <= 0) throw new IllegalArgumentException("jitter must be > 0");
        this.jitter = jitter;
        resetCache();
    }

    public double getBandwidthMultiplier() {
        return bandwidthMultiplier;
    }

    public void setBandwidthMultiplier(double bandwidthMultiplier) {
        if (!(bandwidthMultiplier > 0) || !Double.isFinite(bandwidthMultiplier)) {
            throw new IllegalArgumentException("bandwidthMultiplier must be > 0");
        }
        this.bandwidthMultiplier = bandwidthMultiplier;
        resetCache();
    }

    public int getBwMaxRows() {
        return bwMaxRows;
    }

    public void setBwMaxRows(int bwMaxRows) {
        this.bwMaxRows = Math.max(50, bwMaxRows);
        resetCache();
    }

    // -------------------- GP marginal likelihood core --------------------

    /**
     * Computes: -0.5*y^T C^{-1} y - 0.5*log|C|
     * using Cholesky with jitter escalation.
     */
    private static double gpLogMarginalLikelihood(double[] y, DMatrixRMaj C, double jitter) {
        final int n = y.length;
        if (C.numRows != n || C.numCols != n) throw new IllegalArgumentException("C dimension mismatch");

        // Try Cholesky with escalating jitter.
        CholeskyDecomposition_F64<DMatrixRMaj> chol = DecompositionFactory_DDRM.chol(true);

        double eps = 0.0;
        DMatrixRMaj Cf = null;

        boolean ok = false;
        for (int k = 0; k < 8; k++) {
            Cf = (k == 0) ? C : C.copy();
            if (k > 0) addDiagonalInPlace(Cf, eps);

            if (chol.decompose(Cf)) {
                ok = true;
                break;
            }
            eps = (eps == 0.0) ? jitter : eps * 10.0;
        }
        if (!ok) return Double.NaN;

        DMatrixRMaj L = chol.getT(null); // lower triangular
        double logDet = 0.0;
        for (int i = 0; i < n; i++) {
            double di = L.get(i, i);
            if (!(di > 0) || !Double.isFinite(di)) return Double.NaN;
            logDet += Math.log(di);
        }
        logDet *= 2.0;

        // Solve C^{-1} y via two triangular solves: L u = y, L^T x = u.
        double[] u = Arrays.copyOf(y, n);

        // Forward solve (L u = y)
        for (int i = 0; i < n; i++) {
            double sum = u[i];
            for (int j = 0; j < i; j++) sum -= L.get(i, j) * u[j];
            double di = L.get(i, i);
            u[i] = sum / di;
        }

        // Back solve (L^T x = u) reusing u as x
        for (int i = n - 1; i >= 0; i--) {
            double sum = u[i];
            for (int j = i + 1; j < n; j++) sum -= L.get(j, i) * u[j];
            double di = L.get(i, i);
            u[i] = sum / di;
        }

        // quad = y^T x
        double quad = 0.0;
        for (int i = 0; i < n; i++) quad += y[i] * u[i];

        if (!Double.isFinite(quad) || !Double.isFinite(logDet)) return Double.NaN;

        return -0.5 * quad - 0.5 * logDet;
    }

    private double gpLogMarginalLikelihoodRFF(
            double[] yCentered,          // length n
            int[] parentIdx,             // d parents
            int[] rows,                  // null or length n, mapping into original rows
            int n,
            int mFeatures,               // m
            double bw2,                  // your median ||z-z'||^2 * multiplier^2
            double sigma2,
            long seed
    ) {
        final int d = parentIdx.length;
        if (n < 5) return Double.NaN;
        if (!(sigma2 > 0) || !Double.isFinite(sigma2)) return Double.NaN;
        if (!(bw2 > 0) || !Double.isFinite(bw2)) bw2 = 1.0;

        // RFF parameters for k(x,x') = exp(-||x-x'||^2 / bw2)
        // => w ~ N(0, 2/bw2 I)
        final double wStd = Math.sqrt(2.0 / bw2);
        final double scale = Math.sqrt(2.0 / mFeatures);

        // Deterministic RNG per (target, parentset) key:
        SplittableRandom rng = new SplittableRandom(seed);

        // Sample W (m x d) and b (m)
        // Store as [m][d] for fast dot(row,d) per feature.
        double[][] W = new double[mFeatures][d];
        double[] b = new double[mFeatures];

        for (int j = 0; j < mFeatures; j++) {
            for (int k = 0; k < d; k++) {
                W[j][k] = wStd * nextGaussian(rng);
            }
            b[j] = 2.0 * Math.PI * rng.nextDouble();
        }

        // Accumulate G = Phi^T Phi (m x m, symmetric) and v = Phi^T y (m)
        DMatrixRMaj G = new DMatrixRMaj(mFeatures, mFeatures);
        double[] v = new double[mFeatures];

        double yTy = 0.0;

        // temp feature vector for one row (length m)
        double[] phi = new double[mFeatures];

        for (int ii = 0; ii < n; ii++) {
            int row = (rows == null) ? ii : rows[ii];

            // Build x (d) on the fly from zCols
            // Compute phi_j = sqrt(2/m) cos(w_j^T x + b_j)
            for (int j = 0; j < mFeatures; j++) {
                double dot = 0.0;
                double[] wj = W[j];
                for (int k = 0; k < d; k++) {
                    dot += wj[k] * zCols[parentIdx[k]][row];
                }
                phi[j] = scale * Math.cos(dot + b[j]);
            }

            double yi = yCentered[ii];
            yTy += yi * yi;

            // v += phi * yi
            for (int j = 0; j < mFeatures; j++) {
                v[j] += phi[j] * yi;
            }

            // G += phi^T phi  (rank-1 outer product, symmetric fill lower triangle)
            for (int j = 0; j < mFeatures; j++) {
                double pj = phi[j];
                for (int k = 0; k <= j; k++) {
                    G.add(j, k, pj * phi[k]);
                }
            }
        }

        // Mirror lower -> upper
        for (int j = 0; j < mFeatures; j++) {
            for (int k = 0; k < j; k++) {
                G.set(k, j, G.get(j, k));
            }
        }

        // B = G + sigma2 I
        DMatrixRMaj B = G;
        for (int j = 0; j < mFeatures; j++) {
            B.add(j, j, sigma2);
        }

        // Cholesky(B)
        CholeskyDecomposition_F64<DMatrixRMaj> chol = DecompositionFactory_DDRM.chol(true);
        if (!chol.decompose(B)) return Double.NaN;

        DMatrixRMaj L = chol.getT(null); // lower triangular

        // logdet(B) = 2 * sum log diag(L)
        double logDetB = 0.0;
        for (int i = 0; i < mFeatures; i++) {
            double di = L.get(i, i);
            if (!(di > 0) || !Double.isFinite(di)) return Double.NaN;
            logDetB += Math.log(di);
        }
        logDetB *= 2.0;

        // Solve B^{-1} v via two triangular solves:
        // L u = v, L^T w = u
        double[] u = Arrays.copyOf(v, mFeatures);

        // forward solve
        for (int i = 0; i < mFeatures; i++) {
            double sum = u[i];
            for (int j = 0; j < i; j++) sum -= L.get(i, j) * u[j];
            u[i] = sum / L.get(i, i);
        }
        // back solve
        for (int i = mFeatures - 1; i >= 0; i--) {
            double sum = u[i];
            for (int j = i + 1; j < mFeatures; j++) sum -= L.get(j, i) * u[j];
            u[i] = sum / L.get(i, i);
        }

        // v^T B^{-1} v = v^T w  (w stored in u)
        double vTBInvV = 0.0;
        for (int j = 0; j < mFeatures; j++) vTBInvV += v[j] * u[j];

        // quad = (1/sigma2) yTy - (1/sigma2^2) v^T B^{-1} v
        double invSig = 1.0 / sigma2;
        double quad = invSig * yTy - (invSig * invSig) * vTBInvV;

        // log|C| = (n - m) log sigma2 + log|B|
        double logDetC = (n - mFeatures) * Math.log(sigma2) + logDetB;

        if (!Double.isFinite(quad) || !Double.isFinite(logDetC)) return Double.NaN;

        return -0.5 * quad - 0.5 * logDetC;
    }

    // Box–Muller-ish gaussian from SplittableRandom (fast enough)
    private static double nextGaussian(SplittableRandom rng) {
        // Use Marsaglia polar
        double u, v, s;
        do {
            u = 2.0 * rng.nextDouble() - 1.0;
            v = 2.0 * rng.nextDouble() - 1.0;
            s = u*u + v*v;
        } while (s >= 1.0 || s == 0.0);
        return u * Math.sqrt(-2.0 * Math.log(s) / s);
    }

    // -------------------- kernels --------------------

    /**
     * RBF kernel Gram matrix on standardized parents:
     * K_ij = exp(-||z_i - z_j||^2 / bw2)
     * where bw2 is median ||zi-zj||^2 times multiplier.
     */
    private DMatrixRMaj rbfGramND(int[] parentIdx, int[] rows, int n) {
        int d = parentIdx.length;

        // Extract Z (n x d)
        double[][] Z = new double[n][d];
        for (int r = 0; r < n; r++) {
            int row = (rows == null) ? r : rows[r];
            for (int j = 0; j < d; j++) Z[r][j] = zCols[parentIdx[j]][row];
        }

        double bw2 = medianDistanceSquaredND(Z, Math.min(n, bwMaxRows));
        if (!(bw2 > 0) || !Double.isFinite(bw2)) bw2 = 1.0;
        bw2 *= (bandwidthMultiplier * bandwidthMultiplier);

        double invBw = 1.0 / bw2;

        DMatrixRMaj K = new DMatrixRMaj(n, n);

        // fill diagonal (RBF has k(x,x)=1)
        for (int i = 0; i < n; i++) K.set(i, i, 1.0);

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < i; j++) {
                double dist2 = 0.0;
                for (int k = 0; k < d; k++) {
                    double diff = Z[i][k] - Z[j][k];
                    dist2 += diff * diff;
                }
                double v = Math.exp(-dist2 * invBw);
                K.set(i, j, v);
                K.set(j, i, v);
            }
        }
        return K;
    }

    // -------------------- missingness row selection --------------------

    private int[] validRows(int[] vars) {
        int n = sampleSize;
        int[] tmp = new int[n];
        int m = 0;

        outer:
        for (int r = 0; r < n; r++) {
            for (int v : vars) {
                double val = zCols[v][r]; // zCols preserves NaN
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
        double sd = Math.sqrt(Math.max(1e-12, var));

        for (int i = 0; i < in.length; i++) {
            double v = in[i];
            out[i] = Double.isNaN(v) ? Double.NaN : (v - mean) / sd;
        }
    }

    private static void addDiagonalInPlace(DMatrixRMaj M, double v) {
        int n = Math.min(M.numRows, M.numCols);
        for (int i = 0; i < n; i++) M.add(i, i, v);
    }

    // Median of ||zi-zj||^2 using a subsample of rows for speed.
    private static double medianDistanceSquaredND(double[][] Z, int maxRows) {
        int n = Z.length;
        int d = Z[0].length;
        if (n < 3) return 1.0;

        int m = Math.min(n, maxRows);

        // Take evenly spaced rows (deterministic, no RNG).
        int[] idx = new int[m];
        if (m == n) {
            for (int i = 0; i < m; i++) idx[i] = i;
        } else {
            for (int i = 0; i < m; i++) idx[i] = (int) Math.floor((i * (long) (n - 1)) / (double) (m - 1));
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

    // -------------------- small utilities --------------------

    public int[] append(int[] z, int x) {
        int[] out = Arrays.copyOf(z, z.length + 1);
        out[z.length] = x;
        return out;
    }

    private static int[] concat(int i, int[] parents) {
        int[] all = new int[parents.length + 1];
        all[0] = i;
        System.arraycopy(parents, 0, all, 1, parents.length);
        return all;
    }

    private static long cacheKey(int i, int[] parents) {
        long h = 1469598103934665603L;
        h = (h ^ i) * 1099511628211L;
        for (int p : parents) h = (h ^ p) * 1099511628211L;
        return h;
    }
}