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
import java.util.LinkedList;
import java.util.List;
import java.util.Collections;


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
    private TetradLogger logger = TetradLogger.getInstance();

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
    public TsDagToPag(Graph dag) {
        this.dag = dag;
        int numLags = 1; // need to fix this!
        List<Node> variables = dag.getNodes();
        List<Integer> laglist = new ArrayList<>();
        IKnowledge knowledge = new Knowledge2();
        int lag;
        for (Node node : variables) {
            String varName = node.getName();
            String tmp;
            if(varName.indexOf(':')== -1){
                lag = 0;
                laglist.add(lag);
            } else {
                tmp = varName.substring(varName.indexOf(':')+1,varName.length());
                lag = Integer.parseInt(tmp);
                laglist.add(lag);
            }
        }
        numLags = Collections.max(laglist);
        for (Node node : variables) {
            String varName = node.getName();
            String tmp;
            if(varName.indexOf(':')== -1){
                lag = 0;
                laglist.add(lag);
            } else {
                tmp = varName.substring(varName.indexOf(':')+1,varName.length());
                lag = Integer.parseInt(tmp);
                laglist.add(lag);
            }
            knowledge.addToTier(numLags - lag, node.getName());
        }

        this.setKnowledge(knowledge);

    }

    //========================PUBLIC METHODS==========================//

    public Graph convert() {
        logger.log("info", "Starting DAG to PAG_of_the_true_DAG.");
//        System.out.println("Knowledge is = " + knowledge);
        if (verbose) {
            System.out.println("DAG to PAG_of_the_true_DAG: Starting adjacency search");
        }

        Graph graph = calcAdjacencyGraph();

        if (verbose) {
            System.out.println("DAG to PAG_of_the_true_DAG: Starting collider orientation");
        }

        orientUnshieldedColliders(graph, dag);

        if (verbose) {
            System.out.println("DAG to PAG_of_the_true_DAG: Starting final orientation");
        }

        final FciOrient fciOrient = new FciOrient(new DagSepsets(dag));
        System.out.println("Complete rule set is used? " + completeRuleSetUsed);
        fciOrient.setCompleteRuleSetUsed(completeRuleSetUsed);
        fciOrient.setChangeFlag(false);
        fciOrient.setMaxPathLength(maxPathLength);
        fciOrient.setKnowledge(knowledge);
        fciOrient.ruleR0(graph);
        fciOrient.doFinalOrientation(graph);

        if (verbose) {
            System.out.println("Finishing final orientation");
        }

        return graph;
    }

    private Graph calcAdjacencyGraph() {
        List<Node> allNodes = dag.getNodes();
        List<Node> measured = new ArrayList<>();

        for (Node node : allNodes) {
            if (node.getNodeType() == NodeType.MEASURED) {
                measured.add(node);
            }
        }

        Graph graph = new EdgeListGraphSingleConnections(measured);

        for (int i = 0; i < measured.size(); i++) {
            for (int j = i + 1; j < measured.size(); j++) {
                Node n1 = measured.get(i);
                Node n2 = measured.get(j);

                final List<Node> inducingPath = GraphUtils.getInducingPath(n1, n2, dag);

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
            List<Node> adjb = graph.getAdjacentNodes(b);

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

                        if (verbose) {
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
        boolean ipba = existsInducingPathInto(b, a, dag, knowledge);
        boolean ipbc = existsInducingPathInto(b, c, dag, knowledge);

        if (!(ipba && ipbc)) {
            printTrueDefCollider(a, b, c, false);
            return false;
        }

        printTrueDefCollider(a, b, c, true);

        return true;
    }

    private void printTrueDefCollider(Node a, Node b, Node c, boolean found) {
        if (truePag != null) {
            final boolean defCollider = truePag.isDefCollider(a, b, c);

            if (verbose) {
                if (!found && defCollider) {
                    System.out.println("FOUND COLLIDER FCI");
                } else if (found && !defCollider) {
                    System.out.println("DIDN'T FIND COLLIDER FCI");
                }
            }
        }
    }

    public static boolean existsInducingPathInto(Node x, Node y, Graph graph, IKnowledge knowledge) {
        if (x.getNodeType() != NodeType.MEASURED) throw new IllegalArgumentException();
        if (y.getNodeType() != NodeType.MEASURED) throw new IllegalArgumentException();

        final LinkedList<Node> path = new LinkedList<>();
        path.add(x);

        for (Node b : graph.getAdjacentNodes(x)) {
            Edge edge = graph.getEdge(x, b);
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
        return knowledge;
    }

    public void setKnowledge(IKnowledge knowledge) {
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
        return completeRuleSetUsed;
    }

    /**
     * @param completeRuleSetUsed set to true if Zhang's complete rule set should be used, false if only R1-R4 (the rule
     *                            set of the original FCI) should be used. False by default.
     */
    public void setCompleteRuleSetUsed(boolean completeRuleSetUsed) {
        this.completeRuleSetUsed = completeRuleSetUsed;
    }

    /**
     * True iff verbose output should be printed.
     */
    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public int getMaxPathLength() {
        return maxPathLength;
    }

    public void setMaxPathLength(int maxPathLength) {
        this.maxPathLength = maxPathLength;
    }

    public Graph getTruePag() {
        return truePag;
    }

    public void setTruePag(Graph truePag) {
        this.truePag = truePag;
    }


    public static boolean existsInducingPathVisitts(Graph graph, Node a, Node b, Node x, Node y,
                                                  LinkedList<Node> path, IKnowledge knowledge) {
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
                if (!((graph.isAncestorOf(b, x) && !knowledge.isForbidden(b.getName(),x.getName())) ||
                        (graph.isAncestorOf(b, y) && !knowledge.isForbidden(b.getName(),x.getName())))) {
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




