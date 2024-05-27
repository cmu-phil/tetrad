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
     * Whether to use data order.
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
    private boolean doDiscriminatingPathRule = true;
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
     * Reorients all edges in a Graph as o-o. This method is used to apply the o-o orientation to all edges in the given
     * Graph following the PAG (Partially Ancestral Graph) structure.
     *
     * @param pag The Graph to be reoriented.
     */
    private void reorientWithCircles(Graph pag) {
        if (verbose) {
            TetradLogger.getInstance().forceLogMessage("Orient all edges in PAG as o-o:");
        }
        pag.reorientAllWith(Endpoint.CIRCLE);
    }

    /**
     * Run the search and return s a PAG.
     *
     * @return The PAG.
     */
    public Graph search() {
        List<Node> nodes = this.score.getVariables();

        if (nodes == null) {
            throw new NullPointerException("Nodes from test were null.");
        }

        if (verbose) {
            TetradLogger.getInstance().forceLogMessage("===Starting LV-Lite===");
        }

        if (verbose) {
            TetradLogger.getInstance().forceLogMessage("Running BOSS to get CPDAG and best order.");
        }

        // BOSS seems to be doing better here.
        var suborderSearch = new Boss(score);
        suborderSearch.setKnowledge(knowledge);
        suborderSearch.setResetAfterBM(true);
        suborderSearch.setResetAfterRS(true);
        suborderSearch.setVerbose(false);
        suborderSearch.setUseBes(useBes);
        suborderSearch.setUseDataOrder(useDataOrder);
        suborderSearch.setNumStarts(numStarts);
        var permutationSearch = new PermutationSearch(suborderSearch);
        permutationSearch.setKnowledge(knowledge);
        permutationSearch.search();
        var best = permutationSearch.getOrder();

        if (verbose) {
            TetradLogger.getInstance().forceLogMessage("Best order: " + best);
        }

        var scorer = new TeyssierScorer(null, score);
        scorer.score(best);
        scorer.bookmark();

        if (verbose) {
            TetradLogger.getInstance().forceLogMessage("Initializing PAG to BOSS CPDAG.");
            TetradLogger.getInstance().forceLogMessage("Initializing scorer with BOSS best order.");
        }

        var cpdag = scorer.getGraph(true);
        var pag = new EdgeListGraph(cpdag);
        scorer.score(best);

        var fciOrient = new FciOrient(null);
        fciOrient.setCompleteRuleSetUsed(completeRuleSetUsed);
        fciOrient.setDoDiscriminatingPathColliderRule(false);
        fciOrient.setDoDiscriminatingPathTailRule(false);
        fciOrient.setKnowledge(knowledge);
        fciOrient.setVerbose(verbose);

        if (verbose) {
            TetradLogger.getInstance().forceLogMessage("Collider orientation and edge removal.");
        }

        // The main procedure.
        Set<Triple> unshieldedColliders = new HashSet<>();
        Set<Triple> _unshieldedColliders;

        do {
            _unshieldedColliders = new HashSet<>(unshieldedColliders);
            orientCollidersAndRemoveEdges(pag, fciOrient, best, scorer, unshieldedColliders, cpdag);
        } while (!unshieldedColliders.equals(_unshieldedColliders));

        finalOrientation(fciOrient, pag, scorer, false);
        finalOrientation(fciOrient, pag, scorer, true);

//        boolean changed;
//        int count = 0;
//
//        do {
//            changed = false;
//
//            for (int i = 0; i < nodes.size(); i++) {
//                for (int j = i + 1; j < nodes.size(); j++) {
//                    Node n1 = nodes.get(i);
//                    Node n2 = nodes.get(j);
//                    if (!pag.isAdjacentTo(n1, n2)) {
//                        List<Node> inducingPath = pag.paths().getInducingPath(n1, n2);
//
//                        if (inducingPath != null) {
//                            pag.addNondirectedEdge(n1, n2);
//                            changed = true;
//                        }
//                    }
//                }
//            }
//
//        } while (changed && count++ <= 2);

//        finalOrientation(fciOrient, pag, scorer);

        return GraphUtils.replaceNodes(pag, this.score.getVariables());
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
     * Sets whether the search algorithm should use the order of the data set during the search.
     *
     * @param useDataOrder true if the algorithm should use the data order, false otherwise
     */
    public void setUseDataOrder(boolean useDataOrder) {
        this.useDataOrder = useDataOrder;
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
     * Sets whether the search algorithm should use the Discriminating Path Rule.
     *
     * @param doDiscriminatingPathRule true if the Discriminating Path Rule should be used, false otherwise
     */
    public void setDoDiscriminatingPathRule(boolean doDiscriminatingPathRule) {
        this.doDiscriminatingPathRule = doDiscriminatingPathRule;
    }

    /**
     * Orients and removes edges in a graph according to specified rules. Edges are removed in the course of the
     * algorithm, and the graph is modified in place. The call to this method may be repeated to account for the
     * possibility that the removal of an edge may allow for further removals or orientations.
     *
     * @param pag       The original graph.
     * @param fciOrient The orientation rules to be applied.
     * @param best      The list of best nodes.
     * @param scorer    The scorer used to evaluate edge orientations.
     */
    private void orientCollidersAndRemoveEdges(Graph pag, FciOrient fciOrient, List<Node> best, TeyssierScorer scorer,
                                               Set<Triple> unshieldedColliders, Graph cpdag) {
        reorientWithCircles(pag);
        doRequiredOrientations(fciOrient, pag, best);

        var reverse = new ArrayList<>(best);
        Collections.reverse(reverse);

        // Copy al the unshielded triples from the old PAG to the new PAG where adjacencies still exist.
        for (Node b : reverse) {
            var adj = pag.getAdjacentNodes(b);

            // Sort adj in the order of reverse
            adj.sort(Comparator.comparingInt(reverse::indexOf));

            for (int i = 0; i < adj.size(); i++) {
                for (int j = i + 1; j < adj.size(); j++) {
                    var x = adj.get(i);
                    var y = adj.get(j);

                    if (triple(pag, x, b, y) && unshieldedColliders.contains(new Triple(x, b, y))) {
                        if (copyUnshieldedCollider(x, b, y, scorer, pag, null, true, cpdag)) {
                            if (verbose) {
                                TetradLogger.getInstance().forceLogMessage(
                                        "Recalled " + x + " *-> " + b + " <-* " + y + " from previous PAG.");
                            }
                        }
                    }
                }
            }
        }

        for (Node b : reverse) {
            var adj = pag.getAdjacentNodes(b);

            // Sort adj in the order of reverse
            adj.sort(Comparator.comparingInt(reverse::indexOf));

            for (int i = 0; i < adj.size(); i++) {
                for (int j = i + 1; j < adj.size(); j++) {
                    var x = adj.get(i);
                    var y = adj.get(j);

                    // If you can copy the unshielded collider from the scorer, do so. Otherwise, if x *-* y im the PAG,
                    // and tucking yields the collider, copy this collider x *-> b <-* y into the PAG as well.
                    if (unshieldedCollider(cpdag, x, b, y) && unshieldedTriple(pag, x, b, y)) {
                        if (copyUnshieldedCollider(x, b, y, scorer, pag, unshieldedColliders, true, cpdag)) {
                            if (verbose) {
                                TetradLogger.getInstance().forceLogMessage(
                                        "Copied " + x + " *-> " + b + " <-* " + y + " from CPDAG to PAG.");
                            }
                        }
                    } else if (allowTucks && pag.isAdjacentTo(x, y)) {
                        scorer.goToBookmark();
                        scorer.tuck(b, x);
                        scorer.tuck(b, y);

                        if (copyUnshieldedCollider(x, b, y, scorer, pag, unshieldedColliders, false, cpdag)) {
                            if (verbose) {
                                TetradLogger.getInstance().forceLogMessage(
                                        "TUCKING: Oriented " + x + " *-> " + b + " <-* " + y + ".");
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Copies the content of node x to node b and removes the edge between node x and node y, based on the specified
     * scorer and graph. If the triple is already an unshielded collider, the method returns false, and if the triple is
     * not a collider in the scorer or is not a triple in the PAG, the method returns false. If orienting the triple as
     * a collider is not allowed, the method returns false. Otherwise, true is returned.
     *
     * @param x      The source node to copy from.
     * @param b      The target node to copy to.
     * @param y      The node to remove the edge between x and y.
     * @param scorer The scorer to evaluate the conditions for copying and removing.
     * @param pag    The PAG to perform the copying and removing operations on.
     * @return <code>true</code> if the removal/orientation code was performed, <code>false</code> otherwise.
     */
    private boolean copyUnshieldedCollider(Node x, Node b, Node y, TeyssierScorer scorer, Graph pag,
                                           Set<Triple> unshieldedColliders, boolean checkCpdag, Graph cpdag) {
        if (unshieldedCollider(pag, x, b, y)) {
            return false;
        }

        boolean unshieldedCollider = checkCpdag ? unshieldedCollider(cpdag, x, b, y) : scorer.unshieldedCollider(x, b, y);

        if (unshieldedCollider && triple(pag, x, b, y) && colliderAllowed(pag, x, b, y)) {
            pag.setEndpoint(x, b, Endpoint.ARROW);
            pag.setEndpoint(y, b, Endpoint.ARROW);

            boolean adj = pag.isAdjacentTo(x, y);

            if (pag.removeEdge(x, y)) {
                if (verbose && adj && !pag.isAdjacentTo(x, y)) {
                    TetradLogger.getInstance().forceLogMessage(
                            "TUCKING: Removed adjacency " + x + " *-* " + y + " in the PAG.");
                }
            }

            if (unshieldedColliders != null) {
                unshieldedColliders.add(new Triple(x, b, y));
            }

            return true;
        }

        return false;
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
    private boolean colliderAllowed(Graph pag, Node x, Node b, Node y) {
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
    private void doRequiredOrientations(FciOrient fciOrient, Graph pag, List<Node> best) {
        if (verbose) {
            TetradLogger.getInstance().forceLogMessage("Orient required edges in PAG:");
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
    private boolean unshieldedTriple(Graph graph, Node a, Node b, Node c) {
        return graph.isAdjacentTo(a, b) && graph.isAdjacentTo(b, c) && !graph.isAdjacentTo(a, c);
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
    private boolean triple(Graph graph, Node a, Node b, Node c) {
        return graph.isAdjacentTo(a, b) && graph.isAdjacentTo(b, c);
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
    private boolean unshieldedCollider(Graph graph, Node a, Node b, Node c) {
        return a != c && unshieldedTriple(graph, a, b, c) && graph.isDefCollider(a, b, c);
    }

    /**
     * Determines the final orientation of the graph using the given FciOrient object, Graph object, and scorer object.
     *
     * @param fciOrient The FciOrient object used to determine the final orientation.
     * @param pag       The Graph object for which the final orientation is determined.
     * @param scorer    The scorer object used in the score-based discriminating path rule.
     */
    private void finalOrientation(FciOrient fciOrient, Graph pag, TeyssierScorer scorer, boolean doColliderRule) {
        if (verbose) {
            TetradLogger.getInstance().forceLogMessage("Final Orientation:");
        }

        do {
            if (completeRuleSetUsed) {
                fciOrient.zhangFinalOrientation(pag);
            } else {
                fciOrient.spirtesFinalOrientation(pag);
            }
        } while (discriminatingPathRule(pag, scorer, doColliderRule)); // Score-based discriminating path rule
    }

    /**
     * This is a score-based discriminating path rule.
     * <p>
     * The triangles that must be oriented this way (won't be done by another rule) all look like the ones below, where
     * the dots are a collider path from E to A with each node on the path (except L) a parent of C.
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
     * @param graph      a {@link Graph} object
     * @param doColliderRule
     */
    private boolean discriminatingPathRule(Graph graph, TeyssierScorer scorer, boolean doColliderRule) {
        if (!doDiscriminatingPathRule) return false;

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

                    boolean _oriented = ddpOrient(a, b, c, graph, scorer, doColliderRule);

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
     * @param a          a {@link Node} object
     * @param b          a {@link Node} object
     * @param c          a {@link Node} object
     * @param graph      a {@link Graph} object
     * @param doColliderRule
     */
    private boolean ddpOrient(Node a, Node b, Node c, Graph graph, TeyssierScorer scorer, boolean doColliderRule) {
        Queue<Node> Q = new ArrayDeque<>(20);
        Set<Node> V = new HashSet<>();

        Node e = null;

        Map<Node, Node> previous = new HashMap<>();
        List<Node> path = new ArrayList<>();
        path.add(a);

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

                previous.put(d, t);
                Node p = previous.get(t);

                if (!graph.isDefCollider(d, t, p)) {
                    continue;
                }

                previous.put(d, t);

                if (!path.contains(t)) {
                    path.add(t);
                }

                if (!graph.isAdjacentTo(d, c)) {
                    if (doDdpOrientation(d, a, b, c, path, graph, scorer, doColliderRule)) {
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
     *      the dots are a collider path from E to A with each node on the path (except L) a parent of C.
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
     * @param e          the 'e' node
     * @param a          the 'a' node
     * @param b          the 'b' node
     * @param c          the 'c' node
     * @param graph      the graph representation
     * @param doColliderRule
     * @return true if the orientation is determined, false otherwise
     * @throws IllegalArgumentException if 'e' is adjacent to 'c'
     */
    private boolean doDdpOrientation(Node e, Node a, Node b, Node c, List<Node> path, Graph
            graph, TeyssierScorer scorer, boolean doColliderRule) {

        if (graph.getEndpoint(c, b) != Endpoint.CIRCLE) {
            return false;
        }

        for (Node n : path) {
            if (!graph.isParentOf(n, c)) {
                throw new IllegalArgumentException("Node " + n + " is not a parent of " + c);
            }
        }

        if (!path.contains(a)) {
            throw new IllegalArgumentException("Path does not contain a");
        }

        scorer.goToBookmark();
        scorer.tuck(b, c);
        scorer.tuck(b, e);
        scorer.tuck(c, e);
//
//        for (Node node : path) {
//            scorer.tuck(e, node);
//        }
//
//        scorer.tuck(a, e);

//        scorer.tuck(b, e);

        boolean collider = !scorer.parent(e, c);

        if (collider && doColliderRule) {
            if (!colliderAllowed(graph, a, b, c)) {
                return false;
            }

            graph.setEndpoint(a, b, Endpoint.ARROW);
            graph.setEndpoint(c, b, Endpoint.ARROW);

            if (this.verbose) {
                TetradLogger.getInstance().forceLogMessage(
                        "R4: Definite discriminating path collider rule e = " + e + " " + GraphUtils.pathString(graph, a, b, c));
            }

            return true;
        } else {
            graph.setEndpoint(c, b, Endpoint.TAIL);

            if (this.verbose) {
                TetradLogger.getInstance().forceLogMessage(
                        "R4: Definite discriminating path tail rule e = " + e + " " + GraphUtils.pathString(graph, a, b, c));
            }

            return true;
        }
    }

    public void setAllowTucks(boolean allowTucks) {
        this.allowTucks = allowTucks;
    }
}
