package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Enumerate up to K distinct minimal adjustment sets using the fast single-set RA core.
 * - Reuses caches (oracle/memo) across runs.
 * - After each solution S, adds an "at-most" clause that blocks S and all of its supersets.
 * - Optionally perturbs priority order per try to diversify solutions.
 *
 * INTEGRATION POINTS:
 *  1) RA single-run:   RecursiveAdjustment.run(...), must respect exclusions & caches.
 *  2) Branch checker:  BranchChecker.isBlocked(G, X, Y, S) -> boolean
 *  3) Policy priority: Policy/Ordering hook; we pass a Random to let you shuffle/tie-break.
 */
public final class RAEnumerate {

    /** Policy/knobs passed to RA; adapt to your existing policy object. */
    public interface Policy {
        /** Candidate filtering/ordering is inside your RA; we only hand you a Random to perturb priorities. */
        Policy withRandom(Random rnd);
        /** Universe of nodes that RA may consider (for indexing BitSets). */
        List<Node> universe(Graph G, Node X, Node Y);
    }

    /** Lightweight oracle memo—adapt to your CI/m-sep caching. */
    public interface OracleMemo {
        // define whatever your RA core expects; placeholder here
    }

    /** DFS/state memo across runs—adapt to your RA core. */
    public interface StateMemo {
        // define whatever your RA core expects; placeholder here
    }

    /** Your single-set RA call. Must honor exclusions, use caches, and return null on fail. */
    public interface RAOne {
        Set<Node> run(Graph G, Node X, Node Y,
                      Policy policy,
                      Exclusions exclusions,
                      OracleMemo oracleMemo,
                      StateMemo stateMemo);
    }

    /** Branch checker using your DFS_Open/BranchCheck; true if all backdoor branches are blocked. */
    public interface BranchChecker {
        boolean isBlocked(Graph G, Node X, Node Y, Set<Node> S, OracleMemo oracleMemo);
    }

    /** Popcount-based "at-most" clauses: forbid picking all members of a previously found S (blocks supersets too). */
    public static final class Exclusions {
        private final List<BitSet> clauses = new ArrayList<>();
        private final NodeIndexer indexer;

        Exclusions(NodeIndexer indexer) { this.indexer = indexer; }

        public void addExcludeSupersets(Set<Node> S) {
            BitSet m = new BitSet(indexer.size());
            for (Node n : S) m.set(indexer.id(n));
            clauses.add(m);
        }

        /** Return true if candidate S violates any at-most clause (i.e., contains an entire clause). */
        public boolean violates(Set<Node> S) {
            if (clauses.isEmpty()) return false;
            BitSet cand = new BitSet(indexer.size());
            for (Node n : S) cand.set(indexer.id(n));
            for (BitSet C : clauses) {
                // If (S ∩ C) has popcount == |C|, then S includes all of C → violation
                BitSet tmp = (BitSet) cand.clone();
                tmp.and(C);
                if (tmp.cardinality() == C.cardinality()) return true;
            }
            return false;
        }
    }

    /** Stable Node↔index mapping for BitSet ops. */
    public static final class NodeIndexer {
        private final Map<Node,Integer> toId = new HashMap<>();
        private final List<Node> toNode;

        NodeIndexer(List<Node> universe) {
            this.toNode = new ArrayList<>(universe);
            for (int i = 0; i < toNode.size(); i++) toId.put(toNode.get(i), i);
        }
        int id(Node n) { return toId.get(n); }
        Node node(int i) { return toNode.get(i); }
        int size() { return toNode.size(); }
    }

