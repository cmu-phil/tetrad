package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;

import java.util.*;

/**
 * Implements a procedure for constructing candidate separating sets between two
 * nodes under PAG semantics.
 *
 * <p>This version replaces the mutual recursion between
 * {@code findPathToTargetVisit} and {@code tryBlockAllContinuations} with an
 * explicit stack, eliminating the risk of {@link StackOverflowError} on deep
 * graphs. All semantics are identical to the original recursive version.</p>
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
    // Result type
    // -----------------------------------------------------------------------

    /**
     * Blocks paths between two specified nodes in a graph by iteratively
     * identifying and selecting nodes to include in a blocking set, subject to
     * constraints on path length and traversal rules. Assumes a direct edge
     * between x and y is to be ignored.
     *
     * <p>This overload collapses UNBLOCKABLE and INDETERMINATE both to
     * {@code null} for backward compatibility. Use
     * {@link #blockPathsRecursivelyFull} when the distinction matters.</p>
     *
     * @param graph         the graph in which the nodes and paths are analyzed
     * @param x             the starting node of the path
     * @param y             the target node of the path
     * @param containing    a set of nodes that must be included in the blocking set
     * @param notFollowed   a set of nodes that must not be traversed during path search
     * @param maxPathLength the maximum allowable length of the paths to block (-1 for no limit)
     * @return a set of nodes constituting a blocking set for paths between x and y,
     * or {@code null} if no such set is found within the given constraints
     * @throws InterruptedException if the thread executing the method is interrupted
     */
    public static <E> Set<Node> blockPathsRecursively(Graph graph,
                                                      Node x,
                                                      Node y,
                                                      Set<Node> containing,
                                                      Set<Node> notFollowed,
                                                      int maxPathLength)
            throws InterruptedException {
        return blockPathsRecursivelyFull(graph, x, y, containing, notFollowed,
                maxPathLength, -1, -1, 1, true).blockingSet();
    }

    // -----------------------------------------------------------------------
    // Public entry points — Set<Node> convenience overloads (existing callers)
    // -----------------------------------------------------------------------

    /**
     * Full-parameter entry point, collapsing UNBLOCKABLE and INDETERMINATE to
     * {@code null} for backward compatibility.
     *
     * <p>Use {@link #blockPathsRecursivelyFull} when the distinction matters.</p>
     *
     * @param graph             the graph
     * @param x                 first endpoint
     * @param y                 second endpoint
     * @param containing        nodes forced into Z
     * @param notFollowed       nodes not to be traversed
     * @param maxPathLength     maximum path length (-1 = unlimited)
     * @param maxRadius         BFS radius (-1 = unlimited)
     * @param depth             maximum size of Z (-1 = unlimited)
     * @param nearWhichEndpoint 1 = near x, 2 = near y, 3 = near both
     * @param ignoreDirectEdge  whether to ignore direct edges between x and y
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
                                                  boolean ignoreDirectEdge)
            throws InterruptedException {
        return blockPathsRecursivelyFull(graph, x, y, containing, notFollowed,
                maxPathLength, depth, maxRadius, nearWhichEndpoint,
                ignoreDirectEdge).blockingSet();
    }

    /**
     * Short-parameter entry point returning a full {@link BlockingResult},
     * distinguishing UNBLOCKABLE from INDETERMINATE on failure.
     *
     * @param graph         the graph
     * @param x             first endpoint
     * @param y             second endpoint
     * @param containing    nodes forced into Z
     * @param notFollowed   nodes not to be traversed
     * @param maxPathLength maximum path length (-1 = unlimited)
     * @return a {@link BlockingResult} describing the outcome
     * @throws InterruptedException if the thread is interrupted
     */
    public static BlockingResult blockPathsRecursivelyFull(Graph graph,
                                                           Node x,
                                                           Node y,
                                                           Set<Node> containing,
                                                           Set<Node> notFollowed,
                                                           int maxPathLength)
            throws InterruptedException {
        return blockPathsRecursivelyFull(graph, x, y, containing, notFollowed,
                maxPathLength, -1, -1, 1, true);
    }

    // -----------------------------------------------------------------------
    // Public entry points — full BlockingResult overloads
    // -----------------------------------------------------------------------

    /**
     * Full-parameter entry point returning a full {@link BlockingResult},
     * distinguishing UNBLOCKABLE from INDETERMINATE on failure.
     *
     * @param graph             the graph
     * @param x                 first endpoint
     * @param y                 second endpoint
     * @param containing        nodes forced into Z
     * @param notFollowed       nodes not to be traversed
     * @param maxPathLength     maximum path length (-1 = unlimited)
     * @param depth             maximum size of Z (-1 = unlimited)
     * @param maxRadius         BFS radius (-1 = unlimited)
     * @param nearWhichEndpoint 1 = near x, 2 = near y, 3 = near both
     * @param ignoreDirectEdge  whether to ignore direct edges between x and y
     * @return a {@link BlockingResult} describing the outcome
     * @throws InterruptedException if the thread is interrupted
     */
    public static BlockingResult blockPathsRecursivelyFull(Graph graph,
                                                           Node x,
                                                           Node y,
                                                           Set<Node> containing,
                                                           Set<Node> notFollowed,
                                                           int maxPathLength,
                                                           int depth, int maxRadius,
                                                           int nearWhichEndpoint,
                                                           boolean ignoreDirectEdge)
            throws InterruptedException {

        // Fail fast if the seed set already violates the depth bound
        if (depth >= 0 && containing.size() > depth) {
            return new BlockingResult(null, true); // INDETERMINATE, not UNBLOCKABLE
        }

        Set<Node> pool = buildPool(graph, x, y, maxRadius, nearWhichEndpoint);
        pool.addAll(containing);

        int recursionDepth = maxPathLength < 0 ? graph.getNumNodes() : maxPathLength;

        return blockPathsRecursivelyAdj(
                graph, x, y, containing, notFollowed,
                graph.paths().getDescendantsMap(),
                maxPathLength, recursionDepth, depth, pool, ignoreDirectEdge);
    }

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

        pool.remove(x);
        pool.remove(y);

        return pool;
    }

    // -----------------------------------------------------------------------
    // Pool construction (BFS shells)
    // -----------------------------------------------------------------------

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

    /**
     * Core fixed-point loop. Now returns a {@link BlockingResult} so callers
     * can distinguish UNBLOCKABLE (proven no separator) from INDETERMINATE
     * (search limit hit — verdict unknown).
     */
    private static BlockingResult blockPathsRecursivelyAdj(
            Graph graph,
            Node x,
            Node y,
            Set<Node> containing,
            Set<Node> notFollowed,
            Map<Node, Set<Node>> descendantsMap,
            int maxPathLength,
            int recursionDepth,
            int depth,
            Set<Node> pool,
            boolean ignoreDirectEdge) throws InterruptedException {

        if (x == y) {
            throw new IllegalArgumentException("x and y must be distinct");
        }

        Set<Node> z = new HashSet<>(containing);

        List<Node> firstHops = new ArrayList<>(graph.getAdjacentNodes(x));

        if (ignoreDirectEdge) {
            firstHops.remove(y);
        }

        // Track nodes that have been rolled back due to depth cap.
        // A node rolled back more than once signals oscillation — the depth
        // cap is the binding constraint and further looping won't converge.
        Map<Node, Integer> rollbackCount = new HashMap<>();

        while (true) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }

            if (depth >= 0 && z.size() > depth) {
                return new BlockingResult(null, true);
            }

            int zSizeBefore = z.size();
            boolean anyIndeterminate = false;

            for (Node b : firstHops) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException();
                }

                Set<Node> path = new HashSet<>();
                path.add(x);

                Set<Node> zBefore = new HashSet<>(z);

                Blockable r = findPathToTargetVisit(
                        graph, x, b, y, path, z,
                        maxPathLength, depth, notFollowed, descendantsMap, pool,
                        recursionDepth, 0);

                if (r == Blockable.UNBLOCKABLE) {
                    return new BlockingResult(null, false);
                }

                if (r == Blockable.INDETERMINATE) {
                    // Find which nodes were tentatively added then rolled back.
                    Set<Node> rolledBack = new HashSet<>(z);
                    rolledBack.removeAll(zBefore); // nodes added during this branch
                    z.clear();
                    z.addAll(zBefore);

                    // Also catch nodes that were in z before but got mutated out
                    // (shouldn't happen but be safe), and nodes newly attempted.
                    // The key set to track is what the branch tried to add.
                    for (Node n : rolledBack) {
                        int count = rollbackCount.merge(n, 1, Integer::sum);
                        if (count > 1) {
                            // This node has been rolled back more than once —
                            // we're oscillating. The depth cap is binding.
                            return new BlockingResult(null, true);
                        }
                    }

                    anyIndeterminate = true;
                }
            }

            if (z.size() == zSizeBefore) {
                if (anyIndeterminate) {
                    return new BlockingResult(null, true);
                }
                return new BlockingResult(z, false);
            }
        }
    }

    // -----------------------------------------------------------------------
    // Core algorithm
    // -----------------------------------------------------------------------

    static Blockable findPathToTargetVisit(Graph graph,
                                           Node aInit, Node bInit, Node y,
                                           Set<Node> path, Set<Node> z,
                                           int maxPathLength, int depth,
                                           Set<Node> notFollowed,
                                           Map<Node, Set<Node>> descendantsMap,
                                           Set<Node> pool,
                                           int recursionDepth,
                                           int currentDepthInit)
            throws InterruptedException {

        Deque<Frame> callStack = new ArrayDeque<>();
        callStack.push(new Frame(aInit, bInit, y,
                maxPathLength, depth, recursionDepth, currentDepthInit));

        Blockable lastResult = null;

        while (!callStack.isEmpty()) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();

            Frame f = callStack.peek();

            // =================================================================
            // ENTER
            // =================================================================
            if (f.pass == Pass.ENTER) {

                if (f.currentDepth > f.recursionDepth) {
                    callStack.pop();
                    lastResult = Blockable.INDETERMINATE;
                    continue;
                }

                if (f.b == y) {
                    callStack.pop();
                    lastResult = Blockable.UNBLOCKABLE;
                    continue;
                }
                if (path.contains(f.b)) {
                    callStack.pop();
                    lastResult = Blockable.BLOCKED;
                    continue;
                }
                if (notFollowed.contains(f.b)) {
                    callStack.pop();
                    lastResult = Blockable.INDETERMINATE;
                    continue;
                }
                if (notFollowed.contains(y)) {
                    callStack.pop();
                    lastResult = Blockable.BLOCKED;
                    continue;
                }

                path.add(f.b);

                if (f.maxPathLength >= 0 && path.size() > f.maxPathLength) {
                    path.remove(f.b);
                    callStack.pop();
                    lastResult = Blockable.INDETERMINATE;
                    continue;
                }

                f.zSnapshot = new HashSet<>(z);
                f.pass = Pass.CONTINUATIONS_WITHOUT_B;
            }

            // =================================================================
            // CONTINUATIONS_WITHOUT_B
            // =================================================================
            if (f.pass == Pass.CONTINUATIONS_WITHOUT_B) {

                Blockable contResult;
                if (lastResult != null) {
                    if (lastResult == Blockable.UNBLOCKABLE
                            || lastResult == Blockable.INDETERMINATE) {
                        contResult = lastResult;
                        lastResult = null;
                    } else {
                        f.handled.add(f.pendingC);
                        f.pendingC = null;
                        lastResult = null;

                        contResult = stepContinuationLoop(
                                graph, f, y, path, z, notFollowed, descendantsMap, pool,
                                callStack, false);

                        if (contResult == null) continue;
                    }
                } else {
                    contResult = stepContinuationLoop(
                            graph, f, y, path, z, notFollowed, descendantsMap, pool,
                            callStack, false);

                    if (contResult == null) continue;
                }

                if (f.b.getNodeType() == NodeType.LATENT) {
                    path.remove(f.b);
                    callStack.pop();
                    lastResult = contResult;
                    continue;
                }

                if (contResult == Blockable.BLOCKED) {
                    path.remove(f.b);
                    callStack.pop();
                    lastResult = Blockable.BLOCKED;
                    continue;
                }

                f.withoutBResult = contResult;
                z.clear();
                z.addAll(f.zSnapshot);

                if (!pool.contains(f.b)) {
                    path.remove(f.b);
                    callStack.pop();
                    lastResult = Blockable.INDETERMINATE;
                    continue;
                }
                if (f.depth >= 0 && z.size() >= f.depth) {
                    path.remove(f.b);
                    callStack.pop();
                    lastResult = Blockable.INDETERMINATE;
                    continue;
                }

                z.add(f.b);
                f.handled = new HashSet<>();
                f.pendingC = null;
                f.pass = Pass.CONTINUATIONS_WITH_B;
                lastResult = null;
            }

            // =================================================================
            // CONTINUATIONS_WITH_B
            // =================================================================
            if (f.pass == Pass.CONTINUATIONS_WITH_B) {

                Blockable contResult;
                if (lastResult != null) {
                    if (lastResult == Blockable.UNBLOCKABLE
                            || lastResult == Blockable.INDETERMINATE) {
                        contResult = lastResult;
                        lastResult = null;
                    } else {
                        f.handled.add(f.pendingC);
                        f.pendingC = null;
                        lastResult = null;

                        contResult = stepContinuationLoop(
                                graph, f, y, path, z, notFollowed, descendantsMap, pool,
                                callStack, true);

                        if (contResult == null) continue;
                    }
                } else {
                    contResult = stepContinuationLoop(
                            graph, f, y, path, z, notFollowed, descendantsMap, pool,
                            callStack, true);

                    if (contResult == null) continue;
                }

                Blockable withB = contResult;
                if (withB == Blockable.BLOCKED) {
                    path.remove(f.b);
                    callStack.pop();
                    lastResult = Blockable.BLOCKED;
                } else {
                    z.clear();
                    z.addAll(f.zSnapshot);
                    path.remove(f.b);
                    callStack.pop();
                    lastResult = (withB == Blockable.INDETERMINATE
                            || f.withoutBResult == Blockable.INDETERMINATE)
                            ? Blockable.INDETERMINATE
                            : Blockable.UNBLOCKABLE;
                }
            }
        }

        return lastResult;
    }
        
    //    private static BlockingResult blockPathsRecursivelyAdj(
