package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;

import java.util.*;

/**
 * Implements a recursive procedure for constructing candidate separating sets
 * between two nodes under PAG semantics.
 *
 * <p>Given distinct nodes x and y, the algorithm attempts to build a set Z that
 * blocks all blockable paths between x and y, starting from an optional seed set
 * of nodes to include. If such a set is found it is returned and may later be
 * tested against the distribution for conditional independence. If any path is
 * provably unblockable, or the analysis is interrupted or inconclusive, the
 * routine returns {@code null}, indicating that no valid graphical separating
 * set was found within the given constraints.</p>
 *
 * <p>The eligible conditioning nodes are drawn from BFS shells of radius
 * {@code maxRadius} around {@code x} and/or {@code y}, controlled by
 * {@code nearWhichEndpoint}:</p>
 * <ul>
 *   <li>1 — pool drawn from shells around x only</li>
 *   <li>2 — pool drawn from shells around y only</li>
 *   <li>3 — pool drawn from shells around both x and y (union)</li>
 * </ul>
 *
 * <p>A node outside the pool that would otherwise be required to block a path
 * causes that branch to return {@code INDETERMINATE} rather than
 * {@code UNBLOCKABLE}, so the overall call returns {@code null} without
 * asserting that no separator exists at all — a wider radius might succeed.
 * When {@code maxRadius} is -1 the pool is unrestricted and all graph nodes
 * are eligible. A {@code depth} parameter additionally caps the total size of
 * Z; attempts to exceed it are likewise treated as {@code INDETERMINATE}.</p>
 *
 * <p>The locality constraint on the pool and depth keeps conditioning sets
 * small, improving the power of downstream independence tests from sample,
 * while retaining correct m-separation semantics: all paths — causal and
 * non-causal — are considered.</p>
 *
 * <p>Key features:</p>
 * <ul>
 *   <li>Respects PAG semantics for colliders, non-colliders, and latent nodes.</li>
 *   <li>Uses an outer fixed-point loop to handle colliders activated by Z growth
 *       across branches.</li>
 *   <li>Supports path length limits, depth limits, radius limits, and
 *       "do not follow" constraints.</li>
 *   <li>Background knowledge parameter is reserved for future extension.</li>
 *   <li>The presence of a direct edge x–y does not preempt construction, but
 *       such an edge may prevent a valid separator from existing.</li>
 *   <li>The returned set is always subject to a statistical independence test
 *       to confirm it functions as an actual separating set in the
 *       distribution.</li>
 * </ul>
 */
public class RecursiveBlocking {

    private RecursiveBlocking() {
    }

    // -----------------------------------------------------------------------
    // Public entry points
    // -----------------------------------------------------------------------

    /**
     * Radius-constrained blocking with default pool strategy (union of shells
     * around both endpoints, {@code nearWhichEndpoint = 3}).
     *
     * @param graph         the graph (DAG / CPDAG / MAG / PAG)
     * @param x             first endpoint
     * @param y             second endpoint
     * @param containing    nodes that must be in Z regardless of radius
     * @param notFollowed   nodes not to be traversed during path search
     * @param maxPathLength maximum path length (-1 = unlimited)
     * @return a candidate blocking set, or {@code null} if none found within
     * the radius constraint
     * @throws InterruptedException if the thread is interrupted
     */
    public static <E> Set<Node> blockPathsRecursively(Graph graph,
                                                      Node x,
                                                      Node y,
                                                      Set<Node> containing,
                                                      Set<Node> notFollowed,
                                                      int maxPathLength)
            throws InterruptedException {
        return blockPathsRecursively(graph, x, y, containing, notFollowed, maxPathLength, -1, -1);
    }

