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
 */
public final class Dag implements Graph {
    static final long serialVersionUID = 23L;

    /**
     * The wrapped graph.
     */
    private final Graph graph;

    private Set<Triple> underLineTriples = new HashSet<>();
    private Set<Triple> dottedUnderLineTriples = new HashSet<>();
    private Set<Triple> ambiguousTriples = new HashSet<>();

    //===============================CONSTRUCTORS=======================//

    /**
     * Constructs a new directed acyclic graph (DAG).
     */
    public Dag() {
        this.graph = new EdgeListGraph();
    }

    public Dag(List<Node> nodes) {
        this.graph = new EdgeListGraph(nodes);
    }

    /**
     * Constructs a new directed acyclic graph from the given graph object.
     *
     * @param graph the graph to base the new DAG on.
     * @throws IllegalArgumentException if the given graph cannot for some reason be converted into a DAG.
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

    public boolean addBidirectedEdge(Node node1, Node node2) {
        return this.graph.addBidirectedEdge(node1, node2);
    }

    public boolean addDirectedEdge(Node node1, Node node2) {
        return addEdge(Edges.directedEdge(node1, node2));
    }

    public boolean addUndirectedEdge(Node node1, Node node2) {
        throw new UnsupportedOperationException("Disallowed for a DAG.");
    }

    public boolean addNondirectedEdge(Node node1, Node node2) {
        throw new UnsupportedOperationException("Disallowed for a DAG.");
    }

    public boolean addPartiallyOrientedEdge(Node node1, Node node2) {
        throw new UnsupportedOperationException("Disallowed for a DAG.");
    }

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

    public boolean addNode(Node node) {
        return this.graph.addNode(node);
    }

    public void addPropertyChangeListener(PropertyChangeListener e) {
        this.graph.addPropertyChangeListener(e);
    }

    public void clear() {
        this.graph.clear();
    }

    public boolean containsEdge(Edge edge) {
        return this.graph.containsEdge(edge);
    }

    public boolean containsNode(Node node) {
        return this.graph.containsNode(node);
    }

    public boolean equals(Object o) {
        if (!(o instanceof Graph)) return false;
        return this.graph.equals(o);
    }

    public void fullyConnect(Endpoint endpoint) {
        throw new UnsupportedOperationException("Cannot fully connect a DAG with a single endpoint type.");
    }

    public void reorientAllWith(Endpoint endpoint) {
        throw new UnsupportedOperationException("Cannot reorient all edges in a DAG with a single endpoint type.");
    }

    public Set<Node> getAdjacentNodes(Node node) {
        return this.graph.getAdjacentNodes(node);
    }

    public Set<Node> getChildren(Node node) {
        return this.graph.getChildren(node);
    }

    public int getDegree() {
        return this.graph.getDegree();
    }

    public Edge getEdge(Node node1, Node node2) {
        return this.graph.getEdge(node1, node2);
    }

    public Edge getDirectedEdge(Node node1, Node node2) {
        return this.graph.getDirectedEdge(node1, node2);
    }

    public Set<Edge> getEdges(Node node) {
        return this.graph.getEdges(node);
    }

    public Set<Edge> getEdges(Node node1, Node node2) {
        return this.graph.getEdges(node1, node2);
    }

    public Set<Edge> getEdges() {
        return this.graph.getEdges();
    }

    public Endpoint getEndpoint(Node node1, Node node2) {
        return this.graph.getEndpoint(node1, node2);
    }

    public int getIndegree(Node node) {
        return this.graph.getIndegree(node);
    }

    public int getDegree(Node node) {
        return this.graph.getDegree(node);
    }

    public Node getNode(String name) {
        return this.graph.getNode(name);
    }

    public List<Node> getNodes() {
        return this.graph.getNodes();
    }

    public List<String> getNodeNames() {
        return this.graph.getNodeNames();
    }

    public int getNumEdges() {
        return this.graph.getNumEdges();
    }

    public int getNumEdges(Node node) {
        return this.graph.getNumEdges(node);
    }

    public int getNumNodes() {
        return this.graph.getNumNodes();
    }

    public int getOutdegree(Node node) {
        return this.graph.getOutdegree(node);
    }

    public Set<Node> getParents(Node node) {
        return this.graph.getParents(node);
    }

    public boolean isAdjacentTo(Node node1, Node node2) {
        return this.graph.isAdjacentTo(node1, node2);
    }

    public boolean isChildOf(Node node1, Node node2) {
        return this.graph.isChildOf(node1, node2);
    }

    public boolean isParentOf(Node node1, Node node2) {
        return this.graph.isParentOf(node1, node2);
    }

    public boolean isDefNoncollider(Node node1, Node node2, Node node3) {
        return this.graph.isDefNoncollider(node1, node2, node3);
    }

    public boolean isDefCollider(Node node1, Node node2, Node node3) {
        return this.graph.isDefCollider(node1, node2, node3);
    }

    public boolean isExogenous(Node node) {
        return this.graph.isExogenous(node);
    }

    public List<Node> getNodesInTo(Node node, Endpoint n) {
        return this.graph.getNodesInTo(node, n);
    }

    public List<Node> getNodesOutTo(Node node, Endpoint n) {
        return this.graph.getNodesOutTo(node, n);
    }

    public boolean removeEdge(Edge edge) {
        return this.graph.removeEdge(edge);
    }

    public boolean removeEdge(Node node1, Node node2) {
        return this.graph.removeEdge(node1, node2);
    }

    public boolean removeEdges(Node node1, Node node2) {
        return this.graph.removeEdges(node1, node2);
    }

    public boolean removeEdges(Collection<Edge> edges) {
        return this.graph.removeEdges(edges);
    }

    public boolean removeNode(Node node) {
        return this.graph.removeNode(node);
    }

    public boolean removeNodes(List<Node> nodes) {
        return this.graph.removeNodes(nodes);
    }

    public boolean setEndpoint(Node from, Node to, Endpoint endPoint) {
        throw new UnsupportedOperationException("Setting a single endpoint for a DAG is disallowed.");
    }

    public Graph subgraph(List<Node> nodes) {
        return this.graph.subgraph(nodes);
    }

    public String toString() {
        return this.graph.toString();
    }

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

    public void transferAttributes(Graph graph) throws IllegalArgumentException {
        this.graph.transferAttributes(graph);
    }

    public Paths paths() {
        return this.graph.paths();
    }

    public boolean isParameterizable(Node node) {
        return this.graph.isParameterizable(node);
    }

    public boolean isTimeLagModel() {
        return this.graph.isTimeLagModel();
    }

    public TimeLagGraph getTimeLagGraph() {
        return this.graph.getTimeLagGraph();
    }

    public Set<Node> getSepset(Node n1, Node n2) {
        return this.graph.getSepset(n1, n2);
    }

    public void setNodes(List<Node> nodes) {
        this.graph.setNodes(nodes);
    }

    public Map<String, Object> getAllAttributes() {
        return this.graph.getAllAttributes();
    }

    public Object getAttribute(String key) {
        return this.graph.getAttribute(key);
    }

    public void removeAttribute(String key) {
        this.graph.removeAttribute(key);
    }

    public void addAttribute(String key, Object value) {
        this.graph.addAttribute(key, value);
    }

    public Set<Triple> getAmbiguousTriples() {
        return new HashSet<>(this.ambiguousTriples);
    }

    public void setAmbiguousTriples(Set<Triple> triples) {
        this.ambiguousTriples.clear();

        for (Triple triple : triples) {
            addAmbiguousTriple(triple.getX(), triple.getY(), triple.getZ());
        }
    }

    public Set<Triple> getUnderLines() {
        return new HashSet<>(this.underLineTriples);
    }

    public Set<Triple> getDottedUnderlines() {
        return new HashSet<>(this.dottedUnderLineTriples);
    }

    /**
     * States whether r-s-r is an underline triple or not.
     */
    public boolean isAmbiguousTriple(Node x, Node y, Node z) {
        return this.ambiguousTriples.contains(new Triple(x, y, z));
    }

