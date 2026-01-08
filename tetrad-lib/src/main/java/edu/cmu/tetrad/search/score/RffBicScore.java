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
 * <p>
 * <strong>Idea:</strong>
 * </p>
 *
 * <ul>
 *   <li>
 *     Map parent set <code>Z</code> to random Fourier features
 *     <code>&Phi;(Z)</code> for an RBF kernel (as in RCIT/RCoT).
 *   </li>
 *   <li>
 *     Fit ridge regression of target <code>X</code> on
 *     <code>&Phi;(Z)</code>.
 *   </li>
 *   <li>
 *     Use a Gaussian log-likelihood with
 *     <code>&sigma;2 = RSS / n</code>.
 *   </li>
 *   <li>
 *     Penalize with a BIC-like term using the effective degrees of freedom
 *     under ridge regression:
 *     <pre>
 * df_eff = tr( A (A + &lambda; I)^-1 ),
 * where A = &Phi;T &Phi;
 *     </pre>
 *   </li>
 * </ul>
 *
 * <p>
 * The score convention matches Tetrad: <strong>higher scores indicate better models</strong>.
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
 *     The bandwidth <code>&sigma;</code> can be chosen using one of the following strategies:
 *     <ul>
 *       <li>
 *         <code>PER_VARIABLE_MEDIAN</code>:
 *         precompute <code>&sigma;<sub>j</sub></code> per variable and set the
 *         parent-set bandwidth to the median of these values.
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
 *     explicit matrix inverses for numerical stability.
 *   </li>
 * </ul>
 */
public final class RffBicScore implements Score, EffectiveSampleSizeSettable {

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
    /**
     * #RFF features for parent set Z (dimension of Phi(Z)).
     */
    private int numFeatZ = 200;
    /**
     * Ridge parameter added to Phi^T Phi (must be > 0).
     */
    private double lambda = 1e-4;

    // -------------------- data --------------------
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
     * # independent RFF draws to average (variance reduction).
     */
    private int rffEnsemble = 3;
    /**
     * If true, use cos+sin paired features (lower variance than cos-only).
     */
    private boolean useCosSinPairs = true;
    /**
     * Effective sample size.
     */
    private int nEff;

    // -------------------- construction --------------------

