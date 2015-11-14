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
    private List<String> variables = new ArrayList<String>();
    private int numLags = 0;
    private Map<String, List<Node>> laggedVariables = new HashMap<String, List<Node>>();

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

    public boolean removeVariable(Node node) {
        return false;
    }

    public int addLag() {

        // Creates a new numbered lag variable for each variable (of same latent type as variable)
        // and adds it to the graph... where??
        return 0;
    }

    public int removeLag() {
        return 0;
    }

    public int getNumLags() {
        return 0;
    }



    // Modified methods from graph.
    public boolean addDirectedEdge(Node node1, Node node2) {
        return getGraph().addDirectedEdge(node1, node2);
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

    public boolean addGraphConstraint(GraphConstraint gc) {
        throw new UnsupportedOperationException();
    }

    public void addPropertyChangeListener(PropertyChangeListener e) {
        getGraph().addPropertyChangeListener(e);
    }

    public void clear() {
        getGraph().clear();
    }

    public boolean containsEdge(Edge edge) {
        return getGraph().containsEdge(edge);
    }

    public boolean containsNode(Node node) {
        return getGraph().containsNode(node);
    }

    public boolean existsDirectedCycle() {
        return getGraph().existsDirectedCycle();
    }

    public boolean existsDirectedPathFromTo(Node node1, Node node2) {
        return getGraph().existsDirectedPathFromTo(node1, node2);
    }

    public boolean existsUndirectedPathFromTo(Node node1, Node node2) {
        return getGraph().existsUndirectedPathFromTo(node1, node2);
    }

    public boolean existsSemiDirectedPathFromTo(Node node1, Set<Node> nodes) {
        return getGraph().existsSemiDirectedPathFromTo(node1, nodes);
    }

    public boolean existsInducingPath(Node node1, Node node2) {
        return getGraph().existsInducingPath(node1, node2);
    }

    public boolean existsTrek(Node node1, Node node2) {
        return existsTrek(node1, node2);
    }

    public void fullyConnect(Endpoint endpoint) {
        throw new UnsupportedOperationException();
    }

    public void reorientAllWith(Endpoint endpoint) {
        getGraph().reorientAllWith(endpoint);
    }

    public List<Node> getAdjacentNodes(Node node) {
        return getGraph().getAdjacentNodes(node);
    }

    public List<Node> getAncestors(List<Node> nodes) {
        return getGraph().getAncestors(nodes);
    }

    public List<Node> getChildren(Node node) {
        return getGraph().getChildren(node);
    }

    public int getConnectivity() {
        return getGraph().getConnectivity();
    }

    public List<Node> getDescendants(List<Node> nodes) {
        return getGraph().getDescendants(nodes);
    }

    public Edge getEdge(Node node1, Node node2) {
        return getGraph().getEdge(node1, node2);
    }

    public Edge getDirectedEdge(Node node1, Node node2) {
        return getGraph().getDirectedEdge(node1, node2);
    }

    public List<Edge> getEdges(Node node) {
        return getGraph().getEdges(node);
    }

    public List<Edge> getEdges(Node node1, Node node2) {
        return getGraph().getEdges(node1, node2);
    }

    public Set<Edge> getEdges() {
        return getGraph().getEdges();
    }

    public Endpoint getEndpoint(Node node1, Node node2) {
        return getGraph().getEndpoint(node1, node2);
    }

    public Endpoint[][] getEndpointMatrix() {
        return getGraph().getEndpointMatrix();
    }

    public List<GraphConstraint> getGraphConstraints() {
        return getGraph().getGraphConstraints();
    }

    public int getIndegree(Node node) {
        return getGraph().getIndegree(node);
    }

    public Node getNode(String name) {
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

    public int getNumEdges(Node node) {
        return getGraph().getNumEdges(node);
    }

    public int getNumNodes() {
        return getGraph().getNumNodes();
    }

    public int getOutdegree(Node node) {
        return getGraph().getOutdegree(node);
    }

    public List<Node> getParents(Node node) {
        return getGraph().getParents(node);
    }

    public boolean isAdjacentTo(Node node1, Node node2) {
        return getGraph().isAdjacentTo(node1, node2);
    }

    public boolean isAncestorOf(Node node1, Node node2) {
        return getGraph().isAncestorOf(node1, node2);
    }

    public boolean possibleAncestor(Node node1, Node node2) {
        return getGraph().possibleAncestor(node1, node2);
    }

    public boolean isChildOf(Node node1, Node node2) {
        return getGraph().isChildOf(node2, node2);
    }

    public boolean isParentOf(Node node1, Node node2) {
        return getGraph().isParentOf(node1, node2);
    }

    public boolean isProperAncestorOf(Node node1, Node node2) {
        return getGraph().isProperAncestorOf(node1, node2);
    }

    public boolean isProperDescendentOf(Node node1, Node node2) {
        return getGraph().isProperDescendentOf(node1, node2);
    }

    public boolean isDescendentOf(Node node1, Node node2) {
        return getGraph().isDescendentOf(node1, node2);
    }

    public boolean defNonDescendent(Node node1, Node node2) {
        return getGraph().defNonDescendent(node1, node2);
    }

    public boolean isDefNoncollider(Node node1, Node node2, Node node3) {
        return getGraph().isDefNoncollider(node1, node2, node3);
    }

    public boolean isDefCollider(Node node1, Node node2, Node node3) {
        return getGraph().isDefCollider(node1, node2, node3);
    }

    public boolean isDConnectedTo(Node node1, Node node2, List<Node> z) {
        return getGraph().isDConnectedTo(node1, node2, z);
    }

    public boolean isDSeparatedFrom(Node node1, Node node2, List<Node> z) {
        return getGraph().isDSeparatedFrom(node1, node2, z);
    }

    public boolean possDConnectedTo(Node node1, Node node2, List<Node> z) {
        return getGraph().possDConnectedTo(node1, node2, z);
    }

    public boolean isDirectedFromTo(Node node1, Node node2) {
        return getGraph().isDirectedFromTo(node1, node2);
    }

    public boolean isUndirectedFromTo(Node node1, Node node2) {
        return getGraph().isUndirectedFromTo(node1, node2);
    }

    public boolean defVisible(Edge edge) {
        return getGraph().defVisible(edge);
    }

    public boolean isExogenous(Node node) {
        return getGraph().isExogenous(node);
    }

    public List<Node> getNodesInTo(Node node, Endpoint n) {
        return getGraph().getNodesInTo(node, n);
    }

    public List<Node> getNodesOutTo(Node node, Endpoint n) {
        return getGraph().getNodesOutTo(node, n);
    }

    public boolean removeEdge(Edge edge) {
        return getGraph().removeEdge(edge);
    }

    public boolean removeEdge(Node node1, Node node2) {
        return getGraph().removeEdge(node1, node2);
    }

    public boolean removeEdges(Node node1, Node node2) {
        return getGraph().removeEdges(node1, node2);
    }

    public boolean removeEdges(Collection<Edge> edges) {
        return getGraph().removeEdges(edges);
    }

    public boolean removeNode(Node node) {
        return getGraph().removeNode(node);
    }

    public boolean removeNodes(List<Node> nodes) {
        return getGraph().removeNodes(nodes);
    }

    public boolean setEndpoint(Node from, Node to, Endpoint endPoint) {
        return getGraph().setEndpoint(from, to, endPoint);
    }

    public boolean isGraphConstraintsChecked() {
        return getGraph().isGraphConstraintsChecked();
    }

    public void setGraphConstraintsChecked(boolean checked) {
        getGraph().setGraphConstraintsChecked(checked);
    }

    public Graph subgraph(List<Node> nodes) {
        return getGraph().subgraph(nodes);
    }

    public void transferNodesAndEdges(Graph graph) throws IllegalArgumentException {
        this.getGraph().transferNodesAndEdges(graph);
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

    public boolean isAmbiguousTriple(Node x, Node y, Node z) {
        return getGraph().isAmbiguousTriple(x, y, z);
    }

    public boolean isUnderlineTriple(Node x, Node y, Node z) {
        return getGraph().isUnderlineTriple(x, y, z);
    }

    public boolean isDottedUnderlineTriple(Node x, Node y, Node z) {
        return getGraph().isDottedUnderlineTriple(x, y, z);
    }

    public void addAmbiguousTriple(Node x, Node y, Node Z) {
        getGraph().addAmbiguousTriple(x, y, Z);
    }

    public void addUnderlineTriple(Node x, Node y, Node Z) {
        getGraph().addUnderlineTriple(x, y, Z);
    }

    public void addDottedUnderlineTriple(Node x, Node y, Node Z) {
        getGraph().addDottedUnderlineTriple(x, y, Z);
    }

    public void removeAmbiguousTriple(Node x, Node y, Node z) {
        getGraph().removeAmbiguousTriple(x, y, z);
    }

    public void removeUnderlineTriple(Node x, Node y, Node z) {
        getGraph().removeUnderlineTriple(x, y, z);
    }

    public void removeDottedUnderlineTriple(Node x, Node y, Node z) {
        getGraph().removeDottedUnderlineTriple(x, y, z);
    }

    public void setAmbiguousTriples(Set<Triple> triples) {
        getGraph().setAmbiguousTriples(triples);
    }

    public void setUnderLineTriples(Set<Triple> triples) {
        getGraph().setUnderLineTriples(triples);
    }

    public void setDottedUnderLineTriples(Set<Triple> triples) {
        getGraph().setDottedUnderLineTriples(triples);
    }

    public List<Node> getCausalOrdering() {
        return getGraph().getCausalOrdering();
    }

    public void setHighlighted(Edge edge, boolean highlighted) {
        getGraph().setHighlighted(edge, highlighted);
    }

    public boolean isHighlighted(Edge edge) {
        return getGraph().isHighlighted(edge);
    }

    public boolean isParameterizable(Node node) {
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
    public List<Node> getSepset(Node n1, Node n2) {
        throw new UnsupportedOperationException();
    }

    public Dag getGraph() {
        return graph;
    }

    public void setGraph(Dag graph) {
        this.graph = graph;
    }
}



