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
import edu.cmu.tetrad.search.utils.FciOrient;
import edu.cmu.tetrad.search.utils.PagLegalityCheck;
import edu.cmu.tetrad.search.utils.R0R4StrategyTestBased;
import edu.cmu.tetrad.search.utils.SepsetMap;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.SublistGenerator;
import edu.cmu.tetrad.util.TetradLogger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static edu.cmu.tetrad.graph.GraphUtils.colliderAllowed;

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
 * @see #getMarkovDag(boolean)
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
     * algorithm. The default value is {@code false}, disabling the heuristic by default.
     */
    private boolean useMaxP = false;
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
    private boolean guaranteePag = true;
    /**
     * Whether to do the possible d-sep step; empirically we find this unnecessary, though the defiition of GFCI
     * includes it. In the context of the check pag branch it is not expensive.
     */
    private boolean usePossibleDsep = false;
    /**
     * A flag indicating whether the LV-Heuristic results should be returned. If false, edges will be removed via
     * further independence testing.
     */
    private boolean lvHeuristicOnly = false;
    /**
     * Maximum path length for possible d-sep.
     */
    private int maxPossibleDsepPathLength = -1;

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
     * @param useMaxP    True if the maxP method should be used.
     * @return A separating set of nodes (if found) that is a subset of the adjacency of x or y, or {@code null} if no
     * such set is found.
     * @throws InterruptedException if the process is interrupted during execution.
     */
    public static Set<Node> sepsetSubsetOfAdjxOrAdjy(Graph graph, Node x, Node y, Set<Node> containing,
                                                     IndependenceTest test, int depth, boolean useMaxP)
            throws InterruptedException {

        test.setVerbose(false);

        // We need to look at the original adjx and adjy, not some modified version.
        List<Node> adjx = graph.getAdjacentNodes(x);
        List<Node> adjy = graph.getAdjacentNodes(y);
        adjx.remove(y);
        adjy.remove(x);

        adjx.removeIf(node -> node.getNodeType() == NodeType.LATENT);
        adjy.removeIf(node -> node.getNodeType() == NodeType.LATENT);

        Set<Node> sepset1 = getSepset(x, y, containing, test, depth, adjx, useMaxP);
        Set<Node> sepset2 = getSepset(y, x, containing, test, depth, adjy, useMaxP);

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
     * @param adjx       The adjacency list of node x, from which subsets are generated to test for separation.
     * @return A separating set of nodes that fulfills all constraints and is a subset of adjx, or {@code null} if no
     * such set is found.
     */
    private static @Nullable Set<Node> getSepset(Node x, Node y, Set<Node> containing, IndependenceTest test, int depth,
                                                 List<Node> adjx, boolean useMaxP) throws InterruptedException {
        if (useMaxP) {
            List<Set<Node>> choices = getChoices(adjx, depth);
            // Max p for stability...
            return choices.parallelStream()
                    .max(Comparator.comparingDouble(set -> computeScore(x, y, set, test))) // Find max
                    .filter(set -> computeScore(x, y, set, test) > test.getAlpha()) // Filter by threshold
                    .orElse(null); // Return best set or null if none pass the threshold
        } else { // Greedy
            // Greedy: lazy enumeration (smallest subsets first), batched parallel testing,
            // short-circuit on the first separating set. Nothing is materialized up front.
            int d = (depth < 0 || depth > adjx.size()) ? adjx.size() : depth;
            SublistGenerator cg2 = new SublistGenerator(adjx.size(), d);
            final int BATCH = 4096;
            List<Set<Node>> batch = new ArrayList<>(BATCH);
            boolean exhausted = false;

            while (!exhausted) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();

                batch.clear();

                while (batch.size() < BATCH) {
                    int[] ch = cg2.next();
                    if (ch == null) { exhausted = true; break; }
                    Set<Node> s = GraphUtils.asSet(ch, adjx);
                    if (s.containsAll(containing)) batch.add(s);
                }

                if (batch.isEmpty()) break;

                Optional<Set<Node>> hit = batch.parallelStream()
                        .filter(s -> {
                            try {
                                return test.checkIndependence(x, y, s).isIndependent();
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                        }).findFirst();

                if (hit.isPresent()) return hit.get();
            }

            return null;
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
    private static @NotNull List<Set<Node>> getChoices(List<Node> adjx, int depth) throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException();
        }

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

        Graph cpdag = getMarkovDag(verbose);
        Graph pag = GraphTransforms.dagToPag(cpdag, false);

        if (lvHeuristicOnly) {
            return pag;
        }

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

        // Pass 1: adjacency-subset removal, iterated to a FIXPOINT. Gated commits are
        // order-dependent: a correct removal can be rejected because other spurious edges
        // still present make the trial graph illegal. Re-sweeping after each productive
        // pass retries those rejections against the improved graph; edge count strictly
        // decreases each iteration, so this terminates in <= |E| sweeps. Failed sepset
        // searches are deliberately not cached in foundSepsets, so shrunken adjacencies
        // are re-searched on later sweeps.
        int edgesBefore;
        do {
            edgesBefore = pag.getNumEdges();
            List<Edge> edges = new ArrayList<>(pag.getEdges());
//            RandomUtil.shuffle(edges);

            for (Edge edge : edges) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException();
                }

                Node a = edge.getNode1();
                Node c = edge.getNode2();

                if (!pag.isAdjacentTo(a, c)) {
                    continue;
                }

                Set<Node> sepset = foundSepsets.get(Set.of(a, c));

                if (sepset == null) {
                    sepset = sepsetSubsetOfAdjxOrAdjy(pag, a, c, new HashSet<>(), independenceTest, depth, useMaxP);

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
        } while (pag.getNumEdges() < edgesBefore);

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

            //            if (verbose) {
            //                TetradLogger.getInstance().log("*-FCI finished.");
            //            }

            List<Edge> dsepEdges = new ArrayList<>(pag.getEdges());
            RandomUtil.shuffle(dsepEdges);
            RandomUtil.shuffle(dsepEdges);

            for (Edge edge : dsepEdges) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException();
                }

                Node a = edge.getNode1();
                Node c = edge.getNode2();

                if (!pag.isAdjacentTo(a, c)) {
                    continue;
                }

                // One endpoint suffices (per JR); change `a` to `c`, or union the two, if parity
                // ever turns out to need both ends.
                List<Node> possibleDsep = pag.paths().possibleDsep(a, maxPossibleDsepPathLength);
                possibleDsep.remove(a);
                possibleDsep.remove(c);
                possibleDsep.removeIf(node -> node.getNodeType() == NodeType.LATENT);

                // Candidate sepsets are all subsets of Possible-D-SEP(a), capped at depth.
                // Reuse via foundSepsets; never pre-write sepsetMap (see Pass 1).
                Set<Node> sepset = foundSepsets.get(Set.of(a, c));

                if (sepset == null) {
                    // Possible-D-SEP pools can be very large; an unbounded depth here means
                    // exhausting 2^|pool| subsets for every GENUINE edge. Clamp regardless of
                    // the global depth setting.
                    int dsepDepth = (depth < 0) ? Math.min(possibleDsep.size(), 4) : Math.min(depth, possibleDsep.size());
                    sepset = getSepset(a, c, new HashSet<>(), independenceTest, dsepDepth, possibleDsep, useMaxP);

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

        // Saturating step (gated path only): clears deadlocks of the single-edge gated
        // fixpoint by trying the surviving test-confirmed removals JOINTLY. See the
        // saturatingRemoval javadoc for the witness case and the escalation ladder.
        if (guaranteePag) {
            pag = saturatingRemoval(pag, cpdag, nodes, sepsetMap, unshieldedColliders, selection, foundSepsets);
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

    /**
     * Runs the full *-FCI orientation in place on {@code pag}: reorient everything to circles, apply background
     * knowledge, copy unshielded colliders from the CPDAG and orient colliders implied by the extra sepsets, then run
     * the FCI final-orientation rules. This is the original *-FCI orientation block, factored out unchanged so the
     * legal-PAG gate can re-run it after each candidate removal. {@code unshieldedColliders} is rebuilt from scratch on
     * each call so that, after the (possibly many) re-orientations done by the gate, it always reflects the current
     * committed graph (it is consumed only by the final guaranteePag step).
     */
    private void gfciOrientPag(Graph pag, Graph cpdag, List<Node> nodes, SepsetMap sepsetMap,
                               Set<Triple> unshieldedColliders, FciOrient fciOrient)
            throws InterruptedException {
        unshieldedColliders.clear();

        pag.reorientAllWith(Endpoint.CIRCLE);
        fciOrient.fciOrientbk(knowledge, pag, pag.getNodes(), excludeSelectionBias);

        for (Node y : nodes) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }

            List<Node> adjacentNodes = new ArrayList<>(pag.getAdjacentNodes(y));

            ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException();
                }

                Node x = adjacentNodes.get(combination[0]);
                Node z = adjacentNodes.get(combination[1]);

                if (cpdag.isDefCollider(x, y, z) && !cpdag.isAdjacentTo(x, z)) {
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
        SepsetMap trialMap = new SepsetMap(sepsetMap);
        trialMap.set(a, c, sepset);
        FciOrient trialOrient = buildFciOrient(trialMap);
        gfciOrientPag(_pag, cpdag, nodes, trialMap, unshieldedColliders, trialOrient);

//        PagLegalityCheck.LegalPagRet legal = PagLegalityCheck.isLegalPag(_pag, new LinkedHashSet<>(selection), 30);
        PagLegalityCheck.LegalPagRet legal = PagLegalityCheck.isLegalPag(_pag, new LinkedHashSet<>(selection));

        if (!legal.isLegalPag()) {
            if (verbose) {
                TetradLogger.getInstance().log("\tTried removing " + a + " -- " + c + " (" + type
                        + "), but it didn't lead to a legal PAG (reverted). Reason: " + legal.getReason());
            }

            return pag;                                // committed sepset map untouched
        }

//        try {
//            ICovarianceMatrix covarianceMatrix = getIndependenceTest().getCov();
//
//            double lik = new RicfEjml().ricf(GraphTransforms.zhangMagFromPag(pag), covarianceMatrix).getLogLik();
//            double _lik = new RicfEjml().ricf(GraphTransforms.zhangMagFromPag(_pag), covarianceMatrix).getLogLik();
//
//            if (2 * (_lik - lik) - Math.log(covarianceMatrix.getSampleSize()) < 0) {
//                if (verbose) {
//                    TetradLogger.getInstance().log("\tRejected " + a + " -- " + c + " (" + type
//                            + ") because BIC did not improve.");
//                }
//                return pag;
//            }
//        } catch (Exception e) {
//            throw new RuntimeException(e);
//        }

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
     * Saturating step for the gated path, run once after the single-edge machinery has stalled.
     * <p>
     * The single-edge gated fixpoint can deadlock: a Markov state can carry several spurious edges,
     * each individually separable (test-confirmed sepset in hand), where deleting any ONE of them and
     * re-running the fixed reorientation yields an illegal PAG while the others still stand. The
     * OBS=6 witness cc...cc..ta.aaat.aaacat has exactly two such edges, pairwise shielding triples
     * at a shared separator vertex; deleting them JOINTLY lands on the true PAG, which is legal by
     * construction. This is the RB paper's saturating fallback in its engineering role.
     * <p>
     * Mechanics: R = all still-adjacent pairs with a cached test-confirmed sepset in
     * {@code foundSepsets} (covers stalls from both the adjacency-subset and possible-D-SEP passes).
     * Trial 1 removes all of R on a copy, seeds every sepset into a trial-local map, runs the one
     * full reorientation, and gates once on PAG legality. If that fails, one leave-one-out rung is
     * tried (each subset of size |R|-1, first legal wins) before reverting to the stalled graph.
     * Soundness is unchanged: the same legality gate is applied to the committed candidate, and every
     * removal is individually test-confirmed, so noise costs reach, never soundness. If PKE-style
     * certification still finds stalls, the next rungs are (a) deeper subset descent and (b) an outer
     * fixpoint over {single-edge pass, saturating step}, since a successful saturation shrinks
     * adjacencies and can make previously unseparable pairs separable.
     */
    private Graph saturatingRemoval(Graph pag, Graph cpdag, List<Node> nodes, SepsetMap sepsetMap,
                                    Set<Triple> unshieldedColliders, Set<Node> selection,
                                    Map<Set<Node>, Set<Node>> foundSepsets) throws InterruptedException {
        // R: still-present edges with a test-confirmed sepset in hand.
        List<Set<Node>> stalled = new ArrayList<>();
        for (Set<Node> pair : foundSepsets.keySet()) {
            List<Node> pn = new ArrayList<>(pair);
            if (pag.isAdjacentTo(pn.get(0), pn.get(1))) {
                stalled.add(pair);
            }
        }

        if (stalled.isEmpty()) {
            return pag;
        }

        if (verbose) {
            TetradLogger.getInstance().log("Saturating step: " + stalled.size()
                    + " separable edge(s) survived the single-edge fixpoint; trying joint removal.");
        }

        // Trial 1: full saturation; then one leave-one-out rung. (A singleton R retries the
        // single-edge trial that just failed and will fail again; harmless, and it keeps the
        // control flow uniform.)
        List<List<Set<Node>>> batches = new ArrayList<>();
        batches.add(stalled);
        if (stalled.size() > 1) {
            for (int skip = 0; skip < stalled.size(); skip++) {
                List<Set<Node>> sub = new ArrayList<>(stalled);
                sub.remove(skip);
                batches.add(sub);
            }
        }

        for (List<Set<Node>> batch : batches) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }

            Graph trial = pag.copy();
            SepsetMap trialMap = new SepsetMap(sepsetMap);
            for (Set<Node> pair : batch) {
                List<Node> pn = new ArrayList<>(pair);
                trial.removeEdge(pn.get(0), pn.get(1));
                trialMap.set(pn.get(0), pn.get(1), foundSepsets.get(pair));
            }
            FciOrient trialOrient = buildFciOrient(trialMap);
            gfciOrientPag(trial, cpdag, nodes, trialMap, unshieldedColliders, trialOrient);

            PagLegalityCheck.LegalPagRet legal = PagLegalityCheck.isLegalPag(trial, new LinkedHashSet<>(selection));

            if (legal.isLegalPag()) {
                // Accepted: commit exactly this batch's sepsets and carry the reoriented PAG forward.
                for (Set<Node> pair : batch) {
                    List<Node> pn = new ArrayList<>(pair);
                    sepsetMap.set(pn.get(0), pn.get(1), foundSepsets.get(pair));
                }
                if (verbose) {
                    TetradLogger.getInstance().log("Saturating step: removed " + batch.size() + " of "
                            + stalled.size() + " edge(s) jointly (legal PAG).");
                }
                return trial;
            } else if (verbose) {
                TetradLogger.getInstance().log("\tSaturating step: joint removal of " + batch.size()
                        + " edge(s) not legal (reverted). Reason: " + legal.getReason());
            }
        }

        if (verbose) {
            TetradLogger.getInstance().log("Saturating step: no joint removal was legal; stalled graph kept.");
        }
        return pag;
    }

    /**
     * Builds a fresh FCI orientation engine (complete rules, R4 on) bound to the given sepset map, using
     * the class's independence test, knowledge, and discriminating-path length. A fresh engine is built
     * per gated trial so that each trial's R4 sepset appends land in its own trial-local map and are
     * discarded with the trial.
     */
    private FciOrient buildFciOrient(SepsetMap sepsetMap) {
        R0R4StrategyTestBased strategy = (R0R4StrategyTestBased) R0R4StrategyTestBased.specialConfiguration(independenceTest, knowledge, verbose);
        // Respect the user's depth/length knobs instead of hardcoding unlimited. With
        // usePossibleDsep == false the R4 discriminating-path resolution must not be the
        // back door through which unbounded (possible-D-SEP-scale) conditioning searches
        // re-enter after the adjacency-only pass.
        strategy.setDepth(this.depth);
        strategy.setMaxLength(this.maxDiscriminatingPathLength);
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
     * Constructs and returns the Markov Directed Acyclic Graph (DAG) representing the
     * probabilistic relationships between variables. The Markov DAG is derived from
     * the underlying data or structural constraints.
     *
     * @param verbose a boolean flag indicating whether detailed progress
     *                or debugging information should be printed during the
     *                construction process.
     * @return a Graph object representing the Markov Directed Acyclic Graph (DAG).
     * @throws InterruptedException if the process is interrupted during execution.
     */
    public abstract Graph getMarkovDag(boolean verbose) throws InterruptedException;

    /**
     * Sets whether selection bias should be excluded during the search process.
     *
     * @param excludeSelectionBias A boolean indicating whether to exclude selection bias (true) or not (false).
     */
    public void setExcludeSelectionBias(boolean excludeSelectionBias) {
        this.excludeSelectionBias = excludeSelectionBias;
    }

    /**
     * When true, the extra-edge-removal step mimics FCIT: each candidate removal is committed only if the resulting
     * graph (after re-running the full *-FCI orientation) is a legal PAG; otherwise it is reverted. When false, the
     * original *-FCI behavior is used (greedy removal with a single final orientation). This is the one knob that
     * isolates Bryan's hypothesis: flip it to A/B the "legal PAG at each step" effect with everything else held fixed.
     *
     * @param guaranteePag Whether to pursue the branch that guarantees a legal PAG.
     */
    public void setGuaranteePag(boolean guaranteePag) {
        this.guaranteePag = guaranteePag;
    }

    /**
     * Whether to do the possible d-sep step; empirically we find this unnecessary, though the defiition of GFCI
     * includes it. In the context of the check pag branch it is not expensive.
     *
     * @param usePossibleDsep Whether to use the possible d-sep step.
     */
    public void setUsePossibleDsep(boolean usePossibleDsep) {
        this.usePossibleDsep = usePossibleDsep;
    }

    /**
     * A flag indicating whether the LV-Heuristic results should be returned. If false, edges will be removed via
     * further independence testing.
     *
     * @param lvHeuristicOnly Whether to return the LV-Heuristic results.
     */
    public void setLvHeuristicOnly(boolean lvHeuristicOnly) {
        this.lvHeuristicOnly = lvHeuristicOnly;
    }

    /**
     * Maximum path length for possible d-sep.
     *
     * @param maxPossibleDsepPathLength Maximum path length for possible d-sep.
     */
    public void setMaxPossibleDsepPathLength(int maxPossibleDsepPathLength) {
        if (maxPossibleDsepPathLength < -1) {
            throw new IllegalArgumentException("Maximum path length for possible d-sep must be >= -1 (-1 = unlimited).");
        }
        this.maxPossibleDsepPathLength = maxPossibleDsepPathLength;
    }
}

