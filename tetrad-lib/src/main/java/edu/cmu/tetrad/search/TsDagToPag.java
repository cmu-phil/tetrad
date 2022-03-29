///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
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

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;


/**
 * Extends Erin Korber's implementation of the Fast Causal Inference algorithm (found in FCI.java) with Jiji Zhang's
 * Augmented FCI rules (found in sec. 4.1 of Zhang's 2006 PhD dissertation, "Causal Inference and Reasoning in Causally
 * Insufficient Systems").
 * <p>
 * This class is based off a copy of FCI.java taken from the repository on 2008/12/16, revision 7306. The extension is
 * done by extending doFinalOrientation() with methods for Zhang's rules R5-R10 which implements the augmented search.
 * (By a remark of Zhang's, the rule applications can be staged in this way.)
 *
 * @author Erin Korber, June 2004
 * @author Alex Smith, December 2008
 * @author Joseph Ramsey
 * @author Choh-Man Teng
 * @author Daniel Malinsky
 */
public final class TsDagToPag {

    private final Graph dag;
//    private final IndTestDSep dsep;

    /*
     * The background knowledge.
     */
    private IKnowledge knowledge = new Knowledge2();

    /**
     * Glag for complete rule set, true if should use complete rule set, false otherwise.
     */
    private boolean completeRuleSetUsed = true;

    /**
     * The logger to use.
     */
    private final TetradLogger logger = TetradLogger.getInstance();

    /**
     * True iff verbose output should be printed.
     */
    private boolean verbose = false;
    private int maxPathLength = -1;
    private Graph truePag;

    //============================CONSTRUCTORS============================//

    /**
     * Constructs a new FCI search for the given independence test and background knowledge.
     */
    public TsDagToPag(final Graph dag) {
        this.dag = dag;
        int numLags = 1; // need to fix this!
        final List<Node> variables = dag.getNodes();
        final List<Integer> laglist = new ArrayList<>();
        final IKnowledge knowledge = new Knowledge2();
        int lag;
        for (final Node node : variables) {
            final String varName = node.getName();
            final String tmp;
            if (varName.indexOf(':') == -1) {
                lag = 0;
                laglist.add(lag);
            } else {
                tmp = varName.substring(varName.indexOf(':') + 1, varName.length());
                lag = Integer.parseInt(tmp);
                laglist.add(lag);
            }
        }
        numLags = Collections.max(laglist);
        for (final Node node : variables) {
            final String varName = node.getName();
            final String tmp;
            if (varName.indexOf(':') == -1) {
                lag = 0;
                laglist.add(lag);
            } else {
                tmp = varName.substring(varName.indexOf(':') + 1, varName.length());
                lag = Integer.parseInt(tmp);
                laglist.add(lag);
            }
            knowledge.addToTier(numLags - lag, node.getName());
        }

        this.setKnowledge(knowledge);

    }

    //========================PUBLIC METHODS==========================//

