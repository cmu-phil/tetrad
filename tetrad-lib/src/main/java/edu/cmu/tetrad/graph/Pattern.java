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
 * To see what Pattern is, look at the graph constraints it uses. </p> Important
 * to inform user that when dynamically adding edges, they may not have a valid
 * Pattern until they run closeInducingPaths.  This is done for them when they
 * construct a Pattern from another graph.
 *
 * @author Joseph Ramsey
 * @see MeasuredLatentOnly
 * @see DirectedUndirectedOnly
 * @see AtMostOneEdgePerPair
 */
public final class Pattern implements TetradSerializable, Graph {
    static final long serialVersionUID = 23L;

    private final static GraphConstraint[] constraints = {
            new MeasuredLatentOnly(), new DirectedUndirectedOnly(),
            new AtMostOneEdgePerPair(), new NoEdgesToSelf()};

    /**
     * @serial
     */
    private final Graph graph = new EdgeListGraph();

    //==========================CONSTRUCTORS==========================//

    /**
     * Constructs a new blank Pattern.
     */
    public Pattern() {
        List<GraphConstraint> constraints1 = Arrays.asList(constraints);

        for (Object aConstraints1 : constraints1) {
            addGraphConstraint((GraphConstraint) aConstraints1);
        }
    }

    /**
     * Constructs a new Patterns based on the given graph.
     *
     * @param graph the graph to base the new Pattern on.
     * @throws IllegalArgumentException if the given graph cannot be converted
     *                                  to a Pattern for some reason.
     */
    public Pattern(Graph graph) throws IllegalArgumentException {
        if (graph == null) {
            throw new NullPointerException();
        }

        List<GraphConstraint> constraints1 = Arrays.asList(constraints);

        for (Object aConstraints1 : constraints1) {
            addGraphConstraint((GraphConstraint) aConstraints1);
        }

        transferNodesAndEdges(graph);
        closeInducingPaths();
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static Pattern serializableInstance() {
        return new Pattern();
    }

    //=========================PUBLIC METHODS===========================//

    public final void transferNodesAndEdges(Graph graph)
            throws IllegalArgumentException {
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


    /**
     * States whether x-y-x is an underline triple or not.
     */
    public boolean isAmbiguousTriple(Node x, Node y, Node z) {
        return getGraph().isAmbiguousTriple(x, y, z);
    }

    /**
     * States whether x-y-x is an underline triple or not.
     */
    public boolean isUnderlineTriple(Node x, Node y, Node z) {
        return getGraph().isUnderlineTriple(x, y, z);
    }

    /**
     * States whether x-y-x is an underline triple or not.
     */
    public boolean isDottedUnderlineTriple(Node x, Node y, Node z) {
        return getGraph().isDottedUnderlineTriple(x, y, z);
    }

    public void addAmbiguousTriple(Node x, Node y, Node z) {
        getGraph().addAmbiguousTriple(x, y, z);
    }

    public void addUnderlineTriple(Node x, Node y, Node z) {
        getGraph().addUnderlineTriple(x, y, z);
    }

    public void addDottedUnderlineTriple(Node x, Node y, Node z) {
        getGraph().addDottedUnderlineTriple(x, y, z);
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
    public List<Node> getSepset(Node n1, Node n2) {
        return graph.getSepset(n1, n2);
    }

    public void closeInducingPaths() {
        List<Node> list = new LinkedList<Node>(getNodes());

        // look for inducing undirectedPaths over all pairs of nodes.
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {

                //existsIndPath always returns true for a node to itself.  Do we actually
                // want to add self-edges all the time?  I don't think so.  Therefore not
                // even looking at node-to-self pairings

                Node node1 = list.get(i);
                Node node2 = list.get(j);

                Set<Node> allNodes = new HashSet<Node>(list);
                Set<Node> empty = new HashSet<Node>();

                if (existsInducingPath(node1, node2)) {
                    //is this the right check, or do I have to look at different cond sets?
                    if (getEdges(node1, node2).isEmpty()) {
                        addEdge(appropriateClosingEdge(node1, node2));
                    }
                }
            }
        }
    }

    //should check and make sure that existsIndPath looks in both dirs (99.9% sure it does),
    // not just from node1 to node2

    private Edge appropriateClosingEdge(Node node1, Node node2) {
        if (isAncestorOf(node1, node2)) {
            return Edges.directedEdge(node1, node2);
        } else if (isAncestorOf(node2, node1)) {
            return Edges.directedEdge(node2, node1);
        } else {
            return Edges.nondirectedEdge(node1, node2);
        }
    }


    public void fullyConnect(Endpoint endpoint) {
        getGraph().fullyConnect(endpoint);
    }

    public void reorientAllWith(Endpoint endpoint) {
        getGraph().reorientAllWith(endpoint);
    }

    public Endpoint[][] getEndpointMatrix() {
        return getGraph().getEndpointMatrix();
    }

    public List<Node> getAdjacentNodes(Node node) {
        return getGraph().getAdjacentNodes(node);
    }

    public List<Node> getNodesInTo(Node node, Endpoint endpoint) {
        return getGraph().getNodesInTo(node, endpoint);
    }

    public List<Node> getNodesOutTo(Node node, Endpoint n) {
        return getGraph().getNodesOutTo(node, n);
    }

    public List<Node> getNodes() {
        return getGraph().getNodes();
    }

    public List<String> getNodeNames() {
        return getGraph().getNodeNames();
    }

    public boolean removeEdge(Node node1, Node node2) {
        return getGraph().removeEdge(node1, node2);
    }

    public boolean removeEdges(Node node1, Node node2) {
        return getGraph().removeEdges(node1, node2);
    }

    public boolean isAdjacentTo(Node nodeX, Node nodeY) {
        return getGraph().isAdjacentTo(nodeX, nodeY);
    }

    public boolean setEndpoint(Node node1, Node node2, Endpoint endpoint) {
        return getGraph().setEndpoint(node1, node2, endpoint);
    }

    public Endpoint getEndpoint(Node node1, Node node2) {
        return getGraph().getEndpoint(node1, node2);
    }

    public boolean equals(Object o) {
        return getGraph().equals(o);
    }

    public Graph subgraph(List<Node> nodes) {
        return getGraph().subgraph(nodes);
    }

    public boolean existsDirectedPathFromTo(Node node1, Node node2) {
        return getGraph().existsDirectedPathFromTo(node1, node2);
    }

    public boolean existsUndirectedPathFromTo(Node node1, Node node2) {
        return getGraph().existsUndirectedPathFromTo(node1, node2);
    }

    public boolean existsSemiDirectedPathFromTo(Node node1, Set<Node> nodes2) {
        return getGraph().existsSemiDirectedPathFromTo(node1, nodes2);
    }

    public boolean addDirectedEdge(Node nodeA, Node nodeB) {
        return getGraph().addDirectedEdge(nodeA, nodeB);
    }

    public boolean addUndirectedEdge(Node nodeA, Node nodeB) {
        return getGraph().addUndirectedEdge(nodeA, nodeB);
    }

    public boolean addNondirectedEdge(Node nodeA, Node nodeB) {
        return getGraph().addNondirectedEdge(nodeA, nodeB);
    }

    public boolean addPartiallyOrientedEdge(Node nodeA, Node nodeB) {
        return getGraph().addPartiallyOrientedEdge(nodeA, nodeB);
    }

    public boolean addBidirectedEdge(Node nodeA, Node nodeB) {
        return getGraph().addBidirectedEdge(nodeA, nodeB);
    }

    public boolean addEdge(Edge edge) {
        return getGraph().addEdge(edge);
    }

    public boolean addNode(Node node) {
        return getGraph().addNode(node);
    }

    public void addPropertyChangeListener(PropertyChangeListener l) {
        getGraph().addPropertyChangeListener(l);
    }

    public boolean containsEdge(Edge edge) {
        return getGraph().containsEdge(edge);
    }

    public boolean containsNode(Node node) {
        return getGraph().containsNode(node);
    }

    public Set<Edge> getEdges() {
        return getGraph().getEdges();
    }

    public List<Edge> getEdges(Node node) {
        return getGraph().getEdges(node);
    }

    public List<Edge> getEdges(Node node1, Node node2) {
        return getGraph().getEdges(node1, node2);
    }

    public Node getNode(String name) {
        return getGraph().getNode(name);
    }

    public int getNumEdges() {
        return getGraph().getNumEdges();
    }

    public int getNumNodes() {
        return getGraph().getNumNodes();
    }

    public int getNumEdges(Node node) {
        return getGraph().getNumEdges(node);
    }

    public List<GraphConstraint> getGraphConstraints() {
        return getGraph().getGraphConstraints();
    }

    public boolean isGraphConstraintsChecked() {
        return getGraph().isGraphConstraintsChecked();
    }

    public void setGraphConstraintsChecked(boolean checked) {
        getGraph().setGraphConstraintsChecked(checked);
    }

    public boolean removeEdge(Edge edge) {
        return getGraph().removeEdge(edge);
    }

    public boolean removeEdges(Collection<Edge> edges) {
        return getGraph().removeEdges(edges);
    }

    public boolean removeNode(Node node) {
        return getGraph().removeNode(node);
    }

    public void clear() {
        getGraph().clear();
    }

    public boolean removeNodes(List<Node> nodes) {
        return getGraph().removeNodes(nodes);
    }

    public boolean existsDirectedCycle() {
        return getGraph().existsDirectedCycle();
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

    public boolean isDefNoncollider(Node node1, Node node2, Node node3) {
        return getGraph().isDefNoncollider(node1, node2, node3);
    }

    public boolean isDefCollider(Node node1, Node node2, Node node3) {
        return getGraph().isDefCollider(node1, node2, node3);
    }

    public boolean existsTrek(Node node1, Node node2) {
        return getGraph().existsTrek(node1, node2);
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

    public List<Node> getParents(Node node) {
        return getGraph().getParents(node);
    }

    public int getIndegree(Node node) {
        return getGraph().getIndegree(node);
    }

    public int getOutdegree(Node node) {
        return getGraph().getOutdegree(node);
    }

    public boolean isAncestorOf(Node node1, Node node2) {
        return getGraph().isAncestorOf(node1, node2);
    }

    public boolean possibleAncestor(Node node1, Node node2) {
        return getGraph().possibleAncestor(node1, node2);
    }

    public List<Node> getAncestors(List<Node> nodes) {
        return getGraph().getAncestors(nodes);
    }

    public boolean isChildOf(Node node1, Node node2) {
        return getGraph().isChildOf(node1, node2);
    }

    public boolean isDescendentOf(Node node1, Node node2) {
        return getGraph().isDescendentOf(node1, node2);
    }

    public boolean defNonDescendent(Node node1, Node node2) {
        return getGraph().defNonDescendent(node1, node2);
    }

    public boolean isDConnectedTo(Node node1, Node node2,
                                  List<Node> conditioningNodes) {
        return getGraph().isDConnectedTo(node1, node2, conditioningNodes);
    }

    public boolean isDSeparatedFrom(Node node1, Node node2, List<Node> z) {
        return getGraph().isDSeparatedFrom(node1, node2, z);
    }

    public boolean possDConnectedTo(Node node1, Node node2, List<Node> z) {
        return getGraph().possDConnectedTo(node1, node2, z);
    }

    public boolean existsInducingPath(Node node1, Node node2) {
        return getGraph().existsInducingPath(node1, node2);
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

    public boolean isExogenous(Node node) {
        return getGraph().isExogenous(node);
    }

    public String toString() {
        return getGraph().toString();
    }

    public boolean addGraphConstraint(GraphConstraint gc) {
        return getGraph().addGraphConstraint(gc);
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
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (getGraph() == null) {
            throw new NullPointerException();
        }
    }

    private Graph getGraph() {
        return graph;
    }
}






