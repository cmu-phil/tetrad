///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
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

import edu.cmu.tetrad.util.TetradSerializable;

import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.*;

/**
 * </p> Represents the graphical structure of a structural equation model. The
 * linear structure of the structural equation model is constructed by adding
 * non-error nodes to the graph and connecting them with directed edges. As this
 * is done, the graph automatically maintains the invariant that endogenous
 * non-error nodes are associated with explicit error nodes in the graph and
 * exogenous non-error nodes are not. An associated error node for a node N is
 * an error node that has N as its only child, E-->N. Error nodes for exogenous
 * nodes are always implicit in the graph. So as nodes become endogenous, error
 * nodes are added for them, and as they become exogenous, error nodes are
 * removed for them. Correlated errors are represented using bidirected edges
 * among exogenous nodes. Bidirected edges may therefore be added among any
 * exogenous nodes in the graph, though the easiest way to add (or remove)
 * exogenous nodes is determine which non-exogenous nodes N1, N2 they are
 * representing correlated errors for and then to use this formulation:</p>
 * <pre>
 *     addBidirectedEdge(getExogenous(node1), getExogenous(node2));
 *     removeEdge(getExogenous(node1), getExogenous(node2));
 * </pre>
 * </p> This avoids the problem of not knowing whether the exogenous node for a
 * node is itself or its associated error node.</p>
 *
 * @author Joseph Ramsey
 */
public final class SemGraph implements Graph, TetradSerializable {
    static final long serialVersionUID = 23L;

    /**
     * The underlying graph that stores all the information. This needs to be an
     * EdgeListGraph or something at least that can allow nodes to quickly be
     * added or removed.
     *
     * @serial
     */
    private final Graph graph;

    /**
     * A map from nodes to their error nodes, if they exist. This includes
     * variables nodes to their error nodes and error nodes to themselves. Added
     * because looking them up in the graph was not scalable.
     *
     * @serial
     */
    private Map<Node, Node> errorNodes = new HashMap<>();

    /**
     * True if error terms for exogenous terms should be shown.
     *
     * @serial
     */
    private boolean showErrorTerms = false;

    private boolean pag;
    private boolean cpdag;

    private final Map<String, Object> attributes = new HashMap<>();

    //=========================CONSTRUCTORS============================//

    /**
     * Constructs a new, empty SemGraph.
     */
    public SemGraph() {
        this.graph = new EdgeListGraph();
    }

    /**
     * Constructs a new SemGraph from the nodes and edges of the given graph.
     */
    public SemGraph(final Graph graph) {
        if (graph instanceof SemGraph) {
            if (graph.isTimeLagModel()) {
                this.graph = new TimeLagGraph((TimeLagGraph) ((SemGraph) graph).graph);
            } else {
                this.graph = new EdgeListGraph(graph);
            }

            this.errorNodes =
                    new HashMap<>(((SemGraph) graph).errorNodes);

            for (final Node node : graph.getNodes()) {
                if (!this.errorNodes.containsKey(node)) {
                    addErrorNode(node);
                }
            }

            this.showErrorTerms = ((SemGraph) graph).showErrorTerms;
            setShowErrorTerms(this.showErrorTerms);
        } else if (graph instanceof TimeLagGraph) {
            this.graph = new TimeLagGraph((TimeLagGraph) graph);
        } else {
            this.graph = new EdgeListGraph(graph.getNodes());

            for (final Node node : this.graph.getNodes()) {
                addErrorNode(node);
            }

            setShowErrorTerms(true);

            for (final Edge edge : graph.getEdges()) {
                if (Edges.isDirectedEdge(edge)) {
                    addEdge(edge);
                } else if (Edges.isBidirectedEdge(edge)) {
                    final Node node1 = edge.getNode1();
                    final Node node2 = edge.getNode2();

                    addBidirectedEdge(getExogenous(node1), getExogenous(node2));
                } else {
                    throw new IllegalArgumentException("A SEM graph may contain " +
                            "only directed and bidirected edges: " + edge);
                }
            }

            setShowErrorTerms(false);
        }

        for (final Edge edge : graph.getEdges()) {
            if (graph.isHighlighted(edge)) {
                setHighlighted(edge, true);
            }
        }
    }

