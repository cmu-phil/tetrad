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
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.TetradLogger;

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
        // A. Form MAG from DAG.
        // 1. Find if there is an inducing path between each pair of observed variables. If yes, add adjacency.
        // 2. Find all ancestor relations.
        // 3. Use ancestor relations to put in heads and tails.
        Graph mag = GraphTransforms.dagToMag(dag);

        // B. Form PAG
        // 1. Copy all adjacencies from MAG, but put "o" endpoints on all edges.
        // 2. Apply FCI orientation rules.
        //      a. For every orientation rule that requires looking at a d-separating set between A and B
        //          (i.e., unshielded triples, and discriminating paths), find a d-separating set between A and B
        //          by forming D-SEP(A,B) or D-SEP(B,A).
        //      b. V is in D-SEP(A,B) iff there is a collider path from A to V, in which every vertex except
        //         for the endpoints is an ancestor of A or of V.

        Graph pag = new EdgeListGraph(mag);

        // copy all adjacencies from MAG, but put "o" endpoints on all edges.
        pag.reorientAllWith(Endpoint.CIRCLE);

        // apply FCI orientation rules but with some changes. for r0 and discriminating path, we're going to use
        // D-SEP(A,B) or D-SEP(B,A) to find the d-separating set between A and B.

        // Note that we will re-use FCIOrient but overrise the R0 and discriminating path rules to use D-SEP(A,B) or D-SEP(B,A)
        // to find the d-separating set between A and B.
        FciOrient fciOrient = new FciOrient(new MsepTest(mag)) {

            @Override
            public void ruleR0(Graph graph) {
                graph.reorientAllWith(Endpoint.CIRCLE);
                fciOrientbk(super.knowledge, graph, graph.getNodes());

                List<Node> nodes = graph.getNodes();

                for (Node b : nodes) {
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }

                    List<Node> adjacentNodes = new ArrayList<>(graph.getAdjacentNodes(b));

                    if (adjacentNodes.size() < 2) {
                        continue;
                    }

                    ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
                    int[] combination;

                    while ((combination = cg.next()) != null) {
                        if (Thread.currentThread().isInterrupted()) {
                            break;
                        }

                        Node a = adjacentNodes.get(combination[0]);
                        Node c = adjacentNodes.get(combination[1]);

                        if (graph.isDefCollider(a, b, c)) {
                            continue;
                        }

                        if (isUnshieldedCollider(graph, a, b, c, depth)) {
                            if (!isArrowheadAllowed(a, b, graph, knowledge)) {
                                continue;
                            }

                            if (!isArrowheadAllowed(c, b, graph, knowledge)) {
                                continue;
                            }

                            graph.setEndpoint(a, b, Endpoint.ARROW);
                            graph.setEndpoint(c, b, Endpoint.ARROW);

                            if (super.verbose) {
                                super.logger.log(LogUtilsSearch.colliderOrientedMsg(a, b, c));
                            }

                            super.changeFlag = true;
                        }
                    }
                }
            }

            public boolean isUnshieldedCollider(Graph graph, Node i, Node j, Node k, int depth) {
                Graph mag = ((MsepTest) test).getGraph();

                // Could copy the unshielded colliders from the mag, but we will use D-SEP.
                Set<Node> dsepi = mag.paths().dsep(i, k);
                Set<Node> dsepk = mag.paths().dsep(k, i);

                if (test.checkIndependence(i, k, dsepi).isIndependent()) {
                    return !dsepi.contains(j);
                } else if (test.checkIndependence(k, i, dsepk).isIndependent()) {
                    return !dsepk.contains(j);
                }

                return false;
            }

            public boolean doDiscriminatingPathOrientation(Node e, Node a, Node b, Node c, List<Node> path, Graph graph, int depth) {
                doubleCheckDiscriminatinPathConstruct(e, a, b, c, path, graph);

                if (graph.isAdjacentTo(e, c)) {
                    throw new IllegalArgumentException("e and c must not be adjacent");
                }

                System.out.println("Looking for sepset for " + e + " and " + c + " with path " + path);

                Graph mag = ((MsepTest) test).getGraph();

                Set<Node> dsepe = GraphUtils.dsep(e, c, mag);
                Set<Node> dsepc = GraphUtils.dsep(c, e, mag);

                Set<Node> sepset = null;

                if (test.checkIndependence(e, c, dsepe).isIndependent()) {
                    sepset = dsepe;
                } else if (test.checkIndependence(c, e, dsepc).isIndependent()) {
                    sepset = dsepc;
                }

                System.out.println("...sepset for " + e + " *-* " + c + " = " + sepset);

                if (sepset == null) {
                    return false;
                }

                if (this.verbose) {
                    logger.log("Sepset for e = " + e + " and c = " + c + " = " + sepset);
                }

                boolean collider = !sepset.contains(b);

                if (collider) {
                    graph.setEndpoint(a, b, Endpoint.ARROW);
                    graph.setEndpoint(c, b, Endpoint.ARROW);

                    if (this.verbose) {
                        TetradLogger.getInstance().log(
                                "R4: Definite discriminating path collider rule e = " + e + " " + GraphUtils.pathString(graph, a, b, c));
                    }

                } else {
                    graph.setEndpoint(c, b, Endpoint.TAIL);

                    if (this.verbose) {
                        TetradLogger.getInstance().log(
                                "R4: Definite discriminating path tail rule e = " + e + " " + GraphUtils.pathString(graph, a, b, c));
                    }

                }
                this.changeFlag = true;
                return true;
            }
        };

        fciOrient.setVerbose(verbose);
        fciOrient.orient(pag);

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




