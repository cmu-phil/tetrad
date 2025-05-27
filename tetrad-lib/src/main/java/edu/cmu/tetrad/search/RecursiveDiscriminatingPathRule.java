package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.utils.DiscriminatingPath;
import edu.cmu.tetrad.search.utils.EnsureMarkov;
import edu.cmu.tetrad.search.utils.FciOrient;
import edu.cmu.tetrad.util.SublistGenerator;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Implements the R4 Discriminating Path rule in the final FCI orientation rules (Zhang 2008) using the
 * block_paths_recursively method as a optimization.
 *
 * @author josephramsey
 */
public class RecursiveDiscriminatingPathRule {

    /**
     * Creates a new instance of the RecursiveDiscriminatingPathRule class. This constructor is private to ensure that
     * the class cannot be instantiated directly. The class is designed to provide static methods for evaluating
     * conditional independence and finding separating sets in graph structures through recursive analysis of
     * discriminating paths.
     */
    private RecursiveDiscriminatingPathRule() {

    }

//    /**
//     * Checks for conditional independence between two nodes {@code x} and {@code y} in the context of a given blocking
//     * set, recursively analyzing potential discriminating paths. This method evaluates combinations of nodes in the
//     * blocking set that satisfy independence conditions.
//     *
//     * @param test               The independence test object used to evaluate conditional independence.
//     * @param x                  The first node for which independence is being checked.
//     * @param y                  The second node for which independence is being checked.
//     * @param blocking           The initial set of nodes considered as the blocking set for independence tests.
//     * @param vNodes             The subset of nodes identifying possible colliders in the analysis.
//     * @param discriminatingPath The discriminating path data structure containing relevant path information.
//     * @param ensureMarkovHelper A helper object for ensuring adherence to the Markov property during the checks.
//     * @return {@code true} if the nodes {@code x} and {@code y} are conditionally independent given the blocking set;
//     * otherwise, {@code false}.
//     * @throws InterruptedException If the process is interrupted during execution.
//     */
//    public static Set<Node> checkIndependenceRecursive(IndependenceTest test, Node x, Node y, Set<Node> blocking, Set<Node> vNodes, DiscriminatingPath discriminatingPath, EnsureMarkov ensureMarkovHelper) throws InterruptedException {
//
//        List<Node> vs = new ArrayList<>();
//        List<Node> nonVs = new ArrayList<>();
//
//        for (Node v : blocking) {
//            if (vNodes.contains(v)) {
//                vs.add(v);
//            } else {
//                nonVs.add(v);
//            }
//        }
//
//        Node v = discriminatingPath.getV();
//        vs.remove(v);
//
//        SublistGenerator generator = new SublistGenerator(vs.size(), vs.size());
//        int[] choice;
//
//        while ((choice = generator.next()) != null) {
//            Set<Node> newBlocking = GraphUtils.asSet(choice, vs);
//            newBlocking.add(v);
//            newBlocking.addAll(nonVs);
//
//            // You didn't condition on any colliders. V is in the set. So V is a noncollider.
//            boolean independent = ensureMarkovHelper != null ? ensureMarkovHelper.markovIndependence(x, y, newBlocking) : test.checkIndependence(x, y, newBlocking).isIndependent();
//
//            if (independent) {
//                return newBlocking;
//            }
//        }
//
//        return blocking;
//    }


