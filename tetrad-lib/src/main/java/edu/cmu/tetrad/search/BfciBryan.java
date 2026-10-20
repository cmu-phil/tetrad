/// ////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2026 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
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
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.utils.*;
import edu.cmu.tetrad.util.SublistGenerator;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.*;

import static edu.cmu.tetrad.graph.GraphUtils.colliderAllowed;

/**
 * BFCI-Bryan: BOSS followed by Bryan Andrews's from-scratch, sepset-map-as-state formulation of the gated
 * StarFCI extra-edge-removal step, with SEPSET RE-SELECTION and NO saturating (Pass 3) step.
 * <p>
 * DIFFERENCE FROM {@link StarFci} (the shipping gated template). StarFci freezes the FIRST test-confirmed
 * separating set found for a pair ({@code foundSepsets}: "a discovered separating set is a frozen data fact,
 * reused verbatim") and gates single removals on that one sepset; when the frozen sepset's induced
 * re-orientation is illegal, the pair deadlocks, and the saturating Pass 3 exists to clear such deadlocks by
 * joint removal. This class instead keeps scanning: when a candidate separating set is gate-rejected, the NEXT
 * separating set for the same pair is tried, within the same visit. Different separating sets induce different
 * R0 colliders and hence different re-orientations, so a pair whose first sepset fails the gate can still be
 * removed under another. On the OBS=6 single-removal deadlock witness in {@code StarFci.saturatingRemoval}'s
 * javadoc (positional key cc...cc..ta.aaat.aaacat), re-selection alone recovers the true PAG exactly, with no
 * saturating step, across 40 randomized edge/candidate orders (oracle verification 2026-10-18); the exhaustive
 * OBS=6 certification of this formulation is OPEN, so this class is for empirical comparison against
 * {@link Bfci}, not yet a certified replacement.
 * <p>
 * ALGORITHM (Bryan's pseudocode, with three corrections). State is the committed sepset map S plus the carried
 * graph G, where G is always the output of a full from-scratch re-orientation (circles, CPDAG colliders,
 * sepset colliders, Zhang's complete final rules) against S.
 * <pre>
 * G0 &lt;- BOSS CPDAG;  S &lt;- {};  G &lt;- dagToPag(DAG representative of G0)
 * Pass 1 (adjacency subsets), then Pass 2 (possible-D-SEP subsets), each to a fixpoint:
 *   Repeat
 *     changed &lt;- false
 *     For (a,b) in EDGES(G)
 *       For C in SUBSETS_d(pool(G,a)\{a,b}) ++ SUBSETS_d(pool(G,b)\{a,b})   // smallest first
 *         If a _||_ b | C
 *           If Try(G, S, a, b, C) = ACCEPT
 *             changed &lt;- true; break          // next edge; REJECT =&gt; next C
 *   While changed
 * Return G
 * </pre>
 * where Try removes a-b on a copy, seeds C into a trial-local copy of S, re-orients from scratch, and commits
 * G and S together iff the result passes the legality certificate (modulo background knowledge, exactly as in
 * {@link StarFci}); a rejected trial leaves no trace. The corrections relative to the pseudocode as given:
 * (1) S and G commit atomically (the original added C to S before the gate, so a revert left the sepset
 * behind); (2) the fixpoint test is an explicit changed flag; (3) a pair with no recorded sepset gets no
 * sepset collider in the re-orientation.
 * <p>
 * COSTS, DISCLOSED. Re-selection runs one full re-orientation plus one legality check per SEPARATING candidate
 * set scanned, not per pair, so {@code maxGateTrialsPerEdge} caps the gate trials per edge per visit (the cap
 * is per visit; later sweeps rescan against the shrunken graph). A committed sepset is thereby selected partly
 * for orientation coherence rather than independence strength alone; every committed sepset is still
 * individually test-confirmed. The Pass-2 subset search keeps StarFci's clamp: when {@code depth} is unlimited
 * (-1), subsets of possible-D-SEP pools are capped at size {@value #DSEP_DEPTH_CLAMP}.
 * <p>
 * This class is configured to respect knowledge of forbidden and required edges, including knowledge of
 * temporal tiers, by the same modulo-knowledge certificate as {@link StarFci}.
 *
 * @author bryanandrews
 * @author josephramsey
 * @see StarFci
 * @see Bfci
 * @see Boss
 */
