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
    public boolean addVariable(String variable) {
        if (variables.contains(variable)) {
            return false;
        }

        for (String _variable : variables) {
            if (variable.equals(_variable)) {
                return false;
            }
        }

        variables.add(variable);
        laggedVariables.put(variable, new ArrayList<Node>());

        for (String node : variables) {
            List<Node> _lags = laggedVariables.get(node);
            GraphNode _newNode = new GraphNode(node + "." + _lags.size());
            _lags.add(_newNode);
            _newNode.setCenter(5, 5);
            this.addNode(_newNode);
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
    public boolean addDirectedEdge(Node node1, Node node2) {
        return this.getGraph().addDirectedEdge(node1, node2);
    }

    public boolean addNode(Node node) {
        throw new UnsupportedOperationException();
    }

    // Wrapped methods from graph.

    public boolean addBidirectedEdge(Node node1, Node node2) {
        throw new UnsupportedOperationException();
    }

    public boolean addUndirectedEdge(Node node1, Node node2) {
        throw new UnsupportedOperationException();
    }

    public boolean addNondirectedEdge(Node node1, Node node2) {
        throw new UnsupportedOperationException();
    }

    public boolean addPartiallyOrientedEdge(Node node1, Node node2) {
        throw new UnsupportedOperationException();
    }

    public boolean addEdge(Edge edge) {
        throw new UnsupportedOperationException();
    }

    public void addPropertyChangeListener(PropertyChangeListener e) {
        this.getGraph().addPropertyChangeListener(e);
    }

    public void clear() {
        this.getGraph().clear();
    }

    public boolean containsEdge(Edge edge) {
        return this.getGraph().containsEdge(edge);
    }

    public boolean containsNode(Node node) {
        return this.getGraph().containsNode(node);
    }

    public boolean existsDirectedCycle() {
        return this.getGraph().existsDirectedCycle();
    }

    public boolean existsDirectedPathFromTo(Node node1, Node node2) {
        return this.getGraph().existsDirectedPathFromTo(node1, node2);
    }

    @Override
    public List<Node> findCycle() {
        return this.getGraph().findCycle();
    }

    public boolean existsUndirectedPathFromTo(Node node1, Node node2) {
        return this.getGraph().existsUndirectedPathFromTo(node1, node2);
    }

    public boolean existsSemiDirectedPathFromTo(Node node1, Set<Node> nodes) {
        return this.getGraph().existsSemiDirectedPathFromTo(node1, nodes);
    }

    public boolean existsInducingPath(Node node1, Node node2) {
        return this.getGraph().existsInducingPath(node1, node2);
    }

    public boolean existsTrek(Node node1, Node node2) {
        return this.getGraph().existsTrek(node1, node2);
    }

    public void fullyConnect(Endpoint endpoint) {
        throw new UnsupportedOperationException();
    }

    public void reorientAllWith(Endpoint endpoint) {
        this.getGraph().reorientAllWith(endpoint);
    }

    public List<Node> getAdjacentNodes(Node node) {
        return this.getGraph().getAdjacentNodes(node);
    }

    public List<Node> getAncestors(List<Node> nodes) {
        return this.getGraph().getAncestors(nodes);
    }

    public List<Node> getChildren(Node node) {
        return this.getGraph().getChildren(node);
    }

    public int getConnectivity() {
        return this.getGraph().getConnectivity();
    }

    public List<Node> getDescendants(List<Node> nodes) {
        return this.getGraph().getDescendants(nodes);
    }

    public Edge getEdge(Node node1, Node node2) {
        return this.getGraph().getEdge(node1, node2);
    }

    public Edge getDirectedEdge(Node node1, Node node2) {
        return this.getGraph().getDirectedEdge(node1, node2);
    }

    public List<Edge> getEdges(Node node) {
        return this.getGraph().getEdges(node);
    }

    public List<Edge> getEdges(Node node1, Node node2) {
        return this.getGraph().getEdges(node1, node2);
    }

    public Set<Edge> getEdges() {
        return this.getGraph().getEdges();
    }

    public Endpoint getEndpoint(Node node1, Node node2) {
        return this.getGraph().getEndpoint(node1, node2);
    }

    public Endpoint[][] getEndpointMatrix() {
        return this.getGraph().getEndpointMatrix();
    }

    public int getIndegree(Node node) {
        return this.getGraph().getIndegree(node);
    }

    @Override
    public int getDegree(Node node) {
        return this.getGraph().getDegree(node);
    }

    public Node getNode(String name) {
        return this.getGraph().getNode(name);
    }

    public List<Node> getNodes() {
        return this.getGraph().getNodes();
    }

    public List<String> getNodeNames() {
        return this.getGraph().getNodeNames();
    }

    public int getNumEdges() {
        return this.getGraph().getNumEdges();
    }

    public int getNumEdges(Node node) {
        return this.getGraph().getNumEdges(node);
    }

    public int getNumNodes() {
        return this.getGraph().getNumNodes();
    }

    public int getOutdegree(Node node) {
        return this.getGraph().getOutdegree(node);
    }

    public List<Node> getParents(Node node) {
        return this.getGraph().getParents(node);
    }

    public boolean isAdjacentTo(Node node1, Node node2) {
        return this.getGraph().isAdjacentTo(node1, node2);
    }

    public boolean isAncestorOf(Node node1, Node node2) {
        return this.getGraph().isAncestorOf(node1, node2);
    }

    public boolean possibleAncestor(Node node1, Node node2) {
        return this.getGraph().possibleAncestor(node1, node2);
    }

    public boolean isChildOf(Node node1, Node node2) {
        return this.getGraph().isChildOf(node2, node2);
    }

    public boolean isParentOf(Node node1, Node node2) {
        return this.getGraph().isParentOf(node1, node2);
    }

    public boolean isProperAncestorOf(Node node1, Node node2) {
        return this.getGraph().isProperAncestorOf(node1, node2);
    }

    public boolean isProperDescendentOf(Node node1, Node node2) {
        return this.getGraph().isProperDescendentOf(node1, node2);
    }

    public boolean isDescendentOf(Node node1, Node node2) {
        return this.getGraph().isDescendentOf(node1, node2);
    }

    public boolean defNonDescendent(Node node1, Node node2) {
        return this.getGraph().defNonDescendent(node1, node2);
    }

    public boolean isDefNoncollider(Node node1, Node node2, Node node3) {
        return this.getGraph().isDefNoncollider(node1, node2, node3);
    }

    public boolean isDefCollider(Node node1, Node node2, Node node3) {
        return this.getGraph().isDefCollider(node1, node2, node3);
    }

    public boolean isDConnectedTo(Node node1, Node node2, List<Node> z) {
        return this.getGraph().isDConnectedTo(node1, node2, z);
    }

    public boolean isDSeparatedFrom(Node node1, Node node2, List<Node> z) {
        return this.getGraph().isDSeparatedFrom(node1, node2, z);
    }

    public boolean possDConnectedTo(Node node1, Node node2, List<Node> z) {
        return this.getGraph().possDConnectedTo(node1, node2, z);
    }

    public boolean isDirectedFromTo(Node node1, Node node2) {
        return this.getGraph().isDirectedFromTo(node1, node2);
    }

    public boolean isUndirectedFromTo(Node node1, Node node2) {
        return this.getGraph().isUndirectedFromTo(node1, node2);
    }

    public boolean defVisible(Edge edge) {
        return this.getGraph().defVisible(edge);
    }

    public boolean isExogenous(Node node) {
        return this.getGraph().isExogenous(node);
    }

    public List<Node> getNodesInTo(Node node, Endpoint n) {
        return this.getGraph().getNodesInTo(node, n);
    }

    public List<Node> getNodesOutTo(Node node, Endpoint n) {
        return this.getGraph().getNodesOutTo(node, n);
    }

    public boolean removeEdge(Edge edge) {
        return this.getGraph().removeEdge(edge);
    }

    public boolean removeEdge(Node node1, Node node2) {
        return this.getGraph().removeEdge(node1, node2);
    }

    public boolean removeEdges(Node node1, Node node2) {
        return this.getGraph().removeEdges(node1, node2);
    }

    public boolean removeEdges(Collection<Edge> edges) {
        return this.getGraph().removeEdges(edges);
    }

    public boolean removeNode(Node node) {
        return this.getGraph().removeNode(node);
    }

    public boolean removeNodes(List<Node> nodes) {
        return this.getGraph().removeNodes(nodes);
    }

    public boolean setEndpoint(Node from, Node to, Endpoint endPoint) {
        return this.getGraph().setEndpoint(from, to, endPoint);
    }

    public Graph subgraph(List<Node> nodes) {
        return this.getGraph().subgraph(nodes);
    }

    public void transferNodesAndEdges(Graph graph) throws IllegalArgumentException {
        getGraph().transferNodesAndEdges(graph);
    }

    public void transferAttributes(Graph graph) throws IllegalArgumentException {
        getGraph().transferAttributes(graph);
    }

    public Set<Triple> getAmbiguousTriples() {
        return this.getGraph().getAmbiguousTriples();
    }

    public Set<Triple> getUnderLines() {
        return this.getGraph().getUnderLines();
    }

    public Set<Triple> getDottedUnderlines() {
        return this.getGraph().getDottedUnderlines();
    }

    public boolean isAmbiguousTriple(Node x, Node y, Node z) {
        return this.getGraph().isAmbiguousTriple(x, y, z);
    }

    public boolean isUnderlineTriple(Node x, Node y, Node z) {
        return this.getGraph().isUnderlineTriple(x, y, z);
    }

    public boolean isDottedUnderlineTriple(Node x, Node y, Node z) {
        return this.getGraph().isDottedUnderlineTriple(x, y, z);
    }

    public void addAmbiguousTriple(Node x, Node y, Node Z) {
        this.getGraph().addAmbiguousTriple(x, y, Z);
    }

    public void addUnderlineTriple(Node x, Node y, Node Z) {
        this.getGraph().addUnderlineTriple(x, y, Z);
    }

    public void addDottedUnderlineTriple(Node x, Node y, Node Z) {
        this.getGraph().addDottedUnderlineTriple(x, y, Z);
    }

    public void removeAmbiguousTriple(Node x, Node y, Node z) {
        this.getGraph().removeAmbiguousTriple(x, y, z);
    }

    public void removeUnderlineTriple(Node x, Node y, Node z) {
        this.getGraph().removeUnderlineTriple(x, y, z);
    }

    public void removeDottedUnderlineTriple(Node x, Node y, Node z) {
        this.getGraph().removeDottedUnderlineTriple(x, y, z);
    }

    public void setAmbiguousTriples(Set<Triple> triples) {
        this.getGraph().setAmbiguousTriples(triples);
    }

    public void setUnderLineTriples(Set<Triple> triples) {
        this.getGraph().setUnderLineTriples(triples);
    }

    public void setDottedUnderLineTriples(Set<Triple> triples) {
        this.getGraph().setDottedUnderLineTriples(triples);
    }

    public List<Node> getCausalOrdering() {
        return this.getGraph().getCausalOrdering();
    }

    public void setHighlighted(Edge edge, boolean highlighted) {
        this.getGraph().setHighlighted(edge, highlighted);
    }

    public boolean isHighlighted(Edge edge) {
        return this.getGraph().isHighlighted(edge);
    }

    public boolean isParameterizable(Node node) {
        return this.getGraph().isParameterizable(node);
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
    public List<Node> getSepset(Node n1, Node n2) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setNodes(List<Node> nodes) {
        graph.setNodes(nodes);
    }

    private Dag getGraph() {
        return graph;
    }

    public void setGraph(Dag graph) {
        this.graph = graph;
    }

    @Override
    public List<String> getTriplesClassificationTypes() {
        return null;
    }

    @Override
    public List<List<Triple>> getTriplesLists(Node node) {
        return null;
    }

    @Override
    public boolean isPag() {
        return pag;
    }

    @Override
    public void setPag(boolean pag) {
        this.pag = pag;
    }

    @Override
    public boolean isCPDAG() {
        return CPDAG;
    }

    @Override
    public void setCPDAG(boolean CPDAG) {
        this.CPDAG = CPDAG;
    }

    @Override
    public Map<String, Object> getAllAttributes() {
        return attributes;
    }

    @Override
    public Object getAttribute(String key) {
        return attributes.get(key);
    }

    @Override
    public void removeAttribute(String key) {
        attributes.remove(key);
    }

    @Override
    public void addAttribute(String key, Object value) {
        attributes.put(key, value);
    }

}



