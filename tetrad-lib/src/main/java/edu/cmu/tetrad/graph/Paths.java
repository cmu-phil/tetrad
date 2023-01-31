package edu.cmu.tetrad.graph;

import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.SepsetMap;
import edu.cmu.tetrad.util.SublistGenerator;
import edu.cmu.tetrad.util.TaskManager;
import edu.cmu.tetrad.util.TetradSerializable;

import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;

public class Paths implements TetradSerializable {
    static final long serialVersionUID = 23L;

    Graph graph;

    public Paths(Graph graph) {
        if (graph == null) throw new NullPointerException("Null graph");
        this.graph = graph;
    }

    /**
     * @return the connected components of the given graph, as a list of lists
     * of nodes.
     */
    public List<List<Node>> connectedComponents() {
        List<List<Node>> components = new LinkedList<>();
        LinkedList<Node> unsortedNodes = new LinkedList<>(graph.getNodes());

        while (!unsortedNodes.isEmpty()) {
            Node seed = unsortedNodes.removeFirst();
            Set<Node> component = new ConcurrentSkipListSet<>();
            collectComponentVisit(seed, component, unsortedNodes);
            components.add(new ArrayList<>(component));
        }

        return components;
    }

    /**
     * @param node1 The 'from' node.
     * @param node2 The 'to'node.
     * @return A path from <code>node1</code> to <code>node2</code>, or null if
     * there is no path.
     */
    private List<Node> directedPathFromTo(Node node1, Node node2) {
        return directedPathVisit(node1, node2, new LinkedList<>());
    }

    /**
     * @return the path of the first directed path found from node1 to node2, if
     * any.
     */
    private List<Node> directedPathVisit(Node node1, Node node2, LinkedList<Node> path) {
        path.addLast(node1);

        for (Edge edge : graph.getEdges(node1)) {
            Node child = Edges.traverseDirected(node1, edge);

            if (child == null) {
                continue;
            }

            if (child == node2) {
                return path;
            }

            if (path.contains(child)) {
                continue;
            }

            if (directedPathVisit(child, node2, path) != null) {
                return path;
            }
        }

        path.removeLast();
        return null;
    }


    public List<List<Node>> directedPathsFromTo(Node node1, Node node2, int maxLength) {
        List<List<Node>> paths = new LinkedList<>();
        directedPathsFromToVisit(node1, node2, new LinkedList<>(), paths, maxLength);
        return paths;
    }

    private void directedPathsFromToVisit(Node node1, Node node2, LinkedList<Node> path, List<List<Node>> paths, int maxLength) {
        if (maxLength != -1 && path.size() > maxLength - 2) {
            return;
        }

        int witnessed = 0;

        for (Node node : path) {
            if (node == node1) {
                witnessed++;
            }
        }

        if (witnessed > 1) {
            return;
        }

        path.addLast(node1);

        for (Edge edge : graph.getEdges(node1)) {
            Node child = Edges.traverseDirected(node1, edge);

            if (child == null) {
                continue;
            }

            if (path.contains(child)) {
                continue;
            }

            if (child == node2) {
                LinkedList<Node> _path = new LinkedList<>(path);
                _path.add(child);
                paths.add(_path);
                continue;
            }

            directedPathsFromToVisit(child, node2, path, paths, maxLength);
        }

        path.removeLast();
    }

    public List<List<Node>> semidirectedPathsFromTo(Node node1, Node node2, int maxLength) {
        List<List<Node>> paths = new LinkedList<>();
        semidirectedPathsFromToVisit(node1, node2, new LinkedList<>(), paths, maxLength);
        return paths;
    }

    private void semidirectedPathsFromToVisit(Node node1, Node node2, LinkedList<Node> path, List<List<Node>> paths, int maxLength) {
        if (maxLength != -1 && path.size() > maxLength - 2) {
            return;
        }

        int witnessed = 0;

        for (Node node : path) {
            if (node == node1) {
                witnessed++;
            }
        }

        if (witnessed > 1) {
            return;
        }

        path.addLast(node1);

        for (Edge edge : graph.getEdges(node1)) {
            Node child = Edges.traverseSemiDirected(node1, edge);

            if (child == null) {
                continue;
            }

            if (child == node2) {
                LinkedList<Node> _path = new LinkedList<>(path);
                _path.add(child);
                paths.add(_path);
                continue;
            }

            if (path.contains(child)) {
                continue;
            }

            semidirectedPathsFromToVisit(child, node2, path, paths, maxLength);
        }

        path.removeLast();
    }

