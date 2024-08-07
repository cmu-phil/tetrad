package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

public interface SetEndpointStrategy {
    void setEndpoint(Graph graph, Node a, Node b, Endpoint arrow);
}
