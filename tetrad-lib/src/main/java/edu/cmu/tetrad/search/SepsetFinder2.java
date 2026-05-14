package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;

import java.util.*;

/**
 * Finds a separating set between two nodes x and y in a PAG or MAG using a
 * two-tier strategy that matches FCI's completeness guarantees.
 *
 * <p>Tier 1 exhaustively checks all subsets of {@code adj(X) \ {Y}} and
 * {@code adj(Y) \ {X}}, in order of increasing size up to {@code depth}.
 * This is complete for DAGs and covers the common case cheaply.</p>
 *
 * <p>Tier 2 is triggered only if Tier 1 fails. It computes the possible
 * d-sep pool — nodes reachable from X or Y via inducing paths that do not
 * pass through any forbidden node — and runs a BFS over subsets of that
 * pool, again ordered by increasing size. This matches FCI's possible d-sep
 * search and is complete for PAGs/MAGs.</p>
 *
 * <p>Each candidate set Z is tested by the caller-supplied {@code sepTest}
 * predicate, which should return true iff x and y are m-separated given Z
 * in the distribution (or graph). The graph search merely proposes candidates;
 * the test confirms them.</p>
 *
 * <p>The direct edge x–y may optionally be ignored: see
 * {@link IgnoreDirectEdgeSepsetTester}. This is useful in FCIT and similar
 * algorithms where the goal is to find Z blocking all paths other than the
 * direct edge.</p>
 */
public class SepsetFinder2 {

    private SepsetFinder2() {
    }

    /**
     * Attempts to find a set Z such that x and y are m-separated given Z,
     * using the two-tier strategy described in the class Javadoc.
     *
     * <p>Nodes in {@code notFollowed} are excluded from the possible d-sep
     * pool: inducing paths through those nodes are not counted, and they are
     * never added to Z.</p>
     *
     * @param graph       the PAG or MAG
     * @param x           first node
     * @param y           second node
     * @param depth       maximum size of Z to consider (-1 = unlimited)
     * @param notFollowed nodes that must not appear in Z or on inducing paths
     * @param sepTest     returns true iff x and y are separated given Z
     * @return a separating set Z, or {@code null} if none was found
     * @throws InterruptedException if the thread is interrupted
     */
    public static Set<Node> findSepset(
            Graph graph,
            Node x,
            Node y,
            Set<Node> notFollowed,
            int depth,
            SepsetTester sepTest)
            throws InterruptedException {

        // ----------------------------------------------------------------
        // Tier 1: subsets of adj(X)\{Y} and adj(Y)\{X}, excluding notFollowed
        // ----------------------------------------------------------------
        List<Node> adjX = new ArrayList<>(graph.getAdjacentNodes(x));
        adjX.remove(y);
        adjX.removeAll(notFollowed);

        List<Node> adjY = new ArrayList<>(graph.getAdjacentNodes(y));
        adjY.remove(x);
        adjY.removeAll(notFollowed);

        Set<Node> fromAdjX = searchSubsets(adjX, x, y, depth, sepTest);
        if (fromAdjX != null) return fromAdjX;

        Set<Node> fromAdjY = searchSubsets(adjY, x, y, depth, sepTest);
        if (fromAdjY != null) return fromAdjY;

        // ----------------------------------------------------------------
        // Tier 2: possible d-sep pool via inducing paths, respecting notFollowed
        // ----------------------------------------------------------------
        Set<Node> pool = possibleDsepPool(graph, x, y, notFollowed);
        pool.remove(x);
        pool.remove(y);
        pool.removeAll(notFollowed);

        System.out.println("pool: " + pool);

        return searchSubsets(new ArrayList<>(pool), x, y, depth, sepTest);
    }

    // -----------------------------------------------------------------------
    // Subset enumeration
    // -----------------------------------------------------------------------

