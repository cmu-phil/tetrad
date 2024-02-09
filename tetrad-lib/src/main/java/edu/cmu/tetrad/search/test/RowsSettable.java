package edu.cmu.tetrad.search.test;

import java.util.List;

/**
 * Interface for tests that can have their rows set on the fly.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public interface RowsSettable {

    /**
     * Gets the rows to use for the test. These rows over override testwise deletion if set.
     *
     * @return The rows to use for the test. Can be null.
     */
    List<Integer> getRows();

    /**
     * Sets the rows to use for the test. This will override testwise deletion.
     *
     * @param rows The rows to use for the test. Can be null.
     */
    void setRows(List<Integer> rows);
}
