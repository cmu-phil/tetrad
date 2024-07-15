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

import edu.cmu.tetrad.util.TetradSerializable;

import java.beans.PropertyChangeListener;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Implements a graph capable of storing edges of type N1 *-# N2 where * and # are endpoints of type Endpoint.<p>We
 * stipulate by extending TetradSerializable that all graphs implementing this interface are serializable. This is
 * because for Tetrad they must be serializable. (For randomUtil, in order to be able to cancelAll operations, they must
 * be serializable.)
 *
 * @author josephramsey
 * @version $Id: $Id
 * @see Endpoint
 */
public interface Graph extends TetradSerializable {
    /**
     * Constant <code>serialVersionUID=23L</code>
     */
    long serialVersionUID = 23L;

    /**
     * Adds a bidirected edges &lt;-&gt; to the graph.
     *
     * @param node1 a {@link edu.cmu.tetrad.graph.Node} object
     * @param node2 a {@link edu.cmu.tetrad.graph.Node} object
     * @return a boolean
     */
    boolean addBidirectedEdge(Node node1, Node node2);

    /**
     * Adds a directed edge --&gt; to the graph.
     *
     * @param node1 a {@link edu.cmu.tetrad.graph.Node} object
     * @param node2 a {@link edu.cmu.tetrad.graph.Node} object
     * @return a boolean
     */
    boolean addDirectedEdge(Node node1, Node node2);

    /**
     * Adds an undirected edge --- to the graph.
     *
     * @param node1 a {@link edu.cmu.tetrad.graph.Node} object
     * @param node2 a {@link edu.cmu.tetrad.graph.Node} object
     * @return a boolean
     */
    boolean addUndirectedEdge(Node node1, Node node2);

    /**
     * Adds a nondirected edges o-o to the graph.
     *
     * @param node1 a {@link edu.cmu.tetrad.graph.Node} object
     * @param node2 a {@link edu.cmu.tetrad.graph.Node} object
     * @return a boolean
     */
    boolean addNondirectedEdge(Node node1, Node node2);

    /**
     * Adds a partially oriented edge o-&gt; to the graph.
     *
     * @param node1 a {@link edu.cmu.tetrad.graph.Node} object
     * @param node2 a {@link edu.cmu.tetrad.graph.Node} object
     * @return a boolean
     */
    boolean addPartiallyOrientedEdge(Node node1, Node node2);

    /**
     * Adds the specified edge to the graph, provided it is not already in the graph.
     *
     * @param edge a {@link edu.cmu.tetrad.graph.Edge} object
     * @return true if the edge was added, false if not.
     */
    boolean addEdge(Edge edge);

    /**
     * Adds a node to the graph. Precondition: The proposed name of the node cannot already be used by any other node in
     * the same graph.
     *
     * @param node a {@link edu.cmu.tetrad.graph.Node} object
     * @return true if nodes were added, false if not.
     */
    boolean addNode(Node node);

    /**
     * Adds a PropertyChangeListener to the graph.
     *
     * @param e a {@link java.beans.PropertyChangeListener} object
     */
    void addPropertyChangeListener(PropertyChangeListener e);

    /**
     * Removes all nodes (and therefore all edges) from the graph.
     */
    void clear();

    /**
     * Determines whether this graph contains the given edge.
     *
     * @param edge a {@link edu.cmu.tetrad.graph.Edge} object
     * @return true iff the graph contain 'edge'.
     */
    boolean containsEdge(Edge edge);

    /**
     * Determines whether this graph contains the given node.
     *
     * @param node a {@link edu.cmu.tetrad.graph.Node} object
     * @return true iff the graph contains 'node'.
     */
    boolean containsNode(Node node);

    /**
     * Determines whether this graph is equal to some other graph, in the sense that they contain the same nodes and the
     * sets of edges defined over these nodes in the two graphs are isomorphic typewise. That is, if node A and B exist
     * in both graphs, and if there are, e.g., three edges between A and B in the first graph, two of which are directed
     * edges and one of which is an undirected edge, then in the second graph there must also be two directed edges and
     * one undirected edge between nodes A and B.
     *
     * @param o a {@link java.lang.Object} object
     * @return a boolean
     */
    boolean equals(Object o);

