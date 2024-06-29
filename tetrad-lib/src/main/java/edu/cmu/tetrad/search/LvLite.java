///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //i
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
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.utils.FciOrient;
import edu.cmu.tetrad.search.utils.TeyssierScorer;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.*;

/**
 * The LV-Lite algorithm implements the IGraphSearch interface and represents a search algorithm for learning the
 * structure of a graphical model from observational data.
 * <p>
 * This class provides methods for running the search algorithm and getting the learned pattern as a PAG (Partially
 * Annotated Graph).
 *
 * @author josephramsey
 */
public final class LvLite implements IGraphSearch {

    /**
     * The independence test.
     */
    private final IndependenceTest test;
    /**
     * The score.
     */
    private final Score score;
    /**
     * The background knowledge.
     */
    private Knowledge knowledge = new Knowledge();
    /**
     * Flag for the complete rule set, true if one should use the complete rule set, false otherwise.
     */
    private boolean completeRuleSetUsed = true;
    /**
     * The number of starts for GRaSP.
     */
    private int numStarts = 1;
    /**
     * Flag indicating whether to use data order.
     */
    private boolean useDataOrder = true;
    /**
     * This flag represents whether the Bes algorithm should be used in the search.
     * <p>
     * If set to true, the Bes algorithm will be used. If set to false, the Bes algorithm will not be used.
     * <p>
     * By default, the value of this flag is false.
     */
    private boolean useBes = false;
    /**
     * This variable represents whether the discriminating path rule is used in the LV-Lite class.
     * <p>
     * The discriminating path rule is a rule used in the search algorithm. It determines whether the algorithm
     * considers discriminating paths when searching for patterns in the data.
     * <p>
     * By default, the value of this variable is set to true, indicating that the discriminating path rule is used.
     */
    private boolean doDiscriminatingPathTailRule = true;
    /**
     * Indicates whether the discriminating path collider rule is turned on or off.
     * <p>
     * If set to true, the discriminating path collider rule is enabled. If set to false, the discriminating path
     * collider rule is disabled.
     */
    private boolean doDiscriminatingPathColliderRule = true;
    /**
     * True iff verbose output should be printed.
     */
    private boolean verbose;
    /**
     * The maximum length of a discriminating path.
     */
    private int maxPathLength;
    /**
     * The threshold for equality, a fraction of abs(BIC).
     */
    private double allowableScoreDrop = 5;
    /**
     * The algorithm to use to obtain the initial CPDAG.
     */
    private START_WITH startWith = START_WITH.BOSS;
    /**
     * The depth of the GRaSP if it is used.
     */
    private int depth = 25;
    /**
     * Flag indicating whether to repair a faulty PAG.
     */
    private boolean repairFaultyPag = false;

    /**
     * LV-Lite constructor. Initializes a new object of LvLite search algorithm with the given IndependenceTest and
     * Score object.
     *
     * @param score The Score object to be used for scoring DAGs.
     * @throws NullPointerException if score is null.
     */
    public LvLite(IndependenceTest test, Score score) {
        if (test == null) {
            throw new NullPointerException();
        }

        if (score == null) {
            throw new NullPointerException();
        }

        this.test = test;
        this.score = score;
    }

