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

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.test.CachingIndependenceTest;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.utils.SepsetProducer;
import edu.cmu.tetrad.search.utils.SepsetsMaxP;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.SublistGenerator;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.*;

/**
 * Cyclic Causal Discovery (CCD) after Richardson.
 *
 * <p>Richardson, T. S. (2013). A discovery algorithm for directed cyclic graphs. arXiv:1302.3599.</p>
 * <p>See also Chapter 7 of: Glymour &amp; Cooper (1999), <i>Computation, Causation, and Discovery</i>.</p>
 *
 * <p>Input: a conditional independence oracle/test for data from a directed cyclic model (DCG).
 * Output: a cyclic PAG (with underline/dotted-underline annotations) representing the Markov equivalence class.</p>
 *
 * <p>Note: Background knowledge is supported as <b>forbidden directed edges only</b>. Required edges are
 * intentionally ignored (and should not be supplied) to avoid forcing orientations not justified by CCD.</p>
 */
public final class Ccd implements IGraphSearch {

    /**
     * Fixed node list from the test.
     */
    private final List<Node> nodes;

    /**
     * Cached independence test.
     */
    private IndependenceTest test;

    /**
     * Whether to apply R1 push-away rule (default: true).
     */
    private boolean applyR1 = true;

    /**
     * Verbose logging toggle.
     */
    private boolean verbose;

    /**
     * Maximum conditioning depth; -1 means unlimited.
     */
    private int depth = -1;

    /**
     * Background knowledge: only forbidden directed edges are honored.
     */
    private Knowledge knowledge = new Knowledge();

    /**
     * Constructs a Ccd object with the given independence test.
     *
     * @param test the IndependenceTest to be used within the CCD algorithm. If the test is not an instance of
     *             {@code CachingIndependenceTest}, a caching wrapper will be applied to the provided test. Must not be
     *             null.
     * @throws NullPointerException if the provided test is null.
     */
    public Ccd(IndependenceTest test) {
        if (test == null) throw new NullPointerException("Test is not provided");
        this.test = (test instanceof CachingIndependenceTest) ? test : new CachingIndependenceTest(test);
        this.nodes = this.test.getVariables();
    }

    /**
     * Executes the CCD search algorithm to infer a causal graph based on statistical independence tests. The algorithm
     * applies a series of steps, including adjacency search, orientation rules, and edge propagation, while taking into
     * account background knowledge (if provided) and specific algorithm configurations (e.g., depth). A verbose logging
     * option is available for detailed execution monitoring.
     * <p>
     * The resulting graph is modified with orientations and additional structures based on statistical properties and
     * algorithmic rules. These modifications include reoriented endpoints, the addition of non-collider and collider
     * triples, and propagation of orientation changes to ensure consistency with the underlying causal structure. A
     * final step attempts to direct edges where permissible according to the background knowledge.
     *
     * @return the inferred causal graph as a {@code Graph} object after applying the CCD search algorithm.
     * @throws InterruptedException if the process is interrupted during execution.
     */
    public Graph search() throws InterruptedException {
        Map<Triple, Set<Node>> supSepsets = new HashMap<>();

        if (verbose) TetradLogger.getInstance().log("CCD: Step A — Fast Adjacency Search (FAS)");
        Fas fas = new Fas(this.test);
        Graph psi = fas.search();
        psi.reorientAllWith(Endpoint.CIRCLE);

        // Use Max-P sepsets (depth-aware) for stability downstream.
        SepsetProducer sepsets = new SepsetsMaxP(psi, test, depth);

        stepB(psi, sepsets);
        stepC(psi, sepsets);
        stepD(psi, sepsets, supSepsets);
        stepE(supSepsets, psi);
        stepF(psi, sepsets, supSepsets);

        orientAwayFromArrow(psi);
        return psi;
    }

    // ------------------------- Public configuration --------------------------

    /**
     * Retrieves the current independence test being used.
     *
     * @return the current IndependenceTest instance.
     */
    public IndependenceTest getTest() {
        return test;
    }

    /**
     * Sets the independence test to be used during the algorithm's execution. The provided test must have the same
     * variable set as the current test. A caching wrapper will be applied to the test if it is not already cached.
     *
     * @param test the new independence test to be set. Must not be null and must have the same variable set as the
     *             existing test.
     * @throws NullPointerException     if the provided test is null.
     * @throws IllegalArgumentException if the variable set of the new test differs from the variable set of the current
     *                                  test.
     */
    public void setTest(IndependenceTest test) {
        Objects.requireNonNull(test, "test");
        Set<Node> oldSet = new HashSet<>(this.test.getVariables());
        Set<Node> newSet = new HashSet<>(test.getVariables());
        if (!oldSet.equals(newSet)) {
            throw new IllegalArgumentException("New test must have the same variable set as the existing test.");
        }
        this.test = (test instanceof CachingIndependenceTest) ? test : new CachingIndependenceTest(test);
    }

