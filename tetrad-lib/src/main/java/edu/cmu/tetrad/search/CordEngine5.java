package edu.cmu.tetrad.search;
/*
 * CordEngine5.java -- CORD (Orthogonal Rank-Score Conditional Independence Test)
 * for H0: Y _||_ Z | X, revision 5.
 *
 * Engine 5 is to engine 2 what engine 4 is to engine 3: it carries the same
 * nuisance-quality and calibration upgrades, but it AGGREGATES AT THE LEVEL OF
 * P-VALUES rather than pooling scores. This is the original CORD design --
 * each cross-fit rotation is studentized on its own honest score fold to
 * produce a valid rotation p-value, and the rotation p-values are combined by
 * a Meinshausen-Buhlmann quantile aggregation that is valid under ARBITRARY
 * dependence among the (same-partition) rotations. Engine 4 instead pools all
 * cross-fitted scores into one studentization; engine 5 keeps the rotations
 * statistically separate and combines their p-values.
 *
 * What is inherited from engine 4 (deltas vs. engine 2):
 *
 *  (A) M-FOLD ROTATIONS (default M = 5; M = 3 reproduces engine 2's roles).
 *      Rotation r uses score = fold[r], dir = fold[r+1], TRAIN = the other
 *      M-2 folds pooled. The conditional-CDF nuisances now see (M-2)/M of the
 *      data instead of 1/3, so each rotation p-value is better calibrated and
 *      more powerful. There are now M rotation p-values per partition instead
 *      of 3.
 *
 *  (B) REPEATED CROSS-FITTING (default S = 4 partitions). Each partition
 *      contributes M rotation p-values; all S*M are fed to the aggregator.
 *      More splits -> the quantile aggregation is more stable and the "seed
 *      lottery" (dependence of the reported p on the single random partition)
 *      is largely removed. This is exactly the Meinshausen-Buhlmann-Rit (2009)
 *      multi-split regime.
 *
 *  (C) PER-THRESHOLD SCORES + TWO COMBINATIONS, INSIDE EACH ROTATION. Engine 2
 *      collapsed the K thresholds by an unweighted mean before studentizing.
 *      Engine 5 keeps the score fold's n_s x K score matrix and forms, on that
 *      fold, a GLS mean-type statistic (w = (Sigma_hat + ridge)^{-1} 1) and a
 *      max-type statistic (max over per-threshold studentized scores). Their
 *      Westfall-Young min-p combination is the rotation p-value: near-best
 *      power against both diffuse and concentrated alternatives.
 *
 *  (D) STUDENTIZED MULTIPLIER BOOTSTRAP, INSIDE EACH ROTATION. psi is a product
 *      of residuals (skewed, heavy-tailed), so 1 - Phi(T) is anti-conservative
 *      in the upper tail at the small n_s of a single fold. Because each score
 *      fold's cross-fitted scores are (conditionally) independent, a Mammen
 *      multiplier bootstrap on that fold repairs the tail and supplies the
 *      joint null for the min-p combination. numBootstrap = 0 falls back to the
 *      one-sided-normal p on the GLS mean-type (engine-2-style rotation p).
 *
 *  (E) CUMULATIVE BINARY CDF MODEL. P(Y <= t_k | .) is fit as K binary
 *      gradient-boosted logistic models of 1{Y <= t_k} with a per-row
 *      pool-adjacent-violators pass for monotonicity in k, then clipping. This
 *      replaces engine 2's unordered multiclass softmax and removes the
 *      class-stratification fallback (StratifyException) entirely.
 *
 *  (F) ADAPTIVE PER-THRESHOLD VARIANCE FLOOR
 *      max(varFloorMin, varFloorFrac * q_k(1-q_k)), q_k = (k+0.5)/K, so the
 *      witness is not flattened at the outer thresholds where tail dependence
 *      lives once K grows.
 *
 *  (G) SAMPLE (n-1) STANDARD DEVIATIONS throughout.
 *
 * The aggregation itself is a knob (all valid rotation p-values; degenerate
 * rotations contribute p = 1, never toward rejection):
 *
 *   aggregation = "median"   (DEFAULT; the original CORD choice)
 *       min(1, 2 * median(p_b)). The Meinshausen-Buhlmann gamma = 1/2 quantile
 *       rule; exactly valid (super-uniform under H0) under arbitrary dependence.
 *       Reduces to engine 2's rule when S = 1, M = 3, numBootstrap = 0.
 *
 *   aggregation = "adaptive" (Meinshausen-Buhlmann-Rit 2009 adaptive quantile)
 *       min(1, (1 - log gammaMin) * inf_{gamma in [gammaMin,1)} Q(gamma)),
 *       Q(gamma) = min(1, quantile_gamma({p_b})/gamma). Also arbitrary-
 *       dependence valid; more powerful when only a fraction of the splits are
 *       informative, at the cost of the (1 - log gammaMin) ~ 4 search factor.
 *
 *   aggregation = "cauchy"   (ACAT / Cauchy combination, Liu & Xie 2020)
 *       T = mean_b tan((0.5 - p_b) pi); p = 0.5 - atan(T)/pi. Powerful and
 *       standard in GWAS, but its validity under dependence is asymptotic /
 *       tail-approximate rather than exact -- use when power matters more than
 *       a guaranteed finite-sample level.
 *
 * Validity note. Every rotation p-value is a valid (asymptotically super-
 * uniform) p-value: the score is Neyman-orthogonal, each nuisance is fit on
 * folds disjoint from the fold it is evaluated on, and the within-rotation
 * bootstrap conditions on that fold's honest cross-fitted scores. The "median"
 * and "adaptive" aggregators then preserve the level under arbitrary
 * dependence; "cauchy" is the powerful-but-approximate option.
 *
 * Compute note. Cost is about S*(M/3) x engine 2 (defaults ~7x), dominated by
 * tree fitting; the S*M bootstraps are cheap on top. numRepeats, numFolds and
 * numBootstrap are the dials for batch runs; S = 1, M = 3, B = 0,
 * aggregation = "median" approximates engine 2 while keeping (E), (F), (G).
 *
 * Build:  javac CordEngine5.java
 */

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;

