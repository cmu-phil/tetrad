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
 * KFF-ML Score (Kernel Fourier Features Maximum-Likelihood score).
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
public final class KffMarginalLikelihoodScore implements Score, EffectiveSampleSizeSettable {

    /**
     * Represents the types of features that can be used in random feature mappings.
     * This enumeration is utilized to distinguish between different methods for
     * generating random features in machine learning or statistical models.
     * <p>
     * Available feature types:
     * <p>
     * - RFF (Random Fourier Features): A method commonly used to approximate kernel functions
     * in machine learning via random projections.
     * <p>
     * - ORF (Orthogonal Random Features): A variant of Random Fourier Features that ensures orthogonality
     * in the generated random projections for better numerical stability and performance.
     */
    public enum FeatureType {RFF, ORF}

    // -------------------- configuration knobs --------------------

    /**
     * Base ridge/noise knob. Used to form sigma^2. Must be > 0.
     */
    private volatile double lambda = 1.0;

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

    /**
     * Represents the number of random features used for certain kernel approximations
     * like Random Fourier Features (RFF) or Orthogonal Random Features (ORF).
     * The value of this variable may impact the accuracy and computational
     * efficiency of approximate methods for evaluating kernel-based models.
     *
     * This variable is volatile to ensure thread-safe read and write
     * operations in a concurrent environment.
     */
    private volatile int numFeatures = 256;

    /**
     * Represents the effective sample size, which is a measure of the
     * equivalent number of independent observations in a statistical model.
     * This variable is used to adjust computations to account for dependencies
     * or other factors that reduce the effective sample count.
     * <p>
     * The value of this variable may affect various calculations in the
     * {@code KffMarginalLikelihoodScore} class, including likelihood scores
     * and related model evaluations.
     * <p>
     * This field is declared as {@code volatile} to ensure thread-safe
     * read and write operations in a concurrent environment.
     */
    private volatile int nEff;

    /**
     * The feature type utilized in the current instance for random feature mapping.
     * This variable determines the specific method applied for generating random features
     * within the associated statistical models or machine learning algorithms.
     * <p>
     * The supported feature types are:
     * <ol>
     * <li>{@code FeatureType.RFF}: Random Fourier Features, a method for kernel approximation through random projections.
     * <li>{@code FeatureType.ORF}: Orthogonal Random Features, an extension of Random Fourier Features
     * /ol>
     * ensuring orthogonality in the generated projections, often enhancing stability and performance.
     * <p>
     * The default value is set to {@code FeatureType.ORF}, indicating the use of Orthogonal Random Features.
     */
    private FeatureType featureType = FeatureType.ORF;

    /**
     * The dataset containing the observations or measurements used for
     * computations in the KffMarginalLikelihoodScore class.
     * This dataset is central to various scoring and statistical
     * methods implemented in this class, serving as the underlying
     * data source for evaluating marginal likelihoods, determining
     * conditional dependencies, and other related operations.
     */
    private final DataSet dataSet;

    /**
     * A list of variables represented as {@code Node} objects. These variables
     * are used in the context of scoring and computations within the
     * {@code KffMarginalLikelihoodScore} class.
     * <p>
     * The {@code variables} list typically holds the nodes or features that form
     * the underlying structure of the dataset being analyzed. It is a fundamental
     * component utilized for various methods and calculations within the class,
     * such as determining local scores, effective sample size, and evaluating
     * dependencies.
     */
    private final List<Node> variables;

    /**
     * Represents the total number of data points (or observations) used in the computations for this instance.
     * This value is fixed for the lifetime of the object and is initialized during object construction.
     * <p>
     * The sample size is an essential parameter in statistical modeling and influences the calculations
     * of marginal likelihood scores and other statistical measures within this class.
     */
    private final int sampleSize;

    /**
     * Standardized columns (z-scored globally, NaNs preserved). zCols[p][n].
     */
    private final double[][] zCols;

    /**
     * Cache: (target i, sorted parents) -> score.
     */
    private final AtomicReference<ConcurrentHashMap<Long, Double>> localScoreCacheRef =
            new AtomicReference<>(new ConcurrentHashMap<>());

