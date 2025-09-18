package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.utils.TsUtils;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.*;

/**
 * PCMCI (Runge et al.) – minimal, time-series. Lagged edges only (τ>=1).
 * Pipeline:
 *   1) Build lagged variables up to maxLag.
 *   2) Parent preselection per node V: PC1-like elimination using only strict past of V.
 *   3) MCI: keep X_{t-τ} -> Y_t iff X ⟂̸ Y | Pa(Y)\{X} ∪ Pa(X).
 *   4) Add directed edges X_{t-τ} -> Y_t; optionally collapse to base-time nodes.
 *
 * Notes:
 *  - IndependenceTest is whatever you pass (FisherZ, etc.).
 *  - Knowledge is enforced for allowed/forbidden arcs (including tiers).
 *  - This version orients only τ>0 (no instantaneous τ=0; add later for PCMCI+).
 */
public final class Pcmci implements IGraphSearch {

    // ----------------- configuration -----------------
    public static final class Builder {
        private final DataSet data;
        private final IndependenceTest test;
        private int maxLag = 3;
        private double alpha = 0.05;           // informational unless the test uses it internally
        private int maxCondSize = 3;           // cap |S| in both phases
        private boolean verbose = false;
        private boolean collapseToLag0 = true; // collapse lagged graph back to base nodes

        public Builder(DataSet data, IndependenceTest test) {
            this.data = Objects.requireNonNull(data);
            this.test = Objects.requireNonNull(test);
        }
        public Builder maxLag(int L){ this.maxLag = Math.max(1, L); return this; }
        public Builder alpha(double a){ this.alpha = a; return this; }
        public Builder maxCondSize(int k){ this.maxCondSize = k; return this; }
        public Builder verbose(boolean v){ this.verbose = v; return this; }
        public Builder collapseToLag0(boolean c){ this.collapseToLag0 = c; return this; }

        public Pcmci build(){ return new Pcmci(this); }
    }

    private final DataSet raw;
    private final IndependenceTest test;
    private final int maxLag;
    @SuppressWarnings("unused")
    private final double alpha; // the test’s isIndependent() should embody alpha if needed
    private final int maxCondSize;
    private Knowledge knowledge;
    private final boolean verbose;
    private final boolean collapseToLag0;

    private Pcmci(Builder b) {
        this.raw = b.data;
        this.test = b.test;
        this.maxLag = b.maxLag;
        this.alpha = b.alpha;
        this.maxCondSize = b.maxCondSize;
        this.verbose = b.verbose;
        this.collapseToLag0 = b.collapseToLag0;
    }

