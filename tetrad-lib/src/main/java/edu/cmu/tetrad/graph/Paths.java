package edu.cmu.tetrad.graph;

import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.utils.*;
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

    /**
     * Adds a node to a set within the given map. If there is no set associated with the specified node, a new set is
     * created and added to the map.
     *
     * @param previous The map containing sets of nodes.
     * @param b        The node to be added to the set.
     * @param c        The node associated with the set in the map.
     */
    private static void addToSet(Map<Node, Set<Node>> previous, Node b, Node c) {
        previous.computeIfAbsent(c, k -> new HashSet<>());
        Set<Node> list = previous.get(c);
        list.add(b);
    }

    /**
     * Get the prefix for a list of nodes up to a specified index.
     *
     * @param pi The list of nodes.
     * @param i  The index up to which to include nodes in the prefix.
     * @return A set of nodes representing the prefix.
     */
    private static Set<Node> getPrefix(List<Node> pi, int i) {
        Set<Node> prefix = new HashSet<>();

        for (int j = 0; j < i; j++) {
            prefix.add(pi.get(j));
        }

        return prefix;
    }

    /**
     * Generates a directed acyclic graph (DAG) based on the given list of nodes using Raskutti and Uhler's method.
     *
     * @param pi      a list of nodes representing the set of vertices in the graph
     * @param g       the graph
     * @param verbose whether to print verbose output
     * @return a Graph object representing the generated DAG.
     */
    public static Graph getDag(List<Node> pi, Graph g, boolean verbose) {
        Graph graph = new EdgeListGraph(pi);

        for (int a = 0; a < pi.size(); a++) {
            for (Node b : getParents(pi, a, g, verbose, false)) {
                graph.addDirectedEdge(b, pi.get(a));
            }
        }

        return graph;
    }

    /**
     * Returns the parents of the node at index p, calculated using Pearl's method.
     *
     * @param pi                 The list of nodes.
     * @param p                  The index.
     * @param g                  The graph.
     * @param verbose            Whether to print verbose output.
     * @param allowSelectionBias whether to allow selection bias; if true, then undirected edges X--Y are uniformly
     *                           treated as X-&gt;L&lt;-Y.
     * @return The parents, as a Pair object (parents + score).
     */
    public static Set<Node> getParents(List<Node> pi, int p, Graph g, boolean verbose, boolean allowSelectionBias) {
        Node x = pi.get(p);
        Set<Node> parents = new HashSet<>();
        Set<Node> prefix = getPrefix(pi, p);

        for (Node y : prefix) {
            Set<Node> minus = new HashSet<>(prefix);
            minus.remove(y);
            minus.remove(x);
            Set<Node> z = new HashSet<>(minus);

            if (!g.paths().isMSeparatedFrom(x, y, z, allowSelectionBias)) {
                if (verbose) {
                    System.out.println("Adding " + y + " as a parent of " + x + " with z = " + z);
                }
                parents.add(y);
            }
        }

        return parents;
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
     * Reorders the given order into a valid causal order for either a DAG or a CPDAG. (bryanandrews)
     *
     * @param order Variables in the order will be kept as close to this initial order as possible, either the forward
     *              order or the reverse order, depending on the next parameter.
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
                else
                    throw new IllegalArgumentException("The remaining graph does not have valid sink; there " + "could be a directed cycle or a non-chordal undirected cycle.");
            } while (invalidSink(x, _graph));
            order.add(x);
            _graph.removeNode(x);
            itr.remove();
        }

        Collections.reverse(order);
    }

    /**
     * The variable x is a valid sink if it has no children and its neighbors x--z form a clique; otherwise it is an
     * invalid sink.
     *
     * @param x     The node to test.
     * @param graph The graph to test.
     * @return true if invalid, false if valid.
     */
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
     * Checks if the graph passed as parameter is a legal directed acyclic graph (DAG).
     *
     * @return true if the graph is a legal DAG, false otherwise.
     */
    public boolean isLegalDag() {
        return GraphUtils.isDag(graph);
    }

    /**
     * Checks if the current graph is a legal CPDAG (completed partially directed acyclic graph).
     *
     * @return true if the graph is a legal CPDAG, false otherwise.
     */
    public synchronized boolean isLegalCpdag() {
        Graph g = this.graph;

        for (Edge e : g.getEdges()) {
            if (!(Edges.isDirectedEdge(e) || Edges.isUndirectedEdge(e))) {
                return false;
            }
        }

        List<Node> pi = new ArrayList<>(g.getNodes());

        try {
            g.paths().makeValidOrder(pi);
            Graph dag = getDag(pi, g/*GraphTransforms.dagFromCpdag(g)*/, false);
            Graph cpdag = GraphTransforms.dagToCpdag(dag);
            return g.equals(cpdag);
        } catch (Exception e) {
            // There was no valid sink.
            System.out.println(e.getMessage());
            return false;
        }
    }

    /**
     * Checks if the given graph is a legal Maximal Partial Directed Acyclic Graph (MPDAG). A MPDAG is considered legal
     * if it is equal to a CPDAG where additional edges have been oriented by Knowledge, with Meek rules applied for
     * maximum orientation. The test is performed by attemping to convert the graph to a CPDAG using the DAG to CPDAG
     * transformation and testing whether that graph is a legal CPDAG. Finally, we test to see whether the obtained
     * graph is equal to the original graph.
     *
     * @return true if the MPDAG is legal, false otherwise.
     */
    public boolean isLegalMpdag() {
        Graph g = this.graph;

        for (Edge e : g.getEdges()) {
            if (!(Edges.isDirectedEdge(e) || Edges.isUndirectedEdge(e))) {
                return false;
            }
        }

        List<Node> pi = new ArrayList<>(g.getNodes());

        try {
            g.paths().makeValidOrder(pi);
            Graph dag = getDag(pi, g, false);
            Graph cpdag = GraphTransforms.dagToCpdag(dag);
            Graph _g = new EdgeListGraph(g);
            _g = GraphTransforms.dagToCpdag(_g);

            boolean equals = _g.equals(cpdag);

            // Check maximality...
            if (equals) {
                Graph __g = new EdgeListGraph(g);
                MeekRules meekRules = new MeekRules();
                meekRules.setRevertToUnshieldedColliders(false);
                meekRules.orientImplied(__g);
                return g.equals(__g);
            }

            return false;
        } catch (Exception e) {
            // There was no valid sink.
            System.out.println(e.getMessage());
            return false;
        }
    }

    /**
     * Checks if the given Maximal Ancestral Graph (MPAG) is legal. A MPAG is considered legal if it is equal to a PAG
     * where additional edges have been oriented by Knowledge, with final FCI rules applied for maximum orientation. The
     * test is performed by attemping to convert the graph to a PAG using the DAG to CPDAG transformation and testing
     * whether that graph is a legal PAG. Finally, we test to see whether the obtained graph is equal to the original
     * graph.
     * <p>
     * The user may choose to use the rules from Zhang (2008) or the rules from Spirtes et al. (2000).
     *
     * @return true if the MPDAG is legal, false otherwise.
     */
    public boolean isLegalMpag() {
        Graph g = this.graph;

        try {
            Graph pag = GraphTransforms.dagToPag(g);

            if (pag.paths().isLegalPag()) {
                Graph __g = new DagToPag(graph).convert();

                if (__g.paths().isLegalPag()) {
                    Graph _g = new EdgeListGraph(g);
                    FciOrient fciOrient = new FciOrient(new DagSepsets(_g));
                    fciOrient.zhangFinalOrientation(_g);
                    return g.equals(_g);
                }
            }

            return false;
        } catch (Exception e) {
            // There was no valid sink.
            System.out.println(e.getMessage());
            return false;
        }
    }

    /**
     * Checks if the given graph is a legal mag.
     *
     * @return true if the graph is a legal mag, false otherwise
     */
    public boolean isLegalMag() {
        return GraphSearchUtils.isLegalMag(graph).isLegalMag();
    }

    /**
     * Checks if the given Directed Acyclic Graph (DAG) is a Legal Partial Ancestral Graph (PAG).
     *
     * @return true if the graph is a Legal PAG, false otherwise
     */
    public boolean isLegalPag() {
        return GraphSearchUtils.isLegalPag(graph).isLegalPag();
    }

    /**
     * Returns a set of all maximum cliques in the graph.
     *
     * @return a set of sets of nodes representing the maximum cliques in the graph
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
     * Returns a list of connected components in the graph.
     *
     * @return A list of connected components, where each component is represented as a list of nodes.
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
     * Finds all directed paths from node1 to node2 with a maximum length.
     *
     * @param node1     the starting node
     * @param node2     the destination node
     * @param maxLength the maximum length of the paths
     * @return a list of lists containing the directed paths from node1 to node2
     */
    public List<List<Node>> directedPaths(Node node1, Node node2, int maxLength) {
        List<List<Node>> paths = new LinkedList<>();
        directedPaths(node1, node2, new LinkedList<>(), paths, maxLength);
        return paths;
    }

    private void directedPaths(Node node1, Node node2, LinkedList<Node> path, List<List<Node>> paths, int maxLength) {
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

            directedPaths(child, node2, path, paths, maxLength);
        }

        path.removeLast();
    }

    /**
     * Finds all semi-directed paths between two nodes up to a maximum length.
     *
     * @param node1     the starting node
     * @param node2     the ending node
     * @param maxLength the maximum path length
     * @return a list of all semi-directed paths between the two nodes
     */
    public List<List<Node>> semidirectedPaths(Node node1, Node node2, int maxLength) {
        List<List<Node>> paths = new LinkedList<>();
        semidirectedPathsVisit(node1, node2, new LinkedList<>(), paths, maxLength);
        return paths;
    }

    private void semidirectedPathsVisit(Node node1, Node node2, LinkedList<Node> path, List<List<Node>> paths, int maxLength) {
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

            semidirectedPathsVisit(child, node2, path, paths, maxLength);
        }

        path.removeLast();
    }

    /**
     * Finds all paths from node1 to node2 within a specified maximum length.
     *
     * @param node1     The starting node.
     * @param node2     The target node.
     * @param maxLength The maximum length of the paths.
     * @return A list of paths, where each path is a list of nodes.
     */
    public List<List<Node>> allPaths(Node node1, Node node2, int maxLength) {
        List<List<Node>> paths = new LinkedList<>();
        allPathsVisit(node1, node2, new LinkedList<>(), paths, maxLength);
        return paths;
    }

    private void allPathsVisit(Node node1, Node node2, LinkedList<Node> path, List<List<Node>> paths, int maxLength) {
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

            allPathsVisit(child, node2, path, paths, maxLength);
        }

        path.removeLast();
    }

    /**
     * Finds all directed paths from node1 to node2 with a maximum length.
     *
     * @param node1     The starting node.
     * @param node2     The target node.
     * @param maxLength The maximum length of the paths.
     * @return A list of lists of nodes representing the directed paths from node1 to node2.
     */
    public List<List<Node>> allDirectedPaths(Node node1, Node node2, int maxLength) {
        List<List<Node>> paths = new LinkedList<>();
        allDirectedPathsVisit(node1, node2, new LinkedList<>(), paths, maxLength);
        return paths;
    }

    private void allDirectedPathsVisit(Node node1, Node node2, LinkedList<Node> path, List<List<Node>> paths, int maxLength) {
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

            allDirectedPathsVisit(child, node2, path, paths, maxLength);
        }

        path.removeLast();
    }

    /**
     * Finds all treks from node1 to node2 with a maximum length.
     *
     * @param node1     the starting node
     * @param node2     the destination node
     * @param maxLength the maximum length of the treks
     * @return a list of lists of nodes representing each trek from node1 to node2
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
     * Finds all possible treks between two nodes, including bidirectional treks.
     *
     * @param node1 The starting node.
     * @param node2 The ending node.
     * @return A List of Lists representing all treks between the given nodes.
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
     * Checks if a directed path exists between two nodes within a certain depth.
     *
     * @param node1 the first node in the path
     * @param node2 the second node in the path
     * @param depth the maximum depth to search for the path
     * @return true if a directed path exists between the two nodes within the given depth, false otherwise
     */
    public boolean existsDirectedPath(Node node1, Node node2, int depth) {
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
     * Retrieves the set of nodes that are connected to the given node {@code y} and are also present in the set of
     * nodes {@code z}.
     *
     * @param y The node for which to find the connected nodes.
     * @param z The set of nodes to be considered for connecting nodes.
     * @return The set of nodes that are connected to {@code y} and present in {@code z}.
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

        if (collider && ancestor) {
            return true;
        }

        return false;
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
     * Check to see if a set of variables Z satisfies the back-door criterion relative to node x and node y. (author
     * Kevin V. Bui (March 2020).
     *
     * @param graph a {@link edu.cmu.tetrad.graph.Graph} object
     * @param x     a {@link edu.cmu.tetrad.graph.Node} object
     * @param y     a {@link edu.cmu.tetrad.graph.Node} object
     * @param z     a {@link java.util.Set} object
     * @return a boolean
     */
    public boolean isSatisfyBackDoorCriterion(Graph graph, Node x, Node y, Set<Node> z) {
        Dag dag = new Dag(graph);

        // make sure no nodes in z is a descendant of x
        if (z.stream().anyMatch(zNode -> dag.paths().isDescendentOf(zNode, x))) {
            return false;
        }


        // make sure zNodes bock every path between node x and node y that contains an arrow into node x
        List<List<Node>> directedPaths = allDirectedPaths(x, y, -1);
        directedPaths.forEach(nodes -> {
            // remove all variables that are not on the back-door path
            nodes.forEach(node -> {
                if (!(node == x || node == y)) {
                    dag.removeNode(node);
                }
            });
        });

        return dag.paths().isMSeparatedFrom(x, y, z, false);
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
     * @param x                  a {@link Node} object
     * @param y                  a {@link Node} object
     * @param z                  a {@link Set} object
     * @param allowSelectionBias whether to allow selection bias; if true, then undirected edges X--Y are uniformly
     *                           treated as X-&gt;L&lt;-Y.
     * @return true if x and y are d-connected given z; false otherwise.
     */
    public boolean isMConnectedTo(Node x, Node y, Set<Node> z, boolean allowSelectionBias) {
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

                    // If in a CPDAG we have X->Y--Z<-W, reachability can't determine that the path should be
                    // blocked now matter which way Y--Z is oriented, so we need to make a choice. Choosing Y->Z
                    // works for cyclic directed graphs and for PAGs except where X->Y with no circle at X,
                    // in which case Y--Z should be interpreted as selection bias. This is a limitation of the
                    // reachability algorithm here. The problem is that Y--Z is interpreted differently for CPDAGs
                    // than for PAGs, and we are trying to make an m-connection procedure that works for both.
                    // Simply knowing whether selection bias is being allowed is sufficient to make the right choice.
                    // jdramsey 2024-04-14
                    if (!allowSelectionBias && Edges.isDirectedEdge(edge1) && edge1.pointsTowards(b) && Edges.isUndirectedEdge(edge2)) {
                        edge2 = Edges.directedEdge(b, edge2.getDistalNode(b));
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
     * @param x                  a {@link Node} object
     * @param y                  a {@link Node} object
     * @param z                  a {@link Set} object
     * @param ancestors          a {@link Map} object
     * @param allowSelectionBias whether to allow selection bias; if true, then undirected edges X--Y are uniformly
     *                           treated as X-&gt;L&lt;-Y.
     * @return true if x and y are d-connected given z; false otherwise.
     */
    public boolean isMConnectedTo(Node x, Node y, Set<Node> z, Map<Node, Set<Node>> ancestors, boolean allowSelectionBias) {
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

                    // If in a CPDAG we have X->Y--Z<-W, reachability can't determine that the path should be
                    // blocked now matter which way Y--Z is oriented, so we need to make a choice. Choosing Y->Z
                    // works for cyclic directed graphs and for PAGs except where X->Y with no circle at X,
                    // in which case Y--Z should be interpreted as selection bias. This is a limitation of the
                    // reachability algorithm here. The problem is that Y--Z is interpreted differently for CPDAGs
                    // than for PAGs, and we are trying to make an m-connection procedure that works for both.
                    // Simply knowing whether selection bias is being allowed is sufficient to make the right choice.
                    // jdramsey 2024-04-14
                    if (!allowSelectionBias && Edges.isDirectedEdge(edge1) && edge1.pointsTowards(b) && Edges.isUndirectedEdge(edge2)) {
                        edge2 = Edges.directedEdge(b, edge2.getDistalNode(b));
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
            throw new IllegalArgumentException("Given edge is not in the graph.");
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
            if (existsDirectedPath(node, node)) {
                TetradLogger.getInstance().forceLogMessage("Cycle found at node " + node.getName() + ".");
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if a directed path exists between two nodes in a graph.
     *
     * @param node1 the starting node of the path
     * @param node2 the target node of the path
     * @return true if a directed path exists from node1 to node2, false otherwise
     */
    public boolean existsDirectedPath(Node node1, Node node2) {
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
     * Returns a list of all descendants of the given node.
     *
     * @param node The node for which to find descendants.
     * @return A list of all descendant nodes.
     */
    public Set<Node> getDescendants(Node node) {
        Set<Node> descendants = new HashSet<>();

        for (Node n : graph.getNodes()) {
            if (isDescendentOf(n, node)) {
                descendants.add(n);
            }
        }

        return descendants;
    }

    /**
     * Retrieves the descendants of the given list of nodes.
     *
     * @param nodes The list of nodes to find descendants for.
     * @return A list of nodes that are descendants of the given nodes.
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
        return node1 == node2 || existsDirectedPath(node1, node2);
    }

    /**
     * Retrieves the ancestors of a specified `Node` in the graph.
     *
     * @param node The node whose ancestors are to be retrieved.
     * @return A list of ancestors for the specified `Node`.
     */
    public List<Node> getAncestors(Node node) {
        Set<Node> ancestors = new HashSet<>();

        for (Node n : graph.getNodes()) {
            if (isAncestorOf(n, node)) {
                ancestors.add(n);
            }
        }

        return new ArrayList<>(ancestors);
    }

    /**
     * Returns a list of all ancestors of the given nodes.
     *
     * @param nodes the list of nodes for which to find ancestors
     * @return a list containing all the ancestors of the given nodes
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
        return node1 == node2 || existsDirectedPath(node2, node1);
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
     * <p>
     * Precondition: This graph is a DAG. Please don't violate this constraint; weird things can happen!
     *
     * @param node1              the first node.
     * @param node2              the second node.
     * @param z                  the conditioning set.
     * @param allowSelectionBias whether to allow selection bias; if true, then undirected edges X--Y are uniformly
     *                           treated as X-&gt;L&lt;-Y.
     * @return true if node1 is d-separated from node2 given set t, false if not.
     */
    public boolean isMSeparatedFrom(Node node1, Node node2, Set<Node> z, boolean allowSelectionBias) {
        return !isMConnectedTo(node1, node2, z, allowSelectionBias);
    }

    /**
     * Checks if two nodes are M-separated.
     *
     * @param node1              The first node.
     * @param node2              The second node.
     * @param z                  The set of nodes to be excluded from the path.
     * @param ancestors          A map containing the ancestors of each node.
     * @param allowSelectionBias whether to allow selection bias; if true, then undirected edges X--Y are uniformly
     *                           treated as X-&gt;L&lt;-Y.
     * @return {@code true} if the two nodes are M-separated, {@code false} otherwise.
     */
    public boolean isMSeparatedFrom(Node node1, Node node2, Set<Node> z, Map<Node, Set<Node>> ancestors, boolean allowSelectionBias) {
        return !isMConnectedTo(node1, node2, z, ancestors, allowSelectionBias);
    }

    /**
     * Checks if a semi-directed path exists between the given node and any of the nodes in the provided set.
     *
     * @param node1  The starting node for the path.
     * @param nodes2 The set of nodes to check for a path.
     * @param path   The current path (used for cycle detection).
     * @return {@code true} if a semi-directed path exists, {@code false} otherwise.
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
     * Checks if there is a directed edge from node1 to node2 in the graph.
     *
     * @param node1 the source node
     * @param node2 the destination node
     * @return true if there is a directed edge from node1 to node2, false otherwise
     */
    public boolean isDirected(Node node1, Node node2) {
        List<Edge> edges = graph.getEdges(node1, node2);
        if (edges.size() != 1) {
            return false;
        }
        Edge edge = edges.iterator().next();
        return edge.pointsTowards(node2);
    }

    /**
     * Checks if the edge between two nodes in the graph is undirected.
     *
     * @param node1 the first node
     * @param node2 the second node
     * @return true if the edge is undirected, false otherwise
     */
    public boolean isUndirected(Node node1, Node node2) {
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

    /**
     * Returns the set of nodes that are in the anteriority of the given nodes in the graph.
     *
     * @param X the nodes for which the anteriority needs to be determined
     * @return the set of nodes in the anteriority of the given nodes
     */
    public Set<Node> anteriority(Node... X) {
        return GraphUtils.anteriority(graph, X);
    }

    /**
     * An algorithm to find all cliques in a graph.
     */
    public static class AllCliquesAlgorithm {

        /**
         * Private constructor to prevent instantiation.
         */
        private AllCliquesAlgorithm() {

        }

        /**
         * Main method.
         *
         * @param args the command-line arguments
         */
        public static void main(String[] args) {
            int[][] graph = {{0, 1, 1, 0, 0}, {1, 0, 1, 1, 0}, {1, 1, 0, 1, 1}, {0, 1, 1, 0, 1}, {0, 0, 1, 1, 0}};
            int n = graph.length;

            List<List<Integer>> cliques = findCliques(graph, n);
            System.out.println("All Cliques:");
            for (List<Integer> clique : cliques) {
                System.out.println(clique);
            }
        }

        /**
         * Find all cliques in a graph.
         *
         * @param graph the graph
         * @param n     the number of vertices in the graph
         * @return a list of cliques
         */
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

        private static void bronKerbosch(int[][] graph, Set<Integer> candidates, Set<Integer> excluded, Set<Integer> included, List<List<Integer>> cliques) {
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

                bronKerbosch(graph, intersect(candidates, neighbors), intersect(excluded, neighbors), union(included, vertex), cliques);

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

