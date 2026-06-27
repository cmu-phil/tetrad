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
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.utils.*;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.SublistGenerator;
import edu.cmu.tetrad.util.TetradLogger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static edu.cmu.tetrad.graph.GraphUtils.colliderAllowed;
import static java.util.Collections.shuffle;

/**
 * *-FCI implements a template modification of GFCI that starts with a given Markov CPDAG and then fixes that result to
 * be correct for latent variables models. First, colliders from the Markov DAG are copied into the final circle-circle
 * graph, and some independence reasoning is used to remove edges from this and add the remaining colliders into the
 * graph. Then, the FCI final orientation rules are applied.
 * <p>
 * The Markov CPDAG needs to be supplied by classes inheriting from this abstract class using the getMarkovCpdag()
 * methods.
 * <p>
 * The reference for the GFCI algorithm this is being modeled from is here:
 * <p>
 * Ogarrio, J. M., Spirtes, P., &amp; Ramsey, J. (2016, August). A hybrid causal search algorithm for latent variable
 * models. In Conference on probabilistic graphical models (pp. 368-379). PMLR.
 * <p>
 * We modify this by insistent that getMarkovCpdag() is overridden by a method that will return a CPDAG Markov to the
 * data or underlying generative model and removing the possible d-sep step of the original algorithm.
 * <p>
 * This class is configured to respect knowledge of forbidden and required edges, including knowledge of temporal
 * tiers.
 *
 * @author josephramsey
 * @author bryanandrews
 * @see #getMarkovCpdag()
 * @see Knowledge
 */
public abstract class StarFciCheckPag implements IGraphSearch {
    /**
     * The independence test used in search.
     */
    private final IndependenceTest independenceTest;
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
     * A boolean flag indicating whether to use the maximum p-value heuristic during certain operations in the Star-FCI
     * algorithm. The default value is {@code true}, enabling the heuristic by default.
     */
    private boolean useMaxP = false;
    private boolean excludeSelectionBias = false;
    /**
     * When true, the extra-edge-removal step mimics FCIT: each candidate removal is committed only if the resulting
     * graph (after re-running the full *-FCI orientation) is a legal PAG; otherwise it is reverted. When false, the
     * original *-FCI behavior is used (greedy removal with a single final orientation). This is the one knob that
     * isolates Bryan's hypothesis: flip it to A/B the "legal PAG at each step" effect with everything else held fixed.
     */
    private boolean revertToLegalPag = false;
    /**
     * When true, a possible-D-SEP removal pass is run after the adjacency-subset removal pass: for each remaining edge
     * (a, c), all subsets of Possible-D-SEP(a) are considered as candidate separating sets, and a removal is committed
     * only if it leaves a legal PAG (otherwise reverted). This is the step the original GFCI had that *-FCI dropped; it
     * is off by default and added for parity, not because it was shown necessary. Possible-D-SEP assumes colliders are
     * already oriented as in FCI, which holds for the fully-oriented PAGs maintained here, so the pass always runs on an
     * oriented graph and is always legality-gated regardless of {@link #revertToLegalPag}.
     */
    private boolean usePossibleDsep = false;

