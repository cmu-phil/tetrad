package edu.cmu.tetrad.search.score;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.EffectiveSampleSizeSettable;
import edu.cmu.tetrad.util.TetradLogger;
import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.factory.DecompositionFactory_DDRM;
import org.ejml.interfaces.decomposition.CholeskyDecomposition_F64;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Kernel Marginal Likelihood (KML) score for continuous variables.
 *
 * <p>
 * This score implements the exact Gaussian Process (GP) marginal likelihood
 * for evaluating candidate parent sets in score-based causal discovery.
 * For a target variable {@code Y} and parent set {@code Z}, the model is
 * </p>
 *
 * <pre>
 *   Y = f(Z) + ε,    ε ~ N(0, σ² I)
 *   f ~ GP(0, k(·,·))
 * </pre>
 *
 * <p>
 * where {@code k} is a positive-definite kernel (typically an RBF kernel).
 * The marginal covariance of {@code Y} is
 * </p>
 *
 * <pre>
 *   C = K_Z + σ² I
 * </pre>
 *
 * <p>
 * with {@code K_Z} the kernel Gram matrix over the parent variables.
 * The score (up to an additive constant) is the GP marginal log-likelihood
 * </p>
 *
 * <pre>
 *   S = -0.5 · Yᵀ C⁻¹ Y - 0.5 · log |C|.
 * </pre>
 *
 * <p>
 * Higher scores indicate better fit under the GP model, following
 * Tetrad's score convention.
 * </p>
 *
 * <h2>Key properties</h2>
 *
 * <ul>
 *   <li>
 *     <b>Exact kernel likelihood:</b>
 *     This score evaluates the full GP marginal likelihood without
 *     low-rank or feature approximations.
 *   </li>
 *   <li>
 *     <b>Stable under greedy search:</b>
 *     Unlike operator-based or regression-style kernel scores, the
 *     marginal likelihood changes smoothly under single-parent additions
 *     and deletions, making it well-suited for FGES/BOSS-style searches.
 *   </li>
 *   <li>
 *     <b>Handles high-dimensional parent sets:</b>
 *     The score remains well-defined even when the number of parents
 *     exceeds the sample size.
 *   </li>
 *   <li>
 *     <b>Principled probabilistic interpretation:</b>
 *     Parent sets are compared via an integrated likelihood that
 *     marginalizes over the latent function {@code f}.
 *   </li>
 * </ul>
 *
 * <h2>Computational considerations</h2>
 *
 * <p>
 * Computing the KML score requires forming and factorizing an
 * {@code n × n} kernel covariance matrix, which has
 * {@code O(n³)} time and {@code O(n²)} memory complexity.
 * As a result, this score is best suited for small to moderate
 * sample sizes.
 * </p>
 *
 * <p>
 * For large-sample settings, the {@code KFF-ML} (Random Fourier Feature /
 * Orthogonal Random Feature Marginal Likelihood) score provides a scalable
 * low-rank approximation that preserves the same probabilistic structure.
 * </p>
 *
 * <h2>Regularization and numerical stability</h2>
 *
 * <ul>
 *   <li>
 *     A noise variance {@code σ²} (exposed as {@code lambda}) is added
 *     to the kernel covariance to ensure positive definiteness.
 *   </li>
 *   <li>
 *     Cholesky factorization is used with adaptive jitter escalation
 *     to improve numerical robustness.
 *   </li>
 *   <li>
 *     Columns are globally standardized prior to kernel evaluation
 *     to stabilize bandwidth selection.
 *   </li>
 * </ul>
 *
 * <h2>Intended use</h2>
 *
 * <p>
 * This class is intended for benchmarking, validation, and small-sample
 * causal discovery where exact kernel inference is computationally feasible.
 * It serves as a reference implementation against which approximate
 * kernel scores can be compared.
 * </p>
 *
 * @see FfMl
 * @see edu.cmu.tetrad.search.score .Score
 */
public final class KernelMarginalLikelihoodScore implements Score, EffectiveSampleSizeSettable {

    // -------------------- configuration knobs --------------------

    /**
     * Base ridge/noise knob. Used to form sigma^2. Must be > 0.
     */
    private volatile double lambda = 1e-3;

    /**
     * Jitter escalation base for Cholesky stabilization. Must be > 0.
     */
    private volatile double jitter = 1e-10;

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

    /**
     * If true, use valid row subsets when missing exists.
     */
    private final boolean calculateRowSubsets;

    /**
     * Represents the dataset used within the kernel-based marginal likelihood scoring process.
     * The dataset contains the data upon which calculations and evaluations are performed.
     * It serves as the primary source of input data for various scoring and statistical methods
     * in the KernelMarginalLikelihoodScore class.
     */
    private final DataSet dataSet;

    /**
     * Represents the list of variables associated with the dataset.
     * This list contains the nodes representing the variables in the dataset.
     */
    private final List<Node> variables;

    /**
     * Number of rows in the dataset.
     */
    private final int sampleSize;

    /**
     * Effective sample size, or -1 if not set.
     */
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

