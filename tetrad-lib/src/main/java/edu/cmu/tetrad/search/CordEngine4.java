package edu.cmu.tetrad.search;
/*
 * CordEngine4.java -- CORD (Orthogonal Rank-Score Conditional Independence Test)
 * for H0: Y _||_ Z | X, revision 4.
 *
 * This engine keeps the CORD estimand and the Neyman-orthogonal (doubly
 * residualized) score of CordEngine3, but upgrades the cross-fitting design,
 * the threshold combination, and the null calibration. Deltas vs. engine 3:
 *
 *  (1) M-FOLD ROTATION (default M = 5, engine 3 is the special case M = 3).
 *      Each rotation r assigns score = fold[r], dir = fold[r+1], and TRAIN =
 *      the remaining M-2 folds pooled. Over r = 0..M-1 every fold is "score"
 *      exactly once, so the score folds partition [n] and each point receives
 *      exactly one honest cross-fitted score per partition -- same pooling
 *      logic as engine 3, but the conditional-CDF nuisances now train on
 *      (M-2)/M of the data instead of 1/3, shrinking the second-order
 *      nuisance-error remainder and the variance of the witness g - m_hat.
 *
 *  (2) REPEATED CROSS-FITTING (default S = 5 partitions). The per-point,
 *      per-threshold scores psi_{ik} are AVERAGED across partitions before
 *      studentization. Points are i.i.d., so the sample variance of the
 *      averaged scores remains a valid variance estimate; averaging removes
 *      the pure partition-randomness component of the p-value (the "seed
 *      lottery"), making results reproducible across seeds to first order.
 *
 *  (3) PER-THRESHOLD SCORES + TWO COMBINATIONS. Engine 3 collapsed the K
 *      thresholds by an unweighted mean inside psi. Engine 4 keeps the full
 *      n x K score matrix and forms
 *        T_mean : GLS-weighted mean, w = (Sigma_hat + ridge)^(-1) 1, which
 *                 dominates equal weighting whenever per-threshold variances
 *                 differ (they always do; tail thresholds are noisier), and
 *        T_max  : max over per-threshold studentized statistics, which wins
 *                 when dependence concentrates in part of the Y-distribution.
 *      The reported p-value is the min-p combination of the two, calibrated
 *      jointly (see 4), an omnibus test with near-best power against both
 *      diffuse and concentrated alternatives.
 *
 *  (4) MULTIPLIER (WILD) BOOTSTRAP CALIBRATION (default B = 999, Mammen
 *      two-point weights). psi is a product of residuals -- skewed and
 *      heavy-tailed -- so 1 - Phi(T) is anti-conservative in the upper tail
 *      at moderate n. The studentized multiplier bootstrap on the pooled
 *      scores repairs the tail (Type I) at negligible cost, and provides the
 *      joint null needed to calibrate min(p_mean, p_max) exactly
 *      (Westfall-Young single-loop min-p). Set numBootstrap = 0 to fall back
 *      to the one-sided-normal p on T_mean (engine-3 style calibration).
 *
 *  (5) CUMULATIVE BINARY CDF MODEL. The conditional CDFs P(Y <= t_k | .) are
 *      fit as K binary gradient-boosted logistic models of 1{Y <= t_k}
 *      (respecting the ordinal structure of the thresholds), followed by a
 *      per-row pool-adjacent-violators pass to enforce monotonicity in k,
 *      then clipping. This replaces engine 3's unordered multiclass softmax,
 *      which could not share strength across adjacent Y-bins at small fold
 *      sizes and tripped the stratification fallback when a bin was thin.
 *      Each binary subproblem uses ALL fold rows and needs only two classes,
 *      so the StratifyException pathway disappears (a per-threshold fallback
 *      to no-early-stopping remains for one-sided prevalence).
 *
 *  (6) ADAPTIVE VARIANCE FLOOR. Engine 3 clipped p(1-p) at a fixed 0.02,
 *      which flattens the witness exactly at the outer thresholds where tail
 *      dependence lives once K grows. Engine 4 floors per threshold at
 *      max(varFloorMin, varFloorFrac * q_k (1 - q_k)) with q_k = (k+0.5)/K,
 *      so the floor scales with the population Bernoulli variance at that
 *      quantile level.
 *
 *  (7) SAMPLE (n-1) STANDARD DEVIATIONS throughout (engine 3 used the
 *      population sd). Immaterial once (4) is on; mildly conservative when
 *      the normal fallback is used.
 *
 * The embedded histogram gradient-boosted-trees learner (255-bin histograms,
 * best-first growth to 31 leaves, learning_rate 0.1, max_iter 300,
 * min_samples_leaf 20, l2 0, 15% early-stopping validation) is unchanged
 * from engine 3 apart from the binary-logistic loss in the classifier.
 *
 * Compute note. Cost per repeat is roughly (M/3) x engine 3; total is about
 * S * (M/3) x engine 3 (defaults: ~8x). numRepeats, numFolds, and
 * numBootstrap are the knobs to turn down for batch runs; S = 1, M = 3,
 * B = 0 approximates engine 3's cost envelope while keeping (3), (5), (6).
 *
 * Validity note. All changes act on nuisance quality, weighting, or
 * calibration; the score remains Neyman-orthogonal and every nuisance is fit
 * on folds disjoint from the fold it is evaluated on ((p,q) never see the
 * dir or score fold; (m_hat, e) never see the score fold; e shares no data
 * with p/q). The multiplier bootstrap conditions on the cross-fitted scores,
 * which is the standard studentized-multiplier regime.
 *
 * Build:  javac CordEngine4.java
 */

import java.util.*;

