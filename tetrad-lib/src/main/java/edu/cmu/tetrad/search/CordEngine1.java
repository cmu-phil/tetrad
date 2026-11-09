package edu.cmu.tetrad.search;/*
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

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

/**
 * CordEngine1 is an implementation of a conditional independence testing engine
 * based on the CORD methodology. It provides functionality for running
 * independence tests with configurable parameters and utilities for
 * statistical computations.
 */
public class CordEngine1 implements CordEngine {

    // ===================================================================== //
    //  Configuration  (the six Tetrad params + the hard-coded knobs)         //
    // ===================================================================== //

    /**
     * Represents the significance level used for statistical decision-making.
     * <p>
     * This variable determines the threshold at which the null hypothesis
     * is rejected in hypothesis testing. A common default value of 0.05
     * indicates a 5% probability of rejecting the null hypothesis when it is true,
     * often referred to as the Type I error rate.
     */
    public double alpha = 0.05;            // significance level for the decision
    /**
     * Represents the number of thresholds used in the CORD algorithm for splitting
     * or binning data. This variable is typically associated with determining the
     * granularity of thresholds applied in histogram-based gradient boosting or
     * tree-based methods within the {@code CordEngine1} class.
     * <p>
     * The value of this variable directly influences the model's ability to
     * capture fine-grained patterns in the input data. A higher number of thresholds
     * allows for more detailed splits, potentially improving accuracy, while a
     * lower number facilitates computational efficiency.
     * <p>
     * Default value: 9.
     */
    public int numThresholds = 9;          // K  (cordNumThresholds)
    /**
     * Specifies the number of estimators (or iterations) used in the model training process.
     * The variable determines the maximum number of iterations for optimization or
     * the number of trees in an ensemble method, depending on the algorithm's configuration.
     * <p>
     * A higher value generally allows the model to capture more complex patterns in the data
     * but may increase the risk of overfitting or lead to higher computational cost.
     * The default value for this variable is set to 300.
     */
    public int numEstimators = 300;        // max_iter (cordNumEstimators)
    /**
     * The learning rate used by the {@code CordEngine1} class for optimization
     * processes such as gradient updates.
     * <p>
     * A lower value results in more cautious updates, which can improve
     * convergence stability but may require more iterations. A higher value
     * may speed up convergence but risks overshooting the optimal solution.
     * <p>
     * Default value: 0.1
     */
    public double learningRate = 0.1;      // (cordLearningRate)
    /**
     * Specifies the maximum number of leaf nodes permitted in a decision tree.
     * <p>
     * This variable is primarily used to control the complexity of the tree during
     * its construction. Limiting the number of leaf nodes helps in reducing overfitting
     * and ensures that the tree generalizes better to unseen data. A higher value allows
     * the tree to grow deeper and potentially capture more nuances in the data, whereas
     * a lower value encourages a simpler model.
     */
    public int maxLeafNodes = 31;          // (cordMaxLeafNodes)
    /**
     * Represents a seed value used for generating random numbers and controlling
     * deterministic behavior in computations or data-processing tasks.
     * <p>
     * The {@code seed} variable is composed of a combination of constituent seed types
     * (cordSeed, split seed, and nuisance seed) to maintain reproducibility and
     * synchronize processes requiring randomness. This ensures consistency across
     * multiple executions with the same seed value.
     */
    public long seed = 0L;                 // (cordSeed); split + nuisance seeds
    /**
     * Indicates whether the conditional independence test should be performed
     * in symmetric mode or not.
     * <p>
     * When set to {@code false}, the test operates in non-symmetric mode,
     * using a faithful single orientation of the variables. When set to
     * {@code true}, the test runs in symmetric mode, evaluating the independence
     * in both orientations (e.g., testing Z ⫫ Y | X and Y ⫫ Z | X) and combining
     * the results.
     */
    public boolean symmetric = false;      // (cordSymmetric) off = faithful single orientation

    // Hard-coded to match the Python reference exactly (not exposed as knobs).

