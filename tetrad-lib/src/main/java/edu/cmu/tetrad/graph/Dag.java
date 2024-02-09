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
import java.util.*;

/**
 * Represents a directed acyclic graph--that is, a graph containing only directed edges, with no cycles. Variables are
 * permitted to be either measured or latent, with at most one edge per node pair, and no edges to self.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public final class Dag implements Graph {
    private static final long serialVersionUID = 23L;
    private final Graph graph;
    private final Set<Triple> underLineTriples = new HashSet<>();
    private final Set<Triple> dottedUnderLineTriples = new HashSet<>();
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
     */
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();
    }

    /** {@inheritDoc} */
    public boolean addBidirectedEdge(Node node1, Node node2) {
        return this.graph.addBidirectedEdge(node1, node2);
    }

    /** {@inheritDoc} */
    public boolean addDirectedEdge(Node node1, Node node2) {
        return addEdge(Edges.directedEdge(node1, node2));
    }

    /** {@inheritDoc} */
    public boolean addUndirectedEdge(Node node1, Node node2) {
        throw new UnsupportedOperationException("Disallowed for a DAG.");
    }

    /** {@inheritDoc} */
    public boolean addNondirectedEdge(Node node1, Node node2) {
        throw new UnsupportedOperationException("Disallowed for a DAG.");
    }

    /** {@inheritDoc} */
    public boolean addPartiallyOrientedEdge(Node node1, Node node2) {
        throw new UnsupportedOperationException("Disallowed for a DAG.");
    }

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
    public boolean addNode(Node node) {
        return this.graph.addNode(node);
    }

    /** {@inheritDoc} */
    public void addPropertyChangeListener(PropertyChangeListener e) {
        this.graph.addPropertyChangeListener(e);
    }

    /**
     * <p>clear.</p>
     */
    public void clear() {
        this.graph.clear();
    }

    /** {@inheritDoc} */
    public boolean containsEdge(Edge edge) {
        return this.graph.containsEdge(edge);
    }

    /** {@inheritDoc} */
    public boolean containsNode(Node node) {
        return this.graph.containsNode(node);
    }

    /** {@inheritDoc} */
    public boolean equals(Object o) {
        if (!(o instanceof Graph)) return false;
        return this.graph.equals(o);
    }

    /** {@inheritDoc} */
    public void fullyConnect(Endpoint endpoint) {
        throw new UnsupportedOperationException("Cannot fully connect a DAG with a single endpoint type.");
    }

    /** {@inheritDoc} */
    public void reorientAllWith(Endpoint endpoint) {
        throw new UnsupportedOperationException("Cannot reorient all edges in a DAG with a single endpoint type.");
    }

    /** {@inheritDoc} */
    public List<Node> getAdjacentNodes(Node node) {
        return this.graph.getAdjacentNodes(node);
    }

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
    public Edge getEdge(Node node1, Node node2) {
        return this.graph.getEdge(node1, node2);
    }

    /** {@inheritDoc} */
    public Edge getDirectedEdge(Node node1, Node node2) {
        return this.graph.getDirectedEdge(node1, node2);
    }

    /** {@inheritDoc} */
    public List<Edge> getEdges(Node node) {
        return this.graph.getEdges(node);
    }

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
    public Endpoint getEndpoint(Node node1, Node node2) {
        return this.graph.getEndpoint(node1, node2);
    }

    /** {@inheritDoc} */
    public int getIndegree(Node node) {
        return this.graph.getIndegree(node);
    }

    /** {@inheritDoc} */
    public int getDegree(Node node) {
        return this.graph.getDegree(node);
    }

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
    public int getOutdegree(Node node) {
        return this.graph.getOutdegree(node);
    }

    /** {@inheritDoc} */
    public List<Node> getParents(Node node) {
        return this.graph.getParents(node);
    }

    /** {@inheritDoc} */
    public boolean isAdjacentTo(Node node1, Node node2) {
        return this.graph.isAdjacentTo(node1, node2);
    }

    /** {@inheritDoc} */
    public boolean isChildOf(Node node1, Node node2) {
        return this.graph.isChildOf(node1, node2);
    }

    /** {@inheritDoc} */
    public boolean isParentOf(Node node1, Node node2) {
        return this.graph.isParentOf(node1, node2);
    }

    /** {@inheritDoc} */
    public boolean isDefNoncollider(Node node1, Node node2, Node node3) {
        return this.graph.isDefNoncollider(node1, node2, node3);
    }

    /** {@inheritDoc} */
    public boolean isDefCollider(Node node1, Node node2, Node node3) {
        return this.graph.isDefCollider(node1, node2, node3);
    }

    /** {@inheritDoc} */
    public boolean isExogenous(Node node) {
        return this.graph.isExogenous(node);
    }

    /** {@inheritDoc} */
    public List<Node> getNodesInTo(Node node, Endpoint n) {
        return this.graph.getNodesInTo(node, n);
    }

    /** {@inheritDoc} */
    public List<Node> getNodesOutTo(Node node, Endpoint n) {
        return this.graph.getNodesOutTo(node, n);
    }

    /** {@inheritDoc} */
    public boolean removeEdge(Edge edge) {
        return this.graph.removeEdge(edge);
    }

    /** {@inheritDoc} */
    public boolean removeEdge(Node node1, Node node2) {
        return this.graph.removeEdge(node1, node2);
    }

    /** {@inheritDoc} */
    public boolean removeEdges(Node node1, Node node2) {
        return this.graph.removeEdges(node1, node2);
    }

    /** {@inheritDoc} */
    public boolean removeEdges(Collection<Edge> edges) {
        return this.graph.removeEdges(edges);
    }

    /** {@inheritDoc} */
    public boolean removeNode(Node node) {
        return this.graph.removeNode(node);
    }

    /** {@inheritDoc} */
    public boolean removeNodes(List<Node> nodes) {
        return this.graph.removeNodes(nodes);
    }

    /** {@inheritDoc} */
    public boolean setEndpoint(Node from, Node to, Endpoint endPoint) {
        throw new UnsupportedOperationException("Setting a single endpoint for a DAG is disallowed.");
    }

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
    public Object getAttribute(String key) {
        return this.graph.getAttribute(key);
    }

    /** {@inheritDoc} */
    public void removeAttribute(String key) {
        this.graph.removeAttribute(key);
    }

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
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
     *
     * States whether r-s-r is an underline triple or not.
     */
    public boolean isAmbiguousTriple(Node x, Node y, Node z) {
        return this.ambiguousTriples.contains(new Triple(x, y, z));
    }

    /**
     * {@inheritDoc}
     *
     * States whether r-s-r is an underline triple or not.
     */
    public boolean isUnderlineTriple(Node x, Node y, Node z) {
        return this.underLineTriples.contains(new Triple(x, y, z));
    }

    /** {@inheritDoc} */
    public void addAmbiguousTriple(Node x, Node y, Node z) {
        this.ambiguousTriples.add(new Triple(x, y, z));
    }

    /** {@inheritDoc} */
    public void addUnderlineTriple(Node x, Node y, Node z) {
        Triple triple = new Triple(x, y, z);

        if (!triple.alongPathIn(this)) {
            return;
        }

        this.underLineTriples.add(new Triple(x, y, z));
    }

    /** {@inheritDoc} */
    public void addDottedUnderlineTriple(Node x, Node y, Node z) {
        Triple triple = new Triple(x, y, z);

        if (!triple.alongPathIn(this)) {
            return;
        }

        this.dottedUnderLineTriples.add(triple);
    }

    /** {@inheritDoc} */
    public void removeAmbiguousTriple(Node x, Node y, Node z) {
        this.ambiguousTriples.remove(new Triple(x, y, z));
    }

    /** {@inheritDoc} */
    public void removeUnderlineTriple(Node x, Node y, Node z) {
        this.underLineTriples.remove(new Triple(x, y, z));
    }

    /** {@inheritDoc} */
    public void removeDottedUnderlineTriple(Node x, Node y, Node z) {
        this.dottedUnderLineTriples.remove(new Triple(x, y, z));
    }

    /** {@inheritDoc} */
    public void setUnderLineTriples(Set<Triple> triples) {
        this.underLineTriples.clear();

        for (Triple triple : triples) {
            addUnderlineTriple(triple.getX(), triple.getY(), triple.getZ());
        }
    }

    /** {@inheritDoc} */
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





