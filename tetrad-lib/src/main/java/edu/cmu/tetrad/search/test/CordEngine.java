package edu.cmu.tetrad.search.test;

import java.util.*;

/**
 * Standalone validation harness for the CORD port. No Tetrad dependencies: operates on
 * double[][] / double[]. Contains the histogram gradient-boosted regression tree learner
 * (Gbrt), the threshold-CDF estimator, and the CORD score. main() reproduces the Python
 * example (null vs. higher-moment alternative) and a small calibration/power sweep.
 */
public class CordEngine {

    /**
     * Constructs a new CordEngine instance.
     */
    public CordEngine() {
    }

    // ============================== Gbrt =================================== //

    /** Histogram gradient-boosted regression trees, least-squares loss, leaf-wise growth. */
    static final class Gbrt {
        final int nEstimators, maxLeafNodes, minSamplesLeaf, maxBins, nIterNoChange;
        final double learningRate, validationFraction, tol;
        final boolean earlyStopping;
        final long seed;

        double baseline;
        double[][] binEdges;          // per feature: sorted split thresholds
        List<Tree> trees = new ArrayList<>();
        int nFeatures;

        Gbrt(int nEstimators, double learningRate, int maxLeafNodes, int minSamplesLeaf,
             int maxBins, boolean earlyStopping, double validationFraction, int nIterNoChange,
             double tol, long seed) {
            this.nEstimators = nEstimators; this.learningRate = learningRate;
            this.maxLeafNodes = maxLeafNodes; this.minSamplesLeaf = minSamplesLeaf;
            this.maxBins = maxBins; this.earlyStopping = earlyStopping;
            this.validationFraction = validationFraction; this.nIterNoChange = nIterNoChange;
            this.tol = tol; this.seed = seed;
        }

        Gbrt fit(double[][] x, double[] y) {
            int n = y.length;
            this.nFeatures = (n == 0) ? 0 : x[0].length;

            if (nFeatures == 0) {                 // no conditioning features -> constant mean
                baseline = mean(y);
                return this;
            }

            // Bin edges (quantile split thresholds) per feature.
            binEdges = new double[nFeatures][];
            for (int j = 0; j < nFeatures; j++) binEdges[j] = edges(column(x, j), maxBins);
            int[][] binned = new int[n][nFeatures];
            for (int i = 0; i < n; i++)
                for (int j = 0; j < nFeatures; j++) binned[i][j] = binOf(x[i][j], binEdges[j]);

            // Optional early-stopping split.
            int[] trainIdx, valIdx;
            if (earlyStopping && n >= 20) {
                int[] perm = permutation(n, seed ^ 0x9E3779B97F4A7C15L);
                int nVal = Math.max(1, (int) Math.round(validationFraction * n));
                if (nVal >= n) nVal = n / 5;
                valIdx = Arrays.copyOfRange(perm, 0, nVal);
                trainIdx = Arrays.copyOfRange(perm, nVal, n);
            } else {
                trainIdx = iota(n); valIdx = new int[0];
            }

            baseline = mean(select(y, trainIdx));
            double[] predTr = fill(trainIdx.length, baseline);
            double[] predVal = fill(valIdx.length, baseline);
            double bestVal = valIdx.length > 0 ? mse(select(y, valIdx), predVal) : Double.NaN;
            int bestNTrees = 0, noImprove = 0;

            for (int it = 0; it < nEstimators; it++) {
                double[] resid = new double[trainIdx.length];
                for (int i = 0; i < trainIdx.length; i++) resid[i] = y[trainIdx[i]] - predTr[i];

                Tree tree = new Tree(maxLeafNodes, minSamplesLeaf);
                tree.fit(binned, trainIdx, resid, maxBinsPerFeature());
                trees.add(tree);

                for (int i = 0; i < trainIdx.length; i++)
                    predTr[i] += learningRate * tree.predict(binned[trainIdx[i]]);

                if (valIdx.length > 0) {
                    for (int i = 0; i < valIdx.length; i++)
                        predVal[i] += learningRate * tree.predict(binned[valIdx[i]]);
                    double v = mse(select(y, valIdx), predVal);
                    if (v < bestVal - tol) { bestVal = v; bestNTrees = trees.size(); noImprove = 0; }
                    else if (++noImprove >= nIterNoChange) break;
                }
            }
            if (valIdx.length > 0 && bestNTrees < trees.size())
                trees = new ArrayList<>(trees.subList(0, bestNTrees));
            return this;
        }

