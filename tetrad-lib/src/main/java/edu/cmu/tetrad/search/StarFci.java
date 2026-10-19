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
import edu.cmu.tetrad.search.utils.*;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.SublistGenerator;
import edu.cmu.tetrad.util.TetradLogger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Stream;

import static edu.cmu.tetrad.graph.GraphUtils.colliderAllowed;

/**
 * *-FCI (StarFCI): the GFCI-template latent-variable search of the StarFCI paper (Ramsey, Andrews, and Spirtes,
 * "StarFCI: A GFCI-Template Latent-Variable Search that Returns a PAG by Construction"), with the Markov-CPDAG
 * initializer abstracted ({@link #getMarkovCpdag(boolean)}) and supplied by subclasses: {@link Gfci} (from FGES),
 * {@link Bfci} (from BOSS), {@link GraspFci} (from GRaSP), {@link SpFci} (from SP). With
 * {@code doLegalityGating == true} (the default) this class is the paper's StarFciCheckPag: every extra-edge
 * removal is gated on a check that the re-oriented graph is a legal PAG. With {@code doLegalityGating == false}
 * it is the paper's ungated path: the GFCI template with the initializer substituted, followed by the
 * GuaranteePag post-hoc repair. Either way the output is a PAG (possibly refined by background knowledge; see
 * below).
 * <p>
 * THE GATED FORMULATION (2026-10-18; B. Andrews). The gated path implements the from-scratch, sepset-map-as-state
 * formulation with SEPSET RE-SELECTION. State is the committed sepset map S plus the carried graph G, where G is
 * always the output of a full from-scratch re-orientation (circles, CPDAG colliders, sepset colliders, Zhang's
 * complete final rules) against S. Two passes, each iterated to a fixpoint:
 * <pre>
 * Pass 1 (adjacency subsets) then Pass 2 (possible-D-SEP subsets):
 *   Repeat
 *     changed &lt;- false
 *     For (a,b) in EDGES(G)
 *       For C in SUBSETS_d(pool(G,a)\{a,b}) ++ SUBSETS_d(pool(G,b)\{a,b})   // smallest first
 *         If a _||_ b | C
 *           If Try(G, S, a, b, C) = ACCEPT
 *             changed &lt;- true; break          // next edge; REJECT =&gt; next C
 *   While changed
 * </pre>
 * where Try removes a-b on a copy, seeds C into a trial-local copy of S, re-orients from scratch, and commits G
 * and S together iff the result passes the legality certificate; a rejected trial leaves no trace. The
 * load-bearing line is the inner loop continuing past a gate rejection: a rejected separating set does not
 * retire the pair -- the NEXT separating set for the same pair is tried within the same visit. Different
 * separating sets induce different R0 colliders and hence different re-orientations, so a pair whose first
 * sepset fails the gate can still be removed under another.
 * <p>
 * WHAT THIS REPLACED, AND WHY. The previous gated implementation froze the FIRST test-confirmed separating set
 * per pair (a {@code foundSepsets} cache, reused verbatim) and gated single removals on that one sepset; pairs
 * whose frozen sepset induced an illegal re-orientation deadlocked, and a saturating group-removal step
 * (Pass 3) existed to clear such deadlocks by joint removal. Under re-selection the OBS=6 single-removal
 * deadlock witness (positional key cc...cc..ta.aaat.aaacat; two mutually self-supporting spurious edges whose
 * single removals each cascade to an illegal PAG under the frozen sepset) is resolved by single removals alone:
 * the gate-rejected pair is removed under an alternative separating set (oracle verification 2026-10-18, exact
 * recovery across 40 randomized edge/candidate orders, and 20/20 exact on random 8-node/2-latent oracle DAGs).
 * The saturating step is therefore REMOVED. PROVED vs CONJECTURED, stated plainly: the witness result is a
 * verified instance, not a theorem; the exhaustive OBS=6 certification of the re-selection formulation (the
 * PKE-style sweep) is OPEN, and until it is run, "single removal with re-selection suffices" is a conjecture.
 * <p>
 * CORRESPONDENCE WITH THE PAPER'S ALGORITHM 1 (StarFciCheckPag). The paper's pseudocode maps onto
 * {@link #search()} as follows.
 * <ul>
 *   <li><b>Initialization.</b> {@code getMarkovCpdag()} supplies the Markov CPDAG -- or any DAG in its
 *   equivalence class; the two are interchangeable here, because the only facts this class reads from the
 *   initial graph are its skeleton and its unshielded colliders, both class-invariant. The working graph starts
 *   as {@code dagToPag} of (a DAG representative of) the initial graph. On the ungated path the paper starts
 *   instead from the all-circles skeleton; the two starts are equivalent because the ungated path never consults
 *   an orientation before its first full re-orientation, which blanks all marks. {@code lvHeuristicOnly} returns
 *   the {@code dagToPag} start directly -- the LV-Heuristic baseline of the paper's Section 3.1. The committed
 *   sepset map ({@code sepsetMap}) is the paper's S: a sepset reaches it only when its removal is accepted, and
 *   S and G commit atomically.</li>
 *   <li><b>Pass 1: adjacency-subset removal, to a fixpoint.</b> Gated: candidate sets are subsets of
 *   ADJ(G,a)\{b} and ADJ(G,b)\{a}, smallest first, with re-selection past gate rejections; re-sweeping retries
 *   edges against the shrunken graph. Ungated: the GFCI template's greedy sweep, first separating set removes
 *   unconditionally.</li>
 *   <li><b>Pass 2: possible-D-SEP removal.</b> On by default and REQUIRED for exactness: an exhaustive oracle
 *   test over all PAGs on six observed variables exhibits a nonadjacent pair whose unique separating set
 *   contains a vertex adjacent to neither endpoint, which Pass 1 structurally cannot find (paper, Appendix).
 *   The {@code usePossibleDsep} switch exists for timing studies only. Gated: pools from BOTH endpoints,
 *   fixpoint, re-selection (the previous implementation searched one endpoint only). Ungated: a standalone
 *   orientation precedes a single one-endpoint sweep, exactly the GFCI template; on the gated path the carried
 *   graph is already fully oriented by the last accepted commit.</li>
 *   <li><b>The gated trial.</b> {@link #tryRemoveEdgeGated}: build the candidate on a copy, re-orient from
 *   scratch against a trial-local copy of the committed sepset map seeded with the candidate sepset, and accept
 *   iff the result passes the legality certificate. A rejected trial leaves no trace, and the scan continues
 *   with the next separating set, up to {@link #setMaxGateTrialsPerEdge} trials per edge per visit.</li>
 *   <li><b>Finalization.</b> The ungated path orients once; then, independently of the gating choice, the
 *   {@code guaranteePag} flag (default true) controls the repair step: a final graph failing the
 *   modulo-knowledge certificate is mapped to a nearby legal PAG by {@code GraphUtils.guaranteePag} -- invoked
 *   only on failure, which is equivalent to the paper's unconditional GuaranteePag since that operator is the
 *   identity on any graph already passing the check. On the gated path every commit was already certified, so
 *   the repair is a dormant safety net and the carried graph is returned. With BOTH flags off, the raw greedy
 *   GFCI-template output is returned, which is not necessarily a legal PAG.</li>
 * </ul>
 * Disclosed divergences from the paper's idealized pseudocode: (1) when the global {@code depth} is unlimited
 * (-1), the Pass-2 subset search over possible-D-SEP pools is clamped to subsets of size at most
 * {@link #DSEP_DEPTH_CLAMP}, since those pools can be large enough to make exhaustive subset search intractable
 * on real data; set {@code depth} explicitly to override. (This cannot affect the paper's six-node oracle
 * results, whose separating sets are smaller than the clamp.) (2) On the gated path, legality-gate trials are
 * capped at {@link #setMaxGateTrialsPerEdge} per edge per visit (default 100); re-selection runs one full
 * re-orientation plus one certificate check per SEPARATING candidate, and at moderate alpha on real data that
 * term can grow. The cap is per visit; later sweeps rescan against the shrunken graph. A consequence of
 * re-selection worth stating: a committed sepset is selected partly for orientation coherence rather than
 * independence strength alone; every committed sepset is still individually test-confirmed.
 * <p>
 * *-FCI implements a template modification of GFCI that starts with a given Markov CPDAG and then fixes that result to
 * be correct for latent variables models. First, colliders from the Markov CPDAG are copied into the final
 * circle-circle graph, and some independence reasoning is used to remove edges from this and add the remaining
 * colliders into the graph. Then, the FCI final orientation rules are applied. The Markov CPDAG needs to be supplied
 * by classes inheriting from this abstract class using the getMarkovCpdag() method.
 * <p>
 * HOW BACKGROUND KNOWLEDGE IS KEPT. Knowledge orientations that go beyond the invariant marks of the equivalence
 * class are KEPT, certified modulo knowledge, rather than rejected or erased by the legality machinery. (The
 * knowledge handling formerly in StarFciKeepKnowledgeOrientations is consolidated here; with empty knowledge this
 * class behaves exactly as the knowledge-free gated template does.)
 * <p>
 * THE PROBLEM THE KNOWLEDGE HANDLING SOLVES. Background knowledge (required edges; forbidden edges when selection bias is
 * excluded; temporal tiers) can force arrow and tail marks that are NOT invariant across the Markov equivalence class
 * of the implied MAG. The strict legality certificate (PagLegalityCheck.isLegalPag) ends with an
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
 * Knowledge handling (2026-8): (1) all legality gating uses legalPagModuloKnowledge, which reduces to
 * PagLegalityCheck.isLegalPag when knowledge is empty; (2) on the ungated path, guaranteePag runs only if the
 * (knowledge-refined) graph fails the certificate; (3) a final knowledge-refinement step is applied uniformly at
 * the end of search(), so knowledge marks are present in the output even when no edge removal ever committed.
 * <p>
 * The reference for the GFCI algorithm this is being modeled from is here:
 * <p>
 * Ogarrio, J. M., Spirtes, P., &amp; Ramsey, J. (2016, August). A hybrid causal search algorithm for latent variable
 * models. In Conference on probabilistic graphical models (pp. 368-379). PMLR.
 * <p>
 * This class is configured to respect knowledge of forbidden and required edges, including knowledge of temporal
 * tiers.
 * <p>
 * HISTORY. This class was formerly named StarFciGuaranteePag (a deprecated shell of that name is retained for
 * source compatibility), and before that the knowledge handling lived in StarFciKeepKnowledgeOrientations. An
 * earlier, pre-gating class that carried the name StarFci -- a single ungated adjacency-subset sweep with no
 * possible-D-SEP pass, no saturating step, and no PAG guarantee by default -- has been removed; its GFCI
 * instance survives as {@link GfciOld} for comparison runs (the paper's "pre-gating" arm). The frozen-sepset
 * gated implementation with the saturating Pass 3 (2026-10-09 through 2026-10-18) was replaced by the
 * re-selection formulation above, first prototyped as a standalone BfciBryan class (removed in favor of this
 * consolidation).
 *
 * @author josephramsey
 * @author bryanandrews
 * @see #getMarkovCpdag(boolean)
 * @see Knowledge
 */