    /**
     * A flag to indicate whether the early stopping mechanism is enabled
     * during iterative training processes.
     * <p>
     * Early stopping is used to terminate training when the model's performance
     * on a validation set stops improving, which helps prevent overfitting and
     * reduces computation time.
     */
    public boolean earlyStopping = true;
    /**
     * Represents the fraction of the dataset to be used for validation during training.
     * This value determines the proportion of the dataset that is set aside to evaluate
     * the model's performance while preventing overfitting. The remaining portion of
     * the dataset is used for training.
     * <p>
     * The value should typically be a floating-point number between 0.0 (no validation
     * set) and 1.0 (entire dataset used for validation), with commonly used values
     * being in the range 0.1 to 0.3.
     * <p>
     * A lower value allocates more data for training, potentially improving model fitting
     * but reducing the robustness of the validation-based evaluation. Conversely, a higher
     * value results in better validation at the cost of reduced training data.
     */
    public double validationFraction = 0.15;
    /**
     * A small positive constant used to clip values within the cumulative distribution function (CDF)
     * for numerical stability and to prevent extreme values from causing computational issues.
     * <p>
     * For example, when evaluating probabilities in machine learning or statistical models,
     * very small or very large values in the CDF might lead to instability in the calculations.
     * This variable ensures that the probabilities remain within a safe numerical range.
     */
    public double cdfClip = 1e-3;
    /**
     * Represents a lower threshold value for variance within the class.
     * This variable is typically used to prevent calculations from resulting in
     * excessively small variance values, which could lead to numerical instability.
     * It serves as a safeguard in computations involving gradients or loss functions.
     */
    public double varFloor = 0.02;
    /**
     * The minimum number of samples required to be in a leaf node when growing a decision tree.
     * This parameter acts as a stopping criterion to control the minimum size of terminal nodes
     * (leaf nodes) in the tree, ensuring that splits resulting in very small leaf nodes are avoided.
     * <p>
     * A higher value for {@code minSamplesLeaf} increases the regularization effect, potentially
     * improving generalization at the cost of potentially underfitting the data.
     */
    public int minSamplesLeaf = 20;
    /**
     * A regularization parameter used in the model to penalize large weights during
     * optimization. This parameter helps prevent overfitting by controlling the
     * magnitude of the coefficients in the model. Larger values of this parameter
     * impose stronger regularization and result in smaller coefficients.
     * <p>
     * The L2 regularization term is added to the loss function as the squared
     * magnitude of the weights, scaled by this parameter.
     */
    public double l2Regularization = 0.0;
    /**
     * Represents the maximum number of bins that can be used for data discretization
     * in processing or modeling tasks within the {@code CordEngine1} class.
     * <p>
     * This variable typically determines the granularity of data quantization,
     * where larger values provide finer-grained splits during histogram-based
     * computations. It is commonly used in the context of decision tree training,
     * feature binning, or other algorithms that require partitioning of continuous
     * data into discrete intervals.
     * <p>
     * The default value of 255 reflects a practical trade-off between computational
     * efficiency and modeling performance for many use cases.
     */
    public int maxBins = 255;
    /**
     * Represents the number of consecutive training iterations without improvement
     * in the monitored metric before early stopping is triggered.
     * <p>
     * This variable is used as part of the early-stopping criterion to determine
     * when training should halt in order to prevent overfitting and save resources.
     * When the specified number of iterations is reached without achieving any
     * significant improvement, the optimization process will stop.
     */
    public int nIterNoChange = 10;
    /**
     * The tolerance level for early stopping during iterative algorithms.
     * <p>
     * This variable defines the threshold for improvement in the monitored metric
     * that determines whether the training process should terminate early. If the
     * change in the metric is less than this value for a certain number of
     * consecutive iterations, the algorithm will stop to prevent overfitting
     * or unnecessary computation.
     * <p>
     * Typical use cases include machine learning contexts where early stopping
     * is employed to optimize model training, ensuring efficient convergence
     * without over-training.
     */
    public double tol = 1e-7;

    /**
     * Default constructor for the {@code CordEngine1} class.
     * <p>
     * Initializes an instance of the {@code CordEngine1} class without any specific configurations or parameters.
     * This constructor is intended to create a default state of the class, allowing the user to set properties or call methods thereafter.
     */
    public CordEngine1() {
    }

    private static double safeP(Result r) {
        return r.status.equals("ok") ? r.pvalue : 1.0;
    }