public class CordEngine5 implements CordEngine {

    // ===================================================================== //
    //  Configuration                                                         //
    // ===================================================================== //
    public double alpha = 0.05;            // significance level for the decision
    public int numThresholds = 9;          // K  (cordNumThresholds)
    public int numEstimators = 300;        // max_iter (cordNumEstimators)
    public double learningRate = 0.1;      // (cordLearningRate)
    public int maxLeafNodes = 31;          // (cordMaxLeafNodes)
    public long seed = 0L;                 // base seed; split + nuisance + bootstrap

    // --- new in engine 5 -------------------------------------------------- //
    public int numFolds = 5;               // M >= 3; M = 3 reproduces engine 2's roles
    public int numRepeats = 4;             // S repeated cross-fit partitions
    public int numBootstrap = 999;         // B multiplier draws PER ROTATION; 0 => normal
    public String multiplier = "mammen";   // "mammen" | "rademacher"
    public String combine = "minp";        // per-rotation: "minp" | "mean" | "max"
    public String aggregation = "median";  // across rotations: "median" | "adaptive" | "cauchy"
    public double gamma = 0.5;             // fixed-gamma quantile for "median" (0.5 => 2*median)
    public double gammaMin = 0.05;         // lower search bound for "adaptive"
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

    public CordEngine5() {}

    // ===================================================================== //
    //  Result                                                                //
    // ===================================================================== //
    public static final class Result {
        public final double pvalue;        // aggregated one-sided p (per `aggregation`)
        public final double statistic;     // representative GLS mean-type T (median-p rotation)
        public final double statisticMax;  // representative max-type T (median-p rotation)
        public final int nRotations;       // # ok rotation p-values aggregated
        public final String status;        // "ok" or "degenerate"
        public final int n;
        public final int dimX;
        Result(double pvalue, double statistic, double statisticMax,
               int nRotations, String status, int n, int dimX) {
            this.pvalue = pvalue; this.statistic = statistic; this.statisticMax = statisticMax;
            this.nRotations = nRotations; this.status = status; this.n = n; this.dimX = dimX;
        }
        public boolean reject(double a) { return status.equals("ok") && pvalue < a; }
        @Override public String toString() {
            String verdict = "degenerate (zero score variance)";
            if (status.equals("ok"))
                verdict = String.format("T_mean = %.4f  T_max = %.4f   p = %.4g  (%d rotations)",
                        statistic, statisticMax, pvalue, nRotations);
            return "CORD5  H0: Y _||_ Z | X   [" + verdict + "]";
        }
    }

