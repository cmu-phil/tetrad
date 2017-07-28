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

import java.util.*;


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
 */
public final class DagToPag {

    private final Graph dag;
//    private final IndTestDSep dsep;

    /*
     * The background knowledge.
     */
    private IKnowledge knowledge = new Knowledge2();

    /**
     * Glag for complete rule set, true if should use complete rule set, false otherwise.
     */
    private boolean completeRuleSetUsed = false;

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
    public DagToPag(Graph dag) {
        this.dag = dag;
    }

    //========================PUBLIC METHODS==========================//

    public Graph convert() {
        if (dag == null) throw new NullPointerException();

        logger.log("info", "Starting DAG to PAG_of_the_true_DAG.");

        if (verbose) {
            System.out.println("DAG to PAG_of_the_true_DAG: Starting adjacency search");
        }

        Graph graph = calcAdjacencyGraph();

        if (verbose) {
            System.out.println("DAG to PAG_of_the_true_DAG: Starting collider orientation");
        }

        orientUnshieldedColliders2(graph, dag);

        if (verbose) {
            System.out.println("DAG to PAG_of_the_true_DAG: Starting final orientation");
        }

        final FciOrient fciOrient = new FciOrient(new DagSepsets(dag));
        fciOrient.setCompleteRuleSetUsed(completeRuleSetUsed);
        fciOrient.skipDiscriminatingPathRule(false);
        fciOrient.setChangeFlag(false);
        fciOrient.setMaxPathLength(maxPathLength);
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
            addAdjacencies(measured.get(i), dag, graph);
        }

        return graph;
    }

    public static Set<Node> addAdjacencies(Node x, Graph dag, Graph builtGraph) {
        if (x.getNodeType() != NodeType.MEASURED) throw new IllegalArgumentException();

        final LinkedList<Node> path = new LinkedList<>();
        path.add(x);

        Set<Node> induced = new HashSet<>();

        for (Node b : dag.getAdjacentNodes(x)) {
            collectInducedNodesVisit2(dag, x, b, path, builtGraph);
        }

        return induced;
    }

    public static void collectInducedNodesVisit2(Graph dag, Node x, Node b, LinkedList<Node> path,
                                                 Graph builtGraph) {

        if (path.contains(b)) {
            return;
        }

        path.addLast(b);

        if (b.getNodeType() == NodeType.MEASURED && path.size() >= 2) {
            Node y = path.getLast();

            for (int i = 0; i < path.size() - 2; i++) {
                Node _a = path.get(i);
                Node _b = path.get(i + 1);
                Node _c = path.get(i + 2);

                if (_b.getNodeType() == NodeType.MEASURED) {
                    if (!dag.isDefCollider(_a, _b, _c)) {
                        path.removeLast();
                        return;
                    }
                }

                if (dag.isDefCollider(_a, _b, _c)) {
                    if (!(dag.isAncestorOf(_b, x) || dag.isAncestorOf(_b, y))) {
                        path.removeLast();
                        return;
                    }
                }
            }

            if (!builtGraph.isAdjacentTo(x, b)) {
                builtGraph.addEdge(Edges.nondirectedEdge(x, b));
            }
        }

        for (Node c : dag.getAdjacentNodes(b)) {
            collectInducedNodesVisit2(dag, x, c, path, builtGraph);
        }

        path.removeLast();
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

    private void orientUnshieldedColliders2(Graph graph, Graph dag) {
//        graph.reorientAllWith(Endpoint.CIRCLE);

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

//                    List<Node> d = new ArrayList<>();
//                    d.add(a);
//                    d.add(c);
//
//                    List<Node> anc = dag.getAncestors(d);

                    if (!graph.isAdjacentTo(a, c) && !dag.isAncestorOf(b, a) && !dag.isAncestorOf(b, c)) {// !anc.contains(b)) {

//                        if (verbose) {
//                            System.out.println("Orienting collider " + a + "*->" + b + "<-*" + c);
//                        }

                        graph.setEndpoint(a, b, Endpoint.ARROW);
                        graph.setEndpoint(c, b, Endpoint.ARROW);
                    }
                }
            }
        }
    }

    private boolean foundCollider(Graph dag, Node a, Node b, Node c) {
        boolean ipba = existsInducingPathInto(b, a, dag);
        boolean ipbc = existsInducingPathInto(b, c, dag);

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

    public static boolean existsInducingPathInto(Node x, Node y, Graph graph) {
        if (x.getNodeType() != NodeType.MEASURED) throw new IllegalArgumentException();
        if (y.getNodeType() != NodeType.MEASURED) throw new IllegalArgumentException();

        final LinkedList<Node> path = new LinkedList<>();
        path.add(x);

        for (Node b : graph.getAdjacentNodes(x)) {
            Edge edge = graph.getEdge(x, b);
            if (!edge.pointsTowards(x)) continue;

            if (GraphUtils.existsInducingPathVisit(graph, x, b, x, y, path)) {
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
}




