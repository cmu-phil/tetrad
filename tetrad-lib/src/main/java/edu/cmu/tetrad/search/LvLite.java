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
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.utils.FciOrient;
import edu.cmu.tetrad.search.utils.SepsetProducer;
import edu.cmu.tetrad.search.utils.SepsetsGreedy;
import edu.cmu.tetrad.search.utils.TeyssierScorer;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.List;

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
public final class LvLite implements IGraphSearch {

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
     * True iff verbose output should be printed.
     */
    private boolean verbose;
    /**
     * The number of starts for GRaSP.
     */
    private int numStarts = 1;
    /**
     * Whether to use data order.
     */
    private boolean useDataOrder = true;
    /**
     * The depth for GRaSP.
     */
    private int depth = -1;
    /**
     * The seed used for random number generation. If the seed is not set explicitly, it will be initialized with a
     * value of -1. The seed is used for producing the same sequence of random numbers every time the program runs.
     *
     * @see LvLite#setSeed(long)
     */
    private long seed = -1;

    /**
     * The threshold for tucking.
     */
    private double equalityThreshold;

    private boolean useBes;

    /**
     * Constructs a new GraspFci object.
     *
     * @param test  The independence test.
     * @param score a {@link Score} object
     */
    public LvLite(IndependenceTest test, Score score) {
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

        Boss suborderSearch = new Boss(score);
        suborderSearch.setKnowledge(knowledge);
        suborderSearch.setResetAfterBM(true);
        suborderSearch.setResetAfterRS(true);
        suborderSearch.setVerbose(verbose);
        suborderSearch.setUseBes(useBes);
        suborderSearch.setUseDataOrder(useDataOrder);
        suborderSearch.setNumStarts(numStarts);
        PermutationSearch permutationSearch = new PermutationSearch(suborderSearch);
        permutationSearch.setKnowledge(knowledge);
        permutationSearch.setSeed(seed);
        permutationSearch.search();
        List<Node> best = permutationSearch.getOrder();

        TeyssierScorer teyssierScorer = new TeyssierScorer(independenceTest, score);
        teyssierScorer.score(best);
        Graph cpdag = teyssierScorer.getGraph(true);
        Graph pag = new EdgeListGraph(cpdag);
        pag.reorientAllWith(Endpoint.CIRCLE);

        for (int i = 0; i < best.size(); i++) {
            for (int j = i + 1; j < best.size(); j++) {
                for (int k = j + 1; k < best.size(); k++) {
                    Node a = best.get(i);
                    Node b = best.get(j);
                    Node c = best.get(k);

                    if (cpdag.isAdjacentTo(a, c) && cpdag.isAdjacentTo(b, c) && !cpdag.isAdjacentTo(a, b)
                        && cpdag.getEdge(a, c).pointsTowards(c) && cpdag.getEdge(b, c).pointsTowards(c)) {
                        pag.setEndpoint(a, c, Endpoint.ARROW);
                        pag.setEndpoint(b, c, Endpoint.ARROW);
                    }
                }
            }
        }

        double s1 = teyssierScorer.score(best);
        teyssierScorer.bookmark();

        // Look for every triangle in cpdag A->C, B->C, A->B
        for (int i = 0; i < best.size(); i++) {
            for (int j = i + 1; j < best.size(); j++) {
                for (int k = j + 1; k < best.size(); k++) {
                    Node a = best.get(i);
                    Node b = best.get(j);
                    Node c = best.get(k);

                    Edge ab = cpdag.getEdge(a, b);
                    Edge bc = cpdag.getEdge(b, c);
                    Edge ac = cpdag.getEdge(a, c);

                    if (ab != null && bc != null && ac != null) {
                        if (ab.pointsTowards(b) && bc.pointsTowards(c)) {
                            teyssierScorer.goToBookmark();
                            teyssierScorer.tuck(a, best.indexOf(b));
                            double s2 = teyssierScorer.score();

                            if (s2 > s1 - equalityThreshold) {
                                pag.removeEdge(a, c);
                                pag.setEndpoint(c, b, Endpoint.ARROW);
                            }
                        }
                    }
                }
            }
        }

        SepsetProducer sepsets = new SepsetsGreedy(pag, this.independenceTest, null, this.depth, knowledge);

        FciOrient fciOrient = new FciOrient(sepsets);
        fciOrient.setCompleteRuleSetUsed(completeRuleSetUsed);
        fciOrient.setDoDiscriminatingPathColliderRule(true);
        fciOrient.setDoDiscriminatingPathTailRule(true);
        fciOrient.setVerbose(verbose);
        fciOrient.setKnowledge(knowledge);
        fciOrient.doFinalOrientation(pag);

        GraphUtils.replaceNodes(pag, this.independenceTest.getVariables());

        pag = GraphTransforms.zhangMagFromPag(pag);
        pag = GraphTransforms.dagToPag(pag);

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
     * Sets whether to use data order for GRaSP (as opposed to random order) for the first step of GRaSP
     *
     * @param useDataOrder True, if so.
     */
    public void setUseDataOrder(boolean useDataOrder) {
        this.useDataOrder = useDataOrder;
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
     * Sets the threshold used in the LV-Lite search algorithm.
     *
     * @param equalityThreshold The threshold value to be set.
     */
    public void setEqualityThreshold(double equalityThreshold) {
        if (equalityThreshold < 0) throw new IllegalArgumentException("Threshold should be >= 0.");
        this.equalityThreshold = equalityThreshold;
    }

    public void setUseBes(boolean useBes) {
        this.useBes = useBes;
    }
}
