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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

public class CordEngine2 implements CordEngine {

    // ===================================================================== //
    //  Configuration  (the six Tetrad params + the hard-coded knobs)         //
    // ===================================================================== //
    public double alpha = 0.05;            // significance level for the decision
    public int numThresholds = 9;          // K  (cordNumThresholds)
    public int numEstimators = 300;        // max_iter (cordNumEstimators)
    public double learningRate = 0.1;      // (cordLearningRate)
    public int maxLeafNodes = 31;          // (cordMaxLeafNodes)
    public long seed = 0L;                 // (cordSeed); split + nuisance seeds

    // Hard-coded to match the Python reference exactly (not exposed as knobs).
    public boolean earlyStopping = true;
    public double validationFraction = 0.15;
    public double cdfClip = 1e-3;
    public double varFloor = 0.02;
    public int minSamplesLeaf = 20;
    public double l2Regularization = 0.0;
    public int maxBins = 255;
    public int nIterNoChange = 10;
    public double tol = 1e-7;

    public CordEngine2() {}

    // ===================================================================== //
    //  Result                                                                //
    // ===================================================================== //
    public static final class Result {
        public final double pvalue;      // one-sided upper, 1 - Phi(T)
        public final double statistic;   // studentized score T
        public final String status;      // "ok" or "degenerate"
        public final int n;
        public final int dimX;
        Result(double pvalue, double statistic, String status, int n, int dimX) {
            this.pvalue = pvalue; this.statistic = statistic;
            this.status = status; this.n = n; this.dimX = dimX;
        }
        public boolean reject(double a) { return status.equals("ok") && pvalue < a; }
        @Override public String toString() {
            String verdict = "degenerate (zero score variance)";
            if (status.equals("ok"))
                verdict = String.format("T = %.4f   p = %.4g", statistic, pvalue);
            return "CORD  H0: Y _||_ Z | X   [" + verdict + "]";
        }
    }

    // ===================================================================== //
    //  Public API : test(x, y, z)  (argument order mirrors cord.py fit)      //
    // ===================================================================== //

    /** z given as a single column. */
    public Result test(double[][] x, double[] y, double[] z) {
        double[][] zm = new double[z.length][1];
        for (int i = 0; i < z.length; i++) zm[i][0] = z[i];
        return test(x, y, zm);
    }

    /** Full test.  x:(n,p), y:(n,), z:(n,d). */
    public Result test(double[][] x, double[] y, double[][] z) {
        return run(x, y, z, seed);
    }

    /** Degenerate rotations count as p = 1 in the median aggregation (never toward rejection). */
    private static double safeP(Result r) { return r.status.equals("ok") ? r.pvalue : 1.0; }

    // ===================================================================== //
    //  CORD core                                                             //
    //  run(): three cyclic A/B/C role rotations of a SINGLE partition, each   //
    //  fold playing train/direction/score exactly once, aggregated into one   //
    //  valid p-value via min(1, 2*median(p1,p2,p3))  (Meinshausen & Buhlmann  //
    //  2009, gamma=1/2 quantile aggregation; see uptodate_paper.tex:261).     //
    //  scoreRotation(): one role assignment (port of cord.py run()).          //
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

        // Rotation r uses train=folds[r], dir=folds[r+1], score=folds[r+2] (mod 3), so each fold
        // plays each role exactly once, giving three p-values p1,p2,p3.
        Result[] rot = new Result[3];
        for (int r = 0; r < 3; r++) {
            int[] fTrain = folds[r];
            int[] fDir   = folds[(r + 1) % 3];
            int[] fScore = folds[(r + 2) % 3];
            // Fresh per-model seed block per rotation (block draw, indexed like the Python `s` vector).
            long[] s = new long[3 + K];
            for (int i = 0; i < s.length; i++) s[i] = rng.nextLong();
            rot[r] = scoreRotation(x, y, xz, fTrain, fDir, fScore, s, n, p, K);
        }