public final class BfciBryan implements IGraphSearch {

    /**
     * Cap on the subset size searched over possible-D-SEP pools in Pass 2 when the global {@code depth} is
     * unlimited (-1), exactly as in {@link StarFci}.
     */
    private static final int DSEP_DEPTH_CLAMP = 4;
    /**
     * The independence test used in search.
     */
    private final IndependenceTest independenceTest;
    /**
     * The score used by BOSS.
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
     * Whether the final FCI orientation engine logs its rule-by-rule output; see
     * {@link StarFci#setLogFinalOrientations(boolean)}.
     */
    private boolean logFinalOrientations = false;
    /**
     * Whether to exclude selection bias.
     */
    private boolean excludeSelectionBias = false;
    /**
     * Whether to do the possible-D-SEP pass (Pass 2). Required for exactness; switch retained for timing
     * studies only, as in {@link StarFci}.
     */
    private boolean usePossibleDsep = true;
    /**
     * Maximum path length for possible d-sep.
     */
    private int maxPossibleDsepPathLength = -1;
    /**
     * Cap on legality-gate trials (re-orientation + certificate) per edge per visit. Independence tests that
     * fail to separate do not count. The cap is a compute guard on real data, where many candidate sets may
     * separate at moderate alpha; it is per visit, so later sweeps retry against the shrunken graph.
     */
    private int maxGateTrialsPerEdge = 100;
    /**
     * The number of BOSS restarts.
     */
    private int numStarts = 1;
    /**
     * Whether BOSS should use BES as a final step.
     */
    // Default TRUE (2026-10-20): the hybrid relies on the BOSS CPDAG being an I-map, which BES guarantees under
    // faithfulness; see Params.USE_BES_DEFAULT_TRUE.
    private boolean bossUseBes = true;
    /**
     * The number of threads BOSS uses.
     */
    private int numThreads = 1;

    /**
     * Constructor. The test and score should be for the same data.
     *
     * @param test  The test to use.
     * @param score The score to use (for BOSS).
     */
    public BfciBryan(IndependenceTest test, Score score) {
        if (test == null) throw new NullPointerException("Test is null");
        if (score == null) throw new NullPointerException("Score is null");
        this.independenceTest = test;
        this.score = score;
    }

    /**
     * Runs the search and returns the PAG.
     *
     * @return The PAG.
     * @throws InterruptedException if interrupted.
     */
    public Graph search() throws InterruptedException {
//        this.independenceTest.setVerbose(verbose);
        List<Node> nodes = new ArrayList<>(independenceTest.getVariables());

        Graph cpdag = getMarkovCpdag();
        Graph initDag = cpdag.paths().isLegalDag() ? cpdag : GraphTransforms.dagFromCpdag(cpdag);
        Graph pag = GraphTransforms.dagToPag(initDag, false);

        SepsetMap sepsetMap = new SepsetMap();

        Set<Node> selection = new LinkedHashSet<>();
        for (Node node : nodes) {
            if (node.getNodeType() == NodeType.SELECTION) {
                selection.add(node);
            }
        }

        if (verbose) {
            TetradLogger.getInstance().log("Starting BFCI-Bryan extra edge removal (sepset re-selection, no saturating step).");
        }

        // Pass 1: adjacency subsets, to a fixpoint.
        boolean changed;
        do {
            changed = false;
            for (Edge edge : new ArrayList<>(pag.getEdges())) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                Node a = edge.getNode1();
                Node b = edge.getNode2();
                if (!pag.isAdjacentTo(a, b)) continue;

                List<List<Node>> pools = new ArrayList<>();
                pools.add(adjPool(pag, a, b));
                pools.add(adjPool(pag, b, a));

                Graph next = tryRemoveEdge(pag, a, b, pools, depth, cpdag, nodes, sepsetMap,
                        selection, "adjacency-subset");
                if (next != null) {
                    pag = next;
                    changed = true;
                }
            }
        } while (changed);

