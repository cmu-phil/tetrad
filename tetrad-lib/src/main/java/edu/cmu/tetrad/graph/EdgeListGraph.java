/// ////////////////////////////////////////////////////////////////////////////
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

import edu.cmu.tetrad.search.IndependenceTest;
import org.apache.commons.lang3.tuple.Pair;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.util.*;

import static edu.cmu.tetrad.graph.Edges.directedEdge;

/**
 * <p>
 * Stores a graph a list of lists of edges adjacent to each node in the graph, with an additional list storing all of
 * the edges in the graph. The edges are of the form N1 *-# N2. Multiple edges may be added per node pair to this graph,
 * with the caveat that all edges of the form N1 *-# N2 will be considered equal. For example, if the edge X --&gt; Y is
 * added to the graph, another edge X --&gt; Y may not be added, although an edge Y --&gt; X may be added. Edges from
 * nodes to themselves may also be added.&gt; 0
 *
 * @author josephramsey
 * @author Erin Korber additions summer 2004
 * @version $Id: $Id
 * @see edu.cmu.tetrad.graph.Endpoint
 */
public class EdgeListGraph implements Graph, TripleClassifier {

    @Serial
    private static final long serialVersionUID = 23L;
    /**
     * The edges in the graph.
     */
    final Set<Edge> edgesSet;
    /**
     * A list of the nodes in the graph, in the order in which they were added.
     */
    private final List<Node> nodes;
    /**
     * A hash from node names to nodes;
     */
    private final Map<String, Node> namesHash;

    /**
     * The attributes.
     */
    private final Map<String, Object> attributes = new HashMap<>();
    private final Map<Pair<Node, Node>, Boolean> ancestorRecord = new HashMap<>();
    /**
     * Map from each node to the List of edges connected to that node.
     */
    Map<Node, Set<Edge>> edgeLists;
    /**
     * The property change support.
     */
    private transient PropertyChangeSupport pcs;
    /**
     * The underline triples.
     */
    private Set<Triple> underLineTriples = new HashSet<>();
    /**
     * The dotted underline triples.
     */
    private Set<Triple> dottedUnderLineTriples = new HashSet<>();
    /**
     * The ambiguous triples.
     */
    private Set<Triple> ambiguousTriples = new HashSet<>();

    //==============================CONSTUCTORS===========================//
    /**
     * The parents hash.
     */
    private Map<Node, List<Node>> parentsHash = new HashMap<>();

    /**
     * Constructs a new (empty) EdgeListGraph.
     */
    public EdgeListGraph() {
        this.edgeLists = new HashMap<>();
        this.nodes = new ArrayList<>();
        this.edgesSet = new HashSet<>();
        this.namesHash = new HashMap<>();
//        this.paths = new Paths(this);
//        this.underLineTriples = new HashSet<>();
//        this.dottedUnderLineTriples = new HashSet<>();
//        this.ambiguousTriples = new HashSet<>();

    }

    /**
     * Constructs a EdgeListGraph using the nodes and edges of the given graph. If this cannot be accomplished
     * successfully, an exception is thrown. Note that any graph constraints from the given graph are forgotten in the
     * new graph.
     *
     * @param graph the graph from which nodes and edges are is to be extracted.
     * @throws java.lang.IllegalArgumentException if a duplicate edge is added.
     */
    public EdgeListGraph(Graph graph) throws IllegalArgumentException {
        this();

        if (graph == null) {
            throw new NullPointerException("Graph must not be null.");
        }

        transferNodesAndEdges(graph);

        // Keep attributes from the original graph
        transferAttributes(graph);

        for (Node node : this.nodes) {
            this.namesHash.put(node.getName(), node);
        }

        this.underLineTriples = graph.getUnderLines();
        this.dottedUnderLineTriples = graph.getDottedUnderlines();
        this.ambiguousTriples = graph.getAmbiguousTriples();
    }

