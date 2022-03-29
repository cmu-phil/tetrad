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

import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.*;

/**
 * Represents a directed acyclic graph--that is, a graph containing only
 * directed edges, with no cycles. Variables are permitted to be either measured
 * or latent, with at most one edge per node pair, and no edges to self.
 *
 * @author Joseph Ramsey
 */
public final class Dag implements Graph {
    static final long serialVersionUID = 23L;

    /**
     * The wrapped graph.
     */
    private final Graph graph;

    /**
     * A dpath matrix for the DAG. If used, it is updated (where necessary) each
     * time the getDirectedPath method is called with whatever edges are stored
     * in the dpathNewEdges list. New edges that are added are appended to the
     * dpathNewEdges list. When edges are removed and when nodes are added or
     * removed, dpath is set to null.
     */
    private transient byte[][] dpath;

    /**
     * New edges that need to be added to the dpath matrix.
     */
    private transient LinkedList<Edge> dpathNewEdges = new LinkedList<>();

    /**
     * The order of nodes used for dpath.
     */
    private transient List<Node> dpathNodes;

    private Map<Node, Integer> nodesHash = new HashMap<>();

    private boolean pag;
    private boolean CPDAG;

    private final Map<String, Object> attributes = new HashMap<>();

    //===============================CONSTRUCTORS=======================//

    /**
     * Constructs a new directed acyclic graph (DAG).
     */
    public Dag() {

        // Must use EdgeListGraph because property change events are correctly implemeted. Don't change it!
        // unless you fix that or the interface will break the interface! jdramsey 2015-6-5
        this.graph = new EdgeListGraph();

        reconstituteDpath();
    }

    public Dag(final List<Node> nodes) {
        this.graph = new EdgeListGraph(nodes);
        reconstituteDpath();
    }

