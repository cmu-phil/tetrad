/// ////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.utils.DiscriminatingPath;
import edu.cmu.tetrad.search.utils.FciOrient;
import edu.cmu.tetrad.search.utils.IndependenceCheckCounter;
import edu.cmu.tetrad.search.utils.PreserveMarkov;
import edu.cmu.tetrad.util.SublistGenerator;
import edu.cmu.tetrad.util.TetradLogger;
import org.jetbrains.annotations.NotNull;

import java.util.*;

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
     * Finds the separating set (sepset) recursively using discriminating paths in a partially oriented graph (PAG).
     * This method evaluates conditional independence relationships and identifies separating sets
     * based on the Recursive Discriminating Path algorithm.
     *
     * @param test                 The independence test used to evaluate conditional independence relationships.
     * @param pag                  The partially oriented graph (PAG) being analyzed.
     * @param x                    The first node for which the sepset is determined.
     * @param y                    The second node for which the sepset is determined.
     * @param recursiveDepth       The maximum allowed depth for recursive search in discriminating paths.
     * @param maxDdpPathLength     The maximum length of discriminating paths to consider.
     * @param depth                The current recursion depth of the algorithm.
     * @param preserveMarkovHelper A helper object to ensure the Markov property is preserved during execution.
     * @param timeout              The maximum time allowed for the method to execute in ms.
     * @return A set of nodes representing the separating set (sepset) identified between nodes x and y.
     * @throws InterruptedException If the thread is interrupted while executing the method.
     */
    public static Set<Node> findDdpSepsetRecursive(IndependenceTest test, Graph pag, Node x, Node y,
                                                   int recursiveDepth, int maxDdpPathLength,
                                                   int depth, PreserveMarkov preserveMarkovHelper, long timeout)
            throws InterruptedException {
        return findDdpSepsetRecursive(test, pag, x, y, recursiveDepth, maxDdpPathLength,
                depth, preserveMarkovHelper, null, timeout);
    }

    /**
     * Recursively finds the separating set (sepset) between two non-adjacent nodes in a partially oriented
     * graph (PAG) using the Recursive Discriminating Path algorithm. This method evaluates conditional
     * independence relationships and considers subsets of possible nodes on discriminating paths to identify
     * the sepset.
     *
     * @param test                 The independence test used for evaluating conditional independence.
     * @param pag                  The partially oriented graph (PAG) being analyzed.
     * @param x                    The first node for which the sepset is determined.
     * @param y                    The second node for which the sepset is determined.
     * @param recursiveDepth       The maximum depth for recursive search in discriminating paths.
     * @param maxDdpPathLength     The maximum length of discriminating paths to consider.
     * @param depth                The current recursion depth of the algorithm.
     * @param preserveMarkovHelper A helper object to ensure the Markov property is preserved during execution.
     * @param counter              A counter to track the number of independence checks performed.
     * @return A set of nodes representing the separating set (sepset) identified
     * between nodes x and y, or {@code null} if no sepset is found.
     * @throws InterruptedException If the thread is interrupted during execution.
     */
    public static Set<Node> findDdpSepsetRecursive(IndependenceTest test, Graph pag, Node x, Node y,
                                                   int recursiveDepth, int maxDdpPathLength,
                                                   int depth, PreserveMarkov preserveMarkovHelper,
                                                   IndependenceCheckCounter counter)
            throws InterruptedException {
        return findDdpSepsetRecursive(test, pag, x, y, recursiveDepth, maxDdpPathLength, depth, preserveMarkovHelper, counter, Long.MAX_VALUE);
    }

    /**
     * Finds a separating set (sepset) between two non-adjacent nodes in a given graph using
     * recursive blocking strategies for discriminating paths. This method is primarily used
     * in causal discovery algorithms to identify sets of nodes that render the two input
     * nodes conditionally independent under certain assumptions.
     *
     * @param test                 The independence test used to evaluate conditional independence
     *                             between nodes given a conditioning set.
     * @param pag                  The partially oriented acyclic graph (PAG) that represents the causal
     *                             structure being analyzed.
     * @param x                    The first node involved in the conditional independence relationship.
     * @param y                    The second node involved in the conditional independence relationship.
     * @param recursiveDepth       The maximum depth for recursive blocking during the path analysis.
     * @param maxDdpPathLength     The maximum length of discriminating paths to consider.
     * @param depth                The current depth of recursion.
     * @param preserveMarkovHelper A helper object used to verify Markov equivalence properties
     *                             and ensure consistent conditional independence checks (optional).
     * @param counter              A counter object used to track the number of independence tests or operations
     *                             performed (optional).
     * @param timeout              The maximum amount of time (in milliseconds) allowed for the method to execute
     *                             before terminating. A value of -1 indicates no timeout.
     * @return A set of nodes representing the separating set (sepset) that renders {@code x} and
     * {@code y} conditionally independent, or {@code null} if no such set is found.
     * @throws InterruptedException     If the execution is interrupted while running.
     * @throws IllegalArgumentException If the provided nodes {@code x} and {@code y} are adjacent
     *                                  in the graph.
     */
    public static Set<Node> findDdpSepsetRecursive(IndependenceTest test, Graph pag, Node x, Node y,
                                                   int recursiveDepth, int maxDdpPathLength,
                                                   int depth, PreserveMarkov preserveMarkovHelper,
                                                   IndependenceCheckCounter counter, long timeout)
            throws InterruptedException {
        return findDdpSepsetRecursive(test, pag, x, y, recursiveDepth, maxDdpPathLength, depth,
                preserveMarkovHelper, counter, timeout, false);
    }

    /**
     * As the other overloads, with control over how the separating set is chosen when more than one candidate
     * blocking set renders x and y independent.
     * <p>
     * With {@code useMaxP} false (the default everywhere else) the FIRST passing candidate is returned, so the
     * result depends on the order in which {@link SublistGenerator} happens to enumerate the not-followed subsets.
     * With it true, every candidate is evaluated and the one with the STRONGEST evidence of independence is
     * returned, which removes that order dependence. The choice is not cosmetic: R4 reads the returned set twice,
     * once to check that it contains the collider path and once to decide (by whether it contains V) whether V is
     * a collider on the discriminating path, and the set is recorded in the caller's sepset map.
     * <p>
     * Two caveats. First, max-p forces the full enumeration on every call rather than exiting at the first hit;
     * the enumeration is over subsets of the ambiguous pool, so the cost is bounded by the same worst case the
     * greedy version already faces, but the average case is worse. Second, when a {@link PreserveMarkov} helper is
     * supplied the greedy rule is used regardless of this flag: that helper's accept path MUTATES its running
     * p-value bookkeeping, so evaluating every candidate would fold rejected candidates' updates into the state
     * that later calls depend on. That restriction can be lifted if PreserveMarkov grows a side-effect-free query.
     *
     * @param useMaxP Whether to return the candidate with the strongest evidence of independence rather than the
     *                first one found.
     * @return A separating set, or {@code null} if none was found.
     * @throws InterruptedException If the execution is interrupted while running.
     */
    public static Set<Node> findDdpSepsetRecursive(IndependenceTest test, Graph pag, Node x, Node y,
                                                   int recursiveDepth, int maxDdpPathLength,
                                                   int depth, PreserveMarkov preserveMarkovHelper,
                                                   IndependenceCheckCounter counter, long timeout,
                                                   boolean useMaxP)
            throws InterruptedException {

        if (pag.isAdjacentTo(x, y)) {
            throw new IllegalArgumentException("Nodes must be non-adjacent to each other.");
        }

        long deadlineMs = timeout >= 0L ? System.currentTimeMillis() + timeout : Long.MAX_VALUE;

//        List<Node> notFollowedSuperset = getVNodes(pag, x, y, maxDdpPathLength);

        RecursiveBlocking.BlockingResult result0 = RecursiveBlocking.blockPathsRecursively(
                pag, x, y, Set.of(), Set.of(), recursiveDepth, depth, -1,
                1, true, deadlineMs);

//        Set<Node> noFollowsBlocking = result0.blockingSet();
//        List<Node> notFollowedSuperset = new ArrayList<>();
//
//        // Remove any node from the notFollowedSuperset whose orientations are already completely
//        // determined. We don't need to consider both possibilities for these.
//        for (Node f : noFollowsBlocking) {
        Set<Node> noFollowsBlocking = result0.indeterminate() ? null : result0.blockingSet();

        // The unconstrained pass can legitimately fail to block (not-found, or timed out),
        // in which case there is no blocking set to mine for ambiguous nodes. Fall back to
        // the structural candidate pool instead of dereferencing null: the not-followed
        // enumeration is precisely what may rescue a pair the unconstrained pass cannot block.
        Collection<Node> ambiguousPool = (noFollowsBlocking != null)
                ? noFollowsBlocking
                : getVNodes(pag, x, y, maxDdpPathLength);

        List<Node> notFollowedSuperset = new ArrayList<>();

        for (Node f : ambiguousPool) {
            for (Node s : pag.getAdjacentNodes(f)) {
                if (pag.getEndpoint(s, f) == Endpoint.CIRCLE) {
                    notFollowedSuperset.add(f);
                    break;
                }
            }
        }

        // Try all subsets of notFollowedSuperset as the not-followed set, since we don't know
        // which are colliders on their discriminating paths.
        SublistGenerator gen = new SublistGenerator(notFollowedSuperset.size(), notFollowedSuperset.size());
        int[] choice;
        Set<Set<Node>> testSets = new HashSet<>();

        // Max-p state. The greedy rule returns at the first hit, so these stay unused unless useMaxP is on and
        // no PreserveMarkov helper is in play (see the javadoc for why that helper forces the greedy rule).
        boolean maxP = useMaxP && preserveMarkovHelper == null;
        Set<Node> bestSet = null;
        double bestStrength = Double.NEGATIVE_INFINITY;

        while ((choice = gen.next()) != null) {
            if (System.currentTimeMillis() > deadlineMs) {
                TetradLogger.getInstance().log("\tTimeout reached while searching for DDP sepset");
                break;
            }

            Set<Node> vNodesNotFollowed = GraphUtils.asSet(choice, notFollowedSuperset);

            RecursiveBlocking.BlockingResult result = RecursiveBlocking.blockPathsRecursively(
                    pag, x, y, Set.of(), vNodesNotFollowed, recursiveDepth, depth, -1,
                    1, true, deadlineMs);

            if (result.indeterminate()) {
                continue;
            }

            if (!result.found()) {
                continue;
            }

            // Only add back followed vNodes if the path analysis itself
            // determined they were needed (i.e., they appear in the blocking set).
            // Unconditionally adding them can condition on colliders, opening
            // paths and causing the Markov check to fail.
            Set<Node> blockingSet = result.blockingSet();
            Set<Node> testSet = new HashSet<>(blockingSet);

            if (testSets.contains(testSet)) {
                continue;
            }

            testSets.add(testSet);

            for (Node f : notFollowedSuperset) {
                if (!vNodesNotFollowed.contains(f) && blockingSet.contains(f)) {
                    testSet.add(f);
                }
            }

            boolean independent;
            if (preserveMarkovHelper != null) {
                if (counter != null) counter.increment("findDdpSepsetRecursive (markov)");
                independent = preserveMarkovHelper.markovIndependence(x, y, testSet);
            } else {
                if (counter != null) counter.increment("findDdpSepsetRecursive (test)");
                IndependenceResult independenceResult = test.checkIndependence(x, y, testSet);
                independent = independenceResult.isIndependent();
            }

            if (independent) {
                if (!maxP) {
                    return testSet;
                }

                // Rank by strength of independence, which is LARGER-is-stronger for a p-value test and
                // SMALLER-is-stronger for a score wrapped as a test; see IndependenceTest.isPValueAProbability.
                // Comparing raw reported values would select the weakest sepset whenever the test is score-based.
                IndependenceResult r = test.checkIndependence(x, y, testSet);
                double strength = test.isPValueAProbability() ? r.getPValue() : -r.getScore();

                if (strength > bestStrength) {
                    bestStrength = strength;
                    bestSet = testSet;
                }
            }
        }

        // Under max-p this is the strongest candidate seen; note that if the deadline broke the loop early it is
        // the strongest among those reached, which is still a better answer than the null the greedy rule
        // returns on timeout.
        if (maxP && bestSet != null) {
            return bestSet;
        }

//        TetradLogger.getInstance().log("\tRecursive DDP: No sepset found for " + x + " and " + y);
        return null;
    }

    private static @NotNull List<Node> getVNodes(Graph pag, Node x, Node y, int maxDdpPathLength) {
        // 2) List possible DiscriminatingPaths
        Set<DiscriminatingPath> discriminatingPaths = FciOrient.listDiscriminatingPaths(pag, maxDdpPathLength, true);

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

        return new ArrayList<>(vNodes);
    }

}

