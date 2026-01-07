package edu.cmu.tetrad.search.score;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.EffectiveSampleSizeSettable;
import edu.cmu.tetrad.util.TetradLogger;
import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.CommonOps_DDRM;
import org.ejml.dense.row.factory.DecompositionFactory_DDRM;
import org.ejml.dense.row.factory.LinearSolverFactory_DDRM;
import org.ejml.interfaces.decomposition.CholeskyDecomposition_F64;
import org.ejml.interfaces.linsol.LinearSolverDense;

import java.util.*;

/**
 * RCIT-inspired local score for continuous variables.
 *
 * <strong>Idea:</strong>
 * <ul>
 *   <li>
 *     Map parent set <code>Z</code> to random Fourier features
 *     <code>&Phi;(Z)</code> for an RBF kernel (as in RCIT/RCoT).
 *   </li>
 *   <li>
 *     Fit ridge regression of target <code>X</code> on <code>&Phi;(Z)</code>.
 *   </li>
 *   <li>
 *     Use Gaussian log-likelihood with
 *     <code>&sigma;2 = RSS / n</code>.
 *   </li>
 *   <li>
 *     Penalize with a BIC-like term using the effective degrees of freedom
 *     under ridge regression:
 *     <pre>
 * df_eff = tr( A (A + &lambda; I)-1 ),
 * where A = &Phi;T &Phi;
 *     </pre>
 *   </li>
 * </ul>
 *
 * <p>
 * The score convention matches Tetrad: <strong>higher is better</strong>.
 * </p>
 *
 * <p>
 * <strong>Notes:</strong>
 * </p>
 *
 * <ul>
 *   <li>
 *     Missingness is handled by filtering rows where all variables in
 *     <code>{target} &cup; parents</code> are observed.
 *   </li>
 *   <li>
 *     Bandwidth <code>&sigma;</code> can be chosen using one of the following strategies:
 *     <ul>
 *       <li>
 *         <code>PER_VARIABLE_MEDIAN</code>:
 *         precompute <code>&sigma;<sub>j</sub></code> per variable, then take the median
 *         over parent variables.
 *       </li>
 *       <li>
 *         <code>PARENT_SET_MEDIAN</code>:
 *         compute the median pairwise distance in the parent space
 *         (as in RCIT).
 *       </li>
 *     </ul>
 *   </li>
 *   <li>
 *     Uses EJML with Cholesky factorizations and linear solves, avoiding
 *     explicit matrix inverses.
 *   </li>
 * </ul>
 */
public final class KcvBicScore implements Score, EffectiveSampleSizeSettable {

    // -------------------- tuning knobs --------------------

    private final DataSet dataSet;
    private final List<Node> variables;
    private final Map<Node, Integer> indexMap;
    private final int sampleSize;
    private final boolean calculateRowSubsets;
    /**
     * Cached columns cols[var][row] (may contain NaNs).
     */
    private final double[][] cols;
    /**
     * Precomputed per-variable bandwidths (median pairwise distance), used in PER_VARIABLE_MEDIAN mode.
     */
    private final double[] sigmaPerVar;
    /**
     * Optional score cache.
     */
    private final Map<Long, Double> localScoreCache = new HashMap<>();

    // -------------------- data --------------------
    /**
     * #RFF features for parent set Z (dimension of Phi(Z)).
     */
    private int numFeatZ = 200;
    /**
     * Ridge parameter added to Phi^T Phi (must be > 0).
     */
    private double lambda = 1e-4;
    /**
     * Penalty discount multiplier (like SemBicScore).
     */
    private double penaltyDiscount = 1.0;
    /**
     * Whether to z-score raw variables and also z-score RFF columns.
     */
    private boolean centerFeatures = true;
    /**
     * RNG seed (score is deterministic per (target, parents) given seed).
     */
    private long seed = 1729L;
    /**
     * Max rows used for bandwidth computation (RCIT uses 500).
     */
    private int maxBandwidthRows = 500;
    /**
     * Bandwidth selection mode.
     */
    private BandwidthMode bandwidthMode = BandwidthMode.PER_VARIABLE_MEDIAN;
    /**
     * Small jitter added to A+lambdaI if Cholesky fails.
     */
    private double jitter = 1e-10;
    /**
     * Effective sample size.
     */
    private int nEff;

    // -------------------- construction --------------------

