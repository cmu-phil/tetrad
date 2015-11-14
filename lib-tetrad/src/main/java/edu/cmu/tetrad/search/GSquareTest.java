///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
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

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.util.CombinationIterator;
import edu.cmu.tetrad.util.ProbUtils;

import java.util.Arrays;

/**
 * Performs conditional independence tests of discrete data using the G Square method. Degrees of freedom are calculated
 * as in Fienberg, The Analysis of Cross-Classified Categorical Data, 2nd Edition, 142.
 *
 * @author Frank Wimberly original version
 * @author Joseph Ramsey revision 10/01
 */
public final class GSquareTest extends ChiSquareTest {

//    /**
//     * The data set this test uses.
//     */
//    private DataSet dataSet;
//
//    /**
//     * The number of values for each variable in the data.
//     */
//    private int[] dims;
//
//    /**
//     * Stores the data in the form of a cell table.
//     */
//    private CellTable cellTable;

//    /**
//     * The significance level of the test.
//     */
//    private double alpha = 0.05;

    public GSquareTest(DataSet dataSet, double alpha) {
        super(dataSet, alpha);
//        if (alpha < 0.0 || alpha > 1.0) {
//            throw new IllegalArgumentException("Significance level must be " +
//                    "between 0.0 and 1.0: " + alpha);
//        }
//
//        this.dims = new int[dataSet.getNumColumns()];
//
//        for (int i = 0; i < dims.length; i++) {
//            DiscreteVariable variable =
//                    (DiscreteVariable) dataSet.getVariable(i);
//            this.dims[i] = variable.getNumCategories();
//        }
//
//        this.dataSet = dataSet;
//        this.alpha = alpha;
//        this.cellTable = new CellTable(null);
//        this.cellTable.setMissingValue(DiscreteVariable.MISSING_VALUE);
    }

    /**
     * Calculates g square for a conditional crosstabulation table for independence question 0 _||_ 1 | 2, 3, ...max by
     * summing up g square and degrees of freedom for each conditional table in turn, where rows or columns that consist
     * entirely of zeros have been removed.
     */
    public GSquareTest.Result calcGSquare(int[] testIndices) {

        // Reset the cell table for the columns referred to in
        // 'testIndices.' Do cell coefs for those columns.
        getCellTable().addToTable(getDataSet(), testIndices);

        // Indicator arrays to tell the cell table which margins
        // to calculate. For x _||_ y | z1, z2, ..., we want to
        // calculate the margin for x, the margin for y, and the
        // margin for x and y. (These will be used later.)
        int[] firstVar = new int[]{0};
        int[] secondVar = new int[]{1};
        int[] bothVars = new int[]{0, 1};

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
            int[] combination = (int[]) combinationIterator.next();

            System.arraycopy(combination, 0, coords, 2, combination.length);
            Arrays.fill(attestedRows, true);
            Arrays.fill(attestedCols, true);

            long total = this.getCellTable().calcMargin(coords, bothVars);

            if (total == 0) {
                continue;
            }

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

                    double expected =
                            (double) (sumCol * sumRow) / (double) total;

                    if (observed != 0) {
                        g2 += 2.0 * observed * Math.log(observed / expected);
                    }
                }
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

            df += (numAttestedRows - 1) * (numAttestedCols - 1);
        }

        // If df == 0, return indep.
        if (df == 0) {
            df = 1;
        }

        double pValue = 1.0 - ProbUtils.chisqCdf(g2, df);
        boolean indep = (pValue > getAlpha());
        return new GSquareTest.Result(g2, pValue, df, indep);
    }

