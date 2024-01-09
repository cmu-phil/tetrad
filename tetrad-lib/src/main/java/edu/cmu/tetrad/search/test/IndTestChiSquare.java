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
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.TetradLogger;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Checks the conditional independence X _||_ Y | S, where S is a set of discrete variable, and X and Y are discrete
 * variable not in S, by applying a conditional Chi Square test. A description of such a test is given in Fienberg, "The
 * Analysis of Cross-Classified Categorical Data," 2nd edition. The formulas for the degrees of freedom used in this
 * test are equivalent to the formulation on page 142 of Fienberg.
 *
 * @author josephramsey
 * @see ChiSquareTest
 */
public final class IndTestChiSquare implements IndependenceTest, RowsSettable {

    /**
     * The Chi Square tester.
     */
    private final ChiSquareTest chiSquareTest;

    /**
     * The variables in the discrete data sets for which conditional independence judgments are desired.
     */
    private final List<Node> variables;

    /**
     * The dataset of discrete variables.
     */
    private final DataSet dataSet;

    /**
     * The G Square value associated with a particular call of isIndependent. Set in that method and not in the
     * constructor.
     */
    private double xSquare;

    /**
     * The degrees of freedom associated with a particular call of isIndependent. Set in the method and not in the
     * constructor.
     */
    private int df;

    private int minSumRowOrCol = 0;

    private boolean verbose;
    private List<Integer> rows = null;

    /**
     * Constructs a new independence checker to check conditional independence facts for discrete data using a g square
     * test.
     *
     * @param dataSet the discrete data set.
     * @param alpha   the significance level of the tests.
     */
    public IndTestChiSquare(DataSet dataSet, double alpha) {

        // The g square test requires as parameters: (a) the data set
        // itself, (b) an array containing the number of values for
        // each variable in order, and (c) the significance level of
        // the test. Also, in order to perform specific conditional
        // independence tests, it is necessary to construct an array
        // containing the variables of the requested test, in
        // order. Specifically, to test whether X _||_ Y | Z1, ...,
        // Zn, an array is constructed with the indices, in order of
        // X, Y, Z1, ..., Zn. Therefore, the indices of these
        // variables must be stored. We do this by storing the
        // variables themselves in a List.
        this.dataSet = dataSet;

        this.variables = new ArrayList<>(dataSet.getVariables());
        this.chiSquareTest = new ChiSquareTest(dataSet, alpha, ChiSquareTest.TestType.CHI_SQUARE);
        this.chiSquareTest.setMinSumRowOrCol(minSumRowOrCol);
    }

    /**
     * Creates a new IndTestChiSquare for a subset of the nodes.
     *
     * @param nodes This list of nodes.
     */
    public IndependenceTest indTestSubset(List<Node> nodes) {
        if (nodes.isEmpty()) {
            throw new IllegalArgumentException("Subset may not be empty.");
        }

        for (Node variable : nodes) {
            if (!this.variables.contains(variable)) {
                throw new IllegalArgumentException(
                        "All nodes must be original nodes");
            }
        }

        int[] indices = new int[nodes.size()];
        int j = -1;

        for (int i = 0; i < this.variables.size(); i++) {
            if (!nodes.contains(this.variables.get(i))) {
                continue;
            }

            indices[++j] = i;
        }

        DataSet newDataSet = this.dataSet.subsetColumns(indices);
        double alpha = this.chiSquareTest.getAlpha();
        return new IndTestChiSquare(newDataSet, alpha);
    }

    /**
     * Returns the chi Square value.
     *
     * @return This value.
     */
    public double getChiSquare() {
        return this.xSquare;
    }

    /**
     * Returns the degrees of freedom associated with the most recent call of isIndependent.
     *
     * @return These degrees.
     */
    public int getDf() {
        return this.df;
    }