    // ===================================================================== //
    //  Public API : test(x, y, z)  (argument order mirrors cord.py fit)      //
    // ===================================================================== //

    private static double[] columnMeans1D(double[][] z) {          // collapse z:(n,d) -> (n,) for the swapped role
        double[] o = new double[z.length];
        for (int i = 0; i < z.length; i++) {
            double s = 0;
            for (double v : z[i]) s += v;
            o[i] = s;
        }
        return o;
    }

    private static double[][] z1(double[] y) {
        double[][] o = new double[y.length][1];
        for (int i = 0; i < y.length; i++) o[i][0] = y[i];
        return o;
    }

    static double predictTree(TreeNode nd, int[] row) {
        while (!nd.leaf) nd = (row[nd.feature] <= nd.binThr) ? nd.left : nd.right;
        return nd.value;
    }

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

    // ===================================================================== //
    //  Histogram gradient-boosted trees  (embedded, mirrors sklearn HGB)     //
    // ===================================================================== //

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

    // ===================================================================== //
    //  Numeric helpers (match numpy/scipy semantics used by cord.py)         //
    // ===================================================================== //

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

    // ===================================================================== //
    //  main : self-test (no args) or data-file mode (args)                   //
    // ===================================================================== //

    /**
     * The main entry point for the CordEngine1 program. It determines the mode of execution
     * based on the command-line arguments provided. If no arguments are supplied, it runs in
     * self-test mode. Otherwise, it operates in data-processing mode.
     *
     * @param args An array of strings representing the command-line arguments. If the array
     *             is empty, the program runs in self-test mode. If arguments are provided,
     *             they are passed to the data-processing mode for execution.
     * @throws IOException If an I/O error occurs during execution in data-processing mode.
     */
    public static void main(String[] args) throws IOException {
        if (args.length == 0) selfTest();
        else dataMode(args);
    }

