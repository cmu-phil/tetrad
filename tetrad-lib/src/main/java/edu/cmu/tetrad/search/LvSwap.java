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

import static edu.cmu.tetrad.graph.GraphUtils.addForbiddenReverseEdgesForDirectedEdges;
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
        boss.setKnowledge(knowledge);
        boss.setVerbose(verbose);

        List<Node> variables = new ArrayList<>(this.score.getVariables());
        variables.removeIf(node -> node.getNodeType() == NodeType.LATENT);

        List<Node> pi = boss.bestOrder(variables);
        scorer.score(pi);
        Graph G1 = scorer.getGraph(false);

        Knowledge knowledge2 = new Knowledge(knowledge);
        addForbiddenReverseEdgesForDirectedEdges(SearchGraphUtils.cpdagForDag(G1), knowledge2);

        Graph G2 = new EdgeListGraph(G1);
        retainUnshieldedColliders(G2, knowledge2);

        Graph G3 = new EdgeListGraph(G2);

        Set<Edge> removed = new HashSet<>();

        G3 = swapOrient(G3, scorer, knowledge2, removed);
        G3 = swapRemove(G3, removed);

        // Do final FCI orientation rules app
        Graph G4 = new EdgeListGraph(G3);
        retainUnshieldedColliders(G4, knowledge2);

        List<Node> nodes = G4.getNodes();

        for (Node n1 : nodes) {
            for (Node n2 : nodes) {
                if (n1 == n2) continue;

                List<Node> ddp = ddp(n1, n2, G4);

                if (ddp != null) {
                    System.out.println("DDP from " + n1 + " to " + n2 + ": " + GraphUtils.pathString(ddp, G4));
                }
            }
        }


        finalOrientation(knowledge2, G4);

        G4.setGraphType(EdgeListGraph.GraphType.PAG);

        return G4;
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

    private Graph swapOrient(Graph graph, TeyssierScorer scorer, Knowledge knowledge, Set<Edge> removed) {
        removed.clear();

        graph = new EdgeListGraph(graph);
        List<Node> pi = scorer.getPi();

        for (Node y : pi) {
            for (Node x : graph.getAdjacentNodes(y)) {
                for (Node w : graph.getAdjacentNodes(y)) {

                    Z:
                    for (Node z : graph.getAdjacentNodes(x)) {
//                        if (!distinct(z, x, y, w)) continue;

                        // Check to make sure you have a left collider in the graph--i.e., z->x<-y
                        // with adj(w, x)
                        if (graph.isDefCollider(z, x, y)) {
                            scorer.swap(x, y);

                            // Make aure you get a right unshielded collider in the scorer--i.e. x->y<-w
                            // with ~adj(x, w)
                            if (scorer.adjacent(x, w) || scorer.adjacent(z, w) || !scorer.collider(x, y, w)
                                    || !scorer.collider(z, y, w)) {
                                scorer.swap(x, y);
                                continue;
                            }

                            // Make sure the new scorer orientations are all allowed in the graph...
                            Set<Node> adj = scorer.getAdjacentNodes(x);
                            adj.retainAll(scorer.getAdjacentNodes(w));

                            for (Node y2 : adj) {
                                if (scorer.collider(x, y2, w)) {
                                    if (!FciOrient.isArrowpointAllowed(w, y2, graph, knowledge)
                                            || !FciOrient.isArrowpointAllowed(x, y2, graph, knowledge)) {
                                        scorer.swap(x, y);
                                        continue Z;
                                    }
                                } else {
                                    if (graph.isDefCollider(x, y2, w)) {
                                        scorer.swap(x, y);
                                        continue Z;
                                    }
                                }
                            }

                            // If OK, mark w*-*x for removal and do any new collider orientations in the graph...
                            Edge edge = graph.getEdge(w, x);

                            if (edge != null && !removed.contains(edge)) {
                                out.println("Marking " + edge + " for removal (swapping " + x + " and " + y + ")");
                                removed.add(edge);
                            }

                            for (Node y2 : adj) {
                                if (scorer.collider(x, y2, w)) {
                                    if (!graph.isDefCollider(x, y2, w)) {
                                        graph.setEndpoint(x, y2, Endpoint.ARROW);
                                        graph.setEndpoint(w, y2, Endpoint.ARROW);
                                        out.println("Orienting collider " + GraphUtils.pathString(graph, x, y2, w));
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

    public List<Node> ddp(Node from, Node to, Graph graph) {
        if (!graph.isAdjacentTo(from, to)) return null;

        List<Node> path = new ArrayList<>();
        path.add(from);

        for (Node b : graph.getAdjacentNodes(from)) {
            if (findDdpColliderPath(b, to, path, graph)) {
                return path;
            }
        }

        return null;
    }

    public boolean findDdpColliderPath(Node b, Node to, List<Node> path, Graph graph) {
        if (path.contains(b)) return false;
        path.add(b);
        if (path.size() >= 3 && b == to) return true;

        Node a = path.get(path.size() - 2);

        for (Node c : graph.getAdjacentNodes(b)) {
            if (!graph.isDefCollider(a, b, c)) continue;

//            if (c != to) {
            Edge e = graph.getEdge(b, to);
            if (e == null) {
                path.remove(b);
                return false;
            }
//                if (e.getProximalEndpoint(to) != Endpoint.ARROW) continue;
//            if (e.getProximalEndpoint(b) == Endpoint.ARROW) continue;
            System.out.println("e = " + e + " to = " + to);
//            }


            boolean found = findDdpColliderPath(c, to, path, graph);

            if (found) {
                return true;
            }
        }

        path.remove(b);
        return false;
    }

    private Graph swapRemove(Graph graph, Set<Edge> removed) {
        graph = new EdgeListGraph(graph);

        for (Edge edge : removed) {
            graph.removeEdge(edge);
            out.println("Removing : " + edge);
        }

        return graph;
    }

    private static boolean leftCollider(TeyssierScorer scorer, Node z, Node x, Node y, Node w) {
        if ((z == null || scorer.adjacent(z, x)) && scorer.adjacent(x, y) && scorer.adjacent(y, w)) {
            if (scorer.adjacent(w, x)) {
                return (z == null || scorer.collider(z, x, y));
            }
        }

        return false;
    }

    private static boolean leftCollider(Graph graph, Node z, Node x, Node y, Node w) {
        if (graph.isAdjacentTo(z, x) && graph.isAdjacentTo(x, y) && graph.isAdjacentTo(y, w)
                && graph.isAdjacentTo(w, x)) {
            return graph.isDefCollider(z, x, y);
        }

        return false;
    }

    private static boolean triangle(Graph graph, Node x, Node y, Node w) {
        return graph.isAdjacentTo(x, y) && graph.isAdjacentTo(y, w) && graph.isAdjacentTo(w, x);
    }

    private static boolean unshieldedCollider(TeyssierScorer scorer, Node x, Node y, Node w) {
        if (scorer.adjacent(x, y) && scorer.adjacent(y, w) && !scorer.adjacent(w, x)) {
            return scorer.collider(w, y, x);
        }

        return false;
    }

    private static boolean unshieldedNoncollider(TeyssierScorer scorer, Node x, Node y, Node w) {
        if (scorer.adjacent(x, y) && scorer.adjacent(y, w) && !scorer.adjacent(w, x)) {
            return !scorer.collider(w, y, x);
        }

        return false;
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
