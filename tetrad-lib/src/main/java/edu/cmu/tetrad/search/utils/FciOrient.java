/// ////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.data.KnowledgeEdge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.FciOrientDijkstra;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.PermutationGenerator;
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
 * FGES-FCI and RFCI algorihtms.
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
     * Indicates whether the discriminating path step should be run in parallel.
     */
    private boolean parallel = false;
    /**
     * The endpoint strategy to use for setting endpoints.
     */
    private SetEndpointStrategy endpointStrategy = new DefaultSetEndpointStrategy();
    /**
     * Indicates whether to run R4 or not.
     */
    private boolean useR4 = true;

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
     * Determines if an arrowhead can be placed at node Y in the given graph, based on the adjacency relationships,
     * endpoint types, and any provided prior knowledge constraints.
     *
     * @param x     The first node under consideration in the graph.
     * @param y     The second node under consideration in the graph, where the arrowhead placement is evaluated.
     * @param graph The graph object containing nodes and their relationships.
     * @param K     An object representing prior knowledge that may impose requirements or restrictions on edges.
     * @return true if an arrowhead is allowed at node Y under the given conditions; false otherwise.
     */
    public static boolean isArrowheadAllowed(Node x, Node y, Graph graph, Knowledge K) {
        if (!graph.isAdjacentTo(x, y)) return false;

        Endpoint eXY = graph.getEndpoint(x, y); // endpoint at y
        Endpoint eYX = graph.getEndpoint(y, x); // endpoint at x

        // Already arrow at Y => allowed (no change).
        if (eXY == Endpoint.ARROW) return true;

        // Tail fixed at Y => cannot put an arrowhead at Y.
        if (eXY == Endpoint.TAIL) return false;

        // If knowledge REQUIRES y->x, disallow arrowhead at Y (bidirected would violate the requirement).
        if (K != null && K.isRequired(y.getName(), x.getName())) return false;

        // If knowledge FORBIDS x->y, only allow an arrowhead at Y when we ALREADY have an arrowhead at X
        // (so we'd make x <-> y). Otherwise, block to avoid x->y.
        if (K != null && K.isForbidden(x.getName(), y.getName()) && eYX != Endpoint.ARROW) return false;

        // Otherwise, circle at Y is orientable.
        return eXY == Endpoint.CIRCLE;
    }

    public static Set<DiscriminatingPath> listDiscriminatingPaths(
            Graph graph, int maxLen, boolean checkXyNonadjacency) {

        Set<DiscriminatingPath> out = new HashSet<>();
        for (Node w : graph.getNodes()) {
            for (Node y : graph.getAdjacentNodes(w)) {
                out.addAll(listDiscriminatingPaths(graph, w, y, maxLen, checkXyNonadjacency));
            }
        }
        return out;
    }

    public static Set<DiscriminatingPath> listDiscriminatingPaths(
            Graph graph, Node w, Node y, int maxLen, boolean checkEcNonadjacency) {

        Set<DiscriminatingPath> out = new HashSet<>();

        // Required local relationship between w and y:
        if (checkEcNonadjacency) {
            // strict: only when w is a parent of y
            if (!graph.isParentOf(w, y)) return out;
        } else {
            // relaxed: allow covering edge but not y -> w
            Endpoint e_yw = graph.getEndpoint(y, w);
            if (e_yw == Endpoint.ARROW) return out;
        }

        // v must be adjacent to both w and y
        Set<Node> vset = new HashSet<>(graph.getAdjacentNodes(w));
        vset.retainAll(graph.getAdjacentNodes(y));

        for (Node v : vset) {
            if (v == w || v == y) continue;

            // Need v o-> y to be a candidate
            Endpoint e_yv = graph.getEndpoint(y, v); // endpoint at v on edge y—v
            Endpoint e_vy = graph.getEndpoint(v, y); // endpoint at y on edge v—y
            if (e_yv != Endpoint.CIRCLE) continue;   // circle at v
            if (e_vy != Endpoint.ARROW)  continue;   // arrowhead into y

            discriminatingPathBfs(w, v, y, graph, out, maxLen, checkEcNonadjacency);
        }

        return out;
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
    private static void discriminatingPathBfs(Node w, Node v, Node y, Graph graph, Set<DiscriminatingPath> discriminatingPaths, int maxDiscriminatingPathLength, boolean checkEcNonadjacency) {

        // State carries current node t, previous node p (next in path toward v), and the colliderPath so far (between v and current upstream).
        class State {
            final Node t, p;                   // at node t, previous is p (null at the start)
            final LinkedList<Node> path;       // nodes between v and current upstream endpoint (excludes that upstream x)

            State(Node t, Node p, LinkedList<Node> path) {
                this.t = t;
                this.p = p;
                this.path = path;
            }
        }

        ArrayDeque<State> Q = new ArrayDeque<>();
        // Start at w with no previous; colliderPath initially empty.
        Q.offer(new State(w, null, new LinkedList<>()));

        while (!Q.isEmpty()) {
            if (Thread.currentThread().isInterrupted()) break;

            State s = Q.poll();
            Node t = s.t;
            Node p = s.p;                 // "next in path" toward v
            LinkedList<Node> pathToT = s.path;

            // If t is an interior node (i.e., not the very first step), insist that p *-> t (collider at t),
            // and (optional) that t is a parent of y.
            if (p != null) {
                if (graph.getEndpoint(p, t) != Endpoint.ARROW) continue;      // NOT a collider at t
                if (!graph.isParentOf(t, y)) continue;                         // prune: interior must be parent of y
            }

            // Explore predecessors x with an arrowhead into t : x *-> t
            for (Node x : graph.getNodesInTo(t, Endpoint.ARROW)) {
                if (Thread.currentThread().isInterrupted()) break;

                // avoid immediate 2-cycle and prevent cycles along the current branch
                if (x == p) continue;
                if (pathToT.contains(x)) continue;

                // Build colliderPath for candidate x: it’s pathToT plus the current t
                LinkedList<Node> colliderPath = new LinkedList<>(pathToT);
                colliderPath.add(t); // interior nodes between v and x (t becomes interior once we step to x)

                if (maxDiscriminatingPathLength != -1 && colliderPath.size() + 1 > maxDiscriminatingPathLength) {
                    continue;
                }

                // Let DiscriminatingPath judge validity; we’re just enumerating paths with enforced collider-at-interior.
                DiscriminatingPath dp = new DiscriminatingPath(x, w, v, y, colliderPath, checkEcNonadjacency);

                if (dp.existsIn(graph)) {
                    discriminatingPaths.add(dp);
                }

                // Optional prune: also insist the new upstream node x is a parent of y to keep only promising chains.
                if (!graph.isParentOf(x, y)) continue;

                // Push next state upstream: new current is x, previous is t, carry this branch’s path
                Q.offer(new State(x, t, colliderPath));
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
     * R1: If α *→ β o––* γ, and α and γ are not adjacent, then orient the triple as α *→ β → γ.
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
     * R2: If α → β ∘→ γ  or  α ∘→ β → γ, and α ∘–o γ, then orient α ∘–o γ as α ∘→ γ.
     * <p>
     * Intuition: when there’s a directed path α → β → γ with a circle on the edge incident to β on one side, and α–γ is
     * currently a circle–circle edge, we can orient α–γ toward γ.
     *
     * @param a     α
     * @param b     β
     * @param c     γ
     * @param graph the graph in which the nodes exist
     */
    public void ruleR2(Node a, Node b, Node c, Graph graph) {
        if ((graph.isAdjacentTo(a, c)) && (graph.getEndpoint(a, c) == Endpoint.CIRCLE)) {
            if ((graph.getEndpoint(a, b) == Endpoint.ARROW && graph.getEndpoint(b, c) == Endpoint.ARROW) && (graph.getEndpoint(b, a) == Endpoint.TAIL) || (graph.getEndpoint(a, b) == Endpoint.ARROW && graph.getEndpoint(b, c) == Endpoint.ARROW && graph.getEndpoint(c, b) == Endpoint.TAIL)) {

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
     * R3: If α *→ β ←* γ, α *–o θ o–* γ, α and γ are not adjacent, and θ *–o β, then orient θ *–o β as θ *→ β.
     *
     * @param graph the graph in which the nodes exist
     */
    public void ruleR3(Graph graph) {

        // a = Î±, b = Î², c = Î³, d = Î¸
        List<Node> nodes = graph.getNodes();

        for (Node b : nodes) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            List<Node> adj = new ArrayList<>(graph.getAdjacentNodes(b));

            ChoiceGenerator gen = new ChoiceGenerator(adj.size(), 3);
            int[] choice;

            WH:
            while ((choice = gen.next()) != null) {
                List<Node> adjb = GraphUtils.asList(choice, adj);

                PermutationGenerator pg = new PermutationGenerator(adjb.size());
                int[] perm;

                while ((perm = pg.next()) != null) {
                    Node a = adjb.get(perm[0]);
                    Node d = adjb.get(perm[1]);
                    Node c = adjb.get(perm[2]);

                    if (!graph.isDefCollider(a, b, c)) {
                        continue;
                    }

                    if (!(graph.isAdjacentTo(a, b) && graph.isAdjacentTo(d, b) && graph.isAdjacentTo(c, b))) {
                        continue;
                    }

                    if (!(graph.isAdjacentTo(a, d) && graph.isAdjacentTo(c, d))) {
                        continue;
                    }

                    if (!(graph.getEndpoint(d, b) == Endpoint.CIRCLE && graph.getEndpoint(a, d) == Endpoint.CIRCLE && graph.getEndpoint(c, d) == Endpoint.CIRCLE)) {
                        continue;
                    }

                    if (!FciOrient.isArrowheadAllowed(d, b, graph, knowledge)) {
                        continue;
                    }

                    setEndpoint(graph, d, b, Endpoint.ARROW);

                    if (this.verbose) {
                        this.logger.log(LogUtilsSearch.edgeOrientedMsg("R3: Double triangle", graph.getEdge(d, b)));
                    }

                    this.changeFlag = true;
                    break WH;
                }
            }
        }
    }

    /**
     * R4: If u = ⟨θ, …, α, β, γ⟩ is a discriminating path between θ and γ for β,
     * and β o−∗ γ, then:
     * <ul>
     *   <li>If β ∈ Sepset(θ, γ), orient β o−∗ γ as β → γ;</li>
     *   <li>Otherwise, orient the triple ⟨α, β, γ⟩ as α ↔ β ↔ γ.</li>
     * </ul>
     *
     * <p>This rule uses discriminating paths to determine whether β acts as a collider
     * or non-collider on the triple ⟨α, β, γ⟩, refining orientations in the presence of
     * potential latent confounding.</p>
     *
     * @param graph The {@link edu.cmu.tetrad.graph.Graph} being oriented.
     */
    public void ruleR4(Graph graph) {

        if (!useR4) {
            return;
        }

        if (verbose) {
            TetradLogger.getInstance().log("R4: Discriminating path orientation started.");
        }

        List<Pair<DiscriminatingPath, Boolean>> allResults = new ArrayList<>();

        int testTimeout = this.testTimeout == -1 ? Integer.MAX_VALUE : (int) this.testTimeout;

        // Not parallel is the default.
        if (parallel) {
            while (true) {
                List<Callable<Pair<DiscriminatingPath, Boolean>>> tasks = getDiscriminatingPathTasks(graph, null);

                List<Pair<DiscriminatingPath, Boolean>> results = tasks.parallelStream().map(task -> GraphSearchUtils.runWithTimeout(task, testTimeout, TimeUnit.MILLISECONDS)).toList();

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
                List<Callable<Pair<DiscriminatingPath, Boolean>>> tasks = getDiscriminatingPathTasks(graph, null);
                if (tasks.isEmpty()) break;

                List<Pair<DiscriminatingPath, Boolean>> results = tasks.stream().map(task -> {
                    try {
                        return task.call();
//                        return GraphSearchUtils.runWithTimeout(task, testTimeout, TimeUnit.MILLISECONDS);
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
     * @param graph            the graph
     * @param allowedColliders the allowed colliders
     * @return the list of tasks
     */
    private @NotNull List<Callable<Pair<DiscriminatingPath, Boolean>>> getDiscriminatingPathTasks(Graph graph, Set<Triple> allowedColliders) {
        Set<DiscriminatingPath> discriminatingPaths = listDiscriminatingPaths(graph, maxDiscriminatingPathLength, true);

        Set<Node> vNodes = new HashSet<>();

        for (DiscriminatingPath discriminatingPath : discriminatingPaths) {
            vNodes.add(discriminatingPath.getV());
        }

        List<Callable<Pair<DiscriminatingPath, Boolean>>> tasks = new ArrayList<>();

        for (DiscriminatingPath discriminatingPath : discriminatingPaths) {
            tasks.add(() -> strategy.doDiscriminatingPathOrientation(discriminatingPath, graph, vNodes));
        }

        return tasks;
    }

    /**
     * R5: For every remaining α o−o β, if there exists an uncovered circle path
     * p = ⟨α, γ, …, θ, β⟩ between α and β such that α and θ are not adjacent
     * and β and γ are not adjacent, then orient α o−o β and every edge on p
     * as undirected (−−).
     *
     * <p>This rule converts circle paths into undirected chains when they form
     * an uncovered circle path between α and β, thereby ensuring that the
     * resulting PAG correctly represents selection bias relationships.</p>
     *
     * @param graph The {@link edu.cmu.tetrad.graph.Graph} being oriented.
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
                boolean uncovered = true;
                Map<Node, Node> predecessors = R5R9Dijkstra.distances(fullDijkstraGraph, uncovered, x, y, false).getRight();

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
     * R6: If α — β o−∗ γ (where α and γ may or may not be adjacent),
     * then orient β o−∗ γ as β −∗ γ.
     *
     * <p>This rule orients the circle endpoint on β o−∗ γ as a tail when β
     * is connected to α by an undirected edge, ensuring propagation of
     * definite non-collider structure along the chain.</p>
     *
     * @param graph The {@link edu.cmu.tetrad.graph.Graph} being oriented.
     */
    public void ruleR6(Graph graph) {

        // We first look for undirected edges x â- y and the look for Î³ adjacent to either the x or the
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
     * R7: If α −∘ β o−∗ γ, and α and γ are not adjacent, then orient
     * β o−∗ γ as β −∗ γ.
     *
     * <p>This rule resolves the circle at β by extending the orientation
     * consistently along the partially directed chain from α to γ, provided
     * that α and γ are nonadjacent.</p>
     *
     * @param graph The {@link edu.cmu.tetrad.graph.Graph} being oriented.
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
     * R8: If α → β → γ or α −∘ β → γ, and α ∘→ γ, then orient α ∘→ γ as α → γ.
     *
     * <p>This rule orients the circle endpoint on α ∘→ γ when α already reaches γ
     * through an intermediate directed or partially directed chain, ensuring
     * transitive consistency of arrow directions.</p>
     *
     * @param a     The node α.
     * @param c     The node γ.
     * @param graph The graph being oriented.
     * @return {@code true} if R8 was successfully applied; {@code false} otherwise.
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

            if (graph.getEndpoint(b, a) == Endpoint.TAIL && graph.getEndpoint(a, b) == Endpoint.ARROW && graph.getEndpoint(c, b) == Endpoint.TAIL && graph.getEndpoint(b, c) == Endpoint.ARROW) {
                orient = true;
            } else if (graph.getEndpoint(b, a) == Endpoint.TAIL && graph.getEndpoint(a, b) == Endpoint.CIRCLE && graph.getEndpoint(c, b) == Endpoint.TAIL && graph.getEndpoint(b, c) == Endpoint.ARROW) {
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
     * R9: Suppose α ∘→ γ, and let
     * p = ⟨α, β, θ, …, γ⟩ be an uncovered, potentially-directed path from α to γ
     * such that γ and β are not adjacent.
     * Then orient α ∘→ γ as α → γ.
     *
     * <p>This rule finalizes the circle endpoint on α ∘→ γ when α can reach γ
     * through an uncovered potentially-directed path that begins with a
     * non-adjacent β, ensuring consistency with the causal flow implied by the
     * rest of the graph.</p>
     *
     * @param a     The node α.
     * @param c     The node γ.
     * @param graph The graph being oriented.
     * @return {@code true} if R9 was successfully applied; {@code false} otherwise.
     */
    public boolean ruleR9(Node a, Node c, Graph graph) {

        // We are aiming to orient the tails on certain partially oriented edges Î± oâ Î³, so we first
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
        boolean uncovered = true;
        Map<Node, Node> predecessors = R5R9Dijkstra.distances(fullDijkstraGraph, uncovered, x, y, true).getRight();

        // This gets the path from the predecessor map.
        List<Node> path = FciOrientDijkstra.getPath(predecessors, x, y);

        // If the result is null, there was no path.
        if (path == null) {
            return false;
        }

        // This is the whole point of the rule, to orient the cicle in Î± oâ Î³ as a tail.
        setEndpoint(graph, c, a, Endpoint.TAIL);

        if (verbose) {
            this.logger.log(LogUtilsSearch.edgeOrientedMsg("R9: ", graph.getEdge(c, a)) + " path = " + GraphUtils.pathString(graph, path, false));

            for (int i = 2; i < path.size(); i++) {
                if (graph.isAdjacentTo(path.get(i), path.get(i - 2))) {
                    System.out.println("adjacent " + path.get(i) + " to " + path.get(i - 2));
                }

                if (graph.isAdjacentTo(path.getLast(), path.get(1))) {
                    System.out.println("adjacent gamma = " + path.getLast() + " to beta = " + path.get(1));
                }
            }
        }

        this.changeFlag = true;
        return true;
    }

    /**
     * R10 (Zhang 2008 FCI orientation rule).
     * <p>
     * ASCII version: Suppose alpha o-&gt; gamma, beta -&gt; gamma &lt;- theta. Let p1 be an uncovered potentially directed
     * (semi-directed) path from alpha to beta, and p2 be an uncovered potentially directed path from alpha to theta.
     * Let mu be the vertex adjacent to alpha on p1 (mu could be beta), and omega be the vertex adjacent to alpha on p2
     * (omega could be theta). If mu and omega are distinct and nonadjacent, then orient alpha o-&gt; gamma as alpha -&gt;
     * gamma.
     * <p>
     * Unicode version (same content): Suppose α o→ γ, β → γ ← θ. Let p1 be an uncovered potentially directed
     * (semi-directed) path from α to β, and p2 be an uncovered potentially directed path from α to θ. Let μ be the
     * vertex adjacent to α on p1 (μ could be β), and ω be the vertex adjacent to α on p2 (ω could be θ). If μ and ω are
     * distinct and nonadjacent, then orient α o→ γ as α → γ.
     * <p>
     * Notes: - "Uncovered" means every consecutive triple on the path is unshielded. - "Potentially directed /
     * semi-directed" means no arrowhead points toward alpha along the path.
     *
     * @param alpha the node α
     * @param gamma the node γ
     * @param graph the working {@link edu.cmu.tetrad.graph.Graph}
     */
//    public void ruleR10(Node alpha, Node gamma, Graph graph) {
//
//        // We are aiming to orient the tails on certain partially oriented edges alpha o-> gamma, so we first
//        // need to make sure we have such an edge.
//        Edge edge = graph.getEdge(alpha, gamma);
//
//        if (edge == null) {
//            return;
//        }
//
//        if (!edge.equals(Edges.partiallyOrientedEdge(alpha, gamma))) {
//            return;
//        }
//
//        // Now we are sure we have an alpha o-> gamma edge. Next, we need to find directed edges beta -> gamma <- theta.
//
//        List<Node> into = graph.getNodesInTo(gamma, Endpoint.ARROW);
//        into.remove(alpha);
//
//        for (int i = 0; i < into.size(); i++) {
//            for (int j = i + 1; j < into.size(); j++) {
//                Node beta = into.get(i);
//                Node theta = into.get(j);
//
//                if (graph.getEndpoint(gamma, beta) != Endpoint.TAIL || graph.getEndpoint(gamma, theta) != Endpoint.TAIL) {
//                    continue;
//                }
//
//                // At this point we have beta -> gamma <- theta, with alpha o-> gamma. Next we need to find the
//                // a novel adjacent nu to alpha and a novel adjacent omega to alpha such that nu and omega are not
//                // adjacent.
//
//                List<Node> adj1 = graph.getAdjacentNodes(alpha);
//                adj1.remove(beta);
//                adj1.remove(theta);
//                adj1.remove(beta);
//
//                for (int k = 0; k < adj1.size(); k++) {
//                    for (int l = k + 1; l < adj1.size(); l++) {
//                        Node nu = adj1.get(k);
//                        Node omega = adj1.get(l);
//
//                        if (graph.isAdjacentTo(nu, omega)) {
//                            continue;
//                        }
//
//                        // Now we have our beta, theta, nu, and omega for R10. Next we need to try to find
//                        // alpha semidirected path p1 starting with <alpha, nu>, and ending with beta, and alpha path
//                        // p2 starting with <alpha, omega> and ending with theta.
//
//                        if (graph.paths().existsSemiDirectedPath(nu, beta) && graph.paths().existsSemiDirectedPath(omega, theta)) {
//
//                            // Now we know we have the paths p1 and p2 as required, so R10 applies! We now need to
//                            // orient the circle of the alpha o-> gamma edge as a tail.
//                            setEndpoint(graph, gamma, alpha, Endpoint.TAIL);
//
//                            if (verbose) {
//                                this.logger.log(LogUtilsSearch.edgeOrientedMsg("R10: ", graph.getEdge(gamma, alpha)));
//                            }
//
//                            this.changeFlag = true;
//                            return;
//                        }
//                    }
//                }
//            }
//        }
//    }
    public void ruleR10(Node alpha, Node gamma, Graph graph) {
        // Require alpha o-> gamma
        Edge e = graph.getEdge(alpha, gamma);
        if (e == null || !e.equals(Edges.partiallyOrientedEdge(alpha, gamma))) return;

        // Need beta -> gamma <- theta (exclude alpha)
        final List<Node> into = new ArrayList<>(graph.getNodesInTo(gamma, Endpoint.ARROW));
        into.remove(alpha);
        if (into.size() < 2) return;

        // Neighbors of alpha (include beta/theta if adjacent; μ/ω may be them!)
        final Set<Node> adjAlpha = new HashSet<>(graph.getAdjacentNodes(alpha));
        if (adjAlpha.isEmpty()) return;

        // Cache for uncovered PD subproblems: (prev, curr, target) -> boolean
        final Map<Key3, Boolean> cache = new HashMap<>();

        for (int i = 0; i < into.size(); i++) {
            for (int j = i + 1; j < into.size(); j++) {
                Node beta = into.get(i);
                Node theta = into.get(j);

                // Ensure beta -> gamma <- theta (tails at gamma side)
                if (graph.getEndpoint(gamma, beta) != Endpoint.TAIL) continue;
                if (graph.getEndpoint(gamma, theta) != Endpoint.TAIL) continue;

                // μ candidates for beta and ω candidates for theta are any neighbors of alpha
                // whose first hop satisfies uncovered + potentially-directed, and from which
                // an uncovered PD path reaches the respective target.
                List<Node> muCand = new ArrayList<>();
                List<Node> omegaCand = new ArrayList<>();

                for (Node hop : adjAlpha) {
                    if (existsUncoveredPdPathFromAlphaVia(alpha, hop, beta, graph, cache)) muCand.add(hop);
                    if (existsUncoveredPdPathFromAlphaVia(alpha, hop, theta, graph, cache)) omegaCand.add(hop);
                }

                if (muCand.isEmpty() || omegaCand.isEmpty()) continue;

                // Need μ and ω distinct and nonadjacent
                final Set<Node> omegaSet = new HashSet<>(omegaCand);
                for (Node mu : muCand) {
                    for (Node omega : omegaSet) {
                        if (mu == omega) continue;
                        if (graph.isAdjacentTo(mu, omega)) continue;

                        // Orient α o-> γ as α -> γ
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

    /**
     * Exact check for an uncovered, potentially-directed path that starts with the pair (alpha, hop) and ends at
     * 'target'.
     * <p>
     * Potentially-directed constraint (from alpha to target): for each step (prev -> curr -> next) along the path, the
     * edge (curr, next) must NOT have an arrowhead pointing into 'curr': graph.getEndpoint(next, curr) !=
     * Endpoint.ARROW
     * <p>
     * Uncovered constraint: for each triple (prev, curr, next), prev and next must be nonadjacent.
     */
    private boolean existsUncoveredPdPathFromAlphaVia(Node alpha,
                                                      Node hop,
                                                      Node target,
                                                      Graph graph,
                                                      Map<Key3, Boolean> cache) {
        // First hop must be PD wrt alpha: no arrowhead into alpha on (alpha, hop)
        if (graph.getEndpoint(hop, alpha) == Endpoint.ARROW) return false;

        // Trivial success: hop == target gives path alpha—hop == alpha—target
        if (hop == target) return true;

        // DFS with memo and visited set (simple paths)
        return dfsUncoveredPd(alpha, hop, target, graph, cache, new HashSet<>());
    }

    private boolean dfsUncoveredPd(Node prev,
                                   Node curr,
                                   Node target,
                                   Graph graph,
                                   Map<Key3, Boolean> cache,
                                   Set<NodePair> visitedEdge) {
        Key3 key = Key3.of(prev, curr, target);
        Boolean memo = cache.get(key);
        if (memo != null) return memo;

        // Prevent immediate back-and-forth and general cycles via edge memory
        NodePair edgeKey = NodePair.of(prev, curr);
        if (!visitedEdge.add(edgeKey)) {
            cache.put(key, Boolean.FALSE);
            return false;
        }

        try {
            for (Node next : graph.getAdjacentNodes(curr)) {
                if (next == prev) continue;

                // Potentially-directed step: no arrowhead into 'curr' on (curr,next)
                if (graph.getEndpoint(next, curr) == Endpoint.ARROW) continue;

                // Uncovered triple (prev, curr, next)
                if (graph.isAdjacentTo(prev, next)) continue;

                if (next == target) {
                    cache.put(key, Boolean.TRUE);
                    return true;
                }

                // Recurse
                if (dfsUncoveredPd(curr, next, target, graph, cache, visitedEdge)) {
                    cache.put(key, Boolean.TRUE);
                    return true;
                }
            }

            cache.put(key, Boolean.FALSE);
            return false;
        } finally {
            // Backtrack edge marker
            visitedEdge.remove(edgeKey);
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
        if (!(maxDiscriminatingPathLength == -1 || maxDiscriminatingPathLength >= 4)) {
            throw new IllegalArgumentException("maxDiscriminatingPathLength must be -1 (unlimited) or >= 4: " + maxDiscriminatingPathLength);
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

        for (Iterator<KnowledgeEdge> it = bk.requiredEdgesIterator(); it.hasNext(); ) {
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
     * @param useR4 True, if so.
     */
    public void setUseR4(boolean useR4) {
        this.useR4 = useR4;
    }

    /**
     * Identity-based pair key for visited edges.
     */
    private static final class NodePair {
        final Node a, b;

        private NodePair(Node a, Node b) {
            this.a = a;
            this.b = b;
        }

        static NodePair of(Node a, Node b) {
            return new NodePair(a, b);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof NodePair p)) return false;
            return a == p.a && b == p.b;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(a) * 31 + System.identityHashCode(b);
        }
    }

    /**
     * Identity-based triple key for memoization.
     */
    private static final class Key3 {
        final Node u, v, t;

        private Key3(Node u, Node v, Node t) {
            this.u = u;
            this.v = v;
            this.t = t;
        }

        static Key3 of(Node u, Node v, Node t) {
            return new Key3(u, v, t);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Key3 k)) return false;
            return u == k.u && v == k.v && t == k.t;
        }

        @Override
        public int hashCode() {
            int h = System.identityHashCode(u);
            h = h * 31 + System.identityHashCode(v);
            h = h * 31 + System.identityHashCode(t);
            return h;
        }
    }
}

