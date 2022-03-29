package edu.cmu.tetrad.algcomparison.utils;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.graph.Graph;

/**
 * Tags an algorithm that can take an external graph as input.
 *
 * @author jdramsey
 */
public interface TakesExternalGraph {

    Graph getExternalGraph();

    void setExternalGraph(Graph externalGraph);

    void setExternalGraph(Algorithm algorithm);

}
