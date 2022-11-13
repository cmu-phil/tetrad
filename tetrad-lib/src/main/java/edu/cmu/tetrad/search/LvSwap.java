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
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.TetradLogger;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
        retainUnshieldedColliders(G2, knowledge2);

        Graph G3 = new EdgeListGraph(G2);

        Set<Edge> removed = new HashSet<>();

        G3 = swapOrient(G3, scorer, knowledge2, removed);
        G3 = swapRemove(G3, removed);

        // Do final FCI orientation rules app
        Graph G4 = new EdgeListGraph(G3);

        retainUnshieldedColliders(G4, knowledge2);

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

    public static void retainUnshieldedColliders(Graph graph, Knowledge knowledge) {
        Graph orig = new EdgeListGraph(graph);
        graph.reorientAllWith(Endpoint.CIRCLE);
        List<Node> nodes = graph.getNodes();

        for (Node b : nodes) {
            List<Node> adjacentNodes = graph.getAdjacentNodes(b);

            if (adjacentNodes.size() < 2) {
                continue;
            }

            ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                Node a = adjacentNodes.get(combination[0]);
                Node c = adjacentNodes.get(combination[1]);

                if (orig.isDefCollider(a, b, c) && !orig.isAdjacentTo(a, c)) {
                    if (FciOrient.isArrowpointAllowed(a, b, graph, knowledge)
                            && FciOrient.isArrowpointAllowed(c, b, graph, knowledge)) {
                        graph.setEndpoint(a, b, Endpoint.ARROW);
                        graph.setEndpoint(c, b, Endpoint.ARROW);
                    }
                }
            }
        }
    }

    private Graph swapOrient(Graph graph, TeyssierScorer scorer, Knowledge knowledge, Set<Edge> removed) {
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

                        if (a(scorer, z, x, y, w)) {
                            if (FciOrient.isArrowpointAllowed(w, y, graph, knowledge)) {
                                if (FciOrient.isArrowpointAllowed(x, y, graph, knowledge)) {
                                    scorer.bookmark();
                                    scorer.swap(x, y);

                                    for (Node y2 : nodes) {

                                        if (b(scorer, z, x, y2, w)) {
                                            if (graph.isAdjacentTo(w, x)) {
                                                Edge edge = graph.getEdge(w, x);
                                                removed.add(edge);

                                                if (graph.isAdjacentTo(x, y2) && graph.isAdjacentTo(y2, w)) {
                                                    System.out.println("Queueing " + edge + " for removal (swapped " + x + " and " + y + ")");

                                                    graph.setEndpoint(w, y2, Endpoint.ARROW);
                                                    graph.setEndpoint(x, y2, Endpoint.ARROW);
                                                    System.out.println("Remove orienting " + GraphUtils.pathString(graph, x, y2, w));
                                                }
                                            }
                                        }
                                    }
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

    private Graph swapRemove(Graph graph, Set<Edge> removed) {
        graph = new EdgeListGraph(graph);

        for (Edge edge : removed) {
            graph.removeEdge(edge);
            System.out.println("Swap removing : " + edge);
        }

        return graph;
    }

    private static boolean a(TeyssierScorer scorer, Node z, Node x, Node y, Node w) {
        if ((z == null || scorer.adjacent(z, x)) && scorer.adjacent(x, y) && scorer.adjacent(y, w)) {
            if (scorer.adjacent(w, x) /*&& (z == null || !scorer.adjacent(z, y))*/) {
                return (z == null || scorer.collider(z, x, y));// && scorer.collider(z, x, w);
            }
        }

        return false;
    }

    private static boolean b(TeyssierScorer scorer, Node z, Node x, Node y, Node w) {
        if ((z == null || scorer.adjacent(z, x)) && scorer.adjacent(x, y) && scorer.adjacent(y, w)) {
            if (!scorer.adjacent(w, x) && (z == null || scorer.adjacent(z, y))) {
                return scorer.collider(w, y, x);// && scorer.collider(w, y, z);
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
