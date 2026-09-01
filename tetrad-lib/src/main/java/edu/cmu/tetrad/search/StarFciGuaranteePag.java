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
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.utils.FciOrient;
import edu.cmu.tetrad.search.utils.MagToPag;
import edu.cmu.tetrad.search.utils.PagLegalityCheck;
import edu.cmu.tetrad.search.utils.R0R4StrategyTestBased;
import edu.cmu.tetrad.search.utils.SepsetMap;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.SublistGenerator;
import edu.cmu.tetrad.util.TetradLogger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Stream;

import static edu.cmu.tetrad.graph.GraphUtils.colliderAllowed;

/**
 * The *-FCI template with per-removal legality gating (StarFciCheckPag in the paper), a saturating group-removal
 * step, an ungated path repaired post hoc by GraphUtils.guaranteePag, and background-knowledge support: knowledge
 * orientations that go beyond the invariant marks of the equivalence class are KEPT, certified modulo knowledge,
 * rather than rejected or erased by the legality machinery. (This class consolidates the former
 * StarFciKeepKnowledgeOrientations into StarFciGuaranteePag; with empty knowledge it behaves exactly as the
 * knowledge-free class did.)
 * <p>
 * *-FCI implements a template modification of GFCI that starts with a given Markov CPDAG and then fixes that result to
 * be correct for latent variables models. First, colliders from the Markov DAG are copied into the final circle-circle
 * graph, and some independence reasoning is used to remove edges from this and add the remaining colliders into the
 * graph. Then, the FCI final orientation rules are applied. The Markov CPDAG needs to be supplied by classes
 * inheriting from this abstract class using the getMarkovDag() method.
 * <p>
 * THE PROBLEM THE KNOWLEDGE HANDLING SOLVES. Background knowledge (required edges; forbidden edges when selection bias is
 * excluded; temporal tiers) can force arrow and tail marks that are NOT invariant across the Markov equivalence class
 * of the implied MAG. The legality certificate used by StarFciGuaranteePag (PagLegalityCheck.isLegalPag) ends with an
 * exact round trip: PAG -&gt; Zhang MAG -&gt; canonical PAG, followed by a strict equality test against the input. A
 * graph carrying knowledge marks beyond the invariant marks always fails that equality (the reconstituted canonical
 * PAG has circles where knowledge placed arrows or tails). Consequently, on the gated path every candidate removal is
 * rejected and the knowledge marks never make it into the output; on the ungated path GraphUtils.guaranteePag repairs
 * the graph to a canonical PAG, erasing them.
 * <p>
 * THE FIX. Rather than simply dropping the round-trip equality (which would also disable the certificate's protection
 * against genuinely faulty "between a MAG and a PAG" states arising from noisy sepsets), this class replaces strict
 * equality with equality MODULO KNOWLEDGE: the graph must equal the canonical PAG of its implied Zhang MAG after that
 * canonical PAG is re-refined with background knowledge (fciOrientbk) and re-closed under the complete FCI final
 * rules. Every mark in an accepted graph is thereby accounted for: it is either invariant in the equivalence class or
 * forced by knowledge (directly or by rule propagation). When knowledge is empty the refinement is the identity and
 * the check degenerates to exactly PagLegalityCheck.isLegalPag.
 * <p>
 * OUTPUT CONTRACT. The returned graph is a legal PAG *refined by background knowledge*: stripping the knowledge marks
 * (equivalently, taking the canonical PAG of its implied MAG) yields a legal PAG, and the extra marks are exactly the
 * knowledge-forced ones plus their closure under the final rules. It is generally NOT the canonical PAG of an
 * equivalence class; it denotes the subset of that class consistent with the knowledge. Circles remain only where
 * neither the class nor the knowledge determines the mark.
 * <p>
 * DESIGN DECISION (contestable, so called out): when background knowledge CONFLICTS with data-derived invariant
 * structure (e.g., a required edge whose tail would sit at an invariant arrowhead), the knowledge-refined candidate
 * fails the modulo-knowledge certificate and the algorithm falls back conservatively -- knowledge loses and the
 * canonical marks stand. An alternative policy would force knowledge through and repair around it; that is a
 * modeling choice deliberately not made here.
 * <p>
 * Knowledge handling (2026-8): (1) all legality gating (per-removal commits, the saturating step) uses
 * legalPagModuloKnowledge, which reduces to PagLegalityCheck.isLegalPag when knowledge is empty; (2) on the ungated
 * path, guaranteePag runs only if the (knowledge-refined) graph fails the certificate; (3) a final
 * knowledge-refinement step is applied uniformly at the end of search(), so knowledge marks are present in the
 * output even when no edge removal ever committed.
 * <p>
 * The reference for the GFCI algorithm this is being modeled from is here:
 * <p>
 * Ogarrio, J. M., Spirtes, P., &amp; Ramsey, J. (2016, August). A hybrid causal search algorithm for latent variable
 * models. In Conference on probabilistic graphical models (pp. 368-379). PMLR.
 * <p>
 * This class is configured to respect knowledge of forbidden and required edges, including knowledge of temporal
 * tiers.
 *
 * @author josephramsey
 * @author bryanandrews
 * @see #getMarkovDag(boolean)
 * @see Knowledge
 */
