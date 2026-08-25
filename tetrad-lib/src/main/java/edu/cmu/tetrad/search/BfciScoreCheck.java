///////////////////////////////////////////////////////////////////////////////
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
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.utils.FciOrient;
import edu.cmu.tetrad.search.utils.MagToPag;
import edu.cmu.tetrad.search.utils.PagLegalityCheck;
import edu.cmu.tetrad.search.utils.R0R4StrategyTestBased;
import edu.cmu.tetrad.search.utils.SepsetMap;
import edu.cmu.tetrad.sem.RicfEjml;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.SublistGenerator;
import edu.cmu.tetrad.util.TetradLogger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Stream;

import static edu.cmu.tetrad.graph.GraphUtils.colliderAllowed;

/**
 * EXPERIMENTAL: BFCI with a per-removal SCORE CHECK ("tests propose, scores dispose").
 * <p>
 * This is a self-contained variant of {@link Bfci} / {@link StarFciGuaranteePag}: BOSS supplies the Markov CPDAG,
 * the *-FCI extra-edge-removal machinery runs with per-removal legality gating exactly as in StarFciGuaranteePag,
 * and IN ADDITION every gated structural commitment (each single-edge removal in the adjacency-subset and
 * possible-D-SEP passes, and each joint batch in the saturating step) must not decrease a global model score:
 * the BIC of the Gaussian MAG implied by the candidate PAG, with the MAG likelihood maximized by RICF
 * ({@link RicfEjml}, Drton &amp; Richardson 2004).
 * <p>
 * MOTIVATION. In test-driven LV edge removal, an edge is deleted as soon as ANY candidate conditioning set yields
 * non-rejection; the removal decision is a minimum over many tests, so for attenuated latent-path dependencies the
 * probability of harvesting a spurious independence grows with the number of candidate sets, and each false removal
 * feeds sepset-based collider orientation whose errors the FCI rules then propagate. Here a test outcome is demoted
 * to a PROPOSAL: a removal is committed only if the fitted MAG model of the resulting PAG is at least as good, by
 * BIC, as the fitted MAG model of the current graph. A single spurious independence then no longer suffices to
 * delete an edge; the likelihood must agree that the edge is dispensable. Since Markov-equivalent Gaussian MAGs
 * parameterize the same covariance sets, the score is invariant to the arbitrary orientation choices made by
 * {@link GraphTransforms#zhangMagFromPag(Graph)}, so scoring the Zhang MAG scores the equivalence class.
 * <p>
 * THE SCORE. bic(G) = 2 * logLik(RICF fit of zhangMagFromPag(G)) - penaltyDiscount * k * ln(n), where k = p (error
 * variances) + #directed + #bidirected edges of the MAG (one parameter per edge), higher is better. A candidate is
 * accepted iff legal (the StarFciGuaranteePag certificate, modulo knowledge) AND bic(candidate) &gt;= bic(current).
 * With penaltyDiscount = 1 this acceptance rule is the classical BIC test: a removal that deletes one edge is
 * accepted iff twice the log-likelihood drop is at most ln(n), i.e., iff the data do not insist on the edge.
 * <p>
 * FAIL-SOFT CONTRACT. The score check requires a covariance matrix (taken from the test, the data, or
 * {@link #setCovarianceMatrix(ICovarianceMatrix)}) and a MAG with only directed and bidirected edges (RICF does not
 * fit undirected/selection blocks). Whenever the score cannot be computed (no covariance, undirected edges present,
 * RICF failure or non-finite likelihood), the check DEGRADES TO A PASS and the decision falls back to legality
 * alone, i.e., to exact StarFciGuaranteePag behavior; this is logged when verbose. The score check applies only on
 * the gated path (doLegalityGating == true, the default): on the ungated path removals are not individually
 * re-oriented, so no per-step implied MAG exists to score, and this class behaves exactly like Bfci there.
 * <p>
 * All non-score machinery (knowledge handling, the modulo-knowledge legality certificate, the saturating step, the
 * sepset bookkeeping) is copied verbatim from StarFciGuaranteePag; the score-check insertions are marked with
 * "SCORE CHECK" comments. This duplication is deliberate: the base class's gate internals are private, and this
 * class is an experimental fork meant to be diffed against it, not a maintained parallel implementation.
 *
 * @author josephramsey
 * @author bryanandrews
 * @see Bfci
 * @see StarFciGuaranteePag
 * @see RicfEjml
 */
