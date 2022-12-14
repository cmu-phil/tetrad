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
import edu.cmu.tetrad.util.TetradLogger;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static edu.cmu.tetrad.graph.GraphUtils.retainUnshieldedColliders;

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
    private boolean doDiscriminatingPathColliderRule = true;
    private boolean doDiscriminatingPathTailRule = true;
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

        Boss boss = new Boss(scorer);
        boss.setAlgType(algType);
        boss.setUseScore(useScore);
        boss.setUseRaskuttiUhler(useRaskuttiUhler);
        boss.setUseDataOrder(useDataOrder);
        boss.setDepth(depth);
        boss.setNumStarts(numStarts);
//        boss.setKnowledge(knowledge);
        boss.setVerbose(verbose);

        List<Node> pi = boss.bestOrder(scorer.getPi());
        scorer.score(pi);
        Graph G1 = scorer.getGraph(false);

        Knowledge knowledge2 = new Knowledge(knowledge);
//        addForbiddenReverseEdgesForDirectedEdges(SearchGraphUtils.cpdagForDag(G1), knowledge2);

        Graph G2 = new EdgeListGraph(G1);
        retainUnshieldedColliders(G2, knowledge2);

        Graph G3 = new EdgeListGraph(G2);

        Set<Edge> removed = new HashSet<>();
        Set<Triple> colliders = new HashSet<>();

        Graph G0;

        do {
            G0 = new EdgeListGraph(G3);
            G3 = swapOrient(G3, scorer, knowledge2, removed, colliders);
            removeDdpCovers(G3, scorer, removed, colliders);
            G3 = swapRemove(G3, removed);
            G3 = swapOrientColliders(G3, colliders);

//            removeDdpCovers(G3, scorer, removed, colliders);
//            G3 = swapRemove(G3, removed);
//            G3 = swapOrientColliders(G3, colliders);

        } while (!G0.equals(G3));


        // Do final FCI orientation rules app
        Graph G4 = new EdgeListGraph(G3);
