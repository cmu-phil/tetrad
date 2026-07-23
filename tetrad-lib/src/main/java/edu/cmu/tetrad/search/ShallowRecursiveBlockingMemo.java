package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;

import java.util.*;
import java.util.concurrent.TimeoutException;

import static edu.cmu.tetrad.search.RecursiveBlocking.Blockable.BLOCKED;
import static edu.cmu.tetrad.search.RecursiveBlocking.Blockable.INDETERMINATE;
import static edu.cmu.tetrad.search.RecursiveBlocking.Blockable.UNBLOCKABLE;

/**
 * {@link ShallowRecursiveBlockingStack} plus a delta-replay continuation memo.
 *
 * <p>Same algorithm, same results as the no-memo stack version (and the
 * recursive reference); this class adds the clique optimization that
 * {@link RecursiveBlocking} carries, adapted so it is correct for the
 * shallow-commit variant.</p>
 *
 * <p><b>Why a delta memo, not a verdict-only memo.</b> {@link RecursiveBlocking}
 * caches the verdict alone and, on a hit, returns it without replaying the
 * nodes that the original frame added to {@code Z}.  That is tolerable for the
 * deep machine, whose commit decision never inspects whether {@code Z} grew, but
 * it is unsafe here: the shallow machine's entire "already closed vs. open"
 * decision <em>is</em> the Z-growth signal (Branch A blocked without growing
 * {@code Z} ⇒ already closed ⇒ do not condition on {@code b}).  A verdict-only
 * hit on a child would report BLOCKED with no growth, the parent would read that
 * as "already closed," and a required near-{@code x} conditioning node would
 * silently never be added.  So we cache the pair
 * {@code (verdict, delta = Z\u2011added)} and, on a hit, do {@code z.addAll(delta)}
 * before returning the verdict.</p>
 *
 * <p><b>Why delta replay is correct.</b> The key is {@code (a, b, entry\u2011Z)} and
 * we cache only <em>determined</em> verdicts (BLOCKED with its delta, or
 * UNBLOCKABLE with empty delta), never INDETERMINATE.  A determined verdict is a
 * depth-independent fact: "{@code Z \u222a delta} blocks every {@code b\u2192y}
 * continuation" (BLOCKED) or "some continuation is unblockable regardless of
 * {@code Z}" (UNBLOCKABLE) holds no matter the arrival depth or the path taken
 * to reach {@code (a, b)}.  Because the key pins entry-{@code Z}, a hit means the
 * live {@code Z} equals the stored entry-{@code Z}, so replaying {@code delta}
 * reproduces the original frame's final {@code Z} node-for-node \u2014 which also
 * keeps the {@code depth} cap satisfied (same final size) and preserves the
 * openness signal for the caller.</p>
 *
 * <p><b>Taint.</b> The one path-dependent verdict is a cycle hit
 * ({@code b \u2208 path} ⇒ BLOCKED), which is an artifact of where we came from.
 * Such a verdict, and any ancestor verdict computed using it, must not be
 * cached.  A {@code pathTainted} flag propagates from a cycle-hit child to its
 * parent and onward; tainted frames are never stored.  Taint gates
 * <em>storing</em> only \u2014 a cache <em>hit</em> is always safe because stored
 * entries are untainted and therefore path-independent.  The flag is single and
 * never reset, so a Branch-A cycle hit conservatively taints a frame even when
 * the final verdict came from Branch B; this matches {@link RecursiveBlocking}
 * and keeps the correctness argument simple, at the cost of some cache entries.</p>
 *
 * <p><b>Scope.</b> The memo lives for one {@code findPathToTargetVisitShallow}
 * call (one first hop, one driver iteration).  It is deliberately not shared
 * across first hops or fixed-point iterations, because {@code Z} grows between
 * them and a per-call cache keeps every key consistent with the {@code Z} in
 * effect when each frame was entered.</p>
 *
 * <p>The memo must be a pure speedup: results must be identical to
 * {@link ShallowRecursiveBlockingStack} on every instance.  Any divergence is a
 * memo bug.</p>
 */
public final class ShallowRecursiveBlockingMemo {

    private ShallowRecursiveBlockingMemo() {
    }

    // -----------------------------------------------------------------------
    // Public entry points
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
    // Full core + driver (unchanged from the stack version)
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
        // Consistency with stepContinuationLoop, which silently drops
        // not-followed continuations (skip = assume blocked; the data test is
        // the safety net). Without this, a not-followed neighbor of x reaches
        // the ENTER guard, returns INDETERMINATE, and taints the whole call.
        firstHops.removeAll(notFollowed);

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
    // Explicit-stack recursive step, with delta-replay memo
    // -----------------------------------------------------------------------

