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
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.utils.FciOrient;
import edu.cmu.tetrad.search.utils.SepsetProducer;
import edu.cmu.tetrad.search.utils.SepsetsGreedy;
import edu.cmu.tetrad.util.TetradLogger;

import java.io.PrintStream;
import java.util.List;

import static edu.cmu.tetrad.graph.GraphUtils.gfciExtraEdgeRemovalStep;

/**
 * Implements a modification of FCI that started by running the FGES algorithm and then fixes that result to be correct
 * for latent variables models. First, colliders from the FGES results are copied into the final circle-circle graph,
 * and some independence reasoning is used to add the remaining colliders into the graph. Then, the FCI final
 * orientation rules are applied. The reference is here:
 * <p>
 * Ogarrio, J. M., Spirtes, P., &amp; Ramsey, J. (2016, August). A hybrid causal search algorithm for latent variable
 * models. In Conference on probabilistic graphical models (pp. 368-379). PMLR.
 * <p>
 * Because the method both runs FGES (a score-based algorithm) and does additional checking of conditional
 * independencies, both as part of its collider orientation step and also as part of the the definite discriminating
 * path step in the final FCI orientation rules, both a score and a test need to be used to construct a GFCI algorithm.
 * <p>
 * Note that various score-based algorithms could be used in place of FGES for the initial step; in this repository we
 * give three other options, GRaSP-FCI, BFCI (BOSS FCI), and SP-FCI (see).
 * <p>
 * For more information on the algorithm, see the reference above.
 * <p>
 * This class is configured to respect knowledge of forbidden and required edges, including knowledge of temporal
 * tiers.
 *
 * @author Juan Miguel Ogarrio
 * @author peterspirtes
 * @author josephramsey
 * @see Fci
 * @see FciOrient
 * @see GraspFci
 * @see BFci
 * @see SpFci
 * @see Fges
 * @see Knowledge
 */
public final class GFci implements IGraphSearch {
    // The independence test used in search.
    private final IndependenceTest independenceTest;
    // The logger.
    private final TetradLogger logger = TetradLogger.getInstance();
    // The score used in search.
    private final Score score;
    // The knowledge used in search.
    private Knowledge knowledge = new Knowledge();
    // Whether Zhang's complete rules are used.
    private boolean completeRuleSetUsed = true;
    // The maximum path length for the discriminating path rule.
    private int maxPathLength = -1;
    // The maximum degree of the output graph.
    private int maxDegree = -1;
    // Whether verbose output should be printed.
    private boolean verbose;
    // The print stream used for output.
    private PrintStream out = System.out;
    // Whether one-edge faithfulness is assumed.
    private boolean faithfulnessAssumed = true;
    // Whether the discriminating path rule should be used.
    private boolean doDiscriminatingPathRule = true;
    // The depth of the search for the possible m-sep search.
    private int depth = -1;


    /**
     * Constructs a new GFci algorithm with the given independence test and score.
     *
     * @param test  The independence test to use.
     * @param score The score to use.
     */
    public GFci(IndependenceTest test, Score score) {
        if (score == null) {
            throw new NullPointerException();
        }
        this.score = score;
        this.independenceTest = test;
    }