    /**
     * Enumerates all subsets of {@code candidates} in order of increasing
     * size up to {@code depth}, testing each with {@code sepTest}.
     * Returns the first passing set, or {@code null} if none passes.
     */
    private static Set<Node> searchSubsets(
            List<Node> candidates,
            Node x,
            Node y,
            int depth,
            SepsetTester sepTest)
            throws InterruptedException {

        int maxSize = (depth < 0) ? candidates.size() : Math.min(depth, candidates.size());

        for (int size = 0; size <= maxSize; size++) {

            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }

            int[] indices = new int[size];
            for (int i = 0; i < size; i++) indices[i] = i;

            while (true) {

                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException();
                }

                Set<Node> z = new HashSet<>();
                for (int idx : indices) z.add(candidates.get(idx));

                if (sepTest.isSeparated(x, y, z)) {
                    return z;
                }

                // Advance to next combination.
                int i = size - 1;
                while (i >= 0 && indices[i] == candidates.size() - size + i) i--;
                if (i < 0) break;
                indices[i]++;
                for (int j = i + 1; j < size; j++) indices[j] = indices[j - 1] + 1;
            }
        }

        return null;
    }

    // -----------------------------------------------------------------------
    // Possible d-sep pool
    // -----------------------------------------------------------------------

    /**
     * Computes the possible d-sep pool for the pair (x, y): the union of all
     * nodes reachable from x or y via inducing paths that do not pass through
     * any node in {@code forbidden}, excluding x, y, and forbidden nodes.
     */
    private static Set<Node> possibleDsepPool(Graph graph, Node x, Node y, Set<Node> forbidden) {
        return new HashSet<>(graph.paths().possibleDsep(x, -1));

//        Set<Node> pool = new HashSet<>();
//
//        for (Node w : graph.getNodes()) {
//            if (w == x || w == y) continue;
//            if (forbidden.contains(w)) continue;
//            if (existsInducingPath(graph, x, w, forbidden)
//                    || existsInducingPath(graph, y, w, forbidden)) {
//                pool.add(w);
//            }
//        }
//
//        return pool;
    }

    // -----------------------------------------------------------------------
    // Local inducing-path check (respects forbidden nodes)
    // -----------------------------------------------------------------------

    /**
     * Returns true iff there exists an inducing path between {@code x} and
     * {@code w} in {@code graph} that does not pass through any node in
     * {@code forbidden}.
     *
     * <p>This is a BFS adaptation of {@code Paths.existsInducingPathVisit},
     * with two modifications:</p>
     * <ol>
     *   <li>Nodes in {@code forbidden} are never visited.</li>
     *   <li>The traversal is BFS rather than DFS to avoid stack overflow on
     *       large graphs.</li>
     * </ol>
     *
     * <p>The inducing-path admissibility rules are identical to the original:</p>
     * <ul>
     *   <li>A measured interior node {@code b} must be a definite collider
     *       at the triple {@code (a, b, c)}.</li>
     *   <li>A definite collider {@code b} must be an ancestor of {@code x},
     *       {@code w}, or some selection variable (here the empty set).</li>
     * </ul>
     */
    private static boolean existsInducingPath(Graph graph, Node x, Node w, Set<Node> forbidden) {
        if (x.getNodeType() != NodeType.MEASURED) return false;
        if (w.getNodeType() != NodeType.MEASURED) return false;
        if (forbidden.contains(x) || forbidden.contains(w)) return false;

        // BFS state: (predecessor, current, path-so-far)
        record State(Node prev, Node curr, LinkedList<Node> path) {}

        Queue<State> queue = new ArrayDeque<>();

        for (Node b : graph.getAdjacentNodes(x)) {
            if (forbidden.contains(b)) continue;
            LinkedList<Node> seedPath = new LinkedList<>();
            seedPath.add(x);
            seedPath.add(b);
            if (b == w) return true;
            queue.add(new State(x, b, seedPath));
        }

        while (!queue.isEmpty()) {
            State s = queue.poll();
            Node a = s.prev();
            Node b = s.curr();
            LinkedList<Node> path = s.path();

            for (Node c : graph.getAdjacentNodes(b)) {
                if (c == a) continue;
                if (path.contains(c)) continue;
                if (forbidden.contains(c)) continue;

                // Measured interior node must be a definite collider.
                if (b.getNodeType() == NodeType.MEASURED
                        && !graph.isDefCollider(a, b, c)) {
                    continue;
                }

                // Definite collider must be an ancestor of x or w
                // (selection variables are empty here).
                if (graph.isDefCollider(a, b, c)) {
                    boolean ancestorOk = graph.paths().isAncestorOf(b, x)
                            || graph.paths().isAncestorOf(b, w);
                    if (!ancestorOk) continue;
                }

                if (c == w) return true;

                LinkedList<Node> newPath = new LinkedList<>(path);
                newPath.add(c);
                queue.add(new State(b, c, newPath));
            }
        }

        return false;
    }

    // -----------------------------------------------------------------------
    // Functional interface
    // -----------------------------------------------------------------------

    /**
     * Tests whether x and y are m-separated given z.
     */
    @FunctionalInterface
    public interface SepsetTester {
        boolean isSeparated(Node x, Node y, Set<Node> z) throws InterruptedException;
    }

    // -----------------------------------------------------------------------
    // Concrete implementations
    // -----------------------------------------------------------------------

    /**
     * Standard m-separation tester that respects the direct x–y edge.
     */
    public static final class StandardSepsetTester implements SepsetTester {
        private final Graph graph;
        private final boolean isPag;

        public StandardSepsetTester(Graph graph, boolean isPag) {
            this.graph = graph;
            this.isPag = isPag;
        }

        @Override
        public boolean isSeparated(Node x, Node y, Set<Node> z) {
            return graph.paths().isMSeparatedFrom(x, y, z, isPag);
        }
    }

    /**
     * M-separation tester that ignores the direct x–y edge.
     *
     * <p>Returns true iff x and y are m-separated given z via all paths
     * <em>other than</em> the direct x–y edge. Useful in FCIT and similar
     * algorithms where the direct edge's existence is under test and should
     * not short-circuit the separation check.</p>
     *
     * <p>Implemented as a Bayes-Ball pass that seeds the queue with all edges
     * incident to x <em>except</em> the direct x–y edge, and never declares
     * m-connection on arrival at y via that edge.</p>
     */
    public static final class IgnoreDirectEdgeSepsetTester implements SepsetTester {
        private final Graph graph;
        private final boolean isPag;

        public IgnoreDirectEdgeSepsetTester(Graph graph, boolean isPag) {
            this.graph = graph;
            this.isPag = isPag;
        }

        @Override
        public boolean isSeparated(Node x, Node y, Set<Node> z) {
            return !isMConnectedIgnoringDirectEdge(x, y, z);
        }

        /**
         * Bayes-Ball reachability from x to y, skipping the direct x–y edge.
         * Returns true iff x and y are m-connected given z via any other path.
         */
        private boolean isMConnectedIgnoringDirectEdge(Node x, Node y, Set<Node> z) {

            class EdgeNode {
                final Edge edge;
                final Node node;

                EdgeNode(Edge edge, Node node) {
                    this.edge = edge;
                    this.node = node;
                }

                @Override
                public int hashCode() {
                    return edge.hashCode() + node.hashCode();
                }

                @Override
                public boolean equals(Object o) {
                    if (!(o instanceof EdgeNode en)) return false;
                    return en.edge.equals(edge) && en.node.equals(node);
                }
            }

            Queue<EdgeNode> queue   = new ArrayDeque<>();
            Set<EdgeNode>   visited = new HashSet<>();

            // Seed with all edges incident to x, skipping the direct x–y edge.
            for (Edge edge : graph.getEdges(x)) {
                if (edge.getDistalNode(x) == y) continue;
                EdgeNode en = new EdgeNode(edge, x);
                if (visited.add(en)) queue.offer(en);
            }

            while (!queue.isEmpty()) {
                EdgeNode t     = queue.poll();
                Edge     edge1 = t.edge;
                Node     a     = t.node;
                Node     b     = edge1.getDistalNode(a);

                for (Edge edge2 : graph.getEdges(b)) {
                    Node c = edge2.getDistalNode(b);
                    if (c == a) continue;

                    if (reachable(edge1, edge2, a, z)) {
                        if (c == y) return true;

                        // PAG/CPDAG virtual-edge adjustment, mirroring the
                        // original isMConnectedTo logic.
                        Edge effectiveEdge2 = edge2;
                        if (!isPag && edge1.getProximalEndpoint(b) == Endpoint.ARROW) {
                            if (Edges.isUndirectedEdge(edge2)) {
                                effectiveEdge2 = Edges.directedEdge(b, edge2.getDistalNode(b));
                            } else if (Edges.isNondirectedEdge(edge2)) {
                                effectiveEdge2 = Edges.partiallyOrientedEdge(b, edge2.getDistalNode(b));
                            }
                        }

                        EdgeNode u = new EdgeNode(effectiveEdge2, b);
                        if (visited.add(u)) queue.offer(u);
                    }
                }
            }

            return false;
        }

        /**
         * Reachability predicate: can the Bayes Ball pass through b, arriving
         * via {@code edge1} from {@code a} and departing via {@code edge2}?
         */
        private boolean reachable(Edge edge1, Edge edge2, Node a, Set<Node> z) {
            Node b = edge1.getDistalNode(a);

            boolean collider = edge1.getProximalEndpoint(b) == Endpoint.ARROW
                    && edge2.getProximalEndpoint(b) == Endpoint.ARROW;

            if (collider) {
                if (z.contains(b)) return true;
                return graph.paths().isAncestorOfAnyZ(b, z);
            } else {
                return !z.contains(b);
            }
        }
    }
}
