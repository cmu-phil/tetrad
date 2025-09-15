/// ////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
/// ////////////////////////////////////////////////////////////////////////////
package edu.cmu.tetrad.graph;

import edu.cmu.tetrad.data.AndersonDarlingTest;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.data.KnowledgeEdge;
import edu.cmu.tetrad.graph.Edge.Property;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.search.utils.FciOrient;
import edu.cmu.tetrad.search.utils.GraphSearchUtils;
import edu.cmu.tetrad.search.utils.MagToPag;
import edu.cmu.tetrad.util.*;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.TimeUnit;

/**
 * Utility class for working with graphs.
 */
public final class GraphUtils {

    /**
     * Private constructor to prevent instantiation.
     */
    private GraphUtils() {

    }

    /**
     * Returns the associated node for the given error node in the specified graph.
     *
     * @param errorNode The error node for which the associated node needs to be retrieved.
     * @param graph     The graph in which to search for the associated node.
     * @return The associated node of the error node.
     * @throws IllegalArgumentException If the error node is not of type ERROR or if it does not have exactly one
     *                                  child.
     */
    public static Node getAssociatedNode(Node errorNode, Graph graph) {
        if (errorNode.getNodeType() != NodeType.ERROR) {
            throw new IllegalArgumentException("Can only get an associated node " + "for an error node: " + errorNode);
        }

        List<Node> children = graph.getChildren(errorNode);

        if (children.size() != 1) {
//            System.out.println("children of " + errorNode + " = " + children);
//            System.out.println(graph);

            throw new IllegalArgumentException("An error node should have only " + "one child, which is its associated node: " + errorNode);
        }

        return children.getFirst();
    }