    /**
     * Constructs an instance of KernelMarginalLikelihoodScore using the provided data set.
     * This constructor initializes internal variables, processes the input data,
     * and applies z-scoring to standardize the columns while preserving NaN values.
     *
     * @param dataSet The data set to be used for computing the Kernel Marginal Likelihood Score.
     *                Must not be null. Throws a {@code NullPointerException} if the data set is null.
     */
    public KernelMarginalLikelihoodScore(DataSet dataSet) {
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

    /**
     * Computes the difference in local scores when adding a variable to the conditioning set.
     * <p>
     * The method calculates the local score difference by determining the score when the variable `x`
     * is added to the conditioning set `z`, compared to the score with the original conditioning set `z`.
     *
     * @param x The variable to be added to the conditioning set.
     * @param y The target variable for which the score is being computed.
     * @param z The original conditioning set of variables.
     * @return The difference in local scores between the modified and original conditioning sets.
     */
    @Override
    public double localScoreDiff(int x, int y, int[] z) {
        return localScore(y, append(z, x)) - localScore(y, z);
    }

    @Override
    public double localScore(int i, int... parents) {
        Arrays.sort(parents);
        long key = cacheKey(i, parents);

        final ConcurrentHashMap<Long, Double> cache = localScoreCacheRef.get();

        // computeIfAbsent is atomic for the map (no duplicate computes per key)
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

                DMatrixRMaj C = (parents.length == 0)
                        ? new DMatrixRMaj(n, n)
                        : rbfGramND(parents, rows, n);

                addDiagonalInPlace(C, sigma2);

                return gpLogMarginalLikelihood(y, C, jitter);
            } catch (RuntimeException e) {
                TetradLogger.getInstance().log(e.getMessage());
                return Double.NaN;
            }
        });
    }

    /**
     * Retrieves the list of variables associated with this score object.
     *
     * @return A list of Node objects representing the variables.
     */
    @Override
    public List<Node> getVariables() {
        return new ArrayList<>(variables);
    }

    /**
     * Retrieves the sample size used for scoring.
     *
     * @return The sample size as an integer.
     */
    @Override
    public int getSampleSize() {
        return dataSet.getNumRows();
    }

    /**
     * Retrieves the maximum degree allowed for scoring.
     *
     * @return The maximum degree as an integer.
     */
    @Override
    public int getMaxDegree() {
        return (int) Math.ceil(Math.log(Math.max(5, nEff)));
    }

    /**
     * Determines if a node is determined by a set of nodes.
     *
     * @param z The set of nodes.
     * @param y The node.
     * @return True if the node is determined by the set of nodes, false otherwise.
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
     * Determines if an edge is an effect edge based on a given bump value.
     *
     * @param bump The bump value.
     * @return True if the edge is an effect edge, false otherwise.
     */
    @Override
    public boolean isEffectEdge(double bump) {
        return bump > 0;
    }

    /**
     * Retrieves the data model used for scoring.
     *
     * @return The data model.
     */
    public DataModel getDataModel() {
        return dataSet;
    }

    /**
     * Retrieves the effective sample size used for scoring.
     *
     * @return The effective sample size as an integer.
     */
    @Override
    public int getEffectiveSampleSize() {
        return nEff;
    }

    /**
     * Sets the effective sample size used for scoring.
     *
     * @param nEff the effective sample size
     */
    @Override
    public void setEffectiveSampleSize(int nEff) {
        this.nEff = (nEff < 0) ? this.sampleSize : nEff;
        resetCache();
    }

    /**
     * Retrieves the name of the score.
     *
     * @return The name of the score as a String.
     */
    @Override
    public String toString() {
        return "Huang Kernel Marginal Score (GP form, continuous)";
    }

    /**
     * Sets the lambda hyperparameter for the Kernel Marginal Likelihood Score.
     * The lambda parameter is expected to be a positive value.
     * Throws an {@link IllegalArgumentException} if the given value is less than or equal to 0.
     * After setting the lambda value, the local score cache is reset.
     *
     * @param lambda The new value for the lambda hyperparameter. Must be greater than 0.
     */
    public void setLambda(double lambda) {
        if (lambda <= 0) throw new IllegalArgumentException("lambda must be > 0");
        this.lambda = lambda;
        resetCache();
    }

    /**
     * Sets the jitter parameter for the Kernel Marginal Likelihood Score.
     * The jitter parameter is expected to be a positive value.
     * Throws an {@link IllegalArgumentException} if the given value is less than or equal to 0.
     * After setting the jitter value, the local score cache is reset.
     *
     * @param jitter The new value for the jitter parameter. Must be greater than 0.
     */
    public void setJitter(double jitter) {
        if (jitter <= 0) throw new IllegalArgumentException("jitter must be > 0");
        this.jitter = jitter;
        resetCache();
    }

    /**
     * Sets the bandwidth multiplier parameter for the Kernel Marginal Likelihood Score.
     * The bandwidth multiplier is expected to be a positive finite value.
     * Throws an {@link IllegalArgumentException} if the provided value
     * is less than or equal to 0, or is not finite.
     * After setting the bandwidth multiplier, the local score cache is reset.
     *
     * @param bandwidthMultiplier The new value for the bandwidth multiplier.
     *                            Must be greater than 0 and finite.
     */
    public void setBandwidthMultiplier(double bandwidthMultiplier) {
        if (!(bandwidthMultiplier > 0) || !Double.isFinite(bandwidthMultiplier)) {
            throw new IllegalArgumentException("bandwidthMultiplier must be > 0");
        }
        this.bandwidthMultiplier = bandwidthMultiplier;
        resetCache();
    }

    /**
     * Sets the maximum number of rows to be used in bandwidth calculations.
     * The specified value is constrained to a minimum of 50.
     * After setting the value, the local score cache is reset.
     *
     * @param bwMaxRows The new maximum number of rows for bandwidth calculations.
     *                  If the provided value is less than 50, it will be automatically
     *                  adjusted to 50.
     */
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

    private void resetCache() {
        localScoreCacheRef.set(new ConcurrentHashMap<>());
    }
}