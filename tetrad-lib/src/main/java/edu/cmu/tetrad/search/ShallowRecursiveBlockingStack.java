package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;

import java.util.*;
import java.util.concurrent.TimeoutException;

import static edu.cmu.tetrad.search.RecursiveBlocking.Blockable.BLOCKED;
import static edu.cmu.tetrad.search.RecursiveBlocking.Blockable.INDETERMINATE;
import static edu.cmu.tetrad.search.RecursiveBlocking.Blockable.UNBLOCKABLE;

/**
 * Explicit-stack (no JVM recursion) port of {@link ShallowRecursiveBlocking}
 * (the recursive "near-x" shallow-commit reference).
 *
 * <p>This is the production form: it walks forward from {@code x} to {@code y}
 * and conditions on the <em>earliest</em> blockable non-collider on each open
 * path, so the returned conditioning set clusters near {@code x}.  See
 * {@link ShallowRecursiveBlocking} for the full rationale (openness flows back
 * from {@code y}, so the scout must still trace to {@code y} to establish
 * openness; conditioning on a non-collider opens its collider continuations,
 * which leak toward {@code y}; the returned set is not minimal and is not
 * orientation-neutral).</p>
 *
 * <p><b>Nothing semantic changes vs. {@link ShallowRecursiveBlocking}.</b> The
 * verdict (BLOCKED / UNBLOCKABLE / INDETERMINATE) and the conditioning set are
 * identical; this class exists only so a deep graph cannot raise
 * {@link StackOverflowError}.  Run both on the same instances and diff the
 * results as a correctness check; pick one for production.  The mutual recursion
 * {@code findPath \u2194 blockAllContinuations} is replaced by an explicit
 * {@link Deque} of {@link Frame}s.  Each frame runs three passes: {@code ENTER}
 * (guards, snapshot Z), {@code BRANCH_A} (block continuations <em>without</em>
 * {@code b}; doubles as the openness probe), {@code BRANCH_B} (block
 * continuations <em>with</em> {@code b}).  The shallow commit rule is the
 * post-{@code BRANCH_A} block: if Branch&nbsp;A blocked without growing {@code Z},
 * the path was already closed and {@code b} is not added; otherwise the path was
 * open and we prefer to add {@code b} (Branch&nbsp;B), falling back to Branch&nbsp;A's
 * deeper solution only if adding {@code b} fails.</p>
 *
 * <p><b>One difference from {@link RecursiveBlocking}'s deep stack machine.</b>
 * There, an {@code INDETERMINATE} Branch&nbsp;A short-circuits the whole frame.
 * Here it must not: an inconclusive deep result can still be resolved by
 * conditioning on {@code b} shallowly, so Branch&nbsp;B is still attempted.</p>
 *
 * <p><b>Memoization is intentionally omitted</b> so this stays a 1:1
 * transcription of the recursive reference (diffable for identical output).  The
 * clique-optimization memo is a separate layer; a cache hit returns a verdict
 * without replaying the subtree's {@code Z} additions, which needs care to keep
 * the final {@code Z} complete \u2014 more so here, since the near-{@code x} bias
 * grows {@code Z} more aggressively.</p>
 *
 * <p><b>API.</b> Identical to {@link RecursiveBlocking#blockPathsRecursively}'s
 * full-parameter signature; returns {@link RecursiveBlocking.BlockingResult}.</p>
 */
public final class ShallowRecursiveBlockingStack {

    private ShallowRecursiveBlockingStack() {
    }

    // -----------------------------------------------------------------------
    // Public entry points (mirror RecursiveBlocking.blockPathsRecursively)
    // -----------------------------------------------------------------------