    /**
     * Removes all edges from the graph and fully connects it using #-# edges, where # is the given endpoint.
     *
     * @param endpoint a {@link edu.cmu.tetrad.graph.Endpoint} object
     */
    void fullyConnect(Endpoint endpoint);

    /**
     * Reorients all edges in the graph with the given endpoint.
     *
     * @param endpoint a {@link edu.cmu.tetrad.graph.Endpoint} object
     */
    void reorientAllWith(Endpoint endpoint);

    /**
     * <p>getAdjacentNodes.</p>
     *
     * @param node a {@link edu.cmu.tetrad.graph.Node} object
     * @return a mutable list of nodes adjacent to the given node.
     */
    List<Node> getAdjacentNodes(Node node);

    /**
     * <p>getChildren.</p>
     *
     * @param node a {@link edu.cmu.tetrad.graph.Node} object
     * @return a mutable list of children for a node.
     */
    List<Node> getChildren(Node node);

    /**
     * <p>getDegree.</p>
     *
     * @return the connectivity of the graph.
     */
    int getDegree();

    /**
     * <p>getEdge.</p>
     *
     * @param node1 a {@link edu.cmu.tetrad.graph.Node} object
     * @param node2 a {@link edu.cmu.tetrad.graph.Node} object
     * @return the edge connecting node1 and node2, provided a unique such edge exists.
     * @throws java.lang.UnsupportedOperationException if the graph allows multiple edges between node pairs.
     */
    Edge getEdge(Node node1, Node node2);

    /**
     * <p>getDirectedEdge.</p>
     *
     * @param node1 a {@link edu.cmu.tetrad.graph.Node} object
     * @param node2 a {@link edu.cmu.tetrad.graph.Node} object
     * @return the directed edge from node1 to node2, if there is one.
     * @throws java.lang.UnsupportedOperationException if the graph allows multiple edges between node pairs.
     */
    Edge getDirectedEdge(Node node1, Node node2);

    /**
     * <p>getEdges.</p>
     *
     * @param node a {@link edu.cmu.tetrad.graph.Node} object
     * @return the set of edges connected to a particular node. No particular ordering of the edges in the list is
     * guaranteed.
     */
    Set<Edge> getEdges(Node node);

    /**
     * <p>getEdges.</p>
     *
     * @param node1 a {@link edu.cmu.tetrad.graph.Node} object
     * @param node2 a {@link edu.cmu.tetrad.graph.Node} object
     * @return the edges connecting node1 and node2.
     */
    List<Edge> getEdges(Node node1, Node node2);

    /**
     * <p>getEdges.</p>
     *
     * @return the set of edges in the graph.  No particular ordering of the edges in the list is guaranteed.
     */
    Set<Edge> getEdges();

    /**
     * <p>getEndpoint.</p>
     *
     * @param node1 a {@link edu.cmu.tetrad.graph.Node} object
     * @param node2 a {@link edu.cmu.tetrad.graph.Node} object
     * @return the endpoint along the edge from node to node2 at the node2 end.
     */
    Endpoint getEndpoint(Node node1, Node node2);

    /**
     * <p>getIndegree.</p>
     *
     * @param node a {@link edu.cmu.tetrad.graph.Node} object
     * @return the number of arrow endpoints adjacent to a node.
     */
    int getIndegree(Node node);

    /**
     * <p>getDegree.</p>
     *
     * @param node a {@link edu.cmu.tetrad.graph.Node} object
     * @return the number of arrow endpoints adjacent to a node.
     */
    int getDegree(Node node);

    /**
     * <p>getNode.</p>
     *
     * @param name a {@link java.lang.String} object
     * @return the node with the given string name.  In case of accidental duplicates, the first node encountered with
     * the given name is returned. In case no node exists with the given name, null is returned.
     */
    Node getNode(String name);

    /**
     * <p>getNodes.</p>
     *
     * @return the list of nodes for the graph.
     */
    List<Node> getNodes();

    /**
     * <p>setNodes.</p>
     *
     * @param nodes a {@link java.util.List} object
     */
    void setNodes(List<Node> nodes);

    /**
     * <p>getNodeNames.</p>
     *
     * @return the names of the nodes, in the order of <code>getNodes</code>.
     */
    List<String> getNodeNames();

