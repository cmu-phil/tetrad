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

import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.DepthChoiceGenerator;
import edu.cmu.tetrad.util.TetradLogger;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Does a FCI-style latent variable search using permutation-based reasoning. Follows GFCI to
 * an extent; the GFCI reference is this:
 * <p>
 * J.M. Ogarrio and P. Spirtes and J. Ramsey, "A Hybrid Causal Search Algorithm
 * for Latent Variable Models," JMLR 2016.
 *
 * @author jdramsey
 */
public final class Bfci1 implements GraphSearch {

    // The score used, if GS is used to build DAGs.
    private final Score score;

    // The logger to use.
    private final TetradLogger logger = TetradLogger.getInstance();

    // The covariance matrix being searched over, if continuous data is supplied. This is
    // no used by the algorithm but can be retrieved by another method if desired
    ICovarianceMatrix covarianceMatrix;

    // The test used if Pearl's method is used ot build DAGs
    private IndependenceTest test;

    // Flag for complete rule set, true if you should use complete rule set, false otherwise.
    private boolean completeRuleSetUsed = true;

    // The maximum length for any discriminating path. -1 if unlimited; otherwise, a positive integer.
    private int maxPathLength = -1;

    // True iff verbose output should be printed.
    private boolean verbose;

    // The print stream that output is directed to.
    private PrintStream out = System.out;

    // GRaSP parameters
    private int numStarts = 1;
    private int depth = 4;
    private boolean useRaskuttiUhler;
    private boolean useDataOrder = true;
    private boolean useScore = true;
    private Graph graph;
    private boolean doDiscriminatingPathRule = true;

    //============================CONSTRUCTORS============================//
    public Bfci1(IndependenceTest test, Score score) {
        this.test = test;
        this.score = score;
    }

    //========================PUBLIC METHODS==========================//
    public Graph search() {
        this.logger.log("info", "Starting FCI algorithm.");
        this.logger.log("info", "Independence test = " + getTest() + ".");

        TeyssierScorer scorer = new TeyssierScorer(test, score);

        // Run BOSS-tuck to get a CPDAG (like GFCI with FGES)...
        Boss boss = new Boss(scorer);
        boss.setAlgType(Boss.AlgType.BOSS);
        boss.setUseScore(useScore);
        boss.setUseRaskuttiUhler(useRaskuttiUhler);
        boss.setUseDataOrder(useDataOrder);
        boss.setDepth(depth);
        boss.setNumStarts(numStarts);
        boss.setVerbose(false);

        List<Node> variables = this.score.getVariables();
        assert variables != null;

        boss.bestOrder(variables);
        this.graph = boss.getGraph(false);

        // Keep a copy of this CPDAG.
        Graph cpdag = new EdgeListGraph(this.graph);

        // Orient the CPDAG with all circle endpoints...
        this.graph.reorientAllWith(Endpoint.CIRCLE);

        // Copy the colliders from the copy of the CPDAG into the o-o graph.
        copyColliders(cpdag);

        // Remove as many edges as possible using the "reduce" rule, orienting as many
        // arrowheads this way as possible.
        reduce(scorer);

        retainUnshieldedColliders();

//        SepsetProducer sepsets = new SepsetsTeyssier(this.graph, scorer, null, depth);
        SepsetProducer sepsets = new SepsetsGreedy(this.graph, test, null, depth);

        // Apply final FCI orientation rules.
        FciOrient fciOrient = new FciOrient(sepsets);
        fciOrient.setCompleteRuleSetUsed(this.completeRuleSetUsed);
        fciOrient.setDoDiscriminatingPathRule(this.doDiscriminatingPathRule);
        fciOrient.setChangeFlag(false);
        fciOrient.setMaxPathLength(this.maxPathLength);
        fciOrient.doFinalOrientation(graph);

        this.graph.setPag(true);

        return this.graph;
    }

    private void reduce(TeyssierScorer scorer) {
        for (Edge edge : graph.getEdges()) {
            Node a = edge.getNode1();
            Node b = edge.getNode2();

            reduceVisit(scorer, a, b);
        }
    }

