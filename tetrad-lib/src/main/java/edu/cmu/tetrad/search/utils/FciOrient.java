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
package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.data.KnowledgeEdge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.Fci;
import edu.cmu.tetrad.search.GFci;
import edu.cmu.tetrad.search.Rfci;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.*;

/**
 * <p>Performs the final orientation steps of the FCI algorithms, which
 * is a useful tool to use in a variety of FCI-like algorithms.</p>
 *
 * <p>There are two versions of these final orientation steps, one due to
 * Peter Spirtes (the original, in Causation, Prediction and Search), which is arrow complete, and the other which Jiji
 * Zhang worked out in his Ph.D. dissertation, which is both arrow and tail complete. The references for these are as
 * follows.</p>
 *
 * <p>Spirtes, P., Glymour, C. N., Scheines, R., &amp; Heckerman, D. (2000).
 * Causation, prediction, and search. MIT press.</p>
 *
 * <p>Zhang, J. (2008). On the completeness of orientation rules for causal
 * discovery in the presence of latent confounders and selection bias. Artificial Intelligence, 172(16-17),
 * 1873-1896.</p>
 *
 * <p>These final rules are used in all algorithms in Tetrad that
 * follow and refine the FCI algorithm--for example, the GFCI and RFCI algorihtms.</p>
 *
 * <p>We've made the methods for each of the separate rules publicly
 * accessible in case someone wants to use the individual rules in the context of their own algorithms.</p>
 *
 * @author Erin Korber, June 2004
 * @author Alex Smith, December 2008
 * @author josephramsey
 * @author Choh-Man Teng
 * @see Fci
 * @see GFci
 * @see Rfci
 */
public final class FciOrient {
    private final SepsetProducer sepsets;
    private final TetradLogger logger = TetradLogger.getInstance();
    private Knowledge knowledge = new Knowledge();
    private boolean changeFlag = true;
    private boolean completeRuleSetUsed = true;
    private int maxPathLength = -1;
    private boolean verbose;
    private Graph truePag;
    private boolean doDiscriminatingPathColliderRule = true;
    private boolean doDiscriminatingPathTailRule = true;

    //============================CONSTRUCTORS============================//

    /**
     * Constructs a new FCI search for the given independence test and background knowledge.
     */
    public FciOrient(SepsetProducer sepsets) {
        this.sepsets = sepsets;
    }

    //========================PUBLIC METHODS==========================//

    /**
     * Gets a list of every uncovered partially directed path between two nodes in the graph.
     * <p>
     * Probably extremely slow.
     *
     * @param n1 The beginning node of the undirectedPaths.
     * @param n2 The ending node of the undirectedPaths.
     * @return A list of uncovered partially directed undirectedPaths from n1 to n2.
     */
    public static List<List<Node>> getUcPdPaths(Node n1, Node n2, Graph graph) {
        List<List<Node>> ucPdPaths = new LinkedList<>();

        LinkedList<Node> soFar = new LinkedList<>();
        soFar.add(n1);

        List<Node> adjacencies = graph.getAdjacentNodes(n1);
        for (Node curr : adjacencies) {
            getUcPdPsHelper(curr, soFar, n2, ucPdPaths, graph);
        }

        return ucPdPaths;
    }

    /**
     * Used in getUcPdPaths(n1,n2) to perform a breadth-first search on the graph.
     * <p>
     * ASSUMES soFar CONTAINS AT LEAST ONE NODE!
     * <p>
     * Probably extremely slow.
     *
     * @param curr      The getModel node to test for addition.
     * @param soFar     The getModel partially built-up path.
     * @param end       The node to finish the undirectedPaths at.
     * @param ucPdPaths The getModel list of uncovered p.d. undirectedPaths.
     */
    private static void getUcPdPsHelper(Node curr, List<Node> soFar, Node end,
                                        List<List<Node>> ucPdPaths, Graph graph) {

        if (soFar.contains(curr)) {
            return;
        }

        Node prev = soFar.get(soFar.size() - 1);
        if (graph.getEndpoint(prev, curr) == Endpoint.TAIL
                || graph.getEndpoint(curr, prev) == Endpoint.ARROW) {
            return; // Adding curr would make soFar not p.d.
        } else if (soFar.size() >= 2) {
            Node prev2 = soFar.get(soFar.size() - 2);
            if (graph.isAdjacentTo(prev2, curr)) {
                return; // Adding curr would make soFar not uncovered.
            }
        }

        soFar.add(curr); // Adding curr is OK, so let's do it.

        if (curr.equals(end)) {
            // We've reached the goal! Save soFar as a path.
            ucPdPaths.add(new LinkedList<>(soFar));
        } else {
            // Otherwise, try each node adjacent to the getModel one.
            List<Node> adjacents = graph.getAdjacentNodes(curr);
            for (Node next : adjacents) {
                getUcPdPsHelper(next, soFar, end, ucPdPaths, graph);
            }
        }

        soFar.remove(soFar.get(soFar.size() - 1)); // For other recursive calls.
    }

