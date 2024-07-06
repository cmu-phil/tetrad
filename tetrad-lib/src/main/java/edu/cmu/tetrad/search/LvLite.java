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
import edu.cmu.tetrad.search.utils.TeyssierScorer;
import edu.cmu.tetrad.util.MillisecondTimes;
import edu.cmu.tetrad.util.SublistGenerator;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.*;

/**
 * The LV-Lite algorithm implements the IGraphSearch interface and represents a search algorithm for learning the
 * structure of a graphical model from observational data.
 * <p>
 * This class provides methods for running the search algorithm and getting the learned pattern as a PAG (Partially
 * Annotated Graph).
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
     * The threshold for equality, a fraction of abs(BIC).
     */
    private double allowableScoreDrop = 100;
    /**
     * The depth of the GRaSP if it is used.
     */
    private int recursionDepth = 15;
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
    private boolean verbose;
    private int maxPathLength = 5;

    /**
     * LV-Lite constructor. Initializes a new object of LvLite search algorithm with the given IndependenceTest and
     * Score object.
     *
     * @param test  The IndependenceTest object to be used for testing independence between variables.
     * @param score The Score object to be used for scoring DAGs.
     * @throws NullPointerException if score is null.
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
     * Reorients all edges in a Graph as o-o. This method is used to apply the o-o orientation to all edges in the given
     * Graph following the PAG (Partially Ancestral Graph) structure.
     *
     * @param pag     The Graph to be reoriented.
     * @param verbose A boolean value indicating whether verbose output should be printed.
     */
    public static void reorientWithCircles(Graph pag, boolean verbose) {
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
     * @param verbose             A boolean flag indicating whether verbose output should be printed.
     */
    public static void recallUnshieldedTriples(Graph pag, Set<Triple> unshieldedColliders, Set<Set<Node>> removedEdges,
                                               Knowledge knowledge, boolean verbose) {
        for (Triple triple : unshieldedColliders) {
            Node x = triple.getX();
            Node b = triple.getY();
            Node y = triple.getZ();

            // We can avoid creating almost cycles here, but this does not solve the problem, as we can still
            // creat almost cycles in final orientation.
            if (colliderAllowed(pag, x, b, y, knowledge) && triple(pag, x, b, y) && !createsAlmostCycle(pag, x, b, y)) {
                pag.setEndpoint(x, b, Endpoint.ARROW);
                pag.setEndpoint(y, b, Endpoint.ARROW);
                boolean removed = pag.removeEdge(x, y);

                if (removed) {
                    removedEdges.add(Set.of(x, y));
                }

                if (verbose) {
                    TetradLogger.getInstance().log("Recalled " + x + " *-> " + b + " <-* " + y + " from previous PAG.");

                    if (removed) {
                        TetradLogger.getInstance().log("Removed adjacency " + x + " *-* " + y + " in the PAG.");
                    }
                }
            }
        }
    }

    private static boolean createsAlmostCycle(Graph pag, Node x, Node b, Node y) {
        if (pag.paths().isAncestorOf(x, y) || pag.paths().isAncestorOf(y, x)) {
            return true;
        }

        return false;
    }

    /**
     * Removes extra edges in a graph according to specified conditions.
     *
     * @param pag                 The graph in which to remove extra edges.
     * @param test                The IndependenceTest object used for testing independence between variables.
     * @param maxPathLength       The maximum length of any blocked path.
     * @param unshieldedColliders A set to store the unshielded colliders found during the removal process.
     * @param verbose             A boolean value indicating whether verbose output should be printed.
     */
    public static void removeExtraEdges(Graph pag, IndependenceTest test, int maxPathLength, Set<Triple> unshieldedColliders, boolean verbose) {
        if (verbose) {
            TetradLogger.getInstance().log("Checking larger conditioning sets:");
        }

        Map<Edge, Set<Node>> toRemove = new HashMap<>();

        for (int maxLength = 3; maxLength <= maxPathLength; maxLength++) {
            if (verbose) {
                TetradLogger.getInstance().log("Checking paths of length " + maxLength + ":");
            }

            int _maxPathLength = maxLength;

            pag.getEdges().forEach(edge -> {
                boolean removed = tryRemovingEdge(edge, pag, test, toRemove, _maxPathLength, verbose);

                if (removed) {
                    if (verbose) {
                        TetradLogger.getInstance().log("Removed edge: " + edge);
                    }
                }
            });
        }

        if (verbose) {
            TetradLogger.getInstance().log("Done checking larger conditioning sets.");
        }

        for (Edge edge : toRemove.keySet()) {
            pag.removeEdge(edge.getNode1(), edge.getNode2());
            orientCommonAdjacents(pag, unshieldedColliders, edge, toRemove);
        }

        if (verbose) {
            TetradLogger.getInstance().log("Removed edges: " + toRemove);
        }
    }

    private static void orientCommonAdjacents(Graph pag, Set<Triple> unshieldedColliders, Edge edge, Map<Edge, Set<Node>> toRemove) {
        List<Node> common = pag.getAdjacentNodes(edge.getNode1());
        common.retainAll(pag.getAdjacentNodes(edge.getNode2()));

        for (Node node : common) {
            if (!toRemove.get(edge).contains(node)) {
                unshieldedColliders.add(new Triple(edge.getNode1(), node, edge.getNode2()));
            }
        }
    }

    private static boolean tryRemovingEdge(Edge edge, Graph pag, IndependenceTest test, Map<Edge, Set<Node>> toRemove, int maxPathLength,
                                           boolean verbose) {
        test.setVerbose(verbose);

        TetradLogger.getInstance().log("### Checking edge: " + edge);

        Node x = edge.getNode1();
        Node y = edge.getNode2();

        if (x.getName().equals("X4") && y.getName().equals("X19")) {
            System.out.println("X4 -> X19");
        }

        // This is the set of all possible conditioning variables, though note below.
        Set<Node> defNoncolliders = new HashSet<>();

        // These guys could either hide colliders or not, so we need to consider either conditioning on them or not.
        // These are elements of possibleConditioningVariables, but we need to consider the Cartesian product where we either
        // include these variables in the conditioning set for the test or not.
        Set<Node> couldBeNoncolliders = new HashSet<>();
        List<List<Node>> paths;
        Set<Node> alreadyAdded = new HashSet<>();

        while (true) {
            paths = pag.paths().allPaths(x, y, maxPathLength, defNoncolliders, true);
            boolean changed = false;
            boolean allBlocked = true;

            for (List<Node> path : paths) {
                if (!pag.paths().isMConnectingPath(path, alreadyAdded, true)) {
                    continue;
                }

                boolean blocked = false;

                for (int i = 1; i < path.size() - 1; i++) {
                    Node z1 = path.get(i - 1);
                    Node z2 = path.get(i);
                    Node z3 = path.get(i + 1);

                    if (alreadyAdded.contains(z2)) {
                        continue;
                    }

                    if (!pag.isDefCollider(z1, z2, z3)) {
                        if (pag.isDefNoncollider(z1, z2, z3)) {
                            defNoncolliders.add(z2);
                            alreadyAdded.add(z2);
                            blocked = true;
                        } else {
                            if (path.size() - 1 == 2) {
//                                couldBeNoncolliders.add(z2);
//                                alreadyAdded.add(z2);
//                                blocked = true;

                                if (pag.getEndpoint(z1, z2) == Endpoint.CIRCLE && pag.getEndpoint(z3, z2) == Endpoint.CIRCLE) {
                                    couldBeNoncolliders.add(z2);
                                    alreadyAdded.add(z2);
                                    blocked = true;
                                }

                                if (pag.getEndpoint(z1, z2) == Endpoint.ARROW && pag.getEndpoint(z3, z2) == Endpoint.CIRCLE) {
                                    couldBeNoncolliders.add(z2);
                                    alreadyAdded.add(z2);
                                    blocked = true;
                                }

                                if (pag.getEndpoint(z1, z2) == Endpoint.CIRCLE && pag.getEndpoint(z3, z2) == Endpoint.ARROW) {
                                    couldBeNoncolliders.add(z2);
                                    alreadyAdded.add(z2);
                                    blocked = true;
                                }
                            }
                        }
                    }

                    if (path.size() - 1 > 1 && blocked) {
                        changed = true;
                    }
                }

                if (path.size() - 1 > 1 && !blocked) {
                    allBlocked = false;
                }
            }

            if (!allBlocked) {
                return false;
            }

            if (!changed) break;
        }

        if (verbose) {
            TetradLogger.getInstance().log("Checking independence for " + edge + " given " + defNoncolliders);
            TetradLogger.getInstance().log("Uncovered defNoncolliders for paths of length 2: " + couldBeNoncolliders);
        }

        List<Node> couldBeCollidersList = new ArrayList<>(couldBeNoncolliders);

        SublistGenerator generator = new SublistGenerator(couldBeCollidersList.size(), couldBeCollidersList.size());
        int[] choice;

        while ((choice = generator.next()) != null) {
            Set<Node> conditioningSet = new HashSet<>();

            for (int j : choice) {
                conditioningSet.add(couldBeCollidersList.get(j));
            }

            conditioningSet.addAll(defNoncolliders);

            if (verbose) {
                TetradLogger.getInstance().log("TESTING " + x + " _||_ " + y + " | " + conditioningSet);
            }

            if (test.checkIndependence(x, y, conditioningSet).isIndependent()) {
                toRemove.put(edge, conditioningSet);
                return true;
            }
        }

        return false;
    }

    private static void addCollider(Node x, Node b, Node y, Graph pag, boolean tucked,
                                    TeyssierScorer scorer, double newScore, double bestScore,
                                    double maxScoreDrop, Set<Triple> unshieldedColliders, Set<Triple> tested,
                                    Knowledge knowledge, boolean verbose) {
        if (colliderAllowed(pag, x, b, y, knowledge)) {
            if (scorer.unshieldedCollider(x, b, y) && newScore >= bestScore - maxScoreDrop) {
                unshieldedColliders.add(new Triple(x, b, y));
                tested.add(new Triple(x, b, y));

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
    private static boolean triple(Graph graph, Node a, Node b, Node c) {
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
    private static boolean colliderAllowed(Graph pag, Node x, Node b, Node y, Knowledge knowledge) {
        return FciOrient.isArrowheadAllowed(x, b, pag, knowledge) && FciOrient.isArrowheadAllowed(y, b, pag, knowledge);
    }

    /**
     * Orient required edges in PAG.
     *
     * @param fciOrient The FciOrient object used for orienting the edges.
     * @param pag       The Graph representing the PAG.
     * @param best      The list of Node objects representing the best nodes.
     */
    private static void doRequiredOrientations(FciOrient fciOrient, Graph pag, List<Node> best, Knowledge knowledge, boolean verbose) {
        if (verbose) {
            TetradLogger.getInstance().log("Orient required edges in PAG:");
        }

        fciOrient.fciOrientbk(knowledge, pag, best);
    }

    private static boolean distinct(Node x, Node b, Node y) {
        return x != b && y != b && x != y;
    }

    /**
     * Sets the maximum length of any discriminating path.
     *
     * @param maxPathLength the maximum length of any discriminating path, or -1 if unlimited.
     */
    public void setMaxPathLength(int maxPathLength) {
        if (maxPathLength < -1) {
            throw new IllegalArgumentException("Max path length must be -1 (unlimited) or >= 0: " + maxPathLength);
        }

        this.maxPathLength = maxPathLength;
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

        // BOSS seems to be doing better here.
        if (startWith == START_WITH.BOSS) {

            if (verbose) {
                TetradLogger.getInstance().log("Running BOSS...");
            }

            long start = MillisecondTimes.wallTimeMillis();

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
            best = permutationSearch.getOrder();

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

            edu.cmu.tetrad.search.Grasp grasp = new edu.cmu.tetrad.search.Grasp(test, score);

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
            best = grasp.bestOrder(nodes);
            grasp.getGraph(true);

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

        scorer.setUseScore(true);
        scorer.setKnowledge(knowledge);

        scorer.score(best);
        double bestScore = scorer.score(best);
        scorer.bookmark();

        Graph pag = new EdgeListGraph(scorer.getGraph(true));

        if (verbose) {
            TetradLogger.getInstance().log("Initializing PAG to BOSS CPDAG.");
            TetradLogger.getInstance().log("Initializing scorer with BOSS best order.");
        }

        scorer.score(best);
        scorer.bookmark();

        FciOrient fciOrient = new FciOrient(scorer);
        fciOrient.setCompleteRuleSetUsed(completeRuleSetUsed);
        fciOrient.setDoDiscriminatingPathColliderRule(doDiscriminatingPathColliderRule);
        fciOrient.setDoDiscriminatingPathTailRule(doDiscriminatingPathTailRule);
        fciOrient.setMaxPathLength(-1);
        fciOrient.setKnowledge(knowledge);
        fciOrient.setVerbose(verbose);

        if (verbose) {
            TetradLogger.getInstance().log("Collider orientation and edge removal.");
        }

        // The main procedure.
        Set<Triple> unshieldedColliders = new HashSet<>();
        Set<Triple> tested = new HashSet<>();
        Set<Triple> _unshieldedColliders;

        reorientWithCircles(pag, verbose);

        // We're just looking for unshielded colliders in these next steps that we can detect without doing any tests.
        // We do this by looking at the structure of the DAG implied by the BOSS graph and nearby graphs that can
        // be reached by constrained tucking. The BOSS graph should be edge minimal, so should have the highest
        // number of unshielded colliders to copy to the PAG. Nearby graphs should have fewer unshielded colliders,
        // though like the BOSS graph, they should be Markov, so their unshielded colliders should be valid.
        for (Node b : best) {
            var adj = pag.getAdjacentNodes(b);

            for (Node x : adj) {
                for (Node y : adj) {
                    if (distinct(x, b, y)) {
                        addCollider(x, b, y, pag, false, scorer, bestScore, bestScore, this.allowableScoreDrop, unshieldedColliders, tested, knowledge, verbose);
                    }
                }
            }
        }

        Set<Set<Node>> removedEdges = new HashSet<>();

        do {
            _unshieldedColliders = new HashSet<>(unshieldedColliders);

            for (Node b : best) {
                var adj = pag.getAdjacentNodes(b);

                for (Node x : adj) {
                    for (Node y : adj) {
                        if (distinct(x, b, y) && !tested.contains(new Triple(x, b, y))) {
                            scorer.tuck(y, b);
                            scorer.tuck(x, y);
                            double newScore = scorer.score();
                            addCollider(x, b, y, pag, true, scorer, newScore, bestScore, this.allowableScoreDrop, unshieldedColliders, tested, knowledge, verbose);
                            scorer.goToBookmark();
                        }
                    }
                }
            }

            reorientWithCircles(pag, verbose);
            recallUnshieldedTriples(pag, unshieldedColliders, removedEdges, knowledge, verbose);
        } while (!unshieldedColliders.equals(_unshieldedColliders));

        // Now we have all the unshielded colliders we can find without doing any tests. Heuristically, we now
        // make a PAG to return by copying the unshielded colliders to the PAG and doing final orientation. This
        // produces a PAG that is Markov equivalent to the true graph, but not necessarily edge minimal. The
        // reason is that all the edges removed were removed for correct reasons, and the orientations
        // that were done for correct reasons. The only thing that might be wrong is that we might have missed
        // some unshielded colliders that we could have detected with a test. But the independencies in the graph
        // are correct, so the graph is Markov equivalent to the true graph.
        //
        // To find a minimal PAG, we would need to add a testing step to detect unshielded colliders that we
        // missed. This would be done by testing for independence of X and Y given adjacents of X or Y in
        // the PAG. If X and Y are independent given some set of adjacents in the PAG, then we can remove
        // the edge X *-* Y from the PAG. In this case, we may be able to go back and test whether new unshielded
        // colliders can then be oriented in the PAG. Even this step possibly leaves some edge removals on the
        // table, because we might have missed some unshielded colliders that we could have detected with a
        // possible dsep test. These testing steps are expensive, though, and inaccurate, so until we can find
        // a better way to do them, we will leave them out.
        reorientWithCircles(pag, verbose);
        doRequiredOrientations(fciOrient, pag, best, knowledge, verbose);
        recallUnshieldedTriples(pag, unshieldedColliders, removedEdges, knowledge, verbose);
        fciOrient.zhangFinalOrientation(pag);

//        if (test instanceof MsepTest || test.getAlpha() > 0) {
//            removeExtraEdges(pag, test, maxPathLength, unshieldedColliders, verbose);
//            reorientWithCircles(pag, verbose);
//            doRequiredOrientations(fciOrient, pag, best, knowledge, verbose);
//            recallUnshieldedTriples(pag, unshieldedColliders, removedEdges, knowledge, verbose);
//        }

        if (repairFaultyPag) {
            GraphUtils.repairFaultyPag(pag, fciOrient, knowledge, verbose);
        }

        return GraphUtils.replaceNodes(pag, this.score.getVariables());
    }

    /**
     * Sets the allowable score drop used in the process triples step. A higher bound may orient more colliders.
     *
     * @param allowableScoreDrop the new equality threshold value
     */
    public void setAllowableScoreDrop(double allowableScoreDrop) {
        if (Double.isNaN(allowableScoreDrop) || Double.isInfinite(allowableScoreDrop)) {
            throw new IllegalArgumentException("Equality threshold must be a finite number: " + allowableScoreDrop);
        }

        if (allowableScoreDrop < 0) {
            throw new IllegalArgumentException("Equality threshold must be >= 0: " + allowableScoreDrop);
        }

        this.allowableScoreDrop = allowableScoreDrop;
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
