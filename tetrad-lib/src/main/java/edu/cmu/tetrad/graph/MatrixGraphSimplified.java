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
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.*;

/**
 * <p>Stores a graph a list of lists of edges adjacent to each node in the
 * graph, with an additional list storing all of the edges in the graph. The
 * edges are of the form N1 *-# N2. Multiple edges may be added per node pair to
 * this graph, with the caveat that all edges of the form N1 *-# N2 will be
 * considered equal. For randomUtil, if the edge X --> Y is added to the graph,
 * another edge X --> Y may not be added, although an edge Y --> X may be added.
 * Edges from nodes to themselves may also be added.</p>
 *
 * @author Joseph Ramsey
 * @author Erin Korber additions summer 2004
 * @see edu.cmu.tetrad.graph.Endpoint
 */
public final class MatrixGraphSimplified implements Graph {
    static final long serialVersionUID = 23L;

    /**
     * A list of the nodes in the graph, in the order in which they were added.
     *
     * @serial
     */
    private final List<Node> nodes;

    /**
     * @serial
     */
    private final List<GraphConstraint> graphConstraints;

    /**
     * True iff graph constraints will be checked for future graph
     * modifications.
     *
     * @serial
     */
    private boolean graphConstraintsChecked = true;

    /**
     * Fires property change events.
     */
    private transient PropertyChangeSupport pcs;

    /**
     * Set of ambiguous triples. Note the name can't be changed due to
     * serialization.
     */
    private Set<Triple> ambiguousTriples = new HashSet<>();

    /**
     * @serial
     */
    private Set<Triple> underLineTriples = new HashSet<>();

    /**
     * @serial
     */
    private Set<Triple> dottedUnderLineTriples = new HashSet<>();

    /**
     * True iff nodes were removed since the last call to an accessor for ambiguous, underline, or dotted underline
     * triples. If there are triples in the lists involving removed nodes, these need to be removed from the lists
     * first, so as not to cause confusion.
     */
    private boolean stuffRemovedSinceLastTripleAccess = false;

    /**
     * The set of highlighted edges.
     */
    private Set<Edge> highlightedEdges = new HashSet<>();
    private byte[][] matrix;
    private Map<Node, Integer> nodeMap;

    //==============================CONSTUCTORS===========================//

    /**
     * Constructs a new graph, with no edges, using the the given variable
     * names.
     */
    public MatrixGraphSimplified(List<Node> nodes) {
        this.graphConstraints = new LinkedList<>();
        this.nodes = new ArrayList<>(nodes);

        this.matrix = new byte[nodes.size()][nodes.size()];

        for (int i = 0; i < nodes.size(); i++) {
            if (nodes.get(i) == null) {
                throw new NullPointerException();
            }

            for (int j = 0; j < i; j++) {
                if (nodes.get(i).equals(nodes.get(j))) {
                    throw new IllegalArgumentException("Two variables by the same name: " + nodes.get(i));
                }
            }
        }

        nodeMap = new HashMap<>();

        for (int i = 0; i < nodes.size(); i++) {
            nodeMap.put(nodes.get(i), i);
        }

//        DataGraphUtils.circleLayout(this, 200, 200, 150);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static MatrixGraphSimplified serializableInstance() {
        return new MatrixGraphSimplified(new ArrayList<Node>());
    }

    //===============================PUBLIC METHODS========================//