    /** One rotation's outcome on its honest score fold. */
    private static final class Rot {
        double pMean, pMax, pComb, tMean, tMax;
        boolean ok;
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
    //  CORD core: S partitions x M rotations -> S*M rotation p -> aggregate   //
    // ===================================================================== //
    private Result run(double[][] x, double[] y, double[][] z, long baseSeed) {
        final int n = y.length;
        final int K = numThresholds;
        final int p = x[0].length;
        final int S = Math.max(1, numRepeats);

        int M = Math.max(3, numFolds);
        while (M > 3 && n / M < minFoldSize) M--;
        if (n < 6) return new Result(Double.NaN, Double.NaN, Double.NaN, 0, "degenerate", n, p);

        double[][] xz = hstack(x, z);
        double[] qLevels = new double[K];
        for (int k = 0; k < K; k++) qLevels[k] = (k + 0.5) / K;
        double[] floorK = new double[K];
        for (int k = 0; k < K; k++)
            floorK[k] = Math.max(varFloorMin, varFloorFrac * qLevels[k] * (1.0 - qLevels[k]));

        List<Double> pAll = new ArrayList<>();     // aggregated field per `combine`
        List<Double> tMeanAll = new ArrayList<>(); // parallel, for representative T
        List<Double> tMaxAll = new ArrayList<>();
        int okCount = 0;

        for (int s = 0; s < S; s++) {
            Random rng = new Random(mix(baseSeed, s));
            int[] perm = permutation(n, rng);
            int[][] folds = arraySplit(perm, M);
            for (int r = 0; r < M; r++) {
                int[] fScore = folds[r];
                int[] fDir   = folds[(r + 1) % M];
                int[] fTrain = unionExcept(folds, r, (r + 1) % M);
                long[] sb = new long[4 + K];        // pCdf, qCdf, eCdf, bootstrap, K regressors
                for (int i = 0; i < sb.length; i++) sb[i] = rng.nextLong();
                Rot rot = scoreRotation(x, y, xz, fTrain, fDir, fScore, sb, K, floorK);
                double pRot;
                if (!rot.ok) {
                    pRot = 1.0;                     // degenerate rotation: never toward rejection
                } else {
                    okCount++;
                    pRot = "mean".equalsIgnoreCase(combine) ? rot.pMean
                            : "max".equalsIgnoreCase(combine)  ? rot.pMax
                            : rot.pComb;
                }
                pAll.add(pRot);
                tMeanAll.add(rot.ok ? rot.tMean : Double.NaN);
                tMaxAll.add(rot.ok ? rot.tMax : Double.NaN);
            }
        }

        if (okCount == 0)
            return new Result(Double.NaN, Double.NaN, Double.NaN, 0, "degenerate", n, p);

        double[] ps = toDouble(pAll);
        double pAgg = aggregate(ps);

        // Representative T: the rotation whose p is the median of the ok rotations.
        int rep = medianIndexOk(ps, tMeanAll);
        double tRepMean = rep >= 0 ? tMeanAll.get(rep) : Double.NaN;
        double tRepMax  = rep >= 0 ? tMaxAll.get(rep)  : Double.NaN;
        return new Result(pAgg, tRepMean, tRepMax, okCount, "ok", n, p);
    }

    /** One role assignment: train folds fit the p/q CDFs; the dir fold fits the
     *  centering regression m_hat and a fresh CDF e; the score fold forms the
     *  per-point, per-threshold scores and is studentized + calibrated into a
     *  single rotation p-value (mean / max / min-p). */
    private Rot scoreRotation(double[][] x, double[] y, double[][] xz,
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
            GBRegressor reg = new GBRegressor(this).fit(binXDir, bmDir, target, sb[4 + k]);
            double[] pred = reg.predict(xSc);
            for (int i = 0; i < foldScore.length; i++) mSc[i][k] = pred[i];
        }

        // e_cdf = fresh P(Y<=t | X) on the dir fold (disjoint from p_cdf's train data)
        Cdf eCdf = fitCdfCumulative(xDir, gather(y, foldDir), thr, sb[2]);
        double[][] eSc = eCdf.eval(xSc);

