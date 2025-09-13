package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.util.TetradLogger;

import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

/**
 * CliqueCompletion
 * <p>
 * Post-search utility to (1) complete near-triangles inside dense pockets using small-order conditional tests with a
 * more liberal α (BH-corrected within each pocket), and (2) optionally re-test candidate missing adjacencies restricted
 * to a k-core.
 * <p>
 * It is designed to reduce false negatives that fragment cliques without globally tanking precision.
 * <p>
 * Adds only UNDIRECTED edges. Orientation can be handled by your usual pipeline.
 */
public final class CliqueCompletion {

    // ==== Config ====
    private final EdgeTester tester;
    private final int maxCompletionOrder;    // 0..2 (0: S = ∅ only; 1: S in {∅, {pivot}}; 2: try up to size-2)
    private final double intraAlpha;         // liberal intra-pocket alpha (will be BH-adjusted within pocket)
    private final int kCore;                 // dense region threshold (3 is a good default)
    private final boolean triangleCompletion;
    private final boolean denseCoreRetest;
    private final boolean log;
    private final int minCommonNeighbors;    // require ≥ this many common neighbors before considering a completion

    private CliqueCompletion(Builder b) {
        this.tester = b.tester;
        this.maxCompletionOrder = b.maxCompletionOrder;
        this.intraAlpha = b.intraAlpha;
        this.kCore = b.kCore;
        this.triangleCompletion = b.triangleCompletion;
        this.denseCoreRetest = b.denseCoreRetest;
        this.log = b.log;
        this.minCommonNeighbors = b.minCommonNeighbors;
    }

    // ==== Public API ====

    public static Builder newBuilder(IndependenceTest test) {
        return new Builder(defaultAdapter(test));
    }

    public static Builder newBuilder(EdgeTester tester) {
        return new Builder(tester);
    }

    private static Map<Node, Set<Node>> adjacency(Graph g) {
        Map<Node, Set<Node>> adj = new HashMap<>();
        for (Node v : g.getNodes()) adj.put(v, new HashSet<>());
        for (Node a : g.getNodes()) {
            for (Node b : g.getAdjacentNodes(a)) {
                adj.get(a).add(b);
            }
        }
        return adj;
    }

    // ==== Pass 1: Triangle completion (localized, BH within each pocket) ====

    private static Set<Node> neighbors(Graph g, Node x) {
        return new HashSet<>(g.getAdjacentNodes(x));
    }

    /**
     * Simple k-core using degrees within the induced subgraph and iterative peeling.
     */
    private static Set<Node> kCoreNodes(Map<Node, Set<Node>> adj, int k) {
        if (k <= 0) return adj.keySet();
        Map<Node, Integer> deg = new HashMap<>();
        for (var e : adj.entrySet()) {
            deg.put(e.getKey(), (int) e.getValue().stream().filter(adj::containsKey).count());
        }
        Deque<Node> q = new ArrayDeque<>();
        Set<Node> keep = new HashSet<>(adj.keySet());
        for (var v : keep) {
            if (deg.getOrDefault(v, 0) < k) q.add(v);
        }
        while (!q.isEmpty()) {
            Node v = q.removeFirst();
            if (!keep.remove(v)) continue;
            for (Node u : adj.getOrDefault(v, Collections.emptySet())) {
                if (!keep.contains(u)) continue;
                deg.put(u, deg.get(u) - 1);
                if (deg.get(u) == k - 1) q.add(u);
            }
        }
        return keep;
    }

    /**
     * Connected components induced by a given node subset.
     */
    private static List<Set<Node>> connectedComponents(Graph g, Set<Node> subset) {
        Set<Node> unvisited = new HashSet<>(subset);
        List<Set<Node>> comps = new ArrayList<>();
        while (!unvisited.isEmpty()) {
            Node start = unvisited.iterator().next();
            Set<Node> comp = new HashSet<>();
            Deque<Node> dq = new ArrayDeque<>();
            dq.add(start);
            unvisited.remove(start);
            while (!dq.isEmpty()) {
                Node v = dq.removeFirst();
                comp.add(v);
                for (Node w : g.getAdjacentNodes(v)) {
                    if (subset.contains(w) && unvisited.remove(w)) dq.add(w);
                }
            }
            comps.add(comp);
        }
        return comps;
    }

