package edu.cmu.tetrad.bayes;

import edu.cmu.tetrad.util.TetradSerializable;

/**
 * An interface representing a map of probabilities or counts for nodes in a Bayesian network. Implementations of this
 * interface should provide methods to get the probability or count for a node at a given row and column, as well as
 * methods to retrieve the number of rows and columns in the map.
 * <p>
 * This interface extends the TetradSerializable interface, indicating that implementations should be serializable and
 * follow certain guidelines for compatibility across different versions of Tetrad.
 *
 * @author josephramsey
 * @see CptMapProbs
 * @see CptMapCounts
 */
public interface CptMap extends TetradSerializable {

    /**
     * Retrieves the value at the specified row and column in the CptMap.
     *
     * @param row    the row index of the value to retrieve.
     * @param column the column index of the value to retrieve.
     * @return the value at the specified row and column in the CptMap.
     */
    double get(int row, int column);

    /**
     * Retrieves the number of rows in the CptMap.
     *
     * @return the number of rows in the CptMap.
     */
    int getNumRows();

    /**
     * Retrieves the number of columns in the CptMap.
     *
     * @return the number of columns in the CptMap.
     */
    int getNumColumns();
}
