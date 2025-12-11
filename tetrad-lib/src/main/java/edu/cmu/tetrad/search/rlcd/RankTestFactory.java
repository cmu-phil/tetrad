package edu.cmu.tetrad.search.rlcd;

import edu.cmu.tetrad.data.DataSet;

/**
 * Factory for RankTest objects, so RLCD can request a rank test given
 * the current DataSet.
 */
public interface RankTestFactory {

    /**
     * Creates a RankTest instance for the given DataSet.
     *
     * @param dataSet the DataSet for which the RankTest instance is to be created
     * @return a RankTest instance configured for the provided DataSet
     */
    RankTest create(DataSet dataSet);
}