    /**
     * Constructs an instance of the RffBicScore class.
     * <p>
     * This method initializes the RffBicScore using the given dataset. It extracts the variables and sample size from
     * the dataset, creates an index map for the variables, determines if there are missing values in the dataset, and
     * precomputes relevant properties such as column values and sigma values for each variable.
     *
     * @param dataSet The dataset used to calculate the BIC score. This dataset must not be null, and it provides the
     *                variables, sample size, and data values necessary for computation.
     * @throws NullPointerException if the provided {@code dataSet} is null.
     */
    public RffBicScore(DataSet dataSet) {
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
     * Lower-variance RFF features for RBF kernel.
     * <p>
     * If useCosSinPairs==true: Use m' = floor(m/2) frequencies and output [cos(w^T z + b), sin(w^T z + b)] pairs. This
     * typically reduces variance a lot vs cos-only.
     * <p>
     * Scaling keeps E[Phi Phi^T] close to the RBF kernel.
     */
    private static DMatrixRMaj rffFeaturesStable(double[][] Z, int n, int d, int m, double sigma,
                                                 Random rng, boolean useCosSinPairs) {
        if (sigma <= 0 || !Double.isFinite(sigma)) sigma = 1.0;

        // For RBF kernel exp(-||x-y||^2/(2 sigma^2)), we sample w ~ N(0, I/sigma^2)
        final double wStd = 1.0 / sigma;

        if (!useCosSinPairs) {
            // your original cos-only construction
            double[][] W = new double[m][d];
            double[] b = new double[m];
            for (int i = 0; i < m; i++) {
                for (int j = 0; j < d; j++) W[i][j] = rng.nextGaussian() * wStd;
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

        // cos+sin paired features
        int mf = Math.max(1, m / 2);     // number of frequencies
        int outM = 2 * mf;               // output features (even)
        double[][] W = new double[mf][d];
        double[] b = new double[mf];

        for (int i = 0; i < mf; i++) {
            for (int j = 0; j < d; j++) W[i][j] = rng.nextGaussian() * wStd;
            b[i] = rng.nextDouble() * 2.0 * Math.PI;
        }

        DMatrixRMaj Phi = new DMatrixRMaj(n, outM);
        double scale = Math.sqrt(2.0 / outM);

        for (int i = 0; i < n; i++) {
            double[] zi = Z[i];
            int col = 0;
            for (int f = 0; f < mf; f++) {
                double dot = 0.0;
                double[] wf = W[f];
                for (int j = 0; j < d; j++) dot += wf[j] * zi[j];
                double t = dot + b[f];
                Phi.set(i, col++, scale * Math.cos(t));
                Phi.set(i, col++, scale * Math.sin(t));
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

    // -------------------- parameters (getters/setters) --------------------

    private static long cacheKey(int target, int[] parents) {
        long h = 1469598103934665603L;
        h = (h ^ target) * 1099511628211L;
        for (int p : parents) h = (h ^ p) * 1099511628211L;
        return h;
    }

    /**
     * Computes the difference in local scores when the variable `x` is added to the conditioning set `z` for variable
     * `y`.
     *
     * @param x the variable to add to the conditioning set.
     * @param y the target variable for which the local score is being evaluated.
     * @param z the array of variables representing the initial conditioning set.
     * @return the difference in local scores after adding `x` to the conditioning set `z`.
     */
    @Override
    public double localScoreDiff(int x, int y, int[] z) {
        return localScore(y, append(z, x)) - localScore(y, z);
    }

    /**
     * Computes the local score for a given target variable conditioned on its parent variables. The method utilizes
     * random Fourier features (RFF) for dimensionality reduction and applies scoring based on Gaussian log likelihood,
     * regularized by the effective degrees of freedom.
     *
     * @param target  The target variable whose local score is to be computed. It is represented as an index.
     * @param parents The parent variables of the target variable. These are represented as indices and may affect the
     *                target variable's behavior.
     * @return The computed local score as a double value. If no valid score can be determined due to insufficient data
     * or other numerical issues, the method returns {@code Double.NaN}.
     */
    @Override
    public double localScore(int target, int... parents) {
        Arrays.sort(parents);

        long key = cacheKey(target, parents);
        Double cached = localScoreCache.get(key);
        if (cached != null) return cached;

        int[] all = concat(target, parents);
        int[] rows = calculateRowSubsets ? validRows(all) : null;

        int n = (rows == null) ? nEff : rows.length;
        if (n < 10) {
            localScoreCache.put(key, Double.NaN);
            return Double.NaN;
        }

        // y (target)
        double[] y = extract1D(target, rows, n);

        int d = parents.length;

        // No parents: marginal Gaussian (no penalty)
        if (d == 0) {
            if (centerFeatures) zscoreInPlace(y);
            double ll = gaussianLogLikFromRss(y, n, rssZeroModel(y));
            double score = 2.0 * ll;
            localScoreCache.put(key, score);
            return score;
        }

        // Z (n x d)
        double[][] Z = extractND(parents, rows, n, d);

        // Standardize y and Z for stability
        if (centerFeatures) {
            zscoreInPlace(y);
            zscoreInPlace(Z);
        }

        // Bandwidth
        double sigma = chooseSigma(parents, Z, n);
        if (!(sigma > 0) || !Double.isFinite(sigma)) sigma = 1.0;

        // Optional RCIT-ish tweak: sigma = medianDist / sqrt(2)
        sigma = sigma / Math.sqrt(2.0);

        // Ensemble-average the score over a few deterministic RFF draws
        final int E = Math.max(1, this.rffEnsemble);

        double scoreSum = 0.0;
        int good = 0;

        for (int e = 0; e < E; e++) {
            // Build Phi(Z): n x m
            int m = numFeatZ;
            DMatrixRMaj Phi = rffFeaturesStable(
                    Z, n, d, m, sigma,
                    localRng(target, parents, e),
                    useCosSinPairs
            );

            if (centerFeatures) {
                zscoreColumnsInPlace(Phi);
            }

            final int mPhi = Phi.numCols;

            // A = Phi^T Phi, b = Phi^T y
            DMatrixRMaj A = new DMatrixRMaj(mPhi, mPhi);
            CommonOps_DDRM.multTransA(Phi, Phi, A);

            DMatrixRMaj b = new DMatrixRMaj(mPhi, 1);
            multTransA_vec(Phi, y, b);

            // Ab = A + lambda I
            DMatrixRMaj Ab = A.copy();
            addDiagonalInPlace(Ab, lambda);

            // Factorize Ab once; reuse for beta solve and df trace
            LinearSolverDense<DMatrixRMaj> solver = LinearSolverFactory_DDRM.symmPosDef(mPhi);

            if (!solver.setA(Ab)) {
                // escalate jitter (keep Ab intact; solve with Abj)
                double eps = jitter;
                boolean ok = false;
                for (int k = 0; k < 6; k++) {
                    DMatrixRMaj Abj = Ab.copy();
                    addDiagonalInPlace(Abj, eps);
                    if (solver.setA(Abj)) {
                        ok = true;
                        break;
                    }
                    eps *= 10.0;
                }
                if (!ok) continue;
            }

            // beta = inv(Ab) * b
            DMatrixRMaj beta = new DMatrixRMaj(mPhi, 1);
            solver.solve(b, beta);

            // RSS via quadratic form: y^T y - 2 beta^T b + beta^T A beta
            double yTy = dot(y);
            double betaTb = dot(beta, b);
            double betaTAb = quadForm(A, beta);
            double rss = yTy - 2.0 * betaTb + betaTAb;

            if (!Double.isFinite(rss)) continue;
            if (rss <= 0) rss = 1e-12; // clamp to avoid log(0) / negative variance

            double ll = gaussianLogLikFromRss(y, n, rss);

            // df = tr( A (A + lambda I)^(-1) ) computed from Ab factorization:
            double df = effectiveDfFromChol(solver, mPhi, lambda);
            if (!Double.isFinite(df) || df < 0) df = mPhi;

            double score = 2.0 * ll - penaltyDiscount * df * Math.log(n);
            if (Double.isFinite(score)) {
                scoreSum += score;
                good++;
            }
        }

        double out = (good > 0) ? (scoreSum / good) : Double.NaN;
        localScoreCache.put(key, out);
        return out;
    }

    /**
     * Retrieves the list of variable nodes.
     *
     * @return a list of Node objects representing the variables. The returned list is a copy of the internal variables,
     * ensuring that modifications to the returned list do not affect the internal state.
     */
    @Override
    public List<Node> getVariables() {
        return new ArrayList<>(variables);
    }

    /**
     * Retrieves the sample size used in the score calculation.
     *
     * @return the sample size
     */
    @Override
    public int getSampleSize() {
        return dataSet.getNumRows();
    }

    /**
     * Retrieves the maximum degree for the score calculation.
     *
     * @return the maximum degree
     */
    @Override
    public int getMaxDegree() {
        return (int) Math.ceil(Math.log(Math.max(3, nEff)));
    }

    /**
     * Determines if an edge has an effect based on the given bump value.
     *
     * @param z     The set of nodes.
     * @param yNode The node.
     * @return true if the edge has an effect, false otherwise
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
     * Determines if an edge has an effect based on the given bump value.
     *
     * @param bump the bump value
     * @return true if the edge has an effect, false otherwise
     */
    @Override
    public boolean isEffectEdge(double bump) {
        return bump > 0;
    }

    /**
     * Retrieves the data model used for the score calculation.
     *
     * @return the data model
     */
    public DataModel getDataModel() {
        return dataSet;
    }

    /**
     * Returns the effective sample size used in the score calculation.
     *
     * @return the effective sample size
     */
    @Override
    public int getEffectiveSampleSize() {
        return nEff;
    }

    /**
     * Sets the effective sample size for the score calculation.
     *
     * @param nEff the effective sample size
     */
    @Override
    public void setEffectiveSampleSize(int nEff) {
        this.nEff = (nEff < 0) ? this.sampleSize : nEff;
        clearCache();
    }

    /**
     * Returns a string representation of the score.
     *
     * @return a string representation of the score
     */
    @Override
    public String toString() {
        return "RCIT-RFF Ridge BIC Score (continuous)";
    }

    /**
     * Retrieves the number of features for Z.
     *
     * @return the number of features for Z
     */
    public int getNumFeatZ() {
        return numFeatZ;
    }

    /**
     * Sets the number of features for Z.
     *
     * @param numFeatZ the number of features for Z
     */
    public void setNumFeatZ(int numFeatZ) {
        this.numFeatZ = Math.max(1, numFeatZ);
        clearCache();
    }

    /**
     * Retrieves the value of the lambda variable.
     *
     * @return the current value of lambda.
     */
    public double getLambda() {
        return lambda;
    }

    /**
     * Sets the value of the lambda variable.
     *
     * @param lambda the lambda value to be set
     */
    public void setLambda(double lambda) {
        if (lambda <= 0) throw new IllegalArgumentException("lambda must be > 0");
        this.lambda = lambda;
        clearCache();
    }

    /**
     * Retrieves the penalty discount factor.
     *
     * @return the penalty discount factor as a double.
     */
    public double getPenaltyDiscount() {
        return penaltyDiscount;
    }

    /**
     * Sets the penalty discount factor.
     *
     * @param penaltyDiscount the penalty discount factor to be set
     */
    public void setPenaltyDiscount(double penaltyDiscount) {
        if (penaltyDiscount <= 0) throw new IllegalArgumentException("penaltyDiscount must be > 0");
        this.penaltyDiscount = penaltyDiscount;
        clearCache();
    }

    /**
     * Determines whether the features are centered.
     *
     * @return true if the features are centered, false otherwise.
     */
    public boolean isCenterFeatures() {
        return centerFeatures;
    }

    /**
     * Sets whether the features should be centered.
     *
     * @param centerFeatures true to center features, false otherwise
     */
    public void setCenterFeatures(boolean centerFeatures) {
        this.centerFeatures = centerFeatures;
        clearCache();
    }

    // -------------------- enum --------------------

    /**
     * Retrieves the current value of the seed.
     *
     * @return the seed value as a long.
     */
    public long getSeed() {
        return seed;
    }

    // -------------------- internals --------------------

    /**
     * Sets the seed value for random number generation.
     *
     * @param seed the seed value to be set
     */
    public void setSeed(long seed) {
        this.seed = seed;
        clearCache();
    }

    /**
     * Retrieves the maximum number of bandwidth rows considered during computations.
     *
     * @return the maximum number of rows dedicated to bandwidth calculations
     */
    public int getMaxBandwidthRows() {
        return maxBandwidthRows;
    }

    /**
     * Sets the maximum number of bandwidth rows to be considered during computations. The provided value will be
     * constrained to a minimum of 10. This method also clears the local score cache to ensure updated computations.
     *
     * @param maxBandwidthRows the desired maximum number of bandwidth rows. If the provided value is less than 10, it
     *                         will automatically be set to 10.
     */
    public void setMaxBandwidthRows(int maxBandwidthRows) {
        this.maxBandwidthRows = Math.max(10, maxBandwidthRows);
        clearCache();
    }

    /**
     * Retrieves the current bandwidth mode used for computations. The bandwidth mode determines how sigma values are
     * calculated, such as using per-variable medians or parent set medians.
     *
     * @return the current {@code BandwidthMode}.
     */
    public BandwidthMode getBandwidthMode() {
        return bandwidthMode;
    }

    /**
     * Sets the bandwidth mode used for computations. The bandwidth mode determines how sigma values are calculated for
     * the model, such as utilizing per-variable medians or the median of parent sets. Setting this value will also
     * clear the local score cache to ensure that subsequent calculations are based on the updated bandwidth mode.
     *
     * @param bandwidthMode the {@code BandwidthMode} to set. Must not be null.
     */
    public void setBandwidthMode(BandwidthMode bandwidthMode) {
        this.bandwidthMode = Objects.requireNonNull(bandwidthMode, "bandwidthMode");
        clearCache();
    }

    /**
     * Retrieves the current jitter value used to perturb matrices for numerical stability in computations such as
     * solving positive definite systems.
     *
     * @return the jitter value, which is typically a small positive double to ensure stability.
     */
    public double getJitter() {
        return jitter;
    }

    /**
     * Sets the jitter value used to introduce a small positive perturbation for numerical stability in certain
     * computations, such as solving systems involving positive definite matrices. The jitter must be greater than 0.
     * This method clears the local score cache to ensure updated computations based on the new jitter value.
     *
     * @param jitter the jitter value; must be a positive double greater than 0
     * @throws IllegalArgumentException if the jitter is less than or equal to 0
     */
    public void setJitter(double jitter) {
        if (jitter <= 0) throw new IllegalArgumentException("jitter must be > 0");
        this.jitter = jitter;
        clearCache();
    }

    /**
     * Retrieves the current number of random Fourier feature ensembles (RFF ensembles) being utilized in computations.
     * An RFF ensemble typically represents the number of randomized basis functions used for approximating kernels in
     * machine learning models or statistical computations.
     *
     * @return the number of RFF ensembles currently set for use in the model.
     */
    public int getRffEnsemble() {
        return rffEnsemble;
    }

    /**
     * Sets the number of random Fourier feature ensembles (RFF ensembles) to be used in computations. An RFF ensemble
     * typically represents the number of randomized basis functions used for approximating kernels in machine learning
     * models or statistical computations.
     *
     * @param rffEnsemble the number of RFF ensembles to set for use in the model.
     */
    public void setRffEnsemble(int rffEnsemble) {
        this.rffEnsemble = Math.max(1, rffEnsemble);
        clearCache();
    }

    /**
     * Returns whether cosine and sine pairs are used for RFF ensembles.
     *
     * @return true if cosine and sine pairs are used, false otherwise
     */
    public boolean isUseCosSinPairs() {
        return useCosSinPairs;
    }

    /**
     * Enables or disables the use of cosine and sine pairs in random Fourier feature (RFF) computations. Utilizing
     * cosine and sine pairs can reduce variance in feature representation compared to using cosine-only. This setting
     * affects the generation of RFF features and directly impacts the model's computations. Setting this value also
     * clears the local score cache to ensure updated calculations based on the new configuration.
     *
     * @param useCosSinPairs a boolean flag indicating whether to use cosine and sine pairs. If true, cosine and sine
     *                       features are paired; otherwise, only cosine features are used.
     */
    public void setUseCosSinPairs(boolean useCosSinPairs) {
        this.useCosSinPairs = useCosSinPairs;
        clearCache();
    }

    private void clearCache() {
        localScoreCache.clear();
    }

//    private Random localRng(int target, int[] parents) {
//        long h = 1469598103934665603L;
//        h = (h ^ seed) * 1099511628211L;
//        h = (h ^ target) * 1099511628211L;
//        for (int p : parents) h = (h ^ p) * 1099511628211L;
//        return new Random(h);
//    }

    private Random localRng(int target, int[] parents, int rep) {
        long h = 1469598103934665603L;
        h = (h ^ seed) * 1099511628211L;
        h = (h ^ target) * 1099511628211L;
        for (int p : parents) h = (h ^ p) * 1099511628211L;
        h = (h ^ rep) * 1099511628211L;
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

    private double effectiveDfFromChol(LinearSolverDense<DMatrixRMaj> solver, int m, double lambda) {
        // df = m - lambda * tr(inv(Ab))
        // tr(inv(Ab)) = sum_i (inv(Ab))_{ii}; get it by solving Ab x = e_i and summing x_i
        DMatrixRMaj e = new DMatrixRMaj(m, 1);
        DMatrixRMaj x = new DMatrixRMaj(m, 1);

        double trInv = 0.0;
        for (int i = 0; i < m; i++) {
            Arrays.fill(e.data, 0.0);
            e.data[i] = 1.0;
            solver.solve(e, x);
            trInv += x.data[i];
        }
        return m - lambda * trInv;
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

    private static double dot(double[] y) {
        double s = 0.0;
        for (double v : y) s += v * v;
        return s;
    }

    private static double quadForm(DMatrixRMaj A, DMatrixRMaj x) {
        // returns x^T A x
        int m = A.numCols;
        double sum = 0.0;
        for (int i = 0; i < m; i++) {
            double xi = x.data[i];
            double rowSum = 0.0;
            int base = i * m;
            for (int j = 0; j < m; j++) rowSum += A.data[base + j] * x.data[j];
            sum += xi * rowSum;
        }
        return sum;
    }

    private static double dot(DMatrixRMaj a, DMatrixRMaj b) {
        // both m x 1
        double s = 0.0;
        for (int i = 0; i < a.numRows; i++) s += a.data[i] * b.data[i];
        return s;
    }

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
     * Appends a new element to the end of a given array and returns the resulting array. A new array is created to
     * accommodate the additional element, with the original array's elements copied into it, followed by the appended
     * element.
     *
     * @param z the original array to which the new element will be appended
     * @param x the element to append to the array
     * @return a new array containing all elements of the original array followed by the appended element
     */
    public int[] append(int[] z, int x) {
        int[] out = Arrays.copyOf(z, z.length + 1);
        out[z.length] = x;
        return out;
    }

    /**
     * Defines the bandwidth selection modes for sigma calculation in the model. The selected mode determines how
     * bandwidth parameters are computed during statistical or machine learning processes.
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