public abstract class StarFciGuaranteePag implements IGraphSearch {
    /**
     * The independence test used in search.
     */
    private final IndependenceTest independenceTest;
    /**
     * The knowledge used in search.
     */
    private Knowledge knowledge = new Knowledge();
    /**
     * Whether Zhang's complete rules are used.
     */
    private boolean completeRuleSetUsed = true;
    /**
     * Whether the working PAG replicates across time lags (SVAR).
     */
    private boolean replicatingGraph = false;
    /**
     * The maximum path length for the discriminating path rule.
     */
    private int maxDiscriminatingPathLength = -1;
    /**
     * The depth for independence testing.
     */
    private int depth = -1;
    /**
     * Whether verbose output should be printed.
     */
    private boolean verbose = false;
    /**
     * A boolean flag indicating whether to use the maximum p-value heuristic during certain operations in the Star-FCI
     * algorithm. The default value is {@code false}, disabling the heuristic by default.
     */
    private boolean useMaxP = false;
    /**
     * A boolean flag indicating whether to exclude selection bias during certain operations in the Star-FCI algorithm.
     * The default value is {@code false}, allowing selection bias by default.
     */
    private boolean excludeSelectionBias = false;
    /**
     * When true, the extra-edge-removal step mimics FCIT: each candidate removal is committed only if the resulting
     * graph (after re-running the full *-FCI orientation) passes the modulo-knowledge legality certificate
     * (legalPagModuloKnowledge); otherwise it is reverted. When false, the original *-FCI behavior is used (greedy
     * removal with a single final orientation), and the result is mapped to a nearby legal PAG by
     * GraphUtils.guaranteePag only if it fails the certificate. Either way, the output is guaranteed to be a legal
     * PAG possibly refined by background knowledge; this flag only selects the mechanism.
     */
    private boolean doLegalityGating = true;
    /**
     * Whether to do the possible d-sep step. This step is REQUIRED for exactness: an exhaustive oracle test over
     * all PAGs on six observed variables exhibits a pair whose unique separating set contains a vertex adjacent to
     * neither endpoint, which the adjacency-subset pass structurally cannot find (see the StarFCI paper, Appendix).
     * In the gated configuration it is not expensive.
     */
    private boolean usePossibleDsep = true;
    /**
     * A flag indicating whether the LV-Heuristic results should be returned. If false, edges will be removed via
     * further independence testing.
     */
    private boolean lvHeuristicOnly = false;
    /**
     * Maximum path length for possible d-sep.
     */
    private int maxPossibleDsepPathLength = -1;
    /**
     * Whether to use parallel streams.
     */
    private boolean parallelized = false;

    /**
     * Constructs a new StarFci algorithm with the given independence test.
     *
     * @param test The independence test to use.
     */
    public StarFciGuaranteePag(IndependenceTest test) {
        this.independenceTest = test;
    }