//            Graph graph,
//            Node x,
//            Node y,
//            Set<Node> containing,
//            Set<Node> notFollowed,
//            Map<Node, Set<Node>> descendantsMap,
//            int maxPathLength,
//            int recursionDepth,
//            int depth,
//            Set<Node> pool,
//            boolean ignoreDirectEdge) throws InterruptedException {
//
//        if (x == y) {
//            throw new IllegalArgumentException("x and y must be distinct");
//        }
//
//        Set<Node> z = new HashSet<>(containing);
//
//        List<Node> firstHops = new ArrayList<>(graph.getAdjacentNodes(x));
//
//        if (ignoreDirectEdge) {
//            firstHops.remove(y);
//        }
//
//        while (true) {
//            if (Thread.currentThread().isInterrupted()) {
//                throw new InterruptedException();
//            }
//
//            // Guard: if Z has somehow grown past depth, bail out
//            if (depth >= 0 && z.size() > depth) {
//                return new BlockingResult(null, true);
//            }
//
//            int zSizeBefore = z.size();
//
//            // Track whether any branch was indeterminate this pass. We only report
//            // INDETERMINATE if we complete the full pass without finding a solution —
//            // a later branch might succeed within the depth bound even if an earlier
//            // one hit the limit.
//            boolean anyIndeterminate = false;
//
////            for (Node b : firstHops) {
////                if (Thread.currentThread().isInterrupted()) {
////                    throw new InterruptedException();
////                }
////
////                Set<Node> path = new HashSet<>();
////                path.add(x);
////
////                Blockable r = findPathToTargetVisit(
////                        graph, x, b, y, path, z,
////                        maxPathLength, depth, notFollowed, descendantsMap, pool,
////                        recursionDepth, 0);
////
////                // UNBLOCKABLE: proven no separator exists — definitive failure regardless
////                // of what other branches say.
////                if (r == Blockable.UNBLOCKABLE) {
////                    return new BlockingResult(null, false);
//////                    return BlockingResult.unblockable();
////                }
////
////                // INDETERMINATE: this branch hit a limit, but don't give up yet —
////                // another branch may still find a valid blocking set within the bounds.
////                if (r == Blockable.INDETERMINATE) {
////                    anyIndeterminate = true;
////                }
////            }
//
//            for (Node b : firstHops) {
//                if (Thread.currentThread().isInterrupted()) {
//                    throw new InterruptedException();
//                }
//
//                Set<Node> path = new HashSet<>();
//                path.add(x);
//
//                // Snapshot Z before this branch so we can roll back if it hits
//                // a limit, allowing a shallower branch to succeed instead.
//                Set<Node> zBefore = new HashSet<>(z);
//
//                Blockable r = findPathToTargetVisit(
//                        graph, x, b, y, path, z,
//                        maxPathLength, depth, notFollowed, descendantsMap, pool,
//                        recursionDepth, 0);
//
//                if (r == Blockable.UNBLOCKABLE) {
//                    return new BlockingResult(null, false);
//                }
//
//                if (r == Blockable.INDETERMINATE) {
//                    // Roll back Z mutations from this branch — they exceeded our
//                    // constraints. Another branch may find a shallower solution.
//                    z.clear();
//                    z.addAll(zBefore);
//                    anyIndeterminate = true;
//                }
//                // If BLOCKED, Z growth from this branch is valid — keep it.
//            }
//
//            if (z.size() == zSizeBefore) {
//                // No Z growth this pass. If every branch was BLOCKED, we're done.
//                // If any branch was indeterminate, we can't claim success — report
//                // inconclusive so the caller knows the result may be incomplete.
//                if (anyIndeterminate) {
//                    return new BlockingResult(null, true);
////                    return BlockingResult.indeterminate();
//                }
//                return new BlockingResult(z, false);
////                return BlockingResult.found(z);
//            }
//
//            // Z grew — loop again. Note that anyIndeterminate resets each pass,
//            // so a branch that was indeterminate before Z growth may now be
//            // BLOCKED under the larger Z.
//        }
//    }

