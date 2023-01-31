package edu.cmu.tetrad.graph;

import edu.cmu.tetrad.util.TetradSerializable;

import java.util.*;

public class Paths implements TetradSerializable {
    static final long serialVersionUID = 23L;

    Graph graph;

    public Paths(Graph graph) {
        if (graph == null) throw new NullPointerException("Null graph");
        this.graph = graph;
    }

    /**
     * added by ekorber, 2004/06/11
     *
     * @return true if the given edge is definitely visible (Jiji, pg 25)
     * @throws IllegalArgumentException if the given edge is not a directed edge
     *                                  in the graph
     */
    public boolean defVisible(Edge edge) {
        if (!edge.isDirected()) return false;

        if (graph.containsEdge(edge)) {

            Node A = Edges.getDirectedEdgeTail(edge);
            Node B = Edges.getDirectedEdgeHead(edge);

            for (Node C : graph.getAdjacentNodes(A)) {
                if (C != B && !graph.isAdjacentTo(C, B)) {
                    Edge e = graph.getEdge(C, A);

                    if (e.getProximalEndpoint(A) == Endpoint.ARROW) {
                        return true;
                    }
                }
            }

            return visibleEdgeHelper(A, B, graph);
        } else {
            throw new IllegalArgumentException(
                    "Given edge is not in the graph.");
        }
    }

    private static boolean visibleEdgeHelper(Node A, Node B, Graph graph) {
        if (A.getNodeType() != NodeType.MEASURED) {
            return false;
        }
        if (B.getNodeType() != NodeType.MEASURED) {
            return false;
        }

        LinkedList<Node> path = new LinkedList<>();
        path.add(A);

        for (Node C : graph.getNodesInTo(A, Endpoint.ARROW)) {
            if (graph.isParentOf(C, A)) {
                return true;
            }

            if (visibleEdgeHelperVisit(graph, C, A, B, path)) {
                return true;
            }
        }

        return false;
    }

    private static boolean visibleEdgeHelperVisit(Graph graph, Node c, Node a, Node b,
                                                  LinkedList<Node> path) {
        if (path.contains(a)) {
            return false;
        }

        path.addLast(a);

        if (a == b) {
            return true;
        }

        for (Node D : graph.getNodesInTo(a, Endpoint.ARROW)) {
            if (graph.isParentOf(D, c)) {
                return true;
            }

            if (a.getNodeType() == NodeType.MEASURED) {
                if (!graph.isDefCollider(D, c, a)) {
                    continue;
                }
            }

            if (graph.isDefCollider(D, c, a)) {
                if (!graph.isParentOf(c, b)) {
                    continue;
                }
            }

            if (visibleEdgeHelperVisit(graph, D, c, b, path)) {
                return true;
            }
        }

        path.removeLast();
        return false;
    }

    public boolean existsDirectedCycle() {
        for (Node node : graph.getNodes()) {
            if (existsDirectedPathFromTo(node, node)) return true;
        }
        return false;
    }

    /**
     * @return true iff there is a (nonempty) directed path from node1 to node2. a
     */
    public boolean existsDirectedPathFromTo(Node node1, Node node2) {
        Queue<Node> Q = new LinkedList<>();
        Set<Node> V = new HashSet<>();

        Q.add(node1);
        V.add(node1);

        while (!Q.isEmpty()) {
            Node t = Q.poll();

            for (Node c : graph.getChildren(t)) {
                if (c == node2) return true;

                if (!V.contains(c)) {
                    V.add(c);
                    Q.offer(c);
                }
            }
        }

        return false;
    }

    public List<Node> findDirectedPath(Node from, Node to) {
        LinkedList<Node> path = new LinkedList<>();

        for (Node d : graph.getChildren(from)) {
            if (findDirectedPathVisit(from, d, to, path)) {
                path.addFirst(from);
                return path;
            }
        }

        return path;
    }

    private boolean findDirectedPathVisit(Node prev, Node next, Node to, LinkedList<Node> path) {
        if (path.contains(next)) return false;
        path.addLast(next);
        if (!graph.getEdge(prev, next).pointsTowards(next)) throw new IllegalArgumentException();
        if (next == to) return true;

        for (Node d : graph.getChildren(next)) {
            if (findDirectedPathVisit(next, d, to, path)) return true;
        }

        path.removeLast();
        return false;
    }

    public boolean existsUndirectedPathFromTo(Node node1, Node node2) {
        return existsUndirectedPathVisit(node1, node2, new HashSet<>());
    }

    public boolean existsSemiDirectedPathFromTo(Node node1, Node node2) {
        return existsSemiDirectedPathFromTo(node1, Collections.singleton(node2));
    }

    public boolean existsSemiDirectedPathFromTo(Node node1, Set<Node> nodes) {
        return existsSemiDirectedPathVisit(node1, nodes,
                new LinkedList<>());
    }

