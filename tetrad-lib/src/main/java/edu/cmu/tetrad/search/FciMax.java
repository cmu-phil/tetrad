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
import edu.cmu.tetrad.search.utils.FciOrient;
import edu.cmu.tetrad.search.utils.R0R4StrategyTestBased;
import edu.cmu.tetrad.search.utils.PcCommon;
import edu.cmu.tetrad.search.utils.SepsetMap;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.MillisecondTimes;
import edu.cmu.tetrad.util.SublistGenerator;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

/**
 * Modifies FCI to do orientation of unshielded colliders (X*-*Y*-*Z with X and Z not adjacent) using the max-P rule
 * (see the PC-Max algorithm). This reference is relevant:
 * <p>
 * Raghu, V. K., Zhao, W., Pu, J., Leader, J. K., Wang, R., Herman, J., ... &amp; Wilson, D. O. (2019). Feasibility of
 * lung cancer prediction from low-dose CT scan and smoking factors using causal models. Thorax, 74(7), 643-649.
 * <p>
 * Max-P triple orientation is a method for orienting unshielded triples X*=-*Y*-*Z as one of the following: (a)
 * Collider, X-&gt;Y&lt;-Z, or (b) Noncollider, X--&gt;Y--&gt;Z, or X&lt;-Y&lt;-Z, or X&lt;-Y-&gt;Z. One does this by
 * conditioning on subsets of adj(X) or adj(Z). One first checks conditional independence of X and Z conditional on each
 * of these subsets, and lists the p-values for each test. Then, one chooses the conditioning set out of all of these
 * that maximizes the p-value. If this conditioning set contains Y, then the triple is judged to be a noncollider;
 * otherwise, it is judged to be a collider.
 * <p>
 * All unshielded triples in the graph given by FAS are judged as colliders or non-colliders and the colliders oriented.
 * Then the final FCI orientation rules are applied, as in FCI.
 * <p>
 * This class is configured to respect knowledge of forbidden and required edges, including knowledge of temporal
 * tiers.
 *
 * @author josephramsey
 * @version $Id: $Id
 * @see Fci
 * @see Fas
 * @see FciOrient
 * @see Knowledge
 */
public final class FciMax implements IGraphSearch {
    /**
     * The independence test.
     */
    private final IndependenceTest independenceTest;
    /**
     * The logger.
     */
    private final TetradLogger logger = TetradLogger.getInstance();
    /**
     * The sepsets from the FAS search.
     */
    private SepsetMap sepsets;
    /**
     * The background knowledge.
     */
    private Knowledge knowledge = new Knowledge();
    /**
     * The elapsed time of search.
     */
    private long elapsedTime;
    /**
     * The PC heuristic from PC used in search.
     */
    private PcCommon.PcHeuristicType pcHeuristicType = PcCommon.PcHeuristicType.NONE;
    /**
     * Whether the stable option will be used for search.
     */
    private boolean stable = false;
    /**
     * Whether the discriminating path rule will be used in search.
     */
    private boolean completeRuleSetUsed = true;
    /**
     * Determines whether the discriminating path tail rule should be applied during the search. If set to true, the
     * rule will be applied. If set to false, the rule will not be applied.
     */
    private boolean doDiscriminatingPathTailRule = true;
    /**
     * This variable specifies whether the discriminating path collider rule should be applied during the search. If set
     * to true, the rule will be applied; if set to false, the rule will not be applied.
     */
    private boolean doDiscriminatingPathColliderRule = true;
    /**
     * Whether the discriminating path rule will be used in search.
     */
    private boolean possibleMsepSearchDone = true;
    /**
     * The maximum length of any discriminating path, or -1 if unlimited.
     */
    private int maxPathLength = -1;
    /**
     * The maximum number of variables conditioned in any test.
     */
    private int depth = -1;
    /**
     * Whether verbose output should be printed.
     */
    private boolean verbose = false;
    /**
     * Whether the final orientation step should be left out.
     */
    private boolean ablationLeaveOutFinalOrientation = false;
    private boolean repairFaultyPag;

    /**
     * Constructor.
     *
     * @param independenceTest a {@link edu.cmu.tetrad.search.IndependenceTest} object
     */
    public FciMax(IndependenceTest independenceTest) {
        if (independenceTest == null) {
            throw new NullPointerException();
        }

        this.independenceTest = independenceTest;
    }