    static RecursiveBlocking.Blockable findPathToTargetVisitShallow(
            Graph graph, Node aInit, Node bInit, Node y,
            Set<Node> path, Set<Node> z, int depth,
            Set<Node> notFollowed, Map<Node, Set<Node>> descendantsMap, Set<Node> pool,
            int recursiveDepth, int currentRecursiveDepth, long deadlineMs)
            throws InterruptedException, TimeoutException {

        Deque<Frame> callStack = new ArrayDeque<>();
        callStack.push(new Frame(aInit, bInit, y, depth, recursiveDepth, currentRecursiveDepth,
                graph.getEndpoint(aInit, bInit) == Endpoint.ARROW));

        // Per-call delta-replay memo: (a, b, entry-Z) -> (verdict, Z-added).
        Map<MemoKey, MemoVal> memo = new HashMap<>();

        RecursiveBlocking.Blockable lastResult = null;

        while (!callStack.isEmpty()) {
            checkTimeout(deadlineMs);
            Frame f = callStack.peek();

            // ================================================================
            // ENTER
            // ================================================================
            if (f.pass == Pass.ENTER) {
                // Cheap, possibly path-dependent guards FIRST (before the memo):
                // a cycle hit must take precedence over any path-independent
                // cached verdict for (a, b, z).
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
                    Frame parent = callStack.peek();
                    if (parent != null) parent.pathTainted = true;   // cycle hit: taint, do not cache
                    lastResult = BLOCKED;
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

                // Memo lookup -- a hit resolves the frame with zero exploration.
                MemoKey key = new MemoKey(f.a, f.b, z, f.arrivedHead);
                MemoVal cached = memo.get(key);
                if (cached != null) {
                    callStack.pop();
                    z.addAll(cached.delta());          // replay the subtree's Z additions
                    lastResult = cached.verdict();
                    continue;
                }

                // Miss: do real work. Charge the frame.
                f.cacheKey = key;
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
                    rA = INDETERMINATE;                // do NOT short-circuit; try B
                    lastResult = null;
                } else {
                    if (lastResult != null) {
                        if (lastResult == UNBLOCKABLE) f.hadUnblockableWithout = true;
                        f.handled.add(f.pendingC);
                        f.pendingC = null;
                        lastResult = null;
                    }
                    RecursiveBlocking.Blockable step = stepContinuationLoop(
                            graph, f, y, z, notFollowed, descendantsMap, callStack, deadlineMs);
                    if (step == null) continue;        // child pushed
                    rA = f.hadUnblockableWithout ? UNBLOCKABLE : BLOCKED;
                }

                // ---- shallow decision after Branch A ----
                f.withoutBResult = rA;

                if (rA == BLOCKED && z.size() == f.zSizeAtEntry) {
                    // Already closed under current Z: do not condition on b.
                    path.remove(f.b);
                    callStack.pop();
                    lastResult = finishAndCache(f, BLOCKED, z, callStack, memo);
                    continue;
                }

                // Path was open (A grew Z) or A failed. Prefer to commit at b.
                f.zAfterA = new HashSet<>(z);          // A's deep solution, if rA == BLOCKED
                z.clear();
                z.addAll(f.zSnapshot);                 // roll back A's additions

                boolean latent = f.b.getNodeType() == NodeType.LATENT;
                boolean canAddB = !latent && pool.contains(f.b)
                        && (f.depth < 0 || z.size() < f.depth)
                        && addingBClosesANonCollider(graph, f.a, f.b, z, notFollowed, descendantsMap, f.arrivedHead, deadlineMs);

                if (!canAddB) {
                    path.remove(f.b);
                    if (rA == BLOCKED) {
                        z.clear();
                        z.addAll(f.zAfterA);
                        callStack.pop();
                        lastResult = finishAndCache(f, BLOCKED, z, callStack, memo);
                    } else {
                        callStack.pop();
                        lastResult = finishAndCache(f, rA, z, callStack, memo);  // UNBLOCKABLE or INDETERMINATE
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
                    // Committed at b (near x). Z keeps b plus the deeper fixups
                    // for the collider continuations adding b opened.
                    path.remove(f.b);
                    callStack.pop();
                    lastResult = finishAndCache(f, BLOCKED, z, callStack, memo);
                    continue;
                }

                // Branch B failed. Roll it back; fall back to A's solution if it
                // had one, else propagate the stronger failure.
                z.clear();
                z.addAll(f.zSnapshot);
                path.remove(f.b);

                if (f.withoutBResult == BLOCKED) {
                    z.clear();
                    z.addAll(f.zAfterA);
                    callStack.pop();
                    lastResult = finishAndCache(f, BLOCKED, z, callStack, memo);
                } else {
                    callStack.pop();
                    RecursiveBlocking.Blockable combined =
                            (f.withoutBResult == INDETERMINATE || rB == INDETERMINATE)
                                    ? INDETERMINATE : UNBLOCKABLE;
                    lastResult = finishAndCache(f, combined, z, callStack, memo);
                }
                continue;
            }
        }

        return lastResult;
    }