        double[] ySc = gather(y, foldScore);
        double[][] psi = new double[foldScore.length][K];
        for (int i = 0; i < foldScore.length; i++)
            for (int k = 0; k < K; k++) {
                double resid = (ySc[i] <= thr[k] ? 1.0 : 0.0) - eSc[i][k];
                psi[i][k] = (gSc[i][k] - mSc[i][k]) * resid;
            }

        return rotationP(psi, sb[3]);
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
    //  Per-rotation statistic + calibration (GLS mean, max, min-p bootstrap) //
    // ===================================================================== //
    private Rot rotationP(double[][] psi, long bootSeed) {
        Rot out = new Rot();
        final int nS = psi.length;
        final int K = psi[0].length;
        final int B = Math.max(0, numBootstrap);
        if (nS < 2) { out.ok = false; return out; }

        // per-threshold means / sds and the K x K sample covariance
        double[] mK = new double[K], sK = new double[K];
        for (int k = 0; k < K; k++) {
            double[] col = column(psi, k);
            mK[k] = mean(col);
            sK[k] = sampleSd(col, mK[k]);
        }
        double[][] cov = new double[K][K];
        for (int i = 0; i < nS; i++)
            for (int a = 0; a < K; a++) {
                double da = psi[i][a] - mK[a];
                for (int b = a; b < K; b++) cov[a][b] += da * (psi[i][b] - mK[b]);
            }
        double avgDiag = 0;
        for (int a = 0; a < K; a++)
            for (int b = a; b < K; b++) {
                cov[a][b] /= (nS - 1);
                cov[b][a] = cov[a][b];
                if (a == b) avgDiag += cov[a][a];
            }
        avgDiag /= K;
        if (!(avgDiag > 0)) { out.ok = false; return out; }

        double[][] sigmaR = new double[K][K];
        for (int a = 0; a < K; a++) {
            System.arraycopy(cov[a], 0, sigmaR[a], 0, K);
            sigmaR[a][a] += glsRidge * avgDiag;
        }
        double[] ones = new double[K];
        Arrays.fill(ones, 1.0);
        double[] w = cholSolve(sigmaR, ones);
        if (w == null) { w = new double[K]; Arrays.fill(w, 1.0 / K); }

        // GLS-combined scalar scores u_i = w' psi_i
        double[] u = new double[nS];
        for (int i = 0; i < nS; i++) {
            double acc = 0;
            for (int k = 0; k < K; k++) acc += w[k] * psi[i][k];
            u[i] = acc;
        }
        double uBar = mean(u), uSd = sampleSd(u, uBar);
        if (!(uSd > 0)) { out.ok = false; return out; }
        out.tMean = Math.sqrt(nS) * uBar / uSd;

        out.tMax = Double.NEGATIVE_INFINITY;
        for (int k = 0; k < K; k++)
            if (sK[k] > 0) out.tMax = Math.max(out.tMax, Math.sqrt(nS) * mK[k] / sK[k]);

        if (B == 0) {                       // engine-2-style: normal p on the mean-type
            out.pMean = normSf(out.tMean);
            out.pMax  = Double.NaN;
            out.pComb = out.pMean;
            out.ok = true;
            return out;
        }

        // ---- studentized multiplier bootstrap on this fold, joint (mean, max) ---- //
        double[] uc = new double[nS];
        for (int i = 0; i < nS; i++) uc[i] = u[i] - uBar;
        double[][] c = new double[nS][K];
        for (int i = 0; i < nS; i++)
            for (int k = 0; k < K; k++) c[i][k] = psi[i][k] - mK[k];

        Random brng = new Random(bootSeed);
        boolean mammen = !"rademacher".equalsIgnoreCase(multiplier);
        double[] tmB = new double[B], txB = new double[B];
        double[] xi = new double[nS];
        double[] tmpK = new double[K], tmp2K = new double[K];
        for (int b = 0; b < B; b++) {
            for (int i = 0; i < nS; i++)
                xi[i] = mammen ? mammenWeight(brng) : (brng.nextBoolean() ? 1.0 : -1.0);
            double s1 = 0, s2 = 0;
            for (int i = 0; i < nS; i++) { double v = xi[i] * uc[i]; s1 += v; s2 += v * v; }
            double mu = s1 / nS;
            double var = (s2 - nS * mu * mu) / (nS - 1);
            tmB[b] = var > 0 ? Math.sqrt(nS) * mu / Math.sqrt(var) : Double.NEGATIVE_INFINITY;

            Arrays.fill(tmpK, 0.0); Arrays.fill(tmp2K, 0.0);
            for (int i = 0; i < nS; i++) {
                double xii = xi[i]; double[] ci = c[i];
                for (int k = 0; k < K; k++) { double v = xii * ci[k]; tmpK[k] += v; tmp2K[k] += v * v; }
            }
            double best = Double.NEGATIVE_INFINITY;
            for (int k = 0; k < K; k++) {
                double muk = tmpK[k] / nS;
                double vark = (tmp2K[k] - nS * muk * muk) / (nS - 1);
                if (vark > 0) best = Math.max(best, Math.sqrt(nS) * muk / Math.sqrt(vark));
            }
            txB[b] = best;
        }

        out.pMean = (1.0 + countGE(tmB, out.tMean)) / (B + 1.0);
        out.pMax  = (1.0 + countGE(txB, out.tMax))  / (B + 1.0);

        double[] tmSorted = tmB.clone(); Arrays.sort(tmSorted);
        double[] txSorted = txB.clone(); Arrays.sort(txSorted);
        double minObs = Math.min(out.pMean, out.pMax);
        int hits = 0;
        for (int b = 0; b < B; b++) {
            double pmB = countGEsorted(tmSorted, tmB[b]) / (double) B;
            double pxB = countGEsorted(txSorted, txB[b]) / (double) B;
            if (Math.min(pmB, pxB) <= minObs) hits++;
        }
        out.pComb = (1.0 + hits) / (B + 1.0);
        out.ok = true;
        return out;
    }

