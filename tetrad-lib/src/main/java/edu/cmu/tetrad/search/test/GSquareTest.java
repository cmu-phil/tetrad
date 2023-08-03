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
import edu.cmu.tetrad.util.ProbUtils;
import org.apache.commons.math3.util.FastMath;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * <p>Performs conditional independence tests of discrete data using the G Square method.
 * Degrees of freedom are calculated as in Fienberg (2007), this reference:</p>
 *
 * <p>Fienberg, S. E. (2007). The analysis of cross-classified categorical data.
 * Springer Science & Business Media.</p>
 *
 * @author Frank Wimberly original version
 * @author josephramsey revision 10/01
 */
public final class GSquareTest {

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
     * The significance level of the test.
     */
    private double alpha;

    /**
     * Constructor
     *
     * @param dataSet The discrete dataset for which test results are requested.
     * @param alpha   The alpha sigificance level cutoff.
     */
    public GSquareTest(DataSet dataSet, double alpha) {
        if (alpha < 0.0 || alpha > 1.0) {
            throw new IllegalArgumentException("Significance level must be in " +
                    "[0, 1]: " + alpha);
        }

        this.dims = new int[dataSet.getNumColumns()];

        for (int i = 0; i < getDims().length; i++) {
            DiscreteVariable variable =
                    (DiscreteVariable) dataSet.getVariable(i);
            this.getDims()[i] = variable.getNumCategories();
        }

        this.dataSet = dataSet;
        this.alpha = alpha;
        this.cellTable = new CellTable(null);
        this.getCellTable().setMissingValue(DiscreteVariable.MISSING_VALUE);
    }

    /**
     * Calculates g square for a conditional crosstabulation table for independence question 0 _||_ 1 | 2, 3, ...max by
     * summing up g square and degrees of freedom for each conditional table in turn, where rows or columns that consist
     * entirely of zeros have been removed.
     *
     * @param testIndices The indices of the test result needed, in order. So for the above, [0 1 2 3...max].
     * @return the test result.
     * @see Result
     */
    public Result calcGSquare(int[] testIndices) {

        if (testIndices.length < 2)
            throw new IllegalArgumentException("Need at least two variables for G Square test.");

        // Reset the cell table for the columns referred to in
        // 'testIndices.' Do cell coefs for those columns.
        getCellTable().addToTable(getDataSet(), testIndices);

        // Indicator arrays to tell the cell table which margins
        // to calculate. For x _||_ y | z1, z2, ..., we want to
        // calculate the margin for x, the margin for y, and the
        // margin for x and y. (These will be used later.)
        int[] firstVar = {0};
        int[] secondVar = {1};
        int[] bothVars = {0, 1};

        double g2 = 0.0;
        int df = 0;

        int[] condDims = new int[testIndices.length - 2];
        System.arraycopy(selectFromArray(getDims(), testIndices), 2, condDims, 0,
                condDims.length);

        int[] coords = new int[testIndices.length];
        int numRows = this.getCellTable().getNumValues(0);
        int numCols = this.getCellTable().getNumValues(1);

        boolean[] attestedRows = new boolean[numRows];
        boolean[] attestedCols = new boolean[numCols];

        CombinationIterator combinationIterator =
                new CombinationIterator(condDims);

        while (combinationIterator.hasNext()) {
            int[] combination = combinationIterator.next();

            System.arraycopy(combination, 0, coords, 2, combination.length);
            Arrays.fill(attestedRows, true);
            Arrays.fill(attestedCols, true);

            long total = this.getCellTable().calcMargin(coords, bothVars);

            double _gSquare = 0.0;

            List<Double> e = new ArrayList<>();
            List<Long> o = new ArrayList<>();

            for (int i = 0; i < numRows; i++) {
                for (int j = 0; j < numCols; j++) {
                    coords[0] = i;
                    coords[1] = j;

                    long sumRow = this.getCellTable().calcMargin(coords, secondVar);
                    long sumCol = this.getCellTable().calcMargin(coords, firstVar);
                    long observed = (int) this.getCellTable().getValue(coords);

                    boolean skip = false;

                    if (sumRow == 0) {
                        attestedRows[i] = false;
                        skip = true;
                    }

                    if (sumCol == 0) {
                        attestedCols[j] = false;
                        skip = true;
                    }

                    if (skip) {
                        continue;
                    }

                    e.add((double) sumCol * sumRow);
                    o.add(observed);
                }
            }

            for (int i = 0; i < o.size(); i++) {
                double expected = e.get(i) / (double) total;

                if (o.get(i) != 0) {
                    _gSquare += 2.0 * o.get(i) * FastMath.log(o.get(i) / expected);
                }
            }

            if (total == 0) {
                continue;
            }

            int numAttestedRows = 0;
            int numAttestedCols = 0;

            for (boolean attestedRow : attestedRows) {
                if (attestedRow) {
                    numAttestedRows++;
                }
            }

            for (boolean attestedCol : attestedCols) {
                if (attestedCol) {
                    numAttestedCols++;
                }
            }

            int _df = (numAttestedRows - 1) * (numAttestedCols - 1);

            if (_df > 0) {
                df += _df;
                g2 += _gSquare;
            }
        }

        // If df == 0, return indep.
        if (df == 0) {
            df = 1;
        }

        double pValue = 1.0 - ProbUtils.chisqCdf(g2, df);
        boolean indep = (pValue > getAlpha());
        return new Result(g2, pValue, df, indep);
    }

