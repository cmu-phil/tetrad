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

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.score.BdeuScore;
import edu.cmu.tetrad.search.score.GraphScore;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.test.IndTestDSep;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.utils.GraphSearchUtils;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.search.utils.SepsetProducer;
import edu.cmu.tetrad.search.utils.SepsetsGreedy;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.math3.util.FastMath;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


/**
 * Adapts GFCI to the SVAR case.
 *
 * <p>This class is configured to respect knowledge of forbidden and required
 * edges, including knowledge of temporal tiers.</p>
 *
 * @author danielmalinsky
 * @see GFci
 * @see Knowledge
 * @see SvarFci
 */
public final class SvarGfci implements IGraphSearch {

    // The PAG being constructed.
    private Graph graph;

    // The background knowledge.
    private Knowledge knowledge = new Knowledge();

    // The conditional independence test.
    private IndependenceTest independenceTest;

    // Flag for complete rule set, true if one should use complete rule set, false otherwise.
    private boolean completeRuleSetUsed;

    // The maximum length for any discriminating path. -1 if unlimited; otherwise, a positive integer.
    private int maxPathLength = -1;

    // The maxIndegree for the fast adjacency search.
    private int maxIndegree = -1;

    // The logger to use.
    private final TetradLogger logger = TetradLogger.getInstance();

    // True iff verbose output should be printed.
    private boolean verbose;

    // The covariance matrix being searched over. Assumes continuous data.
    ICovarianceMatrix covarianceMatrix;

    // The penalty discount for the GES search. By default, 2.
    private double penaltyDiscount = 2;

    // The sample prior for the BDeu score (discrete data).
    private double samplePrior = 10;

    // The structure prior for the Bdeu score (discrete data).
    private double structurePrior = 1;

    // True iff one-edge faithfulness is assumed. Speed up the algorith for very large searches.
    // By default, false.
    private boolean faithfulnessAssumed = true;

    // The score.
    private Score score;

    private SepsetProducer sepsets;

    //============================CONSTRUCTORS============================//

    /**
     * Constructs a new GFCI search for the given independence test and background knowledge.
     */
    public SvarGfci(IndependenceTest test, Score score) {
        if (score == null) {
            throw new NullPointerException();
        }
        this.score = score;
        this.independenceTest = test;
    }

    /**
     * Runs the search and returns a PAG.
     *
     * @return a PAG.
     */
    public Graph search() {
        this.logger.log("info", "Starting svarGFCI algorithm.");
        this.logger.log("info", "Independence test = " + this.independenceTest + ".");

        this.graph = new EdgeListGraph(independenceTest.getVariables());

        if (this.score == null) {
            chooseScore();
        }

        SvarFges fges = new SvarFges(this.score);
        fges.setKnowledge(this.knowledge);
        fges.setVerbose(this.verbose);
        fges.setNumCPDAGsToStore(0);
        fges.setFaithfulnessAssumed(this.faithfulnessAssumed);
        this.graph = fges.search();
        Graph fgesGraph = new EdgeListGraph(this.graph);

        this.sepsets = new SepsetsGreedy(fgesGraph, this.independenceTest, null, this.maxIndegree);

        for (Node b : independenceTest.getVariables()) {
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

                if (this.graph.isAdjacentTo(a, c) && fgesGraph.isAdjacentTo(a, c)) {
                    if (this.sepsets.getSepset(a, c) != null) {
                        this.graph.removeEdge(a, c);
                        //  removing similar edges to enforce repeating structure **/
                        removeSimilarEdges(a, c);
                        //  **/
                    }
                }
            }
        }

        modifiedR0(fgesGraph);

        SvarFciOrient fciOrient = new SvarFciOrient(this.sepsets, this.independenceTest);
        fciOrient.setKnowledge(this.knowledge);
        fciOrient.setCompleteRuleSetUsed(this.completeRuleSetUsed);
        fciOrient.setMaxPathLength(this.maxPathLength);
        fciOrient.doFinalOrientation(this.graph);

        GraphUtils.replaceNodes(this.graph, this.independenceTest.getVariables());

