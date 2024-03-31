package edu.cmu.tetrad.bayes;

import edu.cmu.tetrad.data.DataSet;

import java.io.Serial;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a conditional probability table (CPT) in a Bayes net. This represents the CPT as a map from a unique
 * integer index for a particular node to the cell count for that node, where 0's are not stored. Row counts are also
 * stored, so that the probability of a cell can be calculated. A prior cell count of 0 is assumed for all cells,
 * but this may be set by the user to any non-negative count. (A prior count of 0 is equivalent to a maximum
 * likelihood estimate.)
 *
 * @author josephramsey
 */
public class CptMapCounts implements CptMap {
    @Serial
    private static final long serialVersionUID = 23L;
    /**
     * Constructs a new count map, a map from a unique integer index for a particular node to the count for that value,
     * where 0's are not stored.
     */
    private final Map<Integer, Integer> cellCounts = new HashMap<>();
    /**
     * Constructs a new row count map, a map from a unique integer index for a particular node to the count for that
     * row, where 0's are not stored.
     */
    private final Map<Integer, Integer> rowCounts = new HashMap<>();
    /**
     * The number of rows in the table.
     */
    private final int numRows;
    /**
     * The number of columns in the table.
     */
    private final int numColumns;
    /**
     * The prior count for all cells.
     */
    private int priorCount = 1;

    /**
     * Constructs a new probability map, a map from a unique integer index for a particular node to the probability of
     * that node taking on that value, where NaN's are not stored. This probability map assumes that there is a certain
     * number of rows and a certain number of columns in the table.
     *
     * @param numRows    the number of rows in the table
     * @param numColumns the number of columns in the table
     */
    public CptMapCounts(int numRows, int numColumns) {
        if (numRows < 1 || numColumns < 1) {
            throw new IllegalArgumentException("Number of rows and columns must be at least 1.");
        }

        this.numRows = numRows;
        this.numColumns = numColumns;
    }

    /**
     * Constructs a new CptMap based on counts from a given dataset.
     *
     * @param data the DataSet object representing the probability matrix
     * @throws IllegalArgumentException if the data set is null or not discrete
     */
    public CptMapCounts(DataSet data) {
        if (data == null) {
            throw new IllegalArgumentException("Probability matrix must have at least one row and one column.");
        }

        if (!data.isDiscrete()) {
            throw new IllegalArgumentException("Data set must be discrete.");

        }

        numRows = data.getNumRows();
        numColumns = data.getNumColumns();

        for (int i = 0; i < numRows; i++) {
            for (int j = 0; j < numColumns; j++) {
                int key = i * numColumns + j;

                if (data.getInt(i, j) == -1) {
                    continue;
                }

                if (data.getInt(i, j) == 0) {
                    continue;
                }

                if (!cellCounts.containsKey(key)) {
                    cellCounts.put(key, 1);
                } else {
                    cellCounts.put(key, cellCounts.get(key) + 1);
                }

                if (!rowCounts.containsKey(i)) {
                    rowCounts.put(i, 1);
                } else {
                    rowCounts.put(i, rowCounts.get(i) + 1);
                }
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
    @Override
    public double get(int row, int column) {
        if (row < 0 || row >= numRows || column < 0 || column >= numColumns) {
            throw new IllegalArgumentException("Row and column must be within bounds.");
        }

        int key = row * numColumns + column;
        int rowCount = rowCounts.getOrDefault(row, 0);
        int cellCount = cellCounts.getOrDefault(key, 0);
        rowCount += priorCount * numColumns;
        cellCount += priorCount;
        return cellCount / (double) rowCount;
    }

    public void addCounts(int row, int column, int count) {
        if (row < 0 || row >= numRows || column < 0 || column >= numColumns) {
            throw new IllegalArgumentException("Row and column must be within bounds.");
        }

        int key = row * numColumns + column;

        if (!cellCounts.containsKey(key)) {
            cellCounts.put(key, count);
        } else {
            cellCounts.put(key, cellCounts.get(key) + count);
        }

        if (!rowCounts.containsKey(row)) {
            rowCounts.put(row, count);
        } else {
            rowCounts.put(row, rowCounts.get(row) + count);
        }
    }

    /**
     * Returns the number of rows in the probability map.
     *
     * @return the number of rows in the probability map.
     */
    @Override
    public int getNumRows() {
        return numRows;
    }

    /**
     * Returns the number of columns in the probability map.
     *
     * @return the number of columns in the probability map.
     */
    @Override
    public int getNumColumns() {
        return numColumns;
    }

    /**
     * The prior count for all cells.
     */
    public int getPriorCount() {
        return priorCount;
    }

    /**
     * The prior count for all cells.
     */
    public void setPriorCount(int priorCount) {
        this.priorCount = priorCount;
    }
}
