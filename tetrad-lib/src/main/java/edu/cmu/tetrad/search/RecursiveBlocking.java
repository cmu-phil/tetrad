package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;

import java.util.*;

/**
 * The {@code RecursiveBlocking} class provides methods for constructing a set Z that
 * blocks all blockable paths between x and y under PAG semantics. If any path is
 * determined to be UNBLOCKABLE (or the analysis is INDETERMINATE), no valid separating
 * set exists under the given constraints and the routine returns {@code null}.
 */
public class RecursiveBlocking {

    private RecursiveBlocking() {}

    /**
     * Retrieves a set that blocks all blockable paths between x and y in the given graph,
     * where this set contains the given nodes. Returns {@code null} if any path is
     * un-blockable (or indeterminate), i.e., no valid sepset exists under constraints.
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
     * Same as above, honoring (optional) knowledge constraints (currently passed through
     * to future extensions; not used in this routine).
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

    private static Set<Node> blockPathsRecursivelyVisit(Graph graph,
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

        // Z accumulates nodes that block all blockable paths.
        Set<Node> z = new HashSet<>(containing);

        // Maintain visited nodes in the current traversal (cycle guard).
        Set<Node> path = new HashSet<>();
        path.add(x);

        boolean allBlockable = true;

        for (Node b : graph.getAdjacentNodes(x)) {
            if (Thread.currentThread().isInterrupted()) {
                return null;
            }

            // NEW: ignore direct edge x—y if present; we only care about paths that
            // leave x via a node other than y on the first step.
            if (b == y) continue;

            Blockable r = findPathToTarget(graph, x, b, y, path, z, maxPathLength, notFollowed, descendantsMap);

            // If any traversal is UNBLOCKABLE or INDETERMINATE, we cannot certify a valid sepset.
            if (r != Blockable.BLOCKED) {
                allBlockable = false;
                break;
            }
        }

        return allBlockable ? z : null;
    }

    /**
     * Tries to determine whether all paths from a→b onward to y can be blocked by the current Z (possibly
     * augmented by adding b). Returns:
     *  - BLOCKED       if all such continuations are blocked under current Z (with or without conditioning on b)
     *  - UNBLOCKABLE   if some continuation cannot be blocked even after conditioning on b
     *  - INDETERMINATE if analysis aborted (interrupt / path-length cap) or cannot be decided safely
     *
     * NOTE: After calling path.add(b), this method ALWAYS calls path.remove(b) before returning.
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

    public enum Blockable {
        BLOCKED,
        UNBLOCKABLE,
        INDETERMINATE
    }
}