    /**
     * Determines whether the R1 orientation rule is set to be applied during the search process.
     *
     * @return true if the R1 orientation rule is enabled; false otherwise.
     */
    public boolean isApplyR1() {
        return this.applyR1;
    }

    /**
     * Sets whether the R1 orientation rule should be applied during the search process.
     *
     * @param applyR1 a boolean indicating whether to apply the R1 rule. If true, the R1 rule will be applied;
     *                otherwise, it will not.
     */
    public void setApplyR1(boolean applyR1) {
        this.applyR1 = applyR1;
    }

    /**
     * Sets whether verbose output should be enabled for the current process.
     *
     * @param verbose a boolean indicating whether verbose output is enabled. If true, detailed logs or messages may be
     *                printed. If false, minimal or no verbose output will be shown.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
        test.setVerbose(verbose);
    }

    /**
     * Sets the depth parameter, which may affect the algorithm's behavior during its operation.
     *
     * @param depth an integer representing the depth limit or level of recursion to be applied. A higher value
     *              typically increases the scope or complexity taken into account.
     */
    public void setDepth(int depth) {
        this.depth = depth;
    }

    /**
     * Set background knowledge (forbidden edges). Required edges are not supported.
     *
     * @param knowledge the knowledge to be set
     */
    public void setKnowledge(Knowledge knowledge) {
        if (knowledge == null) throw new NullPointerException("knowledge must not be null");

        if (!knowledge.getListOfRequiredEdges().isEmpty()) {
            throw new IllegalArgumentException("Required edges are not supported for CCD.");
        }

        this.knowledge = knowledge;
    }

    // ------------------------------ Algorithm --------------------------------

