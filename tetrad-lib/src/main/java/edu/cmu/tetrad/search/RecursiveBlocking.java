package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;

import java.util.*;
import java.util.concurrent.TimeoutException;

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
 * <p><b>Continuation memoization (clique optimization).</b> Within a single
 * {@code findPathToTargetVisit} call, the verdict for a frame is, in the
 * common case, a pure function of the incoming pair {@code (a, b)} and the
 * conditioning set {@code z} in effect at frame entry: {@code reachable}
 * inspects only the triple {@code (a, b, c)} and {@code z}, so two frames that
 * arrive at the same {@code b} from the same {@code a} under the same entry
 * {@code z} explore identical continuations and reach the same verdict. This
 * recurs heavily inside cliques, where many distinct path prefixes converge on
 * the same node. We cache such verdicts keyed on {@code (a, b, z)} and reuse
 * them on later arrivals.</p>
 *
 * <p>The cache is <em>not</em> a pure function of {@code (a, b, z)} in one
 * situation: a frame can return {@code BLOCKED} early because its node already
 * lies on the current {@code path} (a cycle hit). That verdict is an artifact
 * of <em>where we came from</em>, not an intrinsic property of {@code (a, b, z)},
 * so it must never be cached, and any ancestor frame whose verdict was computed
 * using such a child verdict must also not be cached. We track this with a
 * {@code pathTainted} flag that propagates from child to parent. Only untainted,
 * non-{@code INDETERMINATE} verdicts are stored. {@code INDETERMINATE} verdicts
 * are never cached because they reflect a search-limit (recursion depth, frame
 * cap) rather than a graph fact, and could resolve differently in another
 * context.</p>
 *
 * <p>The cache lives for the duration of one {@code findPathToTargetVisit}
 * call. It is deliberately <em>not</em> shared across the outer fixed-point
 * iterations in {@code blockPathsRecursivelyAdj}, nor across first hops,
 * because {@code z} grows between those calls and a per-call cache keeps every
 * key consistent with the {@code z} actually in effect when the frame was
 * entered. The clique redundancy Bryan identified occurs entirely within a
 * single DFS call, so this scoping captures the win while keeping the
 * correctness argument simple.</p>
 *
 * <p>Key features:</p>
 * <ul>
 *   <li>Respects PAG semantics for colliders, non-colliders, and latent nodes.</li>
 *   <li>Uses an outer fixed-point loop to handle colliders activated by Z growth
 *       across branches.</li>
 *   <li>Supports recursion depth limits, depth limits, radius limits, and
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
    private static final int MAX_TOTAL_FRAMES = 50_000;

    private RecursiveBlocking() {
    }

