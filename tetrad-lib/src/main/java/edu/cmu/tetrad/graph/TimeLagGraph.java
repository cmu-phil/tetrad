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

import edu.cmu.tetrad.search.IndependenceTest;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.Serial;
import java.util.*;

/**
 * Represents a time series graph--that is, a graph with a fixed number S of lags, with edges into initial lags
 * only--that is, into nodes in the first R lags, for some R. Edge structure repeats every R nodes.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class TimeLagGraph implements Graph {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * A node in a time lag graph.
     */
    private final Map<String, Object> attributes = new HashMap<>();
    /**
     * The set of underlined triples.
     */
    private final Set<Triple> underLineTriples = new HashSet<>();
    /**
     * The set of dotted underlined triples.
     */
    private final Set<Triple> dottedUnderLineTriples = new HashSet<>();
    /**
     * The set of ambiguous triples.
     */
    private final Set<Triple> ambiguousTriples = new HashSet<>();
    /**
     * Fires property change events.
     */
    private transient PropertyChangeSupport pcs;
    /**
     * A node in a time lag graph.
     */
    private EdgeListGraph graph = new EdgeListGraph();
    /**
     * The maximum lag.
     */
    private int maxLag = 1;
    /**
     * The number of initial lags.
     */
    private int numInitialLags = 1;
    /**
     * The nodes in lag 0.
     */
    private List<Node> lag0Nodes = new ArrayList<>();
    /**
     * Whether the graph is a PAG.
     */
    private boolean pag;
    /**
     * Whether the graph is a CPDAG.
     */
    private boolean cpdag;
    /**
     * The paths in the graph.
     */
    private Paths paths;

    /**
     * <p>Constructor for TimeLagGraph.</p>
     */
    public TimeLagGraph() {
    }

    /**
     * <p>Constructor for TimeLagGraph.</p>
     *
     * @param graph a {@link edu.cmu.tetrad.graph.TimeLagGraph} object
     */
    public TimeLagGraph(TimeLagGraph graph) {
        this.graph = new EdgeListGraph(graph.getGraph());
        this.maxLag = graph.getMaxLag();
        this.numInitialLags = graph.getNumInitialLags();
        this.lag0Nodes = graph.getLag0Nodes();
        this.pag = graph.pag;
        this.cpdag = graph.cpdag;
        this.paths = new Paths(this.graph);

        this.graph.addPropertyChangeListener(evt -> getPcs().firePropertyChange(evt));
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetrad.graph.TimeLagGraph} object
     */
    public static TimeLagGraph serializableInstance() {
        return new TimeLagGraph();
    }

    /**
     * Adds a node to the graph.
     *
     * @param node the node to be added
     * @return true if the node was added successfully, false otherwise
     */
    public boolean addNode(Node node) {

        NodeId id = getNodeId(node);

        if (id.getLag() != 0) {
            node = node.like(id.getName());
        }

        boolean added = getGraph().addNode(node);

        if (!this.lag0Nodes.contains(node) && !node.getName().startsWith("E_")) {
            this.lag0Nodes.add(node);
        }

        if (node.getNodeType() == NodeType.ERROR) {
            for (int i = 1; i <= getMaxLag(); i++) {
                Node node1 = node.like(id.getName() + ":" + i);

                if (i < getNumInitialLags()) {
                    getGraph().addNode(node1);
                }
            }
        } else {
            for (int i = 1; i <= getMaxLag(); i++) {
                String name = id.getName() + ":" + i;
                Node node1 = node.like(name);

                if (getGraph().getNode(name) == null) {
                    getGraph().addNode(node1);
                }
            }
        }

        getPcs().firePropertyChange("editingFinished", null, null);

        return added;
    }

    /**
     * Removes the given node from the graph.
     *
     * @param node the node to be removed
     * @return true if the node was successfully removed from the graph, false otherwise
     * @throws IllegalArgumentException if the node is not present in the graph
     */
    public boolean removeNode(Node node) {
        if (!containsNode(node)) {
            throw new IllegalArgumentException("That is not a node in this graph: " + node);
        }

        NodeId id = getNodeId(node);

        for (int lag = 0; lag < this.maxLag; lag++) {
            Node _node = getNode(id.getName(), lag);
            if (_node != null) {
                getGraph().removeNode(_node);
            }
            if (_node != null && lag == 0) {
                this.lag0Nodes.remove(_node);
            }
        }

        getPcs().firePropertyChange("editingFinished", null, null);

        return getGraph().containsNode(node) && getGraph().removeNode(node);
    }

    /**
     * Adds a directed edge to the graph.
     *
     * @param edge the directed edge to be added
     * @return true if the edge was successfully added, false otherwise
     * @throws IllegalArgumentException if the edge is not a directed edge, or if the edge does not connect nodes within
     *                                  the current time lag
     */
    public boolean addEdge(Edge edge) {
        if (!Edges.isDirectedEdge(edge)) {
            throw new IllegalArgumentException("Only directed edges supported: " + edge);
        }

        if (!this.lag0Nodes.contains(edge.getNode2())) {
            throw new IllegalArgumentException("Edges into the current time lag only: " + edge);
        }

        Node node1 = Edges.getDirectedEdgeTail(edge);
        Node node2 = Edges.getDirectedEdgeHead(edge);

        NodeId id1 = getNodeId(node1);
        NodeId id2 = getNodeId(node2);
        int lag = id1.getLag() - id2.getLag();

        if (lag < 0) {
            throw new IllegalArgumentException("Backward edges not permitted: " + edge);
        }

        for (int _lag = getNodeId(node2).getLag() % getNumInitialLags(); _lag <= getMaxLag() - lag; _lag += getNumInitialLags()) {
            Node from = getNode(id1.getName(), _lag + lag);
            Node to = getNode(id2.getName(), _lag);

            if (from == null || to == null) {
                continue;
            }

            Edge _edge = Edges.directedEdge(from, to);

            if (!getGraph().containsEdge(_edge)) {
                getGraph().addDirectedEdge(from, to);
            }
        }

        return true;
    }

    /**
     * Removes an edge from the graph.
     *
     * @param edge the edge to be removed
     * @return true if the edge was removed successfully, false otherwise
     * @throws IllegalArgumentException if the edge is not a directed edge
     */
    public boolean removeEdge(Edge edge) {
        if (!Edges.isDirectedEdge(edge))
            throw new IllegalArgumentException("Only directed edges are expected in the model.");

        Node node1 = Edges.getDirectedEdgeTail(edge);
        Node node2 = Edges.getDirectedEdgeHead(edge);

        NodeId id1 = getNodeId(node1);
        NodeId id2 = getNodeId(node2);
        int lag = id1.getLag() - id2.getLag();

        boolean removed = false;

        for (int _lag = 0; _lag <= getMaxLag(); _lag++) {
            Node from = getNode(id1.getName(), _lag + lag);
            Node to = getNode(id2.getName(), _lag);

            if (from != null && to != null) {
                Edge _edge = getGraph().getEdge(from, to);

                if (_edge != null) {
                    boolean b = getGraph().removeEdge(_edge);
                    removed = removed || b;
                }
            }
        }

        return removed;
    }

    /**
     * <p>Setter for the field <code>maxLag</code>.</p>
     *
     * @param maxLag a int
     * @return a boolean
     */
    public boolean setMaxLag(int maxLag) {
        if (maxLag < 0) {
            throw new IllegalArgumentException("Max lag must be at least 0: " + maxLag);
        }

        List<Node> lag0Nodes = getLag0Nodes();

        boolean changed = false;

        if (maxLag > this.getMaxLag()) {
            this.maxLag = maxLag;
            for (Node node : lag0Nodes) {
                addNode(node);
            }

            for (Node node : lag0Nodes) {
                Set<Edge> edges = getGraph().getEdges(node);

                for (Edge edge : edges) {
                    boolean b = addEdge(edge);
                    changed = changed || b;
                }
            }
        } else if (maxLag < this.getMaxLag()) {
            for (Node node : lag0Nodes) {
                Set<Edge> edges = getGraph().getEdges(node);

                for (Edge edge : edges) {
                    Node tail = Edges.getDirectedEdgeTail(edge);

                    if (getNodeId(tail).getLag() > maxLag) {
                        getGraph().removeEdge(edge);
                    }
                }
            }

            for (Node _node : getNodes()) {
                if (getNodeId(_node).getLag() > maxLag) {
                    boolean b = getGraph().removeNode(_node);
                    changed = changed || b;
                }
            }

            this.maxLag = maxLag;
        }

        getPcs().firePropertyChange("editingFinished", null, null);

        return changed;
    }

    /**
     * <p>removeHighLagEdges.</p>
     *
     * @param maxLag a int
     * @return a boolean
     */
    public boolean removeHighLagEdges(int maxLag) {
        List<Node> lag0Nodes = getLag0Nodes();
        boolean changed = false;

        for (Node node : lag0Nodes) {
            Set<Edge> edges = getGraph().getEdges(node);

            for (Edge edge : new ArrayList<>(edges)) {
                Node tail = Edges.getDirectedEdgeTail(edge);

                if (getNodeId(tail).getLag() > maxLag) {
                    boolean b = getGraph().removeEdge(edge);
                    changed = changed || b;
                }
            }
        }

        return changed;
    }

    /**
     * <p>Setter for the field <code>numInitialLags</code>.</p>
     *
     * @param numInitialLags a int
     * @return a boolean
     */
    public boolean setNumInitialLags(int numInitialLags) {
        if (numInitialLags < 1) {
            throw new IllegalArgumentException("The number of initial lags must be at least 1: " + numInitialLags);
        }

        if (numInitialLags == this.numInitialLags) return false;

        List<Node> lag0Nodes = getLag0Nodes();
        boolean changed = false;

        for (Node node : lag0Nodes) {
            NodeId id = getNodeId(node);

            for (int lag = 1; lag <= getMaxLag(); lag++) {
                Node _node = getNode(id.getName(), lag);
                List<Node> nodesInto = getGraph().getNodesInTo(_node, Endpoint.ARROW);

                for (Node _node2 : nodesInto) {
                    Edge edge = Edges.directedEdge(_node2, _node);
                    boolean b = getGraph().removeEdge(edge);
                    changed = changed || b;
                }
            }
        }

        this.numInitialLags = numInitialLags;

        for (Node node : lag0Nodes) {
            for (int lag = 0; lag < numInitialLags; lag++) {
                Set<Edge> edges = getGraph().getEdges(node);

                for (Edge edge : edges) {
                    boolean b = addEdge(edge);
                    changed = changed || b;
                }
            }
        }

        getPcs().firePropertyChange("editingFinished", null, null);

        return changed;
    }

    /**
     * <p>getNodeId.</p>
     *
     * @param node a {@link edu.cmu.tetrad.graph.Node} object
     * @return a {@link edu.cmu.tetrad.graph.TimeLagGraph.NodeId} object
     */
    public NodeId getNodeId(Node node) {
        String _name = node.getName();
        String[] tokens = _name.split(":");
        if (tokens.length > 2) throw new IllegalArgumentException("Name may contain only one colon: " + _name);
        if (tokens[0].length() == 0) throw new IllegalArgumentException("Part to the left of the colon may " +
                                                                        "not be empty; that's the name of the variable: " + _name);
        String name = tokens[0];
        int lag;

        if (tokens.length == 1) {
            lag = 0;
        } else {
            lag = Integer.parseInt(tokens[1]);
            if (lag == 0) throw new IllegalArgumentException("Lag 0 edges don't have :0 descriptors");
        }

        if (lag < 0) throw new IllegalArgumentException("Lag is less than 0: " + lag);
        if (lag > getMaxLag()) throw new IllegalArgumentException("Lag is greater than the maximum lag: " + lag);

        return new NodeId(name, lag);
    }

    /**
     * <p>getNode.</p>
     *
     * @param name a {@link java.lang.String} object
     * @param lag  a int
     * @return a {@link edu.cmu.tetrad.graph.Node} object
     */
    public Node getNode(String name, int lag) {
        if (name.length() == 0) throw new IllegalArgumentException("Empty node name: " + name);
        if (lag < 0) throw new IllegalArgumentException("Negative lag: " + lag);

        String _name;

        if (lag == 0) {
            _name = name;
        } else {
            _name = name + ":" + lag;
        }

        return getNode(_name);
    }

    /**
     * <p>Getter for the field <code>lag0Nodes</code>.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<Node> getLag0Nodes() {
        return new ArrayList<>(this.lag0Nodes);
    }

    private EdgeListGraph getGraph() {
        return this.graph;
    }

    /**
     * <p>Getter for the field <code>maxLag</code>.</p>
     *
     * @return a int
     */
    public int getMaxLag() {
        return this.maxLag;
    }

    /**
     * <p>Getter for the field <code>numInitialLags</code>.</p>
     *
     * @return a int
     */
    public int getNumInitialLags() {
        return this.numInitialLags;
    }

    /**
     * <p>toString.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String toString() {
        return getGraph().toString() + "\n" + this.lag0Nodes;
    }

    /**
     * Adds a directed edge between two nodes to the graph.
     *
     * @param node1 the first node to connect (source)
     * @param node2 the second node to connect (target)
     * @return true if the directed edge was successfully added, false otherwise
     */
    public boolean addDirectedEdge(Node node1, Node node2) {
        return this.graph.addDirectedEdge(node1, node2);
    }

    /**
     * Adds an undirected edge between two nodes.
     *
     * @param node1 the first node to be connected by the edge
     * @param node2 the second node to be connected by the edge
     * @return true if the undirected edge was successfully added; otherwise, false
     * @throws UnsupportedOperationException if undirected edges are not supported
     */
    public boolean addUndirectedEdge(Node node1, Node node2) {
        throw new UnsupportedOperationException("Undirected edges not currently supported.");
    }

    /**
     * Adds a nondirected edge between two nodes.
     *
     * @param node1 the first node.
     * @param node2 the second node.
     * @return true if the nondirected edge is successfully added, false otherwise.
     */
    public boolean addNondirectedEdge(Node node1, Node node2) {
        throw new UnsupportedOperationException("Nondireced edges not supported.");
    }

    /**
     * Adds a partially oriented edge between two given nodes.
     *
     * @param node1 the first node of the edge
     * @param node2 the second node of the edge
     * @return {@code true} if the edge is successfully added, {@code false} otherwise
     */
    public boolean addPartiallyOrientedEdge(Node node1, Node node2) {
        throw new UnsupportedOperationException("Partially oriented edges not supported.");
    }

    /**
     * Adds a bidirected edge between two nodes.
     *
     * @param node1 the first node to connect (non-null)
     * @param node2 the second node to connect (non-null)
     * @return true if the bidirected edge was added successfully, false otherwise
     */
    public boolean addBidirectedEdge(Node node1, Node node2) {
        throw new UnsupportedOperationException("Bidireced edges not currently supported.");
    }

    /**
     * Determines if the given nodes form a definite noncollider in the graph.
     *
     * @param node1 the first node
     * @param node2 the second node
     * @param node3 the third node
     * @return true if the nodes form a definite noncollider, false otherwise
     */
    public boolean isDefNoncollider(Node node1, Node node2, Node node3) {
        return getGraph().isDefNoncollider(node1, node2, node3);
    }

    /**
     * Determines if there is a definite collider relationship between the given nodes.
     *
     * @param node1 The first node.
     * @param node2 The second node.
     * @param node3 The third node.
     * @return {@code true} if there is a definite collider relationship between the nodes, {@code false} otherwise.
     */
    public boolean isDefCollider(Node node1, Node node2, Node node3) {
        return getGraph().isDefCollider(node1, node2, node3);
    }

    /**
     * Returns a list of children nodes for the given node.
     *
     * @param node a {@link Node} object representing the parent node
     * @return a list of {@link Node} objects representing the children nodes
     */
    public List<Node> getChildren(Node node) {
        return getGraph().getChildren(node);
    }

    /**
     * Returns the degree of the graph. The degree of a graph is the number of edges incident to a vertex.
     *
     * @return the degree of the graph
     */
    public int getDegree() {
        return getGraph().getDegree();
    }

    /**
     * Retrieves the edge between the given nodes.
     *
     * @param node1 The first node in the edge.
     * @param node2 The second node in the edge.
     * @return The edge between the given nodes.
     */
    public Edge getEdge(Node node1, Node node2) {
        return getGraph().getEdge(node1, node2);
    }

    /**
     * Retrieves the directed edge connecting two nodes in the graph.
     *
     * @param node1 The first node.
     * @param node2 The second node.
     * @return The directed edge connecting the two nodes, or null if no such edge exists.
     */
    public Edge getDirectedEdge(Node node1, Node node2) {
        return getGraph().getDirectedEdge(node1, node2);
    }

    /**
     * Returns the list of parent nodes for the given node.
     *
     * @param node the node for which to retrieve the parent nodes
     * @return a List of Node objects representing the parent nodes of the given node
     */
    public List<Node> getParents(Node node) {
        return getGraph().getParents(node);
    }

    /**
     * Returns the indegree of a given node in the graph.
     *
     * @param node the node for which the indegree is to be determined
     * @return the indegree of the node
     */
    public int getIndegree(Node node) {
        return getGraph().getIndegree(node);
    }

    /**
     * Retrieves the degree of a given node in the graph. The degree of a node is the number of edges connected to it.
     *
     * @param node a {@link Node} object representing the node for which the degree is to be determined
     * @return the degree of the specified node in the graph
     */
    @Override
    public int getDegree(Node node) {
        return getGraph().getDegree(node);
    }

    /**
     * Retrieves the outdegree of the specified node in the graph.
     *
     * @param node The node for which to calculate the outdegree.
     * @return The number of outgoing edges from the specified node.
     */
    public int getOutdegree(Node node) {
        return getGraph().getOutdegree(node);
    }

    /**
     * Determines whether two nodes are adjacent in the graph.
     *
     * @param node1 the first node to check adjacency
     * @param node2 the second node to check adjacency
     * @return true if the nodes are adjacent, false otherwise
     */
    public boolean isAdjacentTo(Node node1, Node node2) {
        return getGraph().isAdjacentTo(node1, node2);
    }

    /**
     * Checks if a given node is a child of another node in the graph.
     *
     * @param node1 the first {@link Node} to check
     * @param node2 the second {@link Node} to check if it is the parent
     * @return true if node1 is a child of node2, false otherwise
     */
    public boolean isChildOf(Node node1, Node node2) {
        return getGraph().isChildOf(node1, node2);
    }

    /**
     * Determines if a given node is a parent of another node in the graph.
     *
     * @param node1 the first {@link Node} object to compare
     * @param node2 the second {@link Node} object to compare
     * @return true if node1 is a parent of node2, false otherwise
     */
    @Override
    public boolean isParentOf(Node node1, Node node2) {
        return graph.isParentOf(node1, node2);
    }

    /**
     * Transfers nodes and edges from the given graph to the current graph.
     *
     * @param graph the graph from which nodes and edges are to be transferred
     * @throws IllegalArgumentException if the given graph is null
     */
    public void transferNodesAndEdges(Graph graph) throws IllegalArgumentException {
        getGraph().transferNodesAndEdges(graph);
    }

    /**
     * Transfers attributes from the given graph to the current graph.
     *
     * @param graph a {@link Graph} object to transfer attributes from
     * @throws IllegalArgumentException if the provided graph is null
     */
    public void transferAttributes(Graph graph) throws IllegalArgumentException {
        getGraph().transferAttributes(graph);
    }

    /**
     * Returns the instance of Paths.
     *
     * @return the instance of Paths.
     */
    @Override
    public Paths paths() {
        return this.paths;
    }

    /**
     * Checks if a node is parameterizable.
     *
     * @param node The node to be checked. Must be a {@link Node} object.
     * @return True if the node is parameterizable, false otherwise.
     */
    public boolean isParameterizable(Node node) {
        return getNodeId(node).getLag() < getNumInitialLags();
    }

    /**
     * Checks if the model is based on time lag.
     *
     * @return {@code true} if the model is based on time lag, {@code false} otherwise.
     */
    public boolean isTimeLagModel() {
        return true;
    }

    /**
     * Returns the TimeLagGraph object.
     *
     * @return the TimeLagGraph object
     */
    public TimeLagGraph getTimeLagGraph() {
        return this;
    }

    /**
     * Retrieves the sepset of two nodes in the graph.
     *
     * @param n1   The first node
     * @param n2   The second node
     * @param test
     * @return The set of nodes that form the sepset of n1 and n2
     */
    @Override
    public Set<Node> getSepset(Node n1, Node n2, IndependenceTest test) {
        return this.graph.getSepset(n1, n2, false);
    }

    /**
     * Checks if a given node is exogenous.
     *
     * @param node The node to check. It should be a {@link Node} object.
     * @return Returns true if the node is exogenous, false otherwise.
     */
    public boolean isExogenous(Node node) {
        return getGraph().isExogenous(node);
    }

    /**
     * Retrieves a list of adjacent nodes for the given node.
     *
     * @param node The Node object for which to find adjacent nodes.
     * @return A list of adjacent nodes.
     */
    public List<Node> getAdjacentNodes(Node node) {
        return getGraph().getAdjacentNodes(node);
    }

    /**
     * Returns the endpoint between two nodes in the graph.
     *
     * @param node1 The first node in the graph.
     * @param node2 The second node in the graph.
     * @return The endpoint between the two nodes.
     */
    public Endpoint getEndpoint(Node node1, Node node2) {
        return getGraph().getEndpoint(node1, node2);
    }

    /**
     * Sets the endpoint of an edge between two nodes in the graph.
     *
     * @param from     the source node for the edge
     * @param to       the destination node for the edge
     * @param endPoint the endpoint to set for the edge
     * @return true if the endpoint was set successfully, false otherwise
     * @throws IllegalArgumentException if the source or destination node is null or not present in the graph
     */
    public boolean setEndpoint(Node from, Node to, Endpoint endPoint) throws IllegalArgumentException {
        return getGraph().setEndpoint(from, to, endPoint);
    }

    /**
     * Retrieves a list of nodes that have an incoming edge from a specific node and endpoint.
     *
     * @param node     the source node from which the incoming edges are considered
     * @param endpoint the specific endpoint of the incoming edge
     * @return a list of {@code Node} objects representing the nodes that have an incoming edge from the specified node
     * and endpoint
     */
    public List<Node> getNodesInTo(Node node, Endpoint endpoint) {
        return getGraph().getNodesInTo(node, endpoint);
    }

    /**
     * Retrieves the list of nodes in a graph that have an outgoing edge to the given node and endpoint.
     *
     * @param node     the node to which the outgoing edges should be considered
     * @param endpoint the endpoint at which the edges should be considered
     * @return a list of nodes that have an outgoing edge to the specified node and endpoint
     */
    public List<Node> getNodesOutTo(Node node, Endpoint endpoint) {
        return getGraph().getNodesOutTo(node, endpoint);
    }

    /**
     * Adds a {@link PropertyChangeListener} to the list of listeners that are notified when a bound property is
     * changed.
     *
     * @param l The {@link PropertyChangeListener} to be added
     */
    public void addPropertyChangeListener(PropertyChangeListener l) {
        getPcs().addPropertyChangeListener(l);
        getGraph().addPropertyChangeListener(l);
    }

    /**
     * Retrieves the set of edges in the graph.
     *
     * @return a set of edges in the graph
     */
    public Set<Edge> getEdges() {
        return getGraph().getEdges();
    }

    /**
     * Checks if the given {@link Edge} object exists in the graph.
     *
     * @param edge the edge to be tested for existence in the graph
     * @return true if the edge exists in the graph, false otherwise
     */
    public boolean containsEdge(Edge edge) {
        return getGraph().containsEdge(edge);
    }

    /**
     * Checks if the graph contains a specific node.
     *
     * @param node the node to be checked. It should be a {@link Node} object.
     * @return {@code true} if the graph contains the specified node, {@code false} otherwise.
     */
    public boolean containsNode(Node node) {
        return getGraph().containsNode(node);
    }

    /**
     * Returns the list of edges connected to the specified node.
     *
     * @param node a {@link Node} object representing the node
     * @return a {@link List} containing the edges connected to the node, or null if the node does not exist in the
     * graph
     */
    public Set<Edge> getEdges(Node node) {
        if (getGraph().containsNode(node)) {
            return getGraph().getEdges(node);
        } else {
            return null;
        }
    }

    /**
     * Finds all edges between two nodes.
     *
     * @param node1 the first node
     * @param node2 the second node
     * @return a list of edges between the two nodes
     */
    public List<Edge> getEdges(Node node1, Node node2) {
        return getGraph().getEdges(node1, node2);
    }

    /**
     * Returns the hash code value for this object.
     *
     * @return the hash code value for this object
     */
    public int hashCode() {
        return getGraph().hashCode();
    }

    /**
     * Compares this Graph object with the specified object for equality.
     *
     * @param o the object to be compared
     * @return true if the specified object is also a Graph and if their underlying graphs are equal; false otherwise
     */
    public boolean equals(Object o) {
        if (!(o instanceof Graph)) return false;
        return getGraph().equals(o);
    }

    /**
     * Fully connects the given endpoint to all other endpoints in the graph.
     *
     * @param endpoint the endpoint to be fully connected
     */
    public void fullyConnect(Endpoint endpoint) {
        getGraph().fullyConnect(endpoint);
    }

    /**
     * Reorients all edges in the graph to point towards the specified endpoint.
     *
     * @param endpoint the endpoint to reorient all edges with (an instance of {@link Endpoint})
     */
    public void reorientAllWith(Endpoint endpoint) {
        getGraph().reorientAllWith(endpoint);
    }

    /**
     * Retrieves a Node from the graph based on the given name.
     *
     * @param name the name of the Node to retrieve
     * @return the retrieved Node
     */
    public Node getNode(String name) {
        return getGraph().getNode(name);
    }

    /**
     * Gets the number of nodes in the graph.
     *
     * @return the total number of nodes in the graph
     */
    public int getNumNodes() {
        return getGraph().getNumNodes();
    }

    /**
     * Retrieves the number of edges in the graph.
     *
     * @return the number of edges in the graph
     */
    public int getNumEdges() {
        return getGraph().getNumEdges();
    }

    /**
     * Retrieves the number of edges connected to a specific node.
     *
     * @param node a {@link Node} object representing the node to check
     * @return an integer value representing the number of edges connected to the specified node
     */
    public int getNumEdges(Node node) {
        return getGraph().getNumEdges(node);
    }

    /**
     * Returns a subgraph of the current graph based on the provided nodes.
     *
     * @param nodes a list of nodes to include in the subgraph
     * @return a subgraph of the current graph containing only the provided nodes
     */
    public Graph subgraph(List<Node> nodes) {
        return getGraph().subgraph(nodes);
    }

    /**
     * Retrieves a list of nodes from the graph.
     *
     * @return A list of nodes from the graph.
     */
    public List<Node> getNodes() {
        return getGraph().getNodes();
    }

    /**
     * Sets the nodes of the graph.
     *
     * @param nodes a list of Node objects representing the nodes of the graph
     * @throws IllegalArgumentException if an attempt is made to replace the nodes for a time lag graph
     */
    @Override
    public void setNodes(List<Node> nodes) {
        throw new IllegalArgumentException("Sorry, you cannot replace the variables for a time lag graph.");
    }

    /**
     * Returns a list of node names in the graph.
     *
     * @return a List of Strings representing the node names
     */
    public List<String> getNodeNames() {
        return getGraph().getNodeNames();
    }

    /**
     * Clears the graph by removing all vertices and edges.
     */
    public void clear() {
        getGraph().clear();
    }

    /**
     * Removes the edge between two given nodes.
     *
     * @param node1 The first node.
     * @param node2 The second node.
     * @return true if the edge was successfully removed, false otherwise.
     */
    public boolean removeEdge(Node node1, Node node2) {
        return removeEdge(getEdge(node1, node2));
    }

    /**
     * Removes the specified collection of edges from the graph.
     *
     * @param edges a collection of edges to be removed
     * @return true if any edge is successfully removed, false otherwise
     */
    public boolean removeEdges(Collection<Edge> edges) {
        boolean change = false;

        for (Edge edge : edges) {
            boolean _change = removeEdge(edge);
            change = change || _change;
        }

        return change;
    }

    /**
     * Removes the specified nodes from the graph.
     *
     * @param nodes a list of nodes to be removed from the graph
     * @return true if the nodes were successfully removed, false otherwise
     */
    public boolean removeNodes(List<Node> nodes) {
        return getGraph().removeNodes(nodes);
    }

    /**
     * Removes edges between two nodes.
     *
     * @param node1 the first {@link Node} object
     * @param node2 the second {@link Node} object
     * @return true if edges between the two nodes are removed, false otherwise
     */
    public boolean removeEdges(Node node1, Node node2) {
        return removeEdges(getEdges(node1, node2));
    }

    /**
     * Get the PropertyChangeSupport object used for registering listeners and firing property change events.
     *
     * @return the PropertyChangeSupport object
     */
    private PropertyChangeSupport getPcs() {
        if (this.pcs == null) {
            this.pcs = new PropertyChangeSupport(this);
        }
        return this.pcs;
    }

    /**
     * Retrieves all the attributes stored in the object.
     *
     * @return a map containing the attribute names as keys and their corresponding values as values
     */
    @Override
    public Map<String, Object> getAllAttributes() {
        return this.attributes;
    }

    /**
     * Retrieves the value associated with the specified key from the attributes map.
     *
     * @param key The key for which to retrieve the value. Must not be null.
     * @return The value associated with the specified key. If the key is not found, null is returned.
     */
    @Override
    public Object getAttribute(String key) {
        return this.attributes.get(key);
    }

    /**
     * Removes the attribute with the specified key from the object.
     *
     * @param key The key of the attribute to be removed. This must be a {@link String} object.
     */
    @Override
    public void removeAttribute(String key) {
        this.attributes.remove(key);
    }

    /**
     * Adds a key-value pair to the attributes map.
     *
     * @param key   the key of the attribute
     * @param value the value of the attribute
     */
    @Override
    public void addAttribute(String key, Object value) {
        this.attributes.put(key, value);
    }

    /**
     * Retrieves the set of ambiguous triples.
     *
     * @return The set of ambiguous triples.
     */
    public Set<Triple> getAmbiguousTriples() {
        return new HashSet<>(this.ambiguousTriples);
    }

    /**
     * Sets the set of ambiguous triples.
     *
     * @param triples a set of triples to be set as ambiguous triples.
     */
    public void setAmbiguousTriples(Set<Triple> triples) {
        this.ambiguousTriples.clear();

        for (Triple triple : triples) {
            addAmbiguousTriple(triple.getX(), triple.getY(), triple.getZ());
        }
    }

    /**
     * Returns a set of Triple objects representing the underlines.
     *
     * @return a set of Triple objects representing the underlines
     */
    public Set<Triple> getUnderLines() {
        return new HashSet<>(this.underLineTriples);
    }

    /**
     * Returns a set of Triple objects representing dotted underlines.
     *
     * @return a set of Triple objects representing dotted underlines
     */
    public Set<Triple> getDottedUnderlines() {
        return new HashSet<>(this.dottedUnderLineTriples);
    }

    /**
     * Checks whether a triple of nodes is ambiguous.
     *
     * @param x a {@link Node} object representing the first node.
     * @param y a {@link Node} object representing the second node.
     * @param z a {@link Node} object representing the third node.
     * @return true if the given triple (x, y, z) is ambiguous, false otherwise.
     */
    public boolean isAmbiguousTriple(Node x, Node y, Node z) {
        return this.ambiguousTriples.contains(new Triple(x, y, z));
    }

    /**
     * Checks whether a given triple (x, y, z) is an underline triple.
     *
     * @param x The first node of the triple.
     * @param y The second node of the triple.
     * @param z The third node of the triple.
     * @return {@code true} if the triple is an underline triple, {@code false} otherwise.
     */
    public boolean isUnderlineTriple(Node x, Node y, Node z) {
        return this.underLineTriples.contains(new Triple(x, y, z));
    }

    /**
     * Adds an ambiguous triple to the list of ambiguous triples.
     *
     * @param x the first {@link Node} in the triple
     * @param y the second {@link Node} in the triple
     * @param z the third {@link Node} in the triple
     */
    public void addAmbiguousTriple(Node x, Node y, Node z) {
        this.ambiguousTriples.add(new Triple(x, y, z));
    }

    /**
     * Adds an underline triple consisting of three nodes to the graph.
     *
     * @param x the first node of the triple
     * @param y the second node of the triple
     * @param z the third node of the triple
     */
    public void addUnderlineTriple(Node x, Node y, Node z) {
        Triple triple = new Triple(x, y, z);

        if (!triple.alongPathIn(this)) {
            return;
        }

        this.underLineTriples.add(new Triple(x, y, z));
    }

    /**
     * Adds a triple with dotted underline to the list of triples.
     *
     * @param x the first node of the triple. Must not be null.
     * @param y the second node of the triple. Must not be null.
     * @param z the third node of the triple. Must not be null.
     */
    public void addDottedUnderlineTriple(Node x, Node y, Node z) {
        Triple triple = new Triple(x, y, z);

        if (!triple.alongPathIn(this)) {
            return;
        }

        this.dottedUnderLineTriples.add(triple);
    }

    /**
     * Removes an ambiguous triple from the collection.
     *
     * @param x The first node of the triple.
     * @param y The second node of the triple.
     * @param z The third node of the triple.
     */
    public void removeAmbiguousTriple(Node x, Node y, Node z) {
        this.ambiguousTriples.remove(new Triple(x, y, z));
    }

    /**
     * Removes the specified triple (x, y, z) from the list of underline triples.
     *
     * @param x The first node of the triple.
     * @param y The second node of the triple.
     * @param z The third node of the triple.
     */
    public void removeUnderlineTriple(Node x, Node y, Node z) {
        this.underLineTriples.remove(new Triple(x, y, z));
    }

    /**
     * Removes a triple of nodes from the set of dottedUnderLineTriples.
     *
     * @param x the first node in the triple
     * @param y the second node in the triple
     * @param z the third node in the triple
     */
    public void removeDottedUnderlineTriple(Node x, Node y, Node z) {
        this.dottedUnderLineTriples.remove(new Triple(x, y, z));
    }

    /**
     * Sets the underline triples.
     *
     * @param triples the set of triples to set as underline triples
     */
    public void setUnderLineTriples(Set<Triple> triples) {
        this.underLineTriples.clear();

        for (Triple triple : triples) {
            addUnderlineTriple(triple.getX(), triple.getY(), triple.getZ());
        }
    }

    /**
     * Sets the dotted underline triples.
     *
     * @param triples the set of triples to set
     */
    public void setDottedUnderLineTriples(Set<Triple> triples) {
        this.dottedUnderLineTriples.clear();

        for (Triple triple : triples) {
            addDottedUnderlineTriple(triple.getX(), triple.getY(), triple.getZ());
        }
    }

    /**
     * Removes triples from the object's internal lists if any of the nodes in the triple is not present in the graph,
     * or if any of the nodes are not adjacent to each other in the graph. The triples are removed from the list
     * `ambiguousTriples`, `underLineTriples`, and `dottedUnderLineTriples`.
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
     * Represents a NodeId with a name and a lag value.
     */
    public static class NodeId {
        /**
         * The name of the node.
         */
        private final String name;
        /**
         * The lag of the node.
         */
        private final int lag;

        /**
         * <p>Constructor for NodeId.</p>
         *
         * @param name a {@link java.lang.String} object
         * @param lag  a int
         */
        public NodeId(String name, int lag) {
            this.name = name;
            this.lag = lag;
        }

        /**
         * <p>Getter for the field <code>name</code>.</p>
         *
         * @return a {@link java.lang.String} object
         */
        public String getName() {
            return this.name;
        }

        /**
         * <p>Getter for the field <code>lag</code>.</p>
         *
         * @return a int
         */
        public int getLag() {
            return this.lag;
        }
    }
}



