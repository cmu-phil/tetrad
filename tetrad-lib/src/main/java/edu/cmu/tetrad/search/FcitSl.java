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
     * Commit provenance telemetry. {@code zhangCommits}: commits hosted by the canonical
     * Zhang MAG (Stage 1). {@code legCommits}: commits hosted by a non-canonical LEG of the
     * current class (Stage 2). {@code inClassFlipCommits}: commits hosted by a fork-flip seed
     * certified Markov-equivalent to the current class (Stage 2b) -- a within-class
     * representative that is neither a LEG nor a stamped LEG (e.g. a shielded-fork flip whose
     * bidirected edges sit outside the deleted pair's common-neighbor set). {@code
     * escapeCommits}: commits hosted by a certified out-of-class seed (pass 3). A run with
     * {@code escapeCommits == 0} used only Step-Lemma-form witnesses.
     */
    private long zhangCommits = 0, legCommits = 0, inClassFlipCommits = 0, escapeCommits = 0;
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
     * MAG-side analog of adjustForExtraSepsets over every recorded sepset. Idempotent:
     * re-stamping an existing collider is a no-op (the isDefCollider guard), so calling
     * this each commit removes any reliance on prior colliders persisting through the
     * PAG<->MAG round trip. Null sepsets and still-adjacent pairs are skipped.
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

                if (mag.getEndpoint(d, c) != Endpoint.ARROW) {
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

    private boolean newUnshieldedCollider(Node y, Node c, Node d) throws InterruptedException {
        Set<Node> f = foundSepsets.get(Set.of(d, y));

        System.out.println("for <" + y + ", " + d + "> f = " + f);

        if (f != null && !f.contains(c)) {
            return true;
        }

        RecursiveBlocking.BlockingResult result = RecursiveBlocking.blockPathsRecursively(
                interimPags.getLast(), d, y,Set.of(), Set.of(), recursiveDepth, depth, rbRadius, 1, true,
                Long.MAX_VALUE);

        if (result.found()) {
            System.out.println("for <" + y + ", " + d + "> blocking = " + result.blockingSet());
            Set<Node> blockingSet = result.blockingSet();
            blockingSet.remove(c);

            System.out.println("for <" + y + ", " + d + " trying blocking " + blockingSet);

            if (test.checkIndependence(d, y, blockingSet).isIndependent()) {

//                if (!result.blockingSet().contains(c)) {
                    return true;
//                }
            }
        }

        return false;
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
                + escapeCommits + " class-escape (pass 3"
                + (allowClassEscape ? ")." : "; disabled)."));

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

//    private Graph coldReorient(Graph graph) throws InterruptedException {
//        Graph finalPag = graph.copy();
//        List<Node> nodes = graph.getNodes();
//
//        R0R4StrategyTestBased strategy = new R0R4StrategyTestBased(test, timeout);
//        strategy.setSepsetMap(sepsets);
//        strategy.setBlockingType(R0R4StrategyTestBased.BlockingType.RECURSIVE);
//        strategy.setDepth(depth);
//
//        finalPag.reorientAllWith(Endpoint.CIRCLE);
//
//        for (Node y : nodes) {
//            List<Node> adj = graph.getAdjacentNodes(y);
//
//            for (int i = 0; i < adj.size(); i++) {
//                for (int j = i + 1; j < adj.size(); j++) {
//                    Node x = adj.get(i);
//                    Node z = adj.get(j);
//
//                    Set<Node> found = foundSepsets.get(Set.of(x, z));
//                    if (!graph.isAdjacentTo(x, z) && graph.isDefCollider(x, y, z) && found != null && !found.contains(y)) {
//                        finalPag.setEndpoint(x, y, Endpoint.ARROW);
//                        finalPag.setEndpoint(z, y, Endpoint.ARROW);
//                        continue;
//                    }
//
//                    List<Node> common = graph.getAdjacentNodes(x);
//                    common.retainAll(graph.getAdjacentNodes(z));
//
//                    if (!graph.isAdjacentTo(x, z) && graph.isDefCollider(x, y, z)) {
//                        SublistGenerator gen = new SublistGenerator(common.size(), common.size());
//                        int[] choice;
//
//                        while ((choice = gen.next()) != null) {
//                            Set<Node> _choice = GraphUtils.asSet(choice, common);
//
//                            if (test.checkIndependence(x, z, _choice).isIndependent()) {
//                                if (!_choice.contains(y)) {
//                                    finalPag.setEndpoint(x, y, Endpoint.ARROW);
//                                    finalPag.setEndpoint(z, y, Endpoint.ARROW);
//                                    foundSepsets.put(Set.of(x, z), _choice);
//                                    break;
//                                }
//                            }
//                        }
//                    }
//                }
//            }
//        }
//
//        FciOrient fciOrient = new FciOrient(strategy);
//        fciOrient.setVerbose(false);
//        fciOrient.setParallel(false);
//        fciOrient.setCompleteRuleSetUsed(completeRuleSetUsed);
//        fciOrient.setRecursiveDepth(recursiveDepth);
//        fciOrient.setMaxDiscriminatingPathLength(maxDiscriminatingPathLength);
//        fciOrient.setUseR4(true);
//        fciOrient.setKnowledge(knowledge);
//        fciOrient.finalOrientation(finalPag);   // R0 + R1-R4 via R0R4StrategyTestBased; uses the test
//
//        return finalPag;
//    }

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
            Optional<RemovalHit> hit =
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
                            .findFirst();

            if (hit.isEmpty()) {
                break;  // no removable edge in the tail — sweep complete
            }

            RemovalHit h = hit.get();
            Node x = h.edge.getNode1();
            Node y = h.edge.getNode2();

            // Commit against the live PAG using the sepset found during the search —
            // no re-search needed, since the winner was searched against the current PAG.
            boolean didChange = tryToModifyGraph(x, y, h.cond, h.pValue(),
                    excludeSelectionBias, escape);

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

            List<Node> common = this.interimPags.getLast().getAdjacentNodes(x);
            common.retainAll(this.interimPags.getLast().getAdjacentNodes(y));

            List<Node> definitelyRemove = new ArrayList<>();
            for (Node c : common) {
                if (this.interimPags.getLast().isDefCollider(x, c, y)) {
                    definitelyRemove.add(c);
                }
            }

            List<Node> removalCandidates = new ArrayList<>(common);
            removalCandidates.removeAll(definitelyRemove);

            SublistGenerator cGen = new SublistGenerator(removalCandidates.size(), removalCandidates.size());
            int[] cChoice;
            while ((cChoice = cGen.next()) != null) {
                if (System.currentTimeMillis() > deadline) return null; // per-edge budget exhausted
                if (!this.interimPags.getLast().isAdjacentTo(x, y)) break;

                Set<Node> S = new LinkedHashSet<>(B);
                Set<Node> C = GraphUtils.asSet(cChoice, removalCandidates);

                S.removeAll(C);

                if (this.depth != -1 && S.size() > this.depth) continue;

                checkCounter.increment("findIndependenceCheckRecursive (test executed)");

                IndependenceResult independenceResult = this.test.checkIndependence(x, y, S);
                if (independenceResult.isIndependent()) {
                    foundSepsets.put(Set.of(x, y), S);   // remember the fact, survives revert
                    foundPValues.put(Set.of(x, y), independenceResult.getPValue());
                    return new IndependenceCheck(edge, S, independenceResult.getPValue());
                }
            }
        }

        return null;
    }

    // Trying to implement the Step Lemma. Here we know that x--y is a spurious edge, since a sepset b has been
    // found. With escape == false only within-class representatives (the canonical Zhang MAG and the LEGs of
    // the current class) are tried -- the Step Lemma's own quantifier. With escape == true the fork-flip
    // out-of-class seeds are also tried; this is an engineering fallback OUTSIDE the Step Lemma, invoked only
    // at the state level after a full within-class sweep commits nothing.
    private boolean tryToModifyGraph(Node x, Node y, Set<Node> b, double pValue, boolean excludeSelectionBias,
                                     boolean escape)
            throws InterruptedException {
        Edge _edge = interimPags.getLast().getEdge(x, y);
        Graph _pag = new EdgeListGraph(interimPags.getLast());

//        System.out.println("_pag = " + _pag);

        List<Edge> _removed = Collections.singletonList(Objects.requireNonNull(_edge));

        final long deadline = (timeout < 0L) ? Long.MAX_VALUE : System.currentTimeMillis() + timeout;

        // Pick a MAG H'. In within-class mode (escape == false) the only seed is the minimal-
        // bidirected Zhang MAG, and LegEnumerator walks the current class: Stage 1 is the Zhang
        // MAG itself, Stage 2 the other LEGs. In escape mode extra seeds may LEAVE the class by
        // turning a fork on an unblocked x..y path into a collider (fork -> <->), required when
        // the initial DAG compels an orientation that contradicts the true MAG -- a latent
        // common effect the DAG modeled as a common cause.
        List<Graph> seeds = seedMags(_pag, x, y, b, deadline, escape);

        for (int seedIdx = 0; seedIdx < seeds.size(); seedIdx++) {
            Graph seed = seeds.get(seedIdx);
            // Within-class mode lists the Zhang MAG first, then in-class fork-flips; escape
            // mode returns only certified out-of-class seeds, so no seed there is the base.
            boolean baseSeed = (!escape && seedIdx == 0);

            for (Graph mag : new LegEnumerator(seed)) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException();
                }

                if (System.currentTimeMillis() > deadline) {
                    break;
                }

                Graph _mag = mag.copy();

//                System.out.println("Chose LEG: " + _mag);

                // The MAG H' we pick will need to be one where the stored sepsets are honored, so we need in
                // particular to orient common colliders of x and y.
                if (!stampLegColliders(_mag, b, x, y)) {
                    continue;
                }

//                System.out.println("Stamped LEG: " + _mag);

                // We remove f = x *-* y, yielding H' - f.
                _mag.removeEdge(x, y);

//                System.out.println("Removed " + x + " *-* " + y + ": " + _mag + " b = " + b);

                legalityChecks++;

                // Now H' - f needs to satisfy Prong (A)--i.e., it needs to be a MAG, which is to say, it needs to
                // satisfy Lemma 3.6.
                if (_mag.paths().existsInducingPath(x, y, Set.of())) {
//                    System.out.println("Rejected " + x + " *-* " + y + " because it introduces an inducing path.");

                    ipRejects++;                               // non-maximal exactly at the deleted pair
                    continue;
                }

                // Also, H' - f needs to satisfy Prong (B)--i.e., it can't introduce any new CIs that aren't in G*.
                // We spot-check this.
                if (!deletedPairBatteryPasses(_mag, _removed)) {
//                    System.out.println("battery doesn't pass");

                    continue;
                }

                if (verbose) {
                    TetradLogger.getInstance().log("Removing " + _edge + ", sepset = " + b
                            + (Double.isNaN(pValue) ? "" : ", p = " + pValue));
                }

                // Commit provenance: canonical Zhang MAG (Stage 1), other LEG of the current
                // class (Stage 2), certified in-class fork-flip (Stage 2b), or certified
                // out-of-class seed (pass 3).
                if (escape) {
                    escapeCommits++;
                } else if (!baseSeed) {
                    inClassFlipCommits++;
                } else if (magKey(mag).equals(magKey(seed))) {
                    zhangCommits++;
                } else {
                    legCommits++;
                }

                // Add the PAG of H' - f to the list of PAGs and record the sepset b for {x, y}.
                Graph convert = new MagToPag(_mag).convert(false, excludeSelectionBias);
                this.interimPags.add(convert);
                sepsets.set(x, y, b);
                return true;                                   // first representative that hosts it
            }
        }

        if (verbose) {
            TetradLogger.getInstance().log("\tTried removing " + _edge
                    + (escape ? " (class-escape pass)" : " (within-class pass)")
                    + ", but no representative hosted it, sepset = " + b);
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