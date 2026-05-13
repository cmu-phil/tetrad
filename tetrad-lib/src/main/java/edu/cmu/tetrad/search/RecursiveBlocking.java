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
     * @param <E>           the type of the graph nodes
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
        return blockPathsIterativeDeepening(graph, x, y, containing, notFollowed,
                maxPathLength, 8, 4, 1, true).blockingSet();
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
            return new BlockingResult(null, false);
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
     * Breadth-first separating-set search between {@code x} and {@code y}.
     *
     * <p>Unlike {@link #blockPathsRecursivelyFull}, which commits to a path and
     * backtracks, this method maintains a queue of candidate blocking sets and
     * expands them one node at a time, level by level. It finds the
     * <em>smallest</em> blocking set reachable from the seed before trying
     * larger ones, so it terminates quickly when x and y are unconditionally
     * d-separated or separated by a small set.</p>
     *
     * <p>Candidates for addition to Z are drawn exclusively from nodes that lie
     * on <em>active</em> paths between x and y under the current Z — that is,
     * non-collider nodes that are currently open and therefore need to be
     * blocked. This keeps the search space small relative to exhaustive
     * enumeration over adjacency subsets.</p>
     *
     * <p>The method respects the same parameter contract as
     * {@link #blockPathsRecursivelyFull}:</p>
     * <ul>
     *   <li>{@code maxPathLength} bounds the length of paths considered when
     *       collecting candidates; -1 means unlimited.</li>
     *   <li>{@code depth} caps the size of Z; sets larger than this are never
     *       enqueued.</li>
     *   <li>{@code maxRadius} and {@code nearWhichEndpoint} restrict which
     *       nodes may enter Z to a BFS shell around x and/or y.</li>
     *   <li>{@code ignoreDirectEdge} controls whether the direct x–y edge is
     *       skipped when collecting active paths.</li>
     * </ul>
     *
     * <p>Returns a {@link BlockingResult} using the same three-outcome
     * convention:</p>
     * <ul>
     *   <li><b>Found</b>: a blocking set was found and is returned.</li>
     *   <li><b>Unblockable</b>: the queue was exhausted without finding a
     *       blocking set within the depth bound — no separator exists within
     *       the constrained search space.</li>
     *   <li><b>Indeterminate</b>: the search was interrupted.</li>
     * </ul>
     *
     * @param graph             the graph
     * @param x                 first endpoint
     * @param y                 second endpoint
     * @param containing        nodes forced into Z (the seed set)
     * @param notFollowed       nodes not to be traversed when collecting
     *                          active paths
     * @param maxPathLength     maximum path length when collecting active
     *                          paths (-1 = unlimited)
     * @param depth             maximum size of Z (-1 = unlimited)
     * @param maxRadius         BFS radius for the node pool (-1 = unlimited)
     * @param nearWhichEndpoint 1 = near x only, 2 = near y only,
     *                          3 = near both
     * @param ignoreDirectEdge  whether to ignore the direct x–y edge
     * @return a {@link BlockingResult} describing the outcome
     * @throws InterruptedException if the thread is interrupted
     */
    public static BlockingResult blockPathsBfs(
            Graph graph,
            Node x,
            Node y,
            Set<Node> containing,
            Set<Node> notFollowed,
            int maxPathLength,
            int depth,
            int maxRadius,
            int nearWhichEndpoint,
            boolean ignoreDirectEdge)
            throws InterruptedException {

        // Build the pool of nodes eligible to enter Z, same as the recursive version.
        Set<Node> pool = buildPool(graph, x, y, maxRadius, nearWhichEndpoint);
        pool.addAll(containing);

        // Precompute descendants map for the reachability predicate.
        Map<Node, Set<Node>> descendantsMap = graph.paths().getDescendantsMap();

        // BFS queue: each entry is a candidate blocking set.
        // We use a LinkedHashSet to preserve insertion order within each level,
        // which gives deterministic behaviour and avoids re-visiting the same
        // set reached by different expansion orders.
        //
        // visited tracks sets we have already enqueued to avoid duplicates.
        // Since sets can be large, we use a Set<Set<Node>> with hash equality.
        Deque<Set<Node>> queue   = new ArrayDeque<>();
        Set<Set<Node>>   visited = new HashSet<>();

        Set<Node> seed = new HashSet<>(containing);
        queue.add(seed);
        visited.add(seed);

        while (!queue.isEmpty()) {

            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }

            Set<Node> z = queue.poll();

            // Depth guard: never process a set that exceeds the depth cap.
            if (depth >= 0 && z.size() > depth) {
                continue;
            }

            // Check whether z already blocks all active paths from x to y.
            if (blocksAllPaths(graph, x, y, z, notFollowed, descendantsMap,
                    maxPathLength, ignoreDirectEdge)) {
                return new BlockingResult(z, false);
            }

            // If we are already at the depth cap, do not expand further.
            if (depth >= 0 && z.size() == depth) {
                continue;
            }

            // Collect candidates: nodes on active paths that are non-colliders
            // (and therefore blockable by conditioning on them).
            Set<Node> candidates = activeCandidates(
                    graph, x, y, z, notFollowed, descendantsMap,
                    maxPathLength, pool, ignoreDirectEdge);

            for (Node candidate : candidates) {
                Set<Node> zPrime = new HashSet<>(z);
                zPrime.add(candidate);
                if (visited.add(zPrime)) {
                    queue.add(zPrime);
                }
            }
        }

        // Queue exhausted without finding a blocking set within the constraints.
        return new BlockingResult(null, false);
    }

    /**
     * Iterative-deepening separating-set search between {@code x} and {@code y}.
     *
     * <p>This method calls {@link #blockPathsRecursivelyFull} repeatedly,
     * starting with {@code maxPathLength = 0} and incrementing by 1 on each
     * iteration until either a blocking set is found or the path-length cap
     * reaches {@code maxPathLength} (the caller-supplied ceiling). This is
     * iterative deepening applied to path length rather than search depth: it
     * inherits the memory efficiency of the depth-first version while
     * guaranteeing that the shortest-path blocking set is found first.</p>
     *
     * <p>Because the depth-first version can hang on dense graphs when given
     * an unconstrained path length (it may explore exponentially many paths
     * before the recursion cap triggers), bounding each call to a small
     * {@code maxPathLength} keeps each individual call fast. Short separating
     * sets — including the empty set, which handles unconditionally d-separated
     * pairs — are found at the lowest path-length level and return immediately
     * without exploring longer paths at all.</p>
     *
     * <p>The method respects the same parameter contract as
     * {@link #blockPathsRecursivelyFull}. The {@code maxPathLength} parameter
     * here serves as the ceiling for the iterative deepening loop; the
     * per-iteration cap starts at 0 and grows up to this ceiling. Pass -1 to
     * use {@code graph.getNumNodes()} as the ceiling (a natural upper bound,
     * since no simple path in the graph is longer than that).</p>
     *
     * <p>Returns a {@link BlockingResult} using the same three-outcome
     * convention:</p>
     * <ul>
     *   <li><b>Found</b>: a blocking set was found and is returned, together
     *       with the path-length level at which it was found (accessible via
     *       {@link BlockingResult#blockingSet()}).</li>
     *   <li><b>Unblockable</b>: the depth-first call at some level returned
     *       UNBLOCKABLE — a path exists that cannot be blocked regardless of
     *       Z.</li>
     *   <li><b>Indeterminate</b>: the ceiling was reached without finding a
     *       blocking set and without a definitive UNBLOCKABLE result.</li>
     * </ul>
     *
     * @param graph             the graph
     * @param x                 first endpoint
     * @param y                 second endpoint
     * @param containing        nodes forced into Z (the seed set)
     * @param notFollowed       nodes not to be traversed
     * @param maxPathLength     ceiling for the iterative deepening loop
     *                          (-1 = use graph.getNumNodes())
     * @param depth             maximum size of Z (-1 = unlimited)
     * @param maxRadius         BFS radius for the node pool (-1 = unlimited)
     * @param nearWhichEndpoint 1 = near x only, 2 = near y only,
     *                          3 = near both
     * @param ignoreDirectEdge  whether to ignore the direct x–y edge
     * @return a {@link BlockingResult} describing the outcome
     * @throws InterruptedException if the thread is interrupted
     */
    public static BlockingResult blockPathsIterativeDeepening(
            Graph graph,
            Node x,
            Node y,
            Set<Node> containing,
            Set<Node> notFollowed,
            int maxPathLength,
            int depth,
            int maxRadius,
            int nearWhichEndpoint,
            boolean ignoreDirectEdge)
            throws InterruptedException {

        int ceiling = (maxPathLength < 0) ? graph.getNumNodes() : maxPathLength;

        for (int pathLen = 0; pathLen <= ceiling; pathLen++) {

            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }

            BlockingResult result = blockPathsRecursivelyFull(
                    graph, x, y,
                    containing, notFollowed,
                    pathLen,        // maxPathLength for this iteration
                    depth,
                    maxRadius,
                    nearWhichEndpoint,
                    ignoreDirectEdge);

            if (result.found()) {
                // Blocking set found at this path-length level — return it.
                return result;
            }

            if (!result.indeterminate()) {
                // The depth-first call returned UNBLOCKABLE at this level,
                // meaning a path exists that cannot be blocked regardless of Z.
                // There is no point trying longer paths — return UNBLOCKABLE.
                return result;
            }

            // result.indeterminate() == true means the depth-first call hit
            // the path-length cap without finding a set or proving impossibility.
            // Increment the cap and try again.
        }

        // Ceiling reached without finding a blocking set or proving UNBLOCKABLE.
        return new BlockingResult(null, true);
    }

    // -----------------------------------------------------------------------
    // BFS helpers
    // -----------------------------------------------------------------------

    /**
     * Returns true iff {@code z} blocks every active path from {@code x} to
     * {@code y} in {@code graph}.
     *
     * <p>Uses a standard Bayes-Ball reachability pass: a node {@code v}
     * reachable from {@code x} without passing through {@code y} means an
     * active path exists. The pass respects collider/non-collider semantics
     * and the {@code descendantsMap} for collider activation.</p>
     */
    private static boolean blocksAllPaths(
            Graph graph,
            Node x,
            Node y,
            Set<Node> z,
            Set<Node> notFollowed,
            Map<Node, Set<Node>> descendantsMap,
            int maxPathLength,
            boolean ignoreDirectEdge)
            throws InterruptedException {

        // Bayes-Ball forward pass from x. Each entry in the queue is
        // (predecessor, current node, path length so far).
        // We keep track of visited (predecessor, node) pairs to avoid cycles.
        Deque<long[]> queue = new ArrayDeque<>();
        // Encode (predecessor index, node index, length) as a long triple.
        // Since we need object identity rather than indices, use a wrapper.
        Deque<PathEntry> bfsQueue = new ArrayDeque<>();
        Set<String>      seen     = new HashSet<>();

        for (Node neighbor : graph.getAdjacentNodes(x)) {
            if (ignoreDirectEdge && neighbor == y) continue;
            if (notFollowed.contains(neighbor))    continue;

            String key = x.getName() + "->" + neighbor.getName();
            if (seen.add(key)) {
                bfsQueue.add(new PathEntry(x, neighbor, 1));
            }
        }

        while (!bfsQueue.isEmpty()) {

            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }

            PathEntry entry = bfsQueue.poll();
            Node a = entry.predecessor;
            Node b = entry.current;
            int  len = entry.length;

            // Reached y via an active path — not fully blocked.
            if (b == y) return false;

            // Path length cap.
            if (maxPathLength >= 0 && len >= maxPathLength) continue;

            for (Node c : graph.getAdjacentNodes(b)) {
                if (c == a)                         continue;
                if (notFollowed.contains(c))        continue;

                if (!reachable(graph, a, b, c, z, descendantsMap)) continue;

                String key = b.getName() + "->" + c.getName();
                if (seen.add(key)) {
                    bfsQueue.add(new PathEntry(b, c, len + 1));
                }
            }
        }

        return true;
    }

    /**
     * Collects nodes that lie on active paths from {@code x} to {@code y}
     * and are non-colliders on those paths — i.e. nodes that conditioning
     * would block.
     *
     * <p>Only nodes in {@code pool} are returned as candidates, respecting the
     * radius constraint. Nodes in {@code notFollowed} are skipped entirely.</p>
     */
    private static Set<Node> activeCandidates(
            Graph graph,
            Node x,
            Node y,
            Set<Node> z,
            Set<Node> notFollowed,
            Map<Node, Set<Node>> descendantsMap,
            int maxPathLength,
            Set<Node> pool,
            boolean ignoreDirectEdge)
            throws InterruptedException {

        Set<Node> candidates = new LinkedHashSet<>();

        // Forward BFS from x, tracking (predecessor, current, length).
        // When we reach y, walk back along the path and collect non-colliders
        // not already in z.
        //
        // To reconstruct paths we store the predecessor for each (pred, node)
        // pair. We use a simple visited-pair set to avoid revisiting the same
        // directed step.
        Deque<PathEntry> bfsQueue = new ArrayDeque<>();
        Set<String>      seen     = new HashSet<>();

        for (Node neighbor : graph.getAdjacentNodes(x)) {
            if (ignoreDirectEdge && neighbor == y) continue;
            if (notFollowed.contains(neighbor))    continue;
            String key = x.getName() + "->" + neighbor.getName();
            if (seen.add(key)) {
                bfsQueue.add(new PathEntry(x, neighbor, 1));
            }
        }

        while (!bfsQueue.isEmpty()) {

            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }

            PathEntry entry = bfsQueue.poll();
            Node a = entry.predecessor;
            Node b = entry.current;
            int  len = entry.length;

            if (b == y) {
                // Active path reached y — a is a non-collider (the last
                // interior node before y). If it is in the pool and not
                // already in z, it is a candidate.
                // We do not add y itself.
                continue;
            }

            // b is an interior node on an active path.
            // If b is a non-collider w.r.t. the path so far (i.e. not a
            // definite collider at the triple (a, b, *)), conditioning on b
            // would block this path — add it as a candidate.
            //
            // We approximate "non-collider" conservatively: b is a candidate
            // if it is not in z, is in the pool, and is not a definite
            // collider at (a, b, some_continuation).
            if (!z.contains(b) && pool.contains(b)) {
                // Check if b acts as a non-collider on at least one
                // continuation — if so, conditioning on it could block.
                boolean isNonColliderOnSomePath = false;
                for (Node c : graph.getAdjacentNodes(b)) {
                    if (c == a) continue;
                    if (!graph.isDefCollider(a, b, c)) {
                        isNonColliderOnSomePath = true;
                        break;
                    }
                }
                if (isNonColliderOnSomePath) {
                    candidates.add(b);
                }
            }

            if (maxPathLength >= 0 && len >= maxPathLength) continue;

            for (Node c : graph.getAdjacentNodes(b)) {
                if (c == a)                         continue;
                if (notFollowed.contains(c))        continue;
                if (!reachable(graph, a, b, c, z, descendantsMap)) continue;
                String key = b.getName() + "->" + c.getName();
                if (seen.add(key)) {
                    bfsQueue.add(new PathEntry(b, c, len + 1));
                }
            }
        }

        return candidates;
    }

    // -----------------------------------------------------------------------
    // PathEntry helper for BFS
    // -----------------------------------------------------------------------

    /**
     * Lightweight triple used in the BFS queues to track (predecessor, current
     * node, path length so far).
     */
    private static final class PathEntry {
        final Node predecessor;
        final Node current;
        final int  length;

        PathEntry(Node predecessor, Node current, int length) {
            this.predecessor = predecessor;
            this.current     = current;
            this.length      = length;
        }
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

        // Iteration cap: Z can grow by at most one node per outer iteration,
        // and is bounded by pool size, so pool.size() + 1 iterations suffices
        // for convergence. We add a small buffer for safety.
        int maxIterations = pool.size() + 2;
        int iterations = 0;

        while (iterations++ < maxIterations) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }

            if (depth >= 0 && z.size() > depth) {
                return new BlockingResult(null, false);
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
                    // Roll back any Z additions from this branch and note
                    // that this pass was inconclusive. Do not count rollbacks
                    // or declare oscillation — the fixed-point loop may still
                    // converge once other branches have grown Z further.
                    z.clear();
                    z.addAll(zBefore);
                    anyIndeterminate = true;
                }
                // If BLOCKED, Z growth from this branch is valid — keep it.
            }

            if (z.size() == zSizeBefore) {
                // Z did not grow this pass.
                if (anyIndeterminate) {
                    // Some branch was inconclusive and Z didn't grow to help it —
                    // further iterations won't help either.
                    return new BlockingResult(null, true);
                }
                // Every branch was BLOCKED and Z is stable — done.
                return new BlockingResult(z, false);
            }

            // Z grew this pass — loop again. A branch that was INDETERMINATE
            // before may now be BLOCKED under the larger Z.
        }

        // Iteration cap reached without convergence.
        return new BlockingResult(null, true);
    }


    // -----------------------------------------------------------------------
    // Core algorithm
    // -----------------------------------------------------------------------

