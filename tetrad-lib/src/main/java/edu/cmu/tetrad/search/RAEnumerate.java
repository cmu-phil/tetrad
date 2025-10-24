package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import java.util.*;
import java.util.concurrent.*;

/**
 * Enumerate up to K distinct adjustment sets using the fast single-set RA core.
 * - Runs one unadorned RA first (fast path), includes it if valid.
 * - Enumerates alternatives by branching on per-pivot forbid masks.
 * - Enforces GAC A2 via baseForbid (Forb_G(X,Y)); validates backdoor blocking for every set.
 * - Guards with per-attempt timeout and global deadline.
 */
public final class RAEnumerate {

    // ---------- Interfaces ----------

    public interface Policy {
        Policy withRandom(Random rnd);
        Policy withForbid(Set<Node> forbid);   // per-try forbid mask
        Set<Node> forbid();                    // current forbid mask
        List<Node> universe(Graph G, Node X, Node Y);
    }

    public interface OracleMemo { /* optional */ }
    public interface StateMemo  { /* optional */ }

    /** Single fast RA call. Should honor forbid mask and latent mask (provided here via adapter). */
    public interface RAOne {
        Set<Node> run(Graph G, Node X, Node Y,
                      Policy policy,
                      Exclusions exclusions,
                      OracleMemo oracleMemo,
                      StateMemo stateMemo);
    }

    /** true iff all backdoor branches X -> Y are blocked given S. */
    public interface BranchChecker {
        boolean isBlocked(Graph G, Node X, Node Y, Set<Node> S, OracleMemo oracleMemo);
    }

    /** Pre-checks (optional helpers used by findSetsWithStatus). */
    public interface GraphInspector {
        boolean hasPossiblyDirectedPath(Graph G, Node X, Node Y);
        boolean isAmenable(Graph G, Node X, Node Y);
    }

    // ---------- Graph-type helpers ----------

    /** One-time inference (do this once, not per attempt). */
    public static String inferGraphType(Graph G) {
        if (G.paths().isLegalPag())   return "PAG";
        if (G.paths().isLegalMag())   return "MAG";
        if (G.paths().isLegalMpdag()) return "MPDAG";
        return "DAG";
    }

    // ---------- Defaults: wrap your RecursiveAdjustment ----------

    /** Type-bound RA adapter that also carries a latent mask down to the RA core. */
    public static RAOne defaultRAOneFor(String graphType, Set<Node> latentMask) {
        final Set<Node> LM = (latentMask == null)
                ? Collections.emptySet()
                : Collections.unmodifiableSet(new LinkedHashSet<>(latentMask));

        return (G, X, Y, policy, exclusions, oracle, memo) -> {
            final int maxPathLen = -1;
            final Set<Node> seedZ = Collections.emptySet();
            final Set<Node> notFollowed = policy.forbid();

            try {
                Set<Node> S = RecursiveAdjustment.findAdjustmentSet(
                        G, graphType, X, Y, seedZ, notFollowed, maxPathLen, LM
                );
                if (S == null) return null;            // "no set" from RA
                if (exclusions.violates(S)) return null;
                return S; // Branch/minimality checks happen upstream
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return null;
            }
        };
    }

    public static BranchChecker defaultBranchCheckerFor(String graphType) {
        return (G, X, Y, S, oracle) -> {
            try {
                return RecursiveAdjustment.isAdjustmentSet(
                        G, graphType, X, Y, S, Collections.emptySet(), -1
                );
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return false;
            }
        };
    }

    // ---------- Exclusions / Indexer ----------

    /** Popcount-based "at-most" clauses: forbids S and any superset of S (skip empty). */
    public static final class Exclusions {
        private final List<BitSet> clauses = new ArrayList<>();
        private final NodeIndexer indexer;

        Exclusions(NodeIndexer indexer) { this.indexer = indexer; }

        public void addExcludeSupersets(Set<Node> S) {
            if (S.isEmpty()) return; // never exclude supersets of ∅
            BitSet m = new BitSet(indexer.size());
            for (Node n : S) m.set(indexer.id(n));
            clauses.add(m);
        }

        public boolean violates(Set<Node> S) {
            if (clauses.isEmpty()) return false;
            BitSet cand = new BitSet(indexer.size());
            for (Node n : S) cand.set(indexer.id(n));
            for (BitSet C : clauses) {
                BitSet tmp = (BitSet) cand.clone();
                tmp.and(C);
                if (tmp.cardinality() == C.cardinality()) return true;
            }
            return false;
        }
    }

    /** Dynamic indexer so we never NPE if policy under-covers nodes. */
    public static final class NodeIndexer {
        private final Map<Node,Integer> toId = new HashMap<>();
        private final List<Node> toNode;

