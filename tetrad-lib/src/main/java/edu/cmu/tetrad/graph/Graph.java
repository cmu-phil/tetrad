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
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * <p>Implements a graph capable of storing edges of type N1 *-# N2 where * and
 * # are endpoints of type Endpoint.</p> <p>We stipulate by extending
 * TetradSerializable that all graphs implementing this interface are
 * serializable. This is because for Tetrad they must be serializable. (For
 * randomUtil, in order to be able to cancel operations, they must be
 * serializable.)</p>
 *
 * @author Joseph Ramsey
 * @see Endpoint
 */
public interface Graph extends TetradSerializable {
    long serialVersionUID = 23L;

    /**
     * Adds a bidirected edges <-> to the graph.
     */
    boolean addBidirectedEdge(Node node1, Node node2);

    /**
     * Adds a directed edge --> to the graph.
     */
    boolean addDirectedEdge(Node node1, Node node2);

    /**
     * Adds an undirected edge --- to the graph.
     */
    boolean addUndirectedEdge(Node node1, Node node2);

    /**
     * Adds an nondirected edges o-o to the graph.
     */
    boolean addNondirectedEdge(Node node1, Node node2);

    /**
     * Adds a partially oriented edge o-> to the graph.
     */
    boolean addPartiallyOrientedEdge(Node node1, Node node2);

    /**
     * Adds the specified edge to the graph, provided it is not already in the
     * graph.
     *
     * @return true if the edge was added, false if not.
     */
    boolean addEdge(Edge edge);

    /**
     * Adds a graph constraint.
     *
     * @return true if the constraint was added, false if not.
     */
    boolean addGraphConstraint(GraphConstraint gc);

    /**
     * Adds a node to the graph. Precondition: The proposed name of the node
     * cannot already be used by any other node in the same graph.
     *
     * @return true if nodes were added, false if not.
     */
    boolean addNode(Node node);

    /**
     * Adds a PropertyChangeListener to the graph.
     */
    void addPropertyChangeListener(PropertyChangeListener e);

    /**
     * Removes all nodes (and therefore all edges) from the graph.
     */
    void clear();

    /**
     * Determines whether this graph contains the given edge.
     *
     * @return true iff the graph contain 'edge'.
     */
    boolean containsEdge(Edge edge);

    /**
     * Determines whether this graph contains the given node.
     *
     * @return true iff the graph contains 'node'.
     */
    boolean containsNode(Node node);

    /**
     * @return true iff there is a directed cycle in the graph.
     */
    boolean existsDirectedCycle();

    /**
     * @return true iff there is a directed path from node1 to node2 in the
     * graph.
     */
    boolean existsDirectedPathFromTo(Node node1, Node node2);

    /**
     * @return true iff there is a semi-directed path from node1 to something in
     * nodes2 in the graph
     */
    boolean existsUndirectedPathFromTo(Node node1, Node node2);

    /**
     * </p> A semi-directed path from A to B is an undirected path in which no
     * edge has an arrowhead pointing "back" towards A.
     *
     * @return true iff there is a semi-directed path from node1 to something in
     * nodes2 in the graph
     */
    boolean existsSemiDirectedPathFromTo(Node node1, Set<Node> nodes);

    /**
     * Determines whether an inducing path exists between node1 and node2, given
     * a set O of observed nodes and a set sem of conditioned nodes.
     *
     * @param node1 the first node.
     * @param node2 the second node.
     * @return true if an inducing path exists, false if not.
     */
    boolean existsInducingPath(Node node1, Node node2);

    /**
     * @return true iff a trek exists between two nodes in the graph.  A trek
     * exists if there is a directed path between the two nodes or else, for
     * some third node in the graph, there is a path to each of the two nodes in
     * question.
     */
    boolean existsTrek(Node node1, Node node2);

    /**
     * Determines whether this graph is equal to some other graph, in the sense
     * that they contain the same nodes and the sets of edges defined over these
     * nodes in the two graphs are isomorphic typewise. That is, if node A and B
     * exist in both graphs, and if there are, e.g., three edges between A and B
     * in the first graph, two of which are directed edges and one of which is
     * an undirected edge, then in the second graph there must also be two
     * directed edges and one undirected edge between nodes A and B.
     */
    boolean equals(Object o);

    /**
     * Removes all edges from the graph and fully connects it using #-# edges,
     * where # is the given endpoint.
     */
    void fullyConnect(Endpoint endpoint);

    /**
     * Reorients all edges in the graph with the given endpoint.
     */
    void reorientAllWith(Endpoint endpoint);

    /**
     * @return a mutable list of nodes adjacent to the given node.
     */
    List<Node> getAdjacentNodes(Node node);

