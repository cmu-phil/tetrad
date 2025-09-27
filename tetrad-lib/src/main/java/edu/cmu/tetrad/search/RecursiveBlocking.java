package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;

import java.util.*;

/**
 * The {@code RecursiveBlocking} class implements a recursive procedure for
 * constructing candidate separating sets between two nodes under PAG semantics.
 *
 * <p>Given distinct nodes x and y, the algorithm attempts to build a set Z that
 * blocks all blockable paths between x and y, starting from an optional seed set
 * of nodes to include. If such a set is found, it is returned and may later be
 * tested against the distribution for conditional independence. If any path is
 * provably un-blockable (or the analysis is interrupted or inconclusive), the
 * routine returns {@code null}, indicating that no valid graphical separating
 * set exists.</p>
 *
 * <p>Key features:</p>
 * <ul>
 *   <li>Respects PAG semantics for colliders, non-colliders, and latent nodes.</li>
 *   <li>Supports path length limits and "do not follow" constraints.</li>
 *   <li>Can be run with or without background knowledge (currently unused, but
 *       supported for extension).</li>
 *   <li>Returns a candidate blocking set agnostic to adjacency: the presence
 *       of a direct edge x–y does not preempt construction, but such an edge
 *       may prevent a valid separator from existing.</li>
 * </ul>
 *
 * <p>In the context of FCIT and related algorithms, the returned set is always
 * subject to a statistical independence test to confirm whether it functions
 * as an actual separating set in the distribution.</p>
 */
public class RecursiveBlocking {

    private RecursiveBlocking() {
    }

    /**
     * Attempts to construct a candidate blocking set Z between nodes x and y under PAG semantics. The returned set Z
     * contains the nodes in {@code containing} and is augmented as needed to block all blockable paths from x to y,
     * ignoring any direct edge x–y on the first step.
     *
     * <p>Semantics:</p>
     * <ul>
     *   <li>If x and y are adjacent, this method does not produce a separating
     *       set (returns {@code null}).</li>
     *   <li>If x and y are not adjacent, the routine returns a candidate
     *       blocking set Z if all blockable paths can be blocked. This set is
     *       only a <b>graphical</b> sepset and must be validated with an
     *       independence test.</li>
     *   <li>If some path is un-blockable or the recursion is indeterminate
     *       (e.g., interrupted or exceeds {@code maxPathLength}), the method
     *       returns {@code null}.</li>
     * </ul>
     *
     * @param graph         the PAG structure
     * @param x             first endpoint
     * @param y             second endpoint
     * @param containing    nodes that must be included in the blocking set
     * @param notFollowed   nodes that should not be traversed into
     * @param maxPathLength maximum allowed path length (-1 for unlimited)
     * @return a candidate blocking set Z if all blockable paths can be blocked, or {@code null} if no valid graphical
     * separating set exists
     * @throws InterruptedException if the search is interrupted
     */
    public static Set<Node> blockPathsRecursively(Graph graph,
                                                  Node x,
                                                  Node y,
                                                  Set<Node> containing,
                                                  Set<Node> notFollowed,
                                                  int maxPathLength) throws InterruptedException {
        return blockPathsRecursivelyVisit(
                graph, x, y, containing, notFollowed,
                graph.paths().getDescendantsMap(), maxPathLength, null
        );
    }

    /**
     * Variant of {@link #blockPathsRecursively(Graph, Node, Node, Set, Set, int)} that additionally accepts an optional
     * {@code Knowledge} object.
     *
     * <p>Currently, the {@code knowledge} argument is reserved for future
     * extensions and is not applied in this routine, but it is passed along to maintain compatibility with
     * knowledge-aware search strategies.</p>
     *
     * @param graph         the PAG structure
     * @param x             first endpoint
     * @param y             second endpoint
     * @param containing    nodes that must be included in the blocking set
     * @param notFollowed   nodes that should not be traversed into
     * @param maxPathLength maximum allowed path length (-1 for unlimited)
     * @param knowledge     optional background knowledge (currently unused here)
     * @return a candidate blocking set Z if all blockable paths can be blocked, or {@code null} if no valid graphical
     * separating set exists
     * @throws InterruptedException if the search is interrupted
     */
    public static Set<Node> blockPathsRecursively(Graph graph,
                                                  Node x,
                                                  Node y,
                                                  Set<Node> containing,
                                                  Set<Node> notFollowed,
                                                  int maxPathLength,
                                                  Knowledge knowledge) throws InterruptedException {
        return blockPathsRecursivelyVisit(
                graph, x, y, containing, notFollowed,
                graph.paths().getDescendantsMap(), maxPathLength, knowledge
        );
    }

