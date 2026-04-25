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

package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.lang3.tuple.Pair;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


/**
 * Converts a MAG to a PAG.
 *
 * @author josephramsey
 * @author peterspirtes
 * @version $Id: $Id
 */
public final class MagToPag {

    /**
     * The MAG to be converted.
     */
    private final Graph mag;
    /*
     * The background knowledge.
     */
    private Knowledge knowledge = new Knowledge();
    /**
     * Flag for the complete rule set, true if one should use the complete rule set, false otherwise.
     */
    private boolean completeRuleSetUsed = true;
    /**
     * True iff verbose output should be printed.
     */
    private boolean verbose;
    /**
     * Represents the maximum length of blocking paths to be considered during MAG to PAG conversion. A value of -1
     * indicates that all possible blocking paths should be considered without any specific length constraint.
     * This value can be adjusted to limit the depth of analysis based on the specific use case.
     */
    private int maxBlockingPathLength = -1;
    /**
     * Represents the maximum length of discriminating paths to be considered during MAG to PAG conversion. A value of
     * -1 indicates that all possible discriminating paths should be considered without any specific length constraint.
     * This value can be adjusted to limit the depth of analysis based on the specific use case.
     */
    private int maxDiscriminatingPathLength = -1;

    /**
     * Per-node ancestor sets precomputed once at the start of convert(). Anteriority for
     * any (x, y) pair is then a cheap set union rather than a full graph traversal.
     */
    private Map<Node, Set<Node>> ancestorCache;


    /**
     * Constructs a new FCI search for the given independence test and background knowledge.
     *
     * @param mag a {@link Graph} object
     */
    public MagToPag(Graph mag) {
        this.mag = new EdgeListGraph(mag);
    }

    /**
     * Computes anteriority for a pair of nodes from precomputed per-node ancestor sets.
     * This is (An(x) ∪ An(y)) \ {x, y}, computed via set union rather than graph traversal.
     *
     * @param x             first node
     * @param y             second node
     * @param ancestorCache precomputed ancestor sets for all nodes in the MAG
     * @return the anteriority set for (x, y)
     */
    private static Set<Node> anteriorityFromCache(Node x, Node y, Map<Node, Set<Node>> ancestorCache) {
        Set<Node> result = new HashSet<>(ancestorCache.get(x));
        result.addAll(ancestorCache.get(y));
        result.remove(x);
        result.remove(y);
        return result;
    }