    public List<List<Node>> allPathsFromTo(Node node1, Node node2, int maxLength) {
        List<List<Node>> paths = new LinkedList<>();
        allPathsFromToVisit(node1, node2, new LinkedList<>(), paths, maxLength);
        return paths;
    }

    private void allPathsFromToVisit(Node node1, Node node2, LinkedList<Node> path, List<List<Node>> paths, int maxLength) {
        path.addLast(node1);

        if (path.size() > (maxLength == -1 ? 1000 : maxLength)) {
            return;
        }

        for (Edge edge : graph.getEdges(node1)) {
            Node child = Edges.traverse(node1, edge);

            if (child == null) {
                continue;
            }

            if (path.contains(child)) {
                continue;
            }

            if (child == node2) {
                List<Node> _path = new ArrayList<>(path);
                _path.add(child);
                paths.add(_path);
                continue;
            }

            allPathsFromToVisit(child, node2, path, paths, maxLength);
        }

        path.removeLast();
    }

    public List<List<Node>> allDirectedPathsFromTo(Node node1, Node node2, int maxLength) {
        List<List<Node>> paths = new LinkedList<>();
        allDirectedPathsFromToVisit(node1, node2, new LinkedList<>(), paths, maxLength);
        return paths;
    }

    private void allDirectedPathsFromToVisit(Node node1, Node node2, LinkedList<Node> path, List<List<Node>> paths, int maxLength) {
        path.addLast(node1);

        if (path.size() > (maxLength == -1 ? 1000 : maxLength)) {
            path.removeLast();
            return;
        }

        for (Edge edge : graph.getEdges(node1)) {
            Node child = Edges.traverseDirected(node1, edge);

            if (child == null) {
                continue;
            }

            if (path.contains(child)) {
                continue;
            }

            if (child == node2) {
                List<Node> _path = new ArrayList<>(path);
                _path.add(child);
                paths.add(_path);
                continue;
            }

            allDirectedPathsFromToVisit(child, node2, path, paths, maxLength);
        }

        path.removeLast();
    }

    public List<List<Node>> treks(Node node1, Node node2, int maxLength) {
        List<List<Node>> paths = new LinkedList<>();
        treks(node1, node2, new LinkedList<>(), paths, maxLength);
        return paths;
    }

    private void treks(Node node1, Node node2, LinkedList<Node> path, List<List<Node>> paths, int maxLength) {
        if (path.size() > (maxLength == -1 ? 1000 : maxLength - 2)) {
            return;
        }

        if (path.contains(node1)) {
            return;
        }

        if (node1 == node2) {
            return;
        }

        path.addLast(node1);

        for (Edge edge : graph.getEdges(node1)) {
            Node next = Edges.traverse(node1, edge);

            // Must be a directed edge.
            if (!edge.isDirected()) {
                continue;
            }

            // Can't have any colliders on the path.
            if (path.size() > 1) {
                Node node0 = path.get(path.size() - 2);

                if (next == node0) {
                    continue;
                }

                if (graph.isDefCollider(node0, node1, next)) {
                    continue;
                }
            }

            // Found a path.
            if (next == node2 && !path.isEmpty()) {
                LinkedList<Node> _path = new LinkedList<>(path);
                _path.add(next);
                paths.add(_path);
                continue;
            }

            // Nodes may only appear on the path once.
            if (path.contains(next)) {
                continue;
            }

            treks(next, node2, path, paths, maxLength);
        }

        path.removeLast();
    }

    public List<List<Node>> treksIncludingBidirected(Node node1, Node node2) {
        List<List<Node>> paths = new LinkedList<>();
        treksIncludingBidirected(node1, node2, new LinkedList<>(), paths);
        return paths;
    }

    private void treksIncludingBidirected(Node node1, Node node2, LinkedList<Node> path, List<List<Node>> paths) {
        if (!(graph instanceof SemGraph)) {
            throw new IllegalArgumentException("Expecting a SEM graph");
        }

        SemGraph _graph = (SemGraph) graph;

        if (!_graph.isShowErrorTerms()) {
            throw new IllegalArgumentException("The SEM Graph must be showing its error terms; this method " + "doesn't traverse two edges between the same nodes well.");
        }

        if (path.contains(node1)) {
            return;
        }

        if (node1 == node2) {
            return;
        }

        path.addLast(node1);

        for (Edge edge : graph.getEdges(node1)) {
            Node next = Edges.traverse(node1, edge);

            // Must be a directed edge or a bidirected edge.
            if (!(edge.isDirected() || Edges.isBidirectedEdge(edge))) {
                continue;
            }

            // Can't have any colliders on the path.
            if (path.size() > 1) {
                Node node0 = path.get(path.size() - 2);

                if (next == node0) {
                    continue;
                }

                if (graph.isDefCollider(node0, node1, next)) {
                    continue;
                }
            }

            // Found a path.
            if (next == node2 && !path.isEmpty()) {
                LinkedList<Node> _path = new LinkedList<>(path);
                _path.add(next);
                paths.add(_path);
                continue;
            }

            // Nodes may only appear on the path once.
            if (path.contains(next)) {
                continue;
            }

            treksIncludingBidirected(next, node2, path, paths);
        }

        path.removeLast();
    }

