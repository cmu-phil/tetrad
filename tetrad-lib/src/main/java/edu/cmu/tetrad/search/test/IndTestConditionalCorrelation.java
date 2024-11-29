/// ////////////////////////////////////////////////////////////////////////////
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
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.TetradLogger;

import java.text.NumberFormat;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Checks conditional independence of variable in a continuous data set using a conditional correlation test for the
 * nonlinear nonGaussian with the additive error case. This is for additive (but otherwise general) models.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public final class IndTestConditionalCorrelation implements IndependenceTest, RowsSettable {
    /**
     * The number format used for formatting numbers in the application. It is obtained from the application-wide
     * NumberFormatUtil instance.
     */
    private static final NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
    /**
     * The instance of CCI that is wrapped.
     */
    private final ConditionalCorrelationIndependence cci;
    /**
     * The variables of the covariance data, in order. (Unmodifiable list.)
     */
    private final List<Node> variables;
    /**
     * Stores a reference to the data set passed in through the constructor.
     */
    private final DataSet dataSet;
    /**
     * The bandwidth adjustment factor.
     */
    private double bandwidthAdjustment = 2.0;
    /**
     * The significance level of the independence tests.
     */
    private double alpha;
    /**
     * True if verbose output should be printed.
     */
    private boolean verbose;
    /**
     * True if permutation test should be used.
     */
    private boolean usePermutation;

    /**
     * Constructs a new Independence test which checks independence facts based on the correlation data implied by the
     * given data set (must be continuous). The given significance level is used.
     *
     * @param dataSet A data set containing only continuous columns.
     * @param alpha   The q level of the test.
     */
    public IndTestConditionalCorrelation(DataSet dataSet, double alpha) {
        if (!(dataSet.isContinuous())) {
            throw new IllegalArgumentException("Data set must be continuous.");
        }

        if (!(alpha >= 0 && alpha <= 1)) {
            throw new IllegalArgumentException("Q mut be in [0, 1]");
        }

        List<Node> nodes = dataSet.getVariables();

        this.variables = Collections.unmodifiableList(nodes);

        this.cci = new ConditionalCorrelationIndependence(dataSet);
        this.cci.setBandwidthAdjustment(this.bandwidthAdjustment);
        this.alpha = alpha;
        this.dataSet = dataSet;
    }

    /**
     * Constructs a new Independence test which checks independence facts based on the correlation data implied by the
     * given data set (must be continuous). The given significance level is used.
     */
    public IndependenceTest indTestSubset(List<Node> vars) {
        throw new UnsupportedOperationException("This method is not implemented.");
    }

    /**
     * Checks the independence of x _||_ y | z
     *
     * @param x a {@link edu.cmu.tetrad.graph.Node} object
     * @param y a {@link edu.cmu.tetrad.graph.Node} object
     * @param z a {@link java.util.Set} object
     * @return a {@link edu.cmu.tetrad.search.test.IndependenceResult} object
     * @see IndependenceResult
     */
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z) {
        double p;

        if (usePermutation) {
            p = this.cci.permutationTest(x, y, z, 20);
        } else {
            double score = this.cci.isIndependent(x, y, z);
            p = cci.getPValue(score);
        }

        if (Double.isNaN(p)) {
            throw new RuntimeException("Undefined p-value encountered for test: " + LogUtilsSearch.independenceFact(x, y, z));
        }

        boolean independent = p > this.alpha;

        if (this.verbose) {
            if (independent) {
                TetradLogger.getInstance().log(LogUtilsSearch.independenceFactMsg(x, y, z, p));
            }
        }

        return new IndependenceResult(new IndependenceFact(x, y, z), independent, p, alpha - p);
    }

    /**
     * Returns the model significance level.
     *
     * @return This level.
     */
    public double getAlpha() {
        return this.alpha;
    }

    /**
     * Sets the significance level at which independence judgments should be made. Affects the cutoff for partial
     * correlations to be considered statistically equal to zero.
     */
    public void setAlpha(double alpha) {
        if (alpha < 0.0 || alpha > 1.0) {
            throw new IllegalArgumentException("Significance out of range.");
        }

        this.alpha = alpha;
    }

    /**
     * Returns the list of variables over which this independence checker is capable of determining independence
     * relations-- that is, all the variables in the given graph or the given data set.
     *
     * @return This list.
     */
    public List<Node> getVariables() {
        return this.variables;
    }

    /**
     * Returns the data set being analyzed.
     *
     * @return This dataset.
     */
    public DataSet getData() {
        return this.dataSet;
    }

    /**
     * Returns a string representation of this test.
     *
     * @return This string.
     */
    public String toString() {
        var nf = NumberFormatUtil.getInstance().getNumberFormat();
        return "Conditional Correlation, numFunctions=" + cci.getNumFunctions()
               + ", bandwidthAdjustment=" + nf.format(cci.getBandwidthAdjustment());
    }

    /**
     * Returns true if verbose output should be printed.
     *
     * @return True if the case.
     */
    public boolean isVerbose() {
        return this.verbose;
    }

    /**
     * Sets whether verbose output should be printed.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Sets the number of orthogonal functions to use to do the calculations.
     *
     * @param numFunctions This number.
     */
    public void setNumFunctions(int numFunctions) {
        this.cci.setNumFunctions(numFunctions);
    }

    /**
     * Returns the number of orthogonal functions used to do the calculations. The sets used is the polynomial basis
     * functions, x, x^2, x^3, etc. This choice is made to allow for more flexible domains of the functions after
     * standardization.
     *
     * @return This number.
     */
    @Override
    public List<Integer> getRows() {
        return cci.getRows();
    }

    /**
     * Sets the rows to use for the test.
     *
     * @param rows The rows.
     */
    @Override
    public void setRows(List<Integer> rows) {
        cci.setRows(rows);
    }

    /**
     * Sets the bandwidth adjustment factor.
     *
     * @param bandwidthAdjustment The bandwidth adjustment factor.
     */
    public void setBandwidthAdjustment(double bandwidthAdjustment) {
        this.bandwidthAdjustment = bandwidthAdjustment;
    }

    /**
     * Sets whether to use permutation methods in the independence test.
     *
     * @param usePermutation A boolean flag indicating if permutation should be used (true) or not (false).
     */
    public void setUsePermutation(boolean usePermutation) {
        this.usePermutation = usePermutation;
    }
}



