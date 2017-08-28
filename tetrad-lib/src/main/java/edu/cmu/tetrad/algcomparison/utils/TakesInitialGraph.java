package edu.cmu.tetrad.algcomparison.utils;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;

/**
 * Tags an algorithm that can take an initial graph as input.
 *
 * @author jdramsey
 */
public interface TakesInitialGraph {
	
	public Graph getInitialGraph();
	
	public void setInitialGraph(Graph initialGraph);
	
    void setInitialGraph(Algorithm algorithm);

}
