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
    private int basisType;
    /**
     * Represents the scaling factor applied to the basis functions in the independence test. This variable adjusts the
     * influence of the basis functions, which can affect the flexibility and accuracy of the independence tests
     * performed within the IndTestConditionalCorrelation class. The value is initialized to 0.0, indicating no
     * standardization by default.
     */
    private double basisScale = 0.0;
    /**
     * The bandwidth adjustment factor.
     */
    private double scalingFactor = 2.0;
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
    private boolean usePermutation = false;

    /**
     * Constructs a new Independence test which checks independence facts based on the correlation data implied by the
     * given data set (must be continuous). The given significance level is used.
     *
     * @param dataSet    A data set containing only continuous columns.
     * @param alpha      The q level of the test.
     * @param basisScale
     */
    public IndTestConditionalCorrelation(DataSet dataSet, double alpha, double scalingFactor,
                                         int basisType, int numFunctions, double basisScale) {
        if (!(dataSet.isContinuous())) {
            throw new IllegalArgumentException("Data set must be continuous.");
        }

        if (!(alpha >= 0 && alpha <= 1)) {
            throw new IllegalArgumentException("Q mut be in [0, 1]");
        }

        List<Node> nodes = dataSet.getVariables();

        this.variables = Collections.unmodifiableList(nodes);

        this.cci = new ConditionalCorrelationIndependence(dataSet, basisType, basisScale, numFunctions);
        this.cci.setScalingFactor(this.scalingFactor);
        this.alpha = alpha;
        this.scalingFactor = scalingFactor;
        this.basisType = basisType;
        this.basisScale = basisScale;
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

//        if (usePermutation) {
//            p = this.cci.permutationTest(x, y, z, 20);
//        } else {
        p = this.cci.isIndependent(x, y, z);
//            p = cci.getPValue(score);
//        }

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
        return "Conditional Correlation";
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
     * Returns the number of orthogonal functions used to do the calculations. The sets used are the polynomial basis
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
}