    /**
     * Adds a graph constraint.
     *
     * @param gc the graph constraint.
     * @return true if the constraint was added, false if not.
     */
    public boolean addGraphConstraint(GraphConstraint gc) {
        if (!this.graphConstraints.contains(gc)) {
            this.graphConstraints.add(gc);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Adds a directed edge to the graph from node A to node B.
     *
     * @param node1 the "from" node.
     * @param node2 the "to" node.
     */
    public boolean addDirectedEdge(Node node1, Node node2) {
        return addEdge(Edges.directedEdge(node1, node2));
    }

    /**
     * Adds an undirected edge to the graph from node A to node B.
     *
     * @param node1 the "from" node.
     * @param node2 the "to" node.
     */
    public boolean addUndirectedEdge(Node node1, Node node2) {
        return addEdge(Edges.undirectedEdge(node1, node2));
    }

    /**
     * Adds a nondirected edge to the graph from node A to node B.
     *
     * @param node1 the "from" node.
     * @param node2 the "to" node.
     */
    public boolean addNondirectedEdge(Node node1, Node node2) {
        return addEdge(Edges.nondirectedEdge(node1, node2));
    }

    /**
     * Adds a partially oriented edge to the graph from node A to node B.
     *
     * @param node1 the "from" node.
     * @param node2 the "to" node.
     */
    public boolean addPartiallyOrientedEdge(Node node1, Node node2) {
        return addEdge(Edges.partiallyOrientedEdge(node1, node2));
    }

    /**
     * Adds a bidirected edge to the graph from node A to node B.
     *
     * @param node1 the "from" node.
     * @param node2 the "to" node.
     */
    public boolean addBidirectedEdge(Node node1, Node node2) {
        return addEdge(Edges.bidirectedEdge(node1, node2));
    }

    public boolean existsDirectedCycle() {
        for (Node node : getNodes()) {
            if (existsDirectedPathFromTo(node, node)) {
                return true;
            }
        }
        return false;
    }

    public boolean isDirectedFromTo(Node node1, Node node2) {
        return getEndpoint(node2, node1) == Endpoint.TAIL &&
                getEndpoint(node1, node2) == Endpoint.ARROW;
    }

    public boolean isUndirectedFromTo(Node node1, Node node2) {
        return getEndpoint(node2, node1) == Endpoint.TAIL &
                getEndpoint(node1, node2) == Endpoint.TAIL;
    }

    /**
     * added by ekorber, 2004/06/11
     *
     * @return true if the given edge is definitely visible (Jiji, pg 25)
     * @throws IllegalArgumentException if the given edge is not a directed edge
     *                                  in the graph
     */
    public boolean defVisible(Edge edge) {
        if (containsEdge(edge)) {

            Node A = Edges.getDirectedEdgeTail(edge);
            Node B = Edges.getDirectedEdgeHead(edge);
            List<Node> adjToA = getAdjacentNodes(A);

            while (!adjToA.isEmpty()) {
                Node Curr = adjToA.remove(0);
                Edge edge1 = getEdge(Curr, A);

                if (edge1 == null) {
                    throw new NullPointerException();
                }

                if (!((getAdjacentNodes(Curr)).contains(B)) &&
                        (edge1.getProximalEndpoint(A) == Endpoint
                                .ARROW)) {
                    return true;
                }
            }
            return false;
        } else {
            throw new IllegalArgumentException(
                    "Given edge is not in the graph.");
        }
    }

    /**
     * IllegalArgument exception raised (by isDirectedFromTo(getEndpoint) or by
     * getEdge) if there are multiple edges between any of the node pairs.
     */
    public boolean isDefNoncollider(Node node1, Node node2, Node node3) {

//         Is this right? jdramsey 2/15/2009
//        if (isAmbiguous(node1, node2, node3)) {
//            return false;
//        }

        if (isDirectedFromTo(node2, node1) || isDirectedFromTo(node2, node3)) {
            return true;
        } else if (!isAdjacentTo(node1, node3)) {
            boolean endpt1 = getEndpoint(node1, node2) == Endpoint.CIRCLE;
            boolean endpt2 = getEndpoint(node3, node2) == Endpoint.CIRCLE;
            return (endpt1 && endpt2);
//        } else if (getEndpoint(node1, node2) == Endpoint.TAIL && getEndpoint(node3, node2) == Endpoint.TAIL){
//            return true;
        } else {
            return false;
        }
    }

    public boolean isDefCollider(Node node1, Node node2, Node node3) {
        return ((getEndpoint(node1, node2) == Endpoint.ARROW) &&
                (getEndpoint(node3, node2) == Endpoint.ARROW));
    }

    /**
     * @return true iff there is a directed path from node1 to node2.
     */
    public boolean existsDirectedPathFromTo(Node node1, Node node2) {
        return existsDirectedPathVisit(node1, node2, new LinkedList<Node>());
    }

    public boolean existsUndirectedPathFromTo(Node node1, Node node2) {
        return existsUndirectedPathVisit(node1, node2, new LinkedList<Node>());
    }

    public boolean existsSemiDirectedPathFromTo(Node node1, Set<Node> nodes) {
        return existsSemiDirectedPathVisit(node1, nodes,
                new LinkedList<Node>());
    }

    /**
     * Determines whether a trek exists between two nodes in the graph.  A trek
     * exists if there is a directed path between the two nodes or else, for
     * some third node in the graph, there is a path to each of the two nodes in
     * question.
     */
    public boolean existsTrek(Node node1, Node node2) {

        for (Node node3 : getNodes()) {
            Node node = (node3);

            if (isAncestorOf(node, node1) && isAncestorOf(node, node2)) {
                return true;
            }

        }

        return false;
    }

    /**
     * @return the list of children for a node.
     */
    public List<Node> getChildren(Node node) {
        List<Node> children = new LinkedList<>();

        for (Object o : getEdges(node)) {
            Edge edge = (Edge) (o);
            Node sub = Edges.traverseDirected(node, edge);

            if (sub != null) {
                children.add(sub);
            }
        }

        return children;
    }

    public int getConnectivity() {
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

    public List<Node> getDescendants(List<Node> nodes) {
        HashSet<Node> descendants = new HashSet<>();

        for (Object node1 : nodes) {
            Node node = (Node) node1;
            collectDescendantsVisit(node, descendants);
        }

        return new LinkedList<>(descendants);
    }

    /**
     * @return the edge connecting node1 and node2, provided a unique such edge
     * exists.
     */
    public Edge getEdge(Node node1, Node node2) {
        int i = nodeMap.get(node1);
        int j = nodeMap.get(node2);

        if (matrix[i][j] == 0 && matrix[j][i] == 0) {
            return null;
        }

        Endpoint endpoint1 = null;
        Endpoint endpoint2 = null;

        if (matrix[i][j] == 1) {
            endpoint1 = Endpoint.ARROW;
        } else if (matrix[i][j] == 2) {
            endpoint1 = Endpoint.TAIL;
        } else if (matrix[i][j] == 3) {
            endpoint1 = Endpoint.CIRCLE;
        }

        if (matrix[j][i] == 1) {
            endpoint2 = Endpoint.ARROW;
        } else if (matrix[j][i] == 2) {
            endpoint2 = Endpoint.TAIL;
        } else if (matrix[j][i] == 3) {
            endpoint2 = Endpoint.CIRCLE;
        }

        return new Edge(node1, node2, endpoint1, endpoint2);
    }

    public Edge getDirectedEdge(Node node1, Node node2) {
        List<Edge> edges = getEdges(node1, node2);

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
     * @return the list of parents for a node.
     */
    public List<Node> getParents(Node node) {
        List<Node> parents = new LinkedList<>();

        for (Object o : getEdges(node)) {
            Edge edge = (Edge) (o);
            Node sub = Edges.traverseReverseDirected(node, edge);

            if (sub != null) {
                parents.add(sub);
            }
        }

        return parents;
    }

    /**
     * @return the number of edges into the given node.
     */
    public int getIndegree(Node node) {
        return getParents(node).size();
    }

    /**
     * @return the number of edges out of the given node.
     */
    public int getOutdegree(Node node) {
        return getChildren(node).size();
    }

    /**
     * Determines whether some edge or other exists between two nodes.
     */
    public boolean isAdjacentTo(Node node1, Node node2) {
        for (Edge edge : getEdges(node1)) {
            if (edge.getNode1() == edge.getNode2()) {
                throw new IllegalArgumentException("The two nodes are the same: " + edge.getNode1());
            }

            if (Edges.traverse(node1, edge) == node2) {
                return true;
            }
        }

        return false;
    }

    /**
     * Determines whether one node is an ancestor of another.
     */
    public boolean isAncestorOf(Node node1, Node node2) {
        return (node1 == node2) || isProperAncestorOf(node1, node2);
    }

    public boolean possibleAncestor(Node node1, Node node2) {
        return existsSemiDirectedPathFromTo(node1,
                Collections.singleton(node2));
    }

    /**
     * @return true iff node1 is a possible ancestor of at least one member of
     * nodes2
     */
    private boolean possibleAncestorSet(Node node1, List<Node> nodes2) {
        for (Object aNodes2 : nodes2) {
            if (possibleAncestor(node1, (Node) aNodes2)) {
                return true;
            }
        }
        return false;
    }

    public List<Node> getAncestors(List<Node> nodes) {
        HashSet<Node> ancestors = new HashSet<>();

        for (Object node1 : nodes) {
            Node node = (Node) node1;
            collectAncestorsVisit(node, ancestors);
        }

        return new LinkedList<>(ancestors);
    }

    /**
     * Determines whether one node is a child of another.
     */
    public boolean isChildOf(Node node1, Node node2) {
        for (Object o : getEdges(node2)) {
            Edge edge = (Edge) (o);
            Node sub = Edges.traverseDirected(node2, edge);

            if (sub == node1) {
                return true;
            }
        }
        return false;
    }

    /**
     * Determines whether one node is a descendent of another.
     */
    public boolean isDescendentOf(Node node1, Node node2) {
        return (node1 == node2) || isProperDescendentOf(node1, node2);
    }

    /**
     * added by ekorber, 2004/06/12
     *
     * @return true iff node2 is a definite nondecendent of node1
     */
    public boolean defNonDescendent(Node node1, Node node2) {
        return !(possibleAncestor(node1, node2));
    }


    /**
     * Determines whether node1 is d-connected to node2, given a list of
     * conditioning nodes. According to Spirtes, Richardson & Meek, node1 is
     * d-connected to node2 given some conditioning set Z if there is an acyclic
     * undirected path U between node1 and node2, such that every collider on U
     * is an ancestor of some element in Z and every non-collider on U is not in
     * Z. Two elements are d-separated just in case they are not d-connected. A
     * collider is a node which two edges hold in common for which the endpoints
     * leading into the node are both arrow endpoints.
     *
     * @param node1             the first node.
     * @param node2             the second node.
     * @param conditioningNodes the set of conditioning nodes.
     * @return true if node1 is d-connected to node2 given set
     * conditioningNodes, false if not.
     * @see #isDSeparatedFrom
     */
    public boolean isDConnectedTo(Node node1, Node node2,
                                  List<Node> conditioningNodes) {

        // Set up a linked list to hold nodes along the getModel path (to check
        // for cycles).
        LinkedList<Node> path = new LinkedList<>();

        // Fine the closure of conditioningNodes under the parent relation.
        Set<Node> conditioningNodesClosure = new HashSet<>();

        for (Node conditioningNode : conditioningNodes) {
            doParentClosureVisit(conditioningNode, conditioningNodesClosure);
        }

        // Calls the recursive method to discover a d-connecting path from node1
        // to node2, if one exists.  If such a path is found, true is returned;
        // otherwise, false is returned.
        return isDConnectedToVisit(node1, null, null, node2, path,
                conditioningNodes, conditioningNodesClosure);
    }

    /**
     * Determines whether one node is d-separated from another. According to
     * Spirtes, Richardson & Meek, two nodes are d- connected given some
     * conditioning set Z if there is an acyclic undirected path U between them,
     * such that every collider on U is an ancestor of some element in Z and
     * every non-collider on U is not in Z.  Two elements are d-separated just
     * in case they are not d-connected.  A collider is a node which two edges
     * hold in common for which the endpoints leading into the node are both
     * arrow endpoints.
     *
     * @param node1 the first node.
     * @param node2 the second node.
     * @param z     the conditioning set.
     * @return true if node1 is d-separated from node2 given set z, false if
     * not.
     * @see #isDConnectedTo
     */
    public boolean isDSeparatedFrom(Node node1, Node node2, List<Node> z) {
        return !isDConnectedTo(node1, node2, z);
    }

    //added by ekorber, June 2004
    public boolean possDConnectedTo(Node node1, Node node2,
                                    List<Node> condNodes) {
        LinkedList<Node> allNodes = new LinkedList<>(getNodes());
        int sz = allNodes.size();
        int[][] edgeStage = new int[sz][sz];
        int stage = 1;

        int n1x = allNodes.indexOf(node1);
        int n2x = allNodes.indexOf(node2);

        edgeStage[n1x][n1x] = 1;
        edgeStage[n2x][n2x] = 1;

        List<int[]> currEdges;
        List<int[]> nextEdges = new LinkedList<>();

        int[] temp1 = new int[2];
        temp1[0] = n1x;
        temp1[1] = n1x;
        nextEdges.add(temp1);

        int[] temp2 = new int[2];
        temp2[0] = n2x;
        temp2[1] = n2x;
        nextEdges.add(temp2);

        while (true) {
            currEdges = nextEdges;
            nextEdges = new LinkedList<>();
            for (int[] edge : currEdges) {
                Node center = allNodes.get(edge[1]);
                List<Node> adj = new LinkedList<>(getAdjacentNodes(center));

                for (Node anAdj : adj) {
                    // check if we've hit this edge before
                    int testIndex = allNodes.indexOf(anAdj);
                    if (edgeStage[edge[1]][testIndex] != 0) {
                        continue;
                    }

                    // if the edge pair violates possible d-connection,
                    // then go to the next adjacent node.

                    Node X = allNodes.get(edge[0]);
                    Node Y = allNodes.get(edge[1]);
                    Node Z = allNodes.get(testIndex);

                    if (!((isDefNoncollider(X, Y, Z) &&
                            !(condNodes.contains(Y))) || (
                            isDefCollider(X, Y, Z) &&
                                    possibleAncestorSet(Y, condNodes)))) {
                        continue;
                    }

                    // if it gets here, then it's legal, so:
                    // (i) if this is the one we want, we're done
                    if (anAdj.equals(node2)) {
                        return true;
                    }

                    // (ii) if we need to keep going,
                    // add the edge to the nextEdges list
                    int[] nextEdge = new int[2];
                    nextEdge[0] = edge[1];
                    nextEdge[1] = testIndex;
                    nextEdges.add(nextEdge);

                    // (iii) set the edgeStage array
                    edgeStage[edge[1]][testIndex] = stage;
                    edgeStage[testIndex][edge[1]] = stage;
                }
            }

            // find out if there's any reason to move to the next stage
            if (nextEdges.size() == 0) {
                break;
            }

            stage++;
        }

        return false;
    }


    /**
     * Determines whether an inducing path exists between node1 and node2, given
     * a set O of observed nodes and a set sem of conditioned nodes.
     *
     * @param node1 the first node.
     * @param node2 the second node.
     * @return true if an inducing path exists, false if not.
     */
    public boolean existsInducingPath(Node node1, Node node2) {
        return GraphUtils.existsInducingPath(node1, node2, this);
    }

    /**
     * Determines whether one node is a parent of another.
     *
     * @param node1 the first node.
     * @param node2 the second node.
     * @return true if node1 is a parent of node2, false if not.
     * @see #isChildOf
     * @see #getParents
     * @see #getChildren
     */
    public boolean isParentOf(Node node1, Node node2) {
        for (Edge edge1 : getEdges(node1)) {
            Edge edge = (edge1);
            Node sub = Edges.traverseDirected(node1, edge);

            if (sub == node2) {
                return true;
            }
        }

        return false;
    }

    /**
     * Determines whether one node is a proper ancestor of another.
     */
    public boolean isProperAncestorOf(Node node1, Node node2) {
        return existsDirectedPathFromTo(node1, node2);
    }

    /**
     * Determines whether one node is a proper decendent of another
     */
    public boolean isProperDescendentOf(Node node1, Node node2) {
        return existsDirectedPathFromTo(node2, node1);
    }

    /**
     * Transfers nodes and edges from one graph to another.  One way this is
     * used is to change graph types.  One constructs a new graph based on the
     * old graph, and this method is called to transfer the nodes and edges of
     * the old graph to the new graph.
     *
     * @param graph the graph from which nodes and edges are to be pilfered.
     * @throws IllegalArgumentException This exception is thrown if adding some
     *                                  node or edge violates one of the
     *                                  basicConstraints of this graph.
     */
    public void transferNodesAndEdges(Graph graph)
            throws IllegalArgumentException {
        if (graph == null) {
            throw new NullPointerException("No graph was provided.");
        }

        for (Object o : graph.getNodes()) {
            Node node = (Node) o;
            if (!addNode(node)) {
                throw new IllegalArgumentException();
            }
        }

        for (Object o1 : graph.getEdges()) {
            Edge edge = (Edge) o1;
            if (!addEdge(edge)) {
                throw new IllegalArgumentException();
            }
        }
    }

    /**
     * Determines whether a node in a graph is exogenous.
     */
    public boolean isExogenous(Node node) {
        return getIndegree(node) == 0;
    }

    /**
     * @return the set of nodes adjacent to the given node.
     */
    public List<Node> getAdjacentNodes(Node node) {
        Set<Node> adjacentNodesHash = new HashSet<>();
        List<Node> adjacentNodes = new LinkedList<>();
        List<Edge> edges = getEdges(node);

        for (Edge edge : edges) {
            Node _node = edge.getDistalNode(node);

            if (!adjacentNodesHash.contains(_node)) {
                adjacentNodesHash.add(_node);
                adjacentNodes.add(_node);
            }
        }

        return adjacentNodes;
    }

    /**
     * Removes the edge connecting the two given nodes.
     */
    public boolean removeEdge(Node node1, Node node2) {
        List<Edge> edges = getEdges(node1, node2);

        if (edges.size() > 1) {
            throw new IllegalStateException(
                    "There is more than one edge between " + node1 + " and " +
                            node2);
        }

        return removeEdges(edges);
    }

    /**
     * @return the endpoint along the edge from node to node2 at the node2 end.
     */
    public Endpoint getEndpoint(Node node1, Node node2) {
        List<Edge> edges = getEdges(node1, node2);

        if (edges.size() == 0) {
            return null;
        }

        if (edges.size() > 1) {
            throw new IllegalArgumentException(
                    "More than one edge between " + node1 + " and " + node2);
        }

        return (edges.get(0)).getProximalEndpoint(node2);
    }

    /**
     * If there is currently an edge from node1 to node2, sets the endpoint at
     * node2 to the given endpoint; if there is no such edge, adds an edge --#
     * where # is the given endpoint. Setting an endpoint to null, provided
     * there is exactly one edge connecting the given nodes, removes the edge.
     * (If there is more than one edge, an exception is thrown.)
     *
     * @throws IllegalArgumentException if the edge with the revised endpoint
     *                                  cannot be added to the graph.
     */
    public boolean setEndpoint(Node from, Node to, Endpoint endPoint)
            throws IllegalArgumentException {
        List<Edge> edges = getEdges(from, to);

        if (endPoint == null) {
            removeEdge(from, to);
            return true;
        } else if (edges.size() == 0) {
            addEdge(new Edge(from, to, Endpoint.TAIL, endPoint));
            return true;
        } else if (edges.size() == 1) {
            Edge currentEdge = getEdge(from, to);

            Edge edge = edges.get(0);
            Edge newEdge = new Edge(from, to, edge.getProximalEndpoint(from),
                    endPoint);
            removeEdge(currentEdge);

            try {
                addEdge(newEdge);
                return true;
            } catch (IllegalArgumentException e) {
                addEdge(currentEdge);
                return false;
            }
        } else {
            throw new NullPointerException(
                    "An endpoint between node1 and node2 " +
                            "may not be set in this graph if there is more than one " +
                            "edge between node1 and node2.");
        }
    }

    /**
     * Nodes adjacent to the given node with the given proximal endpoint.
     */
    public List<Node> getNodesInTo(Node node, Endpoint endpoint) {
        List<Node> nodes = new LinkedList<>();
        List<Edge> edges = getEdges(node);

        for (Object edge1 : edges) {
            Edge edge = (Edge) edge1;

            if (edge.getProximalEndpoint(node) == endpoint) {
                nodes.add(edge.getDistalNode(node));
            }
        }

        return nodes;
    }

    /**
     * Nodes adjacent to the given node with the given distal endpoint.
     */
    public List<Node> getNodesOutTo(Node node, Endpoint endpoint) {
        List<Node> nodes = new LinkedList<>();
        List<Edge> edges = getEdges(node);

        for (Object edge1 : edges) {
            Edge edge = (Edge) edge1;

            if (edge.getDistalEndpoint(node) == endpoint) {
                nodes.add(edge.getDistalNode(node));
            }
        }

        return nodes;
    }

    /**
     * @return a matrix of endpoints for the nodes in this graph, with nodes in
     * the same order as getNodes().
     */
    public Endpoint[][] getEndpointMatrix() {
        int size = nodes.size();
        Endpoint[][] endpoints = new Endpoint[size][size];

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (i == j) {
                    continue;
                }

                Node nodei = nodes.get(i);
                Node nodej = nodes.get(j);

                endpoints[i][j] = getEndpoint(nodei, nodej);
            }
        }

        return endpoints;
    }

    /**
     * Adds an edge to the graph if the grpah constraints permit it.
     *
     * @param edge the edge to be added
     * @return true if the edge was added, false if not.
     */
    public boolean addEdge(Edge edge) {
        if (isGraphConstraintsChecked() && !checkAddEdge(edge)) {
            throw new IllegalArgumentException(
                    "Violates graph constraints: " + edge);
        }

        int i = nodeMap.get(edge.getNode1());
        int j = nodeMap.get(edge.getNode2());

        if (i == j) throw new IllegalArgumentException("Self loops not supported: " + edge);

        if (edge.getEndpoint2() == Endpoint.ARROW) {
            matrix[i][j] = 1;
        } else if (edge.getEndpoint2() == Endpoint.TAIL) {
            matrix[i][j] = 2;
        } else if (edge.getEndpoint2() == Endpoint.CIRCLE) {
            matrix[i][j] = 3;
        }

        if (edge.getEndpoint1() == Endpoint.ARROW) {
            matrix[j][i] = 1;
        } else if (edge.getEndpoint1() == Endpoint.TAIL) {
            matrix[j][i] = 2;
        } else if (edge.getEndpoint1() == Endpoint.CIRCLE) {
            matrix[j][i] = 3;
        }

        getPcs().firePropertyChange("edgeAdded", null, edge);
        return true;
    }

    /**
     * Adds a PropertyChangeListener to the graph.
     *
     * @param l the property change listener.
     */
    public void addPropertyChangeListener(PropertyChangeListener l) {
        getPcs().addPropertyChangeListener(l);
    }

    /**
     * Adds a node to the graph. Precondition: The proposed name of the node
     * cannot already be used by any other node in the same graph.
     *
     * @param node the node to be added.
     * @return true if the the node was added, false if not.
     */
    public boolean addNode(Node node) {
        throw new UnsupportedOperationException();
    }

    /**
     * @return the list of edges in the graph.  No particular ordering of the
     * edges in the list is guaranteed.
     */
    public Set<Edge> getEdges() {
        Set<Edge> edges = new HashSet<>();

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                int e1 = matrix[i][j];
                int e2 = matrix[j][i];

                if (e1 != 0 && e2 != 0) {
                    if (e1 == 1) {
                        edges.add(new Edge(nodes.get(i), nodes.get(j), Endpoint.TAIL, Endpoint.ARROW));
                    } else if (e2 == 1) {
                        edges.add(new Edge(nodes.get(j), nodes.get(i), Endpoint.TAIL, Endpoint.ARROW));
                    } else {
                        edges.add(new Edge(nodes.get(i), nodes.get(j), endpoint(e1), endpoint(e2)));
                    }
                }

                if (e1 == 0 && e2 != 0) {
                    throw new IllegalArgumentException("Inconsistent edge.");
                } else if (e2 == 0 && e1 != 0) {
                    throw new IllegalArgumentException("Inconsistent edge.");
                }
            }
        }

        return edges;
    }

