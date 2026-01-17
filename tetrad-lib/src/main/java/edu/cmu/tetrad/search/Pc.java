package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.utils.MeekRules;
import edu.cmu.tetrad.search.utils.SepsetMap;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.*;

/**
 * Pc (Unified "Classic PC")
 * Skeleton via FAS (stable toggle), orient unshielded triples using VANILLA/CPC/MAX_P,
 * then Meek rules to closure. This variant prevents directed cycles from being introduced
 * during collider orientation or Meek closure.
 */
public class Pc implements IGraphSearch {

    private IndependenceTest test;
    private Knowledge knowledge = new Knowledge();
    private int depth = -1;
    private boolean fasStable = true;

    private ColliderOrientationStyle colldierOrientationStyle = ColliderOrientationStyle.SEPSETS;

    private AllowBidirected allowBidirected = AllowBidirected.DISALLOW;

    private boolean verbose = false;

    private long timeoutMs = -1;
    private long startTimeMs = 0;

    // MAX-P options
    private boolean maxPGlobalOrder = false;
    private boolean maxPDepthStratified = true;
    private double maxPMargin = 0.0;
    private boolean logMaxPTies = false;
    private java.io.PrintStream logStream = System.out;

    private Fas fas = null;
    private boolean replicatingGraph = false;

    // ---------------- NEW: cycle-safety knobs ----------------
    /** If true, do not allow any orientation that creates a directed cycle. */
    private boolean forbidDirectedCycles = true;

    /**
     * If true, Meek closure is applied in a cycle-safe incremental fashion:
     * attempt one implied orientation at a time; rollback if it creates a cycle.
     */
    private boolean meekCycleSafe = true;

    public Pc(IndependenceTest test) {
        this.test = test;
    }

    private static boolean isArrowheadAllowed(Node from, Node to, Knowledge knowledge) {
        if (knowledge.isEmpty()) return true;
        return !knowledge.isRequired(to.toString(), from.toString())
                && !knowledge.isForbidden(from.toString(), to.toString());
    }

    // ----- Configuration setters -----

    public void setKnowledge(Knowledge knowledge) { this.knowledge = new Knowledge(knowledge); }

    public void setDepth(int depth) { this.depth = depth; }

    public void setFasStable(boolean fasStable) { this.fasStable = fasStable; }

    public void setColliderOrientationStyle(ColliderOrientationStyle rule) { this.colldierOrientationStyle = rule; }