/**
 * The CordEngine4 class is a data-driven machine learning engine designed for regression
 * and classification tasks. It employs advanced statistical methods, tree-based models,
 * and optimization techniques to analyze and learn patterns from data. This class provides
 * functionality for model training, testing, and evaluation while supporting flexible
 * configurations and advanced control options for fine-tuning the performance.
 *
 * Key Features:
 * - Boosted trees for modeling complex patterns.
 * - Early stopping and validation mechanisms for overfitting prevention.
 * - Binary stratified train/validation splits for balanced data handling.
 * - Generalized least squares (GLS) and adaptive thresholds for robust statistics.
 * - Extensive array utilities for efficient data manipulation and computation.
 *
 * This class integrates techniques for tree growth, custom loss functions,
 * statistical adjustments, and data preprocessing to address a wide range
 * of machine learning problems effectively.
 *
 * Fields:
 * - alpha: Regularization parameter used to control the complexity of the model.
 * - numThresholds: The number of thresholds used for partitioning the feature space.
 * - numEstimators: The number of base estimators used in boosted models.
 * - learningRate: Controls the contribution of each tree in the model.
 * - maxLeafNodes: The maximum number of leaf nodes allowed in any tree.
 * - seed: The random seed for reproducibility of operations.
 * - numFolds: The number of data folds used for cross-validation.
 * - numRepeats: The number of repetitions for cross-validation.
 * - numBootstrap: The number of bootstrap samples used for statistical tests.
 * - multiplier: A constant factor used in statistical adjustments.
 * - combine: A flag for combining multiple components.
 * - glsRidge: Value used for GLS ridge regression regularization.
 * - varFloorFrac: Minimum fraction for variance adjustment.
 * - varFloorMin: Minimum allowed variance value.
 * - minFoldSize: Minimum size of a fold in cross-validation.
 * - earlyStopping: Whether to enable early stopping based on validation performance.
 * - validationFraction: Fraction of data reserved for validation during training.
 * - cdfClip: Clipping value for cumulative density function adjustments.
 * - minSamplesLeaf: Minimum number of samples required in a leaf node.
 * - l2Regularization: Regularization term for controlling model complexity.
 * - maxBins: Maximum number of bins used for feature discretization.
 * - nIterNoChange: Number of iterations without improvement before stopping.
 * - tol: Tolerance value for early stopping and convergence checks.
 *
 * Methods:
 * - CordEngine4: Default constructor initializing the engine with default settings.
 * - predictTree: Predicts the output of a decision tree for a given row.
 * - growTree: Constructs a single decision tree based on training data and hyperparameters.
 * - makeNode: Creates an individual tree node with the specified parameters.
 * - shouldStop: Implements an early-stopping criterion based on validation scores.
 * - stratifiedBinarySplit: Performs binary stratified train/validation split.
 * - quantileType7: Computes quantiles using Hyndman-Fan type 7 interpolation.
 * - searchsortedLeft: Searches for the position in a sorted array where the value would fit.
 * - arraySplit: Splits an array into approximately equal-sized chunks.
 * - unionExcept: Computes the union of all folds except specified indices.
 * - pavNondecreasing: Applies least-squares non-decreasing projection to an array.
 * - cholSolve: Solves a linear system using Cholesky decomposition.
 * - mammenWeight: Generates Mammen two-point multiplier weights.
 * - normSf: Computes the survival function (1 - CDF) of the normal distribution.
 * - erfc: Evaluates the complementary error function.
 * - sigmoid: Computes the sigmoid activation function.
 * - permutation: Generates a random permutation of integers.
 * - mix: Combines and mixes seed values for randomness.
 * - countGE: Counts elements in an array greater than or equal to the threshold.
 * - countGEsorted: Counts elements in a sorted array greater than or equal to the threshold.
 * - iota: Generates a range of integers from 0 to a specified value.
 * - clamp: Restricts a value within a specified range.
 * - mean: Calculates the arithmetic mean of an array.
 * - sampleSd: Computes the sample standard deviation of an array.
 * - gather: Extracts specified elements from an array based on indices.
 * - rows: Selects specific rows from a 2D array.
 * - column: Extracts a single column from a 2D array.
 * - hstack: Horizontally stacks two 2D arrays.
 * - unique: Retrieves unique elements from a sorted array.
 * - shuffle: Shuffles the elements of a list using a random generator.
 * - toIntArray: Converts a list of integers to an array.
 * - main: Entry point for the application, triggers the workflow and testing framework.
 * - test: Conducts testing tasks using multiple input formats and configurations.
 * - run: Internal method to execute the core functionality of the engine.
 * - scoreRotation: Assigns roles and computes per-threshold scores for data rotation.
 * - witness: Calculates adaptive floor statistics using witnessed margins.
 * - calibrate: Performs model calibration using GLS mean and other statistical metrics.
 * - fitCdfCumulative: Fits a cumulative density function to the input data.
 *
 * Related Classes:
 * - TreeNode: Represents a single boosted tree node, either a leaf node or an internal node.
 * - BuildNode: Represents an intermediate node during tree construction.
 * - CordEngine: Interface implemented by CordEngine4, providing a base for Cord engines.
 */
public class CordEngine4 implements CordEngine {

    /**
     * Represents the significance level used for hypothesis testing or decision-making.
     * This variable determines the threshold below which the null hypothesis is rejected.
     * Common values are 0.05 or 0.01, depending on the desired level of statistical rigor.
     */
    public double alpha = 0.05;            // significance level for the decision
    /**
     * The number of thresholds used in the CordEngine4 algorithm.
     * This variable determines the granularity of thresholds over which
     * computations (e.g., score calculations and conditional quantile regressions) are performed.
     *
     * A larger value increases the resolution of the thresholds, which may improve
     * the precision of certain statistical computations but can also increase
     * computational overhead.
     */
    public int numThresholds = 9;          // K  (cordNumThresholds)
    /**
     * The number of estimators to be used in the ensemble learning algorithm.
     * Represents the maximum number of iterations for model training.
     * This value is set to 300 by default, providing a balance between predictive
     * accuracy and computational efficiency.
     */
    public int numEstimators = 300;        // max_iter (cordNumEstimators)
    /**
     * Represents the learning rate used in the CordEngine4 model training process.
     * The learning rate determines the step size at each iteration while moving
     * toward a minimum of the loss function. A smaller value allows for more
     * precise adjustments but may slow down convergence, while a larger value
     * speeds up training but may overshoot the optimal solution.
     */
    public double learningRate = 0.1;      // (cordLearningRate)
    /**
     * The maximum number of leaf nodes allowed during the construction or growth of trees
     * in the CordEngine4 framework.
     *
     * This parameter directly influences the complexity of the tree model, with a higher
     * value allowing more splits and potentially finer granularity in decision boundaries.
     * However, an excessively large number of leaf nodes may lead to overfitting the data.
     *
     * Default value is set to 31.
     */
    public int maxLeafNodes = 31;          // (cordMaxLeafNodes)
    /**
     * Represents the base seed used for various randomization purposes within the CordEngine4 class.
     * The seed is utilized as a foundational value for generating split sequences,
     * managing randomness-related nuisances, and handling bootstrap operations.
     */
    public long seed = 0L;                 // base seed; split + nuisance + bootstrap

    /**
     * Number of folds used for splitting the data in cross-validation or
     * other similar iterative processes. Each fold is used as a testing
     * set while the remaining are used as training sets, in a stratified
     * or uniform manner based on the dataset.
     *
     * Constraints:
     * - Must be greater than or equal to 3.
     * - Setting this to 3 reproduces the behavior of CordEngine3's roles.
     */
    public int numFolds = 5;               // M >= 3; M = 3 reproduces engine 3's roles
    /**
     * The number of repetitions or iterations for repeated cross-fitting, where results
     * are averaged over multiple partitions to improve stability and reduce variance.
     */
    public int numRepeats = 5;             // S repeated cross-fit partitions (averaged)
    /**
     * The number of bootstrap multiplier draws to perform when generating resampled datasets.
     * This parameter is used to configure how many resampling iterations occur during
     * a statistical procedure or model simulation process.
     *
     * A value of 0 indicates that no resampling is performed and the process falls back to
     * a normal, non-bootstrap-based computation.
     *
     * It is commonly used in scenarios where statistical robustness needs to be ensured
     * through multiple simulations or in bagging algorithms to enhance prediction performance.
     */
    public int numBootstrap = 300;         // B multiplier draws; 0 => normal fallback
    /**
     * Represents the type of multiplier to be used in statistical computations
     * or algorithms within the CordEngine4 class.
     * <p>
     * The value of this variable determines the type of multiplier:
     * <p>
     * - "mammen": Indicates the use of a Mammen two-point multiplier,
     * characterized by mean 0, variance 1, and a third moment of 1.
     * <p>
     * - "rademacher": Indicates the use of a Rademacher multiplier,
     * which takes values of either -1 or +1 with equal probability.
     * <p>
     * This variable is configurable and plays a critical role in parameter
     * adjustments or calibrations during algorithmic execution.
     */
    public String multiplier = "mammen";   // "mammen" | "rademacher"
    /**
     * Determines the method of combining statistical measures for computations.
     * Can take one of the following values:
     * <p>
     * "minp" - Represents the minimum p-value combination method.
     * <p>
     * "mean" - Represents the mean value combination method.
     * <p>
     * "max"  - Represents the maximum value combination method.
     */
    public String combine = "minp";        // "minp" | "mean" | "max"
    /**
     * Specifies the ridge regularization parameter used in the generalized least squares (GLS)
     * computation. This parameter is expressed as a fraction of the average diagonal value of
     * the Sigma_hat matrix. It is used to introduce stability into the computation by adding
     * a small positive value to the diagonal of the covariance matrix.
     */
    public double glsRidge = 0.05;         // ridge on Sigma_hat, as fraction of avg diag
    /**
     * Fractional multiplier used to compute the adaptive floor during the scoring process.
     * The adaptive floor is defined as: frac * q_k * (1 - q_k), where q_k represents
     * the quantile at the k-th threshold.
     * This variable helps prevent extreme values by imposing a lower bound
     * proportional to the specified fraction of the variance-like term.
     */
    public double varFloorFrac = 0.25;     // adaptive floor: frac * q_k (1 - q_k)
    /**
     * Represents the minimum floor value used in adaptive calculations within the CORD framework.
     * This serves as an absolute lower bound to ensure numerical stability and prevent excessively
     * small floor values that could lead to computational issues.
     */
    public double varFloorMin = 5e-3;      // absolute floor
    /**
     * Minimum allowable size for each fold when splitting data into
     * training and validation sets during cross-validation or other
     * partitioning processes.
     * <p>
     * This variable ensures that the number of samples in each fold
     * does not fall below a given threshold, thereby maintaining the
     * statistical validity of the folds. If the number of samples
     * per fold would be smaller than this value, the total number
     * of folds may be reduced.
     */
    public int minFoldSize = 30;           // shrink M if folds would be smaller