    /** Main API: enumerate up to K distinct minimal sets. */
    public List<Set<Node>> enumerate(Graph G, Node X, Node Y,
                                     Policy basePolicy,
                                     RAOne raOne,
                                     BranchChecker branchChecker,
                                     OracleMemo oracleMemo,
                                     StateMemo stateMemo,
                                     int K,
                                     int maxTries,
                                     long seed) {
        Objects.requireNonNull(G); Objects.requireNonNull(X); Objects.requireNonNull(Y);
        List<Node> universe = basePolicy.universe(G, X, Y);
        NodeIndexer indexer = new NodeIndexer(universe);
        Exclusions exclusions = new Exclusions(indexer);

        List<Set<Node>> results = new ArrayList<>(K);
        Set<BitSet> seen = new HashSet<>(); // dedup by BitSet

        int t = 0;
        while (results.size() < K && t < maxTries) {
            Random rnd = new Random(mix(seed, t));
            Policy policyT = basePolicy.withRandom(rnd);

            // INTEGRATE (1): single fast RA call that respects exclusions and uses the shared caches
            Set<Node> S = raOne.run(G, X, Y, policyT, exclusions, oracleMemo, stateMemo);
            t++;

            if (S == null || S.isEmpty()) continue;
            S = makeMinimal(G, X, Y, S, branchChecker, oracleMemo);   // ensure minimality

            if (exclusions.violates(S)) continue; // safeguard (shouldn’t happen if RA respects clauses)

            BitSet key = bitsetOf(S, indexer);
            if (seen.add(key)) {
                results.add(Collections.unmodifiableSet(new LinkedHashSet<>(S)));
                exclusions.addExcludeSupersets(S); // block this S and all supersets
            }
        }
        return results;
    }

    /** Greedy minimality: try removing any z ∈ S whose removal keeps all backdoor branches blocked. */
    private Set<Node> makeMinimal(Graph G, Node X, Node Y, Set<Node> S,
                                  BranchChecker branchChecker,
                                  OracleMemo oracleMemo) {
        // Try removing in reverse insertion/heuristic order if you keep that; here we just use a stable pass.
        List<Node> order = new ArrayList<>(S);
        // Heuristic: try last-added or highest-coverage first if you track it; we just keep the given order.
        boolean changed;
        Set<Node> cur = new LinkedHashSet<>(S);
        do {
            changed = false;
            for (Iterator<Node> it = order.iterator(); it.hasNext();) {
                Node z = it.next();
                if (!cur.contains(z)) continue;
                cur.remove(z);
                // INTEGRATE (2): branch check = true if all backdoor branches are blocked given cur
                boolean stillBlocked = branchChecker.isBlocked(G, X, Y, cur, oracleMemo);
                if (!stillBlocked) {
                    cur.add(z); // need z
                } else {
                    changed = true;
                    it.remove(); // do not consider z again
                }
            }
        } while (changed);
        return cur;
    }

    private static BitSet bitsetOf(Set<Node> S, NodeIndexer idx) {
        BitSet bs = new BitSet(idx.size());
        for (Node n : S) bs.set(idx.id(n));
        return bs;
    }

    /** Cheap seed mixing to diversify per-try Random without new SplittableRandom. */
    private static long mix(long seed, int t) {
        long x = seed ^ (0x9E3779B97F4A7C15L * (t + 1));
        x ^= (x >>> 33); x *= 0xff51afd7ed558ccdL;
        x ^= (x >>> 33); x *= 0xc4ceb9fe1a85ec53L;
        x ^= (x >>> 33);
        return x;
    }

    // ---------- Convenience builder ----------

    public static final class Builder {
        private Policy policy;
        private RAOne raOne;
        private BranchChecker branchChecker;
        private OracleMemo oracleMemo;
        private StateMemo stateMemo;
        private int K = 2;
        private int maxTries = 64;
        private long seed = ThreadLocalRandom.current().nextLong();

        public Builder policy(Policy p) { this.policy = p; return this; }
        public Builder raOne(RAOne r) { this.raOne = r; return this; }
        public Builder branchChecker(BranchChecker b) { this.branchChecker = b; return this; }
        public Builder oracleMemo(OracleMemo o) { this.oracleMemo = o; return this; }
        public Builder stateMemo(StateMemo m) { this.stateMemo = m; return this; }
        public Builder K(int k) { this.K = k; return this; }
        public Builder maxTries(int mt) { this.maxTries = mt; return this; }
        public Builder seed(long s) { this.seed = s; return this; }

        public List<Set<Node>> run(Graph G, Node X, Node Y) {
            RAEnumerate enumr = new RAEnumerate();
            return enumr.enumerate(G, X, Y, policy, raOne, branchChecker, oracleMemo, stateMemo, K, maxTries, seed);
        }
    }
}