    public boolean existsDirectedPathFromTo(Node node1, Node node2, int depth) {
        return node1 == node2 || existsDirectedPathVisit(node1, node2, new LinkedList<>(), depth, graph);
    }

    private static boolean existsDirectedPathVisit(Node node1, Node node2, LinkedList<Node> path, int depth, Graph graph) {
        path.addLast(node1);

        if (depth == -1) depth = Integer.MAX_VALUE;

        if (path.size() >= depth) {
            return false;
        }

        for (Edge edge : graph.getEdges(node1)) {
            Node child = Edges.traverseDirected(node1, edge);

            if (graph.getEdges(node1, child).size() == 2) {
                return true;
            }

            if (child == null) {
                continue;
            }

            if (child == node2) {
                return true;
            }

            if (path.contains(child)) {
                continue;
            }

            if (existsDirectedPathVisit(child, node2, path, depth, graph)) {
                return true;
            }
        }

        path.removeLast();
        return false;
    }



    // Returns true if a path consisting of undirected and directed edges toward 'to' exists of
    // length at most 'bound'. Cycle checker in other words.
    public boolean existsSemiDirectedPath(Node from, Node to) {
        Queue<Node> Q = new LinkedList<>();
        Set<Node> V = new HashSet<>();

        for (Node u : graph.getAdjacentNodes(from)) {
            Edge edge = graph.getEdge(from, u);
            Node c = GraphUtils.traverseSemiDirected(from, edge);

            if (c == null) {
                continue;
            }

            if (!V.contains(c)) {
                V.add(c);
                Q.offer(c);
            }
        }

        while (!Q.isEmpty()) {
            Node t = Q.remove();

            if (t == to) {
                return true;
            }

            for (Node u : graph.getAdjacentNodes(t)) {
                Edge edge = graph.getEdge(t, u);
                Node c = GraphUtils.traverseSemiDirected(t, edge);

                if (c == null) {
                    continue;
                }

                if (!V.contains(c)) {
                    V.add(c);
                    Q.offer(c);
                }
            }
        }

        return false;
    }

    public boolean isDConnectedTo(List<Node> x, List<Node> y, List<Node> z) {
        Set<Node> zAncestors = zAncestors(z);

        Queue<OrderedPair<Node>> Q = new ArrayDeque<>();
        Set<OrderedPair<Node>> V = new HashSet<>();

        for (Node _x : x) {
            for (Node node : graph.getAdjacentNodes(_x)) {
                if (y.contains(node)) {
                    return true;
                }
                OrderedPair<Node> edge = new OrderedPair<>(_x, node);
                Q.offer(edge);
                V.add(edge);
            }
        }

        while (!Q.isEmpty()) {
            OrderedPair<Node> t = Q.poll();

            Node b = t.getFirst();
            Node a = t.getSecond();

            for (Node c : graph.getAdjacentNodes(b)) {
                if (c == a) {
                    continue;
                }

                boolean collider = graph.isDefCollider(a, b, c);
                if (!((collider && zAncestors.contains(b)) || (!collider && !z.contains(b)))) {
                    continue;
                }

                if (y.contains(c)) {
                    return true;
                }

                OrderedPair<Node> u = new OrderedPair<>(b, c);
                if (V.contains(u)) {
                    continue;
                }

                V.add(u);
                Q.offer(u);
            }
        }

        return false;
    }