//        retainUnshieldedColliders(G4, knowledge2);

        finalOrientation(knowledge2, G4);

        G4.setGraphType(EdgeListGraph.GraphType.PAG);

        return G4;
    }

    private void removeDdpCovers(Graph G4, TeyssierScorer scorer, Set<Edge> toRemove, Set<Triple> colliders) {
        List<Node> nodes = G4.getNodes();

        for (Node n1 : nodes) {
            for (Node n2 : nodes) {
                if (n1 == n2) continue;
                if (!G4.isAdjacentTo(n1, n2)) continue;

//                System.out.println("Checking " + n1 + " --- " + n2);
                List<List<Node>> coveredDdps = coveredDdps(n1, n2, G4);
//                System.out.println("Done checking " + n1 + " --- " + n2);


                D:
                for (List<Node> path : coveredDdps) {
                    if (!G4.isAdjacentTo(n1, n2)) continue;

                    System.out.println("\nEdge from 'from' to 'to': " + G4.getEdge(n1, n2));

                    for (int i = 1; i < path.size() - 2; i++) {
                        System.out.println(G4.getEdge(path.get(i), n2));
                    }

                    System.out.println("DDP path: " + GraphUtils.pathString(G4, path));

                    if (path.size() >= 3) {
                        scorer.bookmark();

                        Node bn = path.get(path.size() - 3);
                        Node c = path.get(path.size() - 2);
                        Node d = path.get(path.size() - 1);

                        for (int i = 1; i <= path.size() - 3; i++) {
                            if (scorer.index(path.get(i)) > scorer.index(d)) continue D;
                            if (scorer.index(path.get(i)) > scorer.index(c)) continue D;
                            if (scorer.index(path.get(0)) > scorer.index(path.get(i))) continue D;
//                            scorer.tuck(path.get(i), scorer.index(d));
//                            scorer.tuck(path.get(i), scorer.index(c));
                        }

                        reverseTuck(c, scorer.index(d), scorer);

//                        scorer.tuck(path.get(0), scorer.index(path.get(1)));
//                        scorer.tuck(path.get(0), scorer.index(c));
//                        scorer.tuck(path.get(0), scorer.index(d));


//                        if (G4.getEndpoint(c, d) == Endpoint.ARROW) {
                        if (!scorer.adjacent(n1, n2)) {// && G4.getEndpoint(d, c) == Endpoint.CIRCLE) {
//                            G4.removeEdge(n1, n2);
                            colliders.add(new Triple(bn, c, d));

//                            G4.setEndpoint(bn, c, Endpoint.ARROW);
//                            G4.setEndpoint(d, c, Endpoint.ARROW);
                            toRemove.add(G4.getEdge(n1, n2));
                        }

//                        if (!flag && !scorer.adjacent(n1, n2)) {// if (G4.getEndpoint(c, d) == Endpoint.ARROW && G4.getEndpoint(d, c) == Endpoint.CIRCLE) {
//                            G4.setEndpoint(d, c, Endpoint.TAIL);
//                            toRemove.add(G4.getEdge(n1, n2));
//                        }


                        scorer.goToBookmark();
                    }
                }
            }
        }

    }

    public boolean reverseTuck(Node k, int j, TeyssierScorer scorer) {
//        if (scorer.adjacent(k, scorer.get(j))) return false;
//        if (scorer.coveredEdge(k, scorer.get(j))) return false;
        int _k = scorer.index(k);
        if (j <= _k) return false;

        Set<Node> descendants = scorer.getDescendants(k);

        System.out.println("Doing a reverse tuck; k = " + k + " pi(j) = " + scorer.get(j));
        System.out.println("Descendanta of " + k + " = " + descendants);

        System.out.println("Iterating down from " + j + " to " + _k);
        System.out.println("Pi before = " + scorer.getPi());

        for (int i = j; i >= 0; i--) {
            Node varI = scorer.get(i);
            if (descendants.contains(varI)) {
                scorer.moveTo(varI, j);
            }
        }

        System.out.println("Pi after = " + scorer.getPi());

        return true;
    }

    private void finalOrientation(Knowledge knowledge2, Graph G4) {
        SepsetProducer sepsets = new SepsetsGreedy(G4, test, null, depth);
        FciOrient fciOrient = new FciOrient(sepsets);
        fciOrient.setCompleteRuleSetUsed(this.completeRuleSetUsed);
        fciOrient.setDoDiscriminatingPathColliderRule(this.doDiscriminatingPathColliderRule);
        fciOrient.setDoDiscriminatingPathTailRule(this.doDiscriminatingPathTailRule);
        fciOrient.setMaxPathLength(this.maxPathLength);
        fciOrient.setKnowledge(knowledge2);
        fciOrient.setVerbose(true);
        fciOrient.doFinalOrientation(G4);
    }

    private Graph swapOrient(Graph graph, TeyssierScorer scorer, Knowledge knowledge, Set<Edge> removed, Set<Triple> colliders) {
        removed.clear();

        graph = new EdgeListGraph(graph);
        List<Node> pi = scorer.getPi();

        for (Node y : pi) {
            for (Node x : graph.getAdjacentNodes(y)) {
                for (Node w : graph.getAdjacentNodes(y)) {
                    for (Node z : graph.getAdjacentNodes(x)) {
                        if (!distinct(z, x, y, w)) continue;

                        // Check to make sure you have a left collider in the graph--i.e., z->x<-y
                        // with adj(w, x)
                        if (graph.isDefCollider(z, x, y) && !graph.isAdjacentTo(z, y) && !graph.isDefCollider(x, y, w)) {// && !graph.isAdjacentTo(z, y) && scorer.adjacent(x, w)) {
                            scorer.swap(x, y);

                            // Make aure you get a right unshielded collider in the scorer--i.e. x->y<-w
                            // with ~adj(x, w)
                            if (scorer.collider(x, y, w) && !scorer.adjacent(x, w)) {/// && scorer.adjacent(z, y)) {

                                // Make sure the new scorer orientations are all allowed in the graph...
                                Set<Node> adj = scorer.getAdjacentNodes(x);
                                adj.retainAll(scorer.getAdjacentNodes(w));

                                boolean conflicting = false;

                                for (Node y2 : adj) {
                                    if (graph.isDefCollider(x, y2, w) && !scorer.collider(x, y2, w)) {
                                        conflicting = true;
                                        break;
                                    }
                                }

                                if (!conflicting) {

                                    // If OK, mark w*-*x for removal and do any new collider orientations in the graph...
                                    Edge edge = graph.getEdge(w, x);

                                    if (edge != null && !removed.contains(edge)) {
                                        out.println("Marking " + edge + " for removal (swapping " + x + " and " + y + ")");
                                        removed.add(edge);
//                                    }

                                        for (Node y2 : adj) {
                                            if (scorer.collider(x, y2, w)) {
                                                if (!graph.isDefCollider(x, y2, w) && graph.isAdjacentTo(x, y2) && graph.isAdjacentTo(w, y2)) {
                                                    colliders.add(new Triple(x, y2, w));

//                                                    graph.setEndpoint(x, y2, Endpoint.ARROW);
//                                                    graph.setEndpoint(w, y2, Endpoint.ARROW);
//                                                    out.println("Orienting collider " + GraphUtils.pathString(graph, x, y2, w));
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            scorer.swap(x, y);
                        }
                    }
                }
            }
        }

        return graph;
    }

    private boolean distinct(Node... n) {
        for (int i = 0; i < n.length; i++) {
            for (int j = i + 1; j < n.length; j++) {
                if (n[i] == n[j]) return false;
            }
        }

        return true;
    }

    public List<List<Node>> coveredDdps(Node from, Node to, Graph graph) {
        if (!graph.isAdjacentTo(from, to)) throw new IllegalArgumentException();

        List<List<Node>> paths = new ArrayList<>();

        List<Node> path = new ArrayList<>();
        path.add(from);

        for (Node b : graph.getAdjacentNodes(from)) {
            findDdpColliderPaths(b, to, path, graph, paths);
        }

        return paths;
    }

    public void findDdpColliderPaths(Node b, Node to, List<Node> path, Graph graph, List<List<Node>> paths) {
        if (path.contains(b)) {
            return;
        }

        boolean ok = true;

        for (int i = 1; i < path.size(); i++) {
            Node d = path.get(i);
            Edge e2 = graph.getEdge(d, to);
            if (!Edges.partiallyOrientedEdge(d, to).equals(e2)) {
                ok = false;
            }
        }

        for (int i = 0; i < path.size() - 2; i++) {
            if (!graph.isDefCollider(path.get(i), path.get(i + 1), path.get(i + 2))) ok = false;
        }

        if (ok) {
            path.add(b);

//        if (!ok) {
//            path.remove(b);
//            return;
//        }

//            System.out.println("path ok = " + GraphUtils.pathString(graph, path));


            if (b == to && path.size() >= 4) {
//            if (ok) {
                paths.add(new ArrayList<>(path));
//            }
            }

//        boolean ok = true;

//        for (int i = 0; i < path.size() - 3; i++) {
//            if (!graph.isDefCollider(path.get(i), path.get(i + 1), path.get(i + 2))) ok = false;
//        }

//        if (ok) {
            for (Node c : graph.getAdjacentNodes(b)) {
                findDdpColliderPaths(c, to, path, graph, paths);
            }
//        }

            path.remove(b);
        }
    }

    private Graph swapRemove(Graph graph, Set<Edge> removed) {
        graph = new EdgeListGraph(graph);

        for (Edge edge : removed) {
            graph.removeEdge(edge.getNode1(), edge.getNode2());
            out.println("Removing : " + edge);
        }

        return graph;
    }

    private Graph swapOrientColliders(Graph graph, Set<Triple> colliders) {
        graph = new EdgeListGraph(graph);

        //                                                    graph.setEndpoint(x, y2, Endpoint.ARROW);
//                                                    graph.setEndpoint(w, y2, Endpoint.ARROW);
//                                                    out.println("Orienting collider " + GraphUtils.pathString(graph, x, y2, w));


        for (Triple triple : colliders) {
            Node x = triple.getX();
            Node y2 = triple.getY();
            Node w = triple.getZ();
            if (graph.isAdjacentTo(x, y2) && graph.isAdjacentTo(y2, w)) {
                graph.setEndpoint(x, y2, Endpoint.ARROW);
                graph.setEndpoint(w, y2, Endpoint.ARROW);
                out.println("Orienting collider " + GraphUtils.pathString(graph, x, y2, w));
            }
//            graph.removeEdge(edge.getNode1(), edge.getNode2());
//            out.println("Removing : " + edge);
        }

        return graph;
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

    public void setDoDiscriminatingPathColliderRule(boolean doDiscriminatingPathColliderRule) {
        this.doDiscriminatingPathColliderRule = doDiscriminatingPathColliderRule;
    }

    public void setDoDiscriminatingPathTailRule(boolean doDiscriminatingPathTailRule) {
        this.doDiscriminatingPathTailRule = doDiscriminatingPathTailRule;
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
