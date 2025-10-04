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
import edu.cmu.tetrad.search.test.CachingIndependenceTest;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.utils.FciOrient;
import edu.cmu.tetrad.search.utils.R0R4StrategyTestBased;
import edu.cmu.tetrad.search.utils.SepsetMap;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.MillisecondTimes;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.*;

/**
 * The Fci class implements the Fast Causal Inference (FCI) algorithm for discovering causal structures from data. It
 * supports various configurations and rule sets for orienting edges in a partially directed acyclic graph (PDAG) or a
 * completed partially directed acyclic graph (CPDAG).
 * <p>
 * It provides methods for running searches, configuring the search process, and managing the independence test,
 * variable filtering, depth settings, and other algorithm-specific options.
 */
public final class Fci implements IGraphSearch {

    // -------------------------
    // Existing fields (unchanged)
    // -------------------------
    private final List<Node> variables = new ArrayList<>();
    private final TetradLogger logger = TetradLogger.getInstance();
    private IndependenceTest test;
    private SepsetMap sepsets;
    private Knowledge knowledge = new Knowledge();
    private boolean completeRuleSetUsed = true;
    private boolean doPossibleDsep = true;
    private int maxDiscriminatingPathLength = -1;
    private int depth = -1;
    private long elapsedTime;
    private boolean verbose;
    private boolean stable = true;
    private boolean guaranteePag;
    private ColliderRule r0ColliderRule = ColliderRule.SEPSETS;
    // Optional MAX-P extras (same as PC)
    private boolean maxPGlobalOrder = false;     // apply global order when orienting
    private boolean maxPDepthStratified = true;  // if global, apply by increasing |S|
    private double maxPMargin = 0.0;            // margin to resolve near-ties (0 => off)
    private boolean logMaxPTies = false;
    private java.io.PrintStream logStream = System.out;
    private boolean replicatingGraph = false;

    /**
     * Constructs an instance of the Fci algorithm using the specified independence test.
     *
     * @param test An {@link IndependenceTest} instance used to evaluate conditional independence among variables in the
     *             search. This cannot be null.
     * @throws NullPointerException If the provided {@code test} is null.
     */
    public Fci(IndependenceTest test) {
        if (test == null) throw new NullPointerException();
        this.test = new CachingIndependenceTest(test);
        this.variables.addAll(test.getVariables());
    }

    /**
     * Constructs an instance of the Fci algorithm using the specified independence test and a subset of variables to
     * include in the search.
     *
     * @param test       An {@link IndependenceTest} instance used to evaluate conditional independence among variables
     *                   in the search. This cannot be null.
     * @param searchVars A list of {@link Node} objects specifying the subset of variables to include in the search.
     *                   Only variables from this list will be considered. Variables not in this subset are removed from
     *                   the internal variable set.
     * @throws NullPointerException If the provided {@code test} is null.
     */
    public Fci(IndependenceTest test, List<Node> searchVars) {
        if (test == null) throw new NullPointerException();
        this.test = test;
        this.variables.addAll(test.getVariables());

        Set<Node> remVars = new HashSet<>();
        for (Node node1 : this.variables) {
            boolean search = false;
            for (Node node2 : searchVars) {
                if (node1.getName().equals(node2.getName())) {
                    search = true;
                    break;
                }
            }
            if (!search) remVars.add(node1);
        }
        this.variables.removeAll(remVars);
    }

    /**
     * Configures the R0 collider rule to be used in the FCI algorithm. The R0 collider rule determines how potential
     * colliders are oriented during the causal discovery process. If the provided rule is null, the default rule
     * {@code ColliderRule.SEPSETS} will be used.
     *
     * @param rule The {@link ColliderRule} option to specify the R0 collider rule. Possible values include
     *             {@code ColliderRule.SEPSETS}, {@code ColliderRule.CONSERVATIVE}, and {@code ColliderRule.MAX_P}. A
     *             null value will default to {@code ColliderRule.SEPSETS}.
     */
    public void setR0ColliderRule(ColliderRule rule) {
        this.r0ColliderRule = rule == null ? ColliderRule.SEPSETS : rule;
    }

