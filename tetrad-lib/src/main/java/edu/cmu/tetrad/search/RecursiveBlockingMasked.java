package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;

import java.util.*;

/**
 * RecursiveBlockingMasked
 *
 * A drop-in companion to RecursiveBlocking that supports an explicit
 * "latent mask": nodes in {@code latentMask} are treated as LATENT for
 * the duration of the run (i.e., they are never added to Z).
 *
 * This class does NOT modify or overload RecursiveBlocking. It has
 * distinct entry points to avoid any null-overload ambiguities and
 * keeps return/branch semantics identical to the baseline algorithm.
 */
public final class RecursiveBlockingMasked {

    private RecursiveBlockingMasked() {}

    // =========================================================================
    // Public API (distinct names; no overload ambiguity with null arguments)
    // =========================================================================

    /**
     * Build a candidate blocking set Z between x and y under PAG semantics,
     * treating all nodes in {@code latentMask} as if they were LATENT for this run.
     *
     * @param graph         PAG
     * @param x             source node
     * @param y             target node
     * @param containing    seed nodes that must be in Z
     * @param notFollowed   nodes that should not be traversed into
     * @param maxPathLength max allowed path length (-1 for unlimited)
     * @param latentMask    nodes never to be added to Z (treated as LATENT)
     * @return Z if all blockable paths are blocked, else null
     */
    public static Set<Node> blockPathsRecursivelyWithLatentMask(Graph graph,
                                                                Node x,
                                                                Node y,
                                                                Set<Node> containing,
                                                                Set<Node> notFollowed,
                                                                int maxPathLength,
                                                                Set<Node> latentMask) throws InterruptedException {
        return blockPathsRecursivelyVisitMasked(
                graph, x, y, containing, notFollowed,
                graph.paths().getDescendantsMap(), maxPathLength, null,
                latentMask == null ? Collections.emptySet() : latentMask
        );
    }

    /**
     * Knowledge variant of {@link #blockPathsRecursivelyWithLatentMask}.
     */
    public static Set<Node> blockPathsRecursivelyWithLatentMask(Graph graph,
                                                                Node x,
                                                                Node y,
                                                                Set<Node> containing,
                                                                Set<Node> notFollowed,
                                                                int maxPathLength,
                                                                Knowledge knowledge,
                                                                Set<Node> latentMask) throws InterruptedException {
        return blockPathsRecursivelyVisitMasked(
                graph, x, y, containing, notFollowed,
                graph.paths().getDescendantsMap(), maxPathLength, knowledge,
                latentMask == null ? Collections.emptySet() : latentMask
        );
    }

    // =========================================================================
    // Private visitor (masked)
    // =========================================================================

    private static Set<Node> blockPathsRecursivelyVisitMasked(
            Graph graph,
            Node x,
            Node y,
            Set<Node> containing,
            Set<Node> notFollowed,
            Map<Node, Set<Node>> descendantsMap,
            int maxPathLength,
            Knowledge knowledge,
            Set<Node> latentMask) throws InterruptedException {

        if (x == null || y == null) throw new NullPointerException("x or y is null");
        if (x == y) throw new IllegalArgumentException("x and y must be distinct");

        // Accumulate candidate Z (copy seed)
        Set<Node> z = new HashSet<>(containing == null ? Collections.emptySet() : containing);

        // Cycle guard for the top-level exploration from x
        Set<Node> path = new HashSet<>();
        path.add(x);

        for (Node b : graph.getAdjacentNodes(x)) {
            if (Thread.currentThread().isInterrupted()) return null;
            if (b == y) continue; // ignore direct edge on first hop

            Blockable r = findPathToTargetMasked(
                    graph, x, b, y, path, z, maxPathLength,
                    notFollowed == null ? Collections.emptySet() : notFollowed,
                    descendantsMap, latentMask
            );

            // Strict semantics: any UNBLOCKABLE or INDETERMINATE -> failure
            if (r == Blockable.UNBLOCKABLE || r == Blockable.INDETERMINATE) {
                return null;
            }
        }

        return z;
    }

    // =========================================================================
    // Masked finder (identical control-flow; mask only changes Case 1 condition)
    // =========================================================================

