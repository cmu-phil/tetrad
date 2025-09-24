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
 * <p><strong>PCMCI</strong> (Runge et&nbsp;al.) &mdash; minimal, time-series implementation with
 * lagged edges only (&tau;&ge;1).</p>
 *
 * <p><em>Pipeline:</em></p>
 * <ol>
 *   <li>Build lagged variables up to <code>maxLag</code>.</li>
 *   <li>Parent pre-selection per node <code>V</code>: PC1-like elimination using only the strict past of <code>V</code>.</li>
 *   <li>MCI step: keep
 *       <code>X</code><sub>t-&tau;</sub> &rarr; <code>Y</code><sub>t</sub>
 *       iff <code>X</code> &not;&perp; <code>Y</code> &nbsp;|&nbsp;
 *       <code>Pa(Y)</code>\{<code>X</code>} &cup; <code>Pa(X)</code>.
 *   </li>
 *   <li>Add directed edges
 *       <code>X</code><sub>t-&tau;</sub> &rarr; <code>Y</code><sub>t</sub>;
 *       optionally collapse to base-time nodes.</li>
 * </ol>
 *
 * <p><em>Notes:</em></p>
 * <ul>
 *   <li><code>IndependenceTest</code> is user-supplied (e.g., FisherZ, etc.).</li>
 *   <li><code>Knowledge</code> constraints are enforced for allowed/forbidden arcs (including tiering).</li>
 *   <li>This version orients only &tau;&gt;0 (no instantaneous &tau;=0); add later for PCMCI+.</li>
 * </ul>
 */
public final class Pcmci implements IGraphSearch {

    /**
     * Builder class for constructing instances of the Pcmci class.
     * This class allows customization of various parameters necessary
     * for the creation and operation of the Pcmci search algorithm.
     */
    public static final class Builder {
        private final DataSet data;
        private final IndependenceTest test;
        private int maxLag = 3;
        private double alpha = 0.05;           // informational unless the test uses it internally
        private int maxCondSize = 3;           // cap |S| in both phases
        private boolean verbose = false;
        private boolean collapseToLag0 = true; // collapse lagged graph back to base nodes

        /**
         * Constructs a Builder instance for configuring and creating a Pcmci object.
         *
         * @param data the data set to be used for the Pcmci algorithm
         * @param test the independence test to be used in the search process
         * @throws NullPointerException if the provided data or test is null
         */
        public Builder(DataSet data, IndependenceTest test) {
            this.data = Objects.requireNonNull(data);
            this.test = Objects.requireNonNull(test);
        }

        /**
         * Sets the maximum lag to be considered in the Pcmci algorithm.
         * The maximum lag determines the largest time lag allowed for relationships
         * between time series variables in the analysis. The value is constrained
         * to be at least 1.
         *
         * @param L the proposed maximum lag to be set (must be a positive integer)
         * @return this builder instance, allowing for method chaining
         */
        public Builder maxLag(int L){ this.maxLag = Math.max(1, L); return this; }

        /**
         * Sets the significance level (alpha) to be used in the Pcmci algorithm.
         * The alpha value is typically used to determine the threshold for statistical
         * significance in the independence tests performed by the algorithm.
         *
         * @param a the significance level to be set, typically a value between 0 and 1
         * @return this builder instance, allowing for method chaining
         */
        public Builder alpha(double a){ this.alpha = a; return this; }

        /**
         * Sets the maximum conditioning set size (|S|) to be used in the Pcmci algorithm.
         * This parameter limits the size of the conditioning sets used during both
         * phases of the algorithm.
         *
         * @param k the maximum conditioning set size to be set
         * @return this builder instance, allowing for method chaining
         */
        public Builder maxCondSize(int k){ this.maxCondSize = k; return this; }

        /**
         * Sets the verbosity level for the Pcmci algorithm.
         * This method allows enabling or disabling of detailed logging or output during
         * the execution of the algorithm. When verbose is set to true, the algorithm may
         * provide additional information about its internal operations and progress.
         *
         * @param v a boolean value indicating whether verbose mode should be enabled
         *          (true for enabling detailed output, false for disabling it)
         * @return this builder instance, allowing for method chaining
         */
        public Builder verbose(boolean v){ this.verbose = v; return this; }

        /**
         * Sets whether the Pcmci algorithm should collapse all lags greater than zero to lag 0.
         * Collapsing to lag 0 means that the algorithm will not distinguish between relationships
         * at different time lags and will consider all relationships as instantaneous.
         *
         * @param c a boolean value indicating whether lag collapsing should be applied
         *          (true to collapse all lags to 0, false to retain lagged relationships)
         * @return this builder instance, allowing for method chaining
         */
        public Builder collapseToLag0(boolean c){ this.collapseToLag0 = c; return this; }

        /**
         * Builds and returns a Pcmci object configured with the parameters set in the Builder instance.
         * The Pcmci object created will use the specified data, test, and various optional configurations,
         * such as maximum lag, significance level, maximum conditioning set size, verbosity, and lag collapsing.
         *
         * @return a new Pcmci instance based on the current Builder configuration
         */
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

    /**
     * Executes the PCMCI (Peter and Clark Momentary Conditional Independence) algorithm
     * to discover causal relationships in time series data. The method constructs a
     * lagged dataset up to the specified maximum lag, performs parent preselection,
     * confirms causal relationships using conditional independence tests, and creates
     * a directed graph representing the causal structure.
     *
     * @return A directed graph where nodes represent time-lagged variables or base-time
     *         variables (depending on the value of `collapseToLag0`) and directed edges
     *         represent causal relationships derived using the PCMCI algorithm.
     * @throws InterruptedException If the process is interrupted during computation.
     */
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

    /**
     * Retrieves the current independence test used by the PCMCI algorithm.
     *
     * @return The {@link IndependenceTest} instance currently associated with this class.
     */
    @Override
    public IndependenceTest getTest() { return test; }

    /**
     * Sets a new independence test for the PCMCI algorithm. The variables used by the new test
     * must match the variables of the current test to ensure consistency in the analysis.
     * Switching tests is also restricted after the PCMCI object has been built.
     *
     * @param newTest The {@link IndependenceTest} instance to set as the new independence test.
     * @throws IllegalArgumentException If the variables in the new independence test do not match
     *                                  the variables in the current test.
     * @throws IllegalStateException If attempting to switch the independence test after the PCMCI
     *                               object has been built.
     */
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