    // ===================================================================== //
    //  P-value aggregation across rotations                                  //
    // ===================================================================== //
    private double aggregate(double[] ps) {
        if ("cauchy".equalsIgnoreCase(aggregation)) return acat(ps);
        if ("adaptive".equalsIgnoreCase(aggregation)) return mbAdaptive(ps, gammaMin);
        return mbFixed(ps, gamma);                              // "median" default
    }

    /** Meinshausen-Buhlmann fixed-gamma: min(1, quantile_gamma({p_b})/gamma).
     *  gamma = 1/2 => min(1, 2*median(p_b)). Valid under arbitrary dependence. */
    static double mbFixed(double[] ps, double gamma) {
        double[] c = ps.clone();
        Arrays.sort(c);
        double q = quantileType7(c, gamma);
        return Math.min(1.0, q / gamma);
    }

    /** Meinshausen-Buhlmann-Rit adaptive quantile:
     *  min(1, (1 - log gammaMin) * inf_{gamma in [gammaMin,1)} quantile_gamma({p})/gamma).
     *  quantile_gamma({p/gamma}) = quantile_gamma({p})/gamma, so sort once. */
    static double mbAdaptive(double[] ps, double gammaMin) {
        double[] c = ps.clone();
        Arrays.sort(c);
        double gmin = Math.max(1e-6, Math.min(gammaMin, 0.99));
        int grid = 200;
        double inf = Double.POSITIVE_INFINITY;
        for (int g = 0; g <= grid; g++) {
            double gam = gmin + (0.999 - gmin) * g / grid;
            double q = quantileType7(c, gam) / gam;
            if (q < inf) inf = q;
        }
        double factor = 1.0 - Math.log(gmin);
        return Math.min(1.0, factor * inf);
    }

    /** ACAT / Cauchy combination (Liu & Xie 2020), equal weights, with the
     *  small-p and large-T stabilizations. Powerful under dependence but only
     *  asymptotically / tail-approximately level. */
    static double acat(double[] ps) {
        int B = ps.length;
        double eps = 1e-15;
        double T = 0.0;
        for (double praw : ps) {
            double pp = clamp(praw, eps, 1.0 - eps);
            if (pp > 1e-8) T += Math.tan((0.5 - pp) * Math.PI);
            else           T += 1.0 / (pp * Math.PI);           // tan((0.5-p)pi) ~ 1/(p pi)
        }
        T /= B;
        if (T > 1e15) return 1.0 / (T * Math.PI);               // upper-tail stabilization
        return 0.5 - Math.atan(T) / Math.PI;
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
        final double[] constant = new double[K];
        for (int k = 0; k < K; k++) {
            int[] lab = new int[n];
            int pos = 0;
            for (int i = 0; i < n; i++) { lab[i] = (y[i] <= thr[k]) ? 1 : 0; pos += lab[i]; }
            if (pos == 0 || pos == n) {
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
                pavNondecreasing(row);
                for (int k = 0; k < K; k++) out[i][k] = clamp(row[k], clip, 1.0 - clip);
            }
            return out;
        };
    }

