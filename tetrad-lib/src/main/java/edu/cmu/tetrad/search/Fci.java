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
import edu.cmu.tetrad.search.utils.*;
import edu.cmu.tetrad.util.MillisecondTimes;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Implements the Fast Causal Inference (FCI) algorithm due to Peter Spirtes, which addressed the case where latent
 * common causes cannot be assumed not to exist with respect to the data set being analyzed. That is, it is assumed that
 * there may be variables that are not included in the data that nonetheless may be causes of two or more variables that
 * are included in data.
 * <p>
 * Two alternatives are provided for doing the final orientation step, one due to Peter Spirtes, which is arrow
 * complete, and another due to Jiji Zhang, which is arrow and tail complete.
 * <p>
 * This algorithm, with the Spirtes final orientation rules, was given in an earlier version of this book:
 * <p>
 * Spirtes, P., Glymour, C. N., Scheines, R., &amp; Heckerman, D. (2000). Causation, prediction, and search. MIT press.
 * <p>
 * The algorithm with the Zhang final orientation rules was given in this reference:
 * <p>
 * Zhang, J. (2008). On the completeness of orientation rules for causal discovery in the presence of latent confounders
 * and selection bias. Artificial Intelligence, 172(16-17), 1873-1896.
 * <p>
 * This class is configured to respect knowledge of forbidden and required edges, including knowledge of temporal
 * tiers.
 *
 * @author peterspirtes
 * @author clarkglymour
 * @author jijizhang
 * @author josephramsey
 * @version $Id: $Id
 * @see FciOrient
 * @see Pc
 * @see Fas
 * @see Knowledge
 */
public final class Fci implements IGraphSearch {
    /**
     * The variables to search over.
     */
    private final List<Node> variables = new ArrayList<>();
    /**
     * The independence test to use.
     */
    private final IndependenceTest independenceTest;
    /**
     * The logger.
     */
    private final TetradLogger logger = TetradLogger.getInstance();
    /**
     * The sepsets from FAS.
     */
    private SepsetMap sepsets;
    /**
     * The background knowledge.
     */
    private Knowledge knowledge = new Knowledge();
    /**
     * Whether the Zhang complete rule set should be used.
     */
    private boolean completeRuleSetUsed = true;
    /**
     * Whether the possible msep step should be done.
     */
    private boolean possibleMsepSearchDone = true;
    /**
     * The maximum length of any discriminating path.
     */
    private int maxPathLength = -1;
    /**
     * The depth of search.
     */
    private int depth = -1;
    /**
     * The elapsed time of search.
     */
    private long elapsedTime;
    /**
     * Whether verbose output should be printed.
     */
    private boolean verbose;
    /**
     * The PC heuristic type to use.
     */
    private PcCommon.PcHeuristicType heuristic = PcCommon.PcHeuristicType.NONE;
    /**
     * Whether the stable options should be used.
     */
    private boolean stable = true;
    /**
     * Whether the discriminating path rule should be used.
     */
    private boolean doDiscriminatingPathTailRule = true;
    /**
     * Whether the discriminating path rule should be used.
     */
    private boolean doDiscriminatingPathColliderRule = true;
    /**
     * Whether the PAG should be repaired.
     */
    private boolean repairFaultyPag;
    /**
     * Whether the final orientation step should be left out.
     */
    private boolean ablationLeaveOutFinalOrientation = false;

    /**
     * Constructor.
     *
     * @param independenceTest The test to use for oracle conditional independence information.
     */
    public Fci(IndependenceTest independenceTest) {
        if (independenceTest == null) {
            throw new NullPointerException();
        }

        this.independenceTest = independenceTest;
        this.variables.addAll(independenceTest.getVariables());
    }

    /**
     * Constructor.
     *
     * @param independenceTest The test to use for oracle conditional independence information.
     * @param searchVars       A specific list of variables to search over.
     */
    public Fci(IndependenceTest independenceTest, List<Node> searchVars) {
        if (independenceTest == null) {
            throw new NullPointerException();
        }

        this.independenceTest = independenceTest;
        this.variables.addAll(independenceTest.getVariables());

        Set<Node> remVars = new HashSet<>();
        for (Node node1 : this.variables) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            boolean search = false;
            for (Node node2 : searchVars) {
                if (node1.getName().equals(node2.getName())) {
                    search = true;
                }
            }
            if (!search) {
                remVars.add(node1);
            }
        }

