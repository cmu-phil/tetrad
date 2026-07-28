package edu.cmu.tetrad.search;
/*
 * Cord.java -- Faithful single-file Java port of CORD (Orthogonal Rank-Score
 * Conditional Independence Test) for  H0: Y _||_ Z | X.
 *
 * This is a dependency-free (pure JDK, Java 8+) port of the Python reference
 * `cord.py` (numpy + scipy + scikit-learn). It reproduces the CORD algorithm
 * exactly -- estimand, A/B/C cross-fit, the one-multiclass-model -> cumulative
 * CDF, the double-residualized score, and the studentized one-sided-normal
 * statistic -- and it embeds a from-scratch histogram gradient-boosted-trees
 * learner mirroring sklearn's HistGradientBoosting{Classifier,Regressor}
 * (255-bin histograms, best-first growth to 31 leaves, learning_rate 0.1,
 * max_iter 300, min_samples_leaf 20, l2 0, 15% early stopping with the
 * class-stratification fallback).
 *
 * Fidelity note. Bit-for-bit equality with the Python output is impossible
 * across languages: numpy's PCG64 stream, sklearn's exact bin edges / split
 * finding, and non-associative floating-point summation all differ. CORD is
 * built so this does not matter -- the orthogonalized (doubly-residualized)
 * cross-fit score is first-order insensitive to the nuisance estimator, so the
 * TEST is statistically equivalent even though the fitted trees and the p-value
 * on a given dataset are not identical to Python's. Every CORD-specific choice
 * (splits, thresholds, binning rule, clips, floors, statistic) is matched exactly.
 *
 * Reference: cord.py  (class CORD; run() at cord.py:58-82 in the package copy).
 *
 * Build:  javac Cord.java
 * Run  :  java Cord                              # Monte-Carlo self-test (size + power)
 *         java Cord <file.tsv> <yCol> <zCol> <xCol1[,xCol2,...]>   # test a triple
 */

import java.util.*;

/**
 * CordEngine3 provides a suite of tools and algorithms for model building,
 * evaluation, and statistical computations. This class implements core
 * functionality from the CORD framework, enabling stratified data splitting,
 * tree-based model construction, and advanced statistical analyses.
 * <p>
 * CordEngine3 includes methods for growing trees, optimizing models,
 * applying statistical tests, handling arrays, and computing metrics.
 * It supports features such as early stopping, cross-validation,
 * and stratified train-validation splits for robust model evaluation.
 */
public class CordEngine3 implements CordEngine {

    // ===================================================================== //
    //  Configuration  (the six Tetrad params + the hard-coded knobs)         //
    // ===================================================================== //

    /**
     * The significance level used for hypothesis testing in the CordEngine3 class.
     * <p>
     * This variable represents the threshold for determining statistical significance
     * in decision-making. A common value for the significance level is 0.05, indicating
     * a 5% risk of concluding that an effect exists when it does not (Type I error).
     * The alpha value is used throughout various computations and statistical tests
     * within the CordEngine3 framework.
     */
    public double alpha = 0.05;            // significance level for the decision
    /**
     * The number of thresholds used in model training or scoring processes.
     * <p>
     * This variable determines the granularity with which continuous variables
     * are discretized into bins or categories. It is typically used in algorithms
     * that rely on histogram-based computations or thresholding for feature splits.
     * <p>
     * A higher value allows for finer-grained divisions but may increase computation
     * time and memory usage, whereas a lower value offers quicker computation at
     * the cost of potentially reduced model precision.
     */
    public int numThresholds = 9;          // K  (cordNumThresholds)
    /**
     * The maximum number of estimators (iterations) used in constructing the model.
     * This parameter controls the upper limit on the number of base learners that
     * can be fit during the training process. Increasing this value may improve
     * the model's performance but could also lead to longer training times and a
     * risk of overfitting if not carefully managed.
     * <p>
     * Commonly used in ensemble learning algorithms such as gradient boosting to
     * determine the total number of boosting rounds or tree growth iterations.
     */
    public int numEstimators = 300;        // max_iter (cordNumEstimators)
    /**
     * The learning rate used in the implementation of gradient-based algorithms.
     * <p>
     * This parameter controls the step size at each iteration during the model optimization process.
     * A smaller value results in slower but more stable convergence, while a larger value may
     * speed up convergence but risks overshooting the optimal solution. It is a crucial
     * hyperparameter that can significantly affect the performance and training dynamics of the model.
     */
    public double learningRate = 0.1;      // (cordLearningRate)
    /**
     * The maximum number of leaf nodes allowed for a tree in the CORD engine.
     * <p>
     * This parameter determines the complexity of the tree-based models
     * generated by the algorithm. It sets an upper limit on the number
     * of leaf nodes that can be created during the tree construction process.
     * Higher values enable more complex trees, potentially capturing more
     * nuanced patterns in the data, but may also increase the risk of
     * overfitting. Conversely, lower values enforce simpler models, which
     * may generalize better but could miss intricate relationships.
     */
    public int maxLeafNodes = 31;          // (cordMaxLeafNodes)
    /**
     * Represents the seed value used in various randomized operations within the CordEngine3 framework.
     * <p>
     * This field combines a primary split seed and auxiliary nuisance seeds to ensure
     * reproducibility in processes such as random shuffling, stratified splitting, and
     * stochastic model initialization. The specific value of the seed dictates the deterministic
     * nature of these operations, allowing experiments and models to be recreated with identical behavior.
     */
    public long seed = 0L;                 // (cordSeed); split + nuisance seeds

