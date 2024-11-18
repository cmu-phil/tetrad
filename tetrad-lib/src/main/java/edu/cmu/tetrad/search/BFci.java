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
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.search.utils.*;
import edu.cmu.tetrad.search.work_in_progress.MagSemBicScore;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static edu.cmu.tetrad.graph.GraphUtils.gfciExtraEdgeRemovalStep;

/**
 * Uses BOSS in place of FGES for the initial step in the GFCI algorithm. This tends to produce an accurate PAG than
 * GFCI as a result, for the latent variables case. This is a simple substitution; the reference for BFCI is here:
 * <p>
 * Andrews, B., Ramsey, J., Sanchez Romero, R., Camchong, J., &amp; Kummerfeld, E. (2024). Fast Scalable and Accurate
 * Discovery of DAGs Using the Best Order Score Search and Grow Shrink Trees. Advances in Neural Information Processing
 * Systems, 36.
 * <p>
 * The reference for GFCI is here:
 * <p>
 * J.M. Ogarrio and P. Spirtes and J. Ramsey, "A Hybrid Causal Search Algorithm for Latent Variable Models," JMLR 2016.
 * Here, BOSS has been substituted for FGES.
 * <p>
 * For BOSS only a score is needed, but there are steps in GFCI that require a test; for these, a test is additionally
 * required.
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
     * Flag for the complete rule set, true if it should use the complete rule set, false otherwise.
     */
    private boolean completeRuleSetUsed = true;
    /**
     * The maximum length for any discriminating path. -1 if unlimited; otherwise, a positive integer.
     */
    private int maxDiscriminatingPathLength = -1;
    /**
     * The number of times to restart the search.
     * <p>
     * The search algorithm may converge to a suboptimal solution. To mitigate this, the algorithm can be restart
     * multiple times with different initial conditions. The {@code numStarts} variable represents the number of times
     * the search algorithm will be restarted.
     * </p>
     *
     * @see BFci#setNumStarts(int)
     * @see BFci#search()
     */
    private int numStarts = 1;
    /**
     * Represents the depth of the search for the constraint-based step.
     */
    private int depth = -1;
    /**
     * Determines whether the Boss search algorithm should use the BES (Backward elimination of shadows) method as a
     * final step.
     */
    private boolean bossUseBes = false;
    /**
     * The seed for the random number generator used in the search. Defaults to -1 if not set.
     */
    private long seed = -1;
    /**
     * The number of threads to use for parallel processing. This variable determines the degree of parallelism for
     * certain operations that can be performed concurrently to improve performance. For example, in multithreaded
     * environments, setting this variable to a value greater than 1 can distribute work across multiple threads,
     * allowing for faster execution of the algorithm.
     * <p>
     * The value of this variable must be at least 1. By default, it is set to 1, meaning that only one thread will be
     * used for processing.
     */
    private int numThreads = 1;
    /**
     * True iff verbose output should be printed.
     */
    private boolean verbose;
    /**
     * Whether to guarantee the output is a PAG by repairing a faulty PAG.
     */
    private boolean guaranteePag;
    /**
     * The method to use for finding sepsets, 1 = greedy, 2 = min-p., 3 = max-p, default min-p.
     */
    private int sepsetFinderMethod = 2;

    /**
     * Constructor. The test and score should be for the same data.
     *
     * @param test  The test to use.
     * @param score The score to use.
     * @see IndependenceTest
     * @see Score
     */
    public BFci(IndependenceTest test, Score score) {
        if (test == null) {
            throw new NullPointerException("Test is null");
        }

        if (score == null) {
            throw new NullPointerException("Score is null");
        }
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

        this.independenceTest.setVerbose(verbose);

        List<Node> nodes = getIndependenceTest().getVariables();

        if (verbose) {
            TetradLogger.getInstance().log("===Starting BFCI===");
        }

        if (verbose) {
            TetradLogger.getInstance().log("Starting BOSS.");
        }

        Boss subAlg = new Boss(this.score);
        subAlg.setUseBes(bossUseBes);
        subAlg.setNumStarts(this.numStarts);
        subAlg.setNumThreads(numThreads);
        subAlg.setVerbose(verbose);
        PermutationSearch alg = new PermutationSearch(subAlg);
        alg.setKnowledge(this.knowledge);

        Graph pag = alg.search();

        if (verbose) {
            TetradLogger.getInstance().log("Finished BOSS.");
        }

        if (score instanceof MagSemBicScore) {
            ((MagSemBicScore) score).setMag(pag);
        }

        Graph cpdag = new EdgeListGraph(pag);
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

        Set<Triple> unshieldedColliders = new HashSet<>();

        gfciExtraEdgeRemovalStep(pag, cpdag, nodes, sepsets, depth, verbose);
        GraphUtils.gfciR0(pag, cpdag, sepsets, knowledge, verbose, unshieldedColliders);

        if (verbose) {
            TetradLogger.getInstance().log("Starting final FCI orientation.");
        }

        R0R4StrategyTestBased strategy = (R0R4StrategyTestBased) R0R4StrategyTestBased.specialConfiguration(independenceTest,
                knowledge, verbose);
        strategy.setDepth(-1);
        strategy.setMaxLength(-1);
        FciOrient fciOrient = new FciOrient(strategy);
        fciOrient.setCompleteRuleSetUsed(completeRuleSetUsed);
        fciOrient.setMaxDiscriminatingPathLength(maxDiscriminatingPathLength);
        fciOrient.setVerbose(verbose);

        fciOrient.finalOrientation(pag);

        if (verbose) {
            TetradLogger.getInstance().log("Finished implied orientation.");
        }

        if (guaranteePag) {
            pag = GraphUtils.guaranteePag(pag, fciOrient, knowledge, unshieldedColliders, unshieldedColliders, false, verbose,
                    new HashSet<>());
        }

        if (verbose) {
            TetradLogger.getInstance().log("BFCI finished.");
        }

        return pag;
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
     * Sets the maximum length of any discriminating path.
     *
     * @param maxDiscriminatingPathLength the maximum length of any discriminating path, or -1 if unlimited.
     */
    public void setMaxDiscriminatingPathLength(int maxDiscriminatingPathLength) {
        if (maxDiscriminatingPathLength < -1) {
            throw new IllegalArgumentException("Max path length must be -1 (unlimited) or >= 0: " + maxDiscriminatingPathLength);
        }

        this.maxDiscriminatingPathLength = maxDiscriminatingPathLength;
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

    /**
     * Sets the number of threads to use.
     *
     * @param numThreads The number of threads to use. Must be at least 1.
     */
    public void setNumThreads(int numThreads) {
        if (numThreads < 1) {
            throw new IllegalArgumentException("Number of threads must be at least 1: " + numThreads);
        }
        this.numThreads = numThreads;
    }

    /**
     * Sets whether to guarantee the output is a PAG by repairing a faulty PAG.
     *
     * @param guaranteePag True if a faulty PAG should be repaired, false otherwise.
     */
    public void setGuaranteePag(boolean guaranteePag) {
        this.guaranteePag = guaranteePag;
    }

    /**
     * Sets the method to be used for finding the sepset.
     *
     * @param sepsetFinderMethod The method to be used for finding the sepset.
     */
    public void setSepsetFinderMethod(int sepsetFinderMethod) {
        this.sepsetFinderMethod = sepsetFinderMethod;
    }
}