    // --------------- IGraphSearch --------------------
    @Override
    public Graph search() throws InterruptedException {
        // 1) Create lagged data up to maxLag. Naming: e.g., "X:t-2" (TsUtils default).
        DataSet lagged = TsUtils.createLagData(raw, maxLag);
        knowledge = lagged.getKnowledge();

        // Tie test to lagged variables:
        if (!test.getVariables().equals(lagged.getVariables())) {
            throw new IllegalStateException("IndependenceTest variables must match the lagged dataset.");
        }

        // Build lag index helper
        TimeLagIndex TL = TimeLagIndex.from(lagged, maxLag);

        // 2) Parent preselection for ALL nodes using their strict past (so Pa(X_{t-τ}) is available)
        Map<Node, LinkedHashSet<Node>> preParents = new LinkedHashMap<>();
        for (Node v : lagged.getVariables()) {
            LinkedHashSet<Node> cand = new LinkedHashSet<>(TL.strictPastOf(v));
            shrinkByPC1(v, cand);
            preParents.put(v, cand);
        }

        // 3) MCI confirmation only for lag-0 targets Y_t
        Map<Node, LinkedHashSet<Node>> parents = new LinkedHashMap<>();
        for (Node y_t : TL.lag0Nodes()) {
            LinkedHashSet<Node> kept = new LinkedHashSet<>();
            for (Node x_tau : preParents.get(y_t)) {
                // Build Sy = Pa(Y)\{X}, Sx = Pa(X)
                Set<Node> Sy = new LinkedHashSet<>(preParents.get(y_t));
                Sy.remove(x_tau);
                Set<Node> Sx = new LinkedHashSet<>(preParents.getOrDefault(x_tau, new LinkedHashSet<>()));
                Set<Node> S = new LinkedHashSet<>(Sy);
                S.addAll(Sx);

                Set<Node> Suse = limitSize(S, maxCondSize);

                IndependenceResult r = test.checkIndependence(x_tau, y_t, Suse);
                boolean dep = !r.isIndependent();
                if (dep) kept.add(x_tau);

                if (verbose) {
                    TetradLogger.getInstance().log(String.format(
                            "[MCI] %s -> %s |S|=%d p=%.3g keep=%s",
                            x_tau.getName(), y_t.getName(), Suse.size(), r.getPValue(), dep));
                }
            }
            parents.put(y_t, kept);
        }

        // 4) Build a directed graph over lagged nodes, arrows from past→present
        Graph gLag = new EdgeListGraph(lagged.getVariables());
        for (Node y_t : TL.lag0Nodes()) {
            for (Node x_tau : parents.get(y_t)) {
                if (TL.lagOf(x_tau) <= 0) continue; // τ>0 only
                if (allowedArrow(x_tau, y_t)) gLag.addDirectedEdge(x_tau, y_t);
            }
        }

        if (!collapseToLag0) return gLag;

        // 5) Collapse to base-time nodes (aggregate any incoming lags X_{t-τ} → Y_t into X → Y)
        Graph collapsed = new EdgeListGraph(TL.baseNodes());
        for (Edge e : gLag.getEdges()) {
            Node src = e.getNode1(), dst = e.getNode2();
            Node X = TL.baseOf(src), Y = TL.baseOf(dst);
            if (X == Y) continue;
            if (allowedArrow(X, Y) && !collapsed.isAdjacentTo(X, Y)) {
                collapsed.addDirectedEdge(X, Y);
            }
        }
        return collapsed;
    }

    @Override
    public IndependenceTest getTest() { return test; }

    @Override
    public void setTest(IndependenceTest newTest) {
        if (!test.getVariables().equals(newTest.getVariables())) {
            throw new IllegalArgumentException("New test’s variables must match the current variables.");
        }
        throw new IllegalStateException("Switching tests after build isn’t supported in this minimal PCMCI.");
    }

    // ----------------- core helpers ------------------

    /** PC1-like shrinking of parent candidates for target v using rising conditioning size up to maxCondSize. */
    private void shrinkByPC1(Node v, LinkedHashSet<Node> cand) throws InterruptedException {
        boolean removed;
        for (int k = 0; k <= maxCondSize; k++) {
            do {
                removed = false;
                List<Node> order = new ArrayList<>(cand);
                for (Node x : order) {
                    List<Node> others = new ArrayList<>(cand);
                    others.remove(x);
                    if (tryDsep(x, v, others, k)) {
                        cand.remove(x);
                        removed = true;
                        if (verbose) {
                            TetradLogger.getInstance().log(String.format("[PC1] remove %s -> %s at |S|=%d",
                                    x.getName(), v.getName(), k));
                        }
                    }
                }
            } while (removed);
        }
    }

    /** Returns true if ∃ subset S ⊆ pool, |S|=k, s.t. x ⟂ y | S. */
    private boolean tryDsep(Node x, Node y, List<Node> pool, int k) throws InterruptedException {
        if (k == 0) {
            IndependenceResult r = test.checkIndependence(x, y, Collections.emptySet());
            return r.isIndependent();
        }
        if (pool.size() < k) return false;
        ChoiceGenerator gen = new ChoiceGenerator(pool.size(), k);
        int[] choice;
        while ((choice = gen.next()) != null) {
            Set<Node> S = GraphUtils.asSet(choice, pool);
            IndependenceResult r = test.checkIndependence(x, y, S);
            if (r.isIndependent()) return true;
        }
        return false;
    }

    private boolean allowedArrow(Node from, Node to) {
        if (knowledge == null || knowledge.isEmpty()) return true;
        String f = baseName(from), t = baseName(to);
        if (knowledge.isForbidden(f, t)) return false;
        if (knowledge.isRequired(t, f)) return false; // would contradict required reverse
        return true;
    }

