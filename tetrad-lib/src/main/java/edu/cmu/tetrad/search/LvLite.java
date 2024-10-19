///////////////////////////////////////////////////////////////////////////////
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
///////////////////////////////////////////////////////////////////////////////
package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.search.utils.*;
import edu.cmu.tetrad.util.MillisecondTimes;
import edu.cmu.tetrad.util.SublistGenerator;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * The LV-Lite algorithm (Latent Variable "Lite") algorithm implements a search algorithm for learning the structure of
 * a graphical model from observational data with latent variables. The algorithm uses the BOSS or GRaSP algorithm to
 * get an initial CPDAG. Then it uses scoring steps to infer some unshielded colliders in the graph, then finishes with
 * a testing step to remove extra edges and orient more unshielded colliders. Finally, the final FCI orientation is
 * applied to the graph.
 *
 * @author josephramsey
 */
public final class LvLite implements IGraphSearch {
    /**
     * The independence test.
     */
    private final IndependenceTest test;
    /**
     * The score.
     */
    private final Score score;
    /**
     * The initial CPDAG, if this is given in the constructor. This is used in place of the DAG/CPDAG obtained from the
     * BOSS or GRaSP algorithm, and only if the startWith parameter is set to INITIAL_GRAPH.
     */
    private Graph cpdag = null;
    /**
     * The background knowledge.
     */
    private Knowledge knowledge = new Knowledge();
    /**
     * The algorithm to use to get the initial CPDAG.
     */
    private START_WITH startWith = START_WITH.BOSS;
    /**
     * Flag indicating the output should be guaranteed to be a PAG.
     */
    private boolean guaranteePag = false;
    /**
     * The number of starts for GRaSP.
     */
    private int numStarts = 1;
    /**
     * The depth of the GRaSP if it is used.
     */
    private int recursionDepth = -1;
    /**
     * The maximum path length for blocking paths.
     */
    private int maxBlockingPathLength = -1;
    /**
     * The maximum size of any conditioning set.
     */
    private int depth = -1;
    /**
     * Flag for the complete rule set, true if one should use the complete rule set, false otherwise.
     */
    private boolean completeRuleSetUsed = true;
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
     * The maximum length of any discriminating path.
     */
    private int maxDdpPathLength = -1;
    /**
     * The style for removing extra edges.
     */
    private ExtraEdgeRemovalStyle extraEdgeRemovalStyle = ExtraEdgeRemovalStyle.PARALLEL;
    /**
     * The timeout for the testing steps, for the extra edge removal steps and the discriminating path steps.
     */
    private long testTimeout = 500;

