/// ////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //i
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
/// ////////////////////////////////////////////////////////////////////////////
package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.score.GraphScore;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.search.utils.*;
import edu.cmu.tetrad.util.MillisecondTimes;
import edu.cmu.tetrad.util.SublistGenerator;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * The FCI Targeted Testing (FCIT) algorithm implements a search algorithm for learning the structure of a graphical
 * model from observational data with latent variables. The algorithm uses the BOSS or GRaSP algorithm to get an initial
 * CPDAG. Then it uses scoring steps to infer some unshielded colliders in the graph, then finishes with a testing step
 * to remove extra edges and orient more unshielded colliders. Finally, the final FCI orientation is applied to the
 * graph.
 *
 * @author josephramsey
 */
public final class Fcit implements IGraphSearch {
    /**
     * The independence test.
     */
    private final IndependenceTest test;
    /**
     * The score.
     */
    private final Score score;
    /**
     * Represents the current status or condition of the search.
     */
    private State state;
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
    private boolean verbose = false;
    /**
     * True if the local Markov property should be ensured from an initial local Markov graph.
     */
    private boolean ensureMarkov = false;

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
     * FCIT constructor. Initializes a new object of FCIT search algorithm with the given IndependenceTest and Score
     * object.
     * <p>
     * In this constructor, we will use BOSS or GRaSP internally to infer an initial CPDAG and valid order of the
     * variables. This is the default behavior of the FCIT algorithm.
     *
     * @param test  The IndependenceTest object to be used for testing independence between variables.
     * @param score The Score object to be used for scoring DAGs.
     * @throws NullPointerException if the score is null.
     */
    public Fcit(IndependenceTest test, Score score) {
        if (test == null) {
            throw new NullPointerException();
        }

        if (score == null) {
            throw new NullPointerException();
        }

        this.test = test;
        this.score = score;

        test.setVerbose(false);

        if (test instanceof MsepTest) {
            this.startWith = START_WITH.GRASP;
        }
    }