        // Pass 2: possible-D-SEP subsets, to a fixpoint. The carried graph is fully oriented (dagToPag
        // start, or the last accepted commit's re-orientation), as possible-D-SEP presupposes.
        if (usePossibleDsep) {
            do {
                changed = false;
                for (Edge edge : new ArrayList<>(pag.getEdges())) {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                    Node a = edge.getNode1();
                    Node b = edge.getNode2();
                    if (!pag.isAdjacentTo(a, b)) continue;

                    List<List<Node>> pools = new ArrayList<>();
                    pools.add(dsepPool(pag, a, b));
                    pools.add(dsepPool(pag, b, a));

                    int poolMax = Math.max(pools.get(0).size(), pools.get(1).size());
                    int dsepDepth = (depth < 0) ? Math.min(poolMax, DSEP_DEPTH_CLAMP) : depth;

//                    if (verbose && depth < 0 && poolMax > DSEP_DEPTH_CLAMP) {
//                        TetradLogger.getInstance().log("Possible-D-SEP subset search for " + a + " -- " + b
//                                + " clamped to subsets of size <= " + DSEP_DEPTH_CLAMP + " (pool size "
//                                + poolMax + "); set depth explicitly to override.");
//                    }

                    Graph next = tryRemoveEdge(pag, a, b, pools, dsepDepth, cpdag, nodes, sepsetMap,
                            selection, "possible-D-SEP");
                    if (next != null) {
                        pag = next;
                        changed = true;
                    }
                }
            } while (changed);
        }

        // Uniform final knowledge refinement, as in StarFci: covers the path on which no removal ever
        // committed, where pag is the untouched dagToPag start carrying no knowledge marks.
        if (!knowledge.isEmpty()) {
            FciOrient refineOrient = buildFciOrient(new SepsetMap(sepsetMap));
            Graph refined = refineWithKnowledge(pag, refineOrient);

            if (legalPagModuloKnowledge(refined, new LinkedHashSet<>(selection), refineOrient).isLegalPag()) {
                pag = refined;
            } else if (verbose) {
                TetradLogger.getInstance().log("Final knowledge refinement failed the modulo-knowledge "
                        + "certificate (likely a knowledge/data conflict); returning the unrefined graph.");
            }
        }

        pag = GraphUtils.replaceNodes(pag, nodes);

        if (verbose) {
            TetradLogger.getInstance().log("BFCI-Bryan finished.");
        }

