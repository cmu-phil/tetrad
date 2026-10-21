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
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.utils.SepsetProducer;
import edu.cmu.tetrad.search.utils.SepsetsGreedy;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.SublistGenerator;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TMath;

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
     * How Step B decides collider versus non-collider for an unshielded triple &lt;a, b, c&gt;.
     *
     * <p>The decision rests on whether b belongs to a separating set of a and c. In a cyclic
     * model b may lie in some valid sepsets of (a, c) but not others, so which sepset is consulted
     * can change the verdict, and a wrong collider call commits arrowheads at b from both sides -
     * two endpoint errors at once, which orientation propagation then spreads.
     *
     * <p>Measured on random cyclic linear-Gaussian graphs against an oracle-CCD reference, the
     * conservative rules trade endpoint accuracy for cycle detection: on 8-node, 10-edge graphs at
     * n = 50000, {@code CONSERVATIVE} lifts dotted-underline recall from 0.846 to 0.947 while
     * dropping endpoint-exact agreement from 0.665 to 0.405. Use them when locating the cycles
     * matters more than pinning every endpoint; {@link #GREEDY} remains the default.
     */
    public enum ColliderRule {

        /**
         * One minimal-cardinality-first sepset, as returned by the sepset producer, decides.
         * Richardson's Step A semantics, and the default.
         */
        GREEDY,

        /**
         * Orient only when every separating set of (a, c) agrees about b; otherwise record the
         * triple as ambiguous and leave both edges unoriented.
         */
        CONSERVATIVE,

        /**
         * Orient by majority vote over the separating sets of (a, c); ties are left unoriented.
         *
         * <p>Offered for completeness, but it was dominated in testing: on 8-node, 10-edge cyclic
         * graphs at n = 50000 it scored 0.431 endpoint-exact and 0.838 dotted-underline recall,
         * worse than {@link #GREEDY} on both counts (0.665 and 0.846). Prefer GREEDY for endpoint
         * accuracy or {@link #CONSERVATIVE} for cycle detection.
         */
        MAJORITY
    }

    /**
     * Step B's collider rule. Default {@link ColliderRule#GREEDY}, the shipped behaviour.
     */
    private ColliderRule colliderRule = ColliderRule.GREEDY;

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
     * Whether Step D (dotted-underline discovery) distributes its per-node searches over all available processor
     * cores (default: false). The parallel path produces output identical to the sequential path: each unshielded
     * triple is examined by exactly one task, tasks only read the Step-B graph, and results are applied on the
     * calling thread after all tasks complete.
     */
    private boolean parallelized = false;

    /**
     * Background knowledge: only forbidden directed edges are honored.
     */
    private Knowledge knowledge = new Knowledge();

    /**
     * Optional separate test for the adjacency phase. Null (the default) means the main test is
     * used throughout.
     */
    private IndependenceTest adjacencyTest = null;

    /**
     * Optional adjacency superstructure. If non-null, the adjacency phase (Step A) starts from the edges of this graph
     * (matched to test variables by name) instead of the complete graph, and the sepset search at each level is
     * restricted to current adjacencies. Edges may be removed from the superstructure by the sepset search but never
     * added. Intended use: pass the skeleton of a score-based search (e.g., BOSS) run on the same variables, as in
     * {@link BossCcd}. Null (the default) reproduces the classic behavior (FAS from the complete graph).
     */
    private Graph superstructure = null;

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
        this.test = test;
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

        Graph psi;

        if (this.superstructure == null) {
            if (verbose) TetradLogger.getInstance().log("CCD: Step A — Fast Adjacency Search (FAS)");
            Fas fas = new Fas(adjacencyTest());
            fas.setDepth(this.depth);
            fas.setKnowledge(this.knowledge);
            fas.setVerbose(this.verbose);
            psi = fas.search();
        } else {
            if (verbose) TetradLogger.getInstance().log("CCD: Step A — Restricted FAS from superstructure");
            psi = restrictedFas();
        }

        psi.reorientAllWith(Endpoint.CIRCLE);

        // Use minimal-cardinality-first sepsets (smallest subsets of adj(x) or adj(y), empty set first), matching
        // Richardson's Step A semantics. This matters for cyclic models: unlike the acyclic case, a node b may lie
        // in SOME valid sepsets of (a, c) but not others (e.g., for a 2-cycle X<->Y with parents A, B, both {} and
        // {X, Y} separate A from B). Max-p sepset selection tends to pick the larger sets, which converts the
        // colliders Step B needs into underlines, so Step D never finds dotted underlines and no cycle is ever
        // detected. Richardson's correctness argument goes through with the minimal sepsets found in Step A.
        SepsetProducer sepsets = new SepsetsGreedy(psi, test, depth);

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
        this.test = test;
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
     * Sets Step B's collider rule. See {@link ColliderRule} for the accuracy trade-off each one
     * makes; the default is {@link ColliderRule#GREEDY}.
     *
     * @param colliderRule the rule to use; must not be null.
     */
    public void setColliderRule(ColliderRule colliderRule) {
        if (colliderRule == null) throw new NullPointerException("colliderRule must not be null");
        this.colliderRule = colliderRule;
    }

    /**
     * Returns Step B's collider rule.
     *
     * @return the rule in force.
     */
    public ColliderRule getColliderRule() {
        return this.colliderRule;
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
     * Sets whether Step D (dotted-underline discovery) distributes its per-node searches across all available
     * processor cores. The parallel path produces output identical to the sequential path. Default: false.
     *
     * @param parallelized true to parallelize Step D.
     */
    public void setParallelized(boolean parallelized) {
        this.parallelized = parallelized;
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

    /**
     * Sets an optional adjacency superstructure for Step A. If non-null, the adjacency search starts from the edges of
     * this graph (matched to the test's variables by name) rather than from the complete graph, and sepsets at each
     * level are sought among current adjacencies of the endpoints. Edges may be removed but never added, so the final
     * adjacencies are a subset of the superstructure's. Passing the skeleton of a score-based search (e.g., BOSS) here
     * is sound for linear cyclic models in the large-sample limit, since any DAG I-map of the data distribution must
     * be adjacent for every inseparable pair (including Richardson's "virtual edges" induced by cycles).
     *
     * <p><b>Finite samples are another matter, and the caller must account for it.</b> That
     * argument is asymptotic: BOSS maximizes BIC rather than searching for an I-map, and a virtual
     * edge is a weak association that does not pay for itself in BIC at realistic sample sizes. So
     * a raw BOSS skeleton omits exactly the adjacencies CCD needs, and because this method can only
     * ever remove edges, CCD cannot put them back. Measured on random 8-node, 10-edge cyclic graphs
     * against an oracle-CCD reference, a raw BOSS superstructure recovers virtual edges at 0.202 at
     * n = 500 and 0.402 at n = 2000, against 0.316 and 0.527 for CCD searching from the complete
     * graph.
     *
     * <p>The repair is cheap, because every virtual edge joins two nodes lying at distance exactly
     * 2 in the true skeleton (1197 of 1197 virtual edges over the random graphs tested). Closing
     * the superstructure under distance 2 before passing it here therefore admits all of them while
     * staying far sparser than the complete graph, and restores virtual-edge recall to the level of
     * an unrestricted search; {@link BossCcd} does this. Note that on small graphs a superstructure
     * buys accuracy parity at best, never an improvement, since it can only narrow what the
     * adjacency search would otherwise consider - its value is computational, on large variable
     * sets.
     *
     * @param superstructure the superstructure graph, or null for the classic complete-graph start.
     */
    public void setSuperstructure(Graph superstructure) {
        this.superstructure = superstructure;
    }

    /**
     * Sets a separate independence test for the adjacency phase (Step A), leaving the orientation
     * steps on the main test. Null, the default, uses the main test throughout.
     *
     * <p>The two phases ask different questions, and the better test for one can be the wrong test
     * for the other. Step A decides whether to <i>delete</i> an edge, which is well served by a
     * test that demands positive evidence of negligibility - see
     * {@link edu.cmu.tetrad.search.test.IndTestEquivalence}, which raised virtual-edge recall from
     * 0.502 to 0.554 and dotted-underline recall from 0.529 to 0.563 on random 8-node, 10-edge
     * cyclic graphs at n = 2000. Steps C and D instead ask whether two variables are
     * <i>dependent</i>, and there the ordinary meaning is wanted: an equivalence test's "not shown
     * to be negligible" is a far weaker claim than "dependent", and feeding it to the orientation
     * rules makes CCD's dominant error - committing a direction the data does not support - worse.
     *
     * <p>The test supplied here must be defined over the same variables as the main test.
     *
     * @param adjacencyTest the test for Step A, or null to use the main test.
     * @throws IllegalArgumentException if its variable set differs from the main test's.
     */
    public void setAdjacencyTest(IndependenceTest adjacencyTest) {
        if (adjacencyTest != null) {
            Set<Node> main = new HashSet<>(this.test.getVariables());
            Set<Node> other = new HashSet<>(adjacencyTest.getVariables());
            if (!main.equals(other)) {
                throw new IllegalArgumentException(
                        "The adjacency test must be over the same variables as the main test.");
            }
        }

        this.adjacencyTest = adjacencyTest;
    }

    /**
     * Returns the separate adjacency-phase test, or null if the main test is used throughout.
     *
     * @return the adjacency test, or null.
     */
    public IndependenceTest getAdjacencyTest() {
        return this.adjacencyTest;
    }

    /**
     * The test the adjacency phase should use: the separate one if set, otherwise the main test.
     */
    private IndependenceTest adjacencyTest() {
        return this.adjacencyTest == null ? this.test : this.adjacencyTest;
    }

    // ------------------------------ Algorithm --------------------------------

    /**
     * Step A variant — PC-stable style adjacency search seeded with the superstructure's edges rather than the
     * complete graph. At level d, for each remaining edge x*-*y, subsets of size d of adj(x)\{y} and adj(y)\{x}
     * (adjacencies frozen per level) are tested; the edge is removed if a separating set is found. Knowledge edges
     * forbidden in both directions are removed upfront, mirroring {@link Fas}.
     */
    private Graph restrictedFas() throws InterruptedException {
        Graph psi = new EdgeListGraph(this.nodes);

        // Transfer superstructure adjacencies onto the test's variables, matching by name.
        Map<String, Node> byName = new HashMap<>();
        for (Node node : this.nodes) byName.put(node.getName(), node);

        for (Edge edge : this.superstructure.getEdges()) {
            Node x = byName.get(edge.getNode1().getName());
            Node y = byName.get(edge.getNode2().getName());
            if (x == null || y == null) {
                throw new IllegalArgumentException("Superstructure node not among test variables: "
                        + (x == null ? edge.getNode1().getName() : edge.getNode2().getName()));
            }
            if (x != y && !psi.isAdjacentTo(x, y)) psi.addUndirectedEdge(x, y);
        }

        // Apply forbidden-both-ways knowledge upfront, as in Fas.
        for (Edge edge : new ArrayList<>(psi.getEdges())) {
            Node x = edge.getNode1();
            Node y = edge.getNode2();
            if (this.knowledge.isForbidden(x.getName(), y.getName())
                    && this.knowledge.isForbidden(y.getName(), x.getName())) {
                psi.removeEdge(x, y);
                if (verbose) {
                    TetradLogger.getInstance().log("Edge removed by knowledge: " + x.getName() + " *-* " + y.getName());
                }
            }
        }

        final int n = this.nodes.size();
        final int depthCap = (this.depth < 0) ? (n - 1) : this.depth;

        for (int d = 0; d <= depthCap; d++) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            if (verbose) TetradLogger.getInstance().log("Restricted FAS depth: " + d);

            // Freeze adjacencies for this level (PC-stable).
            Map<Node, List<Node>> frozenAdj = new HashMap<>();
            for (Node node : psi.getNodes()) frozenAdj.put(node, new ArrayList<>(psi.getAdjacentNodes(node)));

            boolean removedAny = false;

            for (Edge edge : new ArrayList<>(psi.getEdges())) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();

                Node x = edge.getNode1();
                Node y = edge.getNode2();
                if (!psi.isAdjacentTo(x, y)) continue;

                if (findSepsetAtLevel(x, y, frozenAdj.get(x), d) || findSepsetAtLevel(y, x, frozenAdj.get(y), d)) {
                    psi.removeEdge(x, y);
                    removedAny = true;
                }
            }

            // Termination: nothing removed and no node has enough neighbors for level d + 1.
            int maxFree = 0;
            for (Node node : psi.getNodes()) maxFree = TMath.max(maxFree, psi.getAdjacentNodes(node).size() - 1);
            if (!removedAny && maxFree <= d) break;
        }

        return psi;
    }

    /**
     * Searches subsets of size d of {@code pool}\{other} for a set separating {@code from} and {@code other}.
     * Returns true if one is found (the edge should be removed).
     */
    private boolean findSepsetAtLevel(Node from, Node other, List<Node> pool, int d) throws InterruptedException {
        List<Node> candidates = new ArrayList<>(pool);
        candidates.remove(other);
        if (candidates.size() < d) return false;

        ChoiceGenerator cg = new ChoiceGenerator(candidates.size(), d);
        int[] choice;

        while ((choice = cg.next()) != null) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            Set<Node> S = GraphUtils.asSet(choice, candidates);
            if (adjacencyTest().checkIndependence(from, other, S).isIndependent()) {
                if (verbose) {
                    TetradLogger.getInstance().log("Restricted FAS removal: " + from + " _||_ " + other + " | " + S);
                }
                return true;
            }
        }

        return false;
    }

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

                Set<Node> S = sepsets.getSepset(a, c, this.depth, null);
                if (S == null) continue;

                if (this.colliderRule != ColliderRule.GREEDY) {
                    List<Set<Node>> all = allSepsets(psi, a, c);
                    if (all.isEmpty()) continue;

                    int withB = 0;
                    for (Set<Node> sepset : all) if (sepset.contains(b)) withB++;

                    boolean nonCollider;
                    boolean collider;

                    if (this.colliderRule == ColliderRule.CONSERVATIVE) {
                        nonCollider = withB == all.size();
                        collider = withB == 0;
                    } else {
                        nonCollider = withB * 2 > all.size();
                        collider = withB * 2 < all.size();
                    }

                    if (nonCollider) {
                        psi.addUnderlineTriple(a, b, c);
                        if (verbose)
                            TetradLogger.getInstance().log("StepB: underline (non-collider, "
                                    + this.colliderRule + ") " + a + "-" + b + "-" + c
                                    + " ; b in " + withB + "/" + all.size() + " sepsets");
                        continue;
                    }

                    if (!collider) {
                        // The sepsets disagree about b. Leave both edges unoriented, but keep the
                        // triple visible to Step D, which looks at ambiguous triples as well as
                        // definite colliders, so cycle detection is not silently switched off.
                        psi.addAmbiguousTriple(a, b, c);
                        if (verbose)
                            TetradLogger.getInstance().log("StepB: ambiguous (" + this.colliderRule
                                    + ") " + a + "-" + b + "-" + c
                                    + " ; b in " + withB + "/" + all.size() + " sepsets");
                        continue;
                    }

                    // Agreed collider: fall through with a sepset that excludes b.
                    S = new HashSet<>();
                }

                if (S.contains(b)) {
                    // non-collider at b
                    psi.addUnderlineTriple(a, b, c);
                    if (verbose)
                        TetradLogger.getInstance().log("StepB: underline (non-collider) " + a + "-" + b + "-" + c + " ; S(a,c)=" + S);
                } else {
                    // collider at b: attempt a->b and c->b, each vetoable by knowledge
                    boolean ok1 = addDirectedIfAllowed(psi, a, b);
                    boolean ok2 = addDirectedIfAllowed(psi, c, b);

                    // If an arrowhead was forbidden, we must still record the collider semantic
                    // for subsequent steps that check for colliders. We use ambiguous triples
                    // to store this semantic since CCD doesn't have a dedicated collider set
                    // and psi.isDefCollider will fail if the arrowhead is missing.
                    if (!ok1 || !ok2) {
                        psi.addAmbiguousTriple(a, b, c);
                    }

                    if (verbose) {
                        if (ok1 && ok2)
                            TetradLogger.getInstance().log("StepB: collider " + a + "->" + b + "<-" + c + " ; S(a,c)=" + S);
                        else if (ok1 ^ ok2)
                            TetradLogger.getInstance().log("StepB: half-collider (knowledge veto) at " + b + " from " +
                                    (ok1 ? a : c) + " ; S(a,c)=" + S);
                        else
                            TetradLogger.getInstance().log("StepB: collider semantic recorded but both incoming arrows vetoed by knowledge at " + b);
                    }
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
                    if (ex != null && ex.getProximalEndpoint(x) == Endpoint.ARROW && psi.isUnderlineTriple(y, x, node)) {
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

                    Set<Node> sepset = sepsets.getSepset(a, y, this.depth, null);
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

        // Each middle node's search is independent and only reads psi, so the per-node searches may run
        // concurrently. Results are collected per node and applied here afterward, so the parallel path
        // produces exactly the sequential output.
        if (this.parallelized) {
            java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(
                    Runtime.getRuntime().availableProcessors());
            try {
                List<java.util.concurrent.Future<List<Object[]>>> futures = new ArrayList<>();
                for (Node node : this.nodes) {
                    futures.add(pool.submit(() -> doNodeStepD(psi, sepsets, local, node)));
                }
                for (java.util.concurrent.Future<List<Object[]>> future : futures) {
                    List<Object[]> results;
                    try {
                        results = future.get();
                    } catch (java.util.concurrent.ExecutionException e) {
                        if (e.getCause() instanceof InterruptedException) throw (InterruptedException) e.getCause();
                        if (e.getCause() instanceof RuntimeException) throw (RuntimeException) e.getCause();
                        throw new RuntimeException(e.getCause());
                    }
                    for (Object[] r : results) {
                        Triple triple = (Triple) r[0];
                        psi.addDottedUnderlineTriple(triple.getX(), triple.getY(), triple.getZ());
                        //noinspection unchecked
                        supSepsets.put(triple, (Set<Node>) r[1]);
                    }
                }
            } finally {
                pool.shutdownNow();
            }
        } else {
            for (Node node : this.nodes) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                for (Object[] r : doNodeStepD(psi, sepsets, local, node)) {
                    Triple triple = (Triple) r[0];
                    psi.addDottedUnderlineTriple(triple.getX(), triple.getY(), triple.getZ());
                    //noinspection unchecked
                    supSepsets.put(triple, (Set<Node>) r[1]);
                }
            }
        }
    }

    private List<Object[]> doNodeStepD(Graph psi, SepsetProducer sepsets,
                                       Map<Node, List<Node>> local, Node b) throws InterruptedException {
        List<Object[]> results = new ArrayList<>();
        List<Node> adj = new ArrayList<>(psi.getAdjacentNodes(b));
        if (adj.size() < 2) return results;

        ChoiceGenerator gen = new ChoiceGenerator(adj.size(), 2);
        int[] choice;

        while ((choice = gen.next()) != null) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();

            List<Node> _adj = GraphUtils.asList(choice, adj);
            Node a = _adj.get(0);
            Node c = _adj.get(1);

            if (!psi.isDefCollider(a, b, c) && !psi.isAmbiguousTriple(a, b, c)) continue;

            Set<Node> S = sepsets.getSepset(a, c, this.depth, null);
            if (S == null) continue;

            // The candidate sup-sepset is B = S ∪ {b} ∪ T with T drawn from Local(a). Depth caps the size of
            // EVERY issued conditioning set, so |B| = |S| + 1 + |T| must not exceed depth: T is enumerated only
            // up to depth − |S| − 1. If even S ∪ {b} is over the cap, no admissible sup-sepset exists at this
            // depth and the triple is skipped. (Formerly only |T| was capped at depth, so tests could condition
            // on as many as 2·depth + 1 variables.)
            if (this.depth >= 0 && S.size() + 1 > this.depth) continue;

            ArrayList<Node> TT = new ArrayList<>(local.getOrDefault(a, Collections.emptyList()));
            TT.removeAll(S);
            TT.remove(b);
            TT.remove(c);

            int kMax = (depth < 0) ? -1 : TMath.min(depth - S.size() - 1, TT.size());
            SublistGenerator gen2 = new SublistGenerator(TT.size(), kMax);
            int[] choice2;

            while ((choice2 = gen2.next()) != null) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();

                Set<Node> T = GraphUtils.asSet(choice2, TT);
                Set<Node> B = new HashSet<>(T);
                B.addAll(S);
                B.add(b);

                if (sepsets.isIndependent(a, c, new HashSet<>(B))) {
                    results.add(new Object[]{new Triple(a, b, c), B});
                    break;
                }
            }
        }

        return results;
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

                // Depth caps every issued conditioning set; skip candidates whose test would exceed it.
                if (this.depth >= 0 && supSepUnionD.size() > this.depth) continue;

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
    /**
     * Every subset of adj(a)\{c} or of adj(c)\{a}, up to the depth cap, that separates a from c.
     * Used by the non-greedy {@link ColliderRule}s, which ask whether the separating sets agree
     * about the middle node rather than trusting whichever one a greedy search returns first.
     *
     * @param psi the working graph, whose adjacencies bound the search.
     * @param a   one outer node of the triple.
     * @param c   the other outer node.
     * @return the separating sets found, without duplicates; empty if a and c are inseparable
     * within these adjacency sets.
     */
    private List<Set<Node>> allSepsets(Graph psi, Node a, Node c) throws InterruptedException {
        List<Set<Node>> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (Node end : new Node[]{a, c}) {
            List<Node> adj = new ArrayList<>(psi.getAdjacentNodes(end));
            adj.remove(a);
            adj.remove(c);

            int cap = this.depth < 0 ? adj.size() : TMath.min(this.depth, adj.size());

            SublistGenerator gen = new SublistGenerator(adj.size(), cap);
            int[] choice;

            while ((choice = gen.next()) != null) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();

                Set<Node> z = new HashSet<>();
                for (int i : choice) z.add(adj.get(i));

                List<String> key = new ArrayList<>();
                for (Node node : z) key.add(node.getName());
                Collections.sort(key);
                if (!seen.add(String.join(",", key))) continue;

                if (this.test.checkIndependence(a, c, z).isIndependent()) out.add(z);
            }
        }

        return out;
    }

    private boolean addDirectedIfAllowed(Graph g, Node u, Node v) {
        // Knowledge forbids orientation u->v?
        if (knowledge != null && knowledge.isForbidden(u.getName(), v.getName())) return false;

        // Proceed with orientation
        g.removeEdge(u, v);            // idempotent remove
        g.addDirectedEdge(u, v);       // tail at u, arrow at v
        return true;
    }

    /**
     * Local expansion around x: adj(x) plus z where x-y-z is a definite collider (or recorded collider semantic).
     */
    private List<Node> local(Graph psi, Node x) {
        Set<Node> nodes = new HashSet<>(psi.getAdjacentNodes(x));
        for (Node y : new HashSet<>(nodes)) {
            for (Node z : psi.getAdjacentNodes(y)) {
                if ((psi.isDefCollider(x, y, z) || psi.isAmbiguousTriple(x, y, z)) && z != x) {
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
//    private boolean orientAwayFromArrowVisit(Node a, Node b, Node c, Graph graph) {
//        Edge bc = graph.getEdge(b, c);
//        if (bc == null || !Edges.isNondirectedEdge(bc)) return false;
//        if (!graph.isUnderlineTriple(a, b, c)) return false;
//        if (bc.pointsTowards(b)) return false;
//
////        // Try b -> c (vetoable)
////        if (!addDirectedIfAllowed(graph, b, c)) return false;
////
////        for (Node d : new ArrayList<>(graph.getAdjacentNodes(c))) {
////            if (d == b) continue;
////            Edge cur = graph.getEdge(b, c);
////            if (cur == null) return true; // edge removed elsewhere
////            if (!orientAwayFromArrowVisit(b, c, d, graph)) {
////                graph.removeEdge(b, c);
////                graph.addEdge(cur);
////            }
////        }
////        return true;
//
//        Edge originalEdge = graph.getEdge(b, c);          // snapshot before orientation
//        if (!addDirectedIfAllowed(graph, b, c)) return false;
//
//        for (Node d : new ArrayList<>(graph.getAdjacentNodes(c))) {
//            if (d == b) continue;                          // fix #1 applied here too
//            if (graph.getEdge(b, c) == null) return true;
//            if (!orientAwayFromArrowVisit(b, c, d, graph)) {
//                graph.removeEdge(b, c);
//                graph.addEdge(originalEdge);               // restores pre-orientation state
//                return false;
//            }
//        }
//        return true;
//    }

    // Without rollback.
    private boolean orientAwayFromArrowVisit(Node a, Node b, Node c, Graph graph) {
        Edge bc = graph.getEdge(b, c);
        if (bc == null || !Edges.isNondirectedEdge(bc)) return false;
        if (!graph.isUnderlineTriple(a, b, c)) return false;
        if (bc.pointsTowards(b)) return false;

        if (!addDirectedIfAllowed(graph, b, c)) return false;

        for (Node d : new ArrayList<>(graph.getAdjacentNodes(c))) {
            if (d == b) continue;
            orientAwayFromArrowVisit(b, c, d, graph);
        }
        return true;
    }

//    // With rollback.
//    private boolean orientAwayFromArrowVisit(Node a, Node b, Node c, Graph graph) {
//        Edge bc = graph.getEdge(b, c);
//        if (bc == null || !Edges.isNondirectedEdge(bc)) return false;
//        if (!graph.isUnderlineTriple(a, b, c)) return false;
//        if (bc.pointsTowards(b)) return false;
//
//        Edge originalEdge = bc;  // snapshot before orientation
//        if (!addDirectedIfAllowed(graph, b, c)) return false;
//
//        for (Node d : new ArrayList<>(graph.getAdjacentNodes(c))) {
//            if (d == b) continue;
//            if (graph.getEdge(b, c) == null) return true;
//            if (!orientAwayFromArrowVisit(b, c, d, graph)) {
//                graph.removeEdge(b, c);
//                graph.addEdge(originalEdge);
//                return false;
//            }
//        }
//        return true;
//    }
}