///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //i
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
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.search.utils.*;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.List;

import static edu.cmu.tetrad.graph.GraphUtils.gfciExtraEdgeRemovalStep;

/**
 * Uses GRaSP in place of FGES for the initial step in the GFCI algorithm. This tends to produce a accurate PAG than
 * GFCI as a result, for the latent variables case. This is a simple substitution; the reference for GFCI is here: J.M.
 * Ogarrio and P. Spirtes and J. Ramsey, "A Hybrid Causal Search Algorithm for Latent Variable Models," JMLR 2016. Here,
 * BOSS has been substituted for FGES.
 * <p>
 * For the first step, the GRaSP algorithm is used, with the same modifications as in the GFCI algorithm.
 * <p>
 * For the second step, the FCI final orientation algorithm is used, with the same modifications as in the GFCI
 * algorithm.
 * <p>
 * For GRaSP only a score is needed, but there are steps in GFCI that require a test, so for this method, both a test
 * and a score need to be given.
 * <p>
 * This class is configured to respect knowledge of forbidden and required edges, including knowledge of temporal
 * tiers.
 *
 * @author josephramsey
 * @author bryanandrews
 * @version $Id: $Id
 * @see Grasp
 * @see GFci
 * @see FciOrient
 * @see Knowledge
 */
public final class GraspFci implements IGraphSearch {

    /**
     * The conditional independence test.
     */
    private final IndependenceTest independenceTest;
    /**
     * The logger to use.
     */
    private final TetradLogger logger = TetradLogger.getInstance();
    /**
     * The score.
     */
    private final Score score;
    /**
     * The background knowledge.
     */
    private Knowledge knowledge = new Knowledge();
    /**
     * Flag for the complete rule set, true if one should use the complete rule set, false otherwise.
     */
    private boolean completeRuleSetUsed = true;
    /**
     * The maximum length for any discriminating path. -1 if unlimited; otherwise, a positive integer.
     */
    private int maxPathLength = -1;
    /**
     * True iff verbose output should be printed.
     */
    private boolean verbose;
    /**
     * The number of starts for GRaSP.
     */
    private int numStarts = 1;
    /**
     * Whether to use Raskutti and Uhler's modification of GRaSP.
     */
    private boolean useRaskuttiUhler = false;
    /**
     * Whether to use data order.
     */
    private boolean useDataOrder = true;
    /**
     * Whether to use score.
     */
    private boolean useScore = true;
    /**
     * Whether to use the discriminating path rule.
     */
    private boolean doDiscriminatingPathRule = true;
    /**
     * Whether to use the ordered version of GRaSP.
     */
    private boolean ordered = false;
    /**
     * The depth for GRaSP.
     */
    private int depth = -1;
    /**
     * The depth for singular variables.
     */
    private int uncoveredDepth = 1;
    /**
     * The depth for non-singular variables.
     */
    private int nonSingularDepth = 1;
    /**
     * The seed used for random number generation. If the seed is not set explicitly, it will be initialized with a
     * value of -1. The seed is used for producing the same sequence of random numbers every time the program runs.
     *
     * @see GraspFci#setSeed(long)
     */
    private long seed = -1;

    /**
     * Constructs a new GraspFci object.
     *
     * @param test  The independence test.
     * @param score a {@link edu.cmu.tetrad.search.score.Score} object
     */
    public GraspFci(IndependenceTest test, Score score) {
        if (score == null) {
            throw new NullPointerException();
        }

        this.score = score;
        this.independenceTest = test;
    }

