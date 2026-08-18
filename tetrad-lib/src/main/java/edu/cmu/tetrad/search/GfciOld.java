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
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.utils.FciOrient;
import edu.cmu.tetrad.search.utils.R0R4StrategyTestBased;
import edu.cmu.tetrad.search.utils.SepsetMap;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.SublistGenerator;
import edu.cmu.tetrad.util.TetradLogger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.PrintStream;
import java.util.*;

import static edu.cmu.tetrad.graph.GraphUtils.colliderAllowed;

/**
 * Implements the GFCI (Greedy Fast Causal Inference) algorithm as described in:
 * <p>
 * Ogarrio, J. M., Spirtes, P., &amp; Ramsey, J. (2016, August). A hybrid causal search algorithm for latent variable
 * models. In Conference on probabilistic graphical models (pp. 368-379). PMLR.
 * <p>
 * This is a self-contained legacy implementation retained for compatibility and reproducibility with the original
 * algorithm. For a more modular alternative that participates in the *-FCI framework, see {@link Gfci}.
 * <p>
 * The algorithm proceeds in three phases. First, FGES is run to produce a Markov CPDAG. Second, an extra edge
 * removal step tests edges in the CPDAG for conditional independence, followed by a possible d-sep removal step
 * that searches a broader candidate separating set. Third, colliders from the CPDAG are copied into the working
 * PAG, additional colliders are oriented using the sepsets found during edge removal, and the FCI final orientation
 * rules are applied. Collider orientation in the working PAG follows steps C'/F' of the GFCI paper: the triple
 * must be unshielded in the working graph (arrowheads in a PAG assert marks invariant across the Markov
 * equivalence class, and only unshielded colliders carry that invariance), and CPDAG colliders are copied only
 * at triples also unshielded in the CPDAG -- triples shielded in the CPDAG are adjudicated by the recorded
 * sepset instead, since FGES oriented them in the presence of an edge later judged spurious.
 * <p>
 * This class is configured to respect knowledge of forbidden and required edges, including knowledge of temporal
 * tiers.
 *
 * @author Juan Miguel Ogarrio
 * @author peterspirtes
 * @author josephramsey
 * @author bryanandrews
 * @see Gfci
 * @see Fges
 * @see Knowledge
 */
public class GfciOld implements IGraphSearch {
    /**
     * The independence test used in search.
     */
    private final IndependenceTest independenceTest;
    /**
     * The score used for FGES.
     */
    private final Score score;
    /**
     * The knowledge used in search.
     */
    private Knowledge knowledge = new Knowledge();
    /**
     * Whether Zhang's complete rules are used.
     */
    private boolean completeRuleSetUsed = true;
    /**
     * The maximum path length for the discriminating path rule.
     */
    private int maxDiscriminatingPathLength = -1;
    /**
     * The depth for independence testing.
     */
    private int depth = -1;
    /**
     * Whether verbose output should be printed.
     */
    private boolean verbose = false;
    /**
     * Whether to guarantee the output is a PAG by repairing a faulty PAG.
     */
    private boolean guaranteePag = false;
    /**
     * The maximum degree of the output graph.
     */
    private int maxDegree = -1;
    /**
     * The print stream used for output.
     */
    private transient PrintStream out = System.out;
    /**
     * Whether one-edge faithfulness is assumed.
     */
    private boolean faithfulnessAssumed = true;
    /**
     * The number of threads to use in the search. Must be at least 1.
     */
    private int numThreads = 1;

    /**
     * A flag indicating whether the algorithm should start its search from a complete undirected graph.
     * <p>
     * If set to true, the Star-FCI algorithm initializes the search with a complete graph where every node is connected
     * with an undirected edge. If set to false, the algorithm starts the search with an alternative initial graph, such
     * as a learned or predefined CPDAG.
     * <p>
     * This option impacts the structure of the initial graph and may influence the overall search process and results.
     */
    private boolean startFromCompleteGraph;
    /**
     * A flag indicating whether the maximum p-value should be used for selecting separating sets or directing edges in
     * the algorithm.
     * <p>
     * When set to {@code true}, the algorithm prioritizes separating sets or edges based on the highest p-value
     * encountered. This may impact the behavior of independence tests or edge direction decisions during the search
     * process.
     */
    private boolean useMaxP;
    private boolean excludeSelectionBias = false;

    /**
     * Constructs a new GFci algorithm with the given independence test and score.
     *
     * @param test  The independence test to use.
     * @param score The score to use.
     */
    public GfciOld(IndependenceTest test, Score score) {
        this.independenceTest = test;
        this.score = score;
    }

