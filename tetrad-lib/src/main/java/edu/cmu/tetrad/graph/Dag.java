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

import edu.cmu.tetrad.search.IndependenceTest;

import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.util.*;

/**
 * Represents a directed acyclic graph--that is, a graph containing only directed edges, with no cycles. Variables are
 * permitted to be either measured or latent, with at most one edge per node pair, and no edges to self.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public final class Dag implements Graph {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The graph.
     */
    private final Graph graph;

    /**
     * The set of underline triples.
     */
    private final Set<Triple> underLineTriples = new HashSet<>();

    /**
     * The set of dotted underline triples.
     */
    private final Set<Triple> dottedUnderLineTriples = new HashSet<>();

    /**
     * The set of ambiguous triples.
     */
    private final Set<Triple> ambiguousTriples = new HashSet<>();

    //===============================CONSTRUCTORS=======================//

    /**
     * Constructs a new directed acyclic graph (DAG).
     */
    public Dag() {
        this.graph = new EdgeListGraph();
    }

    /**
     * <p>Constructor for Dag.</p>
     *
     * @param nodes a {@link java.util.List} object
     */
    public Dag(List<Node> nodes) {
        this.graph = new EdgeListGraph(nodes);
    }

    /**
     * Constructs a new directed acyclic graph from the given graph object.
     *
     * @param graph the graph to base the new DAG on.
     * @throws java.lang.IllegalArgumentException if the given graph cannot for some reason be converted into a DAG.
     */
    public Dag(Graph graph) throws IllegalArgumentException {
        if (graph.paths().existsDirectedCycle()) {
            throw new IllegalArgumentException("That graph was not acyclic.");
        }

        this.graph = new EdgeListGraph();

        transferNodesAndEdges(graph);

        for (Node node : this.graph.getNodes()) {
            node.getAllAttributes().clear();
        }
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetrad.graph.Dag} object
     */
    public static Dag serializableInstance() {
        Dag dag = new Dag();
        GraphNode node1 = new GraphNode("X");
        dag.addNode(node1);
        return dag;
    }

    //===============================PUBLIC METHODS======================//

    /**
     * Adds semantic checks to the default deserialization method. This method must have the standard signature for a
     * readObject method, and the body of the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from version to version. A readObject method of
     * this form may be added to any class, even if Tetrad sessions were previously saved out using a version of the
     * class that didn't include it. (That's what the "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for
     * help.)
     *
     * @param s The object input stream.
     * @throws IOException            If any.
     * @throws ClassNotFoundException If any.
     */
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();
    }

    /**
     * Adds a bidirectional edge between two nodes.
     *
     * @param node1 the first node to connect (a {@link Node} object)
     * @param node2 the second node to connect (a {@link Node} object)
     * @return true if the bidirectional edge was successfully added, false otherwise
     */
    public boolean addBidirectedEdge(Node node1, Node node2) {
        return this.graph.addBidirectedEdge(node1, node2);
    }

    /**
     * Adds a directed edge between two nodes.
     *
     * @param node1 the first node to connect (source node)
     * @param node2 the second node to connect (target node)
     * @return true if the directed edge is successfully added, false otherwise
     */
    public boolean addDirectedEdge(Node node1, Node node2) {
        return addEdge(Edges.directedEdge(node1, node2));
    }

    /**
     * Adds an undirected edge between two nodes.
     *
     * @param node1 the first node to connect (a {@link Node} object)
     * @param node2 the second node to connect (a {@link Node} object)
     * @return true if the undirected edge is successfully added, false otherwise
     */
    public boolean addUndirectedEdge(Node node1, Node node2) {
        throw new UnsupportedOperationException("Disallowed for a DAG.");
    }

    /**
     * Adds a nondirected edge between two nodes in the graph.
     *
     * @param node1 the first node to connect (a {@link Node} object)
     * @param node2 the second node to connect (a {@link Node} object)
     * @return true if the edge was successfully added, false otherwise
     */
    public boolean addNondirectedEdge(Node node1, Node node2) {
        throw new UnsupportedOperationException("Disallowed for a DAG.");
    }

    /**
     * Adds a partially oriented edge between two nodes.
     *
     * @param node1 the first node to be connected
     * @param node2 the second node to be connected
     * @return true if the partially oriented edge is added successfully, false otherwise
     * @throws UnsupportedOperationException if the graph is a directed acyclic graph (DAG)
     */
    public boolean addPartiallyOrientedEdge(Node node1, Node node2) {
        throw new UnsupportedOperationException("Disallowed for a DAG.");
    }

    /**
     * Adds a directed edge to the Directed Acyclic Graph (DAG).
     *
     * @param edge the directed {@link Edge} object to be added
     * @return true if the edge is successfully added, false otherwise
     * @throws IllegalArgumentException if the provided edge is not a directed edge or adding the edge would result in a
     *                                  cycle
     */
    public boolean addEdge(Edge edge) {
        if (!Edges.isDirectedEdge(edge)) {
            throw new IllegalArgumentException("Only directed edges may be added to a DAG.");
        }

        Node x = Edges.getDirectedEdgeTail(edge);
        Node y = Edges.getDirectedEdgeHead(edge);

        if (paths().isAncestorOf(y, x)) {
            throw new IllegalArgumentException("Adding that edge would create a cycle: " + edge);
        }

        return this.graph.addEdge(edge);
    }

    /**
     * Adds a Node to the graph.
     *
     * @param node the Node object to be added
     * @return true if the Node is successfully added, false otherwise
     */
    public boolean addNode(Node node) {
        return this.graph.addNode(node);
    }

    /**
     * Adds a PropertyChangeListener to the underlying graph object.
     *
     * @param e the PropertyChangeListener to be added
     */
    public void addPropertyChangeListener(PropertyChangeListener e) {
        this.graph.addPropertyChangeListener(e);
    }

    /**
     * <p>clear.</p>
     */
    public void clear() {
        this.graph.clear();
    }

    /**
     * Checks if the given edge is present in the graph.
     *
     * @param edge the edge to check if present in the graph
     * @return true if the edge is present in the graph, false otherwise
     */
    public boolean containsEdge(Edge edge) {
        return this.graph.containsEdge(edge);
    }

    /**
     * Checks if the given Node object is contained in the graph.
     *
     * @param node The Node object to check for containment. Must not be null.
     * @return true if the Node object is contained in the graph, false otherwise.
     */
    public boolean containsNode(Node node) {
        return this.graph.containsNode(node);
    }

    /**
     * Compares this {@link Graph} object with the specified object for equality.
     *
     * @param o the object to compare this graph with
     * @return true if the specified object is equal to this graph, false otherwise
     */
    public boolean equals(Object o) {
        if (!(o instanceof Graph)) return false;
        return this.graph.equals(o);
    }

    /**
     * Fully connects the given endpoint.
     *
     * @param endpoint The endpoint to fully connect.
     * @throws UnsupportedOperationException If the endpoint is a single endpoint type and cannot be fully connected.
     */
    public void fullyConnect(Endpoint endpoint) {
        throw new UnsupportedOperationException("Cannot fully connect a DAG with a single endpoint type.");
    }

    /**
     * Reorients all edges in a Directed Acyclic Graph (DAG) with a single endpoint type.
     *
     * @param endpoint The type of endpoint to reorient all edges with. Must be an instance of
     *                 `edu.cmu.tetrad.graph.Endpoint`.
     * @throws UnsupportedOperationException if attempting to reorient all edges in a DAG with a single endpoint type.
     */
    public void reorientAllWith(Endpoint endpoint) {
        throw new UnsupportedOperationException("Cannot reorient all edges in a DAG with a single endpoint type.");
    }

    /**
     * Retrieves the adjacent nodes of a given node in the graph.
     *
     * @param node the node for which to retrieve adjacent nodes
     * @return a List of nodes that are adjacent to the given node
     */
    public List<Node> getAdjacentNodes(Node node) {
        return this.graph.getAdjacentNodes(node);
    }

    /**
     * Retrieves the children of a specified Node in the graph.
     *
     * @param node The Node object whose children are to be retrieved.
     * @return A List of Node objects representing the children of the specified node.
     */
    public List<Node> getChildren(Node node) {
        return this.graph.getChildren(node);
    }

    /**
     * <p>getDegree.</p>
     *
     * @return a int
     */
    public int getDegree() {
        return this.graph.getDegree();
    }

    /**
     * Retrieves the edge between two nodes in the graph.
     *
     * @param node1 a {@link Node} object representing the first node
     * @param node2 a {@link Node} object representing the second node
     * @return the edge between node1 and node2, or null if no such edge exists
     */
    public Edge getEdge(Node node1, Node node2) {
        return this.graph.getEdge(node1, node2);
    }

    /**
     * Returns the directed edge between the given nodes, if one exists in the graph.
     *
     * @param node1 the first Node object
     * @param node2 the second Node object
     * @return the directed edge between the given nodes, or null if no edge exists
     */
    public Edge getDirectedEdge(Node node1, Node node2) {
        return this.graph.getDirectedEdge(node1, node2);
    }

    /**
     * Returns a list of edges connected to the given node.
     *
     * @param node a {@link Node} object representing the node
     * @return a list of {@link Edge} objects connected to the node
     */
    public Set<Edge> getEdges(Node node) {
        return this.graph.getEdges(node);
    }

    /**
     * Returns a list of edges between the specified nodes in the graph.
     *
     * @param node1 the first node in the edge pair. Must not be null.
     * @param node2 the second node in the edge pair. Must not be null.
     * @return a list of edges between the specified nodes. Returns an empty list if no edges are found.
     */
    public List<Edge> getEdges(Node node1, Node node2) {
        return this.graph.getEdges(node1, node2);
    }

    /**
     * <p>getEdges.</p>
     *
     * @return a {@link java.util.Set} object
     */
    public Set<Edge> getEdges() {
        return this.graph.getEdges();
    }

    /**
     * Returns the endpoint between two nodes in the graph.
     *
     * @param node1 a Node object representing the first node
     * @param node2 a Node object representing the second node
     * @return the Endpoint object representing the endpoint between the two nodes
     */
    public Endpoint getEndpoint(Node node1, Node node2) {
        return this.graph.getEndpoint(node1, node2);
    }

    /**
     * Returns the indegree of the specified node in the graph.
     *
     * @param node the node for which to find the indegree
     * @return the indegree of the specified node
     */
    public int getIndegree(Node node) {
        return this.graph.getIndegree(node);
    }

    /**
     * Returns the degree of a given node in the graph.
     *
     * @param node the node whose degree needs to be calculated
     * @return the degree of the node
     */
    public int getDegree(Node node) {
        return this.graph.getDegree(node);
    }

    /**
     * Retrieves the node in the graph with the specified name.
     *
     * @param name a {@link String} object representing the name of the node
     * @return the {@link Node} object found in the graph with the specified name
     */
    public Node getNode(String name) {
        return this.graph.getNode(name);
    }

    /**
     * <p>getNodes.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<Node> getNodes() {
        return this.graph.getNodes();
    }

    /**
     * Set the nodes of the graph.
     *
     * @param nodes A list of Node objects representing the nodes to be set.
     */
    public void setNodes(List<Node> nodes) {
        this.graph.setNodes(nodes);
    }

    /**
     * <p>getNodeNames.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<String> getNodeNames() {
        return this.graph.getNodeNames();
    }

    /**
     * <p>getNumEdges.</p>
     *
     * @return a int
     */
    public int getNumEdges() {
        return this.graph.getNumEdges();
    }

    /**
     * Returns the number of edges connected to the specified node.
     *
     * @param node the node for which to retrieve the number of edges
     * @return the number of edges connected to the specified node
     */
    public int getNumEdges(Node node) {
        return this.graph.getNumEdges(node);
    }

    /**
     * <p>getNumNodes.</p>
     *
     * @return a int
     */
    public int getNumNodes() {
        return this.graph.getNumNodes();
    }

    /**
     * Returns the outdegree of the given node.
     *
     * @param node a {@link Node} object
     * @return the outdegree of the node
     */
    public int getOutdegree(Node node) {
        return this.graph.getOutdegree(node);
    }

    /**
     * Retrieves the list of parent nodes for a given node in the graph.
     *
     * @param node the node for which to retrieve the parent nodes
     * @return the list of parent nodes for the given node
     */
    public List<Node> getParents(Node node) {
        return this.graph.getParents(node);
    }

    /**
     * Determines whether two nodes are adjacent in the graph.
     *
     * @param node1 The first node to check adjacency.
     * @param node2 The second node to check adjacency.
     * @return true if the nodes are adjacent, false otherwise.
     */
    public boolean isAdjacentTo(Node node1, Node node2) {
        return this.graph.isAdjacentTo(node1, node2);
    }

    /**
     * Checks if the given node1 is a child of node2 in the graph.
     *
     * @param node1 the first Node object to be checked
     * @param node2 the second Node object to be checked against
     * @return true if node1 is a child of node2, false otherwise
     */
    public boolean isChildOf(Node node1, Node node2) {
        return this.graph.isChildOf(node1, node2);
    }

    /**
     * Determines if a given node is a parent of another node in the graph.
     *
     * @param node1 the first node to be compared.
     * @param node2 the second node to be compared.
     * @return true if node1 is a parent of node2, false otherwise.
     */
    public boolean isParentOf(Node node1, Node node2) {
        return this.graph.isParentOf(node1, node2);
    }

    /**
     * Checks if three given nodes form a definite non-collider in a graph.
     *
     * @param node1 the first node
     * @param node2 the second node
     * @param node3 the third node
     * @return true if the three nodes form a definite non-collider, false otherwise
     */
    public boolean isDefNoncollider(Node node1, Node node2, Node node3) {
        return this.graph.isDefNoncollider(node1, node2, node3);
    }

    /**
     * Checks if there is a definite collider between three nodes in the graph.
     *
     * @param node1 the first node to check
     * @param node2 the second node to check
     * @param node3 the third node to check
     * @return true if there is a definite collider, false otherwise
     */
    public boolean isDefCollider(Node node1, Node node2, Node node3) {
        return this.graph.isDefCollider(node1, node2, node3);
    }

    /**
     * Checks whether a given node is exogenous.
     *
     * @param node A {@link Node} object representing the node to be checked.
     * @return True if the given node is exogenous, false otherwise.
     */
    public boolean isExogenous(Node node) {
        return this.graph.isExogenous(node);
    }

    /**
     * Retrieves a list of nodes in the given graph that have edges pointing into the specified node and endpoint.
     *
     * @param node the node to check for incoming edges
     * @param n    the endpoint to check for incoming edges
     * @return a list of nodes with edges pointing into the specified node and endpoint
     */
    public List<Node> getNodesInTo(Node node, Endpoint n) {
        return this.graph.getNodesInTo(node, n);
    }

    /**
     * Retrieves a list of nodes that have outgoing edges to a specified node and endpoint.
     *
     * @param node The node to which the outgoing edges lead.
     * @param n    The endpoint to which the outgoing edges connect to the specified node.
     * @return A list of nodes that have outgoing edges to the specified node and endpoint.
     */
    public List<Node> getNodesOutTo(Node node, Endpoint n) {
        return this.graph.getNodesOutTo(node, n);
    }

    /**
     * Removes a given edge from the graph.
     *
     * @param edge the edge to be removed
     * @return true if the edge was successfully removed, false otherwise
     */
    public boolean removeEdge(Edge edge) {
        return this.graph.removeEdge(edge);
    }

    /**
     * Removes the edge between two nodes in the graph.
     *
     * @param node1 the first node to remove the edge from
     * @param node2 the second node to remove the edge to
     * @return true if the edge was successfully removed, false otherwise
     */
    public boolean removeEdge(Node node1, Node node2) {
        return this.graph.removeEdge(node1, node2);
    }

    /**
     * Removes an edge between two nodes.
     *
     * @param node1 the first node
     * @param node2 the second node
     * @return true if the edge was successfully removed, false otherwise
     */
    public boolean removeEdges(Node node1, Node node2) {
        return this.graph.removeEdges(node1, node2);
    }

    /**
     * Removes the given edges from the graph.
     *
     * @param edges a collection of edges to be removed from the graph
     * @return true if all the edges were successfully removed, false otherwise
     */
    public boolean removeEdges(Collection<Edge> edges) {
        return this.graph.removeEdges(edges);
    }

    /**
     * Removes the specified node from the graph.
     *
     * @param node the node to be removed from the graph
     * @return true if the node was successfully removed, false otherwise
     */
    public boolean removeNode(Node node) {
        return this.graph.removeNode(node);
    }

    /**
     * Removes the specified nodes from the graph.
     *
     * @param nodes a {@link List} of nodes to remove
     * @return {@code true} if the nodes were successfully removed, {@code false} otherwise
     */
    public boolean removeNodes(List<Node> nodes) {
        return this.graph.removeNodes(nodes);
    }

    /**
     * Sets the endpoint of a directed edge between two nodes in a graph.
     *
     * @param from     the starting node of the edge
     * @param to       the ending node of the edge
     * @param endPoint the endpoint of the directed edge
     * @return {@code true} if the endpoint was successfully set, {@code false} otherwise
     */
    public boolean setEndpoint(Node from, Node to, Endpoint endPoint) {
        throw new UnsupportedOperationException("Setting a single endpoint for a DAG is disallowed.");
    }

    /**
     * Returns a subgraph of the current graph consisting only of the specified nodes.
     *
     * @param nodes a list of nodes to include in the subgraph
     * @return a new {@link Graph} object representing the subgraph
     */
    public Graph subgraph(List<Node> nodes) {
        return this.graph.subgraph(nodes);
    }

    /**
     * <p>toString.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String toString() {
        return this.graph.toString();
    }

    /**
     * Transfers nodes and edges from the given graph to the current graph.
     *
     * @param graph the graph from which nodes and edges are to be pilfered
     * @throws IllegalArgumentException if the graph is null, or if adding a node/edge fails
     * @throws NullPointerException     if no graph is provided
     */
    public void transferNodesAndEdges(Graph graph) throws IllegalArgumentException {
        if (graph == null) {
            throw new NullPointerException("No graph was provided.");
        }

        for (Node node : graph.getNodes()) {
            if (!addNode(node)) {
                throw new IllegalArgumentException();
            }
        }

        for (Edge edge : graph.getEdges()) {
            if (!addEdge(edge)) {
                throw new IllegalArgumentException();
            }
        }
    }

    /**
     * Transfers attributes from the given graph to the current graph.
     *
     * @param graph a {@link Graph} object representing the graph from which attributes will be transferred
     * @throws IllegalArgumentException if the graph is null
     */
    public void transferAttributes(Graph graph) throws IllegalArgumentException {
        this.graph.transferAttributes(graph);
    }

    /**
     * <p>paths.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Paths} object
     */
    public Paths paths() {
        return this.graph.paths();
    }

    /**
     * Checks if the given node is parameterizable.
     *
     * @param node the node to be checked for parameterizability
     * @return {@code true} if the node is parameterizable, {@code false} otherwise
     */
    public boolean isParameterizable(Node node) {
        return this.graph.isParameterizable(node);
    }

    /**
     * <p>isTimeLagModel.</p>
     *
     * @return a boolean
     */
    public boolean isTimeLagModel() {
        return this.graph.isTimeLagModel();
    }

    /**
     * <p>getTimeLagGraph.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.TimeLagGraph} object
     */
    public TimeLagGraph getTimeLagGraph() {
        return this.graph.getTimeLagGraph();
    }

    /**
     * Returns the sepset between two given nodes in the graph.
     *
     * @param n1   the first node
     * @param n2   the second node
     * @param test
     * @return a set of nodes representing the sepset between n1 and n2
     */
    public Set<Node> getSepset(Node n1, Node n2, IndependenceTest test) {
        return this.graph.getSepset(n1, n2, test);
    }

    /**
     * <p>getAllAttributes.</p>
     *
     * @return a {@link java.util.Map} object
     */
    public Map<String, Object> getAllAttributes() {
        return this.graph.getAllAttributes();
    }

    /**
     * Retrieves the value associated with the given key in the attribute map.
     *
     * @param key the key of the attribute to be retrieved
     * @return the value associated with the given key
     */
    public Object getAttribute(String key) {
        return this.graph.getAttribute(key);
    }

    /**
     * Removes an attribute from the graph.
     *
     * @param key the key of the attribute to remove
     */
    public void removeAttribute(String key) {
        this.graph.removeAttribute(key);
    }

    /**
     * Adds an attribute to the graph.
     *
     * @param key   the key of the attribute
     * @param value the value of the attribute
     */
    public void addAttribute(String key, Object value) {
        this.graph.addAttribute(key, value);
    }

    /**
     * Returns a set of ambiguous triples.
     *
     * @return a set of ambiguous triples
     */
    public Set<Triple> getAmbiguousTriples() {
        return new HashSet<>(this.ambiguousTriples);
    }

    /**
     * Sets the ambiguous triples for the object.
     *
     * @param triples a set of Triple objects representing ambiguous triples
     */
    public void setAmbiguousTriples(Set<Triple> triples) {
        this.ambiguousTriples.clear();

        for (Triple triple : triples) {
            addAmbiguousTriple(triple.getX(), triple.getY(), triple.getZ());
        }
    }

    /**
     * Retrieves the set of underlined triples.
     *
     * @return The set of underlined triples as a {@code Set} of {@code Triple} objects.
     */
    public Set<Triple> getUnderLines() {
        return new HashSet<>(this.underLineTriples);
    }

    /**
     * <p>getDottedUnderlines.</p>
     *
     * @return a {@link java.util.Set} object
     */
    public Set<Triple> getDottedUnderlines() {
        return new HashSet<>(this.dottedUnderLineTriples);
    }

    /**
     * Determines if a triple of nodes is ambiguous.
     *
     * @param x the first node in the triple.
     * @param y the second node in the triple.
     * @param z the third node in the triple.
     * @return true if the triple is ambiguous, false otherwise.
     */
    public boolean isAmbiguousTriple(Node x, Node y, Node z) {
        return this.ambiguousTriples.contains(new Triple(x, y, z));
    }

    /**
     * Determines if a triple of nodes is underlined.
     *
     * @param x the first Node in the triple
     * @param y the second Node in the triple
     * @param z the third Node in the triple
     * @return true if the triple is underlined, false otherwise
     */
    public boolean isUnderlineTriple(Node x, Node y, Node z) {
        return this.underLineTriples.contains(new Triple(x, y, z));
    }

    /**
     * Adds an ambiguous triple to the list of ambiguous triples. An ambiguous triple consists of three nodes: x, y, and
     * z.
     *
     * @param x the first node in the ambiguous triple
     * @param y the second node in the ambiguous triple
     * @param z the third node in the ambiguous triple
     */
    public void addAmbiguousTriple(Node x, Node y, Node z) {
        this.ambiguousTriples.add(new Triple(x, y, z));
    }

    /**
     * Adds an underline triple to the current object.
     *
     * @param x The first {@link Node} object in the triple.
     * @param y The second {@link Node} object in the triple.
     * @param z The third {@link Node} object in the triple.
     */
    public void addUnderlineTriple(Node x, Node y, Node z) {
        Triple triple = new Triple(x, y, z);

        if (!triple.alongPathIn(this)) {
            return;
        }

        this.underLineTriples.add(new Triple(x, y, z));
    }

    /**
     * Adds a dotted underline triple to the graph.
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
     * Removes an ambiguous triple from the list of ambiguous triples.
     *
     * @param x The first node of the triple.
     * @param y The second node of the triple.
     * @param z The third node of the triple.
     */
    public void removeAmbiguousTriple(Node x, Node y, Node z) {
        this.ambiguousTriples.remove(new Triple(x, y, z));
    }

    /**
     * Removes an underline triple from the list of underline triples.
     *
     * @param x The first {@link Node} object in the underline triple.
     * @param y The second {@link Node} object in the underline triple.
     * @param z The third {@link Node} object in the underline triple.
     */
    public void removeUnderlineTriple(Node x, Node y, Node z) {
        this.underLineTriples.remove(new Triple(x, y, z));
    }

    /**
     * Removes a dotted underline triple from the set of triples.
     *
     * @param x the first node of the triple to be removed
     * @param y the second node of the triple to be removed
     * @param z the third node of the triple to be removed
     */
    public void removeDottedUnderlineTriple(Node x, Node y, Node z) {
        this.dottedUnderLineTriples.remove(new Triple(x, y, z));
    }

    /**
     * Sets the underlined triples.
     *
     * @param triples a set of triples to be set as underlined
     */
    public void setUnderLineTriples(Set<Triple> triples) {
        this.underLineTriples.clear();

        for (Triple triple : triples) {
            addUnderlineTriple(triple.getX(), triple.getY(), triple.getZ());
        }
    }

    /**
     * Sets the dotted underline triples for the given set of Triples. Clears the existing dotted underline triples and
     * adds the new ones from the set.
     *
     * @param triples a Set of Triples to set as dotted underline triples
     */
    public void setDottedUnderLineTriples(Set<Triple> triples) {
        this.dottedUnderLineTriples.clear();

        for (Triple triple : triples) {
            addDottedUnderlineTriple(triple.getX(), triple.getY(), triple.getZ());
        }
    }

    /**
     * Removes triples from the graph that contain nodes not present in the graph or are not adjacent to each other.
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





