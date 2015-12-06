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

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.TetradLogger;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;


/**
 * Replaces the FAS search in the previous version with GES followed by PC adjacency removals for more accuracy.
 * Uses conservative collider orientation. Gets sepsets for X---Y from among adjacents of X or of Y. -jdramsey 3/10/2015
 * <p>
 * Following an idea of Spirtes, now it uses more of the information in GES, to calculating possible dsep paths and to
 * utilize unshielded colliders found by GES. 5/31/2015
 * <p>
 * Previous:
 * Extends Erin Korber's implementation of the Fast Causal Inference algorithm (found in Fci.java) with Jiji Zhang's
 * Augmented FCI rules (found in sec. 4.1 of Zhang's 2006 PhD dissertation, "Causal Inference and Reasoning in Causally
 * Insufficient Systems").
 * <p>
 * This class is based off a copy of Fci.java taken from the repository on 2008/12/16, revision 7306. The extension is
 * done by extending doFinalOrientation() with methods for Zhang's rules R5-R10 which implements the augmented search.
 * (By a remark of Zhang's, the rule applications can be staged in this way.)
 *
 * @author Erin Korber, June 2004
 * @author Alex Smith, December 2008
 * @author Joseph Ramsey
 * @author Choh-Man Teng
 */
public final class GFci {

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

    /**
     * The conditional independence test.
     */
    private IndependenceTest independenceTest;

    /**
     * Flag for complete rule set, true if should use complete rule set, false otherwise.
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

    /**
     * The covariance matrix beign searched over. Assumes continuous data.
     */
    ICovarianceMatrix covarianceMatrix;

    /**
     * The sample size.
     */
    int sampleSize;

    /**
     * The penalty discount for the GES search. By default 2.
     */
    private double penaltyDiscount = 2;

    /**
     * The sample prior for the BDeu score (discrete data).
     */
    private double samplePrior = 10;

    /**
     * The structure prior for the Bdeu score (discrete data).
     */
    private double structurePrior = 1;

    /**
     * Map from variables to their column indices in the data set.
     */
    private ConcurrentMap<Node, Integer> hashIndices;

    /**
     * The print stream that output is directed to.
     */
    private PrintStream out = System.out;

    /**
     * True iff one-edge faithfulness is assumed. Speed up the algorith for very large searches. By default false.
     */
    private boolean faithfulnessAssumed = false;

    //============================CONSTRUCTORS============================//

