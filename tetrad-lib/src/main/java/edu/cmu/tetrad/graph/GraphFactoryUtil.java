package edu.cmu.tetrad.graph;

import java.util.List;

/**
 * Utility class providing factory methods for creating instances of different types of
 * Graph objects. This class allows for the creation of graphs with specific configurations
 * or replication policies.
 */
public class GraphFactoryUtil {

    /**
     * Private constructor to prevent instantiation of the utility class.
     * This class is intended to provide static factory methods for creating Graph instances
     * and cannot be instantiated.
     */
    private GraphFactoryUtil() {

    }

    /**
     * Creates a new instance of a Graph with the specified replication policy.
     * The graph is initialized with an empty list of nodes.
     *
     * @param replicating whether the graph should support replication functionality.
     * @return a new Graph instance configured according to the replication policy.
     */
    public static Graph newGraph(boolean replicating) {
        return newGraph(List.of(), replicating);
    }

    /**
     * Creates a new instance of a Graph initialized with the provided list of nodes.
     * Depending on the `replicating` flag, the method returns either a replicable graph
     * or a standard edge-list-based graph.
     *
     * @param nodes the list of nodes to initialize the graph with
     * @param replicating whether the graph should support replication functionality
     * @return a new Graph instance configured based on the replication policy and node list
     */
    public static Graph newGraph(List<Node> nodes, boolean replicating) {
        if (replicating) {
            return new ReplicatingGraph(nodes, new LagReplicationPolicy());
        } else {
            return new EdgeListGraph(nodes);
        }
    }

    /**
     * Creates a new instance of a Graph based on the given graph. If the input graph is an instance
     * of ReplicatingGraph, a new ReplicatingGraph is created as a copy of the input graph. Otherwise,
     * a new EdgeListGraph is created initialized with the nodes from the input graph.
     *
     * @param graph the source graph used to create a new graph instance
     * @return a new Graph instance, either a ReplicatingGraph or an EdgeListGraph, based on the type
     *         of the provided graph
     */
    public static Graph newGraph(Graph graph) {
        if (graph instanceof ReplicatingGraph) {
            return new ReplicatingGraph((ReplicatingGraph) graph);
        } else {
            return new EdgeListGraph(graph.getNodes());
        }
    }
}