    private static Set<Node> limitSize(Set<Node> S, int cap) {
        if (cap < 0 || S.size() <= cap) return S;
        Iterator<Node> it = S.iterator();
        LinkedHashSet<Node> T = new LinkedHashSet<>();
        for (int i = 0; i < cap && it.hasNext(); i++) T.add(it.next());
        return T;
    }

    private static String baseName(Node n) {
        String s = n.getName();
        int p = s.indexOf(':');
        return p >= 0 ? s.substring(0, p) : s.replaceAll("_t-\\d+$","");
    }

    // ----------------- lag indexing ------------------

    /** Helper to parse lag structure of TsUtils.createLagData output. */
// ----------------- lag indexing ------------------

    /** Helper to parse lag structure of TsUtils.createLagData output. */
    private static final class TimeLagIndex {
        private final Map<Node,Integer> lag = new LinkedHashMap<>();
        private final Map<String,Node> base = new LinkedHashMap<>();
        private final List<Node> lag0 = new ArrayList<>();
        private final List<Node> all;
        @SuppressWarnings("unused")
        private final int L;

        static TimeLagIndex from(DataSet lagged, int maxLag) {
            TimeLagIndex I = new TimeLagIndex(lagged.getVariables(), maxLag);
            for (Node v : lagged.getVariables()) {
                int ell = parseLag(v.getName());
                I.lag.put(v, ell);
                String b = baseOfName(v.getName());
                I.base.putIfAbsent(b, new ContinuousVariable(b));
                if (ell == 0) I.lag0.add(v);
            }
            return I;
        }

        private TimeLagIndex(List<Node> vars, int maxLag) { this.all = vars; this.L = maxLag; }

        List<Node> lag0Nodes() { return Collections.unmodifiableList(lag0); }
        List<Node> baseNodes() { return new ArrayList<>(base.values()); }
        int lagOf(Node v){ return lag.getOrDefault(v, 0); }

        Node baseOf(Node v){
            String b = baseOfName(v.getName());
            return base.get(b);
        }

        /** All strictly past nodes of v: nodes with higher lag index (farther in the past). */
        List<Node> strictPastOf(Node v){
            int ellV = lagOf(v);
            List<Node> out = new ArrayList<>();
            for (Node u : all) {
                int ellU = lagOf(u);
                if (ellU > ellV) out.add(u);
            }
            return out;
        }

        /** Robust lag parser: supports "X:t-2", "X:2", "X_t-2". Returns 0 if no lag marker. */
        private static int parseLag(String name){
            // Prefer the most specific patterns first
            // 1) "...:t-<k>"
            int pt = name.lastIndexOf(":t-");
            if (pt >= 0) {
                try { return Integer.parseInt(name.substring(pt + 3)); } catch (Exception ignored) {}
            }
            // 2) "...:<k>" (your "X1:1" style)
            int pc = name.lastIndexOf(':');
            if (pc >= 0) {
                String tail = name.substring(pc + 1);
                try { return Integer.parseInt(tail); } catch (Exception ignored) {}
            }
            // 3) "..._t-<k>"
            int pu = name.lastIndexOf("_t-");
            if (pu >= 0) {
                try { return Integer.parseInt(name.substring(pu + 3)); } catch (Exception ignored) {}
            }
            // Fallback: no lag marker -> 0
            return 0;
        }

        /** Base name before any lag marker. */
        private static String baseOfName(String name){
            // If there’s a colon, base is before the last colon (handles "X:2" and "group:X:2")
            int pc = name.lastIndexOf(':');
            if (pc >= 0) return name.substring(0, pc);

            // Else strip a trailing "_t-<k>"
            int pu = name.lastIndexOf("_t-");
            if (pu >= 0) return name.substring(0, pu);

            // Else strip a trailing ":t-<k>" (defensive)
            int pt = name.lastIndexOf(":t-");
            if (pt >= 0) return name.substring(0, pt);

            return name;
        }
    }}