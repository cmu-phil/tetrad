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
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.search.utils.*;
import edu.cmu.tetrad.util.MillisecondTimes;
import edu.cmu.tetrad.util.SublistGenerator;
import edu.cmu.tetrad.util.TetradLogger;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FCIT-SL ("Step Lemma"): FCIT-ZM extended with a Step-Lemma-guided representative search.
 *
 * @author josephramsey
 */
public final class FcitSl implements IGraphSearch {
    /**
     * The independence test.
     */
    private final IndependenceTest test;
    /**
     * The score.
     */
    private final Score score;
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
     * Maximum number of fork nodes converted to colliders when building an out-of-class seed
     * for a deletion the current directed class cannot host. See {@link #seedMags}.
     */
    private int maxForkFlips = 2;
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
     * Maximum number of cover moves (endpoint reassignments driven by an unblocked path)
     * per candidate in the closure-cover search. The analog of {@code maxForkFlips}, but
     * counting PATH-DIRECTED assignments rather than blind subset choices, so a small value
     * reaches deeper: each move is spent on a path known to still be active.
     */
    private int maxCoverMoves = 4;
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
            closureIllegalCands = 0, closureClassFiltered = 0;
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
    public FcitSl(IndependenceTest test, Score score) {
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
            TetradLogger.getInstance().log("Copying unshielded colliders from CPDAG.");
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
            TetradLogger.getInstance().log("Doing implied orientation, grabbing unshielded colliders from FciOrient.");
        }

        // Re-derive the final orientation from the independence test. The interim PAGs inherit their
        // marks from dagToPag / MagToPag, which faithfully render whatever MAG they are handed -- and
        // that MAG encodes latent confounding as DIRECTED edges (GRaSP's DAG fit), so a shielded
        // collider such as V1 (whose neighbors W1, Y are adjacent) is never oriented as a collider.
        // When the skeleton already matches the truth, no edge is removed and nothing re-runs R4, so
        // the DAG's tail at V1 survives. Wipe to circles and run R0 + R1-R4 with the test-based
        // strategy: R4 fires on the discriminating path <X, W1, V1, Y> and recovers V1 <-> W1 /
        // V1 <-> Y. On models that DO need removals this only re-confirms marks the test already implies.
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
                + escapeCommits + " class-escape (pass 3)"
                + otherRejects + " other-rejects"
                + (allowClassEscape ? "" : "; disabled)."));

        if (useClosureCoverSearch) {
            TetradLogger.getInstance().log("Closure-cover search: " + closureCandidatesEmitted
                    + " candidate(s) emitted, " + closureInClassCommits + " in-class commit(s), "
                    + closureEscapeCommits + " escape commit(s), "
                    + closureStampPrunes + " stamp-pruned branch(es), "
                    + closureStampObstructions + " invariant stamp obstruction(s), "
                    + closureLongPathMisses + " beyond-bound active-path miss(es), "
                    + closureIllegalCands + " illegal candidate(s) at emission, "
                    + closureClassFiltered + " class-filtered candidate(s).");
        }

        TetradLogger.getInstance().log("\nFCIT-SL finished.");
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

