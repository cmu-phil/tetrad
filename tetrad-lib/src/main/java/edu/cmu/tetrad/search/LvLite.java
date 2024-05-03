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
import org.apache.commons.lang3.tuple.Pair;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The LvLite class implements the IGraphSearch interface and represents a search algorithm for learning the structure
 * of a graphical model from observational data.
 * <p>
 * This class provides methods for running the search algorithm and obtaining the learned pattern as a PAG (Partially
 * Annotated Graph).
 *
 * @author josephramsey
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
     */
    private long seed = -1;
    /**
     * This flag represents whether the Bes algorithm should be used in the search.
     * <p>
     * If set to true, the Bes algorithm will be used. If set to false, the Bes algorithm will not be used.
     * <p>
     * By default, the value of this flag is false.
     */
    private boolean useBes;
    /**
     * This variable represents whether the discriminating path rule is used in the LvLite class.
     * <p>
     * The discriminating path rule is a rule used in the search algorithm. It determines whether the algorithm
     * considers discriminating paths when searching for patterns in the data.
     * <p>
     * By default, the value of this variable is set to false, indicating that the discriminating path rule is not used.
     * To enable the use of the discriminating path rule, set the value of this variable to true using the
     * {@link #setDoDiscriminatingPathRule(boolean)} method.
     */
    private boolean doDiscriminatingPathRule = false;

    /**
     * LvLite constructor. Initializes a new object of LvLite search algorithm with the given IndependenceTest
     * and Score object.
     *
     * @param test  The IndependenceTest object to be used for conditional independence testing.
     * @param score The Score object to be used for scoring DAGs.
     * @throws NullPointerException if score is null.
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
        permutationSearch.search();
        List<Node> best = permutationSearch.getOrder();

        TetradLogger.getInstance().forceLogMessage("Best order: " + best);

        TeyssierScorer teyssierScorer = new TeyssierScorer(independenceTest, score);
        teyssierScorer.score(best);
        Graph dag = teyssierScorer.getGraph(false);
        Graph cpdag = teyssierScorer.getGraph(true);
        Graph pag = new EdgeListGraph(cpdag);
        pag.reorientAllWith(Endpoint.CIRCLE);

        SepsetProducer sepsets = new SepsetsGreedy(pag, this.independenceTest, null, this.depth, knowledge);

        FciOrient fciOrient = new FciOrient(sepsets);
        fciOrient.setCompleteRuleSetUsed(completeRuleSetUsed);
        fciOrient.setDoDiscriminatingPathColliderRule(doDiscriminatingPathRule);
        fciOrient.setDoDiscriminatingPathTailRule(doDiscriminatingPathRule);
        fciOrient.setVerbose(verbose);
        fciOrient.setKnowledge(knowledge);

        fciOrient.fciOrientbk(knowledge, pag, best);

        // Copy unshielded colliders from DAG to PAG
        for (int i = 0; i < best.size(); i++) {
            for (int j = i + 1; j < best.size(); j++) {
                for (int k = j + 1; k < best.size(); k++) {
                    Node a = best.get(i);
                    Node b = best.get(j);
                    Node c = best.get(k);

                    if (dag.isAdjacentTo(a, c) && dag.isAdjacentTo(b, c) && !dag.isAdjacentTo(a, b)
                        && dag.getEdge(a, c).pointsTowards(c) && dag.getEdge(b, c).pointsTowards(c)) {
                        if (FciOrient.isArrowheadAllowed(a, c, pag, knowledge) && FciOrient.isArrowheadAllowed(b, c, pag, knowledge)) {
                            pag.setEndpoint(a, c, Endpoint.ARROW);
                            pag.setEndpoint(b, c, Endpoint.ARROW);

                            if (verbose) {
                                TetradLogger.getInstance().forceLogMessage("Copying unshielded collider " + a + " -> " + c + " <- " + b
                                                                           + " from CPDAG to PAG");
                            }
                        }
                    }
                }
            }
        }

        teyssierScorer.bookmark();

        Set<Edge> toRemove = new HashSet<>();

        Set<Pair<Node, Node>> arrows = new HashSet<>();

        // Our extra collider orientation step to orient <-> edges:
        // For every <a, b, c>, with a, b, c adjacent in the PAG
        for (int i = 0; i < best.size(); i++) {
            for (int j = 0; j < best.size(); j++) {
                for (int k = 0; k < best.size(); k++) {
                    if (i == j || i == k || j == k) {
                        continue;
                    }

                    Node a = best.get(i);
                    Node b = best.get(j);
                    Node c = best.get(k);

                    Edge ab = cpdag.getEdge(a, b);
                    Edge bc = cpdag.getEdge(b, c);
                    Edge ac = cpdag.getEdge(a, c);

                    Edge _ab = pag.getEdge(a, b);
                    Edge _bc = pag.getEdge(b, c);
                    Edge _ac = pag.getEdge(a, c);

                    if (ab != null && (bc != null && bc.pointsTowards(c)) && (ac != null && ac.pointsTowards(c))) {
                        if (_ab != null && (_bc != null && pag.getEndpoint(b, c) == Endpoint.ARROW) && _ac != null) {
                            teyssierScorer.goToBookmark();

                            // Tuck the edge b -> c
                            teyssierScorer.tuck(c, b);

                            // If the score is the same (drops less than a threshold amount)), and the collider is allowed,
                            // remove the a *-* c edge from the pag and orient a *-> b <-* c.
                            if (!teyssierScorer.adjacent(a, c)) {
                                if (FciOrient.isArrowheadAllowed(a, c, pag, knowledge) && FciOrient.isArrowheadAllowed(b, c, pag, knowledge)) {
                                    toRemove.add(pag.getEdge(a, c));
                                    arrows.add(Pair.of(a, b));
                                    arrows.add(Pair.of(c, b));

                                    TetradLogger.getInstance().forceLogMessage("Scheduling removal of " + pag.getEdge(a, c));
                                    TetradLogger.getInstance().forceLogMessage("Scheduling " + a + " -> " + b + " <- " + c + " for orientation.");
                                }
                            }
                        }
                    }
                }
            }
        }

        for (Edge edge : toRemove) {
            Edge n12 = pag.getEdge(edge.getNode1(), edge.getNode2());
            pag.removeEdge(n12);

            if (verbose) {
                TetradLogger.getInstance().forceLogMessage("Removing edge " + n12);
            }
        }

        for (Pair<Node, Node> arrow : arrows) {
            if (!pag.isAdjacentTo(arrow.getLeft(), arrow.getRight())) {
                continue;
            }

            if (pag.paths().isAncestorOf(arrow.getRight(), arrow.getLeft())) {
                continue;
            }

            Edge edge = pag.getEdge(arrow.getLeft(), arrow.getRight());

            pag.setEndpoint(arrow.getLeft(), arrow.getRight(), Endpoint.ARROW);

            if (verbose) {
                TetradLogger.getInstance().forceLogMessage("Orienting " + edge + " to " + pag.getEdge(arrow.getLeft(), arrow.getRight()));
            }
        }

        fciOrient.doFinalOrientation(pag);

        for (Edge edge : pag.getEdges()) {
            if (Edges.isBidirectedEdge(edge)) {
                Node x = edge.getNode1();
                Node y = edge.getNode2();

                if (pag.paths().existsDirectedPath(x, y)) {
                    pag.setEndpoint(y, x, Endpoint.TAIL);
                } else if (pag.paths().existsDirectedPath(y, x)) {
                    pag.setEndpoint(x, y, Endpoint.TAIL);
                }
            }
        }

        GraphUtils.replaceNodes(pag, this.independenceTest.getVariables());
//        pag = GraphTransforms.zhangMagFromPag(pag);
//        pag = GraphTransforms.dagToPag(pag);

//        fciOrient.fciOrientbk(knowledge, pag, best);

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
     * Sets the seed for the random number generator used by the search algorithm.
     *
     * @param seed The seed to set for the random number generator.
     */
    public void setSeed(long seed) {
        this.seed = seed;
    }

    /**
     * Sets whether to use Bes algorithm for search.
     *
     * @param useBes True, if using Bes algorithm. False, otherwise.
     */
    public void setUseBes(boolean useBes) {
        this.useBes = useBes;
    }

    /**
     * Sets whether to use the discriminating path rule during the search algorithm.
     *
     * @param doDiscriminatingPathRule true if the discriminating path rule should be used, false otherwise.
     */
    public void setDoDiscriminatingPathRule(boolean doDiscriminatingPathRule) {
        this.doDiscriminatingPathRule = doDiscriminatingPathRule;
    }
}
