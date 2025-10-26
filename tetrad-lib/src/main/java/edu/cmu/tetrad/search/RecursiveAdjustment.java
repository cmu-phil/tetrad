package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;

import java.util.*;

/**
 * The {@code RecursiveAdjustment} algorithm searches recursively for a
 * (not necessarily minimal) adjustment set between variables X and Y
 * in a causal graph under PAG semantics.
 *
 * <p>This algorithm extends recursive blocking to the adjustment-set problem:
 * it forbids nodes on amenable (causal) paths and recursively adds nodes
 * that block all remaining backdoor paths, returning a graphical candidate
 * adjustment set suitable for causal effect estimation.</p>
 */
public final class RecursiveAdjustment {

    private static final boolean DEBUG = false; // set false to silence logging

    public enum GraphType{PDAG, MAG, PAG}

    private RecursiveAdjustment() {}

    private static void log(String msg) {
        if (DEBUG) System.out.println("[RecursiveAdjustment] " + msg);
    }

    private static void logBranch(Node x, Node b, int count) {
        if (DEBUG) {
            System.out.printf("[RecursiveAdjustment] Branch %s → %s produced %d candidate sets%n",
                    x.getName(), b.getName(), count);
        }
    }

    public static Set<Node> findAdjustmentSet(
            Graph graph, String graphType, Node x, Node y,
            Set<Node> seedZ, Set<Node> notFollowed,
            int maxPathLength, Set<Node> latentMask) throws InterruptedException {

        if (x == y) {
            return Collections.emptySet();
        }

        Set<Node> z0 = visit(
                graph, graphType, x, y,
                seedZ == null ? Collections.emptySet() : seedZ,
                notFollowed == null ? Collections.emptySet() : notFollowed,
                graph.paths().getDescendantsMap(),
                maxPathLength, null,
                latentMask == null ? Collections.emptySet() : latentMask
        );

        if (z0 == null) return null;                // no valid adjustment set
        return minimizeZ(graph, graphType, x, y, z0, notFollowed, maxPathLength);  // checker-based
    }

    private static Set<Node> visit(
            Graph graph, String graphType, Node x, Node y,
            Set<Node> containing, Set<Node> notFollowed,
            Map<Node, Set<Node>> descendantsMap,
            int maxPathLength, Knowledge knowledge, Set<Node> latentMask) throws InterruptedException {

        if (x == null || y == null)
            throw new NullPointerException("x or y is null");
        if (x == y)
            throw new IllegalArgumentException("x and y must differ");

        Set<Node> Z = new HashSet<>(containing);
        Set<Node> path = new HashSet<>();
        path.add(x);

        // Explore only backdoor-starting edges out of X
        for (Node b : graph.getAdjacentNodes(x)) {
            log("Exploring backdoor branch " + x.getName() + " → " + b.getName());

            if (Thread.currentThread().isInterrupted()) return null;
            if (b == y) continue;

            Edge e = graph.getEdge(x, b);
            if (e == null) continue;
            if (!startsBackdoorFromX(graph, graphType, e, x, b, y)) continue;

            Blockable r = descend(graph, x, b, y, path, Z, maxPathLength, notFollowed, descendantsMap, latentMask);
            if (r == Blockable.UNBLOCKABLE || r == Blockable.INDETERMINATE) return null;
        }
        return Z;
    }

    private static boolean startsBackdoorFromX(Graph graph, String graphType, Edge e, Node x, Node b, Node y) {
        GraphType _graphType = GraphType.valueOf(graphType);

        if (_graphType == GraphType.PDAG) {
            return e.pointsTowards(x) || Edges.isUndirectedEdge(e);
        } else if (_graphType == GraphType.MAG) {
            return e.pointsTowards(x) || Edges.isUndirectedEdge(e) || Edges.isBidirectedEdge(e);
        } else if (_graphType == GraphType.PAG) {
            if (e.pointsTowards(x) || Edges.isUndirectedEdge(e)) return true;
            if (Edges.isBidirectedEdge(e)) {
                return graph.paths().existsDirectedPath(b, x) || graph.paths().existsDirectedPath(b, y);
            }
            return false;
        } else { // DAG default
            return e.pointsTowards(x);
        }
    }