    /**
     * Configures whether the global MAX-P order is enabled to avoid order dependence during the causal discovery
     * process in the FCI algorithm.
     *
     * @param enabled A boolean value to enable or disable the global MAX-P order. If true, the global MAX-P order is
     *                enforced; otherwise, it is not.
     */
    public void setMaxPGlobalOrder(boolean enabled) {
        this.maxPGlobalOrder = enabled;
    }

    /**
     * Configures whether the depth-specific stratification of the MAX-P condition is enabled during the FCI algorithm's
     * causal discovery process. This setting influences how null hypotheses are evaluated based on conditional
     * independence at varying depths.
     *
     * @param enabled A boolean value indicating whether to enable the depth-specific stratification of the MAX-P
     *                condition. If true, depth-specific stratification is enforced; otherwise, it is not.
     */
    public void setMaxPDepthStratified(boolean enabled) {
        this.maxPDepthStratified = enabled;
    }

    /**
     * Sets the maximum probability margin (max-P margin) to be used in the FCI algorithm during the causal discovery
     * process. This margin determines the threshold for probability-based decisions in conditional independence
     * evaluations. If a negative value is provided, it will be reset to 0.0.
     *
     * @param margin A double value representing the maximum probability margin to set. If negative, the margin will
     *               default to 0.0.
     */
    public void setMaxPMargin(double margin) {
        this.maxPMargin = Math.max(0.0, margin);
    }

    /**
     * Configures whether logging is enabled for ties in the MAX-P condition during the FCI algorithm's causal discovery
     * process. This setting primarily controls the output of informational logs when ties occur in conditional
     * independence evaluations.
     *
     * @param enabled A boolean value indicating whether to enable or disable logging for MAX-P condition ties. If true,
     *                logging is enabled; otherwise, it is not.
     */
    public void setLogMaxPTies(boolean enabled) {
        this.logMaxPTies = enabled;
    }

    /**
     * Sets the log stream for capturing output messages of the algorithm's execution. This stream allows logging
     * information to be directed to a specified PrintStream instance.
     *
     * @param out A {@link java.io.PrintStream} object representing the output stream to which logs will be written.
     *            Accepts {@code System.out}, {@code System.err}, or any other {@code PrintStream}. Can be set to
     *            {@code null} to disable logging.
     */
    public void setLogStream(java.io.PrintStream out) {
        this.logStream = out;
    }

    /**
     * Sets the maximum depth for the algorithm's search process. The depth controls the maximum number of edges that
     * can be traversed when determining conditional independence relations. A depth of -1 indicates no limit on the
     * depth, while non-negative values specify explicit limitations. Attempts to set a depth less than -1 result in an
     * {@link IllegalArgumentException}.
     *
     * @param depth An integer specifying the maximum depth. Must be -1 (unlimited) or a non-negative value (≥ 0).
     * @throws IllegalArgumentException If the provided {@code depth} is less than -1.
     */
    public void setDepth(int depth) {
        if (depth < -1) throw new IllegalArgumentException("Depth must be -1 (unlimited) or >= 0: " + depth);
        this.depth = depth;
    }

    /**
     * Returns the elapsed time recorded by the algorithm or process.
     *
     * @return A long value representing the elapsed time, typically measured in milliseconds.
     */
    public long getElapsedTime() {
        return this.elapsedTime;
    }

    /**
     * Returns the sepset map maintained by this instance of the FCI algorithm. The sepset map contains information
     * about the separating sets identified during the causal discovery process.
     *
     * @return A {@code SepsetMap} object representing the separating sets computed within the algorithm.
     */
    public SepsetMap getSepsets() {
        return this.sepsets;
    }

    /**
     * Retrieves the knowledge structure maintained by this instance.
     *
     * @return A {@link Knowledge} object representing the domain knowledge used or inferred by the algorithm.
     */
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * Sets the knowledge structure to be used or modified by the algorithm. The knowledge structure contains
     * domain-specific constraints and information that guide the causal discovery process.
     *
     * @param knowledge A {@link Knowledge} object representing the domain-specific knowledge to set. This cannot be
     *                  null.
     * @throws NullPointerException If the provided {@code knowledge} is null.
     */
    public void setKnowledge(Knowledge knowledge) {
        if (knowledge == null) throw new NullPointerException();
        this.knowledge = knowledge;
    }