    /**
     * Run the search and return a PAG.
     *
     * @return The PAG.
     * @throws InterruptedException if any
     */
    public Graph search() throws InterruptedException {
        List<Node> nodes;

        if (this.score != null) {
            nodes = new ArrayList<>(this.score.getVariables());
        } else {
            nodes = new ArrayList<>(this.test.getVariables());
        }

        TetradLogger.getInstance().log("===Starting FCIT===");

        this.state = new State();

        R0R4StrategyTestBased strategy = new R0R4StrategyTestBased(test);
        strategy.setSepsetMap(state.getSepsetMap());
        strategy.setVerbose(verbose);
        strategy.setEnsureMarkovHelper(state.ensureMarkovHelper);
        strategy.setBlockingType(R0R4StrategyTestBased.BlockingType.RECURSIVE);

        state.setStrategy(strategy);

        fciOrient = new FciOrient(strategy);
        fciOrient.setVerbose(verbose);
        fciOrient.setParallel(false);
        fciOrient.setKnowledge(knowledge);

        Graph dag;
        List<Node> best;
        long start1 = System.currentTimeMillis();

        if (startWith == START_WITH.BOSS) {

            if (verbose) {
                TetradLogger.getInstance().log("Running BOSS...");
            }

            if (this.score == null) {
                throw new IllegalArgumentException("For BOSS a non-null score is expected.");
            }

            long start = MillisecondTimes.wallTimeMillis();

            PermutationSearch alg = getBossSearch();

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
            throw new IllegalArgumentException("Unknown startWith option: " + startWith);
        }

        if (verbose) {
            TetradLogger.getInstance().log("Best order: " + best);
        }

        long stop1 = System.currentTimeMillis();

        long start2 = System.currentTimeMillis();

        TeyssierScorer scorer = null;

        if (this.score != null) {
            scorer = new TeyssierScorer(test, score);
            scorer.score(best);
            scorer.setKnowledge(knowledge);
            scorer.setUseScore(!(this.score instanceof GraphScore));
            scorer.setUseRaskuttiUhler(this.score instanceof GraphScore);
            scorer.bookmark();
        }

        if (verbose) {
            TetradLogger.getInstance().log("Initializing PAG to PAG of BOSS DAG.");
            TetradLogger.getInstance().log("Initializing scorer with BOSS best order.");
        }

        if (scorer != null) {
            scorer.score(best);
        }

        if (verbose) {
            TetradLogger.getInstance().log("Copying unshielded colliders from CPDAG.");
        }

        // The main procedure.
        state.setPag(GraphTransforms.dagToPag(dag));
        this.initialColliders = noteInitialColliders(best, state.getPag());
        state.setEnsureMarkovHelper(new EnsureMarkov(state.getPag(), test));
        state.getEnsureMarkovHelper().setEnsureMarkov(ensureMarkov);

        state.storeState();

        // Next, we remove the "extra" adjacencies from the graph. We do this differently than in GFCI. There, we
        // look for a sepset for an edge x *-* y from among adj(x) or adj(y), so the problem is exponential one
        // each side. So in a dense graph, this can take a very long time to complete. Here, we look for a sepset
        // for each edge by examining the structure of the current graph and finding a sepset that blocks all
        // paths between x and y. This is a simpler problem and scales better to dense graphs (though not perfectly).
        // New definite discriminating paths may be created, so additional checking needs to be done until the
        // evolving maximally oriented PAG stabilizes. This could be optimized, since only the new definite
        // discriminating paths need to be checked, but for now, we simply analyze the entire graph again until
        // convergence. Note that for checking discriminating paths, the recursive algorithm may not be 100%
        // effective, so we need to supplement this with FCI-style discriminating path checking in case a sepset
        // is not found. This is to accommodate "Puzzle #2."
        removeExtraEdges();

        // Also, to handle "Puzzle #2," we remove incorrect shields for discriminating path colliders on collider
        // paths and then reorient.
        checkUnconditionalIndependence();

        if (verbose) {
            TetradLogger.getInstance().log("Doing implied orientation, grabbing unshielded colliders from FciOrient.");
        }

        long stop2 = System.currentTimeMillis();

        TetradLogger.getInstance().log("FCIT finished.");
        TetradLogger.getInstance().log("BOSS/GRaSP time: " + (stop1 - start1) + " ms.");
        TetradLogger.getInstance().log("Collider orientation and edge removal time: " + (stop2 - start2) + " ms.");
        TetradLogger.getInstance().log("Total time: " + (stop2 - start1) + " ms.");

        return GraphUtils.replaceNodes(state.getPag(), nodes);
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
        Boss subAlg = new Boss(this.score);
        subAlg.setUseBes(this.useBes);
        subAlg.setNumStarts(this.numStarts);
        subAlg.setNumThreads(Runtime.getRuntime().availableProcessors());
        subAlg.setVerbose(verbose);
        PermutationSearch alg = new PermutationSearch(subAlg);
        alg.setKnowledge(this.knowledge);
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
        grasp.setAllowInternalRandomness(true);
        grasp.setVerbose(false);

        grasp.setNumStarts(numStarts);
        grasp.setKnowledge(this.knowledge);
        return grasp;
    }


    /**
     * Identifies and notes known unshielded colliders from the provided CPDAG (Completed Partially Directed Acyclic
     * Graph) by looking at its implied structure and transferring relevant colliders to the current PAG (Partial
     * Ancestral Graph). This process is justified in the GFCI (Generalized Fast Causal Inference) algorithm, as
     * described in the referenced research.
     *
     * @param best A list of nodes representing the best-known nodes to be evaluated during the collider identification
     *             process.
     * @param pag  The CPDAG from which known colliders are identified and extracted.
     * @return A set of triples representing the known colliders identified in the provided CPDAG.
     */
    private Set<Triple> noteInitialColliders(List<Node> best, Graph pag) {
        Set<Triple> initialColliders = new HashSet<>();

        // We're looking for unshielded colliders in these next steps that we can detect from the CPDAG.
        // We do this by looking at the structure of the CPDAG implied by the BOSS graph and noting all
        // colliders from the BOSS graph into the estimated PAG. This step is justified in the
        // GFCI algorithm; see Ogarrio, J. M., Spirtes, P., & Ramsey, J. (2016, August). A hybrid causal search
        // algorithm for latent variable models. In Conference on probabilistic graphical models (pp. 368-379).
        // PMLR.
        for (Node b : best) {
            var adj = state.getPag().getAdjacentNodes(b);

            for (int i = 0; i < adj.size(); i++) {
                for (int j = i + 1; j < adj.size(); j++) {
                    Node x = adj.get(i);
                    Node y = adj.get(j);

                    if (GraphUtils.distinct(x, b, y)) {
                        if (GraphUtils.colliderAllowed(state.getPag(), x, b, y, knowledge)) {
                            if (pag.isDefCollider(x, b, y)) {// && !pag.isAdjacentTo(x, y)) {
                                initialColliders.add(new Triple(x, b, y));

                                if (verbose) {
                                    TetradLogger.getInstance().log("Copied " + x + " *-> " + b + " <-* " + y + " from initial PAG to PAG.");
                                }
                            }
                        }
                    }
                }
            }
        }

        return initialColliders;
    }

