package edu.cmu.tetrad.search;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * <p>An implemetation of the Kuhn–Munkres assignment algorithm of the year 1957.
 * https://en.wikipedia.org/wiki/Hungarian_algorithm.</p>
 * <p>From: https://github.com/aalmi/HungarianAlgorithm</p>
 * <p>This is not our code; please see the references given. We've just
 * made a few tweaks to make it work better in Tetrad. Thanks aalmi.</p>
 *
 * @author https://github.com/aalmi | march 2014
 * @version 1.0
 */
public class HungarianAlgorithm {

    double[][] costMatrix; // initial matrix (cost matrix)

    // markers in the matrix
    int[] squareInRow, squareInCol, rowIsCovered, colIsCovered, staredZeroesInRow;

    /**
     * Trying to find lowest-cost assignment.
     */
    public HungarianAlgorithm(double[][] costMatrix) {
        if (costMatrix.length != costMatrix[0].length) {
            try {
                throw new IllegalAccessException("The costMatrix is not square!");
            } catch (IllegalAccessException ex) {
                System.err.println(ex);
                System.exit(1);
            }
        }

        this.costMatrix = costMatrix;
        squareInRow = new int[costMatrix.length];       // squareInRow & squareInCol indicate the position
        squareInCol = new int[costMatrix[0].length];    // of the marked zeroes

        rowIsCovered = new int[costMatrix.length];      // indicates whether a row is covered
        colIsCovered = new int[costMatrix[0].length];   // indicates whether a column is covered
        staredZeroesInRow = new int[costMatrix.length]; // storage for the 0*
        Arrays.fill(staredZeroesInRow, -1);
        Arrays.fill(squareInRow, -1);
        Arrays.fill(squareInCol, -1);
    }

    /**
     * find an optimal assignment
     *
     * @return optimal assignment
     */
    public int[][] findOptimalAssignment() {
        step1();    // reduce matrix
        step2();    // mark independent zeroes
        step3();    // cover columns which contain a marked zero

        while (!allColumnsAreCovered()) {
            int[] mainZero = step4();
            while (mainZero == null) {      // while no zero found in step4
                step7();
                mainZero = step4();
            }
            if (squareInRow[mainZero[0]] == -1) {
                // there is no square mark in the mainZero line
                step6(mainZero);
                step3();    // cover columns which contain a marked zero
            } else {
                // there is square mark in the mainZero line
                // step 5
                rowIsCovered[mainZero[0]] = 1;  // cover row of mainZero
                colIsCovered[squareInRow[mainZero[0]]] = 0;  // uncover column of mainZero
                step7();
            }
        }

        int[][] optimalAssignment = new int[costMatrix.length][];
        for (int i = 0; i < squareInCol.length; i++) {
            optimalAssignment[i] = new int[]{i, squareInCol[i]};
        }
        return optimalAssignment;
    }