    /**
     * Configures whether the complete rule set is used in the FCI algorithm during the causal discovery process. This
     * setting determines if all rules in the FCI framework will be applied.
     *
     * @param completeRuleSetUsed A boolean value indicating whether to enable or disable the usage of the complete rule
     *                            set. If true, the entire rule set is used; otherwise, a subset of the rules is
     *                            applied.
     */
    public void setCompleteRuleSetUsed(boolean completeRuleSetUsed) {
        this.completeRuleSetUsed = completeRuleSetUsed;
    }

    /**
     * Configures whether the possible-DSEP (Definite Separation) step is performed as part of the FCI algorithm during
     * the causal discovery process. The possible-DSEP step influences the identification of separating sets in the
     * graph by considering potential additional conditioning sets.
     *
     * @param doPossibleDsep A boolean value indicating whether to enable or disable the possible-DSEP step. If true,
     *                       the possible-DSEP step is performed; otherwise, it is not.
     */
    public void setDoPossibleDsep(boolean doPossibleDsep) {
        this.doPossibleDsep = doPossibleDsep;
    }

    /**
     * Sets the maximum discriminating path length for the algorithm or process. If this value is set to -1, there is no
     * limit on the path length. A non-negative value specifies the upper limit for the path length.
     *
     * @param maxDiscriminatingPathLength the maximum discriminating path length to set. Must be -1 (for no limit) or a
     *                                    non-negative integer.
     * @throws IllegalArgumentException if the specified value is less than -1.
     */
    public void setMaxDiscriminatingPathLength(int maxDiscriminatingPathLength) {
        if (maxDiscriminatingPathLength < -1)
            throw new IllegalArgumentException("Max path length must be -1 (unlimited) or >= 0: " + maxDiscriminatingPathLength);
        this.maxDiscriminatingPathLength = maxDiscriminatingPathLength;
    }

    /**
     * Sets the verbosity level for the system. When set to true, detailed logging or additional information may be
     * displayed or processed.
     *
     * @param verbose a boolean value where true enables verbose mode and false disables it.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
        test.setVerbose(verbose);
    }

    /**
     * Sets the stability status.
     *
     * @param stable a boolean value indicating whether the object is stable or not
     */
    public void setStable(boolean stable) {
        this.stable = stable;
    }

    /**
     * Sets the value of the guaranteePag flag.
     *
     * @param guaranteePag the boolean value to set as the guaranteePag flag
     */
    public void setGuaranteePag(boolean guaranteePag) {
        this.guaranteePag = guaranteePag;
    }

    /**
     * Searches and retrieves a graph using the specified algorithm.
     *
     * @return the resulting Graph object obtained from the search operation
     * @throws InterruptedException if the thread executing the search is interrupted
     */
    public Graph search() throws InterruptedException {
        return search(new Fas(getTest()));
    }

