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
import edu.cmu.tetrad.search.FciOrientDijkstra;
import edu.cmu.tetrad.search.GFci;
import edu.cmu.tetrad.search.Rfci;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.R5R9Dijkstra;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * Performs the final orientation steps of the FCI algorithms, which is a useful tool to use in a variety of FCI-like
 * algorithms.
 * <p>
 * There are two versions of these final orientation steps, one due to Peter Spirtes (the original, in Causation,
 * Prediction and Search), which is arrow complete, and the other due to Jiji Zhang, which is arrow and tail complete.
 * The references for these are as follows.
 * <p>
 * Spirtes, P., Glymour, C. N., Scheines, R., &amp; Heckerman, D. (2000). Causation, prediction, and search. MIT press.
 * <p>
 * Zhang, J. (2008). On the completeness of orientation rules for causal discovery in the presence of latent confounders
 * and selection bias. Artificial Intelligence, 172(16-17), 1873-1896.
 * <p>
 * These final rules are used in all algorithms in Tetrad that follow and refine the FCI algorithm--for example, the
 * GFCI and RFCI algorihtms.
 * <p>
 * We've made the methods for each of the separate rules publicly accessible in case someone wants to use the individual
 * rules in the context of their own algorithms.
 * <p>
 * Note: This class is a modified version of the original FciOrient class, in that we allow the R0 and R4 rules to be be
 * overridden by subclasses. This is useful for the TeyssierScorer class, which needs to override these rules in order
 * to calculate the score of the graph. It is also useful for DAG to PAG, which needs to override these rules in order
 * using D-SEP. The R0 and R4 rules are the only ones that cannot be carried out by an examination of the graph but
 * which require additional analysis of the underlying distribution or graph. In addition, several methods have been
 * optimized.
 *
 * @author Erin Korber, June 2004
 * @author Alex Smith, December 2008
 * @author josephramsey
 * @author Choh-Man Teng
 * @version $Id: $Id
 * @see Fci
 * @see GFci
 * @see Rfci
 */
public class FciOrient {

    final TetradLogger logger = TetradLogger.getInstance();

    /**
     * Represents a strategy for examing the data or true graph for R0 and R4. Note that R0 and R4 are the only rulew in
     * this set that require looking at the distribution; all other rules are graphical only.
     */
    private final R0R4Strategy strategy;
    /**
     * Represents a flag indicating whether a change has occurred.
     */
    boolean changeFlag = true;
    /**
     * A boolean variable that determines whether to output verbose logs or not. By default, it is set to false.
     */
    private boolean verbose = false;
    /**
     * Indicates whether the complete rule set is being used or not.
     * <p>
     * If the value is set to true, it means that the complete rule set is being used, which is arrow and tail complete.
     * If the value is set to false, it means that the arrow complete rules only are used. By default, this is set to
     * true.
     */
    private boolean completeRuleSetUsed = true;
    /**
     * The maximum path length variable.
     * <p>
     * This variable represents the maximum length of a discriminating path, or -1 if no maximum length is set.
     */
    private int maxPathLength = -1;
    /**
     * Indicates whether the Discriminating Path Collider Rule should be applied or not.
     */
    private boolean doDiscriminatingPathColliderRule = true;
    /**
     * Indicates whether the discriminating path tail rule should be applied.
     */
    private boolean doDiscriminatingPathTailRule = true;
    /**
     * Stores knowledge.
     */
    private Knowledge knowledge;
    /**
     * The timeout value (in milliseconds) for tests in the discriminating path step. A value of -1 indicates that there
     * is no timeout.
     */
    private long testTimeout = -1;
    /**
     * The allowed colliders for the discriminating path step
     */
    private Set<Triple> allowedColliders;
    /**
     * The graph used for R5 and R9 for the modified Dijkstra shortest path algorithm.
     */
    private R5R9Dijkstra.Graph fullDijkstraGraph = null;

    /**
     * Initializes a new instance of the FciOrient class with the specified R4Strategy.
     *
     * @param strategy The FciOrientDataExaminationStrategy to use for the examination.
     * @throws NullPointerException If the strategy parameter is null.
     * @see R0R4Strategy
     */
    public FciOrient(R0R4Strategy strategy) {
        if (strategy == null) {
            throw new NullPointerException();
        }

        this.strategy = strategy;
        this.knowledge = strategy.getknowledge();
    }

