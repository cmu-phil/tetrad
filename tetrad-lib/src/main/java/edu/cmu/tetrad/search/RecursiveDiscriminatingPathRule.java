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
     * Finds the set of nodes (separator set) for the Recursive Discriminating Path rule in a graph. This method uses a
     * recursive approach to evaluate possible discriminating paths between two nodes {@code x} and {@code y} in the
     * provided graph {@code pag}.
     *
     * @param test                 The independence test object used to check for conditional independence between
     *                             nodes.
     * @param pag                  The graph structure, typically a partial ancestral graph (PAG), being analyzed.
     * @param x                    The first target node in the analysis.
     * @param y                    The second target node in the analysis.
     * @param recursiveDepth       The maximum allowable length of a blocking path for the analysis.
     * @param maxDdpPathLength     The maximum allowable discriminating path length considered for the analysis.
     * @param depth                The maximum subset depth allowed during subset evaluations; a value of -1 allows all
     *                             subsets.
     * @param preserveMarkovHelper A helper object for additional Markov property checks during the independence
     *                             tests.
     * @return A set of nodes that constitutes the separating set (sepset) between {@code x} and {@code y}, or
     * {@code null} if no such set exists.
     * @throws InterruptedException If any.
     */
    public static Set<Node> findDdpSepsetRecursive(IndependenceTest test, Graph pag, Node x, Node y,
                                                   int recursiveDepth, int maxDdpPathLength,
                                                   int depth, PreserveMarkov preserveMarkovHelper)
            throws InterruptedException {

        if (pag.isAdjacentTo(x, y)) {
            throw new IllegalArgumentException("Nodes must be non-adjacent to each other.");
        }

//        List<Node> notFollowedSuperset = getVNodes(pag, x, y, maxDdpPathLength);

        RecursiveBlocking.BlockingResult result0 = RecursiveBlocking.blockPathsRecursivelySmallerDirection(
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
//        Set<Node> highestPSet = null;
//        double maxPValue = 0.0;

        while ((choice = gen.next()) != null) {
            Set<Node> vNodesNotFollowed = GraphUtils.asSet(choice, notFollowedSuperset);

            RecursiveBlocking.BlockingResult result = RecursiveBlocking.blockPathsRecursivelySmallerDirection(
                    pag, x, y, Set.of(), vNodesNotFollowed, recursiveDepth, depth, -1,
                    1, true);

            if (result.indeterminate()) {
                continue;
            }

            if (!result.found()) {
                continue;
            }

            // Add back the notFollowedSuperset that were followed (i.e. not in vNodesNotFollowed),
            // since those are non-colliders on their paths and belong in the sepset.
//            Set<Node> testSet = new HashSet<>(result.blockingSet());
//
//            if (testSets.contains(testSet)) {
//                continue;
//            }
//
//            testSets.add(testSet);
//
//            for (Node f : notFollowedSuperset) {
//                if (!vNodesNotFollowed.contains(f)) {
//                    testSet.add(f);
//                }
//            }

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
                independent = preserveMarkovHelper.markovIndependence(x, y, testSet);
            } else {
                IndependenceResult independenceResult = test.checkIndependence(x, y, testSet);
                independent = independenceResult.isIndependent();

//                if (!Double.isNaN(independenceResult.getPValue()) && independenceResult.getPValue() > maxPValue) {
////                    maxPValue = Math.max(maxPValue, independenceResult.getPValue());
//                    highestPSet = testSet;
//                }
            }

//            if ((test instanceof MsepTest) &&  independent) {
//                return testSet;
//            }

            if (independent) {
                return testSet;
            }
        }

//        if (highestPSet == null) {
//            System.out.println("No sepset found for " + x + " and " + y);
//        }

        return null;
    }

//    private static @NotNull List<Node> getVNodes(Graph pag, Node x, Node y, int maxDdpPathLength) {
//        // 2) List possible DiscriminatingPaths
//        Set<DiscriminatingPath> discriminatingPaths = FciOrient.listDiscriminatingPaths(pag, maxDdpPathLength, true);
//
//        // 3) Figure out which nodes might be "notFollowed"
//        Set<DiscriminatingPath> relevantPaths = new HashSet<>();
//        for (DiscriminatingPath path : discriminatingPaths) {
//            if ((path.getX() == x && path.getY() == y) || (path.getX() == y && path.getY() == x)) {
//                relevantPaths.add(path);
//            }
//        }
//
//        Set<Node> vNodes = new HashSet<>();
//        for (DiscriminatingPath path : relevantPaths) {
//            if (pag.getEndpoint(path.getY(), path.getV()) == Endpoint.CIRCLE) {
//                vNodes.add(path.getV());
//            }
//
//        }
//
//        return new ArrayList<>(vNodes);
//    }
}

