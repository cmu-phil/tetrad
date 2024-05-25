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
    private static void reorientWithCircles(Graph pag) {
        TetradLogger.getInstance().forceLogMessage("\nOrient all edges in PAG as o-o:\n");
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

        // BOSS seems to be doing better here.
        var suborderSearch = new Boss(score);
        suborderSearch.setKnowledge(knowledge);
        suborderSearch.setResetAfterBM(true);
        suborderSearch.setResetAfterRS(true);
        suborderSearch.setVerbose(verbose);
        suborderSearch.setUseBes(useBes);
        suborderSearch.setUseDataOrder(useDataOrder);
        suborderSearch.setNumStarts(numStarts);
        var permutationSearch = new PermutationSearch(suborderSearch);
        permutationSearch.setKnowledge(knowledge);
        permutationSearch.search();
        var best = permutationSearch.getOrder();

        TetradLogger.getInstance().forceLogMessage("Best order: " + best);

        var teyssierScorer = new TeyssierScorer(null, score);
        teyssierScorer.score(best);
        teyssierScorer.bookmark();

        var cpdag = teyssierScorer.getGraph(true);

        var pag = new EdgeListGraph(cpdag);
        teyssierScorer.score(best);

        var fciOrient = new FciOrient(null);
        fciOrient.setCompleteRuleSetUsed(completeRuleSetUsed);
        fciOrient.setDoDiscriminatingPathColliderRule(false);
        fciOrient.setDoDiscriminatingPathTailRule(false);
        fciOrient.setKnowledge(knowledge);
        fciOrient.setVerbose(verbose);

        // The main procedure.
        orientAndRemoveEdges(pag, fciOrient, best, cpdag, teyssierScorer);
        orientAndRemoveEdges(pag, fciOrient, best, cpdag, teyssierScorer);
        removeNonRequiredSingleArrows(pag);
        finalOrientation(fciOrient, pag, teyssierScorer);

        return GraphUtils.replaceNodes(pag, this.score.getVariables());
    }

    /**
     * Orients and removes edges in a graph according to specified rules. Edges are removed in the course of the
     * algorithm, and the graph is modified in place. The call to this method may be repeated to account for the
     * possibility that the removal of an edge may allow for further removals or orientations.
     *
     * @param pag            The original graph.
     * @param fciOrient      The orientation rules to be applied.
     * @param best           The list of best nodes.
     * @param cpdag          The CPDAG graph.
     * @param teyssierScorer The scorer used to evaluate edge orientations.
     */
    private void orientAndRemoveEdges(Graph pag, FciOrient fciOrient, List<Node> best, Graph cpdag, TeyssierScorer teyssierScorer) {
        reorientWithCircles(pag);
        doRequiredOrientations(fciOrient, pag, best);

        for (Node b : pag.getNodes()) {
            List<Node> adj = pag.getAdjacentNodes(b);

            for (Node x : adj) {
                for (Node y : adj) {

                    // If you can copy the unshielded collider from the CPDAG, do so. Otherwise, if x *-* y, and you
                    // can form at least one bidirected edge
                    if (unshieldedCollider(cpdag, x, b, y) && !pag.isAdjacentTo(x, y) && colliderAllowed(pag, x, b, y)) {
                        pag.setEndpoint(x, b, Endpoint.ARROW);
                        pag.setEndpoint(y, b, Endpoint.ARROW);
                    } else if (pag.isAdjacentTo(x, y) && (pag.getEndpoint(x, b) == Endpoint.ARROW || pag.getEndpoint(b, y) == Endpoint.ARROW)
                               && colliderAllowed(pag, x, b, y)) {

                        // Try to make a collider x *-> b <-* y in the scorer...
                        teyssierScorer.goToBookmark();
                        teyssierScorer.tuck(b, x);
                        teyssierScorer.tuck(b, y);

                        // If you made an unshielded collider, remove x *-* y and orient x *-> b <-* y.
                        if (teyssierScorer.unshieldedCollider(x, b, y)) {
                            pag.removeEdge(x, y);
                            pag.setEndpoint(x, b, Endpoint.ARROW);
                            pag.setEndpoint(x, b, Endpoint.ARROW);
                        }
                    }
                }
            }
        }
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
        TetradLogger.getInstance().forceLogMessage("\nOrient required edges in PAG:\n");

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
     * Checks if the given nodes are unshielded colliders when considering the given graph.
     *
     * @param graph the graph to consider
     * @param a     the first node
     * @param b     the second node
     * @param c     the third node
     * @return true if the nodes are unshielded colliders, false otherwise
     */
    private boolean unshieldedCollider(Graph graph, Node a, Node b, Node c) {
        return unshieldedTriple(graph, a, b, c) && graph.isDefCollider(a, b, c);
    }

    /**
     * Removes non-required single arrows in a graph. For each node b, if there is only one directed edge *-> b, it
     * reorients the edge as *-o b. Uses the knowledge object to determine if the reorientation is required or
     * forbidden.
     *
     * @param pag The graph to remove non-required single arrows from.
     */
    private void removeNonRequiredSingleArrows(Graph pag) {
        TetradLogger.getInstance().forceLogMessage("\nFor each b, if there on only one d *-> b, orient as d *-o b.\n");

        for (Node b : pag.getNodes()) {
            List<Node> nodesInTo = pag.getNodesInTo(b, Endpoint.ARROW);

            if (nodesInTo.size() == 1) {
                for (Node node : nodesInTo) {
                    if (knowledge.isRequired(node.getName(), b.getName()) || knowledge.isForbidden(b.getName(), node.getName())) {
                        continue;
                    }

                    pag.setEndpoint(node, b, Endpoint.CIRCLE);

                    if (verbose) {
                        TetradLogger.getInstance().forceLogMessage("Orienting " + node + " --o " + b + " in PAG");
                    }
                }
            }
        }
    }

    /**
     * Determines the final orientation of the graph using the given FciOrient object, Graph object, and TeyssierScorer
     * object.
     *
     * @param fciOrient      The FciOrient object used to determine the final orientation.
     * @param pag            The Graph object for which the final orientation is determined.
     * @param teyssierScorer The TeyssierScorer object used in the score-based discriminating path rule.
     */
    private void finalOrientation(FciOrient fciOrient, Graph pag, TeyssierScorer teyssierScorer) {
        TetradLogger.getInstance().forceLogMessage("\nFinal Orientation:");

        do {
            if (completeRuleSetUsed) {
                fciOrient.zhangFinalOrientation(pag);
            } else {
                fciOrient.spirtesFinalOrientation(pag);
            }
        } while (discriminatingPathRule(pag, teyssierScorer)); // Score-based discriminating path rule
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
     * @param graph a {@link edu.cmu.tetrad.graph.Graph} object
     */
    private boolean discriminatingPathRule(Graph graph, TeyssierScorer scorer) {
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

                    boolean _oriented = ddpOrient(a, b, c, graph, scorer);

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
     * @param a     a {@link edu.cmu.tetrad.graph.Node} object
     * @param b     a {@link edu.cmu.tetrad.graph.Node} object
     * @param c     a {@link edu.cmu.tetrad.graph.Node} object
     * @param graph a {@link edu.cmu.tetrad.graph.Graph} object
     */
    private boolean ddpOrient(Node a, Node b, Node c, Graph graph, TeyssierScorer scorer) {
        Queue<Node> Q = new ArrayDeque<>(20);
        Set<Node> V = new HashSet<>();

        Node e = null;

        Map<Node, Node> previous = new HashMap<>();

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

                if (!graph.isAdjacentTo(d, c)) {
                    if (doDdpOrientation(d, a, b, c, graph, scorer)) {
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
     * @param e     the 'e' node
     * @param a     the 'a' node
     * @param b     the 'b' node
     * @param c     the 'c' node
     * @param graph the graph representation
     * @return true if the orientation is determined, false otherwise
     * @throws IllegalArgumentException if 'e' is adjacent to 'c'
     */
    private boolean doDdpOrientation(Node e, Node a, Node b, Node c, Graph
            graph, TeyssierScorer scorer) {

        if (graph.getEndpoint(c, b) != Endpoint.CIRCLE) {
            return false;
        }

        scorer.goToBookmark();
        scorer.tuck(b, c);
        scorer.tuck(b, e);
        scorer.tuck(c, e);

        boolean collider = !scorer.parent(e, c);

        if (collider) {
            if (!FciOrient.isArrowheadAllowed(a, b, graph, knowledge)) {
                return false;
            }

            if (!FciOrient.isArrowheadAllowed(c, b, graph, knowledge)) {
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
}