    /**
     * @return a mutable list of ancestors for the given nodes.
     */
    List<Node> getAncestors(List<Node> nodes);

    /**
     * @return a mutable list of children for a node.
     */
    List<Node> getChildren(Node node);

    /**
     * @return the connectivity of the graph.
     */
    int getConnectivity();

    /**
     * @return a mutable list of descendants for the given nodes.
     */
    List<Node> getDescendants(List<Node> nodes);

    /**
     * @return the edge connecting node1 and node2, provided a unique such edge
     * exists.
     * @throws UnsupportedOperationException if the graph allows multiple edges
     *                                       between node pairs.
     */
    Edge getEdge(Node node1, Node node2);

    /**
     * @return the directed edge from node1 to node2, if there is one.
     * @throws UnsupportedOperationException if the graph allows multiple edges
     *                                       between node pairs.
     */
    Edge getDirectedEdge(Node node1, Node node2);

    /**
     * @return the list of edges connected to a particular node. No particular
     * ordering of the edges in the list is guaranteed.
     */
    List<Edge> getEdges(Node node);

    /**
     * @return the edges connecting node1 and node2.
     */
    List<Edge> getEdges(Node node1, Node node2);

    /**
     * @return the list of edges in the graph.  No particular ordering of the
     * edges in the list is guaranteed.
     */
    Set<Edge> getEdges();

    /**
     * @return the endpoint along the edge from node to node2 at the node2 end.
     */
    Endpoint getEndpoint(Node node1, Node node2);

    /**
     * @return a matrix of endpoints for the nodes in this graph, with nodes in
     * the same order as getNodes().
     */
    Endpoint[][] getEndpointMatrix();

    /**
     * @return the list of graph constraints for this graph.
     */
    List<GraphConstraint> getGraphConstraints();

    /**
     * @return the number of arrow endpoints adjacent to a node.
     */
    int getIndegree(Node node);

    /**
     * @return the node with the given string name.  In case of accidental
     * duplicates, the first node encountered with the given name is returned.
     * In case no node exists with the given name, null is returned.
     */
    Node getNode(String name);

    /**
     * @return the list of nodes for the graph.
     */
    List<Node> getNodes();

    /**
     * @return the names of the nodes, in the order of <code>getNodes</code>.
     */
    List<String> getNodeNames();

    /**
     * @return the number of edges in the (entire) graph.
     */
    int getNumEdges();

    /**
     * @return the number of edges in the graph which are connected to a
     * particular node.
     */
    int getNumEdges(Node node);

    /**
     * @return the number of nodes in the graph.
     */
    int getNumNodes();

    /**
     * @return the number of null endpoints adjacent to an edge.
     */
    int getOutdegree(Node node);

    /**
     * @return the list of parents for a node.
     */
    List<Node> getParents(Node node);

    /**
     * @return true iff node1 is adjacent to node2 in the graph.
     */
    boolean isAdjacentTo(Node node1, Node node2);

    /**
     * Determines whether one node is an ancestor of another.
     */
    boolean isAncestorOf(Node node1, Node node2);

    /**
     * added by ekorber, 2004/06/12
     *
     * @return true if node1 is a possible ancestor of node2.
     */
    boolean possibleAncestor(Node node1, Node node2);

    /**
     * @return true iff node1 is a child of node2 in the graph.
     */
    boolean isChildOf(Node node1, Node node2);

    /**
     * Determines whether node1 is a parent of node2.
     */
    boolean isParentOf(Node node1, Node node2);

    /**
     * Determines whether one node is a proper ancestor of another.
     */
    boolean isProperAncestorOf(Node node1, Node node2);

    /**
     * Determines whether one node is a proper decendent of another.
     */
    boolean isProperDescendentOf(Node node1, Node node2);

    /**
     * @return true iff node1 is a (non-proper) descendant of node2.
     */
    boolean isDescendentOf(Node node1, Node node2);

    /**
     * A node Y is a definite nondescendent of a node X just in case there is no
     * semi-directed path from X to Y.
     * <p>
     * added by ekorber, 2004/06/12.
     *
     * @return true if node 2 is a definite nondecendent of node 1
     */
    boolean defNonDescendent(Node node1, Node node2);

    /**
     * Added by ekorber, 2004/6/9.
     *
     * @return true if node 2 is a definite noncollider between 1 and 3
     */
    boolean isDefNoncollider(Node node1, Node node2, Node node3);

    /**
     * Added by ekorber, 2004/6/9.
     *
     * @return true if node 2 is a definite collider between 1 and 3
     */
    boolean isDefCollider(Node node1, Node node2, Node node3);

