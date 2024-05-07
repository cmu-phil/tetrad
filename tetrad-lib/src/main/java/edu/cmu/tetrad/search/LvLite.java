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
 * This class provides methods for running the search algorithm and obtaining the learned pattern as a PAG (Partially
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
     * True iff verbose output should be printed.
     */
    private boolean verbose;
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
    private boolean useBes;
    /**
     * This variable represents whether the discriminating path rule is used in the LvLite class.
     * <p>
     * The discriminating path rule is a rule used in the search algorithm. It determines whether the algorithm
     * considers discriminating paths when searching for patterns in the data.
     * <p>
     * By default, the value of this variable is set to false, indicating that the discriminating path rule is not used.
     * To enable the use of the discriminating path rule, set the value of this variable to true using the
     * {@link #setDoDiscriminatingPathRule(boolean)} method.
     */
    private boolean doDiscriminatingPathRule = false;
    /**
     * Determines whether the search algorithm should resolve almost cyclic paths.
     */
    private boolean resolveAlmostCyclicPaths = true;

    /**
     * LvLite constructor. Initializes a new object of LvLite search algorithm with the given IndependenceTest and Score
     * object.
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
     * Run the search and return s a PAG.
     *
     * @return The PAG.
     */
    public Graph search() {
        List<Node> nodes = this.score.getVariables();

        if (nodes == null) {
            throw new NullPointerException("Nodes from test were null.");
        }

        Boss suborderSearch = new Boss(score);
        suborderSearch.setKnowledge(knowledge);
        suborderSearch.setResetAfterBM(true);
        suborderSearch.setResetAfterRS(true);
        suborderSearch.setVerbose(verbose);
        suborderSearch.setUseBes(useBes);
        suborderSearch.setUseDataOrder(useDataOrder);
        suborderSearch.setNumStarts(numStarts);
        PermutationSearch permutationSearch = new PermutationSearch(suborderSearch);
        permutationSearch.setKnowledge(knowledge);
        permutationSearch.search();
        List<Node> best = permutationSearch.getOrder();

        TetradLogger.getInstance().forceLogMessage("Best order: " + best);

        TeyssierScorer teyssierScorer = new TeyssierScorer(null, score);
        teyssierScorer.score(best);
        Graph dag = teyssierScorer.getGraph(false);
        Graph cpdag = teyssierScorer.getGraph(true);
        Graph pag = new EdgeListGraph(cpdag);
        pag.reorientAllWith(Endpoint.CIRCLE);

        FciOrient fciOrient = new FciOrient(null);
        fciOrient.setCompleteRuleSetUsed(completeRuleSetUsed);
        fciOrient.setDoDiscriminatingPathColliderRule(false);
        fciOrient.setDoDiscriminatingPathTailRule(false);
        fciOrient.setVerbose(verbose);
        fciOrient.setKnowledge(knowledge);

        fciOrient.fciOrientbk(knowledge, pag, best);

        // Copy unshielded colliders from DAG to PAG
        for (int i = 0; i < best.size(); i++) {
            for (int j = i + 1; j < best.size(); j++) {
                for (int k = j + 1; k < best.size(); k++) {
                    Node a = best.get(i);
                    Node b = best.get(j);
                    Node c = best.get(k);

                    if (dag.isAdjacentTo(a, c) && dag.isAdjacentTo(b, c) && !dag.isAdjacentTo(a, b)
                        && dag.getEdge(a, c).pointsTowards(c) && dag.getEdge(b, c).pointsTowards(c)) {
                        if (FciOrient.isArrowheadAllowed(a, c, pag, knowledge) && FciOrient.isArrowheadAllowed(b, c, pag, knowledge)) {
                            pag.setEndpoint(a, c, Endpoint.ARROW);
                            pag.setEndpoint(b, c, Endpoint.ARROW);

                            if (verbose) {
                                TetradLogger.getInstance().forceLogMessage("Copying unshielded collider " + a + " -> " + c + " <- " + b
                                                                           + " from CPDAG to PAG");
                            }
                        }
                    }
                }
            }
        }

        teyssierScorer.bookmark();

        Set<Triple> toRemove = new HashSet<>();

        // Our extra collider orientation step to orient <-> edges:
        for (int i = 0; i < best.size(); i++) {
            for (int j = 0; j < best.size(); j++) {
                for (int k = j + 1; k < best.size(); k++) {
                    Node a = best.get(i);
                    Node b = best.get(j);
                    Node c = best.get(k);

                    Edge ab = cpdag.getEdge(a, b);
                    Edge bc = cpdag.getEdge(b, c);
                    Edge ac = cpdag.getEdge(a, c);

                    Edge _ab = pag.getEdge(a, b);
                    Edge _bc = pag.getEdge(b, c);
                    Edge _ac = pag.getEdge(a, c);

                    if ((bc != null && bc.pointsTowards(c)) && ab != null && ac != null
                        && (_bc != null && pag.getEndpoint(b, c) == Endpoint.ARROW) && _ab != null && _ac != null) {
                        teyssierScorer.goToBookmark();
                        teyssierScorer.tuck(c, b);

                        if (!teyssierScorer.adjacent(a, c)) {
                            toRemove.add(new Triple(a, b, c));
                        }
                    }
                }
            }
        }

        for (Triple triple : toRemove) {
            Node a = triple.getX();
            Node b = triple.getY();
            Node c = triple.getZ();

            if (pag.isAdjacentTo(a, c) && pag.isAdjacentTo(c, b)) {
                if (FciOrient.isArrowheadAllowed(c, b, pag, knowledge)) {
                    pag.removeEdge(a, c);
                    pag.setEndpoint(c, b, Endpoint.ARROW);

                    if (verbose) {
                        TetradLogger.getInstance().forceLogMessage("Orienting " + b + " <-* " + c + " in PAG and removing " + a + " *-* " + c + " from PAG.");
                    }
                }
            }
        }

        for (Triple triple : toRemove) {
            Node b = triple.getY();

            List<Node> nodesInTo = pag.getNodesInTo(b, Endpoint.ARROW);

            if (nodesInTo.size() == 1) {
                for (Node node : nodesInTo) {
                    pag.setEndpoint(node, b, Endpoint.CIRCLE);

                    if (verbose) {
                        TetradLogger.getInstance().forceLogMessage("Orienting " + node + " --o " + b + " in PAG.");
                    }
                }
            }
        }

        do {
            if (completeRuleSetUsed) {
                fciOrient.zhangFinalOrientation(pag);
            } else {
                fciOrient.spirtesFinalOrientation(pag);
            }

            fciOrient.zhangFinalOrientation(pag);
        } while (discriminatingPathRule(pag, teyssierScorer));

        // Optional.
        if (resolveAlmostCyclicPaths) {
            for (Edge edge : pag.getEdges()) {
                if (Edges.isBidirectedEdge(edge)) {
                    Node x = edge.getNode1();
                    Node y = edge.getNode2();

                    if (pag.paths().existsDirectedPath(x, y)) {
                        pag.setEndpoint(y, x, Endpoint.TAIL);
                    } else if (pag.paths().existsDirectedPath(y, x)) {
                        pag.setEndpoint(x, y, Endpoint.TAIL);
                    }
                }
            }

            do {
                if (completeRuleSetUsed) {
                    fciOrient.zhangFinalOrientation(pag);
                } else {
                    fciOrient.spirtesFinalOrientation(pag);
                }
            } while (discriminatingPathRule(pag, teyssierScorer));
        }

        GraphUtils.replaceNodes(pag, this.score.getVariables());
        return pag;
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
     * Sets whether the search algorithm should resolve almost cyclic paths.
     *
     * @param resolveAlmostCyclicPaths true to resolve almost cyclic paths, false otherwise
     */
    public void setResolveAlmostCyclicPaths(boolean resolveAlmostCyclicPaths) {
        this.resolveAlmostCyclicPaths = resolveAlmostCyclicPaths;
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
     * a). This is breadth-first, utilizing "reachability" concept from Geiger, Verma, and Pearl 1990. The body of a DDP
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
        Set<Node> colliderPath = new HashSet<>();
        colliderPath.add(a);

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
                colliderPath.add(t);

                if (!graph.isAdjacentTo(d, c)) {
                    if (doDdpOrientation(d, a, b, c, graph, colliderPath, scorer)) {
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
     * @param e            the 'e' node
     * @param a            the 'a' node
     * @param b            the 'b' node
     * @param c            the 'c' node
     * @param graph        the graph representation
     * @param colliderPath the list of nodes in the collider path
     * @return true if the orientation is determined, false otherwise
     * @throws IllegalArgumentException if 'e' is adjacent to 'c'
     */
    private boolean doDdpOrientation(Node e, Node a, Node b, Node c, Graph graph, Set<Node> colliderPath, TeyssierScorer scorer) {
        if (graph.isAdjacentTo(e, c)) {
            throw new IllegalArgumentException();
        }

        scorer.goToBookmark();

        boolean collider;

        if (false) {

            // Joe's tucking scheme:
            for (Node node : colliderPath) {
                scorer.tuck(node, e);
            }

            scorer.moveTo(b, scorer.index(e) + 1);
            collider = !scorer.adjacent(e, c);
        } else {

            // Bryan's alternative:
//            tuck C before B
//            if (E does not precede C)
//            {
//                if (B precedes E)
//                {
//                    tuck E before B
//                }
//                tuck E before C
//            }

            scorer.tuck(c, b);

            if (!(scorer.index(e) < scorer.index(c))) {
                if (scorer.index(b) < scorer.index(e)) {
                    scorer.tuck(e, b);
                }
                scorer.tuck(e, c);
            }

            collider = !scorer.parent(e, c);
        }

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
