package edu.cmu.tetrad.algcomparison.utils;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;

/**
 * Tags an algorithm that can take an external graph as input.
 *
 * @author josephramsey
 */
public interface TakesExternalGraph {

    void setExternalGraph(Algorithm algorithm);

}
