///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;


/**
 * Converts a DAG (Directed acyclic graph) into the PAG (partial ancestral graph) which it is in the equivalence class
 * of.
 *
 * @author josephramsey
 * @author peterspirtes
 * @version $Id: $Id
 */
public final class DagToPag {

    /**
     * The DAG to be converted.
     */
    private final Graph dag;
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
     * Constructs a new FCI search for the given independence test and background knowledge.
     *
     * @param dag a {@link Graph} object
     */
    public DagToPag(Graph dag) {
        this.dag = new EdgeListGraph(dag);
    }

    /**
     * Calculates the adjacency graph for the given Directed Acyclic Graph (DAG).
     *
     * @param dag The input Directed Acyclic Graph (DAG).
     * @return The adjacency graph represented by a Graph object.
     */
    public static Graph calcAdjacencyGraph(Graph dag) {
        List<Node> allNodes = dag.getNodes();
        List<Node> measured = new ArrayList<>(allNodes);
        measured.removeIf(node -> node.getNodeType() != NodeType.MEASURED);

        Graph graph = new EdgeListGraph(measured);

        for (int i = 0; i < measured.size(); i++) {
            for (int j = i + 1; j < measured.size(); j++) {
                Node n1 = measured.get(i);
                Node n2 = measured.get(j);

                if (graph.isAdjacentTo(n1, n2)) continue;

                List<Node> inducingPath = dag.paths().getInducingPath(n1, n2);

                boolean exists = inducingPath != null;

                if (exists) {
                    graph.addEdge(Edges.nondirectedEdge(n1, n2));
                }
            }
        }

        return graph;
    }

    /**
     * This method does the conversion of DAG to PAG.
     *
     * @return Returns the converted PAG.
     */
    public Graph convert() {

        Graph mag;

        if (dag.paths().isLegalDag()) {
            mag = GraphTransforms.dagToMag(dag);
        } else if (dag.getNodes().stream().noneMatch(n -> n.getNodeType() == NodeType.LATENT)) {
            mag = GraphTransforms.zhangMagFromPag(dag);
        } else {
            throw new IllegalArgumentException("Expecting either a DAG possibly with latents or else a graph with no latents" + "but possibly with circle endpoints.");
        }

        Graph pag = new EdgeListGraph(mag);

        pag.reorientAllWith(Endpoint.CIRCLE);

        // Note that we will re-use FCIOrient but override the R0 and discriminating path rules to use D-SEP(A,B) or D-SEP(B,A)
        // to find the d-separating set between A and B.
        R0R4StrategyTestBased strategy = new R0R4StrategyTestBased(new MsepTest(mag)) {
            @Override
            public boolean isUnshieldedCollider(Graph graph, Node i, Node j, Node k) {
                Graph mag = ((MsepTest) getTest()).getGraph();

                // Could copy the unshielded colliders from the mag but we will use D-SEP.
//                return mag.isDefCollider(i, j, k) && !mag.isAdjacentTo(i, k);

                Set<Node> dsepi = mag.paths().dsep(i, k);
                Set<Node> dsepk = mag.paths().dsep(k, i);

                if (getTest().checkIndependence(i, k, dsepi).isIndependent()) {
                    return !dsepi.contains(j);
                } else if (getTest().checkIndependence(k, i, dsepk).isIndependent()) {
                    return !dsepk.contains(j);
                }

                return false;
            }

            /**
             * Does a discriminating path orientation.
             *
             * @param discriminatingPath the discriminating path
             * @param graph              the graph representation
             * @return a pair of the discriminating path construct and a boolean indicating whether the orientation was determined.
             * @throws IllegalArgumentException if 'e' is adjacent to 'c'
             * @see DiscriminatingPath
             */
            public Pair<DiscriminatingPath, Boolean> doDiscriminatingPathOrientation(DiscriminatingPath discriminatingPath, Graph graph) {
                Node e = discriminatingPath.getE();
                Node a = discriminatingPath.getA();
                Node b = discriminatingPath.getB();
                Node c = discriminatingPath.getC();

                if (!discriminatingPath.isValidForGraph(graph)) {
                    return Pair.of(discriminatingPath, false);
                }

                if (graph.isAdjacentTo(e, c)) {
                    throw new IllegalArgumentException("e and c must not be adjacent");
                }

                Graph mag = ((MsepTest) getTest()).getGraph();

                Set<Node> dsepe = GraphUtils.dsep(e, c, mag);
                Set<Node> dsepc = GraphUtils.dsep(c, e, mag);

                Set<Node> sepset = null;

                if (getTest().checkIndependence(e, c, dsepe).isIndependent()) {
                    sepset = dsepe;
                } else if (getTest().checkIndependence(c, e, dsepc).isIndependent()) {
                    sepset = dsepc;
                }

                if (sepset == null) {
                    return Pair.of(discriminatingPath, false);
                }

                if (verbose) {
                    TetradLogger.getInstance().log("Sepset for e = " + e + " and c = " + c + " = " + sepset);
                }

                boolean collider = !sepset.contains(b);

                if (collider) {
                    if (isDoDiscriminatingPathColliderRule()) {
                        if (!FciOrient.isArrowheadAllowed(a, b, graph, knowledge)) {
                            return Pair.of(discriminatingPath, false);
                        }

                        if (!FciOrient.isArrowheadAllowed(c, b, graph, knowledge)) {
                            return Pair.of(discriminatingPath, false);
                        }

                        graph.setEndpoint(a, b, Endpoint.ARROW);
                        graph.setEndpoint(c, b, Endpoint.ARROW);

                        if (verbose) {
                            TetradLogger.getInstance().log("R4: Definite discriminating path collider rule e = " + e + " " + GraphUtils.pathString(graph, a, b, c));
                        }

                        return Pair.of(discriminatingPath, true);
                    }
                } else {
                    if (isDoDiscriminatingPathTailRule()) {
                        graph.setEndpoint(c, b, Endpoint.TAIL);

                        if (verbose) {
                            TetradLogger.getInstance().log("R4: Definite discriminating path tail rule e = " + e + " " + GraphUtils.pathString(graph, a, b, c));
                        }

                        return Pair.of(discriminatingPath, true);
                    }
                }

                return Pair.of(discriminatingPath, false);
            }

            @Override
            public void setAllowedColliders(Set<Triple> allowedColliders) {
                // Ignore.
            }
        };

        FciOrient fciOrient = new FciOrient(strategy);
        fciOrient.setVerbose(verbose);
        fciOrient.orient(pag);
        fciOrient.setTestTimeout(-1);

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




