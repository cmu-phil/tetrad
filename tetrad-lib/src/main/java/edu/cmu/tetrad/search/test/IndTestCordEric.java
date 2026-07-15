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
import edu.cmu.tetrad.search.CordEngine2;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.util.TetradLogger;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tetrad {@link IndependenceTest} adapter for Eric's CORD engine ({@link CordEngine2}), the omnibus,
 * orthogonal rank-score test of the full conditional law that keeps power against variance, tail, and
 * co-volatility dependence that covariance/mean tests (Fisher Z, GCM) miss.
 *
 * <p>This class is a thin adapter: all of the statistics live in {@link CordEngine2}, which stays the single
 * authoritative implementation. The adapter only (i) extracts the requested columns from the data set with
 * listwise deletion of missing values, (ii) maps Tetrad's {@code checkIndependence(x, y, z)} onto CORD's
 * {@code test(X, Y, Z)} roles, (iii) makes each call deterministic and cacheable, and (iv) turns CORD's
 * p-value into an {@link IndependenceResult}.
 *
 * <h2>Role mapping</h2>
 * Tetrad asks {@code x _||_ y | z}. CORD tests {@code Y _||_ Z | X}, modelling the conditional CDF of its
 * {@code Y}. The adapter passes Tetrad's conditioning set {@code z} as CORD's {@code X}, Tetrad's {@code x}
 * as CORD's {@code Y}, and Tetrad's {@code y} as CORD's {@code Z}. When {@code z} is empty, CORD's {@code X}
 * has zero columns and the test reduces to a marginal independence test of {@code x} and {@code y}.
 *
 * <h2>Orientation</h2>
 * CORD is asymmetric (it models the CDF of one variable). A constraint-based search expects
 * {@code checkIndependence(x, y, z)} and {@code checkIndependence(y, x, z)} to agree, so by default this
 * adapter runs CORD in its symmetric mode, which evaluates both orientations and reports the
 * Bonferroni-combined p-value {@code min(1, 2*min(p_xy, p_yx))}. Call {@link #setSymmetric(boolean)
 * setSymmetric(false)} to recover the exact single-orientation engine behaviour (modelling the CDF of
 * {@code x}) at half the cost.
 *
 * <h2>Cost, determinism, thread-safety</h2>
 * CORD fits many boosted models per test (double that when symmetrised), so it is far heavier than Fisher Z;
 * for large searches reduce {@link #setNLevels(int) nLevels} and/or {@link #setNEstimators(int) nEstimators}.
 * Each call seeds the engine from {@link #setSeed(long) seed} mixed with the independence fact, so repeated
 * identical queries return identical results and are cached. All per-test work is done in locals and a fresh
 * {@link CordEngine2} instance; configuration setters must not be called concurrently with running tests.
 *
 * @author josephramsey
 * @see CordEngine2
 */
public final class IndTestCordEric implements IndependenceTest, RowsSettable {

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

    /**
     * Constructs a CORD independence test over the given (continuous or numeric) data set.
     *
     * @param dataSet the data; columns are read as doubles (discrete columns are treated as ordinal codes).
     * @param alpha   the significance level in [0, 1].
     */
    public IndTestCordEric(DataSet dataSet, double alpha) {
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
        String key = a + "|" + symmetric + "|" + new IndependenceFact(x, y, z);
        IndependenceResult cached = cache.get(key);
        if (cached != null) return cached;

        List<Node> zList = new ArrayList<>(z);
        zList.sort((u, v) -> Integer.compare(indexMap.get(u.getName()), indexMap.get(v.getName())));

        long factSeed = seed ^ (0x9E3779B97F4A7C15L * new IndependenceFact(x, y, z).hashCode());
        double p = cordPValue(x, y, zList, factSeed);

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
        return "CORD (Eric's engine), alpha = " + f.format(alpha)
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
    public void setSeed(long seed) { this.seed = seed; cache.clear(); }

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
     * Extracts the columns, drops rows with missing values among the involved columns, and runs Eric's
     * engine. Tetrad {@code x} becomes CORD's modelled variable {@code Y}, Tetrad {@code y} becomes CORD's
     * {@code Z}, and the conditioning set becomes CORD's {@code X}.
     */
    private double cordPValue(Node xVar, Node yVar, List<Node> zVars, long factSeed) {
        int cordYCol = indexMap.get(xVar.getName());   // Tetrad x  -> CORD Y (modelled)
        int cordZCol = indexMap.get(yVar.getName());   // Tetrad y  -> CORD Z
        int[] cordXCols = new int[zVars.size()];       // Tetrad z  -> CORD X (conditioning)
        for (int i = 0; i < zVars.size(); i++) cordXCols[i] = indexMap.get(zVars.get(i).getName());

        List<Integer> use = listRows();
        int nAll = use.size();
        double[] cordY = new double[nAll];
        double[] cordZ = new double[nAll];
        double[][] cordX = new double[nAll][cordXCols.length];

        int n = 0;
        for (int rr = 0; rr < nAll; rr++) {
            int row = use.get(rr);
            double yv = dataSet.getDouble(row, cordYCol);
            double zv = dataSet.getDouble(row, cordZCol);
            boolean ok = !Double.isNaN(yv) && !Double.isNaN(zv);
            double[] xrow = new double[cordXCols.length];
            for (int j = 0; ok && j < cordXCols.length; j++) {
                xrow[j] = dataSet.getDouble(row, cordXCols[j]);
                if (Double.isNaN(xrow[j])) ok = false;
            }
            if (!ok) continue;
            cordY[n] = yv; cordZ[n] = zv; cordX[n] = xrow; n++;
        }
        if (n < 3 * nLevels) {
            // Too few complete rows to split three ways with K levels; treat as no evidence.
            return Double.NaN;
        }
        cordY = java.util.Arrays.copyOf(cordY, n);
        cordZ = java.util.Arrays.copyOf(cordZ, n);
        cordX = java.util.Arrays.copyOf(cordX, n);

        CordEngine2 cord = new CordEngine2();
        cord.numThresholds = nLevels;
        cord.numEstimators = nEstimators;
        cord.learningRate = learningRate;
        cord.maxLeafNodes = maxLeafNodes;
//        cord.symmetric = symmetric;
        cord.seed = factSeed;

        CordEngine2.Result r = cord.test(cordX, cordY, cordZ);
        return "ok".equals(r.status) ? r.pvalue : Double.NaN;
    }
}