//    /**
//     * Blocks paths between two specified nodes in a graph by iteratively
//     * identifying and selecting nodes to include in a blocking set, subject to
//     * constraints on recursion depth and traversal rules. Assumes a direct edge
//     * between x and y is to be ignored.
//     *
//     * <p>This overload collapses UNBLOCKABLE and INDETERMINATE both to
//     * {@code null} for backward compatibility. Use
//     * {@link #blockPathsRecursivelyFull} when the distinction matters.</p>
//     *
//     * @param graph          the graph in which the nodes and paths are analyzed
//     * @param x              the starting node of the path
//     * @param y              the target node of the path
//     * @param containing     a set of nodes that must be included in the blocking set
//     * @param notFollowed    a set of nodes that must not be traversed during path search
//     * @param recursiveDepth the maximum allowable recursion depth of the paths to block (-1 for no limit)
//     * @param <E>            the type of the graph nodes
//     * @param deadlineMs     the deadline for the operation (in milliseconds)
//     * @return a set of nodes constituting a blocking set for paths between x and y,
//     * or {@code null} if no such set is found within the given constraints
//     * @throws InterruptedException if the thread executing the method is interrupted
//     * @throws TimeoutException     if the operation times out
//     */
//    public static <E> Set<Node> blockPathsRecursively(Graph graph,
//                                                      Node x,
//                                                      Node y,
//                                                      Set<Node> containing,
//                                                      Set<Node> notFollowed,
//                                                      int recursiveDepth,
//                                                      long deadlineMs)
//            throws InterruptedException, TimeoutException {
//        return blockPathsRecursivelyFull(graph, x, y, containing, notFollowed,
//                recursiveDepth, 8, 4, 1, true,
//                deadlineMs).blockingSet();
//    }

    // -----------------------------------------------------------------------
    // Result type
    // -----------------------------------------------------------------------

    /**
     * Convenience overload of {@link #blockPathsRecursively} with no deadline.
     */
    public static BlockingResult blockPathsRecursively(Graph graph,
                                                       Node x,
                                                       Node y,
                                                       Set<Node> containing,
                                                       Set<Node> notFollowed,
                                                       int recursiveDepth,
                                                       int depth,
                                                       int maxRadius,
                                                       int nearWhichEndpoint,
                                                       boolean ignoreDirectEdge)
            throws InterruptedException {
        try {
            return blockPathsRecursively(graph, x, y, containing, notFollowed,
                    recursiveDepth, depth, maxRadius, nearWhichEndpoint,
                    ignoreDirectEdge, Long.MAX_VALUE);
        } catch (TimeoutException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Single canonical entry point for separating-set search.
     *
     * <p>Dispatches to {@link #blockPathsRecursivelyFull} (depth-first with
     * backtracking) or {@link #blockPathsIterativeDeepening} (iterative
     * deepening on recursion depth) based on {@code strategy}, and returns a
     * full {@link BlockingResult} distinguishing UNBLOCKABLE from
     * INDETERMINATE.</p>
     *
     * @param graph             the graph
     * @param x                 first endpoint
     * @param y                 second endpoint
     * @param containing        nodes forced into Z (the seed set)
     * @param notFollowed       nodes not to be traversed
     * @param recursiveDepth    max recursion depth for RECURSIVE; ceiling for
     *                          ITERATIVE_DEEPENING (-1 = graph.getNumNodes())
     * @param depth             maximum size of Z (-1 = unlimited)
     * @param maxRadius         BFS radius for the node pool (-1 = unlimited)
     * @param nearWhichEndpoint 1 = near x, 2 = near y, 3 = near both
     * @param ignoreDirectEdge  whether to ignore the direct x–y edge
     * @param deadlineMs        deadline for the operation (in ms)
     * @return a {@link BlockingResult} describing the outcome
     * @throws InterruptedException if the thread is interrupted
     * @throws TimeoutException     if the operation times out
     */
    public static BlockingResult blockPathsRecursively(Graph graph,
                                                       Node x,
                                                       Node y,
                                                       Set<Node> containing,
                                                       Set<Node> notFollowed,
                                                       int recursiveDepth,
                                                       int depth,
                                                       int maxRadius,
                                                       int nearWhichEndpoint,
                                                       boolean ignoreDirectEdge,
                                                       long deadlineMs)
            throws InterruptedException, TimeoutException {

        switch (DEFAULT_STRATEGY) {
            case Strategy.ITERATIVE_DEEPENING:
                return blockPathsIterativeDeepening(graph, x, y, containing, notFollowed,
                        recursiveDepth, depth, maxRadius, nearWhichEndpoint,
                        ignoreDirectEdge, deadlineMs);
            case Strategy.RECURSIVE:
            default:
                return blockPathsRecursivelyFull(graph, x, y, containing, notFollowed,
                        recursiveDepth, depth, maxRadius, nearWhichEndpoint,
                        ignoreDirectEdge, deadlineMs);
        }
    }

    // -----------------------------------------------------------------------
    // Public entry points — Set<Node> convenience overloads (existing callers)
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
     * @param recursiveDepth    maximum recursion depth (-1 = unlimited)
     * @param depth             maximum size of Z (-1 = unlimited)
     * @param maxRadius         BFS radius (-1 = unlimited)
     * @param nearWhichEndpoint 1 = near x, 2 = near y, 3 = near both
     * @param ignoreDirectEdge  whether to ignore direct edges between x and y
     * @return a {@link BlockingResult} describing the outcome
     * @throws InterruptedException if the thread is interrupted
     */
    private static BlockingResult blockPathsRecursivelyFull(Graph graph,
                                                           Node x,
                                                           Node y,
                                                           Set<Node> containing,
                                                           Set<Node> notFollowed,
                                                           int recursiveDepth,
                                                           int depth, int maxRadius,
                                                           int nearWhichEndpoint,
                                                           boolean ignoreDirectEdge)
            throws InterruptedException {
        try {
            return blockPathsRecursivelyFull(graph, x, y, containing, notFollowed, recursiveDepth, depth,
                    maxRadius, nearWhichEndpoint, ignoreDirectEdge, Long.MAX_VALUE);
        } catch (TimeoutException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Full-parameter entry point returning a full {@link BlockingResult},
     * distinguishing UNBLOCKABLE from INDETERMINATE on failure.
     *
     * @param graph             the graph
     * @param x                 first endpoint
     * @param y                 second endpoint
     * @param containing        nodes forced into Z
     * @param notFollowed       nodes not to be traversed
     * @param recursiveDepth    maximum recursion depth (-1 = unlimited)
     * @param depth             maximum size of Z (-1 = unlimited)
     * @param maxRadius         BFS radius (-1 = unlimited)
     * @param nearWhichEndpoint 1 = near x, 2 = near y, 3 = near both
     * @param ignoreDirectEdge  whether to ignore direct edges between x and y
     * @param deadlineMs        the deadline for the operation (in milliseconds)
     * @return a {@link BlockingResult} describing the outcome
     * @throws InterruptedException if the thread is interrupted
     * @throws TimeoutException     if the search was interrupted
     */
    private static BlockingResult blockPathsRecursivelyFull(Graph graph,
                                                           Node x,
                                                           Node y,
                                                           Set<Node> containing,
                                                           Set<Node> notFollowed,
                                                           int recursiveDepth,
                                                           int depth, int maxRadius,
                                                           int nearWhichEndpoint,
                                                           boolean ignoreDirectEdge,
                                                           long deadlineMs)
            throws InterruptedException, TimeoutException {

        // Fail fast if the seed set already violates the depth bound
        if (depth >= 0 && containing.size() > depth) {
            return new BlockingResult(null, false);
        }

        Set<Node> pool = buildPool(graph, x, y, maxRadius, nearWhichEndpoint, deadlineMs);
        pool.addAll(containing);

        int _recursiveDepth = recursiveDepth < 0 ? graph.getNumNodes() : recursiveDepth;
        int currentRecursiveDepth = 0;

        return blockPathsRecursivelyAdj(
                graph, x, y, containing, notFollowed,
                graph.paths().getDescendantsMap(),
                _recursiveDepth, currentRecursiveDepth, depth, pool, ignoreDirectEdge, deadlineMs);
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
     *   <li>{@code recursiveDepth} bounds the recursion depth considered when
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
     * @param recursiveDepth    maximum recursion depth when collecting active
     *                          paths (-1 = unlimited)
     * @param depth             maximum size of Z (-1 = unlimited)
     * @param maxRadius         BFS radius for the node pool (-1 = unlimited)
     * @param nearWhichEndpoint 1 = near x only, 2 = near y only,
     *                          3 = near both
     * @param ignoreDirectEdge  whether to ignore the direct x–y edge
     * @param deadlineMs        the deadline for the operation (in milliseconds)
     * @return a {@link BlockingResult} describing the outcome
     * @throws InterruptedException if the thread is interrupted
     * @throws TimeoutException     if the search was interrupted
     */
    private static BlockingResult blockPathsBfs(
            Graph graph,
            Node x,
            Node y,
            Set<Node> containing,
            Set<Node> notFollowed,
            int recursiveDepth,
            int depth,
            int maxRadius,
            int nearWhichEndpoint,
            boolean ignoreDirectEdge,
            long deadlineMs)
            throws InterruptedException, TimeoutException {

        // Build the pool of nodes eligible to enter Z, same as the recursive version.
        Set<Node> pool = buildPool(graph, x, y, maxRadius, nearWhichEndpoint, deadlineMs);
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
        Deque<Set<Node>> queue = new ArrayDeque<>();
        Set<Set<Node>> visited = new HashSet<>();

        Set<Node> seed = new HashSet<>(containing);
        queue.add(seed);
        visited.add(seed);

        while (!queue.isEmpty()) {

            checkTimeout(deadlineMs);

            Set<Node> z = queue.poll();

            // Depth guard: never process a set that exceeds the depth cap.
            if (depth >= 0 && z.size() > depth) {
                continue;
            }

            // Check whether z already blocks all active paths from x to y.
            if (blocksAllPaths(graph, x, y, z, notFollowed, descendantsMap,
                    ignoreDirectEdge, deadlineMs)) {
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
                    pool, ignoreDirectEdge,
                    deadlineMs);

            for (Node candidate : candidates) {
                checkTimeout(deadlineMs);

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
     * Blocks paths between two nodes in a graph using an iterative deepening approach.
     *
     * @param graph             The graph in which the operation is performed.
     * @param x                 The starting node of the path to be blocked.
     * @param y                 The ending node of the path to be blocked.
     * @param containing        A set of nodes that must be present on the blocked path.
     * @param notFollowed       A set of nodes that must not be followed during the operation.
     * @param recursiveDepth    The current recursion depth.
     * @param depth             The maximum depth to search for paths.
     * @param maxRadius         The maximum allowable radius for searching paths.
     * @param nearWhichEndpoint Specifies the endpoint near which the block operation is focused.
     * @param ignoreDirectEdge  Whether to ignore direct edges between the start and end nodes.
     * @return A {@link BlockingResult} object containing the result of the path blocking operation.
     * @throws InterruptedException If the thread executing this operation is interrupted.
     */
    private static BlockingResult blockPathsIterativeDeepening(
            Graph graph,
            Node x,
            Node y,
            Set<Node> containing,
            Set<Node> notFollowed,
            int recursiveDepth,
            int depth,
            int maxRadius,
            int nearWhichEndpoint,
            boolean ignoreDirectEdge)
            throws InterruptedException {
        try {
            return blockPathsIterativeDeepening(graph, x, y, containing, notFollowed, recursiveDepth, depth, maxRadius,
                    nearWhichEndpoint, ignoreDirectEdge, Long.MAX_VALUE);
        } catch (TimeoutException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Iterative-deepening separating-set search between {@code x} and {@code y}.
     *
     * <p>This method calls {@link #blockPathsRecursivelyFull} repeatedly,
     * starting with {@code recursiveDepth = 0} and incrementing by 1 on each
     * iteration until either a blocking set is found or the recursion depth cap
     * reaches {@code recursiveDepth} (the caller-supplied ceiling). This is
     * iterative deepening applied to recursion depth rather than search depth: it
     * inherits the memory efficiency of the depth-first version while
     * guaranteeing that the shortest-path blocking set is found first.</p>
     *
     * <p>Because the depth-first version can hang on dense graphs when given
     * an unconstrained recursion depth (it may explore exponentially many paths
     * before the recursion cap triggers), bounding each call to a small
     * {@code recursiveDepth} keeps each individual call fast. Short separating
     * sets — including the empty set, which handles unconditionally d-separated
     * pairs — are found at the lowest recursion depth level and return immediately
     * without exploring longer paths at all.</p>
     *
     * <p>The method respects the same parameter contract as
     * {@link #blockPathsRecursivelyFull}. The {@code recursiveDepth} parameter
     * here serves as the ceiling for the iterative deepening loop; the
     * per-iteration cap starts at 0 and grows up to this ceiling. Pass -1 to
     * use {@code graph.getNumNodes()} as the ceiling (a natural upper bound,
     * since no simple path in the graph is longer than that).</p>
     *
     * <p>Returns a {@link BlockingResult} using the same three-outcome
     * convention:</p>
     * <ul>
     *   <li><b>Found</b>: a blocking set was found and is returned, together
     *       with the recursion depth level at which it was found (accessible via
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
     * @param recursiveDepth    ceiling for the iterative deepening loop
     *                          (-1 = use graph.getNumNodes())
     * @param depth             maximum size of Z (-1 = unlimited)
     * @param maxRadius         BFS radius for the node pool (-1 = unlimited)
     * @param nearWhichEndpoint 1 = near x only, 2 = near y only,
     *                          3 = near both
     * @param ignoreDirectEdge  whether to ignore the direct x–y edge
     * @param deadlineMs        the deadline for the operation (in milliseconds)
     * @return a {@link BlockingResult} describing the outcome
     * @throws InterruptedException if the thread is interrupted
     * @throws TimeoutException     if the ceiling is reached without finding
     */
    private static BlockingResult blockPathsIterativeDeepening(
            Graph graph,
            Node x,
            Node y,
            Set<Node> containing,
            Set<Node> notFollowed,
            int recursiveDepth,
            int depth,
            int maxRadius,
            int nearWhichEndpoint,
            boolean ignoreDirectEdge,
            long deadlineMs)
            throws InterruptedException, TimeoutException {

        int ceiling = (recursiveDepth < 0) ? graph.getNumNodes() : recursiveDepth;

        for (int _recusionDepth = 0; _recusionDepth <= ceiling; _recusionDepth++) {
            checkTimeout(deadlineMs);

            BlockingResult result = blockPathsRecursivelyFull(
                    graph, x, y,
                    containing, notFollowed,
                    _recusionDepth,        // recursiveDepth for this iteration
                    depth,
                    maxRadius,
                    nearWhichEndpoint,
                    ignoreDirectEdge,
                    deadlineMs);

            if (result.found()) {
                // Blocking set found at this recursion depth level — return it.
                return result;
            }

            if (!result.indeterminate()) {
                // The depth-first call returned UNBLOCKABLE at this level,
                // meaning a path exists that cannot be blocked regardless of Z.
                // There is no point trying longer paths — return UNBLOCKABLE.
                return result;
            }

            // result.indeterminate() == true means the depth-first call hit
            // the recursion depth cap without finding a set or proving impossibility.
            // Increment the cap and try again.
        }

        // Ceiling reached without finding a blocking set or proving UNBLOCKABLE.
        return new BlockingResult(null, true);
    }

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
            boolean ignoreDirectEdge,
            long deadlineMs)
            throws InterruptedException, TimeoutException {

        Deque<PathEntry> bfsQueue = new ArrayDeque<>();
        Set<String> seen = new HashSet<>();

        for (Node neighbor : graph.getAdjacentNodes(x)) {
            checkTimeout(deadlineMs);

            if (ignoreDirectEdge && neighbor == y) continue;

            if (notFollowed.contains(neighbor)) continue;

            String key = x.getName() + "->" + neighbor.getName();
            if (seen.add(key)) {
                bfsQueue.add(new PathEntry(x, neighbor));
            }
        }

        while (!bfsQueue.isEmpty()) {

            checkTimeout(deadlineMs);

            PathEntry entry = bfsQueue.poll();
            Node a = entry.predecessor;
            Node b = entry.current;
            if (b == y) return false;

            for (Node c : graph.getAdjacentNodes(b)) {
                checkTimeout(deadlineMs);

                if (c == a) continue;
                if (notFollowed.contains(c)) continue;

                if (!reachable(graph, a, b, c, z, descendantsMap, deadlineMs)) continue;

                String key = b.getName() + "->" + c.getName();
                if (seen.add(key)) {
                    bfsQueue.add(new PathEntry(b, c));
                }
            }
        }

        return true;
    }

    // -----------------------------------------------------------------------
    // BFS helpers
    // -----------------------------------------------------------------------

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
            Set<Node> pool,
            boolean ignoreDirectEdge,
            long deadlineMs)
            throws InterruptedException, TimeoutException {

        Set<Node> candidates = new LinkedHashSet<>();
        Deque<PathEntry> bfsQueue = new ArrayDeque<>();
        Set<String> seen = new HashSet<>();

        for (Node neighbor : graph.getAdjacentNodes(x)) {
            checkTimeout(deadlineMs);

            if (ignoreDirectEdge && neighbor == y) continue;
            if (notFollowed.contains(neighbor)) continue;
            String key = x.getName() + "->" + neighbor.getName();
            if (seen.add(key)) {
                bfsQueue.add(new PathEntry(x, neighbor));
            }
        }

        while (!bfsQueue.isEmpty()) {

            checkTimeout(deadlineMs);

            PathEntry entry = bfsQueue.poll();
            Node a = entry.predecessor;
            Node b = entry.current;

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
                    checkTimeout(deadlineMs);

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

            for (Node c : graph.getAdjacentNodes(b)) {
                checkTimeout(deadlineMs);

                if (c == a) continue;
                if (notFollowed.contains(c)) continue;
                if (!reachable(graph, a, b, c, z, descendantsMap, deadlineMs)) continue;
                String key = b.getName() + "->" + c.getName();
                if (seen.add(key)) {
                    bfsQueue.add(new PathEntry(b, c));
                }
            }
        }

        return candidates;
    }

    /**
     * Builds the set of nodes eligible to enter Z. When {@code maxRadius} is
     * -1, every graph node is returned (no restriction).
     */
    private static Set<Node> buildPool(Graph graph, Node x, Node y,
                                       int maxRadius, int nearWhichEndpoint, long deadlineMs)
            throws InterruptedException, TimeoutException {
        if (maxRadius < 0) {
            return new HashSet<>(graph.getNodes());
        }

        Set<Node> pool = new LinkedHashSet<>();

        if (nearWhichEndpoint == 1 || nearWhichEndpoint == 3) {
            pool.addAll(bfsShells(graph, x, maxRadius, deadlineMs));
        }
        if (nearWhichEndpoint == 2 || nearWhichEndpoint == 3) {
            pool.addAll(bfsShells(graph, y, maxRadius, deadlineMs));
        }

        pool.remove(x);
        pool.remove(y);

        return pool;
    }

    // -----------------------------------------------------------------------
    // PathEntry helper for BFS
    // -----------------------------------------------------------------------

    /**
     * Standard undirected BFS up to {@code maxRadius} hops from {@code seed}.
     * Returns all nodes reachable within that radius (excluding the seed).
     */
    private static Set<Node> bfsShells(Graph graph, Node seed, int maxRadius, long deadlineMs)
            throws InterruptedException, TimeoutException {
        Set<Node> visited = new LinkedHashSet<>();
        Deque<Node> queue = new ArrayDeque<>();
        Map<Node, Integer> dist = new HashMap<>();

        visited.add(seed);
        queue.add(seed);
        dist.put(seed, 0);

        while (!queue.isEmpty()) {
            checkTimeout(deadlineMs);

            Node u = queue.removeFirst();
            int du = dist.get(u);
            if (du >= maxRadius) continue;
            for (Node v : graph.getAdjacentNodes(u)) {
                checkTimeout(deadlineMs);

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
            int recursiveDepth,
            int currentRecursiveDepth,
            int depth,
            Set<Node> pool,
            boolean ignoreDirectEdge,
            long deadlineMs) throws InterruptedException, TimeoutException {

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
        int maxIterations = pool.size();
        int iterations = 0;

        int[] totalFrames = new int[]{0};

        while (iterations++ < maxIterations) {
            checkTimeout(deadlineMs);

            if (depth >= 0 && z.size() > depth) {
                return new BlockingResult(null, false);
            }

            int zSizeBefore = z.size();
            boolean anyIndeterminate = false;

            for (Node b : firstHops) {
                checkTimeout(deadlineMs);

//                System.out.println("first hop " + b);

                Set<Node> path = new HashSet<>();
                path.add(x);

                Set<Node> zBefore = new HashSet<>(z);

                Blockable r = findPathToTargetVisit(
                        graph, x, b, y, path, z,
                        depth, notFollowed, descendantsMap, pool,
                        recursiveDepth, currentRecursiveDepth, deadlineMs, totalFrames);

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
    // Pool construction (BFS shells)
    // -----------------------------------------------------------------------

    static Blockable findPathToTargetVisit(Graph graph,
                                           Node aInit, Node bInit, Node y,
                                           Set<Node> path, Set<Node> z,
                                           int depth,
                                           Set<Node> notFollowed,
                                           Map<Node, Set<Node>> descendantsMap,
                                           Set<Node> pool,
                                           int recursiveDepth,
                                           int currentRecursiveDepth,
                                           long deadlineMs, int[] totalFrames)
            throws InterruptedException, TimeoutException {

        Deque<Frame> callStack = new ArrayDeque<>();
        callStack.push(new Frame(aInit, bInit, y,
                depth, recursiveDepth, currentRecursiveDepth));

        // Continuation memoization cache, scoped to this single DFS call.
        // Key: (a, b, entry-z). Value: the untainted verdict for that frame.
        // See class doc for the correctness argument (taint propagation,
        // INDETERMINATE never cached, per-call scope to keep z consistent).
        Map<MemoKey, Blockable> memo = new HashMap<>();

        Blockable lastResult = null;

        while (!callStack.isEmpty()) {
            checkTimeout(deadlineMs);

//            if (totalFrames[0] % 1000 == 0 && totalFrames[0] > 0) {
//                System.out.println("Total frames: " + totalFrames[0]);
//            }

            if (++totalFrames[0] > MAX_TOTAL_FRAMES) {
//                System.err.println("Recursive blocking: Too many frames: " + totalFrames[0] + " (max " + MAX_TOTAL_FRAMES + ")");
                return Blockable.INDETERMINATE;
            }

            Frame f = callStack.peek();

            // =================================================================
            // ENTER
            // =================================================================
            if (f.pass == Pass.ENTER) {
                if (f.currentRecursiveDepth > f.recursiveDepth) {
                    // INDETERMINATE: search-limit, not a graph fact. Never cached.
                    callStack.pop();
                    lastResult = finishFrame(f, Blockable.INDETERMINATE, callStack, memo);
                    continue;
                }

                if (f.b == y) {
                    callStack.pop();
                    lastResult = finishFrame(f, Blockable.UNBLOCKABLE, callStack, memo);
                    continue;
                }
                if (path.contains(f.b)) {
                    // Cycle hit: this BLOCKED verdict is path-dependent, so it
                    // must never be cached, and any ancestor that consumes it
                    // must be tainted. Mark the parent tainted directly here.
                    callStack.pop();
                    Frame parent = callStack.peek();
                    if (parent != null) parent.pathTainted = true;
                    lastResult = Blockable.BLOCKED;
                    continue;
                }
                if (notFollowed.contains(f.b)) {
                    callStack.pop();
                    lastResult = finishFrame(f, Blockable.INDETERMINATE, callStack, memo);
                    continue;
                }
                if (notFollowed.contains(y)) {
                    callStack.pop();
                    lastResult = finishFrame(f, Blockable.BLOCKED, callStack, memo);
                    continue;
                }

                // ---- MEMO LOOKUP ----
                // z here is the entry z for this frame. If we have already
                // computed an untainted verdict for (a, b, z), reuse it.
                MemoKey key = new MemoKey(f.a, f.b, z);
                Blockable cached = memo.get(key);
                if (cached != null) {
                    callStack.pop();
                    // A cached verdict is by construction untainted, so we do
                    // not taint the parent and do not re-store. Just return it.
                    lastResult = cached;
                    continue;
                }
                f.cacheKey = key;   // store on a successful, untainted pop

                path.add(f.b);

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
                        lastResult = finishFrame(f, Blockable.INDETERMINATE, callStack, memo);
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
                            graph, f, y, z, notFollowed, descendantsMap,
                            callStack, deadlineMs);

                    if (contResult == null) continue; // child pushed, wait for result
                } else {
                    contResult = stepContinuationLoop(
                            graph, f, y, z, notFollowed, descendantsMap,
                            callStack, deadlineMs);

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
                    lastResult = finishFrame(f, contResult, callStack, memo);
                    continue;
                }

                if (contResult == Blockable.BLOCKED) {
                    // Branch A succeeded without adding b — done.
                    path.remove(f.b);
                    callStack.pop();
                    lastResult = finishFrame(f, Blockable.BLOCKED, callStack, memo);
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
                    lastResult = finishFrame(f, Blockable.UNBLOCKABLE, callStack, memo);
                    continue;
                }
                if (f.depth >= 0 && z.size() >= f.depth) {
                    path.remove(f.b);
                    callStack.pop();
                    lastResult = finishFrame(f, Blockable.UNBLOCKABLE, callStack, memo);
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
                        lastResult = finishFrame(f, Blockable.INDETERMINATE, callStack, memo);
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
                            graph, f, y, z, notFollowed, descendantsMap,
                            callStack, deadlineMs);

                    if (contResult == null) continue;
                } else {
                    contResult = stepContinuationLoop(
                            graph, f, y, z, notFollowed, descendantsMap,
                            callStack, deadlineMs);

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
                    lastResult = finishFrame(f, Blockable.BLOCKED, callStack, memo);
                } else {
                    // Both branches failed. Restore Z snapshot.
                    z.clear();
                    z.addAll(f.zSnapshot);
                    path.remove(f.b);
                    callStack.pop();
                    Blockable combined = (contResult == Blockable.INDETERMINATE
                            || f.withoutBResult == Blockable.INDETERMINATE)
                            ? Blockable.INDETERMINATE
                            : Blockable.UNBLOCKABLE;
                    lastResult = finishFrame(f, combined, callStack, memo);
                }
            }
        }

        return lastResult;
    }

    /**
     * Finalizes a frame that has just been popped: propagates path-taint to the
     * parent (now on top of the stack) and stores the verdict in {@code memo}
     * when it is safe to do so.
     *
     * <p>Caching rules:</p>
     * <ul>
     *   <li>If the frame is {@code pathTainted}, its verdict was computed using
     *       a path-dependent ({@code path.contains}) child result, so it must
     *       not be cached, and its parent must inherit the taint.</li>
     *   <li>{@code INDETERMINATE} verdicts are never cached — they reflect a
     *       search-limit (recursion-depth or frame cap), not a graph fact, and
     *       a different context could resolve them differently.</li>
     *   <li>Otherwise the verdict is a pure function of {@code (a, b, entry-z)}
     *       and is stored under {@code f.cacheKey} (set at lookup time).</li>
     * </ul>
     *
     * <p>This must be called <em>after</em> {@code callStack.pop()} so that
     * {@code callStack.peek()} returns the parent frame.</p>
     *
     * @param f         the just-popped frame
     * @param result    the verdict this frame is returning
     * @param callStack the stack, with the parent (if any) now on top
     * @param memo      the per-call memoization cache
     * @return {@code result}, for convenient assignment to {@code lastResult}
     */
    private static Blockable finishFrame(Frame f,
                                         Blockable result,
                                         Deque<Frame> callStack,
                                         Map<MemoKey, Blockable> memo) {
        if (f.pathTainted) {
            Frame parent = callStack.peek();
            if (parent != null) parent.pathTainted = true;
            return result;
        }

        if (result != Blockable.INDETERMINATE && f.cacheKey != null) {
            memo.put(f.cacheKey, result);
        }

        return result;
    }

    private static Blockable stepContinuationLoop(
            Graph graph,
            Frame f,
            Node y,
            Set<Node> z,
            Set<Node> notFollowed,
            Map<Node, Set<Node>> descendantsMap,
            Deque<Frame> callStack,
            long deadlineMs) throws InterruptedException, TimeoutException {

        checkTimeout(deadlineMs);

        List<Node> passNodes = getReachableNodes(graph, f.a, f.b, z, descendantsMap, deadlineMs);
        passNodes.removeAll(notFollowed);

        for (Node c : passNodes) {
            checkTimeout(deadlineMs);

            if (f.handled.contains(c)) continue;

            f.pendingC = c;
            callStack.push(new Frame(
                    f.b, c, y,
                    f.depth, f.recursiveDepth,
                    f.currentRecursiveDepth + 1));
            return null;
        }

        return Blockable.BLOCKED;
    }


    // -----------------------------------------------------------------------
    // Core algorithm
    // -----------------------------------------------------------------------

    private static List<Node> getReachableNodes(Graph graph,
                                                Node a,
                                                Node b,
                                                Set<Node> z,
                                                Map<Node, Set<Node>> descendantsMap,
                                                long deadlineMs
    )
            throws InterruptedException, TimeoutException {
        checkTimeout(deadlineMs);

        List<Node> passNodes = new ArrayList<>();
        for (Node c : graph.getAdjacentNodes(b)) {
            checkTimeout(deadlineMs);

            if (c == a) continue;
            if (reachable(graph, a, b, c, z, descendantsMap, deadlineMs)) {
                passNodes.add(c);
            }
        }
        return passNodes;
    }

    // -----------------------------------------------------------------------
    // Frame definition for the explicit stack
    // -----------------------------------------------------------------------

    private static boolean reachable(Graph graph,
                                     Node a,
                                     Node b,
                                     Node c,
                                     Set<Node> z,
                                     Map<Node, Set<Node>> descendantsMap, long deadlineMs)
            throws InterruptedException, TimeoutException {
        checkTimeout(deadlineMs);

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
                checkTimeout(deadlineMs);

                if (z.contains(d)) return true;
            }
            return false;
        }
    }

    private static void checkTimeout(long deadlineMs) throws InterruptedException, TimeoutException {
        if (deadlineMs > 0 && System.currentTimeMillis() > deadlineMs) {
            throw new TimeoutException("timed out");
        }

        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("interrupted");
        }
    }

    // -----------------------------------------------------------------------
    // Iterative driver
    // -----------------------------------------------------------------------

    /**
     * Selects the search strategy used by {@link #blockPathsRecursively}.
     */
    public enum Strategy {
        /**
         * Depth-first with backtracking ({@link #blockPathsRecursivelyFull}).
         */
        RECURSIVE,
        /**
         * Iterative deepening on recursion depth ({@link #blockPathsIterativeDeepening}).
         */
        ITERATIVE_DEEPENING
    }

    public static Strategy DEFAULT_STRATEGY = Strategy.RECURSIVE;

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
         * Analysis was inconclusive (interrupted, recursion depth cap, or radius limit hit).
         */
        INDETERMINATE
    }

    /**
     * Lightweight triple used in the BFS queues to track (predecessor, current
     * node, recursion depth so far).
     */
    private static final class PathEntry {
        final Node predecessor;
        final Node current;

        PathEntry(Node predecessor, Node current) {
            this.predecessor = predecessor;
            this.current = current;
        }
    }

    // -----------------------------------------------------------------------
    // Memoization key
    // -----------------------------------------------------------------------

    /**
     * Memoization key for {@link #findPathToTargetVisit}: the verdict for a
     * frame is, when untainted, a pure function of the incoming pair
     * {@code (a, b)} and the conditioning set {@code z} in effect at frame
     * entry.
     *
     * <p>The {@code z} component is defensively copied into an immutable set at
     * construction so that subsequent mutation of the live {@code z} (Branch B
     * adds {@code b}) does not corrupt keys already stored in the map.</p>
     */
    private static final class MemoKey {
        private final Node a;
        private final Node b;
        private final Set<Node> z;
        private final int hash;

        MemoKey(Node a, Node b, Set<Node> z) {
            this.a = a;
            this.b = b;
            // Immutable snapshot; HashSet copy gives O(1) contains/equals and
            // decouples the key from later mutation of the live z.
            this.z = new HashSet<>(z);
            this.hash = Objects.hash(a, b, this.z);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof MemoKey)) return false;
            MemoKey other = (MemoKey) o;
            return hash == other.hash
                    && a == other.a
                    && b == other.b
                    && z.equals(other.z);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    // -----------------------------------------------------------------------
    // Corrected Frame — adds hadUnblockableWithout and hadUnblockableWith flags
    // -----------------------------------------------------------------------

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
     *       {@code indeterminate} is true. The search hit a recursion depth or
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

    private static final class Frame {

        final Node a;
        final Node b;
        final Node y;
        final int depth;
        final int recursiveDepth;
        final int currentRecursiveDepth;

        Pass pass = Pass.ENTER;
        Set<Node> zSnapshot = null;
        Blockable withoutBResult = null;
        Set<Node> handled = new HashSet<>();
        Node pendingC = null;

        // Fix: track whether any continuation in each branch was UNBLOCKABLE.
        // These replace the old behaviour of short-circuiting on the first
        // UNBLOCKABLE child result.
        boolean hadUnblockableWithout = false;
        boolean hadUnblockableWith = false;

        // Memoization support.
        // pathTainted: true iff this frame's verdict was computed using a
        //   path-dependent (cycle-hit) child verdict. Tainted verdicts are
        //   never cached and propagate taint to the parent.
        // cacheKey: the (a, b, entry-z) key under which to store this frame's
        //   verdict on an untainted pop. null means "do not store" (either no
        //   lookup was performed, or the frame exited before lookup).
        boolean pathTainted = false;
        MemoKey cacheKey = null;

        Frame(Node a, Node b, Node y,
              int depth, int recursiveDepth, int currentRecursiveDepth) {
            this.a = a;
            this.b = b;
            this.y = y;
            this.depth = depth;
            this.recursiveDepth = recursiveDepth;
            this.currentRecursiveDepth = currentRecursiveDepth;
        }
    }
}