//    private static BlockingResult blockPathsRecursivelyAdj(
//            Graph graph,
//            Node x,
//            Node y,
//            Set<Node> containing,
//            Set<Node> notFollowed,
//            Map<Node, Set<Node>> descendantsMap,
//            int maxPathLength,
//            int recursionDepth,
//            int depth,
//            Set<Node> pool,
//            boolean ignoreDirectEdge) throws InterruptedException {
//
//        if (x == y) {
//            throw new IllegalArgumentException("x and y must be distinct");
//        }
//
//        Set<Node> z = new HashSet<>(containing);
//
//        List<Node> firstHops = new ArrayList<>(graph.getAdjacentNodes(x));
//
//        if (ignoreDirectEdge) {
//            firstHops.remove(y);
//        }
//
//        // Outer fixed-point loop: re-examine every first hop after any Z growth,
//        // because a node added to Z during one branch may activate a collider
//        // on a different branch.
//        while (true) {
//            if (Thread.currentThread().isInterrupted()) {
//                throw new InterruptedException();
//            }
//
//            int zSizeBefore = z.size();
//
//            for (Node b : firstHops) {
//                if (Thread.currentThread().isInterrupted()) {
//                    throw new InterruptedException();
//                }
//
//                Set<Node> path = new HashSet<>();
//                path.add(x);
//
//                Blockable r = findPathToTargetVisit(
//                        graph, x, b, y, path, z,
//                        maxPathLength, depth, notFollowed, descendantsMap, pool,
//                        recursionDepth, 0);
//
//                // UNBLOCKABLE: a path exists that cannot be blocked — definitive failure.
//                if (r == Blockable.UNBLOCKABLE) {
//                    return new BlockingResult(null, false);
//                }
//                // INDETERMINATE: search limit hit — we cannot confirm or deny a separator.
//                if (r == Blockable.INDETERMINATE) {
//                    return new BlockingResult(null, true);
//                }
//            }
//
//            if (z.size() == zSizeBefore) {
//                return new BlockingResult(z, false);
//            }
//        }
//    }

    // -----------------------------------------------------------------------
    // Frame definition for the explicit stack
    // -----------------------------------------------------------------------

    private static Blockable stepContinuationLoop(
            Graph graph,
            Frame f,
            Node y,
            Set<Node> path,
            Set<Node> z,
            Set<Node> notFollowed,
            Map<Node, Set<Node>> descendantsMap,
            Set<Node> pool,
            Deque<Frame> callStack,
            boolean isWithBPass) throws InterruptedException {

        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();

        List<Node> passNodes = getReachableNodes(graph, f.a, f.b, z, descendantsMap);
        passNodes.removeAll(notFollowed);

        for (Node c : passNodes) {
            if (f.handled.contains(c)) continue;

            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();

            f.pendingC = c;
            callStack.push(new Frame(
                    f.b, c, y,
                    f.maxPathLength, f.depth, f.recursionDepth,
                    f.currentDepth + 1));
            return null;
        }

        return Blockable.BLOCKED;
    }

    private static List<Node> getReachableNodes(Graph graph,
                                                Node a,
                                                Node b,
                                                Set<Node> z,
                                                Map<Node, Set<Node>> descendantsMap)
            throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException();
        }

        List<Node> passNodes = new ArrayList<>();
        for (Node c : graph.getAdjacentNodes(b)) {
            if (c == a) continue;
            if (reachable(graph, a, b, c, z, descendantsMap)) {
                passNodes.add(c);
            }
        }
        return passNodes;
    }

    // -----------------------------------------------------------------------
    // Iterative driver
    // -----------------------------------------------------------------------

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

    // -----------------------------------------------------------------------
    // Continuation-loop stepper
    // -----------------------------------------------------------------------

    /**
     * Which "pass" a frame is currently executing inside
     * {@code findPathToTargetVisit}.
     *
     * <p>The lifecycle of a non-LATENT frame is:</p>
     * <pre>
     *   ENTER  →  [guard checks, path.add(b), take zSnapshot]
     *   CONTINUATIONS_WITHOUT_B  →  run tryBlockAllContinuations without b in z
     *                               (may suspend to push child frames)
     *   CONTINUATIONS_WITH_B     →  run tryBlockAllContinuations with b in z
     *                               (may suspend to push child frames)
     *   [path.remove(b), pop, return result]
     * </pre>
     *
     * <p>For LATENT nodes the frame jumps straight to
     * {@code CONTINUATIONS_WITHOUT_B} and never reaches
     * {@code CONTINUATIONS_WITH_B}.</p>
     */
    private enum Pass {
        ENTER,
        CONTINUATIONS_WITHOUT_B,
        CONTINUATIONS_WITH_B
    }

    // -----------------------------------------------------------------------
    // Reachability helpers
    // -----------------------------------------------------------------------

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

    /**
     * The result of a blocking-set search.
     *
     * <p>Three outcomes are possible:</p>
     * <ul>
     *   <li><b>Success</b>: {@code blockingSet} is non-null. A candidate
     *       separating set was found and should be tested for independence.</li>
     *   <li><b>Unblockable</b>: {@code blockingSet} is null and
     *       {@code indeterminate} is false. A path exists that cannot be
     *       blocked regardless of Z — no separator exists within the graph
     *       structure.</li>
     *   <li><b>Indeterminate</b>: {@code blockingSet} is null and
     *       {@code indeterminate} is true. The search hit a path-length or
     *       depth limit before it could confirm or rule out a separator. A
     *       legal-PAG verdict of INCONCLUSIVE should be reported upstream
     *       rather than ILLEGAL.</li>
     * </ul>
     *
     * @param blockingSet   the blocking set found, or {@code null} on failure
     * @param indeterminate true iff the null result was due to a search limit
     *                      rather than a proven impossibility
     */
    public record BlockingResult(Set<Node> blockingSet, boolean indeterminate) {

        /**
         * True iff a blocking set was found.
         */
        public boolean found() {
            return blockingSet != null;
        }
    }

    // -----------------------------------------------------------------------
    // Result enum
    // -----------------------------------------------------------------------

    /**
     * One stack frame — the explicit equivalent of a single activation record
     * for {@code findPathToTargetVisit}.
     */
    private static final class Frame {

        final Node a;
        final Node b;
        final Node y;
        final int maxPathLength;
        final int depth;
        final int recursionDepth;
        final int currentDepth;

        Pass pass = Pass.ENTER;
        Set<Node> zSnapshot = null;
        Blockable withoutBResult = null;
        Set<Node> handled = new HashSet<>();
        Node pendingC = null;

        Frame(Node a, Node b, Node y,
              int maxPathLength, int depth, int recursionDepth, int currentDepth) {
            this.a = a;
            this.b = b;
            this.y = y;
            this.maxPathLength = maxPathLength;
            this.depth = depth;
            this.recursionDepth = recursionDepth;
            this.currentDepth = currentDepth;
        }
    }
}