    /**
     * Constructor for the KcvBicScore class. Initializes the object using the provided dataset
     * and precomputes necessary parameters for the score calculations.
     *
     * @param dataSet The dataset to be used for the score computation. It must not be null.
     *                The dataset includes the variables, their values, and other metadata needed
     *                for scoring. The rows of the dataset correspond to samples, and the columns
     *                correspond to variables.
     */
    public KcvBicScore(DataSet dataSet) {
        this.dataSet = Objects.requireNonNull(dataSet, "dataSet");
        this.variables = dataSet.getVariables();
        this.sampleSize = dataSet.getNumRows();
        setEffectiveSampleSize(-1);

        this.indexMap = new HashMap<>();
        for (int i = 0; i < variables.size(); i++) indexMap.put(variables.get(i), i);

        this.calculateRowSubsets = dataSet.existsMissingValue();

        int p = variables.size();
        this.cols = new double[p][sampleSize];
        for (int j = 0; j < p; j++) {
            for (int r = 0; r < sampleSize; r++) {
                cols[j][r] = dataSet.getDouble(r, j);
            }
        }

        // Precompute sigma per variable (median pairwise distance on up to maxBandwidthRows rows),
        // ignoring NaNs.
        this.sigmaPerVar = new double[p];
        for (int j = 0; j < p; j++) {
            this.sigmaPerVar[j] = medianPairwiseDistance1D(j, null, Math.min(sampleSize, maxBandwidthRows));
            if (!(sigmaPerVar[j] > 0) || !Double.isFinite(sigmaPerVar[j])) sigmaPerVar[j] = 1.0;
        }
    }

    // -------------------- Score interface --------------------

    /**
     * Median of pairwise squared Euclidean distances for an n×d matrix Z. Uses all pairs (i<j). Returns the median of
     * strictly-positive distances if possible, otherwise returns 1.0 as a safe fallback.
     */
    private static double medianDistanceSquaredND(double[][] Z) {
        int n = Z.length;
        if (n < 3) return 1.0;
        int d = Z[0].length;
        if (d == 0) return 1.0;

        // Upper-triangular pairwise squared distances
        double[] d2 = new double[n * (n - 1) / 2];
        int idx = 0;

        for (int i = 1; i < n; i++) {
            double[] Zi = Z[i];
            for (int j = 0; j < i; j++) {
                double[] Zj = Z[j];
                double dist2 = 0.0;
                for (int k = 0; k < d; k++) {
                    double diff = Zi[k] - Zj[k];
                    dist2 += diff * diff;
                }
                d2[idx++] = dist2;
            }
        }

        Arrays.sort(d2, 0, idx);

        // Find first strictly-positive distance (ignore zeros if possible)
        int firstPos = 0;
        while (firstPos < idx && d2[firstPos] <= 0.0) firstPos++;

        if (firstPos >= idx) return 1.0;

        int mid = firstPos + (idx - firstPos) / 2;
        double med = d2[mid];

        return (med > 0.0 && Double.isFinite(med)) ? med : 1.0;
    }

    /**
     * In-place centering of a symmetric Gram matrix:
     * <p>
     * K <- H K H ,  where H = I - (1/n) 11^T
     * <p>
     * i.e.  K_ij <- K_ij - rowMean_i - colMean_j + grandMean
     * <p>
     * Assumes K is n x n.
     */
    private static void centerInPlace(DMatrixRMaj K) {
        final int n = K.numRows;
        if (n == 0) return;

        double[] rowMean = new double[n];
        double[] colMean = new double[n];
        double grand = 0.0;

        // Compute row sums, column sums, and grand sum
        for (int i = 0; i < n; i++) {
            double rs = 0.0;
            for (int j = 0; j < n; j++) {
                double v = K.get(i, j);
                rs += v;
                colMean[j] += v;
                grand += v;
            }
            rowMean[i] = rs;
        }

        final double invN = 1.0 / n;
        final double invN2 = invN * invN;

        // Convert sums -> means
        for (int i = 0; i < n; i++) {
            rowMean[i] *= invN;
            colMean[i] *= invN;
        }
        grand *= invN2;

        // Center in-place
        for (int i = 0; i < n; i++) {
            double rmi = rowMean[i];
            for (int j = 0; j < n; j++) {
                double v = K.get(i, j) - rmi - colMean[j] + grand;
                K.set(i, j, v);
            }
        }
    }

    /**
     * Median of pairwise squared distances for 1D data. Uses a uniform subsample without replacement of size m (<= n)
     * for speed.
     *
     * @param x    length-n data
     * @param m    number of points used for bandwidth estimation (<= n)
     * @param seed RNG seed for reproducibility
     */
    private static double medianPairwiseSquaredDistance1D(double[] x, int m, long seed) {
        final int n = x.length;
        if (n < 3) return 1.0;
        if (m < 3) m = Math.min(n, 3);

        // Choose m indices without replacement (partial Fisher–Yates)
        int[] idx = new int[n];
        for (int i = 0; i < n; i++) idx[i] = i;
        Random rng = new Random(seed);
        for (int i = 0; i < m; i++) {
            int j = i + rng.nextInt(n - i);
            int t = idx[i];
            idx[i] = idx[j];
            idx[j] = t;
        }

        // Collect pairwise squared distances among subsample
        int L = m * (m - 1) / 2;
        double[] d2 = new double[L];
        int k = 0;
        for (int a = 1; a < m; a++) {
            double xa = x[idx[a]];
            for (int b = 0; b < a; b++) {
                double d = xa - x[idx[b]];
                d2[k++] = d * d;
            }
        }

        Arrays.sort(d2, 0, k);

        // Prefer median of positive entries, if possible
        int firstPos = 0;
        while (firstPos < k && d2[firstPos] <= 0.0) firstPos++;
        if (firstPos >= k) return 1.0;

        int mid = firstPos + (k - firstPos) / 2;
        return d2[mid];
    }

