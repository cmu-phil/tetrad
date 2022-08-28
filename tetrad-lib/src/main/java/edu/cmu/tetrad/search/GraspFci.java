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
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.data.KnowledgeEdge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.TetradLogger;

import java.io.PrintStream;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Adjusts GFCI to use a permutation algorithm (such as GRaSP) to do the initial
 * steps of finding adjacencies and unshielded colliders. Adjusts the GFCI rule
 * for finding bidirected edges to use permutation reasoning.
 * <p>
 * GFCI reference is this:
 * <p>
 * J.M. Ogarrio and P. Spirtes and J. Ramsey, "A Hybrid Causal Search Algorithm
 * for Latent Variable Models," JMLR 2016.
 *
 * @author jdramsey
 */
public final class GraspFci implements GraphSearch {

    // The score used, if GS is used to build DAGs.
    private final Score score;

    // The logger to use.
    private final TetradLogger logger = TetradLogger.getInstance();

    // The covariance matrix being searched over, if continuous data is supplied. This is
    // no used by the algorithm but can be retrieved by another method if desired
    ICovarianceMatrix covarianceMatrix;

    // The background knowledge.
    private IKnowledge knowledge = new Knowledge2();

    // The test used if Pearl's method is used ot build DAGs
    private IndependenceTest test;

    // Flag for complete rule set, true if you should use complete rule set, false otherwise.
    private boolean completeRuleSetUsed;

    // The maximum length for any discriminating path. -1 if unlimited; otherwise, a positive integer.
    private int maxPathLength = -1;

    // True iff verbose output should be printed.
    private boolean verbose;

    // The print stream that output is directed to.
    private PrintStream out = System.out;

    // GRaSP parameters
    private int numStarts = 1;
    private int depth = 4;
    private int nonsingularDepth = 1;
    private int uncoveredDepth = 1;
    private int toleranceDepth = 0;
    private boolean useRaskuttiUhler;
    private boolean useDataOrder = true;
    private boolean allowRandomnessInsideAlgorithm;

    private boolean ordered = true;
    private boolean useScore = true;
    private boolean cacheScores = true;
    private Graph graph;
    private SepsetProducer sepsets;

    //============================CONSTRUCTORS============================//
    public GraspFci(IndependenceTest test, Score score) {
        this.test = test;
        this.score = score;
    }

    //========================PUBLIC METHODS==========================//
    public Graph search() {
        this.logger.log("info", "Starting FCI algorithm.");
        this.logger.log("info", "Independence test = " + getTest() + ".");
        long time1 = System.currentTimeMillis();

        // The PAG being constructed.
//        Grasp grasp = new Grasp(this.test, this.score);
//
//        grasp.setDepth(this.depth);
//        grasp.setSingularDepth(this.uncoveredDepth);
//        grasp.setNonSingularDepth(this.nonsingularDepth);
////        grasp.setToleranceDepth(this.toleranceDepth);
//        grasp.setOrdered(this.ordered);
//        grasp.setUseScore(this.useScore);
//        grasp.setUseRaskuttiUhler(this.useRaskuttiUhler);
//        grasp.setUseDataOrder(this.useDataOrder);
//        grasp.setVerbose(this.verbose);
//        grasp.setCacheScores(this.cacheScores);
//
//        grasp.setNumStarts(this.numStarts);
//        grasp.setKnowledge(this.knowledge);

        Boss boss = new Boss(test, score);
        boss.setAlgType(Boss.AlgType.BOSS_TUCK);

//        boss.setDepth(parameters.getInt(Params.GRASP_DEPTH));
//        boss.setUseDataOrder(parameters.getBoolean(Params.GRASP_USE_DATA_ORDER));
//        boss.setVerbose(parameters.getBoolean(Params.VERBOSE));

//        boss.setNumStarts(parameters.getInt(Params.NUM_STARTS));
        boss.setKnowledge(this.knowledge);
        boss.bestOrder(score.getVariables());

        Boss alg = new Boss(test, score);
        alg.setAlgType(Boss.AlgType.BOSS_TUCK);
        alg.setKnowledge(knowledge);
        alg.setUseDataOrder(false);
        alg.setVerbose(false);

        List<Node> variables = null;

        if (this.score != null) {
            variables = this.score.getVariables();
        } else if (this.test != null) {
            variables = this.test.getVariables();
        }

        assert variables != null;
        List<Node> nodes = alg.bestOrder(variables);

        this.graph = alg.getGraph();

//        if (true) return this.graph;

        Graph fgesGraph = new EdgeListGraph(this.graph);

        TeyssierScorer scorer = new TeyssierScorer(test, score);

//        this.sepsets = new SepsetsGreedy(fgesGraph, test, null, 3);
        this.sepsets = new SepsetsTeyssier(fgesGraph, scorer, null, 3);

        if (true) {
            // "extra" swap rule.
            scorer.score(nodes);
            scorer.clearBookmarks();

            copyColliders(fgesGraph);

            for (Edge edge : graph.getEdges()) {
                if (!edge.isDirected()) continue;

                Node d = Edges.getDirectedEdgeTail(edge);
                Node b = Edges.getDirectedEdgeHead(edge);

                List<Node> adj = graph.getAdjacentNodes(b);
                adj.retainAll(graph.getAdjacentNodes(d));

                for (Node c : adj) {
                    for (Node a : graph.getAdjacentNodes(b)) {
                        if (Edges.isBidirectedEdge(graph.getEdge(b, c))) continue;
                        reduce(alg, scorer, a, b, c, d);
                    }
                }
            }

            removeGfciEdges(fgesGraph);
        } else {
            // "Extra" GFCI rule...
            for (Node b : nodes) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                List<Node> adjacentNodes = fgesGraph.getAdjacentNodes(b);

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

                    if (this.graph.isAdjacentTo(a, c) && fgesGraph.isAdjacentTo(a, c)) {
                        if (this.sepsets.getSepset(a, c) != null) {
                            this.graph.removeEdge(a, c);
                        }
                    }
                }
            }

