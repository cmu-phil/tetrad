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
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.ConditionalGaussianLikelihood;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;

/**
 * Performs a test of conditional independence X _||_ Y | Z1...Zn where all variables are either continuous or
 * discrete. This test is valid for both ordinal and non-ordinal discrete variables.
 * <p>
 * Assumes a conditional Gaussian model and uses a likelihood ratio test.
 *
 * Drop-in replacement note:
 * - setRows(...) now respects the provided argument (no random subsampling).
 * - Row restriction is intersected with testwise deletion for the specific (x, y | z) variables.
 * - Caches are cleared when row restrictions or discretization knobs change.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class IndTestConditionalGaussianLrt implements IndependenceTest, RowsSettable {

    /** The data set. */
    private final DataSet data;

    /** A hash of nodes to indices. */
    private final Map<Node, Integer> nodesHash;

    /** Likelihood function. */
    private final ConditionalGaussianLikelihood likelihood;

    /** The significance level of the independence tests. */
    private double alpha;

    /** True if verbose output should be printed. */
    private boolean verbose;

    /** The number of categories to discretize continuous variables into. */
    private int numCategoriesToDiscretize = 3;

    /** The minimum sample size per cell for discretization. */
    private int minSampleSizePerCell = 4;

    /**
     * Optional user-specified row restriction (indices into the original DataSet).
     * If null, all rows are eligible (subject to testwise deletion).
     */
    private List<Integer> rows = null;

    /** The most recent p-value. */
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
     * Returns an independence result that states whether x _||_ y | z and what the p-value of the test is.
     *
     * @param x  a {@link edu.cmu.tetrad.graph.Node} object
     * @param y  a {@link edu.cmu.tetrad.graph.Node} object
     * @param _z a {@link java.util.Set} object
     * @return a {@link edu.cmu.tetrad.search.test.IndependenceResult} object
     * @see IndependenceResult
     */
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> _z) {
        // Normalize key: same (x,y|z) fact should map consistently.
        IndependenceFact fact = new IndependenceFact(x, y, _z);

        this.likelihood.setNumCategoriesToDiscretize(this.numCategoriesToDiscretize);
        this.likelihood.setMinSampleSizePerCell(this.minSampleSizePerCell);

        List<Node> z = new ArrayList<>(_z);
        Collections.sort(z);

        List<Node> allVars = new ArrayList<>(z.size() + 2);
        allVars.addAll(z);
        allVars.add(x);
        allVars.add(y);

        // Apply row restriction + testwise deletion (for these vars) and pass to likelihood.
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

        double pValue;

        if (Double.isNaN(lik_diff)) {
            throw new RuntimeException("Undefined likelihood encountered for test: "
                                       + LogUtilsSearch.independenceFact(x, y, _z));
        } else {
            double x1 = -2 * lik_diff;
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

        if (this.verbose && independent) {
            TetradLogger.getInstance().log(
                    LogUtilsSearch.independenceFactMsg(x, y, _z, pValue));
        }

        return new IndependenceResult(fact, independent, pValue, getAlpha() - pValue);
    }

    /**
     * Returns the probability associated with the most recently executed independence test, or Double.NaN if p value is
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
        if (alpha < 0.0 || alpha > 1.0) {
            throw new IllegalArgumentException("Alpha must be between 0.0 and 1.0.");
        }
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
     * Sets the minimum sample size per cell for the independence test.
     *
     * @param minSampleSizePerCell The minimum sample size per cell.
     */
    public void setMinSampleSizePerCell(int minSampleSizePerCell) {
        this.minSampleSizePerCell = minSampleSizePerCell;
    }

    /**
     * Returns a list of row indices eligible for this test:
     *   (user-specified subset OR all rows) intersected with rows that have no missingness
     *   in the specific variables involved in this test (testwise deletion).
     */
    private List<Integer> getRows(List<Node> allVars, Map<Node, Integer> nodeHash) {
        if (data == null) return Collections.emptyList();

        final List<Integer> candidateRows;
        if (this.rows != null) {
            candidateRows = this.rows;
        } else {
            ArrayList<Integer> all = new ArrayList<>(data.getNumRows());
            for (int i = 0; i < data.getNumRows(); i++) all.add(i);
            candidateRows = all;
        }

        ArrayList<Integer> filtered = new ArrayList<>(candidateRows.size());

        K:
        for (int k : candidateRows) {
            for (Node node : allVars) {
                Integer colObj = nodeHash.get(node);
                if (colObj == null) {
                    // Shouldn't happen if nodes come from this.data.getVariables(), but fail loudly if it does.
                    throw new IllegalArgumentException("Unknown variable (not in dataset): " + node);
                }
                int col = colObj;

                if (node instanceof ContinuousVariable) {
                    if (Double.isNaN(data.getDouble(k, col))) continue K;
                } else if (node instanceof DiscreteVariable) {
                    if (data.getInt(k, col) == -99) continue K;
                } else {
                    throw new IllegalArgumentException("Expecting either continuous or discrete variables.");
                }
            }

            filtered.add(k);
        }

        return filtered;
    }

    /**
     * Returns the user-specified row restriction (or null if none).
     *
     * @return The current row restriction.
     */
    public List<Integer> getRows() {
        return rows;
    }

    /**
     * Allows the user to set which rows are used in the test.
     * If null is passed, all rows are eligible (subject to testwise deletion for each test).
     *
     * @param rows Row indices into the original DataSet.
     */
    @Override
    public void setRows(List<Integer> rows) {
        if (data == null) return;

        if (rows == null) {
            this.rows = null;
            return;
        }

        ArrayList<Integer> copy = new ArrayList<>(rows.size());
        for (Integer r : rows) {
            if (r == null) throw new IllegalArgumentException("Row index cannot be null.");
            if (r < 0 || r >= data.getNumRows()) {
                throw new IllegalArgumentException("Row index out of bounds: " + r);
            }
            copy.add(r);
        }

        this.rows = copy;
    }
}