    /**
     * Orients and removes edges in a graph according to specified rules. Edges are removed in the course of the
     * algorithm, and the graph is modified in place. The call to this method may be repeated to account for the
     * possibility that the removal of an edge may allow for further removals or orientations.
     *
     * @param pag                 The original graph.
     * @param fciOrient           The orientation rules to be applied.
     * @param best                The list of best nodes.
     * @param scorer              The scorer used to evaluate edge orientations.
     * @param unshieldedColliders The set of unshielded colliders.
     * @param knowledge           The knowledge object.
     * @param maxScoreDrop        The threshold for equality. (This is not used for Oracle scoring.)
     * @param verbose             A boolean value indicating whether verbose output should be printed.
     */
    public static void processTriples(Graph pag, FciOrient fciOrient, List<Node> best, double best_score, TeyssierScorer scorer, Set<Triple> unshieldedColliders, Knowledge knowledge, boolean verbose, double maxScoreDrop) {
        reorientWithCircles(pag, verbose);
        recallUnshieldedTriples(pag, unshieldedColliders, verbose);

        doRequiredOrientations(fciOrient, pag, best, knowledge, verbose);

        Set<NodePair> toRemove = new HashSet<>();

        for (Node b : best) {
            var adj = pag.getAdjacentNodes(b);

            for (int i = 0; i < adj.size(); i++) {
                for (int j = 0; j < adj.size(); j++) {
                    if (i == j) continue;

                    var x = adj.get(i);
                    var y = adj.get(j);

                    scorer.goToBookmark();

                    if (!copyCollider(x, b, y, pag, false, scorer, best_score, best_score, maxScoreDrop, unshieldedColliders, toRemove, knowledge, verbose)) {
                        if (scorer.triangle(x, b, y)) {
                            scorer.tuck(y, b);
                            scorer.tuck(x, y);
                            double newScore = scorer.score();

                            copyCollider(x, b, y, pag, true, scorer, newScore, best_score, maxScoreDrop, unshieldedColliders, toRemove, knowledge, verbose);
                        }
                    }
                }
            }
        }

        removeEdges(pag, toRemove, verbose);
        reorientWithCircles(pag, verbose);
        recallUnshieldedTriples(pag, unshieldedColliders, verbose);
        fciOrient.zhangFinalOrientation(pag);
    }

    /**
     * Reorients all edges in a Graph as o-o. This method is used to apply the o-o orientation to all edges in the given
     * Graph following the PAG (Partially Ancestral Graph) structure.
     *
     * @param pag The Graph to be reoriented.
     */
    public static void reorientWithCircles(Graph pag, boolean verbose) {
        if (verbose) {
            TetradLogger.getInstance().log("Orient all edges in PAG as o-o:");
        }
        pag.reorientAllWith(Endpoint.CIRCLE);
    }

    private static void removeEdges(Graph pag, Set<NodePair> toRemove, boolean verbose) {
        for (NodePair remove : toRemove) {
            Node x = remove.getFirst();
            Node y = remove.getSecond();

            boolean _adj = pag.isAdjacentTo(x, y);

            if (pag.removeEdge(x, y)) {
                if (verbose && _adj && !pag.isAdjacentTo(x, y)) {
                    TetradLogger.getInstance().log("AFTER TUCKING Removed adjacency " + x + " *-* " + y + " in the PAG.");
                }
            }
        }
    }

    public static void recallUnshieldedTriples(Graph pag, Set<Triple> unshieldedColliders, boolean verbose) {
        for (Triple triple : unshieldedColliders) {
            Node x = triple.getX();
            Node b = triple.getY();
            Node y = triple.getZ();

            if (triple(pag, x, b, y)) {
                pag.setEndpoint(x, b, Endpoint.ARROW);
                pag.setEndpoint(y, b, Endpoint.ARROW);

                if (verbose) {
                    TetradLogger.getInstance().log("Recalled " + x + " *-> " + b + " <-* " + y + " from previous PAG.");
                }
            }
        }
    }

    private static boolean copyCollider(Node x, Node b, Node y, Graph pag, boolean tucked, TeyssierScorer scorer, double newScore, double bestScore, double maxScoreDrop, Set<Triple> unshieldedColliders, Set<NodePair> toRemove, Knowledge knowledge, boolean verbose) {
        if (scorer.unshieldedCollider(x, b, y)) {
            if (newScore >= bestScore - maxScoreDrop) {
                if (colliderAllowed(pag, x, b, y, knowledge)) {
                    boolean oriented = !pag.isDefCollider(x, b, y);

                    pag.setEndpoint(x, b, Endpoint.ARROW);
                    pag.setEndpoint(y, b, Endpoint.ARROW);

                    toRemove.add(new NodePair(x, y));
                    unshieldedColliders.add(new Triple(x, b, y));

                    if (verbose) {
                        if (tucked) {
                            TetradLogger.getInstance().log("AFTER TUCKING copied " + x + " *-> " + b + " <-* " + y + " from CPDAG to PAG.");
                        } else {
                            TetradLogger.getInstance().log("Copied " + x + " *-> " + b + " <-* " + y + " from CPDAG to PAG.");
                        }
                    }

                    return oriented;
                }
            }
        }

        return false;
    }

