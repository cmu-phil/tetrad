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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;

public class CordEngine4 implements CordEngine {

    // ===================================================================== //
    //  Configuration                                                         //
    // ===================================================================== //
    public double alpha = 0.05;            // significance level for the decision
    public int numThresholds = 9;          // K  (cordNumThresholds)
    public int numEstimators = 300;        // max_iter (cordNumEstimators)
    public double learningRate = 0.1;      // (cordLearningRate)
    public int maxLeafNodes = 31;          // (cordMaxLeafNodes)
    public long seed = 0L;                 // base seed; split + nuisance + bootstrap

    // --- new in engine 4 -------------------------------------------------- //
    public int numFolds = 5;               // M >= 3; M = 3 reproduces engine 3's roles
    public int numRepeats = 5;             // S repeated cross-fit partitions (averaged)
    public int numBootstrap = 300;         // B multiplier draws; 0 => normal fallback
    public String multiplier = "mammen";   // "mammen" | "rademacher"
    public String combine = "minp";        // "minp" | "mean" | "max"
    public double glsRidge = 0.05;         // ridge on Sigma_hat, as fraction of avg diag
    public double varFloorFrac = 0.25;     // adaptive floor: frac * q_k (1 - q_k)
    public double varFloorMin = 5e-3;      // absolute floor
    public int minFoldSize = 30;           // shrink M if folds would be smaller

    // Hard-coded to match the reference HGB configuration.
    public boolean earlyStopping = true;
    public double validationFraction = 0.15;
    public double cdfClip = 1e-3;
    public int minSamplesLeaf = 20;
    public double l2Regularization = 0.0;
    public int maxBins = 255;
    public int nIterNoChange = 10;
    public double tol = 1e-7;

    public CordEngine4() {}

    // ===================================================================== //
    //  Result                                                                //
    // ===================================================================== //
    public static final class Result {
        public final double pvalue;        // reported p (per `combine`; default calibrated min-p)
        public final double statistic;     // GLS mean-type statistic T_mean
        public final double statisticMax;  // max-type statistic T_max
        public final double pMean;         // p for T_mean (bootstrap, or normal if B = 0)
        public final double pMax;          // p for T_max  (bootstrap; NaN if B = 0)
        public final String status;        // "ok" or "degenerate"
        public final int n;
        public final int dimX;
        Result(double pvalue, double statistic, double statisticMax,
               double pMean, double pMax, String status, int n, int dimX) {
            this.pvalue = pvalue; this.statistic = statistic; this.statisticMax = statisticMax;
            this.pMean = pMean; this.pMax = pMax;
            this.status = status; this.n = n; this.dimX = dimX;
        }
        public boolean reject(double a) { return status.equals("ok") && pvalue < a; }
        @Override public String toString() {
            String verdict = "degenerate (zero score variance)";
            if (status.equals("ok"))
                verdict = String.format("T_mean = %.4f  T_max = %.4f   p = %.4g", statistic, statisticMax, pvalue);
            return "CORD4  H0: Y _||_ Z | X   [" + verdict + "]";
        }
    }

    // ===================================================================== //
    //  Public API : test(x, y, z)                                            //
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
                int[] fDir   = folds[(r + 1) % M];
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