    /**
     * Step B — Add underlines (non-colliders) and colliders for unshielded triples.
     */
    private void stepB(Graph psi, SepsetProducer sepsets) throws InterruptedException {
        if (verbose) TetradLogger.getInstance().log("CCD: Step B — Underlines & Colliders");

        for (Node b : this.nodes) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();

            List<Node> adj = new ArrayList<>(psi.getAdjacentNodes(b));
            if (adj.size() < 2) continue;

            ChoiceGenerator cg = new ChoiceGenerator(adj.size(), 2);
            int[] idx;
            while ((idx = cg.next()) != null) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();

                Node a = adj.get(idx[0]);
                Node c = adj.get(idx[1]);

                // Only unshielded triples
                if (psi.isAdjacentTo(a, c)) continue;

                Set<Node> S = sepsets.getSepset(a, c, -1, null);
                if (S == null) continue;

                if (S.contains(b)) {
                    // non-collider at b
                    psi.addUnderlineTriple(a, b, c);
                    if (verbose)
                        TetradLogger.getInstance().log("StepB: underline (non-collider) " + a + "-" + b + "-" + c + " ; S(a,c)=" + S);
                } else {
                    // collider at b: attempt a->b and c->b, each vetoable by knowledge
                    boolean ok1 = addDirectedIfAllowed(psi, a, b);
                    boolean ok2 = addDirectedIfAllowed(psi, c, b);

                    if (verbose) {
                        if (ok1 && ok2)
                            TetradLogger.getInstance().log("StepB: collider " + a + "->" + b + "<-" + c + " ; S(a,c)=" + S);
                        else if (ok1 ^ ok2)
                            TetradLogger.getInstance().log("StepB: half-collider (knowledge veto) at " + b + " from " +
                                                           (ok1 ? a : c) + " ; S(a,c)=" + S);
                        else
                            TetradLogger.getInstance().log("StepB: collider semantic recorded but both incoming arrows vetoed by knowledge at " + b);
                    }
                    // Even if both arrows vetoed, the collider semantic is effectively known to Steps D–F via CCD logic.
                }
            }
        }
    }

    /**
     * Step C — Propagate some orientations (with change-flag until quiescence).
     */
    private void stepC(Graph psi, SepsetProducer sepsets) throws InterruptedException {
        if (verbose) TetradLogger.getInstance().log("CCD: Step C — Orientation propagation");

        boolean changed;
        do {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            changed = false;

            for (Edge edge : new ArrayList<>(psi.getEdges())) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();

                Node x = edge.getNode1();
                Node y = edge.getNode2();

                List<Node> adjx = psi.getAdjacentNodes(x);
                List<Node> adjy = psi.getAdjacentNodes(y);

                // Skip if some node->x and underline(y, x, node)
                boolean skip = false;
                for (Node node : new ArrayList<>(adjx)) {
                    Edge ex = psi.getEdge(node, x);
                    if (ex != null && ex.getEndpoint(x) == Endpoint.ARROW && psi.isUnderlineTriple(y, x, node)) {
                        skip = true;
                        break;
                    }
                }
                if (skip) continue;

                for (Node a : this.nodes) {
                    if (a == x || a == y) continue;
                    if (adjx.contains(a) || adjy.contains(a)) continue;

                    // Orientable? require o? at y-x side
                    if (!(psi.getEndpoint(y, x) == Endpoint.CIRCLE &&
                          (psi.getEndpoint(x, y) == Endpoint.CIRCLE || psi.getEndpoint(x, y) == Endpoint.TAIL))) {
                        continue;
                    }

                    Set<Node> sepset = sepsets.getSepset(a, y, -1, null);
                    if (sepset == null) continue;
                    if (sepset.contains(x)) continue;

                    if (!sepsets.isIndependent(a, x, new HashSet<>(sepset))) {
                        // Attempt y -> x (vetoable)
                        if (addDirectedIfAllowed(psi, y, x)) {
                            orientAwayFromArrow(y, x, psi);
                            changed = true;
                        }
                        break;
                    }
                }
            }
        } while (changed);
    }

    /**
     * Step D — Add dotted-underline triples and compute supSepsets.
     */
    private void stepD(Graph psi, SepsetProducer sepsets, Map<Triple, Set<Node>> supSepsets) throws InterruptedException {
        if (verbose) TetradLogger.getInstance().log("CCD: Step D — Dotted underlines");

        Map<Node, List<Node>> local = new HashMap<>();
        for (Node node : psi.getNodes()) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            local.put(node, local(psi, node));
        }

        for (Node node : this.nodes) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            doNodeStepD(psi, sepsets, supSepsets, local, node);
        }
    }

    private void doNodeStepD(Graph psi, SepsetProducer sepsets, Map<Triple, Set<Node>> supSepsets,
                             Map<Node, List<Node>> local, Node b) throws InterruptedException {
        List<Node> adj = new ArrayList<>(psi.getAdjacentNodes(b));
        if (adj.size() < 2) return;

        ChoiceGenerator gen = new ChoiceGenerator(adj.size(), 2);
        int[] choice;

        while ((choice = gen.next()) != null) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();

            List<Node> _adj = GraphUtils.asList(choice, adj);
            Node a = _adj.get(0);
            Node c = _adj.get(1);

            if (!psi.isDefCollider(a, b, c)) continue;

            Set<Node> S = sepsets.getSepset(a, c, -1, null);
            if (S == null) continue;

            ArrayList<Node> TT = new ArrayList<>(local.getOrDefault(a, Collections.emptyList()));
            TT.removeAll(S);
            TT.remove(b);
            TT.remove(c);

            int kMax = (depth < 0) ? -1 : Math.min(depth, TT.size());
            SublistGenerator gen2 = new SublistGenerator(TT.size(), kMax);
            int[] choice2;

            while ((choice2 = gen2.next()) != null) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();

                Set<Node> T = GraphUtils.asSet(choice2, TT);
                Set<Node> B = new HashSet<>(T);
                B.addAll(S);
                B.add(b);

                if (sepsets.isIndependent(a, c, new HashSet<>(B))) {
                    psi.addDottedUnderlineTriple(a, b, c);
                    supSepsets.put(new Triple(a, b, c), B);
                    break;
                }
            }
        }
    }

    /**
     * Step E — Use supSepsets to orient edges out of b towards neighbors in A∪C neighborhoods.
     */
    private void stepE(Map<Triple, Set<Node>> supSepset, Graph psi) throws InterruptedException {
        if (verbose) TetradLogger.getInstance().log("CCD: Step E — Orientation propagation via supSepsets");

        for (Triple triple : new ArrayList<>(psi.getDottedUnderlines())) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();

            Set<Node> sup = supSepset.get(triple);
            if (sup == null) continue;

            Node a = triple.getX();
            Node b = triple.getY();
            Node c = triple.getZ();

            // Neighbors of a
            for (Node d : new ArrayList<>(psi.getAdjacentNodes(a))) {
                if (d == b) continue;
                if (psi.getEndpoint(b, d) != Endpoint.CIRCLE) continue;

                if (sup.contains(d)) {
                    // B*-oD -> B*-D (tail placement only; always allowed)
                    psi.setEndpoint(b, d, Endpoint.TAIL);
                } else if (psi.getEndpoint(d, b) != Endpoint.ARROW) {
                    // Try B -> D (vetoable)
                    addDirectedIfAllowed(psi, b, d); // if vetoed, leave as-is
                }
            }
            // Neighbors of c
            for (Node d : new ArrayList<>(psi.getAdjacentNodes(c))) {
                if (d == b) continue;
                if (psi.getEndpoint(b, d) != Endpoint.CIRCLE) continue;

                if (sup.contains(d)) {
                    psi.setEndpoint(b, d, Endpoint.TAIL);
                } else if (psi.getEndpoint(d, b) != Endpoint.ARROW) {
                    addDirectedIfAllowed(psi, b, d);
                }
            }
        }
    }

    /**
     * Step F — Further propagation using d-connection given supSepset ∪ {d}.
     */
    private void stepF(Graph psi, SepsetProducer sepsets, Map<Triple, Set<Node>> supSepsets) throws InterruptedException {
        if (verbose) TetradLogger.getInstance().log("CCD: Step F — More orientations via d-connection checks");

        for (Triple triple : new ArrayList<>(psi.getDottedUnderlines())) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();

            Node a = triple.getX();
            Node b = triple.getY();
            Node c = triple.getZ();

            Set<Node> sup = supSepsets.get(triple);
            if (sup == null) continue;

            Set<Node> adj = new HashSet<>(psi.getAdjacentNodes(a));
            adj.addAll(psi.getAdjacentNodes(c));

            for (Node d : new ArrayList<>(adj)) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();

                if (psi.getEndpoint(b, d) != Endpoint.CIRCLE) continue;
                if (psi.getEndpoint(d, b) == Endpoint.ARROW) continue;
                if (psi.isAdjacentTo(a, d) && psi.isAdjacentTo(c, d)) continue;
                if (!psi.isAdjacentTo(b, d)) continue;

                Set<Node> supSepUnionD = new HashSet<>(sup);
                supSepUnionD.add(d);

                if (!sepsets.isIndependent(a, c, new HashSet<>(supSepUnionD))) {
                    // Try B -> D (vetoable)
                    if (addDirectedIfAllowed(psi, b, d)) {
                        orientAwayFromArrow(b, d, psi);
                    }
                }
            }
        }
    }

    // ------------------------------- Helpers ---------------------------------

    /**
     * Attempt to orient u -> v if not forbidden by knowledge. Returns true if applied; false if vetoed (or if the edge
     * vanished concurrently).
     */
    private boolean addDirectedIfAllowed(Graph g, Node u, Node v) {
        // Knowledge forbids orientation u->v?
        if (knowledge != null && knowledge.isForbidden(u.getName(), v.getName())) return false;

        // Proceed with orientation
        g.removeEdge(u, v);            // idempotent remove
        g.addDirectedEdge(u, v);       // tail at u, arrow at v
        return true;
    }

    /**
     * Local expansion around x: adj(x) plus z where x-y-z is a definite collider.
     */
    private List<Node> local(Graph psi, Node x) {
        Set<Node> nodes = new HashSet<>(psi.getAdjacentNodes(x));
        for (Node y : new HashSet<>(nodes)) {
            for (Node z : psi.getAdjacentNodes(y)) {
                if (psi.isDefCollider(x, y, z) && z != x) {
                    nodes.add(z);
                }
            }
        }
        return new ArrayList<>(nodes);
    }

    /**
     * Top-level push-away pass over all arrowheads (iterate over snapshot).
     */
    private void orientAwayFromArrow(Graph graph) throws InterruptedException {
        for (Edge e : new ArrayList<>(graph.getEdges())) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();

            Node n1 = e.getNode1();
            Node n2 = e.getNode2();
            Edge cur = graph.getEdge(n1, n2);
            if (cur == null) continue;

            if (cur.pointsTowards(n1)) {
                orientAwayFromArrow(n2, n1, graph);
            } else if (cur.pointsTowards(n2)) {
                orientAwayFromArrow(n1, n2, graph);
            }
        }
    }

    /**
     * Apply R1 push-away from a->b to b's nondirected neighbors, recursively (veto-aware).
     */
    private void orientAwayFromArrow(Node a, Node b, Graph graph) {
        if (!isApplyR1()) return;
        for (Node c : new ArrayList<>(graph.getAdjacentNodes(b))) {
            if (c == a) continue;
            orientAwayFromArrowVisit(a, b, c, graph);
        }
    }

    /**
     * DFS-style push-away with backtracking safety and knowledge veto.
     */
    private boolean orientAwayFromArrowVisit(Node a, Node b, Node c, Graph graph) {
        Edge bc = graph.getEdge(b, c);
        if (bc == null || !Edges.isNondirectedEdge(bc)) return false;
        if (!graph.isUnderlineTriple(a, b, c)) return false;
        if (bc.pointsTowards(b)) return false;

        // Try b -> c (vetoable)
        if (!addDirectedIfAllowed(graph, b, c)) return false;

        for (Node d : new ArrayList<>(graph.getAdjacentNodes(c))) {
            if (d == b) return true;
            Edge cur = graph.getEdge(b, c);
            if (cur == null) return true; // edge removed elsewhere
            if (!orientAwayFromArrowVisit(b, c, d, graph)) {
                graph.removeEdge(b, c);
                graph.addEdge(cur);
            }
        }
        return true;
    }
}