    /**
     * Finds a separating set that is a subset of the adjacency of nodes x or y in the input graph.
     *
     * @param graph      The graph being analyzed.
     * @param x          The first node between which independence is checked.
     * @param y          The second node between which independence is checked.
     * @param containing A set of nodes that must be included in the separating set.
     * @param test       The independence test used to evaluate separation.
     * @param depth      The maximum size of subsets to be tested for independence.
     * @param order      An optional list specifying the order of nodes for additional constraints.
     * @param useMaxP    True if the maxP method should be used.
     * @return A separating set of nodes (if found) that is a subset of the adjacency of x or y, or {@code null} if no
     * such set is found.
     */
    public static Set<Node> sepsetSubsetOfAdjxOrAdjy(Graph graph, Node x, Node y, Set<Node> containing,
                                                     IndependenceTest test, int depth, List<Node> order, boolean useMaxP) {

        // We need to look at the original adjx and adjy, not some modified version.
        List<Node> adjx = graph.getAdjacentNodes(x);
        List<Node> adjy = graph.getAdjacentNodes(y);
        adjx.remove(y);
        adjy.remove(x);

        adjx.removeIf(node -> node.getNodeType() == NodeType.LATENT);
        adjy.removeIf(node -> node.getNodeType() == NodeType.LATENT);

        Set<Node> sepset1 = getSepset(x, y, containing, test, depth, order, adjx, useMaxP);
        Set<Node> sepset2 = getSepset(y, x, containing, test, depth, order, adjy, useMaxP);

        if (sepset1 == null && sepset2 == null) {
            return null;
        }

        if (sepset1 != null && sepset2 == null) {
            return sepset1;
        }

        if (sepset1 == null) {
            return sepset2;
        }

        // Direction-aware: larger strength = stronger independence for every kind of test.
        double s1 = independenceStrength(x, y, sepset1, test);
        double s2 = independenceStrength(x, y, sepset2, test);

        return s1 > s2 ? sepset1 : sepset2;
    }