    /**
     * Checks if the given set of nodes forms a clique in the specified graph.
     *
     * @param set   the collection of nodes to be checked
     * @param graph the graph in which the nodes are located
     * @return true if the given set forms a clique, false otherwise
     */
    public static boolean isClique(Collection<Node> set, Graph graph) {
        List<Node> setv = new LinkedList<>(set);
        for (int i = 0; i < setv.size() - 1; i++) {
            for (int j = i + 1; j < setv.size(); j++) {
                if (!graph.isAdjacentTo(setv.get(i), setv.get(j))) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Calculates the subgraph over the Markov blanket of a target node in a given DAG, CPDAG, MAG, or PAG. Target Node
     * is not included in the result graph's nodes list. Edges including the target node is included in the result
     * graph's edges list.
     *
     * @param target a node in the given graph.
     * @param graph  a DAG, CPDAG, MAG, or PAG.
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public static Graph markovBlanketSubgraph(Node target, Graph graph) {
        Set<Node> mb = markovBlanket(target, graph);

        Graph mbGraph = new EdgeListGraph();

        for (Node node : mb) {
            mbGraph.addNode(node);
        }

        List<Node> mbList = new ArrayList<>(mb);
        mbList.add(target);

        for (int i = 0; i < mbList.size(); i++) {
            for (int j = i + 1; j < mbList.size(); j++) {
                for (Edge e : graph.getEdges(mbList.get(i), mbList.get(j))) {
                    mbGraph.addEdge(e);
                }
            }
        }

        return mbGraph;
    }

    /**
     * Calculates the subgraph over the Markov blanket of a target node for a DAG, CPDAG, MAG, or PAG. This is not
     * necessarily minimal (i.e. not necessarily a Markov Boundary). Target Node is included in the result graph's nodes
     * list. Edges including the target node is included in the result graph's edges list.
     *
     * @param target a node in the given graph.
     * @param graph  a DAG, CPDAG, MAG, or PAG.
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public static Graph getMarkovBlanketSubgraphWithTargetNode(Graph graph, Node target) {
        EdgeListGraph g = new EdgeListGraph(graph);
        Set<Node> mbNodes = GraphUtils.markovBlanket(target, g);
        mbNodes.add(target);
        return g.subgraph(new ArrayList<>(mbNodes));
    }

    /**
     * Calculates the subgraph over the parents of a target node for a DAG, CPDAG, MAG, or PAG. This is not necessarily
     * minimal (i.e. not necessarily a Markov Boundary). Target Node is included in the result graph's nodes list. Edges
     * including the target node is included in the result graph's edges list.
     *
     * @param target a node in the given graph.
     * @param graph  a DAG, CPDAG, MAG, or PAG.
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public static Graph getParentsSubgraphWithTargetNode(Graph graph, Node target) {
        EdgeListGraph g = new EdgeListGraph(graph);
        List<Node> parents = graph.getParents(target);
        parents.add(target);
        return g.subgraph(parents);
    }

    /**
     * Calculates the subgraph over the adjacency of a target node for a DAG, CPDAG, MAG, or PAG. This is not
     * necessarily minimal (i.e. not necessarily a Markov Boundary). Target Node is included in the result graph's nodes
     * list. Edges including the target node is included in the result graph's edges list.
     *
     * @param target a node in the given graph.
     * @param graph  a DAG, CPDAG, MAG, or PAG.
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public static Graph getAdjacencySubgraphWithTargetNode(Graph graph, Node target) {
        EdgeListGraph g = new EdgeListGraph(graph);
        List<Node> adjs = graph.getAdjacentNodes(target);
        adjs.add(target);
        return g.subgraph(adjs);
    }

    /**
     * <p>removeBidirectedOrientations.</p>
     *
     * @param estCpdag a {@link edu.cmu.tetrad.graph.Graph} object
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public static Graph removeBidirectedOrientations(Graph estCpdag) {
        estCpdag = new EdgeListGraph(estCpdag);

        // Make bidirected edges undirected.
        for (Edge edge : estCpdag.getEdges()) {
            if (Edges.isBidirectedEdge(edge)) {
                estCpdag.removeEdge(edge);
                estCpdag.addUndirectedEdge(edge.getNode1(), edge.getNode2());
            }
        }

        return estCpdag;
    }

    /**
     * <p>undirectedGraph.</p>
     *
     * @param graph a {@link edu.cmu.tetrad.graph.Graph} object
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public static Graph undirectedGraph(Graph graph) {
        Graph graph2 = new EdgeListGraph(graph.getNodes());

        for (Edge edge : graph.getEdges()) {
            if (!graph2.isAdjacentTo(edge.getNode1(), edge.getNode2())) {
                graph2.addUndirectedEdge(edge.getNode1(), edge.getNode2());
            }
        }

        return graph2;
    }

    /**
     * <p>undirectedGraph.</p>
     *
     * @param graph a {@link edu.cmu.tetrad.graph.Graph} object
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public static Graph nondirectedGraph(Graph graph) {
        Graph graph2 = new EdgeListGraph(graph.getNodes());

        for (Edge edge : graph.getEdges()) {
            if (!graph2.isAdjacentTo(edge.getNode1(), edge.getNode2())) {
                graph2.addNondirectedEdge(edge.getNode1(), edge.getNode2());
            }
        }

        return graph2;
    }

    /**
     * <p>completeGraph.</p>
     *
     * @param graph a {@link edu.cmu.tetrad.graph.Graph} object
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public static Graph completeGraph(Graph graph) {
        Graph graph2;

        if (graph instanceof SvarEdgeListGraph) {
            graph2 = new SvarEdgeListGraph(graph.getNodes());
        } else {
            graph2 = new EdgeListGraph(graph.getNodes());
        }

        graph2.removeEdges(new ArrayList<>(graph2.getEdges()));

        List<Node> nodes = graph2.getNodes();

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                Node node1 = nodes.get(i);
                Node node2 = nodes.get(j);
                graph2.addUndirectedEdge(node1, node2);
            }
        }

        return graph2;
    }

    /**
     * Converts a bidirected graph to an undirected graph.
     *
     * @param graph The bidirected graph to be converted.
     * @return The converted undirected graph.
     */
    public static Graph bidirectedToUndirected(Graph graph) {
        Graph newGraph = new EdgeListGraph(graph);

        for (Edge edge : newGraph.getEdges()) {
            if (Edges.isBidirectedEdge(edge)) {
                newGraph.removeEdge(edge);
                newGraph.addUndirectedEdge(edge.getNode1(), edge.getNode2());
            }
        }

        return newGraph;
    }

    /**
     * Converts an undirected graph to a bidirected graph.
     *
     * @param graph the undirected graph to be converted
     * @return a new bidirected graph with the same nodes and bidirected edges as the input graph
     */
    public static Graph undirectedToBidirected(Graph graph) {
        Graph newGraph = new EdgeListGraph(graph);

        for (Edge edge : newGraph.getEdges()) {
            if (Edges.isUndirectedEdge(edge)) {
                newGraph.removeEdge(edge);
                newGraph.addBidirectedEdge(edge.getNode1(), edge.getNode2());
            }
        }

        return newGraph;
    }

    /**
     * Constructs a string representation of a path in a graph.
     *
     * @param graph       the graph in which the path exists
     * @param path        the list of nodes representing the path
     * @param showBlocked determines whether blocked nodes should be included in the string representation
     * @return the string representation of the path
     */
    public static String pathString(Graph graph, List<Node> path, boolean showBlocked) {
        return pathString(graph, path, new HashSet<>(), showBlocked, false);
    }

    /**
     * Generates a string representation of a path in a given graph, starting from the specified nodes.
     *
     * @param graph the graph in which the path is located
     * @param x     the starting nodes of the path
     * @return a string representation of the path
     */
    public static String pathString(Graph graph, Node... x) {
        List<Node> path = new ArrayList<>();
        Collections.addAll(path, x);
        return GraphUtils.pathString(graph, path, new HashSet<>());
    }

    /**
     * Returns a string representation of the given path in the graph, considering the conditioning variables.
     *
     * @param graph            the graph to find the path in
     * @param path             the list of nodes representing the path
     * @param conditioningVars the set of conditioning variables to consider
     * @return a string representation of the path
     */
    public static String pathString(Graph graph, List<Node> path, Set<Node> conditioningVars) {
        return pathString(graph, path, conditioningVars, false, false);
    }

    /**
     * Returns a string representation of the given path in the graph, with additional information about conditioning
     * variables.
     *
     * @param graph              the graph containing the path
     * @param path               the list of nodes representing the path
     * @param conditioningVars   the list of nodes representing the conditioning variables
     * @param showBlocked        whether to show information about blocked paths
     * @param allowSelectionBias whether to allow selection bias. For CPDAGs, this should be false, since undirected
     *                           edges mean directed in one direction or the other. For PAGs, it should be true, since
     *                           undirected edges indicate selection bias.
     * @return a string representation of the path with conditioning information
     */
    public static String pathString(Graph graph, List<Node> path, Set<Node> conditioningVars, boolean showBlocked,
                                    boolean allowSelectionBias) {
        StringBuilder buf = new StringBuilder();

        if (path.size() < 2) {
            return "NO PATH";
        }

        boolean mConnecting = graph.paths().isMConnectingPath(path, conditioningVars, allowSelectionBias);

        if (showBlocked) {
            if (!mConnecting) {
                buf.append("BLOCKED: ");
            } else {
                buf.append("not blocked: ");
            }
        }

        if (path.getFirst().getNodeType() == NodeType.LATENT) {
            buf.append("(").append(path.getFirst().toString()).append(")");
        } else {
            buf.append(path.getFirst().toString());
        }

        String conditioningSymbol = "✔";

        if (conditioningVars.contains(path.getFirst())) {
            buf.append(conditioningSymbol);
        }

        for (int m = 1; m < path.size(); m++) {
            Node n0 = path.get(m - 1);
            Node n1 = path.get(m);
            Node n2 = null;

            if (m < path.size() - 1) {
                n2 = path.get(m + 1);
            }

            Edge edge = graph.getEdge(n0, n1);

            if (edge == null) {
                buf.append("(-)");
            } else if (graph.getEdges(n0, n1).size() == 2) {
                buf.append("<=>");
            } else {
                Endpoint endpoint0 = edge.getProximalEndpoint(n0);
                Endpoint endpoint1 = edge.getProximalEndpoint(n1);

                if (endpoint0 == Endpoint.ARROW) {
                    buf.append("<");
                } else if (endpoint0 == Endpoint.TAIL) {
                    buf.append("-");
                } else if (endpoint0 == Endpoint.CIRCLE) {
                    buf.append("o");
                }

                buf.append("-");

                if (endpoint1 == Endpoint.ARROW) {
                    buf.append(">");
                } else if (endpoint1 == Endpoint.TAIL) {
                    buf.append("-");
                } else if (endpoint1 == Endpoint.CIRCLE) {
                    buf.append("o");
                }
            }

            if (n1.getNodeType() == NodeType.LATENT) {
                buf.append("(").append(n1).append(")");
            } else {
                buf.append(n1);
            }

            if (conditioningVars.contains(n1)) {
                buf.append(conditioningSymbol);
            } else {
                if (n2 != null) {
                    if (graph.isDefCollider(n0, n1, n2)) {
                        Set<Node> descendants = graph.paths().getDescendants(n1);
                        descendants.retainAll(conditioningVars);
                        if (!descendants.isEmpty()) {
                            buf.append("{~~>").append(descendants.iterator().next()).append(conditioningSymbol).append("}");
                        }
                    }
                }
            }
        }
        return buf.toString();
    }

    /**
     * Converts the given graph, <code>originalGraph</code>, to use the new variables (with the same names as the old).
     *
     * @param originalGraph The graph to be converted.
     * @param newVariables  The new variables to use, with the same names as the old ones.
     * @return A new, converted, graph.
     */
    public static Graph replaceNodes(Graph originalGraph, List<Node> newVariables) {
        // Map of name -> replacement node (keep your "no latents" rule)
        Map<String, Node> replacements = new HashMap<>();
        for (Node node : newVariables) {
            if (node.getNodeType() != NodeType.LATENT) {
                replacements.put(node.getName(), node);
            }
        }

        // Build converted graph with ALL original nodes, but replaced by name when possible.
        Graph convertedGraph = new EdgeListGraph();
        // Ensure we reuse the same Node instance for each name in the converted graph
        Map<String, Node> nameToConverted = new HashMap<>();

        for (Node orig : originalGraph.getNodes()) {
            Node rep = replacements.getOrDefault(orig.getName(), orig);
            // Reuse a single instance per name in the new graph
            Node toAdd = nameToConverted.computeIfAbsent(rep.getName(), k -> rep);
            if (!convertedGraph.containsNode(toAdd)) {
                convertedGraph.addNode(toAdd);
            }
        }

        // Recreate edges with mapped endpoints
        for (Edge edge : originalGraph.getEdges()) {
            Node a = nameToConverted.getOrDefault(edge.getNode1().getName(), edge.getNode1());
            Node b = nameToConverted.getOrDefault(edge.getNode2().getName(), edge.getNode2());
            Edge newEdge = new Edge(a, b, edge.getEndpoint1(), edge.getEndpoint2());
            convertedGraph.addEdge(newEdge);
        }

        // Copy triples using mapped nodes (safe lookups)
        for (Triple t : originalGraph.getUnderLines()) {
            Node x = nameToConverted.get(t.getX().getName());
            Node y = nameToConverted.get(t.getY().getName());
            Node z = nameToConverted.get(t.getZ().getName());
            convertedGraph.addUnderlineTriple(x, y, z);
        }

        for (Triple t : originalGraph.getDottedUnderlines()) {
            Node x = nameToConverted.get(t.getX().getName());
            Node y = nameToConverted.get(t.getY().getName());
            Node z = nameToConverted.get(t.getZ().getName());
            convertedGraph.addDottedUnderlineTriple(x, y, z);
        }

        for (Triple t : originalGraph.getAmbiguousTriples()) {
            Node x = nameToConverted.get(t.getX().getName());
            Node y = nameToConverted.get(t.getY().getName());
            Node z = nameToConverted.get(t.getZ().getName());
            convertedGraph.addAmbiguousTriple(x, y, z);
        }

        return convertedGraph;
    }

    /**
     * Removes all latent nodes from the graph and returns the modified graph.
     *
     * @param graph the input graph to be modified
     * @return a new graph with all latent nodes removed
     */
    public static Graph restrictToMeasured(Graph graph) {
        graph = new EdgeListGraph(graph);

        for (Node node : graph.getNodes()) {
            if (node.getNodeType() == NodeType.LATENT) {
                graph.removeNode(node);
            }
        }

        return graph;
    }

    /**
     * Converts the given list of nodes, <code>originalNodes</code>, to use the new variables (with the same names as
     * the old).
     *
     * @param originalNodes The list of nodes to be converted.
     * @param newNodes      A list of new nodes, containing as a subset nodes with the same names as those in
     *                      <code>originalNodes</code>. the old ones.
     * @return The converted list of nodes.
     */
    public static List<Node> replaceNodes(List<Node> originalNodes, List<Node> newNodes) {
        List<Node> convertedNodes = new LinkedList<>();

        for (Node node : originalNodes) {
            if (node == null) {
                throw new NullPointerException("Null node among original nodes.");
            }

            for (Node _node : newNodes) {
                if (_node == null) {
                    throw new NullPointerException("Null node among new nodes.");
                }

                if (node.getName().equals(_node.getName())) {
                    convertedNodes.add(_node);
                    break;
                }
            }
        }

        return convertedNodes;
    }

    /**
     * Counts the adjacencies that are in graph1 but not in graph2.
     *
     * @param graph1 a {@link edu.cmu.tetrad.graph.Graph} object
     * @param graph2 a {@link edu.cmu.tetrad.graph.Graph} object
     * @return a int
     * @throws java.lang.IllegalArgumentException if graph1 and graph2 are not namewise isomorphic.
     */
    public static int countAdjErrors(Graph graph1, Graph graph2) {
        if (graph1 == null) {
            throw new NullPointerException("The reference graph is missing.");
        }

        if (graph2 == null) {
            throw new NullPointerException("The target graph is missing.");
        }

        graph2 = GraphUtils.replaceNodes(graph2, graph1.getNodes());

        int count = 0;

        Set<Edge> edges1 = graph1.getEdges();

        for (Edge edge : edges1) {
            if (!graph2.isAdjacentTo(edge.getNode1(), edge.getNode2())) {
                ++count;
            }
        }

        return count;
    }

    /**
     * Converts the given list of nodes, <code>originalNodes</code>, to use the replacement nodes for them by the same
     * name in the given
     * <code>graph</code>.
     *
     * @param originalNodes The list of nodes to be converted.
     * @param graph         A graph to be used as a source of new nodes.
     * @return A new, converted, graph.
     */
    public static List<Node> replaceNodes(List<Node> originalNodes, Graph graph) {
        List<Node> convertedNodes = new LinkedList<>();

        for (Node node : originalNodes) {
            convertedNodes.add(graph.getNode(node.getName()));
        }

        return convertedNodes;
    }

    /**
     * Creates an empty graph with the specified number of nodes.
     *
     * @param numNodes the number of nodes to create in the graph
     * @return a new empty graph with the specified number of nodes
     */
    public static Graph emptyGraph(int numNodes) {
        List<Node> nodes = new ArrayList<>();

        for (int i = 0; i < numNodes; i++) {
            nodes.add(new GraphNode("X" + i));
        }

        return new EdgeListGraph(nodes);
    }

    /**
     * Retrieves the list of ambiguous triples from the given graph for a given node. These are triple (X, Y, Z) for
     * which Sepset(X, Z) contains Y for some sepsets but not others.
     *
     * @param node  the node for which to find ambiguous triples
     * @param graph the graph from which to retrieve the ambiguous triples
     * @return the list of ambiguous triples found in the graph for the given node
     */
    public static List<Triple> getAmbiguousTriplesFromGraph(Node node, Graph graph) {
        List<Triple> ambiguousTriples = new ArrayList<>();

        List<Node> adj = new ArrayList<>(graph.getAdjacentNodes(node));
        if (adj.size() < 2) {
            return new LinkedList<>();
        }

        ChoiceGenerator gen = new ChoiceGenerator(adj.size(), 2);
        int[] choice;

        while ((choice = gen.next()) != null) {
            Node x = adj.get(choice[0]);
            Node z = adj.get(choice[1]);

            if (graph.isAmbiguousTriple(x, node, z)) {
                ambiguousTriples.add(new Triple(x, node, z));
            }
        }

        return ambiguousTriples;
    }

    /**
     * Retrieves the underlined triples from the given graph that involve the specified node. These are triples that
     * represent definite noncolliders in the given graph.
     *
     * @param node  the node for which to retrieve the underlined triples
     * @param graph the graph from which to retrieve the underlined triples
     * @return a list of underlined triples involving the node
     */
    public static List<Triple> getUnderlinedTriplesFromGraph(Node node, Graph graph) {
        List<Triple> underlinedTriples = new ArrayList<>();
        Set<Triple> allUnderlinedTriples = graph.getUnderLines();

        List<Node> adj = new ArrayList<>(graph.getAdjacentNodes(node));
        if (adj.size() < 2) {
            return new LinkedList<>();
        }

        ChoiceGenerator gen = new ChoiceGenerator(adj.size(), 2);
        int[] choice;

        while ((choice = gen.next()) != null) {
            Node x = adj.get(choice[0]);
            Node z = adj.get(choice[1]);

            if (allUnderlinedTriples.contains(new Triple(x, node, z))) {
                underlinedTriples.add(new Triple(x, node, z));
            }
        }

        return underlinedTriples;
    }

    /**
     * Retrieves the list of dotted and underlined triples from the given graph, with the specified node as the middle
     * node.
     *
     * @param node  The middle node to use for finding the triples.
     * @param graph The graph to search for the triples.
     * @return The list of dotted and underlined triples containing the specified node as the middle node.
     */
    public static List<Triple> getDottedUnderlinedTriplesFromGraph(Node node, Graph graph) {
        List<Triple> dottedUnderlinedTriples = new ArrayList<>();
        Set<Triple> allDottedUnderlinedTriples = graph.getDottedUnderlines();

        List<Node> adj = new ArrayList<>(graph.getAdjacentNodes(node));
        if (adj.size() < 2) {
            return new LinkedList<>();
        }

        ChoiceGenerator gen = new ChoiceGenerator(adj.size(), 2);
        int[] choice;

        while ((choice = gen.next()) != null) {
            Node x = adj.get(choice[0]);
            Node z = adj.get(choice[1]);

            if (allDottedUnderlinedTriples.contains(new Triple(x, node, z))) {
                dottedUnderlinedTriples.add(new Triple(x, node, z));
            }
        }

        return dottedUnderlinedTriples;
    }

    /**
     * Checks if a given graph contains a bidirected edge.
     *
     * @param graph the graph to check for bidirected edges
     * @return true if the graph contains a bidirected edge, false otherwise
     */
    public static boolean containsBidirectedEdge(Graph graph) {
        boolean containsBidirected = false;

        for (Edge edge : graph.getEdges()) {
            if (Edges.isBidirectedEdge(edge)) {
                containsBidirected = true;
                break;
            }
        }
        return containsBidirected;
    }

    /**
     * Generates a list of triples where a node acts as a collider in a given graph.
     *
     * @param graph the graph to search for collider triples in
     * @return a LinkedList of Triples where a node acts as a collider
     */
    public static LinkedList<Triple> listColliderTriples(Graph graph) {
        LinkedList<Triple> colliders = new LinkedList<>();

        for (Node node : graph.getNodes()) {
            List<Node> adj = new ArrayList<>(graph.getAdjacentNodes(node));

            if (adj.size() < 2) {
                continue;
            }

            ChoiceGenerator gen = new ChoiceGenerator(adj.size(), 2);
            int[] choice;

            while ((choice = gen.next()) != null) {
                List<Node> others = GraphUtils.asList(choice, adj);

                if (graph.isDefCollider(others.get(0), node, others.get(1))) {
                    colliders.add(new Triple(others.get(0), node, others.get(1)));
                }
            }
        }
        return colliders;
    }

    /**
     * Constructs a list of nodes from the given <code>nodes</code> list at the given indices in that list.
     *
     * @param indices The indices of the desired nodes in <code>nodes</code>.
     * @param nodes   The list of nodes from which we select a sublist.
     * @return The sublist selected.
     */
    public static List<Node> asList(int[] indices, List<Node> nodes) {
        List<Node> list = new LinkedList<>();

        for (int index : indices) {
            list.add(nodes.get(index));
        }

        return list;
    }

    /**
     * Converts an array of indices into a set of corresponding nodes from a given list of nodes.
     *
     * @param indices an array of indices representing the positions of nodes in the list
     * @param nodes   the list of nodes
     * @return a Set containing the nodes at the specified indices
     */
    public static Set<Node> asSet(int[] indices, List<Node> nodes) {
        Set<Node> set = new HashSet<>();

        for (int i : indices) {
            if (i >= 0 && i < nodes.size()) {
                set.add(nodes.get(i));
            }
        }

        return set;
    }

    /**
     * Converts the given array of nodes into a Set of nodes.
     *
     * @param nodes the array of nodes.
     * @return a Set containing the nodes from the input array.
     */
    public static Set<Node> asSet(Node... nodes) {
        Set<Node> set = new HashSet<>();
        Collections.addAll(set, nodes);
        return set;
    }

    /**
     * Calculates the maximum degree of a graph.
     *
     * @param graph The graph to calculate the degree.
     * @return The maximum degree of the graph. Returns 0 if the graph is empty.
     */
    public static int degree(Graph graph) {
        int maxDegree = 0;

        for (Node node : graph.getNodes()) {
            int n = graph.getEdges(node).size();
            if (n > maxDegree) {
                maxDegree = n;
            }
        }

        return maxDegree;
    }

    /**
     * Generates a comparison string for the intersection of multiple graphs.
     *
     * @param graphs the list of graphs to compare
     * @return a string representation of the intersection of the given graphs
     */
    public static String getIntersectionComparisonString(List<Graph> graphs) {
        if (graphs == null || graphs.isEmpty()) {
            return "";
        }

        StringBuilder b = GraphUtils.undirectedEdges(graphs);

        b.append(GraphUtils.directedEdges(graphs));

        return b.toString();
    }

    /**
     * Returns a StringBuilder object containing information about the undirected edges in the given list of graphs.
     *
     * @param graphs A list of Graph objects representing the graphs to process.
     * @return A StringBuilder object containing information about the undirected edges in the given list of graphs.
     * @throws IllegalArgumentException if an edge is not found in any of the graphs.
     */
    private static StringBuilder undirectedEdges(List<Graph> graphs) {
        List<Graph> undirectedGraphs = new ArrayList<>();

        for (Graph graph : graphs) {
            Graph graph2 = new EdgeListGraph(graph);
            graph2.reorientAllWith(Endpoint.TAIL);
            undirectedGraphs.add(graph2);
        }

        Map<String, Node> exemplars = new HashMap<>();

        for (Graph graph : undirectedGraphs) {
            for (Node node : graph.getNodes()) {
                exemplars.put(node.getName(), node);
            }
        }

        Set<Node> nodeSet = new HashSet<>();

        for (String s : exemplars.keySet()) {
            nodeSet.add(exemplars.get(s));
        }
        List<Node> nodes = new ArrayList<>(nodeSet);
        List<Graph> undirectedGraphs2 = new ArrayList<>();

        for (int i = 0; i < graphs.size(); i++) {
            Graph graph = GraphUtils.replaceNodes(undirectedGraphs.get(i), nodes);
            undirectedGraphs2.add(graph);
        }

        Set<Edge> undirectedEdgesSet = new HashSet<>();

        for (Graph graph : undirectedGraphs2) {
            undirectedEdgesSet.addAll(graph.getEdges());
        }

        List<Edge> undirectedEdges = new ArrayList<>(undirectedEdgesSet);

        undirectedEdges.sort((o1, o2) -> {
            String name11 = o1.getNode1().getName();
            String name12 = o1.getNode2().getName();
            String name21 = o2.getNode1().getName();
            String name22 = o2.getNode2().getName();

            int major = name11.compareTo(name21);
            int minor = name12.compareTo(name22);

            if (major == 0) {
                return minor;
            } else {
                return major;
            }
        });

        List<List<Edge>> groups = new ArrayList<>();
        for (int i = 0; i < graphs.size(); i++) {
            groups.add(new ArrayList<>());
        }

        for (Edge edge : undirectedEdges) {
            int count = 0;

            for (Graph graph : undirectedGraphs2) {
                if (graph.containsEdge(edge)) {
                    count++;
                }
            }

            if (count == 0) {
                throw new IllegalArgumentException();
            }

            groups.get(count - 1).add(edge);
        }

        StringBuilder b = new StringBuilder();

        for (int i = groups.size() - 1; i >= 0; i--) {
            b.append("\n\nIn ").append(i + 1).append(" graph").append((i > 0) ? "s" : "").append("...\n");

            for (int j = 0; j < groups.get(i).size(); j++) {
                b.append("\n").append(j + 1).append(". ").append(groups.get(i).get(j));
            }
        }

        return b;
    }

    /**
     * Returns a StringBuilder object containing the directed edges of the given list of directed graphs.
     *
     * @param directedGraphs a list of directed graphs
     * @return a StringBuilder object that contains the directed edges of the graphs
     */
    private static StringBuilder directedEdges(List<Graph> directedGraphs) {
        Set<Edge> directedEdgesSet = new HashSet<>();

        Map<String, Node> exemplars = new HashMap<>();

        for (Graph graph : directedGraphs) {
            for (Node node : graph.getNodes()) {
                exemplars.put(node.getName(), node);
            }
        }

        Set<Node> nodeSet = new HashSet<>();

        for (String s : exemplars.keySet()) {
            nodeSet.add(exemplars.get(s));
        }

        List<Node> nodes = new ArrayList<>(nodeSet);

        List<Graph> directedGraphs2 = new ArrayList<>();

        for (Graph directedGraph : directedGraphs) {
            Graph graph = GraphUtils.replaceNodes(directedGraph, nodes);
            directedGraphs2.add(graph);
        }

        for (Graph graph : directedGraphs2) {
            directedEdgesSet.addAll(graph.getEdges());
        }

        List<Edge> directedEdges = new ArrayList<>(directedEdgesSet);

        directedEdges.sort((o1, o2) -> {
            String name11 = o1.getNode1().getName();
            String name12 = o1.getNode2().getName();
            String name21 = o2.getNode1().getName();
            String name22 = o2.getNode2().getName();

            int major = name11.compareTo(name21);
            int minor = name12.compareTo(name22);

            if (major == 0) {
                return minor;
            } else {
                return major;
            }
        });

        List<List<Edge>> groups = new ArrayList<>();
        for (int i = 0; i < directedGraphs2.size(); i++) {
            groups.add(new ArrayList<>());
        }
        Set<Edge> contradicted = new HashSet<>();
        Map<Edge, Integer> directionCounts = new HashMap<>();

        for (Edge edge : directedEdges) {
            if (!edge.isDirected()) {
                continue;
            }

            int count1 = 0;
            int count2 = 0;

            for (Graph graph : directedGraphs2) {
                if (graph.containsEdge(edge)) {
                    count1++;
                } else if (graph.containsEdge(edge.reverse())) {
                    count2++;
                }
            }

            if (count1 != 0 && count2 != 0 && !contradicted.contains(edge.reverse())) {
                contradicted.add(edge);
            }

            directionCounts.put(edge, count1);
            directionCounts.put(edge.reverse(), count2);

            if (count1 == 0) {
                groups.get(count2 - 1).add(edge);
            }

            if (count2 == 0) {
                groups.get(count1 - 1).add(edge);
            }
        }

        StringBuilder b = new StringBuilder();

        for (int i = groups.size() - 1; i >= 0; i--) {
            b.append("\n\nUncontradicted in ").append(i + 1).append(" graph").append((i > 0) ? "s" : "").append("...\n");

            for (int j = 0; j < groups.get(i).size(); j++) {
                b.append("\n").append(j + 1).append(". ").append(groups.get(i).get(j));
            }
        }

        b.append("\n\nContradicted:\n");
        int index = 1;

        for (Edge edge : contradicted) {
            b.append("\n").append(index++).append(". ").append(Edges.undirectedEdge(edge.getNode1(), edge.getNode2())).append(" (--> ").append(directionCounts.get(edge)).append(" <-- ").append(directionCounts.get(edge.reverse())).append(")");
        }

        return b;
    }

    /**
     * Generates a textual representation of the edge misclassifications based on the provided counts and number
     * format.
     *
     * @param counts The 2D array representing the counts of edge misclassifications.
     * @param nf     The number format used to format the counts.
     * @return A string containing the textual representation of the edge misclassifications.
     */
    public static String edgeMisclassifications(double[][] counts, NumberFormat nf) {
        StringBuilder builder = new StringBuilder();

        TextTable table2 = new TextTable(9, 7);

        table2.setToken(1, 0, "---");
        table2.setToken(2, 0, "o-o");
        table2.setToken(3, 0, "o->");
        table2.setToken(4, 0, "<-o");
        table2.setToken(5, 0, "-->");
        table2.setToken(6, 0, "<--");
        table2.setToken(7, 0, "<->");
        table2.setToken(8, 0, "No Edge");
        table2.setToken(0, 1, "---");
        table2.setToken(0, 2, "o-o");
        table2.setToken(0, 3, "o->");
        table2.setToken(0, 4, "-->");
        table2.setToken(0, 5, "<->");
        table2.setToken(0, 6, "No Edge");

        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 6; j++) {
                if (i == 7 && j == 5) {
                    table2.setToken(7 + 1, 5 + 1, "*");
                } else {
                    table2.setToken(i + 1, j + 1, nf.format(counts[i][j]));
                }
            }
        }

        builder.append(table2);

        double correctEdges = 0;
        double estimatedEdges = 0;

        for (int i = 0; i < counts.length; i++) {
            for (int j = 0; j < counts[0].length - 1; j++) {
                if ((i == 0 && j == 0) || (i == 1 && j == 1) || (i == 2 && j == 2) || (i == 4 && j == 3) || (i == 6 && j == 4)) {
                    correctEdges += counts[i][j];
                }

                estimatedEdges += counts[i][j];
            }
        }

        NumberFormat nf2 = new DecimalFormat("0.00");

        builder.append("\nRatio correct edges to estimated edges = ").append(nf2.format((correctEdges / estimatedEdges)));

        return builder.toString();
    }

    /**
     * Calculates the misclassifications of edges based on the given counts.
     *
     * @param counts a 2D array containing the counts for different edge classifications
     * @return a string representing the misclassifications of edges
     */
    public static String edgeMisclassifications(int[][] counts) {
        StringBuilder builder = new StringBuilder();

        TextTable table2 = new TextTable(9, 7);

        table2.setToken(1, 0, "---");
        table2.setToken(2, 0, "o-o");
        table2.setToken(3, 0, "o->");
        table2.setToken(4, 0, "&lt;-o");
        table2.setToken(5, 0, "-->");
        table2.setToken(6, 0, "<--");
        table2.setToken(7, 0, "<->");
        table2.setToken(8, 0, "No Edge");
        table2.setToken(0, 1, "---");
        table2.setToken(0, 2, "o-o");
        table2.setToken(0, 3, "o->");
        table2.setToken(0, 4, "-->");
        table2.setToken(0, 5, "<->");
        table2.setToken(0, 6, "No Edge");

        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 6; j++) {
                if (i == 7 && j == 5) {
                    table2.setToken(7 + 1, 5 + 1, "*");
                } else {
                    table2.setToken(i + 1, j + 1, "" + counts[i][j]);
                }
            }
        }

