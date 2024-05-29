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
import edu.cmu.tetrad.util.TetradLogger;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.WeakHashMap;


/**
 * Converts a DAG (Directed acyclic graph) into the PAG (partial ancestral graph) which it is in the equivalence class
 * of.
 *
 * @author josephramsey
 * @author peterspirtes
 * @version $Id: $Id
 */
public final class DagToPag {

//    private static final WeakHashMap<Graph, Graph> history = new WeakHashMap<>();
    private final Graph dag;
    /**
     * The logger to use.
     */
    private final TetradLogger logger = TetradLogger.getInstance();
    /*
     * The background knowledge.
     */
    private Knowledge knowledge = new Knowledge();
    /**
     * Glag for complete rule set, true if should use complete rule set, false otherwise.
     */
    private boolean completeRuleSetUsed = true;
    /**
     * True iff verbose output should be printed.
     */
    private boolean verbose;
    private int maxPathLength = -1;
    private boolean doDiscriminatingPathRule = true;


    /**
     * Constructs a new FCI search for the given independence test and background knowledge.
     *
     * @param dag a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public DagToPag(Graph dag) {
        this.dag = new EdgeListGraph(dag);
    }


    /**
     * <p>existsInducingPathInto.</p>
     *
     * @param x     a {@link edu.cmu.tetrad.graph.Node} object
     * @param y     a {@link edu.cmu.tetrad.graph.Node} object
     * @param graph a {@link edu.cmu.tetrad.graph.Graph} object
     * @return a boolean
     */
    public static boolean existsInducingPathInto(Node x, Node y, Graph graph) {
        if (x.getNodeType() != NodeType.MEASURED) throw new IllegalArgumentException();
        if (y.getNodeType() != NodeType.MEASURED) throw new IllegalArgumentException();

        LinkedList<Node> path = new LinkedList<>();
        path.add(x);

        for (Node b : graph.getAdjacentNodes(x)) {
            Edge edge = graph.getEdge(x, b);
            if (edge.getProximalEndpoint(x) != Endpoint.ARROW) continue;
//            if (!edge.pointsTowards(x)) continue;

            if (graph.paths().existsInducingPathVisit(x, b, x, y, path)) {
                return true;
            }
        }

        return false;
    }

    /**
     * This method does the convertion of DAG to PAG.
     *
     * @return Returns the converted PAG.
     */
    public Graph convert() {
//        if (history.get(dag) != null) return history.get(dag);

        if (this.verbose) {
            System.out.println("DAG to PAG_of_the_true_DAG: Starting adjacency search");
        }

        Graph graph = calcAdjacencyGraph();

        if (this.verbose) {
            System.out.println("DAG to PAG_of_the_true_DAG: Starting collider orientation");
        }

        orientUnshieldedColliders(graph, this.dag);

        if (this.verbose) {
            System.out.println("DAG to PAG_of_the_true_DAG: Starting final orientation");
        }

        FciOrient fciOrient = new FciOrient(new DagSepsets(this.dag));
        fciOrient.setMaxPathLength(this.maxPathLength);
        fciOrient.setCompleteRuleSetUsed(this.completeRuleSetUsed);
        fciOrient.setDoDiscriminatingPathColliderRule(this.doDiscriminatingPathRule);
        fciOrient.setDoDiscriminatingPathTailRule(this.doDiscriminatingPathRule);
        fciOrient.setCompleteRuleSetUsed(this.completeRuleSetUsed);
        fciOrient.setKnowledge(this.knowledge);
        fciOrient.setVerbose(false);
        fciOrient.doFinalOrientation(graph);

        if (this.verbose) {
            System.out.println("Finishing final orientation");
        }

//        history.put(dag, graph);

        return graph;
    }

    /**
     * <p>Getter for the field <code>knowledge</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.data.Knowledge} object
     */
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * <p>Setter for the field <code>knowledge</code>.</p>
     *
     * @param knowledge a {@link edu.cmu.tetrad.data.Knowledge} object
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

    /**
     * Sets the maximum path length for some rules in the conversion.
     *
     * @param maxPathLength This length.
     * @see FciOrient
     */
    public void setMaxPathLength(int maxPathLength) {
        this.maxPathLength = maxPathLength;
    }

    /**
     * <p>Setter for the field <code>doDiscriminatingPathRule</code>.</p>
     *
     * @param doDiscriminatingPathRule a boolean
     */
    public void setDoDiscriminatingPathRule(boolean doDiscriminatingPathRule) {
        this.doDiscriminatingPathRule = doDiscriminatingPathRule;
    }

    private Graph calcAdjacencyGraph() {
        List<Node> allNodes = this.dag.getNodes();
        List<Node> measured = new ArrayList<>(allNodes);
        measured.removeIf(node -> node.getNodeType() == NodeType.LATENT);

        Graph graph = new EdgeListGraph(measured);

        for (int i = 0; i < measured.size(); i++) {
            for (int j = i + 1; j < measured.size(); j++) {
                Node n1 = measured.get(i);
                Node n2 = measured.get(j);

                if (graph.isAdjacentTo(n1, n2)) continue;

                List<Node> inducingPath = this.dag.paths().getInducingPath(n1, n2);

                boolean exists = inducingPath != null;

                if (exists) {
                    graph.addEdge(Edges.nondirectedEdge(n1, n2));
                }
            }
        }

        return graph;
    }

    private void orientUnshieldedColliders(Graph graph, Graph dag) {
        graph.reorientAllWith(Endpoint.CIRCLE);

        List<Node> allNodes = dag.getNodes();
        List<Node> measured = new ArrayList<>();

        for (Node node : allNodes) {
            if (node.getNodeType() == NodeType.MEASURED) {
                measured.add(node);
            }
        }

        for (Node b : measured) {
            List<Node> adjb = new ArrayList<>(graph.getAdjacentNodes(b));

            if (adjb.size() < 2) continue;

            for (int i = 0; i < adjb.size(); i++) {
                for (int j = i + 1; j < adjb.size(); j++) {
                    Node a = adjb.get(i);
                    Node c = adjb.get(j);

                    if (graph.isDefCollider(a, b, c)) {
                        continue;
                    }

                    if (graph.isAdjacentTo(a, c)) {
                        continue;
                    }

                    boolean found = foundCollider(dag, a, b, c);

                    if (found) {

                        if (this.verbose) {
                            System.out.println("Orienting collider " + a + "*->" + b + "<-*" + c);
                        }

                        if (FciOrient.isArrowheadAllowed(a, b, graph, knowledge) && FciOrient.isArrowheadAllowed(c, b, graph, knowledge)) {
                            graph.setEndpoint(a, b, Endpoint.ARROW);
                            graph.setEndpoint(c, b, Endpoint.ARROW);
                        }
                    }
                }
            }
        }
    }

    private boolean foundCollider(Graph dag, Node a, Node b, Node c) {
        boolean ipba = DagToPag.existsInducingPathInto(b, a, dag);
        boolean ipbc = DagToPag.existsInducingPathInto(b, c, dag);

        return ipba && ipbc;
    }
}




