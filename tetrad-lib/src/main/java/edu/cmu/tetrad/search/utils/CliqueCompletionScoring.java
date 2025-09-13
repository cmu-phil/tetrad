package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.data.CorrelationMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.*;
import java.util.stream.Collectors;

/**
 * CliqueCompletionScoring
 * <p>
 * Post-search utility that proposes UNDIRECTED edges inside dense pockets when a local edge score shows positive gain
 * (e.g., ΔBIC > 0 improvement).
 * <p>
 * It mirrors CliqueCompletion but replaces testing/BH with score gains. Orientation is left to downstream procedures.
 */
public final class CliqueCompletionScoring {

    // ==== Config ====
    private final EdgeScorer scorer;          // returns "gain" for adding a--b | S (positive = better)
    private final double gainThreshold;       // minimum gain required to accept an edge
    private final int maxCompletionOrder;     // 0..2 (family of small S)
    private final int kCore;                  // restrict to dense pockets
    private final int minCommonNeighbors;     // support gate
    private final boolean triangleCompletion; // pass 1
    private final boolean denseCoreRetest;    // pass 2
    private final boolean log;

    private CliqueCompletionScoring(Builder b) {
        this.scorer = b.scorer;
        this.gainThreshold = b.gainThreshold;
        this.maxCompletionOrder = b.maxCompletionOrder;
        this.kCore = b.kCore;
        this.minCommonNeighbors = b.minCommonNeighbors;
        this.triangleCompletion = b.triangleCompletion;
        this.denseCoreRetest = b.denseCoreRetest;
        this.log = b.log;
    }

    // ==== Public API ====

    public static Builder newBuilder(EdgeScorer scorer) {
        return new Builder(Objects.requireNonNull(scorer));
    }

    private static Map<Node, Set<Node>> adjacency(Graph g) {
        Map<Node, Set<Node>> adj = new HashMap<>();
        for (Node v : g.getNodes()) adj.put(v, new HashSet<>());
        for (Node a : g.getNodes()) {
            for (Node b : g.getAdjacentNodes(a)) adj.get(a).add(b);
        }
        return adj;
    }

    // ==== Pass 1: triangle completion with scoring ====

    private static Set<Node> neighbors(Graph g, Node x) {
        return new HashSet<>(g.getAdjacentNodes(x));
    }

    // ==== Pass 2: dense-core retest with scoring ====

    /**
     * Simple k-core on undirected view.
     */
    private static Set<Node> kCoreNodes(Map<Node, Set<Node>> adj, int k) {
        if (k <= 0) return adj.keySet();
        Map<Node, Integer> deg = new HashMap<>();
        for (var e : adj.entrySet()) {
            deg.put(e.getKey(), (int) e.getValue().stream().filter(adj::containsKey).count());
        }
        Deque<Node> q = new ArrayDeque<>();
        Set<Node> keep = new HashSet<>(adj.keySet());
        for (var v : keep) if (deg.getOrDefault(v, 0) < k) q.add(v);
        while (!q.isEmpty()) {
            Node v = q.removeFirst();
            if (!keep.remove(v)) continue;
            for (Node u : adj.getOrDefault(v, Set.of())) {
                if (!keep.contains(u)) continue;
                deg.put(u, deg.get(u) - 1);
                if (deg.get(u) == k - 1) q.add(u);
            }
        }
        return keep;
    }

    // ==== Families, pockets, adjacency ====

    private static List<Set<Node>> connectedComponents(Graph g, Set<Node> subset) {
        Set<Node> un = new HashSet<>(subset);
        List<Set<Node>> comps = new ArrayList<>();
        while (!un.isEmpty()) {
            Node s = un.iterator().next();
            Set<Node> comp = new HashSet<>();
            Deque<Node> dq = new ArrayDeque<>();
            dq.add(s);
            un.remove(s);
            while (!dq.isEmpty()) {
                Node v = dq.removeFirst();
                comp.add(v);
                for (Node w : g.getAdjacentNodes(v)) {
                    if (subset.contains(w) && un.remove(w)) dq.add(w);
                }
            }
            comps.add(comp);
        }
        return comps;
    }