    /** One role assignment: train folds fit the p/q CDFs; the dir fold fits the
     *  centering regression m_hat and a fresh CDF e; the score fold forms the
     *  per-point, per-threshold scores psi_{ik} = (g_k - m_k)(1{Y<=t_k} - e_k).
     *  Returns the (|score| x K) score matrix (no threshold averaging here). */
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
        double[][] gDir = witness(pCdf.eval(rows(x, foldDir)),   qCdf.eval(rows(xz, foldDir)),   floorK);
        double[][] gSc  = witness(pCdf.eval(rows(x, foldScore)), qCdf.eval(rows(xz, foldScore)), floorK);

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
        if (w == null) { w = new double[K]; Arrays.fill(w, 1.0 / K); }   // GLS fallback: equal weights

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
            for (int i = 0; i < n; i++) { double v = xi[i] * uc[i]; s1 += v; s2 += v * v; }
            double mu = s1 / n;
            double var = (s2 - n * mu * mu) / (n - 1);
            tmB[b] = var > 0 ? Math.sqrt(n) * mu / Math.sqrt(var) : Double.NEGATIVE_INFINITY;
            // max-type (same xi -> joint null)
            Arrays.fill(tmpK, 0.0); Arrays.fill(tmp2K, 0.0);
            for (int i = 0; i < n; i++) {
                double xii = xi[i];
                double[] ci = c[i];
                for (int k = 0; k < K; k++) { double v = xii * ci[k]; tmpK[k] += v; tmp2K[k] += v * v; }
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
        double pMax  = (1.0 + countGE(txB, tMax))  / (B + 1.0);

        // min-p combination, calibrated on the same joint draws (single-loop Westfall-Young)
        double[] tmSorted = tmB.clone(); Arrays.sort(tmSorted);
        double[] txSorted = txB.clone(); Arrays.sort(txSorted);
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

    // ===================================================================== //
    //  Conditional CDF: K cumulative binary GB fits + PAV monotonization     //
    // ===================================================================== //
    interface Cdf { double[][] eval(double[][] f); }

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
            for (int i = 0; i < n; i++) { lab[i] = (y[i] <= thr[k]) ? 1 : 0; pos += lab[i]; }
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
    //  Histogram gradient-boosted trees (unchanged core; binary logistic     //
    //  classifier replaces the multiclass softmax)                           //
    // ===================================================================== //

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

        int[][] transform(double[][] X) {
            int n = X.length, p = X[0].length;
            int[][] out = new int[n][p];
            for (int i = 0; i < n; i++)
                for (int f = 0; f < p; f++) out[i][f] = searchsortedLeft(thr[f], X[i][f]);
            return out;
        }
    }

    /** A single boosted tree (leaf-wise / best-first). Leaf values include shrinkage. */
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

    static final class BuildNode {
        int[] samples; double sumG, sumH; TreeNode node;
        double gain = 0.0; int feature = -1, binThr = -1;
    }

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
                if (rc < minLeaf) break;
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

    /** Binary logistic gradient-boosted classifier for one cumulative target
     *  1{Y <= t_k}. Uses all fold rows; only two classes exist, so the only
     *  stratification hazard is a singleton class, handled by disabling early
     *  stopping for that threshold. */
    static final class GBBinary {
        final CordEngine4 cfg;
        double baseline;                 // log-odds of prevalence
        List<TreeNode> trees;
        GBBinary(CordEngine4 cfg) { this.cfg = cfg; }

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
                train = tv[0]; val = tv[1];
            } else {
                train = iota(n); val = new int[0];
            }
            int nTr = train.length, nVal = val.length;
            int[][] bTr = new int[nTr][]; int[] yTr = new int[nTr];
            for (int i = 0; i < nTr; i++) { bTr[i] = binned[train[i]]; yTr[i] = lab[train[i]]; }
            int[][] bVal = new int[nVal][]; int[] yVal = new int[nVal];
            for (int i = 0; i < nVal; i++) { bVal[i] = binned[val[i]]; yVal[i] = lab[val[i]]; }

            int posTr = 0;
            for (int v : yTr) posTr += v;
            double prev = clamp(posTr / (double) nTr, 1e-6, 1.0 - 1e-6);
            baseline = Math.log(prev / (1.0 - prev));

            trees = new ArrayList<>();
            double[] rawTr = new double[nTr]; Arrays.fill(rawTr, baseline);
            double[] rawVal = new double[nVal]; Arrays.fill(rawVal, baseline);
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

    /** Squared-error regressor for m_hat = E[g|X].  Unchanged from engine 3. */
    static final class GBRegressor {
        final CordEngine4 cfg;
        BinMapper bm;
        double baseline;
        List<TreeNode> trees;
        GBRegressor(CordEngine4 cfg) { this.cfg = cfg; }

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
                    double mse = 0;
                    for (int i = 0; i < nVal; i++) { double d = rawVal[i] - yVal[i]; mse += d * d; }
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
        return new int[][]{ toIntArray(trainL), toIntArray(valL) };
    }

    // ===================================================================== //
    //  Numeric helpers                                                       //
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

