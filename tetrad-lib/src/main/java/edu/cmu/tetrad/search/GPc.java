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
import java.util.*;
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
public final class GPc implements GraphSearch {

    // If a graph is provided.
    private Graph dag = null;

    // The PAG being constructed.
    private Graph graph;

    // The background knowledge.
    private IKnowledge knowledge = new Knowledge2();

    // The variables to search over (optional)
    private List<Node> variables = new ArrayList<>();

    // The conditional independence test.
    private IndependenceTest independenceTest;

    // Flag for complete rule set, true if should use complete rule set, false otherwise.
    private boolean completeRuleSetUsed = false;

    // True iff the possible dsep search is done.
//    private boolean possibleDsepSearchDone = true;

    // The maximum length for any discriminating path. -1 if unlimited; otherwise, a positive integer.
    private int maxPathLength = -1;

    // The maxIndegree for the fast adjacency search.
    private int maxIndegree = -1;

    // The logger to use.
    private TetradLogger logger = TetradLogger.getInstance();

    // True iff verbose output should be printed.
    private boolean verbose = false;

    // The covariance matrix beign searched over. Assumes continuous data.
    ICovarianceMatrix covarianceMatrix;

    // The sample size.
    int sampleSize;

    // The penalty discount for the GES search. By default 2.
    private double penaltyDiscount = 2;

    // The sample prior for the BDeu score (discrete data).
    private double samplePrior = 10;

    // The structure prior for the Bdeu score (discrete data).
    private double structurePrior = 1;

    // Map from variables to their column indices in the data set.
    private ConcurrentMap<Node, Integer> hashIndices;

    // The print stream that output is directed to.
    private PrintStream out = System.out;

    // True iff one-edge faithfulness is assumed. Speed up the algorith for very large searches. By default false.
    private boolean heuristicSpeedup = true;

    // The score.
    private Score score;

    private SepsetProducer sepsets;
    private long elapsedTime;
    private int fgesDepth = -1;

    //============================CONSTRUCTORS============================//

    public GPc(Score score) {
        if (score == null) throw new NullPointerException();
        this.score = score;

        if (score instanceof GraphScore) {
            this.dag = ((GraphScore) score).getDag();
        }

        this.sampleSize = score.getSampleSize();
        this.independenceTest = new IndTestScore(score);
        this.variables = score.getVariables();
        buildIndexing(variables);
    }

    //========================PUBLIC METHODS==========================//


    public Graph search() {
        long time1 = System.currentTimeMillis();

        List<Node> nodes = getIndependenceTest().getVariables();

        logger.log("info", "Starting FCI algorithm.");
        logger.log("info", "Independence test = " + getIndependenceTest() + ".");

        this.graph = new EdgeListGraphSingleConnections(nodes);

        if (score == null) {
            setScore();
        }

        Fges fges = new Fges(score);
        fges.setKnowledge(getKnowledge());
        fges.setVerbose(verbose);
        fges.setNumPatternsToStore(0);
//        fges.setHeuristicSpeedup(heuristicSpeedup);
//        fges.setMaxDegree(fgesDepth);
        graph = fges.search();

        Graph fgesGraph = new EdgeListGraphSingleConnections(graph);

//        System.out.println("GFCI: FGES done");

        sepsets = new SepsetsGreedy(fgesGraph, independenceTest, null, maxIndegree);
//        ((SepsetsGreedy) sepsets).setMaxDegree(3);
//        sepsets = new SepsetsConservative(fgesGraph, independenceTest, null, maxIndegree);
//        sepsets = new SepsetsConservativeMajority(fgesGraph, independenceTest, null, maxIndegree);
//        sepsets = new SepsetsMaxPValue(fgesGraph, independenceTest, null, maxIndegree);
//        sepsets = new SepsetsMinScore(fgesGraph, independenceTest, null, maxIndegree);
//
//        System.out.println("GFCI: Look inside triangles starting");

        for (Node b : nodes) {
            List<Node> adjacentNodes = fgesGraph.getAdjacentNodes(b);

            if (adjacentNodes.size() < 2) {
                continue;
            }

            ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                Node a = adjacentNodes.get(combination[0]);
                Node c = adjacentNodes.get(combination[1]);

                if (graph.isAdjacentTo(a, c) && fgesGraph.isAdjacentTo(a, c)) {
                    if (sepsets.getSepset(a, c) != null) {
                        graph.removeEdge(a, c);
                    }
                }
            }
        }

//        for (int d = 0; d < 100; d++) {
//
//            LOOP:
//            for (Edge edge : graph.getEdges()) {
//                Node x = edge.getNode1();
//                Node y = edge.getNode2();
//
//                List<Node> adjx = fgesGraph.getAdjacentNodes(x);
//                List<Node> adjy = fgesGraph.getAdjacentNodes(y);
//
//                adjx.remove(y);
//                adjy.remove(x);
//
//                Set<Node> intersection = new HashSet<>(adjx);
//                intersection.retainAll(new HashSet<>(adjy));
//
//                if (intersection.isEmpty()) continue LOOP;
//
//                if (adjx.size() < d) continue LOOP;
//
//                ChoiceGenerator gen = new ChoiceGenerator(adjx.size(), d);
//                int[] choice;
//
//                while ((choice = gen.next()) != null) {
//                    List<Node> _adj = GraphUtils.asList(choice, adjx);
//
//                    if (independenceTest.isIndependent(x, y, _adj)) {
//                        graph.removeEdge(edge);
//                        continue LOOP;
//                    }
//                }
//
//                if (adjy.size() < d) continue LOOP;
//
//                ChoiceGenerator gen2 = new ChoiceGenerator(adjy.size(), d);
//                int[] choice2;
//
//                while ((choice2 = gen2.next()) != null) {
//                    List<Node> _adj = GraphUtils.asList(choice2, adjy);
//
//                    if (independenceTest.isIndependent(x, y, _adj)) {
//                        graph.removeEdge(edge);
//                        continue LOOP;
//                    }
//                }
//            }
//        }
//
////        for (Edge edge : graph.getEdges()) {
////            System.out.println(edge);
////
////            Node a = edge.getNode1();
////            Node c = edge.getNode2();
////
////            Set<Node> x = new HashSet<>(fgesGraph.getAdjacentNodes(a));
////            x.retainAll(fgesGraph.getAdjacentNodes(c));
////
////            if (!x.isEmpty()) {
////                if (sepsets.getSepset(a, c) != null) {
////                    graph.removeEdge(a, c);
////                }
////            }
////        }

