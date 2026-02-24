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
import java.util.SplittableRandom;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * <p><b>FF-ML-Mixed: Feature-Function Marginal Likelihood score for mixed (continuous + discrete) parents</b></p>
 *
 * <p>
 * This class extends the continuous FF-ML (GP marginal-likelihood / “kernel ML”) score to parent sets that
 * include both continuous and discrete variables. The intent is to preserve the nonlinear flexibility of the
 * RBF/GP model for continuous parents while treating discrete parents with an appropriate categorical kernel,
 * rather than forcing integer-coded categories into a smooth Euclidean geometry.
 * </p>
 *
 * <p><b>Product-kernel model</b></p>
 * <p>
 * For a parent vector {@code Z = (Z_c, Z_d)} with continuous part {@code Z_c} and discrete part {@code Z_d},
 * we use a product kernel:
 * </p>
 *
 * <pre>
 *   k((z_c, z_d), (z'_c, z'_d)) = k_cont(z_c, z'_c) * k_cat(z_d, z'_d)
 * </pre>
 *
 * <ul>
 *   <li>{@code k_cont} is an RBF kernel over continuous parents, approximated using Random Fourier Features
 *       (RFF) or Orthogonal Random Features (ORF), as in the continuous FF-ML score.</li>
 *   <li>{@code k_cat} is a simple positive semidefinite (PSD) categorical kernel over discrete levels:
 *       it returns {@code 1} for a level match and {@code ρ} for a mismatch (with {@code 0 ≤ ρ < 1}).</li>
 * </ul>
 *
 * <p><b>Feature representation via a Kronecker map</b></p>
 * <p>
 * The product kernel is implemented using an explicit (finite) feature map. The discrete component is mapped
 * to a categorical feature vector (one block per joint discrete level), and the continuous component is mapped
 * via Fourier features. The mixed feature map is the Kronecker product:
 * </p>
 *
 * <pre>
 *   φ_mix(z_c, z_d) = φ_cat(z_d) ⊗ φ_cont(z_c)
 * </pre>
 *
 * <p>
 * This construction guarantees that the inner product in feature space corresponds to the product kernel above,
 * and it avoids treating discrete codes as continuous numeric inputs to an RBF kernel. For example, if a single
 * discrete parent has {@code L} levels, the effective feature dimension is multiplied by {@code L}. (If multiple
 * discrete parents are present, the categorical feature space corresponds to their joint level combinations.)
 * </p>
 *
 * <p><b>Scoring objective (GP marginal likelihood)</b></p>
 * <p>
 * As in FF-ML, the local score for {@code Y | Pa(Y)} is the log Gaussian-process marginal likelihood in the
 * random-feature approximation. Computation is performed using the standard “Woodbury/dual” reduction to an
 * {@code m×m} system (where {@code m} is the mixed feature dimension) rather than forming an {@code n×n} kernel
 * matrix.
 * </p>
 *
 * <p><b>Bandwidths and preprocessing</b></p>
 * <ul>
 *   <li>Continuous parent columns are globally z-scored.</li>
 *   <li>The RBF bandwidth for {@code k_cont} is chosen by a median pairwise (squared) distance heuristic on the
 *       continuous parents only (typically using a row subsample for speed). If no continuous parents are present,
 *       the continuous bandwidth defaults to {@code 1.0} and the score reduces to a purely categorical-kernel model.</li>
 *   <li>Random-feature generation is deterministic per (target, parent set), enabling stable caching and reproducibility.</li>
 * </ul>
 *
 * <p><b>Practical notes</b></p>
 * <ul>
 *   <li>This score is intended for mixed data where discrete parents are genuinely categorical (unordered) variables.</li>
 *   <li>The mixed feature dimension can grow quickly with multiple discrete parents (via joint-level combinations),
 *       so feature counts and discrete cardinalities materially affect runtime.</li>
 *   <li>The parameter {@code ρ} controls how strongly different categorical levels are treated as “similar”:
 *       {@code ρ=0} corresponds to a strict delta kernel; larger {@code ρ} smooths across levels.</li>
 * </ul>
 */
public final class FfMl implements Score, EffectiveSampleSizeSettable {

    /**
     * If true, use valid row subsets when missing exists.
     */
    private final boolean calculateRowSubsets;

    // -------------------- configuration knobs --------------------
    private final DataSet dataSet;
    private final List<Node> variables;
    private final int sampleSize;
    /**
     * Standardized columns for continuous variables only (NaNs preserved).
     */
    private final double[][] zCols;
    /**
     * Discrete values (int codes) per variable per row; null for continuous vars.
     */
    private final int[][] dCols;
    /**
     * Which variables are discrete.
     */
    private final boolean[] isDiscrete;
    /**
     * Cache: (target i, sorted parents) -> score.
     */
    private final AtomicReference<ConcurrentHashMap<Long, Double>> localScoreCacheRef =
            new AtomicReference<>(new ConcurrentHashMap<>());

    private final ConcurrentHashMap<Long, Double> bw2Cache = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<Long, Double> bw2OptCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Double> bw2MedByTargetContCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Double> bw2OptByTargetContCache = new ConcurrentHashMap<>();
    // ---- make bw selection "global-per-target" for comparability across DAGs ----
    private volatile boolean bwCoupleByTarget = true;     // NEW: default true
    /**
     * Base ridge/noise knob. Used as sigma^2. Must be > 0.
     */
    private volatile double lambda = 1.0;

    // -------------------- reproducibility / feature coupling --------------------

    /**
     * Base seed for random-feature generation. Changing this changes the random feature basis.
     * Default is arbitrary but fixed.
     */
    private volatile long baseSeed = 0xC0FFEE1234ABCDL;

    /**
     * If true (recommended), random features (W,b) are coupled by TARGET only.
     * This stabilizes localScoreDiff comparisons and usually fixes BOSS edge reversals.
     * <p>
     * If false, we revert to the old behavior: seed depends on (target, parent set).
     */
    private volatile boolean coupleFeaturesByTarget = true;

    // -------------------- data --------------------
//    /**
//     * Bandwidth multiplier on the median heuristic (continuous part only).
//     */
//    private volatile double bandwidthMultiplier = 1.0;
    /**
     * Max rows used to estimate median bandwidth (subsample for speed).
     */
    private volatile int bwMaxRows = 400;
    /**
     * Number of random features for continuous kernel approximation (m).
     */
    private volatile int numFeatures = 256;
    /**
     * Effective sample size.
     */
    private volatile int nEff;
    /**
     * RFF vs ORF for continuous features.
     */
    private FeatureType featureType = FeatureType.ORF;
    /**
     * Categorical kernel off-diagonal similarity rho in [0, 1).
     * k_cat(c,c)=1, k_cat(c,c')=rho for c!=c'.
     * <p>
     * For 3-level Origin, rho ~ 0.3..0.8 is a reasonable range.
     * rho closer to 0 => categories treated as very distinct blocks.
     * rho closer to 1 => categories treated as nearly identical.
     */
    private volatile double catRho = 0.5;

