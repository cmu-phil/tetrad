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

import edu.cmu.tetrad.data.CellTable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.util.CombinationIterator;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;

import java.util.List;

import static org.apache.commons.math3.util.FastMath.log;

/**
 * Calculates chi-square or g-square for a conditional cross-tabulation table for independence question 0 _||_ 1 | 2, 3,
 * ...max by summing up chi-square and degrees of freedom for each conditional table in turn, where rows or columns that
 * sum to less than a given threshold have been removed. The adjusted conditional tables are required to have more than
 * 0 total counts and at least 2 rows and 2 columns; otherwise, the test is judged to be invalid. Otherwise, a p-value
 * is returned based on the Chi-Square distribution with the total degrees of freedom and total chi-square.
 *
 * @author frankwimberly
 * @author josephramsey
 * @version $Id: $Id
 * @see TestType
 */
public class ChiSquareTest {

    /**
     * The data set this test uses.
     */
    private final DataSet dataSet;
    /**
     * The number of values for each variable in the data.
     */
    private final int[] dims;
    /**
     * Stores the data in the form of a cell table.
     */
    private final CellTable cellTable;
    /**
     * The type of test to perform.
     */
    private final TestType testType;
    /**
     * The significance level of the test.
     */
    private double alpha;

    /**
     * The minimum sum of a row or column for a conditional table to be included in the overall chi-square and degrees
     * of freedom. Note that this should not be too small, or the chi-square distribution will not be a good
     * approximation to the distribution of the test statistic.
     */
    private double minCountPerCell = 1.0;

    /**
     * Constructs a test using the given data set and significance level.
     *
     * @param dataSet  A data set consisting entirely of discrete variables.
     * @param alpha    The significance level, usually 0.05.
     * @param testType The type of test to perform, either CHI_SQUARE or G_SQUARE.
     */
    public ChiSquareTest(DataSet dataSet, double alpha, TestType testType) {
        if (alpha < 0.0 || alpha > 1.0) {
            throw new IllegalArgumentException("Significance level must be in " + "[0, 1]: " + alpha);
        }

        this.dims = new int[dataSet.getNumColumns()];

        for (int i = 0; i < getDims().length; i++) {
            DiscreteVariable variable = (DiscreteVariable) dataSet.getVariable(i);
            this.getDims()[i] = variable.getNumCategories();
        }

        this.testType = testType;
        this.dataSet = dataSet;
        this.alpha = alpha;
        this.cellTable = new CellTable(null);
        this.getCellTable().setMissingValue(DiscreteVariable.MISSING_VALUE);
    }