    public Set<Node> getDconnectedVars(Node y, List<Node> z) {
        Set<Node> Y = new HashSet<>();

        class EdgeNode {

            private final Edge edge;
            private final Node node;

            public EdgeNode(Edge edge, Node node) {
                this.edge = edge;
                this.node = node;
            }

            public int hashCode() {
                return this.edge.hashCode() + this.node.hashCode();
            }

            public boolean equals(Object o) {
                if (!(o instanceof EdgeNode)) {
                    throw new IllegalArgumentException();
                }
                EdgeNode _o = (EdgeNode) o;
                return _o.edge == this.edge && _o.node == this.node;
            }
        }

        Queue<EdgeNode> Q = new ArrayDeque<>();
        Set<EdgeNode> V = new HashSet<>();

        for (Edge edge : graph.getEdges(y)) {
            EdgeNode edgeNode = new EdgeNode(edge, y);
            Q.offer(edgeNode);
            V.add(edgeNode);
            Y.add(edge.getDistalNode(y));
        }

        while (!Q.isEmpty()) {
            EdgeNode t = Q.poll();

            Edge edge1 = t.edge;
            Node a = t.node;
            Node b = edge1.getDistalNode(a);

            for (Edge edge2 : graph.getEdges(b)) {
                Node c = edge2.getDistalNode(b);
                if (c == a) {
                    continue;
                }

                if (reachable(edge1, edge2, a, z)) {
                    EdgeNode u = new EdgeNode(edge2, b);

                    if (!V.contains(u)) {
                        V.add(u);
                        Q.offer(u);
                        Y.add(c);
                    }
                }
            }
        }

        return Y;
    }



    private boolean reachable(Edge e1, Edge e2, Node a, List<Node> z) {
        Node b = e1.getDistalNode(a);
        Node c = e2.getDistalNode(b);

        boolean collider = e1.getProximalEndpoint(b) == Endpoint.ARROW && e2.getProximalEndpoint(b) == Endpoint.ARROW;

        if ((!collider || graph.getUnderlineModel().isUnderlineTriple(a, b, c)) && !z.contains(b)) {
            return true;
        }

        boolean ancestor = isAncestor(b, z);
        return collider && ancestor;
    }

    private boolean reachable(Node a, Node b, Node c, List<Node> z, Set<Triple> colliders) {
        boolean collider = graph.isDefCollider(a, b, c);

        if (!collider && !z.contains(b)) {
            return true;
        }

        boolean ancestor = isAncestor(b, z);

        boolean colliderReachable = collider && ancestor;

        if (colliders != null && collider && !ancestor) {
            colliders.add(new Triple(a, b, c));
        }

        return colliderReachable;
    }

    private boolean isAncestor(Node b, List<Node> z) {
        if (z.contains(b)) {
            return true;
        }

        Queue<Node> Q = new ArrayDeque<>();
        Set<Node> V = new HashSet<>();

        for (Node node : z) {
            Q.offer(node);
            V.add(node);
        }

        while (!Q.isEmpty()) {
            Node t = Q.poll();
            if (t == b) {
                return true;
            }

            for (Node c : graph.getParents(t)) {
                if (!V.contains(c)) {
                    Q.offer(c);
                    V.add(c);
                }
            }
        }

        return false;

    }



    private boolean reachable(Node a, Node b, Node c, List<Node> z) {
        boolean collider = graph.isDefCollider(a, b, c);

        if ((!collider || graph.getUnderlineModel().isUnderlineTriple(a, b, c)) && !z.contains(b)) {
            return true;
        }

        boolean ancestor = isAncestor(b, z);
        return collider && ancestor;
    }


    private List<Node> getPassNodes(Node a, Node b, List<Node> z) {
        List<Node> passNodes = new ArrayList<>();

        for (Node c : graph.getAdjacentNodes(b)) {
            if (c == a) {
                continue;
            }

            if (reachable(a, b, c, z)) {
                passNodes.add(c);
            }
        }

        return passNodes;
    }



    private Set<Node> zAncestors(List<Node> z) {
        Queue<Node> Q = new ArrayDeque<>();
        Set<Node> V = new HashSet<>();

        for (Node node : z) {
            Q.offer(node);
            V.add(node);
        }

        while (!Q.isEmpty()) {
            Node t = Q.poll();

            for (Node c : graph.getParents(t)) {
                if (!V.contains(c)) {
                    Q.offer(c);
                    V.add(c);
                }
            }
        }

        return V;
    }



    public Set<Node> zAncestors2(List<Node> z) {
        Set<Node> ancestors = new HashSet<>(z);

        boolean changed = true;

        while (changed) {
            changed = false;

            for (Node n : new HashSet<>(ancestors)) {
                List<Node> parents = graph.getParents(n);

                if (!ancestors.containsAll(parents)) {
                    ancestors.addAll(parents);
                    changed = true;
                }
            }
        }

        return ancestors;
    }