    /**
     * Determines whether one node is d-connected to another. According to
     * Spirtes, Richardson & Meek, two nodes are d- connected given some
     * conditioning set Z if there is an acyclic undirected path U between them,
     * such that every collider on U is an ancestor of some element in Z and
     * every non-collider on U is not in Z.  Two elements are d-separated just
     * in case they are not d-connected.  A collider is a node which two edges
     * hold in common for which the endpoints leading into the node are both
     * arrow endpoints.
     */
    boolean isDConnectedTo(Node node1, Node node2, List<Node> z);

    /**
     * Determines whether one node is d-separated from another. Two elements are   E
     * d-separated just in case they are not d-connected.
     */
    boolean isDSeparatedFrom(Node node1, Node node2, List<Node> z);

    /**
     * Determines if nodes 1 and 2 are possibly d-connected given conditioning
     * set z.  A path U is possibly-d-connecting if every definite collider on U
     * is a possible ancestor of a node in z and every definite non-collider is
     * not in z.
     * <p>
     * added by ekorber, 2004/06/15.
     *
     * @return true iff nodes 1 and 2 are possibly d-connected given z
     */
    boolean possDConnectedTo(Node node1, Node node2, List<Node> z);

    /**
     * @return true iff there is a single directed edge from node1 to node2 in
     * the graph.
     */
    boolean isDirectedFromTo(Node node1, Node node2);

    /**
     * @return true iff there is a single undirected edge from node1 to node2 in
     * the graph.
     */
    boolean isUndirectedFromTo(Node node1, Node node2);

    /**
     * A directed edge A->B is definitely visible if there is a node C not
     * adjacent to B such that C*->A is in the PAG. Added by ekorber,
     * 2004/06/11.
     *
     * @return true if the given edge is definitely visible (Jiji, pg 25)
     * @throws IllegalArgumentException if the given edge is not a directed edge
     *                                  in the graph
     */
    boolean defVisible(Edge edge);

    /**
     * @return true iff the given node is exogenous in the graph.
     */
    boolean isExogenous(Node node);

    /**
     * Nodes adjacent to the given node with the given proximal endpoint.
     */
    List<Node> getNodesInTo(Node node, Endpoint n);

    /**
     * Nodes adjacent to the given node with the given distal endpoint.
     */
    List<Node> getNodesOutTo(Node node, Endpoint n);

    /**
     * Removes the given edge from the graph.
     *
     * @return true if the edge was removed, false if not.
     */
    boolean removeEdge(Edge edge);

    /**
     * Removes the edge connecting the two given nodes, provided there is
     * exactly one such edge.
     *
     * @throws UnsupportedOperationException if multiple edges between node
     *                                       pairs are not supported.
     */
    boolean removeEdge(Node node1, Node node2);

    /**
     * Removes all edges connecting node A to node B.  In most cases, this will
     * remove at most one edge, but since multiple edges are permitted in some
     * graph implementations, the number will in some cases be greater than
     * one.
     *
     * @return true if edges were removed, false if not.
     */
    boolean removeEdges(Node node1, Node node2);

    /**
     * Iterates through the list and removes any permissible edges found.  The
     * order in which edges are added is the order in which they are presented
     * in the iterator.
     *
     * @return true if edges were added, false if not.
     */
    boolean removeEdges(Collection<Edge> edges);

    /**
     * Removes a node from the graph.
     *
     * @return true if the node was removed, false if not.
     */
    boolean removeNode(Node node);

    /**
     * Iterates through the list and removes any permissible nodes found.  The
     * order in which nodes are removed is the order in which they are presented
     * in the iterator.
     *
     * @return true if nodes were added, false if not.
     */
    boolean removeNodes(List<Node> nodes);

    /**
     * Sets the endpoint type at the 'to' end of the edge from 'from' to 'to' to
     * the given endpoint.  Note: NOT CONSTRAINT SAFE
     */
    boolean setEndpoint(Node from, Node to, Endpoint endPoint);

    /**
     * @return true iff graph constraints will be checked for future graph
     * modifications.
     */
    boolean isGraphConstraintsChecked();

    /**
     * Set whether graph constraints will be checked for future graph
     * modifications.
     */
    void setGraphConstraintsChecked(boolean checked);

    /**
     * Constructs and returns a subgraph consisting of a given subset of the
     * nodes of this graph together with the edges between them.
     */
    Graph subgraph(List<Node> nodes);

    /**
     * @return a string representation of the graph.
     */
    String toString();

    /**
     * Transfers nodes and edges from one graph to another.  One way this is
     * used is to change graph types.  One constructs a new graph based on the
     * old graph, and this method is called to transfer the nodes and edges of
     * the old graph to the new graph.
     *
     * @param graph the graph from which nodes and edges are to be pilfered.
     * @throws java.lang.IllegalArgumentException This exception is thrown if adding some node or edge violates
     *                                            one of the basicConstraints of this graph.
     */
    void transferNodesAndEdges(Graph graph) throws IllegalArgumentException;

