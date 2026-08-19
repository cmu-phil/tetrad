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

import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.score.GraphScore;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.test.CachedIndependenceQueries;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.search.utils.*;
import edu.cmu.tetrad.sem.RicfEjml;
import edu.cmu.tetrad.util.MillisecondTimes;
import edu.cmu.tetrad.util.SublistGenerator;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FCIT-SL-SCORE-CHECK: {@link FcitSl} with a MAG SCORE CHECK on every candidate the search adjudicates.
 * <p>
 * FCIT-SL searches within (and, in escape mode, beyond) the Markov equivalence class for a representative MAG that
 * can host a proposed deletion, and commits the first candidate that clears its structural gates. This class adds
 * one further gate at exactly that point: a candidate is committed only if the MAG it yields does not DECREASE the
 * RICF Gaussian BIC relative to the currently committed model.
 * <p>
 * WHY THIS IS A CLEAN PLACE FOR THE CHECK. The object being scored is a genuine MAG by construction: the gate has
 * already verified {@code isLegalMag()} on the candidate, and the deletion cannot create an inducing path (removing
 * an edge removes paths and removes ancestor relations, so it can introduce no inducing path between any pair,
 * while the pair being separated is checked explicitly). A Gaussian MAG has a well-defined likelihood via RICF and
 * a well-defined parameter count, so the BIC is meaningful for every candidate reaching the check -- unlike a score
 * applied to an arbitrary PAG-shaped graph, where the implied "MAG" may not be a MAG at all and the number that
 * comes back means nothing.
 * <p>
 * THE REFERENCE MODEL. The BIC of a Gaussian MAG is invariant across a Markov equivalence class -- equivalent MAGs
 * impose the same constraints and carry the same number of parameters -- so it does not matter which
 * representative the class walk has travelled to: the reference is simply the BIC of the currently committed
 * model, computed once per commit and cached.
 * <p>
 * FAIL-SOFT. When the score cannot be computed (no covariance available, an undirected selection edge that RICF
 * cannot fit, a singular covariance, a non-finite likelihood), the candidate is judged by the structural gates
 * alone, exactly as in {@link FcitSl}. A missing score is never read as a rejection: doing so would silently
 * disable all deletion on any dataset RICF cannot fit.
 *
 * @author josephramsey
 * @see FcitSl
 */
public final class FcitSlScoreCheck implements IGraphSearch {
    /**
     * The independence test.
     */
    private final IndependenceTest test;
    /**
     * The score.
     */
    private final Score score;
    /**
     * Whether the MAG SCORE CHECK is applied. When false this class reproduces FcitSl exactly.
     */
    private boolean useScoreCheck = true;
    /**
     * Penalty discount c in bic = 2 * logLik - c * k * ln(n) for the MAG score check. Default 1 (classical BIC).
     * Independent of the penalty discount inside the initializer score.
     */
    private double scoreCheckPenaltyDiscount = 1.0;
    /**
     * The covariance matrix for RICF, resolved lazily from the test or the score.
     */
    private ICovarianceMatrix covarianceMatrix = null;
    /**
     * Cached MAG BIC of the currently committed model; null means "not computed yet" or "unavailable".
     */
    private Double currentMagBic = null;
    /**
     * Per-run tallies for the score check, reported unconditionally at the end of search().
     */
    private int tallyScoreChecked = 0;
    private int tallyScoreVetoes = 0;
    private int tallyScoreAccepted = 0;
    private int tallyScoreUnavailable = 0;
    /**
     * BIC gap (current - candidate, > 0) for each score veto, for the end-of-run summary.
     */
    private final List<Double> scoreVetoGaps = new ArrayList<>();
    /**
     * Running sepsets
     */
    private final SepsetMap sepsets = new SepsetMap();
    /**
     * Counts conditional independence checks, broken down by call site, so we
     * can measure how many tests the recursive-blocking optimization saves.
     */
    private final IndependenceCheckCounter checkCounter = new IndependenceCheckCounter();
    /**
     * Separators discovered for a pair during any sweep, kept across rounds.
     * Distinct from {@link #sepsets}, which records only committed (legal-PAG)
     * separations and is rolled back on a reverted removal. Because X _||_ Y | S
     * is a property of the data, not the current PAG, a set that separated a pair
     * once still separates it; reusing it keeps a pair's recorded sepset stable
     * across rounds instead of being re-derived (and possibly differing) each time
     * the edge is reconsidered after a reverted removal.
     */
    private final Map<Set<Node>, Set<Node>> foundSepsets = new ConcurrentHashMap<>();
    /**
     * P-value of the test that first separated each pair, keyed identically to
     * {@link #foundSepsets}. Lets a cached-separator removal report the original
     * test's p instead of NaN.
     */
    private final Map<Set<Node>, Double> foundPValues = new ConcurrentHashMap<>();
    /**
     * The sequence of interim PAGs built during the search: index 0 is the PAG of the initial
     * DAG, and each committed edge removal appends the PAG of the resulting MAG.
     * {@code interimPags.getLast()} is the live PAG the search reads and mutates. Never null;
     * non-empty after initialization.
     */
    private final @NotNull List<Graph> interimPags = new ArrayList<>();
    /**
     * The background knowledge.
     */
    private Knowledge knowledge = new Knowledge();
    /**
     * The algorithm to use to get the initial CPDAG.
     */
    private START_WITH startWith = START_WITH.BOSS;
    /**
     * The number of starts for GRaSP.
     */
    private int numStarts = 1;
    /**
     * Flag indicating whether to use data order.
     */
    private boolean useDataOrder = true;
    /**
     * This flag represents whether the Bes algorithm should be used in the search.
     * <p>
     * If set to true, the Bes algorithm will be used. If set to false, the Bes algorithm will not be used.
     * <p>
     * By default, the value of this flag is false.
     */
    private boolean useBes = false;
    /**
     * A set representing all identified colliders in the current CPDAG (Completed Partially Directed Acyclic Graph). A
     * collider is a node in the graph where two edges converge, and the directions of the edges are both pointing into
     * the node.
     * <p>
     * This variable is used to store colliders discovered during the execution of the FCIT search algorithm, aiding in
     * the refinement of the graph structure and ensuring proper causal inference.
     * <p>
     * Each collected collider is represented as a Triple, which encapsulates the two parent nodes and the collider
     * node.
     */
    private Set<Triple> initialColliders;
    /**
     * Whether the Zhang complete rule set should be used.
     */
    private boolean completeRuleSetUsed = true;
    /**
     * The depth of search.
     */
    private int depth = -1;
    /**
     * True just in case good and restored changes are printed. The algorithm always moves to a legal PAG; if it
     * doesn't, it is restored to the previous PAG, and a "restored" message is printed. Otherwise, a "good" message is
     * printed.
     */
    private boolean verbose = false;
    /**
     * A flag indicating whether the graph replication process is active.
     * When set to {@code true}, the graph is being replicated.
     * When set to {@code false}, the graph replication process is inactive.
     */
    private boolean replicatingGraph = false;
    /**
     * Indicates whether selection bias should be excluded during processing.
     * <p>
     * When set to {@code true}, mechanisms or algorithms that might introduce
     * or rely on selection bias will be disregarded, aiming to ensure neutrality
     * and fairness in the operation or computation. When {@code false}, selection
     * bias is not specifically excluded.
     */
    private boolean excludeSelectionBias = true;
    /**
     * The type of commit gate to use, basically whether to do the pair batter or not.
     */
    private CommitGate commitGate = CommitGate.DELETED_PAIR_BATTERY;
    /**
     * The depth of the pair battery.
     */
    private int batteryZMax = 2;
    /**
     * Maximum number of nodes that may be dropped from RB's blocking set when searching for a
     * separator in {@link #findIndependenceCheckRecursive}; -1 (default) enumerates all subsets.
     * The search is 2^|B| in the worst case, so a small positive bound (2-3) trades reach for
     * time on larger graphs. Bounding it below the number of "opened" nodes in B can leave a
     * separable pair unseparated -- exactly the failure this candidate set was widened to fix --
     * so leave it unlimited unless the per-edge cost becomes a problem.
     */
    private int maxBlockingSetRemovals = -1;
    /**
     * Maximum number of common neighbours the sepset search may ADD to RB's blocking set (phase 1
     * of {@link #trySubsetsAround}). RB proposes a blocking set against the CURRENT orientations,
     * and the subset search around it can only shrink that set -- so a node RB never proposed is
     * unreachable however the removals are enumerated. When a true non-collider is shown as a
     * collider in the interim PAG, RB reads its path as already blocked and omits exactly the node
     * the separator needs. Negative means unbounded.
     */
    private int maxBlockingSetAdditions = -1;
    /**
     * Whether the sepset search, having failed on every oriented pass, retries with RB run against
     * the SKELETON (every mark a circle). With no marks, no triple is a collider, no path counts as
     * pre-blocked, and the blocking set is orientation-independent -- which recovers separators
     * hidden by a wrong collider reading in the interim PAG. Fires only on failure, so the common
     * path is unaffected. See {@link #findIndependenceCheckRecursive}.
     */
    private boolean orientationBlindFallback = true;
    /**
     * Maximum number of fork nodes converted to colliders when building an out-of-class seed
     * for a deletion the current directed class cannot host. See {@link #seedMags}.
     */
    private int maxForkFlips = 2;
    /**
     * Whether the within-class candidate generator is the CLASS WALK -- a best-first / breadth-first
     * traversal of the current Markov equivalence class by SINGLE MARK CHANGES -- instead of the
     * staged {@link #seedMags} + {@link LegEnumerator} + {@link #forkFlips} search.
     * <p>
     * Motivation (PKE11, V2--V5 at six variables). The staged search is anchored on the canonical
     * Zhang MAG and walks LEGs; by Zhang-Spirtes Prop. 2 a LEG carries the FEWEST bidirected edges
     * in its class, so the staged search is anchored at the bidirected-sparsest end of the class.
     * In that fixture every representative that hosted the deletion carried 4-9 bidirected edges
     * while the staged closure topped out at 2, and raising {@code maxForkFlips} from 2 to 5
     * changed nothing: the fork-flip move family does not span the class. Exhaustive enumeration
     * found 22 in-class hosts, none reachable by the staged generator, so the escape=false failure
     * was a SEARCH-REACH gap, not a Step-Lemma class boundary.
     * <p>
     * The class walk closes it by construction. Zhang and Spirtes prove that Markov-equivalent
     * DMAGs are connected by sequences of single mark changes that preserve Markov equivalence, so
     * a walk whose move is "flip one endpoint mark" and whose filter is "still a legal MAG, still
     * in this class" reaches every representative. Verified on the PKE11 fixture: the walk visited
     * 711/711 class members and all 22 hosts, first host at depth 7 (six edges differ from the
     * seed; one of them needs a reversal, which costs two mark changes through the bidirected
     * intermediate -- precisely the direction a LEG-anchored search has no reason to travel).
     * <p>
     * The commit gates are unchanged, so this flag alters only which candidates are proposed and
     * in what order, never what is accepted. True by default; set false for the legacy generator.
     */
    private boolean useClassWalk = true;
    /**
     * Ordering for {@link ClassWalk}. True (default): best-first, prioritising candidates that
     * already satisfy the collider stamps {@link #stampLegColliders} would otherwise have to
     * impose, then higher bidirected count, then shallower depth. False: plain breadth-first.
     * <p>
     * Best-first matters at scale. On the PKE11 fixture the hosts sit at the MEDIAN breadth-first
     * depth, not near the seed -- reaching the first host by BFS meant expanding 424 of the 711
     * class members -- so an undirected walk pays for most of the class before it succeeds. The
     * stamp-deficit heuristic is aimed directly at the structure the deletion needs.
     */
    private boolean classWalkBestFirst = true;
    /**
     * Safety cap on how many class members {@link ClassWalk} emits for a single deletion attempt.
     * The class is finite but grows fast, so an uncapped walk is a correctness oracle rather than
     * a production search. Reaching the cap is recorded in {@code classWalkTruncations}: a run
     * with a nonzero count has NOT exhausted the class, so a failure to host is inconclusive
     * rather than evidence of a class boundary. Negative means uncapped.
     */
    private int classWalkMaxCandidates = 20000;
    /**
     * Whether pass 3 (the out-of-class escape) may run. Fork-flip seeds are enumerated in both
     * modes and PARTITIONED by class membership (see {@link #seedMags}): flips certified
     * Markov-equivalent to the current class run in the within-class pass as Stage 2b, and only
     * certified non-equivalent flips are deferred to pass 3, which this flag gates. When false,
     * FCIT-SL runs in "Step-Lemma-pure" mode: every commit is hosted by a representative of the
     * current class (Zhang MAG, other LEG, or in-class fork-flip), and a state none of them can
     * serve is left alone. An oracle run that terminates at the true PAG with this false is
     * direct evidence that the Step Lemma's within-class witness sufficed at every commit.
     */
    private boolean allowClassEscape = false;
    /**
     * Whether {@link #tryToModifyGraphClosure} replaces the staged representative search
     * ({@link #seedMags} + LegEnumerator + {@link #forkFlips}) as the candidate GENERATOR.
     * The commit gates -- stamp legality, MAG legality, the removed-pair inducing-path
     * pre-check, and the deleted-pair battery -- are byte-for-byte the same in both paths,
     * so flipping this flag changes only which candidates are proposed and in what order,
     * never what is accepted. False by default so existing PKE baselines are untouched;
     * set true for a head-to-head (e.g. PKE8, 7-2-3) against the staged search.
     */
    private boolean useClosureCoverSearch = false;
    /**
     * Whether a pair the closure-cover search cannot host is handed to the staged generator
     * ({@link #tryToModifyGraph}) rather than left uncommitted. True by default: the closure
     * search's residual failures are structural (it certifies class membership at the end
     * rather than searching within the class, so where the class is a small share of the
     * separating assignments it can generate the right moves and still never land in one),
     * and the staged search has no such weakness. With this on, the closure generator's reach
     * is a superset of staged's. Set false to measure the closure search's UNAIDED reach --
     * which is what {@code closureFallbackCommits} reports either way.
     */
    private boolean closureFallbackToStaged = true;
    /**
     * Bound (in nodes) on the length of the x..y skeleton paths enumerated by the
     * closure-cover search. Paths longer than this are invisible to the covering heuristic;
     * a candidate whose only unblocked path exceeds the bound is caught by the exact
     * MsepTest confirmation at emission (and counted in {@code closureLongPathMisses}),
     * never wrongly committed.
     */
    private int closureMaxPathLength = 8;
    /**
     * Bound on the number of x..y skeleton paths enumerated by the closure-cover search.
     */
    private int closureMaxPaths = 64;
    /**
     * Maximum number of cover moves (endpoint reassignments driven by an unblocked path) per
     * candidate in the closure-cover search. The analog of {@code maxForkFlips}, but counting
     * PATH-DIRECTED assignments rather than blind subset choices, so a small value reaches
     * deeper: each move is spent on a path known to still be active.
     * <p>
     * Negative (the default) means AUTO: the budget is derived per pair from the size of the
     * subproblem -- the number of free (circle) endpoint slots on the closure of the
     * enumerated x..y paths -- capped by {@link #closureAutoBudgetCap}. A FIXED budget is the
     * wrong resource model, because the mark-distance from the Zhang MAG to a hosting
     * representative scales with how much of the local neighbourhood is undetermined, not
     * with a constant: PKE8's V1--V5 class needed five endpoint-changes and so read as
     * "no candidate hosted" at a fixed budget of 4, indistinguishable in the telemetry from a
     * genuine non-hostability, while the same search found it immediately at 6. Tying the
     * budget to the local closure keeps that failure mode from returning as models grow --
     * and the closure, not |V|, is what governs it.
     */
    private int maxCoverMoves = -1;
    /**
     * Ceiling on the AUTO move budget (see {@link #maxCoverMoves}), bounding DFS depth when
     * the local closure is large.
     */
    private int closureAutoBudgetCap = 8;
    /**
     * Closure-cover telemetry. {@code closureCandidatesEmitted}: assignments that separated
     * the pair and were handed to the gates. {@code closureInClassCommits} /
     * {@code closureEscapeCommits}: commits by class of the (pre-stamp) candidate, certified
     * by MagToPag equality exactly as {@link #seedMags} certifies seeds.
     * {@code closureStampPrunes}: branches abandoned because the stamp refused the partial
     * assignment (the gate would refuse everything below). {@code closureStampObstructions}:
     * stamp-compatibility requirements that collided with an INVARIANT arrowhead in the PAG
     * -- evidence (not proof; see {@link #applyStampCompatPins}) that no in-class candidate
     * can pass the stamp gate for that pair. {@code closureLongPathMisses}: candidates that
     * blocked every enumerated path but failed the exact m-separation confirmation, i.e. an
     * active path beyond the enumeration bounds.
     */
    private long closureCandidatesEmitted = 0, closureInClassCommits = 0, closureEscapeCommits = 0,
            closureStampPrunes = 0, closureStampObstructions = 0, closureLongPathMisses = 0,
            closureIllegalCands = 0, closureClassFiltered = 0, closureRelaxedExpansions = 0,
            closureRelaxedPasses = 0, closureFallbackAttempts = 0, closureFallbackCommits = 0;
    /**
     * Commit provenance telemetry. {@code zhangCommits}: commits hosted by the canonical
     * Zhang MAG (Stage 1). {@code legCommits}: commits hosted by a non-canonical LEG of the
     * current class (Stage 2). {@code inClassFlipCommits}: commits hosted by a fork-flip seed
     * certified Markov-equivalent to the current class (Stage 2b) -- a within-class
     * representative that is neither a LEG nor a stamped LEG (e.g. a shielded-fork flip whose
     * bidirected edges sit outside the deleted pair's common-neighbor set). {@code
     * escapeCommits}: commits hosted by a certified out-of-class seed (pass 3). A run with
     * {@code escapeCommits == 0} used only Step-Lemma-form witnesses.
     */
    private long zhangCommits = 0, legCommits = 0, inClassFlipCommits = 0, escapeCommits = 0, otherRejects = 0;
    /**
     * Class-walk telemetry. {@code classWalkCommits}: commits hosted by a representative the walk
     * reached. {@code classWalkVisited}: total class members emitted across all attempts.
     * {@code classWalkTruncations}: attempts that hit {@link #classWalkMaxCandidates} or the
     * deadline before exhausting the class -- if this is nonzero, a "no representative hosted it"
     * outcome is INCONCLUSIVE, not a class boundary.
     */
    private long classWalkCommits = 0, classWalkVisited = 0, classWalkTruncations = 0;
    /**
     * Sepset-search telemetry. {@code additionPhaseRescues}: separators found only by ADDING a
     * common neighbour absent from RB's blocking set -- each one is a separator the removal-only
     * search could not have tested. {@code blindFallbackAttempts}/{@code blindFallbackRescues}:
     * orientation-blind retries run, and how many found a separator the oriented passes missed.
     * A nonzero rescue count means the interim orientations were actively hiding separators.
     */
    private long additionPhaseRescues = 0, blindFallbackAttempts = 0, blindFallbackRescues = 0;
    /**
     * Deleted-pair battery telemetry: number of gate evaluations, refusals, and entailed
     * separation statements verified against the independence test. See
     * {@link #deletedPairBatteryPasses}.
     */
    private long batteryEvals = 0, batteryRefusals = 0, batteryStatementsTested = 0;
    /**
     * Legality-check telemetry.  {@code ipRejects}: candidates rejected by the cheap
     * removed-pair inducing-path pre-check (non-maximal exactly at the deleted pair, the
     * Lemma-B failure mode).  {@code otherRejects}: candidates that PASSED the pre-check
     * yet were still rejected by full {@code isLegalMag} -- i.e.\ illegalities NOT caused
     * by the removed pair's maximality (ancestrality violations, or new non-maximality
     * elsewhere introduced by circle orientation or collider stamping).  A near-zero
     * {@code otherRejects} at a given scope is empirical evidence that the removed-edge
     * inducing-path check alone nearly suffices for legality -- the (conjectural)
     * justification for an aggressive maximality-only shortcut.
     */
    private long ipRejects = 0, legalityChecks = 0;

    // --- focus-pair instrumentation (temporary; remove when done) ------------
    private String focusPair = null;
    private final Map<String, Integer> focusTally = new LinkedHashMap<>();

    /**
     * Sets the focus pair by combining the names of the given nodes in a specific order.
     * The names are concatenated with a null character separator, ensuring the names
     * are ordered lexicographically.
     *
     * @param a the first node whose name will be used in determining the focus pair
     * @param b the second node whose name will be used in determining the focus pair
     */
    public void setFocusPair(Node a, Node b) {
        focusPair = a.getName().compareTo(b.getName()) <= 0
                ? a.getName() + "\u0000" + b.getName()
                : b.getName() + "\u0000" + a.getName();
    }

    /**
     * Retrieves the current focus tally, which is a mapping of keys to their corresponding integer values.
     *
     * @return a map containing the focus tally, where the keys are strings and the values are integers.
     */
    public Map<String, Integer> getFocusTally() {
        return focusTally;
    }

    /** For the focus pair, the conditioning set Z of each entailed-but-rejected
     *  battery statement -- one per battery-refused candidate, in encounter order. */
    private final List<Set<String>> focusBatteryZ = new ArrayList<>();

    /**
     * Retrieves the focus battery data structured as a list of sets of strings.
     *
     * @return a list where each element is a set of strings representing focus battery information.
     */
    public List<Set<String>> getFocusBatteryZ() {
        return focusBatteryZ;
    }

    /** The interim PAG (interimPags.getLast()) the generator faced for the focus pair. */
    private String focusInterimPag = null;
    /** The basePag the generator used as class identity for the focus pair (MagToPag of the
     *  Zhang MAG of the interim PAG) -- if this over-commits vs focusInterimPag, that gap is
     *  the reach bug. */
    private String focusBasePag = null;
    /** Every DISTINCT candidate MAG (pre-stamp) the LEG/fork-flip closure enumerated for the
     *  focus pair. If the true host is absent here, the generator never reached it. */
    private final List<String> focusEnumerated = new ArrayList<>();

    /**
     * Retrieves the value of the focusInterimPag property.
     *
     * @return the current value of the focusInterimPag as a String.
     */
    public String getFocusInterimPag() { return focusInterimPag; }

    /**
     * Retrieves the value of the focusBasePag property.
     *
     * @return the current value of the focusBasePag as a String.
     */
    public String getFocusBasePag() { return focusBasePag; }

    /**
     * Retrieves the value of the focusEnumerated property.
     *
     * @return a list of strings representing the focus enumerated MAGs.
     */
    public List<String> getFocusEnumerated() { return focusEnumerated; }

    /** Every legal-MAG seed/flip generated for the focus pair BEFORE the in/out-class filter,
     *  with its class verdict and bidirected-edge count. Distinguishes "host generated then
     *  misclassified" (option 1 applies) from "host never generated" (it does not). */
    private final List<String> focusSeedLog = new ArrayList<>();

    /**
     * Retrieves the log of focus seed activities.
     *
     * @return a list of strings representing the focus seed log entries.
     */
    public List<String> getFocusSeedLog() { return focusSeedLog; }

    /**
     * For the focus pair, the sepset search's own trace: RB's blocking set, the def-collider
     * strip, and every candidate S actually tested (with any additions marked). Distinguishes a
     * separator that was merely unreached from one excluded from the search space by construction.
     * Synchronized because {@link #findIndependenceCheckRecursive} runs inside the parallel
     * lookahead; read it only after {@link #search()} returns.
     */
    private final List<String> focusSepsetLog = Collections.synchronizedList(new ArrayList<>());

