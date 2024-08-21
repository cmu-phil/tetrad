package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.FciOrientDijkstra;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;

/**
 * A modified implementation of Dijkstra's shortest path algorithm for the R5 and R9 rules from Zhang, J. (2008), On the
 * completeness of orientation rules for causal discovery in the presence of latent confounders and selection bias,
 * Artificial Intelligence, 172(16-17), 1873-1896. These are rules that involve finding uncovered paths of various sorts
 * in a graph; we use a modificiation of Dejkstra as a fast implementation of that requirement suitable for large
 * graphs.
 * <p>
 * We report distances as total weights along the shortest path from the start node to the y node, where by default the
 * weight of each edge is 1. We report unreachable nodes as at a distance of Integer.MAX_VALUE. Edges in the graph are
 * dynamically calculated by the algorithm using two methods--looking for o-o edges only, suitable for the R5 rule, and
 * looking for edges along potentially directed paths (i.e., semidirected paths), suitable for the R9 rule. The end node
 * is used to stop the algorithm once that node has been visited, so that a shortest path has been found.
 * <p>
 * The algorithm is constrained to avoid certain paths. The start *-* end edge itself and start *-* z *-* end paths are
 * avoided, to avoid length 1 or length 2 paths. Also, covered triples, z *-* r *-* w, z *-* w, are avoided to implement
 * the constraint that only uncovered paths are considered. Coverings of end *-* start *-* z and start *-* end *-* w are
 * also avoided, as specified for R5 and R9.
 *
 * @author josephramsey 2024-8-6
 */
public class R5R9Dijkstra {

    /**
     * Prevents instantiation of this utility class.
     */
    private R5R9Dijkstra() {
    }

