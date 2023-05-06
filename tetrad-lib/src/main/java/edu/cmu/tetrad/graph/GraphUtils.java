///////////////////////////////////////////////////////////////////////////////
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
///////////////////////////////////////////////////////////////////////////////
package edu.cmu.tetrad.graph;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Edge.Property;
import edu.cmu.tetrad.search.FciOrient;
import edu.cmu.tetrad.search.utils.GraphUtilsSearch;
import edu.cmu.tetrad.search.utils.SepsetProducer;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.ForkJoinPoolInstance;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TextTable;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.RecursiveTask;

import static edu.cmu.tetrad.search.utils.GraphUtilsSearch.dagToPag;

/**
 * Basic graph utilities.
 *
 * @author Joseph Ramsey
 */
public final class GraphUtils {

    /**
     * @return the node associated with a given error node. This should be the
     * only child of the error node, E --&gt; N.
     */
    public static Node getAssociatedNode(Node errorNode, Graph graph) {
        if (errorNode.getNodeType() != NodeType.ERROR) {
            throw new IllegalArgumentException("Can only get an associated node " + "for an error node: " + errorNode);
        }

        List<Node> children = graph.getChildren(errorNode);

        if (children.size() != 1) {
            System.out.println("children of " + errorNode + " = " + children);
            System.out.println(graph);

            throw new IllegalArgumentException("An error node should have only " + "one child, which is its associated node: " + errorNode);
        }

        return children.get(0);
    }

    /**
     * @return true if <code>set</code> is a clique in <code>graph</code>.
     * R. Silva, June 2004
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
     * Calculates the Markov blanket of a target in a DAG. This includes the
     * target, the parents of the target, the children of the target, the
     * parents of the children of the target, edges from parents to target,
     * target to children, parents of children to children, and parent to
     * parents of children. (Edges among children are implied by the inclusion
     * of edges from parents of children to children.) Edges among parents and
     * among parents of children not explicitly included above are not included.
     * (Joseph Ramsey 8/6/04)
     *
     * @param target a node in the given DAG.
     * @param dag    the DAG with respect to which a Markov blanket DAG is to be
     *               calculated. All the nodes and edges of the Markov Blanket DAG are in
     *               this DAG.
     */
    public static Graph markovBlanketDag(Node target, Graph dag) {
        NodeEqualityMode.setEqualityMode(NodeEqualityMode.Type.NAME);

        if (dag.getNode(target.getName()) == null) {
            throw new NullPointerException("Target node not in graph: " + target);
        }

        Graph blanket = new EdgeListGraph();
        blanket.addNode(target);

        // Add parents of target.
        List<Node> parents = dag.getParents(target);
        for (Node parent1 : parents) {
            blanket.addNode(parent1);
            blanket.addDirectedEdge(parent1, target);
        }

        // Add children of target and parents of children of target.
        List<Node> children = dag.getChildren(target);
        List<Node> parentsOfChildren = new LinkedList<>();
        for (Node child : children) {
            if (!blanket.containsNode(child)) {
                blanket.addNode(child);
            }

            blanket.addDirectedEdge(target, child);

            List<Node> parentsOfChild = dag.getParents(child);
            parentsOfChild.remove(target);
            for (Node aParentsOfChild : parentsOfChild) {
                if (!parentsOfChildren.contains(aParentsOfChild)) {
                    parentsOfChildren.add(aParentsOfChild);
                }

                if (!blanket.containsNode(aParentsOfChild)) {
                    blanket.addNode(aParentsOfChild);
                }

                blanket.addDirectedEdge(aParentsOfChild, child);
            }
        }

        // Add in edges connecting parents and parents of children.
        parentsOfChildren.removeAll(parents);

        for (Node parent2 : parents) {
            for (Node aParentsOfChildren : parentsOfChildren) {
                Edge edge1 = dag.getEdge(parent2, aParentsOfChildren);
                Edge edge2 = blanket.getEdge(parent2, aParentsOfChildren);

                if (edge1 != null && edge2 == null) {
                    Edge newEdge = new Edge(parent2, aParentsOfChildren, edge1.getProximalEndpoint(parent2), edge1.getProximalEndpoint(aParentsOfChildren));

                    blanket.addEdge(newEdge);
                }
            }
        }

        // Add in edges connecting children and parents of children.
        for (Node aChildren1 : children) {

            for (Node aParentsOfChildren : parentsOfChildren) {
                Edge edge1 = dag.getEdge(aChildren1, aParentsOfChildren);
                Edge edge2 = blanket.getEdge(aChildren1, aParentsOfChildren);

                if (edge1 != null && edge2 == null) {
                    Edge newEdge = new Edge(aChildren1, aParentsOfChildren, edge1.getProximalEndpoint(aChildren1), edge1.getProximalEndpoint(aParentsOfChildren));

                    blanket.addEdge(newEdge);
                }
            }
        }

        return blanket;
    }