        double[] predict(double[][] x) {
            int n = x.length;
            double[] out = fill(n, baseline);
            if (nFeatures == 0 || trees.isEmpty()) return out;
            for (int i = 0; i < n; i++) {
                int[] b = new int[nFeatures];
                for (int j = 0; j < nFeatures; j++) b[j] = binOf(x[i][j], binEdges[j]);
                double acc = 0;
                for (Tree t : trees) acc += t.predict(b);
                out[i] += learningRate * acc;
            }
            return out;
        }

        private int[] maxBinsPerFeature() {
            int[] m = new int[nFeatures];
            for (int j = 0; j < nFeatures; j++) m[j] = binEdges[j].length + 1;
            return m;
        }
    }

    /** Single least-squares regression tree grown best-first over binned features. */
    static final class Tree {
        final int maxLeafNodes, minSamplesLeaf;
        int[] featOf; int[] thrOf; int[] leftOf; int[] rightOf; double[] valOf;
        int nNodes = 0;

        Tree(int maxLeafNodes, int minSamplesLeaf) {
            this.maxLeafNodes = maxLeafNodes; this.minSamplesLeaf = minSamplesLeaf;
            int cap = 2 * maxLeafNodes + 1;
            featOf = new int[cap]; thrOf = new int[cap];
            leftOf = new int[cap]; rightOf = new int[cap]; valOf = new double[cap];
            Arrays.fill(leftOf, -1); Arrays.fill(rightOf, -1); Arrays.fill(featOf, -1);
        }

        void fit(int[][] binned, int[] rows, double[] resid, int[] nBins) {
            // resid is indexed parallel to rows.
            int root = newNode(sum(resid) / resid.length);
            PriorityQueue<Cand> pq = new PriorityQueue<>((a, b) -> Double.compare(b.gain, a.gain));
            Cand rc = bestSplit(binned, rows, resid, nBins);
            if (rc != null) { rc.node = root; pq.add(rc); }
            int leaves = 1;
            while (leaves < maxLeafNodes && !pq.isEmpty()) {
                Cand c = pq.poll();
                if (c.gain <= 0) break;
                // partition rows/resid of that candidate
                int nl = c.leftRows.length;
                double[] lr = new double[nl], rr = new double[c.rightRows.length];
                for (int i = 0; i < nl; i++) lr[i] = c.leftResid[i];
                for (int i = 0; i < rr.length; i++) rr[i] = c.rightResid[i];
                int lNode = newNode(sum(lr) / lr.length);
                int rNode = newNode(sum(rr) / rr.length);
                featOf[c.node] = c.feat; thrOf[c.node] = c.thr;
                leftOf[c.node] = lNode; rightOf[c.node] = rNode;
                leaves++;
                Cand lc = bestSplit(binned, c.leftRows, lr, nBins);
                if (lc != null) { lc.node = lNode; pq.add(lc); }
                Cand rcc = bestSplit(binned, c.rightRows, rr, nBins);
                if (rcc != null) { rcc.node = rNode; pq.add(rcc); }
            }
        }

        private int newNode(double val) {
            valOf[nNodes] = val; return nNodes++;
        }

        double predict(int[] bins) {
            int node = 0;
            while (featOf[node] != -1) {
                node = (bins[featOf[node]] <= thrOf[node]) ? leftOf[node] : rightOf[node];
            }
            return valOf[node];
        }

