package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.Triple;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.utils.MeekRules;
import edu.cmu.tetrad.search.utils.SepsetMap;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.*;

/**
 * Pc (Unified "Classic PC")
 * (…existing class-level Javadoc unchanged…)
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

    public Pc(IndependenceTest test) { this.test = test; }

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
    public void setMaxPGlobalOrder(boolean enabled) { this.maxPGlobalOrder = enabled; }
    public void setMaxPDepthStratified(boolean enabled) { this.maxPDepthStratified = enabled; }
    public void setMaxPMargin(double margin) { this.maxPMargin = Math.max(0.0, margin); }

    // ----- Entry points -----
    public Graph search() throws InterruptedException {
        return search(test.getVariables());
    }

    public Graph search(List<Node> nodes) throws InterruptedException {
        checkVars(nodes);
        this.startTimeMs = System.currentTimeMillis();

        // 1) Skeleton via FAS
        this.fas = new Fas(test);
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
    // NEW: Triple classification APIs
    // ------------------------------------------------------------------------------------

    /** Returns all unshielded triples that are definite colliders: x -> z <- y. */
    public List<Triple> getColliderTriples(Graph g) {
        List<Triple> result = new ArrayList<>();
        for (Triple t : collectUnshieldedTriples(g)) {
            if (g.isParentOf(t.getX(), t.getZ()) && g.isParentOf(t.getY(), t.getZ())) {
                result.add(t);
            }
        }
        return result;
    }

    /** Returns all unshielded triples that are definite noncolliders: x <- z -> y (both tails at z). */
    public List<Triple> getNoncolliderTriples(Graph g) {
        List<Triple> result = new ArrayList<>();
        for (Triple t : collectUnshieldedTriples(g)) {
            boolean intoZFromX = g.isParentOf(t.getX(), t.getZ());
            boolean intoZFromY = g.isParentOf(t.getY(), t.getZ());
            boolean outOfZToX  = g.isParentOf(t.getZ(), t.getX());
            boolean outOfZToY  = g.isParentOf(t.getZ(), t.getY());
            boolean undZX = isUndirected(g, t.getZ(), t.getX());
            boolean undZY = isUndirected(g, t.getZ(), t.getY());

            // Definite noncollider only when BOTH incident edges have tails at z
            // i.e., z->x or undirected (tail at z), and z->y or undirected,
            // and there are NO arrowheads pointing into z.
            if (!intoZFromX && !intoZFromY && (outOfZToX || undZX) && (outOfZToY || undZY)) {
                result.add(t);
            }
        }
        return result;
    }

    /**
     * Returns unshielded triples that are neither definite colliders nor definite noncolliders
     * under the current graph marks—i.e., mixed or unresolved at z (includes undirected/bidirected/mixed).
     */
    public List<Triple> getAmbiguousTriples(Graph g) {
        List<Triple> result = new ArrayList<>();
        for (Triple t : collectUnshieldedTriples(g)) {
            boolean collider = g.isParentOf(t.getX(), t.getZ()) && g.isParentOf(t.getY(), t.getZ());

            boolean intoZFromX = g.isParentOf(t.getX(), t.getZ());
            boolean intoZFromY = g.isParentOf(t.getY(), t.getZ());
            boolean outOfZToX  = g.isParentOf(t.getZ(), t.getX());
            boolean outOfZToY  = g.isParentOf(t.getZ(), t.getY());
            boolean undZX = isUndirected(g, t.getZ(), t.getX());
            boolean undZY = isUndirected(g, t.getZ(), t.getY());

            boolean noncollider = !intoZFromX && !intoZFromY && (outOfZToX || undZX) && (outOfZToY || undZY);

            if (!collider && !noncollider) {
                result.add(t);
            }
        }
        return result;
    }

    // Helper: collect all unshielded triples, deterministically ordered.
    private List<Triple> collectUnshieldedTriples(Graph g) {
        List<Node> nodes = new ArrayList<>(g.getNodes());
        nodes.sort(Comparator.comparing(Node::getName));

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
        // deterministic sort of triples
        triples.sort(Comparator
                .comparing((Triple t) -> t.getX().getName())
                .thenComparing(t -> t.getZ().getName())
                .thenComparing(t -> t.getY().getName()));
        return triples;
    }

    private boolean isUndirected(Graph g, Node a, Node b) {
        // Adjacent and neither is a parent of the other → undirected edge
        return g.isAdjacentTo(a, b) && !g.isParentOf(a, b) && !g.isParentOf(b, a);
    }

    // ------------------------------------------------------------------------------------
    // Collider orientation (existing implementation)
    // ------------------------------------------------------------------------------------
    private void orientUnshieldedTriples(Graph g, SepsetMap fasSepsets) throws InterruptedException {
        final List<Node> nodes = new ArrayList<>(g.getNodes());
        nodes.sort(Comparator.comparing(Node::getName));

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
                .comparing((Triple t) -> t.getX().getName())
                .thenComparing(t -> t.getZ().getName())
                .thenComparing(t -> t.getY().getName()));

        if (colliderRule == ColliderRule.MAX_P && maxPGlobalOrder) {
            orientMaxPGlobal(g, triples);
            return;
        }

        for (Triple t : triples) {
            checkTimeout();
            if (g.isParentOf(t.getX(), t.getZ()) && g.isParentOf(t.getY(), t.getZ())) continue;

            ColliderOutcome outcome = switch (colliderRule) {
                case VANILLA -> {
                    Set<Node> s = fasSepsets.get(t.getX(), t.getY());
                    if (s == null) yield ColliderOutcome.NO_SEPSET;
                    yield s.contains(t.getZ()) ? ColliderOutcome.DEPENDENT : ColliderOutcome.INDEPENDENT;
                }
                case CPC   -> judgeConservative(t, g);
                case MAX_P -> judgeMaxP(t, g);
            };

            switch (outcome) {
                case INDEPENDENT -> {
                    if (canOrientCollider(g, t.getX(), t.getZ(), t.getY())) {
                        GraphUtils.orientCollider(g, t.getX(), t.getZ(), t.getY());
                        if (verbose) TetradLogger.getInstance().log(
                                "Collider oriented: " + t.getX().getName() + " -> " + t.getZ().getName() + " <- " + t.getY().getName());
                    }
                }
                case DEPENDENT, NO_SEPSET -> { /* leave unoriented */ }
                case AMBIGUOUS -> {
                    if (allowBidirected == AllowBidirected.ALLOW) {
                        // Optionally mark as bidirected for illustration, if your Graph supports it.
                    }
                    if (verbose) TetradLogger.getInstance().log(
                            "Ambiguous triple: " + t.getX().getName() + " - " + t.getZ().getName() + " - " + t.getY().getName());
                }
            }
        }
    }

    private void orientMaxPGlobal(Graph g, List<Triple> triples) throws InterruptedException {
        List<MaxPDecision> winners = new ArrayList<>();
        for (Triple t : triples) {
            checkTimeout();
            MaxPDecision d = decideMaxPDetail(t, g);
            if (d.outcome == ColliderOutcome.AMBIGUOUS && logMaxPTies) {
                // tie details printed in decideMaxPDetail if enabled
            }
            if (d.outcome == ColliderOutcome.INDEPENDENT) {
                winners.add(d);
            }
        }

        if (maxPDepthStratified) {
            Map<Integer, List<MaxPDecision>> buckets = new TreeMap<>();
            for (MaxPDecision d : winners) {
                buckets.computeIfAbsent(d.bestS.size(), k -> new ArrayList<>()).add(d);
            }
            for (Map.Entry<Integer, List<MaxPDecision>> e : buckets.entrySet()) {
                List<MaxPDecision> level = e.getValue();
                level.sort(Comparator
                        .comparingDouble((MaxPDecision m) -> m.bestP).reversed()
                        .thenComparing(m -> m.t.getX().getName())
                        .thenComparing(m -> m.t.getZ().getName())
                        .thenComparing(m -> m.t.getY().getName())
                        .thenComparing(m -> stringifySet(m.bestS)));
                for (MaxPDecision d : level) {
                    if (canOrientCollider(g, d.t.getX(), d.t.getZ(), d.t.getY())) {
                        GraphUtils.orientCollider(g, d.t.getX(), d.t.getZ(), d.t.getY());
                        if (verbose) TetradLogger.getInstance().log(
                                "[MAX-P global(d=" + d.bestS.size() + ")] " +
                                d.t.getX().getName() + " -> " + d.t.getZ().getName() + " <- " + d.t.getY().getName() +
                                " (p=" + d.bestP + ", S=" + stringifySet(d.bestS) + ")");
                    }
                }
            }
        } else {
            winners.sort(Comparator
                    .comparingDouble((MaxPDecision d) -> d.bestP).reversed()
                    .thenComparing(d -> d.t.getX().getName())
                    .thenComparing(d -> d.t.getZ().getName())
                    .thenComparing(d -> d.t.getY().getName())
                    .thenComparing(d -> stringifySet(d.bestS)));

            for (MaxPDecision d : winners) {
                if (canOrientCollider(g, d.t.getX(), d.t.getZ(), d.t.getY())) {
                    GraphUtils.orientCollider(g, d.t.getX(), d.t.getZ(), d.t.getY());
                    if (verbose) TetradLogger.getInstance().log(
                            "[MAX-P global] Collider oriented: " +
                            d.t.getX().getName() + " -> " + d.t.getZ().getName() + " <- " + d.t.getY().getName() +
                            " (p=" + d.bestP + ", S=" + stringifySet(d.bestS) + ")");
                }
            }
        }
    }

    // ----- CPC/MAX-P decisions (unchanged) ---------------------------------------------

    private ColliderOutcome judgeConservative(Triple t, Graph g) throws InterruptedException {
        boolean sawIncludesZ = false, sawExcludesZ = false, sawAny = false;

        for (SepCandidate cand : enumerateSepsetsWithPvals(t.getX(), t.getY(), g)) {
            if (!cand.independent) continue;
            sawAny = true;
            if (cand.S.contains(t.getZ())) sawIncludesZ = true; else sawExcludesZ = true;
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

    private MaxPDecision decideMaxPDetail(Triple t, Graph g) throws InterruptedException {
        List<SepCandidate> indep = new ArrayList<>();
        for (SepCandidate cand : enumerateSepsetsWithPvals(t.getX(), t.getY(), g)) {
            if (cand.independent) indep.add(cand);
        }
        if (indep.isEmpty()) return new MaxPDecision(t, ColliderOutcome.NO_SEPSET, Double.NaN, Collections.emptySet());

        double bestP = indep.stream().mapToDouble(c -> c.p).max().orElse(Double.NEGATIVE_INFINITY);
        List<SepCandidate> ties = new ArrayList<>();
        for (SepCandidate c : indep) if (c.p == bestP) ties.add(c);
        ties.sort(Comparator
                .comparing((SepCandidate c) -> c.S.contains(t.getZ()))
                .thenComparing(c -> stringifySet(c.S)));

        double bestExcl = Double.NEGATIVE_INFINITY, bestIncl = Double.NEGATIVE_INFINITY;
        for (SepCandidate c : indep) {
            if (c.S.contains(t.getZ())) bestIncl = Math.max(bestIncl, c.p);
            else bestExcl = Math.max(bestExcl, c.p);
        }
        boolean hasExcl = bestExcl > Double.NEGATIVE_INFINITY;
        boolean hasIncl = bestIncl > Double.NEGATIVE_INFINITY;

        if (hasExcl && hasIncl) {
            if (bestExcl >= bestIncl + maxPMargin) {
                Set<Node> bestS = firstTieMatchingContainsZ(ties, t.getZ(), false);
                return new MaxPDecision(t, ColliderOutcome.INDEPENDENT, bestExcl, bestS);
            }
            if (bestIncl >= bestExcl + maxPMargin) {
                Set<Node> bestS = firstTieMatchingContainsZ(ties, t.getZ(), true);
                return new MaxPDecision(t, ColliderOutcome.DEPENDENT, bestIncl, bestS);
            }
            if (logMaxPTies && ties.size() > 1) debugPrintMaxPTies(t, bestP, ties);
            return new MaxPDecision(t, ColliderOutcome.AMBIGUOUS, Math.max(bestExcl, bestIncl),
                    ties.isEmpty() ? Collections.emptySet() : ties.get(0).S);
        } else if (hasExcl) {
            Set<Node> bestS = firstTieMatchingContainsZ(ties, t.getZ(), false);
            return new MaxPDecision(t, ColliderOutcome.INDEPENDENT, bestExcl, bestS);
        } else if (hasIncl) {
            Set<Node> bestS = firstTieMatchingContainsZ(ties, t.getZ(), true);
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
        return String.join("\u0001", names);
    }

    private void debugPrintMaxPTies(Triple t, double bestP, List<SepCandidate> ties) {
        if (logStream == null) return;
        String header = "[MAX-P tie] pair=(" + t.getX().getName() + "," + t.getY().getName() + "), z=" + t.getZ().getName()
                        + ", bestP=" + bestP + ", #ties=" + ties.size();
        logStream.println(header);
        for (SepCandidate c : ties) {
            boolean containsZ = c.S.contains(t.getZ());
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

    public Fas getFas() {
        return fas;
    }

    // ------------------------------------------------------------
    // Enums & small records
    // ------------------------------------------------------------
    public enum ColliderRule { VANILLA, CPC, MAX_P }
    public enum AllowBidirected { ALLOW, DISALLOW }
    private enum ColliderOutcome { INDEPENDENT, DEPENDENT, AMBIGUOUS, NO_SEPSET }

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