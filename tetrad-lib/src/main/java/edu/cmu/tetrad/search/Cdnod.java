package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.utils.MeekRules;
import edu.cmu.tetrad.search.utils.SepsetMap;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.TetradLogger;

import java.lang.reflect.Method;
import java.util.*;

/**
 * CD-NOD implementing IGraphSearch.
 * Pipeline: FAS skeleton -> force C->X (knowledge-aware) -> UC (SEPSETS/CPC/MAX_P) -> Meek (knowledge-aware).
 * Assumption: last column in the working DataSet is the change index C.
 */
public final class Cdnod implements IGraphSearch {

    public enum ColliderOrientationStyle { SEPSETS, CONSERVATIVE, MAX_P }

    // --- core config ---
    private IndependenceTest test;
    private DataSet dataWithC;               // MUST be set before search(); last column is C
    private final double alpha;              // left for parity; not directly used unless Fas exposes setAlpha
    private final boolean stable;
    private final ColliderOrientationStyle colliderStyle;
    private final Knowledge knowledge;
    private final boolean verbose;
    private final double maxPMargin;         // tie-guard for MAX_P (0.0 = classic)
    private final int depth;                 // S-size cap; also applied to FAS for consistency

    // --- runtime ---
    private long timeoutMs = -1;
    private long startTimeMs = 0;

    // -------- Builder --------
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

        public Builder test(IndependenceTest t){ this.test = Objects.requireNonNull(t); return this; }
        /** Provide a DataSet that ALREADY ends with C as the last column. */
        public Builder data(DataSet dataWithC){ this.dataWithC = dataWithC; return this; }
        /** Provide X and a continuous change index C to append as the last column. */
        public Builder dataAndIndex(DataSet dataX, double[] cIndex, String cName){
            this.dataX = dataX; this.cIndex = cIndex; if (cName != null && !cName.isBlank()) this.cName = cName;
            return this;
        }

        public Builder alpha(double a){ this.alpha = a; return this; }
        public Builder stable(boolean s){ this.stable = s; return this; }
        public Builder colliderStyle(ColliderOrientationStyle c){ this.colliderStyle = c; return this; }
        public Builder knowledge(Knowledge k){ this.knowledge = (k==null? new Knowledge(): new Knowledge(k)); return this; }
        public Builder verbose(boolean v){ this.verbose = v; return this; }
        public Builder maxPMargin(double m){ this.maxPMargin = Math.max(0.0, m); return this; }
        public Builder depth(int d){ this.depth = d; return this; }

        public Cdnod build(){
            if (test == null) throw new IllegalStateException("IndependenceTest must be provided.");
            DataSet working = dataWithC;
            if (working == null && dataX != null && cIndex != null) {
                working = appendChangeIndexAsLastColumn(dataX, cIndex, cName);
            }
            Cdnod cd = new Cdnod(test, working, alpha, stable, colliderStyle, knowledge, verbose, maxPMargin, depth);
            return cd;
        }
    }

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
        if (this.test == null) { this.test = newTest; return; }
        List<Node> oldVars = this.test.getVariables();
        List<Node> newVars = newTest.getVariables();
        if (!oldVars.equals(newVars)) {
            throw new IllegalArgumentException("Proposed test's variables must equal the existing test's variables (same order).");
        }
        this.test = newTest;
    }

    // =============== Public helpers ===============

    /** If you need to set/replace the working dataset that already includes C as the LAST column. */
    public void setDataWithC(DataSet dataWithC){
        this.dataWithC = dataWithC;
    }

    /** Convenience to append a continuous change index C to X and set as working dataset. */
    public void setDataAndIndex(DataSet dataX, double[] cIndex, String cName){
        this.dataWithC = appendChangeIndexAsLastColumn(dataX, cIndex, cName);
    }

    public void setTimeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; }

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
                    if (x.getName().compareTo(y.getName()) > 0) { Node tmp = x; x = y; y = tmp; }

                    switch (colliderStyle) {
                        case SEPSETS -> {
                            Set<Node> s = sepsets.get(x, y);
                            if (s != null && !s.contains(z) && canOrientCollider(g, x, z, y)) {
                                GraphUtils.orientCollider(g, x, z, y);
                                if (verbose) TetradLogger.getInstance().log("[SEPSETS] " + x + "->" + z + "<-" + y + " (S=" + labelSet(s) + ")");
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
                                if (verbose) TetradLogger.getInstance().log("[MAX-P] " + x + "->" + z + "<-" + y + " (p=" + d.bestP + ", S=" + labelSet(d.bestS) + ")");
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
            if (c.S.contains(z)) sawIncl = true; else sawExcl = true;
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
                if (c.p > bestIncl) { bestIncl = c.p; bestS_incl = c.S; }
            } else {
                if (c.p > bestExcl) { bestExcl = c.p; bestS_excl = c.S; }
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
        adjx.remove(y); adjy.remove(x);
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
        try { if (knowledge.isForbidden(from, to)) return true; } catch (Throwable ignored) {}
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
        } catch (Throwable ignored) {}
        // If Knowledge exposes isForbiddenByTiers(String,String)
        try {
            Method m = Knowledge.class.getMethod("isForbiddenByTiers", String.class, String.class);
            Object v = m.invoke(knowledge, from, to);
            if (v instanceof Boolean && (Boolean) v) return true;
        } catch (Throwable ignored) {}
        return false;
    }

    private boolean knowledgeRequires(String from, String to) {
        if (knowledge == null || knowledge.isEmpty()) return false;
        try { return knowledge.isRequired(from, to); } catch (Throwable ignored) {}
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

    // ----- tiny records -----
    private enum ColliderOutcome { INDEPENDENT, DEPENDENT, AMBIGUOUS, NO_SEPSET }

    private static final class SepCand {
        final Set<Node> S;
        final boolean indep;
        final double p;
        SepCand(Set<Node> s, boolean indep, double p){
            List<Node> sorted = new ArrayList<>(s);
            sorted.sort(Comparator.comparing(Node::getName));
            this.S = new LinkedHashSet<>(sorted);
            this.indep = indep; this.p = p;
        }
    }

    private static final class MaxPDecision {
        final ColliderOutcome outcome;
        final double bestP;
        final Set<Node> bestS;
        MaxPDecision(ColliderOutcome out, double bestP, Set<Node> bestS){
            this.outcome = out; this.bestP = bestP; this.bestS = bestS;
        }
    }
}