    // ==== Pass 2: Dense-core retest (liberal α, BH within each core) ====

    /**
     * Default adapter: tries test.getPValue(a,b,S) via reflection; else falls back to boolean.
     */
    private static EdgeTester defaultAdapter(IndependenceTest test) {
        // Try to find a "getPValue(Node,Node,List<Node>)" or "(Set<Node>)" by reflection
        Method pList = null, pSet = null;
        try {
            pList = test.getClass().getMethod("getPValue", Node.class, Node.class, List.class);
        } catch (Throwable ignore) {
        }
        try {
            pSet = test.getClass().getMethod("getPValue", Node.class, Node.class, Set.class);
        } catch (Throwable ignore) {
        }

        Method finalPList = pList;
        Method finalPSet = pSet;

        return (a, b, S) -> {
            try {
                if (finalPSet != null) {
                    Object out = finalPSet.invoke(test, a, b, S);
                    if (out instanceof Number) return ((Number) out).doubleValue();
                }
                if (finalPList != null) {
                    Object out = finalPList.invoke(test, a, b, new ArrayList<>(S));
                    if (out instanceof Number) return ((Number) out).doubleValue();
                }
            } catch (Throwable ignore) {
            }
            // Fallback: use isIndependent; map to {1.0, 0.0} as a crude signal
            try {
                boolean indep = test.checkIndependence(a, b, new HashSet<>(S)).isIndependent();
                return indep ? 1.0 : 0.0;
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        };
    }

    // ==== Utilities ====

    /**
     * Apply completion in-place to the given graph.
     */
    public void apply(Graph g) {
        if (triangleCompletion) {
            triangleCompletionPass(g);
        }
        if (denseCoreRetest) {
            denseCoreRetestPass(g);
        }
    }

    private void triangleCompletionPass(Graph g) {
        // Build adjacency map once
        Map<Node, Set<Node>> adj = adjacency(g);

        // Optional: restrict to dense region nodes (k-core) to be extra cautious
        Set<Node> denseNodes = kCore > 0 ? kCoreNodes(adj, kCore) : adj.keySet();

        // Group by local pockets: use connected components induced by denseNodes
        List<Set<Node>> pockets = connectedComponents(g, denseNodes);

        int edgesAdded = 0;

        for (Set<Node> pocket : pockets) {
            // Gather candidate missing edges that close triangles inside this pocket
            List<CandidateEdge> candidates = new ArrayList<>();

            for (Node x : pocket) {
                // neighbors of x inside pocket
                List<Node> neigh = adj.getOrDefault(x, Collections.emptySet()).stream()
                        .filter(pocket::contains).toList();

                final int m = neigh.size();
                for (int i = 0; i < m; i++) {
                    Node y = neigh.get(i);
                    for (int j = i + 1; j < m; j++) {
                        Node z = neigh.get(j);
                        if (y == z) continue;
                        if (g.isAdjacentTo(y, z)) continue; // already present

                        // Require common-neighbor support to avoid clutter
                        Set<Node> common = new HashSet<>(neighbors(g, y));
                        common.retainAll(neighbors(g, z));
                        if (common.size() < minCommonNeighbors) continue;

                        // Candidate missing edge (y,z) completing triangle y-x-z
                        // Build a small family of conditioning sets
                        List<Set<Node>> condSets = smallConditioningSets(g, y, z, x, common);
                        // Evaluate with p-values (take min p across the family)
                        double pMin = pMinOverFamily(y, z, condSets);

                        candidates.add(new CandidateEdge(y, z, pMin));
                    }
                }
            }

            // Deduplicate candidates (multiple pivots can suggest the same pair)
            Map<Pair, CandidateEdge> bestByPair = new HashMap<>();
            for (CandidateEdge ce : candidates) {
                Pair key = new Pair(ce.a, ce.b);
                CandidateEdge prev = bestByPair.get(key);
                if (prev == null || ce.p < prev.p) bestByPair.put(key, ce);
            }

            // BH within the pocket
            List<CandidateEdge> uniq = new ArrayList<>(bestByPair.values());
            uniq.sort(Comparator.comparingDouble(c -> c.p));

            int mTests = uniq.size();
            int k = 0;
            for (int i = 0; i < mTests; i++) {
                double p = uniq.get(i).p;
                // BH threshold: p <= (i+1)/m * alpha
                double thresh = ((i + 1) / (double) mTests) * intraAlpha;
                if (p <= thresh) k = i + 1;
            }

            for (int i = 0; i < k; i++) {
                CandidateEdge ce = uniq.get(i);
                if (!g.isAdjacentTo(ce.a, ce.b)) {
                    g.addUndirectedEdge(ce.a, ce.b);
                    edgesAdded++;
                    if (log) TetradLogger.getInstance().log(
                            String.format("CliqueCompletion: triangle-complete %s--%s (p=%.3g)", ce.a, ce.b, ce.p));
                }
            }
        }

        if (log)
            TetradLogger.getInstance().log("CliqueCompletion: triangle completion added " + edgesAdded + " edges.");
    }

    // Build small conditioning families: S ∈ {∅, {pivot}, some size-1 from mutual neighbors} up to maxCompletionOrder
    private List<Set<Node>> smallConditioningSets(Graph g, Node y, Node z, Node pivot, Set<Node> common) {
        List<Set<Node>> sets = new ArrayList<>();
        sets.add(Collections.emptySet());
        if (maxCompletionOrder >= 1) {
            sets.add(Set.of(pivot));
            // Also consider another size-1: a shared neighbor of y and z (within reason)
            for (Node c : common) {
                if (!c.equals(pivot)) sets.add(Set.of(c));
            }
        }
        if (maxCompletionOrder >= 2) {
            // Try a couple of size-2 variants using pivot + a common neighbor (limit to a few)
            int count = 0;
            for (Node c : common) {
                if (c.equals(pivot)) continue;
                sets.add(new HashSet<>(Arrays.asList(pivot, c)));
                if (++count >= 3) break; // cap for efficiency
            }
        }
        // Deduplicate
        List<Set<Node>> uniq = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Set<Node> S : sets) {
            String key = S.stream().map(Node::getName).sorted().collect(Collectors.joining(","));
            if (seen.add(key)) uniq.add(S);
        }
        return uniq;
    }

