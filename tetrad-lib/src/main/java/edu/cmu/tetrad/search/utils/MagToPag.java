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

package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;
import java.util.Set;


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
    private int depth = -1;


    /**
     * Constructs a new FCI search for the given independence test and background knowledge.
     *
     * @param mag a {@link Graph} object
     */
    public MagToPag(Graph mag) {
        this.mag = new EdgeListGraph(mag);
    }

    /**
     * Returns the final strategy for finding a PAG using D-SEP.
     *
     * @param mag       the MAG (Maximum Ancestral Graph) representation of the graph
     * @param knowledge the background knowledge used for the orientation
     * @param verbose   a boolean indicating whether verabose output should be printed
     * @return the final strategy for finding a PAG using D-SEP
     */
    public static R0R4StrategyTestBased getFinalStrategyUsingDsep(Graph mag, Knowledge knowledge, boolean verbose) {

        // Note that we will re-use FCIOrient but override the R0 and discriminating path rules to use D-SEP(A,B) or D-SEP(B,A)
        // to find the d-separating set between A and B.
        return new R0R4StrategyTestBased(new MsepTest(mag)) {
            @Override
            public boolean isUnshieldedCollider(Graph graph, Node i, Node j, Node k) {

                // We assume the MAG already has the unshielded colliders oriented that the algorithm
                // says should be oriented.
                Graph mag1 = ((MsepTest) getTest()).getGraph();
                return !mag1.isAdjacentTo(i, k) && mag1.isDefCollider(i, j, k);

//                Set<Node> dsepi = mag1.paths().dsep(i, k);
//                Set<Node> dsepk = mag1.paths().dsep(k, i);
//
//                if (getTest().checkIndependence(i, k, dsepi).isIndependent()) {
//                    return !dsepi.contains(j);
//                } else if (getTest().checkIndependence(k, i, dsepk).isIndependent()) {
//                    return !dsepk.contains(j);
//                }
//
//                return false;
            }

            /**
             * Does a discriminating path orientation.
             *
             * @param discriminatingPath the discriminating path
             * @param graph              the graph representation
             * @param vNodes            the set of nodes that are V-nodes
             * @return a pair of the discriminating path construct and a boolean indicating whether the
             * orientation was determined.
             * @throws IllegalArgumentException if 'e' is adjacent to 'c'
             * @see DiscriminatingPath
             */
            public Pair<DiscriminatingPath, Boolean> doDiscriminatingPathOrientation(DiscriminatingPath discriminatingPath, Graph graph, Set<Node> vNodes) {
                Node x = discriminatingPath.getX();
                Node w = discriminatingPath.getW();
                Node v = discriminatingPath.getV();
                Node y = discriminatingPath.getY();

                // Check that the discriminating path construct still exists in the graph.
                if (!discriminatingPath.existsIn(graph)) {
                    return Pair.of(discriminatingPath, false);
                }

                // Check that the discriminating path has not yet been oriented; we don't need to list the ones that have
                // already been oriented.
                if (graph.getEndpoint(y, v) != Endpoint.CIRCLE) {
                    return Pair.of(discriminatingPath, false);
                }

                if (graph.isAdjacentTo(x, y)) {
                    throw new IllegalArgumentException("x and y must not be adjacent");
                }

                Set<Node> sepset = mag.isAdjacentTo(x, y) ? null : mag.paths().anteriority(x, y);

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
     * @param checkMag Whether to check if the MAG is legal before conversion.
     * @return Returns the converted PAG.
     */
    public Graph convert(boolean checkMag) {
        if (checkMag && !this.mag.paths().isLegalMag()) {
            throw new IllegalArgumentException("Not legal mag");
        }

        Graph pag = new EdgeListGraph(mag);

        pag.reorientAllWith(Endpoint.CIRCLE);

        FciOrient fciOrient = new FciOrient(getFinalStrategyUsingDsep(mag, knowledge, verbose));
        fciOrient.setVerbose(verbose);
        fciOrient.setKnowledge(knowledge);
        fciOrient.setMaxDiscriminatingPathLength(-1);
        fciOrient.setCompleteRuleSetUsed(completeRuleSetUsed);

        for (Node y : pag.getNodes()) {
            List<Node> adjy = pag.getAdjacentNodes(y);

            for (int i = 0; i < adjy.size(); i++) {
                for (int j = i + 1; j < adjy.size(); j++) {
                    Node x = adjy.get(i);
                    Node z = adjy.get(j);

                    if (mag.isDefCollider(x, y, z) && !mag.isAdjacentTo(x, z)) {
                        pag.setEndpoint(x, y, Endpoint.ARROW);
                        pag.setEndpoint(z, y, Endpoint.ARROW);
                    }
                }
            }
        }

//        fciOrient.ruleR0(pag, new HashSet<>());
        fciOrient.finalOrientation(pag);

        return pag;
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
     * Setws whether verbose output should be printed.
     *
     * @param verbose True, if so.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }
}





