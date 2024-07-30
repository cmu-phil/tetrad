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
     * Whether to use the discriminating path tail rule.
     */
    private boolean doDiscriminatingPathTailRule = true;
    /**
     * Whether to use the discriminating path collider rule.
     */
    private boolean doDiscriminatingPathColliderRule = true;
    /**
     * Whether to use the ordered version of GRaSP.
     */
    private boolean ordered = false;
    /**
     * The depth for singular variables.
     */
    private int uncoveredDepth = 1;
    /**
     * The depth for non-singular variables.
     */
    private int nonSingularDepth = 1;
    /**
     * The depth for sepsets.
     */
    private int depth = -1;
    /**
     * The seed used for random number generation. If the seed is not set explicitly, it will be initialized with a
     * value of -1. The seed is used for producing the same sequence of random numbers every time the program runs.
     *
     * @see GraspFci#setSeed(long)
     */
    private long seed = -1;
    /**
     * True iff verbose output should be printed.
     */
    private boolean verbose = false;
    /**
     * The flag for whether to repair a faulty PAG.
     */
    private boolean repairFaultyPag = false;
    /**
     * Whether to leave out the final orientation step.
     */
    private boolean ablationLeaveOutFinalOrientation;
    /**
     * The method to use for finding sepsets, 1 = greedy, 2 = min-p., 3 = max-p, default min-p.
     */
    private int sepsetFinderMethod = 2;

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
            TetradLogger.getInstance().log("Starting Grasp-FCI algorithm.");
            TetradLogger.getInstance().log("Independence test = " + this.independenceTest + ".");
        }

        // Run GRaSP to get a CPDAG (like GFCI with FGES)...
        Grasp alg = new Grasp(independenceTest, score);
        alg.setSeed(seed);
        alg.setOrdered(ordered);
        alg.setUseScore(useScore);
        alg.setUseRaskuttiUhler(useRaskuttiUhler);
        alg.setUseDataOrder(useDataOrder);
        alg.setDepth(depth);
        alg.setUncoveredDepth(uncoveredDepth);
        alg.setNonSingularDepth(nonSingularDepth);
        alg.setNumStarts(numStarts);
        alg.setVerbose(false);
        alg.setKnowledge(knowledge);

        List<Node> variables = this.score.getVariables();
        assert variables != null;

        alg.bestOrder(variables);
        Graph pag = alg.getGraph(true);

        Graph referenceCpdag = new EdgeListGraph(pag);

        SepsetProducer sepsets;

        if (independenceTest instanceof MsepTest) {
            sepsets = new DagSepsets(((MsepTest) independenceTest).getGraph());
        } else if (sepsetFinderMethod == 1) {
            sepsets = new SepsetsGreedy(pag, this.independenceTest, this.depth);
        } else if (sepsetFinderMethod == 2) {
            sepsets = new SepsetsMinP(pag, this.independenceTest, this.depth);
        } else if (sepsetFinderMethod == 3) {
            sepsets = new SepsetsMaxP(pag, this.independenceTest, this.depth);
        } else {
            throw new IllegalArgumentException("Invalid sepset finder method: " + sepsetFinderMethod);
        }

        gfciExtraEdgeRemovalStep(pag, referenceCpdag, nodes, sepsets, verbose);
        GraphUtils.gfciR0(pag, referenceCpdag, sepsets, knowledge, verbose);

        FciOrient fciOrient = new FciOrient(
                FciOrientDataExaminationStrategyTestBased.defaultConfiguration(independenceTest, new Knowledge(), false));

        if (!ablationLeaveOutFinalOrientation) {
            fciOrient.finalOrientation(pag);
        }

        if (repairFaultyPag) {
            GraphUtils.repairFaultyPag(pag, fciOrient, knowledge, null, verbose, ablationLeaveOutFinalOrientation);
        }

        GraphUtils.replaceNodes(pag, this.independenceTest.getVariables());
        return pag;
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
     * Sets the maximum length of any discriminating path.
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
     * Sets whether to use the discriminating path tail rule for GRaSP.
     *
     * @param doDiscriminatingPathTailRule True, if so.
     */
    public void setDoDiscriminatingPathTailRule(boolean doDiscriminatingPathTailRule) {
        this.doDiscriminatingPathTailRule = doDiscriminatingPathTailRule;
    }

    /**
     * Sets whether to use the discriminating path collider rule for GRaSP.
     *
     * @param doDiscriminatingPathColliderRule True, if so.
     */
    public void setDoDiscriminatingPathColliderRule(boolean doDiscriminatingPathColliderRule) {
        this.doDiscriminatingPathColliderRule = doDiscriminatingPathColliderRule;
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

    /**
     * Sets the depth for the search algorithm.
     *
     * @param depth The depth value to set for the search algorithm.
     */
    public void setDepth(int depth) {
        this.depth = depth;
    }

    /**
     * Sets the flag for whether to repair a faulty PAG.
     *
     * @param repairFaultyPag True, if so.
     */
    public void setRepairFaultyPag(boolean repairFaultyPag) {
        this.repairFaultyPag = repairFaultyPag;
    }

    /**
     * Sets whether to leave out the final orientation in the search algorithm.
     *
     * @param ablationLeaveOutFinalOrientation true if the final orientation should be left out, false otherwise.
     */
    public void setLeaveOutFinalOrientation(boolean ablationLeaveOutFinalOrientation) {
        this.ablationLeaveOutFinalOrientation = ablationLeaveOutFinalOrientation;
    }

    /**
     * Sets the method for finding sepsets in the GraspFci class.
     *
     * @param sepsetFinderMethod the method for finding sepsets
     */
    public void setSepsetFinderMethod(int sepsetFinderMethod) {
        this.sepsetFinderMethod = sepsetFinderMethod;
    }
}
