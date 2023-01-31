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
import org.jetbrains.annotations.NotNull;

import java.io.PrintStream;
import java.util.*;

import static edu.cmu.tetrad.graph.GraphUtils.removeByPossibleDsep;
import static java.util.Collections.reverse;

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

    public enum AlgType {LVSwap1, LVSwap2a, LVSwap2b}

    private AlgType algType = AlgType.LVSwap1;

    private Boss.AlgType bossAlgType = Boss.AlgType.BOSS1;

    // The score used, if GS is used to build DAGs.
    private final Score score;

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
    private boolean doDiscriminatingPathTailRule = true;

    //============================CONSTRUCTORS============================//
    public LvSwap(IndependenceTest test, Score score) {
        this.test = test;
        this.score = score;
    }

    //========================PUBLIC METHODS==========================//
    public Graph search() {
        if (algType == AlgType.LVSwap1) {
            return lvswap1();
        } else if (algType == AlgType.LVSwap2a) {
            return lvswap2a();
        } else if (algType == AlgType.LVSwap2b) {
            return lvswap2b();
        }

        throw new IllegalArgumentException("Unexpected alg type: " + algType);
    }

    // Bryan.
    public Graph lvswap1() {
        TeyssierScorer scorer = new TeyssierScorer(test, score);

        Boss alg = new Boss(scorer);
        alg.setAlgType(bossAlgType);
        alg.setUseScore(useScore);
        alg.setUseRaskuttiUhler(useRaskuttiUhler);
        alg.setUseDataOrder(useDataOrder);
        alg.setDepth(depth);
        alg.setNumStarts(numStarts);
        alg.setVerbose(verbose);

        alg.bestOrder(this.score.getVariables());
        Graph G = alg.getGraph(true);

        Graph GBoss = new EdgeListGraph(G);

        retainUnshieldedColliders(G);

        scorer.bookmark();

        List<Node> pi = scorer.getPi();
        reverse(pi);

        for (Node x : pi) {
            Map<Node, List<Node>> T = new HashMap<>();

            List<Node> xParents = GBoss.getParents(x);

            int _depth = depth < 0 ? xParents.size() : depth;
            _depth = Math.min(_depth, xParents.size());

            // Order of increasing size
            SublistGenerator gen = new SublistGenerator(xParents.size(), _depth);
            int[] choice;

            while ((choice = gen.next()) != null) {
                List<Node> XSubset = GraphUtils.asList(choice, xParents);

                for (Node z : getComplement(xParents, XSubset)) {
                    if (adj(x, XSubset, GBoss).contains(z)) {
                        scorer.goToBookmark();

                        List<Node> _sub = new ArrayList<>(XSubset);
                        _sub.remove(z);

                        for (Node w : _sub) {
                            scorer.moveTo(w, scorer.index(x));
                        }

                        if (!scorer.parent(z, x)) T.put(z, XSubset);
                    }
                }
            }

            for (Node z : T.keySet()) {
                if (!T.get(z).isEmpty()) {
                    G.removeEdge(x, z);

                    for (Node y : T.get(z)) {
                        if (G.isAdjacentTo(x, y) && G.isAdjacentTo(y, z)) {
                            G.setEndpoint(x, y, Endpoint.ARROW);
                            G.setEndpoint(z, y, Endpoint.ARROW);
                        }
                    }
                }
            }
        }

        scorer.goToBookmark();

        finalOrientation(knowledge, G);

        if (SearchGraphUtils.isLegalPag(G).isLegalPag()) {
            G.setGraphType(EdgeListGraph.GraphType.PAG);
        }

        return G;
    }

    public Graph lvswap2a() {
        TeyssierScorer scorer = new TeyssierScorer(test, score);

        Boss alg = new Boss(scorer);
        alg.setAlgType(Boss.AlgType.BOSS2);
        alg.setUseScore(useScore);
        alg.setUseRaskuttiUhler(useRaskuttiUhler);
        alg.setUseDataOrder(useDataOrder);
        alg.setDepth(depth);
        alg.setNumStarts(numStarts);
        alg.setVerbose(verbose);

        alg.bestOrder(this.score.getVariables());
        Graph G = alg.getGraph(true);

        retainUnshieldedColliders(G);

//        removeByPossibleDsep(G, test, new SepsetMap());


        scorer.bookmark();

        Set<Triple> T = new HashSet<>();

        List<Node> pi = scorer.getPi();

//        for (Node y : pi) {
////            List<Node> adjy = G.getAdjacentNodes(y);
//
//            for (Node x : pi) {
//                for (Node z : pi) {
//                    if (y == x) continue;
//                    if (y == z) continue;
//                    if (x == z) continue;

        for (Node y : pi) {
            List<Node> adjy = G.getAdjacentNodes(y);

            for (Node x : adjy) {
                for (Node z : adjy) {
                    if (x == z) continue;
//                    if (!G.isAdjacentTo(x, z)) continue;
                    if (T.contains(new Triple(x, y, z))) continue;

                    scorer.goToBookmark();
                    scorer.swaptuck(x, y, z, true);

                    if (!scorer.adjacent(x, z) && scorer.collider(x, y, z)) {
                        Set<Node> adj = scorer.getAdjacentNodes(x);
                        adj.retainAll(scorer.getAdjacentNodes(z));

                        for (Node w : adj) {
                            if (scorer.collider(x, w, z)) {
                                T.add(new Triple(x, w, z));
                            }
                        }
                    }
                }
            }
        }

        removeShields(G, T);
        retainUnshieldedColliders(G);
        orientColliders(G, T);

        finalOrientation(knowledge, G);

        if (SearchGraphUtils.isLegalPag(G).isLegalPag()) {
            G.setGraphType(EdgeListGraph.GraphType.PAG);
        }

        scorer.goToBookmark();
        return G;
    }

    public Graph lvswap2b() {
        TeyssierScorer scorer = new TeyssierScorer(test, score);

        BossDC alg = new BossDC(scorer);
        alg.setAlgType(bossAlgType);
//        alg.setUseScore(useScore);
//        alg.setUseRaskuttiUhler(useRaskuttiUhler);
        alg.setUseDataOrder(useDataOrder);
        alg.setDepth(depth);
        alg.setNumStarts(numStarts);
        alg.setVerbose(verbose);

        alg.bestOrder(this.score.getVariables());
        Graph G = alg.getGraph(false);

        retainUnshieldedColliders(G);

        scorer.bookmark();

        Set<Triple> T = new HashSet<>();

        List<Node> pi = scorer.getPi();

//        for (Node y : pi) {
//            List<Node> adjy = G.getAdjacentNodes(y);

//            for (Node x : pi) {
//                for (Node z : pi) {
//                    if (y == x) continue;
//                    if (y == z) continue;
//                    if (x == z) continue;

        for (Node y : pi) {
            List<Node> adjy = G.getAdjacentNodes(y);

            for (Node x : adjy) {
                for (Node z : adjy) {
                    if (!scorer.adjacent(x, z)) continue;
                    if (T.contains(new Triple(x, y, z))) continue;

                    scorer.goToBookmark();
                    scorer.swaptuck(x, y, z, false);

                    if (!scorer.adjacent(x, z) && scorer.collider(x, y, z)) {
                        Set<Node> adj = scorer.getAdjacentNodes(x);
                        adj.retainAll(scorer.getAdjacentNodes(z));

                        for (Node w : adj) {
                            if (scorer.collider(x, w, z)) {
                                T.add(new Triple(x, w, z));
                            }
                        }
                    } else {
                        scorer.bookmark();
                    }

                    scorer.goToBookmark();
                    scorer.swaptuck(x, y, z, true);

                    if (!scorer.adjacent(x, z) && scorer.collider(x, y, z)) {
                        Set<Node> adj = scorer.getAdjacentNodes(x);
                        adj.retainAll(scorer.getAdjacentNodes(z));

                        for (Node w : adj) {
                            if (scorer.collider(x, w, z)) {
                                T.add(new Triple(x, w, z));
                            }
                        }
                    }
                }
            }
        }

        removeShields(G, T);
        retainUnshieldedColliders(G);
        orientColliders(G, T);

        finalOrientation(knowledge, G);

        if (SearchGraphUtils.isLegalPag(G).isLegalPag()) {
            G.setGraphType(EdgeListGraph.GraphType.PAG);
        }

        scorer.goToBookmark();
        return G;
    }

    public Graph lvswap3() {
        TeyssierScorer scorer = new TeyssierScorer(test, score);

        Boss alg = new Boss(scorer);
        alg.setAlgType(bossAlgType);
        alg.setUseScore(useScore);
        alg.setUseRaskuttiUhler(useRaskuttiUhler);
        alg.setUseDataOrder(useDataOrder);
        alg.setDepth(depth);
        alg.setNumStarts(numStarts);
        alg.setVerbose(verbose);

        alg.bestOrder(this.score.getVariables());
        Graph G = alg.getGraph(true);

        retainUnshieldedColliders(G);

        Graph G2 = new EdgeListGraph(G);

        scorer.bookmark();

        Set<Triple> T = new HashSet<>();
        Set<Triple> allT = new HashSet<>();

        do {
            allT.addAll(T);

            G = new EdgeListGraph(G2);

            removeShields(G, allT);
            retainUnshieldedColliders(G);
            orientColliders(G, allT);

            T = new HashSet<>();
            List<Node> nodes = G.getNodes();

            for (Node y : nodes) {
                for (Node x : G.getAdjacentNodes(y)) {
                    for (Node z : G.getAdjacentNodes(y)) {
                        if (x == y) continue;
                        if (x == z) continue;
                        if (y == z) continue;

                        if (!G.isAdjacentTo(x, z)) continue;

                        scorer.goToBookmark();

                        List<Node> children = new ArrayList<>(scorer.getAdjacentNodes(y));

                        int _depth = depth < 0 ? children.size() : depth;
                        _depth = Math.min(_depth, children.size());

                        // Order of increasing size
                        SublistGenerator gen = new SublistGenerator(children.size(), _depth);
                        int[] choice;

                        while ((choice = gen.next()) != null) {
                            if (choice.length == 0) continue;
                            scorer.goToBookmark();

                            List<Node> Q = GraphUtils.asList(choice, children);

                            for (Node w : Q) {
                                scorer.moveTo(w, scorer.index(y));
                            }

                            if (scorer.collider(x, y, z) && !scorer.adjacent(x, z) && !G.isDefCollider(x, y, z)) {
                                T.add(new Triple(x, y, z));
                                break;
                            }
                        }
                    }
                }
            }
        } while (!allT.containsAll(T));

        finalOrientation(knowledge, G);

        if (SearchGraphUtils.isLegalPag(G).isLegalPag()) {
            G.setGraphType(EdgeListGraph.GraphType.PAG);
        }

        return G;
    }

    @NotNull
    private static List<Node> getComplement(List<Node> X, List<Node> Y) {
        List<Node> complement = new ArrayList<>(X);
        complement.removeAll(Y);
        return complement;
    }

    private Set<Node> adj(Node x, List<Node> Y, Graph g) {
        Set<Node> adj = new HashSet<>();

        for (Node y : Y) {
            adj.addAll(g.getAdjacentNodes(y));
        }

        adj.remove(x);

        return adj;
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

    public void setAlgType(AlgType bossAlgType) {
        this.algType = bossAlgType;
    }

    public void setBossAlgType(Boss.AlgType algType) {
        this.bossAlgType = algType;
    }

    public void setDoDefiniteDiscriminatingPathTailRule(boolean doDiscriminatingPathTailRule) {
        this.doDiscriminatingPathTailRule = doDiscriminatingPathTailRule;
    }
}
