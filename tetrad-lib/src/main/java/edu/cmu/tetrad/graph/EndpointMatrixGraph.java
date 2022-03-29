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
 * @see Endpoint
 */
public class EndpointMatrixGraph implements Graph {
    static final long serialVersionUID = 23L;

    private short[][] graphMatrix = new short[0][0];

    /**
     * A list of the nodes in the graph, in the order in which they were added.
     *
     * @serial
     */
    private List<Node> nodes;

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

    /**
     * A hash from node names to nodes;
     */
    private Map<String, Node> namesHash = new HashMap<>();
    private HashMap<Node, Integer> nodesHash;
    private HashMap<Short, Endpoint> shortsToEndpoints;
    private HashMap<Endpoint, Short> endpointsToShorts;
    private int numEdges = 0;

    private boolean pag;
    private boolean CPDAG;

    private final Map<String, Object> attributes = new HashMap<>();

    //==============================CONSTUCTORS===========================//

    /**
     * Constructs a new (empty) EdgeListGraph.
     */
    public EndpointMatrixGraph() {
        this.nodes = new ArrayList<>();
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
    public EndpointMatrixGraph(final Graph graph) throws IllegalArgumentException {
        this();

        if (graph == null) {
            throw new NullPointerException("Graph must not be null.");
        }

        transferNodesAndEdges(graph);
        this.ambiguousTriples = graph.getAmbiguousTriples();
        this.underLineTriples = graph.getUnderLines();
        this.dottedUnderLineTriples = graph.getDottedUnderlines();


        for (final Edge edge : graph.getEdges()) {
            if (graph.isHighlighted(edge)) {
                setHighlighted(edge, true);
            }
        }

        for (final Node node : this.nodes) {
            this.namesHash.put(node.getName(), node);
        }

        initHashes();
    }

    /**
     * Constructs a new graph, with no edges, using the the given variable
     * names.
     */
    private EndpointMatrixGraph(final List<Node> nodes) {
        this();

        if (nodes == null) {
            throw new NullPointerException();
        }

        for (final Object variable : nodes) {
            if (!addNode((Node) variable)) {
                throw new IllegalArgumentException();
            }
        }

        this.graphMatrix = new short[nodes.size()][nodes.size()];

        for (final Node node : nodes) {
            this.namesHash.put(node.getName(), node);
        }

        initHashes();
    }

    // Makes a copy with the same object identical edges in it. If you make changes to those edges they will be
    // reflected here.
    public static Graph shallowCopy(final EndpointMatrixGraph graph) {
        final EndpointMatrixGraph _graph = new EndpointMatrixGraph();

        _graph.graphMatrix = copy(graph.graphMatrix);
        _graph.nodes = new ArrayList<>(graph.nodes);
        _graph.ambiguousTriples = new HashSet<>(graph.ambiguousTriples);
        _graph.underLineTriples = new HashSet<>(graph.underLineTriples);
        _graph.dottedUnderLineTriples = new HashSet<>(graph.dottedUnderLineTriples);
        _graph.stuffRemovedSinceLastTripleAccess = graph.stuffRemovedSinceLastTripleAccess;
        _graph.highlightedEdges = new HashSet<>(graph.highlightedEdges);
        _graph.namesHash = new HashMap<>(graph.namesHash);
        return _graph;
    }

    private static short[][] copy(final short[][] graphMatrix) {
        final short[][] copy = new short[graphMatrix.length][graphMatrix[0].length];

        if (copy.length == 0) {
            return new short[0][0];
        }

        for (int i = 0; i < copy.length; i++) {
            System.arraycopy(graphMatrix[i], 0, copy[i], 0, copy[0].length);
        }

        return copy;
    }

    private void initHashes() {
        this.nodesHash = new HashMap<>();

        for (final Node node : this.nodes) {
            this.nodesHash.put(node, this.nodes.indexOf(node));
        }

        this.endpointsToShorts = new HashMap<>();

        this.endpointsToShorts.put(Endpoint.TAIL, (short) 1);
        this.endpointsToShorts.put(Endpoint.ARROW, (short) 2);
        this.endpointsToShorts.put(Endpoint.CIRCLE, (short) 3);

        this.shortsToEndpoints = new HashMap<>();

        this.shortsToEndpoints.put((short) 1, Endpoint.TAIL);
        this.shortsToEndpoints.put((short) 2, Endpoint.ARROW);
        this.shortsToEndpoints.put((short) 3, Endpoint.CIRCLE);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static EndpointMatrixGraph serializableInstance() {
        return new EndpointMatrixGraph();
    }

    //===============================PUBLIC METHODS========================//

    /**
     * Adds a directed edge to the graph from node A to node B.
     *
     * @param node1 the "from" node.
     * @param node2 the "to" node.
     */
    public boolean addDirectedEdge(final Node node1, final Node node2) {
        final int i = this.nodesHash.get(node1);
        final int j = this.nodesHash.get(node2);

        if (this.graphMatrix[i][j] != 0) {
            return false;
        }

        this.graphMatrix[j][i] = 1;
        this.graphMatrix[i][j] = 2;

        this.numEdges++;

        return true;
    }

    /**
     * Adds an undirected edge to the graph from node A to node B.
     *
     * @param node1 the "from" node.
     * @param node2 the "to" node.
     */
    public boolean addUndirectedEdge(final Node node1, final Node node2) {
        return addEdge(Edges.undirectedEdge(node1, node2));
    }

    /**
     * Adds a nondirected edge to the graph from node A to node B.
     *
     * @param node1 the "from" node.
     * @param node2 the "to" node.
     */
    public boolean addNondirectedEdge(final Node node1, final Node node2) {
        return addEdge(Edges.nondirectedEdge(node1, node2));
    }

    /**
     * Adds a partially oriented edge to the graph from node A to node B.
     *
     * @param node1 the "from" node.
     * @param node2 the "to" node.
     */
    public boolean addPartiallyOrientedEdge(final Node node1, final Node node2) {
        return addEdge(Edges.partiallyOrientedEdge(node1, node2));
    }

    /**
     * Adds a bidirected edge to the graph from node A to node B.
     *
     * @param node1 the "from" node.
     * @param node2 the "to" node.
     */
    public boolean addBidirectedEdge(final Node node1, final Node node2) {
        return addEdge(Edges.bidirectedEdge(node1, node2));
    }

    public boolean existsDirectedCycle() {
        for (final Node node : getNodes()) {
            if (existsDirectedPathFromTo(node, node)) {
                return true;
            }
        }
        return false;
    }

    public boolean isDirectedFromTo(final Node node1, final Node node2) {
        final List<Edge> edges = getEdges(node1, node2);
        if (edges.size() != 1) return false;
        final Edge edge = edges.get(0);
        return edge.pointsTowards(node2);
    }

    public boolean isUndirectedFromTo(final Node node1, final Node node2) {
        final Edge edge = getEdge(node1, node2);

        return edge != null && edge.getEndpoint1() == Endpoint.TAIL && edge.getEndpoint2() == Endpoint.TAIL;

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
    public boolean defVisible(final Edge edge) {
        if (containsEdge(edge)) {

            final Node A = Edges.getDirectedEdgeTail(edge);
            final Node B = Edges.getDirectedEdgeHead(edge);
            final List<Node> adjToA = getAdjacentNodes(A);

            while (!adjToA.isEmpty()) {
                final Node Curr = adjToA.remove(0);
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
    public boolean isDefNoncollider(final Node node1, final Node node2, final Node node3) {
        final List<Edge> edges = getEdges(node2);
        boolean circle12 = false;
        boolean circle32 = false;

        for (final Edge edge : edges) {
            final boolean _node1 = edge.getDistalNode(node2) == node1;
            final boolean _node3 = edge.getDistalNode(node2) == node3;

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

    public boolean isDefCollider(final Node node1, final Node node2, final Node node3) {
        final Edge edge1 = getEdge(node1, node2);
        final Edge edge2 = getEdge(node2, node3);

        if (edge1 == null) {
            throw new NullPointerException();
        }

        if (edge2 == null) {
            throw new NullPointerException();
        }

        return edge1.getProximalEndpoint(node2) == Endpoint.ARROW &&
                edge2.getProximalEndpoint(node2) == Endpoint.ARROW;
    }

    /**
     * @return true iff there is a directed path from node1 to node2.
     * a
     */
    public boolean existsDirectedPathFromTo(final Node node1, final Node node2) {
        return existsDirectedPathVisit(node1, node2, new LinkedList<Node>());
    }

    @Override
    public List<Node> findCycle() {
        throw new UnsupportedOperationException();
    }

    public boolean existsUndirectedPathFromTo(final Node node1, final Node node2) {
        return existsUndirectedPathVisit(node1, node2, new LinkedList<Node>());
    }

    public boolean existsSemiDirectedPathFromTo(final Node node1, final Set<Node> nodes) {
        return existsSemiDirectedPathVisit(node1, nodes,
                new LinkedList<Node>());
    }

    /**
     * Determines whether a trek exists between two nodes in the graph.  A trek
     * exists if there is a directed path between the two nodes or else, for
     * some third node in the graph, there is a path to each of the two nodes in
     * question.
     */
    public boolean existsTrek(final Node node1, final Node node2) {

        for (final Node node3 : getNodes()) {
            final Node node = (node3);

            if (isAncestorOf(node, node1) && isAncestorOf(node, node2)) {
                return true;
            }

        }

        return false;
    }

    /**
     * @return the list of children for a node.
     */
    public List<Node> getChildren(final Node node) {
        final int i = this.nodesHash.get(node);
        final List<Node> children = new ArrayList<>();

        for (int j = 0; j < this.nodes.size(); j++) {
            final int m1 = this.graphMatrix[j][i];
            final int m2 = this.graphMatrix[i][j];
            if (m1 == 1 && m2 == 2) {
                children.add(this.nodes.get(j));
            }
        }

        return children;
    }

    public int getConnectivity() {
        int connectivity = 0;

        final List<Node> nodes = getNodes();

        for (final Node node : nodes) {
            final int n = getNumEdges(node);
            if (n > connectivity) {
                connectivity = n;
            }
        }

        return connectivity;
    }

    public List<Node> getDescendants(final List<Node> nodes) {
        final HashSet<Node> descendants = new HashSet<>();

        for (final Object node1 : nodes) {
            final Node node = (Node) node1;
            collectDescendantsVisit(node, descendants);
        }

        return new LinkedList<>(descendants);
    }

    /**
     * @return the edge connecting node1 and node2, provided a unique such edge
     * exists.
     */
    public Edge getEdge(final Node node1, final Node node2) {
        final int i = this.nodesHash.get(node1);
        final int j = this.nodesHash.get(node2);

        final Endpoint e1 = this.shortsToEndpoints.get(this.graphMatrix[j][i]);
        final Endpoint e2 = this.shortsToEndpoints.get(this.graphMatrix[i][j]);

        if (e1 != null) {
            return new Edge(node1, node2, e1, e2);
        } else {
            return null;
        }
    }

    public Edge getDirectedEdge(final Node node1, final Node node2) {
        final List<Edge> edges = getEdges(node1, node2);

        if (edges == null) return null;

        if (edges.size() == 0) {
            return null;
        }

        for (final Edge edge : edges) {
            if (Edges.isDirectedEdge(edge) && edge.getProximalEndpoint(node2) == Endpoint.ARROW) {
                return edge;
            }
        }

        return null;
    }

    /**
     * @return the list of parents for a node.
     */
    public List<Node> getParents(final Node node) {
        final int j = this.nodesHash.get(node);
        final List<Node> parents = new ArrayList<>();

        for (int i = 0; i < this.nodes.size(); i++) {
            final int m1 = this.graphMatrix[j][i];
            final int m2 = this.graphMatrix[i][j];
            if (m1 == 1 && m2 == 2) {
                parents.add(this.nodes.get(i));
            }
        }

        return parents;
    }

    /**
     * @return the number of edges into the given node.
     */
    public int getIndegree(final Node node) {
        return getParents(node).size();
    }

    @Override
    public int getDegree(final Node node) {
        return 0;
    }

    /**
     * @return the number of edges out of the given node.
     */
    public int getOutdegree(final Node node) {
        return getChildren(node).size();
    }

    /**
     * Determines whether some edge or other exists between two nodes.
     */
    public boolean isAdjacentTo(final Node node1, final Node node2) {
        final int i = this.nodesHash.get(node1);
        final int j = this.nodesHash.get(node2);

        return this.graphMatrix[i][j] != 0;
    }

    /**
     * Determines whether one node is an ancestor of another.
     */
    public boolean isAncestorOf(final Node node1, final Node node2) {
        return (node1 == node2) || isProperAncestorOf(node1, node2);
    }

    public boolean possibleAncestor(final Node node1, final Node node2) {
        return existsSemiDirectedPathFromTo(node1,
                Collections.singleton(node2));
    }

    /**
     * @return true iff node1 is a possible ancestor of at least one member of
     * nodes2
     */
    private boolean possibleAncestorSet(final Node node1, final List<Node> nodes2) {
        for (final Object aNodes2 : nodes2) {
            if (possibleAncestor(node1, (Node) aNodes2)) {
                return true;
            }
        }
        return false;
    }

    public List<Node> getAncestors(final List<Node> nodes) {
        final HashSet<Node> ancestors = new HashSet<>();

        for (final Object node1 : nodes) {
            final Node node = (Node) node1;
            collectAncestorsVisit(node, ancestors);
        }

        return new ArrayList<>(ancestors);
    }

    /**
     * Determines whether one node is a child of another.
     */
    public boolean isChildOf(final Node node1, final Node node2) {
        for (final Object o : getEdges(node2)) {
            final Edge edge = (Edge) (o);
            final Node sub = Edges.traverseDirected(node2, edge);

            if (sub == node1) {
                return true;
            }
        }
        return false;
    }

    /**
     * Determines whether one node is a descendent of another.
     */
    public boolean isDescendentOf(final Node node1, final Node node2) {
        return (node1 == node2) || isProperDescendentOf(node1, node2);
    }

    /**
     * added by ekorber, 2004/06/12
     *
     * @return true iff node2 is a definite nondecendent of node1
     */
    public boolean defNonDescendent(final Node node1, final Node node2) {
        return !(possibleAncestor(node1, node2));
    }

    // Assume acyclicity.
    public boolean isDConnectedTo(final Node x, final Node y, final List<Node> z) {
        final Set<Node> zAncestors = zAncestors2(z);

        final Queue<Pair> Q = new ArrayDeque<>();
        final Set<Pair> V = new HashSet<>();

        for (final Node node : getAdjacentNodes(x)) {
            if (node == y) return true;
            final Pair edge = new Pair(x, node);
            Q.offer(edge);
            V.add(edge);
        }

        while (!Q.isEmpty()) {
            final Pair t = Q.poll();

            final Node b = t.getY();
            final Node a = t.getX();

            for (final Node c : getAdjacentNodes(b)) {
                if (c == a) continue;

                final boolean collider = isDefCollider(a, b, c);
                if (!((collider && zAncestors.contains(b)) || (!collider && !z.contains(b)))) continue;

                if (c == y) return true;

                final Pair u = new Pair(b, c);
                if (V.contains(u)) continue;

                V.add(u);
                Q.offer(u);
            }
        }

        return false;
    }

    private boolean isDConnectedTo(final List<Node> x, final List<Node> y, final List<Node> z) {
        final Set<Node> zAncestors = zAncestors2(z);

        final Queue<Pair> Q = new ArrayDeque<>();
        final Set<Pair> V = new HashSet<>();

        for (final Node _x : x) {
            for (final Node node : getAdjacentNodes(_x)) {
//                if (node == y) return true;
                if (y.contains(node)) return true;
                final Pair edge = new Pair(_x, node);
//                System.out.println("Edge " + edge);
                Q.offer(edge);
                V.add(edge);
            }
        }

        while (!Q.isEmpty()) {
            final Pair t = Q.poll();

            final Node b = t.getY();
            final Node a = t.getX();

            for (final Node c : getAdjacentNodes(b)) {
                if (c == a) continue;

                final boolean collider = isDefCollider(a, b, c);
                if (!((collider && zAncestors.contains(b)) || (!collider && !z.contains(b)))) continue;

//                if (c == y) return true;
                if (y.contains(c)) return true;

                final Pair u = new Pair(b, c);
                if (V.contains(u)) continue;

//                System.out.println("u = " + u);

                V.add(u);
                Q.offer(u);
            }
        }

        return false;
    }

    public boolean isDSeparatedFrom(final List<Node> x, final List<Node> y, final List<Node> z) {
        return !isDConnectedTo(x, y, z);
    }

    @Override
    public List<String> getTriplesClassificationTypes() {
        return null;
    }

    @Override
    public List<List<Triple>> getTriplesLists(final Node node) {
        return null;
    }

    @Override
    public boolean isPag() {
        return this.pag;
    }

    @Override
    public void setPag(final boolean pag) {
        this.pag = pag;
    }

    @Override
    public boolean isCPDAG() {
        return this.CPDAG;
    }

    @Override
    public void setCPDAG(final boolean CPDAG) {
        this.CPDAG = CPDAG;
    }

    private static class Pair {
        private final Node x;
        private final Node y;

        public Pair(final Node x, final Node y) {
            this.x = x;
            this.y = y;
        }

        public Node getX() {
            return this.x;
        }

        public Node getY() {
            return this.y;
        }

        public int hashCode() {
            return this.x.hashCode() + 17 * this.y.hashCode();
        }

        public boolean equals(final Object o) {
            if (o == this) return true;
            if (!(o instanceof Pair)) return false;
            final Pair pair = (Pair) o;
            return this.x == pair.getX() && this.y == pair.getY();
        }

        public String toString() {
            return "(" + this.x.toString() + ", " + this.y.toString() + ")";
        }
    }

    private Set<Node> zAncestors2(final List<Node> z) {
        final Queue<Node> Q = new ArrayDeque<>();
        final Set<Node> V = new HashSet<>();

        for (final Node node : z) {
            Q.offer(node);
            V.add(node);
        }

        while (!Q.isEmpty()) {
            final Node t = Q.poll();

            for (final Node c : getParents(t)) {
                if (V.contains(c)) continue;
                V.add(c);
                Q.offer(c);
            }
        }

        return V;
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

    public boolean isDSeparatedFrom(final Node node1, final Node node2, final List<Node> z) {
        return !isDConnectedTo(node1, node2, z);
    }

    //added by ekorber, June 2004
    public boolean possDConnectedTo(final Node node1, final Node node2,
                                    final List<Node> condNodes) {
        final LinkedList<Node> allNodes = new LinkedList<>(getNodes());
        final int sz = allNodes.size();
        final int[][] edgeStage = new int[sz][sz];
        int stage = 1;

        final int n1x = allNodes.indexOf(node1);
        final int n2x = allNodes.indexOf(node2);

        edgeStage[n1x][n1x] = 1;
        edgeStage[n2x][n2x] = 1;

        List<int[]> currEdges;
        List<int[]> nextEdges = new LinkedList<>();

        final int[] temp1 = new int[2];
        temp1[0] = n1x;
        temp1[1] = n1x;
        nextEdges.add(temp1);

        final int[] temp2 = new int[2];
        temp2[0] = n2x;
        temp2[1] = n2x;
        nextEdges.add(temp2);

        while (true) {
            currEdges = nextEdges;
            nextEdges = new LinkedList<>();
            for (final int[] edge : currEdges) {
                final Node center = allNodes.get(edge[1]);
                final List<Node> adj = new LinkedList<>(getAdjacentNodes(center));

                for (final Node anAdj : adj) {
                    // check if we've hit this edge before
                    final int testIndex = allNodes.indexOf(anAdj);
                    if (edgeStage[edge[1]][testIndex] != 0) {
                        continue;
                    }

                    // if the edge pair violates possible d-connection,
                    // then go to the next adjacent node.

                    final Node X = allNodes.get(edge[0]);
                    final Node Y = allNodes.get(edge[1]);
                    final Node Z = allNodes.get(testIndex);

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
                    final int[] nextEdge = new int[2];
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
    public boolean existsInducingPath(final Node node1, final Node node2) {
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
    public boolean isParentOf(final Node node1, final Node node2) {
        for (final Edge edge1 : getEdges(node1)) {
            final Edge edge = (edge1);
            final Node sub = Edges.traverseDirected(node1, edge);

            if (sub == node2) {
                return true;
            }
        }

        return false;
    }

    /**
     * Determines whether one node is a proper ancestor of another.
     */
    public boolean isProperAncestorOf(final Node node1, final Node node2) {
        return existsDirectedPathFromTo(node1, node2);
    }

    /**
     * Determines whether one node is a proper decendent of another
     */
    public boolean isProperDescendentOf(final Node node1, final Node node2) {
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
    public void transferNodesAndEdges(final Graph graph)
            throws IllegalArgumentException {
        if (graph == null) {
            throw new NullPointerException("No graph was provided.");
        }

//        System.out.println("TANSFER BEFORE " + graph.getEdges());

        for (final Node node : graph.getNodes()) {

            node.getAllAttributes().clear();

            if (!addNode(node)) {
                throw new IllegalArgumentException();
            }
        }

        for (final Edge edge : graph.getEdges()) {
            if (!addEdge(edge)) {
                throw new IllegalArgumentException();
            }
        }

//        System.out.println("TANSFER AFTER " + getEdges());
    }

    public void transferAttributes(final Graph graph)
            throws IllegalArgumentException {
        if (graph == null) {
            throw new NullPointerException("No graph was provided.");
        }
        this.attributes.putAll(graph.getAllAttributes());
    }

    /**
     * Determines whether a node in a graph is exogenous.
     */
    public boolean isExogenous(final Node node) {
        return getIndegree(node) == 0;
    }

    /**
     * @return the set of nodes adjacent to the given node. If there are multiple edges between X and Y, Y will show
     * up twice in the list of adjacencies for X, for optimality; simply create a list an and array from these to
     * eliminate the duplication.
     */
    public List<Node> getAdjacentNodes(final Node node) {
        final int j = this.nodesHash.get(node);
        final List<Node> adj = new ArrayList<>();

        for (int i = 0; i < this.nodes.size(); i++) {
            if (this.graphMatrix[i][j] != (short) 0) {
                adj.add(this.nodes.get(i));
            }
        }

        return adj;
    }

    /**
     * Removes the edge connecting the two given nodes.
     */
    public boolean removeEdge(final Node node1, final Node node2) {
        final List<Edge> edges = getEdges(node1, node2);

        if (edges.size() > 1) {
            throw new IllegalStateException(
                    "There is more than one edge between " + node1 + " and " +
                            node2);
        }

        this.numEdges--;

        return removeEdges(edges);
    }

    /**
     * @return the endpoint along the edge from node to node2 at the node2 end.
     */
    public Endpoint getEndpoint(final Node node1, final Node node2) {
        final List<Edge> edges = getEdges(node2);

        for (final Edge edge : edges) {
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
    public boolean setEndpoint(final Node from, final Node to, final Endpoint endPoint)
            throws IllegalArgumentException {
        final List<Edge> edges = getEdges(from, to);

        if (endPoint == null) {
            throw new NullPointerException();
        } else if (edges.size() == 0) {
//            removeEdge(from, to);
            addEdge(new Edge(from, to, Endpoint.TAIL, endPoint));
            return true;
        } else if (edges.size() == 1) {
            final Edge edge = edges.get(0);
            final Edge newEdge = new Edge(from, to, edge.getProximalEndpoint(from), endPoint);

            try {
                removeEdge(edge);
                addEdge(newEdge);
                return true;
            } catch (final IllegalArgumentException e) {
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
    public List<Node> getNodesInTo(final Node node, final Endpoint endpoint) {
        final List<Node> nodes = new ArrayList<>(4);
        final List<Edge> edges = getEdges(node);

        for (final Object edge1 : edges) {
            final Edge edge = (Edge) edge1;

            if (edge.getProximalEndpoint(node) == endpoint) {
                nodes.add(edge.getDistalNode(node));
            }
        }

        return nodes;
    }

    /**
     * Nodes adjacent to the given node with the given distal endpoint.
     */
    public List<Node> getNodesOutTo(final Node node, final Endpoint endpoint) {
        final List<Node> nodes = new ArrayList<>(4);
        final List<Edge> edges = getEdges(node);

        for (final Object edge1 : edges) {
            final Edge edge = (Edge) edge1;

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
        final int size = this.nodes.size();
        final Endpoint[][] endpoints = new Endpoint[size][size];

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (i == j) {
                    continue;
                }

                final Node nodei = this.nodes.get(i);
                final Node nodej = this.nodes.get(j);

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
    public boolean addEdge(final Edge edge) {
        final int i = this.nodesHash.get(edge.getNode1());
        final int j = this.nodesHash.get(edge.getNode2());

        if (this.graphMatrix[i][j] != 0) {
            return false;
        }

        final short e1 = this.endpointsToShorts.get(edge.getEndpoint1());
        final short e2 = this.endpointsToShorts.get(edge.getEndpoint2());

        this.graphMatrix[j][i] = e1;
        this.graphMatrix[i][j] = e2;

        this.numEdges++;

        return true;
    }

    /**
     * Throws unsupported operation exception.
     */
    public void addPropertyChangeListener(final PropertyChangeListener l) {
        throw new UnsupportedOperationException();
    }

    /**
     * Adds a node to the graph. Precondition: The proposed name of the node
     * cannot already be used by any other node in the same graph.
     *
     * @param node the node to be added.
     * @return true if the the node was added, false if not.
     */
    public boolean addNode(final Node node) {
        if (node == null) {
            throw new NullPointerException();
        }

        if (!(getNode(node.getName()) == null)) {
            return false;

            // This is problematic for the sem updater. jdramsey 7/23/2005
//            throw new IllegalArgumentException("A node by name " +
//                    node.getNode() + " has already been added to the graph.");
        }

        if (this.nodes.contains(node)) {
            return false;
        }

        final List<Node> _nodes = new ArrayList<>();
        this.nodes.add(node);
        this.namesHash.put(node.getName(), node);

        reconstituteGraphMatrix(_nodes, this.nodes);

        initHashes();

        return true;
    }

    private void reconstituteGraphMatrix(final List<Node> nodes, final List<Node> nodes1) {
        final short[][] newGraphMatrix = new short[nodes1.size()][nodes1.size()];

        for (int i = 0; i < nodes1.size(); i++) {
            for (int j = 0; j < nodes1.size(); j++) {
                final int i1 = nodes.indexOf(nodes1.get(i));
                final int j1 = nodes.indexOf(nodes1.get(i));

                if (i1 != -1 && j1 != -1)
                    newGraphMatrix[i][j] = this.graphMatrix[i1][j1];
            }
        }

        this.graphMatrix = newGraphMatrix;
    }

    /**
     * @return the list of edges in the graph.  No particular ordering of the
     * edges in the list is guaranteed.
     */
    public Set<Edge> getEdges() {
        final HashSet<Edge> edges = new HashSet<>();

        for (int i = 0; i < this.nodes.size(); i++) {
            for (int j = i + 1; j < this.nodes.size(); j++) {
                final Edge edge = getEdge(this.nodes.get(i), this.nodes.get(j));

                if (edge != null) {
                    edges.add(edge);
                }
            }
        }

        return edges;
    }

    /**
     * Determines if the graph contains a particular edge.
     */
    public boolean containsEdge(final Edge edge) {
        final int i = this.nodesHash.get(edge.getNode1());
        final int j = this.nodesHash.get(edge.getNode2());

        return this.graphMatrix[i][j] != 0;
    }

    /**
     * Determines whether the graph contains a particular node.
     */
    public boolean containsNode(final Node node) {
        return this.nodes.contains(node);
    }

    /**
     * @return the list of edges connected to a particular node. No particular
     * ordering of the edges in the list is guaranteed.
     */
    public List<Edge> getEdges(final Node node) {
        final List<Node> adj = getAdjacentNodes(node);

        final List<Edge> edges = new ArrayList<>();

        for (final Node _node : adj) {
            edges.add(getEdge(node, _node));
        }

        return edges;
    }

    public int hashCode() {
        int hashCode = 0;
        int sum = 0;

        for (final Node node : getNodes()) {
            sum += node.hashCode();
        }

        hashCode += 23 * sum;
        sum = 0;

        for (final Edge edge : getEdges()) {
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
    public boolean equals(final Object o) {
        if (!(o instanceof EndpointMatrixGraph)) {
            return false;
        }

        final EndpointMatrixGraph graph = (EndpointMatrixGraph) o;

        if (!graph.nodes.equals(this.nodes)) return false;

        for (int i = 0; i < this.nodes.size(); i++) {
            for (int j = 0; j < this.nodes.size(); j++) {
                if (graph.graphMatrix[i][j] != this.graphMatrix[i][j]) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Resets the graph so that it is fully connects it using #-# edges, where #
     * is the given endpoint.
     */
    public void fullyConnect(final Endpoint endpoint) {
        final short s = this.endpointsToShorts.get(endpoint);

        for (int i = 0; i < this.nodes.size(); i++) {
            for (int j = 0; j < this.nodes.size(); j++) {
                this.graphMatrix[i][j] = s;
            }
        }
    }

    public void reorientAllWith(final Endpoint endpoint) {
        final short s = this.endpointsToShorts.get(endpoint);

        for (int i = 0; i < this.nodes.size(); i++) {
            for (int j = 0; j < this.nodes.size(); j++) {
                if (i == j) continue;
                if (this.graphMatrix[i][j] != 0) {
                    this.graphMatrix[i][j] = s;
                }
            }
        }
    }

    /**
     * @return the node with the given name, or null if no such node exists.
     */
    public Node getNode(final String name) {
        Node node = this.namesHash.get(name);

        if (node == null /*|| !name.equals(node.getNode())*/) {
            this.namesHash = new HashMap<>();

            for (final Node _node : this.nodes) {
                this.namesHash.put(_node.getName(), _node);
            }

            node = this.namesHash.get(name);
        }

        return node;

//        for (Node node : nodes) {
//            if (node.getNode().equals(name)) {
//                return node;
//            }
//        }
//
//        return namesHash.get(name);

//        return null;
    }

    /**
     * @return the number of nodes in the graph.
     */
    public int getNumNodes() {
        return this.nodes.size();
    }

    /**
     * @return the number of edges in the (entire) graph.
     */
    public int getNumEdges() {
        return this.numEdges;
    }

    /**
     * @return the number of edges connected to a particular node in the graph.
     */
    public int getNumEdges(final Node node) {
        return getEdges(node).size();
    }

    public List<Node> getNodes() {
        return new ArrayList<>(this.nodes);
    }

    /**
     * Removes all nodes (and therefore all edges) from the graph.
     */
    public void clear() {
        for (int i = 0; i < this.nodes.size(); i++) {
            for (int j = 0; j < this.nodes.size(); j++) {
                this.graphMatrix[i][j] = 0;
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
    public boolean removeEdge(final Edge edge) {
        final int i = this.nodesHash.get(edge.getNode1());
        final int j = this.nodesHash.get(edge.getNode2());

        this.graphMatrix[i][j] = 0;
        this.graphMatrix[j][i] = 0;

        return true;
    }

    /**
     * Removes any relevant edge objects found in this collection. G
     *
     * @param edges the collection of edges to remove.
     * @return true if any edges in the collection were removed, false if not.
     */
    public boolean removeEdges(final Collection<Edge> edges) {
        boolean change = false;

        for (final Edge edge : edges) {
            final boolean _change = removeEdge(edge);
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
    public boolean removeEdges(final Node node1, final Node node2) {
        return removeEdges(getEdges(node1, node2));
    }

    /**
     * Removes a node from the graph.
     */
    public boolean removeNode(final Node node) {
        if (this.nodes.contains(node)) {
            return false;
        }

        final List<Node> _nodes = new ArrayList<>(this.nodes);
        this.nodes.remove(node);
        this.namesHash.remove(node.getName());

        reconstituteGraphMatrix(_nodes, this.nodes);

        initHashes();

        this.stuffRemovedSinceLastTripleAccess = true;

        return true;
    }

    /**
     * Removes any relevant node objects found in this collection.
     *
     * @param newNodes the collection of nodes to remove.
     * @return true if nodes from the collection were removed, false if not.
     */
    public boolean removeNodes(final List<Node> newNodes) {
        boolean changed = false;

        for (final Object newNode : newNodes) {
            final boolean _changed = removeNode((Node) newNode);
            changed = changed || _changed;
        }

        return changed;
    }

    /**
     * @return a string representation of the graph.
     */
    public String toString() {
        final StringBuilder buf = new StringBuilder();

        buf.append("\nGraph Nodes:\n");

        for (int i = 0; i < this.nodes.size(); i++) {
//            buf.append("\n" + (i + 1) + ". " + nodes.get(i));
            buf.append(this.nodes.get(i)).append(" ");
            if ((i + 1) % 30 == 0) buf.append("\n");
        }

        buf.append("\n\nGraph Edges: ");

        final List<Edge> edges = new ArrayList<>(getEdges());
        Edges.sortEdges(edges);

        for (int i = 0; i < edges.size(); i++) {
            final Edge edge = edges.get(i);
            buf.append("\n").append(i + 1).append(". ").append(edge);
        }

        buf.append("\n");
        buf.append("\n");

//        Set<Triple> ambiguousTriples = getAmbiguousTriples();

        if (!this.ambiguousTriples.isEmpty()) {
            buf.append("Ambiguous triples (i.e. list of triples for which there is ambiguous data" +
                    "\nabout whether they are colliders or not): \n");

            for (final Triple triple : this.ambiguousTriples) {
                buf.append(triple).append("\n");
            }
        }

        if (!this.underLineTriples.isEmpty()) {
            buf.append("Underline triples: \n");

            for (final Triple triple : this.underLineTriples) {
                buf.append(triple).append("\n");
            }
        }

        if (!this.dottedUnderLineTriples.isEmpty()) {
            buf.append("Dotted underline triples: \n");

            for (final Triple triple : this.dottedUnderLineTriples) {
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

    public Graph subgraph(final List<Node> nodes) {
        final Graph graph = new EndpointMatrixGraph(nodes);
        final Set<Edge> edges = getEdges();

        for (final Object edge1 : edges) {
            final Edge edge = (Edge) edge1;

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
    public List<Edge> getEdges(final Node node1, final Node node2) {
        final List<Edge> edges = getEdges(node1);
        final List<Edge> _edges = new ArrayList<>();

        for (final Edge edge : edges) {
            if (edge.getDistalNode(node1) == node2) {
                _edges.add(edge);
            }
        }

        return _edges;
    }

    public Set<Triple> getAmbiguousTriples() {
        removeTriplesNotInGraph();
        return new HashSet<>(this.ambiguousTriples);
    }

    public Set<Triple> getUnderLines() {
        removeTriplesNotInGraph();
        return new HashSet<>(this.underLineTriples);
    }

    public Set<Triple> getDottedUnderlines() {
        removeTriplesNotInGraph();
        return new HashSet<>(this.dottedUnderLineTriples);
    }


    /**
     * States whether r-s-r is an underline triple or not.
     */
    public boolean isAmbiguousTriple(final Node x, final Node y, final Node z) {
        final Triple triple = new Triple(x, y, z);
        if (!triple.alongPathIn(this)) {
            throw new IllegalArgumentException("<" + x + ", " + y + ", " + z + "> is not along a path.");
        }
        removeTriplesNotInGraph();
        return this.ambiguousTriples.contains(triple);
    }

    /**
     * States whether r-s-r is an underline triple or not.
     */
    public boolean isUnderlineTriple(final Node x, final Node y, final Node z) {
        removeTriplesNotInGraph();
        return this.underLineTriples.contains(new Triple(x, y, z));
    }

    /**
     * States whether r-s-r is an underline triple or not.
     */
    public boolean isDottedUnderlineTriple(final Node x, final Node y, final Node z) {
        final Triple triple = new Triple(x, y, z);
        if (!triple.alongPathIn(this)) {
            throw new IllegalArgumentException("<" + x + ", " + y + ", " + z + "> is not along a path.");
        }
        removeTriplesNotInGraph();
        return this.dottedUnderLineTriples.contains(new Triple(x, y, z));
    }

    public void addAmbiguousTriple(final Node x, final Node y, final Node z) {
        final Triple triple = new Triple(x, y, z);

        if (!triple.alongPathIn(this)) {
            throw new IllegalArgumentException("<" + x + ", " + y + ", " + z + "> must lie along a path in the graph.");
        }

        this.ambiguousTriples.add(new Triple(x, y, z));
    }

    public void addUnderlineTriple(final Node x, final Node y, final Node z) {
        final Triple triple = new Triple(x, y, z);

        if (!triple.alongPathIn(this)) {
            throw new IllegalArgumentException("<" + x + ", " + y + ", " + z + "> must lie along a path in the graph.");
        }

        this.underLineTriples.add(new Triple(x, y, z));
    }

    public void addDottedUnderlineTriple(final Node x, final Node y, final Node z) {
        final Triple triple = new Triple(x, y, z);

        if (!triple.alongPathIn(this)) {
            throw new IllegalArgumentException("<" + x + ", " + y + ", " + z + "> must lie along a path in the graph.");
        }

        this.dottedUnderLineTriples.add(triple);
    }

    public void removeAmbiguousTriple(final Node x, final Node y, final Node z) {
        this.ambiguousTriples.remove(new Triple(x, y, z));
    }

    public void removeUnderlineTriple(final Node x, final Node y, final Node z) {
        this.underLineTriples.remove(new Triple(x, y, z));
    }

    public void removeDottedUnderlineTriple(final Node x, final Node y, final Node z) {
        this.dottedUnderLineTriples.remove(new Triple(x, y, z));
    }


    public void setAmbiguousTriples(final Set<Triple> triples) {
        this.ambiguousTriples.clear();

        for (final Triple triple : triples) {
            addAmbiguousTriple(triple.getX(), triple.getY(), triple.getZ());
        }
    }

    public void setUnderLineTriples(final Set<Triple> triples) {
        this.underLineTriples.clear();

        for (final Triple triple : triples) {
            addUnderlineTriple(triple.getX(), triple.getY(), triple.getZ());
        }
    }


    public void setDottedUnderLineTriples(final Set<Triple> triples) {
        this.dottedUnderLineTriples.clear();

        for (final Triple triple : triples) {
            addDottedUnderlineTriple(triple.getX(), triple.getY(), triple.getZ());
        }
    }

    public List<String> getNodeNames() {
        final List<String> names = new ArrayList<>();

        for (final Node node : getNodes()) {
            names.add(node.getName());
        }

        return names;
    }


    //===============================PRIVATE METHODS======================//

    public void removeTriplesNotInGraph() {
        if (!this.stuffRemovedSinceLastTripleAccess) return;

        for (final Triple triple : new HashSet<>(this.ambiguousTriples)) {
            if (!containsNode(triple.getX()) || !containsNode(triple.getY()) || !containsNode(triple.getZ())) {
                this.ambiguousTriples.remove(triple);
                continue;
            }

            if (!isAdjacentTo(triple.getX(), triple.getY()) || !isAdjacentTo(triple.getY(), triple.getZ())) {
                this.ambiguousTriples.remove(triple);
            }
        }

        for (final Triple triple : new HashSet<>(this.underLineTriples)) {
            if (!containsNode(triple.getX()) || !containsNode(triple.getY()) || !containsNode(triple.getZ())) {
                this.underLineTriples.remove(triple);
                continue;
            }

            if (!isAdjacentTo(triple.getX(), triple.getY()) || !isAdjacentTo(triple.getY(), triple.getZ())) {
                this.underLineTriples.remove(triple);
            }
        }

        for (final Triple triple : new HashSet<>(this.dottedUnderLineTriples)) {
            if (!containsNode(triple.getX()) || !containsNode(triple.getY()) || !containsNode(triple.getZ())) {
                this.dottedUnderLineTriples.remove(triple);
                continue;
            }

            if (!isAdjacentTo(triple.getX(), triple.getY()) || !isAdjacentTo(triple.getY(), triple.getZ())) {
                this.dottedUnderLineTriples.remove(triple);
            }
        }

        this.stuffRemovedSinceLastTripleAccess = false;
    }

    @Override
    public List<Node> getSepset(final Node n1, final Node n2) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setNodes(final List<Node> nodes) {
        if (nodes.size() != this.nodes.size()) {
            throw new IllegalArgumentException("Sorry, there is a mismatch in the number of variables " +
                    "you are trying to set.");
        }

        this.nodes = nodes;
    }


    private void collectAncestorsVisit(final Node node, final Set<Node> ancestors) {
        ancestors.add(node);
        final List<Node> parents = getParents(node);

        if (!parents.isEmpty()) {
            for (final Object parent1 : parents) {
                final Node parent = (Node) parent1;
                doParentClosureVisit(parent, ancestors);
            }
        }
    }

    private void collectDescendantsVisit(final Node node, final Set<Node> descendants) {
        descendants.add(node);
        final List<Node> children = getChildren(node);

        if (!children.isEmpty()) {
            for (final Object aChildren : children) {
                final Node child = (Node) aChildren;
                doChildClosureVisit(child, descendants);
            }
        }
    }

    /**
     * closure under the child relation
     */
    private void doChildClosureVisit(final Node node, final Set<Node> closure) {
        if (!closure.contains(node)) {
            closure.add(node);

            for (final Edge edge1 : getEdges(node)) {
                final Node sub = Edges.traverseDirected(node, edge1);

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
    private void doParentClosureVisit(final Node node, final Set<Node> closure) {
        if (closure.contains(node)) return;
        closure.add(node);

        for (final Edge edge : getEdges(node)) {
            final Node sub = Edges.traverseReverseDirected(node, edge);
            if (sub != null) {
                doParentClosureVisit(sub, closure);
            }
        }
    }

    /**
     * @return true iff there is a directed path from node1 to node2.
     */
    private boolean existsUndirectedPathVisit(final Node node1, final Node node2,
                                              final LinkedList<Node> path) {
        path.addLast(node1);

        for (final Edge edge : getEdges(node1)) {
            final Node child = Edges.traverse(node1, edge);

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

    private boolean existsDirectedPathVisit(final Node node1, final Node node2,
                                            final LinkedList<Node> path) {
        path.addLast(node1);

        for (final Edge edge : getEdges(node1)) {
            final Node child = Edges.traverseDirected(node1, edge);

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
    private boolean existsSemiDirectedPathVisit(final Node node1, final Set<Node> nodes2,
                                                final LinkedList<Node> path) {
        path.addLast(node1);

        for (final Edge edge : getEdges(node1)) {
            final Node child = Edges.traverseSemiDirected(node1, edge);

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

    public void setHighlighted(final Edge edge, final boolean highlighted) {
        this.highlightedEdges.add(edge);
    }

    public boolean isHighlighted(final Edge edge) {
        return this.highlightedEdges.contains(edge);
    }

    public boolean isParameterizable(final Node node) {
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
    private void readObject(final ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (this.nodes == null) {
            throw new NullPointerException();
        }

        if (this.ambiguousTriples == null) {
            this.ambiguousTriples = new HashSet<>();
        }

        if (this.highlightedEdges == null) {
            this.highlightedEdges = new HashSet<>();
        }

        if (this.underLineTriples == null) {
            this.underLineTriples = new HashSet<>();
        }

        if (this.dottedUnderLineTriples == null) {
            this.dottedUnderLineTriples = new HashSet<>();
        }
    }

    @Override
    public Map<String, Object> getAllAttributes() {
        return this.attributes;
    }

    @Override
    public Object getAttribute(final String key) {
        return this.attributes.get(key);
    }

    @Override
    public void removeAttribute(final String key) {
        this.attributes.remove(key);
    }

    @Override
    public void addAttribute(final String key, final Object value) {
        this.attributes.put(key, value);
    }

}