    private NongenuineScan findR4NongenuineEdge(Graph pag) throws InterruptedException {
        Set<DiscriminatingPath> ddps = FciOrient.listDiscriminatingPaths(pag, -1, true);

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

        grasp.setSeed(-1);
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

        Set<Node> nfCandSet = new LinkedHashSet<>();
        if (!b0result.indeterminate() && b0result.blockingSet() != null) {
            for (Node v : b0result.blockingSet()) {
                // Only ambiguous nodes — those with at least one circle endpoint
                if (interimPags.getLast().getAdjacentNodes(v).stream().anyMatch(
                        w -> interimPags.getLast().getEndpoint(v, w) == Endpoint.CIRCLE
                                || interimPags.getLast().getEndpoint(w, v) == Endpoint.CIRCLE)) {
                    nfCandSet.add(v);
                }
            }
        }

        List<Node> nfCand = new ArrayList<>(nfCandSet);

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
            Set<Node> definitelyRemove = new LinkedHashSet<>();
            for (Node c : common) {
                if (this.interimPags.getLast().isDefCollider(x, c, y)) {
                    definitelyRemove.add(c);
                }
            }

            Set<Node> B0 = new LinkedHashSet<>(B);
            B0.removeAll(definitelyRemove);

            List<Node> removalCandidates = new ArrayList<>();
            for (Node v : B0) if (common.contains(v)) removalCandidates.add(v);
            for (Node v : B0) if (!common.contains(v)) removalCandidates.add(v);

            int maxRemove = (this.maxBlockingSetRemovals < 0)
                    ? removalCandidates.size()
                    : Math.min(this.maxBlockingSetRemovals, removalCandidates.size());

            SublistGenerator cGen = new SublistGenerator(removalCandidates.size(), maxRemove);
            int[] cChoice;
            while ((cChoice = cGen.next()) != null) {
                if (System.currentTimeMillis() > deadline) return null; // per-edge budget exhausted
                if (!this.interimPags.getLast().isAdjacentTo(x, y)) break;

                Set<Node> S = new LinkedHashSet<>(B0);
                Set<Node> C = GraphUtils.asSet(cChoice, removalCandidates);

                S.removeAll(C);

                if (this.depth != -1 && S.size() > this.depth) continue;

                checkCounter.increment("findIndependenceCheckRecursive (test executed)");

                IndependenceResult independenceResult = this.test.checkIndependence(x, y, S);
                if (independenceResult.isIndependent()) {
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

//    // Trying to implement the Step Lemma. Here we know that x--y is a spurious edge, since a sepset b has been
//    // found. With escape == false only within-class representatives (the canonical Zhang MAG and the LEGs of
//    // the current class) are tried -- the Step Lemma's own quantifier. With escape == true the fork-flip
//    // out-of-class seeds are also tried; this is an engineering fallback OUTSIDE the Step Lemma, invoked only
//    // at the state level after a full within-class sweep commits nothing.
//    private boolean tryToModifyGraph(Node x, Node y, Set<Node> b, double pValue, boolean excludeSelectionBias,
//                                     boolean escape)
//            throws InterruptedException {
//        Edge _edge = interimPags.getLast().getEdge(x, y);
//        Graph _pag = new EdgeListGraph(interimPags.getLast());
//

    /// /        System.out.println("_pag = " + _pag);
//
//        List<Edge> _removed = Collections.singletonList(Objects.requireNonNull(_edge));
//
//        final long deadline = (timeout < 0L) ? Long.MAX_VALUE : System.currentTimeMillis() + timeout;
//
//        // Pick a MAG H'. In within-class mode (escape == false) the only seed is the minimal-
//        // bidirected Zhang MAG, and LegEnumerator walks the current class: Stage 1 is the Zhang
//        // MAG itself, Stage 2 the other LEGs. In escape mode extra seeds may LEAVE the class by
//        // turning a fork on an unblocked x..y path into a collider (fork -> <->), required when
//        // the initial DAG compels an orientation that contradicts the true MAG -- a latent
//        // common effect the DAG modeled as a common cause.
//        List<Graph> seeds = seedMags(_pag, x, y, b, deadline, escape);
//
//        for (int seedIdx = 0; seedIdx < seeds.size(); seedIdx++) {
//            Graph seed = seeds.get(seedIdx);
//            // Within-class mode lists the Zhang MAG first, then in-class fork-flips; escape
//            // mode returns only certified out-of-class seeds, so no seed there is the base.
//            boolean baseSeed = (!escape && seedIdx == 0);
//
//            for (Graph mag : new LegEnumerator(seed)) {
//                if (Thread.currentThread().isInterrupted()) {
//                    throw new InterruptedException();
//                }
//
//                if (System.currentTimeMillis() > deadline) {
//                    break;
//                }
//
//                Graph _mag = mag.copy();
//
//                System.out.println("Chose LEG: " + _mag);
//
//                // The MAG H' we pick will need to be one where the stored sepsets are honored, so we need in
//                // particular to orient common colliders of x and y.
//                if (!stampLegColliders(_mag, b, x, y)) {
//                    continue;
//                }
//
//                System.out.println("Stamped LEG: " + _mag);
//
//                // We remove f = x *-* y, yielding H' - f.
//                _mag.removeEdge(x, y);
//
//                System.out.println("Removed " + x + " *-* " + y + ": " + _mag + " b = " + b);
//
//                legalityChecks++;
//
//                // Now H' - f needs to satisfy Prong (A)--i.e., it needs to be a MAG, which is to say, it needs to
//                // satisfy Lemma 3.6.
//                if (_mag.paths().existsInducingPath(x, y, Set.of())) {
//                    System.out.println("Rejected " + x + " *-* " + y + " because it introduces an inducing path.");
//
//                    ipRejects++;                               // non-maximal exactly at the deleted pair
//                    continue;
//                }
//
//                // Also, H' - f needs to satisfy Prong (B)--i.e., it can't introduce any new CIs that aren't in G*.
//                // We spot-check this.
//                if (!deletedPairBatteryPasses(_mag, _removed)) {
//                    System.out.println("battery doesn't pass");
//
//                    continue;
//                }
//
//                if (verbose) {
//                    TetradLogger.getInstance().log("Removing " + _edge + ", sepset = " + b
//                            + (Double.isNaN(pValue) ? "" : ", p = " + pValue));
//                }
//
//                // Commit provenance: canonical Zhang MAG (Stage 1), other LEG of the current
//                // class (Stage 2), certified in-class fork-flip (Stage 2b), or certified
//                // out-of-class seed (pass 3).
//                if (escape) {
//                    escapeCommits++;
//                } else if (!baseSeed) {
//                    inClassFlipCommits++;
//                } else if (magKey(mag).equals(magKey(seed))) {
//                    zhangCommits++;
//                } else {
//                    legCommits++;
//                }
//
//                // Add the PAG of H' - f to the list of PAGs and record the sepset b for {x, y}.
//                Graph convert = new MagToPag(_mag).convert(false, excludeSelectionBias);
//                this.interimPags.add(convert);
//                sepsets.set(x, y, b);
//                return true;                                   // first representative that hosts it
//            }
//        }
//
//        if (verbose) {
//            TetradLogger.getInstance().log("\tTried removing " + _edge
//                    + (escape ? " (class-escape pass)" : " (within-class pass)")
//                    + ", but no representative hosted it, sepset = " + b);
//        }
//
//        return false;
//    }

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

        List<Graph> seeds = seedMags(_pag, x, y, b, deadline, escape);

        // Class identity reference for flip classification; same construction seedMags uses.
        Graph basePag = new MagToPag(GraphTransforms.zhangMagFromPag(_pag))
                .convert(false, excludeSelectionBias);

        // Candidates recur across seeds and walks; test each distinct MAG once.
        Set<String> tried = new HashSet<>();

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

                        Graph _mag = mag.copy();

                        // H' must honour the stored sepsets: stamp the common colliders of x and y.
                        // Refuses any LEG whose stamp would create a NEW unshielded collider.
                        if (!stampLegColliders(_mag, b, x, y)) {
                            continue;
                        }

                        // The STAMPED graph is the H' that every lemma quantifies over, so it must itself
                        // be a legal MAG. The stamp can make an ancestral-but-NON-MAXIMAL graph: an inducing
                        // path between a pair OTHER than {x, y}, whose ancestry certificate runs through the
                        // edge about to be deleted. Deleting then kills the path and mints a NEW separation
                        // at that other pair -- invisible to the deleted-pair battery, and outside the
                        // hypotheses of deletion-locality and pair-locality (both assume H' is a MAG).
                        // Concretely: stamping V1 for the {V3,V4} deletion on the V4<->V2 flip produced the
                        // inducing path V3<->V1<->V4<->V2 (V4 in An(V3) via V4-->V3); deleting V4-->V3
                        // yielded the false V3 _||_ V2 | {V1}.
                        if (!_mag.paths().isLegalMag()) {
                            otherRejects++;
                            continue;
                        }

                        // We remove f = x *-* y, yielding H' - f.
                        _mag.removeEdge(x, y);

                        legalityChecks++;

                        // Prong (A): H' - f must be a MAG (Lemma 3.6 localizes this to the pair).
                        if (_mag.paths().existsInducingPath(x, y, Set.of())) {
                            ipRejects++;
                            continue;
                        }

                        // Prong (B): no new CIs absent from G*. Spot-checked by the battery.
                        if (!deletedPairBatteryPasses(_mag, _removed)) {
                            continue;
                        }

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
            if (seedPag.equals(basePag)) {
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
                                  long deadline, boolean escape) throws InterruptedException {
        List<Graph> out = new ArrayList<>();

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

        boolean committed = closureCoverDfs(cand, pins, paths, maxCoverMoves, _pag, basePag,
                x, y, b, pValue, excludeSelectionBias, _removed, _edge, escape, tried, deadline);

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
    private enum GateStatus {COMMITTED, STAMP_REFUSED, ILLEGAL_MAG, INDUCING_PATH, BATTERY_REFUSED}

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

                boolean okWithArrow = (!unshX || pag.isDefCollider(d, c, x))
                        && (!unshY || pag.isDefCollider(d, c, y));
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
                                    Edge edgeForLog, boolean escape, Set<String> tried, long deadline)
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
            moves = coverMoves(pag, stamped, cand, pins, active, b);
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
                closureIllegalCands++;
                return false;
            }

            closureCandidatesEmitted++;

            // Class membership, certified as seedMags certifies seeds: pre-stamp, by
            // MagToPag equality against the base PAG.
            boolean inClass = new MagToPag(new EdgeListGraph(cand))
                    .convert(false, excludeSelectionBias).equals(basePag);

            if (inClass == escape) {
                closureClassFiltered++;
                return false;   // no principled move targets the class boundary
            }

            GateStatus st = closureGateAndCommit(cand, x, y, b, pValue, excludeSelectionBias,
                    removed, edgeForLog,
                    escape ? () -> closureEscapeCommits++ : () -> closureInClassCommits++);

            if (st == GateStatus.COMMITTED) return true;
            if (movesLeft == 0) return false;

            moves = switch (st) {
                case ILLEGAL_MAG -> illegalityRepairMoves(pag, stamped, pins);
                case INDUCING_PATH -> inducingRepairMoves(pag, probe, pins, x, y);
                default -> List.of();   // STAMP_REFUSED cannot recur here; BATTERY is terminal
            };
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
                    pValue, excludeSelectionBias, removed, edgeForLog, escape, tried, deadline);

            undoMove(cand, pins, mv, saved, addedPins);

            if (done) return true;
        }