    /**
     * <p>getNumEdges.</p>
     *
     * @return the number of edges in the (entire) graph.
     */
    int getNumEdges();

    /**
     * <p>getNumEdges.</p>
     *
     * @param node a {@link edu.cmu.tetrad.graph.Node} object
     * @return the number of edges in the graph which are connected to a particular node.
     */
    int getNumEdges(Node node);

    /**
     * <p>getNumNodes.</p>
     *
     * @return the number of nodes in the graph.
     */
    int getNumNodes();

    /**
     * <p>getOutdegree.</p>
     *
     * @param node a {@link edu.cmu.tetrad.graph.Node} object
     * @return the number of null endpoints adjacent to an edge.
     */
    int getOutdegree(Node node);

    /**
     * <p>getParents.</p>
     *
     * @param node a {@link edu.cmu.tetrad.graph.Node} object
     * @return the list of parents for a node.
     */
    List<Node> getParents(Node node);

    /**
     * <p>isAdjacentTo.</p>
     *
     * @param node1 a {@link edu.cmu.tetrad.graph.Node} object
     * @param node2 a {@link edu.cmu.tetrad.graph.Node} object
     * @return true iff node1 is adjacent to node2 in the graph.
     */
    boolean isAdjacentTo(Node node1, Node node2);

    /**
     * <p>isChildOf.</p>
     *
     * @param node1 a {@link edu.cmu.tetrad.graph.Node} object
     * @param node2 a {@link edu.cmu.tetrad.graph.Node} object
     * @return true iff node1 is a child of node2 in the graph.
     */
    boolean isChildOf(Node node1, Node node2);

    /**
     * Determines whether node1 is a parent of node2.
     *
     * @param node1 a {@link edu.cmu.tetrad.graph.Node} object
     * @param node2 a {@link edu.cmu.tetrad.graph.Node} object
     * @return a boolean
     */
    boolean isParentOf(Node node1, Node node2);

    /**
     * Added by ekorber, 2004/6/9.
     *
     * @param node1 a {@link edu.cmu.tetrad.graph.Node} object
     * @param node2 a {@link edu.cmu.tetrad.graph.Node} object
     * @param node3 a {@link edu.cmu.tetrad.graph.Node} object
     * @return true if node 2 is a definite noncollider between 1 and 3
     */
    boolean isDefNoncollider(Node node1, Node node2, Node node3);

    /**
     * Added by ekorber, 2004/6/9.
     *
     * @param node1 a {@link edu.cmu.tetrad.graph.Node} object
     * @param node2 a {@link edu.cmu.tetrad.graph.Node} object
     * @param node3 a {@link edu.cmu.tetrad.graph.Node} object
     * @return true if node 2 is a definite collider between 1 and 3
     */
    boolean isDefCollider(Node node1, Node node2, Node node3);

    /**
     * <p>isExogenous.</p>
     *
     * @param node a {@link edu.cmu.tetrad.graph.Node} object
     * @return true iff the given node is exogenous in the graph.
     */
    boolean isExogenous(Node node);

    /**
     * Nodes adjacent to the given node with the given proximal endpoint.
     *
     * @param node a {@link edu.cmu.tetrad.graph.Node} object
     * @param n    a {@link edu.cmu.tetrad.graph.Endpoint} object
     * @return a {@link java.util.List} object
     */
    List<Node> getNodesInTo(Node node, Endpoint n);

    /**
     * Nodes adjacent to the given node with the given distal endpoint.
     *
     * @param node a {@link edu.cmu.tetrad.graph.Node} object
     * @param n    a {@link edu.cmu.tetrad.graph.Endpoint} object
     * @return a {@link java.util.List} object
     */
    List<Node> getNodesOutTo(Node node, Endpoint n);

    /**
     * Removes the given edge from the graph.
     *
     * @param edge a {@link edu.cmu.tetrad.graph.Edge} object
     * @return true if the edge was removed, false if not.
     */
    boolean removeEdge(Edge edge);

    /**
     * Removes an edge between two given nodes.
     *
     * @param node1 The first node.
     * @param node2 The second node.
     * @return true if the edge between node1 and node2 was successfully removed, false otherwise.
     */
    boolean removeEdge(Node node1, Node node2);