    /**
     * Executes the search process using the provided `IFas` implementation and performs various graph orientation and
     * refinement steps based on the FCI algorithm. This includes applying initial orientations, handling possible-dsep,
     * and finalizing the graph structure according to the defined rules.
     * <p>
     * The method logs details of the operations performed when the verbose flag is enabled. It also ensures the
     * resulting graph is in a valid PAG state if the guaranteePag option is set.
     *
     * @param fas Implementation of the `IFas` interface, providing the functionality for the Fast Adjacency Search
     *            (FAS) to find the skeleton of the graph. Must be properly configured before calling this method.
     * @return A `Graph` object representing the partially oriented graph (PAG) resulting from the search and
     * orientation rules applied.
     * @throws InterruptedException If the execution of the search process is interrupted.
     */
    public Graph search(IFas fas) throws InterruptedException {
        long start = MillisecondTimes.timeMillis();

//        Fas fas = new Fas(getIndependenceTest());

        fas.setReplicatingGraph(this.replicatingGraph);

        if (verbose) {
            TetradLogger.getInstance().log("Starting FCI algorithm.");
            TetradLogger.getInstance().log("Independence test = " + getTest() + ".");
        }

        fas.setKnowledge(getKnowledge());
        fas.setDepth(this.depth);
        fas.setVerbose(this.verbose);
        fas.setStable(this.stable);

        if (verbose) TetradLogger.getInstance().log("Starting FAS search.");

        Graph pag = fas.search();
        this.sepsets = fas.getSepsets();

        if (verbose) TetradLogger.getInstance().log("Reorienting with o-o.");
        pag.reorientAllWith(Endpoint.CIRCLE);

        // Build unshielded triple set once here (for guaranteePag); weâll refresh after possible-dsep as well.
        Set<edu.cmu.tetrad.graph.Triple> unshieldedTriples = collectUnshieldedTriplesAsGraphTriples(pag);

        // R0 with selected collider rule (replaces vanilla ruleR0 here)
        if (verbose) TetradLogger.getInstance().log("Applying R0 (" + r0ColliderRule + ").");
        orientR0(pag, this.sepsets);

        // Optional possible-dsep step (unchanged)
        R0R4StrategyTestBased strategy = (R0R4StrategyTestBased) R0R4StrategyTestBased.specialConfiguration(test, knowledge, verbose);
        strategy.setDepth(-1);
        strategy.setMaxLength(-1);
        strategy.setBlockingType(R0R4StrategyTestBased.BlockingType.GREEDY);

        FciOrient fciOrient = new FciOrient(strategy);
        fciOrient.setCompleteRuleSetUsed(completeRuleSetUsed);
        fciOrient.setMaxDiscriminatingPathLength(maxDiscriminatingPathLength);
        fciOrient.setVerbose(verbose);

        if (this.doPossibleDsep) {
            TetradLogger.getInstance().log("Doing possible dsep search.");

            for (Edge edge : new ArrayList<>(pag.getEdges())) {
                Node x = edge.getNode1();
                Node y = edge.getNode2();

                Set<Node> d = new HashSet<>(pag.paths().possibleDsep(x, 3));
                d.remove(x);
                d.remove(y);
                if (test.checkIndependence(x, y, d).isIndependent()) {
                    TetradLogger.getInstance().log("Removed " + pag.getEdge(x, y) + " by possible dsep");
                    pag.removeEdge(x, y);
                }

                if (pag.isAdjacentTo(x, y)) {
                    d = new HashSet<>(pag.paths().possibleDsep(y, 3));
                    d.remove(x);
                    d.remove(y);
                    if (test.checkIndependence(x, y, d).isIndependent()) {
                        TetradLogger.getInstance().log("Removed " + pag.getEdge(x, y) + " by possible dsep");
                        pag.removeEdge(x, y);
                    }
                }
            }

            // Reset marks and re-apply R0 with the chosen rule.
            pag.reorientAllWith(Endpoint.CIRCLE);
            if (verbose) TetradLogger.getInstance().log("Re-applying R0 after possible-dsep (" + r0ColliderRule + ").");
            orientR0(pag, this.sepsets);

            // Refresh unshielded triples after structural changes
            unshieldedTriples = collectUnshieldedTriplesAsGraphTriples(pag);
        }

        // Proceed with the remaining FCI orientation rules as usual
        if (verbose) TetradLogger.getInstance().log("Starting final FCI orientation.");
        fciOrient.finalOrientation(pag);
        if (verbose) TetradLogger.getInstance().log("Finished final FCI orientation.");

        if (guaranteePag) {
            pag = GraphUtils.guaranteePag(pag, fciOrient, knowledge, unshieldedTriples, verbose, new HashSet<>());
        }

        long stop = MillisecondTimes.timeMillis();
        this.elapsedTime = stop - start;
        return pag;
    }

    /**
     * Retrieves the IndependenceTest instance.
     *
     * @return the IndependenceTest instance
     */
    public IndependenceTest getTest() {
        return test;
    }

    /**
     * Sets the independence test for the current object. The method checks if the variables of the provided test match
     * the variables of the existing test. If they do not match, an IllegalArgumentException will be thrown.
     *
     * @param test the new IndependenceTest to be set. Must have the same list of variables as the existing test.
     * @throws IllegalArgumentException if the variables of the provided test do not match the variables of the current
     *                                  test.
     */
    public void setTest(IndependenceTest test) {
        List<Node> nodes = this.test.getVariables();
        List<Node> _nodes = test.getVariables();

        if (!nodes.equals(_nodes)) {
            throw new IllegalArgumentException(String.format("The nodes of the proposed new test are not equal list-wise\n" + "to the nodes of the existing test."));
        }

        this.test = test;
    }