        builder.append(table2);

        int correctEdges = 0;
        int estimatedEdges = 0;

        for (int i = 0; i < counts.length; i++) {
            for (int j = 0; j < counts[0].length - 1; j++) {
                if ((i == 0 && j == 0) || (i == 1 && j == 1) || (i == 2 && j == 2) || (i == 4 && j == 3) || (i == 6 && j == 4)) {
                    correctEdges += counts[i][j];
                }

                estimatedEdges += counts[i][j];
            }
        }

        NumberFormat nf2 = new DecimalFormat("0.00");

        builder.append("\nRatio correct edges to estimated edges = ").append(nf2.format((correctEdges / (double) estimatedEdges)));

        return builder.toString();
    }

    /**
     * Adds markups for edge specializations for the edges in the given graph. This used to be called PAG coloring.
     *
     * @param graph The graph to which PAG edge specialization markups will be added.
     */
    public static void addEdgeSpecializationMarkup(Graph graph) {
        for (Edge edge : graph.getEdges()) {
            edge.getProperties().clear();

            if (!Edges.isDirectedEdge(edge)) {
                continue;
            }

            Node x = Edges.getDirectedEdgeTail(edge);
            Node y = Edges.getDirectedEdgeHead(edge);

            graph.removeEdge(edge);
            graph.addEdge(edge);

            Edge xyEdge = graph.getEdge(x, y);
            graph.removeEdge(xyEdge);

            boolean existsSemidirectedPath;

            if (graph instanceof EdgeListGraph) {
                existsSemidirectedPath = graph.existsSemidirectedPath(x, y);
            } else {
                existsSemidirectedPath = new Paths(graph).existsSemiDirectedPath(x, y);
            }

            if (existsSemidirectedPath) {
                edge.addProperty(Property.dd); // green.
            } else {
                edge.addProperty(Property.pd); // blue
            }

            graph.addEdge(xyEdge);

            Paths paths = graph.paths();
            if (paths.defVisiblePag(edge.getNode1(), edge.getNode2())) {
                edge.addProperty(Property.nl); // solid.
            } else {
                edge.addProperty(Property.pl); // dashed
            }
        }
    }

    /**
     * Computes the misclassification counts for each edge in the given graphs.
     *
     * @param leftGraph The left graph.
     * @param topGraph  The top graph.
     * @param print     Whether to print debug information.
     * @return A 2-dimensional array containing the counts for each misclassification type. The array has dimensions
     * [m][n], where m is the number of misclassification types in the left graph and n is the number of
     * misclassification types in the top graph.
     */
    public static int[][] edgeMisclassificationCounts(Graph leftGraph, Graph topGraph, boolean print) {
        class CountTask extends RecursiveTask<Counts> {

            private final List<Edge> edges;
            private final Graph leftGraph;
            private final Graph topGraph;
            private final Counts counts;
            private final int[] count;
            private final int chunk;
            private final int from;
            private final int to;

            public CountTask(int chunk, int from, int to, List<Edge> edges, Graph leftGraph, Graph topGraph, int[] count) {
                this.chunk = chunk;
                this.from = from;
                this.to = to;
                this.edges = edges;
                this.leftGraph = leftGraph;
                this.topGraph = topGraph;
                this.counts = new Counts();
                this.count = count;
            }

            @Override
            protected Counts compute() {
                int range = this.to - this.from;

                if (range <= this.chunk) {
                    for (int i = this.from; i < this.to; i++) {
                        ++this.count[0];

                        Edge edge = this.edges.get(i);

                        Node x = edge.getNode1();
                        Node y = edge.getNode2();

                        Edge left = this.leftGraph.getEdge(x, y);
                        Edge top = this.topGraph.getEdge(x, y);

                        int m = GraphUtils.getTypeLeft(left, top);
                        int n = GraphUtils.getTypeTop(top);

                        this.counts.increment(m, n);
                    }

                    return this.counts;
                } else {
                    int mid = (this.to + this.from) / 2;
                    CountTask left = new CountTask(this.chunk, this.from, mid, this.edges, this.leftGraph, this.topGraph, this.count);
                    CountTask right = new CountTask(this.chunk, mid, this.to, this.edges, this.leftGraph, this.topGraph, this.count);

                    left.fork();
                    Counts rightAnswer = right.compute();
                    Counts leftAnswer = left.join();

                    leftAnswer.addAll(rightAnswer);
                    return leftAnswer;
                }
            }

        }

        Set<Edge> edgeSet = new HashSet<>();
        edgeSet.addAll(topGraph.getEdges());
        edgeSet.addAll(leftGraph.getEdges());

        if (print) {
            System.out.println("Top graph " + topGraph.getEdges().size());
            System.out.println("Left graph " + leftGraph.getEdges().size());
            System.out.println("All edges " + edgeSet.size());
        }

        List<Edge> edges = new ArrayList<>(edgeSet);

        int parallelism = Runtime.getRuntime().availableProcessors();
        ForkJoinPool pool = new ForkJoinPool(parallelism);

        CountTask task = new CountTask(500, 0, edges.size(), edges, leftGraph, topGraph, new int[1]);

        try {
            Counts counts = pool.invoke(task);

            if (!pool.awaitQuiescence(1, TimeUnit.DAYS)) {
                throw new IllegalStateException("Pool timed out");
            }

            return counts.countArray();
        } catch (Exception e) {
            Thread.currentThread().interrupt();
            throw e;
        }
    }

    /**
     * Returns the type of an edge based on its properties.
     *
     * @param edgeTop the edge to determine the type of
     * @return the type of the edge: - 0 if the edge is undirected - 1 if the edge is nondirected - 2 if the edge is
     * partially oriented - 3 if the edge is directed - 4 if the edge is bidirected - 5 if the edge is null or of an
     * unknown type
     */
    private static int getTypeTop(Edge edgeTop) {
        if (edgeTop == null) {
            return 5;
        }

        if (Edges.isUndirectedEdge(edgeTop)) {
            return 0;
        }

        if (Edges.isNondirectedEdge(edgeTop)) {
            return 1;
        }

        if (Edges.isPartiallyOrientedEdge(edgeTop)) {
            return 2;
        }

        if (Edges.isDirectedEdge(edgeTop)) {
            return 3;
        }

        if (Edges.isBidirectedEdge(edgeTop)) {
            return 4;
        }

        if (edgeTop.getEndpoint1() == Endpoint.TAIL && edgeTop.getEndpoint2() == Endpoint.CIRCLE) {
            return 6;
        }

        if (edgeTop.getEndpoint1() == Endpoint.CIRCLE && edgeTop.getEndpoint2() == Endpoint.TAIL) {
            return 7;
        }

        return 5;
    }

    /**
     * Determines the type of the left edge based on the provided edges.
     *
     * @param edgeLeft the left edge (may be null)
     * @param edgeTop  the top edge (may be null)
     * @return the type of the left edge as an integer
     * @throws IllegalArgumentException if the edge type is unsupported
     */
    private static int getTypeLeft(Edge edgeLeft, Edge edgeTop) {
        if (edgeLeft == null) {
            return 7;
        }

        if (edgeTop == null) {
            edgeTop = edgeLeft;
        }

        if (Edges.isUndirectedEdge(edgeLeft)) {
            return 0;
        }

        if (Edges.isNondirectedEdge(edgeLeft)) {
            return 1;
        }

        Node x = edgeLeft.getNode1();
        Node y = edgeLeft.getNode2();

        if (Edges.isPartiallyOrientedEdge(edgeLeft)) {
            if ((edgeLeft.pointsTowards(x) && edgeTop.pointsTowards(y)) || (edgeLeft.pointsTowards(y) && edgeTop.pointsTowards(x))) {
                return 3;
            } else {
                return 2;
            }
        }

        if (Edges.isDirectedEdge(edgeLeft)) {
            if ((edgeLeft.pointsTowards(x) && edgeTop.pointsTowards(y)) || (edgeLeft.pointsTowards(y) && edgeTop.pointsTowards(x))) {
                return 5;
            } else {
                return 4;
            }
        }

        if (Edges.isBidirectedEdge(edgeLeft)) {
            return 6;
        }

        if (edgeLeft.getEndpoint1() == Endpoint.TAIL && edgeLeft.getEndpoint2() == Endpoint.CIRCLE) {
            return 7;
        }

        if (edgeLeft.getEndpoint1() == Endpoint.CIRCLE && edgeLeft.getEndpoint2() == Endpoint.TAIL) {
            return 8;
        }

        throw new IllegalArgumentException("Unsupported edge type : " + edgeLeft);
    }

    /**
     * Finds all maximal cliques in a given graph.
     *
     * @param graph The graph in which to find the maximal cliques
     * @param nodes The list of nodes in the graph
     * @return The set of all maximal cliques in the graph
     */
    public static Set<Set<Node>> maximalCliques(Graph graph, List<Node> nodes) {
        Set<Set<Node>> report = new HashSet<>();
        GraphUtils.brokKerbosh1(new HashSet<>(), new HashSet<>(nodes), new HashSet<>(), report, graph);
        return report;
    }

    /**
     * Find all cliques (complete subgraphs) in a graph using the Brokk-Kerbosch algorithm.
     *
     * @param R      The current clique being constructed
     * @param P      The candidates to add to the clique
     * @param X      The excluded vertices
     * @param report The set of cliques found
     * @param graph  The graph to search in
     */
    private static void brokKerbosh1(Set<Node> R, Set<Node> P, Set<Node> X, Set<Set<Node>> report, Graph graph) {
        if (P.isEmpty() && X.isEmpty()) {
            report.add(new HashSet<>(R));
        }

        for (Node v : new HashSet<>(P)) {
            Set<Node> _R = new HashSet<>(R);
            Set<Node> _P = new HashSet<>(P);
            Set<Node> _X = new HashSet<>(X);
            _R.add(v);
            _P.retainAll(graph.getAdjacentNodes(v));
            _X.retainAll(graph.getAdjacentNodes(v));
            GraphUtils.brokKerbosh1(_R, _P, _X, report, graph);
            P.remove(v);
            X.add(v);
        }
    }

    /**
     * Converts the attributes of nodes in a graph to text format.
     *
     * @param graph     the graph containing nodes
     * @param title     the title to be displayed before the attributes, can be null or empty
     * @param delimiter the delimiter character used for separating node attributes
     * @return a string representation of the graph node attributes, or null if there are no attributes
     */
    public static String graphNodeAttributesToText(Graph graph, String title, char delimiter) {
        List<Node> nodes = graph.getNodes();

        Map<String, Map<String, Object>> graphNodeAttributes = new LinkedHashMap<>();
        for (Node node : nodes) {
            Map<String, Object> attributes = node.getAllAttributes();

            if (!attributes.isEmpty()) {
                for (String key : attributes.keySet()) {
                    Object value = attributes.get(key);

                    Map<String, Object> nodeAttributes = graphNodeAttributes.get(key);
                    if (nodeAttributes == null) {
                        nodeAttributes = new LinkedHashMap<>();
                    }
                    nodeAttributes.put(node.getName(), value);

                    graphNodeAttributes.put(key, nodeAttributes);
                }
            }
        }

        if (!graphNodeAttributes.isEmpty()) {
            StringBuilder sb = (title == null || title.isEmpty()) ? new StringBuilder() : new StringBuilder(String.format("%s", title));

            for (String key : graphNodeAttributes.keySet()) {
                Map<String, Object> nodeAttributes = graphNodeAttributes.get(key);
                int size = nodeAttributes.size();
                int count = 0;

                sb.append(String.format("%n%s: [", key));

                for (String nodeName : nodeAttributes.keySet()) {
                    Object value = nodeAttributes.get(nodeName);

                    sb.append(String.format("%s: %s", nodeName, value));

                    count++;

                    if (count < size) {
                        sb.append(delimiter);
                    }

                }

                sb.append("]");
            }

            return sb.toString();
        }

        return null;
    }

    /**
     * Converts the attributes of a given graph into a text format.
     *
     * @param graph the graph whose attributes are to be converted
     * @param title the title to be included at the beginning of the converted text
     * @return the converted attributes in text format, or null if the graph has no attributes
     */
    public static String graphAttributesToText(Graph graph, String title) {
        Map<String, Object> attributes = graph.getAllAttributes();
        if (!attributes.isEmpty()) {
            StringBuilder sb = (title == null || title.isEmpty()) ? new StringBuilder() : new StringBuilder(String.format("%s%n", title));

            for (String key : attributes.keySet()) {
                Object value = attributes.get(key);

                sb.append(key);
                sb.append(": ");
                if (value instanceof String) {
                    sb.append(value);
                } else if (value instanceof Number) {
                    sb.append(String.format("%f%n", ((Number) value).doubleValue()));
                }
            }

            return sb.toString();
        }

        return null;
    }

    /**
     * Converts the nodes of a graph to a formatted text representation.
     *
     * @param graph     the graph containing the nodes
     * @param title     the title to be displayed at the beginning of the text (optional, can be null)
     * @param delimiter the character used to separate the nodes in the text
     * @return a string representing the nodes of the graph
     */
    public static String graphNodesToText(Graph graph, String title, char delimiter) {
        StringBuilder sb = (title == null || title.isEmpty()) ? new StringBuilder() : new StringBuilder(String.format("%s%n", title));

        List<Node> nodes = graph.getNodes();
        int size = nodes.size();
        int count = 0;
        for (Node node : nodes) {
            count++;

            if (node.getNodeType() == NodeType.LATENT) {
                sb.append("(").append(node.getName()).append(")");
            } else if (node.getNodeType() == NodeType.SELECTION) {
                sb.append("[").append(node.getName()).append("]");
            } else {
                sb.append(node.getName());
            }

            if (count < size) {
                sb.append(delimiter);
            }
        }

        return sb.toString();
    }

    /**
     * Converts the edges of a graph to text representation.
     *
     * @param graph The graph whose edges will be converted.
     * @param title The title to be included in the text representation. Can be null or empty.
     * @return The text representation of the graph edges.
     */
    public static String graphEdgesToText(Graph graph, String title) {
        Formatter fmt = new Formatter();

        if (title != null && !title.isEmpty()) {
            fmt.format("%s%n", title);
        }

        List<Edge> edges = new ArrayList<>(graph.getEdges());

        Edges.sortEdges(edges);

        int count = 0;

        for (Edge edge : edges) {
            count++;

            // We will print edge's properties in the edge (via toString() function) level.
            //List<Edge.Property> properties = edge.getProperties();
            final String f = "%d. %s";
            Object[] o = new Object[2 /*+ properties.size()*/];/*+ properties.size()*/// <- here we include its properties (nl dd pl pd)
            o[0] = count;
            o[1] = edge; // <- here we include its properties (nl dd pl pd)
            fmt.format(f, o);
            fmt.format("\n");
        }

        return fmt.toString();
    }

    /**
     * Converts a set of triples into a formatted string.
     *
     * @param triples the set of triples to convert
     * @param title   the optional title to include in the string
     * @return the formatted string representation of the triples
     */
    public static String triplesToText(Set<Triple> triples, String title) {
        Formatter fmt = new Formatter();

        if (title != null && !title.isEmpty()) {
            fmt.format("%s%n", title);
        }

        int size = (triples == null) ? 0 : triples.size();
        if (size > 0) {
            int count = 0;
            for (Triple triple : triples) {
                count++;
                if (count < size) {
                    fmt.format("%s%n", triple);
                } else {
                    fmt.format("%s", triple);
                }
            }
        }

        return fmt.toString();
    }

    /**
     * Returns the TwoCycleErrors object that represents errors for direct feedback loops.
     *
     * @param trueGraph The true Graph object.
     * @param estGraph  The estimated Graph object.
     * @return The TwoCycleErrors object that represents the adjacency errors.
     */
    public static TwoCycleErrors getTwoCycleErrors(Graph trueGraph, Graph estGraph) {
        Set<Edge> trueEdges = trueGraph.getEdges();
        Set<Edge> trueTwoCycle = new HashSet<>();

        for (Edge edge : trueEdges) {
            if (!edge.isDirected()) {
                continue;
            }

            Node node1 = edge.getNode1();
            Node node2 = edge.getNode2();

            if (trueEdges.contains(Edges.directedEdge(node2, node1))) {
                Edge undirEdge = Edges.undirectedEdge(node1, node2);
                trueTwoCycle.add(undirEdge);
            }
        }

        Set<Edge> estEdges = estGraph.getEdges();
        Set<Edge> estTwoCycle = new HashSet<>();

        for (Edge edge : estEdges) {
            if (!edge.isDirected()) {
                continue;
            }

            Node node1 = edge.getNode1();
            Node node2 = edge.getNode2();

            if (estEdges.contains(Edges.directedEdge(node2, node1))) {
                Edge undirEdge = Edges.undirectedEdge(node1, node2);
                estTwoCycle.add(undirEdge);
            }
        }

        Graph trueTwoCycleGraph = new EdgeListGraph(trueGraph.getNodes());

        for (Edge edge : trueTwoCycle) {
            trueTwoCycleGraph.addEdge(edge);
        }

        Graph estTwoCycleGraph = new EdgeListGraph(estGraph.getNodes());

        for (Edge edge : estTwoCycle) {
            estTwoCycleGraph.addEdge(edge);
        }

        estTwoCycleGraph = GraphUtils.replaceNodes(estTwoCycleGraph, trueTwoCycleGraph.getNodes());

        int adjFn = GraphUtils.countAdjErrors(trueTwoCycleGraph, estTwoCycleGraph);
        int adjFp = GraphUtils.countAdjErrors(estTwoCycleGraph, trueTwoCycleGraph);

        Graph undirectedGraph = GraphUtils.undirectedGraph(estTwoCycleGraph);
        int adjCorrect = undirectedGraph.getNumEdges() - adjFp;

        return new TwoCycleErrors(adjCorrect, adjFn, adjFp);
    }

    /**
     * Returns the maximum degree of a graph.
     *
     * @param graph the graph to calculate the degree for
     * @return the maximum degree of the graph
     */
    public static int getDegree(Graph graph) {
        int max = 0;

        for (Node node : graph.getNodes()) {
            if (graph.getAdjacentNodes(node).size() > max) {
                max = graph.getAdjacentNodes(node).size();
            }
        }

        return max;
    }

    /**
     * Calculates the maximum indegree in a given graph.
     *
     * @param graph The graph to calculate the maximum indegree for.
     * @return The maximum indegree in the graph.
     */
    public static int getIndegree(Graph graph) {
        int max = 0;

        for (Node node : graph.getNodes()) {
            if (graph.getAdjacentNodes(node).size() > max) {
                max = graph.getIndegree(node);
            }
        }

        return max;
    }

    /**
     * Traverses a semi-directed edge to identify the next node in the traversal.
     *
     * @param node The starting node of the edge.
     * @param edge The semi-directed edge to be traversed.
     * @return The next node in the traversal, or null if no such node exists.
     */
    public static Node traverseSemiDirected(Node node, Edge edge) {
        if (node == edge.getNode1()) {
            if (edge.getEndpoint1() == Endpoint.TAIL || edge.getEndpoint1() == Endpoint.CIRCLE) {
                return edge.getNode2();
            }
        } else if (node == edge.getNode2()) {
            if (edge.getEndpoint2() == Endpoint.TAIL || edge.getEndpoint2() == Endpoint.CIRCLE) {
                return edge.getNode1();
            }
        }
        return null;
    }

    // Used to find semidirected paths for cycle checking.

    /**
     * Returns a comparison graph based on the specified parameters.
     *
     * @param graph  the original graph to compare
     * @param params the parameters for comparison
     * @return the comparison graph based on the specified parameters
     */
    public static Graph getComparisonGraph(Graph graph, Parameters params) {
        String type = params.getString("graphComparisonType");

        switch (type) {
            case "DAG" -> {
                params.set("graphComparisonType", "DAG");
                return new EdgeListGraph(graph);
            }
            case "CPDAG" -> {
                params.set("graphComparisonType", "CPDAG");
                return GraphTransforms.dagToCpdag(graph);
            }
            case "PAG" -> {
                params.set("graphComparisonType", "PAG");
                return GraphTransforms.dagToPag(graph);
            }
            case null, default -> {
                params.set("graphComparisonType", "DAG");
                return new EdgeListGraph(graph);
            }
        }
    }

    /**
     * Adds forbidden reverse edges for directed edges in the given graph based on the knowledge.
     *
     * @param graph     The graph to add forbidden reverse edges to.
     * @param knowledge The knowledge used to determine the forbidden reverse edges.
     */
    public static void addForbiddenReverseEdgesForDirectedEdges(Graph graph, Knowledge knowledge) {
        List<Node> nodes = graph.getNodes();

        for (Node x : nodes) {
            for (Node y : nodes) {
                if (x == y) continue;
                if (graph.paths().isAncestorOf(x, y)) {
                    knowledge.setForbidden(y.getName(), x.getName());
                }
            }
        }
    }

    /**
     * Removes non-skeleton edges from the given graph based on the provided knowledge. A non-skeleton edge is
     * determined by the name of the nodes. If either node's name starts with "E_", the edge is considered a skeleton
     * edge and will not be removed.
     *
     * @param graph     the graph from which to remove non-skeleton edges
     * @param knowledge the knowledge base to check for forbidden edges
     */
    public static void removeNonSkeletonEdges(Graph graph, Knowledge knowledge) {
        List<Node> nodes = graph.getNodes();

        int numOfNodes = nodes.size();
        for (int i = 0; i < numOfNodes; i++) {
            for (int j = i + 1; j < numOfNodes; j++) {
                Node n1 = nodes.get(i);
                Node n2 = nodes.get(j);

                if (n1.getName().startsWith("E_") || n2.getName().startsWith("E_")) {
                    continue;
                }

                if (!graph.isAdjacentTo(n1, n2)) {
                    if (!knowledge.isForbidden(n1.getName(), n2.getName())) {
                        knowledge.setForbidden(n1.getName(), n2.getName());
                    }

                    if (!knowledge.isForbidden(n2.getName(), n1.getName())) {
                        knowledge.setForbidden(n2.getName(), n1.getName());
                    }
                }
            }
        }
    }

    /**
     * Determines if two edges are compatible.
     *
     * @param edge1 The first edge to compare.
     * @param edge2 The second edge to compare.
     * @return true if the edges are compatible, false otherwise.
     */
    public static boolean compatible(Edge edge1, Edge edge2) {
        if (edge1 == null || edge2 == null) return true;

        Node x = edge1.getNode1();
        Node y = edge1.getNode2();

        Endpoint ex1 = edge1.getProximalEndpoint(x);
        Endpoint ey1 = edge1.getProximalEndpoint(y);

        Endpoint ex2 = edge2.getProximalEndpoint(x);
        Endpoint ey2 = edge2.getProximalEndpoint(y);

        return (ex1 == Endpoint.CIRCLE || (ex1 == ex2 || ex2 == Endpoint.CIRCLE)) && (ey1 == Endpoint.CIRCLE || (ey1 == ey2 || ey2 == Endpoint.CIRCLE));
    }

    /**
     * Returns a Markov blanket of a node for a DAG, CPDAG, MAG, or PAG. This is not necessarily minimal (i.e. not
     * necessarily a Markov Boundary).
     *
     * @param x The target node.
     * @param G The PAG.
     * @return A Markov blanket of the target node.
     */
    public static Set<Node> markovBlanket(Node x, Graph G) {
        Set<Node> mb = new HashSet<>();

        LinkedList<Node> path = new LinkedList<>();

        // Follow all the colliders.
        markovBlanketFollowColliders(null, x, path, G, mb);
        mb.addAll(G.getAdjacentNodes(x));
        mb.remove(x);
        return mb;
    }

    /**
     * This method calculates the Markov Blanket by following colliders in a given graph.
     *
     * @param d    The node representing the direct cause (can be null).
     * @param a    The node for which the Markov Blanket is calculated.
     * @param path A linked list of nodes in the current path.
     * @param G    The graph in which the Markov Blanket is calculated.
     * @param mb   A set to store the nodes in the Markov Blanket.
     */
    private static void markovBlanketFollowColliders(Node d, Node a, LinkedList<Node> path, Graph G, Set<Node> mb) {
        if (path.contains(a)) return;
        path.add(a);

        for (Node b : G.getNodesOutTo(a, Endpoint.ARROW)) {
            if (path.contains(b)) continue;

            // Make sure that d*->a<-* b is a collider.
            if (d != null && !G.isDefCollider(d, a, b)) continue;

            for (Node c : G.getNodesInTo(b, Endpoint.ARROW)) {
                if (path.contains(c)) continue;

                if (!G.isDefCollider(a, b, c)) continue;

                // a *-> b <-* c
                mb.add(b);
                mb.add(c);

                markovBlanketFollowColliders(a, b, path, G, mb);
            }
        }

        path.remove(a);
    }

    /**
     * Calculates the district of a given node in a graph.
     *
     * @param x the node for which the district needs to be calculated
     * @param G the graph in which to calculate the district
     * @return the set of nodes that belong to the district of the given node
     */
    public static Set<Node> district(Node x, Graph G) {
        Set<Node> district = new HashSet<>();
        Set<Node> boundary = new HashSet<>();

        for (Edge e : G.getEdges(x)) {
            if (Edges.isBidirectedEdge(e)) {
                Node other = e.getDistalNode(x);
                district.add(other);
                boundary.add(other);
            }
        }

        do {
            Set<Node> previousBoundary = new HashSet<>(boundary);
            boundary = new HashSet<>();

            for (Node x2 : previousBoundary) {
                for (Edge e : G.getEdges(x2)) {
                    if (Edges.isBidirectedEdge(e)) {
                        Node other = e.getDistalNode(x2);

                        if (!district.contains(other)) {
                            district.add(other);
                            boundary.add(other);
                        }
                    }
                }
            }
        } while (!boundary.isEmpty());

        district.remove(x);

        return district;
    }

    /**
     * Calculates visual-edge adjustments given graph G between two nodes x and y that are subsets of MB(X).
     *
     * @param G                the input graph
     * @param x                the source node
     * @param y                the target node
     * @param numSmallestSizes the number of smallest adjustment sets to return
     * @param graphType        the type of the graph
     * @return the adjustment sets as a set of sets of nodes
     * @throws IllegalArgumentException if the input graph is not a legal MPDAG
     */
    public static Set<Set<Node>> visibleEdgeAdjustments1(Graph G, Node x, Node y, int numSmallestSizes, GraphType graphType) {
        Graph G2 = getGraphWithoutXToY(G, x, y, graphType);

        if (G2 == null) {
            return new HashSet<>();
        }

        if (G2.paths().isLegalMpdag() && G.isAdjacentTo(x, y) && !Edges.isDirectedEdge(G.getEdge(x, y))) {
            System.out.println("The edge from x to y must be visible: " + G.getEdge(x, y));
            return new HashSet<>();
        } else {
            if (G2.paths().isLegalPag() && G.isAdjacentTo(x, y)) {
                Paths paths = G.paths();
                Edge edge = G.getEdge(x, y);
                if (!paths.defVisiblePag(edge.getNode1(), edge.getNode2())) {
                    System.out.println("The edge from x to y must be visible:" + G.getEdge(x, y));
                    return new HashSet<>();
                }
            }
        }

        // Get the Markov blanket for x in G2.
        Set<Node> mbX = markovBlanket(x, G2);
        mbX.remove(x);
        mbX.remove(y);
        mbX.removeAll(G.paths().getDescendants(x));
        return getNMinimalSubsets(getGraphWithoutXToY(G, x, y, graphType), mbX, x, y, numSmallestSizes);
    }

    /**
     * This method calculates visible-edge adjustments for a given graph, two nodes, a number of smallest sizes, and a
     * graph type.
     *
     * @param G                the input graph
     * @param x                the first node
     * @param y                the second node
     * @param numSmallestSizes the number of smallest sizes to consider
     * @param graphType        the type of the graph
     * @return a set of subsets of nodes representing visible-edge adjustments
     */
    public static Set<Set<Node>> visibleEdgeAdjustments3(Graph G, Node x, Node y, int numSmallestSizes, GraphType graphType) {
        Graph G2;

        try {
            G2 = getGraphWithoutXToY(G, x, y, graphType);
        } catch (Exception e) {
            return new HashSet<>();
        }

        if (G2 == null) {
            return new HashSet<>();
        }

        if (!G.isAdjacentTo(x, y)) {
            return new HashSet<>();
        }

        if (G2.paths().isLegalMpdag() && G.isAdjacentTo(x, y) && !Edges.isDirectedEdge(G.getEdge(x, y))) {
            System.out.println("The edge from x to y must be visible: " + G.getEdge(x, y));
            return new HashSet<>();
        } else {
            if (G2.paths().isLegalPag() && G.isAdjacentTo(x, y)) {
                Paths paths = G.paths();
                Edge edge = G.getEdge(x, y);
                if (!paths.defVisiblePag(edge.getNode1(), edge.getNode2())) {
                    System.out.println("The edge from x to y must be visible:" + G.getEdge(x, y));
                    return new HashSet<>();
                }
            }
        }

        Set<Node> anteriority = G.paths().anteriority(x, y);
        anteriority.remove(x);
        anteriority.remove(y);
        anteriority.removeAll(G.paths().getDescendants(x));
        return getNMinimalSubsets(getGraphWithoutXToY(G, x, y, graphType), anteriority, x, y, numSmallestSizes);
    }

    /**
     * Returns a graph that is obtained by removing the edge from node x to node y from the input graph. The type of the
     * output graph is determined by the provided graph type.
     *
     * @param G         the input graph
     * @param x         the starting node of the edge to be removed
     * @param y         the ending node of the edge to be removed
     * @param graphType the type of the output graph (CPDAG, PAG, or MAG)
     * @return the resulting graph after removing the edge from node x to node y
     * @throws IllegalArgumentException if the input graph type is not legal (must be CPDAG, PAG, or MAG)
     */
    public static Graph getGraphWithoutXToY(Graph G, Node x, Node y, GraphType graphType) {
        if (graphType == GraphType.CPDAG) {
            return getGraphWithoutXToYMpdag(G, x, y);
        } else if (graphType == GraphType.PAG) {
            return getGraphWithoutXToYPag(G, x, y);
        } else {
            throw new IllegalArgumentException("Graph must be a legal MPDAG, PAG, or MAG.");
        }
    }

    /**
     * This method returns a graph G2 without the edge between Node x and Node y, creating a Maximum Partially Directed
     * Acyclic Graph (MPDAG) representation.
     *
     * @param G the original graph
     * @param x the starting node of the edge
     * @param y the ending node of the edge
     * @return a graph G2 without the edge between Node x and Node y, in MPDAG representation
     * @throws IllegalArgumentException if the edge from x to y does not exist, is not directed, or does not point
     *                                  towards y
     */
    private static Graph getGraphWithoutXToYMpdag(Graph G, Node x, Node y) {
        Graph G2 = new EdgeListGraph(G);

        if (!G2.isAdjacentTo(x, y)) {
            throw new IllegalArgumentException("Edge from x to y must exist.");
        } else if (Edges.isUndirectedEdge(G2.getEdge(x, y))) {
            throw new IllegalArgumentException("Edge from x to y must be directed.");
        } else if (G2.getEdge(x, y).pointsTowards(x)) {
            throw new IllegalArgumentException("Edge from x to y must point towards y.");
        }

        G2.removeEdge(x, y);
        return G2;
    }

    /**
     * Returns a graph without the edge from x to y in the given graph. If the edge is undirected, bidirected, or
     * partially oriented, the method returns null. If the edge is directed, the method orients the edge from x to y and
     * returns the resulting graph.
     *
     * @param G the graph in which to remove the edge
     * @param x the first node in the edge
     * @param y the second node in the edge
     * @return a graph without the edge from x to y
     * @throws IllegalArgumentException if the edge from x to y does not exist, is not directed, or does not point
     *                                  towards
     */
    private static Graph getGraphWithoutXToYPag(Graph G, Node x, Node y) throws IllegalArgumentException {
        if (!G.isAdjacentTo(x, y)) return null;

        Edge edge = G.getEdge(x, y);

        if (edge == null) {
            throw new IllegalArgumentException("Edge from x to y must exist.");
        } else if (!Edges.isDirectedEdge(edge)) {
            throw new IllegalArgumentException("Edge from x to y must be directed.");
        } else if (edge.pointsTowards(x)) {
            throw new IllegalArgumentException("Edge from x to y must point towards y.");
        } else {
            Paths paths = G.paths();
            if (!paths.defVisiblePag(edge.getNode1(), edge.getNode2())) {
                throw new IllegalArgumentException("Edge from x to y must be visible.");
            }
        }

        Graph G2 = new EdgeListGraph(G);
        G2.removeEdge(x, y);
        return G2;
    }

    /**
     * Returns the subsets T of S such that X _||_ Y | T in G and T is a subset of up to the numSmallestSizes smallest
     * minimal sizes of subsets for S.
     *
     * @param G                the graph in which to compute the subsets
     * @param S                the set of nodes for which to compute the subsets
     * @param X                the first node in the separation
     * @param Y                the second node in the separation
     * @param numSmallestSizes the number of the smallest sizes for the subsets to return
     * @return the subsets T of S such that X _||_ Y | T in G and T is a subset of up to the numSmallestSizes minimal
     * sizes of subsets for S
     */
    private static Set<Set<Node>> getNMinimalSubsets(Graph G, Set<Node> S, Node X, Node Y,
                                                     int numSmallestSizes) {
        if (numSmallestSizes < 0) {
            throw new IllegalArgumentException("numSmallestSizes must be greater than or equal to 0.");
        }

        List<Node> _S = new ArrayList<>(S);
        Set<Set<Node>> nMinimal = new HashSet<>();
        var sublists = new SublistGenerator(_S.size(), _S.size());
        int[] choice;
        int _n = 0;
        int size = -1;

        while ((choice = sublists.next()) != null) {
            List<Node> subset = GraphUtils.asList(choice, _S);
            HashSet<Node> s = new HashSet<>(subset);
            if (G.paths().isMSeparatedFrom(X, Y, s, true)) {

                if (choice.length > size) {
                    size = choice.length;
                    _n++;

                    if (_n > numSmallestSizes) {
                        break;
                    }
                }

                nMinimal.add(s);
            }
        }

        return nMinimal;
    }

    /**
     * Computes the anteriority of the given nodes in a graph. An anterior node is a node that has a directed path to
     * any of the given nodes. This method returns a set of anterior nodes.
     *
     * @param G the graph to compute anteriority on
     * @param x the nodes to compute anteriority for
     * @return a set of anterior nodes
     */
    public static Set<Node> anteriority(Graph G, Node... x) {
        Set<Node> anteriority = new HashSet<>();

        for (Node z : G.getNodes()) {
            for (Node _x : x) {
                if (G.paths().existsDirectedPath(z, _x)) {
                    anteriority.add(z);
                }
            }
        }

        for (Node _x : x) {
            anteriority.remove(_x);
        }

        return anteriority;
    }

    /**
     * Determines if the given graph is a directed acyclic graph (DAG).
     *
     * @param graph the graph to be checked
     * @return true if the graph is a DAG, false otherwise
     */
    public static boolean isDag(Graph graph) {
        boolean allDirected = true;

        for (Edge edge : graph.getEdges()) {
            if (!Edges.isDirectedEdge(edge)) {
                allDirected = false;
            }
        }

        return allDirected && !graph.paths().existsDirectedCycle();
    }

    /**
     * Converts a string spec of a graph--for example, "X1--&gt;X2, X1---X3, X2o-&gt;X4, X3&lt;-&gt;X4" to a Graph. The
     * spec consists of a comma separated list of edge specs of the forms just used in the previous sentence.
     * Unconnected nodes may be listed separately--example: "X,Y-&gt;Z". To specify a node as latent, use "Latent()."
     * Example: "Latent(L1),Y-&gt;L1."
     *
     * @param spec a {@link java.lang.String} object
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public static Graph convert(String spec) {
        Graph graph = new EdgeListGraph();
        StringTokenizer st1;
        StringTokenizer st2;

        for (st1 = new StringTokenizer(spec, ", "); st1.hasMoreTokens(); ) {
            String edgeSpec = st1.nextToken();

            st2 = new StringTokenizer(edgeSpec, "<>-o ");

            String var1 = st2.nextToken();

            if (var1.startsWith("Latent(")) {
                String latentName = (String) var1.subSequence(7, var1.length() - 1);
                GraphNode node = new GraphNode(latentName);
                node.setNodeType(NodeType.LATENT);
                graph.addNode(node);
                continue;
            }

            if (!st2.hasMoreTokens()) {
                graph.addNode(new GraphNode(var1));
                continue;
            }

            String var2 = st2.nextToken();

            if (graph.getNode(var1) == null) {
                graph.addNode(new GraphNode(var1));
            }

            if (graph.getNode(var2) == null) {
                graph.addNode(new GraphNode(var2));
            }

            Node nodeA = graph.getNode(var1);
            Node nodeB = graph.getNode(var2);
            Edge edge = graph.getEdge(nodeA, nodeB);

            if (edge != null) {
                throw new IllegalArgumentException("Multiple edges connecting " + "nodes is not supported.");
            }

            if (edgeSpec.lastIndexOf("-->") != -1) {
                graph.addDirectedEdge(nodeA, nodeB);
            } else if (edgeSpec.lastIndexOf("<--") != -1) {
                graph.addDirectedEdge(nodeB, nodeA);
            } else if (edgeSpec.lastIndexOf("---") != -1) {
                graph.addUndirectedEdge(nodeA, nodeB);
            } else if (edgeSpec.lastIndexOf("<->") != -1) {
                graph.addBidirectedEdge(nodeA, nodeB);
            } else if (edgeSpec.lastIndexOf("o->") != -1) {
                graph.addPartiallyOrientedEdge(nodeA, nodeB);
            } else if (edgeSpec.lastIndexOf("<-o") != -1) {
                graph.addPartiallyOrientedEdge(nodeB, nodeA);
            } else if (edgeSpec.lastIndexOf("o-o") != -1) {
                graph.addNondirectedEdge(nodeB, nodeA);
            } else if (edgeSpec.lastIndexOf("o--") != -1) {
                Edge _edge = new Edge(nodeA, nodeB, Endpoint.CIRCLE, Endpoint.TAIL);
                graph.addEdge(_edge);
            } else if (edgeSpec.lastIndexOf("--o") != -1) {
                Edge _edge = new Edge(nodeA, nodeB, Endpoint.TAIL, Endpoint.CIRCLE);
                graph.addEdge(_edge);
            } else {
                throw new IllegalArgumentException("Unknown edge spec: " + edgeSpec);
            }
        }

        return graph;
    }

    /**
     * Attempts to orient the edges in the graph based on the given knowledge.
     *
     * @param knowledge The knowledge containing the forbidden and required edges to orient.
     * @param graph     The graph to orient the edges in.
     * @param variables The list of nodes representing variables in the graph.
     */
    public static void fciOrientbk(Knowledge knowledge, Graph graph, List<Node> variables) {
        for (Iterator<KnowledgeEdge> it = knowledge.forbiddenEdgesIterator(); it.hasNext(); ) {
            KnowledgeEdge edge = it.next();

            //match strings to variables in the graph.
            Node from = GraphSearchUtils.translate(edge.getFrom(), variables);
            Node to = GraphSearchUtils.translate(edge.getTo(), variables);

            if (from == null || to == null) {
                continue;
            }

            if (graph.getEdge(from, to) == null) {
                continue;
            }

            // Orient to*->from
            graph.setEndpoint(to, from, Endpoint.ARROW);
        }

        for (Iterator<KnowledgeEdge> it = knowledge.requiredEdgesIterator(); it.hasNext(); ) {
            KnowledgeEdge edge = it.next();

            //match strings to variables in this graph
            Node from = GraphSearchUtils.translate(edge.getFrom(), variables);
            Node to = GraphSearchUtils.translate(edge.getTo(), variables);

            if (from == null || to == null) {
                continue;
            }

            if (graph.getEdge(from, to) == null) {
                continue;
            }

            graph.setEndpoint(to, from, Endpoint.TAIL);
            graph.setEndpoint(from, to, Endpoint.ARROW);
        }
    }

    /**
     * Trims the given graph based on the specified trimming style.
     *
     * @param targets       the list of target nodes to be trimmed
     * @param graph         the graph to be trimmed
     * @param trimmingStyle the style indicating how the graph should be trimmed - 1: No trimming - 2: Trim nodes
     *                      adjacent to target nodes - 3: Trim nodes in the Markov blanket of target nodes - 4: Trim
     *                      semidirected arcs adjacent to target nodes
     * @return the trimmed graph
     * @throws IllegalArgumentException if an unknown trimming style is given
     */
    public static Graph trimGraph(List<Node> targets, Graph graph, int trimmingStyle) {
        switch (trimmingStyle) {
            case 1:
                break;
            case 2:
                graph = trimAdjacentToTarget(targets, graph);
                break;
            case 3:
                graph = trimMarkovBlanketGraph(targets, graph);
                break;
            case 4:
                graph = trimSemidirected(targets, graph);
                break;
            default:
                throw new IllegalArgumentException("Unknown trimming style: " + trimmingStyle);
        }

        return graph;
    }

    /**
     * Trims the nodes in the graph that are adjacent to any of the target nodes.
     *
     * @param targets The list of target nodes.
     * @param graph   The graph to be trimmed.
     * @return The trimmed graph.
     */
    private static Graph trimAdjacentToTarget(List<Node> targets, Graph graph) {
        Graph _graph = new EdgeListGraph(graph);

        M:
        for (Node m : graph.getNodes()) {
            if (!targets.contains(m)) {
                for (Node n : targets) {
                    if (graph.isAdjacentTo(m, n)) {
                        continue M;
                    }
                }

                _graph.removeNode(m);
            }
        }

        return _graph;
    }

    /**
     * Trims the Markov blanket graph based on the given target nodes.
     *
     * @param targets the list of target nodes to trim the Markov blanket graph
     * @param graph   the original graph from which the Markov blanket graph is derived
     * @return the trimmed Markov blanket graph
     */
    private static Graph trimMarkovBlanketGraph(List<Node> targets, Graph graph) {
        Graph mbDag = new EdgeListGraph(graph);

        M:
        for (Node n : graph.getNodes()) {
            if (!targets.contains(n)) {
                for (Node m : targets) {
                    if (graph.isAdjacentTo(n, m)) {
                        continue M;
                    }
                }

                for (Node m : targets) {
                    Set<Node> ch = new HashSet<>(graph.getChildren(m));
                    ch.retainAll(graph.getChildren(n));

                    if (!ch.isEmpty()) {
                        continue M;
                    }
                }

                mbDag.removeNode(n);
            }
        }

        return mbDag;
    }

    /**
     * Trims a semidirected graph by removing nodes that are not reachable from the target nodes.
     *
     * @param targets the list of target nodes
     * @param graph   the original graph to be trimmed
     * @return a trimmed graph with only the nodes reachable from the target nodes
     */
    private static Graph trimSemidirected(List<Node> targets, Graph graph) {
        Graph _graph = new EdgeListGraph(graph);

        M:
        for (Node m : graph.getNodes()) {
            if (!targets.contains(m)) {
                for (Node n : targets) {
                    if (graph.paths().existsSemiDirectedPath(m, n)) {
                        continue M;
                    }
                }

                _graph.removeNode(m);
            }
        }

        return _graph;
    }

    /**
     * Checks if the given trek in a graph is a confounding trek. This is a trek from measured node x to measured node y
     * that has only latent nodes in between.
     *
     * @param trueGraph the true graph representing the causal relationships between nodes
     * @param trek      the trek to be checked
     * @param x         the first node in the trek
     * @param y         the last node in the trek
     * @return true if the trek is a confounding trek, false otherwise
     */
    public static boolean isConfoundingTrek(Graph trueGraph, List<Node> trek, Node x, Node y) {
        if (x.getNodeType() != NodeType.MEASURED || y.getNodeType() != NodeType.MEASURED) {
            return false;
        }

        Node source = getTrekSource(trueGraph, trek);

        if (source == x || source == y) {
            return false;
        }

        if (trek.size() < 3) {
            return false;
        }

        boolean allLatent = true;

        for (int i = 1; i < trek.size() - 1; i++) {
            Node z = trek.get(i);

            if (z.getNodeType() != NodeType.LATENT) {
                allLatent = false;
                break;
            }
        }

        return allLatent;
    }

    /**
     * This method returns the source node of a given trek in a graph.
     *
     * @param graph The graph containing the nodes and edges.
     * @param trek  The list of nodes representing the trek.
     * @return The source node of the trek.
     */
    public static Node getTrekSource(Graph graph, List<Node> trek) {
        Node source = trek.getLast();

        // Find the first node where the direction is left to right.
        for (int i = 0; i < trek.size() - 1; i++) {
            Node n1 = trek.get(i);
            Node n2 = trek.get(i + 1);

            if (graph.getEdge(n1, n2).pointsTowards(n2)) {
                source = n1;
                break;
            }
        }

        return source;
    }

    /**
     * Determines if the given bidirected edge has a latent confounder in the true graph--that is, whether for X
     * &lt;-&gt; Y there is a latent node Z such that X &lt;- (Z) -&gt; Y.
     *
     * @param edge      The edge to check.
     * @param trueGraph The true graph (DAG, CPDAG, PAG_of_the_true_DAG).
     * @return true if the given bidirected has a latent confounder in the true graph, false otherwise.
     * @throws IllegalArgumentException if the edge is not bidirected.
     */
    public static boolean isCorrectBidirectedEdge(Edge edge, Graph trueGraph) {
        if (!Edges.isBidirectedEdge(edge)) {
            throw new IllegalArgumentException("The edge is not bidirected: " + edge);
        }

        Node x = edge.getNode1();
        Node y = edge.getNode2();

        List<List<Node>> treks = trueGraph.paths().treks(x, y, 3);
        boolean existsLatentConfounder = false;

        for (List<Node> trek : treks) {
            if (isConfoundingTrek(trueGraph, trek, x, y)) {
                existsLatentConfounder = true;
            }
        }

        return existsLatentConfounder;
    }

    /**
     * Guarantees the correctness of a Partial Ancestral Graph (PAG) by repairing faulty structures such as cycles,
     * violations of maximality, and incorrectly oriented edges. It uses FCI orientation rules and knowledge constraints
     * to perform the repair process.
     *
     * @param pag            the initial PAG to be repaired
     * @param fciOrient      the FCI (Fast Causal Inference) orientation utility for edge orientation
     * @param knowledge      the background knowledge to enforce during the repair process
     * @param knownColliders a set of triples representing unshielded colliders to be enforced
     * @param verbose        whether to provide detailed logging of the repair process
     * @param selection      a set of nodes to be considered during the maximality repair
     * @return the repaired PAG that satisfies required constraints and is free of faults
     */
    public static Graph guaranteePag(Graph pag, FciOrient fciOrient, Knowledge knowledge,
                                     Set<Triple> knownColliders,
                                     boolean verbose, Set<Node> selection) {
        if (verbose) {
            TetradLogger.getInstance().log("Repairing faulty PAG...");
        }


        Graph orig = new EdgeListGraph(pag);

        if (orig.paths().isLegalPag()) {
            return orig;
        }

        boolean changed;

        do {
            changed = false;

            changed |= removeAlmostCycles(pag, knownColliders, verbose);
            changed |= repairMaximality(pag, verbose, selection, fciOrient, knowledge, knownColliders);
            changed |= removeCycles(pag, verbose);
            reorientWithFci(pag, fciOrient, knowledge, knownColliders, verbose);
        } while (changed);

        MagToPag dagToPag = new MagToPag(GraphTransforms.zhangMagFromPag(pag));
        dagToPag.setKnowledge(knowledge);
        Graph pag2 = dagToPag.convert();

        if (pag2.equals(orig)) {
            if (verbose) TetradLogger.getInstance().log("NO FAULTY PAG CORRECTIONS MADE.");
        } else {
            if (verbose) TetradLogger.getInstance().log("Faulty PAG repaired.");
        }

        return pag2;
    }

    private static boolean removeAlmostCycles(Graph pag,
                                              Set<Triple> extraKnownColliders,
                                              boolean verbose) {

        boolean changedOverall = false;

        boolean changedThisRound;
        int round = 0;

        do {
            changedThisRound = false;
            round++;

            Graph mag = GraphTransforms.zhangMagFromPag(pag);
            Map<Node, Set<Node>> reachable =
                    buildDescendantsMap(mag);

            List<Edge> candidates = mag.getEdges().stream()
                    .filter(Edges::isBidirectedEdge)
                    .filter(e ->
                            reachable.getOrDefault(e.getNode1(), Set.of()).contains(e.getNode2()) ||
                            reachable.getOrDefault(e.getNode2(), Set.of()).contains(e.getNode1()))
                    .toList();

            for (Edge edge : candidates) {
                Node x = edge.getNode1(), y = edge.getNode2();

                for (Iterator<Triple> it = extraKnownColliders.iterator();
                     it.hasNext(); ) {

                    Triple t = it.next();
                    if (t.getY().equals(x) &&
                        (t.getZ().equals(y) || t.getX().equals(y))) {

                        Node u = t.getX().equals(y) ? t.getZ() : t.getX();

                        if (!pag.isAdjacentTo(u, y)) {
                            pag.addNondirectedEdge(u, y);
                            it.remove();
                            changedThisRound = true;
                            changedOverall = true;
                        }
                    }
                }
            }

            if (verbose) {
                TetradLogger.getInstance()
                        .log("Round " + round +
                             ", candidates = " + candidates.size() +
                             ", changed = " + changedThisRound);
            }
        } while (changedThisRound);   // stop when a complete pass makes no changes

        return changedOverall;        // tell the caller whether anything changed *at all*
    }

    /**
     * Repairs the maximality of a PAG (Partial Ancestral Graph) by ensuring that any inducing path between two nodes
     * not currently adjacent in the graph results in an added non-directed edge. The method modifies the graph
     * in-place.
     *
     * @param pag            the Partial Ancestral Graph to be repaired for maximality
     * @param verbose        if true, logs the actions performed during the repair process
     * @param selection      a set of nodes to be considered during the inducing path check
     * @param fciOrient      The Fci orientation procedure.
     * @param knowledge      The knowledge.
     * @param knownColliders Known colliders.
     * @return true if the graph was modified during the repair process; false otherwise
     */
    public static boolean repairMaximality(Graph pag, boolean verbose, Set<Node> selection, FciOrient fciOrient,
                                           Knowledge knowledge, Set<Triple> knownColliders) {
        boolean changed = false;
        for (Node x : pag.getNodes()) {
            for (Node y : pag.getNodes()) {
                if (x != y && !pag.isAdjacentTo(x, y) && pag.paths().existsInducingPath(x, y, selection)) {
                    if (!pag.isAdjacentTo(x, y)) {
                        pag.addNondirectedEdge(x, y);
                        changed = true;
                        if (verbose) TetradLogger.getInstance().log("Maximality repair: added edge " + x + " o-o " + y);
                    }
                }

//                reorientWithFci(pag, fciOrient, knowledge, knownColliders, verbose);
            }
        }
        return changed;
    }

    private static boolean removeCycles(Graph pag, boolean verbose) {
        boolean changed = false;
        Graph mag = GraphTransforms.zhangMagFromPag(pag);
        Map<Node, Set<Node>> reachableFrom = buildDescendantsMap(mag);

        for (Node x : mag.getNodes()) {
            if (reachableFrom.get(x).contains(x)) {
                List<List<Node>> paths = mag.paths().directedPaths(x, x, -1);
                for (List<Node> path : paths) {
                    for (int i = 1; i < path.size() - 1; i++) {
                        Node y = path.get(i);
                        Node a = path.get(i - 1);
                        Node b = path.get(i + 1);
                        if (pag.isParentOf(a, y) && pag.isParentOf(b, y) && !pag.isAdjacentTo(a, b)) {
                            if (!pag.isAdjacentTo(a, b)) {
                                pag.addNondirectedEdge(a, b);
                                changed = true;
                            }
                        }
                    }
                }
            }
        }

        if (verbose && changed) {
            TetradLogger.getInstance().log("Cycles removed via covering colliders.");
        }

        return changed;
    }

    private static void reorientWithFci(Graph pag, FciOrient fciOrient, Knowledge knowledge,
                                        Set<Triple> unshieldedColliders, boolean verbose) {
        reorientWithCircles(pag, verbose);
        fciOrient.fciOrientbk(knowledge, pag, pag.getNodes());
        recallInitialColliders(pag, unshieldedColliders, knowledge);
        fciOrient.finalOrientation(pag);
    }

    private static Map<Node, Set<Node>> buildDescendantsMap(Graph graph) {
        Map<Node, Set<Node>> map = new HashMap<>();
        for (Node node : graph.getNodes()) {
            Set<Node> descendants = new HashSet<>(graph.paths().getDescendants(node));
            descendants.remove(node);
            map.put(node, descendants);
        }
        return map;
    }

    /**
     * Calculates the number of induced adjacencies in the given estiamted Partial Ancestral (PAG) with respect to the
     * given true PAG. An induced adjacency in a PAG is an edge that is adjacent in the estimated graph, but not in the
     * true graph, and is not covering a collider or noncollider in the true graph.
     *
     * @param trueGraph the true PAG.
     * @param estGraph  the estimated PAG.
     * @return the number of induced adjacencies in the PAG.
     * @see #edgeInEstInTrue(Graph, Graph, Node, Node)
     */
    public static int getNumInducedAdjacenciesInPag(Graph trueGraph, Graph estGraph) {

        // Assume trueGraph and estGraph are PAGs; information may be unhelpful if not.
        int count = 0;

        for (Edge edge : estGraph.getEdges()) {
            Node x = edge.getNode1();
            Node y = edge.getNode2();

            boolean isInducedAdjacency = edgeInEstInTrue(trueGraph, estGraph, x, y);

            if (isInducedAdjacency) {
                count++;
            }
        }

        return count;
    }

    /**
     * Returns the number of covering edges in the given estimated partial ancestral graph (PAG) with respect to the
     * given true PAG. A covering edge in a PAG connects two nodes such that the edges in the true graph represent the
     * edges in the estimated graph.
     *
     * @param trueGraph The true ancestral graph
     * @param estGraph  The estimated ancestral graph
     * @return The count of covering edges in the PAG
     * @see #isCoveringAdjacency(Graph, Graph, Node, Node)
     */
    public static int getNumCoveringAdjacenciesInPag(Graph trueGraph, Graph estGraph) {

        // Assume trueGraph and estGraph are PAGs; information may be unhelpful if not.
        int count = 0;

        for (Edge edge : estGraph.getEdges()) {
            Node x = edge.getNode1();
            Node y = edge.getNode2();

            boolean isCoveringAdjacency = isCoveringAdjacency(trueGraph, estGraph, x, y);

            if (isCoveringAdjacency) {
                count++;
            }
        }

        return count;
    }

    /**
     * Checks if an edge between two nodes is in the estimated graph but is not adjacent in the true graph.
     *
     * @param trueGraph The true graph.
     * @param estGraph  The estimated graph.
     * @param x         The first node.
     * @param y         The second node.
     * @return True if the edge is in the estimated graph but not in the true graph, false otherwise.
     * @see #isCoveringAdjacency(Graph, Graph, Node, Node)
     */
    private static boolean edgeInEstInTrue(Graph trueGraph, Graph estGraph, Node x, Node y) {
        boolean inEstNotTrue = false;

        if (estGraph.isAdjacentTo(x, y)) {
//            boolean coveringEdge = isCoveringAdjacency(trueGraph, estGraph, x, y);

            // If the edge is not a covering edge, and it is non-adjacent in the true graph, then it is an
            // induced edge in the true graph. We count the induced edges.
            if (trueGraph.isAdjacentTo(x, y)) {// && !coveringEdge) {
                inEstNotTrue = true;
            }
        }

        return inEstNotTrue;
    }

    /**
     * Determines whether an edge between two nodes in the estimated graph is covering a collider or noncollider in the
     * true graph. This is the case if the edge is adjacent in the estimated graph, but not in the true graph, and there
     * is a common adjacent node in the estimated graph that is also a common adjacent node in the true graph. If the
     * path through the common adjacent node is a collider in the true graph if and only if it is a noncollider in the
     * estimated graph, then the edge is covering a collider or noncollider.
     *
     * @param trueGraph the true graph
     * @param estGraph  the estimated graph
     * @param x         the first node
     * @param y         the second node
     * @return true if the edge is covering a collider or noncollider, false otherwise
     */
    public static boolean isCoveringAdjacency(Graph trueGraph, Graph estGraph, Node x, Node y) {

        // We need to look at common adjacents of x and y in the estimated graph, which are also common
        // adjacents of x and y in the true graph.
        List<Node> commonAdjacents = estGraph.getAdjacentNodes(x);
        commonAdjacents.retainAll(estGraph.getAdjacentNodes(y));

        boolean coveringAdjacency = false;

        for (Node z : commonAdjacents) {

            // We need to determine if adjacency x *-* y in the estimated graph is covering a collider or
            // noncollider in the true graph. For this, we first of all need to make sure that x and y are
            // non-adjacent in the true graph. Then we need to check if some path through a common adjacent z
            // in both the true and estimated graphs is a collider in the true graph if and only if it is
            // a noncollider in the estimated graph.
            if (!trueGraph.isAdjacentTo(x, y)) {
                if (trueGraph.isAdjacentTo(x, z) && trueGraph.isAdjacentTo(y, z)) {
                    boolean colliderInTrueGraph = trueGraph.isDefCollider(x, z, y);
                    boolean colliderInEstGraph = estGraph.isDefCollider(x, z, y);

                    if (colliderInTrueGraph != colliderInEstGraph) {
                        coveringAdjacency = true;
                        break;
                    }
                }
            }
        }

        return coveringAdjacency;
    }

    /**
     * Creates a new list containing the elements of the given array.
     *
     * @param choice the array of integers to be converted to a list
     * @return a list of integers containing the elements of the array
     */
    public static @NotNull List<Integer> asList(int[] choice) {
        return MathUtils.getInts(choice);
    }

    /**
     * Returns D-SEP(x, y) for a MAG G (or inducing path graph G, as in Causation, Prediction and Search). This method
     * implements a reachability style.
     * <p>
     * We trust the user to make sure the given graph is a MAG or IPG; we don't check this.
     *
     * @param x The one endpoint.
     * @param y The other endpoint.
     * @param G The MAG.
     * @return D-SEP(x, y) for MAG G.
     */
    public static Set<Node> dsep0(Node x, Node y, Graph G) {

        Set<Node> dsep = new HashSet<>();
        Set<Node> path = new HashSet<>();

        for (Node a : G.getAdjacentNodes(x)) {
            if (path.contains(a)) continue;
            path.add(a);

            if (G.getEdge(x, a).getDistalEndpoint(x) != Endpoint.ARROW) {
                dsep.add(a);
            }

            for (Node b : G.getAdjacentNodes(a)) {
                if (path.contains(b)) continue;
                path.add(b);

                if (G.isDefCollider(x, a, b)) {
                    if (G.paths().isAncestorOf(a, y)) {
                        dsep.add(a);
                        dsep.add(b);
                        dsepFollowPath(a, b, x, y, dsep, path, G);
                    }
                }

                path.remove(b);
            }

            path.remove(a);
        }

        dsep.remove(x);
        dsep.remove(y);

        return dsep;
    }

    /**
     * This method follows a path in a MAG (or inducing path graph G, as in Causation, Prediction and Search),
     * reachability style, to determine the D-SEP(a, y) set.
     *
     * @param a    The current node.
     * @param x    The starting node.
     * @param y    The ending node.
     * @param dsep The D-SEP(a, y) set being built.
     * @param path The current path.
     * @param G    The MAG.
     */
    private static void dsepFollowPath(Node a, Node b, Node x, Node y, Set<Node> dsep, Set<Node> path, Graph G) {
        for (Node c : G.getAdjacentNodes(b)) {
            if (path.contains(c)) continue;
            path.add(c);

            if (G.isDefCollider(a, b, c)) {
                if (G.paths().isAncestorOf(b, x) || G.paths().isAncestorOf(b, y)) {
                    dsep.add(b);
                    dsep.add(c);
                    dsepFollowPath(b, c, x, y, dsep, path, G);
                }
            }

            path.remove(c);
        }
    }

    /**
     * Returns D-SEP(x, y) for a MAG G. This method implements a non-reachability style.
     * <p>
     * We trust the user to make sure the given graph is a MAG or IPG; we don't check this.
     *
     * @param x The one endpoint.
     * @param y The other endpoint.
     * @param G The MAG.
     * @return D-SEP(x, y) for MAG G.
     */
    public static Set<Node> dsep(Node x, Node y, Graph G) {

        Set<Node> dsep = new HashSet<>();
        Set<Node> path = new HashSet<>();

        dsepFollowPath(x, x, y, dsep, path, G);

        dsep.remove(x);
        dsep.remove(y);

        return dsep;
    }

    /**
     * This method follows a path in a MAG to determine the D-SEP(a, y) set. This method implements a non-reachability
     * style.
     *
     * @param a    The current node.
     * @param x    The starting node.
     * @param y    The ending node.
     * @param dsep The D-SEP(a, y) set being built.
     * @param path The current path.
     * @param G    The MAG.
     */
    private static void dsepFollowPath(Node a, Node x, Node y, Set<Node> dsep, Set<Node> path, Graph G) {

        if (path.contains(a)) return;
        path.add(a);

        for (Node b : G.getAdjacentNodes(a)) {
            if (path.contains(b)) continue;
            path.add(b);

            if (G.getEdge(a, b).getDistalEndpoint(a) != Endpoint.ARROW) {
                dsep.add(b);
            }

            for (Node c : G.getAdjacentNodes(b)) {
                if (path.contains(c)) continue;
                path.add(c);

                if (G.isDefCollider(a, b, c)) {
                    if (G.paths().isAncestorOf(b, x) || G.paths().isAncestorOf(b, y)) {
                        dsep.add(b);
                        dsep.add(c);
                        dsepFollowPath(b, x, y, dsep, path, G);
                    }
                }

                path.remove(c);
            }

            path.remove(b);
        }

        path.remove(a);
    }

    /**
     * Returns D-SEP(x, y) for a MAG G. Somewhat optimized version.
     * <p>
     * We trust the user to make sure the given graph is a MAG or IPG; we don't check this.
     *
     * @param x The one endpoint.
     * @param y The other endpoint.
     * @param G The MAG.
     * @return D-SEP(x, y) for MAG G.
     */
    public static Set<Node> dsep2(Node x, Node y, Graph G) {
        Set<Node> dsep = new HashSet<>();
        Set<Node> visited = new HashSet<>();

        // Precompute ancestors for efficiency
        Set<Node> ancestorsOfX = new HashSet<>(G.paths().getAncestors(x));
        Set<Node> ancestorsOfY = new HashSet<>(G.paths().getAncestors(y));

        // Initialize stack for iterative DFS
        Deque<Node> stack = new ArrayDeque<>();
        stack.push(x);

        while (!stack.isEmpty()) {
            Node a = stack.pop();

            if (visited.contains(a)) continue;
            visited.add(a);

            for (Node b : G.getAdjacentNodes(a)) {
                if (visited.contains(b)) continue;

                // Add to D-SEP if edge does not point away
                if (G.getEdge(a, b).getDistalEndpoint(a) != Endpoint.ARROW) {
                    dsep.add(b);
                }

                for (Node c : G.getAdjacentNodes(b)) {
                    if (visited.contains(c)) continue;

                    // Check for collider and ancestor condition
                    if (G.isDefCollider(a, b, c)) {
                        if (ancestorsOfX.contains(b) || ancestorsOfY.contains(b)) {
                            dsep.add(b);
                            dsep.add(c);
                            stack.push(b); // Continue exploration from b
                        }
                    }
                }
            }
        }

        dsep.remove(x);
        dsep.remove(y);
        return dsep;
    }

    /**
     * Returns D-SEP(x, y) for a MAG G. This method implements a reachability style.
     * <p>
     * We trust the user to make sure the given graph is a MAG or IPG; we don't check this.
     *
     * @param x The one endpoint.
     * @param y The other endpoint.
     * @param G The MAG.
     * @return D-SEP(x, y) for MAG G.
     */
    public static Set<Node> dsepReachability(Node x, Node y, Graph G) {
        Set<Node> dsep = new HashSet<>();
        Set<Node> visited = new HashSet<>();
        Queue<Node> queue = new LinkedList<>();

        // Start the reachability exploration from x
        queue.add(x);
        visited.add(x);

        while (!queue.isEmpty()) {
            Node current = queue.poll();

            for (Node neighbor : G.getAdjacentNodes(current)) {
                if (visited.contains(neighbor)) continue;

                // Check edge validity
                if (G.getEdge(current, neighbor).getDistalEndpoint(current) != Endpoint.ARROW) {
                    dsep.add(neighbor);
                    queue.add(neighbor);
                    visited.add(neighbor);
                } else {
                    // Check collider conditions
                    for (Node next : G.getAdjacentNodes(neighbor)) {
                        if (visited.contains(next)) continue;

                        if (G.isDefCollider(current, neighbor, next) &&
                            (G.paths().isAncestorOf(neighbor, x) || G.paths().isAncestorOf(neighbor, y))) {
                            dsep.add(neighbor);
                            dsep.add(next);
                            queue.add(neighbor);
                            queue.add(next);
                            visited.add(neighbor);
                            visited.add(next);
                        }
                    }
                }
            }
        }

        // Remove x and y from the D-SEP set
        dsep.remove(x);
        dsep.remove(y);

        return dsep;
    }

    /**
     * Reorients all edges in a Graph as o-o. This method is used to apply the o-o orientation to all edges in the given
     * Graph following the PAG (Partially Ancestral Graph) structure.
     *
     * @param pag     The Graph to be reoriented.
     * @param verbose A boolean value indicating whether verbose output should be printed.
     */
    public static void reorientWithCircles(Graph pag, boolean verbose) {
        if (verbose) {
            TetradLogger.getInstance().log("Orient all edges in PAG as o-o:");
        }
        pag.reorientAllWith(Endpoint.CIRCLE);
    }

    /**
     * Recall unshielded triples in a given graph.
     *
     * @param pag              The graph to recall unshielded triples from.
     * @param initialColliders The set of unshielded colliders that need to be recalled from the CPDAG.
     * @param knowledge        the knowledge object.
     */
    public static void recallInitialColliders(Graph pag, Set<Triple> initialColliders, Knowledge knowledge) {
        for (Triple triple : new HashSet<>(initialColliders)) {
            Node x = triple.getX();
            Node b = triple.getY();
            Node y = triple.getZ();

            if (!distinct(x, b, y)) {
                throw new IllegalArgumentException("Nodes not distinct.");
            }

            if (triple(pag, x, b, y) && !pag.isAdjacentTo(x, y) && colliderAllowed(pag, x, b, y, knowledge)) {
                pag.setEndpoint(x, b, Endpoint.ARROW);
                pag.setEndpoint(y, b, Endpoint.ARROW);
            }
        }
    }

    /**
     * Checks if three nodes are connected in a graph.
     *
     * @param graph the graph to check for connectivity
     * @param a     the first node
     * @param b     the second node
     * @param c     the third node
     * @return {@code true} if all three nodes are connected, {@code false} otherwise
     */
    public static boolean triple(Graph graph, Node a, Node b, Node c) {
        return distinct(a, b, c) && graph.isAdjacentTo(a, b) && graph.isAdjacentTo(b, c);
    }

    /**
     * Determines if the collider is allowed.
     *
     * @param pag       The Graph representing the PAG.
     * @param x         The Node object representing the first node.
     * @param b         The Node object representing the second node.
     * @param y         The Node object representing the third node.
     * @param knowledge The Knowledge object.
     * @return true if the collider is allowed, false otherwise.
     */
    public static boolean colliderAllowed(Graph pag, Node x, Node b, Node y, Knowledge knowledge) {
        return FciOrient.isArrowheadAllowed(x, b, pag, knowledge) && FciOrient.isArrowheadAllowed(y, b, pag, knowledge);
    }

    /**
     * Determines whether three {@link Node} objects are distinct.
     *
     * @param n the nodes to check for distinctness
     * @return true if x, b, and y are distinct; false otherwise
     */
    public static boolean distinct(Node... n) {
        for (int i = 0; i < n.length; i++) {
            for (int j = i + 1; j < n.length; j++) {
                if (n[i].equals(n[j])) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Initializes and evaluates p-values for local Markov properties in a given graph.
     *
     * @param dag            The input graph, a DAG (Directed Acyclic Graph).
     * @param preserveMarkov Flag indicating that the method should proceed only if set to true.
     * @param test           The statistical test instance used to check for conditional independence.
     * @param pValues        A map to store the p-values, indexed by pairs of nodes.
     * @return The percentage of p-values that are less than the significance level (alpha) used in the test. Returns
     * 0.0 if the number of p-values is less than 5 or if preserveMarkov is false or test instance is invalid.
     * @throws IllegalArgumentException if preserveMarkov is false.
     * @throws InterruptedException     if any
     */
    public static double localMarkovInitializePValues(Graph dag, boolean preserveMarkov, IndependenceTest test,
                                                      Map<Pair<Node, Node>, Set<Double>> pValues) throws InterruptedException {
        if (!preserveMarkov) {
            throw new IllegalArgumentException("This method should only be called when preserveMarkov is true.");
        }

        if (test == null || test instanceof MsepTest) {
            return Double.NaN;
        }

//        MsepTest msep = new MsepTest(dag);

        for (Node x : dag.getNodes()) {
            Set<Node> parentsX = new HashSet<>(dag.getParents(x));

            for (Node y : dag.getNodes()) {
                if (x.equals(y)) {
                    continue;
                }

                if (!parentsX.contains(y) && !dag.paths().existsDirectedPath(x, y, null)) {
                    IndependenceResult result = test.checkIndependence(x, y, parentsX);
                    if (result.isIndependent()) {
//                        if (msep.isMSeparated(x, y, parentsX)) {
                        pValues.putIfAbsent(Pair.of(x, y), new HashSet<>());
                        pValues.get(Pair.of(x, y)).add(result.getPValue());
//                        }
                    }
                }
            }
        }

        // Calculate the percentage of p-values in the pValues map that are less than alpha
        int numPValues = 0;
        int numSignificant = 0;

        for (Pair<Node, Node> pair : pValues.keySet()) {
            numPValues += pValues.get(pair).size();

            for (Double pValue : pValues.get(pair)) {
                if (pValue < test.getAlpha()) {
                    numSignificant++;
                }
            }
        }

        return numPValues < 5 ? 0.0 : (double) numSignificant / numPValues;
    }

    /**
     * Calculates the p-value using the Anderson-Darling test for a given set of p-values from an independence test.
     *
     * @param _pValues A map where each key is a pair of nodes, and each value is a set of p-values associated with that
     *                 node pair.
     * @return The p-value calculated from the Anderson-Darling test.
     */
    public static double pValuesAdP(Map<Pair<Node, Node>, Set<Double>> _pValues) {
        // Calculate the percentage of p-values in the _pValues map that are less than alpha
        List<Double> pValues = new ArrayList<>();

        for (Pair<Node, Node> pair : _pValues.keySet()) {
            pValues.addAll(_pValues.get(pair));
        }

        // convert the List of p-values to a double[] array.
        double[] pValuesArray = new double[pValues.size()];
        for (int i = 0; i < pValues.size(); i++) {
            pValuesArray[i] = pValues.get(i);
        }

        AndersonDarlingTest _test = new AndersonDarlingTest(pValuesArray);
        return _test.getP();
    }

    /**
     * Processes the given graph by fixing the directions of edges to ensure consistency, flipping edges where
     * necessary, and optionally preserving ancillary graph information.
     *
     * @param graph the input graph whose edge directions are to be fixed
     * @return a new graph with corrected edge directions, preserving ancillary graph information if present
     */
    public static @NotNull Graph fixDirections(Graph graph) {
        List<Edge> edges = new ArrayList<>(graph.getEdges());
        EdgeListGraph fixedDirections = new EdgeListGraph(graph.getNodes());

        for (Edge edge : edges) {
            if (edge.pointsTowards(edge.getNode1())) {
                fixedDirections.addEdge(edge.sameEdgeFlippedDirection());
            } else {
                fixedDirections.addEdge(edge);
            }
        }

        Graph samplingGraph = ((EdgeListGraph) graph).getAncillaryGraph("samplingGraph");

        if (samplingGraph != null) {
            fixedDirections.setAncillaryGraph("samplingGraph", samplingGraph);
        }

        return fixedDirections;
    }

    public static void orientCollider(Graph g, Node x, Node z, Node y) {
        g.setEndpoint(x, z, Endpoint.ARROW);
        g.setEndpoint(y, z, Endpoint.ARROW);
    }

    /**
     * The GraphType enum represents the types of graphs that can be used in the application.
     */
    public enum GraphType {

        /**
         * The CPDAG graph type.
         */
        CPDAG,

        /**
         * The PAG graph type.
         */
        PAG
    }

    /**
     * The Counts class represents a matrix of counts for different edge types.
     */
    private static class Counts {

        /**
         * The counts.
         */
        private final int[][] counts;

        /**
         * Constructs a new Counts.
         */
        public Counts() {
            this.counts = new int[10][8];
        }

        /**
         * Increments the count at the specified matrix position.
         *
         * @param m the row index of the matrix
         * @param n the column index of the matrix
         */
        public void increment(int m, int n) {
            this.counts[m][n]++;
        }

        /**
         * Returns the count at the specified matrix position.
         *
         * @param m the row index of the matrix
         * @param n the column index of the matrix
         * @return the count at the specified matrix position
         */
        public int getCount(int m, int n) {
            return this.counts[m][n];
        }

        /**
         * Adds the counts from another Counts object to this Counts object.
         *
         * @param counts2 the Counts object containing the counts to add
         */
        public void addAll(Counts counts2) {
            for (int i = 0; i < 8; i++) {
                for (int j = 0; j < 6; j++) {
                    this.counts[i][j] += counts2.getCount(i, j);
                }
            }
        }

        /**
         * Returns the counts.
         *
         * @return a int[][]
         */
        public int[][] countArray() {
            return this.counts;
        }
    }

    /**
     * Represents a comparison between two graphs.
     */
    public static class GraphComparison {

        /**
         * Counts.
         */
        private final int[][] counts;

        /**
         * Adjacency false negatives.
         */
        private final int adjFn;

        /**
         * Adjacency false positives.
         */
        private final int adjFp;

        /**
         * Adjacency correct.
         */
        private final int adjCorrect;

        /**
         * Arrowhead false negatives.
         */
        private final int ahdFn;

        /**
         * Arrowhead false positives.
         */
        private final int ahdFp;

        /**
         * Arrowhead correct.
         */
        private final int ahdCorrect;

        /**
         * Adjacency precision.
         */
        private final double adjPrec;

        /**
         * Adjacency recall.
         */
        private final double adjRec;

        /**
         * Arrowhead precision.
         */
        private final double ahdPrec;

        /**
         * Arrowhead recall.
         */
        private final double ahdRec;

        /**
         * Structural Hamming distance.
         */
        private final int shd;

        /**
         * Edges added.
         */
        private final List<Edge> edgesAdded;

        /**
         * Edges removed.
         */
        private final List<Edge> edgesRemoved;

        /**
         * Constructs a new GraphComparison.
         *
         * @param adjFn        a int
         * @param adjFp        a int
         * @param adjCorrect   a int
         * @param ahdFn        a int
         * @param ahdFp        a int
         * @param ahdCorrect   a int
         * @param adjPrec      a double
         * @param adjRec       a double
         * @param ahdPrec      a double
         * @param ahdRec       a double
         * @param shd          a int
         * @param edgesAdded   a {@link java.util.List} object
         * @param edgesRemoved a {@link java.util.List} object
         * @param counts       a int[][]
         */
        public GraphComparison(int adjFn, int adjFp, int adjCorrect, int ahdFn, int ahdFp,
                               int ahdCorrect, double adjPrec, double adjRec, double ahdPrec,
                               double ahdRec, int shd, List<Edge> edgesAdded, List<Edge> edgesRemoved,
                               int[][] counts) {
            this.adjFn = adjFn;
            this.adjFp = adjFp;
            this.adjCorrect = adjCorrect;
            this.ahdFn = ahdFn;
            this.ahdFp = ahdFp;
            this.ahdCorrect = ahdCorrect;

            this.adjPrec = adjPrec;
            this.adjRec = adjRec;
            this.ahdPrec = ahdPrec;
            this.ahdRec = ahdRec;

            this.shd = shd;
            this.edgesAdded = edgesAdded;
            this.edgesRemoved = edgesRemoved;

            this.counts = counts;
        }

        /**
         * Returns the adjacency false negatives.
         *
         * @return the adjacency false negatives.
         */
        public int getAdjFn() {
            return this.adjFn;
        }

        /**
         * Returns the adjacency false positives.
         *
         * @return the adjacency false positives.
         */
        public int getAdjFp() {
            return this.adjFp;
        }

        /**
         * Returns the adjacency correct.
         *
         * @return the adjacency correct.
         */
        public int getAdjCor() {
            return this.adjCorrect;
        }

        /**
         * Returns the arrowhead false negatives.
         *
         * @return the arrowhead false negatives.
         */
        public int getAhdFn() {
            return this.ahdFn;
        }

        /**
         * Returns the arrowhead false positives.
         *
         * @return the arrowhead false positives.
         */
        public int getAhdFp() {
            return this.ahdFp;
        }

        /**
         * Returns the arrowhead correct.
         *
         * @return the arrowhead correct.
         */
        public int getAhdCor() {
            return this.ahdCorrect;
        }

        /**
         * Returns the adjacency precision.
         *
         * @return the adjacency precision.
         */
        public int getShd() {
            return this.shd;
        }

        /**
         * Returns the edges added.
         *
         * @return the edges added.
         */
        public List<Edge> getEdgesAdded() {
            return this.edgesAdded;
        }

        /**
         * Returns the edges removed.
         *
         * @return the edges removed.
         */
        public List<Edge> getEdgesRemoved() {
            return this.edgesRemoved;
        }

        /**
         * Returns the adjaency precision.
         *
         * @return the adjacency precision.
         */
        public double getAdjPrec() {
            return this.adjPrec;
        }

        /**
         * Returns the adjacency recall.
         *
         * @return the adjacency recall.
         */
        public double getAdjRec() {
            return this.adjRec;
        }

        /**
         * Returns the arrowhead precision.
         *
         * @return the arrowhead precision.
         */
        public double getAhdPrec() {
            return this.ahdPrec;
        }

        /**
         * Returns the arrowhead recall.
         *
         * @return the arrowhead recall.
         */
        public double getAhdRec() {
            return this.ahdRec;
        }

        /**
         * Returns the counts.
         *
         * @return the counts.
         */
        public int[][] getCounts() {
            return this.counts;
        }
    }

    /**
     * Two-cycle errors.
     */
    public static class TwoCycleErrors {

        /**
         * The number of correct edges.
         */
        public int twoCycCor;

        /**
         * The number of false negatives.
         */
        public int twoCycFn;

        /**
         * The number of false positives.
         */
        public int twoCycFp;

        /**
         * Constructs a new TwoCycleErrors.
         *
         * @param twoCycCor the number of correct edges.
         * @param twoCycFn  the number of false negatives.
         * @param twoCycFp  the number of false positives.
         */
        public TwoCycleErrors(int twoCycCor, int twoCycFn, int twoCycFp) {
            this.twoCycCor = twoCycCor;
            this.twoCycFn = twoCycFn;
            this.twoCycFp = twoCycFp;
        }

        /**
         * Returns a string representation of this object.
         *
         * @return a string representation of this object.
         */
        public String toString() {
            return "2c cor = " + this.twoCycCor + "\t" + "2c fn = " + this.twoCycFn + "\t" + "2c fp = " + this.twoCycFp;
        }
    }
}
