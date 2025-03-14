/// ////////////////////////////////////////////////////////////////////////////
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
import edu.cmu.tetrad.search.FciOrientDijkstra;
import edu.cmu.tetrad.util.ChoiceGenerator;
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
 * to use D-SEP. The R0 and R4 rules are the only ones that cannot be carried out by an examination of the graph but
 * which require additional analysis of the underlying distribution or graph. In addition, several methods have been
 * optimized.
 *
 * @author Erin Korber, June 2004
 * @author Alex Smith, December 2008
 * @author josephramsey 2024-8-21
 * @author Choh-Man Teng
 * @version $Id: $Id
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
    private int maxDiscriminatingPathLength = -1;
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
     * Indicates whether the discriminating path step should be run in parallel.
     */
    private boolean parallel = true;
    /**
     * The endpoint strategy to use for setting endpoints.
     */
    private SetEndpointStrategy endpointStrategy = new DefaultSetEndpointStrategy();
    /**
     * Indicates whether to run R4 or not.
     */
    private boolean doR4 = true;

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
     * Lists all the discriminating paths in the given graph.
     *
     * @param graph                       the graph to analyze
     * @param maxDiscriminatingPathLength the maximum length of a discriminating path
     * @param checkEcNonadjacency         whether to check for EC nonadjacency
     * @return a set of discriminating paths found in the graph
     */
    public static Set<DiscriminatingPath> listDiscriminatingPaths(Graph graph, int maxDiscriminatingPathLength, boolean checkEcNonadjacency) {
        Set<DiscriminatingPath> discriminatingPaths = new HashSet<>();

        List<Node> nodes = graph.getNodes();

        //  *         B
        // *         *o           * is either an arrowhead or a circle; note B *-> A is not a condition in Zhang's rule
        // *        /  \
        // *       v    *
        // * E....A --> C
        for (Node a : nodes) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            for (Node c : graph.getAdjacentNodes(a)) {
                discriminatingPaths.addAll(listDiscriminatingPaths(graph, a, c, maxDiscriminatingPathLength, checkEcNonadjacency));
            }
        }

        return discriminatingPaths;
    }

    /**
     * Lists the discriminating paths for &lt;w, y&gt; in the graph.
     *
     * @param graph                       The graph.
     * @param w                           The first node.
     * @param y                           The second node.
     * @param maxDiscriminatingPathLength The maximum length of w discriminating path.
     * @param checkEcNonadjacency         Whether to check for EC nonadjacency.
     * @return The set of discriminating paths for &lt;w, y&gt;.
     */
    public static Set<DiscriminatingPath> listDiscriminatingPaths(Graph graph, Node w, Node y,
                                                                  int maxDiscriminatingPathLength, boolean checkEcNonadjacency) {
        Set<DiscriminatingPath> discriminatingPaths = new HashSet<>();

        if (checkEcNonadjacency) {
            if (!graph.isParentOf(w, y)) {
                return discriminatingPaths;
            }
        } else {
            if (graph.getEndpoint(y, w) == Endpoint.ARROW) {
                return discriminatingPaths;
            }
        }

        List<Node> vnodes = graph.getAdjacentNodes(y);
        vnodes.retainAll(graph.getAdjacentNodes(w));

        for (Node v : vnodes) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            // Here we simply assert that W and Y are adjacent to V; we let the DiscriminatingPath class determine
            // whether the path is valid.

            if (w == y) continue;

            if (checkEcNonadjacency) {
                if (!graph.isParentOf(w, y)) continue;
            } else {
                if (graph.getEndpoint(y, w) == Endpoint.ARROW) continue;
            }

            // We ignore any discriminating paths that do not require orientation.
            if (graph.getEndpoint(y, v) != Endpoint.CIRCLE) {
                continue;
            }

            if (graph.getEndpoint(v, y) != Endpoint.ARROW) {
                continue;
            }

            discriminatingPathBfs(w, v, y, graph, discriminatingPaths, maxDiscriminatingPathLength, checkEcNonadjacency);
        }

        return discriminatingPaths;
    }

    /**
     * A method to search "back from w" to find w discriminating path. It is called with w reachability list (first
     * consisting only of w). This is breadth-first, using "reachability" concept from Geiger, Verma, and Pearl 1990.
     * The body of w discriminating path consists of colliders that are parents of y.
     *
     * @param w                   w {@link Node} object
     * @param v                   w {@link Node} object
     * @param y                   w {@link Node} object
     * @param graph               w {@link Graph} object
     * @param checkEcNonadjacency Whether to check for EC nonadjacency
     */
    private static void discriminatingPathBfs(Node w, Node v, Node y, Graph graph, Set<DiscriminatingPath> discriminatingPaths,
                                              int maxDiscriminatingPathLength, boolean checkEcNonadjacency) {
        Queue<Node> Q = new ArrayDeque<>();
        Set<Node> V = new HashSet<>();
        Map<Node, Node> previous = new HashMap<>();

        Q.offer(w);
        V.add(w);
        V.add(v);

        previous.put(w, null);
        previous.put(v, w);

        while (!Q.isEmpty()) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            Node t = Q.poll();

            List<Node> nodesInTo = graph.getNodesInTo(t, Endpoint.ARROW);

            for (Node x : nodesInTo) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                if (V.contains(x)) {
                    continue;
                }

                previous.put(x, t);

                // The collider path should be all nodes between E and C.
                LinkedList<Node> colliderPath = new LinkedList<>();
                Node d = x;

                while ((d = previous.get(d)) != null) {
                    if (d != x) {
                        colliderPath.addFirst(d);
                    }
                }

                if (maxDiscriminatingPathLength != -1 && colliderPath.size() > maxDiscriminatingPathLength) {
                    continue;
                }

                DiscriminatingPath discriminatingPath = new DiscriminatingPath(x, w, v, y, colliderPath, checkEcNonadjacency);

                if (discriminatingPath.existsIn(graph)) {
                    discriminatingPaths.add(discriminatingPath);
                }

                if (!V.contains(x)) {
                    Q.offer(x);
                    V.add(x);
                }
            }
        }
    }

    /**
     * Performs FCI orientation on the given graph, including R0 and either the Spirtes or Zhang final orientation
     * rules.
     *
     * @param graph             The graph to orient.
     * @param unshieldedTriples The set of unshielded triples oriented by R0. This set is updated with new triples.
     */
    public void orient(Graph graph, Set<Triple> unshieldedTriples) {

        if (verbose) {
            this.logger.log("Starting FCI orientation.");
        }

        ruleR0(graph, unshieldedTriples);

        if (this.verbose) {
            logger.log("R0");
        }

        // Step CI D. (Zhang's step R4.)
        finalOrientation(graph);
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
     * @param graph             The graph to orient.
     * @param unshieldedTriples The set of unshielded triples oriented by R0. This set is updated with new triples.
     */
    public void ruleR0(Graph graph, Set<Triple> unshieldedTriples) {
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

                    setEndpoint(graph, a, b, Endpoint.ARROW);
                    setEndpoint(graph, c, b, Endpoint.ARROW);

                    unshieldedTriples.add(new Triple(a, b, c));

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
                ruleR6(graph);
                ruleR7(graph);
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

                // choice generator doesn't do different orders, so we must switch A & C around
                ruleR1(A, B, C, graph);
                ruleR1(C, B, A, graph);
                ruleR2(A, B, C, graph);
                ruleR2(C, B, A, graph);
            }
        }
    }

    /**
     * R1 If α ∗→ β o−−∗ γ, and α and γ are not adjacent, then orient the triple as α ∗→ β → γ.
     *
     * @param a     α
     * @param b     β
     * @param c     γ
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

            setEndpoint(graph, c, b, Endpoint.TAIL);
            setEndpoint(graph, b, c, Endpoint.ARROW);

            if (this.verbose) {
                this.logger.log(LogUtilsSearch.edgeOrientedMsg("R1: Away from collider", graph.getEdge(b, c)));
            }

            this.changeFlag = true;
        }
    }

    /**
     * R2 If α → β ∗→ γ or α ∗→ β → γ, and α ∗−o γ, then orient α ∗−o γ as α ∗→ γ.
     *
     * @param a     α
     * @param b     β
     * @param c     γ
     * @param graph the graph in which the nodes exist
     */
    public void ruleR2(Node a, Node b, Node c, Graph graph) {
        if ((graph.isAdjacentTo(a, c)) && (graph.getEndpoint(a, c) == Endpoint.CIRCLE)) {
            if ((graph.getEndpoint(a, b) == Endpoint.ARROW && graph.getEndpoint(b, c) == Endpoint.ARROW) && (graph.getEndpoint(b, a) == Endpoint.TAIL)
                || (graph.getEndpoint(a, b) == Endpoint.ARROW && graph.getEndpoint(b, c) == Endpoint.ARROW && graph.getEndpoint(c, b) == Endpoint.TAIL)) {

                if (!FciOrient.isArrowheadAllowed(a, c, graph, knowledge)) {
                    return;
                }

                setEndpoint(graph, a, c, Endpoint.ARROW);

                if (this.verbose) {
                    this.logger.log(LogUtilsSearch.edgeOrientedMsg("R2: Away from ancestor", graph.getEdge(a, c)));
                }

                this.changeFlag = true;
            }
        }
    }

    /**
     * R3 If α ∗→ β ←∗ γ, α ∗−o θ o−∗ γ, α and γ are not adjacent, and θ ∗−o β, then orient θ ∗−o β as θ ∗→ β.
     *
     * @param graph a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public void ruleR3(Graph graph) {

        // a = α, b = β, c = γ, d = θ
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

                if (!graph.isAdjacentTo(a, c) && graph.isAdjacentTo(a, d) && graph.isAdjacentTo(c, d)) {
                    if (graph.isDefCollider(a, b, c) && graph.getEndpoint(a, d) == Endpoint.CIRCLE && graph.getEndpoint(c, d) == Endpoint.CIRCLE
                        && graph.getEndpoint(d, b) == Endpoint.CIRCLE) {
                        if (!FciOrient.isArrowheadAllowed(d, b, graph, knowledge)) {
                            continue;
                        }

                        setEndpoint(graph, d, b, Endpoint.ARROW);

                        if (this.verbose) {
                            this.logger.log(LogUtilsSearch.edgeOrientedMsg("R3: Double triangle", graph.getEdge(d, b)));
                        }

                        this.changeFlag = true;
                    }
                }
            }
        }
    }

    /**
     * R4 If u = &lt;θ ,...,α,β,γ&gt; is a discriminating path between θ and γ for β, and β o−−∗ γ; then if β ∈
     * Sepset(θ,γ), orient β o−−∗ γ as β → γ; otherwise orient the triple &lt;α,β,γ&gt; as α ↔ β ↔ γ.
     *
     * @param graph a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public void ruleR4(Graph graph) {

        if (!doR4) {
            return;
        }

        if (verbose) {
            TetradLogger.getInstance().log("R4: Discriminating path orientation started.");
        }

        List<Pair<DiscriminatingPath, Boolean>> allResults = new ArrayList<>();

        int testTimeout = this.testTimeout == -1 ? Integer.MAX_VALUE : (int) this.testTimeout;

        // Parallel is the default.
        if (parallel) {
            while (true) {
                List<Callable<Pair<DiscriminatingPath, Boolean>>> tasks = getDiscriminatingPathTasks(graph, allowedColliders);

                List<Pair<DiscriminatingPath, Boolean>> results = tasks.stream()
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
            while (true) {
                List<Callable<Pair<DiscriminatingPath, Boolean>>> tasks = getDiscriminatingPathTasks(graph, allowedColliders);
                if (tasks.isEmpty()) break;

                List<Pair<DiscriminatingPath, Boolean>> results = tasks.stream().map(task -> {
                    try {
                        return GraphSearchUtils.runWithTimeout(task, testTimeout, TimeUnit.MILLISECONDS);
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

        }

        for (Pair<DiscriminatingPath, Boolean> result : allResults) {
            if (result != null && result.getRight()) {
//                if (verbose) {
//                    DiscriminatingPath left = result.getLeft();
//                    TetradLogger.getInstance().log("R4: Discriminating path oriented: " + left);
//
//                    Node a = left.getA();
//                    Node b = left.getB();
//                    Node c = left.getC();
//
//                    TetradLogger.getInstance().log("    Oriented as: " + GraphUtils.pathString(graph, a, b, c));
//                }

                this.changeFlag = true;
            }
        }

        if (verbose) {
            TetradLogger.getInstance().log("R4: Discriminating path orientation finished.");
        }
    }

    /**
     * Makes a list of tasks for the discriminating path orientation step based on the current graph.
     *
     * @param graph           the graph
     * @param allowedCollders the allowed colliders
     * @return the list of tasks
     */
    private @NotNull List<Callable<Pair<DiscriminatingPath, Boolean>>> getDiscriminatingPathTasks(Graph graph, Set<Triple> allowedCollders) {
        Set<DiscriminatingPath> discriminatingPaths = listDiscriminatingPaths(graph, maxDiscriminatingPathLength, true);

        Set<Node> vNodes = new HashSet<>();

        for (DiscriminatingPath discriminatingPath : discriminatingPaths) {
            vNodes.add(discriminatingPath.getV());
        }

        List<Callable<Pair<DiscriminatingPath, Boolean>>> tasks = new ArrayList<>();
        strategy.setAllowedColliders(allowedCollders);

        for (DiscriminatingPath discriminatingPath : discriminatingPaths) {
            tasks.add(() -> strategy.doDiscriminatingPathOrientation(discriminatingPath, graph, vNodes));
        }

        return tasks;
    }

    /**
     * R5 For every (remaining) α o−−o β, if there is an uncovered circle path p = &lt;α,γ,...,θ,β&gt; between α and β
     * s.t. α,θ are not adjacent and β,γ are not adjacent, then orient α o−−o β and every edge on p as undirected edges
     * (--).
     *
     * @param graph the graph to orient.
     */
    public void ruleR5(Graph graph) {

        // We do this by finding a shortest o-o path using Dijkstra's shortest path algorithm. We constrain the algorithm
        // so that the path must be a circle path, there can be no length 1 or length 2 paths, and all nodes on the path
        // are uncovered. We add further constraints so that the path taken together with the x o-o y edge forms an
        // uncovered cyclic circle path.
        R5R9Dijkstra.Graph fullDijkstraGraph = new R5R9Dijkstra.Graph(graph, R5R9Dijkstra.Rule.R5);

        for (Edge edge : graph.getEdges()) {
            if (Edges.isNondirectedEdge(edge)) {
                Node x = edge.getNode1();
                Node y = edge.getNode2();

                // Returns a map from each node to its predecessor in the shortest path. This is needed to reconstruct
                // the path, since the Dijkstra algorithm proper does not pay attention to the path, only to the
                // shortest distances. So we need to record this information.
                Map<Node, Node> predecessors = R5R9Dijkstra.distances(fullDijkstraGraph, x, y).getRight();

                // This reconstructs the path given the predecessor map.
                List<Node> path = FciOrientDijkstra.getPath(predecessors, x, y);

                // If the result is null, there was no path.
                if (path == null) {
                    continue;
                }

                // At this point, we know the uncovered circle path is as required, so R5 applies! We now need to
                // orient all the circles on the path as tails.
                setEndpoint(graph, x, y, Endpoint.TAIL);
                setEndpoint(graph, y, x, Endpoint.TAIL);

                for (int i = 0; i < path.size() - 1; i++) {
                    Node w = path.get(i);
                    Node z = path.get(i + 1);

                    setEndpoint(graph, w, z, Endpoint.TAIL);
                    setEndpoint(graph, z, w, Endpoint.TAIL);
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
     * R6 If α —- β o−−∗ γ (α and γ may or may not be adjacent), then orient β o−−∗ γ as β −−∗ γ.
     *
     * @param graph a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public void ruleR6(Graph graph) {

        // We first look for undirected edges x —- y and the look for γ adjacent to either the x or the
        // y endpoint.

        for (Edge edge : graph.getEdges()) {
            if (!Edges.isUndirectedEdge(edge)) {
                continue;
            }

            {
                Node a = edge.getNode1();
                Node b = edge.getNode2();

                for (Node c : graph.getAdjacentNodes(b)) {
                    if (c != a && graph.getEndpoint(c, b) == Endpoint.CIRCLE) {
                        setEndpoint(graph, c, b, Endpoint.TAIL);
                        changeFlag = true;

                        if (verbose) {
                            this.logger.log(LogUtilsSearch.edgeOrientedMsg("R6: Single tails (tail)", graph.getEdge(c, b)));
                        }
                    }
                }
            }

            {
                Node a = edge.getNode2();
                Node b = edge.getNode1();

                for (Node c : graph.getAdjacentNodes(b)) {
                    if (c != a && graph.getEndpoint(c, b) == Endpoint.CIRCLE) {
                        setEndpoint(graph, c, b, Endpoint.TAIL);
                        changeFlag = true;

                        if (verbose) {
                            this.logger.log(LogUtilsSearch.edgeOrientedMsg("R6: Single tails (tail)", graph.getEdge(c, b)));
                        }
                    }
                }
            }
        }
    }

    /**
     * R7 If α −−o β o−−∗ γ, and α, γ are not adjacent, then orient β o−−∗ γ as β −−∗ γ.
     *
     * @param graph a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public void ruleR7(Graph graph) {
        for (Edge edge : graph.getEdges()) {
            {
                Node a = edge.getNode1();
                Node b = edge.getNode2();

                if (graph.getEndpoint(a, b) == Endpoint.CIRCLE && graph.getEndpoint(b, a) == Endpoint.TAIL) {
                    for (Node c : graph.getAdjacentNodes(b)) {
                        if (c != a && !graph.isAdjacentTo(a, c) && graph.getEndpoint(c, b) == Endpoint.CIRCLE) {
                            setEndpoint(graph, c, b, Endpoint.TAIL);
                            changeFlag = true;

                            if (verbose) {
                                TetradLogger.getInstance().log(LogUtilsSearch.edgeOrientedMsg("R7: Single tails (tail)", graph.getEdge(c, b)));
                            }
                        }
                    }
                }
            }

            {
                Node a = edge.getNode2();
                Node b = edge.getNode1();

                if (graph.getEndpoint(a, b) == Endpoint.CIRCLE && graph.getEndpoint(b, a) == Endpoint.TAIL) {
                    for (Node c : graph.getAdjacentNodes(b)) {
                        if (c != a && !graph.isAdjacentTo(a, c) && graph.getEndpoint(c, b) == Endpoint.CIRCLE) {
                            Endpoint tail = Endpoint.TAIL;

                            setEndpoint(graph, c, b, tail);
                            changeFlag = true;

                            if (verbose) {
                                TetradLogger.getInstance().log(LogUtilsSearch.edgeOrientedMsg("R7: Single tails (tail)", graph.getEdge(c, b)));
                            }
                        }
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
     * R8 If α → β → γ or α−−◦β → γ, and α o→ γ, orient α o→ γ as α → γ.
     *
     * @param a     α
     * @param c     γ
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
                setEndpoint(graph, c, a, Endpoint.TAIL);

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
     * R9 If α o→ γ, and p = &lt;α,β,θ,...,γ&gt; is an uncovered potentialy directed path from α to γ such that γ and β
     * are not adjacent, then orient α o→ γ as α → γ.
     *
     * @param a     The node A.
     * @param c     The node C.
     * @param graph The graph being oriented.
     * @return Whether R9 was successfully applied.
     */
    public boolean ruleR9(Node a, Node c, Graph graph) {

        // We are aiming to orient the tails on certain partially oriented edges α o→ γ, so we first
        // need to make sure we have such an edge.
        Edge edge = graph.getEdge(a, c);

        if (edge == null) {
            return false;
        }

        if (!edge.equals(Edges.partiallyOrientedEdge(a, c))) {
            return false;
        }

        // We do this by finding a shortest path using Dijkstra's shortest path algorithm. We constrain the algorithm
        // so that the path must be potentially directed (i.e., semidirected), there can be no length 1 or length 2
        // paths, and all nodes on the path are uncovered. We add further constraints so that the path taken together
        // with the x o-o y edge forms an uncovered cyclic path, and that the path is a potential directed path.

        R5R9Dijkstra.Graph fullDijkstraGraph = new R5R9Dijkstra.Graph(graph, R5R9Dijkstra.Rule.R9);

        Node x = edge.getNode1();
        Node y = edge.getNode2();

        // This returns a map from each node to its predecessor on the path, so that we can reconstruct the path.
        // (Dijkstra's algorithm proper doesn't specify that the paths be recorded, only that the shortest distances
        // be recorded, but we can keep track of the paths as well.
        Map<Node, Node> predecessors = R5R9Dijkstra.distances(fullDijkstraGraph, x, y).getRight();

        // This gets the path from the predecessor map.
        List<Node> path = FciOrientDijkstra.getPath(predecessors, x, y);

        // If the result is null, there was no path.
        if (path == null) {
            return false;
        }

        // This is the whole point of the rule, to orient the cicle in α o→ γ as a tail.
        setEndpoint(graph, c, a, Endpoint.TAIL);

        if (verbose) {
            this.logger.log(LogUtilsSearch.edgeOrientedMsg("R9: ", graph.getEdge(c, a)) + " path = " + GraphUtils.pathString(graph, path, false));
        }

        this.changeFlag = true;
        return true;
    }

    /**
     * R10 Suppose α o→ γ, β → γ ← θ, p1 is an uncovered potentially directed (semidirected) path from α to β, and p2 is
     * an uncovered p.d. path from α to θ. Let μ be the vertex adjacent to α on p1 (μ could be β), and ω be the vertex
     * adjacent to α on p2 (ω could be θ). If μ and ω are distinct, and are not adjacent, then orient α o→ γ as α → γ.
     *
     * @param alpha α
     * @param gamma γ
     * @param graph alpha {@link edu.cmu.tetrad.graph.Graph} object
     */
    public void ruleR10(Node alpha, Node gamma, Graph graph) {

        // We are aiming to orient the tails on certain partially oriented edges alpha o-> gamma, so we first
        // need to make sure we have such an edge.
        Edge edge = graph.getEdge(alpha, gamma);

        if (edge == null) {
            return;
        }

        if (!edge.equals(Edges.partiallyOrientedEdge(alpha, gamma))) {
            return;
        }

        // Now we are sure we have an alpha o-> gamma edge. Next, we need to find directed edges beta -> gamma <- theta.

        List<Node> into = graph.getNodesInTo(gamma, Endpoint.ARROW);
        into.remove(alpha);

        for (int i = 0; i < into.size(); i++) {
            for (int j = i + 1; j < into.size(); j++) {
                Node beta = into.get(i);
                Node theta = into.get(j);

                if (graph.getEndpoint(gamma, beta) != Endpoint.TAIL || graph.getEndpoint(gamma, theta) != Endpoint.TAIL) {
                    continue;
                }

                // At this point we have beta -> gamma <- theta, with alpha o-> gamma. Next we need to find the
                // a novel adjacent nu to alpha and a novel adjacent omega to alpha such that nu and omega are not
                // adjacent.

                List<Node> adj1 = graph.getAdjacentNodes(alpha);
                adj1.remove(beta);
                adj1.remove(theta);
                adj1.remove(beta);

                for (int k = 0; k < adj1.size(); k++) {
                    for (int l = k + 1; l < adj1.size(); l++) {
                        Node nu = adj1.get(k);
                        Node omega = adj1.get(l);

                        if (graph.isAdjacentTo(nu, omega)) {
                            continue;
                        }

                        // Now we have our beta, theta, nu, and omega for R10. Next we need to try to find
                        // alpha semidirected path p1 starting with <alpha, nu>, and ending with beta, and alpha path
                        // p2 starting with <alpha, omega> and ending with theta.

                        if (graph.paths().existsSemiDirectedPath(nu, beta) && graph.paths().existsSemiDirectedPath(omega, theta)) {

                            // Now we know we have the paths p1 and p2 as required, so R10 applies! We now need to
                            // orient the circle of the alpha o-> gamma edge as a tail.
                            setEndpoint(graph, gamma, alpha, Endpoint.TAIL);

                            if (verbose) {
                                this.logger.log(LogUtilsSearch.edgeOrientedMsg("R10: ", graph.getEdge(gamma, alpha)));
                            }

                            this.changeFlag = true;
                            return;
                        }
                    }
                }
            }
        }
    }

    /**
     * Returns the maximum path length, or -1 if unlimited.
     *
     * @return the maximum path length
     */
    public int getMaxDiscriminatingPathLength() {
        return this.maxDiscriminatingPathLength;
    }

    /**
     * Sets the maximum length of any discriminating path.
     *
     * @param maxDiscriminatingPathLength the maximum length of any discriminating path, or -1 if unlimited.
     */
    public void setMaxDiscriminatingPathLength(int maxDiscriminatingPathLength) {
        if (maxDiscriminatingPathLength < -1) {
            throw new IllegalArgumentException("Max path length must be -1 (unlimited) or >= 0: " + maxDiscriminatingPathLength);
        }

        this.maxDiscriminatingPathLength = maxDiscriminatingPathLength;
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

    /**
     * Sets whether the discriminating path orientation should be run in parallel.
     *
     * @param parallel True, if so.
     */
    public void setParallel(boolean parallel) {
        this.parallel = parallel;
    }

    /**
     * Sets the endpoint strategy for this object.
     *
     * @param endpointStrategy the endpoint strategy to set
     * @see SetEndpointStrategy
     */
    public void setEndpointStrategy(SetEndpointStrategy endpointStrategy) {
        this.endpointStrategy = endpointStrategy;
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
            setEndpoint(graph, to, from, Endpoint.ARROW);

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

            setEndpoint(graph, to, from, Endpoint.TAIL);
            setEndpoint(graph, from, to, Endpoint.ARROW);

            if (verbose) {
                this.logger.log(LogUtilsSearch.edgeOrientedMsg("Knowledge", graph.getEdge(from, to)));
            }

            this.changeFlag = true;
        }

        if (verbose) {
            this.logger.log("Finishing BK Orientation.");
        }
    }

    private void setEndpoint(Graph graph, Node a, Node b, Endpoint endpoint) {
        endpointStrategy.setEndpoint(graph, a, b, endpoint);
    }

    /**
     * Sets whether R4 should be run.
     *
     * @param doR4 True, if so.
     */
    public void setDoR4(boolean doR4) {
        this.doR4 = doR4;
    }
}
