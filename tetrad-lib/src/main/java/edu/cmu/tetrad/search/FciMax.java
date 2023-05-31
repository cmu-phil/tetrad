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
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.utils.FciOrient;
import edu.cmu.tetrad.search.utils.PcCommon;
import edu.cmu.tetrad.search.utils.SepsetMap;
import edu.cmu.tetrad.search.utils.SepsetsSet;
import edu.cmu.tetrad.util.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RecursiveTask;

/**
 * <p>Modifies FCI to do orientation of unshielded colliders (X*-*Y*-*Z with X and Z
 * not adjacent) using the max-P rule (see the PC-Max algorithm). This reference is relevant:</p>
 *
 * <p>Raghu, V. K., Zhao, W., Pu, J., Leader, J. K., Wang, R., Herman, J., ... &amp;
 * Wilson, D. O. (2019). Feasibility of lung cancer prediction from low-dose CT scan and smoking factors using causal
 * models. Thorax, 74(7), 643-649.</p>
 *
 * <p>Max-P triple orientation is a method for orienting unshielded triples
 * X*=-*Y*-*Z as one of the following: (a) Collider, X->Y<-Z, or (b) Noncollider, X-->Y-->Z, or X<-Y<-Z, or X<-Y->Z. One
 * does this by conditioning on subsets of adj(X) or adj(Z). One first checks conditional independence of X and Z
 * conditional on each of these subsets, and lists the p-values for each test. Then, one chooses the conditioning set
 * out of all of these that maximizes the p-value. If this conditioning set contains Y, then the triple is judged to be
 * a noncollider; otherwise, it is judged to be a collider.</p>
 *
 * <p>All unshielded triples in the graph given by FAS are judged as colliders
 * or non-colliders and the colliders oriented. Then the final FCI orientation rules are applied, as in FCI.</p>
 *
 * <p>This class is configured to respect knowledge of forbidden and required
 * edges, including knowledge of temporal tiers.</p>
 *
 * @author josephramsey
 * @see Fci
 * @see Fas
 * @see FciOrient
 * @see Knowledge
 */
public final class FciMax implements IGraphSearch {
    private SepsetMap sepsets;
    private Knowledge knowledge = new Knowledge();
    private final IndependenceTest independenceTest;
    private long elapsedTime;
    private final TetradLogger logger = TetradLogger.getInstance();
    private PcCommon.PcHeuristicType pcHeuristicType = PcCommon.PcHeuristicType.NONE;
    private boolean stable = false;
    private boolean completeRuleSetUsed = true;
    private boolean doDiscriminatingPathRule = false;
    private boolean possibleMsepSearchDone = true;
    private int maxPathLength = -1;
    private int depth = -1;
    private boolean verbose = false;

    //============================CONSTRUCTORS============================//

    /**
     * Constructor.
     */
    public FciMax(IndependenceTest independenceTest) {
        if (independenceTest == null) {
            throw new NullPointerException();
        }

        this.independenceTest = independenceTest;
    }

    //========================PUBLIC METHODS==========================//

    /**
     * Performs the search and returns the PAG.
     *
     * @return This PAG.
     */
    public Graph search() {
        long start = MillisecondTimes.timeMillis();

        Fas fas = new Fas(getIndependenceTest());
        this.logger.log("info", "Starting FCI algorithm.");
        this.logger.log("info", "Independence test = " + getIndependenceTest() + ".");

        fas.setKnowledge(getKnowledge());
        fas.setDepth(this.depth);
        fas.setPcHeuristicType(this.pcHeuristicType);
        fas.setVerbose(this.verbose);
        fas.setStable(this.stable);
        fas.setPcHeuristicType(this.pcHeuristicType);

        //The PAG being constructed.
        Graph graph = fas.search();
        this.sepsets = fas.getSepsets();

        graph.reorientAllWith(Endpoint.CIRCLE);

        // The original FCI, with or without JiJi Zhang's orientation rules
        // Optional step: Possible Msep. (Needed for correctness but very time-consuming.)
        if (this.possibleMsepSearchDone) {
            new FciOrient(new SepsetsSet(this.sepsets, this.independenceTest)).ruleR0(graph);
            graph.paths().removeByPossibleMsep(independenceTest, sepsets);

            // Reorient all edges as o-o.
            graph.reorientAllWith(Endpoint.CIRCLE);
        }

        // Step CI C (Zhang's step F3.)

        FciOrient fciOrient = new FciOrient(new SepsetsSet(this.sepsets, this.independenceTest));

        fciOrient.setCompleteRuleSetUsed(this.completeRuleSetUsed);
        fciOrient.setMaxPathLength(this.maxPathLength);
        fciOrient.setDoDiscriminatingPathColliderRule(this.doDiscriminatingPathRule);
        fciOrient.setDoDiscriminatingPathTailRule(this.doDiscriminatingPathRule);
        fciOrient.setVerbose(this.verbose);
        fciOrient.setKnowledge(this.knowledge);

        fciOrient.fciOrientbk(this.knowledge, graph, graph.getNodes());
        addColliders(graph);
        fciOrient.doFinalOrientation(graph);

        long stop = MillisecondTimes.timeMillis();

        this.elapsedTime = stop - start;

        return graph;
    }

    /**
     * Sets the maximum nubmer of variables conditonied in any test.
     *
     * @param depth This maximum.
     */
    public void setDepth(int depth) {
        if (depth < -1) {
            throw new IllegalArgumentException(
                    "Depth must be -1 (unlimited) or >= 0: " + depth);
        }

        this.depth = depth;
    }

    /**
     * Returns the elapsed time of search.
     *
     * @return This time.
     */
    public long getElapsedTime() {
        return this.elapsedTime;
    }

