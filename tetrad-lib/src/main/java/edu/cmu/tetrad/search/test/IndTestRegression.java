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
import edu.cmu.tetrad.regression.Regression;
import edu.cmu.tetrad.regression.RegressionDataset;
import edu.cmu.tetrad.regression.RegressionResult;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.Vector;

import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Checks independence of X _||_ Y | Z for variables X and Y and list Z of variables by regressing X on {Y} U Z and
 * testing whether the coefficient for Y is zero.
 *
 * @author josephramsey
 * @author Frank Wimberly
 * @version $Id: $Id
 */
public final class IndTestRegression implements IndependenceTest {

    /**
     * The standard number formatter for Tetrad.
     */
    private static final NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
    /**
     * The correlation matrix.
     */
    private final Matrix data;
    /**
     * The variables of the correlation matrix, in order. (Unmodifiable list.)
     */
    private final List<Node> variables;
    /**
     * The data set.
     */
    private final DataSet dataSet;
    /**
     * A cache of results for independence facts.
     */
    private final Map<IndependenceFact, IndependenceResult> facts = new ConcurrentHashMap<>();
    /**
     * The significance level of the independence tests.
     */
    private double alpha;
    /**
     * The value of the Fisher's Z statistic associated with the las calculated partial correlation.
     */
    private boolean verbose;

    /**
     * Constructs a new Independence test which checks independence facts based on the correlation matrix implied by the
     * given data set (must be continuous). The given significance level is used.
     *
     * @param dataSet A data set containing only continuous columns.
     * @param alpha   The alpha level of the test.
     */
    public IndTestRegression(DataSet dataSet, double alpha) {
        if (!(alpha >= 0 && alpha <= 1)) {
            throw new IllegalArgumentException("Alpha mut be in [0, 1]");
        }

        this.dataSet = dataSet;
        this.data = new Matrix(dataSet.getDoubleData().toArray());
        this.variables = Collections.unmodifiableList(dataSet.getVariables());
        setAlpha(alpha);
    }


    /**
     * Performs an independence test for a sublist of variables.
     *
     * @param vars The sublist of variables.
     * @return The independence test result.
     */
    public IndependenceTest indTestSubset(List<Node> vars) {
        return null;
    }

    /**
     * Checks the independence between two variables, given a set of conditioning variables.
     *
     * @param xVar  The first variable to test for independence.
     * @param yVar  The second variable to test for independence.
     * @param zList The set of conditioning variables.
     * @return An IndependenceResult object containing the result of the independence test.
     */
    public IndependenceResult checkIndependence(Node xVar, Node yVar, Set<Node> zList) {
        if (facts.containsKey(new IndependenceFact(xVar, yVar, zList))) {
            return facts.get(new IndependenceFact(xVar, yVar, zList));
        }

        for (Node node : zList) {
            if (node == null) {
                throw new NullPointerException();
            }
        }

        List<Node> regressors = new ArrayList<>();
        regressors.add(this.dataSet.getVariable(yVar.getName()));

        for (Node zVar : zList) {
            regressors.add(this.dataSet.getVariable(zVar.getName()));
        }

        Regression regression = new RegressionDataset(this.dataSet);
        RegressionResult result;

        try {
            result = regression.regress(xVar, regressors);
        } catch (Exception e) {
            return new IndependenceResult(new IndependenceFact(xVar, yVar, zList),
                    false, Double.NaN, Double.NaN);
        }

        double p = result.getP()[1];

        if (Double.isNaN(p)) {
            throw new RuntimeException("Undefined p-value encountered when testing " +
                                       LogUtilsSearch.independenceFact(xVar, yVar, zList));
        }

        boolean independent = p > this.alpha;

        if (this.verbose) {
            if (independent) {
                String message = LogUtilsSearch.independenceFactMsg(xVar, yVar, zList, p);
                TetradLogger.getInstance().log(message);
            } else {
                String message = LogUtilsSearch.dependenceFactMsg(xVar, yVar, zList, p);
                TetradLogger.getInstance().log(message);
            }
        }

        if (this.verbose) {
            if (independent) {
                TetradLogger.getInstance().log(
                        LogUtilsSearch.independenceFactMsg(xVar, yVar, zList, p));
            }
        }

        IndependenceResult result1 = new IndependenceResult(new IndependenceFact(xVar, yVar, zList),
                independent, p, getAlpha() - p);
        facts.put(new IndependenceFact(xVar, yVar, zList), result1);
        return result1;
    }

