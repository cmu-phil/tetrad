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
import edu.cmu.tetrad.util.SublistGenerator;
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
public final class LvSwap2 implements GraphSearch {

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
    private boolean doDiscriminatingPathTailRule = true;

    //============================CONSTRUCTORS============================//
    public LvSwap2(IndependenceTest test, Score score) {
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

        alg.bestOrder(this.score.getVariables());
        Graph G = alg.getGraph(true);

        retainUnshieldedColliders(G);

        Graph G0;
        Set<Triple> allT = new HashSet<>();
        Graph G2 = new EdgeListGraph(G);

        do {
            G0 = new EdgeListGraph(G);
            allT.addAll(swapRule(G, scorer));

            G = new EdgeListGraph(G2);

            removeShields(G, allT);
            orientColliders(G, allT);

        } while (!G.equals(G0));

        finalOrientation(knowledge, G);

        G.setGraphType(EdgeListGraph.GraphType.PAG);

        return G;
    }

    public static void retainUnshieldedColliders(Graph graph) {
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

    public static void retainColliders(Graph graph) {
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

                if (orig.isDefCollider(a, b, c)) {
                    graph.setEndpoint(a, b, Endpoint.ARROW);
                    graph.setEndpoint(c, b, Endpoint.ARROW);
                }
            }
        }
    }


    public static void retainArrows(Graph graph) {
        Graph orig = new EdgeListGraph(graph);
        graph.reorientAllWith(Endpoint.CIRCLE);

        List<Node> nodes = graph.getNodes();

        for (Node x : nodes) {
            for (Node y : nodes) {
                if (x == y) continue;
                if (orig.getEndpoint(x, y) == Endpoint.ARROW) {
                    graph.setEndpoint(x, y, Endpoint.ARROW);
                }
            }
        }
    }

    private void finalOrientation(Knowledge knowledge2, Graph G) {
        SepsetProducer sepsets = new SepsetsGreedy(G, test, null, depth);
        FciOrient fciOrient = new FciOrient(sepsets);
        fciOrient.setCompleteRuleSetUsed(this.completeRuleSetUsed);
        fciOrient.setDoDiscriminatingPathColliderRule(false);
        fciOrient.setDoDiscriminatingPathTailRule(doDiscriminatingPathTailRule);
        fciOrient.setMaxPathLength(this.maxPathLength);
        fciOrient.setKnowledge(knowledge2);
        fciOrient.setVerbose(true);
        fciOrient.doFinalOrientation(G);
    }

    private Set<Triple> extendedSwap(Graph G, TeyssierScorer scorer) {
        scorer.bookmark();

        Set<Triple> T = new HashSet<>();

        List<Node> Y = G.getNodes();

        for (Node y : Y) {
            List<Node> X = G.getAdjacentNodes(y);

            for (Node x : X) {
                if (x == y) continue;

                // Try to remove x*-*y
                List<Node> Z = G.getAdjacentNodes(x);
                Z.retainAll(G.getAdjacentNodes(y));

                // Need y<-oz*-*x
                Z.removeIf(z -> !(Edges.partiallyOrientedEdge(z, y).equals(G.getEdge(z, y))));

                if (Z.isEmpty()) continue;

                // Need to try making every combination of Z latent. If a combination works,
                // for Z = {Z1,...,Zm}, orient Y<->Z1, ..., Y<->Zm.
                SublistGenerator gen = new SublistGenerator(Z.size(), Z.size());
                int[] choice;

                while ((choice = gen.next()) != null) {
                    scorer.goToBookmark();

                    List<Node> ZZ = GraphUtils.asList(choice, Z);

                    for (Node z : ZZ) {
                        scorer.moveTo(z, scorer.size() - 1);
                    }

                    if (!scorer.adjacent(x, y)) {
                        for (Node z : ZZ) {
                            T.add(new Triple(x, z, y));


                        }
                    }
                }
            }
        }

        scorer.goToBookmark();

        return T;
    }

    private static Set<Triple> swapRule(Graph G, TeyssierScorer scorer) {
        Set<Triple> T = new HashSet<>();

        List<Node> nodes = G.getNodes();

        // For every x*-*y*-*w that is not already an unshielded collider...
        for (Node y : nodes) {
            for (Node x : G.getAdjacentNodes(y)) {
                if (x == y) continue;

                for (Node z : G.getAdjacentNodes(y)) {
                    if (x == z) continue;
                    if (y == z) continue;

                    // Check that  <x, y, z> is an unshielded collider or else is a shielded collider or noncollider
                    // (either way you can end up after possible reorientation with an unshielded collider),
                    if (!G.isDefCollider(x, y, z) && !G.isAdjacentTo(x, z)) continue;

                    scorer.bookmark();

                    // and make sure you're conditioning on district(x, G)...
//                    Set<Node> S = GraphUtils.pagMb(x, G);

//                    for (Node p : S) {
//                        scorer.tuck(p, x);
//                    }

                    scorer.swaptuck(x, y);

                    // If that's true, and if <x, y, z> is an unshielded collider in DAG(π),
                    if (scorer.collider(x, y, z) && !scorer.adjacent(x, z)) {

                        // look at each y2 commonly adjacent to both x and z,
                        Set<Node> adj = scorer.getAdjacentNodes(x);
                        adj.retainAll(scorer.getAdjacentNodes(z));

                        for (Node y2 : adj) {

                            // and x->y2<-z is an unshielded collider in DAG(swap(x, z, π))
                            // not already oriented as an unshielded collider in G,
                            if (scorer.collider(x, y2, z) && !scorer.adjacent(x, z)
                                    && !(G.isDefCollider(x, y2, z) && !G.isAdjacentTo(x, z))) {

                                // then add <x, y2, z> to the set of new unshielded colliders to process.
                                T.add(new Triple(x, y2, z));

//                                removeShields(G, T);
//                                orientColliders(G, T);

                            }
                        }
                    }

                    scorer.goToBookmark();
                }
            }
        }

        return T;
    }


    private void removeShields(Graph graph, Set<Triple> unshieldedColliders) {
        for (Triple triple : unshieldedColliders) {
            Node x = triple.getX();
            Node w = triple.getZ();

            Edge edge = graph.getEdge(x, w);

            if (edge != null) {
                graph.removeEdge(x, w);
                out.println("Removing (swap rule): " + edge);
            }
        }
    }

    private void orientColliders(Graph graph, Set<Triple> unshieldedColliders) {
        for (Triple triple : unshieldedColliders) {
            Node x = triple.getX();
            Node y = triple.getY();
            Node w = triple.getZ();

            if (graph.isAdjacentTo(x, y) && graph.isAdjacentTo(y, w) && !graph.isDefCollider(x, y, w)) {
                graph.setEndpoint(x, y, Endpoint.ARROW);
                graph.setEndpoint(w, y, Endpoint.ARROW);
                out.println("Orienting collider (Swap rule): " + GraphUtils.pathString(graph, x, y, w));
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

    public void setDoDefiniteDiscriminatingPathTailRule(boolean doDiscriminatingPathTailRule) {
        this.doDiscriminatingPathTailRule = doDiscriminatingPathTailRule;
    }
}