//    static Blockable findPathToTargetVisit(Graph graph,
//                                           Node aInit, Node bInit, Node y,
//                                           Set<Node> path, Set<Node> z,
//                                           int maxPathLength, int depth,
//                                           Set<Node> notFollowed,
//                                           Map<Node, Set<Node>> descendantsMap,
//                                           Set<Node> pool,
//                                           int recursionDepth,
//                                           int currentDepthInit)
//            throws InterruptedException {
//
//        Deque<Frame> callStack = new ArrayDeque<>();
//        callStack.push(new Frame(aInit, bInit, y,
//                maxPathLength, depth, recursionDepth, currentDepthInit));
//
//        Blockable lastResult = null;
//
//        while (!callStack.isEmpty()) {
//            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
//
//            Frame f = callStack.peek();
//
//            // =================================================================
//            // ENTER
//            // =================================================================
//            if (f.pass == Pass.ENTER) {
//
//                if (f.currentDepth > f.recursionDepth) {
//                    callStack.pop();
//                    lastResult = Blockable.INDETERMINATE;
//                    continue;
//                }
//
//                if (f.b == y) {
//                    callStack.pop();
//                    lastResult = Blockable.UNBLOCKABLE;
//                    continue;
//                }
//                if (path.contains(f.b)) {
//                    callStack.pop();
//                    lastResult = Blockable.BLOCKED;
//                    continue;
//                }
//                if (notFollowed.contains(f.b)) {
//                    callStack.pop();
//                    lastResult = Blockable.INDETERMINATE;
//                    continue;
//                }
//                if (notFollowed.contains(y)) {
//                    callStack.pop();
//                    lastResult = Blockable.BLOCKED;
//                    continue;
//                }
//
//                path.add(f.b);
//
//                if (f.maxPathLength >= 0 && path.size() > f.maxPathLength) {
//                    path.remove(f.b);
//                    callStack.pop();
//                    lastResult = Blockable.INDETERMINATE;
//                    continue;
//                }
//
//                f.zSnapshot = new HashSet<>(z);
//                f.pass = Pass.CONTINUATIONS_WITHOUT_B;
//            }
//
//            // =================================================================
//            // CONTINUATIONS_WITHOUT_B
//            // =================================================================
//            if (f.pass == Pass.CONTINUATIONS_WITHOUT_B) {
//
//                Blockable contResult;
//                if (lastResult != null) {
//                    if (lastResult == Blockable.UNBLOCKABLE
//                            || lastResult == Blockable.INDETERMINATE) {
//                        contResult = lastResult;
//                        lastResult = null;
//                    } else {
//                        f.handled.add(f.pendingC);
//                        f.pendingC = null;
//                        lastResult = null;
//
//                        contResult = stepContinuationLoop(
//                                graph, f, y, path, z, notFollowed, descendantsMap, pool,
//                                callStack, false);
//
//                        if (contResult == null) continue;
//                    }
//                } else {
//                    contResult = stepContinuationLoop(
//                            graph, f, y, path, z, notFollowed, descendantsMap, pool,
//                            callStack, false);
//
//                    if (contResult == null) continue;
//                }
//
//                if (f.b.getNodeType() == NodeType.LATENT) {
//                    path.remove(f.b);
//                    callStack.pop();
//                    lastResult = contResult;
//                    continue;
//                }
//
//                if (contResult == Blockable.BLOCKED) {
//                    path.remove(f.b);
//                    callStack.pop();
//                    lastResult = Blockable.BLOCKED;
//                    continue;
//                }
//
//                f.withoutBResult = contResult;
//                z.clear();
//                z.addAll(f.zSnapshot);
//
//                if (!pool.contains(f.b)) {
//                    path.remove(f.b);
//                    callStack.pop();
//                    lastResult = Blockable.INDETERMINATE;
//                    continue;
//                }
//                if (f.depth >= 0 && z.size() >= f.depth) {
//                    path.remove(f.b);
//                    callStack.pop();
//                    lastResult = Blockable.INDETERMINATE;
//                    continue;
//                }
//
//                z.add(f.b);
//                f.handled = new HashSet<>();
//                f.pendingC = null;
//                f.pass = Pass.CONTINUATIONS_WITH_B;
//                lastResult = null;
//            }
//
//            // =================================================================
//            // CONTINUATIONS_WITH_B
//            // =================================================================
//            if (f.pass == Pass.CONTINUATIONS_WITH_B) {
//
//                Blockable contResult;
//                if (lastResult != null) {
//                    if (lastResult == Blockable.UNBLOCKABLE
//                            || lastResult == Blockable.INDETERMINATE) {
//                        contResult = lastResult;
//                        lastResult = null;
//                    } else {
//                        f.handled.add(f.pendingC);
//                        f.pendingC = null;
//                        lastResult = null;
//
//                        contResult = stepContinuationLoop(
//                                graph, f, y, path, z, notFollowed, descendantsMap, pool,
//                                callStack, true);
//
//                        if (contResult == null) continue;
//                    }
//                } else {
//                    contResult = stepContinuationLoop(
//                            graph, f, y, path, z, notFollowed, descendantsMap, pool,
//                            callStack, true);
//
//                    if (contResult == null) continue;
//                }
//
//                Blockable withB = contResult;
//                if (withB == Blockable.BLOCKED) {
//                    path.remove(f.b);
//                    callStack.pop();
//                    lastResult = Blockable.BLOCKED;
//                } else {
//                    z.clear();
//                    z.addAll(f.zSnapshot);
//                    path.remove(f.b);
//                    callStack.pop();
//                    lastResult = (withB == Blockable.INDETERMINATE
//                            || f.withoutBResult == Blockable.INDETERMINATE)
//                            ? Blockable.INDETERMINATE
//                            : Blockable.UNBLOCKABLE;
//                }
//            }
//        }
//
//        return lastResult;
//    }

    // -----------------------------------------------------------------------
    // Drop-in replacement for findPathToTargetVisit and Frame.
    // The bug fix: when a child continuation returns UNBLOCKABLE, we no longer
    // short-circuit the continuation loop. Instead we record that at least one
    // continuation was unblockable (hadUnblockable flag on Frame) and keep
    // processing the remaining continuations. Only after all continuations are
    // exhausted do we return UNBLOCKABLE from the loop. This mirrors the
    // correct behaviour for BLOCKED, where each child result causes the loop
    // to advance to the next continuation rather than stopping immediately.
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
                    if (lastResult == Blockable.INDETERMINATE) {
                        // INDETERMINATE from a child short-circuits the whole
                        // continuation loop — we cannot conclude anything.
                        lastResult = null;
                        z.clear();
                        z.addAll(f.zSnapshot);
                        path.remove(f.b);
                        callStack.pop();
                        lastResult = Blockable.INDETERMINATE;
                        continue;
                    } else if (lastResult == Blockable.UNBLOCKABLE) {
                        // Record that this continuation was unblockable, but
                        // keep iterating — there may be more continuations to
                        // process, and we need to know whether ALL of them are
                        // handled before deciding the overall loop result.
                        f.hadUnblockableWithout = true;
                        f.handled.add(f.pendingC);
                        f.pendingC = null;
                        lastResult = null;
                    } else {
                        // BLOCKED — this continuation is handled, move on.
                        f.handled.add(f.pendingC);
                        f.pendingC = null;
                        lastResult = null;
                    }

                    contResult = stepContinuationLoop(
                            graph, f, y, path, z, notFollowed, descendantsMap, pool,
                            callStack, false);

                    if (contResult == null) continue; // child pushed, wait for result
                } else {
                    contResult = stepContinuationLoop(
                            graph, f, y, path, z, notFollowed, descendantsMap, pool,
                            callStack, false);

                    if (contResult == null) continue;
                }

                // stepContinuationLoop returned BLOCKED meaning no more
                // reachable continuations remain. Combine with accumulated flag.
                if (contResult == Blockable.BLOCKED) {
                    contResult = f.hadUnblockableWithout
                            ? Blockable.UNBLOCKABLE
                            : Blockable.BLOCKED;
                }

                // Now handle the overall Branch A result.
                if (f.b.getNodeType() == NodeType.LATENT) {
                    path.remove(f.b);
                    callStack.pop();
                    lastResult = contResult;
                    continue;
                }

                if (contResult == Blockable.BLOCKED) {
                    // Branch A succeeded without adding b — done.
                    path.remove(f.b);
                    callStack.pop();
                    lastResult = Blockable.BLOCKED;
                    continue;
                }

                // Branch A did not fully block (UNBLOCKABLE or INDETERMINATE).
                // Try Branch B: add b to Z and retry.
                f.withoutBResult = contResult;
                z.clear();
                z.addAll(f.zSnapshot);

                if (!pool.contains(f.b)) {
                    path.remove(f.b);
                    callStack.pop();
                    lastResult = Blockable.UNBLOCKABLE;
                    continue;
                }
                if (f.depth >= 0 && z.size() >= f.depth) {
                    path.remove(f.b);
                    callStack.pop();
                    lastResult = Blockable.UNBLOCKABLE;
                    continue;
                }

                z.add(f.b);
                f.handled = new HashSet<>();
                f.hadUnblockableWith = false;
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
                    if (lastResult == Blockable.INDETERMINATE) {
                        // INDETERMINATE short-circuits.
                        lastResult = null;
                        z.clear();
                        z.addAll(f.zSnapshot);
                        path.remove(f.b);
                        callStack.pop();
                        lastResult = Blockable.INDETERMINATE;
                        continue;
                    } else if (lastResult == Blockable.UNBLOCKABLE) {
                        // Record unblockable but keep iterating.
                        f.hadUnblockableWith = true;
                        f.handled.add(f.pendingC);
                        f.pendingC = null;
                        lastResult = null;
                    } else {
                        // BLOCKED.
                        f.handled.add(f.pendingC);
                        f.pendingC = null;
                        lastResult = null;
                    }

                    contResult = stepContinuationLoop(
                            graph, f, y, path, z, notFollowed, descendantsMap, pool,
                            callStack, true);

                    if (contResult == null) continue;
                } else {
                    contResult = stepContinuationLoop(
                            graph, f, y, path, z, notFollowed, descendantsMap, pool,
                            callStack, true);

                    if (contResult == null) continue;
                }

                // Combine with accumulated flag.
                if (contResult == Blockable.BLOCKED) {
                    contResult = f.hadUnblockableWith
                            ? Blockable.UNBLOCKABLE
                            : Blockable.BLOCKED;
                }

                if (contResult == Blockable.BLOCKED) {
                    // Branch B succeeded.
                    path.remove(f.b);
                    callStack.pop();
                    lastResult = Blockable.BLOCKED;
                } else {
                    // Both branches failed. Restore Z snapshot.
                    z.clear();
                    z.addAll(f.zSnapshot);
                    path.remove(f.b);
                    callStack.pop();
                    lastResult = (contResult == Blockable.INDETERMINATE
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

//    /**
//     * Maximum number of reachable continuations allowed at any single node
//     * before returning INDETERMINATE. If the number of reachable nodes from
//     * a given intermediate node exceeds this bound, the branch is treated as
//     * inconclusive rather than explored exhaustively. Tune this to trade off
//     * completeness against runtime on dense graphs.
//     */
//    static final int MAX_PASS_NODES = 200;
//
//    // -----------------------------------------------------------------------
//    // Drop-in replacement for stepContinuationLoop.
//    // Returns INDETERMINATE immediately if the number of reachable
//    // continuations at the current node exceeds MAX_PASS_NODES, preventing
//    // combinatorial explosion on dense graphs without requiring a global
//    // budget counter.
//    // -----------------------------------------------------------------------
//
//    private static Blockable stepContinuationLoop(
//            Graph graph,
//            Frame f,
//            Node y,
//            Set<Node> path,
//            Set<Node> z,
//            Set<Node> notFollowed,
//            Map<Node, Set<Node>> descendantsMap,
//            Set<Node> pool,
//            Deque<Frame> callStack,
//            boolean isWithBPass) throws InterruptedException {
//
//        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
//
//        List<Node> passNodes = getReachableNodes(graph, f.a, f.b, z, descendantsMap);
//        passNodes.removeAll(notFollowed);
//
//        // Count unhandled reachable continuations. If there are too many,
//        // return INDETERMINATE immediately rather than exploring them all.
//        int unhandledCount = 0;
//        for (Node c : passNodes) {
//            if (!f.handled.contains(c)) {
//                unhandledCount++;
//            }
//        }
//
//        if (unhandledCount > MAX_PASS_NODES) {
//            return Blockable.INDETERMINATE;
//        }
//
//        for (Node c : passNodes) {
//            if (f.handled.contains(c)) continue;
//
//            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
//
//            f.pendingC = c;
//            callStack.push(new Frame(
//                    f.b, c, y,
//                    f.maxPathLength, f.depth, f.recursionDepth,
//                    f.currentDepth + 1));
//            return null;
//        }
//
//        return Blockable.BLOCKED;
//    }


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
         *
         * @return true iff {@link #blockingSet} is non-null
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
//    private static final class Frame {
//
//        final Node a;
//        final Node b;
//        final Node y;
//        final int maxPathLength;
//        final int depth;
//        final int recursionDepth;
//        final int currentDepth;
//
//        Pass pass = Pass.ENTER;
//        Set<Node> zSnapshot = null;
//        Blockable withoutBResult = null;
//        Set<Node> handled = new HashSet<>();
//        Node pendingC = null;
//
//        Frame(Node a, Node b, Node y,
//              int maxPathLength, int depth, int recursionDepth, int currentDepth) {
//            this.a = a;
//            this.b = b;
//            this.y = y;
//            this.maxPathLength = maxPathLength;
//            this.depth = depth;
//            this.recursionDepth = recursionDepth;
//            this.currentDepth = currentDepth;
//        }
//    }


    // -----------------------------------------------------------------------
    // Corrected Frame — adds hadUnblockableWithout and hadUnblockableWith flags
    // -----------------------------------------------------------------------

    private static final class Frame {

        final Node a;
        final Node b;
        final Node y;
        final int maxPathLength;
        final int depth;
        final int recursionDepth;
        final int currentDepth;

        Pass      pass               = Pass.ENTER;
        Set<Node> zSnapshot          = null;
        Blockable withoutBResult     = null;
        Set<Node> handled            = new HashSet<>();
        Node      pendingC           = null;

        // Fix: track whether any continuation in each branch was UNBLOCKABLE.
        // These replace the old behaviour of short-circuiting on the first
        // UNBLOCKABLE child result.
        boolean hadUnblockableWithout = false;
        boolean hadUnblockableWith    = false;

        Frame(Node a, Node b, Node y,
              int maxPathLength, int depth, int recursionDepth, int currentDepth) {
            this.a              = a;
            this.b              = b;
            this.y              = y;
            this.maxPathLength  = maxPathLength;
            this.depth          = depth;
            this.recursionDepth = recursionDepth;
            this.currentDepth   = currentDepth;
        }
    }
}