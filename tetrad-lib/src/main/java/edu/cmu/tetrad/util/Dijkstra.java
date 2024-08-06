package edu.cmu.tetrad.util;

import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.Node;

import java.util.*;

/**
 * A simple implementation of Dijkstra's algorithm for finding the shortest path in a graph. We are modifying the
 * algorithm to stop when an end node is reached. (The end node may be left unspecified, in which case the algorithm
 * will find the shortest path to all nodes in the graph.)
 * <p>
 * Weights should all be positive. We report distances as total weights along the shortest path from the start node to
 * the destination node. We report unreachable nodes as being a distance of Integer.MAX_VALUE. We assume the graph is
 * undirected. An end nodes may be specified, in which case, once the end node is reached, we report all further nodes
 * as being at a distance of Integer.MAX_VALUE.
 *
 * @author josephramsey, chat.
 */
public class Dijkstra {

    /**
     * Finds shortest distances from a start node to all other nodes in a graph. Unreachable nodes are reported as being
     * at a distance of Integer.MAX_VALUE. The graph is assumed to be undirected.
     *
     * @param graph        The graph to search; should include only the relevant edge in the graph.
     * @param start        The starting node.
     * @param predecessors A map to store the predecessors of each node in the shortest path.
     * @return A map of nodes to their shortest distances from the start node.
     */
    public static Map<Node, Integer> distances(Graph graph, Node start, Map<Node, Node> predecessors) {
        return distances(graph, start, null, predecessors, false, false);
    }

    /**
     * Finds shortest distances from a x node to all other nodes in a graph. Unreachable nodes are reported as being at
     * a distance of Integer.MAX_VALUE. The graph is assumed to be undirected. An y node may be specified, in which
     * case, once the y node is reached, all further nodes are reported as being at a distance of Integer.MAX_VALUE.
     *
     * @param graph               The graph to search; should include only the relevant edge in the graph.
     * @param x                   The starting node.
     * @param y                   The ending node. Maybe be null. If not null, the algorithm will stop when this node is
     *                            reached.
     * @param predecessors        A map to store the predecessors of each node in the shortest path.
     * @param uncovered           If true, the algorithm will not traverse edges y--z where an adjacency exists between
     *                            predecessor(y)  and z.
     * @param potentiallyDirected If true, the algorithm will traverse edges that are potentially directed.
     */
    public static Map<Node, Integer> distances(Graph graph, Node x, Node y,
                                               Map<Node, Node> predecessors, boolean uncovered, boolean potentiallyDirected) {
        Map<Node, Integer> distances = new HashMap<>();
        PriorityQueue<DijkstraNode> priorityQueue = new PriorityQueue<>(Comparator.comparingInt(dijkstraNode -> dijkstraNode.distance));
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
            Node currentVertex = currentDijkstraNode.vertex;

            if (!visited.add(currentVertex)) {
                continue;
            }

            for (DijkstraEdge dijkstraEdge : graph.getNeighbors(currentVertex, potentiallyDirected)) {
                Node predecessor = getPredecessor(predecessors, currentVertex);

                // Skip x o-o y itself.
                if (dijkstraEdge.getDestination() == y && currentVertex == x) {
                    continue;
                }

                if (dijkstraEdge.getDestination() == x && currentVertex == y) {
                    continue;
                }

                // If uncovered, skip triangles.
                if (uncovered) {
                    if (dijkstraEdge.getDestination() == y && predecessor == x) {
                        continue;
                    }

                    if (dijkstraEdge.getDestination() == x && predecessor == y) {
                        continue;
                    }
                }

                // If uncovered, skip covered triples.
                if (uncovered) {
                    if (adjacent(graph, dijkstraEdge.getDestination(), predecessor)) {
                        continue;
                    }
                }

                Node neighbor = dijkstraEdge.getDestination();
                int newDist = distances.get(currentVertex) + dijkstraEdge.getWeight();

                distances.putIfAbsent(neighbor, Integer.MAX_VALUE);

                if (newDist < distances.get(neighbor)) {
                    distances.put(neighbor, newDist);
                    predecessors.put(neighbor, currentVertex);
                    priorityQueue.add(new DijkstraNode(neighbor, newDist));
                    if (dijkstraEdge.getDestination().equals(y)) { // y can be null.
                        return distances;
                    }
                }
            }
        }

