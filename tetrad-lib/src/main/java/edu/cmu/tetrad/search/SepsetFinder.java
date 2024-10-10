package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.util.SublistGenerator;
import edu.cmu.tetrad.util.TetradLogger;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * This class provides methods for finding sepsets in a given graph.
 */
public class SepsetFinder {

    /**
     * Private constructor to prevent instantiation.
     */
    public SepsetFinder() {
        // Private constructor to prevent instantiation.
    }

    /**
     * Returns the sepset that contains the greedy test for variables x and y in the given graph.
     *
     * @param graph      the graph containing the variables
     * @param x          the first variable
     * @param y          the second variable
     * @param containing the set of nodes that must be contained in the sepset (optional)
     * @param test       the independence test to use
     * @param depth      the depth of the search
     * @return the sepset containing the greedy test for variables x and y, or null if no sepset is found
     */
    public static Set<Node> getSepsetContainingGreedy(Graph graph, Node x, Node y, Set<Node> containing, IndependenceTest test, int depth) {
        List<Node> adjx = graph.getAdjacentNodes(x);
        List<Node> adjy = graph.getAdjacentNodes(y);
        adjx.remove(y);
        adjy.remove(x);

        if (containing != null) {
            adjx.removeAll(containing);
            adjy.removeAll(containing);
        }

        adjx.removeIf(node -> node.getNodeType() == NodeType.LATENT);
        adjy.removeIf(node -> node.getNodeType() == NodeType.LATENT);

        List<List<Integer>> choices = getChoices(adjx, depth);
        List<Integer> sepset = choices.parallelStream().filter(_choice -> separates(x, y, combination(_choice, adjx), test)).findFirst().orElse(null);

        if (sepset != null) {
            return combination(sepset, adjx);
        }

        // Do the same for adjy.
        choices = getChoices(adjy, depth);
        sepset = choices.parallelStream().filter(_choice -> separates(x, y, combination(_choice, adjy), test)).findFirst().orElse(null);

        if (sepset != null) {
            return combination(sepset, adjy);
        }

        return null;
    }

    /**
     * Returns the set of nodes that act as a separating set between two given nodes (x and y) in a graph. The method
     * calculates the p-value for each possible separating set and returns the set that has the maximum p-value above
     * the specified alpha threshold.
     *
     * @param graph      the graph containing the nodes
     * @param x          the first node
     * @param y          the second node
     * @param containing the set of nodes that must be included in the separating set (optional, can be null)
     * @param test       the independence test used to calculate the p-values
     * @param depth      the maximum depth to explore for each separating set
     * @return the set of nodes that act as a separating set, or null if such set is not found
     */
    public static Set<Node> getSepsetContainingMaxPHybrid(Graph graph, Node x, Node y, Set<Node> containing, IndependenceTest test, int depth) {
        List<Node> adjx = graph.getAdjacentNodes(x);
        List<Node> adjy = graph.getAdjacentNodes(y);
        adjx.remove(y);
        adjy.remove(x);

        if (containing != null) {
            adjx.removeAll(containing);
            adjy.removeAll(containing);
        }

        // Remove latent nodes.
        adjx.removeIf(node -> node.getNodeType() == NodeType.LATENT);
        adjy.removeIf(node -> node.getNodeType() == NodeType.LATENT);

        // Find the best separating set among adjx
        Set<Node> bestSepset = findMaxPSepset(x, y, adjx, test, depth);
        if (bestSepset != null) {
            return bestSepset;
        }

        // Find the best separating set among adjy
        return findMaxPSepset(x, y, adjy, test, depth);
    }

    private static Set<Node> findMaxPSepset(Node x, Node y, List<Node> adj, IndependenceTest test, int depth) {
        List<List<Integer>> choices = getChoices(adj, depth);
        double maxPValue = -1.0;
        List<Integer> bestChoice = null;

        for (List<Integer> choice : choices) {
            Set<Node> subset = combination(choice, adj);

            // Check if the subset is a separating set
            if (separates(x, y, subset, test)) {
                double pValue = getPValue(x, y, subset, test);

                // Track the subset with the highest p-value
                if (pValue > maxPValue) {
                    maxPValue = pValue;
                    bestChoice = choice;
                }
            }
        }

        // Check if we found a valid separating set with a p-value above the alpha threshold
        if (bestChoice != null && maxPValue > test.getAlpha()) {
            return combination(bestChoice, adj);
        }

        return null;
    }