    /**
     * Check if all columns are covered. If that's the case then the
     * optimal solution is found
     *
     * @return true or false
     */
    private boolean allColumnsAreCovered() {
        for (int i : colIsCovered) {
            if (i == 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Step 1:
     * Reduce the matrix so that in each row and column at least one zero exists:
     * 1. subtract each row minima from each element of the row
     * 2. subtract each column minima from each element of the column
     */
    private void step1() {
        // rows
        for (int i = 0; i < costMatrix.length; i++) {
            // find the min value of the current row
            double currentRowMin = Double.POSITIVE_INFINITY;
            for (int j = 0; j < costMatrix[i].length; j++) {
                if (costMatrix[i][j] < currentRowMin) {
                    currentRowMin = costMatrix[i][j];
                }
            }
            // subtract min value from each element of the current row
            for (int k = 0; k < costMatrix[i].length; k++) {
                costMatrix[i][k] -= currentRowMin;
            }
        }

        // cols
        for (int i = 0; i < costMatrix[0].length; i++) {
            // find the min value of the current column
            double currentColMin = Double.POSITIVE_INFINITY;
            for (double[] doubles : costMatrix) {
                if (doubles[i] < currentColMin) {
                    currentColMin = doubles[i];
                }
            }
            // subtract min value from each element of the current column
            for (int k = 0; k < costMatrix.length; k++) {
                costMatrix[k][i] -= currentColMin;
            }
        }
    }

    /**
     * Step 2:
     * mark each 0 with a "square", if there are no other marked zeroes in the same row or column
     */
    private void step2() {
        int[] rowHasSquare = new int[costMatrix.length];
        int[] colHasSquare = new int[costMatrix[0].length];

        for (int i = 0; i < costMatrix.length; i++) {
            for (int j = 0; j < costMatrix.length; j++) {
                // mark if current value == 0 & there are no other marked zeroes in the same row or column
                if (costMatrix[i][j] == 0 && rowHasSquare[i] == 0 && colHasSquare[j] == 0) {
                    rowHasSquare[i] = 1;
                    colHasSquare[j] = 1;
                    squareInRow[i] = j; // save the row-position of the zero
                    squareInCol[j] = i; // save the column-position of the zero
                    continue; // jump to next row
                }
            }
        }
    }

    /**
     * Step 3:
     * Cover all columns which are marked with a "square"
     */
    private void step3() {
        for (int i = 0; i < squareInCol.length; i++) {
            colIsCovered[i] = squareInCol[i] != -1 ? 1 : 0;
        }
    }

    /**
     * Step 7:
     * 1. Find the smallest uncovered value in the matrix.
     * 2. Subtract it from all uncovered values
     * 3. Add it to all twice-covered values
     */
    private void step7() {
        // Find the smallest uncovered value in the matrix
        double minUncoveredValue = Double.POSITIVE_INFINITY;
        for (int i = 0; i < costMatrix.length; i++) {
            if (rowIsCovered[i] == 1) {
                continue;
            }
            for (int j = 0; j < costMatrix[0].length; j++) {
                if (colIsCovered[j] == 0 && costMatrix[i][j] < minUncoveredValue) {
                    minUncoveredValue = costMatrix[i][j];
                }
            }
        }

        if (minUncoveredValue > 0) {
            for (int i = 0; i < costMatrix.length; i++) {
                for (int j = 0; j < costMatrix[0].length; j++) {
                    if (rowIsCovered[i] == 1 && colIsCovered[j] == 1) {
                        // Add min to all twice-covered values
                        costMatrix[i][j] += minUncoveredValue;
                    } else if (rowIsCovered[i] == 0 && colIsCovered[j] == 0) {
                        // Subtract min from all uncovered values
                        costMatrix[i][j] -= minUncoveredValue;
                    }
                }
            }
        }
    }

    /**
     * Step 4:
     * Find zero value Z_0 and mark it as "0*".
     *
     * @return position of Z_0 in the matrix
     */
    private int[] step4() {
        for (int i = 0; i < costMatrix.length; i++) {
            if (rowIsCovered[i] == 0) {
                for (int j = 0; j < costMatrix[i].length; j++) {
                    if (costMatrix[i][j] == 0 && colIsCovered[j] == 0) {
                        staredZeroesInRow[i] = j; // mark as 0*
                        return new int[]{i, j};
                    }
                }
            }
        }
        return null;
    }

    /**
     * Step 6:
     * Create a chain K of alternating "squares" and "0*"
     *
     * @param mainZero => Z_0 of Step 4
     */
    private void step6(int[] mainZero) {
        int i = mainZero[0];
        int j = mainZero[1];

        Set<int[]> K = new LinkedHashSet<>();
        //(a)
        // add Z_0 to K
        K.add(mainZero);
        boolean found = false;
        do {
            // (b)
            // add Z_1 to K if
            // there is a zero Z_1 which is marked with a "square " in the column of Z_0
            if (squareInCol[j] != -1) {
                K.add(new int[]{squareInCol[j], j});
                found = true;
            } else {
                found = false;
            }

            // if no zero element Z_1 marked with "square" exists in the column of Z_0, then cancel the loop
            if (!found) {
                break;
            }

            // (c)
            // replace Z_0 with the 0* in the row of Z_1
            i = squareInCol[j];
            j = staredZeroesInRow[i];
            // add the new Z_0 to K
            if (j != -1) {
                K.add(new int[]{i, j});
                found = true;
            } else {
                found = false;
            }

        } while (found); // (d) as long as no new "square" marks are found

        // (e)
        for (int[] zero : K) {
            // remove all "square" marks in K
            if (squareInCol[zero[1]] == zero[0]) {
                squareInCol[zero[1]] = -1;
                squareInRow[zero[0]] = -1;
            }
            // replace the 0* marks in K with "square" marks
            if (staredZeroesInRow[zero[0]] == zero[1]) {
                squareInRow[zero[0]] = zero[1];
                squareInCol[zero[1]] = zero[0];
            }
        }

        // (f)
        // remove all marks
        Arrays.fill(staredZeroesInRow, -1);
        Arrays.fill(rowIsCovered, 0);
        Arrays.fill(colIsCovered, 0);
    }

    public static void main(String[] args) {

        // the problem is written in the form of a matrix
        double[][] dataMatrix = {
                //col0  col1  col2  col3
                {70, 40, 20, 55},  //row0
                {65, 60, 45, 90},  //row1
                {30, 45, 50, 75},  //row2
                {25, 30, 55, 40}   //row3
        };

        //find optimal assignment
        HungarianAlgorithm ha = new HungarianAlgorithm(dataMatrix);
        int[][] assignment = ha.findOptimalAssignment();

        if (assignment.length > 0) {
            // print assignment
            for (int[] ints : assignment) {
                System.out.print("Col" + ints[0] + " => Row" + ints[1] + " (" + dataMatrix[ints[0]][ints[1]] + ")");
                System.out.println();
            }
        } else {
            System.out.println("no assignment found!");
        }
    }
}