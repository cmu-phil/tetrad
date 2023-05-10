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
 * Implements a graph capable of storing edges of type N1 *-# N2 where * and
 * # are endpoints of type Endpoint.<p>We stipulate by extending
 * TetradSerializable that all graphs implementing this interface are
 * serializable. This is because for Tetrad they must be serializable. (For
 * randomUtil, in order to be able to cancelAll operations, they must be
 * serializable.)
 *
 * @author josephramsey
 * @see Endpoint
 */
public interface Graph extends TetradSerializable {
    long serialVersionUID = 23L;

    /**
     * Adds a bidirected edges &lt;-&gt; to the graph.
     */
    boolean addBidirectedEdge(Node node1, Node node2);

    /**
     * Adds a directed edge --&gt; to the graph.
     */
    boolean addDirectedEdge(Node node1, Node node2);

    /**
     * Adds an undirected edge --- to the graph.
     */
    boolean addUndirectedEdge(Node node1, Node node2);

    /**
     * Adds a nondirected edges o-o to the graph.
     */
    boolean addNondirectedEdge(Node node1, Node node2);

    /**
     * Adds a partially oriented edge o-&gt; to the graph.
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
     * @return a mutable list of children for a node.
     */
    List<Node> getChildren(Node node);

    /**
     * @return the connectivity of the graph.
     */
    int getDegree();

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
     * @return the set of edges in the graph.  No particular ordering of the
     * edges in the list is guaranteed.
     */
    Set<Edge> getEdges();

    /**
     * @return the endpoint along the edge from node to node2 at the node2 end.
     */
    Endpoint getEndpoint(Node node1, Node node2);

    /**
     * @return the number of arrow endpoints adjacent to a node.
     */
    int getIndegree(Node node);

    /**
     * @return the number of arrow endpoints adjacent to a node.
     */
    int getDegree(Node node);

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
     * @return true iff node1 is a child of node2 in the graph.
     */
    boolean isChildOf(Node node1, Node node2);

    /**
     * Determines whether node1 is a parent of node2.
     */
    boolean isParentOf(Node node1, Node node2);

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
     * @throws java.lang.IllegalArgumentException This exception is thrown if adding some node.
     */
    void transferNodesAndEdges(Graph graph) throws IllegalArgumentException;

    void transferAttributes(Graph graph) throws IllegalArgumentException;

    Underlines underlines();

    Paths paths();

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

    List<Node> getSepset(Node n1, Node n2);

    void setNodes(List<Node> nodes);

    Map<String, Object> getAllAttributes();

    Object getAttribute(String key);

    void removeAttribute(String key);

    void addAttribute(String key, Object value);
}





