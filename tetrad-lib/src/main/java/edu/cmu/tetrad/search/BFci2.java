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
import java.util.Iterator;
import java.util.List;

import static edu.cmu.tetrad.graph.GraphUtils.addForbiddenReverseEdgesForDirectedEdges;

/**
 * J.M. Ogarrio and P. Spirtes and J. Ramsey, "A Hybrid Causal Search Algorithm
 * for Latent Variable Models," JMLR 2016. Here, BOSS has been substituted for
 * FGES.
 *
 * @author Juan Miguel Ogarrio
 * @author ps7z
 * @author jdramsey
 * @author bryan andrews
 */
public final class BFci2 implements GraphSearch {

    // The PAG being constructed.
    private Graph graph;

    // The background knowledge.
    private IKnowledge knowledge = new Knowledge2();

    // The conditional independence test.
    private IndependenceTest independenceTest;

    // Flag for complete rule set, true if should use complete rule set, false otherwise.
    private boolean completeRuleSetUsed = true;

    // The maximum length for any discriminating path. -1 if unlimited; otherwise, a positive integer.
    private int maxPathLength = -1;

    // The maxDegree for the fast adjacency search.
    private int maxDegree = -1;

    // The logger to use.
    private final TetradLogger logger = TetradLogger.getInstance();

    // True iff verbose output should be printed.
    private boolean verbose;

    // The covariance matrix beign searched over. Assumes continuous data.
    ICovarianceMatrix covarianceMatrix;

    // The sample size.
    int sampleSize;

    // The print stream that output is directed to.
    private PrintStream out = System.out;

    // The score.
    private final Score score;
    private int numStarts = 1;
    private int depth = -1;
    private boolean useRaskuttiUhler = false;
    private boolean useDataOrder = true;
    private boolean useScore = true;
    private boolean doDiscriminatingPathRule = true;
    private boolean possibleDsepSearchDone = true;

    //============================CONSTRUCTORS============================//
    public BFci2(IndependenceTest test, Score score) {
        if (score == null) {
            throw new NullPointerException();
        }
        this.sampleSize = score.getSampleSize();
        this.score = score;
        this.independenceTest = test;
    }

    //========================PUBLIC METHODS==========================//
    public Graph search() {
        List<Node> nodes = getIndependenceTest().getVariables();

        this.logger.log("info", "Starting FCI algorithm.");
        this.logger.log("info", "Independence test = " + getIndependenceTest() + ".");

        this.graph = new EdgeListGraph(nodes);

        List<Node> variables = this.score.getVariables();
        assert variables != null;

        TeyssierScorer scorer = new TeyssierScorer(independenceTest, score);

        this.graph = getBossCpdag(variables, scorer);

        if (score instanceof MagSemBicScore) {
            ((MagSemBicScore) score).setMag(graph);
        }

        IKnowledge knowledge2 = new Knowledge2((Knowledge2) knowledge);
        addForbiddenReverseEdgesForDirectedEdges(SearchGraphUtils.cpdagForDag(graph), knowledge2);
//
        // Keep a copy of this CPDAG.
        Graph reference = new EdgeListGraph(this.graph);

        keepArrows(reference);

        // GFCI extra edge removal step...
        SepsetProducer sepsets = new SepsetsGreedy(this.graph, this.independenceTest, null, this.depth);
        removeSomeMoreEdgesAndOrientSomeBidirectedEdgesByTesting(this.graph, reference, nodes, sepsets, knowledge2);
        doFinalOrientation(sepsets, knowledge2);

        graph.setPag(true);

        GraphUtils.replaceNodes(this.graph, this.independenceTest.getVariables());

        this.graph.setPag(true);

        return this.graph;
    }

    private Graph getBossCpdag(List<Node> variables, TeyssierScorer scorer) {
        // Run BOSS-tuck to get a CPDAG (like GFCI with FGES)...
        Boss alg = new Boss(scorer);
        alg.setAlgType(Boss.AlgType.BOSS);
        alg.setUseScore(useScore);
        alg.setUseRaskuttiUhler(useRaskuttiUhler);
        alg.setUseDataOrder(useDataOrder);
        alg.setDepth(depth);
        alg.setNumStarts(numStarts);
        alg.setVerbose(false);

        alg.bestOrder(variables);
        return alg.getGraph(true);
    }

