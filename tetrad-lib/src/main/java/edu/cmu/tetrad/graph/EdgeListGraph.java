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

import static edu.cmu.tetrad.graph.Edges.directedEdge;

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
public class EdgeListGraph implements Graph {
    static final long serialVersionUID = 23L;

    /**
     * A list of the nodes in the graph, in the order in which they were added.
     *
     * @serial
     */
    private List<Node> nodes;

    /**
     * @deprecated
     */
    private List<Edge> edges;

    /**
     * The edges in the graph.
     *
     * @serial
     */
    private Set<Edge> edgesSet;

    /**
     * Map from each node to the List of edges connected to that node.
     *
     * @serial
     */
    private Map<Node, List<Edge>> edgeLists;

    /**
     * These are the graph constraints currently used.
     *
     * @serial
     */
    private List<GraphConstraint> graphConstraints;

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
    private Set<Triple> ambiguousTriples = new HashSet<Triple>();

    /**
     * @serial
     */
    private Set<Triple> underLineTriples = new HashSet<Triple>();

    /**
     * @serial
     */
    private Set<Triple> dottedUnderLineTriples = new HashSet<Triple>();

    /**
     * @serial
     * @deprecated 7/8/09
     */
    private Set<Pair> ambiguousPairs = new HashSet<Pair>();

    /**
     * True iff nodes were removed since the last call to an accessor for ambiguous, underline, or dotted underline
     * triples. If there are triples in the lists involving removed nodes, these need to be removed from the lists
     * first, so as not to cause confusion.
     */
    private boolean stuffRemovedSinceLastTripleAccess = false;

    /**
     * The set of highlighted edges.
     */
    private Set<Edge> highlightedEdges = new HashSet<Edge>();

    /**
     * A hash from node names to nodes;
     */
    private Map<String, Node> namesHash = new HashMap<String, Node>();

    Map<List<Node>, Set<Node>> ancestors;


    //==============================CONSTUCTORS===========================//

    /**
     * Constructs a new (empty) EdgeListGraph.
     */
    public EdgeListGraph() {
        this.graphConstraints = new LinkedList<GraphConstraint>();
        this.edgeLists = new HashMap<Node, List<Edge>>();
        this.nodes = new ArrayList<Node>();
        this.edgesSet = new HashSet<Edge>();
        this.namesHash = new HashMap<String, Node>();

        for (Node node : nodes) {
            namesHash.put(node.getName(), node);
        }
    }

    /**
     * Constructs a EdgeListGraph using the nodes and edges of the given graph.
     * If this cannot be accomplished successfully, an exception is thrown. Note
     * that any graph constraints from the given graph are forgotten in the new
     * graph.
     *
     * @param graph the graph from which nodes and edges are is to be
     *              extracted.
     * @throws IllegalArgumentException if a duplicate edge is added.
     */
    public EdgeListGraph(Graph graph) throws IllegalArgumentException {
        this();

        if (graph == null) {
            throw new NullPointerException("Graph must not be null.");
        }

        transferNodesAndEdges(graph);
        this.ambiguousTriples = graph.getAmbiguousTriples();
        this.underLineTriples = graph.getUnderLines();
        this.dottedUnderLineTriples = graph.getDottedUnderlines();


        for (Edge edge : graph.getEdges()) {
            if (graph.isHighlighted(edge)) {
                setHighlighted(edge, true);
            }
        }

        for (Node node : nodes) {
            namesHash.put(node.getName(), node);
        }
    }

    /**
     * Constructs a new graph, with no edges, using the the given variable
     * names.
     */
    public EdgeListGraph(List<Node> nodes) {
        this();

        if (nodes == null) {
            throw new NullPointerException();
        }

//        for (int i = 0; i < nodes.size(); i++) {
//            if (nodes.get(i) == null) {
//                throw new NullPointerException();
//            }
//
//            for (int j = 0; j < i; j++) {
//                if (nodes.get(i).equals(nodes.get(j))) {
//                    throw new IllegalArgumentException("Two variables by the same name: " + nodes.get(i));
//                }
//            }
//        }

        for (Object variable : nodes) {
            if (!addNode((Node) variable)) {
                throw new IllegalArgumentException();
            }
        }

//        DataGraphUtils.circleLayout(this, 200, 200, 150);
        for (Node node : nodes) {
            namesHash.put(node.getName(), node);
        }
    }