    private static Blockable descend(
            Graph graph, Node a, Node b, Node y,
            Set<Node> path, Set<Node> Z, int maxPathLength,
            Set<Node> notFollowed, Map<Node, Set<Node>> descendantsMap, Set<Node> latentMask) throws InterruptedException {

        if (Thread.currentThread().isInterrupted()) return Blockable.INDETERMINATE;

        if (b == y) return Blockable.UNBLOCKABLE;
        if (path.contains(b)) return Blockable.UNBLOCKABLE;
        if (notFollowed.contains(b)) return Blockable.INDETERMINATE;
        if (notFollowed.contains(y)) return Blockable.BLOCKED;

        path.add(b);
        try {
            if (maxPathLength != -1 && path.size() > maxPathLength)
                return Blockable.INDETERMINATE;

            boolean maskedLatent = latentMask.contains(b);
            if (b == null || b == y) return Blockable.UNBLOCKABLE;

            // Case 1: cannot/shouldn’t condition on b
            if (b.getNodeType() == NodeType.LATENT || maskedLatent || Z.contains(b)) {
                for (Node c : children(graph, a, b, Z, descendantsMap, notFollowed)) {
                    Blockable r = descend(graph, b, c, y, path, Z, maxPathLength, notFollowed, descendantsMap, latentMask);
                    if (r == Blockable.UNBLOCKABLE || r == Blockable.INDETERMINATE)
                        return Blockable.UNBLOCKABLE;
                }
                return Blockable.BLOCKED;
            }

            // Case 2: try without b in Z
            boolean allBlocked = true;
            for (Node c : children(graph, a, b, Z, descendantsMap, notFollowed)) {
                Blockable r = descend(graph, b, c, y, path, Z, maxPathLength, notFollowed, descendantsMap, latentMask);
                if (r == Blockable.UNBLOCKABLE || r == Blockable.INDETERMINATE) {
                    allBlocked = false;
                    break;
                }
            }
            if (allBlocked) return Blockable.BLOCKED;

            // Case 3: try with b in Z (persist if works)
            Z.add(b);
            boolean allBlockedWithB = true;
            for (Node c : children(graph, a, b, Z, descendantsMap, notFollowed)) {
                Blockable r = descend(graph, b, c, y, path, Z, maxPathLength, notFollowed, descendantsMap, latentMask);
                if (r == Blockable.UNBLOCKABLE || r == Blockable.INDETERMINATE) {
                    allBlockedWithB = false;
                    break;
                }
            }
            if (allBlockedWithB) {
                return Blockable.BLOCKED; // keep b in Z
            } else {
                Z.remove(b);
                return Blockable.UNBLOCKABLE;
            }

        } finally {
            path.remove(b);
        }
    }

    private static List<Node> children(Graph graph, Node a, Node b, Set<Node> Z,
                                       Map<Node, Set<Node>> descendantsMap, Set<Node> notFollowed) {
        List<Node> pass = new ArrayList<>();
        for (Node c : graph.getAdjacentNodes(b)) {
            if (c == a) continue;
            if (notFollowed.contains(c)) continue;
            if (reachable(graph, a, b, c, Z, descendantsMap)) pass.add(c);
        }
        return pass;
    }

    private static boolean reachable(Graph graph, Node a, Node b, Node c,
                                     Set<Node> Z, Map<Node, Set<Node>> descendantsMap) {
        boolean collider = graph.isDefCollider(a, b, c);

        if ((!collider || graph.isUnderlineTriple(a, b, c)) && !Z.contains(b)) {
            return true;
        }

        if (!collider) return false;

        if (descendantsMap == null) {
            return graph.paths().isAncestorOfAnyZ(b, Z);
        } else {
            for (Node d : descendantsMap.getOrDefault(b, Collections.emptySet())) {
                if (Z.contains(d)) return true;
            }
            return false;
        }
    }

    private static Set<Node> minimizeZ(
            Graph graph, String graphType, Node x, Node y, Set<Node> Z,
            Set<Node> notFollowed, int maxPathLength) throws InterruptedException {

        List<Node> order = new ArrayList<>(Z);
        Set<Node> best = new HashSet<>(Z);
        for (Node n : order) {
            if (Thread.currentThread().isInterrupted()) return best;
            Set<Node> trial = new HashSet<>(best);
            trial.remove(n);
            if (isAdjustmentSet(graph, graphType, x, y, trial, notFollowed, maxPathLength)) {
                best = trial;
            }
        }
        return best;
    }

    public static boolean isAdjustmentSet(
            Graph graph, String graphType, Node x, Node y, Set<Node> Z,
            Set<Node> notFollowed, int maxPathLength) throws InterruptedException {

        Set<Node> path = new HashSet<>();
        path.add(x);

        for (Node b : graph.getAdjacentNodes(x)) {
            if (Thread.currentThread().isInterrupted()) return false;
            if (b == y) continue;

            Edge e = graph.getEdge(x, b);
            if (e == null) continue;
            if (!startsBackdoorFromX(graph, graphType,  e, x, b, y)) continue;

            Blockable r = descendCheck(graph, x, b, y, path, Z, maxPathLength, notFollowed, graph.paths().getDescendantsMap());
            if (r == Blockable.UNBLOCKABLE || r == Blockable.INDETERMINATE) return false;
        }
        return true;
    }

    private static Blockable descendCheck(
            Graph graph, Node a, Node b, Node y,
            Set<Node> path, Set<Node> Z, int maxPathLength,
            Set<Node> notFollowed, Map<Node, Set<Node>> descendantsMap) throws InterruptedException {

        if (b == y) return Blockable.UNBLOCKABLE;
        if (path.contains(b)) return Blockable.BLOCKED;
        if (notFollowed.contains(b)) return Blockable.INDETERMINATE;
        if (maxPathLength != -1 && path.size() > maxPathLength) return Blockable.INDETERMINATE;

        path.add(b);
        try {
            List<Node> kids = children(graph, a, b, Z, descendantsMap, notFollowed);
            if (kids.isEmpty()) return Blockable.BLOCKED;
            for (Node c : kids) {
                Blockable r = descendCheck(graph, b, c, y, path, Z, maxPathLength, notFollowed, descendantsMap);
                if (r == Blockable.UNBLOCKABLE || r == Blockable.INDETERMINATE)
                    return Blockable.UNBLOCKABLE;
            }
            return Blockable.BLOCKED;
        } finally {
            path.remove(b);
        }
    }

    // ---------------------------------------------------------------------
    // Enum
    // ---------------------------------------------------------------------

    public enum Blockable { BLOCKED, UNBLOCKABLE, INDETERMINATE }
}