        /** Scan every feature's bin histogram; return the best gain split (or null). */
        private Cand bestSplit(int[][] binned, int[] rows, double[] resid, int[] nBins) {
            int n = rows.length;
            if (n < 2 * minSamplesLeaf) return null;
            double G = sum(resid);
            double parent = G * G / n;
            double bestGain = 0; int bestFeat = -1, bestThr = -1;
            int nFeat = nBins.length;
            for (int j = 0; j < nFeat; j++) {
                int nb = nBins[j];
                double[] sg = new double[nb]; int[] cnt = new int[nb];
                for (int r = 0; r < n; r++) { int b = binned[rows[r]][j]; sg[b] += resid[r]; cnt[b]++; }
                double gl = 0; int nl = 0;
                for (int b = 0; b < nb - 1; b++) {
                    gl += sg[b]; nl += cnt[b];
                    if (nl < minSamplesLeaf) continue;
                    int nr = n - nl; if (nr < minSamplesLeaf) break;
                    double gr = G - gl;
                    double gain = gl * gl / nl + gr * gr / nr - parent;
                    if (gain > bestGain) { bestGain = gain; bestFeat = j; bestThr = b; }
                }
            }
            if (bestFeat == -1) return null;
            // Materialize the partition for the chosen split.
            int nl = 0;
            for (int r = 0; r < n; r++) if (binned[rows[r]][bestFeat] <= bestThr) nl++;
            int[] lRows = new int[nl], rRows = new int[n - nl];
            double[] lRes = new double[nl], rRes = new double[n - nl];
            int li = 0, ri = 0;
            for (int r = 0; r < n; r++) {
                if (binned[rows[r]][bestFeat] <= bestThr) { lRows[li] = rows[r]; lRes[li++] = resid[r]; }
                else { rRows[ri] = rows[r]; rRes[ri++] = resid[r]; }
            }
            Cand c = new Cand();
            c.gain = bestGain; c.feat = bestFeat; c.thr = bestThr;
            c.leftRows = lRows; c.rightRows = rRows; c.leftResid = lRes; c.rightResid = rRes;
            return c;
        }
    }

    static final class Cand {
        double gain; int feat, thr, node;
        int[] leftRows, rightRows; double[] leftResid, rightResid;
    }

    // ============================== CORD =================================== //

    static final class Result { double statistic, pValue; boolean degenerate; }

    static final class Cord {
        final int nLevels, nEstimators, maxLeafNodes;
        final double learningRate, cdfClip, varFloor;
        final long randomState;

        Cord(int nLevels, int nEstimators, double learningRate, int maxLeafNodes,
             double cdfClip, double varFloor, long randomState) {
            this.nLevels = nLevels; this.nEstimators = nEstimators;
            this.learningRate = learningRate; this.maxLeafNodes = maxLeafNodes;
            this.cdfClip = cdfClip; this.varFloor = varFloor; this.randomState = randomState;
        }

        private Gbrt boost(long seed) {
            return new Gbrt(nEstimators, learningRate, maxLeafNodes, 20, 256,
                    true, 0.15, 10, 1e-7, seed);
        }

        Result fit(double[][] x, double[] y, double[][] z) {
            int n = y.length;
            long baseSeed = randomState;
            SplittableRandom rng = new SplittableRandom(baseSeed);

            int[] perm = permutation(n, baseSeed);
            int[][] folds = arraySplit3(perm);
            int[] a = folds[0], b = folds[1], c = folds[2];

            double[] thr = quantileGrid(select(y, a), nLevels);
            double[][] xz = hstack(x, z);

            long s0 = rng.nextLong(), s1 = rng.nextLong(), s2 = rng.nextLong();
            long[] sm = new long[nLevels];
            for (int k = 0; k < nLevels; k++) sm[k] = rng.nextLong();

            Cdf pCdf = fitCdf(rows(x, a), select(y, a), thr, s0);         // P(Y<=t | X) on A
            Cdf qCdf = fitCdf(rows(xz, a), select(y, a), thr, s1);        // P(Y<=t | X,Z) on A

            double[][] gB = witness(pCdf.predict(rows(x, b)), qCdf.predict(rows(xz, b)));
            double[][] gC = witness(pCdf.predict(rows(x, c)), qCdf.predict(rows(xz, c)));

            double[][] mC = new double[c.length][nLevels];               // m_t = E[g_t|X], B -> C
            double[][] xB = rows(x, b), xC = rows(x, c);
            for (int k = 0; k < nLevels; k++) {
                double[] target = colOf(gB, k);
                double[] pred = boost(sm[k]).fit(xB, target).predict(xC);
                for (int i = 0; i < c.length; i++) mC[i][k] = pred[i];
            }

            Cdf eCdf = fitCdf(rows(x, b), select(y, b), thr, s2);         // fresh e_t on B
            double[][] eC = eCdf.predict(xC);

            double[] yc = select(y, c);
            double[] psi = new double[c.length];
            for (int i = 0; i < c.length; i++) {
                double acc = 0;
                for (int k = 0; k < nLevels; k++) {
                    double resid = (yc[i] <= thr[k] ? 1.0 : 0.0) - eC[i][k];
                    acc += (gC[i][k] - mC[i][k]) * resid;
                }
                psi[i] = acc / nLevels;
            }

            Result r = new Result();
            double sd = std0(psi);
            if (sd > 0) {
                r.statistic = Math.sqrt(c.length) * mean(psi) / sd;
                r.pValue = normSf(r.statistic);
                r.degenerate = false;
            } else {
                r.statistic = Double.NaN; r.pValue = Double.NaN; r.degenerate = true;
            }
            return r;
        }

