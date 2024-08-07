package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

public class DefaultSetEndpointStrategy implements SetEndpointStrategy {
    @Override
    public void setEndpoint(Graph graph, Node a, Node b, Endpoint endpoint) {
        graph.setEndpoint(a, b, endpoint);
    }
}