    /**
     * Constructs an instance of FfMlMixed for the given dataset. This constructor processes
     * the dataset to handle discrete and continuous variables, computes z-scores for continuous
     * variables, and initializes internal structures used for mixed data modeling.
     *
     * @param dataSet the dataset to be used in the mixed data model. Must not be null.
     *                The dataset is expected to contain a mix of discrete and continuous variables.
     *                Each variable in the dataset is processed to identify its type and structure
     *                corresponding internal data representations.
     * @throws NullPointerException if the provided dataset is null.
     */
    public FfMl(DataSet dataSet) {
        if (dataSet == null) throw new NullPointerException("dataSet");
        this.dataSet = dataSet;
        this.variables = dataSet.getVariables();
        this.sampleSize = dataSet.getNumRows();
        setEffectiveSampleSize(-1);

        this.calculateRowSubsets = dataSet.existsMissingValue();

        int p = variables.size();

        this.isDiscrete = new boolean[p];
        this.dCols = new int[p][];
        double[][] raw = new double[p][sampleSize];

        for (int j = 0; j < p; j++) {
            Node v = variables.get(j);
            boolean disc = (v instanceof DiscreteVariable);
            isDiscrete[j] = disc;

            if (disc) {
                dCols[j] = new int[sampleSize];
                for (int r = 0; r < sampleSize; r++) {
                    dCols[j][r] = readDiscreteValue(dataSet, r, j);
                }
                // raw not used for discrete
                Arrays.fill(raw[j], Double.NaN);
            } else {
                for (int r = 0; r < sampleSize; r++) {
                    raw[j][r] = dataSet.getDouble(r, j);
                }
                dCols[j] = null;
            }
        }

        this.zCols = new double[p][sampleSize];
        for (int j = 0; j < p; j++) {
            if (isDiscrete[j]) {
                Arrays.fill(zCols[j], Double.NaN);
            } else {
                zscoreColumnPreserveNaN(raw[j], zCols[j]);
            }
        }

        resetCache();
    }

    /**
     * Build a one-hot matrix for a discrete variable and center each column.
     * Levels are taken as the distinct observed values in the current row subset.
     * <p>
     * Returns Y (n x L) where each column has mean 0.
     */
    private static double[][] oneHotCentered(int[] vals) {
        int n = vals.length;
        if (n == 0) return null;

        // Use observed levels in this subset (important when some levels are absent after filtering)
//        int[] uniq = Arrays.stream(vals).distinct().sorted().toArray();

        int[] uniq = Arrays.stream(vals)
                .filter(v -> v != DiscreteVariable.MISSING_VALUE && v != Integer.MIN_VALUE)
                .distinct().sorted().toArray();

        int L = uniq.length;
        if (L <= 0) return null;

        // Map each value -> level index 0..L-1
        double[][] Y = new double[n][L];

//        for (int r = 0; r < n; r++) {
//            int v = vals[r];
//            int pos = Arrays.binarySearch(uniq, v);
//            if (pos < 0) {
//                // Shouldn't happen, but defensively clamp
//                pos = 0;
//            }
//            Y[r][pos] = 1.0;
//        }

        for (int r = 0; r < n; r++) {
            int v = vals[r];
            if (v == DiscreteVariable.MISSING_VALUE || v == Integer.MIN_VALUE) continue;
            int pos = Arrays.binarySearch(uniq, v);
            if (pos < 0) continue;
            Y[r][pos] = 1.0;
        }

        // Center each column
        for (int j = 0; j < L; j++) {
            double sum = 0.0;
            for (int r = 0; r < n; r++) sum += Y[r][j];
            double mean = sum / n;
            for (int r = 0; r < n; r++) Y[r][j] -= mean;
        }

        return Y;
    }

    // -------------------- Score interface --------------------

    /**
     * Sigma-only multi-output: sum the sigma-only GP marginal likelihood across columns.
     * Each column is treated as an independent output with the same sigma^2 I covariance.
     */
    private static double gpLogMarginalLikelihoodSigmaOnlyMulti(double[][] Ycentered, double sigma2) {
        int n = Ycentered.length;
        if (n == 0) return Double.NaN;
        int L = Ycentered[0].length;
        if (L == 0) return Double.NaN;

        double sum = 0.0;
        for (int j = 0; j < L; j++) {
            double[] col = new double[n];
            for (int r = 0; r < n; r++) col[r] = Ycentered[r][j];
            sum += gpLogMarginalLikelihoodSigmaOnly(col, sigma2);
        }
        return sum;
    }

    /**
     * A tiny 64-bit mixing function for seed diversification.
     */
    private static long mix64(long z) {
        z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
        z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return z ^ (z >>> 33);
    }

    /**
     * Build an exact feature map for k_cat where diag=1 and offdiag=rho.
     * We:
     * - find unique levels among vals
     * - map them to 0..L-1
     * - form K_levels (LxL)
     * - Cholesky factor K_levels = A A^T
     * - feature(level)=row of A
     */
    private static CatFeatureMap buildCatMap(int[] vals, double rho) {
        // map distinct values to compact 0..L-1
        int n = vals.length;

//        int[] uniq = Arrays.stream(vals).distinct().sorted().toArray();

        int[] uniq = Arrays.stream(vals)
                .filter(v -> v != DiscreteVariable.MISSING_VALUE && v != Integer.MIN_VALUE)
                .distinct().sorted().toArray();

        int L = uniq.length;
        if (L <= 0) return null;

        int[] levelOfRow = new int[n];
        for (int i = 0; i < n; i++) {
            int v = vals[i];

            if (v == DiscreteVariable.MISSING_VALUE || v == Integer.MIN_VALUE) {
                // This should not happen if rows were filtered correctly.
                throw new IllegalStateException("Missing discrete value encountered in buildCatMap after row filtering.");
            }

            int pos = Arrays.binarySearch(uniq, v);
            if (pos < 0) {
                // Also should not happen since uniq comes from vals (minus missing).
                throw new IllegalStateException("Discrete level not found in uniq: " + v);
            }

            levelOfRow[i] = pos;
        }

        // K_levels
        double[][] K = new double[L][L];
        for (int i = 0; i < L; i++) {
            for (int j = 0; j < L; j++) {
                K[i][j] = (i == j) ? 1.0 : rho;
            }
        }

        // Cholesky: K = A A^T (A lower-tri)
        double[][] A = choleskyLower(K);
        if (A == null) return null;

        // We want features as rows of A.
        // A is lower triangular; that’s fine: row i is a length-L feature vector.
        return new CatFeatureMap(L, levelOfRow, A);
    }