    // Hard-coded to match the Python reference exactly (not exposed as knobs).

    /**
     * Indicates whether the early stopping criterion is enabled for the training process.
     * <p>
     * When set to {@code true}, the model will monitor the validation performance
     * during training and stop the process early if the performance does not improve
     * after a predefined number of iterations. This technique helps prevent overfitting
     * and reduces computational cost by terminating training that no longer yields meaningful
     * improvements.
     */
    public boolean earlyStopping = true;
    /**
     * Proportion of the training data to set aside as validation data during model training.
     * Used for evaluating the model's performance and supporting techniques like early stopping.
     * <p>
     * The value should be a fraction between 0.0 (no data reserved for validation) and 1.0
     * (all data reserved for validation), where typically a small portion of the entire
     * dataset (e.g., 0.1 to 0.3) is reserved for validation.
     * <p>
     * This parameter ensures that the model generalizes well by monitoring its performance
     * on unseen data during training.
     */
    public double validationFraction = 0.15;
    /**
     * A small constant used to clip or constrain cumulative distribution function (CDF) values
     * in various computations to avoid numerical instability or division by zero.
     * <p>
     * - This constant can ensure that probabilities stay within acceptable bounds.
     * <p>
     * - Commonly useful in tasks like statistical modeling, tree training, or gradient-based methods.
     * <p>
     * - Typically prevents too small or extreme values from impacting calculations.
     */
    public double cdfClip = 1e-3;
    /**
     * Represents the minimum allowable floor value for variance calculations.
     * <p>
     * This parameter is used to ensure numerical stability during operations
     * that involve variance computations, preventing the variance from becoming
     * too small and causing potential issues such as division by zero or
     * excessively high growth rates in calculations.
     * <p>
     * The value is typically set as a very small positive number.
     */
    public double varFloor = 0.02;
    /**
     * Specifies the minimum number of samples required to be at a leaf node in a decision tree.
     * <p>
     * This parameter acts as a constraint on the growth of the decision tree to prevent overfitting.
     * Increasing its value requires a leaf to contain more samples, which can make the model
     * more generalized by reducing tree depth while potentially lowering its predictive power.
     * <p>
     * It is used during the tree-building process to determine whether further splits are allowed
     * based on the number of samples in a node.
     */
    public int minSamplesLeaf = 20;
    /**
     * Represents the L2 regularization parameter used to penalize large model coefficients
     * and prevent overfitting during the model training process.
     *
     * This parameter adds a term to the loss function proportional to the sum of the
     * squares of the model's coefficients, effectively shrinking them towards zero.
     * A higher value of this parameter leads to stronger regularization, while a value
     * of 0 disables L2 regularization.
     */
    public double l2Regularization = 0.0;
    /**
     * Specifies the maximum number of discrete bins that can be used for feature quantization
     * in tree-based algorithms. Binning is an essential preprocessing step that simplifies
     * continuous features into discrete categories, enabling efficient computation of histograms
     * for gradient and hessian aggregation during training.
     * <p>
     * Larger values allow for finer granularity, capturing more feature detail at the cost of
     * increased computational and memory requirements. Smaller values result in coarser
     * granularity, which may lead to information loss but improve efficiency.
     * <p>
     * Typical usage involves setting this parameter based on the complexity of the dataset
     * and the hardware resources available.
     */
    public int maxBins = 255;
    /**
     * Specifies the number of iterations with no improvement in model performance
     * after which the training process may be halted early.
     * <p>
     * This variable is used as a criterion for implementing early stopping
     * during model training. If the model's performance metric does not improve
     * over the course of this many consecutive iterations, the training process
     * may terminate to prevent overfitting and save computational resources.
     * <p>
     * A higher value results in longer training times before early stopping
     * is triggered, whereas a lower value may stop training prematurely if the
     * performance metric fluctuates minimally.
     */
    public int nIterNoChange = 10;
    /**
     * A tolerance value utilized as a convergence threshold in numerical methods
     * or iterative algorithms within the CordEngine3 class.
     * <p>
     * This parameter is typically used to determine when an iterative process,
     * such as optimization or model fitting, has sufficiently converged by
     * comparing the difference between consecutive steps or iterations against
     * this threshold.
     * <p>
     * A smaller value indicates a stricter convergence criterion, which may
     * lead to more precise results at the cost of increased computational effort.
     */
    public double tol = 1e-7;

