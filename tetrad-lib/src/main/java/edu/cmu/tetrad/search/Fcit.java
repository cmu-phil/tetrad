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
import edu.cmu.tetrad.search.score.GraphScore;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.test.CachedIndependenceQueries;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.search.utils.*;
import edu.cmu.tetrad.util.MillisecondTimes;
import edu.cmu.tetrad.util.SublistGenerator;
import edu.cmu.tetrad.util.TetradLogger;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * The FCI Targeted Testing (FCIT) algorithm: learns a PAG from observational data
 * with latent variables. BOSS/GRaSP supplies the initial CPDAG; its DAG member is
 * projected to the initial Markov PAG G_0 (dagToPag); a testing phase then removes
 * spurious edges one at a time, retaining a removal only when the from-scratch
 * reorientation passes the PAG legality check, and closes with a saturating pass.
 *
 * <p><b>Conformance to the RB paper's theory.</b> This implementation realizes the
 * FCIT (PAG -&gt; PAG) procedure of the Algorithmic Consequences section:</p>
 *
 * <ol>
 * <li><b>Deletion step (Def. rb-step).</b> Each candidate removal is proposed by a
 *     single shared sweep ({@link #sweepForSepset}): recursive blocking supplies a
 *     base blocking set; a not-followed (NF) outer layer enumerates subsets of the
 *     ambiguous (circle-bearing) members of that set; an inner layer enumerates
 *     removals over the whole base (common neighbors first) and additions over
 *     common neighbors omitted from the base. Every candidate is adjudicated by the
 *     independence test; the graph view only proposes, never decides.</li>
 *
 * <li><b>Blind proposal view (Rem. blind-proposal).</b> When the sweep against the
 *     live, marked PAG finds nothing, the same sweep is re-run against the bare
 *     skeleton (every endpoint a circle), on which no triple is a collider and
 *     nothing counts as pre-blocked, so the proposal is a function of adj(H)
 *     alone. This is the skeleton-relative core the progress lemma's coverage
 *     argument leans on; it closes the reach gap in which a wrong interim mark
 *     off the common neighborhood hides the separator.</li>
 *
 * <li><b>Recorded, not live (P1/P2).</b> Committed separators live in
 *     {@link #sepsets}; separators recorded by R4 during a candidate reorientation
 *     are written to a per-candidate scratch copy of that map, adopted wholesale on
 *     commit and discarded wholesale on revert, so no entry recorded against a
 *     rejected state survives. Unrecorded R4 endpoint pairs are additionally
 *     computed against the frozen initial PAG G_0 (setSepsetGraph), never the live
 *     mid-reorientation graph.</li>
 *
 * <li><b>Saturating pass (Cor. saturation / Thm. algorithm, phase 2).</b> When no
 *     single-edge removal commits, every still-adjacent pair whose separator has
 *     been test-confirmed (the {@link #foundSepsets} record, which survives
 *     reverts) is deleted simultaneously and the graph reoriented once from
 *     scratch. Under the oracle and out-of-B coverage the result is the legal
 *     Markov PAG G*; the pass is therefore accepted when legal, and a loud
 *     diagnostic is emitted when it is not (finite-sample test error, coverage
 *     failure, or a bug -- exactly what an oracle harness should catch).</li>
 *
 * <li><b>Detection and discharge.</b> The final scan over discriminating-path legs
 *     confirms spuriousness by the same sweep plus the test (never by a graphical
 *     proposal alone); a newly confirmed leg is recorded and fed back to the
 *     removal/saturation machinery. Indeterminate outcomes (deadline expiries,
 *     cap truncations) are propagated into the final report rather than the graph
 *     being certified phantom-free (Rem. three-valued / Rem. one-copy).</li>
 *
 * <li><b>Determinism.</b> All candidate lists are name-sorted, edges are scanned in
 *     a canonical order, and only the winning (lowest-index) speculative search of
 *     each parallel lookahead phase records its separator, so the recorded
 *     separator for a pair is a deterministic function of the committed history --
 *     the one-canonical-separator convention.</li>
 * </ol>
 *
 * @author josephramsey
 */
public final class Fcit implements IGraphSearch {

    /**
     * Canonical node order for candidate enumeration (Def. rb-step's
     * "lexicographic over name-sorted nodes").
     */
    private static final Comparator<Node> NODE_ORDER = Comparator.comparing(Node::getName);

    /**
     * Whether the constructor wraps the supplied test in
     * {@link CachedIndependenceQueries}. Static because it must be decided
     * before construction. Set false for tests with internal randomness where
     * repeated-query consistency is not wanted.
     */
    public static boolean useTestCache = true;

    /**
     * Canonical edge order for the sweep scan and the saturating pass.
     */
    private static final Comparator<Edge> EDGE_ORDER =
            Comparator.comparing(Fcit::minName).thenComparing(Fcit::maxName);

    /**
     * The independence test.
     */
    private final IndependenceTest test;
    /**
     * The score.
     */
    private final Score score;
    /**
     * The list of selection nodes in the graph.
     */
    private final List<Node> selection;
    /**
     * Counts conditional independence checks, broken down by call site, so we can
     * measure how many tests the recursive-blocking optimization saves.
     */
    private final IndependenceCheckCounter checkCounter = new IndependenceCheckCounter();
    /**
     * Separators discovered for a pair during any sweep, kept across rounds and
     * across reverts. Distinct from {@link #sepsets}, which records only committed
     * (legal-PAG) separations. Because X _||_ Y | S is a property of the data, not
     * the current PAG, a set that separated a pair once still separates it;
     * reusing it keeps a pair's recorded sepset stable across rounds instead of
     * being re-derived (and possibly differing) each time the edge is reconsidered
     * after a reverted removal. This is also the confirmed-spurious record the
     * saturating pass deletes from.
     *
     * <p>Concurrency: written only from sequential code (the winner of a parallel
     * lookahead phase, and the sequential detection scan); read concurrently
     * during the parallel phase, during which there are no writes.</p>
     */
    private final Map<Set<Node>, Set<Node>> foundSepsets = new HashMap<>();
    /**
     * Monotone counter bumped on every graph-changing commit. Sweep results are
     * a function of the graph, so a definite not-found remains valid until this
     * changes.
     */
    private volatile long graphVersion = 0L;
    /**
     * Pairs whose two-view sweep exhausted without a separator, and the graph
     * version at which that happened. Consulted before re-sweeping.
     * Concurrent because {@code findIndependenceCheckRecursive} runs inside the
     * parallel lookahead.
     */
    private final Map<Set<Node>, Long> sweepFailedAt = new java.util.concurrent.ConcurrentHashMap<>();
    /**
     * Committed separation sets: one entry per pair whose edge was removed by a
     * committed (legal) deletion, plus R4-time separators recorded along the
     * committed sequence. Reassigned wholesale on commit (scratch adoption); see
     * {@link #tryToModifyGraph} and {@link #saturatingPass}.
     */
    private SepsetMap sepsets = new SepsetMap();
    /**
     * Memo of candidate states already found ILLEGAL, keyed by
     * (skeleton signature, sepset signature). The from-scratch reorientation and
     * the legality check are together a deterministic function of that pair
     * (knowledge, seed colliders, and the test being fixed for the run), so a
     * state that failed once fails again. Only failures are memoized: a legal
     * candidate is committed at once and its state never recurs, and skipping
     * its reorientation would discard the oriented graph we need.
     *
     * <p>Diagnostic value beyond speed: if reorientation were not in fact
     * deterministic -- residual hash-order dependence in FciOrient, say -- this
     * memo freezes whichever verdict arrived first, so a run that differs with
     * the memo on and off is evidence of exactly that bug. The
     * {@code auditLegalityMemo} flag turns the memo into such an audit.</p>
     */
    private final Map<String, Boolean> illegalStates = new HashMap<>();
    /**
     * When true, the legality memo is consulted but not trusted: every hit is
     * recomputed and a mismatch is logged. Use to verify that reorientation is
     * deterministic; leave false in production.
     */
    private boolean auditLegalityMemo = false;
    /**
     * The background knowledge.
     */
    private Knowledge knowledge = new Knowledge();
    /**
     * The algorithm to use to get the initial CPDAG.
     */
    private START_WITH startWith = START_WITH.BOSS;
    /**
     * The number of starts for GRaSP/BOSS.
     */
    private int numStarts = 1;
    /**
     * Flag indicating whether to use data order.
     */
    private boolean useDataOrder = true;
    /**
     * Whether BES should be used inside BOSS.
     */
    private boolean useBes = false;
    /**
     * True iff verbose output should be printed.
     */
    private boolean superVerbose = false;
    /**
     * The final-orientation machinery (R0-R10 with the test-based R0/R4 strategy).
     */
    private FciOrient fciOrient = null;
    /**
     * The R0/R4 strategy in use, held so its sepset map can be repointed at the
     * per-candidate scratch map and back (the P1 discipline).
     */
    private R0R4StrategyTestBased r0r4Strategy = null;
    /**
     * Unshielded colliders of the seed CPDAG's PAG (the sound seed colliders of
     * the reachability theory), recalled into every from-scratch reorientation.
     */
    private Set<Triple> initialColliders;
    /**
     * Whether the Zhang complete rule set should be used.
     */
    private boolean completeRuleSetUsed = true;
    /**
     * The depth of search (maximum conditioning-set size), -1 unlimited.
     */
    private int depth = -1;
    /**
     * True just in case good and restored changes are printed.
     */
    private boolean verbose = false;
    /**
     * The evolving PAG.
     */
    private @NotNull Graph pag = new EdgeListGraph();
    /**
     * Whether the graph replication (time-lag) wrapper is active.
     */
    private boolean replicatingGraph = false;
    /**
     * Whether selection bias handling is excluded.
     */
    private boolean excludeSelectionBias = false;
    /**
     * Maximum recursion depth for recursive blocking. TODO: make a parameter.
     */
    private int recursiveDepth = -1;
    /**
     * BFS-shell radius for recursive blocking's conditioning pool (-1 unlimited).
     */
    private int rbRadius = -1;
    /**
     * Maximum discriminating-path length (-1 unlimited).
     */
    private int maxDiscriminatingPathLength = -1;    /**
     * Per-edge (and per-pair, in detection) search budget in ms, -1 unlimited.
     * NOTE: wall-clock, hence nondeterministic across runs under varying load.
     * For reproducible experiments prefer {@link #maxTestsPerPair}.
     */
    private long timeout = -1L;
    /**
     * Cap on the size of not-followed subsets in the sweep's outer layer, -1
     * unlimited. The outer layer is a powerset over the ambiguous nodes, so this
     * is the strongest single lever: 2^|A| RB calls become O(|A|^k). Withholding
     * many nodes at once is rarely what finds a separator, so small values (2-3)
     * retain most of the reach at a fraction of the cost -- but the family is no
     * longer the one Def. rb-step specifies, and coverage arguments over
     * labelings of B no longer apply.
     */
    private int sweepNfMaxSize = -1;
    /**
     * Cap on the size of addition subsets in the sweep's inner layer, -1
     * unlimited.
     */
    private int sweepAddMaxSize = -1;
    /**
     * Cap on the size of removal subsets in the sweep's inner layer, -1
     * unlimited. Removals are enumerated common-neighbors-first, so a cap keeps
     * the coverage-bearing candidates and discards the rest.
     */
    private int sweepRemoveMaxSize = -1;
    /**
     * Whether the bare-skeleton (blind) proposal view is searched when the
     * marked view finds nothing. Disabling halves sweep cost and forfeits
     * Rem. blind-proposal: separators hidden by a wrong interim mark become
     * unreachable except through the completion layer.
     */
    private boolean useBlindView = true;
    /**
     * Deterministic per-pair budget: maximum number of independence tests
     * executed across BOTH views for one pair, -1 unlimited. Unlike
     * {@link #timeout} this is reproducible, so it is the budget to use in
     * enumeration harnesses. Exhausting it yields an indeterminate sweep, not a
     * not-found verdict.
     */
    private long maxTestsPerPair = -1L;
    /**
     * How much of the completion layer to run (Prop. completion).
     */
    private CompletionPolicy completionPolicy = CompletionPolicy.FULL;
    /**
     * Cap on conditioning-set size in the completion layer, -1 unlimited (in
     * which case {@link #depth} still applies). The completion enumeration is by
     * increasing size, so a cap here is a clean "separators up to size k only"
     * restriction: sound, incomplete, and the incompleteness is legible.
     */
    private int completionMaxSubsetSize = -1;
    /**
     * Whether the final discriminating-path scan runs. OFF skips detection and
     * the discharge it feeds, so confirmed-spurious legs that neither
     * single-edge removal nor saturation caught are neither reported nor
     * removed.
     */
    private boolean detectionEnabled = true;
    /**
     * Before invoking recursive blocking for a pair, test subsets of the pair's
     * common neighborhood by increasing size up to this cap; -1 disables the
     * pass (the default, which preserves the full algorithm's trajectory
     * exactly). Most separators are small and lie in the common neighborhood,
     * so a cap of 2 or 3 answers the majority of pairs in a handful of tests
     * and leaves recursive blocking for the residue. NOT output-identical: the
     * separators recorded are smaller, so orientations and hence trajectories
     * change.
     */
    private int quickSubsetDepth = -1;
    /**
     * Whether the recursive-blocking sweep runs at all. When false, no separator
     * is proposed by recursive blocking; the completion layer
     * (Prop. completion) becomes the sole source of separators, feeding
     * {@code foundSepsets}, from which the legality-gated removal loop and the
     * saturating pass then work. The result is FCI-style subset enumeration with
     * a legality gate: complete at the oracle, and at small p often cheaper than
     * the sweep it replaces, since a pair admits at most 2^(p-2) candidate sets
     * while the sweep runs a powerset of not-followed sets with a recursive
     * blocking call apiece. At large p the trade inverts sharply, which is why
     * the sweep is the default. Setting this false with
     * {@code completionPolicy == OFF} leaves no separator-finding machinery at
     * all; the search will remove nothing.
     */
    private boolean useRecursiveBlockingSweep = true;

    /**
     * FCIT constructor.
     *
     * @param test  The IndependenceTest object to be used for testing independence between variables.
     * @param score The Score object to be used for scoring DAGs.
     * @throws NullPointerException if the test or score is null.
     */
    public Fcit(IndependenceTest test, Score score) {
        if (test == null) {
            throw new NullPointerException();
        }

        if (score == null) {
            throw new NullPointerException();
        }

        // Wrap in the query cache unless the caller already did. The sweep
        // retests the same (x, y, S) across views, rounds, and the completion
        // layer; the cache is output-identical for a deterministic test.
        // NOTE: for a test with internal randomness (e.g. RCIT) this is NOT a
        // no-op -- it makes repeated queries consistent, which changes results.
        this.test = (useTestCache && !(test instanceof CachedIndependenceQueries))
                ? new CachedIndependenceQueries(test)
                : test;
        this.score = score;

        this.selection = this.test.getVariables().stream()
                .filter(node -> node.getNodeType() == NodeType.SELECTION).toList();

        test.setVerbose(superVerbose);

        if (test instanceof MsepTest) {
            this.startWith = START_WITH.GRASP;
        }
    }

    private static String minName(Edge e) {
        String a = e.getNode1().getName(), b = e.getNode2().getName();
        return a.compareTo(b) <= 0 ? a : b;
    }

    private static String maxName(Edge e) {
        String a = e.getNode1().getName(), b = e.getNode2().getName();
        return a.compareTo(b) <= 0 ? b : a;
    }

    /**
     * Identifies known unshielded colliders from the PAG of the seed CPDAG/DAG.
     * These are the sound seed colliders of the reachability theory (the GFCI
     * justification), recalled into every from-scratch reorientation.
     */
    private static Set<Triple> noteInitialColliders(List<Node> best, Graph graph) {
        Set<Triple> initialColliders = new HashSet<>();

        for (Node b : best) {
            var adj = graph.getAdjacentNodes(b);

            for (int i = 0; i < adj.size(); i++) {
                for (int j = i + 1; j < adj.size(); j++) {
                    Node x = adj.get(i);
                    Node y = adj.get(j);

                    if (graph.isDefCollider(x, b, y) && !graph.isAdjacentTo(x, y)) {
                        initialColliders.add(new Triple(x, b, y));
                    }
                }
            }
        }

        return initialColliders;
    }

    /**
     * The from-scratch reorientation of Def. rb-step: every endpoint reset to a
     * circle, the seed colliders copied in, R0 applied from the recorded
     * separators for every pair now non-adjacent, and R1-R10 applied to closure
     * (R4 reading recorded separators via the strategy's sepset map).
     */
    private static void redoGfciOrientation(Graph pag, FciOrient fciOrient, Knowledge knowledge,
                                            Set<Triple> initialColliders, SepsetMap sepsets, boolean excludeSelectionBias,
                                            boolean superVerbose) {
        GraphUtils.reorientWithCircles(pag, superVerbose);
        GraphUtils.recallInitialColliders(pag, initialColliders, knowledge);
        adjustForExtraSepsets(sepsets, pag);
        fciOrient.finalOrientation(pag, excludeSelectionBias);
    }

    /**
     * R0 from the recorded separators: for every recorded pair now non-adjacent,
     * each common neighbor absent from the recorded set is oriented as a collider.
     * The recorded set is the one whose independence test licensed the deletion,
     * so the verdict is by membership -- never re-derived from the current graph.
     */
    private static void adjustForExtraSepsets(SepsetMap sepsets, Graph pag) {
        for (Set<Node> edge : sepsets.keySet()) {
            List<Node> arr = new ArrayList<>(edge);
            if (arr.size() != 2) continue;

            Node x = arr.get(0);
            Node y = arr.get(1);

            if (pag.isAdjacentTo(x, y)) {
                continue;
            }

            Set<Node> sep = sepsets.get(x, y);
            if (sep == null) {
                continue;
            }

            List<Node> common = pag.getAdjacentNodes(x);
            common.retainAll(pag.getAdjacentNodes(y));

            for (Node node : common) {
                if (!sep.contains(node)) {
                    if (!pag.isDefCollider(x, node, y)) {
                        pag.setEndpoint(x, node, Endpoint.ARROW);
                        pag.setEndpoint(y, node, Endpoint.ARROW);
                    }
                }
            }
        }
    }

    /**
     * All-circles copy of {@code g}: the bare-skeleton proposal view of
     * Rem. blind-proposal. No triple is a collider on this view, so nothing
     * counts as pre-blocked and the sweep's proposals depend on adj(H) alone.
     * The view proposes only; every candidate is still test-confirmed.
     */
    private static Graph blindView(Graph g) {
        Graph blind = new EdgeListGraph(g);
        for (Edge e : new ArrayList<>(blind.getEdges())) {
            blind.setEndpoint(e.getNode1(), e.getNode2(), Endpoint.CIRCLE);
            blind.setEndpoint(e.getNode2(), e.getNode1(), Endpoint.CIRCLE);
        }
        return blind;
    }

    /**
     * Canonical signature of a graph's skeleton: name-sorted adjacencies. Two
     * graphs with the same signature reorient identically from circles.
     */
    private static String skeletonSignature(Graph g) {
        List<String> pairs = new ArrayList<>();
        for (Edge e : g.getEdges()) {
            String a = e.getNode1().getName(), b = e.getNode2().getName();
            pairs.add(a.compareTo(b) <= 0 ? a + "-" + b : b + "-" + a);
        }
        Collections.sort(pairs);
        return String.join(",", pairs);
    }

    /**
     * Canonical signature of a sepset map: name-sorted pairs, each with its
     * name-sorted separator.
     */
    private static String sepsetSignature(SepsetMap m) {
        List<String> entries = new ArrayList<>();
        for (Set<Node> key : m.keySet()) {
            List<Node> pr = new ArrayList<>(key);
            if (pr.size() != 2) continue;
            Set<Node> s = m.get(pr.get(0), pr.get(1));
            if (s == null) continue;
            String a = pr.get(0).getName(), b = pr.get(1).getName();
            String pair = a.compareTo(b) <= 0 ? a + "-" + b : b + "-" + a;
            List<String> members = new ArrayList<>();
            for (Node n : s) members.add(n.getName());
            Collections.sort(members);
            entries.add(pair + ":" + String.join("+", members));
        }
        Collections.sort(entries);
        return String.join(";", entries);
    }

    /**
     * Deep-enough copy of a SepsetMap for the scratch/commit discipline: pair
     * keys and value sets are copied; nodes are shared.
     */
    private static SepsetMap copySepsetMap(SepsetMap src) {
        SepsetMap out = new SepsetMap();
        for (Set<Node> key : src.keySet()) {
            List<Node> pr = new ArrayList<>(key);
            if (pr.size() != 2) continue;
            Set<Node> s = src.get(pr.get(0), pr.get(1));
            if (s != null) {
                out.set(pr.get(0), pr.get(1), new HashSet<>(s));
            }
        }
        return out;
    }

    @Override
    public IndependenceTest getTest() {
        return test;
    }

    /**
     * Run the search and return a PAG.
     *
     * @return The PAG.
     * @throws InterruptedException if any
     */
    public Graph search() throws InterruptedException {
        List<Node> nodes;

        if (score != null) {
            nodes = new ArrayList<>(score.getVariables());
        } else {
            nodes = new ArrayList<>(test.getVariables());
        }

        TetradLogger.getInstance().log("===Starting FCIT===");

        R0R4StrategyTestBased strategy = new R0R4StrategyTestBased(test, timeout);
        strategy.setSepsetMap(sepsets);
        strategy.setVerbose(superVerbose);
        strategy.setBlockingType(R0R4StrategyTestBased.BlockingType.RECURSIVE);
        strategy.setDepth(depth);
        this.r0r4Strategy = strategy;

        fciOrient = new FciOrient(strategy);
        fciOrient.setVerbose(superVerbose);
        fciOrient.setParallel(false); // We're doing parallel lookahead.
        fciOrient.setCompleteRuleSetUsed(completeRuleSetUsed);
        fciOrient.setRecursiveDepth(recursiveDepth);
        fciOrient.setMaxDiscriminatingPathLength(maxDiscriminatingPathLength);
        fciOrient.setKnowledge(knowledge);

        Graph dag;
        List<Node> best;
        long start1 = System.currentTimeMillis();

        if (startWith != START_WITH.COMPLETE_GRAPH) {
            if (startWith == START_WITH.BOSS) {

                if (superVerbose) {
                    TetradLogger.getInstance().log("Running BOSS...");
                }

                if (this.score == null) {
                    throw new IllegalArgumentException("For BOSS a non-null score is expected.");
                }

                long start = MillisecondTimes.wallTimeMillis();

                PermutationSearch alg = getBossSearch();
                alg.setKnowledge(knowledge);
                alg.setReplicatingGraph(this.replicatingGraph);

                dag = alg.search(false);
                best = dag.paths().getValidOrder(dag.getNodes(), true);

                long stop = MillisecondTimes.wallTimeMillis();

                if (superVerbose) {
                    TetradLogger.getInstance().log("BOSS took " + (stop - start) + " ms.");
                    TetradLogger.getInstance().log("Initializing PAG to BOSS CPDAG.");
                    TetradLogger.getInstance().log("Initializing scorer with BOSS best order.");
                }
            } else if (startWith == START_WITH.GRASP) {
                // We need the GRaSP option here so that we can run FCIT from Oracle.

                if (superVerbose) {
                    TetradLogger.getInstance().log("Running GRaSP...");
                }

                long start = MillisecondTimes.wallTimeMillis();

                Grasp grasp = getGraspSearch();
                grasp.setReplicatingGraph(this.replicatingGraph);
                best = grasp.bestOrder(nodes);
                dag = grasp.getGraph(false);

                long stop = MillisecondTimes.wallTimeMillis();

                if (superVerbose) {
                    TetradLogger.getInstance().log("GRaSP took " + (stop - start) + " ms.");
                    TetradLogger.getInstance().log("Initializing PAG to GRaSP CPDAG.");
                    TetradLogger.getInstance().log("Initializing scorer with GRaSP best order.");
                }
            } else if (startWith == START_WITH.SP) {

                if (superVerbose) {
                    TetradLogger.getInstance().log("Running SP...");
                }

                long start = MillisecondTimes.wallTimeMillis();

                if (this.score == null) {
                    throw new IllegalArgumentException("For SP a non-null score is expected.");
                }

                Sp subAlg = new Sp(this.score);
                PermutationSearch alg = new PermutationSearch(subAlg);
                alg.setKnowledge(this.knowledge);
                alg.setReplicatingGraph(this.replicatingGraph);

                dag = alg.search(false);
                best = dag.paths().getValidOrder(dag.getNodes(), true);

                long stop = MillisecondTimes.wallTimeMillis();

                if (superVerbose) {
                    TetradLogger.getInstance().log("SP took " + (stop - start) + " ms.");
                    TetradLogger.getInstance().log("Initializing PAG to SP CPDAG.");
                    TetradLogger.getInstance().log("Initializing scorer with SP best order.");
                }
            } else {
                throw new IllegalArgumentException("That startWith option has not been configured: " + startWith);
            }
        } else {
            dag = GraphUtils.completeGraph(new EdgeListGraph(nodes));
            GraphUtils.reorientWithCircles(dag, superVerbose);
            best = dag.getNodes();
        }

        if (superVerbose) {
            TetradLogger.getInstance().log("Best order: " + best);
        }

        long stop1 = System.currentTimeMillis();

        long start2 = System.currentTimeMillis();

        TeyssierScorer scorer = null;

        if (score != null) {
            scorer = new TeyssierScorer(test, score);
            scorer.score(best);
            scorer.setKnowledge(knowledge);
            scorer.setUseScore(!(score instanceof GraphScore));
            scorer.setUseRaskuttiUhler(score instanceof GraphScore);
            scorer.bookmark();
        }

        if (scorer != null) {
            scorer.score(best);
        }

        if (superVerbose) {
            TetradLogger.getInstance().log("Copying unshielded colliders from CPDAG.");
        }

        // We make all latent variables at this point measured for the duration of
        // the procedure so that the latent structure search will work.
        List<Node> latents = new ArrayList<>();
        for (Node node : dag.getNodes()) {
            if (node.getNodeType() == NodeType.LATENT) {
                latents.add(node);
                node.setNodeType(NodeType.MEASURED);
            }
        }

        // The main procedure. G_0 = PAG of the seed DAG's MAG-equivalence class.
        this.pag = GraphTransforms.dagToPag(dag, knowledge, excludeSelectionBias, recursiveDepth);

        if (replicatingGraph) {
            this.pag = new ReplicatingGraph(pag, new LagReplicationPolicy());
        }

        this.initialColliders = noteInitialColliders(pag.getNodes(), pag);

        // P1 ("recorded, not live"): unrecorded R4 endpoint separators are
        // computed against the frozen initial Markov PAG G_0, never the live
        // mid-reorientation graph. Such pairs are exactly the ones non-adjacent
        // from the CPDAG on; deleted pairs always carry a recorded sepset.
        strategy.setSepsetGraph(new EdgeListGraph(this.pag));

        // ---------------------------------------------------------------------
        // Thm. algorithm: phase 1 (legality-gated single-edge deletions) to
        // fixpoint; then phase 2 (one saturating step over all confirmed
        // spurious edges); then detection-with-confirmation over discriminating
        // path legs, feeding any newly confirmed leg back into phase 1.
        // Terminates: each productive iteration removes an edge or records a new
        // confirmed pair, both bounded.
        // ---------------------------------------------------------------------
        int round = 0;
        boolean progressed;
        NongenuineScan lastScan = null;

        do {
            progressed = false;

            do {
                TetradLogger.getInstance().log("\nRound: " + (++round));
            } while (removeEdgesRecursively(excludeSelectionBias, initialColliders));

            if (saturatingPass(excludeSelectionBias, initialColliders)) {
                progressed = true;
                lastScan = null;
                continue;
            }

            if (pdsCompletionPass()) {
                progressed = true;
                lastScan = null;
                continue;
            }

            if (!detectionEnabled) {
                lastScan = new NongenuineScan(null, false, false);
                break;
            }

            lastScan = scanNongenuineLegs();
            progressed = lastScan.newEvidence();
        } while (progressed);

        if (lastScan == null) {
            lastScan = detectionEnabled
                    ? scanNongenuineLegs()
                    : new NongenuineScan(null, false, false);
        }

        if (superVerbose) {
            TetradLogger.getInstance().log("Finished all rounds and the saturating pass.");
        }

        long stop2 = System.currentTimeMillis();

        // Revert nodes made latent to latent.
        for (Node node : latents) {
            node.setNodeType(NodeType.LATENT);
        }

        if (lastScan.edge() != null) {
            TetradLogger.getInstance().log("\nNon-genuine DDPs detected: a discriminating-path leg is "
                    + "test-confirmed spurious but could not be discharged (single-edge removal and the "
                    + "saturating pass both refused). First such edge: " + lastScan.edge());
        } else if (lastScan.indeterminate()) {
            TetradLogger.getInstance().log(
                    "\nDetection inconclusive: a blocking search timed out or was truncated before a verdict. "
                            + "No non-genuine DDP was confirmed, but the graph cannot be certified phantom-free.");
        } else {
            TetradLogger.getInstance().log("\nNo non-genuine DDPs detected in the final graph.");
        }

        TetradLogger.getInstance().log("\nFCIT finished.");
        TetradLogger.getInstance().log("BOSS/GRaSP time: " + (stop1 - start1) + " ms.");
        TetradLogger.getInstance().log("Collider orientation and edge removal time: " + (stop2 - start2) + " ms.");
        TetradLogger.getInstance().log("Total time: " + (stop2 - start1) + " ms.");
        TetradLogger.getInstance().log(checkCounter.report());

        CachedIndependenceQueries cache = findCache();
        if (cache != null) {
            TetradLogger.getInstance().log(cache.cacheReport());
        }

        return GraphUtils.replaceNodes(this.pag, nodes);
    }

    // -------------------------------------------------------------------------
    // Phase 1: legality-gated single-edge removals with parallel lookahead.
    // -------------------------------------------------------------------------

    /**
     * One sweep over the edges of the current PAG, attempting removals. Each
     * phase scans the tail of a canonical edge list in parallel; the
     * lowest-index removable edge (deterministic under findFirst on an ordered
     * stream) is committed against the live PAG, gated on the legality check.
     *
     * @return true if at least one edge was removed, false otherwise
     */
    private boolean removeEdgesRecursively(boolean excludeSelectionBias, Set<Triple> unshieldedTriples) {
        if (superVerbose) {
            TetradLogger.getInstance().log("Removing extra edges (recursive-blocking sweep).");
        }

        boolean changedThisSweep = false;

        // Canonical snapshot of the edges for this sweep. `from` is the scan
        // position; we never go back before it, so each edge is searched at most
        // once per sweep.
        List<Edge> edgeList = new ArrayList<>(this.pag.getEdges());
        edgeList.sort(EDGE_ORDER);
        int from = 0;

        while (from < edgeList.size()) {
            final int start = from;

            // One blind proposal view per phase; the PAG is not mutated during
            // the parallel scan, so concurrent reads of both views are safe.
            final Graph blind = blindView(this.pag);

            // Parallel search over the tail [start, end). findFirst on an ordered
            // parallel stream returns the LOWEST-index removable edge
            // deterministically, independent of which thread finishes first.
            java.util.Optional<RemovalHit> hit =
                    java.util.stream.IntStream.range(start, edgeList.size())
                            .parallel()
                            .mapToObj(i -> {
                                Edge e = edgeList.get(i);
                                Node x = e.getNode1();
                                Node y = e.getNode2();

                                // Eligibility filters (read-only).
                                if (!this.pag.isAdjacentTo(x, y)) return null;
                                if (knowledge != null
                                        && (knowledge.isRequired(x.getName(), y.getName())
                                        || knowledge.isRequired(y.getName(), x.getName()))) return null;

                                try {
                                    IndependenceCheck check = findIndependenceCheckRecursive(e, blind);
                                    if (check == null) return null;
                                    return new RemovalHit(i, e, check.cond());
                                } catch (InterruptedException ie) {
                                    Thread.currentThread().interrupt();
                                    throw new RuntimeException(ie);
                                }
                            })
                            .filter(Objects::nonNull)
                            .findFirst();

            if (hit.isEmpty()) {
                break;  // no removable edge in the tail -- sweep complete
            }

            RemovalHit h = hit.get();
            Node x = h.edge().getNode1();
            Node y = h.edge().getNode2();

            // Winner-only recording (determinism): only the committed-to hit's
            // separator enters the cross-round record. Losing speculative
            // searches are discarded, so the recorded separator for a pair never
            // depends on thread scheduling. The record survives a revert: the
            // independence is a data fact, and the saturating pass deletes from
            // exactly this record.
            foundSepsets.putIfAbsent(Set.of(x, y), h.cond());

            boolean didChange = tryToModifyGraph(x, y, h.cond(), "recursive",
                    excludeSelectionBias, unshieldedTriples);

            if (didChange) {
                changedThisSweep = true;
            }
            // Either way, resume scanning AFTER this edge. On a commit the PAG
            // changed and the tail is re-searched against the new graph; on a
            // revert the PAG is unchanged and the tail search is consistent.
            from = h.index() + 1;
        }

        return changedThisSweep;
    }

    /**
     * The full separator search for one edge: committed and cross-round fast
     * paths, then the shared sweep against the live view, then against the
     * blind (bare-skeleton) view (Rem. blind-proposal).
     */
    private IndependenceCheck findIndependenceCheckRecursive(Edge edge, Graph blind) throws InterruptedException {
        final Node x = edge.getNode1();
        final Node y = edge.getNode2();

        // A committed sepset for a still-adjacent pair should be impossible
        // (pairs never re-adjoin, and scratch discipline discards reverted
        // recordings), but read it back if present rather than re-deriving.
        Set<Node> known = sepsets.get(x, y);
        if (known != null) {
            return new IndependenceCheck(edge, known);
        }

        // Reuse a separator already found for this pair in an earlier sweep.
        // The independence is a data fact, invariant across rounds; re-searching
        // the (evolved) PAG would only risk returning a *different* valid set.
        // tryToModifyGraph still judges PAG legality; if it reverts, the edge is
        Set<Node> cached = foundSepsets.get(Set.of(x, y));
        if (cached != null) {
            return new IndependenceCheck(edge, cached);
        }

        // The sweep is a function of the graph. If it already exhausted for this
        // pair at the current graph version, re-running it is guaranteed waste.
        Long failedAt = sweepFailedAt.get(Set.of(x, y));
        if (failedAt != null && failedAt == graphVersion) {
            return null;
        }

        // Per-edge deadline: at most `timeout` ms spent separating THIS edge,
        // shared across every RB call and both views below.
        final long deadline = (timeout < 0L)
                ? Long.MAX_VALUE
                : System.currentTimeMillis() + timeout;

        // Candidate sets already tested for this edge, shared across both views:
        // the nested enumerations can arrive at the same S by different routes.
        Set<Set<Node>> tried = new HashSet<>();

        if (!useRecursiveBlockingSweep) {
            // No proposal machinery here; the completion layer supplies
            // separators, and they arrive via the foundSepsets fast path above
            // on the next pass of the outer loop.
            return null;
        }

        // Shortcut pass: small subsets of the common neighborhood, by increasing
        // size. Candidates feed `tried`, so the sweep below never retests them.
        if (quickSubsetDepth >= 0) {
            Set<Node> quick = quickCommonSubsetSepset(x, y, deadline, tried);
            if (quick != null) {
                return new IndependenceCheck(edge, quick);
            }
        }

        SweepOutcome live = sweepForSepset(this.pag, x, y, deadline, tried);
        if (live.sepset() != null) {
            return new IndependenceCheck(edge, live.sepset());
        }

        if (!useBlindView) {
            if (!live.indeterminate()) {
                sweepFailedAt.put(Set.of(x, y), graphVersion);
            }
            return null;
        }

        SweepOutcome blindOutcome = sweepForSepset(blind, x, y, deadline, tried);
        if (blindOutcome.sepset() != null) {
            return new IndependenceCheck(edge, blindOutcome.sepset());
        }

        // Cache only a DEFINITE exhaustion. A budget-truncated failure could
        // succeed on a later attempt with a fresh budget, so it is not cached.
        if (!live.indeterminate() && !blindOutcome.indeterminate()) {
            sweepFailedAt.put(Set.of(x, y), graphVersion);
        }

        return null;
    }

    // -------------------------------------------------------------------------
    // The shared sweep (Def. rb-step): one implementation, used by the removal
    // phase (live + blind views) and by the detection scan.
    // -------------------------------------------------------------------------

    /**
     * One sweep against one view of the graph. The view supplies proposals only;
     * every returned set is test-confirmed. Candidate order is canonical
     * (name-sorted lists, subsets in SublistGenerator order), so the first
     * confirmed separator is a deterministic function of (view, x, y).
     *
     * <p>Layers: NF enumeration over the ambiguous (circle-bearing) members of
     * the seed blocking set; per NF, removals over the whole blocking set
     * (common neighbors first) and additions over common neighbors the blocking
     * set omitted. For every subset S of the swept common neighbors this family
     * contains a candidate that conditions on S and withholds the rest both from
     * traversal and from the tested set -- the coverage-bearing core of the
     * progress lemma.</p>
     */
    private SweepOutcome sweepForSepset(Graph view, Node x, Node y, long deadline, Set<Set<Node>> tried)
            throws InterruptedException {

        boolean sawIndeterminate = false;

        // Seed blocking set with no forbidden nodes.
        RecursiveBlocking.BlockingResult b0 = blockWithEscalation(view, x, y, Set.of(), deadline);
        if (b0.indeterminate()) {
            sawIndeterminate = true;
        }

        List<Node> common = new ArrayList<>(view.getAdjacentNodes(x));
        common.retainAll(view.getAdjacentNodes(y));
        common.sort(NODE_ORDER);

        // NF candidates: ambiguous nodes -- those with at least one circle
        // endpoint -- in the seed blocking set, UNIONED with the ambiguous
        // common neighbors of the pair. The union matters: when the seed run is
        // UNBLOCKABLE on the live view (interim marks force conditioning that
        // activates a collider route to y), the seed set is null and a
        // seed-only harvest would empty the NF layer on exactly the view whose
        // marks expose the collider activations the fixed-point loop needs.
        // Ambiguous common neighbors are the skeleton-relative pool B' of
        // Def. rb-step; forbidding a subset of them prunes the poisoned routes
        // and lets the live-view fixed point pull in off-common-neighbor
        // blockers (e.g., the unique separator {V1,V5} for a spurious V2-V3
        // with V1 outside the common neighborhood -- the PKE12 witness).
        Set<Node> nfCandSet = new LinkedHashSet<>();
        if (b0.blockingSet() != null) {
            for (Node v : b0.blockingSet()) {
                if (hasCircleEndpoint(view, v)) {
                    nfCandSet.add(v);
                }
            }
        }
        for (Node c : common) {
            if (hasCircleEndpoint(view, c)) {
                nfCandSet.add(c);
            }
        }
        List<Node> nfCand = new ArrayList<>(nfCandSet);
        nfCand.sort(NODE_ORDER);

        int nfMax = (sweepNfMaxSize < 0)
                ? nfCand.size()
                : Math.min(sweepNfMaxSize, nfCand.size());
        SublistGenerator nfGen = new SublistGenerator(nfCand.size(), nfMax);
        int[] nfChoice;
        while ((nfChoice = nfGen.next()) != null) {
            if (System.currentTimeMillis() > deadline) return new SweepOutcome(null, true);

            Set<Node> notFollowed = GraphUtils.asSet(nfChoice, nfCand);

            RecursiveBlocking.BlockingResult result = notFollowed.isEmpty()
                    ? b0
                    : blockWithEscalation(view, x, y, notFollowed, deadline);

            if (result.indeterminate()) {
                sawIndeterminate = true;
                continue;
            }

            Set<Node> B = result.blockingSet();
            if (B == null) {
                continue; // Unblockable under this NF; try another NF.
            }

            Set<Node> base = new LinkedHashSet<>(B);

            // Removal candidates: the whole base, common neighbors first, each
            // block name-sorted. Removing a common neighbor from the tested set
            // (together with withholding it from traversal via NF) is what lets
            // the distribution, rather than the still-ambiguous graph, fix the
            // collider status of each swept node.
            List<Node> removalCandidates = new ArrayList<>();
            for (Node n : base) if (common.contains(n)) removalCandidates.add(n);
            removalCandidates.sort(NODE_ORDER);
            List<Node> restOfBase = new ArrayList<>();
            for (Node n : base) if (!common.contains(n)) restOfBase.add(n);
            restOfBase.sort(NODE_ORDER);
            removalCandidates.addAll(restOfBase);

            // Additive candidates: common neighbors the blocking search omitted.
            // RB omits a node either because it conditioned around it or because
            // a definite mark made the path look blocked; when that mark is an
            // artifact of the edge under test, the omitted node must be
            // enumerable back in. (On the blind view there are no definite
            // colliders, so this pool shrinks toward empty there.)
            List<Node> addCandidates = new ArrayList<>();
            for (Node c : common) if (!base.contains(c)) addCandidates.add(c);
            // `common` is name-sorted, so addCandidates is too.

            int addMax = (sweepAddMaxSize < 0)
                    ? addCandidates.size()
                    : Math.min(sweepAddMaxSize, addCandidates.size());
            SublistGenerator aGen = new SublistGenerator(addCandidates.size(), addMax);
            int[] aChoice;
            while ((aChoice = aGen.next()) != null) {
                if (System.currentTimeMillis() > deadline) return new SweepOutcome(null, true);

                Set<Node> A = GraphUtils.asSet(aChoice, addCandidates);

                int remMax = (sweepRemoveMaxSize < 0)
                        ? removalCandidates.size()
                        : Math.min(sweepRemoveMaxSize, removalCandidates.size());
                SublistGenerator cGen = new SublistGenerator(removalCandidates.size(), remMax);
                int[] cChoice;
                while ((cChoice = cGen.next()) != null) {
                    if (System.currentTimeMillis() > deadline) return new SweepOutcome(null, true);

                    Set<Node> S = new HashSet<>(base);
                    S.removeAll(GraphUtils.asSet(cChoice, removalCandidates));
                    S.addAll(A);

                    if (this.depth != -1 && S.size() > this.depth) continue;

                    if (!tried.add(S)) continue; // already tested this candidate

                    if (maxTestsPerPair >= 0 && tried.size() > maxTestsPerPair) {
                        // Budget exhausted: not-found within budget, which is
                        // INDETERMINATE, not "no separator exists".
                        return new SweepOutcome(null, true);
                    }

                    checkCounter.increment("sepset sweep (test executed)");

                    if (this.test.checkIndependence(x, y, S).isIndependent()) {
                        return new SweepOutcome(S, sawIndeterminate);
                    }
                }
            }
        }

        return new SweepOutcome(null, sawIndeterminate);
    }

    /**
     * True iff {@code v} carries at least one circle endpoint on some incident
     * edge of {@code view} -- the ambiguity criterion of Def. rb-step's harvest.
     */
    private static boolean hasCircleEndpoint(Graph view, Node v) {
        for (Node w : view.getAdjacentNodes(v)) {
            if (view.getEndpoint(v, w) == Endpoint.CIRCLE
                    || view.getEndpoint(w, v) == Endpoint.CIRCLE) {
                return true;
            }
        }
        return false;
    }

    /**
     * Small-subset shortcut: tests subsets of the common neighborhood of
     * {@code (x, y)} by increasing size, up to {@link #quickSubsetDepth}.
     * Returns the first confirmed separator, or null. Every candidate is
     * test-confirmed exactly as in the sweep, so soundness is untouched; what
     * changes is which valid separator is found first.
     */
    private Set<Node> quickCommonSubsetSepset(Node x, Node y, long deadline, Set<Set<Node>> tried)
            throws InterruptedException {
        List<Node> common = new ArrayList<>(this.pag.getAdjacentNodes(x));
        common.retainAll(this.pag.getAdjacentNodes(y));
        common.sort(NODE_ORDER);

        int max = Math.min(quickSubsetDepth, common.size());
        if (this.depth >= 0) max = Math.min(max, this.depth);

        SublistGenerator gen = new SublistGenerator(common.size(), max);
        int[] choice;
        while ((choice = gen.next()) != null) {
            if (System.currentTimeMillis() > deadline) return null;

            Set<Node> S = GraphUtils.asSet(choice, common);
            if (!tried.add(S)) continue;

            if (maxTestsPerPair >= 0 && tried.size() > maxTestsPerPair) return null;

            checkCounter.increment("quick common-subset pass (test executed)");

            if (this.test.checkIndependence(x, y, S).isIndependent()) {
                return S;
            }
        }

        return null;
    }

    /**
     * Recursive blocking with depth escalation when a conditioning-set cap is
     * set. NOTE: RecursiveBlocking currently reports a binding depth cap (and a
     * binding radius pool) as UNBLOCKABLE rather than INDETERMINATE, contrary to
     * its documented three-valued contract; until that is fixed there, we
     * escalate on any not-found result rather than only on indeterminate().
     * Harmless when the verdict is genuinely UNBLOCKABLE: the cap bounds the
     * extra calls.
     */
    private RecursiveBlocking.BlockingResult blockWithEscalation(Graph view, Node x, Node y,
                                                                 Set<Node> notFollowed, long deadline)
            throws InterruptedException {
        if (this.depth < 0) {
            return RecursiveBlocking.blockPathsRecursively(
                    view, x, y, Set.of(), notFollowed, recursiveDepth, this.depth, rbRadius, 1, true,
                    deadline);
        }

        RecursiveBlocking.BlockingResult result = null;

        for (int d = 1; d <= this.depth; d++) {
            if (System.currentTimeMillis() > deadline) {
                return new RecursiveBlocking.BlockingResult(null, true);
            }

            result = RecursiveBlocking.blockPathsRecursively(
                    view, x, y, Set.of(), notFollowed, recursiveDepth, d, rbRadius, 1, true,
                    deadline);

            if (result.found()) {
                return result;
            }
        }

        return result == null ? new RecursiveBlocking.BlockingResult(null, true) : result;
    }

    // -------------------------------------------------------------------------
    // Commit/revert with the scratch sepset discipline.
    // -------------------------------------------------------------------------

    /**
     * Attempts one deletion: removes the edge, reorients from scratch against a
     * scratch copy of the committed sepset map (so R4-time recordings made
     * during the candidate reorientation are adopted on commit and discarded on
     * revert -- the P1 discipline), and gates the commit on the PAG legality
     * check. A reorientation failure (e.g., an R4 separator search that cannot
     * complete) is treated as a failed candidate and reverted, not a crash.
     */
    private boolean tryToModifyGraph(Node x, Node y, Set<Node> b, String type, boolean excludeSelectionBias,
                                     Set<Triple> initialColliders) {
        Edge _edge = pag.getEdge(x, y);
        if (_edge == null) {
            return false;
        }

        Graph _pag = new EdgeListGraph(pag);

        SepsetMap scratch = copySepsetMap(this.sepsets);
        scratch.set(x, y, b);
        if (r0r4Strategy != null) {
            r0r4Strategy.setSepsetMap(scratch);
        }

        this.pag.removeEdge(_edge);

        // Memo consult: the state is fixed once the edge is removed and the
        // scratch sepsets are set, and both are inputs to a deterministic
        // reorientation, so a state known illegal can be reverted without
        // reorienting or checking.
        final String stateKey = skeletonSignature(this.pag) + "|" + sepsetSignature(scratch);
        final boolean memoHit = Boolean.TRUE.equals(illegalStates.get(stateKey));

        if (memoHit && !auditLegalityMemo) {
            if (verbose) {
                TetradLogger.getInstance().log("\tTried removing " + _edge
                        + " (memoized illegal state; no reorientation), sepset = " + b);
            }
            restorePag(_pag);
            if (r0r4Strategy != null) {
                r0r4Strategy.setSepsetMap(this.sepsets);
            }
            return false;
        }

        boolean orientationFailed = false;
        String failureReason = null;

        try {
            redoGfciOrientation(this.pag, fciOrient, knowledge, initialColliders, scratch,
                    excludeSelectionBias, superVerbose);
        } catch (IllegalStateException ex) {
            orientationFailed = true;
            failureReason = ex.getMessage();
        }

        PagLegalityCheck.LegalPagRet legalPagQuiet = null;
        if (!orientationFailed) {
            legalPagQuiet = PagLegalityCheck.isLegalPag(this.pag, new HashSet<>(selection));
        }

        if (orientationFailed || !legalPagQuiet.isLegalPag()) {
            illegalStates.put(stateKey, Boolean.TRUE);

            if (verbose) {
                TetradLogger.getInstance().log("\tTried removing " + _edge
                        + ", but it didn't lead to a PAG, sepset = " + b);
                TetradLogger.getInstance().log("\tReason = "
                        + (orientationFailed ? ("reorientation failed: " + failureReason)
                        : legalPagQuiet.getReason()));
            }

            restorePag(_pag);
            if (r0r4Strategy != null) {
                r0r4Strategy.setSepsetMap(this.sepsets);
            }
            return false;
        }

        if (memoHit) {
            // Audit mode only: the memo said illegal, the recomputation says
            // legal. Reorientation is not a function of (skeleton, sepsets).
            TetradLogger.getInstance().log("LEGALITY MEMO MISMATCH at " + _edge
                    + ": memoized ILLEGAL, recomputed LEGAL. Reorientation is "
                    + "nondeterministic given (skeleton, sepsets); investigate before "
                    + "trusting the memo.");
        }

        // Commit: adopt the scratch map wholesale. The strategy already points
        // at it.
        this.sepsets = scratch;
        graphVersion++;

        if (verbose) {
            TetradLogger.getInstance().log("Removing " + _edge + " (" + type + "), sepset = " + b);
        }

        return true;
    }

    /**
     * Restores the PAG from a backup copy, re-wrapping in the replication policy
     * if it was in force (a plain copy would silently drop the wrapper).
     */
    private void restorePag(Graph backup) {
        this.pag = replicatingGraph
                ? new ReplicatingGraph(backup, new LagReplicationPolicy())
                : backup;
    }

    // -------------------------------------------------------------------------
    // Phase 2: the saturating step (Cor. saturation / Thm. algorithm).
    // -------------------------------------------------------------------------

    /**
     * Deletes every still-adjacent pair whose separator has been test-confirmed
     * (the {@link #foundSepsets} record) simultaneously, records the separators,
     * and reorients once from scratch. Under the oracle, with the confirmed set
     * comprising all spurious edges (progress / out-of-B coverage), the result
     * is the legal Markov PAG G* -- unconditionally, with no per-step
     * certificate. The result is kept when legal; an illegal result is reverted
     * with a loud diagnostic, since under the oracle illegality here contradicts
     * the corollary and indicates test error, a coverage failure, or a bug.
     *
     * @return true iff the saturating step committed (removed at least one edge)
     */
    private boolean saturatingPass(boolean excludeSelectionBias, Set<Triple> initialColliders) {
        List<Edge> confirmed = new ArrayList<>();

        for (Edge e : this.pag.getEdges()) {
            Node x = e.getNode1();
            Node y = e.getNode2();

            if (knowledge != null && (knowledge.isRequired(x.getName(), y.getName())
                    || knowledge.isRequired(y.getName(), x.getName()))) continue;

            if (foundSepsets.containsKey(Set.of(x, y))) {
                confirmed.add(e);
            }
        }

        if (confirmed.isEmpty()) {
            return false;
        }

        confirmed.sort(EDGE_ORDER);

        TetradLogger.getInstance().log("\nSaturating step: deleting " + confirmed.size()
                + " confirmed-spurious edge(s) together and reorienting once from the recorded separators.");

        Graph backup = new EdgeListGraph(this.pag);

        SepsetMap scratch = copySepsetMap(this.sepsets);
        if (r0r4Strategy != null) {
            r0r4Strategy.setSepsetMap(scratch);
        }

        for (Edge e : confirmed) {
            Node x = e.getNode1();
            Node y = e.getNode2();
            Edge live = this.pag.getEdge(x, y);
            if (live != null) {
                this.pag.removeEdge(live);
            }
            scratch.set(x, y, foundSepsets.get(Set.of(x, y)));
        }

        final String satKey = skeletonSignature(this.pag) + "|" + sepsetSignature(scratch);
        if (Boolean.TRUE.equals(illegalStates.get(satKey)) && !auditLegalityMemo) {
            restorePag(backup);
            if (r0r4Strategy != null) {
                r0r4Strategy.setSepsetMap(this.sepsets);
            }
            return false;
        }

        boolean orientationFailed = false;
        String failureReason = null;

        try {
            redoGfciOrientation(this.pag, fciOrient, knowledge, initialColliders, scratch,
                    excludeSelectionBias, superVerbose);
        } catch (IllegalStateException ex) {
            orientationFailed = true;
            failureReason = ex.getMessage();
        }

        PagLegalityCheck.LegalPagRet legal = null;
        if (!orientationFailed) {
            legal = PagLegalityCheck.isLegalPag(this.pag, new HashSet<>(selection));
        }

        if (orientationFailed || !legal.isLegalPag()) {
            illegalStates.put(satKey, Boolean.TRUE);
            restorePag(backup);
            if (r0r4Strategy != null) {
                r0r4Strategy.setSepsetMap(this.sepsets);
            }

            TetradLogger.getInstance().log("SATURATION REVERTED: the saturating reorientation "
                    + (orientationFailed ? ("failed (" + failureReason + ")") : "was not a legal PAG ("
                    + legal.getReason() + ")")
                    + ". Every deleted pair was test-confirmed independent, so under the oracle this "
                    + "contradicts the saturation corollary; it indicates finite-sample test error, an "
                    + "out-of-B coverage failure, or a bug, and should be investigated.");
            return false;
        }

        this.sepsets = scratch;
        graphVersion++;

        TetradLogger.getInstance().log("Saturating step committed: " + confirmed.size()
                + " edge(s) removed.");
        return true;
    }

    // -------------------------------------------------------------------------
    // Completion layer with a classical guarantee (FCI D-SEP completeness).
    // -------------------------------------------------------------------------

    /**
     * Completion pass for remaining adjacencies the two-view sweep could not
     * separate. For each such edge, conditioning sets are enumerated by
     * increasing size from a permissive Possible-D-SEP pool (and, failing that,
     * from all remaining nodes), each candidate adjudicated by the test.
     *
     * <p>Rationale: the recursive-blocking family reads the marks of a view,
     * and a propagated unsound mark on all-real edges (the displacement
     * mechanism) can make an M*-active path look blocked at a node outside the
     * swept set -- an out-of-B coverage failure, observed at six observed
     * variables. The pool here is mark-agnostic in the dangerous direction:
     * the walk continues through b iff the triple is a triangle or both
     * path-edge endpoints at b are arrow-or-circle, so circles and EXTRA
     * unsound arrowheads cannot hide a member; the all-nodes escalation covers
     * unsound tails as well. Under the oracle, the adjacency-superset
     * invariant plus D-SEP completeness then makes every spurious edge
     * confirmable unconditionally -- coverage becomes an efficiency statement
     * (how often this pass fires), not a correctness hypothesis.</p>
     *
     * <p>Every firing of this pass is a witness that the recursive-blocking
     * family missed a separator; every confirmation is a concrete
     * coverage-failure instance and is logged as such for the harness.</p>
     *
     * @return true iff a separator was confirmed for at least one remaining
     * edge (recorded in {@link #foundSepsets} for discharge by removal or
     * saturation)
     */
    private boolean pdsCompletionPass() throws InterruptedException {
        if (completionPolicy == CompletionPolicy.OFF) {
            return false;
        }

        boolean any = false;

        List<Edge> edges = new ArrayList<>(this.pag.getEdges());
        edges.sort(EDGE_ORDER);

        for (Edge e : edges) {
            Node x = e.getNode1();
            Node y = e.getNode2();

            if (knowledge != null && (knowledge.isRequired(x.getName(), y.getName())
                    || knowledge.isRequired(y.getName(), x.getName()))) continue;

            if (foundSepsets.containsKey(Set.of(x, y))) continue;

            final long deadline = (timeout < 0L)
                    ? Long.MAX_VALUE
                    : System.currentTimeMillis() + timeout;

            Set<Set<Node>> tried = new HashSet<>();

            // Primary pool: permissive possible-D-SEP from both endpoints.
            Set<Node> pool = new LinkedHashSet<>(permissivePossibleDsep(x, y));
            pool.addAll(permissivePossibleDsep(y, x));

            Set<Node> found = enumeratePoolSubsets(x, y, pool, deadline, tried);

            // Escalation: all remaining nodes. Covers separators whose members
            // an unsound TAIL hid even from the permissive walk. Certified
            // complete at the oracle; deadline- and depth-capped from sample.
            if (found == null && completionPolicy == CompletionPolicy.FULL) {
                Set<Node> all = new LinkedHashSet<>(this.pag.getNodes());
                all.remove(x);
                all.remove(y);
                if (!all.equals(pool)) {
                    found = enumeratePoolSubsets(x, y, all, deadline, tried);
                }
            }

            if (found != null) {
                TetradLogger.getInstance().log("PDS COMPLETION: confirmed separator for " + e
                        + " that the recursive-blocking family missed (out-of-B coverage-failure "
                        + "witness): sepset = " + found);
                foundSepsets.put(Set.of(x, y), found);
                any = true;
            }
        }

        return any;
    }

    /**
     * Enumerates subsets of {@code pool} by increasing size (canonical order)
     * and returns the first test-confirmed separator of (x, y), or null.
     */
    private Set<Node> enumeratePoolSubsets(Node x, Node y, Set<Node> pool, long deadline,
                                           Set<Set<Node>> tried) throws InterruptedException {
        List<Node> poolList = new ArrayList<>(pool);
        poolList.sort(NODE_ORDER);

        int maxSize = poolList.size();
        if (this.depth >= 0) maxSize = Math.min(maxSize, this.depth);
        if (completionMaxSubsetSize >= 0) maxSize = Math.min(maxSize, completionMaxSubsetSize);

        SublistGenerator gen = new SublistGenerator(poolList.size(), maxSize);
        int[] choice;
        while ((choice = gen.next()) != null) {
            if (System.currentTimeMillis() > deadline) return null;

            Set<Node> S = GraphUtils.asSet(choice, poolList);

            if (!tried.add(S)) continue;

            checkCounter.increment("possible-dsep completion (test executed)");

            if (this.test.checkIndependence(x, y, S).isIndependent()) {
                return S;
            }
        }
        return null;
    }

    /**
     * Permissive Possible-D-SEP of x (relative to y) on the current PAG: v is
     * in the pool iff a path x ... v exists on which every non-endpoint b is
     * either in a triangle (its path-neighbors adjacent) or a POSSIBLE
     * collider -- both path-edge endpoints at b are ARROW or CIRCLE. The
     * possible-collider reading (rather than definite-collider) makes the pool
     * robust to circles and to extra unsound arrowheads; only an unsound tail
     * can exclude a member, and the all-nodes escalation covers that.
     */
    private Set<Node> permissivePossibleDsep(Node x, Node y) {
        Set<Node> pds = new LinkedHashSet<>();
        Deque<Node[]> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();

        List<Node> firstHops = new ArrayList<>(this.pag.getAdjacentNodes(x));
        firstHops.sort(NODE_ORDER);

        for (Node n : firstHops) {
            if (visited.add(x.getName() + ">" + n.getName())) {
                pds.add(n);
                queue.add(new Node[]{x, n});
            }
        }

        while (!queue.isEmpty()) {
            Node[] pr = queue.poll();
            Node a = pr[0];
            Node b = pr[1];

            List<Node> nexts = new ArrayList<>(this.pag.getAdjacentNodes(b));
            nexts.sort(NODE_ORDER);

            for (Node c : nexts) {
                if (c == a) continue;

                boolean triangle = this.pag.isAdjacentTo(a, c);
                boolean possibleCollider =
                        this.pag.getEndpoint(a, b) != Endpoint.TAIL
                                && this.pag.getEndpoint(c, b) != Endpoint.TAIL;

                if (!(triangle || possibleCollider)) continue;

                if (visited.add(b.getName() + ">" + c.getName())) {
                    pds.add(c);
                    queue.add(new Node[]{b, c});
                }
            }
        }

        pds.remove(x);
        pds.remove(y);
        return pds;
    }

    // -------------------------------------------------------------------------
    // Detection with confirmation, and discharge.
    // -------------------------------------------------------------------------

    /**
     * Scans every leg and chord of every discriminating path in the current PAG
     * and classifies it via the same sweep the removal phase uses -- a leg is
     * confirmed spurious only by a test-confirmed separator, never by a
     * graphical proposal alone. Newly confirmed pairs are recorded into
     * {@link #foundSepsets}, which is what lets the outer loop discharge them
     * (by single-edge removal or by the saturating pass). Indeterminate blocking
     * searches are propagated so a null result reads "no phantom confirmed
     * within budget" rather than "no phantom exists".
     */
    private NongenuineScan scanNongenuineLegs() throws InterruptedException {
        Set<DiscriminatingPath> ddps = FciOrient.listDiscriminatingPaths(pag, -1, true);

        // Within one pass, the same pair can appear as a leg/chord of several
        // discriminating paths; memoize the verdict per unordered pair. (The PAG
        // is not mutated during this scan, so the verdict is stable.)
        Map<Set<Node>, LegVerdict> verdictCache = new HashMap<>();

        final long deadlineMs = (timeout < 0L)
                ? Long.MAX_VALUE
                : System.currentTimeMillis() + timeout;

        Graph blind = blindView(this.pag);

        boolean sawIndeterminate = false;
        boolean newEvidence = false;
        Edge firstConfirmed = null;

        for (DiscriminatingPath dd : ddps) {
            List<Node> colliderPath = dd.getColliderPath();

            List<Node> spine = new ArrayList<>(colliderPath);
            spine.addFirst(dd.getX());
            spine.addLast(dd.getY());

            // Path edges: consecutive spine vertices.
            for (int i = 0; i < spine.size() - 1; i++) {
                Node m = spine.get(i);
                Node n = spine.get(i + 1);

                boolean known = foundSepsets.containsKey(Set.of(m, n));
                LegVerdict v = legVerdict(m, n, blind, deadlineMs, verdictCache);

                if (v == LegVerdict.SPURIOUS) {
                    if (!known) newEvidence = true;
                    if (firstConfirmed == null) firstConfirmed = pag.getEdge(m, n);
                } else if (v == LegVerdict.INDETERMINATE) {
                    sawIndeterminate = true;
                }
            }

            // Chords v_i *-> c: each interior collider to the far endpoint y.
            Node y = dd.getY();
            for (Node v0 : colliderPath) {
                boolean known = foundSepsets.containsKey(Set.of(v0, y));
                LegVerdict v = legVerdict(v0, y, blind, deadlineMs, verdictCache);

                if (v == LegVerdict.SPURIOUS) {
                    if (!known) newEvidence = true;
                    if (firstConfirmed == null) firstConfirmed = pag.getEdge(v0, y);
                } else if (v == LegVerdict.INDETERMINATE) {
                    sawIndeterminate = true;
                }
            }
        }

        return new NongenuineScan(firstConfirmed, newEvidence, sawIndeterminate);
    }

    /**
     * Classifies one adjacent pair on a discriminating path. SPURIOUS requires a
     * test-confirmed separator: either one already on record, or one found now
     * by the shared sweep (live view, then blind view). A confirmed separator is
     * recorded so the pair can be discharged.
     */
    private LegVerdict legVerdict(Node m, Node n, Graph blind, long deadlineMs, Map<Set<Node>, LegVerdict> cache)
            throws InterruptedException {
        Edge edge = pag.getEdge(m, n);
        if (edge == null) {
            return LegVerdict.NOT_SPURIOUS;
        }

        Set<Node> key = Set.of(m, n);

        // Cheapest signal first: a separator already confirmed for this pair is
        // a data fact; the adjacency is spurious.
        if (foundSepsets.containsKey(key)) {
            return LegVerdict.SPURIOUS;
        }

        LegVerdict cached = cache.get(key);
        if (cached != null) {
            return cached;
        }

        Set<Set<Node>> tried = new HashSet<>();

        if (!useRecursiveBlockingSweep) {
            // Confirm by the completion enumeration instead, so detection keeps
            // its "test-confirmed or nothing" contract.
            Set<Node> byCompletion = null;

            if (completionPolicy != CompletionPolicy.OFF) {
                Set<Node> pool = new LinkedHashSet<>(permissivePossibleDsep(m, n));
                pool.addAll(permissivePossibleDsep(n, m));
                byCompletion = enumeratePoolSubsets(m, n, pool, deadlineMs, tried);

                if (byCompletion == null && completionPolicy == CompletionPolicy.FULL) {
                    Set<Node> all = new LinkedHashSet<>(this.pag.getNodes());
                    all.remove(m);
                    all.remove(n);
                    byCompletion = enumeratePoolSubsets(m, n, all, deadlineMs, tried);
                }
            }

            LegVerdict vc;
            if (byCompletion != null) {
                foundSepsets.put(key, byCompletion);
                vc = LegVerdict.SPURIOUS;
            } else if (completionPolicy == CompletionPolicy.OFF) {
                // Nothing left that can confirm; do not report NOT_SPURIOUS.
                vc = LegVerdict.INDETERMINATE;
            } else {
                vc = LegVerdict.NOT_SPURIOUS;
            }

            cache.put(key, vc);
            return vc;
        }

        SweepOutcome live = sweepForSepset(this.pag, m, n, deadlineMs, tried);
        SweepOutcome blindOutcome = (live.sepset() != null || !useBlindView)
                ? live
                : sweepForSepset(blind, m, n, deadlineMs, tried);

        Set<Node> found = (live.sepset() != null) ? live.sepset() : blindOutcome.sepset();

        LegVerdict v;
        if (found != null) {
            foundSepsets.put(key, found);  // test-confirmed; enables discharge
            v = LegVerdict.SPURIOUS;
        } else if (live.indeterminate() || blindOutcome.indeterminate()) {
            v = LegVerdict.INDETERMINATE;
        } else {
            v = LegVerdict.NOT_SPURIOUS;
        }

        cache.put(key, v);
        return v;
    }

    // -------------------------------------------------------------------------
    // Seed searches.
    // -------------------------------------------------------------------------

    /**
     * Configures and returns a new PermutationSearch using the BOSS algorithm.
     */
    private @NotNull PermutationSearch getBossSearch() {
        Boss subAlg = new Boss(score);
        subAlg.setUseBes(useBes);
        subAlg.setNumStarts(numStarts);
        subAlg.setNumThreads(Runtime.getRuntime().availableProcessors());
        subAlg.setVerbose(false);
        PermutationSearch alg = new PermutationSearch(subAlg);
        alg.setKnowledge(knowledge);
        return alg;
    }

    /**
     * Parameterizes and returns a new GRaSP search.
     */
    private @NotNull Grasp getGraspSearch() {
        Grasp grasp = new Grasp(test, score);

        grasp.setSeed(-1);
        grasp.setDepth(3);
        grasp.setUncoveredDepth(1);
        grasp.setNonSingularDepth(1);
        grasp.setOrdered(true);
        grasp.setUseScore(true);
        grasp.setUseRaskuttiUhler(false);
        grasp.setUseDataOrder(useDataOrder);
        grasp.setAllowInternalRandomness(false);
        grasp.setVerbose(superVerbose);
        grasp.setNumStarts(numStarts);
        grasp.setKnowledge(this.knowledge);

        return grasp;
    }

    // -------------------------------------------------------------------------
    // Settings.
    // -------------------------------------------------------------------------

    /**
     * Sets the algorithm to use to get the initial CPDAG.
     *
     * @param startWith the algorithm to use to get the initial CPDAG.
     */
    public void setStartWith(START_WITH startWith) {
        this.startWith = startWith;
    }

    /**
     * Sets the knowledge used in search.
     *
     * @param knowledge This knowledge.
     */
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }

    /**
     * Sets the verbosity level of the search algorithm.
     *
     * @param superVerbose true to enable superVerbose mode, false to disable it
     */
    public void setSuperVerbose(boolean superVerbose) {
        this.superVerbose = superVerbose;
    }

    /**
     * True just in case good and restored changes are printed.
     *
     * @param verbose True if changes to the graph should be printed.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Sets the number of starts for BOSS.
     *
     * @param numStarts The number of starts.
     */
    public void setNumStarts(int numStarts) {
        this.numStarts = numStarts;
    }

    /**
     * Sets whether to use the BES (Backward Elimination Search) algorithm during the search.
     *
     * @param useBes true to use the BES algorithm, false otherwise
     */
    public void setUseBes(boolean useBes) {
        this.useBes = useBes;
    }

    /**
     * Sets the flag indicating whether to use data order.
     *
     * @param useDataOrder {@code true} if the data order should be used, {@code false} otherwise.
     */
    public void setUseDataOrder(boolean useDataOrder) {
        this.useDataOrder = useDataOrder;
    }

    /**
     * Sets whether the Zhang complete rule set should be used; false if only R1-R4 (the rule set of the original FCI)
     * should be used. True by default.
     *
     * @param completeRuleSetUsed True for the complete Zhang rule set.
     */
    public void setCompleteRuleSetUsed(boolean completeRuleSetUsed) {
        this.completeRuleSetUsed = completeRuleSetUsed;
    }

    /**
     * Sets the depth of search, which is the maximum number of variables conditioned on in any test.
     *
     * @param depth This maximum.
     */
    public void setDepth(int depth) {
        if (depth < -1) {
            throw new IllegalArgumentException("Depth must be -1 (unlimited) or >= 0: " + depth);
        }

        this.depth = depth;
    }

    /**
     * Sets the flag indicating whether the graph should be replicated during the search process.
     *
     * @param replicatingGraph true to enable graph replication, false otherwise.
     */
    public void setReplicatingGraph(boolean replicatingGraph) {
        this.replicatingGraph = replicatingGraph;
    }

    /**
     * Sets whether selection bias should be excluded during the search process.
     *
     * @param excludeSelectionBias True to exclude selection bias, false otherwise.
     */
    public void setExcludeSelectionBias(boolean excludeSelectionBias) {
        this.excludeSelectionBias = excludeSelectionBias;
    }

    /**
     * Sets the radius for the RB (Recursive Blocking) conditioning pool.
     *
     * @param rbRadius the radius to be set (-1 for unlimited)
     */
    public void setRbRadius(int rbRadius) {
        this.rbRadius = rbRadius;
    }

    /**
     * Retrieves the current depth of recursion.
     *
     * @return the depth of recursion as an integer
     */
    public int getRecursiveDepth() {
        return recursiveDepth;
    }

    /**
     * Sets the depth level for recursive operations.
     *
     * @param recursiveDepth the maximum depth to which the recursion is allowed
     */
    public void setRecursiveDepth(int recursiveDepth) {
        this.recursiveDepth = recursiveDepth;
    }

    /**
     * Sets the maximum length of the discriminating path.
     *
     * @param maxDiscriminatingPathLength the maximum number of steps or nodes allowed in the discriminating path.
     */
    public void setMaxDiscriminatingPathLength(int maxDiscriminatingPathLength) {
        this.maxDiscriminatingPathLength = maxDiscriminatingPathLength;
    }

    /**
     * Sets the timeout for per-edge separator searches, or -1 for unlimited.
     * Wall-clock, hence nondeterministic; prefer {@link #setMaxTestsPerPair} in
     * experiments that must be reproducible.
     *
     * @param timeout the maximum time in milliseconds per edge.
     */
    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    /**
     * Caps the size of not-followed subsets in the sweep's outer layer.
     *
     * @param sweepNfMaxSize the cap, or -1 for unlimited (the full family).
     */
    public void setSweepNfMaxSize(int sweepNfMaxSize) {
        this.sweepNfMaxSize = sweepNfMaxSize;
    }

    /**
     * Caps the size of addition subsets in the sweep's inner layer.
     *
     * @param sweepAddMaxSize the cap, or -1 for unlimited.
     */
    public void setSweepAddMaxSize(int sweepAddMaxSize) {
        this.sweepAddMaxSize = sweepAddMaxSize;
    }

    /**
     * Caps the size of removal subsets in the sweep's inner layer.
     *
     * @param sweepRemoveMaxSize the cap, or -1 for unlimited.
     */
    public void setSweepRemoveMaxSize(int sweepRemoveMaxSize) {
        this.sweepRemoveMaxSize = sweepRemoveMaxSize;
    }

    /**
     * Sets whether the bare-skeleton proposal view is searched when the marked
     * view finds nothing.
     *
     * @param useBlindView true for both views (the default), false for the
     *                     marked view only.
     */
    public void setUseBlindView(boolean useBlindView) {
        this.useBlindView = useBlindView;
    }

    /**
     * Sets a deterministic per-pair budget on independence tests, across both
     * views. Exhaustion yields an indeterminate sweep.
     *
     * @param maxTestsPerPair the cap, or -1 for unlimited.
     */
    public void setMaxTestsPerPair(long maxTestsPerPair) {
        this.maxTestsPerPair = maxTestsPerPair;
    }

    /**
     * Sets how much of the completion layer to run.
     *
     * @param completionPolicy the policy; FULL by default.
     */
    public void setCompletionPolicy(CompletionPolicy completionPolicy) {
        this.completionPolicy = completionPolicy;
    }

    /**
     * Caps conditioning-set size in the completion layer.
     *
     * @param completionMaxSubsetSize the cap, or -1 for unlimited.
     */
    public void setCompletionMaxSubsetSize(int completionMaxSubsetSize) {
        this.completionMaxSubsetSize = completionMaxSubsetSize;
    }

    /**
     * Turns the legality memo into an audit: hits are recomputed and mismatches
     * logged, verifying that reorientation is deterministic given
     * (skeleton, sepsets). Slower than either memo-on or memo-off; diagnostic
     * use only.
     *
     * @param auditLegalityMemo false by default.
     */
    public void setAuditLegalityMemo(boolean auditLegalityMemo) {
        this.auditLegalityMemo = auditLegalityMemo;
    }

    /**
     * Sets whether the final discriminating-path detection scan runs.
     *
     * @param detectionEnabled true by default.
     */
    public void setDetectionEnabled(boolean detectionEnabled) {
        this.detectionEnabled = detectionEnabled;
    }

    /**
     * Sets the cap for the small-subset shortcut pass; -1 disables it.
     * Non-default values change trajectories and require re-validation.
     *
     * @param quickSubsetDepth the cap, or -1 for off (default).
     */
    public void setQuickSubsetDepth(int quickSubsetDepth) {
        this.quickSubsetDepth = quickSubsetDepth;
    }

    /**
     * Sets whether the recursive-blocking sweep runs. False makes the completion
     * layer the sole source of separators.
     *
     * @param useRecursiveBlockingSweep true by default.
     */
    public void setUseRecursiveBlockingSweep(boolean useRecursiveBlockingSweep) {
        this.useRecursiveBlockingSweep = useRecursiveBlockingSweep;
    }

    /**
     * Returns the CachedIndependenceQueries wrapping this run's test, if any, for cache-statistics reporting. Returns
     * null if the test is not cached.
     */
    private CachedIndependenceQueries findCache() {
        if (test instanceof CachedIndependenceQueries c) {
            return c;
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Types.
    // -------------------------------------------------------------------------

    private enum LegVerdict {SPURIOUS, NOT_SPURIOUS, INDETERMINATE}

    /**
     * How much of the completion layer of Prop. completion to run. FULL is the
     * only setting under which the oracle guarantee holds unconditionally;
     * POOL_ONLY drops the all-nodes escalation (sound, and complete unless an
     * unsound tail hides a pool member); OFF returns the procedure to the
     * recursive-blocking family alone, i.e. to the out-of-B coverage condition,
     * which is refuted at six observed variables.
     */
    public enum CompletionPolicy {
        /**
         * Possible-D-SEP stage and all-nodes escalation. Default.
         */
        FULL,
        /**
         * Possible-D-SEP stage only, no escalation.
         */
        POOL_ONLY,
        /**
         * No completion layer.
         */
        OFF
    }

    /**
     * Enumeration representing different start options.
     */
    public enum START_WITH {
        /**
         * Start with BOSS.
         */
        BOSS,
        /**
         * Start with GRaSP.
         */
        GRASP,
        /**
         * Start with SP.
         */
        SP,
        /**
         * Starts with an initial CPDAG over the variables of the independence test that is given in the constructor.
         */
        INITIAL_GRAPH,
        /**
         * Starts with a complete o-o graph.
         */
        COMPLETE_GRAPH
    }

    private record RemovalHit(int index, Edge edge, Set<Node> cond) {
    }

    private record IndependenceCheck(Edge edge, Set<Node> cond) {
    }

    /**
     * Outcome of one sweep against one view: the first test-confirmed separator
     * (or null), and whether any blocking search along the way was truncated by
     * a deadline or cap, so a null separator means "not found within budget"
     * rather than "none exists".
     */
    private record SweepOutcome(Set<Node> sepset, boolean indeterminate) {
    }

    /**
     * Outcome of a detection scan. {@code edge} is a test-confirmed spurious
     * discriminating-path leg (the first in canonical scan order), or null if
     * none was confirmed. {@code newEvidence} is true iff the scan recorded a
     * separator for a pair not previously confirmed -- the discharge trigger.
     * {@code indeterminate} is true if any pair's search was budget-truncated,
     * so a null {@code edge} means "no phantom confirmed within budget" rather
     * than "no phantom exists."
     */
    private record NongenuineScan(Edge edge, boolean newEvidence, boolean indeterminate) {
    }
}
