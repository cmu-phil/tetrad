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
 * context variables (Tier-0 in {@link Knowledge}).
 *
 * <p><b>Important behavioral change:</b> This implementation no longer requires a single context variable to be the last
 * column. Instead, <b>ALL Tier-0 variables</b> in the supplied {@link Knowledge} are treated as context variables
 * (consistent with CD-NOD-PAG).</p>
 *
 * <p>Optionally, contexts can be excluded from conditioning sets (enabled by default) to match the PAG runner behavior
 * ({@code withExcludeContextsFromS(true)} in {@code CdnodPag}).</p>
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
    private DataSet data;                    // dataset containing all variables (contexts may be anywhere)

    // --- runtime ---
    private long timeoutMs = -1;
    private long startTimeMs = 0;

    // --- derived per run() ---
    private Set<Node> contextNodes = Collections.emptySet();

    // --- behavior flags ---
    // Match CD-NOD-PAG: exclude contexts from conditioning sets by default.
    private final boolean excludeContextsFromS = true;

    private Cdnod(IndependenceTest test,
                  DataSet data,
                  double alpha,
                  boolean stable,
                  ColliderOrientationStyle colliderStyle,
                  Knowledge knowledge,
                  boolean verbose,
                  double maxPMargin,
                  int depth) {
        this.test = test;
        this.data = data; // may be null; user can set later
        this.alpha = alpha;
        this.stable = stable;
        this.colliderStyle = colliderStyle;
        this.knowledge = knowledge == null ? new Knowledge() : knowledge;
        this.verbose = verbose;
        this.maxPMargin = maxPMargin;
        this.depth = depth;
    }

    /**
     * Backwards-compatible helper: append a single continuous change-index column as the last column.
     * <p>
     * NOTE: This is no longer required for CD-NOD; contexts are read from Knowledge tier 0. This helper remains to
     * support older calling code that supplies (X, cIndex).
     */
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

    // =============== IGraphSearch ===============

    @Override
    public Graph search() throws InterruptedException {
        if (data == null) {
            throw new IllegalStateException("Cdnod: data is null. Provide a DataSet via Builder.data(...), " +
                                            "or use Builder.dataAndIndex(...) to append a column before search().");
        }

        // Ensure test variables match dataset variables
        List<Node> testVars = test.getVariables();
        if (!testVars.equals(data.getVariables())) {
            throw new IllegalStateException("Cdnod: IndependenceTest variables must match data variables (same order).");
        }

        return run(data);
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
     * @param data the dataset to be assigned
     */
    public void setData(DataSet data) {
        this.data = data;
    }

    /**
     * Backwards-compatible setter that appends a single change-index column as the last column and stores that as data.
     * <p>
     * NOTE: contexts are read from Knowledge tier 0. If you want this appended column treated as a context, put its
     * name in tier 0 of the Knowledge you pass in (e.g., "C").
     */
    public void setDataAndIndex(DataSet dataX, double[] cIndex, String cName) {
        this.data = appendChangeIndexAsLastColumn(dataX, cIndex, cName);
    }

    /**
     * Sets the timeout value in milliseconds for this instance.
     *
     * @param timeoutMs the timeout in milliseconds
     */
    public void setTimeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    // =============== Core ===============

    private Graph run(DataSet dataAll) throws InterruptedException {
        this.startTimeMs = System.currentTimeMillis();

        // Resolve contexts from Knowledge tier 0 (CD-NOD-PAG semantics).
        this.contextNodes = resolveContextNodesTier0(dataAll);

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

        // If no contexts were provided, we just do PC-style orientation and return.
        if (contextNodes.isEmpty()) {
            if (verbose) {
                TetradLogger.getInstance().log("CD-NOD: No Tier-0 contexts in Knowledge; skipping context forcing.");
            }
        } else {
            // 2) Force Context -> X where adjacent (respect knowledge/tiers)
            if (verbose) {
                List<String> cn = contextNodes.stream().map(Node::getName).sorted().toList();
                TetradLogger.getInstance().log("CD-NOD: Forcing Context -> X for contexts=" + cn);
            }
            for (Node c : contextNodes) {
                for (Node nbr : new ArrayList<>(g.getAdjacentNodes(c))) {
                    if (contextNodes.contains(nbr)) continue; // do not force among contexts
                    String from = c.getName(), to = nbr.getName();
                    if (knowledgeForbids(from, to) || knowledgeRequires(to, from)) {
                        continue; // skip if forbidden or opposite required
                    }
                    g.removeEdges(c, nbr);
                    g.addDirectedEdge(c, nbr);
                }
            }
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

    /**
     * Context semantics: all Tier-0 variables in {@link Knowledge} are treated as contexts.
     * Any Tier-0 names not present in the DataSet are silently ignored (matches the PAG runner style).
     */
    private Set<Node> resolveContextNodesTier0(DataSet dataAll) {
        if (knowledge == null) return Collections.emptySet();
        Set<Node> out = new LinkedHashSet<>();
        try {
            List<String> tier0 = knowledge.getTier(0);
            for (String name : tier0) {
                Node v = dataAll.getVariable(name);
                if (v != null) out.add(v);
            }
        } catch (Throwable ignored) {
            // If tier APIs aren't available in this Knowledge version, fall back to empty.
        }
        return out;
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

        // Match CD-NOD-PAG: exclude contexts from S (conditioning candidates).
        if (excludeContextsFromS && contextNodes != null && !contextNodes.isEmpty()) {
            adjx.removeAll(contextNodes);
            adjy.removeAll(contextNodes);
        }

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
        SEPSETS,
        CONSERVATIVE,
        MAX_P
    }

    private enum ColliderOutcome {
        INDEPENDENT,
        DEPENDENT,
        AMBIGUOUS,
        NO_SEPSET
    }

    /**
     * Builder class for creating instances of the Cdnod class with customized parameters.
     *
     * <p><b>Note:</b> This builder remains backwards-compatible: you may still call {@code dataAndIndex(...)} to append a
     * "C" column. But CD-NOD now determines contexts from Knowledge tier 0, not from column position.</p>
     */
    public static final class Builder {
        private IndependenceTest test;
        private DataSet data;       // renamed from dataWithC (but keep semantics)
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

        public Builder() {
        }

        public Builder test(IndependenceTest t) {
            this.test = Objects.requireNonNull(t);
            return this;
        }

        /**
         * Provide a DataSet containing all variables (contexts may be anywhere).
         */
        public Builder data(DataSet data) {
            this.data = data;
            return this;
        }

        /**
         * Backwards-compatible: provide X and a continuous change index C to append as the last column.
         * If you want the appended column to be treated as a context, add its name (default "C") to Knowledge tier 0.
         */
        public Builder dataAndIndex(DataSet dataX, double[] cIndex, String cName) {
            this.dataX = dataX;
            this.cIndex = cIndex;
            if (cName != null && !cName.isBlank()) this.cName = cName;
            return this;
        }

        public Builder alpha(double a) {
            this.alpha = a;
            return this;
        }

        public Builder stable(boolean s) {
            this.stable = s;
            return this;
        }

        public Builder colliderStyle(ColliderOrientationStyle c) {
            this.colliderStyle = c;
            return this;
        }

        public Builder knowledge(Knowledge k) {
            this.knowledge = (k == null ? new Knowledge() : new Knowledge(k));
            return this;
        }

        public Builder verbose(boolean v) {
            this.verbose = v;
            return this;
        }

        public Builder maxPMargin(double m) {
            this.maxPMargin = Math.max(0.0, m);
            return this;
        }

        public Builder depth(int d) {
            this.depth = d;
            return this;
        }

        public Cdnod build() {
            if (test == null) throw new IllegalStateException("IndependenceTest must be provided.");
            DataSet working = data;
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