    /**
     * Gets a list of every uncovered circle path between two nodes in the graph by iterating through the uncovered
     * partially directed undirectedPaths and only keeping the circle undirectedPaths.
     * <p>
     * Probably extremely slow.
     *
     * @param n1 The beginning node of the undirectedPaths.
     * @param n2 The ending node of the undirectedPaths.
     * @return A list of uncovered circle undirectedPaths between n1 and n2.
     */
    public static List<List<Node>> getUcCirclePaths(Node n1, Node n2, Graph graph) {
        List<List<Node>> ucCirclePaths = new LinkedList<>();
        List<List<Node>> ucPdPaths = getUcPdPaths(n1, n2, graph);

        for (List<Node> path : ucPdPaths) {
            for (int i = 0; i < path.size() - 1; i++) {
                Node j = path.get(i);
                Node sj = path.get(i + 1);

                if (!(graph.getEndpoint(j, sj) == Endpoint.CIRCLE)) {
                    break;
                }
                if (!(graph.getEndpoint(sj, j) == Endpoint.CIRCLE)) {
                    break;
                }
                // This edge is OK, it's all circles.

                if (i == path.size() - 2) {
                    // We're at the last edge, so this is a circle path.
                    ucCirclePaths.add(path);
                }
            }
        }

        return ucCirclePaths;
    }

    public static boolean isArrowheadAllowed(Node x, Node y, Graph graph, Knowledge knowledge) {
        if (!graph.isAdjacentTo(x, y)) return false;

        if (graph.getEndpoint(x, y) == Endpoint.ARROW) {
            return true;
        }

        if (graph.getEndpoint(x, y) == Endpoint.TAIL) {
            return false;
        }

        if (graph.getEndpoint(y, x) == Endpoint.ARROW && graph.getEndpoint(x, y) == Endpoint.CIRCLE) {
            if (knowledge.isForbidden(x.getName(), y.getName())) {
                return true;
            }
        }

        if (graph.getEndpoint(y, x) == Endpoint.TAIL && graph.getEndpoint(x, y) == Endpoint.CIRCLE) {
            if (knowledge.isForbidden(x.getName(), y.getName())) {
                return false;
            }
        }

        return graph.getEndpoint(x, y) == Endpoint.CIRCLE;
    }

    /**
     * Performs final FCI orientation on the given graph.
     *
     * @param graph The graph to further orient.
     * @return The oriented graph.
     */
    public Graph orient(Graph graph) {
        this.logger.forceLogMessage("Starting FCI algorithm.");
        ruleR0(graph);

        if (this.verbose) {
            logger.forceLogMessage("R0");
        }

        // Step CI D. (Zhang's step F4.)
        doFinalOrientation(graph);

        if (this.verbose) {
            this.logger.forceLogMessage("Returning graph: " + graph);
        }

        return graph;
    }

    /**
     * Returns the map from {x,y} to {z1,...,zn} for x _||_ y | z1,..,zn.
     *
     * @return Thia map.
     */
    public SepsetProducer getSepsets() {
        return this.sepsets;
    }

    /**
     * Sets the knowledge to use for the final orientation.
     *
     * @param knowledge This knowledge.
     */
    public void setKnowledge(Knowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException();
        }