    /**
     * Constructs a new FCI search for the given independence test and background knowledge.
     */
    public GFci(IndependenceTest independenceTest) {
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

    public Graph search() {
        List<Node> nodes = getIndependenceTest().getVariables();

        logger.log("info", "Starting FCI algorithm.");
        logger.log("info", "Independence test = " + getIndependenceTest() + ".");

        this.graph = new EdgeListGraphSingleConnections(nodes);

        DataSet dataSet = (DataSet) independenceTest.getData();

        sampleSize = independenceTest.getSampleSize();
        double penaltyDiscount = getPenaltyDiscount();

        // Adjacency phase

        // Run GES to get an initial graph.
        Fgs ges;
        Graph gesGraph;

        if (dataSet == null || dataSet.isContinuous()) {
            covarianceMatrix = independenceTest.getCov();
            ges = new Fgs(covarianceMatrix);
            ges.setKnowledge(getKnowledge());
            ges.setPenaltyDiscount(penaltyDiscount);
            ges.setVerbose(verbose);
            ges.setLog(false);
            ges.setDepth(getDepth());
            ges.setNumPatternsToStore(0);
          /*  ges.setFaithfulnessAssumed(true);
            Graph initialGraph = ges.search();
            ges.setInitialGraph(initialGraph);*/
            ges.setFaithfulnessAssumed(false);
            graph = ges.search();
            gesGraph = new EdgeListGraphSingleConnections(graph);
        } else if (dataSet.isDiscrete()) {
            ges = new Fgs(dataSet);
            ges.setKnowledge(getKnowledge());
            ges.setPenaltyDiscount(penaltyDiscount);
            ges.setSamplePrior(samplePrior);
            ges.setStructurePrior(structurePrior);
            ges.setStructurePrior(1);
            ges.setVerbose(false);
            ges.setLog(false);
            ges.setDepth(getDepth());
            ges.setNumPatternsToStore(0);
            ges.setFaithfulnessAssumed(faithfulnessAssumed);
            graph = ges.search();
            gesGraph = new EdgeListGraphSingleConnections(graph);
        } else {
            throw new IllegalArgumentException("Mixed data not supported.");
        }

        System.out.println("GES done " + gesGraph.getNumEdges() + " edges in graph");
        SepsetProducer sepsets;

//        if (possibleDsepSearchDone) {
//            sepsets = new SepsetsPossibleDsep(gesGraph, getIndependenceTest(), knowledge, depth,
//                    maxPathLength);
//        } else {
        sepsets = new SepsetsMaxPValue(gesGraph, getIndependenceTest(), null, depth);
//        }

//        if (possibleDsepSearchDone) {
//            System.out.println("Possible Dsep started maxPathLength = " + maxPathLength);
//
//            for (Edge edge : new ArrayList<>(graph.getEdges())) {
//                Node i = edge.getNode1();
//                Node k = edge.getNode2();
//
//                List<Node> j = graph.getAdjacentNodes(i);
//                j.retainAll(graph.getAdjacentNodes(k));
//
//                if (!j.isEmpty()) {
//                    sepsets.getSepset(i, k);
//
//                    if (sepsets.getPValue() > getIndependenceTest().getAlpha()) {
//                        gesGraph.removeEdge(edge);
//                    }
//                }
//            }
//
//            System.out.println("Possible Dsep finished");
//        } else {

        // Look in triangles
        for (Edge edge : gesGraph.getEdges()) {
            Node i = edge.getNode1();
            Node k = edge.getNode2();

            List<Node> j = gesGraph.getAdjacentNodes(i);
            j.retainAll(gesGraph.getAdjacentNodes(k));

            if (!j.isEmpty()) {
                sepsets.getSepset(i, k);

                if (sepsets.getPValue() > getIndependenceTest().getAlpha()) {
                    graph.removeEdge(edge);
                }
            }
        }
//        }

        // Orientation phase.

        // Step CI C, modified collider orientation step for FCI-GES due to Spirtes.
        ruleR0Special(graph, gesGraph, sepsets, ges);

        FciOrient fciOrient = new FciOrient(sepsets);
        fciOrient.setKnowledge(getKnowledge());
        fciOrient.setCompleteRuleSetUsed(completeRuleSetUsed);
        fciOrient.setMaxPathLength(maxPathLength);
        fciOrient.doFinalOrientation(graph);

        GraphUtils.replaceNodes(graph, independenceTest.getVariables());

        //end.

        return graph;
    }

    public static boolean markovIndependent(Graph pattern, Node i, Node k, IndependenceTest test) {
        List<Node> futurei = pattern.getDescendants(Collections.singletonList(i));
        List<Node> boundaryi = pattern.getAdjacentNodes(i);
        boundaryi.remove(k);
        boundaryi.removeAll(futurei);
        List<Node> closurei = new ArrayList<>(boundaryi);
        closurei.add(i);

        if (futurei.contains(k) || closurei.contains(k)) return true;
        if (test.isIndependent(i, k, boundaryi)) return true;

        List<Node> futurek = pattern.getDescendants(Collections.singletonList(k));
        List<Node> boundaryk = pattern.getAdjacentNodes(k);
        boundaryk.removeAll(futurek);
        boundaryk.remove(i);
        List<Node> closurek = new ArrayList<>(boundaryk);
        closurek.add(k);

        if (futurek.contains(i) || closurek.contains(i)) return true;
        if (test.isIndependent(i, k, boundaryk)) return true;

        return false;
    }

    public void ruleR0Special(Graph graph, Graph gesGraph, SepsetProducer sepsets, Fgs ges) {
        SepsetsMaxPValue sepsetProducer = new SepsetsMaxPValue(graph, independenceTest, null, getDepth());

        graph.reorientAllWith(Endpoint.CIRCLE);
        fciOrientbk(knowledge, graph, graph.getNodes());

        System.out.println("R0 start");

        List<Node> nodes = graph.getNodes();

        for (Node b : nodes) {
            List<Node> adjacentNodes = graph.getAdjacentNodes(b);

            if (adjacentNodes.size() < 2) {
                continue;
            }

            ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                Node a = adjacentNodes.get(combination[0]);
                Node c = adjacentNodes.get(combination[1]);

                // Skip triples that are shielded.
                if (graph.isAdjacentTo(a, c)) {
                    continue;
                }

                // Skip triples already oriented as colliders
//                if (graph.isDefCollider(a, b, c)) {
//                    continue;
//                }

                // Skip triple where collider orientations are forbidden by background knowledge
                if (!isArrowpointAllowed(a, b, graph)) {
                    continue;
                }

                if (!isArrowpointAllowed(c, b, graph)) {
                    continue;
                }

                if (gesGraph.isAdjacentTo(a, c)) {
//                    SearchGraphUtils.CpcTripleType type = SearchGraphUtils.getCpcTripleType(a, b, c,
//                            getIndependenceTest(), depth, graph, verbose);
//                    if (type == SearchGraphUtils.CpcTripleType.COLLIDER) {
//                        graph.setEndpoint(a, b, Endpoint.ARROW);
//                        graph.setEndpoint(c, b, Endpoint.ARROW);
//                        logger.log("colliderOrientations", "Copying from GES: " + SearchLogUtils.colliderOrientedMsg(a, b, c));
//                        System.out.println("Copying from GES: " + SearchLogUtils.colliderOrientedMsg(a, b, c));
//                    }

                    if (sepsetProducer.isCollider(a, b, c)) {
                        graph.setEndpoint(a, b, Endpoint.ARROW);
                        graph.setEndpoint(c, b, Endpoint.ARROW);
                        logger.log("colliderOrientations", "Copying from GES: " + SearchLogUtils.colliderOrientedMsg(a, b, c));
                        System.out.println("Copying from GES: " + SearchLogUtils.colliderOrientedMsg(a, b, c));
                    }
                } else {
                    if (gesGraph.isDefCollider(a, b, c)) {
                        graph.setEndpoint(a, b, Endpoint.ARROW);
                        graph.setEndpoint(c, b, Endpoint.ARROW);
                        logger.log("colliderOrientations", "Copying from GES: " + SearchLogUtils.colliderOrientedMsg(a, b, c));
                        System.out.println("Copying from GES: " + SearchLogUtils.colliderOrientedMsg(a, b, c));
                    }
                }

//                if (!gesGraph.isAdjacentTo(a, c)) {
//
//                    // Copy colliders from the GES graph into the current graph where possible
//                    if (gesGraph.isDefCollider(a, b, c)) {
//                        graph.setEndpoint(a, b, Endpoint.ARROW);
//                        graph.setEndpoint(c, b, Endpoint.ARROW);
//                        logger.log("colliderOrientations", "Copying from GES: " + SearchLogUtils.colliderOrientedMsg(a, b, c));
//                        System.out.println("Copying from GES: " + SearchLogUtils.colliderOrientedMsg(a, b, c));
//                    }
//                } else {
//                    if (sepsets.isCollider(a, b, c)) {
//                        graph.setEndpoint(a, b, Endpoint.ARROW);
//                        graph.setEndpoint(c, b, Endpoint.ARROW);
//                        logger.log("colliderOrientations", "On testing: " + SearchLogUtils.colliderOrientedMsg(a, b, c));
//                        System.out.println("On testing: " + SearchLogUtils.colliderOrientedMsg(a, b, c));
//                    }
//                }


            }
        }
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
        return maxPathLength;
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

    public ICovarianceMatrix getCovMatrix() {
        return covarianceMatrix;
    }

    public ICovarianceMatrix getCovarianceMatrix() {
        return covarianceMatrix;
    }

    public void setCovarianceMatrix(ICovarianceMatrix covarianceMatrix) {
        this.covarianceMatrix = covarianceMatrix;
    }

    public PrintStream getOut() {
        return out;
    }

    public void setOut(PrintStream out) {
        this.out = out;
    }

    public void setIndependenceTest(IndependenceTest independenceTest) {
        this.independenceTest = independenceTest;
    }

    public void setFaithfulnessAssumed(boolean faithfulnessAssumed) {
        this.faithfulnessAssumed = faithfulnessAssumed;
    }

    //===========================================PRIVATE METHODS=======================================//

    private void buildIndexing(List<Node> nodes) {
        this.hashIndices = new ConcurrentHashMap<>();
        for (Node node : nodes) {
            this.hashIndices.put(node, variables.indexOf(node));
        }
    }

    /**
     * Helper method. Appears to check if an arrowpoint is permitted by background knowledge.
     *
     * @param x The possible other node.
     * @param y The possible point node.
     * @return Whether the arrowpoint is allowed.
     */
    private boolean isArrowpointAllowed(Node x, Node y, Graph graph) {
        if (graph.getEndpoint(x, y) == Endpoint.ARROW) {
            return true;
        }

        if (graph.getEndpoint(x, y) == Endpoint.TAIL) {
            return false;
        }

        if (graph.getEndpoint(y, x) == Endpoint.ARROW) {
            if (!knowledge.isForbidden(x.getName(), y.getName())) return true;
        }

        if (graph.getEndpoint(y, x) == Endpoint.TAIL) {
            if (!knowledge.isForbidden(x.getName(), y.getName())) return true;
        }

        return graph.getEndpoint(y, x) == Endpoint.CIRCLE;
    }

    /**
     * Orients according to background knowledge
     */
    private void fciOrientbk(IKnowledge knowledge, Graph graph, List<Node> variables) {
        logger.log("info", "Starting BK Orientation.");

        for (Iterator<KnowledgeEdge> it = knowledge.forbiddenEdgesIterator(); it.hasNext(); ) {
            KnowledgeEdge edge = it.next();

            //match strings to variables in the graph.
            Node from = SearchGraphUtils.translate(edge.getFrom(), variables);
            Node to = SearchGraphUtils.translate(edge.getTo(), variables);


            if (from == null || to == null) {
                continue;
            }

            if (graph.getEdge(from, to) == null) {
                continue;
            }

            // Orient to*->from
            graph.setEndpoint(to, from, Endpoint.ARROW);
            graph.setEndpoint(from, to, Endpoint.CIRCLE);
            logger.log("knowledgeOrientation", SearchLogUtils.edgeOrientedMsg("Knowledge", graph.getEdge(from, to)));
        }

        for (Iterator<KnowledgeEdge> it = knowledge.requiredEdgesIterator(); it.hasNext(); ) {
            KnowledgeEdge edge = it.next();

            //match strings to variables in this graph
            Node from = SearchGraphUtils.translate(edge.getFrom(), variables);
            Node to = SearchGraphUtils.translate(edge.getTo(), variables);

            if (from == null || to == null) {
                continue;
            }

            if (graph.getEdge(from, to) == null) {
                continue;
            }

            // Orient from*->to (?)
            // Orient from-->to

//            System.out.println("Rule R8: Orienting " + from + "-->" + to);

            graph.setEndpoint(to, from, Endpoint.TAIL);
            graph.setEndpoint(from, to, Endpoint.ARROW);
            logger.log("knowledgeOrientation", SearchLogUtils.edgeOrientedMsg("Knowledge", graph.getEdge(from, to)));
        }

        logger.log("info", "Finishing BK Orientation.");
    }

    public void setSamplePrior(double samplePrior) {
        this.samplePrior = samplePrior;
    }

    public void setStructurePrior(double structurePrior) {
        this.structurePrior = structurePrior;
    }
}




