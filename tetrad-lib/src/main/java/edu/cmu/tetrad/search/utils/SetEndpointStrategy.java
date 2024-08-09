package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

/**
 * The SetEndpointStrategy interface provides a strategy for setting the endpoint of an edge in a graph.
 */
public interface SetEndpointStrategy {

    /**
     * Sets the endpoint of a graph given the two nodes and the desired endpoint.
     *
     * @param graph the graph in which the endpoint is being set
     * @param a     the starting node of the endpoint
     * @param b     the ending node of the endpoint
     * @param arrow the desired endpoint value
     */
    void setEndpoint(Graph graph, Node a, Node b, Endpoint arrow);
}
