package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.utils.MeekRules;
import edu.cmu.tetrad.search.utils.SepsetMap;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.TetradLogger;

import java.lang.reflect.Method;
import java.util.*;

/**
 * The {@code Cdnod} class implements the causal discovery algorithm for detecting changing dependencies with respect to
 * a change index variable in a dataset.
 *
 * <p>The algorithm is capable of orienting edges in a graph based on statistical
 * tests, knowledge constraints, and user-defined parameters. It also supports conservative decision-making and
 * constraints on collider orientations.</p>
 *
 * <p>This class extends the functionality of {@code IGraphSearch} and provides methods
 * for configuring the statistical independence test, working dataset, maximum conditioning depth, and timeout
 * settings.</p>
 * <p>
 * Key Features:
 * <ul>
 *   <li>Searches for a graph representing causal structure based on a change index variable.</li>
 *   <li>Provides support for collider orientation with both conservative logic and
 *       maximum p-margin decisions.</li>
 *   <li>Enables customization of constraints using a {@code Knowledge} object.</li>
 *   <li>Supports timeout settings to limit the duration of computations.</li>
 * </ul>
 */
public final class Cdnod implements IGraphSearch {

    private final double alpha;              // left for parity; not directly used unless Fas exposes setAlpha
    private final boolean stable;
    private final ColliderOrientationStyle colliderStyle;
    private final Knowledge knowledge;
    private final boolean verbose;
    private final double maxPMargin;         // tie-guard for MAX_P (0.0 = classic)
    private final int depth;                 // S-size cap; also applied to FAS for consistency
    // --- core config ---
    private IndependenceTest test;
    private DataSet dataWithC;               // MUST be set before search(); last column is C
    // --- runtime ---
    private long timeoutMs = -1;
    private long startTimeMs = 0;

    private Cdnod(IndependenceTest test,
                  DataSet dataWithC,
                  double alpha,
                  boolean stable,
                  ColliderOrientationStyle colliderStyle,
                  Knowledge knowledge,
                  boolean verbose,
                  double maxPMargin,
                  int depth) {
        this.test = test;
        this.dataWithC = dataWithC; // may be null; user can set later
        this.alpha = alpha;
        this.stable = stable;
        this.colliderStyle = colliderStyle;
        this.knowledge = knowledge == null ? new Knowledge() : knowledge;
        this.verbose = verbose;
        this.maxPMargin = maxPMargin;
        this.depth = depth;
    }

