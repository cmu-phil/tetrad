package edu.cmu.tetrad.algcomparison.utils;

import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;

/**
 * Tags an algorithm as using an independence wrapper.
 *
 * @author Jeremy Espino MD Created  7/13/17 2:25 PM
 */
public interface TakesIndependenceWrapper {

    /**
     * Returns the independence wrapper.
     *
     * @return the independence wrapper.
     */
    IndependenceWrapper getIndependenceWrapper();

    /**
     * Sets the independence wrapper.
     *
     * @param independenceWrapper the independence wrapper.
     */
    void setIndependenceWrapper(IndependenceWrapper independenceWrapper);

}