    private static Blockable findPathToTargetMasked(Graph graph,
                                                    Node a,
                                                    Node b,
                                                    Node y,
                                                    Set<Node> path,
                                                    Set<Node> z,
                                                    int maxPathLength,
                                                    Set<Node> notFollowed,
                                                    Map<Node, Set<Node>> descendantsMap,
                                                    Set<Node> latentMask) throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) return Blockable.INDETERMINATE;

        // Immediate termination cases before adding b to path.
        if (b == y) return Blockable.UNBLOCKABLE;
        if (path.contains(b)) return Blockable.UNBLOCKABLE;
        if (notFollowed.contains(b)) return Blockable.INDETERMINATE;
        // If y is "not followed", treat as BLOCKED for this branch (can't make paths less blockable).
        if (notFollowed.contains(y)) return Blockable.BLOCKED;

        path.add(b);
        try {
            if (maxPathLength != -1 && path.size() > maxPathLength) {
                return Blockable.INDETERMINATE;
            }

            // Treat masked nodes as latent => never add to Z (Case 1 path)
            boolean maskedLatent = latentMask != null && latentMask.contains(b);

            // Case 1: if b is latent, masked-latent, or already in Z
            if (b.getNodeType() == NodeType.LATENT || maskedLatent || z.contains(b)) {
                List<Node> passNodes = getReachableNodes(graph, a, b, z, descendantsMap);
                passNodes.removeAll(notFollowed);

                for (Node c : passNodes) {
                    if (Thread.currentThread().isInterrupted()) return Blockable.INDETERMINATE;

                    Blockable blockable = findPathToTargetMasked(
                            graph, b, c, y, path, z, maxPathLength, notFollowed, descendantsMap, latentMask
                    );

                    if (blockable == Blockable.UNBLOCKABLE || blockable == Blockable.INDETERMINATE) {
                        return Blockable.UNBLOCKABLE;
                    }
                }

                // All continuations through b are blocked without adding b.
                return Blockable.BLOCKED;
            }

            // Case 2: Try WITHOUT conditioning on b.
            {
                boolean blockable1 = true;

                List<Node> passNodes = getReachableNodes(graph, a, b, z, descendantsMap);
                passNodes.removeAll(notFollowed);

                for (Node c : passNodes) {
                    if (Thread.currentThread().isInterrupted()) return Blockable.INDETERMINATE;

                    Blockable blockType = findPathToTargetMasked(
                            graph, b, c, y, path, z, maxPathLength, notFollowed, descendantsMap, latentMask
                    );

                    if (blockType == Blockable.UNBLOCKABLE || blockType == Blockable.INDETERMINATE) {
                        blockable1 = false;
                        break;
                    }
                }

                if (blockable1) {
                    return Blockable.BLOCKED;
                }
            }

            // Case 3: Try WITH conditioning on b (only if not latent/masked; we are here).
            z.add(b);
            try {
                boolean blockable2 = true;

                List<Node> passNodes = getReachableNodes(graph, a, b, z, descendantsMap);
                passNodes.removeAll(notFollowed);

                for (Node c : passNodes) {
                    if (Thread.currentThread().isInterrupted()) return Blockable.INDETERMINATE;

                    Blockable blockable = findPathToTargetMasked(
                            graph, b, c, y, path, z, maxPathLength, notFollowed, descendantsMap, latentMask
                    );

                    if (blockable == Blockable.UNBLOCKABLE || blockable == Blockable.INDETERMINATE) {
                        blockable2 = false;
                        break;
                    }
                }

                if (blockable2) {
                    return Blockable.BLOCKED;
                } else {
                    return Blockable.UNBLOCKABLE;
                }
            } finally {
                z.remove(b); // rollback
            }

        } finally {
            path.remove(b); // ALWAYS clean up
        }
    }

    // =========================================================================
    // Neighbor filters (shared with baseline semantics)
    // =========================================================================

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
            for (Node d : desc) {
                if (z.contains(d)) {
                    return true;
                }
            }
            return false;
        }
    }

    // =========================================================================
    // Result enum (duplicated here to keep class self-contained)
    // =========================================================================
    public enum Blockable {
        BLOCKED,
        UNBLOCKABLE,
        INDETERMINATE
    }
}