    /**
     * Orients edges in the provided graph (pag) according to specific rules (R0 orientation rules) using unshielded
     * triples. The orientation is based on the collider rule specified (e.g., SEPSETS, CONSERVATIVE, MAX_P). Depending
     * on the rules and conditions, some edges are left unoriented.
     *
     * @param pag        The graph on which the R0 orientation rules will be applied. It is expected to have the
     *                   required structure and edges for the orientation process.
     * @param fasSepsets A map containing separation sets (sepsets) for pairs of nodes in the graph. This is used to
     *                   determine independencies or dependencies when applying the SEPSETS rule.
     * @throws InterruptedException If the process is interrupted during execution.
     */
    private void orientR0(Graph pag, SepsetMap fasSepsets) throws InterruptedException {
        List<TripleLocal> triples = collectUnshieldedTriplesLocal(pag);

        if (r0ColliderRule == ColliderRule.MAX_P && maxPGlobalOrder) {
            orientR0MaxPGlobal(pag, triples);
            return;
        }

        for (TripleLocal t : triples) {
            if (pag.isParentOf(t.x, t.z) && pag.isParentOf(t.y, t.z)) continue; // collider already

            ColliderOutcome out = switch (r0ColliderRule) {
                case SEPSETS -> {
                    Set<Node> s = fasSepsets.get(t.x, t.y);
                    if (s == null) yield ColliderOutcome.NO_SEPSET;
                    yield s.contains(t.z) ? ColliderOutcome.DEPENDENT : ColliderOutcome.INDEPENDENT;
                }
                case CONSERVATIVE -> judgeConservative(t, pag);
                case MAX_P -> judgeMaxP(t, pag);
            };

            if (out == ColliderOutcome.INDEPENDENT && canOrientCollider(pag, t.x, t.z, t.y)) {
                GraphUtils.orientCollider(pag, t.x, t.z, t.y);
                if (verbose)
                    TetradLogger.getInstance().log("[R0-" + r0ColliderRule + "] " + t.x.getName() + " -> " + t.z.getName() + " <- " + t.y.getName());
            }
            // DEPENDENT/NO_SEPSET/AMBIGUOUS -> leave as circles at z
        }
    }

    /**
     * Orients R0-Max-P global colliders in the given graph based on a collection of triples. This method determines the
     * best orientation of edges in a graph using statistical measures and optionally logs the results.
     *
     * @param pag     the graph object where the edges will be oriented based on the computed decisions
     * @param triples a list of triple structures containing candidate collider triples to evaluate
     * @throws InterruptedException if the thread executing the method is interrupted during processing
     */
    private void orientR0MaxPGlobal(Graph pag, List<TripleLocal> triples) throws InterruptedException {
        List<MaxPDecision> winners = new ArrayList<>();

        for (TripleLocal t : triples) {
            MaxPDecision d = decideMaxPDetail(t, pag);
            if (d.outcome == ColliderOutcome.INDEPENDENT) winners.add(d);
            else if (d.outcome == ColliderOutcome.AMBIGUOUS && logMaxPTies) {
                // details printed inside decideMaxPDetail when enabled
            }
        }

        if (maxPDepthStratified) {
            Map<Integer, List<MaxPDecision>> byDepth = new TreeMap<>();
            for (MaxPDecision d : winners) byDepth.computeIfAbsent(d.bestS.size(), k -> new ArrayList<>()).add(d);
            for (List<MaxPDecision> bucket : byDepth.values()) {
                bucket.sort(Comparator.comparingDouble((MaxPDecision m) -> m.bestP).reversed().thenComparing(m -> m.t.x.getName()).thenComparing(m -> m.t.z.getName()).thenComparing(m -> m.t.y.getName()).thenComparing(m -> stringifySet(m.bestS)));
                for (MaxPDecision d : bucket) {
                    if (canOrientCollider(pag, d.t.x, d.t.z, d.t.y)) {
                        GraphUtils.orientCollider(pag, d.t.x, d.t.z, d.t.y);
                        if (verbose)
                            TetradLogger.getInstance().log("[R0-MAXP global(d=" + d.bestS.size() + ")] " + d.t.x.getName() + " -> " + d.t.z.getName() + " <- " + d.t.y.getName() + " (p=" + d.bestP + ", S=" + stringifySet(d.bestS) + ")");
                    }
                }
            }
        } else {
            winners.sort(Comparator.comparingDouble((MaxPDecision d) -> d.bestP).reversed().thenComparing(d -> d.t.x.getName()).thenComparing(d -> d.t.z.getName()).thenComparing(d -> d.t.y.getName()).thenComparing(d -> stringifySet(d.bestS)));
            for (MaxPDecision d : winners) {
                if (canOrientCollider(pag, d.t.x, d.t.z, d.t.y)) {
                    GraphUtils.orientCollider(pag, d.t.x, d.t.z, d.t.y);
                    if (verbose)
                        TetradLogger.getInstance().log("[R0-MAXP global] " + d.t.x.getName() + " -> " + d.t.z.getName() + " <- " + d.t.y.getName() + " (p=" + d.bestP + ", S=" + stringifySet(d.bestS) + ")");
                }
            }
        }
    }