    /**
     * @return the list of ambiguous triples associated with this graph. Triples <x, y, z> that no longer
     * lie along a path in the getModel graph are removed.
     */
    Set<Triple> getAmbiguousTriples();

    /**
     * @return the set of underlines associated with this graph. This is used currently by ION, DCI, and CCD.
     * It used to be used by FCI, but it not in the getModel form.  Triples <x, y, z> that no longer
     * lie along a path in the getModel graph are removed.
     */
    Set<Triple> getUnderLines();

    /**
     * @return the set of dotted underlines associated with this graph. This used to be used by FCI, but it is
     * not used in the getModel form. It is used by CCD.  Triples <x, y, z> that no longer
     * lie along a path in the getModel graph are removed.
     */
    Set<Triple> getDottedUnderlines();

    /**
     * @return true iff the triple <x, y, z> is set as ambiguous.  Triples <x, y, z> that no longer
     * lie along a path in the getModel graph are removed.
     */
    boolean isAmbiguousTriple(Node x, Node y, Node z);

    /**
     * @return true iff the triple <x, y, z> is set as underlined.  Triples <x, y, z> that no longer
     * lie along a path in the getModel graph are removed.
     */
    boolean isUnderlineTriple(Node x, Node y, Node z);

    /**
     * @return true iff the triple <x, y, z> is set as dotted underlined.   Triples <x, y, z> that no longer
     * lie along a path in the getModel graph are removed.
     */
    boolean isDottedUnderlineTriple(Node x, Node y, Node z);

    /**
     * Adds the triple <x, y, z> as an ambiguous triple in the graph.
     *
     * @throws IllegalArgumentException if <x, y, z> does not lie along a path in the graph.
     */
    void addAmbiguousTriple(Node x, Node y, Node Z);

    /**
     * Adds the triple <x, y, z> as an underline triple in the graph.
     *
     * @throws IllegalArgumentException if <x, y, z> does not lie along a path in the graph.
     */
    void addUnderlineTriple(Node x, Node y, Node Z);

    /**
     * Adds the triple <x, y, z> as a dotted underlined triple in the graph.
     *
     * @throws IllegalArgumentException if <x, y, z> does not lie along a path in the graph.
     */
    void addDottedUnderlineTriple(Node x, Node y, Node Z);

    /**
     * Removes the triple <x, y, z> from thet set of ambiguous triples.
     */
    void removeAmbiguousTriple(Node x, Node y, Node z);

    /**
     * Removes the triple <x, y, z> from the set of underlined triples.
     */
    void removeUnderlineTriple(Node x, Node y, Node z);

    /**
     * Removes the triple <x, y, z> from the set of dotted underlined triples.
     */
    void removeDottedUnderlineTriple(Node x, Node y, Node z);

    /**
     * Sets the list of ambiguous triples to the triples in the given set.
     *
     * @param triples The new set of ambiguous triples. This replaces the old list.
     * @throws IllegalArgumentException if any triple <x, y, z> in <code>triples</code> does not lie along a path in the graph.
     */
    void setAmbiguousTriples(Set<Triple> triples);

    /**
     * Sets the list of underlined triples to the triples in the given set.
     *
     * @param triples The new list of ambiguous triples. This replaces the old list.
     * @throws IllegalArgumentException if any triple <x, y, z> in <code>triples</code> does not lie along a path in the graph.
     */
    void setUnderLineTriples(Set<Triple> triples);

    /**
     * Sets the list of dotted underlined triples to the triples in the given set.
     *
     * @param triples The new list of dotted underlined triples. This replaces the old list.
     * @throws IllegalArgumentException if any triple <x, y, z> in <code>triples</code> does not lie along a path in the graph.
     */
    void setDottedUnderLineTriples(Set<Triple> triples);

    /**
     * @return a tier orderering, for acyclic graphs. Undefined for cyclic graphs.
     */
    List<Node> getCausalOrdering();

    /**
     * Sets an edge to be highlighted.
     */
    void setHighlighted(Edge edge, boolean highlighted);

    /**
     * @return true just in case the given edge is highlighted.
     * @throws IllegalArgumentException if the given edge is not in the graph.
     */
    boolean isHighlighted(Edge edge);

    /**
     * @return true if the given node is parameterizable.
     */
    boolean isParameterizable(Node node);

    /**
     * @return true if this is a time lag model, in which case getTimeLagGraph() returns the graph.
     */
    boolean isTimeLagModel();

    /**
     * @return the underlying time lag model, if there is one; otherwise, returns null.
     */
    TimeLagGraph getTimeLagGraph();


    void removeTriplesNotInGraph();

    List<Node> getSepset(Node n1, Node n2);
}