    /**
     * Gets the getModel significance level.
     *
     * @return a double
     */
    public double getAlpha() {
        return this.alpha;
    }

    /**
     * Sets the significance level.
     *
     * @param alpha This level.
     * @throws IllegalArgumentException if alpha is not within the range [0, 1].
     */
    public void setAlpha(double alpha) {
        if (alpha < 0.0 || alpha > 1.0) {
            throw new IllegalArgumentException("Significance out of range.");
        }

        this.alpha = alpha;
    }

    /**
     * Returns the list of variables associated with this object.
     *
     * @return the list of variables
     */
    public List<Node> getVariables() {
        return this.variables;
    }


    /**
     * Returns a string representation of the Linear Regression Test object.
     *
     * @return the string representation of the Linear Regression Test object
     */
    public String toString() {
        return "Linear Regression Test, alpha = " + IndTestRegression.nf.format(getAlpha());
    }


    /**
     * Determines if a variable xVar can be determined by a list of conditioning variables zList.
     *
     * @param zList The list of conditioning variables.
     * @param xVar  The variable to test for determination.
     * @return True if xVar is determined by zList, false otherwise.
     * @throws NullPointerException if zList or any of its elements is null.
     */
    public boolean determines(List<Node> zList, Node xVar) {
        if (zList == null) {
            throw new NullPointerException();
        }

        for (Node node : zList) {
            if (node == null) {
                throw new NullPointerException();
            }
        }

        int size = zList.size();
        int[] zCols = new int[size];

        int xIndex = getVariables().indexOf(xVar);

        for (int i = 0; i < zList.size(); i++) {
            zCols[i] = getVariables().indexOf(zList.get(i));
        }

        int[] zRows = new int[this.data.getNumRows()];
        for (int i = 0; i < this.data.getNumRows(); i++) {
            zRows[i] = i;
        }

        Matrix Z = this.data.getSelection(zRows, zCols);
        Vector x = this.data.getColumn(xIndex);
        Matrix Zt = Z.transpose();
        Matrix ZtZ = Zt.times(Z);
        Matrix G =ZtZ.inverse();

        // Bug in Colt? Need to make a copy before multiplying to avoid
        // a ClassCastException.
        Matrix Zt2 = Zt.like();
        Zt2.assign(Zt);
        Matrix GZt = G.times(Zt2);
        Vector b_x = GZt.times(x);
        Vector xPred = Z.times(b_x);
        Vector xRes = xPred.minus(x);
        double SSE = xRes.dot(xRes);// xRes.aggregate(Functions.plus, Functions.square);
        boolean determined = SSE < 0.0001;

        if (determined) {
            StringBuilder sb = new StringBuilder();
            sb.append("Determination found: ").append(xVar).append(
                    " is determined by {");

            for (int i = 0; i < zList.size(); i++) {
                sb.append(zList.get(i));

                if (i < zList.size() - 1) {
                    sb.append(", ");
                }
            }

            sb.append("}");

            TetradLogger.getInstance().log(sb.toString());
        }

        return determined;
    }

    /**
     * Returns the data used.
     *
     * @return the data used.
     */
    public DataSet getData() {
        return this.dataSet;
    }

    /**
     * Returns true if the test prints verbose output.
     *
     * @return a boolean
     */
    public boolean isVerbose() {
        return this.verbose;
    }

    /**
     * Sets whether this test will print verbose output.
     *
     * @param verbose True, if verbose output should be printed.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }
}




