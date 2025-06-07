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
     * Represents a map for storing and managing separation sets (sepsets) used in the context of algorithms involving
     * conditional independence or causal discovery.
     * <p>
     * This variable is an instance of {@link SepsetMap}, which provides methods to access and manipulate separation
     * sets - specifically to check conditional independencies between pairs of variables given a separating set.
     */
    private final SepsetMap sepsets = new SepsetMap();
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
     * True if condition sets should at the end be checked that are subsets of adjacents of the variables. This is only
     * done after all recursive sepset removals have been done. True by default. This is needed in order to pass an
     * Oracle test but can reduce accuracy from data.
     */
    private boolean checkAdjacencySepsets = true;
    private @NotNull Graph pag;

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

        test.setVerbose(superVerbose);

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

        if (score != null) {
            nodes = new ArrayList<>(score.getVariables());
        } else {
            nodes = new ArrayList<>(test.getVariables());
        }

        TetradLogger.getInstance().log("===Starting FCIT===");

        R0R4StrategyTestBased strategy = new R0R4StrategyTestBased(test);
        strategy.setSepsetMap(sepsets);
        strategy.setVerbose(superVerbose);
        strategy.setBlockingType(R0R4StrategyTestBased.BlockingType.RECURSIVE);
        strategy.setDepth(depth);

        fciOrient = new FciOrient(strategy);
        fciOrient.setVerbose(superVerbose);
        fciOrient.setParallel(false);
        fciOrient.setCompleteRuleSetUsed(completeRuleSetUsed);
        fciOrient.setKnowledge(knowledge);

        Graph dag;
        List<Node> best;
        long start1 = System.currentTimeMillis();

        if (startWith == START_WITH.BOSS) {

            if (superVerbose) {
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

        // The main procedure.
        this.pag = GraphTransforms.dagToPag(dag);

        initialColliders = noteInitialColliders(best, this.pag);

        removeEdgesRecursively();

        if (checkAdjacencySepsets) {
            removeEdgesSubsetsOfAdjacents();
        }

        GraphUtils.reorientWithCircles(this.pag, superVerbose);
        GraphUtils.recallInitialColliders(this.pag, initialColliders, knowledge);
        adjustForExtraSepsets();
        fciOrient.fciOrientbk(knowledge, this.pag, this.pag.getNodes());
        fciOrient.finalOrientation(this.pag);

        if (superVerbose) {
            TetradLogger.getInstance().log("Doing implied orientation, grabbing unshielded colliders from FciOrient.");
        }

        long stop2 = System.currentTimeMillis();

        TetradLogger.getInstance().log("FCIT finished.");
        TetradLogger.getInstance().log("BOSS/GRaSP time: " + (stop1 - start1) + " ms.");
        TetradLogger.getInstance().log("Collider orientation and edge removal time: " + (stop2 - start2) + " ms.");
        TetradLogger.getInstance().log("Total time: " + (stop2 - start1) + " ms.");

        return GraphUtils.replaceNodes(this.pag, nodes);
    }

    private void removeEdgesSubsetsOfAdjacents() throws InterruptedException {
        EDGE:
        for (Edge edge : this.pag.getEdges()) {
            Node x = edge.getNode1();
            Node y = edge.getNode2();

            List<Node> adjx = this.pag.getAdjacentNodes(x);
            List<Node> adjy = this.pag.getAdjacentNodes(y);
            adjx.remove(y);
            adjy.remove(x);

            SublistGenerator gen1 = new SublistGenerator(adjx.size(), adjy.size());
            int[] choice1;

            while ((choice1 = gen1.next()) != null) {
                Set<Node> cond = GraphUtils.asSet(choice1, adjx);

                if (test.checkIndependence(x, y, cond).isIndependent()) {
                    if (verbose) {
                        TetradLogger.getInstance().log("Tried removing edge " + edge + " for adjacency reasons.");
                    }
                    this.pag.removeEdge(x, y);
                    sepsets.set(x, y, cond);
                    refreshGraph(x + " _||_ " + y + " | " + cond);
                    continue EDGE;
                }
            }

            SublistGenerator gen2 = new SublistGenerator(adjy.size(), adjy.size());
            int[] choice2;

            while ((choice2 = gen2.next()) != null) {
                Set<Node> cond = GraphUtils.asSet(choice2, adjy);

                if (test.checkIndependence(x, y, cond).isIndependent()) {
                    TetradLogger.getInstance().log("Tried removing edge " + edge + " for adjacency reasons.");
                    this.pag.removeEdge(x, y);
                    sepsets.set(x, y, cond);
                    refreshGraph(x + " _||_ " + y + " | " + cond);
                    continue EDGE;
                }
            }
        }
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
        subAlg.setVerbose(superVerbose);
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
        grasp.setAllowInternalRandomness(true);
        grasp.setVerbose(superVerbose);

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
            var adj = this.pag.getAdjacentNodes(b);

            for (int i = 0; i < adj.size(); i++) {
                for (int j = i + 1; j < adj.size(); j++) {
                    Node x = adj.get(i);
                    Node y = adj.get(j);

                    if (GraphUtils.distinct(x, b, y)) {
                        if (GraphUtils.colliderAllowed(this.pag, x, b, y, knowledge)) {
                            if (pag.isDefCollider(x, b, y)) {// && !pag.isAdjacentTo(x, y)) {
                                initialColliders.add(new Triple(x, b, y));

                                if (superVerbose) {
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
     * Refreshes the current Partial Ancestral Graph (PAG) by reorienting edges, adjusting for separation sets, and
     * applying final orientations. This method ensures the PAG remains valid after performing necessary modifications.
     *
     * @param message A descriptive message indicating the context or purpose of the graph refresh operation.
     */
    private void refreshGraph(String message) {
        GraphUtils.reorientWithCircles(this.pag, superVerbose);
        GraphUtils.recallInitialColliders(this.pag, initialColliders, knowledge);
        adjustForExtraSepsets();
        fciOrient.fciOrientbk(knowledge, this.pag, this.pag.getNodes());
        fciOrient.finalOrientation(this.pag);

        // Don't need to check legal PAG here; can limit the check to these two conditions, as removing an edge
        // cannot cause new cycles or almost-cycles to be formed.
        noteRejects(message);
    }

    private void noteRejects(String message) {
        if (!this.pag.paths().isLegalPag()) {
            if (verbose) {
                TetradLogger.getInstance().log("Rejected: " + message);
            }
        } else {
            if (verbose) {
                TetradLogger.getInstance().log("ACCEPTED: " + message);
            }
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
        sepsets.keySet().forEach(edge -> {
            List<Node> arr = new ArrayList<>(edge);

            Node x = arr.get(0);
            Node y = arr.get(1);

            List<Node> common = this.pag.getAdjacentNodes(x);
            common.retainAll(this.pag.getAdjacentNodes(y));

            if (superVerbose) {
                TetradLogger.getInstance().log("Removed adjacency " + x + " *-* " + y + " from PAG.");
            }

            for (Node node : common) {
                if (!sepsets.get(x, y).contains(node)) {
                    if (!this.pag.isDefCollider(x, node, y)) {
                        this.pag.setEndpoint(x, node, Endpoint.ARROW);
                        this.pag.setEndpoint(y, node, Endpoint.ARROW);

                        if (superVerbose) {
                            TetradLogger.getInstance().log("Oriented " + x + " *-> " + node + " <-* " + y + " in PAG.");
                        }
                    }
                }
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
    private void removeEdgesRecursively() {
        if (superVerbose) {
            TetradLogger.getInstance().log("Removing extra edges from discriminating paths.");
        }

        // The final orientation rules were applied just before this step, so this should list only
        // discriminating paths that could not be oriented by them...
        Set<DiscriminatingPath> discriminatingPaths = FciOrient.listDiscriminatingPaths(this.pag,
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
        EDGE:
        for (Edge edge : this.pag.getEdges()) {
            if (superVerbose) {
                TetradLogger.getInstance().log("Considering removing edge " + edge);
            }

            Node x = edge.getNode1();
            Node y = edge.getNode2();

            Set<DiscriminatingPath> paths = pathsByEdge.get(Set.of(x, y));
            paths = (paths == null) ? Set.of() : paths;
            Set<Node> perhapsNotFollowed = new HashSet<>();

            // Don't repeat the same independence test twice for this edge x *-* y.
            Set<Set<Node>> S = new HashSet<>();

            for (DiscriminatingPath path : paths) {
                if (this.pag.getEndpoint(path.getY(), path.getV()) == Endpoint.CIRCLE) {
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
                Set<Node> _b;
                try {
                    _b = RecursiveBlocking.blockPathsRecursively(this.pag, x, y, Set.of(), notFollowed, -1);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }

                if (superVerbose && !notFollowed.isEmpty()) {
                    TetradLogger.getInstance().log("Not followed set = " + notFollowed + " b set = " + _b);
                }

                // b will be null if the search did not conclude with a set known to either m-separate
                // or not m-separate x and y.
                if (_b == null) {
                    continue;
                }

                List<Node> common = this.pag.getAdjacentNodes(x);
                common.retainAll(this.pag.getAdjacentNodes(y));

                SublistGenerator gen2 = new SublistGenerator(common.size(), common.size());
                int[] choice2;

                while ((choice2 = gen2.next()) != null) {
                    if (!this.pag.isAdjacentTo(x, y)) {
                        break;
                    }

                    Set<Node> b = new HashSet<>(_b);
                    Set<Node> c = GraphUtils.asSet(choice2, common);

                    for (Node n : c) {
                        if (c.add(n))                                                                                                                                {
                            b.add(n);
                        } else {
                            b.remove(n);
                        }
                    }

                    if (S.contains(b)) continue;
                    S.add(new HashSet<>(b));

                    if (b.size() > (depth == -1 ? test.getVariables().size() : depth)) {
                        continue;
                    }

                    try {
                        if (test.checkIndependence(x, y, b).isIndependent()) {
                            if (verbose) {
                                TetradLogger.getInstance().log("Tried removing " + edge + " for recursive reasons.");
                            }

                            this.pag.removeEdge(x, y);
                            sepsets.set(x, y, b);
                            refreshGraph(x + " _||_ " + y + " | " + b + " (new sepset)");
                            continue EDGE;
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
            throw new IllegalArgumentException(
                    "Depth must be -1 (unlimited) or >= 0: " + depth);
        }

        this.depth = depth;
    }

    /**
     * True if condition sets should at the end be checked that are subsets of adjacents of the variables. This is only
     * done after all recursive sepset removals have been done. True by default. This is needed in order to pass an
     * Oracle test but can reduce accuracy from data.
     *
     * @param checkAdjacencySepsets True if the final FCI-style rule of checking adjacency sepsets should be performed.
     */
    public void setCheckAdjacencySepsets(boolean checkAdjacencySepsets) {
        this.checkAdjacencySepsets = checkAdjacencySepsets;
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