    private ColliderOutcome judgeConservative(TripleLocal t, Graph g) throws InterruptedException {
        Node x = t.x, y = t.y;
        if (x.getName().compareTo(y.getName()) > 0) {
            Node tmp = x;
            x = y;
            y = tmp;
        }

        boolean sawIncl = false, sawExcl = false, sawAny = false;
        for (SepCandidate cand : enumerateSepsetsWithPvals(x, y, g)) {
            if (!cand.independent) continue;
            sawAny = true;
            if (cand.S.contains(t.z)) sawIncl = true;
            else sawExcl = true;
            if (sawIncl && sawExcl) return ColliderOutcome.AMBIGUOUS;
        }
        if (!sawAny) return ColliderOutcome.NO_SEPSET;
        if (sawExcl && !sawIncl) return ColliderOutcome.INDEPENDENT;
        if (sawIncl && !sawExcl) return ColliderOutcome.DEPENDENT;
        return ColliderOutcome.AMBIGUOUS;
    }

    private ColliderOutcome judgeMaxP(TripleLocal t, Graph g) throws InterruptedException {
        return decideMaxPDetail(t, g).outcome;
    }

    private MaxPDecision decideMaxPDetail(TripleLocal t, Graph g) throws InterruptedException {
        Node x = t.x, y = t.y;
        if (x.getName().compareTo(y.getName()) > 0) {
            Node tmp = x;
            x = y;
            y = tmp;
        }

        List<SepCandidate> indep = new ArrayList<>();
        for (SepCandidate cand : enumerateSepsetsWithPvals(x, y, g)) {
            if (cand.independent) indep.add(cand);
        }
        if (indep.isEmpty()) return new MaxPDecision(t, ColliderOutcome.NO_SEPSET, Double.NaN, Collections.emptySet());

        double bestP = indep.stream().mapToDouble(c -> c.p).max().orElse(Double.NEGATIVE_INFINITY);
        List<SepCandidate> ties = new ArrayList<>();
        for (SepCandidate c : indep) if (c.p == bestP) ties.add(c);

        // Order ties deterministically, prefer S that EXCLUDE z when only logging/choosing a representative
        ties.sort(Comparator.comparing((SepCandidate c) -> c.S.contains(t.z)).thenComparing(c -> stringifySet(c.S)));

        double bestExcl = Double.NEGATIVE_INFINITY, bestIncl = Double.NEGATIVE_INFINITY;
        for (SepCandidate c : indep) {
            if (c.S.contains(t.z)) bestIncl = Math.max(bestIncl, c.p);
            else bestExcl = Math.max(bestExcl, c.p);
        }
        boolean hasExcl = bestExcl > Double.NEGATIVE_INFINITY;
        boolean hasIncl = bestIncl > Double.NEGATIVE_INFINITY;

        if (hasExcl && hasIncl) {
            if (bestExcl >= bestIncl + maxPMargin) {
                Set<Node> bestS = firstTieMatchingContainsZ(ties, t.z, false);
                return new MaxPDecision(t, ColliderOutcome.INDEPENDENT, bestExcl, bestS);
            }
            if (bestIncl >= bestExcl + maxPMargin) {
                Set<Node> bestS = firstTieMatchingContainsZ(ties, t.z, true);
                return new MaxPDecision(t, ColliderOutcome.DEPENDENT, bestIncl, bestS);
            }
            if (logMaxPTies && ties.size() > 1) debugPrintMaxPTies(t, bestP, ties);
            return new MaxPDecision(t, ColliderOutcome.AMBIGUOUS, Math.max(bestExcl, bestIncl), ties.isEmpty() ? Collections.emptySet() : ties.get(0).S);
        } else if (hasExcl) {
            Set<Node> bestS = firstTieMatchingContainsZ(ties, t.z, false);
            return new MaxPDecision(t, ColliderOutcome.INDEPENDENT, bestExcl, bestS);
        } else if (hasIncl) {
            Set<Node> bestS = firstTieMatchingContainsZ(ties, t.z, true);
            return new MaxPDecision(t, ColliderOutcome.DEPENDENT, bestIncl, bestS);
        } else {
            return new MaxPDecision(t, ColliderOutcome.NO_SEPSET, Double.NaN, Collections.emptySet());
        }
    }

