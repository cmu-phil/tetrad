package edu.cmu.tetrad.graph;

import java.util.List;

public class GraphFactoryUtil {
    public static Graph newGraph(boolean repeating) {
        return newGraph(List.of(), repeating);
    }

    public static Graph newGraph(List<Node> nodes, boolean repeating) {
        if (repeating) {
            return new ReplicatingGraph(nodes, new LagReplicationPolicy());
        } else {
            return new EdgeListGraph(nodes);
        }
    }
}