    public Graph convert() {
        this.logger.log("info", "Starting DAG to PAG_of_the_true_DAG.");
//        System.out.println("Knowledge is = " + knowledge);
        if (this.verbose) {
            System.out.println("DAG to PAG_of_the_true_DAG: Starting adjacency search");
        }

        final Graph graph = calcAdjacencyGraph();

        if (this.verbose) {
            System.out.println("DAG to PAG_of_the_true_DAG: Starting collider orientation");
        }

        orientUnshieldedColliders(graph, this.dag);

        if (this.verbose) {
            System.out.println("DAG to PAG_of_the_true_DAG: Starting final orientation");
        }

        final FciOrient fciOrient = new FciOrient(new DagSepsets(this.dag));
        System.out.println("Complete rule set is used? " + this.completeRuleSetUsed);
        fciOrient.setCompleteRuleSetUsed(this.completeRuleSetUsed);
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

    private Graph calcAdjacencyGraph() {
        final List<Node> allNodes = this.dag.getNodes();
        final List<Node> measured = new ArrayList<>();

        for (final Node node : allNodes) {
            if (node.getNodeType() == NodeType.MEASURED) {
                measured.add(node);
            }
        }

        final Graph graph = new EdgeListGraph(measured);

        for (int i = 0; i < measured.size(); i++) {
            for (int j = i + 1; j < measured.size(); j++) {
                final Node n1 = measured.get(i);
                final Node n2 = measured.get(j);

                final List<Node> inducingPath = GraphUtils.getInducingPath(n1, n2, this.dag);

                final boolean exists = inducingPath != null;

                if (exists) {
                    graph.addEdge(Edges.nondirectedEdge(n1, n2));
                }
            }
        }

        return graph;
    }

    private void orientUnshieldedColliders(final Graph graph, final Graph dag) {
        graph.reorientAllWith(Endpoint.CIRCLE);

        final List<Node> allNodes = dag.getNodes();
        final List<Node> measured = new ArrayList<>();

        for (final Node node : allNodes) {
            if (node.getNodeType() == NodeType.MEASURED) {
                measured.add(node);
            }
        }

        for (final Node b : measured) {
            final List<Node> adjb = graph.getAdjacentNodes(b);

            if (adjb.size() < 2) continue;

            for (int i = 0; i < adjb.size(); i++) {
                for (int j = i + 1; j < adjb.size(); j++) {
                    final Node a = adjb.get(i);
                    final Node c = adjb.get(j);

                    if (graph.isDefCollider(a, b, c)) {
                        continue;
                    }

                    if (graph.isAdjacentTo(a, c)) {
                        continue;
                    }

                    final boolean found = foundCollider(dag, a, b, c);

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

    private boolean foundCollider(final Graph dag, final Node a, final Node b, final Node c) {
        final boolean ipba = existsInducingPathInto(b, a, dag, this.knowledge);
        final boolean ipbc = existsInducingPathInto(b, c, dag, this.knowledge);

        if (!(ipba && ipbc)) {
            printTrueDefCollider(a, b, c, false);
            return false;
        }

        printTrueDefCollider(a, b, c, true);

        return true;
    }

    private void printTrueDefCollider(final Node a, final Node b, final Node c, final boolean found) {
        if (this.truePag != null) {
            final boolean defCollider = this.truePag.isDefCollider(a, b, c);

            if (this.verbose) {
                if (!found && defCollider) {
                    System.out.println("FOUND COLLIDER FCI");
                } else if (found && !defCollider) {
                    System.out.println("DIDN'T FIND COLLIDER FCI");
                }
            }
        }
    }

    public static boolean existsInducingPathInto(final Node x, final Node y, final Graph graph, final IKnowledge knowledge) {
        if (x.getNodeType() != NodeType.MEASURED) throw new IllegalArgumentException();
        if (y.getNodeType() != NodeType.MEASURED) throw new IllegalArgumentException();

        final LinkedList<Node> path = new LinkedList<>();
        path.add(x);

        for (final Node b : graph.getAdjacentNodes(x)) {
            final Edge edge = graph.getEdge(x, b);
            if (!edge.pointsTowards(x)) continue;

            if (existsInducingPathVisitts(graph, x, b, x, y, path, knowledge)) {
                return true;
            }
        }

        return false;
    }

//    private static boolean existsInducingPathVisit(Graph graph, Node a, Node b, Node x, Node y,
//                                                   LinkedList<Node> path) {
//        if (b == y) {
//            path.addLast(b);
//            return true;
//        }
//
//        if (path.contains(b)) {
//            return false;
//        }
//
//        path.addLast(b);
//
//        for (Node c : graph.getAdjacentNodes(b)) {
//            if (c == a) continue;
//
//            if (b.getNodeType() == NodeType.MEASURED) {
//                if (!graph.isDefCollider(a, b, c)) continue;
//
//                if (!(graph.isAncestorOf(b, x) || graph.isAncestorOf(b, y))) {
//                    continue;
//                }
//            }
//
//            if (DataGraphUtils.existsInducingPathVisit(graph, b, c, x, y, path)) {
//                return true;
//            }
//        }
//
//        path.removeLast();
//        return false;
//    }


    public IKnowledge getKnowledge() {
        return this.knowledge;
    }

    public void setKnowledge(final IKnowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException();
        }

        this.knowledge = knowledge;
    }

    /**
     * @return true if Zhang's complete rule set should be used, false if only R1-R4 (the rule set of the original FCI)
     * should be used. False by default.
     */
    public boolean isCompleteRuleSetUsed() {
        return this.completeRuleSetUsed;
    }

    /**
     * @param completeRuleSetUsed set to true if Zhang's complete rule set should be used, false if only R1-R4 (the rule
     *                            set of the original FCI) should be used. False by default.
     */
    public void setCompleteRuleSetUsed(final boolean completeRuleSetUsed) {
        this.completeRuleSetUsed = completeRuleSetUsed;
    }

    /**
     * True iff verbose output should be printed.
     */
    public boolean isVerbose() {
        return this.verbose;
    }

    public void setVerbose(final boolean verbose) {
        this.verbose = verbose;
    }

    public int getMaxPathLength() {
        return this.maxPathLength;
    }

    public void setMaxPathLength(final int maxPathLength) {
        this.maxPathLength = maxPathLength;
    }

    public Graph getTruePag() {
        return this.truePag;
    }

    public void setTruePag(final Graph truePag) {
        this.truePag = truePag;
    }


    public static boolean existsInducingPathVisitts(final Graph graph, final Node a, final Node b, final Node x, final Node y,
                                                    final LinkedList<Node> path, final IKnowledge knowledge) {
        if (path.contains(b)) {
            return false;
        }

        path.addLast(b);

        if (b == y) return true;

        for (final Node c : graph.getAdjacentNodes(b)) {
            if (c == a) continue;

            if (b.getNodeType() == NodeType.MEASURED) {
                if (!graph.isDefCollider(a, b, c)) continue;

            }

            if (graph.isDefCollider(a, b, c)) {
                if (!((graph.isAncestorOf(b, x) && !knowledge.isForbidden(b.getName(), x.getName())) ||
                        (graph.isAncestorOf(b, y) && !knowledge.isForbidden(b.getName(), x.getName())))) {
                    continue;
                }
            }

            if (existsInducingPathVisitts(graph, b, c, x, y, path, knowledge)) {
                return true;
            }
        }

        path.removeLast();
        return false;
    }


}




