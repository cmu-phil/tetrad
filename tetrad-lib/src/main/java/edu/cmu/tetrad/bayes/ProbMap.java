package edu.cmu.tetrad.bayes;

import edu.cmu.tetrad.util.Vector;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a probability map. A probability map is a map from a unique integer index for a particular node to the *
 * probability of that node taking on that value, where NaN's are not stored.
 */
public class ProbMap {

    /**
     * Constructs a new probability map, a map from a unique integer index for a particular node to the probability of
     * that node taking on that value, where NaN's are not stored.
     */
    private final Map<Integer, Double> map = new HashMap<>();
    private final int numRows;
    private final int numColumns;

    /**
     * Constructs a new probability map, a map from a unique integer index for a particular node to the probability of
     * that node taking on that value, where NaN's are not stored. This probability map assumes that there is a certain
     * number of rows and a certain number of columns in the table.
     */
    public ProbMap(int numRows, int numColumns) {
        if (numRows < 1 || numColumns < 1) {
            throw new IllegalArgumentException("Number of rows and columns must be at least 1.");
        }

        this.numRows = numRows;
        this.numColumns = numColumns;
    }

    public ProbMap(double[][] probMatrix) {
        numRows = probMatrix.length;
        numColumns = probMatrix[0].length;

        for (int i = 0; i < numRows; i++) {
            if (probMatrix[i].length != numColumns) {
                throw new IllegalArgumentException("All rows must have the same number of columns.");
            }
        }

        for (int i = 0; i < numRows; i++) {
            for (int j = 0; j < numColumns; j++) {
                map.put(i * numColumns + j, probMatrix[i][j]);
            }
        }
    }

    /**
     * Returns the probability of the node taking on the value specified by the given row and column.
     */
    public double get(int row, int column) {
        if (row < 0 || row >= numRows || column < 0 || column >= numColumns) {
            throw new IllegalArgumentException("Row and column must be within bounds.");
        }

        return map.get(row * numColumns + column);
    }

    /**
     * Sets the probability of the node taking on the value specified by the given row and column to the given value.
     */
    public void set(int row, int column, double value) {
        if (row < 0 || row >= numRows || column < 0 || column >= numColumns) {
            throw new IllegalArgumentException("Row and column must be within bounds.");
        }

        map.put(row * numColumns + column, value);
    }

    /**
     * Returns the number of rows in the probability map.
     *
     * @return the number of rows in the probability map.
     */
    public int getNumRows() {
        return numRows;
    }

    /**
     * Returns the number of columns in the probability map.
     *
     * @return the number of columns in the probability map.
     */
    public int getNumColumns() {
        return numColumns;
    }

    public void assignRow(int rowIndex, Vector vector) {
        if (vector.size() != numColumns) {
            throw new IllegalArgumentException("Vector must have the same number of columns as the probability map.");
        }

        for (int i = 0; i < numColumns; i++) {
            set(rowIndex, i, vector.get(i));
        }
    }
}