    /**
     * Determines whether an inducing path exists between node1 and node2, given
     * a set O of observed nodes and a set sem of conditioned nodes.
     *
     * @param x the first node.
     * @param y the second node.
     * @return true if an inducing path exists, false if not.
     */
    public boolean existsInducingPath(Node x, Node y) {
        if (x.getNodeType() != NodeType.MEASURED) {
            throw new IllegalArgumentException();
        }
        if (y.getNodeType() != NodeType.MEASURED) {
            throw new IllegalArgumentException();
        }

        LinkedList<Node> path = new LinkedList<>();
        path.add(x);

        for (Node b : graph.getAdjacentNodes(x)) {
            if (existsInducingPathVisit(x, b, x, y, path)) {
                return true;
            }
        }

        return false;
    }

    // Needs to be public.
    public boolean existsInducingPathVisit(Node a, Node b, Node x, Node y, LinkedList<Node> path) {
        if (path.contains(b)) {
            return false;
        }

        path.addLast(b);

        if (b == y) {
            return true;
        }

        for (Node c : graph.getAdjacentNodes(b)) {
            if (c == a) {
                continue;
            }

            if (b.getNodeType() == NodeType.MEASURED) {
                if (!graph.isDefCollider(a, b, c)) {
                    continue;
                }

            }

            if (graph.isDefCollider(a, b, c)) {
                if (!(graph.paths().isAncestorOf(b, x) || graph.paths().isAncestorOf(b, y))) {
                    continue;
                }
            }

            if (existsInducingPathVisit(b, c, x, y, path)) {
                return true;
            }
        }

        path.removeLast();
        return false;
    }

    public Set<Node> getInducedNodes(Node x) {
        if (x.getNodeType() != NodeType.MEASURED) {
            throw new IllegalArgumentException();
        }

        LinkedList<Node> path = new LinkedList<>();
        path.add(x);

        Set<Node> induced = new HashSet<>();

        for (Node b : graph.getAdjacentNodes(x)) {
            collectInducedNodesVisit(x, b, path, induced);
        }

        return induced;
    }

    private void collectInducedNodesVisit(Node x, Node b, LinkedList<Node> path, Set<Node> induced) {
        if (path.contains(b)) {
            return;
        }

        if (induced.contains(b)) {
            return;
        }

        path.addLast(b);

        if (isInducingPath(path)) {
            induced.add(b);
        }

        for (Node c : graph.getAdjacentNodes(b)) {
            collectInducedNodesVisit(x, c, path, induced);
        }

        path.removeLast();
    }

    public boolean isInducingPath(LinkedList<Node> path) {
        if (path.size() < 2) {
            return false;
        }
        if (path.get(0).getNodeType() != NodeType.MEASURED) {
            return false;
        }
        if (path.get(path.size() - 1).getNodeType() != NodeType.MEASURED) {
            return false;
        }

        System.out.println("Path = " + path);

        Node x = path.get(0);
        Node y = path.get(path.size() - 1);

        for (int i = 0; i < path.size() - 2; i++) {
            Node a = path.get(i);
            Node b = path.get(i + 1);
            Node c = path.get(i + 2);

            if (b.getNodeType() == NodeType.MEASURED) {
                if (!graph.isDefCollider(a, b, c)) {
                    return false;
                }
            }

            if (graph.isDefCollider(a, b, c)) {
                if (!(graph.paths().isAncestorOf(b, x) || graph.paths().isAncestorOf(b, y))) {
                    return false;
                }
            }
        }

        return true;
    }

    public List<Node> getInducingPath(Node x, Node y) {
        if (x.getNodeType() != NodeType.MEASURED) {
            throw new IllegalArgumentException();
        }
        if (y.getNodeType() != NodeType.MEASURED) {
            throw new IllegalArgumentException();
        }

        LinkedList<Node> path = new LinkedList<>();
        path.add(x);

        for (Node b : graph.getAdjacentNodes(x)) {
            if (existsInducingPathVisit(x, b, x, y, path)) {
                return path;
            }
        }

        return null;
    }

