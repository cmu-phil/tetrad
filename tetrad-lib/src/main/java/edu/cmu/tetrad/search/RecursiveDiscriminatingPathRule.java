package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.search.utils.*;
import edu.cmu.tetrad.util.SublistGenerator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;

public class RecursiveDiscriminatingPathRule {
    public static Set<Node> findDdpSepsetRecursive(
            IndependenceTest test, Graph pag, Node x, Node y, FciOrient fciOrient,
            int maxBlockingPathLength, int maxDdpPathLength, EnsureMarkov ensureMarkovHelper, int depth) {
        // 1) Preliminary orientation steps
        fciOrient.setDoR4(false);
        fciOrient.finalOrientation(pag);
        fciOrient.setDoR4(true);

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
                Set<Node> b = SepsetFinder.blockPathsRecursively(
                        pag, x, y, Set.of(), notFollowedSet, maxBlockingPathLength
                );

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
                    Set<Node> testSet = new HashSet<>(b);
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
     * Provides a special configuration for creating an instance of FciOrientDataExaminationStrategy.
     *
     * @param test      the IndependenceTest object used by the strategy
     * @param knowledge the Knowledge object used by the strategy
     * @param verbose   boolean indicating whether to provide verbose output
     * @return a configured FciOrientDataExaminationStrategy object
     * @throws IllegalArgumentException if test or knowledge is null
     */
    public static R0R4Strategy specialConfiguration(IndependenceTest test, Knowledge knowledge, boolean verbose) {
        if (test == null) {
            throw new IllegalArgumentException("Test is null.");
        }

        if (knowledge == null) {
            throw new IllegalArgumentException("Knowledge is null.");
        }

        if (test instanceof MsepTest) {
            R0R4Strategy r0R4Strategy = defaultConfiguration(((MsepTest) test).getGraph(), knowledge);
            R0R4StrategyTestBased _r0R4Strategy = (R0R4StrategyTestBased) r0R4Strategy;
            _r0R4Strategy.setVerbose(verbose);
            return _r0R4Strategy;
        } else {
            R0R4StrategyTestBased strategy = new R0R4StrategyTestBased(test);
            strategy.setKnowledge(knowledge);
            strategy.setVerbose(verbose);
            return strategy;
        }
    }

    /**
     * Returns a default configuration of the FciOrientDataExaminationStrategy object.
     *
     * @param dag       the graph representation
     * @param knowledge the Knowledge object used by the strategy
     * @return a default configured FciOrientDataExaminationStrategy object
     */
    public static R0R4Strategy defaultConfiguration(Graph dag, Knowledge knowledge) {
        return defaultConfiguration(new MsepTest(dag), knowledge);
    }

    /**
     * Returns a default configuration of the FciOrientDataExaminationStrategy object.
     *
     * @param test      the IndependenceTest object used by the strategy
     * @param knowledge the Knowledge object used by the strategy
     * @return a configured FciOrientDataExaminationStrategy object
     * @throws IllegalArgumentException if test or knowledge is null
     */
    public static R0R4Strategy defaultConfiguration(IndependenceTest test, Knowledge knowledge) {
        R0R4StrategyTestBased strategy = new R0R4StrategyTestBased(test);
        strategy.setKnowledge(knowledge);
        return strategy;
    }

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

    /** Simple custom exception to indicate "I couldn't find a solution." */
    public static class NoSolutionFoundException extends Exception {
        public NoSolutionFoundException(String message) {
            super(message);
        }
    }
}