public abstract class StarFci implements IGraphSearch {
    /**
     * Cap on the subset size searched over possible-D-SEP pools in Pass 2 when the global {@code depth} is
     * unlimited (-1). Possible-D-SEP pools can be large, and an unbounded subset search over such a pool means
     * exhausting 2^|pool| candidate sets for every GENUINE edge; the clamp keeps the default configuration
     * tractable. Setting {@code depth} explicitly overrides the clamp (the search then uses
     * min(depth, |pool|)). Disclosed in the class Javadoc as a divergence from the paper's pseudocode.
     */
    private static final int DSEP_DEPTH_CLAMP = 4;
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
     * Whether the final FCI orientation engine logs its rule-by-rule output (R0 collider copies,
     * R1-R4 firings, BK orientation banners, discriminating-path stamps). Separate from
     * {@link #verbose}, which controls the search-level narrative (removals tried and committed,
     * sepsets): the gated path re-runs the full orientation inside every trial, so orientation
     * logging is voluminous and is off by default even when {@code verbose} is on.
     */
    private boolean logFinalOrientations = false;
    /**
     * A boolean flag indicating whether to use the maximum p-value heuristic. On the ungated path this selects
     * the strongest separating set, as before. On the gated path it orders the GATE TRIALS: separating
     * candidates are tried in decreasing independence strength rather than smallest-subset-first. The default
     * value is {@code false}.
     */
    private boolean useMaxP = false;
    /**
     * A boolean flag indicating whether to exclude selection bias during certain operations in the Star-FCI algorithm.
     * The default value is {@code false}, allowing selection bias by default.
     */
    private boolean excludeSelectionBias = false;
    /**
     * When true, the extra-edge-removal step is gated: each candidate removal is committed only if the resulting
     * graph (after re-running the full *-FCI orientation from scratch) passes the modulo-knowledge legality
     * certificate (legalPagModuloKnowledge), with sepset re-selection past gate rejections. When false, the
     * original *-FCI behavior is used (greedy removal with a single final orientation), and the result is mapped
     * to a nearby legal PAG by GraphUtils.guaranteePag only if it fails the certificate. Either way, the output
     * is guaranteed to be a legal PAG possibly refined by background knowledge; this flag only selects the
     * mechanism.
     */
    private boolean doLegalityGating = true;
    /**
     * When true, the final graph is checked against the modulo-knowledge certificate and, if it fails, mapped to
     * a nearby legal PAG by GraphUtils.guaranteePag. Independent of {@code doLegalityGating}: on the gated path
     * every commit was already certified, so the check should pass and the repair is a dormant safety net; on the
     * ungated path this is the paper's GuaranteePag step, and turning it off returns the raw greedy GFCI-template
     * output, which is NOT necessarily a legal PAG. Default true, preserving the previous behavior on both paths.
     */
    private boolean guaranteePag = true;
    /**
     * Whether to log the individual conditional independence results: when true, the independence test itself
     * is put in verbose mode (every CI query printed), and the removal/trial log lines include the p-value of
     * the committed or rejected sepset (which costs one extra test call per line). Off by default: plain
     * {@code verbose} gives the search-level narrative (removals committed, gate rejections, clamps) without
     * the CI flood. For the curious who want to check all the details.
     */
    private boolean logCiDetails = false;
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
     * Whether to use parallel streams (ungated sepset search only; the gated trial sequence is inherently
     * sequential).
     */
    private boolean parallelized = false;
    /**
     * Cap on legality-gate trials (re-orientation + certificate) per edge per visit on the gated path.
     * Independence tests that fail to separate do not count. Disclosed in the class Javadoc.
     */
    private int maxGateTrialsPerEdge = 100;

