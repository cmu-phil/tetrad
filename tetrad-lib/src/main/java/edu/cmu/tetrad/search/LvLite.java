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
import edu.cmu.tetrad.search.utils.FciOrient;
import edu.cmu.tetrad.search.utils.FciOrientDataExaminationStrategyTestBased;
import edu.cmu.tetrad.search.utils.TeyssierScorer;
import edu.cmu.tetrad.util.MillisecondTimes;
import edu.cmu.tetrad.util.TetradLogger;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The LV-Lite algorithm implements a search algorithm for learning the structure of a graphical model from
 * observational data with latent variables. The algorithm uses the BOSS or GRaSP algorithm to obtain an initial CPDAG,
 * then uses scoring steps to infer some unshielded colliders in the graph, then finishes with a testing step to remove
 * extra edges and orient more unshielded colliders. Finally, the final FCI orientation is applied to the graph.
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
     * The background knowledge.
     */
    private Knowledge knowledge = new Knowledge();
    /**
     * The algorithm to use to obtain the initial CPDAG.
     */
    private START_WITH startWith = START_WITH.BOSS;
    /**
     * Flag indicating whether to repair a faulty PAG.
     */
    private boolean repairFaultyPag = false;
    /**
     * The number of starts for GRaSP.
     */
    private int numStarts = 1;
    /**
     * The maximum score drop for tucking.
     */
    private double maxScoreDrop = -1;
    /**
     * The depth of the GRaSP if it is used.
     */
    private int recursionDepth = 10;
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
     * This variable represents whether the discriminating path rule is used in the LV-Lite class.
     * <p>
     * The discriminating path rule is a rule used in the search algorithm. It determines whether the algorithm
     * considers discriminating paths when searching for patterns in the data.
     * <p>
     * By default, the value of this variable is set to true, indicating that the discriminating path rule is used.
     */
    private boolean doDiscriminatingPathTailRule = true;
    /**
     * Indicates whether the discriminating path collider rule is turned on or off.
     * <p>
     * If set to true, the discriminating path collider rule is enabled. If set to false, the discriminating path
     * collider rule is disabled.
     */
    private boolean doDiscriminatingPathColliderRule = true;
    /**
     * True iff verbose output should be printed.
     */
    private boolean verbose = false;
    /**
     * Determines if testing is allowed. Default value is true.
     */
    private boolean ablationLeaveOutTestingStep = false;
    /**
     * The maximum length of any discriminating path.
     */
    private int maxDdpPathLength = -1;
    /**
     * ABLATION: The flag indicating whether to leave out the final orientation.
     */
    private boolean ablationLeaveOutFinalOrientation;

    /**
     * LV-Lite constructor. Initializes a new object of LvLite search algorithm with the given IndependenceTest and
     * Score object.
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

        if (test instanceof MsepTest) {
            this.startWith = START_WITH.GRASP;
        }
    }

    /**
     * Run the search and return s a PAG.
     *
     * @return The PAG.
     */
    public Graph search() {
        List<Node> nodes = new ArrayList<>(this.score.getVariables());

        if (verbose) {
            TetradLogger.getInstance().log("===Starting LV-Lite===");
        }

        List<Node> best;
        Graph dag;

        if (startWith == START_WITH.BOSS) {

            if (verbose) {
                TetradLogger.getInstance().log("Running BOSS...");
            }

            long start = MillisecondTimes.wallTimeMillis();

            var permutationSearch = getBossSearch();
            dag = permutationSearch.search(false);
            best = permutationSearch.getOrder();
            best = dag.paths().getValidOrder(best, true);

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
            dag = grasp.getGraph(false);

            long stop = MillisecondTimes.wallTimeMillis();

            if (verbose) {
                TetradLogger.getInstance().log("GRaSP took " + (stop - start) + " ms.");
            }

            if (verbose) {
                TetradLogger.getInstance().log("Initializing PAG to GRaSP CPDAG.");
                TetradLogger.getInstance().log("Initializing scorer with GRaSP best order.");
            }
        } else {
            throw new IllegalArgumentException("Unknown startWith algorithm: " + startWith);
        }

        if (verbose) {
            TetradLogger.getInstance().log("Best order: " + best);
        }

        var scorer = new TeyssierScorer(test, score);
        scorer.setKnowledge(knowledge);
        double bestScore = scorer.score(best);
        scorer.bookmark();

        // We initialize the estimated PAG to the BOSS/GRaSP CPDAG.
        Graph pag = new EdgeListGraph(dag);


        if (verbose) {
            TetradLogger.getInstance().log("Initializing PAG to BOSS CPDAG.");
            TetradLogger.getInstance().log("Initializing scorer with BOSS best order.");
        }

        FciOrient fciOrient = new FciOrient(FciOrientDataExaminationStrategyTestBased.specialConfiguration(test, knowledge,
                doDiscriminatingPathTailRule, doDiscriminatingPathColliderRule, verbose));
        fciOrient.setMaxPathLength(maxDdpPathLength);
        fciOrient.setCompleteRuleSetUsed(completeRuleSetUsed);

        if (verbose) {
            TetradLogger.getInstance().log("Collider orientation and edge removal.");
        }

        // The main procedure.
        Set<Triple> unshieldedColliders = new HashSet<>();
        Set<Triple> checked = new HashSet<>();

        reorientWithCircles(pag, verbose);

        // We're looking for unshielded colliders in these next steps that we can detect without using only
        // the scorer. We do this by looking at the structure of the DAG implied by the BOSS graph and nearby graphs
        // that can be reached by constrained tucking. The BOSS graph should be edge minimal, so should have the
        // highest number of unshielded colliders to copy to the PAG. Nearby graphs should have fewer unshielded
        // colliders, though like the BOSS graph, they should be Markov, so their unshielded colliders should be
        // valid. From sample, because of unfaithfulness, the quality may fall off depending on the difference in
        // score between the best order and a tucked order.
        for (Node b : best) {
            var adj = pag.getAdjacentNodes(b);

            for (Node x : adj) {
                for (Node y : adj) {
                    if (distinct(x, b, y) && !checked.contains(new Triple(x, b, y))) {
                        checkUntucked(x, b, y, pag, dag, scorer, bestScore, unshieldedColliders, checked);
                    }
                }
            }
        }

        reorientWithCircles(pag, verbose);
        doRequiredOrientations(fciOrient, pag, best, knowledge, false);
        recallUnshieldedTriples(pag, unshieldedColliders, knowledge);

        Map<Edge, Set<Node>> extraSepsets = null;

        if (!ablationLeaveOutTestingStep) {

            // Remove extra edges using a test by examining paths in the BOSS/GRaSP DAG. The goal of this is to find a
            // sufficient set of sepsets to test for extra edges in the PAG that is small, preferably just one test
            // per edge.
            extraSepsets = removeExtraEdges(pag, unshieldedColliders);

            reorientWithCircles(pag, verbose);
            doRequiredOrientations(fciOrient, pag, best, knowledge, verbose);
            recallUnshieldedTriples(pag, unshieldedColliders, knowledge);

            for (Edge edge : extraSepsets.keySet()) {
                orientCommonAdjacents(edge, pag, unshieldedColliders, extraSepsets);
            }
        }

        // Final FCI orientation.
        if (!ablationLeaveOutFinalOrientation) {
            fciOrient.finalOrientation(pag);
        }

        if (repairFaultyPag) {
            GraphUtils.repairFaultyPag(pag, fciOrient, knowledge, unshieldedColliders, verbose, ablationLeaveOutFinalOrientation);
        }

        if (verbose) {
            TetradLogger.getInstance().log("Doing final orientation.");
        }

        if (verbose) {
            TetradLogger.getInstance().log("Finished final orientation.");
        }

        return GraphUtils.replaceNodes(pag, this.score.getVariables());
    }

    /**
     * Try adding an unshielded collider by checking the BOSS/GRaSP DAG.
     *
     * @param x                   Node - The first node.
     * @param b                   Node - The second node.
     * @param y                   Node - The third node.
     * @param pag                 Graph - The graph to operate on.
     * @param scorer              The scorer to use for scoring the colliders.
     * @param bestScore           double - The best score obtained so far.
     * @param unshieldedColliders The set to store unshielded colliders.
     * @param checked             The set to store already checked nodes.
     */
    private void checkUntucked(Node x, Node b, Node y, Graph pag, Graph cpdag, TeyssierScorer scorer, double bestScore,
                               Set<Triple> unshieldedColliders, Set<Triple> checked) {
        tryAddingCollider(x, b, y, pag, cpdag, false, scorer, bestScore, bestScore, unshieldedColliders,
                checked, knowledge, verbose);
    }

    /**
     * Try adding an unshielded collider by projected DAG after tucking.
     *
     * @param x                   The node 'x' of the triple (x, b, y)
     * @param b                   The node 'b' of the triple (x, b, y)
     * @param y                   The node 'y' of the triple (x, b, y)
     * @param pag                 The graph
     * @param scorer              The scorer object
     * @param bestScore           The previous best score
     * @param unshieldedColliders The set of unshielded colliders
     * @param checked             The set of checked triples
     */
    private void checkTucked(Node x, Node b, Node y, Graph pag, TeyssierScorer scorer, double bestScore,
                             Set<Triple> unshieldedColliders, Set<Triple> checked) {
        if (!checked.contains(new Triple(x, b, y))) {
            scorer.tuck(y, b);
            scorer.tuck(x, y);
            double newScore = scorer.score();
            tryAddingCollider(x, b, y, pag, null, true, scorer, newScore, bestScore,
                    unshieldedColliders, checked, knowledge, verbose);
            scorer.goToBookmark();
        }
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
     * Sets the allowable score drop used in the process triples step. Higher bounds may orient more colliders.
     *
     * @param maxScoreDrop the new equality threshold value
     */
    public void setMaxScoreDrop(double maxScoreDrop) {
        if (Double.isNaN(maxScoreDrop) || Double.isInfinite(maxScoreDrop)) {
            throw new IllegalArgumentException("Equality threshold must be a finite number: " + maxScoreDrop);
        }

        if (maxScoreDrop < 0) {
            throw new IllegalArgumentException("Equality threshold must be >= 0: " + maxScoreDrop);
        }

        this.maxScoreDrop = maxScoreDrop;
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
     * Sets whether to repair a faulty PAG.
     *
     * @param repairFaultyPag true if a faulty PAG should be repaired, false otherwise
     */
    public void setRepairFaultyPag(boolean repairFaultyPag) {
        this.repairFaultyPag = repairFaultyPag;
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
     * Sets whether the discriminating path tail rule should be used.
     *
     * @param doDiscriminatingPathTailRule True, if so.
     */
    public void setDoDiscriminatingPathTailRule(boolean doDiscriminatingPathTailRule) {
        this.doDiscriminatingPathTailRule = doDiscriminatingPathTailRule;
    }

    /**
     * Sets whether the discriminating path collider rule should be used.
     *
     * @param doDiscriminatingPathColliderRule True, if so.
     */
    public void setDoDiscriminatingPathColliderRule(boolean doDiscriminatingPathColliderRule) {
        this.doDiscriminatingPathColliderRule = doDiscriminatingPathColliderRule;
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
     * Reorients all edges in a Graph as o-o. This method is used to apply the o-o orientation to all edges in the given
     * Graph following the PAG (Partially Ancestral Graph) structure.
     *
     * @param pag     The Graph to be reoriented.
     * @param verbose A boolean value indicating whether verbose output should be printed.
     */
    private void reorientWithCircles(Graph pag, boolean verbose) {
        if (verbose) {
            TetradLogger.getInstance().log("Orient all edges in PAG as o-o:");
        }
        pag.reorientAllWith(Endpoint.CIRCLE);
    }

    /**
     * Recall unshielded triples in a given graph.
     *
     * @param pag                 The graph to recall unshielded triples from.
     * @param unshieldedColliders The set of unshielded colliders that need to be recalled.
     * @param knowledge           the knowledge object.
     */
    private void recallUnshieldedTriples(Graph pag, Set<Triple> unshieldedColliders, Knowledge knowledge) {
        for (Triple triple : unshieldedColliders) {
            Node x = triple.getX();
            Node b = triple.getY();
            Node y = triple.getZ();

            // We can avoid creating almost cycles here, but this does not solve the problem, as we can still
            // creat almost cycles in final orientation.
            if (colliderAllowed(pag, x, b, y, knowledge) && triple(pag, x, b, y) && !couldCreateAlmostCycle(pag, x, y)) {
                pag.setEndpoint(x, b, Endpoint.ARROW);
                pag.setEndpoint(y, b, Endpoint.ARROW);
                pag.removeEdge(x, y);
            }
        }
    }

    /**
     * Checks if creating an almost cycle between nodes x, b, and y is possible in a given graph.
     *
     * @param pag The graph to check if the almost cycle can be created.
     * @param x   The first node of the almost cycle.
     * @param y   The third node of the almost cycle.
     * @return True if creating the almost cycle is possible, false otherwise.
     */
    private boolean couldCreateAlmostCycle(Graph pag, Node x, Node y) {
        return pag.paths().isAncestorOf(x, y) || pag.paths().isAncestorOf(y, x);
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

        // Note that we can use the MAG here instead of the DAG.
        Graph mag = GraphTransforms.zhangMagFromPag(pag);

        Map<Edge, Set<Node>> extraSepsets = new ConcurrentHashMap<>();

        pag.getEdges().forEach(edge -> {
            Set<Node> sepset = SepsetFinder.getSepsetPathBlockingOutOfX(pag, edge.getNode1(), edge.getNode2(), test,
                    maxBlockingPathLength, depth, true, true, new HashSet<>());

            System.out.println("For edge " + edge + " sepset: " + sepset);

            if (sepset != null) {
                extraSepsets.put(edge, sepset);
            }
        });

        for (Edge _edge : extraSepsets.keySet()) {
            pag.removeEdge(_edge.getNode1(), _edge.getNode2());
            orientCommonAdjacents(_edge, pag, unshieldedColliders, extraSepsets);
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
    private void orientCommonAdjacents(Edge edge, Graph
            pag, Set<Triple> unshieldedColliders, Map<Edge, Set<Node>> extraSepsets) {
        List<Node> common = pag.getAdjacentNodes(edge.getNode1());
        common.retainAll(pag.getAdjacentNodes(edge.getNode2()));

        pag.removeEdge(edge.getNode1(), edge.getNode2());

        for (Node node : common) {
            if (!extraSepsets.get(edge).contains(node)) {
                pag.setEndpoint(edge.getNode1(), node, Endpoint.ARROW);
                pag.setEndpoint(edge.getNode2(), node, Endpoint.ARROW);

                if (verbose) {
                    TetradLogger.getInstance().log("Oriented " + edge.getNode1() + " *-> " + node + " <-* " + edge.getNode2() + " in PAG.");
                }

                unshieldedColliders.add(new Triple(edge.getNode1(), node, edge.getNode2()));
            }
        }
    }

    /**
     * Adds a collider if it's a collider in the current scorer and knowledge permits it in the current PAG.
     *
     * @param x                   The first node of the unshielded collider.
     * @param b                   The second node of the unshielded collider.
     * @param y                   The third node of the unshielded collider.
     * @param pag                 The graph in which to add the unshielded collider.
     * @param tucked              A boolean flag indicating whether the unshielded collider is tucked.
     * @param scorer              The scorer to use for scoring the unshielded collider.
     * @param newScore            The new score of the unshielded collider.
     * @param bestScore           The best score of the unshielded collider.
     * @param unshieldedColliders The set of unshielded colliders to add the new unshielded collider to.
     * @param checked             The set of checked unshielded colliders.
     * @param knowledge           The knowledge object.
     * @param verbose             A boolean flag indicating whether verbose output should be printed.
     */
    private void tryAddingCollider(Node x, Node b, Node y, Graph pag, Graph cpdag, boolean tucked, TeyssierScorer
            scorer,
                                   double newScore, double bestScore, Set<Triple> unshieldedColliders,
                                   Set<Triple> checked, Knowledge knowledge, boolean verbose) {
        if (cpdag != null) {
            if (cpdag.isDefCollider(x, b, y) && !cpdag.isAdjacentTo(x, y)) {
                unshieldedColliders.add(new Triple(x, b, y));
                checked.add(new Triple(x, b, y));

                if (verbose) {
                    if (tucked) {
                        TetradLogger.getInstance().log("AFTER TUCKING copied " + x + " *-> " + b + " <-* " + y + " from CPDAG to PAG.");
                    } else {
                        TetradLogger.getInstance().log("Copied " + x + " *-> " + b + " <-* " + y + " from CPDAG to PAG.");
                    }
                }
            }
        } else if (colliderAllowed(pag, x, b, y, knowledge)) {
            if (scorer.unshieldedCollider(x, b, y) && (maxScoreDrop == -1 || newScore >= bestScore - maxScoreDrop)) {
                unshieldedColliders.add(new Triple(x, b, y));
                checked.add(new Triple(x, b, y));

                if (verbose) {
                    if (tucked) {
                        TetradLogger.getInstance().log("AFTER TUCKING copied " + x + " *-> " + b + " <-* " + y + " from CPDAG to PAG.");
                    } else {
                        TetradLogger.getInstance().log("Copied " + x + " *-> " + b + " <-* " + y + " from CPDAG to PAG.");
                    }
                }
            }
        }
    }

    /**
     * Checks if three nodes are connected in a graph.
     *
     * @param graph the graph to check for connectivity
     * @param a     the first node
     * @param b     the second node
     * @param c     the third node
     * @return {@code true} if all three nodes are connected, {@code false} otherwise
     */
    private boolean triple(Graph graph, Node a, Node b, Node c) {
        return distinct(a, b, c) && graph.isAdjacentTo(a, b) && graph.isAdjacentTo(b, c);
    }

    /**
     * Determines if the collider is allowed.
     *
     * @param pag The Graph representing the PAG.
     * @param x   The Node object representing the first node.
     * @param b   The Node object representing the second node.
     * @param y   The Node object representing the third node.
     * @return true if the collider is allowed, false otherwise.
     */
    private boolean colliderAllowed(Graph pag, Node x, Node b, Node y, Knowledge knowledge) {
        return FciOrient.isArrowheadAllowed(x, b, pag, knowledge) && FciOrient.isArrowheadAllowed(y, b, pag, knowledge);
    }

    /**
     * Orient required edges in PAG.
     *
     * @param fciOrient The FciOrient object used for orienting the edges.
     * @param pag       The Graph representing the PAG.
     * @param best      The list of Node objects representing the best nodes.
     */
    private void doRequiredOrientations(FciOrient fciOrient, Graph pag, List<Node> best, Knowledge knowledge,
                                        boolean verbose) {
        if (verbose) {
            TetradLogger.getInstance().log("Orient required edges in PAG:");
        }

        fciOrient.fciOrientbk(knowledge, pag, best);
    }

    /**
     * Determines whether three {@link Node} objects are distinct.
     *
     * @param x the first Node object
     * @param b the second Node object
     * @param y the third Node object
     * @return true if x, b, and y are distinct; false otherwise
     */
    private boolean distinct(Node x, Node b, Node y) {
        return x != b && y != b && x != y;
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
     * Sets whether testing is allowed or not.
     *
     * @param ablationLeaveOutTestingStep true if testing is allowed, false otherwise
     */
    public void setAblationLeaveOutTestingStep(boolean ablationLeaveOutTestingStep) {
        this.ablationLeaveOutTestingStep = ablationLeaveOutTestingStep;
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
     * ABLATION: Sets whether to leave out the final orientation.
     *
     * @param leaveOutFinalOrientation true if the final orientation should be left out, false otherwise
     */
    public void ablationSetLeaveOutFinalOrientation(boolean leaveOutFinalOrientation) {
        this.ablationLeaveOutFinalOrientation = leaveOutFinalOrientation;
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
        GRASP
    }
}