    /**
     * Constructs a new directed acyclic graph from the given graph object.
     *
     * @param graph the graph to base the new DAG on.
     * @throws IllegalArgumentException if the given graph cannot for some
     *                                  reason be converted into a DAG.
     */
    public Dag(final Graph graph) throws IllegalArgumentException {
        if (graph.existsDirectedCycle()) {
            throw new IllegalArgumentException("That graph was not acyclic.");
        }

        this.graph = new EdgeListGraph();

        transferNodesAndEdges(graph);

        for (final Node node : this.graph.getNodes()) {
            node.getAllAttributes().clear();
        }

        resetDPath();
        reconstituteDpath();

        for (final Edge edge : graph.getEdges()) {
            if (graph.isHighlighted(edge)) {
                setHighlighted(edge, true);
            }
        }
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static Dag serializableInstance() {
        final Dag dag = new Dag();
        final GraphNode node1 = new GraphNode("X");
        dag.addNode(node1);
        return dag;
    }

    //===============================PUBLIC METHODS======================//

    public boolean addBidirectedEdge(final Node node1, final Node node2) {
        throw new UnsupportedOperationException();
    }

    public boolean addEdge(final Edge edge) {
        reconstituteDpath();
        final Node _node1 = Edges.getDirectedEdgeTail(edge);
        final Node _node2 = Edges.getDirectedEdgeHead(edge);

        final int i = this.dpathNodes.indexOf(_node1);
        final int j = this.dpathNodes.indexOf(_node2);

        if (this.dpath[j][i] == 1) {
            return false;
        }

        adjustDPath(i, j);

        final boolean added = getGraph().addEdge(edge);

        if (added) {
            dpathNewEdges().add(edge);
        }

        return added;
    }

    public boolean addDirectedEdge(final Node node1, final Node node2) {
        return addEdge(Edges.directedEdge(node1, node2));
    }

    public boolean addPartiallyOrientedEdge(final Node node1, final Node node2) {
        throw new UnsupportedOperationException();
    }

    public boolean addNode(final Node node) {
        final boolean added = getGraph().addNode(node);

        if (added) {
            resetDPath();
            reconstituteDpath();
        }

        return added;
    }

    public void addPropertyChangeListener(final PropertyChangeListener l) {
        getGraph().addPropertyChangeListener(l);
    }

    public boolean addUndirectedEdge(final Node node1, final Node node2) {
        throw new UnsupportedOperationException();
    }

    public boolean addNondirectedEdge(final Node node1, final Node node2) {
        throw new UnsupportedOperationException();
    }

    public void clear() {
        getGraph().clear();
    }

    public boolean containsEdge(final Edge edge) {
        return getGraph().containsEdge(edge);
    }

    public boolean containsNode(final Node node) {
        return getGraph().containsNode(node);
    }

    public boolean defNonDescendent(final Node node1, final Node node2) {
        return getGraph().defNonDescendent(node1, node2);
    }

    public boolean existsDirectedCycle() {
        return false;
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

    public boolean equals(final Object o) {
        return o instanceof Dag && getGraph().equals(o);
    }

    public boolean existsDirectedPathFromTo(final Node node1, final Node node2) {
//        resetDPath();
//        reconstituteDpath();

//        node1 = graph.getNode(node1.getNode());
//        node2 = graph.getNode(node2.getNode());

        //System.out.println(MatrixUtils.toString(dpath));


        final int index1 = this.nodesHash.get(node1);
        final int index2 = this.nodesHash.get(node2);

//        int index1 = dpathNodes.indexOf(node1);
//        int index2 = dpathNodes.indexOf(node2);

        return this.dpath[index1][index2] == 1;
    }

    @Override
    public List<Node> findCycle() {
        return getGraph().findCycle();
    }

    public boolean existsUndirectedPathFromTo(final Node node1, final Node node2) {
        return false;
    }


    public boolean existsSemiDirectedPathFromTo(final Node node1, final Set<Node> nodes) {
        return getGraph().existsSemiDirectedPathFromTo(node1, nodes);
    }

    public boolean existsInducingPath(final Node node1, final Node node2) {
        return getGraph().existsInducingPath(node1, node2);
    }

    public void fullyConnect(final Endpoint endpoint) {
        throw new UnsupportedOperationException();
        //graph.fullyConnect(endpoint);
    }

    public Endpoint getEndpoint(final Node node1, final Node node2) {
        return getGraph().getEndpoint(node1, node2);
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

    /**
     * This method returns the nodes of a digraph in such an order that as one
     * iterates through the list, the parents of each node have already been
     * encountered in the list.
     *
     * @return a tier ordering for the nodes in this graph.
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

    public boolean isTimeLagModel() {
        return getGraph().isTimeLagModel();
    }

    public TimeLagGraph getTimeLagGraph() {
        return getGraph().getTimeLagGraph();
    }

    @Override
    public void removeTriplesNotInGraph() {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Node> getSepset(final Node n1, final Node n2) {
//        return graph.getSepset(n1, n2);
        return GraphUtils.getSepset(n1, n2, this);
    }

    @Override
    public void setNodes(final List<Node> nodes) {
        this.graph.setNodes(nodes);
    }

    public boolean isAdjacentTo(final Node nodeX, final Node nodeY) {
        return getGraph().isAdjacentTo(nodeX, nodeY);
    }

    public boolean isAncestorOf(final Node node1, final Node node2) {
        return node1 == node2 || GraphUtils.existsDirectedPathFromTo(node1, node2, this);
    }

    public boolean isDirectedFromTo(final Node node1, final Node node2) {
        return getGraph().isDirectedFromTo(node1, node2);
    }

    public boolean isUndirectedFromTo(final Node node1, final Node node2) {
        return false;
    }

    public boolean isParentOf(final Node node1, final Node node2) {
        return getGraph().isParentOf(node1, node2);
    }

    public boolean isProperAncestorOf(final Node node1, final Node node2) {
        return node1 != node2 && isAncestorOf(node1, node2);
    }

    public boolean isProperDescendentOf(final Node node1, final Node node2) {
        return node1 != node2 && isDescendentOf(node1, node2);
    }

    public boolean isExogenous(final Node node) {
        return getGraph().isExogenous(node);
    }

    public boolean isDConnectedTo(final Node node1, final Node node2,
                                  final List<Node> conditioningNodes) {
        return getGraph().isDConnectedTo(node1, node2, conditioningNodes);
    }

    public boolean isDSeparatedFrom(final Node node1, final Node node2, final List<Node> z) {
        return getGraph().isDSeparatedFrom(node1, node2, z);
    }

    public boolean isChildOf(final Node node1, final Node node2) {
        return getGraph().isChildOf(node1, node2);
    }

    public boolean isDescendentOf(final Node node1, final Node node2) {
        return node1 == node2 || GraphUtils.existsDirectedPathFromTo(node2, node1, this);
    }

    public boolean removeEdge(final Node node1, final Node node2) {
        final boolean removed = getGraph().removeEdge(node1, node2);

        if (removed) {
            resetDPath();
            reconstituteDpath();
        }

        return removed;
    }

    public boolean removeEdges(final Node node1, final Node node2) {
        final boolean removed = getGraph().removeEdges(node1, node2);

        if (removed) {
            resetDPath();
            reconstituteDpath();
        }

        return removed;
    }

    public boolean setEndpoint(final Node node1, final Node node2, final Endpoint endpoint) {
        final boolean ret = getGraph().setEndpoint(node1, node2, endpoint);

        resetDPath();
        reconstituteDpath();

        return ret;
    }

    public Graph subgraph(final List<Node> nodes) {
        return getGraph().subgraph(nodes);
    }

    public boolean removeEdge(final Edge edge) {
        final boolean removed = getGraph().removeEdge(edge);
        resetDPath();
        reconstituteDpath();
        return removed;
    }

    public boolean removeEdges(final Collection<Edge> edges) {
        boolean change = false;

        for (final Edge edge : edges) {
            final boolean _change = removeEdge(edge);
            change = change || _change;
        }

        return change;

        //return graph.removeEdges(edges);
    }

    public boolean removeNode(final Node node) {
        final boolean removed = getGraph().removeNode(node);

        if (removed) {
            resetDPath();
            reconstituteDpath();
        }

        return removed;
    }

    public boolean removeNodes(final List<Node> nodes) {
        return getGraph().removeNodes(nodes);
    }

    public void reorientAllWith(final Endpoint endpoint) {
        throw new UnsupportedOperationException();
        //graph.reorientAllWith(endpoint);
    }

    public boolean possibleAncestor(final Node node1, final Node node2) {
        return getGraph().possibleAncestor(node1, node2);
    }

    public List<Node> getAncestors(final List<Node> nodes) {
        return getGraph().getAncestors(nodes);
    }

    public boolean possDConnectedTo(final Node node1, final Node node2, final List<Node> z) {
        return getGraph().possDConnectedTo(node1, node2, z);
    }

    private void resetDPath() {
        this.dpath = null;
        dpathNewEdges().clear();
        dpathNewEdges().addAll(getEdges());
    }

    private void reconstituteDpath() {
        if (this.dpath == null) {
            this.dpathNodes = getNodes();
            final int numNodes = this.dpathNodes.size();
            this.dpath = new byte[numNodes][numNodes];
        }

        while (!dpathNewEdges().isEmpty()) {
            final Edge edge = dpathNewEdges().removeFirst();
            final Node _node1 = Edges.getDirectedEdgeTail(edge);
            final Node _node2 = Edges.getDirectedEdgeHead(edge);
            final int i = this.dpathNodes.indexOf(_node1);
            final int j = this.dpathNodes.indexOf(_node2);
            adjustDPath(i, j);
        }

        this.nodesHash = new HashMap<>();

        for (int i = 0; i < this.dpathNodes.size(); i++) {
            this.nodesHash.put(this.dpathNodes.get(i), i);
        }
    }

    private void adjustDPath(final int i, final int j) {
        this.dpath[i][j] = 1;

        for (int k = 0; k < this.dpathNodes.size(); k++) {
            if (this.dpath[k][i] == 1) {
                this.dpath[k][j] = 1;
            }

            if (this.dpath[j][k] == 1) {
                this.dpath[i][k] = 1;
            }
        }
    }

    public final void transferNodesAndEdges(final Graph graph)
            throws IllegalArgumentException {
        this.getGraph().transferNodesAndEdges(graph);
        for (final Node node : this.getGraph().getNodes()) {
            node.getAllAttributes().clear();
        }
    }

    public final void transferAttributes(final Graph graph)
            throws IllegalArgumentException {
        this.getGraph().transferAttributes(graph);
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

    public String toString() {
        return getGraph().toString();
    }

    private LinkedList<Edge> dpathNewEdges() {
        if (this.dpathNewEdges == null) {
            this.dpathNewEdges = new LinkedList<>();
        }
        return this.dpathNewEdges;
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
        return this.CPDAG;
    }

    @Override
    public void setCPDAG(final boolean CPDAG) {
        this.CPDAG = CPDAG;
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