    private Set<Node> firstTieMatchingContainsZ(List<SepCandidate> ties, Node z, boolean containsZ) {
        for (SepCandidate c : ties) if (c.S.contains(z) == containsZ) return c.S;
        return ties.isEmpty() ? Collections.emptySet() : ties.get(0).S;
    }

    // -------------------------
    // Enumeration of S (unique across both sides), depth-capped
    // -------------------------
    private Iterable<SepCandidate> enumerateSepsetsWithPvals(Node x, Node y, Graph g) throws InterruptedException {
        if (x.getName().compareTo(y.getName()) > 0) {
            Node tmp = x;
            x = y;
            y = tmp;
        }

        Map<String, SepCandidate> uniq = new LinkedHashMap<>();

        List<Node> adjx = new ArrayList<>(g.getAdjacentNodes(x));
        List<Node> adjy = new ArrayList<>(g.getAdjacentNodes(y));
        adjx.remove(y);
        adjy.remove(x);
        adjx.sort(Comparator.comparing(Node::getName));
        adjy.sort(Comparator.comparing(Node::getName));

        final int depthCap = (depth < 0) ? Integer.MAX_VALUE : depth;
        int maxAdj = Math.max(adjx.size(), adjy.size());

        for (int d = 0; d <= Math.min(depthCap, maxAdj); d++) {
            for (List adj : new List[]{adjx, adjy}) {
                if (d > adj.size()) continue;
                ChoiceGenerator gen = new ChoiceGenerator(adj.size(), d);
                int[] choice;
                while ((choice = gen.next()) != null) {
                    checkTimeout();
                    Set<Node> S = GraphUtils.asSet(choice, adj);
                    String key = setKey(S);
                    if (uniq.containsKey(key)) continue;

                    IndependenceResult r = test.checkIndependence(x, y, S);
                    uniq.put(key, new SepCandidate(S, r.isIndependent(), r.getPValue()));
                }
            }
        }
        return uniq.values();
    }

    // -------------------------
    // Utility helpers
    // -------------------------
    private boolean canOrientCollider(Graph g, Node x, Node z, Node y) {
        if (!g.isAdjacentTo(x, z) || !g.isAdjacentTo(z, y)) return false;
        if (!FciOrient.isArrowheadAllowed(x, z, g, knowledge) || !FciOrient.isArrowheadAllowed(y, z, g, knowledge))
            return false;
        // In PAGs we typically avoid creating arrowheads conflicting with existing tails at z->x / z->y
        if (g.isParentOf(z, x) || g.isParentOf(z, y)) return false;
        return true;
    }