    /**
     * Finds the set of nodes (separator set) for the Recursive Discriminating Path rule in a graph. This method uses a
     * recursive approach to evaluate possible discriminating paths between two nodes {@code x} and {@code y} in the
     * provided graph {@code pag}.
     *
     * @param test                  The independence test object used to check for conditional independence between
     *                              nodes.
     * @param pag                   The graph structure, typically a partial ancestral graph (PAG), being analyzed.
     * @param x                     The first target node in the analysis.
     * @param y                     The second target node in the analysis.
     * @param fciOrient             An orientation helper object used to apply FCI rules to edges in the graph.
     * @param maxBlockingPathLength The maximum allowable length of a blocking path for the analysis.
     * @param maxDdpPathLength      The maximum allowable discriminating path length considered for the analysis.
     * @param ensureMarkovHelper    A helper object for additional Markov property checks during the independence
     *                              tests.
     * @param depth                 The maximum subset depth allowed during subset evaluations; a value of -1 allows all
     *                              subsets.
     * @return A set of nodes that constitutes the separating set (sepset) between {@code x} and {@code y}, or
     * {@code null} if no such set exists.
     */
    public static Set<Node> findDdpSepsetRecursive(IndependenceTest test, Graph pag, Node x, Node y, FciOrient fciOrient,
                                                   int maxBlockingPathLength, int maxDdpPathLength, EnsureMarkov ensureMarkovHelper, int depth)
            throws InterruptedException {

        // Get the V nodes--these need to be blocked in every combination, as we don't know which of these are colliders
        // on their respective discriminating paths.
        List<Node> vNodes = getVNodes(pag, x, y, maxDdpPathLength);

        // Get the common neighbors, some subset of which are common childeren (hence length-2 collider paths that
        // must not be conditioned on in order to block them.
        List<Node> common = getCommonNeighbors(pag, x, y);

        // (B) For each subset of "common," check independence
        SublistGenerator gen1 = new SublistGenerator(common.size(), common.size());
        int[] choice2;

        while ((choice2 = gen1.next()) != null) {
            Set<Node> c = GraphUtils.asSet(choice2, common);
            Set<Node> perhapsNotFollowed = new HashSet<>(vNodes);
            perhapsNotFollowed.addAll(c);

            // Generate all subsets from vNodes
            SublistGenerator gen = new SublistGenerator(perhapsNotFollowed.size(), perhapsNotFollowed.size());
            List<int[]> allChoices = new ArrayList<>();

            int[] choice;
            while ((choice = gen.next()) != null) {
                allChoices.add(choice.clone());
            }

            List<Node> _perhapsNotFollowed = new ArrayList<>(perhapsNotFollowed);

            // 6) Build a Callable for each subset. We throw an exception if no solution is found.
            for (int[] indices : allChoices) {

                // Convert indices -> actual nodes
                Set<Node> vNodesNotFollowed = GraphUtils.asSet(indices, _perhapsNotFollowed);

                // (A) blockPathsRecursively
                Pair<Set<Node>, Boolean> b = RecursiveBlocking.blockPathsRecursively(pag, x, y, Set.of(), vNodesNotFollowed, maxBlockingPathLength);

                Set<Node> blocking = b.getLeft();

                for (Node f : vNodes) {
                    if (!vNodesNotFollowed.contains(f)) {
                        blocking.add(f);
                    }
                }

//                System.out.println("Blocking set for x = " + x + " y = " + y + " not followed = " + vNodesNotFollowed + " = " + blocking);

                // b minus c
                Set<Node> testSet = new HashSet<>(blocking);
                testSet.removeAll(c);

                // Check independence
                boolean independent;
                if (ensureMarkovHelper != null) {
                    independent = ensureMarkovHelper.markovIndependence(x, y, testSet);
                } else {
                    independent = test.checkIndependence(x, y, testSet).isIndependent();
                }

                if (independent) {
                    return testSet;
                }
            }
        }

        return null;
    }

    private static @NotNull List<Node> getCommonNeighbors(Graph pag, Node x, Node y) {
        List<Node> common = new ArrayList<>(pag.getAdjacentNodes(x));
        common.retainAll(pag.getAdjacentNodes(y));
        return common;
    }

    private static @NotNull List<Node> getVNodes(Graph pag, Node x, Node y, int maxDdpPathLength) {
        // 2) List possible DiscriminatingPaths
        Set<DiscriminatingPath> discriminatingPaths = FciOrient.listDiscriminatingPaths(pag, maxDdpPathLength, false);

        // 3) Figure out which nodes might be "notFollowed"
        Set<DiscriminatingPath> relevantPaths = new HashSet<>();
        for (DiscriminatingPath path : discriminatingPaths) {
            if ((path.getX() == x && path.getY() == y) || (path.getX() == y && path.getY() == x)) {
                relevantPaths.add(path);
            }
        }

        Set<Node> vNodes = new HashSet<>();
        for (DiscriminatingPath path : relevantPaths) {
            if (pag.getEndpoint(path.getY(), path.getV()) == Endpoint.CIRCLE) {
                vNodes.add(path.getV());
            }

        }
        List<Node> _vNodes = new ArrayList<>(vNodes);
        return _vNodes;
    }
}
