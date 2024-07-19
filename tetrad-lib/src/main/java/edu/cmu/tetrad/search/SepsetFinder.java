package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.util.SublistGenerator;
import edu.cmu.tetrad.util.TetradLogger;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Function;

public class SepsetFinder {


    /**
     * Retrieves the sepset (a set of nodes) between two given nodes. The sepset is the minimal set of nodes that need
     * to be conditioned on in order to render two nodes conditionally independent.
     *
     * @param x    the first node
     * @param y    the second node
     * @param test
     * @return the sepset between the two nodes as a Set<Node>
     */
    public static Set<Node> getSepsetContainingRecursive(Graph graph, Node x, Node y, Set<Node> containing, IndependenceTest test) {
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
//                    path.remove(b);
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

//            z.remove(b);
//            path.remove(b);
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

    public static Set<Node> getSepsetContainingGreedy(Graph graph, Node x, Node y, Set<Node> containing, IndependenceTest test) {
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

        List<List<Integer>> choices = getChoices(adjx);
        List<Integer> sepset = choices.parallelStream().filter(_choice -> separates(x, y, combination(_choice, adjx), test)).findFirst().orElse(null);

        if (sepset != null) {
            return combination(sepset, adjx);
        }

        // Do the same for adjy.
        choices = getChoices(adjy);
        sepset = choices.parallelStream().filter(_choice -> separates(x, y, combination(_choice, adjy), test)).findFirst().orElse(null);

        if (sepset != null) {
            return combination(sepset, adjy);
        }

        return null;
    }

    private static @NotNull List<List<Integer>> getChoices(List<Node> adjx) {
        List<List<Integer>> choices = new ArrayList<>();

        SublistGenerator cg = new SublistGenerator(adjx.size(), adjx.size());
        int[] choice;

        while ((choice = cg.next()) != null) {
            choices.add(asList(choice));
        }
        return choices;
    }

    private static @NotNull List<Integer> asList(int[] choice) {
        List<Integer> integerList = new ArrayList<>();
        for (int i : choice) {
            integerList.add(i);
        }
        return integerList;
    }

    public static Set<Node> getSepsetContainingMaxP(Graph graph, Node x, Node y, Set<Node> containing, IndependenceTest test) {
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

        List<List<Integer>> choices = getChoices(adjx);
        Function<List<Integer>, Double> function = choice -> getPValue(x, y, combination(choice, adjx), test);

        // Find the object that maximizes the function in parallel
        List<Integer> maxObject = choices.parallelStream()
                .max(Comparator.comparing(function))
                .orElse(null);

        if (maxObject != null && getPValue(x, y, combination(maxObject, adjx), test) > test.getAlpha()) {
            return combination(maxObject, adjx);
        }

        // Do the same for adjy.
        choices = getChoices(adjx);
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

    public static Set<Node> getSepsetContainingMinP(Graph graph, Node x, Node y, Set<Node> containing, IndependenceTest test) {
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

        List<List<Integer>> choices = getChoices(adjx);
        Function<List<Integer>, Double> function = choice -> getPValue(x, y, combination(choice, adjx), test);

        // Find the object that maximizes the function in parallel
        List<Integer> minObject = choices.parallelStream()
                .min(Comparator.comparing(function))
                .orElse(null);

        if (minObject != null && getPValue(x, y, combination(minObject, adjx), test) > test.getAlpha()) {
            return combination(minObject, adjx);
        }

        // Do the same for adjy.
        choices = getChoices(adjx);
        function = choice -> getPValue(x, y, combination(choice, adjx), test);

        // Find the object that maximizes the function in parallel
        minObject = choices.parallelStream()
                .min(Comparator.comparing(function))
                .orElse(null);

        if (minObject != null && getPValue(x, y, combination(minObject, adjx), test) > test.getAlpha()) {
            return combination(minObject, adjx);
        }

        return null;
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
     * Searches for sets, by following paths from x to y in the given MPDAG, that could possibly block all paths from x
     * to y except for an edge from x to y itself. These possible sets are then tested for independence, and the first
     * set that is found to be independent is returned as the sepset.
     * <p>
     * This is the sepset finding method from LV-lite.
     *
     * @param x          the first node
     * @param y          the second node
     * @param mpdag      the MPDAG graph to analyze (can be a DAG or a CPDAG)
     * @param test       the independence test to use
     * @param maxLength  the maximum blocking length for paths, or -1 for no limit
     * @param depth      the maximum depth of the sepset, or -1 for no limit
     * @param printTrace whether to print trace information; false by default. This can be quite verbose, so it's
     *                   recommended to only use this for debugging.
     * @return the sepset of the endpoints for the given edge in the DAG graph based on the specified conditions, or
     * {@code null} if no sepset can be found.
     */
    public static Set<Node> getSepsetPathBlocking(Node x, Node y, Graph mpdag, IndependenceTest test, Map<Node, Set<Node>> ancestors,
                                                  int maxLength, int depth, boolean printTrace) {
        if (printTrace) {
            Edge e = mpdag.getEdge(x, y);
            TetradLogger.getInstance().log("\n\n### CHECKING x = " + x + " y = " + y + "edge = " + ((e != null) ? e : "null") + " ###\n\n");
        }

        // This is the set of all possible conditioning variables, though note below.
        Set<Node> noncolliders = new HashSet<>();

        // We are considering removing the edge x *-* y, so for length 2 paths, so we don't know whether
        // noncollider z2 in the GRaSP/BOSS DAG is a noncollider or a collider in the true DAG. We need to
        // check both scenarios.
        Set<Node> couldBeColliders = new HashSet<>();

        Set<List<Node>> paths;

        boolean _changed = true;

        while (_changed) {
            _changed = false;

            paths = mpdag.paths().allPaths(x, y, -1, maxLength, noncolliders, ancestors, false);

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
                        if (noncolliders.contains(z2)) {
                            blocked = true;

                            if (printTrace) {
                                TetradLogger.getInstance().log("This " + path + "--is already blocked by " + z2);
                            }

                            break;
                        }

                        noncolliders.add(z2);
                        blocked = true;
                        _changed = true;

                        if (printTrace) {
                            TetradLogger.getInstance().log("Blocking " + path + " with noncollider " + z2);
                        }

                        if (mpdag.isAdjacentTo(z1, z3)) {
                            couldBeColliders.add(z2);

                            if (printTrace) {
                                TetradLogger.getInstance().log("Noting that " + z2 + " could be a collider on " + path);
                            }
                        }

                        if (depth != -1 && noncolliders.size() > depth) {
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
            TetradLogger.getInstance().log("noncolliders: " + noncolliders);
            TetradLogger.getInstance().log("couldBeColliders: " + couldBeColliders);
        }

        // Now, for each conditioning set we identify, where the length-2 noncolliders are either included or not
        // in the set, we check independence greedily. Hopefully the number of options here is small.
        List<Node> couldBeCollidersList = new ArrayList<>(couldBeColliders);
        noncolliders.removeAll(couldBeColliders);

        SublistGenerator generator = new SublistGenerator(couldBeCollidersList.size(), couldBeCollidersList.size());
        int[] choice;

        while ((choice = generator.next()) != null) {
            Set<Node> sepset = new HashSet<>();

            for (int k : choice) {
                sepset.add(couldBeCollidersList.get(k));
            }

            sepset.addAll(noncolliders);

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
}
