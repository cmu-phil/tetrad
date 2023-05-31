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

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.TetradLogger;

import java.text.NumberFormat;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Checks conditional independence of variable in a continuous data set using a conditional correlation test for the
 * nonlinear nonGaussian with additive error case. This is for additive (but otherwise general) models.
 *
 * @author josephramsey
 */
public final class IndTestConditionalCorrelation implements IndependenceTest {

    /**
     * The instance of CCI that is wrapped.
     */
    private final ConditionalCorrelationIndependence cci;

    /**
     * The variables of the covariance data, in order. (Unmodifiable list.)
     */
    private final List<Node> variables;

    /**
     * The significance level of the independence tests.
     */
    private double alpha;

    /**
     * Formats as 0.0000.
     */
    private static final NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

    /**
     * Stores a reference to the data set passed in through the constructor.
     */
    private final DataSet dataSet;

    /**
     * True if verbose output should be printed.
     */
    private boolean verbose;
    private double score = Double.NaN;

    //==========================CONSTRUCTORS=============================//

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
        this.alpha = alpha;
        this.dataSet = dataSet;
    }

    //==========================PUBLIC METHODS=============================//

    /**
     * @throws UnsupportedOperationException This method is not implemented.
     */
    public IndependenceTest indTestSubset(List<Node> vars) {
        throw new UnsupportedOperationException("This method is not implemented.");
    }

    /**
     * Checks the independence of x _||_ y | z
     *
     * @return the result.
     * @see IndependenceResult
     */
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z) {

        double score = this.cci.isIndependent(x, y, z);
        this.score = score;
        double p = this.cci.getPValue(score);
        boolean independent = p > this.alpha;

        if (this.verbose) {
            if (independent) {
                TetradLogger.getInstance().forceLogMessage(
                        LogUtilsSearch.independenceFactMsg(x, y, z, p));
            }
        }

        return new IndependenceResult(new IndependenceFact(x, y, z), independent, p, alpha - p);
    }

    /**
     * Returns the p-value of the test,
     *
     * @return The p-value.
     */
    public double getPValue() {
        return this.cci.getPValue();
    }

    /**
     * Sets the significance level at which independence judgments should be made. Affects the cutoff for partial
     * correlations to be considered statistically equal to zero.
     *
     * @param alpha The alpha level.
     */
    public void setAlpha(double alpha) {
        if (alpha < 0.0 || alpha > 1.0) {
            throw new IllegalArgumentException("Significance out of range.");
        }

        this.alpha = alpha;
        this.cci.setAlpha(alpha);
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
     * Returns the list of variables over which this independence checker is capable of determinine independence
     * relations-- that is, all the variables in the given graph or the given data set.
     *
     * @return This list.
     */
    public List<Node> getVariables() {
        return this.variables;
    }

    /**
     * @throws UnsupportedOperationException Since such code is not available.
     */
    public boolean determines(List<Node> z, Node x) throws UnsupportedOperationException {
        throw new UnsupportedOperationException("The 'determines' method is not implemented");
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
        return "Conditional Correlation, q = " + IndTestConditionalCorrelation.nf.format(getAlpha());
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
     *
     * @param verbose True if so.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Sets the number of orthogal functions to use to do the calculations.
     *
     * @param numFunctions This number.
     */
    public void setNumFunctions(int numFunctions) {
        this.cci.setNumFunctions(numFunctions);
    }

    /**
     * Returns the kernel width.
     *
     * @return This width.
     */
    public double getWidth() {
        return this.cci.getWidth();
    }

    /**
     * Returns the kernel multiplier.
     *
     * @param multiplier This multiplier.
     */
    public void setKernelMultiplier(double multiplier) {
        this.cci.setWidth(multiplier);
    }

    /**
     * Sets the kernel to be used.
     *
     * @param kernel This kernel.
     * @see ConditionalCorrelationIndependence.Kernel
     */
    public void setKernel(ConditionalCorrelationIndependence.Kernel kernel) {
        this.cci.setKernelMultiplier(kernel);
    }

    /**
     * Sets the basis used for the calculation.
     *
     * @param basis This basis.
     * @see ConditionalCorrelationIndependence.Basis
     */
    public void setBasis(ConditionalCorrelationIndependence.Basis basis) {
        this.cci.setBasis(basis);
    }

    /**
     * Sets the kernal regression sample size.
     *
     * @param size This size.
     */
    public void setKernelRegressionSampleSize(int size) {
        this.cci.setKernelRegressionSampleSize(size);
    }
}



