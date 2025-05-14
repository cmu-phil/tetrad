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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;

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

    /**
     * Checks for conditional independence between two nodes {@code x} and {@code y} in the context of a given blocking
     * set, recursively analyzing potential discriminating paths. This method evaluates combinations of nodes in the
     * blocking set that satisfy independence conditions.
     *
     * @param test               The independence test object used to evaluate conditional independence.
     * @param x                  The first node for which independence is being checked.
     * @param y                  The second node for which independence is being checked.
     * @param blocking           The initial set of nodes considered as the blocking set for independence tests.
     * @param vNodes             The subset of nodes identifying possible colliders in the analysis.
     * @param discriminatingPath The discriminating path data structure containing relevant path information.
     * @param ensureMarkovHelper A helper object for ensuring adherence to the Markov property during the checks.
     * @return {@code true} if the nodes {@code x} and {@code y} are conditionally independent given the blocking set;
     * otherwise, {@code false}.
     * @throws InterruptedException If the process is interrupted during execution.
     */
    public static boolean checkIndependenceRecursive(IndependenceTest test, Node x, Node y, Set<Node> blocking, Set<Node> vNodes,
                                                     DiscriminatingPath discriminatingPath, EnsureMarkov ensureMarkovHelper) throws InterruptedException {

        List<Node> vs = new ArrayList<>();
        List<Node> nonVs = new ArrayList<>();

        for (Node v : blocking) {
            if (vNodes.contains(v)) {
                vs.add(v);
            } else {
                nonVs.add(v);
            }
        }

        Node v = discriminatingPath.getV();
        vs.remove(v);

        SublistGenerator generator = new SublistGenerator(vs.size(), vs.size());
        int[] choice;

        while ((choice = generator.next()) != null) {
            Set<Node> newBlocking = GraphUtils.asSet(choice, vs);
            newBlocking.add(v);
            newBlocking.addAll(nonVs);

            // You didn't condition on any colliders. V is in the set. So V is a noncollider.
            boolean independent = ensureMarkovHelper != null ? ensureMarkovHelper.markovIndependence(x, y, newBlocking) :
                    test.checkIndependence(x, y, newBlocking).isIndependent();

            if (independent) {
                return true;
            }
        }

        return false;
    }


    /**
     * Finds the set of nodes (separator set) for the Recursive Discriminating Path rule in a graph. This method uses a
     * recursive approach to evaluate possible discriminating paths between two nodes {@code x} and {@code y} in the
     * provided graph {@code pag}. It involves complex independence checks and is computationally intensive.
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
    public static Set<Node> findDdpSepsetRecursive(
            IndependenceTest test, Graph pag, Node x, Node y, FciOrient fciOrient,
            int maxBlockingPathLength, int maxDdpPathLength, EnsureMarkov ensureMarkovHelper, int depth) {

//        // 1) Preliminary orientation steps
//        fciOrient.setDoR4(false);
//        fciOrient.setCompleteRuleSetUsed(false);
//        fciOrient.finalOrientation(pag);
//        fciOrient.setDoR4(true);

        // 2) List possible "DiscriminatingPath" objects
        Set<DiscriminatingPath> discriminatingPaths =
                FciOrient.listDiscriminatingPaths(pag, maxDdpPathLength, false);

        // 3) Figure out which nodes might be "notFollowed"
        Set<DiscriminatingPath> relevantPaths = new HashSet<>();
        for (DiscriminatingPath path : discriminatingPaths) {
            if ((path.getX() == x && path.getY() == y) || (path.getX() == y && path.getY() == x)) {
                relevantPaths.add(path);
            }
        }

        Set<Node> perhapsNotFollowed = new HashSet<>();
        for (DiscriminatingPath path : relevantPaths) {
            if (pag.getEndpoint(path.getY(), path.getV()) == Endpoint.CIRCLE) {
                perhapsNotFollowed.add(path.getV());
            }
        }
        List<Node> _perhapsNotFollowed = new ArrayList<>(perhapsNotFollowed);

        // 4) Possibly limit subset size by "depth".
        int _depth = (depth == -1) ? _perhapsNotFollowed.size() : depth;
        _depth = Math.min(_depth, _perhapsNotFollowed.size());

        // Generate all subsets from _perhapsNotFollowed
        SublistGenerator gen = new SublistGenerator(_perhapsNotFollowed.size(), _depth);
        List<int[]> allChoices = new ArrayList<>();
        int[] choice;
        while ((choice = gen.next()) != null) {
            allChoices.add(choice.clone());
        }

        // 5) Build "common" neighbors
        List<Node> common = new ArrayList<>(pag.getAdjacentNodes(x));
        common.retainAll(pag.getAdjacentNodes(y));
        int _depth2 = (depth == -1) ? common.size() : depth;
        _depth2 = Math.min(_depth2, common.size());

        int __depth2 = _depth2;

        // 6) Build a Callable for each subset. We throw an exception if no solution is found.
        List<Callable<Set<Node>>> tasks = new ArrayList<>();
        for (int[] indices : allChoices) {
            tasks.add(() -> {
                // Check for interruption at the start (optional)
                if (Thread.currentThread().isInterrupted()) {
                    throw new NoSolutionFoundException("Thread interrupted before start");
                }

                // Convert indices -> actual nodes
                Set<Node> notFollowedSet = GraphUtils.asSet(indices, _perhapsNotFollowed);

                // (A) blockPathsRecursively
                Pair<Set<Node>, Boolean> b = RecursiveBlocking.blockPathsRecursively(
                        pag, x, y, Set.of(), notFollowedSet, maxBlockingPathLength
                );

//                if (!b.getRight()) {
//                    throw new IllegalArgumentException("Expecting all paths blocked.");
//                }

                // (B) For each subset of "common," check independence
                SublistGenerator gen2 = new SublistGenerator(common.size(), __depth2);
                int[] choice2;

                outerLoop:
                while ((choice2 = gen2.next()) != null) {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new NoSolutionFoundException("Thread interrupted in loop");
                    }

                    Set<Node> c = GraphUtils.asSet(choice2, common);

                    // Skip if there's a definite collider x->node<-y
                    for (Node node : c) {
                        if (pag.isDefCollider(x, node, y)) {
                            continue outerLoop;
                        }
                    }

                    // b minus c
                    Set<Node> testSet = new HashSet<>(b.getLeft());
                    testSet.removeAll(c);

                    // Check independence
                    boolean independent;
                    if (ensureMarkovHelper != null) {
                        independent = ensureMarkovHelper.markovIndependence(x, y, testSet);
                    } else {
                        independent = test.checkIndependence(x, y, testSet).isIndependent();
                    }

                    if (independent) {
                        // Found a valid solution => return it
                        return testSet;
                    }
                }
                // If we never find a solution in this task => throw
                throw new NoSolutionFoundException("No solution in this subset");
            });
        }

        ForkJoinPool forkJoinPool = new ForkJoinPool(Runtime.getRuntime().availableProcessors() / 2);

        try {
            // 8) Use invokeAny => returns as soon as one task completes successfully
            // i.e., doesn't throw an exception
            Set<Node> result = forkJoinPool.invokeAny(tasks);

            // 9) If we get here, 'result' is from the first successfully completed task
            // We can shut down forcibly, to kill all other tasks
            forkJoinPool.shutdownNow();
            return result;

        } catch (InterruptedException e) {
            // If the main thread is interrupted
            forkJoinPool.shutdownNow();
            throw new RuntimeException(e);

        } catch (ExecutionException e) {
            // This means *all* tasks either threw or never completed
            // Typically indicates no solution was found (or some other error).
            forkJoinPool.shutdownNow();
            // You might choose to return null or rethrow
            return null;

        } finally {
            if (!forkJoinPool.isShutdown()) {
                forkJoinPool.shutdownNow();
            }
        }
    }

    /**
     * Simple custom exception to indicate "I couldn't find a solution."
     */
    public static class NoSolutionFoundException extends Exception {

        /**
         * Constructs a NoSolutionFoundException with the specified detail message.
         *
         * @param message the detail message providing information about the exception
         */
        public NoSolutionFoundException(String message) {
            super(message);
        }
    }
}
