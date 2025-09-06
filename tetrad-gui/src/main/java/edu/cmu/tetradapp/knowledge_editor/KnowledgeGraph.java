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

package edu.cmu.tetradapp.knowledge_editor;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.util.TetradSerializableExcluded;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.beans.PropertyChangeListener;
import java.io.Serial;
import java.util.*;

/**
 * This class represents a directed acyclic graph.  In addition to the constraints imposed by Graph, the following
 * (mostly redundant) basicConstraints are in place: (a) The graph may contain only measured and latent variables (no
 * error variables). (b) The graph may contain only directed edges (c) The graph may contain no directed cycles.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class KnowledgeGraph implements Graph, TetradSerializableExcluded {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Represents a graph data structure.
     * <p>
     * The graph can be of any type, allowing different implementations of the graph interface. In this case, the
     * {@link EdgeListGraph} implementation is used.
     * <p>
     * The graph variable is marked as private and final to restrict external modifications.
     *
     * @see EdgeListGraph
     */
    private final Graph graph = new EdgeListGraph();

    /**
     * The knowledge.
     */
    private final Knowledge knowledge;

    /**
     * The attributes.
     */
    private final Map<String, Object> attributes = new HashMap<>();

    /**
     * The paths.
     */
    private final Paths paths;

    /**
     * The underline triples.
     */
    private final Set<Triple> underLineTriples = new HashSet<>();

    /**
     * The dotted underline triples.
     */
    private final Set<Triple> dottedUnderLineTriples = new HashSet<>();

    /**
     * The ambiguous triples.
     */
    private final Set<Triple> ambiguousTriples = new HashSet<>();

    //============================CONSTRUCTORS=============================//

    /**
     * Constructs a new directed acyclic graph (DAG).
     *
     * @param knowledge a {@link edu.cmu.tetrad.data.Knowledge} object
     */
    public KnowledgeGraph(Knowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException();
        }

        this.knowledge = knowledge;
        this.paths = new Paths(this.graph);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetradapp.knowledge_editor.KnowledgeGraph} object
     * @see TetradSerializableUtils
     */
    public static KnowledgeGraph serializableInstance() {
        return new KnowledgeGraph(Knowledge.serializableInstance());
    }

    //=============================PUBLIC METHODS==========================//

    /**
     * Transfer nodes and edges from the given graph to the current graph.
     *
     * @param graph the graph from which to transfer nodes and edges
     * @throws IllegalArgumentException if the provided graph is null
     */
    public final void transferNodesAndEdges(Graph graph)
            throws IllegalArgumentException {
        this.getGraph().transferNodesAndEdges(graph);
        for (Node node : this.getGraph().getNodes()) {
            node.getAllAttributes().clear();
        }
    }

    /**
     * Transfers the attributes from the given graph to this graph.
     *
     * @param graph The graph from which the attribute values should be transferred.
     * @throws IllegalArgumentException If the given graph is null.
     */
    public final void transferAttributes(Graph graph)
            throws IllegalArgumentException {
        this.getGraph().transferAttributes(graph);
    }

    /**
     * Returns the Paths object associated with this instance.
     *
     * @return the Paths object.
     */
    @Override
    public Paths paths() {
        return this.paths;
    }

    /**
     * Checks whether the given Node is parameterizable.
     *
     * @param node The Node to check.
     * @return true if the Node is parameterizable, false otherwise.
     */
    public boolean isParameterizable(Node node) {
        return false;
    }

    /**
     * Checks if the model is a time lag model.
     *
     * @return true if the model is a time lag model, false otherwise.
     */
    public boolean isTimeLagModel() {
        return false;
    }

    /**
     * Retrieves the TimeLagGraph object.
     *
     * @return The TimeLagGraph object.
     */
    public TimeLagGraph getTimeLagGraph() {
        return null;
    }

    /**
     * Returns the set of nodes that form the separator set for the given two nodes in the graph.
     *
     * @param n1   the first node
     * @param n2   the second node
     * @param test
     * @return the set of nodes that form the separator set
     */
    @Override
    public Set<Node> getSepset(Node n1, Node n2, IndependenceTest test) {
        return this.graph.getSepset(n1, n2, test);
    }

    /**
     * Retrieves the names of all the nodes in the graph
     *
     * @return The list of node names
     */
    public List<String> getNodeNames() {
        return getGraph().getNodeNames();
    }

    /**
     * Connects the specified endpoint to all other endpoints in the graph.
     *
     * @param endpoint the endpoint to be fully connected
     */
    public void fullyConnect(Endpoint endpoint) {
        getGraph().fullyConnect(endpoint);
    }

    /**
     * Reorients all endpoints in the graph with the specified endpoint.
     *
     * @param endpoint the endpoint to reorient all endpoints in the graph with
     */
    public void reorientAllWith(Endpoint endpoint) {
        getGraph().reorientAllWith(endpoint);
    }

    /**
     * Returns a list of adjacent nodes to the given node in the graph.
     *
     * @param node the node for which to find adjacent nodes
     * @return a list of adjacent nodes
     */
    public List<Node> getAdjacentNodes(Node node) {
        return getGraph().getAdjacentNodes(node);
    }

    /**
     * Get the list of nodes in the graph that have an edge pointing into the given node and connected to the given
     * endpoint.
     *
     * @param node     The node for which to get the incoming nodes.
     * @param endpoint The endpoint that connects the nodes.
     * @return The list of nodes in the graph that have an edge pointing into the given node and connected to the given
     * endpoint.
     */
    public List<Node> getNodesInTo(Node node, Endpoint endpoint) {
        return getGraph().getNodesInTo(node, endpoint);
    }

    /**
     * Retrieves the list of nodes that have outgoing edges to the specified destination node.
     *
     * @param node the source node from which the edges originate
     * @param n    the destination endpoint node
     * @return the list of nodes that have outgoing edges to the specified destination node
     */
    public List<Node> getNodesOutTo(Node node, Endpoint n) {
        return getGraph().getNodesOutTo(node, n);
    }

    /**
     * Retrieves the list of nodes in the graph.
     *
     * @return the list of nodes in the graph
     */
    public List<Node> getNodes() {
        return getGraph().getNodes();
    }

    /**
     * Sets the list of nodes in the graph.
     *
     * @param nodes the list of nodes to be set
     */
    @Override
    public void setNodes(List<Node> nodes) {
        this.graph.setNodes(nodes);
    }

    /**
     * Removes the edge between two nodes.
     *
     * @param node1 the first node
     * @param node2 the second node
     * @return true if the edge is successfully removed, false if the edge does not exist
     */
    public boolean removeEdge(Node node1, Node node2) {
        return removeEdge(getEdge(node1, node2));
    }

    /**
     * Removes the edges between two nodes in the graph.
     *
     * @param node1 the first node
     * @param node2 the second node
     * @return true if the edges are successfully removed, false otherwise
     */
    public boolean removeEdges(Node node1, Node node2) {
        return getGraph().removeEdges(node1, node2);
    }

    /**
     * Checks if two nodes are adjacent in the graph.
     *
     * @param nodeX the first node to check adjacency
     * @param nodeY the second node to check adjacency
     * @return true if nodeX is adjacent to nodeY, otherwise false
     */
    public boolean isAdjacentTo(Node nodeX, Node nodeY) {
        return getGraph().isAdjacentTo(nodeX, nodeY);
    }

    /**
     * Sets the endpoint of a given graph's edge between the specified nodes.
     *
     * @param node1    The starting node of the edge.
     * @param node2    The ending node of the edge.
     * @param endpoint The desired endpoint for the edge.
     * @return true if the endpoint was successfully set, false otherwise.
     */
    public boolean setEndpoint(Node node1, Node node2, Endpoint endpoint) {
        return getGraph().setEndpoint(node1, node2, endpoint);
    }

    /**
     * Retrieves the endpoint of a given pair of nodes in the graph.
     *
     * @param node1 the first node
     * @param node2 the second node
     * @return the endpoint of the nodes in the graph
     */
    public Endpoint getEndpoint(Node node1, Node node2) {
        return getGraph().getEndpoint(node1, node2);
    }

    /**
     * Compares this KnowledgeGraph with the specified Object for equality.
     *
     * @param o the Object to be compared for equality
     * @return true if the specified Object is equal to this KnowledgeGraph, false otherwise
     */
    public boolean equals(Object o) {
        if (!(o instanceof KnowledgeGraph)) return false;
        return getGraph().equals(o);
    }

    /**
     * Returns a subgraph of the graph, containing only the nodes specified in the input list.
     *
     * @param nodes the list of nodes to include in the subgraph
     * @return a subgraph containing only the specified nodes
     */
    public Graph subgraph(List<Node> nodes) {
        return getGraph().subgraph(nodes);
    }

    /**
     * Adds a directed edge from the source node to the destination node.
     *
     * @param nodeA the source node
     * @param nodeB the destination node
     * @return true if the directed edge is successfully added, false otherwise
     */
    public boolean addDirectedEdge(Node nodeA, Node nodeB) {
        throw new UnsupportedOperationException();
    }

    /**
     * Adds an undirected edge between two nodes.
     *
     * @param nodeA the first node to connect
     * @param nodeB the second node to connect
     * @return {@code true} if the edge between the two nodes is successfully added, {@code false} otherwise
     * @throws UnsupportedOperationException if the method is called on an unsupported operation
     */
    public boolean addUndirectedEdge(Node nodeA, Node nodeB) {
        throw new UnsupportedOperationException();
    }

    /**
     * Adds a nondirected edge between two nodes.
     *
     * @param nodeA the first node
     * @param nodeB the second node
     * @return true if the edge was successfully added, false otherwise
     */
    public boolean addNondirectedEdge(Node nodeA, Node nodeB) {
        throw new UnsupportedOperationException();
    }

    /**
     * Adds a partially oriented edge between {@code nodeA} and {@code nodeB}.
     *
     * @param nodeA the origin node of the partially oriented edge
     * @param nodeB the destination node of the partially oriented edge
     * @return {@code true} if the partially oriented edge was added successfully, otherwise {@code false}
     */
    public boolean addPartiallyOrientedEdge(Node nodeA, Node nodeB) {
        throw new UnsupportedOperationException();
    }

    /**
     * Adds a bidirectional edge between two nodes.
     *
     * @param nodeA the first node
     * @param nodeB the second node
     * @return true if the bidirectional edge is added successfully, false otherwise
     */
    public boolean addBidirectedEdge(Node nodeA, Node nodeB) {
        throw new UnsupportedOperationException();
    }

    /**
     * Adds the specified edge to the graph.
     *
     * @param edge the edge to be added to the graph
     * @return true if the edge is successfully added, false otherwise
     */
    public boolean addEdge(Edge edge) {
        if (!(edge instanceof KnowledgeModelEdge _edge)) {
            return false;
        }
        KnowledgeModelNode _node1 = (KnowledgeModelNode) _edge.getNode1();
        KnowledgeModelNode _node2 = (KnowledgeModelNode) _edge.getNode2();
        String from = _node1.getName();
        String to = _node2.getName();

        if (_edge.getType() == KnowledgeModelEdge.FORBIDDEN_EXPLICITLY) {
            this.knowledge.setForbidden(from, to);
        } else if (_edge.getType() == KnowledgeModelEdge.REQUIRED) {
            this.knowledge.setRequired(from, to);
        } else if (_edge.getType() == KnowledgeModelEdge.FORBIDDEN_BY_TIERS) {
            if (!this.knowledge.isForbiddenByTiers(from, to)) {
                throw new IllegalArgumentException("Edge " + from + "-->" + to +
                                                   " is not forbidden by tiers.");
            }
        } else if (_edge.getType() == KnowledgeModelEdge.FORBIDDEN_BY_GROUPS) {
            if (!this.knowledge.isForbiddenByGroups(from, to)) {
                throw new IllegalArgumentException("Edge " + from + "-->" + to +
                                                   " is not forbidden by groups.");
            }
        } else if (_edge.getType() == KnowledgeModelEdge.REQUIRED_BY_GROUPS) {
            if (!this.knowledge.isRequiredByGroups(from, to)) {
                throw new IllegalArgumentException("Edge " + from + "-->" + to +
                                                   " is not required by groups.");
            }
        }

        if (!getGraph().containsEdge(edge)) {
            return getGraph().addEdge(edge);
        }

        return false;
    }

    /**
     * Adds a node to the graph.
     *
     * @param node the node to be added
     * @return true if the node was added successfully, false otherwise
     */
    public boolean addNode(Node node) {
        return getGraph().addNode(node);
    }

    /**
     * Adds a PropertyChangeListener to the Graph. The PropertyChangeListener will be notified of any changes to the
     * properties of the Graph.
     *
     * @param l the PropertyChangeListener to be added
     */
    public void addPropertyChangeListener(PropertyChangeListener l) {
        getGraph().addPropertyChangeListener(l);
    }

    /**
     * Checks if the graph contains the specified edge.
     *
     * @param edge the edge to check for
     * @return {@code true} if the graph contains the edge, otherwise {@code false}
     */
    public boolean containsEdge(Edge edge) {
        return getGraph().containsEdge(edge);
    }

    /**
     * Checks if a specific node is present in the graph.
     *
     * @param node The node to check for presence in the graph.
     * @return {@code true} if the node is present in the graph, otherwise {@code false}.
     */
    public boolean containsNode(Node node) {
        return getGraph().containsNode(node);
    }

    /**
     * Returns the set of edges in the graph.
     *
     * @return a Set of Edge objects representing the edges in the graph
     */
    public Set<Edge> getEdges() {
        return getGraph().getEdges();
    }

    /**
     * Retrieves the list of edges connected to the given node in the graph.
     *
     * @param node the node for which to retrieve the edges
     * @return the list of edges connected to the given node
     */
    public Set<Edge> getEdges(Node node) {
        return getGraph().getEdges(node);
    }

    /**
     * Returns a list of edges between two nodes in the graph.
     *
     * @param node1 the first node
     * @param node2 the second node
     * @return a list of edges between node1 and node2
     */
    public List<Edge> getEdges(Node node1, Node node2) {
        return getGraph().getEdges(node1, node2);
    }

    /**
     * Retrieves a node from the graph with the specified name.
     *
     * @param name the name of the node to retrieve
     * @return the node with the specified name, or null if not found
     */
    public Node getNode(String name) {
        return getGraph().getNode(name);
    }

    /**
     * Returns the number of edges in the graph.
     *
     * @return the number of edges in the graph.
     */
    public int getNumEdges() {
        return getGraph().getNumEdges();
    }

    /**
     * Retrieves the number of nodes in the graph.
     *
     * @return the number of nodes in the graph.
     */
    public int getNumNodes() {
        return getGraph().getNumNodes();
    }

    /**
     * Retrieves the number of edges for a given node in the graph. This method uses the getGraph() method to access the
     * graph and uses the getNumEdges() method of the graph to retrieve the number of edges for the given node.
     *
     * @param node the node for which to retrieve the number of edges
     * @return the number of edges for the given node in the graph
     */
    public int getNumEdges(Node node) {
        return getGraph().getNumEdges(node);
    }

    /**
     * Removes an edge from the knowledge graph.
     *
     * @param edge the edge to be removed
     * @return true if the edge was successfully removed, false otherwise
     */
    public boolean removeEdge(Edge edge) {
        KnowledgeModelEdge _edge = (KnowledgeModelEdge) edge;
        KnowledgeModelNode _node1 = (KnowledgeModelNode) _edge.getNode1();
        KnowledgeModelNode _node2 = (KnowledgeModelNode) _edge.getNode2();
        String from = _node1.getName();
        String to = _node2.getName();

        if (_edge.getType() == KnowledgeModelEdge.FORBIDDEN_EXPLICITLY) {
            getKnowledge().removeForbidden(from, to);
        } else if (_edge.getType() == KnowledgeModelEdge.REQUIRED) {
            getKnowledge().removeRequired(from, to);
        } else if (_edge.getType() == KnowledgeModelEdge.FORBIDDEN_BY_TIERS) {
            throw new IllegalArgumentException(
                    "Please use the tiers interface " +
                    "to remove edges forbidden by tiers.");
        } else if (_edge.getType() == KnowledgeModelEdge.FORBIDDEN_BY_GROUPS) {
            throw new IllegalArgumentException("Please use the Other Groups interface to " +
                                               "remove edges forbidden by groups.");
        } else if (_edge.getType() == KnowledgeModelEdge.REQUIRED_BY_GROUPS) {
            throw new IllegalArgumentException("Please use the Other Groups interface to " +
                                               "remove edges required by groups.");
        }

        return getGraph().removeEdge(edge);
    }

    /**
     * Removes a collection of edges from the graph.
     *
     * @param edges the collection of edges to be removed
     * @return {@code true} if any edge is successfully removed, {@code false} otherwise
     */
    public boolean removeEdges(Collection<Edge> edges) {
        boolean removed = false;

        for (Edge edge : edges) {
            removed = removed || removeEdge(edge);
        }

        return removed;
    }

    /**
     * Removes a given node from the graph.
     *
     * @param node the node to be removed
     * @return true if the node was successfully removed, false otherwise
     */
    public boolean removeNode(Node node) {
        return getGraph().removeNode(node);
    }

    /**
     * Clears the graph by removing all its elements.
     */
    public void clear() {
        getGraph().clear();
    }

    /**
     * Removes the given nodes from the graph.
     *
     * @param nodes The list of nodes to be removed.
     * @return True if the nodes were successfully removed, false otherwise.
     */
    public boolean removeNodes(List<Node> nodes) {
        return getGraph().removeNodes(nodes);
    }

    /**
     * Checks if the given nodes form a default noncollider in the graph.
     *
     * @param node1 the first node in the potential noncollider
     * @param node2 the second node in the potential noncollider
     * @param node3 the third node in the potential noncollider
     * @return true if the nodes form a default noncollider, false otherwise
     */
    public boolean isDefNoncollider(Node node1, Node node2, Node node3) {
        return getGraph().isDefNoncollider(node1, node2, node3);
    }

    /**
     * Determines if there is a default collider between three nodes.
     *
     * @param node1 the first node
     * @param node2 the second node
     * @param node3 the third node
     * @return true if there is a default collider, false otherwise
     */
    public boolean isDefCollider(Node node1, Node node2, Node node3) {
        return getGraph().isDefCollider(node1, node2, node3);
    }

    /**
     * Returns a list of child nodes for the given node.
     *
     * @param node the node for which to retrieve the child nodes.
     * @return a list of child nodes for the given node.
     */
    public List<Node> getChildren(Node node) {
        return getGraph().getChildren(node);
    }

    /**
     * Returns the degree of the graph.
     *
     * @return the degree of the graph
     */
    public int getDegree() {
        return getGraph().getDegree();
    }

    /**
     * Retrieves the edge between two nodes in the graph.
     *
     * @param node1 the first node
     * @param node2 the second node
     * @return the edge between node1 and node2
     */
    public Edge getEdge(Node node1, Node node2) {
        return getGraph().getEdge(node1, node2);
    }

    /**
     * Returns the directed edge between two nodes.
     *
     * @param node1 the first node
     * @param node2 the second node
     * @return the directed edge between the two nodes
     */
    public Edge getDirectedEdge(Node node1, Node node2) {
        return getGraph().getDirectedEdge(node1, node2);
    }

    /**
     * Returns the list of parent nodes for the given node.
     *
     * @param node The node for which parents need to be retrieved.
     * @return The list of parent nodes for the given node.
     */
    public List<Node> getParents(Node node) {
        return getGraph().getParents(node);
    }

    /**
     * Returns the indegree of the specified node in the graph.
     *
     * @param node the node to get the indegree for
     * @return the indegree of the specified node
     */
    public int getIndegree(Node node) {
        return getGraph().getIndegree(node);
    }

    /**
     * Retrieves the degree of the given node in the graph.
     *
     * @param node the node for which to retrieve the degree
     * @return the degree of the specified node in the graph
     */
    @Override
    public int getDegree(Node node) {
        return getGraph().getDegree(node);
    }

    /**
     * Returns the outdegree of a given node in the graph.
     *
     * @param node The node for which to determine the outdegree.
     * @return The outdegree of the given node.
     */
    public int getOutdegree(Node node) {
        return getGraph().getOutdegree(node);
    }

    /**
     * Checks if a given Node is a child of another Node.
     *
     * @param node1 the Node to be checked
     * @param node2 the potential parent Node
     * @return true if node1 is a child of node2, false otherwise
     */
    public boolean isChildOf(Node node1, Node node2) {
        return getGraph().isChildOf(node1, node2);
    }

    /**
     * Returns true if the first node is a parent of the second node in the graph.
     *
     * @param node1 The first node.
     * @param node2 The second node.
     * @return True if the first node is a parent of the second node, otherwise false.
     */
    public boolean isParentOf(Node node1, Node node2) {
        return getGraph().isParentOf(node1, node2);
    }

    /**
     * Determines if a given node is exogenous.
     *
     * @param node the node to check
     * @return <code>true</code> if the node is exogenous, <code>false</code> otherwise
     */
    public boolean isExogenous(Node node) {
        return getGraph().isExogenous(node);
    }

    /**
     * Returns a string representation of the object. The returned string is obtained by calling the toString method of
     * the underlying graph object.
     *
     * @return a string representation of the object.
     */
    public String toString() {
        return getGraph().toString();
    }

    /**
     * Retrieves the knowledge object.
     *
     * @return The knowledge object.
     */
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * Retrieves the graph object.
     *
     * @return The graph object.
     */
    private Graph getGraph() {
        return this.graph;
    }

    /**
     * Retrieves all attributes stored in the object.
     *
     * @return A Map representing the attributes stored in the object.
     */
    @Override
    public Map<String, Object> getAllAttributes() {
        return this.attributes;
    }

    /**
     * Retrieves the value associated with the specified key from this object's attributes.
     *
     * @param key the key whose associated value is to be retrieved
     * @return the value to which the specified key is mapped, or null if this object contains no mapping for the key
     */
    @Override
    public Object getAttribute(String key) {
        return this.attributes.get(key);
    }

    /**
     * Removes the attribute with the specified key from the object.
     *
     * @param key the key associated with the attribute to be removed
     */
    @Override
    public void removeAttribute(String key) {
        this.attributes.remove(key);
    }

    /**
     * Adds an attribute to the internal attribute map.
     *
     * @param key   the key of the attribute
     * @param value the value of the attribute
     */
    @Override
    public void addAttribute(String key, Object value) {
        this.attributes.put(key, value);
    }

    /**
     * Retrieves a set of ambiguous triples.
     *
     * @return the set of ambiguous triples
     */
    public Set<Triple> getAmbiguousTriples() {
        return new HashSet<>(this.ambiguousTriples);
    }

    /**
     * Sets the ambiguous triples.
     *
     * @param triples - the set of triples to be set as ambiguous
     */
    public void setAmbiguousTriples(Set<Triple> triples) {
        this.ambiguousTriples.clear();

        for (Triple triple : triples) {
            addAmbiguousTriple(triple.getX(), triple.getY(), triple.getZ());
        }
    }

    /**
     * Retrieves the set of underlines.
     *
     * @return the set of underlines as a new HashSet.
     */
    public Set<Triple> getUnderLines() {
        return new HashSet<>(this.underLineTriples);
    }

    /**
     * Returns a set of Triple objects representing the dotted underlines.
     *
     * @return a set of Triple objects representing the dotted underlines
     */
    public Set<Triple> getDottedUnderlines() {
        return new HashSet<>(this.dottedUnderLineTriples);
    }

    /**
     * Determines if a triple of nodes is ambiguous.
     *
     * @param x the first node
     * @param y the second node
     * @param z the third node
     * @return true if the triple is ambiguous, false otherwise
     */
    public boolean isAmbiguousTriple(Node x, Node y, Node z) {
        return this.ambiguousTriples.contains(new Triple(x, y, z));
    }

    /**
     * Checks if a given triple of nodes is an underline triple.
     *
     * @param x the first node in the triple
     * @param y the second node in the triple
     * @param z the third node in the triple
     * @return true if the triple is an underline triple, false otherwise
     */
    public boolean isUnderlineTriple(Node x, Node y, Node z) {
        return this.underLineTriples.contains(new Triple(x, y, z));
    }

    /**
     * Adds an ambiguous triple to the collection.
     *
     * @param x - the first node of the triple
     * @param y - the second node of the triple
     * @param z - the third node of the triple
     */
    public void addAmbiguousTriple(Node x, Node y, Node z) {
        this.ambiguousTriples.add(new Triple(x, y, z));
    }

    /**
     * Adds the given triple to the collection of underline triples if it exists along a path in the current node.
     *
     * @param x The first node of the triple.
     * @param y The second node of the triple.
     * @param z The third node of the triple.
     */
    public void addUnderlineTriple(Node x, Node y, Node z) {
        Triple triple = new Triple(x, y, z);

        if (!triple.alongPathIn(this)) {
            return;
        }

        this.underLineTriples.add(new Triple(x, y, z));
    }

    /**
     * Adds a triple with dotted underline to the collection of dotted underline triples.
     *
     * @param x The first node of the triple.
     * @param y The second node of the triple.
     * @param z The third node of the triple.
     */
    public void addDottedUnderlineTriple(Node x, Node y, Node z) {
        Triple triple = new Triple(x, y, z);

        if (!triple.alongPathIn(this)) {
            return;
        }

        this.dottedUnderLineTriples.add(triple);
    }

    /**
     * Removes the specified triple from the list of ambiguous triples.
     *
     * @param x the first node of the triple to be removed
     * @param y the second node of the triple to be removed
     * @param z the third node of the triple to be removed
     */
    public void removeAmbiguousTriple(Node x, Node y, Node z) {
        this.ambiguousTriples.remove(new Triple(x, y, z));
    }

    /**
     * Removes an underline triple from the collection.
     *
     * @param x the first node of the triple to be removed
     * @param y the second node of the triple to be removed
     * @param z the third node of the triple to be removed
     */
    public void removeUnderlineTriple(Node x, Node y, Node z) {
        this.underLineTriples.remove(new Triple(x, y, z));
    }

    /**
     * Removes the specified triple (x, y, z) from the list of dotted underline triples.
     *
     * @param x The first node of the triple to be removed.
     * @param y The second node of the triple to be removed.
     * @param z The third node of the triple to be removed.
     */
    public void removeDottedUnderlineTriple(Node x, Node y, Node z) {
        this.dottedUnderLineTriples.remove(new Triple(x, y, z));
    }

    /**
     * Sets the underline triples.
     *
     * @param triples the set of triples to be set as underline triples
     */
    public void setUnderLineTriples(Set<Triple> triples) {
        this.underLineTriples.clear();

        for (Triple triple : triples) {
            addUnderlineTriple(triple.getX(), triple.getY(), triple.getZ());
        }
    }

    /**
     * Clears the existing collection of dotted underlined triples and adds new triples to it.
     *
     * @param triples The collection of triples to add.
     */
    public void setDottedUnderLineTriples(Set<Triple> triples) {
        this.dottedUnderLineTriples.clear();

        for (Triple triple : triples) {
            addDottedUnderlineTriple(triple.getX(), triple.getY(), triple.getZ());
        }
    }

    /**
     * Removes triples from the lists ("ambiguousTriples", "underLineTriples", and "dottedUnderLineTriples") that do not
     * have all three nodes present in the graph or are not adjacent to each other.
     */
    public void removeTriplesNotInGraph() {
        for (Triple triple : new HashSet<>(this.ambiguousTriples)) {
            if (!containsNode(triple.getX()) || !containsNode(triple.getY())
                || !containsNode(triple.getZ())) {
                this.ambiguousTriples.remove(triple);
                continue;
            }

            if (!isAdjacentTo(triple.getX(), triple.getY())
                || !isAdjacentTo(triple.getY(), triple.getZ())) {
                this.ambiguousTriples.remove(triple);
            }
        }

        for (Triple triple : new HashSet<>(this.underLineTriples)) {
            if (!containsNode(triple.getX()) || !containsNode(triple.getY())
                || !containsNode(triple.getZ())) {
                this.underLineTriples.remove(triple);
                continue;
            }

            if (!isAdjacentTo(triple.getX(), triple.getY()) || !isAdjacentTo(triple.getY(), triple.getZ())) {
                this.underLineTriples.remove(triple);
            }
        }

        for (Triple triple : new HashSet<>(this.dottedUnderLineTriples)) {
            if (!containsNode(triple.getX()) || !containsNode(triple.getY()) || !containsNode(triple.getZ())) {
                this.dottedUnderLineTriples.remove(triple);
                continue;
            }

            if (!isAdjacentTo(triple.getX(), triple.getY()) || isAdjacentTo(triple.getY(), triple.getZ())) {
                this.dottedUnderLineTriples.remove(triple);
            }
        }
    }
}





