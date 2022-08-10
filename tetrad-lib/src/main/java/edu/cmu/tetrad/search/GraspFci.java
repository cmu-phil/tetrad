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
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
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

    //============================CONSTRUCTORS============================//
    public GraspFci(IndependenceTest test, Score score) {
        this.test = test;
        this.score = score;
    }

    //========================PUBLIC METHODS==========================//
    public Graph search() {
        this.logger.log("info", "Starting FCI algorithm.");
        this.logger.log("info", "Independence test = " + getTest() + ".");

        // The PAG being constructed.
        Grasp grasp = new Grasp(this.test, this.score);

        grasp.setDepth(this.depth);
        grasp.setSingularDepth(this.uncoveredDepth);
        grasp.setNonSingularDepth(this.nonsingularDepth);
//        grasp.setToleranceDepth(this.toleranceDepth);
        grasp.setOrdered(this.ordered);
        grasp.setUseScore(this.useScore);
        grasp.setUseRaskuttiUhler(this.useRaskuttiUhler);
        grasp.setUseDataOrder(this.useDataOrder);
        grasp.setVerbose(this.verbose);
        grasp.setCacheScores(this.cacheScores);

        grasp.setNumStarts(this.numStarts);
//        grasp.setKnowledge(this.knowledge);

        List<Node> variables = null;

        if (this.score != null) {
            variables = this.score.getVariables();
        } else if (this.test != null) {
            variables = this.test.getVariables();
        }

        assert variables != null;
        List<Node> perm = grasp.bestOrder(variables);

        System.out.println("perm = " + perm);

        Graph graph = grasp.getGraph(false);

        System.out.println("graph = " + graph);

        Graph graspGraph = new EdgeListGraph(graph);

        SepsetProducer sepsets = new SepsetsGreedy(graspGraph, this.test, null, -1);

        // "Extra" GFCI rule...
//        for (Node b : score.getVariables()) {
//            if (Thread.currentThread().isInterrupted()) {
//                break;
//            }
//
//            List<Node> adjacentNodes = graspGraph.getAdjacentNodes(b);
//
//            if (adjacentNodes.size() < 2) {
//                continue;
//            }
//
//            ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
//            int[] combination;
//
//            while ((combination = cg.next()) != null) {
//                if (Thread.currentThread().isInterrupted()) {
//                    break;
//                }
//
//                Node a = adjacentNodes.get(combination[0]);
//                Node c = adjacentNodes.get(combination[1]);
//
//                if (graph.isAdjacentTo(a, c) && graspGraph.isAdjacentTo(a, c)) {
//                    if (sepsets.getSepset(a, c) != null) {
//                        graph.removeEdge(a, c);
////                        knowledge.setForbidden(a.getName(), c.getName());
////                        knowledge.setForbidden(c.getName(), a.getName());
//                    }
//                }
//            }
//        }

//        perm = grasp.bestOrder(this.score.getVariables());
//
//        System.out.println("perm = " + perm);
//
//        graph = grasp.getGraph(true);

        graph.reorientAllWith(Endpoint.CIRCLE);



//        if (true) return graph;

//
//        graspGraph = new EdgeListGraph(graph);


        for (Node b : perm) {
            List<Node> adj = graph.getAdjacentNodes(b);

            for (int i = 0; i < adj.size(); i++) {
                for (int j = i + 1; j < adj.size(); j++) {
                    Node a = adj.get(i);
                    Node c = adj.get(j);

                    if (!graph.isAdjacentTo(a, c) && graspGraph.isDefCollider(a, b, c)
                            && isArrowpointAllowed(a, b, graph)
                            && isArrowpointAllowed(c, b, graph)
                    ) {
                        graph.setEndpoint(a, b, Endpoint.ARROW);
                        graph.setEndpoint(c, b, Endpoint.ARROW);
                    }
                }
            }
        }

        TeyssierScorer scorer = new TeyssierScorer(this.test, this.score);
        scorer.setUseRaskuttiUhler(this.useRaskuttiUhler);
        scorer.setKnowledge(knowledge);
        scorer.setUseScore(this.useScore);
        scorer.setCachingScores(this.cacheScores);

        scorer.score(perm);

        scorer.clearBookmarks();

        for (Node b : perm) {
            Set<Node> into = scorer.getParents(b);

            for (Node a : into) {
                for (Node c : into) {
                    for (Node d : perm) {
                        if (configuration(scorer, a, b, c, d)) {
                            System.out.println("Configuration " + a + "->" + b + "<-" + c + "--" + d);

                            scorer.bookmark();
                            double score = scorer.score();

                            scorer.swap(b, c);

                            grasp.bestOrder(scorer.getPi());

                            if (configuration(scorer, d, c, b, a) && score == scorer.score()) {
                                System.out.println("Configuration " + d + "->" + c + "<-" + b + "--" + a);

                                graph.removeEdge(b, d);
                                graph.setEndpoint(b, c, Endpoint.ARROW);
                                graph.setEndpoint(d, c, Endpoint.ARROW);

                            }

                            scorer.goToBookmark();
                        }
                    }
                }
            }
        }

        fciOrientBk(this.knowledge, graph, graph.getNodes());

        // The maxDegree for the discriminating path step.
        FciOrient fciOrient = new FciOrient(sepsets);
        fciOrient.setVerbose(this.verbose);
        fciOrient.setMaxPathLength(this.maxPathLength);
        fciOrient.skipDiscriminatingPathRule(false);
        fciOrient.setKnowledge(knowledge);
        fciOrient.setCompleteRuleSetUsed(this.completeRuleSetUsed);
        fciOrient.setMaxPathLength(this.maxPathLength);
        fciOrient.doFinalOrientation(graph);

        graph.setPag(true);

        graph.removeAttribute("BIC");

        return graph;
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