    private void doFinalOrientation(SepsetProducer sepsets, IKnowledge knowledge2) {
        FciOrient fciOrient = new FciOrient(sepsets);
        fciOrient.setCompleteRuleSetUsed(this.completeRuleSetUsed);
        fciOrient.setMaxPathLength(this.maxPathLength);
        fciOrient.setDoDiscriminatingPathRule(this.doDiscriminatingPathRule);
        fciOrient.setVerbose(true);
        fciOrient.setKnowledge(knowledge2);
        fciOrient.doFinalOrientation(graph);
    }

    /**
     * @param maxDegree The maximum indegree of the output graph.
     */
    public void setMaxDegree(int maxDegree) {
        if (maxDegree < -1) {
            throw new IllegalArgumentException("Depth must be -1 (unlimited) or >= 0: " + maxDegree);
        }

        this.maxDegree = maxDegree;
    }

    /**
     * Returns The maximum indegree of the output graph.
     */
    public int getMaxDegree() {
        return this.maxDegree;
    }

    // Due to Spirtes.
    private void keepArrows(Graph fgesGraph) {
        this.graph = new EdgeListGraph(graph);
        this.graph.reorientAllWith(Endpoint.CIRCLE);
        fciOrientbk(this.knowledge, this.graph, this.graph.getNodes());

        for (Edge edge : fgesGraph.getEdges()) {
            if (edge.getEndpoint1() == Endpoint.ARROW) {
                this.graph.setEndpoint(edge.getNode2(), edge.getNode1(), Endpoint.ARROW);
            }

            if (edge.getEndpoint2() == Endpoint.ARROW) {
                this.graph.setEndpoint(edge.getNode1(), edge.getNode2(), Endpoint.ARROW);
            }
        }
    }

    private void removeSomeMoreEdgesAndOrientSomeBidirectedEdgesByTesting(Graph graph, Graph referenceCpdag, List<Node> nodes, SepsetProducer sepsets, IKnowledge knowledge) {
        for (Node b : nodes) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            List<Node> adjacentNodes = referenceCpdag.getAdjacentNodes(b);

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

                if (graph.isAdjacentTo(a, c) && referenceCpdag.isAdjacentTo(a, c)) {
                    List<Node> sepset = sepsets.getSepset(a, c);

                    if (sepset != null) {
                        graph.removeEdge(a, c);

                        if (graph.isAdjacentTo(a, b) && graph.isAdjacentTo(c, b)) {
                            if (!sepset.contains(b)) {
                                if (FciOrient.isArrowpointAllowed(a, b, graph, knowledge)
                                        && FciOrient.isArrowpointAllowed(c, b, graph, knowledge)) {
                                    this.graph.setEndpoint(a, b, Endpoint.ARROW);
                                    this.graph.setEndpoint(c, b, Endpoint.ARROW);
                                }
                            }
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


    public IKnowledge getKnowledge() {
        return this.knowledge;
    }

    public void setKnowledge(IKnowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException("Knowledge was null");
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
    public IndependenceTest getIndependenceTest() {
        return this.independenceTest;
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

    public void setIndependenceTest(IndependenceTest independenceTest) {
        this.independenceTest = independenceTest;
    }

    //===========================================PRIVATE METHODS=======================================//


    public void setNumStarts(int numStarts) {
        this.numStarts = numStarts;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

    public void setUseRaskuttiUhler(boolean useRaskuttiUhler) {
        this.useRaskuttiUhler = useRaskuttiUhler;
    }

    public void setUseDataOrder(boolean useDataOrder) {
        this.useDataOrder = useDataOrder;
    }

    public void setUseScore(boolean useScore) {
        this.useScore = useScore;
    }

    public void setDoDiscriminatingPathRule(boolean doDiscriminatingPathRule) {
        this.doDiscriminatingPathRule = doDiscriminatingPathRule;
    }

    public void setPossibleDsepSearchDone(boolean possibleDsepSearchDone) {
        this.possibleDsepSearchDone = possibleDsepSearchDone;
    }


}

