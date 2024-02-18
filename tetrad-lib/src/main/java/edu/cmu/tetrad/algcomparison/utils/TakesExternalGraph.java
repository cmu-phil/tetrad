package edu.cmu.tetrad.algcomparison.utils;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;

/**
 * Tags an algorithm that can take an external graph as input.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public interface TakesExternalGraph {

    /**
     * <p>setExternalGraph.</p>
     *
     * @param algorithm a {@link edu.cmu.tetrad.algcomparison.algorithm.Algorithm} object
     */
    void setExternalGraph(Algorithm algorithm);

}