    /**
     * Finalizes a just-popped frame: propagates path-taint to the parent (now on
     * top of the stack) and stores {@code (result, delta)} in {@code memo} when
     * safe.  {@code delta} is the net Z additions of this frame's subtree,
     * {@code z \ zSnapshot}, computed from the live {@code z} at the moment of
     * finishing (after any zAfterA restore).
     *
     * <ul>
     *   <li>Tainted frames are not cached; their taint propagates to the parent.</li>
     *   <li>INDETERMINATE is never cached (a search-limit, not a fact).</li>
     *   <li>Otherwise store {@code (result, z \ zSnapshot)} under {@code cacheKey}.</li>
     * </ul>
     *
     * <p>Must be called <em>after</em> {@code callStack.pop()} so {@code peek()}
     * is the parent.</p>
     */
    private static RecursiveBlocking.Blockable finishAndCache(
            Frame f, RecursiveBlocking.Blockable result,
            Set<Node> z, Deque<Frame> callStack, Map<MemoKey, MemoVal> memo) {

        if (f.pathTainted) {
            Frame parent = callStack.peek();
            if (parent != null) parent.pathTainted = true;
            return result;
        }

        if (result != INDETERMINATE && f.cacheKey != null) {
            Set<Node> delta = new HashSet<>(z);
            delta.removeAll(f.zSnapshot);
            memo.put(f.cacheKey, new MemoVal(result, delta));
        }

        return result;
    }

    private static RecursiveBlocking.Blockable stepContinuationLoop(
            Graph graph, Frame f, Node y, Set<Node> z,
            Set<Node> notFollowed, Map<Node, Set<Node>> descendantsMap,
            Deque<Frame> callStack, long deadlineMs)
            throws InterruptedException, TimeoutException {

        checkTimeout(deadlineMs);

        List<Node> passNodes = getReachableNodes(graph, f.a, f.b, z, descendantsMap, f.arrivedHead, deadlineMs);
        passNodes.removeAll(notFollowed);

        for (Node c : passNodes) {
            checkTimeout(deadlineMs);
            if (f.handled.contains(c)) continue;

            // Arrival mark at c: the graph's own arrowhead at c, or the one
            // FORCED by passing through b as a non-collider after arriving
            // head-in at b. Head-in plus non-collider status forces a tail at
            // b on the b-c edge, and a MAG edge with a tail at b is b -> c,
            // i.e., an arrowhead at c -- even if the graph shows a circle.
            boolean colliderAtB = f.arrivedHead && graph.getEndpoint(c, f.b) == Endpoint.ARROW;
            boolean childArrivedHead = (!colliderAtB && f.arrivedHead)
                    || graph.getEndpoint(f.b, c) == Endpoint.ARROW;

            f.pendingC = c;
            callStack.push(new Frame(
                    f.b, c, y,
                    f.depth, f.recursiveDepth, f.currentRecursiveDepth + 1,
                    childArrivedHead));
            return null;
        }

        return BLOCKED;
    }

    // -----------------------------------------------------------------------
    // Helpers (verbatim from RecursiveBlocking)
    // -----------------------------------------------------------------------

    /**
     * True iff conditioning on {@code b} would close at least one currently-open
     * continuation -- i.e. some reachable continuation {@code (a, b, c)} is a
     * plain non-collider, which {@code b \u2208 Z} blocks. Colliders and underline
     * triples are <em>not</em> closed by adding {@code b} (it opens them), so if
     * every open continuation through {@code b} is one of those, conditioning on
     * {@code b} closes nothing and we must block deeper instead.
     *
     * <p>This is the guard that keeps {@code Z} honest: {@code b} is conditioned
     * on only to close a non-collider continuation. It is a pure function of
     * {@code (a, b, z)} and the graph, so it does not disturb the memo: the
     * cached verdict and delta for a key remain a deterministic function of that
     * key.</p>
     */
    private static boolean addingBClosesANonCollider(
            Graph graph, Node a, Node b, Set<Node> z, Set<Node> notFollowed,
            Map<Node, Set<Node>> descendantsMap, boolean arrivedHead, long deadlineMs)
            throws InterruptedException, TimeoutException {
        for (Node c : getReachableNodes(graph, a, b, z, descendantsMap, arrivedHead, deadlineMs)) {
            checkTimeout(deadlineMs);
            if (notFollowed.contains(c)) continue;
            if (!(arrivedHead && graph.getEndpoint(c, b) == Endpoint.ARROW)) {
                return true;   // open non-collider continuation; b in Z would block it
            }
        }
        return false;
    }