    /**
     * Shallow ("near-x") separating-set search, no deadline.
     *
     * @param graph             the graph
     * @param x                 first endpoint
     * @param y                 second endpoint
     * @param containing        nodes forced into Z (the seed set)
     * @param notFollowed       nodes not to be traversed
     * @param recursiveDepth    maximum recursion depth (-1 = graph.getNumNodes())
     * @param depth             maximum size of Z (-1 = unlimited)
     * @param maxRadius         BFS radius for the node pool (-1 = unlimited)
     * @param nearWhichEndpoint 1 = near x, 2 = near y, 3 = near both
     * @param ignoreDirectEdge  whether to ignore the direct x\u2013y edge
     * @return a {@link RecursiveBlocking.BlockingResult} describing the outcome
     * @throws InterruptedException if the thread is interrupted
     */
    public static RecursiveBlocking.BlockingResult blockPathsRecursively(
            Graph graph, Node x, Node y,
            Set<Node> containing, Set<Node> notFollowed,
            int recursiveDepth, int depth, int maxRadius,
            int nearWhichEndpoint, boolean ignoreDirectEdge)
            throws InterruptedException {
        return blockPathsRecursively(graph, x, y, containing, notFollowed,
                recursiveDepth, depth, maxRadius, nearWhichEndpoint,
                ignoreDirectEdge, Long.MAX_VALUE);
    }

    /**
     * Shallow ("near-x") separating-set search.
     *
     * @param graph             the graph
     * @param x                 first endpoint
     * @param y                 second endpoint
     * @param containing        nodes forced into Z (the seed set)
     * @param notFollowed       nodes not to be traversed
     * @param recursiveDepth    maximum recursion depth (-1 = graph.getNumNodes())
     * @param depth             maximum size of Z (-1 = unlimited)
     * @param maxRadius         BFS radius for the node pool (-1 = unlimited)
     * @param nearWhichEndpoint 1 = near x, 2 = near y, 3 = near both
     * @param ignoreDirectEdge  whether to ignore the direct x\u2013y edge
     * @param deadlineMs        deadline for the operation (in ms)
     * @return a {@link RecursiveBlocking.BlockingResult} describing the outcome
     * @throws InterruptedException if the thread is interrupted
     */
    public static RecursiveBlocking.BlockingResult blockPathsRecursively(
            Graph graph, Node x, Node y,
            Set<Node> containing, Set<Node> notFollowed,
            int recursiveDepth, int depth, int maxRadius,
            int nearWhichEndpoint, boolean ignoreDirectEdge, long deadlineMs)
            throws InterruptedException {
        try {
            return blockPathsShallowFull(graph, x, y, containing, notFollowed,
                    recursiveDepth, depth, maxRadius, nearWhichEndpoint,
                    ignoreDirectEdge, deadlineMs);
        } catch (TimeoutException e) {
            return new RecursiveBlocking.BlockingResult(null, true);
        }
    }

    // -----------------------------------------------------------------------
    // Full core + driver (the driver was already iterative; only findPath was
    // recursive, and that is what becomes the explicit-stack machine below)
    // -----------------------------------------------------------------------

    private static RecursiveBlocking.BlockingResult blockPathsShallowFull(
            Graph graph, Node x, Node y,
            Set<Node> containing, Set<Node> notFollowed,
            int recursiveDepth, int depth, int maxRadius,
            int nearWhichEndpoint, boolean ignoreDirectEdge, long deadlineMs)
            throws InterruptedException, TimeoutException {

        if (depth >= 0 && containing.size() > depth) {
            return new RecursiveBlocking.BlockingResult(null, false);
        }

        Set<Node> pool = buildPool(graph, x, y, maxRadius, nearWhichEndpoint, deadlineMs);
        pool.addAll(containing);

        int recursionCap = recursiveDepth < 0 ? graph.getNumNodes() : recursiveDepth;

        return blockPathsShallowAdj(
                graph, x, y, containing, notFollowed,
                graph.paths().getDescendantsMap(),
                recursionCap, 0, depth, pool, ignoreDirectEdge, deadlineMs);
    }