    // -------- data mode ---------------------------------------------------- //
    static void dataMode(String[] args) throws IOException {
        if (args.length < 4) {
            System.err.println("usage: java Cord <file.tsv> <yCol> <zCol> <xCol1[,xCol2,...]> [more x cols]");
            System.err.println("  columns may be header names (e.g. X1) or 0-based indices");
            System.exit(2);
        }
        String file = args[0];
        List<String> header = new ArrayList<>();
        List<double[]> rows = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line = br.readLine();
            for (String tok : line.trim().split("\\s+")) header.add(tok);
            int p = header.size();
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split("\\s+");
                double[] row = new double[p];
                for (int j = 0; j < p; j++) row[j] = Double.parseDouble(parts[j]);
                rows.add(row);
            }
        }
        Map<String, Integer> nameToIdx = new HashMap<>();
        for (int j = 0; j < header.size(); j++) nameToIdx.put(header.get(j), j);
        int n = rows.size();
        int yc = resolveCol(args[1], nameToIdx);
        int zc = resolveCol(args[2], nameToIdx);
        List<Integer> xcs = new ArrayList<>();
        for (int a = 3; a < args.length; a++)
            for (String tok : args[a].split(","))
                if (!tok.isEmpty()) xcs.add(resolveCol(tok, nameToIdx));

        double[] y = new double[n];
        double[] z = new double[n];
        double[][] x = new double[n][xcs.size()];
        for (int i = 0; i < n; i++) {
            y[i] = rows.get(i)[yc];
            z[i] = rows.get(i)[zc];
            for (int j = 0; j < xcs.size(); j++) x[i][j] = rows.get(i)[xcs.get(j)];
        }

        CordEngine1 cord = new CordEngine1();
        Result r = cord.test(x, y, z);
        System.out.println("CORD conditional independence test");
        System.out.printf("    file = %s   n = %d%n", file, n);
        System.out.printf("    Y = %s   Z = %s   X = %s%n", args[1], args[2], xcs);
        if (r.status.equals("ok")) {
            System.out.printf("    statistic T = %.4f      p-value = %.4g%n", r.statistic, r.pvalue);
            System.out.printf("    decision at alpha = %.3g: %s%n", cord.alpha,
                    r.reject(cord.alpha) ? "REJECT H0 (conditional dependence detected)" : "fail to reject H0");
        } else {
            System.out.println("    status: degenerate (zero score variance; p-value NaN)");
        }
    }

    static int resolveCol(String tok, Map<String, Integer> nameToIdx) {
        Integer idx = nameToIdx.get(tok);
        if (idx != null) return idx;
        return Integer.parseInt(tok);
    }

    // -------- self-test (Monte-Carlo size + power + determinism) ----------- //
    static void selfTest() {
        final int n = 300, p = 2, reps = 120;
        System.out.println("CORD self-test  (n=" + n + ", dim(X)=" + p + ", K=9, reps=" + reps + ")");
        System.out.println("  cores=" + Runtime.getRuntime().availableProcessors()
                + "  -- running, this takes a bit...\n");

        // (0) Determinism: same data + same seed => identical statistic.
        double[][] X0 = new double[n][p];
        double[] Y0 = new double[n], Z0 = new double[n];
        genNull(new Random(42), X0, Y0, Z0);
        double t1 = new CordEngine1() {{
            seed = 7;
        }}.test(X0, Y0, Z0).statistic;
        double t2 = new CordEngine1() {{
            seed = 7;
        }}.test(X0, Y0, Z0).statistic;
        boolean deterministic = (Double.compare(t1, t2) == 0);
        System.out.printf("  [determinism] repeated run T1=%.6f  T2=%.6f  ->  %s%n%n",
                t1, t2, deterministic ? "identical" : "DIFFER");

        // (1) Single illustrative runs.
        System.out.println("  [demo] one null dataset and one higher-moment alternative:");
        double[][] Xd = new double[600][3];
        double[] Yn = new double[600], Zn = new double[600];
        genNull(new Random(0), Xd, Yn, Zn);
        System.out.println("        null : " + new CordEngine1() {{
            seed = 0;
        }}.test(Xd, Yn, Zn));
        double[] Ya = new double[600], Za = new double[600];
        genHigherMomentAlt(new Random(1), Xd, Ya, Za);
        System.out.println("        alt  : " + new CordEngine1() {{
            seed = 0;
        }}.test(Xd, Ya, Za));
        System.out.println();

        // (2) Monte-Carlo rejection rates at alpha = 0.05.
        double alpha = 0.05;
        double nullRate = monteCarlo(reps, n, p, alpha, Kind.NULL);
        double linPower = monteCarlo(reps, n, p, alpha, Kind.LINEAR_ALT);
        double hmPower = monteCarlo(reps, n, p, alpha, Kind.HIGHER_MOMENT_ALT);

        System.out.printf("  [size ] null rejection rate      = %.3f   (target ~ %.2f)%n", nullRate, alpha);
        System.out.printf("  [power] linear-alt rejection     = %.3f%n", linPower);
        System.out.printf("  [power] higher-moment-alt reject = %.3f   <- CORD's signature (mean/GCM tests miss this)%n%n", hmPower);

        boolean sizeOk = nullRate >= 0.005 && nullRate <= 0.14;
        boolean powerOk = linPower >= 0.80;
        boolean pass = deterministic && sizeOk && powerOk;
        System.out.println("  checks: determinism=" + deterministic + "  size_ok=" + sizeOk
                + "  linear_power_ok=" + powerOk);
        System.out.println(pass ? "\n  RESULT: PASS" : "\n  RESULT: FAIL");
        if (!pass) System.exit(1);
    }

    static double monteCarlo(int reps, int n, int p, double alpha, Kind kind) {
        AtomicInteger rej = new AtomicInteger(0);
        IntStream.range(0, reps).parallel().forEach(r -> {
            Random rng = new Random(1000L * kind.ordinal() + r);
            double[][] X = new double[n][p];
            double[] Y = new double[n], Z = new double[n];
            switch (kind) {
                case NULL:
                    genNull(rng, X, Y, Z);
                    break;
                case LINEAR_ALT:
                    genLinearAlt(rng, X, Y, Z);
                    break;
                case HIGHER_MOMENT_ALT:
                    genHigherMomentAlt(rng, X, Y, Z);
                    break;
            }
            CordEngine1 cord = new CordEngine1();
            cord.seed = r;                        // per-rep deterministic
            Result res = cord.test(X, Y, Z);
            if (res.reject(alpha)) rej.incrementAndGet();
        });
        return rej.get() / (double) reps;
    }

    // ---- data-generating processes --------------------------------------- //
    // Null:  Y and Z share only the common driver X  ->  Y _||_ Z | X.
    static void genNull(Random rng, double[][] X, double[] Y, double[] Z) {
        int n = X.length, p = X[0].length;
        for (int i = 0; i < n; i++) {
            double sSin = 0, sCos = 0;
            for (int j = 0; j < p; j++) {
                X[i][j] = rng.nextGaussian();
                sSin += Math.sin(X[i][j]);
                sCos += Math.cos(X[i][j]);
            }
            Y[i] = sSin + rng.nextGaussian();
            Z[i] = sCos + rng.nextGaussian();
        }
    }

    // Linear alternative: a shared latent eta enters Y and Z in the mean  ->  cov(Y,Z|X) != 0.
    static void genLinearAlt(Random rng, double[][] X, double[] Y, double[] Z) {
        int n = X.length, p = X[0].length;
        for (int i = 0; i < n; i++) {
            double sSin = 0, sCos = 0;
            for (int j = 0; j < p; j++) {
                X[i][j] = rng.nextGaussian();
                sSin += Math.sin(X[i][j]);
                sCos += Math.cos(X[i][j]);
            }
            double eta = rng.nextGaussian();
            Z[i] = sCos + eta;
            Y[i] = sSin + eta + rng.nextGaussian();
        }
    }

    // Higher-moment alternative: cov(Y,Z|X)=0 but Y depends on eta^2  ->  only an omnibus test sees it.
    static void genHigherMomentAlt(Random rng, double[][] X, double[] Y, double[] Z) {
        int n = X.length, p = X[0].length;
        for (int i = 0; i < n; i++) {
            double sSin = 0, sCos = 0;
            for (int j = 0; j < p; j++) {
                X[i][j] = rng.nextGaussian();
                sSin += Math.sin(X[i][j]);
                sCos += Math.cos(X[i][j]);
            }
            double eta = rng.nextGaussian();
            Z[i] = sCos + eta;
            Y[i] = sSin + (eta * eta - 1.0) + rng.nextGaussian();
        }
    }

    /**
     * Conducts a conditional independence test to evaluate the hypothesis
     * H0: Y is independent of Z given X. Simplifies the test by converting
     * the Z array into a 2D column matrix and delegates to the full test method.
     *
     * @param x A 2D array representing the predictor variables (n by p), where n is
     *          the number of samples and p is the number of predictors.
     * @param y A 1D array representing the outcome variable (n), where n is
     *          the number of samples.
     * @param z A 1D array representing the conditioning variable (n), where n is
     *          the number of samples.
     * @return A {@code Result} object encapsulating the test statistic, p-value,
     * status, sample size, and dimensionality of the conditioning set.
     */
    public Result test(double[][] x, double[] y, double[] z) {
        double[][] zm = new double[z.length][1];
        for (int i = 0; i < z.length; i++) zm[i][0] = z[i];
        return test(x, y, zm);
    }

    /**
     * Performs a conditional independence test to evaluate the hypothesis
     * H0: Y is independent of Z given X. This method supports both symmetric
     * and non-symmetric modes.
     * <p>
     * In symmetric mode, the test is conducted in both orientations:
     * testing Z ⫫ Y | X and Y ⫫ Z | X. The results are then combined using
     * Bonferroni correction to account for multiple testing. The method returns
     * the p-value, test statistic, and additional status information.
     *
     * @param x A 2D array representing the predictor variables (n by p), where n
     *          is the number of samples and p is the number of predictors.
     * @param y A 1D array representing the outcome variable (n), where n is the
     *          number of samples.
     * @param z A 2D array representing the conditional variables (n by q), where n
     *          is the number of samples and q is the number of conditioning variables.
     * @return A {@code Result} object encapsulating the test statistic, p-value,
     * status, sample size, and dimensionality of the conditioning set.
     */
    public Result test(double[][] x, double[] y, double[][] z) {
        if (!symmetric) return run(x, y, z, seed);
        // Symmetric mode (opt-in): Bonferroni over both orientations.
        Result xy = run(x, y, z, seed);
        Result yx = run(x, columnMeans1D(z), z1(y), seed);   // test Z _||_ Y | X
        double p = Math.min(1.0, 2.0 * Math.min(safeP(xy), safeP(yx)));
        double t = Math.max(xy.statistic, yx.statistic);
        String st = (xy.status.equals("ok") || yx.status.equals("ok")) ? "ok" : "degenerate";
        return new Result(st.equals("ok") ? p : Double.NaN, t, st, y.length, x[0].length);
    }

    // ===================================================================== //
    //  CORD core  (port of cord.py run(), lines 58-82)                       //
    // ===================================================================== //
    private Result run(double[][] x, double[] y, double[][] z, long theSeed) {
        final int n = y.length;
        final int K = numThresholds;
        final int p = x[0].length;
        Random rng = new Random(theSeed);

        // A/B/C split via permutation + np.array_split(., 3).
        int[] perm = permutation(n, rng);
        int[][] folds = arraySplit3(perm);
        int[] A = folds[0], B = folds[1], C = folds[2];

        // Per-model seeds (block draw, indexed like the Python `s` vector).
        long[] s = new long[3 + K];
        for (int i = 0; i < s.length; i++) s[i] = rng.nextLong();

        // Thresholds = K quantiles of Y on fold A, at p_k = (k + 0.5)/K.
        double[] yA = gather(y, A);
        double[] yAsorted = yA.clone();
        Arrays.sort(yAsorted);
        double[] thr = new double[K];
        for (int k = 0; k < K; k++) thr[k] = quantileType7(yAsorted, (k + 0.5) / K);

        // xz = [x | z]
        double[][] xz = hstack(x, z);

        // p_cdf = P(Y<=t | X) on A ;  q_cdf = P(Y<=t | X,Z) on A
        Cdf pCdf = fitCdf(rows(x, A), yA, thr, s[0]);
        Cdf qCdf = fitCdf(rows(xz, A), yA, thr, s[1]);

        // witness g = (q - p)/max(p(1-p), varFloor) on B (target) and C (score)
        double[][] gB = witness(pCdf.eval(rows(x, B)), qCdf.eval(rows(xz, B)));
        double[][] gC = witness(pCdf.eval(rows(x, C)), qCdf.eval(rows(xz, C)));

        // m_hat[:,k] = E[g_k | X], per-threshold squared-error regressor fit on B, predicted on C.
        double[][] xB = rows(x, B), xC = rows(x, C);
        BinMapper bmB = BinMapper.fit(xB, maxBins);
        int[][] binXB = bmB.transform(xB);
        double[][] mC = new double[C.length][K];
        for (int k = 0; k < K; k++) {
            double[] target = column(gB, k);
            GBRegressor reg = new GBRegressor(this).fit(binXB, bmB, target, s[3 + k]);
            double[] pred = reg.predict(xC);
            for (int i = 0; i < C.length; i++) mC[i][k] = pred[i];
        }

        // e_cdf = fresh P(Y<=t | X) on B (disjoint from p_cdf on A)
        Cdf eCdf = fitCdf(xB, gather(y, B), thr, s[2]);

        // resid = 1{Y_c <= thr} - e_cdf(x_c)      (note: <=, vs strict < in the training bins)
        double[][] eC = eCdf.eval(xC);
        double[] yC = gather(y, C);
        double[][] resid = new double[C.length][K];
        for (int i = 0; i < C.length; i++)
            for (int k = 0; k < K; k++)
                resid[i][k] = (yC[i] <= thr[k] ? 1.0 : 0.0) - eC[i][k];

        // psi_i = mean_k (g - m)(1{Y<=t} - e)
        double[] psi = new double[C.length];
        for (int i = 0; i < C.length; i++) {
            double acc = 0;
            for (int k = 0; k < K; k++) acc += (gC[i][k] - mC[i][k]) * resid[i][k];
            psi[i] = acc / K;
        }

        double sd = popStd(psi);
        if (!(sd > 0.0))
            return new Result(Double.NaN, Double.NaN, "degenerate", n, p);
        double t = Math.sqrt(C.length) * mean(psi) / sd;
        return new Result(normSf(t), t, "ok", n, p);
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

    enum Kind {NULL, LINEAR_ALT, HIGHER_MOMENT_ALT}

    // ===================================================================== //
    //  Conditional CDF  (port of cord.py _cdf / _fit_cdf)                     //
    // ===================================================================== //
    interface Cdf {
        double[][] eval(double[][] f);
    }

    /**
     * Encapsulates the results of a conditional independence test for the hypothesis
     * H0: Y is independent of Z given X.
     * <p>
     * This class provides information about the test statistics, p-value,
     * sample size, and the dimensionality of the conditioning set.
     */
    public static final class Result {
        /**
         * Represents the p-value of a statistical hypothesis test, which quantifies
         * the probability of obtaining a test statistic at least as extreme as the
         * one observed, under the null hypothesis.
         *
         * <ul>
         * <li>This field corresponds specifically to a one-sided upper probability: 1 - Phi(T),
         * where T is the test statistic and Phi(T) is the cumulative distribution function
         * for the standard normal distribution.</li>
         * <li>A smaller p-value indicates stronger evidence against the null hypothesis.</li>
         * </ul>
         */
        public final double pvalue;      // one-sided upper, 1 - Phi(T)
        /**
         * Represents the test statistic (studentized score T) used in a conditional
         * independence test. This statistic is calculated under the null hypothesis
         * that Y is independent of Z given X. Its value determines the strength of
         * the evidence against the null hypothesis, with associated interpretation
         * depending on the p-value derived from it.
         */
        public final double statistic;   // studentized score T
        /**
         * Represents the status of the test result, indicating
         * whether the outcome is valid or degenerate.
         * <p>
         * Possible values:
         * <p>
         * - "ok": The test result is valid and the procedure has run successfully.
         * <p>
         * - "degenerate": The test result is invalid due to degeneracy in the test conditions.
         */
        public final String status;      // "ok" or "degenerate"
        /**
         * Represents the sample size used in a statistical test.
         * This variable is utilized to determine the number of observations
         * or data points in the analysis, and plays a critical role in the
         * validity and interpretation of statistical results.
         */
        public final int n;
        /**
         * Represents the dimensionality of the conditioning set X used in a conditional
         * independence test. This integer value specifies the number of variables or
         * dimensions included in the set X that acts as a conditioning factor in the
         * hypothesis H0: Y is independent of Z given X.
         */
        public final int dimX;

        /**
         * Constructs an instance of the Result class encapsulating the results of
         * a conditional independence test.
         *
         * @param pvalue    the p-value of the hypothesis test; corresponds to a one-sided
         *                  upper probability (1 - Phi(T)).
         * @param statistic the test statistic (studentized score T).
         * @param status    the status of the test, indicating whether the result is valid
         *                  ("ok") or degenerate ("degenerate").
         * @param n         the sample size used in the test.
         * @param dimX      the dimensionality of the conditioning set X.
         */
        Result(double pvalue, double statistic, String status, int n, int dimX) {
            this.pvalue = pvalue;
            this.statistic = statistic;
            this.status = status;
            this.n = n;
            this.dimX = dimX;
        }

        /**
         * Evaluates whether to reject the null hypothesis H0: Y is independent of Z given X.
         * The method compares the p-value against a specified significance level and checks
         * the status of the test result.
         *
         * @param a the significance level (alpha) to compare against the p-value.
         * @return {@code true} if the null hypothesis should be rejected (i.e., the test status is "ok"
         * and the p-value is less than {@code a}); {@code false} otherwise.
         */
        public boolean reject(double a) {
            return status.equals("ok") && pvalue < a;
        }

        /**
         * Returns a string representation of the hypothesis test result.
         * The result includes the hypothesis being tested, the statistical
         * test outcome, and its interpretation.
         *
         * @return a string summarizing the independence test, including the test statistic,
         * p-value, or an indication of a degenerate case.
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
        final CordEngine1 cfg;
        BinMapper bm;
        int[] classes;                 // ascending distinct labels
        double[] baseline;             // per class (log priors)
        List<List<TreeNode>> trees;    // per class

        GBClassifier(CordEngine1 cfg) {
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
        final CordEngine1 cfg;
        BinMapper bm;
        double baseline;
        List<TreeNode> trees;

        GBRegressor(CordEngine1 cfg) {
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
