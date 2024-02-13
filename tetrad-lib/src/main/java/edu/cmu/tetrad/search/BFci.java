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
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.List;

import static edu.cmu.tetrad.graph.GraphUtils.gfciExtraEdgeRemovalStep;

/**
 * Uses BOSS in place of FGES for the initial step in the GFCI algorithm. This tends to produce a accurate PAG than GFCI
 * as a result, for the latent variables case. This is a simple substitution; the reference for GFCI is here:
 * <p>
 * J.M. Ogarrio and P. Spirtes and J. Ramsey, "A Hybrid Causal Search Algorithm for Latent Variable Models," JMLR 2016.
 * Here, BOSS has been substituted for FGES.
 * <p>
 * BOSS is an algorithm that is currently being written up for publication, so we don't yet have a reference for it.
 * <p>
 * For BOSS only a score is needed, but there are steps in GFCI that require a test, so for this method, both a test and
 * a score need to be given.
 * <p>
 * This class is configured to respect knowledge of forbidden and required edges, including knowledge of temporal
 * tiers.
 *
 * @author josephramsey
 * @author bryan andrews
 * @version $Id: $Id
 * @see Boss
 * @see GFci
 * @see GraspFci
 * @see SpFci
 * @see Fges
 * @see Knowledge
 */
public final class BFci implements IGraphSearch {

    // The conditional independence test.
    private final IndependenceTest independenceTest;
    // The logger to use.
    private final TetradLogger logger = TetradLogger.getInstance();
    // The score.
    private final Score score;
    // The sample size.
    int sampleSize;
    // The background knowledge.
    private Knowledge knowledge = new Knowledge();
    // Flag for the complete rule set, true if it should use the complete rule set, false otherwise.
    private boolean completeRuleSetUsed = true;
    // The maximum length for any discriminating path. -1 if unlimited; otherwise, a positive integer.
    private int maxPathLength = -1;
    // True iff verbose output should be printed.
    private boolean verbose;
    private int numStarts = 1;
    private int depth = -1;
    private boolean doDiscriminatingPathRule = true;
    private boolean bossUseBes = false;
    private long seed = -1;
    private int numThreads = 1;


    /**
     * Constructor. The test and score should be for the same data.
     *
     * @param test  The test to use.
     * @param score The score to use.
     * @see IndependenceTest
     * @see Score
     */
    public BFci(IndependenceTest test, Score score) {
        if (score == null) {
            throw new NullPointerException();
        }
        this.sampleSize = score.getSampleSize();
        this.score = score;
        this.independenceTest = test;
    }


    /**
     * Does the search and returns a PAG.
     *
     * @return The discovered graph.
     */
    public Graph search() {
        if (seed != -1) {
            RandomUtil.getInstance().setSeed(seed);
        }

        List<Node> nodes = getIndependenceTest().getVariables();

        this.logger.log("info", "Starting FCI algorithm.");
        this.logger.log("info", "Independence test = " + getIndependenceTest() + ".");

        // BOSS CPDAG learning step
        Boss subAlg = new Boss(this.score);
        subAlg.setUseBes(bossUseBes);
        subAlg.setNumStarts(this.numStarts);
        subAlg.setNumThreads(numThreads);
        PermutationSearch alg = new PermutationSearch(subAlg);
        alg.setKnowledge(this.knowledge);

        Graph graph = alg.search();

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
        fciOrient.setKnowledge(knowledge);

        fciOrient.doFinalOrientation(graph);

        GraphUtils.replaceNodes(graph, this.independenceTest.getVariables());

        return graph;
    }

    /**
     * Sets the knowledge to be used for the search.
     *
     * @param knowledge This knowledge.
     */
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }

    /**
     * Sets whether the complete (Zhang's) rule set should be used.
     *
     * @param completeRuleSetUsed True if Zhang's complete rule set should be used, false if only R1-R4 (the rule set of
     *                            the original FCI) should be used. False by default.
     */
    public void setCompleteRuleSetUsed(boolean completeRuleSetUsed) {
        this.completeRuleSetUsed = completeRuleSetUsed;
    }

    /**
     * Returns the maximum length of any discriminating path, or -1 if unlimited.
     *
     * @param maxPathLength This maximum.
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
     * @param verbose True iff the case
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * The independence test being used for some steps in final orientation.
     *
     * @return This test.
     */
    public IndependenceTest getIndependenceTest() {
        return this.independenceTest;
    }

    /**
     * Returns the number of times to restart the search.
     *
     * @param numStarts The number of times to restart the search.
     */
    public void setNumStarts(int numStarts) {
        this.numStarts = numStarts;
    }

    /**
     * Sets the depth of the search (for the constraint-based step).
     *
     * @param depth The depth of the search.
     */
    public void setDepth(int depth) {
        this.depth = depth;
    }

    /**
     * Sets whether the discriminating path rule should be used.
     *
     * @param doDiscriminatingPathRule True if the discriminating path rule should be used, false otherwise.
     */
    public void setDoDiscriminatingPathRule(boolean doDiscriminatingPathRule) {
        this.doDiscriminatingPathRule = doDiscriminatingPathRule;
    }

    /**
     * Sets whether the BES should be used.
     *
     * @param useBes True if the BES should be used, false otherwise.
     */
    public void setBossUseBes(boolean useBes) {
        this.bossUseBes = useBes;
    }

    /**
     * Sets the seed for the random number generator.
     *
     * @param seed The seed for the random number generator.
     */
    public void setSeed(long seed) {
        this.seed = seed;
    }

    public void setNumThreads(int numThreads) {
        if (numThreads < 1) {
            throw new IllegalArgumentException("Number of threads must be at least 1: " + numThreads);
        }
        this.numThreads = numThreads;
    }
}
