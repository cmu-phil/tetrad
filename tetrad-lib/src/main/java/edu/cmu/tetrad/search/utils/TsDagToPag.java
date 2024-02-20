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
import edu.cmu.tetrad.search.Fci;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;


/**
 * Finds the PAG to which a DAG belongs, for a time series model.
 *
 * @author danielmalinsky
 * @version $Id: $Id
 * @see Fci
 * @see DagToPag
 */
public final class TsDagToPag {

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
    private Graph truePag;
    private boolean doDiscriminatingPathRule = false;


    /**
     * Constructs a new FCI search for the given independence test and background knowledge.
     *
     * @param dag a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public TsDagToPag(Graph dag) {
        this.dag = dag;
        int numLags = 1; // need to fix this!
        List<Node> variables = dag.getNodes();
        List<Integer> laglist = new ArrayList<>();
        Knowledge knowledge = new Knowledge();
        int lag;
        for (Node node : variables) {
            String varName = node.getName();
            String tmp;
            if (varName.indexOf(':') == -1) {
                lag = 0;
            } else {
                tmp = varName.substring(varName.indexOf(':') + 1);
                lag = Integer.parseInt(tmp);
            }
            laglist.add(lag);
        }
        numLags = Collections.max(laglist);
        for (Node node : variables) {
            String varName = node.getName();
            String tmp;
            if (varName.indexOf(':') == -1) {
                lag = 0;
            } else {
                tmp = varName.substring(varName.indexOf(':') + 1);
                lag = Integer.parseInt(tmp);
            }
            laglist.add(lag);
            knowledge.addToTier(numLags - lag, node.getName());
        }

        this.setKnowledge(knowledge);

    }

    /**
     * <p>existsInducingPathInto.</p>
     *
     * @param x         a {@link edu.cmu.tetrad.graph.Node} object
     * @param y         a {@link edu.cmu.tetrad.graph.Node} object
     * @param graph     a {@link edu.cmu.tetrad.graph.Graph} object
     * @param knowledge a {@link edu.cmu.tetrad.data.Knowledge} object
     * @return a boolean
     */
    public static boolean existsInducingPathInto(Node x, Node y, Graph graph, Knowledge knowledge) {
        if (x.getNodeType() != NodeType.MEASURED) throw new IllegalArgumentException();
        if (y.getNodeType() != NodeType.MEASURED) throw new IllegalArgumentException();

        LinkedList<Node> path = new LinkedList<>();
        path.add(x);

        for (Node b : graph.getAdjacentNodes(x)) {
            Edge edge = graph.getEdge(x, b);
            if (!edge.pointsTowards(x)) continue;

            if (TsDagToPag.existsInducingPathVisitts(graph, x, b, x, y, path, knowledge)) {
                return true;
            }
        }

        return false;
    }

    /**
     * <p>existsInducingPathVisitts.</p>
     *
     * @param graph     a {@link edu.cmu.tetrad.graph.Graph} object
     * @param a         a {@link edu.cmu.tetrad.graph.Node} object
     * @param b         a {@link edu.cmu.tetrad.graph.Node} object
     * @param x         a {@link edu.cmu.tetrad.graph.Node} object
     * @param y         a {@link edu.cmu.tetrad.graph.Node} object
     * @param path      a {@link java.util.LinkedList} object
     * @param knowledge a {@link edu.cmu.tetrad.data.Knowledge} object
     * @return a boolean
     */
    public static boolean existsInducingPathVisitts(Graph graph, Node a, Node b, Node x, Node y,
                                                    LinkedList<Node> path, Knowledge knowledge) {
        if (path.contains(b)) {
            return false;
        }

        path.addLast(b);

        if (b == y) return true;

        for (Node c : graph.getAdjacentNodes(b)) {
            if (c == a) continue;

            if (b.getNodeType() == NodeType.MEASURED) {
                if (!graph.isDefCollider(a, b, c)) continue;

            }

            if (graph.isDefCollider(a, b, c)) {
                if (!((graph.paths().isAncestorOf(b, x) && !knowledge.isForbidden(b.getName(), x.getName())) ||
                        (graph.paths().isAncestorOf(b, y) && !knowledge.isForbidden(b.getName(), x.getName())))) {
                    continue;
                }
            }

            if (TsDagToPag.existsInducingPathVisitts(graph, b, c, x, y, path, knowledge)) {
                return true;
            }
        }

        path.removeLast();
        return false;
    }

