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
     * Adds a bidirectional edge between two nodes to the graph.
     *
     * @param node1 the first node to connect
     * @param node2 the second node to connect
     * @return true if the edge was successfully added, false otherwise
     */
    public boolean addBidirectedEdge(Node node1, Node node2) {
        return this.graph.addBidirectedEdge(node1, node2);
    }

    /**
     * Adds a directed edge between two nodes.
     *
     * @param node1 the first node to be connected (source node)
     * @param node2 the second node to be connected (target node)
     * @return true if the directed edge was successfully added, false otherwise
     */
    public boolean addDirectedEdge(Node node1, Node node2) {
        return addEdge(Edges.directedEdge(node1, node2));
    }

    /**
     * Adds an undirected edge between two nodes.
     *
     * @param node1 The first node involved in the edge.
     * @param node2 The second node involved in the edge.
     * @return True if the edge was successfully added, false otherwise.
     * @throws UnsupportedOperationException if the operation is disallowed for a Directed Acyclic Graph (DAG).
     */
    public boolean addUndirectedEdge(Node node1, Node node2) {
        throw new UnsupportedOperationException("Disallowed for a DAG.");
    }

    /**
     * Adds a nondirected edge between two nodes in the graph.
     *
     * @param node1 The first node to connect.
     * @param node2 The second node to connect.
     * @return Returns true if the nondirected edge was added successfully.
     * @throws UnsupportedOperationException Thrown if the graph is a directed acyclic graph (DAG).
     */
    public boolean addNondirectedEdge(Node node1, Node node2) {
        throw new UnsupportedOperationException("Disallowed for a DAG.");
    }

    /**
     * Adds a partially oriented edge between two nodes.
     *
     * @param node1 The first node involved in the edge. Must be a {@link Node} object.
     * @param node2 The second node involved in the edge. Must be a {@link Node} object.
     * @return True if the edge was added successfully, false otherwise.
     * @throws UnsupportedOperationException If the graph is a Directed Acyclic Graph (DAG), this operation is not allowed.
     */
    public boolean addPartiallyOrientedEdge(Node node1, Node node2) {
        throw new UnsupportedOperationException("Disallowed for a DAG.");
    }

    /**
     * Adds a directed edge to the DAG (Directed Acyclic Graph).
     *
     * @param edge The Edge object to be added.
     * @return Returns true if the edge was successfully added to the DAG, false otherwise.
     * @throws IllegalArgumentException if the given edge is not a directed edge.
     * @throws IllegalArgumentException if adding the edge would create a cycle in the DAG.
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
     * Adds a node to the graph.
     *
     * @param node - The node to be added to the graph.
     * @return true if the node was successfully added to the graph, false otherwise.
     */
    public boolean addNode(Node node) {
        return this.graph.addNode(node);
    }

    /**
     * Adds a PropertyChangeListener to this object.
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
     * Checks if the graph contains the specified edge.
     *
     * @param edge the edge to be checked.
     * @return true if the graph contains the edge, false otherwise.
     */
    public boolean containsEdge(Edge edge) {
        return this.graph.containsEdge(edge);
    }

    /**
     * Checks if the specified node is contained in the graph.
     *
     * @param node the node to check if contained in the graph
     * @return true if the node is contained in the graph, false otherwise
     */
    public boolean containsNode(Node node) {
        return this.graph.containsNode(node);
    }

    /**
     * Compares this graph with the specified object for equality.
     *
     * @param o the object to be compared with this graph
     * @return true if the specified object is equal to this graph, false otherwise
     */
    public boolean equals(Object o) {
        if (!(o instanceof Graph)) return false;
        return this.graph.equals(o);
    }

    /**
     * This method fully connects a Directed Acyclic Graph (DAG) with a single endpoint type.
     *
     * @param endpoint the endpoint to be fully connected
     * @throws UnsupportedOperationException if the DAG has a single endpoint type
     */
    public void fullyConnect(Endpoint endpoint) {
        throw new UnsupportedOperationException("Cannot fully connect a DAG with a single endpoint type.");
    }

    /**
     * Reorients all edges in a directed acyclic graph (DAG) with a single specified endpoint type.
     *
     * @param endpoint the endpoint type to reorient the edges with.
     *                 It should be a valid {@link Endpoint} object.
     * @throws UnsupportedOperationException if the DAG does not have a single endpoint type, i.e., it is not a DAG.
     */
    public void reorientAllWith(Endpoint endpoint) {
        throw new UnsupportedOperationException("Cannot reorient all edges in a DAG with a single endpoint type.");
    }

    /**
     * Retrieves the adjacent nodes of a given node in the graph.
     *
     * @param node The node for which to retrieve the adjacent nodes.
     * @return A list of adjacent nodes to the given node.
     */
    public List<Node> getAdjacentNodes(Node node) {
        return this.graph.getAdjacentNodes(node);
    }

    /**
     * Returns a list of children nodes for the given node.
     *
     * @param node The node for which to retrieve the children.
     * @return A list of children nodes.
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
     * Retrieves the edge between two given nodes in the graph.
     *
     * @param node1 The first node {@link Node} in the edge.
     * @param node2 The second node {@link Node} in the edge.
     * @return The {@link Edge} between the given nodes.
     */
    public Edge getEdge(Node node1, Node node2) {
        return this.graph.getEdge(node1, node2);
    }

    /**
     * Retrieves the directed edge between two nodes in the graph.
     *
     * @param node1 The first node.
     * @param node2 The second node.
     * @return The directed edge between the two nodes, or null if there is no such edge.
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
    public List<Edge> getEdges(Node node) {
        return this.graph.getEdges(node);
    }

    /**
     * Returns a list of edges between two nodes.
     *
     * @param node1 the first node
     * @param node2 the second node
     * @return a list of edges between the two nodes
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
     * Gets the endpoint between two nodes in the graph.
     *
     * @param node1 the first node
     * @param node2 the second node
     * @return the endpoint between the two nodes
     */
    public Endpoint getEndpoint(Node node1, Node node2) {
        return this.graph.getEndpoint(node1, node2);
    }

    /**
     * Returns the indegree of the given node in the graph.
     *
     * @param node the node whose indegree is to be determined
     * @return the indegree of the given node
     */
    public int getIndegree(Node node) {
        return this.graph.getIndegree(node);
    }

    /**
     * Returns the degree of a given Node in the graph.
     *
     * @param node a {@link Node} object representing the node
     * @return an integer representing the degree of the node
     */
    public int getDegree(Node node) {
        return this.graph.getDegree(node);
    }

    /**
     * Retrieves a node from the graph by its name.
     *
     * @param name the name of the node to be retrieved
     * @return the node object, or null if not found
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
     * Sets the list of nodes in the graph.
     *
     * @param nodes a list of nodes to be set in the graph. Must not be null.
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
     * @param node a {@link Node} object representing the node of interest
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
     * Returns the outdegree of a given node.
     *
     * @param node a {@link Node} object representing the node whose outdegree is to be retrieved
     * @return an integer value representing the outdegree of the node
     */
    public int getOutdegree(Node node) {
        return this.graph.getOutdegree(node);
    }

    /**
     * Retrieves the parents of a given Node in the graph.
     *
     * @param node The Node for which to retrieve the parents.
     * @return A List of Nodes representing the parents.
     */
    public List<Node> getParents(Node node) {
        return this.graph.getParents(node);
    }

    /**
     * Checks if two nodes are adjacent in the graph.
     *
     * @param node1 The first node to check adjacency for. (NonNull)
     * @param node2 The second node to check adjacency for. (NonNull)
     * @return true if the nodes are adjacent, false otherwise.
     */
    public boolean isAdjacentTo(Node node1, Node node2) {
        return this.graph.isAdjacentTo(node1, node2);
    }

    /**
     * {@inheritDoc}
     */
    public boolean isChildOf(Node node1, Node node2) {
        return this.graph.isChildOf(node1, node2);
    }

    /**
     * {@inheritDoc}
     */
    public boolean isParentOf(Node node1, Node node2) {
        return this.graph.isParentOf(node1, node2);
    }

    /**
     * {@inheritDoc}
     */
    public boolean isDefNoncollider(Node node1, Node node2, Node node3) {
        return this.graph.isDefNoncollider(node1, node2, node3);
    }

    /**
     * {@inheritDoc}
     */
    public boolean isDefCollider(Node node1, Node node2, Node node3) {
        return this.graph.isDefCollider(node1, node2, node3);
    }

    /**
     * {@inheritDoc}
     */
    public boolean isExogenous(Node node) {
        return this.graph.isExogenous(node);
    }

    /**
     * {@inheritDoc}
     */
    public List<Node> getNodesInTo(Node node, Endpoint n) {
        return this.graph.getNodesInTo(node, n);
    }

    /**
     * {@inheritDoc}
     */
    public List<Node> getNodesOutTo(Node node, Endpoint n) {
        return this.graph.getNodesOutTo(node, n);
    }

    /**
     * {@inheritDoc}
     */
    public boolean removeEdge(Edge edge) {
        return this.graph.removeEdge(edge);
    }

    /**
     * {@inheritDoc}
     */
    public boolean removeEdge(Node node1, Node node2) {
        return this.graph.removeEdge(node1, node2);
    }

    /**
     * {@inheritDoc}
     */
    public boolean removeEdges(Node node1, Node node2) {
        return this.graph.removeEdges(node1, node2);
    }

    /**
     * {@inheritDoc}
     */
    public boolean removeEdges(Collection<Edge> edges) {
        return this.graph.removeEdges(edges);
    }

    /**
     * {@inheritDoc}
     */
    public boolean removeNode(Node node) {
        return this.graph.removeNode(node);
    }

    /**
     * {@inheritDoc}
     */
    public boolean removeNodes(List<Node> nodes) {
        return this.graph.removeNodes(nodes);
    }

    /**
     * {@inheritDoc}
     */
    public boolean setEndpoint(Node from, Node to, Endpoint endPoint) {
        throw new UnsupportedOperationException("Setting a single endpoint for a DAG is disallowed.");
    }

    /**
     * {@inheritDoc}
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
     * {@inheritDoc}
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
     * {@inheritDoc}
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
     * {@inheritDoc}
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
     * {@inheritDoc}
     */
    public Set<Node> getSepset(Node n1, Node n2) {
        return this.graph.getSepset(n1, n2);
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
     * {@inheritDoc}
     */
    public Object getAttribute(String key) {
        return this.graph.getAttribute(key);
    }

    /**
     * {@inheritDoc}
     */
    public void removeAttribute(String key) {
        this.graph.removeAttribute(key);
    }

    /**
     * {@inheritDoc}
     */
    public void addAttribute(String key, Object value) {
        this.graph.addAttribute(key, value);
    }

    /**
     * <p>Getter for the field <code>ambiguousTriples</code>.</p>
     *
     * @return a {@link java.util.Set} object
     */
    public Set<Triple> getAmbiguousTriples() {
        return new HashSet<>(this.ambiguousTriples);
    }

    /**
     * {@inheritDoc}
     */
    public void setAmbiguousTriples(Set<Triple> triples) {
        this.ambiguousTriples.clear();

        for (Triple triple : triples) {
            addAmbiguousTriple(triple.getX(), triple.getY(), triple.getZ());
        }
    }

    /**
     * <p>getUnderLines.</p>
     *
     * @return a {@link java.util.Set} object
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
     * {@inheritDoc}
     * <p>
     * States whether r-s-r is an underline triple or not.
     */
    public boolean isAmbiguousTriple(Node x, Node y, Node z) {
        return this.ambiguousTriples.contains(new Triple(x, y, z));
    }

    /**
     * {@inheritDoc}
     * <p>
     * States whether r-s-r is an underline triple or not.
     */
    public boolean isUnderlineTriple(Node x, Node y, Node z) {
        return this.underLineTriples.contains(new Triple(x, y, z));
    }

    /**
     * {@inheritDoc}
     */
    public void addAmbiguousTriple(Node x, Node y, Node z) {
        this.ambiguousTriples.add(new Triple(x, y, z));
    }

    /**
     * {@inheritDoc}
     */
    public void addUnderlineTriple(Node x, Node y, Node z) {
        Triple triple = new Triple(x, y, z);

        if (!triple.alongPathIn(this)) {
            return;
        }

        this.underLineTriples.add(new Triple(x, y, z));
    }

    /**
     * {@inheritDoc}
     */
    public void addDottedUnderlineTriple(Node x, Node y, Node z) {
        Triple triple = new Triple(x, y, z);

        if (!triple.alongPathIn(this)) {
            return;
        }

        this.dottedUnderLineTriples.add(triple);
    }

    /**
     * {@inheritDoc}
     */
    public void removeAmbiguousTriple(Node x, Node y, Node z) {
        this.ambiguousTriples.remove(new Triple(x, y, z));
    }

    /**
     * {@inheritDoc}
     */
    public void removeUnderlineTriple(Node x, Node y, Node z) {
        this.underLineTriples.remove(new Triple(x, y, z));
    }

    /**
     * {@inheritDoc}
     */
    public void removeDottedUnderlineTriple(Node x, Node y, Node z) {
        this.dottedUnderLineTriples.remove(new Triple(x, y, z));
    }

    /**
     * {@inheritDoc}
     */
    public void setUnderLineTriples(Set<Triple> triples) {
        this.underLineTriples.clear();

        for (Triple triple : triples) {
            addUnderlineTriple(triple.getX(), triple.getY(), triple.getZ());
        }
    }

    /**
     * {@inheritDoc}
     */
    public void setDottedUnderLineTriples(Set<Triple> triples) {
        this.dottedUnderLineTriples.clear();

        for (Triple triple : triples) {
            addDottedUnderlineTriple(triple.getX(), triple.getY(), triple.getZ());
        }
    }

    /**
     * <p>removeTriplesNotInGraph.</p>
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