        return distances;
    }

    private static Node getPredecessor(Map<Node, Node> predecessors, Node currentVertex) {
        return predecessors.get(currentVertex);
    }

    private static boolean adjacent(Graph graph, Node currentVertex, Node predecessor) {
        List<DijkstraEdge> dijkstraEdges = graph.getNeighbors(currentVertex, false);

        for (DijkstraEdge dijkstraEdge : dijkstraEdges) {
            if (dijkstraEdge.getDestination().equals(predecessor)) {
                return true;
            }
        }

        return false;
    }

    public static List<Node> getPath(Map<Node, Node> predecessors,
                                     Node start, Node end) {
        List<Node> path = new ArrayList<>();
        for (Node at = end; at != null; at = predecessors.get(at)) {
            path.add(at);
        }
        Collections.reverse(path);
        if (path.get(0).equals(start)) {
            return path;
        } else {
            return null; // No path found
        }
    }

    /**
     * A simple test of the Dijkstra algorithm. This could be moved to a unit test. TODO
     *
     * @param args Command line arguments.
     */
    public static void main(String[] args) {
        Graph graph = new Graph(null);

        Map<String, Node> index = new HashMap<>();

        for (int i = 1; i <= 10; i++) {
            Node node = new GraphNode(i + "");
            index.put(i + "", node);
        }

        graph.addEdge(index.get("1"), index.get("3"), 1);


        graph.addEdge(index.get("1"), index.get("2"), 1);
        graph.addEdge(index.get("2"), index.get("3"), 1);

        graph.addEdge(index.get("1"), index.get("4"), 1);
        graph.addEdge(index.get("4"), index.get("5"), 1);
        graph.addEdge(index.get("5"), index.get("3"), 1);

        // Let's cover some edges.
//        graph.addEdge(index.get("1"), index.get("3"), 1);
//        graph.addEdge(index.get("2"), index.get("4"), 1);
//        graph.addEdge(index.get("2"), index.get("4"), 1);

        Map<Node, Node> predecessors = new HashMap<>();

        boolean uncovered = true;

        Map<Node, Integer> distances = Dijkstra.distances(graph, index.get("1"), index.get("3"),
                predecessors, uncovered, false);

        for (Map.Entry<Node, Integer> entry : distances.entrySet()) {
            System.out.println("Distance from 1 to " + entry.getKey() + " is " + entry.getValue());
        }

        List<Node> path = getPath(predecessors, index.get("1"), index.get("3"));
        System.out.println("Shortest path " + path);

    }

    /**
     * Represents a graph for Dijkstra's algorithm.
     */
    public static class Graph {
        private final Map<Node, List<DijkstraEdge>> adjacencyList;
        private edu.cmu.tetrad.graph.Graph _graph = null;

        public Graph(edu.cmu.tetrad.graph.Graph graph) {
            this.adjacencyList = new HashMap<>();
            this._graph = graph;
        }

        public void addEdge(Node source, Node destination, int weight) {
            this.adjacencyList.putIfAbsent(source, new ArrayList<>());
            this.adjacencyList.get(source).add(new DijkstraEdge(destination, weight));

            // For undirected graph, add the reverse edge as well
            this.adjacencyList.putIfAbsent(destination, new ArrayList<>());
            this.adjacencyList.get(destination).add(new DijkstraEdge(source, weight));
        }

        public List<DijkstraEdge> getNeighbors(Node node, boolean potentiallyDirected) {
            List<DijkstraEdge> filteredNeighbors = new ArrayList<>();

            if (potentiallyDirected) {
                if (_graph == null) {
                    throw new IllegalArgumentException("Graph is null.");
                }

                // We need to filter these neighbors to allow only those that pass using TraverseSemidirected.
                for (DijkstraEdge dijkstraEdge : this.adjacencyList.getOrDefault(node, new ArrayList<>())) {
                    Node other = Edges.traverseSemiDirected(node, _graph.getEdge(node, dijkstraEdge.getDestination()));

                    if (other == null) {
                        continue;
                    }

                    filteredNeighbors.add(new DijkstraEdge(other, 1));
                }

                adjacencyList.put(node, filteredNeighbors);
                return filteredNeighbors;
            } else {
                return this.adjacencyList.getOrDefault(node, new ArrayList<>());
            }
        }

        public Set<Node> getNodes() {
            return this.adjacencyList.keySet();
        }
    }

    /**
     * Represents a node in Dijkstra's algorithm. The distance of the nodes from the start is stored in the distance
     * field.
     */
    public static class DijkstraEdge {
        private final Node destination;
        private int weight;

        public DijkstraEdge(Node destination, int weight) {
            if (destination == null) {
                throw new IllegalArgumentException("Destination cannot be null.");
            }

            if (weight <= 0) {
                throw new IllegalArgumentException("Weight must be positive.");
            }

            this.destination = destination;
            this.weight = weight;
        }

        public Node getDestination() {
            return destination;
        }

        public int getWeight() {
            return weight;
        }

        public void setWeight(int weight) {
            this.weight = weight;
        }

        public String toString() {
            return "DijkstraEdge{" + "destination=" + destination + ", weight=" + weight + '}';
        }
    }

    /**
     * Represents a node in Dijkstra's algorithm. The distance of the nodes from the start is stored in the distance
     * field.
     */
    static class DijkstraNode {
        private Node vertex;
        private int distance;

        public DijkstraNode(Node vertex, int distance) {
            this.vertex = vertex;
            this.distance = distance;
        }

        public Node getVertex() {
            return vertex;
        }

        public void setVertex(Node vertex) {
            this.vertex = vertex;
        }

        public int getDistance() {
            return distance;
        }

        public void setDistance(int distance) {
            this.distance = distance;
        }
    }
}

