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
     * A boolean flag indicating whether to use the maximum p-value heuristic during certain operations in the Star-FCI
     * algorithm. The default value is {@code true}, enabling the heuristic by default.
     */
    private boolean useMaxP = true;
    /**
     * A boolean flag indicating whether to exclude selection bias during certain operations in the Star-FCI algorithm.
     * The default value is {@code false}, allowing selection bias by default.
     */
    private boolean excludeSelectionBias = false;
    /**
     * When true, the extra-edge-removal step mimics FCIT: each candidate removal is committed only if the resulting
     * graph (after re-running the full *-FCI orientation) is a legal PAG; otherwise it is reverted. When false, the
     * original *-FCI behavior is used (greedy removal with a single final orientation). This is the one knob that
     * isolates Bryan's hypothesis: flip it to A/B the "legal PAG at each step" effect with everything else held fixed.
     */
    private boolean guaranteePag = false;
    /**
     * When true, a possible-D-SEP removal pass is run after the adjacency-subset removal pass: for each remaining edge
     * (a, c), all subsets of Possible-D-SEP(a) are considered as candidate separating sets, and a removal is committed
     * only if it leaves a legal PAG (otherwise reverted). This is the step the original GFCI had that *-FCI dropped; it
     * is off by default and added for parity, not because it was shown necessary. Possible-D-SEP assumes colliders are
     * already oriented as in FCI, which holds for the fully-oriented PAGs maintained here, so the pass always runs on an
     * oriented graph and is always legality-gated regardless of {@link #guaranteePag}.
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
        // Reusable data facts (X _||_ Y | S), invariant across passes. Kept OUT of sepsetMap
        // so a reverted removal can never leave a sepset in the committed map that orientPag
        // then stamps as a collider on a still-adjacent pair. Survives reverts by design.
        Map<Set<Node>, Set<Node>> foundSepsets = new HashMap<>();

        if (verbose) {
            TetradLogger.getInstance().log("Starting *-FCI extra edge removal step.");
        }

        // Orientation engine for the ungated final pass, bound to the committed sepset map. The gated
        // commit builds its own engine per trial (against a trial-local sepset copy), so R4's
        // discriminating-path sepset appends never pollute the committed map.
        FciOrient fciOrient = buildFciOrient(sepsetMap);

        // Selection nodes, needed by the PAG-legality check (mirrors FCIT).
        Set<Node> selection = new LinkedHashSet<>();
        for (Node node : nodes) {
            if (node.getNodeType() == NodeType.SELECTION) {
                selection.add(node);
            }
        }

        List<Edge> edges = new ArrayList<>(pag.getEdges());
        shuffle(edges);

        // Pass 1: adjacency-subset removal. Candidate sepsets are subsets of adj(a) or adj(c).
        for (Edge edge : edges) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            Node a = edge.getNode1();
            Node c = edge.getNode2();

            if (!pag.isAdjacentTo(a, c)) {
                continue;
            }

            Set<Node> sepset = foundSepsets.get(Set.of(a, c));

            if (sepset == null) {
                sepset = sepsetSubsetOfAdjxOrAdjy(pag, a, c, new HashSet<>(), independenceTest, depth, null, useMaxP);

                if (sepset != null) {
                    foundSepsets.put(Set.of(a, c), sepset);
                }
            }

            if (sepset == null) {
                continue;
            }

            pag = commitRemoval(pag, a, c, sepset, "adjacency-subset", guaranteePag,
                    cpdag, nodes, sepsetMap, unshieldedColliders, selection);
        }

        // Pass 2 (optional): possible-D-SEP removal. The original GFCI step that *-FCI dropped,
        // restored here for parity. Always runs on an oriented PAG and is always legality-gated,
        // since Possible-D-SEP presupposes FCI-oriented colliders.
        if (usePossibleDsep) {
            // Ensure colliders are oriented as in FCI before computing Possible-D-SEP. In the GATED
            // path pag already carries the last-accepted, gate-legal full orientation (commitRemoval
            // ran the complete gfciOrientPag on the accepted candidate), so this standalone pass is
            // unnecessary and is skipped. In the UNGATED path nothing has oriented pag yet, so this is
            // the sole orientation pass, exactly as before.
            if (!guaranteePag) {
                gfciOrientPag(pag, cpdag, nodes, sepsetMap, unshieldedColliders, fciOrient);
            }

            if (verbose) {
                TetradLogger.getInstance().log("*-FCI finished.");
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
                // Reuse via foundSepsets; never pre-write sepsetMap (see Pass 1).
                Set<Node> sepset = foundSepsets.get(Set.of(a, c));

                if (sepset == null) {
                    sepset = getSepset(a, c, new HashSet<>(), independenceTest, depth, null, possibleDsep, useMaxP);

                    if (sepset != null) {
                        foundSepsets.put(Set.of(a, c), sepset);
                    }
                }

                if (sepset == null) {
                    continue;
                }

                pag = commitRemoval(pag, a, c, sepset, "possible-D-SEP", true,
                        cpdag, nodes, sepsetMap, unshieldedColliders, selection);
            }
        }

        // Final orientation only for the ungated greedy path. In the gated path pag is already the
        // last-accepted, fully oriented, proven-legal PAG (commitRemoval ran the complete
        // gfciOrientPag on each accepted candidate), so no further orientation is applied here.
        if (!guaranteePag) {
            gfciOrientPag(pag, cpdag, nodes, sepsetMap, unshieldedColliders, fciOrient);
        }

        pag = GraphUtils.replaceNodes(pag, nodes);

        if (verbose) {
            TetradLogger.getInstance().log("*-FCI finished.");
        }

        return pag;
    }

    private List<Edge> findSpuriousEdges(Graph pag) throws InterruptedException {
        List<Edge> spuriousEdges = new ArrayList<>();

        for (Edge edge : pag.getEdges()) {
            Node m = edge.getNode1();
            Node n = edge.getNode2();

            long deadlineMs = 1000;//(timeout < 0L)
//                    ? Long.MAX_VALUE
//                    : System.currentTimeMillis() + timeout;

            RecursiveBlocking.BlockingResult result = RecursiveBlocking.blockPathsRecursively(
                    pag, m, n, Set.of(), Set.of(), -1, depth, -1, 1, false,
                    Long.MAX_VALUE);

            // !found() => blockingSet() closes every path: a candidate sepset.
            if (result.found()) {
                if (independenceTest.checkIndependence(m, n, result.blockingSet()).isIndependent()) {
                    spuriousEdges.add(edge);
                }
            }
        }

        return spuriousEdges;
    }

    /**
     * Runs the full *-FCI orientation in place on {@code pag}: reorient everything to circles, apply background
     * knowledge, copy unshielded colliders from the CPDAG and orient colliders implied by the extra sepsets, then run
     * the FCI final-orientation rules. This is the original *-FCI orientation block, factored out unchanged so the
     * legal-PAG gate can re-run it after each candidate removal. {@code unshieldedColliders} is rebuilt from scratch on
     * each call so that, after the (possibly many) re-orientations done by the gate, it always reflects the current
     * committed graph (it is consumed only by the final guaranteePag step).
     */
    private void gfciOrientPag(Graph pag, Graph cpdag, List<Node> nodes, SepsetMap sepsetMap,
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
     * Attempts to remove edge (a, c) using the given candidate sepset.
     * <p>
     * When {@code guaranteePag} is false, the edge is removed unconditionally (original greedy *-FCI),
     * leaving the graph for the single final orientation, and the sepset is written to the committed map.
     * <p>
     * When {@code guaranteePag} is true, the removal is gated on per-step PAG legality. The candidate is
     * built on a copy: the edge is removed and the graph is re-oriented from scratch by the full GFCI
     * orientation {@link #gfciOrientPag} — re-blank to circles, copy unshielded CPDAG colliders, stamp
     * recorded-sepset colliders, and apply the complete FCI rules (R0–R4). The full reorient (rather than
     * an incremental collider stamp) is required for soundness: when an edge that was a leg of an
     * unshielded collider is removed, the arrowheads and tails it induced must be rebuilt from circles, or
     * stale endpoints survive into the output. If the re-oriented candidate is a legal PAG it is carried
     * forward and the sepset is committed; otherwise nothing is committed and the unchanged graph is
     * returned.
     * <p>
     * Orientation runs against a trial-local <em>copy</em> of the committed sepset map, seeded with the
     * candidate sepset. This is essential: {@link #gfciOrientPag}'s R4 (discriminating-path) rule appends
     * sepsets to whatever map its strategy is bound to, so binding it to the committed map would let a
     * rejected trial leave spurious sepsets behind (later read as colliders) and let accepted trials grow
     * the map unboundedly, breaking maximality. Only the deliberate {@code (a, c) = sepset} write reaches
     * the committed map, and only on acceptance.
     */
    private Graph commitRemoval(Graph pag, Node a, Node c, Set<Node> sepset, String type, boolean guaranteePag,
                                Graph cpdag, List<Node> nodes, SepsetMap sepsetMap, Set<Triple> unshieldedColliders,
                                Set<Node> selection) throws InterruptedException {
        if (!guaranteePag) {
            pag.removeEdge(a, c);
            sepsetMap.set(a, c, sepset);
            if (verbose) {
                IndependenceResult result = independenceTest.checkIndependence(a, c, sepset);
                TetradLogger.getInstance().log("Removed edge " + a + " -- " + c + " (" + type + "); sepset = "
                        + sepset + ", p-value = " + result.getPValue() + ".");
            }
            return pag;
        }

        // Gated path: work on a copy; pag is not mutated until we accept.
        Graph _pag = pag.copy();
        _pag.removeEdge(a, c);

        // Full GFCI reorient against a trial-local sepset map (committed sepsets + this candidate), so
        // R4's discriminating-path sepset appends are discarded with the trial and never touch the
        // committed map.
        SepsetMap trialSepsets = copyOf(sepsetMap);
        trialSepsets.set(a, c, sepset);
        FciOrient trialOrient = buildFciOrient(trialSepsets);
        gfciOrientPag(_pag, cpdag, nodes, trialSepsets, unshieldedColliders, trialOrient);

        PagLegalityCheck.LegalPagRet legal = PagLegalityCheck.isLegalPag(_pag, new LinkedHashSet<>(selection));

        if (!legal.isLegalPag()) {
            if (verbose) {
                TetradLogger.getInstance().log("\tTried removing " + a + " -- " + c + " (" + type
                        + "), but it didn't lead to a legal PAG (reverted). Reason: " + legal.getReason());
            }

            return pag;                                // committed sepset map untouched
        }

        // Accepted: commit only the deliberate sepset write and carry the reoriented PAG forward.
        sepsetMap.set(a, c, sepset);

        if (verbose) {
            IndependenceResult result = independenceTest.checkIndependence(a, c, sepset);
            TetradLogger.getInstance().log("Removed edge " + a + " -- " + c + " (" + type
                    + ", legal PAG); sepset = " + sepset + ", p-value = " + result.getPValue() + ".");
        }

        return _pag;
    }

    /**
     * Builds a fresh FCI orientation engine (complete rules, R4 on) bound to the given sepset map, using
     * the class's independence test, knowledge, and discriminating-path length. A fresh engine is built
     * per gated trial so that each trial's R4 sepset appends land in its own trial-local map and are
     * discarded with the trial.
     */
    private FciOrient buildFciOrient(SepsetMap sepsetMap) {
        R0R4StrategyTestBased strategy = (R0R4StrategyTestBased) R0R4StrategyTestBased.specialConfiguration(independenceTest, knowledge, verbose);
        strategy.setDepth(depth);
        strategy.setMaxLength(-1);
        strategy.setSepsetMap(sepsetMap);
        strategy.setVerbose(false);
        FciOrient fciOrient = new FciOrient(strategy);
        fciOrient.setCompleteRuleSetUsed(completeRuleSetUsed);
        fciOrient.setRecursiveDepth(-1);
        fciOrient.setMaxDiscriminatingPathLength(maxDiscriminatingPathLength);
        fciOrient.setUseR4(true);
        fciOrient.setVerbose(false);
        return fciOrient;
    }

    /**
     * Returns a copy of a sepset map: the same (unordered-pair → sepset) entries in a new map. Used to
     * give each gated trial its own sepset map, so R4 appends during orientation cannot leak into the
     * committed map.
     */
    private static SepsetMap copyOf(SepsetMap sepsetMap) {
        SepsetMap copy = new SepsetMap();
        for (Set<Node> pair : sepsetMap.keySet()) {
            List<Node> arr = new ArrayList<>(pair);
            copy.set(arr.get(0), arr.get(1), sepsetMap.get(arr.get(0), arr.get(1)));
        }
        return copy;
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
     * @return true if verbose output is enabled, fals  e otherwise.
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
     * Sets whether to run the possible-D-SEP removal pass (the original GFCI step that *-FCI dropped). False by
     * default. When true, after the adjacency-subset pass, each remaining edge (a, c) is re-tested against all subsets
     * of Possible-D-SEP(a), and any separating removal that keeps the graph a legal PAG is committed. The pass always
     * runs on an oriented, legality-gated graph, so it is meaningful independently of {@link #setGuaranteePag(boolean)}.
     *
     * @param usePossibleDsep True to run the possible-D-SEP removal pass.
     */
    public void setUsePossibleDsep(boolean usePossibleDsep) {
        this.usePossibleDsep = usePossibleDsep;
    }

    /**
     * When true, the extra-edge-removal step mimics FCIT: each candidate removal is committed only if the resulting
     * graph (after re-running the full *-FCI orientation) is a legal PAG; otherwise it is reverted. When false, the
     * original *-FCI behavior is used (greedy removal with a single final orientation). This is the one knob that
     * isolates Bryan's hypothesis: flip it to A/B the "legal PAG at each step" effect with everything else held fixed.
     */
    public void setGuaranteePag(boolean guaranteePag) {
        this.guaranteePag = guaranteePag;
    }
}