    /**
     * Determines whether an arrowhead is allowed between two nodes in a graph, based on specific conditions.
     *
     * @param x         The first node.
     * @param y         The second node.
     * @param graph     The graph data structure.
     * @param knowledge The knowledge base containing forbidden connections.
     * @return true if an arrowhead is allowed between X and Y, false otherwise.
     */
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
     * Performs FCI orientation on the given graph, including R0 and either the Spirtes or Zhang final orientation
     * rules.
     *
     * @param graph The graph to orient.
     * @return The oriented graph.
     */
    public void orient(Graph graph) {

        if (verbose) {
            this.logger.log("Starting FCI orientation.");
        }

        ruleR0(graph);

        if (this.verbose) {
            logger.log("R0");
        }

        // Step CI D. (Zhang's step R4.)
        finalOrientation(graph);

        if (this.verbose) {
            this.logger.log("Returning graph: " + graph);
        }
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
        strategy.setKnowledge(knowledge);
    }

    /**
     * Checks if the complete rule set is being used.
     *
     * @return true if the complete rule set is being used, false otherwise.
     */
    public boolean isCompleteRuleSetUsed() {
        return this.completeRuleSetUsed;
    }

    /**
     * Sets the flag indicating if the complete rule set is being used.
     *
     * @param completeRuleSetUsed boolean value indicating if the complete rule set is being used
     */
    public void setCompleteRuleSetUsed(boolean completeRuleSetUsed) {
        this.completeRuleSetUsed = completeRuleSetUsed;
    }

    /**
     * Orients unshielded colliders in the graph. (FCI Step C, Zhang's step F3, rule R0.)
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

                if (strategy.isUnshieldedCollider(graph, a, b, c)) {
                    if (!FciOrient.isArrowheadAllowed(a, b, graph, knowledge)) {
                        continue;
                    }

                    if (!FciOrient.isArrowheadAllowed(c, b, graph, knowledge)) {
                        continue;
                    }

                    graph.setEndpoint(a, b, Endpoint.ARROW);
                    graph.setEndpoint(c, b, Endpoint.ARROW);

                    if (this.verbose) {
                        this.logger.log(LogUtilsSearch.colliderOrientedMsg(a, b, c));
                    }

                    this.changeFlag = true;
                }
            }
        }
    }

    /**
     * Orients the graph (in place) according to rules in the graph (FCI step D).
     * <p>
     * Zhang's rules R1-R10.
     *
     * @param graph a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public void finalOrientation(Graph graph) {
        fullDijkstraGraph = null;

        if (this.completeRuleSetUsed) {
            zhangFinalOrientation(graph);
        } else {
            spirtesFinalOrientation(graph);
        }
    }

    /**
     * Iteratively applies rules (in place) to orient the Spirtes final orientation rules in the graph. These are arrow
     * complete.
     *
     * @param graph The graph containing the sprites.
     */
    private void spirtesFinalOrientation(Graph graph) {
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
                ruleR4(graph);
                firstTime = false;
            }