    /**
     * States whether r-s-r is an underline triple or not.
     */
    public boolean isUnderlineTriple(Node x, Node y, Node z) {
        return this.underLineTriples.contains(new Triple(x, y, z));
    }

    public void addAmbiguousTriple(Node x, Node y, Node z) {
        this.ambiguousTriples.add(new Triple(x, y, z));
    }

    public void addUnderlineTriple(Node x, Node y, Node z) {
        Triple triple = new Triple(x, y, z);

        if (!triple.alongPathIn(this)) {
            return;
        }

        this.underLineTriples.add(new Triple(x, y, z));
    }

    public void addDottedUnderlineTriple(Node x, Node y, Node z) {
        Triple triple = new Triple(x, y, z);

        if (!triple.alongPathIn(this)) {
            return;
        }

        this.dottedUnderLineTriples.add(triple);
    }

    public void removeAmbiguousTriple(Node x, Node y, Node z) {
        this.ambiguousTriples.remove(new Triple(x, y, z));
    }

    public void removeUnderlineTriple(Node x, Node y, Node z) {
        this.underLineTriples.remove(new Triple(x, y, z));
    }

    public void removeDottedUnderlineTriple(Node x, Node y, Node z) {
        this.dottedUnderLineTriples.remove(new Triple(x, y, z));
    }

    public void setUnderLineTriples(Set<Triple> triples) {
        this.underLineTriples.clear();

        for (Triple triple : triples) {
            addUnderlineTriple(triple.getX(), triple.getY(), triple.getZ());
        }
    }

    public void setDottedUnderLineTriples(Set<Triple> triples) {
        this.dottedUnderLineTriples.clear();

        for (Triple triple : triples) {
            addDottedUnderlineTriple(triple.getX(), triple.getY(), triple.getZ());
        }
    }

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





