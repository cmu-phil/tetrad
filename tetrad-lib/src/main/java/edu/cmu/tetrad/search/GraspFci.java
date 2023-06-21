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

import java.util.List;

import static edu.cmu.tetrad.graph.GraphUtils.gfciExtraEdgeRemovalStep;

/**
 * <p>Uses GRaSP in place of FGES for the initial step in the GFCI algorithm.
 * This tends to produce a accurate PAG than GFCI as a result, for the latent variables case. This is a simple
 * substitution; the reference for GFCI is here:</p>
 * <p>J.M. Ogarrio and P. Spirtes and J. Ramsey, "A Hybrid Causal Search Algorithm
 * for Latent Variable Models," JMLR 2016. Here, BOSS has been substituted for FGES.</p>
 *
 * <p>For the first step, the GRaSP algorithm is used, with the same
 * modifications as in the GFCI algorithm.</p>
 *
 * <p>For the second step, the FCI final orientation algorithm is used, with the same
 * modifications as in the GFCI algorithm.</p>
 *
 * <p>For GRaSP only a score is needed, but there are steps in GFCI that require
 * a test, so for this method, both a test and a score need to be given.</p>
 *
 * <p>This class is configured to respect knowledge of forbidden and required
 * edges, including knowledge of temporal tiers.</p>
 *
 * @author josephramsey
 * @author bryanandrews
 * @see Grasp
 * @see GFci
 * @see FciOrient
 * @see Knowledge
 */
public final class GraspFci implements IGraphSearch {

    // The background knowledge.
    private Knowledge knowledge = new Knowledge();

    // The conditional independence test.
    private final IndependenceTest independenceTest;

    // Flag for complete rule set, true if one should use complete rule set, false otherwise.
    private boolean completeRuleSetUsed = true;

    // The maximum length for any discriminating path. -1 if unlimited; otherwise, a positive integer.
    private int maxPathLength = -1;

    // The logger to use.
    private final TetradLogger logger = TetradLogger.getInstance();

    // True iff verbose output should be printed.
    private boolean verbose;

    // The score.
    private final Score score;
    private int numStarts = 1;
    private int depth = -1;
    private boolean useRaskuttiUhler = false;
    private boolean useDataOrder = true;
    private boolean useScore = true;
    private boolean doDiscriminatingPathRule = true;
    private boolean ordered = false;
    private int uncoveredDepth = 1;
    private int nonSingularDepth = 1;

    //============================CONSTRUCTORS============================//
    public GraspFci(IndependenceTest test, Score score) {
        if (score == null) {
            throw new NullPointerException();
        }

        this.score = score;
        this.independenceTest = test;
    }

    //========================PUBLIC METHODS==========================//

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

        this.logger.log("info", "Starting FCI algorithm.");
        this.logger.log("info", "Independence test = " + this.independenceTest + ".");

        // The PAG being constructed.
        // Run GRaSP to get a CPDAG (like GFCI with FGES)...
        Grasp alg = new Grasp(independenceTest, score);
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

        GraphUtils.replaceNodes(graph, this.independenceTest.getVariables());

        return graph;
    }

    /**
     * Sets the knoweldge used in search.
     *
     * @param knowledge This knoweldge.
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
     * @param verbose True if so.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
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

    public void setSingularDepth(int uncoveredDepth) {
        if (uncoveredDepth < -1) throw new IllegalArgumentException("Uncovered depth should be >= -1.");
        this.uncoveredDepth = uncoveredDepth;
    }

    public void setNonSingularDepth(int nonSingularDepth) {
        if (nonSingularDepth < -1) throw new IllegalArgumentException("Non-singular depth should be >= -1.");
        this.nonSingularDepth = nonSingularDepth;
    }

    public void setOrdered(boolean ordered) {
        this.ordered = ordered;
    }
}