        // Whole conditional CDF over thresholds via K monotone-clamped threshold regressions.
        private Cdf fitCdf(double[][] feat, double[] y, double[] thr, long seed) {
            int K = thr.length;
            Gbrt[] models = new Gbrt[K];
            SplittableRandom r = new SplittableRandom(seed);
            for (int k = 0; k < K; k++) {
                double[] ind = new double[y.length];
                for (int i = 0; i < y.length; i++) ind[i] = (y[i] <= thr[k]) ? 1.0 : 0.0;
                models[k] = boost(r.nextLong()).fit(feat, ind);
            }
            return new Cdf(models, cdfClip);
        }

        private double[][] witness(double[][] p, double[][] q) {
            int n = p.length, K = p[0].length;
            double[][] g = new double[n][K];
            for (int i = 0; i < n; i++)
                for (int k = 0; k < K; k++) {
                    double v = Math.max(p[i][k] * (1.0 - p[i][k]), varFloor);
                    g[i][k] = (q[i][k] - p[i][k]) / v;
                }
            return g;
        }
    }

    /** Fitted conditional CDF: K threshold models, clipped and made monotone across t per row. */
    static final class Cdf {
        final Gbrt[] models; final double clip;
        Cdf(Gbrt[] models, double clip) { this.models = models; this.clip = clip; }

        double[][] predict(double[][] feat) {
            int K = models.length, n = feat.length;
            double[][] out = new double[n][K];
            for (int k = 0; k < K; k++) {
                double[] pk = models[k].predict(feat);
                for (int i = 0; i < n; i++) out[i][k] = pk[i];
            }
            for (int i = 0; i < n; i++) {          // monotone non-decreasing across thresholds
                double run = 0;
                for (int k = 0; k < K; k++) {
                    run = Math.max(run, out[i][k]);
                    out[i][k] = Math.min(Math.max(run, clip), 1.0 - clip);
                }
            }
            return out;
        }
    }

    // ============================ utilities =============================== //

    static double[] column(double[][] x, int j) {
        double[] c = new double[x.length];
        for (int i = 0; i < x.length; i++) c[i] = x[i][j];
        return c;
    }
    static double[] colOf(double[][] m, int k) {
        double[] c = new double[m.length];
        for (int i = 0; i < m.length; i++) c[i] = m[i][k];
        return c;
    }
    static double[][] rows(double[][] x, int[] idx) {
        double[][] o = new double[idx.length][];
        for (int i = 0; i < idx.length; i++) o[i] = x[idx[i]];
        return o;
    }
    static double[] select(double[] y, int[] idx) {
        double[] o = new double[idx.length];
        for (int i = 0; i < idx.length; i++) o[i] = y[idx[i]];
        return o;
    }
    static double[][] hstack(double[][] a, double[][] b) {
        int n = a.length, pa = n == 0 ? 0 : a[0].length, pb = n == 0 ? 0 : b[0].length;
        double[][] o = new double[n][pa + pb];
        for (int i = 0; i < n; i++) {
            System.arraycopy(a[i], 0, o[i], 0, pa);
            System.arraycopy(b[i], 0, o[i], pa, pb);
        }
        return o;
    }
    static double mean(double[] a) { double s = 0; for (double v : a) s += v; return a.length == 0 ? 0 : s / a.length; }
    static double sum(double[] a) { double s = 0; for (double v : a) s += v; return s; }
    static double std0(double[] a) {
        double m = mean(a), s = 0; for (double v : a) s += (v - m) * (v - m);
        return Math.sqrt(s / a.length);
    }
    static double mse(double[] y, double[] p) {
        double s = 0; for (int i = 0; i < y.length; i++) { double d = y[i] - p[i]; s += d * d; }
        return s / y.length;
    }
    static double[] fill(int n, double v) { double[] a = new double[n]; Arrays.fill(a, v); return a; }
    static int[] iota(int n) { int[] a = new int[n]; for (int i = 0; i < n; i++) a[i] = i; return a; }