        modifiedR0(fgesGraph);

        MeekRules rules = new MeekRules();
        rules.setAggressivelyPreventCycles(false);
        rules.setKnowledge(knowledge);
        rules.setUndirectUnforcedEdges(false);
        rules.orientImplied(graph);

        GraphUtils.replaceNodes(graph, independenceTest.getVariables());

        long time2 = System.currentTimeMillis();

        elapsedTime = time2 - time1;

        return graph;
    }

    @Override
    public long getElapsedTime() {
        return elapsedTime;
    }

    private void setScore() {
        sampleSize = independenceTest.getSampleSize();
        double penaltyDiscount = getPenaltyDiscount();

        DataSet dataSet = (DataSet) independenceTest.getData();
        ICovarianceMatrix cov = independenceTest.getCov();
        Score score;

        if (independenceTest instanceof IndTestDSep) {
            score = new GraphScore(dag);
        } else if (cov != null) {
            covarianceMatrix = cov;
            SemBicScore score0 = new SemBicScore(cov);
            score0.setPenaltyDiscount(penaltyDiscount);
            score = score0;
        } else if (dataSet.isContinuous()) {
            covarianceMatrix = new CovarianceMatrixOnTheFly(dataSet);
            SemBicScore score0 = new SemBicScore(covarianceMatrix);
            score0.setPenaltyDiscount(penaltyDiscount);
            score = score0;
        } else if (dataSet.isDiscrete()) {
            BDeuScore score0 = new BDeuScore(dataSet);
            score0.setSamplePrior(samplePrior);
            score0.setStructurePrior(structurePrior);
            score = score0;
        } else {
            throw new IllegalArgumentException("Mixed data not supported.");
        }

        this.score = score;
    }

    public int getMaxIndegree() {
        return maxIndegree;
    }

    public void setMaxIndegree(int maxIndegree) {
        if (maxIndegree < -1) {
            throw new IllegalArgumentException(
                    "Depth must be -1 (unlimited) or >= 0: " + maxIndegree);
        }

        this.maxIndegree = maxIndegree;
    }

    // Due to Spirtes.
    public void modifiedR0(Graph fgesGraph) {
        graph.reorientAllWith(Endpoint.TAIL);
        pcOrientBk(knowledge, graph, graph.getNodes());

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

                if (fgesGraph.isDefCollider(a, b, c)) {
                    graph.setEndpoint(a, b, Endpoint.ARROW);
                    graph.setEndpoint(c, b, Endpoint.ARROW);
                } else if (fgesGraph.isAdjacentTo(a, c) && !graph.isAdjacentTo(a, c)) {
                    List<Node> sepset = sepsets.getSepset(a, c);

                    if (sepset != null && !sepset.contains(b)) {
                        graph.setEndpoint(a, b, Endpoint.ARROW);
                        graph.setEndpoint(c, b, Endpoint.ARROW);
                    }
                }
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

    public void setHeuristicSpeedup(boolean heuristicSpeedup) {
        this.heuristicSpeedup = heuristicSpeedup;
    }

    //===========================================PRIVATE METHODS=======================================//

    private void buildIndexing(List<Node> nodes) {
        this.hashIndices = new ConcurrentHashMap<>();

        int i = 0;

        for (Node node : nodes) {
            this.hashIndices.put(node, i++);
        }
    }

    /**
     * Orients according to background knowledge
     */
    private void pcOrientBk(IKnowledge knowledge, Graph graph, List<Node> variables) {
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
            graph.setEndpoint(from, to, Endpoint.TAIL);
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

    public int getFgesDepth() {
        return fgesDepth;
    }

    public void setFgesDepth(int fgesDepth) {
        this.fgesDepth = fgesDepth;
    }
}