    /**
     * Checks if three nodes are connected in a graph.
     *
     * @param graph the graph to check for connectivity
     * @param a     the first node
     * @param b     the second node
     * @param c     the third node
     * @return {@code true} if all three nodes are connected, {@code false} otherwise
     */
    private static boolean triple(Graph graph, Node a, Node b, Node c) {
        return a != b && b != c && a != c && graph.isAdjacentTo(a, b) && graph.isAdjacentTo(b, c);
    }

    /**
     * Determines if the collider is allowed.
     *
     * @param pag The Graph representing the PAG.
     * @param x   The Node object representing the first node.
     * @param b   The Node object representing the second node.
     * @param y   The Node object representing the third node.
     * @return true if the collider is allowed, false otherwise.
     */
    private static boolean colliderAllowed(Graph pag, Node x, Node b, Node y, Knowledge knowledge) {
        return FciOrient.isArrowheadAllowed(x, b, pag, knowledge) && FciOrient.isArrowheadAllowed(y, b, pag, knowledge);
    }

    /**
     * Orient required edges in PAG.
     *
     * @param fciOrient The FciOrient object used for orienting the edges.
     * @param pag       The Graph representing the PAG.
     * @param best      The list of Node objects representing the best nodes.
     */
    private static void doRequiredOrientations(FciOrient fciOrient, Graph pag, List<Node> best, Knowledge knowledge, boolean verbose) {
        if (verbose) {
            TetradLogger.getInstance().log("Orient required edges in PAG:");
        }

        fciOrient.fciOrientbk(knowledge, pag, best);
    }

    public static void removeExtraEdges(Graph pag, IndependenceTest test, int maxPathLength, boolean verbose) {
        if (verbose) {
            TetradLogger.getInstance().log("Checking larger conditioning sets:");
        }

        List<Edge> toRemove = new ArrayList<>();

        // Embarrasingly parallel.
        pag.getEdges().parallelStream().forEach(edge -> {
            tryRemovingEdge(edge, pag, test, toRemove, maxPathLength, verbose);
        });

        if (verbose) {
            TetradLogger.getInstance().log("Done listing larger conditioning sets.");
        }

        for (Edge edge : toRemove) {
            pag.removeEdge(edge.getNode1(), edge.getNode2());
        }

        if (verbose) {
            TetradLogger.getInstance().log("Removed edges: " + toRemove);
        }
    }

    private static void tryRemovingEdge(Edge edge, Graph pag, IndependenceTest test, List<Edge> toRemove, int maxPathLength,
                                        boolean verbose) {
        Node x = edge.getNode1();
        Node y = edge.getNode2();
        Set<Node> conditioningSet = new HashSet<>();
        List<List<Node>> paths;

        W:
        while (true) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

//            for (int length = 3; length <= 5; length++) {
            paths = pag.paths().allPaths(x, y, maxPathLength, conditioningSet, true);

            // Sort paths by length.
            paths.sort(Comparator.comparingInt(List::size));

            for (List<Node> path : paths) {
                for (int i = 1; i < path.size() - 1; i++) {
                    Node z1 = path.get(i - 1);
                    Node z2 = path.get(i);
                    Node z3 = path.get(i + 1);

                    if (!pag.isDefCollider(z1, z2, z3) && !pag.isAdjacentTo(z1, z3)) {
                        conditioningSet.add(z2);
                        if (path.size() - 1 > 2) {
                            continue W;
                        }
                    }
                }
//                }
            }

            break;
        }

        if (verbose) {
            TetradLogger.getInstance().log("Checking independence of " + x + " *-* " + y + " given " + conditioningSet);
        }

