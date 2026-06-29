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
 * Shallow-commit ("near-x") variant of {@link RecursiveBlocking}.
 *
 * <p>{@link RecursiveBlocking} walks forward from {@code x} toward {@code y}
 * and prefers to discharge each open path as <em>deep</em> as possible: at an
 * intermediate node {@code b} it first tries to block all continuations
 * <em>without</em> {@code b} (pushing the block toward {@code y}), and only
 * conditions on {@code b} when nothing past {@code b} can close the path.  The
 * result is a conditioning set that clusters near {@code y}.</p>
 *
 * <p>This class keeps the forward walk {@code x \u2192 y} <em>identical</em> but
 * flips the commit preference: at each intermediate node {@code b} it prefers to
 * condition on {@code b} itself (the shallow, near-{@code x} node) as soon as it
 * has established that a path through {@code b} is open.  Only the
 * <em>placement</em> of conditioning nodes changes; the set of paths explored,
 * and the three-valued verdict (BLOCKED / UNBLOCKABLE / INDETERMINATE), are the
 * same disjunction over the two branches as in {@link RecursiveBlocking}.</p>
 *
 * <p><b>Why the walk cannot become lazy.</b> Openness flows backward from
 * {@code y}: a prefix {@code x\u22efb} is open iff it extends to an active path that
 * actually reaches {@code y}, and that cannot be read off locally at {@code b}.
 * So the scout still has to walk all the way to {@code y} to learn whether
 * {@code b} needs blocking at all.  It just commits near {@code x} once openness
 * is established, rather than near {@code y}.</p>
 *
 * <p><b>The openness probe is folded into Branch&nbsp;A.</b> Running
 * {@code BlockAllContinuations} without {@code b} both checks openness and (on
 * the deep version) would block the path.  We exploit a monotonicity fact: that
 * call only ever <em>adds</em> to {@code Z}, and it adds something iff there was
 * a genuinely open path through {@code b} that had to be blocked.  Hence
 * "Branch&nbsp;A returned BLOCKED and {@code Z} did not grow" is exactly
 * "the path through {@code b} was already closed \u2014 do not condition on
 * {@code b}".  When {@code Z} did grow (or Branch&nbsp;A failed), the path was
 * open; we roll back Branch&nbsp;A's deeper additions and prefer to add
 * {@code b} instead.  A merely-reachable continuation that never reaches
 * {@code y} adds nothing, so it is correctly treated as "already closed" \u2014 this
 * is why a naive "are there reachable continuations?" test would be wrong and
 * the {@code Z}-growth test is right.</p>
 *
 * <p><b>Caveats (all inherent, not artifacts of this implementation):</b></p>
 * <ul>
 *   <li><b>The clustering is not pure.</b> Conditioning on a non-collider
 *       {@code b} blocks its non-collider continuations but <em>opens</em> its
 *       collider continuations, which must then be blocked deeper \u2014 toward
 *       {@code y}.  So a near-{@code x} set still leaks a tail toward {@code y}
 *       wherever colliders sit on the open paths.  ({@link RecursiveBlocking}'s
 *       reverse-role trick has the same leak, mirrored.)</li>
 *   <li><b>Larger sets, more work.</b> This variant always runs Branch&nbsp;A
 *       (for openness) and usually Branch&nbsp;B as well, whereas the deep
 *       version often stops after Branch&nbsp;A.  Worse, adding {@code b} near
 *       {@code x} activates colliders that are <em>ancestors</em> of {@code b},
 *       which can spawn new active first-hops in the driver's fixed-point loop.
 *       It is structurally more exposed to combinatorial blow-up than the deep
 *       version.  The returned set is not minimal.</li>
 *   <li><b>Not orientation-neutral.</b> The returned set is a sepset, and FCI
 *       orients {@code b} as a collider on {@code a *-* b *-* c} exactly when
 *       {@code b \u2209 sepset(a, c)}.  A near-{@code x} set and a near-{@code y} set
 *       can disagree on whether a given {@code b} is included even when both
 *       genuinely m-separate, so they can yield different colliders and
 *       different PAGs.  Switch deliberately, and re-check any downstream
 *       legality/maximality argument that leaned on the deep-set structure.</li>
 * </ul>
 *
 * <p><b>Implementation note.</b> Unlike {@link RecursiveBlocking}, which uses an
 * explicit stack to avoid {@link StackOverflowError} on deep graphs, this class
 * is written as straightforward mutual recursion ({@code findPathShallow} \u2194
 * {@code blockAllContinuations}) so the shallow commit rule is easy to read and
 * validate against Procedures&nbsp;1\u20133 of the paper.  Recursion depth is bounded
 * by {@code recursiveDepth} (\u2264 number of nodes), which is fine for typical
 * FCI/FCIT use; for very deep graphs, port this to the explicit-stack machine in
 * {@link RecursiveBlocking} (the openness/commit split has to thread across the
 * ENTER / WITHOUT_B / WITH_B passes).</p>
 *
 * <p><b>API.</b> Mirrors {@link RecursiveBlocking#blockPathsRecursively}'s
 * full-parameter signature and returns the same {@link RecursiveBlocking.BlockingResult}
 * record, so it is a drop-in alternative at call sites that already consume that
 * type.  Only the RECURSIVE-style depth-first strategy is provided; an
 * iterative-deepening or BFS wrapper can be added exactly as in
 * {@link RecursiveBlocking} if wanted.</p>
 */
public final class ShallowRecursiveBlocking {

    private ShallowRecursiveBlocking() {
    }

    // -----------------------------------------------------------------------
    // Public entry points (mirror RecursiveBlocking.blockPathsRecursively)
    // -----------------------------------------------------------------------

    /**
     * Shallow ("near-x") separating-set search between {@code x} and {@code y},
     * with no deadline.  See {@link #blockPathsRecursively(Graph, Node, Node,
     * Set, Set, int, int, int, int, boolean, long)}.
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
     * Shallow ("near-x") separating-set search between {@code x} and {@code y}.
     *
     * <p>Walks forward from {@code x} to {@code y} (never reversing roles) and
     * conditions on the <em>earliest</em> blockable non-collider on each open
     * path, so the returned conditioning set clusters near {@code x}.  Returns a
     * full {@link RecursiveBlocking.BlockingResult} distinguishing UNBLOCKABLE
     * (proven no separator within the structure) from INDETERMINATE (a search
     * limit was hit).  A timeout is reported as INDETERMINATE.</p>
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
            // Timeout is a search-limit, not a structural fact -> INDETERMINATE.
            return new RecursiveBlocking.BlockingResult(null, true);
        }
    }

    // -----------------------------------------------------------------------
    // Full core: build pool, then run the fixed-point driver
    // -----------------------------------------------------------------------

    private static RecursiveBlocking.BlockingResult blockPathsShallowFull(
            Graph graph, Node x, Node y,
            Set<Node> containing, Set<Node> notFollowed,
            int recursiveDepth, int depth, int maxRadius,
            int nearWhichEndpoint, boolean ignoreDirectEdge, long deadlineMs)
            throws InterruptedException, TimeoutException {

        // Fail fast if the seed already violates the depth bound.
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

    // -----------------------------------------------------------------------
    // Procedure 1: driver (fixed-point loop over first hops). Same control
    // flow as RecursiveBlocking.blockPathsRecursivelyAdj; only the recursive
    // step differs.
    // -----------------------------------------------------------------------

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

        // Z grows by at least one node per non-terminal pass and is bounded by
        // the pool; |pool| + 1 passes suffice (the +1 to confirm stabilization).
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

                RecursiveBlocking.Blockable r = findPathShallow(
                        graph, x, b, y, path, z, depth, notFollowed, descendantsMap,
                        pool, recursiveDepth, currentRecursiveDepth, deadlineMs);

                if (r == UNBLOCKABLE) {
                    return new RecursiveBlocking.BlockingResult(null, false);
                }

                if (r == INDETERMINATE) {
                    // Roll back this branch's additions; it may resolve once
                    // other branches have grown Z further.
                    z.clear();
                    z.addAll(zBefore);
                    anyIndeterminate = true;
                }
                // If BLOCKED, keep this branch's Z growth.
            }

            if (z.size() == zSizeBefore) {
                if (anyIndeterminate) {
                    // A branch stayed inconclusive and Z can't grow to help it.
                    return new RecursiveBlocking.BlockingResult(null, true);
                }
                // All branches BLOCKED and Z is stable.
                return new RecursiveBlocking.BlockingResult(z, false);
            }
            // Z grew; retry -- a previously INDETERMINATE branch may now block.
        }

        // Iteration cap reached without convergence.
        return new RecursiveBlocking.BlockingResult(null, true);
    }

    // -----------------------------------------------------------------------
    // Procedure 2: recursive step, SHALLOW commit.
    //
    // The only structural difference from RecursiveBlocking is the order of
    // preference: there, Branch A (block deeper, without b) wins if it can, so
    // conditioning clusters near y. Here, once we know the path through b is
    // open, we prefer Branch B (condition on b) so conditioning clusters near x.
    // -----------------------------------------------------------------------

    private static RecursiveBlocking.Blockable findPathShallow(
            Graph graph, Node a, Node b, Node y,
            Set<Node> path, Set<Node> z, int depth,
            Set<Node> notFollowed, Map<Node, Set<Node>> descendantsMap, Set<Node> pool,
            int recursiveDepth, int currentRecursiveDepth, long deadlineMs)
            throws InterruptedException, TimeoutException {

        checkTimeout(deadlineMs);

        // --- Terminal / guard checks (mirror RecursiveBlocking's ENTER pass) ---
        if (currentRecursiveDepth > recursiveDepth) return INDETERMINATE;
        if (b == y) return UNBLOCKABLE;            // reached the target
        if (path.contains(b)) return BLOCKED;      // cycle: no new path to y
        if (notFollowed.contains(b)) return INDETERMINATE;
        if (notFollowed.contains(y)) return BLOCKED;

        path.add(b);
        Set<Node> zSnap = new HashSet<>(z);
        int zSizeBefore = z.size();

        RecursiveBlocking.Blockable result;

        // --- Branch A: block continuations deeper, WITHOUT adding b. ---
        // Doubles as the openness probe: this call only ever grows Z, and it
        // grows Z iff an active path through b actually reaches y and had to be
        // blocked somewhere past b.
        RecursiveBlocking.Blockable rA = blockAllContinuations(
                graph, a, b, y, path, z, depth, notFollowed, descendantsMap,
                pool, recursiveDepth, currentRecursiveDepth, deadlineMs);

        if (rA == BLOCKED && z.size() == zSizeBefore) {
            // Branch A blocked without adding anything => the path through b was
            // already closed under the current Z. Nothing to condition on.
            // (Branch A is monotone over zSnap, so unchanged size == unchanged
            // set; a reachable-but-non-reaching continuation adds nothing and is
            // correctly treated as closed here.)
            result = BLOCKED;
        } else {
            // The path through b was open (Branch A grew Z) or Branch A failed.
            // Prefer to commit at b itself (shallow / near x).
            Set<Node> zAfterA = new HashSet<>(z);   // A's deep solution, if rA == BLOCKED
            z.clear();
            z.addAll(zSnap);                        // roll back A's additions

            boolean latent = b.getNodeType() == NodeType.LATENT;
            boolean canAddB = !latent && pool.contains(b) && (depth < 0 || z.size() < depth);

            if (canAddB) {
                // --- Branch B: add b to Z and block whatever it opens, deeper. ---
                z.add(b);
                RecursiveBlocking.Blockable rB = blockAllContinuations(
                        graph, a, b, y, path, z, depth, notFollowed, descendantsMap,
                        pool, recursiveDepth, currentRecursiveDepth, deadlineMs);

                if (rB == BLOCKED) {
                    // Committed at b (near x). Z keeps b plus whatever was needed
                    // to block the collider continuations that adding b opened
                    // (those sit deeper, toward y -- the inherent leak).
                    result = BLOCKED;
                } else {
                    z.clear();
                    z.addAll(zSnap);                // roll back Branch B
                    if (rA == BLOCKED) {
                        z.clear();
                        z.addAll(zAfterA);          // fall back to A's deep solution
                        result = BLOCKED;
                    } else {
                        // Neither branch blocked. Propagate the stronger failure.
                        result = (rA == INDETERMINATE || rB == INDETERMINATE)
                                ? INDETERMINATE : UNBLOCKABLE;
                    }
                }
            } else {
                // b cannot be conditioned on (latent / out of pool / depth cap);
                // only A's deeper solution is available.
                if (rA == BLOCKED) {
                    z.clear();
                    z.addAll(zAfterA);
                    result = BLOCKED;
                } else {
                    // Propagate A's genuine verdict.
                    //
                    // NOTE: this differs from RecursiveBlocking, which returns
                    // UNBLOCKABLE outright when b is out of pool. Propagating rA
                    // keeps an out-of-pool / depth-capped failure INDETERMINATE
                    // (a wider radius or larger depth might let b in and succeed),
                    // matching the class doc's stated intent for the pool. A
                    // genuine structural UNBLOCKABLE from deeper still surfaces as
                    // UNBLOCKABLE because rA carries it. Flip this to a bare
                    // UNBLOCKABLE if you want bit-for-bit parity with the deep
                    // version's verdict here.
                    result = rA;
                }
            }
        }

        path.remove(b);
        return result;
    }

    // -----------------------------------------------------------------------
    // Procedure 3: block every reachable continuation from b (coming from a)
    // under the current Z. Identical to the deep version -- the shallow/deep
    // choice lives entirely in findPathShallow's add-b ordering above.
    //
    // Loops because a recursive call on one continuation may grow Z and thereby
    // open a previously-closed collider at b. An UNBLOCKABLE child is recorded
    // but does not stop the pass; the accumulated flag is consulted only once no
    // new reachable continuation remains.
    // -----------------------------------------------------------------------

    private static RecursiveBlocking.Blockable blockAllContinuations(
            Graph graph, Node a, Node b, Node y,
            Set<Node> path, Set<Node> z, int depth,
            Set<Node> notFollowed, Map<Node, Set<Node>> descendantsMap, Set<Node> pool,
            int recursiveDepth, int currentRecursiveDepth, long deadlineMs)
            throws InterruptedException, TimeoutException {

        Set<Node> handled = new HashSet<>();
        boolean hadUnblockable = false;

        while (true) {
            checkTimeout(deadlineMs);

            List<Node> reachableCont =
                    getReachableNodes(graph, a, b, z, descendantsMap, deadlineMs);
            reachableCont.removeAll(notFollowed);
            reachableCont.removeAll(handled);

            if (reachableCont.isEmpty()) {
                return hadUnblockable ? UNBLOCKABLE : BLOCKED;
            }

            for (Node c : reachableCont) {
                checkTimeout(deadlineMs);

                RecursiveBlocking.Blockable r = findPathShallow(
                        graph, b, c, y, path, z, depth, notFollowed, descendantsMap,
                        pool, recursiveDepth, currentRecursiveDepth + 1, deadlineMs);

                if (r == INDETERMINATE) {
                    return INDETERMINATE;   // search limit -- propagate upward
                }
                if (r == UNBLOCKABLE) {
                    hadUnblockable = true;  // record, but keep processing
                }
                handled.add(c);
                // Z may have grown; reachability is recomputed on the next
                // while-iteration so newly opened colliders are picked up.
            }
        }
    }

    // -----------------------------------------------------------------------
    // Helpers (lifted verbatim from RecursiveBlocking; copied rather than shared
    // because they are private there. Make them package-private in
    // RecursiveBlocking and delete these if you'd rather not duplicate.)
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
}