    // Makes a copy with the same object identical edges in it. If you make changes to those edges they will be
    // reflected here.
    public static Graph shallowCopy(EdgeListGraph graph) {
        EdgeListGraph _graph = new EdgeListGraph();

        _graph.nodes = new ArrayList(graph.nodes);
        _graph.edgesSet = new HashSet(graph.edgesSet);
        _graph.edgeLists = new HashMap<Node, List<Edge>>(graph.edgeLists);
        for (Node node : graph.nodes) _graph.edgeLists.put(node, new ArrayList(graph.edgeLists.get(node)));
        _graph.graphConstraints = new ArrayList<GraphConstraint>(graph.graphConstraints);
        _graph.graphConstraintsChecked = graph.graphConstraintsChecked;
        _graph.ambiguousTriples = new HashSet<Triple>(graph.ambiguousTriples);
        _graph.underLineTriples = new HashSet<Triple>(graph.underLineTriples);
        _graph.dottedUnderLineTriples = new HashSet<Triple>(graph.dottedUnderLineTriples);
        _graph.stuffRemovedSinceLastTripleAccess = graph.stuffRemovedSinceLastTripleAccess;
        _graph.highlightedEdges = new HashSet<Edge>(graph.highlightedEdges);
        _graph.namesHash = new HashMap(graph.namesHash);
        return _graph;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static EdgeListGraph serializableInstance() {
        return new EdgeListGraph();
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
        return addEdge(directedEdge(node1, node2));
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
            if (GraphUtils.existsDirectedPathFromToBreathFirst(node, node, this)) {
                return true;
            }

//            if (existsDirectedPathFromTo(node, node)) {
//                return true;
//            }
        }
        return false;
    }

    public boolean isDirectedFromTo(Node node1, Node node2) {
        List<Edge> edges = getEdges(node1, node2);
        if (edges.size() != 1) return false;
        Edge edge = edges.get(0);
        return edge.pointsTowards(node2);
    }