    /**
     * Internal recursive routine for constructing a candidate blocking set Z between nodes x and y under PAG
     * semantics.
     *
     * <p>Semantics:
     * <ul>
     *   <li>If x and y are <b>adjacent</b> in the graph, this routine does not
     *       attempt to certify a separating set (returns {@code null}).</li>
     *   <li>If x and y are <b>not adjacent</b>, the routine attempts to build
     *       a set Z (containing the provided {@code containing} nodes) such that
     *       all <i>blockable</i> paths from x to y are blocked by Z. Direct edge
     *       x–y is ignored on the first hop so that adjacency can later be tested
     *       empirically using independence tests.</li>
     *   <li>If every path is successfully blocked, the accumulated Z is returned
     *       as a <b>graphical separating set candidate</b>. This set must still be
     *       validated against the distribution with an independence test.</li>
     *   <li>If some path is <b>unblockable</b> (or the recursion aborts due to
     *       interrupt or path-length cap), the routine returns {@code null}.</li>
     * </ul>
     *
     * @param graph          the PAG structure
     * @param x              first endpoint
     * @param y              second endpoint
     * @param containing     nodes that must be included in the blocking set
     * @param notFollowed    nodes that should not be traversed into
     * @param descendantsMap precomputed map of node → descendants (for collider tests)
     * @param maxPathLength  maximum allowed path length (-1 for unlimited)
     * @param knowledge      optional background knowledge (currently unused here)
     * @return a candidate blocking set Z if all blockable paths can be blocked, or {@code null} if no valid graphical
     * separating set exists
     * @throws InterruptedException if the search is interrupted
     */
    private static Set<Node> blockPathsRecursivelyVisit(
            Graph graph,
            Node x,
            Node y,
            Set<Node> containing,
            Set<Node> notFollowed,
            Map<Node, Set<Node>> descendantsMap,
            int maxPathLength,
            Knowledge knowledge) throws InterruptedException {

        if (x == y) {
            throw new NullPointerException("x and y are equal");
        }

        // Z accumulates nodes that block all *blockable* paths.
        Set<Node> z = new HashSet<>(containing);

        // Maintain visited nodes in the current traversal (cycle guard).
        Set<Node> path = new HashSet<>();
        path.add(x);

        for (Node b : graph.getAdjacentNodes(x)) {
            if (Thread.currentThread().isInterrupted()) {
                return null; // indeterminate
            }

            // Ignore the direct edge x—y on the first hop; we only explore paths that
            // leave x via a node other than y.
            if (b == y) continue;

            Blockable r = findPathToTarget(
                    graph, x, b, y, path, z, maxPathLength, notFollowed, descendantsMap
            );

            // STRICT: If any branch is UNBLOCKABLE, then no graphical sepset exists.
            if (r == Blockable.UNBLOCKABLE) {
                return null;
            }
            // If analysis is indeterminate anywhere, we cannot certify a sepset.
            if (r == Blockable.INDETERMINATE) {
                return null;
            }
            // Otherwise r == BLOCKED: continue checking other branches.
        }

        // All explored branches are BLOCKED under Z → candidate sepset found.
        return z;
    }