    private static RecursiveBlocking.BlockingResult blockPathsShallowAdj(
            Graph graph, Node x, Node y,
            Set<Node> containing, Set<Node> notFollowed,
            Map<Node, Set<Node>> descendantsMap,
            int recursiveDepth, int currentRecursiveDepth,
            int depth, Set<Node> pool, boolean ignoreDirectEdge, long deadlineMs)
            throws InterruptedException, TimeoutException {

        if (x == y) {
            throw new IllegalArgumentException("x and y must be distinct");
        }

        Set<Node> z = new HashSet<>(containing);

        List<Node> firstHops = new ArrayList<>(graph.getAdjacentNodes(x));
        if (ignoreDirectEdge) {
            firstHops.remove(y);
        }

        int maxIterations = pool.size() + 1;
        int iterations = 0;

        while (iterations++ < maxIterations) {
            checkTimeout(deadlineMs);

            if (depth >= 0 && z.size() > depth) {
                return new RecursiveBlocking.BlockingResult(null, false);
            }

            int zSizeBefore = z.size();
            boolean anyIndeterminate = false;

            for (Node b : firstHops) {
                checkTimeout(deadlineMs);

                Set<Node> path = new HashSet<>();
                path.add(x);

                Set<Node> zBefore = new HashSet<>(z);

                RecursiveBlocking.Blockable r = findPathToTargetVisitShallow(
                        graph, x, b, y, path, z, depth, notFollowed, descendantsMap,
                        pool, recursiveDepth, currentRecursiveDepth, deadlineMs);

                if (r == UNBLOCKABLE) {
                    return new RecursiveBlocking.BlockingResult(null, false);
                }

                if (r == INDETERMINATE) {
                    z.clear();
                    z.addAll(zBefore);
                    anyIndeterminate = true;
                }
            }

            if (z.size() == zSizeBefore) {
                if (anyIndeterminate) {
                    return new RecursiveBlocking.BlockingResult(null, true);
                }
                return new RecursiveBlocking.BlockingResult(z, false);
            }
        }

        return new RecursiveBlocking.BlockingResult(null, true);
    }

    // -----------------------------------------------------------------------
    // The recursive step, as an explicit-stack state machine.
    //
    // Each Frame is one findPath(a, b, ...) invocation. blockAllContinuations is
    // inlined into the BRANCH_A / BRANCH_B passes via stepContinuationLoop, which
    // pushes one child frame at a time and recomputes reachability between
    // children (Z may have grown). The shallow commit rule is the post-BRANCH_A
    // block.
    // -----------------------------------------------------------------------