    /**
     * Run the search and return s a PAG.
     *
     * @return The PAG.
     */
    public Graph search() {
        List<Node> nodes = this.independenceTest.getVariables();

        if (nodes == null) {
            throw new NullPointerException("Nodes from test were null.");
        }

        if (verbose) {
            TetradLogger.getInstance().forceLogMessage("Starting Grasp-FCI algorithm.");
            TetradLogger.getInstance().forceLogMessage("Independence test = " + this.independenceTest + ".");
        }

        // The PAG being constructed.
        // Run GRaSP to get a CPDAG (like GFCI with FGES)...
        Grasp alg = new Grasp(independenceTest, score);
        alg.setSeed(seed);
        alg.setOrdered(ordered);
        alg.setUseScore(useScore);
        alg.setUseRaskuttiUhler(useRaskuttiUhler);
        alg.setUseDataOrder(useDataOrder);
        int graspDepth = 3;
        alg.setDepth(graspDepth);
        alg.setUncoveredDepth(uncoveredDepth);
        alg.setNonSingularDepth(nonSingularDepth);
        alg.setNumStarts(numStarts);
        alg.setVerbose(verbose);

        List<Node> variables = this.score.getVariables();
        assert variables != null;

        alg.bestOrder(variables);
        Graph graph = alg.getGraph(true); // Get the DAG

        Graph referenceDag = new EdgeListGraph(graph);

//        // GFCI extra edge removal step...
//        SepsetProducer sepsets = new SepsetsGreedy(graph, this.independenceTest, null, this.depth, knowledge);
//        gfciExtraEdgeRemovalStep(graph, referenceDag, nodes, sepsets);
//        GraphUtils.gfciR0(graph, referenceDag, sepsets, knowledge);
//
//        FciOrient fciOrient = new FciOrient(sepsets);
//        fciOrient.setCompleteRuleSetUsed(this.completeRuleSetUsed);
//        fciOrient.setMaxPathLength(this.maxPathLength);
//        fciOrient.setDoDiscriminatingPathColliderRule(this.doDiscriminatingPathRule);
//        fciOrient.setDoDiscriminatingPathTailRule(this.doDiscriminatingPathRule);
//        fciOrient.setVerbose(verbose);
//        fciOrient.setKnowledge(knowledge);

        // GFCI extra edge removal step...
//        SepsetProducer sepsets = new SepsetsGreedy(graph, this.independenceTest, null, this.depth, knowledge);
        SepsetProducer sepsets = new SepsetsConservative(graph, this.independenceTest, null, this.depth);
        gfciExtraEdgeRemovalStep(graph, referenceDag, nodes, sepsets, verbose);
        GraphUtils.gfciR0(graph, referenceDag, sepsets, knowledge, verbose);

        Graph referencePag = independenceTest instanceof MsepTest ? ((MsepTest) independenceTest).getGraph() : graph;
        FciOrient fciOrient = new FciOrient(new DagSepsets(referencePag));

        fciOrient.setCompleteRuleSetUsed(completeRuleSetUsed);
        fciOrient.setDoDiscriminatingPathColliderRule(true);
        fciOrient.setDoDiscriminatingPathTailRule(true);
        fciOrient.setVerbose(verbose);
        fciOrient.setKnowledge(knowledge);

        fciOrient.doFinalOrientation(graph);

        GraphUtils.replaceNodes(graph, this.independenceTest.getVariables());

        return graph;
    }

    /**
     * Sets the knowledge used in search.
     *
     * @param knowledge This knowledge.
     */
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }

    /**
     * Sets whether Zhang's complete rules set is used.
     *
     * @param completeRuleSetUsed set to true if Zhang's complete rule set should be used, false if only R1-R4 (the rule
     *                            set of the original FCI) should be used. False by default.
     */
    public void setCompleteRuleSetUsed(boolean completeRuleSetUsed) {
        this.completeRuleSetUsed = completeRuleSetUsed;
    }

    /**
     * Sets the maximum length of any discriminating path searched.
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
     * Sets the number of starts for GRaSP.
     *
     * @param numStarts The number of starts.
     */
    public void setNumStarts(int numStarts) {
        this.numStarts = numStarts;
    }

    /**
     * Sets the depth for GRaSP.
     *
     * @param depth The depth.
     */
    public void setDepth(int depth) {
        this.depth = depth;
    }

    /**
     * Sets whether to use Raskutti and Uhler's modification of GRaSP.
     *
     * @param useRaskuttiUhler True, if so.
     */
    public void setUseRaskuttiUhler(boolean useRaskuttiUhler) {
        this.useRaskuttiUhler = useRaskuttiUhler;
    }

    /**
     * Sets whether to use data order for GRaSP (as opposed to random order) for the first step of GRaSP
     *
     * @param useDataOrder True, if so.
     */
    public void setUseDataOrder(boolean useDataOrder) {
        this.useDataOrder = useDataOrder;
    }

    /**
     * Sets whether to use score for GRaSP (as opposed to independence test) for GRaSP.
     *
     * @param useScore True, if so.
     */
    public void setUseScore(boolean useScore) {
        this.useScore = useScore;
    }

    /**
     * Sets whether to use the discriminating path rule for GRaSP.
     *
     * @param doDiscriminatingPathRule True, if so.
     */
    public void setDoDiscriminatingPathRule(boolean doDiscriminatingPathRule) {
        this.doDiscriminatingPathRule = doDiscriminatingPathRule;
    }

    /**
     * Sets depth for singular tucks.
     *
     * @param uncoveredDepth The depth for singular tucks.
     */
    public void setSingularDepth(int uncoveredDepth) {
        if (uncoveredDepth < -1) throw new IllegalArgumentException("Uncovered depth should be >= -1.");
        this.uncoveredDepth = uncoveredDepth;
    }

    /**
     * Sets depth for non-singular tucks.
     *
     * @param nonSingularDepth The depth for non-singular tucks.
     */
    public void setNonSingularDepth(int nonSingularDepth) {
        if (nonSingularDepth < -1) throw new IllegalArgumentException("Non-singular depth should be >= -1.");
        this.nonSingularDepth = nonSingularDepth;
    }

    /**
     * Sets whether to use the ordered version of GRaSP.
     *
     * @param ordered True, if so.
     */
    public void setOrdered(boolean ordered) {
        this.ordered = ordered;
    }

    /**
     * <p>Setter for the field <code>seed</code>.</p>
     *
     * @param seed a long
     */
    public void setSeed(long seed) {
        this.seed = seed;
    }
}
