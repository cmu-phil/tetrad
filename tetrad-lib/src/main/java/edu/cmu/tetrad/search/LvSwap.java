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
package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.TetradLogger;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Does BOSS, followed by two swap rules, then final FCI orientation.
 *
 * @author jdramsey
 */
public final class LvSwap implements GraphSearch {

    // The score used, if GS is used to build DAGs.
    private final Score score;

    // The logger to use.
    private final TetradLogger logger = TetradLogger.getInstance();

    // The covariance matrix being searched over, if continuous data is supplied. This is
    // not used by the algorithm beut can be retrieved by another method if desired
    ICovarianceMatrix covarianceMatrix;

    // The test used if Pearl's method is used ot build DAGs
    private IndependenceTest test;

    // Flag for complete rule set, true if you should use complete rule set, false otherwise.
    private boolean completeRuleSetUsed = true;

    // The maximum length for any discriminating path. -1 if unlimited; otherwise, a positive integer.
    private int maxPathLength = -1;

    // True iff verbose output should be printed.
    private boolean verbose;

    // The print stream that output is directed to.
    private PrintStream out = System.out;

    // GRaSP parameters
    private int numStarts = 1;
    private int depth = -1;
    private boolean useRaskuttiUhler;
    private boolean useDataOrder = true;
    private boolean useScore = true;
    private boolean doDiscriminatingPathRule = true;
    private Knowledge knowledge = new Knowledge();

    //============================CONSTRUCTORS============================//
    public LvSwap(IndependenceTest test, Score score) {
        this.test = test;
        this.score = score;
    }

    //========================PUBLIC METHODS==========================//
    public Graph search() {
        this.logger.log("info", "Starting FCI algorithm.");
        this.logger.log("info", "Independence test = " + getTest() + ".");

        TeyssierScorer scorer = new TeyssierScorer(test, score);

        Boss boss = new Boss(scorer);
        boss.setAlgType(Boss.AlgType.BOSS2);
        boss.setUseScore(useScore);
        boss.setUseRaskuttiUhler(useRaskuttiUhler);
        boss.setUseDataOrder(useDataOrder);
        boss.setDepth(depth);
        boss.setNumStarts(numStarts);
        boss.setKnowledge(knowledge);
        boss.setVerbose(true);

        List<Node> variables = new ArrayList<>(this.score.getVariables());
        variables.removeIf(node -> node.getNodeType() == NodeType.LATENT);

        boss.bestOrder(variables);
        Graph G1 = boss.getGraph(true);

        Knowledge knowledge2 = new Knowledge(knowledge);
//        addForbiddenReverseEdgesForDirectedEdges(SearchGraphUtils.cpdagForDag(G1), knowledge2);

        Graph G2 = new EdgeListGraph(G1);
        keepArrows(G2);

        Graph G3 = new EdgeListGraph(G2);

        G3 = swap1(G3, scorer, knowledge2);
        G3 = swap2(G3, scorer, knowledge2);
        G3 = swap1(G3, scorer, knowledge2);
        G3 = swap2(G3, scorer, knowledge2);

        // Do final FCI orientation rules app
        Graph G4 = new EdgeListGraph(G3);
        finalOrientation(knowledge2, G4);
        G4.setPag(true);

        return G4;
    }

    private void finalOrientation(Knowledge knowledge2, Graph G4) {
        SepsetProducer sepsets = new SepsetsGreedy(G4, test, null, depth);
        FciOrient fciOrient = new FciOrient(sepsets);
        fciOrient.setCompleteRuleSetUsed(this.completeRuleSetUsed);
        fciOrient.setDoDiscriminatingPathRule(this.doDiscriminatingPathRule);
        fciOrient.setMaxPathLength(this.maxPathLength);
        fciOrient.setKnowledge(knowledge2);
        fciOrient.setVerbose(true);
        fciOrient.doFinalOrientation(G4);
    }

//    public static void retainUnshieldedColliders(Graph graph, Knowledge knowledge) {
//        Graph orig = new EdgeListGraph(graph);
//        graph.reorientAllWith(Endpoint.CIRCLE);
//        List<Node> nodes = graph.getNodes();
//
//        for (Node b : nodes) {
//            List<Node> adjacentNodes = graph.getAdjacentNodes(b);
//
//            if (adjacentNodes.size() < 2) {
//                continue;
//            }
//
//            ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
//            int[] combination;
//
//            while ((combination = cg.next()) != null) {
//                Node a = adjacentNodes.get(combination[0]);
//                Node c = adjacentNodes.get(combination[1]);
//
//                if (orig.isDefCollider(a, b, c) && !orig.isAdjacentTo(a, c)) {
//                    if (FciOrient.isArrowpointAllowed(a, b, graph, knowledge)
//                            && FciOrient.isArrowpointAllowed(c, b, graph, knowledge)) {
//                        graph.setEndpoint(a, b, Endpoint.ARROW);
//                        graph.setEndpoint(c, b, Endpoint.ARROW);
//                    }
//                }
//            }
//        }
//    }