            modifiedR0(fgesGraph);
        }

        FciOrient fciOrient = new FciOrient(this.sepsets);
        fciOrient.setVerbose(this.verbose);
        fciOrient.setKnowledge(getKnowledge());
        fciOrient.setCompleteRuleSetUsed(this.completeRuleSetUsed);
        fciOrient.setMaxPathLength(this.maxPathLength);
        fciOrient.doFinalOrientation(this.graph);

        this.graph.setPag(true);

        return this.graph;


//        fciOrientBk(this.knowledge, graph, graph.getNodes());
//
//        // The maxDegree for the discriminating path step.
//        FciOrient fciOrient = new FciOrient(this.sepsets);
//        fciOrient.setVerbose(this.verbose);
//        fciOrient.setKnowledge(getKnowledge());
//        fciOrient.setCompleteRuleSetUsed(this.completeRuleSetUsed);
//        fciOrient.setMaxPathLength(this.maxPathLength);
//        fciOrient.doFinalOrientation(this.graph);
//
//        GraphUtils.replaceNodes(this.graph, test.getVariables());
//
//        graph.setPag(true);
//
//        graph.removeAttribute("BIC");
//
//        return graph;
    }

    private void reduce(Boss alg, TeyssierScorer scorer, Node a, Node b, Node c, Node d) {
        if (configuration(scorer, a, b, c, d)) {
            scorer.bookmark();
            scorer.swap(b, c);
            alg.bestOrder(scorer.getPi());

            if (configuration(scorer, d, c, b, a)) {
                System.out.println("Found latent " + c + "<->" + b);

                if (graph.isAdjacentTo(a, c) || !graph.isDefCollider(b, c, d)) {
                    graph.removeEdge(b, d);

                    graph.setEndpoint(d, c, Endpoint.ARROW);
                    graph.setEndpoint(b, c, Endpoint.ARROW);

                    scorer.goToBookmark();

                    return;
                }
            }

            scorer.goToBookmark();
        }
    }

    public void modifiedR0(Graph fgesGraph) {
        this.graph = new EdgeListGraph(graph);
        this.graph.reorientAllWith(Endpoint.CIRCLE);
        fciOrientbk(this.knowledge, this.graph, this.graph.getNodes());

        List<Node> nodes = this.graph.getNodes();

        for (Node b : nodes) {
            List<Node> adjacentNodes = this.graph.getAdjacentNodes(b);

            if (adjacentNodes.size() < 2) {
                continue;
            }

            ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                Node a = adjacentNodes.get(combination[0]);
                Node c = adjacentNodes.get(combination[1]);

                if (fgesGraph.isDefCollider(a, b, c)) {
                    this.graph.setEndpoint(a, b, Endpoint.ARROW);
                    this.graph.setEndpoint(c, b, Endpoint.ARROW);
                } else if (fgesGraph.isAdjacentTo(a, c) && !this.graph.isAdjacentTo(a, c)) {
                    List<Node> sepset = this.sepsets.getSepset(a, c);

                    if (sepset != null && !sepset.contains(b)) {
                        this.graph.setEndpoint(a, b, Endpoint.ARROW);
                        this.graph.setEndpoint(c, b, Endpoint.ARROW);
                    }
                }
            }
        }
    }

    public void copyColliders(Graph fgesGraph) {
        this.graph = new EdgeListGraph(graph);
        this.graph.reorientAllWith(Endpoint.CIRCLE);
        fciOrientbk(this.knowledge, this.graph, this.graph.getNodes());

        List<Node> nodes = this.graph.getNodes();

        for (Node b : nodes) {
            List<Node> adjacentNodes = this.graph.getAdjacentNodes(b);

            if (adjacentNodes.size() < 2) {
                continue;
            }

            ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                Node a = adjacentNodes.get(combination[0]);
                Node c = adjacentNodes.get(combination[1]);

                if (fgesGraph.isDefCollider(a, b, c)) {
                    this.graph.setEndpoint(a, b, Endpoint.ARROW);
                    this.graph.setEndpoint(c, b, Endpoint.ARROW);
                }
            }
        }
    }

    public void removeGfciEdges(Graph fgesGraph) {
        List<Node> nodes = this.graph.getNodes();

        for (Node b : nodes) {
            List<Node> adjacentNodes = this.graph.getAdjacentNodes(b);
            if (adjacentNodes.size() < 2) continue;
            ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                Node a = adjacentNodes.get(combination[0]);
                Node c = adjacentNodes.get(combination[1]);

                if (this.graph.isAdjacentTo(a, c) && fgesGraph.isAdjacentTo(a, c)) {
                    if (!Edges.isBidirectedEdge(this.graph.getEdge(a, c))) {
                        if (this.sepsets.getSepset(a, c) != null) {
                            this.graph.removeEdge(a, c);
                        }
                    }
                }
            }
        }
    }

    private void fciOrientbk(IKnowledge knowledge, Graph graph, List<Node> variables) {
        this.logger.log("info", "Starting BK Orientation.");

        for (Iterator<KnowledgeEdge> it = knowledge.forbiddenEdgesIterator(); it.hasNext(); ) {
            KnowledgeEdge edge = it.next();

            //match strings to variables in the graph.
            Node from = SearchGraphUtils.translate(edge.getFrom(), variables);
            Node to = SearchGraphUtils.translate(edge.getTo(), variables);

            if (from == null || to == null) {
                continue;
            }

            if (graph.getEdge(from, to) == null) {
                continue;
            }

            // Orient to*->from
            graph.setEndpoint(to, from, Endpoint.ARROW);
            this.logger.log("knowledgeOrientation", SearchLogUtils.edgeOrientedMsg("Knowledge", graph.getEdge(from, to)));
        }

        for (Iterator<KnowledgeEdge> it = knowledge.requiredEdgesIterator(); it.hasNext(); ) {
            KnowledgeEdge edge = it.next();

            //match strings to variables in this graph
            Node from = SearchGraphUtils.translate(edge.getFrom(), variables);
            Node to = SearchGraphUtils.translate(edge.getTo(), variables);

            if (from == null || to == null) {
                continue;
            }

            if (graph.getEdge(from, to) == null) {
                continue;
            }

            graph.setEndpoint(to, from, Endpoint.TAIL);
            graph.setEndpoint(from, to, Endpoint.ARROW);
            this.logger.log("knowledgeOrientation", SearchLogUtils.edgeOrientedMsg("Knowledge", graph.getEdge(from, to)));
        }

        this.logger.log("info", "Finishing BK Orientation.");
    }

    private boolean configuration(TeyssierScorer scorer, Node a, Node b, Node c, Node d) {
        if (!distinct(a, b, c, d)) return false;

        return scorer.adjacent(a, b)
                && scorer.adjacent(b, c)
                && scorer.adjacent(c, d)
                && scorer.adjacent(b, d)
                && !scorer.adjacent(a, c)
                && scorer.collider(a, b, c);
    }

    private boolean distinct(Node a, Node b, Node c, Node d) {
        Set<Node> nodes = new HashSet<>();

        nodes.add(a);
        nodes.add(b);
        nodes.add(c);
        nodes.add(d);

        return nodes.size() == 4;
    }

    public IKnowledge getKnowledge() {
        return this.knowledge;
    }

    public void setKnowledge(IKnowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException();
        }

        this.knowledge = knowledge;
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

    //===========================================PRIVATE METHODS=======================================//

    /**
     * Orients according to background knowledge
     */
    private void fciOrientBk(IKnowledge knowledge, Graph graph, List<Node> variables) {
        this.logger.log("info", "Starting BK Orientation.");

        for (Iterator<KnowledgeEdge> it = knowledge.forbiddenEdgesIterator(); it.hasNext(); ) {
            KnowledgeEdge edge = it.next();

            //match strings to variables in the graph.
            Node from = SearchGraphUtils.translate(edge.getFrom(), variables);
            Node to = SearchGraphUtils.translate(edge.getTo(), variables);

            if (from == null || to == null) {
                continue;
            }

            if (graph.getEdge(from, to) == null) {
                continue;
            }

            // Orient to*->from
            graph.setEndpoint(to, from, Endpoint.ARROW);
            this.logger.log("knowledgeOrientation", SearchLogUtils.edgeOrientedMsg("Knowledge", graph.getEdge(from, to)));
        }

        for (Iterator<KnowledgeEdge> it = knowledge.requiredEdgesIterator(); it.hasNext(); ) {
            KnowledgeEdge edge = it.next();

            //match strings to variables in this graph
            Node from = SearchGraphUtils.translate(edge.getFrom(), variables);
            Node to = SearchGraphUtils.translate(edge.getTo(), variables);

            if (from == null || to == null) {
                continue;
            }

            if (graph.getEdge(from, to) == null) {
                continue;
            }

            graph.setEndpoint(to, from, Endpoint.TAIL);
            graph.setEndpoint(from, to, Endpoint.ARROW);
            this.logger.log("knowledgeOrientation", SearchLogUtils.edgeOrientedMsg("Knowledge", graph.getEdge(from, to)));
        }

        this.logger.log("info", "Finishing BK Orientation.");
    }

    public void setNumStarts(int numStarts) {
        this.numStarts = numStarts;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

    public void setUncoveredDepth(int uncoveredDepth) {
        this.uncoveredDepth = uncoveredDepth;
    }

    public void setUseRaskuttiUhler(boolean useRaskuttiUhler) {
        this.useRaskuttiUhler = useRaskuttiUhler;
    }

    public void setNonSingularDepth(int nonsingularDepth) {
        this.nonsingularDepth = nonsingularDepth;
    }

    public void setToleranceDepth(int toleranceDepth) {
        this.toleranceDepth = toleranceDepth;
    }

    public void setOrdered(boolean ordered) {
        this.ordered = ordered;
    }

    public void setUseScore(boolean useScore) {
        this.useScore = useScore;
    }

    public void setCacheScores(boolean cacheScores) {
        this.cacheScores = cacheScores;
    }

    public void setUseDataOrder(boolean useDataOrder) {
        this.useDataOrder = useDataOrder;
    }

    public void setAllowRandomnessInsideAlgorithm(boolean allowRandomnessInsideAlgorithms) {
        this.allowRandomnessInsideAlgorithm = allowRandomnessInsideAlgorithms;
    }

    private boolean isArrowpointAllowed(Node x, Node y, Graph graph) {
        if (graph.getEndpoint(x, y) == Endpoint.ARROW) {
            return true;
        }

        if (graph.getEndpoint(x, y) == Endpoint.TAIL) {
            return false;
        }

        if (graph.getEndpoint(y, x) == Endpoint.ARROW) {
            return !this.knowledge.isForbidden(x.getName(), y.getName());
        } else if (graph.getEndpoint(y, x) == Endpoint.TAIL) {
            return !this.knowledge.isForbidden(x.getName(), y.getName());
        }

        return graph.getEndpoint(x, y) == Endpoint.CIRCLE;
    }

}
