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

        List<Node> pi = boss.bestOrder(variables);
        Graph G1 = boss.getGraph(true);

        Knowledge knowledge2 = new Knowledge(knowledge);
//        addForbiddenReverseEdgesForDirectedEdges(SearchGraphUtils.cpdagForDag(G1), knowledge2);

        Graph G2 = new EdgeListGraph(G1);
        retainUnshieldedColliders(G2, knowledge2);

        Graph G3 = new EdgeListGraph(G2);
        Graph G0;

        Set<Edge> removed = new HashSet<>();

        do {
            G0 = new EdgeListGraph(G3);
            G3 = swapFindRemove(G3, scorer, knowledge2, removed);
//            G3 = swapRemove3(G3, scorer, knowledge2, true, removed);
//            G3 = swapRemove(G3, removed);
            G3 = swapOrient(G3, scorer, knowledge2, false, removed, pi);
        } while (!G3.equals(G0));

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

    private Graph swapFindRemove(Graph graph, TeyssierScorer scorer, Knowledge knowledge, Set<Edge> removed) {
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

                        if (config(scorer, z, x, y, w, true)) {
                            if (FciOrient.isArrowpointAllowed(w, y, graph, knowledge)) {
                                if (FciOrient.isArrowpointAllowed(x, y, graph, knowledge)) {
                                    scorer.bookmark();
                                    scorer.swap(x, y);

                                    if (config(scorer, z, x, y, w, false)) {

                                        if (graph.isAdjacentTo(w, x)) {
                                            Edge edge = graph.getEdge(w, x);
                                            graph.removeEdge(edge);
                                            removed.add(edge);
                                            System.out.println("Swap removing : " + edge);

                                            graph.setEndpoint(w, y, Endpoint.ARROW);
                                            graph.setEndpoint(x, y, Endpoint.ARROW);
                                            System.out.println("Swap orienting " + GraphUtils.pathString(graph, x, y, w));
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
            removed.add(edge);
            System.out.println("Swap removing : " + edge);
        }

        return graph;
    }

    private Graph swapRemove3(Graph graph, TeyssierScorer scorer, Knowledge knowledge, boolean remove, Set<Edge> removed) {
        graph = new EdgeListGraph(graph);
        List<Node> nodes = graph.getNodes();

        for (Node x : nodes) {
            for (Node y : nodes) {
                for (Node z : nodes) {
                    if (scorer.index(x) == scorer.index(y)) continue;
                    if (scorer.index(x) == scorer.index(z)) continue;
                    if (scorer.index(y) == scorer.index(z)) continue;

                    if (config3(graph, x, y, z, true)) {
                        scorer.bookmark();
                        scorer.swap(x, y);

                        if (config3(graph, x, y, z, false)) {
                            if (remove) {
                                Edge edge = graph.getEdge(x, z);
//                                graph.removeEdge(edge);
                                removed.add(edge);
                                System.out.println("Swap removing : " + edge);

                            }
                        }

                        scorer.goToBookmark();
                    }
                }
            }
        }

        return graph;
    }

    private Graph swapOrient(Graph graph, TeyssierScorer scorer, Knowledge knowledge, boolean remove, Set<Edge> removed,
                             List<Node> pi) {
        graph = new EdgeListGraph(graph);
        List<Node> nodes = graph.getNodes();

        for (Node x : nodes) {
            for (Node y : nodes) {
                for (Node w : nodes) {
                    if (scorer.index(x) == scorer.index(y)) continue;
                    if (scorer.index(x) == scorer.index(w)) continue;
                    if (scorer.index(y) == scorer.index(w)) continue;

                    for (Edge edge : removed) {
                        if ((w == edge.getNode1() && x == edge.getNode2())
                                || (w == edge.getNode2() && x == edge.getNode1())) {
                            if (graph.isAdjacentTo(w, y) && graph.isAdjacentTo(x, y)) {
//                                List<Node> adjy = graph.getAdjacentNodes(y);
//
//                                boolean found = false;
//
//                                for (Node a : adjy) {
//                                    for (Node b : adjy) {
//                                        if (a != b && !graph.isAdjacentTo(a, b)) {
//                                            found = true;
//                                            break;
//                                        }
//                                    }
//                                }
                                if (FciOrient.isArrowpointAllowed(w, y, graph, knowledge)) {
                                    if (FciOrient.isArrowpointAllowed(x, y, graph, knowledge)) {

                                        boolean after = pi.indexOf(w) < pi.indexOf(y) || pi.indexOf(x) < pi.indexOf(y);

                                        if (after) {
                                            graph.setEndpoint(w, y, Endpoint.ARROW);
                                            graph.setEndpoint(x, y, Endpoint.ARROW);
                                            System.out.println("Swap orienting " + GraphUtils.pathString(graph, x, y, w));
                                        }
                                    }
                                }
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

    private static boolean config(TeyssierScorer scorer, Node z, Node x, Node y, Node w, boolean flag) {
        if (flag) {
            if (scorer.adjacent(z, x) && scorer.adjacent(x, y) && scorer.adjacent(y, w)) {
                if (scorer.adjacent(w, x) && !scorer.adjacent(z, y)) {
                    return scorer.collider(z, x, y);// && scorer.collider(z, x, w);
                }
            }
        } else {
            if (scorer.adjacent(z, x) && scorer.adjacent(x, y) && scorer.adjacent(y, w)) {
                if (!scorer.adjacent(w, x) && scorer.adjacent(z, y)) {
                    return scorer.collider(w, y, x);// && scorer.collider(w, y, z);
                }
            }
        }

        return false;
    }

    private static boolean config3(Graph graph, Node x, Node y, Node z, boolean flag) {
        if (flag) {
            return graph.isAdjacentTo(x, y) && graph.isAdjacentTo(x, z) && graph.isAdjacentTo(y, z);
        } else {
            return graph.isAdjacentTo(x, y) && !graph.isAdjacentTo(x, z) && graph.isAdjacentTo(y, z);
        }
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