    /**
     * Returns the final strategy for finding a PAG using D-SEP.
     *
     * @param mag           the MAG (Maximum Ancestral Graph) representation of the graph
     * @param knowledge     the background knowledge used for the orientation
     * @param verbose       a boolean indicating whether verbose output should be printed
     * @param ancestorCache precomputed per-node ancestor sets for O(n) anteriority lookups
     * @return the final strategy for finding a PAG using D-SEP
     */
    public static R0R4StrategyTestBased getFinalStrategyUsingDsep(Graph mag, Knowledge knowledge, boolean verbose,
                                                                  Map<Node, Set<Node>> ancestorCache) {
        return new R0R4StrategyTestBased(new MsepTest(mag)) {
            @Override
            public boolean isUnshieldedCollider(Graph graph, Node i, Node j, Node k) {
                Graph mag1 = ((MsepTest) getTest()).getGraph();
                return !mag1.isAdjacentTo(i, k) && mag1.isDefCollider(i, j, k);
            }

            /**
             * Does a discriminating path orientation.
             *
             * @param discriminatingPath the discriminating path
             * @param graph              the graph representation
             * @param vNodes             the set of nodes that are V-nodes
             * @return a pair of the discriminating path construct and a boolean indicating whether the
             * orientation was determined.
             * @throws IllegalArgumentException if x is adjacent to y
             * @see DiscriminatingPath
             */
            public Pair<DiscriminatingPath, Boolean> doDiscriminatingPathOrientation(DiscriminatingPath discriminatingPath, Graph graph, Set<Node> vNodes) {
                Node x = discriminatingPath.getX();
                Node w = discriminatingPath.getW();
                Node v = discriminatingPath.getV();
                Node y = discriminatingPath.getY();

                if (!discriminatingPath.existsIn(graph)) {
                    return Pair.of(discriminatingPath, false);
                }

                if (graph.getEndpoint(y, v) != Endpoint.CIRCLE) {
                    return Pair.of(discriminatingPath, false);
                }

                if (graph.isAdjacentTo(x, y)) {
                    throw new IllegalArgumentException("x and y must not be adjacent");
                }

                // Anteriority is now a free set union from precomputed ancestor sets
                // rather than an O(n^2) graph traversal.
                Set<Node> sepset = mag.isAdjacentTo(x, y)
                        ? null
                        : anteriorityFromCache(x, y, ancestorCache);

                if (verbose) {
                    TetradLogger.getInstance().log("Sepset for x = " + x + " and y = " + y + " = " + sepset);
                }

                if (sepset != null && sepset.contains(v)) {
                    graph.setEndpoint(y, v, Endpoint.TAIL);

                    if (verbose) {
                        TetradLogger.getInstance().log("R4: Definite discriminating path tail rule x = " + x + " " + GraphUtils.pathString(graph, w, v, y));
                    }

                    return Pair.of(discriminatingPath, true);
                } else {
                    if (!FciOrient.isArrowheadAllowed(w, v, graph, knowledge)) {
                        return Pair.of(discriminatingPath, false);
                    }

                    if (!FciOrient.isArrowheadAllowed(y, v, graph, knowledge)) {
                        return Pair.of(discriminatingPath, false);
                    }

                    graph.setEndpoint(w, v, Endpoint.ARROW);
                    graph.setEndpoint(y, v, Endpoint.ARROW);

                    if (verbose) {
                        TetradLogger.getInstance().log("R4: Definite discriminating path collider rule x = " + x + " " + GraphUtils.pathString(graph, w, v, y));
                    }

                    return Pair.of(discriminatingPath, true);
                }
            }
        };
    }

