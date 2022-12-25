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
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.TetradLogger;

import java.io.PrintStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Does BOSS2, followed by two swap rules, then final FCI orientation.
 * </p>
 * Definitions
 * A(z, x, y, w) iff z*->x<-*y*-*w & ~adj(z, y) & ~adj(z, w) & maybe adj(x, w)
 * B(z, x, y, w) iff z*-*x*->y<-*w & ~adj(z, y) & ~adj(z, w) & ~adj(x, w)
 * BOSS2(π, score) is the permutation π‘ returned by BOSS2 for input permutation π
 * DAG(π, score) is the DAG built by BOSS (using Grow-Shrink) for permutation π
 * swap(x, y, π) is the permutation obtained from π by swapping x and y
 * </p>
 * Procedure LV-SWAP(π, score)
 * G1, π’ <- DAG(BOSS2(π, score))
 * G2 <- Keep only unshielded colliders in G1, turn all tails into circles
 * Find all <z, x, y, w> that satisfy A(z, x, y, w) in DAG(π‘, score) and B(z, x, y’, w) for some y’, in DAG(swap(x, y, π‘), score)
 * Orient all such x*->y’<-*w in G2
 * Add all such w*-*x to set S
 * Remove all edges in S from G2.
 * G3 <- Keep only unshielded colliders in G2, making all other endpoints circles.
 * G4 <- finalOrient(G3)
 * Full ruleset.
 * DDP tail orientation only.
 * Return PAG G4
 *
 * @author jdramsey
 */
public final class LvSwap implements GraphSearch {

    // The score used, if GS is used to build DAGs.
    private final Score score;

    // The logger to use.
    private final TetradLogger logger = TetradLogger.getInstance();

    // The covariance matrix being searched over, if continuous data is supplied. This is
    // not used by the algorithm beut can be retrieved by another method if desired
    ICovarianceMatrix covarianceMatrix;

    // The test used if Pearl's method is used ot build DAGs
    private IndependenceTest test;

    // Flag for complete rule set, true if you should use complete rule set, false otherwise.
    private boolean completeRuleSetUsed = true;

    // The maximum length for any discriminating path. -1 if unlimited; otherwise, a positive integer.
    private int maxPathLength = -1;
    private int numStarts = 1;
    private int depth = -1;
    private boolean useRaskuttiUhler;
    private boolean useDataOrder = true;
    private boolean useScore = true;
    private Knowledge knowledge = new Knowledge();
    private boolean verbose = false;
    private PrintStream out = System.out;
    private Boss.AlgType algType = Boss.AlgType.BOSS1;

    //============================CONSTRUCTORS============================//
    public LvSwap(IndependenceTest test, Score score) {
        this.test = test;
        this.score = score;
    }

    //========================PUBLIC METHODS==========================//
    public Graph search() {
        this.logger.log("info", "Starting FCI algorithm.");
        this.logger.log("info", "Independence test = " + this.test + ".");

        TeyssierScorer scorer = new TeyssierScorer(test, score);

        Boss alg = new Boss(scorer);
        alg.setAlgType(algType);
        alg.setUseScore(useScore);
        alg.setUseRaskuttiUhler(useRaskuttiUhler);
        alg.setUseDataOrder(useDataOrder);
        alg.setDepth(depth);
        alg.setNumStarts(numStarts);
        alg.setVerbose(verbose);

        List<Node> variables = this.score.getVariables();
        assert variables != null;

        alg.bestOrder(variables);

        Graph G = alg.getGraph(true); // Get the DAG

        Knowledge knowledge2 = new Knowledge(knowledge);
        retainUnshieldedColliders(G, knowledge2);

        Set<Triple> colliders;

        while (!(colliders = swapOrient(G, scorer)).isEmpty()) {
            swapRemove(G, colliders, knowledge2);
            swapOrientColliders(G, colliders, knowledge2);
        }

        finalOrientation(knowledge2, G);

        G.setGraphType(EdgeListGraph.GraphType.PAG);

        return G;
    }