    // Minimum p over a small family of conditioning sets
    private double pMinOverFamily(Node a, Node b, List<Set<Node>> condSets) {
        double pmin = 1.0;
        for (Set<Node> S : condSets) {
            double p = tester.pValue(a, b, S);
            if (p < pmin) pmin = p;
        }
        return pmin;
    }

    // ==== Adapters & helpers ====

    private void denseCoreRetestPass(Graph g) {
        Map<Node, Set<Node>> adj = adjacency(g);
        Set<Node> core = kCoreNodes(adj, kCore);
        if (core.isEmpty()) return;

        // Work per connected component inside the core
        List<Set<Node>> pockets = connectedComponents(g, core);

        int edgesAdded = 0;

        for (Set<Node> pocket : pockets) {
            // Consider ALL missing pairs inside pocket; but score only *nearby* ones (share enough neighbors)
            List<CandidateEdge> cands = new ArrayList<>();

            List<Node> nodes = new ArrayList<>(pocket);
            int n = nodes.size();
            for (int i = 0; i < n; i++) {
                Node a = nodes.get(i);
                for (int j = i + 1; j < n; j++) {
                    Node b = nodes.get(j);
                    if (g.isAdjacentTo(a, b)) continue;

                    Set<Node> common = new HashSet<>(neighbors(g, a));
                    common.retainAll(neighbors(g, b));
                    if (common.size() < minCommonNeighbors) continue; // support gate

                    // Build small family: ∅, any single common neighbor, and up to two size-2 sets
                    List<Set<Node>> fam = new ArrayList<>();
                    fam.add(Collections.emptySet());
                    int limit1 = 0;
                    for (Node c : common) {
                        fam.add(Set.of(c));
                        if (++limit1 >= 3) break; // cap
                    }
                    int limit2 = 0;
                    List<Node> commonList = new ArrayList<>(common);
                    for (int u = 0; u < commonList.size(); u++) {
                        for (int v = u + 1; v < commonList.size(); v++) {
                            fam.add(Set.of(commonList.get(u), commonList.get(v)));
                            if (++limit2 >= 3) break;
                        }
                        if (limit2 >= 3) break;
                    }

                    double p = pMinOverFamily(a, b, fam);
                    cands.add(new CandidateEdge(a, b, p));
                }
            }

            // BH within pocket
            cands.sort(Comparator.comparingDouble(c -> c.p));
            int mTests = cands.size();
            int k = 0;
            for (int i = 0; i < mTests; i++) {
                double p = cands.get(i).p;
                double thresh = ((i + 1) / (double) mTests) * intraAlpha;
                if (p <= thresh) k = i + 1;
            }

            for (int i = 0; i < k; i++) {
                CandidateEdge ce = cands.get(i);
                if (!g.isAdjacentTo(ce.a, ce.b)) {
                    g.addUndirectedEdge(ce.a, ce.b);
                    edgesAdded++;
                    if (log) TetradLogger.getInstance().log(
                            String.format("CliqueCompletion: core-retest add %s--%s (p=%.3g)", ce.a, ce.b, ce.p));
                }
            }
        }

        if (log) TetradLogger.getInstance().log("CliqueCompletion: dense-core retest added " + edgesAdded + " edges.");
    }