    private Endpoint endpoint(int e1) {
        if (e1 == 1) {
            return Endpoint.ARROW;
        } else if (e1 == 2) {
            return Endpoint.TAIL;
        } else if (e1 == 3) {
            return Endpoint.CIRCLE;
        }

        throw new IllegalArgumentException("Endpoint number must be 1, 2, or 3: " + e1);
    }

    /**
     * Determines if the graph contains a particular edge.
     */
    public boolean containsEdge(Edge edge) {
        int index1 = nodeMap.get(edge.getNode1());
        int index2 = nodeMap.get(edge.getNode2());
        return matrix[index1][index2] != 0 && matrix[index2][index1] != 0;
    }

    /**
     * Determines whether the graph contains a particular node.
     */
    public boolean containsNode(Node node) {
        return nodes.contains(node);
    }

    /**
     * @return the list of edges connected to a particular node. No particular
     * ordering of the edges in the list is guaranteed.
     */
    public List<Edge> getEdges(Node node) {
        List<Edge> edges = new ArrayList<>();
        int i = nodeMap.get(node);

        for (int j = 0; j < nodes.size(); j++) {
            if (j == i) continue;

            int e1 = matrix[i][j];
            int e2 = matrix[j][i];

            if (e1 != 0 && e2 != 0) {
                if (e2 == 1) {
                    edges.add(new Edge(nodes.get(i), nodes.get(j), Endpoint.TAIL, Endpoint.ARROW));
                } else if (e1 == 1) {
                    edges.add(new Edge(nodes.get(j), nodes.get(i), Endpoint.TAIL, Endpoint.ARROW));
                } else {
                    edges.add(new Edge(nodes.get(i), nodes.get(j), endpoint(e1), endpoint(e2)));
                }
            }

            if (e1 == 0 && e2 != 0) {
                throw new IllegalArgumentException("Inconsistent edge.");
            } else if (e2 == 0 && e1 != 0) {
                throw new IllegalArgumentException("Inconsistent edge.");
            }
        }

        return edges;
    }