    /**
     * Copy constructor.
     */
    public SemGraph(final SemGraph graph) {
        if (graph.isTimeLagModel()) {
            this.graph = new TimeLagGraph((TimeLagGraph) graph.graph);
        } else {
            this.graph = new EdgeListGraph(graph.getGraph());
        }

        this.errorNodes = new HashMap<>(graph.errorNodes);

        if (graph.showErrorTerms) {
            for (final Node node : this.graph.getNodes()) {
                if (!errorNodes().containsKey(node)) {
                    addErrorNode(node);
                }
            }
        }

        this.showErrorTerms = graph.showErrorTerms;
        setShowErrorTerms(this.showErrorTerms);

        for (final Edge edge : graph.getEdges()) {
            if (graph.isHighlighted(edge)) {
                setHighlighted(edge, true);
            }
        }
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static SemGraph serializableInstance() {
        return new SemGraph();
    }

    //============================PUBLIC METHODS==========================//

    /**
     * @return the error node associated with the given node, or null if the
     * node has no associated error node.
     */
    public Node getErrorNode(final Node node) {
        return this.errorNodes.get(node);
    }

    /**
     * This method returns the nodes of a digraph ordered in such a way that the
     * parents of any node are contained lower down in the list.
     *
     * @return a tier ordering for the nodes in this graph.
     * @throws IllegalStateException if the graph is cyclic.
     */
    public List<Node> getCausalOrdering() {
        return GraphUtils.getCausalOrdering(this);
    }

    public void setHighlighted(final Edge edge, final boolean highlighted) {
        getGraph().setHighlighted(edge, highlighted);
    }

    public boolean isHighlighted(final Edge edge) {
        return getGraph().isHighlighted(edge);
    }

    public boolean isParameterizable(final Node node) {
        return getGraph().isParameterizable(node);
    }

    /**
     * @return a tier order, including error terms, if they are shown.
     * @throws IllegalStateException if the graph is cyclic.
     */
    public List<Node> getFullTierOrdering() {
        if (existsDirectedCycle()) {
            throw new IllegalStateException("The tier ordering method assumes acyclicity.");
        }

        final List<Node> found = new LinkedList<>();
        final Set<Node> notFound = new HashSet<>(getNodes());

//        for (Node node1 : getNodes()) {
//            notFound.add(node1);
//        }

//        List<Node> errorTerms = new LinkedList<Node>();
//
//        for (Node node : notFound) {
//            if (node.getNodeType() == NodeType.ERROR) {
//                errorTerms.add(node);
//            }
//        }
//
//        notFound.removeAll(errorTerms);

        while (!notFound.isEmpty()) {
            for (final Iterator<Node> it = notFound.iterator(); it.hasNext(); ) {
                final Node node = it.next();

                final List<Node> parents = getParents(node);
//                parents.removeAll(errorTerms);

                if (found.containsAll(parents)) {
                    found.add(node);
                    it.remove();
                }
            }
        }

        return found;
    }

    /**
     * @return true iff either node associated with edge is an error term.
     */
    public static boolean isErrorEdge(final Edge edge) {
        return (edge.getNode1().getNodeType() == NodeType.ERROR ||
                (edge.getNode2().getNodeType() == NodeType.ERROR));
    }

    /**
     * @return the variable node for this node--that is, the associated node, if
     * this is an error node, or the node itself, if it is not.
     */
    public Node getVarNode(final Node node) {
        final boolean isError = node.getNodeType() == NodeType.ERROR;

        if (!containsNode(node)) {
            throw new NullPointerException("Node is not in graph: " + node);
        }

        if (isError) {
            return GraphUtils.getAssociatedNode(node, this);
        } else {
            return node;
        }
    }

    /**
     * @param node the node you want the exogenous node for.
     * @return the exogenous node for that node.
     */
    public Node getExogenous(final Node node) {
        return isExogenous(node) ? node : this.errorNodes.get(node);
    }

    public final void transferNodesAndEdges(final Graph graph)
            throws IllegalArgumentException {
        throw new UnsupportedOperationException();
    }

    public final void transferAttributes(final Graph graph)
            throws IllegalArgumentException {
        throw new UnsupportedOperationException();
    }

    public Set<Triple> getAmbiguousTriples() {
        return getGraph().getAmbiguousTriples();
    }

    public Set<Triple> getUnderLines() {
        return getGraph().getUnderLines();
    }

    public Set<Triple> getDottedUnderlines() {
        return getGraph().getDottedUnderlines();
    }


    /**
     * States whether x-y-x is an underline triple or not.
     */
    public boolean isAmbiguousTriple(final Node x, final Node y, final Node z) {
        return getGraph().isAmbiguousTriple(x, y, z);
    }

    /**
     * States whether x-y-x is an underline triple or not.
     */
    public boolean isUnderlineTriple(final Node x, final Node y, final Node z) {
        return getGraph().isUnderlineTriple(x, y, z);
    }

    /**
     * States whether x-y-x is an underline triple or not.
     */
    public boolean isDottedUnderlineTriple(final Node x, final Node y, final Node z) {
        return getGraph().isDottedUnderlineTriple(x, y, z);
    }

    public void addAmbiguousTriple(final Node x, final Node y, final Node z) {
        getGraph().addAmbiguousTriple(x, y, z);
    }

    public void addUnderlineTriple(final Node x, final Node y, final Node z) {
        getGraph().addUnderlineTriple(x, y, z);
    }

    public void addDottedUnderlineTriple(final Node x, final Node y, final Node z) {
        getGraph().addDottedUnderlineTriple(x, y, z);
    }

    public void removeAmbiguousTriple(final Node x, final Node y, final Node z) {
        getGraph().removeAmbiguousTriple(x, y, z);
    }

    public void removeUnderlineTriple(final Node x, final Node y, final Node z) {
        getGraph().removeUnderlineTriple(x, y, z);
    }

    public void removeDottedUnderlineTriple(final Node x, final Node y, final Node z) {
        getGraph().removeDottedUnderlineTriple(x, y, z);
    }


    public void setAmbiguousTriples(final Set<Triple> triples) {
        getGraph().setAmbiguousTriples(triples);
    }

    public void setUnderLineTriples(final Set<Triple> triples) {
        getGraph().setUnderLineTriples(triples);
    }


    public void setDottedUnderLineTriples(final Set<Triple> triples) {
        getGraph().setDottedUnderLineTriples(triples);
    }

    public List<String> getNodeNames() {
        return getGraph().getNodeNames();
    }


    public void fullyConnect(final Endpoint endpoint) {
        throw new UnsupportedOperationException();
    }

    public void reorientAllWith(final Endpoint endpoint) {
        throw new UnsupportedOperationException();
    }

    public Endpoint[][] getEndpointMatrix() {
        return getGraph().getEndpointMatrix();
    }

    public List<Node> getAdjacentNodes(final Node node) {
        return getGraph().getAdjacentNodes(node);
    }

    public List<Node> getNodesInTo(final Node node, final Endpoint endpoint) {
        return getGraph().getNodesInTo(node, endpoint);
    }

    public List<Node> getNodesOutTo(final Node node, final Endpoint n) {
        return getGraph().getNodesOutTo(node, n);
    }

    public List<Node> getNodes() {
        return getGraph().getNodes();
    }

    public boolean removeEdge(final Node node1, final Node node2) {
        final List<Edge> edges = getEdges(node1, node2);

        if (edges.size() > 1) {
            throw new IllegalStateException(
                    "There is more than one edge between " + node1 + " and " +
                            node2);
        }

        return removeEdges(edges);
    }

    public boolean removeEdges(final Node node1, final Node node2) {
        return removeEdges(getEdges(node1, node2));
    }

    public boolean isAdjacentTo(final Node nodeX, final Node nodeY) {
        return getGraph().isAdjacentTo(nodeX, nodeY);
    }

    public boolean setEndpoint(final Node node1, final Node node2, final Endpoint endpoint) {
        return getGraph().setEndpoint(node1, node2, endpoint);
    }

    public Endpoint getEndpoint(final Node node1, final Node node2) {
        return getGraph().getEndpoint(node1, node2);
    }

    public boolean equals(final Object o) {
        return (o instanceof SemGraph) && getGraph().equals(o);
    }

    public Graph subgraph(final List<Node> nodes) {
        return getGraph().subgraph(nodes);
    }

    public boolean existsDirectedPathFromTo(final Node node1, final Node node2) {
        return getGraph().existsDirectedPathFromTo(node1, node2);
    }

    @Override
    public List<Node> findCycle() {
        return getGraph().findCycle();
    }

    public boolean existsUndirectedPathFromTo(final Node node1, final Node node2) {
        return getGraph().existsUndirectedPathFromTo(node1, node2);
    }

    public boolean existsSemiDirectedPathFromTo(final Node node1, final Set<Node> nodes2) {
        return getGraph().existsSemiDirectedPathFromTo(node1, nodes2);
    }

    public boolean addDirectedEdge(final Node nodeA, final Node nodeB) {
        return addEdge(Edges.directedEdge(nodeA, nodeB));
    }

    public boolean addUndirectedEdge(final Node nodeA, final Node nodeB) {
        throw new UnsupportedOperationException();
    }

    public boolean addNondirectedEdge(final Node nodeA, final Node nodeB) {
        throw new UnsupportedOperationException();
    }

    public boolean addPartiallyOrientedEdge(final Node nodeA, final Node nodeB) {
        throw new UnsupportedOperationException();
    }

    public boolean addBidirectedEdge(final Node nodeA, final Node nodeB) {
        return addEdge(Edges.bidirectedEdge(nodeA, nodeB));
    }

    public boolean addEdge(final Edge edge) {
        final Node node1 = edge.getNode1();
        final Node node2 = edge.getNode2();

        if (!getGraph().containsNode(node1) || !getGraph().containsNode(node2)) {
            if (node1.getNodeType() == NodeType.ERROR || node2.getNodeType() == NodeType.ERROR) {
                return false;
            }

            throw new IllegalArgumentException(
                    "Nodes for edge must be in graph already: " + edge);
        }

        if (Edges.isDirectedEdge(edge)) {
            if (getGraph().containsEdge(edge)) {
                return false;
            }

            final Node head = Edges.getDirectedEdgeHead(edge);

            if (head.getNodeType() == NodeType.ERROR) {
                return false;
            }

            getGraph().addEdge(edge);
            adjustErrorForNode(head);
            return true;
        } else if (Edges.isBidirectedEdge(edge)) {
            if (getGraph().containsEdge(edge)) {
                return false;
            }

//            if (!isExogenous(node1) || !isExogenous(node2)) {
//                throw new IllegalArgumentException("Nodes for a bidirected " +
//                        "edge must be exogenous: " + edge);
//            }

            getGraph().addEdge(edge);
            return true;
        } else {
            throw new IllegalArgumentException(
                    "Only directed and bidirected edges allowed.");
        }
    }

    public boolean addNode(final Node node) {
        if (node.getNodeType() == NodeType.ERROR) {
            throw new IllegalArgumentException("Error nodes cannot be added " +
                    "directly to the graph: " + node);
        }

        return getGraph().addNode(node);
    }

    public void addPropertyChangeListener(final PropertyChangeListener l) {
        getGraph().addPropertyChangeListener(l);
    }

    public boolean containsEdge(final Edge edge) {
        return getGraph().containsEdge(edge);
    }

    public boolean containsNode(final Node node) {
        return getGraph().containsNode(node);
    }

    public Set<Edge> getEdges() {
        return getGraph().getEdges();
    }

    public List<Edge> getEdges(final Node node) {
        return getGraph().getEdges(node);
    }

    public List<Edge> getEdges(final Node node1, final Node node2) {
        return getGraph().getEdges(node1, node2);
    }

    public Node getNode(final String name) {
        return getGraph().getNode(name);
    }

    public int getNumEdges() {
        return getGraph().getNumEdges();
    }

    public int getNumNodes() {
        return getGraph().getNumNodes();
    }

    public int getNumEdges(final Node node) {
        return getGraph().getNumEdges(node);
    }

    public boolean removeEdge(final Edge edge) {
        if (!getGraph().containsEdge(edge)) {
            throw new IllegalArgumentException(
                    "Can only remove edges that are " +
                            "already in the graph: " + edge);
        }

        if (Edges.isDirectedEdge(edge)) {
            final Node head = Edges.getDirectedEdgeHead(edge);
            final Node tail = Edges.getDirectedEdgeTail(edge);

            if (tail.getNodeType() != NodeType.ERROR) {
                getGraph().removeEdge(edge);
                adjustErrorForNode(head);
                return true;
            } else {
                return false;
            }
        } else if (Edges.isBidirectedEdge(edge)) {
            return getGraph().removeEdge(edge);
        } else {
            throw new IllegalArgumentException(
                    "Only directed and bidirected edges allowed.");
        }
    }

    public boolean removeEdges(final Collection<Edge> edges) {
        boolean change = false;

        for (final Object edge : edges) {
            final boolean _change = removeEdge((Edge) edge);
            change = change || _change;
        }

        return change;
    }

    public boolean removeNode(final Node node) {
        if (!getGraph().containsNode(node)) {
            throw new IllegalArgumentException(
                    "Graph must contain node: " + node);
        }

        if (node.getNodeType() == NodeType.ERROR) {
            throw new IllegalArgumentException(
                    "Error nodes cannot be removed " +
                            "directly from the graph: " + node);
        }

        final Node errorNode = getErrorNode(node);

        if (errorNode != null) {
            getGraph().removeNode(errorNode);
            this.errorNodes.remove(errorNode);
            this.errorNodes.remove(node);
        }

        final List<Node> children = getGraph().getChildren(node);
        getGraph().removeNode(node);

        for (int i = 0; i > children.size(); i++) {
            final Node child = children.get(i);
            adjustErrorForNode(child);
        }

        return true;
    }

    public void clear() {
        getGraph().clear();
    }

    public boolean removeNodes(final List<Node> nodes) {
        return getGraph().removeNodes(nodes);
    }

    public boolean existsDirectedCycle() {
        return getGraph().existsDirectedCycle();
    }

    public boolean isDirectedFromTo(final Node node1, final Node node2) {
        return getGraph().isDirectedFromTo(node1, node2);
    }

    public boolean isUndirectedFromTo(final Node node1, final Node node2) {
        return getGraph().isUndirectedFromTo(node1, node2);
    }

    public boolean defVisible(final Edge edge) {
        return getGraph().defVisible(edge);
    }

    public boolean isDefNoncollider(final Node node1, final Node node2, final Node node3) {
        return getGraph().isDefNoncollider(node1, node2, node3);
    }

    public boolean isDefCollider(final Node node1, final Node node2, final Node node3) {
        return getGraph().isDefCollider(node1, node2, node3);
    }

    public boolean existsTrek(final Node node1, final Node node2) {
        return getGraph().existsTrek(node1, node2);
    }

    public List<Node> getChildren(final Node node) {
        return getGraph().getChildren(node);
    }

    public int getConnectivity() {
        return getGraph().getConnectivity();
    }

    public List<Node> getDescendants(final List<Node> nodes) {
        return getGraph().getDescendants(nodes);
    }

    public Edge getEdge(final Node node1, final Node node2) {
        return getGraph().getEdge(node1, node2);
    }

    public Edge getDirectedEdge(final Node node1, final Node node2) {
        return getGraph().getDirectedEdge(node1, node2);
    }

    public List<Node> getParents(final Node node) {
        return getGraph().getParents(node);
    }

    public int getIndegree(final Node node) {
        return getGraph().getIndegree(node);
    }

    @Override
    public int getDegree(final Node node) {
        return getGraph().getDegree(node);
    }

    public int getOutdegree(final Node node) {
        return getGraph().getOutdegree(node);
    }

    public boolean isAncestorOf(final Node node1, final Node node2) {
        return getGraph().isAncestorOf(node1, node2);
    }

    public boolean possibleAncestor(final Node node1, final Node node2) {
        return getGraph().possibleAncestor(node1, node2);
    }

    public List<Node> getAncestors(final List<Node> nodes) {
        return getGraph().getAncestors(nodes);
    }

    public boolean isChildOf(final Node node1, final Node node2) {
        return getGraph().isChildOf(node1, node2);
    }

    public boolean isDescendentOf(final Node node1, final Node node2) {
        return getGraph().isDescendentOf(node1, node2);
    }

    public boolean defNonDescendent(final Node node1, final Node node2) {
        return getGraph().defNonDescendent(node1, node2);
    }

    public boolean isDConnectedTo(final Node node1, final Node node2,
                                  final List<Node> conditioningNodes) {
        return getGraph().isDConnectedTo(node1, node2, conditioningNodes);
    }

    public boolean isDSeparatedFrom(final Node node1, final Node node2, final List<Node> z) {
        return getGraph().isDSeparatedFrom(node1, node2, z);
    }

    public boolean possDConnectedTo(final Node node1, final Node node2, final List<Node> z) {
        return getGraph().possDConnectedTo(node1, node2, z);
    }

    public boolean existsInducingPath(final Node node1, final Node node2) {
        return getGraph().existsInducingPath(node1, node2);
    }

    public boolean isParentOf(final Node node1, final Node node2) {
        return getGraph().isParentOf(node1, node2);
    }

    public boolean isProperAncestorOf(final Node node1, final Node node2) {
        return getGraph().isProperAncestorOf(node1, node2);
    }

    public boolean isProperDescendentOf(final Node node1, final Node node2) {
        return getGraph().isProperDescendentOf(node1, node2);
    }

    public boolean isExogenous(final Node node) {
        return getGraph().isExogenous(node) || isShowErrorTerms() && getErrorNode(node) == null;
    }

    public String toString() {
        return getGraph().toString();
    }


    public boolean isShowErrorTerms() {
        return this.showErrorTerms;
    }

    public void setShowErrorTerms(final boolean showErrorTerms) {
        this.showErrorTerms = showErrorTerms;

        final List<Node> nodes = getNodes();

        for (final Node node : nodes) {
            if (!isParameterizable(node)) {
                continue;
            }

            if (!(node.getNodeType() == NodeType.ERROR)) {
                adjustErrorForNode(node);
            }
        }
    }

    public boolean isTimeLagModel() {
        return this.graph instanceof TimeLagGraph;
    }

    public TimeLagGraph getTimeLagGraph() {
        if (!isTimeLagModel()) {
            throw new IllegalArgumentException("Not a time lag model.");
        }

        return new TimeLagGraph((TimeLagGraph) this.graph);
    }

    @Override
    public void removeTriplesNotInGraph() {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Node> getSepset(final Node n1, final Node n2) {
        return this.graph.getSepset(n1, n2);
    }

    @Override
    public void setNodes(final List<Node> nodes) {
        this.graph.setNodes(nodes);
    }

    //========================PRIVATE METHODS===========================//

    /**
     * Creates a new error node, names it, and adds it to the graph.
     *
     * @param node the node which the new error node which be attached to.
     */
    private void addErrorNode(final Node node) {
        if (this.errorNodes.get(node) != null) {
            throw new IllegalArgumentException("Node already in map.");
        }

        final List<Node> nodes = getGraph().getNodes();

        Node error = errorNodes().get(node);

        if (error == null) {
            error = new GraphNode("E" + "_" + node.getName());
            error.setCenter(node.getCenterX() + 50, node.getCenterY() + 50);
            error.setNodeType(NodeType.ERROR);
            this.errorNodes.put(node, error);
        }

        for (final Node possibleError : nodes) {
            if (error.getName().equals(possibleError.getName())) {
                moveAttachedBidirectedEdges(possibleError, node);
                if (getGraph().containsNode(possibleError)) {
                    getGraph().removeNode(possibleError);
                }
                this.errorNodes.remove(node);
                this.errorNodes.remove(possibleError);
            }
        }

        getGraph().addNode(error);
        this.errorNodes.put(node, error);
        this.errorNodes.put(error, error);
        addDirectedEdge(error, node);
    }

    private Map<Node, Node> errorNodes() {
        if (this.errorNodes == null) {
            this.errorNodes = new HashMap<>();
        }

        return this.errorNodes;
    }

    private void moveAttachedBidirectedEdges(final Node node1, final Node node2) {
        if (node1 == null || node2 == null) {
            throw new IllegalArgumentException("Node must not be null.");
        }

        final Graph graph = getGraph();
        List<Edge> edges = graph.getEdges(node1);

        if (edges == null) {
            System.out.println();
            edges = new ArrayList<>();
        }

        final List<Edge> attachedEdges = new LinkedList<>(edges);

        for (final Edge edge : attachedEdges) {
            if (Edges.isBidirectedEdge(edge)) {
                graph.removeEdge(edge);
                final Node distal = edge.getDistalNode(node1);
                graph.addBidirectedEdge(node2, distal);
            }
        }
    }

    /**
     * If the specified node is exogenous and has an error node, moves any edges
     * attached to its error node to the node itself and removes the error node.
     * If the specified node is endogenous and has no error node, adds an error
     * node and moves any bidirected edges attached to the node to its error
     * node.
     */
    private void adjustErrorForNode(final Node node) {
        if (!this.showErrorTerms) {
//            if (!isShowErrorTerms() || shouldBeExogenous(node)) {
            final Node errorNode = getErrorNode(node);

            if (errorNode != null && this.graph.containsNode(errorNode)) {
                moveAttachedBidirectedEdges(errorNode, node);
                getGraph().removeNode(errorNode);
                this.errorNodes.remove(node);
                this.errorNodes.remove(errorNode);
            }
        } else {
            Node errorNode = getErrorNode(node);

            if (errorNode == null) {
                addErrorNode(node);
            }

            errorNode = getErrorNode(node);
            moveAttachedBidirectedEdges(node, errorNode);
        }
    }

    /**
     * @return true iff the given (non-error) term ought to be exogenous.
     */
    private boolean shouldBeExogenous(final Node node) {
        final List<Node> parents = getGraph().getParents(node);

        for (final Node parent : parents) {
            if (parent.getNodeType() != NodeType.ERROR) {
                return false;
            }
        }

        return true;
    }

    /**
     * Adds semantic checks to the default deserialization method. This method
     * must have the standard signature for a readObject method, and the body of
     * the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from
     * version to version. A readObject method of this form may be added to any
     * class, even if Tetrad sessions were previously saved out using a version
     * of the class that didn't include it. (That's what the
     * "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for help.
     *
     * @throws java.io.IOException
     * @throws ClassNotFoundException
     */
    private void readObject(final ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (getGraph() == null) {
            throw new NullPointerException();
        }
    }

    private Graph getGraph() {
        return this.graph;
    }

    public void resetErrorPositions() {
        for (final Node node : getNodes()) {
            final Node error = errorNodes().get(node);

            if (error != null) {
                error.setCenter(node.getCenterX() + 50, node.getCenterY() + 50);
            }
        }
    }

    @Override
    public List<String> getTriplesClassificationTypes() {
        return null;
    }

    @Override
    public List<List<Triple>> getTriplesLists(final Node node) {
        return null;
    }

    @Override
    public boolean isPag() {
        return this.pag;
    }

    @Override
    public void setPag(final boolean pag) {
        this.pag = pag;
    }

    @Override
    public boolean isCPDAG() {
        return this.cpdag;
    }

    @Override
    public void setCPDAG(final boolean cpdag) {
        this.cpdag = cpdag;
    }

    @Override
    public Map<String, Object> getAllAttributes() {
        return this.attributes;
    }

    @Override
    public Object getAttribute(final String key) {
        return this.attributes.get(key);
    }

    @Override
    public void removeAttribute(final String key) {
        this.attributes.remove(key);
    }

    @Override
    public void addAttribute(final String key, final Object value) {
        this.attributes.put(key, value);
    }

}





