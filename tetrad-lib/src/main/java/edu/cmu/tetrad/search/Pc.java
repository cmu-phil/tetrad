package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.test.CachingIndependenceTest;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.utils.MeekRules;
import edu.cmu.tetrad.search.utils.SepsetMap;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.*;

/**
 * # Pc (Unified "Classic PC")
 *
 * A single, configurable implementation of the classic PC algorithm:
 *  1) Skeleton via FAS (stable toggle)
 *  2) Orient unshielded triples as colliders using one of:
 *     - VANILLA: use the (first-found) sepset from FAS; orient X->Z<-Y iff Z ∉ S(X,Y).
 *     - CPC: Conservative PC; enumerate separating sets from adjacencies of X and Y; orient iff ALL separating sets exclude Z,
 *            dependent iff ALL include Z, otherwise ambiguous.
 *     - MAX_P: Among separating sets, pick S* with the largest p-value; orient independent iff Z ∉ S*, dependent iff Z ∈ S*.
 *            Ties that disagree ⇒ ambiguous. Optional global ordering by p (stable) with depth stratification.
 *  3) Close under Meek rules.
 *
 * Notes:
 *  - Deterministic processing (sorted names & tie-breakers).
 *  - No internal CI cache here; for speed, wrap your base test with CachingIndependenceTest.
 *  - Enumeration de-dups identical S across both sides (adj(X)\{Y} and adj(Y)\{X}) for clean logs & logic.
 *  - Optional margin guard for MAX_P decisions to avoid brittle calls.
 *  - Optional allowance of bidirected edges to illustrate ambiguous colliders (off by default).
 */
public class Pc implements IGraphSearch {

    private final IndependenceTest test;
    private Knowledge knowledge = new Knowledge();
    private int depth = -1;                  // -1 => no cap
    private boolean fasStable = true;        // PC-Stable skeleton
    private ColliderRule colliderRule = ColliderRule.VANILLA;
    private AllowBidirected allowBidirected = AllowBidirected.DISALLOW;
    private boolean verbose = false;

    private long timeoutMs = -1;             // <0 => no timeout
    private long startTimeMs = 0;

    // MAX-P options
    private boolean maxPGlobalOrder = false;     // if true, apply global order
    private boolean maxPDepthStratified = true;  // when global order is on, process by increasing |S|
    private double  maxPMargin = 0.0;            // margin guard; 0 => off

    // Optional tie logging for MAX_P
    private boolean logMaxPTies = false;
    private java.io.PrintStream logStream = System.out;
    private Fas fas = null;

    public Pc(IndependenceTest test) { this.test = new CachingIndependenceTest(test); }