    public int hashCode() {
        int hashCode = 17;

        for (Node node : getNodes()) {
            hashCode += 23 * node.hashCode();
        }

        for (Edge edge : getEdges()) {
            hashCode += 29 * edge.hashCode();
        }

        return hashCode;
    }

    /**
     * @return true iff the given object is a graph that is equal to this graph,
     * in the sense that it contains the same nodes and the edges are
     * isomorphic.
     */
    public boolean equals(Object o) {
        if (o == null) {
            return false;
        }

        if (!(o instanceof MatrixGraphSimplified)) return false;

        Graph graph = (Graph) o;

        if (!new HashSet<>(graph.getNodeNames()).equals(new HashSet<>(getNodeNames()))) {
            return false;
        }

        if (!new HashSet<>(graph.getEdges()).equals(new HashSet<>(getEdges()))) {
            return false;
        }

        // If all tests pass, then return true.
        return true;
    }

    /**
     * Resets the graph so that it is fully connects it using #-# edges, where #
     * is the given endpoint.
     */
    public void fullyConnect(Endpoint endpoint) {
        byte endpointNumber = endpointNumber(endpoint);

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = 0; j < nodes.size(); j++) {
                if (i == j) continue;
                matrix[i][j] = endpointNumber;
            }
        }
    }

    private byte endpointNumber(Endpoint endpoint) {
        if (endpoint == Endpoint.ARROW) {
            return 1;
        } else if (endpoint == Endpoint.TAIL) {
            return 2;
        } else if (endpoint == Endpoint.CIRCLE) {
            return 3;
        }

        throw new IllegalArgumentException("Must be ARROR, TAIL, or CIRCLE: " + endpoint);
    }

    public void reorientAllWith(Endpoint endpoint) {
        byte endpointNumber = endpointNumber(endpoint);

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = 0; j < nodes.size(); j++) {
                if (i == j) continue;

                if (matrix[i][j] != 0 && matrix[j][i] != 0) {
                    matrix[i][j] = endpointNumber;
                    matrix[j][i] = endpointNumber;
                }
            }
        }
    }

    /**
     * @return the node with the given name, or null if no such node exists.
     */
    public Node getNode(String name) {
        for (Node node : nodes) {
            if (node.getName().equals(name)) {
                return node;
            }
        }

        return null;
    }

    /**
     * @return the number of nodes in the graph.
     */
    public int getNumNodes() {
        return nodes.size();
    }

    /**
     * @return the number of edges in the (entire) graph.
     */
    public int getNumEdges() {
        int count = 0;

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                if (i == j) continue;

                if (matrix[i][j] != 0 && matrix[j][i] != 0) {
                    count++;
                }
            }
        }

        return count;
    }

    /**
     * @return the number of edges connected to a particular node in the graph.
     */
    public int getNumEdges(Node node) {
        int i = nodeMap.get(node);

        int count = 0;

        for (int j = 0; j < nodes.size(); j++) {
            if (i == j) continue;

            if (matrix[i][j] != 0 && matrix[j][i] != 0) {
                count++;
            }
        }

        return count;
    }

    /**
     * @return the list of graph constraints for this graph.
     */
    public List<GraphConstraint> getGraphConstraints() {
        return new LinkedList<>(graphConstraints);
    }

    /**
     * @return true iff graph constraints will be checked for future graph
     * modifications.
     */
    public boolean isGraphConstraintsChecked() {
        return this.graphConstraintsChecked;
    }

    /**
     * Set whether graph constraints will be checked for future graph
     * modifications.
     */
    public void setGraphConstraintsChecked(boolean checked) {
        this.graphConstraintsChecked = checked;
    }

    public List<Node> getNodes() {
        return new ArrayList<>(nodes);
    }

    /**
     * Removes all nodes (and therefore all edges) from the graph.
     */
    public void clear() {
        for (int i = 0; i < nodes.size(); i++) {
            for (int j = 0; j < nodes.size(); j++) {
                matrix[i][j] = 0;
            }
        }
    }

    /**
     * Removes an edge from the graph. (Note: It is dangerous to make a
     * recursive call to this method (as it stands) from a method containing
     * certain types of iterators. The problem is that if one uses an iterator
     * that iterates over the edges of node A or node B, and tries in the
     * process to remove those edges using this method, a concurrent
     * modification exception will be thrown.)
     *
     * @param edge the edge to remove.
     * @return true if the edge was removed, false if not.
     */
    public boolean removeEdge(Edge edge) {
        int i = nodeMap.get(edge.getNode1());
        int j = nodeMap.get(edge.getNode2());

        if (matrix[i][j] != 0 && matrix[j][i] != 0) {
            matrix[i][j] = 0;
            matrix[j][i] = 0;
            return true;
        }

        return false;
    }

    /**
     * Removes any relevant edge objects found in this collection. G
     *
     * @param edges the collection of edges to remove.
     * @return true if any edges in the collection were removed, false if not.
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
     * Removes all edges connecting node A to node B.
     *
     * @param node1 the first node.,
     * @param node2 the second node.
     * @return true if edges were removed between A and B, false if not.
     */
    public boolean removeEdges(Node node1, Node node2) {
        return removeEdges(getEdges(node1, node2));
    }

    /**
     * Removes a node from the graph.
     */
    public boolean removeNode(Node node) {
        throw new UnsupportedOperationException();
    }

    /**
     * Removes any relevant node objects found in this collection.
     *
     * @param newNodes the collection of nodes to remove.
     * @return true if nodes from the collection were removed, false if not.
     */
    public boolean removeNodes(List<Node> newNodes) {
        throw new UnsupportedOperationException();
    }

    /**
     * @return a string representation of the graph.
     */
    public String toString() {
        StringBuilder buf = new StringBuilder();

        buf.append("\nGraph Nodes:\n");

        for (int i = 0; i < nodes.size(); i++) {
//            buf.append("\n" + (i + 1) + ". " + nodes.get(i));
            buf.append(nodes.get(i)).append(" ");
            if ((i + 1) % 30 == 0) buf.append("\n");
        }

        buf.append("\n\nGraph Edges: ");

        List<Edge> edges = new ArrayList<>(getEdges());
        Edges.sortEdges(edges);

        for (int i = 0; i < edges.size(); i++) {
            Edge edge = edges.get(i);
            buf.append("\n").append(i + 1).append(". ").append(edge);
        }

        buf.append("\n");
        buf.append("\n");

//        Set<Triple> ambiguousTriples = getAmbiguousTriples();

        if (!ambiguousTriples.isEmpty()) {
            buf.append("Ambiguous triples (i.e. list of triples for which there is ambiguous data" +
                    "\nabout whether they are colliders or not): \n");

            for (Triple triple : ambiguousTriples) {
                buf.append(triple).append("\n");
            }
        }

        if (!underLineTriples.isEmpty()) {
            buf.append("Underline triples: \n");

            for (Triple triple : underLineTriples) {
                buf.append(triple).append("\n");
            }
        }

        if (!dottedUnderLineTriples.isEmpty()) {
            buf.append("Dotted underline triples: \n");

            for (Triple triple : dottedUnderLineTriples) {
                buf.append(triple).append("\n");
            }
        }

        return buf.toString();
    }

    public Graph subgraph(List<Node> nodes) {
        Graph graph = new MatrixGraphSimplified(nodes);
        Set<Edge> edges = getEdges();

        for (Object edge1 : edges) {
            Edge edge = (Edge) edge1;

            if (nodes.contains(edge.getNode1()) &&
                    nodes.contains(edge.getNode2())) {
                graph.addEdge(edge);
            }
        }

        return graph;
    }

    /**
     * @return the edges connecting node1 and node2.
     */
    public List<Edge> getEdges(Node node1, Node node2) {
        List<Edge> edges = new LinkedList<>(getEdges(node1));

        for (Iterator<Edge> i = edges.iterator(); i.hasNext(); ) {
            Edge edge = i.next();

            if (edge.getDistalNode(node1) != node2) {
                i.remove();
            }
        }

        return edges;
    }

    public Set<Triple> getAmbiguousTriples() {
        removeTriplesNotInGraph();
        return new HashSet<>(ambiguousTriples);
    }

    public Set<Triple> getUnderLines() {
        removeTriplesNotInGraph();
        return new HashSet<>(underLineTriples);
    }

    public Set<Triple> getDottedUnderlines() {
        removeTriplesNotInGraph();
        return new HashSet<>(dottedUnderLineTriples);
    }


    /**
     * States whether x-y-x is an underline triple or not.
     */
    public boolean isAmbiguousTriple(Node x, Node y, Node z) {
        Triple triple = new Triple(x, y, z);
        if (!triple.alongPathIn(this)) {
            throw new IllegalArgumentException("<" + x + ", " + y + ", " + z + "> is not along a path.");
        }
        removeTriplesNotInGraph();
        return ambiguousTriples.contains(triple);
    }

    /**
     * States whether x-y-x is an underline triple or not.
     */
    public boolean isUnderlineTriple(Node x, Node y, Node z) {
        removeTriplesNotInGraph();
        return underLineTriples.contains(new Triple(x, y, z));
    }

    /**
     * States whether x-y-x is an underline triple or not.
     */
    public boolean isDottedUnderlineTriple(Node x, Node y, Node z) {
        Triple triple = new Triple(x, y, z);
        if (!triple.alongPathIn(this)) {
            throw new IllegalArgumentException("<" + x + ", " + y + ", " + z + "> is not along a path.");
        }
        removeTriplesNotInGraph();
        return dottedUnderLineTriples.contains(new Triple(x, y, z));
    }

    public void addAmbiguousTriple(Node x, Node y, Node z) {
        Triple triple = new Triple(x, y, z);

        if (!triple.alongPathIn(this)) {
            throw new IllegalArgumentException("<" + x + ", " + y + ", " + z + "> must lie along a path in the graph.");
        }

        ambiguousTriples.add(new Triple(x, y, z));
    }

    public void addUnderlineTriple(Node x, Node y, Node z) {
        Triple triple = new Triple(x, y, z);

        if (!triple.alongPathIn(this)) {
            throw new IllegalArgumentException("<" + x + ", " + y + ", " + z + "> must lie along a path in the graph.");
        }

        underLineTriples.add(new Triple(x, y, z));
    }

    public void addDottedUnderlineTriple(Node x, Node y, Node z) {
        Triple triple = new Triple(x, y, z);

        if (!triple.alongPathIn(this)) {
            throw new IllegalArgumentException("<" + x + ", " + y + ", " + z + "> must lie along a path in the graph.");
        }

        dottedUnderLineTriples.add(triple);
    }

    public void removeAmbiguousTriple(Node x, Node y, Node z) {
        ambiguousTriples.remove(new Triple(x, y, z));
    }

    public void removeUnderlineTriple(Node x, Node y, Node z) {
        underLineTriples.remove(new Triple(x, y, z));
    }

    public void removeDottedUnderlineTriple(Node x, Node y, Node z) {
        dottedUnderLineTriples.remove(new Triple(x, y, z));
    }


    public void setAmbiguousTriples(Set<Triple> triples) {
        ambiguousTriples.clear();

        for (Triple triple : triples) {
            addAmbiguousTriple(triple.getX(), triple.getY(), triple.getZ());
        }
    }

    public void setUnderLineTriples(Set<Triple> triples) {
        underLineTriples.clear();

        for (Triple triple : triples) {
            addUnderlineTriple(triple.getX(), triple.getY(), triple.getZ());
        }
    }


    public void setDottedUnderLineTriples(Set<Triple> triples) {
        dottedUnderLineTriples.clear();

        for (Triple triple : triples) {
            addDottedUnderlineTriple(triple.getX(), triple.getY(), triple.getZ());
        }
    }

    public List<String> getNodeNames() {
        List<String> names = new ArrayList<>();

        for (Node node : getNodes()) {
            names.add(node.getName());
        }

        return names;
    }


    //===============================PRIVATE METHODS======================//

    public void removeTriplesNotInGraph() {
        if (!stuffRemovedSinceLastTripleAccess) return;

        for (Triple triple : new HashSet<>(ambiguousTriples)) {
            if (!containsNode(triple.getX()) || !containsNode(triple.getY()) || !containsNode(triple.getZ())) {
                ambiguousTriples.remove(triple);
                continue;
            }

            if (!isAdjacentTo(triple.getX(), triple.getY()) || !isAdjacentTo(triple.getY(), triple.getZ())) {
                ambiguousTriples.remove(triple);
            }
        }

        for (Triple triple : new HashSet<>(underLineTriples)) {
            if (!containsNode(triple.getX()) || !containsNode(triple.getY()) || !containsNode(triple.getZ())) {
                underLineTriples.remove(triple);
                continue;
            }

            if (!isAdjacentTo(triple.getX(), triple.getY()) || !isAdjacentTo(triple.getY(), triple.getZ())) {
                underLineTriples.remove(triple);
            }
        }

        for (Triple triple : new HashSet<>(dottedUnderLineTriples)) {
            if (!containsNode(triple.getX()) || !containsNode(triple.getY()) || !containsNode(triple.getZ())) {
                dottedUnderLineTriples.remove(triple);
                continue;
            }

            if (!isAdjacentTo(triple.getX(), triple.getY()) || !isAdjacentTo(triple.getY(), triple.getZ())) {
                dottedUnderLineTriples.remove(triple);
            }
        }

        stuffRemovedSinceLastTripleAccess = false;
    }

    @Override
    public List<Node> getSepset(Node n1, Node n2) {
        throw new UnsupportedOperationException();
    }


    private void collectAncestorsVisit(Node node, Set<Node> ancestors) {
        ancestors.add(node);
        List<Node> parents = getParents(node);

        if (!parents.isEmpty()) {
            for (Object parent1 : parents) {
                Node parent = (Node) parent1;
                doParentClosureVisit(parent, ancestors);
            }
        }
    }

    private void collectDescendantsVisit(Node node, Set<Node> descendants) {
        descendants.add(node);
        List<Node> children = getChildren(node);

        if (!children.isEmpty()) {
            for (Object aChildren : children) {
                Node child = (Node) aChildren;
                doChildClosureVisit(child, descendants);
            }
        }
    }

    /**
     * closure under the child relation
     */
    private void doChildClosureVisit(Node node, Set<Node> closure) {
        if (!closure.contains(node)) {
            closure.add(node);

            for (Edge edge1 : getEdges(node)) {
                Node sub = Edges.traverseDirected(node, edge1);

                if (sub == null) {
                    continue;
                }

                doChildClosureVisit(sub, closure);
            }
        }
    }

    /**
     * This is a simple auxiliary visit method for the isDConnectedTo() method
     * used to find the closure of a conditioning set of nodes under the parent
     * relation.
     *
     * @param node    the node in question
     * @param closure the closure of the conditioning set uner the parent
     *                relation (to be calculated recursively).
     */
    private void doParentClosureVisit(Node node, Set<Node> closure) {
        closure.add(node);

        for (Edge edge : getEdges(node)) {
            Node sub = Edges.traverseReverseDirected(node, edge);

            if (sub == null || closure.contains(sub)) {
                continue;
            }

            doParentClosureVisit(sub, closure);
        }
    }

    /**
     * This is the main recursive visit method for the isDConnectedTo method.
     *
     * @param currentNode              the getModel node in the recursion
     * @param inEdgeEndpoint           the endpoint type of the incoming edge,
     *                                 needed to check for colliders.
     * @param targetNode               the node a d-connecting path is trying to
     *                                 reach.
     * @param path                     the list of nodes along the getModel path,
     *                                 to check for cycles.
     * @param conditioningNodes        a d-connecting path conditional on these
     *                                 nodes is being sought.
     * @param conditioningNodesClosure the closure of the conditioning nodes
     *                                 under the ancestor relation.
     * @return true if a d-connection is found along this path (here or down
     * some sub-branch), false if not.
     * @see #isDConnectedTo
     * @see #isDSeparatedFrom
     */
    private boolean isDConnectedToVisit(Node currentNode, Endpoint actualInEdgeEndpoint,
                                        Endpoint inEdgeEndpoint, Node targetNode, LinkedList<Node> path,
                                        List<Node> conditioningNodes, Set<Node> conditioningNodesClosure) {
//        System.out.println("Visiting " + currentNode);

        if (currentNode == targetNode) {
            return true;
        }

        if (path.contains(currentNode)) {
            return false;
        }

//        if (path.size() >= 4) {
//            return false;
//        }

//        HashSet<Node> s = new HashSet<Node>();
//        s.add(getNode("X2"));
//        s.add(getNode("X5"));
//        if (new HashSet<Node>(conditioningNodes).equals(s)
//                && isAdjacentTo(getNode("X1"), getNode("X2"))
//                && isAdjacentTo(getNode("X2"), getNode("X3"))
//                && isAdjacentTo(getNode("X2"), getNode("X4"))
//                && isAdjacentTo(getNode("X2"), getNode("X5"))
//                && isAdjacentTo(getNode("X3"), getNode("X5"))
//                ) {
//            System.out.println();
//        }

        path.addLast(currentNode);

        for (Edge edge1 : getEdges(currentNode)) {
            Endpoint outEdgeEndpoint = edge1.getProximalEndpoint(currentNode);

            // Apply the definition of d-connection to determine whether
            // we can pass through on a path from this incoming edge to
            // this outgoing edge through this node.  it all depends
            // on whether this path through the node is a collider or
            // not--that is, whether the incoming endpoint and the outgoing
            // endpoint are both arrow endpoints.
            boolean isCollider = (inEdgeEndpoint == Endpoint.ARROW) &&
                    (outEdgeEndpoint == Endpoint.ARROW);
            boolean passAsCollider = isCollider &&
                    conditioningNodesClosure.contains(currentNode);
            boolean passAsNonCollider =
                    !isCollider && !conditioningNodes.contains(currentNode);

            // makes sure not ->Xo-oY<- if passing as noncollider - Robert Tillman 7/19/2008
            if (passAsCollider && actualInEdgeEndpoint != null) {
                if (!actualInEdgeEndpoint.equals(Endpoint.ARROW)) {
                    passAsCollider = false;
                }
            }

            if (!Endpoint.ARROW.equals(actualInEdgeEndpoint)) {
                passAsCollider = false;
            }

            if (passAsCollider || passAsNonCollider) {
                Node nextNode = Edges.traverse(currentNode, edge1);
                // makes sure not ->Xo-oY<- if passing as noncollider - Robert Tillman 7/19/2008
                Endpoint previousEndpoint;
                Endpoint previousActual = edge1.getProximalEndpoint(nextNode);
                if (inEdgeEndpoint != null && inEdgeEndpoint.equals(Endpoint.ARROW) && passAsNonCollider) {
                    previousEndpoint = Endpoint.ARROW;
                } else {
                    previousEndpoint = previousActual;
                }
                if (isDConnectedToVisit(nextNode, previousActual, previousEndpoint, targetNode,
                        path, conditioningNodes, conditioningNodesClosure)) {
                    return true;
                }
                //   }
            }
        }
        path.removeLast();
        return false;
    }

    /**
     * Checks to see whether all of the graph constraints will be satisfied on
     * adding a particular edge.
     *
     * @param edge the edge to check.
     * @return true if the condition is met.
     */
    private boolean checkAddEdge(Edge edge) {
        for (GraphConstraint graphConstraint : graphConstraints) {
            GraphConstraint gc = (graphConstraint);

            if (!gc.isEdgeAddable(edge, this)) {
                System.out.println("Edge " + edge + " failed " + gc);
                return false;
            }
        }

        return true;
    }

    /**
     * @return this object.
     */
    private PropertyChangeSupport getPcs() {
        if (pcs == null) {
            pcs = new PropertyChangeSupport(this);
        }
        return pcs;
    }

    /**
     * @return true iff there is a directed path from node1 to node2.
     */
    private boolean existsUndirectedPathVisit(Node node1, Node node2,
                                              LinkedList<Node> path) {
        path.addLast(node1);

        for (Edge edge : getEdges(node1)) {
            Node child = Edges.traverse(node1, edge);

            if (child == null) {
                continue;
            }

            if (child == node2) {
                return true;
            }

            if (path.contains(child)) {
                continue;
            }

            if (existsUndirectedPathVisit(child, node2, path)) {
                return true;
            }
        }

        path.removeLast();
        return false;
    }

    private boolean existsDirectedPathVisit(Node node1, Node node2,
                                            LinkedList<Node> path) {
        path.addLast(node1);

        for (Edge edge : getEdges(node1)) {
            Node child = Edges.traverseDirected(node1, edge);

            if (child == null) {
                continue;
            }

            if (child == node2) {
                return true;
            }

            if (path.contains(child)) {
                continue;
            }

            if (existsDirectedPathVisit(child, node2, path)) {
                return true;
            }
        }

        path.removeLast();
        return false;
    }

    /**
     * @return true iff there is a semi-directed path from node1 to node2
     */
    private boolean existsSemiDirectedPathVisit(Node node1, Set<Node> nodes2,
                                                LinkedList<Node> path) {
        path.addLast(node1);

        for (Edge edge : getEdges(node1)) {
            Node child = Edges.traverseSemiDirected(node1, edge);

            if (child == null) {
                continue;
            }

            if (nodes2.contains(child)) {
                return true;
            }

            if (path.contains(child)) {
                continue;
            }

            if (existsSemiDirectedPathVisit(child, nodes2, path)) {
                return true;
            }
        }

        path.removeLast();
        return false;
    }

    public List<Node> getCausalOrdering() {
        List<Node> found = new LinkedList<>();
        Set<Node> notFound = new HashSet<>();

        for (Node node1 : getNodes()) {
            notFound.add(node1);
        }

        while (!notFound.isEmpty()) {
            for (Iterator<Node> it = notFound.iterator(); it.hasNext(); ) {
                Node node = it.next();

                if (found.containsAll(getParents(node))) {
                    found.add(node);
                    it.remove();
                }
            }
        }

        return found;
    }

    public void setHighlighted(Edge edge, boolean highlighted) {
        highlightedEdges.add(edge);
    }

    public boolean isHighlighted(Edge edge) {
        return highlightedEdges.contains(edge);
    }

    public boolean isParameterizable(Node node) {
        return true;
    }

    public boolean isTimeLagModel() {
        return false;
    }

    public TimeLagGraph getTimeLagGraph() {
        return null;
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

        if (nodes == null) {
            throw new NullPointerException();
        }

        if (graphConstraints == null) {
            throw new NullPointerException();
        }

        if (ambiguousTriples == null) {
            ambiguousTriples = new HashSet<>();
        }

        if (highlightedEdges == null) {
            highlightedEdges = new HashSet<>();
        }
    }
}


