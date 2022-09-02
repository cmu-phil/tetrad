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
import edu.cmu.tetrad.data.KnowledgeEdge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.TetradLogger;

import java.io.PrintStream;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Does a FCI-style latent variable search using mostly permutation-based reasoning. Follows GFCI to
 * an extent; the GFCI reference is this:
 * <p>
 * J.M. Ogarrio and P. Spirtes and J. Ramsey, "A Hybrid Causal Search Algorithm
 * for Latent Variable Models," JMLR 2016.
 *
 * @author jdramsey
 */
public final class BossFci implements GraphSearch {

    // The score used, if GS is used to build DAGs.
    private final Score score;

    // The logger to use.
    private final TetradLogger logger = TetradLogger.getInstance();

    // The covariance matrix being searched over, if continuous data is supplied. This is
    // no used by the algorithm but can be retrieved by another method if desired
    ICovarianceMatrix covarianceMatrix;

    // The background knowledge.
    private IKnowledge knowledge = new Knowledge2();

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
    private boolean possibleDsepDone = false;

    //============================CONSTRUCTORS============================//
    public BossFci(IndependenceTest test, Score score) {
        this.test = test;
        this.score = score;
    }

    //========================PUBLIC METHODS==========================//
    public Graph search() {
        this.logger.log("info", "Starting FCI algorithm.");
        this.logger.log("info", "Independence test = " + getTest() + ".");

        TeyssierScorer scorer = new TeyssierScorer(test, score);

        // Run BOSS-tuck to get a CPDAG (like GFCI with FGES)...
        Boss alg = new Boss(scorer);
        alg.setAlgType(Boss.AlgType.BOSS_TUCK);
        alg.setUseScore(useScore);
        alg.setUseRaskuttiUhler(useRaskuttiUhler);
        alg.setUseDataOrder(useDataOrder);
        alg.setDepth(depth);
        alg.setNumStarts(numStarts);
        alg.setKnowledge(knowledge);
        alg.setVerbose(false);

        List<Node> variables = this.score.getVariables();
        assert variables != null;

        alg.bestOrder(variables);
        this.graph = alg.getGraph(true);

        // Keep a copy of this CPDAG.
        Graph cpdag = new EdgeListGraph(this.graph);

        // Orient the CPDAG with all circle endpoints...
        this.graph.reorientAllWith(Endpoint.CIRCLE);

        // Copy the unshielded colliders from the copy of the CPDAG into the o-o graph.
        copyColliders(cpdag);

        // Remove as many edges as possible using the "reduce" rule, orienting as many
        // arrowheads this way as possible.
        reduce(scorer);

        // Optimally remove edges using the possible dsep rule. (Needed for correctness but
        // very heavy-handed.
        if (possibleDsepDone) {
            removeEdgesByPossibleDsep();
        }

//        SepsetProducer sepsets = new SepsetsTeyssier(this.graph, scorer, null, depth);
        SepsetProducer sepsets = new SepsetsGreedy(this.graph, test, null, depth);
        orientCollidersBySepset(cpdag, sepsets);

        // Apply final FCI orientation rules.
        FciOrient fciOrient = new FciOrient(sepsets);
        fciOrient.setKnowledge(knowledge);
        fciOrient.setCompleteRuleSetUsed(this.completeRuleSetUsed);
        fciOrient.setChangeFlag(false);
        fciOrient.setMaxPathLength(this.maxPathLength);
        fciOrient.doFinalOrientation(graph);

        this.graph.setPag(true);

        return this.graph;
    }

    private void removeEdgesByPossibleDsep() {
        SepsetsPossibleDsep sp = new SepsetsPossibleDsep(this.graph, test, this.knowledge, this.depth, this.maxPathLength);

        for (Edge edge : this.graph.getEdges()) {
            Node n1 = edge.getNode1();
            Node n2 = edge.getNode2();

            List<Node> nodes = sp.getSepset(n1, n2);

            if (nodes != null && nodes.size() > 0) {
                System.out.println("example possible dsep(" + n1 + ", " + n2 + ") = " + nodes);
                this.graph.removeEdge(edge);
            }
        }
    }

    private void reduce(TeyssierScorer scorer) {
        for (Edge edge : graph.getEdges()) {
            boolean remove = visit(scorer, edge.getNode1(), edge.getNode2());
            remove = remove || visit(scorer, edge.getNode2(), edge.getNode1());

            if (remove) {
                System.out.println("Removing " + edge + " by reduce rule");
                graph.removeEdge(edge.getNode1(), edge.getNode2());
            }
        }
    }

    private boolean visit(TeyssierScorer scorer, Node d, Node b) {
        Set<Node> adj = scorer.getAdjacentNodes(d);
        adj.retainAll(scorer.getAdjacentNodes(b));
        boolean remove = false;

        for (Node c : adj) {
            for (Node a : scorer.getAdjacentNodes(b)) {
                remove = remove || tryToRemove(scorer, a, b, c, d);
            }
        }

        return remove;
    }

