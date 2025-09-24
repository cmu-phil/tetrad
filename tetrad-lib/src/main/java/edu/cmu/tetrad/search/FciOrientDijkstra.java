///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Node;

import java.util.*;

/**
 * A simple implementation of Dijkstra's algorithm for finding the shortest path in a graph. We are modifying the
 * algorithm to find paths for rules R5, R9, and R10 in FciOrient. We are also modifying the algorithm to stop when an
 * end node is reached. (The end node may be left unspecified, in which case the algorithm will find the shortest path
 * to all nodes in the graph.)
 * <p>
 * Weights should all be positive. We report distances as total weights along the shortest path from the start node to
 * the y node. We report unreachable nodes as being a distance of Integer.MAX_VALUE. We assume the graph is undirected.
 * An end nodes may be specified, in which case, once the end node is reached, we report all further nodes as being at a
 * distance of Integer.MAX_VALUE.
 *
 * @author josephramsey, chat.
 */
public class FciOrientDijkstra {

    /**
     * Private constructor to prevent instantiation.
     */
    private FciOrientDijkstra() {
    }

    /**
     * Finds shortest distances from a start node to all other nodes in a graph. Unreachable nodes are reported as being
     * at a distance of Integer.MAX_VALUE. The graph is assumed to be undirected.
     *
     * @param graph        The graph to search; should include only the relevant edge in the graph.
     * @param start        The starting node.
     * @param predecessors A map to store the predecessors of each node in the shortest path.
     * @param uncovered    Whether the path should be uncovered.
     * @return A map of nodes to their shortest distances from the start node.
     */
    public static Map<Node, Integer> distances(Graph graph, Node start, boolean uncovered, Map<Node, Node> predecessors) {
        return distances(graph, start, null, predecessors, uncovered);
    }