    /**
     * Runs the graph and returns the search PAG.
     *
     * @return This PAG.
     */
    public Graph search() {
        this.independenceTest.setVerbose(verbose);
        List<Node> nodes = getIndependenceTest().getVariables();

        this.logger.log("info", "Starting FCI algorithm.");
        this.logger.log("info", "Independence test = " + getIndependenceTest() + ".");

        Graph graph;

        Fges fges = new Fges(this.score);
        fges.setKnowledge(getKnowledge());
        fges.setVerbose(this.verbose);
        fges.setFaithfulnessAssumed(this.faithfulnessAssumed);
        fges.setMaxDegree(this.maxDegree);
        fges.setOut(this.out);
        graph = fges.search();

        Knowledge knowledge2 = new Knowledge(knowledge);
        Graph referenceDag = new EdgeListGraph(graph);

        // GFCI extra edge removal step...
        SepsetProducer sepsets = new SepsetsGreedy(graph, this.independenceTest, null, this.depth, knowledge);
        gfciExtraEdgeRemovalStep(graph, referenceDag, nodes, sepsets);
        GraphUtils.gfciR0(graph, referenceDag, sepsets, knowledge);

        FciOrient fciOrient = new FciOrient(sepsets);
        fciOrient.setCompleteRuleSetUsed(this.completeRuleSetUsed);
        fciOrient.setMaxPathLength(this.maxPathLength);
        fciOrient.setDoDiscriminatingPathColliderRule(this.doDiscriminatingPathRule);
        fciOrient.setDoDiscriminatingPathTailRule(this.doDiscriminatingPathRule);
        fciOrient.setVerbose(verbose);
        fciOrient.setKnowledge(knowledge2);

        fciOrient.doFinalOrientation(graph);
        return graph;
    }

    /**
     * Sets the maximum indegree of the output graph.
     *
     * @param maxDegree This maximum.
     */
    public void setMaxDegree(int maxDegree) {
        if (maxDegree < -1) {
            throw new IllegalArgumentException(
                    "Depth must be -1 (unlimited) or >= 0: " + maxDegree);
        }

        this.maxDegree = maxDegree;
    }

    /**
     * Returns the knowledge used in search.
     *
     * @return This knowledge
     */
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * Sets the knowledge to use in search.
     *
     * @param knowledge This knowledge.
     */
    public void setKnowledge(Knowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException();
        }

        this.knowledge = knowledge;
    }

    /**
     * Sets whether Zhang's complete rules are used.
     *
     * @param completeRuleSetUsed set to true if Zhang's complete rule set should be used, false if only R1-R4 (the rule
     *                            set of the original FCI) should be used. True by default.
     */
    public void setCompleteRuleSetUsed(boolean completeRuleSetUsed) {
        this.completeRuleSetUsed = completeRuleSetUsed;
    }

    /**
     * Sets the maximum path length for the discriminating path rule.
     *
     * @param maxPathLength the maximum length of any discriminating path, or -1 if unlimited.
     */
    public void setMaxPathLength(int maxPathLength) {
        if (maxPathLength < -1) {
            throw new IllegalArgumentException("Max path length must be -1 (unlimited) or >= 0: " + maxPathLength);
        }

        this.maxPathLength = maxPathLength;
    }

    /**
     * Sets whether verbose output should be printed.
     *
     * @param verbose True, if so.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Returns the independence test used in search.
     *
     * @return This test.
     */
    public IndependenceTest getIndependenceTest() {
        return this.independenceTest;
    }

    /**
     * Sets the print stream used for output, default System.out.
     *
     * @param out This print stream.
     */
    public void setOut(PrintStream out) {
        this.out = out;
    }

    /**
     * Sets whether one-edge faithfulness is assumed. For FGES
     *
     * @param faithfulnessAssumed True, if so.
     * @see Fges#setFaithfulnessAssumed(boolean)
     */
    public void setFaithfulnessAssumed(boolean faithfulnessAssumed) {
        this.faithfulnessAssumed = faithfulnessAssumed;
    }

    /**
     * Sets whether the discriminating path rule should be used.
     *
     * @param doDiscriminatingPathRule True, if so.
     */
    public void setDoDiscriminatingPathRule(boolean doDiscriminatingPathRule) {
        this.doDiscriminatingPathRule = doDiscriminatingPathRule;
    }

    /**
     * Sets whether the possible m-sep search should be done.
     *
     * @param possibleMsepSearchDone True, if so.
     */
    public void setPossibleMsepSearchDone(boolean possibleMsepSearchDone) {
    }

    /**
     * Sets the depth of the search for the possible m-sep search.
     *
     * @param depth This depth.
     */
    public void setDepth(int depth) {
        this.depth = depth;
    }


}
