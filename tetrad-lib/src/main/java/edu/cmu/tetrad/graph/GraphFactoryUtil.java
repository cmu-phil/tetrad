package edu.cmu.tetrad.graph;

import java.util.List;

public class GraphFactoryUtil {
    public static Graph newGraph(boolean replicating) {
        return newGraph(List.of(), replicating);
    }

    public static Graph newGraph(List<Node> nodes, boolean replicating) {
        if (replicating) {
            return new ReplicatingGraph(nodes, new LagReplicationPolicy());
        } else {
            return new EdgeListGraph(nodes);
        }
    }

    public static Graph newGraph(Graph graph) {
        if (graph instanceof ReplicatingGraph) {
            return new ReplicatingGraph(graph.getNodes(), ((ReplicatingGraph) graph).getReplicationPolicy());
        } else {
            return new EdgeListGraph(graph.getNodes());
        }
    }
}