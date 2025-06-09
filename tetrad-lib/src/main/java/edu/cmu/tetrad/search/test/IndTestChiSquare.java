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
import edu.cmu.tetrad.search.EffectiveSampleSizeSettable;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.TetradLogger;

import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static edu.cmu.tetrad.search.utils.GraphSearchUtils.getAllRows;

/**
 * Checks the conditional independence X _||_ Y | S, where S is a set of discrete variable, and X and Y are discrete
 * variable not in S, by applying a conditional Chi Square test. A description of such a test is given in Fienberg, "The
 * Analysis of Cross-Classified Categorical Data," 2nd edition. The formulas for the degrees of freedom used in this
 * test are equivalent to the formulation on page 142 of Fienberg.
 *
 * @author josephramsey
 * @version $Id: $Id
 * @see ChiSquareTest
 */
public final class IndTestChiSquare implements IndependenceTest, EffectiveSampleSizeSettable, RowsSettable {

    /**
     * The variables in the discrete data sets for which conditional independence judgments are desired.
     */
    private final List<Node> variables;
    /**
     * The dataset of discrete variables.
     */
    private final DataSet dataSet;
    /**
     * The Chi Square tester.
     */
    private ChiSquareTest chiSquareTest;
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
    /**
     * The minimum number of counts per conditional table for chi-square for that table and its degrees of freedom to be
     * included in the overall chi-square and degrees of freedom. Note that this should not be too small, or the
     * chi-square distribution will not be a good approximation to the distribution of the test statistic.
     */
    private double minCountPerCell = 1.0;
    /**
     * This variable represents whether verbose output should be printed.
     * <p>
     * The default value is false.
     */
    private boolean verbose;
    /**
     * Represents the list of rows to be used for a test.
     * <p>
     * This variable is used in the class "IndTestChiSquare" to specify which rows of data should be used in the
     * chi-square test. If the variable "rows" is set to null, all rows of the data will be used in the test.
     * <p>
     * The class "IndTestChiSquare" is a subclass of the "IndependenceTest" class, which is a superclass for all
     * independence tests in the Tetrad library. It also implements the "RowsSettable" interface, which allows for
     * setting the rows to be used for the test.
     *
     * @see IndTestChiSquare
     * @see IndependenceTest
     * @see RowsSettable
     */
    private List<Integer> rows = null;
    /**
     * The sample size to use for the test. If not set, this is the sample size of the dataset.
     */
    private int sampleSize;
    /**
     * The lower bound of percentages of observation of some category in the data, given some particular combination of
     * values of conditioning variables, that coefs as 'determining.'
     */
    private double determinationP = 0.99;

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
        this.chiSquareTest = new ChiSquareTest(dataSet, alpha, ChiSquareTest.TestType.CHI_SQUARE, rows);
        this.chiSquareTest.setMinCountPerCell(minCountPerCell);
        this.rows = getAllRows(dataSet.getNumRows());
        this.sampleSize = this.rows.size();
    }

    /**
     * Checks conditional independence between variables in a subset.
     *
     * @param nodes The sublist of variables.
     * @return An instance of IndependenceTest representing the test for conditional independence.
     * @throws IllegalArgumentException If the subset of variables is empty or contains non-original nodes.
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
     * @param x  a {@link edu.cmu.tetrad.graph.Node} object
     * @param y  a {@link edu.cmu.tetrad.graph.Node} object
     * @param _z a {@link java.util.Set} object
     * @return a {@link edu.cmu.tetrad.search.test.IndependenceResult} object
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

        ChiSquareTest.Result result = this.chiSquareTest.calcChiSquare(testIndices, sampleSize);

        this.xSquare = result.getXSquare();
        this.df = result.getDf();
        double pValue = result.getPValue();

        if (verbose) {
            if (result.isIndep()) {
                TetradLogger.getInstance().log(
                        LogUtilsSearch.independenceFactMsg(x, y, _z, pValue));
            }
        }

        IndependenceFact fact = new IndependenceFact(x, y, _z);
        return new IndependenceResult(fact, result.isIndep(), result.getPValue(), getAlpha() - pValue);
    }

    /**
     * Returns the pvalue if the fact of X _||_ Y | Z is within the cache of results for independence fact.
     *
     * @param x the first node
     * @param y the second node
     * @param z the set of conditioning nodes
     * @return the pValue result or null if not within the cache
     */
    public Double getPValue(Node x, Node y, Set<Node> z) {
        IndependenceResult result = checkIndependence(x, y, z);
        return result.getPValue();
    }

    /**
     * Determines whether variable x is independent of variable y given a list of conditioning nodes.
     *
     * @param z The list of conditioning nodes.
     * @param x The variable x.
     * @return True if variable x is determined by the list of conditioning nodes, false otherwise.
     * @throws NullPointerException     if z or any node in z is null.
     * @throws IllegalArgumentException if any node in z is not used in the constructor.
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

            TetradLogger.getInstance().log(sb.toString());
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
     * Checks if the verbosity flag is enabled.
     *
     * @return true if the verbosity flag is enabled, false otherwise
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
     * Returns the lower bound of percentages of observation of some category in the data, given some particular
     * combination of values of conditioning variables, that coefs as 'determining.'
     *
     * @return The lower bound of percentages of observation.
     */
    private double getDeterminationP() {
        /*
         * The lower bound of percentages of observation of some category in the data, given some particular combination of
         * values of conditioning variables, that coefs as 'determining.'
         */
        return 0.99;
    }

    /**
     * The minimum number of counts per conditional table for chi-square for that table and its degrees of freedom to be
     * included in the overall chi-square and degrees of freedom. Note that this should not be too small, or the
     * chi-square distribution will not be a good approximation to the distribution of the test statistic.
     *
     * @param minCountPerCell The minimum number of counts per conditional table. The default is 1; this must be >= 0.
     */
    public void setMinCountPerCell(double minCountPerCell) {
        this.minCountPerCell = minCountPerCell;
        this.chiSquareTest.setMinCountPerCell(minCountPerCell);
    }

    /**
     * Returns the rows used for the test. If null, all rows are used.
     */
    @Override
    public List<Integer> getRows() {
        return new ArrayList<>(rows);
    }

    /**
     * Sets the rows to use for the test. If null, all rows are used.
     */
    @Override
    public void setRows(List<Integer> rows) {
        if (rows == null) {
            this.rows = null;
            chiSquareTest = new ChiSquareTest(dataSet, chiSquareTest.getAlpha(), ChiSquareTest.TestType.CHI_SQUARE, rows);
        } else {
            for (int i : rows) {
                if (i < 0 || i >= dataSet.getNumRows()) {
                    throw new IllegalArgumentException("Row " + i + " is out of bounds.");
                }
            }

            this.rows = new ArrayList<>(rows);
            chiSquareTest = new ChiSquareTest(dataSet, chiSquareTest.getAlpha(), ChiSquareTest.TestType.CHI_SQUARE, rows);
        }
    }

    @Override
    public void setEffectiveSampleSize(int sampleSize) {
        this.sampleSize = sampleSize;
    }

    /**
     * Sets the cell table type.
     *
     * @param cellTableType The cell table type.
     */
    public void setCellTableType(ChiSquareTest.CellTableType cellTableType) {
        this.chiSquareTest.setCellTableType(cellTableType);
    }

    /**
     * Sets the threshold for making judgments of determination.
     *
     * @param determinationP This threshold.
     */
    public void setDeterminationP(double determinationP) {
        this.determinationP = determinationP;
    }

    /**
     * Determines whether variable x is independent of a set of variables _z.
     *
     * @param _z a set of variables to condition on
     * @param x  the variable to check for independence
     * @return true if variable x is independent of _z, false otherwise
     * @throws NullPointerException     if _z or any element in _z is null
     * @throws IllegalArgumentException if any variable in _z or x was not used in the constructor
     */
    public boolean determines(Set<Node> _z, Node x) {
        if (_z == null) {
            throw new NullPointerException();
        }

        for (Node node : _z) {
            if (node == null) {
                throw new NullPointerException();
            }
        }

        List<Node> z = new ArrayList<>(_z);
        Collections.sort(z);

        // For testing x, y given z1,...,zn, set up an array of length
        // n + 2 containing the indices of these variables in order.
        int[] testIndices = new int[1 + z.size()];
        testIndices[0] = this.variables.indexOf(x);

        for (int i = 0; i < z.size(); i++) {
            testIndices[i + 1] = this.variables.indexOf(z.get(i));
        }

        // the following is lame code--need a better test
        for (int i = 0; i < testIndices.length; i++) {
            if (testIndices[i] < 0) {
                throw new IllegalArgumentException(
                        "Variable " + i + "was not used in the constructor.");
            }
        }

        //        System.out.println("Testing " + x + " _||_ " + y + " | " + z);

        boolean determined =
                this.chiSquareTest.isDetermined(testIndices, this.determinationP);

        if (determined) {
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

            TetradLogger.getInstance().log(sb.toString());
        }

        return determined;
    }

}