    private static DataSet appendChangeIndexAsLastColumn(DataSet dataX, double[] cIndex, String cName) {
        if (cIndex.length != dataX.getNumRows())
            throw new IllegalArgumentException("Length mismatch: cIndex vs rows.");
        String name = (cName == null || cName.isBlank()) ? "C" : cName;

        int n = dataX.getNumRows();
        int p = dataX.getNumColumns();

        List<Node> vars = new ArrayList<>(dataX.getVariables());
        ContinuousVariable cVar = new ContinuousVariable(name);
        vars.add(cVar);

        DoubleDataBox box = new DoubleDataBox(n, p + 1);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < p; j++) box.set(i, j, dataX.getDouble(i, j));
            box.set(i, p, cIndex[i]);
        }
        return new BoxDataSet(box, vars);
    }

    private static void ensureLastIsChangeIndex(DataSet data) {
        if (data.getNumColumns() < 2) {
            throw new IllegalArgumentException("Expect at least one X column plus C as last column.");
        }
    }

    // =============== IGraphSearch ===============

    @Override
    public Graph search() throws InterruptedException {
        if (dataWithC == null) {
            throw new IllegalStateException("Cdnod: dataWithC is null. Provide a DataSet whose last column is C, " +
                                            "or use the Builder.dataAndIndex(...) to append C before search().");
        }
        ensureLastIsChangeIndex(dataWithC);
        // Optional: ensure test variables match dataset variables
        List<Node> testVars = test.getVariables();
        if (!testVars.equals(dataWithC.getVariables())) {
            throw new IllegalStateException("Cdnod: IndependenceTest variables must match dataWithC variables (same order).");
        }
        return run(dataWithC);
    }

    @Override
    public IndependenceTest getTest() {
        return this.test;
    }

    @Override
    public void setTest(IndependenceTest newTest) {
        if (newTest == null) throw new IllegalArgumentException("test cannot be null");
        if (this.test == null) {
            this.test = newTest;
            return;
        }
        List<Node> oldVars = this.test.getVariables();
        List<Node> newVars = newTest.getVariables();
        if (!oldVars.equals(newVars)) {
            throw new IllegalArgumentException("Proposed test's variables must equal the existing test's variables (same order).");
        }
        this.test = newTest;
    }

    // =============== Public helpers ===============

    /**
     * Sets the dataset to be used in this instance.
     *
     * @param dataWithC the dataset to be assigned
     */
    public void setDataWithC(DataSet dataWithC) {
        this.dataWithC = dataWithC;
    }

    /**
     * Updates the internal dataset by appending a change index as the last column.
     * This method modifies the data to include an additional column (defined by the provided change index and name)
     * and stores the updated dataset for further processing.
     *
     * @param dataX the original dataset to which the change index will be appended
     * @param cIndex an array representing the change index values to be incorporated into the dataset
     * @param cName the name of the new column that will represent the change index
     */
    public void setDataAndIndex(DataSet dataX, double[] cIndex, String cName) {
        this.dataWithC = appendChangeIndexAsLastColumn(dataX, cIndex, cName);
    }

    /**
     * Sets the timeout value in milliseconds for this instance. This value determines the maximum time allowed for
     * certain operations before they are interrupted or terminated.
     *
     * @param timeoutMs the timeout in milliseconds
     */
    public void setTimeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    // =============== Core ===============

    private Graph run(DataSet dataAug) throws InterruptedException {
        this.startTimeMs = System.currentTimeMillis();

        // 1) Skeleton (FAS)
        Fas fas = new Fas(test);
        fas.setStable(stable);
        fas.setVerbose(verbose);
        if (knowledge != null && !knowledge.isEmpty()) fas.setKnowledge(knowledge);
        if (depth >= 0) fas.setDepth(depth);
        // If Fas exposes alpha, you can uncomment:
        // fas.setAlpha(alpha);

        if (verbose) TetradLogger.getInstance().log("CD-NOD: FAS skeleton...");
        Graph g = fas.search();
        SepsetMap sepsets = fas.getSepsets();

        // 2) Force C -> X where adjacent (respect knowledge/tiers)
        Node C = dataAug.getVariable(dataAug.getNumColumns() - 1);
        if (verbose) TetradLogger.getInstance().log("CD-NOD: Forcing " + C.getName() + " -> X");
        for (Node nbr : new ArrayList<>(g.getAdjacentNodes(C))) {
            String from = C.getName(), to = nbr.getName();
            if (knowledgeForbids(from, to) || knowledgeRequires(to, from)) {
                continue; // skip if forbidden or opposite required
            }
            g.removeEdges(C, nbr);
            g.addDirectedEdge(C, nbr);
        }

        // 3) UC orientation per style
        if (verbose) TetradLogger.getInstance().log("CD-NOD: UC orientation (" + colliderStyle + ")...");
        orientUnshieldedTriples(g, sepsets);

        // 4) Meek closure
        if (verbose) TetradLogger.getInstance().log("CD-NOD: Meek closure...");
        MeekRules meek = new MeekRules();
        meek.setKnowledge(knowledge);
        meek.orientImplied(g);

        return g;
    }

    // ------------- collider orientation (SEPSETS / CONSERVATIVE / MAX_P) --------------

    private void orientUnshieldedTriples(Graph g, SepsetMap sepsets) throws InterruptedException {
        List<Node> nodes = new ArrayList<>(g.getNodes());
        nodes.sort(Comparator.comparing(Node::getName));

        for (Node z : nodes) {
            List<Node> adj = new ArrayList<>(g.getAdjacentNodes(z));
            adj.sort(Comparator.comparing(Node::getName));

            for (int i = 0; i < adj.size(); i++) {
                Node x = adj.get(i);
                for (int j = i + 1; j < adj.size(); j++) {
                    Node y = adj.get(j);
                    if (g.isAdjacentTo(x, y)) continue; // only unshielded

                    checkTimeout();

                    // Canonicalize endpoints (x <= y by name)
                    if (x.getName().compareTo(y.getName()) > 0) {
                        Node tmp = x;
                        x = y;
                        y = tmp;
                    }

                    switch (colliderStyle) {
                        case SEPSETS -> {
                            Set<Node> s = sepsets.get(x, y);
                            if (s != null && !s.contains(z) && canOrientCollider(g, x, z, y)) {
                                GraphUtils.orientCollider(g, x, z, y);
                                if (verbose)
                                    TetradLogger.getInstance().log("[SEPSETS] " + x + "->" + z + "<-" + y + " (S=" + labelSet(s) + ")");
                            }
                        }
                        case CONSERVATIVE -> {
                            ColliderOutcome out = judgeConservative(g, x, z, y);
                            if (out == ColliderOutcome.INDEPENDENT && canOrientCollider(g, x, z, y)) {
                                GraphUtils.orientCollider(g, x, z, y);
                                if (verbose) TetradLogger.getInstance().log("[CPC] " + x + "->" + z + "<-" + y);
                            }
                        }
                        case MAX_P -> {
                            MaxPDecision d = decideMaxP(g, x, z, y);
                            if (d.outcome == ColliderOutcome.INDEPENDENT && canOrientCollider(g, x, z, y)) {
                                GraphUtils.orientCollider(g, x, z, y);
                                if (verbose)
                                    TetradLogger.getInstance().log("[MAX-P] " + x + "->" + z + "<-" + y + " (p=" + d.bestP + ", S=" + labelSet(d.bestS) + ")");
                            }
                        }
                    }
                }
            }
        }
    }

    // CPC: if any separating set S excludes z AND no separating set includes z -> collider.
    // if both kinds exist -> ambiguous; if only includes-z exist -> noncollider; if none -> no sepset.
    private ColliderOutcome judgeConservative(Graph g, Node x, Node z, Node y) throws InterruptedException {
        boolean sawAny = false, sawIncl = false, sawExcl = false;

        for (SepCand c : enumerateSepsetsWithP(g, x, y)) {
            if (!c.indep) continue;
            sawAny = true;
            if (c.S.contains(z)) sawIncl = true;
            else sawExcl = true;
            if (sawIncl && sawExcl) return ColliderOutcome.AMBIGUOUS;
        }
        if (!sawAny) return ColliderOutcome.NO_SEPSET;
        if (sawExcl && !sawIncl) return ColliderOutcome.INDEPENDENT;
        if (sawIncl && !sawExcl) return ColliderOutcome.DEPENDENT;
        return ColliderOutcome.AMBIGUOUS;
    }

    // MAX-P: pick side (includes-z vs excludes-z) with strictly larger best p (by > margin). Else ambiguous.
    private MaxPDecision decideMaxP(Graph g, Node x, Node z, Node y) throws InterruptedException {
        double bestIncl = Double.NEGATIVE_INFINITY;
        double bestExcl = Double.NEGATIVE_INFINITY;
        Set<Node> bestS_incl = Collections.emptySet();
        Set<Node> bestS_excl = Collections.emptySet();

        for (SepCand c : enumerateSepsetsWithP(g, x, y)) {
            if (!c.indep) continue;
            if (c.S.contains(z)) {
                if (c.p > bestIncl) {
                    bestIncl = c.p;
                    bestS_incl = c.S;
                }
            } else {
                if (c.p > bestExcl) {
                    bestExcl = c.p;
                    bestS_excl = c.S;
                }
            }
        }
        boolean hasIncl = bestIncl > Double.NEGATIVE_INFINITY;
        boolean hasExcl = bestExcl > Double.NEGATIVE_INFINITY;

        if (hasIncl && hasExcl) {
            if (bestExcl >= bestIncl + maxPMargin)
                return new MaxPDecision(ColliderOutcome.INDEPENDENT, bestExcl, bestS_excl);
            if (bestIncl >= bestExcl + maxPMargin)
                return new MaxPDecision(ColliderOutcome.DEPENDENT, bestIncl, bestS_incl);
            return new MaxPDecision(ColliderOutcome.AMBIGUOUS, Math.max(bestIncl, bestExcl),
                    (bestIncl >= bestExcl ? bestS_incl : bestS_excl));
        } else if (hasExcl) {
            return new MaxPDecision(ColliderOutcome.INDEPENDENT, bestExcl, bestS_excl);
        } else if (hasIncl) {
            return new MaxPDecision(ColliderOutcome.DEPENDENT, bestIncl, bestS_incl);
        } else {
            return new MaxPDecision(ColliderOutcome.NO_SEPSET, Double.NaN, Collections.emptySet());
        }
    }

    // enumerate candidate sepsets (unique by content), across both adjacency sides, up to depth cap.
    private Iterable<SepCand> enumerateSepsetsWithP(Graph g, Node x, Node y) throws InterruptedException {
        Map<String, SepCand> uniq = new LinkedHashMap<>();

        List<Node> adjx = new ArrayList<>(g.getAdjacentNodes(x));
        List<Node> adjy = new ArrayList<>(g.getAdjacentNodes(y));
        adjx.remove(y);
        adjy.remove(x);
        adjx.sort(Comparator.comparing(Node::getName));
        adjy.sort(Comparator.comparing(Node::getName));

        int maxAdj = Math.max(adjx.size(), adjy.size());
        int cap = (depth < 0 ? maxAdj : Math.min(depth, maxAdj));

        for (int d = 0; d <= cap; d++) {
            for (List<Node> adj : new List[]{adjx, adjy}) {
                if (d > adj.size()) continue;
                ChoiceGenerator gen = new ChoiceGenerator(adj.size(), d);
                int[] choice;
                while ((choice = gen.next()) != null) {
                    checkTimeout();
                    Set<Node> S = GraphUtils.asSet(choice, adj);
                    String key = setKey(S);
                    if (uniq.containsKey(key)) continue;

                    IndependenceResult r = test.checkIndependence(x, y, S);
                    uniq.put(key, new SepCand(S, r.isIndependent(), r.getPValue()));
                }
            }
        }
        return uniq.values();
    }

    // ------------- utils -------------

    private boolean canOrientCollider(Graph g, Node x, Node z, Node y) {
        if (!g.isAdjacentTo(x, z) || !g.isAdjacentTo(z, y)) return false;

        // Respect knowledge (forbids/requires + tiers)
        if (knowledge != null && !knowledge.isEmpty()) {
            if (knowledgeForbids(x.getName(), z.getName()) || knowledgeRequires(z.getName(), x.getName())) return false;
            if (knowledgeForbids(y.getName(), z.getName()) || knowledgeRequires(z.getName(), y.getName())) return false;
        }

        // Don’t create z->x or z->y conflicts
        return !g.isParentOf(z, x) && !g.isParentOf(z, y);
    }

    private boolean knowledgeForbids(String from, String to) {
        if (knowledge == null || knowledge.isEmpty()) return false;
        try {
            if (knowledge.isForbidden(from, to)) return true;
        } catch (Throwable ignored) {
        }
        // If tiers are defined and tier(from) > tier(to), treat as forbidden
        try {
            Method mNum = Knowledge.class.getMethod("getNumTiers");
            int T = (Integer) mNum.invoke(knowledge);
            if (T > 0) {
                Method mTier = Knowledge.class.getMethod("getTier", String.class);
                int tf = (Integer) mTier.invoke(knowledge, from);
                int tt = (Integer) mTier.invoke(knowledge, to);
                if (tf >= 0 && tt >= 0 && tf > tt) return true;
            }
        } catch (Throwable ignored) {
        }
        // If Knowledge exposes isForbiddenByTiers(String,String)
        try {
            Method m = Knowledge.class.getMethod("isForbiddenByTiers", String.class, String.class);
            Object v = m.invoke(knowledge, from, to);
            if (v instanceof Boolean && (Boolean) v) return true;
        } catch (Throwable ignored) {
        }
        return false;
    }

    private boolean knowledgeRequires(String from, String to) {
        if (knowledge == null || knowledge.isEmpty()) return false;
        try {
            return knowledge.isRequired(from, to);
        } catch (Throwable ignored) {
        }
        return false;
    }

    private String labelSet(Set<Node> S) {
        List<String> names = new ArrayList<>(S.stream().map(Node::getName).toList());
        Collections.sort(names);
        return "{" + String.join(",", names) + "}";
    }

    private String setKey(Set<Node> S) {
        List<String> names = new ArrayList<>(S.stream().map(Node::getName).toList());
        Collections.sort(names);
        return String.join("\u0001", names);
    }

    private void checkTimeout() throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Interrupted");
        if (timeoutMs >= 0) {
            long now = System.currentTimeMillis();
            if (now - startTimeMs > timeoutMs)
                throw new InterruptedException("Timed out after " + (now - startTimeMs) + " ms");
        }
    }

    /**
     * Enumeration representing different strategies for orienting colliders in causal discovery.
     */
    public enum ColliderOrientationStyle {
        /**
         * Orient based on separating sets derived from the data. Each pair of variables is analyzed to determine
         * whether a separating set exists to justify collider orientation.
         */
        SEPSETS,
        /**
         * Apply a conservative approach to collider orientation, favoring ambiguity when evidence for orientation is
         * inconclusive. This approach ensures orientational robustness under stricter constraints.
         */
        CONSERVATIVE,
        /**
         * Decide collider orientation by comparing the maximum p-value margins between two sides (includes given
         * variable vs excludes given variable). The decision is made based on which side has a strictly larger best
         * p-value over a specified threshold margin.
         */
        MAX_P
    }

    /**
     * Represents the possible outcomes when evaluating the presence of a collider in a causal graph under different
     * conditions or testing scenarios.
     * <p>
     * The enumerations are used in methods related to the identification and orientation of colliders during causal
     * structure learning, particularly within constraints-based or score-based search algorithms.
     */
    private enum ColliderOutcome {
        /**
         * Indicates that the nodes involved are conditionally independent and form a collider.
         */
        INDEPENDENT,
        /**
         * Indicates that the nodes involved are conditionally dependent under the given test.
         */
        DEPENDENT,
        /**
         * Indicates that the determination of a collider is uncertain due to conflicting evidence.
         */
        AMBIGUOUS,
        /**
         * Indicates that no valid separating set exists for the evaluated nodes within the given constraints.
         */
        NO_SEPSET
    }

    /**
     * Builder class for creating instances of the Cdnod class with customized parameters. The Builder provides a
     * flexible and fluent API for setting optional configurations in the resulting Cdnod instance.
     */
    public static final class Builder {
        private IndependenceTest test;
        private DataSet dataWithC;
        private DataSet dataX;
        private double[] cIndex;
        private String cName = "C";

        private double alpha = 0.05;
        private boolean stable = true;
        private ColliderOrientationStyle colliderStyle = ColliderOrientationStyle.SEPSETS;
        private Knowledge knowledge = new Knowledge();
        private boolean verbose = false;
        private double maxPMargin = 0.0;
        private int depth = -1;

        /**
         * Constructs a new instance of the Builder class. Instantiates an object used for configuring and creating
         * instances of {@link Cdnod}.
         */
        public Builder() {
        }

        /**
         * Sets the {@link IndependenceTest} instance to be used by the {@code Builder}.
         *
         * @param t The {@link IndependenceTest} instance to be set. This parameter must not be null.
         * @return The current Builder instance for method chaining.
         * @throws NullPointerException If the provided {@link IndependenceTest} instance is null.
         */
        public Builder test(IndependenceTest t) {
            this.test = Objects.requireNonNull(t);
            return this;
        }

        /**
         * Provide a DataSet that ALREADY ends with C as the last column.
         *
         * @param dataWithC The {@link DataSet} instance to be set. This parameter must not be null.
         * @return The current Builder instance for method chaining.
         */
        public Builder data(DataSet dataWithC) {
            this.dataWithC = dataWithC;
            return this;
        }

        /**
         * Provide X and a continuous change index C to append as the last column.
         *
         * @param dataX  The {@link DataSet} instance to be set. This parameter must not be null.
         * @param cIndex The continuous change index C to be appended. This parameter must not be null.
         * @param cName  The name of the continuous change index C. This parameter must not be null.
         * @return The current Builder instance for method chaining.
         */
        public Builder dataAndIndex(DataSet dataX, double[] cIndex, String cName) {
            this.dataX = dataX;
            this.cIndex = cIndex;
            if (cName != null && !cName.isBlank()) this.cName = cName;
            return this;
        }

        /**
         * Sets the alpha value to be used by the {@code Builder}. The alpha value typically represents the significance
         * level for statistical tests in the constructed object.
         *
         * @param a The alpha value to be set. This parameter must be a non-negative double.
         * @return The current Builder instance for method chaining.
         */
        public Builder alpha(double a) {
            this.alpha = a;
            return this;
        }

        /**
         * Configures whether the builder operates in a stable mode. This can affect the behavior of the resulting
         * object to ensure stability.
         *
         * @param s A boolean indicating whether stability should be enabled (true) or disabled (false).
         * @return The current Builder instance for method chaining.
         */
        public Builder stable(boolean s) {
            this.stable = s;
            return this;
        }

        /**
         * Sets the {@link ColliderOrientationStyle} to be used by the {@code Builder}. The
         * {@link ColliderOrientationStyle} determines the strategy for orienting colliders in causal discovery, such as
         * separating sets, conservative approaches, or using maximum p-value margins.
         *
         * @param c The {@link ColliderOrientationStyle} to be set. This parameter must not be null.
         * @return The current {@code Builder} instance for method chaining.
         * @throws NullPointerException If the provided {@link ColliderOrientationStyle} is null.
         */
        public Builder colliderStyle(ColliderOrientationStyle c) {
            this.colliderStyle = c;
            return this;
        }

        /**
         * Sets the {@link Knowledge} instance to be used by the {@code Builder}. If the provided {@link Knowledge}
         * instance is null, a new instance of {@link Knowledge} is created.
         *
         * @param k The {@link Knowledge} instance to be set. This parameter can be null.
         * @return The current {@code Builder} instance for method chaining.
         */
        public Builder knowledge(Knowledge k) {
            this.knowledge = (k == null ? new Knowledge() : new Knowledge(k));
            return this;
        }

        /**
         * Configures whether the builder operates in verbose mode. When enabled, verbose mode may produce more detailed
         * logs, messages, or debug outputs during the building process, depending on the specific implementation of the
         * builder or the constructed object.
         *
         * @param v A boolean indicating whether verbose mode should be enabled (true) or disabled (false).
         * @return The current Builder instance for method chaining.
         */
        public Builder verbose(boolean v) {
            this.verbose = v;
            return this;
        }

        /**
         * Sets the maximum p-value margin to be used by the {@code Builder}. If the provided value is negative, it is
         * set to 0.0.
         *
         * @param m The maximum p-value margin to be set. This parameter must be a non-negative double.
         * @return The current {@code Builder} instance for method chaining.
         */
        public Builder maxPMargin(double m) {
            this.maxPMargin = Math.max(0.0, m);
            return this;
        }

        /**
         * Sets the depth to be used by the {@code Builder}. The depth typically represents a parameter for controlling
         * the extent or level of operations in the constructed object.
         *
         * @param d The depth value to be set. This parameter must be a valid integer.
         * @return The current {@code Builder} instance for method chaining.
         */
        public Builder depth(int d) {
            this.depth = d;
            return this;
        }

        /**
         * Builds and returns an instance of {@link Cdnod} using the parameters specified in the {@code Builder}. The
         * method constructs the {@link Cdnod} object based on the provided or default configurations, ensuring that all
         * required parameters have been properly initialized.
         *
         * @return A newly constructed {@link Cdnod} instance.
         * @throws IllegalStateException If the {@link IndependenceTest} is not set before invoking this method.
         */
        public Cdnod build() {
            if (test == null) throw new IllegalStateException("IndependenceTest must be provided.");
            DataSet working = dataWithC;
            if (working == null && dataX != null && cIndex != null) {
                working = appendChangeIndexAsLastColumn(dataX, cIndex, cName);
            }
            return new Cdnod(test, working, alpha, stable, colliderStyle, knowledge, verbose, maxPMargin, depth);
        }
    }

    private static final class SepCand {
        final Set<Node> S;
        final boolean indep;
        final double p;

        SepCand(Set<Node> s, boolean indep, double p) {
            List<Node> sorted = new ArrayList<>(s);
            sorted.sort(Comparator.comparing(Node::getName));
            this.S = new LinkedHashSet<>(sorted);
            this.indep = indep;
            this.p = p;
        }
    }

    private static final class MaxPDecision {
        final ColliderOutcome outcome;
        final double bestP;
        final Set<Node> bestS;

        MaxPDecision(ColliderOutcome out, double bestP, Set<Node> bestS) {
            this.outcome = out;
            this.bestP = bestP;
            this.bestS = bestS;
        }
    }
}