    /**
     * trace of a square matrix.
     */
    private static double trace(DMatrixRMaj A) {
        int n = Math.min(A.numRows, A.numCols);
        double s = 0.0;
        for (int i = 0; i < n; i++) s += A.get(i, i);
        return s;
    }

    /**
     * A <- (A + A^T)/2 for numeric symmetry.
     */
    private static void symmetrizeInPlace(DMatrixRMaj A) {
        int n = A.numRows;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < i; j++) {
                double v = 0.5 * (A.get(i, j) + A.get(j, i));
                A.set(i, j, v);
                A.set(j, i, v);
            }
        }
    }

    private static double gaussianLogLikFromRss(double[] y, int n, double rss) {
        double sigma2 = rss / n;
        return -0.5 * n * (Math.log(2.0 * Math.PI * sigma2) + 1.0);
    }

    private static double rssZeroModel(double[] y) {
        // if centered, mean ~ 0; otherwise compute mean
        double mean = 0.0;
        for (double v : y) mean += v;
        mean /= y.length;
        double rss = 0.0;
        for (double v : y) {
            double e = v - mean;
            rss += e * e;
        }
        return rss;
    }

    private static double rssFromPhiBeta(DMatrixRMaj Phi, DMatrixRMaj beta, double[] y) {
        int n = Phi.numRows;
        int m = Phi.numCols;
        double rss = 0.0;

        for (int i = 0; i < n; i++) {
            double fit = 0.0;
            int idx = i * m;
            for (int j = 0; j < m; j++) {
                fit += Phi.data[idx + j] * beta.get(j, 0);
            }
            double e = y[i] - fit;
            rss += e * e;
        }
        return rss;
    }

    private static void addDiagonalInPlace(DMatrixRMaj M, double v) {
        int n = Math.min(M.numRows, M.numCols);
        for (int i = 0; i < n; i++) {
            M.add(i, i, v);
        }
    }

    /**
     * RFF features for RBF kernel: Phi = sqrt(2/m) * cos( W z + b ) with W ~ N(0, 1/sigma), b ~ Uniform(0, 2π).
     */
    private static DMatrixRMaj rffFeatures(double[][] Z, int n, int d, int m, double sigma, Random rng) {
        double sd = 1.0 / sigma;

        // W is m x d, b is m
        double[][] W = new double[m][d];
        double[] b = new double[m];

        for (int i = 0; i < m; i++) {
            for (int j = 0; j < d; j++) W[i][j] = rng.nextGaussian() * sd;
            b[i] = rng.nextDouble() * 2.0 * Math.PI;
        }

        DMatrixRMaj Phi = new DMatrixRMaj(n, m);
        double scale = Math.sqrt(2.0 / m);

        for (int i = 0; i < n; i++) {
            double[] zi = Z[i];
            for (int f = 0; f < m; f++) {
                double dot = 0.0;
                double[] wf = W[f];
                for (int j = 0; j < d; j++) dot += wf[j] * zi[j];
                Phi.set(i, f, scale * Math.cos(dot + b[f]));
            }
        }

        return Phi;
    }

    private static void zscoreInPlace(double[] x) {
        int n = x.length;
        if (n < 2) return;
        double sum = 0.0, sumsq = 0.0;
        for (double v : x) {
            sum += v;
            sumsq += v * v;
        }
        double mean = sum / n;
        double var = (sumsq - n * mean * mean) / (n - 1);
        double sd = (var > 0) ? Math.sqrt(var) : 1.0;
        for (int i = 0; i < n; i++) x[i] = (x[i] - mean) / sd;
    }

    private static void zscoreInPlace(double[][] X) {
        int n = X.length;
        if (n == 0) return;
        int d = X[0].length;
        if (n < 2 || d == 0) return;

        for (int j = 0; j < d; j++) {
            double sum = 0.0, sumsq = 0.0;
            for (int i = 0; i < n; i++) {
                double v = X[i][j];
                sum += v;
                sumsq += v * v;
            }
            double mean = sum / n;
            double var = (sumsq - n * mean * mean) / (n - 1);
            double sd = (var > 0) ? Math.sqrt(var) : 1.0;

            for (int i = 0; i < n; i++) {
                X[i][j] = (X[i][j] - mean) / sd;
            }
        }
    }

    private static void zscoreColumnsInPlace(DMatrixRMaj M) {
        int n = M.numRows, d = M.numCols;
        if (n < 2 || d == 0) return;

        for (int j = 0; j < d; j++) {
            double sum = 0.0, sumsq = 0.0;
            for (int i = 0; i < n; i++) {
                double v = M.get(i, j);
                sum += v;
                sumsq += v * v;
            }
            double mean = sum / n;
            double var = (sumsq - n * mean * mean) / (n - 1);
            double sd = (var > 0) ? Math.sqrt(var) : 1.0;

            for (int i = 0; i < n; i++) {
                M.set(i, j, (M.get(i, j) - mean) / sd);
            }
        }
    }

    private static double medianPairwiseDistanceND(double[][] Z, int limit) {
        int n = Math.min(Z.length, limit);
        if (n < 3) return 1.0;
        int d = Z[0].length;
        if (d == 0) return 1.0;

        double[] dists = new double[n * (n - 1) / 2];
        int idx = 0;

        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                double ss = 0.0;
                for (int k = 0; k < d; k++) {
                    double diff = Z[i][k] - Z[j][k];
                    ss += diff * diff;
                }
                double dist = Math.sqrt(ss);
                if (dist > 0 && Double.isFinite(dist)) dists[idx++] = dist;
            }
        }
        if (idx == 0) return 1.0;

        Arrays.sort(dists, 0, idx);
        return dists[idx / 2];
    }

    private static void multTransA_vec(DMatrixRMaj A, double[] x, DMatrixRMaj out) {
        // out = A^T x, where A is n x m, x is n
        int n = A.numRows;
        int m = A.numCols;
        if (out.numRows != m || out.numCols != 1) throw new IllegalArgumentException("out dim mismatch");

        Arrays.fill(out.data, 0.0);
        for (int i = 0; i < n; i++) {
            double xi = x[i];
            int idx = i * m;
            for (int j = 0; j < m; j++) {
                out.data[j] += A.data[idx + j] * xi;
            }
        }
    }

    private static int[] concat(int i, int[] parents) {
        int[] all = new int[parents.length + 1];
        all[0] = i;
        System.arraycopy(parents, 0, all, 1, parents.length);
        return all;
    }

    private static long cacheKey(int target, int[] parents) {
        long h = 1469598103934665603L;
        h = (h ^ target) * 1099511628211L;
        for (int p : parents) h = (h ^ p) * 1099511628211L;
        return h;
    }

    /**
     * Computes the local score difference for a specific variable and its parent set.
     * This method evaluates the change in score when a new variable is added to the parent set.
     *
     * @param x The variable being evaluated for inclusion in the parent set.
     * @param y The target variable for which the local score is computed.
     * @param z The current set of parent variables for the target variable.
     * @return The difference in local score after including the variable {@code x} in the parent set.
     */
    @Override
    public double localScoreDiff(int x, int y, int[] z) {
        return localScore(y, append(z, x)) - localScore(y, z);
    }

    // -------------------- parameters (getters/setters) --------------------

    /**
     * Computes the local score for a given variable and its parent variables based on a kernel-based
     * regression model. The score is calculated as the combination of a log-likelihood term and a
     * penalty term to balance model fit and complexity.
     *
     * @param i         The index of the target variable for which the score is being evaluated.
     * @param parents   The indices of the parent variables of the target variable. Can be empty if
     *                  there are no parents.
     * @return          The computed local score for the given target variable and its parents.
     */
    @Override
    public double localScore(int i, int... parents) {
        Arrays.sort(parents);

        // Cache
        long key = cacheKey(i, parents);
        Double cached = localScoreCache.get(key);
        if (cached != null) return cached;

        // Valid rows for {i} ∪ parents, only if missing values exist.
        int[] all = concat(i, parents);
        int[] rows = calculateRowSubsets ? validRows(all) : null;

        final int n = (rows == null) ? nEff : rows.length;
        if (n < 5) return Double.NaN;

        // --- KX (centered) ---
        DMatrixRMaj Kx = rbfGram1D(i, rows);
        centerInPlace(Kx);

        // --- KZ (centered) ---
        DMatrixRMaj Kz;
        if (parents.length == 0) {
            // No parents => M = I (no residualization), edf = 0
            Kz = null;
        } else {
            Kz = rbfGramND(parents, rows);
            centerInPlace(Kz);
        }

        // --- Kernel ridge regression residual-maker ---
        // H = Kz (Kz + lambda I)^-1, M = I - H
        // RX = M Kx M
        // ll ≈ -0.5 * n * log( tr(RX)/n )
        // penalty ≈ -0.5 * log(n) * edf, where edf = tr(H)
        final double varianceFloor = 1e-12;

        double ll;
        double pen;

        if (parents.length == 0) {
            // RX = Kx
            double v = Math.max(trace(Kx) / n, varianceFloor);
            ll = -0.5 * n * Math.log(v);
            pen = 0.0;
        } else {
            // A = Kz + lambda I
            DMatrixRMaj A = Kz.copy();
            for (int d = 0; d < n; d++) A.add(d, d, lambda);

            // Invert A stably (SPD expected due to +lambda I)
            DMatrixRMaj Inv = CommonOps_DDRM.identity(n);
            LinearSolverDense<DMatrixRMaj> solver = LinearSolverFactory_DDRM.symmPosDef(n);

            boolean ok = solver.setA(A);
            if (ok) {
                solver.invert(Inv);
            } else {
                // Fallback: add diagonal jitter and try again, then generic inverse as last resort.
                DMatrixRMaj Aj = A.copy();
                for (int d = 0; d < n; d++) Aj.add(d, d, 1e-8);

                ok = solver.setA(Aj);
                if (ok) {
                    solver.invert(Inv);
                } else {
                    // Last resort
                    Inv = Aj;
                    CommonOps_DDRM.invert(Inv);
                }
            }

            // H = Kz * Inv
            DMatrixRMaj H = new DMatrixRMaj(n, n);
            CommonOps_DDRM.mult(Kz, Inv, H);
            symmetrizeInPlace(H);

            // M = I - H
            DMatrixRMaj M = CommonOps_DDRM.identity(n);
            CommonOps_DDRM.subtractEquals(M, H);
            symmetrizeInPlace(M);

            // RX = M * Kx * M
            DMatrixRMaj tmp = new DMatrixRMaj(n, n);
            DMatrixRMaj RX = new DMatrixRMaj(n, n);
            CommonOps_DDRM.mult(M, Kx, tmp);
            CommonOps_DDRM.mult(tmp, M, RX);
            symmetrizeInPlace(RX);

            // Fit proxy
            double v = Math.max(trace(RX) / n, varianceFloor);
            ll = -0.5 * n * Math.log(v);

            // Complexity proxy: edf = tr(H)
            double edf = Math.max(1e-12, trace(H));
            pen = -0.5 * edf * Math.log(Math.max(3.0, n));
            // If you already have a "penaltyDiscount" field, multiply here:
            // pen *= penaltyDiscount;
        }

        double score = ll + pen;

        localScoreCache.put(key, score);
        return score;
    }

    /**
     * RBF Gram matrix for a multivariate parent set (ND):
     * <p>
     * K_ij = exp( -||z_i - z_j||^2 / bw2 )
     * <p>
     * where bw2 is the median of pairwise squared distances (robust heuristic).
     *
     * @param parentIdx column indices of parents (already sorted upstream is fine but not required here)
     * @param rows      optional subset of dataset row indices; if null, uses rows 0..(nEff-1)
     * @return n x n Gram matrix
     */
    private DMatrixRMaj rbfGramND(int[] parentIdx, int[] rows) {
        final int n = (rows == null) ? nEff : rows.length;
        final int d = parentIdx.length;

        // Edge case: no parents => zero Gram (caller often special-cases anyway)
        if (d == 0) return new DMatrixRMaj(n, n);

        // Extract Z matrix: n x d
        double[][] Z = new double[n][d];
        if (rows == null) {
            for (int r = 0; r < n; r++) {
                for (int j = 0; j < d; j++) {
                    Z[r][j] = cols[parentIdx[j]][r];
                }
            }
        } else {
            for (int r = 0; r < n; r++) {
                int row = rows[r];
                for (int j = 0; j < d; j++) {
                    Z[r][j] = cols[parentIdx[j]][row];
                }
            }
        }

        double bw2 = medianDistanceSquaredND(Z);
        if (!(bw2 > 0.0) || !Double.isFinite(bw2)) bw2 = 1.0;
        final double invBw = 1.0 / bw2;

        DMatrixRMaj K = new DMatrixRMaj(n, n);

        for (int i = 0; i < n; i++) {
            K.set(i, i, 1.0);
            for (int j = 0; j < i; j++) {
                double dist2 = 0.0;

                // squared Euclidean distance in parent space
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

    /**
     * RBF Gram matrix for a single variable (1D): K_ij = exp( -(xi-xj)^2 / bw2 ).
     * <p>
     * Bandwidth heuristic: bw2 = median_{i<j} (xi-xj)^2   on up to maxBWPoints points.
     * <p>
     * If bw2 is degenerate (<=0 / NaN / Inf), falls back to 1.0.
     *
     * @param varIndex column index of the variable in cols[][]
     * @param rows     subset of dataset rows to use; if null uses first nEff rows
     */
    private DMatrixRMaj rbfGram1D(int varIndex, int[] rows) {
        final int n = (rows == null) ? nEff : rows.length;

        // Extract x
        final double[] x = new double[n];
        if (rows == null) {
            for (int r = 0; r < n; r++) x[r] = cols[varIndex][r];
        } else {
            for (int r = 0; r < n; r++) x[r] = cols[varIndex][rows[r]];
        }

        // Bandwidth: median squared distance (subsampled for speed)
        final int maxBWPoints = 256;  // tune: 128–512 are all reasonable
        double bw2 = medianPairwiseSquaredDistance1D(x, Math.min(n, maxBWPoints), 1729);

        // Robust fallback
        if (!(bw2 > 0.0) || !Double.isFinite(bw2)) bw2 = 1.0;

        // Optional: keep bw2 from being *too* tiny to avoid near-identity kernels
        // (especially with small n or quantized data)
        final double minBw2 = 1e-12;
        if (bw2 < minBw2) bw2 = minBw2;

        final double invBw2 = 1.0 / bw2;

        // Build symmetric Gram
        DMatrixRMaj K = new DMatrixRMaj(n, n);
        for (int i = 0; i < n; i++) {
            K.set(i, i, 1.0);
            double xi = x[i];
            for (int j = 0; j < i; j++) {
                double d = xi - x[j];
                double v = Math.exp(-(d * d) * invBw2);
                K.set(i, j, v);
                K.set(j, i, v);
            }
        }
        return K;
    }

    /**
     * Retrieves the list of variables used in the computation or associated with this instance.
     *
     * @return A list of {@code Node} objects representing the variables.
     */
    @Override
    public List<Node> getVariables() {
        return new ArrayList<>(variables);
    }

    /**
     * Retrieves the sample size from the associated dataset.
     *
     * @return The number of rows in the dataset, representing the sample size.
     */
    @Override
    public int getSampleSize() {
        return dataSet.getNumRows();
    }

    /**
     * Retrieves the maximum degree allowed for variables in the computation.
     *
     * @return The maximum degree as an integer.
     */
    @Override
    public int getMaxDegree() {
        return (int) Math.ceil(Math.log(Math.max(3, nEff)));
    }

    /**
     * Determines whether the given node yNode is influenced by the list of nodes z
     * based on a calculated local score.
     *
     * @param z the list of nodes representing potential influencing factors
     * @param yNode the target node to evaluate for influence
     * @return true if the local score is NaN, infinite, or an exception occurs; false otherwise
     */
    @Override
    public boolean determines(List<Node> z, Node yNode) {
        int i = variables.indexOf(yNode);
        int[] parents = new int[z.size()];
        for (int t = 0; t < z.size(); t++) parents[t] = variables.indexOf(z.get(t));

        try {
            double s = localScore(i, parents);
            return Double.isNaN(s) || Double.isInfinite(s);
        } catch (RuntimeException e) {
            TetradLogger.getInstance().log(e.getMessage());
            return true;
        }
    }

    /**
     * Determines whether a given bump value corresponds to an effect edge.
     *
     * @param bump the numeric value representing the bump to evaluate
     * @return true if the bump value is greater than 0, otherwise false
     */
    @Override
    public boolean isEffectEdge(double bump) {
        return bump > 0;
    }

    /**
     * Retrieves the current data model associated with this instance.
     *
     * @return the data model represented as a DataModel object
     */
    public DataModel getDataModel() {
        return dataSet;
    }

    /**
     * Calculates and returns the effective sample size, which is a measure
     * of the number of independent observations in a dataset, potentially
     * accounting for dependencies or weights.
     *
     * @return the effective sample size as an integer value
     */
    @Override
    public int getEffectiveSampleSize() {
        return nEff;
    }

    /**
     * Sets the effective sample size for the model. If the provided value is negative,
     * the effective sample size is set to the default sample size.
     *
     * @param nEff the effective sample size to be used. If negative, defaults
     *             to the full sample size of the dataset.
     */
    @Override
    public void setEffectiveSampleSize(int nEff) {
        this.nEff = (nEff < 0) ? this.sampleSize : nEff;
        clearCache();
    }

    /**
     * Returns a string representation of the object.
     *
     * @return a string that describes the RCIT-RFF Ridge BIC Score for continuous data.
     */
    @Override
    public String toString() {
        return "RCIT-RFF Ridge BIC Score (continuous)";
    }

    /**
     * Retrieves the value of the numFeatZ property.
     *
     * @return the number of features represented by the numFeatZ value.
     */
    public int getNumFeatZ() {
        return numFeatZ;
    }

    /**
     * Sets the number of features for the Z dimension.
     * Ensures the value is at least 1, and updates the internal state by clearing the cache.
     *
     * @param numFeatZ the number of features for the Z dimension;
     *                 must be a positive integer, with a minimum value of 1.
     */
    public void setNumFeatZ(int numFeatZ) {
        this.numFeatZ = Math.max(1, numFeatZ);
        clearCache();
    }

    /**
     * Retrieves the value of the lambda property.
     *
     * @return the regularization parameter lambda.
     */
    public double getLambda() {
        return lambda;
    }

    /**
     * Sets the regularization parameter lambda.
     * Must be positive, otherwise throws an IllegalArgumentException.
     *
     * @param lambda the regularization parameter to set
     */
    public void setLambda(double lambda) {
        if (lambda <= 0) throw new IllegalArgumentException("lambda must be > 0");
        this.lambda = lambda;
        clearCache();
    }

    // -------------------- enum --------------------

    /**
     * Retrieves the penalty discount factor used in the score calculation.
     *
     * @return the penalty discount factor.
     */
    public double getPenaltyDiscount() {
        return penaltyDiscount;
    }

    // -------------------- internals --------------------

    /**
     * Sets the penalty discount factor used in the score calculation.
     *
     * @param penaltyDiscount the penalty discount factor to set, must be positive
     */
    public void setPenaltyDiscount(double penaltyDiscount) {
        if (penaltyDiscount <= 0) throw new IllegalArgumentException("penaltyDiscount must be > 0");
        this.penaltyDiscount = penaltyDiscount;
        clearCache();
    }

    /**
     * Retrieves whether feature centering is enabled.
     *
     * @return true if feature centering is enabled, otherwise false
     */
    public boolean isCenterFeatures() {
        return centerFeatures;
    }

    /**
     * Sets whether feature centering is enabled.
     *
     * @param centerFeatures true to enable feature centering, false to disable
     */
    public void setCenterFeatures(boolean centerFeatures) {
        this.centerFeatures = centerFeatures;
        clearCache();
    }

    /**
     * Retrieves the random seed used for reproducibility.
     *
     * @return the random seed as a long integer
     */
    public long getSeed() {
        return seed;
    }

    /**
     * Sets the random seed for reproducibility.
     *
     * @param seed the random seed to set, must be non-negative
     */
    public void setSeed(long seed) {
        this.seed = seed;
        clearCache();
    }

    /**
     * Retrieves the maximum number of rows to consider for bandwidth estimation.
     *
     * @return the maximum number of rows for bandwidth estimation
     */
    public int getMaxBandwidthRows() {
        return maxBandwidthRows;
    }

    /**
     * Sets the maximum number of rows to consider for bandwidth estimation.
     * Ensures the value is at least 10, and updates the internal state by clearing the cache.
     *
     * @param maxBandwidthRows the maximum number of rows for bandwidth estimation
     */
    public void setMaxBandwidthRows(int maxBandwidthRows) {
        this.maxBandwidthRows = Math.max(10, maxBandwidthRows);
        clearCache();
    }

    /**
     * Retrieves the bandwidth estimation mode.
     *
     * @return the bandwidth estimation mode
     */
    public BandwidthMode getBandwidthMode() {
        return bandwidthMode;
    }

    /**
     * Sets the bandwidth estimation mode.
     * Requires a non-null bandwidthMode, otherwise throws NullPointerException.
     *
     * @param bandwidthMode the bandwidth estimation mode to set
     */
    public void setBandwidthMode(BandwidthMode bandwidthMode) {
        this.bandwidthMode = Objects.requireNonNull(bandwidthMode, "bandwidthMode");
        clearCache();
    }

    /**
     * Retrieves the jitter value used in bandwidth estimation.
     *
     * @return the jitter value
     */
    public double getJitter() {
        return jitter;
    }

    /**
     * Sets the jitter value used in bandwidth estimation.
     * Must be positive, otherwise throws IllegalArgumentException.
     *
     * @param jitter the jitter value to set
     */
    public void setJitter(double jitter) {
        if (jitter <= 0) throw new IllegalArgumentException("jitter must be > 0");
        this.jitter = jitter;
        clearCache();
    }

    private void clearCache() {
        localScoreCache.clear();
    }

    private Random localRng(int target, int[] parents) {
        long h = 1469598103934665603L;
        h = (h ^ seed) * 1099511628211L;
        h = (h ^ target) * 1099511628211L;
        for (int p : parents) h = (h ^ p) * 1099511628211L;
        return new Random(h);
    }

    // -------------------- bandwidth helpers --------------------

    private double chooseSigma(int[] parents, double[][] Z, int n) {
        if (bandwidthMode == BandwidthMode.PER_VARIABLE_MEDIAN) {
            double[] s = new double[parents.length];
            for (int i = 0; i < parents.length; i++) s[i] = sigmaPerVar[parents[i]];
            Arrays.sort(s);
            return s[s.length / 2];
        } else {
            // Parent-set median in d-dim on up to maxBandwidthRows rows
            int r = Math.min(n, maxBandwidthRows);
            return medianPairwiseDistanceND(Z, r);
        }
    }

    /**
     * df = tr( A (A + lambda I)^(-1) ) = tr( X ), where (A + lambda I) X = A.
     */
    private double effectiveDf(DMatrixRMaj A, double lambda) {
        int m = A.numRows;
        DMatrixRMaj M = A.copy();
        addDiagonalInPlace(M, lambda);

        // Solve M X = A
        DMatrixRMaj X = new DMatrixRMaj(m, m);

        LinearSolverDense<DMatrixRMaj> solver = LinearSolverFactory_DDRM.symmPosDef(m);
        if (!solver.setA(M)) {
            // try jitter
            DMatrixRMaj M2 = M.copy();
            addDiagonalInPlace(M2, jitter);
            if (!solver.setA(M2)) return Double.NaN;
        }

        solver.solve(A, X);

        double tr = 0.0;
        for (int i = 0; i < m; i++) tr += X.get(i, i);
        return tr;
    }

    // -------------------- missingness row filtering --------------------

    private boolean solveSymPosDefWithJitter(DMatrixRMaj A, DMatrixRMaj B, DMatrixRMaj X) {
        int n = A.numRows;

        // Prefer a solver
        LinearSolverDense<DMatrixRMaj> solver = LinearSolverFactory_DDRM.symmPosDef(n);
        if (solver.setA(A)) {
            solver.solve(B, X);
            return true;
        }

        // Fall back: try Cholesky with increasing jitter
        double eps = jitter;
        for (int k = 0; k < 6; k++) {
            DMatrixRMaj Aj = A.copy();
            addDiagonalInPlace(Aj, eps);
            CholeskyDecomposition_F64<DMatrixRMaj> chol = DecompositionFactory_DDRM.chol(true);
            if (chol.decompose(Aj)) {
                if (solver.setA(Aj)) {
                    solver.solve(B, X);
                    return true;
                }
            }
            eps *= 10.0;
        }
        return false;
    }

    // -------------------- extraction helpers --------------------

    private double medianPairwiseDistance1D(int varIndex, int[] rows, int limit) {
        // collect up to "limit" non-NaN values
        double[] tmp = new double[limit];
        int m = 0;

        if (rows == null) {
            for (int r = 0; r < sampleSize && m < limit; r++) {
                double v = cols[varIndex][r];
                if (Double.isNaN(v)) continue;
                tmp[m++] = v;
            }
        } else {
            for (int k = 0; k < rows.length && m < limit; k++) {
                double v = cols[varIndex][rows[k]];
                if (Double.isNaN(v)) continue;
                tmp[m++] = v;
            }
        }

        if (m < 3) return 1.0;
        tmp = Arrays.copyOf(tmp, m);

        double[] dists = new double[m * (m - 1) / 2];
        int idx = 0;
        for (int i = 1; i < m; i++) {
            double xi = tmp[i];
            for (int j = 0; j < i; j++) {
                double dist = Math.abs(xi - tmp[j]);
                if (dist > 0 && Double.isFinite(dist)) dists[idx++] = dist;
            }
        }
        if (idx == 0) return 1.0;

        Arrays.sort(dists, 0, idx);
        return dists[idx / 2];
    }

    private int[] validRows(int[] vars) {
        int[] tmp = new int[sampleSize];
        int m = 0;

        outer:
        for (int r = 0; r < sampleSize; r++) {
            for (int v : vars) {
                double val = cols[v][r];
                if (Double.isNaN(val)) continue outer;
            }
            tmp[m++] = r;
        }

        return Arrays.copyOf(tmp, m);
    }

    // -------------------- small utilities --------------------

    private double[] extract1D(int varIndex, int[] rows, int n) {
        double[] x = new double[n];
        if (rows == null) {
            // use first nEff rows (assumed complete)
            for (int i = 0; i < n; i++) x[i] = cols[varIndex][i];
        } else {
            for (int i = 0; i < n; i++) x[i] = cols[varIndex][rows[i]];
        }
        return x;
    }

    private double[][] extractND(int[] vars, int[] rows, int n, int d) {
        double[][] Z = new double[n][d];
        if (rows == null) {
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < d; j++) Z[i][j] = cols[vars[j]][i];
            }
        } else {
            for (int i = 0; i < n; i++) {
                int r = rows[i];
                for (int j = 0; j < d; j++) Z[i][j] = cols[vars[j]][r];
            }
        }
        return Z;
    }

    /**
     * Appends an integer to the end of an array of integers.
     *
     * @param z The list of ints.
     * @param x   The extra int.
     * @return the resulting array with the integer appended
     */
    public int[] append(int[] z, int x) {
        int[] out = Arrays.copyOf(z, z.length + 1);
        out[z.length] = x;
        return out;
    }

    /**
     * Enumeration representing different modes for bandwidth computation.
     */
    public enum BandwidthMode {
        /**
         * Precompute per-variable medians; parent-set sigma = median of parent sigmas.
         */
        PER_VARIABLE_MEDIAN,
        /**
         * Compute median pairwise distance in the parent space (more RCIT-like, more expensive).
         */
        PARENT_SET_MEDIAN
    }
}