    static RecursiveBlocking.Blockable findPathToTargetVisitShallow(
            Graph graph, Node aInit, Node bInit, Node y,
            Set<Node> path, Set<Node> z, int depth,
            Set<Node> notFollowed, Map<Node, Set<Node>> descendantsMap, Set<Node> pool,
            int recursiveDepth, int currentRecursiveDepth, long deadlineMs)
            throws InterruptedException, TimeoutException {

        Deque<Frame> callStack = new ArrayDeque<>();
        callStack.push(new Frame(aInit, bInit, y, depth, recursiveDepth, currentRecursiveDepth));

        RecursiveBlocking.Blockable lastResult = null;

        while (!callStack.isEmpty()) {
            checkTimeout(deadlineMs);
            Frame f = callStack.peek();

            // ================================================================
            // ENTER
            // ================================================================
            if (f.pass == Pass.ENTER) {
                if (f.currentRecursiveDepth > f.recursiveDepth) {
                    callStack.pop();
                    lastResult = INDETERMINATE;
                    continue;
                }
                if (f.b == y) {
                    callStack.pop();
                    lastResult = UNBLOCKABLE;          // reached target
                    continue;
                }
                if (path.contains(f.b)) {
                    callStack.pop();
                    lastResult = BLOCKED;              // cycle
                    continue;
                }
                if (notFollowed.contains(f.b)) {
                    callStack.pop();
                    lastResult = INDETERMINATE;
                    continue;
                }
                if (notFollowed.contains(y)) {
                    callStack.pop();
                    lastResult = BLOCKED;
                    continue;
                }

                path.add(f.b);
                f.zSnapshot = new HashSet<>(z);
                f.zSizeAtEntry = z.size();
                f.pass = Pass.BRANCH_A;
                lastResult = null;
                // fall through to BRANCH_A
            }

            // ================================================================
            // BRANCH_A: block continuations WITHOUT b (also the openness probe)
            // ================================================================
            if (f.pass == Pass.BRANCH_A) {
                RecursiveBlocking.Blockable rA;

                if (lastResult == INDETERMINATE) {
                    // A child was inconclusive. Unlike the deep machine, do NOT
                    // short-circuit the frame -- conditioning on b may still
                    // resolve it. Treat Branch A as INDETERMINATE and try B.
                    rA = INDETERMINATE;
                    lastResult = null;
                } else {
                    if (lastResult != null) {                 // a child (BLOCKED/UNBLOCKABLE) returned
                        if (lastResult == UNBLOCKABLE) f.hadUnblockableWithout = true;
                        f.handled.add(f.pendingC);
                        f.pendingC = null;
                        lastResult = null;
                    }
                    RecursiveBlocking.Blockable step = stepContinuationLoop(
                            graph, f, y, z, notFollowed, descendantsMap, callStack, deadlineMs);
                    if (step == null) continue;               // a new child was pushed
                    rA = f.hadUnblockableWithout ? UNBLOCKABLE : BLOCKED;
                }

                // ---- shallow decision after Branch A ----
                f.withoutBResult = rA;

                if (rA == BLOCKED && z.size() == f.zSizeAtEntry) {
                    // Branch A blocked without adding anything => the path through
                    // b was already closed; do not condition on b.
                    path.remove(f.b);
                    callStack.pop();
                    lastResult = BLOCKED;
                    continue;
                }

                // Path was open (A grew Z) or A failed. Prefer to commit at b.
                f.zAfterA = new HashSet<>(z);                 // A's deep solution, if rA == BLOCKED
                z.clear();
                z.addAll(f.zSnapshot);                        // roll back A's additions

                boolean latent = f.b.getNodeType() == NodeType.LATENT;
                boolean canAddB = !latent && pool.contains(f.b)
                        && (f.depth < 0 || z.size() < f.depth);

                if (!canAddB) {
                    path.remove(f.b);
                    callStack.pop();
                    if (rA == BLOCKED) {
                        z.clear();
                        z.addAll(f.zAfterA);
                        lastResult = BLOCKED;
                    } else {
                        lastResult = rA;                      // UNBLOCKABLE or INDETERMINATE
                    }
                    continue;
                }

                // Set up Branch B.
                z.add(f.b);
                f.handled = new HashSet<>();
                f.pendingC = null;
                f.hadUnblockableWith = false;
                f.pass = Pass.BRANCH_B;
                lastResult = null;
                // fall through to BRANCH_B
            }

            // ================================================================
            // BRANCH_B: block continuations WITH b
            // ================================================================
            if (f.pass == Pass.BRANCH_B) {
                RecursiveBlocking.Blockable rB;

                if (lastResult == INDETERMINATE) {
                    rB = INDETERMINATE;
                    lastResult = null;
                } else {
                    if (lastResult != null) {
                        if (lastResult == UNBLOCKABLE) f.hadUnblockableWith = true;
                        f.handled.add(f.pendingC);
                        f.pendingC = null;
                        lastResult = null;
                    }
                    RecursiveBlocking.Blockable step = stepContinuationLoop(
                            graph, f, y, z, notFollowed, descendantsMap, callStack, deadlineMs);
                    if (step == null) continue;
                    rB = f.hadUnblockableWith ? UNBLOCKABLE : BLOCKED;
                }

                if (rB == BLOCKED) {
                    // Committed at b (near x). Z keeps b plus whatever was needed
                    // to block the collider continuations adding b opened.
                    path.remove(f.b);
                    callStack.pop();
                    lastResult = BLOCKED;
                    continue;
                }

                // Branch B failed. Roll it back; fall back to A's solution if it
                // had one, else propagate the stronger failure.
                z.clear();
                z.addAll(f.zSnapshot);
                path.remove(f.b);
                callStack.pop();

                if (f.withoutBResult == BLOCKED) {
                    z.clear();
                    z.addAll(f.zAfterA);
                    lastResult = BLOCKED;
                } else {
                    lastResult = (f.withoutBResult == INDETERMINATE || rB == INDETERMINATE)
                            ? INDETERMINATE : UNBLOCKABLE;
                }
                continue;
            }
        }

        return lastResult;
    }

