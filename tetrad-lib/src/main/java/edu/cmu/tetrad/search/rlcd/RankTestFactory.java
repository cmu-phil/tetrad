package edu.cmu.tetrad.search.rlcd;

import edu.cmu.tetrad.data.DataSet;

/**
 * Factory for RankTest objects, so RLCD can request a rank test given
 * the current DataSet.
 */
public interface RankTestFactory {
    RankTest create(DataSet dataSet);
}