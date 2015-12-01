///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
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

import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;


/**
 * <p></p>This is experimental; you should use it. It will probably be
 * removed from the repository.</p>
 * <p>
 * <p></p>Extends Erin Korber's implementation of the Fast Causal Inference algorithm (found in Fci.java) with Jiji Zhang's
 * Augmented FCI rules (found in sec. 4.1 of Zhang's 2006 PhD dissertation, "Causal Inference and Reasoning in Causally
 * Insufficient Systems").
 * </p>
 * <p></p>This class is based off a copy of Fci.java taken from the repository on 2008/12/16, revision 7306. The extension is
 * done by extending doFinalOrientation() with methods for Zhang's rules R5-R10 which implements the augmented search.
 * (By a remark of Zhang's, the rule applications can be staged in this way.)
 * </p>
 *
 * @author Erin Korber, June 2004
 * @author Alex Smith, December 2008
 * @author Joseph Ramsey
 * @author Choh-Man Teng
 * @deprecated
 */
public final class TFci implements GraphSearch {

    /**
     * The PAG being constructed.
     */
    private Graph graph;

    /**
     * The background knowledge.
     */
    private IKnowledge knowledge = new Knowledge2();

    /**
     * The variables to search over (optional)
     */
    private List<Node> variables = new ArrayList<Node>();

    private IndependenceTest independenceTest;

    /**
     * flag for complete rule set, true if should use complete rule set, false otherwise.
     */
    private boolean completeRuleSetUsed = false;

    /**
     * True iff the possible dsep search is done.
     */
    private boolean possibleDsepSearchDone = true;

    /**
     * The maximum length for any discriminating path. -1 if unlimited; otherwise, a positive integer.
     */
    private int maxPathLength = -1;

    /**
     * The depth for the fast adjacency search.
     */
    private int depth = -1;

    /**
     * The logger to use.
     */
    private TetradLogger logger = TetradLogger.getInstance();

    /**
     * True iff verbose output should be printed.
     */
    private boolean verbose = false;
    private ConcurrentMap<Node, Integer> hashIndices;
    private double penaltyDiscount = 2;
    private Graph initialGraph;


    //============================CONSTRUCTORS============================//

    /**
     * Constructs a new FCI search for the given independence test and background knowledge.
     */
    public TFci(IndependenceTest independenceTest) {
        if (independenceTest == null || knowledge == null) {
            throw new NullPointerException();
        }

        this.independenceTest = independenceTest;
        this.variables.addAll(independenceTest.getVariables());
        buildIndexing(independenceTest.getVariables());
    }

    //========================PUBLIC METHODS==========================//

    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        if (depth < -1) {
            throw new IllegalArgumentException(
                    "Depth must be -1 (unlimited) or >= 0: " + depth);
        }

        this.depth = depth;
    }

    public long getElapsedTime() {
        throw new UnsupportedOperationException();
    }

    public Graph search() {
        return search(new FasStableConcurrent(getIndependenceTest()));
    }

    public void setInitialGraph(Graph initialGraph) {
        this.initialGraph = initialGraph;
    }

    public Graph search(IFas fas) {
        logger.log("info", "Starting FCI algorithm.");
        logger.log("info", "Independence test = " + getIndependenceTest() + ".");

        fas.setKnowledge(getKnowledge());
        fas.setDepth(depth);
        fas.setVerbose(verbose);
        fas.setInitialGraph(initialGraph);
        this.graph = fas.search();

        graph.reorientAllWith(Endpoint.CIRCLE);

        SepsetProducer sp;

        if (possibleDsepSearchDone) {
//            sp = new SepsetsPossibleDsep(graph, getIndependenceTest(), knowledge, depth, maxPathLength);
            sp = new SepsetsMaxPValuePossDsep(graph, getIndependenceTest(), null, depth, maxPathLength);
        } else {
            sp = new SepsetsMaxPValue(graph, getIndependenceTest(), null, depth);
        }

        // The original FCI, with or without JiJi Zhang's orientation rules
        // Optional step: Possible Dsep. (Needed for correctness but very time consuming.)
        new FciOrientT(new SepsetsMaxPValue(graph, independenceTest, null, getDepth()),
                independenceTest).ruleR0(graph);

        for (Edge edge : new ArrayList<>(graph.getEdges())) {
            Node x = edge.getNode1();
            Node y = edge.getNode2();

            List<Node> sepset = sp.getSepset(x, y);

            if (sepset != null) {
                graph.removeEdge(x, y);
                System.out.println("Possible DSEP Removed " + x + "--- " + y + " sepset = " + sepset);
            }
        }

        // Reorient all edges as o-o.
        graph.reorientAllWith(Endpoint.CIRCLE);

        // Step CI C (Zhang's step F3.)
        long time5 = System.currentTimeMillis();
        FciOrientT fciOrient = new FciOrientT(new SepsetsMaxPValue(graph, independenceTest, null,
                getDepth()), independenceTest);
//        FciOrientT fciOrient = new FciOrientT(new SepsetsMaxPValuePossDsep(graph, independenceTest, null,
//                getDepth(), maxPathLength), independenceTest);
        fciOrient.ruleR0(graph);

        long time6 = System.currentTimeMillis();
        logger.log("info", "Step CI C: " + (time6 - time5) / 1000. + "s");

        fciOrient.setCompleteRuleSetUsed(completeRuleSetUsed);
        fciOrient.setMaxPathLength(maxPathLength);
        fciOrient.setKnowledge(knowledge);
        fciOrient.doFinalOrientation(graph);
        return graph;
    }

    public IKnowledge getKnowledge() {
        return knowledge;
    }

    public void setKnowledge(IKnowledge knowledge) {
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
        return completeRuleSetUsed;
    }

    /**
     * @param completeRuleSetUsed set to true if Zhang's complete rule set should be used, false if only R1-R4 (the rule
     *                            set of the original FCI) should be used. False by default.
     */
    public void setCompleteRuleSetUsed(boolean completeRuleSetUsed) {
        this.completeRuleSetUsed = completeRuleSetUsed;
    }

    public boolean isPossibleDsepSearchDone() {
        return possibleDsepSearchDone;
    }

    public void setPossibleDsepSearchDone(boolean possibleDsepSearchDone) {
        this.possibleDsepSearchDone = possibleDsepSearchDone;
    }

    /**
     * @return the maximum length of any discriminating path, or -1 of unlimited.
     */
    public int getMaxPathLength() {
        return maxPathLength == Integer.MAX_VALUE ? -1 : maxPathLength;
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
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * The independence test.
     */
    public IndependenceTest getIndependenceTest() {
        return independenceTest;
    }

    public double getPenaltyDiscount() {
        return penaltyDiscount;
    }

    public void setPenaltyDiscount(double penaltyDiscount) {
        this.penaltyDiscount = penaltyDiscount;
    }

    //===========================PRIVATE METHODS=========================//

    private void buildIndexing(List<Node> nodes) {
        this.hashIndices = new ConcurrentHashMap<>();
        for (Node node : nodes) {
            this.hashIndices.put(node, variables.indexOf(node));
        }
    }
}