    // Hard-coded to match the reference HGB configuration.

    /**
     * Indicates whether early stopping is enabled during model training.
     * When set to true, training will halt when a specific early stopping
     * criterion, such as no improvement in validation performance over
     * a number of iterations, is met.
     */
    public boolean earlyStopping = true;
    /**
     * The fraction of the dataset to allocate to the validation split during training.
     * This value is used to create a hold-out set for evaluating performance and controlling
     * overfitting during model training. Must be a value between 0.0 (no validation split)
     * and 1.0 (entire dataset used for validation), exclusive.
     */
    public double validationFraction = 0.15;
    /**
     * Specifies the minimum cumulative distribution function (CDF) value
     * to be used when computations involving the CDF are clipped or thresholded.
     * This prevents extreme values in statistical calculations that might
     * arise due to numerical instability or division by very small probabilities.
     * Typical usage includes ensuring that no CDF value is less than this
     * predefined threshold during fitting or scoring operations.
     */
    public double cdfClip = 1e-3;
    /**
     * Specifies the minimum number of samples required to form a leaf node in a tree-based model.
     * <p>
     * This parameter acts as a regularization tool by controlling the granularity of the leaf nodes,
     * potentially preventing overfitting. A larger value will lead to fewer, larger leaves, while a smaller
     * value will allow the model to capture finer details in the data.
     */
    public int minSamplesLeaf = 20;
    /**
     * Specifies the L2 regularization parameter used for controlling the magnitude of model
     * coefficients in regularized regression techniques. L2 regularization, also known as
     * Ridge regularization, adds a penalty proportional to the square of the coefficients
     * to the loss function, thereby discouraging large weights and helping to prevent
     * overfitting.
     * <p>
     * A value of 0.0 indicates no L2 regularization, while larger values increase the degree
     * of regularization. The appropriate value depends on the model and dataset.
     */
    public double l2Regularization = 0.0;
    /**
     * The maximum number of discrete bins that the algorithm can use for
     * representing and processing data.
     * <p>
     * This variable is typically used in the context of data discretization
     * or histogram-based methods, where numeric values are grouped into
     * a finite number of intervals (bins). The value significantly influences
     * the resolution of the discretization process and can impact the algorithm's
     * accuracy and computational efficiency.
     * <p>
     * A larger value allows for a finer representation of the data,
     * potentially improving accuracy but increasing computational cost.
     * Conversely, a smaller value reduces computational complexity at the
     * expense of losing finer details in the data distribution.
     * <p>
     * Default value is set to 255.
     */
    public int maxBins = 255;
    /**
     * Specifies the number of consecutive iterations during which no improvement
     * is observed before an early stopping criterion is triggered.
     * This parameter is used to prevent unnecessary computation by halting
     * iterative processes that are unlikely to improve further.
     */
    public int nIterNoChange = 10;
    /**
     * A tolerance level used as a convergence criterion for iterative algorithms
     * or as a threshold for numerical comparisons. This value denotes the smallest
     * acceptable difference between two floating-point numbers to be considered
     * equal or for an optimization process to be deemed converged.
     */
    public double tol = 1e-7;

    /**
     * Default constructor for the CordEngine4 class.
     * Initializes an instance of CordEngine4 with default settings.
     */
    public CordEngine4() {
    }

    static double predictTree(TreeNode nd, int[] row) {
        while (!nd.leaf) nd = (row[nd.feature] <= nd.binThr) ? nd.left : nd.right;
        return nd.value;
    }

    // ===================================================================== //
    //  Public API : test(x, y, z)                                            //
    // ===================================================================== //

    static TreeNode growTree(int[][] binned, int[] nBins, double[] grad, double[] hess,
                             int[] rootSamples, int maxLeaves, int minLeaf, double l2, double lr) {
        int p = nBins.length;
        BuildNode root = makeNode(rootSamples, binned, nBins, grad, hess, p, minLeaf, l2, lr);
        PriorityQueue<BuildNode> pq = new PriorityQueue<>((a, b) -> Double.compare(b.gain, a.gain));
        if (root.gain > 0) pq.add(root);
        int leaves = 1;
        while (leaves < maxLeaves && !pq.isEmpty()) {
            BuildNode bn = pq.poll();
            if (!(bn.gain > 0)) continue;
            int f = bn.feature, tb = bn.binThr;
            int cntL = 0;
            for (int idx : bn.samples) if (binned[idx][f] <= tb) cntL++;
            int[] left = new int[cntL], right = new int[bn.samples.length - cntL];
            int li = 0, ri = 0;
            for (int idx : bn.samples) {
                if (binned[idx][f] <= tb) left[li++] = idx;
                else right[ri++] = idx;
            }
            TreeNode nd = bn.node;
            nd.leaf = false;
            nd.feature = f;
            nd.binThr = tb;
            BuildNode L = makeNode(left, binned, nBins, grad, hess, p, minLeaf, l2, lr);
            BuildNode R = makeNode(right, binned, nBins, grad, hess, p, minLeaf, l2, lr);
            nd.left = L.node;
            nd.right = R.node;
            leaves++;
            if (L.gain > 0) pq.add(L);
            if (R.gain > 0) pq.add(R);
        }
        return root.node;
    }

    static BuildNode makeNode(int[] samples, int[][] binned, int[] nBins, double[] grad, double[] hess,
                              int p, int minLeaf, double l2, double lr) {
        double sumG = 0, sumH = 0;
        for (int idx : samples) {
            sumG += grad[idx];
            sumH += hess[idx];
        }
        BuildNode bn = new BuildNode();
        bn.samples = samples;
        bn.sumG = sumG;
        bn.sumH = sumH;
        TreeNode nd = new TreeNode();
        nd.leaf = true;
        nd.value = lr * (-sumG / (sumH + l2 + 1e-12));
        bn.node = nd;

        int total = samples.length;
        double bestGain = 0;
        int bestF = -1, bestB = -1;
        for (int f = 0; f < p; f++) {
            int nb = nBins[f];
            double[] hg = new double[nb];
            double[] hh = new double[nb];
            int[] hc = new int[nb];
            for (int idx : samples) {
                int b = binned[idx][f];
                hg[b] += grad[idx];
                hh[b] += hess[idx];
                hc[b]++;
            }
            double accG = 0, accH = 0;
            int accC = 0;
            for (int b = 0; b < nb - 1; b++) {
                accG += hg[b];
                accH += hh[b];
                accC += hc[b];
                if (accC < minLeaf) continue;
                int rc = total - accC;
                if (rc < minLeaf) break;
                double GR = sumG - accG, HR = sumH - accH;
                double gain = 0.5 * (accG * accG / (accH + l2 + 1e-12)
                        + GR * GR / (HR + l2 + 1e-12)
                        - sumG * sumG / (sumH + l2 + 1e-12));
                if (gain > bestGain) {
                    bestGain = gain;
                    bestF = f;
                    bestB = b;
                }
            }
        }
        bn.gain = bestGain;
        bn.feature = bestF;
        bn.binThr = bestB;
        return bn;
    }

