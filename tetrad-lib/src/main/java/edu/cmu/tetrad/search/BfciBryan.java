/// ////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2026 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
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
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.utils.*;
import edu.cmu.tetrad.util.SublistGenerator;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.*;

import static edu.cmu.tetrad.graph.GraphUtils.colliderAllowed;

/**
 * FROZEN REFERENCE (2026-10-21): this formulation became the shipping {@link StarFci}/{@link Bfci}; the class is kept
 * unchanged, off the interface, so that saved sessions that used "BFCI-Bryan" still load. New work goes in StarFci.
 * <p>
 * BFCI-Bryan: BOSS followed by Bryan Andrews's from-scratch, sepset-map-as-state formulation of the gated
 * StarFCI extra-edge-removal step, with SEPSET RE-SELECTION, plus a final saturating step (on by default;
 * see {@link #setUseSaturatingStep(boolean)}).
 * <p>
 * DIFFERENCE FROM {@link StarFci} (the shipping gated template). StarFci freezes the FIRST test-confirmed
 * separating set found for a pair ({@code foundSepsets}: "a discovered separating set is a frozen data fact,
 * reused verbatim") and gates single removals on that one sepset; when the frozen sepset's induced
 * re-orientation is illegal, the pair deadlocks, and the saturating Pass 3 exists to clear such deadlocks by
 * joint removal. This class instead keeps scanning: when a candidate separating set is gate-rejected, the NEXT
 * separating set for the same pair is tried, within the same visit. Different separating sets induce different
 * R0 colliders and hence different re-orientations, so a pair whose first sepset fails the gate can still be
 * removed under another. On the OBS=6 single-removal deadlock witness in {@code StarFci.saturatingRemoval}'s
 * javadoc (positional key cc...cc..ta.aaat.aaacat), re-selection alone recovers the true PAG exactly, with no
 * saturating step, across 40 randomized edge/candidate orders (oracle verification 2026-10-18). Re-selection
 * alone is NOT enough in general, though: on the 6-variable model ta...ca.aaat.aa.ataaaaat the search can reach,
 * by moves the gate accepts, a state from which no single removal is legal under any true separator, while the
 * joint removal of the two remaining spurious edges gives the true PAG (2026-10-20). Hence the final
 * saturating step, which does nothing on a run that does not stall.
 * <p>
 * ALGORITHM (Bryan's pseudocode, with three corrections). State is the committed sepset map S alone; the
 * carried graph G is always the PROJECTION of S: a full from-scratch re-orientation (circles, colliders at
 * unshielded triples x-y-z from y's absence from S(x,z), Zhang's complete final rules with R4 reading S) with
 * no other source of orientation. S is TOTAL on nonadjacencies throughout: it is seeded from the DAG
 * representative D of the BOSS CPDAG (for each nonadjacent pair, PA(a) or PA(b), whichever d-separates in D;
 * these entries reproduce exactly D's unshielded colliders), and every removal overwrites one entry. This is
 * the same seeding and projection as {@link StarFciC}; the two classes differ only in the removal schedule
 * (re-selection plus a final saturating step here; frozen sepsets plus a saturating step there), so StarFciC
 * stands to this class as an optimization.
 * <pre>
 * G0 &lt;- BOSS CPDAG;  D &lt;- DAG representative of G0
 * S &lt;- { (a,b) -&gt; PA_D(a) or PA_D(b), the one d-separating a,b in D : a,b nonadjacent in D }
 * G &lt;- dagToPag(D)                                  // = projection of S, legal by construction
 * Pass 0: for each (a,b) nonadjacent in D with NOT a _||_ b | S(a,b) in the data   // D not an I-map here
 *   re-select S(a,b) by the same loop as an edge visit (Try over separating C, smallest first); keep D's
 *   entry if nothing is accepted. Repeated once after Pass 2 for pairs still pending.
 * Pass 1 (adjacency subsets), then Pass 2 (possible-D-SEP subsets), each to a fixpoint:
 *   Repeat
 *     changed &lt;- false
 *     For (a,b) in EDGES(G)
 *       For C in SUBSETS_d(pool(G,a)\{a,b}) ++ SUBSETS_d(pool(G,b)\{a,b})   // smallest first
 *         If a _||_ b | C
 *           If Try(G, S, a, b, C) = ACCEPT
 *             changed &lt;- true; break          // next edge; REJECT =&gt; next C
 *   While changed
 * Return G
 * </pre>
 * where Try removes a-b on a copy (a no-op in Pass 0, where the pair is already nonadjacent), seeds C into a
 * trial-local copy of S, re-projects from scratch, and commits G and S together iff the result passes the
 * legality certificate (modulo background knowledge, exactly as in {@link StarFci}); a rejected trial leaves no
 * trace. The corrections relative to the pseudocode as given: (1) S and G commit atomically (the original
 * added C to S before the gate, so a revert left the sepset behind); (2) the fixpoint test is an explicit
 * changed flag; (3) a pair with no recorded sepset gets no sepset collider in the re-orientation (with the
 * total seeding this can no longer occur).
 * <p>
 * WHY PASS 0 (2026-10-20). The initializer can return a D that is not an I-map of the test (seen in the
 * exhaustive 5-variable certification of BFCI-C when BOSS ran without BES: a tied, non-I-map D claimed a false
 * d-separation; projecting it stamped a false collider that closed to a legal PAG of the wrong class, which the
 * legality gate cannot see). Pass 0 lets the test correct such entries, under the same gate as everything
 * else. Overwriting them ungated instead would make S the projection of no single MAG and the baseline
 * routinely illegal at scale. With BES on, D is an I-map under faithfulness and Pass 0 queues nothing.
 * <p>
 * COSTS, DISCLOSED. Re-selection runs one full re-orientation plus one legality check per SEPARATING candidate
 * set scanned, not per pair, so {@code maxGateTrialsPerEdge} caps the gate trials per edge per visit (the cap
 * is per visit; later sweeps rescan against the shrunken graph). A committed sepset is thereby selected partly
 * for orientation coherence rather than independence strength alone; every committed sepset is still
 * individually test-confirmed. The Pass-2 subset search keeps StarFci's clamp: when {@code depth} is unlimited
 * (-1), subsets of possible-D-SEP pools are capped at size {@value #DSEP_DEPTH_CLAMP}.
 * <p>
 * This class is configured to respect knowledge of forbidden and required edges, including knowledge of
 * temporal tiers, by the same modulo-knowledge certificate as {@link StarFci}.
 *
 * @author bryanandrews
 * @author josephramsey
 * @see StarFci
 * @see Bfci
 * @see Boss
 */