    /**
     * Constructs a new StarFci algorithm with the given independence test.
     *
     * @param test The independence test to use.
     */
    public StarFciCheckPag(IndependenceTest test) {
        this.independenceTest = test;
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

        test.setVerbose(false);

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

        try {
            double p1 = test.checkIndependence(x, y, sepset1).getPValue();
            double p2 = test.checkIndependence(x, y, sepset2).getPValue();

            return p1 > p2 ? sepset1 : sepset2;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
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
                    .max(Comparator.comparingDouble(set -> computeScore(x, y, set, test))) // Find max
                    .filter(set -> computeScore(x, y, set, test) > test.getAlpha()) // Filter by threshold
                    .orElse(null); // Return best set or null if none pass the threshold
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
     * Sets whether to use the maxP criterion during the search process.
     *
     * @param useMaxP A boolean indicating whether the maxP criterion should be applied (true) or not (false).
     */
    public void setUseMaxP(boolean useMaxP) {
        this.useMaxP = useMaxP;
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

        Graph cpdag = getMarkovCpdag();
        Graph pag = GraphTransforms.dagToPag(cpdag, false);
        Set<Triple> unshieldedColliders = new HashSet<>();
        SepsetMap sepsetMap = new SepsetMap();

        if (verbose) {
            TetradLogger.getInstance().log("Starting *-FCI extra edge removal step.");
        }

        // Build the orientation engine up front: the legal-PAG gate re-runs the full
        // *-FCI orientation after every candidate removal, so we need it inside the loop.
        R0R4StrategyTestBased strategy = (R0R4StrategyTestBased) R0R4StrategyTestBased.specialConfiguration(independenceTest, knowledge, verbose);
        strategy.setDepth(-1);
        strategy.setMaxLength(-1);
        FciOrient fciOrient = new FciOrient(strategy);
        fciOrient.setCompleteRuleSetUsed(completeRuleSetUsed);
        fciOrient.setRecursiveDepth(-1);
        fciOrient.setMaxDiscriminatingPathLength(maxDiscriminatingPathLength);
        fciOrient.setUseR4(true);
        fciOrient.setVerbose(false);

        // Selection nodes, needed by the PAG-legality check (mirrors FCIT).
        Set<Node> selection = new LinkedHashSet<>();
        for (Node node : nodes) {
            if (node.getNodeType() == NodeType.SELECTION) {
                selection.add(node);
            }
        }

        List<Edge> edges = new ArrayList<>(pag.getEdges());
        shuffle(edges);

        // Baseline orientation: the gate compares each candidate removal against a fully
        // oriented PAG (the *-FCI starting point), the way FCIT starts from dagToPag(BOSS DAG)
        // before any edge is removed. Needed only when the main pass is gated.
        if (revertToLegalPag) {
            orientPag(pag, cpdag, nodes, sepsetMap, unshieldedColliders, fciOrient);
        }

        // Pass 1: adjacency-subset removal. Candidate sepsets are subsets of adj(a) or adj(c).
        for (Edge edge : edges) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            if (verbose) {
                TetradLogger.getInstance().log("Trying to remove " + edge + " by adjacency-subset.");
            }

            Node a = edge.getNode1();
            Node c = edge.getNode2();

            if (!pag.isAdjacentTo(a, c)) {
                continue;
            }

            Set<Node> sepset = sepsetSubsetOfAdjxOrAdjy(pag, a, c, new HashSet<>(), independenceTest, depth, null, useMaxP);

            if (sepset == null) {
                continue;
            }

            pag = commitRemoval(pag, a, c, sepset, "adjacency-subset", revertToLegalPag,
                    cpdag, nodes, sepsetMap, unshieldedColliders, fciOrient, selection);
        }

        // Pass 2 (optional): possible-D-SEP removal. The original GFCI step that *-FCI dropped,
        // restored here for parity. Always runs on an oriented PAG and is always legality-gated,
        // since Possible-D-SEP presupposes FCI-oriented colliders.
        if (usePossibleDsep) {
            // Ensure colliders are oriented as in FCI before computing Possible-D-SEP. Idempotent
            // when the main pass was gated (already oriented); required when it was greedy.
            orientPag(pag, cpdag, nodes, sepsetMap, unshieldedColliders, fciOrient);

            if (verbose) {
                TetradLogger.getInstance().log("Starting possible-D-SEP removal step.");
            }

            List<Edge> dsepEdges = new ArrayList<>(pag.getEdges());
            shuffle(dsepEdges);

            for (Edge edge : dsepEdges) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                Node a = edge.getNode1();
                Node c = edge.getNode2();

                if (!pag.isAdjacentTo(a, c)) {
                    continue;
                }

                // One endpoint suffices (per JR); change `a` to `c`, or union the two, if parity
                // ever turns out to need both ends.
                int maxPathLength = -1; // unlimited path length
                List<Node> possibleDsep = pag.paths().possibleDsep(a, maxPathLength);
                possibleDsep.remove(a);
                possibleDsep.remove(c);
                possibleDsep.removeIf(node -> node.getNodeType() == NodeType.LATENT);

                // Candidate sepsets are all subsets of Possible-D-SEP(a), capped at depth.
                Set<Node> sepset = getSepset(a, c, new HashSet<>(), independenceTest, depth, null, possibleDsep, useMaxP);

                if (sepset == null) {
                    continue;
                }

                pag = commitRemoval(pag, a, c, sepset, "possible-D-SEP", true,
                        cpdag, nodes, sepsetMap, unshieldedColliders, fciOrient, selection);
            }
        }

        // Final orientation: re-syncs the collider set after a possible trailing revert in the
        // gated path, and is the sole orientation pass in the ungated path.
        orientPag(pag, cpdag, nodes, sepsetMap, unshieldedColliders, fciOrient);

        if (guaranteePag) {
            pag = GraphUtils.guaranteePag(pag, fciOrient, knowledge, unshieldedColliders, verbose, new HashSet<>(),
                    excludeSelectionBias, Integer.MAX_VALUE);
        }

        if (verbose) {
            TetradLogger.getInstance().log("*-FCI finished.");
        }

        TetradLogger.getInstance().log("Orienting final graph as a PAG");
        pag = GraphUtils.replaceNodes(new MagToPag(GraphTransforms.zhangMagFromPag(pag)).convert(false, false), nodes);

        return pag;
    }