    /**
     * Finds shortest distances from a x node to all other nodes in a graph, subject to the following constraints. (1)
     * Length 1 paths are not considered. (2) Length 2 paths are not considered. (3) Covered triples are not considered.
     * (4) The y node is used to stop the algorithm once that node has been visited. (5) The graph is assumed to be
     * undirected.
     * <p>
     * Nodes that are not reached by the algorithm are reported as being at a distance of Integer.MAX_VALUE.
     *
     * @param graph The graph to search; should include only the relevant edge in the graph.
     * @param x     The starting node.
     * @param y     The ending node. The algorithm will stop when this node is reached.
     * @return A map of distances from the start node to each node in the graph, and a map of predecessors for each
     * node.
     */
    public static Pair<Map<Node, Integer>, Map<Node, Node>> distances(Graph graph, Node x, Node y) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph cannot be null.");
        }

        if (x == null || y == null) {
            throw new IllegalArgumentException("x and y cannot be null.");
        }

        Map<Node, Node> predecessors = new HashMap<>();

        Map<Node, Integer> distances = new HashMap<>();
        PriorityQueue<DijkstraNode> priorityQueue = new PriorityQueue<>(Comparator.comparingInt(DijkstraNode::getDistance));
        Set<Node> visited = new HashSet<>();

        // Initialize distances
        for (Node node : graph.getNodes()) {
            distances.put(node, Integer.MAX_VALUE);
            predecessors.put(node, null);
        }

        distances.put(x, 0);
        priorityQueue.add(new DijkstraNode(x, 0));

        while (!priorityQueue.isEmpty()) {
            DijkstraNode currentDijkstraNode = priorityQueue.poll();
            Node currentVertex = currentDijkstraNode.node;

            if (!visited.add(currentVertex)) {
                continue;
            }

            for (DijkstraEdge dijkstraEdge : graph.getNeighbors(currentVertex)) {
                Node predecessor = predecessors.get(currentVertex);

                // Skip length-1 paths.
                if (dijkstraEdge.getToNode() == y && currentVertex == x) {
                    continue;
                }

                if (dijkstraEdge.getToNode() == x && currentVertex == y) {
                    continue;
                }

                // Skip length-2 paths.
                if (dijkstraEdge.getToNode() == y && predecessor == x) {
                    continue;
                }

                if (dijkstraEdge.getToNode() == x && predecessor == y) {
                    continue;
                }

                // Skip covered triples.
                if (adjacent(graph, dijkstraEdge.getToNode(), predecessor)) {
                    continue;
                }

                Node neighbor = dijkstraEdge.getToNode();
                int newDist = distances.get(currentVertex) + dijkstraEdge.getWeight();

                distances.putIfAbsent(neighbor, Integer.MAX_VALUE);

                if (newDist < distances.get(neighbor)) {
                    distances.put(neighbor, newDist);
                    predecessors.put(neighbor, currentVertex);
                    priorityQueue.add(new DijkstraNode(neighbor, newDist));

                    if (dijkstraEdge.getToNode().equals(y)) {
                        return Pair.of(distances, predecessors);
                    }
                }
            }
        }

        return Pair.of(distances, predecessors);
    }

    /**
     * Determines whether there is an edge from x to y in the Dijkstra graph.
     *
     * @param graph The graph to search.
     * @param x     The one node.
     * @param y     The other node.
     * @return True if there is an edge from x to y in the graph.
     */
    private static boolean adjacent(Graph graph, Node x, Node y) {
        List<DijkstraEdge> dijkstraEdges = graph.getNeighbors(x);

        for (DijkstraEdge dijkstraEdge : dijkstraEdges) {
            if (dijkstraEdge.getToNode().equals(y)) {
                return true;
            }
        }

        return false;
    }

    /**
     * A simple test of the Dijkstra algorithm. TODO This could be moved to a unit test.
     *
     * @param args Command line arguments.
     */
    public static void main(String[] args) {
        edu.cmu.tetrad.graph.Graph graph = new edu.cmu.tetrad.graph.EdgeListGraph();

        Map<String, edu.cmu.tetrad.graph.Node> index = new HashMap<>();

        for (int i = 1; i <= 10; i++) {
            Node node = new GraphNode(i + "");
            index.put(i + "", node);
        }

        graph.addNondirectedEdge(index.get("1"), index.get("3"));


        graph.addNondirectedEdge(index.get("1"), index.get("2"));
        graph.addNondirectedEdge(index.get("2"), index.get("3"));

        graph.addNondirectedEdge(index.get("1"), index.get("4"));
        graph.addNondirectedEdge(index.get("4"), index.get("5"));
        graph.addNondirectedEdge(index.get("5"), index.get("3"));

        // Let's cover some edges.
//        graph.addEdge(index.get("1"), index.get("3"), 1);
//        graph.addEdge(index.get("2"), index.get("4"), 1);
//        graph.addEdge(index.get("2"), index.get("4"), 1);

        Map<Node, Node> predecessors = new HashMap<>();

        boolean uncovered = true;

        Graph _graph = new Graph(graph, R5R9Dijkstra.Rule.R5);

        Map<Node, Integer> distances = R5R9Dijkstra.distances(_graph, index.get("1"), index.get("3")).getLeft();

        for (Map.Entry<Node, Integer> entry : distances.entrySet()) {
            System.out.println("Distance from 1 to " + entry.getKey() + " is " + entry.getValue());
        }

        Node start = index.get("1");
        Node end = index.get("3");
        List<Node> path = FciOrientDijkstra.getPath(predecessors, start, end);
        System.out.println("Shortest path " + path);
    }

    /**
     * The rule that is being implemented, R5 or R9.
     */
    public enum Rule {R5, R9}

    /**
     * Represents a graph for Dijkstra's algorithm. This wraps a Tetrad graph and provides methods to get neighbors and
     * nodes. The nodes are just the nodes in the underlying Tetrad graph, and neighbors are determined dynamically
     * based on the edges in the graph. There are two modes of operation, one for potentially directed graphs and one
     * for nondirected graphs. In the potentially directed mode, the algorithm will only traverse edges that are
     * semidirected, i.e., edges that are all directable in one direction but not the other. This is suitable for R9. In
     * the nondirected mode, the algorithm will traverse nondirected edges only in both directions. This is suitable for
     * R5.
     */
    public static class Graph {
        /**
         * The rule to implement, R5 or R9.
         */
        private final Rule rule;
        /**
         * The Tetrad graph to wrap.
         */
        private final edu.cmu.tetrad.graph.Graph tetradGraph;

        /**
         * Represents a graph for Dijkstra's algorithm. This wraps a Tetrad graph and provides methods to get neighbors
         * and nodes. The nodes are just the nodes in the underlying Tetrad graph, and neighbors are determined
         * dynamically based on the edges in the graph.
         *
         * @param graph The Tetrad graph to wrap.
         * @param rule  The rule to implement, R5 or R9.
         */
        public Graph(edu.cmu.tetrad.graph.Graph graph, Rule rule) {
            this.tetradGraph = graph;
            this.rule = rule;
        }

        /**
         * Retrieves the filtered neighbors of a given node.
         *
         * @param node The node for which to retrieve the neighbors.
         * @return The filtered neighbors of the given node.
         */
        public List<DijkstraEdge> getNeighbors(Node node) {
            List<DijkstraEdge> filteredNeighbors = new ArrayList<>();

            // Peter--here is the choice point between R5 and R9.
            if (rule == Rule.R9) {

                // For R9 we follow semidirected (potentially directed) paths.

                // R9
                Set<edu.cmu.tetrad.graph.Edge> edges = tetradGraph.getEdges(node);

                // We need to filter these neighbors to allow only those that pass using TraverseSemidirected.
                for (Edge edge : edges) {
                    Node other = Edges.traverseSemiDirected(node, edge);

                    if (other == null) {
                        continue;
                    }

                    filteredNeighbors.add(new DijkstraEdge(other, 1));
                }

                return filteredNeighbors;
            } else if (rule == Rule.R5) {

                // For R5 we follow nondirected paths.

                // R5
                Set<edu.cmu.tetrad.graph.Edge> edges = tetradGraph.getEdges(node);

                // We need to filter these neighbors to allow only those that pass using TraverseSemidirected.
                for (Edge edge : edges) {
                    Node other = Edges.traverseNondirected(node, edge);

                    if (other == null) {
                        continue;
                    }

                    filteredNeighbors.add(new DijkstraEdge(other, 1));
                }

                return filteredNeighbors;
            } else {
                throw new IllegalArgumentException("Rule must be R5 or R9.");
            }
        }

        /**
         * Retrieves the nodes in the graph.
         *
         * @return A set of nodes in the graph.
         */
        public Set<Node> getNodes() {
            return new HashSet<>(tetradGraph.getNodes());
        }
    }

    /**
     * Represents a node in Dijkstra's algorithm. The weight of the edge from the start is stored in the distance field
     * and is modified by the algorithm.
     */
    public static class DijkstraEdge {
        /**
         * Represents the node to which the edge connects.
         */
        private final Node toNode;
        /**
         * Represents the weight of an edge in Dijkstra's algorithm.
         */
        private final int weight;

        /**
         * Represents an edge connecting two nodes in Dijkstra's algorithm. The edge has a weight that represents the
         * cost of traversing from one node to another.
         * <p>
         * Immutable.
         *
         * @param y      the to-node.
         * @param weight the weight of the edge.
         */
        public DijkstraEdge(Node y, int weight) {
            if (y == null) {
                throw new IllegalArgumentException("y cannot be null.");
            }

            if (weight <= 0) {
                throw new IllegalArgumentException("Weight must be positive.");
            }

            this.toNode = y;
            this.weight = weight;
        }

        /**
         * Retrieves to-node represented by this DijkstraEdge.
         *
         * @return the to-node.
         */
        public Node getToNode() {
            return toNode;
        }

        /**
         * Retrieves the weight of the edge represented by this DijkstraEdge.
         *
         * @return the weight of the edge
         */
        public int getWeight() {
            return weight;
        }

        /**
         * Returns a string representation of the DijkstraEdge object.
         *
         * @return a string representation of the DijkstraEdge object
         */
        public String toString() {
            return "DijkstraEdge{" + "y=" + toNode + ", weight=" + weight + '}';
        }
    }

    /**
     * Represents a node in Dijkstra's algorithm. The distance of the nodes from the start is stored in the distance.
     * <p>
     * Immutable.
     */
    private static class DijkstraNode {
        /**
         * Represents an object with a name, node type, and position that can serve as a node in a graph.
         */
        private final Node node;
        /**
         * Represents the distance of a node from the start in Dijkstra's algorithm. The distance is an integer value.
         * This variable is private and final, meaning it cannot be modified once assigned a value.
         */
        private final int distance;

        /**
         * Represents a node in Dijkstra's algorithm. The distance of the nodes from the start is stored in the distance
         * field and is modified by the algorithm.
         *
         * @param vertex   the node represented by this DijkstraNode.
         * @param distance the distance of the node from the start.
         */
        public DijkstraNode(Node vertex, int distance) {
            this.node = vertex;
            this.distance = distance;
        }

        /**
         * Retrieves the distance of the node.
         *
         * @return the distance of the node.
         */
        public int getDistance() {
            return distance;
        }
    }
}