        this.variables.removeAll(remVars);
    }

    /**
     * Performs a search using the FCI algorithm.
     *
     * @return The resulting graph.
     */
    public Graph search() {
        long start = MillisecondTimes.timeMillis();

        Fas fas = new Fas(getIndependenceTest());

        if (verbose) {
            TetradLogger.getInstance().log("Starting FCI algorithm.");
            TetradLogger.getInstance().log("Independence test = " + getIndependenceTest() + ".");
        }

        fas.setKnowledge(getKnowledge());
        fas.setDepth(this.depth);
        fas.setPcHeuristicType(this.heuristic);
        fas.setVerbose(this.verbose);
        fas.setStable(this.stable);

        //The PAG being constructed.

        if (verbose) {
            TetradLogger.getInstance().log("Starting FAS search.");
        }

        Graph graph = fas.search();
        this.sepsets = fas.getSepsets();
        Set<Triple> unshieldedTriples = new HashSet<>();

        if (verbose) {
            TetradLogger.getInstance().log("Reorienting with o-o.");
        }

        graph.reorientAllWith(Endpoint.CIRCLE);

        // The original FCI, with or without JiJi Zhang's orientation rules
        // Optional step: Possible Msep. (Needed for correctness but very time-consuming.)
        FciOrient fciOrient = new FciOrient(
                R0R4StrategyTestBased.defaultConfiguration(independenceTest, knowledge));
        fciOrient.setVerbose(verbose);

        if (this.possibleMsepSearchDone) {
            if (verbose) {
                TetradLogger.getInstance().log("Starting possible msep search.");
            }

            if (verbose) {
                TetradLogger.getInstance().log("Doing R0.");
            }

            fciOrient.ruleR0(graph, unshieldedTriples);

            if (verbose) {
                TetradLogger.getInstance().log("Removing by possible d-sep.");
            }

            graph.paths().removeByPossibleMsep(independenceTest, sepsets);

            if (verbose) {
                TetradLogger.getInstance().log("Reorienting all edges as o-o.");
            }

            // Reorient all edges as o-o.
            graph.reorientAllWith(Endpoint.CIRCLE);
        }

        // Step CI C (Zhang's step F3.)

        if (verbose) {
            TetradLogger.getInstance().log("Doing R0.");
        }

        fciOrient.ruleR0(graph, unshieldedTriples);

        if (verbose) {
            TetradLogger.getInstance().log("Doing Final Orientation.");
        }

        if (!ablationLeaveOutFinalOrientation) {
            fciOrient.finalOrientation(graph);
        }

        if (repairFaultyPag) {
            graph = GraphUtils.repairFaultyPag(graph, fciOrient, knowledge, unshieldedTriples, verbose);
        }

        long stop = MillisecondTimes.timeMillis();
        this.elapsedTime = stop - start;
        return graph;
    }

    /**
     * Sets the depth of search, which is the maximum number of variables conditioned on in any test.
     *
     * @param depth This maximum.
     */
    public void setDepth(int depth) {
        if (depth < -1) {
            throw new IllegalArgumentException(
                    "Depth must be -1 (unlimited) or >= 0: " + depth);
        }

        this.depth = depth;
    }

    /**
     * Returns the elapsed time of search.
     *
     * @return This time.
     */
    public long getElapsedTime() {
        return this.elapsedTime;
    }

    /**
     * Returns the sepset map from FAS.
     *
     * @return This map.
     * @see SepsetMap
     */
    public SepsetMap getSepsets() {
        return this.sepsets;
    }

    /**
     * Returns the background knowledge that was set.
     *
     * @return This knowledge.
     */
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * Sets background knowledge for the search.
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
     * Sets whether the Zhang complete rule set should be used; false if only R1-R4 (the rule set of the original FCI)
     * should be used. False by default.
     *
     * @param completeRuleSetUsed True for the complete Zhang rule set.
     */
    public void setCompleteRuleSetUsed(boolean completeRuleSetUsed) {
        this.completeRuleSetUsed = completeRuleSetUsed;
    }

    /**
     * Sets whether the (time-consuming) possible msep step should be done.
     *
     * @param possibleMsepSearchDone True, if so.
     */
    public void setPossibleMsepSearchDone(boolean possibleMsepSearchDone) {
        this.possibleMsepSearchDone = possibleMsepSearchDone;
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
     * Returns the independence test used in search.
     *
     * @return This test.
     */
    public IndependenceTest getIndependenceTest() {
        return this.independenceTest;
    }

    /**
     * Sets which PC heuristic type should be used in the initial adjacency search.
     *
     * @param heuristic The heuristic type.
     * @see edu.cmu.tetrad.search.utils.PcCommon.PcHeuristicType
     */
    public void setPcHeuristicType(PcCommon.PcHeuristicType heuristic) {
        this.heuristic = heuristic;
    }

    /**
     * Sets whether the stable options should be used in the initial adjacency search.
     *
     * @param stable True, if so.
     * @see Pc
     */
    public void setStable(boolean stable) {
        this.stable = stable;
    }

    /**
     * Sets whether the discriminating path tail rule should be used.
     *
     * @param doDiscriminatingPathTailRule True, if so.
     */
    public void setDoDiscriminatingPathTailRule(boolean doDiscriminatingPathTailRule) {
        this.doDiscriminatingPathTailRule = doDiscriminatingPathTailRule;
    }

    /**
     * Sets whether the discriminating path collider rule should be used.
     *
     * @param doDiscriminatingPathColliderRule True, if so.
     */
    public void setDoDiscriminatingPathColliderRule(boolean doDiscriminatingPathColliderRule) {
        this.doDiscriminatingPathColliderRule = doDiscriminatingPathColliderRule;
    }

    /**
     * Sets whether the PAG should be repaired if faulty.
     *
     * @param repairFaultyPag True, if so.
     */
    public void setRepairFaultyPag(boolean repairFaultyPag) {
        this.repairFaultyPag = repairFaultyPag;
    }

    /**
     * Sets whether to leave out the final orientation in the search.
     *
     * @param ablationLeaveOutFinalOrientation True to leave out the final orientation, false otherwise.
     */
    public void setLeaveOutFinalOrientation(boolean ablationLeaveOutFinalOrientation) {
        this.ablationLeaveOutFinalOrientation = ablationLeaveOutFinalOrientation;
    }
}