    /**
     * Removes all edges connecting node A to node B.  In most cases, this will remove at most one edge, but since
     * multiple edges are permitted in some graph implementations, the number will in some cases be greater than one.
     *
     * @param node1 a {@link edu.cmu.tetrad.graph.Node} object
     * @param node2 a {@link edu.cmu.tetrad.graph.Node} object
     * @return true if edges were removed, false if not.
     */
    boolean removeEdges(Node node1, Node node2);

    /**
     * Iterates through the list and removes any permissible edges found.  The order in which edges are added is the
     * order in which they are presented in the iterator.
     *
     * @param edges a {@link java.util.Collection} object
     * @return true if edges were added, false if not.
     */
    boolean removeEdges(Collection<Edge> edges);

    /**
     * Removes a node from the graph.
     *
     * @param node a {@link edu.cmu.tetrad.graph.Node} object
     * @return true if the node was removed, false if not.
     */
    boolean removeNode(Node node);

    /**
     * Iterates through the list and removes any permissible nodes found.  The order in which nodes are removed is the
     * order in which they are presented in the iterator.
     *
     * @param nodes a {@link java.util.List} object
     * @return true if nodes were added, false if not.
     */
    boolean removeNodes(List<Node> nodes);

    /**
     * Sets the endpoint type at the 'to' end of the edge from 'from' to 'to' to the given endpoint.  Note: NOT
     * CONSTRAINT SAFE
     *
     * @param from     a {@link edu.cmu.tetrad.graph.Node} object
     * @param to       a {@link edu.cmu.tetrad.graph.Node} object
     * @param endPoint a {@link edu.cmu.tetrad.graph.Endpoint} object
     * @return a boolean
     */
    boolean setEndpoint(Node from, Node to, Endpoint endPoint);

    /**
     * Constructs and returns a subgraph consisting of a given subset of the nodes of this graph together with the edges
     * between them.
     *
     * @param nodes a {@link java.util.List} object
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    Graph subgraph(List<Node> nodes);

    /**
     * <p>toString.</p>
     *
     * @return a string representation of the graph.
     */
    String toString();

    /**
     * Transfers nodes and edges from one graph to another.  One way this is used is to change graph types.  One
     * constructs a new graph based on the old graph, and this method is called to transfer the nodes and edges of the
     * old graph to the new graph.
     *
     * @param graph the graph from which nodes and edges are to be pilfered.
     * @throws java.lang.IllegalArgumentException This exception is thrown if adding some node.
     */
    void transferNodesAndEdges(Graph graph) throws IllegalArgumentException;

    /**
     * <p>transferAttributes.</p>
     *
     * @param graph a {@link edu.cmu.tetrad.graph.Graph} object
     * @throws java.lang.IllegalArgumentException if any.
     */
    void transferAttributes(Graph graph) throws IllegalArgumentException;

    /**
     * <p>paths.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Paths} object
     */
    Paths paths();

    /**
     * <p>isParameterizable.</p>
     *
     * @param node a {@link edu.cmu.tetrad.graph.Node} object
     * @return true if the given node is parameterizable.
     */
    boolean isParameterizable(Node node);

    /**
     * <p>isTimeLagModel.</p>
     *
     * @return true if this is a time lag model, in which case getTimeLagGraph() returns the graph.
     */
    boolean isTimeLagModel();

    /**
     * <p>getTimeLagGraph.</p>
     *
     * @return the underlying time lag model, if there is one; otherwise, returns null.
     */
    TimeLagGraph getTimeLagGraph();

    /**
     * <p>getSepset.</p>
     *
     * @param n1 a {@link edu.cmu.tetrad.graph.Node} object
     * @param n2 a {@link edu.cmu.tetrad.graph.Node} object
     * @return a {@link java.util.Set} object
     */
    Set<Node> getSepset(Node n1, Node n2);

    /**
     * <p>getAllAttributes.</p>
     *
     * @return a {@link java.util.Map} object
     */
    Map<String, Object> getAllAttributes();

    /**
     * <p>getAttribute.</p>
     *
     * @param key a {@link java.lang.String} object
     * @return a {@link java.lang.Object} object
     */
    Object getAttribute(String key);

    /**
     * <p>removeAttribute.</p>
     *
     * @param key a {@link java.lang.String} object
     */
    void removeAttribute(String key);

