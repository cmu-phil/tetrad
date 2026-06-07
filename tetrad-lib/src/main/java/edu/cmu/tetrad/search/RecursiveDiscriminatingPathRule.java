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
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.search.utils.IndependenceCheckCounter;
import edu.cmu.tetrad.search.utils.PreserveMarkov;
import edu.cmu.tetrad.util.SublistGenerator;

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

    /**
     * Finds the separating set (sepset) recursively using discriminating paths in a partially oriented graph (PAG).
     * This method evaluates conditional independence relationships and identifies separating sets
     * based on the Recursive Discriminating Path algorithm.
     *
     * @param test               The independence test used to evaluate conditional independence relationships.
     * @param pag                The partially oriented graph (PAG) being analyzed.
     * @param x                  The first node for which the sepset is determined.
     * @param y                  The second node for which the sepset is determined.
     * @param recursiveDepth     The maximum allowed depth for recursive search in discriminating paths.
     * @param maxDdpPathLength   The maximum length of discriminating paths to consider.
     * @param depth              The current recursion depth of the algorithm.
     * @param preserveMarkovHelper A helper object to ensure the Markov property is preserved during execution.
     * @return                   A set of nodes representing the separating set (sepset) identified between nodes x and y.
     * @throws InterruptedException If the thread is interrupted while executing the method.
     */
    public static Set<Node> findDdpSepsetRecursive(IndependenceTest test, Graph pag, Node x, Node y,
                                                   int recursiveDepth, int maxDdpPathLength,
                                                   int depth, PreserveMarkov preserveMarkovHelper)
            throws InterruptedException {
        return findDdpSepsetRecursive(test, pag, x, y, recursiveDepth, maxDdpPathLength,
                depth, preserveMarkovHelper, null);
    }

    /**
     * Recursively finds the separating set (sepset) between two non-adjacent nodes in a partially oriented
     * graph (PAG) using the Recursive Discriminating Path algorithm. This method evaluates conditional
     * independence relationships and considers subsets of possible nodes on discriminating paths to identify
     * the sepset.
     *
     * @param test                The independence test used for evaluating conditional independence.
     * @param pag                 The partially oriented graph (PAG) being analyzed.
     * @param x                   The first node for which the sepset is determined.
     * @param y                   The second node for which the sepset is determined.
     * @param recursiveDepth      The maximum depth for recursive search in discriminating paths.
     * @param maxDdpPathLength    The maximum length of discriminating paths to consider.
     * @param depth               The current recursion depth of the algorithm.
     * @param preserveMarkovHelper A helper object to ensure the Markov property is preserved during execution.
     * @param counter             A counter to track the number of independence checks performed.
     * @return                    A set of nodes representing the separating set (sepset) identified
     *                            between nodes x and y, or {@code null} if no sepset is found.
     * @throws InterruptedException If the thread is interrupted during execution.
     */
    public static Set<Node> findDdpSepsetRecursive(IndependenceTest test, Graph pag, Node x, Node y,
                                                   int recursiveDepth, int maxDdpPathLength,
                                                   int depth, PreserveMarkov preserveMarkovHelper,
                                                   IndependenceCheckCounter counter)
            throws InterruptedException {

        if (pag.isAdjacentTo(x, y)) {
            throw new IllegalArgumentException("Nodes must be non-adjacent to each other.");
        }

//        List<Node> notFollowedSuperset = getVNodes(pag, x, y, maxDdpPathLength);

        RecursiveBlocking.BlockingResult result0 = RecursiveBlocking.blockPathsRecursively(
                pag, x, y, Set.of(), Set.of(), recursiveDepth, depth, -1,
                1, true);

        Set<Node> noFollowsBlocking = result0.blockingSet();
        List<Node> notFollowedSuperset = new ArrayList<>();

        // Remove any node from the notFollowedSuperset whose orientations are already completely
        // determined. We don't need to consider both possibilities for these.
        for (Node f : noFollowsBlocking) {
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

        while ((choice = gen.next()) != null) {
            Set<Node> vNodesNotFollowed = GraphUtils.asSet(choice, notFollowedSuperset);

            RecursiveBlocking.BlockingResult result = RecursiveBlocking.blockPathsRecursively(
                    pag, x, y, Set.of(), vNodesNotFollowed, recursiveDepth, depth, -1,
                    1, true);

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
                return testSet;
            }
        }

        System.out.println("Recursive DDP: No sepset found for " + x + " and " + y);
        return null;
    }
}