    private void checkTimeout() throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Interrupted");
        // FCI doesn't have its own timeout knob here; add if desired.
    }

    private String setKey(Set<Node> S) {
        List<String> names = new ArrayList<>(S.stream().map(Node::getName).toList());
        Collections.sort(names);
        return String.join("\u0001", names);
    }

    private String stringifySet(Set<Node> S) {
        List<String> names = new ArrayList<>(S.stream().map(Node::getName).toList());
        Collections.sort(names);
        return "{" + String.join(",", names) + "}";
    }

    private void debugPrintMaxPTies(TripleLocal t, double bestP, List<SepCandidate> ties) {
        if (logStream == null) return;
        Node x = t.x, y = t.y;
        if (x.getName().compareTo(y.getName()) > 0) {
            Node tmp = x;
            x = y;
            y = tmp;
        }
        String header = "[R0-MAXP tie] pair=(" + x.getName() + "," + y.getName() + "), z=" + t.z.getName() + ", bestP=" + bestP + ", #ties=" + ties.size();
        logStream.println(header);
        for (SepCandidate c : ties) {
            boolean containsZ = c.S.contains(t.z);
            String line = "  S=" + stringifySet(c.S) + " | contains(z)=" + containsZ + " | p=" + c.p;
            logStream.println(line);
        }
    }

    // Unshielded triple collector (local form for orientation)
    private List<TripleLocal> collectUnshieldedTriplesLocal(Graph g) {
        List<Node> nodes = new ArrayList<>(g.getNodes());
        nodes.sort(Comparator.comparing(Node::getName));
        List<TripleLocal> triples = new ArrayList<>();
        for (Node z : nodes) {
            List<Node> adj = new ArrayList<>(g.getAdjacentNodes(z));
            adj.sort(Comparator.comparing(Node::getName));
            int m = adj.size();
            for (int i = 0; i < m; i++) {
                Node xi = adj.get(i);
                for (int j = i + 1; j < m; j++) {
                    Node yj = adj.get(j);
                    if (!g.isAdjacentTo(xi, yj)) {
                        Node x = xi, y = yj;
                        if (x.getName().compareTo(y.getName()) > 0) {
                            Node tmp = x;
                            x = y;
                            y = tmp;
                        }
                        triples.add(new TripleLocal(x, z, y));
                    }
                }
            }
        }
        triples.sort(Comparator.comparing((TripleLocal t) -> t.x.getName()).thenComparing(t -> t.z.getName()).thenComparing(t -> t.y.getName()));
        return triples;
    }

    // Unshielded triple collector as graph.Triple for guaranteePag bookkeeping
    private Set<edu.cmu.tetrad.graph.Triple> collectUnshieldedTriplesAsGraphTriples(Graph g) {
        Set<edu.cmu.tetrad.graph.Triple> set = new HashSet<>();
        for (TripleLocal t : collectUnshieldedTriplesLocal(g)) {
            set.add(new edu.cmu.tetrad.graph.Triple(t.x, t.z, t.y));
        }
        return set;
    }

    /**
     * Sets the state of the replicatingGraph flag.
     *
     * @param replicatingGraph a boolean value indicating whether the graph should be in a replicating state.
     */
    public void setReplicatingGraph(boolean replicatingGraph) {
        this.replicatingGraph = replicatingGraph;
    }

    /**
     * The ColliderRule enum defines the rules or strategies used to handle collider structures in causal inference or
     * related algorithms. A collider is a specific type of dependency structure that arises in probabilistic graphical
     * models and causal graphs.
     */
    public enum ColliderRule {
        /**
         * Indicates that separation sets (or conditional independence sets) are to be used for handling colliders.
         */
        SEPSETS,
        /**
         * Represents a more conservative approach to handling colliders, avoiding assumptions that may lead to
         * incorrect inferences.
         */
        CONSERVATIVE,
        /**
         * Refers to the rule where the maximum p-value is considered when deciding colliders, often used in statistical
         * tests or algorithms.
         */
        MAX_P
    }

    // -------------------------
    // CPC / MAX-P decisions (shared semantics with PC)
    // -------------------------
    private enum ColliderOutcome {INDEPENDENT, DEPENDENT, AMBIGUOUS, NO_SEPSET}

    private static final class TripleLocal {
        final Node x, z, y;

        TripleLocal(Node x, Node z, Node y) {
            this.x = x;
            this.z = z;
            this.y = y;
        }
    }

    private static final class SepCandidate {
        final Set<Node> S;
        final boolean independent;
        final double p;

        SepCandidate(Set<Node> S, boolean independent, double p) {
            List<Node> sorted = new ArrayList<>(S);
            sorted.sort(Comparator.comparing(Node::getName));
            this.S = new LinkedHashSet<>(sorted);
            this.independent = independent;
            this.p = p;
        }
    }

    private static final class MaxPDecision {
        final TripleLocal t;
        final ColliderOutcome outcome;
        final double bestP;
        final Set<Node> bestS;

        MaxPDecision(TripleLocal t, ColliderOutcome outcome, double bestP, Set<Node> bestS) {
            this.t = t;
            this.outcome = outcome;
            this.bestP = bestP;
            this.bestS = bestS;
        }
    }
}