//    public boolean isDetermined(int[] testIndices, double p) {
//
//        // Reset the cell table for the columns referred to in
//        // 'testIndices.' Do cell coefs for those columns.
////        this.cellTable.reset(selectFromArray(dims, testIndices));
////        this.cellTable.addToTable(selectFromArray(data, testIndices),
////                CellTable.COLUMN_MAJOR);
//
//        this.getCellTable().addToTable(getDataSet(), testIndices);
//
//        // Indicator arrays to tell the cell table which margins
//        // to calculate. For x _||_ y | z1, z2, ..., we want to
//        // calculate the margin for x, the margin for y, and the
//        // margin for x and y. (These will be used later.)
//        int[] firstVar = new int[]{0};
//
//        int[] condDims = new int[testIndices.length - 1];
//        System.arraycopy(selectFromArray(getDims(), testIndices), 1, condDims, 0,
//                condDims.length);
//
//        int[] coords = new int[testIndices.length];
//        int numValues = this.getCellTable().getNumValues(0);
//
//        CombinationIterator combinationIterator =
//                new CombinationIterator(condDims);
//
//        while (combinationIterator.hasNext()) {
//            int[] combination = (int[]) combinationIterator.next();
//            System.arraycopy(combination, 0, coords, 1, combination.length);
//
//            long total = this.getCellTable().calcMargin(coords, firstVar);
//
//            if (total == 0) {
//                continue;
//            }
//
//            boolean existsDominator = false;
//
//            for (int i = 0; i < numValues; i++) {
//                coords[0] = i;
//
//                long numi = this.getCellTable().getValue(coords);
//
//                if ((double) numi / total >= p) {
//                    existsDominator = true;
//                }
//            }
//
//            if (!existsDominator) {
//                return false;
//            }
//        }
//
//        return true;
//    }

//    public boolean isSplitDetermined(int[] testIndices, double p) {
//
//        // Reset the cell table for the columns referred to in
//        // 'testIndices.' Do cell coefs for those columns.
//        this.getCellTable().addToTable(getDataSet(), testIndices);
//
//        // Indicator arrays to tell the cell table which margins
//        // to calculate. For x _||_ y | z1, z2, ..., we want to
//        // calculate the margin for x, the margin for y, and the
//        // margin for x and y. (These will be used later.)
//        int[] firstVar = new int[]{0};
//        int[] secondVar = new int[]{1};
//        int[] bothVars = new int[]{0, 1};
//
//        int[] condDims = new int[testIndices.length - 2];
//        System.arraycopy(selectFromArray(getDims(), testIndices), 2, condDims, 0,
//                condDims.length);
//
//        int[] coords = new int[testIndices.length];
//        int numRows = this.getCellTable().getNumValues(0);
//        int numCols = this.getCellTable().getNumValues(1);
//
//        boolean[] attestedRows = new boolean[numRows];
//        boolean[] attestedCols = new boolean[numCols];
//
//        CombinationIterator combinationIterator =
//                new CombinationIterator(condDims);
//
//        while (combinationIterator.hasNext()) {
//            int[] combination = (int[]) combinationIterator.next();
//
//            System.arraycopy(combination, 0, coords, 2, combination.length);
//            Arrays.fill(attestedRows, true);
//            Arrays.fill(attestedCols, true);
//
//            long total = this.getCellTable().calcMargin(coords, bothVars);
//
//            if (total == 0) {
//                continue;
//            }
//
//            boolean dominates = false;
//
//            marginals:
//            for (int i = 0; i < numRows; i++) {
//                for (int j = 0; j < numCols; j++) {
//                    coords[0] = i;
//                    coords[1] = j;
//
//                    long sumRow = this.getCellTable().calcMargin(coords, secondVar);
//                    long sumCol = this.getCellTable().calcMargin(coords, firstVar);
//
//                    if ((double) sumRow / total >= 0.99) {
//                        dominates = true;
//                        break marginals;
//                    }
//
//                    if ((double) sumCol / total >= 0.99) {
//                        dominates = true;
//                        break marginals;
//                    }
//                }
//
//            }
//
//            if (!dominates) {
//                return false;
//            }
//        }
//
//        return true;
//    }

//    /**
//     * @return the getModel significance level being used for tests.
//     */
//    public double getAlpha() {
//        return this.alpha;
//    }

//    /**
//     * Sets the significance level to be used for tests.
//     */
//    public void setAlpha(double alpha) {
//        this.alpha = alpha;
//    }

//    private static int[] selectFromArray(int[] arr, int[] indices) {
//        int[] retArr = new int[indices.length];
//
//        for (int i = 0; i < indices.length; i++) {
//            retArr[i] = arr[indices[i]];
//        }
//
//        return retArr;
//    }

    /**
     * Simple class to store the parameters of the result returned by the G Square test.
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

        public final double getGSquare() {
            return gSquare;
        }

        public final double getPValue() {
            return pValue;
        }

        public final int getDf() {
            return df;
        }

        public final boolean isIndep() {
            return isIndep;
        }
    }
}