    /**
     * Finds a separating set between nodes x and y that satisfies certain conditions, including containing a specified
     * set of nodes, maintaining optional ordering constraints, and ensuring the independence between x and y with
     * respect to the separating set.
     * <p>
     * The separating set is constructed from the adjacency list of node x.
     *
     * @param x          The first node between which independence is being checked.
     * @param y          The second node between which independence is being checked.
     * @param containing A set of nodes that must be included in the separating set.
     * @param test       The independence test used to evaluate separation between x and y.
     * @param depth      The maximum size of subsets to be tested for independence.
     * @param order      An optional list specifying the processing order of nodes, used to enforce additional
     *                   constraints during the search.
     * @param adjx       The adjacency list of node x, from which subsets are generated to test for separation.
     * @return A separating set of nodes that fulfills all constraints and is a subset of adjx, or {@code null} if no
     * such set is found.
     */
    private static @Nullable Set<Node> getSepset(Node x, Node y, Set<Node> containing, IndependenceTest test, int depth,
                                                 List<Node> order, List<Node> adjx, boolean useMaxP) {
        List<Set<Node>> choices = getChoices(adjx, depth);

        if (useMaxP) {
            // Max p for stability...
            return choices.parallelStream()
                    .filter(set -> independenceHolds(x, y, set, test))   // keep only separating sets
                    .max(Comparator.comparingDouble(set -> independenceStrength(x, y, set, test)))
                    .orElse(null); // Return the STRONGEST separating set, or null if there is none
        } else { // Greedy

            // Parallelize processing for adjx
            // Generate combinations in parallel
            // Filter combinations that don't contain 'containing'
            return choices.parallelStream() // Generate combinations in parallel
                    .filter(subset -> subset.containsAll(containing)) // Filter combinations that don't contain 'containing'
                    .filter(subset -> {
                        try {
                            return test.checkIndependence(x, y, subset).isIndependent();
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }).findFirst().orElse(null);
        }
    }

    private static double computeScore(Node x, Node y, Set<Node> set, IndependenceTest test) {
        try {
            return test.checkIndependence(x, y, set).getPValue();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Strength of the evidence for x _||_ y | set, on a scale where LARGER always means stronger independence,
     * for both genuine hypothesis tests and scores wrapped as tests. A score-based test reports a score
     * difference that is negative for independence, so its value is negated here; see
     * {@link IndependenceTest#isPValueAProbability()}. Ranking candidate sepsets by the raw reported value
     * instead selects the WEAKEST separating set whenever the test is score-based.
     */
    private static double independenceStrength(Node x, Node y, Set<Node> set, IndependenceTest test) {
        try {
            IndependenceResult result = test.checkIndependence(x, y, set);
            return test.isPValueAProbability() ? result.getPValue() : -result.getScore();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Whether x _||_ y | set holds according to the test. Correct for every test, unlike comparing the reported
     * p-value against test.getAlpha(), which is meaningless for a test that does not test at a level (a score
     * wrapped as a test reports alpha = -1).
     */
    private static boolean independenceHolds(Node x, Node y, Set<Node> set, IndependenceTest test) {
        try {
            return test.checkIndependence(x, y, set).isIndependent();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * Generates a list of all possible choices for sublists from the adjacency list with sizes up to the given depth
     * using combinations.
     *
     * @param adjx  The adjacency list of nodes to generate combinations from.
     * @param depth The maximum size of the sublists to be generated. If the depth is negative or exceeds the size of
     *              the adjacency list, it will be adjusted to the size of the adjacency list.
     * @return A list of all possible lists of integers representing combinations of indices from the adjacency list up
     * to the given depth.
     */
    private static @NotNull List<Set<Node>> getChoices(List<Node> adjx, int depth) {
        List<Set<Node>> choices = new ArrayList<>();

        if (depth < 0 || depth > adjx.size()) depth = adjx.size();

        SublistGenerator cg = new SublistGenerator(adjx.size(), depth);
        int[] choice;

        while ((choice = cg.next()) != null) {
            choices.add(GraphUtils.asSet(choice, adjx));
        }

        return choices;
    }

    /**
     * Runs the graph and returns the search PAG.
     *
     * @return This PAG.
     * @throws InterruptedException if any
     */
    public Graph search() throws InterruptedException {
        this.independenceTest.setVerbose(verbose);
        List<Node> nodes = new ArrayList<>(getIndependenceTest().getVariables());

        Graph cpdag;

        if (startFromCompleteGraph) {
            TetradLogger.getInstance().log("===Starting with complete graph=== ");
            cpdag = new EdgeListGraph(independenceTest.getVariables());
            cpdag = GraphUtils.completeGraph(cpdag);
        } else {
            cpdag = getMarkovCpdag();
        }

        Graph pag = GraphFactoryUtil.newGraph(cpdag);

        Set<Triple> unshieldedColliders = new HashSet<>();

        SepsetMap sepsetMap = new SepsetMap();

        if (verbose) {
            TetradLogger.getInstance().log("Starting *-FCI extra edge removal step.");
        }

        for (Edge edge : new ArrayList<>(pag.getEdges())) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            Node a = edge.getNode1();
            Node c = edge.getNode2();

            Set<Node> sepset = sepsetSubsetOfAdjxOrAdjy(pag, a, c, new HashSet<>(), independenceTest, depth, null, useMaxP);

            if (sepset != null) {
                pag.removeEdge(a, c);
                sepsetMap.set(a, c, sepset);

                List<Node> adj = pag.getAdjacentNodes(a);
                adj.retainAll(pag.getAdjacentNodes(c));

                if (verbose) {
                    IndependenceResult result = independenceTest.checkIndependence(a, c, sepset);
                    double pValue = result.getPValue();
                    TetradLogger.getInstance().log("Removed edge " + a + " -- " + c + " in extra-edge removal step; sepset = "
                            + sepset + ", p-value = " + pValue + ".");
                }
            }
        }

        if (verbose) {
            TetradLogger.getInstance().log("Starting *-FCI-R0.");
        }

        pag.reorientAllWith(Endpoint.CIRCLE);


        R0R4StrategyTestBased strategy = (R0R4StrategyTestBased) R0R4StrategyTestBased.specialConfiguration(independenceTest, knowledge, verbose);
        strategy.setDepth(-1);
        strategy.setMaxLength(-1);
        strategy.setBlockingType(R0R4StrategyTestBased.BlockingType.GREEDY);
        FciOrient fciOrient = new FciOrient(strategy);
        fciOrient.setCompleteRuleSetUsed(completeRuleSetUsed);
        fciOrient.setRecursiveDepth(-1);
        fciOrient.setMaxDiscriminatingPathLength(maxDiscriminatingPathLength);
        fciOrient.setVerbose(verbose);

        fciOrient.fciOrientbk(knowledge, pag, pag.getNodes(), excludeSelectionBias);

        for (Node y : nodes) {
            List<Node> adjacentNodes = new ArrayList<>(pag.getAdjacentNodes(y));

            ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                Node x = adjacentNodes.get(combination[0]);
                Node z = adjacentNodes.get(combination[1]);

                if (cpdag.isDefCollider(x, y, z) && !cpdag.isAdjacentTo(x, z)) {

                    // Step C' of the GFCI paper (Ogarrio et al., 2016): orient <x, y, z>, unshielded in the
                    // working graph, as a collider if it is an UNSHIELDED collider in the CPDAG. Both conjuncts
                    // matter. Unshielded in the working graph: arrowheads in a PAG assert marks invariant across
                    // the Markov equivalence class, and only unshielded colliders carry that invariance (this is
                    // implied by CPDAG-unshieldedness, since working-graph adjacencies are a subset of CPDAG
                    // adjacencies, but is kept explicit as the rule's stated precondition). Unshielded in the
                    // CPDAG: a collider FGES oriented at a triple shielded in the CPDAG was oriented in the
                    // presence of the x--z edge; if that edge is later removed as spurious, the orientation is
                    // no longer trustworthy, and the triple must instead be adjudicated by the sepset test in
                    // the branch below.
                    if (!pag.isAdjacentTo(x, z) && colliderAllowed(pag, x, y, z, knowledge)) {
                        pag.setEndpoint(x, y, Endpoint.ARROW);
                        pag.setEndpoint(z, y, Endpoint.ARROW);
                        unshieldedColliders.add(new Triple(x, y, z));

                        if (verbose) {
                            TetradLogger.getInstance().log("Copied collider " + x + " *-> " + y + " <-* " + z + " from CPDAG.");
                        }
                    }
                } else if (cpdag.isAdjacentTo(x, z)) {
                    Set<Node> sepset = sepsetMap.get(x, z);

                    if (sepset != null && !sepset.contains(y)) {

                        // Unshieldedness in the working graph is a precondition of orientation itself (GFCI R0'),
                        // not merely of the unshieldedColliders bookkeeping. (A sepset entry currently implies the
                        // edge was removed, so this guard is expected to hold; enforcing it makes the invariant
                        // explicit rather than accidental.)
                        if (!pag.isAdjacentTo(x, z) && colliderAllowed(pag, x, y, z, knowledge)) {
                            pag.setEndpoint(x, y, Endpoint.ARROW);
                            pag.setEndpoint(z, y, Endpoint.ARROW);
                            unshieldedColliders.add(new Triple(x, y, z));

                            if (verbose) {
                                TetradLogger.getInstance().log("Oriented collider by separating set: " + x + " *-> " + y + " <-* " + z);
                            }
                        }
                    }
                }
            }
        }

        // Possible d-sep removal
        for (Edge edge : pag.getEdges()) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            Node a = edge.getNode1();
            Node c = edge.getNode2();

            List<Node> possibleDsep = pag.paths().possibleDsep(a, -1);
            possibleDsep.remove(a);
            possibleDsep.remove(c);
            Set<Node> sepset = getSepset(a, c, new HashSet<>(), independenceTest, depth, null, possibleDsep, useMaxP);

            if (sepset != null) {
                pag.removeEdge(a, c);
                sepsetMap.set(a, c, sepset);

                if (verbose) {
                    IndependenceResult result = independenceTest.checkIndependence(a, c, sepset);
                    double pValue = result.getPValue();
                    TetradLogger.getInstance().log("Removed edge " + a + " -- " + c + " in extra-edge removal step; sepset = "
                            + sepset + ", p-value = " + pValue + ".");
                }
            }

            if (pag.isAdjacentTo(a, c)) {
                possibleDsep = pag.paths().possibleDsep(c, -1);
                possibleDsep.remove(a);
                possibleDsep.remove(c);
                sepset = getSepset(a, c, new HashSet<>(), independenceTest, depth, null, possibleDsep, useMaxP);

                if (sepset != null) {
                    pag.removeEdge(a, c);
                    sepsetMap.set(a, c, sepset);

                    if (verbose) {
                        IndependenceResult result = independenceTest.checkIndependence(a, c, sepset);
                        double pValue = result.getPValue();
                        TetradLogger.getInstance().log("Removed edge " + a + " -- " + c + " in extra-edge removal step; sepset = "
                                + sepset + ", p-value = " + pValue + ".");
                    }
                }
            }
        }

        // As in FCI (Spirtes et al.), re-orient from scratch after the possible d-sep removal step
        // (step E of the GFCI paper: unorient all edges that remain). A collider stamped in the first
        // sweep may have had a leg removed as spurious by the possible d-sep step; the arrowhead on
        // the surviving leg is then unjustified residue, since the collider inference required both
        // legs to be genuine adjacencies. Blanking to circles and re-deriving unshielded colliders
        // below (from the CPDAG and the recorded sepsets, which now include the
        // possible-d-sep-phase sepsets) removes such residue before the final rules run. First-sweep
        // marks exist to support the Possible-D-SEP computation and are not carried forward;
        // unshieldedColliders is likewise rebuilt so the set handed to guaranteePag reflects only
        // colliders justified in the final graph.
        pag.reorientAllWith(Endpoint.CIRCLE);
        fciOrient.fciOrientbk(knowledge, pag, pag.getNodes(), excludeSelectionBias);
        unshieldedColliders.clear();

        for (Node y : nodes) {
            List<Node> adjacentNodes = new ArrayList<>(pag.getAdjacentNodes(y));

            ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                Node x = adjacentNodes.get(combination[0]);
                Node z = adjacentNodes.get(combination[1]);

                if (cpdag.isDefCollider(x, y, z) && !cpdag.isAdjacentTo(x, z)) {

                    // Step F' of the GFCI paper: same rule as step C' -- see the comment on the first sweep.
                    // Unshielded colliders in the CPDAG are copied; colliders at triples shielded in the CPDAG
                    // are adjudicated by the sepset test in the branch below. Triples whose x--z shield was
                    // removed by the possible d-sep step become orientable here.
                    if (!pag.isAdjacentTo(x, z) && colliderAllowed(pag, x, y, z, knowledge)) {
                        pag.setEndpoint(x, y, Endpoint.ARROW);
                        pag.setEndpoint(z, y, Endpoint.ARROW);
                        unshieldedColliders.add(new Triple(x, y, z));

                        if (verbose) {
                            TetradLogger.getInstance().log("Copied collider " + x + " *-> " + y + " <-* " + z + " from CPDAG.");
                        }
                    }
                } else if (cpdag.isAdjacentTo(x, z)) {
                    Set<Node> sepset = sepsetMap.get(x, z);

                    if (sepset != null && !sepset.contains(y)) {

                        // As in the first sweep: unshieldedness in the working graph is a precondition of
                        // orientation itself (GFCI R0'), not merely of the unshieldedColliders bookkeeping.
                        if (!pag.isAdjacentTo(x, z) && colliderAllowed(pag, x, y, z, knowledge)) {
                            pag.setEndpoint(x, y, Endpoint.ARROW);
                            pag.setEndpoint(z, y, Endpoint.ARROW);
                            unshieldedColliders.add(new Triple(x, y, z));

                            if (verbose) {
                                TetradLogger.getInstance().log("Oriented collider by separating set: " + x + " *-> " + y + " <-* " + z);
                            }
                        }
                    }
                }
            }
        }


        if (verbose) {
            TetradLogger.getInstance().log("Starting final FCI orientation.");
        }

        fciOrient.finalOrientation(pag, excludeSelectionBias);

        if (verbose) {
            TetradLogger.getInstance().log("Finished implied orientation.");
        }

        if (guaranteePag) {
            pag = GraphUtils.guaranteePag(pag, fciOrient, knowledge, unshieldedColliders, verbose, new HashSet<>(), excludeSelectionBias,
                    Integer.MAX_VALUE);
        }

//        GraphUtils.applyForbiddenCircleResolution(pag, knowledge);

        if (verbose) {
            TetradLogger.getInstance().log("GFCI finished.");
        }

        return pag;
    }

    /**
     * Returns the knowledge used in search.
     *
     * @return This knowledge
     */
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * Sets the knowledge to use in search.
     *
     * @param knowledge This knowledge.
     */
    public void setKnowledge(Knowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException();
        }

        this.knowledge = knowledge;
    }

    /**
     * Sets whether Zhang's complete rules are used.
     *
     * @param completeRuleSetUsed set to true if Zhang's complete rule set should be used, false if only R1-R4 (the rule
     *                            set of the original FCI) should be used. True by default.
     */
    public void setCompleteRuleSetUsed(boolean completeRuleSetUsed) {
        this.completeRuleSetUsed = completeRuleSetUsed;
    }

    /**
     * Sets the maximum length of any discriminating path.
     *
     * @param maxDiscriminatingPathLength the maximum length of any discriminating path, or -1 if unlimited.
     */
    public void setMaxDiscriminatingPathLength(int maxDiscriminatingPathLength) {
        if (maxDiscriminatingPathLength < -1) {
            throw new IllegalArgumentException("Max path length must be -1 (unlimited) or >= 0: " + maxDiscriminatingPathLength);
        }

        this.maxDiscriminatingPathLength = maxDiscriminatingPathLength;
    }

    /**
     * Indicates whether verbose output is enabled.
     *
     * @return true if verbose output is enabled, false otherwise.
     */
    public boolean isVerbose() {
        return verbose;
    }

    /**
     * Sets whether verbose output should be printed.
     *
     * @param verbose True, if so.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Returns the independence test used in search.
     *
     * @return This test.
     */
    public IndependenceTest getIndependenceTest() {
        return this.independenceTest;
    }

    /**
     * Sets the depth of the search for the possible m-sep search.
     *
     * @param depth This depth.
     */
    public void setDepth(int depth) {
        this.depth = depth;
    }

    /**
     * Sets the flag indicating whether to guarantee the output is a legal PAG.
     *
     * @param guaranteePag A boolean value indicating whether to guarantee the output is a legal PAG.
     */
    public void setGuaranteePag(boolean guaranteePag) {
        this.guaranteePag = guaranteePag;
    }

    /**
     * Sets the maximum indegree of the output graph.
     *
     * @param maxDegree This maximum.
     */
    public void setMaxDegree(int maxDegree) {
        if (maxDegree < -1) {
            throw new IllegalArgumentException("Depth must be -1 (unlimited) or >= 0: " + maxDegree);
        }

        this.maxDegree = maxDegree;
    }

    /**
     * Sets the print stream used for output, default System.out.
     *
     * @param out This print stream.
     */
    public void setOut(PrintStream out) {
        this.out = out;
    }

    /**
     * Sets whether one-edge faithfulness is assumed. For FGES
     *
     * @param faithfulnessAssumed True, if so.
     * @see Fges#setFaithfulnessAssumed(boolean)
     */
    public void setFaithfulnessAssumed(boolean faithfulnessAssumed) {
        this.faithfulnessAssumed = faithfulnessAssumed;
    }

    /**
     * Sets the number of threads to use in the search.
     *
     * @param numThreads The number of threads to use. Must be at least 1.
     */
    public void setNumThreads(int numThreads) {
        if (numThreads < 1) {
            throw new IllegalArgumentException("Number of threads must be at least 1: " + numThreads);
        }
        this.numThreads = numThreads;
    }

    /**
     * Sets whether the search should start from a complete graph.
     *
     * @param startFromCompleteGraph A boolean value indicating if the search should start from a complete graph.
     */
    public void setStartFromCompleteGraph(boolean startFromCompleteGraph) {
        this.startFromCompleteGraph = startFromCompleteGraph;
    }

    /**
     * Executes the FGES algorithm to compute the Markov equivalence class in the form of a completed partially directed
     * acyclic graph (CPDAG) based on the provided score and algorithm configuration.
     *
     * @return The resulting CPDAG representing the Markov equivalence class.
     * @throws InterruptedException if the operation is interrupted.
     */
    public Graph getMarkovCpdag() throws InterruptedException {
        if (isVerbose()) {
            TetradLogger.getInstance().log("Starting FGES.");
        }

        Fges fges = new Fges(this.score);
        fges.setReplicating(true);
        fges.setKnowledge(getKnowledge());
        fges.setVerbose(isVerbose());
        fges.setFaithfulnessAssumed(this.faithfulnessAssumed);
        fges.setMaxDegree(this.maxDegree);
        fges.setOut(this.out);
        fges.setNumThreads(numThreads);
        Graph cpdag = fges.search();

        if (isVerbose()) {
            TetradLogger.getInstance().log("Finished FGES.");
        }

        return cpdag;
    }

    /**
     * Sets whether the "Use Max-P" option is enabled or not.
     *
     * @param useMaxP A boolean flag indicating whether the "Use Max-P" option is enabled (true) or disabled (false).
     */
    public void setUseMaxP(boolean useMaxP) {
        this.useMaxP = useMaxP;
    }

    /**
     * Sets whether selection bias should be excluded during the search process.
     *
     * @param excludeSelectionBias True to exclude selection bias, false otherwise.
     */
    public void setExcludeSelectionBias(boolean excludeSelectionBias) {
        this.excludeSelectionBias = excludeSelectionBias;
    }
}

