package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.utils.MeekRules;
import edu.cmu.tetrad.search.utils.SepsetMap;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Classic PC with VANILLA / CPC / MAX_P collider rules.
 * CPC/MAX_P enumeration uses S ⊆ adj(x)\{y} and adj(y)\{x} (z may be in S).
 * Memoizes independence calls with unordered (x,y)+sorted(S) keys.
 */
public class ClassicPc {

    private final IndependenceTest test;
    private Knowledge knowledge = new Knowledge();
    private int depth = -1;                  // -1 => no cap
    private boolean fasStable = true;        // PC-Stable skeleton
    private ColliderRule colliderRule = ColliderRule.VANILLA;
    private AllowBidirected allowBidirected = AllowBidirected.DISALLOW;
    private boolean verbose = false;
    private long timeoutMs = -1;             // <0 => no timeout
    private long startTimeMs = 0;

    // Optional: stabilize MAX-P by globally ordering colliders by best p-value
    private boolean maxPGlobalOrder = false;

    // Tie logging for MAX_P:
    private boolean logMaxPTies = false;
    private java.io.PrintStream logStream = System.out;

    // Memo table for CI: unordered (x,y) + sorted S
    private Map<SepKey, SepResult> sepCache = new ConcurrentHashMap<>(1 << 14);

    public ClassicPc(IndependenceTest test) { this.test = test; }