        // Meinshausen-Buhlmann gamma=1/2 aggregation: min(1, 2*median(p1,p2,p3)); valid under
        // arbitrary dependence among the (same-partition) rotations. Degenerate rotation -> p = 1.
        int okCount = 0;
        for (Result r0 : rot) if (r0.status.equals("ok")) okCount++;
        if (okCount == 0) return new Result(Double.NaN, Double.NaN, "degenerate", n, p);

        double[] ps = { safeP(rot[0]), safeP(rot[1]), safeP(rot[2]) };
        double pAgg = Math.min(1.0, 2.0 * median(ps));

        // Report the studentized T of the median-p rotation (fall back to the max ok T if that
        // rotation was degenerate, so an "ok" aggregate never carries a NaN statistic).
        Integer[] ord = { 0, 1, 2 };
        Arrays.sort(ord, (a, b) -> {
            int c = Double.compare(ps[a], ps[b]);
            return c != 0 ? c : Integer.compare(a, b);
        });
        double tRep = rot[ord[1]].statistic;
        if (Double.isNaN(tRep)) {
            double best = Double.NEGATIVE_INFINITY;
            for (Result r0 : rot) if (r0.status.equals("ok")) best = Math.max(best, r0.statistic);
            tRep = best;
        }
        return new Result(pAgg, tRep, "ok", n, p);
    }

    /** One A/B/C role assignment (port of cord.py run()): the train fold fits the CDFs, the dir
     *  fold fits the centering regression + a fresh CDF, the score fold forms the statistic. */
    private Result scoreRotation(double[][] x, double[] y, double[][] xz,
                                 int[] foldTrain, int[] foldDir, int[] foldScore,
                                 long[] s, int n, int p, int K) {
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
        double[][] gDir = witness(pCdf.eval(rows(x, foldDir)),   qCdf.eval(rows(xz, foldDir)));
        double[][] gSc  = witness(pCdf.eval(rows(x, foldScore)), qCdf.eval(rows(xz, foldScore)));

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

        double sd = popStd(psi);
        if (!(sd > 0.0))
            return new Result(Double.NaN, Double.NaN, "degenerate", n, p);
        double t = Math.sqrt(foldScore.length) * mean(psi) / sd;
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

    // ===================================================================== //
    //  Conditional CDF  (port of cord.py _cdf / _fit_cdf)                     //
    // ===================================================================== //
    interface Cdf { double[][] eval(double[][] f); }

    private Cdf fitCdf(double[][] feat, double[] y, double[] thr, long modelSeed) {
        final int K = thr.length;
        final double clip = cdfClip;
        int[] bins = new int[y.length];
        int mn = Integer.MAX_VALUE, mx = Integer.MIN_VALUE;
        for (int i = 0; i < y.length; i++) {
            bins[i] = searchsortedLeft(thr, y[i]);         // #{t_k < y}  in {0,...,K}
            mn = Math.min(mn, bins[i]); mx = Math.max(mx, bins[i]);
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
    //  Histogram gradient-boosted trees  (embedded, mirrors sklearn HGB)     //
    // ===================================================================== //

    static final class StratifyException extends RuntimeException {}

    /** Feature binner: per-feature quantile bin edges, <= maxBins bins. */
    static final class BinMapper {
        final double[][] thr;   // per feature, ascending edges
        final int[] nBins;      // per feature = thr[f].length + 1
        private BinMapper(double[][] thr, int[] nBins) { this.thr = thr; this.nBins = nBins; }

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

        int binOf(int f, double v) { return searchsortedLeft(thr[f], v); }

        int[][] transform(double[][] X) {
            int n = X.length, p = X[0].length;
            int[][] out = new int[n][p];
            for (int i = 0; i < n; i++)
                for (int f = 0; f < p; f++) out[i][f] = searchsortedLeft(thr[f], X[i][f]);
            return out;
        }
    }

    /** A single boosted tree (leaf-wise / best-first). Leaf values include the shrinkage. */
    static final class TreeNode {
        boolean leaf = true;
        int feature = -1, binThr = -1;
        TreeNode left, right;
        double value;
    }

    static double predictTree(TreeNode nd, int[] row) {
        while (!nd.leaf) nd = (row[nd.feature] <= nd.binThr) ? nd.left : nd.right;
        return nd.value;
    }

    /** Node under construction, carrying its samples, stats, and best candidate split. */
    static final class BuildNode {
        int[] samples; double sumG, sumH; TreeNode node;
        double gain = 0.0; int feature = -1, binThr = -1;
    }

    /** Grow one tree by best-first splitting on gradient/hessian histograms. */
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
            for (int idx : bn.samples) { if (binned[idx][f] <= tb) left[li++] = idx; else right[ri++] = idx; }
            TreeNode nd = bn.node;
            nd.leaf = false; nd.feature = f; nd.binThr = tb;
            BuildNode L = makeNode(left, binned, nBins, grad, hess, p, minLeaf, l2, lr);
            BuildNode R = makeNode(right, binned, nBins, grad, hess, p, minLeaf, l2, lr);
            nd.left = L.node; nd.right = R.node;
            leaves++;
            if (L.gain > 0) pq.add(L);
            if (R.gain > 0) pq.add(R);
        }
        return root.node;
    }

    static BuildNode makeNode(int[] samples, int[][] binned, int[] nBins, double[] grad, double[] hess,
                              int p, int minLeaf, double l2, double lr) {
        double sumG = 0, sumH = 0;
        for (int idx : samples) { sumG += grad[idx]; sumH += hess[idx]; }
        BuildNode bn = new BuildNode();
        bn.samples = samples; bn.sumG = sumG; bn.sumH = sumH;
        TreeNode nd = new TreeNode();
        nd.leaf = true;
        nd.value = lr * (-sumG / (sumH + l2 + 1e-12));
        bn.node = nd;

        int total = samples.length;
        double bestGain = 0; int bestF = -1, bestB = -1;
        for (int f = 0; f < p; f++) {
            int nb = nBins[f];
            double[] hg = new double[nb]; double[] hh = new double[nb]; int[] hc = new int[nb];
            for (int idx : samples) { int b = binned[idx][f]; hg[b] += grad[idx]; hh[b] += hess[idx]; hc[b]++; }
            double accG = 0, accH = 0; int accC = 0;
            for (int b = 0; b < nb - 1; b++) {
                accG += hg[b]; accH += hh[b]; accC += hc[b];
                if (accC < minLeaf) continue;
                int rc = total - accC;
                if (rc < minLeaf) break;                 // accC only grows => rc only shrinks
                double GR = sumG - accG, HR = sumH - accH;
                double gain = 0.5 * (accG * accG / (accH + l2 + 1e-12)
                                   + GR * GR / (HR + l2 + 1e-12)
                                   - sumG * sumG / (sumH + l2 + 1e-12));
                if (gain > bestGain) { bestGain = gain; bestF = f; bestB = b; }
            }
        }
        bn.gain = bestGain; bn.feature = bestF; bn.binThr = bestB;
        return bn;
    }

    /** Multiclass (softmax) classifier: one tree per class per boosting iteration. */
    static final class GBClassifier {
        final CordEngine2 cfg;
        BinMapper bm;
        int[] classes;                 // ascending distinct labels
        double[] baseline;             // per class (log priors)
        List<List<TreeNode>> trees;    // per class
        GBClassifier(CordEngine2 cfg) { this.cfg = cfg; }

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
                train = tv[0]; val = tv[1];
            } else {
                train = iota(n); val = new int[0];
            }
            int nTr = train.length;

            // binned train / val matrices and mapped classes
            int[][] bTr = new int[nTr][]; int[] clsTr = new int[nTr];
            for (int i = 0; i < nTr; i++) { bTr[i] = binned[train[i]]; clsTr[i] = cls[train[i]]; }
            int nVal = val.length;
            int[][] bVal = new int[nVal][]; int[] clsVal = new int[nVal];
            for (int i = 0; i < nVal; i++) { bVal[i] = binned[val[i]]; clsVal[i] = cls[val[i]]; }

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

    /** Squared-error regressor for m_hat = E[g|X]. */
    static final class GBRegressor {
        final CordEngine2 cfg;
        BinMapper bm;
        double baseline;
        List<TreeNode> trees;
        GBRegressor(CordEngine2 cfg) { this.cfg = cfg; }

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
                train = iota(n); val = new int[0];
            }
            int nTr = train.length, nVal = val.length;
            int[][] bTr = new int[nTr][]; double[] yTr = new double[nTr];
            for (int i = 0; i < nTr; i++) { bTr[i] = binned[train[i]]; yTr[i] = target[train[i]]; }
            int[][] bVal = new int[nVal][]; double[] yVal = new double[nVal];
            for (int i = 0; i < nVal; i++) { bVal[i] = binned[val[i]]; yVal[i] = target[val[i]]; }

            baseline = mean(yTr);
            trees = new ArrayList<>();
            double[] rawTr = new double[nTr]; Arrays.fill(rawTr, baseline);
            double[] rawVal = new double[nVal]; Arrays.fill(rawVal, baseline);
            int[] rootSamples = iota(nTr);
            List<Double> scores = new ArrayList<>();
            for (int iter = 0; iter < cfg.numEstimators; iter++) {
                double[] grad = new double[nTr], hess = new double[nTr];
                for (int i = 0; i < nTr; i++) { grad[i] = rawTr[i] - yTr[i]; hess[i] = 1.0; }
                TreeNode tree = growTree(bTr, bm.nBins, grad, hess, rootSamples,
                        cfg.maxLeafNodes, cfg.minSamplesLeaf, cfg.l2Regularization, cfg.learningRate);
                trees.add(tree);
                for (int i = 0; i < nTr; i++) rawTr[i] += predictTree(tree, bTr[i]);
                for (int i = 0; i < nVal; i++) rawVal[i] += predictTree(tree, bVal[i]);
                if (es) {
                    double mse = 0; for (int i = 0; i < nVal; i++) { double d = rawVal[i] - yVal[i]; mse += d * d; }
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
        return new int[][]{ toIntArray(trainL), toIntArray(valL) };
    }

    // ===================================================================== //
    //  Numeric helpers (match numpy/scipy semantics used by cord.py)         //
    // ===================================================================== //

    /** numpy np.quantile default: linear / Hyndman-Fan type 7.  `sorted` ascending. */
    static double quantileType7(double[] sorted, double q) {
        int N = sorted.length;
        if (N == 1) return sorted[0];
        double pos = q * (N - 1);
        int lo = (int) Math.floor(pos);
        double frac = pos - lo;
        if (lo >= N - 1) return sorted[N - 1];
        return sorted[lo] + frac * (sorted[lo + 1] - sorted[lo]);
    }

    /** Median via numpy quantile (type 7) on a defensive sorted clone; length 3 -> middle element. */
    static double median(double[] a) {
        double[] c = a.clone();
        Arrays.sort(c);
        return quantileType7(c, 0.5);
    }

    /** np.searchsorted(arr, v, side="left"): count of arr[i] STRICTLY LESS than v. arr ascending. */
    static int searchsortedLeft(double[] arr, double v) {
        int lo = 0, hi = arr.length;
        while (lo < hi) { int mid = (lo + hi) >>> 1; if (arr[mid] < v) lo = mid + 1; else hi = mid; }
        return lo;
    }

    /** np.array_split(perm, 3): first (n%3) chunks get one extra element. */
    static int[][] arraySplit3(int[] perm) {
        int n = perm.length, q = n / 3, r = n % 3;
        int sA = q + (r >= 1 ? 1 : 0), sB = q + (r >= 2 ? 1 : 0), sC = q;
        int[] A = Arrays.copyOfRange(perm, 0, sA);
        int[] B = Arrays.copyOfRange(perm, sA, sA + sB);
        int[] C = Arrays.copyOfRange(perm, sA + sB, sA + sB + sC);
        return new int[][]{A, B, C};
    }

    /** One-sided upper p-value  1 - Phi(t) = 0.5*erfc(t/sqrt2).  erfc via NR (frac err < 1.2e-7). */
    static double normSf(double t) { return 0.5 * erfc(t / Math.sqrt(2.0)); }

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
        for (int i = n - 1; i > 0; i--) { int j = rng.nextInt(i + 1); int tmp = a[i]; a[i] = a[j]; a[j] = tmp; }
        return a;
    }

    static double[][] softmaxRows(double[][] raw) {
        int n = raw.length, C = (n == 0 ? 0 : raw[0].length);
        double[][] out = new double[n][C];
        for (int i = 0; i < n; i++) {
            double mx = Double.NEGATIVE_INFINITY;
            for (int c = 0; c < C; c++) mx = Math.max(mx, raw[i][c]);
            double sum = 0;
            for (int c = 0; c < C; c++) { out[i][c] = Math.exp(raw[i][c] - mx); sum += out[i][c]; }
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
    static int[] iota(int n) { int[] a = new int[n]; for (int i = 0; i < n; i++) a[i] = i; return a; }
    static double clamp(double v, double lo, double hi) { return v < lo ? lo : (v > hi ? hi : v); }
    static double mean(double[] a) { double s = 0; for (double v : a) s += v; return a.length == 0 ? 0 : s / a.length; }
    static double popStd(double[] a) {
        double m = mean(a), s = 0;
        for (double v : a) { double d = v - m; s += d * d; }
        return Math.sqrt(s / a.length);
    }
    static double[] gather(double[] y, int[] idx) { double[] o = new double[idx.length]; for (int i = 0; i < idx.length; i++) o[i] = y[idx[i]]; return o; }
    static double[][] rows(double[][] m, int[] idx) { double[][] o = new double[idx.length][]; for (int i = 0; i < idx.length; i++) o[i] = m[idx[i]]; return o; }
    static double[] column(double[][] m, int k) { double[] o = new double[m.length]; for (int i = 0; i < m.length; i++) o[i] = m[i][k]; return o; }
    static double[][] hstack(double[][] a, double[][] b) {
        int n = a.length, pa = a[0].length, pb = b[0].length;
        double[][] o = new double[n][pa + pb];
        for (int i = 0; i < n; i++) { System.arraycopy(a[i], 0, o[i], 0, pa); System.arraycopy(b[i], 0, o[i], pa, pb); }
        return o;
    }
    static double[] unique(double[] sortedAsc) {
        if (sortedAsc.length == 0) return sortedAsc;
        double[] tmp = new double[sortedAsc.length]; int m = 0;
        for (double v : sortedAsc) if (m == 0 || v != tmp[m - 1]) tmp[m++] = v;
        return Arrays.copyOf(tmp, m);
    }
    static int[] unique(int[] a) {
        int[] c = a.clone(); Arrays.sort(c);
        int[] tmp = new int[c.length]; int m = 0;
        for (int v : c) if (m == 0 || v != tmp[m - 1]) tmp[m++] = v;
        return Arrays.copyOf(tmp, m);
    }
    static void shuffle(List<Integer> list, Random rng) {
        for (int i = list.size() - 1; i > 0; i--) { int j = rng.nextInt(i + 1); int t = list.get(i); list.set(i, list.get(j)); list.set(j, t); }
    }
    static int[] toIntArray(List<Integer> l) { int[] a = new int[l.size()]; for (int i = 0; i < a.length; i++) a[i] = l.get(i); return a; }

    // ===================================================================== //
    //  main : self-test (no args) or data-file mode (args)                   //
    // ===================================================================== //
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

        CordEngine2 cord = new CordEngine2();
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
        double[][] X0 = new double[n][p]; double[] Y0 = new double[n], Z0 = new double[n];
        genNull(new Random(42), X0, Y0, Z0);
        double t1 = new CordEngine2() {{ seed = 7; }}.test(X0, Y0, Z0).statistic;
        double t2 = new CordEngine2() {{ seed = 7; }}.test(X0, Y0, Z0).statistic;
        boolean deterministic = (Double.compare(t1, t2) == 0);
        System.out.printf("  [determinism] repeated run T1=%.6f  T2=%.6f  ->  %s%n%n",
                t1, t2, deterministic ? "identical" : "DIFFER");

        // (1) Single illustrative runs.
        System.out.println("  [demo] one null dataset and one higher-moment alternative:");
        double[][] Xd = new double[600][3]; double[] Yn = new double[600], Zn = new double[600];
        genNull(new Random(0), Xd, Yn, Zn);
        System.out.println("        null : " + new CordEngine2() {{ seed = 0; }}.test(Xd, Yn, Zn));
        double[] Ya = new double[600], Za = new double[600];
        genHigherMomentAlt(new Random(1), Xd, Ya, Za);
        System.out.println("        alt  : " + new CordEngine2() {{ seed = 0; }}.test(Xd, Ya, Za));
        System.out.println();

        // (2) Monte-Carlo rejection rates at alpha = 0.05.
        double alpha = 0.05;
        double nullRate  = monteCarlo(reps, n, p, alpha, Kind.NULL);
        double linPower  = monteCarlo(reps, n, p, alpha, Kind.LINEAR_ALT);
        double hmPower   = monteCarlo(reps, n, p, alpha, Kind.HIGHER_MOMENT_ALT);

        System.out.printf("  [size ] null rejection rate      = %.3f   (target ~ %.2f)%n", nullRate, alpha);
        System.out.printf("  [power] linear-alt rejection     = %.3f%n", linPower);
        System.out.printf("  [power] higher-moment-alt reject = %.3f   <- CORD's signature (mean/GCM tests miss this)%n%n", hmPower);

        // Only an upper bound: the min(1, 2*median) aggregation is deliberately conservative, so a
        // near-zero null rejection rate is expected and valid; the check guards anti-conservative inflation.
        boolean sizeOk  = nullRate <= 0.14;
        boolean powerOk = linPower >= 0.80;
        boolean pass = deterministic && sizeOk && powerOk;
        System.out.println("  checks: determinism=" + deterministic + "  size_ok=" + sizeOk
                + "  linear_power_ok=" + powerOk);
        System.out.println(pass ? "\n  RESULT: PASS" : "\n  RESULT: FAIL");
        if (!pass) System.exit(1);
    }

    enum Kind { NULL, LINEAR_ALT, HIGHER_MOMENT_ALT }

    static double monteCarlo(int reps, int n, int p, double alpha, Kind kind) {
        AtomicInteger rej = new AtomicInteger(0);
        IntStream.range(0, reps).parallel().forEach(r -> {
            Random rng = new Random(1000L * kind.ordinal() + r);
            double[][] X = new double[n][p]; double[] Y = new double[n], Z = new double[n];
            switch (kind) {
                case NULL:               genNull(rng, X, Y, Z); break;
                case LINEAR_ALT:         genLinearAlt(rng, X, Y, Z); break;
                case HIGHER_MOMENT_ALT:  genHigherMomentAlt(rng, X, Y, Z); break;
            }
            CordEngine2 cord = new CordEngine2();
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
            for (int j = 0; j < p; j++) { X[i][j] = rng.nextGaussian(); sSin += Math.sin(X[i][j]); sCos += Math.cos(X[i][j]); }
            Y[i] = sSin + rng.nextGaussian();
            Z[i] = sCos + rng.nextGaussian();
        }
    }
    // Linear alternative: a shared latent eta enters Y and Z in the mean  ->  cov(Y,Z|X) != 0.
    static void genLinearAlt(Random rng, double[][] X, double[] Y, double[] Z) {
        int n = X.length, p = X[0].length;
        for (int i = 0; i < n; i++) {
            double sSin = 0, sCos = 0;
            for (int j = 0; j < p; j++) { X[i][j] = rng.nextGaussian(); sSin += Math.sin(X[i][j]); sCos += Math.cos(X[i][j]); }
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
            for (int j = 0; j < p; j++) { X[i][j] = rng.nextGaussian(); sSin += Math.sin(X[i][j]); sCos += Math.cos(X[i][j]); }
            double eta = rng.nextGaussian();
            Z[i] = sCos + eta;
            Y[i] = sSin + (eta * eta - 1.0) + rng.nextGaussian();
        }
    }
}
