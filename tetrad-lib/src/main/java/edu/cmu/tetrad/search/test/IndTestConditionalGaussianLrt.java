///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.score.ConditionalGaussianLikelihood;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.Double.NaN;

/**
 * Performs a test of conditional independence X _||_ Y | Z1...Zn where all searchVariables are either continuous or
 * discrete. This test is valid for both ordinal and non-ordinal discrete searchVariables.
 * <p>
 * Assumes a conditional Gaussian model and uses a likelihood ratio test.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class IndTestConditionalGaussianLrt implements IndependenceTest, RowsSettable {
    /**
     * The data set.
     */
    private final DataSet data;
    /**
     * A hash of nodes to indices.
     */
    private final Map<Node, Integer> nodesHash;
    /**
     * Likelihood function
     */
    private final ConditionalGaussianLikelihood likelihood;
    /**
     * A cache of results for independence facts.
     */
    private final Map<IndependenceFact, IndependenceResult> facts = new ConcurrentHashMap<>();
    /**
     * The significance level of the independence tests.
     */
    private double alpha;
    /**
     * True if verbose output should be printed.
     */
    private boolean verbose;
    /**
     * The number of categories to discretize continuous variables into.
     */
    private int numCategoriesToDiscretize = 3;
    /**
     * The minimum sample size per cell for discretization.
     */
    private int minSampleSizePerCell = 4;
    /**
     * The rows used in the test.
     */
    private List<Integer> rows = null;
    private double pValue;

    /**
     * Constructor.
     *
     * @param data       The data to analyze.
     * @param alpha      The significance level.
     * @param discretize Whether discrete children of continuous parents should be discretized.
     */
    public IndTestConditionalGaussianLrt(DataSet data, double alpha, boolean discretize) {
        this.data = data;
        this.likelihood = new ConditionalGaussianLikelihood(data);
        this.likelihood.setDiscretize(discretize);
        this.nodesHash = new HashMap<>();

        List<Node> variables = data.getVariables();

        for (int i = 0; i < variables.size(); i++) {
            this.nodesHash.put(variables.get(i), i);
        }

        this.alpha = alpha;
    }

    /**
     * This method returns an instance of the IndependenceTest interface that can test the independence of a subset of
     * variables.
     *
     * @param vars The sublist of variables to test for independence.
     * @return An instance of the IndependenceTest interface.
     * @see IndependenceTest
     */
    public IndependenceTest indTestSubset(List<Node> vars) {
        throw new UnsupportedOperationException("This method is not implemented.");
    }

    /**
     * Returns and independence result that states whether x _||_y | z and what the p-value of the test is.
     *
     * @param x  a {@link edu.cmu.tetrad.graph.Node} object
     * @param y  a {@link edu.cmu.tetrad.graph.Node} object
     * @param _z a {@link java.util.Set} object
     * @return a {@link edu.cmu.tetrad.search.test.IndependenceResult} object
     * @see IndependenceResult
     */
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> _z) {
        if (this.facts.containsKey(new IndependenceFact(x, y, _z))) {
            return facts.get(new IndependenceFact(x, y, _z));
        }

        this.likelihood.setNumCategoriesToDiscretize(this.numCategoriesToDiscretize);
        this.likelihood.setMinSampleSizePerCell(this.minSampleSizePerCell);

        List<Node> z = new ArrayList<>(_z);
        Collections.sort(z);

        List<Node> allVars = new ArrayList<>(z);
        allVars.add(x);
        allVars.add(y);

        this.likelihood.setRows(getRows(allVars, this.nodesHash));

        int _x = this.nodesHash.get(x);
        int _y = this.nodesHash.get(y);

        int[] list0 = new int[z.size()];
        int[] list1 = new int[z.size() + 1];

        list1[0] = _x;

        for (int i = 0; i < z.size(); i++) {
            int __z = this.nodesHash.get(z.get(i));
            list0[i] = __z;
            list1[i + 1] = __z;
        }

        ConditionalGaussianLikelihood.Ret ret0 = likelihood.getLikelihood(_y, list0);
        ConditionalGaussianLikelihood.Ret ret1 = likelihood.getLikelihood(_y, list1);

        double lik_diff = ret0.getLik() - ret1.getLik();
        double dof_diff = ret1.getDof() - ret0.getDof();

        if (dof_diff <= 0) return new IndependenceResult(new IndependenceFact(x, y, _z),
                false, NaN, NaN);
        if (this.alpha == 0) return new IndependenceResult(new IndependenceFact(x, y, _z),
                false, NaN, NaN);
        if (this.alpha == 1) return new IndependenceResult(new IndependenceFact(x, y, _z),
                false, NaN, NaN);
        if (lik_diff == Double.POSITIVE_INFINITY) return new IndependenceResult(new IndependenceFact(x, y, _z),
                false, NaN, NaN);

        double pValue;

        if (Double.isNaN(lik_diff)) {
            throw new RuntimeException("Undefined likelihood encountered for test: " + LogUtilsSearch.independenceFact(x, y, _z));
        } else {

            double x1 = -2 * lik_diff;
//            pValue = 1.0 - new ChiSquaredDistribution(dof_diff).cumulativeProbability(x1);

            ChiSquaredDistribution chisq = new ChiSquaredDistribution(dof_diff);

            if (Double.isInfinite(x1)) {
                pValue = 0.0;
            } else if (x1 == 0.0) {
                pValue = 1.0;
            } else {
                pValue = 1.0 - chisq.cumulativeProbability(x1);
            }
        }

        this.pValue = pValue;

        boolean independent = pValue > alpha;

        if (this.verbose) {
            if (independent) {
                TetradLogger.getInstance().log(
                        LogUtilsSearch.independenceFactMsg(x, y, _z, pValue));
            }
        }

        IndependenceResult result = new IndependenceResult(new IndependenceFact(x, y, _z), independent,
                pValue, getAlpha() - pValue);
        facts.put(new IndependenceFact(x, y, _z), result);
        return result;
    }

    /**
     * Returns the probability associated with the most recently executed independence test, of Double.NaN if p value is
     * not meaningful for this test.
     *
     * @return This p-value.
     */
    public double getPValue() {
        return this.pValue;
    }

    /**
     * Returns the list of variables over which this independence checker is capable of determining independence
     * relations.
     *
     * @return This list.
     */
    public List<Node> getVariables() {
        return new ArrayList<>(this.data.getVariables());
    }

    /**
     * Determines whether a given list of nodes (z) determines a node (y).
     *
     * @param z The list of nodes to check if they determine y.
     * @param y The node to check if it is determined by z.
     * @return True if z determines y, false otherwise.
     * @throws UnsupportedOperationException if not implemented.
     */
    public boolean determines(List<Node> z, Node y) {
        throw new UnsupportedOperationException("Determinism method not implemented.");
    }

    /**
     * Returns the significance level of the independence test.
     *
     * @return This level.
     */
    public double getAlpha() {
        return this.alpha;
    }

    /**
     * Sets the significance level.
     */
    public void setAlpha(double alpha) {
        this.alpha = alpha;
    }

    /**
     * Returns the data.
     *
     * @return This data.
     */
    public DataSet getData() {
        return this.data;
    }

    /**
     * Returns a string representation of this test.
     *
     * @return This string.
     */
    public String toString() {
        NumberFormat nf = new DecimalFormat("0.0000");
        return "Conditional Gaussian LRT, alpha = " + nf.format(getAlpha());
    }

    /**
     * Returns true iff verbose output should be printed.
     */
    @Override
    public boolean isVerbose() {
        return this.verbose;
    }

    /**
     * Sets whether verbose output should be printed.
     */
    @Override
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Sets the number of categories used to discretize variables.
     *
     * @param numCategoriesToDiscretize This number, by default 3.
     */
    public void setNumCategoriesToDiscretize(int numCategoriesToDiscretize) {
        this.numCategoriesToDiscretize = numCategoriesToDiscretize;
    }

    /**
     * Returns a list of row indices where the values of the specified nodes are not missing or invalid.
     *
     * @param allVars  The list of nodes to check.
     * @param nodeHash A map containing node-index pairs.
     * @return A list of row indices.
     */
    private List<Integer> getRows(List<Node> allVars, Map<Node, Integer> nodeHash) {
        if (this.rows != null) {
            return this.rows;
        }

        List<Integer> rows = new ArrayList<>();

        K:
        for (int k = 0; k < this.data.getNumRows(); k++) {
            for (Node node : allVars) {
                if (node instanceof ContinuousVariable) {
                    if (Double.isNaN(this.data.getDouble(k, nodeHash.get(node)))) continue K;
                } else if (node instanceof DiscreteVariable) {
                    if (this.data.getInt(k, nodeHash.get(node)) == -99) continue K;
                }
            }

            rows.add(k);
        }
        return rows;
    }

    /**
     * Returns the rows used in the test.
     *
     * @return The rows used in the test.
     */
    public List<Integer> getRows() {
        return rows;
    }

    /**
     * Allows the user to set which rows are used in the test. Otherwise, all rows are used, except those with missing
     * values.
     */
    public void setRows(List<Integer> rows) {
        if (data == null) {
            return;
        }

        List<Integer> all = new ArrayList<>();
        for (int i = 0; i < data.getNumRows(); i++) all.add(i);
        Collections.shuffle(all);

        List<Integer> _rows = new ArrayList<>();
        for (int i = 0; i < data.getNumRows() / 2; i++) {
            _rows.add(all.get(i));
        }

        for (Integer row : _rows) {
            if (row < 0 || row >= data.getNumRows()) {
                throw new IllegalArgumentException("Row index out of bounds.");
            }
        }

        this.rows = _rows;
    }

    /**
     * Sets the minimum sample size per cell for the independence test.
     *
     * @param minSampleSizePerCell The minimum sample size per cell.
     */
    public void setMinSampleSizePerCell(int minSampleSizePerCell) {
        this.minSampleSizePerCell = minSampleSizePerCell;
    }
}