    /**
     * <p>addAttribute.</p>
     *
     * @param key   a {@link java.lang.String} object
     * @param value a {@link java.lang.Object} object
     */
    void addAttribute(String key, Object value);

    /**
     * <p>getUnderLines.</p>
     *
     * @return a {@link java.util.Set} object
     */
    Set<Triple> getUnderLines();

    /**
     * <p>getDottedUnderlines.</p>
     *
     * @return a {@link java.util.Set} object
     */
    Set<Triple> getDottedUnderlines();

    /**
     * <p>getAmbiguousTriples.</p>
     *
     * @return a {@link java.util.Set} object
     */
    Set<Triple> getAmbiguousTriples();

    /**
     * <p>setAmbiguousTriples.</p>
     *
     * @param triples a {@link java.util.Set} object
     */
    void setAmbiguousTriples(Set<Triple> triples);

    /**
     * States whether r-s-r is an underline triple or not.
     *
     * @param x a {@link edu.cmu.tetrad.graph.Node} object
     * @param y a {@link edu.cmu.tetrad.graph.Node} object
     * @param z a {@link edu.cmu.tetrad.graph.Node} object
     * @return a boolean
     */
    boolean isAmbiguousTriple(Node x, Node y, Node z);

    /**
     * States whether r-s-r is an underline triple or not.
     *
     * @param x a {@link edu.cmu.tetrad.graph.Node} object
     * @param y a {@link edu.cmu.tetrad.graph.Node} object
     * @param z a {@link edu.cmu.tetrad.graph.Node} object
     * @return a boolean
     */
    boolean isUnderlineTriple(Node x, Node y, Node z);

    /**
     * <p>addAmbiguousTriple.</p>
     *
     * @param x a {@link edu.cmu.tetrad.graph.Node} object
     * @param y a {@link edu.cmu.tetrad.graph.Node} object
     * @param z a {@link edu.cmu.tetrad.graph.Node} object
     */
    void addAmbiguousTriple(Node x, Node y, Node z);

    /**
     * <p>addUnderlineTriple.</p>
     *
     * @param x a {@link edu.cmu.tetrad.graph.Node} object
     * @param y a {@link edu.cmu.tetrad.graph.Node} object
     * @param z a {@link edu.cmu.tetrad.graph.Node} object
     */
    void addUnderlineTriple(Node x, Node y, Node z);

    /**
     * <p>addDottedUnderlineTriple.</p>
     *
     * @param x a {@link edu.cmu.tetrad.graph.Node} object
     * @param y a {@link edu.cmu.tetrad.graph.Node} object
     * @param z a {@link edu.cmu.tetrad.graph.Node} object
     */
    void addDottedUnderlineTriple(Node x, Node y, Node z);

    /**
     * <p>removeAmbiguousTriple.</p>
     *
     * @param x a {@link edu.cmu.tetrad.graph.Node} object
     * @param y a {@link edu.cmu.tetrad.graph.Node} object
     * @param z a {@link edu.cmu.tetrad.graph.Node} object
     */
    void removeAmbiguousTriple(Node x, Node y, Node z);

    /**
     * <p>removeUnderlineTriple.</p>
     *
     * @param x a {@link edu.cmu.tetrad.graph.Node} object
     * @param y a {@link edu.cmu.tetrad.graph.Node} object
     * @param z a {@link edu.cmu.tetrad.graph.Node} object
     */
    void removeUnderlineTriple(Node x, Node y, Node z);

    /**
     * <p>removeDottedUnderlineTriple.</p>
     *
     * @param x a {@link edu.cmu.tetrad.graph.Node} object
     * @param y a {@link edu.cmu.tetrad.graph.Node} object
     * @param z a {@link edu.cmu.tetrad.graph.Node} object
     */
    void removeDottedUnderlineTriple(Node x, Node y, Node z);

    /**
     * <p>setUnderLineTriples.</p>
     *
     * @param triples a {@link java.util.Set} object
     */
    void setUnderLineTriples(Set<Triple> triples);

    /**
     * <p>setDottedUnderLineTriples.</p>
     *
     * @param triples a {@link java.util.Set} object
     */
    void setDottedUnderLineTriples(Set<Triple> triples);

    /**
     * <p>removeTriplesNotInGraph.</p>
     */
    void removeTriplesNotInGraph();
}