    /**
     * Retrieves the log of focus separation sets.
     *
     * @return A list of strings representing the focus separation set log.
     */
    public List<String> getFocusSepsetLog() { return focusSepsetLog; }

    private static int bidirectedCount(Graph g) {
        int n = 0;
        for (Edge e : g.getEdges()) if (Edges.isBidirectedEdge(e)) n++;
        return n;
    }

    private boolean isFocus(Node x, Node y) {
        if (focusPair == null) return false;
        String k = x.getName().compareTo(y.getName()) <= 0
                ? x.getName() + "\u0000" + y.getName()
                : y.getName() + "\u0000" + x.getName();
        return k.equals(focusPair);
    }

    private void bump(String bucket) {
        focusTally.merge(bucket, 1, Integer::sum);
    }
    // -------------------------------------------------------------------------
    /**
     * Final-orientation provenance telemetry. {@code r0SepsetBacked}: R0 triples adjudicated by
     * a separator this search recorded (committed sepsets first, then sweep-discovered ones).
     * {@code r0CpdagBacked}: triples over pairs nonadjacent from the start, adjudicated by
     * copying the score-based CPDAG's collider verdict (the GFCI justification). {@code
     * r0TestFallback}: triples with NO recorded evidence, adjudicated by a fresh test-based
     * sepset search -- the noisy path; this should be zero or near-zero, since every
     * nonadjacent pair is either CPDAG-nonadjacent or was removed by a commit that recorded
     * its separator. A large fallback count means evidence is being lost somewhere upstream.
     * {@code r4SepsetBacked} / {@code r4TestFallback}: the same split for discriminating-path
     * (R4) verdicts, keyed by the path's endpoint pair.
     */
    private long r0SepsetBacked = 0, r0CpdagBacked = 0, r0TestFallback = 0,
            r4SepsetBacked = 0, r4TestFallback = 0;
    /**
     * Represents the maximum depth allowed for a recursive operation.
     * This variable is used to prevent excessive recursion,
     * which can lead to stack overflow errors.
     */
    private int recursiveDepth = -1;
    /**
     * Represents the radius for the RB (Recursive Backtracking) algorithm.
     * This variable is used to control the scope of the RB algorithm,
     * which can affect the performance and accuracy of the search.
     */
    private int rbRadius = -1;
    /**
     * Represents the maximum length of a discriminating path used in a specific algorithm or process.
     * A discriminating path can refer to a unique sequence of decisions or nodes evaluated during
     * execution. This value serves as a constraint or threshold and is initialized to -1, indicating
     * that no specific limit is set by default.
     */
    private int maxDiscriminatingPathLength = -1;
    /**
     * Test timout in milliseconds.
     */
    private long timeout = -1L;
    /**
     * A flag indicating whether the LV-Heuristic results should be returned. If false, edges will be removed via
     * further independence testing.
     */
    private boolean lvHeuristicOnly;

    /**
     * FCIT constructor. Initializes a new object of the FCIT search algorithm with the given IndependenceTest and Score
     * object.
     * <p>
     * In this constructor, we will use BOSS or GRaSP internally to infer an initial CPDAG and a valid order of the
     * variables. This is the default behavior of the FCIT algorithm.
     *
     * @param test  The IndependenceTest object to be used for testing independence between variables.
     * @param score The Score object to be used for scoring DAGs.
     * @throws NullPointerException if the score is null.
     */
    public FcitSlScoreCheck(IndependenceTest test, Score score) {
        if (test == null) {
            throw new NullPointerException();
        }

        if (score == null) {
            throw new NullPointerException();
        }

        this.test = test;
        this.score = score;

        if (test instanceof MsepTest) {
            this.startWith = START_WITH.GRASP;
        }
    }