    /**
     * Functional adapter for independence testing with p-values.
     */
    public interface EdgeTester {
        /**
         * Return p-value for testing a ⫫ b | S (lower = stronger evidence of dependence).
         */
        double pValue(Node a, Node b, Set<Node> S);
    }

    private record CandidateEdge(Node a, Node b, double p) {
    }

    private record Pair(String a, String b) {
        Pair(Node x, Node y) {
            this(x.getName().compareTo(y.getName()) <= 0 ? x.getName() : y.getName(),
                    x.getName().compareTo(y.getName()) <= 0 ? y.getName() : x.getName());
        }
    }

    // ==== Builder ====

    public static final class Builder {
        private final EdgeTester tester;
        private int maxCompletionOrder = 1;
        private double intraAlpha = 0.02; // if global α=0.01, 2× is a good start
        private int kCore = 3;
        private boolean triangleCompletion = true;
        private boolean denseCoreRetest = true;
        private boolean log = true;
        private int minCommonNeighbors = 2; // default: need ≥2 shared neighbors to consider completing

        private Builder(EdgeTester tester) {
            this.tester = Objects.requireNonNull(tester);
        }

        public Builder maxCompletionOrder(int ord) {
            this.maxCompletionOrder = Math.max(0, Math.min(2, ord));
            return this;
        }

        public Builder intraAlpha(double a) {
            this.intraAlpha = Math.max(1e-12, Math.min(0.5, a));
            return this;
        }

        public Builder kCore(int k) {
            this.kCore = Math.max(0, k);
            return this;
        }

        public Builder enableTriangleCompletion(boolean v) {
            this.triangleCompletion = v;
            return this;
        }

        public Builder enableDenseCoreRetest(boolean v) {
            this.denseCoreRetest = v;
            return this;
        }

        public Builder log(boolean v) {
            this.log = v;
            return this;
        }

        /**
         * Require at least this many common neighbors before considering completion.
         */
        public Builder minCommonNeighbors(int k) {
            this.minCommonNeighbors = Math.max(0, k);
            return this;
        }

        public CliqueCompletion build() {
            return new CliqueCompletion(this);
        }
    }
}