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
    public static Set<Node> findSepsetSubsetOfAdjxOrAdjy(Graph graph, Node x, Node y, Set<Node> containing, IndependenceTest test, int depth) {
        List<Node> adjx = graph.getAdjacentNodes(x);
        List<Node> adjy = graph.getAdjacentNodes(y);
        adjx.remove(y);
        adjy.remove(x);

        adjx.removeIf(node -> node.getNodeType() == NodeType.LATENT);
        adjy.removeIf(node -> node.getNodeType() == NodeType.LATENT);

        List<List<Integer>> choices = getChoices(adjx, depth);

        // Parallelize processing for adjx
        Set<Node> sepset = choices.stream()
                .map(choice -> combination(choice, adjx)) // Generate combinations in parallel
                .filter(subset -> subset.containsAll(containing)) // Filter combinations that don't contain 'containing'
                .filter(subset -> {
                    try {
                        return test.checkIndependence(x, y, subset).isIndependent();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }) // Further filter by separating sets
                .findFirst()
                .orElse(null);

        if (sepset != null) {
            return sepset;
        }

        // Parallelize processing for adjy
        choices = getChoices(adjy, depth);

        sepset = choices.parallelStream()
                .map(choice -> combination(choice, adjy)) // Generate combinations in parallel
                .filter(subset -> subset.containsAll(containing)) // Filter combinations that don't contain 'containing'
                .filter(subset -> {
                    try {
                        return test.checkIndependence(x, y, subset).isIndependent();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }) // Further filter by separating sets
                .findFirst() // Return the first matching subset
                .orElse(null);

        return sepset;
    }

    /**
     * Identifies a separating set (sepset) containing a given subset of nodes between two nodes x and y in a graph
     * using a greedy approach and subsets of (adj(x) U adu(y)) \ {x, y}. The method applies constraints such as
     * independence testing, specified node ordering, and optional filtering of certain node types.
     * <p>
     * This method mainly focuses on finding a feasible separating set under the given constraints by iterating through
     * node combinations from the union of adjacent nodes of x and y in the graph.
     *
     * @param graph      The graph in which the nodes and their adjacency relationships are defined.
     * @param cpdag      The CDPDAG.
     * @param x          The first node for which the sepset is being determined.
     * @param y          The second node for which the sepset is being determined.
     * @param containing A specified subset of nodes that the resulting sepset must contain.
     * @param test       The independence test to verify conditional independence between nodes.
     * @param depth      The maximum allowable size for subsets to consider during the search for the sepset.
     * @return A set of nodes representing the sepset containing the given subset, or null if no such set is found.
     */
    public static Set<Node> getSepsetContainingGreedySubsetMb(Graph graph, Graph cpdag, Node x, Node y, Set<Node> containing, IndependenceTest test, int depth) {
        List<Node> mbx = new ArrayList<>(cpdag.paths().markovBlanket(x));

        if (("A".equals(x.getName()) && "E".equals(y.getName()))
            || ("E".equals(x.getName()) && "A".equals(y.getName()))) {
            System.out.println("mb(x) = " + graph.paths().markovBlanket(x));
            System.out.println("mb(y) = " + graph.paths().markovBlanket(y));

            MsepTest msepTest = new MsepTest(graph);

            Set<Node> bd = new HashSet<>();
            bd.add(graph.getNode("B"));
            bd.add(graph.getNode("D"));

            System.out.println("dsep(x, y | BD" + msepTest.checkIndependence(x, y, bd).isIndependent());
        }

        mbx.remove(y);
        List<Node> mby = new ArrayList<>(cpdag.paths().markovBlanket(y));
        mby.remove(x);

        List<Node> mb = mbx.size() < mby.size() ? mbx : mby;

        mb.removeIf(node -> node.getNodeType() == NodeType.LATENT);
        mb.removeIf(node -> node.getNodeType() == NodeType.LATENT);
        List<List<Integer>> choices = getChoices(mb, depth);

        // Parallelize processing for adjx
        Set<Node> sepset = choices.parallelStream()
                .map(choice -> combination(choice, mb)) // Generate combinations in parallel
                .filter(subset -> subset.containsAll(containing)) // Filter combinations that don't contain 'containing'
                .filter(subset -> {
                    try {
                        return test.checkIndependence(x, y, subset).isIndependent();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                })
                .findFirst() // Return the first matching subset
                .orElse(null);

        if (sepset != null) {
            return sepset;
        }

        return sepset;
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
     * @throws InterruptedException if any
     */
    public static Set<Node> getSepsetContainingMaxPHybrid(Graph graph, Node x, Node y, Set<Node> containing, IndependenceTest test, int depth) throws InterruptedException {
        List<Node> adjx = graph.getAdjacentNodes(x);
        List<Node> adjy = graph.getAdjacentNodes(y);
        adjx.remove(y);
        adjy.remove(x);

//        if (containing != null) {
//            adjx.removeAll(containing);
//            adjy.removeAll(containing);
//        }

        // Remove latent nodes.
        adjx.removeIf(node -> node.getNodeType() == NodeType.LATENT);
        adjy.removeIf(node -> node.getNodeType() == NodeType.LATENT);

        // Find the best separating set among adjx
        Set<Node> bestSepset = findMaxPSepset(x, y, adjx, test, containing, depth);
        if (bestSepset != null) {
            return bestSepset;
        }

        // Find the best separating set among adjy
        return findMaxPSepset(x, y, adjy, test, containing, depth);
    }

    private static Set<Node> findMaxPSepset(Node x, Node y, List<Node> adj, IndependenceTest test, Set<Node> containing, int depth) throws InterruptedException {
        List<List<Integer>> choices = getChoices(adj, depth);
        double maxPValue = -1.0;
        List<Integer> bestChoice = null;

        for (List<Integer> choice : choices) {
            Set<Node> subset = combination(choice, adj);
            if (containing != null && !subset.containsAll(containing)) continue;

            // Check if the subset is a separating set
            if (test.checkIndependence(x, y, subset).isIndependent()) {
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
     * @param graph the graph representing the network
     * @param x     the first node
     * @param y     the second node
     * @param test  the independence test to use for calculating the p-value
     * @param depth the depth of the search for the sepset
     * @return the sepset containing the minimum p-value, or null if no sepset is found
     * @throws InterruptedException if any
     */
    public static Set<Node> getSepsetContainingMinPHybrid(Graph graph, Node x, Node y, IndependenceTest test, int depth) throws InterruptedException {
        List<Node> adjx = graph.getAdjacentNodes(x);
        List<Node> adjy = graph.getAdjacentNodes(y);
        adjx.remove(y);
        adjy.remove(x);

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

    private static Set<Node> findMinPSepset(Node x, Node y, List<Node> adj, IndependenceTest test, int depth) throws InterruptedException {
        List<List<Integer>> choices = getChoices(adj, depth);
        double minPValue = Double.MAX_VALUE;
        List<Integer> bestChoice = null;

        for (List<Integer> choice : choices) {
            Set<Node> subset = combination(choice, adj);

            // Check if the subset is a separating set
            if (test.checkIndependence(x, y, subset).isIndependent()) {
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

    /**
     * Returns a set of nodes that are the parents of the given node in the graph.
     *
     * @param graph the graph containing the nodes and edges
     * @param x     the node whose parent nodes are to be found
     * @return a set of nodes that are the parents of the given node
     */
    public static Set<Node> blockPathsLocalMarkov(Graph graph, Node x) {
        return new HashSet<>(graph.getParents(x));
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

    private static double getPValue(Node x, Node y, Set<Node> combination, IndependenceTest test) {
        double pValue;
        try {
            pValue = test.checkIndependence(x, y, combination).getPValue();
        } catch (Exception e) {
            TetradLogger.getInstance().log("Error in getPValue: " + e.getMessage());
            return 0.0;
        }
        return Double.isNaN(pValue) ? 1.0 : pValue;
    }

    /**
     * Finds a smallest subset S of <code>blocking</code> that renders two nodes x and y conditionally d-separated
     * conditional on S in the given graph. (There may be more than one smallest subset; only one is returned.)
     *
     * @param x          the first node.
     * @param y          the second node.
     * @param blocking   the initial set of blocking nodes; this may not be a sepset.
     * @param graph      the graph containing the nodes.
     * @param containing a set of nodes that must be contained in the sepset.
     * @param isPag      true if the graph is a PAG (Partial Ancestral Graph), false otherwise.
     * @return the smallest set of nodes that renders x and y conditionally independent.
     */
    public static Set<Node> getSmallestSubset(Node x, Node y, Set<Node> blocking, Set<Node> containing, Graph graph, boolean isPag) {
        List<Node> _cond = new ArrayList<>(blocking);

        Set<Node> newCond = null;

        SublistGenerator generator = new SublistGenerator(_cond.size(), -1);
        int[] choice;
        MsepTest test = new MsepTest(graph, isPag);

        while ((choice = generator.next()) != null) {
            Set<Node> sepset = new HashSet<>();

            for (int k : choice) {
                sepset.add(_cond.get(k));
            }

            if (!sepset.containsAll(containing)) {
                continue;
            }

            if (test.checkIndependence(x, y, sepset).isIndependent()) {
                newCond = sepset;
                break;
            }
        }

        return newCond;
    }

    /**
     * Identifies the set of nodes that form the Markov Blanket for a given node in a graph.
     *
     * @param x The node for which the Markov Blanket is to be identified.
     * @param G The graph containing the node and its relationships.
     * @return A set of nodes that form the Markov Blanket of the specified node.
     */
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