    private void reduceVisit(TeyssierScorer scorer, Node a, Node b) {
        List<Node> inTriangle = new ArrayList<>(graph.getAdjacentNodes(a));
        inTriangle.retainAll(graph.getAdjacentNodes(b));

        DepthChoiceGenerator gen = new DepthChoiceGenerator(inTriangle.size(), inTriangle.size());
        int[] choice;

        List<Node> curColl = new ArrayList<>();

        for (Node x : inTriangle) {
            if (graph.isDefCollider(a, x, b)) {
                curColl.add(x);
            }
        }

        W:
        while ((choice = gen.next()) != null) {
            List<Node> after = GraphUtils.asList(choice, inTriangle);
            List<Node> before = new ArrayList<>(inTriangle);

            for (Node x : curColl) {
                if (!after.contains(x)) continue W;
            }

            before.removeAll(after);

            List<Node> perm = new ArrayList<>(before);
            perm.add(a);
            perm.add(b);

            scorer.score(perm);

            if (!scorer.adjacent(a, b) && graph.isAdjacentTo(a, b)) {
                graph.removeEdge(a, b);

                for (Node x : after) {
                    if (graph.isAdjacentTo(a, x) && graph.isAdjacentTo(b, x)) {
                        graph.setEndpoint(a, x, Endpoint.ARROW);
                        graph.setEndpoint(b, x, Endpoint.ARROW);
                    }
                }

                break;
            }

            scorer.goToBookmark();
        }
    }

    private void copyColliders(Graph cpdag) {
        List<Node> nodes = this.graph.getNodes();

        for (Node b : nodes) {
            List<Node> adjacentNodes = this.graph.getAdjacentNodes(b);

            if (adjacentNodes.size() < 2) {
                continue;
            }

            ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                Node a = adjacentNodes.get(combination[0]);
                Node c = adjacentNodes.get(combination[1]);

                if (cpdag.isDefCollider(a, b, c)) {
                    this.graph.setEndpoint(a, b, Endpoint.ARROW);
                    this.graph.setEndpoint(c, b, Endpoint.ARROW);
                }
            }
        }
    }

    public void retainUnshieldedColliders() {
        Graph orig = new EdgeListGraph(graph);
        this.graph.reorientAllWith(Endpoint.CIRCLE);
        List<Node> nodes = this.graph.getNodes();

        for (Node b : nodes) {
            List<Node> adjacentNodes = this.graph.getAdjacentNodes(b);

            if (adjacentNodes.size() < 2) {
                continue;
            }

            ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                Node a = adjacentNodes.get(combination[0]);
                Node c = adjacentNodes.get(combination[1]);

                if (orig.isDefCollider(a, b, c) && !orig.isAdjacentTo(a, c)) {
                    this.graph.setEndpoint(a, b, Endpoint.ARROW);
                    this.graph.setEndpoint(c, b, Endpoint.ARROW);
                }
            }
        }
    }

    /**
     * @return true if Zhang's complete rule set should be used, false if only
     * R1-R4 (the rule set of the original FCI) should be used. False by
     * default.
     */
    public boolean isCompleteRuleSetUsed() {
        return this.completeRuleSetUsed;
    }

    /**
     * @param completeRuleSetUsed set to true if Zhang's complete rule set
     *                            should be used, false if only R1-R4 (the rule set of the original FCI)
     *                            should be used. False by default.
     */
    public void setCompleteRuleSetUsed(boolean completeRuleSetUsed) {
        this.completeRuleSetUsed = completeRuleSetUsed;
    }

    /**
     * @return the maximum length of any discriminating path, or -1 of
     * unlimited.
     */
    public int getMaxPathLength() {
        return this.maxPathLength;
    }

    /**
     * @param maxPathLength the maximum length of any discriminating path, or -1
     *                      if unlimited.
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

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * The independence test.
     */
    public IndependenceTest getTest() {
        return this.test;
    }

    public void setTest(IndependenceTest test) {
        this.test = test;
    }

    public ICovarianceMatrix getCovMatrix() {
        return this.covarianceMatrix;
    }

    public ICovarianceMatrix getCovarianceMatrix() {
        return this.covarianceMatrix;
    }

    public void setCovarianceMatrix(ICovarianceMatrix covarianceMatrix) {
        this.covarianceMatrix = covarianceMatrix;
    }

    public PrintStream getOut() {
        return this.out;
    }

    public void setOut(PrintStream out) {
        this.out = out;
    }

    public void setNumStarts(int numStarts) {
        this.numStarts = numStarts;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

    public void setUseRaskuttiUhler(boolean useRaskuttiUhler) {
        this.useRaskuttiUhler = useRaskuttiUhler;
    }

    public void setUseScore(boolean useScore) {
        this.useScore = useScore;
    }

    public void setUseDataOrder(boolean useDataOrder) {
        this.useDataOrder = useDataOrder;
    }

    public void setDoDiscriminatingPathRule(boolean doDiscriminatingPathRule) {
        this.doDiscriminatingPathRule = doDiscriminatingPathRule;
    }
}
