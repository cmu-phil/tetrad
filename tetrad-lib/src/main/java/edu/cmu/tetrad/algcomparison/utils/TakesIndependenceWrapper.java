package edu.cmu.tetrad.algcomparison.utils;

import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;

/**
 * Author : Jeremy Espino MD Created  7/13/17 2:25 PM
 */
public interface TakesIndependenceWrapper {

    IndependenceWrapper getIndependenceWrapper();

    void setIndependenceWrapper(IndependenceWrapper independenceWrapper);

}
