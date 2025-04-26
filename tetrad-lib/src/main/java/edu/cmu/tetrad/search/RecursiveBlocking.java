package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;

import java.util.*;

public class RecursiveBlocking {

    /**
     * Retrieves set that blocks all blockable paths between x and y in the given graph, where this set contains the
     * given nodes.
     *
     * @param graph         the graph to analyze
     * @param x             the first node
     * @param y             the second node
     * @param containing    the set of nodes that must be in the sepset
     * @param notFollowed   the set of nodes that should not be followed along paths
     * @param maxPathLength the maximum length of a path to consider
     * @return the sepset of the endpoints for the given edge in the DAG graph based on the specified conditions, or
     * {@code null} if no sepset can be found.
     */
    public static Set<Node> blockPathsRecursively(Graph graph, Node x, Node y, Set<Node> containing, Set<Node> notFollowed,
                                                  int maxPathLength) throws InterruptedException {
        return blockPathsRecursivelyVisit(graph, x, y, containing, notFollowed, graph.paths().getDescendantsMap(), maxPathLength);
    }

    private static Set<Node> blockPathsRecursivelyVisit(Graph graph, Node x, Node y, Set<Node> containing,
                                                        Set<Node> notFollowed, Map<Node, Set<Node>> ancestorMap, int maxPathLength)
            throws InterruptedException {
        if (x == y) {
            return null;
        }

        Set<Node> z = new HashSet<>(containing);

        Set<Node> path = new HashSet<>();
        path.add(x);

        for (Node b : graph.getAdjacentNodes(x)) {
            if (Thread.currentThread().isInterrupted()) {
                return null;
            }

            findPathToTarget(graph, x, b, y, path, z, maxPathLength, notFollowed, ancestorMap);
        }

        return z;
    }

    /**
     * Finds a path from node a to node b that can be blocked by conditioning on a set of nodes z. The method returns
     * true if the path can be blocked, and false otherwise.
     * <p>
     * The side effects of this method are changes to z and colliders; this method is private, and the public methods
     * that call it are responsible for handling these side effects.
     *
     * @param graph         The graph containing the nodes.
     * @param a             The first node in the pair.
     * @param b             The second node in the pair.
     * @param y             The target node.
     * @param path          The current path.
     * @param z             The set of nodes that can block the path. This is a set of conditioning nodes that is being
     *                      built.
     * @param maxPathLength The maximum length of the paths to consider.
     * @param notFollowed   A set of nodes that should not be followed along paths.
     * @return True if the path can be blocked, false otherwise.
     */
    public static Blockable findPathToTarget(Graph graph, Node a, Node b, Node y, Set<Node> path, Set<Node> z,
                                                          int maxPathLength, Set<Node> notFollowed, Map<Node, Set<Node>> ancestorMap)
            throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            return Blockable.INDETERMINATE;
        }

        if (b == y) {
            return Blockable.UNBLOCKABLE;
        }

        if (path.contains(b)) {
            return Blockable.UNBLOCKABLE;
        }

        path.add(b);

        if (maxPathLength != -1) {
            if (path.size() > maxPathLength) {
                return Blockable.INDETERMINATE;
            }
        }

        // If b is latent, we cannot condition on it. If z already contains b, we know we've already conditioned on
        // it, so there's no point considering further whether to condition on it or now.
        if (b.getNodeType() == NodeType.LATENT || z.contains(b)) {
            List<Node> passNodes = getReachableNodes(graph, a, b, z, ancestorMap);
            passNodes.removeAll(notFollowed);

            for (Node c : passNodes) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException();
                }

                Blockable blockable = findPathToTarget(graph, b, c, y, path, z, maxPathLength, notFollowed, ancestorMap);

                if (blockable == Blockable.UNBLOCKABLE || blockable == Blockable.INDETERMINATE) {
                    return Blockable.UNBLOCKABLE;
                }
            }

            path.remove(b);
            return Blockable.BLOCKED; // blocked.
        } else {

            // We're going to look to see whether the path to y has already been blocked by z. If it has, we can
            // stop here. If it hasn't, we'll see if we can block it by conditioning also on b. If it can't be
            // blocked either way, well, then, it just can't be blocked.
            boolean blockable1 = true;

            List<Node> passNodes = getReachableNodes(graph, a, b, z, ancestorMap);
            passNodes.removeAll(notFollowed);

            for (Node c : passNodes) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException();
                }

                Blockable blockType = findPathToTarget(graph, b, c, y, path, z, maxPathLength, notFollowed, ancestorMap);

                if (blockType == Blockable.UNBLOCKABLE || blockType == Blockable.INDETERMINATE) {
                    blockable1 = false;
                    break;
                }
            }

            if (blockable1) {
                path.remove(b);
                return Blockable.BLOCKED;
            }

            z.add(b);

            boolean blockable2 = true;
            passNodes = getReachableNodes(graph, a, b, z, ancestorMap);
            passNodes.removeAll(notFollowed);

            for (Node c : passNodes) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException();
                }

                Blockable blackable = findPathToTarget(graph, b, c, y, path, z, maxPathLength, notFollowed, ancestorMap);

                if (blackable == Blockable.UNBLOCKABLE || blackable == Blockable.INDETERMINATE) {
                    blockable2 = false;
                    break;
                }
            }

            if (blockable2) {
                path.remove(b);
                return Blockable.BLOCKED;
            }

            path.remove(b);
            return Blockable.UNBLOCKABLE;
        }
    }

    private static List<Node> getReachableNodes(Graph graph, Node a, Node b, Set<Node> z, Map<Node, Set<Node>> ancestorMap) {
        List<Node> passNodes = new ArrayList<>();

        for (Node c : graph.getAdjacentNodes(b)) {
            if (c == a) {
                continue;
            }

            if (reachable(graph, a, b, c, z, ancestorMap)) {
                passNodes.add(c);
            }
        }

        return passNodes;
    }

    private static boolean reachable(Graph graph, Node a, Node b, Node c, Set<Node> z,
                                     Map<Node, Set<Node>> ancestors) {
        boolean collider = graph.isDefCollider(a, b, c);

        if ((!collider || graph.isUnderlineTriple(a, b, c)) && !z.contains(b)) {
            return true;
        }

        if (ancestors == null) {
            return collider && graph.paths().isAncestorOfAnyZ(b, z);
        } else {
            boolean ancestor = false;

            for (Node _z : ancestors.get(b)) {
                if (z.contains(_z)) {
                    ancestor = true;
                    break;
                }
            }

            return collider && ancestor;
        }
    }

    public static enum Blockable {BLOCKED, UNBLOCKABLE, INDETERMINATE}
}