    private Graph swap1(Graph graph, TeyssierScorer scorer, Knowledge knowledge) {
        graph = new EdgeListGraph(graph);
        List<Node> nodes = graph.getNodes();

        for (Node x : nodes) {
            for (Node y : nodes) {
                for (Node z : nodes) {
                    for (Node w : nodes) {
                        if (scorer.index(x) == scorer.index(y)) continue;
                        if (scorer.index(x) == scorer.index(z)) continue;
                        if (scorer.index(x) == scorer.index(w)) continue;
                        if (scorer.index(y) == scorer.index(z)) continue;
                        if (scorer.index(y) == scorer.index(w)) continue;
                        if (scorer.index(z) == scorer.index(w)) continue;

                        if (config(graph, z, x, y, w)) {
                            if (FciOrient.isArrowpointAllowed(w, y, graph, knowledge)) {
                                if (FciOrient.isArrowpointAllowed(x, y, graph, knowledge)) {
                                    scorer.bookmark();
                                    scorer.swap(x, y);

                                    if (config(scorer, w, y, x, z)) {
                                        scorer.goToBookmark();


                                        if (graph.isAdjacentTo(w, x)) {
                                            Edge edge = graph.getEdge(w, x);
                                            graph.removeEdge(edge);
                                            System.out.println("Swap removing : " + edge);
                                        }

                                        graph.setEndpoint(w, y, Endpoint.ARROW);
                                        graph.setEndpoint(x, y, Endpoint.ARROW);

                                        System.out.println("Swap orienting " + GraphUtils.pathString(graph, z, x, y, w));
                                    }

                                    scorer.goToBookmark();
                                }
                            }
                        }


                        if (config(graph, w, y, x, z)) {
                            if (FciOrient.isArrowpointAllowed(w, y, graph, knowledge)) {
                                if (FciOrient.isArrowpointAllowed(x, y, graph, knowledge)) {
                                    scorer.bookmark();
                                    scorer.swap(x, y);

                                    if (config(scorer, z, x, y, w)) {
                                        scorer.goToBookmark();

                                        if (graph.isAdjacentTo(z, y)) {
                                            Edge edge = graph.getEdge(z, y);
                                            graph.removeEdge(edge);
                                            System.out.println("Swap removing : " + edge);
                                        }

                                        if (graph.isAdjacentTo(z, x) && graph.isAdjacentTo(y, x)) {
                                            graph.setEndpoint(z, x, Endpoint.ARROW);
                                            graph.setEndpoint(y, x, Endpoint.ARROW);
                                        }

                                        System.out.println("Swap orienting " + GraphUtils.pathString(graph, w, y, x, z));
                                    }

                                    scorer.goToBookmark();
                                }
                            }
                        }
                    }
                }
            }
        }

        return graph;
    }

    private Graph swap2(Graph graph, TeyssierScorer scorer, Knowledge knowledge) {
        graph = new EdgeListGraph(graph);
        List<Node> nodes = graph.getNodes();

        for (Node x : nodes) {
            for (Node y : nodes) {
                for (Node z : nodes) {
                    if (scorer.index(x) == scorer.index(y)) continue;
                    if (scorer.index(x) == scorer.index(z)) continue;
                    if (scorer.index(y) == scorer.index(z)) continue;

                    if (config2(graph, z, x, y, true)) {
                        if (FciOrient.isArrowpointAllowed(z, x, graph, knowledge)) {
                            if (FciOrient.isArrowpointAllowed(y, x, graph, knowledge)) {
                                scorer.bookmark();
                                scorer.swap(x, y);

                                if (config2(scorer, z, x, y, false)) {
                                    if (graph.isAdjacentTo(z, y)) {
                                        Edge edge = graph.getEdge(z, y);
                                        graph.removeEdge(edge);
                                        System.out.println("Swap removing : " + edge);
                                    }

                                    graph.setEndpoint(z, x, Endpoint.ARROW);
                                    graph.setEndpoint(y, z, Endpoint.ARROW);

                                    System.out.println("Swap orienting " + GraphUtils.pathString(graph, z, x, y));
                                }

                                scorer.goToBookmark();
                            }
                        }
                    }

                    if (config2(graph, z, x, y, false)) {
                        if (FciOrient.isArrowpointAllowed(z, x, graph, knowledge)) {
                            if (FciOrient.isArrowpointAllowed(y, x, graph, knowledge)) {
                                scorer.bookmark();
                                scorer.swap(x, y);

                                if (config2(scorer, z, x, y, true)) {
                                    graph.setEndpoint(z, x, Endpoint.ARROW);
                                    graph.setEndpoint(y, z, Endpoint.ARROW);

                                    System.out.println("Swap orienting " + GraphUtils.pathString(graph, z, x, y));
                                }

                                scorer.goToBookmark();
                            }
                        }
                    }
                }
            }
        }

        return graph;
    }

