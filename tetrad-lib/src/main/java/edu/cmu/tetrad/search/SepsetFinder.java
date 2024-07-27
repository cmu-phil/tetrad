package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.util.SublistGenerator;
import edu.cmu.tetrad.util.TetradLogger;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Function;

public class SepsetFinder {

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
    public static Set<Node> getSepsetContainingMaxP(Graph graph, Node x, Node y, Set<Node> containing, IndependenceTest test, int depth) {
        List<Node> adjx = graph.getAdjacentNodes(x);
        List<Node> adjy = graph.getAdjacentNodes(y);
        adjx.remove(y);
        adjy.remove(x);

        if (containing != null) {
            adjx.removeAll(containing);
            adjy.removeAll(containing);
        }

        // remove latents.
        adjx.removeIf(node -> node.getNodeType() == NodeType.LATENT);
        adjy.removeIf(node -> node.getNodeType() == NodeType.LATENT);

        List<List<Integer>> choices = getChoices(adjx, depth);
        Function<List<Integer>, Double> function = choice -> getPValue(x, y, combination(choice, adjx), test);

        // Find the object that maximizes the function in parallel
        List<Integer> maxObject = choices.parallelStream()
                .max(Comparator.comparing(function))
                .orElse(null);

        if (maxObject != null && getPValue(x, y, combination(maxObject, adjx), test) > test.getAlpha()) {
            return combination(maxObject, adjx);
        }

        // Do the same for adjy.
        choices = getChoices(adjx, depth);
        function = choice -> getPValue(x, y, combination(choice, adjx), test);

        // Find the object that maximizes the function in parallel
        maxObject = choices.parallelStream()
                .max(Comparator.comparing(function))
                .orElse(null);

        if (maxObject != null && getPValue(x, y, combination(maxObject, adjx), test) > test.getAlpha()) {
            return combination(maxObject, adjx);
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
    public static Set<Node> getSepsetContainingMinP(Graph graph, Node x, Node y, Set<Node> containing, IndependenceTest test, int depth) {
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
        Function<List<Integer>, Double> function = choice -> getPValue(x, y, combination(choice, adjx), test);

        List<Integer> minObject = choices.parallelStream()
                .min(Comparator.comparing(function))
                .orElse(null);

        if (minObject != null && getPValue(x, y, combination(minObject, adjx), test) > test.getAlpha()) {
            return combination(minObject, adjx);
        }

        choices = getChoices(adjx, depth);
        function = choice -> getPValue(x, y, combination(choice, adjx), test);

        minObject = choices.parallelStream()
                .min(Comparator.comparing(function))
                .orElse(null);

        if (minObject != null && getPValue(x, y, combination(minObject, adjx), test) > test.getAlpha()) {
            return combination(minObject, adjx);
        }

        return null;
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
    public static Set<Node> getSepsetContainingRecursive(Graph graph, Node x, Node y, Set<Node> containing, IndependenceTest test) {
        return getSepsetVisit(graph, x, y, containing, graph.paths().getAncestorMap(), test);
    }

    /**
     * Retrieves the parents of nodes X and Y that also share in their parents based on the given DAG graph and the
     * provided independence test.
     *
     * @param dag  the DAG graph to analyze
     * @param x    the first node
     * @param y    the second node
     * @param test the independence test to use
     * @return the set of nodes that are parents of both X and Y, excluding X and Y themselves, and excluding any latent
     * nodes. Returns {@code null} if no common parents can be found or if the given graph is not a legal DAG.
     * @throws IllegalArgumentException if the given graph is not a legal DAG
     */
    public static Set<Node> getSepsetParentsOfXorY(Graph dag, Node x, Node y, IndependenceTest test) {
        if (!dag.paths().isLegalDag()) {
            throw new IllegalArgumentException("Graph is not a legal DAG; can't use this method.");
        }

        Set<Node> parentsX = new HashSet<>(dag.getParents(x));
        Set<Node> parentsY = new HashSet<>(dag.getParents(y));
        parentsX.remove(y);
        parentsY.remove(x);

        // Remove latents.
        parentsX.removeIf(node -> node.getNodeType() == NodeType.LATENT);
        parentsY.removeIf(node -> node.getNodeType() == NodeType.LATENT);

        if (test.checkIndependence(x, y, parentsX).isIndependent()) {
            return parentsX;
        } else if (test.checkIndependence(x, y, parentsY).isIndependent()) {
            return parentsY;
        }

        return null;
    }


    /**
     * Calculates the sepset path blocking out-of operation for a given pair of nodes in a graph. This method searches
     * for m-connecting paths out of x and y, and then tries to block these paths by conditioning on definite
     * noncollider nodes. If all paths are blocked, the method returns the sepset; otherwise, it returns null. The
     * length of the paths to consider can be limited by the maxLength parameter, and the depth of the final sepset can
     * be limited by the depth parameter. When increasing the considered path length does not yield any new paths, the
     * search is terminated early.
     *
     * @param mpdag      The graph representing the Markov equivalence class that contains the nodes.
     * @param x          The first node in the pair.
     * @param y          The second node in the pair.
     * @param test       The independence test object to use for checking independence.
     * @param maxLength  The maximum length of the paths to consider. If set to a negative value or a value greater than
     *                   the number of nodes minus one, it is adjusted accordingly.
     * @param depth      The maximum depth of the final sepset. If set to a negative value, no limit is applied.
     * @param printTrace A boolean flag indicating whether to print trace information.
     * @return The sepset if independence holds, otherwise null.
     */
    public static Set<Node> getSepsetPathBlockingOutOfX(Graph mpdag, Node x, Node y, IndependenceTest test,
                                                        int maxLength, int depth, boolean printTrace) {

        if (maxLength < 0 || maxLength > mpdag.getNumNodes() - 1) {
            maxLength = mpdag.getNumNodes() - 1;
        }

        Set<List<Node>> lastPaths;
        Set<List<Node>> paths = new HashSet<>();

        Set<Node> conditioningSet = new HashSet<>();
        Set<Node> couldBeColliders = new HashSet<>();
        Set<Node> blacklist = new HashSet<>();

        for (int length = 1; length < maxLength; length++) {
            lastPaths = new HashSet<>(paths);

            paths = tryToBlockPaths(x, y, mpdag, conditioningSet, couldBeColliders, blacklist, length, printTrace);

            if (paths.equals(lastPaths)) {
                break;
            }
        }

        List<Node> couldBeCollidersList = new ArrayList<>(couldBeColliders);
        conditioningSet.removeAll(couldBeColliders);

        SublistGenerator generator = new SublistGenerator(couldBeCollidersList.size(), depth);
        int[] choice;

        while ((choice = generator.next()) != null) {
            Set<Node> sepset = new HashSet<>();

            for (int k : choice) {
                sepset.add(couldBeCollidersList.get(k));
            }

            sepset.addAll(conditioningSet);

            if (depth != -1 && sepset.size() > depth) {
                continue;
            }

            sepset.remove(y);

            if (test.checkIndependence(x, y, sepset).isIndependent()) {
                Set<Node> _z = new HashSet<>(sepset);
                boolean removed;

                do {
                    removed = false;

                    for (Node w : new HashSet<>(_z)) {
                        Set<Node> __z = new HashSet<>(_z);

                        __z.remove(w);

                        if (test.checkIndependence(x, y, __z).isIndependent()) {
                            removed = true;
                            _z = __z;
                        }
                    }
                } while (removed);

                sepset = new HashSet<>(_z);

                if (!test.checkIndependence(x, y, sepset).isIndependent()) {
                    throw new IllegalArgumentException("Independence does not hold.");
                }

                if (printTrace) {
                    TetradLogger.getInstance().log("\n\tINDEPENDENCE HOLDS!: " + LogUtilsSearch.independenceFact(x, y, sepset));
                }

                return sepset;
            }
        }

        return null;
    }


    /**
     * Calculates the sepset path blocking out-of operation for a given pair of nodes in a graph. This method searches
     * for m-connecting paths out of x and y, and then tries to block these paths by conditioning on definite
     * noncollider nodes. If all paths are blocked, the method returns the sepset; otherwise, it returns null. The
     * length of the paths to consider can be limited by the maxLength parameter, and the depth of the final sepset can
     * be limited by the depth parameter. When increasing the considered path length does not yield any new paths, the
     * search is terminated early.
     *
     * @param mpdag      The graph representing the Markov equivalence class that contains the nodes.
     * @param x          The first node in the pair.
     * @param y          The second node in the pair.
     * @param test       The independence test object to use for checking independence.
     * @param maxLength  The maximum length of the paths to consider. If set to a negative value or a value greater than
     *                   the number of nodes minus one, it is adjusted accordingly.
     * @param depth      The maximum depth of the final sepset. If set to a negative value, no limit is applied.
     * @param printTrace A boolean flag indicating whether to print trace information.
     * @return The sepset if independence holds, otherwise null.
     */
    public static Set<Node> getSepsetPathBlockingOutOfX2(Graph mpdag, Node x, Node y, IndependenceTest test,
                                                         int maxLength, int depth, boolean printTrace) {

        if (maxLength < 0 || maxLength > mpdag.getNumNodes() - 1) {
            maxLength = mpdag.getNumNodes() - 1;
        }

        Set<List<Node>> lastPaths;
        Set<List<Node>> paths = new HashSet<>();

        Set<Node> conditioningSet = new HashSet<>();
        Set<Triple> couldBeColliders = new HashSet<>();
        Set<Node> blacklist = new HashSet<>();

        for (int length = 1; length < maxLength; length++) {
            lastPaths = new HashSet<>(paths);

            paths = tryToBlockPaths2(x, y, mpdag, conditioningSet, couldBeColliders, blacklist, length, printTrace);

            if (paths.equals(lastPaths)) {
                break;
            }
        }

        if (test.checkIndependence(x, y, conditioningSet).isIndependent()) {
            if (printTrace) {
                TetradLogger.getInstance().log("\n\tINDEPENDENCE HOLDS!: " + LogUtilsSearch.independenceFact(x, y, conditioningSet));
            }

            return conditioningSet;
        }

        return null;
    }

    /**
     * Computes the sepset path blocking out of either node X or Y in the given MPDAG graph.
     *
     * @param mpdag      the directed acyclic graph (MPDAG) representing the variables and their dependencies
     * @param x          the first node
     * @param y          the second node
     * @param test       the independence test used to determine conditional independence of variables
     * @param maxLength  the maximum length of the path to search for in the MPDAG
     * @param depth      the depth of recursion to be used in the algorithm
     * @param printTrace a flag indicating whether to print the trace of the execution
     * @return a set of nodes representing the sepset path blocking out of either node X or Y
     */
    public static Set<Node> getSepsetPathBlockingOutOfXorY(Graph mpdag, Node x, Node y, IndependenceTest test,
                                                           int maxLength, int depth, boolean printTrace) {
        Set<Node> sepsetPathBlockingOutOfX = getSepsetPathBlockingOutOfX(mpdag, x, y, test, maxLength, depth, printTrace);
        Set<Node> sepsetPathBlockingOutOfY = getSepsetPathBlockingOutOfX(mpdag, y, x, test, maxLength, depth, printTrace);

        if (sepsetPathBlockingOutOfX != null) {
            return sepsetPathBlockingOutOfX;
        } else {
            return sepsetPathBlockingOutOfY;
        }


//        if (mpdag.getAdjacentNodes(x).size() < mpdag.getAdjacentNodes(y).size()) {
//             return sepsetPathBlockingOutOfX;
//        } else {
//            return sepsetPathBlockingOutOfX;
//        }
    }


    /**
     * Searches for sets, by following paths from x to y in the given MPDAG, that could possibly block all paths from x
     * to y except for an edge from x to y itself. These possible sets are then tested for independence, and the first
     * set that is found to be independent is returned as the sepset.
     * <p>
     * This is the sepset finding method from LV-lite.
     *
     * @param mpdag      the MPDAG graph to analyze (can be a DAG or a CPDAG)
     * @param x          the first node
     * @param y          the second node
     * @param test       the independence test to use
     * @param maxLength  the maximum blocking length for paths, or -1 for no limit
     * @param depth      the maximum depth of the sepset, or -1 for no limit
     * @param printTrace whether to print trace information; false by default. This can be quite verbose, so it's
     *                   recommended to only use this for debugging.
     * @return the sepset of the endpoints for the given edge in the DAG graph based on the specified conditions, or
     * {@code null} if no sepset can be found.
     */
    public static Set<Node> getSepsetPathBlockingXtoY(Graph mpdag, Node x, Node y, IndependenceTest test,
                                                      int maxLength, int depth, boolean printTrace) {
        if (printTrace) {
            Edge e = mpdag.getEdge(x, y);
            TetradLogger.getInstance().log("\n\n### CHECKING x = " + x + " y = " + y + "edge = " + ((e != null) ? e : "null") + " ###\n\n");
        }

        // This is the set of all possible conditioning variables, though note below.
        Set<Node> conditioningSet = new HashSet<>();

        // We are considering removing the edge x *-* y, so for length 2 paths, so we don't know whether
        // noncollider z2 in the GRaSP/BOSS DAG is a noncollider or a collider in the true DAG. We need to
        // check both scenarios.
        Set<Node> couldBeColliders = new HashSet<>();

        Set<List<Node>> paths;

        boolean _changed = true;

        while (_changed) {
            _changed = false;

            paths = mpdag.paths().allPaths(x, y, -1, maxLength, conditioningSet, null, false);

            // We note whether all current paths are blocked.
            boolean allBlocked = true;

            List<List<Node>> _paths = new ArrayList<>(paths);

            // Sort paths by increasing size. We want to block the sorter paths first.
            _paths.sort(Comparator.comparingInt(List::size));

            for (List<Node> path : _paths) {
                boolean blocked = false;

                for (int n = 1; n < path.size() - 1; n++) {
                    Node z1 = path.get(n - 1);
                    Node z2 = path.get(n);
                    Node z3 = path.get(n + 1);

                    if (!mpdag.isDefCollider(z1, z2, z3)) {
                        if (conditioningSet.contains(z2)) {
                            blocked = true;

                            if (printTrace) {
                                TetradLogger.getInstance().log("This " + path + "--is already blocked by " + z2);
                            }

                            break;
                        }

                        conditioningSet.add(z2);
                        blocked = true;
                        _changed = true;

                        if (printTrace) {
                            TetradLogger.getInstance().log("Blocking " + path + " with noncollider " + z2);
                        }

                        addCouldBeCollider(z1, z2, z3, path, mpdag, couldBeColliders, printTrace);

                        if (depth != -1 && conditioningSet.size() > depth) {
                            return null;
                        }

                        break;
                    }
                }

                if (path.size() - 1 > 1 && !blocked) {
                    allBlocked = false;
                }
            }

            // We need to block *all* of the current paths, so if any path remains unblocked after that above, we
            // need to return false (since we can't remove the edge).
            if (!allBlocked) {
                return null;
            }
        }

        if (printTrace) {
            TetradLogger.getInstance().log("conditioningSet: " + conditioningSet);
            TetradLogger.getInstance().log("couldBeColliders: " + couldBeColliders);
        }

        // Now, for each conditioning set we identify, where the length-2 conditioningSet are either included or not
        // in the set, we check independence greedily. Hopefully the number of options here is small.
        List<Node> couldBeCollidersList = new ArrayList<>(couldBeColliders);
        conditioningSet.removeAll(couldBeColliders);

        SublistGenerator generator = new SublistGenerator(couldBeCollidersList.size(), couldBeCollidersList.size());
        int[] choice;

        while ((choice = generator.next()) != null) {
            Set<Node> sepset = new HashSet<>();

            for (int k : choice) {
                sepset.add(couldBeCollidersList.get(k));
            }

            sepset.addAll(conditioningSet);

            if (depth != -1 && sepset.size() > depth) {
                continue;
            }

            if (test.checkIndependence(x, y, sepset).isIndependent()) {
                if (printTrace) {
                    TetradLogger.getInstance().log("\n\tINDEPENDENCE HOLDS!: " + LogUtilsSearch.independenceFact(x, y, sepset));
                }

                return sepset;
            }
        }

        // We've checked a sufficient set of possible sepsets, and none of them worked, so we return false, since
        // we can't remove the edge.
        return null;
    }


    public static Set<Node> getDsepSepset(Graph mag, Node x, Node y, IndependenceTest test) {
        Set<Node> sepset1 = mag.paths().dsep(x, y);
        Set<Node> sepset2 = mag.paths().dsep(y, x);

        if (test.checkIndependence(x, y, sepset1).isIndependent()) {
            return sepset1;
        } else if (test.checkIndependence(x, y, sepset2).isIndependent()) {
            return sepset2;
        } else {
            return null;
        }
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
                if (sepsetPathFound(graph, x, b, y, path, z, colliders, -1, ancestorMap, test)) {
                    return null;
                }
            }
        } while (!new HashSet<>(z).equals(new HashSet<>(_z)));

        if (test.checkIndependence(x, y, z).isIndependent()) {
            return z;
        } else {
            return null;
        }
    }

    private static boolean sepsetPathFound(Graph graph, Node a, Node b, Node y, Set<Node> path, Set<Node> z, Set<Triple> colliders, int bound, Map<Node,
            Set<Node>> ancestorMap, IndependenceTest test) {
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
                if (sepsetPathFound(graph, b, c, y, path, z, colliders, bound, ancestorMap, test)) {
                    return true;
                }
            }

            path.remove(b);
            return false;
        } else {
            boolean found1 = false;
            Set<Triple> _colliders1 = new HashSet<>();

            for (Node c : getPassNodes(graph, a, b, z, ancestorMap)) {
                if (sepsetPathFound(graph, b, c, y, path, z, _colliders1, bound, ancestorMap, test)) {
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
                if (sepsetPathFound(graph, b, c, y, path, z, _colliders2, bound, ancestorMap, test)) {
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
        return test.checkIndependence(x, y, combination).getPValue();
    }


    /**
     * Attempts to block all paths from x to y by conditioning on definite noncollider nodes. If all paths are blocked,
     * returns true; otherwise, returns false.
     *
     * @param y                the second node
     * @param mpdag            the MPDAG graph to analyze
     * @param conditioningSet  the set of nodes to condition on
     * @param couldBeColliders the set of nodes that could be colliders
     * @param printTrace       whether to print trace information
     */
    private static Set<List<Node>> tryToBlockPaths(Node x, Node y, Graph mpdag, Set<Node> conditioningSet, Set<Node> couldBeColliders,
                                                   Set<Node> blacklist, int maxLength, boolean printTrace) {
        Set<List<Node>> paths = mpdag.paths().allPathsOutOf(x, maxLength, conditioningSet, false);

        // Sort paths by increasing size. We want to block the shorter paths first.
        List<List<Node>> _paths = new ArrayList<>(paths);
        _paths.sort(Comparator.comparingInt(List::size));

        for (List<Node> path : _paths) {
            if (path.size() - 1 < 2) {
                continue;
            }

            blockPath(path, mpdag, conditioningSet, couldBeColliders, blacklist, x, y, printTrace);
        }

        return paths;
    }


    /**
     * Attempts to block all paths from x to y by conditioning on definite noncollider nodes. If all paths are blocked,
     * returns true; otherwise, returns false.
     *
     * @param y                the second node
     * @param mpdag            the MPDAG graph to analyze
     * @param conditioningSet  the set of nodes to condition on
     * @param couldBeColliders the set of nodes that could be colliders
     * @param printTrace       whether to print trace information
     */
    private static Set<List<Node>> tryToBlockPaths2(Node x, Node y, Graph mpdag, Set<Node> conditioningSet, Set<Triple> couldBeColliders,
                                                    Set<Node> blacklist, int maxLength, boolean printTrace) {
        Set<List<Node>> paths = mpdag.paths().allPathsOutOf(x, maxLength, conditioningSet, false);
//        Set<List<Node>> paths = allPathsOutOf3(x, y, conditioningSet, maxLength, false, mpdag);

        // Sort paths by increasing size. We want to block the shorter paths first.
        // Sort paths by increasing size. We want to block the shorter paths first.
        List<List<Node>> _paths = new ArrayList<>(paths);
        _paths.sort(Comparator.comparingInt(List::size));

        for (List<Node> path : _paths) {
            if (path.size() - 1 < 2) {
                continue;
            }

            blockPath2(path, mpdag, conditioningSet, couldBeColliders, blacklist, x, y, printTrace);
        }

        return paths;
    }

    /**
     * Tries to block the given path is blocked by conditioning on definite noncollider nodes. Return true if the path
     * is blocked, false otherwise.
     *
     * @param path             the path to check
     * @param mpdag            the MPDAG graph to analyze
     * @param conditioningSet  the set of nodes to condition on; this may be modified
     * @param couldBeColliders the set of nodes that could be colliders; this may be modified
     * @param y                the second node
     * @param printTrace       whether to print trace information
     */
    private static void blockPath(List<Node> path, Graph mpdag, Set<Node> conditioningSet, Set<Node> couldBeColliders, Set<Node> blacklist,
                                  Node x, Node y, boolean printTrace) {

        for (int n = 1; n < path.size() - 1; n++) {
            Node z1 = path.get(n - 1);
            Node z2 = path.get(n);
            Node z3 = path.get(n + 1);

            if (z2.getNodeType() == NodeType.LATENT) {
                continue;
            }

            if (z1.getNodeType().equals(NodeType.LATENT) || z3.getNodeType().equals(NodeType.LATENT)) {
                continue;
            }

            if (z1 == x && z3 == y && mpdag.isDefCollider(z1, z2, z3)) {
                blacklist.add(z2);
                break;
            }

            if (mpdag.isDefNoncollider(z1, z2, z3)) {
                if (conditioningSet.contains(z2)) {
                    if (printTrace) {
                        TetradLogger.getInstance().log("This " + path + "--is already blocked by " + z2);
                    }

                    if (z1 == x) {
                        addCouldBeCollider(z1, z2, z3, path, mpdag, couldBeColliders, printTrace);
                    }
                }

                conditioningSet.add(z2);
                conditioningSet.removeAll(blacklist);

                if (printTrace) {
                    TetradLogger.getInstance().log("Blocking " + path + " with noncollider " + z2);
                }

                // If this noncollider is adjacent to the endpoints (i.e. is covered), we note that
                // it could be a collider. We will need to either consider this to be a collider or
                // a noncollider below.
                if (z1 == x) {
                    addCouldBeCollider(z1, z2, z3, path, mpdag, couldBeColliders, printTrace);
                }

                break;
            }
        }

    }


    /**
     * Tries to block the given path is blocked by conditioning on definite noncollider nodes. Return true if the path
     * is blocked, false otherwise.
     *
     * @param path             the path to check
     * @param mpdag            the MPDAG graph to analyze
     * @param conditioningSet  the set of nodes to condition on; this may be modified
     * @param couldBeColliders the set of nodes that could be colliders; this may be modified
     * @param y                the second node
     * @param printTrace       whether to print trace information
     */
    private static void blockPath2(List<Node> path, Graph mpdag, Set<Node> conditioningSet, Set<Triple> couldBeColliders, Set<Node> blacklist,
                                   Node x, Node y, boolean printTrace) {

        for (int n = 1; n < path.size() - 1; n++) {
            Node z1 = path.get(n - 1);
            Node z2 = path.get(n);
            Node z3 = path.get(n + 1);

            if (z2 == y) {
                break;
            }

            if (z2.getNodeType() == NodeType.LATENT) {
                continue;
            }

            if (z1.getNodeType().equals(NodeType.LATENT) || z3.getNodeType().equals(NodeType.LATENT)) {
                continue;
            }

            if (z1 == x && z3 == y && mpdag.isDefCollider(z1, z2, z3)) {
//                blacklist.add(z2);
                addCouldBeCollider2(z1, z2, z3, path, mpdag, couldBeColliders, printTrace);
                break;
            }

            // If this noncollider is adjacent to the endpoints (i.e. is covered), we note that
            // it could be a collider. We will need to either consider this to be a collider or
            // a noncollider below.
//            if (z1 == x) {
            if (addCouldBeCollider2(z1, z2, z3, path, mpdag, couldBeColliders, printTrace)) {
                break;
            }

            if (couldBeColliders.contains(new Triple(z1, z2, z3))) {
                break;
            }
//            }

            if (mpdag.isDefNoncollider(z1, z2, z3)) {
                if (conditioningSet.contains(z2)) {
                    if (printTrace) {
                        TetradLogger.getInstance().log("This " + path + "--is already blocked by " + z2);
                    }

                    if (z1 == x) {
                        addCouldBeCollider2(z1, z2, z3, path, mpdag, couldBeColliders, printTrace);
                    }
                }

                conditioningSet.add(z2);
                conditioningSet.removeAll(blacklist);

                if (printTrace) {
                    TetradLogger.getInstance().log("Blocking " + path + " with noncollider " + z2);
                }


                break;
            }
        }

    }

    private static void addCouldBeCollider(Node z1, Node z2, Node z3, List<Node> path, Graph mpdag,
                                           Set<Node> couldBeColliders, boolean printTrace) {
        if (mpdag.isAdjacentTo(z1, z3)) {
            couldBeColliders.add(z2);

            if (printTrace) {
                TetradLogger.getInstance().log("Noting that " + z2 + " could be a collider on " + path);
            }
        }
    }

    private static boolean addCouldBeCollider2(Node z1, Node z2, Node z3, List<Node> path, Graph mpdag,
                                               Set<Triple> couldBeColliders, boolean printTrace) {
        if (mpdag.isAdjacentTo(z1, z3)) {
            couldBeColliders.add(new Triple(z1, z2, z3));

            if (printTrace) {
                TetradLogger.getInstance().log("Noting that " + z2 + " could be a collider on " + path);
            }

            return true;
        }

        return false;
    }

    public static Set<List<Node>> allPathsOutOf3(Node a, Node b, Set<Node> conditioningSet, int maxLength, boolean allowSelectionBias, Graph graph) {
        Queue<Node> Q = new ArrayDeque<>();
        Set<Node> V = new HashSet<>();
        Map<Node, Node> previous = new HashMap<>();
        Set<List<Node>> paths = new HashSet<>();

        Q.offer(a);
        V.add(a);
        V.add(b);

        previous.put(a, null);

        W:
        while (!Q.isEmpty()) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            Node t = Q.poll();

            for (Node e : graph.getAdjacentNodes(t)) {
                if (Thread.currentThread().isInterrupted()) {
                    break W;
                }

//                if (e == b) {
//                    continue;
//                }

                if (V.contains(e)) {
                    continue;
                }

                previous.put(e, t);

                LinkedList<Node> path = new LinkedList<>();

                Node d = e;

                do {
                    path.addFirst(d);
                    d = previous.get(d);
                } while (previous.get(d) != null);

                path.addFirst(a);

                if (path.size() - 1 > maxLength) {
                    break;
                }

                // Now we have a path. Check that it's m-connecting.
                if (path.size() - 1 >= 1 && !graph.paths().isMConnectingPath(path, conditioningSet, allowSelectionBias)) {
                    continue;
                }

//                if (path.size() - 1 >= 1) {
                    paths.add(new ArrayList<>(path));
                    System.out.println(GraphUtils.pathString(graph, path, conditioningSet, true, allowSelectionBias));
                    System.out.println();

//                }

                // Now we need to do something with this path... let's look at getSepsetPathBlockingOutOfX2.

                if (!V.contains(e)) {
                    Q.offer(e);
                    V.add(e);
                }
            }
        }

        return paths;
    }
}