    private static double[][] choleskyLower(double[][] M) {
        int n = M.length;
        double[][] L = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j <= i; j++) {
                double sum = M[i][j];
                for (int k = 0; k < j; k++) sum -= L[i][k] * L[j][k];

                if (i == j) {
                    if (sum <= 1e-15) return null;
                    L[i][j] = Math.sqrt(sum);
                } else {
                    L[i][j] = sum / L[j][j];
                }
            }
        }
        return L;
    }

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

    private static double[][] sampleOrthogonalW(int mFeatures, int d, double wStd, SplittableRandom rng) {
        double[][] W = new double[mFeatures][d];
        if (d <= 0) return W;

        int filled = 0;
        while (filled < mFeatures) {
            int block = Math.min(d, mFeatures - filled);

            double[][] Q = new double[block][d];
            for (int i = 0; i < block; i++) {
                for (int j = 0; j < d; j++) Q[i][j] = nextGaussian(rng);
            }

            for (int i = 0; i < block; i++) {
                for (int k = 0; k < i; k++) {
                    double dot = 0.0;
                    for (int j = 0; j < d; j++) dot += Q[i][j] * Q[k][j];
                    for (int j = 0; j < d; j++) Q[i][j] -= dot * Q[k][j];
                }
                double norm2 = 0.0;
                for (int j = 0; j < d; j++) norm2 += Q[i][j] * Q[i][j];
                double norm = Math.sqrt(Math.max(1e-18, norm2));
                for (int j = 0; j < d; j++) Q[i][j] /= norm;
            }

            for (int i = 0; i < block; i++) {
                double r = chiRadius(d, rng);
                double s = wStd * r;
                int outRow = filled + i;
                for (int j = 0; j < d; j++) W[outRow][j] = s * Q[i][j];
            }

            filled += block;
        }

        return W;
    }

    private static double chiRadius(int d, SplittableRandom rng) {
        double ss = 0.0;
        for (int k = 0; k < d; k++) {
            double g = nextGaussian(rng);
            ss += g * g;
        }
        return Math.sqrt(Math.max(1e-18, ss));
    }

    private static double nextGaussian(SplittableRandom rng) {
        double u, v, s;
        do {
            u = 2.0 * rng.nextDouble() - 1.0;
            v = 2.0 * rng.nextDouble() - 1.0;
            s = u * u + v * v;
        } while (s >= 1.0 || s == 0.0);
        return u * Math.sqrt(-2.0 * Math.log(s) / s);
    }

    private static double medianDistanceSquaredND(double[][] Z, int maxRows) {
        int n = Z.length;
        int d = Z[0].length;
        if (n < 3) return 1.0;

        int m = Math.min(n, maxRows);

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

    private static long keyTargetCont(int target, int[] contParentsSorted) {
        long h = 1469598103934665603L;
        h = (h ^ target) * 1099511628211L;
        for (int p : contParentsSorted) h = (h ^ p) * 1099511628211L;
        return h;
    }

    private static boolean isMissingDiscrete(int v) {
        return v == DiscreteVariable.MISSING_VALUE || v == Integer.MIN_VALUE;
    }

    private static int readDiscreteValue(DataSet ds, int row, int col) {
        try {
            return ds.getInt(row, col);
        } catch (Throwable t) {
            // Fallback if dataset stores codes as doubles
            double v = ds.getDouble(row, col);
            if (!Double.isFinite(v)) return Integer.MIN_VALUE;
            return (int) Math.rint(v);
        }
    }

    // -------------------- tuning knobs --------------------

    @Override
    public double localScoreDiff(int x, int y, int[] z) {
        return localScore(y, append(z, x)) - localScore(y, z);
    }

    @Override
    public double localScore(int i, int... parents) {
        Arrays.sort(parents);

        // full key for localScore cache (still parent-set coupled)
        final long key = cacheKey(i, parents);
        final ConcurrentHashMap<Long, Double> cache = localScoreCacheRef.get();

        return cache.computeIfAbsent(key, k -> {
            try {
                // rows for *scoring* depend on target + ALL parents (cont+disc)
                int[] all = concat(i, parents);
                int[] rows = calculateRowSubsets ? validRowsMixed(all) : null;

                int n = (rows == null) ? nEff : rows.length;
                if (n < 5) return Double.NaN;

                double sigma2 = lambda;
                if (!(sigma2 > 0) || !Double.isFinite(sigma2)) return Double.NaN;

                // Split parents into continuous and discrete
                int[] contParents = new int[parents.length];
                int[] discParents = new int[parents.length];
                int nc = 0, nd = 0;

                for (int pIdx : parents) {
                    if (isDiscrete[pIdx]) discParents[nd++] = pIdx;
                    else contParents[nc++] = pIdx;
                }

                contParents = Arrays.copyOf(contParents, nc);
                discParents = Arrays.copyOf(discParents, nd);

                // ---------- Median bw^2 on continuous parents ONLY ----------
                double bw2Med = 1.0;

                if (nc > 0) {
                    // IMPORTANT: bandwidth rows must NOT depend on discrete parents
                    // (otherwise "coupled by target" caching becomes inconsistent).
                    int[] bwVars = concat(i, contParents);
                    int[] rowsBw = calculateRowSubsets ? validRowsMixed(bwVars) : null;
                    int nBw = (rowsBw == null) ? nEff : rowsBw.length;
                    if (nBw < 5) return Double.NaN;

                    final int finalNc = nc;
                    final int[] finalContParents = contParents;

                    if (bwCoupleByTarget) {
                        // Cache by (target, contParents) signature
                        final long bwKey = keyTargetCont(i, finalContParents);

                        bw2Med = bw2MedByTargetContCache.computeIfAbsent(bwKey, kk -> {
                            double[][] Zc = new double[nBw][finalNc];
                            for (int r = 0; r < nBw; r++) {
                                int row = (rowsBw == null) ? r : rowsBw[r];
                                for (int j = 0; j < finalNc; j++) {
                                    Zc[r][j] = zCols[finalContParents[j]][row];
                                }
                            }
                            double est = medianDistanceSquaredND(Zc, Math.min(nBw, bwMaxRows));
                            if (!(est > 0) || !Double.isFinite(est)) est = 1.0;
                            return est;
                        });
                    } else {
                        // Old behavior: cache by full (target, parents) key
                        bw2Med = bw2Cache.computeIfAbsent(key, kk -> {
                            double[][] Zc = new double[n][finalNc];
                            for (int r = 0; r < n; r++) {
                                int row = (rows == null) ? r : rows[r];
                                for (int j = 0; j < finalNc; j++) {
                                    Zc[r][j] = zCols[finalContParents[j]][row];
                                }
                            }
                            double est = medianDistanceSquaredND(Zc, Math.min(n, bwMaxRows));
                            if (!(est > 0) || !Double.isFinite(est)) est = 1.0;
                            return est;
                        });
                    }
                }

                // Deterministic seed
                long seed = seedFor(i, key);

                // ----------------------------
                // CASE A: continuous target
                // ----------------------------
                if (!isDiscrete[i]) {
                    double[] y = extract1DContinuous(i, rows, n);
                    centerInPlace(y);

                    if (parents.length == 0) {
                        return gpLogMarginalLikelihoodSigmaOnly(y, sigma2);
                    }

                    // IMPORTANT: for "coupled" bw optimization, pickBw2 should cache by (target, contParents)
                    long bwOptKey = bwCoupleByTarget ? keyTargetCont(i, contParents) : key;

                    double bw2 = pickBw2ByGridSearch(
                            bwOptKey,          // NOTE: pass bwOptKey (not the fullKey) when coupled
                            y,
                            contParents, discParents,
                            rows, n,
                            numFeatures,
                            bw2Med,
                            sigma2,
                            seed
                    );

                    return gpLogMarginalLikelihoodRFFMixed(
                            y, contParents, discParents, rows, n,
                            numFeatures, bw2, sigma2, seed
                    );
                }

                // ----------------------------
                // CASE B: discrete target
                // ----------------------------
                int[] yDisc = extractDiscrete(i, rows, n);
                double[][] Y = oneHotCentered(yDisc);
                if (Y == null || Y[0].length == 0) return Double.NaN;

                if (parents.length == 0) {
                    return gpLogMarginalLikelihoodSigmaOnlyMulti(Y, sigma2);
                }

                // For discrete targets, keep bandwidth simple: median on continuous parents
                double bw2 = (nc > 0 && Double.isFinite(bw2Med) && bw2Med > 0) ? bw2Med : 1.0;

                return gpLogMarginalLikelihoodRFFMixedMultiOutput(
                        Y, contParents, discParents, rows, n,
                        numFeatures, bw2, sigma2, seed
                );

            } catch (RuntimeException e) {
                TetradLogger.getInstance().log(e.getMessage());
                return Double.NaN;
            }
        });
    }

    /**
     * Choose bw^2 by a tiny grid-search around the median heuristic.
     * <p>
     * Key idea (when bwCoupleByTarget=true):
     * - We cache the *optimized* bw^2 by a key that depends only on (target, contParents),
     * NOT on discrete parents and NOT on the score-row-subset for the full parent set.
     * - That keeps bw comparable across DAGs/parent-sets and avoids discrete-parent-driven
     * missingness changing the bandwidth optimization target.
     * <p>
     * IMPORTANT: when bwCoupleByTarget=true, the caller should pass:
     * fullKey = keyTargetCont(targetIndex, contParents)
     * (i.e., “fullKey” is really the bw-opt cache key in that mode).
     */
    private double pickBw2ByGridSearch(
            long fullKey,
            double[] yCentered,
            int[] contParents,
            int[] discParents,
            int[] rows,
            int n,
            int mFeatures,
            double bw2Med,
            double sigma2,
            long seed
    ) {
        if (contParents == null || contParents.length == 0) return 1.0;  // no continuous inputs
        if (!(bw2Med > 0) || !Double.isFinite(bw2Med)) bw2Med = 1.0;

        // Cache lookup
        if (bwCoupleByTarget) {
            // Here, fullKey should already be keyTargetCont(targetIndex, contParents)
            Double cached = bw2OptByTargetContCache.get(fullKey);
            if (cached != null) return cached;
        } else {
            Double cached = bw2OptCache.get(fullKey);
            if (cached != null) return cached;
        }

        // Grid in multiplier space. Interpret as bw2 = bw2Med * mult^2
        final double[] mult = {0.25, 0.35, 0.5, 0.7, 1.0, 1.4, 2.0, 2.8, 4.0};

        double bestBw2 = bw2Med;
        double best = Double.NEGATIVE_INFINITY;

        for (double m : mult) {
            double bw2 = bw2Med * (m * m);
            if (!(bw2 > 0) || !Double.isFinite(bw2)) continue;

            double ll = gpLogMarginalLikelihoodRFFMixed(
                    yCentered,
                    contParents,
                    discParents,
                    rows,
                    n,
                    mFeatures,
                    bw2,
                    sigma2,
                    seed
            );

            if (Double.isFinite(ll) && ll > best) {
                best = ll;
                bestBw2 = bw2;
            }
        }

        // Fallback safety
        if (!Double.isFinite(best) || !(bestBw2 > 0) || !Double.isFinite(bestBw2)) bestBw2 = bw2Med;

        // Store
        if (bwCoupleByTarget) {
            bw2OptByTargetContCache.put(fullKey, bestBw2);
        } else {
            bw2OptCache.put(fullKey, bestBw2);
        }

        return bestBw2;
    }

    /**
     * Mixed multi-output GP-ML surrogate for discrete targets:
     * score each centered one-hot column using your existing mixed-parent GP-ML,
     * and sum across columns.
     * <p>
     * Note: This is a Gaussian multi-output surrogate (not a true multinomial likelihood),
     * but it is often very effective for structure scoring (detecting dependencies).
     */
    private double gpLogMarginalLikelihoodRFFMixedMultiOutput(
            double[][] Ycentered,          // n x L, columns already centered
            int[] contParents,
            int[] discParents,
            int[] rows,
            int n,
            int mFeatures,
            double bw2,
            double sigma2,
            long seed
    ) {
        int L = Ycentered[0].length;
        if (L == 0) return Double.NaN;

        double total = 0.0;

        // Simple deterministic per-output seed derivation
        long s = seed;
        for (int j = 0; j < L; j++) {
            double[] yj = new double[n];
            for (int r = 0; r < n; r++) yj[r] = Ycentered[r][j];

            // Slightly perturb seed per column so the RFF sampling isn't identical across outputs
            s = mix64(s + 0x9E3779B97F4A7C15L + j);

            double ll = gpLogMarginalLikelihoodRFFMixed(
                    yj, contParents, discParents, rows, n,
                    mFeatures, bw2, sigma2, s
            );
            if (!Double.isFinite(ll)) return Double.NaN;
            total += ll;
        }

        return total;
    }

    /**
     * Retrieves a list of variable nodes.
     *
     * @return a new list containing the variable nodes.
     */
    @Override
    public List<Node> getVariables() {
        return new ArrayList<>(variables);
    }

    /**
     * Returns the sample size of the dataset by retrieving the number
     * of rows in the dataset.
     *
     * @return the number of rows in the dataset, representing the sample size.
     */
    @Override
    public int getSampleSize() {
        return dataSet.getNumRows();
    }

    /**
     * Calculates the maximum degree based on the effective size (nEff).
     * The calculation is performed as the ceiling value of the logarithm
     * of the larger between 5 and nEff.
     *
     * @return the maximum degree as an integer
     */
    @Override
    public int getMaxDegree() {
        return (int) Math.ceil(Math.log(Math.max(5, nEff)));
    }

    /**
     * Determines whether a given node y is conditionally independent of other nodes z
     * based on the local score calculation.
     *
     * @param z a list of Node objects representing the conditional set.
     * @param y a Node object for which the dependency is being determined.
     * @return true if the local score for the given node and parents is NaN or infinite,
     * false otherwise.
     */
    @Override
    public boolean determines(List<Node> z, Node y) {
        int i = variables.indexOf(y);
        int[] parents = new int[z.size()];
        for (int t = 0; t < z.size(); t++) parents[t] = variables.indexOf(z.get(t));

        double s = localScore(i, parents);
        return Double.isNaN(s) || Double.isInfinite(s);
    }

    /**
     * Determines whether the given bump value indicates an effect edge.
     * An effect edge is considered true if the bump value is greater than 0.
     *
     * @param bump the value to evaluate, typically representing a change or effect magnitude
     * @return {@code true} if the bump value is greater than 0, otherwise {@code false}
     */
    @Override
    public boolean isEffectEdge(double bump) {
        return bump > 0;
    }

    /**
     * Retrieves the data model associated with this instance, encapsulating the
     * data used for computations or analysis in the current context.
     *
     * @return the {@code DataModel} object representing the data set.
     */
    public DataModel getDataModel() {
        return dataSet;
    }

    /* ====================================================================== */
    /* ======================  n×n kernel (fast for 2+)  ===================== */
    /* ====================================================================== */

    /**
     * Returns the effective sample size used in computations. The effective sample size
     * is a value that represents the number of independent data points after accounting
     * for dependencies or adjustments.
     *
     * @return the effective sample size as an integer
     */
    @Override
    public int getEffectiveSampleSize() {
        return nEff;
    }

    /* ====================================================================== */
    /* ===================  feature-space (your original)  =================== */
    /* ====================================================================== */

    /**
     * Sets the effective sample size for computations. If the provided value is less than zero,
     * the effective sample size is set to the total sample size instead. Invokes a reset
     * of the internal cache to reflect the updated sample size.
     *
     * @param nEff the effective sample size to set; if less than 0, the entire sample size is used
     */
    @Override
    public void setEffectiveSampleSize(int nEff) {
        this.nEff = (nEff < 0) ? this.sampleSize : nEff;
        resetCache();
    }

    // -------------------- categorical kernel helper --------------------

    /**
     * Returns a string representation of the object, describing the type of kernel
     * configuration as "KFF-ML Mixed (continuous+categorical product-kernel)".
     *
     * @return a string describing the mixed kernel configuration.
     */
    @Override
    public String toString() {
        return "FFML (continuous+categorical product-kernel)";
    }

    /**
     * Sets the value of the lambda parameter. The lambda parameter must be greater than 0.
     * Updates the internal state by resetting the cache.
     *
     * @param lambda the value of lambda to set (must be > 0)
     * @throws IllegalArgumentException if the lambda value is less than or equal to 0
     */
    public void setLambda(double lambda) {
        if (lambda <= 0) throw new IllegalArgumentException("lambda must be > 0");
        this.lambda = lambda;
        resetCache();
    }

//    /**
//     * Sets the bandwidth multiplier to adjust the data transfer rate.
//     * The provided value must be greater than 0 and finite.
//     *
//     * @param bandwidthMultiplier the positive finite value to set as the bandwidth multiplier
//     * @throws IllegalArgumentException if the provided bandwidthMultiplier is not greater than 0
//     *                                  or is not a finite value
//     */
//    public void setBandwidthMultiplier(double bandwidthMultiplier) {
//        if (!(bandwidthMultiplier > 0) || !Double.isFinite(bandwidthMultiplier)) {
//            throw new IllegalArgumentException("bandwidthMultiplier must be > 0");
//        }
//        this.bandwidthMultiplier = bandwidthMultiplier;
//        resetCache();
//    }

    // -------------------- sigma-only case --------------------

    /**
     * Sets the maximum number of rows allowed for a given operation, ensuring a minimum value of 50.
     * If the provided value is less than 50, the limit will default to 50.
     * Also resets the internal cache after updating the maximum row limit.
     *
     * @param bwMaxRows the desired maximum number of rows; must be greater than or equal to 50.
     */
    public void setBwMaxRows(int bwMaxRows) {
        this.bwMaxRows = Math.max(50, bwMaxRows);
        resetCache();
    }

    // -------------------- missingness row selection (mixed) --------------------

    /**
     * Sets the number of features to be used and resets the internal cache.
     *
     * @param numFeatures the number of features to set
     */
    public void setNumFeatures(int numFeatures) {
        if (numFeatures < 8) throw new IllegalArgumentException("numFeatures should be >= 8");
        this.numFeatures = numFeatures;
        resetCache();
    }

    /**
     * Returns the base seed value used for initializing or seeding operations.
     *
     * @return the base seed as a long value
     */
    public long getBaseSeed() {
        return baseSeed;
    }

    /**
     * Sets the base seed value for the random generator or algorithm
     * and resets any associated cached data.
     *
     * @param baseSeed the base seed value to initialize the random generator
     */
    public void setBaseSeed(long baseSeed) {
        this.baseSeed = baseSeed;
        resetCache();
    }

    /**
     * Indicates whether features are coupled by target.
     *
     * @return true if features are coupled by target; false otherwise.
     */
    public boolean isCoupleFeaturesByTarget() {
        return coupleFeaturesByTarget;
    }

    /**
     * Sets the state of the coupleFeaturesByTarget property, which determines whether
     * features are coupled based on the target.
     *
     * @param coupleFeaturesByTarget a boolean indicating whether features should
     *                               be coupled by target (true) or not (false)
     */
    public void setCoupleFeaturesByTarget(boolean coupleFeaturesByTarget) {
        this.coupleFeaturesByTarget = coupleFeaturesByTarget;
        resetCache();
    }

    // -------------------- extraction + preprocessing --------------------

    /**
     * Retrieves the feature type associated with this object.
     *
     * @return the feature type as a FeatureType instance.
     */
    public FeatureType getFeatureType() {
        return featureType;
    }

    /**
     * Sets the feature type for the current object. This method assigns a new value
     * to the featureType property and clears any cached data associated with the previous value.
     *
     * @param featureType the feature type to set; must not be null
     * @throws IllegalArgumentException if the featureType parameter is null
     */
    public void setFeatureType(FeatureType featureType) {
        if (featureType == null) throw new IllegalArgumentException("featureType cannot be null");
        this.featureType = featureType;
        resetCache();
    }

    /**
     * Retrieves the value of the catRho property.
     *
     * @return the current value of catRho as a double.
     */
    public double getCatRho() {
        return catRho;
    }

    /**
     * Set categorical similarity rho in [0,1).
     * Try 0.3, 0.5, 0.7 for Auto-MPG Origin.
     *
     * @param rho the categorical similarity rho to set; must be in [0,1)
     */
    public void setCatRho(double rho) {
        if (!(rho >= 0.0 && rho < 1.0) || !Double.isFinite(rho)) {
            throw new IllegalArgumentException("catRho must be in [0,1)");
        }
        this.catRho = rho;
        resetCache();
    }

    // -------------------- ORF / Gaussian helpers --------------------

    /**
     * Drop-in replacement for gpLogMarginalLikelihoodRFFMixed(...).
     * <p>
     * Key change: when there are 2+ discrete parents, we *do not* build the Kronecker-expanded
     * feature map (which explodes as m * Π L). Instead we switch to the n×n GP formulation:
     * <p>
     * K = Kcont ⊙ Kcat
     * C = K + sigma^2 I
     * <p>
     * and compute:  -0.5 * (y^T C^{-1} y + log|C|)   (no additive constants),
     * matching your existing convention.
     * <p>
     * For 0–1 discrete parent, we keep your existing feature-space path (fast), with one
     * micro-fix: avoid per-row allocations in the Kronecker expansion.
     */
    private double gpLogMarginalLikelihoodRFFMixed(
            double[] yCentered,          // length n
            int[] contParents,           // continuous parent indices
            int[] discParents,           // discrete parent indices
            int[] rows,                  // null or length n, mapping into original rows
            int n,
            int mFeatures,               // m (continuous features)
            double bw2,                  // bandwidth^2 for continuous part
            double sigma2,
            long seed
    ) {
        if (n < 5) return Double.NaN;
        if (!(sigma2 > 0) || !Double.isFinite(sigma2)) return Double.NaN;

        // TODO: I am using the n x n formulation for all discrete variables here
        // by choosing discParents.length >= 1 instead of >= 2. Might want a class-level
        // field to allow this to be configured. jdramsey 2026-1-21.

        // Heuristic: Kronecker feature dimension is m * Π L. This explodes quickly.
        // For >= 2 discrete parents, always use the n×n kernel path.
        if (discParents.length >= 2) {
            return gpLogML_mixedKernelNxN(
                    yCentered, contParents, discParents, rows, n,
                    mFeatures, bw2, sigma2, seed
            );
        }

        // 0–1 discrete parent: keep your original feature-space method (fast),
        // but remove per-row allocation in the Kronecker step for the 1-discrete case.
        return gpLogML_mixedFeatureSpace(
                yCentered, contParents, discParents, rows, n,
                mFeatures, bw2, sigma2, seed
        );
    }

    private double gpLogML_mixedKernelNxN(
            double[] yCentered,
            int[] contParents,
            int[] discParents,
            int[] rows,
            int n,
            int mFeatures,
            double bw2,
            double sigma2,
            long seed
    ) {
        // 1) Build continuous random features Phi (n×m) and Kcont = Phi Phi^T (n×n).
        final int dc = contParents.length;

        // Bandwidth sanity
        if (dc > 0) {
            if (!(bw2 > 0) || !Double.isFinite(bw2)) bw2 = 1.0;
        } else {
            // no continuous inputs; bw2 irrelevant
            bw2 = 1.0;
        }

        // Deterministic RNG per (target, parents)
        SplittableRandom rng = new SplittableRandom(seed);

        // Sample W,b exactly like your existing code
        double wStd = (dc > 0) ? Math.sqrt(2.0 / bw2) : 1.0;
        double[][] W;
        double[] b = new double[mFeatures];

        if (dc > 0) {
            if (featureType == FeatureType.RFF) {
                W = new double[mFeatures][dc];
                for (int j = 0; j < mFeatures; j++) {
                    for (int k = 0; k < dc; k++) W[j][k] = wStd * nextGaussian(rng);
                    b[j] = 2.0 * Math.PI * rng.nextDouble();
                }
            } else { // ORF
                W = sampleOrthogonalW(mFeatures, dc, wStd, rng);
                for (int j = 0; j < mFeatures; j++) b[j] = 2.0 * Math.PI * rng.nextDouble();
            }
        } else {
            // no continuous parents: phi_cont is constant cos(b)
            W = null;
            for (int j = 0; j < mFeatures; j++) b[j] = 2.0 * Math.PI * rng.nextDouble();
        }

        final double contScale = Math.sqrt(2.0 / mFeatures);

        // Phi: store as double[n][mFeatures]
        double[][] Phi = new double[n][mFeatures];

        for (int ii = 0; ii < n; ii++) {
            int row = (rows == null) ? ii : rows[ii];

            if (dc == 0) {
                for (int j = 0; j < mFeatures; j++) {
                    Phi[ii][j] = contScale * Math.cos(b[j]);
                }
            } else {
                for (int j = 0; j < mFeatures; j++) {
                    double dot = 0.0;
                    double[] wj = W[j];
                    for (int k = 0; k < dc; k++) dot += wj[k] * zCols[contParents[k]][row];
                    Phi[ii][j] = contScale * Math.cos(dot + b[j]);
                }
            }
        }

        // Kcont = Phi Phi^T  (n×n), symmetric
        DMatrixRMaj K = new DMatrixRMaj(n, n);
        for (int i = 0; i < n; i++) {
            double[] phiI = Phi[i];
            for (int j = 0; j <= i; j++) {
                double[] phiJ = Phi[j];
                double dot = 0.0;
                for (int t = 0; t < mFeatures; t++) dot += phiI[t] * phiJ[t];
                K.set(i, j, dot);
            }
        }
        // mirror
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < i; j++) K.set(j, i, K.get(i, j));
        }

        // 2) Build Kcat (n×n) and do Hadamard product into K.
        // Start with implicit ones; multiply by (same?1:rho) for each discrete parent.
        for (int dp : discParents) {
            // extract codes for this discrete parent (length n)
            int[] codes = extractDiscrete(dp, rows, n);

            final double rho = catRho; // k_cat(c,c)=1, k_cat(c,c')=rho
            for (int i = 0; i < n; i++) {
                int ci = codes[i];
                for (int j = 0; j <= i; j++) {
                    int cj = codes[j];
                    double mult = (ci == cj) ? 1.0 : rho;
                    K.set(i, j, K.get(i, j) * mult);
                }
            }
            // mirror again (cheaper to mirror per parent than re-loop upper inside main mult)
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < i; j++) K.set(j, i, K.get(i, j));
            }
        }

        // 3) C = K + sigma2 I
        for (int i = 0; i < n; i++) K.add(i, i, sigma2);

        // 4) Cholesky(C)
        CholeskyDecomposition_F64<DMatrixRMaj> chol = DecompositionFactory_DDRM.chol(true);
        if (!chol.decompose(K)) return Double.NaN;
        DMatrixRMaj L = chol.getT(null);

        // logdet(C) = 2*sum(log(diag(L)))
        double logDetC = 0.0;
        for (int i = 0; i < n; i++) {
            double di = L.get(i, i);
            if (!(di > 0) || !Double.isFinite(di)) return Double.NaN;
            logDetC += Math.log(di);
        }
        logDetC *= 2.0;

        // 5) Solve C^{-1} y via two triangular solves.
        double[] alpha = Arrays.copyOf(yCentered, n);

        // forward: L u = y
        for (int i = 0; i < n; i++) {
            double sum = alpha[i];
            for (int j = 0; j < i; j++) sum -= L.get(i, j) * alpha[j];
            alpha[i] = sum / L.get(i, i);
        }
        // back: L^T alpha = u
        for (int i = n - 1; i >= 0; i--) {
            double sum = alpha[i];
            for (int j = i + 1; j < n; j++) sum -= L.get(j, i) * alpha[j];
            alpha[i] = sum / L.get(i, i);
        }

        // y^T C^{-1} y
        double quad = 0.0;
        for (int i = 0; i < n; i++) quad += yCentered[i] * alpha[i];

        if (!Double.isFinite(quad) || !Double.isFinite(logDetC)) return Double.NaN;

        // Match your convention: omit additive constants
        return -0.5 * quad - 0.5 * logDetC;
    }

    private double gpLogML_mixedFeatureSpace(
            double[] yCentered,          // length n
            int[] contParents,           // continuous parent indices
            int[] discParents,           // 0 or 1 discrete parent indices
            int[] rows,                  // null or length n
            int n,
            int mFeatures,
            double bw2,
            double sigma2,
            long seed
    ) {
        // This is essentially your original gpLogMarginalLikelihoodRFFMixed,
        // but with two small improvements:
        //  (i) For 1 discrete parent, preallocate the Kronecker temp buffer once per call.
        //  (ii) For 1 discrete parent, avoid building CatFeatureMap (Cholesky on L×L) by using
        //       your existing buildCatMap code as-is (keeps exact same semantics).
        //
        // I kept your original computations for G, v, yTy, B=G+sigma2I, logdet, solves.

        final int dc = contParents.length;

        double[][] W = null;
        double[] b = null;

        double wStd = 1.0;
        if (dc > 0) {
            if (!(bw2 > 0) || !Double.isFinite(bw2)) bw2 = 1.0;
            wStd = Math.sqrt(2.0 / bw2);
        }
        final double contScale = Math.sqrt(2.0 / mFeatures);

        SplittableRandom rng = new SplittableRandom(seed);

        if (dc > 0) {
            if (featureType == FeatureType.RFF) {
                W = new double[mFeatures][dc];
                b = new double[mFeatures];
                for (int j = 0; j < mFeatures; j++) {
                    for (int k = 0; k < dc; k++) W[j][k] = wStd * nextGaussian(rng);
                    b[j] = 2.0 * Math.PI * rng.nextDouble();
                }
            } else {
                W = sampleOrthogonalW(mFeatures, dc, wStd, rng);
                b = new double[mFeatures];
                for (int j = 0; j < mFeatures; j++) b[j] = 2.0 * Math.PI * rng.nextDouble();
            }
        } else {
            b = new double[mFeatures];
            for (int j = 0; j < mFeatures; j++) b[j] = 2.0 * Math.PI * rng.nextDouble();
        }

        // categorical map(s): at most one here by construction
        final CatFeatureMap[] catMaps = new CatFeatureMap[discParents.length];
        for (int t = 0; t < discParents.length; t++) {
            int var = discParents[t];
            int[] vals = extractDiscrete(var, rows, n);
            catMaps[t] = buildCatMap(vals, catRho);
            if (catMaps[t] == null) return Double.NaN;
        }

        int catDim = 1;
        for (CatFeatureMap fm : catMaps) {
            if (fm.L > 50) return Double.NaN;
            long prod = (long) catDim * (long) fm.L;
            if (prod > 200_000L) return Double.NaN;
            catDim *= fm.L;
        }
        final int mTotal = mFeatures * catDim;

        DMatrixRMaj G = new DMatrixRMaj(mTotal, mTotal);
        double[] v = new double[mTotal];

        double yTy = 0.0;

        double[] phiCont = new double[mFeatures];
        double[] phiMix = new double[mTotal];

        // Preallocate Kronecker buffer once (big GC win).
        // For 0 discrete parents, unused. For 1 discrete parent, size = mFeatures * L.
        double[] kronTmp = (discParents.length == 1) ? new double[mTotal] : null;

        for (int ii = 0; ii < n; ii++) {
            int row = (rows == null) ? ii : rows[ii];

            // continuous features
            if (dc == 0) {
                for (int j = 0; j < mFeatures; j++) phiCont[j] = contScale * Math.cos(b[j]);
            } else {
                for (int j = 0; j < mFeatures; j++) {
                    double dot = 0.0;
                    double[] wj = W[j];
                    for (int k = 0; k < dc; k++) dot += wj[k] * zCols[contParents[k]][row];
                    phiCont[j] = contScale * Math.cos(dot + b[j]);
                }
            }

            // mixed (Kronecker across discrete parents)
            int curLen = mFeatures;
            System.arraycopy(phiCont, 0, phiMix, 0, mFeatures);

            for (CatFeatureMap fm : catMaps) {
                double[] catFeat = fm.featureForRow(ii); // length L
                int Llev = fm.L;
                int newLen = curLen * Llev;

                // use preallocated buffer if possible (1 discrete parent case)
                double[] tmp = (kronTmp != null && kronTmp.length == newLen) ? kronTmp : new double[newLen];

                int pos = 0;
                for (int a = 0; a < Llev; a++) {
                    double ca = catFeat[a];
                    for (int j = 0; j < curLen; j++) {
                        tmp[pos++] = ca * phiMix[j];
                    }
                }
                System.arraycopy(tmp, 0, phiMix, 0, newLen);
                curLen = newLen;
            }

            if (curLen != mTotal) return Double.NaN;

            double yi = yCentered[ii];
            yTy += yi * yi;

            for (int j = 0; j < mTotal; j++) v[j] += phiMix[j] * yi;

            for (int j = 0; j < mTotal; j++) {
                double pj = phiMix[j];
                for (int k = 0; k <= j; k++) {
                    G.add(j, k, pj * phiMix[k]);
                }
            }
        }

        for (int j = 0; j < mTotal; j++) {
            for (int k = 0; k < j; k++) G.set(k, j, G.get(j, k));
        }

        // B = G + sigma2 I
        DMatrixRMaj B = G;
        for (int j = 0; j < mTotal; j++) B.add(j, j, sigma2);

        CholeskyDecomposition_F64<DMatrixRMaj> chol = DecompositionFactory_DDRM.chol(true);
        if (!chol.decompose(B)) return Double.NaN;
        DMatrixRMaj L = chol.getT(null);

        double logDetB = 0.0;
        for (int i = 0; i < mTotal; i++) {
            double di = L.get(i, i);
            if (!(di > 0) || !Double.isFinite(di)) return Double.NaN;
            logDetB += Math.log(di);
        }
        logDetB *= 2.0;

        double[] u = Arrays.copyOf(v, mTotal);

        // forward: L u = v
        for (int i = 0; i < mTotal; i++) {
            double sum = u[i];
            for (int j = 0; j < i; j++) sum -= L.get(i, j) * u[j];
            u[i] = sum / L.get(i, i);
        }
        // back: L^T w = u
        for (int i = mTotal - 1; i >= 0; i--) {
            double sum = u[i];
            for (int j = i + 1; j < mTotal; j++) sum -= L.get(j, i) * u[j];
            u[i] = sum / L.get(i, i);
        }

        double vTBInvV = 0.0;
        for (int j = 0; j < mTotal; j++) vTBInvV += v[j] * u[j];

        double invSig = 1.0 / sigma2;
        double quad = invSig * yTy - (invSig * invSig) * vTBInvV;

        // log|C| = (n - mTotal) log sigma2 + log|B|
        double logDetC = (n - mTotal) * Math.log(sigma2) + logDetB;

        if (!Double.isFinite(quad) || !Double.isFinite(logDetC)) return Double.NaN;

        return -0.5 * quad - 0.5 * logDetC;
    }

    // -------------------- median heuristic on continuous part --------------------

    private int[] validRowsMixed(int[] vars) {
        int n = sampleSize;
        int[] tmp = new int[n];
        int m = 0;

        outer:
        for (int r = 0; r < n; r++) {
            for (int v : vars) {
                if (isDiscrete[v]) {
                    int dv = dCols[v][r];
                    if (isMissingDiscrete(dv)) continue outer;
                } else {
                    double val = zCols[v][r];
                    if (Double.isNaN(val)) continue outer;
                }
            }
            tmp[m++] = r;
        }
        return Arrays.copyOf(tmp, m);
    }

    // -------------------- cache + utilities --------------------

    private double[] extract1DContinuous(int varIndex, int[] rows, int n) {
        if (isDiscrete[varIndex]) {
            // If someone scores a discrete target with this, we refuse (for now).
            return new double[n]; // will lead to nonsense; better to return NaN upstream if desired.
        }
        double[] x = new double[n];
        if (rows == null) {
            for (int r = 0; r < n; r++) x[r] = zCols[varIndex][r];
        } else {
            for (int r = 0; r < n; r++) x[r] = zCols[varIndex][rows[r]];
        }
        return x;
    }

    private int[] extractDiscrete(int varIndex, int[] rows, int n) {
        int[] x = new int[n];
        if (rows == null) {
            System.arraycopy(dCols[varIndex], 0, x, 0, n);
        } else {
            for (int r = 0; r < n; r++) x[r] = dCols[varIndex][rows[r]];
        }
        return x;
    }