    public KffMarginalLikelihoodScore(DataSet dataSet) {
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
     * Calculates the difference in local scores between two configurations of variables.
     * Specifically, this method computes the difference between the local score of variable {@code y}
     * conditioned on {@code z} and {@code x}, and the local score of {@code y} conditioned only on {@code z}.
     *
     * @param x the variable being added to the parent set of {@code y}.
     * @param y the target variable whose local score is being computed.
     * @param z an array of integers representing the current set of parent variables of {@code y}.
     * @return the difference in local score resulting from adding {@code x} to the parent set {@code z} of {@code y}.
     */
    @Override
    public double localScoreDiff(int x, int y, int[] z) {
        return localScore(y, append(z, x)) - localScore(y, z);
    }

    /**
     * Computes the local score for a given variable and its parent set in a Bayesian network.
     *
     * @param i The index of the target variable for which the local score is to be computed.
     * @param parents The indices of the parent variables of the target variable.
     * @return The computed local score as a double value. If the score cannot be computed due to invalid input
     *         or other issues, returns Double.NaN.
     */
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

                return gpLogMarginalLikelihoodRFF(
                        y, parents, rows, n, numFeatures, bw2, sigma2, seed
                );

            } catch (RuntimeException e) {
                TetradLogger.getInstance().log(e.getMessage());
                return Double.NaN;
            }
        });
    }

    /**
     * Exact sigma-only case: C = sigma2 I.
     */
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

    /**
     * Retrieves the list of variables in the current instance.
     *
     * @return a new list containing the variables of type {@code Node}.
     */
    @Override
    public List<Node> getVariables() {
        return new ArrayList<>(variables);
    }

    /**
     * Retrieves the sample size, which corresponds to the number of rows in the associated data set.
     *
     * @return the sample size as an integer.
     */
    @Override
    public int getSampleSize() {
        return dataSet.getNumRows();
    }

    /**
     * Computes the maximum degree based on a logarithmic function
     * that evaluates the current effective sample size.
     * The result is the ceiling of the logarithm of the larger value
     * between 5 and the effective sample size {@code nEff}.
     *
     * @return the maximum degree as an integer, calculated as the ceiling
     *         of the logarithm of the larger value between 5 and {@code nEff}.
     */
    @Override
    public int getMaxDegree() {
        return (int) Math.ceil(Math.log(Math.max(5, nEff)));
    }

    /**
     * Determines whether a given node is conditionally independent of a set of nodes
     * based on the local score calculation.
     *
     * @param z a list of nodes representing the conditional set.
     * @param y the node whose conditional independence is being evaluated.
     * @return true if the local score calculation results in NaN or infinity, indicating
     *         that the conditional independence cannot be determined reliably; false otherwise.
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
     * Determines if the given bump value represents an "effect edge."
     *
     * @param bump the bump value to evaluate
     * @return true if the bump value is greater than 0, otherwise false
     */
    @Override
    public boolean isEffectEdge(double bump) {
        return bump > 0;
    }

    /**
     * Retrieves the data model associated with the current instance.
     *
     * @return the data model object of type DataModel
     */
    public DataModel getDataModel() {
        return dataSet;
    }

    /**
     * Returns the effective sample size, which represents the number of observations
     * adjusted for correlations or weighting within the sample data.
     *
     * @return the effective sample size as an integer
     */
    @Override
    public int getEffectiveSampleSize() {
        return nEff;
    }

    /**
     * Sets the effective sample size to be used in calculations.
     * If the provided sample size is negative, it will default to the current sample size.
     *
     * @param nEff the effective sample size to set; defaults to the current sample size if negative
     */
    @Override
    public void setEffectiveSampleSize(int nEff) {
        this.nEff = (nEff < 0) ? this.sampleSize : nEff;
        resetCache();
    }

    /**
     * Returns a string representation of this object.
     * The string provides a descriptive label for this kernel,
     * indicating its type and mathematical form.
     *
     * @return a string describing this kernel, including its name and characteristics
     */
    @Override
    public String toString() {
        return "Huang Kernel Marginal Score (GP form, continuous)";
    }

    // -------------------- public tuning knobs --------------------

    /**
     * Sets the value of the lambda parameter in the current instance. Lambda is
     * a positive scalar value that influences specific scoring computations.
     * If the provided value is not greater than zero, an
     * IllegalArgumentException is thrown.
     *
     * @param lambda the new value to be set for the lambda parameter. Must be
     *               greater than zero.
     * @throws IllegalArgumentException if the input lambda is less than or
     *                                   equal to zero.
     */
    public void setLambda(double lambda) {
        if (lambda <= 0) throw new IllegalArgumentException("lambda must be > 0");
        this.lambda = lambda;
        resetCache();
    }

    /**
     * Sets the value of the bandwidth multiplier parameter in the current instance.
     * The bandwidth multiplier is a positive scalar that scales the bandwidth
     * parameter in the kernel function. The value must be greater than zero.
     *
     * @param bandwidthMultiplier the new value to be set for the bandwidth multiplier parameter.
     */
    public void setBandwidthMultiplier(double bandwidthMultiplier) {
        if (!(bandwidthMultiplier > 0) || !Double.isFinite(bandwidthMultiplier)) {
            throw new IllegalArgumentException("bandwidthMultiplier must be > 0");
        }
        this.bandwidthMultiplier = bandwidthMultiplier;
        resetCache();
    }

    /**
     * Sets the maximum number of rows to consider for bandwidth estimation in the current instance.
     * This parameter limits the number of rows used for bandwidth estimation to prevent excessive computation.
     * Default 50.
     *
     * @param bwMaxRows the maximum number of rows to set. Must be a positive integer.
     */
    public void setBwMaxRows(int bwMaxRows) {
        this.bwMaxRows = Math.max(50, bwMaxRows);
        resetCache();
    }

    /**
     * Sets the number of features for computations in the current instance.
     * This parameter defines the dimensionality of the random feature mappings.
     * Default 256.
     *
     * @param numFeatures the number of features to set. Must be a positive integer.
     */
    public void setNumFeatures(int numFeatures) {
        this.numFeatures = numFeatures;
    }

    // -------------------- GP marginal likelihood core --------------------

    /**
     * Sets the feature type for the current instance based on the provided integer value.
     * The feature type determines which type of random feature mapping is utilized.
     * Valid feature types are defined in the {@code FeatureType} enumeration.
     *
     * @param featureType an integer representing the desired feature type.
     *                    Acceptable values are:
     *                    <ul>
     *                       <li>1: Sets the feature type to {@code FeatureType.RFF} (Random Fourier Features).</li>
     *                       <li>2: Sets the feature type to {@code FeatureType.ORF} (Orthogonal Random Features).</li>
     *                    </ul>
     * @throws IllegalArgumentException if the input {@code featureType} is not 1 or 2.
     */
    public void setFeatureType(FeatureType featureType) {
        if (featureType == null) {
            throw new IllegalArgumentException("featureType cannot be null");
        }
        this.featureType = featureType;
    }

    /**
     * Retrieves the feature type for the current instance.
     * The feature type determines the type of random feature mapping being utilized.
     *
     * @return an integer representing the feature type:
     * <ul>
     *    <li>1: if the feature type to {@code FeatureType.RFF} (Random Fourier Features).</li>
     *    <li>2: if the feature type to {@code FeatureType.ORF} (Orthogonal Random Features).</li>
     * </ul>
     */
    public FeatureType getFeatureType() {
        return featureType;
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
        double[][] W;
        double[] b;

        if (featureType == FeatureType.RFF) {

            W = new double[mFeatures][d];
            b = new double[mFeatures];

            for (int j = 0; j < mFeatures; j++) {
                for (int k = 0; k < d; k++) {
                    W[j][k] = wStd * nextGaussian(rng);
                }
                b[j] = 2.0 * Math.PI * rng.nextDouble();
            }
        } else if (featureType == FeatureType.ORF) {
            W = sampleOrthogonalW(mFeatures, d, wStd, rng);

            b = new double[mFeatures];
            for (int j = 0; j < mFeatures; j++) {
                b[j] = 2.0 * Math.PI * rng.nextDouble();
            }
        } else {
            throw new IllegalArgumentException("featureType must be RFF or ORF");
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

    /**
     * Orthogonal Random Features (ORF) weights for RBF kernel.
     * <p>
     * Produces W with (block-)orthogonal rows:
     * W_row = (wStd * r) * q_row
     * where q_row are orthonormal directions and r ~ chi(d).
     * <p>
     * If mFeatures > d, rows are generated in blocks of size d; orthogonality holds within each block.
     */
    private static double[][] sampleOrthogonalW(int mFeatures, int d, double wStd, SplittableRandom rng) {
        double[][] W = new double[mFeatures][d];
        if (d <= 0) return W;

        int filled = 0;

        // Generate in blocks of size d (or remaining rows).
        while (filled < mFeatures) {
            int block = Math.min(d, mFeatures - filled);

            // Step 1: Gaussian block G (block x d)
            double[][] Q = new double[block][d];
            for (int i = 0; i < block; i++) {
                for (int j = 0; j < d; j++) {
                    Q[i][j] = nextGaussian(rng);
                }
            }

            // Step 2: Orthonormalize rows of Q (Gram-Schmidt on rows)
            for (int i = 0; i < block; i++) {
                // subtract projections on previous rows
                for (int k = 0; k < i; k++) {
                    double dot = 0.0;
                    for (int j = 0; j < d; j++) dot += Q[i][j] * Q[k][j];
                    for (int j = 0; j < d; j++) Q[i][j] -= dot * Q[k][j];
                }
                // normalize
                double norm2 = 0.0;
                for (int j = 0; j < d; j++) norm2 += Q[i][j] * Q[i][j];
                double norm = Math.sqrt(Math.max(1e-18, norm2));
                for (int j = 0; j < d; j++) Q[i][j] /= norm;
            }

            // Step 3: scale each row by chi(d) radius (approximate Gaussian row norm)
            for (int i = 0; i < block; i++) {
                double r = chiRadius(d, rng);     // ~ ||N(0,I_d)||

                double s = wStd * r;
                int outRow = filled + i;
                for (int j = 0; j < d; j++) {
                    W[outRow][j] = s * Q[i][j];
                }
            }

            filled += block;
        }

        return W;
    }

    /**
     * Radius r ~ chi(d) via sqrt(sum_k g_k^2), g_k ~ N(0,1).
     */
    private static double chiRadius(int d, SplittableRandom rng) {
        double ss = 0.0;
        for (int k = 0; k < d; k++) {
            double g = nextGaussian(rng);
            ss += g * g;
        }
        return Math.sqrt(Math.max(1e-18, ss));
    }

    // Box–Muller-ish gaussian from SplittableRandom (fast enough)
    private static double nextGaussian(SplittableRandom rng) {
        // Use Marsaglia polar
        double u, v, s;
        do {
            u = 2.0 * rng.nextDouble() - 1.0;
            v = 2.0 * rng.nextDouble() - 1.0;
            s = u * u + v * v;
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