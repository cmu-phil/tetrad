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
 * @author Daniel Malinsky
 */
public final class TsGFci implements GraphSearch {

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
    private boolean faithfulnessAssumed = true;

    // The score.
    private Score score;

    private SepsetProducer sepsets;
    private long elapsedTime;

    private int depth = -1;

    //============================CONSTRUCTORS============================//

    /**
     * Constructs a new GFCI search for the given independence test and background knowledge.
     */
    public TsGFci(IndependenceTest test, Score score) {
        if (score == null) {
            throw new NullPointerException();
        }
        this.sampleSize = score.getSampleSize();
        this.score = score;
        this.independenceTest = test;
    }

    //========================PUBLIC METHODS==========================//


    public Graph search() {
        long time1 = System.currentTimeMillis();

        List<Node> nodes = getIndependenceTest().getVariables();

        logger.log("info", "Starting tsGFCI algorithm.");
        logger.log("info", "Independence test = " + getIndependenceTest() + ".");

        this.graph = new EdgeListGraphSingleConnections(nodes);

        if (score == null) {
            setScore();
        }

        TsFges2 fges = new TsFges2(score);
        fges.setKnowledge(getKnowledge());
        fges.setVerbose(verbose);
        fges.setNumPatternsToStore(0);
        fges.setFaithfulnessAssumed(faithfulnessAssumed);
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
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                Node a = adjacentNodes.get(combination[0]);
                Node c = adjacentNodes.get(combination[1]);

                if (graph.isAdjacentTo(a, c) && fgesGraph.isAdjacentTo(a, c)) {
                    if (sepsets.getSepset(a, c) != null) {
                        graph.removeEdge(a, c);
                        /** removing similar edges to enforce repeating structure **/
                        removeSimilarEdges(a, c);
                        /** **/
                    }
                }
            }
        }

//        SepsetMap map = new SepsetMap();
//
//        for (Edge edge : graph.getEdges()) {
//            Node a = edge.getNode1();
//            Node c = edge.getNode2();
//
//            Edge e = fgesGraph.getEdge(a, c);
//
//            if (e != null && e.isDirected()) {
//
//                // Only the ones that are in triangles.
//                Set<Node> _adj = new HashSet<>(fgesGraph.getAdjacentNodes(a));
//                _adj.retainAll(fgesGraph.getAdjacentNodes(c));
//                if (_adj.isEmpty()) continue;
//
//                Node f = Edges.getDirectedEdgeHead(e);
//                List<Node> adj = fgesGraph.getAdjacentNodes(f);
//                adj.remove(Edges.getDirectedEdgeTail(e));
//
//                DepthChoiceGenerator gen = new DepthChoiceGenerator(adj.size(), adj.size());
//                int[] choice;
//
//                while ((choice = gen.next()) != null) {
//                    List<Node> cond = GraphUtils.asList(choice, adj);
//
//                    if (independenceTest.isIndependent(a, c, cond)) {
//                        graph.removeEdge(a, c);
//                        map.set(a, c, cond);
//                    }
//                }
//            }
//        }

//        System.out.println("GFCI: Look inside triangles done");

        modifiedR0(fgesGraph);

//    modifiedR0(fgesGraph, map);

//        System.out.println("GFCI: R0 done");

        TsFciOrient fciOrient = new TsFciOrient(sepsets, independenceTest);
        fciOrient.setKnowledge(getKnowledge());
        fciOrient.setCompleteRuleSetUsed(completeRuleSetUsed);
        fciOrient.setMaxPathLength(maxPathLength);
        fciOrient.doFinalOrientation(graph);