    /**
     * Evaluates whether all paths from a→b onward to y can be blocked by the current candidate set Z, possibly
     * augmented with b.
     *
     * <p>The method explores continuations from the triple (a, b, c) under PAG
     * semantics and returns one of three outcomes:</p>
     * <ul>
     *   <li>{@code BLOCKED} — all continuations through b are blocked given the
     *       current Z (with or without conditioning on b).</li>
     *   <li>{@code UNBLOCKABLE} — some continuation cannot be blocked even after
     *       adding b to Z.</li>
     *   <li>{@code INDETERMINATE} — traversal was aborted (interrupted or path
     *       length exceeded) or could not be decided safely.</li>
     * </ul>
     *
     * <p>Special cases:</p>
     * <ul>
     *   <li>If b == y, the path immediately certifies as {@code UNBLOCKABLE}.</li>
     *   <li>If b has already been visited in the current path, it is treated as
     *       {@code UNBLOCKABLE} (cycle guard).</li>
     *   <li>If b is in {@code notFollowed}, the branch is aborted as
     *       {@code INDETERMINATE}.</li>
     *   <li>If y is in {@code notFollowed}, that branch is treated as
     *       {@code BLOCKED} (refusing to follow into y cannot make paths less
     *       blockable).</li>
     * </ul>
     *
     * <p>Traversal policy:</p>
     * <ol>
     *   <li>If b is latent or already in Z, traversal continues without
     *       conditioning on b.</li>
     *   <li>Otherwise, the method first tries to block without conditioning on
     *       b. If that fails, it retries with b added to Z, rolling back if
     *       this does not succeed.</li>
     * </ol>
     *
     * <p>Path bookkeeping: after adding b to the current path, this method
     * guarantees that b is removed again before returning.</p>
     *
     * @param graph          the PAG structure
     * @param a              predecessor node in the path
     * @param b              current node under consideration
     * @param y              target node to be separated from x
     * @param path           nodes visited so far (cycle guard)
     * @param z              current candidate blocking set
     * @param maxPathLength  maximum allowed path length (-1 for unlimited)
     * @param notFollowed    nodes not to be traversed into
     * @param descendantsMap precomputed node→descendants map (for collider checks)
     * @return one of {@code BLOCKED}, {@code UNBLOCKABLE}, or {@code INDETERMINATE}
     * @throws InterruptedException if the traversal is interrupted
     */
    public static Blockable findPathToTarget(Graph graph,
                                             Node a,
                                             Node b,
                                             Node y,
                                             Set<Node> path,
                                             Set<Node> z,
                                             int maxPathLength,
                                             Set<Node> notFollowed,
                                             Map<Node, Set<Node>> descendantsMap) throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            return Blockable.INDETERMINATE;
        }

        // Immediate termination cases before adding b to path.
        if (b == y) {
            return Blockable.UNBLOCKABLE;
        }
        if (path.contains(b)) {
            return Blockable.UNBLOCKABLE;
        }
        if (notFollowed.contains(b)) {
            return Blockable.INDETERMINATE;
        }
        // IMPORTANT: If y is "not followed", treat as BLOCKED for this branch.
        if (notFollowed.contains(y)) {
            return Blockable.BLOCKED;
        }

        path.add(b);

        try {
            if (maxPathLength != -1 && path.size() > maxPathLength) {
                return Blockable.INDETERMINATE;
            }

            // Case 1: if b is latent or already in Z, we cannot (or need not) condition on it.
            if (b.getNodeType() == NodeType.LATENT || z.contains(b)) {
                List<Node> passNodes = getReachableNodes(graph, a, b, z, descendantsMap);
                passNodes.removeAll(notFollowed);

                for (Node c : passNodes) {
                    if (Thread.currentThread().isInterrupted()) {
                        return Blockable.INDETERMINATE;
                    }

                    Blockable blockable = findPathToTarget(graph, b, c, y, path, z, maxPathLength, notFollowed, descendantsMap);

                    if (blockable == Blockable.UNBLOCKABLE || blockable == Blockable.INDETERMINATE) {
                        return Blockable.UNBLOCKABLE;
                    }
                }

                // All continuations are blocked without needing to add b.
                return Blockable.BLOCKED;
            }

            // Case 2: Try first WITHOUT conditioning on b.
            {
                boolean blockable1 = true;

                List<Node> passNodes = getReachableNodes(graph, a, b, z, descendantsMap);
                passNodes.removeAll(notFollowed);

                for (Node c : passNodes) {
                    if (Thread.currentThread().isInterrupted()) {
                        return Blockable.INDETERMINATE;
                    }

                    Blockable blockType = findPathToTarget(graph, b, c, y, path, z, maxPathLength, notFollowed, descendantsMap);

                    if (blockType == Blockable.UNBLOCKABLE || blockType == Blockable.INDETERMINATE) {
                        blockable1 = false;
                        break;
                    }
                }

                if (blockable1) {
                    // Already blocked without adding b.
                    return Blockable.BLOCKED;
                }
            }

            // Case 3: Try WITH conditioning on b.
            z.add(b);
            {
                boolean blockable2 = true;

                List<Node> passNodes = getReachableNodes(graph, a, b, z, descendantsMap);
                passNodes.removeAll(notFollowed);

                for (Node c : passNodes) {
                    if (Thread.currentThread().isInterrupted()) {
                        // Roll back Z before returning.
                        z.remove(b);
                        return Blockable.INDETERMINATE;
                    }

                    Blockable blockable = findPathToTarget(graph, b, c, y, path, z, maxPathLength, notFollowed, descendantsMap);

                    if (blockable == Blockable.UNBLOCKABLE || blockable == Blockable.INDETERMINATE) {
                        blockable2 = false;
                        break;
                    }
                }

                if (blockable2) {
                    return Blockable.BLOCKED;
                } else {
                    // Roll back Z: adding b did not help, leave Z unchanged.
                    z.remove(b);
                    return Blockable.UNBLOCKABLE;
                }
            }
        } finally {
            // ALWAYS clean up the path.
            path.remove(b);
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

        // Non-collider (or underlined collider) is traversable if we are NOT conditioning on b.
        if ((!collider || graph.isUnderlineTriple(a, b, c)) && !z.contains(b)) {
            return true;
        }

        // Collider is traversable iff collider or a DESCENDANT of it is in Z.
        if (descendantsMap == null) {
            return collider && graph.paths().isAncestorOfAnyZ(b, z);
        } else {
            Set<Node> desc = descendantsMap.getOrDefault(b, Collections.emptySet());
            boolean hasZDesc = false;
            for (Node d : desc) {
                if (z.contains(d)) {
                    hasZDesc = true;
                    break;
                }
            }
            return collider && hasZDesc;
        }
    }

    /**
     * The Blockable enum represents the state of an entity in relation to its
     * ability to be blocked. It defines three possible states:
     */
    public enum Blockable {
        /**
         * Indicates that the entity is currently blocked.
         */
        BLOCKED,
        /**
         * Indicates that the entity cannot be blocked.
         */
        UNBLOCKABLE,
        /**
         * Indicates that the blockable state of the entity is unclear or undefined.
         */
        INDETERMINATE
    }
}