    // ---- early-stopping criterion (sklearn _should_stop) ------------------ //
    static boolean shouldStop(List<Double> scores, int nIterNoChange, double tol) {
        int ref = nIterNoChange + 1;
        if (scores.size() < ref) return false;
        double reference = scores.get(scores.size() - ref) + tol;   // scores: higher = better
        for (int j = scores.size() - ref + 1; j < scores.size(); j++)
            if (scores.get(j) > reference) return false;
        return true;
    }

    // ---- binary stratified train/val split -------------------------------- //
    static int[][] stratifiedBinarySplit(int[] lab, double valFrac, Random rng) {
        List<Integer> c0 = new ArrayList<>(), c1 = new ArrayList<>();
        for (int i = 0; i < lab.length; i++) (lab[i] == 0 ? c0 : c1).add(i);
        List<Integer> valL = new ArrayList<>(), trainL = new ArrayList<>();
        for (List<Integer> idx : Arrays.asList(c0, c1)) {
            shuffle(idx, rng);
            int nVal = Math.max(1, (int) Math.floor(valFrac * idx.size()));
            if (nVal >= idx.size()) nVal = idx.size() - 1;           // keep >=1 in train
            for (int i = 0; i < idx.size(); i++)
                (i < nVal ? valL : trainL).add(idx.get(i));
        }
        return new int[][]{toIntArray(trainL), toIntArray(valL)};
    }

    /**
     * numpy np.quantile default: linear / Hyndman-Fan type 7.  `sorted` ascending.
     */
    static double quantileType7(double[] sorted, double q) {
        int N = sorted.length;
        if (N == 1) return sorted[0];
        double pos = q * (N - 1);
        int lo = (int) Math.floor(pos);
        double frac = pos - lo;
        if (lo >= N - 1) return sorted[N - 1];
        return sorted[lo] + frac * (sorted[lo + 1] - sorted[lo]);
    }