    /**
     * Determines whether a trek exists between two nodes in the graph. A trek
     * exists if there is a directed path between the two nodes or else, for
     * some third node in the graph, there is a path to each of the two nodes in
     * question.
     */
    public boolean existsTrek(Node node1, Node node2) {
        for (Node node : graph.getNodes()) {
            if (isAncestorOf((node), node1) && isAncestorOf((node), node2)) {
                return true;
            }
        }

        return false;
    }

    public List<Node> getDescendants(List<Node> nodes) {
        Set<Node> ancestors = new HashSet<>();

        for (Node n : graph.getNodes()) {
            for (Node m : nodes) {
                if (isDescendentOf(n, m)) {
                    ancestors.add(n);
                }
            }
        }

        return new ArrayList<>(ancestors);
    }



    /**
     * Determines whether one node is an ancestor of another.
     */
    public boolean isAncestorOf(Node node1, Node node2) {
        return node1 == node2 || existsDirectedPathFromTo(node1, node2);
    }

    /**
     * @return true iff node1 is a possible ancestor of at least one member of
     * nodes2
     */
    protected boolean possibleAncestorSet(Node node1, List<Node> nodes2) {
        for (Node node2 : nodes2) {
            if (possibleAncestor(node1, node2)) {
                return true;
            }
        }
        return false;
    }

    public List<Node> getAncestors(List<Node> nodes) {
        Set<Node> ancestors = new HashSet<>();

        for (Node n : graph.getNodes()) {
            for (Node m : nodes) {
                if (isAncestorOf(n, m)) {
                    ancestors.add(n);
                }
            }
        }

        return new ArrayList<>(ancestors);
    }

    /**
     * Determines whether one node is a descendent of another.
     */
    public boolean isDescendentOf(Node node1, Node node2) {
        return node1 == node2 || GraphUtils.existsDirectedPathFromTo(node2, node1, graph);
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
        return GraphUtils.isDConnectedTo(x, y, z, graph);
    }

    public boolean isDConnectedTo(List<Node> x, List<Node> y, List<Node> z) {
        return GraphUtils.isDConnectedTo(x, y, z, graph);
    }


    /**
     * Determines whether one n ode is d-separated from another. According to
     * Spirtes, Richardson and Meek, two nodes are d- connected given some
     * conditioning set Z if there is an acyclic undirected path U between them,
     * such that every collider on U is an ancestor of some element in Z and
     * every non-collider on U is not in Z. Two elements are d-separated just in
     * case they are not d-connected. A collider is a node which two edges hold
     * in common for which the endpoints leading into the node are both arrow
     * endpoints.
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
        LinkedList<Node> allNodes = new LinkedList<>(graph.getNodes());
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
                List<Node> adj = new LinkedList<>(graph.getAdjacentNodes(center));

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

                    if (!((graph.isDefNoncollider(X, Y, Z)
                            && !(condNodes.contains(Y))) || (graph.isDefCollider(X, Y, Z)
                            && possibleAncestorSet(Y, condNodes)))) {
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
        return GraphUtils.existsInducingPath(node2, node1, graph);
    }

    /**
     * Determines whether one node is a proper ancestor of another.
     */
    public boolean isProperAncestorOf(Node node1, Node node2) {
        return node1 != node2 && isAncestorOf(node1, node2);
    }

    /**
     * @return true iff there is a directed path from node1 to node2.
     */
    boolean existsUndirectedPathVisit(Node node1, Node node2, Set<Node> path) {
        path.add(node1);

        for (Edge edge : graph.getEdges(node1)) {
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

        path.remove(node1);
        return false;
    }

    /**
     * @return true iff there is a semi-directed path from node1 to node2
     */
    private boolean existsSemiDirectedPathVisit(Node node1, Set<Node> nodes2,
                                                LinkedList<Node> path) {
        path.addLast(node1);

        for (Edge edge : graph.getEdges(node1)) {
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


    public boolean isDirectedFromTo(Node node1, Node node2) {
        List<Edge> edges = graph.getEdges(node1, node2);
        if (edges.size() != 1) {
            return false;
        }
        Edge edge = edges.get(0);
        return edge.pointsTowards(node2);
    }

    public boolean isUndirectedFromTo(Node node1, Node node2) {
        Edge edge = graph.getEdge(node1, node2);
        return edge != null && edge.getEndpoint1() == Endpoint.TAIL && edge.getEndpoint2() == Endpoint.TAIL;
    }

    public boolean possibleAncestor(Node node1, Node node2) {
        return existsSemiDirectedPathFromTo(node1,
                Collections.singleton(node2));
    }

    /**
     * Determines whether one node is a proper decendent of another
     */
    public boolean isProperDescendentOf(Node node1, Node node2) {
        return node1 != node2 && isDescendentOf(node1, node2);
    }



    public List<Node> findCycle() {
        for (Node a : graph.getNodes()) {
            List<Node> path = findDirectedPath(a, a);
            if (!path.isEmpty()) {
                return path;
            }
        }

        return new LinkedList<>();
    }
}