    /**
     * Apply completion in-place to the given graph.
     */
    public void apply(Graph g) {
        if (triangleCompletion) triangleCompletionPass(g);
        if (denseCoreRetest) denseCoreRetestPass(g);
    }

    private void triangleCompletionPass(Graph g) {
        Map<Node, Set<Node>> adj = adjacency(g);
        Set<Node> dense = kCore > 0 ? kCoreNodes(adj, kCore) : adj.keySet();
        List<Set<Node>> pockets = connectedComponents(g, dense);

        int added = 0;
        for (Set<Node> pocket : pockets) {
            List<Candidate> cands = new ArrayList<>();
            for (Node x : pocket) {
                List<Node> neigh = adj.getOrDefault(x, Set.of()).stream()
                        .filter(pocket::contains).toList();
                for (int i = 0; i < neigh.size(); i++) {
                    Node y = neigh.get(i);
                    for (int j = i + 1; j < neigh.size(); j++) {
                        Node z = neigh.get(j);
                        if (y == z || g.isAdjacentTo(y, z)) continue;

                        Set<Node> common = new HashSet<>(neighbors(g, y));
                        common.retainAll(neighbors(g, z));
                        if (common.size() < minCommonNeighbors) continue;

                        // family of small S from pivot x and/or common neighbors
                        for (Set<Node> S : smallFamilies(y, z, x, common)) {
                            double gain = scorer.gain(y, z, S);
                            if (Double.isFinite(gain)) {
                                cands.add(new Candidate(y, z, gain));
                            }
                        }
                    }
                }
            }
            // keep best gain per pair
            Map<Pair, Candidate> best = new HashMap<>();
            for (Candidate c : cands) {
                Pair key = new Pair(c.a, c.b);
                Candidate prev = best.get(key);
                if (prev == null || c.gain > prev.gain) best.put(key, c);
            }
            // accept those above threshold, sorted by gain
            List<Candidate> acc = new ArrayList<>(best.values());
            acc.sort((u, v) -> Double.compare(v.gain, u.gain));
            for (Candidate c : acc) {
                if (c.gain <= gainThreshold) break;
                if (!g.isAdjacentTo(c.a, c.b)) {
                    g.addUndirectedEdge(c.a, c.b);
                    added++;
                    if (log) TetradLogger.getInstance().log(
                            String.format("CliqueCompletionScoring: triangle add %s--%s (gain=%.4f)",
                                    c.a.getName(), c.b.getName(), c.gain));
                }
            }
        }
        if (log) TetradLogger.getInstance().log(
                "CliqueCompletionScoring: triangle completion added " + added + " edges.");
    }

