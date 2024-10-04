package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.SublistGenerator;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Function;

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
     * Searches for sets, by following paths from x to y in the given MPDAG, that could possibly block all paths from x
     * to y except for an edge from x to y itself. These possible sets are then tested for independence, and the first
     * set that is found to be independent is returned as the sepset.
     * <p>
     * This is the sepset finding method from LV-lite.
     *
     * @param mpdag     the MPDAG graph to analyze (can be a DAG or a CPDAG)
     * @param x         the first node
     * @param y         the second node
     * @param test      the independence test to use
     * @param maxLength the maximum blocking length for paths, or -1 for no limit
     * @param depth     the maximum depth of the sepset, or -1 for no limit
     * @param verbose   whether to print trace information; false by default. This can be quite verbose, so it's
     *                  recommended to only use this for debugging.
     * @return the sepset of the endpoints for the given edge in the DAG graph based on the specified conditions, or
     * {@code null} if no sepset can be found.
     */
    public static Set<Node> getSepsetPathBlockingXtoY(Graph mpdag, Node x, Node y, IndependenceTest test,
                                                      int maxLength, int depth, boolean verbose) {
        if (verbose) {
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

            paths = bfsAllPaths(mpdag, conditioningSet, maxLength, x, y);

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

                            if (verbose) {
                                TetradLogger.getInstance().log("This " + path + "--is already blocked by " + z2);
                            }

                            break;
                        }

                        conditioningSet.add(z2);
                        blocked = true;
                        _changed = true;

                        addCouldBeCollider(z1, z2, z3, path, mpdag, couldBeColliders, verbose);

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
                return sepset;
            }
        }

        // We've checked a sufficient set of possible sepsets, and none of them worked, so we return false, since
        // we can't remove the edge.
        return null;
    }

    /**
     * Returns the sepset (separation set) between two nodes in a graph based on the given independence test.
     *
     * @param mag  The graph containing the nodes.
     * @param x    The first node.
     * @param y    The second node.
     * @param test The independence test used to check the independence between the nodes.
     * @return The sepset between the two nodes, or null if no sepset is found.
     */
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
     * Tries to block the given path is blocked by conditioning on definite noncollider nodes. Return true if the path
     * is blocked, false otherwise.
     *
     * @param path             the path to check
     * @param graph            the MPDAG graph to analyze
     * @param conditioningSet  the set of nodes to condition on; this may be modified
     * @param couldBeColliders the set of nodes that could be colliders; this may be modified
     * @param y                the second node
     * @param verbose          whether to print trace information
     */
    private static void blockPath(List<Node> path, Graph graph, Set<Node> conditioningSet, Set<Node> couldBeColliders, Set<Node> blacklist,
                                  Node x, Node y, boolean verbose) {

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

            if (z1 == x && z3 == y && graph.isDefCollider(z1, z2, z3)) {
                blacklist.add(z2);
                break;
            }

            if (!graph.isDefCollider(z1, z2, z3)) {
                if (conditioningSet.contains(z2)) {
                    conditioningSet.removeAll(blacklist);
                    addCouldBeCollider(z1, z2, z3, path, graph, couldBeColliders, verbose);
                }

                conditioningSet.add(z2);
                conditioningSet.removeAll(blacklist);

                // If this noncollider is adjacent to the endpoints (i.e. is covered), we note that
                // it could be a collider. We will need to either consider this to be a collider or
                // a noncollider below.
                addCouldBeCollider(z1, z2, z3, path, graph, couldBeColliders, verbose);
                break;
            }
        }
    }

    private static void addCouldBeCollider(Node z1, Node z2, Node z3, List<Node> path, Graph mpdag,
                                           Set<Node> couldBeColliders, boolean verbose) {
        if (mpdag.isAdjacentTo(z1, z3)) {
            couldBeColliders.add(z2);
        }
    }

    /**
     * Adds potential colliders to the set of couldBeColliders based on a given condition.
     *
     * @param z1               The first Node.
     * @param z2               The second Node.
     * @param z3               The third Node.
     * @param path             The List of Nodes representing the path.
     * @param mpdag            The Graph representing the Multi-Perturbation Directed Acyclic Graph.
     * @param couldBeColliders The Set of Triples representing potential colliders.
     * @param printTrace       A boolean indicating whether to print error traces.
     * @return true if z2 could be a collider on the given path, false otherwise.
     */
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

    /**
     * Performs a breadth-first search to find all paths from node x to node y in a given graph.
     *
     * @param graph        the graph to perform the search on
     * @param conditionSet a set of nodes to condition the paths on
     * @param maxLength    the maximum length of the paths, -1 for no limit
     * @param x            the starting node
     * @param y            the target node
     * @return a set of lists of nodes, representing all found paths from x to y
     * @throws IllegalArgumentException if the conditionSet is null
     */
    public static Set<List<Node>> bfsAllPaths(Graph graph, Set<Node> conditionSet, int maxLength, Node x, Node y) {
        Set<List<Node>> allPaths = new HashSet<>();
        Queue<List<Node>> queue = new LinkedList<>();
        queue.add(Collections.singletonList(x));

        if (conditionSet == null) {
            throw new IllegalArgumentException("Conditioning set cannot be null.");
        }

        while (!queue.isEmpty()) {
            List<Node> path = queue.poll();

            if (maxLength >= 0 && path.size() > maxLength) {
                continue;
            }

            Node node = path.get(path.size() - 1);

            if (node == y) {
                allPaths.add(path);
            } else {
                for (Node adjacent : graph.getAdjacentNodes(node)) {
                    if (!path.contains(adjacent)) {
                        List<Node> newPath = new ArrayList<>(path);
                        newPath.add(adjacent);
                        queue.add(newPath);

                        if (newPath.size() - 1 <= 1) {
                            queue.add(newPath);
                        } else {
                            if (graph.paths().isMConnectingPath(path, conditionSet, true)) {
                                queue.add(newPath);
                            }
                        }
                    }
                }
            }
        }

        return allPaths;
    }

    /**
     * Performs a breadth-first search to find all paths out of a specific node in a graph, considering certain
     * conditions and constraints.
     *
     * @param graph              the graph to search
     * @param blacklist          the set of nodes to exclude from the search
     * @param maxLength          the maximum length of the paths (-1 for unlimited)
     * @param x                  the starting node
     * @param y                  the destination node
     * @param allowSelectionBias flag to indicate whether to allow selection bias in path selection
     * @return a set of all paths that satisfy the conditions and constraints
     * @throws IllegalArgumentException if the conditioning set is null
     */
    public static Pair<Set<Node>, Set<Node>> bfsAllPathsOutOfX(Graph graph, Set<Node> blacklist, int maxLength,
                                                               Node x, Node y, boolean allowSelectionBias) {
        Set<Node> conditionSet = new HashSet<>();
        Set<Node> couldBeColliders = new HashSet<>();

        Queue<List<Node>> queue = new LinkedList<>();
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

            for (Node z3 : graph.getAdjacentNodes(node)) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                if (!path.contains(z3)) {
                    List<Node> newPath = new ArrayList<>(path);
                    newPath.add(z3);

                    if (newPath.size() - 1 == 1) {
                        queue.add(newPath);
                    }

                    // If the path is of at least length 1, and the last two nodes on the path form a noncollider
                    // with 'adjacent', we need to block these noncolliders first by conditioning on node.
                    if (newPath.size() - 1 > 1) {
                        Node z1 = newPath.get(newPath.size() - 3);
                        Node z2 = newPath.get(newPath.size() - 2);

                        if (!graph.isDefCollider(z1, z2, z3)) {
                            blockPath(newPath, graph, conditionSet, couldBeColliders, blacklist, x, y, true);

                            if (graph.paths().isMConnectingPath(newPath, conditionSet, allowSelectionBias)) {
                                queue.add(newPath);
                            }
                        }
                    }
                }
            }

            for (Node z3 : graph.getAdjacentNodes(node)) {
                if (!path.contains(z3)) {
                    List<Node> newPath = new ArrayList<>(path);
                    newPath.add(z3);

                    if (newPath.size() - 1 == 1) {
                        queue.add(newPath);
                    }

                    // If the path is of at least length 1, and the last two nodes on the path form a noncollider
                    // with 'adjacent', we need to block these noncolliders first by conditioning on node.
                    if (newPath.size() - 1 > 1) {
                        Node z1 = newPath.get(newPath.size() - 3);
                        Node z2 = newPath.get(newPath.size() - 2);

                        if (graph.isDefCollider(z1, z2, z3)) {
                            blockPath(newPath, graph, conditionSet, couldBeColliders, blacklist, x, y, true);

                            if (graph.paths().isMConnectingPath(newPath, conditionSet, allowSelectionBias)) {
                                queue.add(newPath);
                            }
                        }
                    }
                }
            }
        }

        return Pair.of(conditionSet, couldBeColliders);
    }

    /**
     * Finds all paths from a given starting node in a graph, with a maximum length and satisfying a set of conditions.
     *
     * @param graph              The input graph.
     * @param node1              The starting node for finding paths.
     * @param maxLength          The maximum length of paths to consider.
     * @param conditionSet       The set of conditions that the paths must satisfy.
     * @param allowSelectionBias Determines whether to allow biased selection when multiple paths are available.
     * @return A set of lists, where each list represents a path from the starting node that satisfies the conditions.
     */
    public static Set<List<Node>> allPathsOutOf(Graph graph, Node node1, int maxLength, Set<Node> conditionSet,
                                                boolean allowSelectionBias) {
        Set<List<Node>> paths = new HashSet<>();
        allPathsVisitOutOf(graph, null, node1, new HashSet<>(), new LinkedList<>(), paths, maxLength, conditionSet, allowSelectionBias);
        return paths;
    }

    private static void allPathsVisitOutOf(Graph graph, Node previous, Node node1, Set<Node> pathSet, LinkedList<Node> path, Set<List<Node>> paths, int maxLength,
                                           Set<Node> conditionSet, boolean allowSelectionBias) {
        if (maxLength != -1 && path.size() - 1 > maxLength) {
            return;
        }

        if (pathSet.contains(node1)) {
            return;
        }

        path.addLast(node1);
        pathSet.add(node1);

        LinkedList<Node> _path = new LinkedList<>(path);
        int maxPaths = 500;

        if (path.size() - 1 > 1) {
            if (paths.size() < maxPaths && graph.paths().isMConnectingPath(path, conditionSet, allowSelectionBias)) {
                paths.add(_path);
            }
        }

        for (Edge edge : graph.getEdges(node1)) {
            Node child = Edges.traverse(node1, edge);

            if (child == null) {
                continue;
            }

            if (pathSet.contains(child)) {
                continue;
            }

            if (paths.size() < maxPaths) {
                allPathsVisitOutOf(graph, node1, child, pathSet, path, paths, maxLength, conditionSet, allowSelectionBias);
            }
        }

        path.removeLast();
        pathSet.remove(node1);
    }

    /**
     * Calculates the sepset path blocking out-of operation for a given pair of nodes in a graph. This method searches
     * for m-connecting paths out of x and y, and then tries to block these paths by conditioning on definite
     * noncollider nodes. If all paths are blocked, the method returns the sepset; otherwise, it returns null. The
     * length of the paths to consider can be limited by the maxLength parameter, and the depth of the final sepset can
     * be limited by the depth parameter. When increasing the considered path length does not yield any new paths, the
     * search is terminated early.
     *
     * @param mpdag              The graph representing the Markov equivalence class that contains the nodes.
     * @param x                  The first node in the pair.
     * @param y                  The second node in the pair.
     * @param test               The independence test object to use for checking independence.
     * @param maxLength          The maximum length of the paths to consider. If set to a negative value or a value
     *                           greater than the number of nodes minus one, it is adjusted accordingly.
     * @param depth              The maximum depth of the final sepset. If set to a negative value, no limit is
     *                           applied.
     * @param allowSelectionBias A boolean flag indicating whether to allow selection bias.
     * @return The sepset if independence holds, otherwise null.
     */
    public static Set<Node> getSepsetPathBlockingFromSideOfX(Graph mpdag, Node x, Node y, IndependenceTest test,
                                                             int maxLength, int depth, boolean allowSelectionBias) {
        Set<Node> blacklist = new HashSet<>();

        int maxLength1 = maxLength;
        if (maxLength1 < 0 || maxLength1 > mpdag.getNumNodes() - 1) {
            maxLength1 = mpdag.getNumNodes() - 1;
        }


        Pair<Set<Node>, Set<Node>> ret = bfsAllPathsOutOfX(mpdag, blacklist, maxLength1, x, y, allowSelectionBias);

        Set<Node> conditioningSet = ret.getLeft();
        Set<Node> couldBeColliders = ret.getRight();

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

                return sepset;
            }
        }

        if (test.checkIndependence(x, y, new HashSet<>()).isIndependent()) {
            return new HashSet<>();
        }

        return null;
    }
}