        return false;
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
    private List<CoverMove> illegalityRepairMoves(Graph pag, Graph stamped, Map<String, Endpoint> pins) {
        List<CoverMove> out = new ArrayList<>();

        for (Edge e : stamped.getEdges()) {
            Node u = e.getNode1(), w = e.getNode2();
            Endpoint atU = stamped.getEndpoint(w, u), atW = stamped.getEndpoint(u, w);

            if (atU == Endpoint.ARROW && atW == Endpoint.ARROW) {          // u <-> w
                List<Node> p = firstDirectedPath(stamped, u, w, closureMaxPathLength);
                if (p == null) p = firstDirectedPath(stamped, w, u, closureMaxPathLength);
                if (p != null) addPathBreakMoves(pag, pins, p, out);
            } else if (atU == Endpoint.TAIL && atW == Endpoint.ARROW) {    // u --> w
                List<Node> p = firstDirectedPath(stamped, w, u, closureMaxPathLength);
                if (p != null) addPathBreakMoves(pag, pins, p, out);       // w ~> u closes a cycle
            }
        }

        // Non-maximality: an inducing path between a nonadjacent pair.
        List<Node> nodes = stamped.getNodes();
        for (int i = 0; i < nodes.size() && out.isEmpty(); i++) {
            for (int j = i + 1; j < nodes.size() && out.isEmpty(); j++) {
                Node u = nodes.get(i), w = nodes.get(j);
                if (stamped.isAdjacentTo(u, w)) continue;
                List<Node> ip = firstInducingPathOverEmpty(stamped, u, w, closureMaxPathLength);
                if (ip != null) addInducingBreakMoves(pag, stamped, pins, ip, u, w, out);
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
                                                Node x, Node y) {
        List<CoverMove> out = new ArrayList<>();
        List<Node> ip = firstInducingPathOverEmpty(probe, x, y, closureMaxPathLength);
        if (ip != null) addInducingBreakMoves(pag, probe, pins, ip, x, y, out);
        return out;
    }

    /**
     * Assignments that break one link of a directed path: for each edge p --&gt; q on it,
     * the reversal (arrowhead at p, tail at q -- the LEG-style fix, proposed first) and the
     * bidirected cut (arrowhead at p alone), wherever the PAG and pins allow.
     */
    private void addPathBreakMoves(Graph pag, Map<String, Endpoint> pins, List<Node> path,
                                   List<CoverMove> out) {
        for (int i = 0; i < path.size() - 1; i++) {
            Node p = path.get(i), q = path.get(i + 1);
            boolean arrowAtP = slotCanBe(pag, pins, q, p, Endpoint.ARROW);
            if (arrowAtP && slotCanBe(pag, pins, p, q, Endpoint.TAIL)) {
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
                                       List<Node> path, Node u, Node w, List<CoverMove> out) {
        for (int i = 1; i < path.size() - 1; i++) {
            Node prev = path.get(i - 1), m = path.get(i), next = path.get(i + 1);

            addTailMove(pag, g, pins, prev, m, out);
            addTailMove(pag, g, pins, next, m, out);

            List<Assign> quench = new ArrayList<>();
            for (Node z : g.getAdjacentNodes(m)) {
                if (g.getEndpoint(z, m) == Endpoint.TAIL && g.getEndpoint(m, z) == Endpoint.ARROW
                        && slotCanBe(pag, pins, z, m, Endpoint.ARROW)) {
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
                                       List<Node> path, Set<Node> S) {
        List<CoverMove> out = new ArrayList<>();

        for (int i = 1; i < path.size() - 1; i++) {
            Node a = path.get(i - 1), m = path.get(i), c = path.get(i + 1);

            if (!S.contains(m)) {
                if (!stamped.isDefCollider(a, m, c)) {
                    if (slotCanBe(pag, pins, a, m, Endpoint.ARROW)
                            && slotCanBe(pag, pins, c, m, Endpoint.ARROW)
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
                                && slotCanBe(pag, pins, w, m, Endpoint.ARROW)) {
                            Assign cut = new Assign(w, m, Endpoint.ARROW);
                            out.add(new CoverMove(List.of(cut)));
                            quench.add(cut);
                        }
                    }
                    if (quench.size() > 1) {
                        out.add(new CoverMove(List.copyOf(quench)));
                    }
                }
            } else {
                if (stamped.isDefCollider(a, m, c)) {
                    addTailMove(pag, cand, pins, a, m, out);
                    addTailMove(pag, cand, pins, c, m, out);
                }
            }
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
        if (!slotCanBe(pag, pins, far, m, Endpoint.TAIL)) return;

        List<Assign> assigns = new ArrayList<>();
        assigns.add(new Assign(far, m, Endpoint.TAIL));

        if (cand.getEndpoint(m, far) == Endpoint.TAIL) {
            if (!slotCanBe(pag, pins, m, far, Endpoint.ARROW)) return;   // tail--tail: illegal shape
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
        Endpoint e = pag.getEndpoint(from, at);
        if (e != Endpoint.CIRCLE && e != want) return false;
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
     * Applies a move: records prior endpoints for undo, sets the assignments, and pins each
     * slot it newly pins (already-pinned slots were verified compatible by
     * {@link #slotCanBe}).
     */
    private void applyMove(Graph cand, Map<String, Endpoint> pins, CoverMove mv,
                           List<Endpoint> saved, List<String> addedPins) {
        for (Assign a : mv.assigns()) {
            saved.add(cand.getEndpoint(a.from(), a.at()));
            cand.setEndpoint(a.from(), a.at(), a.end());

            String k = slotKey(a.from(), a.at());
            if (pins.putIfAbsent(k, a.end()) == null) {
                addedPins.add(k);
            }
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
                return this.test.checkIndependence(x, y, z).isIndependent();
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
     * Sets the maximum number of fork nodes converted to colliders when building an out-of-class
     * seed. Bounded low (1-2) for audit-scale models. See {@link #seedMags}.
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