        if (test.checkIndependence(x, y, conditioningSet).isIndependent()) {
            toRemove.add(edge);
        }
    }

    /**
     * Run the search and return s a PAG.
     *
     * @return The PAG.
     */
    public Graph search() {
        List<Node> nodes = new ArrayList<>(this.score.getVariables());

        if (verbose) {
            TetradLogger.getInstance().log("===Starting LV-Lite===");
        }

        List<Node> best;

        // BOSS seems to be doing better here.
        if (startWith == START_WITH.BOSS) {
            var suborderSearch = new Boss(score);
            suborderSearch.setResetAfterBM(true);
            suborderSearch.setResetAfterRS(true);
            suborderSearch.setVerbose(false);
            suborderSearch.setUseBes(useBes);
            suborderSearch.setUseDataOrder(useDataOrder);
            suborderSearch.setNumStarts(numStarts);
            var permutationSearch = new PermutationSearch(suborderSearch);
            permutationSearch.setKnowledge(knowledge);
            permutationSearch.search();
            best = permutationSearch.getOrder();

            if (verbose) {
                TetradLogger.getInstance().log("Initializing PAG to BOSS CPDAG.");
                TetradLogger.getInstance().log("Initializing scorer with BOSS best order.");
            }
        } else if (startWith == START_WITH.GRASP) {
            edu.cmu.tetrad.search.Grasp grasp = new edu.cmu.tetrad.search.Grasp(test, score);

            grasp.setSeed(-1);
            grasp.setDepth(depth);
            grasp.setUncoveredDepth(1);
            grasp.setNonSingularDepth(1);
            grasp.setOrdered(true);
            grasp.setUseScore(true);
            grasp.setUseRaskuttiUhler(false);
            grasp.setUseDataOrder(useDataOrder);
            grasp.setAllowInternalRandomness(true);
            grasp.setVerbose(false);

            grasp.setNumStarts(numStarts);
            grasp.setKnowledge(this.knowledge);
            best = grasp.bestOrder(nodes);
            grasp.getGraph(true);

            if (verbose) {
                TetradLogger.getInstance().log("Initializing PAG to GRaSP CPDAG.");
                TetradLogger.getInstance().log("Initializing scorer with GRaSP best order.");
            }
        } else {
            throw new IllegalArgumentException("Unknown startWith algorithm: " + startWith);
        }

        if (verbose) {
            TetradLogger.getInstance().log("Best order: " + best);
        }


        var scorer = new TeyssierScorer(test, score);

        scorer.setUseScore(true);
        scorer.setKnowledge(knowledge);
        double best_score = scorer.score(best);
        scorer.bookmark();
        Graph pag = new EdgeListGraph(scorer.getGraph(true));

        if (verbose) {
            TetradLogger.getInstance().log("Initializing PAG to BOSS CPDAG.");
            TetradLogger.getInstance().log("Initializing scorer with BOSS best order.");
        }


        scorer.score(best);

        FciOrient fciOrient = new FciOrient(scorer);
        fciOrient.setCompleteRuleSetUsed(completeRuleSetUsed);
        fciOrient.setDoDiscriminatingPathColliderRule(doDiscriminatingPathColliderRule);
        fciOrient.setDoDiscriminatingPathTailRule(doDiscriminatingPathTailRule);
        fciOrient.setMaxPathLength(-1);
        fciOrient.setKnowledge(knowledge);
        fciOrient.setVerbose(verbose);

        if (verbose) {
            TetradLogger.getInstance().log("Collider orientation and edge removal.");
        }

        // The main procedure.
        Set<Triple> unshieldedColliders = new HashSet<>();
        Set<Triple> _unshieldedColliders;

        do {
            _unshieldedColliders = new HashSet<>(unshieldedColliders);
            processTriples(pag, fciOrient, best, best_score, scorer, unshieldedColliders, knowledge, verbose, this.allowableScoreDrop);
        } while (!unshieldedColliders.equals(_unshieldedColliders));

        fciOrient.zhangFinalOrientation(pag);

        if (repairFaultyPag) {
            GraphUtils.repairFaultyPag(pag, fciOrient, verbose);
        }

        removeExtraEdges(pag, test, maxPathLength, verbose);
        reorientWithCircles(pag, verbose);
        recallUnshieldedTriples(pag, unshieldedColliders, verbose);
        fciOrient.zhangFinalOrientation(pag);

        if (repairFaultyPag) {
            GraphUtils.repairFaultyPag(pag, fciOrient, verbose);
        }

        return GraphUtils.replaceNodes(pag, this.score.getVariables());
    }

    /**
     * Sets the algorithm to use to obtain the initial CPDAG.
     *
     * @param startWith the algorithm to use to obtain the initial CPDAG.
     */
    public void setStartWith(START_WITH startWith) {
        this.startWith = startWith;
    }

    /**
     * Sets the knowledge used in search.
     *
     * @param knowledge This knowledge.
     */
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }

    /**
     * Sets whether the complete rule set should be used during the search algorithm. By default, the complete rule set
     * is not used.
     *
     * @param completeRuleSetUsed true if the complete rule set should be used, false otherwise
     */
    public void setCompleteRuleSetUsed(boolean completeRuleSetUsed) {
        this.completeRuleSetUsed = completeRuleSetUsed;
    }

    /**
     * Sets the verbosity level of the search algorithm.
     *
     * @param verbose true to enable verbose mode, false to disable it
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Sets the number of starts for BOSS.
     *
     * @param numStarts The number of starts.
     */
    public void setNumStarts(int numStarts) {
        this.numStarts = numStarts;
    }

    /**
     * Sets whether the discriminating path tail rule should be used.
     *
     * @param doDiscriminatingPathTailRule True, if so.
     */
    public void setDoDiscriminatingPathTailRule(boolean doDiscriminatingPathTailRule) {
        this.doDiscriminatingPathTailRule = doDiscriminatingPathTailRule;
    }

    /**
     * Sets whether the discriminating path collider rule should be used.
     *
     * @param doDiscriminatingPathColliderRule True, if so.
     */
    public void setDoDiscriminatingPathColliderRule(boolean doDiscriminatingPathColliderRule) {
        this.doDiscriminatingPathColliderRule = doDiscriminatingPathColliderRule;
    }

    /**
     * Sets whether to use the BES (Backward Elimination Search) algorithm during the search.
     *
     * @param useBes true to use the BES algorithm, false otherwise
     */
    public void setUseBes(boolean useBes) {
        this.useBes = useBes;
    }

    /**
     * Sets the flag indicating whether to use data order.
     *
     * @param useDataOrder {@code true} if the data order should be used, {@code false} otherwise.
     */
    public void setUseDataOrder(boolean useDataOrder) {
        this.useDataOrder = useDataOrder;
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
     * Sets the allowable score drop used in the process triples step. A higher bound may orient more colliders.
     *
     * @param allowableScoreDrop the new equality threshold value
     */
    public void setAllowableScoreDrop(double allowableScoreDrop) {
        if (Double.isNaN(allowableScoreDrop) || Double.isInfinite(allowableScoreDrop)) {
            throw new IllegalArgumentException("Equality threshold must be a finite number: " + allowableScoreDrop);
        }

        if (allowableScoreDrop < 0) {
            throw new IllegalArgumentException("Equality threshold must be >= 0: " + allowableScoreDrop);
        }

        this.allowableScoreDrop = allowableScoreDrop;
    }

    /**
     * Sets the depth of the GRaSP if it is used.
     *
     * @param depth The depth of the GRaSP.
     */
    public void setDepth(int depth) {
        this.depth = depth;
    }

    /**
     * Sets whether to repair a faulty PAG.
     *
     * @param repairFaultyPag true if a faulty PAG should be repaired, false otherwise
     */
    public void setRepairFaultyPag(boolean repairFaultyPag) {
        this.repairFaultyPag = repairFaultyPag;
    }

    /**
     * Enumeration representing different start options.
     */
    public enum START_WITH {
        /**
         * Start with BOSS.
         */
        BOSS,
        /**
         * Start with GRaSP.
         */
        GRASP
    }
}
