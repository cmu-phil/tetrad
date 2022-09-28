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
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.SublistGenerator;
import edu.cmu.tetrad.util.TetradLogger;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static edu.cmu.tetrad.graph.GraphUtils.addForbiddenReverseEdgesForDirectedEdges;
import static edu.cmu.tetrad.graph.GraphUtils.retainUnshieldedColliders;

/**
 * Does an FCI-style latent variable search using permutation-based reasoning. Follows GFCI to
 * an extent; the GFCI reference is this:
 * <p>
 * J.M. Ogarrio and P. Spirtes and J. Ramsey, "A Hybrid Causal Search Algorithm
 * for Latent Variable Models," JMLR 2016.
 *
 * @author jdramsey
 */
public final class Bfci2 implements GraphSearch {

    // The score used, if GS is used to build DAGs.
    private final Score score;

    // The logger to use.
    private final TetradLogger logger = TetradLogger.getInstance();

    // The covariance matrix being searched over, if continuous data is supplied. This is
    // no used by the algorithm beut can be retrieved by another method if desired
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
    private int depth = -1;
    private boolean useRaskuttiUhler;
    private boolean useDataOrder = true;
    private boolean useScore = true;
    private boolean doDiscriminatingPathRule = true;
    private IKnowledge knowledge = new Knowledge2();

    //============================CONSTRUCTORS============================//
    public Bfci2(IndependenceTest test, Score score) {
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
        boss.setAlgType(Boss.AlgType.BOSS_OLD);
        boss.setUseScore(useScore);
        boss.setUseRaskuttiUhler(useRaskuttiUhler);
        boss.setUseDataOrder(useDataOrder);
        boss.setDepth(depth);
        boss.setNumStarts(numStarts);
        boss.setVerbose(false);

        List<Node> variables = this.score.getVariables();
        assert variables != null;

        boss.bestOrder(variables);
        Graph graph = boss.getGraph(false);  // Get the DAG

        if (score instanceof edu.cmu.tetrad.search.MagSemBicScore) {
            ((edu.cmu.tetrad.search.MagSemBicScore) score).setMag(graph);
        }

        IKnowledge knowledge2 = new Knowledge2((Knowledge2) knowledge);
//        addForbiddenReverseEdgesForDirectedEdges(SearchGraphUtils.cpdagForDag(graph), knowledge2);

        // Remove edges by conditioning on subsets of variables in triangles, orienting more colliders
//        triangleReduce2(graph, scorer, knowledge); // Adds <-> edges to the DAG


        LvBesJoe lvBesJoe = new LvBesJoe(score);
        lvBesJoe.setDepth(depth);
        lvBesJoe.setKnowledge(knowledge2);
        lvBesJoe.bes(graph, variables);
//
//        for (Edge edge : graph.getEdges()) {
//            if (Edges.isPartiallyOrientedEdge(edge)) {
//                if (edge.pointsTowards(edge.getNode2()) && knowledge.isForbidden(edge.getNode1().getName(), edge.getNode2().getName())) {
//                    graph.setEndpoint(edge.getNode2(), edge.getNode1(), Endpoint.ARROW);
//                } else if (edge.pointsTowards(edge.getNode1()) && knowledge.isForbidden(edge.getNode2().getName(), edge.getNode1().getName())) {
//                    graph.setEndpoint(edge.getNode2(), edge.getNode2(), Endpoint.ARROW);
//                }
//            }
//        }

//        graph = SearchGraphUtils.cpdagForDag(graph);
//
//        for (Edge edge : graph.getEdges()) {
//            if (edge.getEndpoint1() == Endpoint.TAIL) edge.setEndpoint1(Endpoint.CIRCLE);
//            if (edge.getEndpoint2() == Endpoint.TAIL) edge.setEndpoint2(Endpoint.CIRCLE);
//        }

        // Retain only the unshielded colliders.
        retainUnshieldedColliders(graph);

        // Do final FCI orientation rules app
        SepsetProducer sepsets = new SepsetsGreedy(graph, test, null, depth);
        FciOrient fciOrient = new FciOrient(sepsets);
        fciOrient.setCompleteRuleSetUsed(this.completeRuleSetUsed);
        fciOrient.setDoDiscriminatingPathRule(this.doDiscriminatingPathRule);
        fciOrient.setMaxPathLength(this.maxPathLength);
        fciOrient.setKnowledge(knowledge2);
        fciOrient.doFinalOrientation(graph);

        graph.setPag(true);

        return graph;
    }

