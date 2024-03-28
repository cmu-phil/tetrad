package edu.cmu.tetrad.bayes;

import edu.cmu.tetrad.util.TetradSerializable;
import edu.cmu.tetrad.util.Vector;

import java.io.Serial;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a conditional probability table (CPT) in a Bayes net. This represents the CPT as a  map from a unique
 * integer index for a particular node to the probability of that node taking on that value, where NaN's are not
 * stored.
 * <p>
 * The goal of this is to allow huge conditional probability tables to be stored in a compact way when estimated from
 * finite samples. The idea is that the CPT is stored as a map from a unique integer index for a particular node to the
 * probability of that node taking on that value, where NaN's are not stored. This is useful because the CPTs can be
 * huge and sparse, and this allows them to be stored in a compact way. The unique integer index for a particular node
 * is calculated as follows: row * numColumns + column, where row is the row of the node and column is the column of the
 * node.
 */
public class CptMap implements TetradSerializable {
    @Serial
    private static final long serialVersionUID = 23L;
    /**
     * Constructs a new probability map, a map from a unique integer index for a particular node to the probability of
     * that node taking on that value, where NaN's are not stored.
     */
    private final Map<Integer, Double> map = new HashMap<>();
    /**
     * The number of rows in the table.
     */
    private final int numRows;
    /**
     * The number of columns in the table.
     */
    private final int numColumns;

    /**
     * Constructs a new probability map, a map from a unique integer index for a particular node to the probability of
     * that node taking on that value, where NaN's are not stored. This probability map assumes that there is a certain
     * number of rows and a certain number of columns in the table.
     *
     * @param numRows    the number of rows in the table
     * @param numColumns the number of columns in the table
     */
    public CptMap(int numRows, int numColumns) {
        if (numRows < 1 || numColumns < 1) {
            throw new IllegalArgumentException("Number of rows and columns must be at least 1.");
        }

        this.numRows = numRows;
        this.numColumns = numColumns;
    }

    /**
     * Constructs a new probability map based on the given 2-dimensional array.
     *
     * @param probMatrix the 2-dimensional array representing the probability matrix
     * @throws IllegalArgumentException if the number of columns in any row is different
     */
    public CptMap(double[][] probMatrix) {
        if (probMatrix == null || probMatrix.length == 0 || probMatrix[0].length == 0) {
            throw new IllegalArgumentException("Probability matrix must have at least one row and one column.");
        }

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
     *
     * @param row    the row of the node
     * @param column the column of the node
     * @return the probability of the node taking on the value specified by the given row and column
     */
    public double get(int row, int column) {
        if (row < 0 || row >= numRows || column < 0 || column >= numColumns) {
            throw new IllegalArgumentException("Row and column must be within bounds.");
        }

        int key = row * numColumns + column;

        if (!map.containsKey(key)) {
            return Double.NaN;
        }

        return map.get(key);
    }

    /**
     * Sets the probability of the node taking on the value specified by the given row and column to the given value.
     *
     * @param row    the row of the node
     * @param column the column of the node
     * @param value  the probability of the node taking on the value specified by the given row and column (NaN to
     *               remove the value)
     */
    public void set(int row, int column, double value) {
        if (row < 0 || row >= numRows || column < 0 || column >= numColumns) {
            throw new IllegalArgumentException("Row and column must be within bounds.");
        }

        int key = row * numColumns + column;

        if (Double.isNaN(value)) {
            map.remove(key);
            return;
        }

        map.put(key, value);
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

    /**
     * Assigns the values in the provided vector to a specific row in the probability map.
     *
     * @param rowIndex the index of the row to be assigned
     * @param vector   the vector containing the values to be assigned to the row
     * @throws IllegalArgumentException if the size of the vector is not equal to the number of columns in the
     *                                  probability map
     */
    public void assignRow(int rowIndex, Vector vector) {
        if (vector.size() != numColumns) {
            throw new IllegalArgumentException("Vector must have the same number of columns as the probability map.");
        }

        for (int i = 0; i < numColumns; i++) {
            set(rowIndex, i, vector.get(i));
        }
    }
}
