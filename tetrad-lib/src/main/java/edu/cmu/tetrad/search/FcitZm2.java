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
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FCIT with a MAG-side legality gate. A score-based search (BOSS/GRaSP) supplies a seed
 * CPDAG whose unshielded colliders are sound; recursive blocking then proposes single-edge
 * deletions, each committed only when the candidate Zhang MAG is legal, and a saturating
 * pass removes any test-separable adjacency the single-edge phase left standing.
 *
 * <p><b>Two commit routes.</b> The legality gate is always judged on the MAG. What differs
 * is how the accepted state is carried forward:
 * <ul>
 *   <li>{@link COMMIT_ROUTE#PAG} (default) reorients the PAG directly, leaving endpoints
 *       as circles until a rule commits them.</li>
 *   <li>{@link COMMIT_ROUTE#MAG} projects back via {@code MagToPag(zhangMagFromPag(...))},
 *       which is the route described as FCIT-ZM in the reachability paper and the one its
 *       exhaustive enumeration measured.</li>
 * </ul>
 * Neither route is per-step Markov: over five observed variables the enumeration exhibits
 * 1,687 legal non-Markov waypoints on the PAG route, of which the MAG re-commit breaks
 * identically on 885 and lands back inside the I-map class on 802, refusing none. The
 * per-step guarantee is therefore carried by the saturating pass and the terminal identity
 * (the spurious-free skeleton forces the true PAG), not by the legality gate.
 *
 * <p><b>What the gate does guarantee.</b> Every committed state, and the returned graph,
 * is a legal PAG -- a property of the reorientation referring neither to the true PAG nor
 * to faithfulness, hence surviving the passage from oracle to finite sample. The final
 * reorientation is itself gated (see {@code search()}); if it fails, the last gated state
 * is returned instead.
 *
 * <p><b>Seed soundness.</b> The soundness argument consumes exactly one orientation
 * premise: that the seed's unshielded colliders are sound. Every reorientation performed
 * here therefore recalls that seed collider set rather than recomputing colliders from the
 * current graph, which could re-derive marks stamped unsoundly at an earlier step.
 *
 * <p><b>Reach repair (2026-8).</b> The separator search and the loop structure were narrower
 * than Fcit's, in four ways that an exhaustive enumeration over small observed sizes is
 * built to expose. All four are now closed; none of them touches the MAG-side legality gate
 * or either commit route, which remain this algorithm's identity:
 * <ol>
 *   <li>NF candidates are the ambiguous seed-blocking-set members UNIONED with the ambiguous
 *       common neighbors, not the seed alone (which left the NF layer empty on unblockable
 *       views);</li>
 *   <li>common neighbors the blocking search omitted can now be added BACK into the tested
 *       set, not only removed from it;</li>
 *   <li>conflict-driven mark-flip escalation retries the sweep on views distrusting one
 *       load-bearing arrowhead at a time;</li>
 *   <li>the single-edge removal fixpoint and the saturating pass now iterate to a JOINT
 *       fixpoint instead of running once each in sequence.</li>
 * </ol>
 * Soundness is untouched by all four: views only ever propose candidates, and every
 * separator committed is confirmed by the independence test and gated on MAG legality. What
 * changes is reach -- which true separators the search can find -- so the expected effect is
 * strictly fewer missed deletions.
 *
 * @author josephramsey
 */
public final class FcitZm2 implements IGraphSearch {
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
    /** Canonical node order, so candidate enumeration is deterministic. */
    private static final Comparator<Node> NODE_ORDER = Comparator.comparing(Node::getName);
    /**
     * Whether the conflict-driven mark-flip escalation runs when the live-view sweep fails.
     * Added in the 2026-8 reach repair; see findIndependenceCheckRecursive.
     */
    private boolean useMarkFlipEscalation = true;
    /** Max flip hypotheses tried per pair; -1 unlimited. Hypotheses are name-sorted. */
    private int maxMarkFlips = -1;
    private final SepsetMap sepsets = new SepsetMap();
    /**
     * The list of selection nodes in the graph.
     */
    private final List<Node> selection;
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
     * R4 provenance: firings decided from a separator already recorded for the
     * discriminating path's endpoint pair, versus firings whose separator had to be
     * found on demand, versus firings declined for want of one.
     */
    private int r4SepsetBacked = 0, r4Backfilled = 0, r4Declined = 0;
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
     * True iff verbose output should be printed.
     */
    private boolean superVerbose = false;
    /**
     * Specifies the orientation rules or procedures used in the FCIT algorithm for orienting edges in a PAG (Partial
     * Ancestral Graph). This variable determines how unshielded colliders, discriminating paths, and other structural
     * elements of the PAG are identified and processed during the search. The orientation strategy implemented in this
     * variable can influence the causal interpretation of the resulting graph.
     */
    private FciOrient fciOrient = null;
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
     * Whether the main loop is run once at {@link #depth} (false, the default
     * and the historical behavior) or repeatedly at increasing conditioning-set
     * caps 0, 1, ..., up to {@link #depth} (true).
     *
     * <p>Rationale, by analogy with PC's depth loop: the per-edge search already
     * prefers small candidate sets, but the ordering is per edge, not global.
     * With a single full-depth pass, an edge scanned early can be discharged by
     * a large separator before an edge scanned later would have been discharged
     * by a small one -- and the large-separator removal is the less reliable of
     * the two, both because it is tested with less power and because it commits
     * the legality gate to a state the small removal would have reached
     * differently. Deepening globally orders removals by separator size, so
     * every edge a size-k separator can discharge is discharged before any
     * size-(k+1) separator is consulted, and the higher levels then run against
     * a sparser view.</p>
     *
     * <p>NOT output-identical to a single pass when enabled. Soundness is
     * untouched -- every separator is still test-confirmed and every removal
     * still passes the same legality gate -- but which valid separator is found
     * first, and hence the trajectory, changes.</p>
     */
    private boolean iterativeDeepening = false;
    /**
     * First conditioning-set cap in the deepening schedule (default 0). Ignored
     * unless {@link #iterativeDeepening} is true.
     */
    private int deepeningStart = 0;
    /**
     * When {@link #depth} is -1 (unlimited) and deepening is on, the schedule
     * cannot count up to the cap, so it ramps through finite levels
     * {@code deepeningStart .. unlimitedRampCeiling} and then runs one final
     * unlimited level. Default 5.
     */
    private int unlimitedRampCeiling = 5;
    /**
     * The conditioning-set cap in force for the current level of the deepening
     * schedule. Equal to {@link #depth} throughout when deepening is off. Every
     * size gate in the separator search reads this, not {@link #depth}.
     *
     * <p>Volatile: written sequentially between levels, read inside the
     * parallel lookahead.</p>
     */
    private volatile int activeDepth = -1;
    /**
     * True just in case good and restored changes are printed. The algorithm always moves to a legal PAG; if it
     * doesn't, it is restored to the previous PAG, and a "restored" message is printed. Otherwise, a "good" message is
     * printed.
     */
    private boolean verbose = false;
    /**
     * A field representing the Partial Ancestral Graph (PAG) used during the causal discovery process. The PAG is
     * initialized as an empty {@link EdgeListGraph} and is updated throughout the search algorithm to incorporate
     * causal structure information.
     * <p>
     * This graph serves as the central data structure, reflecting the results of independence tests, edge orientations,
     * and adjustments based on causal constraints. It is used to store and refine the causal relationships inferred by
     * the algorithm.
     * <p>
     * The {@code @NotNull} annotation indicates the field cannot hold a null value. In its default state, the PAG is
     * instantiated to an empty graph structure.
     */
    private @NotNull List<Graph> interimPags = new ArrayList<>();
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
    private boolean excludeSelectionBias = false;
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
     * How an accepted deletion is carried forward. The MAG legality gate applies to both;
     * see the class javadoc.
     */
    private COMMIT_ROUTE commitRoute = COMMIT_ROUTE.MAG;
    /**
     * When false (default) only the current PAG is retained, since every earlier state is
     * a full graph copy and nothing downstream reads the history. Set true to keep the
     * whole trajectory for debugging.
     */
    private boolean keepInterimTrace = false;

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
    public FcitZm2(IndependenceTest test, Score score) {
        if (test == null) {
            throw new NullPointerException();
        }

        if (score == null) {
            throw new NullPointerException();
        }

        this.test = test;
        this.score = score;

        this.selection = this.test.getVariables().stream()
                .filter(node -> node.getNodeType() == NodeType.SELECTION).toList();

        test.setVerbose(superVerbose);

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
    /**
     * R0 from the recorded separators, PAG side: for every recorded pair now non-adjacent,
     * each common neighbor absent from the recorded set is oriented as a collider. The
     * recorded set is the one whose independence test licensed the deletion, so the verdict
     * is by membership and is never re-derived from the current graph.
     */
    private static void adjustForExtraSepsets(SepsetMap sepsets, Graph pag) {
        for (Set<Node> pair : sepsets.keySet()) {
            List<Node> arr = new ArrayList<>(pair);
            if (arr.size() != 2) continue;

            Node x = arr.get(0);
            Node y = arr.get(1);

            if (pag.isAdjacentTo(x, y)) continue;

            Set<Node> sep = sepsets.get(x, y);
            if (sep == null) continue;

            List<Node> common = pag.getAdjacentNodes(x);
            common.retainAll(pag.getAdjacentNodes(y));

            for (Node node : common) {
                if (sep.contains(node)) continue;
                if (pag.isDefCollider(x, node, y)) continue;
                pag.setEndpoint(x, node, Endpoint.ARROW);
                pag.setEndpoint(y, node, Endpoint.ARROW);
            }
        }
    }

    private static void orientSepsetCollidersInMag(Graph mag, SepsetMap sepsets) {
        for (Set<Node> pair : sepsets.keySet()) {
            List<Node> arr = new ArrayList<>(pair);
            Node x = arr.get(0);
            Node y = arr.get(1);

            Set<Node> s = sepsets.get(x, y);
            if (s == null) continue;
            if (mag.isAdjacentTo(x, y)) continue;      // only meaningful once x–y is gone

            List<Node> common = mag.getAdjacentNodes(x);
            common.retainAll(mag.getAdjacentNodes(y));

            for (Node c : common) {
                if (s.contains(c)) continue;               // not a collider; leave it
                if (mag.isDefCollider(x, c, y)) continue;  // already x*->c<-*y
                mag.setEndpoint(x, c, Endpoint.ARROW);     // arrowheads into c
                mag.setEndpoint(y, c, Endpoint.ARROW);
            }
        }
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

        TetradLogger.getInstance().log("===Starting FCIT-ZM===");

        R0R4StrategyTestBased strategy = new R0R4StrategyTestBased(test, timeout);
        strategy.setSepsetMap(sepsets);
        strategy.setVerbose(superVerbose);
        strategy.setBlockingType(R0R4StrategyTestBased.BlockingType.RECURSIVE);
        this.activeDepth = depth;
        strategy.setDepth(this.activeDepth);

        fciOrient = new FciOrient(new SepsetBackfillR0R4Strategy(strategy));
        fciOrient.setVerbose(superVerbose);
        fciOrient.setParallel(false); // We're doing parallel lookahead.
        fciOrient.setCompleteRuleSetUsed(completeRuleSetUsed);
        fciOrient.setRecursiveDepth(recursiveDepth);
        fciOrient.setMaxDiscriminatingPathLength(maxDiscriminatingPathLength);
        fciOrient.setUseR4(true);
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
                }

                if (superVerbose) {
                    TetradLogger.getInstance().log("Initializing PAG to BOSS CPDAG.");
                    TetradLogger.getInstance().log("Initializing scorer with BOSS best order.");
                }
            } else if (startWith == START_WITH.GRASP) {
                // We need to include the GRaSP option here so that we can run FCIT from Oracle.

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
                }

                if (superVerbose) {
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
                }

                if (superVerbose) {
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

        if (superVerbose) {
            TetradLogger.getInstance().log("Initializing PAG to PAG of BOSS DAG.");
            TetradLogger.getInstance().log("Initializing scorer with BOSS best order.");
        }

        if (scorer != null) {
            scorer.score(best);
        }

        if (superVerbose) {
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

        this.interimPags.add(pag);

        // The seed colliders: computed ONCE from the PAG of the score-based DAG, and
        // recalled at every subsequent reorientation. Soundness of this set is the single
        // orientation premise the argument consumes.
        this.initialColliders = noteInitialColliders(pag.getNodes(), pag);

        // Outer fixpoint over {single-edge removal fixpoint, saturating pass}.
        //
        // Changes from the pre-2026 implementation: the removal fixpoint ran ONCE and the
        // saturating pass ran ONCE after it, with no feedback. That ordering is incomplete
        // in both directions. A successful saturation shrinks adjacencies, which can make
        // previously unseparable pairs separable, so the single-edge phase deserves another
        // sweep; and a single-edge removal accepted after the saturating pass would have
        // re-populated the separable-but-standing set the saturating pass exists to clear.
        // Iterating the two to a joint fixpoint closes both gaps. Termination: every
        // productive iteration removes at least one edge, so there are at most |E| of them.
        // With deepening off this is the singleton schedule {depth}, so the loop below
        // collapses to exactly one call of the historical main loop.
        List<Integer> schedule = deepeningSchedule();
        RoundCounter rounds = new RoundCounter();

        for (int level = 0; level < schedule.size(); level++) {
            this.activeDepth = schedule.get(level);
            strategy.setDepth(this.activeDepth);

            if (schedule.size() > 1) {
                TetradLogger.getInstance().log("\n=== Deepening level " + (level + 1) + "/"
                        + schedule.size() + ": conditioning-set cap = "
                        + (this.activeDepth == -1 ? "unlimited" : this.activeDepth) + " ===");
            }

            runMainLoop(excludeSelectionBias, rounds);
        }

        if (superVerbose) {
            TetradLogger.getInstance().log("Doing implied orientation, grabbing unshielded colliders from FciOrient.");
        }

        long stop2 = System.currentTimeMillis();

        // Revert nodes made latent to latent. Change from the pre-2026 implementation: this
        // ran BEFORE the saturating step, so that step (and its MAG legality gate) saw node
        // types the rest of the search had deliberately set to MEASURED. Reverting after the
        // whole removal phase keeps every gated decision under one convention.
        for (Node node : latents) {
            node.setNodeType(NodeType.LATENT);
        }

        // Final orientation: re-derive marks from the test on the finished skeleton. Without
        // this the output orientation comes only from the per-commit reorientations, so
        // shielded colliders are never recovered by R4 and arrow precision drops. coldReorient
        // wipes to circles, recalls the SEED colliders, then runs R0 and R1-R4.
        Graph finalPag = coldReorient(interimPags.getLast());

        // The legality gate must cover the object we RETURN, not merely every commit en
        // route: the practical guarantee this algorithm offers is that its OUTPUT is a legal
        // PAG, a property referring neither to the true PAG nor to faithfulness. coldReorient
        // is a fresh reorientation and is not otherwise checked, so verify it here and fall
        // back to the last gated state if it fails.
        Graph finalMag = GraphTransforms.zhangMagFromPag(new EdgeListGraph(finalPag));
        PagLegalityCheck.LegalMagRet finalLegal =
                PagLegalityCheck.isLegalMag(finalMag, new LinkedHashSet<>(selection));

        if (!finalLegal.isLegalMag()) {
            TetradLogger.getInstance().log("\nFinal reorientation is not legal ("
                    + finalLegal.getReason() + "); returning the last gated PAG instead.");
            finalPag = interimPags.getLast();
        }

        // Diagnostics are run on the graph actually RETURNED. Running them on the last
        // interim state would report colliders the final reorientation may have added or
        // removed.
        NongenuineScan finalScan = findR4NongenuineEdge(finalPag);

        if (finalScan.edge() != null) {
            TetradLogger.getInstance().log("\nNon-genuine DDPs detected (R4).");
        } else if (finalScan.indeterminate()) {
            TetradLogger.getInstance().log(
                    "\nR4: Detection inconclusive: a blocking search timed out before a verdict. "
                            + "No non-genuine DDP was confirmed, but the graph cannot be certified phantom-free.");
        } else {
            TetradLogger.getInstance().log("\nNo non-genuine DDPs detected in the final graph.");
        }

        reportColliderGenuineness(finalPag);

        TetradLogger.getInstance().log("\nFCIT-ZM finished.");
        TetradLogger.getInstance().log("BOSS/GRaSP time: " + (stop1 - start1) + " ms.");
        TetradLogger.getInstance().log("Collider orientation and _edge removal time: " + (stop2 - start2) + " ms.");
        TetradLogger.getInstance().log("Total time: " + (stop2 - start1) + " ms.");
        TetradLogger.getInstance().log("R4 provenance: " + r4SepsetBacked + " sepset-backed, "
                + r4Backfilled + " backfilled by on-demand search, " + r4Declined
                + " declined (no separator confirmed).");
        TetradLogger.getInstance().log(checkCounter.report());

        CachedIndependenceQueries cache = findCache();
        if (cache != null) {
            TetradLogger.getInstance().log(cache.cacheReport());
        }

        return GraphUtils.replaceNodes(finalPag, nodes);
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

//            // A recorded separator (sepsets = committed; foundSepsets = data fact,
//            // survives revert) already certifies independence; X _||_ Y | S is
//            // invariant across rounds, so no re-test — a present entry means the
//            // still-standing edge is spurious.
//            if (sepsets.get(m, n) != null || foundSepsets.get(Set.of(m, n)) != null) {
//                spuriousEdges.add(edge);
//            }
        }

        return spuriousEdges;
    }

    /**
     * Collider-genuineness scan (the per-instance Markov certificate), evaluated on the
     * canonical Zhang MAG of the given PAG.
     *
     * <p>This ranges over EVERY collider of the MAG, shielded and unshielded alike, rather
     * than only the unshielded R0 sites visible in the PAG. The reason is empirical: over
     * five observed variables the exhaustive enumeration finds the legal non-Markov
     * waypoints to be overwhelmingly SHIELDED (1,557 of 1,687, discriminating-path type),
     * with the unshielded-R0 bin EMPTY. A scan restricted to R0 sites therefore checks
     * precisely the mechanism that never fires and misses the one that does.
     *
     * <p><b>A clean scan is not a certificate.</b> Evaluation on the canonical
     * representative is a strict under-approximation: 130 legal non-Markov waypoints in
     * that enumeration carry no separable-leg collider in their canonical MAG at all,
     * having displaced the unsound mark onto legs that are real in the true PAG. The
     * certificate is conjectured sound only when quantified over realizing MAGs, and the
     * corrected form (an ancestral side condition) is open.
     *
     * @param pag      the PAG to scan.
     * @param shielded true to report shielded colliders (R4/discriminating-path type),
     *                 false for unshielded ones (R0 sites and MAG-completion products).
     * @return the flagged triples, as colliders of the MAG.
     * @throws InterruptedException if interrupted.
     */
    private List<Triple> findMagCollidersWithSeparableLeg(Graph pag, boolean shielded)
            throws InterruptedException {
        Set<Set<Node>> separable = new LinkedHashSet<>();
        for (Edge e : findSpuriousEdges(pag)) {
            separable.add(Set.of(e.getNode1(), e.getNode2()));
        }
        for (Set<Node> pair : sepsets.keySet()) {
            List<Node> arr = new ArrayList<>(pair);
            if (arr.size() == 2 && sepsets.get(arr.get(0), arr.get(1)) != null) {
                separable.add(pair);
            }
        }

        Graph mag = GraphTransforms.zhangMagFromPag(new EdgeListGraph(pag));

        List<Triple> flagged = new ArrayList<>();
        Set<Triple> seen = new LinkedHashSet<>();

        for (Node c : mag.getNodes()) {
            List<Node> adj = mag.getAdjacentNodes(c);

            for (int i = 0; i < adj.size(); i++) {
                for (int j = i + 1; j < adj.size(); j++) {
                    Node a = adj.get(i);
                    Node b = adj.get(j);

                    if (!mag.isDefCollider(a, c, b)) continue;
                    if (mag.isAdjacentTo(a, b) != shielded) continue;

                    Triple t = new Triple(a, c, b);
                    if (!seen.add(t)) continue;

                    if (separable.contains(Set.of(a, c)) || separable.contains(Set.of(c, b))) {
                        flagged.add(t);
                    }
                }
            }
        }

        return flagged;
    }

    /**
     * Logs the collider-genuineness scan, splitting shielded from unshielded so the result
     * can be compared directly against the enumeration's mechanism breakdown. A clean scan
     * is reported as an absence of detections, never as a certificate of Markovness -- see
     * {@link #findMagCollidersWithSeparableLeg}.
     *
     * @param pag the PAG to report on (should be the graph actually returned).
     * @throws InterruptedException if interrupted.
     */
    private void reportColliderGenuineness(Graph pag) throws InterruptedException {
        List<Triple> shielded = findMagCollidersWithSeparableLeg(pag, true);
        List<Triple> unshielded = findMagCollidersWithSeparableLeg(pag, false);

        if (shielded.isEmpty() && unshielded.isEmpty()) {
            TetradLogger.getInstance().log("\nNo collider of the canonical MAG carries a "
                    + "test-separable leg. Markovness is NOT thereby certified: the canonical "
                    + "representative is a strict under-approximation, and a displaced firing "
                    + "leaves no separable leg to find.");
            return;
        }

        TetradLogger.getInstance().log("\nCollider-genuineness scan (canonical MAG): "
                + shielded.size() + " shielded, " + unshielded.size()
                + " unshielded collider(s) carry a separable leg; Markovness not certified.");
        if (!shielded.isEmpty()) {
            TetradLogger.getInstance().log("  shielded (discriminating-path type): " + shielded);
        }
        if (!unshielded.isEmpty()) {
            TetradLogger.getInstance().log("  unshielded (R0 / MAG completion): " + unshielded);
        }
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
        subAlg.setVerbose(false);
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
        grasp.setVerbose(superVerbose);
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
     * @return true if at least one edge was removed, false otherwise
     */
    /**
     * Mutable round counter, so round numbering is continuous across the levels
     * of a deepening schedule rather than restarting at each level.
     */
    private static final class RoundCounter {
        private int n = 0;

        int next() {
            return ++n;
        }
    }

    /**
     * The sequence of conditioning-set caps the main loop is run at, in order.
     *
     * <p>Deepening off: the singleton {@code {depth}}, which reproduces the
     * historical single-pass behavior exactly. Deepening on with a finite
     * {@code depth}: {@code deepeningStart, ..., depth}. Deepening on with
     * {@code depth == -1}: a finite ramp {@code deepeningStart, ...,
     * unlimitedRampCeiling} followed by one unlimited level, since an unlimited
     * cap gives the schedule nothing to count up to.</p>
     */
    private List<Integer> deepeningSchedule() {
        if (!iterativeDeepening) {
            return List.of(depth);
        }

        List<Integer> schedule = new ArrayList<>();

        if (depth >= 0) {
            for (int d = Math.min(deepeningStart, depth); d <= depth; d++) {
                schedule.add(d);
            }
        } else {
            for (int d = deepeningStart; d <= unlimitedRampCeiling; d++) {
                schedule.add(d);
            }
            schedule.add(-1);
        }

        if (schedule.isEmpty()) {
            schedule.add(depth);
        }

        return schedule;
    }

    /**
     * The main loop at the cap currently in {@link #activeDepth}: the
     * single-edge removal phase to fixpoint and the saturating step, iterated to
     * a joint fixpoint. This is the body that ran once inline before deepening
     * was added; with deepening off it is still called exactly once.
     *
     * @param excludeSelectionBias passed through to the removal gate
     * @param rounds               shared round counter for logging
     */
    private void runMainLoop(boolean excludeSelectionBias, RoundCounter rounds)
            throws InterruptedException {
        boolean progressed;

        do {
            progressed = false;

            do {
                TetradLogger.getInstance().log("\nRound: " + rounds.next());
            } while (removeEdgesRecursively(excludeSelectionBias));

            // Saturating step: fire whenever ANY test-separable adjacency survives, not only
            // two or more. A lone survivor means the single-edge phase proposed and REVERTED
            // it, which is exactly the case worth re-attempting in batch; skipping it would
            // return a graph carrying an adjacency already certified absent.
            List<Edge> spurious = findSpuriousEdges(interimPags.getLast());
            TetradLogger.getInstance().log(spurious.isEmpty()
                    ? "\nNo spurious edges remain."
                    : "\n" + spurious.size() + " spurious edge(s) remain: " + spurious);

            if (!spurious.isEmpty()) {
                boolean removed = tryToModifyGraph(spurious, excludeSelectionBias);
                TetradLogger.getInstance().log(removed
                        ? "\nSaturating step: spurious edges removed."
                        : "\nSaturating step REFUSED: deleting the confirmed-separable set together "
                        + "does not yield a legal MAG. In the oracle limit this is a certificate of "
                        + "unfaithfulness (a true edge was certified independent); from sample it is "
                        + "evidence of test error. Retaining the last gated PAG.");

                if (removed) progressed = true;
            }
        } while (progressed);
    }

    // The seed collider set is read from the `initialColliders` field by the commit step,
    // so it is no longer threaded through here as an unused parameter.
    private boolean removeEdgesRecursively(boolean excludeSelectionBias) {

        // This version does parallel lookahead, so that the only time graph rebuilding is done is when
        // edge removals are attempted.

        boolean changedThisSweep = false;

        // Ordered snapshot of the edges for this sweep. `from` is the scan position;
        // we never go back before it, so each edge is searched at most once per sweep.
        List<Edge> edgeList = new ArrayList<>(this.interimPags.getLast().getEdges());
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
                    excludeSelectionBias);

            if (didChange) {
                changedThisSweep = true;
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

    /**
     * Finds a test-confirmed separator for the pair of {@code edge}, or null.
     * <p>
     * <b>Changes from the pre-2026 implementation.</b> The search reach was narrower than
     * Fcit's in three ways, each of which is a place the exhaustive enumeration can expose a
     * miss; all three are closed here, leaving the MAG-side legality gate and both commit
     * routes untouched:
     * <ol>
     *   <li><b>NF pool.</b> Not-followed candidates were harvested from the seed blocking set
     *       ALONE, and not at all when the seed run came back indeterminate or unblockable.
     *       On a live view whose interim marks force conditioning that activates a collider
     *       route, the seed set is null and the NF layer was therefore empty on exactly the
     *       view that needs it. The pool is now the ambiguous members of the seed set UNIONED
     *       with the ambiguous common neighbors of the pair (the skeleton-relative pool B' of
     *       Def. rb-step).</li>
     *   <li><b>Additions.</b> Only removals from the blocking set were enumerated. Recursive
     *       blocking omits a common neighbor either because it conditioned around it or
     *       because a definite mark made the path look blocked; when that mark is an artifact
     *       of the edge under test, the omitted node must be enumerable back IN. Omitted
     *       common neighbors are now an additive layer.</li>
     *   <li><b>Mark-flip escalation.</b> Absent entirely. When the live-view sweep fails, the
     *       load-bearing blocked-collider marks the walk relied on are now collected and the
     *       sweep is retried on views that each distrust one of them.</li>
     * </ol>
     * Soundness is unchanged: the view only ever PROPOSES candidates, and every returned set
     * is confirmed by the independence test.
     */
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
        // exactly the cross-round inconsistency. tryToModifyGraph still judges MAG
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

        // Candidates already tested for this pair, shared across the live sweep and every
        // flip-view sweep so no subset is tested twice.
        Set<Set<Node>> tried = new HashSet<>();

        // Live-view sweep. Under mark-flip escalation, collect the load-bearing
        // blocked-collider marks the walk relied on: on failure, these are the
        // escalation's flip hypotheses.
        Set<RecursiveBlocking.Mark> conflicts = null;
        SweepOutcome live;

        if (useMarkFlipEscalation) {
            RecursiveBlocking.beginConflictCollection();
            try {
                live = sweepForSepset(interimPags.getLast(), x, y, deadline, tried);
            } finally {
                conflicts = RecursiveBlocking.endConflictCollection();
            }
        } else {
            live = sweepForSepset(interimPags.getLast(), x, y, deadline, tried);
        }

        if (live.sepset() != null) {
            return recordSepset(edge, x, y, live.sepset());
        }

        // Conflict-driven mark-flip escalation: structure-guided -- the failed walk
        // nominates the marks.
        if (useMarkFlipEscalation && conflicts != null && !conflicts.isEmpty()) {
            SweepOutcome flip = markFlipEscalation(x, y, conflicts, deadline, tried);
            if (flip.sepset() != null) {
                return recordSepset(edge, x, y, flip.sepset());
            }
        }

        return null;
    }

    /** Records a confirmed separator in the cross-round caches and wraps it for the caller. */
    private IndependenceCheck recordSepset(Edge edge, Node x, Node y, Set<Node> s)
            throws InterruptedException {
        double p = this.test.checkIndependence(x, y, s).getPValue();
        foundSepsets.put(Set.of(x, y), s);   // remember the fact, survives revert
        foundPValues.put(Set.of(x, y), p);
        return new IndependenceCheck(edge, s, p);
    }

    /**
     * One sweep against one view of the graph. The view supplies proposals only; every
     * returned set is test-confirmed. Candidate order is canonical (name-sorted lists,
     * subsets in SublistGenerator order), so the first confirmed separator is a
     * deterministic function of (view, x, y).
     *
     * <p>Layers: NF enumeration over the ambiguous (circle-bearing) members of the seed
     * blocking set unioned with the ambiguous common neighbors; per NF, removals over the
     * whole blocking set (common neighbors first) and additions over common neighbors the
     * blocking set omitted. For every subset S of the swept common neighbors this family
     * contains a candidate that conditions on S and withholds the rest both from traversal
     * and from the tested set.</p>
     */
    private SweepOutcome sweepForSepset(Graph view, Node x, Node y, long deadline, Set<Set<Node>> tried)
            throws InterruptedException {

        boolean sawIndeterminate = false;

        RecursiveBlocking.BlockingResult b0 = RecursiveBlocking.blockPathsRecursively(
                view, x, y, Set.of(), Set.of(), recursiveDepth, this.activeDepth, rbRadius, 1, true,
                deadline);
        if (b0.indeterminate()) {
            sawIndeterminate = true;
        }

        List<Node> common = new ArrayList<>(view.getAdjacentNodes(x));
        common.retainAll(view.getAdjacentNodes(y));
        common.sort(NODE_ORDER);

        // NF candidates: ambiguous seed members UNIONED with ambiguous common neighbors.
        // The union is the fix for the empty-NF-layer case (see the method Javadoc above).
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

        SublistGenerator nfGen = new SublistGenerator(nfCand.size(), nfCand.size());
        int[] nfChoice;

        while ((nfChoice = nfGen.next()) != null) {
            if (System.currentTimeMillis() > deadline) return new SweepOutcome(null, true);
            if (!view.isAdjacentTo(x, y)) break; // edge already removed upstream

            Set<Node> notFollowed = GraphUtils.asSet(nfChoice, nfCand);

            RecursiveBlocking.BlockingResult result = notFollowed.isEmpty() ? b0
                    : RecursiveBlocking.blockPathsRecursively(
                    view, x, y, Set.of(), notFollowed, recursiveDepth, this.activeDepth, rbRadius, 1, true,
                    deadline);

            if (result.indeterminate()) {
                sawIndeterminate = true;
                continue;
            }

            Set<Node> B = result.blockingSet();
            if (B == null) {
                continue; // Unblockable under this NF; try another NF.
            }

            Set<Node> base = new LinkedHashSet<>(B);

            // Removal candidates: the whole base, common neighbors first, each block
            // name-sorted.
            List<Node> removalCandidates = new ArrayList<>();
            for (Node n : base) if (common.contains(n)) removalCandidates.add(n);
            removalCandidates.sort(NODE_ORDER);
            List<Node> restOfBase = new ArrayList<>();
            for (Node n : base) if (!common.contains(n)) restOfBase.add(n);
            restOfBase.sort(NODE_ORDER);
            removalCandidates.addAll(restOfBase);

            // Additive candidates: common neighbors the blocking search omitted.
            List<Node> addCandidates = new ArrayList<>();
            for (Node c : common) if (!base.contains(c)) addCandidates.add(c);

            SublistGenerator aGen = new SublistGenerator(addCandidates.size(), addCandidates.size());
            int[] aChoice;

            while ((aChoice = aGen.next()) != null) {
                if (System.currentTimeMillis() > deadline) return new SweepOutcome(null, true);

                Set<Node> A = GraphUtils.asSet(aChoice, addCandidates);

                SublistGenerator cGen = new SublistGenerator(removalCandidates.size(),
                        removalCandidates.size());
                int[] cChoice;

                while ((cChoice = cGen.next()) != null) {
                    if (System.currentTimeMillis() > deadline) return new SweepOutcome(null, true);

                    Set<Node> S = new LinkedHashSet<>(base);
                    S.removeAll(GraphUtils.asSet(cChoice, removalCandidates));
                    S.addAll(A);

                    if (this.activeDepth != -1 && S.size() > this.activeDepth) continue;

                    if (!tried.add(S)) continue; // already tested this candidate

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
     * Conflict-driven mark-flip escalation. For each load-bearing arrowhead the failed live
     * sweep reported, build a view with that arrowhead circled and re-run the sweep. The
     * failure itself nominates the hypotheses, so the search is bounded by the marks the walk
     * actually leaned on rather than by any pool's powerset.
     */
    private SweepOutcome markFlipEscalation(Node x, Node y, Set<RecursiveBlocking.Mark> conflicts,
                                            long deadline, Set<Set<Node>> tried)
            throws InterruptedException {
        List<RecursiveBlocking.Mark> marks = new ArrayList<>(conflicts);
        marks.sort(Comparator
                .comparing((RecursiveBlocking.Mark m) -> m.at().getName())
                .thenComparing(m -> m.from().getName()));

        int cap = (maxMarkFlips < 0) ? marks.size() : Math.min(maxMarkFlips, marks.size());
        boolean sawIndeterminate = false;

        for (int i = 0; i < cap; i++) {
            if (System.currentTimeMillis() > deadline) return new SweepOutcome(null, true);

            RecursiveBlocking.Mark m = marks.get(i);

            Graph flipped = new EdgeListGraph(interimPags.getLast());
            flipped.setEndpoint(m.from(), m.at(), Endpoint.CIRCLE);

            checkCounter.increment("mark-flip escalation (view swept)");

            SweepOutcome out = sweepForSepset(flipped, x, y, deadline, tried);

            if (out.sepset() != null) {
                TetradLogger.getInstance().log("MARK-FLIP: separator for " + x + " *-* " + y
                        + " found under distrust of the arrowhead at " + m.at()
                        + " on " + m.from() + " *-> " + m.at() + ": " + out.sepset());
                return out;
            }

            if (out.indeterminate()) sawIndeterminate = true;
        }

        return new SweepOutcome(null, sawIndeterminate);
    }

    /** True iff v has at least one circle endpoint in the view. */
    private static boolean hasCircleEndpoint(Graph view, Node v) {
        for (Node w : view.getAdjacentNodes(v)) {
            if (view.getEndpoint(v, w) == Endpoint.CIRCLE
                    || view.getEndpoint(w, v) == Endpoint.CIRCLE) {
                return true;
            }
        }
        return false;
    }

//    private boolean tryToModifyGraph(Node x, Node y, Set<Node> b, double pValue, boolean excludeSelectionBias) {
//        Edge _edge = interimPags.getLast().getEdge(x, y);
//        Graph _pag = new EdgeListGraph(interimPags.getLast());
//
//        // MAG of the pre-removal (legal) PAG, so zhangMagFromPag's circle resolution
//        // is well defined. Then delete the edge under test.
//        Graph _mag = GraphTransforms.zhangMagFromPag(_pag);
//        _mag.removeEdge(x, y);
//
//        Set<Node> prevSepset = sepsets.get(x, y);
//        sepsets.set(x, y, b);
//
//        // Stamp every recorded sepset's colliders onto the MAG we keep (idempotent),
//        // so the PAG we carry forward retains those arrowheads and RB sees fewer circles.
//        orientSepsetCollidersInMag(_mag, sepsets);
//
//        PagLegalityCheck.LegalMagRet legal =
//                PagLegalityCheck.isLegalMag(_mag, new LinkedHashSet<>(selection));
//
//        if (!legal.isLegalMag()) {
//            if (verbose) {
//                TetradLogger.getInstance().log("\tTried removing " + _edge
//                        + ", but it didn't lead to a PAG, sepset = " + b);
//                System.out.println("\tReason = " + legal.getReason());
//            }
//
//            sepsets.set(x, y, prevSepset);
//            return false;
//        }
//
//        if (verbose) {
//            TetradLogger.getInstance().log("Removing " + _edge + ", sepset = " + b
//                    + (Double.isNaN(pValue) ? "" : ", p = " + pValue));
//        }
//
//        // Colliders are baked into _mag, so the PAG you carry forward keeps those
//        // arrowheads and RB sees fewer circle endpoints. Pass the real flag, not false.
//        this.interimPags.add(new MagToPag(_mag).convert(false, excludeSelectionBias));
//        return true;
//    }

    private boolean tryToModifyGraph(Node x, Node y, Set<Node> b, double pValue, boolean excludeSelectionBias) {
        Edge _edge = interimPags.getLast().getEdge(x, y);

        Set<Node> prevSepset = sepsets.get(x, y);
        sepsets.set(x, y, b);

        // --- legality gate: still judged on the MAG, but on the MAG of the REORIENTED
        //     post-deletion PAG.
        //
        // Change from the pre-2026 implementation: the candidate MAG was the Zhang
        // completion of the CURRENT PAG with the edge then deleted from it. Those
        // orientations were derived while the edge was still present, so the completion
        // resolved circles using stale marks and could manufacture an almost-cycle that the
        // correctly reoriented graph does not have -- refusing a removal whose reorientation
        // is in fact the true PAG. (Witness: the PKE14 model ca....aaaa.acccacacacaccc, where
        // removing V2 *-* V5 on sepset {V6} was refused for "directed path exists from V3 to
        // V2", arising from V3 o-o V6 completing as V3 --> V6; reorienting first stamps
        // V6 o-> V3 from the recorded separator and the candidate is legal -- and is exactly
        // G*.) The reorientation is the same one the PAG route already applied AFTER the
        // gate, so this moves work rather than adding it; what is new is that the gate now
        // judges the object the algorithm would actually carry forward.
        Graph _pag = new EdgeListGraph(interimPags.getLast());
        _pag.removeEdge(x, y);
        reorientCandidate(_pag, excludeSelectionBias);

        Graph _mag = GraphTransforms.zhangMagFromPag(new EdgeListGraph(_pag));
        orientSepsetCollidersInMag(_mag, sepsets);

        PagLegalityCheck.LegalMagRet legal =
                PagLegalityCheck.isLegalMag(_mag, new LinkedHashSet<>(selection));
        if (!legal.isLegalMag()) {
            if (verbose) {
                TetradLogger.getInstance().log("\tTried removing " + _edge
                        + ", but it didn't lead to a PAG, sepset = " + b);
            }
            sepsets.set(x, y, prevSepset);
            return false;
        }

        if (verbose) {
            TetradLogger.getInstance().log("Removing " + _edge + ", sepset = " + b
                    + (Double.isNaN(pValue) ? "" : ", p = " + pValue));
        }

        // --- Carry the accepted state forward. The MAG route projects back through the
        //     canonical Zhang MAG (FCIT-ZM proper); the PAG route reorients the PAG
        //     directly, leaving endpoints as circles until a rule commits them. The gate
        //     above is identical either way. ---
        if (commitRoute == COMMIT_ROUTE.MAG) {
            pushPag(new MagToPag(_mag).convert(false, excludeSelectionBias));
            return true;
        }

        // PAG route: carry forward the very graph the gate just certified.
        pushPag(_pag);
        return true;
    }

    /**
     * The candidate reorientation: reset to circles, RECALL THE SEED COLLIDERS, stamp R0 from
     * the recorded separators, and close under the final rules.
     * <p>
     * Seed soundness is the single orientation premise the soundness argument consumes, so
     * the seed set is re-supplied at every reorientation; recomputing colliders from the
     * current graph would instead re-derive whatever was stamped earlier, unsound marks
     * included. The {@code adjustForExtraSepsets} stamp is a change from the pre-2026
     * implementation: without it the colliders created by this search's own removals were
     * never seeded on the interim graphs (only the seed colliders were), which both
     * under-oriented the carried state and -- through the Zhang completion -- produced the
     * spurious almost-cycles that made the gate refuse sound removals.
     */
    private void reorientCandidate(Graph pag, boolean excludeSelectionBias) {
        pag.reorientAllWith(Endpoint.CIRCLE);
        fciOrient.orient(pag, new HashSet<>(initialColliders), excludeSelectionBias);
        adjustForExtraSepsets(sepsets, pag);
        fciOrient.finalOrientation(pag, excludeSelectionBias);
    }

    private boolean tryToModifyGraph(List<Edge> edges,
                                     boolean excludeSelectionBias) {
        Map<Edge, Set<Node>> prev = new LinkedHashMap<>();    // for clean rollback

        Graph _pag = new EdgeListGraph(interimPags.getLast());

        for (Edge edge : edges) {
            Node m = edge.getNode1();
            Node n = edge.getNode2();

            // Prefer a committed separator; fall back to the deadlock-survivor one.
            Set<Node> z = sepsets.get(m, n);
            if (z == null) z = foundSepsets.get(Set.of(m, n));

            prev.put(edge, sepsets.get(m, n));
            sepsets.set(m, n, z);

            _pag.removeEdge(m, n);
        }

        // Reorient the post-deletion PAG before completing it, for the same reason as the
        // single-edge gate: a completion of the pre-deletion orientations can manufacture an
        // almost-cycle the correctly reoriented graph does not have, and the saturating pass
        // is precisely where a refusal is read as a certificate of unfaithfulness -- so a
        // spurious refusal here is maximally misleading.
        reorientCandidate(_pag, excludeSelectionBias);

        Graph _mag = GraphTransforms.zhangMagFromPag(new EdgeListGraph(_pag));

        // Stamp all sepset-implied colliders, then judge MAG legality once.
        orientSepsetCollidersInMag(_mag, sepsets);

        PagLegalityCheck.LegalMagRet legal =
                PagLegalityCheck.isLegalMag(_mag, new LinkedHashSet<>(selection));

        if (!legal.isLegalMag()) {
            if (verbose) {
                TetradLogger.getInstance().log("\tTried removing " + edges
                        + ", but it didn't lead to a PAG");
                System.out.println("\tReason = " + legal.getReason());
            }
            // No interimPags.removeLast() here — nothing was added; the add happens
            // only on the success path below. Just roll back the sepset writes.
            for (Edge edge : edges) {
                sepsets.set(edge.getNode1(), edge.getNode2(), prev.get(edge));
            }
            return false;
        }

        if (verbose) {
            TetradLogger.getInstance().log("Removing " + edges + " (multi-edge), reached a PAG");
        }

        if (commitRoute == COMMIT_ROUTE.MAG) {
            pushPag(new MagToPag(_mag).convert(false, excludeSelectionBias));
            return true;
        }

        // PAG route: carry forward the very graph the gate just certified.
        pushPag(_pag);
        return true;
    }

//    private boolean tryToModifyGraph(List<Edge> edges,
//                                     boolean excludeSelectionBias) {
//        // --- legality gate: unchanged, still judged on the MAG ---
//        Graph _mag = GraphTransforms.zhangMagFromPag(
//                new EdgeListGraph(interimPags.getLast()));   // one MAG of the current legal PAG
//
//        Map<Edge, Set<Node>> prev = new LinkedHashMap<>();   // for clean rollback
//
//        for (Edge edge : edges) {
//            Node m = edge.getNode1();
//            Node n = edge.getNode2();
//
//            // Prefer a committed separator; fall back to the deadlock-survivor one.
//            Set<Node> z = sepsets.get(m, n);
//            if (z == null) z = foundSepsets.get(Set.of(m, n));
//
//            prev.put(edge, sepsets.get(m, n));
//            sepsets.set(m, n, z);
//
//            _mag.removeEdge(m, n);
//        }
//
//        // Stamp all sepset-implied colliders, then judge MAG legality once.
//        orientSepsetCollidersInMag(_mag, sepsets);
//
//        PagLegalityCheck.LegalMagRet legal =
//                PagLegalityCheck.isLegalMag(_mag, new LinkedHashSet<>(selection));
//
//        if (!legal.isLegalMag()) {
//            if (verbose) {
//                TetradLogger.getInstance().log("\tTried removing " + edges
//                        + ", but it didn't lead to a PAG");
//                System.out.println("\tReason = " + legal.getReason());
//            }
//            // Nothing was added to interimPags; just roll back the sepset writes.
//            for (Edge edge : edges) {
//                sepsets.set(edge.getNode1(), edge.getNode2(), prev.get(edge));
//            }
//            return false;
//        }
//
//        if (verbose) {
//            TetradLogger.getInstance().log("Removing " + edges + " (multi-edge), reached a PAG");
//        }
//
//        // --- carry the PAG forward by orienting the PAG DIRECTLY, not by
//        //     MagToPag(zhangMagFromPag(...)). Endpoints stay circles until R4 fires,
//        //     instead of being committed to tails by zhangMagFromPag. ---
//        Graph _pag = new EdgeListGraph(interimPags.getLast());
//        for (Edge edge : edges) {
//            _pag.removeEdge(edge.getNode1(), edge.getNode2());
//        }
//
////        _pag.reorientAllWith(Endpoint.CIRCLE);   // keep iff you kept it in the single-edge version
////        fciOrient.orient(_pag, new HashSet<>(), excludeSelectionBias);                  // R0 + R1–R4 via the test-based strategy
//
//        this.interimPags.add(_pag);
//        return true;
//    }

    /**
     * Wipes to circles, recalls the SEED unshielded colliders, and re-closes under R0 and
     * R1-R4 from the recorded separators. Recalling the seed set is what keeps the single
     * orientation premise of the soundness argument in force: the earlier version recomputed
     * colliders from the graph being reoriented, which re-derives any collider stamped
     * unsoundly at an earlier step and so silently discharges the premise it needs.
     */
    private Graph coldReorient(Graph graph) {
        Graph finalPag = graph.copy();

        R0R4StrategyTestBased strategy = new R0R4StrategyTestBased(test, timeout);
        strategy.setSepsetMap(sepsets);
        strategy.setBlockingType(R0R4StrategyTestBased.BlockingType.RECURSIVE);
        strategy.setDepth(depth);

        FciOrient fciOrient = new FciOrient(strategy);
        fciOrient.setVerbose(false);
        fciOrient.setParallel(false);
        fciOrient.setCompleteRuleSetUsed(completeRuleSetUsed);
        fciOrient.setRecursiveDepth(recursiveDepth);
        fciOrient.setMaxDiscriminatingPathLength(maxDiscriminatingPathLength);
        fciOrient.setUseR4(true);
        fciOrient.setKnowledge(knowledge);

        finalPag.reorientAllWith(Endpoint.CIRCLE);
        // Re-apply background-knowledge endpoint marks before recalling colliders, mirroring
        // ruleR0's ordering. Previously this cold pass discarded the knowledge marks placed by
        // dagToPag and never restored them (reorientCandidate does, via fciOrient.orient), so
        // the returned graph ignored tier knowledge entirely.
        fciOrient.fciOrientbk(knowledge, finalPag, finalPag.getNodes(), excludeSelectionBias);
        GraphUtils.recallInitialColliders(finalPag, initialColliders, knowledge);

        // Stamp R0 from the RECORDED separators. Change from the pre-2026 implementation:
        // coldReorient recalled only the SEED colliders and then went straight to
        // finalOrientation, which applies R1-R10 but never R0. The colliders created by this
        // search's own removals -- for every recorded pair now non-adjacent, each common
        // neighbor absent from the recorded separator -- were therefore never stamped. The
        // missing arrowheads then let R9/R10 add tails that the true PAG leaves as circles,
        // which shows up as an ORIENTATION-only miss on an otherwise exact skeleton. The
        // verdict is by membership in the recorded set (the one whose test licensed the
        // deletion), never re-derived from the current graph, matching Fcit's
        // adjustForExtraSepsets.
        adjustForExtraSepsets(sepsets, finalPag);

        fciOrient.finalOrientation(finalPag);   // R1-R10; R0 stamped above from the recorded sepsets

        return finalPag;
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
     * Sets the verbosity level of the search algorithm.
     *
     * @param superVerbose true to enable superVerbose mode, false to disable it
     */
    public void setSuperVerbose(boolean superVerbose) {
        this.superVerbose = superVerbose;
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
//        this.superVerbose = verbose;
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
     * Sets whether the main loop is run once at the full depth (false, the
     * default and the historical behavior) or repeatedly at increasing
     * conditioning-set caps up to the full depth (true).
     *
     * <p>Enabling this is not output-identical to a single pass: soundness is
     * unchanged, but removals are globally ordered by separator size, so which
     * valid separator is found first -- and hence the orientation trajectory --
     * changes.</p>
     *
     * @param iterativeDeepening true to run the deepening schedule.
     */
    public void setIterativeDeepening(boolean iterativeDeepening) {
        this.iterativeDeepening = iterativeDeepening;
    }

    /**
     * Sets the first conditioning-set cap in the deepening schedule (default 0).
     * Has no effect unless iterative deepening is enabled.
     *
     * @param deepeningStart the first cap; must be &gt;= 0.
     */
    public void setDeepeningStart(int deepeningStart) {
        if (deepeningStart < 0) {
            throw new IllegalArgumentException("Deepening start must be >= 0: " + deepeningStart);
        }

        this.deepeningStart = deepeningStart;
    }

    /**
     * Sets the highest finite cap in the deepening ramp used when the depth is
     * unlimited (-1); after this level one unlimited level is run. Default 5.
     * Has no effect unless iterative deepening is enabled and depth is -1.
     *
     * @param unlimitedRampCeiling the highest finite cap; must be &gt;= 0.
     */
    public void setUnlimitedRampCeiling(int unlimitedRampCeiling) {
        if (unlimitedRampCeiling < 0) {
            throw new IllegalArgumentException(
                    "Unlimited ramp ceiling must be >= 0: " + unlimitedRampCeiling);
        }

        this.unlimitedRampCeiling = unlimitedRampCeiling;
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
     * Sets the radius for RA (Recursive Association) algorithm.
     *
     * @param rbRadius the radius for RA algorithm to be set
     */
    /**
     * Sets whether the conflict-driven mark-flip escalation runs when the live-view sweep
     * fails to find a separator. True by default (2026-8 reach repair); set false to
     * reproduce the pre-repair search reach.
     *
     * @param useMarkFlipEscalation whether to run the escalation.
     */
    public void setUseMarkFlipEscalation(boolean useMarkFlipEscalation) {
        this.useMarkFlipEscalation = useMarkFlipEscalation;
    }

    /**
     * Sets the maximum number of mark-flip hypotheses tried per pair; -1 for unlimited.
     *
     * @param maxMarkFlips the cap.
     */
    public void setMaxMarkFlips(int maxMarkFlips) {
        this.maxMarkFlips = maxMarkFlips;
    }

    /**
     * Sets the radius value for the rbRadius property.
     *
     * @param rbRadius the radius value to be set, typically a positive integer.
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
     * Records a newly committed state, trimming the history unless a trace was requested.
     */
    private void pushPag(Graph pag) {
        this.interimPags.add(pag);
        if (!keepInterimTrace && this.interimPags.size() > 1) {
            Graph last = this.interimPags.getLast();
            this.interimPags.clear();
            this.interimPags.add(last);
        }
    }

    /**
     * Sets how an accepted deletion is carried forward; the MAG legality gate applies to
     * both routes. Defaults to {@link COMMIT_ROUTE#PAG}.
     *
     * @param commitRoute the commit route.
     */
    public void setCommitRoute(COMMIT_ROUTE commitRoute) {
        this.commitRoute = commitRoute;
    }

    /**
     * Sets whether the full trajectory of committed PAGs is retained. Off by default.
     *
     * @param keepInterimTrace true to retain every interim PAG.
     */
    public void setKeepInterimTrace(boolean keepInterimTrace) {
        this.keepInterimTrace = keepInterimTrace;
    }

    /**
     * How an accepted deletion is carried forward past the MAG legality gate.
     */
    public enum COMMIT_ROUTE {
        /**
         * Reorient the PAG directly; endpoints stay circles until a rule commits them.
         */
        PAG,
        /**
         * Project back through the canonical Zhang MAG (the route the paper calls FCIT-ZM).
         */
        MAG
    }

    /**
     * Wraps the test-based strategy so R4 can handle a discriminating path whose endpoint
     * pair has no recorded separator. Such a pair is typically nonadjacent in the seed and so
     * never the subject of a deletion, leaving nothing recorded for it; the delegate's
     * single-shot blocking search can then come back empty on a partly-reoriented graph --
     * the marks R0/R1-R3 have already stamped need not be sound -- and R4 declines, leaving a
     * circle where the PAG has an arrowhead. In FCIT that circle also made the trial
     * reorientation non-maximal (an inducing path opened by the un-oriented endpoint), so the
     * legality gate refused a deletion whose separator had in fact been found.
     * <p>
     * Here the pair gets its own spanning search, including an orientation-blind pass; the
     * confirmed separator is recorded; and the firing is adjudicated by membership. Found
     * once, read back thereafter. R0 and everything else delegate unchanged.
     */
    private final class SepsetBackfillR0R4Strategy implements R0R4Strategy {
        private final R0R4StrategyTestBased delegate;

        SepsetBackfillR0R4Strategy(R0R4StrategyTestBased delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean isUnshieldedCollider(Graph graph, Node a, Node b, Node c) {
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

            Set<Node> s = sepsets.get(x, y);
            if (s == null) s = foundSepsets.get(Set.of(x, y));

            if (s == null) {
                Set<Node> found = searchSepsetForPair(graph, x, y);

                if (found == null) {
                    r4Declined++;
                    if (verbose) {
                        TetradLogger.getInstance().log("R4 backfill: no separator confirmed for ("
                                + x.getName() + "," + y.getName() + "); declining to orient.");
                    }
                    return Pair.of(discriminatingPath, false);
                }

                // A test-confirmed separator for a nonadjacent pair is a data fact, so
                // committing it is safe even if the trial reorientation around this call is
                // later reverted: it licenses R0 stamps on exactly the pair it separates,
                // unlike a deletion commitment, which is graph-relative.
                s = found;
                sepsets.set(x, y, s);
                foundSepsets.putIfAbsent(Set.of(x, y), s);
                r4Backfilled++;

                if (verbose) {
                    TetradLogger.getInstance().log("R4 backfill: recorded sepset(" + x.getName()
                            + "," + y.getName() + ") = " + s + " (test-confirmed).");
                }
            } else {
                r4SepsetBacked++;
            }

            // Adjudicate by membership: v in Sep(x, y) => noncollider (tail at v), else collider.
            // Compared by name, so a MAG/PAG round trip cannot break identity.
            final String vName = v.getName();
            boolean vInS = s.stream().anyMatch(n -> n.getName().equals(vName));

            if (!discriminatingPath.existsIn(graph)) {
                return Pair.of(discriminatingPath, false);
            }

            if (graph.getEndpoint(y, v) != Endpoint.CIRCLE) {
                return Pair.of(discriminatingPath, false);
            }

            if (vInS) {
                graph.setEndpoint(y, v, Endpoint.TAIL);
                return Pair.of(discriminatingPath, true);
            }

            if (!FciOrient.isArrowheadAllowed(w, v, graph, knowledge)) {
                return Pair.of(discriminatingPath, false);
            }

            if (!FciOrient.isArrowheadAllowed(y, v, graph, knowledge)) {
                return Pair.of(discriminatingPath, false);
            }

            graph.setEndpoint(w, v, Endpoint.ARROW);
            graph.setEndpoint(y, v, Endpoint.ARROW);
            return Pair.of(discriminatingPath, true);
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
         * Spanning separator search for one nonadjacent pair, self-contained on the graph
         * passed to R4 (not interimPags, which is a different graph at this point). Same
         * family as the sweep in findIndependenceCheckRecursive -- not-followed enumeration
         * over the ambiguous members of the blocking set, common neighbours forced in,
         * removals enumerated from empty upward -- run first against the live graph and then,
         * if that proposes nothing, against the bare skeleton.
         */
        private Set<Node> searchSepsetForPair(Graph graph, Node x, Node y)
                throws InterruptedException {
            final long deadline = (timeout < 0L)
                    ? Long.MAX_VALUE
                    : System.currentTimeMillis() + timeout;

            Set<Node> found = sweepOnView(graph, x, y, deadline, "oriented");
            if (found != null) return found;

            // Orientation-blind pass. Mid-FciOrient the graph carries marks R0/R1-R3 have
            // stamped, some of them unsound; recursive blocking reads those marks and can
            // report the pair unblockable, returning no blocking set at all -- the candidate
            // family is then empty and not one test runs. On the bare skeleton no triple is a
            // collider, nothing counts as pre-blocked, and the proposal depends on the
            // adjacencies alone. This weakens nothing: the blind view only PROPOSES, and every
            // candidate is still confirmed against the test before it is returned or recorded.
            Graph blind = new EdgeListGraph(graph);
            for (Edge e : new ArrayList<>(blind.getEdges())) {
                blind.setEndpoint(e.getNode1(), e.getNode2(), Endpoint.CIRCLE);
                blind.setEndpoint(e.getNode2(), e.getNode1(), Endpoint.CIRCLE);
            }

            return sweepOnView(blind, x, y, deadline, "blind");
        }

        /**
         * One sweep against one view. The view supplies proposals only.
         */
        private Set<Node> sweepOnView(Graph graph, Node x, Node y, long deadline, String label)
                throws InterruptedException {

            RecursiveBlocking.BlockingResult b0 = RecursiveBlocking.blockPathsRecursively(
                    graph, x, y, Set.of(), Set.of(), recursiveDepth, depth, rbRadius, 1, true,
                    deadline);

            if (superVerbose) {
                TetradLogger.getInstance().log("    R4 backfill [" + label + "] RB seed: "
                        + (b0.indeterminate() ? "INDETERMINATE"
                        : b0.blockingSet() == null ? "UNBLOCKABLE (no blocking set -- family empty)"
                        : "blocking set = " + b0.blockingSet()));
            }

            Set<Node> nfCandSet = new LinkedHashSet<>();
            if (!b0.indeterminate() && b0.blockingSet() != null) {
                for (Node n : b0.blockingSet()) {
                    if (graph.getAdjacentNodes(n).stream().anyMatch(
                            w2 -> graph.getEndpoint(n, w2) == Endpoint.CIRCLE
                                    || graph.getEndpoint(w2, n) == Endpoint.CIRCLE)) {
                        nfCandSet.add(n);
                    }
                }
            }

            List<Node> nfCand = new ArrayList<>(nfCandSet);
            nfCand.sort(Comparator.comparing(Node::getName));

            List<Node> common = graph.getAdjacentNodes(x);
            common.retainAll(graph.getAdjacentNodes(y));
            List<Node> removalCandidates = new ArrayList<>(common);
            removalCandidates.sort(Comparator.comparing(Node::getName));

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

                Set<Node> B = new LinkedHashSet<>(result.blockingSet());
                B.addAll(common);

                SublistGenerator cGen = new SublistGenerator(
                        removalCandidates.size(), removalCandidates.size());
                int[] cChoice;
                while ((cChoice = cGen.next()) != null) {
                    if (System.currentTimeMillis() > deadline) return null;

                    Set<Node> S = new LinkedHashSet<>(B);
                    S.removeAll(GraphUtils.asSet(cChoice, removalCandidates));

                    if (depth != -1 && S.size() > depth) continue;

                    checkCounter.increment("R4 endpoint-pair search (test executed)");

                    if (test.checkIndependence(x, y, S).isIndependent()) {
                        return S;
                    }
                }
            }

            return null;
        }
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

    /** Result of one sweep: a confirmed separator (or null) and whether any budget/blocking
     *  search came back indeterminate, so "not found" can be distinguished from "none exists". */
    private record SweepOutcome(Set<Node> sepset, boolean indeterminate) {
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

