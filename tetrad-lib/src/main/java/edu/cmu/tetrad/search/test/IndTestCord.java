/// ////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.util.TetradLogger;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.SplittableRandom;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CORD conditional-independence test: an omnibus, model-X-free test of the full conditional law
 * {@code H0: y _||_ z | Z}, ported from the reference Python implementation (Chen et al.,
 * "Orthogonal Rank Scores for Conditional Dependence").
 *
 * <h2>What it tests</h2>
 * Unlike covariance/mean tests such as Fisher's Z or the generalised covariance measure, CORD keeps
 * power when the conditional covariance is zero but the dependence lives in the variance, the tails,
 * or the co-volatility, while still controlling size. It targets the whole conditional distribution
 * function of one variable rather than a single moment.
 *
 * <h2>Method (one A/B/C split, cross-fitted, doubly residualised)</h2>
 * Writing {@code Y} for the modelled variable, {@code W} for the other variable and {@code X} for the
 * conditioning set, over a grid of {@code K} thresholds {@code t}:
 * <ol>
 *   <li><b>A</b> fit two conditional CDFs of {@code Y}: {@code p_t(X)=P(Y<=t|X)} and
 *       {@code q_t(X,W)=P(Y<=t|X,W)}; form the witness {@code g_t=(q_t-p_t)/v_t}, {@code v_t=p_t(1-p_t)}.</li>
 *   <li><b>B</b> fit the centring {@code m_t(X)=E[g_t|X]} and a <em>fresh</em> {@code e_t(X)=P(Y<=t|X)}
 *       (disjoint from {@code p_t}).</li>
 *   <li><b>C</b> score {@code psi_i = mean_t (g_t-m_t)(1{Y_i<=t}-e_t)};
 *       {@code T = sqrt(|C|)*mean(psi)/sd(psi)}.</li>
 * </ol>
 * Under {@code H0} both centred factors are conditionally mean-zero and independent given {@code X}, so
 * the null bias is a product of two small nuisance errors and {@code T -> N(0,1)}. The test is one-sided
 * upper; the p-value is {@code 1 - Phi(T)}.
 *
 * <h2>Nuisance models (the one substantive porting decision)</h2>
 * The Python reference uses scikit-learn's {@code HistGradientBoosting}. There is no drop-in Java
 * equivalent, so this class ships a small self-contained histogram gradient-boosted regression-tree
 * learner ({@link Gbrt}). Each conditional CDF {@code P(Y<=t|.)} is estimated as {@code K} least-squares
 * regressions of the threshold indicators {@code 1{Y<=t_k}} (made monotone in {@code t} per row and
 * clipped), rather than one joint multiclass model. The two estimands are identical; the estimator is
 * simpler. Because CORD is doubly robust, this weaker learner costs a little power but preserves size:
 * on the reference DGPs the null mean p-value and the (conservative) null rejection rate match the
 * Python within Monte-Carlo error, with modestly lower power on the higher-moment alternative.
 *
 * <h2>Orientation</h2>
 * CORD is asymmetric: it models the CDF of one of the two variables. A constraint-based search expects
 * {@code checkIndependence(x,y,Z)} and {@code checkIndependence(y,x,Z)} to agree, so by default this
 * class is <b>symmetrised</b>: it runs both orientations and reports the Bonferroni-combined p-value
 * {@code min(1, 2*min(p_xy, p_yx))} (a valid level-alpha test under arbitrary dependence of the two
 * halves). Call {@link #setSymmetric(boolean) setSymmetric(false)} to recover the exact single-orientation
 * CORD.py behaviour (modelling the CDF of {@code x}) at half the cost.
 *
 * <h2>Cost, determinism, thread-safety</h2>
 * Each test fits {@code ~4K} boosted models (double that when symmetrised), so CORD is far heavier than a
 * Fisher-Z test; for large searches reduce {@link #setNLevels(int) nLevels} and/or
 * {@link #setNEstimators(int) nEstimators}. Results are deterministic: the per-test random split is seeded
 * from {@link #setSeed(long) seed} mixed with the independence fact, so repeated identical queries return
 * identical results and are cached. All per-test computation is done in locals; configuration setters must
 * not be called concurrently with running tests.
 *
 * @author josephramsey
 */
public final class IndTestCord implements IndependenceTest, RowsSettable {

    private final DataSet dataSet;
    private final List<Node> variables;
    private final Map<String, Integer> indexMap;
    private final Map<String, Node> nameMap;

    // Cache of computed results (valid because every test is deterministic given the seed + fact).
    private final Map<String, IndependenceResult> cache = new ConcurrentHashMap<>();

    private volatile double alpha;
    private volatile boolean verbose = false;
    private volatile boolean symmetric = true;
    private volatile List<Integer> rows = null;

    // CORD hyper-parameters (defaults inherited from the reference study).
    private volatile int nLevels = 9;
    private volatile int nEstimators = 300;
    private volatile double learningRate = 0.1;
    private volatile int maxLeafNodes = 31;
    private volatile double cdfClip = 1e-3;
    private volatile double varFloor = 0.02;
    private volatile long seed = 0L;

    /**
     * Constructs a CORD independence test over the given (continuous or numeric) data set.
     *
     * @param dataSet the data; columns are read as doubles (discrete columns are treated as ordinal codes).
     * @param alpha   the significance level in [0, 1].
     */
    public IndTestCord(DataSet dataSet, double alpha) {
        if (dataSet == null) throw new NullPointerException("dataSet");
        this.dataSet = dataSet;
        this.variables = new ArrayList<>(dataSet.getVariables());
        this.indexMap = new HashMap<>();
        this.nameMap = new HashMap<>();
        for (int i = 0; i < variables.size(); i++) {
            Node v = variables.get(i);
            indexMap.put(v.getName(), i);
            nameMap.put(v.getName(), v);
        }
        setAlpha(alpha);
    }

    /* ============================ IndependenceTest ============================ */

    /**
     * Tests {@code x _||_ y | z} with CORD and returns the result.
     *
     * <p>Thread-safe: all computation is in locals and the returned {@link IndependenceResult} is
     * freshly allocated. Results are cached by the independence fact.
     */
    @Override
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z) {
        final double a = this.alpha;

        List<Node> zList = new ArrayList<>(z);
        zList.sort((u, v) -> Integer.compare(indexMap.get(u.getName()), indexMap.get(v.getName())));

        // Cache key: in symmetric mode the pair {x,y} is unordered, so canonicalize it.
        String pairKey = (symmetric && x.getName().compareTo(y.getName()) > 0)
                ? y.getName() + "," + x.getName()
                : x.getName() + "," + y.getName();
        String key = a + "|" + symmetric + "|" + pairKey + "|" + zList;
        IndependenceResult cached = cache.get(key);
        if (cached != null) return cached;

        double p;
        if (symmetric) {
            // Each orientation's seed depends only on (modelled, other, Z), so p1 and p2 are
            // identical regardless of the argument order the caller used.
            double p1 = cordPValue(x, y, zList, orientationSeed(x, y, zList));
            double p2 = cordPValue(y, x, zList, orientationSeed(y, x, zList));
            double m = Math.min(nanTo1(p1), nanTo1(p2));
            p = Math.min(1.0, 2.0 * m);
        } else {
            p = cordPValue(x, y, zList, orientationSeed(x, y, zList));
        }

        if (Double.isNaN(p)) {
            // Degenerate score variance: no evidence against independence.
            IndependenceResult r = new IndependenceResult(new IndependenceFact(x, y, z), true, 1.0, a - 1.0);
            cache.put(key, r);
            return r;
        }

        boolean independent = p > a;
        IndependenceResult result = new IndependenceResult(new IndependenceFact(x, y, z), independent, p, a - p);
        if (this.verbose && independent) {
            TetradLogger.getInstance().log(LogUtilsSearch.independenceFactMsg(x, y, z, p));
        }
        cache.put(key, result);
        return result;
    }

    /**
     * Retrieves the list of variables associated with this test.
     *
     * @return the variables.
     */
    @Override
    public List<Node> getVariables() {
        return new ArrayList<>(variables);
    }

    /**
     * Retrieves the data model backing this test.
     *
     * @return the data set.
     */
    @Override
    public DataModel getData() {
        return dataSet;
    }

    /**
     * Returns whether verbose output is printed.
     *
     * @return true if verbose.
     */
    @Override
    public boolean isVerbose() {
        return verbose;
    }

    /**
     * Sets whether verbose output is printed.
     *
     * @param verbose true, if so.
     */
    @Override
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Returns the significance level.
     *
     * @return the significance level.
     */
    @Override
    public double getAlpha() {
        return alpha;
    }

    /**
     * Sets the significance level, validated to lie in [0, 1].
     *
     * @param alpha the significance level.
     */
    @Override
    public void setAlpha(double alpha) {
        if (alpha < 0.0 || alpha > 1.0) throw new IllegalArgumentException("Significance out of range: " + alpha);
        this.alpha = alpha;
    }

    /**
     * Returns the sample size (number of rows in use).
     *
     * @return the sample size.
     */
    @Override
    public int getSampleSize() {
        return listRows().size();
    }

    /**
     * Returns a string representation of this test.
     *
     * @return the string.
     */
    @Override
    public String toString() {
        DecimalFormat f = new DecimalFormat("0.0###");
        return "CORD (omnibus), alpha = " + f.format(alpha)
                + ", K = " + nLevels + ", trees = " + nEstimators
                + (symmetric ? ", symmetric" : ", single-orientation");
    }

    /* ============================== RowsSettable ============================= */

    /**
     * Returns the row indices currently in use, or {@code null} if all rows are used.
     *
     * @return the row indices.
     */
    @Override
    public List<Integer> getRows() {
        return rows;
    }

    /**
     * Restricts the test to a subset of rows (for subsampling / bootstrap). Passing {@code null} uses
     * all rows. Changing the rows clears the result cache.
     *
     * @param rows the row indices to use (non-null, non-negative), or {@code null} for all rows.
     */
    @Override
    public void setRows(List<Integer> rows) {
        if (rows == null) {
            this.rows = null;
        } else {
            for (int i = 0; i < rows.size(); i++) {
                if (rows.get(i) == null) throw new NullPointerException("Row " + i + " is null.");
                if (rows.get(i) < 0) throw new IllegalArgumentException("Row " + i + " is negative.");
            }
            this.rows = List.copyOf(rows);
        }
        cache.clear();
    }

    /* ============================ CORD configuration ========================= */

    /** @param symmetric whether to symmetrise over the two orientations (default true). Clears the cache. */
    public void setSymmetric(boolean symmetric) { this.symmetric = symmetric; cache.clear(); }

    /** @return whether the test is symmetrised over both orientations. */
    public boolean isSymmetric() { return symmetric; }

    /** @param nLevels number of Y-threshold levels K (the CDF grid). Clears the cache. */
    public void setNLevels(int nLevels) {
        if (nLevels < 2) throw new IllegalArgumentException("nLevels must be >= 2");
        this.nLevels = nLevels; cache.clear();
    }

    /** @return the number of threshold levels K. */
    public int getNLevels() { return nLevels; }

    /** @param nEstimators boosting iterations per nuisance model. Clears the cache. */
    public void setNEstimators(int nEstimators) {
        if (nEstimators < 1) throw new IllegalArgumentException("nEstimators must be >= 1");
        this.nEstimators = nEstimators; cache.clear();
    }

    /** @return the boosting iterations per nuisance model. */
    public int getNEstimators() { return nEstimators; }

    /** @param learningRate the nuisance learning rate. Clears the cache. */
    public void setLearningRate(double learningRate) {
        if (learningRate <= 0) throw new IllegalArgumentException("learningRate must be > 0");
        this.learningRate = learningRate; cache.clear();
    }

    /** @param maxLeafNodes the nuisance tree size. Clears the cache. */
    public void setMaxLeafNodes(int maxLeafNodes) {
        if (maxLeafNodes < 2) throw new IllegalArgumentException("maxLeafNodes must be >= 2");
        this.maxLeafNodes = maxLeafNodes; cache.clear();
    }

    /** @param seed the base random seed for the A/B/C split. Clears the cache. */
    public void setSeed(long seed) { this.seed = seed; cache.clear(); }

    /** @return the base random seed. */
    public long getSeed() { return seed; }

    /* ============================== internals =============================== */

    private static double nanTo1(double p) { return Double.isNaN(p) ? 1.0 : p; }

    /** Stable seed for "model {@code modelled}'s CDF against {@code other} given {@code zList}". */
    private long orientationSeed(Node modelled, Node other, List<Node> zList) {
        int h = modelled.getName().hashCode();
        h = 31 * h + other.getName().hashCode();
        for (Node zn : zList) h = 31 * h + zn.getName().hashCode();
        return seed ^ (0x9E3779B97F4A7C15L * h);
    }

    private List<Integer> listRows() {
        List<Integer> r = this.rows;
        if (r != null) return r;
        List<Integer> all = new ArrayList<>(dataSet.getNumRows());
        for (int i = 0; i < dataSet.getNumRows(); i++) all.add(i);
        return all;
    }

    /**
     * Extracts the columns for one orientation, drops rows with missing values among the involved
     * columns, runs CORD (modelling the CDF of {@code yVar}), and returns the p-value.
     */
    private double cordPValue(Node yVar, Node wVar, List<Node> xVars, long factSeed) {
        int yi = indexMap.get(yVar.getName());
        int wi = indexMap.get(wVar.getName());
        int[] xi = new int[xVars.size()];
        for (int i = 0; i < xVars.size(); i++) xi[i] = indexMap.get(xVars.get(i).getName());

        List<Integer> use = listRows();
        int nAll = use.size();
        double[] Y = new double[nAll];
        double[] W = new double[nAll];
        double[][] X = new double[nAll][xi.length];

        int n = 0;
        for (int rr = 0; rr < nAll; rr++) {
            int row = use.get(rr);
            double yv = dataSet.getDouble(row, yi);
            double wv = dataSet.getDouble(row, wi);
            boolean ok = !Double.isNaN(yv) && !Double.isNaN(wv);
            double[] xrow = new double[xi.length];
            for (int j = 0; ok && j < xi.length; j++) {
                xrow[j] = dataSet.getDouble(row, xi[j]);
                if (Double.isNaN(xrow[j])) ok = false;
            }
            if (!ok) continue;
            Y[n] = yv; W[n] = wv; X[n] = xrow; n++;
        }
        if (n < 3 * nLevels) {
            // Too few complete rows to split three ways with K levels; treat as no evidence.
            return Double.NaN;
        }
        Y = Arrays.copyOf(Y, n);
        W = Arrays.copyOf(W, n);
        X = Arrays.copyOf(X, n);
        double[][] Wm = new double[n][1];
        for (int i = 0; i < n; i++) Wm[i][0] = W[i];

        Cord cord = new Cord(nLevels, nEstimators, learningRate, maxLeafNodes, cdfClip, varFloor, factSeed);
        return cord.fit(X, Y, Wm).pValue;
    }

    /* =============================================================================================
     *  Self-contained CORD engine (no external dependencies). Validated against the Python reference.
     * ============================================================================================= */

    private static final class CordResult { double statistic; double pValue; boolean degenerate; }

    private static final class Cord {
        final int nLevels, nEstimators, maxLeafNodes;
        final double learningRate, cdfClip, varFloor;
        final long randomState;

        Cord(int nLevels, int nEstimators, double learningRate, int maxLeafNodes,
             double cdfClip, double varFloor, long randomState) {
            this.nLevels = nLevels; this.nEstimators = nEstimators;
            this.learningRate = learningRate; this.maxLeafNodes = maxLeafNodes;
            this.cdfClip = cdfClip; this.varFloor = varFloor; this.randomState = randomState;
        }

        private Gbrt boost(long s) {
            return new Gbrt(nEstimators, learningRate, maxLeafNodes, 20, 256, true, 0.15, 10, 1e-7, s);
        }

        CordResult fit(double[][] x, double[] y, double[][] z) {
            int n = y.length;
            SplittableRandom rng = new SplittableRandom(randomState);

            int[] perm = permutation(n, randomState);
            int[][] folds = arraySplit3(perm);
            int[] a = folds[0], b = folds[1], c = folds[2];

            double[] thr = quantileGrid(select(y, a), nLevels);
            double[][] xz = hstack(x, z);

            long s0 = rng.nextLong(), s1 = rng.nextLong(), s2 = rng.nextLong();
            long[] sm = new long[nLevels];
            for (int k = 0; k < nLevels; k++) sm[k] = rng.nextLong();

            Cdf pCdf = fitCdf(rows(x, a), select(y, a), thr, s0);
            Cdf qCdf = fitCdf(rows(xz, a), select(y, a), thr, s1);

            double[][] gB = witness(pCdf.predict(rows(x, b)), qCdf.predict(rows(xz, b)));
            double[][] gC = witness(pCdf.predict(rows(x, c)), qCdf.predict(rows(xz, c)));

            double[][] mC = new double[c.length][nLevels];
            double[][] xB = rows(x, b), xC = rows(x, c);
            for (int k = 0; k < nLevels; k++) {
                double[] target = colOf(gB, k);
                double[] pred = boost(sm[k]).fit(xB, target).predict(xC);
                for (int i = 0; i < c.length; i++) mC[i][k] = pred[i];
            }

            Cdf eCdf = fitCdf(rows(x, b), select(y, b), thr, s2);
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

            CordResult r = new CordResult();
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

        private Cdf fitCdf(double[][] feat, double[] y, double[] thr, long s) {
            int K = thr.length;
            Gbrt[] models = new Gbrt[K];
            SplittableRandom r = new SplittableRandom(s);
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

    /** Fitted conditional CDF: K threshold models, clipped and made monotone across thresholds per row. */
    private static final class Cdf {
        final Gbrt[] models; final double clip;

        Cdf(Gbrt[] models, double clip) { this.models = models; this.clip = clip; }

        double[][] predict(double[][] feat) {
            int K = models.length, n = feat.length;
            double[][] out = new double[n][K];
            for (int k = 0; k < K; k++) {
                double[] pk = models[k].predict(feat);
                for (int i = 0; i < n; i++) out[i][k] = pk[i];
            }
            for (int i = 0; i < n; i++) {
                double run = 0;
                for (int k = 0; k < K; k++) {
                    run = Math.max(run, out[i][k]);
                    out[i][k] = Math.min(Math.max(run, clip), 1.0 - clip);
                }
            }
            return out;
        }
    }

    /** Histogram gradient-boosted regression trees (least-squares, leaf-wise), no external deps. */
    private static final class Gbrt {
        final int nEstimators, maxLeafNodes, minSamplesLeaf, maxBins, nIterNoChange;
        final double learningRate, validationFraction, tol;
        final boolean earlyStopping;
        final long seed;

        double baseline;
        double[][] binEdges;
        List<Tree> trees = new ArrayList<>();
        int nFeatures;

        Gbrt(int nEstimators, double learningRate, int maxLeafNodes, int minSamplesLeaf, int maxBins,
             boolean earlyStopping, double validationFraction, int nIterNoChange, double tol, long seed) {
            this.nEstimators = nEstimators; this.learningRate = learningRate;
            this.maxLeafNodes = maxLeafNodes; this.minSamplesLeaf = minSamplesLeaf;
            this.maxBins = maxBins; this.earlyStopping = earlyStopping;
            this.validationFraction = validationFraction; this.nIterNoChange = nIterNoChange;
            this.tol = tol; this.seed = seed;
        }

        Gbrt fit(double[][] x, double[] y) {
            int n = y.length;
            this.nFeatures = (n == 0) ? 0 : x[0].length;
            if (nFeatures == 0) { baseline = mean(y); return this; }

            binEdges = new double[nFeatures][];
            for (int j = 0; j < nFeatures; j++) binEdges[j] = edges(column(x, j), maxBins);
            int[][] binned = new int[n][nFeatures];
            for (int i = 0; i < n; i++)
                for (int j = 0; j < nFeatures; j++) binned[i][j] = binOf(x[i][j], binEdges[j]);

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
            int[] nBins = new int[nFeatures];
            for (int j = 0; j < nFeatures; j++) nBins[j] = binEdges[j].length + 1;

            for (int it = 0; it < nEstimators; it++) {
                double[] resid = new double[trainIdx.length];
                for (int i = 0; i < trainIdx.length; i++) resid[i] = y[trainIdx[i]] - predTr[i];

                Tree tree = new Tree(maxLeafNodes, minSamplesLeaf);
                tree.fit(binned, trainIdx, resid, nBins);
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
    }

    /** Single least-squares regression tree grown best-first over binned features. */
    private static final class Tree {
        final int maxLeafNodes, minSamplesLeaf;
        final int[] featOf, thrOf, leftOf, rightOf;
        final double[] valOf;
        int nNodes = 0;

        Tree(int maxLeafNodes, int minSamplesLeaf) {
            this.maxLeafNodes = maxLeafNodes; this.minSamplesLeaf = minSamplesLeaf;
            int cap = 2 * maxLeafNodes + 1;
            featOf = new int[cap]; thrOf = new int[cap];
            leftOf = new int[cap]; rightOf = new int[cap]; valOf = new double[cap];
            Arrays.fill(featOf, -1); Arrays.fill(leftOf, -1); Arrays.fill(rightOf, -1);
        }

        void fit(int[][] binned, int[] rows, double[] resid, int[] nBins) {
            int root = newNode(sum(resid) / resid.length);
            PriorityQueue<Cand> pq = new PriorityQueue<>((p, q) -> Double.compare(q.gain, p.gain));
            Cand rc = bestSplit(binned, rows, resid, nBins);
            if (rc != null) { rc.node = root; pq.add(rc); }
            int leaves = 1;
            while (leaves < maxLeafNodes && !pq.isEmpty()) {
                Cand c = pq.poll();
                if (c.gain <= 0) break;
                int lNode = newNode(sum(c.leftResid) / c.leftResid.length);
                int rNode = newNode(sum(c.rightResid) / c.rightResid.length);
                featOf[c.node] = c.feat; thrOf[c.node] = c.thr;
                leftOf[c.node] = lNode; rightOf[c.node] = rNode;
                leaves++;
                Cand lc = bestSplit(binned, c.leftRows, c.leftResid, nBins);
                if (lc != null) { lc.node = lNode; pq.add(lc); }
                Cand rcc = bestSplit(binned, c.rightRows, c.rightResid, nBins);
                if (rcc != null) { rcc.node = rNode; pq.add(rcc); }
            }
        }

        private int newNode(double val) { valOf[nNodes] = val; return nNodes++; }

        double predict(int[] bins) {
            int node = 0;
            while (featOf[node] != -1) node = (bins[featOf[node]] <= thrOf[node]) ? leftOf[node] : rightOf[node];
            return valOf[node];
        }

        private Cand bestSplit(int[][] binned, int[] rows, double[] resid, int[] nBins) {
            int n = rows.length;
            if (n < 2 * minSamplesLeaf) return null;
            double G = sum(resid);
            double parent = G * G / n;
            double bestGain = 0; int bestFeat = -1, bestThr = -1;
            for (int j = 0; j < nBins.length; j++) {
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

    private static final class Cand {
        double gain; int feat, thr, node;
        int[] leftRows, rightRows; double[] leftResid, rightResid;
    }

    /* ------------------------------- helpers ------------------------------- */

    private static double[] column(double[][] x, int j) {
        double[] c = new double[x.length];
        for (int i = 0; i < x.length; i++) c[i] = x[i][j];
        return c;
    }
    private static double[] colOf(double[][] m, int k) {
        double[] c = new double[m.length];
        for (int i = 0; i < m.length; i++) c[i] = m[i][k];
        return c;
    }
    private static double[][] rows(double[][] x, int[] idx) {
        double[][] o = new double[idx.length][];
        for (int i = 0; i < idx.length; i++) o[i] = x[idx[i]];
        return o;
    }
    private static double[] select(double[] y, int[] idx) {
        double[] o = new double[idx.length];
        for (int i = 0; i < idx.length; i++) o[i] = y[idx[i]];
        return o;
    }
    private static double[][] hstack(double[][] a, double[][] b) {
        int n = a.length, pa = n == 0 ? 0 : a[0].length, pb = n == 0 ? 0 : b[0].length;
        double[][] o = new double[n][pa + pb];
        for (int i = 0; i < n; i++) {
            System.arraycopy(a[i], 0, o[i], 0, pa);
            System.arraycopy(b[i], 0, o[i], pa, pb);
        }
        return o;
    }
    private static double mean(double[] a) { double s = 0; for (double v : a) s += v; return a.length == 0 ? 0 : s / a.length; }
    private static double sum(double[] a) { double s = 0; for (double v : a) s += v; return s; }
    private static double std0(double[] a) {
        double m = mean(a), s = 0; for (double v : a) s += (v - m) * (v - m);
        return Math.sqrt(s / a.length);
    }
    private static double mse(double[] y, double[] p) {
        double s = 0; for (int i = 0; i < y.length; i++) { double d = y[i] - p[i]; s += d * d; }
        return s / y.length;
    }
    private static double[] fill(int n, double v) { double[] a = new double[n]; Arrays.fill(a, v); return a; }
    private static int[] iota(int n) { int[] a = new int[n]; for (int i = 0; i < n; i++) a[i] = i; return a; }

    private static int[] permutation(int n, long seed) {
        int[] p = iota(n);
        SplittableRandom r = new SplittableRandom(seed);
        for (int i = n - 1; i > 0; i--) { int j = r.nextInt(i + 1); int t = p[i]; p[i] = p[j]; p[j] = t; }
        return p;
    }
    private static int[][] arraySplit3(int[] perm) {
        int n = perm.length, base = n / 3, rem = n % 3, off = 0;
        int[][] f = new int[3][];
        for (int k = 0; k < 3; k++) {
            int sz = base + (k < rem ? 1 : 0);
            f[k] = Arrays.copyOfRange(perm, off, off + sz);
            off += sz;
        }
        return f;
    }
    private static double[] quantileGrid(double[] y, int K) {
        double[] s = y.clone(); Arrays.sort(s);
        double[] q = new double[K];
        for (int k = 0; k < K; k++) {
            double p = (k + 0.5) / K, pos = p * (s.length - 1);
            int lo = (int) Math.floor(pos); double frac = pos - lo;
            q[k] = (lo + 1 < s.length) ? s[lo] + frac * (s[lo + 1] - s[lo]) : s[lo];
        }
        return q;
    }
    private static double[] edges(double[] col, int maxBins) {
        double[] s = col.clone(); Arrays.sort(s);
        int u = 0; double[] uniq = new double[s.length];
        for (double v : s) if (u == 0 || v != uniq[u - 1]) uniq[u++] = v;
        uniq = Arrays.copyOf(uniq, u);
        if (u <= 1) return new double[0];
        if (u <= maxBins) {
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
        int m = 0; double[] de = new double[nEdges];
        for (double v : e) if (m == 0 || v != de[m - 1]) de[m++] = v;
        return Arrays.copyOf(de, m);
    }
    private static int binOf(double x, double[] edges) {
        int lo = 0, hi = edges.length;
        while (lo < hi) { int mid = (lo + hi) >>> 1; if (edges[mid] <= x) lo = mid + 1; else hi = mid; }
        return lo;
    }
    /** Upper-tail standard normal, 1 - Phi(z), via a high-accuracy erfc. */
    private static double normSf(double z) { return 0.5 * erfc(z / Math.sqrt(2.0)); }
    private static double erfc(double x) {
        double t = 1.0 / (1.0 + 0.5 * Math.abs(x));
        double tau = t * Math.exp(-x * x - 1.26551223 + t * (1.00002368 + t * (0.37409196
                + t * (0.09678418 + t * (-0.18628806 + t * (0.27886807 + t * (-1.13520398
                + t * (1.48851587 + t * (-0.82215223 + t * 0.17087277)))))))));
        return x >= 0 ? tau : 2.0 - tau;
    }
}
