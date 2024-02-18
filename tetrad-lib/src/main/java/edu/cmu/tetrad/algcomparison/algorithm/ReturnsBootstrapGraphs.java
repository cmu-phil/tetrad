package edu.cmu.tetrad.algcomparison.algorithm;

import edu.cmu.tetrad.graph.Graph;

import java.util.List;

/**
 * <p>ReturnsBootstrapGraphs interface.</p>
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public interface ReturnsBootstrapGraphs {

    /**
     * Returns the bootstrap graphs.
     *
     * @return the bootstrap graphs.
     */
    List<Graph> getBootstrapGraphs();

}