    /**
     * Returns the sepset containing the minimum p-value for the given variables x and y.
     *
     * @param graph      the graph representing the network
     * @param x          the first node
     * @param y          the second node
     * @param containing the set of nodes to be excluded from the sepset
     * @param test       the independence test to use for calculating the p-value
     * @param depth      the depth of the search for the sepset
     * @return the sepset containing the minimum p-value, or null if no sepset is found
     */
    public static Set<Node> getSepsetContainingMinPHybrid(Graph graph, Node x, Node y, Set<Node> containing, IndependenceTest test, int depth) {
        List<Node> adjx = graph.getAdjacentNodes(x);
        List<Node> adjy = graph.getAdjacentNodes(y);
        adjx.remove(y);
        adjy.remove(x);

        if (containing != null) {
            adjx.removeAll(containing);
            adjy.removeAll(containing);
        }

        // Remove latent nodes.
        adjx.removeIf(node -> node.getNodeType() == NodeType.LATENT);
        adjy.removeIf(node -> node.getNodeType() == NodeType.LATENT);

        // Find the best separating set among adjx
        Set<Node> bestSepset = findMinPSepset(x, y, adjx, test, depth);
        if (bestSepset != null) {
            return bestSepset;
        }

        // Find the best separating set among adjy
        return findMinPSepset(x, y, adjy, test, depth);
    }

    private static Set<Node> findMinPSepset(Node x, Node y, List<Node> adj, IndependenceTest test, int depth) {
        List<List<Integer>> choices = getChoices(adj, depth);
        double minPValue = Double.MAX_VALUE;
        List<Integer> bestChoice = null;

        for (List<Integer> choice : choices) {
            Set<Node> subset = combination(choice, adj);

            // Check if the subset is a separating set
            if (separates(x, y, subset, test)) {
                double pValue = getPValue(x, y, subset, test);

                // Track the subset with the smallest p-value that is still above the alpha threshold
                if (pValue < minPValue && pValue > test.getAlpha()) {
                    minPValue = pValue;
                    bestChoice = choice;
                }
            }
        }

        // Return the combination with the smallest p-value above the alpha threshold
        if (bestChoice != null) {
            return combination(bestChoice, adj);
        }

        return null;
    }

    public static Set<Node> blockPathsLocalMarkov(Graph graph, Node x) {
        return new HashSet<>(graph.getParents(x));
    }

    /**
     * Retrieves the sepset (a set of nodes) between two given nodes. The sepset is the minimal set of nodes that need
     * to be conditioned on to render two nodes conditionally independent.
     *
     * @param graph      the graph to analyze
     * @param x          the first node
     * @param y          the second node
     * @param containing the set of nodes that must be in the sepset
     * @param test       the independence test to use
     * @return the sepset of the endpoints for the given edge in the DAG graph based on the specified conditions, or
     * {@code null} if no sepset can be found.
     */
    public static Set<Node> blockPathsRecursively(Graph graph, Node x, Node y, Set<Node> containing, IndependenceTest test) {
        return getSepsetVisit(graph, x, y, containing, graph.paths().getAncestorMap(), test);
    }

    private static Set<Node> getSepsetVisit(Graph graph, Node x, Node y, Set<Node> containing, Map<Node, Set<Node>> ancestorMap, IndependenceTest test) {
        if (x == y) {
            return null;
        }

        Set<Node> z = new HashSet<>(containing);

        Set<Node> _z;

        do {
            _z = new HashSet<>(z);

            Set<Node> path = new HashSet<>();
            path.add(x);
            Set<Triple> colliders = new HashSet<>();

            for (Node b : graph.getAdjacentNodes(x)) {
                if (sepsetPathFound(graph, x, b, y, path, z, colliders, -1, ancestorMap)) {
                    continue;
                }
            }
        } while (!new HashSet<>(z).equals(new HashSet<>(_z)));

        return z;
    }