    private static void triangleReduce1(Graph graph, TeyssierScorer scorer, IKnowledge knowledge) {
        boolean changed = true;

        while (changed) {
            changed = false;

            for (Edge edge : graph.getEdges()) {
                Node a = edge.getNode1();
                Node b = edge.getNode2();

                if (graph.isAdjacentTo(a, b)) {
                    List<Node> inTriangle = graph.getAdjacentNodes(a);
                    inTriangle.retainAll(graph.getAdjacentNodes(b));

                    Set<Node> _all = new HashSet<>(inTriangle);
                    _all.addAll(graph.getAdjacentNodes(a));
                    _all.addAll(graph.getAdjacentNodes(b));

                    List<Node> all = new ArrayList<>(_all);

                    SublistGenerator gen = new SublistGenerator(all.size(), all.size());
                    int[] choice;

                    float maxScore = Float.NEGATIVE_INFINITY;
                    List<Node> maxAfter = null;
                    boolean remove = false;

                    while ((choice = gen.next()) != null) {
                        List<Node> before = GraphUtils.asList(choice, all);
                        List<Node> after = new ArrayList<>(inTriangle);
                        after.removeAll(before);

                        List<Node> perm = new ArrayList<>(before);
                        perm.add(a);
                        perm.add(b);
                        perm.addAll(after);

                        float score = scorer.score(perm);

                        if (score >= maxScore && !scorer.adjacent(a, b)) {
                            maxScore = score;
                            maxAfter = after;
                            remove = !scorer.adjacent(a, b);
                        }
                    }

                    if (remove) {

                        for (Node x : maxAfter) {
                            changed = true;

                            // Only remove an edge and orient a new collider if it will create a bidirected edge.
//                            if (graph.getEndpoint(x, a) == Endpoint.ARROW || graph.getEndpoint(x, b) == Endpoint.ARROW) {
                            graph.removeEdge(a, b);
                            graph.setEndpoint(a, x, Endpoint.ARROW);
                            graph.setEndpoint(b, x, Endpoint.ARROW);

                            if (graph.getEndpoint(x, a) == Endpoint.CIRCLE && knowledge.isForbidden(a.getName(), x.getName())) {
                                graph.setEndpoint(x, a, Endpoint.ARROW);
                            }

                            if (graph.getEndpoint(x, b) == Endpoint.CIRCLE && knowledge.isForbidden(b.getName(), x.getName())) {
                                graph.setEndpoint(x, b, Endpoint.ARROW);
                            }

                        }
//                        }

//                        break;
                    }
                }
            }
        }
    }

    private static void triangleReduce2(Graph graph, TeyssierScorer scorer0, IKnowledge knowledge) {
        TeyssierScorer scorer = new TeyssierScorer(scorer0);
        Graph origGaph = new EdgeListGraph(graph);

        for (Edge edge : graph.getEdges()) {
            Node a = edge.getNode1();
            Node b = edge.getNode2();
            t2visit(origGaph, graph, scorer0, knowledge, scorer, a, b);
            t2visit(origGaph, graph, scorer0, knowledge, scorer, b, a);
        }
    }

    private static boolean t2visit(Graph origGraph, Graph graph, TeyssierScorer scorer0, IKnowledge knowledge, TeyssierScorer scorer,
                                   Node a, Node b) {
        if (!graph.isAdjacentTo(a, b)) return false;
        boolean changed = false;
        List<Node> _inTriangle = origGraph.getAdjacentNodes(a);
        _inTriangle.retainAll(origGraph.getAdjacentNodes(b));
        List<Node> parents = origGraph.getParents(a);
        parents.remove(b);
        for (Node n : _inTriangle) {
            parents.remove(n);
        }

        List<Node> inTriangle = new ArrayList<>();
        List<Node> all = new ArrayList<>();
        for (Node n : scorer0.getPi()) {
            if (_inTriangle.contains(n)) inTriangle.add(n);
            if (_inTriangle.contains(n) || n == a || n == b) all.add(n);
        }

        if (_inTriangle.isEmpty()) return false;

        SublistGenerator gen = new SublistGenerator(all.size(), all.size());
        int[] choice;

        float maxScore = Float.NEGATIVE_INFINITY;
        List<Node> maxAfter = null;
        boolean remove = false;

        while ((choice = gen.next()) != null) {
            List<Node> before = GraphUtils.asList(choice, all);
            List<Node> after = new ArrayList<>(inTriangle);
            after.removeAll(before);

            SublistGenerator gen2 = new SublistGenerator(parents.size(), -1);
            int[] choice2;

            while ((choice2 = gen2.next()) != null) {
                List<Node> p = GraphUtils.asList(choice2, parents);

                List<Node> perm = new ArrayList<>(p);

                for (Node n : all) {
                    perm.remove(n);
                    perm.add(n);
                }

                for (Node n : after) {
                    perm.remove(n);
                    perm.add(n);
                }

                float score = scorer.score(perm);

                if (score >= maxScore && !scorer.adjacent(a, b)) {
                    maxScore = score;
                    maxAfter = after;
                    remove = !scorer.adjacent(a, b);
                }
            }
        }

        if (remove) {
            for (Node x : maxAfter) {
                changed = true;

                // Only remove an edge and orient a new collider if it will create a bidirected edge.
                graph.removeEdge(a, b);

                if (graph.isAdjacentTo(a, x)) {
                    graph.setEndpoint(a, x, Endpoint.ARROW);

//                    if (graph.getEndpoint(x, a) == Endpoint.CIRCLE && knowledge.isForbidden(a.getName(), x.getName())) {
//                        graph.setEndpoint(x, a, Endpoint.ARROW);
//                    }
                }

                if (graph.isAdjacentTo(b, x)) {
                    graph.setEndpoint(b, x, Endpoint.ARROW);

//                    if (graph.getEndpoint(x, b) == Endpoint.CIRCLE && knowledge.isForbidden(b.getName(), x.getName())) {
//                        graph.setEndpoint(x, b, Endpoint.ARROW);
//                    }
                }
            }
        }

        return changed;
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

    public void setKnowledge(IKnowledge knowledge) {
        if (knowledge == null) throw new NullPointerException("Knowledge was null");
        this.knowledge = knowledge;
    }
}
