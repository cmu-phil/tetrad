package edu.cmu.tetrad.search.rlcd;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.search.test.IndependenceTest;

/**
 * For Phase 1 variants that use CI tests (PC/FCI) instead of FGES.
 */
public interface IndependenceTestFactory {

    /**
     * Creates an independence test based on the provided dataset and significance level.
     *
     * @param dataSet The dataset to be utilized for the independence test, represented as a {@link DataSet} object.
     * @param alpha The significance level (alpha value) for the independence test; typically a value between 0 and 1.
     * @return An {@link IndependenceTest} instance configured with the given dataset and significance level.
     */
    IndependenceTest create(DataSet dataSet, double alpha);
}