    /**
     * Retrieves the map from variable pairs to sepsets from the FAS search.
     *
     * @return This map.
     */
    public SepsetMap getSepsets() {
        return this.sepsets;
    }

    /**
     * Retrieves the background knowledge that was set.
     *
     * @return This knoweldge,
     */
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * Sets background knowledge for the search.
     *
     * @param knowledge This knowledge,
     */
    public void setKnowledge(Knowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException();
        }

        this.knowledge = knowledge;
    }

    /**
     * Sets whether Zhang's complete ruleset is used in the search.
     *
     * @param completeRuleSetUsed set to true if Zhang's complete rule set should be used, false if only R1-R4 (the rule
     *                            set of the original FCI) should be used. False by default.
     */
    public void setCompleteRuleSetUsed(boolean completeRuleSetUsed) {
        this.completeRuleSetUsed = completeRuleSetUsed;
    }

    /**
     * Sets whether the (time-consuming) possible msep step should be done.
     *
     * @param possibleMsepSearchDone True if so.
     */
    public void setPossibleMsepSearchDone(boolean possibleMsepSearchDone) {
        this.possibleMsepSearchDone = possibleMsepSearchDone;
    }

    /**
     * Sets the maximum length of any discriminating path, or -1 if unlimited.
     *
     * @param maxPathLength This maximum.
     */
    public void setMaxPathLength(int maxPathLength) {
        if (maxPathLength < -1) {
            throw new IllegalArgumentException("Max path length must be -1 (unlimited) or >= 0: " + maxPathLength);
        }

        this.maxPathLength = maxPathLength;
    }

    /**
     * Sets whether verbose output should be printed.
     *
     * @param verbose True if so.
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
     * Sets the FAS heuristic from PC used in search.
     *
     * @param pcHeuristicType This heuristic.
     * @see Pc
     */
    public void setPcHeuristicType(PcCommon.PcHeuristicType pcHeuristicType) {
        this.pcHeuristicType = pcHeuristicType;
    }

    /**
     * Sets whetehr the stable option will be used for search.
     *
     * @param stable True if so.
     */
    public void setStable(boolean stable) {
        this.stable = stable;
    }

    /**
     * Sets whether the discriminating path rule will be used in search.
     *
     * @param doDiscriminatingPathRule True if so.
     */
    public void setDoDiscriminatingPathRule(boolean doDiscriminatingPathRule) {
        this.doDiscriminatingPathRule = doDiscriminatingPathRule;
    }

    private void addColliders(Graph graph) {
        Map<Triple, Double> scores = new ConcurrentHashMap<>();

        List<Node> nodes = graph.getNodes();

        class Task extends RecursiveTask<Boolean> {
            final int from;
            final int to;
            final int chunk = 20;
            final List<Node> nodes;
            final Graph graph;

            public Task(List<Node> nodes, Graph graph, int from, int to) {
                this.nodes = nodes;
                this.graph = graph;
                this.from = from;
                this.to = to;
            }

            @Override
            protected Boolean compute() {
                if (this.to - this.from <= this.chunk) {
                    for (int i = this.from; i < this.to; i++) {
                        doNode(this.graph, scores, this.nodes.get(i));
                    }

                } else {
                    int mid = (this.to + this.from) / 2;

                    Task left = new Task(this.nodes, this.graph, this.from, mid);
                    Task right = new Task(this.nodes, this.graph, mid, this.to);

                    left.fork();
                    right.compute();
                    left.join();

                }
                return true;
            }
        }

        Task task = new Task(nodes, graph, 0, nodes.size());

        ForkJoinPoolInstance.getInstance().getPool().invoke(task);

        List<Triple> tripleList = new ArrayList<>(scores.keySet());

        // Most independent ones first.
        tripleList.sort((o1, o2) -> Double.compare(scores.get(o2), scores.get(o1)));

        for (Triple triple : tripleList) {
            Node a = triple.getX();
            Node b = triple.getY();
            Node c = triple.getZ();

            graph.setEndpoint(a, b, Endpoint.ARROW);
            graph.setEndpoint(c, b, Endpoint.ARROW);
        }
    }

    private void doNode(Graph graph, Map<Triple, Double> scores, Node b) {
        List<Node> adjacentNodes = new ArrayList<>(graph.getAdjacentNodes(b));

        if (adjacentNodes.size() < 2) {
            return;
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

            List<Node> adja = new ArrayList<>(graph.getAdjacentNodes(a));
            double score = Double.POSITIVE_INFINITY;
            Set<Node> S = null;

            SublistGenerator cg2 = new SublistGenerator(adja.size(), -1);
            int[] comb2;

            while ((comb2 = cg2.next()) != null) {
                Set<Node> s = GraphUtils.asSet(comb2, adja);
                IndependenceResult result = this.independenceTest.checkIndependence(a, c, s);
                double _score = result.getScore();

                if (_score < score) {
                    score = _score;
                    S = s;
                }
            }

            List<Node> adjc = new ArrayList<>(graph.getAdjacentNodes(c));

            SublistGenerator cg3 = new SublistGenerator(adjc.size(), -1);
            int[] comb3;

            while ((comb3 = cg3.next()) != null) {
                Set<Node> s = GraphUtils.asSet(comb3, adjc);
                IndependenceResult result = this.independenceTest.checkIndependence(c, a, s);
                double _score = result.getScore();

                if (_score < score) {
                    score = _score;
                    S = s;
                }
            }

            getSepsets().set(a, c, S);

            // S actually has to be non-null here, but the compiler doesn't know that.
            if (S != null && !S.contains(b)) {
                scores.put(new Triple(a, b, c), score);
            }
        }
    }
}