    /**
     * Runs the full *-FCI orientation in place on {@code pag}: reorient everything to circles, apply background
     * knowledge, copy unshielded colliders from the CPDAG and orient colliders implied by the extra sepsets, then run
     * the FCI final-orientation rules. This is the original *-FCI orientation block, factored out unchanged so the
     * legal-PAG gate can re-run it after each candidate removal. {@code unshieldedColliders} is rebuilt from scratch on
     * each call so that, after the (possibly many) re-orientations done by the gate, it always reflects the current
     * committed graph (it is consumed only by the final guaranteePag step).
     */
    private void orientPag(Graph pag, Graph cpdag, List<Node> nodes, SepsetMap sepsetMap,
                           Set<Triple> unshieldedColliders, FciOrient fciOrient) throws InterruptedException {
        unshieldedColliders.clear();

        pag.reorientAllWith(Endpoint.CIRCLE);
        fciOrient.fciOrientbk(knowledge, pag, pag.getNodes(), excludeSelectionBias);

        for (Node y : nodes) {
            List<Node> adjacentNodes = new ArrayList<>(pag.getAdjacentNodes(y));

            ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                Node x = adjacentNodes.get(combination[0]);
                Node z = adjacentNodes.get(combination[1]);

                if (cpdag.isDefCollider(x, y, z)) {
                    if (colliderAllowed(pag, x, y, z, knowledge)) {
                        pag.setEndpoint(x, y, Endpoint.ARROW);
                        pag.setEndpoint(z, y, Endpoint.ARROW);
                        unshieldedColliders.add(new Triple(x, y, z));
                    }
                } else if (cpdag.isAdjacentTo(x, z)) {
                    Set<Node> sepset = sepsetMap.get(x, z);

                    if (sepset != null && !sepset.contains(y)) {
                        if (colliderAllowed(pag, x, y, z, knowledge)) {
                            pag.setEndpoint(x, y, Endpoint.ARROW);
                            pag.setEndpoint(z, y, Endpoint.ARROW);

                            if (!pag.isAdjacentTo(x, z)) {
                                unshieldedColliders.add(new Triple(x, y, z));
                            }
                        }
                    }
                }
            }
        }