    /**
     * Advances one continuation of the current branch.  Recomputes the reachable
     * continuations of {@code (a, b)} under the current {@code z} (so colliders
     * opened by a prior child's Z-growth are picked up), skips those already
     * {@code handled}, and pushes the first remaining one as a child frame.
     *
     * @return {@code null} if a child frame was pushed (caller should suspend),
     * or {@code BLOCKED} if no unhandled reachable continuation remains.
     */
    private static RecursiveBlocking.Blockable stepContinuationLoop(
            Graph graph, Frame f, Node y, Set<Node> z,
            Set<Node> notFollowed, Map<Node, Set<Node>> descendantsMap,
            Deque<Frame> callStack, long deadlineMs)
            throws InterruptedException, TimeoutException {

        checkTimeout(deadlineMs);

        List<Node> passNodes = getReachableNodes(graph, f.a, f.b, z, descendantsMap, deadlineMs);
        passNodes.removeAll(notFollowed);

        for (Node c : passNodes) {
            checkTimeout(deadlineMs);
            if (f.handled.contains(c)) continue;

            f.pendingC = c;
            callStack.push(new Frame(
                    f.b, c, y,
                    f.depth, f.recursiveDepth, f.currentRecursiveDepth + 1));
            return null;
        }

        return BLOCKED;
    }

    // -----------------------------------------------------------------------
    // Helpers (verbatim from RecursiveBlocking)
    // -----------------------------------------------------------------------

    private static List<Node> getReachableNodes(Graph graph, Node a, Node b,
                                                Set<Node> z,
                                                Map<Node, Set<Node>> descendantsMap,
                                                long deadlineMs)
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

    private static boolean reachable(Graph graph, Node a, Node b, Node c,
                                     Set<Node> z, Map<Node, Set<Node>> descendantsMap,
                                     long deadlineMs)
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

    private static void checkTimeout(long deadlineMs) throws InterruptedException, TimeoutException {
        if (deadlineMs > 0 && System.currentTimeMillis() > deadlineMs) {
            throw new TimeoutException("timed out");
        }
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("interrupted");
        }
    }

    // -----------------------------------------------------------------------
    // Frame + passes
    // -----------------------------------------------------------------------

    /**
     * Which pass a frame is executing.
     *
     * <pre>
     *   ENTER     -> guard checks, path.add(b), snapshot Z
     *   BRANCH_A  -> block continuations WITHOUT b (openness probe + deep solution)
     *   BRANCH_B  -> block continuations WITH b (the near-x commit)
     * </pre>
     * <p>A frame for a LATENT b never reaches BRANCH_B (it cannot be added to Z),
     * handled by the {@code canAddB} test after BRANCH_A.</p>
     */
    private enum Pass {
        ENTER,
        BRANCH_A,
        BRANCH_B
    }

    private static final class Frame {

        final Node a;
        final Node b;
        final Node y;
        final int depth;
        final int recursiveDepth;
        final int currentRecursiveDepth;

        Pass pass = Pass.ENTER;

        // Z at frame entry (for rollback), and its size (to detect whether
        // Branch A actually had to block anything -- the openness signal).
        Set<Node> zSnapshot = null;
        int zSizeAtEntry = 0;

        // Branch A's solution, captured before rolling back to prefer Branch B;
        // restored if adding b (Branch B) fails but A had succeeded.
        Set<Node> zAfterA = null;
        RecursiveBlocking.Blockable withoutBResult = null;

        // Continuation-loop state for the current branch (reset between A and B).
        Set<Node> handled = new HashSet<>();
        Node pendingC = null;
        boolean hadUnblockableWithout = false;
        boolean hadUnblockableWith = false;

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