    /**
     * Updates and refines the structure of the Partial Ancestral Graph (PAG) based on current configurations and
     * knowledge, while maintaining the validity of the PAG. This method is fundamental to the iterative refinement
     * process in the FCIT algorithm.
     * <p>
     * The method performs the following steps:
     * <p>
     * (a) Reorients the current PAG using circular structures if applicable. (b) Transfers known colliders identified
     * from the provided CPDAG into the PAG based on given knowledge constraints. (c) Adjusts separation sets to account
     * for extra independence information. (d) Applies background knowledge and performs structure orientation using the
     * implemented orientation strategies, including R4-strategy.
     * <p>
     * If the updated PAG violates the legality constraints, restores the state to the last valid configuration;
     * otherwise, checkpoints the current state for future reference.
     */
    private void refreshGraph(String message) {
        GraphUtils.reorientWithCircles(state.getPag(), verbose);
        GraphUtils.recallInitialColliders(state.getPag(), initialColliders, knowledge);
        adjustForExtraSepsets();
        fciOrient.fciOrientbk(knowledge, state.getPag(), state.getPag().getNodes());
        fciOrient.setUseR4(true);
        fciOrient.finalOrientation(state.getPag());

        // Don't need to check legal PAG here; can limit the check to these two conditions, as removing an edge
        // cannot cause new cycles or almost-cycles to be formed.
        if (!state.getPag().paths().isMaximal() || edgeMarkingDiscrepancy()) {
            System.out.println("Restored: " + message);
            state.restoreState();
        } else {
            System.out.println("Good: " + message);
            state.storeState();
        }
    }

    private boolean edgeMarkingDiscrepancy() {
        Graph mag = GraphTransforms.zhangMagFromPag(state.getPag());
        Graph pag2 = GraphTransforms.dagToPag(mag);
        return !state.getPag().equals(pag2);
    }

    /**
     * Refines the structure of the Partial Ancestral Graph (PAG) by adjusting separation sets based on additional
     * independence evidence and ensuring consistency with known independence and causality constraints. This method
     * identifies and orients specific edges in the PAG to maintain its validity.
     * <p>
     * The method performs the following steps: (a) Iterates over all edges in the separation set map's key set. (b) For
     * each edge, identifies adjacent nodes in the PAG and finds their common neighbors. (c) Removes adjacency between
     * the nodes if applicable and logs the operation if verbose mode is enabled. (d) Examines each common neighbor,
     * checking whether it is part of the separation set for the given nodes. If it is not part of the separation set
     * and does not create a forbidden collider, the endpoints of the edge between the common neighbor and the adjacent
     * nodes are adjusted to a directed orientation. (e) Logs oriented relationships in verbose mode.
     * <p>
     * This adjustment ensures proper handling of induced dependencies and maintains the correctness of the causal
     * structure represented by the PAG. The orientation of edges follows the rules
     */
    private void adjustForExtraSepsets() {
        state.getSepsetMap().keySet().forEach(edge -> {
            List<Node> arr = new ArrayList<>(edge);

            Node x = arr.get(0);
            Node y = arr.get(1);

            List<Node> common = state.getPag().getAdjacentNodes(x);
            common.retainAll(state.getPag().getAdjacentNodes(y));

            if (verbose) {
                TetradLogger.getInstance().log("Removed adjacency " + x + " *-* " + y + " from PAG.");
            }

            for (Node node : common) {
                if (!state.getSepsetMap().get(x, y).contains(node)) {
                    if (!state.getPag().isDefCollider(x, node, y)) {
                        state.getPag().setEndpoint(x, node, Endpoint.ARROW);
                        state.getPag().setEndpoint(y, node, Endpoint.ARROW);

                        if (verbose) {
                            TetradLogger.getInstance().log("Oriented " + x + " *-> " + node + " <-* " + y + " in PAG.");
                        }
                    }
                }
            }
        });
    }