    public List<Node> possibleDsep(Node x, Node y, int maxPathLength) {
        Set<Node> dsep = new HashSet<>();

        Queue<OrderedPair<Node>> Q = new ArrayDeque<>();
        Set<OrderedPair<Node>> V = new HashSet<>();

        Map<Node, Set<Node>> previous = new HashMap<>();
        previous.put(x, new HashSet<>());

        OrderedPair<Node> e = null;
        int distance = 0;

        assert graph != null;
        Set<Node> adjacentNodes = new HashSet<>(graph.getAdjacentNodes(x));

        for (Node b : adjacentNodes) {
            if (b == y) {
                continue;
            }
            OrderedPair<Node> edge = new OrderedPair<>(x, b);
            if (e == null) {
                e = edge;
            }
            Q.offer(edge);
            V.add(edge);
            addToSet(previous, b, x);
            dsep.add(b);
        }

        while (!Q.isEmpty()) {
            OrderedPair<Node> t = Q.poll();

            if (e == t) {
                e = null;
                distance++;
                if (distance > 0 && distance > (maxPathLength == -1 ? 1000 : maxPathLength)) {
                    break;
                }
            }

            Node a = t.getFirst();
            Node b = t.getSecond();

            if (existOnePathWithPossibleParents(previous, b, x, b)) {
                dsep.add(b);
            }

            for (Node c : graph.getAdjacentNodes(b)) {
                if (c == a) {
                    continue;
                }
                if (c == x) {
                    continue;
                }
                if (c == y) {
                    continue;
                }

                addToSet(previous, b, c);

                if (graph.isDefCollider(a, b, c) || graph.isAdjacentTo(a, c)) {
                    OrderedPair<Node> u = new OrderedPair<>(a, c);
                    if (V.contains(u)) {
                        continue;
                    }

                    V.add(u);
                    Q.offer(u);

                    if (e == null) {
                        e = u;
                    }
                }
            }
        }

        dsep.remove(x);
        dsep.remove(y);

        List<Node> _dsep = new ArrayList<>(dsep);

        Collections.sort(_dsep);
        Collections.reverse(_dsep);

        return _dsep;
    }

    /**
     * Remove edges by the possible d-separation rule.
     *
     * @param test    The independence test to use to remove edges.
     * @param sepsets A sepset map to which sepsets should be added. May be null, in which case sepsets
     *                will not be recorded.
     */
    public boolean removeByPossibleDsep(IndependenceTest test, SepsetMap sepsets) {
        boolean changed = false;

        for (Edge edge : graph.getEdges()) {
            Node a = edge.getNode1();
            Node b = edge.getNode2();

            {
                List<Node> possibleDsep = possibleDsep(a, b, -1);

                SublistGenerator gen = new SublistGenerator(possibleDsep.size(), possibleDsep.size());
                int[] choice;

                while ((choice = gen.next()) != null) {
                    if (choice.length < 2) continue;
                    List<Node> sepset = GraphUtils.asList(choice, possibleDsep);
                    if (new HashSet<>(graph.getAdjacentNodes(a)).containsAll(sepset)) continue;
                    if (new HashSet<>(graph.getAdjacentNodes(b)).containsAll(sepset)) continue;
                    if (test.checkIndependence(a, b, sepset).independent()) {
                        graph.removeEdge(edge);
                        changed = true;

                        if (sepsets != null) {
                            sepsets.set(a, b, sepset);
                        }

                        break;
                    }
                }
            }

            if (graph.containsEdge(edge)) {
                {
                    List<Node> possibleDsep = possibleDsep(b, a, -1);

                    SublistGenerator gen = new SublistGenerator(possibleDsep.size(), possibleDsep.size());
                    int[] choice;

                    while ((choice = gen.next()) != null) {
                        if (choice.length < 2) continue;
                        List<Node> sepset = GraphUtils.asList(choice, possibleDsep);
                        if (new HashSet<>(graph.getAdjacentNodes(a)).containsAll(sepset)) continue;
                        if (new HashSet<>(graph.getAdjacentNodes(b)).containsAll(sepset)) continue;
                        if (test.checkIndependence(a, b, sepset).independent()) {
                            graph.removeEdge(edge);
                            changed = true;

                            if (sepsets != null) {
                                sepsets.set(a, b, sepset);
                            }

                            break;
                        }
                    }
                }
            }
        }

        return changed;
    }


    private boolean existOnePathWithPossibleParents(Map<Node, Set<Node>> previous, Node w, Node x, Node b) {
        if (w == x) {
            return true;
        }

        Set<Node> p = previous.get(w);
        if (p == null) {
            return false;
        }

        for (Node r : p) {
            if (r == b || r == x) {
                continue;
            }

            if ((existsSemidirectedPath(r, x)) || existsSemidirectedPath(r, b)) {
                return true;
            }
        }

        return false;
    }

    private static void addToSet(Map<Node, Set<Node>> previous, Node b, Node c) {
        previous.computeIfAbsent(c, k -> new HashSet<>());
        Set<Node> list = previous.get(c);
        list.add(b);
    }

