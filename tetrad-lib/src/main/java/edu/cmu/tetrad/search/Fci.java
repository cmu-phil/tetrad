package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.test.CachingIndependenceTest;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.utils.*;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.MillisecondTimes;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.*;

/**
 * FCI with configurable R0 collider orientation:
 *   VANILLA:  use the FAS sepset S(x,y); orient x->z<-y iff z ∉ S.
 *   CPC:      enumerate S ⊆ adj(x)\{y} and adj(y)\{x}; orient iff ALL separating sets exclude z;
 *             dependent iff ALL include z; otherwise ambiguous (no orientation).
 *   MAX_P:    among separating sets, choose S* with max p; orient independent iff z ∉ S*, dependent iff z ∈ S*.
 *
 * All other steps (possible-dsep, R1.., final orientation: Spirtes or Zhang) are unchanged.
 */
public final class Fci implements IGraphSearch {

    // -------------------------
    // Existing fields (unchanged)
    // -------------------------
    private final List<Node> variables = new ArrayList<>();
    private final IndependenceTest independenceTest;
    private final TetradLogger logger = TetradLogger.getInstance();
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

    // -------------------------
    // New: R0 collider-rule options (shared semantics with PC)
    // -------------------------
    public enum ColliderRule { VANILLA, CPC, MAX_P }
    private ColliderRule r0ColliderRule = ColliderRule.VANILLA;

    // Optional MAX-P extras (same as PC)
    private boolean maxPGlobalOrder = false;     // apply global order when orienting
    private boolean maxPDepthStratified = true;  // if global, apply by increasing |S|
    private double  maxPMargin = 0.0;            // margin to resolve near-ties (0 => off)
    private boolean logMaxPTies = false;
    private java.io.PrintStream logStream = System.out;

    // ----------------------------------
    // Constructors (unchanged signatures)
    // ----------------------------------
    public Fci(IndependenceTest independenceTest) {
        if (independenceTest == null) throw new NullPointerException();
        this.independenceTest = new CachingIndependenceTest(independenceTest);
        this.variables.addAll(independenceTest.getVariables());
    }

    public Fci(IndependenceTest independenceTest, List<Node> searchVars) {
        if (independenceTest == null) throw new NullPointerException();
        this.independenceTest = independenceTest;
        this.variables.addAll(independenceTest.getVariables());

        Set<Node> remVars = new HashSet<>();
        for (Node node1 : this.variables) {
            boolean search = false;
            for (Node node2 : searchVars) {
                if (node1.getName().equals(node2.getName())) { search = true; break; }
            }
            if (!search) remVars.add(node1);
        }
        this.variables.removeAll(remVars);
    }

    // -------------------------
    // Public knobs (existing + new)
    // -------------------------
    public void setR0ColliderRule(ColliderRule rule) { this.r0ColliderRule = rule == null ? ColliderRule.VANILLA : rule; }
    public void setMaxPGlobalOrder(boolean enabled) { this.maxPGlobalOrder = enabled; }
    public void setMaxPDepthStratified(boolean enabled) { this.maxPDepthStratified = enabled; }
    public void setMaxPMargin(double margin) { this.maxPMargin = Math.max(0.0, margin); }
    public void setLogMaxPTies(boolean enabled) { this.logMaxPTies = enabled; }
    public void setLogStream(java.io.PrintStream out) { this.logStream = out; }

    public void setDepth(int depth) {
        if (depth < -1) throw new IllegalArgumentException("Depth must be -1 (unlimited) or >= 0: " + depth);
        this.depth = depth;
    }
    public long getElapsedTime() { return this.elapsedTime; }
    public SepsetMap getSepsets() { return this.sepsets; }
    public Knowledge getKnowledge() { return this.knowledge; }
    public void setKnowledge(Knowledge knowledge) {
        if (knowledge == null) throw new NullPointerException();
        this.knowledge = knowledge;
    }
    public void setCompleteRuleSetUsed(boolean completeRuleSetUsed) { this.completeRuleSetUsed = completeRuleSetUsed; }
    public void setDoPossibleDsep(boolean doPossibleDsep) { this.doPossibleDsep = doPossibleDsep; }
    public void setMaxDiscriminatingPathLength(int maxDiscriminatingPathLength) {
        if (maxDiscriminatingPathLength < -1)
            throw new IllegalArgumentException("Max path length must be -1 (unlimited) or >= 0: " + maxDiscriminatingPathLength);
        this.maxDiscriminatingPathLength = maxDiscriminatingPathLength;
    }
    public void setVerbose(boolean verbose) { this.verbose = verbose; }
    public IndependenceTest getIndependenceTest() { return this.independenceTest; }
    public void setStable(boolean stable) { this.stable = stable; }
    public void setGuaranteePag(boolean guaranteePag) { this.guaranteePag = guaranteePag; }

    // -------------------------
    // Search
    // -------------------------