    /**
     * Initializes a new instance of the CordEngine3 class.
     * <p>
     * The constructor sets up the environment for the CordEngine3 engine,
     * which provides functionality for building and scoring models through
     * core CORD algorithms. The instance will utilize various configuration
     * parameters and internal methods for tasks like tree growth,
     * stratified data splitting, and model evaluation in its operations.
     */
    public CordEngine3() {
    }

    static double predictTree(TreeNode nd, int[] row) {
        while (!nd.leaf) nd = (row[nd.feature] <= nd.binThr) ? nd.left : nd.right;
        return nd.value;
    }

    // ===================================================================== //
    //  Public API : test(x, y, z)  (argument order mirrors cord.py fit)      //
    // ===================================================================== //

    /**
     * Grow one tree by best-first splitting on gradient/hessian histograms.
     */
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
                if (rc < minLeaf) break;                 // accC only grows => rc only shrinks
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

    // ---- stratified train/val split (throws if a class can't be stratified) //
    static int[][] stratifiedSplit(int[] cls, int C, double valFrac, long modelSeed) {
        List<List<Integer>> byClass = new ArrayList<>();
        for (int c = 0; c < C; c++) byClass.add(new ArrayList<>());
        for (int i = 0; i < cls.length; i++) byClass.get(cls[i]).add(i);
        Random rng = new Random(modelSeed);
        List<Integer> valL = new ArrayList<>(), trainL = new ArrayList<>();
        for (int c = 0; c < C; c++) {
            List<Integer> idx = byClass.get(c);
            if (idx.size() < 2) throw new StratifyException();       // mirrors sklearn ValueError
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
     * np.searchsorted(arr, v, side="left"): count of arr[i] STRICTLY LESS than v. arr ascending.
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
     * np.array_split(perm, 3): first (n%3) chunks get one extra element.
     */
    static int[][] arraySplit3(int[] perm) {
        int n = perm.length, q = n / 3, r = n % 3;
        int sA = q + (r >= 1 ? 1 : 0), sB = q + (r >= 2 ? 1 : 0), sC = q;
        int[] A = Arrays.copyOfRange(perm, 0, sA);
        int[] B = Arrays.copyOfRange(perm, sA, sA + sB);
        int[] C = Arrays.copyOfRange(perm, sA + sB, sA + sB + sC);
        return new int[][]{A, B, C};
    }

    // ===================================================================== //
    //  Histogram gradient-boosted trees  (embedded, mirrors sklearn HGB)     //
    // ===================================================================== //

    /**
     * One-sided upper p-value  1 - Phi(t) = 0.5*erfc(t/sqrt2).  erfc via NR (frac err < 1.2e-7).
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

    static double[][] softmaxRows(double[][] raw) {
        int n = raw.length, C = (n == 0 ? 0 : raw[0].length);
        double[][] out = new double[n][C];
        for (int i = 0; i < n; i++) {
            double mx = Double.NEGATIVE_INFINITY;
            for (int c = 0; c < C; c++) mx = Math.max(mx, raw[i][c]);
            double sum = 0;
            for (int c = 0; c < C; c++) {
                out[i][c] = Math.exp(raw[i][c] - mx);
                sum += out[i][c];
            }
            for (int c = 0; c < C; c++) out[i][c] /= sum;
        }
        return out;
    }

    static double multiLogLoss(double[][] raw, int[] trueCls) {
        if (raw.length == 0) return 0.0;
        double[][] p = softmaxRows(raw);
        double loss = 0;
        for (int i = 0; i < raw.length; i++) loss += -Math.log(Math.max(p[i][trueCls[i]], 1e-15));
        return loss / raw.length;
    }

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

    static double popStd(double[] a) {
        double m = mean(a), s = 0;
        for (double v : a) {
            double d = v - m;
            s += d * d;
        }
        return Math.sqrt(s / a.length);
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

    // ===================================================================== //
    //  Numeric helpers (match numpy/scipy semantics used by cord.py)         //
    // ===================================================================== //

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

    static int[] unique(int[] a) {
        int[] c = a.clone();
        Arrays.sort(c);
        int[] tmp = new int[c.length];
        int m = 0;
        for (int v : c) if (m == 0 || v != tmp[m - 1]) tmp[m++] = v;
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

    /**
     * Main entry point for the CordEngine3 application.
     *
     * @param args Command-line arguments passed to the program.
     */
    public static void main(String[] args) {
        // Self-test / data-file harness omitted in the Tetrad copy.
    }

    /**
     * Tests the model using the provided data and auxiliary features.
     *
     * @param x A 2D array representing the main feature matrix.
     * @param y An array representing the response variable.
     * @param z An array representing auxiliary features for the model.
     * @return A Result object containing the output of the computation.
     */
    public Result test(double[][] x, double[] y, double[] z) {
        double[][] zm = new double[z.length][1];
        for (int i = 0; i < z.length; i++) zm[i][0] = z[i];
        return test(x, y, zm);
    }

    /**
     * Executes the core functionality of the CORD engine using the provided inputs.
     *
     * @param x A 2D array representing the main feature matrix.
     * @param y An array representing the response variable.
     * @param z A 2D array representing auxiliary features for the model.
     * @return A Result object containing the output of the computation.
     */
    public Result test(double[][] x, double[] y, double[][] z) {
        return run(x, y, z, seed);
    }

    // ===================================================================== //
    //  CORD core (score-pooled cross-fit)                                    //
    //  run(): three cyclic A/B/C role rotations of a SINGLE partition, each   //
    //  fold playing train/direction/score exactly once. Because each fold is  //
    //  "score" exactly once, the three score folds partition [n], so every    //
    //  point receives exactly one honest cross-fitted score psi_i (its        //
    //  nuisances fit on the two folds that exclude it). We POOL all n scores   //
    //  and studentize ONCE: T = sqrt(n) * mean(psi) / sd(psi). This is the     //
    //  standard 3-fold DML cross-fit estimator -- full-sample, directional,    //
    //  and continuous (no atom at p = 1 that the min(1, 2*median(.)) p-level   //
    //  aggregation of the median variant produces on a one-sided statistic).   //
    //  scoreRotation(): one role assignment (port of cord.py run()), returns   //
    //  that rotation's per-score-fold psi vector.                             //
    // ===================================================================== //
    private Result run(double[][] x, double[] y, double[][] z, long theSeed) {
        final int n = y.length;
        final int K = numThresholds;
        final int p = x[0].length;
        Random rng = new Random(theSeed);

        // ONE A/B/C split via permutation + np.array_split(., 3); shared across the rotations.
        int[] perm = permutation(n, rng);
        int[][] folds = arraySplit3(perm);

        // xz = [x | z]   (role-independent; built once)
        double[][] xz = hstack(x, z);

        // Rotation r uses train=folds[r], dir=folds[r+1], score=folds[r+2] (mod 3). The score folds
        // over r = 0,1,2 are folds[2], folds[0], folds[1] -- a partition of [n] -- so concatenating
        // the per-rotation psi vectors yields one honest cross-fitted score per observation.
        double[] psiAll = new double[n];
        int filled = 0;
        for (int r = 0; r < 3; r++) {
            int[] fTrain = folds[r];
            int[] fDir = folds[(r + 1) % 3];
            int[] fScore = folds[(r + 2) % 3];
            // Fresh per-model seed block per rotation (block draw, indexed like the Python `s` vector).
            long[] s = new long[3 + K];
            for (int i = 0; i < s.length; i++) s[i] = rng.nextLong();
            double[] psi = scoreRotation(x, y, xz, fTrain, fDir, fScore, s, K);
            System.arraycopy(psi, 0, psiAll, filled, psi.length);
            filled += psi.length;
        }
        if (filled < n) psiAll = Arrays.copyOf(psiAll, filled);
        if (filled == 0) return new Result(Double.NaN, Double.NaN, "degenerate", n, p);

        // Pool and studentize once over all cross-fitted scores.
        double sd = popStd(psiAll);
        if (!(sd > 0.0))
            return new Result(Double.NaN, Double.NaN, "degenerate", n, p);
        double t = Math.sqrt(filled) * mean(psiAll) / sd;
        return new Result(normSf(t), t, "ok", n, p);
    }

    /**
     * One A/B/C role assignment (port of cord.py run()): the train fold fits the CDFs, the dir
     * fold fits the centering regression + a fresh CDF, the score fold forms the per-point scores.
     * Returns the vector {psi_i : i in foldScore}.
     */
    private double[] scoreRotation(double[][] x, double[] y, double[][] xz,
                                   int[] foldTrain, int[] foldDir, int[] foldScore,
                                   long[] s, int K) {
        // Thresholds = K quantiles of Y on the TRAIN fold only, at p_k = (k + 0.5)/K.
        double[] yTr = gather(y, foldTrain);
        double[] yTrSorted = yTr.clone();
        Arrays.sort(yTrSorted);
        double[] thr = new double[K];
        for (int k = 0; k < K; k++) thr[k] = quantileType7(yTrSorted, (k + 0.5) / K);

        // p_cdf = P(Y<=t | X) ;  q_cdf = P(Y<=t | X,Z)   on the train fold
        Cdf pCdf = fitCdf(rows(x, foldTrain), yTr, thr, s[0]);
        Cdf qCdf = fitCdf(rows(xz, foldTrain), yTr, thr, s[1]);

        // witness g = (q - p)/max(p(1-p), varFloor) on dir (target) and score (score) folds
        double[][] gDir = witness(pCdf.eval(rows(x, foldDir)), qCdf.eval(rows(xz, foldDir)));
        double[][] gSc = witness(pCdf.eval(rows(x, foldScore)), qCdf.eval(rows(xz, foldScore)));

        // m_hat[:,k] = E[g_k | X], per-threshold squared-error regressor fit on dir, predicted on score.
        double[][] xDir = rows(x, foldDir), xSc = rows(x, foldScore);
        BinMapper bmDir = BinMapper.fit(xDir, maxBins);
        int[][] binXDir = bmDir.transform(xDir);
        double[][] mSc = new double[foldScore.length][K];
        for (int k = 0; k < K; k++) {
            double[] target = column(gDir, k);
            GBRegressor reg = new GBRegressor(this).fit(binXDir, bmDir, target, s[3 + k]);
            double[] pred = reg.predict(xSc);
            for (int i = 0; i < foldScore.length; i++) mSc[i][k] = pred[i];
        }

        // e_cdf = fresh P(Y<=t | X) on the dir fold (disjoint from p_cdf on the train fold)
        Cdf eCdf = fitCdf(xDir, gather(y, foldDir), thr, s[2]);

        // resid = 1{Y_s <= thr} - e_cdf(x_s)      (note: <=, vs strict < in the training bins)
        double[][] eSc = eCdf.eval(xSc);
        double[] ySc = gather(y, foldScore);
        double[][] resid = new double[foldScore.length][K];
        for (int i = 0; i < foldScore.length; i++)
            for (int k = 0; k < K; k++)
                resid[i][k] = (ySc[i] <= thr[k] ? 1.0 : 0.0) - eSc[i][k];

        // psi_i = mean_k (g - m)(1{Y<=t} - e)
        double[] psi = new double[foldScore.length];
        for (int i = 0; i < foldScore.length; i++) {
            double acc = 0;
            for (int k = 0; k < K; k++) acc += (gSc[i][k] - mSc[i][k]) * resid[i][k];
            psi[i] = acc / K;
        }
        return psi;
    }

    // ---- witness ---------------------------------------------------------- //
    private double[][] witness(double[][] pm, double[][] qm) {
        int n = pm.length, K = pm[0].length;
        double[][] g = new double[n][K];
        for (int i = 0; i < n; i++)
            for (int k = 0; k < K; k++) {
                double pp = pm[i][k];
                double v = Math.max(pp * (1.0 - pp), varFloor);
                g[i][k] = (qm[i][k] - pp) / v;
            }
        return g;
    }

    private Cdf fitCdf(double[][] feat, double[] y, double[] thr, long modelSeed) {
        final int K = thr.length;
        final double clip = cdfClip;
        int[] bins = new int[y.length];
        int mn = Integer.MAX_VALUE, mx = Integer.MIN_VALUE;
        for (int i = 0; i < y.length; i++) {
            bins[i] = searchsortedLeft(thr, y[i]);         // #{t_k < y}  in {0,...,K}
            mn = Math.min(mn, bins[i]);
            mx = Math.max(mx, bins[i]);
        }
        if (mn == mx) {                                    // all fold-Y in one bin: constant CDF
            final double[] cst = new double[K];
            for (int j = 0; j < K; j++)
                cst[j] = clamp(j >= bins[0] ? 1.0 : 0.0, clip, 1.0 - clip);
            return f -> {
                double[][] out = new double[f.length][K];
                for (int i = 0; i < f.length; i++) System.arraycopy(cst, 0, out[i], 0, K);
                return out;
            };
        }
        BinMapper bm = BinMapper.fit(feat, maxBins);
        int[][] binned = bm.transform(feat);
        GBClassifier clf;
        try {
            clf = new GBClassifier(this).fit(binned, bm, bins, K, modelSeed, true);
        } catch (StratifyException e) {                     // a bin too small to stratify -> es off
            clf = new GBClassifier(this).fit(binned, bm, bins, K, modelSeed, false);
        }
        final GBClassifier model = clf;
        final int[] classes = clf.classes;                 // ascending subset of {0..K}
        return f -> {
            double[][] proba = model.predictProba(f);       // (n, C) in `classes` order
            double[][] out = new double[f.length][K];
            for (int i = 0; i < f.length; i++) {
                double[] full = new double[K + 1];
                for (int c = 0; c < classes.length; c++) full[classes[c]] = proba[i][c];
                double acc = 0;
                for (int j = 0; j < K; j++) {               // cumsum, drop last col, clip
                    acc += full[j];
                    out[i][j] = clamp(acc, clip, 1.0 - clip);
                }
            }
            return out;
        };
    }

    // ===================================================================== //
    //  Conditional CDF  (port of cord.py _cdf / _fit_cdf)                     //
    // ===================================================================== //
    interface Cdf {
        double[][] eval(double[][] f);
    }

    /**
     * Represents the result of a statistical hypothesis test, encapsulating the p-value,
     * test statistic, test status, sample size, and dimensionality of the conditioning variable.
     * This class provides methods to evaluate the hypothesis test outcome and to generate
     * a summary of the results.
     */
    public static final class Result {
        /**
         * The p-value representing the one-sided upper bound of the statistical test result.
         * Specifically, it is calculated as 1 - Phi(T), where Phi(T) is the cumulative
         * distribution function of the standard normal distribution at the test statistic T.
         * <p>
         * A p-value provides a measure of the strength of evidence against the null hypothesis.
         * Lower values indicate stronger evidence to reject the null hypothesis in favor of
         * the alternative hypothesis.
         */
        public final double pvalue;      // one-sided upper, 1 - Phi(T)
        /**
         * The test statistic for a statistical hypothesis test, commonly referred to as the studentized score T.
         * It represents the computed value of the test statistic used to determine if the null hypothesis can be rejected.
         * This value is often compared against critical values to draw conclusions about the hypothesis.
         */
        public final double statistic;   // studentized score T
        /**
         * Represents the status of the test result in a hypothesis testing context.
         * <p>
         * The status can take one of the following values:
         * <p>
         * - "ok": Indicates that the test result is valid and the computations were successful.
         * <p>
         * - "degenerate": Indicates a degenerate case where the test could not be performed properly,
         * such as a zero variance condition in the score.
         */
        public final String status;      // "ok" or "degenerate"
        /**
         * Represents the sample size used in the statistical test.
         * This denotes the number of observations or data points included in the analysis.
         */
        public final int n;
        /**
         * Represents the dimensionality of the conditioning variable X in a statistical
         * hypothesis test. This value indicates the number of components or features
         * within the variable X that are conditioned upon in the test, providing
         * context for interpreting the test results.
         * <p>
         * Typically, higher-dimensional variables may increase the complexity of the
         * test and impact the statistical properties of the result.
         */
        public final int dimX;

        /**
         * Constructs a Result object that stores the details of a statistical test result.
         *
         * @param pvalue    The p-value of the test, representing the one-sided upper bound, 1 - Phi(T).
         * @param statistic The test statistic value (studentized score T).
         * @param status    The status of the test result, which can be "ok" or "degenerate".
         * @param n         The sample size used in the test.
         * @param dimX      The dimensionality of the conditioning variable X.
         */
        Result(double pvalue, double statistic, String status, int n, int dimX) {
            this.pvalue = pvalue;
            this.statistic = statistic;
            this.status = status;
            this.n = n;
            this.dimX = dimX;
        }

        /**
         * Determines whether to reject the null hypothesis based on the given significance level.
         *
         * @param a The significance level threshold for rejecting the null hypothesis.
         * @return {@code true} if the result's status is "ok" and the p-value is less than the given threshold;
         * {@code false} otherwise.
         */
        public boolean reject(double a) {
            return status.equals("ok") && pvalue < a;
        }

        /**
         * Returns a string representation of the result, including the null hypothesis,
         * test statistic, p-value, and status verdict.
         *
         * @return A string summarizing the hypothesis test results.
         * If the status is "ok", the string includes the test statistic and p-value.
         * Otherwise, it indicates a "degenerate" case with zero score variance.
         */
        @Override
        public String toString() {
            String verdict = "degenerate (zero score variance)";
            if (status.equals("ok"))
                verdict = String.format("T = %.4f   p = %.4g", statistic, pvalue);
            return "CORD  H0: Y _||_ Z | X   [" + verdict + "]";
        }
    }

    static final class StratifyException extends RuntimeException {
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

        int binOf(int f, double v) {
            return searchsortedLeft(thr[f], v);
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
     * A single boosted tree (leaf-wise / best-first). Leaf values include the shrinkage.
     */
    static final class TreeNode {
        boolean leaf = true;
        int feature = -1, binThr = -1;
        TreeNode left, right;
        double value;
    }

    /**
     * Node under construction, carrying its samples, stats, and best candidate split.
     */
    static final class BuildNode {
        int[] samples;
        double sumG, sumH;
        TreeNode node;
        double gain = 0.0;
        int feature = -1, binThr = -1;
    }

    /**
     * Multiclass (softmax) classifier: one tree per class per boosting iteration.
     */
    static final class GBClassifier {
        final CordEngine3 cfg;
        BinMapper bm;
        int[] classes;                 // ascending distinct labels
        double[] baseline;             // per class (log priors)
        List<List<TreeNode>> trees;    // per class

        GBClassifier(CordEngine3 cfg) {
            this.cfg = cfg;
        }

        GBClassifier fit(int[][] binned, BinMapper bm, int[] labels, int K, long modelSeed, boolean es) {
            this.bm = bm;
            int n = binned.length;
            classes = unique(labels);                 // sorted ascending
            int C = classes.length;
            Map<Integer, Integer> toIdx = new HashMap<>();
            for (int c = 0; c < C; c++) toIdx.put(classes[c], c);
            int[] cls = new int[n];
            for (int i = 0; i < n; i++) cls[i] = toIdx.get(labels[i]);

            // train / validation split
            int[] train, val;
            if (es) {
                int[][] tv = stratifiedSplit(cls, C, cfg.validationFraction, modelSeed);
                train = tv[0];
                val = tv[1];
            } else {
                train = iota(n);
                val = new int[0];
            }
            int nTr = train.length;

            // binned train / val matrices and mapped classes
            int[][] bTr = new int[nTr][];
            int[] clsTr = new int[nTr];
            for (int i = 0; i < nTr; i++) {
                bTr[i] = binned[train[i]];
                clsTr[i] = cls[train[i]];
            }
            int nVal = val.length;
            int[][] bVal = new int[nVal][];
            int[] clsVal = new int[nVal];
            for (int i = 0; i < nVal; i++) {
                bVal[i] = binned[val[i]];
                clsVal[i] = cls[val[i]];
            }

            // baseline = log class proportions on train
            int[] cnt = new int[C];
            for (int t : clsTr) cnt[t]++;
            baseline = new double[C];
            for (int c = 0; c < C; c++) baseline[c] = Math.log(Math.max(cnt[c], 1) / (double) nTr);

            trees = new ArrayList<>();
            for (int c = 0; c < C; c++) trees.add(new ArrayList<>());

            double[][] rawTr = new double[nTr][C];
            double[][] rawVal = new double[nVal][C];
            for (int i = 0; i < nTr; i++) System.arraycopy(baseline, 0, rawTr[i], 0, C);
            for (int i = 0; i < nVal; i++) System.arraycopy(baseline, 0, rawVal[i], 0, C);

            int[] rootSamples = iota(nTr);
            List<Double> scores = new ArrayList<>();
            for (int iter = 0; iter < cfg.numEstimators; iter++) {
                double[][] pTr = softmaxRows(rawTr);
                for (int c = 0; c < C; c++) {
                    double[] grad = new double[nTr], hess = new double[nTr];
                    for (int i = 0; i < nTr; i++) {
                        double pik = pTr[i][c];
                        grad[i] = pik - (clsTr[i] == c ? 1.0 : 0.0);
                        hess[i] = pik * (1.0 - pik);
                    }
                    TreeNode tree = growTree(bTr, bm.nBins, grad, hess, rootSamples,
                            cfg.maxLeafNodes, cfg.minSamplesLeaf, cfg.l2Regularization, cfg.learningRate);
                    trees.get(c).add(tree);
                    for (int i = 0; i < nTr; i++) rawTr[i][c] += predictTree(tree, bTr[i]);
                    for (int i = 0; i < nVal; i++) rawVal[i][c] += predictTree(tree, bVal[i]);
                }
                if (es) {
                    scores.add(-multiLogLoss(rawVal, clsVal));
                    if (shouldStop(scores, cfg.nIterNoChange, cfg.tol)) break;
                }
            }
            return this;
        }

        double[][] predictProba(double[][] F) {
            int[][] bF = bm.transform(F);
            int C = classes.length;
            double[][] raw = new double[F.length][C];
            for (int i = 0; i < F.length; i++) {
                System.arraycopy(baseline, 0, raw[i], 0, C);
                for (int c = 0; c < C; c++)
                    for (TreeNode t : trees.get(c)) raw[i][c] += predictTree(t, bF[i]);
            }
            return softmaxRows(raw);
        }
    }

    /**
     * Squared-error regressor for m_hat = E[g|X].
     */
    static final class GBRegressor {
        final CordEngine3 cfg;
        BinMapper bm;
        double baseline;
        List<TreeNode> trees;

        GBRegressor(CordEngine3 cfg) {
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