    /**
     * Returns the dimensions of the variables, in order.
     *
     * @return These dimensions, as an int[] array. For instance, if the array is [2 3], then the first variable has 2
     * categories and second variable has 3 categories.
     */
    public int[] getDims() {
        return this.dims;
    }

    /**
     * Returns the cell table for this test.
     *
     * @return This table.
     * @see CellTable
     */
    public CellTable getCellTable() {
        return this.cellTable;
    }

    /**
     * @return the getModel significance level being used for tests.
     */
    public double getAlpha() {
        return this.alpha;
    }

    /**
     * Sets the significance level to be used for tests.
     *
     * @param alpha The alpha significance level of the test.
     */
    public void setAlpha(double alpha) {
        if (alpha < 0.0 || alpha > 1.0) {
            throw new IllegalArgumentException("Significance level must be in " +
                    "[0, 1]: " + alpha);
        }

        this.alpha = alpha;
    }

    /**
     * Returns the dataset used for this test.
     *
     * @return This dataset.
     */
    public DataSet getDataSet() {
        return this.dataSet;
    }

    /**
     * Returns a judgement of whether the variables index by 'testIndices' determine the variable index by 'p'.
     *
     * @param testIndices The indices of the conditioning variables.
     * @param p           The index of the child variable.
     * @return True if the conditioning variables determine the child variable.
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
        System.arraycopy(selectFromArray(getDims(), testIndices), 1, condDims, 0,
                condDims.length);

        int[] coords = new int[testIndices.length];
        int numValues = this.getCellTable().getNumValues(0);

        CombinationIterator combinationIterator =
                new CombinationIterator(condDims);

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

                long numi = this.getCellTable().getValue(coords);

                if ((double) numi / total >= p) {
                    dominates = true;
                }
            }

            if (!dominates) {
                return false;
            }
        }

        return true;
    }

    private int[] selectFromArray(int[] arr, int[] indices) {
        int[] retArr = new int[indices.length];

        for (int i = 0; i < indices.length; i++) {
            retArr[i] = arr[indices[i]];
        }

        return retArr;
    }

    /**
     * Stores the parameters of the result returned by the G Square test and its p-value.
     *
     * @author Frank Wimberly
     */
    public static final class Result {

        /**
         * The g square value itself.
         */
        private final double gSquare;

        /**
         * The pValue of the result.
         */
        private final double pValue;

        /**
         * The adjusted degrees of freedom.
         */
        private final int df;

        /**
         * Whether the conditional independence holds or not. (True if it does, false if it doesn't.
         */
        private final boolean isIndep;

        /**
         * Constructs a new g square result using the given parameters.
         */
        public Result(double gSquare, double pValue, int df, boolean isIndep) {
            this.gSquare = gSquare;
            this.pValue = pValue;
            this.df = df;
            this.isIndep = isIndep;
        }

        public double getGSquare() {
            return this.gSquare;
        }

        public double getPValue() {
            return this.pValue;
        }

        public int getDf() {
            return this.df;
        }

        public boolean isIndep() {
            return this.isIndep;
        }
    }
}