    // ===================================================================== //
    //  Histogram gradient-boosted trees                                      //
    // ===================================================================== //

    static final class BinMapper {
        final double[][] thr;
        final int[] nBins;
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
                if (uniq.length <= maxBins) {
                    e = new double[Math.max(0, uniq.length - 1)];
                    for (int i = 0; i + 1 < uniq.length; i++) e[i] = 0.5 * (uniq[i] + uniq[i + 1]);
                } else {
                    e = new double[maxBins - 1];
                    for (int i = 1; i < maxBins; i++) e[i - 1] = quantileType7(sorted, (double) i / maxBins);
                    e = unique(e);
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

    /** Binary logistic gradient-boosted classifier for one cumulative target 1{Y <= t_k}. */
    static final class GBBinary {
        final CordEngine5 cfg;
        double baseline;
        List<TreeNode> trees;
        GBBinary(CordEngine5 cfg) { this.cfg = cfg; }

        GBBinary fit(int[][] binned, BinMapper bm, int[] lab, long modelSeed) {
            int n = binned.length;
            Random rng = new Random(modelSeed);

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

    /** Squared-error regressor for m_hat = E[g|X]. */
    static final class GBRegressor {
        final CordEngine5 cfg;
        BinMapper bm;
        double baseline;
        List<TreeNode> trees;
        GBRegressor(CordEngine5 cfg) { this.cfg = cfg; }

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
        double reference = scores.get(scores.size() - ref) + tol;
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
            if (nVal >= idx.size()) nVal = idx.size() - 1;
            for (int i = 0; i < idx.size(); i++)
                (i < nVal ? valL : trainL).add(idx.get(i));
        }
        return new int[][]{ toIntArray(trainL), toIntArray(valL) };
    }

    // ===================================================================== //
    //  Numeric helpers                                                       //
    // ===================================================================== //

    static double quantileType7(double[] sorted, double q) {
        int N = sorted.length;
        if (N == 1) return sorted[0];
        double pos = q * (N - 1);
        int lo = (int) Math.floor(pos);
        double frac = pos - lo;
        if (lo >= N - 1) return sorted[N - 1];
        return sorted[lo] + frac * (sorted[lo + 1] - sorted[lo]);
    }

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

    static int[] permutation(int n, Random rng) {
        int[] a = iota(n);
        for (int i = n - 1; i > 0; i--) { int j = rng.nextInt(i + 1); int tmp = a[i]; a[i] = a[j]; a[j] = tmp; }
        return a;
    }

    static long mix(long seed, long stream) {
        long z = seed + 0x9E3779B97F4A7C15L * (stream + 0x632BE59BD9B4E019L);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    static int countGE(double[] a, double t) {
        int c = 0;
        for (double v : a) if (v >= t) c++;
        return c;
    }

    static int countGEsorted(double[] sortedAsc, double t) {
        int lo = 0, hi = sortedAsc.length;
        while (lo < hi) { int mid = (lo + hi) >>> 1; if (sortedAsc[mid] < t) lo = mid + 1; else hi = mid; }
        return sortedAsc.length - lo;
    }

    /** Index (into ps / tMean) of the rotation whose p is the median among ok rotations. */
    static int medianIndexOk(double[] ps, List<Double> tMean) {
        List<Integer> ok = new ArrayList<>();
        for (int i = 0; i < ps.length; i++) if (!Double.isNaN(tMean.get(i))) ok.add(i);
        if (ok.isEmpty()) return -1;
        ok.sort((a, b) -> Double.compare(ps[a], ps[b]));
        return ok.get(ok.size() / 2);
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
    static double[] toDouble(List<Double> l) { double[] a = new double[l.size()]; for (int i = 0; i < a.length; i++) a[i] = l.get(i); return a; }
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
    //  main : disabled in the Tetrad build (wire through IndTestCordEric5).   //
    // ===================================================================== //
    public static void main(String[] args) {
        // Self-test / data-file harness omitted in the Tetrad copy.
    }
}