    /**
     * Radius-constrained blocking with default pool strategy (union of shells
     * around both endpoints, {@code nearWhichEndpoint = 3}).
     *
     * @param graph         the graph (DAG / CPDAG / MAG / PAG)
     * @param x             first endpoint
     * @param y             second endpoint
     * @param containing    nodes that must be in Z regardless of radius
     * @param notFollowed   nodes not to be traversed during path search
     * @param maxPathLength maximum path length (-1 = unlimited)
     * @param maxRadius     BFS radius for pool construction (-1 = unlimited)
     * @return a candidate blocking set, or {@code null} if none found within
     * the radius constraint
     * @throws InterruptedException if the thread is interrupted
     */
    public static Set<Node> blockPathsRecursively(Graph graph,
                                                  Node x,
                                                  Node y,
                                                  Set<Node> containing,
                                                  Set<Node> notFollowed,
                                                  int maxPathLength,
                                                  int maxRadius,
                                                  int depth)
            throws InterruptedException {
        return blockPathsRecursively(graph, x, y, containing, notFollowed,
                maxPathLength, maxRadius, depth, 3, null);
    }

    /**
     * Full-parameter entry point.
     *
     * @param graph             the graph
     * @param x                 first endpoint
     * @param y                 second endpoint
     * @param containing        nodes forced into Z
     * @param notFollowed       nodes not to be traversed
     * @param maxPathLength     maximum path length (-1 = unlimited)
     * @param maxRadius         BFS radius (-1 = unlimited)
     * @param nearWhichEndpoint 1 = near x, 2 = near y, 3 = near both
     * @param knowledge         optional background knowledge (reserved)
     * @return a candidate blocking set, or {@code null}
     * @throws InterruptedException if the thread is interrupted
     */
    public static Set<Node> blockPathsRecursively(Graph graph,
                                                  Node x,
                                                  Node y,
                                                  Set<Node> containing,
                                                  Set<Node> notFollowed,
                                                  int maxPathLength,
                                                  int maxRadius,
                                                  int depth,
                                                  int nearWhichEndpoint,
                                                  Knowledge knowledge)
            throws InterruptedException {
        Set<Node> pool = buildPool(graph, x, y, maxRadius, nearWhichEndpoint);
        // Nodes in 'containing' are always eligible, even if outside the radius.
        pool.addAll(containing);

        return blockPathsRecursivelyAdj(
                graph, x, y, containing, notFollowed,
                graph.paths().getDescendantsMap(),
                maxPathLength, depth, pool, knowledge);
    }

    // -----------------------------------------------------------------------
    // Pool construction (BFS shells)
    // -----------------------------------------------------------------------

    /**
     * Builds the set of nodes eligible to enter Z. When {@code maxRadius} is
     * -1, every graph node is returned (no restriction).
     */
    private static Set<Node> buildPool(Graph graph, Node x, Node y,
                                       int maxRadius, int nearWhichEndpoint) {
        if (maxRadius < 0) {
            return new HashSet<>(graph.getNodes());
        }

        Set<Node> pool = new LinkedHashSet<>();

        if (nearWhichEndpoint == 1 || nearWhichEndpoint == 3) {
            pool.addAll(bfsShells(graph, x, maxRadius));
        }
        if (nearWhichEndpoint == 2 || nearWhichEndpoint == 3) {
            pool.addAll(bfsShells(graph, y, maxRadius));
        }

        // x and y themselves are never conditioning candidates.
        pool.remove(x);
        pool.remove(y);

        return pool;
    }

    /**
     * Standard undirected BFS up to {@code maxRadius} hops from {@code seed}.
     * Returns all nodes reachable within that radius (excluding the seed).
     */
    private static Set<Node> bfsShells(Graph graph, Node seed, int maxRadius) {
        Set<Node> visited = new LinkedHashSet<>();
        Deque<Node> queue = new ArrayDeque<>();
        Map<Node, Integer> dist = new HashMap<>();

        visited.add(seed);
        queue.add(seed);
        dist.put(seed, 0);

        while (!queue.isEmpty()) {
            Node u = queue.removeFirst();
            int du = dist.get(u);
            if (du >= maxRadius) continue;
            for (Node v : graph.getAdjacentNodes(u)) {
                if (visited.add(v)) {
                    dist.put(v, du + 1);
                    queue.addLast(v);
                }
            }
        }

        visited.remove(seed);
        return visited;
    }