    public void setKnowledge(Knowledge knowledge) { this.knowledge = new Knowledge(knowledge); }
    public void setDepth(int depth) { this.depth = depth; }
    public void setFasStable(boolean fasStable) { this.fasStable = fasStable; }
    public void setColliderRule(ColliderRule rule) { this.colliderRule = rule; }
    public void setAllowBidirected(AllowBidirected allow) { this.allowBidirected = allow; }
    public void setVerbose(boolean verbose) { this.verbose = verbose; }
    public void setTimeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; }
    public void setLogMaxPTies(boolean enabled) { this.logMaxPTies = enabled; }
    public void setLogStream(java.io.PrintStream out) { this.logStream = out; }
    public void setSharedSepCache(Map<SepKey, SepResult> shared) {
        if (shared == null) throw new IllegalArgumentException("shared cache is null");
        this.sepCache = shared;
    }
    /** Turn on order-independent MAX-P collider orientation (global sort by p). */
    public void setMaxPGlobalOrder(boolean enabled) { this.maxPGlobalOrder = enabled; }

    public Graph search() throws InterruptedException {
        return search(test.getVariables());
    }

    public Graph search(List<Node> nodes) throws InterruptedException {
        checkVars(nodes);
        this.startTimeMs = System.currentTimeMillis();
        // this.sepCache.clear(); // keep shared if injected

        // 1) Skeleton via FAS
        Fas fas = new Fas(test);
        fas.setKnowledge(knowledge);
        fas.setDepth(depth);
        fas.setStable(fasStable);
        fas.setVerbose(verbose);

        Graph g = fas.search(nodes);
        SepsetMap sepsets = fas.getSepsets();

        // 2) Orient colliders
        orientUnshieldedTriples(g, sepsets);

        // 3) Meek rules
        applyMeekRules(g);
        return g;
    }

    // ------------------------------------------------------------------------------------
    // Collider orientation
    // ------------------------------------------------------------------------------------
    private void orientUnshieldedTriples(Graph g, SepsetMap fasSepsets) throws InterruptedException {
        final List<Node> nodes = new ArrayList<>(g.getNodes());
        nodes.sort(Comparator.comparing(Node::getName));

        // Collect unshielded triples (X - Z - Y), X != Y, X !~ Y
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
        triples.sort(Comparator
                .comparing((Triple t) -> t.x.getName())
                .thenComparing(t -> t.z.getName())
                .thenComparing(t -> t.y.getName()));

        if (colliderRule == ColliderRule.MAX_P && maxPGlobalOrder) {
            orientMaxPGlobal(g, triples); // new stable global-order path
            return;
        }

        // Existing per-triple path
        for (Triple t : triples) {
            checkTimeout();
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
                        // hook if you want to illustrate ambiguity
                        // GraphUtils.makeBidirected(g, t.x, t.z);
                        // GraphUtils.makeBidirected(g, t.y, t.z);
                    }
                    if (verbose) TetradLogger.getInstance().log(
                            "Ambiguous triple: " + t.x.getName() + " - " + t.z.getName() + " - " + t.y.getName());
                }
            }
        }
    }

    /** Global, order-independent MAX-P collider orientation (no ↔ needed). */
    private void orientMaxPGlobal(Graph g, List<Triple> triples) throws InterruptedException {
        // Phase 1: compute decisions and keep only INDEPENDENT ones with their bestP
        List<MaxPDecision> winners = new ArrayList<>();
        for (Triple t : triples) {
            checkTimeout();
            MaxPDecision d = decideMaxPDetail(t, g);
            if (d.outcome == ColliderOutcome.AMBIGUOUS && logMaxPTies) {
                // Already printed inside decideMaxPDetail if logging is on
            }
            if (d.outcome == ColliderOutcome.INDEPENDENT) {
                winners.add(d);
            }
        }
        // Phase 2: sort by p desc (then deterministic tie-breakers)
        winners.sort(Comparator
                .comparingDouble((MaxPDecision d) -> d.bestP).reversed()
                .thenComparing(d -> d.t.x.getName())
                .thenComparing(d -> d.t.z.getName())
                .thenComparing(d -> d.t.y.getName())
                .thenComparing(d -> stringifySet(d.bestS)));

        // Phase 3: apply greedily if still feasible (prevents order dependence & ↔)
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

    /** Detailed MAX-P: returns outcome + bestP + bestS (and prints ties if enabled). */
    private MaxPDecision decideMaxPDetail(Triple t, Graph g) throws InterruptedException {
        List<SepCandidate> indep = new ArrayList<>();
        for (SepCandidate cand : enumerateSepsetsWithPvals(t.x, t.y, g)) {
            if (cand.independent) indep.add(cand);
        }
        if (indep.isEmpty()) return new MaxPDecision(t, ColliderOutcome.NO_SEPSET, Double.NaN, Collections.emptySet());

        double bestP = indep.stream().mapToDouble(c -> c.p).max().orElse(Double.NEGATIVE_INFINITY);
        List<SepCandidate> ties = new ArrayList<>();
        for (SepCandidate c : indep) if (c.p == bestP) ties.add(c);
        ties.sort(Comparator
                .comparing((SepCandidate c) -> c.S.contains(t.z))
                .thenComparing(c -> stringifySet(c.S)));

        boolean anyExcludesZ = ties.stream().anyMatch(c -> !c.S.contains(t.z));
        boolean anyIncludesZ = ties.stream().anyMatch(c ->  c.S.contains(t.z));
        if (logMaxPTies && ties.size() > 1) debugPrintMaxPTies(t, bestP, ties);

        if (anyExcludesZ && anyIncludesZ) {
            return new MaxPDecision(t, ColliderOutcome.AMBIGUOUS, bestP, ties.get(0).S);
        }
        // All top ties agree; pick the first deterministically
        Set<Node> bestS = ties.get(0).S;
        ColliderOutcome out = anyExcludesZ ? ColliderOutcome.INDEPENDENT : ColliderOutcome.DEPENDENT;
        return new MaxPDecision(t, out, bestP, bestS);
    }

    // ----- enumeration (unique S across both sides), memoized CI ------------------------

    private Iterable<SepCandidate> enumerateSepsetsWithPvals(Node x, Node y, Graph g) throws InterruptedException {
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
                    String sKey = setKey(S);
                    if (uniq.containsKey(sKey)) continue; // de-dup

                    SepKey key = SepKey.of(x, y, S);
                    SepResult res = sepCache.get(key);
                    if (res == null) {
                        IndependenceResult r;
                        try {
                            r = test.checkIndependence(x, y, S);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw e;
                        }
                        res = new SepResult(r.isIndependent(), r.getPValue());
                        sepCache.put(key, res);
                    }

                    uniq.put(sKey, new SepCandidate(S, res.independent, res.p));
                }
            }
        }
        return uniq.values();
    }

    private String setKey(Set<Node> S) {
        List<String> names = new ArrayList<>(S.stream().map(Node::getName).toList());
        Collections.sort(names);
        return String.join("\u0001", names); // \u0001 as safe separator
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

    public static final class SepKey {
        public final String a, b;
        public final List<String> sNames;
        private SepKey(String a, String b, List<String> sNames) { this.a = a; this.b = b; this.sNames = sNames; }
        public static SepKey of(Node x, Node y, Set<Node> S) {
            String nx = x.getName(), ny = y.getName();
            String a = (nx.compareTo(ny) <= 0) ? nx : ny;
            String b = (nx.compareTo(ny) <= 0) ? ny : nx;
            List<String> names = new ArrayList<>(S.stream().map(Node::getName).toList());
            Collections.sort(names);
            return new SepKey(a, b, Collections.unmodifiableList(names));
        }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof SepKey)) return false;
            SepKey k = (SepKey) o;
            return a.equals(k.a) && b.equals(k.b) && sNames.equals(k.sNames);
        }
        @Override public int hashCode() { return Objects.hash(a, b, sNames); }
    }

    public static final class SepResult {
        public final boolean independent;
        public final double p;
        public SepResult(boolean independent, double p) { this.independent = independent; this.p = p; }
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