    /** np.searchsorted(arr, v, side="left"): count of arr[i] STRICTLY LESS than v. */
    static int searchsortedLeft(double[] arr, double v) {
        int lo = 0, hi = arr.length;
        while (lo < hi) { int mid = (lo + hi) >>> 1; if (arr[mid] < v) lo = mid + 1; else hi = mid; }
        return lo;
    }

    /** np.array_split(perm, M): first (n % M) chunks get one extra element. */
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

    /** Union of all folds except indices a and b, in fold order. */
    static int[] unionExcept(int[][] folds, int a, int b) {
        int total = 0;
        for (int j = 0; j < folds.length; j++) if (j != a && j != b) total += folds[j].length;
        int[] out = new int[total];
        int pos = 0;
        for (int j = 0; j < folds.length; j++)
            if (j != a && j != b) { System.arraycopy(folds[j], 0, out, pos, folds[j].length); pos += folds[j].length; }
        return out;
    }

    /** Pool-adjacent-violators: in-place least-squares nondecreasing projection. */
    static void pavNondecreasing(double[] v) {
        int K = v.length;
        double[] vals = new double[K];
        int[] cnt = new int[K];
        int m = 0;
        for (double x0 : v) {
            vals[m] = x0; cnt[m] = 1; m++;
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

    /** Solve A x = b for SPD A via Cholesky; returns null if not SPD. */
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

    /** Mammen two-point multiplier: mean 0, variance 1, third moment 1. */
    static double mammenWeight(Random r) {
        double sqrt5 = Math.sqrt(5.0);
        double pNeg = (sqrt5 + 1.0) / (2.0 * sqrt5);
        return r.nextDouble() < pNeg ? (1.0 - sqrt5) / 2.0 : (1.0 + sqrt5) / 2.0;
    }

    /** One-sided upper p-value  1 - Phi(t) = 0.5*erfc(t/sqrt2). */
    static double normSf(double t) { return 0.5 * erfc(t / Math.sqrt(2.0)); }

    static double erfc(double x) {
        double z = Math.abs(x);
        double s = 1.0 / (1.0 + 0.5 * z);
        double ans = s * Math.exp(-z * z - 1.26551223 + s * (1.00002368 + s * (0.37409196
                + s * (0.09678418 + s * (-0.18628806 + s * (0.27886807 + s * (-1.13520398
                + s * (1.48851587 + s * (-0.82215223 + s * 0.17087277)))))))));
        return x >= 0.0 ? ans : 2.0 - ans;
    }

    static double sigmoid(double x) { return 1.0 / (1.0 + Math.exp(-x)); }

    static int[] permutation(int n, Random rng) {           // Fisher-Yates
        int[] a = iota(n);
        for (int i = n - 1; i > 0; i--) { int j = rng.nextInt(i + 1); int tmp = a[i]; a[i] = a[j]; a[j] = tmp; }
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
        while (lo < hi) { int mid = (lo + hi) >>> 1; if (sortedAsc[mid] < t) lo = mid + 1; else hi = mid; }
        return sortedAsc.length - lo;
    }

    // ---- small array utilities ------------------------------------------- //
    static int[] iota(int n) { int[] a = new int[n]; for (int i = 0; i < n; i++) a[i] = i; return a; }
    static double clamp(double v, double lo, double hi) { return v < lo ? lo : (v > hi ? hi : v); }
    static double mean(double[] a) { double s = 0; for (double v : a) s += v; return a.length == 0 ? 0 : s / a.length; }
    static double sampleSd(double[] a, double m) {
        if (a.length < 2) return 0.0;
        double s = 0;
        for (double v : a) { double d = v - m; s += d * d; }
        return Math.sqrt(s / (a.length - 1));
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
    static void shuffle(List<Integer> list, Random rng) {
        for (int i = list.size() - 1; i > 0; i--) { int j = rng.nextInt(i + 1); int t = list.get(i); list.set(i, list.get(j)); list.set(j, t); }
    }
    static int[] toIntArray(List<Integer> l) { int[] a = new int[l.size()]; for (int i = 0; i < a.length; i++) a[i] = l.get(i); return a; }

    // ===================================================================== //
    //  main : disabled in the Tetrad build (wire through IndTestCordEric4).   //
    // ===================================================================== //
    public static void main(String[] args) {
        // Self-test / data-file harness omitted in the Tetrad copy.
    }
}
