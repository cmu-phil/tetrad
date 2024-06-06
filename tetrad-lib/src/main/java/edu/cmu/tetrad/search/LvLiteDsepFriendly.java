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
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.score.GraphScore;
import edu.cmu.tetrad.search.score.IndTestScore;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.search.utils.DagSepsets;
import edu.cmu.tetrad.search.utils.FciOrient;
import edu.cmu.tetrad.search.utils.TeyssierScorer;
import edu.cmu.tetrad.util.TetradLogger;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * The LV-Lite algorithm implements the IGraphSearch interface and represents a search algorithm for learning the
 * structure of a graphical model from observational data.
 * <p>
 * This class provides methods for running the search algorithm and getting the learned pattern as a PAG (Partially
 * Annotated Graph).
 *
 * @author josephramsey
 */
public final class LvLiteDsepFriendly implements IGraphSearch {
    /**
     * This variable represents a list of nodes that store different variables. It is declared as private and final,
     * hence it cannot be modified or accessed from outside the class where it is declared.
     */
    private final ArrayList<Node> variables;
    /**
     * Indicates whether to use Raskutti Uhler feature.
     */
    private boolean useRaskuttiUhler;
    /**
     * The independence test.
     */
    private final IndependenceTest test;
    /**
     * The score.
     */
    private Score score;
    /**
     * Indicates whether the score should be used.
     */
    private boolean useScore;
    /**
     * The background knowledge.
     */
    private Knowledge knowledge = new Knowledge();
    /**
     * Flag for the complete rule set, true if one should use the complete rule set, false otherwise.
     */
    private boolean completeRuleSetUsed = true;
    /**
     * The number of starts for GRaSP.
     */
    private int numStarts = 1;
    /**
     * Whether to use data order.
     */
    private boolean useDataOrder = true;
    /**
     * This variable represents whether the discriminating path rule is used in the LV-Lite class.
     * <p>
     * The discriminating path rule is a rule used in the search algorithm. It determines whether the algorithm
     * considers discriminating paths when searching for patterns in the data.
     * <p>
     * By default, the value of this variable is set to true, indicating that the discriminating path rule is used.
     */
    private boolean doDiscriminatingPathTailRule = true;
    /**
     * Indicates whether the discriminating path collider rule is turned on or off.
     * <p>
     * If set to true, the discriminating path collider rule is enabled. If set to false, the discriminating path
     * collider rule is disabled.
     */
    private boolean doDiscriminatingPathColliderRule = true;
    /**
     * True iff verbose output should be printed.
     */
    private boolean verbose;
    /**
     * Represents a variable that determines whether tucks are allowed. The value of this variable determines whether
     * tucks are enabled or disabled.
     */
    private boolean allowTucks = true;
    /**
     * Whether to impose an ordering on the three GRaSP algorithms.
     */
    private boolean ordered = false;
    /**
     * Specifies whether internal randomness is allowed.
     */
    private boolean allowInternalRandomness = false;
    /**
     * Represents the seed used for random number generation or shuffling.
     */
    private long seed = -1;
    /**
     * The maximum path length.
     */
    private int maxPathLength = -1;

    /**
     * Constructor for a test.
     *
     * @param test The test to use.
     */
    public LvLiteDsepFriendly(@NotNull IndependenceTest test) {
        this.test = test;
        this.variables = new ArrayList<>(test.getVariables());
        this.useScore = false;
        this.useRaskuttiUhler = true;
    }

    /**
     * Constructor that takes both a test and a score; only one is used-- the parameter setting will decide which.
     *
     * @param test  The test to use.
     * @param score The score to use.
     */
    public LvLiteDsepFriendly(@NotNull IndependenceTest test, Score score) {
        this.test = test;
        this.score = score;
        this.variables = new ArrayList<>(test.getVariables());
    }

    /**
     * Run the search and return s a PAG.
     *
     * @return The PAG.
     */
    public Graph search() {
        List<Node> nodes = this.test.getVariables();

        if (nodes == null) {
            throw new NullPointerException("Nodes from test were null.");
        }

        if (verbose) {
            TetradLogger.getInstance().log("===Starting LV-Lite-DSEP friendly===");
        }

        if (verbose) {
            TetradLogger.getInstance().log("Running GRaSP to get CPDAG and best order.");
        }

        test.setVerbose(verbose);

        edu.cmu.tetrad.search.Grasp grasp = new edu.cmu.tetrad.search.Grasp(test, score);

        grasp.setSeed(seed);
        grasp.setDepth(25);
        grasp.setUncoveredDepth(1);
        grasp.setNonSingularDepth(1);
        grasp.setOrdered(ordered);
        grasp.setUseScore(useScore);
        grasp.setUseRaskuttiUhler(useRaskuttiUhler);
        grasp.setUseDataOrder(useDataOrder);
        grasp.setAllowInternalRandomness(allowInternalRandomness);
        grasp.setVerbose(false);

        grasp.setNumStarts(numStarts);
        grasp.setKnowledge(this.knowledge);
        List<Node> best = grasp.bestOrder(variables);
        Graph cpdag = grasp.getGraph(true);
        var pag = new EdgeListGraph(cpdag);

        if (verbose) {
            TetradLogger.getInstance().log("Best order: " + best);
        }

        var scorer = new TeyssierScorer(test, score);
        scorer.setUseScore(useScore);
        scorer.score(best);
        scorer.bookmark();

        if (verbose) {
            TetradLogger.getInstance().log("Initializing PAG to BOSS CPDAG.");
            TetradLogger.getInstance().log("Initializing scorer with BOSS best order.");
        }


        scorer.score(best);

        FciOrient fciOrient;

        if (test instanceof MsepTest) {
            fciOrient = new FciOrient(new DagSepsets(((MsepTest) test).getGraph()));
        } else {
            fciOrient = new FciOrient(null);
        }

        fciOrient.setCompleteRuleSetUsed(completeRuleSetUsed);
        fciOrient.setDoDiscriminatingPathColliderRule(false);
        fciOrient.setDoDiscriminatingPathTailRule(false);
        fciOrient.setMaxPathLength(maxPathLength);
        fciOrient.setKnowledge(knowledge);
        fciOrient.setVerbose(verbose);

        if (verbose) {
            TetradLogger.getInstance().log("Collider orientation and edge removal.");
        }

        // The main procedure.
        Set<Triple> unshieldedColliders = new HashSet<>();
        Set<Triple> _unshieldedColliders;

        do {
            _unshieldedColliders = new HashSet<>(unshieldedColliders);
            LvLite.orientCollidersAndRemoveEdges(pag, fciOrient, best, scorer, unshieldedColliders, cpdag, knowledge,
                    allowTucks, verbose);
        } while (!unshieldedColliders.equals(_unshieldedColliders));

        LvLite.finalOrientation(fciOrient, pag, scorer, completeRuleSetUsed, doDiscriminatingPathTailRule,
                doDiscriminatingPathColliderRule, verbose);

        return GraphUtils.replaceNodes(pag, this.score.getVariables());
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
     * Sets the allowTucks flag to the specified value.
     *
     * @param allowTucks the boolean value indicating whether tucks are allowed
     */
    public void setAllowTucks(boolean allowTucks) {
        this.allowTucks = allowTucks;
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

    public void setAllowInternalRandomness(boolean allowInternalRandomness) {
        this.allowInternalRandomness = allowInternalRandomness;
    }
}