    // ----- Configuration setters -----
    public void setKnowledge(Knowledge knowledge) { this.knowledge = new Knowledge(knowledge); }
    public void setDepth(int depth) { this.depth = depth; }
    public void setFasStable(boolean fasStable) { this.fasStable = fasStable; }
    public void setColliderRule(ColliderRule rule) { this.colliderRule = rule; }
    public void setAllowBidirected(AllowBidirected allow) { this.allowBidirected = allow; }
    public void setVerbose(boolean verbose) { this.verbose = verbose; }
    public void setTimeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; }
    public void setLogMaxPTies(boolean enabled) { this.logMaxPTies = enabled; }
    public void setLogStream(java.io.PrintStream out) { this.logStream = out; }
    /** Order-independent MAX-P collider orientation via global p-sort. */
    public void setMaxPGlobalOrder(boolean enabled) { this.maxPGlobalOrder = enabled; }
    /** When global MAX-P is used, process winners by increasing |S| (safer). */
    public void setMaxPDepthStratified(boolean enabled) { this.maxPDepthStratified = enabled; }
    /** Margin guard for MAX-P: require best side to exceed the other by at least margin; else ambiguous. */
    public void setMaxPMargin(double margin) { this.maxPMargin = Math.max(0.0, margin); }

    // ----- Entry points -----
    public Graph search() throws InterruptedException {
        return search(test.getVariables());
    }

    public Graph search(List<Node> nodes) throws InterruptedException {
        checkVars(nodes);
        this.startTimeMs = System.currentTimeMillis();

        // 1) Skeleton via FAS
        fas = new Fas(test);
        fas.setKnowledge(knowledge);
        fas.setDepth(depth);
        fas.setStable(fasStable);
        fas.setVerbose(verbose);

        Graph g = fas.search(nodes);
        SepsetMap sepsets = fas.getSepsets();

        // 2) Orient colliders
        orientUnshieldedTriples(g, sepsets);

        // 3) Meek rules to closure
        applyMeekRules(g);

        return g;
    }

    // ------------------------------------------------------------------------------------
    // Collider orientation
    // ------------------------------------------------------------------------------------
    private void orientUnshieldedTriples(Graph g, SepsetMap fasSepsets) throws InterruptedException {
        final List<Node> nodes = new ArrayList<>(g.getNodes());
        nodes.sort(Comparator.comparing(Node::getName));

        // Collect unshielded triples (X - Z - Y) with X != Y and X not adjacent to Y
        List<Triple> triples = new ArrayList<>();
        for (Node z : nodes) {
            List<Node> adj = new ArrayList<>(g.getAdjacentNodes(z));
            adj.sort(Comparator.comparing(Node::getName));
            int m = adj.size();
            for (int i = 0; i < m; i++) {
                Node x = adj.get(i);
                for (int j = i + 1; j < m; j++) {
                    Node y = adj.get(j);
                    if (!g.isAdjacentTo(x, y)) {
                        triples.add(new Triple(x, z, y));
                    }
                }
            }
        }

        // Deterministic processing order
        triples.sort(Comparator
                .comparing((Triple t) -> t.x.getName())
                .thenComparing(t -> t.z.getName())
                .thenComparing(t -> t.y.getName()));

        // Optional global MAX-P (order-independent)
        if (colliderRule == ColliderRule.MAX_P && maxPGlobalOrder) {
            orientMaxPGlobal(g, triples);
            return;
        }

        // Local (per-triple) path
        for (Triple t : triples) {
            checkTimeout();

            // Already collider? skip
            if (g.isParentOf(t.x, t.z) && g.isParentOf(t.y, t.z)) continue;

            ColliderOutcome outcome = switch (colliderRule) {
                case VANILLA -> {
                    Set<Node> s = fasSepsets.get(t.x, t.y);
                    if (s == null) yield ColliderOutcome.NO_SEPSET;
                    yield s.contains(t.z) ? ColliderOutcome.DEPENDENT : ColliderOutcome.INDEPENDENT;
                }
                case CPC   -> judgeConservative(t, g);
                case MAX_P -> judgeMaxP(t, g);
            };

            switch (outcome) {
                case INDEPENDENT -> {
                    if (canOrientCollider(g, t.x, t.z, t.y)) {
                        GraphUtils.orientCollider(g, t.x, t.z, t.y);
                        if (verbose) TetradLogger.getInstance().log(
                                "Collider oriented: " + t.x.getName() + " -> " + t.z.getName() + " <- " + t.y.getName());
                    }
                }
                case DEPENDENT, NO_SEPSET -> { /* leave unoriented */ }
                case AMBIGUOUS -> {
                    if (allowBidirected == AllowBidirected.ALLOW) {
                        // For illustration you could add bidirected marks here if your graph supports them.
                    }
                    if (verbose) TetradLogger.getInstance().log(
                            "Ambiguous triple: " + t.x.getName() + " - " + t.z.getName() + " - " + t.y.getName());
                }
            }
        }
    }

    /** Global, order-independent MAX-P collider orientation (optionally depth-stratified; avoids ↔). */
    private void orientMaxPGlobal(Graph g, List<Triple> triples) throws InterruptedException {
        // Phase 1: compute local MAX-P decisions; keep only INDEPENDENT winners
        List<MaxPDecision> winners = new ArrayList<>();
        for (Triple t : triples) {
            checkTimeout();
            MaxPDecision d = decideMaxPDetail(t, g);
            if (d.outcome == ColliderOutcome.AMBIGUOUS && logMaxPTies) {
                // tie details already printed in decideMaxPDetail if enabled
            }
            if (d.outcome == ColliderOutcome.INDEPENDENT) {
                winners.add(d);
            }
        }

        if (maxPDepthStratified) {
            // Phase 2a: bucket by |S| (conditioning depth)
            Map<Integer, List<MaxPDecision>> buckets = new TreeMap<>();
            for (MaxPDecision d : winners) {
                buckets.computeIfAbsent(d.bestS.size(), k -> new ArrayList<>()).add(d);
            }
            // Phase 3a: within each depth, sort by p desc (deterministic ties) and apply greedily
            for (Map.Entry<Integer, List<MaxPDecision>> e : buckets.entrySet()) {
                List<MaxPDecision> level = e.getValue();
                level.sort(Comparator
                        .comparingDouble((MaxPDecision m) -> m.bestP).reversed()
                        .thenComparing(m -> m.t.x.getName())
                        .thenComparing(m -> m.t.z.getName())
                        .thenComparing(m -> m.t.y.getName())
                        .thenComparing(m -> stringifySet(m.bestS)));
                for (MaxPDecision d : level) {
                    if (canOrientCollider(g, d.t.x, d.t.z, d.t.y)) {
                        GraphUtils.orientCollider(g, d.t.x, d.t.z, d.t.y);
                        if (verbose) TetradLogger.getInstance().log(
                                "[MAX-P global(d=" + d.bestS.size() + ")] " +
                                d.t.x.getName() + " -> " + d.t.z.getName() + " <- " + d.t.y.getName() +
                                " (p=" + d.bestP + ", S=" + stringifySet(d.bestS) + ")");
                    }
                }
            }
        } else {
            // Phase 2b/3b: single global ordering
            winners.sort(Comparator
                    .comparingDouble((MaxPDecision d) -> d.bestP).reversed()
                    .thenComparing(d -> d.t.x.getName())
                    .thenComparing(d -> d.t.z.getName())
                    .thenComparing(d -> d.t.y.getName())
                    .thenComparing(d -> stringifySet(d.bestS)));

            for (MaxPDecision d : winners) {
                if (canOrientCollider(g, d.t.x, d.t.z, d.t.y)) {
                    GraphUtils.orientCollider(g, d.t.x, d.t.z, d.t.y);
                    if (verbose) TetradLogger.getInstance().log(
                            "[MAX-P global] Collider oriented: " +
                            d.t.x.getName() + " -> " + d.t.z.getName() + " <- " + d.t.y.getName() +
                            " (p=" + d.bestP + ", S=" + stringifySet(d.bestS) + ")");
                }
            }
        }
    }

    // ----- CPC/MAX-P decisions -----------------------------------------------------------

    private ColliderOutcome judgeConservative(Triple t, Graph g) throws InterruptedException {
        boolean sawIncludesZ = false, sawExcludesZ = false, sawAny = false;

        for (SepCandidate cand : enumerateSepsetsWithPvals(t.x, t.y, g)) {
            if (!cand.independent) continue;
            sawAny = true;
            if (cand.S.contains(t.z)) sawIncludesZ = true; else sawExcludesZ = true;
            if (sawIncludesZ && sawExcludesZ) return ColliderOutcome.AMBIGUOUS;
        }

        if (!sawAny) return ColliderOutcome.NO_SEPSET;
        if (sawExcludesZ && !sawIncludesZ) return ColliderOutcome.INDEPENDENT;
        if (sawIncludesZ && !sawExcludesZ) return ColliderOutcome.DEPENDENT;
        return ColliderOutcome.AMBIGUOUS;
    }

    private ColliderOutcome judgeMaxP(Triple t, Graph g) throws InterruptedException {
        return decideMaxPDetail(t, g).outcome;
    }

    /**
     * Detailed MAX-P: returns outcome + bestP + bestS. Applies the margin guard:
     * if both sides have candidates and their best p's differ by < maxPMargin, returns AMBIGUOUS.
     */
    private MaxPDecision decideMaxPDetail(Triple t, Graph g) throws InterruptedException {
        List<SepCandidate> indep = new ArrayList<>();
        for (SepCandidate cand : enumerateSepsetsWithPvals(t.x, t.y, g)) {
            if (cand.independent) indep.add(cand);
        }
        if (indep.isEmpty()) return new MaxPDecision(t, ColliderOutcome.NO_SEPSET, Double.NaN, Collections.emptySet());

        // Overall best p (for logging / deterministic bestS selection)
        double bestP = indep.stream().mapToDouble(c -> c.p).max().orElse(Double.NEGATIVE_INFINITY);
        List<SepCandidate> ties = new ArrayList<>();
        for (SepCandidate c : indep) if (c.p == bestP) ties.add(c);
        ties.sort(Comparator
                .comparing((SepCandidate c) -> c.S.contains(t.z))   // prefer excludes-Z first in ordering
                .thenComparing(c -> stringifySet(c.S)));

        // Side-wise best p for margin guard
        double bestExcl = Double.NEGATIVE_INFINITY, bestIncl = Double.NEGATIVE_INFINITY;
        for (SepCandidate c : indep) {
            if (c.S.contains(t.z)) bestIncl = Math.max(bestIncl, c.p);
            else bestExcl = Math.max(bestExcl, c.p);
        }
        boolean hasExcl = bestExcl > Double.NEGATIVE_INFINITY;
        boolean hasIncl = bestIncl > Double.NEGATIVE_INFINITY;

        if (hasExcl && hasIncl) {
            if (bestExcl >= bestIncl + maxPMargin) {
                // choose a top excluding-Z set deterministically
                Set<Node> bestS = firstTieMatchingContainsZ(ties, t.z, false);
                return new MaxPDecision(t, ColliderOutcome.INDEPENDENT, bestExcl, bestS);
            }
            if (bestIncl >= bestExcl + maxPMargin) {
                Set<Node> bestS = firstTieMatchingContainsZ(ties, t.z, true);
                return new MaxPDecision(t, ColliderOutcome.DEPENDENT, bestIncl, bestS);
            }
            // within margin: ambiguous
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
        for (SepCandidate c : ties) {
            if (c.S.contains(z) == containsZ) return c.S;
        }
        return ties.isEmpty() ? Collections.emptySet() : ties.get(0).S;
    }

    // ----- enumeration (unique S across both sides), rely on external cache --------------

    /**
     * Enumerate S from adj(x)\{y} and adj(y)\{x}, de-duplicated across both sides
     * (by sorted-name key). Relies on the provided test (ideally wrapped with a cache)
     * to avoid redundant evaluations.
     */
    private Iterable<SepCandidate> enumerateSepsetsWithPvals(Node x, Node y, Graph g) throws InterruptedException {
        Map<String, SepCandidate> uniq = new LinkedHashMap<>(); // deterministic order

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
                    String sKey = setKey(S);
                    if (uniq.containsKey(sKey)) continue; // de-dup across sides

                    IndependenceResult r = test.checkIndependence(x, y, S);
                    uniq.put(sKey, new SepCandidate(S, r.isIndependent(), r.getPValue()));
                }
            }
        }
        return uniq.values();
    }

    private String setKey(Set<Node> S) {
        List<String> names = new ArrayList<>(S.stream().map(Node::getName).toList());
        Collections.sort(names);
        return String.join("\u0001", names); // non-printing field separator
    }

    private void debugPrintMaxPTies(Triple t, double bestP, List<SepCandidate> ties) {
        if (logStream == null) return;
        String header = "[MAX-P tie] pair=(" + t.x.getName() + "," + t.y.getName() + "), z=" + t.z.getName()
                        + ", bestP=" + bestP + ", #ties=" + ties.size();
        logStream.println(header);
        for (SepCandidate c : ties) {
            boolean containsZ = c.S.contains(t.z);
            String line = "  S=" + stringifySet(c.S) + " | contains(z)=" + containsZ + " | p=" + c.p;
            logStream.println(line);
        }
    }

    private String stringifySet(Set<Node> S) {
        List<String> names = new ArrayList<>(S.stream().map(Node::getName).toList());
        Collections.sort(names);
        return "{" + String.join(",", names) + "}";
    }

    // ----- checks / utils ---------------------------------------------------------------

    private boolean canOrientCollider(Graph g, Node x, Node z, Node y) {
        if (!g.isAdjacentTo(x, z) || !g.isAdjacentTo(z, y)) return false;
        if (allowBidirected != AllowBidirected.ALLOW && (g.isParentOf(z, x) || g.isParentOf(z, y))) return false;
        return true;
    }

    private void applyMeekRules(Graph g) throws InterruptedException {
        new MeekRules().orientImplied(g);
    }

    private void checkVars(List<Node> nodes) {
        if (!new HashSet<>(test.getVariables()).containsAll(nodes)) {
            throw new IllegalArgumentException("All nodes must be contained in the test's variables.");
        }
    }

    private void checkTimeout() throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Interrupted");
        if (timeoutMs >= 0) {
            long now = System.currentTimeMillis();
            if (now - startTimeMs > timeoutMs) throw new InterruptedException("Timed out after " + (now - startTimeMs) + " ms");
        }
    }

    public SepsetMap getSepsets() {
        if (this.fas == null) {
            throw new  IllegalStateException("SepsetMap not initialized");
        }

        return fas.getSepsets();
    }

    // ------------------------------------------------------------
    // Enums & small records
    // ------------------------------------------------------------
    public enum ColliderRule { VANILLA, CPC, MAX_P }
    public enum AllowBidirected { ALLOW, DISALLOW }
    private enum ColliderOutcome { INDEPENDENT, DEPENDENT, AMBIGUOUS, NO_SEPSET }

    private static final class Triple {
        final Node x, z, y;
        Triple(Node x, Node z, Node y) { this.x = x; this.z = z; this.y = y; }
    }

    private static final class SepCandidate {
        final Set<Node> S;         // deterministic storage
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
        final Triple t;
        final ColliderOutcome outcome;
        final double bestP;
        final Set<Node> bestS;
        MaxPDecision(Triple t, ColliderOutcome outcome, double bestP, Set<Node> bestS) {
            this.t = t;
            this.outcome = outcome;
            this.bestP = bestP;
            this.bestS = bestS;
        }
    }
}