    /**
     * Calculates chi square for a conditional cross-tabulation table for independence question 0 _||_ 1 | 2, 3, ...max
     * by summing up chi square and degrees of freedom for each conditional table in turn, where rows or columns that
     * with fewer than minSumRowOrCol counts have been removed. The adjusted conditional tables are required to have
     * more than 0 total counts and at least 2 rows and 2 columns; otherwise, the test is judged to be invalid.
     * Otherwise, a p-value is returned based on the Chi-Square distribution with the total degrees of freedom and total
     * chi-square.
     *
     * @param testIndices These indices, in order.
     * @return a Chi square test result.
     * @see Result
     */
    public Result calcChiSquare(int[] testIndices) {

        // Reset the cell table for the columns referred to in
        // 'testIndices.' Do cell coefs for those columns.
        this.getCellTable().addToTable(getDataSet(), testIndices);

        // Indicator arrays to tell the cell table which margins
        // to calculate. For x _||_ y | z1, z2, ..., we want to
        // calculate the margin for x, the margin for y, and the
        // margin for x and y. (These will be used later.)
        int[] firstVar = {0};
        int[] secondVar = {1};
        int[] bothVars = {0, 1};

        double xSquare = 0.0;
        int df = 0;

        int[] condDims = new int[testIndices.length - 2];
        System.arraycopy(selectFromArray(getDims(), testIndices), 2, condDims, 0, condDims.length);

        int[] coords = new int[testIndices.length];
        int numRows = this.getCellTable().getNumValues(0);
        int numCols = this.getCellTable().getNumValues(1);

        CombinationIterator combinationIterator = new CombinationIterator(condDims);

        // Make a chi square table for each condition combination, strike zero rows and columns and calculate
        // chi square and degrees of freedom for the remaining rows and columns in the table. See Friedman.
        while (combinationIterator.hasNext()) {
            int[] combination = combinationIterator.next();
            System.arraycopy(combination, 0, coords, 2, combination.length);

            double[] sumRows = new double[numRows];
            double[] sumCols = new double[numCols];
            boolean[] zeroRows = new boolean[numRows];
            boolean[] zeroCols = new boolean[numCols];
            int numNonZeroRows = 0;
            int numNonZeroCols = 0;

            for (int i = 0; i < numRows; i++) {
                coords[0] = i;
                sumRows[i] = getCellTable().calcMargin(coords, secondVar);

                if (sumRows[i] == 0 || sumRows[i] < minCountPerCell * numCols) {
                    zeroRows[i] = true;
                } else {
                    numNonZeroRows++;
                }
            }

            for (int j = 0; j < numCols; j++) {
                coords[1] = j;
                sumCols[j] = getCellTable().calcMargin(coords, firstVar);

                if (sumCols[j] == 0 || sumCols[j] < minCountPerCell * numRows) {
                    zeroCols[j] = true;
                } else {
                    numNonZeroCols++;
                }
            }

            double total = getCellTable().calcMargin(coords, bothVars);

            if (total < minCountPerCell * numRows * numCols) {
                continue;
            }

            if (total > 0 && numNonZeroRows > 1 && numNonZeroCols > 1) {
                double _xSquare = 0.0;

                for (int i = 0; i < numRows; i++) {
                    for (int j = 0; j < numCols; j++) {
                        coords[0] = i;
                        coords[1] = j;

                        if (zeroRows[i] || zeroCols[j]) {
                            continue;
                        }

                        double observed = getCellTable().getValue(coords);

                        // Under the above conditions, expected > 0.
                        double expected = (sumRows[i] * sumCols[j]) / total;

                        if (testType == TestType.CHI_SQUARE) {

                            // Note here expected > 0, so we can divide by it.
                            double d = observed - expected;
                            _xSquare += (d * d) / expected;
                        } else if (testType == TestType.G_SQUARE) {

                            // The G-square test is a likelihood ratio test, so we need to take the log of the
                            // observed/expected ratio. When observed is zero, we add 0 to the total. This is the
                            // correct thing to do, since the limit of x * log(x) as x approaches zero is zero.
                            if (observed > 0) {
                                _xSquare += 2.0 * observed * log(observed / expected);
                            } else {
                                _xSquare += 0;
                            }
                        } else {
                            throw new IllegalArgumentException("Unknown test type: " + testType);
                        }
                    }
                }

                int _df = (numNonZeroRows - 1) * (numNonZeroCols - 1);
                if (_df == 0) _df = 1;
                xSquare += _xSquare;
                df += _df;
            }
        }

        if (df == 0) {

            // If no conditional table had positive degrees of freedom, the test is invalid.
            return new Result(Double.NaN, Double.NaN, 0, true, false);
        } else {

            // Otherwise, we can calculate a p-value for the test.
            double pValue = 1.0 - new ChiSquaredDistribution(df).cumulativeProbability(xSquare);
            return new Result(xSquare, pValue, df, (pValue > getAlpha()), true);
        }
    }

    /**
     * Returns a judgment of whether a set of parent variables determines a child variables.
     *
     * @param testIndices An array of indices for variables in the dataset supplied in the constructor.
     * @param p           The probability that some marginal for some table dominates. A good value is 0.99.
     * @return True if the variable at index 0 is determined by the variables at the other indices.
     */
    public boolean isDetermined(int[] testIndices, double p) {

        // Reset the cell table for the columns referred to in
        // 'testIndices.' Do cell coefs for those columns.
        this.getCellTable().addToTable(getDataSet(), testIndices);

        // Indicator arrays to tell the cell table which margins
        // to calculate. For x _||_ y | z1, z2, ..., we want to
        // calculate the margin for x, the margin for y, and the
        // margin for x and y. (These will be used later.)
        int[] firstVar = {0};

        int[] condDims = new int[testIndices.length - 1];
        System.arraycopy(selectFromArray(getDims(), testIndices), 1, condDims, 0, condDims.length);

        int[] coords = new int[testIndices.length];
        int numValues = this.getCellTable().getNumValues(0);

        CombinationIterator combinationIterator = new CombinationIterator(condDims);

        while (combinationIterator.hasNext()) {
            int[] combination = combinationIterator.next();
            System.arraycopy(combination, 0, coords, 1, combination.length);

            long total = this.getCellTable().calcMargin(coords, firstVar);

            if (total == 0) {
                continue;
            }

            boolean dominates = false;

            for (int i = 0; i < numValues; i++) {
                coords[0] = i;

                long value = this.getCellTable().getValue(coords);

                if ((double) value / total >= p) {
                    dominates = true;
                }
            }

            if (!dominates) {
                return false;
            }
        }

        return true;
    }