    /**
     * LV-Lite constructor. Initializes a new object of LV-Lite search algorithm with the given IndependenceTest and
     * Score object.
     * <p>
     * In this constructor, we will use BOSS or GRaSP internally to infer an initial CPDAG and valid order of the
     * variables. This is the default behavior of the LV-Lite algorithm.
     *
     * @param test  The IndependenceTest object to be used for testing independence between variables.
     * @param score The Score object to be used for scoring DAGs.
     * @throws NullPointerException if the score is null.
     */
    public LvLite(IndependenceTest test, Score score) {
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
     * Alternative LV-Lite constructor. Initializes a new object of LV-Lite search algorithm with the given initial
     * CPDAG, along with the IndependenceTest. These should all be over variables with the same names as the variables
     * in the supplied test.
     * <p>
     * This constructor allows the user to employ an external algorithm to find this initial CPDAG (and an implied order
     * of the variables). This is useful when the user has a preferred algorithm for this task. In this case, the
     * algorithm will perform only the steps after the CPDAG initialization step.
     * <p>
     * It is important here that the CPDAG be obtained from an algorithm like BOSS or GRaSP that yields a high-quality
     * DAG or CPDAG over the variables, with a valid order, under the (possibly false) assumption that the data model is
     * causally sufficient. The CPDAG is used to establish the initial structure of the estimated PAG and to copy
     * unshielded colliders from the CPDAG into the estimated PAG. This step is justified in the GFCI algorithm.
     * Ogarrio, J. M., Spirtes, P., &amp; Ramsey, J. (2016, August). A hybrid causal search algorithm for latent
     * variable models. In Conference on probabilistic graphical models (pp. 368-379). PMLR. The idea is we start with
     * this initial estimated of the PAG, with edges reoriented as o-o edges. Then we use the scorer to copy unshielded
     * colliders from the CPDAG into the estimated PAG, as an initial step of converting the initial CPDAG into a PAG.
     *
     * @param cpdag The initial CPDAG.
     * @param test  The independence test.
     */
    public LvLite(Graph cpdag, IndependenceTest test) {
        this.score = null;
        this.test = test;
        this.cpdag = GraphUtils.replaceNodes(cpdag, this.test.getVariables());

        // Check to make sure the variable names in the cpdag are the same as the variable names in the test.
        List<String> cpdagVariableNames = cpdag.getNodes().stream().map(Node::getName).toList();
        List<String> testVariableNames = test.getVariables().stream().map(Node::getName).toList();

        if (!new HashSet<>(cpdagVariableNames).equals(new HashSet<>(testVariableNames))) {
            throw new IllegalArgumentException("The variable names in the CPDAG must be the same as the variable names in the test.");
        }

        this.startWith = START_WITH.INITIAL_GRAPH;

        test.setVerbose(false);
    }

    /**
     * Run the search and return s a PAG.
     *
     * @return The PAG.
     */
    public Graph search() {
        List<Node> nodes;

        if (this.score != null) {
            nodes = new ArrayList<>(this.score.getVariables());
        } else {
            nodes = new ArrayList<>(this.test.getVariables());
        }

        if (verbose) {
            TetradLogger.getInstance().log("===Starting LV-Lite===");
        }

        Graph pag;
        Graph cpdag;
        List<Node> best;

        if (startWith == START_WITH.BOSS) {

            if (verbose) {
                TetradLogger.getInstance().log("Running BOSS...");
            }

            long start = MillisecondTimes.wallTimeMillis();

            Boss subAlg = new Boss(this.score);
            subAlg.setUseBes(false);
            subAlg.setNumStarts(this.numStarts);
            subAlg.setNumThreads(30);
            subAlg.setVerbose(verbose);
            PermutationSearch alg = new PermutationSearch(subAlg);
            alg.setKnowledge(this.knowledge);

            cpdag = alg.search();
            best = cpdag.paths().getValidOrder(cpdag.getNodes(), true);

//            var permutationSearch = getBossSearch();
//            cpdag = permutationSearch.search(false);
//            best = permutationSearch.getOrder();
//            best = cpdag.paths().getValidOrder(best, true);

            long stop = MillisecondTimes.wallTimeMillis();

            if (verbose) {
                TetradLogger.getInstance().log("BOSS took " + (stop - start) + " ms.");
            }

            if (verbose) {
                TetradLogger.getInstance().log("Initializing PAG to BOSS CPDAG.");
                TetradLogger.getInstance().log("Initializing scorer with BOSS best order.");
            }
        } else if (startWith == START_WITH.GRASP) {
            if (verbose) {
                TetradLogger.getInstance().log("Running GRaSP...");
            }

            long start = MillisecondTimes.wallTimeMillis();

            Grasp grasp = getGraspSearch();
            best = grasp.bestOrder(nodes);
            cpdag = grasp.getGraph(false);

            long stop = MillisecondTimes.wallTimeMillis();

            if (verbose) {
                TetradLogger.getInstance().log("GRaSP took " + (stop - start) + " ms.");
            }

            if (verbose) {
                TetradLogger.getInstance().log("Initializing PAG to GRaSP CPDAG.");
                TetradLogger.getInstance().log("Initializing scorer with GRaSP best order.");
            }
        } else if (startWith == START_WITH.INITIAL_GRAPH) {
            if (verbose) {
                TetradLogger.getInstance().log("Using initial graph.");
            }

            cpdag = GraphUtils.replaceNodes(this.cpdag, nodes);
            best = cpdag.paths().getValidOrder(cpdag.getNodes(), true);

            if (verbose) {
                TetradLogger.getInstance().log("Initializing PAG to initial CPDAG.");
                TetradLogger.getInstance().log("Initializing scorer with initial CPDAG best order.");
            }
        } else {
            throw new IllegalArgumentException("Unknown startWith algorithm: " + startWith);
        }

        if (verbose) {
            TetradLogger.getInstance().log("Best order: " + best);
        }

        TeyssierScorer scorer = null;

        if (this.score != null) {
            scorer = new TeyssierScorer(test, score);
            scorer.score(best);
            scorer.setKnowledge(knowledge);
            scorer.setUseScore(false);
            scorer.setUseRaskuttiUhler(true);
            scorer.bookmark();
        }

        // We initialize the estimated PAG to the BOSS/GRaSP CPDAG, reoriented as a o-o graph.
        pag = new EdgeListGraph(cpdag);

        if (verbose) {
            TetradLogger.getInstance().log("Initializing PAG to BOSS CPDAG.");
            TetradLogger.getInstance().log("Initializing scorer with BOSS best order.");
        }

        R0R4StrategyTestBased strategy = new R0R4StrategyTestBased(test);
        strategy.setVerbose(verbose);
        strategy.setDepth(depth);
        strategy.setMaxLength(maxBlockingPathLength);

        FciOrient fciOrient = new FciOrient(strategy);
        fciOrient.setMaxDiscriminatingPathLength(maxDdpPathLength);
        fciOrient.setCompleteRuleSetUsed(completeRuleSetUsed);
        fciOrient.setTestTimeout(testTimeout);
        fciOrient.setVerbose(verbose);
        fciOrient.setParallel(true);
        fciOrient.setKnowledge(knowledge);

        if (verbose) {
            TetradLogger.getInstance().log("Collider orientation and edge removal.");
        }

        // The main procedure.
        Set<Triple> unshieldedColliders = new HashSet<>();
        Set<Triple> checked = new HashSet<>();

        if (scorer != null) {
            scorer.score(best);
        }

        GraphUtils.reorientWithCircles(pag, verbose);
        GraphUtils.doRequiredOrientations(fciOrient, pag, best, knowledge, false);

        // We're looking for unshielded colliders in these next steps that we can detect without using only
        // the scorer. We do this by looking at the structure of the DAG implied by the BOSS graph and copying
        // unshielded colliders from the BOSS graph into the estimated PAG. This step is justified in the
        // GFCI algorithm. Ogarrio, J. M., Spirtes, P., & Ramsey, J. (2016, August). A hybrid causal search
        // algorithm for latent variable models. In Conference on probabilistic graphical models (pp. 368-379).
        // PMLR.
        for (Node b : best) {
            var adj = pag.getAdjacentNodes(b);

            for (Node x : adj) {
                for (Node y : adj) {
                    if (GraphUtils.distinct(x, b, y) && !checked.contains(new Triple(x, b, y))) {
                        copyUnshieldedCollider(x, b, y, pag, cpdag, scorer, unshieldedColliders, checked);
                    }
                }
            }
        }

        GraphUtils.reorientWithCircles(pag, verbose);
        GraphUtils.doRequiredOrientations(fciOrient, pag, best, knowledge, false);
        GraphUtils.recallUnshieldedTriples(pag, unshieldedColliders, knowledge);

        // Next, we remove the "extra" adjacencies from the graph. We do this differently than in GFCI. There, we
        // look for a sepset for an edge x *-* y from among adj(x) or adj(y), so the problem is exponential one
        // each side. So in a dense graph, this can take a very long time to complete. Here, we look for a sepset
        // for each edge by examining the structure of the current graph and finding a sepset that blocks all
        // paths between x and y. This is a simpler problem and scales better to dense graphs (though not perfectly).
        Map<Edge, Set<Node>> extraSepsets = removeExtraEdges(pag, unshieldedColliders);

        GraphUtils.reorientWithCircles(pag, verbose);
        GraphUtils.doRequiredOrientations(fciOrient, pag, best, knowledge, verbose);
        GraphUtils.recallUnshieldedTriples(pag, unshieldedColliders, knowledge);

        // Final FCI orientation.
        if (verbose) {
            TetradLogger.getInstance().log("Doing implied orientation, grabbing unshielded colliders from FciOrient.");
        }

        fciOrient.setInitialAllowedColliders(new HashSet<>());
        fciOrient.finalOrientation(pag);

        if (verbose) {
            TetradLogger.getInstance().log("Finished implied orientation.");
        }

        // Now test the specific extra condition where DDPs colliders would have been oriented had an edge not been
        // there in this graph.
        for (Edge edge : pag.getEdges()) {
            Node x = edge.getNode1();
            Node y = edge.getNode2();

            List<Node> exclude = new ArrayList<>();

            for (Node z : pag.getAdjacentNodes(x)) {
                Set<DiscriminatingPath> discriminatingPaths = FciOrient.listDiscriminatingPaths(pag, x, z, maxDdpPathLength, false);

                for (DiscriminatingPath path : discriminatingPaths) {
                    if (path.getX() == y) {
                        pag.removeEdge(x, y);
                        exclude.add(path.getV());
                    }
                }
            }

            for (Node z : pag.getAdjacentNodes(y)) {
                Set<DiscriminatingPath> discriminatingPaths = FciOrient.listDiscriminatingPaths(pag, y, z, maxDdpPathLength,
                        false);

                for (DiscriminatingPath path : discriminatingPaths) {
                    if (path.getX() == x) {
                        pag.removeEdge(x, y);
                        exclude.add(path.getV());
                    }
                }
            }

            // We're only checking length 3 DDPs here; there could be longer ones.
//            for (Node w : pag.getAdjacentNodes(x)) {
//                for (Node v : pag.getAdjacentNodes(y)) {
//                    if (w == v) continue;
//                    if (w == y || v == x) continue;
////                    if (pag.isAdjacentTo(y, w) && pag.isAdjacentTo(v, w) && !pag.isAdjacentTo(x, v)) {
////                        if (pag.getEndpoint(x, w) == Endpoint.ARROW
////                            && pag.getEndpoint(v, y) == Endpoint.ARROW
////                            && pag.getEndpoint(v, w) == Endpoint.ARROW
////                            && pag.getEndpoint(y, w) != Endpoint.ARROW
////                            && pag.getEndpoint(w, v) == Endpoint.CIRCLE
////                            && pag.getEndpoint(y, v) == Endpoint.CIRCLE) {
////                            exclude.add(v);
////                        }
////                    }
//
//                    if (pag.isAdjacentTo(y, w) && pag.isAdjacentTo(v, w) && !pag.isAdjacentTo(x, v)) {
//                        if (pag.getEndpoint(x, w) != Endpoint.ARROW) {
//                            continue;
//                        }
//                        if (pag.getEndpoint(v, y) != Endpoint.ARROW) {
//                            continue;
//                        }
//                        if (pag.getEndpoint(v, w) != Endpoint.ARROW) {
//                            continue;
//                        }
//                        if (pag.getEndpoint(y, w) == Endpoint.ARROW) {
//                            continue;
//                        }
//                        if (pag.getEndpoint(w, v) != Endpoint.CIRCLE) {
//                            continue;
//                        }
//                        if (pag.getEndpoint(y, v) != Endpoint.CIRCLE) {
//                            continue;
//                        }
//
//                        exclude.add(v);
//                    }
//                }
//            }

            Set<Node> blocking = SepsetFinder.blockPathsRecursively(pag, x, y, Set.of(), maxBlockingPathLength);

            SublistGenerator gen = new SublistGenerator(exclude.size(), exclude.size());
            int[] choice;

            while ((choice = gen.next()) != null) {
                Set<Node> myExclude = GraphUtils.asSet(choice, exclude);
                blocking.removeAll(myExclude);

                if (test.checkIndependence(x, y, blocking).isIndependent()) {
                    if (verbose) {
                        TetradLogger.getInstance().log("Removed edge " + x + " *-* " + y + " because of DDP.");
                    }

                    pag.removeEdge(x, y);
                    break;
                }
            }
        }

        GraphUtils.reorientWithCircles(pag, verbose);
        GraphUtils.doRequiredOrientations(fciOrient, pag, best, knowledge, verbose);
        GraphUtils.recallUnshieldedTriples(pag, unshieldedColliders, knowledge);

        // Final FCI orientation.
        if (verbose) {
            TetradLogger.getInstance().log("Doing implied orientation, grabbing unshielded colliders from FciOrient.");
        }

        fciOrient.setInitialAllowedColliders(new HashSet<>());
        fciOrient.finalOrientation(pag);

        if (verbose) {
            TetradLogger.getInstance().log("Finished implied orientation.");
        }

        if (guaranteePag) {
            pag = GraphUtils.guaranteePag(pag, fciOrient, knowledge, unshieldedColliders, false, verbose, new HashSet<>());
        }

        if (verbose) {
            TetradLogger.getInstance().log("LV-Lite finished.");
        }

        return GraphUtils.replaceNodes(pag, nodes);
    }

    /**
     * Try adding an unshielded collider by checking the BOSS/GRaSP DAG.
     *
     * @param x                   Node - The first node.
     * @param b                   Node - The second node.
     * @param y                   Node - The third node.
     * @param pag                 Graph - The graph to operate on.
     * @param scorer              The scorer to use for scoring the colliders.
     * @param unshieldedColliders The set to store unshielded colliders.
     * @param checked             The set to store already checked nodes.
     */
    private void copyUnshieldedCollider(Node x, Node b, Node y, Graph pag, Graph cpdag, TeyssierScorer scorer, Set<Triple> unshieldedColliders, Set<Triple> checked) {
        tryAddingCollider(x, b, y, pag, cpdag, scorer, unshieldedColliders, checked, knowledge, verbose);
    }

    /**
     * Parameterizes and returns a new BOSS search.
     *
     * @return A new BOSS search.
     */
    private @NotNull PermutationSearch getBossSearch() {
        var suborderSearch = new Boss(score);
        suborderSearch.setResetAfterBM(true);
        suborderSearch.setResetAfterRS(true);
        suborderSearch.setVerbose(false);
        suborderSearch.setUseBes(useBes);
        suborderSearch.setUseDataOrder(useDataOrder);
        suborderSearch.setNumStarts(numStarts);
        suborderSearch.setVerbose(verbose);
        var permutationSearch = new PermutationSearch(suborderSearch);
        permutationSearch.setKnowledge(knowledge);
        permutationSearch.search();
        return permutationSearch;
    }

    /**
     * Parameterizes and returns a new GRaSP search.
     *
     * @return A new GRaSP search.
     */
    private @NotNull Grasp getGraspSearch() {
        Grasp grasp = new Grasp(test, score);

        grasp.setSeed(-1);
        grasp.setDepth(recursionDepth);
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
     * Sets the maximum length of any discriminating path.
     *
     * @param maxBlockingPathLength the maximum length of any discriminating path, or -1 if unlimited.
     */
    public void setMaxBlockingPathLength(int maxBlockingPathLength) {
        if (maxBlockingPathLength < -1) {
            throw new IllegalArgumentException("Max path length must be -1 (unlimited) or >= 0: " + maxBlockingPathLength);
        }

        this.maxBlockingPathLength = maxBlockingPathLength;
    }

    /**
     * Sets the depth of the GRaSP if it is used.
     *
     * @param recursionDepth The depth of the GRaSP.
     */
    public void setRecursionDepth(int recursionDepth) {
        this.recursionDepth = recursionDepth;
    }

    /**
     * Sets whether to guarantee a PAG output by repairing a faulty PAG.
     *
     * @param guaranteePag true if a faulty PAGs should be repaired, false otherwise
     */
    public void setGuaranteePag(boolean guaranteePag) {
        this.guaranteePag = guaranteePag;
    }

    /**
     * Sets the algorithm to use to obtain the initial CPDAG.
     *
     * @param startWith the algorithm to use to obtain the initial CPDAG.
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
     * Sets whether the complete rule set should be used during the search algorithm. By default, the complete rule set
     * is not used.
     *
     * @param completeRuleSetUsed true if the complete rule set should be used, false otherwise
     */
    public void setCompleteRuleSetUsed(boolean completeRuleSetUsed) {
        this.completeRuleSetUsed = completeRuleSetUsed;
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
     * Tries removing extra edges from the PAG using a test with sepsets obtained by examining the BOSS/GRaSP DAG.
     *
     * @param pag                 The graph in which to remove extra edges.
     * @param unshieldedColliders A set to store the unshielded colliders found during the removal process.
     * @return A map of edges to remove to sepsets used to remove them. The sepsets are the conditioning sets used to
     * remove the edges. These can be used to do orientation of common adjacents, as x *-&gt: b &lt;-* y just in case b
     * is not in this sepset.
     */
    private Map<Edge, Set<Node>> removeExtraEdges(Graph pag, Set<Triple> unshieldedColliders) {
        if (verbose) {
            TetradLogger.getInstance().log("Checking for additional sepsets:");
        }

        MsepTest msep = new MsepTest(pag);

        // Note that we can use the MAG here instead of the DAG.
        Map<Edge, Set<Node>> extraSepsets = new ConcurrentHashMap<>();

        if (extraEdgeRemovalStyle == ExtraEdgeRemovalStyle.PARALLEL) {
            List<Callable<Pair<Edge, Set<Node>>>> tasks = new ArrayList<>();

            for (Edge edge : pag.getEdges()) {
                tasks.add(() -> {
                    Node x = edge.getNode1();
                    Node y = edge.getNode2();

                    Set<Node> blockers = SepsetFinder.getPathBlockingSetRecursive(pag, x,
                            y, new HashSet<>(), maxBlockingPathLength);
//                    Set<Node> blockers = SepsetFinder.blockPathsNoncollidersOnly(pag, edge.getNode1(),
//                            edge.getNode2(), maxBlockingPathLength, true);
//                    Set<Node> blockers = SepsetFinder.getSepsetPathBlockingOutOfX(pag, edge.getNode1(), edge.getNode2(),
//                            msep, -1, -1, true, new HashSet<>());

                    // Find the common adjacents of x and y.
                    List<Node> common = pag.getAdjacentNodes(x);
                    common.retainAll(pag.getAdjacentNodes(y));

//                    for (Node z : common) {
//                        if (!pag.isDefCollider(x, z, y)) {
//                            blockers.remove(z);
//                        }
//                    }


                    blockers.removeAll(common);

                    if (this.test.checkIndependence(x, y, blockers).isIndependent()) {
                        return Pair.of(edge, blockers);
                    } else {
                        return Pair.of(edge, null);
                    }
                });
            }

            List<Pair<Edge, Set<Node>>> results;

            if (testTimeout == -1) {
                results = tasks.parallelStream().map(task -> {
                    try {
                        return task.call();
                    } catch (Exception e) {
                        return null;
                    }
                }).toList();
            } else if (testTimeout > 0) {
                results = tasks.stream().map(task -> GraphSearchUtils.runWithTimeout(task, testTimeout,
                        TimeUnit.MILLISECONDS)).toList();
            } else {
                throw new IllegalArgumentException("Test timeout must be -1 (unlimited) or > 0: " + testTimeout);
            }

            for (Pair<Edge, Set<Node>> _edge : results) {
                if (_edge != null && _edge.getRight() != null) {
                    extraSepsets.put(_edge.getLeft(), _edge.getRight());
                }
            }

            for (Edge edge : extraSepsets.keySet()) {
                orientCommonAdjacents(edge, pag, unshieldedColliders, extraSepsets);
            }
        } else if (extraEdgeRemovalStyle == ExtraEdgeRemovalStyle.SERIAL) {

            Set<Edge> edges = new HashSet<>(pag.getEdges());
            Set<Edge> visited = new HashSet<>();
            Deque<Edge> toVisit = new LinkedList<>(edges);

            // Sort edges x *-* y in toVisit by |adj(x)| + |adj(y)|.
            toVisit = toVisit.stream().sorted(Comparator.comparingInt(edge -> pag.getAdjacentNodes(
                    edge.getNode1()).size() + pag.getAdjacentNodes(edge.getNode2()).size())).collect(Collectors.toCollection(LinkedList::new));

            while (!toVisit.isEmpty()) {
                Edge edge = toVisit.removeFirst();
                visited.add(edge);

                Set<Node> sepset = SepsetFinder.blockPathsNoncollidersOnly(pag, edge.getNode1(), edge.getNode2(),
                        maxBlockingPathLength, true);

                if (verbose) {
                    TetradLogger.getInstance().log("For edge " + edge + " sepset: " + sepset);
                }

                if (sepset != null) {
                    extraSepsets.put(edge, sepset);
                    pag.removeEdge(edge.getNode1(), edge.getNode2());
                    orientCommonAdjacents(edge, pag, unshieldedColliders, extraSepsets);

                    for (Node node : pag.getAdjacentNodes(edge.getNode1())) {
                        Edge adjacentEdge = pag.getEdge(node, edge.getNode1());
                        if (!visited.contains(adjacentEdge)) {
                            toVisit.remove(adjacentEdge);
                            toVisit.addFirst(adjacentEdge);
                        }
                    }

                    for (Node node : pag.getAdjacentNodes(edge.getNode2())) {
                        Edge adjacentEdge = pag.getEdge(node, edge.getNode2());
                        if (!visited.contains(adjacentEdge)) {
                            toVisit.remove(adjacentEdge);
                            toVisit.addFirst(adjacentEdge);
                        }
                    }
                }
            }
        }

        if (verbose) {
            TetradLogger.getInstance().log("Done checking for additional sepsets max length = " + maxBlockingPathLength + ".");
        }

        return extraSepsets;
    }

    /**
     * Orients an unshielded collider in a graph based on a sepset from a test and adds the unshielded collider to the
     * set of unshielded colliders.
     *
     * @param edge                The edge to remove the adjacency for.
     * @param pag                 The graph in which to orient the unshielded collider.
     * @param unshieldedColliders The set of unshielded colliders to add the new unshielded collider to.
     * @param extraSepsets        The map of edges to sepsets used to remove them.
     */
    private boolean orientCommonAdjacents(Edge edge, Graph pag, Set<Triple> unshieldedColliders, Map<Edge, Set<Node>> extraSepsets) {
        boolean changed = false;

        Node x = edge.getNode1();
        Node y = edge.getNode2();

        if (pag.isAdjacentTo(x, y)) {
            pag.removeEdge(x, y);
            changed = true;
        }

        List<Node> common = pag.getAdjacentNodes(x);
        common.retainAll(pag.getAdjacentNodes(y));

        if (verbose) {
            TetradLogger.getInstance().log("Removing adjacency " + x + " *-* " + y + " from PAG.");
        }

        for (Node node : common) {
            if (!extraSepsets.get(edge).contains(node)) {
                if (!pag.isDefCollider(x, node, y)) {
                    pag.setEndpoint(x, node, Endpoint.ARROW);
                    pag.setEndpoint(y, node, Endpoint.ARROW);

                    if (verbose) {
                        TetradLogger.getInstance().log("Oriented " + x + " *-> " + node + " <-* " + y + " in PAG.");
                    }

                    unshieldedColliders.add(new Triple(x, node, y));
                    changed = true;
                }
            }
        }

        return changed;
    }

    /**
     * Adds a collider if it's a collider in the current scorer and knowledge permits it in the current PAG.
     *
     * @param x                   The first node of the unshielded collider.
     * @param b                   The second node of the unshielded collider.
     * @param y                   The third node of the unshielded collider.
     * @param pag                 The graph in which to add the unshielded collider.
     * @param scorer              The scorer to use for scoring the unshielded collider.
     * @param unshieldedColliders The set of unshielded colliders to add the new unshielded collider to.
     * @param checked             The set of checked unshielded colliders.
     * @param knowledge           The knowledge object.
     * @param verbose             A boolean flag indicating whether verbose output should be printed.
     */
    private void tryAddingCollider(Node x, Node b, Node y, Graph pag, Graph cpdag, TeyssierScorer scorer, Set<Triple> unshieldedColliders, Set<Triple> checked, Knowledge knowledge, boolean verbose) {
        if (cpdag != null) {
            if (GraphUtils.colliderAllowed(pag, x, b, y, knowledge)) {
                if (cpdag.isDefCollider(x, b, y) && !cpdag.isAdjacentTo(x, y)) {
                    unshieldedColliders.add(new Triple(x, b, y));
                    checked.add(new Triple(x, b, y));

                    if (verbose) {
                        TetradLogger.getInstance().log("Copied " + x + " *-> " + b + " <-* " + y + " from CPDAG to PAG.");
                    }
                }
            }
        } else if (score != null) {
            if (GraphUtils.colliderAllowed(pag, x, b, y, knowledge)) {
                if (scorer.unshieldedCollider(x, b, y)) {
                    unshieldedColliders.add(new Triple(x, b, y));
                    checked.add(new Triple(x, b, y));

                    if (verbose) {
                        TetradLogger.getInstance().log("Copied " + x + " *-> " + b + " <-* " + y + " from CPDAG to PAG.");
                    }
                }
            }
        } else {
            throw new IllegalArgumentException("No CPDAG or scorer available.");
        }
    }

    /**
     * Sets the maximum size of the separating set used in the graph search algorithm.
     *
     * @param depth the maximum size of the separating set
     */
    public void setDepth(int depth) {
        this.depth = depth;
    }

    /**
     * Sets the maximum DDP path length.
     *
     * @param maxDdpPathLength the maximum DDP path length to set
     */
    public void setMaxDdpPathLength(int maxDdpPathLength) {
        this.maxDdpPathLength = maxDdpPathLength;
    }

    /**
     * Sets the style for removing extra edges.
     *
     * @param extraEdgeRemovalStyle the style for removing extra edges
     */
    public void setExtraEdgeRemovalStyle(ExtraEdgeRemovalStyle extraEdgeRemovalStyle) {
        this.extraEdgeRemovalStyle = extraEdgeRemovalStyle;
    }

    /**
     * Sets the timeout for the testing steps, for the extra edge removal steps and the discriminating path steps.
     *
     * @param testTimeout the timeout for the testing steps, for the extra edge removal steps and the discriminating
     *                    path steps.
     */
    public void setTestTimeout(long testTimeout) {
        this.testTimeout = testTimeout;
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
         * Starts with an initial CPDAG over the variables of the independence test that is given in the constructor.
         */
        INITIAL_GRAPH
    }

    /**
     * The ExtraEdgeRemovalStyle enum specifies the styles for removing extra edges.
     */
    public enum ExtraEdgeRemovalStyle {

        /**
         * Remove extra edges in parallel.
         */
        PARALLEL,

        /**
         * Remove extra edges in serial.
         */
        SERIAL,
    }
}