            if (this.verbose) {
                logger.log("Epoch");
            }
        }
    }

    /**
     * Applies Zhang's final orientation algorithm (in place) to the given graph using the rules R1-R10. These are arrow
     * and tail complete.
     *
     * @param graph the graph to apply the final orientation algorithm to
     */
    private void zhangFinalOrientation(Graph graph) {
        this.changeFlag = true;
        boolean firstTime = true;

        while (this.changeFlag && !Thread.currentThread().isInterrupted()) {
            this.changeFlag = false;
            rulesR1R2cycle(graph);
            ruleR3(graph);

            // R4 requires an arrow orientation.
            if (this.changeFlag || (firstTime && !this.knowledge.isEmpty())) {
                ruleR4(graph);
                firstTime = false;
            }

            if (this.verbose) {
                logger.log("Epoch");
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

    /**
     * Apply rules R1 and R2 in cycles for a given graph.
     *
     * @param graph The graph to apply the rules on.
     */
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

                // choice gen doesn't do diff orders, so must switch A & C around.
                ruleR1(A, B, C, graph);
                ruleR1(C, B, A, graph);
                ruleR2(A, B, C, graph);
                ruleR2(C, B, A, graph);
            }
        }
    }

    /**
     * Changes the orientation of an edge in the graph according to Rule R1. If node 'a' is not adjacent to node 'c',
     * then: - If the endpoint of edge 'a' -&gt; 'b' is an arrow and the endpoint of edge 'c' -&gt; 'b' is a circle, and
     * - Arrowhead is allowed between node 'b' and 'c' in the given graph, then changes the endpoint of edge 'c' -&gt;
     * 'b' to tail and the endpoint of edge 'b' -&gt; 'c' to arrow. If 'verbose' flag is true, logs a message about the
     * change. Sets 'changeFlag' to true.
     *
     * @param a     the first node in the edge
     * @param b     the second node in the edge
     * @param c     the third node in the edge
     * @param graph the graph containing the edges and nodes
     */
    public void ruleR1(Node a, Node b, Node c, Graph graph) {
        if (graph.isAdjacentTo(a, c)) {
            return;
        }

        if (graph.getEndpoint(a, b) == Endpoint.ARROW && graph.getEndpoint(c, b) == Endpoint.CIRCLE) {
            if (!FciOrient.isArrowheadAllowed(b, c, graph, knowledge)) {
                return;
            }

            graph.setEndpoint(c, b, Endpoint.TAIL);
            graph.setEndpoint(b, c, Endpoint.ARROW);

            if (this.verbose) {
                this.logger.log(LogUtilsSearch.edgeOrientedMsg("R1: Away from collider", graph.getEdge(b, c)));
            }

            this.changeFlag = true;
        }
    }

    /**
     * Sets the endpoint of node `a` and node `c` in the given graph to `Endpoint.ARROW` if the following conditions
     * hold: 1. Node `a` is adjacent to node `c` in the graph. 2. The endpoint of the edge between node `a` and node `c`
     * is `Endpoint.CIRCLE`. 3. The endpoints of the edges between node `a` and node `b`, and between node `b` and node
     * `c` are both `Endpoint.ARROW`. 4. Either the endpoint of the edge between node `b` and node `a` is
     * `Endpoint.TAIL` or the endpoint of the edge between node `c` and node `b` is `Endpoint.TAIL`. 5. The arrowhead is
     * allowed between node `a` and node `c` in the given graph and knowledge.
     *
     * @param a     the first node
     * @param b     the intermediate node
     * @param c     the last node
     * @param graph the graph in which the nodes exist
     */
    public void ruleR2(Node a, Node b, Node c, Graph graph) {
        if ((graph.isAdjacentTo(a, c)) && (graph.getEndpoint(a, c) == Endpoint.CIRCLE)) {
            if ((graph.getEndpoint(a, b) == Endpoint.ARROW && graph.getEndpoint(b, c) == Endpoint.ARROW) && (graph.getEndpoint(b, a) == Endpoint.TAIL)
                || (graph.getEndpoint(a, b) == Endpoint.ARROW && graph.getEndpoint(b, c) == Endpoint.ARROW && graph.getEndpoint(c, b) == Endpoint.TAIL)) {

                if (!FciOrient.isArrowheadAllowed(a, c, graph, knowledge)) {
                    return;
                }

                graph.setEndpoint(a, c, Endpoint.ARROW);

                if (this.verbose) {
                    this.logger.log(LogUtilsSearch.edgeOrientedMsg("R2: Away from ancestor", graph.getEdge(a, c)));
                }

                this.changeFlag = true;
            }
        }
    }

    /**
     * Implements the double-triangle orientation rule, which states that if D*-oB, A*-&gt;B&lt;-*C and A*-oDo-*C, and
     * !adj(a, c), D*-oB, then D*->B.
     * <p>
     * This is Zhang's rule R3.
     *
     * @param graph a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public void ruleR3(Graph graph) {
        List<Node> nodes = graph.getNodes();

        for (Node b : nodes) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            List<Node> adj = new ArrayList<>(graph.getAdjacentNodes(b));

            ChoiceGenerator gen = new ChoiceGenerator(adj.size(), 3);
            int[] choice;

            while ((choice = gen.next()) != null) {
                List<Node> B = GraphUtils.asList(choice, adj);

                Node a = B.get(0);
                Node c = B.get(1);
                Node d = B.get(2);

                if (graph.isAdjacentTo(a, c)) {
                    continue;
                }

                if (!graph.isAdjacentTo(a, d)) {
                    continue;
                }

                if (!graph.isAdjacentTo(c, d)) {
                    continue;
                }

                if (graph.isDefCollider(a, b, c) && graph.getEndpoint(a, c) == Endpoint.CIRCLE && graph.getEndpoint(c, d) == Endpoint.CIRCLE) {
                    if (!FciOrient.isArrowheadAllowed(d, b, graph, knowledge)) {
                        continue;
                    }

                    graph.setEndpoint(d, b, Endpoint.ARROW);

                    if (this.verbose) {
                        this.logger.log(LogUtilsSearch.edgeOrientedMsg("R3: Double triangle", graph.getEdge(d, b)));
                    }

                    this.changeFlag = true;
                }
            }
        }
    }

    /**
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
     * @param graph a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public void ruleR4(Graph graph) {

        TetradLogger.getInstance().log("R4: Discriminating path orientation started.");

        List<Pair<DiscriminatingPath, Boolean>> allResults = new ArrayList<>();

        if (testTimeout == -1) {
            while (true) {
                List<Callable<Pair<DiscriminatingPath, Boolean>>> tasks = getDiscriminatingPathTasks(graph, allowedColliders);
                if (tasks.isEmpty()) break;

                List<Pair<DiscriminatingPath, Boolean>> results = tasks.stream().map(task -> {
                    try {
                        return task.call();
                    } catch (Exception e) {
                        return null;
                    }
                }).toList();

                allResults.addAll(results);

                boolean existsTrue = false;

                for (Pair<DiscriminatingPath, Boolean> result : results) {
                    if (result != null && result.getRight()) {
                        existsTrue = true;
                        break;
                    }
                }

                if (!existsTrue) {
                    break;
                }
            }
        } else if (testTimeout > 0) {
            while (true) {
                List<Callable<Pair<DiscriminatingPath, Boolean>>> tasks = getDiscriminatingPathTasks(graph, allowedColliders);

                List<Pair<DiscriminatingPath, Boolean>> results = tasks.parallelStream()
                        .map(task -> GraphSearchUtils.runWithTimeout(task, testTimeout, TimeUnit.MILLISECONDS))
                        .toList();

                allResults.addAll(results);

                boolean existsTrue = false;

                for (Pair<DiscriminatingPath, Boolean> result : results) {
                    if (result != null && result.getRight()) {
                        existsTrue = true;
                        break;
                    }
                }

                if (!existsTrue) {
                    break;
                }
            }
        } else {
            throw new IllegalArgumentException("testTimeout must be greater than 0 or -1");
        }

        for (Pair<DiscriminatingPath, Boolean> result : allResults) {
            if (result != null && result.getRight()) {
                if (verbose) {
                    DiscriminatingPath left = result.getLeft();
                    TetradLogger.getInstance().log("R4: Discriminating path oriented: " + left);

                    Node a = left.getA();
                    Node b = left.getB();
                    Node c = left.getC();

                    TetradLogger.getInstance().log("    Oriented as: " + GraphUtils.pathString(graph, a, b, c));
                }

                this.changeFlag = true;
            }
        }

        TetradLogger.getInstance().log("R4: Discriminating path orientation finished.");
    }

    /**
     * Makes a list of tasks for the discriminating path orientation step based on the current graph.
     *
     * @param graph           the graph
     * @param allowedCollders the allowed colliders
     * @return the list of tasks
     */
    private @NotNull List<Callable<Pair<DiscriminatingPath, Boolean>>> getDiscriminatingPathTasks(Graph graph, Set<Triple> allowedCollders) {
        Set<DiscriminatingPath> discriminatingPaths = listDiscriminatingPaths(graph);

        List<Callable<Pair<DiscriminatingPath, Boolean>>> tasks = new ArrayList<>();
        strategy.setAllowedColliders(allowedCollders);

        for (DiscriminatingPath discriminatingPath : discriminatingPaths) {
            tasks.add(() -> strategy.doDiscriminatingPathOrientation(discriminatingPath, graph));
        }

        return tasks;
    }

    /**
     * Lists all the discriminating paths in the given graph.
     *
     * @param graph the graph to analyze
     * @return a set of discriminating paths found in the graph
     */
    private Set<DiscriminatingPath> listDiscriminatingPaths(Graph graph) {
        Set<DiscriminatingPath> discriminatingPaths = new HashSet<>();

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

                        // Some discriminating path orientation may already have been made.
                        if (graph.getEndpoint(c, b) != Endpoint.CIRCLE) {
                            continue;
                        }

                        if (graph.getEndpoint(b, c) != Endpoint.ARROW) {
                            continue;
                        }

                        discriminatingPathOrient(a, b, c, graph, discriminatingPaths);
                    }
                }
            }
        }

        return discriminatingPaths;
    }

    /**
     * A method to search "back from a" to find a discriminating path. It is called with a reachability list (first
     * consisting only of a). This is breadth-first, using "reachability" concept from Geiger, Verma, and Pearl 1990.
     * The body of a discriminating path consists of colliders that are parents of c.
     *
     * @param a     a {@link Node} object
     * @param b     a {@link Node} object
     * @param c     a {@link Node} object
     * @param graph a {@link Graph} object
     */
    private void discriminatingPathOrient(Node a, Node b, Node c, Graph graph, Set<DiscriminatingPath> discriminatingPaths) {
        Queue<Node> Q = new ArrayDeque<>();
        Set<Node> V = new HashSet<>();
        Map<Node, Node> previous = new HashMap<>();

        Q.offer(a);
        V.add(a);
        V.add(b);

        previous.put(b, null);
        previous.put(a, b);

        while (!Q.isEmpty()) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            Node t = Q.poll();

            List<Node> nodesInTo = graph.getNodesInTo(t, Endpoint.ARROW);

            D:
            for (Node e : nodesInTo) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                if (V.contains(e)) {
                    continue;
                }

                previous.put(e, t);

                LinkedList<Node> path = new LinkedList<>();

                Node d = e;

                while (previous.get(d) != null) {
                    path.addLast(d);
                    d = previous.get(d);
                }

                if (maxPathLength != -1 && path.size() - 3 > maxPathLength) {
                    continue;
                }

                for (int i = 0; i < path.size() - 2; i++) {
                    Node x = path.get(i);
                    Node y = path.get(i + 1);
                    Node z = path.get(i + 2);

                    if (!graph.isDefCollider(x, y, z) || !graph.isParentOf(y, c)) {
                        continue D;
                    }
                }

                if (!graph.isAdjacentTo(e, c)) {
                    LinkedList<Node> colliderPath = new LinkedList<>(path);
                    colliderPath.remove(e);
                    colliderPath.remove(b);

                    DiscriminatingPath discriminatingPath = new DiscriminatingPath(e, a, b, c, colliderPath);
                    discriminatingPaths.add(discriminatingPath);
                }

                if (!V.contains(e)) {
                    Q.offer(e);
                    V.add(e);
                }
            }
        }
    }

    /**
     * Implements Zhang's rule R5, orient circle undirectedPaths: for any Ao-oB, if there is an uncovered circle path u
     * = [A,C,...,D,B] such that A,D nonadjacent and B,C nonadjacent, then A---B and orient every edge on u undirected.
     *
     * @param graph a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public void ruleR5(Graph graph) {
        if (fullDijkstraGraph == null) {
            fullDijkstraGraph = new R5R9Dijkstra.Graph(graph, true);
        }

        for (Edge edge : graph.getEdges()) {
            if (Edges.isNondirectedEdge(edge)) {
                Node x = edge.getNode1();
                Node y = edge.getNode2();

                Map<Node, Node> predecessors = R5R9Dijkstra.distances(fullDijkstraGraph, x, y).getRight();
                List<Node> path = FciOrientDijkstra.getPath(predecessors, x, y);

                if (path == null) {
                    continue;
                }

                // We know u is as required: R5 applies!
                graph.setEndpoint(x, y, Endpoint.TAIL);
                graph.setEndpoint(y, x, Endpoint.TAIL);

                for (int i = 0; i < path.size() - 1; i++) {
                    Node w = path.get(i);
                    Node z = path.get(i + 1);

                    graph.setEndpoint(w, z, Endpoint.TAIL);
                    graph.setEndpoint(z, w, Endpoint.TAIL);
                }

                if (verbose) {
                    String s = GraphUtils.pathString(graph, path, false);
                    this.logger.log("R5: Orient circle path, " + edge + " " + s);
                }

                this.changeFlag = true;
            }
        }
    }

    /**
     * Implements Zhang's rules R6 and R7, applies them over the graph once. Orient single tails. R6: If A---Bo-*C then
     * A---B--*C. R7: If A--oBo-*C and A,C nonadjacent, then A--oB--*C
     *
     * @param graph a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public void ruleR6R7(Graph graph) {
        ruleR6(graph);
        ruleR7(graph);
    }

    private void ruleR6(Graph graph) {
        for (Edge edge : graph.getEdges()) {
            if (!Edges.isUndirectedEdge(edge)) {
                continue;
            }

            {
                Node b = edge.getNode2();

                for (Node c : graph.getAdjacentNodes(b)) {
                    if (graph.getEndpoint(c, b) != Endpoint.CIRCLE) {
                        continue;
                    }

                    graph.setEndpoint(c, b, Endpoint.TAIL);
                    changeFlag = true;
                }
            }

            {
                Node b = edge.getNode1();

                for (Node c : graph.getAdjacentNodes(b)) {
                    if (graph.getEndpoint(c, b) != Endpoint.CIRCLE){
                        continue;
                    }

                    graph.setEndpoint(c, b, Endpoint.TAIL);
                    changeFlag = true;

                    if (verbose) {
                        this.logger.log(LogUtilsSearch.edgeOrientedMsg("R6: Single tails (tail)", graph.getEdge(c, b)));
                    }
                }
            }
        }
    }

    private void ruleR7(Graph graph) {
        for (Edge edge : graph.getEdges()) {
            {
                Node a = edge.getNode1();
                Node b = edge.getNode2();

                if (graph.getEndpoint(a, b) != Endpoint.CIRCLE) {
                    continue;
                }

                for (Node c : graph.getAdjacentNodes(b)) {
                    if (c == a) continue;

                    if (graph.getEndpoint(c, b) != Endpoint.CIRCLE) {
                        continue;
                    }

                    if (graph.isAdjacentTo(a, c)) {
                        continue;
                    }

                    graph.setEndpoint(c, b, Endpoint.TAIL);
                    changeFlag = true;

                    if (verbose) {
                        this.logger.log(LogUtilsSearch.edgeOrientedMsg("R7: Single tails (tail)", graph.getEdge(c, b)));
                    }
                }
            }
        }
    }

    /**
     * Implements Zhang's rules R8, R9, R10, applies them over the graph once. Orient arrow tails. I.e., tries R8, R9,
     * and R10 in that sequence on each Ao-&gt;C in the graph.
     *
     * @param graph a {@link edu.cmu.tetrad.graph.Graph} object
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
     * Applies Zhang's rule R8 to a pair of nodes A and C which are assumed to be such that Ao->C.
     * <p>
     * R8: If Ao->C and A-->B-->C or A--oB-->C, then A-->C.
     *
     * @param a     The node A.
     * @param c     The node C.
     * @param graph a {@link edu.cmu.tetrad.graph.Graph} object
     * @return Whether R8 was successfully applied.
     */
    public boolean ruleR8(Node a, Node c, Graph graph) {

        // We are aiming to orient the tails on certain partially oriented edges a o-> c, so we first
        // need to make sure we have such an edge.
        Edge edge = graph.getEdge(a, c);

        if (edge == null) {
            return false;
        }

        if (!edge.equals(Edges.partiallyOrientedEdge(a, c))) {
            return false;
        }

        // Pick b from the common adjacents of a and c.
        List<Node> common = new ArrayList<>(graph.getAdjacentNodes(a));
        common.retainAll(graph.getAdjacentNodes(c));

        for (Node b : common) {
            boolean orient = false;

            if (graph.getEndpoint(b, a) == Endpoint.TAIL && graph.getEndpoint(a, b) == Endpoint.ARROW
                && graph.getEndpoint(c, b) == Endpoint.TAIL && graph.getEndpoint(b, c) == Endpoint.ARROW) {
                orient = true;
            } else if (graph.getEndpoint(b, a) == Endpoint.TAIL && graph.getEndpoint(a, b) == Endpoint.CIRCLE
                       && graph.getEndpoint(c, b) == Endpoint.TAIL && graph.getEndpoint(b, c) == Endpoint.ARROW) {
                orient = true;
            }

            if (orient) {
                graph.setEndpoint(c, a, Endpoint.TAIL);

                if (verbose) {
                    this.logger.log(LogUtilsSearch.edgeOrientedMsg("R8: ", graph.getEdge(c, a)));
                }

                this.changeFlag = true;
                return true;
            }
        }

        return false;
    }

    /**
     * Applies Zhang's rule R9 to a pair of nodes A and C which are assumed to be such that Ao->C.
     * <p>
     * R9: If Ao-&gt;C and there is an uncovered p.d. path u=&lt;A,B,..,C&gt; such that C,B nonadjacent, then A--&gt;C.
     *
     * @param a     The node A.
     * @param c     The node C.
     * @param graph a {@link edu.cmu.tetrad.graph.Graph} object
     * @return Whether R9 was succesfully applied.
     */
    public boolean ruleR9(Node a, Node c, Graph graph) {

        // We are aiming to orient the tails on certain partially oriented edges a o-> c, so we first
        // need to make sure we have such an edge.
        Edge edge = graph.getEdge(a, c);

        if (edge == null) {
            return false;
        }

        if (!edge.equals(Edges.partiallyOrientedEdge(a, c))) {
            return false;
        }

        // Now that we know we have one, we need to determine whether there is a partially oriented (i.e.,
        // semi-directed) path from a to c other than a o-> c itself and with at least one edge out of a.
        if (fullDijkstraGraph == null) {
            fullDijkstraGraph = new R5R9Dijkstra.Graph(graph, true);
        }

        Node x = edge.getNode1();
        Node y = edge.getNode2();

        Map<Node, Node> predecessors = R5R9Dijkstra.distances(fullDijkstraGraph, x, y).getRight();
        List<Node> path = FciOrientDijkstra.getPath(predecessors, x, y);

        if (path == null) {
            return false;
        }

        // We know u is as required: R9 applies!
        graph.setEndpoint(c, a, Endpoint.TAIL);

        if (verbose) {
            this.logger.log(LogUtilsSearch.edgeOrientedMsg("R9: ", graph.getEdge(c, a)));
        }

        this.changeFlag = true;
        return true;
    }

    /**
     * Orient the edges of a graph based on the given knowledge.
     *
     * @param bk        The knowledge containing forbidden and required edges.
     * @param graph     The graph to be oriented.
     * @param variables The list of nodes in the graph.
     */
    public void fciOrientbk(Knowledge bk, Graph graph, List<Node> variables) {
        if (verbose) {
            this.logger.log("Starting BK Orientation.");
        }

        for (Iterator<KnowledgeEdge> it = bk.forbiddenEdgesIterator(); it.hasNext(); ) {
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

            if (!FciOrient.isArrowheadAllowed(to, from, graph, knowledge)) {
                return;
            }

            // Orient to*->from
            graph.setEndpoint(to, from, Endpoint.ARROW);

            if (verbose) {
                this.logger.log(LogUtilsSearch.edgeOrientedMsg("Knowledge", graph.getEdge(to, from)));
            }

            this.changeFlag = true;
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

            if (!FciOrient.isArrowheadAllowed(from, to, graph, knowledge)) {
                return;
            }

            graph.setEndpoint(to, from, Endpoint.TAIL);
            graph.setEndpoint(from, to, Endpoint.ARROW);

            if (verbose) {
                this.logger.log(LogUtilsSearch.edgeOrientedMsg("Knowledge", graph.getEdge(from, to)));
            }

            this.changeFlag = true;
        }

        if (verbose) {
            this.logger.log("Finishing BK Orientation.");
        }
    }

    /**
     * Returns the maximum path length, or -1 if unlimited.
     *
     * @return the maximum path length
     */
    public int getMaxPathLength() {
        return this.maxPathLength;
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
     * Applies Zhang's rule R10 to a pair of nodes A and C which are assumed to be such that Ao->C.
     * <p>
     * R10: If Ao-&gt;C, B--&gt;C&lt;--D, there is an uncovered p.d. path u1=&lt;A,M,...,B&gt; and an uncovered p.d.
     * path u2= &lt;A,N,...,D&gt; with M != N and M,N nonadjacent then A--&gt;C.
     *
     * @param a     The node A.
     * @param c     The node C.
     * @param graph a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public void ruleR10(Node a, Node c, Graph graph) {

        // We are aiming to orient the tails on certain partially oriented edges a o-> c, so we first
        // need to make sure we have such an edge.
        Edge edge = graph.getEdge(a, c);

        if (edge == null) {
            return;
        }

        if (!edge.equals(Edges.partiallyOrientedEdge(a, c))) {
            return;
        }

        List<Node> adj1 = graph.getAdjacentNodes(a);
        List<Node> filtered1 = new ArrayList<>();

        for (Node n : adj1) {
            Node other = Edges.traverseSemiDirected(a, graph.getEdge(a, n));
            if (other != null && other.equals(n)) {
                filtered1.add(n);
            }
        }

        for (Node mu : filtered1) {
            for (Node omega : filtered1) {
                if (mu.equals(omega)) continue;
                if (graph.isAdjacentTo(mu, omega)) continue;

                List<Node> adj2 = graph.getNodesInTo(c, Endpoint.ARROW);
                List<Node> filtered2 = new ArrayList<>();

                for (Node n : adj2) {
                    if (graph.getEdges(n, c).equals(Edges.directedEdge(n, c))) {
                        Node other = Edges.traverseSemiDirected(n, graph.getEdge(n, c));
                        if (other != null && other.equals(n)) {
                            filtered2.add(n);
                        }
                    }

                    for (Node beta : filtered2) {
                        for (Node theta : filtered2) {
                            if (beta.equals(theta)) continue;
                            if (graph.isAdjacentTo(mu, omega)) continue;

                            // Now we have our beta, theta, mu, and omega for R10. Next we need to try to find
                            // a semidirected path p1 starting with <a, mu>, and ending with beta, and a path
                            // p2 starting with <a, omega> and ending with theta.

                            if (graph.paths().existsSemiDirectedPath(mu, beta) && graph.paths().existsSemiDirectedPath(omega, theta)) {

                                // We know we have the paths p1 and p2 as required: R10 applies!
                                graph.setEndpoint(c, a, Endpoint.TAIL);

                                if (verbose) {
                                    this.logger.log(LogUtilsSearch.edgeOrientedMsg("R10: ", graph.getEdge(c, a)));
                                }

                                this.changeFlag = true;
                                return;
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Gets the current value of the verbose flag.
     *
     * @return true if the verbose flag is set, false otherwise
     */
    public boolean isVerbose() {
        return verbose;
    }

    /**
     * Sets whether verbose output is printed.
     *
     * @param verbose True, if so.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Sets the timeout for running tests.
     *
     * @param testTimeout the timeout value in milliseconds
     */
    public void setTestTimeout(long testTimeout) {
        this.testTimeout = testTimeout;
    }

    /**
     * Sets the allowed colliders for this object. These are passed to R4 is the set of unshielded colliders for the
     * model is to be restricted. TODO Think this through again.
     *
     * @param allowedColliders the set of colliders allowed to interact with this object
     */
    public void setAllowedColliders(Set<Triple> allowedColliders) {
        this.allowedColliders = allowedColliders;
    }

    /**
     * Returns the initial allowed colliders based on the current strategy. These are the unshielded colliders from R4's
     * first run.
     * <p>
     * TODO think this through again.
     *
     * @return a collection of Triple objects representing the initial allowed colliders.
     */
    public Collection<Triple> getInitialAllowedColliders() {
        return strategy.getInitialAllowedColliders();
    }

    /**
     * Sets the initial allowed colliders for the strategy.
     * <p>
     * TODO: Think this thorugh again.
     *
     * @param initialAllowedColliders The set of initial allowed colliders.
     */
    public void setInitialAllowedColliders(HashSet<Triple> initialAllowedColliders) {
        strategy.setInitialAllowedColliders(initialAllowedColliders);
    }
}
