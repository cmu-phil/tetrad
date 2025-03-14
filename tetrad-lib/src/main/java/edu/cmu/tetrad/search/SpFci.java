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
import edu.cmu.tetrad.util.TetradLogger;

import java.io.PrintStream;
import java.util.List;

/**
 * Uses SP in place of FGES for the initial step in the StarFci algorithm.
 * <p>
 * For SP only a score is needed, but there are steps in GFCI that require a test, so for this method, both a test and a
 * score need to be given.
 * <p>
 * Note that SP considers all permutations of the algorithm, which is exponential in the number of variables. So SP is
 * limited to about 10 variables.
 * <p>
 * This class is configured to respect knowledge of forbidden and required edges, including knowledge of temporal
 * tiers.
 *
 * @author josephramsey
 * @author bryan andrews
 * @version $Id: $Id
 * @see StarFci
 * @see Sp
 */
public final class SpFci extends StarFci {

    /**
     * The score.
     */
    private final Score score;
    /**
     * The conditional independence test.
     */
    private final IndependenceTest independenceTest;
    /**
     * The background knowledge.
     */
    private Knowledge knowledge = new Knowledge();
    /**
     * Flag for complete rule set, true if you should use the complete rule set, false otherwise.
     */
    private boolean completeRuleSetUsed = true;
    /**
     * The maximum length for any discriminating path. -1 if unlimited; otherwise, a positive integer.
     */
    private int maxDiscriminatingPathLength = -1;
    /**
     * The maxDegree for the fast adjacency search.
     */
    private int maxDegree = -1;
    /**
     * True iff verbose output should be printed.
     */
    private boolean verbose;

    /**
     * Constructor; requires by ta test and a score, over the same variables.
     *
     * @param test  The test.
     * @param score The score.
     */
    public SpFci(IndependenceTest test, Score score) {
        super(test);
        if (score == null) {
            throw new NullPointerException();
        }
        this.score = score;
        this.independenceTest = test;
    }

    public Graph getMarkovCpdag() throws InterruptedException {
        Graph cpdag;
        if (verbose) {
            TetradLogger.getInstance().log("Starting SP.");
        }

        Sp subAlg = new Sp(this.score);
        PermutationSearch alg = new PermutationSearch(subAlg);
        alg.setKnowledge(this.knowledge);
        cpdag = alg.search();

        if (verbose) {
            TetradLogger.getInstance().log("Finished SP.");
        }
        return cpdag;
    }

    /**
     * Returns The maximum indegree of the output graph.
     *
     * @return This maximum.
     */
    public int getMaxDegree() {
        return this.maxDegree;
    }

    /**
     * Sets the max degree of the search.
     *
     * @param maxDegree This maximum.
     */
    public void setMaxDegree(int maxDegree) {
        if (maxDegree < -1) {
            throw new IllegalArgumentException("Depth must be -1 (unlimited) or >= 0: " + maxDegree);
        }

        this.maxDegree = maxDegree;
    }

    /**
     * Returns the knowledge.
     *
     * @return This knowledge.
     */
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * Sets the knoweldge used in the search.
     *
     * @param knowledge This knowledge.
     */
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }

    /**
     * Returns whether the complete rule set is used.
     *
     * @return True if Zhang's complete rule set should be used, False if only R1-R4 (the rule set of the original FCI)
     * should be used. False by default.
     */
    public boolean isCompleteRuleSetUsed() {
        return this.completeRuleSetUsed;
    }

    /**
     * Sets whether Zhang's complete rule set is used.
     *
     * @param completeRuleSetUsed set to true if Zhang's complete rule set should be used, false if only R1-R4 (the rule
     *                            set of the original FCI) should be used. False by default.
     */
    public void setCompleteRuleSetUsed(boolean completeRuleSetUsed) {
        this.completeRuleSetUsed = completeRuleSetUsed;
    }

    /**
     * Returns the maximum length of any discriminating path, or -1 of unlimited.
     *
     * @return This length.
     */
    public int getMaxDiscriminatingPathLength() {
        return this.maxDiscriminatingPathLength;
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
     * Sets whether verbose output is printed.
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
     * Sets the output stream used to print. Unused, but the implementation needs to be here.
     *
     * @param out This print stream.
     */
    public void setOut(PrintStream out) {
        // The print stream that output is directed to.
    }
}