        this.knowledge = new Knowledge(knowledge);
    }

    /**
     * @return true if Zhang's complete rule set should be used, false if only R1-R4 (the rule set of the original FCI)
     * should be used. False by default.
     */
    public boolean isCompleteRuleSetUsed() {
        return this.completeRuleSetUsed;
    }

    /**
     * @param completeRuleSetUsed set to true if Zhang's complete rule set should be used, false if only R1-R4 (the rule
     *                            set of the original FCI) should be used. False by default.
     */
    public void setCompleteRuleSetUsed(boolean completeRuleSetUsed) {
        this.completeRuleSetUsed = completeRuleSetUsed;
    }

    /**
     * Orients colliders in the graph. (FCI Step C)
     * <p>
     * Zhang's step F3, rule R0.
     *
     * @param graph The graph to orient.
     */
    public void ruleR0(Graph graph) {
        graph.reorientAllWith(Endpoint.CIRCLE);
        fciOrientbk(this.knowledge, graph, graph.getNodes());

        List<Node> nodes = graph.getNodes();

        for (Node b : nodes) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            List<Node> adjacentNodes = new ArrayList<>(graph.getAdjacentNodes(b));

            if (adjacentNodes.size() < 2) {
                continue;
            }

            ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                Node a = adjacentNodes.get(combination[0]);
                Node c = adjacentNodes.get(combination[1]);

                // Skip triples that are shielded.
                if (graph.isAdjacentTo(a, c)) {
                    continue;
                }

                if (graph.isDefCollider(a, b, c)) {
                    continue;
                }

                if (this.sepsets.isUnshieldedCollider(a, b, c)) {
                    if (!isArrowheadAllowed(a, b, graph, knowledge)) {
                        continue;
                    }

                    if (!isArrowheadAllowed(c, b, graph, knowledge)) {
                        continue;
                    }

                    graph.setEndpoint(a, b, Endpoint.ARROW);
                    graph.setEndpoint(c, b, Endpoint.ARROW);
                    if (this.verbose) {
                        this.logger.forceLogMessage(LogUtilsSearch.colliderOrientedMsg(a, b, c));

                        printWrongColliderMessage(a, b, c, graph);
                    }
                }
            }
        }
    }

    /**
     * Orients the graph according to rules in the graph (FCI step D).
     * <p>
     * Zhang's step F4, rules R1-R10.
     */
    public void doFinalOrientation(Graph graph) {
        if (this.completeRuleSetUsed) {
            zhangFinalOrientation(graph);
        } else {
            spirtesFinalOrientation(graph);
        }
    }

    public void spirtesFinalOrientation(Graph graph) {
        this.changeFlag = true;
        boolean firstTime = true;

        while (this.changeFlag) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            this.changeFlag = false;
            rulesR1R2cycle(graph);
            ruleR3(graph);

            // R4 requires an arrow orientation.
            if (this.changeFlag || (firstTime && !this.knowledge.isEmpty())) {
                ruleR4B(graph);
                firstTime = false;
            }

            if (this.verbose) {
                logger.forceLogMessage("Epoch");
            }
        }
    }

    public void zhangFinalOrientation(Graph graph) {
        this.changeFlag = true;
        boolean firstTime = true;

        while (this.changeFlag && !Thread.currentThread().isInterrupted()) {
            this.changeFlag = false;
            rulesR1R2cycle(graph);
            ruleR3(graph);

            // R4 requires an arrow orientation.
            if (this.changeFlag || (firstTime && !this.knowledge.isEmpty())) {
                ruleR4B(graph);
                firstTime = false;
            }

            if (this.verbose) {
                logger.forceLogMessage("Epoch");
            }
        }

        if (isCompleteRuleSetUsed()) {
            // Now, by a remark on page 100 of Zhang's dissertation, we apply rule
            // R5 once.
            ruleR5(graph);

            // Now, by a further remark on page 102, we apply R6,R7 as many times
            // as possible.
            this.changeFlag = true;

            while (this.changeFlag && !Thread.currentThread().isInterrupted()) {
                this.changeFlag = false;
                ruleR6R7(graph);
            }

            // Finally, we apply R8-R10 as many times as possible.
            this.changeFlag = true;

            while (this.changeFlag && !Thread.currentThread().isInterrupted()) {
                this.changeFlag = false;
                rulesR8R9R10(graph);
            }

        }
    }

    //Does all 3 of these rules at once instead of going through all
    // triples multiple times per iteration of doFinalOrientation.
    public void rulesR1R2cycle(Graph graph) {
        List<Node> nodes = graph.getNodes();

        for (Node B : nodes) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            List<Node> adj = new ArrayList<>(graph.getAdjacentNodes(B));

            if (adj.size() < 2) {
                continue;
            }

            ChoiceGenerator cg = new ChoiceGenerator(adj.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null && !Thread.currentThread().isInterrupted()) {
                Node A = adj.get(combination[0]);
                Node C = adj.get(combination[1]);

                //choice gen doesnt do diff orders, so must switch A & C around.
                ruleR1(A, B, C, graph);
                ruleR1(C, B, A, graph);
                ruleR2(A, B, C, graph);
                ruleR2(C, B, A, graph);
            }
        }
    }

    /// R1, away from collider
    // If a*->bo-*c and a, c not adjacent then a*->b->c
    public void ruleR1(Node a, Node b, Node c, Graph graph) {
        if (graph.isAdjacentTo(a, c)) {
            return;
        }

        if (graph.getEndpoint(a, b) == Endpoint.ARROW && graph.getEndpoint(c, b) == Endpoint.CIRCLE) {
            if (!isArrowheadAllowed(b, c, graph, knowledge)) {
                return;
            }

            graph.setEndpoint(c, b, Endpoint.TAIL);
            graph.setEndpoint(b, c, Endpoint.ARROW);
            this.changeFlag = true;

            if (this.verbose) {
                this.logger.forceLogMessage(LogUtilsSearch.edgeOrientedMsg("R1: Away from collider", graph.getEdge(b, c)));
            }
        }
    }

    //if a*-oc and either a-->b*->c or a*->b-->c, and a*-oc then a*->c
    // This is Zhang's rule R2.
    public void ruleR2(Node a, Node b, Node c, Graph graph) {
        if ((graph.isAdjacentTo(a, c)) && (graph.getEndpoint(a, c) == Endpoint.CIRCLE)) {
            if ((graph.getEndpoint(a, b) == Endpoint.ARROW && graph.getEndpoint(b, c) == Endpoint.ARROW)
                    && (graph.getEndpoint(b, a) == Endpoint.TAIL || graph.getEndpoint(c, b) == Endpoint.TAIL)) {

                if (!isArrowheadAllowed(a, c, graph, knowledge)) {
                    return;
                }

                graph.setEndpoint(a, c, Endpoint.ARROW);

                if (this.verbose) {
                    this.logger.forceLogMessage(LogUtilsSearch.edgeOrientedMsg("R2: Away from ancestor", graph.getEdge(a, c)));
                }

                this.changeFlag = true;
            }
        }
    }

    /**
     * Implements the double-triangle orientation rule, which states that if D*-oB, A*->B<-*C and A*-oDo-*C, and !adj(a,
     * c), D*-oB, then D*->B.
     * <p>
     * This is Zhang's rule R3.
     */
    public void ruleR3(Graph graph) {
        List<Node> nodes = graph.getNodes();

        for (Node b : nodes) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            List<Node> intoBArrows = graph.getNodesInTo(b, Endpoint.ARROW);

            if (intoBArrows.size() < 2) continue;

            ChoiceGenerator gen = new ChoiceGenerator(intoBArrows.size(), 2);
            int[] choice;

            while ((choice = gen.next()) != null) {
                List<Node> B = GraphUtils.asList(choice, intoBArrows);

                Node a = B.get(0);
                Node c = B.get(1);

                List<Node> adj = new ArrayList<>(graph.getAdjacentNodes(a));
                adj.retainAll(graph.getAdjacentNodes(c));

                for (Node d : adj) {
                    if (d == a) continue;

                    if (graph.getEndpoint(a, d) == Endpoint.CIRCLE && graph.getEndpoint(c, d) == Endpoint.CIRCLE) {
                        if (!graph.isAdjacentTo(a, c)) {
                            if (graph.getEndpoint(d, b) == Endpoint.CIRCLE) {
                                graph.setEndpoint(d, b, Endpoint.ARROW);

                                if (this.verbose) {
                                    this.logger.forceLogMessage(LogUtilsSearch.edgeOrientedMsg("R3: Double triangle", graph.getEdge(d, b)));
                                }

                                this.changeFlag = true;
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * The triangles that must be oriented this way (won't be done by another rule) all look like the ones below, where
     * the dots are a collider path from L to A with each node on the path (except L) a parent of C.
     * <pre>
     *          B
     *         xo           x is either an arrowhead or a circle
     *        /  \
     *       v    v
     * L....A --> C
     * </pre>
     * <p>
     * This is Zhang's rule R4, discriminating paths.
     */
    public void ruleR4B(Graph graph) {

        if (doDiscriminatingPathColliderRule || doDiscriminatingPathTailRule) {
            List<Node> nodes = graph.getNodes();

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

                        ddpOrient(a, b, c, graph);
                    }
                }
            }
        }
    }

    /**
     * a method to search "back from a" to find a DDP. It is called with a reachability list (first consisting only of
     * a). This is breadth-first, utilizing "reachability" concept from Geiger, Verma, and Pearl 1990. The body of a DDP
     * consists of colliders that are parents of c.
     */
    public void ddpOrient(Node a, Node b, Node c, Graph graph) {
        Queue<Node> Q = new ArrayDeque<>(20);
        Set<Node> V = new HashSet<>();

        Node e = null;
        int distance = 0;

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
                distance++;
                if (distance > 0 && distance > (this.maxPathLength == -1 ? 1000 : this.maxPathLength)) {
                    return;
                }
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
                    if (doDdpOrientation(d, a, b, c, graph)) {
                        return;
                    }
                }

                if (cParents.contains(d)) {
                    Q.offer(d);
                    V.add(d);
                }
            }
        }
    }

    /**
     * Implements Zhang's rule R5, orient circle undirectedPaths: for any Ao-oB, if there is an uncovered circle path u
     * = [A,C,...,D,B] such that A,D nonadjacent and B,C nonadjacent, then A---B and orient every edge on u undirected.
     */
    public void ruleR5(Graph graph) {
        List<Node> nodes = graph.getNodes();

        for (Node a : nodes) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            List<Node> adjacents = graph.getNodesInTo(a, Endpoint.CIRCLE);

            for (Node b : adjacents) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                if (!(graph.getEndpoint(a, b) == Endpoint.CIRCLE)) {
                    continue;
                }
                // We know Ao-oB.

                List<List<Node>> ucCirclePaths = getUcCirclePaths(a, b, graph);

                for (List<Node> u : ucCirclePaths) {
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }

                    if (u.size() < 3) {
                        continue;
                    }

                    Node c = u.get(1);
                    Node d = u.get(u.size() - 2);

                    if (graph.isAdjacentTo(a, d)) {
                        continue;
                    }
                    if (graph.isAdjacentTo(b, c)) {
                        continue;
                    }
                    // We know u is as required: R5 applies!


                    graph.setEndpoint(a, b, Endpoint.TAIL);
                    graph.setEndpoint(b, a, Endpoint.TAIL);

                    if (verbose) {
                        this.logger.forceLogMessage(LogUtilsSearch.edgeOrientedMsg(
                                "R5: Orient circle path", graph.getEdge(a, b)));
                    }

                    orientTailPath(u, graph);
                    this.changeFlag = true;
                }
            }
        }
    }

    /**
     * Implements Zhang's rules R6 and R7, applies them over the graph once. Orient single tails. R6: If A---Bo-*C then
     * A---B--*C. R7: If A--oBo-*C and A,C nonadjacent, then A--oB--*C
     */
    public void ruleR6R7(Graph graph) {
        List<Node> nodes = graph.getNodes();

        for (Node b : nodes) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            List<Node> adjacents = new ArrayList<>(graph.getAdjacentNodes(b));

            if (adjacents.size() < 2) {
                continue;
            }

            ChoiceGenerator cg = new ChoiceGenerator(adjacents.size(), 2);

            for (int[] choice = cg.next(); choice != null && !Thread.currentThread().isInterrupted(); choice = cg.next()) {
                Node a = adjacents.get(choice[0]);
                Node c = adjacents.get(choice[1]);

                if (graph.isAdjacentTo(a, c)) {
                    continue;
                }

                if (!(graph.getEndpoint(b, a) == Endpoint.TAIL)) {
                    continue;
                }
                if (!(graph.getEndpoint(c, b) == Endpoint.CIRCLE)) {
                    continue;
                }

                // We know A--*Bo-*C.

                if (graph.getEndpoint(a, b) == Endpoint.TAIL) {

                    // We know A---Bo-*C: R6 applies!
                    graph.setEndpoint(c, b, Endpoint.TAIL);

                    if (verbose) {
                        this.logger.forceLogMessage(LogUtilsSearch.edgeOrientedMsg(
                                "R6: Single tails (tail)", graph.getEdge(c, b)));
                    }

                    this.changeFlag = true;
                }

                if (graph.getEndpoint(a, b) == Endpoint.CIRCLE) {
//                    if (graph.isAdjacentTo(a, c)) continue;

                    graph.setEndpoint(c, b, Endpoint.TAIL);

                    if (verbose) {
                        this.logger.forceLogMessage(LogUtilsSearch.edgeOrientedMsg("R7: Single tails (tail)", graph.getEdge(c, b)));
                    }

                    // We know A--oBo-*C and A,C nonadjacent: R7 applies!
                    this.changeFlag = true;
                }

            }
        }
    }

    /**
     * Implements Zhang's rules R8, R9, R10, applies them over the graph once. Orient arrow tails. I.e., tries R8, R9,
     * and R10 in that sequence on each Ao-&gt;C in the graph.
     */
    public void rulesR8R9R10(Graph graph) {
        List<Node> nodes = graph.getNodes();

        for (Node c : nodes) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            List<Node> intoCArrows = graph.getNodesInTo(c, Endpoint.ARROW);

            for (Node a : intoCArrows) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                if (!(graph.getEndpoint(c, a) == Endpoint.CIRCLE)) {
                    continue;
                }
                // We know Ao->C.

                // Try each of R8, R9, R10 in that order, stopping ASAP.
                if (!ruleR8(a, c, graph)) {
                    boolean b = ruleR9(a, c, graph);

                    if (!b) {
                        ruleR10(a, c, graph);
                    }
                }
            }
        }
    }

    /**
     * Orients the edges inside the definte discriminating path triangle. Takes the left endpoint, and a,b,c as
     * arguments.
     */
    public boolean doDdpOrientation(Node d, Node a, Node b, Node c, Graph graph) {
        if (graph.isAdjacentTo(d, c)) {
            throw new IllegalArgumentException();
        }

        Set<Node> sepset = getSepsets().getSepset(d, c);

        if (this.verbose) {
            logger.forceLogMessage("Sepset for d = " + d + " and c = " + c + " = " + sepset);
        }

        if (sepset == null) {
            if (this.verbose) {
                logger.forceLogMessage("Must be a sepset: " + d + " and " + c + "; they're non-adjacent.");
            }
            return false;
        }

        if (!sepset.contains(b) && doDiscriminatingPathColliderRule) {
            if (!isArrowheadAllowed(a, b, graph, knowledge)) {
                return false;
            }

            if (!isArrowheadAllowed(c, b, graph, knowledge)) {
                return false;
            }

            graph.setEndpoint(a, b, Endpoint.ARROW);
            graph.setEndpoint(c, b, Endpoint.ARROW);

            if (this.verbose) {
                this.logger.forceLogMessage(
                        "R4: Definite discriminating path collider rule d = " + d + " " + GraphUtils.pathString(graph, a, b, c));
            }

            this.changeFlag = true;
        } else if (doDiscriminatingPathTailRule) {
            graph.setEndpoint(c, b, Endpoint.TAIL);

            if (this.verbose) {
                this.logger.forceLogMessage(LogUtilsSearch.edgeOrientedMsg(
                        "R4: Definite discriminating path tail rule d = " + d, graph.getEdge(b, c)));
            }

            this.changeFlag = true;
            return true;
        }

        return false;
    }

    /**
     * Orients every edge on a path as undirected (i.e. A---B).
     * <p>
     * DOES NOT CHECK IF SUCH EDGES ACTUALLY EXIST: MAY DO WEIRD THINGS IF PASSED AN ARBITRARY LIST OF NODES THAT IS NOT
     * A PATH.
     *
     * @param path The path to orient as all tails.
     */
    public void orientTailPath(List<Node> path, Graph graph) {
        for (int i = 0; i < path.size() - 1; i++) {
            Node n1 = path.get(i);
            Node n2 = path.get(i + 1);

            graph.setEndpoint(n1, n2, Endpoint.TAIL);
            graph.setEndpoint(n2, n1, Endpoint.TAIL);
            this.changeFlag = true;

            if (verbose) {
                this.logger.forceLogMessage("R8: Orient circle undirectedPaths " +
                        GraphUtils.pathString(graph, n1, n2));
            }
        }
    }

    /**
     * Tries to apply Zhang's rule R8 to a pair of nodes A and C which are assumed to be such that Ao->C.
     * <p>
     * MAY HAVE WEIRD EFFECTS ON ARBITRARY NODE PAIRS.
     * <p>
     * R8: If Ao->C and A-->B-->C or A--oB-->C, then A-->C.
     *
     * @param a The node A.
     * @param c The node C.
     * @return Whether R8 was successfully applied.
     */
    public boolean ruleR8(Node a, Node c, Graph graph) {
        List<Node> intoCArrows = graph.getNodesInTo(c, Endpoint.ARROW);

        for (Node b : intoCArrows) {
            // We have B*-&gt;C.
            if (!graph.isAdjacentTo(a, b)) {
                continue;
            }
            if (!graph.isAdjacentTo(b, c)) {
                continue;
            }

            // We have A*-*B*->C.
            if (!(graph.getEndpoint(b, a) == Endpoint.TAIL)) {
                continue;
            }
            if (!(graph.getEndpoint(c, b) == Endpoint.TAIL)) {
                continue;
            }
            // We have A--*B-->C.

            if (graph.getEndpoint(a, b) == Endpoint.TAIL) {
                continue;
            }
            // We have A-->B-->C or A--oB-->C: R8 applies!

            graph.setEndpoint(c, a, Endpoint.TAIL);

            if (verbose) {
                this.logger.forceLogMessage(LogUtilsSearch.edgeOrientedMsg("R8: ", graph.getEdge(c, a)));
            }

            this.changeFlag = true;
            return true;
        }

        return false;
    }

    /**
     * Tries to apply Zhang's rule R9 to a pair of nodes A and C which are assumed to be such that Ao->C.
     * <p>
     * MAY HAVE WEIRD EFFECTS ON ARBITRARY NODE PAIRS.
     * <p>
     * R9: If Ao->C and there is an uncovered p.d. path u=<A,B,..,C> such that C,B nonadjacent, then A-->C.
     *
     * @param a The node A.
     * @param c The node C.
     * @return Whether R9 was succesfully applied.
     */
    public boolean ruleR9(Node a, Node c, Graph graph) {
        Edge e = graph.getEdge(a, c);

        if (e == null) return false;
        if (!e.equals(Edges.partiallyOrientedEdge(a, c))) return false;

        List<List<Node>> ucPdPsToC = getUcPdPaths(a, c, graph);

        for (List<Node> u : ucPdPsToC) {
            Node b = u.get(1);
            if (graph.isAdjacentTo(b, c)) {
                continue;
            }
            if (b == c) {
                continue;
            }
            // We know u is as required: R9 applies!


            graph.setEndpoint(c, a, Endpoint.TAIL);

            if (verbose) {
                this.logger.forceLogMessage(LogUtilsSearch.edgeOrientedMsg("R9: ", graph.getEdge(c, a)));
            }

            this.changeFlag = true;
            return true;
        }

        return false;
    }

    /**
     * Orients according to background knowledge
     */
    public void fciOrientbk(Knowledge bk, Graph graph, List<Node> variables) {
        this.logger.forceLogMessage("Starting BK Orientation.");

        for (Iterator<KnowledgeEdge> it
             = bk.forbiddenEdgesIterator(); it.hasNext(); ) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            KnowledgeEdge edge = it.next();

            //match strings to variables in the graph.
            Node from = GraphSearchUtils.translate(edge.getFrom(), variables);
            Node to = GraphSearchUtils.translate(edge.getTo(), variables);

            if (from == null || to == null) {
                continue;
            }

            if (graph.getEdge(from, to) == null) {
                continue;
            }

            // Orient to*->from
            graph.setEndpoint(to, from, Endpoint.ARROW);
            this.changeFlag = true;
            this.logger.forceLogMessage(LogUtilsSearch.edgeOrientedMsg("Knowledge", graph.getEdge(from, to)));
        }

        for (Iterator<KnowledgeEdge> it
             = bk.requiredEdgesIterator(); it.hasNext(); ) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            KnowledgeEdge edge = it.next();

            //match strings to variables in this graph
            Node from = GraphSearchUtils.translate(edge.getFrom(), variables);
            Node to = GraphSearchUtils.translate(edge.getTo(), variables);

            if (from == null || to == null) {
                continue;
            }

            if (graph.getEdge(from, to) == null) {
                continue;
            }

            graph.setEndpoint(to, from, Endpoint.TAIL);
            graph.setEndpoint(from, to, Endpoint.ARROW);
            this.changeFlag = true;
            this.logger.forceLogMessage(LogUtilsSearch.edgeOrientedMsg("Knowledge", graph.getEdge(from, to)));
        }

        this.logger.forceLogMessage("Finishing BK Orientation.");
    }

    /**
     * @return the maximum length of any discriminating path, or -1 of unlimited.
     */
    public int getMaxPathLength() {
        return this.maxPathLength;
    }

    /**
     * @param maxPathLength the maximum length of any discriminating path, or -1 if unlimited.
     */
    public void setMaxPathLength(int maxPathLength) {
        if (maxPathLength < -1) {
            throw new IllegalArgumentException("Max path length must be -1 (unlimited) or >= 0: " + maxPathLength);
        }

        this.maxPathLength = maxPathLength;
    }

    /**
     * Sets whether verbose output is printed.
     *
     * @param verbose True if so.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * The true PAG if available. Can be null.
     */
    public Graph getTruePag() {
        return this.truePag;
    }

    /**
     * Sets the true PAG for comparison.
     *
     * @param truePag This PAG.
     */
    public void setTruePag(Graph truePag) {
        this.truePag = truePag;
    }

    /**
     * Change flag for repeat rules
     *
     * @return True if a change has occurred.
     */
    public boolean isChangeFlag() {
        return this.changeFlag;
    }

    /**
     * Sets the change flag--marks externally that a change has been made.
     *
     * @param changeFlag This flag.
     */
    public void setChangeFlag(boolean changeFlag) {
        this.changeFlag = changeFlag;
    }

    /**
     * Sets whether the discriminating path collider rule should be done.
     *
     * @param doDiscriminatingPathColliderRule True is done.
     */
    public void setDoDiscriminatingPathColliderRule(boolean doDiscriminatingPathColliderRule) {
        this.doDiscriminatingPathColliderRule = doDiscriminatingPathColliderRule;
    }

    /**
     * Sets whether the discriminating path tail rule should be done.
     *
     * @param doDiscriminatingPathTailRule True if done.
     */
    public void setDoDiscriminatingPathTailRule(boolean doDiscriminatingPathTailRule) {
        this.doDiscriminatingPathTailRule = doDiscriminatingPathTailRule;
    }

    /**
     * Tries to apply Zhang's rule R10 to a pair of nodes A and C which are assumed to be such that Ao->C.
     * <p>
     * MAY HAVE WEIRD EFFECTS ON ARBITRARY NODE PAIRS.
     * <p>
     * R10: If Ao->C, B-->C<--D, there is an uncovered p.d. path u1=<A,M,...,B> and an uncovered p.d. path u2=
     * <A,N,...,D> with M != N and M,N nonadjacent then A-->C.
     *
     * @param a The node A.
     * @param c The node C.
     */
    public void ruleR10(Node a, Node c, Graph graph) {
        List<Node> intoCArrows = graph.getNodesInTo(c, Endpoint.ARROW);

        for (Node b : intoCArrows) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            if (b == a) {
                continue;
            }

            if (!(graph.getEndpoint(c, b) == Endpoint.TAIL)) {
                continue;
            }
            // We know Ao->C and B-->C.

            for (Node d : intoCArrows) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                if (d == a || d == b) {
                    continue;
                }

                if (!(graph.getEndpoint(d, c) == Endpoint.TAIL)) {
                    continue;
                }
                // We know Ao->C and B-->C&lt;--D.

                List<List<Node>> ucPdPsToB = getUcPdPaths(a, b, graph);
                List<List<Node>> ucPdPsToD = getUcPdPaths(a, d, graph);
                for (List<Node> u1 : ucPdPsToB) {
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }

                    Node m = u1.get(1);
                    for (List<Node> u2 : ucPdPsToD) {
                        if (Thread.currentThread().isInterrupted()) {
                            break;
                        }

                        Node n = u2.get(1);

                        if (m.equals(n)) {
                            continue;
                        }
                        if (graph.isAdjacentTo(m, n)) {
                            continue;
                        }
                        // We know B,D,u1,u2 as required: R10 applies!

                        graph.setEndpoint(c, a, Endpoint.TAIL);

                        if (verbose) {
                            this.logger.forceLogMessage(LogUtilsSearch.edgeOrientedMsg("R10: ", graph.getEdge(c, a)));
                        }

                        this.changeFlag = true;
                        return;
                    }
                }
            }
        }

    }

    private void printWrongColliderMessage(Node a, Node b, Node c, Graph graph) {
        if (this.truePag != null && graph.isDefCollider(a, b, c) && !this.truePag.isDefCollider(a, b, c)) {
            logger.forceLogMessage("R0" + ": Orienting collider by mistake: " + a + "*->;" + b + "<-*" + c);
        }
    }
}