    public boolean isUndirectedFromTo(Node node1, Node node2) {
        Edge edge = getEdge(node1, node2);

        if (edge == null) return false;

        return edge.getEndpoint1() == Endpoint.TAIL && edge.getEndpoint2() == Endpoint.TAIL;
//        return getEdges(node1, node2).size() == 1
//                && getEndpoint(node2, node1) == Endpoint.TAIL
//                && getEndpoint(node1, node2) == Endpoint.TAIL;
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
                if (!((getAdjacentNodes(Curr)).contains(B)) &&
                        ((getEdge(Curr, A)).getProximalEndpoint(A) == Endpoint
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
        List<Edge> edges = getEdges(node2);
        boolean circle12 = false;
        boolean circle32 = false;

        for (int i = 0; i < edges.size(); i++) {
            Edge edge = edges.get(i);
            boolean _node1 = edge.getDistalNode(node2) == node1;
            boolean _node3 = edge.getDistalNode(node2) == node3;

            if (_node1 && edge.pointsTowards(node1)) return true;
            if (_node3 && edge.pointsTowards(node3)) return true;

            if (_node1 && edge.getProximalEndpoint(node2) == Endpoint.CIRCLE) circle12 = true;
            if (_node3 && edge.getProximalEndpoint(node2) == Endpoint.CIRCLE) circle32 = true;
            if (circle12 && circle32 && !isAdjacentTo(node1, node2)) return true;
        }

        return false;

//        if (isDirectedFromTo(node2, node1) || isDirectedFromTo(node2, node3)) {
//            return true;
//        } else if (!isAdjacentTo(node1, node3)) {
//            boolean endpt1 = getEndpoint(node1, node2) == Endpoint.CIRCLE;
//            boolean endpt2 = getEndpoint(node3, node2) == Endpoint.CIRCLE;
//            return (endpt1 && endpt2);
////        } else if (getEndpoint(node1, node2) == Endpoint.TAIL && getEndpoint(node3, node2) == Endpoint.TAIL){
////            return true;
//        } else {
//            return false;
//        }
    }

    public boolean isDefCollider(Node node1, Node node2, Node node3) {
        Edge edge1 = getEdge(node1, node2);
        Edge edge2 = getEdge(node2, node3);

        if (edge1 == null || edge2 == null) {
            return false;
        }

        return edge1.getProximalEndpoint(node2) == Endpoint.ARROW &&
                edge2.getProximalEndpoint(node2) == Endpoint.ARROW;
    }

    /**
     * @return true iff there is a directed path from node1 to node2.
     * a
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
        List<Node> children = new ArrayList<Node>();

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
        HashSet<Node> descendants = new HashSet<Node>();

        for (Object node1 : nodes) {
            Node node = (Node) node1;
            collectDescendantsVisit(node, descendants);
        }

        return new LinkedList<Node>(descendants);
    }

    /**
     * @return the edge connecting node1 and node2, provided a unique such edge
     * exists.
     */
    public Edge getEdge(Node node1, Node node2) {
        List<Edge> edges1 = edgeLists.get(node1);

        if (edges1 == null) return null;

        for (Edge edge : edges1) {
            if (edge.getNode1() == node1 && edge.getNode2() == node2) {
                return edge;
            } else if (edge.getNode1() == node2 && edge.getNode2() == node1) {
                return edge;
            }
        }

        return null;
    }

    public Edge getDirectedEdge(Node node1, Node node2) {
        List<Edge> edges = getEdges(node1, node2);

        if (edges == null) return null;

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
        List<Node> parents = new ArrayList<Node>();
        List<Edge> edges = edgeLists.get(node);

        for (Edge edge : edges) {
            if (edge == null) continue;

            Endpoint endpoint1 = edge.getDistalEndpoint(node);
            Endpoint endpoint2 = edge.getProximalEndpoint(node);

            if (endpoint1 == Endpoint.TAIL && endpoint2 == Endpoint.ARROW) {
                parents.add(edge.getDistalNode(node));
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
        if (node1 == null || node2 == null || edgeLists.get(node1) == null || edgeLists.get(node2) == null) {
            return false;
        }

        for (Edge edge : edgeLists.get(node1)) {
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
        return (node1 == node2) || GraphUtils.existsDirectedPathFromTo(node1, node2, this);
    }

    public boolean possibleAncestor(Node node1, Node node2) {
        return existsSemiDirectedPathFromTo(node1,
                Collections.singleton(node2));
    }

    /**
     * @return true iff node1 is a possible ancestor of at least one member of
     * nodes2
     */
    public boolean possibleAncestorSet(Node node1, List<Node> nodes2) {
        for (Object aNodes2 : nodes2) {
            if (possibleAncestor(node1, (Node) aNodes2)) {
                return true;
            }
        }
        return false;
    }

    public List<Node> getAncestors(List<Node> nodes) {
        HashSet<Node> ancestors = new HashSet<Node>();

        for (Object node1 : nodes) {
            Node node = (Node) node1;
            collectAncestorsVisit(node, ancestors);
        }

        return new ArrayList<Node>(ancestors);
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


    public boolean isDConnectedTo(Node x, Node y, List<Node> z) {
        return GraphUtils.isDConnectedTo(x, y, z, this);
    }

    public boolean isDConnectedTo(List<Node> x, List<Node> y, List<Node> z) {
        Set<Node> zAncestors = zAncestors(z);

        Queue<Pair> Q = new ArrayDeque<Pair>();
        Set<Pair> V = new HashSet<Pair>();

        for (Node _x : x) {
            for (Node node : getAdjacentNodes(_x)) {
                if (y.contains(node)) return true;
                Pair edge = new Pair(_x, node);
                Q.offer(edge);
                V.add(edge);
            }
        }

        while (!Q.isEmpty()) {
            Pair t = Q.poll();

            Node b = t.getY();
            Node a = t.getX();

            for (Node c : getAdjacentNodes(b)) {
                if (c == a) continue;

                boolean collider = isDefCollider(a, b, c);
                if (!((collider && zAncestors.contains(b)) || (!collider && !z.contains(b)))) continue;

                if (y.contains(c)) return true;

                Pair u = new Pair(b, c);
                if (V.contains(u)) continue;

                V.add(u);
                Q.offer(u);
            }
        }

        return false;
    }

    public List<Node> getSepset(Node x, Node y) {
        return GraphUtils.getSepset(x, y, this);
    }

    private boolean pass(Node a, Node b, Node c, List<Node> z) {
        boolean collider = isDefCollider(a, b, c);

        boolean ancestor = false;

        for (Node n : z) {
            if (isAncestorOf(b, n)) {
                ancestor = true;
                break;
            }
        }
        return collider && ancestor || !collider && !z.contains(b);
    }

    private Set<Node> zAncestors(List<Node> z) {
        Queue<Node> Q = new ArrayDeque<Node>();
        Set<Node> V = new HashSet<Node>();

        for (Node node : z) {
            Q.offer(node);
            V.add(node);
        }

        while (!Q.isEmpty()) {
            Node t = Q.poll();

            for (Node c : getParents(t)) {
                if (V.contains(c)) continue;
                V.add(c);
                Q.offer(c);
            }
        }

        return V;
    }


    public boolean isDSeparatedFrom(List<Node> x, List<Node> y, List<Node> z) {
        return !isDConnectedTo(x, y, z);
    }

    private static class Pair {
        private Node x;
        private Node y;

        public Pair(Node x, Node y) {
            this.x = x;
            this.y = y;
        }

        public Node getX() {
            return x;
        }

        public Node getY() {
            return y;
        }

        public int hashCode() {
            return x.hashCode() + 17 * y.hashCode();
        }

        public boolean equals(Object o) {
            if (o == this) return true;
            Pair pair = (Pair) o;
            return x == pair.getX() && y == pair.getY();
        }

        public String toString() {
            return "(" + x.toString() + ", " + y.toString() + ")";
        }

    }

    /**
     * Determines whether one n ode is d-separated from another. According to
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
     * @return true if node1 is d-separated from node2 given set t, false if
     * not.
     * @see #isDConnectedTo
     */

    public boolean isDSeparatedFrom(Node node1, Node node2, List<Node> z) {
        return !isDConnectedTo(node1, node2, z);
    }

    //added by ekorber, June 2004
    public boolean possDConnectedTo(Node node1, Node node2,
                                    List<Node> condNodes) {
        LinkedList<Node> allNodes = new LinkedList<Node>(getNodes());
        int sz = allNodes.size();
        int[][] edgeStage = new int[sz][sz];
        int stage = 1;

        int n1x = allNodes.indexOf(node1);
        int n2x = allNodes.indexOf(node2);

        edgeStage[n1x][n1x] = 1;
        edgeStage[n2x][n2x] = 1;

        List<int[]> currEdges;
        List<int[]> nextEdges = new LinkedList<int[]>();

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
            nextEdges = new LinkedList<int[]>();
            for (int[] edge : currEdges) {
                Node center = allNodes.get(edge[1]);
                List<Node> adj = new LinkedList<Node>(getAdjacentNodes(center));

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

//        System.out.println("TANSFER BEFORE " + graph.getEdges());

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

//        System.out.println("TANSFER AFTER " + getEdges());
    }

    /**
     * Determines whether a node in a graph is exogenous.
     */
    public boolean isExogenous(Node node) {
        return getIndegree(node) == 0;
    }

    /**
     * @return the set of nodes adjacent to the given node. If there are multiple edges between X and Y, Y will show
     * up twice in the list of adjacencies for X, for optimality; simply create a list an and array from these to
     * eliminate the duplication.
     */
    public List<Node> getAdjacentNodes(Node node) {
        List<Edge> edges = edgeLists.get(node);
        List<Node> adj = new ArrayList<Node>(edges.size());

        for (Edge edge : edges) {
            if (edge == null) continue;
            adj.add(edge.getDistalNode(node));
        }

        return adj;
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

        ancestors = null;

        return removeEdges(edges);
    }

    /**
     * @return the endpoint along the edge from node to node2 at the node2 end.
     */
    public Endpoint getEndpoint(Node node1, Node node2) {
        List<Edge> edges = getEdges(node2);

        for (int i = 0; i < edges.size(); i++) {
            Edge edge = edges.get(i);
            if (edge.getDistalNode(node2) == node1) return edge.getProximalEndpoint(node2);
        }

        return null;


//        List<Edge> edges = getEdges(node1, node2);
//
//        if (edges.size() == 0) {
//            retu rn null;
//        }
//
//        if (edges.size() > 1) {
//            throw new IllegalArgumentException(
//                    "More than one edge between " + node1 + " and " + node2);
//        }
//
//        return (edges.get(0)).getProximalEndpoint(node2);
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
            throw new NullPointerException();
        } else if (edges.size() == 0) {
            removeEdges(from, to);
            addEdge(new Edge(from, to, Endpoint.TAIL, endPoint));
            return true;
        } else if (edges.size() == 1) {
            Edge edge = edges.get(0);
            Edge newEdge = new Edge(from, to, edge.getProximalEndpoint(from), endPoint);

            try {
                removeEdges(edge.getNode1(), edge.getNode2());
                addEdge(newEdge);
                return true;
            } catch (IllegalArgumentException e) {
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
        List<Node> nodes = new ArrayList<Node>(4);
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
        List<Node> nodes = new ArrayList<Node>(4);
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
     * Adds an edge to the graph.
     *
     * @param edge the edge to be added
     * @return true if the edge was added, false if not.
     */
    public boolean addEdge(Edge edge) {
        if (edge == null) throw new NullPointerException();

//        if (isGraphConstraintsChecked() && !checkAddEdge(edge)) {
//            throw new IllegalArgumentException(
//                    "Violates graph constraints: " + edge);
//        }

        List<Edge> edgeList1 = edgeLists.get(edge.getNode1());
        List<Edge> edgeList2 = edgeLists.get(edge.getNode2());

        if (edgeList1 == null || edgeList2 == null) {

            // Do not comment this out; if the user changes the names of variables, this is the
            // mechanism for adjusting the maps from nodes to edge lists to compensate.
            edgeLists = new HashMap<Node, List<Edge>>(edgeLists);
            edgeList1 = edgeLists.get(edge.getNode1());
            edgeList2 = edgeLists.get(edge.getNode2());
        }

        if (edgeList1 == null || edgeList2 == null) {
            throw new NullPointerException("Can't add an edge unless both " +
                    "nodes are in the graph: " + edge);
        }

        if (edgeList1.contains(edge)) {
            return true;
//            throw new IllegalArgumentException(
//                    "That edge is already in the graph: " + edge);
        }

        if (edgeList2.contains(edge)) {
            return true;
//            throw new IllegalArgumentException(
//                    "That edge is already in the graph: " + edge);
        }

        edgeList1.add(edge);
        edgeList2.add(edge);
        edgesSet.add(edge);

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
        if (nodes.contains(node)) return true;

        if (node == null) {
            throw new NullPointerException();
        }

        if (!(getNode(node.getName()) == null)) {
            if (nodes.contains(node)) {
                namesHash.put(node.getName(), node);
            }

//            return false;

            // This is problematic for the sem updater. jdramsey 7/23/2005
//            throw new IllegalArgumentException("A node by name " +
//                    node.getName() + " has already been added to the graph.");
        }

        if (edgeLists.containsKey(node)) {
            return false;
        }

        if (nodes.contains(node)) {
            return false;
        }

        if (isGraphConstraintsChecked() && !checkAddNode(node)) {
            return false;
        }

        edgeLists.put(node, new ArrayList<Edge>(4));
        nodes.add(node);
        namesHash.put(node.getName(), node);

        if (node.getNodeType() != NodeType.ERROR) {
            getPcs().firePropertyChange("nodeAdded", null, node);
        }

        ancestors = null;

        return true;
    }

    /**
     * @return the list of edges in the graph.  No particular ordering of the
     * edges in the list is guaranteed.
     */
    public Set<Edge> getEdges() {
        return new HashSet<Edge>(this.edgesSet);
    }

    /**
     * Determines if the graph contains a particular edge.
     */
    public boolean containsEdge(Edge edge) {
        return edgesSet.contains(edge);
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
        List<Edge> list = edgeLists.get(node);
        return new ArrayList<>(list);
    }

    public int hashCode() {
        int hashCode = 0;
        int sum = 0;

        for (Node node : getNodes()) {
            sum += node.hashCode();
        }

        hashCode += 23 * sum;
        sum = 0;

        for (Edge edge : getEdges()) {
            sum += edge.hashCode();
        }

        hashCode += 41 * sum;

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

        if (o instanceof EdgeListGraph) {
            EdgeListGraph _o = (EdgeListGraph) o;
            boolean nodesEqual = new HashSet<Node>(_o.nodes).equals(new HashSet<Node>(this.nodes));
            boolean edgesEqual = new HashSet<Edge>(_o.edgesSet).equals(new HashSet<Edge>(this.edgesSet));
            return (nodesEqual && edgesEqual);
        } else {
            Graph graph = (Graph) o;

            if (!new HashSet<String>(graph.getNodeNames()).equals(new HashSet<String>(getNodeNames()))) {
                return false;
            }

            if (!new HashSet<Edge>(graph.getEdges()).equals(new HashSet<Edge>(getEdges()))) {
                return false;
            }

            return true;
        }
    }

    /**
     * Resets the graph so that it is fully connects it using #-# edges, where #
     * is the given endpoint.
     */
    public void fullyConnect(Endpoint endpoint) {
        edgesSet.clear();
        edgeLists.clear();

        for (Node node : nodes) {
            edgeLists.put(node, new ArrayList<Edge>(4));
        }

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                Node node1 = nodes.get(i);
                Node node2 = nodes.get(j);

                Edge edge = new Edge(node1, node2, endpoint, endpoint);
                addEdge(edge);
            }
        }
    }

    public void reorientAllWith(Endpoint endpoint) {
        for (Edge edge : new ArrayList<Edge>(edgesSet)) {
            Node a = edge.getNode1();
            Node b = edge.getNode2();
            setEndpoint(a, b, endpoint);
            setEndpoint(b, a, endpoint);
        }
    }

    /**
     * @return the node with the given name, or null if no such node exists.
     */
    public Node getNode(String name) {
        return namesHash.get(name);
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
        return edgesSet.size();
    }

    /**
     * @return the number of edges connected to a particular node in the graph.
     */
    public int getNumEdges(Node node) {
        List<Edge> list = edgeLists.get(node);
        return (list == null) ? 0 : list.size();
    }

    /**
     * @return the list of graph constraints for this graph.
     */
    public List<GraphConstraint> getGraphConstraints() {
        return new LinkedList<GraphConstraint>(graphConstraints);
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
        return new ArrayList<Node>(nodes);
    }

    /**
     * Removes all nodes (and therefore all edges) from the graph.
     */
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
            namesHash.remove(node.getName());
            getPcs().firePropertyChange("nodeRemoved", node, null);
        }

        edgeLists.clear();
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
        if (edgesSet.contains(edge) && !checkRemoveEdge(edge)) {
            return false;
        }

        List<Edge> edgeList1 = edgeLists.get(edge.getNode1());
        List<Edge> edgeList2 = edgeLists.get(edge.getNode2());

        edgesSet.remove(edge);
        edgeList1.remove(edge);
        edgeList2.remove(edge);
        highlightedEdges.remove(edge);
        stuffRemovedSinceLastTripleAccess = true;

        getPcs().firePropertyChange("edgeRemoved", edge, null);
        return true;
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
        if (nodes.contains(node) && !checkRemoveNode(node)) {
            return false;
        }

        boolean changed = false;
        List<Edge> edgeList1 =
                edgeLists.get(node);    //list of edges connected to that node

        for (Iterator<Edge> i = edgeList1.iterator(); i.hasNext(); ) {
            Edge edge = (i.next());
            Node node2 = edge.getDistalNode(node);

            if (node2 != node) {
                List<Edge> edgeList2 = edgeLists.get(node2);
                edgeList2.remove(edge);
                edgesSet.remove(edge);
                changed = true;
            }

            i.remove();
            getPcs().firePropertyChange("edgeRemoved", edge, null);
        }

        edgeLists.remove(node);
        nodes.remove(node);
        namesHash.remove(node.getName());
        stuffRemovedSinceLastTripleAccess = true;
        ancestors = null;

        getPcs().firePropertyChange("nodeRemoved", node, null);
        return changed;
    }

    /**
     * Removes any relevant node objects found in this collection.
     *
     * @param newNodes the collection of nodes to remove.
     * @return true if nodes from the collection were removed, false if not.
     */
    public boolean removeNodes(List<Node> newNodes) {
        boolean changed = false;

        for (Object newNode : newNodes) {
            boolean _changed = removeNode((Node) newNode);
            changed = changed || _changed;
        }

        return changed;
    }

    /**
     * @return a string representation of the graph.
     */
    public String toString() {
        StringBuilder buf = new StringBuilder();

        buf.append("\nGraph Nodes:\n");

        for (int i = 0; i < nodes.size(); i++) {
//            buf.append("\n" + (i + 1) + ". " + nodes.get(i));
            buf.append(nodes.get(i) + " ");
            if ((i + 1) % 30 == 0) buf.append("\n");
        }

        buf.append("\n\nGraph Edges: ");

        List<Edge> edges = new ArrayList<Edge>(this.edgesSet);
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
//
//        buf.append("\nNode positions\n");
//
//        for (Node node : getNodes()) {
//            buf.append("\n" + node + ": (" + node.getCenterX() + ", " + node.getCenterY() + ")");
//        }

        return buf.toString();
    }

    public Graph subgraph(List<Node> nodes) {
        Graph graph = new EdgeListGraph(nodes);
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
        List<Edge> edges = edgeLists.get(node1);
        List<Edge> _edges = new ArrayList<Edge>();

        for (int i = 0; i < edges.size(); i++) {
            Edge edge = edges.get(i);
            if (edge.getDistalNode(node1) == node2) {
                _edges.add(edge);
            }
        }

        return _edges;
    }

    public Set<Triple> getAmbiguousTriples() {
//        removeTriplesNotInGraph();
        return new HashSet<Triple>(ambiguousTriples);
    }

    public Set<Triple> getUnderLines() {
//        removeTriplesNotInGraph();
        return new HashSet<Triple>(underLineTriples);
    }

    public Set<Triple> getDottedUnderlines() {
//        removeTriplesNotInGraph();
        return new HashSet<Triple>(dottedUnderLineTriples);
    }


    /**
     * States whether r-s-r is an underline triple or not.
     */
    public boolean isAmbiguousTriple(Node x, Node y, Node z) {
//        Triple triple = new Triple(x, y, z);
//        if (!triple.alongPathIn(this)) {
//            throw new IllegalArgumentException("<" + x + ", " + y + ", " + z + "> is not along a path.");
//        }
//        removeTriplesNotInGraph();
        return ambiguousTriples.contains(new Triple(x, y, z));
    }

    /**
     * States whether r-s-r is an underline triple or not.
     */
    public boolean isUnderlineTriple(Node x, Node y, Node z) {
//        Triple triple = new Triple(x, y, z);
//        if (!triple.alongPathIn(this)) {
//            throw new IllegalArgumentException("<" + r + ", " + s + ", " + t + "> is not along a path.");
//        }
//        removeTriplesNotInGraph();
        return underLineTriples.contains(new Triple(x, y, z));
    }

    /**
     * States whether r-s-r is an underline triple or not.
     */
    public boolean isDottedUnderlineTriple(Node x, Node y, Node z) {
//        Triple triple = new Triple(x, y, z);
//        if (!triple.alongPathIn(this)) {
//            throw new IllegalArgumentException("<" + x + ", " + y + ", " + z + "> is not along a path.");
//        }
//        removeTriplesNotInGraph();
        return dottedUnderLineTriples.contains(new Triple(x, y, z));
    }

    public void addAmbiguousTriple(Node x, Node y, Node z) {
        Triple triple = new Triple(x, y, z);

        if (!triple.alongPathIn(this)) {
            for (Edge e : getEdges(y)) {
                System.out.println(e);
            }

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
        List<String> names = new ArrayList<String>();

        for (Node node : getNodes()) {
            names.add(node.getName());
        }

        return names;
    }


    //===============================PRIVATE METHODS======================//

    public void removeTriplesNotInGraph() {
//        if (!stuffRemovedSinceLastTripleAccess) return;

        for (Triple triple : new HashSet<Triple>(ambiguousTriples)) {
            if (!containsNode(triple.getX()) || !containsNode(triple.getY()) || !containsNode(triple.getZ())) {
                ambiguousTriples.remove(triple);
                continue;
            }

            if (!isAdjacentTo(triple.getX(), triple.getY()) || !isAdjacentTo(triple.getY(), triple.getZ())) {
                ambiguousTriples.remove(triple);
            }
        }

        for (Triple triple : new HashSet<Triple>(underLineTriples)) {
            if (!containsNode(triple.getX()) || !containsNode(triple.getY()) || !containsNode(triple.getZ())) {
                underLineTriples.remove(triple);
                continue;
            }

            if (!isAdjacentTo(triple.getX(), triple.getY()) || !isAdjacentTo(triple.getY(), triple.getZ())) {
                underLineTriples.remove(triple);
            }
        }

        for (Triple triple : new HashSet<Triple>(dottedUnderLineTriples)) {
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
        if (closure.contains(node)) return;
        closure.add(node);

        for (Edge edge : getEdges(node)) {
            Node sub = Edges.traverseReverseDirected(node, edge);
            if (sub != null) {
                doParentClosureVisit(sub, closure);
            }
        }
    }


    /**
     * This is the main visit method for the existsInducingPath() method.
     *
     * @param node1 the getModel node in the recursion.
     * @param node2 the target node.
     * @param inEnd the endpoint type of the incoming edge.
     * @return true if an inducing path is found along this path (here or down
     * some sub-branch), false if not.
     * @see Graph#existsInducingPath
     */
    private boolean existsInducingPathVisit(Node node1, Node node2,
                                            Endpoint inEnd, Set<Node> pathNodes, Set<Node> observedNodes,
                                            Set<Node> conditioningNodes, Set<Node> sClosure) {
        if (node1 == node2) {
            return true;
        } else if (pathNodes.contains(node1)) {
            return false;
        } else {
            pathNodes.add(node1);

            for (Edge edge1 : getEdges(node1)) {
                Endpoint outEnd = edge1.getProximalEndpoint(node1);

                // apply the definition of inducing path to determine whether
                // we can pass through on a path from this incoming edge to
                // this outgoing edge through this node.  it all depends
                // on whether this path through the node is a collider or
                // not--that is, whether the incoming endpoint and the outgoing
                // endpoint are both arrows.
                boolean isCollider =
                        (inEnd == Endpoint.ARROW) && (outEnd == Endpoint.ARROW);
                boolean passAsCollider = isCollider && sClosure.contains(node1);
                boolean passAsNonCollider = !isCollider &&
                        !observedNodes.contains(node1) &&
                        !conditioningNodes.contains(node1);

                if (passAsCollider || passAsNonCollider) {
                    Node sub = Edges.traverse(node1, edge1);
                    Endpoint newIn = edge1.getProximalEndpoint(sub);

                    if (existsInducingPathVisit(sub, node2, newIn, pathNodes,
                            observedNodes, conditioningNodes, sClosure)) {
                        return true;
                    }
                }
            }

            pathNodes.remove(node1);
            return false;
        }
    }

    /**
     * Checks to see whether all of the graph basicConstraints will be satisfied
     * on adding a particular node.
     *
     * @param node the node to check.
     * @return true if adding the node is permitted by all of the graph
     * constraints, false if not.
     */
    private boolean checkAddNode(Node node) {
        for (GraphConstraint graphConstraint : graphConstraints) {
            GraphConstraint gc = (graphConstraint);

            if (!gc.isNodeAddable(node, this)) {
                return false;
            }
        }

        return true;
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
     * Checks to see whether all of the graph constraints will be satisfied on
     * removing a particular node.
     *
     * @param node the node to check.
     * @return true if removing the node is permitted by all of the graph
     * constraints, false if not.
     */
    private boolean checkRemoveNode(Node node) {
        for (GraphConstraint graphConstraint : graphConstraints) {
            GraphConstraint gc = (graphConstraint);

            if (!gc.isNodeRemovable(node, this)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Checks to see whether all of the graph constraints will be satisfied on
     * removing a particular edge.
     *
     * @param edge the edge to check.
     * @return true if removing the edge is permitted by all of the graph
     * constraints, false if not.
     */
    private boolean checkRemoveEdge(Edge edge) {
        for (GraphConstraint graphConstraint : graphConstraints) {
            GraphConstraint gc = (graphConstraint);

            if (!gc.isEdgeRemovable(edge, this)) {
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
        return GraphUtils.getCausalOrdering(this);
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

        if (edges != null && edgesSet == null) {
            edgesSet = new HashSet<Edge>(edges);
        }

        if (edgesSet == null) {
            throw new NullPointerException();
        }

        if (edgeLists == null) {
            throw new NullPointerException();
        }

        if (graphConstraints == null) {
            throw new NullPointerException();
        }

        if (ambiguousTriples == null) {
            ambiguousTriples = new HashSet<Triple>();
        }

        if (ambiguousPairs == null) {
            ambiguousPairs = new HashSet<Pair>();
        }

        if (highlightedEdges == null) {
            highlightedEdges = new HashSet<Edge>();
        }

        if (underLineTriples == null) {
            underLineTriples = new HashSet<Triple>();
        }

        if (dottedUnderLineTriples == null) {
            dottedUnderLineTriples = new HashSet<Triple>();
        }
    }

    public void changeName(String name, String newName) {
        Node node = namesHash.get(name);
        namesHash.remove(name);
        node.setName(newName);
        namesHash.put(newName, node);
    }
}