        return this.graph;
    }

    /**
     * Sets the knowledge for the search.
     *
     * @param knowledge The knowledge.
     */
    public void setKnowledge(Knowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException();
        }

        this.knowledge = knowledge;
    }

    /**
     * Sets whether the complete ruleset is used.
     *
     * @param completeRuleSetUsed True if Zhang's complete rule set should be used, False if only R1-R4 (the rule
     *                            set of the original FCI) should be used. False by default.
     */
    public void setCompleteRuleSetUsed(boolean completeRuleSetUsed) {
        this.completeRuleSetUsed = completeRuleSetUsed;
    }

    /**
     * Sets the maximum length of any discriminating path.
     *
     * @param maxPathLength the maximum length of any discriminating path, or -1 if unlimited.
     */
    public void setMaxPathLength(int maxPathLength) {
        if (maxPathLength < -1) {
            throw new IllegalArgumentException("Max path length must be -1 (unlimited) or >= 0: " + maxPathLength);
        }

        this.maxPathLength = maxPathLength;
    }

    /**
     * Sets whether verbose output is printed.
     *
     * @param verbose true if so.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    private void chooseScore() {
        double penaltyDiscount = this.penaltyDiscount;

        DataSet dataSet = (DataSet) this.independenceTest.getData();
        ICovarianceMatrix cov = this.independenceTest.getCov();
        Score score;

        if (this.independenceTest instanceof IndTestDSep) {
            score = new GraphScore(((IndTestDSep) independenceTest).getGraph());
        } else if (cov != null) {
            this.covarianceMatrix = cov;
            SemBicScore score0 = new SemBicScore(cov);
            score0.setPenaltyDiscount(penaltyDiscount);
            score = score0;
        } else if (dataSet.isContinuous()) {
            this.covarianceMatrix = new CovarianceMatrix(dataSet);
            SemBicScore score0 = new SemBicScore(this.covarianceMatrix);
            score0.setPenaltyDiscount(penaltyDiscount);
            score = score0;
        } else if (dataSet.isDiscrete()) {
            BdeuScore score0 = new BdeuScore(dataSet);
            score0.setSamplePrior(this.samplePrior);
            score0.setStructurePrior(this.structurePrior);
            score = score0;
        } else {
            throw new IllegalArgumentException("Mixed data not supported.");
        }

        this.score = score;
    }

    // Due to Spirtes.
    private void modifiedR0(Graph fgesGraph) {
        this.graph.reorientAllWith(Endpoint.CIRCLE);
        fciOrientbk(this.knowledge, this.graph, this.graph.getNodes());

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

                if (fgesGraph.isDefCollider(a, b, c)) {
                    this.graph.setEndpoint(a, b, Endpoint.ARROW);
                    this.graph.setEndpoint(c, b, Endpoint.ARROW);
                    //  orienting similar pairs to enforce repeating structure **/
                    orientSimilarPairs(this.graph, this.knowledge, a, b);
                    orientSimilarPairs(this.graph, this.knowledge, c, b);
                    //  **/

                } else if (fgesGraph.isAdjacentTo(a, c) && !this.graph.isAdjacentTo(a, c)) {
                    List<Node> sepset = this.sepsets.getSepset(a, c);

                    if (sepset != null && !sepset.contains(b)) {
                        this.graph.setEndpoint(a, b, Endpoint.ARROW);
                        this.graph.setEndpoint(c, b, Endpoint.ARROW);
                        //  orienting similar pairs to enforce repeating structure **/
                        orientSimilarPairs(this.graph, this.knowledge, a, b);
                        orientSimilarPairs(this.graph, this.knowledge, c, b);
                        //  **/
                    }
                }
            }
        }
    }

    /**
     * Orients according to background knowledge
     */
    private void fciOrientbk(Knowledge knowledge, Graph graph, List<Node> variables) {
        this.logger.log("info", "Starting BK Orientation.");

        for (Iterator<KnowledgeEdge> it = knowledge.forbiddenEdgesIterator(); it.hasNext(); ) {
            KnowledgeEdge edge = it.next();

            //match strings to variables in the graph.
            Node from = GraphSearchUtils.translate(edge.getFrom(), variables);
            Node to = GraphSearchUtils.translate(edge.getTo(), variables);


            if (from == null || to == null) {
                continue;
            }

            if (graph.getEdge(from, to) == null) {
                continue;
            }

            // Orient to*-&gt;from
            graph.setEndpoint(to, from, Endpoint.ARROW);
            graph.setEndpoint(from, to, Endpoint.CIRCLE);
            this.logger.log("knowledgeOrientation", LogUtilsSearch.edgeOrientedMsg("Knowledge", graph.getEdge(from, to)));
        }

        for (Iterator<KnowledgeEdge> it = knowledge.requiredEdgesIterator(); it.hasNext(); ) {
            KnowledgeEdge edge = it.next();

            //match strings to variables in this graph
            Node from = GraphSearchUtils.translate(edge.getFrom(), variables);
            Node to = GraphSearchUtils.translate(edge.getTo(), variables);

            if (from == null || to == null) {
                continue;
            }

            if (graph.getEdge(from, to) == null) {
                continue;
            }

            graph.setEndpoint(to, from, Endpoint.TAIL);
            graph.setEndpoint(from, to, Endpoint.ARROW);
            this.logger.log("knowledgeOrientation", LogUtilsSearch.edgeOrientedMsg("Knowledge", graph.getEdge(from, to)));
        }

        this.logger.log("info", "Finishing BK Orientation.");
    }

    private String getNameNoLag(Object obj) {
        String tempS = obj.toString();
        if (tempS.indexOf(':') == -1) {
            return tempS;
        } else return tempS.substring(0, tempS.indexOf(':'));
    }

    private void removeSimilarEdges(Node x, Node y) {
        List<List<Node>> simList = returnSimilarPairs(x, y);
        if (simList.isEmpty()) return;
        List<Node> x1List = simList.get(0);
        List<Node> y1List = simList.get(1);
        Iterator<Node> itx = x1List.iterator();
        Iterator<Node> ity = y1List.iterator();
        while (itx.hasNext() && ity.hasNext()) {
            Node x1 = itx.next();
            Node y1 = ity.next();
            System.out.println("$$$$$$$$$$$ similar pair x,y = " + x1 + ", " + y1);
            System.out.println("removing edge between x = " + x1 + " and y = " + y1);
            Edge oldxy = this.graph.getEdge(x1, y1);
            this.graph.removeEdge(oldxy);
        }
    }

    private void orientSimilarPairs(Graph graph, Knowledge knowledge, Node x, Node y) {
        if (x.getName().equals("time") || y.getName().equals("time")) {
            return;
        }
        System.out.println("Entering orient similar pairs method for x and y: " + x + ", " + y);
        int ntiers = knowledge.getNumTiers();
        int indx_tier = knowledge.isInWhichTier(x);
        int indy_tier = knowledge.isInWhichTier(y);
        int tier_diff = FastMath.max(indx_tier, indy_tier) - FastMath.min(indx_tier, indy_tier);
        int indx_comp = -1;
        int indy_comp = -1;
        List<String> tier_x = knowledge.getTier(indx_tier);
        List<String> tier_y = knowledge.getTier(indy_tier);

        int i;
        for (i = 0; i < tier_x.size(); ++i) {
            if (getNameNoLag(x.getName()).equals(getNameNoLag(tier_x.get(i)))) {
                indx_comp = i;
                break;
            }
        }

        for (i = 0; i < tier_y.size(); ++i) {
            if (getNameNoLag(y.getName()).equals(getNameNoLag(tier_y.get(i)))) {
                indy_comp = i;
                break;
            }
        }

        if (indx_comp == -1) System.out.println("WARNING: indx_comp = -1!!!! ");
        if (indy_comp == -1) System.out.println("WARNING: indy_comp = -1!!!! ");

        for (i = 0; i < ntiers - tier_diff; ++i) {
            if (knowledge.getTier(i).size() == 1) continue;
            String A;
            Node x1;
            String B;
            Node y1;
            if (indx_tier >= indy_tier) {
                List<String> tmp_tier1 = knowledge.getTier(i + tier_diff);
                List<String> tmp_tier2 = knowledge.getTier(i);
                A = tmp_tier1.get(indx_comp);
                B = tmp_tier2.get(indy_comp);
                if (A.equals(B)) continue;
                if (A.equals(tier_x.get(indx_comp)) && B.equals(tier_y.get(indy_comp))) continue;
                if (B.equals(tier_x.get(indx_comp)) && A.equals(tier_y.get(indy_comp))) continue;
                x1 = this.independenceTest.getVariable(A);
                y1 = this.independenceTest.getVariable(B);

                if (graph.isAdjacentTo(x1, y1) && graph.getEndpoint(x1, y1) == Endpoint.CIRCLE) {
                    System.out.print("Orient edge " + graph.getEdge(x1, y1).toString());
                    graph.setEndpoint(x1, y1, Endpoint.ARROW);
                    System.out.println(" by structure knowledge as: " + graph.getEdge(x1, y1).toString());
                }
            }
        }

    }

    // returnSimilarPairs based on orientSimilarPairs in SvarFciOrient.java by Entner and Hoyer
    private List<List<Node>> returnSimilarPairs(Node x, Node y) {
        System.out.println("$$$$$ Entering returnSimilarPairs method with x,y = " + x + ", " + y);
        if (x.getName().equals("time") || y.getName().equals("time")) {
            return new ArrayList<>();
        }
//        System.out.println("Knowledge within returnSimilar : " + knowledge);
        int ntiers = this.knowledge.getNumTiers();
        int indx_tier = this.knowledge.isInWhichTier(x);
        int indy_tier = this.knowledge.isInWhichTier(y);
        int tier_diff = FastMath.max(indx_tier, indy_tier) - FastMath.min(indx_tier, indy_tier);
        int indx_comp = -1;
        int indy_comp = -1;
        List<String> tier_x = this.knowledge.getTier(indx_tier);
        List<String> tier_y = this.knowledge.getTier(indy_tier);

        int i;
        for (i = 0; i < tier_x.size(); ++i) {
            if (getNameNoLag(x.getName()).equals(getNameNoLag(tier_x.get(i)))) {
                indx_comp = i;
                break;
            }
        }

        for (i = 0; i < tier_y.size(); ++i) {
            if (getNameNoLag(y.getName()).equals(getNameNoLag(tier_y.get(i)))) {
                indy_comp = i;
                break;
            }
        }

        System.out.println("original independence: " + x + " and " + y);

        if (indx_comp == -1) System.out.println("WARNING: indx_comp = -1!!!! ");
        if (indy_comp == -1) System.out.println("WARNING: indy_comp = -1!!!! ");


        List<Node> simListX = new ArrayList<>();
        List<Node> simListY = new ArrayList<>();

        for (i = 0; i < ntiers - tier_diff; ++i) {
            if (this.knowledge.getTier(i).size() == 1) continue;
            String A;
            Node x1;
            String B;
            Node y1;
            List<String> tmp_tier1;
            List<String> tmp_tier2;
            if (indx_tier >= indy_tier) {
                tmp_tier1 = this.knowledge.getTier(i + tier_diff);
                tmp_tier2 = this.knowledge.getTier(i);
            } else {
                tmp_tier1 = this.knowledge.getTier(i);
                tmp_tier2 = this.knowledge.getTier(i + tier_diff);
            }
            A = tmp_tier1.get(indx_comp);
            B = tmp_tier2.get(indy_comp);
            if (A.equals(B)) continue;
            if (A.equals(tier_x.get(indx_comp)) && B.equals(tier_y.get(indy_comp))) continue;
            if (B.equals(tier_x.get(indx_comp)) && A.equals(tier_y.get(indy_comp))) continue;
            x1 = this.graph.getNode(A);
            y1 = this.graph.getNode(B);
            System.out.println("Adding pair to simList = " + x1 + " and " + y1);
            simListX.add(x1);
            simListY.add(y1);
        }

        List<List<Node>> pairList = new ArrayList<>();
        pairList.add(simListX);
        pairList.add(simListY);
        return (pairList);
    }
}