    /**
     * Identifies and notes known unshielded colliders from the provided CPDAG (Completed Partially Directed Acyclic
     * Graph) by looking at its implied structure and transferring relevant colliders to the current PAG (Partial
     * Ancestral Graph). This process is justified in the GFCI (Generalized Fast Causal Inference) algorithm, as
     * described in the referenced research.
     *
     * @param best  A list of nodes representing the best-known nodes to be evaluated during the collider identification
     *              process.
     * @param graph The graph from which known unshielded colliders are identified and extracted.
     * @return A set of triples representing the known colliders identified in the provided CPDAG.
     */
    private static Set<Triple> noteInitialColliders(List<Node> best, Graph graph) {
        Set<Triple> initialColliders = new LinkedHashSet<>();

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
     * Stamp arrowheads into {@code f} from each still-adjacent node in {@code from} (edges become {@code <->}).
     */
    private static void makeCollider(Graph g, Node f, Set<Node> from) {
        for (Node w : from) {
            if (g.isAdjacentTo(w, f)) g.setEndpoint(w, f, Endpoint.ARROW);
        }
    }

    /**
     * True iff m is in S or is an ancestor of some node in S.
     */
    private static boolean ancestorInS(Graph g, Node m, Set<Node> S) {
        for (Node z : S) {
            if (m.equals(z) || g.paths().isAncestorOf(m, z)) return true;
        }
        return false;
    }

    /**
     * Canonical edge-token key (direction-aware for -&gt;, order-independent for &lt;-&gt;) for dedup.
     */
    private static String magKey(Graph g) {
        List<String> toks = new ArrayList<>();
        for (Edge e : g.getEdges()) {
            Node a = e.getNode1(), b = e.getNode2();
            Endpoint ea = e.getProximalEndpoint(a), eb = e.getDistalEndpoint(a);
            String u = a.getName(), v = b.getName();
            if (ea == Endpoint.TAIL && eb == Endpoint.ARROW) toks.add(u + ">" + v);
            else if (ea == Endpoint.ARROW && eb == Endpoint.TAIL) toks.add(v + ">" + u);
            else if (ea == Endpoint.ARROW && eb == Endpoint.ARROW)
                toks.add(u.compareTo(v) <= 0 ? u + "<>" + v : v + "<>" + u);
            else toks.add(u.compareTo(v) <= 0 ? u + "-" + v : v + "-" + u);
        }
        Collections.sort(toks);
        return String.join("|", toks);
    }

    /**
     * Canonical key for an endpoint SLOT: the mark at {@code at} on the (undirected) edge
     * {@code from}--{@code at}. Used by the closure-cover search's pin map; the edge part is
     * order-normalized so the slot is identified however the edge is named, while the
     * {@code @at} suffix distinguishes the edge's two slots.
     */
    private static String slotKey(Node from, Node at) {
        String u = from.getName(), v = at.getName();
        return (u.compareTo(v) <= 0 ? u + "\u0000" + v : v + "\u0000" + u) + "@" + at.getName();
    }

    /**
     * MAG-side analog of adjustForExtraSepsets over every recorded sepset. Idempotent:
     * re-stamping an existing collider is a no-op (the isDefCollider guard), so calling
     * this each commit removes any reliance on prior colliders persisting through the
     * PAG<->MAG round trip. Null sepsets and still-adjacent pairs are skipped.
     * If stamping the new leg colliders would cause an existing unshielded noncollider
     * to be converted on an unshielded collider, false is returned; otherwise true,
     *
     * @return true iff stamping yielded no new unshielded colliders.
     */
    private boolean stampLegColliders(Graph mag, Set<Node> b, Node x, Node y) throws InterruptedException {
        List<Node> common = mag.getAdjacentNodes(x);
        common.retainAll(mag.getAdjacentNodes(y));

        for (Node c : common) {
            if (b.contains(c)) continue;               // not a collider; leave it
            if (mag.isDefCollider(x, c, y)) continue;  // already x*->c<-*y

            for (Node d : mag.getAdjacentNodes(c)) {
                if (d == x) continue;
                if (d == y) continue;

                if (mag.getEndpoint(d, c) == Endpoint.ARROW) {
                    if (!mag.isAdjacentTo(d, x)) {
                        if (!mag.isDefCollider(d, c, x)) return false;
                    }

                    if (!mag.isAdjacentTo(d, y)) {
                        if (!mag.isDefCollider(d, c, y)) return false;
                    }
                }
            }

            mag.setEndpoint(x, c, Endpoint.ARROW);
            mag.setEndpoint(y, c, Endpoint.ARROW);
        }

        return true;
    }

    /**
     * Returns the independence test used in this search.
     *
     * @return the independence test
     */
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

        TetradLogger.getInstance().log("===Starting FCIT-SL===");

        Graph dag;
        List<Node> best;
        long start1 = System.currentTimeMillis();

        if (startWith != START_WITH.COMPLETE_GRAPH) {
            if (startWith == START_WITH.BOSS) {

                if (verbose) {
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

                if (verbose) {
                    TetradLogger.getInstance().log("BOSS took " + (stop - start) + " ms.");
                }

                if (verbose) {
                    TetradLogger.getInstance().log("Initializing PAG to BOSS CPDAG.");
                    TetradLogger.getInstance().log("Initializing scorer with BOSS best order.");
                }
            } else if (startWith == START_WITH.GRASP) {
                // We need to include the GRaSP option here so that we can run FCIT from Oracle.

                if (verbose) {
                    TetradLogger.getInstance().log("Running GRaSP...");
                }

                long start = MillisecondTimes.wallTimeMillis();

                Grasp grasp = getGraspSearch();
                grasp.setReplicatingGraph(this.replicatingGraph);
                best = grasp.bestOrder(nodes);
                dag = grasp.getGraph(false);

                long stop = MillisecondTimes.wallTimeMillis();

                if (verbose) {
                    TetradLogger.getInstance().log("GRaSP took " + (stop - start) + " ms.");
                }

                if (verbose) {
                    TetradLogger.getInstance().log("Initializing PAG to GRaSP CPDAG.");
                    TetradLogger.getInstance().log("Initializing scorer with GRaSP best order.");
                }
            } else if (startWith == START_WITH.SP) {

                if (verbose) {
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

                if (verbose) {
                    TetradLogger.getInstance().log("SP took " + (stop - start) + " ms.");
                }

                if (verbose) {
                    TetradLogger.getInstance().log("Initializing PAG to SP CPDAG.");
                    TetradLogger.getInstance().log("Initializing scorer with SP best order.");
                }
            } else {
                throw new IllegalArgumentException("That startWith option has not been configured: " + startWith);
            }
        } else {
            dag = GraphUtils.completeGraph(new EdgeListGraph(nodes));
            GraphUtils.reorientWithCircles(dag, verbose);
            best = dag.getNodes();
        }

        long stop1 = System.currentTimeMillis();

        long start2 = System.currentTimeMillis();

        TeyssierScorer scorer = new TeyssierScorer(test, score);
        scorer.score(best);
        scorer.setKnowledge(knowledge);
        scorer.setUseScore(!(score instanceof GraphScore));
        scorer.setUseRaskuttiUhler(score instanceof GraphScore);
        scorer.bookmark();

        if (verbose) {
            TetradLogger.getInstance().log("Initializing PAG to PAG of BOSS DAG.");
            TetradLogger.getInstance().log("Initializing scorer with BOSS best order.");
        }

        scorer.score(best);

        if (verbose) {
            TetradLogger.getInstance().log("Noting unshielded colliders from the CPDAG "
                    + "(consumed by the evidence-backed final orientation).");
        }

        // We make all latent variables at this point measured for the duration of the
        // procedure so that the latent structure search will work.
        List<Node> latents = new ArrayList<>();
        for (Node node : dag.getNodes()) {
            if (node.getNodeType() == NodeType.LATENT) {
                latents.add(node);
                node.setNodeType(NodeType.MEASURED);
            }
        }

        // The main procedure.
        Graph pag = GraphTransforms.dagToPag(dag, knowledge, excludeSelectionBias, recursiveDepth);

        if (replicatingGraph) {
            pag = new ReplicatingGraph(pag, new LagReplicationPolicy());
        }

        if (lvHeuristicOnly) {
            return pag;
        }

        this.interimPags.add(pag);

        this.initialColliders = noteInitialColliders(interimPags.getFirst().getNodes(), interimPags.getFirst());

        int round = 0;

        // The within-class sweep tries, per edge: the canonical Zhang MAG (Stage 1), the other
        // LEGs (Stage 2), and every fork-flip seed certified Markov-equivalent to the current
        // class (Stage 2b) -- all Step-Lemma-form witnesses. It is exhausted over ALL edges
        // before any escape is attempted: the Step Lemma's witness is existential over spurious
        // edges as well as representatives, so another edge's within-class witness takes
        // priority over this edge's out-of-class seed. Pass 3 (certified out-of-class seeds
        // only) runs when a full within-class sweep commits nothing, and only if
        // allowClassEscape; it commits at most one edge, then within-class sweeping resumes
        // against the new state.
        boolean changed;
        do {
            TetradLogger.getInstance().log("\nRound: " + (++round));
            changed = removeEdgesRecursively(excludeSelectionBias, false);
            if (!changed && allowClassEscape) {
                TetradLogger.getInstance().log("Within-class sweep committed nothing; attempting class-escape sweep.");
                changed = removeEdgesRecursively(excludeSelectionBias, true);
            }
        } while (changed);

        if (verbose) {
            TetradLogger.getInstance().log("Final orientation: cold R0 / R1-R10, evidence-backed "
                    + "(committed sepsets first, CPDAG colliders second, test only as fallback).");
        }

        // Re-derive the final orientation COLD -- the interim marks inherit GRaSP's DAG rendering
        // of latent confounding, so they must be wiped -- but adjudicate R0 and R4 from the
        // search's own RECORDED evidence rather than fresh test queries. At the oracle the two are
        // indistinguishable (MsepTest answers every query correctly, which is why PKE audits
        // cannot see the difference), but on finite samples a fresh per-triple sepset search has a
        // systematic collider bias: pairs typically have many valid separators, small ones are
        // found first, and noise admits sets excluding the middle node far more often than truth
        // warrants. Worse, a fresh search can CONTRADICT the very separator that justified the
        // deletion the triple rides on. So: for a pair removed by this search, the recorded
        // separator answers the collider question (self-consistency with the deletions by
        // construction); for a pair nonadjacent from the start, the score-based CPDAG's verdict
        // is copied (the GFCI justification -- this is what initialColliders was harvested for);
        // the raw test is consulted only for triples with no recorded evidence, which should be
        // rare (see the r0TestFallback telemetry). Note ruleR0 wipes to circles and applies
        // background knowledge internally, so no separate reorientWithCircles / fciOrientbk calls
        // are needed here.
        Graph finalPag = interimPags.getLast().copy();

        R0R4StrategyTestBased testStrategy = new R0R4StrategyTestBased(test);
        testStrategy.setDepth(depth);
        testStrategy.setKnowledge(knowledge);
        testStrategy.setVerbose(verbose);

        EvidenceBackedR0R4Strategy strategy = new EvidenceBackedR0R4Strategy(
                testStrategy, interimPags.getFirst(), initialColliders);

        FciOrient fciOrient = new FciOrient(strategy);
        fciOrient.setCompleteRuleSetUsed(completeRuleSetUsed);
        fciOrient.setMaxDiscriminatingPathLength(maxDiscriminatingPathLength);
        fciOrient.setVerbose(false);
        fciOrient.ruleR0(finalPag, new HashSet<>(), excludeSelectionBias);
        fciOrient.finalOrientation(finalPag);
        interimPags.addLast(finalPag);

        long stop2 = System.currentTimeMillis();

        // Revert nodes made latent to latent.
        for (Node node : latents) {
            node.setNodeType(NodeType.LATENT);
        }

        List<Edge> spurious = findSpuriousEdges(interimPags.getLast());
        TetradLogger.getInstance().log(spurious.isEmpty()
                ? "\nNo spurious edges remain."
                : "\n" + spurious.size() + " spurious edge(s) remain: " + spurious);

        NongenuineScan finalScan = findR4NongenuineEdge(interimPags.getLast());

        if (finalScan.edge() != null) {
            TetradLogger.getInstance().log("\nNon-genuine DDPs detected (R4).");
        } else if (finalScan.indeterminate()) {
            TetradLogger.getInstance().log(
                    "\nR4: Detection inconclusive: a blocking search timed out before a verdict. "
                            + "No non-genuine DDP was confirmed, but the graph cannot be certified phantom-free.");
        } else {
            TetradLogger.getInstance().log("\nNo non-genuine DDPs detected in the final graph.");
        }

        List<Triple> r0Suspect = findR0CollidersWithSeparableLeg(interimPags.getLast());
        TetradLogger.getInstance().log(r0Suspect.isEmpty()
                ? "\nNo R0 collider has a test-separable leg (collider-genuine on the R0 side)."
                : "\n" + r0Suspect.size() + " R0 collider(s) carry a separable leg; "
                + "Markovness not certified: " + r0Suspect);

        TetradLogger.getInstance().log("Legality checks: " + legalityChecks
                + " (" + ipRejects + " rejected by removed-pair inducing-path pre-check, "
                + "[ancestrality / non-maximality elsewhere]).");
        TetradLogger.getInstance().log("Commit gate: " + commitGate
                + (commitGate == CommitGate.DELETED_PAIR_BATTERY
                ? " (zMax=" + batteryZMax + "): " + batteryEvals + " evaluation(s), "
                + batteryStatementsTested + " entailed statement(s) tested, "
                + batteryRefusals + " refusal(s)."
                : "."));
        TetradLogger.getInstance().log("Commit provenance: " + zhangCommits
                + " Zhang-MAG (Stage 1), " + legCommits + " LEG (Stage 2), "
                + inClassFlipCommits + " in-class fork-flip (Stage 2b), "
                + escapeCommits + " class-escape (pass 3), "
                + otherRejects + " other-rejects"
                + (allowClassEscape ? "" : "; escape disabled."));

        if (useClassWalk) {
            TetradLogger.getInstance().log("Class walk (single mark changes): " + classWalkCommits
                    + " commit(s), " + classWalkVisited + " class member(s) visited, "
                    + classWalkTruncations + " truncation(s)"
                    + (classWalkBestFirst ? ", best-first on stamp deficit" : ", breadth-first")
                    + (classWalkMaxCandidates >= 0 ? ", cap " + classWalkMaxCandidates : ", uncapped")
                    + "."
                    + (classWalkTruncations > 0
                    ? " WARNING: a truncated walk did not exhaust the class, so any \"no representative"
                    + " hosted it\" above is inconclusive rather than a class boundary."
                    : ""));
        }
        TetradLogger.getInstance().log("Sepset search: " + additionPhaseRescues
                + " separator(s) found only by ADDING to RB's blocking set; "
                + blindFallbackAttempts + " orientation-blind retry/retries, "
                + blindFallbackRescues + " rescue(s)."
                + ((additionPhaseRescues > 0 || blindFallbackRescues > 0)
                ? " Nonzero means the interim orientations were hiding separators from the"
                + " removal-only search."
                : ""));
        TetradLogger.getInstance().log("Final-orientation provenance: R0 " + r0SepsetBacked
                + " sepset-backed, " + r0CpdagBacked + " CPDAG-backed, " + r0TestFallback
                + " test-fallback; R4 " + r4SepsetBacked + " sepset-backed, "
                + r4TestFallback + " test-fallback."
                + (r0TestFallback > 0
                ? " (Nonzero R0 test-fallback: some nonadjacent pair had no recorded evidence.)"
                : ""));

        if (useClosureCoverSearch) {
            TetradLogger.getInstance().log("Closure-cover search: " + closureCandidatesEmitted
                    + " candidate(s) emitted, " + closureInClassCommits + " in-class commit(s), "
                    + closureEscapeCommits + " escape commit(s), "
                    + closureStampPrunes + " stamp-pruned branch(es), "
                    + closureStampObstructions + " invariant stamp obstruction(s), "
                    + closureLongPathMisses + " beyond-bound active-path miss(es), "
                    + closureIllegalCands + " illegal candidate(s) at emission, "
                    + closureClassFiltered + " class-filtered candidate(s), "
                    + closureRelaxedExpansions + " relaxed-mark expansion(s), "
                    + closureRelaxedPasses + " full relaxed retry pass(es).");
            TetradLogger.getInstance().log("Closure-cover fallback: " + closureFallbackAttempts
                    + " pair(s) handed to the staged generator, " + closureFallbackCommits
                    + " committed there"
                    + (closureFallbackToStaged ? "." : " (fallback DISABLED; unaided reach)."));
        }

        TetradLogger.getInstance().log(scoreCheckSummary());
        TetradLogger.getInstance().log("\nFCIT-SL-Score-Check finished.");
        TetradLogger.getInstance().log("BOSS/GRaSP time: " + (stop1 - start1) + " ms.");
        TetradLogger.getInstance().log("Collider orientation and _edge removal time: " + (stop2 - start2) + " ms.");
        TetradLogger.getInstance().log("Total time: " + (stop2 - start1) + " ms.");
        TetradLogger.getInstance().log(checkCounter.report());

        CachedIndependenceQueries cache = findCache();
        if (cache != null) {
            TetradLogger.getInstance().log(cache.cacheReport());
        }

        return GraphUtils.replaceNodes(interimPags.getLast(), nodes);
    }

    // ================= MAG SCORE CHECK helpers =================

    /**
     * The MAG SCORE CHECK prong, shared by {@link #hostOrNull} (the staged and class-walk generators) and by
     * {@link #closureGateAndCommit} (the closure-cover generator), so that every candidate the search adjudicates
     * is judged by the same rule regardless of which generator produced it.
     * <p>
     * The candidate is refused when its Gaussian MAG BIC is strictly below that of the currently committed model.
     * On acceptance the candidate's BIC becomes the new reference: both call sites commit unconditionally once
     * this returns false, so the update is safe here.
     * <p>
     * FAIL-SOFT. A score that cannot be computed -- no covariance, a selection edge RICF cannot fit, a singular
     * covariance, a non-finite likelihood -- is never read as a refusal; the candidate is left to the structural
     * prongs, which are exactly FcitSl's. Reading "no score" as "reject" would silently disable all deletion on
     * any dataset RICF cannot fit.
     *
     * @param candidateMag The legal MAG carrying the deletion.
     * @param edgeForLog   A label for the edge under consideration, for logging only.
     * @return True if the candidate is refused on score grounds.
     */
    private boolean scoreCheckRefuses(Graph candidateMag, Object edgeForLog) {
        if (!useScoreCheck) {
            return false;
        }

        Double candidateBic = magBic(candidateMag);

        if (candidateBic == null) {
            tallyScoreUnavailable++;

            if (verbose) {
                TetradLogger.getInstance().log("\tScore check unavailable for " + edgeForLog
                        + " (falling back to the structural gates alone).");
            }

            return false;
        }

        if (currentMagBic == null) {
            currentMagBic = committedMagBic();
        }

        if (currentMagBic == null) {
            return false;   // no reference to compare against; defer to the structural gates
        }

        tallyScoreChecked++;

        if (candidateBic < currentMagBic) {
            double gap = currentMagBic - candidateBic;
            tallyScoreVetoes++;
            scoreVetoGaps.add(gap);

            if (verbose) {
                TetradLogger.getInstance().log("\tTried removing " + edgeForLog
                        + ", legal MAG but SCORE CHECK failed (refused): MAG BIC " + candidateBic
                        + " < current " + currentMagBic + "; gap = " + gap + ".");
            }

            return true;
        }

        tallyScoreAccepted++;
        currentMagBic = candidateBic;   // this candidate is about to be committed by the caller
        return false;
    }

    /**
     * Sets whether the MAG score check is applied. With this false the class reproduces {@link FcitSl} exactly.
     *
     * @param useScoreCheck True to apply the check.
     */
    public void setUseScoreCheck(boolean useScoreCheck) {
        this.useScoreCheck = useScoreCheck;
    }

    /**
     * Sets the penalty discount c used by the MAG score check, in bic = 2 * logLik - c * k * ln(n). Default 1,
     * the classical BIC comparison. Larger values make the check more willing to accept deletions (a deletion
     * saves c * ln(n) of penalty); smaller values make it more conservative. Independent of the penalty discount
     * inside the initializer score.
     *
     * @param scoreCheckPenaltyDiscount The penalty discount; must be positive.
     */
    public void setScoreCheckPenaltyDiscount(double scoreCheckPenaltyDiscount) {
        if (scoreCheckPenaltyDiscount <= 0) {
            throw new IllegalArgumentException("Score check penalty discount must be positive: "
                                               + scoreCheckPenaltyDiscount);
        }

        this.scoreCheckPenaltyDiscount = scoreCheckPenaltyDiscount;
    }

    /**
     * Sets the covariance matrix used by the MAG score check explicitly. If not set, it is resolved from the test
     * or the score.
     *
     * @param covarianceMatrix The covariance matrix.
     */
    public void setCovarianceMatrix(ICovarianceMatrix covarianceMatrix) {
        this.covarianceMatrix = covarianceMatrix;
    }

    /**
     * The covariance matrix for RICF, resolved once from the explicit setting, then the test, then the score.
     * Returns null when none is available, which puts the score check into its fail-soft mode.
     */
    private @Nullable ICovarianceMatrix resolveCovariance() {
        if (covarianceMatrix != null) {
            return covarianceMatrix;
        }

        try {
            covarianceMatrix = test.getCov();
        } catch (Exception e) {
            covarianceMatrix = null;
        }

        if (covarianceMatrix == null) {
            try {
                if (score instanceof edu.cmu.tetrad.search.score.SemBicScore semBic) {
                    covarianceMatrix = semBic.getCovariances();
                }
            } catch (Exception e) {
                covarianceMatrix = null;
            }
        }

        return covarianceMatrix;
    }

    /**
     * The Gaussian BIC of a MAG, 2 * logLik - c * k * ln(n), with the log-likelihood from RICF and k = p error
     * variances plus one parameter per (directed or bidirected) edge. Returns null -- never throws, and never
     * returns a number for a model RICF cannot fit -- when the score is unavailable, which the caller reads as
     * "defer to the structural gates".
     *
     * @param mag The MAG to score. Must be a MAG; this is guaranteed at the one call site.
     * @return The BIC, or null if it cannot be computed.
     */
    private @Nullable Double magBic(Graph mag) {
        ICovarianceMatrix cov = resolveCovariance();

        if (cov == null) {
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
            logLik = new RicfEjml().ricf(mag, cov).getLogLik();
        } catch (Exception e) {
            return null;
        }

        if (Double.isNaN(logLik) || Double.isInfinite(logLik)) {
            return null;
        }

        int p = cov.getDimension();
        int k = p + numEdges;
        int n = cov.getSampleSize();

        return 2.0 * logLik - scoreCheckPenaltyDiscount * k * Math.log(n);
    }

    /**
     * The BIC of the currently committed model, obtained from the Zhang MAG of the current interim PAG. Because
     * the Gaussian BIC is invariant across a Markov equivalence class, any representative gives the same value,
     * so which representative the class walk has reached does not matter.
     */
    private @Nullable Double committedMagBic() {
        if (interimPags.isEmpty()) {
            return null;
        }

        try {
            return magBic(GraphTransforms.zhangMagFromPag(interimPags.getLast()));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * One-line, unconditional per-run summary of what the score check did.
     */
    private String scoreCheckSummary() {
        StringBuilder sb = new StringBuilder("FCIT-SL-Score-Check summary: score check ")
                .append(useScoreCheck ? "ON" : "OFF")
                .append(" (c = ").append(scoreCheckPenaltyDiscount)
                .append("); candidates scored = ").append(tallyScoreChecked)
                .append(", accepted = ").append(tallyScoreAccepted)
                .append(", refused = ").append(tallyScoreVetoes)
                .append(", score unavailable = ").append(tallyScoreUnavailable).append(".");

        if (!scoreVetoGaps.isEmpty()) {
            List<Double> gaps = new ArrayList<>(scoreVetoGaps);
            Collections.sort(gaps);
            double median = gaps.size() % 2 == 1
                    ? gaps.get(gaps.size() / 2)
                    : (gaps.get(gaps.size() / 2 - 1) + gaps.get(gaps.size() / 2)) / 2.0;
            sb.append(" Refusal BIC gaps: min = ").append(gaps.get(0))
                    .append(", median = ").append(median)
                    .append(", max = ").append(gaps.get(gaps.size() - 1)).append(".");
        }

        return sb.toString();
    }

    // ================= end MAG SCORE CHECK helpers =================

    private NongenuineScan findR4NongenuineEdge(Graph pag) throws InterruptedException {
        Set<DiscriminatingPath> ddps = FciOrient.listDiscriminatingPaths(pag, maxDiscriminatingPathLength, true);

        // Within one pass, the same pair can appear as a leg/chord of several
        // discriminating paths. blockPathsRecursively is the expensive call, so
        // memoize its verdict per unordered pair for the duration of this pass.
        // (The PAG is not mutated during findNongenuineEdge, so the verdict is stable.)
        Map<Set<Node>, LegVerdict> verdictCache = new LinkedHashMap<>();

        // Match findIndependenceCheckRecursive's convention: unlimited stays
        // unlimited (no Long.MAX_VALUE + now overflow), otherwise a per-pass
        // budget of `timeout` ms.
        final long deadlineMs = (timeout < 0L)
                ? Long.MAX_VALUE
                : System.currentTimeMillis() + timeout;

        boolean sawIndeterminate = false;

        for (DiscriminatingPath dd : ddps) {
            List<Node> colliderPath = dd.getColliderPath();

            List<Node> spine = new ArrayList<>(colliderPath);
            spine.addFirst(dd.getX());
            spine.addLast(dd.getY());

            // Path edges: consecutive spine vertices.
            for (int i = 0; i < spine.size() - 1; i++) {
                Node m = spine.get(i);
                Node n = spine.get(i + 1);
                LegVerdict v = legVerdict(m, n, deadlineMs, verdictCache);
                if (v == LegVerdict.SPURIOUS) {
                    return new NongenuineScan(pag.getEdge(m, n), sawIndeterminate);
                }
                if (v == LegVerdict.INDETERMINATE) {
                    sawIndeterminate = true;
                }
            }

            // Chords v_i *-> c: each interior collider to the far endpoint y.
            Node y = dd.getY();
            for (Node v0 : colliderPath) {
                LegVerdict v = legVerdict(v0, y, deadlineMs, verdictCache);
                if (v == LegVerdict.SPURIOUS) {
                    return new NongenuineScan(pag.getEdge(v0, y), sawIndeterminate);
                }
                if (v == LegVerdict.INDETERMINATE) {
                    sawIndeterminate = true;
                }
            }
        }

        // No confirmed-spurious leg. If any pair was indeterminate, we cannot
        // claim the graph is phantom-free; report it.
        return new NongenuineScan(null, sawIndeterminate);
    }

    private List<Edge> findSpuriousEdges(Graph pag) throws InterruptedException {
        List<Edge> spuriousEdges = new ArrayList<>();

        for (Edge edge : pag.getEdges()) {
            Node m = edge.getNode1();
            Node n = edge.getNode2();

            if (foundSepsets.get(Set.of(m, n)) != null) {
                spuriousEdges.add(edge);
            }
        }

        return spuriousEdges;
    }

    /**
     * Identifies R0 colliders in a given PAG (Partial Ancestral Graph) where one or both legs are
     * separable. An R0 collider is defined as a triple of nodes (x, c, y) such that c is a collider
     * between x and y in the PAG but has certain structural properties. This method flags such
     * triples based on separation criteria and graph adjacency.
     *
     * @param pag the Partial Ancestral Graph (PAG) in which to search for R0 colliders
     *            with separable legs.
     * @return a list of triples representing the flagged R0 colliders where at least one of the legs
     * is separable in the PAG.
     * @throws InterruptedException if the execution is interrupted while performing the analysis.
     */
    private List<Triple> findR0CollidersWithSeparableLeg(Graph pag) throws InterruptedException {
        Set<Set<Node>> separable = new LinkedHashSet<>();
        for (Edge e : findSpuriousEdges(pag)) {
            separable.add(Set.of(e.getNode1(), e.getNode2()));
        }

        List<Triple> flagged = new ArrayList<>();
        Set<Triple> seen = new LinkedHashSet<>();

        for (Set<Node> pair : sepsets.keySet()) {
            List<Node> arr = new ArrayList<>(pair);
            if (arr.size() != 2) continue;
            Node x = arr.get(0);
            Node y = arr.get(1);

            Set<Node> s = sepsets.get(x, y);
            if (s == null) continue;
            if (interimPags.getLast().isAdjacentTo(x, y)) continue;       // R0 fires only once x-y is gone

            List<Node> common = interimPags.getLast().getAdjacentNodes(x);
            common.retainAll(interimPags.getLast().getAdjacentNodes(y));

            for (Node c : common) {
                if (s.contains(c)) continue;                // c in sepset: non-collider, not R0
                if (!interimPags.getLast().isDefCollider(x, c, y)) continue;  // collider not present in final PAG
                Triple t = new Triple(x, c, y);
                if (!seen.add(t)) continue;
                if (separable.contains(Set.of(x, c)) || separable.contains(Set.of(c, y))) {
                    flagged.add(t);
                }
            }
        }

        return flagged;
    }

    /**
     * True iff {@code v} carries at least one circle endpoint on some incident
     * edge -- the ambiguity criterion for NF-candidate harvesting.
     */
    private static boolean hasCircleEndpoint(Graph g, Node v) {
        for (Node w : g.getAdjacentNodes(v)) {
            if (g.getEndpoint(v, w) == Endpoint.CIRCLE || g.getEndpoint(w, v) == Endpoint.CIRCLE) {
                return true;
            }
        }
        return false;
    }

    private LegVerdict legVerdict(Node m, Node n, long deadlineMs, Map<Set<Node>, LegVerdict> cache) throws InterruptedException {
        Edge edge = interimPags.getLast().getEdge(m, n);
        if (edge == null) {
            return LegVerdict.NOT_SPURIOUS;
        }

        // Cheapest signal first: a recorded separator means the pair is already
        // known independent, so the adjacency is spurious. No blocking needed.
        // Sound positive verdicts only. A committed separator means the pair was
        // already separated.
        if (sepsets.get(m, n) != null) {
            return LegVerdict.SPURIOUS;
        }

        Set<Node> key = Set.of(m, n);

        // foundSepsets is never rolled back on a reverted removal, so a still-present
        // edge with an entry is a test-confirmed separable-but-stuck adjacency (a
        // deadlock survivor). RB's blocking set, by contrast, is only a *candidate*
        // in skip-direct mode, so it is never promoted to a positive verdict here.
        if (foundSepsets.get(key) != null) {
            return LegVerdict.SPURIOUS;
        }

        LegVerdict cached = cache.get(key);
        if (cached != null) {
            return cached;
        }

        // RB is consulted only to flag a timed-out search as inconclusive.
        RecursiveBlocking.BlockingResult result = RecursiveBlocking.blockPathsRecursively(
                interimPags.getLast(), m, n, Set.of(), Set.of(), recursiveDepth, depth, rbRadius, 1, true,
                deadlineMs);

        LegVerdict v = result.indeterminate()
                ? LegVerdict.INDETERMINATE
                : LegVerdict.NOT_SPURIOUS;
        cache.put(key, v);
        return v;
    }

    /**
     * Configures and returns a new instance of PermutationSearch using the BOSS algorithm. The method initializes the
     * BOSS algorithm with parameters such as the score function, verbosity, number of starts, number of threads, and
     * whether to use the BES algorithm. The constructed PermutationSearch is further configured with the existing
     * knowledge.
     *
     * @return A fully configured PermutationSearch instance using the BOSS algorithm.
     */
    private @NotNull PermutationSearch getBossSearch() {
        Boss subAlg = new Boss(score);
        subAlg.setUseBes(useBes);
        subAlg.setNumStarts(numStarts);
        subAlg.setNumThreads(Runtime.getRuntime().availableProcessors());
        subAlg.setVerbose(verbose);
        PermutationSearch alg = new PermutationSearch(subAlg);
        alg.setKnowledge(knowledge);
        return alg;
    }

    /**
     * Parameterizes and returns a new GRaSP search.
     *
     * @return A new GRaSP search.
     */
    private @NotNull Grasp getGraspSearch() {
        Grasp grasp = new Grasp(test, score);

        grasp.setDepth(3);
        grasp.setUncoveredDepth(1);
        grasp.setNonSingularDepth(1);
        grasp.setOrdered(true);
        grasp.setUseScore(true);
        grasp.setUseRaskuttiUhler(false);
        grasp.setUseDataOrder(useDataOrder);
        grasp.setAllowInternalRandomness(false);
        grasp.setVerbose(verbose);
        grasp.setNumStarts(numStarts);
        grasp.setKnowledge(this.knowledge);

        return grasp;
    }

    /**
     * Attempts to remove additional edges from the current PAG by exploiting discriminating paths that could not be
     * oriented by the final FCI orientation rules. For each candidate edge, the method:
     * <p>
     * 1. Gathers unresolved discriminating paths involving the edge. 2. Uses recursive blocking to propose conditioning
     * sets that would separate the endpoints. 3. Runs the independence test on those candidate sets. 4. If independence
     * is found, tries to remove the edge and re-orient the graph accordingly.
     * <p>
     * If {@code guaranteePag} is true, removals that would yield an illegal MAG are reverted; otherwise, illegal PAG
     * states may persist. Verbose logging records each attempted removal and orientation.
     *
     * @param escape whether tryToModifyGraph may use fork-flip (out-of-class) seeds; false for
     *               the within-class passes (Stage 1: Zhang MAG; Stage 2: LEGs), true only for
     *               the state-level pass 3 run after a full within-class sweep commits nothing
     * @return true if at least one edge was removed, false otherwise
     */
    private boolean removeEdgesRecursively(boolean excludeSelectionBias, boolean escape)
            throws InterruptedException {

        // This version does parallel lookahead, so that the only time graph rebuilding is done is when
        // edge removals are attempted.

        boolean changedThisSweep = false;

        // Ordered snapshot of the edges for this sweep. `from` is the scan position;
        // we never go back before it, so each edge is searched at most once per sweep.
        List<Edge> edgeList = new ArrayList<>(this.interimPags.getLast().getEdges());
        edgeList.sort(Comparator
                .comparing((Edge e) -> {
                    String a = e.getNode1().getName(), b = e.getNode2().getName();
                    return a.compareTo(b) <= 0 ? a + '\u0000' + b : b + '\u0000' + a;
                }));

        int from = 0;

        while (from < edgeList.size()) {
            final int start = from;

            // Parallel search over the tail [start, end). findFirst on an ordered
            // parallel stream returns the LOWEST-index removable edge deterministically,
            // independent of which thread finishes first. Each search reads the live
            // PAG; nothing mutates it during this phase, so concurrent reads are safe.
            // No short-circuit: evaluate the WHOLE tail. findFirst let branches already in flight
            // finish, so which losing edges left a cached separator behind depended on thread
            // timing -- and since a pair can have several valid separators (V4--V5 yields {V3}
            // against the pre-deletion PAG and {} after), that made the whole search
            // nondeterministic. Evaluating all of them and recording in index order keeps the
            // reach the cache provides while making it a function of the sweep, not the scheduler.
            List<RemovalHit> hits =
                    java.util.stream.IntStream.range(start, edgeList.size())
                            .parallel()
                            .mapToObj(i -> {
                                Edge e = edgeList.get(i);
                                Node x = e.getNode1();
                                Node y = e.getNode2();

                                // Eligibility filters (read-only).
                                if (!this.interimPags.getLast().isAdjacentTo(x, y)) return null;
                                if (sepsets.get(x, y) != null) return null;
                                if (!(knowledge == null || !Edges.isDirectedEdge(e)
                                        || !knowledge.isForbidden(x.getName(), y.getName()))) return null;

                                try {
                                    IndependenceCheck check = findIndependenceCheckRecursive(e);
                                    if (check == null) return null;
                                    return new RemovalHit(i, e, check.cond(), check.pValue());
                                } catch (InterruptedException ie) {
                                    Thread.currentThread().interrupt();
                                    throw new RuntimeException(ie);
                                }
                            })
                            .filter(Objects::nonNull)
                            .sorted(Comparator.comparingInt(RemovalHit::index))
                            .toList();

            // Record EVERY confirmed separator, in index order. X _||_ Y | S is a fact about the
            // data, so keeping it is sound whichever edge won; discarding the losers' facts costs
            // reach, because the separator search can succeed against one interim PAG and fail
            // against a later one.
            for (RemovalHit rh : hits) {
                Set<Node> k = Set.of(rh.edge().getNode1(), rh.edge().getNode2());
                foundSepsets.putIfAbsent(k, rh.cond());
                if (!Double.isNaN(rh.pValue())) foundPValues.putIfAbsent(k, rh.pValue());
            }

            if (hits.isEmpty()) {
                break;  // no removable edge in the tail — sweep complete
            }

            RemovalHit h = hits.get(0);
            Node x = h.edge.getNode1();
            Node y = h.edge.getNode2();

            // Commit against the live PAG using the sepset found during the search —
            // no re-search needed, since the winner was searched against the current PAG.
            // Same gates either way; only the candidate generator differs.
            boolean didChange = useClosureCoverSearch
                    ? tryToModifyGraphClosure(x, y, h.cond, h.pValue(), excludeSelectionBias, escape)
                    : tryToModifyGraph(x, y, h.cond, h.pValue(), excludeSelectionBias, escape);

            if (didChange) {
                changedThisSweep = true;

                // In escape mode, commit at most ONE edge per sweep and hand control back to
                // the within-class passes: the escape is a state-level fallback, and after any
                // commit the new state deserves a fresh Stage-1/Stage-2 sweep before further
                // escapes.
                if (escape) {
                    return true;
                }

                // PAG changed; resume scanning AFTER the removed edge against the new graph.
                from = h.index + 1;
            } else {
                // Reverted: PAG is unchanged. Speculative results for later edges would
                // still be valid, but findFirst already discarded them; cheapest correct
                // thing is to advance past this edge and re-search the tail. The tail
                // search is against the same (unchanged) PAG, so results are consistent.
                from = h.index + 1;
            }
        }

        return changedThisSweep;
    }

    private IndependenceCheck findIndependenceCheckRecursive(Edge edge) throws InterruptedException {
        final Node x = edge.getNode1();
        final Node y = edge.getNode2();

        Set<Node> known = sepsets.get(x, y);
        if (known != null) {
            return new IndependenceCheck(edge, known, Double.NaN);
        }

        // Reuse a separator already found for this pair in an earlier sweep. The
        // independence is a data fact, invariant across rounds, so re-searching the
        // (evolved) PAG would only risk returning a *different* valid set — which is
        // exactly the cross-round inconsistency. tryToModifyGraph still judges PAG
        // legality; if it reverts, the edge is retried next round with the same set.
        Set<Node> cached = foundSepsets.get(Set.of(x, y));
        if (cached != null) {
            Double cachedP = foundPValues.get(Set.of(x, y));
            return new IndependenceCheck(edge, cached, cachedP == null ? Double.NaN : cachedP);
        }

        // Per-edge deadline: at most `timeout` ms spent separating THIS edge,
        // shared across every RB call below. Unlimited stays unlimited without
        // relying on Long.MAX_VALUE + now overflowing to a negative.
        final long deadline = (timeout < 0L)
                ? Long.MAX_VALUE
                : System.currentTimeMillis() + timeout;

        // Candidate generation stays on the PAG so the NF/ambiguity search keeps its
        // full toggle set; MAG-awareness lives only at the commit step. (This also drops
        // the per-edge MAG rebuild, which was identical for every edge in the sweep.)
        RecursiveBlocking.BlockingResult b0result = RecursiveBlocking.blockPathsRecursively(
                interimPags.getLast(), x, y, Set.of(), Set.of(), recursiveDepth, depth, rbRadius, 1, true,
                deadline);

        // NF candidates: ambiguous nodes -- those carrying at least one circle
        // endpoint -- drawn from the seed run's blocking set UNIONED WITH the
        // common neighbours of the pair.
        //
        // The union is load-bearing. Harvesting from the seed run alone leaves
        // nfCand EMPTY whenever that run returns no blocking set, which happens
        // precisely when a wrong interim mark forces conditioning that activates
        // a collider route to y. The outer layer then collapses to NF = {} and
        // the live view contributes nothing, leaving only the blind fallback --
        // and the blind view is ACTIVATION-BLIND: with every mark a circle it
        // displays no colliders, so its fixed point never sees a collider opened
        // by a node it just conditioned on and never walks to the blocker that
        // activation requires. Observed in FCIT at six variables: a spurious pair
        // whose unique separator had a member outside the common neighbourhood,
        // reachable only on the live view and only after an ambiguous common
        // neighbour was withheld from traversal.
        Graph pagNow = interimPags.getLast();

        Set<Node> nfCandSet = new LinkedHashSet<>();
        if (!b0result.indeterminate() && b0result.blockingSet() != null) {
            for (Node v : b0result.blockingSet()) {
                if (hasCircleEndpoint(pagNow, v)) {
                    nfCandSet.add(v);
                }
            }
        }

        List<Node> commonNf = new ArrayList<>(pagNow.getAdjacentNodes(x));
        commonNf.retainAll(pagNow.getAdjacentNodes(y));
        for (Node c : commonNf) {
            if (hasCircleEndpoint(pagNow, c)) {
                nfCandSet.add(c);
            }
        }

        List<Node> nfCand = new ArrayList<>(nfCandSet);
        nfCand.sort(Comparator.comparing(Node::getName));


        // Enumerate subsets of the "not-followed" set NF ⊆ nfCand
        SublistGenerator nfGen = new SublistGenerator(nfCand.size(), nfCand.size());
        int[] nfChoice;
        while ((nfChoice = nfGen.next()) != null) {
            if (System.currentTimeMillis() > deadline) return null; // per-edge budget exhausted
            if (!this.interimPags.getLast().isAdjacentTo(x, y)) break; // edge already removed upstream

            Set<Node> notFollowed = GraphUtils.asSet(nfChoice, nfCand);
            RecursiveBlocking.BlockingResult result = null;

            if (this.depth < 0) {
                result = RecursiveBlocking.blockPathsRecursively(
                        interimPags.getLast(), x, y, Set.of(), notFollowed, recursiveDepth, depth, rbRadius, 1, true,
                        deadline);

            } else {
                int depth = 0;
                int maxDepth = this.depth;

                do {
                    depth++;

                    if (depth > maxDepth) break;

                    result = RecursiveBlocking.blockPathsRecursively(
                            interimPags.getLast(), x, y, Set.of(), notFollowed, recursiveDepth, depth, rbRadius, 1, true,
                            deadline);
                } while (result.indeterminate());
            }

            if (result == null || result.indeterminate()) {
                continue;
            }

            Set<Node> B = result.blockingSet();

            if (B == null) {
                continue; // No separating set possible for this NF; try another NF
            }

            // EVERY member of the blocking set is a removal candidate, not just the common
            // neighbours of x and y. RB returns ONE blocking set, chosen against a graph whose
            // circles hide collider status; a node it blocks may be a collider in the truth, or
            // a DESCENDANT of one, in which case conditioning on it OPENS a path and no superset
            // of it can separate. Such a node need not be adjacent to both endpoints -- in the
            // V1--V5 example B = {V2, V3} with V2 adjacent to V1 but not V5, a collider and a
            // descendant of the collider V4 -- so restricting removals to common neighbours left
            // the true separator {V3} untestable and the spurious edge was never separated.
            // Subsets are enumerated smallest-removal-first and common neighbours are listed
            // first within each size, so the previous search order is still reached first.
            List<Node> common = this.interimPags.getLast().getAdjacentNodes(x);
            common.retainAll(this.interimPags.getLast().getAdjacentNodes(y));

            // A common neighbour that is ALREADY a definite collider between x and y can never
            // belong to a separator (conditioning on it opens x*->c<-*y), so drop it outright
            // instead of leaving it to the subset search. (Previously these were excluded from
            // the removal candidates, i.e. never removable -- the opposite of the name's intent.)
            // Drop a common neighbour that is a definite collider between x and y
            // ONLY when the reading does not depend on a mark that could itself be
            // an artifact of the edge under test. A displaced unsound arrowhead
            // can land on all-real edges and make an all-real triple a false
            // definite collider; excluding the node then removes the true
            // separator's member from every candidate on this view, and since the
            // same false collider blocks the proposing walk, no NF choice recovers
            // it. (Observed in FCIT at six variables, twice.) Requiring both legs
            // to be free of circles keeps the optimization on triples the search
            // has actually settled and declines it elsewhere; excluded-no-longer
            // nodes are simply subject to the ordinary removal enumeration.
            Set<Node> definitelyRemove = new LinkedHashSet<>();
            Graph pagHere = this.interimPags.getLast();
            for (Node c : common) {
                if (!pagHere.isDefCollider(x, c, y)) continue;

                boolean legsSettled =
                        pagHere.getEndpoint(c, x) != Endpoint.CIRCLE
                                && pagHere.getEndpoint(c, y) != Endpoint.CIRCLE;

                if (legsSettled) {
                    definitelyRemove.add(c);
                }
            }

            Set<Node> B0 = new LinkedHashSet<>(B);
            B0.removeAll(definitelyRemove);

            if (isFocus(x, y)) {
                focusSepsetLog.add("NF pass: B=" + B + "  definitelyRemove=" + definitelyRemove
                        + "  -> B0=" + new LinkedHashSet<>(B0));
            }

            IndependenceCheck hit = trySubsetsAround(edge, x, y, B0, common, deadline);
            if (hit != null) return hit;
        }

        // Orientation-blind fallback. Every NF pass above searched subsets drawn from a blocking
        // set RB computed against the CURRENT orientations, and those orientations can be wrong in
        // exactly the way that hides the separator: a true non-collider that the interim PAG shows
        // as a collider makes RB treat its path as already blocked, so the node never enters B, and
        // an add/remove search around B cannot recover what RB never proposed. Observed at six
        // variables (model aa..aaaaatatat..atatcacacc): the spurious V2--V6 edge shields the
        // V2-V3-V6 triple, the shielded triple is oriented V2<->V3 where G* has V3-->V2, that
        // reading makes V3 a definite collider between V2 and V6, and the separator {V3,V4,V5} --
        // which GRaSP itself found -- was never testable. The edge protected itself.
        // Re-running RB on the SKELETON (every mark a circle) makes no triple a collider, so no
        // path counts as pre-blocked and the blocking set is orientation-independent. Fires only
        // after the oriented passes have all failed, so it costs nothing on the common path.
        if (orientationBlindFallback && this.interimPags.getLast().isAdjacentTo(x, y)
                && System.currentTimeMillis() <= deadline) {
            blindFallbackAttempts++;

            Graph blind = new EdgeListGraph(this.interimPags.getLast());
            for (Edge e : new ArrayList<>(blind.getEdges())) {
                blind.setEndpoint(e.getNode1(), e.getNode2(), Endpoint.CIRCLE);
                blind.setEndpoint(e.getNode2(), e.getNode1(), Endpoint.CIRCLE);
            }

            RecursiveBlocking.BlockingResult blindResult = RecursiveBlocking.blockPathsRecursively(
                    blind, x, y, Set.of(), Set.of(), recursiveDepth, depth, rbRadius, 1, true, deadline,
                    RecursiveBlocking.Strategy.RECURSIVE);

            if (blindResult != null && !blindResult.indeterminate() && blindResult.blockingSet() != null) {
                List<Node> common = this.interimPags.getLast().getAdjacentNodes(x);
                common.retainAll(this.interimPags.getLast().getAdjacentNodes(y));

                Set<Node> blindBase = new LinkedHashSet<>(blindResult.blockingSet());

                if (isFocus(x, y)) {
                    focusSepsetLog.add("BLIND pass (skeleton, no mark is a collider): B="
                            + blindResult.blockingSet());
                }

                IndependenceCheck hit = trySubsetsAround(edge, x, y, blindBase, common, deadline);
                if (hit != null) {
                    blindFallbackRescues++;
                    return hit;
                }
            }
        }

        return null;
    }

    /**
     * Tests candidate separators drawn from {@code base} and the common neighbours of {@code x}
     * and {@code y}, in two phases.
     * <p>
     * Phase 0 (additions = none) enumerates subsets of {@code base} smallest-removal-first, which
     * is byte-for-byte the search this method replaced, so anything the old code found is still
     * found first and in the same order. Phase 1 then ADDS common neighbours that {@code base}
     * omits -- the nodes a wrong collider reading kept out of the blocking set, or that
     * {@code definitelyRemove} stripped on the strength of a def-collider status that is only as
     * trustworthy as the current orientations.
     * <p>
     * Not complete: a node that is neither in {@code base} nor a common neighbour of the pair is
     * still unreachable, so a null return remains "not found", never "does not exist".
     *
     * @return an independence check if some candidate separates, else null.
     */
    private IndependenceCheck trySubsetsAround(Edge edge, Node x, Node y, Set<Node> base,
                                               List<Node> common, long deadline)
            throws InterruptedException {

        // Canonical order: common neighbours before the rest (preserving the
        // historical search order), name-sorted within each block, so the
        // recorded separator is a function of the search state and not of
        // adjacency storage order.
        Comparator<Node> byName = Comparator.comparing(Node::getName);

        List<Node> commonSorted = new ArrayList<>(common);
        commonSorted.sort(byName);

        List<Node> inCommon = new ArrayList<>();
        List<Node> notInCommon = new ArrayList<>();
        for (Node v : base) {
            if (commonSorted.contains(v)) inCommon.add(v);
            else notInCommon.add(v);
        }
        inCommon.sort(byName);
        notInCommon.sort(byName);

        List<Node> removalCandidates = new ArrayList<>(inCommon);
        removalCandidates.addAll(notInCommon);

        List<Node> addCandidates = new ArrayList<>();
        for (Node c : commonSorted) if (!base.contains(c)) addCandidates.add(c);

        int maxRemove = (this.maxBlockingSetRemovals < 0)
                ? removalCandidates.size()
                : Math.min(this.maxBlockingSetRemovals, removalCandidates.size());

        int maxAdd = (this.maxBlockingSetAdditions < 0)
                ? addCandidates.size()
                : Math.min(this.maxBlockingSetAdditions, addCandidates.size());

        SublistGenerator addGen = new SublistGenerator(addCandidates.size(), maxAdd);
        int[] addChoice;

        while ((addChoice = addGen.next()) != null) {
            if (System.currentTimeMillis() > deadline) return null;
            if (!this.interimPags.getLast().isAdjacentTo(x, y)) return null;

            Set<Node> A = GraphUtils.asSet(addChoice, addCandidates);

            SublistGenerator cGen = new SublistGenerator(removalCandidates.size(), maxRemove);
            int[] cChoice;

            while ((cChoice = cGen.next()) != null) {
                if (System.currentTimeMillis() > deadline) return null; // per-edge budget exhausted
                if (!this.interimPags.getLast().isAdjacentTo(x, y)) return null;

                Set<Node> S = new LinkedHashSet<>(base);
                Set<Node> C = GraphUtils.asSet(cChoice, removalCandidates);

                S.removeAll(C);
                S.addAll(A);

                if (this.depth != -1 && S.size() > this.depth) continue;

                checkCounter.increment("findIndependenceCheckRecursive (test executed)");

                IndependenceResult independenceResult = this.test.checkIndependence(x, y, S);

                if (isFocus(x, y)) {
                    focusSepsetLog.add("  tested S=" + S + (A.isEmpty() ? "" : "  (added " + A + ")")
                            + " -> " + (independenceResult.isIndependent() ? "INDEPENDENT" : "dependent"));
                }

                if (independenceResult.isIndependent()) {
                    if (!A.isEmpty()) additionPhaseRescues++;
                    // NO cache write here. This method runs inside the parallel lookahead, whose
                    // findFirst short-circuits: branches already in flight still run to completion,
                    // so WHICH losing edges leave a cached separator behind is scheduling-dependent.
                    // Since a pair can have several valid separators, and which one is found depends
                    // on the PAG at search time (V4--V5 yields {V3} before V3--V5 is deleted and {}
                    // after), a speculative write makes the whole search nondeterministic: the two
                    // sepsets stamp differently, and one hosts the deletion while the other does not.
                    // The winner's separator is recorded by the caller, after findFirst returns.
                    return new IndependenceCheck(edge, S, independenceResult.getPValue());
                }
            }
        }

        return null;
    }

    // Trying to implement the Step Lemma. Here we know that x--y is a spurious edge, since a sepset b has been
    // found. With escape == false only within-class representatives are tried -- the Step Lemma's own
    // quantifier. Candidates per class: the canonical Zhang MAG (Stage 1), the other LEGs reached by
    // legitimate reversal (Stage 2), and the in-class fork-flip variants OF EACH of those (Stage 2b).
    // With escape == true the certified out-of-class seeds and their flips are tried instead; this is an
    // engineering fallback OUTSIDE the Step Lemma, invoked only at the state level after a full
    // within-class sweep commits nothing.
    private boolean tryToModifyGraph(Node x, Node y, Set<Node> b, double pValue, boolean excludeSelectionBias,
                                     boolean escape)
            throws InterruptedException {
        Edge _edge = interimPags.getLast().getEdge(x, y);
        Graph _pag = new EdgeListGraph(interimPags.getLast());
        List<Edge> _removed = Collections.singletonList(Objects.requireNonNull(_edge));

        final long deadline = (timeout < 0L) ? Long.MAX_VALUE : System.currentTimeMillis() + timeout;

        final boolean f = isFocus(x, y);

        // Class identity reference for membership tests; same construction seedMags uses.
        Graph basePag = new MagToPag(GraphTransforms.zhangMagFromPag(_pag))
                .convert(false, excludeSelectionBias);

        // Staged generator seeds, built only when the staged generator will actually run.
        List<Graph> seeds = (useClassWalk && !escape)
                ? Collections.emptyList()
                : seedMags(_pag, x, y, b, deadline, escape);

        if (isFocus(x, y)) {
            focusInterimPag = _pag.toString();
            focusBasePag = basePag.toString();
        }

        // Candidates recur across seeds and walks; test each distinct MAG once.
        Set<String> tried = new HashSet<>();

        // ---- Within-class pass via the class walk (default). The staged seeds/LEG/fork-flip
        // generator is retained below for escape mode and for setUseClassWalk(false).
        if (useClassWalk && !escape) {
            Graph seed = GraphTransforms.zhangMagFromPag(_pag);
            ClassWalk walk = new ClassWalk(seed, basePag, x, y, b, deadline);

            while (walk.hasNext()) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();

                Graph mag = walk.next();
                if (!tried.add(magKey(mag))) continue;
                if (f) bump("candidatesTried");
                if (f) focusEnumerated.add(mag.toString());

                Graph _mag = hostOrNull(mag, x, y, b, _removed, f);
                if (_mag == null) continue;

                if (verbose) {
                    TetradLogger.getInstance().log("Removing " + _edge + ", sepset = " + b
                            + (Double.isNaN(pValue) ? "" : ", p = " + pValue)
                            + " [class walk: candidate " + walk.emitted()
                            + ", depth " + walk.maxDepthSeen() + "]");
                }

                classWalkCommits++;
                classWalkVisited += walk.emitted();

                this.interimPags.add(new MagToPag(_mag).convert(false, excludeSelectionBias));
                sepsets.set(x, y, b);
                return true;
            }

            classWalkVisited += walk.emitted();
            if (walk.truncated()) classWalkTruncations++;

            if (verbose) {
                TetradLogger.getInstance().log("\tTried removing " + _edge
                        + " (class walk), but no representative MAG hosted it, sepset = " + b
                        + "; visited " + walk.emitted() + " class member(s) to depth "
                        + walk.maxDepthSeen()
                        + (walk.truncated()
                        ? " -- TRUNCATED on budget/deadline, so the class was NOT exhausted and this"
                        + " is inconclusive rather than a class boundary."
                        : " -- class exhausted, so no representative of this class hosts it."));
            }

            return false;
        }

        for (int seedIdx = 0; seedIdx < seeds.size(); seedIdx++) {
            Graph seed = seeds.get(seedIdx);
            boolean baseSeed = (!escape && seedIdx == 0);

            for (Graph leg : new LegEnumerator(seed)) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException();
                }

                if (System.currentTimeMillis() > deadline) {
                    break;
                }

                // Phase 0: the LEG itself. Phase 1: its fork-flip variants, computed ONLY if the
                // LEG failed, so the flip enumeration costs nothing on the common path.
                for (int phase = 0; phase < 2; phase++) {
                    List<Graph> candidates = (phase == 0)
                            ? Collections.singletonList(leg)
                            : forkFlips(leg, basePag, x, y, b, deadline, escape);

                    for (Graph mag : candidates) {
                        if (System.currentTimeMillis() > deadline) break;
                        if (!tried.add(magKey(mag))) continue;
                        if (f) bump("candidatesTried");
                        if (f) focusEnumerated.add(mag.toString());

                        Graph _mag = hostOrNull(mag, x, y, b, _removed, f);
                        if (_mag == null) continue;

                        if (verbose) {
                            TetradLogger.getInstance().log("Removing " + _edge + ", sepset = " + b
                                    + (Double.isNaN(pValue) ? "" : ", p = " + pValue));
                        }

                        // Commit provenance: Zhang MAG (Stage 1), another LEG (Stage 2), a fork-flip
                        // variant in class (Stage 2b), or a certified out-of-class seed (pass 3).
                        if (escape) {
                            escapeCommits++;
                        } else if (phase > 0 || !baseSeed) {
                            inClassFlipCommits++;
                        } else if (magKey(mag).equals(magKey(seed))) {
                            zhangCommits++;
                        } else {
                            legCommits++;
                        }

                        this.interimPags.add(new MagToPag(_mag).convert(false, excludeSelectionBias));
                        sepsets.set(x, y, b);
                        return true;
                    }
                }
            }
        }

        if (verbose) {
            TetradLogger.getInstance().log("\tTried removing " + _edge
                    + (escape ? " (class-escape pass)" : " (within-class pass)")
                    + ", but no representative MAG hosted it, sepset = " + b);
        }

        return false;
    }

    /**
     * Builds the seed MAGs fed to the representative ({@link LegEnumerator}) search for deleting
     * {@code x--y} with sepset {@code S}. The first seed is always the minimal-bidirected Zhang
     * MAG. Fork-flip seeds are then enumerated in BOTH modes: on each active x..y path (given S)
     * we find the non-collider nodes not in S and, for bounded subsets of them, convert them to
     * colliders by stamping arrowheads in from their path-neighbors (turning the incident
     * directed edges into {@code <->}). Each legal-MAG result is CLASSIFIED by whether it remains
     * Markov-equivalent to the current class (its PAG coincides with the base's; skeletons agree
     * by construction, and MagToPag is canonical per class, so PAG identity is class identity).
     * A flip at a shielded triple that disturbs no discriminating path stays in class -- a
     * legitimate Zhang-Spirtes representative that is neither a LEG nor reachable by separator
     * stamping (its bidirected edges can sit outside the deleted pair's common neighbors) --
     * and is served to the within-class pass as Stage 2b. Certified non-equivalent flips are
     * genuine class escapes and are served only to pass 3.
     * <p>
     * With {@code escape == false} the returned list is the base followed by the in-class flips
     * (the Step Lemma's quantifier, approximated from below); with {@code escape == true} it is
     * the out-of-class flips only (the within-class seeds were already exhausted by the sweep
     * that triggered pass 3).
     * <p>
     * NOTE (proved-vs-conjectured ledger): a certified OUT-OF-CLASS seed is neither
     * Markov-equivalent to the current state nor certifiably an I-map of the truth, so the
     * technical note's exactness claims (zero oracle false refusals for the deleted-pair battery;
     * Conjecture pairlocal) do not cover pass-3 commits. The battery remains sound to RUN on them
     * -- it tests only statements the candidate entails -- but its guarantees are stated for
     * within-class candidates only. In-class flips, being equivalent representatives, sit fully
     * inside the note's scope.
     */
    /**
     * True iff MAGs a and b encode the SAME m-separation model -- the DEFINITION of Markov
     * equivalence, tested directly instead of via MagToPag-canonical-PAG equality. MagToPag
     * equality fails when MagToPag over-commits a class-variant mark (e.g. rendering a variant
     * V6&lt;-&gt;V3 as an invariant V3--&gt;V6), splitting one class in two and rejecting genuine
     * representatives. This test cannot: it compares the entailed independencies themselves.
     * O(pairs * 2^{n-2}) m-sep queries -- sound at any scope, affordable at the enumerated scope.
     * Compares by node NAME, robust to identity differences across copies.
     */
    private static boolean sameMsepModel(Graph a, Graph b) throws InterruptedException {
        MsepTest ta = new MsepTest(a);
        MsepTest tb = new MsepTest(b);
        List<Node> nodes = a.getNodes();
        int n = nodes.size();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                Node ai = nodes.get(i), aj = nodes.get(j);
                Node bi = b.getNode(ai.getName()), bj = b.getNode(aj.getName());
                List<Node> rest = new ArrayList<>(nodes);
                rest.remove(ai);
                rest.remove(aj);
                SublistGenerator gen = new SublistGenerator(rest.size(), rest.size());
                int[] c;
                while ((c = gen.next()) != null) {
                    Set<Node> zA = GraphUtils.asSet(c, rest);
                    Set<Node> zB = new HashSet<>();
                    for (Node z : zA) zB.add(b.getNode(z.getName()));
                    if (ta.checkIndependence(ai, aj, zA).isIndependent()
                            != tb.checkIndependence(bi, bj, zB).isIndependent()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    // ================= class walk: single-mark-change traversal of the class =================

    /**
     * Flips one endpoint mark: tail becomes arrowhead and vice versa. Returns null for a circle
     * (a MAG has none, so a circle means the caller was handed a PAG by mistake).
     */
    private static Endpoint flipMark(Endpoint e) {
        if (e == Endpoint.TAIL) return Endpoint.ARROW;
        if (e == Endpoint.ARROW) return Endpoint.TAIL;
        return null;
    }

    /**
     * Copy of {@code g} with the {@code a}--{@code c} edge's marks replaced. Node objects are
     * preserved (EdgeListGraph's copy constructor shares them), so callers may keep using the
     * {@code x}/{@code y} references they already hold.
     */
    private static Graph withMarks(Graph g, Node a, Node c, Endpoint atA, Endpoint atC) {
        Graph h = new EdgeListGraph(g);
        Node na = h.getNode(a.getName()), nc = h.getNode(c.getName());
        h.removeEdge(na, nc);
        h.addEdge(new Edge(na, nc, atA, atC));
        return h;
    }

    /**
     * Every graph one SINGLE MARK CHANGE from {@code g}: for each edge, flip the mark at one
     * endpoint. Note that a full reversal {@code a-->c} to {@code a<--c} is TWO mark changes and
     * is reached through the bidirected intermediate {@code a<->c}. That is not an inefficiency
     * to be optimised away -- it is the reason this neighbourhood reaches representatives a
     * LEG-anchored search cannot, since the intermediate is bidirected-richer than either end.
     * <p>
     * The neighbourhood is proposed unconditionally; {@link ClassWalk} accepts a neighbour iff it
     * is a legal MAG and still in the class. Zhang and Spirtes give graphical side conditions
     * characterising exactly when a single mark change preserves Markov equivalence; testing
     * equivalence directly yields the same neighbourhood without depending on those conditions
     * being transcribed correctly, at the cost of one MagToPag conversion per proposal. If that
     * conversion ever becomes the bottleneck, the side conditions are the optimisation -- and
     * this method is the oracle to validate it against.
     */
    private static List<Graph> markChangeNeighbors(Graph g) {
        List<Graph> out = new ArrayList<>();
        for (Edge e : g.getEdges()) {
            Node a = e.getNode1(), c = e.getNode2();
            Endpoint atA = g.getEndpoint(c, a);
            Endpoint atC = g.getEndpoint(a, c);
            Endpoint fa = flipMark(atA), fc = flipMark(atC);
            if (fa != null) out.add(withMarks(g, a, c, fa, atC));
            if (fc != null) out.add(withMarks(g, a, c, atA, fc));
        }
        return out;
    }

    /**
     * How many collider stamps {@link #stampLegColliders} would still have to impose on
     * {@code mag} for the {@code x}--{@code y} deletion: the common neighbours outside the sepset
     * that are not already colliders. Zero means the representative already carries the structure
     * the deletion needs, so nothing has to be forced and the post-stamp legality check cannot
     * fail. This is the class walk's best-first priority, and it is aimed at the observed failure
     * mode -- in the PKE11 fixture the seed had deficit 2 and every host had deficit 0.
     */
    private static int stampDeficit(Graph mag, Set<Node> b, Node x, Node y) {
        List<Node> common = mag.getAdjacentNodes(x);
        common.retainAll(mag.getAdjacentNodes(y));
        int deficit = 0;
        for (Node c : common) {
            if (b.contains(c)) continue;
            if (!mag.isDefCollider(x, c, y)) deficit++;
        }
        return deficit;
    }

    /**
     * A frontier entry: a class member, its depth in mark changes from the seed, and the two
     * priority keys.
     */
    private record WalkNode(Graph g, int depth, int deficit, int bi) {
    }

    /**
     * Lazy traversal of the Markov equivalence class of a seed MAG by single mark changes,
     * emitting each class member exactly once. Lazy because the caller stops at the first
     * representative that hosts the deletion; the class is enumerated only as far as it must be.
     * <p>
     * Completeness rests on the Zhang-Spirtes transformational characterization: Markov-equivalent
     * DMAGs are connected by sequences of single mark changes preserving Markov equivalence, so
     * with an unbounded budget this reaches every representative of the class. With a bounded one
     * it does not, and {@link #truncated()} says which happened -- a distinction that matters,
     * because "the walk found no host" is evidence about the Step Lemma only when the walk ran to
     * exhaustion.
     */
    private final class ClassWalk implements Iterator<Graph> {

        private final Graph basePag;
        private final Node x, y;
        private final Set<Node> b;
        private final long deadline;

        private final Set<String> seen = new HashSet<>();
        private final Deque<WalkNode> fifo = new ArrayDeque<>();
        private final PriorityQueue<WalkNode> heap = new PriorityQueue<>(
                Comparator.<WalkNode>comparingInt(WalkNode::deficit)
                        .thenComparingInt(w -> -w.bi())
                        .thenComparingInt(WalkNode::depth));

        private WalkNode pending;
        private int emitted = 0;
        private boolean truncated = false;
        private int maxDepthSeen = 0;

        ClassWalk(Graph seed, Graph basePag, Node x, Node y, Set<Node> b, long deadline) {
            this.basePag = basePag;
            this.x = x;
            this.y = y;
            this.b = b;
            this.deadline = deadline;
            seen.add(magKey(seed));
            push(new WalkNode(seed, 0, stampDeficit(seed, b, x, y), bidirectedCount(seed)));
        }

        private void push(WalkNode w) {
            if (classWalkBestFirst) heap.add(w);
            else fifo.addLast(w);
        }

        private WalkNode pop() {
            return classWalkBestFirst ? heap.poll() : fifo.pollFirst();
        }

        private boolean frontierEmpty() {
            return classWalkBestFirst ? heap.isEmpty() : fifo.isEmpty();
        }

        @Override
        public boolean hasNext() {
            if (pending != null) return true;
            if (frontierEmpty()) return false;

            if (classWalkMaxCandidates >= 0 && emitted >= classWalkMaxCandidates) {
                truncated = true;
                return false;
            }
            if (System.currentTimeMillis() > deadline) {
                truncated = true;
                return false;
            }

            WalkNode cur = pop();
            if (cur == null) return false;
            pending = cur;
            maxDepthSeen = Math.max(maxDepthSeen, cur.depth());
            expand(cur);
            return true;
        }

        @Override
        public Graph next() {
            if (!hasNext()) throw new NoSuchElementException();
            Graph g = pending.g();
            pending = null;
            emitted++;
            return g;
        }

        /**
         * Adds the in-class legal neighbours of {@code cur} to the frontier. Neighbours are marked
         * seen whether or not they are accepted: an illegal or out-of-class graph is not a valid
         * waypoint, so no path through it needs revisiting.
         */
        private void expand(WalkNode cur) {
            for (Graph nb : markChangeNeighbors(cur.g())) {
                if (System.currentTimeMillis() > deadline) {
                    truncated = true;
                    return;
                }
                if (!seen.add(magKey(nb))) continue;
                if (!nb.paths().isLegalMag()) continue;
                if (!new MagToPag(nb).convert(false, excludeSelectionBias).equals(basePag)) continue;
                push(new WalkNode(nb, cur.depth() + 1, stampDeficit(nb, b, x, y), bidirectedCount(nb)));
            }
        }

        /** True iff the walk stopped on the budget or the deadline rather than exhausting the class. */
        boolean truncated() {
            return truncated;
        }

        int emitted() {
            return emitted;
        }

        int maxDepthSeen() {
            return maxDepthSeen;
        }
    }

    /**
     * The commit gate, shared by every candidate generator so that changing the generator cannot
     * change what is accepted. Applies, in order: the collider stamp for the recorded sepsets;
     * legality of the STAMPED graph; deletion of {@code x}--{@code y}; prong (A), the removed-pair
     * inducing-path pre-check; and prong (B), the deleted-pair battery.
     *
     * @return the stamped, deleted MAG if the candidate hosts the deletion, else null.
     */
    private Graph hostOrNull(Graph mag, Node x, Node y, Set<Node> b, List<Edge> removed, boolean f)
            throws InterruptedException {
        Graph _mag = mag.copy();

        // H' must honour the stored sepsets: stamp the common colliders of x and y.
        // Refuses any candidate whose stamp would create a NEW unshielded collider.
        if (!stampLegColliders(_mag, b, x, y)) {
            if (f) bump("stampPruned");
            return null;
        }

        // The STAMPED graph is the H' every lemma quantifies over, so it must itself be a legal
        // MAG. The stamp can make an ancestral-but-NON-MAXIMAL graph: an inducing path between a
        // pair OTHER than {x, y}, whose ancestry certificate runs through the edge about to be
        // deleted. Deleting then kills the path and mints a NEW separation at that other pair --
        // invisible to the deleted-pair battery, and outside the hypotheses of deletion-locality
        // and pair-locality (both assume H' is a MAG). Concretely: stamping V1 for the {V3,V4}
        // deletion on the V4<->V2 flip produced the inducing path V3<->V1<->V4<->V2 (V4 in An(V3)
        // via V4-->V3); deleting V4-->V3 yielded the false V3 _||_ V2 | {V1}.
        if (!_mag.paths().isLegalMag()) {
            if (f) bump("illegalMagAfterStamp");
            otherRejects++;
            return null;
        }

        // We remove f = x *-* y, yielding H' - f.
        _mag.removeEdge(x, y);

        legalityChecks++;

        // Prong (A): H' - f must be a MAG (Lemma 3.6 localizes this to the pair).
        if (_mag.paths().existsInducingPath(x, y, Set.of())) {
            if (f) bump("inducingPathReject");
            ipRejects++;
            return null;
        }

        // Prong (B): no new CIs absent from G*. Spot-checked by the battery.
        if (!deletedPairBatteryPasses(_mag, removed)) {
            if (f) bump("batteryReject");
            return null;
        }

        // Prong (C): the MAG SCORE CHECK. _mag is a legal MAG carrying the deletion, so its Gaussian BIC is
        // well defined; refuse the candidate if the deletion makes the fit worse. Placing this here rather than
        // at the call sites keeps the property this method's javadoc asserts -- that changing the candidate
        // generator cannot change what is accepted -- since every generator's candidates pass through here.
        if (scoreCheckRefuses(_mag, x + " *-* " + y)) {
            if (f) bump("scoreReject");
            return null;
        }

        if (f) bump("committed");
        return _mag;
    }

    private List<Graph> seedMags(Graph pag, Node x, Node y, Set<Node> S, long deadline, boolean escape)
            throws InterruptedException {
        Graph base = GraphTransforms.zhangMagFromPag(pag);

        LinkedHashMap<String, Graph> inClass = new LinkedHashMap<>();
        LinkedHashMap<String, Graph> outOfClass = new LinkedHashMap<>();
        inClass.put(magKey(base), base);

        Graph probe = new EdgeListGraph(base);
        probe.removeEdge(x, y);

        // If the bare Zhang deletion already m-separates the pair given S, no x..y path is
        // active given S, so the fork enumeration below is empty; short-circuit the DFS.
        if (new MsepTest(probe).checkIndependence(x, y, S).isIndependent()) {
            return new ArrayList<>((escape ? outOfClass : inClass).values());
        }

        // Fork/chain nodes (non-colliders not in S) on active x..y paths, with the path-neighbors
        // whose arrowheads-into would make them colliders.
        Map<Node, Set<Node>> forkNbrs = new LinkedHashMap<>();
        for (List<Node> p : activePathsGivenS(probe, x, y, S, 8)) {
            for (int i = 1; i < p.size() - 1; i++) {
                Node a = p.get(i - 1), m = p.get(i), c = p.get(i + 1);
                if (S.contains(m)) continue;
                if (!probe.isDefCollider(a, m, c)) {
                    forkNbrs.computeIfAbsent(m, k -> new LinkedHashSet<>()).add(a);
                    forkNbrs.get(m).add(c);
                }
            }
        }

        // Try converting bounded subsets of forks to colliders; classify the legal-MAG results
        // by class membership against the base's PAG.
        Graph basePag = new MagToPag(base).convert(false, this.excludeSelectionBias);

        if (isFocus(x, y)) {
            focusSeedLog.add("BASE (Zhang seed) inClass=true bi=" + bidirectedCount(base) + "\n" + base);
        }

        List<Node> forks = new ArrayList<>(forkNbrs.keySet());
        int cap = Math.min(forks.size(), maxForkFlips);
        SublistGenerator gen = new SublistGenerator(forks.size(), cap);
        int[] choice;
        while ((choice = gen.next()) != null) {
            if (System.currentTimeMillis() > deadline) break;
            if (choice.length == 0) continue;               // base already present (in-class)
            Graph seed = new EdgeListGraph(base);
            for (int idx : choice) makeCollider(seed, forks.get(idx), forkNbrs.get(forks.get(idx)));
            if (!seed.paths().isLegalMag()) continue;

            String key = magKey(seed);
            if (inClass.containsKey(key) || outOfClass.containsKey(key)) continue;

            Graph seedPag = new MagToPag(seed).convert(false, this.excludeSelectionBias);
            boolean seedInClass = seedPag.equals(basePag);
            if (isFocus(x, y)) {
                focusSeedLog.add("SEED magToPagInClass=" + seedInClass
                        + " msepInClass=" + sameMsepModel(seed, base)
                        + " bi=" + bidirectedCount(seed) + "\n" + seed);
            }
            if (seedInClass) {
                inClass.put(key, seed);
            } else {
                outOfClass.put(key, seed);
            }
        }

        return new ArrayList<>((escape ? outOfClass : inClass).values());
    }

    /**
     * Fork-flip variants of ONE representative. Same construction as {@link #seedMags}'s flip
     * enumeration -- on each active x..y path given S, convert non-collider path nodes to
     * colliders by stamping arrowheads in from their path-neighbours -- but applied to an
     * arbitrary representative and classified against {@code basePag}: within-class mode keeps
     * only Markov-equivalent flips (Stage 2b), escape mode only non-equivalent ones (pass 3).
     * <p>
     * Applied to EVERY LEG the walk emits, not only the canonical Zhang MAG. The witness the
     * Step Lemma promises can need a directed orientation reachable only by a legitimate
     * reversal TOGETHER WITH a non-invariant bidirected edge reachable only by a flip, and
     * flipping the base alone cannot compose the two. Concretely (V5--V4 deletion): the witness
     * needs V3-->V1, so that stamping V5*->V3 creates no unshielded collider, AND V1<->V4, so
     * that V3 is not an ancestor of V4 and the deletion leaves no inducing path. Flipping the
     * Zhang MAG yields V1<->V3 with V1<->V4; walking the LEGs yields V3-->V1 with V1-->V4; only
     * a flip OF A WALKED LEG yields both. Since the PAG has no invariant bidirected edge here,
     * every LEG is a DAG with V1-->V4 forced, so no LEG hosts the deletion at all -- a
     * LEG-sufficiency counterexample, not merely a search-order artifact.
     */
    private List<Graph> forkFlips(Graph mag, Graph basePag, Node x, Node y, Set<Node> S,
                                  long deadline, boolean escape) throws InterruptedException {List<Graph> out = new ArrayList<>();

        // Reference MAG for the focus-only m-sep diagnostic; computed only when focusing.
        Graph base = isFocus(x, y) ? GraphTransforms.zhangMagFromPag(basePag) : null;

        Graph probe = new EdgeListGraph(mag);
        probe.removeEdge(x, y);

        // If this representative already hosts the deletion, no x..y path is active given S,
        // so the fork inventory is empty; skip the DFS.
        if (new MsepTest(probe).checkIndependence(x, y, S).isIndependent()) return out;

        Map<Node, Set<Node>> forkNbrs = new LinkedHashMap<>();
        for (List<Node> p : activePathsGivenS(probe, x, y, S, 8)) {
            for (int i = 1; i < p.size() - 1; i++) {
                Node a = p.get(i - 1), m = p.get(i), c = p.get(i + 1);
                if (S.contains(m)) continue;
                if (!probe.isDefCollider(a, m, c)) {
                    forkNbrs.computeIfAbsent(m, k -> new LinkedHashSet<>()).add(a);
                    forkNbrs.get(m).add(c);
                }
            }
        }

        List<Node> forks = new ArrayList<>(forkNbrs.keySet());
        int cap = Math.min(forks.size(), maxForkFlips);
        SublistGenerator gen = new SublistGenerator(forks.size(), cap);
        int[] choice;

        while ((choice = gen.next()) != null) {
            if (System.currentTimeMillis() > deadline) break;
            if (choice.length == 0) continue;

//            System.out.println("mag to flip: " + mag);

            // the representative itself
            Graph flip = new EdgeListGraph(mag);
            for (int idx : choice) makeCollider(flip, forks.get(idx), forkNbrs.get(forks.get(idx)));
            if (!flip.paths().isLegalMag()) continue;

//            System.out.println("mag flipped: " + flip);

            boolean inClass = new MagToPag(flip).convert(false, this.excludeSelectionBias).equals(basePag);
            if (isFocus(x, y)) {
                focusSeedLog.add("FLIP magToPagInClass=" + inClass
                        + " msepInClass=" + sameMsepModel(flip, base)
                        + " bi=" + bidirectedCount(flip) + "\n" + flip);
            }
            if (escape != inClass) out.add(flip);             // escape wants out-of-class, else in-class

//            System.out.println("mag in/out class: " + inClass);
        }

//        System.out.println("MAGs to return = " + out);

        return out;
    }

    /**
     * Simple x..y paths (bounded to {@code maxPaths}) that are active (m-connecting) given {@code S}.
     * A path is active iff every non-collider on it is outside S and every collider on it is an
     * ancestor of some node in S (or lies in S).
     */
    private List<List<Node>> activePathsGivenS(Graph g, Node x, Node y, Set<Node> S, int maxPaths) {
        List<List<Node>> out = new ArrayList<>();
        Deque<Node> path = new ArrayDeque<>();
        Set<Node> onPath = new HashSet<>();
        path.addLast(x);
        onPath.add(x);
        dfsActive(g, x, y, S, path, onPath, out, maxPaths);
        return out;
    }

    private void dfsActive(Graph g, Node cur, Node y, Set<Node> S, Deque<Node> path,
                           Set<Node> onPath, List<List<Node>> out, int maxPaths) {
        if (out.size() >= maxPaths) return;
        if (cur.equals(y)) {
            List<Node> p = new ArrayList<>(path);
            if (p.size() >= 3 && isActiveGivenS(g, p, S)) out.add(p);
            return;
        }
        for (Node next : g.getAdjacentNodes(cur)) {
            if (onPath.contains(next)) continue;
            path.addLast(next);
            onPath.add(next);
            dfsActive(g, next, y, S, path, onPath, out, maxPaths);
            onPath.remove(next);
            path.removeLast();
            if (out.size() >= maxPaths) return;
        }
    }

    /**
     * m-connection test for a fixed simple path given S.
     */
    private boolean isActiveGivenS(Graph g, List<Node> path, Set<Node> S) {
        for (int i = 1; i < path.size() - 1; i++) {
            Node a = path.get(i - 1), m = path.get(i), c = path.get(i + 1);
            if (g.isDefCollider(a, m, c)) {
                if (!ancestorInS(g, m, S)) return false;    // collider must have a descendant in S
            } else {
                if (S.contains(m)) return false;            // non-collider must be outside S
            }
        }
        return true;
    }

    // ==================== CLOSURE-COVER WITNESS SEARCH ====================
    //
    // A drop-in alternative CANDIDATE GENERATOR behind the SAME commit gates as the staged
    // search (seedMags + LegEnumerator + forkFlips). Rationale: a MAG in the current class
    // is exactly an assignment of TAIL/ARROW to the PAG's circle endpoints (the invariant
    // marks are shared by every member), so the Zhang MAG, the LEGs, and the in-class
    // fork-flips are all points in ONE assignment space -- and the composition the staged
    // search reaches only by flipping every walked LEG (the V5--V4 note at forkFlips) is
    // just another point in it. This search explores that space directly, but only on the
    // closure of the bounded x..y skeleton paths, driven by a covering requirement: every
    // enumerated path must be blocked by S in the candidate. Endpoints outside the closure
    // stay frozen at the Zhang orientation, so the subproblem size is governed by local
    // path structure, not |V|.
    //
    // Search structure per candidate: (Phase A) stamp-compatibility propagation pins the
    // TAILs that stampLegColliders will demand -- this is what composes "a reversal needed
    // for the stamp" with "a flip needed for blocking" in one pass; (Phase B) a depth-first
    // cover search repeatedly takes the first still-active path and branches on the moves
    // that could block it (collider-ize a non-S node, or de-collider-ize an S node); a state
    // with no active enumerated path is confirmed EXACTLY by MsepTest on the stamped
    // candidate minus the edge, then classified (MagToPag equality against the base PAG,
    // pre-stamp, matching seedMags' seed-level classification) and handed to
    // closureGateAndCommit, whose gate sequence is byte-for-byte tryToModifyGraph's.
    //
    // Proved-vs-conjectured ledger: nothing here changes what is CERTIFIED. Emitted
    // candidates pass the identical gates; in-class commits sit inside the technical note's
    // scope, and escape commits carry the same pass-3 asterisk as before. The covering
    // heuristic and the stamp-compatibility propagation affect only which candidates are
    // proposed, in what order, and how fast.

    /**
     * Closure-cover replacement for {@link #tryToModifyGraph}: same contract, same gates,
     * different candidate generator. Stage 1 (the plain Zhang MAG) is preserved verbatim --
     * and counted in {@code zhangCommits} -- so the common case costs what it costs today
     * and the provenance ledger stays comparable across generators.
     */
    private boolean tryToModifyGraphClosure(Node x, Node y, Set<Node> b, double pValue,
                                            boolean excludeSelectionBias, boolean escape)
            throws InterruptedException {
        Edge _edge = interimPags.getLast().getEdge(x, y);
        Graph _pag = new EdgeListGraph(interimPags.getLast());
        List<Edge> _removed = Collections.singletonList(Objects.requireNonNull(_edge));

        final long deadline = (timeout < 0L) ? Long.MAX_VALUE : System.currentTimeMillis() + timeout;

        Graph base = GraphTransforms.zhangMagFromPag(_pag);

        // Class identity reference; same round-tripped construction seedMags uses.
        Graph basePag = new MagToPag(base).convert(false, excludeSelectionBias);

        Set<String> tried = new HashSet<>();

        // Stage 1: the canonical Zhang MAG, exactly as the staged search tries it first.
        if (!escape) {
            if (closureGateAndCommit(new EdgeListGraph(base), x, y, b, pValue, excludeSelectionBias,
                    _removed, _edge, () -> zhangCommits++) == GateStatus.COMMITTED) {
                return true;
            }
            tried.add(magKey(base));
        }

        // Phase A: stamp-compatibility propagation. Pins the TAILs the stamp gate will
        // demand and records the stamp's own arrowheads as virtual pins so cover moves
        // cannot contradict them.
        Map<String, Endpoint> pins = new HashMap<>();
        Graph cand = new EdgeListGraph(base);
        applyStampCompatPins(_pag, cand, pins, x, y, b);

        // The skeleton is class-invariant, so the paths are enumerated once, shortest first.
        List<List<Node>> paths = boundedSkeletonPaths(_pag, x, y, closureMaxPathLength, closureMaxPaths);

        int budget = (maxCoverMoves >= 0) ? maxCoverMoves : autoBudget(_pag, paths);

        boolean committed = closureCoverDfs(cand, pins, paths, budget, _pag, basePag,
                x, y, b, pValue, excludeSelectionBias, _removed, _edge, escape, false, tried, deadline);

        if (!committed) {
            // SECOND PASS, FULLY RELAXED. The frozen-marks assumption is only as sound as the
            // PAG, and MagToPag can over-commit (see slotCanBe) -- when it does, the witness
            // sits outside the frozen search space entirely and no amount of searching inside
            // it helps. The per-node fallback above catches only the case where a node has NO
            // frozen move; it cannot catch the case that matters more, where frozen moves exist
            // in abundance and every one of them leads somewhere out of class. PKE8's 6-observed
            // V6--V4 class is that case: nine free slots kept the frozen search busy for
            // hundreds of candidates, while the hosting representative needed arrowheads at V1
            // and V2 on edges the PAG had already called tails. So the trigger is the failure of
            // the whole frozen search, not the emptiness of one node's move set.
            //
            // Correctness is unaffected: relaxation widens only move GENERATION. Class
            // membership is still certified by MagToPag equality and every candidate still
            // clears the same gates, so a relaxed pass can commit nothing a frozen pass would
            // have been wrong to commit.
            closureRelaxedPasses++;

            Map<String, Endpoint> pins2 = new HashMap<>();
            Graph cand2 = new EdgeListGraph(base);
            applyStampCompatPins(_pag, cand2, pins2, x, y, b);

            // Leaf verdicts are pass-independent, but the MOVES available at a leaf are not,
            // so a state adjudicated frozen must be re-openable under relaxation.
            tried.clear();
            if (!escape) tried.add(magKey(base));

            committed = closureCoverDfs(cand2, pins2, paths, budget, _pag, basePag,
                    x, y, b, pValue, excludeSelectionBias, _removed, _edge, escape, true, tried, deadline);
        }

        if (!committed && closureFallbackToStaged) {
            // FALLBACK TO THE STAGED GENERATOR. The closure search's weakness is structural,
            // not a missing move: it searches MAG space and tests class membership at the end,
            // whereas LegEnumerator only ever visits class members. Where the class is a small
            // share of the separating assignments -- dense PAGs at 6+ observed variables -- the
            // closure DFS can generate exactly the right moves (verified) and still never land
            // in class. Deferring to the staged search on failure makes this generator's reach a
            // superset of staged's while keeping its speed on the cases it does host, at the
            // cost that such edges' provenance reads as staged. {@code closureFallbackCommits}
            // counts them, so the ledger stays honest and the residual rate stays visible.
            //
            // Nothing has been mutated at this point: the closure search touches interimPags
            // and sepsets only via closureGateAndCommit, which runs only on success. So the
            // staged search sees exactly the state it would have seen had it run alone.
            closureFallbackAttempts++;
            committed = tryToModifyGraph(x, y, b, pValue, excludeSelectionBias, escape);
            if (committed) closureFallbackCommits++;
        }

        if (!committed && verbose) {
            TetradLogger.getInstance().log("\tClosure-cover search: no candidate hosted " + _edge
                    + (escape ? " (escape mode)" : " (within-class mode)") + ", sepset = " + b);
        }

        return committed;
    }

    /**
     * Gate outcomes for the closure path; the diagnosis drives repair-move generation at
     * separating leaves (see {@link #closureCoverDfs}).
     */
    private enum GateStatus {COMMITTED, STAMP_REFUSED, ILLEGAL_MAG, INDUCING_PATH, BATTERY_REFUSED, SCORE_REFUSED}

    /**
     * AUTO move budget: the number of free (circle) endpoint slots on the closure of the
     * enumerated x..y paths, floored at 4 so short closures still get the historical budget
     * and capped by {@link #closureAutoBudgetCap} to bound the DFS. This is the local measure
     * of how far the search may have to travel -- every move sets a slot that was free, so a
     * witness cannot be further than the number of free slots, and bounding by the closure
     * rather than by a constant is what keeps the bound meaningful as models grow.
     */
    private int autoBudget(Graph pag, List<List<Node>> paths) {
        Set<Node> closure = new LinkedHashSet<>();
        for (List<Node> p : paths) closure.addAll(p);

        Set<String> free = new LinkedHashSet<>();
        for (Node u : closure) {
            for (Node w : pag.getAdjacentNodes(u)) {
                if (pag.getEndpoint(w, u) == Endpoint.CIRCLE) free.add(slotKey(w, u));
                if (pag.getEndpoint(u, w) == Endpoint.CIRCLE) free.add(slotKey(u, w));
            }
        }

        return Math.max(4, Math.min(closureAutoBudgetCap, free.size()));
    }

    /**
     * The gate pipeline, factored for the closure path but IDENTICAL in content and order to
     * the inline gates of {@link #tryToModifyGraph}: honour the stored sepsets by stamping the
     * common colliders of the pair; require the stamped graph to be a legal MAG (the H' every
     * lemma quantifies over -- see the inducing-path incident documented at the corresponding
     * gate in tryToModifyGraph); remove the edge; require no inducing path at the pair (prong
     * A, localized by Lemma 3.6); pass the deleted-pair battery (prong B); then commit and
     * record provenance. Returns the failure kind rather than a bare boolean so the caller
     * can target repairs at the actual obstruction.
     */
    private GateStatus closureGateAndCommit(Graph candidate, Node x, Node y, Set<Node> b, double pValue,
                                            boolean excludeSelectionBias, List<Edge> removed, Edge edgeForLog,
                                            Runnable provenance) throws InterruptedException {
        Graph _mag = candidate.copy();

        if (!stampLegColliders(_mag, b, x, y)) {
            return GateStatus.STAMP_REFUSED;
        }

        if (!_mag.paths().isLegalMag()) {
            otherRejects++;
            return GateStatus.ILLEGAL_MAG;
        }

        _mag.removeEdge(x, y);

        legalityChecks++;

        if (_mag.paths().existsInducingPath(x, y, Set.of())) {
            ipRejects++;
            return GateStatus.INDUCING_PATH;
        }

        if (!deletedPairBatteryPasses(_mag, removed)) {
            return GateStatus.BATTERY_REFUSED;
        }

        // ---- MAG SCORE CHECK: same prong the staged gate (hostOrNull) applies. ----
        if (scoreCheckRefuses(_mag, edgeForLog)) {
            return GateStatus.SCORE_REFUSED;
        }


        if (verbose) {
            TetradLogger.getInstance().log("Removing " + edgeForLog + ", sepset = " + b
                    + (Double.isNaN(pValue) ? "" : ", p = " + pValue));
        }

        provenance.run();

        this.interimPags.add(new MagToPag(_mag).convert(false, excludeSelectionBias));
        sepsets.set(x, y, b);

        return GateStatus.COMMITTED;
    }

    /**
     * Stamp-compatibility propagation (Phase A). For each common neighbour c of x and y outside
     * S, the gate will stamp x *-&gt; c &lt;-* y. {@link #stampLegColliders} refuses when some d
     * with an arrowhead into c is unshielded from x (or y) without the triple already being a
     * definite collider -- the stamp would mint a new unshielded collider. So wherever the
     * current PAG leaves the mark at c on d--c FREE (a circle), pin it TAIL in the candidate:
     * this is exactly the "reversal needed for the stamp" that the staged search can reach only
     * by walking to the right LEG, obtained here by unit propagation before any search. Where
     * the PAG mark is an INVARIANT arrowhead and the triple is not an invariant collider, the
     * obstruction is recorded ({@code closureStampObstructions}) but NOT treated as a proof of
     * non-hostability: stampLegColliders' pre-check consults the candidate's marks, and a
     * candidate that pre-orients the x-side arrowhead at c can still satisfy it, so we leave
     * such cases for the gate to adjudicate rather than over-prune. (An obstruction count that
     * tracks refusals one-for-one at the oracle would be evidence the pre-check could be
     * strengthened to a certificate; PKE can audit that.)
     */
    private void applyStampCompatPins(Graph pag, Graph cand, Map<String, Endpoint> pins,
                                      Node x, Node y, Set<Node> b) {
        List<Node> common = pag.getAdjacentNodes(x);
        common.retainAll(pag.getAdjacentNodes(y));

        for (Node c : common) {
            if (b.contains(c)) continue;                   // not stamped; no requirement
            if (pag.isDefCollider(x, c, y)) continue;      // stamp is a no-op here

            // The stamp will force arrowheads at c from x and y; record them as virtual
            // pins (NOT applied to cand -- the gate re-derives them, and classification is
            // pre-stamp) so no cover move tries to tail them.
            pins.putIfAbsent(slotKey(x, c), Endpoint.ARROW);
            pins.putIfAbsent(slotKey(y, c), Endpoint.ARROW);

            for (Node d : pag.getAdjacentNodes(c)) {
                if (d == x || d == y) continue;

                boolean unshX = !pag.isAdjacentTo(d, x);
                boolean unshY = !pag.isAdjacentTo(d, y);
                if (!unshX && !unshY) continue;

                // Positing an arrowhead at c from d: stampLegColliders' pre-check passes iff
                // d*->c<-*x is ALREADY a definite collider, and since the stamp sets its own
                // arrowheads only AFTER the check, that reduces to "the candidate already
                // carries an arrowhead at c from x". Judging this on the PAG's circles
                // instead pins TAIL far too often -- in PKE8's X1--X5 case the Zhang MAG
                // already had X1 --> X4, so an arrowhead at X4 from X7 was perfectly legal,
                // yet the PAG-based test pinned it TAIL and blocked the only cut that breaks
                // X4's ancestry into S, leaving the DFS with no moves at all. Erring
                // permissive is the safe direction: a branch the stamp would refuse is caught
                // by the STAMP_REFUSED prune, whereas an over-tight pin silently removes the
                // witness from the search space.
                boolean okWithArrow = (!unshX || cand.getEndpoint(x, c) == Endpoint.ARROW)
                        && (!unshY || cand.getEndpoint(y, c) == Endpoint.ARROW);
                if (okWithArrow) continue;

                Endpoint atC = pag.getEndpoint(d, c);

                if (atC == Endpoint.ARROW) {
                    // Invariant arrowhead: every candidate this generator freezes-and-flips
                    // carries it, and the stamp's conservative pre-check will likely refuse.
                    // Recorded as evidence, adjudicated by the gate.
                    closureStampObstructions++;
                } else if (atC == Endpoint.CIRCLE) {
                    Endpoint prev = pins.putIfAbsent(slotKey(d, c), Endpoint.TAIL);
                    if (prev == null) {
                        // Legal-shape companion: a tail at c against a tail at d would make
                        // d -- c UNDIRECTED (a selection edge, illegal here), so anticipate
                        // the arrowhead at d when the PAG allows it; when it doesn't, the
                        // pin is infeasible in every candidate this generator produces --
                        // release it, record the obstruction, and let the gate adjudicate.
                        if (cand.getEndpoint(c, d) == Endpoint.TAIL) {
                            if (slotCanBe(pag, pins, c, d, Endpoint.ARROW)) {
                                cand.setEndpoint(d, c, Endpoint.TAIL);
                                cand.setEndpoint(c, d, Endpoint.ARROW);
                                pins.put(slotKey(c, d), Endpoint.ARROW);
                            } else {
                                pins.remove(slotKey(d, c));
                                closureStampObstructions++;
                            }
                        } else {
                            cand.setEndpoint(d, c, Endpoint.TAIL);
                        }
                    }
                    // prev == TAIL: already pinned, nothing to do. prev == ARROW cannot
                    // happen: arrow pins live only at c's marks on x--c / y--c.
                }
                // atC == TAIL: invariant tail already satisfies the requirement.
            }
        }
    }

    /**
     * The cover-and-repair search (Phase B). Each node: stamp the current partial assignment
     * (a refused stamp abandons the branch -- {@code closureStampPrunes} -- since the gate
     * would refuse every completion that leaves the obstructing marks untouched; completions
     * that change them are reached on sibling branches), then find the first enumerated path
     * still active given S.
     * <p>
     * ACTIVE PATH: branch on the COVER moves that could block it (see {@link #coverMoves}).
     * <p>
     * NO ACTIVE PATH: confirm exactly, classify, and hand to the gates. When the gate refuses
     * a separating candidate, the leaf is NOT abandoned: no path-driven move can exist (there
     * is no active path to derive one from), but OTHER representatives that also separate may
     * survive the gate -- the staged search reaches them by walking LEGs; this search reaches
     * them by REPAIR moves generated from the gate's own diagnosis: for a stamp-induced
     * illegality, break the offending directed/almost-directed cycle or non-maximal inducing
     * path (reversals first -- the LEG-style fix -- then bidirected cuts); for a prong-(A)
     * failure, break the removed pair's inducing path. This was learned from the V3--V4
     * five-node case, where the unique-up-to-reversal separating assignment was stamp-illegal
     * and only a V2--V4-style reversal (a Stage-2 LEG in the staged search) hosts the
     * deletion. Battery refusals get no repair -- they are data verdicts, terminal per
     * candidate exactly as in the staged search. First commit wins.
     */
    private boolean closureCoverDfs(Graph cand, Map<String, Endpoint> pins, List<List<Node>> paths,
                                    int movesLeft, Graph pag, Graph basePag, Node x, Node y, Set<Node> b,
                                    double pValue, boolean excludeSelectionBias, List<Edge> removed,
                                    Edge edgeForLog, boolean escape, boolean relaxed,
                                    Set<String> tried, long deadline)
            throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException();
        }

        if (System.currentTimeMillis() > deadline) {
            return false;
        }

        // Judge activity on what the gate will actually evaluate: the stamped candidate
        // minus the edge.
        Graph stamped = new EdgeListGraph(cand);
        if (!stampLegColliders(stamped, b, x, y)) {
            closureStampPrunes++;
            return false;
        }
        Graph probe = new EdgeListGraph(stamped);
        probe.removeEdge(x, y);

        List<Node> active = firstActivePath(probe, paths, b);

        List<CoverMove> moves;

        if (active != null) {
            if (movesLeft == 0) return false;
            moves = coverMoves(pag, stamped, cand, pins, active, paths, b, relaxed);
            if (moves.isEmpty() && !relaxed) {
                // No move exists under the frozen-marks assumption. The assumption itself may
                // be what is wrong here (see slotCanBe), so retry with the PAG's non-circle
                // marks unfrozen; class membership and every gate still adjudicate.
                moves = coverMoves(pag, stamped, cand, pins, active, paths, b, true);
                if (!moves.isEmpty()) closureRelaxedExpansions++;
            }
        } else {
            // Every enumerated path is blocked; confirm exactly (an active path beyond the
            // enumeration bounds would otherwise slip through the covering heuristic).
            if (!new MsepTest(probe).checkIndependence(x, y, b).isIndependent()) {
                closureLongPathMisses++;
                return false;
            }

            String key = magKey(cand);
            if (!tried.add(key)) return false;   // separating state already adjudicated

            if (!cand.paths().isLegalMag()) {
                // A separating assignment that is not itself a MAG. The obstruction is an
                // ancestrality violation or a non-maximality, so the SAME repair family that
                // services a post-stamp illegality applies -- the only difference is that it
                // is read off the candidate rather than the stamped candidate. (PKE8's
                // 5-node V1--V5 case: de-collider-izing V3 on the V1--V3--V5 leg via the V1
                // side closed the directed cycle V3->V1->V5->V3; breaking it is on the road
                // to the in-class witness.)
                closureIllegalCands++;
                if (movesLeft == 0) return false;
                moves = illegalityRepairMoves(pag, cand, pins, relaxed);
            } else {
                closureCandidatesEmitted++;

                // Class membership, certified as seedMags certifies seeds: pre-stamp, by
                // MagToPag equality against the base PAG.
                boolean inClass = new MagToPag(new EdgeListGraph(cand))
                        .convert(false, excludeSelectionBias).equals(basePag);

                if (inClass == escape) {
                    // Separating and legal, but on the wrong side of the class boundary.
                    // The staged search never meets this state because LegEnumerator walks
                    // only in-class representatives; this search reaches the class the other
                    // way round, by repairing the marks that MagToPag over- or
                    // under-determines relative to the class. See {@link #classRepairMoves}.
                    closureClassFiltered++;
                    if (movesLeft == 0) return false;
                    moves = classRepairMoves(pag, cand, pins, basePag, excludeSelectionBias, relaxed);
                } else {
                    GateStatus st = closureGateAndCommit(cand, x, y, b, pValue, excludeSelectionBias,
                            removed, edgeForLog,
                            escape ? () -> closureEscapeCommits++ : () -> closureInClassCommits++);

                    if (st == GateStatus.COMMITTED) return true;
                    if (movesLeft == 0) return false;

                    moves = switch (st) {
                        case ILLEGAL_MAG -> illegalityRepairMoves(pag, stamped, pins, relaxed);
                        case INDUCING_PATH -> inducingRepairMoves(pag, probe, pins, x, y, relaxed);
                        default -> List.of();   // STAMP_REFUSED cannot recur; BATTERY is terminal
                    };
                }
            }
        }

        for (CoverMove mv : moves) {
            // Skip no-op moves (every assignment already holds): they would burn budget
            // re-deriving the same state one level deeper.
            boolean noop = true;
            for (Assign a : mv.assigns()) {
                if (cand.getEndpoint(a.from(), a.at()) != a.end()) {
                    noop = false;
                    break;
                }
            }
            if (noop) continue;

            List<Endpoint> saved = new ArrayList<>();
            List<String> addedPins = new ArrayList<>();
            applyMove(cand, pins, mv, saved, addedPins);

            boolean done = closureCoverDfs(cand, pins, paths, movesLeft - 1, pag, basePag, x, y, b,
                    pValue, excludeSelectionBias, removed, edgeForLog, escape, relaxed, tried, deadline);

            undoMove(cand, pins, mv, saved, addedPins);

            if (done) return true;
        }

        return false;
    }

    /**
     * Repair moves for a separating, legal candidate that landed OUTSIDE the current class.
     * The class boundary is not a path property, so no cover move targets it; but it is
     * legible as a mark-by-mark difference between the candidate's own PAG and the base PAG.
     * For every slot where {@code MagToPag(cand)} determines a mark that the class leaves as
     * a circle (or determines the other way), propose the assignment ON THE CANDIDATE that
     * relaxes it: a candidate arrowhead that the class does not entail becomes a tail (with
     * the legal-shape companion, i.e. a reversal), a candidate tail becomes an arrowhead.
     * <p>
     * This is the closure-side image of LegEnumerator's walk. The staged search never sees an
     * out-of-class state because it only ever enumerates in-class representatives; this search
     * arrives at the class from outside, and each such difference names an edge whose
     * orientation has to change to get back in. In PKE8's 5-node V1--V5 case the candidate's
     * PAG carried V2 o-&gt; V5 where the class has V2 o-o V5 -- V5 had become an unshielded
     * collider -- and relaxing exactly that arrowhead is the second reversal that the staged
     * search's winning LEG performs.
     * <p>
     * Purely a move generator: class membership itself is still certified by MagToPag
     * equality, and every candidate still passes the same gates.
     */
    private List<CoverMove> classRepairMoves(Graph pag, Graph cand, Map<String, Endpoint> pins,
                                             Graph basePag, boolean excludeSelectionBias, boolean relaxed)
            throws InterruptedException {
        List<CoverMove> out = new ArrayList<>();

        Graph candPag = new MagToPag(new EdgeListGraph(cand)).convert(false, excludeSelectionBias);

        for (Edge e : basePag.getEdges()) {
            String un = e.getNode1().getName(), wn = e.getNode2().getName();
            proposeMarkRepair(pag, cand, pins, basePag, candPag, un, wn, out, relaxed);
            proposeMarkRepair(pag, cand, pins, basePag, candPag, wn, un, out, relaxed);
        }

        // AGGREGATE CLASS REPAIR, tried first. Class membership is a GLOBAL property of the
        // assignment: relaxing one over-determined mark generally leaves the candidate still
        // out of class, so repairing mark-by-mark costs one move per difference and the move
        // budget -- not the move set -- becomes what decides whether the witness is reachable.
        // That is a scaling trap: the number of differing marks grows with the model while the
        // budget does not. PKE8's V1--V5 class made it concrete -- the winning representative
        // sits five endpoint-changes from the Zhang MAG, so it was unreachable at the default
        // budget of 4 and appeared only when the budget was raised. Applying every proposed
        // relaxation at once puts the class one move away regardless of how many marks differ,
        // which is the same lesson the aggregate fork-flip taught. Conflicting proposals (the
        // same slot wanted both ways) mean no single composite exists, so only the individual
        // moves are offered.
        if (out.size() > 1) {
            Map<String, Assign> union = new LinkedHashMap<>();
            boolean conflict = false;
            for (CoverMove mv : out) {
                for (Assign a : mv.assigns()) {
                    Assign prev = union.putIfAbsent(slotKey(a.from(), a.at()), a);
                    if (prev != null && prev.end() != a.end()) {
                        conflict = true;
                        break;
                    }
                }
                if (conflict) break;
            }
            if (!conflict) out.addFirst(new CoverMove(new ArrayList<>(union.values())));
        }

        return dedupMoves(out);
    }

    /**
     * One slot of {@link #classRepairMoves}: the mark at {@code atName} on the edge
     * {@code fromName}--{@code atName}. Nodes are resolved by NAME in each graph, since the
     * PAGs are built by conversion and need not share node objects with the candidate.
     */
    private void proposeMarkRepair(Graph pag, Graph cand, Map<String, Endpoint> pins,
                                   Graph basePag, Graph candPag, String fromName, String atName,
                                   List<CoverMove> out, boolean relaxed) {
        Node bf = basePag.getNode(fromName), ba = basePag.getNode(atName);
        Node pf = candPag.getNode(fromName), pa = candPag.getNode(atName);
        if (bf == null || ba == null || pf == null || pa == null) return;
        if (!candPag.isAdjacentTo(pf, pa)) return;

        Endpoint want = basePag.getEndpoint(bf, ba);
        Endpoint got = candPag.getEndpoint(pf, pa);
        if (want == got) return;

        Node cf = cand.getNode(fromName), ca = cand.getNode(atName);
        if (cf == null || ca == null || !cand.isAdjacentTo(cf, ca)) return;

        // Only the candidate's own mark at this slot is movable; MagToPag may have derived
        // the differing mark from elsewhere, in which case another slot's repair carries it.
        if (got == Endpoint.ARROW && cand.getEndpoint(cf, ca) == Endpoint.ARROW) {
            addTailMove(pag, cand, pins, cf, ca, out, relaxed);
        } else if (got == Endpoint.TAIL && cand.getEndpoint(cf, ca) == Endpoint.TAIL
                && slotCanBe(pag, pins, cf, ca, Endpoint.ARROW, relaxed)) {
            out.add(new CoverMove(List.of(new Assign(cf, ca, Endpoint.ARROW))));
        }
    }

    /**
     * Repair moves for a stamped candidate {@code isLegalMag} rejected: locate each
     * ancestrality violation -- a bidirected edge with a directed path between its endpoints
     * (almost-directed cycle) or a directed edge opposed by a directed path (directed cycle)
     * -- and each non-maximality (an inducing path over the empty set between a nonadjacent
     * pair), and propose the assignments that break them. The bidirected edge itself is
     * typically stamp-pinned, so {@link #slotCanBe} steers repairs at the directed path,
     * which is exactly where the staged search's winning LEGs differ from the Zhang MAG.
     */
    private List<CoverMove> illegalityRepairMoves(Graph pag, Graph stamped, Map<String, Endpoint> pins,
                                                  boolean relaxed) {
        List<CoverMove> out = new ArrayList<>();

        for (Edge e : stamped.getEdges()) {
            Node u = e.getNode1(), w = e.getNode2();
            Endpoint atU = stamped.getEndpoint(w, u), atW = stamped.getEndpoint(u, w);

            if (atU == Endpoint.ARROW && atW == Endpoint.ARROW) {          // u <-> w
                // An almost-directed cycle u <-> w with u ~> w has TWO resolutions: break the
                // directed path, or relax the bidirected edge itself to u --> w (which is not
                // a cycle at all). Only the first was generated, and the second is what
                // PKE8's V4--V5 family needs -- there the winning LEG differs from the
                // candidate purely by carrying V5 --> V3 where the search had cut to V5 <-> V3.
                List<Node> p = firstDirectedPath(stamped, u, w, closureMaxPathLength);
                if (p != null) {
                    addPathBreakMoves(pag, pins, p, out, relaxed);
                    addTailMove(pag, stamped, pins, w, u, out, relaxed);       // u <-> w  ==>  u --> w
                } else {
                    p = firstDirectedPath(stamped, w, u, closureMaxPathLength);
                    if (p != null) {
                        addPathBreakMoves(pag, pins, p, out, relaxed);
                        addTailMove(pag, stamped, pins, u, w, out, relaxed);   // u <-> w  ==>  w --> u
                    }
                }
            } else if (atU == Endpoint.TAIL && atW == Endpoint.ARROW) {    // u --> w
                List<Node> p = firstDirectedPath(stamped, w, u, closureMaxPathLength);
                if (p != null) addPathBreakMoves(pag, pins, p, out, relaxed);       // w ~> u closes a cycle
            }
        }

        // Non-maximality: an inducing path between a nonadjacent pair.
        List<Node> nodes = stamped.getNodes();
        for (int i = 0; i < nodes.size() && out.isEmpty(); i++) {
            for (int j = i + 1; j < nodes.size() && out.isEmpty(); j++) {
                Node u = nodes.get(i), w = nodes.get(j);
                if (stamped.isAdjacentTo(u, w)) continue;
                List<Node> ip = firstInducingPathOverEmpty(stamped, u, w, closureMaxPathLength);
                if (ip != null) addInducingBreakMoves(pag, stamped, pins, ip, u, w, out, relaxed);
            }
        }

        return out;
    }

    /**
     * Repair moves for a prong-(A) rejection: break the removed pair's inducing path in the
     * deleted graph, by de-collider-izing an interior node or cutting its ancestry to the
     * endpoints.
     */
    private List<CoverMove> inducingRepairMoves(Graph pag, Graph probe, Map<String, Endpoint> pins,
                                                Node x, Node y, boolean relaxed) {
        List<CoverMove> out = new ArrayList<>();
        List<Node> ip = firstInducingPathOverEmpty(probe, x, y, closureMaxPathLength);
        if (ip != null) addInducingBreakMoves(pag, probe, pins, ip, x, y, out, relaxed);
        return out;
    }

    /**
     * Assignments that break one link of a directed path: for each edge p --&gt; q on it,
     * the reversal (arrowhead at p, tail at q -- the LEG-style fix, proposed first) and the
     * bidirected cut (arrowhead at p alone), wherever the PAG and pins allow.
     */
    private void addPathBreakMoves(Graph pag, Map<String, Endpoint> pins, List<Node> path,
                                   List<CoverMove> out, boolean relaxed) {
        for (int i = 0; i < path.size() - 1; i++) {
            Node p = path.get(i), q = path.get(i + 1);
            boolean arrowAtP = slotCanBe(pag, pins, q, p, Endpoint.ARROW, relaxed);
            if (arrowAtP && slotCanBe(pag, pins, p, q, Endpoint.TAIL, relaxed)) {
                out.add(new CoverMove(List.of(
                        new Assign(q, p, Endpoint.ARROW),
                        new Assign(p, q, Endpoint.TAIL))));
            }
            if (arrowAtP) {
                out.add(new CoverMove(List.of(new Assign(q, p, Endpoint.ARROW))));
            }
        }
    }

    /**
     * Assignments that break an inducing path over the empty set: de-collider-ize an interior
     * node (tail move with its legal-shape companion), or cut its ancestry to the endpoints
     * (individual and aggregate arrowhead cuts on its outgoing directed edges), wherever the
     * PAG and pins allow.
     */
    private void addInducingBreakMoves(Graph pag, Graph g, Map<String, Endpoint> pins,
                                       List<Node> path, Node u, Node w, List<CoverMove> out,
                                       boolean relaxed) {
        for (int i = 1; i < path.size() - 1; i++) {
            Node prev = path.get(i - 1), m = path.get(i), next = path.get(i + 1);

            addTailMove(pag, g, pins, prev, m, out, relaxed);
            addTailMove(pag, g, pins, next, m, out, relaxed);

            List<Assign> quench = new ArrayList<>();
            for (Node z : g.getAdjacentNodes(m)) {
                if (g.getEndpoint(z, m) == Endpoint.TAIL && g.getEndpoint(m, z) == Endpoint.ARROW
                        && slotCanBe(pag, pins, z, m, Endpoint.ARROW, relaxed)) {
                    Assign cut = new Assign(z, m, Endpoint.ARROW);
                    out.add(new CoverMove(List.of(cut)));
                    quench.add(cut);
                }
            }
            if (quench.size() > 1) out.add(new CoverMove(List.copyOf(quench)));
        }
    }

    /**
     * First directed path from {@code from} to {@code to} (edges with a tail at the source and
     * an arrowhead at the target), BFS, bounded; null if none within the bound.
     */
    private List<Node> firstDirectedPath(Graph g, Node from, Node to, int maxLen) {
        Map<Node, Node> parent = new LinkedHashMap<>();
        Deque<Node> queue = new ArrayDeque<>();
        Map<Node, Integer> depth = new LinkedHashMap<>();
        queue.add(from);
        depth.put(from, 0);

        while (!queue.isEmpty()) {
            Node cur = queue.removeFirst();
            if (depth.get(cur) >= maxLen) continue;
            for (Node z : g.getAdjacentNodes(cur)) {
                if (depth.containsKey(z)) continue;
                if (g.getEndpoint(z, cur) == Endpoint.TAIL && g.getEndpoint(cur, z) == Endpoint.ARROW) {
                    parent.put(z, cur);
                    depth.put(z, depth.get(cur) + 1);
                    if (z.equals(to)) {
                        LinkedList<Node> path = new LinkedList<>();
                        for (Node n = z; n != null; n = parent.get(n)) path.addFirst(n);
                        return path;
                    }
                    queue.addLast(z);
                }
            }
        }
        return null;
    }

    /**
     * First inducing path over the empty conditioning set between {@code u} and {@code w}:
     * a path on which every interior node is a collider AND an ancestor of {@code u} or
     * {@code w}. Bounded DFS; null if none within the bound.
     */
    private List<Node> firstInducingPathOverEmpty(Graph g, Node u, Node w, int maxLen) {
        Deque<Node> path = new ArrayDeque<>();
        Set<Node> onPath = new HashSet<>();
        path.addLast(u);
        onPath.add(u);
        return inducingDfs(g, u, w, maxLen, path, onPath);
    }

    private List<Node> inducingDfs(Graph g, Node cur, Node w, int maxLen,
                                   Deque<Node> path, Set<Node> onPath) {
        if (cur.equals(w)) {
            return path.size() >= 3 ? new ArrayList<>(path) : null;
        }
        if (path.size() > maxLen) return null;

        Node u = path.peekFirst();
        List<Node> asList = new ArrayList<>(path);

        for (Node next : g.getAdjacentNodes(cur)) {
            if (onPath.contains(next)) continue;

            // The node BEFORE next (i.e. cur) becomes interior once we extend; check its
            // collider/ancestry duty on the extended path (skip for the first hop, where
            // cur is the endpoint u).
            if (asList.size() >= 2) {
                Node prev = asList.get(asList.size() - 2);
                boolean collider = g.getEndpoint(prev, cur) == Endpoint.ARROW
                        && g.getEndpoint(next, cur) == Endpoint.ARROW;
                if (!collider) continue;
                if (!(g.paths().isAncestorOf(cur, u) || g.paths().isAncestorOf(cur, w))) continue;
            }

            path.addLast(next);
            onPath.add(next);
            List<Node> found = inducingDfs(g, next, w, maxLen, path, onPath);
            onPath.remove(next);
            path.removeLast();
            if (found != null) return found;
        }
        return null;
    }

    /**
     * Moves that could block the given active path, respecting the PAG's invariant marks and
     * the current pins. Collider/ancestry STATUS is judged on the stamped view (what the gate
     * evaluates); feasibility on the PAG and pins; assignments apply to the pre-stamp
     * candidate. Three move families:
     * <p>
     * (1) COLLIDER-IZE: an interior node m outside S that is not yet a collider on its path
     * edges gets arrowheads in from both path neighbours -- proposed only when the PAG's
     * invariant directed edges do not already force m into An(S), since then no in-scope
     * assignment makes the collider block.
     * <p>
     * (2) DESCENDANT CUT: an interior node m outside S that IS a collider but remains an
     * ancestor of S (so the collider does not block). For each outgoing directed edge
     * m --&gt; w whose mark at m is free, a cut converts it to m &lt;-&gt; w, severing that
     * ancestry route; individually, and -- when several are cuttable -- as one aggregate
     * "quench" move that severs them all at once. The quench is the closure analog of the
     * staged search's makeCollider stamping arrowheads in from every path neighbour: the
     * seed-9025-style witness (all of a fork's outgoing edges turned bidirected so the
     * stamped collider stops being an ancestor and prong (A)'s inducing path dies) is
     * reached in one step. Cuts one hop deeper (m --&gt; w fixed but w --&gt; z free) are
     * not generated; the budget and other triples give partial reach, and PKE residue will
     * show whether deeper cuts are ever needed.
     * <p>
     * (3) DE-COLLIDER-IZE: an interior node m in S sitting as a collider frees one side to a
     * tail -- with a companion arrowhead at the far end when the far mark is a tail, since
     * tail--tail is an undirected (selection) edge, illegal here; if the companion is not
     * available the move is infeasible.
     */
    private List<CoverMove> coverMoves(Graph pag, Graph stamped, Graph cand, Map<String, Endpoint> pins,
                                       List<Node> path, List<List<Node>> allPaths, Set<Node> S,
                                       boolean relaxed) {
        List<CoverMove> out = new ArrayList<>();

        for (int i = 1; i < path.size() - 1; i++) {
            Node a = path.get(i - 1), m = path.get(i), c = path.get(i + 1);

            if (!S.contains(m)) {
                if (!stamped.isDefCollider(a, m, c)) {
                    if (slotCanBe(pag, pins, a, m, Endpoint.ARROW, relaxed)
                            && slotCanBe(pag, pins, c, m, Endpoint.ARROW, relaxed)
                            && !invariantAncestorOfS(pag, m, S)) {
                        out.add(new CoverMove(List.of(
                                new Assign(a, m, Endpoint.ARROW),
                                new Assign(c, m, Endpoint.ARROW))));
                    }
                } else if (ancestorInS(stamped, m, S) && !invariantAncestorOfS(pag, m, S)) {
                    List<Assign> quench = new ArrayList<>();
                    for (Node w : stamped.getAdjacentNodes(m)) {
                        if (stamped.getEndpoint(w, m) == Endpoint.TAIL          // tail at m
                                && stamped.getEndpoint(m, w) == Endpoint.ARROW  // m --> w
                                && slotCanBe(pag, pins, w, m, Endpoint.ARROW, relaxed)) {
                            Assign cut = new Assign(w, m, Endpoint.ARROW);
                            out.add(new CoverMove(List.of(cut)));
                            quench.add(cut);
                        }
                    }
                    if (quench.size() > 1) {
                        out.add(new CoverMove(List.copyOf(quench)));
                    }

                    // ANCESTRY ROUTE BREAK. A collider blocks only if it has no descendant in
                    // S, and the route from m into S may run several edges deep -- severing
                    // only edges INCIDENT to m cannot reach past the first hop. So locate the
                    // directed route and offer the same reversal/cut breaks used on cycles, at
                    // every edge along it. PKE8's 6-observed V5--V6 class is the case that
                    // needs this: cutting V1 --> V4 still leaves V1 --> V2 --> V4 alive, the
                    // first hop is frozen by a Phase-A pin, and the staged winner breaks the
                    // route at its SECOND edge, reversing V2 --> V4 to V4 --> V2.
                    for (Node z : S) {
                        List<Node> route = firstDirectedPath(stamped, m, z, closureMaxPathLength);
                        if (route != null) {
                            addPathBreakMoves(pag, pins, route, out, relaxed);
                            break;
                        }
                    }
                }
            } else {
                if (stamped.isDefCollider(a, m, c)) {
                    addTailMove(pag, cand, pins, a, m, out, relaxed);
                    addTailMove(pag, cand, pins, c, m, out, relaxed);
                }
            }
        }

        // (4) AGGREGATE FORK-FLIP -- the Stage-2b move, ported. A fork can lie on several
        // active paths at once, and the staged search's makeCollider stamps arrowheads in
        // from EVERY neighbour occurring beside it on ANY of them, in a single step. The
        // per-path move above only ever stamps the two neighbours on the path being covered,
        // and reaching the same state pairwise means passing through intermediate assignments
        // that are typically illegal or off-class -- so the composite is unreachable by parts
        // and has to be generated whole. This is what PKE8's V3--V4 / V3--V5 class needs: the
        // fork sits on two active paths, and only the simultaneous flip both blocks them and
        // stays in class.
        Map<Node, Set<Node>> forkNbrs = new LinkedHashMap<>();
        for (List<Node> p : allPaths) {
            if (!isActiveGivenS(stamped, p, S)) continue;
            for (int i = 1; i < p.size() - 1; i++) {
                Node a = p.get(i - 1), m = p.get(i), c = p.get(i + 1);
                if (S.contains(m)) continue;
                if (stamped.isDefCollider(a, m, c)) continue;
                forkNbrs.computeIfAbsent(m, k -> new LinkedHashSet<>()).add(a);
                forkNbrs.get(m).add(c);
            }
        }

        for (Map.Entry<Node, Set<Node>> en : forkNbrs.entrySet()) {
            Node m = en.getKey();
            if (invariantAncestorOfS(pag, m, S)) continue;

            List<Assign> assigns = new ArrayList<>();
            boolean feasible = true;
            for (Node w : en.getValue()) {
                if (!slotCanBe(pag, pins, w, m, Endpoint.ARROW, relaxed)) {
                    feasible = false;
                    break;
                }
                assigns.add(new Assign(w, m, Endpoint.ARROW));
            }
            if (feasible && !assigns.isEmpty()) out.add(new CoverMove(assigns));
        }

        return dedupMoves(out);
    }

    /**
     * Drops moves with an identical assignment set (the aggregate fork-flip coincides with the
     * per-path collider-ization whenever the fork lies on exactly one active path); duplicates
     * are harmless but would spend budget re-deriving the same state.
     */
    private List<CoverMove> dedupMoves(List<CoverMove> moves) {
        Set<String> seen = new HashSet<>();
        List<CoverMove> out = new ArrayList<>();
        for (CoverMove mv : moves) {
            List<String> toks = new ArrayList<>();
            for (Assign a : mv.assigns()) toks.add(slotKey(a.from(), a.at()) + "=" + a.end());
            Collections.sort(toks);
            if (seen.add(String.join("|", toks))) out.add(mv);
        }
        return out;
    }

    /**
     * A de-collider-ize move: tail at m on far--m, with the legal-shape companion arrowhead
     * at the far end when needed (see {@link #coverMoves}, family 3). Adds nothing when
     * infeasible.
     */
    private void addTailMove(Graph pag, Graph cand, Map<String, Endpoint> pins, Node far, Node m,
                             List<CoverMove> out) {
        addTailMove(pag, cand, pins, far, m, out, false);
    }

    private void addTailMove(Graph pag, Graph cand, Map<String, Endpoint> pins, Node far, Node m,
                             List<CoverMove> out, boolean relaxed) {
        if (!slotCanBe(pag, pins, far, m, Endpoint.TAIL, relaxed)) return;

        List<Assign> assigns = new ArrayList<>();
        assigns.add(new Assign(far, m, Endpoint.TAIL));

        if (cand.getEndpoint(m, far) == Endpoint.TAIL) {
            if (!slotCanBe(pag, pins, m, far, Endpoint.ARROW, relaxed)) return;   // tail--tail illegal
            assigns.add(new Assign(m, far, Endpoint.ARROW));
        }

        out.add(new CoverMove(assigns));
    }

    /**
     * True iff the endpoint at {@code at} on the edge {@code from}--{@code at} may be assigned
     * {@code want}: the PAG's mark there is a circle or already {@code want}, and no pin says
     * otherwise.
     */
    private boolean slotCanBe(Graph pag, Map<String, Endpoint> pins, Node from, Node at, Endpoint want) {
        return slotCanBe(pag, pins, from, at, want, false);
    }

    /**
     * True iff the endpoint at {@code at} on the edge {@code from}--{@code at} may be assigned
     * {@code want}. Normally the PAG's non-circle marks are treated as invariants of the class
     * and frozen; under {@code relaxed} that restriction is lifted and only Phase A's
     * stamp-compatibility pins constrain the slot.
     * <p>
     * The relaxation exists because the freeze is only as sound as the PAG. A true PAG's tails
     * and arrowheads hold in EVERY class member, so freezing them loses nothing -- but the
     * interim PAGs here come from {@code MagToPag}, and at least one of them over-commits: in
     * PKE8's V3--V7 class the PAG carries V4 --&gt; V7 while a MAG verified Markov-equivalent
     * to the Zhang MAG (zero m-separation differences over all pairs and conditioning sets)
     * carries V7 &lt;-&gt; V4. Freezing that tail put the only witness outside the search space
     * entirely, which is why the DFS reported no moves at all. The staged search never meets
     * this because LegEnumerator walks MAG space from the Zhang MAG and never consults PAG
     * marks as constraints.
     * <p>
     * Relaxation costs nothing in soundness: class membership is still certified by MagToPag
     * equality at emission and every candidate still passes the same gates. It is applied only
     * as a fallback, when the frozen search yields no move at all, so the common case keeps the
     * smaller space.
     */
    private boolean slotCanBe(Graph pag, Map<String, Endpoint> pins, Node from, Node at, Endpoint want,
                              boolean relaxed) {
        if (!relaxed) {
            Endpoint e = pag.getEndpoint(from, at);
            if (e != Endpoint.CIRCLE && e != want) return false;
        }
        Endpoint pinned = pins.get(slotKey(from, at));
        return pinned == null || pinned == want;
    }

    /**
     * True iff the PAG's INVARIANT directed edges alone certify m in An(S): a directed path
     * m --&gt; ... --&gt; s for some s in S using only edges with a tail at the source and an
     * arrowhead at the target in the PAG. Such ancestry holds in EVERY member of the class, so
     * a collider at m can never block for any candidate this generator produces.
     */
    private boolean invariantAncestorOfS(Graph pag, Node m, Set<Node> S) {
        Deque<Node> queue = new ArrayDeque<>();
        Set<Node> seen = new HashSet<>();
        queue.add(m);
        seen.add(m);

        while (!queue.isEmpty()) {
            Node cur = queue.removeFirst();
            if (!cur.equals(m) && S.contains(cur)) return true;

            for (Node w : pag.getAdjacentNodes(cur)) {
                if (seen.contains(w)) continue;
                if (pag.getEndpoint(w, cur) == Endpoint.TAIL
                        && pag.getEndpoint(cur, w) == Endpoint.ARROW) {   // cur --> w invariant
                    seen.add(w);
                    queue.addLast(w);
                }
            }
        }

        return false;
    }

    /**
     * Applies a move, recording prior endpoints so {@link #undoMove} can restore them.
     * <p>
     * Deliberately does NOT pin the slots it assigns. The pin map carries only the HARD
     * constraints of the subproblem -- the PAG's invariant marks (consulted directly) and
     * Phase A's stamp-compatibility requirements -- and a move's own assignment is not one of
     * those: it is a hypothesis the search may need to revise. Pinning moves made them
     * irreversible within a branch and so blocked repairs from undoing them, which is exactly
     * how PKE8's V4--V5 family stalled: a cover move cut V2--V5 to bidirected and pinned the
     * arrowhead, the stamp then minted an inducing path over the nonadjacent pair (V2,V4),
     * and the one repair that fixes it -- relaxing that same arrowhead to a tail, i.e. the
     * reversal the staged search's winning LEG performs -- was refused by the move's own pin.
     * Termination does not depend on these pins: depth is bounded by the move budget and
     * separating states are deduplicated by {@code magKey}.
     */
    private void applyMove(Graph cand, Map<String, Endpoint> pins, CoverMove mv,
                           List<Endpoint> saved, List<String> addedPins) {
        for (Assign a : mv.assigns()) {
            saved.add(cand.getEndpoint(a.from(), a.at()));
            cand.setEndpoint(a.from(), a.at(), a.end());
        }
    }

    /**
     * Undoes a move in reverse order and releases only the pins it added.
     */
    private void undoMove(Graph cand, Map<String, Endpoint> pins, CoverMove mv,
                          List<Endpoint> saved, List<String> addedPins) {
        for (int i = mv.assigns().size() - 1; i >= 0; i--) {
            Assign a = mv.assigns().get(i);
            cand.setEndpoint(a.from(), a.at(), saved.get(i));
        }
        for (String k : addedPins) {
            pins.remove(k);
        }
    }

    /**
     * Simple x..y paths in the (class-invariant) skeleton, bounded in length and count,
     * shortest first so cover moves target cheap paths early. Marks-blind: whether a path is
     * BLOCKED is a property of the candidate, so activity is judged per-candidate by
     * {@link #firstActivePath}; the enumeration here fixes only the universe of paths the
     * covering heuristic can see.
     */
    private List<List<Node>> boundedSkeletonPaths(Graph g, Node x, Node y, int maxLen, int maxPaths) {
        List<List<Node>> out = new ArrayList<>();
        Deque<Node> path = new ArrayDeque<>();
        Set<Node> onPath = new HashSet<>();
        path.addLast(x);
        onPath.add(x);
        skeletonDfs(g, x, y, maxLen, maxPaths, path, onPath, out);
        out.sort(Comparator.<List<Node>>comparingInt(List::size));
        return out;
    }

    private void skeletonDfs(Graph g, Node cur, Node y, int maxLen, int maxPaths,
                             Deque<Node> path, Set<Node> onPath, List<List<Node>> out) {
        if (out.size() >= maxPaths) return;

        if (cur.equals(y)) {
            if (path.size() >= 3) out.add(new ArrayList<>(path));   // exclude the direct edge
            return;
        }

        if (path.size() > maxLen) return;

        for (Node next : g.getAdjacentNodes(cur)) {
            if (onPath.contains(next)) continue;
            path.addLast(next);
            onPath.add(next);
            skeletonDfs(g, next, y, maxLen, maxPaths, path, onPath, out);
            onPath.remove(next);
            path.removeLast();
            if (out.size() >= maxPaths) return;
        }
    }

    /**
     * The first enumerated path that is m-connecting given S in {@code probe} (the stamped
     * candidate minus the edge), or null if all are blocked.
     */
    private List<Node> firstActivePath(Graph probe, List<List<Node>> paths, Set<Node> S) {
        for (List<Node> p : paths) {
            if (isActiveGivenS(probe, p, S)) return p;
        }
        return null;
    }

    // ================== END CLOSURE-COVER WITNESS SEARCH ==================

    /**
     * DELETED-PAIR BATTERY.  For each removed pair {x,y}: enumerate every conditioning set
     * $Z$ over the remaining variables with |Z| <= batteryZMax that the candidate MAG
     * ENTAILS separates x and y (a graphical m-separation query on the candidate), and
     * verify each entailed statement against the independence test; refuse on the first
     * rejection.  Passing vacuously when the gate is LEGALITY_PLUS_SEPARATOR.  Testing only
     * entailed statements means a Markov-preserving commit can never be refused at the
     * oracle; in sample, test noise produces refusals (reach cost), never unsound commits
     * beyond what the gate design admits.  Both the zero-false-refusal claim and the 548/548
     * coverage figure are stated for WITHIN-CLASS candidates (H' Markov-equivalent to the
     * current I-map state); a pass-3 fork-flip candidate is outside that scope -- the battery
     * remains sound to run on it, but the note's exactness accounting does not cover such
     * commits.  Interruption during testing is treated as a
     * refusal -- the safe direction while the search winds down -- with the interrupt flag
     * restored.
     */
    private boolean deletedPairBatteryPasses(Graph mag, List<Edge> removed) {
        if (commitGate != CommitGate.DELETED_PAIR_BATTERY) return true;
        batteryEvals++;
        try {
            MsepTest entails = new MsepTest(mag);
            for (Edge f : removed) {
                Node x = f.getNode1(), y = f.getNode2();
                List<Node> others = new ArrayList<>(mag.getNodes());
                others.remove(x);
                others.remove(y);
                int kMax = Math.min(batteryZMax, others.size());
                int[] idx = new int[Math.max(kMax, 1)];
                for (int k = 0; k <= kMax; k++) {
                    if (!batteryScanSubsets(entails, x, y, others, k, 0, 0, idx)) {
                        batteryRefusals++;
                        return false;
                    }
                }
            }
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            batteryRefusals++;
            return false;
        }
    }

    /**
     * Recursive k-subset scan over candidate conditioning sets; returns false on the first
     * statement the candidate entails but the test rejects.
     */
    private boolean batteryScanSubsets(MsepTest entails, Node x, Node y, List<Node> others,
                                       int k, int start, int depth, int[] idx) throws InterruptedException {
        if (depth == k) {
            Set<Node> z = new HashSet<>();
            for (int i = 0; i < k; i++) z.add(others.get(idx[i]));
            if (entails.checkIndependence(x, y, z).isIndependent()) {
                batteryStatementsTested++;
                boolean ok = this.test.checkIndependence(x, y, z).isIndependent();
                if (!ok && isFocus(x, y)) {
                    Set<String> names = new TreeSet<>();
                    for (Node n : z) names.add(n.getName());
                    focusBatteryZ.add(names);
                }
                return ok;
            }
            return true;
        }
        for (int i = start; i < others.size(); i++) {
            idx[depth] = i;
            if (!batteryScanSubsets(entails, x, y, others, k, i + 1, depth + 1, idx)) return false;
        }
        return true;
    }

    /**
     * Sets the commit gate; see {@link CommitGate}.
     *
     * @param commitGate the gate.
     */
    public void setCommitGate(CommitGate commitGate) {
        this.commitGate = commitGate;
    }

    /**
     * Sets the maximum conditioning-set size for the deleted-pair battery.
     *
     * @param batteryZMax the bound; PKE6 evidence supports 2 at the enumerated scope.
     */
    public void setBatteryZMax(int batteryZMax) {
        this.batteryZMax = batteryZMax;
    }

    /**
     * Sets the maximum number of nodes droppable from RB's blocking set during separator search.
     *
     * @param maxBlockingSetRemovals the bound; -1 (default) for all subsets.
     */
    public void setMaxBlockingSetRemovals(int maxBlockingSetRemovals) {
        this.maxBlockingSetRemovals = maxBlockingSetRemovals;
    }

    /**
     * Sets whether the within-class candidate generator is the class walk (single mark changes
     * over the Markov equivalence class) rather than the staged seeds/LEG/fork-flip search. The
     * commit gates are identical either way, so this changes which candidates are proposed and in
     * what order, never what is accepted. See {@link #useClassWalk}.
     *
     * @param useClassWalk true for the class walk (default), false for the legacy generator.
     */
    public void setUseClassWalk(boolean useClassWalk) {
        this.useClassWalk = useClassWalk;
    }

    /**
     * Sets the class walk's ordering: true (default) for best-first on stamp deficit, false for
     * plain breadth-first. See {@link #classWalkBestFirst}.
     *
     * @param classWalkBestFirst true for best-first ordering.
     */
    public void setClassWalkBestFirst(boolean classWalkBestFirst) {
        this.classWalkBestFirst = classWalkBestFirst;
    }

    /**
     * Sets the per-deletion cap on class members the walk may emit; negative means uncapped.
     * Hitting the cap makes a failure to host INCONCLUSIVE -- see {@link #classWalkMaxCandidates}.
     *
     * @param classWalkMaxCandidates the cap, or negative for uncapped.
     */
    public void setClassWalkMaxCandidates(int classWalkMaxCandidates) {
        this.classWalkMaxCandidates = classWalkMaxCandidates;
    }

    /**
     * Sets the cap on common neighbours the sepset search may add to RB's blocking set; negative
     * means unbounded. See {@link #maxBlockingSetAdditions}.
     *
     * @param maxBlockingSetAdditions the cap, or negative for unbounded.
     */
    public void setMaxBlockingSetAdditions(int maxBlockingSetAdditions) {
        this.maxBlockingSetAdditions = maxBlockingSetAdditions;
    }

    /**
     * Sets whether the sepset search retries with RB run against the skeleton after every oriented
     * pass fails. See {@link #orientationBlindFallback}.
     *
     * @param orientationBlindFallback true to enable the fallback (default).
     */
    public void setOrientationBlindFallback(boolean orientationBlindFallback) {
        this.orientationBlindFallback = orientationBlindFallback;
    }

    /**
     * Sets the maximum number of fork nodes converted to colliders when building an out-of-class
     * seed. Bounded low (1-2) for audit-scale models. Applies to the staged generator and to
     * escape mode; the class walk does not use it. See {@link #seedMags}.
     *
     * @param maxForkFlips the bound.
     */
    public void setMaxForkFlips(int maxForkFlips) {
        this.maxForkFlips = maxForkFlips;
    }

    /**
     * Sets whether pass 3 (certified out-of-class seeds) may run; see {@link #seedMags}. False
     * is "Step-Lemma-pure" mode: every commit is hosted by a representative of the current class
     * -- the canonical Zhang MAG, another LEG, or a fork-flip certified Markov-equivalent -- so
     * an oracle run that terminates at the true PAG with this false is direct Step-Lemma
     * evidence, undiluted by the escape hatch.
     *
     * @param allowClassEscape true to permit the state-level escape pass (the default).
     */
    public void setAllowClassEscape(boolean allowClassEscape) {
        this.allowClassEscape = allowClassEscape;
    }

    /**
     * Sets whether the closure-cover generator replaces the staged representative search; see
     * {@link #useClosureCoverSearch}. Same gates either way -- flipping this changes which
     * candidates are proposed and in what order, never what is accepted. False by default.
     *
     * @param useClosureCoverSearch true to use the closure-cover generator.
     */
    public void setUseClosureCoverSearch(boolean useClosureCoverSearch) {
        this.useClosureCoverSearch = useClosureCoverSearch;
    }

    /**
     * Sets whether a pair the closure-cover search cannot host is handed to the staged
     * generator; see {@link #closureFallbackToStaged}. True by default. Set false to measure
     * the closure search's unaided reach.
     *
     * @param closureFallbackToStaged true to enable the fallback.
     */
    public void setClosureFallbackToStaged(boolean closureFallbackToStaged) {
        this.closureFallbackToStaged = closureFallbackToStaged;
    }

    /**
     * Sets the bound (in nodes) on the skeleton paths the closure-cover search enumerates.
     *
     * @param closureMaxPathLength the bound; see {@link #closureMaxPathLength}.
     */
    public void setClosureMaxPathLength(int closureMaxPathLength) {
        this.closureMaxPathLength = closureMaxPathLength;
    }

    /**
     * Sets the bound on the number of skeleton paths the closure-cover search enumerates.
     *
     * @param closureMaxPaths the bound; see {@link #closureMaxPaths}.
     */
    public void setClosureMaxPaths(int closureMaxPaths) {
        this.closureMaxPaths = closureMaxPaths;
    }

    /**
     * Sets the per-candidate move budget of the closure-cover search; the analog of
     * {@code maxForkFlips}, but counting path-directed assignments.
     *
     * @param maxCoverMoves the budget; see {@link #maxCoverMoves}.
     */
    public void setMaxCoverMoves(int maxCoverMoves) {
        this.maxCoverMoves = maxCoverMoves;
    }

    /**
     * Sets the ceiling on the AUTO move budget; see {@link #maxCoverMoves}.
     *
     * @param closureAutoBudgetCap the cap.
     */
    public void setClosureAutoBudgetCap(int closureAutoBudgetCap) {
        this.closureAutoBudgetCap = closureAutoBudgetCap;
    }

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
     * True, just in case good and restored changes are printed. The algorithm always moves to a legal PAG; if it
     * doesn't, it is restored to the previous PAG, and a "restored" message is printed. Otherwise, a "good" message is
     * printed.
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
     * should be used. False by default.
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
     * Sets the radius for the RB (Recursive Blocking) algorithm, which bounds the scope of the
     * recursive path-blocking search.
     *
     * @param rbRadius the radius to set; -1 for unlimited
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
     * Returns the CachedIndependenceQueries wrapping this run's test, if any,
     * for cache-statistics reporting. Returns null if the test is not cached.
     */
    private CachedIndependenceQueries findCache() {
        if (test instanceof CachedIndependenceQueries c) {
            return c;
        }
        return null;
    }

    /**
     * Sets the maximum length of the discriminating path.
     *
     * @param maxDiscriminatingPathLength the maximum number of steps or nodes
     *                                    allowed in the discriminating path.
     */
    public void setMaxDiscriminatingPathLength(int maxDiscriminatingPathLength) {
        this.maxDiscriminatingPathLength = maxDiscriminatingPathLength;
    }

    /**
     * Sets the timeout for the search algorithm, or -1 for unlimited.
     *
     * @param timeout the maximum time in milliseconds to allow the search to run.
     */
    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    /**
     * Returns the map of committed separating sets discovered during the search. A pair {x, y}
     * has an entry once its edge has been removed by a legal-PAG commit; the value is the
     * conditioning set that separated it. This reflects only committed separations and is rolled
     * back on a reverted removal.
     *
     * @return the separating-set map; never {@code null}, but empty before {@link #search()} runs
     */
    public SepsetMap getSepsetMap() {
        return sepsets;
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
     * Commit gate.  LEGALITY_PLUS_SEPARATOR is FCIT-ZM's gate (MAG legality plus the one
     * test-confirmed separator for the removed pair): FCIT-SL's default, preserving the
     * exactly-as-sound-as-FCIT-ZM contract.  DELETED_PAIR_BATTERY additionally verifies,
     * against the independence test, EVERY separation of the removed pair that the candidate
     * MAG entails with conditioning sets of size at most {@code batteryZMax}.  Motivation
     * (PKE6 audit, N=7, two latents): in all 548 oracle cases where a legal deletion exited
     * Markov space, some false statement concerned the deleted pair itself at |Z| &lt;= 2, so
     * this gate with zMax = 2 caught 548/548; and since it tests only statements the
     * candidate entails, a Markov-preserving commit passes every one -- zero false refusals
     * at the oracle.  In sample, a test rejection refuses the commit: noise costs reach,
     * never soundness.
     */
    public enum CommitGate {
        /**
         * FCIT-ZM's gate: commit a removal on MAG legality plus the one test-confirmed separator
         * for the removed pair. The default, preserving the exactly-as-sound-as-FCIT-ZM contract.
         */
        LEGALITY_PLUS_SEPARATOR,
        /**
         * The legality-plus-separator gate, plus verification against the independence test of
         * every separation of the removed pair that the candidate MAG entails with conditioning
         * sets of size at most {@code batteryZMax}. Refuses the commit on the first rejection.
         */
        DELETED_PAIR_BATTERY
    }

    private enum LegVerdict {SPURIOUS, NOT_SPURIOUS, INDETERMINATE}

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

    /**
     * R0/R4 strategy for the FINAL orientation pass that consults the search's own recorded
     * evidence before ever re-asking the data. Priority order for an unshielded triple
     * &lt;a, b, c&gt;:
     * <p>
     * (1) SEPSET-BACKED. If a separator was recorded for {a, c} -- a committed sepset first,
     * else a sweep-discovered one from {@code foundSepsets} -- the collider verdict is membership:
     * collider iff b is not in the recorded set. This is the original FCI R0 semantics, and it
     * makes the final orientation consistent BY CONSTRUCTION with the deletions the skeleton is
     * built on: the same set that removed the edge decides the triple. A fresh test-based search
     * here could return a DIFFERENT valid separator (pairs typically have several) and orient a
     * collider that contradicts the recorded evidence -- at the oracle the answers coincide, so
     * PKE audits cannot distinguish the two policies, but on data the fresh search's
     * minimality-plus-noise bias mints colliders systematically.
     * <p>
     * (2) CPDAG-BACKED. If {a, c} is nonadjacent in the INITIAL graph, the triple was adjudicated
     * by the score-based CPDAG search, whose collider decisions are jointly optimized and far more
     * stable in sample than per-triple tests; copy its verdict (the GFCI justification -- see
     * {@link #noteInitialColliders}). This is the consumer the {@code initialColliders} field was
     * always meant to have.
     * <p>
     * (3) TEST FALLBACK. Only for triples with no recorded evidence -- which should not normally
     * occur, since every nonadjacent pair is either CPDAG-nonadjacent or was removed by a commit
     * that recorded its separator. Counted in {@code r0TestFallback}; a nonzero count is a
     * diagnostic that evidence is being lost upstream, not business as usual.
     * <p>
     * R4 gets the same split, keyed by the discriminating path's endpoint pair {x, y}: a recorded
     * separator decides collider-vs-tail at v by membership (mirroring the delegate's guards and
     * orientation actions exactly, minus its RB blocking search); pairs never adjudicated by this
     * search fall through to the delegate's recursive-blocking machinery.
     * <p>
     * All lookups are BY NAME, so the strategy is robust to node-identity changes across the
     * MagToPag round trips. The evidence maps are frozen at construction, which happens after the
     * removal loop completes, so they see the final state of {@code sepsets} and
     * {@code foundSepsets}.
     */
    private final class EvidenceBackedR0R4Strategy implements R0R4Strategy {
        private final R0R4StrategyTestBased delegate;
        private final Graph initialGraph;
        private final Set<String> cpdagColliderKeys = new HashSet<>();
        private final Map<String, Set<String>> recordedSepsets = new HashMap<>();

        EvidenceBackedR0R4Strategy(R0R4StrategyTestBased delegate, Graph initialGraph,
                                   Set<Triple> cpdagColliders) {
            this.delegate = delegate;
            this.initialGraph = initialGraph;

            if (cpdagColliders != null) {
                for (Triple t : cpdagColliders) {
                    cpdagColliderKeys.add(tripleKey(t.getX(), t.getY(), t.getZ()));
                }
            }

            // Committed separators first: each one justified a deletion, so it is the
            // authoritative record for its pair.
            for (Set<Node> key : sepsets.keySet()) {
                List<Node> pr = new ArrayList<>(key);
                if (pr.size() != 2) continue;
                Set<Node> s = sepsets.get(pr.get(0), pr.get(1));
                if (s != null) recordedSepsets.put(pairKey(pr.get(0), pr.get(1)), names(s));
            }

            // Then sweep-discovered separators, without overwriting a committed record. (These
            // matter only for pairs somehow nonadjacent without a commit; belt and braces.)
            for (Map.Entry<Set<Node>, Set<Node>> e : foundSepsets.entrySet()) {
                List<Node> pr = new ArrayList<>(e.getKey());
                if (pr.size() != 2) continue;
                recordedSepsets.putIfAbsent(pairKey(pr.get(0), pr.get(1)), names(e.getValue()));
            }
        }

        private Set<String> names(Set<Node> s) {
            Set<String> out = new HashSet<>();
            for (Node n : s) out.add(n.getName());
            return out;
        }

        private String pairKey(Node a, Node c) {
            String u = a.getName(), v = c.getName();
            return u.compareTo(v) <= 0 ? u + "\u0000" + v : v + "\u0000" + u;
        }

        private String tripleKey(Node a, Node b, Node c) {
            return pairKey(a, c) + "@" + b.getName();
        }

        @Override
        public boolean isUnshieldedCollider(Graph graph, Node a, Node b, Node c) {
            // (1) Sepset-backed.
            Set<String> s = recordedSepsets.get(pairKey(a, c));
            if (s != null) {
                r0SepsetBacked++;
                return !s.contains(b.getName());
            }

            // (2) CPDAG-backed.
            Node ia = initialGraph.getNode(a.getName());
            Node ic = initialGraph.getNode(c.getName());
            if (ia != null && ic != null && !initialGraph.isAdjacentTo(ia, ic)) {
                r0CpdagBacked++;
                return cpdagColliderKeys.contains(tripleKey(a, b, c));
            }

            // (3) Test fallback -- no recorded evidence for this pair.
            r0TestFallback++;
            return delegate.isUnshieldedCollider(graph, a, b, c);
        }

        @Override
        public Pair<DiscriminatingPath, Boolean> doDiscriminatingPathOrientation(
                DiscriminatingPath discriminatingPath, int maxBlockingPathLength,
                int maxDiscriminatingPathLength, Graph graph, Set<Node> vNodes)
                throws InterruptedException {
            Node x = discriminatingPath.getX();
            Node w = discriminatingPath.getW();
            Node v = discriminatingPath.getV();
            Node y = discriminatingPath.getY();

            Set<String> s = recordedSepsets.get(pairKey(x, y));

            if (verbose) {
                TetradLogger.getInstance().log("R4 considering DDP x=" + x.getName()
                        + " w=" + w.getName() + " v=" + v.getName() + " y=" + y.getName()
                        + "; recorded sepset(" + x.getName() + "," + y.getName() + ")="
                        + (s == null ? "NONE -> delegating to test" : s)
                        + "; existsIn=" + discriminatingPath.existsIn(graph)
                        + "; mark at " + v.getName() + " on " + y.getName() + "--" + v.getName()
                        + " = " + graph.getEndpoint(y, v));
            }

            if (s == null) {
                // Pair never adjudicated by the removal search (nonadjacent from the CPDAG on).
                // Def. rb-step's provision for exactly this case: find a separator NOW, confirm
                // it by test, RECORD it, and thereafter read it back rather than recompute. The
                // delegate's single-shot RB+test is strictly weaker than the spanning sweep and
                // can fail on interim marks while a confirmed separator exists (observed: DDP
                // endpoints (V5,V1) with the run's own trace holding V5 _||_ V1 | V6, V3), so
                // run the sweep here instead of delegating.
                r4TestFallback++;

                Set<Node> found = searchSepsetForPair(graph, x, y);

                if (found == null) {
                    if (verbose) {
                        TetradLogger.getInstance().log("    R4 pair search: no separator "
                                + "confirmed for (" + x.getName() + "," + y.getName()
                                + "); declining to orient.");
                    }
                    return Pair.of(discriminatingPath, false);
                }

                // Commit to the live sepset map (found at most once, read back thereafter --
                // the (P1)/(P2) discipline) and to this strategy's snapshot, so later passes
                // and any cold re-orientation read this pair sepset-backed. On a genuine DDP
                // the apex's membership is invariant across all separators, so any confirmed
                // set decides the branch identically.
                sepsets.set(x, y, found);
                s = names(found);
                recordedSepsets.put(pairKey(x, y), s);

                if (verbose) {
                    TetradLogger.getInstance().log("    R4 pair search: recorded sepset("
                            + x.getName() + "," + y.getName() + ") = " + found
                            + " (test-confirmed); adjudicating by membership.");
                }
            } else {
                r4SepsetBacked++;
            }

            // Mirror the delegate's guards and orientation actions exactly, adjudicating by the
            // recorded separator: v in Sepset(x, y) => noncollider (tail at v); else collider.

            if (!discriminatingPath.existsIn(graph)) {
                return Pair.of(discriminatingPath, false);
            }

            if (graph.getEndpoint(y, v) != Endpoint.CIRCLE) {
                return Pair.of(discriminatingPath, false);
            }

            if (s.contains(v.getName())) {
                graph.setEndpoint(y, v, Endpoint.TAIL);

                if (verbose) {
                    TetradLogger.getInstance().log("R4 (sepset-backed): oriented "
                            + GraphUtils.pathString(graph, w, v, y) + " from recorded sepset.");
                }

                return Pair.of(discriminatingPath, true);
            } else {
                if (!FciOrient.isArrowheadAllowed(w, v, graph, knowledge)) {
                    return Pair.of(discriminatingPath, false);
                }

                if (!FciOrient.isArrowheadAllowed(y, v, graph, knowledge)) {
                    return Pair.of(discriminatingPath, false);
                }

                graph.setEndpoint(w, v, Endpoint.ARROW);
                graph.setEndpoint(y, v, Endpoint.ARROW);

                if (verbose) {
                    TetradLogger.getInstance().log("R4 (sepset-backed): oriented "
                            + GraphUtils.pathString(graph, w, v, y) + " from recorded sepset.");
                }

                return Pair.of(discriminatingPath, true);
            }
        }

        @Override
        public void setKnowledge(Knowledge knowledge) {
            delegate.setKnowledge(knowledge);
        }

        @Override
        public Knowledge getknowledge() {
            return delegate.getknowledge();
        }

        /**
         * Pair-level spanning separator search on {@code graph}, for discriminating-path
         * endpoint pairs never adjudicated by the removal sweep. Same family as the
         * removal-phase search (NF enumeration over ambiguous blocking-set members;
         * removals over the whole blocking set, common neighbours first; additions of
         * omitted common neighbours) but self-contained on the given graph: the pair is
         * nonadjacent, so the removal sweep's adjacency guards do not apply. Returns the
         * first test-confirmed separator, or null for not-found / budget-exhausted.
         */
        private Set<Node> searchSepsetForPair(Graph graph, Node x, Node y)
                throws InterruptedException {
            final long deadline = (timeout < 0L)
                    ? Long.MAX_VALUE
                    : System.currentTimeMillis() + timeout;

            // Pass 1: propose against the live, mid-orientation graph.
            Set<Node> found = sweepOnView(graph, x, y, deadline, "oriented");
            if (found != null) return found;

            // Pass 2: orientation-blind, the same remedy findIndependenceCheckRecursive uses.
            // R4 runs mid-FciOrient, so `graph` carries marks R0/R1-R3 have already stamped --
            // some of them wrong in exactly the way that hides the separator. RB reads those
            // marks, finds a path it cannot block, and returns no blocking set at all; the NF
            // harvest then has nothing to draw on and the whole candidate family is empty (the
            // observed failure: zero tests executed for this pair). On the all-circles skeleton
            // no triple is a collider, nothing counts as pre-blocked, and the proposal is
            // orientation-independent. This weakens nothing: the blind view only PROPOSES, and
            // every candidate is still confirmed against the test before it is returned.
            Graph blind = new EdgeListGraph(graph);
            for (Edge e : new ArrayList<>(blind.getEdges())) {
                blind.setEndpoint(e.getNode1(), e.getNode2(), Endpoint.CIRCLE);
                blind.setEndpoint(e.getNode2(), e.getNode1(), Endpoint.CIRCLE);
            }

            return sweepOnView(blind, x, y, deadline, "blind");
        }

        /**
         * One sweep against one view of the graph. {@code graph} supplies the proposals only;
         * every returned set is test-confirmed.
         */
        private Set<Node> sweepOnView(Graph graph, Node x, Node y, long deadline, String label)
                throws InterruptedException {

            RecursiveBlocking.BlockingResult b0 = RecursiveBlocking.blockPathsRecursively(
                    graph, x, y, Set.of(), Set.of(), recursiveDepth, depth, rbRadius, 1, true,
                    deadline);

            if (verbose) {
                TetradLogger.getInstance().log("    R4 pair search [" + label + "] RB seed: "
                        + (b0.indeterminate() ? "INDETERMINATE"
                        : b0.blockingSet() == null ? "UNBLOCKABLE (no blocking set -- family empty)"
                        : "blocking set = " + b0.blockingSet()));
            }

            Set<Node> nfCandSet = new LinkedHashSet<>();
            if (!b0.indeterminate() && b0.blockingSet() != null) {
                for (Node n : b0.blockingSet()) {
                    if (graph.getAdjacentNodes(n).stream().anyMatch(
                            w -> graph.getEndpoint(n, w) == Endpoint.CIRCLE
                                    || graph.getEndpoint(w, n) == Endpoint.CIRCLE)) {
                        nfCandSet.add(n);
                    }
                }
            }
            List<Node> nfCand = new ArrayList<>(nfCandSet);

            List<Node> common = graph.getAdjacentNodes(x);
            common.retainAll(graph.getAdjacentNodes(y));

            SublistGenerator nfGen = new SublistGenerator(nfCand.size(), nfCand.size());
            int[] nfChoice;
            while ((nfChoice = nfGen.next()) != null) {
                if (System.currentTimeMillis() > deadline) return null;

                Set<Node> notFollowed = GraphUtils.asSet(nfChoice, nfCand);

                RecursiveBlocking.BlockingResult result = notFollowed.isEmpty()
                        ? b0
                        : RecursiveBlocking.blockPathsRecursively(
                        graph, x, y, Set.of(), notFollowed, recursiveDepth, depth,
                        rbRadius, 1, true, deadline);

                if (result == null || result.indeterminate() || result.blockingSet() == null) {
                    continue;
                }

                Set<Node> base = new LinkedHashSet<>(result.blockingSet());

                List<Node> removalCandidates = new ArrayList<>();
                for (Node n : base) if (common.contains(n)) removalCandidates.add(n);
                for (Node n : base) if (!common.contains(n)) removalCandidates.add(n);

                List<Node> addCandidates = new ArrayList<>();
                for (Node c : common) if (!base.contains(c)) addCandidates.add(c);

                SublistGenerator addGen = new SublistGenerator(addCandidates.size(), addCandidates.size());
                int[] addChoice;
                while ((addChoice = addGen.next()) != null) {
                    if (System.currentTimeMillis() > deadline) return null;

                    Set<Node> A = GraphUtils.asSet(addChoice, addCandidates);

                    SublistGenerator cGen = new SublistGenerator(removalCandidates.size(), removalCandidates.size());
                    int[] cChoice;
                    while ((cChoice = cGen.next()) != null) {
                        if (System.currentTimeMillis() > deadline) return null;

                        Set<Node> S = new LinkedHashSet<>(base);
                        S.removeAll(GraphUtils.asSet(cChoice, removalCandidates));
                        S.addAll(A);

                        if (depth != -1 && S.size() > depth) continue;

                        checkCounter.increment("R4 pair sepset search (test executed)");

                        if (test.checkIndependence(x, y, S).isIndependent()) {
                            return S;
                        }
                    }
                }
            }

            return null;
        }
    }

    private record RemovalHit(int index, Edge edge, Set<Node> cond, double pValue) {
    }

    /**
     * One endpoint assignment of the closure-cover search: the mark at {@code at} on the edge
     * {@code from}--{@code at} becomes {@code end}.
     */
    private record Assign(Node from, Node at, Endpoint end) {
    }

    /**
     * A cover move: the joint endpoint assignment that blocks (or un-opens) one triple on an
     * active path -- two arrowheads for a collider-ization, one tail for a de-collider-ization.
     */
    private record CoverMove(List<Assign> assigns) {
    }

    private record IndependenceCheck(Edge edge, Set<Node> cond, double pValue) {
    }

    /**
     * Outcome of a phantom scan. `edge` is a confirmed-spurious discriminating-path
     * leg to discharge, or null if none was confirmed. `indeterminate` is true if
     * any pair's blocking search timed out before a verdict, so a null `edge` means
     * "no phantom confirmed within budget" rather than "no phantom exists."
     */
    private record NongenuineScan(Edge edge, boolean indeterminate) {
    }
}