    /**
     * np.searchsorted(arr, v, side="left"): count of arr[i] STRICTLY LESS than v.
     */
    static int searchsortedLeft(double[] arr, double v) {
        int lo = 0, hi = arr.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (arr[mid] < v) lo = mid + 1;
            else hi = mid;
        }
        return lo;
    }

    /**
     * np.array_split(perm, M): first (n % M) chunks get one extra element.
     */
    static int[][] arraySplit(int[] perm, int M) {
        int n = perm.length, q = n / M, r = n % M;
        int[][] out = new int[M][];
        int start = 0;
        for (int j = 0; j < M; j++) {
            int sz = q + (j < r ? 1 : 0);
            out[j] = Arrays.copyOfRange(perm, start, start + sz);
            start += sz;
        }
        return out;
    }

    /**
     * Union of all folds except indices a and b, in fold order.
     */
    static int[] unionExcept(int[][] folds, int a, int b) {
        int total = 0;
        for (int j = 0; j < folds.length; j++) if (j != a && j != b) total += folds[j].length;
        int[] out = new int[total];
        int pos = 0;
        for (int j = 0; j < folds.length; j++)
            if (j != a && j != b) {
                System.arraycopy(folds[j], 0, out, pos, folds[j].length);
                pos += folds[j].length;
            }
        return out;
    }

    // ===================================================================== //
    //  Histogram gradient-boosted trees (unchanged core; binary logistic     //
    //  classifier replaces the multiclass softmax)                           //
    // ===================================================================== //

    /**
     * Pool-adjacent-violators: in-place least-squares nondecreasing projection.
     */
    static void pavNondecreasing(double[] v) {
        int K = v.length;
        double[] vals = new double[K];
        int[] cnt = new int[K];
        int m = 0;
        for (double x0 : v) {
            vals[m] = x0;
            cnt[m] = 1;
            m++;
            while (m > 1 && vals[m - 2] > vals[m - 1]) {
                double merged = (vals[m - 2] * cnt[m - 2] + vals[m - 1] * cnt[m - 1])
                        / (cnt[m - 2] + cnt[m - 1]);
                cnt[m - 2] += cnt[m - 1];
                vals[m - 2] = merged;
                m--;
            }
        }
        int idx = 0;
        for (int b = 0; b < m; b++)
            for (int j = 0; j < cnt[b]; j++) v[idx++] = vals[b];
    }

    /**
     * Solve A x = b for SPD A via Cholesky; returns null if not SPD.
     */
    static double[] cholSolve(double[][] A, double[] b) {
        int K = A.length;
        double[][] L = new double[K][K];
        for (int i = 0; i < K; i++) {
            for (int j = 0; j <= i; j++) {
                double s = A[i][j];
                for (int t = 0; t < j; t++) s -= L[i][t] * L[j][t];
                if (i == j) {
                    if (!(s > 0)) return null;
                    L[i][i] = Math.sqrt(s);
                } else {
                    L[i][j] = s / L[j][j];
                }
            }
        }
        double[] yv = new double[K];
        for (int i = 0; i < K; i++) {
            double s = b[i];
            for (int t = 0; t < i; t++) s -= L[i][t] * yv[t];
            yv[i] = s / L[i][i];
        }
        double[] x = new double[K];
        for (int i = K - 1; i >= 0; i--) {
            double s = yv[i];
            for (int t = i + 1; t < K; t++) s -= L[t][i] * x[t];
            x[i] = s / L[i][i];
        }
        return x;
    }

    /**
     * Mammen two-point multiplier: mean 0, variance 1, third moment 1.
     */
    static double mammenWeight(Random r) {
        double sqrt5 = Math.sqrt(5.0);
        double pNeg = (sqrt5 + 1.0) / (2.0 * sqrt5);
        return r.nextDouble() < pNeg ? (1.0 - sqrt5) / 2.0 : (1.0 + sqrt5) / 2.0;
    }

    /**
     * One-sided upper p-value  1 - Phi(t) = 0.5*erfc(t/sqrt2).
     */
    static double normSf(double t) {
        return 0.5 * erfc(t / Math.sqrt(2.0));
    }

    static double erfc(double x) {
        double z = Math.abs(x);
        double s = 1.0 / (1.0 + 0.5 * z);
        double ans = s * Math.exp(-z * z - 1.26551223 + s * (1.00002368 + s * (0.37409196
                + s * (0.09678418 + s * (-0.18628806 + s * (0.27886807 + s * (-1.13520398
                + s * (1.48851587 + s * (-0.82215223 + s * 0.17087277)))))))));
        return x >= 0.0 ? ans : 2.0 - ans;
    }

    static double sigmoid(double x) {
        return 1.0 / (1.0 + Math.exp(-x));
    }

    static int[] permutation(int n, Random rng) {           // Fisher-Yates
        int[] a = iota(n);
        for (int i = n - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int tmp = a[i];
            a[i] = a[j];
            a[j] = tmp;
        }
        return a;
    }

    static long mix(long seed, long stream) {               // SplitMix64-style stream separation
        long z = seed + 0x9E3779B97F4A7C15L * (stream + 0x632BE59BD9B4E019L);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    static int countGE(double[] a, double t) {              // #{a_i >= t}
        int c = 0;
        for (double v : a) if (v >= t) c++;
        return c;
    }

    static int countGEsorted(double[] sortedAsc, double t) { // #{a_i >= t}, a sorted ascending
        int lo = 0, hi = sortedAsc.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (sortedAsc[mid] < t) lo = mid + 1;
            else hi = mid;
        }
        return sortedAsc.length - lo;
    }

    // ===================================================================== //
    //  Numeric helpers                                                       //
    // ===================================================================== //

    // ---- small array utilities ------------------------------------------- //
    static int[] iota(int n) {
        int[] a = new int[n];
        for (int i = 0; i < n; i++) a[i] = i;
        return a;
    }

    static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    static double mean(double[] a) {
        double s = 0;
        for (double v : a) s += v;
        return a.length == 0 ? 0 : s / a.length;
    }

    static double sampleSd(double[] a, double m) {
        if (a.length < 2) return 0.0;
        double s = 0;
        for (double v : a) {
            double d = v - m;
            s += d * d;
        }
        return Math.sqrt(s / (a.length - 1));
    }

    static double[] gather(double[] y, int[] idx) {
        double[] o = new double[idx.length];
        for (int i = 0; i < idx.length; i++) o[i] = y[idx[i]];
        return o;
    }

    static double[][] rows(double[][] m, int[] idx) {
        double[][] o = new double[idx.length][];
        for (int i = 0; i < idx.length; i++) o[i] = m[idx[i]];
        return o;
    }

    static double[] column(double[][] m, int k) {
        double[] o = new double[m.length];
        for (int i = 0; i < m.length; i++) o[i] = m[i][k];
        return o;
    }

    static double[][] hstack(double[][] a, double[][] b) {
        int n = a.length, pa = a[0].length, pb = b[0].length;
        double[][] o = new double[n][pa + pb];
        for (int i = 0; i < n; i++) {
            System.arraycopy(a[i], 0, o[i], 0, pa);
            System.arraycopy(b[i], 0, o[i], pa, pb);
        }
        return o;
    }

    static double[] unique(double[] sortedAsc) {
        if (sortedAsc.length == 0) return sortedAsc;
        double[] tmp = new double[sortedAsc.length];
        int m = 0;
        for (double v : sortedAsc) if (m == 0 || v != tmp[m - 1]) tmp[m++] = v;
        return Arrays.copyOf(tmp, m);
    }

    static void shuffle(List<Integer> list, Random rng) {
        for (int i = list.size() - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int t = list.get(i);
            list.set(i, list.get(j));
            list.set(j, t);
        }
    }

    static int[] toIntArray(List<Integer> l) {
        int[] a = new int[l.size()];
        for (int i = 0; i < a.length; i++) a[i] = l.get(i);
        return a;
    }

    // ===================================================================== //
    //  main : disabled in the Tetrad build (wire through IndTestCordEric4).   //
    // ===================================================================== //


    /**
     * The main method serves as the entry point for the application.
     * This method is executed when the program starts and is primarily used for
     * triggering the application workflow or performing tests.
     *
     * @param args an array of command-line arguments passed to the program. This
     *             can be used to provide input data or configure the system behavior.
     */
    public static void main(String[] args) {
        // Self-test / data-file harness omitted in the Tetrad copy.
    }

    /**
     * Performs a test based on the provided input data. This method takes a 2D array `x`,
     * a 1D array `y`, and a 1D array `z` as input, reformats `z` into a 2D array, and
     * forwards the processed inputs to another overloaded `test` method.
     *
     * @param x a 2D array representing the primary input data, typically of dimensions (n, p)
     * @param y a 1D array representing the target variable, typically of length n
     * @param z a 1D array representing additional input features, typically of length n
     * @return a {@code Result} object containing the output of the test
     */
    public Result test(double[][] x, double[] y, double[] z) {
        double[][] zm = new double[z.length][1];
        for (int i = 0; i < z.length; i++) zm[i][0] = z[i];
        return test(x, y, zm);
    }

    /**
     * Conducts a test using the provided input data and returns the results.
     *
     * @param x a 2D array representing the input features, generally of size (n, p),
     *          where n is the number of samples and p is the number of features.
     * @param y a 1D array representing the target variable or labels, typically of length n.
     * @param z a 2D array representing additional input features or data, generally of size (n, q),
     *          where q is the number of additional features.
     * @return a {@code Result} object containing the outcome of the test.
     */
    public Result test(double[][] x, double[] y, double[][] z) {
        return run(x, y, z, seed);
    }

    // ===================================================================== //
    //  CORD core                                                             //
    // ===================================================================== //
    private Result run(double[][] x, double[] y, double[][] z, long baseSeed) {
        final int n = y.length;
        final int K = numThresholds;
        final int p = x[0].length;
        final int S = Math.max(1, numRepeats);

        int M = Math.max(3, numFolds);
        while (M > 3 && n / M < minFoldSize) M--;   // keep folds workable at small n
        if (n < 3 * 2) return new Result(Double.NaN, Double.NaN, Double.NaN,
                Double.NaN, Double.NaN, "degenerate", n, p);

        double[][] xz = hstack(x, z);
        double[] qLevels = new double[K];
        for (int k = 0; k < K; k++) qLevels[k] = (k + 0.5) / K;
        double[] floorK = new double[K];
        for (int k = 0; k < K; k++)
            floorK[k] = Math.max(varFloorMin, varFloorFrac * qLevels[k] * (1.0 - qLevels[k]));

        // Per-point, per-threshold cross-fitted scores, averaged over S partitions.
        double[][] psi = new double[n][K];
        for (int s = 0; s < S; s++) {
            Random rng = new Random(mix(baseSeed, s));
            int[] perm = permutation(n, rng);
            int[][] folds = arraySplit(perm, M);
            for (int r = 0; r < M; r++) {
                int[] fScore = folds[r];
                int[] fDir = folds[(r + 1) % M];
                int[] fTrain = unionExcept(folds, r, (r + 1) % M);
                long[] sb = new long[3 + K];               // pCdf, qCdf, eCdf, K regressors
                for (int i = 0; i < sb.length; i++) sb[i] = rng.nextLong();
                double[][] psiRot = scoreRotation(x, y, xz, fTrain, fDir, fScore, sb, K, floorK);
                for (int i = 0; i < fScore.length; i++)
                    for (int k = 0; k < K; k++) psi[fScore[i]][k] += psiRot[i][k];
            }
        }
        for (int i = 0; i < n; i++)
            for (int k = 0; k < K; k++) psi[i][k] /= S;

        return calibrate(psi, n, p);
    }

    /**
     * One role assignment: train folds fit the p/q CDFs; the dir fold fits the
     * centering regression m_hat and a fresh CDF e; the score fold forms the
     * per-point, per-threshold scores psi_{ik} = (g_k - m_k)(1{Y<=t_k} - e_k).
     * Returns the (|score| x K) score matrix (no threshold averaging here).
     */
    private double[][] scoreRotation(double[][] x, double[] y, double[][] xz,
                                     int[] foldTrain, int[] foldDir, int[] foldScore,
                                     long[] sb, int K, double[] floorK) {
        // Thresholds = K quantiles of Y on the TRAIN folds only, at (k + 0.5)/K.
        double[] yTr = gather(y, foldTrain);
        double[] yTrSorted = yTr.clone();
        Arrays.sort(yTrSorted);
        double[] thr = new double[K];
        for (int k = 0; k < K; k++) thr[k] = quantileType7(yTrSorted, (k + 0.5) / K);

        // p_cdf = P(Y<=t | X) ;  q_cdf = P(Y<=t | X,Z)   on the train folds
        Cdf pCdf = fitCdfCumulative(rows(x, foldTrain), yTr, thr, sb[0]);
        Cdf qCdf = fitCdfCumulative(rows(xz, foldTrain), yTr, thr, sb[1]);

        // witness g = (q - p)/max(p(1-p), floor_k) on dir (target) and score folds
        double[][] gDir = witness(pCdf.eval(rows(x, foldDir)), qCdf.eval(rows(xz, foldDir)), floorK);
        double[][] gSc = witness(pCdf.eval(rows(x, foldScore)), qCdf.eval(rows(xz, foldScore)), floorK);

        // m_hat[:,k] = E[g_k | X], per-threshold regressor fit on dir, predicted on score.
        double[][] xDir = rows(x, foldDir), xSc = rows(x, foldScore);
        BinMapper bmDir = BinMapper.fit(xDir, maxBins);
        int[][] binXDir = bmDir.transform(xDir);
        double[][] mSc = new double[foldScore.length][K];
        for (int k = 0; k < K; k++) {
            double[] target = column(gDir, k);
            GBRegressor reg = new GBRegressor(this).fit(binXDir, bmDir, target, sb[3 + k]);
            double[] pred = reg.predict(xSc);
            for (int i = 0; i < foldScore.length; i++) mSc[i][k] = pred[i];
        }

        // e_cdf = fresh P(Y<=t | X) on the dir fold (disjoint from p_cdf's train data)
        Cdf eCdf = fitCdfCumulative(xDir, gather(y, foldDir), thr, sb[2]);
        double[][] eSc = eCdf.eval(xSc);

        double[] ySc = gather(y, foldScore);
        double[][] out = new double[foldScore.length][K];
        for (int i = 0; i < foldScore.length; i++)
            for (int k = 0; k < K; k++) {
                double resid = (ySc[i] <= thr[k] ? 1.0 : 0.0) - eSc[i][k];
                out[i][k] = (gSc[i][k] - mSc[i][k]) * resid;
            }
        return out;
    }

    // ---- witness (per-threshold adaptive floor) --------------------------- //
    private double[][] witness(double[][] pm, double[][] qm, double[] floorK) {
        int n = pm.length, K = pm[0].length;
        double[][] g = new double[n][K];
        for (int i = 0; i < n; i++)
            for (int k = 0; k < K; k++) {
                double pp = pm[i][k];
                double v = Math.max(pp * (1.0 - pp), floorK[k]);
                g[i][k] = (qm[i][k] - pp) / v;
            }
        return g;
    }

    // ===================================================================== //
    //  Statistics: GLS mean, per-threshold max, multiplier calibration       //
    // ===================================================================== //
    private Result calibrate(double[][] psi, int n, int dimX) {
        final int K = psi[0].length;
        final int B = Math.max(0, numBootstrap);

        // per-threshold means / sds, and the K x K sample covariance
        double[] mK = new double[K], sK = new double[K];
        for (int k = 0; k < K; k++) {
            double[] col = column(psi, k);
            mK[k] = mean(col);
            sK[k] = sampleSd(col, mK[k]);
        }
        double[][] cov = new double[K][K];
        for (int i = 0; i < n; i++)
            for (int a = 0; a < K; a++) {
                double da = psi[i][a] - mK[a];
                for (int b = a; b < K; b++) cov[a][b] += da * (psi[i][b] - mK[b]);
            }
        double avgDiag = 0;
        for (int a = 0; a < K; a++)
            for (int b = a; b < K; b++) {
                cov[a][b] /= (n - 1);
                cov[b][a] = cov[a][b];
                if (a == b) avgDiag += cov[a][a];
            }
        avgDiag /= K;
        if (!(avgDiag > 0))
            return new Result(Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                    "degenerate", n, dimX);
        double[][] sigmaR = new double[K][K];
        for (int a = 0; a < K; a++) {
            System.arraycopy(cov[a], 0, sigmaR[a], 0, K);
            sigmaR[a][a] += glsRidge * avgDiag;
        }
        double[] ones = new double[K];
        Arrays.fill(ones, 1.0);
        double[] w = cholSolve(sigmaR, ones);
        if (w == null) {
            w = new double[K];
            Arrays.fill(w, 1.0 / K);
        }   // GLS fallback: equal weights

        // GLS-combined scalar scores u_i = w' psi_i
        double[] u = new double[n];
        for (int i = 0; i < n; i++) {
            double acc = 0;
            for (int k = 0; k < K; k++) acc += w[k] * psi[i][k];
            u[i] = acc;
        }
        double uBar = mean(u);
        double uSd = sampleSd(u, uBar);
        if (!(uSd > 0))
            return new Result(Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                    "degenerate", n, dimX);
        double tMean = Math.sqrt(n) * uBar / uSd;

        // per-threshold studentized statistics; max-type
        double tMax = Double.NEGATIVE_INFINITY;
        for (int k = 0; k < K; k++)
            if (sK[k] > 0) tMax = Math.max(tMax, Math.sqrt(n) * mK[k] / sK[k]);

        if (B == 0) {   // normal fallback: mean-type only, engine-3-style calibration
            double pm = normSf(tMean);
            return new Result(pm, tMean, tMax, pm, Double.NaN, "ok", n, dimX);
        }

        // ---- studentized multiplier bootstrap, joint over (T_mean, T_max) ---- //
        double[] uc = new double[n];                 // centered GLS scores
        for (int i = 0; i < n; i++) uc[i] = u[i] - uBar;
        double[][] c = new double[n][K];             // centered per-threshold scores
        for (int i = 0; i < n; i++)
            for (int k = 0; k < K; k++) c[i][k] = psi[i][k] - mK[k];

        Random brng = new Random(mix(seed, 0x0B0057));
        boolean mammen = !"rademacher".equalsIgnoreCase(multiplier);
        double[] tmB = new double[B], txB = new double[B];
        double[] xi = new double[n];
        double[] tmpK = new double[K], tmp2K = new double[K];
        for (int b = 0; b < B; b++) {
            for (int i = 0; i < n; i++)
                xi[i] = mammen ? mammenWeight(brng) : (brng.nextBoolean() ? 1.0 : -1.0);
            // mean-type
            double s1 = 0, s2 = 0;
            for (int i = 0; i < n; i++) {
                double v = xi[i] * uc[i];
                s1 += v;
                s2 += v * v;
            }
            double mu = s1 / n;
            double var = (s2 - n * mu * mu) / (n - 1);
            tmB[b] = var > 0 ? Math.sqrt(n) * mu / Math.sqrt(var) : Double.NEGATIVE_INFINITY;
            // max-type (same xi -> joint null)
            Arrays.fill(tmpK, 0.0);
            Arrays.fill(tmp2K, 0.0);
            for (int i = 0; i < n; i++) {
                double xii = xi[i];
                double[] ci = c[i];
                for (int k = 0; k < K; k++) {
                    double v = xii * ci[k];
                    tmpK[k] += v;
                    tmp2K[k] += v * v;
                }
            }
            double best = Double.NEGATIVE_INFINITY;
            for (int k = 0; k < K; k++) {
                double muk = tmpK[k] / n;
                double vark = (tmp2K[k] - n * muk * muk) / (n - 1);
                if (vark > 0) best = Math.max(best, Math.sqrt(n) * muk / Math.sqrt(vark));
            }
            txB[b] = best;
        }

        double pMean = (1.0 + countGE(tmB, tMean)) / (B + 1.0);
        double pMax = (1.0 + countGE(txB, tMax)) / (B + 1.0);

        // min-p combination, calibrated on the same joint draws (single-loop Westfall-Young)
        double[] tmSorted = tmB.clone();
        Arrays.sort(tmSorted);
        double[] txSorted = txB.clone();
        Arrays.sort(txSorted);
        double minObs = Math.min(pMean, pMax);
        int hits = 0;
        for (int b = 0; b < B; b++) {
            double pmB = countGEsorted(tmSorted, tmB[b]) / (double) B;   // includes self => >= 1/B
            double pxB = countGEsorted(txSorted, txB[b]) / (double) B;
            if (Math.min(pmB, pxB) <= minObs) hits++;
        }
        double pComb = (1.0 + hits) / (B + 1.0);

        double pReport = "mean".equalsIgnoreCase(combine) ? pMean
                : "max".equalsIgnoreCase(combine) ? pMax : pComb;
        return new Result(pReport, tMean, tMax, pMean, pMax, "ok", n, dimX);
    }

    private Cdf fitCdfCumulative(double[][] feat, double[] y, double[] thr, long modelSeed) {
        final int K = thr.length;
        final double clip = cdfClip;
        final int n = y.length;

        BinMapper bm = BinMapper.fit(feat, maxBins);
        int[][] binned = bm.transform(feat);
        Random srng = new Random(mix(modelSeed, 41));

        final GBBinary[] models = new GBBinary[K];
        final double[] constant = new double[K];      // used where models[k] == null
        for (int k = 0; k < K; k++) {
            int[] lab = new int[n];
            int pos = 0;
            for (int i = 0; i < n; i++) {
                lab[i] = (y[i] <= thr[k]) ? 1 : 0;
                pos += lab[i];
            }
            if (pos == 0 || pos == n) {               // one-sided prevalence: constant CDF value
                models[k] = null;
                constant[k] = clamp(pos == 0 ? 0.0 : 1.0, clip, 1.0 - clip);
            } else {
                models[k] = new GBBinary(this).fit(binned, bm, lab, srng.nextLong());
            }
        }
        return f -> {
            int[][] bF = bm.transform(f);
            double[][] out = new double[f.length][K];
            double[] row = new double[K];
            for (int i = 0; i < f.length; i++) {
                for (int k = 0; k < K; k++)
                    row[k] = (models[k] == null) ? constant[k] : models[k].predictOne(bF[i]);
                pavNondecreasing(row);                // ordinal coherence across thresholds
                for (int k = 0; k < K; k++) out[i][k] = clamp(row[k], clip, 1.0 - clip);
            }
            return out;
        };
    }

    // ===================================================================== //
    //  Conditional CDF: K cumulative binary GB fits + PAV monotonization     //
    // ===================================================================== //
    interface Cdf {
        double[][] eval(double[][] f);
    }

    /**
     * Represents the result of a statistical test for conditional independence,
     * specifically designed to assess the relationship between variables under
     * the hypothesis H0: Y is conditionally independent of Z given X.
     * This class encapsulates key metrics and information about the test outcome,
     * including p-values, test statistics, sample size, and the dimensionality of the conditioning set.
     *
     * Instances of this class are immutable and provide methods to interpret
     * the test results and their significance.
     */
    public static final class Result {
        /**
         * The reported p-value, typically representing the calibrated minimum p-value
         * for the statistical test. It is used to evaluate the strength of evidence
         * against the null hypothesis, with smaller values indicating stronger evidence.
         * The value is derived from combining statistics in the context of the test.
         */
        public final double pvalue;        // reported p (per `combine`; default calibrated min-p)
        /**
         * Represents the GLS (Generalized Least Squares) mean-type test statistic (referred to as T_mean).
         * This statistic is used in hypothesis testing to measure the strength of evidence against
         * the null hypothesis, particularly in situations involving conditional independence testing.
         * <p>
         * The value of this statistic is computed during the statistical test and is intended to
         * assess the performance of the model under the mean-type framework.
         */
        public final double statistic;     // GLS mean-type statistic T_mean
        /**
         * The GLS max-type test statistic (T_max), representing the maximum test statistic
         * value obtained in a statistical test. It is used to assess the conditional independence
         * of variables in the test.
         */
        public final double statisticMax;  // max-type statistic T_max
        /**
         * The p-value corresponding to the T_mean statistic.
         * This value is computed using either a bootstrap method or a normal approximation if the
         * bootstrap sample size (B) is zero.
         * The T_mean statistic generally represents a mean-type test statistic in the context of
         * a statistical hypothesis test.
         */
        public final double pMean;         // p for T_mean (bootstrap, or normal if B = 0)
        /**
         * The p-value associated with the GLS max-type test statistic (T_max) for the statistical test.
         * This value is computed using a bootstrap methodology. If the bootstrap iterations (B) are set to 0,
         * the value will be set to {@code NaN}.
         * <p>
         * This field provides insight into the statistical significance of the maximum test statistic,
         * aiding in hypothesis testing frameworks where the T_max statistic is relevant.
         */
        public final double pMax;          // p for T_max  (bootstrap; NaN if B = 0)
        /**
         * Represents the status of the test result, indicating whether the test
         * outcome is valid or represents a degenerate case.
         * <p>
         * Possible values:
         * - "ok": The test result is valid.
         * - "degenerate": The test result corresponds to a degenerate case, such
         * as when the variance of the test statistic is zero.
         */
        public final String status;        // "ok" or "degenerate"
        /**
         * The sample size used in the statistical test.
         * This value represents the number of data points included
         * in the analysis and is a key factor in determining
         * the power and statistical validity of the test results.
         */
        public final int n;
        /**
         * The dimensionality of the conditioning set X used in the statistical test.
         * This variable represents the number of dimensions or variables included
         * in the set X, which conditions the relationship being tested for
         * conditional independence.
         */
        public final int dimX;

        /**
         * Constructs a new {@code Result} instance representing the outcome of a statistical test.
         *
         * @param pvalue       The reported p-value, typically the calibrated minimum p-value for the test.
         * @param statistic    The GLS mean-type test statistic (T_mean).
         * @param statisticMax The GLS max-type test statistic (T_max).
         * @param pMean        The p-value for the T_mean statistic (bootstrap, or normal approximation if B = 0).
         * @param pMax         The p-value for the T_max statistic (bootstrap; NaN if B = 0).
         * @param status       The status of the test result, either "ok" or "degenerate", indicating
         *                     whether the test result is valid or represents a degenerate case.
         * @param n            The sample size used in the statistical test.
         * @param dimX         The dimensionality of the conditioning set X in the test.
         */
        Result(double pvalue, double statistic, double statisticMax,
               double pMean, double pMax, String status, int n, int dimX) {
            this.pvalue = pvalue;
            this.statistic = statistic;
            this.statisticMax = statisticMax;
            this.pMean = pMean;
            this.pMax = pMax;
            this.status = status;
            this.n = n;
            this.dimX = dimX;
        }

        /**
         * Determines if the null hypothesis should be rejected based on the given significance level.
         * The null hypothesis is rejected if the method's `status` is "ok" and the `pvalue` is less than the given threshold.
         *
         * @param a the significance level (threshold) for rejecting the null hypothesis
         * @return {@code true} if the null hypothesis is rejected, {@code false} otherwise
         */
        public boolean reject(double a) {
            return status.equals("ok") && pvalue < a;
        }

        /**
         * Represents a string representation of the statistical test result for the null hypothesis
         * H0: Y is conditionally independent of Z given X.
         * The format of the string depends on the status of the result:
         * - If the status is "ok", the string includes the test statistics T_mean, T_max, and the p-value.
         * - If the status indicates a degenerate case, a fixed message is returned.
         *
         * @return A string summarizing the test result, including test statistics and p-value
         * if the status is "ok", or "degenerate (zero score variance)" otherwise.
         */
        @Override
        public String toString() {
            String verdict = "degenerate (zero score variance)";
            if (status.equals("ok"))
                verdict = String.format("T_mean = %.4f  T_max = %.4f   p = %.4g", statistic, statisticMax, pvalue);
            return "CORD4  H0: Y _||_ Z | X   [" + verdict + "]";
        }
    }

    /**
     * Feature binner: per-feature quantile bin edges, <= maxBins bins.
     */
    static final class BinMapper {
        final double[][] thr;   // per feature, ascending edges
        final int[] nBins;      // per feature = thr[f].length + 1

        private BinMapper(double[][] thr, int[] nBins) {
            this.thr = thr;
            this.nBins = nBins;
        }

        static BinMapper fit(double[][] X, int maxBins) {
            int p = X[0].length, n = X.length;
            double[][] edges = new double[p][];
            int[] nb = new int[p];
            for (int f = 0; f < p; f++) {
                double[] col = new double[n];
                for (int i = 0; i < n; i++) col[i] = X[i][f];
                double[] sorted = col.clone();
                Arrays.sort(sorted);
                double[] uniq = unique(sorted);
                double[] e;
                if (uniq.length <= maxBins) {                  // midpoints between distinct values
                    e = new double[Math.max(0, uniq.length - 1)];
                    for (int i = 0; i + 1 < uniq.length; i++) e[i] = 0.5 * (uniq[i] + uniq[i + 1]);
                } else {                                        // maxBins-1 quantile edges
                    e = new double[maxBins - 1];
                    for (int i = 1; i < maxBins; i++) e[i - 1] = quantileType7(sorted, (double) i / maxBins);
                    e = unique(e);                              // guard against ties collapsing edges
                }
                edges[f] = e;
                nb[f] = e.length + 1;
            }
            return new BinMapper(edges, nb);
        }

        int[][] transform(double[][] X) {
            int n = X.length, p = X[0].length;
            int[][] out = new int[n][p];
            for (int i = 0; i < n; i++)
                for (int f = 0; f < p; f++) out[i][f] = searchsortedLeft(thr[f], X[i][f]);
            return out;
        }
    }

    /**
     * A single boosted tree (leaf-wise / best-first). Leaf values include shrinkage.
     */
    static final class TreeNode {
        boolean leaf = true;
        int feature = -1, binThr = -1;
        TreeNode left, right;
        double value;
    }

    static final class BuildNode {
        int[] samples;
        double sumG, sumH;
        TreeNode node;
        double gain = 0.0;
        int feature = -1, binThr = -1;
    }

    /**
     * Binary logistic gradient-boosted classifier for one cumulative target
     * 1{Y <= t_k}. Uses all fold rows; only two classes exist, so the only
     * stratification hazard is a singleton class, handled by disabling early
     * stopping for that threshold.
     */
    static final class GBBinary {
        final CordEngine4 cfg;
        double baseline;                 // log-odds of prevalence
        List<TreeNode> trees;

        GBBinary(CordEngine4 cfg) {
            this.cfg = cfg;
        }

        GBBinary fit(int[][] binned, BinMapper bm, int[] lab, long modelSeed) {
            int n = binned.length;
            Random rng = new Random(modelSeed);

            // stratified train/val split; fall back to es=off if a class is a singleton
            int pos = 0;
            for (int v : lab) pos += v;
            boolean es = cfg.earlyStopping && pos >= 2 && (n - pos) >= 2 && n >= 4;
            int[] train, val;
            if (es) {
                int[][] tv = stratifiedBinarySplit(lab, cfg.validationFraction, rng);
                train = tv[0];
                val = tv[1];
            } else {
                train = iota(n);
                val = new int[0];
            }
            int nTr = train.length, nVal = val.length;
            int[][] bTr = new int[nTr][];
            int[] yTr = new int[nTr];
            for (int i = 0; i < nTr; i++) {
                bTr[i] = binned[train[i]];
                yTr[i] = lab[train[i]];
            }
            int[][] bVal = new int[nVal][];
            int[] yVal = new int[nVal];
            for (int i = 0; i < nVal; i++) {
                bVal[i] = binned[val[i]];
                yVal[i] = lab[val[i]];
            }

            int posTr = 0;
            for (int v : yTr) posTr += v;
            double prev = clamp(posTr / (double) nTr, 1e-6, 1.0 - 1e-6);
            baseline = Math.log(prev / (1.0 - prev));

            trees = new ArrayList<>();
            double[] rawTr = new double[nTr];
            Arrays.fill(rawTr, baseline);
            double[] rawVal = new double[nVal];
            Arrays.fill(rawVal, baseline);
            int[] rootSamples = iota(nTr);
            List<Double> scores = new ArrayList<>();
            for (int iter = 0; iter < cfg.numEstimators; iter++) {
                double[] grad = new double[nTr], hess = new double[nTr];
                for (int i = 0; i < nTr; i++) {
                    double pr = sigmoid(rawTr[i]);
                    grad[i] = pr - yTr[i];
                    hess[i] = Math.max(pr * (1.0 - pr), 1e-12);
                }
                TreeNode tree = growTree(bTr, bm.nBins, grad, hess, rootSamples,
                        cfg.maxLeafNodes, cfg.minSamplesLeaf, cfg.l2Regularization, cfg.learningRate);
                trees.add(tree);
                for (int i = 0; i < nTr; i++) rawTr[i] += predictTree(tree, bTr[i]);
                for (int i = 0; i < nVal; i++) rawVal[i] += predictTree(tree, bVal[i]);
                if (es) {
                    double loss = 0;
                    for (int i = 0; i < nVal; i++) {
                        double pr = clamp(sigmoid(rawVal[i]), 1e-15, 1.0 - 1e-15);
                        loss += yVal[i] == 1 ? -Math.log(pr) : -Math.log(1.0 - pr);
                    }
                    scores.add(-(nVal > 0 ? loss / nVal : 0.0));
                    if (shouldStop(scores, cfg.nIterNoChange, cfg.tol)) break;
                }
            }
            return this;
        }

        double predictOne(int[] binnedRow) {
            double raw = baseline;
            for (TreeNode t : trees) raw += predictTree(t, binnedRow);
            return sigmoid(raw);
        }
    }

    /**
     * Squared-error regressor for m_hat = E[g|X].  Unchanged from engine 3.
     */
    static final class GBRegressor {
        final CordEngine4 cfg;
        BinMapper bm;
        double baseline;
        List<TreeNode> trees;

        GBRegressor(CordEngine4 cfg) {
            this.cfg = cfg;
        }

        GBRegressor fit(int[][] binned, BinMapper bm, double[] target, long modelSeed) {
            this.bm = bm;
            int n = binned.length;
            boolean es = cfg.earlyStopping && n >= 4;
            int[] train, val;
            if (es) {
                int[] perm = permutation(n, new Random(modelSeed));
                int nVal = Math.max(1, (int) Math.floor(cfg.validationFraction * n));
                val = Arrays.copyOfRange(perm, 0, nVal);
                train = Arrays.copyOfRange(perm, nVal, n);
            } else {
                train = iota(n);
                val = new int[0];
            }
            int nTr = train.length, nVal = val.length;
            int[][] bTr = new int[nTr][];
            double[] yTr = new double[nTr];
            for (int i = 0; i < nTr; i++) {
                bTr[i] = binned[train[i]];
                yTr[i] = target[train[i]];
            }
            int[][] bVal = new int[nVal][];
            double[] yVal = new double[nVal];
            for (int i = 0; i < nVal; i++) {
                bVal[i] = binned[val[i]];
                yVal[i] = target[val[i]];
            }

            baseline = mean(yTr);
            trees = new ArrayList<>();
            double[] rawTr = new double[nTr];
            Arrays.fill(rawTr, baseline);
            double[] rawVal = new double[nVal];
            Arrays.fill(rawVal, baseline);
            int[] rootSamples = iota(nTr);
            List<Double> scores = new ArrayList<>();
            for (int iter = 0; iter < cfg.numEstimators; iter++) {
                double[] grad = new double[nTr], hess = new double[nTr];
                for (int i = 0; i < nTr; i++) {
                    grad[i] = rawTr[i] - yTr[i];
                    hess[i] = 1.0;
                }
                TreeNode tree = growTree(bTr, bm.nBins, grad, hess, rootSamples,
                        cfg.maxLeafNodes, cfg.minSamplesLeaf, cfg.l2Regularization, cfg.learningRate);
                trees.add(tree);
                for (int i = 0; i < nTr; i++) rawTr[i] += predictTree(tree, bTr[i]);
                for (int i = 0; i < nVal; i++) rawVal[i] += predictTree(tree, bVal[i]);
                if (es) {
                    double mse = 0;
                    for (int i = 0; i < nVal; i++) {
                        double d = rawVal[i] - yVal[i];
                        mse += d * d;
                    }
                    scores.add(-(nVal > 0 ? mse / nVal : 0.0));
                    if (shouldStop(scores, cfg.nIterNoChange, cfg.tol)) break;
                }
            }
            return this;
        }

        double[] predict(double[][] F) {
            int[][] bF = bm.transform(F);
            double[] out = new double[F.length];
            for (int i = 0; i < F.length; i++) {
                double v = baseline;
                for (TreeNode t : trees) v += predictTree(t, bF[i]);
                out[i] = v;
            }
            return out;
        }
    }
}
