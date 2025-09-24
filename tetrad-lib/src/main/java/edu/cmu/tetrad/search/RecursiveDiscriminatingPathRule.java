///////////////////////////////////////////////////////////////////////////////
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
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.utils.DiscriminatingPath;
import edu.cmu.tetrad.search.utils.FciOrient;
import edu.cmu.tetrad.search.utils.PreserveMarkov;
import edu.cmu.tetrad.util.SublistGenerator;
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
     * @param preserveMarkovHelper  A helper object for additional Markov property checks during the independence
     *                              tests.
     * @param depth                 The maximum subset depth allowed during subset evaluations; a value of -1 allows all
     *                              subsets.
     * @return A set of nodes that constitutes the separating set (sepset) between {@code x} and {@code y}, or
     * {@code null} if no such set exists.
     * @throws InterruptedException If any.
     */
    public static Set<Node> findDdpSepsetRecursive(IndependenceTest test, Graph pag, Node x, Node y, FciOrient fciOrient,
                                                   int maxBlockingPathLength, int maxDdpPathLength, PreserveMarkov preserveMarkovHelper, int depth)
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
                Set<Node> blocking = RecursiveBlocking.blockPathsRecursively(pag, x, y, Set.of(), vNodesNotFollowed, maxBlockingPathLength);

                // This is set up to always return a set.
//                if (blocking == null) {
//                    continue;
//                }

                for (Node f : vNodes) {
                    if (!vNodesNotFollowed.contains(f)) {
                        blocking.add(f);
                    }
                }

                // b minus c
                Set<Node> testSet = new HashSet<>(blocking);
                testSet.removeAll(c);

                // Check independence
                boolean independent;
                if (preserveMarkovHelper != null) {
                    independent = preserveMarkovHelper.markovIndependence(x, y, testSet);
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
        List<Node> _vNodes = new ArrayList<>(vNodes);
        return _vNodes;
    }
}