    /**
     * Examines all edges in the PAG for unconditional independence based on the current separation set map and Markov
     * independence checks. If two variables are found to be unconditionally independent, the edge connecting them is
     * removed, and the separation set map is updated accordingly.
     * <p>
     * The method operates by iterating over all edges in the PAG and performing the following: (a) Identifies the two
     * nodes connected by the edge. (b) Checks if there exists a separation set for the nodes in the current separation
     * set map. If a separation set exists, it skips further processing for that edge. (c) Identifies common neighbors
     * of the two nodes connected by the edge and determines if they form a non-collider structure. If all common
     * neighbors create colliders, the edge is skipped. (d) Checks unconditional independence between the two nodes
     * using the ensureMarkovHelper's Markov independence method. If independence is confirmed: (d.1) Logs the operation
     * if verbose mode is enabled. (d.2) Updates the separation set map for the nodes to an empty set. (d.3) Removes the
     * edge from the PAG. (e) Handles any `InterruptedException` thrown during the Markov independence check.
     * <p>
     * If verbose mode is enabled, relevant logging information is captured using the `TetradLogger` to provide detailed
     * insights into the operations performed.
     * <p>
     * This method helps refine the PAG by ensuring its representation aligns with the detected unconditional
     * independencies and the causal structure implied by the data.
     */
    private void checkUnconditionalIndependence() {
        if (verbose) {
            TetradLogger.getInstance().log("Removing extra edges from discriminating paths.");
        }

        state.getPag().getEdges().forEach(edge -> {
            Node x = edge.getNode1();
            Node y = edge.getNode2();

            if (state.getSepsetMap().get(x, y) != null) {
                return;
            }

            List<Node> common = state.getPag().getAdjacentNodes(x);
            common.retainAll(state.getPag().getAdjacentNodes(y));

            boolean found = false;

            for (Node node : common) {
                if (!state.getPag().isDefCollider(x, node, y)) {
                    found = true;
                }
            }

            if (!found) {
                return;
            }

            try {
                if (state.getEnsureMarkovHelper().markovIndependence(x, y, Set.of())) {
                    if (verbose) {
                        TetradLogger.getInstance().log("Marking " + edge + " for removal because of unconditional independence.");
                    }

                    state.getSepsetMap().set(x, y, Set.of());
                    state.getPag().removeEdge(x, y);
                    refreshGraph("Unconditional independence");
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Removes extra edges from a Partial Ancestral Graph (PAG) by analyzing discriminating paths that could not be
     * oriented using final orientation rules and applying specific conditions to validate the existence of those edges.
     * This method is part of the causal discovery process.
     * <p>
     * The method performs several key steps:
     * <p>
     * 1. Identifies discriminating paths in the PAG that are candidates for edge removals. 2. Creates a map of
     * discriminating paths organized by their corresponding edge pairs for efficient lookup during subsequent
     * processing. 3. Iterates over all edges in the PAG to evaluate whether they can be removed based on conditions
     * derived from discriminating paths and the causal implications of their removal. 4. Considers subsets of nodes for
     * which paths may not be followed to evaluate blocking conditions recursively and determine m-separation. 5.
     * Applies independence tests to finalize edge removals when conditions are met.
     * <p>
     * The method logs the intermediate steps and decisions if verbose logging is enabled. This includes information on
     * the discriminating paths, potential collider pairs, blocking sets, and the specific edges being considered for
     * removal.
     * <p>
     * The logic ensures that edges are removed only if doing so maintains the Markov property and aligns with the
     * causal structure represented by the PAG.
     * <p>
     * Exceptions such as `InterruptedException` are caught and wrapped in a runtime exception to ensure proper flow of
     * execution for asynchronous tasks.
     */
    private void removeExtraEdges() {
        if (verbose) {
            TetradLogger.getInstance().log("Removing extra edges from discriminating paths.");
        }

        // The final orientation rules were applied just before this step, so this should list only
        // discriminating paths that could not be oriented by them...
        Set<DiscriminatingPath> discriminatingPaths = FciOrient.listDiscriminatingPaths(state.getPag(),
                -1, false);
        Map<Set<Node>, Set<DiscriminatingPath>> pathsByEdge = new HashMap<>();
        for (DiscriminatingPath path : discriminatingPaths) {
            Node x = path.getX();
            Node y = path.getY();

            pathsByEdge.computeIfAbsent(Set.of(x, y), k -> new HashSet<>());
            pathsByEdge.get(Set.of(x, y)).add(path);
        }

        // Now test the specific extra condition where DDPs colliders would have been oriented had an edge not been
        // there in this graph.
        for (Edge edge : state.getPag().getEdges()) {
            if (verbose) {
                TetradLogger.getInstance().log("Considering removing edge " + edge);
            }

            Node x = edge.getNode1();
            Node y = edge.getNode2();

            if (state.getSepsetMap().get(x, y) != null) {
                if (verbose) {
                    TetradLogger.getInstance().log("Marking " + edge + " for removal because of potential DDP collider orientations.");
                }

                state.getPag().removeEdge(x, y);
                refreshGraph("Potential DDP (recall sepset)");
                return;
            }

            List<Node> common = state.getPag().getAdjacentNodes(x);
            common.retainAll(state.getPag().getAdjacentNodes(y));

            Set<DiscriminatingPath> paths = pathsByEdge.get(Set.of(x, y));
            paths = paths == null ? Set.of() : paths;
            Set<Node> perhapsNotFollowed = new HashSet<>();

            if (verbose) {
                TetradLogger.getInstance().log("Discriminating paths for " + x + " and " + y + " are " + paths);
            }

            // Don't repeat the same independence test twice for this edge x *-* y.
            Set<Set<Node>> S = new HashSet<>();

            for (DiscriminatingPath path : paths) {
                if (state.getPag().getEndpoint(path.getY(), path.getV()) == Endpoint.CIRCLE) {
                    perhapsNotFollowed.add(path.getV());
                }
            }

            List<Node> E = new ArrayList<>(perhapsNotFollowed);

            // Generate subsets and check blocking paths
            SublistGenerator gen = new SublistGenerator(E.size(), E.size());
            int[] choice;

            while ((choice = gen.next()) != null) {
                Set<Node> notFollowed = GraphUtils.asSet(choice, E);

                // Instead of newSingleThreadExecutor(), we use the shared 'executor'
                Pair<Set<Node>, Boolean> B;
                try {
                    B = SepsetFinder.blockPathsRecursively(state.getPag(), x, y, Set.of(), notFollowed, -1);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }

                if (verbose && !notFollowed.isEmpty()) {
                    TetradLogger.getInstance().log("Not followed set = " + notFollowed + " b set = " + B.getLeft());
                }

                // b will be null if the search did not conclude with a set known to either m-separate
                // or not m-separate x and y.
                if (B == null) {
                    continue;
                }

                if (!B.getRight()) {
                    continue;
                }

                Set<Node> b = B.getLeft();

                SublistGenerator gen2 = new SublistGenerator(common.size(), common.size());
                int[] choice2;

                while ((choice2 = gen2.next()) != null) {
                    if (!state.getPag().isAdjacentTo(x, y)) {
                        break;
                    }

                    Set<Node> c = GraphUtils.asSet(choice2, common);

                    for (Node n : c) {
                        if (!state.getPag().isDefCollider(x, n, y)) {
                            b.remove(n);
                        }
                    }

//                    b.removeAll(c);
                    if (S.contains(b)) continue;
                    S.add(new HashSet<>(b));

                    try {
                        if (state.getEnsureMarkovHelper().markovIndependence(x, y, b)) {
                            if (verbose) {
                                TetradLogger.getInstance().log("Marking " + edge + " for removal because of potential DDP collider orientations.");
                            }

                            state.getSepsetMap().set(x, y, b);
                            state.getPag().removeEdge(x, y);
                            refreshGraph("x _||_ y | b \\ c (new sepset)");
                        }
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
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
     * @param verbose true to enable verbose mode, false to disable it
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
     * Sets the value indicating whether the process should ensure Markov property.
     *
     * @param ensureMarkov a boolean value, true to ensure Markov property, false otherwise.
     */
    public void setEnsureMarkov(boolean ensureMarkov) {
        this.ensureMarkov = ensureMarkov;
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
        INITIAL_GRAPH
    }

    /**
     * Represents the state of the algorithm during its execution.
     * <p>
     * This class is used to store and manage various components of the search process, including graphs, separation
     * sets, and helper objects that enforce Markov properties. It provides methods for saving, restoring, and accessing
     * these elements, enabling the algorithm to checkpoint and rollback its progress as needed.
     */
    private static class State {
        /**
         * Represents the R0R4 test-based strategy used in the search algorithm.
         * <p>
         * This variable stores an instance of the {@code R0R4StrategyTestBased} class, which is employed to perform
         * specific operations and decision-making during the execution of the causal discovery algorithm. The strategy
         * defines how the algorithm conducts certain tests and transitions, contributing to the overall search
         * process.
         * <p>
         * It is initially set to {@code null} and is expected to be explicitly initialized or configured before being
         * utilized in the algorithm. The strategy can be updated or replaced as necessary to
         */
        private R0R4StrategyTestBased strategy = null;
        /**
         * Represents the Partial Ancestral Graph (PAG) currently being learned or maintained by the algorithm.
         * <p>
         * The PAG is a graph structure used in causal discovery to capture causal relationships between variables while
         * accounting for potential latent variables. Initially set to null, this variable is updated during the
         * execution of the algorithm as the relationships are inferred. It serves as the central representation of the
         * model's learned causal structure.
         */
        private Graph pag = null;
        /**
         * Represents the most recently stored Partial Ancestral Graph (PAG) within the state of the algorithm. This
         * graph captures causal structures identified during the most recent execution or checkpoint.
         * <p>
         * The `lastPag` variable is utilized during state restoration to revert to the previously inferred PAG. It is
         * initialized to null and updated whenever a new checkpoint is created by the algorithm.
         * <p>
         * This variable plays a critical role in enabling rollback functionality and maintaining the integrity
         */
        private Graph lastPag = null;
        /**
         * A map that stores separator sets (sepsets) for pairs of nodes within a graph. The sepsetMap is used to record
         * and retrieve separator sets, which represent conditional independence relationships identified during the
         * causal structure learning process. It plays a critical role in determining the graph's structure by encoding
         * these constraints into the algorithm's state.
         */
        private SepsetMap sepsetMap = new SepsetMap();
        /**
         * Stores the most recently computed set of separation sets (sepsets) in the graph. This variable is used to
         * encode and maintain the separating sets identified during causal discovery, which are essential for refining
         * and validating the graph's structure.
         * <p>
         * The separation sets represented by this map are linked to pairs of nodes in the graph, indicating the
         * conditional independence relationships that were last computed or updated. Preserving this information is
         * critical for checkpointing and restoring the algorithm's state during its execution.
         * <p>
         * Initialized to null, the value of this variable is updated dynamically as the search process progresses.
         */
        private SepsetMap lastSepsetMap = null;
        /**
         * An instance of the EnsureMarkov class used to assist in maintaining the Markov property during the execution
         * of the algorithm. This variable is primarily leveraged to enforce the necessary constraints that ensure the
         * resulting graph adheres to the Markov condition.
         * <p>
         * During the algorithm's execution, this helper may be initialized, updated, or restored to preserve the
         * required state for maintaining causal consistency. It is also utilized for facilitating operations that
         * demand adherence to the Markov property across different steps of the algorithm.
         */
        private EnsureMarkov ensureMarkovHelper = null;
        /**
         * A reference to the last instance of the EnsureMarkov helper used during the algorithm's execution. This
         * variable facilitates the management and reuse of the EnsureMarkov helper object, which is designed to
         * preserve and enforce the Markov property during the search process. It is updated to reflect the most recent
         * state of the EnsureMarkov helper, ensuring consistency throughout the algorithm's iterations or steps.
         */
        private EnsureMarkov lastEnsureMarkovHelper = null;

        /**
         * Default constructor for the State class.
         * <p>
         * This constructor initializes a new instance of the State class without setting any specific field values. It
         * is primarily used to create a State object that can later be configured using its various methods.
         */
        public State() {
        }

        /**
         * Captures and stores the current state of several key fields in the algorithm for later restoration. This
         * method creates copies of the current state of `pag`, known colliders, separation set map, and the
         * `ensureMarkovHelper` instance. These copies are saved into respective fields to preserve the state at that
         * moment in time.
         * <p>
         * This operation is useful for checkpointing the algorithm's progress and facilitates rollback or review of
         * previous states during the algorithm’s execution.
         */
        private void storeState() {
            this.lastPag = new EdgeListGraph(this.pag);
            this.lastSepsetMap = new SepsetMap(sepsetMap);
            this.lastEnsureMarkovHelper = new EnsureMarkov(ensureMarkovHelper);
        }

        /**
         * Restores the previously saved state of various fields in the algorithm.
         * <p>
         * This method reinitializes the following components using their last saved states: (a) The PAG graph is
         * restored to its previous state using the last known PAG. (b) The set of known CPDAG colliders is updated
         * using the last recorded colliders. (c) The separation set map is restored from its last saved version. (d)
         * The `ensureMarkovHelper` object is reset to its previous state.
         * <p>
         * It also updates the search strategy with the restored separation set map, ensuring consistency with the
         * previous checkpoint. This operation enables the algorithm to roll back or resume from a prior state if
         * needed.
         */
        private void restoreState() {
            this.pag = new EdgeListGraph(this.lastPag);
            this.sepsetMap = new SepsetMap(lastSepsetMap);
            this.ensureMarkovHelper = new EnsureMarkov(lastEnsureMarkovHelper);
            this.strategy.setSepsetMap(sepsetMap);
        }

        /**
         * Sets the R0R4 strategy test-based instance that is used for the search algorithm.
         *
         * @param strategy the R0R4 strategy test-based instance to be used in the algorithm.
         */
        public void setStrategy(R0R4StrategyTestBased strategy) {
            this.strategy = strategy;
        }

        /**
         * Represents the learned Partial Ancestral Graph (PAG) in the FCIT search algorithm. The PAG is a graphical
         * representation that encodes causal relationships among variables that are consistent with the observed data
         * and assumes the presence of latent confounders but no selection bias. It is initialized to null and is
         * populated as the search algorithm progresses.
         */
        public Graph getPag() {
            return pag;
        }

        /**
         * Sets the Partial Ancestral Graph (PAG) to be used for representing causal relationships among variables in
         * the algorithm.
         *
         * @param pag the PAG graph to be assigned to the current state. This graph encodes causal structures consistent
         *            with the observed data, considering latent variables while excluding selection bias.
         */
        public void setPag(Graph pag) {
            this.pag = pag;
        }

        /**
         * A map that maintains separator sets (sepsets) for pairs of nodes in a graph. The separator sets are used to
         * represent conditional independence relationships identified during the graph search process. This variable is
         * crucial in storing and retrieving separating sets for specific node pairs and contributes to determining the
         * structure of the graph by encoding constraints.
         */
        public SepsetMap getSepsetMap() {
            return sepsetMap;
        }

        /**
         * A helper class to help preserve Markov.
         */
        public EnsureMarkov getEnsureMarkovHelper() {
            return ensureMarkovHelper;
        }

        /**
         * Sets the instance of the EnsureMarkov helper to be used for managing and enforcing the Markov property during
         * the algorithm's execution.
         *
         * @param ensureMarkov the EnsureMarkov instance to be associated with the current state.
         */
        public void setEnsureMarkovHelper(EnsureMarkov ensureMarkov) {
            this.ensureMarkovHelper = ensureMarkov;
        }
    }
}