    private static List<Node> getReachableNodes(Graph graph, Node a, Node b,
                                                Set<Node> z,
                                                Map<Node, Set<Node>> descendantsMap,
                                                boolean arrivedHead,
                                                long deadlineMs)
            throws InterruptedException, TimeoutException {
        checkTimeout(deadlineMs);

        List<Node> passNodes = new ArrayList<>();
        for (Node c : graph.getAdjacentNodes(b)) {
            checkTimeout(deadlineMs);
            if (c == a) continue;
            if (reachable(graph, a, b, c, z, descendantsMap, arrivedHead, deadlineMs)) {
                passNodes.add(c);
            }
        }
        return passNodes;
    }

    private static boolean reachable(Graph graph, Node a, Node b, Node c,
                                     Set<Node> z, Map<Node, Set<Node>> descendantsMap,
                                     boolean arrivedHead, long deadlineMs)
            throws InterruptedException, TimeoutException {
        checkTimeout(deadlineMs);

        // Definite-or-forced collider at b on THIS path: we arrived at b
        // through a definite or forced arrowhead, and the b-c edge has an
        // arrowhead at b. arrivedHead incorporates the graph's own mark at
        // every push, so this subsumes isDefCollider; the extra strength is
        // the forced case, which closes phantom "non-collider chains" like
        // x *-> u o-o v <-* y that no consistent MAG orientation leaves open.
        boolean collider = arrivedHead && graph.getEndpoint(c, b) == Endpoint.ARROW;

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
    // Memo key / value
    // -----------------------------------------------------------------------

    /**
     * Memoization value: a determined verdict and the net Z additions
     * ({@code delta}) that produced it.  {@code delta} is empty for UNBLOCKABLE.
     */
    private record MemoVal(RecursiveBlocking.Blockable verdict, Set<Node> delta) {
    }

    /**
     * Memoization key {@code (a, b, entry-Z)}.  {@code z} is snapshot-copied at
     * construction so later mutation of the live {@code z} does not corrupt
     * stored keys.
     */
    private static final class MemoKey {
        private final Node a;
        private final Node b;
        private final Set<Node> z;
        private final int hash;

        private final boolean arrivedHead;

        MemoKey(Node a, Node b, Set<Node> z, boolean arrivedHead) {
            this.a = a;
            this.b = b;
            this.z = new HashSet<>(z);
            this.arrivedHead = arrivedHead;
            this.hash = Objects.hash(a, b, this.z, arrivedHead);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof MemoKey)) return false;
            MemoKey other = (MemoKey) o;
            return hash == other.hash
                    && a == other.a
                    && b == other.b
                    && arrivedHead == other.arrivedHead
                    && z.equals(other.z);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    // -----------------------------------------------------------------------
    // Frame + passes
    // -----------------------------------------------------------------------

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

        // True iff the endpoint at b on the a-b edge is a definite arrowhead,
        // OR an arrowhead forced by the parent's non-collider passage (head-in
        // at the parent forces a tail there, hence an arrowhead here). Part of
        // the memo key: the same (a, b, entry-Z) can carry different verdicts
        // under different arrival marks.
        final boolean arrivedHead;

        Pass pass = Pass.ENTER;

        Set<Node> zSnapshot = null;
        int zSizeAtEntry = 0;

        Set<Node> zAfterA = null;
        RecursiveBlocking.Blockable withoutBResult = null;

        Set<Node> handled = new HashSet<>();
        Node pendingC = null;
        boolean hadUnblockableWithout = false;
        boolean hadUnblockableWith = false;

        // Memo support.
        // pathTainted: this frame's verdict used a cycle-hit (path-dependent)
        //   child verdict; never cached, propagates to the parent.
        // cacheKey: (a, b, entry-Z) under which to store on an untainted finish;
        //   null until set at the lookup point (so guard exits are never cached).
        boolean pathTainted = false;
        MemoKey cacheKey = null;

        Frame(Node a, Node b, Node y,
              int depth, int recursiveDepth, int currentRecursiveDepth,
              boolean arrivedHead) {
            this.a = a;
            this.b = b;
            this.y = y;
            this.depth = depth;
            this.recursiveDepth = recursiveDepth;
            this.currentRecursiveDepth = currentRecursiveDepth;
            this.arrivedHead = arrivedHead;
        }
    }
}