    public Graph search() throws InterruptedException {
        return search(new Fas(getIndependenceTest()));
    }

    public Graph search(IFas fas) throws InterruptedException {
        long start = MillisecondTimes.timeMillis();

//        Fas fas = new Fas(getIndependenceTest());

        if (verbose) {
            TetradLogger.getInstance().log("Starting FCI algorithm.");
            TetradLogger.getInstance().log("Independence test = " + getIndependenceTest() + ".");
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

        // Build unshielded triple set once here (for guaranteePag); we’ll refresh after possible-dsep as well.
        Set<edu.cmu.tetrad.graph.Triple> unshieldedTriples = collectUnshieldedTriplesAsGraphTriples(pag);

        // R0 with selected collider rule (replaces vanilla ruleR0 here)
        if (verbose) TetradLogger.getInstance().log("Applying R0 (" + r0ColliderRule + ").");
        orientR0(pag, this.sepsets);

        // Optional possible-dsep step (unchanged)
        R0R4StrategyTestBased strategy = (R0R4StrategyTestBased)
                R0R4StrategyTestBased.specialConfiguration(independenceTest, knowledge, verbose);
        strategy.setDepth(-1);
        strategy.setMaxLength(-1);
        strategy.setBlockingType(R0R4StrategyTestBased.BlockingType.GREEDY);

        FciOrient fciOrient = new FciOrient(strategy);
        fciOrient.setCompleteRuleSetUsed(completeRuleSetUsed);
        fciOrient.setMaxDiscriminatingPathLength(maxDiscriminatingPathLength);
        fciOrient.setVerbose(verbose);

        if (this.doPossibleDsep) {
            for (Edge edge : new ArrayList<>(pag.getEdges())) {
                Node x = edge.getNode1();
                Node y = edge.getNode2();

                Set<Node> d = new HashSet<>(pag.paths().possibleDsep(x, 3));
                d.remove(x); d.remove(y);
                if (independenceTest.checkIndependence(x, y, d).isIndependent()) {
                    TetradLogger.getInstance().log("Removed " + pag.getEdge(x, y) + " by possible dsep");
                    pag.removeEdge(x, y);
                }

                if (pag.isAdjacentTo(x, y)) {
                    d = new HashSet<>(pag.paths().possibleDsep(y, 3));
                    d.remove(x); d.remove(y);
                    if (independenceTest.checkIndependence(x, y, d).isIndependent()) {
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

    // -------------------------
    // R0 orientation with configurable collider rule
    // -------------------------
    private void orientR0(Graph pag, SepsetMap fasSepsets) throws InterruptedException {
        List<TripleLocal> triples = collectUnshieldedTriplesLocal(pag);

        if (r0ColliderRule == ColliderRule.MAX_P && maxPGlobalOrder) {
            orientR0MaxPGlobal(pag, triples);
            return;
        }

        for (TripleLocal t : triples) {
            if (pag.isParentOf(t.x, t.z) && pag.isParentOf(t.y, t.z)) continue; // collider already

            ColliderOutcome out = switch (r0ColliderRule) {
                case VANILLA -> {
                    Set<Node> s = fasSepsets.get(t.x, t.y);
                    if (s == null) yield ColliderOutcome.NO_SEPSET;
                    yield s.contains(t.z) ? ColliderOutcome.DEPENDENT : ColliderOutcome.INDEPENDENT;
                }
                case CPC   -> judgeConservative(t, pag);
                case MAX_P -> judgeMaxP(t, pag);
            };

            if (out == ColliderOutcome.INDEPENDENT && canOrientCollider(pag, t.x, t.z, t.y)) {
                GraphUtils.orientCollider(pag, t.x, t.z, t.y);
                if (verbose) TetradLogger.getInstance().log(
                        "[R0-" + r0ColliderRule + "] " + t.x.getName() + " -> " + t.z.getName() + " <- " + t.y.getName());
            }
            // DEPENDENT/NO_SEPSET/AMBIGUOUS -> leave as circles at z
        }
    }

    /** Global MAX-P order to avoid order dependence; same semantics as in PC. */
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
                bucket.sort(Comparator
                        .comparingDouble((MaxPDecision m) -> m.bestP).reversed()
                        .thenComparing(m -> m.t.x.getName())
                        .thenComparing(m -> m.t.z.getName())
                        .thenComparing(m -> m.t.y.getName())
                        .thenComparing(m -> stringifySet(m.bestS)));
                for (MaxPDecision d : bucket) {
                    if (canOrientCollider(pag, d.t.x, d.t.z, d.t.y)) {
                        GraphUtils.orientCollider(pag, d.t.x, d.t.z, d.t.y);
                        if (verbose) TetradLogger.getInstance().log(
                                "[R0-MAXP global(d=" + d.bestS.size() + ")] "
                                + d.t.x.getName() + " -> " + d.t.z.getName() + " <- " + d.t.y.getName()
                                + " (p=" + d.bestP + ", S=" + stringifySet(d.bestS) + ")");
                    }
                }
            }
        } else {
            winners.sort(Comparator
                    .comparingDouble((MaxPDecision d) -> d.bestP).reversed()
                    .thenComparing(d -> d.t.x.getName())
                    .thenComparing(d -> d.t.z.getName())
                    .thenComparing(d -> d.t.y.getName())
                    .thenComparing(d -> stringifySet(d.bestS)));
            for (MaxPDecision d : winners) {
                if (canOrientCollider(pag, d.t.x, d.t.z, d.t.y)) {
                    GraphUtils.orientCollider(pag, d.t.x, d.t.z, d.t.y);
                    if (verbose) TetradLogger.getInstance().log(
                            "[R0-MAXP global] " + d.t.x.getName() + " -> " + d.t.z.getName() + " <- " + d.t.y.getName()
                            + " (p=" + d.bestP + ", S=" + stringifySet(d.bestS) + ")");
                }
            }
        }
    }

    // -------------------------
    // CPC / MAX-P decisions (shared semantics with PC)
    // -------------------------
    private enum ColliderOutcome { INDEPENDENT, DEPENDENT, AMBIGUOUS, NO_SEPSET }

    private ColliderOutcome judgeConservative(TripleLocal t, Graph g) throws InterruptedException {
        Node x = t.x, y = t.y;
        if (x.getName().compareTo(y.getName()) > 0) { Node tmp = x; x = y; y = tmp; }

        boolean sawIncl = false, sawExcl = false, sawAny = false;
        for (SepCandidate cand : enumerateSepsetsWithPvals(x, y, g)) {
            if (!cand.independent) continue;
            sawAny = true;
            if (cand.S.contains(t.z)) sawIncl = true; else sawExcl = true;
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
        if (x.getName().compareTo(y.getName()) > 0) { Node tmp = x; x = y; y = tmp; }

        List<SepCandidate> indep = new ArrayList<>();
        for (SepCandidate cand : enumerateSepsetsWithPvals(x, y, g)) {
            if (cand.independent) indep.add(cand);
        }
        if (indep.isEmpty()) return new MaxPDecision(t, ColliderOutcome.NO_SEPSET, Double.NaN, Collections.emptySet());

        double bestP = indep.stream().mapToDouble(c -> c.p).max().orElse(Double.NEGATIVE_INFINITY);
        List<SepCandidate> ties = new ArrayList<>();
        for (SepCandidate c : indep) if (c.p == bestP) ties.add(c);

        // Order ties deterministically, prefer S that EXCLUDE z when only logging/choosing a representative
        ties.sort(Comparator
                .comparing((SepCandidate c) -> c.S.contains(t.z))
                .thenComparing(c -> stringifySet(c.S)));

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
            return new MaxPDecision(t, ColliderOutcome.AMBIGUOUS, Math.max(bestExcl, bestIncl),
                    ties.isEmpty() ? Collections.emptySet() : ties.get(0).S);
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
        if (x.getName().compareTo(y.getName()) > 0) { Node tmp = x; x = y; y = tmp; }

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
            for (List<Node> adj : new List[]{adjx, adjy}) {
                if (d > adj.size()) continue;
                ChoiceGenerator gen = new ChoiceGenerator(adj.size(), d);
                int[] choice;
                while ((choice = gen.next()) != null) {
                    checkTimeout();
                    Set<Node> S = GraphUtils.asSet(choice, adj);
                    String key = setKey(S);
                    if (uniq.containsKey(key)) continue;

                    IndependenceResult r = independenceTest.checkIndependence(x, y, S);
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
        if (!FciOrient.isArrowheadAllowed(x, z, g, knowledge) || !FciOrient.isArrowheadAllowed(y, z, g, knowledge)) return false;
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
        if (x.getName().compareTo(y.getName()) > 0) { Node tmp = x; x = y; y = tmp; }
        String header = "[R0-MAXP tie] pair=(" + x.getName() + "," + y.getName() + "), z=" + t.z.getName()
                        + ", bestP=" + bestP + ", #ties=" + ties.size();
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
                        if (x.getName().compareTo(y.getName()) > 0) { Node tmp = x; x = y; y = tmp; }
                        triples.add(new TripleLocal(x, z, y));
                    }
                }
            }
        }
        triples.sort(Comparator
                .comparing((TripleLocal t) -> t.x.getName())
                .thenComparing(t -> t.z.getName())
                .thenComparing(t -> t.y.getName()));
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

    private static final class TripleLocal {
        final Node x, z, y;
        TripleLocal(Node x, Node z, Node y) { this.x = x; this.z = z; this.y = y; }
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
            this.t = t; this.outcome = outcome; this.bestP = bestP; this.bestS = bestS;
        }
    }
}