        fciOrient.finalOrientation(pag, excludeSelectionBias);
    }

    /**
     * Attempts to remove edge (a, c) using the given candidate sepset. When {@code gated} is true, the removal is
     * committed only if re-running the full *-FCI orientation yields a legal PAG; otherwise the graph and the pair's
     * sepset are rolled back. When {@code gated} is false, the edge is removed unconditionally (original *-FCI
     * behavior), leaving the graph for a single orientation pass later. Returns the graph to use going forward: the
     * committed graph on success (or in the ungated case), or the restored snapshot on a reverted gated attempt.
     */
    private Graph commitRemoval(Graph pag, Node a, Node c, Set<Node> sepset, String type, boolean gated,
                                Graph cpdag, List<Node> nodes, SepsetMap sepsetMap, Set<Triple> unshieldedColliders,
                                FciOrient fciOrient, Set<Node> selection) throws InterruptedException {
        if (!gated) {
            pag.removeEdge(a, c);
            sepsetMap.set(a, c, sepset);
            if (verbose) {
                IndependenceResult result = independenceTest.checkIndependence(a, c, sepset);
                TetradLogger.getInstance().log("Removed edge " + a + " -- " + c + " (" + type + "); sepset = "
                        + sepset + ", p-value = " + result.getPValue() + ".");
            }
            return pag;
        }

//        // --- FCIT-style try / re-orient / check-legal / revert ---
//        Graph saved = new EdgeListGraph(pag);          // snapshot
//        Set<Node> oldSepset = sepsetMap.get(a, c);     // may be null
//
//        pag.removeEdge(a, c);
//        sepsetMap.set(a, c, sepset);
//        orientPag(pag, cpdag, nodes, sepsetMap, unshieldedColliders, fciOrient);
//
//        PagLegalityCheck.LegalPagRet legal = PagLegalityCheck.isLegalPag(pag, selection);
//
//        if (!legal.isLegalPag()) {
//            sepsetMap.set(a, c, oldSepset);            // revert sepset
//            if (verbose) {
//                TetradLogger.getInstance().log("\tTried removing " + a + " -- " + c + " (" + type
//                        + "), but it didn't lead to a legal PAG (reverted). Reason: " + legal.getReason());
//            }
//            return saved;                              // revert graph
//        }
//
//        if (verbose) {
//            IndependenceResult result = independenceTest.checkIndependence(a, c, sepset);
//            TetradLogger.getInstance().log("Removed edge " + a + " -- " + c + " (" + type
//                    + ", legal PAG); sepset = " + sepset + ", p-value = " + result.getPValue() + ".");
//        }
//        return pag;

        // --- FCIT-MAG-style try / re-orient / check-legal-MAG / revert ---
        Graph saved = new EdgeListGraph(pag);          // snapshot
        Set<Node> oldSepset = sepsetMap.get(a, c);     // may be null

        pag.removeEdge(a, c);
        sepsetMap.set(a, c, sepset);
        orientPag(pag, cpdag, nodes, sepsetMap, unshieldedColliders, fciOrient);

        // Gate on the Zhang MAG of the almost-PAG. R4 is off in fciOrient, so orientPag
        // leaves an almost-PAG; it has already stamped the CPDAG and sepset colliders into
        // pag, and zhangMagFromPag only resolves circles, so those arrowheads carry into the
        // MAG with no separate stamping pass (unlike FCIT-MAG, which operates on the MAG
        // directly and must call orientSepsetCollidersInMag). An illegal almost-PAG yields an
        // illegal MAG and is reverted, as before — only the predicate changed.
        Graph mag = GraphTransforms.zhangMagFromPag(pag);
        PagLegalityCheck.LegalMagRet legal = PagLegalityCheck.isLegalMag(mag, new LinkedHashSet<>(selection));

        if (!legal.isLegalMag()) {
            sepsetMap.set(a, c, oldSepset);            // revert sepset
            if (verbose) {
                TetradLogger.getInstance().log("\tTried removing " + a + " -- " + c + " (" + type
                        + "), but it didn't lead to a legal MAG (reverted). Reason: " + legal.getReason());
            }
            return saved;                              // revert graph
        }

        if (verbose) {
            IndependenceResult result = independenceTest.checkIndependence(a, c, sepset);
            TetradLogger.getInstance().log("Removed edge " + a + " -- " + c + " (" + type
                    + ", legal MAG); sepset = " + sepset + ", p-value = " + result.getPValue() + ".");
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
     * Returns a Markov CPDAG to use as the initial graph in the Star-FCI search.
     *
     * @return This CPDAG.
     * @throws InterruptedException if interrupted.
     */
    public abstract Graph getMarkovCpdag() throws InterruptedException;

    /**
     * Sets the flag indicating whether the graph is being replicated. (Unused.)
     *
     * @param replicatingGraph A boolean value where {@code true} indicates that
     *                         the graph is being replicated, and {@code false}
     *                         otherwise.
     * @throws UnsupportedOperationException Graph replication is not supported by this algorithm.
     */
    public void setReplicatingGraph(boolean replicatingGraph) {
//        this.replicatingGraph = replicatingGraph;
        // Unused.
    }

    /**
     * Sets whether selection bias should be excluded during the search process.
     *
     * @param excludeSelectionBias A boolean indicating whether to exclude selection bias (true) or not (false).
     */
    public void setExcludeSelectionBias(boolean excludeSelectionBias) {
        this.excludeSelectionBias = excludeSelectionBias;
    }

    /**
     * Sets whether the extra-edge-removal step should commit a removal only when the re-oriented graph is a legal PAG
     * (reverting otherwise), the way FCIT does. True by default. Set to false to recover the original *-FCI behavior
     * (greedy removal, single final orientation), which gives a clean within-class A/B test of the "legal PAG at each
     * step" effect with the orientation rules and sepset search held fixed.
     *
     * @param revertToLegalPag True to gate each removal on PAG legality, false for the original greedy behavior.
     */
    public void setRevertToLegalPag(boolean revertToLegalPag) {
        this.revertToLegalPag = revertToLegalPag;
    }

    /**
     * Sets whether to run the possible-D-SEP removal pass (the original GFCI step that *-FCI dropped). False by
     * default. When true, after the adjacency-subset pass, each remaining edge (a, c) is re-tested against all subsets
     * of Possible-D-SEP(a), and any separating removal that keeps the graph a legal PAG is committed. The pass always
     * runs on an oriented, legality-gated graph, so it is meaningful independently of {@link #setRevertToLegalPag}.
     *
     * @param usePossibleDsep True to run the possible-D-SEP removal pass.
     */
    public void setUsePossibleDsep(boolean usePossibleDsep) {
//        this.usePossibleDsep = usePossibleDsep;
    }
}