public final class BfciBryan implements IGraphSearch {

    /**
     * Cap on the subset size searched over possible-D-SEP pools in Pass 2 when the global {@code depth} is
     * unlimited (-1), exactly as in {@link StarFci}.
     */
    private static final int DSEP_DEPTH_CLAMP = 4;
    /**
     * The independence test used in search.
     */
    private final IndependenceTest independenceTest;
    /**
     * The score used by BOSS.
     */
    private final Score score;
    /**
     * The knowledge used in search.
     */
    private Knowledge knowledge = new Knowledge();
    /**
     * Whether Zhang's complete rules are used.
     */
    private boolean completeRuleSetUsed = true;
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
     * Whether the final FCI orientation engine logs its rule-by-rule output; see
     * {@link StarFci#setLogFinalOrientations(boolean)}.
     */
    private boolean logFinalOrientations = false;
    /**
     * Whether to exclude selection bias.
     */
    private boolean excludeSelectionBias = false;
    /**
     * Whether to do the possible-D-SEP pass (Pass 2). Required for exactness; switch retained for timing
     * studies only, as in {@link StarFci}.
     */
    private boolean usePossibleDsep = true;
    /**
     * Maximum path length for possible d-sep.
     */
    private int maxPossibleDsepPathLength = -1;
    /**
     * Cap on legality-gate trials (re-orientation + certificate) per edge per visit. Independence tests that
     * fail to separate do not count. The cap is a compute guard on real data, where many candidate sets may
     * separate at moderate alpha; it is per visit, so later sweeps retry against the shrunken graph.
     */
    private int maxGateTrialsPerEdge = 100;
    /**
     * Whether to run a saturating step after the passes: jointly remove every still-adjacent pair for which a
     * separating set was found but every gated single removal was rejected. Default true. Without it the search
     * can stall from a reachable state in which no single removal is legal (6-variable instance: model
     * ta...ca.aaat.aa.ataaaaat, reached by legal moves V2-V3 | {V6}, V2-V6 | {V1,V5}, V1-V5 | {V6}). In the oracle
     * a pair gets a separator only if it is truly non-adjacent, so the joint removal yields the true skeleton
     * with true separators, whose projection is G*. On a run that never stalls the step does nothing.
     */
    private boolean useSaturatingStep = true;
    /**
     * EXPERIMENTAL I-MAP GATE (2026-10-23; the 2026-10-21 gate restored, with a separate alpha). When true, every
     * candidate the legality gate accepts in Passes 1-2 and in the saturating step must additionally be an I-map
     * of the test: for every nonadjacent pair x,z of the candidate's Zhang MAG, some separator of x,z in that
     * MAG must be judged independent. Checked pairwise with as few tests as a test-based certificate allows: a
     * pair passes at no cost if a KNOWN separator (its committed entry S(x,z), or any set an earlier I-map check
     * found independent for it) m-separates x,z in the candidate MAG; otherwise one recursive-blocking separator
     * is tested at {@link #imapAlpha}. The gate vetoes only violations the MOVE introduces: a pair that is
     * already uncertified in the committed state (a refuted seed, or a pair whose entry stopped separating under
     * an earlier Pass-0 repair) is exempt, so that a pre-existing violation cannot veto every later candidate
     * (observed 2026-10-23: one such pair vetoed 599 candidates in a row). Known separators are cached only;
     * they are NOT written into S, since the carried graph must stay the projection of S.
     * <p>
     * What this does and does not give: with it, every committed state after Pass 0 asserts no independence the
     * test has not confirmed (at imapAlpha). Correctness of the gated search then rests on the "GMC for
     * projections" conjecture (from every I-map projection state other than G*, some single projection move with
     * a true separator reaches another I-map state), exhaustively supported at 5 observed variables and so far at
     * 6, open in general. On data (2026-10-21, with imapAlpha = alpha) it reduced accuracy: each false "dependent"
     * vetoes a correct move. The separate, smaller imapAlpha is the attempt to fix that.
     */
    private boolean requireImapStates = false;
    /**
     * Whether Pass 0 runs (default true). Pass 0 is not in Bryan's pseudocode: it re-selects seeded sepset entries
     * the test refutes. 2026-10-23, 20+5 variables, n = 1000, 10 models: the RICF-score arm, which removed
     * almost no edges, still lost 7 points of arrowhead precision relative to the BOSS start; Pass 0 is the only
     * other source of change, so this switch exists to measure it.
     */
    private boolean usePass0 = true;
    /**
     * EXPERIMENTAL (2026-10-24). Keep S P-FAITHFUL: after every commit, each entry S(x,z) that no longer
     * m-separates x,z in the committed graph (its Zhang MAG) is replaced by a separator that does (recursive
     * blocking). Entries that still separate are left alone. Default false.
     * <p>
     * Why. For a total map of separators OF THE CURRENT PAG P, the projection is P itself (any separator decides
     * every unshielded collider; R4 reads P-separators; Zhang's rules complete), so re-harvesting preserves
     * G = proj(S). With S P-faithful, the generalized Meek conjecture (GMC) implies the no-stall property of the
     * I-map-gated search: take a GMC path from a MAG of P to M*; the class P'' right after its first deletion
     * (a,b) satisfies I(P) &sube; I(P''), so S's entries are P''-separators, any P''-separator C of a,b completes a
     * total P''-separator map, and proj(S + {(a,b) -&gt; C}) = P'' -- legal, an I-map, one edge fewer. The mark
     * changes GMC may need before that deletion are absorbed by re-projection. Without P-faithfulness the
     * projection of S + {C} need not be P'' (S-faithfulness failure, seen in accepted states), and GMC gives
     * nothing. Remaining lemma, not yet written out: C lies in the pools computed in P (possible-D-SEP
     * monotonicity for comparable I-maps, modulo the depth clamp).
     * <p>
     * On data a re-harvested entry is graph-derived, not test-confirmed; the I-map gate treats it as unconfirmed
     * until a check tests it. In the oracle, from an I-map state, every re-harvested entry is a true separator.
     * When verbose, the idempotence claim is checked after each re-harvest by re-projecting and comparing.
     */
    private boolean reharvestSepsets = false;
    /** Pairs whose entry was re-harvested from the graph and not (yet) test-confirmed. */
    private final Set<Set<Node>> unconfirmedEntries = new HashSet<>();
    /** Re-harvest tallies. */
    private int reharvestedEntries = 0, reharvestFailures = 0, reprojectionMismatches = 0;
    /**
     * Significance level of the I-map check's tests, applied to the test's p-value when the test reports a
     * probability (else the test's own verdict is used). Smaller than the removal alpha by design: a veto should
     * need a STRONG dependence. Default 0.001.
     */
    private double imapAlpha = 0.001;
    /**
     * Known test-confirmed separators per pair for the I-map check: the committed entry plus any separator an
     * earlier check found independent. A cache, never written to S.
     */
    private final Map<Set<Node>, List<Set<Node>>> knownSeparators = new HashMap<>();
    /** Pairs whose seeded entry the test refuted: their entry is not a known separator. */
    private final Set<Set<Node>> refutedSeeds = new HashSet<>();
    /** Pairs uncertified in the committed state; exempt from the check (violations predating the move). */
    private Set<Set<Node>> baselineUncertified = new HashSet<>();
    /** I-map check tallies for the end-of-run summary. */
    private int imapCandidates = 0, imapVetoes = 0, imapTests = 0, imapIndeterminate = 0;
    /** First separating set found for each pair, cached across visits (survives gate rejections). */
    private final Map<Set<Node>, Set<Node>> foundSepsets = new HashMap<>();
    /**
     * The number of BOSS restarts.
     */
    private int numStarts = 1;
    /**
     * Whether BOSS should use BES as a final step.
     */
    // Default TRUE (2026-10-20): the hybrid relies on the BOSS CPDAG being an I-map, which BES guarantees under
    // faithfulness; see Params.USE_BES_DEFAULT_TRUE.
    private boolean bossUseBes = true;
    /**
     * The number of threads BOSS uses.
     */
    private int numThreads = 1;

    /**
     * Constructor. The test and score should be for the same data.
     *
     * @param test  The test to use.
     * @param score The score to use (for BOSS).
     */
    public BfciBryan(IndependenceTest test, Score score) {
        if (test == null) throw new NullPointerException("Test is null");
        if (score == null) throw new NullPointerException("Score is null");
        this.independenceTest = test;
        this.score = score;
    }

    /**
     * Runs the search and returns the PAG.
     *
     * @return The PAG.
     * @throws InterruptedException if interrupted.
     */
    public Graph search() throws InterruptedException {
//        this.independenceTest.setVerbose(verbose);
        List<Node> nodes = new ArrayList<>(independenceTest.getVariables());

        Graph cpdag = getMarkovCpdag();
        Graph initDag = cpdag.paths().isLegalDag() ? cpdag : GraphTransforms.dagFromCpdag(cpdag);
        Graph pag = GraphTransforms.dagToPag(initDag, false);

        SepsetMap sepsetMap = new SepsetMap();
        foundSepsets.clear();
        knownSeparators.clear();
        refutedSeeds.clear();
        unconfirmedEntries.clear();
        reharvestedEntries = reharvestFailures = reprojectionMismatches = 0;
        imapCandidates = imapVetoes = imapTests = imapIndeterminate = 0;

        // SEEDING (see class doc): a TOTAL sepset map from the DAG representative D -- for each nonadjacent
        // pair, PA(a) if it d-separates a and b in D, else PA(b) (one always does: nonadjacent nodes cannot
        // be mutual descendants). Identical to StarFciC's harvest. Entries that fail the actual test are
        // queued for Pass 0 re-selection; D's entry stays in S meanwhile, so S remains D's projection.
        List<Node[]> pendingReselect = new ArrayList<>();

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                Node a = nodes.get(i);
                Node b = nodes.get(j);
                Node ga = initDag.getNode(a.getName());
                Node gb = initDag.getNode(b.getName());

                if (ga == null || gb == null || initDag.isAdjacentTo(ga, gb)) {
                    continue;
                }

                Set<Node> paA = new HashSet<>(initDag.getParents(ga));
                Set<Node> sep = initDag.paths().isMSeparatedFrom(ga, gb, paA, false)
                        ? paA : new HashSet<>(initDag.getParents(gb));

                // Record against the search's own node objects.
                Set<Node> sepMapped = new HashSet<>();
                for (Node n : sep) {
                    Node own = independenceTest.getVariable(n.getName());
                    sepMapped.add(own != null ? own : n);
                }
                sepsetMap.set(a, b, sepMapped);

                if (!independenceTest.checkIndependence(a, b, sepMapped).isIndependent()) {
                    pendingReselect.add(new Node[]{a, b});
                    refutedSeeds.add(Set.of(a, b));
                }
            }
        }