    /**
     * Finds shortest distances from a x node to all other nodes in a graph. Unreachable nodes are reported as being at
     * a distance of Integer.MAX_VALUE. The graph is assumed to be undirected. An y node may be specified, in which
     * case, once the y node is reached, all further nodes are reported as being at a distance of Integer.MAX_VALUE.
     *
     * @param graph        The graph to search; should include only the relevant edge in the graph.
     * @param x            The starting node.
     * @param y            The ending node. Maybe be null. If not null, the algorithm will stop when this node is
     *                     reached.
     * @param predecessors A map to store the predecessors of each node in the shortest path.
     * @param uncovered    If true, the algorithm will not traverse edges y--z where an adjacency exists between
     *                     predecessor(y)  and z.
     * @return A map of nodes to their shortest distances from the start node.
     */
    public static Map<Node, Integer> distances(Graph graph, Node x, Node y,
                                               Map<Node, Node> predecessors, boolean uncovered) {
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

            for (DijkstraEdge dijkstraEdge : graph.getNeighbors(currentVertex)) {
                Node predecessor = getPredecessor(predecessors, currentVertex);
                Node prepredessor = getPredecessor(predecessors, predecessor);

                // Skip x o-o y itself.
                if (dijkstraEdge.gety() == y && currentVertex == x) {
                    continue;
                }

                if (dijkstraEdge.gety() == x && currentVertex == y) {
                    continue;
                }

                // If uncovered, skip triangles.
                if (uncovered) {
                    if (dijkstraEdge.gety() == y && predecessor == x) {
                        continue;
                    }

                    if (dijkstraEdge.gety() == x && predecessor == y) {
                        continue;
                    }
                }

//                // If uncovered, skip covered triples.
//                if (uncovered) {
//                    if (adjacent(origGraph, dijkstraEdge.gety(), predecessor)) {
//                        continue;
//                    }
//                }

                Node neighbor = dijkstraEdge.gety();
                int newDist = distances.get(currentVertex) + dijkstraEdge.getWeight();

                distances.putIfAbsent(neighbor, Integer.MAX_VALUE);

                if (newDist < distances.get(neighbor)) {
                    distances.put(neighbor, newDist);
                    predecessors.put(neighbor, currentVertex);
                    priorityQueue.add(new DijkstraNode(neighbor, newDist));
                    if (dijkstraEdge.gety().equals(y)) { // y can be null.
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

    /**
     * Checks whether a node is adjacent to another node in the graph.
     *
     * @param graph         The graph to search.
     * @param currentVertex The node whose neighbors are being checked.
     * @param predecessor   The node to check adjacency against.
     * @return true if the nodes are adjacent, otherwise false.
     */
    public static boolean adjacent(Graph graph, Node currentVertex, Node predecessor) {
        List<DijkstraEdge> dijkstraEdges = graph.getNeighbors(currentVertex);

        for (DijkstraEdge dijkstraEdge : dijkstraEdges) {
            if (dijkstraEdge.gety().equals(predecessor)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns the shortest path from the start node to the end node. If no path is found, null is returned.
     *
     * @param predecessors A map of nodes to their predecessors in the shortest path.
     * @param start        The start node.
     * @param end          The end node.
     * @return The shortest path from the start node to the end node.
     */
    public static List<Node> getPath(Map<Node, Node> predecessors, Node start, Node end) {
        List<Node> path = new ArrayList<>();
        for (Node at = end; at != null; at = predecessors.get(at)) {
            path.add(at);
        }
        Collections.reverse(path);
        if (path.getFirst().equals(start)) {
            return path;
        } else {
            return null; // No path found
        }
    }

//    /**
//     * A simple test of the Dijkstra algorithm. This could be moved to a unit test. TODO
//     *
//     * @param args Command line arguments.
//     */
//    public static void main(String[] args) {
//        edu.cmu.tetrad.graph.Graph graph = new edu.cmu.tetrad.graph.EdgeListGraph();
//
//        Map<String, Node> index = new HashMap<>();
//
//        for (int i = 1; i <= 10; i++) {
//            Node node = new GraphNode(i + "");
//            index.put(i + "", node);
//        }
//
//        graph.addNondirectedEdge(index.get("1"), index.get("3"));
//
//
//        graph.addNondirectedEdge(index.get("1"), index.get("2"));
//        graph.addNondirectedEdge(index.get("2"), index.get("3"));
//
//        graph.addNondirectedEdge(index.get("1"), index.get("4"));
//        graph.addNondirectedEdge(index.get("4"), index.get("5"));
//        graph.addNondirectedEdge(index.get("5"), index.get("3"));
//
//        // Let's cover some edges.
////        graph.addEdge(index.get("1"), index.get("3"), 1);
////        graph.addEdge(index.get("2"), index.get("4"), 1);
////        graph.addEdge(index.get("2"), index.get("4"), 1);
//
//        Map<Node, Node> predecessors = new HashMap<>();
//
//        boolean uncovered = true;
//
//        Graph _graph = new Graph(graph, false);
//
//        Map<Node, Integer> distances = FciOrientDijkstra.distances(_graph, graph, index.get("1"), index.get("3"),
//                predecessors, uncovered, false);
//
//        for (Map.Entry<Node, Integer> entry : distances.entrySet()) {
//            System.out.println("Distance from 1 to " + entry.getKey() + " is " + entry.getValue());
//        }
//
//        List<Node> path = getPath(predecessors, index.get("1"), index.get("3"));
//        System.out.println("Shortest path " + path);
//
//    }

    /**
     * Represents a graph for Dijkstra's algorithm.
     */
    public static class Graph {
        private final boolean potentiallyDirected;
        private edu.cmu.tetrad.graph.Graph _graph = null;

        /**
         * Represents a graph used in Dijkstra's algorithm.
         *
         * @param graph               The graph.
         * @param potentiallyDirected If true, the graph is potentially directed.
         */
        public Graph(edu.cmu.tetrad.graph.Graph graph, boolean potentiallyDirected) {
            this._graph = graph;
            this.potentiallyDirected = potentiallyDirected;
        }

        /**
         * Returns the neighbors of a node, reachable via DijkstraEdges in the grph.
         *
         * @param node The node.
         * @return The neighbors of the node.
         */
        public List<DijkstraEdge> getNeighbors(Node node) {
            List<DijkstraEdge> filteredNeighbors = new ArrayList<>();

            if (potentiallyDirected) {
                Set<Edge> edges = _graph.getEdges(node);

                // We need to filter these neighbors to allow only those that pass using TraverseSemidirected.
                for (Edge edge : edges) {
                    Node other = Edges.traverseSemiDirected(node, edge);

                    if (other == null) {
                        continue;
                    }

                    filteredNeighbors.add(new DijkstraEdge(other, 1));
                }

                return filteredNeighbors;
            } else {
                Set<Edge> edges = _graph.getEdges(node);

                // We need to filter these neighbors to allow only those that pass using TraverseSemidirected.
                for (Edge edge : edges) {
                    Node other = Edges.traverseNondirected(node, edge);

                    if (other == null) {
                        continue;
                    }

                    filteredNeighbors.add(new DijkstraEdge(other, 1));
                }

                return filteredNeighbors;
            }
        }

        /**
         * Returns the nodes in the graph.
         *
         * @return The nodes in the graph.
         */
        public Set<Node> getNodes() {
            return new HashSet<>(_graph.getNodes());
        }
    }

    /**
     * Represents a node in Dijkstra's algorithm. The distance of the nodes from the start is stored in the distance
     * field.
     */
    public static class DijkstraEdge {
        private final Node y;
        private int weight;

        /**
         * Creates a new DijkstraEdge.
         *
         * @param y      The node.
         * @param weight The weight of the edge.
         */
        public DijkstraEdge(Node y, int weight) {
            if (y == null) {
                throw new IllegalArgumentException("y cannot be null.");
            }

            if (weight <= 0) {
                throw new IllegalArgumentException("Weight must be positive.");
            }

            this.y = y;
            this.weight = weight;
        }

        /**
         * Returns the node.
         *
         * @return The node.
         */
        public Node gety() {
            return y;
        }

        /**
         * Returns the weight of the edge.
         *
         * @return The weight of the edge.
         */
        public int getWeight() {
            return weight;
        }

        /**
         * Sets the weight of the edge.
         *
         * @param weight The weight of the edge.
         */
        public void setWeight(int weight) {
            this.weight = weight;
        }

        /**
         * Returns a string representation of the DijkstraEdge.
         *
         * @return A string representation of the DijkstraEdge.
         */
        public String toString() {
            return "DijkstraEdge{" + "y=" + y + ", weight=" + weight + '}';
        }
    }

    /**
     * Represents a node in Dijkstra's algorithm. The distance of the nodes from the start is stored in the distance
     * field.
     */
    static class DijkstraNode {
        /**
         * The node.
         */
        private Node vertex;
        /**
         * The distance of the node from the start.
         */
        private int distance;

        /**
         * Creates a new DijkstraNode.
         *
         * @param vertex   The node.
         * @param distance The distance of the node from the start.
         */
        public DijkstraNode(Node vertex, int distance) {
            this.vertex = vertex;
            this.distance = distance;
        }

        /**
         * Returns the node.
         *
         * @return The node.
         */
        public Node getVertex() {
            return vertex;
        }

        /**
         * Sets the node.
         *
         * @param vertex The node.
         */
        public void setVertex(Node vertex) {
            this.vertex = vertex;
        }

        /**
         * Returns the distance of the node from the start.
         *
         * @return The distance of the node from the start.
         */
        public int getDistance() {
            return distance;
        }

        /**
         * Sets the distance of the node from the start.
         *
         * @param distance The distance of the node from the start.
         */
        public void setDistance(int distance) {
            this.distance = distance;
        }
    }
}