    private static double computeScore(Node x, Node y, Set<Node> set, IndependenceTest test) {
        try {
            return test.checkIndependence(x, y, set).getPValue();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Strength of the evidence for x _||_ y | set, on a scale where LARGER always means stronger independence,
     * for both genuine hypothesis tests and scores wrapped as tests. A score-based test reports a score
     * difference that is negative for independence, so its value is negated here; see
     * {@link IndependenceTest#isPValueAProbability()}. Ranking candidate sepsets by the raw reported value
     * instead selects the WEAKEST separating set whenever the test is score-based.
     */
    private static double independenceStrength(Node x, Node y, Set<Node> set, IndependenceTest test) {
        try {
            IndependenceResult result = test.checkIndependence(x, y, set);
            return test.isPValueAProbability() ? result.getPValue() : -result.getScore();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Whether x _||_ y | set holds according to the test. Correct for every test, unlike comparing the reported
     * p-value against test.getAlpha(), which is meaningless for a test that does not test at a level (a score
     * wrapped as a test reports alpha = -1).
     */
    private static boolean independenceHolds(Node x, Node y, Set<Node> set, IndependenceTest test) {
        try {
            return test.checkIndependence(x, y, set).isIndependent();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * Generates a list of all possible choices for sublists from the adjacency list with sizes up to the given depth
     * using combinations.
     *
     * @param adjx  The adjacency list of nodes to generate combinations from.
     * @param depth The maximum size of the sublists to be generated. If the depth is negative or exceeds the size of
     *              the adjacency list, it will be adjusted to the size of the adjacency list.
     * @return A list of all possible lists of integers representing combinations of indices from the adjacency list up
     * to the given depth.
     */
    private static @NotNull List<Set<Node>> getChoices(List<Node> adjx, int depth) throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException();
        }

        List<Set<Node>> choices = new ArrayList<>();

        if (depth < 0 || depth > adjx.size()) depth = adjx.size();

        SublistGenerator cg = new SublistGenerator(adjx.size(), depth);
        int[] choice;

        while ((choice = cg.next()) != null) {
            choices.add(GraphUtils.asSet(choice, adjx));
        }

        return choices;
    }

    /**
     * Finds a separating set that is a subset of the adjacency of nodes x or y in the input graph.
     *
     * @param graph      The graph being analyzed.
     * @param x          The first node between which independence is checked.
     * @param y          The second node between which independence is checked.
     * @param containing A set of nodes that must be included in the separating set.
     * @param test       The independence test used to evaluate separation.
     * @param depth      The maximum size of subsets to be tested for independence.
     * @param useMaxP    True if the maxP method should be used.
     * @return A separating set of nodes (if found) that is a subset of the adjacency of x or y, or {@code null} if no
     * such set is found.
     * @throws InterruptedException if the process is interrupted during execution.
     */
    public Set<Node> sepsetSubsetOfAdjxOrAdjy(Graph graph, Node x, Node y, Set<Node> containing,
                                              IndependenceTest test, int depth, boolean useMaxP)
            throws InterruptedException {

        test.setVerbose(false);

        // We need to look at the original adjx and adjy, not some modified version.
        List<Node> adjx = graph.getAdjacentNodes(x);
        List<Node> adjy = graph.getAdjacentNodes(y);
        adjx.remove(y);
        adjy.remove(x);

        adjx.removeIf(node -> node.getNodeType() == NodeType.LATENT);
        adjy.removeIf(node -> node.getNodeType() == NodeType.LATENT);

        Set<Node> sepset1 = getSepset(x, y, containing, test, depth, adjx, useMaxP);
        Set<Node> sepset2 = getSepset(y, x, containing, test, depth, adjy, useMaxP);

        if (sepset1 == null && sepset2 == null) {
            return null;
        }

        if (sepset1 != null && sepset2 == null) {
            return sepset1;
        }

        if (sepset1 == null) {
            return sepset2;
        }

        // Direction-aware: larger strength = stronger independence for every kind of test.
        double s1 = independenceStrength(x, y, sepset1, test);
        double s2 = independenceStrength(x, y, sepset2, test);

        return s1 > s2 ? sepset1 : sepset2;
    }

    /**
     * Finds a separating set between nodes x and y that satisfies certain conditions, including containing a specified
     * set of nodes, maintaining optional ordering constraints, and ensuring the independence between x and y with
     * respect to the separating set.
     * <p>
     * The separating set is constructed from the adjacency list of node x.
     *
     * @param x          The first node between which independence is being checked.
     * @param y          The second node between which independence is being checked.
     * @param containing A set of nodes that must be included in the separating set.
     * @param test       The independence test used to evaluate separation between x and y.
     * @param depth      The maximum size of subsets to be tested for independence.
     * @param adjx       The adjacency list of node x, from which subsets are generated to test for separation.
     * @return A separating set of nodes that fulfills all constraints and is a subset of adjx, or {@code null} if no
     * such set is found.
     */
    private @Nullable Set<Node> getSepset(Node x, Node y, Set<Node> containing, IndependenceTest test, int depth,
                                          List<Node> adjx, boolean useMaxP) throws InterruptedException {
        if (useMaxP) {
            List<Set<Node>> choices = getChoices(adjx, depth);
            // Max p for stability...
            Stream<Set<Node>> setStream = parallelized ? choices.parallelStream() : choices.stream();
            return setStream
                    .filter(set -> independenceHolds(x, y, set, test))   // keep only separating sets
                    .max(Comparator.comparingDouble(set -> independenceStrength(x, y, set, test)))
                    .orElse(null); // Return the STRONGEST separating set, or null if there is none
        } else { // Greedy
            // Greedy: lazy enumeration (smallest subsets first), batched parallel testing,
            // short-circuit on the first separating set. Nothing is materialized up front.
            int d = (depth < 0 || depth > adjx.size()) ? adjx.size() : depth;
            SublistGenerator cg2 = new SublistGenerator(adjx.size(), d);
            final int BATCH = 4096;
            List<Set<Node>> batch = new ArrayList<>(BATCH);
            boolean exhausted = false;

            while (!exhausted) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();

                batch.clear();

                while (batch.size() < BATCH) {
                    int[] ch = cg2.next();
                    if (ch == null) {
                        exhausted = true;
                        break;
                    }
                    Set<Node> s = GraphUtils.asSet(ch, adjx);
                    if (s.containsAll(containing)) batch.add(s);
                }

                if (batch.isEmpty()) break;

                Stream<Set<Node>> setStream = parallelized ? batch.parallelStream() : batch.stream();
                Optional<Set<Node>> hit = setStream
                        .filter(s -> {
                            try {
                                return test.checkIndependence(x, y, s).isIndependent();
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                        }).findFirst();

                if (hit.isPresent()) return hit.get();
            }

            return null;
        }
    }

    /**
     * Sets the parallel processing mode for the StarFciGuaranteePag class.
     *
     * @param parallelized a boolean value indicating whether to enable (true) or
     *                     disable (false) parallel processing.
     */
    public void setParallelized(boolean parallelized) {
        this.parallelized = parallelized;
    }

    /**
     * Sets whether to use the maxP criterion during the search process.
     *
     * @param useMaxP A boolean indicating whether the maxP criterion should be applied (true) or not (false).
     */
    public void setUseMaxP(boolean useMaxP) {
        this.useMaxP = useMaxP;
    }

    /**
     * Runs the graph and returns the search PAG.
     *
     * @return This PAG.
     * @throws InterruptedException if any
     */
    public Graph search() throws InterruptedException {
        this.independenceTest.setVerbose(verbose);
        List<Node> nodes = new ArrayList<>(getIndependenceTest().getVariables());

        Graph cpdag = getMarkovDag(verbose);
        Graph pag = rewrap(GraphTransforms.dagToPag(cpdag, false));

        if (lvHeuristicOnly) {
            return pag;
        }

        Set<Triple> unshieldedColliders = new HashSet<>();
        SepsetMap sepsetMap = new SepsetMap();
        // Reusable data facts (X _||_ Y | S), invariant across passes. Kept OUT of sepsetMap
        // so a reverted removal can never leave a sepset in the committed map that orientPag
        // then stamps as a collider on a still-adjacent pair. Survives reverts by design.
        Map<Set<Node>, Set<Node>> foundSepsets = new HashMap<>();

        if (verbose) {
            TetradLogger.getInstance().log("Starting *-FCI extra edge removal step.");
        }

        // Orientation engine for the ungated final pass, bound to the committed sepset map. The gated
        // commit builds its own engine per trial (against a trial-local sepset copy), so R4's
        // discriminating-path sepset appends never pollute the committed map.
        FciOrient fciOrient = buildFciOrient(sepsetMap);

        // Selection nodes, needed by the PAG-legality check (mirrors FCIT).
        Set<Node> selection = new LinkedHashSet<>();
        for (Node node : nodes) {
            if (node.getNodeType() == NodeType.SELECTION) {
                selection.add(node);
            }
        }

        // Pass 1: adjacency-subset removal, iterated to a FIXPOINT. Gated commits are
        // order-dependent: a correct removal can be rejected because other spurious edges
        // still present make the trial graph illegal. Re-sweeping after each productive
        // pass retries those rejections against the improved graph; edge count strictly
        // decreases each iteration, so this terminates in <= |E| sweeps. Failed sepset
        // searches are deliberately not cached in foundSepsets, so shrunken adjacencies
        // are re-searched on later sweeps.
        int edgesBefore;
        do {
            edgesBefore = pag.getNumEdges();
            List<Edge> edges = new ArrayList<>(pag.getEdges());

            for (Edge edge : edges) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException();
                }

                Node a = edge.getNode1();
                Node c = edge.getNode2();

                if (!pag.isAdjacentTo(a, c)) {
                    continue;
                }

                Set<Node> sepset = foundSepsets.get(Set.of(a, c));

                if (sepset == null) {
                    sepset = sepsetSubsetOfAdjxOrAdjy(pag, a, c, new HashSet<>(), independenceTest, depth, useMaxP);

                    if (sepset != null) {
                        foundSepsets.put(Set.of(a, c), sepset);
                    }
                }

                if (sepset == null) {
                    continue;
                }

                pag = commitRemoval(pag, a, c, sepset, "adjacency-subset", doLegalityGating,
                        cpdag, nodes, sepsetMap, unshieldedColliders, selection);
            }
        } while (pag.getNumEdges() < edgesBefore);

        // Pass 2 (optional): possible-D-SEP removal. The original GFCI step that *-FCI dropped,
        // restored here for parity. Always runs on an oriented PAG, since Possible-D-SEP
        // presupposes FCI-oriented colliders; that precondition is met by the standalone
        // orientation below (ungated path) or by the gate's per-step reorientation (gated path).
        // Removals honor doLegalityGating exactly as in Pass 1: gated runs gate them, ungated
        // runs remove unconditionally (original greedy *-FCI).
        if (usePossibleDsep) {
            // Ensure colliders are oriented as in FCI before computing Possible-D-SEP. In the GATED
            // path pag already carries the last-accepted, gate-legal full orientation (commitRemoval
            // ran the complete gfciOrientPag on the accepted candidate), so this standalone pass is
            // unnecessary and is skipped. In the UNGATED path nothing has oriented pag yet, so this is
            // the sole orientation pass, exactly as before.
            if (!doLegalityGating) {
                gfciOrientPag(pag, cpdag, nodes, sepsetMap, unshieldedColliders, fciOrient);
            }

            //            if (verbose) {
            //                TetradLogger.getInstance().log("*-FCI finished.");
            //            }

            List<Edge> dsepEdges = new ArrayList<>(pag.getEdges());

            for (Edge edge : dsepEdges) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException();
                }

                Node a = edge.getNode1();
                Node c = edge.getNode2();

                if (!pag.isAdjacentTo(a, c)) {
                    continue;
                }

                // One endpoint suffices (per JR); change `a` to `c`, or union the two, if parity
                // ever turns out to need both ends.
                List<Node> possibleDsep = pag.paths().possibleDsep(a, maxPossibleDsepPathLength);
                possibleDsep.remove(a);
                possibleDsep.remove(c);
                possibleDsep.removeIf(node -> node.getNodeType() == NodeType.LATENT);

                // Candidate sepsets are all subsets of Possible-D-SEP(a), capped at depth.
                // Reuse via foundSepsets; never pre-write sepsetMap (see Pass 1).
                Set<Node> sepset = foundSepsets.get(Set.of(a, c));

                if (sepset == null) {
                    // Possible-D-SEP pools can be very large; an unbounded depth here means
                    // exhausting 2^|pool| subsets for every GENUINE edge. Clamp regardless of
                    // the global depth setting.
                    int dsepDepth = (depth < 0) ? Math.min(possibleDsep.size(), 4) : Math.min(depth, possibleDsep.size());
                    sepset = getSepset(a, c, new HashSet<>(), independenceTest, dsepDepth, possibleDsep, useMaxP);

                    if (sepset != null) {
                        foundSepsets.put(Set.of(a, c), sepset);
                    }
                }

                if (sepset == null) {
                    continue;
                }

                pag = commitRemoval(pag, a, c, sepset, "possible-D-SEP", doLegalityGating,
                        cpdag, nodes, sepsetMap, unshieldedColliders, selection);
            }
        }

        // Saturating step (gated path only): clears deadlocks of the single-edge gated
        // fixpoint by trying the surviving test-confirmed removals JOINTLY. See the
        // saturatingRemoval javadoc for the witness case and the escalation ladder.
        if (doLegalityGating) {
            pag = rewrap(saturatingRemoval(pag, cpdag, nodes, sepsetMap, unshieldedColliders, selection, foundSepsets));
        }

        // Ungated greedy path: run the single final orientation. If the result already passes the
        // modulo-knowledge certificate, keep it AS IS -- running GraphUtils.guaranteePag here (as the
        // base class does unconditionally) would judge the knowledge-refined graph illegal under the
        // strict certificate and repair it to the canonical PAG, erasing exactly the knowledge marks
        // this class exists to keep. Only if the certificate fails is the graph mapped to a nearby
        // legal PAG by guaranteePag (dropping knowledge extras); the final refinement step below then
        // tries to reapply them.
        if (!doLegalityGating) {
            gfciOrientPag(pag, cpdag, nodes, sepsetMap, unshieldedColliders, fciOrient);

            if (!legalPagModuloKnowledge(pag, new LinkedHashSet<>(selection), fciOrient).isLegalPag()) {
                pag = rewrap(GraphUtils.guaranteePag(pag, fciOrient, knowledge, new HashSet<>(), verbose, new HashSet<>(),
                        excludeSelectionBias, Integer.MAX_VALUE));
            }
        }

        // Uniform final knowledge refinement, both paths. Covers in particular the gated path on which
        // NO removal ever committed: there pag is still the untouched dagToPag start graph, which has
        // never been through gfciOrientPag and so carries no knowledge marks at all. Refine a copy with
        // knowledge and keep it only if the refined graph passes the modulo-knowledge certificate; on
        // graphs that are already knowledge-refined fixed points this is a no-op (the refinement is
        // idempotent), and on certificate failure the unrefined (legal) graph is kept -- knowledge
        // never degrades legality.
        if (!knowledge.isEmpty()) {
            FciOrient refineOrient = buildFciOrient(new SepsetMap(sepsetMap));
            Graph refined = refineWithKnowledge(pag, refineOrient);

            if (legalPagModuloKnowledge(refined, new LinkedHashSet<>(selection), refineOrient).isLegalPag()) {
                pag = rewrap(refined);
            } else if (verbose) {
                TetradLogger.getInstance().log("Final knowledge refinement failed the modulo-knowledge "
                        + "certificate (likely a knowledge/data conflict); returning the unrefined graph.");
            }
        }

        pag = rewrap(GraphUtils.replaceNodes(pag, nodes));

        if (verbose) {
            TetradLogger.getInstance().log("*-FCI finished.");
        }

        return pag;
    }

    /**
     * Runs the full *-FCI orientation in place on {@code pag}: reorient everything to circles, apply background
     * knowledge, copy unshielded colliders from the CPDAG and orient colliders implied by the extra sepsets, then run
     * the FCI final-orientation rules. This is the original *-FCI orientation block, factored out unchanged so the
     * legal-PAG gate can re-run it after each candidate removal. {@code unshieldedColliders} is rebuilt from scratch on
     * each call so that, after the (possibly many) re-orientations done by the gate, it always reflects the current
     * committed graph (it is consumed only by the final GraphUtils.guaranteePag repair step, on the ungated path).
     */
    private void gfciOrientPag(Graph pag, Graph cpdag, List<Node> nodes, SepsetMap sepsetMap,
                               Set<Triple> unshieldedColliders, FciOrient fciOrient)
            throws InterruptedException {
        unshieldedColliders.clear();

        pag.reorientAllWith(Endpoint.CIRCLE);
        fciOrient.fciOrientbk(knowledge, pag, pag.getNodes(), excludeSelectionBias);

        for (Node y : nodes) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }

            List<Node> adjacentNodes = new ArrayList<>(pag.getAdjacentNodes(y));

            ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException();
                }