    public static void retainUnshieldedColliders(Graph graph, Knowledge knowledge) {
        Graph orig = new EdgeListGraph(graph);
        graph.reorientAllWith(Endpoint.CIRCLE);
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

                if (orig.isDefCollider(a, b, c) && !orig.isAdjacentTo(a, c)) {
                    graph.setEndpoint(a, b, Endpoint.ARROW);
                    graph.setEndpoint(c, b, Endpoint.ARROW);
                }
            }
        }
    }

    private void finalOrientation(Knowledge knowledge2, Graph G4) {
        SepsetProducer sepsets = new SepsetsGreedy(G4, test, null, depth);
        FciOrient fciOrient = new FciOrient(sepsets);
        fciOrient.setCompleteRuleSetUsed(this.completeRuleSetUsed);
        fciOrient.setDoDiscriminatingPathColliderRule(false);
        fciOrient.setDoDiscriminatingPathTailRule(false);
        fciOrient.setMaxPathLength(this.maxPathLength);
        fciOrient.setKnowledge(knowledge2);
        fciOrient.setVerbose(true);
        fciOrient.doFinalOrientation(G4);
    }

    private Set<Triple>     swapOrient(Graph graph, TeyssierScorer scorer) {
        Set<Triple> colliders = new HashSet<>();

        graph = new EdgeListGraph(graph);
        List<Node> pi = scorer.getPi();

        for (Node y : pi) {
            for (Node x : graph.getAdjacentNodes(y)) {

                W:
                for (Node w : graph.getAdjacentNodes(y)) {
                    if (!distinct(x, y, w)) continue;
                    if (graph.isDefCollider(x, y, w) && graph.isAdjacentTo(x, w)) continue;

                    for (Node p : graph.getParents(x)) {
                        if (!(scorer.parent(p, x))) continue W;
                    }

                    // Check to make sure you have a left collider in the graph--i.e., z->x<-y
                    scorer.swap(x, y);

                    // Make aure you get a right unshielded collider in the scorer--i.e. x->y<-w
                    // with ~adj(x, w)
                    if (scorer.collider(x, y, w) && !scorer.adjacent(x, w)) {

                        // Make sure the new scorer orientations are all allowed in the graph...
                        Set<Node> adj = scorer.getAdjacentNodes(x);
                        adj.retainAll(scorer.getAdjacentNodes(w));

                        // If OK, mark w*-*x for removal and do any new collider orientations in the graph...
                        for (Node y2 : adj) {
                            if (scorer.collider(x, y2, w)) {
                                if (!graph.isDefCollider(x, y2, w) && graph.isAdjacentTo(x, y2) && graph.isAdjacentTo(w, y2)) {
                                    colliders.add(new Triple(x, y2, w));
                                }
                            }
                        }
                    }

                    scorer.swap(x, y);
                }
            }
        }

        return colliders;
    }

    private boolean distinct(Node... n) {
        for (int i = 0; i < n.length; i++) {
            for (int j = i + 1; j < n.length; j++) {
                if (n[i] == n[j]) return false;
            }
        }

        return true;
    }

    private void swapRemove(Graph graph, Set<Triple> colliders, Knowledge knowledge2) {
        for (Triple triple : colliders) {
            Node x = triple.getX();
            Node w = triple.getZ();

            Edge edge = graph.getEdge(x, w);

            if (graph.isAdjacentTo(x, w)) {
                graph.removeEdge(x, w);
                out.println("Removing (Swap rule): " + edge);
            } else {
                out.println("Edge already removed (Swap rule) " + x + "*-*" + w);
            }

            if (edge != null) {
                graph.removeEdge(x, w);
                out.println("Removing (swap rule): " + edge);
            }
        }
    }

    private void swapOrientColliders(Graph graph, Set<Triple> colliders, Knowledge knowledge2) {
        for (Triple triple : colliders) {
            Node x = triple.getX();
            Node y2 = triple.getY();
            Node w = triple.getZ();
            if (graph.isAdjacentTo(x, y2) && graph.isAdjacentTo(y2, w)) {
//                if (FciOrient.isArrowpointAllowed(x, y2, graph, knowledge2) && FciOrient.isArrowpointAllowed(w, y2, graph, knowledge2)) {
                graph.setEndpoint(x, y2, Endpoint.ARROW);
                graph.setEndpoint(w, y2, Endpoint.ARROW);
                out.println("Orienting collider (Swap rule): " + GraphUtils.pathString(graph, x, y2, w));
//                }
            }
        }
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
     * @param maxPathLength the maximum length of any discriminating path, or -1
     *                      if unlimited.
     */
    public void setMaxPathLength(int maxPathLength) {
        if (maxPathLength < -1) {
            throw new IllegalArgumentException("Max path length must be -1 (unlimited) or >= 0: " + maxPathLength);
        }

        this.maxPathLength = maxPathLength;
    }

    public void setTest(IndependenceTest test) {
        this.test = test;
    }

    public void setCovarianceMatrix(ICovarianceMatrix covarianceMatrix) {
        this.covarianceMatrix = covarianceMatrix;
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

    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public void setOut(PrintStream out) {
        this.out = out;
    }

    public void setAlgType(Boss.AlgType algType) {
        this.algType = algType;
    }
}
