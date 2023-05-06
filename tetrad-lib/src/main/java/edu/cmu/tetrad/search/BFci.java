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

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.data.KnowledgeEdge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.utils.GraphUtilsSearch;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.search.utils.SepsetProducer;
import edu.cmu.tetrad.search.utils.SepsetsGreedy;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.Iterator;
import java.util.List;

import static edu.cmu.tetrad.graph.GraphUtils.gfciExtraEdgeRemovalStep;

/**
 * <p>J.M. Ogarrio and P. Spirtes and J. Ramsey, "A Hybrid Causal Search Algorithm
 * for Latent Variable Models," JMLR 2016. Here, BOSS has been substituted for
 * FGES.</p>
 *
 * <p>This uses PermutationSearch (see), calling the BOSS algorithm (see).</p>
 *
 * <p>An independence test must be provided for the definite discriminating path step
 * of FCI. Otherwise, the provided score is used throughout.</p>
 *
 * @author Juan Miguel Ogarrio
 * @author ps7z
 * @author jdramsey
 * @author bryan andrews
 * @see PermutationSearch
 * @see Boss
 * @see GFci
 */
public final class BFci implements GraphSearch {

    // The PAG being constructed.
    private Graph graph;

    // The background knowledge.
    private Knowledge knowledge = new Knowledge();

    // The conditional independence test.
    private final IndependenceTest independenceTest;

    // Flag for complete rule set, true if it should use complete rule set, false otherwise.
    private boolean completeRuleSetUsed = true;

    // The maximum length for any discriminating path. -1 if unlimited; otherwise, a positive integer.
    private int maxPathLength = -1;

    // The logger to use.
    private final TetradLogger logger = TetradLogger.getInstance();

    // True iff verbose output should be printed.
    private boolean verbose;

    // The sample size.
    int sampleSize;

    // The score.
    private final Score score;
    private int numStarts = 1;
    private int depth = -1;
    private boolean doDiscriminatingPathRule = true;

    //============================CONSTRUCTORS============================//
    public BFci(IndependenceTest test, Score score) {
        if (score == null) {
            throw new NullPointerException();
        }
        this.sampleSize = score.getSampleSize();
        this.score = score;
        this.independenceTest = test;
    }

    //========================PUBLIC METHODS==========================//

    /**
     * Does the search and returns a PAG.
     *
     * @return The discovered graph.
     */
    public Graph search() {
        List<Node> nodes = getIndependenceTest().getVariables();

        this.logger.log("info", "Starting FCI algorithm.");
        this.logger.log("info", "Independence test = " + getIndependenceTest() + ".");

        this.graph = new EdgeListGraph(nodes);

        // BOSS CPDAG learning step
        Boss subAlg = new Boss(this.score);
        subAlg.setNumStarts(this.numStarts);
        PermutationSearch alg = new PermutationSearch(subAlg);
        alg.setKnowledge(this.knowledge);
        alg.setVerbose(this.verbose);

        this.graph = alg.search();

        Knowledge knowledge2 = new Knowledge(knowledge);
        Graph referenceDag = new EdgeListGraph(this.graph);
        SepsetProducer sepsets = new SepsetsGreedy(this.graph, this.independenceTest, null, this.depth);

        // FCI extra edge removal step
        gfciExtraEdgeRemovalStep(this.graph, referenceDag, nodes, sepsets);
        modifiedR0(referenceDag, sepsets);

        FciOrient fciOrient = new FciOrient(sepsets);
        fciOrient.setCompleteRuleSetUsed(this.completeRuleSetUsed);
        fciOrient.setMaxPathLength(this.maxPathLength);
        fciOrient.setDoDiscriminatingPathColliderRule(this.doDiscriminatingPathRule);
        fciOrient.setDoDiscriminatingPathTailRule(this.doDiscriminatingPathRule);
        fciOrient.setVerbose(verbose);
        fciOrient.setKnowledge(knowledge2);

        fciOrient.doFinalOrientation(graph);

        GraphUtils.replaceNodes(this.graph, this.independenceTest.getVariables());

        return this.graph;
    }

    /**
     * Sets the maximum indegree of the output graph, to guide search.
     *
     * @param maxDegree This maximum.
     */
    public void setMaxDegree(int maxDegree) {
        if (maxDegree < -1) {
            throw new IllegalArgumentException("Depth must be -1 (unlimited) or >= 0: " + maxDegree);
        }

        // The maxDegree for the fast adjacency search.
    }

    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
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

    /**
     * Sets whether verbose output should be printed.
     *
     * @param verbose True iff the case
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * The independence test being used for some steps in final orientation.
     */
    public IndependenceTest getIndependenceTest() {
        return this.independenceTest;
    }

    //===========================================PRIVATE METHODS=======================================//

    // Due to Spirtes.
    private void modifiedR0(Graph fgesGraph, SepsetProducer sepsets) {
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
                    List<Node> sepset = sepsets.getSepset(a, c);

                    if (sepset != null && !sepset.contains(b)) {
                        this.graph.setEndpoint(a, b, Endpoint.ARROW);
                        this.graph.setEndpoint(c, b, Endpoint.ARROW);
                    }
                }
            }
        }
    }

    /**
     * Orients according to background knowledge
     */
    private void fciOrientbk(Knowledge knowledge, Graph graph, List<Node> variables) {
        this.logger.log("info", "Starting BK Orientation.");

        for (Iterator<KnowledgeEdge> it = knowledge.forbiddenEdgesIterator(); it.hasNext(); ) {
            KnowledgeEdge edge = it.next();

            //match strings to variables in the graph.
            Node from = GraphUtilsSearch.translate(edge.getFrom(), variables);
            Node to = GraphUtilsSearch.translate(edge.getTo(), variables);

            if (from == null || to == null) {
                continue;
            }

            if (graph.getEdge(from, to) == null) {
                continue;
            }

            // Orient to*->from
            graph.setEndpoint(to, from, Endpoint.ARROW);
            this.logger.log("knowledgeOrientation", LogUtilsSearch.edgeOrientedMsg("Knowledge", graph.getEdge(from, to)));
        }

        for (Iterator<KnowledgeEdge> it = knowledge.requiredEdgesIterator(); it.hasNext(); ) {
            KnowledgeEdge edge = it.next();

            //match strings to variables in this graph
            Node from = GraphUtilsSearch.translate(edge.getFrom(), variables);
            Node to = GraphUtilsSearch.translate(edge.getTo(), variables);

            if (from == null || to == null) {
                continue;
            }

            if (graph.getEdge(from, to) == null) {
                continue;
            }

            graph.setEndpoint(to, from, Endpoint.TAIL);
            graph.setEndpoint(from, to, Endpoint.ARROW);
            this.logger.log("knowledgeOrientation", LogUtilsSearch.edgeOrientedMsg("Knowledge", graph.getEdge(from, to)));
        }

        this.logger.log("info", "Finishing BK Orientation.");
    }

    public void setNumStarts(int numStarts) {
        this.numStarts = numStarts;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

    public void setDoDiscriminatingPathRule(boolean doDiscriminatingPathRule) {
        this.doDiscriminatingPathRule = doDiscriminatingPathRule;
    }
}
