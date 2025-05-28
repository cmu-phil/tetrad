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
     * A helper class to help preserve Markov.
     */
    private EnsureMarkov ensureMarkovHelper = null;
    /**
     * Represents the learned Partial Ancestral Graph (PAG) in the FCIT search algorithm. The PAG is a graphical
     * representation that encodes causal relationships among variables that are consistent with the observed data and
     * assumes the presence of latent confounders but no selection bias. It is initialized to null and is populated as
     * the search algorithm progresses.
     */
    private Graph pag = null;
    /**
     * Specifies the orientation rules or procedures used in the FCIT algorithm for orienting edges in a PAG (Partial
     * Ancestral Graph). This variable determines how unshielded colliders, discriminating paths, and other structural
     * elements of the PAG are identified and processed during the search. The orientation strategy implemented in this
     * variable can influence the causal interpretation of the resulting graph.
     */
    private FciOrient fciOrient = null;
    /**
     * Represents the most recently calculated Partially Oriented Acyclic Graph (PAG) during the execution of the FCIT
     * algorithm. This graph contains the causal relationships inferred up to the latest step.
     * <p>
     * Initially set to null, this variable is updated upon completion of a search to store the final or intermediate
     * result of the PAG generated by the algorithm.
     */
    private Graph lastPag = null;
    /**
     * A mapping of edges to sets of nodes, representing extra separating sets utilized in the search process. This
     * variable stores information about the separating sets that were last computed or updated during the algorithm's
     * execution.
     * <p>
     * The key represents an {@link Edge}, while the value is a {@link Set} of {@link Node} objects that form the
     * separating set associated with that edge. These separating sets are used to refine the graph structure during
     * causal discovery.
     */
    private SepsetMap lastSepsetMap = null;
    /**
     * A set that keeps track of the previously identified colliders during the execution of the FCIT search algorithm.
     * Colliders are unshielded triples of nodes (X, Y, Z) such that X and Z are not adjacent, but are both parents of
     * Y.
     * <p>
     * This variable plays a key role in maintaining state about the structure of the underlying graph and assists in
     * ensuring that the search algorithm respects these previously identified relationships.
     */
    private Set<Triple> lasCpdagColliders = null;
    /**
     * A reference to the last instance of the EnsureMarkov helper used during the search process. This variable is
     * utilized to manage and reuse the helper object across multiple steps or passes of the algorithm to enforce the
     * Markov property when required.
     */
    private EnsureMarkov lastEnsureMarkovHelper = null;
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
    private Set<Triple> cpdagColliders;
    /**
     * A map that maintains separator sets (sepsets) for pairs of nodes in a graph. The separator sets are used to
     * represent conditional independence relationships identified during the graph search process. This variable is
     * crucial in storing and retrieving separating sets for specific node pairs and contributes to determining the
     * structure of the graph by encoding constraints.
     */
    private SepsetMap sepsetMap = new SepsetMap();
    /**
     * Represents the strategy used for testing and validating during the FCIT (Fast Causal Inference Technique)
     * algorithm process.
     * <p>
     * This variable defines the specific algorithmic approach or methodology (e.g., test-based strategies) applied in
     * the initial stage of causal inference or graph construction. It integrates with the IndependenceTest and scoring
     * mechanisms to ensure the reliability and effectiveness of the search process.
     * <p>
     * The choice and configuration of the strategy can influence the behavior and results of the FCIT algorithm,
     * including its efficiency, accuracy, and compliance with causal discovery objectives.
     */
    private R0R4StrategyTestBased strategy;

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

        strategy = new R0R4StrategyTestBased(test);
        strategy.setSepsetMap(sepsetMap);
        strategy.setVerbose(verbose);
        strategy.setEnsureMarkovHelper(ensureMarkovHelper);
        strategy.setBlockingType(R0R4StrategyTestBased.BlockingType.RECURSIVE);

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
            throw new IllegalArgumentException("Unknown startWith algorithm: " + startWith);
        }

        if (verbose) {
            TetradLogger.getInstance().log("Best order: " + best);
        }

        long stop1 = System.currentTimeMillis();

        long start2 = System.currentTimeMillis();

        Graph cpdag = GraphTransforms.dagToCpdag(dag);

        TeyssierScorer scorer = null;

        if (this.score != null) {
            scorer = new TeyssierScorer(test, score);
            scorer.score(best);
            scorer.setKnowledge(knowledge);
            scorer.setUseScore(!(this.score instanceof GraphScore));
            scorer.setUseRaskuttiUhler(this.score instanceof GraphScore);
            scorer.bookmark();
        }

        this.pag = new EdgeListGraph(cpdag);

        if (verbose) {
            TetradLogger.getInstance().log("Initializing PAG to PAG of BOSS DAG.");
            TetradLogger.getInstance().log("Initializing scorer with BOSS best order.");
        }

        if (verbose) {
            TetradLogger.getInstance().log("Collider orientation and edge removal.");
        }

        // The main procedure.
        if (scorer != null) {
            scorer.score(best);
        }

        if (verbose) {
            TetradLogger.getInstance().log("Copying unshielded colliders from CPDAG.");
        }

        this.pag = GraphTransforms.dagToPag(dag);
        this.cpdagColliders = noteKnownCollidersFromCpdag(best, pag);

        ensureMarkovHelper = new EnsureMarkov(pag, test);
        ensureMarkovHelper.setEnsureMarkov(ensureMarkov);

        storeState();

        // Next, we remove the "extra" adjacencies from the graph. We do this differently than in GFCI. There, we
        // look for a sepset for an edge x *-* y from among adj(x) or adj(y), so the problem is exponential one
        // each side. So in a dense graph, this can take a very long time to complete. Here, we look for a sepset
        // for each edge by examining the structure of the current graph and finding a sepset that blocks all
        // paths between x and y. This is a simpler problem and scales better to dense graphs (though not perfectly).
        // New definite discriminating paths may be created, so additional checking needs to be done until the
        // evolving maximally oriented PAG stabilizes. This could be optimized, since only the new definite
        // discriminating paths need to be checked, but for now, we simply analyze the entire graph again until
        // convergence.
        removeExtraEdgesDdp();
        refreshGraph();