    private boolean tryToRemove(TeyssierScorer scorer, Node a, Node b, Node c, Node d) {
        boolean remove = false;

        if (configuration(scorer, a, b, c, d)) {
            scorer.swap(b, c);

            if (configuration(scorer, d, c, b, a)) {
                System.out.println("Found by reduce rule: " + c + "<->" + d);
                graph.setEndpoint(b, c, Endpoint.ARROW);
                graph.setEndpoint(d, c, Endpoint.ARROW);
                remove = true;
            }

            scorer.swap(b, c);
        }

        return remove;
    }

    private static boolean configuration(TeyssierScorer scorer, Node a, Node b, Node c, Node d) {
        return distinct(a, b, c, d)
                && scorer.adjacent(a, b)
                && scorer.adjacent(b, c)
                && scorer.adjacent(c, d)
                && scorer.adjacent(b, d)
                && !scorer.adjacent(a, c)
                && !scorer.adjacent(a, d)
                && scorer.collider(a, b, c);
    }

    public void copyColliders(Graph cpdag) {
        List<Node> nodes = this.graph.getNodes();
        fciOrientbk(this.knowledge, this.graph, this.graph.getNodes());

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

    public void copyUnshieldedColliders(Graph cpdag) {
        List<Node> nodes = this.graph.getNodes();
        fciOrientbk(this.knowledge, this.graph, this.graph.getNodes());

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

                if (cpdag.isDefCollider(a, b, c) && !cpdag.isAdjacentTo(a, c)) {
                    this.graph.setEndpoint(a, b, Endpoint.ARROW);
                    this.graph.setEndpoint(c, b, Endpoint.ARROW);
                }
            }
        }
    }

    public void orientCollidersBySepset(Graph fgesGraph, SepsetProducer sepsets) {
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

                if (!fgesGraph.isDefCollider(a, b, c) && !this.graph.isDefCollider(a, b, c)) {
                    if (fgesGraph.isAdjacentTo(a, c) && !this.graph.isAdjacentTo(a, c)) {
                        List<Node> sepset = sepsets.getSepset(a, c);

                        if (sepset != null && !sepset.isEmpty() && !sepset.contains(b)) {
                            System.out.println("Orienting by sepsets: " + a + "->" + b + "<-" + c);
                            this.graph.setEndpoint(a, b, Endpoint.ARROW);
                            this.graph.setEndpoint(c, b, Endpoint.ARROW);
                        }

                    }
                }
            }
        }
    }

    private void fciOrientbk(IKnowledge knowledge, Graph graph, List<Node> variables) {
        this.logger.log("info", "Starting BK Orientation.");

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
            this.logger.log("knowledgeOrientation", SearchLogUtils.edgeOrientedMsg("Knowledge", graph.getEdge(from, to)));
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
            this.logger.log("knowledgeOrientation", SearchLogUtils.edgeOrientedMsg("Knowledge", graph.getEdge(from, to)));
        }

        this.logger.log("info", "Finishing BK Orientation.");
    }

    private static boolean distinct(Node a, Node b, Node c, Node d) {
        Set<Node> nodes = new HashSet<>();

        nodes.add(a);
        nodes.add(b);
        nodes.add(c);
        nodes.add(d);

        return nodes.size() == 4;
    }

    public IKnowledge getKnowledge() {
        return this.knowledge;
    }

    public void setKnowledge(IKnowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException();
        }

        this.knowledge = knowledge;
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

    //===========================================PRIVATE METHODS=======================================//

    /**
     * Orients according to background knowledge
     */
    private void fciOrientBk(IKnowledge knowledge, Graph graph, List<Node> variables) {
        this.logger.log("info", "Starting BK Orientation.");

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
            this.logger.log("knowledgeOrientation", SearchLogUtils.edgeOrientedMsg("Knowledge", graph.getEdge(from, to)));
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
            this.logger.log("knowledgeOrientation", SearchLogUtils.edgeOrientedMsg("Knowledge", graph.getEdge(from, to)));
        }

        this.logger.log("info", "Finishing BK Orientation.");
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

    private boolean isArrowpointAllowed(Node x, Node y, Graph graph) {
        if (graph.getEndpoint(x, y) == Endpoint.ARROW) {
            return true;
        }

        if (graph.getEndpoint(x, y) == Endpoint.TAIL) {
            return false;
        }

        if (graph.getEndpoint(y, x) == Endpoint.ARROW) {
            return !this.knowledge.isForbidden(x.getName(), y.getName());
        } else if (graph.getEndpoint(y, x) == Endpoint.TAIL) {
            return !this.knowledge.isForbidden(x.getName(), y.getName());
        }

        return graph.getEndpoint(x, y) == Endpoint.CIRCLE;
    }

    public void setPossibleDsepDone(boolean possibleDsepDone) {
        this.possibleDsepDone = possibleDsepDone;
    }
}
