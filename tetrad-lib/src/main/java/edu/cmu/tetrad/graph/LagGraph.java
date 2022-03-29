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
import java.util.*;

/**
 * Implements a graph allowing nodes in the getModel time lag to have parents
 * taken from previous time lags. This is intended to be interpreted as a
 * repeating time series graph for purposes of simulation.
 *
 * @author Joseph Ramsey
 */
public class LagGraph implements Graph {
    static final long serialVersionUID = 23L;

    private Dag graph = new Dag();
    private final List<String> variables = new ArrayList<>();
    private final int numLags = 0;
    private final Map<String, List<Node>> laggedVariables = new HashMap<>();
    private boolean pag;
    private boolean CPDAG;

    private final Map<String, Object> attributes = new HashMap<>();

    // New methods.
    public boolean addVariable(final String variable) {
        if (this.variables.contains(variable)) {
            return false;
        }

        for (final String _variable : this.variables) {
            if (variable.equals(_variable)) {
                return false;
            }
        }

        this.variables.add(variable);
        this.laggedVariables.put(variable, new ArrayList<Node>());

        for (final String node : this.variables) {
            final List<Node> _lags = this.laggedVariables.get(node);
            final GraphNode _newNode = new GraphNode(node + "." + _lags.size());
            _lags.add(_newNode);
            _newNode.setCenter(5, 5);
            addNode(_newNode);
        }

        return true;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static LagGraph serializableInstance() {
        return new LagGraph();
    }

    // Modified methods from graph.
    public boolean addDirectedEdge(final Node node1, final Node node2) {
        return getGraph().addDirectedEdge(node1, node2);
    }

    public boolean addNode(final Node node) {
        throw new UnsupportedOperationException();
    }

    // Wrapped methods from graph.

    public boolean addBidirectedEdge(final Node node1, final Node node2) {
        throw new UnsupportedOperationException();
    }

    public boolean addUndirectedEdge(final Node node1, final Node node2) {
        throw new UnsupportedOperationException();
    }

    public boolean addNondirectedEdge(final Node node1, final Node node2) {
        throw new UnsupportedOperationException();
    }

    public boolean addPartiallyOrientedEdge(final Node node1, final Node node2) {
        throw new UnsupportedOperationException();
    }

    public boolean addEdge(final Edge edge) {
        throw new UnsupportedOperationException();
    }

    public void addPropertyChangeListener(final PropertyChangeListener e) {
        getGraph().addPropertyChangeListener(e);
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

    public boolean existsDirectedCycle() {
        return getGraph().existsDirectedCycle();
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

    public boolean existsSemiDirectedPathFromTo(final Node node1, final Set<Node> nodes) {
        return getGraph().existsSemiDirectedPathFromTo(node1, nodes);
    }

    public boolean existsInducingPath(final Node node1, final Node node2) {
        return getGraph().existsInducingPath(node1, node2);
    }

    public boolean existsTrek(final Node node1, final Node node2) {
        return getGraph().existsTrek(node1, node2);
    }

    public void fullyConnect(final Endpoint endpoint) {
        throw new UnsupportedOperationException();
    }

    public void reorientAllWith(final Endpoint endpoint) {
        getGraph().reorientAllWith(endpoint);
    }

    public List<Node> getAdjacentNodes(final Node node) {
        return getGraph().getAdjacentNodes(node);
    }

    public List<Node> getAncestors(final List<Node> nodes) {
        return getGraph().getAncestors(nodes);
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

    public List<Edge> getEdges(final Node node) {
        return getGraph().getEdges(node);
    }

    public List<Edge> getEdges(final Node node1, final Node node2) {
        return getGraph().getEdges(node1, node2);
    }

    public Set<Edge> getEdges() {
        return getGraph().getEdges();
    }

    public Endpoint getEndpoint(final Node node1, final Node node2) {
        return getGraph().getEndpoint(node1, node2);
    }

    public Endpoint[][] getEndpointMatrix() {
        return getGraph().getEndpointMatrix();
    }

    public int getIndegree(final Node node) {
        return getGraph().getIndegree(node);
    }

    @Override
    public int getDegree(final Node node) {
        return getGraph().getDegree(node);
    }

    public Node getNode(final String name) {
        return getGraph().getNode(name);
    }

    public List<Node> getNodes() {
        return getGraph().getNodes();
    }

    public List<String> getNodeNames() {
        return getGraph().getNodeNames();
    }

    public int getNumEdges() {
        return getGraph().getNumEdges();
    }

    public int getNumEdges(final Node node) {
        return getGraph().getNumEdges(node);
    }

    public int getNumNodes() {
        return getGraph().getNumNodes();
    }

    public int getOutdegree(final Node node) {
        return getGraph().getOutdegree(node);
    }

    public List<Node> getParents(final Node node) {
        return getGraph().getParents(node);
    }

    public boolean isAdjacentTo(final Node node1, final Node node2) {
        return getGraph().isAdjacentTo(node1, node2);
    }

    public boolean isAncestorOf(final Node node1, final Node node2) {
        return getGraph().isAncestorOf(node1, node2);
    }

    public boolean possibleAncestor(final Node node1, final Node node2) {
        return getGraph().possibleAncestor(node1, node2);
    }

    public boolean isChildOf(final Node node1, final Node node2) {
        return getGraph().isChildOf(node2, node2);
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

    public boolean isDescendentOf(final Node node1, final Node node2) {
        return getGraph().isDescendentOf(node1, node2);
    }

    public boolean defNonDescendent(final Node node1, final Node node2) {
        return getGraph().defNonDescendent(node1, node2);
    }

    public boolean isDefNoncollider(final Node node1, final Node node2, final Node node3) {
        return getGraph().isDefNoncollider(node1, node2, node3);
    }

    public boolean isDefCollider(final Node node1, final Node node2, final Node node3) {
        return getGraph().isDefCollider(node1, node2, node3);
    }

    public boolean isDConnectedTo(final Node node1, final Node node2, final List<Node> z) {
        return getGraph().isDConnectedTo(node1, node2, z);
    }

    public boolean isDSeparatedFrom(final Node node1, final Node node2, final List<Node> z) {
        return getGraph().isDSeparatedFrom(node1, node2, z);
    }

    public boolean possDConnectedTo(final Node node1, final Node node2, final List<Node> z) {
        return getGraph().possDConnectedTo(node1, node2, z);
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

    public boolean isExogenous(final Node node) {
        return getGraph().isExogenous(node);
    }

    public List<Node> getNodesInTo(final Node node, final Endpoint n) {
        return getGraph().getNodesInTo(node, n);
    }

    public List<Node> getNodesOutTo(final Node node, final Endpoint n) {
        return getGraph().getNodesOutTo(node, n);
    }

    public boolean removeEdge(final Edge edge) {
        return getGraph().removeEdge(edge);
    }

    public boolean removeEdge(final Node node1, final Node node2) {
        return getGraph().removeEdge(node1, node2);
    }

    public boolean removeEdges(final Node node1, final Node node2) {
        return getGraph().removeEdges(node1, node2);
    }

    public boolean removeEdges(final Collection<Edge> edges) {
        return getGraph().removeEdges(edges);
    }

    public boolean removeNode(final Node node) {
        return getGraph().removeNode(node);
    }

    public boolean removeNodes(final List<Node> nodes) {
        return getGraph().removeNodes(nodes);
    }

    public boolean setEndpoint(final Node from, final Node to, final Endpoint endPoint) {
        return getGraph().setEndpoint(from, to, endPoint);
    }

    public Graph subgraph(final List<Node> nodes) {
        return getGraph().subgraph(nodes);
    }

    public void transferNodesAndEdges(final Graph graph) throws IllegalArgumentException {
        this.getGraph().transferNodesAndEdges(graph);
    }

    public void transferAttributes(final Graph graph) throws IllegalArgumentException {
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

    public boolean isAmbiguousTriple(final Node x, final Node y, final Node z) {
        return getGraph().isAmbiguousTriple(x, y, z);
    }

    public boolean isUnderlineTriple(final Node x, final Node y, final Node z) {
        return getGraph().isUnderlineTriple(x, y, z);
    }

    public boolean isDottedUnderlineTriple(final Node x, final Node y, final Node z) {
        return getGraph().isDottedUnderlineTriple(x, y, z);
    }

    public void addAmbiguousTriple(final Node x, final Node y, final Node Z) {
        getGraph().addAmbiguousTriple(x, y, Z);
    }

    public void addUnderlineTriple(final Node x, final Node y, final Node Z) {
        getGraph().addUnderlineTriple(x, y, Z);
    }

    public void addDottedUnderlineTriple(final Node x, final Node y, final Node Z) {
        getGraph().addDottedUnderlineTriple(x, y, Z);
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

    public List<Node> getCausalOrdering() {
        return getGraph().getCausalOrdering();
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
        return false;
    }

    public TimeLagGraph getTimeLagGraph() {
        return null;
    }

    @Override
    public void removeTriplesNotInGraph() {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Node> getSepset(final Node n1, final Node n2) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setNodes(final List<Node> nodes) {
        this.graph.setNodes(nodes);
    }

    private Dag getGraph() {
        return this.graph;
    }

    public void setGraph(final Dag graph) {
        this.graph = graph;
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