public final class BfciScoreCheck implements IGraphSearch {
    /**
     * The independence test used in search.
     */
    private final IndependenceTest independenceTest;
    /**
     * The score used by BOSS to find the Markov CPDAG.
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
     * Whether to use the maximum p-value heuristic in the sepset search.
     */
    private boolean useMaxP = false;
    /**
     * Whether to exclude selection bias.
     */
    private boolean excludeSelectionBias = false;
    /**
     * Whether each candidate removal is gated on per-step PAG legality (and, in this class, on the score check).
     * When false this class behaves exactly like Bfci's ungated path; the score check does not apply there.
     */
    private boolean doLegalityGating = true;
    /**
     * Whether to do the possible d-sep step.
     */
    private boolean usePossibleDsep = true;
    /**
     * A flag indicating whether the LV-Heuristic results should be returned.
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
     * The number of times to restart the BOSS search.
     */
    private int numStarts = 1;
    /**
     * Whether BOSS should run BES as a final step.
     */
    private boolean bossUseBes = false;
    /**
     * The number of threads for BOSS.
     */
    private int numThreads = 1;

    // ---------------- SCORE CHECK state ----------------
    /**
     * Whether the RICF-BIC score check is applied to gated commitments. When false this class reduces exactly to
     * Bfci (useful for A/B runs within the same wrapper).
     */
    private boolean useScoreCheck = true;
    /**
     * Penalty discount c in bic = 2 * logLik - c * k * ln(n) for the MAG score check. Default 1 (classical BIC).
     * This knob is independent of the penalty discount inside the BOSS score; the algcomparison wrapper sets it
     * from its own parameter, Params.SCORE_CHECK_PENALTY_DISCOUNT.
     */
    private double scoreCheckPenaltyDiscount = 1.0;
    /**
     * The covariance matrix for RICF. If not set explicitly, it is resolved lazily from the test or its data.
     */
    private ICovarianceMatrix covarianceMatrix = null;
    /**
     * Cached MAG BIC of the current committed graph on the gated path; null means "not computed yet" or
     * "unavailable". Reset at the start of every search() and updated on every accepted commitment.
     */
    private Double currentMagBic = null;

    // ---------------- instrumentation (reset per search) ----------------
    /**
     * Per-run tallies for the removal machinery, reset at the start of every search() and reported in an
     * UNCONDITIONAL summary line at the end (accepted removals are also logged unconditionally as they happen,
     * so a run with verbose off still shows every removal). A "proposal" is a commitRemoval call, i.e., a pair
     * for which some test found a separating set.
     */
    private int tallyProposalsAdjacency = 0;
    private int tallyProposalsPDsep = 0;
    private int tallyLegalityVetoes = 0;
    private int tallyScoreVetoes = 0;
    private int tallyAcceptedSingle = 0;
    private int tallyAcceptedSaturating = 0;
    /**
     * BIC gap (current - trial, > 0) for every score veto, single-edge and saturating, for the summary's
     * min/median/max report against c * ln(n).
     */
    private final List<Double> scoreVetoGaps = new ArrayList<>();

    /**
     * Constructor. The test and score should be for the same data.
     *
     * @param test  The test to use.
     * @param score The score to use (for BOSS).
     * @see IndependenceTest
     * @see Score
     */
    public BfciScoreCheck(IndependenceTest test, Score score) {
        if (test == null) {
            throw new NullPointerException("Test is null");
        }
        if (score == null) {
            throw new NullPointerException("Score is null");
        }
        this.independenceTest = test;
        this.score = score;
    }