        return pag;
    }

    /**
     * One edge visit: scan candidate conditioning sets from the given pools, smallest first, trying each
     * SEPARATING set against the legality gate until one is accepted or the gate-trial cap is reached. This is
     * the sepset re-selection loop: a gate rejection moves to the NEXT separating set for the same pair rather
     * than retiring the pair.
     *
     * @return the accepted (re-oriented) graph, with the sepset committed to {@code sepsetMap}; or null if no
     * candidate was accepted (graph and committed map untouched).
     */
    private Graph tryRemoveEdge(Graph pag, Node a, Node b, List<List<Node>> pools, int subsetDepth,
                                Graph cpdag, List<Node> nodes, SepsetMap sepsetMap, Set<Node> selection,
                                String type) throws InterruptedException {
        Set<Set<Node>> tried = new HashSet<>();
        int gateTrials = 0;

        for (List<Node> pool : pools) {
            int d = (subsetDepth < 0 || subsetDepth > pool.size()) ? pool.size() : subsetDepth;
            SublistGenerator gen = new SublistGenerator(pool.size(), d);
            int[] choice;

            while ((choice = gen.next()) != null) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();

                Set<Node> C = GraphUtils.asSet(choice, pool);
                if (!tried.add(C)) continue;

                if (!independenceTest.checkIndependence(a, b, C).isIndependent()) continue;

                if (gateTrials >= maxGateTrialsPerEdge) {
                    if (verbose) {
                        TetradLogger.getInstance().log("\t" + a + " -- " + b + " (" + type + "): gate-trial cap of "
                                + maxGateTrialsPerEdge + " reached; deferring to a later sweep.");
                    }
                    return null;
                }
                gateTrials++;

                // Gated trial: copy, remove, re-orient from scratch against a trial-local sepset map seeded
                // with this candidate, certify. Commit S and G together on acceptance; no trace on rejection.
                Graph _pag = pag.copy();
                _pag.removeEdge(a, b);

                SepsetMap trialMap = new SepsetMap(sepsetMap);
                trialMap.set(a, b, C);
                FciOrient trialOrient = buildFciOrient(trialMap);
                gfciOrientPag(_pag, cpdag, nodes, trialMap, trialOrient);

                PagLegalityCheck.LegalPagRet legal =
                        legalPagModuloKnowledge(_pag, new LinkedHashSet<>(selection), trialOrient);

                if (legal.isLegalPag()) {
                    sepsetMap.set(a, b, C);
                    if (verbose) {
                        TetradLogger.getInstance().log("Removed edge " + a + " -- " + b + " (" + type
                                + ", legal PAG); sepset = " + C + " (gate trial " + gateTrials + ").");
                    }
                    return _pag;
                } else if (verbose) {
                    TetradLogger.getInstance().log("\tTried removing " + a + " -- " + b + " (" + type
                            + "); sepset = " + C + ", but it didn't lead to a legal PAG; trying the next "
                            + "separating set. Reason: " + legal.getReason());
                }
            }
        }

        if (verbose && gateTrials > 0) {
            TetradLogger.getInstance().log("\t" + a + " -- " + b + " (" + type + "): all " + gateTrials
                    + " separating set(s) gate-rejected this visit; edge kept.");
        }

        return null;
    }

    /**
     * Adjacency pool for one endpoint: ADJ(G, a) \ {b}, latents removed.
     */
    private List<Node> adjPool(Graph graph, Node a, Node b) {
        List<Node> adj = graph.getAdjacentNodes(a);
        adj.remove(b);
        adj.removeIf(node -> node.getNodeType() == NodeType.LATENT);
        return adj;
    }

    /**
     * Possible-D-SEP pool for one endpoint: PSBLDSEP(G, a) \ {a, b}, latents removed.
     */
    private List<Node> dsepPool(Graph graph, Node a, Node b) {
        List<Node> pool = graph.paths().possibleDsep(a, maxPossibleDsepPathLength);
        pool.remove(a);
        pool.remove(b);
        pool.removeIf(node -> node.getNodeType() == NodeType.LATENT);
        return pool;
    }

    /**
     * The full from-scratch *-FCI orientation, verbatim from {@link StarFci}: reorient everything to circles,
     * apply background knowledge, copy unshielded colliders from the CPDAG, stamp sepset colliders for
     * CPDAG-adjacent pairs, then run the FCI final-orientation rules.
     */
    private void gfciOrientPag(Graph pag, Graph cpdag, List<Node> nodes, SepsetMap sepsetMap,
                               FciOrient fciOrient) throws InterruptedException {
        pag.reorientAllWith(Endpoint.CIRCLE);
        fciOrient.fciOrientbk(knowledge, pag, pag.getNodes(), excludeSelectionBias);

        for (Node y : nodes) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();

            List<Node> adjacentNodes = new ArrayList<>(pag.getAdjacentNodes(y));

            for (int i = 0; i < adjacentNodes.size(); i++) {
                for (int j = i + 1; j < adjacentNodes.size(); j++) {
                    Node x = adjacentNodes.get(i);
                    Node z = adjacentNodes.get(j);

                    if (cpdag.isDefCollider(x, y, z) && !cpdag.isAdjacentTo(x, z)) {
                        if (colliderAllowed(pag, x, y, z, knowledge)) {
                            pag.setEndpoint(x, y, Endpoint.ARROW);
                            pag.setEndpoint(z, y, Endpoint.ARROW);
                        }
                    } else if (cpdag.isAdjacentTo(x, z)) {
                        Set<Node> sepset = sepsetMap.get(x, z);

                        if (sepset != null && !sepset.contains(y)) {
                            if (colliderAllowed(pag, x, y, z, knowledge)) {
                                pag.setEndpoint(x, y, Endpoint.ARROW);
                                pag.setEndpoint(z, y, Endpoint.ARROW);
                            }
                        }
                    }
                }
            }
        }

        fciOrient.finalOrientation(pag, excludeSelectionBias);
    }

    /**
     * Builds a fresh FCI orientation engine bound to the given sepset map, exactly as in {@link StarFci}, so
     * each trial's R4 discriminating-path sepset appends land in that trial's own map and are discarded with
     * the trial.
     */
    private FciOrient buildFciOrient(SepsetMap sepsetMap) {
        R0R4StrategyTestBased strategy = (R0R4StrategyTestBased)
                R0R4StrategyTestBased.specialConfiguration(independenceTest, knowledge, logFinalOrientations);
        strategy.setDepth(this.depth);
        strategy.setMaxLength(this.maxDiscriminatingPathLength);
        strategy.setSepsetMap(sepsetMap);
        strategy.setBlockingType(R0R4StrategyTestBased.BlockingType.GREEDY);
        strategy.setVerbose(logFinalOrientations);
        FciOrient fciOrient = new FciOrient(strategy);
        fciOrient.setCompleteRuleSetUsed(completeRuleSetUsed);
        fciOrient.setRecursiveDepth(-1);
        fciOrient.setMaxDiscriminatingPathLength(maxDiscriminatingPathLength);
        fciOrient.setUseR4(true);
        fciOrient.setVerbose(logFinalOrientations);
        return fciOrient;
    }

    /**
     * Legal PAG modulo background knowledge, verbatim from {@link StarFci}: with empty knowledge this is
     * exactly {@link PagLegalityCheck#isLegalPag(Graph, Set)}; otherwise the round-trip equality is taken
     * against the knowledge-refined canonical PAG of the implied Zhang MAG.
     */
    private PagLegalityCheck.LegalPagRet legalPagModuloKnowledge(Graph pag, Set<Node> selection, FciOrient orient)
            throws InterruptedException {
        if (knowledge.isEmpty()) {
            return PagLegalityCheck.isLegalPag(pag, selection);
        }

        for (Node n : pag.getNodes()) {
            if (n.getNodeType() != NodeType.MEASURED) {
                return new PagLegalityCheck.LegalPagRet(false, "Node " + n + " is not measured");
            }
        }

        Graph mag;
        try {
            mag = GraphTransforms.zhangMagFromPag(pag);
        } catch (Exception e) {
            return new PagLegalityCheck.LegalPagRet(false, "PAG to MAG failed");
        }

        PagLegalityCheck.LegalMagRet legalMag = PagLegalityCheck.isLegalMag(mag, selection);

        if (!legalMag.isLegalMag()) {
            return new PagLegalityCheck.LegalPagRet(false, legalMag.getReason() + " in a MAG implied by this graph");
        }

        Graph pag2;
        try {
            MagToPag magToPag = new MagToPag(mag);
            pag2 = magToPag.convert(false, false);
        } catch (IllegalStateException e) {
            return new PagLegalityCheck.LegalPagRet(false, "Legal PAG status could not be determined");
        }

        Graph pag2k = refineWithKnowledge(pag2, orient);

        if (!pag.equals(pag2k)) {
            String reason = "The MAG implied by this graph was a legal MAG, but the graph is not recoverable as the "
                    + "knowledge-refined canonical PAG of that MAG -- it carries marks forced neither by the "
                    + "equivalence class nor by background knowledge";
            return new PagLegalityCheck.LegalPagRet(false, reason);
        }

        return new PagLegalityCheck.LegalPagRet(true, "This is a legal PAG refined by background knowledge");
    }

    /**
     * Refines a graph with background knowledge and closes under the complete FCI final rules, verbatim from
     * {@link StarFci}. Works on a copy; identity when knowledge is empty.
     */
    private Graph refineWithKnowledge(Graph graph, FciOrient orient) throws InterruptedException {
        if (knowledge.isEmpty()) {
            return graph;
        }

        Graph g = graph.copy();
        orient.fciOrientbk(knowledge, g, g.getNodes(), excludeSelectionBias);
        orient.finalOrientation(g, excludeSelectionBias);
        return g;
    }

    /**
     * Runs BOSS to obtain the initial Markov CPDAG, exactly as in {@link Bfci}.
     */
    private Graph getMarkovCpdag() throws InterruptedException {
        if (verbose) {
            TetradLogger.getInstance().log("Starting BOSS.");
        }

        Boss subAlg = new Boss(this.score);
        subAlg.setUseBes(bossUseBes);
        subAlg.setNumStarts(this.numStarts);
        subAlg.setNumThreads(numThreads);
        subAlg.setVerbose(verbose);
        PermutationSearch alg = new PermutationSearch(subAlg);
        alg.setKnowledge(knowledge);
        Graph cpdag = alg.search(true);

        if (verbose) {
            TetradLogger.getInstance().log("Finished BOSS.");
        }

        return cpdag;
    }

    /**
     * Sets the knowledge to use in search.
     *
     * @param knowledge This knowledge.
     */
    public void setKnowledge(Knowledge knowledge) {
        if (knowledge == null) throw new NullPointerException();
        this.knowledge = knowledge;
    }

    /**
     * Sets whether Zhang's complete rules are used.
     *
     * @param completeRuleSetUsed True if so.
     */
    public void setCompleteRuleSetUsed(boolean completeRuleSetUsed) {
        this.completeRuleSetUsed = completeRuleSetUsed;
    }

    /**
     * Sets the maximum length of any discriminating path.
     *
     * @param maxDiscriminatingPathLength the maximum length, or -1 if unlimited.
     */
    public void setMaxDiscriminatingPathLength(int maxDiscriminatingPathLength) {
        if (maxDiscriminatingPathLength < -1) {
            throw new IllegalArgumentException("Max path length must be -1 (unlimited) or >= 0: "
                    + maxDiscriminatingPathLength);
        }
        this.maxDiscriminatingPathLength = maxDiscriminatingPathLength;
    }

    /**
     * Sets the depth of the sepset search.
     *
     * @param depth This depth.
     */
    public void setDepth(int depth) {
        this.depth = depth;
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
     * Sets whether the final FCI orientation engine logs its rule-by-rule output.
     *
     * @param logFinalOrientations True, if so.
     */
    public void setLogFinalOrientations(boolean logFinalOrientations) {
        this.logFinalOrientations = logFinalOrientations;
    }

    /**
     * Sets whether selection bias should be excluded.
     *
     * @param excludeSelectionBias True to exclude.
     */
    public void setExcludeSelectionBias(boolean excludeSelectionBias) {
        this.excludeSelectionBias = excludeSelectionBias;
    }

    /**
     * Sets whether to do the possible-D-SEP pass (Pass 2). Required for exactness; the switch is for timing
     * studies only.
     *
     * @param usePossibleDsep True to run Pass 2.
     */
    public void setUsePossibleDsep(boolean usePossibleDsep) {
        this.usePossibleDsep = usePossibleDsep;
    }

    /**
     * Sets the maximum path length for possible d-sep.
     *
     * @param maxPossibleDsepPathLength the maximum path length, or -1 if unlimited.
     */
    public void setMaxPossibleDsepPathLength(int maxPossibleDsepPathLength) {
        if (maxPossibleDsepPathLength < -1) {
            throw new IllegalArgumentException("Maximum path length for possible d-sep must be >= -1 (-1 = unlimited).");
        }
        this.maxPossibleDsepPathLength = maxPossibleDsepPathLength;
    }

    /**
     * Sets the cap on legality-gate trials per edge per visit.
     *
     * @param maxGateTrialsPerEdge the cap; must be at least 1.
     */
    public void setMaxGateTrialsPerEdge(int maxGateTrialsPerEdge) {
        if (maxGateTrialsPerEdge < 1) {
            throw new IllegalArgumentException("Gate-trial cap must be at least 1: " + maxGateTrialsPerEdge);
        }
        this.maxGateTrialsPerEdge = maxGateTrialsPerEdge;
    }

    /**
     * Sets the number of BOSS restarts.
     *
     * @param numStarts The number of restarts.
     */
    public void setNumStarts(int numStarts) {
        this.numStarts = numStarts;
    }

    /**
     * Sets whether BOSS should use BES.
     *
     * @param useBes True if so.
     */
    public void setBossUseBes(boolean useBes) {
        this.bossUseBes = useBes;
    }

    /**
     * Sets the number of threads BOSS uses.
     *
     * @param numThreads The number of threads; at least 1.
     */
    public void setNumThreads(int numThreads) {
        if (numThreads < 1) {
            throw new IllegalArgumentException("Number of threads must be at least 1: " + numThreads);
        }
        this.numThreads = numThreads;
    }
}