    private void denseCoreRetestPass(Graph g) {
        Map<Node, Set<Node>> adj = adjacency(g);
        Set<Node> core = kCoreNodes(adj, kCore);
        if (core.isEmpty()) return;

        List<Set<Node>> pockets = connectedComponents(g, core);
        int added = 0;

        for (Set<Node> pocket : pockets) {
            List<Node> L = new ArrayList<>(pocket);
            List<Candidate> cands = new ArrayList<>();

            for (int i = 0; i < L.size(); i++) {
                for (int j = i + 1; j < L.size(); j++) {
                    Node a = L.get(i), b = L.get(j);
                    if (g.isAdjacentTo(a, b)) continue;

                    Set<Node> common = new HashSet<>(neighbors(g, a));
                    common.retainAll(neighbors(g, b));
                    if (common.size() < minCommonNeighbors) continue;

                    // fam: ∅, some size-1 from common, and limited size-2
                    List<Set<Node>> fam = new ArrayList<>();
                    fam.add(Collections.emptySet());
                    int c1 = 0;
                    for (Node c : common) {
                        fam.add(Set.of(c));
                        if (++c1 >= 3) break;
                    }
                    int c2 = 0;
                    List<Node> CL = new ArrayList<>(common);
                    for (int u = 0; u < CL.size() && c2 < 3; u++) {
                        for (int v = u + 1; v < CL.size() && c2 < 3; v++, c2++) {
                            fam.add(Set.of(CL.get(u), CL.get(v)));
                        }
                    }

                    double bestGain = Double.NEGATIVE_INFINITY;
                    for (Set<Node> S : fam) {
                        double gain = scorer.gain(a, b, S);
                        if (gain > bestGain) bestGain = gain;
                    }
                    if (Double.isFinite(bestGain))
                        cands.add(new Candidate(a, b, bestGain));
                }
            }

            cands.sort((u, v) -> Double.compare(v.gain, u.gain));
            for (Candidate c : cands) {
                if (c.gain <= gainThreshold) break;
                if (!g.isAdjacentTo(c.a, c.b)) {
                    g.addUndirectedEdge(c.a, c.b);
                    added++;
                    if (log) TetradLogger.getInstance().log(
                            String.format("CliqueCompletionScoring: core add %s--%s (gain=%.4f)",
                                    c.a.getName(), c.b.getName(), c.gain));
                }
            }
        }
        if (log) TetradLogger.getInstance().log(
                "CliqueCompletionScoring: dense-core retest added " + added + " edges.");
    }

    private List<Set<Node>> smallFamilies(Node y, Node z, Node pivot, Set<Node> common) {
        List<Set<Node>> out = new ArrayList<>();
        out.add(Collections.emptySet());
        if (maxCompletionOrder >= 1) {
            out.add(Set.of(pivot));
            for (Node c : common) if (!c.equals(pivot)) out.add(Set.of(c));
        }
        if (maxCompletionOrder >= 2) {
            int k = 0;
            for (Node c : common) {
                if (c.equals(pivot)) continue;
                out.add(new HashSet<>(Arrays.asList(pivot, c)));
                if (++k >= 3) break;
            }
        }
        // dedup
        Set<String> seen = new HashSet<>();
        List<Set<Node>> uniq = new ArrayList<>();
        for (Set<Node> S : out) {
            String key = S.stream().map(Node::getName).sorted().collect(Collectors.joining(","));
            if (seen.add(key)) uniq.add(S);
        }
        return uniq;
    }

    // ==== Types ====

    /**
     * Returns a gain for adding edge a--b | S; positive = accept.
     */
    @FunctionalInterface
    public interface EdgeScorer {
        double gain(Node a, Node b, Set<Node> S);
    }

    private record Candidate(Node a, Node b, double gain) {
    }

    private record Pair(String a, String b) {
        Pair(Node x, Node y) {
            this(x.getName().compareTo(y.getName()) <= 0 ? x.getName() : y.getName(),
                    x.getName().compareTo(y.getName()) <= 0 ? y.getName() : x.getName());
        }
    }

    // ==== Builder ====

    public static final class Builder {
        private final EdgeScorer scorer;
        private double gainThreshold = 0.0; // require BIC improvement by default
        private int maxCompletionOrder = 1;
        private int kCore = 3;
        private int minCommonNeighbors = 2;
        private boolean triangleCompletion = true;
        private boolean denseCoreRetest = true;
        private boolean log = true;

        private Builder(EdgeScorer scorer) {
            this.scorer = scorer;
        }

        public Builder gainThreshold(double t) {
            this.gainThreshold = t;
            return this;
        }

        public Builder maxCompletionOrder(int ord) {
            this.maxCompletionOrder = Math.max(0, Math.min(2, ord));
            return this;
        }

        public Builder kCore(int k) {
            this.kCore = Math.max(0, k);
            return this;
        }