    // -----------------------------------------------------------------------
    // Core algorithm (identical to RecursiveBlocking except for pool guard)
    // -----------------------------------------------------------------------

    private static Set<Node> blockPathsRecursivelyAdj(
            Graph graph,
            Node x,
            Node y,
            Set<Node> containing,
            Set<Node> notFollowed,
            Map<Node, Set<Node>> descendantsMap,
            int maxPathLength,
            int depth,
            Set<Node> pool,
            Knowledge knowledge) throws InterruptedException {

        if (x == y) {
            throw new IllegalArgumentException("x and y must be distinct");
        }

        Set<Node> z = new HashSet<>(containing);

        List<Node> firstHops = new ArrayList<>();
        for (Node b : graph.getAdjacentNodes(x)) {
            if (b != y) firstHops.add(b);
        }

        // Outer fixed-point loop: re-examine every first hop after any Z growth,
        // because a node added to Z during one branch may activate a collider
        // on a different branch.
        while (true) {
            if (Thread.currentThread().isInterrupted()) {
                return null;
            }

            int zSizeBefore = z.size();

            for (Node b : firstHops) {
                if (Thread.currentThread().isInterrupted()) {
                    return null;
                }

                Set<Node> path = new HashSet<>();
                path.add(x);

                Blockable r = findPathToTargetVisit(
                        graph, x, b, y, path, z,
                        maxPathLength, depth, notFollowed, descendantsMap, pool);

                if (r == Blockable.UNBLOCKABLE) {
                    return null;
                }
                if (r == Blockable.INDETERMINATE) {
                    return null;
                }
            }

            if (z.size() == zSizeBefore) {
                // A complete pass made no additions to Z — every first-hop
                // branch is BLOCKED under the current Z.
                return z;
            }
        }
    }

