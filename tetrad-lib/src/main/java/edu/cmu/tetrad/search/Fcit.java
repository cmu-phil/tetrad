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
     * The maximum size of any conditioning set.
     */
    private int depth = -1;
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
     * True if the local Markov property should be ensured from an initial local Markov graph.
     */
    private boolean ensureMarkov = false;
    /**
     * A helper class to help preserve Markov.
     */
    private EnsureMarkov ensureMarkovHelper = null;
    /**
     * Represents whether the payment guarantee feature is enabled or not. This variable is a flag to determine if the
     * guarantee payment option is active in the current context.
     */
    private boolean guaranteePag = false;
    /**
     * Represents the learned Partial Ancestral Graph (PAG) in the FCIT search algorithm. The PAG is a graphical
     * representation that encodes causal relationships among variables that are consistent with the observed data and
     * assumes the presence of latent confounders but no selection bias. It is initialized to null and is populated as
     * the search algorithm progresses.
     */
    private Graph pag = null;
    /**
     * Stores additional separating sets identified during the process of graph construction but not originally part of
     * the main separation sets used in the search.
     * <p>
     * The map associates an {@link Edge} with a corresponding {@link Set} of {@link Node}s representing separating sets
     * specific to that edge.
     * <p>
     * These additional separating sets are used to refine the graph structure and ensure that the inferred causality
     * relationships adhere to the conditions of independence determined by the algorithm.
     */
    private Map<Edge, Set<Node>> extraSepsets = new HashMap<>();
    /**
     * A set to store unshielded colliders identified from the CPDAG (Completed Partially Directed Acyclic Graph).
     * <p>
     * Colliders represent triples of nodes (X, Y, Z) where X → Y ← Z. These are unshielded colliders, meaning there is
     * no direct edge between X and Z. This set maintains all such colliders during the search process for constructing
     * and analyzing the causal structure.
     */
    private Set<Triple> cpdagColliders = new HashSet<>();
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
    private Map<Edge, Set<Node>> lastExtraSepsets = null;
    /**
     * A set that keeps track of the previously identified colliders during the execution of the FCIT search algorithm.
     * Colliders are unshielded triples of nodes (X, Y, Z) such that X and Z are not adjacent, but are both parents of
     * Y.
     * <p>
     * This variable plays a key role in maintaining state about the structure of the underlying graph and assists in
     * ensuring that the search algorithm respects these previously identified relationships.
     */
    private Set<Triple> lastKnownColliders = null;

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
     * Run the search and return s a PAG.
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

        R0R4StrategyTestBased strategy = new R0R4StrategyTestBased(test);
        strategy.setVerbose(verbose);
        strategy.setDepth(depth);
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

            Boss subAlg = new Boss(this.score);
            subAlg.setUseBes(this.useBes);
            subAlg.setNumStarts(this.numStarts);
            subAlg.setNumThreads(Runtime.getRuntime().availableProcessors());
            subAlg.setVerbose(verbose);
            PermutationSearch alg = new PermutationSearch(subAlg);
            alg.setKnowledge(this.knowledge);

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

        ensureMarkovHelper = new EnsureMarkov(dag, test);
        ensureMarkovHelper.setEnsureMarkov(ensureMarkov);

        // We initialize the estimated PAG to the BOSS/GRaSP CPDAG, reoriented as a o-o graph.
        pag = new EdgeListGraph(cpdag);

        if (verbose) {
            TetradLogger.getInstance().log("Initializing PAG to BOSS CPDAG.");
            TetradLogger.getInstance().log("Initializing scorer with BOSS best order.");
        }

        if (verbose) {
            TetradLogger.getInstance().log("Collider orientation and edge removal.");
        }

        // The main procedure.
        extraSepsets = new HashMap<>();

        if (scorer != null) {
            scorer.score(best);
        }

        GraphUtils.reorientWithCircles(pag, verbose);
        fciOrient.fciOrientbk(knowledge, pag, nodes);

        if (verbose) {
            TetradLogger.getInstance().log("Copying unshielded colliders from CPDAG.");
        }

        noteKnownCollidersFromCpdag(best, cpdag);

        fciOrient.fciOrientbk(knowledge, pag, pag.getNodes());
        fciOrient.setUseR4(true);
        GraphUtils.recallCollidersFromCpdag(pag, cpdagColliders, knowledge);
        fciOrient.finalOrientation(pag);

        lastPag = new EdgeListGraph(pag);
        lastKnownColliders = new HashSet<>(cpdagColliders);
        lastExtraSepsets = new HashMap<>(extraSepsets);

        refreshGraph();

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

        while (true) {
            Graph _pag = new EdgeListGraph(pag);

            removeExtraEdgesDdp();
            refreshGraph();

            if (_pag.equals(pag)) {
                break;
            }
        }

        checkUnconditionalIndependence();

        removeExtraEdgesDdp();
        refreshGraph();

        while (true) {
            Graph _pag = new EdgeListGraph(pag);

            removeExtraEdgesDdp();
            refreshGraph();

            if (_pag.equals(pag)) {
                break;
            }
        }


        if (verbose) {
            TetradLogger.getInstance().log("Doing implied orientation, grabbing unshielded colliders from FciOrient.");
        }

        long stop2 = System.currentTimeMillis();

        TetradLogger.getInstance().log("FCIT finished.");
        TetradLogger.getInstance().log("BOSS/GRaSP time: " + (stop1 - start1) + " ms.");
        TetradLogger.getInstance().log("Collider orientation and edge removal time: " + (stop2 - start2) + " ms.");
        TetradLogger.getInstance().log("Total time: " + (stop2 - start1) + " ms.");

        if (guaranteePag) {
            pag = GraphUtils.guaranteePag(pag, fciOrient, knowledge, cpdagColliders, verbose, new HashSet<>());
        }

        return GraphUtils.replaceNodes(pag, nodes);
    }

    private void noteKnownCollidersFromCpdag(List<Node> best, Graph cpdag) {

        // We're looking for unshielded colliders in these next steps that we can detect from the CPDAG.
        // We do this by looking at the structure of the CPDAG implied by the BOSS graph and copying
        // colliders from the BOSS graph into the estimated PAG. This step is justified in the
        // GFCI algorithm. Ogarrio, J. M., Spirtes, P., & Ramsey, J. (2016, August). A hybrid causal search
        // algorithm for latent variable models. In Conference on probabilistic graphical models (pp. 368-379).
        // PMLR.
        for (Node b : best) {
            var adj = pag.getAdjacentNodes(b);

            for (int i = 0; i < adj.size(); i++) {
                for (int j = i + 1; j < adj.size(); j++) {
                    Node x = adj.get(i);
                    Node y = adj.get(j);

                    if (GraphUtils.distinct(x, b, y)) {
                        if (GraphUtils.colliderAllowed(pag, x, b, y, knowledge)) {
                            if (cpdag.isDefCollider(x, b, y)) {
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
    }

    private void refreshGraph() {
        GraphUtils.reorientWithCircles(pag, verbose);
        GraphUtils.recallCollidersFromCpdag(pag, cpdagColliders, knowledge);
        adjustForExtraSepsets();
        fciOrient.fciOrientbk(knowledge, pag, pag.getNodes());
        fciOrient.setUseR4(true);
        fciOrient.finalOrientation(pag);

        if (!pag.paths().isMaximal()) {
            pag = new EdgeListGraph(lastPag);
            cpdagColliders = new HashSet<>(lastKnownColliders);
            extraSepsets = new HashMap<>(lastExtraSepsets);
        } else {
            lastPag = new EdgeListGraph(pag);
            lastKnownColliders = new HashSet<>(cpdagColliders);
            lastExtraSepsets = new HashMap<>(extraSepsets);
        }
    }

    private void adjustForExtraSepsets() {
        extraSepsets.keySet().forEach(edge -> {
            Node x = edge.getNode1();
            Node y = edge.getNode2();

            List<Node> common = pag.getAdjacentNodes(x);
            common.retainAll(pag.getAdjacentNodes(y));

            if (verbose) {
                TetradLogger.getInstance().log("Removed adjacency " + x + " *-* " + y + " from PAG.");
            }

            for (Node node : common) {
                if (!extraSepsets.get(edge).contains(node)) {
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

    // requires faithfulness
    private void checkUnconditionalIndependence() {
        if (verbose) {
            TetradLogger.getInstance().log("Removing extra edges from discriminating paths.");
        }

        fciOrient.finalOrientation(pag);

        pag.getEdges().forEach(edge -> {
            Node x = edge.getNode1();
            Node y = edge.getNode2();

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

                    extraSepsets.put(pag.getEdge(x, y), Set.of());
                    pag.removeEdge(x, y);
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void removeExtraEdgesDdp() {
        if (verbose) {
            TetradLogger.getInstance().log("Removing extra edges from discriminating paths.");
        }

        fciOrient.finalOrientation(pag);

        Set<DiscriminatingPath> discriminatingPaths = FciOrient.listDiscriminatingPaths(pag, maxDdpPathLength, false);

        Map<Set<Node>, Set<DiscriminatingPath>> pathsByEdge = new HashMap<>();

        pag.getEdges().forEach(edge -> {
            Node x = edge.getNode1();
            Node y = edge.getNode2();

            Set<DiscriminatingPath> paths = new HashSet<>();

            for (DiscriminatingPath path : discriminatingPaths) {
                if (path.getX() == x && path.getY() == y) {
                    paths.add(path);
                } else if (path.getX() == y && path.getY() == x) {
                    paths.add(path);
                }
            }

            pathsByEdge.put(Set.of(x, y), paths);
        });

        // Now test the specific extra condition where DDPs colliders would have been oriented had an edge not been
        // there in this graph.
        pag.getEdges().forEach(edge -> {
            if (verbose) {
                TetradLogger.getInstance().log("Considering removing edge " + edge);
            }

            Node x = edge.getNode1();
            Node y = edge.getNode2();

            List<Node> common = pag.getAdjacentNodes(x);
            common.retainAll(pag.getAdjacentNodes(y));

            Set<DiscriminatingPath> paths = pathsByEdge.get(Set.of(x, y));
            Set<Node> perhapsNotFollowed = new HashSet<>();

            if (verbose) {
                TetradLogger.getInstance().log("Discriminating paths for " + x + " and " + y + " are " + paths);
            }

            // Don't repeat the same independence test twice for this edge x *-* y.
            Set<Set<Node>> S = new HashSet<>();

            if (paths == null) {
                return;
            }

            for (DiscriminatingPath path : paths) {
                if (pag.getEndpoint(path.getY(), path.getV()) == Endpoint.CIRCLE) {
                    perhapsNotFollowed.add(path.getV());
                }
            }

            List<Node> E = new ArrayList<>(perhapsNotFollowed);

            int _depth = depth == -1 ? E.size() : depth;
            _depth = Math.min(_depth, E.size());

            // Generate subsets and check blocking paths
            SublistGenerator gen = new SublistGenerator(E.size(), _depth);
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

                int _depth2 = depth == -1 ? common.size() : depth;
                _depth2 = Math.min(_depth2, common.size());

                SublistGenerator gen2 = new SublistGenerator(common.size(), _depth2);
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

                                extraSepsets.put(pag.getEdge(x, y), b);
                                pag.removeEdge(x, y);
                            }
//                            else if (ensureMarkovHelper.markovIndependence(x, y, Set.of())) {
//                                if (verbose) {
//                                    TetradLogger.getInstance().log("Marking " + edge + " for removal because of unconditional independence.");
//                                }
//
//                                extraSepsets.put(pag.getEdge(x, y), Set.of());
//                                pag.removeEdge(x, y);
//                            }
                        }
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        });
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
     * Sets the value indicating whether the process should ensure Markov property.
     *
     * @param ensureMarkov a boolean value, true to ensure Markov property, false otherwise.
     */
    public void setEnsureMarkov(boolean ensureMarkov) {
        this.ensureMarkov = ensureMarkov;
    }

    /**
     * Sets the value of the guaranteePag property.
     *
     * @param guaranteePag a boolean value indicating whether the guaranteePag is enabled or not
     */
    public void setGuaranteePag(boolean guaranteePag) {
        this.guaranteePag = guaranteePag;
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