    private void keepArrows(Graph graph) {
        Graph graph2 = new EdgeListGraph(graph);
        graph.reorientAllWith(Endpoint.CIRCLE);
//        fciOrientbk(this.knowledge, this.graph, this.graph.getNodes());

        for (Edge edge : graph2.getEdges()) {
            if (edge.getEndpoint1() == Endpoint.ARROW) {
                graph.setEndpoint(edge.getNode2(), edge.getNode1(), Endpoint.ARROW);
            }

            if (edge.getEndpoint2() == Endpoint.ARROW) {
                graph.setEndpoint(edge.getNode1(), edge.getNode2(), Endpoint.ARROW);
            }
        }
    }

    private static boolean config(TeyssierScorer scorer, Node z, Node x, Node y, Node w) {
        if (scorer.adjacent(z, x) && scorer.adjacent(x, y) && scorer.adjacent(y, w)) {
            if (scorer.adjacent(w, x) && !scorer.adjacent(z, y)) {
                return scorer.collider(z, x, y) && scorer.collider(z, x, w);
            }
        }

        return false;
    }

    private static boolean config2(Graph graph, Node z, Node x, Node y, boolean flag) {
        if (graph.isAdjacentTo(x, y)) {
            return graph.isAdjacentTo(z, x) && (flag && graph.isAdjacentTo(z, y));
        }

        return false;
    }

    private static boolean config2(TeyssierScorer scorer, Node z, Node x, Node y, boolean flag) {
        if (scorer.adjacent(x, y)) {
            return scorer.adjacent(z, x) && (flag && scorer.adjacent(z, y));
        }

        return false;
    }

    private static boolean config(Graph graph, Node z, Node x, Node y, Node w) {
        if (graph.isAdjacentTo(z, x) && graph.isAdjacentTo(x, y) && graph.isAdjacentTo(y, w)) {
            if (graph.isAdjacentTo(w, x) && !graph.isAdjacentTo(z, y)) {
                return graph.isDefCollider(z, x, y) && graph.isDefCollider(z, x, w);
            }
        }

        return false;
    }

    /**
     * @return true if Zhang's complete rule set should be used, false if only
     * R1-R4 (the rule set of the original FCI) should be used. False by
     * default.
     */
    public boolean isCompleteRuleSetUsed() {
        return this.completeRuleSetUsed;
    }

    /**
     * @param completeRuleSetUsed set to true if Zhang's complete rule set
     *                            should be used, false if only R1-R4 (the rule set of the original FCI)
     *                            should be used. False by default.
     */
    public void setCompleteRuleSetUsed(boolean completeRuleSetUsed) {
        this.completeRuleSetUsed = completeRuleSetUsed;
    }

    /**
     * @return the maximum length of any discriminating path, or -1 of
     * unlimited.
     */
    public int getMaxPathLength() {
        return this.maxPathLength;
    }

    /**
     * @param maxPathLength the maximum length of any discriminating path, or -1
     *                      if unlimited.
     */
    public void setMaxPathLength(int maxPathLength) {
        if (maxPathLength < -1) {
            throw new IllegalArgumentException("Max path length must be -1 (unlimited) or >= 0: " + maxPathLength);
        }

        this.maxPathLength = maxPathLength;
    }

    /**
     * True iff verbose output should be printed.
     */
    public boolean isVerbose() {
        return this.verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * The independence test.
     */
    public IndependenceTest getTest() {
        return this.test;
    }

    public void setTest(IndependenceTest test) {
        this.test = test;
    }

    public ICovarianceMatrix getCovMatrix() {
        return this.covarianceMatrix;
    }

    public ICovarianceMatrix getCovarianceMatrix() {
        return this.covarianceMatrix;
    }

    public void setCovarianceMatrix(ICovarianceMatrix covarianceMatrix) {
        this.covarianceMatrix = covarianceMatrix;
    }

    public PrintStream getOut() {
        return this.out;
    }

    public void setOut(PrintStream out) {
        this.out = out;
    }

    public void setNumStarts(int numStarts) {
        this.numStarts = numStarts;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

    public void setUseRaskuttiUhler(boolean useRaskuttiUhler) {
        this.useRaskuttiUhler = useRaskuttiUhler;
    }

    public void setUseScore(boolean useScore) {
        this.useScore = useScore;
    }

    public void setUseDataOrder(boolean useDataOrder) {
        this.useDataOrder = useDataOrder;
    }

    public void setDoDiscriminatingPathRule(boolean doDiscriminatingPathRule) {
        this.doDiscriminatingPathRule = doDiscriminatingPathRule;
    }

    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }
}