//        System.out.println("GFCI: Final orientation done");

        GraphUtils.replaceNodes(graph, independenceTest.getVariables());

        long time2 = System.currentTimeMillis();

        elapsedTime = time2 - time1;

        graph.setPag(true);

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
        graph.reorientAllWith(Endpoint.CIRCLE);
        fciOrientbk(knowledge, graph, graph.getNodes());

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
                    /** orienting similar pairs to enforce repeating structure **/
                    orientSimilarPairs(graph, knowledge, a, b, Endpoint.ARROW);
                    orientSimilarPairs(graph, knowledge, c, b, Endpoint.ARROW);
                    /** **/

                } else if (fgesGraph.isAdjacentTo(a, c) && !graph.isAdjacentTo(a, c)) {
                    List<Node> sepset = sepsets.getSepset(a, c);

                    if (sepset != null && !sepset.contains(b)) {
                        graph.setEndpoint(a, b, Endpoint.ARROW);
                        graph.setEndpoint(c, b, Endpoint.ARROW);
                        /** orienting similar pairs to enforce repeating structure **/
                        orientSimilarPairs(graph, knowledge, a, b, Endpoint.ARROW);
                        orientSimilarPairs(graph, knowledge, c, b, Endpoint.ARROW);
                        /** **/
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

    public void setFaithfulnessAssumed(boolean faithfulnessAssumed) {
        this.faithfulnessAssumed = faithfulnessAssumed;
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

    private int freeDegree(List<Node> nodes, Graph graph) {
        int max = 0;

        for (Node x : nodes) {
            List<Node> opposites = graph.getAdjacentNodes(x);

            for (Node y : opposites) {
                Set<Node> adjx = new HashSet<>(opposites);
                adjx.remove(y);

                if (adjx.size() > max) {
                    max = adjx.size();
                }
            }
        }

        return max;
    }

    private void orientSimilarPairs(Graph graph, IKnowledge knowledge, Node x, Node y, Endpoint mark) {
        if(x.getName().equals("time") || y.getName().equals("time")){
            return;
        }
        System.out.println("Entering orient similar pairs method for x and y: " + x + ", " + y);
        int ntiers = knowledge.getNumTiers();
        int indx_tier = knowledge.isInWhichTier(x);
        int indy_tier = knowledge.isInWhichTier(y);
        int tier_diff = Math.max(indx_tier, indy_tier) - Math.min(indx_tier, indy_tier);
        int indx_comp = -1;
        int indy_comp = -1;
        List tier_x = knowledge.getTier(indx_tier);
//        Collections.sort(tier_x);
        List tier_y = knowledge.getTier(indy_tier);
//        Collections.sort(tier_y);

        int i;
        for(i = 0; i < tier_x.size(); ++i) {
            if(getNameNoLag(x.getName()).equals(getNameNoLag(tier_x.get(i)))) {
                indx_comp = i;
                break;
            }
        }

        for(i = 0; i < tier_y.size(); ++i) {
            if(getNameNoLag(y.getName()).equals(getNameNoLag(tier_y.get(i)))) {
                indy_comp = i;
                break;
            }
        }

        if (indx_comp == -1) System.out.println("WARNING: indx_comp = -1!!!! ");
        if (indy_comp == -1) System.out.println("WARNING: indy_comp = -1!!!! ");

        for(i = 0; i < ntiers - tier_diff; ++i) {
            if(knowledge.getTier(i).size()==1) continue;
            String A;
            Node x1;
            String B;
            Node y1;
            if (indx_tier >= indy_tier) {
                List tmp_tier1 = knowledge.getTier(i + tier_diff);
//                Collections.sort(tmp_tier1);
                List tmp_tier2 = knowledge.getTier(i);
//                Collections.sort(tmp_tier2);
                A = (String) tmp_tier1.get(indx_comp);
                B = (String) tmp_tier2.get(indy_comp);
                if (A.equals(B)) continue;
                if (A.equals(tier_x.get(indx_comp)) && B.equals(tier_y.get(indy_comp))) continue;
                if (B.equals(tier_x.get(indx_comp)) && A.equals(tier_y.get(indy_comp))) continue;
                x1 = this.independenceTest.getVariable(A);
                y1 = this.independenceTest.getVariable(B);

//                if(getSepset2(x1,y1) != null) if (getSepset2(x1,y1).isEmpty() || getSepset2(y1,x1).isEmpty()){
//                    System.out.println("$$$ empty sepset between x1,y1 = " + x1 + " and " + y1);
//                    continue;
//                } // added 05.01.2016

                if(graph.isAdjacentTo(x1, y1) && graph.getEndpoint(x1, y1) == Endpoint.CIRCLE) {
                    System.out.print("Orient edge " + graph.getEdge(x1, y1).toString());
                    graph.setEndpoint(x1, y1, mark);
                    System.out.println(" by structure knowledge as: " + graph.getEdge(x1, y1).toString());
                }
            } else {
//                System.out.println("############## WARNING (orientSimilarPairs): did not catch x,y pair " + x + ", " + y);
            }
        }

    }

    public String getNameNoLag(Object obj) {
        String tempS = obj.toString();
        if(tempS.indexOf(':')== -1) {
            return tempS;
        } else return tempS.substring(0, tempS.indexOf(':'));
    }

    // returnSimilarPairs based on orientSimilarPairs in TsFciOrient.java by Entner and Hoyer
    private List<List<Node>> returnSimilarPairs(Node x, Node y) {
        System.out.println("$$$$$ Entering returnSimilarPairs method with x,y = " + x + ", " + y);
        if(x.getName().equals("time") || y.getName().equals("time")){
            return new ArrayList<>();
        }
//        System.out.println("Knowledge within returnSimilar : " + knowledge);
        int ntiers = knowledge.getNumTiers();
        int indx_tier = knowledge.isInWhichTier(x);
        int indy_tier = knowledge.isInWhichTier(y);
        int tier_diff = Math.max(indx_tier, indy_tier) - Math.min(indx_tier, indy_tier);
        int indx_comp = -1;
        int indy_comp = -1;
        List tier_x = knowledge.getTier(indx_tier);
//        Collections.sort(tier_x);
        List tier_y = knowledge.getTier(indy_tier);
//        Collections.sort(tier_y);

        int i;
        for(i = 0; i < tier_x.size(); ++i) {
            if(getNameNoLag(x.getName()).equals(getNameNoLag(tier_x.get(i)))) {
                indx_comp = i;
                break;
            }
        }

        for(i = 0; i < tier_y.size(); ++i) {
            if(getNameNoLag(y.getName()).equals(getNameNoLag(tier_y.get(i)))) {
                indy_comp = i;
                break;
            }
        }

        System.out.println("original independence: " + x + " and " + y);

        if (indx_comp == -1) System.out.println("WARNING: indx_comp = -1!!!! ");
        if (indy_comp == -1) System.out.println("WARNING: indy_comp = -1!!!! ");


        List<Node> simListX = new ArrayList<>();
        List<Node> simListY = new ArrayList<>();

        for(i = 0; i < ntiers - tier_diff; ++i) {
            if(knowledge.getTier(i).size()==1) continue;
            String A;
            Node x1;
            String B;
            Node y1;
            if (indx_tier >= indy_tier) {
                List tmp_tier1 = knowledge.getTier(i + tier_diff);
//                Collections.sort(tmp_tier1);
                List tmp_tier2 = knowledge.getTier(i);
//                Collections.sort(tmp_tier2);
                A = (String) tmp_tier1.get(indx_comp);
                B = (String) tmp_tier2.get(indy_comp);
                if (A.equals(B)) continue;
                if (A.equals(tier_x.get(indx_comp)) && B.equals(tier_y.get(indy_comp))) continue;
                if (B.equals(tier_x.get(indx_comp)) && A.equals(tier_y.get(indy_comp))) continue;
                x1 = graph.getNode(A);
                y1 = graph.getNode(B);
                System.out.println("Adding pair to simList = " + x1 + " and " + y1);
                simListX.add(x1);
                simListY.add(y1);
            } else {
                //System.out.println("############## WARNING (returnSimilarPairs): did not catch x,y pair " + x + ", " + y);
                //System.out.println();
                List tmp_tier1 = knowledge.getTier(i);
//                Collections.sort(tmp_tier1);
                List tmp_tier2 = knowledge.getTier(i + tier_diff);
//                Collections.sort(tmp_tier2);
                A = (String) tmp_tier1.get(indx_comp);
                B = (String) tmp_tier2.get(indy_comp);
                if (A.equals(B)) continue;
                if (A.equals(tier_x.get(indx_comp)) && B.equals(tier_y.get(indy_comp))) continue;
                if (B.equals(tier_x.get(indx_comp)) && A.equals(tier_y.get(indy_comp))) continue;
                x1 = graph.getNode(A);
                y1 = graph.getNode(B);
                System.out.println("Adding pair to simList = " + x1 + " and " + y1);
                simListX.add(x1);
                simListY.add(y1);
            }
        }

        List<List<Node>> pairList = new ArrayList<>();
        pairList.add(simListX);
        pairList.add(simListY);
        return(pairList);
    }

    public void removeSimilarEdges(Node x, Node y){
        List<List<Node>> simList = returnSimilarPairs(x,y);
        if(simList.isEmpty()) return;
        List<Node> x1List = simList.get(0);
        List<Node> y1List = simList.get(1);
        Iterator itx = x1List.iterator();
        Iterator ity = y1List.iterator();
        while(itx.hasNext() && ity.hasNext()){
            Node x1 = (Node)itx.next();
            Node y1 = (Node)ity.next();
            System.out.println("$$$$$$$$$$$ similar pair x,y = " + x1 + ", " + y1);
            System.out.println("removing edge between x = " + x1 + " and y = " + y1);
            Edge oldxy = graph.getEdge(x1, y1);
            graph.removeEdge(oldxy);
        }
    }

}