    private static double computePValue(Node x, Node y, Set<Node> set, IndependenceTest test) {
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
     * Runs BOSS to obtain the Markov CPDAG (as in Bfci).
     *
     * @param verbose whether to print progress.
     * @return the Markov CPDAG.
     * @throws InterruptedException if interrupted.
     */
    public Graph getMarkovDag(boolean verbose) throws InterruptedException {
        if (isVerbose()) {
            TetradLogger.getInstance().log("Starting BOSS.");
        }

        Boss subAlg = new Boss(this.score);
        subAlg.setUseBes(bossUseBes);
        subAlg.setNumStarts(this.numStarts);
        subAlg.setNumThreads(numThreads);
        subAlg.setVerbose(verbose);
        PermutationSearch alg = new PermutationSearch(subAlg);
        alg.setKnowledge(getKnowledge());
        Graph cpdag = alg.search(false);

        if (isVerbose()) {
            TetradLogger.getInstance().log("Finished BOSS.");
        }

        return cpdag;
    }

    /**
     * Finds a separating set that is a subset of the adjacency of nodes x or y in the input graph.
     * (Verbatim from StarFciGuaranteePag.)
     *
     * @param graph      The graph being analyzed.
     * @param x          The first node between which independence is checked.
     * @param y          The second node between which independence is checked.
     * @param containing A set of nodes that must be included in the separating set.
     * @param test       The independence test used to evaluate separation.
     * @param depth      The maximum size of subsets to be tested for independence.
     * @param useMaxP    True if the maxP method should be used.
     * @return A separating set (if found), or {@code null}.
     * @throws InterruptedException if interrupted.
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
     * Finds a separating set for x and y as a subset of adjx. (Verbatim from StarFciGuaranteePag.)
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
     * Runs the search and returns the PAG. Structure identical to StarFciGuaranteePag.search(), with the score
     * check applied inside the gated commitments.
     *
     * @return This PAG.
     * @throws InterruptedException if any
     */
    public Graph search() throws InterruptedException {
        this.independenceTest.setVerbose(verbose);
        List<Node> nodes = new ArrayList<>(getIndependenceTest().getVariables());

        // SCORE CHECK: reset per-search state.
        this.currentMagBic = null;

        // Instrumentation: reset per-search tallies.
        this.tallyProposalsAdjacency = 0;
        this.tallyProposalsPDsep = 0;
        this.tallyLegalityVetoes = 0;
        this.tallyScoreVetoes = 0;
        this.tallyAcceptedSingle = 0;
        this.tallyAcceptedSaturating = 0;
        this.scoreVetoGaps.clear();

        Graph cpdag = getMarkovDag(verbose);
        Graph pag = GraphTransforms.dagToPag(cpdag, false);

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
            TetradLogger.getInstance().log("Starting *-FCI extra edge removal step (with RICF-BIC score check).");
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
            pag = saturatingRemoval(pag, cpdag, nodes, sepsetMap, unshieldedColliders, selection, foundSepsets);
        }

        // Ungated greedy path: run the single final orientation. If the result already passes the
        // modulo-knowledge certificate, keep it AS IS; only if the certificate fails is the graph
        // mapped to a nearby legal PAG by guaranteePag. (Score check does not apply on this path;
        // see the class javadoc.)
        if (!doLegalityGating) {
            gfciOrientPag(pag, cpdag, nodes, sepsetMap, unshieldedColliders, fciOrient);

            if (!legalPagModuloKnowledge(pag, new LinkedHashSet<>(selection), fciOrient).isLegalPag()) {
                pag = GraphUtils.guaranteePag(pag, fciOrient, knowledge, new HashSet<>(), verbose, new HashSet<>(),
                        excludeSelectionBias, Integer.MAX_VALUE);
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
                pag = refined;
            } else if (verbose) {
                TetradLogger.getInstance().log("Final knowledge refinement failed the modulo-knowledge "
                        + "certificate (likely a knowledge/data conflict); returning the unrefined graph.");
            }
        }

        pag = GraphUtils.replaceNodes(pag, nodes);

        // Instrumentation: UNCONDITIONAL summary, so a run with verbose off still reports what the removal
        // machinery did (and, in particular, whether zero removals means zero proposals or all-vetoed).
        TetradLogger.getInstance().log(removalSummary());

        if (verbose) {
            TetradLogger.getInstance().log("BFCI-Score-Check finished.");
        }

        return pag;
    }