//    private void resetCache() {
//        localScoreCacheRef.set(new ConcurrentHashMap<>());
//    }

    //    private void resetCache() {
//        localScoreCacheRef.set(new ConcurrentHashMap<>());
//        bw2Cache.clear();
//    }
    private void resetCache() {
        // Local score cache
        localScoreCacheRef.set(new ConcurrentHashMap<>());

        // Old-style parent-set-coupled bandwidth caches
        bw2Cache.clear();                 // median bw^2 (full parent-set key)
        bw2OptCache.clear();              // optimized bw^2 (full parent-set key)

        // New-style target+continuous-parent–coupled caches
        bw2MedByTargetContCache.clear();  // median bw^2 keyed by (target, contParents)
        bw2OptByTargetContCache.clear();  // optimized bw^2 keyed by (target, contParents)
    }

    private long seedFor(int targetIndex, long cacheKey) {
        // If coupled: same feature basis for all parent sets of this target.
        // If not: old behavior (seed changes with parent set).
        long s = coupleFeaturesByTarget ? (baseSeed ^ (long) targetIndex) : (baseSeed ^ cacheKey);
        return mix64(s);
    }

    /**
     * Appends an integer value to the end of an integer array.
     *
     * @param z the original array to which the value will be appended
     * @param x the integer value to append to the array
     * @return a new array containing all elements of the original array with the appended value at the end
     */
    public int[] append(int[] z, int x) {
        int[] out = Arrays.copyOf(z, z.length + 1);
        out[z.length] = x;
        return out;
    }

    /**
     * Represents the type of features used in the model.
     * <p>
     * FeatureType is an enumeration that indicates the feature representation
     * applied during model computations, especially in scenarios involving
     * random Fourier features or related feature-based approximations.
     * This enumeration is utilized in methods and configurations that require
     * such distinctions.
     */
    public enum FeatureType {
        /**
         * Represents the Random Fourier Features (RFF) option within the enumeration.
         * <p>
         * Random Fourier Features are used for approximating kernel methods by
         * mapping input data into a randomized feature space. This technique is
         * commonly employed in machine learning to efficiently calculate approximate
         * kernel functions for large-scale datasets.
         */
        RFF,

        /**
         * Represents the Orthogonal Random Features (ORF) option within the enumeration.
         * <p>
         * Orthogonal Random Features are a variation of Random Fourier Features (RFF),
         * designed to improve the approximation of kernel methods by introducing
         * orthogonality constraints in the feature representation. This technique
         * is commonly employed in machine learning to enhance model performance
         * and reduce variance during computation, particularly for large-scale datasets.
         */
        ORF
    }

    // -------------------- discrete reading --------------------

    private static final class CatFeatureMap {
        final int L;
        final int[] levelOfRow;     // maps each row (0..n-1) to 0..L-1
        final double[][] A;         // L x L, rows are features for each level

        CatFeatureMap(int L, int[] levelOfRow, double[][] A) {
            this.L = L;
            this.levelOfRow = levelOfRow;
            this.A = A;
        }

        double[] featureForRow(int rowIndexWithinActive) {
            int lev = levelOfRow[rowIndexWithinActive];
            return A[lev];
        }
    }
}