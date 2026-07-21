///////////////////////////////////////////////////////////////////////////////
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
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.search.CordEngine;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradLogger;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tetrad {@link IndependenceTest} adapter for the score-pooled CORD engine ({@link CordEngine3}), the
 * omnibus, orthogonal rank-score test of the full conditional law that keeps power against variance,
 * tail, and co-volatility dependence that covariance/mean tests (Fisher Z, GCM) miss.
 *
 * <p>{@link CordEngine3} runs three cyclic role-rotations of one A/B/C partition and pools the resulting
 * per-observation cross-fitted scores into a single studentized statistic (full-sample 3-fold DML
 * cross-fitting). Unlike the median-of-p aggregation, the pooled statistic is <em>continuous</em> and
 * does not deposit an atom of probability at {@code p = 1}, so its p-values spread out enough to be
 * thresholded in a constraint-based search.
 *
 * <p>This class is a thin adapter: the statistics live in {@link CordEngine3}. The adapter (i) extracts the
 * requested columns with listwise deletion of missing values, (ii) maps Tetrad's
 * {@code checkIndependence(x, y, z)} onto CORD's {@code test(X, Y, Z)} roles, (iii) makes each call
 * deterministic and cacheable, and (iv) turns the p-value into an {@link IndependenceResult}.
 *
 * <h2>Role mapping</h2>
 * Tetrad asks {@code x _||_ y | z}. CORD tests {@code Y _||_ Z | X}, modelling the conditional CDF of its
 * {@code Y}. The adapter passes Tetrad's conditioning set {@code z} as CORD's {@code X}, Tetrad's {@code x}
 * as CORD's {@code Y}, and Tetrad's {@code y} as CORD's {@code Z}. When {@code z} is empty, CORD's {@code X}
 * has zero columns and the test reduces to a marginal independence test of {@code x} and {@code y}.
 *
 * <h2>Orientation</h2>
 * CORD is directional (it models the CDF of one variable), and the pooled statistic is directional too. A
 * constraint-based search expects {@code checkIndependence(x, y, z)} and {@code checkIndependence(y, x, z)}
 * to agree, so by default this adapter runs both orientations and combines their p-values with the
 * Cauchy/ACAT rule (Liu &amp; Xie, 2020): order-invariant, valid under arbitrary dependence, and -- unlike a
 * Bonferroni {@code 2*min} -- still continuous with no atom at 1.0. Call {@link #setSymmetric(boolean)
 * setSymmetric(false)} for the single-orientation statistic (modelling the CDF of {@code x}) at half the
 * cost; it too is atom-free.
 *
 * <h2>Cost, determinism, thread-safety</h2>
 * Each test fits many boosted models across three rotations (double that when symmetrised), so it is far
 * heavier than Fisher Z; for large searches reduce {@link #setNLevels(int) nLevels} and/or
 * {@link #setNEstimators(int) nEstimators}. Each call seeds the engine from {@link #setSeed(long) seed}
 * mixed with the independence fact, so repeated identical queries return identical, cached results. All
 * per-test work is done in locals and fresh {@link CordEngine3} instances; configuration setters must not be
 * called concurrently with running tests.
 *
 * @author josephramsey
 * @see CordEngine3
 */
public final class IndTestCord implements IndependenceTest, RowsSettable {

    private final DataSet dataSet;
    private final List<Node> variables;
    private final Map<String, Integer> indexMap;

    // Cache of computed results (valid because every test is deterministic given the seed + fact).
    private final Map<String, IndependenceResult> cache = new ConcurrentHashMap<>();

    private volatile double alpha;
    private volatile boolean verbose = false;
    private volatile boolean symmetric = true;
    private volatile List<Integer> rows = null;

    // CORD hyper-parameters (defaults inherited from Eric's engine / the reference study).
    private volatile int nLevels = 9;
    private volatile int nEstimators = 300;
    private volatile double learningRate = 0.1;
    private volatile int maxLeafNodes = 31;
    private volatile long seed = 0L;
    private int cordEngine = 3;

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
        this.indexMap = new ConcurrentHashMap<>();
        for (int i = 0; i < variables.size(); i++) {
            indexMap.put(variables.get(i).getName(), i);
        }
        setAlpha(alpha);
    }

    /* ============================ IndependenceTest ============================ */

    /**
     * Tests {@code x _||_ y | z} with CORD and returns the result.
     *
     * <p>Thread-safe: all computation is in locals and a fresh engine instance; the returned
     * {@link IndependenceResult} is freshly allocated. Results are cached by the independence fact.
     *
     * @param x the first variable.
     * @param y the second variable.
     * @param z the conditioning set.
     * @return the independence result.
     */
    @Override
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z) {
        final double a = this.alpha;

        // In symmetric mode the test is order-invariant, so canonicalize the unordered pair {x,y} by
        // index for the seed and cache key; combined with ACAT's symmetric combiner this makes
        // checkIndependence(x,y,z) and checkIndependence(y,x,z) return byte-identical results. In
        // directional mode the given order is kept (the statistic genuinely depends on it).
        Node xc = x, yc = y;
        if (symmetric && indexMap.get(y.getName()) < indexMap.get(x.getName())) { xc = y; yc = x; }

        List<Node> zList = new ArrayList<>(z);
        zList.sort((u, v) -> Integer.compare(indexMap.get(u.getName()), indexMap.get(v.getName())));

        StringBuilder kb = new StringBuilder();
        kb.append(a).append('|').append(symmetric).append('|')
                .append(xc.getName()).append(',').append(yc.getName()).append('|');
        for (Node zn : zList) kb.append(zn.getName()).append(',');
        String key = kb.toString();

        IndependenceResult cached = cache.get(key);
        if (cached != null) return cached;

        long factSeed = seed ^ (0x9E3779B97F4A7C15L * key.hashCode());
        double p = cordPValue(xc, yc, zList, factSeed);

        if (Double.isNaN(p)) {
            // Degenerate score variance / too few complete rows: no evidence against independence.
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
        return "CORD (score-pooled, CordEric3), alpha = " + f.format(alpha)
                + ", K = " + nLevels + ", trees = " + nEstimators
                + (symmetric ? ", symmetric (ACAT)" : ", single-orientation");
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
     * Restricts the test to a subset of rows (for subsampling / bootstrap). Passing {@code null} uses all
     * rows. Changing the rows clears the result cache.
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

    /** @param symmetric whether to combine both orientations via ACAT (default true). Clears the cache. */
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

    /** @return the nuisance learning rate. */
    public double getLearningRate() { return learningRate; }

    /** @param maxLeafNodes the nuisance tree size. Clears the cache. */
    public void setMaxLeafNodes(int maxLeafNodes) {
        if (maxLeafNodes < 2) throw new IllegalArgumentException("maxLeafNodes must be >= 2");
        this.maxLeafNodes = maxLeafNodes; cache.clear();
    }

    /** @return the nuisance tree size. */
    public int getMaxLeafNodes() { return maxLeafNodes; }

    /** @param seed the base random seed for the A/B/C split. Clears the cache. */
    public void setSeed(long seed) {
        this.seed = seed ==  -1 ? RandomUtil.getInstance().nextLong() : seed;
        cache.clear();
    }

    /** @return the base random seed. */
    public long getSeed() { return seed; }

    /* ============================== internals =============================== */

    private List<Integer> listRows() {
        List<Integer> r = this.rows;
        if (r != null) return r;
        List<Integer> all = new ArrayList<>(dataSet.getNumRows());
        for (int i = 0; i < dataSet.getNumRows(); i++) all.add(i);
        return all;
    }

    /**
     * Extracts the columns, drops rows with missing values among the involved columns, and runs the
     * score-pooled engine. Tetrad {@code x} becomes CORD's modelled variable {@code Y}, Tetrad {@code y}
     * becomes CORD's {@code Z}, and the conditioning set becomes CORD's {@code X}. In symmetric mode both
     * orientations are run and their (continuous, atom-free) p-values are combined by the Cauchy/ACAT
     * rule, which is order-invariant and does not pile mass at 1.0 the way a Bonferroni {@code 2*min}
     * would.
     */
    private double cordPValue(Node xVar, Node yVar, List<Node> zVars, long factSeed) {
        int colA = indexMap.get(xVar.getName());       // Tetrad x
        int colB = indexMap.get(yVar.getName());       // Tetrad y
        int[] cordXCols = new int[zVars.size()];       // Tetrad z -> CORD X (conditioning)
        for (int i = 0; i < zVars.size(); i++) cordXCols[i] = indexMap.get(zVars.get(i).getName());

        List<Integer> use = listRows();
        int nAll = use.size();
        double[] colAv = new double[nAll];
        double[] colBv = new double[nAll];
        double[][] cordX = new double[nAll][cordXCols.length];

        int n = 0;
        for (int rr = 0; rr < nAll; rr++) {
            int row = use.get(rr);
            double av = dataSet.getDouble(row, colA);
            double bv = dataSet.getDouble(row, colB);
            boolean ok = !Double.isNaN(av) && !Double.isNaN(bv);
            double[] xrow = new double[cordXCols.length];
            for (int j = 0; ok && j < cordXCols.length; j++) {
                xrow[j] = dataSet.getDouble(row, cordXCols[j]);
                if (Double.isNaN(xrow[j])) ok = false;
            }
            if (!ok) continue;
            colAv[n] = av; colBv[n] = bv; cordX[n] = xrow; n++;
        }
        if (n < 3 * nLevels) {
            // Too few complete rows to split three ways with K levels; treat as no evidence.
            return Double.NaN;
        }
        colAv = java.util.Arrays.copyOf(colAv, n);
        colBv = java.util.Arrays.copyOf(colBv, n);
        cordX = java.util.Arrays.copyOf(cordX, n);

        // Orientation 1: model Tetrad x (CORD Y = colA, CORD Z = colB).
        double pForward = runEngine(cordX, colAv, colBv, factSeed);
        if (!symmetric) return pForward;

        // Orientation 2: model Tetrad y (CORD Y = colB, CORD Z = colA); same partition (same seed).
        double pReverse = runEngine(cordX, colBv, colAv, factSeed);
        return acatCombine(pForward, pReverse);
    }

    /** Runs the selected score-pooled engine on one orientation; returns its one-sided p, or NaN if degenerate. */
    private double runEngine(double[][] cordX, double[] cordY, double[] cordZ, long seed) {
        return switch (this.cordEngine) {
            case 1 -> {
                CordEngine1 cord = new CordEngine1();
                cord.numThresholds = nLevels;
                cord.numEstimators = nEstimators;
                cord.learningRate = learningRate;
                cord.maxLeafNodes = maxLeafNodes;
                cord.seed = seed;
                CordEngine1.Result r = cord.test(cordX, cordY, cordZ);
                yield "ok".equals(r.status) ? r.pvalue : Double.NaN;
            }
            case 2 -> {
                CordEngine2 cord = new CordEngine2();
                cord.numThresholds = nLevels;
                cord.numEstimators = nEstimators;
                cord.learningRate = learningRate;
                cord.maxLeafNodes = maxLeafNodes;
                cord.seed = seed;
                CordEngine2.Result r = cord.test(cordX, cordY, cordZ);
                yield "ok".equals(r.status) ? r.pvalue : Double.NaN;
            }
            case 3 -> {
                CordEngine3 cord = new CordEngine3();
                cord.numThresholds = nLevels;
                cord.numEstimators = nEstimators;
                cord.learningRate = learningRate;
                cord.maxLeafNodes = maxLeafNodes;
                cord.seed = seed;
                CordEngine3.Result r = cord.test(cordX, cordY, cordZ);
                yield "ok".equals(r.status) ? r.pvalue : Double.NaN;
            }
            case 4 -> {
                CordEngine4 cord = new CordEngine4();
                cord.numThresholds = nLevels;
                cord.numEstimators = nEstimators;
                cord.learningRate = learningRate;
                cord.maxLeafNodes = maxLeafNodes;
                cord.seed = seed;
                // Engine-4-specific knobs; wire these to test-class fields if you expose them,
                // otherwise engine 4 uses its own defaults (S=5, M=5, B=999, min-p combine).
                // cord.numRepeats   = cordNumRepeats;
                // cord.numFolds     = cordNumFolds;
                // cord.numBootstrap = cordNumBootstrap;
                // cord.combine      = cordCombine;
                CordEngine4.Result r = cord.test(cordX, cordY, cordZ);
                yield "ok".equals(r.status) ? r.pvalue : Double.NaN;
            }
            case 5 -> {
                CordEngine5 cord = new CordEngine5();
                cord.numThresholds = nLevels;
                cord.numEstimators = nEstimators;
                cord.learningRate = learningRate;
                cord.maxLeafNodes = maxLeafNodes;
                cord.seed = seed;
                // Engine-5-specific knobs; wire these to test-class fields if you expose them,
                // otherwise engine 5 uses its own defaults (S=4, M=5, B=999, min-p combine,
                // aggregation = "median" -- the original CORD p-value-level rule).
                // cord.numRepeats   = cordNumRepeats;
                // cord.numFolds     = cordNumFolds;
                // cord.numBootstrap = cordNumBootstrap;
                // cord.combine      = cordCombine;      // per-rotation: "minp" | "mean" | "max"
                // cord.aggregation  = cordAggregation;  // across rotations: "median" | "adaptive" | "cauchy"
                CordEngine5.Result r = cord.test(cordX, cordY, cordZ);
                yield "ok".equals(r.status) ? r.pvalue : Double.NaN;
            }
            default -> {
                throw new IllegalArgumentException("Invalid engine version: " + this.cordEngine);
            }
        };
    }

    /**
     * Cauchy/ACAT combination of two p-values testing the same null (Liu &amp; Xie, 2020). Symmetric in
     * its arguments, valid under arbitrary dependence in the tail, and continuous (no atom at 1.0). If one
     * input is NaN (degenerate orientation) the other is returned; if both are NaN the result is NaN.
     */
    private static double acatCombine(double pa, double pb) {
        boolean na = Double.isNaN(pa), nb = Double.isNaN(pb);
        if (na && nb) return Double.NaN;
        if (na) return pb;
        if (nb) return pa;
        double t = 0.5 * (cauchyTan(pa) + cauchyTan(pb));
        double p = 0.5 - Math.atan(t) / Math.PI;
        if (p <= 0.0) p = Double.MIN_VALUE;      // extreme-significant guard
        if (p > 1.0) p = 1.0;
        return p;
    }

    private static double cauchyTan(double p) {
        double pc = Math.min(Math.max(p, 1e-15), 1.0 - 1e-15);
        return Math.tan((0.5 - pc) * Math.PI);
    }

    /**
     * Sets the cord engine version.
     * @param cordEngine 1, 2, or 3; defx   ault 3.
     */
    public void setCordEngine(int cordEngine) {
        if (cordEngine < 1 || cordEngine > 3) {
            throw new IllegalArgumentException("Invalid CordEngine version");
        }

        this.cordEngine = cordEngine;
    }
}