    /**
     * The score check's veto threshold context, c * ln(n), or NaN if no covariance is available.
     */
    private double cLogN() {
        ICovarianceMatrix cov = resolveCovariance();
        return cov == null ? Double.NaN : scoreCheckPenaltyDiscount * Math.log(cov.getSampleSize());
    }

    /**
     * One-line per-run summary of the removal machinery's tallies, including the score-veto BIC-gap
     * distribution against c * ln(n) (a removal that costs no likelihood improves BIC by exactly c * ln(n),
     * so gaps of that order are borderline calls while gaps far above it are decisive vetoes).
     */
    private String removalSummary() {
        StringBuilder sb = new StringBuilder("BFCI-Score-Check removal summary: proposals = ")
                .append(tallyProposalsAdjacency + tallyProposalsPDsep)
                .append(" (adjacency ").append(tallyProposalsAdjacency)
                .append(", pDsep ").append(tallyProposalsPDsep)
                .append("), accepted = ").append(tallyAcceptedSingle + tallyAcceptedSaturating)
                .append(" (single ").append(tallyAcceptedSingle)
                .append(", saturating ").append(tallyAcceptedSaturating)
                .append("), legality-vetoed = ").append(tallyLegalityVetoes)
                .append(", score-vetoed = ").append(tallyScoreVetoes).append(".");

        if (!scoreVetoGaps.isEmpty()) {
            List<Double> gaps = new ArrayList<>(scoreVetoGaps);
            Collections.sort(gaps);
            double min = gaps.get(0);
            double max = gaps.get(gaps.size() - 1);
            double median = gaps.size() % 2 == 1
                    ? gaps.get(gaps.size() / 2)
                    : (gaps.get(gaps.size() / 2 - 1) + gaps.get(gaps.size() / 2)) / 2.0;
            sb.append(" Score-veto BIC gaps: min = ").append(min)
                    .append(", median = ").append(median)
                    .append(", max = ").append(max)
                    .append("; c*ln(n) = ").append(cLogN()).append(".");
        }

        return sb.toString();
    }

