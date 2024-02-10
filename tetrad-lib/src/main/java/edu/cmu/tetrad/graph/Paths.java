package edu.cmu.tetrad.graph;

import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.utils.SepsetMap;
import edu.cmu.tetrad.util.SublistGenerator;
import edu.cmu.tetrad.util.TaskManager;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializable;

import java.io.Serial;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * <p>Paths class.</p>
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class Paths implements TetradSerializable {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The graph.
     */
    private final Graph graph;

    /**
     * <p>Constructor for Paths.</p>
     *
     * @param graph a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Paths(Graph graph) {
        if (graph == null) throw new NullPointerException("Null graph");
        this.graph = graph;
    }

    private static void addToSet(Map<Node, Set<Node>> previous, Node b, Node c) {
        previous.computeIfAbsent(c, k -> new HashSet<>());
        Set<Node> list = previous.get(c);
        list.add(b);
    }

    /**
     * Returns a valid causal order for either a DAG or a CPDAG. (bryanandrews)
     *
     * @param initialOrder Variables in the order will be kept as close to this initial order as possible, either the
     *                     forward order or the reverse order, depending on the next parameter.
     * @param forward      Whether the variables will be iterated over in forward or reverse direction.
     * @return The valid causal order found.
     */
    public List<Node> getValidOrder(List<Node> initialOrder, boolean forward) {
        List<Node> _initialOrder = new ArrayList<>(initialOrder);
        Graph _graph = new EdgeListGraph(this.graph);

        if (forward) Collections.reverse(_initialOrder);
        List<Node> newOrder = new ArrayList<>();

        while (!_initialOrder.isEmpty()) {
            Iterator<Node> itr = _initialOrder.iterator();
            Node x;
            do {
                if (itr.hasNext()) x = itr.next();
                else throw new IllegalArgumentException("This graph has a cycle.");
            } while (invalidSink(x, _graph));
            newOrder.add(x);
            _graph.removeNode(x);
            itr.remove();
        }

        Collections.reverse(newOrder);

        return newOrder;
    }

    /**
     * <p>makeValidOrder.</p>
     *
     * @param order a {@link java.util.List} object
     */
    public void makeValidOrder(List<Node> order) {
        List<Node> initialOrder = new ArrayList<>(order);
        Graph _graph = new EdgeListGraph(this.graph);

        Collections.reverse(initialOrder);
        order.clear();

        while (!initialOrder.isEmpty()) {
            Iterator<Node> itr = initialOrder.iterator();
            Node x;
            do {
                if (itr.hasNext()) x = itr.next();
                else throw new IllegalArgumentException("This graph has a cycle.");
            } while (invalidSink(x, _graph));
            order.add(x);
            _graph.removeNode(x);
            itr.remove();
        }

        Collections.reverse(order);
    }

    private boolean invalidSink(Node x, Graph graph) {
        LinkedList<Node> neighbors = new LinkedList<>();

        for (Edge edge : graph.getEdges(x)) {
            if (edge.getDistalEndpoint(x) == Endpoint.ARROW) return true;
            if (edge.getProximalEndpoint(x) == Endpoint.TAIL) neighbors.add(edge.getDistalNode(x));
        }

        while (!neighbors.isEmpty()) {
            Node y = neighbors.pop();
            for (Node z : neighbors) if (!graph.isAdjacentTo(y, z)) return true;
        }

        return false;
    }

    /**
     * <p>maxCliques.</p>
     *
     * @return a {@link java.util.Set} object
     */
    public Set<Set<Node>> maxCliques() {
        int[][] graph = new int[this.graph.getNumNodes()][this.graph.getNumNodes()];
        List<Node> nodes = this.graph.getNodes();
        for (int i = 0; i < nodes.size(); i++) {
            for (int j = 0; j < nodes.size(); j++) {
                if (this.graph.isAdjacentTo(nodes.get(i), nodes.get(j))) {
                    graph[i][j] = 1;
                    graph[j][i] = 1;
                }
            }
        }

        List<List<Integer>> _cliques = AllCliquesAlgorithm.findCliques(graph, graph.length);

        Set<Set<Node>> cliques = new HashSet<>();
        for (List<Integer> _clique : _cliques) {
            if (_clique.size() < 2) continue;
            Set<Node> clique = new HashSet<>();
            for (Integer i : _clique) {
                clique.add(nodes.get(i));
            }
            cliques.add(clique);
        }

        Set<Set<Node>> copy = new HashSet<>(cliques);
        boolean changed = true;

        while (changed) {
            changed = false;

            for (Set<Node> clique : new HashSet<>(copy)) {
                for (Set<Node> other : new HashSet<>(copy)) {
                    if (clique == other) continue;
                    if (clique.containsAll(other)) {
                        copy.remove(other);
                        changed = true;
                    }
                }
            }
        }

        return copy;
    }

    /**
     * <p>connectedComponents.</p>
     *
     * @return the connected components of the given graph, as a list of lists of nodes.
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
     * <p>directedPathsFromTo.</p>
     *
     * @param node1     a {@link edu.cmu.tetrad.graph.Node} object
     * @param node2     a {@link edu.cmu.tetrad.graph.Node} object
     * @param maxLength a int
     * @return a {@link java.util.List} object
     */
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

    /**
     * <p>semidirectedPathsFromTo.</p>
     *
     * @param node1     a {@link edu.cmu.tetrad.graph.Node} object
     * @param node2     a {@link edu.cmu.tetrad.graph.Node} object
     * @param maxLength a int
     * @return a {@link java.util.List} object
     */
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

    /**
     * <p>allPathsFromTo.</p>
     *
     * @param node1     a {@link edu.cmu.tetrad.graph.Node} object
     * @param node2     a {@link edu.cmu.tetrad.graph.Node} object
     * @param maxLength a int
     * @return a {@link java.util.List} object
     */
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

    /**
     * <p>allDirectedPathsFromTo.</p>
     *
     * @param node1     a {@link edu.cmu.tetrad.graph.Node} object
     * @param node2     a {@link edu.cmu.tetrad.graph.Node} object
     * @param maxLength a int
     * @return a {@link java.util.List} object
     */
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

    /**
     * <p>treks.</p>
     *
     * @param node1     a {@link edu.cmu.tetrad.graph.Node} object
     * @param node2     a {@link edu.cmu.tetrad.graph.Node} object
     * @param maxLength a int
     * @return a {@link java.util.List} object
     */
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

    /**
     * <p>treksIncludingBidirected.</p>
     *
     * @param node1 a {@link edu.cmu.tetrad.graph.Node} object
     * @param node2 a {@link edu.cmu.tetrad.graph.Node} object
     * @return a {@link java.util.List} object
     */
    public List<List<Node>> treksIncludingBidirected(Node node1, Node node2) {
        List<List<Node>> paths = new LinkedList<>();
        treksIncludingBidirected(node1, node2, new LinkedList<>(), paths);
        return paths;
    }

    private void treksIncludingBidirected(Node node1, Node node2, LinkedList<Node> path, List<List<Node>> paths) {
        if (!(graph instanceof SemGraph _graph)) {
            throw new IllegalArgumentException("Expecting a SEM graph");
        }

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

    /**
     * <p>existsDirectedPathFromTo.</p>
     *
     * @param node1 a {@link edu.cmu.tetrad.graph.Node} object
     * @param node2 a {@link edu.cmu.tetrad.graph.Node} object
     * @param depth a int
     * @return a boolean
     */
    public boolean existsDirectedPathFromTo(Node node1, Node node2, int depth) {
        return node1 == node2 || existsDirectedPathVisit(node1, node2, new LinkedList<>(), depth);
    }

    private boolean existsDirectedPathVisit(Node node1, Node node2, LinkedList<Node> path, int depth) {
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

            if (existsDirectedPathVisit(child, node2, path, depth)) {
                return true;
            }
        }

        path.removeLast();
        return false;
    }


    // Returns true if a path consisting of undirected and directed edges toward 'to' exists of
    // length at most 'bound'. Cycle checker in other words.

    /**
     * <p>existsSemiDirectedPath.</p>
     *
     * @param from a {@link edu.cmu.tetrad.graph.Node} object
     * @param to   a {@link edu.cmu.tetrad.graph.Node} object
     * @return a boolean
     */
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

    /**
     * <p>isMConnectedTo.</p>
     *
     * @param x a {@link java.util.Set} object
     * @param y a {@link java.util.Set} object
     * @param z a {@link java.util.Set} object
     * @return a boolean
     */
    public boolean isMConnectedTo(Set<Node> x, Set<Node> y, Set<Node> z) {
        Set<Node> ancestors = ancestorsOf(z);

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
                if (!((collider && ancestors.contains(b)) || (!collider && !z.contains(b)))) {
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

    /**
     * Checks to see if x and y are d-connected given z.
     *
     * @param ancestorMap A map of nodes to their ancestors.
     * @param x           a {@link java.util.Set} object
     * @param y           a {@link java.util.Set} object
     * @param z           a {@link java.util.Set} object
     * @return True if x and y are d-connected given z.
     */
    public boolean isMConnectedTo(Set<Node> x, Set<Node> y, Set<Node> z, Map<Node, Set<Node>> ancestorMap) {
        if (ancestorMap == null) throw new NullPointerException("Ancestor map cannot be null.");

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

                boolean ancestor = false;

                for (Node _z : z) {
                    if (ancestorMap.get(_z).contains(b)) {
                        ancestor = true;
                        break;
                    }
                }

                if (!((collider && ancestor) || (!collider && !z.contains(b)))) {
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

    /**
     * <p>getMConnectedVars.</p>
     *
     * @param y a {@link edu.cmu.tetrad.graph.Node} object
     * @param z a {@link java.util.Set} object
     * @return a {@link java.util.Set} object
     */
    public Set<Node> getMConnectedVars(Node y, Set<Node> z) {
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
                if (!(o instanceof EdgeNode _o)) {
                    throw new IllegalArgumentException();
                }
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

    /**
     * <p>getMConnectedVars.</p>
     *
     * @param y         a {@link edu.cmu.tetrad.graph.Node} object
     * @param z         a {@link java.util.Set} object
     * @param ancestors a {@link java.util.Map} object
     * @return a {@link java.util.Set} object
     */
    public Set<Node> getMConnectedVars(Node y, Set<Node> z, Map<Node, Set<Node>> ancestors) {
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
                if (!(o instanceof EdgeNode _o)) {
                    throw new IllegalArgumentException();
                }
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

                if (reachable(edge1, edge2, a, z, ancestors)) {
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

    private boolean reachable(Edge e1, Edge e2, Node a, Set<Node> z) {
        Node b = e1.getDistalNode(a);
        Node c = e2.getDistalNode(b);

        boolean collider = e1.getProximalEndpoint(b) == Endpoint.ARROW && e2.getProximalEndpoint(b) == Endpoint.ARROW;

        if ((!collider || graph.isUnderlineTriple(a, b, c)) && !z.contains(b)) {
            return true;
        }

        boolean ancestor = isAncestor(b, z);
        return collider && ancestor;
    }

    // Return true if b is an ancestor of any node in z
    private boolean reachable(Edge e1, Edge e2, Node a, Set<Node> z, Map<Node, Set<Node>> ancestors) {
        Node b = e1.getDistalNode(a);
        Node c = e2.getDistalNode(b);

        boolean collider = e1.getProximalEndpoint(b) == Endpoint.ARROW && e2.getProximalEndpoint(b) == Endpoint.ARROW;

        boolean ancestor = false;

        for (Node _z : ancestors.get(b)) {
            if (z.contains(_z)) {
                ancestor = true;
                break;
            }
        }

        if ((!collider || graph.isUnderlineTriple(a, b, c)) && !z.contains(b)) {
            return true;
        }

        return collider && ancestor;
    }

    /**
     * Return a map from each node to its ancestors.
     *
     * @return This map.
     */
    public Map<Node, Set<Node>> getAncestorMap() {
        Map<Node, Set<Node>> ancestorsMap = new HashMap<>();

        for (Node node : graph.getNodes()) {
            ancestorsMap.put(node, new HashSet<>());
        }

        for (Node n1 : graph.getNodes()) {
            for (Node n2 : graph.getNodes()) {
                if (isAncestor(n1, Collections.singleton(n2))) {
                    ancestorsMap.get(n1).add(n2);
                }
            }
        }

        return ancestorsMap;
    }

    // Return true if b is an ancestor of any node in z
    private boolean isAncestor(Node b, Set<Node> z) {
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


    private boolean reachable(Node a, Node b, Node c, Set<Node> z) {
        boolean collider = graph.isDefCollider(a, b, c);

        if ((!collider || graph.isUnderlineTriple(a, b, c)) && !z.contains(b)) {
            return true;
        }

        boolean ancestor = isAncestor(b, z);
        return collider && ancestor;
    }


    private List<Node> getPassNodes(Node a, Node b, Set<Node> z) {
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


    private Set<Node> ancestorsOf(Set<Node> z) {
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

    /**
     * Determines whether an inducing path exists between node1 and node2, given a set O of observed nodes and a set sem
     * of conditioned nodes.
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

    /**
     * <p>existsInducingPathVisit.</p>
     *
     * @param a    a {@link edu.cmu.tetrad.graph.Node} object
     * @param b    a {@link edu.cmu.tetrad.graph.Node} object
     * @param x    a {@link edu.cmu.tetrad.graph.Node} object
     * @param y    a {@link edu.cmu.tetrad.graph.Node} object
     * @param path a {@link java.util.LinkedList} object
     * @return a boolean
     */
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

    /**
     * <p>getInducingPath.</p>
     *
     * @param x a {@link edu.cmu.tetrad.graph.Node} object
     * @param y a {@link edu.cmu.tetrad.graph.Node} object
     * @return a {@link java.util.List} object
     */
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

    /**
     * <p>possibleMsep.</p>
     *
     * @param x             a {@link edu.cmu.tetrad.graph.Node} object
     * @param y             a {@link edu.cmu.tetrad.graph.Node} object
     * @param maxPathLength a int
     * @return a {@link java.util.List} object
     */
    public List<Node> possibleMsep(Node x, Node y, int maxPathLength) {
        Set<Node> msep = new HashSet<>();

        Queue<OrderedPair<Node>> Q = new ArrayDeque<>();
        Set<OrderedPair<Node>> V = new HashSet<>();

        Map<Node, Set<Node>> previous = new HashMap<>();
        previous.put(x, new HashSet<>());

        OrderedPair<Node> e = null;
        int distance = 0;

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
            msep.add(b);
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
                msep.add(b);
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

        msep.remove(x);
        msep.remove(y);

        List<Node> _msep = new ArrayList<>(msep);

        Collections.sort(_msep);
        Collections.reverse(_msep);

        return _msep;
    }

    /**
     * Remove edges by the possible m-separation rule.
     *
     * @param test    The independence test to use to remove edges.
     * @param sepsets A sepset map to which sepsets should be added. May be null, in which case sepsets will not be
     *                recorded.
     */
    public void removeByPossibleMsep(IndependenceTest test, SepsetMap sepsets) {
        for (Edge edge : graph.getEdges()) {
            Node a = edge.getNode1();
            Node b = edge.getNode2();

            {
                List<Node> possibleMsep = possibleMsep(a, b, -1);

                SublistGenerator gen = new SublistGenerator(possibleMsep.size(), possibleMsep.size());
                int[] choice;

                while ((choice = gen.next()) != null) {
                    if (choice.length < 2) continue;
                    Set<Node> sepset = GraphUtils.asSet(choice, possibleMsep);
                    if (new HashSet<>(graph.getAdjacentNodes(a)).containsAll(sepset)) continue;
                    if (new HashSet<>(graph.getAdjacentNodes(b)).containsAll(sepset)) continue;
                    if (test.checkIndependence(a, b, sepset).isIndependent()) {
                        graph.removeEdge(edge);

                        if (sepsets != null) {
                            sepsets.set(a, b, sepset);
                        }

                        break;
                    }
                }
            }

            if (graph.containsEdge(edge)) {
                {
                    List<Node> possibleMsep = possibleMsep(b, a, -1);

                    SublistGenerator gen = new SublistGenerator(possibleMsep.size(), possibleMsep.size());
                    int[] choice;

                    while ((choice = gen.next()) != null) {
                        if (choice.length < 2) continue;
                        Set<Node> sepset = GraphUtils.asSet(choice, possibleMsep);
                        if (new HashSet<>(graph.getAdjacentNodes(a)).containsAll(sepset)) continue;
                        if (new HashSet<>(graph.getAdjacentNodes(b)).containsAll(sepset)) continue;
                        if (test.checkIndependence(a, b, sepset).isIndependent()) {
                            graph.removeEdge(edge);

                            if (sepsets != null) {
                                sepsets.set(a, b, sepset);
                            }

                            break;
                        }
                    }
                }
            }
        }
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

            if ((existsSemiDirectedPath(r, x)) || existsSemiDirectedPath(r, b)) {
                return true;
            }
        }

        return false;
    }


    /**
     * Check to see if a set of variables Z satisfies the back-door criterion relative to node x and node y.
     *
     * @param graph a {@link edu.cmu.tetrad.graph.Graph} object
     * @param x     a {@link edu.cmu.tetrad.graph.Node} object
     * @param y     a {@link edu.cmu.tetrad.graph.Node} object
     * @param z     a {@link java.util.Set} object
     * @return a boolean
     * @author Kevin V. Bui (March 2020)
     */
    public boolean isSatisfyBackDoorCriterion(Graph graph, Node x, Node y, Set<Node> z) {
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

        return dag.paths().isMSeparatedFrom(x, y, z);
    }

    // Finds a sepset for x and y, if there is one; otherwise, returns null.

    /**
     * <p>getSepset.</p>
     *
     * @param x a {@link edu.cmu.tetrad.graph.Node} object
     * @param y a {@link edu.cmu.tetrad.graph.Node} object
     * @return a {@link java.util.Set} object
     */
    public Set<Node> getSepset(Node x, Node y) {
        Set<Node> sepset = getSepsetVisit(x, y);
        if (sepset == null) {
            sepset = getSepsetVisit(y, x);
        }
        return sepset;
    }

    private Set<Node> getSepsetVisit(Node x, Node y) {
        if (x == y) {
            return null;
        }

        Set<Node> z = new HashSet<>();

        Set<Node> _z;

        do {
            _z = new HashSet<>(z);

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

    private boolean sepsetPathFound(Node a, Node b, Node y, Set<Node> path, Set<Node> z, Set<Triple> colliders, int bound) {
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

    /**
     * Detemrmines whether x and y are d-connected given z.
     *
     * @param x a {@link edu.cmu.tetrad.graph.Node} object
     * @param y a {@link edu.cmu.tetrad.graph.Node} object
     * @param z a {@link java.util.Set} object
     * @return true if x and y are d-connected given z; false otherwise.
     */
    public boolean isMConnectedTo(Node x, Node y, Set<Node> z) {
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
                if (!(o instanceof EdgeNode _o)) {
                    throw new IllegalArgumentException();
                }
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
     * Detemrmines whether x and y are d-connected given z.
     *
     * @param x         a {@link edu.cmu.tetrad.graph.Node} object
     * @param y         a {@link edu.cmu.tetrad.graph.Node} object
     * @param z         a {@link java.util.Set} object
     * @param ancestors a {@link java.util.Map} object
     * @return true if x and y are d-connected given z; false otherwise.
     */
    public boolean isMConnectedTo(Node x, Node y, Set<Node> z, Map<Node, Set<Node>> ancestors) {
        class EdgeNode {

            private final Edge edge;
            private final Node node;

            public EdgeNode(Edge edge, Node node) {
                this.edge = edge;
                this.node = node;
            }

            public int hashCode() {
                return this.edge.hashCode() + 5 * this.node.hashCode();
            }

            public boolean equals(Object o) {
                if (!(o instanceof EdgeNode _o)) {
                    throw new IllegalArgumentException();
                }
                return _o.edge.equals(this.edge) && _o.node.equals(this.node);
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

                if (reachable(edge1, edge2, a, z, ancestors)) {
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
     * @param edge a {@link edu.cmu.tetrad.graph.Edge} object
     * @return true if the given edge is definitely visible (Jiji, pg 25)
     * @throws java.lang.IllegalArgumentException if the given edge is not a directed edge in the graph
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

            return visibleEdgeHelper(A, B);
        } else {
            throw new IllegalArgumentException(
                    "Given edge is not in the graph.");
        }
    }

    private boolean visibleEdgeHelper(Node A, Node B) {
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

            if (visibleEdgeHelperVisit(C, A, B, path)) {
                return true;
            }
        }

        return false;
    }

    private boolean visibleEdgeHelperVisit(Node c, Node a, Node b, LinkedList<Node> path) {
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

            if (visibleEdgeHelperVisit(D, c, b, path)) {
                return true;
            }
        }

        path.removeLast();
        return false;
    }

    /**
     * <p>existsDirectedCycle.</p>
     *
     * @return a boolean
     */
    public boolean existsDirectedCycle() {
        for (Node node : graph.getNodes()) {
            if (existsDirectedPathFromTo(node, node)) {
                TetradLogger.getInstance().forceLogMessage("Cycle found at node " + node.getName() + ".");
                return true;
            }
        }
        return false;
    }

    /**
     * <p>existsDirectedPathFromTo.</p>
     *
     * @param node1 a {@link edu.cmu.tetrad.graph.Node} object
     * @param node2 a {@link edu.cmu.tetrad.graph.Node} object
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

    /**
     * <p>existsSemiDirectedPath.</p>
     *
     * @param node1 a {@link edu.cmu.tetrad.graph.Node} object
     * @param nodes a {@link java.util.Set} object
     * @return a boolean
     */
    public boolean existsSemiDirectedPath(Node node1, Set<Node> nodes) {
        return existsSemiDirectedPathVisit(node1, nodes, new LinkedList<>());
    }

    /**
     * Determines whether a trek exists between two nodes in the graph. A trek exists if there is a directed path
     * between the two nodes or else, for some third node in the graph, there is a path to each of the two nodes in
     * question.
     *
     * @param node1 a {@link edu.cmu.tetrad.graph.Node} object
     * @param node2 a {@link edu.cmu.tetrad.graph.Node} object
     * @return a boolean
     */
    public boolean existsTrek(Node node1, Node node2) {
        for (Node node : graph.getNodes()) {
            if (isAncestorOf((node), node1) && isAncestorOf((node), node2)) {
                return true;
            }
        }

        return false;
    }

    /**
     * <p>getDescendants.</p>
     *
     * @param nodes a {@link java.util.List} object
     * @return a {@link java.util.List} object
     */
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
     *
     * @param node1 a {@link edu.cmu.tetrad.graph.Node} object
     * @param node2 a {@link edu.cmu.tetrad.graph.Node} object
     * @return a boolean
     */
    public boolean isAncestorOf(Node node1, Node node2) {
        return node1 == node2 || existsDirectedPathFromTo(node1, node2);
    }

    /**
     * <p>getAncestors.</p>
     *
     * @param nodes a {@link java.util.List} object
     * @return a {@link java.util.List} object
     */
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
     *
     * @param node1 a {@link edu.cmu.tetrad.graph.Node} object
     * @param node2 a {@link edu.cmu.tetrad.graph.Node} object
     * @return a boolean
     */
    public boolean isDescendentOf(Node node1, Node node2) {
        return node1 == node2 || existsDirectedPathFromTo(node2, node1);
    }

    /**
     * added by ekorber, 2004/06/12
     *
     * @param node1 a {@link edu.cmu.tetrad.graph.Node} object
     * @param node2 a {@link edu.cmu.tetrad.graph.Node} object
     * @return true iff node2 is a definite nondecendent of node1
     */
    public boolean definiteNonDescendent(Node node1, Node node2) {
        return !(possibleAncestor(node1, node2));
    }

    /**
     * Determines whether one n ode is d-separated from another. According to Spirtes, Richardson and Meek, two nodes
     * are d- connected given some conditioning set Z if there is an acyclic undirected path U between them, such that
     * every collider on U is an ancestor of some element in Z and every non-collider on U is not in Z. Two elements are
     * d-separated just in case they are not d-connected. A collider is a node which two edges hold in common for which
     * the endpoints leading into the node are both arrow endpoints.
     *
     * @param node1 the first node.
     * @param node2 the second node.
     * @param z     the conditioning set.
     * @return true if node1 is d-separated from node2 given set t, false if not.
     * @see #isMConnectedTo
     */
    public boolean isMSeparatedFrom(Node node1, Node node2, Set<Node> z) {
        return !isMConnectedTo(node1, node2, z);
    }

    /**
     * <p>isMSeparatedFrom.</p>
     *
     * @param node1     a {@link edu.cmu.tetrad.graph.Node} object
     * @param node2     a {@link edu.cmu.tetrad.graph.Node} object
     * @param z         a {@link java.util.Set} object
     * @param ancestors a {@link java.util.Map} object
     * @return a boolean
     */
    public boolean isMSeparatedFrom(Node node1, Node node2, Set<Node> z, Map<Node, Set<Node>> ancestors) {
        return !isMConnectedTo(node1, node2, z, ancestors);
    }

    /**
     * @return true iff there is a semi-directed path from node1 to node2
     */
    private boolean existsSemiDirectedPathVisit(Node node1, Set<Node> nodes2, LinkedList<Node> path) {
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

    /**
     * <p>isDirectedFromTo.</p>
     *
     * @param node1 a {@link edu.cmu.tetrad.graph.Node} object
     * @param node2 a {@link edu.cmu.tetrad.graph.Node} object
     * @return a boolean
     */
    public boolean isDirectedFromTo(Node node1, Node node2) {
        List<Edge> edges = graph.getEdges(node1, node2);
        if (edges.size() != 1) {
            return false;
        }
        Edge edge = edges.iterator().next();
        return edge.pointsTowards(node2);
    }

    /**
     * <p>isUndirectedFromTo.</p>
     *
     * @param node1 a {@link edu.cmu.tetrad.graph.Node} object
     * @param node2 a {@link edu.cmu.tetrad.graph.Node} object
     * @return a boolean
     */
    public boolean isUndirectedFromTo(Node node1, Node node2) {
        Edge edge = graph.getEdge(node1, node2);
        return edge != null && edge.getEndpoint1() == Endpoint.TAIL && edge.getEndpoint2() == Endpoint.TAIL;
    }

    /**
     * <p>possibleAncestor.</p>
     *
     * @param node1 a {@link edu.cmu.tetrad.graph.Node} object
     * @param node2 a {@link edu.cmu.tetrad.graph.Node} object
     * @return a boolean
     */
    public boolean possibleAncestor(Node node1, Node node2) {
        return existsSemiDirectedPath(node1, Collections.singleton(node2));
    }

    public static class AllCliquesAlgorithm {

        public static void main(String[] args) {
            int[][] graph = {
                    {0, 1, 1, 0, 0},
                    {1, 0, 1, 1, 0},
                    {1, 1, 0, 1, 1},
                    {0, 1, 1, 0, 1},
                    {0, 0, 1, 1, 0}
            };
            int n = graph.length;

            List<List<Integer>> cliques = findCliques(graph, n);
            System.out.println("All Cliques:");
            for (List<Integer> clique : cliques) {
                System.out.println(clique);
            }
        }

        public static List<List<Integer>> findCliques(int[][] graph, int n) {
            List<List<Integer>> cliques = new ArrayList<>();
            Set<Integer> candidates = new HashSet<>();
            Set<Integer> excluded = new HashSet<>();
            Set<Integer> included = new HashSet<>();

            for (int i = 0; i < n; i++) {
                candidates.add(i);
            }

            bronKerbosch(graph, candidates, excluded, included, cliques);

            return cliques;
        }

        private static void bronKerbosch(int[][] graph, Set<Integer> candidates,
                                         Set<Integer> excluded, Set<Integer> included,
                                         List<List<Integer>> cliques) {
            if (candidates.isEmpty() && excluded.isEmpty()) {
                cliques.add(new ArrayList<>(included));
                return;
            }

            Set<Integer> candidatesCopy = new HashSet<>(candidates);
            for (int vertex : candidatesCopy) {
                Set<Integer> neighbors = new HashSet<>();
                for (int i = 0; i < graph.length; i++) {
                    if (graph[vertex][i] == 1 && candidates.contains(i)) {
                        neighbors.add(i);
                    }
                }

                bronKerbosch(graph, intersect(candidates, neighbors),
                        intersect(excluded, neighbors),
                        union(included, vertex),
                        cliques);

                candidates.remove(vertex);
                excluded.add(vertex);
            }
        }

        private static Set<Integer> intersect(Set<Integer> set1, Set<Integer> set2) {
            Set<Integer> result = new HashSet<>(set1);
            result.retainAll(set2);
            return result;
        }

        private static Set<Integer> union(Set<Integer> set, int element) {
            Set<Integer> result = new HashSet<>(set);
            result.add(element);
            return result;
        }
    }
}