    private static boolean sepsetPathFound(Graph graph, Node a, Node b, Node y, Set<Node> path, Set<Node> z, Set<Triple> colliders, int bound, Map<Node, Set<Node>> ancestorMap) {
        if (b == y) {
            return true;
        }

        if (path.contains(b)) {
            return false;
        }

        if (path.size() > (bound == -1 ? 1000 : bound)) {
            return false;
        }

        path.add(b);

        if (b.getNodeType() == NodeType.LATENT || z.contains(b)) {
            List<Node> passNodes = getPassNodes(graph, a, b, z, ancestorMap);

            for (Node c : passNodes) {
                if (sepsetPathFound(graph, b, c, y, path, z, colliders, bound, ancestorMap)) {
                    return true;
                }
            }

            path.remove(b);
            return false;
        } else {
            boolean found1 = false;
            Set<Triple> _colliders1 = new HashSet<>();

            for (Node c : getPassNodes(graph, a, b, z, ancestorMap)) {
                if (sepsetPathFound(graph, b, c, y, path, z, _colliders1, bound, ancestorMap)) {
                    found1 = true;
                    break;
                }
            }

            if (!found1) {
                path.remove(b);
                colliders.addAll(_colliders1);
                return false;
            }

            z.add(b);
            boolean found2 = false;
            Set<Triple> _colliders2 = new HashSet<>();

            for (Node c : getPassNodes(graph, a, b, z, ancestorMap)) {
                if (sepsetPathFound(graph, b, c, y, path, z, _colliders2, bound, ancestorMap)) {
                    found2 = true;
                    break;
                }
            }

            if (!found2) {
                path.remove(b);
                colliders.addAll(_colliders2);
                return false;
            }

            return true;
        }
    }