                Node x = adjacentNodes.get(combination[0]);
                Node z = adjacentNodes.get(combination[1]);

                if (cpdag.isDefCollider(x, y, z) && !cpdag.isAdjacentTo(x, z)) {
                    if (colliderAllowed(pag, x, y, z, knowledge)) {
                        pag.setEndpoint(x, y, Endpoint.ARROW);
                        pag.setEndpoint(z, y, Endpoint.ARROW);
                        unshieldedColliders.add(new Triple(x, y, z));
                    }
                } else if (cpdag.isAdjacentTo(x, z)) {
                    Set<Node> sepset = sepsetMap.get(x, z);

                    if (sepset != null && !sepset.contains(y)) {
                        if (colliderAllowed(pag, x, y, z, knowledge)) {
                            pag.setEndpoint(x, y, Endpoint.ARROW);
                            pag.setEndpoint(z, y, Endpoint.ARROW);

                            if (!pag.isAdjacentTo(x, z)) {
                                unshieldedColliders.add(new Triple(x, y, z));
                            }
                        }
                    }
                }
            }
        }

        fciOrient.finalOrientation(pag, excludeSelectionBias);
    }

    /**
     * Attempts to remove edge (a, c) using the given candidate sepset.
     * <p>
     * When {@code doLegalityGating} is false, the edge is removed unconditionally (original greedy *-FCI),
     * leaving the graph for the single final orientation, and the sepset is written to the committed map.
     * <p>
     * When {@code doLegalityGating} is true, the removal is gated on per-step PAG legality. The candidate is
     * built on a copy: the edge is removed and the graph is re-oriented from scratch by the full GFCI
     * orientation {@link #gfciOrientPag} — re-blank to circles, copy unshielded CPDAG colliders, stamp
     * recorded-sepset colliders, and apply the complete FCI rules (R0–R4). The full reorient (rather than
     * an incremental collider stamp) is required for soundness: when an edge that was a leg of an
     * unshielded collider is removed, the arrowheads and tails it induced must be rebuilt from circles, or
     * stale endpoints survive into the output. If the re-oriented candidate is a legal PAG it is carried
     * forward and the sepset is committed; otherwise nothing is committed and the unchanged graph is
     * returned.
     * <p>
     * Orientation runs against a trial-local <em>copy</em> of the committed sepset map, seeded with the
     * candidate sepset. This is essential: {@link #gfciOrientPag}'s R4 (discriminating-path) rule appends
     * sepsets to whatever map its strategy is bound to, so binding it to the committed map would let a
     * rejected trial leave spurious sepsets behind (later read as colliders) and let accepted trials grow
     * the map unboundedly, breaking maximality. Only the deliberate {@code (a, c) = sepset} write reaches
     * the committed map, and only on acceptance.
     */
    private Graph commitRemoval(Graph pag, Node a, Node c, Set<Node> sepset, String type, boolean doLegalityGating,
                                Graph cpdag, List<Node> nodes, SepsetMap sepsetMap, Set<Triple> unshieldedColliders,
                                Set<Node> selection) throws InterruptedException {
        if (!doLegalityGating) {
            pag.removeEdge(a, c);
            sepsetMap.set(a, c, sepset);
            if (verbose) {
                IndependenceResult result = independenceTest.checkIndependence(a, c, sepset);
                TetradLogger.getInstance().log("Removed edge " + a + " -- " + c + " (" + type + "); sepset = "
                        + sepset + ", p-value = " + result.getPValue() + ".");
            }
            return pag;
        }

        // Gated path: work on a copy; pag is not mutated until we accept.
        Graph _pag = pag.copy();
        _pag.removeEdge(a, c);

        // Full GFCI reorient against a trial-local sepset map (committed sepsets + this candidate), so
        // R4's discriminating-path sepset appends are discarded with the trial and never touch the
        // committed map.
        SepsetMap trialMap = new SepsetMap(sepsetMap);
        trialMap.set(a, c, sepset);
        FciOrient trialOrient = buildFciOrient(trialMap);
        gfciOrientPag(_pag, cpdag, nodes, trialMap, unshieldedColliders, trialOrient);

        // Modulo-knowledge certificate: strict round-trip equality when knowledge is empty; otherwise the
        // reconstituted canonical PAG is knowledge-refined (with this trial's own orientation engine, so R4's
        // sepset appends stay trial-local) before the equality test. See legalPagModuloKnowledge.
        PagLegalityCheck.LegalPagRet legal = legalPagModuloKnowledge(_pag, new LinkedHashSet<>(selection), trialOrient);

        if (!legal.isLegalPag()) {
            if (verbose) {
                IndependenceResult result = independenceTest.checkIndependence(a, c, sepset);
                TetradLogger.getInstance().log("\tTried removing " + a + " -- " + c + " (" + type
                        + "); sepset = " + sepset + ", p-value = " + result.getPValue()
                        + ", but it didn't lead to a legal PAG (reverted). Reason: " + legal.getReason());
            }

            return pag;                                // committed sepset map untouched
        }

        // Accepted: commit only the deliberate sepset write and carry the reoriented PAG forward.
        sepsetMap.set(a, c, sepset);

        if (verbose) {
            IndependenceResult result = independenceTest.checkIndependence(a, c, sepset);
            TetradLogger.getInstance().log("Removed edge " + a + " -- " + c + " (" + type
                    + ", legal PAG); sepset = " + sepset + ", p-value = " + result.getPValue() + ".");
        }

        return _pag;
    }

    /**
     * Formats a batch of candidate removals as "a -- c (sepset = [...])" pairs for verbose logging,
     * so stall traces show which edges were tried and under which separating sets.
     */
    private String formatBatch(List<Set<Node>> batch, Map<Set<Node>, Set<Node>> foundSepsets) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < batch.size(); i++) {
            if (i > 0) sb.append(", ");
            List<Node> pn = new ArrayList<>(batch.get(i));
            sb.append(pn.get(0)).append(" -- ").append(pn.get(1))
                    .append(" (sepset = ").append(foundSepsets.get(batch.get(i))).append(")");
        }
        return sb.toString();
    }

    /**
     * Saturating step for the gated path, run once after the single-edge machinery has stalled.
     * <p>
     * The single-edge gated fixpoint can deadlock: a Markov state can carry several spurious edges,
     * each individually separable (test-confirmed sepset in hand), where deleting any ONE of them and
     * re-running the fixed reorientation yields an illegal PAG while the others still stand. The
     * OBS=6 witness cc...cc..ta.aaat.aaacat has exactly two such edges, pairwise shielding triples
     * at a shared separator vertex; deleting them JOINTLY lands on the true PAG, which is legal by
     * construction. This is the RB paper's saturating fallback in its engineering role.
     * <p>
     * Mechanics: R = all still-adjacent pairs with a cached test-confirmed sepset in
     * {@code foundSepsets} (covers stalls from both the adjacency-subset and possible-D-SEP passes).
     * Trial 1 removes all of R on a copy, seeds every sepset into a trial-local map, runs the one
     * full reorientation, and gates once on PAG legality. If that fails, one leave-one-out rung is
     * tried (each subset of size |R|-1, first legal wins) before reverting to the stalled graph.
     * Soundness is unchanged: the same legality gate is applied to the committed candidate, and every
     * removal is individually test-confirmed, so noise costs reach, never soundness. If PKE-style
     * certification still finds stalls, the next rungs are (a) deeper subset descent and (b) an outer
     * fixpoint over {single-edge pass, saturating step}, since a successful saturation shrinks
     * adjacencies and can make previously unseparable pairs separable.
     */
    private Graph saturatingRemoval(Graph pag, Graph cpdag, List<Node> nodes, SepsetMap sepsetMap,
                                    Set<Triple> unshieldedColliders, Set<Node> selection,
                                    Map<Set<Node>, Set<Node>> foundSepsets) throws InterruptedException {
        // R: still-present edges with a test-confirmed sepset in hand.
        List<Set<Node>> stalled = new ArrayList<>();
        for (Set<Node> pair : foundSepsets.keySet()) {
            List<Node> pn = new ArrayList<>(pair);
            if (pag.isAdjacentTo(pn.get(0), pn.get(1))) {
                stalled.add(pair);
            }
        }

        if (stalled.isEmpty()) {
            return pag;
        }

        if (verbose) {
            TetradLogger.getInstance().log("Saturating step: " + stalled.size()
                    + " separable edge(s) survived the single-edge fixpoint; trying joint removal: "
                    + formatBatch(stalled, foundSepsets) + ".");
        }

        // Trial 1: full saturation; then one leave-one-out rung. (A singleton R retries the
        // single-edge trial that just failed and will fail again; harmless, and it keeps the
        // control flow uniform.)
        List<List<Set<Node>>> batches = new ArrayList<>();
        batches.add(stalled);
        if (stalled.size() > 1) {
            for (int skip = 0; skip < stalled.size(); skip++) {
                List<Set<Node>> sub = new ArrayList<>(stalled);
                sub.remove(skip);
                batches.add(sub);
            }
        }

        for (List<Set<Node>> batch : batches) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }

            Graph trial = pag.copy();
            SepsetMap trialMap = new SepsetMap(sepsetMap);
            for (Set<Node> pair : batch) {
                List<Node> pn = new ArrayList<>(pair);
                trial.removeEdge(pn.get(0), pn.get(1));
                trialMap.set(pn.get(0), pn.get(1), foundSepsets.get(pair));
            }
            FciOrient trialOrient = buildFciOrient(trialMap);
            gfciOrientPag(trial, cpdag, nodes, trialMap, unshieldedColliders, trialOrient);

            PagLegalityCheck.LegalPagRet legal = legalPagModuloKnowledge(trial, new LinkedHashSet<>(selection), trialOrient);

            if (legal.isLegalPag()) {
                // Accepted: commit exactly this batch's sepsets and carry the reoriented PAG forward.
                for (Set<Node> pair : batch) {
                    List<Node> pn = new ArrayList<>(pair);
                    sepsetMap.set(pn.get(0), pn.get(1), foundSepsets.get(pair));
                }
                if (verbose) {
                    TetradLogger.getInstance().log("Saturating step: removed " + batch.size() + " of "
                            + stalled.size() + " edge(s) jointly (legal PAG): "
                            + formatBatch(batch, foundSepsets) + ".");
                }
                return trial;
            } else if (verbose) {
                TetradLogger.getInstance().log("\tSaturating step: joint removal of " + batch.size()
                        + " edge(s) not legal (reverted): " + formatBatch(batch, foundSepsets)
                        + ". Reason: " + legal.getReason());
            }
        }

        if (verbose) {
            TetradLogger.getInstance().log("Saturating step: no joint removal was legal; stalled graph kept.");
        }
        return pag;
    }

    /**
     * Builds a fresh FCI orientation engine (complete rules, R4 on) bound to the given sepset map, using
     * the class's independence test, knowledge, and discriminating-path length. A fresh engine is built
     * per gated trial so that each trial's R4 sepset appends land in its own trial-local map and are
     * discarded with the trial.
     */
    private FciOrient       buildFciOrient(SepsetMap sepsetMap) {
        R0R4StrategyTestBased strategy = (R0R4StrategyTestBased) R0R4StrategyTestBased.specialConfiguration(independenceTest, knowledge, verbose);
        // Respect the user's depth/length knobs instead of hardcoding unlimited. With
        // usePossibleDsep == false the R4 discriminating-path resolution must not be the
        // back door through which unbounded (possible-D-SEP-scale) conditioning searches
        // re-enter after the adjacency-only pass.
        strategy.setDepth(this.depth);
        strategy.setMaxLength(this.maxDiscriminatingPathLength);
        strategy.setSepsetMap(sepsetMap);
        strategy.setBlockingType(R0R4StrategyTestBased.BlockingType.GREEDY);
        strategy.setVerbose(false);
        FciOrient fciOrient = new FciOrient(strategy);
        fciOrient.setCompleteRuleSetUsed(completeRuleSetUsed);
        fciOrient.setRecursiveDepth(-1);
        fciOrient.setMaxDiscriminatingPathLength(maxDiscriminatingPathLength);
        fciOrient.setUseR4(true);
        fciOrient.setVerbose(true);
        return fciOrient;
    }

    /**
     * The legality certificate of this class: legal PAG *modulo background knowledge*.
     * <p>
     * When knowledge is empty this delegates to {@link PagLegalityCheck#isLegalPag(Graph, Set)} and is therefore
     * exactly the strict certificate of StarFciGuaranteePag. Otherwise the strict certificate's final round-trip
     * equality is replaced by equality modulo knowledge:
     * <ol>
     *   <li>all nodes must be measured;</li>
     *   <li>the graph's implied Zhang MAG must exist and be a legal MAG (this is where structural pathologies --
     *       cycles, almost-cycles, non-maximality -- are caught, so none of that protection is given up);</li>
     *   <li>the canonical PAG of that MAG is computed (MagToPag), then re-refined with background knowledge and
     *       re-closed under the complete FCI final rules using the SAME orientation engine that produced the
     *       candidate (so R4's data-driven discriminating-path resolutions are reproduced, and its sepset appends
     *       land in that engine's own map);</li>
     *   <li>the candidate must equal the knowledge-refined canonical PAG exactly.</li>
     * </ol>
     * Step 4 is what makes the verdict a certificate: every mark in an accepted graph is either invariant in the
     * Markov equivalence class of the implied MAG or forced by knowledge (directly or via rule propagation). A graph
     * that is "between a MAG and a PAG" for any OTHER reason -- e.g., a non-invariant collider stamped from a noisy
     * sepset -- still fails, exactly as under the strict certificate.
     *
     * @param pag       the candidate graph (already oriented by gfciOrientPag, hence already carrying knowledge
     *                  marks)
     * @param selection the selection nodes for the MAG legality check
     * @param orient    the orientation engine used to knowledge-refine the reconstituted canonical PAG; pass the
     *                  engine that oriented the candidate so trial-local sepset bookkeeping stays trial-local
     * @return a LegalPagRet whose isLegalPag() is true iff the candidate is a legal PAG refined by knowledge
     */
    private PagLegalityCheck.LegalPagRet legalPagModuloKnowledge(Graph pag, Set<Node> selection, FciOrient orient)
            throws InterruptedException {
        if (knowledge.isEmpty()) {
            return PagLegalityCheck.isLegalPag(pag, selection);
        }

        for (Node n : pag.getNodes()) {
            if (n.getNodeType() != NodeType.MEASURED) {
                return new PagLegalityCheck.LegalPagRet(false, "Node " + n + " is not measured");
            }
        }

        Graph mag;
        try {
            mag = GraphTransforms.zhangMagFromPag(pag);
        } catch (Exception e) {
            return new PagLegalityCheck.LegalPagRet(false, "PAG to MAG failed");
        }

        PagLegalityCheck.LegalMagRet legalMag = PagLegalityCheck.isLegalMag(mag, selection);

        if (!legalMag.isLegalMag()) {
            return new PagLegalityCheck.LegalPagRet(false, legalMag.getReason() + " in a MAG implied by this graph");
        }

        Graph pag2;
        try {
            MagToPag magToPag = new MagToPag(mag);
            pag2 = magToPag.convert(false, false);
        } catch (IllegalStateException e) {
            return new PagLegalityCheck.LegalPagRet(false, "Legal PAG status could not be determined");
        }

        Graph pag2k = refineWithKnowledge(pag2, orient);

        if (!pag.equals(pag2k)) {
            String edgeMismatch = "";

            for (Edge e : pag.getEdges()) {
                Edge e2 = pag2k.getEdge(e.getNode1(), e.getNode2());
                if (!e.equals(e2)) {
                    edgeMismatch = "For example, the candidate has edge " + e
                            + " whereas the knowledge-refined reconstituted graph has edge " + e2;
                    break;
                }
            }

            String reason = "The MAG implied by this graph was a legal MAG, but the graph is not recoverable as the "
                    + "knowledge-refined canonical PAG of that MAG -- it carries marks forced neither by the "
                    + "equivalence class nor by background knowledge";

            if (!edgeMismatch.isEmpty()) {
                reason += ". " + edgeMismatch;
            }

            return new PagLegalityCheck.LegalPagRet(false, reason);
        }

        return new PagLegalityCheck.LegalPagRet(true, "This is a legal PAG refined by background knowledge");
    }

    /**
     * Refines a graph with background knowledge and closes under the complete FCI final rules: apply
     * {@link FciOrient#fciOrientbk} for the required/forbidden-edge marks, then {@link FciOrient#finalOrientation}
     * to propagate them (R1, R2, ...). Works on a copy; the input is not modified. Identity when knowledge is
     * empty. Note that this can only ADD arrow/tail marks to a graph closed under the final rules; on a graph that
     * is already a knowledge-refined fixed point it is a no-op.
     *
     * @param graph  the graph to refine (not modified)
     * @param orient the orientation engine to use (supplies knowledge marks and the final-rule closure)
     * @return a knowledge-refined copy of the graph
     */
    private Graph refineWithKnowledge(Graph graph, FciOrient orient) throws InterruptedException {
        if (knowledge.isEmpty()) {
            return graph;
        }

        Graph g = graph.copy();
        orient.fciOrientbk(knowledge, g, g.getNodes(), excludeSelectionBias);
        orient.finalOrientation(g, excludeSelectionBias);
        return g;
    }

    /**
     * Returns the knowledge used in search.
     *
     * @return This knowledge
     */
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * Sets the knowledge to use in search.
     *
     * @param knowledge This knowledge.
     */
    public void setKnowledge(Knowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException();
        }

        this.knowledge = knowledge;
    }

    /**
     * Sets whether Zhang's complete rules are used.
     *
     * @param completeRuleSetUsed set to true if Zhang's complete rule set should be used, false if only R1-R4 (the rule
     *                            set of the original FCI) should be used. True by default.
     */
    public void setCompleteRuleSetUsed(boolean completeRuleSetUsed) {
        this.completeRuleSetUsed = completeRuleSetUsed;
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
     * Indicates whether verbose output is enabled.
     *
     * @return true if verbose output is enabled, false otherwise.
     */
    public boolean isVerbose() {
        return verbose;
    }

    /**
     * Sets whether verbose output should be printed.
     *
     * @param verbose True, if so.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Returns the independence test used in search.
     *
     * @return This test.
     */
    public IndependenceTest getIndependenceTest() {
        return this.independenceTest;
    }

    /**
     * Sets the depth of the search for the possible m-sep search.
     *
     * @param depth This depth.
     */
    public void setDepth(int depth) {
        this.depth = depth;
    }

    /**
     * Constructs and returns the Markov Directed Acyclic Graph (DAG) representing the
     * probabilistic relationships between variables. The Markov DAG is derived from
     * the underlying data or structural constraints.
     *
     * @param verbose a boolean flag indicating whether detailed progress
     *                or debugging information should be printed during the
     *                construction process.
     * @return a Graph object representing the Markov Directed Acyclic Graph (DAG).
     * @throws InterruptedException if the process is interrupted during execution.
     */
    /**
     * Sets whether the working PAG should be a replicating (time-lag repeating) graph: if set,
     * the PAG is maintained as a ReplicatingGraph with a LagReplicationPolicy, so that edge
     * additions, removals, and endpoint orientations are mirrored across homologous lagged
     * variable pairs during the search, as in the other SVAR-capable algorithms. Subclasses
     * should consult isReplicatingGraph() to propagate the setting to the search that produces
     * the initial Markov DAG.
     *
     * @param replicatingGraph true if the graph should replicate across time lags.
     */
    public void setReplicatingGraph(boolean replicatingGraph) {
        this.replicatingGraph = replicatingGraph;
    }

    /**
     * Returns whether the working PAG replicates across time lags.
     *
     * @return true if replicating.
     */
    public boolean isReplicatingGraph() {
        return this.replicatingGraph;
    }

    /**
     * Re-wraps a graph in the replication policy if it is in force (operations that build fresh
     * graphs would otherwise silently drop the wrapper).
     *
     * @param g the graph.
     * @return the graph, wrapped if replication is in force.
     */
    private Graph rewrap(Graph g) {
        return this.replicatingGraph && !(g instanceof ReplicatingGraph)
                ? new ReplicatingGraph(g, new LagReplicationPolicy())
                : g;
    }

    /**
     * Constructs and returns the Markov Directed Acyclic Graph (DAG) representation
     * of a probabilistic model. This method may provide additional details
     * during execution if the verbose option is enabled.
     *
     * @param verbose a boolean flag indicating whether to enable verbose logging
     *                during the construction of the Markov DAG.
     * @return a Graph object representing the Markov DAG.
     * @throws InterruptedException if the operation is interrupted during execution.
     */
    public abstract Graph getMarkovDag(boolean verbose) throws InterruptedException;

    /**
     * Sets whether selection bias should be excluded during the search process.
     *
     * @param excludeSelectionBias A boolean indicating whether to exclude selection bias (true) or not (false).
     */
    public void setExcludeSelectionBias(boolean excludeSelectionBias) {
        this.excludeSelectionBias = excludeSelectionBias;
    }

    /**
     * When true, the extra-edge-removal step mimics FCIT: each candidate removal is committed only if the resulting
     * graph (after re-running the full *-FCI orientation) passes the modulo-knowledge legality certificate;
     * otherwise it is reverted. When false, the original *-FCI behavior is used (greedy removal with a single final
     * orientation), mapped to a nearby legal PAG by GraphUtils.guaranteePag only if the certificate fails. Either
     * way, the output is guaranteed to be a legal PAG possibly refined by background knowledge; this flag only
     * selects the mechanism.
     *
     * @param doLegalityGating Whether to gate each removal on per-step PAG legality (true) or repair post hoc (false).
     */
    public void setDoLegalityGating(boolean doLegalityGating) {
        this.doLegalityGating = doLegalityGating;
    }

    /**
     * Whether to do the possible d-sep step; empirically we find this unnecessary, though the defiition of GFCI
     * includes it. In the context of the check pag branch it is not expensive.
     *
     * @param usePossibleDsep Whether to use the possible d-sep step.
     */
    public void setUsePossibleDsep(boolean usePossibleDsep) {
        this.usePossibleDsep = usePossibleDsep;
    }

    /**
     * A flag indicating whether the LV-Heuristic results should be returned. If false, edges will be removed via
     * further independence testing.
     *
     * @param lvHeuristicOnly Whether to return the LV-Heuristic results.
     */
    public void setLvHeuristicOnly(boolean lvHeuristicOnly) {
        this.lvHeuristicOnly = lvHeuristicOnly;
    }

    /**
     * Maximum path length for possible d-sep.
     *
     * @param maxPossibleDsepPathLength Maximum path length for possible d-sep.
     */
    public void setMaxPossibleDsepPathLength(int maxPossibleDsepPathLength) {
        if (maxPossibleDsepPathLength < -1) {
            throw new IllegalArgumentException("Maximum path length for possible d-sep must be >= -1 (-1 = unlimited).");
        }
        this.maxPossibleDsepPathLength = maxPossibleDsepPathLength;
    }
}

