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
 * A Markov-preserving variant of {@link FcitZm}. It is byte-for-byte the same search as FcitMag, except that every
 * committed edge removal (both the single-edge and the multi-edge path) is passed through an additional
 * <em>Markov-preservation gate</em> after the legal-MAG check. A move is committed only if it introduces no spurious
 * m-separation; otherwise it is reverted exactly as an illegal-MAG move would be.
 * <p>
 * <b>What it guarantees.</b> Run the chain of PAGs as an induction. If the base PAG (here {@code dagToPag} of the BOSS
 * DAG) is Markov to the data, then each committed move preserves Markovness, so every PAG in the chain is Markov. The
 * gate enforces the exact condition: a move from a Markov MAG {@code M} to {@code M'} keeps Markovness iff every
 * m-separation entailed by {@code M'} but not by {@code M} is a real conditional independence in the data. The legal-MAG
 * check alone does not give this (legality is not Markovness), and neither does the single tested fact x &perp; y | S,
 * because deleting x&ndash;y and re-stamping colliders can entail further separations for other pairs.
 * <p>
 * <b>How the check stays cheap.</b> The gate enumerates the ordered local Markov constraints of {@code M'} and, using
 * the previous (Markov) MAG as an oracle, skips every constraint that {@code M} already entailed (those hold by the
 * induction hypothesis). Only the genuinely new constraints cost a data test. The skip filter is a graph m-separation
 * query on the previous MAG, which is fast and confined to the neighborhood of the change. Under a compositional
 * graphoid distribution (e.g. regular Gaussian), the ordered local constraints are equivalent to global Markovness, so
 * the gate is a complete certificate, not merely a sufficient heuristic.
 * <p>
 * <b>Assumptions / caveats.</b> (i) The base PAG is Markov &mdash; true at the population level if BOSS lands a Markov
 * DAG, only approximately at finite n. (ii) A consistent CI test; with finite data the &ldquo;guarantee&rdquo; is
 * really &ldquo;no move introduces a test-detected spurious separation.&rdquo; (iii) The distribution is a compositional
 * graphoid (licenses ordered-local &hArr; global). (iv) The Markov-pillow construction here is written for MAGs over
 * directed (&rarr;) and bidirected (&harr;) edges, i.e. the {@code excludeSelectionBias} regime; selection-bias MAGs
 * with undirected edges would need the extended pillow.
 *
 * @author josephramsey
 */
public final class FcitMagMarkov implements IGraphSearch {
    /**
     * The independence test.
     */
    private final IndependenceTest test;
    /**
     * The score.
     */
    private final Score score;
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
    private @NotNull Graph pag = new EdgeListGraph();
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
     * TODO: Make this a parameter.
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
     * Separators discovered for a pair during any sweep, kept across rounds.
     * Distinct from {@link #sepsets}, which records only committed (legal-PAG)
     * separations and is rolled back on a reverted removal. Because X _||_ Y | S
     * is a property of the data, not the current PAG, a set that separated a pair
     * once still separates it; reusing it keeps a pair's recorded sepset stable
     * across rounds instead of being re-derived (and possibly differing) each time
     * the edge is reconsidered after a reverted removal.
     */
    private final Map<Set<Node>, Set<Node>> foundSepsets = new ConcurrentHashMap<>();

    private long timeout = -1L;

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
    public FcitMagMarkov(IndependenceTest test, Score score) {
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

    private static void redoGfciOrientation(Graph pag, FciOrient fciOrient, Knowledge knowledge,
                                            Set<Triple> initialColliders, SepsetMap sepsets, boolean excludeSelectionBias,
                                            boolean superVerbose) {
        GraphUtils.reorientWithCircles(pag, superVerbose);
        GraphUtils.recallInitialColliders(pag, initialColliders, knowledge);
        adjustForExtraSepsets(sepsets, pag);
        fciOrient.finalOrientation(pag, excludeSelectionBias);
    }

    /**
     * Refines the structure of the Partial Ancestral Graph (PAG) by adjusting separation sets based on additional
     * independence evidence and ensuring consistency with known independence and causality constraints. This method
     * identifies and orients specific edges in the PAG to maintain its validity.
     * <p>
     * The method performs the following steps: (a) Iterates over all edges in the separation set map's key set. (cond)
     * For each edge, identifies adjacent nodes in the PAG and finds their common neighbors. (c) Removes adjacency
     * between the nodes if applicable and logs the operation if verbose mode is enabled. (d) Examines each common
     * neighbor, checking whether it is part of the separation set for the given nodes. If it is not part of the
     * separation set and does not create a forbidden collider, the endpoints of the edge between the common neighbor
     * and the adjacent nodes are adjusted to a directed orientation. (e) Logs oriented relationships in verbose mode.
     * <p>
     * This adjustment ensures proper handling of induced dependencies and maintains the correctness of the causal
     * structure represented by the PAG. The orientation of edges follows the rules
     */
    private static void adjustForExtraSepsets(SepsetMap sepsets, Graph pag) {
        for (Set<Node> edge : sepsets.keySet()) {
            List<Node> arr = new ArrayList<>(edge);

            Node x = arr.get(0);
            Node y = arr.get(1);

            if (pag.isAdjacentTo(x, y)) {
                continue;
            }

            List<Node> common = pag.getAdjacentNodes(x);
            common.retainAll(pag.getAdjacentNodes(y));

            for (Node node : common) {
                if (!sepsets.get(x, y).contains(node)) {
                    if (!pag.isDefCollider(x, node, y)) {
                        pag.setEndpoint(x, node, Endpoint.ARROW);
                        pag.setEndpoint(y, node, Endpoint.ARROW);
                    }
                }
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

        TetradLogger.getInstance().log("===Starting FCIT (Markov-preserving)===");

        R0R4StrategyTestBased strategy = new R0R4StrategyTestBased(test, timeout);
        strategy.setSepsetMap(sepsets);
        strategy.setVerbose(superVerbose);
        strategy.setBlockingType(R0R4StrategyTestBased.BlockingType.RECURSIVE);
        strategy.setDepth(depth);

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
        this.pag = GraphTransforms.dagToPag(dag, knowledge, excludeSelectionBias, recursiveDepth);

        if (replicatingGraph) {
            this.pag = new ReplicatingGraph(pag, new LagReplicationPolicy());
        }

        this.initialColliders = noteInitialColliders(pag.getNodes(), pag);

        int round = 0;

        do {
            TetradLogger.getInstance().log("\nRound: " + (++round));
        } while (removeEdgesRecursively(excludeSelectionBias, initialColliders));

        if (superVerbose) {
            TetradLogger.getInstance().log("Doing implied orientation, grabbing unshielded colliders from FciOrient.");
        }

        long stop2 = System.currentTimeMillis();

        // Revert nodes made latent to latent.
        for (Node node : latents) {
            node.setNodeType(NodeType.LATENT);
        }

        List<Edge> spurious = findSpuriousEdges();
        TetradLogger.getInstance().log(spurious.isEmpty()
                ? "\nNo spurious edges remain."
                : "\n" + spurious.size() + " spurious edge(s) remain: " + spurious);

        if (spurious.size() >= 2) {
            tryToModifyGraph(spurious,           "multi-edge", excludeSelectionBias, initialColliders);
        }

        TetradLogger.getInstance().log("\nFCIT finished.");
        TetradLogger.getInstance().log("BOSS/GRaSP time: " + (stop1 - start1) + " ms.");
        TetradLogger.getInstance().log("Collider orientation and _edge removal time: " + (stop2 - start2) + " ms.");
        TetradLogger.getInstance().log("Total time: " + (stop2 - start1) + " ms.");
        TetradLogger.getInstance().log(checkCounter.report());

        CachedIndependenceQueries cache = findCache();
        if (cache != null) {
            TetradLogger.getInstance().log(cache.cacheReport());
        }

        return GraphUtils.replaceNodes(this.pag, nodes);
    }

    private List<Edge> findSpuriousEdges() throws InterruptedException {
        List<Edge> spuriousEdges = new ArrayList<>();

        for (Edge edge : pag.getEdges()) {
            Node m = edge.getNode1();
            Node n = edge.getNode2();

            Set<Node> sepset = sepsets.get(m, n);  // your stored structure
            if (sepset != null && test.checkIndependence(m, n, sepset).isIndependent()) {
                spuriousEdges.add(edge);
            } else {
                sepset = foundSepsets.get(Set.of(m, n));

                if (sepset != null && test.checkIndependence(m, n, sepset).isIndependent()) {
                    spuriousEdges.add(edge);
                }
            }
        }

        return spuriousEdges;
    }

    private LegVerdict legVerdict(Node m, Node n, long deadlineMs, Map<Set<Node>, LegVerdict> cache)
            throws InterruptedException {
        Edge edge = pag.getEdge(m, n);
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
                pag, m, n, Set.of(), Set.of(), recursiveDepth, depth, rbRadius, 1, true,
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
    private boolean removeEdgesRecursively(boolean excludeSelectionBias, Set<Triple> unshieldedTriples) {

        // This version does parallel lookahead, so that the only time graph rebuilding is done is when
        // edge removals are attempted.

        boolean changedThisSweep = false;

        // Ordered snapshot of the edges for this sweep. `from` is the scan position;
        // we never go back before it, so each edge is searched at most once per sweep.
        List<Edge> edgeList = new ArrayList<>(this.pag.getEdges());
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
                                if (!this.pag.isAdjacentTo(x, y)) return null;
                                if (sepsets.get(x, y) != null) return null;
                                if (!(knowledge == null || !Edges.isDirectedEdge(e)
                                        || !knowledge.isForbidden(x.getName(), y.getName()))) return null;

                                try {
                                    IndependenceCheck check = findIndependenceCheckRecursive(e);
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
                break;  // no removable edge in the tail — sweep complete
            }

            RemovalHit h = hit.get();
            Node x = h.edge.getNode1();
            Node y = h.edge.getNode2();

            // Commit against the live PAG using the sepset found during the search —
            // no re-search needed, since the winner was searched against the current PAG.
            boolean didChange = tryToModifyGraph(x, y, h.cond, "recursive",
                    excludeSelectionBias, unshieldedTriples);

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

    private IndependenceCheck findIndependenceCheckRecursive(Edge edge) throws InterruptedException {
        final Node x = edge.getNode1();
        final Node y = edge.getNode2();

        Set<Node> known = sepsets.get(x, y);
        if (known != null) {
            return new IndependenceCheck(edge, known);
        }

        // Reuse a separator already found for this pair in an earlier sweep. The
        // independence is a data fact, invariant across rounds, so re-searching the
        // (evolved) PAG would only risk returning a *different* valid set — which is
        // exactly the cross-round inconsistency. tryToModifyGraph still judges PAG
        // legality; if it reverts, the edge is retried next round with the same set.
        Set<Node> cached = foundSepsets.get(Set.of(x, y));
        if (cached != null) {
            return new IndependenceCheck(edge, cached);
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
                pag, x, y, Set.of(), Set.of(), recursiveDepth, depth, rbRadius, 1, true,
                deadline);

        Set<Node> nfCandSet = new LinkedHashSet<>();
        if (!b0result.indeterminate() && b0result.blockingSet() != null) {
            for (Node v : b0result.blockingSet()) {
                // Only ambiguous nodes — those with at least one circle endpoint
                if (pag.getAdjacentNodes(v).stream().anyMatch(
                        w -> pag.getEndpoint(v, w) == Endpoint.CIRCLE
                                || pag.getEndpoint(w, v) == Endpoint.CIRCLE)) {
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
            if (!this.pag.isAdjacentTo(x, y)) break; // edge already removed upstream

            Set<Node> notFollowed = GraphUtils.asSet(nfChoice, nfCand);
            RecursiveBlocking.BlockingResult result = null;

            if (this.depth < 0) {
                result = RecursiveBlocking.blockPathsRecursively(
                        pag, x, y, Set.of(), notFollowed, recursiveDepth, depth, rbRadius, 1, true,
                        deadline);

            } else {
                int depth = 0;
                int maxDepth = this.depth;

                do {
                    depth++;

                    if (depth > maxDepth) break;

                    result = RecursiveBlocking.blockPathsRecursively(
                            pag, x, y, Set.of(), notFollowed, recursiveDepth, depth, rbRadius, 1, true,
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

            List<Node> common = this.pag.getAdjacentNodes(x);
            common.retainAll(this.pag.getAdjacentNodes(y));

            List<Node> definitelyRemove = new ArrayList<>();
            for (Node c : common) {
                if (this.pag.isDefCollider(x, c, y)) {
                    definitelyRemove.add(c);
                }
            }

            List<Node> removalCandidates = new ArrayList<>(common);
            removalCandidates.removeAll(definitelyRemove);

            SublistGenerator cGen = new SublistGenerator(removalCandidates.size(), removalCandidates.size());
            int[] cChoice;
            while ((cChoice = cGen.next()) != null) {
                if (System.currentTimeMillis() > deadline) return null; // per-edge budget exhausted
                if (!this.pag.isAdjacentTo(x, y)) break;

                Set<Node> S = new LinkedHashSet<>(B);
                Set<Node> C = GraphUtils.asSet(cChoice, removalCandidates);

                S.removeAll(C);

                if (this.depth != -1 && S.size() > this.depth) continue;

                IndependenceCheck probe = new IndependenceCheck(edge, S);
                checkCounter.increment("findIndependenceCheckRecursive (test executed)");

                IndependenceResult independenceResult = this.test.checkIndependence(x, y, S);
                if (independenceResult.isIndependent()) {
                    foundSepsets.put(Set.of(x, y), S);   // remember the fact, survives revert
                    return probe;
                }
            }
        }

        return null;
    }

    private boolean tryToModifyGraph(Node x, Node y, Set<Node> b, String type,
                                     boolean excludeSelectionBias, Set<Triple> initialColliders) {
        Edge _edge = pag.getEdge(x, y);
        Graph _pag = new EdgeListGraph(pag);

        // MAG of the pre-removal (legal) PAG, so zhangMagFromPag's circle resolution
        // is well defined. Then delete the edge under test.
        Graph _mag = GraphTransforms.zhangMagFromPag(_pag);

        // Un-mutated "before" MAG, used by the Markov gate as the induction-hypothesis
        // oracle: every m-separation it entails already holds in the data.
        Graph magPrev = new EdgeListGraph(_mag);

        _mag.removeEdge(x, y);

        Set<Node> prevSepset = sepsets.get(x, y);
        sepsets.set(x, y, b);

        // Stamp every recorded sepset's colliders onto the MAG we keep (idempotent),
        // so the PAG we carry forward retains those arrowheads and RB sees fewer circles.
        orientSepsetCollidersInMag(_mag, sepsets);

        PagLegalityCheck.LegalMagRet legal =
                PagLegalityCheck.isLegalMag(_mag, new LinkedHashSet<>(selection));

        if (!legal.isLegalMag()) {
            if (verbose) {
                TetradLogger.getInstance().log("\tTried removing " + _edge
                        + ", but it didn't lead to a MAG, sepset = " + b);
                System.out.println("\tReason = " + legal.getReason());
            }
            this.pag = _pag;
            sepsets.set(x, y, prevSepset);
            return false;
        }

        // Markov gate: commit only if the move (from magPrev to _mag) introduces no
        // spurious m-separation. Same revert path as an illegal MAG.
        boolean markovOk;
        try {
            markovOk = markovPreserved(magPrev, _mag);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(ie);
        }

        if (!markovOk) {
            if (verbose) {
                TetradLogger.getInstance().log("\tTried removing " + _edge
                        + ", but it would break Markov (a new m-separation is not in the data), sepset = " + b);
            }
            this.pag = _pag;
            sepsets.set(x, y, prevSepset);
            return false;
        }

        if (verbose) {
            TetradLogger.getInstance().log("Removing " + _edge + ", sepset = " + b);
        }

        // Colliders are baked into _mag, so the PAG you carry forward keeps those
        // arrowheads and RB sees fewer circle endpoints. Pass the real flag, not false.
        this.pag = new MagToPag(_mag).convert(false, excludeSelectionBias);
        return true;
    }

    /**
     * MAG-side analog of adjustForExtraSepsets over every recorded sepset. Idempotent:
     * re-stamping an existing collider is a no-op (the isDefCollider guard), so calling
     * this each commit removes any reliance on prior colliders persisting through the
     * PAG<->MAG round trip. Null sepsets and still-adjacent pairs are skipped.
     */
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

    private boolean tryToModifyGraph(List<Edge> edges, String type,
                                     boolean excludeSelectionBias, Set<Triple> initialColliders) {
        Graph _pag = new EdgeListGraph(pag);
        Graph _mag = GraphTransforms.zhangMagFromPag(_pag);   // one MAG of the current legal PAG

        // Un-mutated "before" MAG for the Markov gate (induction-hypothesis oracle).
        Graph magPrev = new EdgeListGraph(_mag);

        Map<Edge, Set<Node>> prev = new LinkedHashMap<>();    // for clean rollback

        for (Edge edge : edges) {
            Node m = edge.getNode1();
            Node n = edge.getNode2();

            // Prefer a committed separator; fall back to the deadlock-survivor one.
            Set<Node> z = sepsets.get(m, n);
            if (z == null) z = foundSepsets.get(Set.of(m, n));

            prev.put(edge, sepsets.get(m, n));
            sepsets.set(m, n, z);

            _mag.removeEdge(m, n);
        }

        // Stamp all sepset-implied colliders, then judge MAG legality once.
        orientSepsetCollidersInMag(_mag, sepsets);

        PagLegalityCheck.LegalMagRet legal =
                PagLegalityCheck.isLegalMag(_mag, new LinkedHashSet<>(selection));

        if (!legal.isLegalMag()) {
            if (verbose) {
                TetradLogger.getInstance().log("\tTried removing " + edges
                        + " (multi-edge), but it didn't lead to a MAG");
                System.out.println("\tReason = " + legal.getReason());
            }
            this.pag = _pag;
            prev.forEach((e, s) -> sepsets.set(e.getNode1(), e.getNode2(), s));
            return false;
        }

        // Markov gate for the whole batch: the before/after MAGs differ by all the
        // removed edges at once, so one delta check covers the batch.
        boolean markovOk;
        try {
            markovOk = markovPreserved(magPrev, _mag);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(ie);
        }

        if (!markovOk) {
            if (verbose) {
                TetradLogger.getInstance().log("\tTried removing " + edges
                        + " (multi-edge), but it would break Markov");
            }
            this.pag = _pag;
            prev.forEach((e, s) -> sepsets.set(e.getNode1(), e.getNode2(), s));
            return false;
        }

        if (verbose) {
            TetradLogger.getInstance().log("Removing " + edges + " (multi-edge), reached a MAG");
        }

        this.pag = new MagToPag(_mag).convert(false, excludeSelectionBias);
        return true;
    }

    // ===================================================================================
    // Markov-preservation gate.
    //
    // A move from a Markov MAG `magPrev` to `magNew` keeps Markovness iff every
    // m-separation entailed by magNew but not by magPrev is a real CI in the data.
    // We certify this via the ordered local Markov property of magNew: for each vertex
    // v in a topological order, v is independent of its earlier non-pillow vertices
    // given its Markov pillow mb(v). Under composition this vertex-wise statement is
    // equivalent to the pairwise tests v _||_ a | mb(v), and under a compositional
    // graphoid the whole family is equivalent to global Markovness. Constraints already
    // entailed by magPrev are skipped: they hold by the induction hypothesis, so only
    // genuinely new separations cost a data test.
    //
    // Written for MAGs over -> and <-> edges (the excludeSelectionBias regime).
    // ===================================================================================

    private boolean markovPreserved(Graph magPrev, Graph magNew) throws InterruptedException {
        // Oracle m-separation in the previous (Markov-by-hypothesis) MAG.
        MsepTest prevMsep = new MsepTest(magPrev);

        List<Node> order = directedTopologicalOrder(magNew);

        for (int i = 0; i < order.size(); i++) {
            Node v = order.get(i);
            Set<Node> preV = new LinkedHashSet<>(order.subList(0, i + 1)); // {w : w <= v}, includes v
            Set<Node> mb = markovPillow(magNew, v, preV);

            for (Node a : preV) {
                if (a.equals(v)) continue;
                if (mb.contains(a)) continue;   // a is a neighbor of v / already in the pillow

                // Inherited constraint: if mb m-separates (v, a) in magPrev, the
                // independence holds by the induction hypothesis — no data test.
                if (prevMsep.checkIndependence(v, a, mb).isIndependent()) continue;

                // New separation entailed by magNew. It must be a real CI, or this
                // move makes the PAG non-Markov.
                checkCounter.increment("markovPreserved (test executed)");
                if (!test.checkIndependence(v, a, mb).isIndependent()) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Topological order over the directed (->) part of the graph only; bidirected (<->)
     * edges impose no order. A legal MAG is ancestral, so the directed part is acyclic
     * and Kahn's algorithm covers every vertex.
     */
    private static List<Node> directedTopologicalOrder(Graph g) {
        List<Node> nodes = new ArrayList<>(g.getNodes());
        Map<Node, Integer> indeg = new HashMap<>();
        Map<Node, List<Node>> children = new HashMap<>();

        for (Node n : nodes) {
            indeg.put(n, 0);
            children.put(n, new ArrayList<>());
        }

        for (Node child : nodes) {
            for (Node parent : g.getParents(child)) {   // parent -> child
                children.get(parent).add(child);
                indeg.merge(child, 1, Integer::sum);
            }
        }

        Deque<Node> q = new ArrayDeque<>();
        for (Node n : nodes) {
            if (indeg.get(n) == 0) q.add(n);
        }

        List<Node> order = new ArrayList<>(nodes.size());
        while (!q.isEmpty()) {
            Node n = q.poll();
            order.add(n);
            for (Node c : children.get(n)) {
                indeg.merge(c, -1, Integer::sum);
                if (indeg.get(c) == 0) q.add(c);
            }
        }

        // Defensive: a legal MAG should leave none behind. If it does, append them so
        // the gate still inspects every vertex rather than silently skipping some.
        if (order.size() != nodes.size()) {
            for (Node n : nodes) {
                if (!order.contains(n)) order.add(n);
            }
        }

        return order;
    }

    /**
     * Markov pillow of v in magNew, restricted to the "past" preV (vertices up to and
     * including v in the topological order): mb(v) = (dis(v) ∪ pa(dis(v))) \ {v}, where
     * dis(v) is the bidirected-connected component (district) of v within preV. Parents
     * of district members are ancestors, hence already in preV under a topological order.
     */
    private static Set<Node> markovPillow(Graph magNew, Node v, Set<Node> preV) {
        Set<Node> dis = districtWithin(magNew, v, preV);

        Set<Node> mb = new LinkedHashSet<>(dis);
        for (Node d : dis) {
            mb.addAll(magNew.getParents(d));   // parents via directed (->) edges
        }
        mb.remove(v);
        return mb;
    }

    /**
     * District of v within the vertex set preV: the bidirected-connected component
     * reachable from v using only <-> edges whose endpoints both lie in preV.
     */
    private static Set<Node> districtWithin(Graph mag, Node v, Set<Node> preV) {
        Set<Node> dis = new LinkedHashSet<>();
        Deque<Node> stack = new ArrayDeque<>();
        stack.push(v);
        dis.add(v);

        while (!stack.isEmpty()) {
            Node cur = stack.pop();
            for (Node nb : mag.getAdjacentNodes(cur)) {
                if (!preV.contains(nb)) continue;
                if (dis.contains(nb)) continue;

                // Bidirected edge cur <-> nb : arrowheads at both ends.
                if (mag.getEndpoint(cur, nb) == Endpoint.ARROW
                        && mag.getEndpoint(nb, cur) == Endpoint.ARROW) {
                    dis.add(nb);
                    stack.push(nb);
                }
            }
        }

        return dis;
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
     * Sets the radius for RA (Recursive Association) algorithm.
     *
     * @param rbRadius the radius for RA algorithm to be set
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

    private record RemovalHit(int index, Edge edge, Set<Node> cond) {
    }

    private record IndependenceCheck(Edge edge, Set<Node> cond) {
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
