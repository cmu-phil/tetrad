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
    // Public entry points
    // -----------------------------------------------------------------------

    /**
     * Blocks paths between two specified nodes in a graph by iteratively
     * identifying and selecting nodes to include in a blocking set, subject to
     * constraints on path length and traversal rules. Assumes a direct edge
     * between x and y is to be ignored.
     *
     * @param graph         the graph in which the nodes and paths are analyzed
     * @param x             the starting node of the path
     * @param y             the target node of the path
     * @param containing    a set of nodes that must be included in the blocking set
     * @param notFollowed   a set of nodes that must not be traversed during path search
     * @param maxPathLength the maximum allowable length of the paths to block (-1 for no limit)
     * @return a set of nodes constituting a blocking set for paths between x and y,
     *         or {@code null} if no such set is found within the given constraints
     * @throws InterruptedException if the thread executing the method is interrupted
     */
    public static <E> Set<Node> blockPathsRecursively(Graph graph,
                                                      Node x,
                                                      Node y,
                                                      Set<Node> containing,
                                                      Set<Node> notFollowed,
                                                      int maxPathLength)
            throws InterruptedException {
        return blockPathsRecursively(graph, x, y, containing, notFollowed,
                maxPathLength, -1, -1, 1, true);
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
        Set<Node> pool = buildPool(graph, x, y, maxRadius, nearWhichEndpoint);
        // Nodes in 'containing' are always eligible, even if outside the radius.
        pool.addAll(containing);

        int recursionDepth = maxPathLength < 0 ? Integer.MAX_VALUE : maxPathLength;

        return blockPathsRecursivelyAdj(
                graph, x, y, containing, notFollowed,
                graph.paths().getDescendantsMap(),
                maxPathLength, recursionDepth, depth, pool, ignoreDirectEdge);
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
    // Core algorithm
    // -----------------------------------------------------------------------

    private static Set<Node> blockPathsRecursivelyAdj(
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

        // Outer fixed-point loop: re-examine every first hop after any Z growth,
        // because a node added to Z during one branch may activate a collider
        // on a different branch.
        while (true) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }

            int zSizeBefore = z.size();

            for (Node b : firstHops) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException();
                }

                Set<Node> path = new HashSet<>();
                path.add(x);

                Blockable r = findPathToTargetVisit(
                        graph, x, b, y, path, z,
                        maxPathLength, depth, notFollowed, descendantsMap, pool,
                        recursionDepth, 0);

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

    // -----------------------------------------------------------------------
    // Frame definition for the explicit stack
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
        /** Frame has just been pushed; guard checks have not run yet. */
        ENTER,
        /**
         * Running (or resuming) the continuation loop that does NOT add b to z.
         * For LATENT nodes this is the only pass.
         */
        CONTINUATIONS_WITHOUT_B,
        /**
         * Running (or resuming) the continuation loop that has added b to z.
         * Never reached for LATENT nodes.
         */
        CONTINUATIONS_WITH_B
    }

    /**
     * One stack frame — the explicit equivalent of a single activation record
     * for {@code findPathToTargetVisit}.
     *
     * <p>Fields that would normally live on the JVM call stack (local variables
     * and the "program counter" within the method) are stored here so that the
     * driver loop in {@link #findPathToTargetVisit} can suspend and resume a
     * frame after a child frame completes.</p>
     */
    private static final class Frame {

        // --- call parameters (immutable after construction) ----------------
        final Node a;             // predecessor node on the path
        final Node b;             // node being visited by this frame
        final Node y;             // target node
        final int maxPathLength;
        final int depth;
        final int recursionDepth;
        final int currentDepth;

        // --- resumption state ----------------------------------------------

        /** Which pass is currently active. */
        Pass pass = Pass.ENTER;

        /**
         * Snapshot of z taken before the WITHOUT_B pass begins.
         * Used to restore z before the WITH_B pass, and again on exit.
         */
        Set<Node> zSnapshot = null;

        /**
         * Result of the WITHOUT_B continuation pass.
         * Saved so it can be combined with the WITH_B result on exit.
         */
        Blockable withoutBResult = null;

        /**
         * Nodes whose sub-call inside {@code tryBlockAllContinuations} has
         * already completed successfully (returned BLOCKED) for the current
         * pass.  Mirrors the {@code handled} local variable in the original
         * {@code tryBlockAllContinuations}.
         *
         * <p>Reset to a fresh set when switching from WITHOUT_B to WITH_B.</p>
         */
        Set<Node> handled = new HashSet<>();

        /**
         * The continuation node ({@code c}) whose child frame was most recently
         * pushed and has not yet completed.  Set just before pushing the child;
         * read by the driver loop to add {@code c} to {@code handled} when the
         * child returns BLOCKED.  {@code null} when no child is in flight.
         */
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

    // -----------------------------------------------------------------------
    // Iterative driver  (replaces both findPathToTargetVisit and
    //                    tryBlockAllContinuations)
    // -----------------------------------------------------------------------

    /**
     * Iterative, stack-based replacement for the mutual recursion between
     * {@code findPathToTargetVisit} and {@code tryBlockAllContinuations}.
     *
     * <p>The semantics are identical to the original recursive
     * {@code findPathToTargetVisit}: given the edge {@code a → b}, explore all
     * onwards paths toward {@code y} and decide whether the current branch is
     * {@link Blockable#BLOCKED}, {@link Blockable#UNBLOCKABLE}, or
     * {@link Blockable#INDETERMINATE}.</p>
     *
     * <p><b>How the stack works</b></p>
     * <p>Each {@link Frame} on {@code callStack} represents one suspended
     * activation of {@code findPathToTargetVisit}.  The driver loop peeks at
     * the top frame, advances it by one "micro-step", and either:</p>
     * <ul>
     *   <li>pushes a new child frame (suspending the current one), or</li>
     *   <li>pops the current frame and writes its result into
     *       {@code lastResult}, so the parent frame can read it on its next
     *       step.</li>
     * </ul>
     *
     * <p><b>Shared mutable state ({@code path} and {@code z})</b></p>
     * <p>{@code path} and {@code z} are still shared across all frames, exactly
     * as they were in the recursive version.  Each frame adds {@code b} to
     * {@code path} on entry and removes it on exit (pop), and takes/restores a
     * snapshot of {@code z} around each continuation pass — again mirroring the
     * original {@code try/finally} and snapshot pattern precisely.</p>
     */
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

        // The explicit call stack.
        Deque<Frame> callStack = new ArrayDeque<>();
        callStack.push(new Frame(aInit, bInit, y,
                maxPathLength, depth, recursionDepth, currentDepthInit));

        // Result written by a frame just before it is popped.
        // The parent frame reads this value when it resumes.
        Blockable lastResult = null;

        while (!callStack.isEmpty()) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();

            Frame f = callStack.peek();

            // =================================================================
            // ENTER — first time we touch this frame; run guard checks and
            //         add b to path.
            // =================================================================
            if (f.pass == Pass.ENTER) {

                // --- depth / interrupt guards --------------------------------
                if (f.currentDepth > f.recursionDepth) {
                    callStack.pop();
                    lastResult = Blockable.INDETERMINATE;
                    continue;
                }

                // --- structural guards (do not touch path yet) ---------------
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

                // --- add b to path (mirrors original path.add(b)) -----------
                path.add(f.b);

                // --- path-length guard (path already contains b) -------------
                if (f.maxPathLength >= 0 && path.size() > f.maxPathLength) {
                    path.remove(f.b);
                    callStack.pop();
                    lastResult = Blockable.INDETERMINATE;
                    continue;
                }

                // --- snapshot z and start WITHOUT_B pass --------------------
                f.zSnapshot = new HashSet<>(z);
                f.pass = Pass.CONTINUATIONS_WITHOUT_B;
                // fall through immediately into the WITHOUT_B handler below
            }

            // =================================================================
            // CONTINUATIONS_WITHOUT_B — run (or resume) the continuation loop
            // without b in z.  For LATENT nodes this is the only pass.
            // =================================================================
            if (f.pass == Pass.CONTINUATIONS_WITHOUT_B) {

                // If we are resuming after a child frame completed, incorporate
                // its result before continuing the loop.
                //
                // In the original tryBlockAllContinuations:
                //   UNBLOCKABLE / INDETERMINATE  → return immediately (end loop)
                //   BLOCKED                      → handled.add(c), continue loop
                //
                // But the loop result feeds into findPathToTargetVisit, which
                // only short-circuits on BLOCKED; UNBLOCKABLE and INDETERMINATE
                // both fall through to the WITH_B pass.  We therefore must not
                // propagate UNBLOCKABLE/INDETERMINATE all the way up here —
                // instead, treat them as the terminal result of the WITHOUT_B
                // continuation loop and let the normal post-loop code handle them.
                Blockable contResult;
                if (lastResult != null) {
                    if (lastResult == Blockable.UNBLOCKABLE
                            || lastResult == Blockable.INDETERMINATE) {
                        // Child ended the continuation loop early.
                        // Use this as the loop's terminal result and fall through
                        // to the post-loop handling below (same as if stepContinuationLoop
                        // had returned this value directly).
                        contResult = lastResult;
                        lastResult = null;
                    } else {
                        // lastResult == BLOCKED: mark pending child as handled, re-scan.
                        f.handled.add(f.pendingC);
                        f.pendingC = null;
                        lastResult = null;

                        // Run the continuation loop from where we left off.
                        contResult = stepContinuationLoop(
                                graph, f, y, path, z, notFollowed, descendantsMap, pool,
                                callStack, /* isWithBPass= */ false);

                        if (contResult == null) {
                            // A child frame was pushed; we'll resume here when it pops.
                            continue;
                        }
                    }
                } else {
                    // First entry (not a resume): run the continuation loop.
                    contResult = stepContinuationLoop(
                            graph, f, y, path, z, notFollowed, descendantsMap, pool,
                            callStack, /* isWithBPass= */ false);

                    if (contResult == null) {
                        // A child frame was pushed; we'll resume here when it pops.
                        continue;
                    }
                }

                // The continuation loop finished for this pass.
                if (f.b.getNodeType() == NodeType.LATENT) {
                    // LATENT: no "with b" pass — return the single result.
                    path.remove(f.b);
                    callStack.pop();
                    lastResult = contResult;
                    continue;
                }

                if (contResult == Blockable.BLOCKED) {
                    // Blocked without conditioning on b — we're done, no need
                    // to try adding b.  Mirrors: if (withoutB == BLOCKED) return BLOCKED.
                    path.remove(f.b);
                    callStack.pop();
                    lastResult = Blockable.BLOCKED;
                    continue;
                }

                // withoutB is UNBLOCKABLE or INDETERMINATE.  We still try
                // adding b to z — the original only short-circuits on BLOCKED;
                // UNBLOCKABLE and INDETERMINATE both fall through to WITH_B.
                f.withoutBResult = contResult;
                z.clear();
                z.addAll(f.zSnapshot);

                if (!pool.contains(f.b)) {
                    path.remove(f.b);
                    callStack.pop();
                    lastResult = Blockable.INDETERMINATE;
                    continue;
                }
                if (f.depth >= 0 && z.size() > f.depth) {
                    path.remove(f.b);
                    callStack.pop();
                    lastResult = Blockable.INDETERMINATE;
                    continue;
                }

                // Transition to the WITH_B pass.
                z.add(f.b);
                f.handled = new HashSet<>(); // fresh handled set for this pass
                f.pendingC = null;
                f.pass = Pass.CONTINUATIONS_WITH_B;
                lastResult = null;
                // fall through immediately into the WITH_B handler
            }

            // =================================================================
            // CONTINUATIONS_WITH_B — run (or resume) the continuation loop
            // with b already added to z.
            // =================================================================
            if (f.pass == Pass.CONTINUATIONS_WITH_B) {

                // Incorporate child result if resuming.
                // Same logic as WITHOUT_B: UNBLOCKABLE/INDETERMINATE ends the
                // continuation loop (mirrors early return in tryBlockAllContinuations)
                // and becomes the withB terminal result; BLOCKED means handled.add + rescan.
                Blockable contResult;
                if (lastResult != null) {
                    if (lastResult == Blockable.UNBLOCKABLE
                            || lastResult == Blockable.INDETERMINATE) {
                        contResult = lastResult;
                        lastResult = null;
                    } else {
                        // BLOCKED — mark pending child as handled and continue loop.
                        f.handled.add(f.pendingC);
                        f.pendingC = null;
                        lastResult = null;

                        contResult = stepContinuationLoop(
                                graph, f, y, path, z, notFollowed, descendantsMap, pool,
                                callStack, /* isWithBPass= */ true);

                        if (contResult == null) {
                            continue; // child pushed, resume later
                        }
                    }
                } else {
                    contResult = stepContinuationLoop(
                            graph, f, y, path, z, notFollowed, descendantsMap, pool,
                            callStack, /* isWithBPass= */ true);

                    if (contResult == null) {
                        continue; // child pushed, resume later
                    }
                }

                // Combine results from both passes.
                // Mirrors the original:
                //   if (withB == BLOCKED) return BLOCKED;          // z keeps b
                //   z.clear(); z.addAll(zSnapshot);                // z restored only here
                //   return (withB==INDET || withoutB==INDET) ? INDET : UNBLOCKABLE;
                Blockable withB = contResult;
                if (withB == Blockable.BLOCKED) {
                    // z intentionally retains f.b (the node that achieved blocking).
                    path.remove(f.b);
                    callStack.pop();
                    lastResult = Blockable.BLOCKED;
                } else {
                    // Not blocked even with b — restore z and report.
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

    // -----------------------------------------------------------------------
    // Continuation-loop stepper
    // -----------------------------------------------------------------------

    /**
     * Performs one scan of the continuation loop for frame {@code f},
     * corresponding to one iteration of the {@code while(true)} loop in the
     * original {@code tryBlockAllContinuations}.
     *
     * <p>Rescans reachable nodes (because z may have grown since the last scan),
     * skips already-handled ones, and for the first unhandled node pushes a
     * child {@link Frame} onto {@code callStack}, records it in
     * {@link Frame#pendingC}, and returns {@code null} to suspend.  When no
     * unhandled node exists returns {@link Blockable#BLOCKED}.</p>
     *
     * <p>The driver loop calls this method again each time a child frame returns
     * {@link Blockable#BLOCKED} (after recording {@link Frame#pendingC} in
     * {@link Frame#handled}).  This preserves the re-scan-after-z-growth
     * semantics of the original {@code while(true)} loop without the risk of an
     * infinite loop.</p>
     *
     * @return {@code null} if a child frame was pushed (caller must re-enter),
     *         or {@link Blockable#BLOCKED} when the loop is fully exhausted.
     */
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

        // Rescan reachable nodes — z may have grown since the last call.
        List<Node> passNodes = getReachableNodes(graph, f.a, f.b, z, descendantsMap);
        passNodes.removeAll(notFollowed);

        for (Node c : passNodes) {
            if (f.handled.contains(c)) continue;

            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();

            // Record which node is in flight so the driver can add it to
            // handled when the child returns BLOCKED (mirrors handled.add(c)
            // after the recursive call in the original).
            f.pendingC = c;
            callStack.push(new Frame(
                    f.b, c, y,
                    f.maxPathLength, f.depth, f.recursionDepth,
                    f.currentDepth + 1));
            return null; // suspend — driver resumes when child pops
        }

        // No unhandled pass-node found — this branch is fully blocked.
        return Blockable.BLOCKED;
    }

    // -----------------------------------------------------------------------
    // Reachability helpers (unchanged)
    // -----------------------------------------------------------------------

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
    // Result enum (unchanged)
    // -----------------------------------------------------------------------

    /**
     * Three-valued result of path-blocking analysis.
     */
    public enum Blockable {
        /** All paths through this branch are blocked by Z. */
        BLOCKED,
        /** Some path is unblockable regardless of Z (e.g. direct x–y edge or latent bow). */
        UNBLOCKABLE,
        /** Analysis was inconclusive (interrupted, path-length cap, or radius limit hit). */
        INDETERMINATE
    }
}
