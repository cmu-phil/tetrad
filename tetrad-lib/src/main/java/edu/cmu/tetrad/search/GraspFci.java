/// ////////////////////////////////////////////////////////////////////////////
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
/// ////////////////////////////////////////////////////////////////////////////
package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.util.TetradLogger;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Uses GRaSP in the StarFCI algorithm. the reference for GraSP is here:
 * <p>
 * For GRaSP either a score or a test is needed. StarFci requires a test. So both are needed.
 * <p>
 * This class is configured to respect knowledge of forbidden and required edges, including knowledge of temporal
 * tiers.
 *
 * @author josephramsey
 * @author bryanandrews
 * @version $Id: $Id
 * @see StarFci
 * @see Grasp
 */
public final class GraspFci extends StarFci {

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
     * Constructs a new GraspFci object.
     *
     * @param test  The independence test.
     * @param score a {@link Score} object
     */
    public GraspFci(IndependenceTest test, Score score) {
        super(test);
        if (score == null) {
            throw new NullPointerException();
        }

        this.score = score;
        this.independenceTest = test;
    }

    public @NotNull Graph getMarkovCpdag() throws InterruptedException {
        if (verbose) {
            TetradLogger.getInstance().log("Starting GRaSP.");
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
        Graph cpdag = alg.getGraph(true);

        if (verbose) {
            TetradLogger.getInstance().log("Finished GRaSP.");
        }
        return cpdag;
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
        super.setDepth(depth);
    }
}