    /**
     * <p>Constructor for EdgeListGraph.</p>
     *
     * @param graph a {@link edu.cmu.tetrad.graph.EdgeListGraph} object
     * @throws java.lang.IllegalArgumentException if any.
     */
    public EdgeListGraph(EdgeListGraph graph) throws IllegalArgumentException {
        this.nodes = new ArrayList<>(graph.nodes);
        this.edgeLists = new HashMap<>();
        for (Node node : nodes) {
            edgeLists.put(node, Collections.unmodifiableSet(graph.edgeLists.get(node)));
        }
        this.edgesSet = new HashSet<>(graph.edgesSet);
        this.namesHash = new HashMap<>(graph.namesHash);
        this.parentsHash = new HashMap<>(graph.parentsHash);
//        this.paths = new Paths(this);

        this.underLineTriples = graph.getUnderLines();
        this.dottedUnderLineTriples = graph.getDottedUnderlines();
        this.ambiguousTriples = graph.getAmbiguousTriples();

    }

    /**
     * Constructs a new graph, with no edges, using the given variable names.
     *
     * @param nodes a {@link java.util.List} object
     */
    public EdgeListGraph(List<Node> nodes) {
        this();

        if (nodes == null) {
            throw new NullPointerException();
        }

        for (Node variable : nodes) {
            if (!addNode(variable)) {
                throw new IllegalArgumentException();
            }
        }

        for (Node node : nodes) {
            this.namesHash.put(node.getName(), node);
        }
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetrad.graph.EdgeListGraph} object
     */
    public static EdgeListGraph serializableInstance() {
        return new EdgeListGraph();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Adds a directed edge to the graph from node A to node B.
     */
    @Override
    public boolean addDirectedEdge(Node node1, Node node2) {
        if (node1 == null || node2 == null) return false;
        return addEdge(directedEdge(node1, node2));
    }

    /**
     * {@inheritDoc}
     * <p>
     * Adds an undirected edge to the graph from node A to node B.
     */
    @Override
    public boolean addUndirectedEdge(Node node1, Node node2) {
        if (node1 == null || node2 == null) return false;
        return addEdge(Edges.undirectedEdge(node1, node2));
    }

    /**
     * {@inheritDoc}
     * <p>
     * Adds a nondirected edge to the graph from node A to node B.
     */
    @Override
    public boolean addNondirectedEdge(Node node1, Node node2) {
        if (node1 == null || node2 == null) return false;
        return addEdge(Edges.nondirectedEdge(node1, node2));
    }

    /**
     * {@inheritDoc}
     * <p>
     * Adds a partially oriented edge to the graph from node A to node B.
     */
    @Override
    public boolean addPartiallyOrientedEdge(Node node1, Node node2) {
        if (node1 == null || node2 == null) return false;
        return addEdge(Edges.partiallyOrientedEdge(node1, node2));
    }

    /**
     * {@inheritDoc}
     * <p>
     * Adds a bidirected edge to the graph from node A to node B.
     */
    @Override
    public boolean addBidirectedEdge(Node node1, Node node2) {
        if (node1 == null || node2 == null) return false;
        return addEdge(Edges.bidirectedEdge(node1, node2));
    }

    /**
     * {@inheritDoc}
     * <p>
     * IllegalArgument exception raised (by isDirectedFromTo(getEndpoint) or by getEdge) if there are multiple edges
     * between any of the node pairs.
     */
    @Override
    public boolean isDefNoncollider(Node node1, Node node2, Node node3) {
        if (node1 == null || node2 == null || node3 == null) return false;
        Set<Edge> edges = getEdges(node2);
        boolean circle12 = false;
        boolean circle32 = false;

        // Sufficient. Check to see if in the middle node either of the edges has a tail.

        // If an unshielded triple and either one is a circle, it's a definitely noncollider.

        // Zhang 2008 other paper, 1446

        // tail out or both circles and covered.

        for (Edge edge : edges) {
            boolean _node1 = edge.getDistalNode(node2) == node1;
            boolean _node3 = edge.getDistalNode(node2) == node3;

            if (_node1 && edge.pointsTowards(node1)) {
                return true;
            }
            if (_node3 && edge.pointsTowards(node3)) {
                return true;
            }

            if (_node1 && edge.getProximalEndpoint(node2) == Endpoint.CIRCLE) {
                circle12 = true;
            }
            if (_node3 && edge.getProximalEndpoint(node2) == Endpoint.CIRCLE) {
                circle32 = true;
            }
            if (circle12 && circle32 && !isAdjacentTo(node1, node2)) {
                return true;
            }
        }

        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isDefCollider(Node node1, Node node2, Node node3) {
        if (node1 == null || node2 == null || node3 == null) return false;
        Edge edge1 = getEdge(node1, node2);
        Edge edge2 = getEdge(node2, node3);

        if (edge1 == null || edge2 == null) return false;

        return edge1.getProximalEndpoint(node2) == Endpoint.ARROW && edge2.getProximalEndpoint(node2) == Endpoint.ARROW;

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized List<Node> getChildren(Node node) {
        List<Node> children = new ArrayList<>();

        for (Edge edge : getEdges(node)) {
            if (Edges.isDirectedEdge(edge)) {
                Node sub = Edges.traverseDirected(node, edge);

                if (sub != null) {
                    children.add(sub);
                }
            }
        }

        return children;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getDegree() {
        int connectivity = 0;

        List<Node> nodes = getNodes();

        for (Node node : nodes) {
            int n = getNumEdges(node);
            if (n > connectivity) {
                connectivity = n;
            }
        }

        return connectivity;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Edge getEdge(Node node1, Node node2) {
        Set<Edge> edges = this.edgeLists.get(node1);

        if (edges == null) {
            return null;
        }

        for (Edge edge : edges) {
            if (edge.getNode1() == node1 && edge.getNode2() == node2) {
                return edge;
            } else if (edge.getNode1() == node2 && edge.getNode2() == node1) {
                return edge;
            }
        }

        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Edge getDirectedEdge(Node node1, Node node2) {
        List<Edge> edges = getEdges(node1, node2);

        if (edges == null) {
            return null;
        }

        if (edges.size() == 0) {
            return null;
        }

        for (Edge edge : edges) {
            if (Edges.isDirectedEdge(edge) && edge.getProximalEndpoint(node2) == Endpoint.ARROW) {
                return edge;
            }
        }

        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized List<Node> getParents(Node node) {
        if (!parentsHash.containsKey(node)) {
            List<Node> parents = new ArrayList<>();
            Set<Edge> edges = this.edgeLists.get(node);

            if (edges == null) {
                throw new IllegalArgumentException("Node " + node + " is not in the graph.");
            }

            for (Edge edge : edges) {
                if (edge == null) continue;

                Endpoint endpoint1 = edge.getDistalEndpoint(node);
                Endpoint endpoint2 = edge.getProximalEndpoint(node);

                if (endpoint1 == Endpoint.TAIL && endpoint2 == Endpoint.ARROW) {
                    parents.add(edge.getDistalNode(node));
                }
            }

            parentsHash.put(node, parents);
        }

        return parentsHash.get(node);
    }

    /**
     * Determines whether one node is an ancestor of another.
     * @param node1 The first node.
     * @param node2 The second node.
     * @return True if the first node is an ancestor of the second, false if not.
     */
    public boolean isAncestorOf(Node node1, Node node2) {
        Boolean ancestor = ancestorRecord.get(Pair.of(node1, node2));

        if (ancestor == null) {
            ancestor = node1 == node2 || paths().existsDirectedPath(node1, node2);
            ancestorRecord.put(Pair.of(node1, node2), ancestor);
        }

        return ancestor;

//        return node1 == node2 || paths().existsDirectedPath(node1, node2);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getIndegree(Node node) {
        return getParents(node).size();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getDegree(Node node) {
        return this.edgeLists.get(node).size();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getOutdegree(Node node) {
        return getChildren(node).size();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Determines whether some edge or other exists between two nodes.
     */
    @Override
    public boolean isAdjacentTo(Node node1, Node node2) {
        if (!this.edgeLists.containsKey(node1) || !this.edgeLists.containsKey(node2)) {
            return false;
        }

        for (Edge edge : this.edgeLists.get(node1)) {
            if (Edges.traverse(node1, edge) == node2) {
                return true;
            }
        }

        return false;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Determines whether one node is a child of another.
     */
    @Override
    public boolean isChildOf(Node node1, Node node2) {
        for (Edge edge : getEdges(node2)) {
            Node sub = Edges.traverseDirected(node2, edge);

            if (sub == node1) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<Node> getSepset(Node x, Node y, IndependenceTest test) {
        return new Paths(this).getSepset(x, y, false, test, -1);
    }

    /**
     * Retrieves the set of nodes that form the sepset between two given nodes. This method needs specifically to be
     * called on the EdgeListGraph class, as it is not implemented in the Graph interface.
     *
     * @param x         The first node.
     * @param y         The second node.
     * @param maxLength The maximum length of the paths to consider.
     * @return The set of nodes that form the sepset between the two given nodes.
     */
    public Set<Node> getSepset(Node x, Node y, int maxLength) {
        return new Paths(this).getSepsetContaining(x, y, new HashSet<>(), maxLength);
    }

    /**
     * Retrieves the set of nodes that form the sepset between two given nodes. This method needs specifically
     *
     * @param x          The first node.
     * @param y          The second node.
     * @param containing The set of nodes that must be contained in the sepset.
     * @param maxLength  The maximum length of the paths to consider.
     * @return The set of nodes that form the sepset between the two given nodes.
     */
    public Set<Node> getSepsetContaining(Node x, Node y, Set<Node> containing, int maxLength) {
        return new Paths(this).getSepsetContaining(x, y, containing, maxLength);
    }

    /**
     * Determines whether x and y are d-separated given z.
     *
     * @param x a {@link edu.cmu.tetrad.graph.Node} object
     * @param y a {@link edu.cmu.tetrad.graph.Node} object
     * @param z a {@link java.util.Set} object
     * @return True if the nodes in x are all d-separated from nodes in y given  nodes in z, false if not.
     */
    public boolean isMSeparatedFrom(Node x, Node y, Set<Node> z) {
        return !new Paths(this).isMConnectedTo(x, y, z, false);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Determines whether one node is a parent of another.
     *
     * @see #isChildOf
     * @see #getParents
     * @see #getChildren
     */
    @Override
    public boolean isParentOf(Node node1, Node node2) {
        for (Edge edge : getEdges(node1)) {
            Node sub = Edges.traverseDirected(node1, (edge));

            if (sub == node2) {
                return true;
            }
        }

        return false;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Transfers nodes and edges from one graph to another. One way this is used is to change graph types. One
     * constructs a new graph based on the old graph, and this method is called to transfer the nodes and edges of the
     * old graph to the new graph.
     */
    @Override
    public void transferNodesAndEdges(Graph graph)
            throws IllegalArgumentException {
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

    /**
     * {@inheritDoc}
     */
    @Override
    public void transferAttributes(Graph graph)
            throws IllegalArgumentException {
        if (graph == null) {
            throw new NullPointerException("No graph was provided.");
        }

        this.attributes.putAll(graph.getAllAttributes());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Paths paths() {
        return new Paths(this);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Determines whether a node in a graph is exogenous.
     */
    @Override
    public boolean isExogenous(Node node) {
        return getIndegree(node) == 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Node> getAdjacentNodes(Node node) {
        Set<Edge> edges = this.edgeLists.get(node);
        Set<Node> adj = new HashSet<>();

        if (edges != null) {
            for (Edge edge : edges) {
                if (edge == null) {
                    continue;
                }

                adj.add(edge.getDistalNode(node));
            }
        }

        return new ArrayList<>(adj);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Removes the edge connecting the two given nodes.
     */
    @Override
    public boolean removeEdge(Node node1, Node node2) {
        List<Edge> edges = getEdges(node1, node2);

        if (edges.size() > 1) {
            throw new IllegalStateException(
                    "There is more than one edge between " + node1 + " and "
                    + node2);
        }

        removeTriplesNotInGraph();

        parentsHash.remove(node1);
        parentsHash.remove(node2);

        return removeEdges(edges);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Endpoint getEndpoint(Node node1, Node node2) {
        Edge edge = getEdge(node1, node2);

        if (edge != null) {
            return edge.getProximalEndpoint(node2);
        }

        return null;
    }

    /**
     * {@inheritDoc}
     * <p>
     * If there is currently an edge from node1 to node2, sets the endpoint at node2 to the given endpoint; if there is
     * no such edge, adds an edge --# where # is the given endpoint. Setting an endpoint to null, provided there is
     * exactly one edge connecting the given nodes, removes the edge. (If there is more than one edge, an exception is
     * thrown.)
     */
    @Override
    public synchronized boolean setEndpoint(Node from, Node to, Endpoint endPoint)
            throws IllegalArgumentException {
        if (!isAdjacentTo(from, to)) throw new IllegalArgumentException("Not adjacent");

        Edge edge = getEdge(from, to);

        removeEdge(edge);

        Edge newEdge = new Edge(from, to,
                edge.getProximalEndpoint(from), endPoint);
        addEdge(newEdge);
        return true;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Nodes adjacent to the given node with the given proximal endpoint.
     */
    @Override
    public List<Node> getNodesInTo(Node node, Endpoint endpoint) {
        List<Node> nodes = new ArrayList<>();
        Set<Edge> edges = getEdges(node);

        for (Edge edge : edges) {
            if (edge.getProximalEndpoint(node) == endpoint) {
                nodes.add(edge.getDistalNode(node));
            }
        }

        return nodes;
    }

    /**
     * {@inheritDoc}
     * <p>
     * ( Nodes adjacent to the given node with the given distal endpoint.
     */
    @Override
    public List<Node> getNodesOutTo(Node node, Endpoint endpoint) {
        List<Node> nodes = new ArrayList<>();
        Set<Edge> edges = getEdges(node);

        for (Edge edge : edges) {
            if (edge.getDistalEndpoint(node) == endpoint) {
                nodes.add(edge.getDistalNode(node));
            }
        }

        return nodes;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Adds an edge to the graph.
     */
    @Override
    public boolean addEdge(Edge edge) {
        if (edge == null) {
            throw new NullPointerException("Null edge.");
        }

        Map<Node, Set<Edge>> edgeListMap = this.edgeLists;

//        synchronized (edgeListMap) {
        Node node1 = edge.getNode1();
        Node node2 = edge.getNode2();

        // Someoone may have changed the name of one of these variables, in which
        // case we need to reconstitute the edgeLists map, since the name of a
        // node is used part of the definition of node equality.
        if (!edgeLists.containsKey(node1) || !edgeLists.containsKey(node2)) {
            this.edgeLists = new HashMap<>(this.edgeLists);
        }

        // System.out.println("Missing node1 is not in edgeLists: " + node1);
        edgeLists.computeIfAbsent(node1, k -> new HashSet<>());
        // System.out.println("Missing node2 is not in edgeLists: " + node2);
        edgeLists.computeIfAbsent(node2, k -> new HashSet<>());

        Set<Edge> edges1 = new HashSet<>(this.edgeLists.get(node1));
        Set<Edge> edges2 = new HashSet<>(this.edgeLists.get(node2));
        edges1.add(edge);
        edges2.add(edge);
        this.edgeLists.put(node1, Collections.unmodifiableSet(edges1));
        this.edgeLists.put(node2, Collections.unmodifiableSet(edges2));
        this.edgesSet.add(edge);

        this.parentsHash.remove(node1);
        this.parentsHash.remove(node2);


        ancestorRecord.clear();
//        }

        if (Edges.isDirectedEdge(edge)) {
            Node node = Edges.getDirectedEdgeTail(edge);

            if (node.getNodeType() == NodeType.ERROR) {
                getPcs().firePropertyChange("nodeAdded", null, node);
            }
        }


        getPcs().firePropertyChange("edgeAdded", null, edge);
        return true;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Adds a node to the graph. Precondition: The proposed name of the node cannot already be used by any other node in
     * the same graph.
     */
    @Override
    public boolean addNode(Node node) {
        if (this.nodes.contains(node)) {
            return true;
        }

        if (node == null) {
            throw new NullPointerException();
        }

        if (!(getNode(node.getName()) == null)) {
            if (this.nodes.contains(node)) {
                this.namesHash.put(node.getName(), node);
            }
        }

        if (this.edgeLists.containsKey(node)) {
            return false;
        }

        this.edgeLists.put(node, new HashSet<>());
        this.nodes.add(node);
        this.namesHash.put(node.getName(), node);

        if (node.getNodeType() != NodeType.ERROR) {
            getPcs().firePropertyChange("nodeAdded", null, node);
        }

        ancestorRecord.clear();

        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized Set<Edge> getEdges() {
        return new HashSet<>(this.edgesSet);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Determines if the graph contains a particular edge.
     */
    @Override
    public boolean containsEdge(Edge edge) {
        return this.edgesSet.contains(edge);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Determines whether the graph contains a particular node.
     */
    @Override
    public boolean containsNode(Node node) {
        return this.nodes.contains(node);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<Edge> getEdges(Node node) {
        Set<Edge> edges = this.edgeLists.get(node);
        if (edges == null) {
            return new HashSet<>();
        }
        return new HashSet<>(edges);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        int hashCode = 0;

        for (Edge edge : getEdges()) {
            hashCode += edge.hashCode();
        }

        return (new HashSet<>(this.nodes)).hashCode() + hashCode;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o) {
        if (o == null) {
            return false;
        }

        if (o instanceof EdgeListGraph _o) {
            boolean nodesEqual = new HashSet<>(_o.nodes).equals(new HashSet<>(this.nodes));
            boolean edgesEqual = new HashSet<>(_o.edgesSet).equals(new HashSet<>(this.edgesSet));

            // to check discrepancies if necessary...
//            if (!edgesEqual) {
//                Set<Edge> edges1 = new HashSet<>(_o.edgesSet);
//                edges1.removeAll(this.edgesSet);
//
//                Set<Edge> edges2 = new HashSet<>(this.edgesSet);
//                edges2.removeAll(_o.edgesSet);
//
//                System.out.println("Edges in this graph but not in the other: " + edges1);
//                System.out.println("Edges in the other graph but not in this: " + edges2);
//            }

            return (nodesEqual && edgesEqual);
        } else {
            Graph graph = (Graph) o;
            return new HashSet<>(graph.getNodeNames()).equals(new HashSet<>(getNodeNames()))
                   && new HashSet<>(graph.getEdges()).equals(new HashSet<>(getEdges()));

        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Resets the graph so that it is fully connects it using #-# edges, where # is the given endpoint.
     */
    @Override
    public void fullyConnect(Endpoint endpoint) {
        this.edgesSet.clear();
        this.edgeLists.clear();
        this.parentsHash.clear();

        for (Node node : this.nodes) {
            this.edgeLists.put(node, new HashSet<>());
        }

        for (int i = 0; i < this.nodes.size(); i++) {
            for (int j = i + 1; j < this.nodes.size(); j++) {
                Node node1 = this.nodes.get(i);
                Node node2 = this.nodes.get(j);

                Edge edge = new Edge(node1, node2, endpoint, endpoint);
                addEdge(edge);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void reorientAllWith(Endpoint endpoint) {
        for (Edge edge : getEdges()) {
            removeEdge(edge);
            Edge edge2 = new Edge(edge);
            edge2.setEndpoint1(endpoint);
            edge2.setEndpoint2(endpoint);
            addEdge(edge2);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Node getNode(String name) {
        return this.namesHash.get(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getNumNodes() {
        return this.nodes.size();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getNumEdges() {
        return this.edgesSet.size();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getNumEdges(Node node) {
        Set<Edge> list = this.edgeLists.get(node);
        return (list == null) ? 0 : list.size();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Node> getNodes() {
        return new ArrayList<>(this.nodes);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setNodes(List<Node> nodes) {
        if (nodes.size() != this.nodes.size()) {
            throw new IllegalArgumentException("Sorry, there is a mismatch in the number of variables "
                                               + "you are trying to set.");
        }

        this.nodes.clear();
        this.nodes.addAll(nodes);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Removes all nodes (and therefore all edges) from the graph.
     */
    @Override
    public void clear() {
        Iterator<Edge> it = getEdges().iterator();

        while (it.hasNext()) {
            Edge edge = it.next();
            it.remove();
            getPcs().firePropertyChange("edgeRemoved", edge, null);
        }

        Iterator<Node> it2 = this.nodes.iterator();

        while (it2.hasNext()) {
            Node node = it2.next();
            it2.remove();
            this.namesHash.remove(node.getName());
            getPcs().firePropertyChange("nodeRemoved", node, null);
        }

        this.edgeLists.clear();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Removes an edge from the graph. (Note: It is dangerous to make a recursive call to this method (as it stands)
     * from a method containing certain types of iterators. The problem is that if one uses an iterator that iterates
     * over the edges of node A or node B, and tries in the process to remove those edges using this method, a
     * concurrent modification exception will be thrown.)
     */
    @Override
    public boolean removeEdge(Edge edge) {
        Map<Node, Set<Edge>> edgeLists = this.edgeLists;

        synchronized (edgeLists) {
            if (!this.edgesSet.contains(edge)) {
                return false;
            }

            Set<Edge> edgeList1 = this.edgeLists.get(edge.getNode1());
            Set<Edge> edgeList2 = this.edgeLists.get(edge.getNode2());

            edgeList1 = new HashSet<>(edgeList1);
            edgeList2 = new HashSet<>(edgeList2);

            this.edgesSet.remove(edge);
            edgeList1.remove(edge);
            edgeList2.remove(edge);

            this.edgeLists.put(edge.getNode1(), Collections.unmodifiableSet(edgeList1));
            this.edgeLists.put(edge.getNode2(), Collections.unmodifiableSet(edgeList2));

            this.parentsHash.remove(edge.getNode1());
            this.parentsHash.remove(edge.getNode2());

            ancestorRecord.clear();

            getPcs().firePropertyChange("edgeRemoved", edge, null);
            return true;
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Removes any relevant edge objects found in this collection. G
     */
    @Override
    public boolean removeEdges(Collection<Edge> edges) {
        boolean change = false;

        for (Edge edge : edges) {
            boolean _change = removeEdge(edge);
            change = change || _change;
        }

        return change;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Removes all edges connecting node A to node B.
     */
    @Override
    public boolean removeEdges(Node node1, Node node2) {
        return removeEdges(getEdges(node1, node2));
    }

    /**
     * {@inheritDoc}
     * <p>
     * Removes a node from the graph.
     */
    @Override
    public boolean removeNode(Node node) {
        if (!this.nodes.contains(node)) {
            return false;
        }

        boolean changed = false;
        Set<Edge> _edgeSet = this.edgeLists.get(node);
        if (_edgeSet == null) return true;
        Set<Edge> edgeSet1 = new HashSet<>(_edgeSet);    //list of edges connected to that node

        for (Iterator<Edge> i = edgeSet1.iterator(); i.hasNext(); ) {
            Edge edge = (i.next());
            Node node2 = edge.getDistalNode(node);

            if (node2 != node) {
                Set<Edge> edgeList2 = new HashSet<>(this.edgeLists.get(node2));
                edgeList2.remove(edge);
                this.edgeLists.put(node2, Collections.unmodifiableSet(edgeList2));
                this.edgesSet.remove(edge);
                this.parentsHash.remove(edge.getNode1());
                this.parentsHash.remove(edge.getNode2());
                changed = true;
            }

            i.remove();
            getPcs().firePropertyChange("edgeRemoved", edge, null);
        }

        this.edgeLists.remove(node);
        this.nodes.remove(node);
        this.parentsHash.remove(node);
        this.namesHash.remove(node.getName());

        removeTriplesNotInGraph();

        getPcs().firePropertyChange("nodeRemoved", node, null);
        return changed;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Removes any relevant node objects found in this collection.
     */
    @Override
    public boolean removeNodes(List<Node> newNodes) {
        boolean changed = false;

        for (Node node : newNodes) {
            boolean _changed = removeNode(node);
            changed = changed || _changed;
        }

        return changed;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return GraphSaveLoadUtils.loadGraphTxt(this, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph subgraph(List<Node> nodes) {
        Graph graph = new EdgeListGraph(nodes);
        Set<Edge> edges = getEdges();

        for (Edge edge : edges) {
            if (nodes.contains(edge.getNode1())
                && nodes.contains(edge.getNode2())) {
                graph.addEdge(edge);
            }
        }

        return graph;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Edge> getEdges(Node node1, Node node2) {
        Set<Edge> edges = this.edgeLists.get(node1);
        if (edges == null) {
            return new ArrayList<>();
        }

        List<Edge> _edges = new ArrayList<>();

        for (Edge edge : edges) {
            if (edge.getDistalNode(node1) == node2) {
                _edges.add(edge);
            }
        }

        return _edges;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getNodeNames() {
        List<String> names = new ArrayList<>();

        for (Node node : getNodes()) {
            names.add(node.getName());
        }

        return names;
    }

    //===============================PRIVATE METHODS======================//

    /**
     * <p>Getter for the field <code>pcs</code>.</p>
     *
     * @return this object.
     */
    protected PropertyChangeSupport getPcs() {
        if (this.pcs == null) {
            this.pcs = new PropertyChangeSupport(this);
        }
        return this.pcs;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isParameterizable(Node node) {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isTimeLagModel() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TimeLagGraph getTimeLagGraph() {
        return null;
    }

    /**
     * Adds semantic checks to the default deserialization method. This method must have the standard signature for a
     * readObject method, and the body of the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from version to version. A readObject method of
     * this form may be added to any class, even if Tetrad sessions were previously saved out using a version of the
     * class that didn't include it. (That's what the "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for
     * help.)
     *
     * @param s a {@link java.io.ObjectInputStream} object
     * @throws IOException            if any.
     * @throws ClassNotFoundException if any.
     */
    @Serial
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();
    }

    /**
     * <p>changeName.</p>
     *
     * @param name    a {@link java.lang.String} object
     * @param newName a {@link java.lang.String} object
     */
    public void changeName(String name, String newName) {
        Node node = this.namesHash.get(name);
        this.namesHash.remove(name);
        node.setName(newName);
        this.namesHash.put(newName, node);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, Object> getAllAttributes() {
        return this.attributes;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object getAttribute(String key) {
        return this.attributes.get(key);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeAttribute(String key) {
        this.attributes.remove(key);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addAttribute(String key, Object value) {
        this.attributes.put(key, value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addPropertyChangeListener(PropertyChangeListener l) {
        getPcs().addPropertyChangeListener(l);
    }

    /**
     * <p>Getter for the field <code>ambiguousTriples</code>.</p>
     *
     * @return a {@link java.util.Set} object
     */
    public Set<Triple> getAmbiguousTriples() {
        return new HashSet<>(this.ambiguousTriples);
    }

    /**
     * {@inheritDoc}
     */
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
     * <p>
     * States whether r-s-r is an underline triple or not.
     */
    public boolean isAmbiguousTriple(Node x, Node y, Node z) {
        return this.ambiguousTriples.contains(new Triple(x, y, z));
    }

    /**
     * {@inheritDoc}
     * <p>
     * States whether r-s-r is an underline triple or not.
     */
    public boolean isUnderlineTriple(Node x, Node y, Node z) {
        return this.underLineTriples.contains(new Triple(x, y, z));
    }

    /**
     * {@inheritDoc}
     */
    public void addAmbiguousTriple(Node x, Node y, Node z) {
        this.ambiguousTriples.add(new Triple(x, y, z));
    }

    /**
     * {@inheritDoc}
     */
    public void addUnderlineTriple(Node x, Node y, Node z) {
        Triple triple = new Triple(x, y, z);

        if (!triple.alongPathIn(this)) {
            return;
        }

        this.underLineTriples.add(new Triple(x, y, z));
    }

    /**
     * {@inheritDoc}
     */
    public void addDottedUnderlineTriple(Node x, Node y, Node z) {
        Triple triple = new Triple(x, y, z);

        if (!triple.alongPathIn(this)) {
            return;
        }

        this.dottedUnderLineTriples.add(triple);
    }

    /**
     * {@inheritDoc}
     */
    public void removeAmbiguousTriple(Node x, Node y, Node z) {
        this.ambiguousTriples.remove(new Triple(x, y, z));
    }

    /**
     * {@inheritDoc}
     */
    public void removeUnderlineTriple(Node x, Node y, Node z) {
        this.underLineTriples.remove(new Triple(x, y, z));
    }

    /**
     * {@inheritDoc}
     */
    public void removeDottedUnderlineTriple(Node x, Node y, Node z) {
        this.dottedUnderLineTriples.remove(new Triple(x, y, z));
    }

    /**
     * {@inheritDoc}
     */
    public void setUnderLineTriples(Set<Triple> triples) {
        this.underLineTriples.clear();

        for (Triple triple : triples) {
            addUnderlineTriple(triple.getX(), triple.getY(), triple.getZ());
        }
    }

    /**
     * {@inheritDoc}
     */
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


    /**
     * <p>getTriplesClassificationTypes.</p>
     *
     * @return the names of the triple classifications. Coordinates with
     * <code>getTriplesList</code>
     */
    public List<String> getTriplesClassificationTypes() {
        List<String> names = new ArrayList<>();
        names.add("Underlines");
        names.add("Dotted Underlines");
        names.add("Ambiguous Triples");
        return names;
    }


    /**
     * {@inheritDoc}
     */
    public List<List<Triple>> getTriplesLists(Node node) {
        List<List<Triple>> triplesList = new ArrayList<>();
        triplesList.add(GraphUtils.getUnderlinedTriplesFromGraph(node, this));
        triplesList.add(GraphUtils.getDottedUnderlinedTriplesFromGraph(node, this));
        triplesList.add(GraphUtils.getAmbiguousTriplesFromGraph(node, this));
        return triplesList;
    }
}
