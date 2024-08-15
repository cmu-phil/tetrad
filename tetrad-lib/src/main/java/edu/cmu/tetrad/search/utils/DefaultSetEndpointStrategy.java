package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

/**
 * The DefaultSetEndpointStrategy class implements the SetEndpointStrategy interface and provides a default strategy for
 * setting the endpoint of an edge in a graph.
 */
public class DefaultSetEndpointStrategy implements SetEndpointStrategy {

    /**
     * Creates a new instance of DefaultSetEndpointStrategy.
     */
    public DefaultSetEndpointStrategy() {
    }

    /**
     * Sets the endpoint of a graph given the two nodes and the desired endpoint.
     *
     * @param graph    the graph in which the endpoint is being set
     * @param a        the starting node of the endpoint
     * @param b        the ending node of the endpoint
     * @param endpoint the desired endpoint value
     */
    @Override
    public void setEndpoint(Graph graph, Node a, Node b, Endpoint endpoint) {
        graph.setEndpoint(a, b, endpoint);
    }
}