    //all adjancencies are directed <=> there is no uncertainty about whom the parents of 'node' are.
    public static boolean allAdjacenciesAreDirected(Node node, Graph graph) {
        List<Edge> nodeEdges = graph.getEdges(node);
        for (Edge edge : nodeEdges) {
            if (!edge.isDirected()) {
                return false;
            }
        }
        return true;
    }

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

    public static Graph removeBidirectedEdges(Graph estCpdag) {
        estCpdag = new EdgeListGraph(estCpdag);

        // Remove bidirected edges altogether.
        for (Edge edge : new ArrayList<>(estCpdag.getEdges())) {
            if (Edges.isBidirectedEdge(edge)) {
                estCpdag.removeEdge(edge);
            }
        }

        return estCpdag;
    }

    public static Graph undirectedGraph(Graph graph) {
        Graph graph2 = new EdgeListGraph(graph.getNodes());

        for (Edge edge : graph.getEdges()) {
            if (!graph2.isAdjacentTo(edge.getNode1(), edge.getNode2())) {
                graph2.addUndirectedEdge(edge.getNode1(), edge.getNode2());
            }
        }

        return graph2;
    }

    public static Graph completeGraph(Graph graph) {
        Graph graph2 = new EdgeListGraph(graph.getNodes());

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
     * @return the edges that are in <code>graph1</code> but not in
     * <code>graph2</code>, as a list of undirected edges..
     */
    public static List<Edge> adjacenciesComplement(Graph graph1, Graph graph2) {
        List<Edge> edges = new ArrayList<>();

        for (Edge edge1 : graph1.getEdges()) {
            String name1 = edge1.getNode1().getName();
            String name2 = edge1.getNode2().getName();

            Node node21 = graph2.getNode(name1);
            Node node22 = graph2.getNode(name2);

            if (node21 == null || node22 == null || !graph2.isAdjacentTo(node21, node22)) {
                edges.add(Edges.nondirectedEdge(edge1.getNode1(), edge1.getNode2()));
            }
        }

        return edges;
    }

    /**
     * @return a new graph in which the bidirectred edges of the given graph
     * have been changed to undirected edges.
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
     * @return a new graph in which the undirectred edges of the given graph
     * have been changed to bidirected edges.
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

    public static String pathString(Graph graph, List<Node> path) {
        return GraphUtils.pathString(graph, path, new LinkedList<>());
    }

    public static String pathString(Graph graph, Node... x) {
        List<Node> path = new ArrayList<>();
        Collections.addAll(path, x);
        return GraphUtils.pathString(graph, path, new LinkedList<>());
    }

    private static String pathString(Graph graph, List<Node> path, List<Node> conditioningVars) {
        StringBuilder buf = new StringBuilder();

        if (path.size() < 2) {
            return "NO PATH";
        }

        if (path.get(0).getNodeType() == NodeType.LATENT) {
            buf.append("(").append(path.get(0).toString()).append(")");
        } else {
            buf.append(path.get(0).toString());
        }


        if (conditioningVars.contains(path.get(0))) {
            buf.append("(C)");
        }

        for (int m = 1; m < path.size(); m++) {
            Node n0 = path.get(m - 1);
            Node n1 = path.get(m);

            Edge edge = graph.getEdge(n0, n1);

            if (edge == null) {
                buf.append("(-)");
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
                buf.append("(C)");
            }
        }
        return buf.toString();
    }

    /**
     * Converts the given graph, <code>originalGraph</code>, to use the new
     * variables (with the same names as the old).
     *
     * @param originalGraph The graph to be converted.
     * @param newVariables  The new variables to use, with the same names as the
     *                      old ones.
     * @return A new, converted, graph.
     */
    public static Graph replaceNodes(Graph originalGraph, List<Node> newVariables) {
        Map<String, Node> newNodes = new HashMap<>();
        List<Node> _newNodes = new ArrayList<>();

        for (Node node : newVariables) {
            if (node.getNodeType() != NodeType.LATENT) {
                newNodes.put(node.getName(), node);
                _newNodes.add(node);
            }
        }

        Graph convertedGraph = new EdgeListGraph(_newNodes);

        for (Edge edge : originalGraph.getEdges()) {
            Node node1 = newNodes.get(edge.getNode1().getName());
            Node node2 = newNodes.get(edge.getNode2().getName());

            if (node1 == null) {
                node1 = edge.getNode1();
            }

            if (!convertedGraph.containsNode(node1)) {
                convertedGraph.addNode(node1);
            }

            if (node2 == null) {
                node2 = edge.getNode2();
            }

            if (!convertedGraph.containsNode(node2)) {
                convertedGraph.addNode(node2);
            }

            if (!convertedGraph.containsNode(node1)) {
                convertedGraph.addNode(node1);
            }

            if (!convertedGraph.containsNode(node2)) {
                convertedGraph.addNode(node2);
            }

            Endpoint endpoint1 = edge.getEndpoint1();
            Endpoint endpoint2 = edge.getEndpoint2();
            Edge newEdge = new Edge(node1, node2, endpoint1, endpoint2);
            convertedGraph.addEdge(newEdge);
        }

        for (Triple triple : originalGraph.underlines().getUnderLines()) {
            convertedGraph.underlines().addUnderlineTriple(convertedGraph.getNode(triple.getX().getName()), convertedGraph.getNode(triple.getY().getName()), convertedGraph.getNode(triple.getZ().getName()));
        }

        for (Triple triple : originalGraph.underlines().getDottedUnderlines()) {
            convertedGraph.underlines().addDottedUnderlineTriple(convertedGraph.getNode(triple.getX().getName()), convertedGraph.getNode(triple.getY().getName()), convertedGraph.getNode(triple.getZ().getName()));
        }

        for (Triple triple : originalGraph.underlines().getAmbiguousTriples()) {
            convertedGraph.underlines().addAmbiguousTriple(convertedGraph.getNode(triple.getX().getName()), convertedGraph.getNode(triple.getY().getName()), convertedGraph.getNode(triple.getZ().getName()));
        }

        return convertedGraph;
    }

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
     * Converts the given list of nodes, <code>originalNodes</code>, to use the
     * new variables (with the same names as the old).
     *
     * @param originalNodes The list of nodes to be converted.
     * @param newNodes      A list of new nodes, containing as a subset nodes with
     *                      the same names as those in <code>originalNodes</code>. the old ones.
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
     * @throws IllegalArgumentException if graph1 and graph2 are not namewise
     *                                  isomorphic.
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
     * Counts the arrowheads that are in graph1 but not in graph2.
     */
    public static int countArrowptErrors(Graph graph1, Graph graph2) {
        if (graph1 == null) {
            throw new NullPointerException("The reference graph is missing.");
        }

        if (graph2 == null) {
            throw new NullPointerException("The target graph is missing.");
        }

        graph2 = GraphUtils.replaceNodes(graph2, graph1.getNodes());

        int count = 0;

        for (Edge edge1 : graph1.getEdges()) {
            Node node1 = edge1.getNode1();
            Node node2 = edge1.getNode2();

            Edge edge2 = graph2.getEdge(node1, node2);

            if (edge1.getEndpoint1() == Endpoint.ARROW) {
                if (edge2 == null) {
                    count++;
                } else if (edge2.getProximalEndpoint(edge1.getNode1()) != Endpoint.ARROW) {
                    count++;
                }
            }

            if (edge1.getEndpoint2() == Endpoint.ARROW) {
                if (edge2 == null) {
                    count++;
                } else if (edge2.getProximalEndpoint(edge1.getNode2()) != Endpoint.ARROW) {
                    count++;
                }
            }
        }

        for (Edge edge1 : graph2.getEdges()) {
            Node node1 = edge1.getNode1();
            Node node2 = edge1.getNode2();

            Edge edge2 = graph1.getEdge(node1, node2);

            if (edge1.getEndpoint1() == Endpoint.ARROW) {
                if (edge2 == null) {
                    count++;
                } else if (edge2.getProximalEndpoint(edge1.getNode1()) != Endpoint.ARROW) {
                    count++;
                }
            }

            if (edge1.getEndpoint2() == Endpoint.ARROW) {
                if (edge2 == null) {
                    count++;
                } else if (edge2.getProximalEndpoint(edge1.getNode2()) != Endpoint.ARROW) {
                    count++;
                }
            }
        }

        return count;
    }

    public static int getNumCorrectArrowpts(Graph correct, Graph estimated) {
        correct = GraphUtils.replaceNodes(correct, estimated.getNodes());

        Set<Edge> edges = estimated.getEdges();
        int numCorrect = 0;

        for (Edge estEdge : edges) {
            Edge correctEdge = correct.getEdge(estEdge.getNode1(), estEdge.getNode2());
            if (correctEdge == null) {
                continue;
            }

            if (estEdge.getProximalEndpoint(estEdge.getNode1()) == Endpoint.ARROW && correctEdge.getProximalEndpoint(estEdge.getNode1()) == Endpoint.ARROW) {
                numCorrect++;
            }

            if (estEdge.getProximalEndpoint(estEdge.getNode2()) == Endpoint.ARROW && correctEdge.getProximalEndpoint(estEdge.getNode2()) == Endpoint.ARROW) {
                numCorrect++;
            }
        }

        return numCorrect;
    }

    /**
     * Converts the given list of nodes, <code>originalNodes</code>, to use the
     * replacement nodes for them by the same name in the given
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
     * @return an empty graph with the given number of nodes.
     */
    public static Graph emptyGraph(int numNodes) {
        List<Node> nodes = new ArrayList<>();

        for (int i = 0; i < numNodes; i++) {
            nodes.add(new GraphNode("X" + i));
        }

        return new EdgeListGraph(nodes);
    }


    private static Node getNode(List<Node> nodes, String x) {
        for (Node node : nodes) {
            if (x.equals(node.getName())) {
                return node;
            }
        }

        return null;
    }


    /**
     * @return A list of triples of the form X, Y, Z, where X, Y, Z is a
     * definite noncollider in the given graph.
     */
    public static List<Triple> getNoncollidersFromGraph(Node node, Graph graph) {
        List<Triple> noncolliders = new ArrayList<>();

        List<Node> adj = graph.getAdjacentNodes(node);
        if (adj.size() < 2) {
            return new LinkedList<>();
        }

        ChoiceGenerator gen = new ChoiceGenerator(adj.size(), 2);
        int[] choice;

        while ((choice = gen.next()) != null) {
            Node x = adj.get(choice[0]);
            Node z = adj.get(choice[1]);

            Endpoint endpt1 = graph.getEdge(x, node).getProximalEndpoint(node);
            Endpoint endpt2 = graph.getEdge(z, node).getProximalEndpoint(node);

            if (endpt1 == Endpoint.ARROW && endpt2 == Endpoint.TAIL || endpt1 == Endpoint.TAIL && endpt2 == Endpoint.ARROW || endpt1 == Endpoint.TAIL && endpt2 == Endpoint.TAIL) {
                noncolliders.add(new Triple(x, node, z));
            }
        }

        return noncolliders;
    }

    /**
     * @return A list of triples of the form &lt;X, Y, Z&gt;, where &lt;X, Y, Z&gt; is a
     * definite noncollider in the given graph.
     */
    public static List<Triple> getAmbiguousTriplesFromGraph(Node node, Graph graph) {
        List<Triple> ambiguousTriples = new ArrayList<>();

        List<Node> adj = graph.getAdjacentNodes(node);
        if (adj.size() < 2) {
            return new LinkedList<>();
        }

        ChoiceGenerator gen = new ChoiceGenerator(adj.size(), 2);
        int[] choice;

        while ((choice = gen.next()) != null) {
            Node x = adj.get(choice[0]);
            Node z = adj.get(choice[1]);

            if (graph.underlines().isAmbiguousTriple(x, node, z)) {
                ambiguousTriples.add(new Triple(x, node, z));
            }
        }

        return ambiguousTriples;
    }

    /**
     * @return A list of triples of the form &lt;X, Y, Z&gt;, where &lt;X, Y, Z&gt; is a
     * definite noncollider in the given graph.
     */
    public static List<Triple> getUnderlinedTriplesFromGraph(Node node, Graph graph) {
        List<Triple> underlinedTriples = new ArrayList<>();
        Set<Triple> allUnderlinedTriples = graph.underlines().getUnderLines();

        List<Node> adj = graph.getAdjacentNodes(node);
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
     * @return A list of triples of the form &lt;X, Y, Z&gt;, where &lt;X, Y, Z&gt; is a
     * definite noncollider in the given graph.
     */
    public static List<Triple> getDottedUnderlinedTriplesFromGraph(Node node, Graph graph) {
        List<Triple> dottedUnderlinedTriples = new ArrayList<>();
        Set<Triple> allDottedUnderlinedTriples = graph.underlines().getDottedUnderlines();

        List<Node> adj = graph.getAdjacentNodes(node);
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


    public static LinkedList<Triple> listColliderTriples(Graph graph) {
        LinkedList<Triple> colliders = new LinkedList<>();

        for (Node node : graph.getNodes()) {
            List<Node> adj = graph.getAdjacentNodes(node);

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
     * Constructs a list of nodes from the given <code>nodes</code> list at the
     * given indices in that list.
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

    public static Set<Node> asSet(int[] indices, List<Node> nodes) {
        Set<Node> set = new HashSet<>();

        for (int i : indices) {
            set.add(nodes.get(i));
        }

        return set;
    }

    public static int numDirectionalErrors(Graph result, Graph cpdag) {
        int count = 0;

        for (Edge edge : result.getEdges()) {
            Node node1 = edge.getNode1();
            Node node2 = edge.getNode2();

            Node _node1 = cpdag.getNode(node1.getName());
            Node _node2 = cpdag.getNode(node2.getName());

            Edge _edge = cpdag.getEdge(_node1, _node2);

            if (_edge == null) {
                continue;
            }

            if (Edges.isDirectedEdge(edge)) {
                if (_edge.pointsTowards(_node1)) {
                    count++;
                } else if (Edges.isUndirectedEdge(_edge)) {
                    count++;
                }
            }
        }

        return count;
    }

    public static int numBidirected(Graph result) {
        int numBidirected = 0;

        for (Edge edge : result.getEdges()) {
            if (Edges.isBidirectedEdge(edge)) {
                numBidirected++;
            }
        }

        return numBidirected;
    }

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

    public static String getIntersectionComparisonString(List<Graph> graphs) {
        if (graphs == null || graphs.isEmpty()) {
            return "";
        }

        StringBuilder b = GraphUtils.undirectedEdges(graphs);

        b.append(GraphUtils.directedEdges(graphs));

        return b.toString();
    }

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
            b.append("\n").append(index++).append(". ").append(Edges.undirectedEdge(edge.getNode1(), edge.getNode2())).append(" (--> ").append(directionCounts.get(edge)).append(" &lt;-- ").append(directionCounts.get(edge.reverse())).append(")");
        }

        return b;
    }

    private static boolean uncontradicted(Edge edge1, Edge edge2) {
        if (edge1 == null || edge2 == null) {
            return true;
        }

        Node x = edge1.getNode1();
        Node y = edge1.getNode2();

        if (edge1.pointsTowards(x) && edge2.pointsTowards(y)) {
            return false;
        } else return !edge1.pointsTowards(y) || !edge2.pointsTowards(x);
    }

    public static String edgeMisclassifications(double[][] counts, NumberFormat nf) {
        StringBuilder builder = new StringBuilder();

        TextTable table2 = new TextTable(9, 7);

        table2.setToken(1, 0, "---");
        table2.setToken(2, 0, "o-o");
        table2.setToken(3, 0, "o->");
        table2.setToken(4, 0, "&lt;-o");
        table2.setToken(5, 0, "-->");
        table2.setToken(6, 0, "&lt;--");
        table2.setToken(7, 0, "&lt;->");
        table2.setToken(8, 0, "No Edge");
        table2.setToken(0, 1, "---");
        table2.setToken(0, 2, "o-o");
        table2.setToken(0, 3, "o->");
        table2.setToken(0, 4, "-->");
        table2.setToken(0, 5, "&lt;->");
        table2.setToken(0, 6, "No Edge");

        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 6; j++) {
                if (i == 7 && j == 5) {
                    table2.setToken(7 + 1, 5 + 1, "*");
                } else {
                    table2.setToken(i + 1, j + 1, "" + nf.format(counts[i][j]));
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

    public static void addPagColoring(Graph graph) {
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

            if (!new Paths(graph).existsSemiDirectedPath(x, y)) {
                edge.addProperty(Property.dd); // green.
            } else {
                edge.addProperty(Property.pd); // blue
            }

            graph.addEdge(xyEdge);

            if (graph.paths().defVisible(edge)) {
                edge.addProperty(Property.nl); // solid.
            } else {
                edge.addProperty(Property.pl); // dashed
            }
        }
    }


    public static int[][] edgeMisclassificationCounts(Graph leftGraph, Graph topGraph, boolean print) {
//        topGraph = GraphUtils.replaceNodes(topGraph, leftGraph.getNodes());

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
                        int j = ++this.count[0];

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

            public Counts getCounts() {
                return this.counts;
            }
        }

        Set<Edge> edgeSet = new HashSet<>();
        edgeSet.addAll(topGraph.getEdges());
        edgeSet.addAll(leftGraph.getEdges());

//        System.out.println("Union formed");
        if (print) {
            System.out.println("Top graph " + topGraph.getEdges().size());
            System.out.println("Left graph " + leftGraph.getEdges().size());
            System.out.println("All edges " + edgeSet.size());
        }

        List<Edge> edges = new ArrayList<>(edgeSet);

//        System.out.println("Finding pool");
        ForkJoinPoolInstance pool = ForkJoinPoolInstance.getInstance();

//        System.out.println("Starting count task");
        CountTask task = new CountTask(500, 0, edges.size(), edges, leftGraph, topGraph, new int[1]);
        Counts counts = pool.getPool().invoke(task);

//        System.out.println("Finishing count task");
        return counts.countArray();
    }

    private static Set<Edge> complement(Set<Edge> edgeSet, Graph topGraph) {
        Set<Edge> complement = new HashSet<>(edgeSet);
        complement.removeAll(topGraph.getEdges());
        return complement;
    }

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

        return 5;

//        throw new IllegalArgumentException("Unsupported edge type : " + edgeTop);
    }

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

        throw new IllegalArgumentException("Unsupported edge type : " + edgeLeft);
    }

    private static int getTypeLeft2(Edge edgeLeft) {
        if (edgeLeft == null) {
            return 7;
        }

        if (Edges.isUndirectedEdge(edgeLeft)) {
            return 0;
        }

        if (Edges.isNondirectedEdge(edgeLeft)) {
            return 1;
        }

        if (Edges.isPartiallyOrientedEdge(edgeLeft)) {
            return 2;
        }

        if (Edges.isDirectedEdge(edgeLeft)) {
            return 4;
        }

        if (Edges.isBidirectedEdge(edgeLeft)) {
            return 6;
        }

        throw new IllegalArgumentException("Unsupported edge type : " + edgeLeft);
    }

    public static Set<Set<Node>> maximalCliques(Graph graph, List<Node> nodes) {
        Set<Set<Node>> report = new HashSet<>();
        GraphUtils.brokKerbosh1(new HashSet<>(), new HashSet<>(nodes), new HashSet<>(), report, graph);
        return report;
    }

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

    public static String graphToText(Graph graph, boolean doPagColoring) {
        if (doPagColoring) {
            GraphUtils.addPagColoring(graph);
        }

        Formatter fmt = new Formatter();
        fmt.format("%s%n%n", GraphUtils.graphNodesToText(graph, "Graph Nodes:", ';'));
        fmt.format("%s%n", GraphUtils.graphEdgesToText(graph, "Graph Edges:"));

        // Graph Attributes
        String graphAttributes = GraphUtils.graphAttributesToText(graph, "Graph Attributes:");
        if (graphAttributes != null) {
            fmt.format("%s%n", graphAttributes);
        }

        // Nodes Attributes
        String graphNodeAttributes = GraphUtils.graphNodeAttributesToText(graph, "Graph Node Attributes:", ';');
        if (graphNodeAttributes != null) {
            fmt.format("%s%n", graphNodeAttributes);
        }

        Set<Triple> ambiguousTriples = graph.underlines().getAmbiguousTriples();
        if (!ambiguousTriples.isEmpty()) {
            fmt.format("%n%n%s", GraphUtils.triplesToText(ambiguousTriples, "Ambiguous triples (i.e. list of triples for which there is ambiguous data about whether they are colliders or not):"));
        }

        Set<Triple> underLineTriples = graph.underlines().getUnderLines();
        if (!underLineTriples.isEmpty()) {
            fmt.format("%n%n%s", GraphUtils.triplesToText(underLineTriples, "Underline triples:"));
        }

        Set<Triple> dottedUnderLineTriples = graph.underlines().getDottedUnderlines();
        if (!dottedUnderLineTriples.isEmpty()) {
            fmt.format("%n%n%s", GraphUtils.triplesToText(dottedUnderLineTriples, "Dotted underline triples:"));
        }

        return fmt.toString();
    }

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
            StringBuilder sb = (title == null || title.length() == 0) ? new StringBuilder() : new StringBuilder(String.format("%s", title));

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

    public static String graphAttributesToText(Graph graph, String title) {
        Map<String, Object> attributes = graph.getAllAttributes();
        if (!attributes.isEmpty()) {
            StringBuilder sb = (title == null || title.length() == 0) ? new StringBuilder() : new StringBuilder(String.format("%s%n", title));

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

    public static String graphNodesToText(Graph graph, String title, char delimiter) {
        StringBuilder sb = (title == null || title.length() == 0) ? new StringBuilder() : new StringBuilder(String.format("%s%n", title));

        List<Node> nodes = graph.getNodes();
        int size = nodes.size();
        int count = 0;
        for (Node node : nodes) {
            count++;

            if (node.getNodeType() == NodeType.LATENT) {
                sb.append("(").append(node.getName()).append(")");
            } else {
                sb.append(node.getName());
            }

            if (count < size) {
                sb.append(delimiter);
            }
        }

        return sb.toString();
    }

    public static String graphEdgesToText(Graph graph, String title) {
        Formatter fmt = new Formatter();

        if (title != null && title.length() > 0) {
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

    public static String triplesToText(Set<Triple> triples, String title) {
        Formatter fmt = new Formatter();

        if (title != null && title.length() > 0) {
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

    private static Set<Triple> colliders(Node b, Graph graph, Set<Triple> colliders) {
        Set<Triple> _colliders = new HashSet<>();

        for (Triple collider : colliders) {
            if (graph.paths().isAncestorOf(collider.getY(), b)) {
                _colliders.add(collider);
            }
        }

        return _colliders;
    }


    public static int getDegree(Graph graph) {
        int max = 0;

        for (Node node : graph.getNodes()) {
            if (graph.getAdjacentNodes(node).size() > max) {
                max = graph.getAdjacentNodes(node).size();
            }
        }

        return max;
    }

    public static int getIndegree(Graph graph) {
        int max = 0;

        for (Node node : graph.getNodes()) {
            if (graph.getAdjacentNodes(node).size() > max) {
                max = graph.getIndegree(node);
            }
        }

        return max;
    }

    // Used to find semidirected paths for cycle checking.
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

    public static Graph getComparisonGraph(Graph graph, Parameters params) {
        String type = params.getString("graphComparisonType");

        if ("DAG".equals(type)) {
            params.set("graphComparisonType", "DAG");
            return new EdgeListGraph(graph);
        } else if ("CPDAG".equals(type)) {
            params.set("graphComparisonType", "CPDAG");
            return GraphUtilsSearch.cpdagForDag(graph);
        } else if ("PAG".equals(type)) {
            params.set("graphComparisonType", "PAG");
            return dagToPag(graph);
        } else {
            params.set("graphComparisonType", "DAG");
            return new EdgeListGraph(graph);
        }
    }

    /**
     * The extra edge removal step for GFCI. This removed edges in triangles in the reference graph by looking
     * for sepsets for edge a--b among the adjacents of a or the adjacents of b.
     *
     * @param graph          The graph being operated on and changed.
     * @param referenceCpdag The reference graph, a CPDAG or a DAG obtained using such an algorithm.
     * @param nodes          The nodes in the graph.
     * @param sepsets        A SepsetProducer that will do the sepset search operation described.
     */
    public static void gfciExtraEdgeRemovalStep(Graph graph, Graph referenceCpdag, List<Node> nodes,
                                                SepsetProducer sepsets) {
        for (Node b : nodes) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            List<Node> adjacentNodes = referenceCpdag.getAdjacentNodes(b);

            if (adjacentNodes.size() < 2) {
                continue;
            }

            ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                Node a = adjacentNodes.get(combination[0]);
                Node c = adjacentNodes.get(combination[1]);

                if (graph.isAdjacentTo(a, c) && referenceCpdag.isAdjacentTo(a, c)) {
                    List<Node> sepset = sepsets.getSepset(a, c);
                    if (sepset != null) {
                        graph.removeEdge(a, c);
                    }
                }
            }
        }
    }

    /**
     * Retains only the unshielded colliders of the given graph.
     *
     * @param graph The graph to retain unshielded colliders in.
     */
    public static void retainUnshieldedColliders(Graph graph, Knowledge knowledge) {
        Graph orig = new EdgeListGraph(graph);
        graph.reorientAllWith(Endpoint.CIRCLE);
        List<Node> nodes = graph.getNodes();

        for (Node b : nodes) {
            List<Node> adjacentNodes = graph.getAdjacentNodes(b);

            if (adjacentNodes.size() < 2) {
                continue;
            }

            ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                Node a = adjacentNodes.get(combination[0]);
                Node c = adjacentNodes.get(combination[1]);

                if (orig.isDefCollider(a, b, c) && !orig.isAdjacentTo(a, c)) {
                    if (FciOrient.isArrowheadAllowed(a, b, graph, knowledge)
                            && FciOrient.isArrowheadAllowed(c, b, graph, knowledge)) {
                        graph.setEndpoint(a, b, Endpoint.ARROW);
                        graph.setEndpoint(c, b, Endpoint.ARROW);
                    }
                }
            }
        }
    }

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

    public static Set<Node> pagMb(Node x, Graph G) {
        Set<Node> mb = new HashSet<>();

        LinkedList<Node> path = new LinkedList<>();
        path.add(x);
        mb.add(x);

        for (Node d : G.getAdjacentNodes(x)) {
            pagMbVisit(d, path, G, mb);
        }

        mb.remove(x);

        return mb;
    }

    private static void pagMbVisit(Node c, LinkedList<Node> path, Graph G, Set<Node> mb) {
        if (path.contains(c)) return;
        if (mb.contains(c)) return;
        path.add(c);

        if (path.size() >= 3) {
            Node w1 = path.get(path.size() - 3);
            Node w2 = path.get(path.size() - 2);

            if (!G.isDefCollider(w1, w2, c)) {
                path.remove(c);
                return;
            }
        }

        mb.add(c);

        for (Node d : G.getAdjacentNodes(c)) {
            pagMbVisit(d, path, G, mb);
        }

        path.remove(c);
    }

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
     * Converts a string spec of a graph--for example, "X1--&gt;X2, X1---X3,
     * X2o-&gt;X4, X3&lt;-&gt;X4" to a Graph. The spec consists of a comma separated list
     * of edge specs of the forms just used in the previous sentence.
     * Unconnected nodes may be listed separately--example: "X,Y-&gt;Z". To specify
     * a node as latent, use "Latent()." Example: "Latent(L1),Y-&gt;L1".
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
                String latentName =
                        (String) var1.subSequence(7, var1.length() - 1);
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
                throw new IllegalArgumentException(
                        "Multiple edges connecting " +
                                "nodes is not supported.");
            }

            if (edgeSpec.lastIndexOf("-->") != -1) {
                graph.addDirectedEdge(nodeA, nodeB);
            }

            if (edgeSpec.lastIndexOf("<--") != -1) {
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
            }
        }

        return graph;
    }


    private static class Counts {

        private final int[][] counts;

        public Counts() {
            this.counts = new int[8][6];
        }

        public void increment(int m, int n) {
            this.counts[m][n]++;
        }

        public int getCount(int m, int n) {
            return this.counts[m][n];
        }

        public void addAll(Counts counts2) {
            for (int i = 0; i < 8; i++) {
                for (int j = 0; j < 6; j++) {
                    this.counts[i][j] += counts2.getCount(i, j);
                }
            }
        }

        public int[][] countArray() {
            return this.counts;
        }
    }

    public static class GraphComparison {

        private final int[][] counts;
        private final int adjFn;
        private final int adjFp;
        private final int adjCorrect;
        private final int arrowptFn;
        private final int arrowptFp;
        private final int arrowptCorrect;

        private final double adjPrec;
        private final double adjRec;
        private final double arrowptPrec;
        private final double arrowptRec;

        private final int shd;
        private final int twoCycleFn;
        private final int twoCycleFp;
        private final int twoCycleCorrect;

        private final List<Edge> edgesAdded;
        private final List<Edge> edgesRemoved;
        private final List<Edge> edgesReorientedFrom;
        private final List<Edge> edgesReorientedTo;
        private final List<Edge> edgesAdjacencies;

        public GraphComparison(int adjFn, int adjFp, int adjCorrect, int arrowptFn, int arrowptFp,
                               int arrowptCorrect, double adjPrec, double adjRec, double arrowptPrec,
                               double arrowptRec, int shd, int twoCycleCorrect, int twoCycleFn,
                               int twoCycleFp, List<Edge> edgesAdded, List<Edge> edgesRemoved,
                               List<Edge> edgesReorientedFrom, List<Edge> edgesReorientedTo,
                               List<Edge> edgesAdjacencies, int[][] counts) {
            this.adjFn = adjFn;
            this.adjFp = adjFp;
            this.adjCorrect = adjCorrect;
            this.arrowptFn = arrowptFn;
            this.arrowptFp = arrowptFp;
            this.arrowptCorrect = arrowptCorrect;

            this.adjPrec = adjPrec;
            this.adjRec = adjRec;
            this.arrowptPrec = arrowptPrec;
            this.arrowptRec = arrowptRec;

            this.shd = shd;
            this.twoCycleCorrect = twoCycleCorrect;
            this.twoCycleFn = twoCycleFn;
            this.twoCycleFp = twoCycleFp;
            this.edgesAdded = edgesAdded;
            this.edgesRemoved = edgesRemoved;
            this.edgesReorientedFrom = edgesReorientedFrom;
            this.edgesReorientedTo = edgesReorientedTo;
            this.edgesAdjacencies = edgesAdjacencies;

            this.counts = counts;
        }

        public int getAdjFn() {
            return this.adjFn;
        }

        public int getAdjFp() {
            return this.adjFp;
        }

        public int getAdjCor() {
            return this.adjCorrect;
        }

        public int getAhdFn() {
            return this.arrowptFn;
        }

        public int getAhdFp() {
            return this.arrowptFp;
        }

        public int getAhdCor() {
            return this.arrowptCorrect;
        }

        public int getShd() {
            return this.shd;
        }

        public int getTwoCycleFn() {
            return this.twoCycleFn;
        }

        public int getTwoCycleFp() {
            return this.twoCycleFp;
        }

        public int getTwoCycleCorrect() {
            return this.twoCycleCorrect;
        }

        public List<Edge> getEdgesAdded() {
            return this.edgesAdded;
        }

        public List<Edge> getEdgesRemoved() {
            return this.edgesRemoved;
        }

        public double getAdjPrec() {
            return this.adjPrec;
        }

        public double getAdjRec() {
            return this.adjRec;
        }

        public double getAhdPrec() {
            return this.arrowptPrec;
        }

        public double getAhdRec() {
            return this.arrowptRec;
        }

        public int[][] getCounts() {
            return this.counts;
        }
    }

    public static class TwoCycleErrors {

        public int twoCycCor;
        public int twoCycFn;
        public int twoCycFp;

        public TwoCycleErrors(int twoCycCor, int twoCycFn, int twoCycFp) {
            this.twoCycCor = twoCycCor;
            this.twoCycFn = twoCycFn;
            this.twoCycFp = twoCycFp;
        }

        public String toString() {
            return "2c cor = " + this.twoCycCor + "\t" + "2c fn = " + this.twoCycFn + "\t" + "2c fp = " + this.twoCycFp;
        }

    }

}
