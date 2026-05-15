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
     * The maximum blocking path length variable.
     * <p>
     * This variable represents the maximum length of a blocking path, or -1 if no maximum length is set.
     */
    private int maxBlockingPathLength = -1;
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
    private final SetEndpointStrategy endpointStrategy = new DefaultSetEndpointStrategy();

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
//        Endpoint eYX = graph.getEndpoint(y, x); // endpoint at x

        // Already arrow at Y => allowed (no change).
        if (eXY == Endpoint.ARROW) return true;

        // Tail fixed at Y => cannot put an arrowhead at Y.
        if (eXY == Endpoint.TAIL) return false;

        // If knowledge REQUIRES y->x, disallow arrowhead at Y (bidirected would violate the requirement).
        if (K != null && K.isRequired(y.getName(), x.getName())) return false;

        // If knowledge FORBIDS x->y, disallow arrowhead at Y.
        if (K != null && K.isForbidden(x.getName(), y.getName())) return false;

        // Otherwise, circle at Y is orientable.
        return eXY == Endpoint.CIRCLE;
    }

    /**
     * Finds and returns a set of discriminating paths in the given graph. A discriminating path is determined based on
     * the criteria provided such as maximum path length and whether to check XY non-adjacency.
     *
     * @param graph               the input graph in which to search for discriminating paths
     * @param maxLen              the maximum allowable length of the paths
     * @param checkXyNonadjacency a boolean indicating whether to verify non-adjacency between certain nodes (X and Y)
     *                            in the graph
     * @return a set containing discriminating paths found in the graph
     */
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

    /**
     * Finds and returns the set of discriminating paths in the given graph, based on the specified parameters.
     * A discriminating path is a specific type of path in a causal graph, used in graph-based causal inference to
     * identify the causal structure that satisfies certain conditions.
     *
     * @param graph               The graph in which to search for discriminating paths.
     * @param w                   The starting node for the path, which must satisfy specific adjacency conditions with the target node y.
     * @param y                   The target node for which discriminating paths are being identified.
     * @param maxLen              The maximum allowable length for the paths being considered.
     * @param checkEcNonadjacency A flag indicating whether strict adjacency conditions between the nodes w and y should be enforced
     *                            (true for strict adjacency checks, false for relaxed checks).
     * @return A set of discriminating paths that satisfy the required conditions, or an empty set if no such paths are found.
     */
    public static Set<DiscriminatingPath> listDiscriminatingPaths(
            Graph graph, Node w, Node y, int maxLen, boolean checkEcNonadjacency) {

        Set<DiscriminatingPath> out = new HashSet<>();

        // In the strict/original setting, W must be a parent of Y,
        // since W is one of the vertices between X and V.
        if (checkEcNonadjacency) {
            if (!graph.isParentOf(w, y)) {
                return out;
            }
        } else {
            // Relaxed variant: allow W -* Y, but not Y -> W.
            if (!graph.isAdjacentTo(w, y)) {
                return out;
            }
            if (graph.getEndpoint(y, w) == Endpoint.ARROW) {
                return out;
            }
        }

        // Candidate V must be adjacent to both W and Y.
        Set<Node> vset = new HashSet<>(graph.getAdjacentNodes(w));
        vset.retainAll(graph.getAdjacentNodes(y));

        for (Node v : vset) {
            if (v == w || v == y) {
                continue;
            }

            // R4 applies when V o-* Y, not only V o-> Y.
            // So the endpoint at V on edge V--Y must be a circle.
            Endpoint endpointAtV = graph.getEndpoint(y, v); // endpoint at v
            if (endpointAtV != Endpoint.CIRCLE) {
                continue;
            }

            discriminatingPathBfs(w, v, y, graph, out, maxLen, checkEcNonadjacency);
        }

        return out;
    }

    /**
     * Search backward from W to find discriminating paths of the form
     * <X, ..., W, V, Y> for V.
     * <p>
     * The interior vertices between X and V must be colliders on the path
     * and parents of Y (or satisfy the relaxed analogue).
     * <p>
     * The colliderPath stored in DiscriminatingPath is [W, ..., first-after-X].
     */
    private static void discriminatingPathBfs(
            Node w, Node v, Node y,
            Graph graph,
            Set<DiscriminatingPath> discriminatingPaths,
            int maxDiscriminatingPathLength,
            boolean checkEcNonadjacency) {

        // Use an indexed path array + bitset for visited — no per-state allocation.
        // body[0] = W, body[1] = next toward V, etc.  We store the spine in a
        // single int[] (node indices) and flip a visited bit rather than copying sets.

        List<Node> nodeList = graph.getNodes();
        int n = nodeList.size();
        Map<Node, Integer> idx = new HashMap<>(n * 2);
        for (int i = 0; i < n; i++) idx.put(nodeList.get(i), i);

        boolean[] visited = new boolean[n];
        visited[idx.get(w)] = true;
        visited[idx.get(v)] = true;
        visited[idx.get(y)] = true;

        // body grows as we go upstream; we store actual Node refs here.
        ArrayDeque<Node> body = new ArrayDeque<>();

        dfsDisc(w, null, v, y, graph, discriminatingPaths,
                maxDiscriminatingPathLength, checkEcNonadjacency,
                visited, idx, body);
    }

    private static void dfsDisc(
            Node t,           // current node
            Node p,           // node toward V (null when t == W)
            Node v,
            Node y,
            Graph graph,
            Set<DiscriminatingPath> discriminatingPaths,
            int maxLen,
            boolean checkEcNonadjacency,
            boolean[] visited,
            Map<Node, Integer> idx,
            ArrayDeque<Node> body) {

        if (Thread.currentThread().isInterrupted()) return;

        // Interior-node check (skip for the initial W, where p == null).
        if (p != null) {
            if (graph.getEndpoint(p, t) != Endpoint.ARROW) return;
            if (checkEcNonadjacency) {
                if (!graph.isParentOf(t, y)) return;
            } else {
                if (!graph.isAdjacentTo(t, y) ||
                        graph.getEndpoint(y, t) == Endpoint.ARROW) return;
            }
        }

        // Explore X such that X *-> t.
        for (Node x : graph.getNodesInTo(t, Endpoint.ARROW)) {
            if (Thread.currentThread().isInterrupted()) break;
            if (x == p || x == v || x == y) continue;
            int xi = idx.get(x);
            if (visited[xi]) continue;

            // body stores [W, ..., t] — append t before recursing.
            body.addLast(t);

            int edgeCount = 1 + body.size(); // edges from x to v through body
            edgeCount += 1;               // edge v–y

            if (maxLen < 0 || edgeCount <= maxLen) {
                LinkedList<Node> bodySnap = new LinkedList<>(body);
                DiscriminatingPath dp =
                        new DiscriminatingPath(x, body.peekFirst() /* W */,
                                v, y, bodySnap, checkEcNonadjacency);
                if (dp.existsIn(graph)) {
                    discriminatingPaths.add(dp);
                }
            }

            // Extend further only if x could itself be interior.
            boolean canExtend;
            if (checkEcNonadjacency) {
                canExtend = graph.isParentOf(x, y);
            } else {
                canExtend = graph.isAdjacentTo(x, y) &&
                        graph.getEndpoint(y, x) != Endpoint.ARROW;
            }

            if (canExtend && (maxLen < 0 || edgeCount < maxLen)) {
                visited[xi] = true;
                dfsDisc(x, t, v, y, graph, discriminatingPaths,
                        maxLen, checkEcNonadjacency, visited, idx, body);
                visited[xi] = false;
            }

            body.removeLast(); // backtrack
        }
    }

    /**
     * Performs FCI orientation on the given graph, including R0 and either the Spirtes or Zhang final orientation
     * rules.
     *
     * @param graph                The graph to orient.
     * @param unshieldedTriples    The set of unshielded triples oriented by R0. This set is updated with new triples.
     * @param excludeSelectionBias whether to exclude selection bias
     */
    public void orient(Graph graph, Set<Triple> unshieldedTriples, boolean excludeSelectionBias) {

        if (verbose) {
            this.logger.log("Starting FCI orientation.");
        }

        ruleR0(graph, unshieldedTriples, excludeSelectionBias);

        if (this.verbose) {
            logger.log("R0");
        }

        // Step CI D. (Zhang's step R4.)
        finalOrientation(graph, excludeSelectionBias);
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
     * @param graph                The graph to orient.
     * @param unshieldedTriples    The set of unshielded triples oriented by R0. This set is updated with new triples.
     * @param excludeSelectionBias True to exclude selection bias, false otherwise.
     */
    public void ruleR0(Graph graph, Set<Triple> unshieldedTriples, boolean excludeSelectionBias) {
        graph.reorientAllWith(Endpoint.CIRCLE);
        fciOrientbk(this.knowledge, graph, graph.getNodes(), excludeSelectionBias);

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
     * Determines the final orientation of the given graph based on the rule set being used. Selection bias is not
     * excluded.
     *
     * @param graph the graph object whose edges are to be oriented
     */
    public void finalOrientation(Graph graph) {
        if (this.completeRuleSetUsed) {
            zhangFinalOrientation(graph, false);
        } else {
            spirtesFinalOrientation(graph);
        }
    }

    /**
     * Orients the graph (in place) according to rules in the graph (FCI step D).
     * <p>
     * Zhang's rules R1-R10.
     * <p>
     * If selection bias is excluded, rules R5-R7 are not applied; applies only to Zhang final orientation.
     *
     * @param graph                a {@link Graph} object
     * @param excludeSelectionBias whether to exclude selection bias.
     * @throws IllegalStateException if a discriminating path cannot be found.
     */
    public void finalOrientation(Graph graph, boolean excludeSelectionBias) {
        if (this.completeRuleSetUsed) {
            zhangFinalOrientation(graph, excludeSelectionBias);
        } else {
            spirtesFinalOrientation(graph);
        }
    }

    /**
     * Iteratively applies rules (in place) to orient the Spirtes final orientation rules in the graph. These are arrow
     * complete.
     *
     * @param graph The graph containing the sprites.
     * @throws IllegalStateException if a discriminating path cannot be found.
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
     * and tail complete. If selection bias is excluded, rules R5-R7 are not applied.
     *
     * @param graph                the graph to apply the final orientation algorithm to
     * @param excludeSelectionBias whether to exclude selection bias
     * @throws IllegalStateException if a discriminating path cannot be found.
     */
    private void zhangFinalOrientation(Graph graph, boolean excludeSelectionBias) {
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

            if (!excludeSelectionBias) {
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
        List<Node> nodes = graph.getNodes();

        for (Node b : nodes) {
            if (Thread.currentThread().isInterrupted()) break;

            List<Node> adj = new ArrayList<>(graph.getAdjacentNodes(b));
            int sz = adj.size();
            if (sz < 3) continue;

            // Collect collider pairs into b: all (a, c) with a *-> b <-* c, a not adj c.
            List<Node[]> colliderPairs = new ArrayList<>();
            for (int i = 0; i < sz; i++) {
                for (int j = i + 1; j < sz; j++) {
                    Node a = adj.get(i), c = adj.get(j);
                    if (!graph.isDefCollider(a, b, c)) continue;
                    if (graph.isAdjacentTo(a, c)) continue;
                    colliderPairs.add(new Node[]{a, c});
                }
            }
            if (colliderPairs.isEmpty()) continue;

            // Candidate θ nodes: adjacent to b with a circle at b.
            List<Node> thetaCandidates = new ArrayList<>();
            for (Node d : adj) {
                if (graph.getEndpoint(d, b) == Endpoint.CIRCLE) {
                    thetaCandidates.add(d);
                }
            }
            if (thetaCandidates.isEmpty()) continue;

            outer:
            for (Node[] pair : colliderPairs) {
                Node a = pair[0], c = pair[1];

                for (Node d : thetaCandidates) {
                    if (d == a || d == c) continue;

                    // θ must be adjacent to both α and γ with circles toward them.
                    if (!graph.isAdjacentTo(a, d)) continue;
                    if (!graph.isAdjacentTo(c, d)) continue;
                    if (graph.getEndpoint(a, d) != Endpoint.CIRCLE) continue;
                    if (graph.getEndpoint(c, d) != Endpoint.CIRCLE) continue;

                    if (!FciOrient.isArrowheadAllowed(d, b, graph, knowledge)) continue;

                    setEndpoint(graph, d, b, Endpoint.ARROW);

                    if (verbose) {
                        logger.log(LogUtilsSearch.edgeOrientedMsg(
                                "R3: Double triangle", graph.getEdge(d, b)));
                    }

                    changeFlag = true;
                    break outer;
                }
            }
        }
    }

    /**
     * R4: If u = ⟨θ, …, α, β, γ⟩ is a discriminating path between θ and γ for β, and β o−∗ γ, then:
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
     * @throws IllegalStateException if a discriminating path cannot be found.
     */
    public void ruleR4(Graph graph) {
        boolean useR4 = true;
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
                List<Callable<Pair<DiscriminatingPath, Boolean>>> tasks = getDiscriminatingPathTasks(graph);

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
                List<Callable<Pair<DiscriminatingPath, Boolean>>> tasks = getDiscriminatingPathTasks(graph);
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

        }

        for (Pair<DiscriminatingPath, Boolean> result : allResults) {
            if (result != null && result.getRight()) {
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
     * @param graph the graph
     * @return the list of tasks
     * @throws IllegalStateException if a discriminating path cannot be found. (This can only be because a path length
     */
    private @NotNull List<Callable<Pair<DiscriminatingPath, Boolean>>> getDiscriminatingPathTasks(Graph graph) {
        Set<DiscriminatingPath> discriminatingPaths = listDiscriminatingPaths(graph, maxDiscriminatingPathLength, true);

        Set<Node> vNodes = new HashSet<>();

        for (DiscriminatingPath discriminatingPath : discriminatingPaths) {
            vNodes.add(discriminatingPath.getV());
        }

        List<Callable<Pair<DiscriminatingPath, Boolean>>> tasks = new ArrayList<>();

        for (DiscriminatingPath discriminatingPath : discriminatingPaths) {
            tasks.add(() -> strategy.doDiscriminatingPathOrientation(discriminatingPath, maxBlockingPathLength, maxDiscriminatingPathLength, graph, vNodes));
        }

        return tasks;
    }

    /**
     * R5: For every remaining α o−o β, if there exists an uncovered circle path p = ⟨α, γ, …, θ, β⟩ between α and β
     * such that α and θ are not adjacent and β and γ are not adjacent, then orient α o−o β and every edge on p as
     * undirected (−−).
     *
     * <p>This rule converts circle paths into undirected chains when they form
     * an uncovered circle path between α and β, thereby ensuring that the resulting PAG correctly represents selection
     * bias relationships.</p>
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
     * R6: If α — β o−∗ γ (where α and γ may or may not be adjacent), then orient β o−∗ γ as β −∗ γ.
     *
     * <p>This rule orients the circle endpoint on β o−∗ γ as a tail when β
     * is connected to α by an undirected edge, ensuring propagation of definite non-collider structure along the
     * chain.</p>
     *
     * @param graph The {@link edu.cmu.tetrad.graph.Graph} being oriented.
     */
    public void ruleR6(Graph graph) {
        for (Edge edge : graph.getEdges()) {
            if (!Edges.isUndirectedEdge(edge)) {
                continue;
            }

            orientR6(graph, edge.getNode1(), edge.getNode2());
            orientR6(graph, edge.getNode2(), edge.getNode1());
        }
    }

    private void orientR6(Graph graph, Node a, Node b) {
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

    /**
     * R7: If α −∘ β o−∗ γ, and α and γ are not adjacent, then orient β o−∗ γ as β −∗ γ.
     *
     * <p>This rule resolves the circle at β by extending the orientation
     * consistently along the partially directed chain from α to γ, provided that α and γ are nonadjacent.</p>
     *
     * @param graph The {@link edu.cmu.tetrad.graph.Graph} being oriented.
     */
    public void ruleR7(Graph graph) {
        for (Edge edge : graph.getEdges()) {
            orientR7(graph, edge.getNode1(), edge.getNode2());
            orientR7(graph, edge.getNode2(), edge.getNode1());
        }
    }

    private void orientR7(Graph graph, Node a, Node b) {
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
     * through an intermediate directed or partially directed chain, ensuring transitive consistency of arrow
     * directions.</p>
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
     * R9: Suppose α ∘→ γ, and let p = ⟨α, β, θ, …, γ⟩ be an uncovered, potentially-directed path from α to γ such that
     * γ and β are not adjacent. Then orient α ∘→ γ as α → γ.
     *
     * <p>This rule finalizes the circle endpoint on α ∘→ γ when α can reach γ
     * through an uncovered potentially-directed path that begins with a non-adjacent β, ensuring consistency with the
     * causal flow implied by the rest of the graph.</p>
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
        // so that the path must be potentially directed, there can be no length 1 or length 2
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
                    this.logger.log("adjacent " + path.get(i) + " to " + path.get(i - 2));
                }

                if (graph.isAdjacentTo(path.getLast(), path.get(1))) {
                    this.logger.log("adjacent gamma = " + path.getLast() + " to beta = " + path.get(1));
                }
            }
        }

        this.changeFlag = true;
        return true;
    }

    /**
     * R10 (Zhang 2008 FCI orientation rule).
     * <p>
     * Suppose alpha o-&gt; gamma, beta -&gt; gamma &lt;- theta.
     * Let p1 be an uncovered potentially directed path from alpha to beta,
     * and p2 be an uncovered potentially directed path from alpha to theta.
     * Let mu be the vertex adjacent to alpha on p1 (mu could be beta), and
     * omega be the vertex adjacent to alpha on p2 (omega could be theta).
     * If mu and omega are distinct and nonadjacent, then orient
     * alpha o-> gamma as alpha -> gamma.
     *
     * @param alpha the node α
     * @param gamma the node γ
     * @param graph the working graph
     */
    public void ruleR10(Node alpha, Node gamma, Graph graph) {
        Edge e = graph.getEdge(alpha, gamma);
        if (e == null || !e.equals(Edges.partiallyOrientedEdge(alpha, gamma))) return;

        List<Node> intoGamma = new ArrayList<>(graph.getNodesInTo(gamma, Endpoint.ARROW));
        intoGamma.remove(alpha);
        if (intoGamma.size() < 2) return;

        // Keep only nodes with a definite tail into gamma (beta -> gamma).
        intoGamma.removeIf(n -> graph.getEndpoint(gamma, n) != Endpoint.TAIL);
        if (intoGamma.size() < 2) return;

        List<Node> adjAlpha = new ArrayList<>(graph.getAdjacentNodes(alpha));
        if (adjAlpha.isEmpty()) return;

        // Precompute: for each first-hop 'hop' from alpha, which targets in
        // intoGamma are reachable via an uncovered PD path through that hop?
        // Store as hop -> Set<reachable target>.
        Set<Node> targetSet = new HashSet<>(intoGamma);
        Map<Node, Set<Node>> hopReach = new HashMap<>();

        for (Node hop : adjAlpha) {
            if (graph.getEndpoint(hop, alpha) == Endpoint.ARROW) continue; // not PD out of alpha

            Set<Node> reachable = new HashSet<>();

            if (targetSet.contains(hop)) {
                reachable.add(hop); // trivial length-1 path
            }

            // DFS from hop, collecting all reachable targets.
            Set<Node> visited = new HashSet<>();
            visited.add(alpha);
            visited.add(hop);
            collectUncoveredPdReach(alpha, hop, targetSet, graph, visited, reachable);

            if (!reachable.isEmpty()) {
                hopReach.put(hop, reachable);
            }
        }

        if (hopReach.size() < 2) return; // need at least two distinct first-hops

        List<Node> hops = new ArrayList<>(hopReach.keySet());

        for (int i = 0; i < intoGamma.size(); i++) {
            Node beta = intoGamma.get(i);

            for (int j = i + 1; j < intoGamma.size(); j++) {
                Node theta = intoGamma.get(j);

                // Find mu: a hop that can reach beta.
                Node mu = null, omega = null;

                for (Node hop : hops) {
                    Set<Node> r = hopReach.get(hop);
                    if (r.contains(beta) && mu == null) {
                        mu = hop;
                        continue;
                    }
                    if (r.contains(theta) && omega == null) omega = hop;
                    if (mu != null && omega != null) break;
                }

                // Also check if the same hop covers both roles — that's not allowed
                // (mu and omega must be distinct).
                if (mu == null || omega == null || mu == omega) {
                    // Try swapping: hop reaches theta first, then beta.
                    mu = null;
                    omega = null;
                    for (Node hop : hops) {
                        Set<Node> r = hopReach.get(hop);
                        if (r.contains(theta) && mu == null) {
                            mu = hop;
                            continue;
                        }
                        if (r.contains(beta) && omega == null) omega = hop;
                        if (mu != null && omega != null) break;
                    }
                    if (mu == null || omega == null || mu == omega) continue;
                    // swap so mu->beta, omega->theta convention
                    Node tmp = mu;
                    mu = omega;
                    omega = tmp;
                }

                if (graph.isAdjacentTo(mu, omega)) continue;

                setEndpoint(graph, gamma, alpha, Endpoint.TAIL);

                if (verbose) {
                    logger.log(LogUtilsSearch.edgeOrientedMsg("R10: ", graph.getEdge(gamma, alpha))
                            + " beta=" + beta + ", theta=" + theta
                            + ", mu=" + mu + ", omega=" + omega);
                }

                changeFlag = true;
                return;
            }
        }
    }

    /**
     * Collects all nodes in targetSet reachable from 'curr' (first-hop already taken)
     * via an uncovered potentially-directed simple path starting alpha--prev--curr.
     */
    private void collectUncoveredPdReach(
            Node prev, Node curr,
            Set<Node> targets,
            Graph graph,
            Set<Node> visited,
            Set<Node> reachable) {

        for (Node next : graph.getAdjacentNodes(curr)) {
            if (next == prev || visited.contains(next)) continue;
            if (graph.getEndpoint(next, curr) == Endpoint.ARROW) continue; // not PD
            if (graph.isAdjacentTo(prev, next)) continue; // not uncovered

            if (targets.contains(next)) reachable.add(next);

            visited.add(next);
            collectUncoveredPdReach(curr, next, targets, graph, visited, reachable);
            visited.remove(next);
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
            TetradLogger.getInstance().log("WARNING: path length must be -1 (unlimited) or >= 4" +
                    "in order to find discriminating paths: " + maxDiscriminatingPathLength);
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
     * Orient the edges of a graph based on the given knowledge.
     *
     * @param bk                   The knowledge containing forbidden and required edges.
     * @param graph                The graph to be oriented.
     * @param variables            The list of nodes in the graph.
     * @param excludeSelectionBias If true, selection bias is excluded and forbidden edges are enforced in the standard
     *                             way. If false (default), selection bias is allowed and we do NOT enforce forbidden
     *                             edges by forcing an arrowhead.
     */
    public void fciOrientbk(Knowledge bk, Graph graph, List<Node> variables, boolean excludeSelectionBias) {
        if (verbose) {
            this.logger.log("Starting BK Orientation.");
        }

        // -------------------------------------------------------------------------
        // Forbidden edges: "from -> to" is forbidden.
        //
        // If selection bias is EXCLUDED (excludeSelectionBias == true):
        //     enforce by orienting  to  *-> from  (standard FCI behavior)
        //
        // If selection bias is ALLOWED (excludeSelectionBias == false):
        //     do NOT enforce via orientation; leave endpoints unchanged.
        // -------------------------------------------------------------------------
        for (Iterator<KnowledgeEdge> it = bk.forbiddenEdgesIterator(); it.hasNext(); ) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            KnowledgeEdge edge = it.next();

            Node from = GraphSearchUtils.translate(edge.getFrom(), variables);
            Node to = GraphSearchUtils.translate(edge.getTo(), variables);

            if (from == null || to == null) {
                continue;
            }
            if (graph.getEdge(from, to) == null) {
                continue;
            }

            // If we ALLOW selection bias, we skip enforcement entirely.
            if (!excludeSelectionBias) {
                continue;
            }

            // Enforce forbidden edge when selection bias is excluded.
            if (!FciOrient.isArrowheadAllowed(to, from, graph, knowledge)) {
                return;
            }

            // Orient: to *-> from   (arrowhead at 'from')
            setEndpoint(graph, to, from, Endpoint.ARROW);

            if (verbose) {
                this.logger.log(LogUtilsSearch.edgeOrientedMsg("Knowledge", graph.getEdge(to, from)));
            }

            this.changeFlag = true;
        }

        // -------------------------------------------------------------------------
        // Required edges: "from -> to" must hold.
        // Unaffected by selection bias policy.
        // -------------------------------------------------------------------------
        for (Iterator<KnowledgeEdge> it = bk.requiredEdgesIterator(); it.hasNext(); ) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            KnowledgeEdge edge = it.next();

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

            // Orient: from ---*> to  (tail at from, arrow at to)
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
     * Sets the maximum allowed blocking path length.
     *
     * @param maxBlockingPathLength the maximum length of the blocking path, specified as an integer
     */
    public void setRecursionDepth(int maxBlockingPathLength) {
        this.maxBlockingPathLength = maxBlockingPathLength;
    }
}