    /**
     * Runs the full *-FCI orientation in place on {@code pag}. (Verbatim from StarFciGuaranteePag.)
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
     * Attempts to remove edge (a, c) using the given candidate sepset. Identical to
     * StarFciGuaranteePag.commitRemoval, plus the SCORE CHECK on the gated path: after the legality certificate
     * passes, the candidate must additionally have a MAG BIC at least as large as the current committed graph's;
     * otherwise the removal is reverted. If the score is unavailable (see class javadoc), the check degrades to a
     * pass.
     */
    private Graph commitRemoval(Graph pag, Node a, Node c, Set<Node> sepset, String type, boolean doLegalityGating,
                                Graph cpdag, List<Node> nodes, SepsetMap sepsetMap, Set<Triple> unshieldedColliders,
                                Set<Node> selection) throws InterruptedException {
        // Instrumentation: a commitRemoval call is a test proposal (a sepset was found for this pair).
        if ("possible-D-SEP".equals(type)) {
            tallyProposalsPDsep++;
        } else {
            tallyProposalsAdjacency++;
        }

        if (!doLegalityGating) {
            pag.removeEdge(a, c);
            sepsetMap.set(a, c, sepset);
            tallyAcceptedSingle++;
            TetradLogger.getInstance().log("REMOVED edge " + a + " -- " + c + " (" + type + ", ungated); sepset = "
                    + sepset + ".");
            if (verbose) {
                IndependenceResult result = independenceTest.checkIndependence(a, c, sepset);
                TetradLogger.getInstance().log("\t... p-value = " + result.getPValue() + ".");
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
        // reconstituted canonical PAG is knowledge-refined before the equality test.
        PagLegalityCheck.LegalPagRet legal = legalPagModuloKnowledge(_pag, new LinkedHashSet<>(selection), trialOrient);

        if (!legal.isLegalPag()) {
            tallyLegalityVetoes++;
            if (verbose) {
                TetradLogger.getInstance().log("\tTried removing " + a + " -- " + c + " (" + type
                        + "), but it didn't lead to a legal PAG (reverted). Reason: " + legal.getReason());
            }

            return pag;                                // committed sepset map untouched
        }

        // SCORE CHECK: tests propose, scores dispose. The removal must not decrease the RICF-BIC of the
        // implied MAG. If either BIC is unavailable, fall back to legality alone.
        Double trialBic = null;
        if (useScoreCheck) {
            trialBic = magBic(_pag);

            if (trialBic != null) {
                if (currentMagBic == null) {
                    currentMagBic = magBic(pag);
                }

                if (currentMagBic != null && trialBic < currentMagBic) {
                    double gap = currentMagBic - trialBic;
                    tallyScoreVetoes++;
                    scoreVetoGaps.add(gap);
                    if (verbose) {
                        TetradLogger.getInstance().log("\tTried removing " + a + " -- " + c + " (" + type
                                + "); legal PAG but SCORE CHECK failed (reverted): MAG BIC " + trialBic
                                + " < current " + currentMagBic + "; gap = " + gap
                                + ", c*ln(n) = " + cLogN() + ".");
                    }
                    return pag;                        // committed sepset map untouched
                }
            } else if (verbose) {
                TetradLogger.getInstance().log("\tScore check unavailable for " + a + " -- " + c
                        + " (falling back to legality alone).");
            }
        }

        // Accepted: commit only the deliberate sepset write and carry the reoriented PAG forward.
        sepsetMap.set(a, c, sepset);
        currentMagBic = trialBic;   // SCORE CHECK cache; null forces recomputation on the next trial.
        tallyAcceptedSingle++;

        TetradLogger.getInstance().log("REMOVED edge " + a + " -- " + c + " (" + type
                + ", legal PAG" + (useScoreCheck && trialBic != null ? ", score check passed" : "")
                + "); sepset = " + sepset + ".");
        if (verbose) {
            IndependenceResult result = independenceTest.checkIndependence(a, c, sepset);
            TetradLogger.getInstance().log("\t... p-value = " + result.getPValue() + ".");
        }

        return _pag;
    }

    /**
     * Saturating step for the gated path, run once after the single-edge machinery has stalled. Identical to
     * StarFciGuaranteePag.saturatingRemoval, plus the SCORE CHECK: an accepted batch must be legal AND have a MAG
     * BIC at least as large as the stalled graph's (fail-soft as elsewhere).
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
                    + " separable edge(s) survived the single-edge fixpoint; trying joint removal.");
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
                // SCORE CHECK for the joint removal, same fail-soft semantics as the single-edge gate.
                Double trialBic = null;
                if (useScoreCheck) {
                    trialBic = magBic(trial);

                    if (trialBic != null) {
                        if (currentMagBic == null) {
                            currentMagBic = magBic(pag);
                        }

                        if (currentMagBic != null && trialBic < currentMagBic) {
                            double gap = currentMagBic - trialBic;
                            tallyScoreVetoes++;
                            scoreVetoGaps.add(gap);
                            if (verbose) {
                                TetradLogger.getInstance().log("\tSaturating step: joint removal of " + batch.size()
                                        + " edge(s) legal but SCORE CHECK failed (reverted): MAG BIC " + trialBic
                                        + " < current " + currentMagBic + "; gap = " + gap
                                        + ", c*ln(n) = " + cLogN() + ".");
                            }
                            continue;   // try the next batch
                        }
                    } else if (verbose) {
                        TetradLogger.getInstance().log("\tSaturating step: score check unavailable "
                                + "(falling back to legality alone).");
                    }
                }

                // Accepted: commit exactly this batch's sepsets and carry the reoriented PAG forward.
                for (Set<Node> pair : batch) {
                    List<Node> pn = new ArrayList<>(pair);
                    sepsetMap.set(pn.get(0), pn.get(1), foundSepsets.get(pair));
                    TetradLogger.getInstance().log("REMOVED edge " + pn.get(0) + " -- " + pn.get(1)
                            + " (saturating joint removal); sepset = " + foundSepsets.get(pair) + ".");
                }
                currentMagBic = trialBic;   // SCORE CHECK cache; null forces recomputation later.
                tallyAcceptedSaturating += batch.size();
                TetradLogger.getInstance().log("Saturating step: removed " + batch.size() + " of "
                        + stalled.size() + " edge(s) jointly (legal PAG"
                        + (useScoreCheck && trialBic != null ? ", score check passed" : "") + ").");
                return trial;
            } else if (verbose) {
                TetradLogger.getInstance().log("\tSaturating step: joint removal of " + batch.size()
                        + " edge(s) not legal (reverted). Reason: " + legal.getReason());
            }
        }

        if (verbose) {
            TetradLogger.getInstance().log("Saturating step: no joint removal was legal and score-improving; "
                    + "stalled graph kept.");
        }
        return pag;
    }

    // ---------------- SCORE CHECK machinery ----------------

    /**
     * BIC of the Gaussian MAG implied by the given PAG-like graph, fitted by RICF: 2 * logLik - c * k * ln(n),
     * where k = p + (number of MAG edges). Higher is better. Returns null whenever the score cannot be computed
     * (no covariance available; the Zhang MAG carries an edge that is neither directed nor bidirected, e.g. an
     * undirected selection edge RICF cannot fit; RICF throws; or the likelihood is non-finite). Since
     * Markov-equivalent Gaussian MAGs parameterize identical covariance sets, the value is invariant to the
     * orientation choices made inside zhangMagFromPag.
     *
     * @param pag the candidate graph (a legal PAG or the committed graph on the gated path).
     * @return the MAG BIC, or null if unavailable.
     */
    private @Nullable Double magBic(Graph pag) {
        ICovarianceMatrix cov = resolveCovariance();
        if (cov == null) {
            return null;
        }

        Graph mag;
        try {
            mag = GraphTransforms.zhangMagFromPag(pag);
        } catch (Exception e) {
            return null;
        }

        int numEdges = 0;
        for (Edge e : mag.getEdges()) {
            if (Edges.isDirectedEdge(e) || Edges.isBidirectedEdge(e)) {
                numEdges++;
            } else {
                // Undirected (selection) edges: RICF (directed + bidirected only) cannot fit this model.
                return null;
            }
        }

        double logLik;
        try {
            RicfEjml.RicfResult result = new RicfEjml().ricf(mag, cov);
            logLik = result.getLogLik();
        } catch (Exception e) {
            return null;
        }

        if (Double.isNaN(logLik) || Double.isInfinite(logLik)) {
            return null;
        }

        int p = cov.getDimension();
        int k = p + numEdges;   // p error variances + one parameter per (directed or bidirected) edge.
        int n = cov.getSampleSize();

        return 2.0 * logLik - scoreCheckPenaltyDiscount * k * Math.log(n);
    }

    /**
     * Resolves the covariance matrix for RICF: the explicitly set one if present, else the test's covariance, else
     * a covariance computed from the test's (continuous) data set. Caches the result. Returns null if none of these
     * is available; the score check then degrades to a pass.
     */
    private @Nullable ICovarianceMatrix resolveCovariance() {
        if (covarianceMatrix != null) {
            return covarianceMatrix;
        }

        try {
            covarianceMatrix = independenceTest.getCov();
            if (covarianceMatrix != null) {
                return covarianceMatrix;
            }
        } catch (Exception ignored) {
            // Test doesn't expose a covariance; fall through to the data.
        }

        try {
            DataModel data = independenceTest.getData();
            if (data instanceof ICovarianceMatrix icm) {
                covarianceMatrix = icm;
                return covarianceMatrix;
            }
            if (data instanceof DataSet ds && ds.isContinuous()) {
                covarianceMatrix = new CovarianceMatrix(ds);
                return covarianceMatrix;
            }
        } catch (Exception ignored) {
            // No data available either.
        }

        return null;
    }

    /**
     * Builds a fresh FCI orientation engine (complete rules, R4 on) bound to the given sepset map.
     * (Verbatim from StarFciGuaranteePag.)
     */
    private FciOrient buildFciOrient(SepsetMap sepsetMap) {
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
        fciOrient.setVerbose(false);
        return fciOrient;
    }

    /**
     * The legality certificate: legal PAG modulo background knowledge. (Verbatim from StarFciGuaranteePag.)
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
     * Refines a graph with background knowledge and closes under the complete FCI final rules.
     * (Verbatim from StarFciGuaranteePag.)
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

    // ---------------- setters/getters ----------------

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
     * @param completeRuleSetUsed set to true if Zhang's complete rule set should be used, false if only R1-R4 (the
     *                            rule set of the original FCI) should be used. True by default.
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
     * Sets whether selection bias should be excluded during the search process.
     *
     * @param excludeSelectionBias A boolean indicating whether to exclude selection bias (true) or not (false).
     */
    public void setExcludeSelectionBias(boolean excludeSelectionBias) {
        this.excludeSelectionBias = excludeSelectionBias;
    }

    /**
     * Sets whether each candidate removal is gated on per-step PAG legality (true, the default) or handled by the
     * original greedy *-FCI machinery with a post-hoc repair (false). The score check applies only when gating is
     * on.
     *
     * @param doLegalityGating Whether to gate each removal.
     */
    public void setDoLegalityGating(boolean doLegalityGating) {
        this.doLegalityGating = doLegalityGating;
    }

    /**
     * Whether to do the possible d-sep step.
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
     * Sets the parallel processing mode.
     *
     * @param parallelized a boolean value indicating whether to enable (true) or disable (false) parallel
     *                     processing.
     */
    public void setParallelized(boolean parallelized) {
        this.parallelized = parallelized;
    }

    /**
     * Sets whether to use the maxP criterion during the sepset search.
     *
     * @param useMaxP A boolean indicating whether the maxP criterion should be applied (true) or not (false).
     */
    public void setUseMaxP(boolean useMaxP) {
        this.useMaxP = useMaxP;
    }

    /**
     * Sets the number of times to restart the BOSS search.
     *
     * @param numStarts The number of times to restart the search.
     */
    public void setNumStarts(int numStarts) {
        this.numStarts = numStarts;
    }

    /**
     * Sets whether the BES should be used in BOSS.
     *
     * @param useBes True if the BES should be used, false otherwise.
     */
    public void setBossUseBes(boolean useBes) {
        this.bossUseBes = useBes;
    }

    /**
     * Sets the number of threads to use in BOSS.
     *
     * @param numThreads The number of threads to use. Must be at least 1.
     */
    public void setNumThreads(int numThreads) {
        if (numThreads < 1) {
            throw new IllegalArgumentException("Number of threads must be at least 1: " + numThreads);
        }
        this.numThreads = numThreads;
    }

    /**
     * Sets whether the RICF-BIC score check is applied to gated commitments. When false this class reduces exactly
     * to Bfci, which is useful for A/B comparisons within the same wrapper. Default true.
     *
     * @param useScoreCheck Whether to apply the score check.
     */
    public void setUseScoreCheck(boolean useScoreCheck) {
        this.useScoreCheck = useScoreCheck;
    }

    /**
     * Sets the penalty discount c in the MAG BIC, bic = 2 * logLik - c * k * ln(n). Default 1 (classical BIC).
     * Larger values make the gate MORE willing to remove edges (the parameter saving of a removal counts for
     * more); smaller values make it more conservative.
     *
     * @param scoreCheckPenaltyDiscount the penalty discount; must be positive.
     */
    public void setScoreCheckPenaltyDiscount(double scoreCheckPenaltyDiscount) {
        if (scoreCheckPenaltyDiscount <= 0) {
            throw new IllegalArgumentException("Score-check penalty discount must be positive: "
                    + scoreCheckPenaltyDiscount);
        }
        this.scoreCheckPenaltyDiscount = scoreCheckPenaltyDiscount;
    }

    /**
     * Sets the covariance matrix used by the RICF score check. If not set, the covariance is resolved from the
     * independence test (its covariance if it exposes one, else a covariance computed from its continuous data
     * set).
     *
     * @param covarianceMatrix the covariance matrix; may be null to force resolution from the test.
     */
    public void setCovarianceMatrix(ICovarianceMatrix covarianceMatrix) {
        this.covarianceMatrix = covarianceMatrix;
    }
}