    static int[] permutation(int n, long seed) {
        int[] p = iota(n);
        SplittableRandom r = new SplittableRandom(seed);
        for (int i = n - 1; i > 0; i--) { int j = r.nextInt(i + 1); int t = p[i]; p[i] = p[j]; p[j] = t; }
        return p;
    }
    // numpy array_split into 3: first (n%3) folds get the extra element.
    static int[][] arraySplit3(int[] perm) {
        int n = perm.length, base = n / 3, rem = n % 3;
        int[][] f = new int[3][];
        int off = 0;
        for (int k = 0; k < 3; k++) {
            int sz = base + (k < rem ? 1 : 0);
            f[k] = Arrays.copyOfRange(perm, off, off + sz);
            off += sz;
        }
        return f;
    }
    // quantile grid (k+0.5)/K, numpy 'linear' interpolation.
    static double[] quantileGrid(double[] y, int K) {
        double[] s = y.clone(); Arrays.sort(s);
        double[] q = new double[K];
        for (int k = 0; k < K; k++) {
            double p = (k + 0.5) / K, pos = p * (s.length - 1);
            int lo = (int) Math.floor(pos); double frac = pos - lo;
            q[k] = (lo + 1 < s.length) ? s[lo] + frac * (s[lo + 1] - s[lo]) : s[lo];
        }
        return q;
    }
    // Quantile split-threshold edges for histogram binning.
    static double[] edges(double[] col, int maxBins) {
        double[] s = col.clone(); Arrays.sort(s);
        // unique
        int u = 0; double[] uniq = new double[s.length];
        for (double v : s) if (u == 0 || v != uniq[u - 1]) uniq[u++] = v;
        uniq = Arrays.copyOf(uniq, u);
        if (u <= 1) return new double[0];
        if (u <= maxBins) {                       // one bin per unique value; midpoints
            double[] e = new double[u - 1];
            for (int i = 0; i < u - 1; i++) e[i] = 0.5 * (uniq[i] + uniq[i + 1]);
            return e;
        }
        int nEdges = maxBins - 1;
        double[] e = new double[nEdges];
        for (int i = 0; i < nEdges; i++) {
            double p = (i + 1.0) / maxBins, pos = p * (s.length - 1);
            int lo = (int) Math.floor(pos); double frac = pos - lo;
            e[i] = (lo + 1 < s.length) ? s[lo] + frac * (s[lo + 1] - s[lo]) : s[lo];
        }
        // dedup edges (ties collapse bins, that's fine)
        int m = 0; double[] de = new double[nEdges];
        for (double v : e) if (m == 0 || v != de[m - 1]) de[m++] = v;
        return Arrays.copyOf(de, m);
    }
    static int binOf(double x, double[] edges) {
        // number of edges strictly less than x  (searchsorted 'right' on <=): use <=
        int lo = 0, hi = edges.length;
        while (lo < hi) { int mid = (lo + hi) >>> 1; if (edges[mid] <= x) lo = mid + 1; else hi = mid; }
        return lo;
    }