    /**
     * Identical to {@link RecursiveBlocking#findPathToTargetVisit} except that
     * before adding {@code b} to Z the method checks whether {@code b} is in
     * the pool. If not, conditioning on {@code b} is forbidden and that option
     * is treated as INDETERMINATE (cannot block via out-of-radius node).
     */
    static Blockable findPathToTargetVisit(Graph graph,
                                           Node a,
                                           Node b,
                                           Node y,
                                           Set<Node> path,
                                           Set<Node> z,
                                           int maxPathLength,
                                           int depth,
                                           Set<Node> notFollowed,
                                           Map<Node, Set<Node>> descendantsMap,
                                           Set<Node> pool) throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            return Blockable.INDETERMINATE;
        }

        if (b == y) {
            return Blockable.UNBLOCKABLE;
        }
        if (path.contains(b)) {
            return Blockable.BLOCKED;
        }
        if (notFollowed.contains(b)) {
            return Blockable.INDETERMINATE;
        }
        if (notFollowed.contains(y)) {
            return Blockable.BLOCKED;
        }

        path.add(b);

        try {
            if (maxPathLength >= 0 && path.size() > maxPathLength) {
                return Blockable.INDETERMINATE;
            }

            // Case 1: b is latent — cannot condition on it; just traverse.
            if (b.getNodeType() == NodeType.LATENT) {
                return tryBlockAllContinuations(graph, a, b, y, path, z,
                        maxPathLength, depth, notFollowed, descendantsMap, pool);
            }

            // Snapshot Z for clean rollback.
            Set<Node> zSnapshot = new HashSet<>(z);

            // Case 2: Try WITHOUT conditioning on b.
            Blockable withoutB = tryBlockAllContinuations(graph, a, b, y, path, z,
                    maxPathLength, depth, notFollowed, descendantsMap, pool);

            if (withoutB == Blockable.BLOCKED) {
                return Blockable.BLOCKED;
            }

            // Roll back.
            z.clear();
            z.addAll(zSnapshot);

            // Case 3: Try WITH conditioning on b — but only if b is in the pool.
            if (!pool.contains(b)) {
                // Out-of-radius: cannot condition on b, and without-b already
                // failed, so this branch is indeterminate (not unblockable —
                // a wider radius might succeed).
                return Blockable.INDETERMINATE;
            }

            // Also, if the depth limit has already been reached, this branch is indeterminate,
            // since we cannot determine if conditioning on b would block the path without
            // adding a new node to Z, exceeding the depth limit.
            if (depth >= 0 && z.size() > depth) {
                return Blockable.INDETERMINATE;
            }

            z.add(b);
            Blockable withB = tryBlockAllContinuations(graph, a, b, y, path, z,
                    maxPathLength, depth, notFollowed, descendantsMap, pool);

            if (withB == Blockable.BLOCKED) {
                return Blockable.BLOCKED;
            }

            // Neither option worked — roll back.
            z.clear();
            z.addAll(zSnapshot);

            return (withB == Blockable.INDETERMINATE || withoutB == Blockable.INDETERMINATE)
                    ? Blockable.INDETERMINATE
                    : Blockable.UNBLOCKABLE;

        } finally {
            path.remove(b);
        }
    }

    private static Blockable tryBlockAllContinuations(Graph graph,
                                                      Node a,
                                                      Node b,
                                                      Node y,
                                                      Set<Node> path,
                                                      Set<Node> z,
                                                      int maxPathLength,
                                                      int depth,
                                                      Set<Node> notFollowed,
                                                      Map<Node, Set<Node>> descendantsMap,
                                                      Set<Node> pool)
            throws InterruptedException {
        Set<Node> handled = new HashSet<>();

        while (true) {
            if (Thread.currentThread().isInterrupted()) {
                return Blockable.INDETERMINATE;
            }

            List<Node> passNodes = getReachableNodes(graph, a, b, z, descendantsMap);
            passNodes.removeAll(notFollowed);

            boolean progressed = false;

            for (Node c : passNodes) {
                if (handled.contains(c)) continue;
                progressed = true;

                if (Thread.currentThread().isInterrupted()) {
                    return Blockable.INDETERMINATE;
                }

                Blockable result = findPathToTargetVisit(graph, b, c, y, path, z,
                        maxPathLength, depth, notFollowed, descendantsMap, pool);

                if (result == Blockable.UNBLOCKABLE) {
                    return Blockable.UNBLOCKABLE;
                }
                if (result == Blockable.INDETERMINATE) {
                    return Blockable.INDETERMINATE;
                }
                handled.add(c);
            }

            if (!progressed) {
                return Blockable.BLOCKED;
            }
        }
    }

    private static List<Node> getReachableNodes(Graph graph,
                                                Node a,
                                                Node b,
                                                Set<Node> z,
                                                Map<Node, Set<Node>> descendantsMap) {
        List<Node> passNodes = new ArrayList<>();
        for (Node c : graph.getAdjacentNodes(b)) {
            if (c == a) continue;
            if (reachable(graph, a, b, c, z, descendantsMap)) {
                passNodes.add(c);
            }
        }
        return passNodes;
    }

    private static boolean reachable(Graph graph,
                                     Node a,
                                     Node b,
                                     Node c,
                                     Set<Node> z,
                                     Map<Node, Set<Node>> descendantsMap) {
        boolean collider = graph.isDefCollider(a, b, c);

        if ((!collider || graph.isUnderlineTriple(a, b, c)) && !z.contains(b)) {
            return true;
        }

        if (!collider) return false;

        if (z.contains(b)) return true;

        if (descendantsMap == null) {
            return graph.paths().isAncestorOfAnyZ(b, z);
        } else {
            Set<Node> desc = descendantsMap.getOrDefault(b, Collections.emptySet());
            for (Node d : desc) {
                if (z.contains(d)) return true;
            }
            return false;
        }
    }

    /**
     * Three-valued result of path-blocking analysis.
     */
    public enum Blockable {
        /**
         * All paths through this branch are blocked by Z.
         */
        BLOCKED,
        /**
         * Some path is unblockable regardless of Z (e.g. direct x–y edge or latent bow).
         */
        UNBLOCKABLE,
        /**
         * Analysis was inconclusive (interrupted, path-length cap, or radius limit hit).
         */
        INDETERMINATE
    }
}