        public Builder minCommonNeighbors(int k) {
            this.minCommonNeighbors = Math.max(0, k);
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

        public CliqueCompletionScoring build() {
            return new CliqueCompletionScoring(this);
        }
    }

    // ==== Ready-made scorer: Gaussian partial-correlation BIC ====

    /**
     * Computes gain = -ΔBIC for allowing ρ_{ab|S} ≠ 0 versus ρ_{ab|S} = 0 in a Gaussian model. Positive gain means
     * adding edge a--b reduces BIC.
     */
    public static final class GaussianPartialCorrelationBic implements EdgeScorer {
        private final CorrelationMatrix cm;
        private final DataSet data;

        public GaussianPartialCorrelationBic(DataSet data) {
            this.data = data;
            this.cm = new CorrelationMatrix(data);
        }

        /**
         * Small symmetric matrix inversion (Gauss-Jordan) for p <= ~20.
         */
        private static double[][] invertSymmetric(double[][] A) {
            int n = A.length;
            double[][] M = new double[n][n];
            double[][] I = new double[n][n];
            for (int i = 0; i < n; i++) {
                System.arraycopy(A[i], 0, M[i], 0, n);
                I[i][i] = 1.0;
            }
            // Gauss-Jordan with partial pivoting
            for (int k = 0; k < n; k++) {
                int piv = k;
                double best = Math.abs(M[k][k]);
                for (int i = k + 1; i < n; i++) {
                    double v = Math.abs(M[i][k]);
                    if (v > best) {
                        best = v;
                        piv = i;
                    }
                }
                if (best < 1e-12) throw new RuntimeException("Matrix nearly singular in partial-corr inversion.");
                if (piv != k) {
                    double[] tmp = M[k];
                    M[k] = M[piv];
                    M[piv] = tmp;
                    double[] tmp2 = I[k];
                    I[k] = I[piv];
                    I[piv] = tmp2;
                }
                double diag = M[k][k];
                for (int j = 0; j < n; j++) {
                    M[k][j] /= diag;
                    I[k][j] /= diag;
                }
                for (int i = 0; i < n; i++)
                    if (i != k) {
                        double f = M[i][k];
                        if (f == 0) continue;
                        for (int j = 0; j < n; j++) {
                            M[i][j] -= f * M[k][j];
                            I[i][j] -= f * I[k][j];
                        }
                    }
            }
            return I;
        }

        @Override
        public double gain(Node a, Node b, Set<Node> S) {
            double r = Math.max(-0.999999, Math.min(0.999999, partialCorrelation(a, b, S)));
            int n = data.getNumRows();
            int neff = Math.max(1, n - S.size() - 3); // effective n like Fisher-Z
            // -2ΔlogL ≈ neff * ln(1/(1 - r^2))
            double minus2DeltaLL = neff * Math.log(1.0 / (1.0 - r * r));
            double deltaBic = -minus2DeltaLL + Math.log(n); // add 1 parameter
            return -deltaBic; // positive = improvement
        }

        private double partialCorrelation(Node a, Node b, Set<Node> S) {
            // Build correlation submatrix over [a,b,S...]
            List<Node> vars = new ArrayList<>(2 + S.size());
            vars.add(a);
            vars.add(b);
            vars.addAll(S);
            int p = vars.size();
            double[][] R = new double[p][p];
            for (int i = 0; i < p; i++) {
                for (int j = 0; j < p; j++) {
                    int ii = data.getColumn(data.getVariable(vars.get(i).getName()));
                    int jj = data.getColumn(data.getVariable(vars.get(j).getName()));
                    R[i][j] = cm.getValue(ii, jj);
                }
            }
            double[][] Omega = invertSymmetric(R);
            // ρ_ab|S = -Ω_ab / sqrt(Ω_aa Ω_bb)
            double num = -Omega[0][1];
            double den = Math.sqrt(Math.max(1e-12, Omega[0][0] * Omega[1][1]));
            return num / den;
        }
    }
}