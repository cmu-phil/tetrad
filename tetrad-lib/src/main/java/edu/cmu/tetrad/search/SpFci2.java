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
import edu.cmu.tetrad.util.TetradLogger;

import java.io.PrintStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static edu.cmu.tetrad.graph.GraphUtils.gfciExtraEdgeRemovalStepUnionOfAdj;

/**
 * Uses SP in place of FGES for the initial step in the GFCI algorithm. This tends to produce an accurate PAG than GFCI
 * as a result, for the latent variables case. This is a simple substitution; the reference for GFCI is here:
 * <p>
 * J.M. Ogarrio and P. Spirtes and J. Ramsey, "A Hybrid Causal Search Algorithm for Latent Variable Models," JMLR 2016.
 * Here, SP has been substituted for FGES.
 * <p>
 * The reference for the SP algorithm is here:
 * <p>
 * Raskutti, G., &amp; Uhler, C. (2018). Learning directed acyclic graph models based on sparsest permutations. Stat,
 * 7(1), e183.
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
 * @see Grasp
 * @see GFci
 * @see Sp
 * @see Knowledge
 * @see FciOrient
 */
public final class SpFci2 implements IGraphSearch {

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
     * Indicates the maximum number of variables that can be conditioned on during the search. A negative depth value
     * (-1 in this case) indicates unlimited depth.
     */
    private int depth = -1;
    /**
     * True iff verbose output should be printed.
     */
    private boolean verbose;
    /**
     * True iff the search should guarantee a PAG output.
     */
    private boolean guaranteePag = false;
    /**
     * The method to use for finding sepsets, 1 = greedy, 2 = min-p., 3 = max-p, default min-p.
     */
    private int sepsetFinderMethod;
    /**
     * Indicates whether the search should initialize using a complete graph.
     * By default, this field is set to false, meaning the search does not
     * start from a complete graph. If set to true, the search begins with
     * a complete graph, which may influence both the efficiency and the
     * outcome of the search process.
     */
    private boolean startFromCompleteGraph = false;

    /**
     * Constructor; requires by ta test and a score, over the same variables.
     *
     * @param test  The test.
     * @param score The score.
     */
    public SpFci2(IndependenceTest test, Score score) {
        if (score == null) {
            throw new NullPointerException();
        }
        this.score = score;
        this.independenceTest = test;
    }

    /**
     * Runs the search and returns the discovered PAG.
     *
     * @return This PAG.
     */
    public Graph search() throws InterruptedException {
        List<Node> nodes = this.independenceTest.getVariables();

        if (nodes == null) {
            throw new NullPointerException("Nodes from test were null.");
        }

        if (verbose) {
            TetradLogger.getInstance().log("===Starting GRaSP-FCI===");
        }

        Graph cpdag;

        if (startFromCompleteGraph) {
            TetradLogger.getInstance().log("===Starting with complete graph=== ");
            cpdag = new EdgeListGraph(independenceTest.getVariables());
            cpdag = GraphUtils.completeGraph(cpdag);
        } else {
            cpdag = getCpdag();
        }

        StarFci starFci = new StarFci(cpdag, independenceTest);
        starFci.setKnowledge(knowledge);
        starFci.setDepth(depth);
        starFci.setSepsetFinderMethod(sepsetFinderMethod);
        starFci.setVerbose(verbose);
        starFci.setMaxDiscriminatingPathLength(maxDiscriminatingPathLength);
        starFci.setGuaranteePag(guaranteePag);
        starFci.setCompleteRuleSetUsed(completeRuleSetUsed);

        return starFci.search();
    }

    private Graph getCpdag() throws InterruptedException {
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

    /**
     * Sets the maximum number of variables conditioned on.
     *
     * @param depth This maximum.
     */
    public void setDepth(int depth) {
        this.depth = depth;
    }

    /**
     * Sets whether the search should guarantee the output is a legal PAG.
     *
     * @param guaranteePag True, if so.
     */
    public void setGuaranteePag(boolean guaranteePag) {
        this.guaranteePag = guaranteePag;
    }

    /**
     * Sets the method to use for finding sepsets, 1 = greedy, 2 = min-p., 3 = max-p, default min-p.
     *
     * @param sepsetFinderMethod the method to use for finding sepsets
     */
    public void setSepsetFinderMethod(int sepsetFinderMethod) {
        this.sepsetFinderMethod = sepsetFinderMethod;
    }

    /**
     * Sets whether the search should begin from a complete graph.
     *
     * @param startFromCompleteGraph True if the search should start from a complete graph, false otherwise.
     */
    public void setStartFromCompleteGraph(boolean startFromCompleteGraph) {
        this.startFromCompleteGraph = startFromCompleteGraph;
    }
}