    /**
     * Constructs a new StarFci algorithm with the given independence test.
     *
     * @param test The independence test to use.
     */
    public StarFci(IndependenceTest test) {
        this.independenceTest = test;
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
     * Finds a separating set that is a subset of the adjacency of nodes x or y in the input graph. (Used by the
     * ungated path; the gated path scans candidates itself in {@link #tryRemoveEdgeGated}.)
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
     * Sets the parallel processing mode for the StarFci class.
     *
     * @param parallelized a boolean value indicating whether to enable (true) or
     *                     disable (false) parallel processing.
     */
    public void setParallelized(boolean parallelized) {
        this.parallelized = parallelized;
    }

    /**
     * Sets whether to use the maxP criterion during the search process. On the ungated path this selects the
     * strongest separating set; on the gated path it orders the gate trials by decreasing independence strength.
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
        // Test-level verbosity (every CI query printed) is governed by logCiDetails, not verbose: plain
        // verbose gives the search-level narrative without the CI flood. The initial CPDAG run is verbose
        // whenever verbose is set (getMarkovCpdag(verbose), below).
        this.independenceTest.setVerbose(logCiDetails);
        List<Node> nodes = new ArrayList<>(getIndependenceTest().getVariables());

        // Paper, Alg. 1: the Markov CPDAG from the initializer. Subclasses may return the CPDAG itself
        // or any DAG in its class; the two are interchangeable below, since only the skeleton and the
        // unshielded colliders are consulted and both are class-invariant.
        Graph cpdag = getMarkovCpdag(verbose);

        // dagToPag of a DAG representative is cpdagToPag of the class. On the ungated path the paper starts
        // from the all-circles skeleton instead; the two starts are equivalent because no orientation of pag
        // is consulted before the first full re-orientation, which blanks all marks (see gfciOrientPag).
        Graph initDag = cpdag.paths().isLegalDag() ? cpdag : GraphTransforms.dagFromCpdag(cpdag);
        Graph pag = rewrap(GraphTransforms.dagToPag(initDag, false));

        if (lvHeuristicOnly) {
            return pag;
        }

        Set<Triple> unshieldedColliders = new HashSet<>();
        SepsetMap sepsetMap = new SepsetMap();

        if (verbose) {
            TetradLogger.getInstance().log("Starting *-FCI extra edge removal step.");
        }

        // Orientation engine for the ungated final pass, bound to the committed sepset map. The gated
        // trial builds its own engine per trial (against a trial-local sepset copy), so R4's
        // discriminating-path sepset appends never pollute the committed map.
        FciOrient fciOrient = buildFciOrient(sepsetMap);

        // Selection nodes, needed by the PAG-legality check (mirrors FCIT).
        Set<Node> selection = new LinkedHashSet<>();
        for (Node node : nodes) {
            if (node.getNodeType() == NodeType.SELECTION) {
                selection.add(node);
            }
        }

        if (doLegalityGating) {

            // GATED PATH: the re-selection formulation. Pass 1 then Pass 2, each to a fixpoint. Each
            // accepted removal strictly decreases the edge count, so each fixpoint terminates in <= |E|
            // sweeps.

            // Pass 1: adjacency-subset removal with sepset re-selection, to a fixpoint.
            boolean changed;
            do {
                changed = false;

                for (Edge edge : new ArrayList<>(pag.getEdges())) {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new InterruptedException();
                    }

                    Node a = edge.getNode1();
                    Node c = edge.getNode2();

                    if (!pag.isAdjacentTo(a, c)) {
                        continue;
                    }

                    List<List<Node>> pools = new ArrayList<>();
                    pools.add(adjPool(pag, a, c));
                    pools.add(adjPool(pag, c, a));

                    Graph next = tryRemoveEdgeGated(pag, a, c, pools, depth, cpdag, nodes, sepsetMap,
                            unshieldedColliders, selection, "adjacency-subset");

                    if (next != null) {
                        pag = next;
                        changed = true;
                    }
                }
            } while (changed);

            // Pass 2: possible-D-SEP removal with sepset re-selection, to a fixpoint, pooling from BOTH
            // endpoints. The carried graph is already fully oriented (the dagToPag start, or the last
            // accepted commit's re-orientation), as possible-D-SEP presupposes.
            if (usePossibleDsep) {
                do {
                    changed = false;

                    for (Edge edge : new ArrayList<>(pag.getEdges())) {
                        if (Thread.currentThread().isInterrupted()) {
                            throw new InterruptedException();
                        }

                        Node a = edge.getNode1();
                        Node c = edge.getNode2();

                        if (!pag.isAdjacentTo(a, c)) {
                            continue;
                        }

                        List<List<Node>> pools = new ArrayList<>();
                        pools.add(dsepPool(pag, a, c));
                        pools.add(dsepPool(pag, c, a));

                        // Possible-D-SEP pools can be very large; an unbounded depth here means
                        // exhausting 2^|pool| subsets for every GENUINE edge. When depth is unlimited,
                        // clamp to DSEP_DEPTH_CLAMP (a disclosed divergence from the paper's pseudocode;
                        // see the class Javadoc). An explicit depth is honored as given.
                        int poolMax = Math.max(pools.get(0).size(), pools.get(1).size());
                        int dsepDepth = (depth < 0) ? Math.min(poolMax, DSEP_DEPTH_CLAMP) : depth;

                        if (verbose && depth < 0 && poolMax > DSEP_DEPTH_CLAMP) {
                            TetradLogger.getInstance().log("Possible-D-SEP subset search for " + a + " -- " + c
                                    + " clamped to subsets of size <= " + DSEP_DEPTH_CLAMP + " (pool size "
                                    + poolMax + "); set depth explicitly to override.");
                        }

                        Graph next = tryRemoveEdgeGated(pag, a, c, pools, dsepDepth, cpdag, nodes, sepsetMap,
                                unshieldedColliders, selection, "possible-D-SEP");

                        if (next != null) {
                            pag = next;
                            changed = true;
                        }
                    }
                } while (changed);
            }
        } else {

            // UNGATED PATH: exactly the pre-gating GFCI template with the initializer substituted
            // (unchanged behavior), finished by the GuaranteePag repair below.

            // Pass 1: greedy adjacency-subset removal, iterated to a fixpoint.
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

                    Set<Node> sepset = sepsetSubsetOfAdjxOrAdjy(pag, a, c, new HashSet<>(), independenceTest,
                            depth, useMaxP);

                    if (sepset == null) {
                        continue;
                    }

                    pag.removeEdge(a, c);
                    sepsetMap.set(a, c, sepset);

                    if (verbose) {
                        TetradLogger.getInstance().log("Removed edge " + a + " -- " + c
                                + " (adjacency-subset); sepset = " + sepset + pValueSuffix(a, c, sepset) + ".");
                    }
                }
            } while (pag.getNumEdges() < edgesBefore);

            // Pass 2: possible-D-SEP removal, single one-endpoint sweep, preceded by the standalone
            // orientation (GFCI template; possible-D-SEP presupposes FCI-oriented colliders).
            if (usePossibleDsep) {
                gfciOrientPag(pag, cpdag, nodes, sepsetMap, unshieldedColliders, fciOrient);

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

                    List<Node> possibleDsep = dsepPool(pag, a, c);

                    int dsepDepth = (depth < 0) ? Math.min(possibleDsep.size(), DSEP_DEPTH_CLAMP)
                            : Math.min(depth, possibleDsep.size());

                    if (verbose && depth < 0 && possibleDsep.size() > DSEP_DEPTH_CLAMP) {
                        TetradLogger.getInstance().log("Possible-D-SEP subset search for " + a + " -- " + c
                                + " clamped to subsets of size <= " + DSEP_DEPTH_CLAMP + " (pool size "
                                + possibleDsep.size() + "); set depth explicitly to override.");
                    }

                    Set<Node> sepset = getSepset(a, c, new HashSet<>(), independenceTest, dsepDepth,
                            possibleDsep, useMaxP);

                    if (sepset == null) {
                        continue;
                    }

                    pag.removeEdge(a, c);
                    sepsetMap.set(a, c, sepset);

                    if (verbose) {
                        TetradLogger.getInstance().log("Removed edge " + a + " -- " + c
                                + " (possible-D-SEP); sepset = " + sepset + pValueSuffix(a, c, sepset) + ".");
                    }
                }
            }

            // The single final orientation (part of the template, not of the repair).
            gfciOrientPag(pag, cpdag, nodes, sepsetMap, unshieldedColliders, fciOrient);
        }

        // GuaranteePag step, independent of the gating choice: if requested and the graph fails the
        // modulo-knowledge certificate, map it to a nearby legal PAG. The repair runs ONLY on certificate
        // failure -- running GraphUtils.guaranteePag unconditionally would judge a knowledge-refined graph
        // illegal under the strict certificate and repair it to the canonical PAG, erasing exactly the
        // knowledge marks this class exists to keep (the final refinement step below then reapplies them).
        // On the gated path every commit was already certified, so this check should pass and the repair is
        // a dormant safety net; on the ungated path it is the paper's GuaranteePag step. With guaranteePag
        // false the graph is returned as it stands, which on the ungated path is not necessarily a legal PAG.
        if (guaranteePag) {
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
     * One gated edge visit: scan candidate conditioning sets from the given pools, trying each SEPARATING set
     * against the legality gate until one is accepted or the gate-trial cap is reached. This is the sepset
     * re-selection loop: a gate rejection moves to the NEXT separating set for the same pair rather than
     * retiring the pair. Candidates are scanned smallest-subset-first; with {@code useMaxP}, separating
     * candidates are instead gated in decreasing independence strength.
     * <p>
     * The gated trial itself: the edge is removed on a copy and the graph is re-oriented from scratch by the
     * full GFCI orientation {@link #gfciOrientPag} -- re-blank to circles, copy unshielded CPDAG colliders,
     * stamp recorded-sepset colliders, and apply the complete FCI rules (R0-R4). The full reorient (rather than
     * an incremental collider stamp) is required for soundness: when an edge that was a leg of an unshielded
     * collider is removed, the arrowheads and tails it induced must be rebuilt from circles, or stale endpoints
     * survive into the output. Orientation runs against a trial-local COPY of the committed sepset map, seeded
     * with the candidate sepset: {@link #gfciOrientPag}'s R4 rule appends sepsets to whatever map its strategy
     * is bound to, so binding it to the committed map would let a rejected trial leave spurious sepsets behind.
     * Only on acceptance do the graph and the deliberate {@code (a, c) = sepset} write commit, atomically.
     *
     * @return the accepted (re-oriented) graph with the sepset committed, or null if no candidate was accepted
     * (graph and committed map untouched).
     */
    private Graph tryRemoveEdgeGated(Graph pag, Node a, Node c, List<List<Node>> pools, int subsetDepth,
                                     Graph cpdag, List<Node> nodes, SepsetMap sepsetMap,
                                     Set<Triple> unshieldedColliders, Set<Node> selection, String type)
            throws InterruptedException {
        Set<Set<Node>> tried = new HashSet<>();
        int gateTrials = 0;

        // Candidate order: lazy smallest-first by default; with useMaxP, materialize the separating
        // candidates and gate in decreasing independence strength.
        List<Set<Node>> ordered = null;
        if (useMaxP) {
            ordered = new ArrayList<>();
            for (List<Node> pool : pools) {
                for (Set<Node> C : getChoices(pool, subsetDepth)) {
                    if (tried.add(C) && independenceHolds(a, c, C, independenceTest)) {
                        ordered.add(C);
                    }
                }
            }
            ordered.sort(Comparator.comparingDouble(
                    (Set<Node> C) -> independenceStrength(a, c, C, independenceTest)).reversed());
        }

        if (ordered != null) {
            for (Set<Node> C : ordered) {
                if (gateTrials >= maxGateTrialsPerEdge) {
                    logGateCap(a, c, type);
                    return null;
                }
                gateTrials++;

                Graph accepted = gateTrial(pag, a, c, C, cpdag, nodes, sepsetMap, unshieldedColliders,
                        selection, type, gateTrials);
                if (accepted != null) return accepted;
            }
            logAllRejected(a, c, type, gateTrials);
            return null;
        }

        for (List<Node> pool : pools) {
            int d = (subsetDepth < 0 || subsetDepth > pool.size()) ? pool.size() : subsetDepth;
            SublistGenerator gen = new SublistGenerator(pool.size(), d);
            int[] choice;

            while ((choice = gen.next()) != null) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException();
                }

                Set<Node> C = GraphUtils.asSet(choice, pool);
                if (!tried.add(C)) continue;

                if (!independenceTest.checkIndependence(a, c, C).isIndependent()) continue;

                if (gateTrials >= maxGateTrialsPerEdge) {
                    logGateCap(a, c, type);
                    return null;
                }
                gateTrials++;

                Graph accepted = gateTrial(pag, a, c, C, cpdag, nodes, sepsetMap, unshieldedColliders,
                        selection, type, gateTrials);
                if (accepted != null) return accepted;
            }
        }

        if (gateTrials > 0) {
            logAllRejected(a, c, type, gateTrials);
        }

        return null;
    }

    /**
     * One gated trial of removing a-c under candidate sepset C; see {@link #tryRemoveEdgeGated}. Returns the
     * accepted re-oriented graph (sepset committed) or null (no trace).
     */
    private @Nullable Graph gateTrial(Graph pag, Node a, Node c, Set<Node> C, Graph cpdag, List<Node> nodes,
                                      SepsetMap sepsetMap, Set<Triple> unshieldedColliders, Set<Node> selection,
                                      String type, int gateTrial) throws InterruptedException {
        Graph _pag = pag.copy();
        _pag.removeEdge(a, c);

        SepsetMap trialMap = new SepsetMap(sepsetMap);
        trialMap.set(a, c, C);
        FciOrient trialOrient = buildFciOrient(trialMap);
        gfciOrientPag(_pag, cpdag, nodes, trialMap, unshieldedColliders, trialOrient);

        PagLegalityCheck.LegalPagRet legal =
                legalPagModuloKnowledge(_pag, new LinkedHashSet<>(selection), trialOrient);

        if (legal.isLegalPag()) {
            sepsetMap.set(a, c, C);

            if (verbose) {
                TetradLogger.getInstance().log("Removed edge " + a + " -- " + c + " (" + type
                        + ", legal PAG); sepset = " + C + pValueSuffix(a, c, C)
                        + " (gate trial " + gateTrial + ").");
            }

            return _pag;
        }

        if (verbose) {
            TetradLogger.getInstance().log("\tTried removing " + a + " -- " + c + " (" + type
                    + "); sepset = " + C + pValueSuffix(a, c, C)
                    + ", but it didn't lead to a legal PAG; trying the next separating set. Reason: "
                    + legal.getReason());
        }

        return null;
    }

    /**
     * ", p-value = ..." for the given CI fact when {@code logCiDetails} is set, otherwise the empty string.
     * Keeping this behind the flag also avoids re-running the test just to decorate a log line.
     */
    private String pValueSuffix(Node a, Node c, Set<Node> sepset) throws InterruptedException {
        if (!logCiDetails) return "";
        IndependenceResult result = independenceTest.checkIndependence(a, c, sepset);
        return ", p-value = " + result.getPValue();
    }

    private void logGateCap(Node a, Node c, String type) {
        if (verbose) {
            TetradLogger.getInstance().log("\t" + a + " -- " + c + " (" + type + "): gate-trial cap of "
                    + maxGateTrialsPerEdge + " reached; deferring to a later sweep.");
        }
    }

    private void logAllRejected(Node a, Node c, String type, int gateTrials) {
        if (verbose) {
            TetradLogger.getInstance().log("\t" + a + " -- " + c + " (" + type + "): all " + gateTrials
                    + " separating set(s) gate-rejected this visit; edge kept.");
        }
    }

    /**
     * Adjacency pool for one endpoint: ADJ(G, a) \ {b}, latents removed.
     */
    private List<Node> adjPool(Graph graph, Node a, Node b) {
        List<Node> adj = graph.getAdjacentNodes(a);
        adj.remove(b);
        adj.removeIf(node -> node.getNodeType() == NodeType.LATENT);
        return adj;
    }

    /**
     * Possible-D-SEP pool for one endpoint: PSBLDSEP(G, a) \ {a, b}, latents removed.
     */
    private List<Node> dsepPool(Graph graph, Node a, Node b) {
        List<Node> pool = graph.paths().possibleDsep(a, maxPossibleDsepPathLength);
        pool.remove(a);
        pool.remove(b);
        pool.removeIf(node -> node.getNodeType() == NodeType.LATENT);
        return pool;
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
     * Builds a fresh FCI orientation engine (complete rules, R4 on) bound to the given sepset map, using
     * the class's independence test, knowledge, and discriminating-path length. A fresh engine is built
     * per gated trial so that each trial's R4 sepset appends land in its own trial-local map and are
     * discarded with the trial.
     */
    private FciOrient buildFciOrient(SepsetMap sepsetMap) {
        R0R4StrategyTestBased strategy = (R0R4StrategyTestBased) R0R4StrategyTestBased.specialConfiguration(independenceTest, knowledge, logFinalOrientations);
        // Respect the user's depth/length knobs instead of hardcoding unlimited. With
        // usePossibleDsep == false the R4 discriminating-path resolution must not be the
        // back door through which unbounded (possible-D-SEP-scale) conditioning searches
        // re-enter after the adjacency-only pass.
        strategy.setDepth(this.depth);
        strategy.setMaxLength(this.maxDiscriminatingPathLength);
        strategy.setSepsetMap(sepsetMap);
        strategy.setBlockingType(R0R4StrategyTestBased.BlockingType.GREEDY);
        strategy.setVerbose(logFinalOrientations);
        FciOrient fciOrient = new FciOrient(strategy);
        fciOrient.setCompleteRuleSetUsed(completeRuleSetUsed);
        fciOrient.setRecursiveDepth(-1);
        fciOrient.setMaxDiscriminatingPathLength(maxDiscriminatingPathLength);
        fciOrient.setUseR4(true);
        fciOrient.setVerbose(logFinalOrientations);
        return fciOrient;
    }

    /**
     * The legality certificate of this class: legal PAG *modulo background knowledge*.
     * <p>
     * When knowledge is empty this delegates to {@link PagLegalityCheck#isLegalPag(Graph, Set)} and is therefore
     * exactly the strict certificate used when no knowledge is present. Otherwise the strict certificate's final round-trip
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
     * Indicates whether the final FCI orientation engine logs its rule-by-rule output.
     *
     * @return true if final-orientation logging is enabled, false otherwise.
     */
    public boolean isLogFinalOrientations() {
        return logFinalOrientations;
    }

    /**
     * Sets whether the final FCI orientation engine logs its rule-by-rule output (R1-R4 firings,
     * BK orientation banners, discriminating-path stamps). Independent of {@link #setVerbose}:
     * setting {@code verbose} alone yields the search-level narrative without the orientation
     * flood; setting this flag alone yields only the orientation output.
     *
     * @param logFinalOrientations True, if so.
     */
    public void setLogFinalOrientations(boolean logFinalOrientations) {
        this.logFinalOrientations = logFinalOrientations;
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
     * Returns whether the working PAG replicates across time lags.
     *
     * @return true if replicating.
     */
    public boolean isReplicatingGraph() {
        return this.replicatingGraph;
    }

    /**
     * Sets whether the working PAG should be a replicating (time-lag repeating) graph: if set,
     * the PAG is maintained as a ReplicatingGraph with a LagReplicationPolicy, so that edge
     * additions, removals, and endpoint orientations are mirrored across homologous lagged
     * variable pairs during the search, as in the other SVAR-capable algorithms. Subclasses
     * should consult isReplicatingGraph() to propagate the setting to the search that produces
     * the initial Markov CPDAG.
     *
     * @param replicatingGraph true if the graph should replicate across time lags.
     */
    public void setReplicatingGraph(boolean replicatingGraph) {
        this.replicatingGraph = replicatingGraph;
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
     * Returns the initial Markov model for the search: a CPDAG that is Markov to the data or the underlying
     * generative model (paper, Alg. 1 line 1), or any DAG in that CPDAG's Markov equivalence class. The two are
     * interchangeable: the search consults only the skeleton and the unshielded colliders of the returned graph,
     * and both are invariant across the class.
     *
     * @param verbose a boolean flag indicating whether to enable verbose logging during the construction of the
     *                Markov CPDAG.
     * @return a Markov CPDAG, or a DAG in its Markov equivalence class.
     * @throws InterruptedException if the operation is interrupted during execution.
     */
    public abstract Graph getMarkovCpdag(boolean verbose) throws InterruptedException;

    /**
     * Sets whether selection bias should be excluded during the search process.
     *
     * @param excludeSelectionBias A boolean indicating whether to exclude selection bias (true) or not (false).
     */
    public void setExcludeSelectionBias(boolean excludeSelectionBias) {
        this.excludeSelectionBias = excludeSelectionBias;
    }

    /**
     * When true, the extra-edge-removal step is gated: each candidate removal is committed only if the resulting
     * graph (after re-running the full *-FCI orientation from scratch) passes the modulo-knowledge legality
     * certificate, with sepset re-selection past gate rejections; otherwise the next separating set is tried.
     * When false, the original greedy *-FCI behavior is used (greedy removal with a single final orientation),
     * mapped to a nearby legal PAG by GraphUtils.guaranteePag only if the certificate fails. Either way, the
     * output is guaranteed to be a legal PAG possibly refined by background knowledge; this flag only selects
     * the mechanism.
     *
     * @param doLegalityGating Whether to gate each removal on per-step PAG legality (true) or repair post hoc (false).
     */
    public void setDoLegalityGating(boolean doLegalityGating) {
        this.doLegalityGating = doLegalityGating;
    }

    /**
     * Whether to do the possible d-sep step (Pass 2). On by default, and REQUIRED for exactness: an exhaustive
     * oracle test over all PAGs on six observed variables exhibits a pair whose unique separating set contains a
     * vertex adjacent to neither endpoint, which the adjacency-subset pass structurally cannot find (see the
     * StarFCI paper, Appendix A.1). Turning it off yields a theoretically incomplete configuration; the switch
     * is retained for timing studies only. In the gated configuration the pass is not expensive.
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

    /**
     * Sets the cap on legality-gate trials (re-orientation + certificate) per edge per visit on the gated path.
     * Independence tests that fail to separate do not count against the cap. The cap is per visit; later sweeps
     * rescan the edge against the shrunken graph. Disclosed in the class Javadoc as a divergence from the
     * idealized pseudocode.
     *
     * @param maxGateTrialsPerEdge the cap; must be at least 1. Default 100.
     */
    public void setMaxGateTrialsPerEdge(int maxGateTrialsPerEdge) {
        if (maxGateTrialsPerEdge < 1) {
            throw new IllegalArgumentException("Gate-trial cap must be at least 1: " + maxGateTrialsPerEdge);
        }
        this.maxGateTrialsPerEdge = maxGateTrialsPerEdge;
    }

    /**
     * Sets whether to map the final graph to a nearby legal PAG (GraphUtils.guaranteePag) when it fails the
     * modulo-knowledge certificate. Independent of {@link #setDoLegalityGating}: on the gated path every commit
     * was already certified, so the repair is a dormant safety net; on the ungated path this is the paper's
     * GuaranteePag step, and with it off the raw greedy GFCI-template output is returned, which is NOT
     * necessarily a legal PAG. Default true.
     *
     * @param guaranteePag True to repair a certificate-failing final graph to a nearby legal PAG.
     */
    public void setGuaranteePag(boolean guaranteePag) {
        this.guaranteePag = guaranteePag;
    }

    /**
     * Sets whether to log the individual conditional independence results: the independence test itself is put
     * in verbose mode (every CI query printed), and removal/trial log lines include the sepset's p-value (one
     * extra test call per line). Off by default; plain {@link #setVerbose} gives the search-level narrative
     * (removals committed, gate rejections, clamps) without the CI flood. The initial CPDAG search is verbose
     * whenever {@code verbose} is set, independent of this flag.
     *
     * @param logCiDetails True to additionally print the individual CI results.
     */
    public void setLogCiDetails(boolean logCiDetails) {
        this.logCiDetails = logCiDetails;
    }
}
