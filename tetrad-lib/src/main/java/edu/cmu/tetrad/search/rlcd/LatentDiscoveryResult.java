package edu.cmu.tetrad.search.rlcd;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import java.util.List;

final class LatentDiscoveryResult {
    private final Graph graph;
    private final List<Node> nodes;
    private final int[][] adjacency;

    LatentDiscoveryResult(Graph graph, List<Node> nodes, int[][] adjacency) {
        this.graph = graph;
        this.nodes = nodes;
        this.adjacency = adjacency;
    }

    Graph toTetradGraph() {
        return graph;
    }

    public List<Node> getNodes() {
        return nodes;
    }

    public int[][] getAdjacency() {
        return adjacency;
    }
}