    /**
     * This method does the conversion of MAG to PAG.
     *
     * @param checkMag             Whether to check if the MAG is legal before conversion.
     * @param excludeSelectionBias True to exclude selection bias, false otherwise.
     * @return Returns the converted PAG.
     * @throws IllegalStateException if a discriminating path cannot be found. (This can only be because a path length
     * bound was exceeded in looking for one, a rare case.)
     */
    public Graph convert(boolean checkMag, boolean excludeSelectionBias) {
        if (checkMag && !this.mag.paths().isLegalMag()) {
            throw new IllegalArgumentException("Not legal mag");
        }

        // Precompute ancestor sets for all nodes once. Each call to anteriority()
        // in the original code was O(n * pathSearch); now it's a single O(n * pathSearch)
        // pass here, and each subsequent anteriority lookup is just a set union — O(n).
        ancestorCache = new HashMap<>();
        for (Node n : mag.getNodes()) {
            ancestorCache.put(n, new HashSet<>(mag.paths().getAncestors(n)));
        }

        Graph pag = new EdgeListGraph(mag);
        pag.reorientAllWith(Endpoint.CIRCLE);

        FciOrient fciOrient = new FciOrient(getFinalStrategyUsingDsep(mag, knowledge, verbose, ancestorCache));
        fciOrient.setVerbose(verbose);
        fciOrient.setKnowledge(knowledge);
        fciOrient.setCompleteRuleSetUsed(completeRuleSetUsed);
        fciOrient.setMaxBlockingPathLength(maxBlockingPathLength);
        fciOrient.setMaxDiscriminatingPathLength(maxDiscriminatingPathLength);
        fciOrient.fciOrientbk(knowledge, pag, pag.getNodes(), excludeSelectionBias);

        // Optimization #6: collect all collider-qualifying triples in parallel, then
        // apply orientations on the main thread. The parallel scan is safe because we
        // only read from mag and pag here — writes happen after the stream completes.
        //
        // We gather (x, y, z) triples that need orienting into a thread-safe set,
        // deduplicating by the centre node y (since setEndpoint is idempotent for
        // arrowheads, duplicate entries are harmless but wasteful).
        List<Node> nodes = pag.getNodes();
        Set<Triple> collidersToOrient = ConcurrentHashMap.newKeySet();

        nodes.parallelStream().forEach(y -> {
            List<Node> adjy = pag.getAdjacentNodes(y);

            for (int i = 0; i < adjy.size(); i++) {
                for (int j = i + 1; j < adjy.size(); j++) {
                    Node x = adjy.get(i);
                    Node z = adjy.get(j);

                    // Optimization #2: skip if both endpoints are already arrowheads —
                    // setEndpoint would be a no-op and isDefCollider is more expensive.
                    if (pag.getEndpoint(x, y) == Endpoint.ARROW
                            && pag.getEndpoint(z, y) == Endpoint.ARROW) {
                        continue;
                    }

                    // Optimization #4 (retained): cheap adjacency check before
                    // expensive isDefCollider.
                    if (!mag.isAdjacentTo(x, z) && mag.isDefCollider(x, y, z)) {
                        collidersToOrient.add(new Triple(x, y, z));
                    }
                }
            }
        });

        // Apply orientations single-threadedly to avoid any graph mutation races.
        for (Triple t : collidersToOrient) {
            pag.setEndpoint(t.x, t.y, Endpoint.ARROW);
            pag.setEndpoint(t.z, t.y, Endpoint.ARROW);
        }

        fciOrient.finalOrientation(pag, excludeSelectionBias);

        return pag;
    }

    /**
     * Sets the maximum length of blocking paths to be considered during processing.
     *
     * @param maxBlockingPathLength the maximum length of blocking paths
     */
    public void setMaxBlockingPathLength(int maxBlockingPathLength) {
        this.maxBlockingPathLength = maxBlockingPathLength;
    }

    /**
     * Lightweight value type for a triple of nodes (x, y, z) used to collect
     * collider orientations from the parallel scan before applying them.
     */
    private record Triple(Node x, Node y, Node z) {
    }

    /**
     * <p>Getter for the field <code>knowledge</code>.</p>
     *
     * @return a {@link Knowledge} object
     */
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * <p>Setter for the field <code>knowledge</code>.</p>
     *
     * @param knowledge a {@link Knowledge} object
     */
    public void setKnowledge(Knowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException();
        }

        this.knowledge = knowledge;
    }

    /**
     * <p>isCompleteRuleSetUsed.</p>
     *
     * @return true if Zhang's complete rule set should be used, false if only R1-R4 (the rule set of the original FCI)
     * should be used. False by default.
     */
    public boolean isCompleteRuleSetUsed() {
        return this.completeRuleSetUsed;
    }

    /**
     * <p>Setter for the field <code>completeRuleSetUsed</code>.</p>
     *
     * @param completeRuleSetUsed set to true if Zhang's complete rule set should be used, false if only R1-R4 (the rule
     *                            set of the original FCI) should be used. False by default.
     */
    public void setCompleteRuleSetUsed(boolean completeRuleSetUsed) {
        this.completeRuleSetUsed = completeRuleSetUsed;
    }

    /**
     * Sets whether verbose output should be printed.
     *
     * @param verbose True, if so.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Sets the maximum length of discriminating paths to be considered during processing.
     *
     * @param maxDiscriminatingPathLength the maximum length of discriminating paths
     */
    public void setMaxDiscriminatingPathLength(int maxDiscriminatingPathLength) {
        this.maxDiscriminatingPathLength = maxDiscriminatingPathLength;
    }
}