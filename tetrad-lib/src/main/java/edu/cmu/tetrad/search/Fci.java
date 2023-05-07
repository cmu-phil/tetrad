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
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.utils.FciOrient;
import edu.cmu.tetrad.search.utils.SepsetMap;
import edu.cmu.tetrad.search.utils.SepsetsSet;
import edu.cmu.tetrad.util.MillisecondTimes;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * <p>Implements the Fast Causal Inference (FCI) algorithm due to Peter Spirtes, which addressed
 * the case where latent common causes cannot be assumed not to exist with respect to the data set
 * being analyzed. That is, it is assumed that there may be variables that are not included in the
 * data that nonetheless may be causes of two or more variables that are included in data.</p>
 *
 * <p>Two alternatives are provided for doing the final orientation step, one due to Peter Spirtes,
 * which is arrow complete, and another due to Jiji Zhang, which is arrow and tail complete.</p>
 *
 * <p>This algorithm, with the Spirtes final orientation rules, was given in an earlier version of
 * this book:</p>
 *
 * <p>Spirtes, P., Glymour, C. N., Scheines, R., &amp; Heckerman, D. (2000). Causation,
 * prediction, and search. MIT press.</p>
 *
 * <p>The algorithm with the Zhang final orintation rules was given in this reference:</p>
 *
 * <p>Zhang, J. (2008). On the completeness of orientation rules for causal discovery in the presence
 * of latent confounders and selection bias. Artificial Intelligence, 172(16-17), 1873-1896.</p>
 *
 * @author peterspirtes
 * @author clarkglymour
 * @author jijizhang
 * @author josephramsey
 * @see FciOrient
 * @see Pc
 */
public final class Fci implements IGraphSearch {
    private SepsetMap sepsets;
    private Knowledge knowledge = new Knowledge();
    private final List<Node> variables = new ArrayList<>();
    private final IndependenceTest independenceTest;
    private boolean completeRuleSetUsed = true;
    private boolean possibleDsepSearchDone = true;
    private int maxPathLength = -1;
    private int depth = -1;
    private long elapsedTime;
    private final TetradLogger logger = TetradLogger.getInstance();
    private boolean verbose;
    private int heuristic;
    private boolean stable;
    private boolean doDiscriminatingPathRule = true;

    //============================CONSTRUCTORS============================//

    /**
     * Constructs a new FCI search for the given independence test and background knowledge.
     */
    public Fci(IndependenceTest independenceTest) {
        if (independenceTest == null) {
            throw new NullPointerException();
        }

        this.independenceTest = independenceTest;
        this.variables.addAll(independenceTest.getVariables());
    }

    /**
     * Constructs a new FCI search for the given independence test and background knowledge and a list of variables to
     * search over.
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

    //========================PUBLIC METHODS==========================//

    public int getDepth() {
        return this.depth;
    }

    public void setDepth(int depth) {
        if (depth < -1) {
            throw new IllegalArgumentException(
                    "Depth must be -1 (unlimited) or >= 0: " + depth);
        }

        this.depth = depth;
    }

    public long getElapsedTime() {
        return this.elapsedTime;
    }

    public Graph search() {
        long start = MillisecondTimes.timeMillis();

        Fas fas = new Fas(getIndependenceTest());
        this.logger.log("info", "Starting FCI algorithm.");
        this.logger.log("info", "Independence test = " + getIndependenceTest() + ".");

        fas.setKnowledge(getKnowledge());
        fas.setDepth(this.depth);
        fas.setHeuristic(this.heuristic);
        fas.setVerbose(this.verbose);
        fas.setStable(this.stable);
        fas.setHeuristic(this.heuristic);

        //The PAG being constructed.
        Graph graph = fas.search();
        this.sepsets = fas.getSepsets();

        graph.reorientAllWith(Endpoint.CIRCLE);

        // The original FCI, with or without JiJi Zhang's orientation rules
        // Optional step: Possible Dsep. (Needed for correctness but very time-consuming.)
        SepsetsSet sepsets1 = new SepsetsSet(this.sepsets, this.independenceTest);

        if (isPossibleDsepSearchDone()) {
            new FciOrient(sepsets1).ruleR0(graph);
            graph.paths().removeByPossibleDsep(independenceTest, sepsets);

            // Reorient all edges as o-o.
            graph.reorientAllWith(Endpoint.CIRCLE);
        }

        // Step CI C (Zhang's step F3.)

        FciOrient fciOrient = new FciOrient(sepsets1);

        fciOrient.setCompleteRuleSetUsed(this.completeRuleSetUsed);
        fciOrient.setMaxPathLength(this.maxPathLength);
        fciOrient.setDoDiscriminatingPathColliderRule(this.doDiscriminatingPathRule);
        fciOrient.setDoDiscriminatingPathTailRule(this.doDiscriminatingPathRule);
        fciOrient.setVerbose(this.verbose);
        fciOrient.setKnowledge(this.knowledge);

        fciOrient.ruleR0(graph);

        fciOrient.doFinalOrientation(graph);

        long stop = MillisecondTimes.timeMillis();

        this.elapsedTime = stop - start;

        return graph;
    }

    /**
     * Retrieves the sepset map from FAS.
     */
    public SepsetMap getSepsets() {
        return this.sepsets;
    }

    /**
     * Retrieves the background knowledge that was set.
     */
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * Sets background knowledge for the search.
     */
    public void setKnowledge(Knowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException();
        }

        this.knowledge = knowledge;
    }

    /**
     * @return true if Zhang's complete rule set should be used, false if only R1-R4 (the rule set of the original FCI)
     * should be used. False by default.
     */
    public boolean isCompleteRuleSetUsed() {
        return this.completeRuleSetUsed;
    }

    /**
     * @param completeRuleSetUsed set to true if Zhang's complete rule set should be used, false if only R1-R4 (the rule
     *                            set of the original FCI) should be used. False by default.
     */
    public void setCompleteRuleSetUsed(boolean completeRuleSetUsed) {
        this.completeRuleSetUsed = completeRuleSetUsed;
    }

    /**
     * True iff the (time-consuming) possible dsep step should be done.
     */
    public boolean isPossibleDsepSearchDone() {
        return this.possibleDsepSearchDone;
    }

    /**
     * True iff the (time-consuming) possible dsep step should be done.
     */
    public void setPossibleDsepSearchDone(boolean possibleDsepSearchDone) {
        this.possibleDsepSearchDone = possibleDsepSearchDone;
    }

    /**
     * @return the maximum length of any discriminating path, or -1 of unlimited.
     */
    public int getMaxPathLength() {
        return this.maxPathLength == Integer.MAX_VALUE ? -1 : this.maxPathLength;
    }

    /**
     * @param maxPathLength the maximum length of any discriminating path, or -1 if unlimited.
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
     * True iff verbose output should be printed.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * The independence test.
     */
    public IndependenceTest getIndependenceTest() {
        return this.independenceTest;
    }

    /**
     * The FAS heuristic.
     */
    public void setHeuristic(int heuristic) {
        this.heuristic = heuristic;
    }

    /**
     * The FAS stable option.
     */
    public void setStable(boolean stable) {
        this.stable = stable;
    }

    /**
     * Sets whether the discriminating path rule should be used.
     *
     * @param doDiscriminatingPathRule True if so.
     */
    public void setDoDiscriminatingPathRule(boolean doDiscriminatingPathRule) {
        this.doDiscriminatingPathRule = doDiscriminatingPathRule;
    }
}