    public void setAllowBidirected(AllowBidirected allow) { this.allowBidirected = allow; }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
        test.setVerbose(verbose);
    }

    public void setTimeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; }

    public void setLogMaxPTies(boolean enabled) { this.logMaxPTies = enabled; }

    public void setLogStream(java.io.PrintStream out) { this.logStream = out; }

    public void setMaxPGlobalOrder(boolean enabled) { this.maxPGlobalOrder = enabled; }

    public void setMaxPDepthStratified(boolean enabled) { this.maxPDepthStratified = enabled; }

    public void setMaxPMargin(double margin) { this.maxPMargin = Math.max(0.0, margin); }

    // NEW knobs (optional)
    public void setForbidDirectedCycles(boolean enabled) { this.forbidDirectedCycles = enabled; }

    public void setMeekCycleSafe(boolean enabled) { this.meekCycleSafe = enabled; }

    // ----- Entry points -----

    @Override
    public Graph search() throws InterruptedException {
        return search(test.getVariables());
    }

    public Graph search(List<Node> nodes) throws InterruptedException {
        checkVars(nodes);
        this.startTimeMs = System.currentTimeMillis();

        // 1) Skeleton via FAS
        this.fas = new Fas(test);
        fas.setReplicatingGraph(replicatingGraph);
        fas.setKnowledge(knowledge);
        fas.setDepth(depth);
        fas.setStable(fasStable);
        fas.setVerbose(verbose);

        Graph g = fas.search(nodes);
        SepsetMap sepsets = fas.getSepsets();

        // 2) Orient colliders
        orientUnshieldedTriples(g, sepsets);

        // 3) Meek rules to closure (cycle-safe)
        applyMeekRules(g);

        return g;
    }

    public IndependenceTest getTest() { return test; }

    public void setTest(IndependenceTest test) {
        List<Node> nodes = this.test.getVariables();
        List<Node> _nodes = test.getVariables();
        if (!nodes.equals(_nodes)) {
            throw new IllegalArgumentException(
                    "The nodes of the proposed new test are not equal list-wise to the nodes of the existing test."
            );
        }
        this.test = test;
    }

    public Fas getFas() { return fas; }

    public void setReplicatingGraph(boolean replicatingGraph) { this.replicatingGraph = replicatingGraph; }

    // ------------------------------------------------------------------------------------
    // Triple helpers
    // ------------------------------------------------------------------------------------

    private List<Triple> collectUnshieldedTriples(Graph g) {
        List<Node> nodes = new ArrayList<>(g.getNodes());
        nodes.sort(Comparator.comparing(Node::getName));

        List<Triple> triples = new ArrayList<>();
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
                            Node tmp = x; x = y; y = tmp;
                        }
                        triples.add(new Triple(x, z, y));
                    }
                }
            }
        }
        triples.sort(Comparator.comparing((Triple t) -> t.x.getName())
                .thenComparing(t -> t.z.getName())
                .thenComparing(t -> t.y.getName()));
        return triples;
    }

    private static String stringifySet(Set<Node> S) {
        List<String> names = new ArrayList<>(S.stream().map(Node::getName).toList());
        Collections.sort(names);
        return "{" + String.join(",", names) + "}";
    }

    // ------------------------------------------------------------------------------------
    // Collider orientation (cycle-safe)
    // ------------------------------------------------------------------------------------

    private void orientUnshieldedTriples(Graph g, SepsetMap fasSepsets) throws InterruptedException {
        List<Triple> triples = collectUnshieldedTriples(g);

        if (colldierOrientationStyle == ColliderOrientationStyle.MAX_P && maxPGlobalOrder) {
            orientMaxPGlobal(g, triples);
            return;
        }

        List<Triple> ambiguousTriples = new ArrayList<>();

        for (Triple t : triples) {
            checkTimeout();

            // Already collider? skip
            if (g.isParentOf(t.x, t.z) && g.isParentOf(t.y, t.z)) continue;

            ColliderOutcome outcome = switch (colldierOrientationStyle) {
                case SEPSETS -> {
                    Set<Node> s = fasSepsets.get(t.x, t.y);
                    if (s == null) yield ColliderOutcome.NO_SEPSET;
                    yield s.contains(t.z) ? ColliderOutcome.DEPENDENT : ColliderOutcome.INDEPENDENT;
                }
                case CONSERVATIVE -> judgeConservative(t, g);
                case MAX_P -> judgeMaxP(t, g);
            };

            switch (outcome) {
                case INDEPENDENT -> {
                    if (canOrientCollider(g, t.x, t.z, t.y)) {
                        // orientCollider does both x->z and y->z
                        GraphUtils.orientCollider(g, t.x, t.z, t.y);
                        if (verbose) {
                            TetradLogger.getInstance().log(
                                    "Collider oriented: " + t.x.getName() + " -> " + t.z.getName() +
                                            " <- " + t.y.getName());
                        }
                    } else if (verbose) {
                        TetradLogger.getInstance().log(
                                "Skipped collider (cycle/knowledge/bidir guard): " +
                                        t.x.getName() + " - " + t.z.getName() + " - " + t.y.getName());
                    }
                }
                case DEPENDENT, NO_SEPSET -> { /* leave unoriented */ }
                case AMBIGUOUS -> {
                    if (allowBidirected == AllowBidirected.ALLOW) ambiguousTriples.add(t);
                    if (verbose) {
                        TetradLogger.getInstance().log(
                                "Ambiguous triple: " + t.x.getName() + " - " + t.z.getName() + " - " + t.y.getName());
                    }
                }
            }
        }

        // store ambiguous triple marks
        Set<edu.cmu.tetrad.graph.Triple> _ambiguousTriples = new HashSet<>();
        for (Triple t : ambiguousTriples) _ambiguousTriples.add(new edu.cmu.tetrad.graph.Triple(t.x, t.z, t.y));
        g.setAmbiguousTriples(_ambiguousTriples);
    }

    /**
     * Cycle/knowledge/bidirected-safe collider orientation gate.
     *
     * Forbids:
     *  - violating knowledge arrowhead constraints
     *  - creating bidirected (unless allowed)
     *  - creating a directed cycle (if enabled)
     */
    private boolean canOrientCollider(Graph g, Node x, Node z, Node y) {
        if (!g.isAdjacentTo(x, z) || !g.isAdjacentTo(z, y)) return false;

        // knowledge: require arrowheads into z allowed
        if (!isArrowheadAllowed(x, z, knowledge) || !isArrowheadAllowed(y, z, knowledge)) return false;

        // if bidirected not allowed, disallow if z already points to x or y (would create <->)
        if (allowBidirected != AllowBidirected.ALLOW && (g.isParentOf(z, x) || g.isParentOf(z, y))) return false;

        if (forbidDirectedCycles) {
            // If there is already a directed path z -> ... -> x, then x -> z would create a cycle.
            if (g.paths().existsDirectedPath(z, x)) return false;
            if (g.paths().existsDirectedPath(z, y)) return false;
        }

        return true;
    }

    // ------------------------------------------------------------------------------------
    // MAX-P / Conservative logic (same as your code, but left intact)
    // ------------------------------------------------------------------------------------

    private void orientMaxPGlobal(Graph g, List<Triple> triples) throws InterruptedException {
        List<MaxPDecision> winners = new ArrayList<>();
        for (Triple t : triples) {
            checkTimeout();
            MaxPDecision d = decideMaxPDetail(t, g);
            if (d.outcome == ColliderOutcome.INDEPENDENT) winners.add(d);
        }

        if (maxPDepthStratified) {
            Map<Integer, List<MaxPDecision>> buckets = new TreeMap<>();
            for (MaxPDecision d : winners) buckets.computeIfAbsent(d.bestS.size(), k -> new ArrayList<>()).add(d);

            for (Map.Entry<Integer, List<MaxPDecision>> e : buckets.entrySet()) {
                List<MaxPDecision> level = e.getValue();
                level.sort(Comparator.comparingDouble((MaxPDecision m) -> m.bestP).reversed()
                        .thenComparing(m -> m.t.x.getName())
                        .thenComparing(m -> m.t.z.getName())
                        .thenComparing(m -> m.t.y.getName())
                        .thenComparing(m -> stringifySet(m.bestS)));

                for (MaxPDecision d : level) {
                    if (d.outcome != ColliderOutcome.INDEPENDENT) continue;
                    if (canOrientCollider(g, d.t.x, d.t.z, d.t.y)) {
                        GraphUtils.orientCollider(g, d.t.x, d.t.z, d.t.y);
                        if (verbose) {
                            TetradLogger.getInstance().log(
                                    "[MAX-P global(d=" + d.bestS.size() + ")] " +
                                            d.t.x.getName() + " -> " + d.t.z.getName() + " <- " + d.t.y.getName() +
                                            " (p=" + d.bestP + ", S=" + stringifySet(d.bestS) + ")");
                        }
                    }
                }
            }
        } else {
            winners.sort(Comparator.comparingDouble((MaxPDecision d) -> d.bestP).reversed()
                    .thenComparing(d -> d.t.x.getName())
                    .thenComparing(d -> d.t.z.getName())
                    .thenComparing(d -> d.t.y.getName())
                    .thenComparing(d -> stringifySet(d.bestS)));

            for (MaxPDecision d : winners) {
                if (d.outcome != ColliderOutcome.INDEPENDENT) continue;
                if (canOrientCollider(g, d.t.x, d.t.z, d.t.y)) {
                    GraphUtils.orientCollider(g, d.t.x, d.t.z, d.t.y);
                    if (verbose) {
                        TetradLogger.getInstance().log(
                                "[MAX-P global] " + d.t.x.getName() + " -> " + d.t.z.getName() + " <- " + d.t.y.getName() +
                                        " (p=" + d.bestP + ", S=" + stringifySet(d.bestS) + ")");
                    }
                }
            }
        }
    }

    private ColliderOutcome judgeConservative(Triple t, Graph g) throws InterruptedException {
        Node x = t.x, y = t.y;
        if (x.getName().compareTo(y.getName()) > 0) { Node tmp = x; x = y; y = tmp; }

        boolean sawIncludesZ = false, sawExcludesZ = false, sawAny = false;

        for (SepCandidate cand : enumerateSepsetsWithPvals(x, y, g)) {
            if (!cand.independent) continue;
            sawAny = true;
            if (cand.S.contains(t.z)) sawIncludesZ = true;
            else sawExcludesZ = true;
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
        Node x = t.x, y = t.y;
        if (x.getName().compareTo(y.getName()) > 0) { Node tmp = x; x = y; y = tmp; }

        List<SepCandidate> indep = new ArrayList<>();
        for (SepCandidate cand : enumerateSepsetsWithPvals(x, y, g)) {
            if (cand.independent) indep.add(cand);
        }
        if (indep.isEmpty()) return new MaxPDecision(t, ColliderOutcome.NO_SEPSET, Double.NaN, Collections.emptySet());

        double bestP = indep.stream().mapToDouble(c -> c.p).max().orElse(Double.NEGATIVE_INFINITY);

        double bestExcl = Double.NEGATIVE_INFINITY, bestIncl = Double.NEGATIVE_INFINITY;
        for (SepCandidate c : indep) {
            if (c.S.contains(t.z)) bestIncl = Math.max(bestIncl, c.p);
            else bestExcl = Math.max(bestExcl, c.p);
        }
        boolean hasExcl = bestExcl > Double.NEGATIVE_INFINITY;
        boolean hasIncl = bestIncl > Double.NEGATIVE_INFINITY;

        if (hasExcl && hasIncl) {
            if (bestExcl >= bestIncl + maxPMargin) {
                Set<Node> bestS = firstTieMatchingContainsZ(indep, t.z, false, bestExcl);
                return new MaxPDecision(t, ColliderOutcome.INDEPENDENT, bestExcl, bestS);
            }
            if (bestIncl >= bestExcl + maxPMargin) {
                Set<Node> bestS = firstTieMatchingContainsZ(indep, t.z, true, bestIncl);
                return new MaxPDecision(t, ColliderOutcome.DEPENDENT, bestIncl, bestS);
            }
            if (logMaxPTies && logStream != null) {
                logStream.println("[MAX-P ambiguous] pair=(" + x.getName() + "," + y.getName() + "), z=" + t.z.getName()
                        + " bestExcl=" + bestExcl + " bestIncl=" + bestIncl + " margin=" + maxPMargin);
            }
            return new MaxPDecision(t, ColliderOutcome.AMBIGUOUS, Math.max(bestExcl, bestIncl), Collections.emptySet());
        } else if (hasExcl) {
            Set<Node> bestS = firstTieMatchingContainsZ(indep, t.z, false, bestExcl);
            return new MaxPDecision(t, ColliderOutcome.INDEPENDENT, bestExcl, bestS);
        } else if (hasIncl) {
            Set<Node> bestS = firstTieMatchingContainsZ(indep, t.z, true, bestIncl);
            return new MaxPDecision(t, ColliderOutcome.DEPENDENT, bestIncl, bestS);
        } else {
            return new MaxPDecision(t, ColliderOutcome.NO_SEPSET, Double.NaN, Collections.emptySet());
        }
    }

    private Set<Node> firstTieMatchingContainsZ(List<SepCandidate> indep, Node z, boolean containsZ, double targetP) {
        List<SepCandidate> ties = new ArrayList<>();
        for (SepCandidate c : indep) if (c.p == targetP && c.S.contains(z) == containsZ) ties.add(c);
        ties.sort(Comparator.comparing(c -> stringifySet(c.S)));
        return ties.isEmpty() ? Collections.emptySet() : ties.get(0).S;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
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
                    String sKey = setKey(S);
                    if (uniq.containsKey(sKey)) continue;

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

    // ------------------------------------------------------------------------------------
    // Meek closure (cycle-safe)
    // ------------------------------------------------------------------------------------

    private void applyMeekRules(Graph g) {
        if (!meekCycleSafe || !forbidDirectedCycles) {
            MeekRules meekRules = new MeekRules();
            meekRules.setKnowledge(knowledge);
            meekRules.orientImplied(g);
            return;
        }

        // Cycle-safe Meek: repeatedly apply Meek, but only keep implied orientations that do not introduce cycles.
        boolean changed;
        int guard = 0;

        do {
            changed = false;
            guard++;
            if (guard > 10_000) break; // safety

            // snapshot edges before
            List<Edge> before = new ArrayList<>(g.getEdges());

            MeekRules meekRules = new MeekRules();
            meekRules.setKnowledge(knowledge);
            meekRules.orientImplied(g);

            // detect new directed edges introduced (or newly oriented)
            List<Edge> after = new ArrayList<>(g.getEdges());

            // Build a map key -> edge for comparisons
            Map<String, Edge> beforeMap = new HashMap<>();
            for (Edge e : before) beforeMap.put(edgeKey(e), e);

            for (Edge eAfter : after) {
                Edge eBefore = beforeMap.get(edgeKey(eAfter));
                if (eBefore == null) continue;

                // If orientation changed to introduce arrowhead(s), validate it
                if (!sameOrientation(eBefore, eAfter)) {
                    // If this particular change creates a cycle, rollback it.
                    // Rollback by restoring the old edge.
                    if (wouldCreateCycleIfSetEdge(g, eAfter)) {
                        // revert
                        g.removeEdge(eAfter);
                        g.addEdge(eBefore);
                        changed = true;
                    }
                }
            }

            // If we reverted anything, we should rerun meek to closure again, hence loop.
        } while (changed);
    }

    /** Return true if the current graph contains any directed cycle. */
    private static boolean hasDirectedCycle(Graph g) {
        // Cheap test: for each node, check if there is a directed path from it to itself.
        // If your GraphPaths has a cycle check, use that instead.
        for (Node v : g.getNodes()) {
            if (g.paths().existsDirectedPath(v, v)) return true;
        }
        return false;
    }

    /**
     * Conservative check: temporarily set the edge orientation (already in graph),
     * then check for directed cycles; revert handled by caller.
     */
    private boolean wouldCreateCycleIfSetEdge(Graph g, Edge orientedEdgeNowInGraph) {
        // Quick local sufficient check is hard for arbitrary Meek changes; just test cycle presence.
        // (Graph sizes here are usually small/moderate in PC.)
        return hasDirectedCycle(g);
    }

    private static boolean sameOrientation(Edge a, Edge b) {
        return a.getEndpoint1() == b.getEndpoint1() && a.getEndpoint2() == b.getEndpoint2();
    }

    private static String edgeKey(Edge e) {
        Node n1 = e.getNode1();
        Node n2 = e.getNode2();
        String a = n1.getName();
        String b = n2.getName();
        if (a.compareTo(b) <= 0) return a + "||" + b;
        return b + "||" + a;
    }

    // ------------------------------------------------------------------------------------
    // Checks / timeouts
    // ------------------------------------------------------------------------------------

    private void checkVars(List<Node> nodes) {
        if (!new HashSet<>(test.getVariables()).containsAll(nodes)) {
            throw new IllegalArgumentException("All nodes must be contained in the test's variables.");
        }
    }

    private void checkTimeout() throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Interrupted");
        if (timeoutMs >= 0) {
            long now = System.currentTimeMillis();
            if (now - startTimeMs > timeoutMs)
                throw new InterruptedException("Timed out after " + (now - startTimeMs) + " ms");
        }
    }

    // ------------------------------------------------------------
    // Enums & small records
    // ------------------------------------------------------------

    public enum ColliderOrientationStyle { SEPSETS, CONSERVATIVE, MAX_P }

    public enum AllowBidirected { ALLOW, DISALLOW }

    private enum ColliderOutcome { INDEPENDENT, DEPENDENT, AMBIGUOUS, NO_SEPSET }

    public static final class Triple {
        public final Node x;
        public final Node z;
        public final Node y;
        public Triple(Node x, Node z, Node y) { this.x = x; this.z = z; this.y = y; }
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