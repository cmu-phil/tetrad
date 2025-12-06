package edu.cmu.tetrad.search.rlcd;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.search.test.IndependenceTest;

/**
 * For Phase 1 variants that use CI tests (PC/FCI) instead of FGES.
 */
public interface IndependenceTestFactory {
    IndependenceTest create(DataSet dataSet, double alpha);
}