    /** Upper-tail standard normal, 1 - Phi(z), via a high-accuracy erfc. */
    static double normSf(double z) { return 0.5 * erfc(z / Math.sqrt(2.0)); }
    static double erfc(double x) {
        double t = 1.0 / (1.0 + 0.5 * Math.abs(x));
        double tau = t * Math.exp(-x * x - 1.26551223 + t * (1.00002368 + t * (0.37409196
                + t * (0.09678418 + t * (-0.18628806 + t * (0.27886807 + t * (-1.13520398
                + t * (1.48851587 + t * (-0.82215223 + t * 0.17087277)))))))));
        return x >= 0 ? tau : 2.0 - tau;
    }

    // ============================== main ================================== //

    static double[][] gauss(int n, int p, java.util.Random r) {
        double[][] x = new double[n][p];
        for (int i = 0; i < n; i++) for (int j = 0; j < p; j++) x[i][j] = r.nextGaussian();
        return x;
    }
    static double rowSin(double[] row) { double s = 0; for (double v : row) s += Math.sin(v); return s; }
    static double rowCos(double[] row) { double s = 0; for (double v : row) s += Math.cos(v); return s; }

    /**
     * Main method that serves as the entry point for executing the statistical calibration,
     * power sweep, and single-run tests. This method evaluates the null and alternative hypotheses
     * using a Monte Carlo simulation across multiple seeds.
     *
     * @param args Command-line arguments (not utilized in this implementation).
     */
    public static void main(String[] args) {
        // Single runs mirroring the Python __main__.
        runOnce(0, false);
        runOnce(0, true);

        // Calibration / power sweep across seeds.
        int reps = 30, nRej = 0, nRejAlt = 0;
        double sumPnull = 0;
        for (int s = 1; s <= reps; s++) {
            double pn = single(s, false), pa = single(s, true);
            sumPnull += pn;
            if (pn < 0.05) nRej++;
            if (pa < 0.05) nRejAlt++;
        }
        System.out.printf("%n=== sweep over %d seeds ===%n", reps);
        System.out.printf("NULL: rejection rate @0.05 = %.3f (target ~0.05), mean p = %.3f (target ~0.5)%n",
                nRej / (double) reps, sumPnull / reps);
        System.out.printf("ALT : rejection rate @0.05 = %.3f (target ~1.0)%n", nRejAlt / (double) reps);
    }

    static double single(int seed, boolean alt) {
        java.util.Random r = new java.util.Random(seed);
        int n = 600;
        double[][] X = gauss(n, 3, r);
        double[] Y = new double[n]; double[][] Z = new double[n][1];
        if (!alt) {
            for (int i = 0; i < n; i++) { Y[i] = rowSin(X[i]) + r.nextGaussian(); Z[i][0] = rowCos(X[i]) + r.nextGaussian(); }
        } else {
            for (int i = 0; i < n; i++) {
                double eta = r.nextGaussian();
                Z[i][0] = rowCos(X[i]) + eta;
                Y[i] = rowSin(X[i]) + (eta * eta - 1.0) + r.nextGaussian();
            }
        }
        Cord cord = new Cord(9, 300, 0.1, 31, 1e-3, 0.02, seed);
        return cord.fit(X, Y, Z).pValue;
    }

    static void runOnce(int seed, boolean alt) {
        java.util.Random r = new java.util.Random(seed + 100);
        int n = 600;
        double[][] X = gauss(n, 3, r);
        double[] Y = new double[n]; double[][] Z = new double[n][1];
        if (!alt) {
            for (int i = 0; i < n; i++) { Y[i] = rowSin(X[i]) + r.nextGaussian(); Z[i][0] = rowCos(X[i]) + r.nextGaussian(); }
        } else {
            for (int i = 0; i < n; i++) {
                double eta = r.nextGaussian();
                Z[i][0] = rowCos(X[i]) + eta;
                Y[i] = rowSin(X[i]) + (eta * eta - 1.0) + r.nextGaussian();
            }
        }
        Cord cord = new Cord(9, 300, 0.1, 31, 1e-3, 0.02, seed);
        Result res = cord.fit(X, Y, Z);
        System.out.printf("%-5s : T = %8.4f   p = %.4g   %s%n",
                alt ? "ALT" : "NULL", res.statistic, res.pValue,
                res.pValue < 0.05 ? "REJECT" : "fail to reject");
    }
}