    private static List<Node> getPassNodes(Graph graph, Node a, Node b, Set<Node> z, Map<Node, Set<Node>> ancestorMap) {
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

    private static boolean reachable(Graph graph, Node a, Node b, Node c, Set<Node> z, Map<Node, Set<Node>> ancestors) {
        boolean collider = graph.isDefCollider(a, b, c);

        if ((!collider || graph.isUnderlineTriple(a, b, c)) && !z.contains(b)) {
            return true;
        }

        if (ancestors == null) {
            return collider && graph.paths().isAncestor(b, z);
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

    public static Set<Node> getPathBlockingSetRecursive(Graph graph, Node x, Node y, Set<Node> containing, IndependenceTest test) {
        Map<Triple, Boolean> pathMemo = new HashMap<>(); // Memoization map to store path exploration results.
        return getPathBlockingSetRecursiveVisit(graph, x, y, containing, graph.paths().getAncestorMap(), test, pathMemo);
    }

    private static Set<Node> getPathBlockingSetRecursiveVisit(Graph graph, Node x, Node y, Set<Node> containing, Map<Node, Set<Node>> ancestorMap, IndependenceTest test, Map<Triple, Boolean> pathMemo) {
        if (x == y) {
            return null;
        }

        Set<Node> z = new HashSet<>(containing);
        Set<Node> previousZ;

        do {
            previousZ = new HashSet<>(z);

            Set<Node> path = new HashSet<>();
            path.add(x);
            Set<Triple> colliders = new HashSet<>();

            // Iterate over adjacent nodes to find potential paths.
            for (Node b : graph.getAdjacentNodes(x)) {
                sepsetPathFound(graph, x, b, y, path, z, colliders, -1, ancestorMap);
            }
        } while (!previousZ.equals(z)); // Repeat if the set z changes.

        return z;
    }

    private static @NotNull List<List<Integer>> getChoices(List<Node> adjx, int depth) {
        List<List<Integer>> choices = new ArrayList<>();

        if (depth < 0 || depth > adjx.size()) depth = adjx.size();

        SublistGenerator cg = new SublistGenerator(adjx.size(), depth);
        int[] choice;

        while ((choice = cg.next()) != null) {
            choices.add(GraphUtils.asList(choice));
        }

        return choices;
    }

    private static Set<Node> combination(List<Integer> choice, List<Node> adj) {

        // Create a set of nodes from the subset of adjx represented by choice.
        Set<Node> combination = new HashSet<>();

        for (int i : choice) {
            combination.add(adj.get(i));
        }

        return combination;
    }

    private static boolean separates(Node x, Node y, Set<Node> combination, IndependenceTest test) {
        return test.checkIndependence(x, y, combination).isIndependent();
    }

    private static double getPValue(Node x, Node y, Set<Node> combination, IndependenceTest test) {
        double pValue = 0;
        try {
            pValue = test.checkIndependence(x, y, combination).getPValue();
        } catch (Exception e) {
            TetradLogger.getInstance().log("Error in getPValue: " + e.getMessage());
            return 0.0;
        }
        return Double.isNaN(pValue) ? 1.0 : pValue;
    }

    /**
     * Returns a set that blocks all paths that can be blocked by conditioning on noncolliders only, searching outward
     * from x.
     *
     * @param graph     The graph representing the Markov equivalence class that contains the nodes.
     * @param x         The first node in the pair.
     * @param y         The second node in the pair.
     * @param maxLength The maximum length of the paths to consider. If set to a negative value or a value greater than
     *                  the number of nodes minus one, it is adjusted accordingly.
     * @param isPag     A flag indicating whether the graph is a PAG or a CPDAG, true = PAG, false = MPDAG. This is
     *                  needed to make sure the proper version of the separation algorithm is used.
     * @return A set of nodes that can block all blockable paths from x to y that can be blocked with noncolliders only,
     * or null if no such set exists.
     */
    public static Set<Node> blockPathsNoncollidersOnly(Graph graph, Node x, Node y, int maxLength, boolean isPag) {
        Set<Node> cond = new HashSet<>();
        Set<Node> blackList = new HashSet<>();

        Deque<List<Node>> queue = new LinkedList<>();
        queue.add(Collections.singletonList(x));

        while (!queue.isEmpty()) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            List<Node> path = queue.poll();

            if (maxLength != -1 && path.size() > maxLength) {
                continue;
            }

            Node node = path.get(path.size() - 1);

            if (node == y) continue;

            Map<Node, Boolean> blocked = new HashMap<>();

            for (Node adjacent : graph.getAdjacentNodes(node)) {
                blocked.put(adjacent, false);

                if (!path.contains(adjacent)) {
                    List<Node> newPath = new ArrayList<>(path);
                    newPath.add(adjacent);

                    // If the path length is less than 3, it cannot form a triple and is added directly.
                    if (newPath.size() < 3) {
                        queue.add(newPath);
                    } else {
                        for (int i = 1; i < newPath.size() - 1; i++) {
                            if (blocked.get(adjacent)) {
                                break;
                            }

                            Node z1 = newPath.get(i - 1);
                            Node z2 = newPath.get(i);
                            Node z3 = newPath.get(i + 1);

                            // Skip this node if it forms a collider in the path, but move past it to
                            // find a noncollider.
                            if (graph.isDefCollider(z1, z2, z3)) {
                                blackList.add(z2);  // Mark the collider to prevent conditioning.

                                // This is a collider; if the path is not already blocked by a noncollider,
                                // we want to move past it to find a noncollider.
                                if (graph.paths().isMConnectingPath(path, cond, isPag)) {
                                    queue.offer(newPath);
                                }
                            } else {
                                if (!blackList.contains(z2)) {
                                    if (!graph.isAdjacentTo(z1, z3)) {
                                        cond.add(z2);
                                        blackList.addAll(graph.paths().getDescendants(z2));
                                        blocked.put(adjacent, true);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return cond;
    }

    public static @NotNull Set<Node> getSmallestSubset(Node x, Node y, Set<Node> cond, Graph graph, boolean isPag) {
        List<Node> _cond = new ArrayList<>(cond);
        SublistGenerator generator = new SublistGenerator(_cond.size(), -1);
        int[] choice;
        MsepTest test = new MsepTest(graph, isPag);

        while ((choice = generator.next()) != null) {
            Set<Node> sepset = new HashSet<>();

            for (int k : choice) {
                sepset.add(_cond.get(k));
            }

            if (test.checkIndependence(x, y, sepset).isIndependent()) {
                cond = sepset;
                break;
            }
        }

        return cond;
    }

    public static Set<Node> blockPathsWithMarkovBlanket(Node x, Graph G) {
        Set<Node> mb = new HashSet<>();

        LinkedList<Node> path = new LinkedList<>();

        // Follow all the colliders.
        markovBlanketFollowColliders(null, x, path, G, mb);
        mb.addAll(G.getAdjacentNodes(x));
        mb.remove(x);
        return mb;
    }

    /**
     * This method calculates the Markov Blanket by following colliders in a given graph.
     *
     * @param d    The node representing the direct cause (can be null).
     * @param a    The node for which the Markov Blanket is calculated.
     * @param path A linked list of nodes in the current path.
     * @param G    The graph in which the Markov Blanket is calculated.
     * @param mb   A set to store the nodes in the Markov Blanket.
     */
    private static void markovBlanketFollowColliders(Node d, Node a, LinkedList<Node> path, Graph G, Set<Node> mb) {
        if (path.contains(a)) return;
        path.add(a);

        for (Node b : G.getNodesOutTo(a, Endpoint.ARROW)) {
            if (path.contains(b)) continue;

            // Make sure that d*->a<-* b is a collider.
            if (d != null && !G.isDefCollider(d, a, b)) continue;

            for (Node c : G.getNodesInTo(b, Endpoint.ARROW)) {
                if (path.contains(c)) continue;

                if (!G.isDefCollider(a, b, c)) continue;

                // a *-> b <-* c
                mb.add(b);
                mb.add(c);

                markovBlanketFollowColliders(a, b, path, G, mb);
            }
        }

        path.remove(a);
    }
}
