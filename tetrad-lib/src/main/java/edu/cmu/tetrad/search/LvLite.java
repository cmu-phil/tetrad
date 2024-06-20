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
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static java.lang.Math.abs;

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
     * Represents a variable that determines whether tucks are allowed. The value of this variable determines whether
     * tucks are enabled or disabled.
     */
    private boolean allowTucks = true;
    /**
     * The maximum length of a discriminating path.
     */
    private int maxPathLength;
    /**
     * The threshold for equality, a fraction of abs(BIC).
     */
    private double equalityThreshold = 0.0005;
    /**
     * The algorithm to use to obtain the initial CPDAG.
     */
    private START_WITH startWith = START_WITH.BOSS;
    private int depth = 25;

    /**
     * LV-Lite constructor. Initializes a new object of LvLite search algorithm with the given IndependenceTest and
     * Score object.
     *
     * @param score The Score object to be used for scoring DAGs.
     * @throws NullPointerException if score is null.
     */
    public LvLite(Score score) {
        if (score == null) {
            throw new NullPointerException();
        }

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
     * @param cpdag               The CPDAG.
     * @param knowledge           The knowledge object.
     * @param allowTucks          A boolean value indicating whether tucks are allowed.
     * @param equalityThreshold   The threshold for equality. (This is not used for Oracle scoring.)
     * @param verbose             A boolean value indicating whether verbose output should be printed.
     */
    public static void orientCollidersAndRemoveEdges(Graph pag, FciOrient fciOrient, List<Node> best, TeyssierScorer scorer,
                                                     Set<Triple> unshieldedColliders, Graph cpdag, Knowledge knowledge,
                                                     boolean allowTucks, boolean verbose, double equalityThreshold) {
        reorientWithCircles(pag, verbose);
        recallUnshieldedTriples(pag, unshieldedColliders, verbose);

        doRequiredOrientations(fciOrient, pag, best, knowledge, verbose);

        var reverse = new ArrayList<>(best);
        Collections.reverse(reverse);
        Set<NodePair> toRemove = new HashSet<>();

        for (Node b : reverse) {
            var adj = pag.getAdjacentNodes(b);

            for (int i = 0; i < adj.size(); i++) {
                for (int j = 0; j < adj.size(); j++) {
                    if (i == j) continue;

                    var x = adj.get(i);
                    var y = adj.get(j);

                    if (unshieldedCollider(pag, x, b, y)) {
                        continue;
                    }

                    if (!copyColliderCpdag(pag, cpdag, x, b, y, unshieldedColliders, toRemove, knowledge, verbose)) {
                        if (allowTucks) {
                            if (!unshieldedCollider(pag, x, b, y)) {
                                scorer.goToBookmark();

                                double score1 = scorer.score();

                                scorer.tuck(b, x);
                                scorer.tuck(x, y);
//                                scorer.tuck(y, x);

                                double score2 = scorer.score();

                                if (Double.isNaN(equalityThreshold) || score2 > score1 - equalityThreshold * abs(score1)) {
                                    copyColliderScorer(x, b, y, pag, scorer, unshieldedColliders, toRemove, knowledge, verbose);
                                }
                            }
                        }
                    }
                }
            }
        }

        removeEdges(pag, toRemove, verbose);
    }

    /**
     * Determines the final orientation of the graph using the given FciOrient object, Graph object, and scorer object.
     *
     * @param fciOrient                        The FciOrient object used to determine the final orientation.
     * @param pag                              The Graph object for which the final orientation is determined.
     * @param scorer                           The scorer object used in the score-based discriminating path rule.
     * @param doDiscriminatingPathTailRule     A boolean value indicating whether the discriminating path tail rule
     *                                         should be applied. If set to true, the discriminating path tail rule will
     *                                         be applied. If set to false, the discriminating path tail rule will not
     *                                         be applied.
     * @param doDiscriminatingPathColliderRule A boolean value indicating whether the discriminating path collider rule
     *                                         should be applied. If set to true, the discriminating path collider rule
     *                                         will be applied. If set to false, the discriminating path collider rule
     *                                         will not be applied.
     * @param completeRuleSetUsed              A boolean value indicating whether the complete rule set should be used.
     * @param verbose                          A boolean value indicating whether verbose output should be printed.
     */
    public static void finalOrientation(FciOrient fciOrient, Graph pag, TeyssierScorer scorer, boolean completeRuleSetUsed,
                                        boolean doDiscriminatingPathTailRule, boolean doDiscriminatingPathColliderRule, boolean verbose) {
        if (verbose) {
            TetradLogger.getInstance().log("Final Orientation:");
        }

        fciOrient.setVerbose(verbose);

        do {
            if (completeRuleSetUsed) {
                fciOrient.zhangFinalOrientation(pag);
            } else {
                fciOrient.spirtesFinalOrientation(pag);
            }
        } while (discriminatingPathRule(pag, scorer, doDiscriminatingPathTailRule, doDiscriminatingPathColliderRule, verbose));
    }

    /**
     * Reorients all edges in a Graph as o-o. This method is used to apply the o-o orientation to all edges in the given
     * Graph following the PAG (Partially Ancestral Graph) structure.
     *
     * @param pag The Graph to be reoriented.
     */
    private static void reorientWithCircles(Graph pag, boolean verbose) {
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
                    TetradLogger.getInstance().log(
                            "TUCKING: Removed adjacency " + x + " *-* " + y + " in the PAG.");
                }
            }
        }
    }

    private static void recallUnshieldedTriples(Graph pag, Set<Triple> unshieldedColliders, boolean verbose) {
        for (Triple triple : unshieldedColliders) {
            Node x = triple.getX();
            Node b = triple.getY();
            Node y = triple.getZ();

            if (triple(pag, x, b, y)) {
                pag.setEndpoint(x, b, Endpoint.ARROW);
                pag.setEndpoint(y, b, Endpoint.ARROW);

                if (verbose) {
                    TetradLogger.getInstance().log(
                            "Recalled " + x + " *-> " + b + " <-* " + y + " from previous PAG.");
                }
            }
        }
    }

    private static boolean copyColliderCpdag(Graph pag, Graph cpdag, Node x, Node b, Node y, Set<Triple> unshieldedColliders,
                                             Set<NodePair> toRemove, Knowledge knowledge, boolean verbose) {
        if (unshieldedTriple(pag, x, b, y) && unshieldedCollider(cpdag, x, b, y)) {
            if (colliderAllowed(pag, x, b, y, knowledge)) {
                boolean oriented = !pag.isDefCollider(x, b, y);

                pag.setEndpoint(x, b, Endpoint.ARROW);
                pag.setEndpoint(y, b, Endpoint.ARROW);

                toRemove.add(new NodePair(x, y));
                unshieldedColliders.add(new Triple(x, b, y));

                if (verbose) {
                    TetradLogger.getInstance().log(
                            "Copied " + x + " *-> " + b + " <-* " + y + " from CPDAG to PAG.");
                }

                return oriented;
            }
        }

        return false;
    }

    private static boolean copyColliderScorer(Node x, Node b, Node y, Graph pag, TeyssierScorer scorer, Set<Triple> unshieldedColliders,
                                              Set<NodePair> toRemove, Knowledge knowledge, boolean verbose) {
        if (triple(pag, x, b, y) && scorer.unshieldedCollider(x, b, y)) {
            if (colliderAllowed(pag, x, b, y, knowledge)) {
                boolean oriented = false;

                if (!pag.isDefCollider(x, b, y)) {
                    oriented = true;
                }

                pag.setEndpoint(x, b, Endpoint.ARROW);
                pag.setEndpoint(y, b, Endpoint.ARROW);

                toRemove.add(new NodePair(x, y));
                unshieldedColliders.add(new Triple(x, b, y));

                if (verbose) {
                    TetradLogger.getInstance().log(
                            "FROM TUCKING oriented " + x + " *-> " + b + " <-* " + y + " from CPDAG to PAG.");
                }

                return oriented;
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
        return a != b && b != c && a != c
               && graph.isAdjacentTo(a, b) && graph.isAdjacentTo(b, c);
    }

    private static boolean triangle(Graph graph, Node a, Node b, Node c) {
        return a != b && b != c && a != c
               && graph.isAdjacentTo(a, b) && graph.isAdjacentTo(b, c) && graph.isAdjacentTo(a, c);
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
        return FciOrient.isArrowheadAllowed(x, b, pag, knowledge)
               && FciOrient.isArrowheadAllowed(y, b, pag, knowledge);
    }

    /**
     * Orient required edges in PAG.
     *
     * @param fciOrient The FciOrient object used for orienting the edges.
     * @param pag       The Graph representing the PAG.
     * @param best      The list of Node objects representing the best nodes.
     */
    private static void doRequiredOrientations(FciOrient fciOrient, Graph pag, List<Node> best, Knowledge knowledge,
                                               boolean verbose) {
        if (verbose) {
            TetradLogger.getInstance().log("Orient required edges in PAG:");
        }

        fciOrient.fciOrientbk(knowledge, pag, best);
    }

    /**
     * Checks if three nodes in a graph form an unshielded triple. An unshielded triple is a configuration where node a
     * is adjacent to node b, node b is adjacent to node c, but node a is not adjacent to node c.
     *
     * @param graph The graph in which the nodes reside.
     * @param a     The first node in the triple.
     * @param b     The second node in the triple.
     * @param c     The third node in the triple.
     * @return {@code true} if the nodes form an unshielded triple, {@code false} otherwise.
     */
    private static boolean unshieldedTriple(Graph graph, Node a, Node b, Node c) {
        return a != b && b != c && a != c
               && graph.isAdjacentTo(a, b) && graph.isAdjacentTo(b, c) && !graph.isAdjacentTo(a, c);
    }

    /**
     * Checks if the given nodes are unshielded colliders when considering the given graph.
     *
     * @param graph the graph to consider
     * @param a     the first node
     * @param b     the second node
     * @param c     the third node
     * @return true if the nodes are unshielded colliders, false otherwise
     */
    private static boolean unshieldedCollider(Graph graph, Node a, Node b, Node c) {
        return a != b && b != c && a != c
               && unshieldedTriple(graph, a, b, c) && graph.isDefCollider(a, b, c);
    }

    private static @NotNull List<Node> commonAdjacents(Node x, Node y, Graph pag) {
        List<Node> commonAdjacents = new ArrayList<>(pag.getAdjacentNodes(x));
        commonAdjacents.retainAll(pag.getAdjacentNodes(y));
        return commonAdjacents;
    }

    /**
     * This is a score-based discriminating path rule.
     * <p>
     * The triangles that must be oriented this way (won't be done by another rule) all look like the ones below, where
     * the dots are a collider path from E to A with each node on the path (except E) a parent of C.
     * <pre>
     *          B
     *         xo           x is either an arrowhead or a circle
     *        /  \
     *       v    v
     * E....A --> C
     * </pre>
     * <p>
     * This is Zhang's rule R4, discriminating paths.
     *
     * @param graph a {@link Graph} object
     */
    private static boolean discriminatingPathRule(Graph graph, TeyssierScorer scorer,
                                                  boolean doDiscriminatingPathTailRule,
                                                  boolean doDiscriminatingPathColliderRule,
                                                  boolean verbose) {
        List<Node> nodes = graph.getNodes();
        boolean oriented = false;

        for (Node b : nodes) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            // potential A and C candidate pairs are only those
            // that look like this:   A<-*Bo-*C
            List<Node> possA = graph.getNodesOutTo(b, Endpoint.ARROW);
            List<Node> possC = graph.getNodesInTo(b, Endpoint.CIRCLE);

            for (Node a : possA) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                for (Node c : possC) {
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }

                    if (a == c) continue;

                    if (!graph.isParentOf(a, c)) {
                        continue;
                    }

                    if (graph.getEndpoint(b, c) != Endpoint.ARROW) {
                        continue;
                    }

                    boolean _oriented = ddpOrient(a, b, c, graph, scorer, doDiscriminatingPathTailRule,
                            doDiscriminatingPathColliderRule, verbose);

                    if (_oriented) oriented = true;
                }
            }
        }

        return oriented;
    }

    /**
     * A method to search "back from a" to find a DDP. It is called with a reachability list (first consisting only of
     * a). This is breadth-first, using "reachability" concept from Geiger, Verma, and Pearl 1990. The body of a DDP
     * consists of colliders that are parents of c.
     *
     * @param a     a {@link Node} object
     * @param b     a {@link Node} object
     * @param c     a {@link Node} object
     * @param graph a {@link Graph} object
     */
    private static boolean ddpOrient(Node a, Node b, Node c, Graph graph, TeyssierScorer scorer,
                                     boolean doDiscriminatingPathTailRule, boolean doDiscriminatingPathColliderRule,
                                     boolean verbose) {
        Queue<Node> Q = new ArrayDeque<>(20);
        Set<Node> V = new HashSet<>();

        Node e = null;

        Map<Node, Node> previous = new HashMap<>();
        List<Node> path = new ArrayList<>();

        List<Node> cParents = graph.getParents(c);

        Q.offer(a);
        V.add(a);
        V.add(b);
        previous.put(a, b);

        while (!Q.isEmpty()) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            Node t = Q.poll();

            if (e == null || e == t) {
                e = t;
            }

            List<Node> nodesInTo = graph.getNodesInTo(t, Endpoint.ARROW);

            for (Node d : nodesInTo) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                if (V.contains(d)) {
                    continue;
                }

                Node p = previous.get(t);

                if (!graph.isDefCollider(d, t, p)) {
                    continue;
                }

                previous.put(d, t);

                if (!path.contains(t)) {
                    path.add(t);
                }

                if (!graph.isAdjacentTo(d, c)) {
                    if (doDdpOrientation(d, a, b, c, path, graph, scorer,
                            doDiscriminatingPathTailRule, doDiscriminatingPathColliderRule, verbose)) {
                        return true;
                    }
                }

                if (cParents.contains(d)) {
                    Q.offer(d);
                    V.add(d);
                }
            }
        }

        return false;
    }

    /**
     * Determines the orientation for the nodes in a Directed Acyclic Graph (DAG) based on the Discriminating Path Rule
     * Here, we insist that the sepset for D and B contain all the nodes along the collider path.
     * <p>
     * Reminder:
     * <pre>
     *      The triangles that must be oriented this way (won't be done by another rule) all look like the ones below, where
     *      the dots are a collider path from E to A with each node on the path (except E) a parent of C.
     *      <pre>
     *               B
     *              xo           x is either an arrowhead or a circle
     *             /  \
     *            v    v
     *      E....A --> C
     *
     *      This is Zhang's rule R4, discriminating paths. The "collider path" here is all of the collider nodes
     *      along the E...A path (all parents of C), including A. The idea is that is we know that E is independent
     *      of C given all of nodes on the collider path plus perhaps some other nodes, then there should be a collider
     *      at B; otherwise, there should be a noncollider at B.
     * </pre>
     *
     * @param e     the 'e' node
     * @param a     the 'a' node
     * @param b     the 'b' node
     * @param c     the 'c' node
     * @param graph the graph representation
     * @return true if the orientation is determined, false otherwise
     * @throws IllegalArgumentException if 'e' is adjacent to 'c'
     */
    private static boolean doDdpOrientation(Node e, Node a, Node b, Node c, List<Node> path, Graph graph,
                                            TeyssierScorer scorer, boolean doDiscriminatingPathTailRule,
                                            boolean doDiscriminatingPathColliderRule, boolean verbose) {

        if (graph.getEndpoint(b, c) != Endpoint.ARROW) {
            return false;
        }

        if (graph.getEndpoint(c, b) != Endpoint.CIRCLE) {
            return false;
        }

        if (graph.getEndpoint(a, c) != Endpoint.ARROW) {
            return false;
        }

        if (graph.getEndpoint(b, a) != Endpoint.ARROW) {
            return false;
        }

        if (graph.getEndpoint(c, a) != Endpoint.TAIL) {
            return false;
        }

        if (!path.contains(a)) {
            throw new IllegalArgumentException("Path does not contain a");
        }

        for (Node n : path) {
            if (!graph.isParentOf(n, c)) {
                throw new IllegalArgumentException("Node " + n + " is not a parent of " + c);
            }
        }

        scorer.goToBookmark();
        scorer.tuck(b, c);
            scorer.tuck(b, e);
//        scorer.tuck(c, e);

//        scorer.goToBookmark();
//
//        for (Node n : path) {
//            scorer.tuck(e, n);
//        }
//
//        scorer.tuck(b, c);

        boolean collider = !scorer.adjacent(e, c);

        if (collider) {
            if (doDiscriminatingPathColliderRule) {
                graph.setEndpoint(a, b, Endpoint.ARROW);
                graph.setEndpoint(c, b, Endpoint.ARROW);

                if (verbose) {
                    TetradLogger.getInstance().log(
                            "R4: Definite discriminating path collider rule e = " + e + " " + GraphUtils.pathString(graph, a, b, c));
                }

                return true;
            }
        } else {
            if (doDiscriminatingPathTailRule) {
                graph.setEndpoint(c, b, Endpoint.TAIL);

                if (verbose) {
                    TetradLogger.getInstance().log(
                            "R4: Definite discriminating path tail rule e = " + e + " " + GraphUtils.pathString(graph, a, b, c));
                }

                return true;
            }
        }

        return false;
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

        Graph cpdag;
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
            cpdag = permutationSearch.search();
            best = permutationSearch.getOrder();

            if (verbose) {
                TetradLogger.getInstance().log("Initializing PAG to BOSS CPDAG.");
                TetradLogger.getInstance().log("Initializing scorer with BOSS best order.");
            }
        } else if (startWith == START_WITH.GRASP) {
            edu.cmu.tetrad.search.Grasp grasp = new edu.cmu.tetrad.search.Grasp(null, score);

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
            cpdag = grasp.getGraph(true);

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

        var pag = new EdgeListGraph(cpdag);

        var scorer = new TeyssierScorer(null, score);
        scorer.setUseScore(true);
        scorer.score(best);
        scorer.bookmark();

        if (verbose) {
            TetradLogger.getInstance().log("Initializing PAG to BOSS CPDAG.");
            TetradLogger.getInstance().log("Initializing scorer with BOSS best order.");
        }


        scorer.score(best);

        FciOrient fciOrient = new FciOrient(null);
        fciOrient.setCompleteRuleSetUsed(completeRuleSetUsed);
        fciOrient.setDoDiscriminatingPathColliderRule(false);
        fciOrient.setDoDiscriminatingPathTailRule(false);
        fciOrient.setMaxPathLength(maxPathLength);
        fciOrient.setKnowledge(knowledge);
        fciOrient.setVerbose(verbose);

        if (verbose) {
            TetradLogger.getInstance().log("Collider orientation and edge removal.");
        }

        // The main procedure.
        Set<Triple> unshieldedColliders = new HashSet<>();
        Set<Triple> _unshieldedColliders;
        double equalityThreshold = this.equalityThreshold;

        do {
            _unshieldedColliders = new HashSet<>(unshieldedColliders);
            LvLite.orientCollidersAndRemoveEdges(pag, fciOrient, best, scorer, unshieldedColliders, cpdag, knowledge,
                    allowTucks, verbose, equalityThreshold);
        } while (!unshieldedColliders.equals(_unshieldedColliders));

        LvLite.finalOrientation(fciOrient, pag, scorer, completeRuleSetUsed, doDiscriminatingPathTailRule,
                doDiscriminatingPathColliderRule, verbose);

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
     * Sets the allowTucks flag to the specified value.
     *
     * @param allowTucks the boolean value indicating whether tucks are allowed
     */
    public void setAllowTucks(boolean allowTucks) {
        this.allowTucks = allowTucks;
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
     * Sets the equality threshold used for comparing values, a fraction of abs(BIC).
     *
     * @param equalityThreshold the new equality threshold value
     */
    public void setEqualityThreshold(double equalityThreshold) {
        if (equalityThreshold < 0) {
            throw new IllegalArgumentException("Equality threshold must be >= 0: " + equalityThreshold);
        }

        this.equalityThreshold = equalityThreshold;
    }

    /**
     * Sets the depth of the GRaSP if it is used.
     * @param depth The depth of the GRaSP.
     */
    public void setDepth(int depth) {
        this.depth = depth;
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