    /**
     * Returns the model significance level being used for tests.
     *
     * @return this level.
     */
    public double getAlpha() {
        return this.alpha;
    }

    /**
     * Sets the significance level to be used for tests.
     *
     * @param alpha This significance level.
     */
    public void setAlpha(double alpha) {
        if (alpha < 0.0 || alpha > 1.0) {
            throw new IllegalArgumentException("Significance level must be in " + "[0, 1]: " + alpha);
        }

        this.alpha = alpha;
    }

    /**
     * Sets the rows to use in the data.
     *
     * @param rows The rows to use.
     */
    public void setRows(List<Integer> rows) {
        this.cellTable.setRows(rows);
    }

    private int[] selectFromArray(int[] arr, int[] indices) {
        int[] retArr = new int[indices.length];

        for (int i = 0; i < indices.length; i++) {
            retArr[i] = arr[indices[i]];
        }

        return retArr;
    }

    private DataSet getDataSet() {
        return this.dataSet;
    }

    private int[] getDims() {
        return this.dims;
    }

    private CellTable getCellTable() {
        return this.cellTable;
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
    }

    /**
     * The type of test to perform.
     */
    public enum TestType {

        /**
         * The chi-square test. See Spirtes et al., Causation, Predication and Search.
         */
        CHI_SQUARE,

        /**
         * The G-square test. See Spirtes et al. Causation, Prediction and Search.
         */
        G_SQUARE
    }

    /**
     * Simple class to store the parameters of the result returned by the G Square test.
     *
     * @author Frank Wimberly
     */
    public static class Result {
        private final double chiSquare;
        private final double pValue;
        private final int df;
        private final boolean isIndep;
        private final boolean isValid;

        /**
         * Constructs a new g square result using the given parameters.
         *
         * @param chiSquare The chi square value.
         * @param pValue    The pValue of the result.
         * @param df        The adjusted degrees of freedom.
         * @param isIndep   Whether the conditional independence holds or not. (True if it does, false if it doesn't.)
         */
        public Result(double chiSquare, double pValue, int df, boolean isIndep) {
            this(chiSquare, pValue, df, isIndep, true);
        }

        /**
         * Constructs a new g square result using the given parameters.
         *
         * @param chiSquare The chi square value.
         * @param pValue    The pValue of the result.
         * @param df        The adjusted degrees of freedom.
         * @param isIndep   Whether the conditional independence holds or not. (True if it does, false if it doesn't.)
         * @param isValid   Whether the result is isValid or not.
         */
        public Result(double chiSquare, double pValue, int df, boolean isIndep, boolean isValid) {
            this.chiSquare = chiSquare;
            this.pValue = pValue;
            this.df = df;
            this.isIndep = isIndep;
            this.isValid = isValid;
        }

        /**
         * Returns the chi square value, or NaN if the chi square value cannot be determined.
         *
         * @return the chi square value.
         */
        public double getXSquare() {
            return this.chiSquare;
        }

        /**
         * Returns the pValue of the result, or NaN if the p-value cannot be determined.
         *
         * @return the pValue of the result.
         */
        public double getPValue() {
            return this.pValue;
        }

        /**
         * Returns the adjusted degrees of freedom, or -1 if the degrees of freedom cannot be determined.
         *
         * @return the adjusted degrees of freedom.
         */
        public int getDf() {
            return this.df;
        }

        /**
         * Returns whether the conditional independence holds or not. (True if it does, false if it doesn't.) For
         * invalid results, this method returns a value set by the test.
         *
         * @return whether the conditional independence holds or not.
         */
        public boolean isIndep() {
            return this.isIndep;
        }

        /**
         * Returns whether the result is valid or not. A result is valid if its judgment of independence is not
         * indeterminate.
         *
         * @return whether the result is valid or not.
         */
        public boolean isValid() {
            return isValid;
        }
    }
}