    /**
     * Determines whether variable x is independent of variable y given a list of conditioning varNames z.
     *
     * @return True iff x _||_ y | z.
     */
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> _z) {
        if (_z == null) {
            throw new NullPointerException();
        }

        for (Node v : _z) {
            if (v == null) {
                throw new NullPointerException();
            }
        }

        List<Node> z = new ArrayList<>(_z);
        Collections.sort(z);

        // For testing x, y given z1,...,zn, set up an array of length
        // n + 2 containing the indices of these variables in order.
        int[] testIndices = new int[2 + z.size()];

        testIndices[0] = this.variables.indexOf(x);
        testIndices[1] = this.variables.indexOf(y);

        for (int i = 0; i < z.size(); i++) {
            testIndices[i + 2] = this.variables.indexOf(z.get(i));
        }

        // the following is not great code--need a better test
        for (int i = 0; i < testIndices.length; i++) {
            if (testIndices[i] < 0) {
                throw new IllegalArgumentException("Variable " + i +
                        " was not used in the constructor.");
            }
        }

        ChiSquareTest.Result result = this.chiSquareTest.calcChiSquare(testIndices);
        this.xSquare = result.getXSquare();
        this.df = result.getDf();
        double pValue = result.getPValue();

        if (verbose) {
            if (result.isIndep()) {
                TetradLogger.getInstance().forceLogMessage(
                        LogUtilsSearch.independenceFactMsg(x, y, _z, pValue));
            }
        }

        IndependenceFact fact = new IndependenceFact(x, y, _z);
        return new IndependenceResult(fact, result.isIndep(), result.getPValue(), getAlpha() - pValue);
    }

    /**
     * Returns True if the variables z determining the variable z.
     *
     * @param z The list of variables z1,...,zn with respect to which we want to know whether z determines x oir z.
     * @param x The one variable whose determination by z we want to know.
     * @return true if it is estimated that z determines x or z determines y.
     */
    public boolean determines(List<Node> z, Node x) {
        if (z == null) {
            throw new NullPointerException();
        }

        for (Node aZ : z) {
            if (aZ == null) {
                throw new NullPointerException();
            }
        }

        // For testing x, y given z1,...,zn, set up an array of length
        // n + 2 containing the indices of these variables in order.
        int[] testIndices = new int[1 + z.size()];
        testIndices[0] = this.variables.indexOf(x);

        for (int i = 0; i < z.size(); i++) {
            testIndices[i + 1] = this.variables.indexOf(z.get(i));
        }

        // the following is not great code--need a better test
        for (int i = 0; i < testIndices.length; i++) {
            if (testIndices[i] < 0) {
                throw new IllegalArgumentException(
                        "Variable " + i + "was not used in the constructor.");
            }
        }

        //        System.out.println("Testing " + x + " _||_ " + y + " | " + z);

        boolean countDetermined =
                this.chiSquareTest.isDetermined(testIndices, getDeterminationP());

        if (countDetermined) {
            StringBuilder sb = new StringBuilder();
            sb.append("Determination found: ").append(x).append(
                    " is determined by {");

            for (int i = 0; i < z.size(); i++) {
                sb.append(z.get(i));

                if (i < z.size() - 1) {
                    sb.append(", ");
                }
            }

            sb.append("}");

            TetradLogger.getInstance().log("independencies", sb.toString());
        }

        return countDetermined;
    }

    /**
     * Returns the alpha significance level of the test.
     *
     * @return This level.
     */
    public double getAlpha() {
        return this.chiSquareTest.getAlpha();
    }

    /**
     * Sets the significance level at which independence judgments should be made.  Affects the cutoff for partial
     * correlations to be considered statistically equal to zero.
     *
     * @param alpha the new significance level.
     */
    public void setAlpha(double alpha) {
        this.chiSquareTest.setAlpha(alpha);
    }

    /**
     * Returns the list of variables over which this independence checker is capable of determining independence
     * relations-- that is, all the variables in the given graph or the given data set.
     *
     * @return This list.
     */
    public List<Node> getVariables() {
        return Collections.unmodifiableList(this.variables);
    }

    /**
     * Returns a string representation of this test.
     *
     * @return This string.
     */
    public String toString() {
        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
        return "Chi Square, alpha = " + nf.format(getAlpha());
    }

    /**
     * Returns the data being analyzed.
     *
     * @return This data.
     */
    public DataSet getData() {
        return this.dataSet;
    }

    /**
     * Returns true if verbose output should be printed.
     *
     * @return This.
     */
    @Override
    public boolean isVerbose() {
        return this.verbose;
    }

    /**
     * Sets whether verbose output should be printed.
     *
     * @param verbose True, if so.
     */
    @Override
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    private double getDeterminationP() {
        /*
         * The lower bound of percentages of observation of some category in the data, given some particular combination of
         * values of conditioning variables, that coefs as 'determining.'
         */
        return 0.99;
    }

    /**
     * The minimum number of counts per conditional table for chi-square expressed as a fraction of the total number of
     * cells in the conditional table. Default is 2. Note that this should not be too small, or the chi-square
     * distribution will not be a good approximation to the distribution of the test statistic.
     *
     * @param minSumRowOrCol The minimum number of counts per conditional table expressed as a fraction of the total
     *                         number of cells in the conditional table.
     */
    public void setMinSumRowOrCol(int minSumRowOrCol) {
        this.minSumRowOrCol = minSumRowOrCol;
        this.chiSquareTest.setMinSumRowOrCol(minSumRowOrCol);
    }

    /**
     * Returns the rows used for the test. If null, all rows are used.
     * @return The rows used for the test. Can be null.
     */
    @Override
    public List<Integer> getRows() {
        return new ArrayList<>(rows);
    }

    /**
     * Sets the rows to use for the test. If null, all rows are used.
     * @param rows The rows to use for the test. Can be null.
     */
    @Override
    public void setRows(List<Integer> rows) {
        if (rows == null) {
            this.rows = null;
            chiSquareTest.setRows(null);
        } else {
            for (int i : rows) {
                if (i < 0 || i >= dataSet.getNumRows()) {
                    throw new IllegalArgumentException("Row " + i + " is out of bounds.");
                }
            }

            this.rows = new ArrayList<>(rows);
            chiSquareTest.setRows(this.rows);
        }
    }
}