        Set<Node> selection = new LinkedHashSet<>();
        for (Node node : nodes) {
            if (node.getNodeType() == NodeType.SELECTION) {
                selection.add(node);
            }
        }

        if (verbose) {
            TetradLogger.getInstance().log("Starting BFCI-Bryan extra edge removal (sepset projection, "
                    + "sepset re-selection" + (useSaturatingStep ? ", saturating step" : ", no saturating step")
                    + "); " + pendingReselect.size()
                    + " seeded sepset(s) failed the test and are queued for re-selection.");
        }

        if (requireImapStates) refreshBaseline(pag, sepsetMap);

        // Pass 0: re-select seeded sepsets the test rejects, by the ordinary edge-visit loop (the pair is
        // already nonadjacent, so the trial's removeEdge is a no-op; only the entry changes). Gated like
        // everything else; a pair with no accepted candidate keeps D's entry and is retried after Pass 2.
        if (usePass0) pag = reselectPending(pag, pendingReselect, cpdag, nodes, sepsetMap, selection);

        // Pass 1: adjacency subsets, to a fixpoint.
        boolean changed;
        do {
            changed = false;
            for (Edge edge : new ArrayList<>(pag.getEdges())) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                Node a = edge.getNode1();
                Node b = edge.getNode2();
                if (!pag.isAdjacentTo(a, b)) continue;

                List<List<Node>> pools = new ArrayList<>();
                pools.add(adjPool(pag, a, b));
                pools.add(adjPool(pag, b, a));

                Graph next = tryRemoveEdge(pag, a, b, pools, depth, cpdag, nodes, sepsetMap,
                        selection, "adjacency-subset");
                if (next != null) {
                    pag = next;
                    changed = true;
                }
            }
        } while (changed);

        // Pass 2: possible-D-SEP subsets, to a fixpoint. The carried graph is fully oriented (dagToPag
        // start, or the last accepted commit's re-orientation), as possible-D-SEP presupposes.
        if (usePossibleDsep) {
            do {
                changed = false;
                for (Edge edge : new ArrayList<>(pag.getEdges())) {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                    Node a = edge.getNode1();
                    Node b = edge.getNode2();
                    if (!pag.isAdjacentTo(a, b)) continue;

                    List<List<Node>> pools = new ArrayList<>();
                    pools.add(dsepPool(pag, a, b));
                    pools.add(dsepPool(pag, b, a));

                    int poolMax = Math.max(pools.get(0).size(), pools.get(1).size());
                    int dsepDepth = (depth < 0) ? Math.min(poolMax, DSEP_DEPTH_CLAMP) : depth;

//                    if (verbose && depth < 0 && poolMax > DSEP_DEPTH_CLAMP) {
//                        TetradLogger.getInstance().log("Possible-D-SEP subset search for " + a + " -- " + b
//                                + " clamped to subsets of size <= " + DSEP_DEPTH_CLAMP + " (pool size "
//                                + poolMax + "); set depth explicitly to override.");
//                    }

                    Graph next = tryRemoveEdge(pag, a, b, pools, dsepDepth, cpdag, nodes, sepsetMap,
                            selection, "possible-D-SEP");
                    if (next != null) {
                        pag = next;
                        changed = true;
                    }
                }
            } while (changed);
        }

        // Pass 0 again for pairs still pending: removals since may have made a re-selection legal.
        if (usePass0) pag = reselectPending(pag, pendingReselect, cpdag, nodes, sepsetMap, selection);

        // Saturating step: clears states in which no single removal is legal (see the field doc).
        if (useSaturatingStep) {
            pag = saturatingRemoval(pag, cpdag, nodes, sepsetMap, selection);
        }

        if (reharvestSepsets) {
            TetradLogger.getInstance().log("BFCI-Bryan re-harvest summary: entries re-harvested = "
                    + reharvestedEntries + ", pairs with no separator found = " + reharvestFailures
                    + (verbose ? ", re-projection mismatches = " + reprojectionMismatches : "") + ".");
        }

        if (requireImapStates) {
            TetradLogger.getInstance().log("BFCI-Bryan I-map gate summary (imapAlpha = " + imapAlpha
                    + "): legal candidates checked = " + imapCandidates + ", vetoed = " + imapVetoes
                    + ", tests spent = " + imapTests + ", indeterminate separators = " + imapIndeterminate + ".");
        }

        // Uniform final knowledge refinement, as in StarFci: covers the path on which no removal ever
        // committed, where pag is the untouched dagToPag start carrying no knowledge marks.
        if (!knowledge.isEmpty()) {
            FciOrient refineOrient = buildFciOrient(new SepsetMap(sepsetMap));
            Graph refined = refineWithKnowledge(pag, refineOrient);

            if (legalPagModuloKnowledge(refined, new LinkedHashSet<>(selection), refineOrient).isLegalPag()) {
                pag = refined;
            } else if (verbose) {
                TetradLogger.getInstance().log("Final knowledge refinement failed the modulo-knowledge "
                        + "certificate (likely a knowledge/data conflict); returning the unrefined graph.");
            }
        }

        pag = GraphUtils.replaceNodes(pag, nodes);

        if (verbose) {
            TetradLogger.getInstance().log("BFCI-Bryan finished.");
        }

        return pag;
    }

    /**
     * Pass 0: for each queued nonadjacent pair, run the ordinary edge-visit loop over the adjacency pools of the
     * current graph. An accepted candidate replaces the pair's seeded entry in the committed map (and the carried
     * graph becomes the accepted projection); the pair is then dropped from the queue. Pairs with no accepted
     * candidate keep D's entry and stay queued.
     */
    private Graph reselectPending(Graph pag, List<Node[]> pending, Graph cpdag, List<Node> nodes,
                                  SepsetMap sepsetMap, Set<Node> selection) throws InterruptedException {
        for (Iterator<Node[]> it = pending.iterator(); it.hasNext(); ) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            Node[] pr = it.next();
            Node a = pr[0], b = pr[1];

            List<List<Node>> pools = new ArrayList<>();
            pools.add(adjPool(pag, a, b));
            pools.add(adjPool(pag, b, a));

            Graph next = tryRemoveEdge(pag, a, b, pools, depth, cpdag, nodes, sepsetMap, selection,
                    "seeded-sepset re-selection");
            if (next != null) {
                pag = next;
                it.remove();
                refutedSeeds.remove(Set.of(a, b));
            }
        }
        return pag;
    }

    /**
     * Saturating step, run once after the passes. R = every still-adjacent pair with a cached separating set.
     * Trial 1 removes all of R at once, records their separators, re-projects, and gates once on legality. If
     * that fails, one leave-one-out rung is tried (each subset of size |R|-1, first legal wins) before keeping
     * the graph as it is. Every removal in a batch is individually test-confirmed; the batch as a whole must
     * pass the same legality certificate as a single removal.
     */
    private Graph saturatingRemoval(Graph pag, Graph cpdag, List<Node> nodes, SepsetMap sepsetMap,
                                    Set<Node> selection) throws InterruptedException {
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
                    + " separable edge(s) survived the passes; trying joint removal: " + formatBatch(stalled) + ".");
        }

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
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();

            Graph trial = pag.copy();
            SepsetMap trialMap = new SepsetMap(sepsetMap);
            for (Set<Node> pair : batch) {
                List<Node> pn = new ArrayList<>(pair);
                trial.removeEdge(pn.get(0), pn.get(1));
                trialMap.set(pn.get(0), pn.get(1), foundSepsets.get(pair));
            }
            FciOrient trialOrient = buildFciOrient(trialMap);
            gfciOrientPag(trial, cpdag, nodes, trialMap, trialOrient);

            PagLegalityCheck.LegalPagRet legal =
                    legalPagModuloKnowledge(trial, new LinkedHashSet<>(selection), trialOrient);

            if (legal.isLegalPag() && requireImapStates && !isImapOfTest(trial, trialMap,
                    "saturating batch " + formatBatch(batch))) {
                continue;
            }

            if (legal.isLegalPag()) {
                for (Set<Node> pair : batch) {
                    List<Node> pn = new ArrayList<>(pair);
                    sepsetMap.set(pn.get(0), pn.get(1), foundSepsets.get(pair));
                }
                for (Set<Node> pair : batch) unconfirmedEntries.remove(pair);
                if (reharvestSepsets) reharvest(trial, sepsetMap, cpdag, nodes);
                if (requireImapStates) refreshBaseline(trial, sepsetMap);
                if (verbose) {
                    TetradLogger.getInstance().log("Saturating step: removed " + batch.size() + " of "
                            + stalled.size() + " edge(s) jointly (legal PAG): " + formatBatch(batch) + ".");
                }
                return trial;
            } else if (verbose) {
                TetradLogger.getInstance().log("\tSaturating step: joint removal of " + batch.size()
                        + " edge(s) not legal (reverted): " + formatBatch(batch) + ". Reason: " + legal.getReason());
            }
        }

        if (verbose) {
            TetradLogger.getInstance().log("Saturating step: no joint removal was legal; graph kept.");
        }
        return pag;
    }

    /** Formats a batch of pairs as "a -- b (sepset = [...])" for verbose logging. */
    private String formatBatch(List<Set<Node>> batch) {
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
     * One edge visit: scan candidate conditioning sets from the given pools, smallest first, trying each
     * SEPARATING set against the legality gate until one is accepted or the gate-trial cap is reached. This is
     * the sepset re-selection loop: a gate rejection moves to the NEXT separating set for the same pair rather
     * than retiring the pair.
     *
     * @return the accepted (re-oriented) graph, with the sepset committed to {@code sepsetMap}; or null if no
     * candidate was accepted (graph and committed map untouched).
     */
    private Graph tryRemoveEdge(Graph pag, Node a, Node b, List<List<Node>> pools, int subsetDepth,
                                Graph cpdag, List<Node> nodes, SepsetMap sepsetMap, Set<Node> selection,
                                String type) throws InterruptedException {
        Set<Set<Node>> tried = new HashSet<>();
        int gateTrials = 0;

        for (List<Node> pool : pools) {
            int d = (subsetDepth < 0 || subsetDepth > pool.size()) ? pool.size() : subsetDepth;
            SublistGenerator gen = new SublistGenerator(pool.size(), d);
            int[] choice;

            while ((choice = gen.next()) != null) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();

                Set<Node> C = GraphUtils.asSet(choice, pool);
                if (!tried.add(C)) continue;

                if (!independenceTest.checkIndependence(a, b, C).isIndependent()) continue;
                foundSepsets.putIfAbsent(Set.of(a, b), C);

                if (gateTrials >= maxGateTrialsPerEdge) {
                    if (verbose) {
                        TetradLogger.getInstance().log("\t" + a + " -- " + b + " (" + type + "): gate-trial cap of "
                                + maxGateTrialsPerEdge + " reached; deferring to a later sweep.");
                    }
                    return null;
                }
                gateTrials++;

                // Gated trial: copy, remove, re-orient from scratch against a trial-local sepset map seeded
                // with this candidate, certify. Commit S and G together on acceptance; no trace on rejection.
                Graph _pag = pag.copy();
                _pag.removeEdge(a, b);

                SepsetMap trialMap = new SepsetMap(sepsetMap);
                trialMap.set(a, b, C);
                FciOrient trialOrient = buildFciOrient(trialMap);
                gfciOrientPag(_pag, cpdag, nodes, trialMap, trialOrient);

                PagLegalityCheck.LegalPagRet legal =
                        legalPagModuloKnowledge(_pag, new LinkedHashSet<>(selection), trialOrient);

                if (legal.isLegalPag() && requireImapStates
                        && !isImapOfTest(_pag, trialMap, a + " -- " + b + " | " + C)) {
                    continue;   // I-map veto: nothing committed; try the next separating set
                }

                if (legal.isLegalPag()) {
                    sepsetMap.set(a, b, C);
                    unconfirmedEntries.remove(Set.of(a, b));
                    if (reharvestSepsets) reharvest(_pag, sepsetMap, cpdag, nodes);
                    if (requireImapStates) refreshBaseline(_pag, sepsetMap);
                    if (verbose) {
                        TetradLogger.getInstance().log("Removed edge " + a + " -- " + b + " (" + type
                                + ", legal PAG); sepset = " + C + " (gate trial " + gateTrials + ").");
                    }
                    return _pag;
                } else if (verbose) {
                    TetradLogger.getInstance().log("\tTried removing " + a + " -- " + b + " (" + type
                            + "); sepset = " + C + ", but it didn't lead to a legal PAG; trying the next "
                            + "separating set. Reason: " + legal.getReason());
                }
            }
        }

        if (verbose && gateTrials > 0) {
            TetradLogger.getInstance().log("\t" + a + " -- " + b + " (" + type + "): all " + gateTrials
                    + " separating set(s) gate-rejected this visit; edge kept.");
        }

        return null;
    }

    /**
     * The I-map check (see {@link #requireImapStates}): the candidate passes iff every nonadjacent pair of its
     * Zhang MAG that is NOT already uncertified in the committed state has a test-confirmed separator.
     */
    private boolean isImapOfTest(Graph candidate, SepsetMap trialMap, String what) throws InterruptedException {
        imapCandidates++;
        Set<Node> bad = firstNewUncertifiedPair(candidate, trialMap, what);
        if (bad != null) imapVetoes++;
        return bad == null;
    }

    /**
     * Recomputes the committed state's uncertified pairs after a commit (tests spent here are cached by the
     * known-separator map and by the test itself).
     */
    private void refreshBaseline(Graph committed, SepsetMap map) throws InterruptedException {
        Set<Set<Node>> base = new HashSet<>();
        Graph mag;
        try {
            mag = GraphTransforms.zhangMagFromPag(committed);
        } catch (Exception e) {
            baselineUncertified = base;
            return;
        }
        List<Node> ns = mag.getNodes();
        for (int i = 0; i < ns.size(); i++) {
            for (int j = i + 1; j < ns.size(); j++) {
                Node x = ns.get(i), z = ns.get(j);
                if (x.getNodeType() != NodeType.MEASURED || z.getNodeType() != NodeType.MEASURED) continue;
                if (mag.isAdjacentTo(x, z)) continue;
                if (!certified(mag, map, x, z, null)) base.add(Set.of(x, z));
            }
        }
        if (verbose && !base.isEmpty()) {
            TetradLogger.getInstance().log("\tI-map gate: committed state has " + base.size()
                    + " uncertified pair(s), exempt until repaired: " + base);
        }
        baselineUncertified = base;
    }

    /**
     * Returns the first nonadjacent pair of the candidate's Zhang MAG that is uncertified and not exempt, or null
     * if none (the candidate introduces no uncertified independence).
     */
    private Set<Node> firstNewUncertifiedPair(Graph candidate, SepsetMap trialMap, String what)
            throws InterruptedException {
        Graph mag;
        try {
            mag = GraphTransforms.zhangMagFromPag(candidate);
        } catch (Exception e) {
            if (verbose) TetradLogger.getInstance().log("\tI-map gate: no Zhang MAG for " + what + "; vetoed.");
            return Set.of();
        }

        List<Node> ns = mag.getNodes();
        for (int i = 0; i < ns.size(); i++) {
            for (int j = i + 1; j < ns.size(); j++) {
                Node x = ns.get(i), z = ns.get(j);
                if (x.getNodeType() != NodeType.MEASURED || z.getNodeType() != NodeType.MEASURED) continue;
                if (mag.isAdjacentTo(x, z)) continue;
                Set<Node> key = Set.of(x, z);
                if (baselineUncertified.contains(key)) continue;
                if (!certified(mag, trialMap, x, z, what)) return key;
            }
        }
        return null;
    }

    /**
     * Re-harvest (see {@link #reharvestSepsets}): replace each entry that no longer m-separates its pair in the
     * committed graph's Zhang MAG by a recursive-blocking separator in that MAG. Replaced entries are marked
     * unconfirmed for the I-map gate. When verbose, re-projects and reports any mismatch with the committed graph.
     */
    private void reharvest(Graph committed, SepsetMap map, Graph cpdag, List<Node> nodes) throws InterruptedException {
        Graph mag;
        try {
            mag = GraphTransforms.zhangMagFromPag(committed);
        } catch (Exception e) {
            return;
        }

        List<Node> ns = mag.getNodes();
        int changed = 0;

        for (int i = 0; i < ns.size(); i++) {
            for (int j = i + 1; j < ns.size(); j++) {
                Node x = ns.get(i), z = ns.get(j);
                if (mag.isAdjacentTo(x, z)) continue;
                Set<Node> entry = map.get(x, z);
                if (entry != null && mag.paths().isMSeparatedFrom(x, z, entry, false)) continue;

                Set<Node> d;
                try {
                    RecursiveBlocking.BlockingResult b = RecursiveBlocking.blockPathsRecursively(
                            mag, x, z, Set.of(), Set.of(), -1, -1, -1, 1, true);
                    d = (b == null || b.indeterminate() || b.blockingSet() == null) ? null : new HashSet<>(b.blockingSet());
                } catch (Exception e) {
                    d = null;
                }

                if (d == null || !mag.paths().isMSeparatedFrom(x, z, d, false)) {
                    reharvestFailures++;
                    if (verbose) {
                        TetradLogger.getInstance().log("\tRe-harvest: no separator found for " + x + ", " + z
                                + " in the committed MAG; entry left as " + entry + ".");
                    }
                    continue;
                }

                d.removeIf(n -> n.getNodeType() == NodeType.LATENT);
                map.set(x, z, d);
                unconfirmedEntries.add(Set.of(x, z));
                reharvestedEntries++;
                changed++;
                if (verbose) {
                    TetradLogger.getInstance().log("\tRe-harvest: S(" + x + ", " + z + ") " + entry + " -> " + d + ".");
                }
            }
        }

        if (verbose && changed > 0) {
            // Idempotence check: a total map of P-separators should project back to P.
            Graph re = committed.copy();
            gfciOrientPag(re, cpdag, nodes, new SepsetMap(map), buildFciOrient(new SepsetMap(map)));
            if (!re.equals(committed)) {
                reprojectionMismatches++;
                TetradLogger.getInstance().log("\tRe-harvest: WARNING, re-projection of the re-harvested map differs "
                        + "from the committed graph (idempotence failure); G = proj(S) does not hold here.");
            }
        }
    }

    /**
     * Whether x,z (nonadjacent in mag) has a test-confirmed separator: a known separator (the map entry, unless
     * the pair is a refuted seed, or a cached confirmed set) that m-separates them in mag; failing that, the
     * recursive-blocking separator in mag, tested once at imapAlpha and cached if independent.
     */
    private boolean certified(Graph mag, SepsetMap map, Node x, Node z, String what) throws InterruptedException {
        Set<Node> key = Set.of(x, z);
        List<Set<Node>> known = new ArrayList<>();
        Set<Node> entry = map.get(x, z);
        if (entry != null && !refutedSeeds.contains(key) && !unconfirmedEntries.contains(key)) known.add(entry);
        List<Set<Node>> cached = knownSeparators.get(key);
        if (cached != null) known.addAll(cached);

        for (Set<Node> sep : known) {
            if (mag.paths().isMSeparatedFrom(x, z, sep, false)) return true;
        }

        Set<Node> d;
        try {
            RecursiveBlocking.BlockingResult b = RecursiveBlocking.blockPathsRecursively(
                    mag, x, z, Set.of(), Set.of(), -1, -1, -1, 1, true);
            d = (b == null || b.indeterminate() || b.blockingSet() == null) ? null : new HashSet<>(b.blockingSet());
        } catch (Exception e) {
            d = null;
        }

        if (d == null || !mag.paths().isMSeparatedFrom(x, z, d, false)) {
            imapIndeterminate++;
            if (verbose && what != null) {
                TetradLogger.getInstance().log("\tI-map gate: no separator found for " + x + ", " + z
                        + " in the candidate MAG (" + what + "); vetoed.");
            }
            return false;
        }

        d.removeIf(n -> n.getNodeType() == NodeType.LATENT);
        imapTests++;
        IndependenceResult r = independenceTest.checkIndependence(x, z, d);
        boolean indep = independenceTest.isPValueAProbability() ? r.getPValue() > imapAlpha : r.isIndependent();

        if (!indep) {
            if (verbose && what != null) {
                TetradLogger.getInstance().log("\tI-map gate: " + what + " asserts " + x + " _||_ " + z + " | " + d
                        + ", judged dependent (p = " + r.getPValue() + "); vetoed.");
            }
            return false;
        }

        knownSeparators.computeIfAbsent(key, k -> new ArrayList<>()).add(d);
        return true;
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
     * The full from-scratch projection of the sepset map, verbatim from {@link StarFciC}: reorient everything to
     * circles, apply background knowledge, stamp a collider at every unshielded triple x-y-z with y not in
     * S(x,z) -- the map is total on nonadjacencies, and the CPDAG is NOT consulted -- then run the FCI
     * final-orientation rules with R4 reading S. ({@code cpdag} is retained in the signature for symmetry with
     * the trial loop; it is unused.)
     */
    private void gfciOrientPag(Graph pag, Graph cpdag, List<Node> nodes, SepsetMap sepsetMap,
                               FciOrient fciOrient) throws InterruptedException {
        pag.reorientAllWith(Endpoint.CIRCLE);
        fciOrient.fciOrientbk(knowledge, pag, pag.getNodes(), excludeSelectionBias);

        for (Node y : nodes) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();

            List<Node> adjacentNodes = new ArrayList<>(pag.getAdjacentNodes(y));

            for (int i = 0; i < adjacentNodes.size(); i++) {
                for (int j = i + 1; j < adjacentNodes.size(); j++) {
                    Node x = adjacentNodes.get(i);
                    Node z = adjacentNodes.get(j);

                    if (!pag.isAdjacentTo(x, z)) {
                        Set<Node> sepset = sepsetMap.get(x, z);

                        if (sepset != null && !sepset.contains(y)) {
                            if (colliderAllowed(pag, x, y, z, knowledge)) {
                                pag.setEndpoint(x, y, Endpoint.ARROW);
                                pag.setEndpoint(z, y, Endpoint.ARROW);
                            }
                        }
                    }
                }
            }
        }

        fciOrient.finalOrientation(pag, excludeSelectionBias);
    }

    /**
     * Builds a fresh FCI orientation engine bound to the given sepset map, exactly as in {@link StarFci}, so
     * each trial's R4 discriminating-path sepset appends land in that trial's own map and are discarded with
     * the trial.
     */
    private FciOrient buildFciOrient(SepsetMap sepsetMap) {
        R0R4StrategyTestBased strategy = (R0R4StrategyTestBased)
                R0R4StrategyTestBased.specialConfiguration(independenceTest, knowledge, logFinalOrientations);
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
     * Legal PAG modulo background knowledge, verbatim from {@link StarFci}: with empty knowledge this is
     * exactly {@link PagLegalityCheck#isLegalPag(Graph, Set)}; otherwise the round-trip equality is taken
     * against the knowledge-refined canonical PAG of the implied Zhang MAG.
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
            String reason = "The MAG implied by this graph was a legal MAG, but the graph is not recoverable as the "
                    + "knowledge-refined canonical PAG of that MAG -- it carries marks forced neither by the "
                    + "equivalence class nor by background knowledge";
            return new PagLegalityCheck.LegalPagRet(false, reason);
        }

        return new PagLegalityCheck.LegalPagRet(true, "This is a legal PAG refined by background knowledge");
    }

    /**
     * Refines a graph with background knowledge and closes under the complete FCI final rules, verbatim from
     * {@link StarFci}. Works on a copy; identity when knowledge is empty.
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
     * Runs BOSS to obtain the initial Markov CPDAG, exactly as in {@link Bfci}.
     */
    private Graph getMarkovCpdag() throws InterruptedException {
        if (verbose) {
            TetradLogger.getInstance().log("Starting BOSS.");
        }

        Boss subAlg = new Boss(this.score);
        subAlg.setUseBes(bossUseBes);
        subAlg.setNumStarts(this.numStarts);
        subAlg.setNumThreads(numThreads);
        subAlg.setVerbose(verbose);
        PermutationSearch alg = new PermutationSearch(subAlg);
        alg.setKnowledge(knowledge);
        Graph cpdag = alg.search(true);

        if (verbose) {
            TetradLogger.getInstance().log("Finished BOSS.");
        }

        return cpdag;
    }

    /**
     * Sets the knowledge to use in search.
     *
     * @param knowledge This knowledge.
     */
    public void setKnowledge(Knowledge knowledge) {
        if (knowledge == null) throw new NullPointerException();
        this.knowledge = knowledge;
    }

    /**
     * Sets whether Zhang's complete rules are used.
     *
     * @param completeRuleSetUsed True if so.
     */
    public void setCompleteRuleSetUsed(boolean completeRuleSetUsed) {
        this.completeRuleSetUsed = completeRuleSetUsed;
    }

    /**
     * Sets the maximum length of any discriminating path.
     *
     * @param maxDiscriminatingPathLength the maximum length, or -1 if unlimited.
     */
    public void setMaxDiscriminatingPathLength(int maxDiscriminatingPathLength) {
        if (maxDiscriminatingPathLength < -1) {
            throw new IllegalArgumentException("Max path length must be -1 (unlimited) or >= 0: "
                    + maxDiscriminatingPathLength);
        }
        this.maxDiscriminatingPathLength = maxDiscriminatingPathLength;
    }

    /**
     * Sets the depth of the sepset search.
     *
     * @param depth This depth.
     */
    public void setDepth(int depth) {
        this.depth = depth;
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
     * Sets whether the final FCI orientation engine logs its rule-by-rule output.
     *
     * @param logFinalOrientations True, if so.
     */
    public void setLogFinalOrientations(boolean logFinalOrientations) {
        this.logFinalOrientations = logFinalOrientations;
    }

    /**
     * Sets whether selection bias should be excluded.
     *
     * @param excludeSelectionBias True to exclude.
     */
    public void setExcludeSelectionBias(boolean excludeSelectionBias) {
        this.excludeSelectionBias = excludeSelectionBias;
    }

    /**
     * Sets whether to do the possible-D-SEP pass (Pass 2). Required for exactness; the switch is for timing
     * studies only.
     *
     * @param usePossibleDsep True to run Pass 2.
     */
    public void setUsePossibleDsep(boolean usePossibleDsep) {
        this.usePossibleDsep = usePossibleDsep;
    }

    /**
     * Sets the maximum path length for possible d-sep.
     *
     * @param maxPossibleDsepPathLength the maximum path length, or -1 if unlimited.
     */
    public void setMaxPossibleDsepPathLength(int maxPossibleDsepPathLength) {
        if (maxPossibleDsepPathLength < -1) {
            throw new IllegalArgumentException("Maximum path length for possible d-sep must be >= -1 (-1 = unlimited).");
        }
        this.maxPossibleDsepPathLength = maxPossibleDsepPathLength;
    }

    /**
     * Sets the cap on legality-gate trials per edge per visit.
     *
     * @param maxGateTrialsPerEdge the cap; must be at least 1.
     */
    public void setMaxGateTrialsPerEdge(int maxGateTrialsPerEdge) {
        if (maxGateTrialsPerEdge < 1) {
            throw new IllegalArgumentException("Gate-trial cap must be at least 1: " + maxGateTrialsPerEdge);
        }
        this.maxGateTrialsPerEdge = maxGateTrialsPerEdge;
    }

    /**
     * Sets whether to run the saturating step after the passes. Default true; see the field documentation.
     *
     * @param useSaturatingStep true to run it.
     */
    public void setUseSaturatingStep(boolean useSaturatingStep) {
        this.useSaturatingStep = useSaturatingStep;
    }

    /**
     * Sets the number of BOSS restarts.
     *
     * @param numStarts The number of restarts.
     */
    public void setNumStarts(int numStarts) {
        this.numStarts = numStarts;
    }

    /**
     * Sets whether BOSS should use BES.
     *
     * @param useBes True if so.
     */
    public void setBossUseBes(boolean useBes) {
        this.bossUseBes = useBes;
    }

    /**
     * Sets the number of threads BOSS uses.
     *
     * @param numThreads The number of threads; at least 1.
     */
    public void setNumThreads(int numThreads) {
        if (numThreads < 1) {
            throw new IllegalArgumentException("Number of threads must be at least 1: " + numThreads);
        }
        this.numThreads = numThreads;
    }

    /**
     * Turns the experimental I-map gate on or off (default off); see {@link #requireImapStates}.
     *
     * @param requireImapStates true to require every committed state after Pass 0 to be an I-map of the test.
     */
    public void setRequireImapStates(boolean requireImapStates) {
        this.requireImapStates = requireImapStates;
    }

    /**
     * Sets the significance level of the I-map gate's tests (default 0.001); see {@link #imapAlpha}.
     *
     * @param imapAlpha a level in [0, 1].
     */
    public void setImapAlpha(double imapAlpha) {
        if (!(imapAlpha >= 0 && imapAlpha <= 1)) throw new IllegalArgumentException("imapAlpha must be in [0, 1]");
        this.imapAlpha = imapAlpha;
    }

    /**
     * Turns Pass 0 (re-selection of test-refuted seeded sepsets) on or off; default on. See {@link #usePass0}.
     *
     * @param usePass0 false to skip Pass 0, leaving D's entries in S even where the test refutes them.
     */
    public void setUsePass0(boolean usePass0) {
        this.usePass0 = usePass0;
    }

    /**
     * Turns sepset re-harvesting on or off (default off); see {@link #reharvestSepsets}.
     *
     * @param reharvestSepsets true to keep every entry of S a separator in the committed graph.
     */
    public void setReharvestSepsets(boolean reharvestSepsets) {
        this.reharvestSepsets = reharvestSepsets;
    }
}