//        Graph _pag;

//        do {
//            _pag = new EdgeListGraph(pag);
//            removeExtraEdgesDdp();
//            refreshGraph();
//            break;
//        } while (!_pag.equals(pag));

        Graph _pag = new EdgeListGraph(pag);
        checkUnconditionalIndependence();

        removeExtraEdgesDdp();
        refreshGraph();

//        if (!_pag.equals(pag)) {
//            do {
//                _pag = new EdgeListGraph(pag);
//                removeExtraEdgesDdp();
//                refreshGraph();
//                break;
//            } while (!_pag.equals(pag));
//        }

        if (verbose) {
            TetradLogger.getInstance().log("Doing implied orientation, grabbing unshielded colliders from FciOrient.");
        }

        long stop2 = System.currentTimeMillis();

        TetradLogger.getInstance().log("FCIT finished.");
        TetradLogger.getInstance().log("BOSS/GRaSP time: " + (stop1 - start1) + " ms.");
        TetradLogger.getInstance().log("Collider orientation and edge removal time: " + (stop2 - start2) + " ms.");
        TetradLogger.getInstance().log("Total time: " + (stop2 - start1) + " ms.");

        return GraphUtils.replaceNodes(pag, nodes);
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
     * Captures and stores the current state of several key fields in the algorithm for later restoration. This method
     * creates copies of the current state of `pag`, known colliders, separation set map, and the `ensureMarkovHelper`
     * instance. These copies are saved into respective fields to preserve the state at that moment in time.
     * <p>
     * This operation is useful for checkpointing the algorithm's progress and facilitates rollback or review of
     * previous states during the algorithm’s execution.
     */
    private void storeState() {
        this.lastPag = new EdgeListGraph(this.pag);
        this.lasCpdagColliders = new HashSet<>(this.cpdagColliders);
        this.lastSepsetMap = new SepsetMap(sepsetMap);
        this.lastEnsureMarkovHelper = new EnsureMarkov(ensureMarkovHelper);
    }

    /**
     * Restores the previously saved state of various fields in the algorithm.
     * <p>
     * This method reinitializes the following components using their last saved states: (a) The PAG graph is restored
     * to its previous state using the last known PAG. (b) The set of known CPDAG colliders is updated using the last
     * recorded colliders. (c) The separation set map is restored from its last saved version. (d) The
     * `ensureMarkovHelper` object is reset to its previous state.
     * <p>
     * It also updates the search strategy with the restored separation set map, ensuring consistency with the previous
     * checkpoint. This operation enables the algorithm to roll back or resume from a prior state if needed.
     */
    private void restoreState() {
        this.pag = new EdgeListGraph(this.lastPag);
        this.cpdagColliders = new HashSet<>(this.lasCpdagColliders);
        this.sepsetMap = new SepsetMap(lastSepsetMap);
        this.ensureMarkovHelper = new EnsureMarkov(lastEnsureMarkovHelper);
        this.strategy.setSepsetMap(sepsetMap);
    }

    /**
     * Identifies and notes known unshielded colliders from the provided CPDAG (Completed Partially Directed Acyclic
     * Graph) by looking at its implied structure and transferring relevant colliders to the current PAG (Partial
     * Ancestral Graph). This process is justified in the GFCI (Generalized Fast Causal Inference) algorithm, as
     * described in the referenced research.
     *
     * @param best  A list of nodes representing the best-known nodes to be evaluated during the collider identification
     *              process.
     * @param cpdag The CPDAG from which known colliders are identified and extracted.
     * @return A set of triples representing the known colliders identified in the provided CPDAG.
     */
    private Set<Triple> noteKnownCollidersFromCpdag(List<Node> best, Graph cpdag) {
        Set<Triple> cpdagColliders = new HashSet<>();

        // We're looking for unshielded colliders in these next steps that we can detect from the CPDAG.
        // We do this by looking at the structure of the CPDAG implied by the BOSS graph and noting all
        // colliders from the BOSS graph into the estimated PAG. This step is justified in the
        // GFCI algorithm; see Ogarrio, J. M., Spirtes, P., & Ramsey, J. (2016, August). A hybrid causal search
        // algorithm for latent variable models. In Conference on probabilistic graphical models (pp. 368-379).
        // PMLR.
        for (Node b : best) {
            var adj = this.pag.getAdjacentNodes(b);

            for (int i = 0; i < adj.size(); i++) {
                for (int j = i + 1; j < adj.size(); j++) {
                    Node x = adj.get(i);
                    Node y = adj.get(j);

                    if (GraphUtils.distinct(x, b, y)) {
                        if (GraphUtils.colliderAllowed(pag, x, b, y, knowledge)) {
                            if (cpdag.isDefCollider(x, b, y)) {// && !cpdag.isAdjacentTo(x, y)) {
                                cpdagColliders.add(new Triple(x, b, y));

                                if (verbose) {
                                    TetradLogger.getInstance().log("Copied " + x + " *-> " + b + " <-* " + y + " from CPDAG to PAG.");
                                }
                            }
                        }
                    }
                }
            }
        }

        return cpdagColliders;
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
    private void refreshGraph() {
        GraphUtils.reorientWithCircles(pag, verbose);
        GraphUtils.recallCollidersFromCpdag(pag, cpdagColliders, knowledge);
        adjustForExtraSepsets();
        fciOrient.fciOrientbk(knowledge, pag, pag.getNodes());
        fciOrient.setUseR4(true);
        fciOrient.finalOrientation(pag);

        if (!pag.paths().isLegalPag()) {
            restoreState();
        } else {
            storeState();
        }
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
        sepsetMap.keySet().forEach(edge -> {
            List<Node> arr = new ArrayList<>(edge);

            Node x = arr.get(0);
            Node y = arr.get(1);

            List<Node> common = pag.getAdjacentNodes(x);
            common.retainAll(pag.getAdjacentNodes(y));

            if (verbose) {
                TetradLogger.getInstance().log("Removed adjacency " + x + " *-* " + y + " from PAG.");
            }

            for (Node node : common) {
                if (!sepsetMap.get(x, y).contains(node)) {
                    if (!pag.isDefCollider(x, node, y)) {
                        pag.setEndpoint(x, node, Endpoint.ARROW);
                        pag.setEndpoint(y, node, Endpoint.ARROW);

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

        pag.getEdges().forEach(edge -> {
            Node x = edge.getNode1();
            Node y = edge.getNode2();

            if (sepsetMap.get(x, y) != null) {
                return;
            }

            List<Node> common = pag.getAdjacentNodes(x);
            common.retainAll(pag.getAdjacentNodes(y));

            boolean found = false;

            for (Node node : common) {
                if (!pag.isDefCollider(x, node, y)) {
                    found = true;
                }
            }

            if (!found) {
                return;
            }

            try {
                if (ensureMarkovHelper.markovIndependence(x, y, Set.of())) {
                    if (verbose) {
                        TetradLogger.getInstance().log("Marking " + edge + " for removal because of unconditional independence.");
                    }

                    sepsetMap.set(x, y, Set.of());
                    pag.removeEdge(x, y);
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
    private void removeExtraEdgesDdp() {
        if (verbose) {
            TetradLogger.getInstance().log("Removing extra edges from discriminating paths.");
        }

        // The final orientation rules were applied just before this step, so this should list only
        // discriminating paths that could not be oriented by them...
        Set<DiscriminatingPath> discriminatingPaths = FciOrient.listDiscriminatingPaths(pag,
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
        for (Edge edge : pag.getEdges()) {
            if (verbose) {
                TetradLogger.getInstance().log("Considering removing edge " + edge);
            }

            Node x = edge.getNode1();
            Node y = edge.getNode2();

            if (pag.isAdjacentTo(x, y)) {
                if (sepsetMap.get(x, y) != null) {
                    if (verbose) {
                        TetradLogger.getInstance().log("Marking " + edge + " for removal because of potential DDP collider orientations.");
                    }

                    pag.removeEdge(x, y);
                }
            }

            List<Node> common = pag.getAdjacentNodes(x);
            common.retainAll(pag.getAdjacentNodes(y));

            Set<DiscriminatingPath> paths = pathsByEdge.get(Set.of(x, y));
            paths = paths == null ? Set.of() : paths;
            Set<Node> perhapsNotFollowed = new HashSet<>();

            if (verbose) {
                TetradLogger.getInstance().log("Discriminating paths for " + x + " and " + y + " are " + paths);
            }

            // Don't repeat the same independence test twice for this edge x *-* y.
            Set<Set<Node>> S = new HashSet<>();

            for (DiscriminatingPath path : paths) {
                if (pag.getEndpoint(path.getY(), path.getV()) == Endpoint.CIRCLE) {
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
                    B = SepsetFinder.blockPathsRecursively(pag, x, y, Set.of(), notFollowed, -1);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }

                if (verbose) {
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

                W:
                while ((choice2 = gen2.next()) != null) {
                    if (!pag.isAdjacentTo(x, y)) {
                        break;
                    }

                    Set<Node> c = GraphUtils.asSet(choice2, common);

                    for (Node node : c) {
                        if (pag.isDefCollider(x, node, y)) {
                            continue W;
                        }
                    }

                    b.removeAll(c);
                    if (S.contains(b)) continue;
                    S.add(new HashSet<>(b));

                    try {
                        if (pag.isAdjacentTo(x, y)) {
                            if (ensureMarkovHelper.markovIndependence(x, y, b)) {
                                if (verbose) {
                                    TetradLogger.getInstance().log("Marking " + edge + " for removal because of potential DDP collider orientations.");
                                }

                                sepsetMap.set(x, y, b);
                                pag.removeEdge(x, y);
                            }
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
}
