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
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.search.utils.*;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.math3.util.FastMath;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;


/**
 * Represents a GFCI search algorithm for structure learning in causal discovery.
 */
public final class SvarGfci implements IGraphSearch {
    /**
     * The conditional independence test.
     */
    private final IndependenceTest independenceTest;
    /**
     * The covariance matrix being searched over. Assumes continuous data.
     */
    ICovarianceMatrix covarianceMatrix;
    /**
     * The PAG being constructed.
     */
    private Graph graph;
    /**
     * The background knowledge.
     */
    private Knowledge knowledge = new Knowledge();
    /**
     * Flag for the complete rule set, true if one should use the complete rule set, false otherwise.
     */
    private boolean completeRuleSetUsed;
    /**
     * The maximum length for any discriminating path. -1 if unlimited; otherwise, a positive integer.
     */
    private int maxDiscriminatingPathLength = -1;
    /**
     * True iff verbose output should be printed.
     */
    private boolean verbose;
    /**
     * The score.
     */
    private Score score;
    /**
     * The sepsets.
     */
    private SepsetProducer sepsets;
    /**
     * Indicates whether the search algorithm should resolve almost cyclic paths.
     */
    private boolean resolveAlmostCyclicPaths;


    /**
     * Constructs a new GFCI search for the given independence test and background knowledge.
     *
     * @param test  The independence test.
     * @param score The score.
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
        independenceTest.setVerbose(verbose);

        if (verbose) {
            TetradLogger.getInstance().log("Starting svarGFCI algorithm.");
            TetradLogger.getInstance().log("Independence test = " + this.independenceTest + ".");
        }

        this.graph = new EdgeListGraph(independenceTest.getVariables());

        if (this.score == null) {
            chooseScore();
        }

        SvarFges fges = new SvarFges(this.score);
        fges.setKnowledge(this.knowledge);
        fges.setVerbose(this.verbose);
        fges.setNumCPDAGsToStore(0);
        // True, iff one-edge faithfulness is assumed. Speed up the algorithm for very large searches.
        // By default, false.
        boolean faithfulnessAssumed = true;
        fges.setFaithfulnessAssumed(faithfulnessAssumed);
        this.graph = fges.search();
        Graph fgesGraph = new EdgeListGraph(this.graph);

        // The maxIndegree for the fast adjacency search.
        int maxIndegree = -1;
        this.sepsets = new SepsetsMinP(fgesGraph, this.independenceTest, maxIndegree);

        for (Node b : independenceTest.getVariables()) {
            List<Node> adjacentNodes = new ArrayList<>(fgesGraph.getAdjacentNodes(b));

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
                    if (this.sepsets.getSepset(a, c, -1) != null) {
                        this.graph.removeEdge(a, c);
                        removeSimilarEdges(a, c);
                    }
                }
            }
        }

        modifiedR0(fgesGraph);

        R0R4StrategyTestBased strategy = (R0R4StrategyTestBased) R0R4StrategyTestBased.specialConfiguration(independenceTest,
                knowledge, verbose);
        strategy.setDepth(-1);
        strategy.setMaxLength(-1);
        FciOrient fciOrient = new FciOrient(strategy);
        fciOrient.setKnowledge(this.knowledge);
        fciOrient.setEndpointStrategy(new SvarSetEndpointStrategy(this.independenceTest, this.knowledge));

        fciOrient.setCompleteRuleSetUsed(this.completeRuleSetUsed);
        fciOrient.setMaxDiscriminatingPathLength(this.maxDiscriminatingPathLength);
        fciOrient.setVerbose(this.verbose);

        fciOrient.finalOrientation(this.graph);

        if (resolveAlmostCyclicPaths) {
            for (Edge edge : graph.getEdges()) {
                if (Edges.isBidirectedEdge(edge)) {
                    Node x = edge.getNode1();
                    Node y = edge.getNode2();

                    if (graph.paths().existsDirectedPath(x, y)) {
                        graph.setEndpoint(y, x, Endpoint.TAIL);
                    } else if (graph.paths().existsDirectedPath(y, x)) {
                        graph.setEndpoint(x, y, Endpoint.TAIL);
                    }
                }
            }
        }

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
     * Sets whether the complete rule set is used.
     *
     * @param completeRuleSetUsed True if Zhang's complete rule set should be used, False if only R1-R4 (the rule set of
     *                            the original FCI) should be used. False by default.
     */
    public void setCompleteRuleSetUsed(boolean completeRuleSetUsed) {
        this.completeRuleSetUsed = completeRuleSetUsed;
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
     * This method chooses the appropriate score for the search algorithm based on the provided data and settings. The
     * score is chosen as follows: - If the independence test is of type MsepTest, the score is set to GraphScore using
     * the graph from the independence test. - If a covariance matrix is available, the score is set to SemBicScore
     * using the covariance matrix. The penalty discount is set to a default value of 2. - If the data set is
     * continuous, the score is set to SemBicScore using a new CovarianceMatrix created from the data set. The penalty
     * discount is set to a default value of 2. - If the data set is discrete, the score is set to BdeuScore using the
     * data set. The sample prior is set to a default value of 10, and the structure prior is set to a default value of
     * 1. Otherwise, an IllegalArgumentException is thrown since mixed data is not supported.
     */
    private void chooseScore() {
        // The penalty discount for the GES search. By default, 2.
        double penaltyDiscount = 2;

        DataSet dataSet = (DataSet) this.independenceTest.getData();
        ICovarianceMatrix cov = this.independenceTest.getCov();
        Score score;

        if (this.independenceTest instanceof MsepTest) {
            score = new GraphScore(((MsepTest) independenceTest).getGraph());
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
            // The sample prior for the BDeu score (discrete data).
            double samplePrior = 10;
            score0.setSamplePrior(samplePrior);
            // The structure prior for the Bdeu score (discrete data).
            double structurePrior = 1;
            score0.setStructurePrior(structurePrior);
            score = score0;
        } else {
            throw new IllegalArgumentException("Mixed data not supported.");
        }

        this.score = score;
    }

    /**
     * Modifies the R0 structure of the given graph according to the FGES algorithm.
     *
     * @param fgesGraph The graph to modify.
     */
    private void modifiedR0(Graph fgesGraph) {
        this.graph.reorientAllWith(Endpoint.CIRCLE);
        fciOrientbk(this.knowledge, this.graph, this.graph.getNodes());

        List<Node> nodes = this.graph.getNodes();

        for (Node b : nodes) {
            List<Node> adjacentNodes = new ArrayList<>(this.graph.getAdjacentNodes(b));

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
                    Set<Node> sepset = this.sepsets.getSepset(a, c, -1);

                    if (sepset != null && !sepset.contains(b)) {
                        this.graph.setEndpoint(a, b, Endpoint.ARROW);
                        this.graph.setEndpoint(c, b, Endpoint.ARROW);
                        orientSimilarPairs(this.graph, this.knowledge, a, b);
                        orientSimilarPairs(this.graph, this.knowledge, c, b);
                    }
                }
            }
        }
    }

    /**
     * Orient the edges in the given graph based on the forbidden and required edges from the provided knowledge.
     *
     * @param knowledge The knowledge containing the forbidden and required edges.
     * @param graph     The graph to orient the edges in.
     * @param variables The list of variables in the graph.
     */
    private void fciOrientbk(Knowledge knowledge, Graph graph, List<Node> variables) {
        if (verbose) {
            TetradLogger.getInstance().log("Starting BK Orientation.");
        }

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

            // Orient to*->from
            graph.setEndpoint(to, from, Endpoint.ARROW);
            graph.setEndpoint(from, to, Endpoint.CIRCLE);

            if (verbose) {
                String message = LogUtilsSearch.edgeOrientedMsg("Knowledge", graph.getEdge(from, to));
                TetradLogger.getInstance().log(message);
            }
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

            if (verbose) {
                String message = LogUtilsSearch.edgeOrientedMsg("Knowledge", graph.getEdge(from, to));
                TetradLogger.getInstance().log(message);
            }
        }

        if (verbose) {
            TetradLogger.getInstance().log("Finishing BK Orientation.");
        }
    }

    /**
     * Returns the name of an object as a string without any lagging characters.
     *
     * @param obj The object to get the name from.
     * @return The name of the object without any lagging characters.
     */
    private String getNameNoLag(Object obj) {
        String tempS = obj.toString();
        if (tempS.indexOf(':') == -1) {
            return tempS;
        } else return tempS.substring(0, tempS.indexOf(':'));
    }

    /**
     * Removes similar edges between two nodes in a graph.
     *
     * @param x The first node.
     * @param y The second node.
     */
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
            Edge oldxy = this.graph.getEdge(x1, y1);
            this.graph.removeEdge(oldxy);
        }
    }

    /**
     * Orients similar pairs of edges between two nodes in a graph based on knowledge.
     *
     * @param graph     The graph to orient the edges in.
     * @param knowledge The knowledge containing information about the edges.
     * @param x         The first node.
     * @param y         The second node.
     */
    private void orientSimilarPairs(Graph graph, Knowledge knowledge, Node x, Node y) {
        if (x.getName().equals("time") || y.getName().equals("time")) {
            return;
        }
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

        for (i = 0; i < ntiers - tier_diff; ++i) {
            if (knowledge.getTier(i).size() == 1) continue;
            if (indx_tier >= indy_tier) {
                List<String> tmp_tier1 = knowledge.getTier(i + tier_diff);
                List<String> tmp_tier2 = knowledge.getTier(i);
                String A = tmp_tier1.get(indx_comp);
                String B = tmp_tier2.get(indy_comp);
                if (A.equals(B)) continue;
                if (A.equals(tier_x.get(indx_comp)) && B.equals(tier_y.get(indy_comp))) continue;
                if (B.equals(tier_x.get(indx_comp)) && A.equals(tier_y.get(indy_comp))) continue;
                Node x1 = this.independenceTest.getVariable(A);
                Node y1 = this.independenceTest.getVariable(B);

                if (graph.isAdjacentTo(x1, y1) && graph.getEndpoint(x1, y1) == Endpoint.CIRCLE) {
                    graph.setEndpoint(x1, y1, Endpoint.ARROW);

                    if (verbose) {
                        TetradLogger.getInstance().log("Orient edge " + graph.getEdge(x1, y1).toString());
                        TetradLogger.getInstance().log(" by structure knowledge as: " + graph.getEdge(x1, y1).toString());
                    }
                }
            }
        }
    }

    /**
     * Returns a list of similar pairs of nodes in a graph based on knowledge.
     *
     * @param x The first node.
     * @param y The second node.
     * @return A list of similar pairs of nodes in the graph.
     */
    // returnSimilarPairs based on orientSimilarPairs in SvarFciOrient.java by Entner and Hoyer
    private List<List<Node>> returnSimilarPairs(Node x, Node y) {
        if (x.getName().equals("time") || y.getName().equals("time")) {
            return new ArrayList<>();
        }

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
            simListX.add(x1);
            simListY.add(y1);
        }

        List<List<Node>> pairList = new ArrayList<>();
        pairList.add(simListX);
        pairList.add(simListY);
        return (pairList);
    }

    /**
     * Sets whether to resolve almost cyclic paths during the search.
     *
     * @param resolveAlmostCyclicPaths True if almost cyclic paths should be resolved, false otherwise.
     */
    public void setResolveAlmostCyclicPaths(boolean resolveAlmostCyclicPaths) {
        this.resolveAlmostCyclicPaths = resolveAlmostCyclicPaths;
    }
}