    public boolean existsSemidirectedPath(Node from, Node to) {
        Queue<Node> Q = new LinkedList<>();
        Set<Node> V = new HashSet<>();

        for (Node u : graph.getAdjacentNodes(from)) {
            Edge edge = graph.getEdge(from, u);
            Node c = GraphUtils.traverseSemiDirected(from, edge);

            if (c == null) {
                continue;
            }

            if (!V.contains(c)) {
                V.add(c);
                Q.offer(c);
            }
        }

        while (!Q.isEmpty()) {
            Node t = Q.remove();

            if (t == to) {
                return true;
            }

            for (Node u : graph.getAdjacentNodes(t)) {
                Edge edge = graph.getEdge(t, u);
                Node c = GraphUtils.traverseSemiDirected(t, edge);

                if (c == null) {
                    continue;
                }

                if (!V.contains(c)) {
                    V.add(c);
                    Q.offer(c);
                }
            }
        }

        return false;
    }



    // Returns true if a path consisting of undirected and directed edges toward 'to' exists of
    // length at most 'bound'. Cycle checker in other words.
    public List<Node> existsUnblockedSemiDirectedPath(Node from, Node to, Set<Node> cond, int bound) {
        Queue<Node> Q = new LinkedList<>();
        Set<Node> V = new HashSet<>();
        Q.offer(from);
        V.add(from);
        Node e = null;
        int distance = 0;
        Map<Node, Node> back = new HashMap<>();

        while (!Q.isEmpty()) {
            Node t = Q.remove();
            if (t == to) {
                LinkedList<Node> _back = new LinkedList<>();
                _back.add(to);
                return _back;
            }

            if (e == t) {
                e = null;
                distance++;
                if (distance > (bound == -1 ? 1000 : bound)) {
                    return null;
                }
            }

            for (Node u : graph.getAdjacentNodes(t)) {
                Edge edge = graph.getEdge(t, u);
                Node c = GraphUtils.traverseSemiDirected(t, edge);
                if (c == null) {
                    continue;
                }
                if (cond.contains(c)) {
                    continue;
                }

                if (c == to) {
                    back.put(c, t);
                    LinkedList<Node> _back = new LinkedList<>();
                    _back.addLast(to);
                    Node f = to;

                    for (int i = 0; i < 10; i++) {
                        f = back.get(f);
                        if (f == null) {
                            break;
                        }
                        _back.addFirst(f);
                    }

                    return _back;
                }

                if (!V.contains(c)) {
                    back.put(c, t);
                    V.add(c);
                    Q.offer(c);

                    if (e == null) {
                        e = u;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Check to see if a set of variables Z satisfies the back-door criterion
     * relative to node x and node y.
     *
     * @author Kevin V. Bui (March 2020)
     */
    public boolean isSatisfyBackDoorCriterion(Graph graph, Node x, Node y, List<Node> z) {
        Dag dag = new Dag(graph);

        // make sure no nodes in z is a descendant of x
        if (z.stream().anyMatch(zNode -> dag.paths().isDescendentOf(zNode, x))) {
            return false;
        }

        // make sure zNodes bock every path between node x and node y that contains an arrow into node x
        List<List<Node>> directedPaths = allDirectedPathsFromTo(x, y, -1);
        directedPaths.forEach(nodes -> {
            // remove all variables that are not on the back-door path
            nodes.forEach(node -> {
                if (!(node == x || node == y)) {
                    dag.removeNode(node);
                }
            });
        });

        return dag.paths().isDSeparatedFrom(x, y, z);
    }

    private static class EdgeNode {

        private final Edge edge;
        private final Node node;

        public EdgeNode(Edge edge, Node node) {
            if (edge.getNode1() == node) {
                this.edge = edge;
            } else if (edge.getNode2() == node) {
                this.edge = edge.reverse();
            } else {
                throw new IllegalArgumentException("Edge does not contain node.");
            }

            this.node = node;
        }

        public int hashCode() {
            return this.edge.hashCode() + 7 * this.node.hashCode();
        }

        public boolean equals(Object o) {
            if (o == null) {
                return false;
            }

            if (!(o instanceof EdgeNode)) {
                return false;
            }

            EdgeNode _o = (EdgeNode) o;
            return _o.edge.equals(this.edge) && _o.node.equals(this.node);
        }

        public Edge getEdge() {
            return this.edge;
        }

        public Node getNode() {
            return this.node;
        }
    }



    // Finds a sepset for x and y, if there is one; otherwise, returns null.
    public List<Node> getSepset(Node x, Node y) {
        List<Node> sepset = getSepsetVisit(x, y);
        if (sepset == null) {
            sepset = getSepsetVisit(y, x);
        }
        return sepset;
    }

    private List<Node> getSepsetVisit(Node x, Node y) {
        if (x == y) {
            return null;
        }

        List<Node> z = new ArrayList<>();

        List<Node> _z;

        do {
            _z = new ArrayList<>(z);

            Set<Node> path = new HashSet<>();
            path.add(x);
            Set<Triple> colliders = new HashSet<>();

            for (Node b : graph.getAdjacentNodes(x)) {
                if (sepsetPathFound(x, b, y, path, z, colliders, -1)) {
                    return null;
                }
            }
        } while (!new HashSet<>(z).equals(new HashSet<>(_z)));

        return z;
    }

    private boolean sepsetPathFound(Node a, Node b, Node y, Set<Node> path, List<Node> z, Set<Triple> colliders, int bound) {
        if (b == y) {
            return true;
        }

        if (path.contains(b)) {
            return false;
        }

        if (path.size() > (bound == -1 ? 1000 : bound)) {
            return false;
        }

        path.add(b);

        if (b.getNodeType() == NodeType.LATENT || z.contains(b)) {
            List<Node> passNodes = getPassNodes(a, b, z);

            for (Node c : passNodes) {
                if (sepsetPathFound(b, c, y, path, z, colliders, bound)) {
                    path.remove(b);
                    return true;
                }
            }

            path.remove(b);
            return false;
        } else {
            boolean found1 = false;
            Set<Triple> _colliders1 = new HashSet<>();

            for (Node c : getPassNodes(a, b, z)) {
                if (sepsetPathFound(b, c, y, path, z, _colliders1, bound)) {
                    found1 = true;
                    break;
                }
            }

            if (!found1) {
                path.remove(b);
                colliders.addAll(_colliders1);
                return false;
            }

            z.add(b);
            boolean found2 = false;
            Set<Triple> _colliders2 = new HashSet<>();

            for (Node c : getPassNodes(a, b, z)) {
                if (sepsetPathFound(b, c, y, path, z, _colliders2, bound)) {
                    found2 = true;
                    break;
                }
            }

            if (!found2) {
                path.remove(b);
                colliders.addAll(_colliders2);
                return false;
            }

            z.remove(b);
            path.remove(b);
            return true;
        }
    }

    // Breadth first.
    public boolean isDConnectedTo(Node x, Node y, List<Node> z) {
        class EdgeNode {

            private final Edge edge;
            private final Node node;

            public EdgeNode(Edge edge, Node node) {
                this.edge = edge;
                this.node = node;
            }

            public int hashCode() {
                return this.edge.hashCode() + this.node.hashCode();
            }

            public boolean equals(Object o) {
                if (!(o instanceof EdgeNode)) {
                    throw new IllegalArgumentException();
                }
                EdgeNode _o = (EdgeNode) o;
                return _o.edge == this.edge && _o.node == this.node;
            }
        }

        Queue<EdgeNode> Q = new ArrayDeque<>();
        Set<EdgeNode> V = new HashSet<>();

        if (x == y) {
            return true;
        }

        for (Edge edge : graph.getEdges(x)) {
            if (edge.getDistalNode(x) == y) {
                return true;
            }
            EdgeNode edgeNode = new EdgeNode(edge, x);
            Q.offer(edgeNode);
            V.add(edgeNode);
        }

        while (!Q.isEmpty()) {
            EdgeNode t = Q.poll();

            Edge edge1 = t.edge;
            Node a = t.node;
            Node b = edge1.getDistalNode(a);

            for (Edge edge2 : graph.getEdges(b)) {
                Node c = edge2.getDistalNode(b);
                if (c == a) {
                    continue;
                }

                if (reachable(edge1, edge2, a, z)) {
                    if (c == y) {
                        return true;
                    }

                    EdgeNode u = new EdgeNode(edge2, b);

                    if (!V.contains(u)) {
                        V.add(u);
                        Q.offer(u);
                    }
                }
            }
        }

        return false;
    }



    /**
     * Assumes node should be in component.
     */
    private void collectComponentVisit(Node node, Set<Node> component, List<Node> unsortedNodes) {
        if (TaskManager.getInstance().isCanceled()) {
            return;
        }

        component.add(node);
        unsortedNodes.remove(node);
        List<Node> adj = graph.getAdjacentNodes(node);

        for (Node anAdj : adj) {
            if (!component.contains(anAdj)) {
                collectComponentVisit(anAdj, component, unsortedNodes);
            }
        }
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
        return node1 == node2 || existsDirectedPathFromTo(node2, node1);
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