    /**
     * <p>convert.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Graph convert() {
        TetradLogger.getInstance().forceLogMessage("Starting DAG to PAG_of_the_true_DAG.");
        //        System.out.println("Knowledge is = " + knowledge);
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
        System.out.println("Complete rule set is used? " + this.completeRuleSetUsed);
        fciOrient.setCompleteRuleSetUsed(this.completeRuleSetUsed);
        fciOrient.setDoDiscriminatingPathColliderRule(this.doDiscriminatingPathRule);
        fciOrient.setDoDiscriminatingPathTailRule(this.doDiscriminatingPathRule);
        fciOrient.setChangeFlag(false);
        fciOrient.setMaxPathLength(this.maxPathLength);
        fciOrient.setKnowledge(this.knowledge);
        fciOrient.ruleR0(graph);
        fciOrient.doFinalOrientation(graph);

        if (this.verbose) {
            System.out.println("Finishing final orientation");
        }

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
     * True iff verbose output should be printed.
     *
     * @return a boolean
     */
    public boolean isVerbose() {
        return this.verbose;
    }

    /**
     * <p>Setter for the field <code>verbose</code>.</p>
     *
     * @param verbose a boolean
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * <p>Getter for the field <code>maxPathLength</code>.</p>
     *
     * @return a int
     */
    public int getMaxPathLength() {
        return this.maxPathLength;
    }

    /**
     * <p>Setter for the field <code>maxPathLength</code>.</p>
     *
     * @param maxPathLength a int
     */
    public void setMaxPathLength(int maxPathLength) {
        this.maxPathLength = maxPathLength;
    }

    /**
     * <p>Getter for the field <code>truePag</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Graph getTruePag() {
        return this.truePag;
    }

    /**
     * <p>Setter for the field <code>truePag</code>.</p>
     *
     * @param truePag a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public void setTruePag(Graph truePag) {
        this.truePag = truePag;
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
        List<Node> measured = new ArrayList<>();

        for (Node node : allNodes) {
            if (node.getNodeType() == NodeType.MEASURED) {
                measured.add(node);
            }
        }

        Graph graph = new EdgeListGraph(measured);

        for (int i = 0; i < measured.size(); i++) {
            for (int j = i + 1; j < measured.size(); j++) {
                Node n1 = measured.get(i);
                Node n2 = measured.get(j);

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

                        graph.setEndpoint(a, b, Endpoint.ARROW);
                        graph.setEndpoint(c, b, Endpoint.ARROW);
                    }
                }
            }
        }
    }

    private boolean foundCollider(Graph dag, Node a, Node b, Node c) {
        boolean ipba = TsDagToPag.existsInducingPathInto(b, a, dag, this.knowledge);
        boolean ipbc = TsDagToPag.existsInducingPathInto(b, c, dag, this.knowledge);

        if (!(ipba && ipbc)) {
            printTrueDefCollider(a, b, c, false);
            return false;
        }

        printTrueDefCollider(a, b, c, true);

        return true;
    }

    private void printTrueDefCollider(Node a, Node b, Node c, boolean found) {
        if (this.truePag != null) {
            boolean defCollider = this.truePag.isDefCollider(a, b, c);

            if (this.verbose) {
                if (!found && defCollider) {
                    System.out.println("FOUND COLLIDER FCI");
                } else if (found && !defCollider) {
                    System.out.println("DIDN'T FIND COLLIDER FCI");
                }
            }
        }
    }

}




