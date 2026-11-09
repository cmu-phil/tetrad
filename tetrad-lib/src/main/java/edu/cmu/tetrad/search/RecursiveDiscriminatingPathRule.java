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
 * Finds separating sets for the discriminating path rule (R4 of the final FCI orientation rules, Zhang 2008) by
 * recursive path blocking rather than by searching over subsets of adjacents.
 * <p>
 * R4 needs, for a discriminating path from x to y through v, a set that renders x and y independent; whether that set
 * contains v then decides v's orientation. The difficulty is that some nodes on the candidate paths have circle
 * endpoints and so might be colliders (which must not be followed when blocking) or non-colliders (which may be).
 * This class handles that by first running {@link RecursiveBlocking#blockPathsRecursively} with nothing excluded to
 * obtain a pool of "ambiguous" nodes (those in the blocking set with a circle endpoint on some incident edge; or, if
 * the unconstrained pass fails, the v-nodes of the discriminating paths from x to y), and then enumerating subsets of
 * that pool as the not-followed set, re-blocking for each and testing the resulting set for independence. The first
 * passing set is returned, or, under the max-p option, the passing set with the strongest evidence of independence.
 * <p>
 * All methods are static; the class is not instantiable.
 *
 * @author josephramsey
 * @see RecursiveBlocking
 * @see edu.cmu.tetrad.search.utils.FciOrient
 */
public class RecursiveDiscriminatingPathRule {

    /**
     * Private constructor; this class provides only static methods and is not meant to be instantiated.
     */
    private RecursiveDiscriminatingPathRule() {

    }

    /**
     * Finds a separating set for the non-adjacent nodes x and y by recursive blocking over discriminating paths,
     * returning the first passing candidate, with no independence-check counter and the given timeout. See
     * {@link #findDdpSepsetRecursive(IndependenceTest, Graph, Node, Node, int, int, int, PreserveMarkov,
     * IndependenceCheckCounter, long, boolean)} for the algorithm and the meaning of the parameters.
     *
     * @param test                 the independence test used to check candidate separating sets.
     * @param pag                  the partially oriented graph being oriented.
     * @param x                    one endpoint; must not be adjacent to y.
     * @param y                    the other endpoint.
     * @param recursiveDepth       the maximum recursion depth of the path-blocking search (-1 for unlimited).
     * @param maxDdpPathLength     the maximum length of discriminating paths considered when the ambiguous pool has
     *                             to be built from the paths themselves (-1 for unlimited).
     * @param depth                the maximum size of a candidate separating set (-1 for unlimited).
     * @param preserveMarkovHelper if non-null, candidates are checked through this helper instead of the test, so
     *                             that its running Markov bookkeeping is respected; may be null.
     * @param timeout              the maximum time allowed, in milliseconds; a negative value means no timeout.
     * @return a separating set for x and y, or null if none was found within the limits.
     * @throws InterruptedException     if the thread is interrupted.
     * @throws IllegalArgumentException if x and y are adjacent in the graph.
     */
    public static Set<Node> findDdpSepsetRecursive(IndependenceTest test, Graph pag, Node x, Node y,
                                                   int recursiveDepth, int maxDdpPathLength,
                                                   int depth, PreserveMarkov preserveMarkovHelper, long timeout)
            throws InterruptedException {
        return findDdpSepsetRecursive(test, pag, x, y, recursiveDepth, maxDdpPathLength,
                depth, preserveMarkovHelper, null, timeout);
    }

    /**
     * Finds a separating set for the non-adjacent nodes x and y by recursive blocking over discriminating paths,
     * returning the first passing candidate, with an independence-check counter and no timeout. See
     * {@link #findDdpSepsetRecursive(IndependenceTest, Graph, Node, Node, int, int, int, PreserveMarkov,
     * IndependenceCheckCounter, long, boolean)} for the algorithm and the meaning of the parameters.
     *
     * @param test                 the independence test used to check candidate separating sets.
     * @param pag                  the partially oriented graph being oriented.
     * @param x                    one endpoint; must not be adjacent to y.
     * @param y                    the other endpoint.
     * @param recursiveDepth       the maximum recursion depth of the path-blocking search (-1 for unlimited).
     * @param maxDdpPathLength     the maximum length of discriminating paths considered when the ambiguous pool has
     *                             to be built from the paths themselves (-1 for unlimited).
     * @param depth                the maximum size of a candidate separating set (-1 for unlimited).
     * @param preserveMarkovHelper if non-null, candidates are checked through this helper instead of the test, so
     *                             that its running Markov bookkeeping is respected; may be null.
     * @param counter              a counter incremented once per independence check performed; may be null.
     * @return a separating set for x and y, or null if none was found within the limits.
     * @throws InterruptedException     if the thread is interrupted.
     * @throws IllegalArgumentException if x and y are adjacent in the graph.
     */
    public static Set<Node> findDdpSepsetRecursive(IndependenceTest test, Graph pag, Node x, Node y,
                                                   int recursiveDepth, int maxDdpPathLength,
                                                   int depth, PreserveMarkov preserveMarkovHelper,
                                                   IndependenceCheckCounter counter)
            throws InterruptedException {
        return findDdpSepsetRecursive(test, pag, x, y, recursiveDepth, maxDdpPathLength, depth, preserveMarkovHelper, counter, Long.MAX_VALUE);
    }

    /**
     * Finds a separating set for the non-adjacent nodes x and y by recursive blocking over discriminating paths,
     * returning the first passing candidate. See
     * {@link #findDdpSepsetRecursive(IndependenceTest, Graph, Node, Node, int, int, int, PreserveMarkov,
     * IndependenceCheckCounter, long, boolean)} for the algorithm and the meaning of the parameters; this overload
     * fixes {@code useMaxP} to false.
     *
     * @param test                 the independence test used to check candidate separating sets.
     * @param pag                  the partially oriented graph being oriented.
     * @param x                    one endpoint; must not be adjacent to y.
     * @param y                    the other endpoint.
     * @param recursiveDepth       the maximum recursion depth of the path-blocking search (-1 for unlimited).
     * @param maxDdpPathLength     the maximum length of discriminating paths considered when the ambiguous pool has
     *                             to be built from the paths themselves (-1 for unlimited).
     * @param depth                the maximum size of a candidate separating set (-1 for unlimited).
     * @param preserveMarkovHelper if non-null, candidates are checked through this helper instead of the test, so
     *                             that its running Markov bookkeeping is respected; may be null.
     * @param counter              a counter incremented once per independence check performed; may be null.
     * @param timeout              the maximum time allowed, in milliseconds; a negative value means no timeout.
     * @return a separating set for x and y, or null if none was found within the limits.
     * @throws InterruptedException     if the thread is interrupted.
     * @throws IllegalArgumentException if x and y are adjacent in the graph.
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
     * Finds a separating set for the non-adjacent nodes x and y by recursive blocking over discriminating paths,
     * with control over how the set is chosen when more than one candidate renders x and y independent.
     * <p>
     * The algorithm: (1) run {@link RecursiveBlocking#blockPathsRecursively} from x to y with nothing excluded; (2)
     * take as the ambiguous pool those nodes of the resulting blocking set that have a circle endpoint on some
     * incident edge, or, if that pass found no blocking set or timed out, the v-nodes of the discriminating paths
     * from x to y of length at most {@code maxDdpPathLength}; (3) for each subset of the pool, re-run the blocking
     * with that subset marked not-followed (treating those nodes as colliders), add back any pool node the blocking
     * itself placed in the set, and check the resulting set for independence, skipping sets already checked.
     * <p>
     * With {@code useMaxP} false the FIRST passing candidate is returned, so the result depends on the order in
     * which {@link SublistGenerator} enumerates the subsets. With it true, every candidate is checked and the one
     * with the STRONGEST evidence of independence is returned (largest p-value for a p-value test, smallest score
     * for a score wrapped as a test), which removes that order dependence. The choice matters: R4 reads the returned
     * set twice, once to check that it contains the collider path and once to decide (by whether it contains V)
     * whether V is a collider on the discriminating path, and the set is recorded in the caller's sepset map.
     * <p>
     * Two caveats. First, max-p forces the full enumeration on every call rather than exiting at the first hit; the
     * enumeration is over subsets of the ambiguous pool, so the cost is bounded by the same worst case the greedy
     * rule already faces, but the average case is worse. If the timeout cuts the enumeration short, the strongest
     * candidate seen so far is returned, whereas the greedy rule returns null on timeout unless a hit was already
     * found. Second, when a {@link PreserveMarkov} helper is supplied the greedy rule is used regardless of this
     * flag: that helper's accept path mutates its running p-value bookkeeping, so evaluating every candidate would
     * fold rejected candidates' updates into the state that later calls depend on. That restriction can be lifted if
     * PreserveMarkov grows a side-effect-free query.
     *
     * @param test                 the independence test used to check candidate separating sets, and, under max-p,
     *                             to rank them.
     * @param pag                  the partially oriented graph being oriented.
     * @param x                    one endpoint; must not be adjacent to y.
     * @param y                    the other endpoint.
     * @param recursiveDepth       the maximum recursion depth of the path-blocking search (-1 for unlimited).
     * @param maxDdpPathLength     the maximum length of discriminating paths considered when the ambiguous pool has
     *                             to be built from the paths themselves (-1 for unlimited).
     * @param depth                the maximum size of a candidate separating set (-1 for unlimited).
     * @param preserveMarkovHelper if non-null, candidates are checked through this helper instead of the test, so
     *                             that its running Markov bookkeeping is respected, and the greedy rule is used
     *                             regardless of {@code useMaxP}; may be null.
     * @param counter              a counter incremented once per independence check performed; may be null.
     * @param timeout              the maximum time allowed, in milliseconds, shared by the blocking passes and the
     *                             enumeration; a negative value means no timeout.
     * @param useMaxP              whether to return the passing candidate with the strongest evidence of independence
     *                             rather than the first one found.
     * @return a separating set for x and y, or null if none was found within the limits.
     * @throws InterruptedException     if the thread is interrupted.
     * @throws IllegalArgumentException if x and y are adjacent in the graph.
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