        NodeIndexer(List<Node> universe) {
            this.toNode = new ArrayList<>(universe);
            for (int i = 0; i < toNode.size(); i++) toId.put(toNode.get(i), i);
        }
        int id(Node n) {
            Integer id = toId.get(n);
            if (id != null) return id;
            int newId = toNode.size();
            toId.put(n, newId);
            toNode.add(n);
            return newId;
        }
        int size() { return toNode.size(); }
    }

    private static BitSet bitsetOf(Set<Node> S, NodeIndexer idx) {
        BitSet bs = new BitSet(idx.size());
        for (Node n : S) bs.set(idx.id(n));
        return bs;
    }

    private static long mix(long seed, int t) {
        long x = seed ^ (0x9E3779B97F4A7C15L * (t + 1));
        x ^= (x >>> 33); x *= 0xff51afd7ed558ccdL;
        x ^= (x >>> 33); x *= 0xc4ceb9fe1a85ec53L;
        x ^= (x >>> 33);
        return x;
    }

    private static Set<Node> runWithTimeout(Callable<Set<Node>> task, long perAttemptMillis) {
        if (perAttemptMillis <= 0) {
            try { return task.call(); } catch (Exception e) { return null; }
        }
        ExecutorService ex = Executors.newSingleThreadExecutor();
        Future<Set<Node>> f = ex.submit(task);
        try {
            return f.get(perAttemptMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException te) {
            f.cancel(true); // interrupts RA core
            return null;
        } catch (Exception e) {
            return null;
        } finally {
            ex.shutdownNow();
        }
    }

    // ---------- Core enumeration ----------

    /**
     * Enumerate up to K sets. Guarantees for each accepted S:
     *   1) S ∩ baseForbid = ∅  (GAC A2)
     *   2) branchChecker.isBlocked(G,X,Y,S) == true (GAC A3)
     *   3) no superset exclusion added for ∅
     */
    public List<Set<Node>> enumerate(Graph G, Node X, Node Y,
                                     Policy basePolicy,
                                     RAOne raOne,
                                     BranchChecker branchChecker,
                                     OracleMemo oracleMemo,
                                     StateMemo stateMemo,
                                     int K,
                                     int maxTries,
                                     long seed,
                                     boolean enforceMinimality, // optional trim on top of RA
                                     long maxMillis,            // global deadline (0 disables)
                                     long perAttemptMillis,     // per-try timeout (0 disables)
                                     Set<Node> baseForbid) {    // Forb_G(X,Y)
        Objects.requireNonNull(G); Objects.requireNonNull(X); Objects.requireNonNull(Y);
        Objects.requireNonNull(basePolicy); Objects.requireNonNull(raOne); Objects.requireNonNull(branchChecker);

        final Set<Node> BASE_FORBID = (baseForbid == null)
                ? Collections.emptySet()
                : Collections.unmodifiableSet(new LinkedHashSet<>(baseForbid));

        final long deadlineNanos = (maxMillis > 0)
                ? (System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(maxMillis))
                : Long.MAX_VALUE;

        // Universe & bookkeeping
        List<Node> universe = basePolicy.universe(G, X, Y);
        NodeIndexer indexer   = new NodeIndexer(universe);
        Exclusions exclusions = new Exclusions(indexer);

        List<Set<Node>> results = new ArrayList<>(Math.min(K, 8));
        Set<BitSet> seenSets  = new HashSet<>();
        Set<BitSet> seenMasks = new HashSet<>();
        Map<BitSet, Boolean> adjMemo = new HashMap<>(); // for optional trim

        int triesUsed = 0;

        // ---------- 0) Single fast run (unadorned, but with BASE_FORBID) ----------
        if (System.nanoTime() <= deadlineNanos) {
            Random rnd0 = new Random(mix(seed, triesUsed));
            Policy p0 = basePolicy.withRandom(rnd0).withForbid(Collections.emptySet());
            Set<Node> s0 = runWithTimeout(() -> raOne.run(G, X, Y, p0, exclusions, oracleMemo, stateMemo),
                    /* perAttemptMillis */ 0L);
            triesUsed++;

            if (s0 != null) {
                // Enforce A2
                if (Collections.disjoint(s0, BASE_FORBID)) {
                    // Validate backdoor blocking for ALL sets (including empty)
                    boolean ok = branchChecker.isBlocked(G, X, Y, s0, oracleMemo);
                    if (ok && !exclusions.violates(s0)) {
                        BitSet key0 = bitsetOf(s0, indexer);
                        if (seenSets.add(key0)) {
                            results.add(Collections.unmodifiableSet(new LinkedHashSet<>(s0)));
                            if (!s0.isEmpty()) exclusions.addExcludeSupersets(s0);
                            if (K == 1) return results; // fast path
                        }
                    }
                }
            }
        }

        // ---------- 1) Seed mask queue ----------
        Deque<Set<Node>> maskQueue = new ArrayDeque<>();
        if (!results.isEmpty()) {
            for (Node z : orderPivots(G, X, Y, results.get(0))) {
                Set<Node> m = new LinkedHashSet<>();
                m.add(z);
                BitSet mk = bitsetOf(m, indexer);
                if (seenMasks.add(mk)) maskQueue.addLast(m);
            }
        } else {
            Set<Node> empty = Collections.emptySet();
            maskQueue.add(empty);
            seenMasks.add(bitsetOf(empty, indexer));
        }

        // ---------- 2) Enumerate with mask branching ----------
        while (results.size() < K && triesUsed < maxTries && !maskQueue.isEmpty()) {
            if (System.nanoTime() > deadlineNanos) break;

            Set<Node> forbid = maskQueue.removeFirst();
            Set<Node> effectiveForbid = new LinkedHashSet<>(BASE_FORBID);
            effectiveForbid.addAll(forbid);

            Random rnd = new Random(mix(seed, triesUsed));
//            Policy policyT = basePolicy.withRandom(rnd).withForbid(effectiveForbid);
            Policy policyT = basePolicy.withRandom(rnd).withForbid(forbid);

            Set<Node> S = runWithTimeout(() ->
                            raOne.run(G, X, Y, policyT, exclusions, oracleMemo, stateMemo),
                    perAttemptMillis);
            triesUsed++;

            if (S == null) continue;
            // Enforce A2
            if (!Collections.disjoint(S, BASE_FORBID)) continue;

            // Validate backdoor blocking (A3)
            boolean ok = branchChecker.isBlocked(G, X, Y, S, oracleMemo);
            if (!ok) continue;

            if (enforceMinimality) {
                S = tryTrimOnce(G, X, Y, S, branchChecker, oracleMemo, indexer, adjMemo);
                if (S == null) continue;
            }

            if (exclusions.violates(S)) continue;

            BitSet key = bitsetOf(S, indexer);
            if (seenSets.add(key)) {
                results.add(Collections.unmodifiableSet(new LinkedHashSet<>(S)));
                if (!S.isEmpty()) exclusions.addExcludeSupersets(S);

                // branch new masks (one pivot per element), ordered & deduped
                for (Node z : orderPivots(G, X, Y, S)) {
                    Set<Node> nextMask = new LinkedHashSet<>(forbid);
                    nextMask.add(z);
                    BitSet mk = bitsetOf(nextMask, indexer);
                    if (seenMasks.add(mk)) maskQueue.addLast(nextMask);
                }
            }
        }

        return results;
    }

    /** One-pass O(|S|) trim using memoized branch checks (keep enforceMinimality=false unless needed). */
    private Set<Node> tryTrimOnce(Graph G, Node X, Node Y, Set<Node> S,
                                  BranchChecker branchChecker,
                                  OracleMemo oracleMemo,
                                  NodeIndexer idx,
                                  Map<BitSet, Boolean> adjMemo) {
        LinkedHashSet<Node> cur = new LinkedHashSet<>(S);
        for (Node z : new ArrayList<>(cur)) {
            cur.remove(z);
            BitSet key = bitsetOf(cur, idx);
            Boolean ok = adjMemo.get(key);
            if (ok == null) {
                ok = branchChecker.isBlocked(G, X, Y, cur, oracleMemo);
                adjMemo.put((BitSet) key.clone(), ok);
            }
            if (!ok) cur.add(z); // z is necessary
        }
        return cur;
    }

    private static List<Node> orderPivots(Graph G, Node X, Node Y, Set<Node> S) {
        Map<Node,Integer> deg = new HashMap<>();
        for (Node n : G.getNodes()) deg.put(n, G.getAdjacentNodes(n).size());
        List<Node> pivots = new ArrayList<>(S);
        pivots.sort((a,b) -> Integer.compare(deg.get(b), deg.get(a))); // high degree first
        return pivots;
    }

    // ---------- Optional status helpers (unchanged API) ----------

    public enum AdjKind {
        NO_VALUE_RETURNED,
        NO_CAUSAL_PATH,
        NOT_AMENABLE,
        EMPTY_SET_SUFFICES,
        NONEMPTY_SETS,
        TIME_BUDGET_EXHAUSTED,
        SEARCH_TRUNCATED_BY_PATH_LIMIT
    }

    public static final class AdjSummary {
        public final AdjKind kind;
        public final List<Set<Node>> sets;
        public AdjSummary(AdjKind kind, List<Set<Node>> sets) {
            this.kind = kind;
            this.sets = (sets == null) ? List.of() : Collections.unmodifiableList(sets);
        }
        @Override public String toString() {
            switch (kind) {
                case NONEMPTY_SETS -> {
                    StringBuilder out = new StringBuilder();
                    for (int i = 0; i < sets.size(); i++) {
                        out.append("Adjustment Set ").append(i + 1).append(": ").append(sets.get(i)).append('\n');
                    }
                    if (out.length() > 0) out.setLength(out.length() - 1);
                    return out.toString();
                }
                case EMPTY_SET_SUFFICES -> { return "Empty set suffices; no adjustment needed."; }
                case NO_CAUSAL_PATH      -> { return "There are no possibly directed causal paths."; }
                case NOT_AMENABLE        -> { return "There are possibly directed paths, but amenability fails."; }
                case SEARCH_TRUNCATED_BY_PATH_LIMIT -> { return "The search exceeded the path-length limit."; }
                case TIME_BUDGET_EXHAUSTED -> { return "The search timed out."; }
                case NO_VALUE_RETURNED  -> { return "The search returned with no value."; }
                default -> throw new IllegalArgumentException("Unsupported status: " + kind);
            }
        }
    }

    public static GraphInspector defaultInspector(String graphType) {
        return new GraphInspector() {
            @Override
            public boolean hasPossiblyDirectedPath(Graph G, Node X, Node Y) {
                try {
                    return G.paths().existsSemiDirectedPath(X, Y); // replace if you have a better check
                } catch (Throwable t) {
                    return true;
                }
            }
            @Override
            public boolean isAmenable(Graph G, Node X, Node Y) {
                try {
                    if (!hasPossiblyDirectedPath(G, X, Y)) return false;
                    // plug exact amenability if available; keep permissive otherwise
                    return true;
                } catch (Throwable t) {
                    return true;
                }
            }
        };
    }

    // ---------- Builder ----------

    public static final class Builder {
        private Policy policy;
        private RAOne raOne;
        private BranchChecker branchChecker;
        private OracleMemo oracleMemo;
        private StateMemo stateMemo;
        private int K = 2;
        private int maxTries = 64;
        private long seed = ThreadLocalRandom.current().nextLong();
        private boolean enforceMinimality = false;
        private long maxMillis = 0L;
        private long perAttemptMillis = 250L;
        private String graphType;                 // PAG/MAG/MPDAG/DAG
        private Set<Node> baseForbid = Set.of(); // Forb_G(X,Y)
        private Set<Node> latentMask = Set.of(); // Latents / disallowed adjusters

        public Builder policy(Policy p) { this.policy = p; return this; }
        public Builder raOne(RAOne r) { this.raOne = r; return this; }
        public Builder branchChecker(BranchChecker b) { this.branchChecker = b; return this; }
        public Builder oracleMemo(OracleMemo o) { this.oracleMemo = o; return this; }
        public Builder stateMemo(StateMemo m) { this.stateMemo = m; return this; }
        public Builder K(int k) { this.K = k; return this; }
        public Builder maxTries(int mt) { this.maxTries = mt; return this; }
        public Builder seed(long s) { this.seed = s; return this; }
        public Builder enforceMinimality(boolean b) { this.enforceMinimality = b; return this; }
        public Builder maxMillis(long ms) { this.maxMillis = ms; return this; }
        public Builder perAttemptMillis(long ms) { this.perAttemptMillis = ms; return this; }
        public Builder graphType(String gt) { this.graphType = gt; return this; }
        public Builder baseForbid(Set<Node> f) {
            this.baseForbid = (f == null) ? Set.of() : new LinkedHashSet<>(f); return this;
        }
        public Builder latentMask(Set<Node> m) {
            this.latentMask = (m == null) ? Set.of() : new LinkedHashSet<>(m); return this;
        }

        public List<Set<Node>> run(Graph G, Node X, Node Y) {
            Objects.requireNonNull(policy, "policy must be set");
            RAEnumerate enumr = new RAEnumerate();

            String gt = (this.graphType != null) ? this.graphType : RAEnumerate.inferGraphType(G);
            RAOne r1 = (this.raOne != null) ? this.raOne : RAEnumerate.defaultRAOneFor(gt, this.latentMask);
            BranchChecker bc = (this.branchChecker != null) ? this.branchChecker : RAEnumerate.defaultBranchCheckerFor(gt);

            return enumr.enumerate(G, X, Y, policy, r1, bc, oracleMemo, stateMemo,
                    K, maxTries, seed, enforceMinimality, maxMillis, perAttemptMillis, baseForbid);
        }
    }
}