    /**
     * Performs the search and returns the PAG.
     *
     * @return This PAG.
     */
    public Graph search() {
        long start = MillisecondTimes.timeMillis();

        Fas fas = new Fas(getIndependenceTest());

        if (verbose) {
            TetradLogger.getInstance().log("Starting FCI-Max algorithm.");
            TetradLogger.getInstance().log("Independence test = " + getIndependenceTest() + ".");
        }

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
            FciOrient fciOrient = new FciOrient(
                    R0R4StrategyTestBased.defaultConfiguration(independenceTest, new Knowledge()));
            graph.paths().removeByPossibleMsep(independenceTest, sepsets);

            // Reorient all edges as o-o.
            graph.reorientAllWith(Endpoint.CIRCLE);
        }

        // Step CI C (Zhang's step F3.)

        FciOrient fciOrient = new FciOrient(
                R0R4StrategyTestBased.defaultConfiguration(independenceTest, new Knowledge()));

        fciOrient.fciOrientbk(this.knowledge, graph, graph.getNodes());

        Set<Triple> unshieldedColldiders = new HashSet<>();

        addColliders(graph, unshieldedColldiders);

        if (!ablationLeaveOutFinalOrientation) {
            fciOrient.finalOrientation(graph);
        }

        if (repairFaultyPag) {
            graph = GraphUtils.repairFaultyPag(graph, fciOrient, knowledge, unshieldedColldiders, verbose);
        }

        long stop = MillisecondTimes.timeMillis();

        this.elapsedTime = stop - start;

        return graph;
    }

    /**
     * Sets the maximum nubmer of variables conditioned in any test.
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
     * Sets whether Zhang's complete rule set is used in the search.
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
     * @param possibleMsepSearchDone True, if so.
     */
    public void setPossibleMsepSearchDone(boolean possibleMsepSearchDone) {
        this.possibleMsepSearchDone = possibleMsepSearchDone;
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
     * Sets whether verbose output should be printed.
     *
     * @param verbose True, if so.
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
     * Sets whether the stable option will be used for search.
     *
     * @param stable True, if so.
     */
    public void setStable(boolean stable) {
        this.stable = stable;
    }

    /**
     * Sets whether the discriminating path tail rule should be applied during the search.
     *
     * @param doDiscriminatingPathTailRule True, if the rule should be applied. False otherwise.
     */
    public void setDoDiscriminatingPathTailRule(boolean doDiscriminatingPathTailRule) {
        this.doDiscriminatingPathTailRule = doDiscriminatingPathTailRule;
    }


    /**
     * Adds colliders to the given graph.
     *
     * @param graph             The graph to which colliders should be added.
     * @param unshieldedColliders
     */
    private void addColliders(Graph graph, Set<Triple> unshieldedColliders) {
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

        int parallelism = Runtime.getRuntime().availableProcessors();
        new ForkJoinPool(parallelism).invoke(task);

        List<Triple> tripleList = new ArrayList<>(scores.keySet());

        // Most independent ones first.
        tripleList.sort((o1, o2) -> Double.compare(scores.get(o2), scores.get(o1)));

        for (Triple triple : tripleList) {
            Node a = triple.getX();
            Node b = triple.getY();
            Node c = triple.getZ();

            graph.setEndpoint(a, b, Endpoint.ARROW);
            graph.setEndpoint(c, b, Endpoint.ARROW);

            unshieldedColliders.add(new Triple(a, b, c));
        }
    }

    /**
     * Performs the DO operation on a node in the graph.
     *
     * @param graph  The graph containing the nodes.
     * @param scores The map of node triples to scores.
     * @param b      The node on which to perform the DO operation.
     */
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

    /**
     * Sets whether the discriminating path collider rule should be applied during the search.
     *
     * @param doDiscriminatingPathColliderRule True, if the rule should be applied. False otherwise.
     */
    public void setDoDiscriminatingPathColliderRule(boolean doDiscriminatingPathColliderRule) {
        this.doDiscriminatingPathColliderRule = doDiscriminatingPathColliderRule;
    }

    /**
     * Sets whether to leave out the final orientation in the FCI search.
     *
     * @param ablationLeaveOutFinalOrientation true to leave out the final orientation, false otherwise.
     */
    public void setLeaveOutFinalOrientation(boolean ablationLeaveOutFinalOrientation) {
        this.ablationLeaveOutFinalOrientation = ablationLeaveOutFinalOrientation;
    }

    public void setRepairFaultyPag(boolean repairFaultyPag) {
        this.repairFaultyPag = repairFaultyPag;
    }
}




