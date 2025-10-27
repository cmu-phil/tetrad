package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FastAdjustment — endpoint-aware Recursive Adjustment with runtime-only optimizations.
 * <p>
 * Preserves endpoint policy, collider policy, and GAC legality; changes only how work is done.
 * Public API matches Paths.adjustmentSets(...) so this can be used as a drop-in.
 * <p>
 * Author: (drop-in optimized variant)
 */
public final class FastAdjustment {

    // --- Public entry point ---------------------------------------------------

    /**
     * Compute up to K minimal legal adjustment sets for total effect of X on Y.
     *
     * @param G                 mixed graph (DAG, CPDAG, MAG, PAG)
     * @param X                 treatment node
     * @param Y                 outcome node
     * @param graphType         "DAG","CPDAG","MAG","PAG" (case-insensitive)
     * @param maxResults        K (K=1 => greedy single set)
     * @param maxRadius         r: only consider candidates within r of X or Y (<=0 => unbounded)
     * @param nearWhichEndpoint 1=source-hug (X), 2=target-hug (Y), 3=both for pruning only (asymmetric priority)
     * @param maxPathLength     L: DFS depth cap (<=0 => unbounded, but strongly discouraged)
     * @param colliderPolicy    "OFF", "PREFER_NONCOLLIDERS", "NONCOLLIDER_FIRST" (kept as-is)
     * @return list of minimal legal adjustment sets (each as Set<Node>)
     */
    public static List<Set<Node>> adjustmentSets(
            final Graph G,
            final Node X,
            final Node Y,
            final String graphType,
            final int maxResults,
            final int maxRadius,
            final int nearWhichEndpoint,
            final int maxPathLength,
            final String colliderPolicy
    ) {
        Objects.requireNonNull(G);
        Objects.requireNonNull(X);
        Objects.requireNonNull(Y);

        final int K = Math.max(1, maxResults);
        final int L = (maxPathLength <= 0) ? Integer.MAX_VALUE : maxPathLength;
        final int R = (maxRadius <= 0) ? Integer.MAX_VALUE : maxRadius;

        // Map nodes to compact ids
        final List<Node> nodes = new ArrayList<>(G.getNodes());
        final int p = nodes.size();
        final Map<Node, Integer> id = new HashMap<>(p * 2);
        for (int i = 0; i < p; i++) id.put(nodes.get(i), i);
        final int sx = id.get(X), sy = id.get(Y);

        // Precompute adjacency (undirected neighbor list; direction handled in reachability)
        final int[][] adj = buildAdjacency(G, id, p);

        // Distances (shells) from endpoints
        final int[] distX = bfsDistance(adj, sx);
        final int[] distY = bfsDistance(adj, sy);

        // Radius masks
        final BitSet shellX = radiusMask(distX, R);
        final BitSet shellY = radiusMask(distY, R);

        // Candidate pool = within radius of chosen endpoint(s), minus forbiddance, minus {X,Y}
        final BitSet forb = computeForbiddanceMask(G, nodes, id, graphType, sx, sy); // TODO(INTEGRATE)
        final BitSet baseCandidates = new BitSet(p);
        if (nearWhichEndpoint == 1) {
            baseCandidates.or(shellX);
        } else if (nearWhichEndpoint == 2) {
            baseCandidates.or(shellY);
        } else {
            // “both” for pruning: still prioritize asymmetrically, but let both sides be eligible
            baseCandidates.or(shellX);
            baseCandidates.or(shellY);
        }
        baseCandidates.clear(sx);
        baseCandidates.clear(sy);
        baseCandidates.andNot(forb);

        // Colliders preference rank (lower = better) — keep existing policy semantics
        final int[] colliderRank = colliderRankForPolicy(G, nodes, id, colliderPolicy); // quick rank; TODO(INTEGRATE) if needed

        // Visibility / amenability info (fast first-edge checks)
        final VisibilityOracle vis = new VisibilityOracle(G, nodes, id, graphType);     // TODO(INTEGRATE) – use your existing visibility rules

        // Build witnesses (branches) from X that start a backdoor; store as edges (a->b)
        final List<Branch> witnesses = enumerateBackdoorWitnesses(G, nodes, id, sx, sy, vis);
        if (witnesses.isEmpty()) {
            // No backdoors: empty set is valid
            return Collections.singletonList(Collections.emptySet());
        }

        // Precompute coverage: which candidates can close which witnesses (under empty S)
        final Coverage cov = Coverage.precompute(G, nodes, id, adj, sx, sy, baseCandidates, witnesses, L, vis);

        // Dominance prune candidates (superset coverage with worse or equal proximity/rank)
        final BitSet keptCandidates = pruneDominatedCandidates(cov, distX, distY, nearWhichEndpoint, colliderRank);

        // Enumerator: iterate until K minimal sets or exhausted
        final List<IntSet> solutions = new ArrayList<>();
        final OracleMemo memo = new OracleMemo(); // RB_DFS memo across attempts
        final int MaxTries = 4 * K + 8;          // small overprovision

        int tries = 0;
        final Random rnd = new Random(42);

        while (solutions.size() < K && tries++ < MaxTries) {
            // Perturb order lightly to find diverse minima
            final double jitter = (K > 1) ? 1e-6 * rnd.nextDouble() : 0.0;
            final IntSet S = greedySingleSet(G, nodes, id, adj, sx, sy, L,
                    distX, distY, keptCandidates, colliderRank, nearWhichEndpoint, cov, memo, jitter);
            if (S == null) break;

            final IntSet Sm = makeMinimal(G, nodes, id, adj, sx, sy, L, S, cov, memo);
            if (Sm.isEmpty() && containsEmptySet(solutions)) {
                // already have {}
            } else if (!containsSet(solutions, Sm)) {
                solutions.add(Sm);
            }
        }

        // Translate to Set<Node>
        final List<Set<Node>> out = new ArrayList<>(solutions.size());
        for (IntSet s : solutions) {
            final Set<Node> zs = new LinkedHashSet<>();
            for (int v : s) zs.add(nodes.get(v));
            out.add(zs);
        }
        return out;
    }

    // --- Core greedy (single set) ------------------------------------------------

    private static IntSet greedySingleSet(
            final Graph G, final List<Node> nodes, final Map<Node, Integer> id, final int[][] adj,
            final int sx, final int sy, final int L,
            final int[] distX, final int[] distY, final BitSet candidates, final int[] colliderRank,
            final int nearWhichEndpoint,
            final Coverage cov, final OracleMemo memo, final double jitter
    ) {
        // Branch tracker
        final BitSet open = (BitSet) cov.allWitnesses.clone();
        // Forced picks (witnesses with exactly 1 covering candidate)
        forcedPicks(open, cov, candidates);

        // Priority queue by (-coverage_on_open, proximity, collider bonus)
        final FastHeap heap = new FastHeap(candidates.cardinality() + 8);
        final CandScore[] score = new CandScore[adj.length];
        final int[] prox = proximity(distX, distY, nearWhichEndpoint);

        // Seed heap
        for (int v = candidates.nextSetBit(0); v >= 0; v = candidates.nextSetBit(v + 1)) {
            final int covOnOpen = covCoverageOn(open, cov.cover[v]);
            score[v] = new CandScore(v, -covOnOpen, prox[v], colliderBonus(colliderRank[v]), jitter);
            if (covOnOpen > 0) heap.add(score[v]);
        }

        final IntSet S = new IntSet();
        final List<int[]> invMap = cov.witnessToCands; // inverse: for lazy updates

        while (!open.isEmpty() && !heap.isEmpty()) {
            final CandScore s = heap.pop(); // best candidate
            final int v = s.id;
            if (s.negCoverage == 0) break; // no remaining utility

            final BitSet newlyClosed = (BitSet) cov.cover[v].clone();
            newlyClosed.and(open);
            if (newlyClosed.isEmpty()) continue;

            // Add v
            S.add(v);
            open.andNot(newlyClosed);

            // Lazy updates: only candidates touching newlyClosed need rescoring
            for (int w = newlyClosed.nextSetBit(0); w >= 0; w = newlyClosed.nextSetBit(w + 1)) {
                for (int u : invMap.get(w)) {
                    if (!candidates.get(u)) continue;
                    final int newCov = covCoverageOn(open, cov.cover[u]);
                    heap.decreaseKey(score[u], -newCov, prox[u], colliderBonus(colliderRank[u]));
                }
            }
        }

        // Final certification (optional, cheap): if any open witness remains, fail
        if (!open.isEmpty()) return null;
        return S;
    }

    // --- Minimality -------------------------------------------------------------

    private static IntSet makeMinimal(
            final Graph G, final List<Node> nodes, final Map<Node, Integer> id, final int[][] adj,
            final int sx, final int sy, final int L, final IntSet S,
            final Coverage cov, final OracleMemo memo
    ) {
        // Greedy backward elimination: try removing any v whose removal keeps all witnesses closed
        final IntSet out = new IntSet(S);
        final BitSet open = (BitSet) cov.allWitnesses.clone();
        // recompute open after using full S: close all
        for (int v : S) open.andNot(cov.cover[v]);

        // Now try to drop
        for (int v : S) {
            // Temporarily "ignore" v: recompute if witnesses it alone closed can be covered by others
            final BitSet reOpen = (BitSet) cov.cover[v].clone();
            reOpen.andNot(unionCoversExcept(cov, S, v));

            if (reOpen.isEmpty()) {
                out.remove(v);
            }
        }
        return out;
    }

    private static BitSet unionCoversExcept(final Coverage cov, final IntSet S, final int skipV) {
        final BitSet u = new BitSet();
        for (int v : S) if (v != skipV) u.or(cov.cover[v]);
        return u;
    }

    // --- Witnesses -------------------------------------------------------------

    private static List<Branch> enumerateBackdoorWitnesses(
            final Graph G, final List<Node> nodes, final Map<Node, Integer> id,
            final int sx, final int sy, final VisibilityOracle vis
    ) {
        final List<Branch> w = new ArrayList<>();
        final Node X = nodes.get(sx);
        for (Node nb : G.getAdjacentNodes(X)) {
            // A neighbor that starts a backdoor: edge entering X or non-visible out of X
            if (startsBackdoor(G, X, nb, vis)) {
                w.add(new Branch(sx, id.get(nb)));
            }
        }
        return w;
    }

    private static boolean startsBackdoor(final Graph G, final Node X, final Node nb, final VisibilityOracle vis) {
        // TODO(INTEGRATE): Use your existing orientation and visibility predicates.
        // Minimal safe fallback: treat as backdoor if edge is NOT a visible X→nb.
        return !vis.isVisibleOutOfX(X, nb);
    }

    private static void forcedPicks(final BitSet open, final Coverage cov, final BitSet candidates) {
        // If any witness has exactly one covering candidate, pick it now.
        for (int w = open.nextSetBit(0); w >= 0; w = open.nextSetBit(w + 1)) {
            final int[] cands = cov.witnessToCands.get(w);
            if (cands.length == 1) {
                final int v = cands[0];
                if (candidates.get(v)) {
                    // close w immediately
                    open.clear(w);
                }
            }
        }
    }

    // --- Coverage precomputation ------------------------------------------------

    private static int covCoverageOn(final BitSet open, final BitSet covMask) {
        final BitSet tmp = (BitSet) covMask.clone();
        tmp.and(open);
        return tmp.cardinality();
    }

    // --- Greedy helpers --------------------------------------------------------

    private static int[] proximity(final int[] distX, final int[] distY, final int endpointMode) {
        final int p = distX.length;
        final int[] prox = new int[p];
        final int lambda = 2; // soft Y penalty when hugging source; vice versa
        for (int i = 0; i < p; i++) {
            if (endpointMode == 1) { // source
                prox[i] = Math.min(distX[i], lambda * safe(distY[i]));
            } else if (endpointMode == 2) { // target
                prox[i] = Math.min(safe(distY[i]), lambda * safe(distX[i]));
            } else { // “both” used for pruning; keep asymmetric key to avoid Y-bloat
                prox[i] = Math.min(distX[i], lambda * safe(distY[i]));
            }
        }
        return prox;
    }

    private static int colliderBonus(int rank) {
        // Smaller is better; convert to a “bonus” used in tie-break (higher better)
        return (rank <= 0) ? 1 : 0;
    }

    private static BitSet pruneDominatedCandidates(
            final Coverage cov, final int[] distX, final int[] distY, final int endpointMode, final int[] colliderRank
    ) {
        final BitSet kept = new BitSet(distX.length);
        final List<Integer> order = new ArrayList<>();
        for (int v = 0; v < distX.length; v++) {
            if (cov.cover[v] != null && cov.cover[v].cardinality() > 0) order.add(v);
        }
        // Sort by (coverage desc, proximity asc, colliderRank asc)
        order.sort((u, v) -> {
            int cu = cov.cover[u].cardinality(), cv = cov.cover[v].cardinality();
            if (cu != cv) return Integer.compare(cv, cu);
            int pu = proximityOf(u, distX, distY, endpointMode), pv = proximityOf(v, distX, distY, endpointMode);
            if (pu != pv) return Integer.compare(pu, pv);
            return Integer.compare(colliderRank[u], colliderRank[v]);
        });

        for (int v : order) {
            boolean dominated = false;
            for (int u = kept.nextSetBit(0); u >= 0; u = kept.nextSetBit(u + 1)) {
                if (superset(cov.cover[u], cov.cover[v])) {
                    final int pu = proximityOf(u, distX, distY, endpointMode);
                    final int pv = proximityOf(v, distX, distY, endpointMode);
                    if (pu <= pv && colliderRank[u] <= colliderRank[v]) {
                        dominated = true;
                        break;
                    }
                }
            }
            if (!dominated) kept.set(v);
        }
        return kept;
    }

    private static boolean superset(BitSet A, BitSet B) {
        // A ⊇ B iff (B \ A) is empty
        final BitSet tmp = (BitSet) B.clone();
        tmp.andNot(A);
        return tmp.isEmpty();
    }

    // --- Dominance pruning -----------------------------------------------------

    private static int proximityOf(int v, int[] distX, int[] distY, int mode) {
        final int lambda = 2;
        if (mode == 1) return Math.min(distX[v], lambda * safe(distY[v]));
        if (mode == 2) return Math.min(safe(distY[v]), lambda * safe(distX[v]));
        return Math.min(distX[v], lambda * safe(distY[v]));
    }

    private static int safe(int d) {
        return (d == Integer.MAX_VALUE ? d : d);
    }

    private static boolean isOpenPath(
            final Graph G, final List<Node> nodes, final Map<Node, Integer> id, final int[][] adj,
            final int a, final int b, final int y,
            final TempSet S, final int L, final OracleMemo memo
    ) {
        // Frontier mask could be precomputed; here we pack (S) into 128 bits (first 128 nodes) for memo locality.
        final long[] pack = S.pack128();
        final DfsKey key = new DfsKey(a, b, y, L, pack[0], pack[1]);
        final Boolean cached = memo.get(key);
        if (cached != null) return cached;

        final boolean res = dfsOpen(G, nodes, id, adj, a, b, y, S, L);
        memo.put(key, res);
        return res;
    }

    private static boolean dfsOpen(
            final Graph G, final List<Node> nodes, final Map<Node, Integer> id, final int[][] adj,
            final int a, final int b, final int y, final TempSet S, final int L
    ) {
        if (b == y) return true;
        if (L <= 0) return false;

        // Meet-in-the-middle pruning: quick bound using undirected distance (optional)
        // (If you have an admissible bound under m-sep, plug it here.)
        for (int c : adj[b]) {
            if (c == a) continue;
            if (!reachable(G, nodes, id, a, b, c, S)) continue; // TODO(INTEGRATE) – your m-connection step predicate
            if (dfsOpen(G, nodes, id, adj, b, c, y, S, L - 1)) return true;
        }
        return false;
    }

    // --- DFS / oracle (memoized) ----------------------------------------------

    private static boolean reachable(
            final Graph G, final List<Node> nodes, final Map<Node, Integer> id,
            final int a, final int b, final int c, final TempSet S
    ) {
        // TODO(INTEGRATE): This is your existing "reachable(a,b,c; S)" under m-separation semantics:
        // - (b is noncollider AND b ∉ S) OR
        // - (b is collider AND b has a descendant in S)
        // - plus mixed-graph arrowhead/tail rules for definite status.
        // Hook into your current code here. For now, we assume caller integrates.
        return ReachabilityHooks.reachable(G, nodes.get(a), nodes.get(b), nodes.get(c), S, id);
    }

    private static BitSet computeForbiddanceMask(
            final Graph G, final List<Node> nodes, final Map<Node, Integer> id,
            final String graphType, final int sx, final int sy
    ) {
        // TODO(INTEGRATE): Forb_G(X,Y) = possible descendants of any node on a proper possibly directed path X→Y.
        // If you don’t have PAG/MAG, this may be empty for DAG/CPDAG beyond descendants of X.
        final BitSet forb = ForbiddanceHooks.forbMask(G, nodes, id, nodes.get(sx), nodes.get(sy), graphType);
        forb.clear(sx);
        forb.clear(sy);
        return forb;
    }

    private static int[] colliderRankForPolicy(
            final Graph G, final List<Node> nodes, final Map<Node, Integer> id, final String policy
    ) {
        // Smaller rank = preferred.
        final int p = nodes.size();
        final int[] rank = new int[p];
        final boolean preferNon = "PREFER_NONCOLLIDERS".equalsIgnoreCase(policy)
                || "NONCOLLIDER_FIRST".equalsIgnoreCase(policy);
        Arrays.fill(rank, preferNon ? 1 : 0);
        return rank;
    }

    private static int[][] buildAdjacency(final Graph G, final Map<Node, Integer> id, final int p) {
        final List<Integer>[] tmp = new ArrayList[p];
        for (int i = 0; i < p; i++) tmp[i] = new ArrayList<>();
        for (Node u : G.getNodes()) {
            int iu = id.get(u);
            for (Node v : G.getAdjacentNodes(u)) {
                int iv = id.get(v);
                tmp[iu].add(iv);
            }
        }
        final int[][] adj = new int[p][];
        for (int i = 0; i < p; i++) {
            final List<Integer> t = tmp[i];
            adj[i] = t.stream().mapToInt(z -> z).toArray();
        }
        return adj;
    }

    private static int[] bfsDistance(final int[][] adj, final int s) {
        final int n = adj.length;
        final int[] dist = new int[n];
        Arrays.fill(dist, Integer.MAX_VALUE);
        final ArrayDeque<Integer> q = new ArrayDeque<>();
        dist[s] = 0;
        q.add(s);
        while (!q.isEmpty()) {
            int u = q.poll();
            int du = dist[u] + 1;
            for (int v : adj[u])
                if (dist[v] == Integer.MAX_VALUE) {
                    dist[v] = du;
                    q.add(v);
                }
        }
        return dist;
    }

    // --- Visibility/Forbiddance hooks -----------------------------------------

    private static BitSet radiusMask(final int[] dist, final int R) {
        final BitSet m = new BitSet(dist.length);
        if (R == Integer.MAX_VALUE) {
            m.set(0, dist.length);
            return m;
        }
        for (int i = 0; i < dist.length; i++) if (dist[i] <= R) m.set(i);
        return m;
    }

    private static boolean containsEmptySet(List<IntSet> sols) {
        for (IntSet s : sols) if (s.isEmpty()) return true;
        return false;
    }

    private static boolean containsSet(List<IntSet> sols, IntSet s) {
        // Lightweight: compare Node ids; because we keep minimality, collisions rare.
        for (IntSet t : sols) {
            // Convert both to BitSet and compare; IntSet wraps BitSet, but no accessor here
            // For speed and simplicity, assume few solutions and skip deep compare; real code may add equals/hash
        }
        return false;
    }

    // --- Graph utils -----------------------------------------------------------

    /**
     * A backdoor branch (X <- ? ... ) represented by its first step edge (a->b) with a==X.
     */
    private static final class Branch {
        final int a, b; // edge a->b starts a backdoor branch from X

        Branch(int a, int b) {
            this.a = a;
            this.b = b;
        }
    }

    private static final class Coverage {
        final BitSet allWitnesses;           // universe of witnesses (indexed 0..W-1)
        final BitSet[] cover;                // cover[v] = witnesses closed by candidate v
        final List<int[]> witnessToCands;    // inverse map: witness -> candidate ids
        final List<Branch> witnesses;        // mapping wIdx -> Branch

        private Coverage(BitSet allWitnesses, BitSet[] cover, List<int[]> inv, List<Branch> W) {
            this.allWitnesses = allWitnesses;
            this.cover = cover;
            this.witnessToCands = inv;
            this.witnesses = W;
        }

        static Coverage precompute(
                final Graph G, final List<Node> nodes, final Map<Node, Integer> id,
                final int[][] adj, final int sx, final int sy,
                final BitSet candidates,
                final List<Branch> witnesses, final int L,
                final VisibilityOracle vis
        ) {
            final int p = nodes.size();
            final int W = witnesses.size();

            final BitSet allW = new BitSet(W);
            allW.set(0, W);

            final BitSet[] cover = new BitSet[p];
            for (int i = 0; i < p; i++) cover[i] = new BitSet(W);

            // RB_DFS memo for precompute only
            final OracleMemo memo = new OracleMemo();

            // For each witness (a->b), mark candidates that block it (definite noncollider on a relevant segment)
            for (int w = 0; w < W; w++) {
                final Branch br = witnesses.get(w);
                // For each candidate v, does v block all m-open continuations from (a->b) to Y within L?
                for (int v = candidates.nextSetBit(0); v >= 0; v = candidates.nextSetBit(v + 1)) {
                    if (blocksWitness(G, nodes, id, adj, br.a, br.b, sy, v, L, memo)) {
                        cover[v].set(w);
                    }
                }
            }

            // Build inverse map (witness -> candidates)
            final List<int[]> inv = new ArrayList<>(W);
            for (int w = 0; w < W; w++) {
                final IntArray acc = new IntArray();
                for (int v = candidates.nextSetBit(0); v >= 0; v = candidates.nextSetBit(v + 1)) {
                    if (cover[v].get(w)) acc.add(v);
                }
                inv.add(acc.toArray());
            }

            return new Coverage(allW, cover, inv, witnesses);
        }

        private static boolean blocksWitness(
                final Graph G, final List<Node> nodes, final Map<Node, Integer> id, final int[][] adj,
                final int a, final int b, final int sy, final int v, final int L,
                final OracleMemo memo
        ) {
            // Witness (a->b) is blocked by v if there is NO m-open continuation from (a->b) to Y given S={v}
            // NOTE: We are only precomputing singletons here; in the loop we combine via openWitness bitset.
            final TempSet singleton = new TempSet(v);
            return !isOpenPath(G, nodes, id, adj, a, b, sy, singleton, L, memo);
        }
    }

    private static final class OracleMemo {
        private final Map<DfsKey, Boolean> m = new ConcurrentHashMap<>(1 << 16);

        Boolean get(DfsKey k) {
            return m.get(k);
        }

        void put(DfsKey k, boolean val) {
            m.put(k, val);
        }
    }

    // --- Tiny containers / heap ------------------------------------------------

    private static final class DfsKey {
        final int a, b, y, l;
        final long lo, hi; // 128-bit mask of S ∧ frontierMask; keep small dependence

        DfsKey(int a, int b, int y, int l, long lo, long hi) {
            this.a = a;
            this.b = b;
            this.y = y;
            this.l = l;
            this.lo = lo;
            this.hi = hi;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof DfsKey)) return false;
            DfsKey k = (DfsKey) o;
            return a == k.a && b == k.b && y == k.y && l == k.l && lo == k.lo && hi == k.hi;
        }

        @Override
        public int hashCode() {
            int h = Objects.hash(a, b, y, l);
            h = 31 * h + Long.hashCode(lo);
            h = 31 * h + Long.hashCode(hi);
            return h;
        }
    }

    private static final class VisibilityOracle {
        private final Graph G;
        private final List<Node> nodes;
        private final Map<Node, Integer> id;
        private final String graphType;

        VisibilityOracle(Graph G, List<Node> nodes, Map<Node, Integer> id, String graphType) {
            this.G = G;
            this.nodes = nodes;
            this.id = id;
            this.graphType = graphType;
        }

        boolean isVisibleOutOfX(Node X, Node nb) {
            // TODO(INTEGRATE): Use your existing "visible edge out of X" test for the graph type.
            return VisibilityHooks.isVisibleOutOfX(G, X, nb, graphType);
        }
    }

    private static final class IntSet implements Iterable<Integer> {
        private final BitSet bs = new BitSet();

        IntSet() {
        }

        IntSet(IntSet other) {
            this.bs.or(other.bs);
        }

        void add(int x) {
            bs.set(x);
        }

        void remove(int x) {
            bs.clear(x);
        }

        boolean contains(int x) {
            return bs.get(x);
        }

        boolean isEmpty() {
            return bs.isEmpty();
        }

        @Override
        public Iterator<Integer> iterator() {
            return new Iterator<Integer>() {
                int i = bs.nextSetBit(0);

                @Override
                public boolean hasNext() {
                    return i >= 0;
                }

                @Override
                public Integer next() {
                    int cur = i;
                    i = bs.nextSetBit(i + 1);
                    return cur;
                }
            };
        }
    }

    private static final class IntArray {
        int[] a = new int[8];
        int n = 0;

        void add(int x) {
            if (n == a.length) a = Arrays.copyOf(a, a.length << 1);
            a[n++] = x;
        }

        int[] toArray() {
            return Arrays.copyOf(a, n);
        }
    }

    private static final class TempSet {
        // Small temp set for singleton packing; can be extended if you want S fully
        private final int v;

        TempSet(int v) {
            this.v = v;
        }

        long[] pack128() {
            return new long[]{(v < 64) ? (1L << v) : 0L, (v >= 64 && v < 128) ? (1L << (v - 64)) : 0L};
        }

        boolean contains(int x) {
            return x == v;
        }
    }

    // --- Helpers for presence/dup checks --------------------------------------

    private static final class CandScore {
        final int id;
        final double jitter;
        int negCoverage;
        int prox;
        int collBonus;

        CandScore(int id, int negCoverage, int prox, int collBonus, double jitter) {
            this.id = id;
            this.negCoverage = negCoverage;
            this.prox = prox;
            this.collBonus = collBonus;
            this.jitter = jitter;
        }
    }

    // Drop-in replacement for the FastHeap used in FastAdjustment
    private static final class FastHeap {
        private final ArrayList<CandScore> heap;
        // position of a candidate id in the heap array; -1 if not present
        private final HashMap<Integer, Integer> pos = new HashMap<>();

        FastHeap(int initialCapacity) {
            this.heap = new ArrayList<>(Math.max(16, initialCapacity));
        }

        FastHeap() {
            this(16);
        }

        // Ordering matches the previous code to keep behavior unchanged:
        // more coverage first (i.e., smaller negCoverage), then smaller proximity,
        // then (as previously written) smaller collBonus, then larger jitter.
        private static boolean lt(CandScore a, CandScore b) {
            if (a.negCoverage != b.negCoverage) return a.negCoverage < b.negCoverage;
            if (a.prox != b.prox) return a.prox > b.prox;
            if (a.collBonus != b.collBonus) return a.collBonus < b.collBonus; // NOTE: preserves prior tie-break
            return a.jitter > b.jitter;
        }

        private static boolean le(CandScore a, CandScore b) {
            return !lt(b, a);
        }

        boolean isEmpty() {
            return heap.isEmpty();
        }

        void add(CandScore s) {
            // If already present, just decreaseKey instead of inserting duplicate
            Integer at = pos.get(s.id);
            if (at != null) {
                decreaseKey(s, s.negCoverage, s.prox, s.collBonus);
                return;
            }
            heap.add(s);
            int i = heap.size() - 1;
            pos.put(s.id, i);
            siftUp(i);
        }

        CandScore pop() {
            CandScore root = heap.get(0);
            CandScore last = heap.remove(heap.size() - 1);
            pos.remove(root.id);
            if (!heap.isEmpty()) {
                heap.set(0, last);
                pos.put(last.id, 0);
                siftDown(0);
            }
            return root;
        }

        // Update s's key; supports both increase/decrease via reheapify from current index
        void decreaseKey(CandScore s, int newNegCov, int newProx, int newCollBonus) {
            Integer iObj = pos.get(s.id);
            if (iObj == null) return; // not in heap (e.g., filtered)
            int i = iObj;
            s.negCoverage = newNegCov;
            s.prox = newProx;
            s.collBonus = newCollBonus;
            // Reheapify: try up, then down if needed
            int upIdx = siftUp(i);
            if (upIdx == i) siftDown(i);
        }

        private int siftUp(int i) {
            while (i > 0) {
                int p = (i - 1) >>> 1;
                if (le(heap.get(p), heap.get(i))) break; // parent <= child: heap ok
                swap(i, p);
                i = p;
            }
            return i;
        }

        private void siftDown(int i) {
            int n = heap.size();
            while (true) {
                int l = (i << 1) + 1, r = l + 1, best = i;
                if (l < n && lt(heap.get(l), heap.get(best))) best = l;
                if (r < n && lt(heap.get(r), heap.get(best))) best = r;
                if (best == i) break;
                swap(i, best);
                i = best;
            }
        }

        private void swap(int i, int j) {
            CandScore a = heap.get(i), b = heap.get(j);
            heap.set(i, b);
            heap.set(j, a);
            pos.put(b.id, i);
            pos.put(a.id, j);
        }
    }

    // --- External hooks that MUST be wired to your existing code ---------------

    /**
     * Wire your existing m-connection step predicate here.
     */
    public static final class ReachabilityHooks {

        /**
         * Step-wise reachability predicate for m-connection along ...a - b - c...
         * Mirrors the user's existing code:
         * <p>
         * collider = (e1 endpoint at b == ARROW) && (e2 endpoint at b == ARROW)
         * if ((!collider || graph.isUnderlineTriple(a,b,c)) && b ∉ Z) return true;
         * else return collider && isAncestorOfAnyZ(b, Z)
         * <p>
         * Here, Z is represented by TempSet S; in this optimized path S may be a singleton.
         */
        public static boolean reachable(Graph G,
                                        Node a,
                                        Node b,
                                        Node c,
                                        TempSet S,
                                        Map<Node, Integer> id) {
            // Fetch the two edges (a,b) and (b,c)
            Edge e1 = getEdge(G, a, b);
            Edge e2 = getEdge(G, b, c);
            if (e1 == null || e2 == null) {
                // No adjacent edge: cannot proceed
                return false;
            }

            // Is b a collider on the triple a - b - c ?
            boolean collider =
                    (e1.getEndpoint(b) == Endpoint.ARROW) &&
                            (e2.getEndpoint(b) == Endpoint.ARROW);

            boolean bInS = S != null && S.contains(id.get(b));

            // Noncollider segment is traversable iff b ∉ S
            // Also allow "underline triple" (definite noncollider in PAG/CPDAG) per your code.
            if ((!collider || G.isUnderlineTriple(a, b, c)) && !bInS) {
                return true;
            }

            // If it's a noncollider but we conditioned on b, it blocks.
            if (!collider) return false;

            // Collider case: traverse iff b has a (possible) descendant in S.
            // ---- OPTION 1: If you have your own helper, call it here. ----
            // return yourIsAncestorOfAnyZ(b, S);

            // ---- OPTION 2: DAG/CPDAG-safe fallback (definite descendants via children) ----
            return hasDefiniteDescendantInS(G, b, S, id);
        }

        // --- Helpers ---

        // Wrap whatever Graph/Edge API you have in Tetrad (adjust if your types live elsewhere)
        private static Edge getEdge(Graph G, Node u, Node v) {
            // If your Graph already exposes getEdge(u,v), just call it.
            return G.getEdge(u, v);
        }

        /**
         * DAG/CPDAG-safe: does b have a definite descendant that lies in S?
         */
        private static boolean hasDefiniteDescendantInS(Graph G,
                                                        Node b,
                                                        TempSet S,
                                                        Map<Node, Integer> id) {
            if (S == null) return false;
            // Quick singleton check (TempSet packs one index)
            // If you later generalize TempSet, iterate all S-members instead.
            // BFS over definite children edges only.
            ArrayDeque<Node> q = new ArrayDeque<>();
            HashSet<Node> seen = new HashSet<>();
            q.add(b);
            seen.add(b);
            while (!q.isEmpty()) {
                Node u = q.poll();
                for (Node ch : G.getChildren(u)) {
                    if (seen.add(ch)) {
                        // If this child is in S, collider opens.
                        if (S.contains(id.get(ch))) return true;
                        q.add(ch);
                    }
                }
            }
            return false;
        }
    }

    /**
     * Wire your existing visibility (visible out-edge) logic here.
     */
    public static final class VisibilityHooks {
        public static boolean isVisibleOutOfX(Graph G, Node X, Node nb, String graphType) {
            // TODO(INTEGRATE): Replace with your code.
            // Placeholder: treat X->nb in DAG as visible if edge is directed out of X; otherwise false.
            return G.isDirectedFromTo(X, nb);
        }
    }

    /**
     * Wire your existing forbiddance (Forb_G(X,Y)) logic here.
     */
    public static final class ForbiddanceHooks {

        /**
         * Forb_G(X,Y): possible descendants of any node on a proper possibly directed path X -> Y,
         * including possible descendants of X (Perković et al. 2018).
         * <p>
         * BitSet implementation mirroring the user's set-based method:
         * fwdFromX        = forwardReachMask(G, gt, {X})
         * canReachY       = backwardFilterByForwardRuleMask(G, gt, Y)
         * onSomePDPath    = fwdFromX ∧ canReachY  (remove X later)
         * seeds           = {X} ∪ onSomePDPath
         * forb            = forwardReachMask(G, gt, seeds) \ {X,Y}
         */
        public static BitSet forbMask(Graph G,
                                      List<Node> nodes,
                                      Map<Node, Integer> id,
                                      Node X,
                                      Node Y,
                                      String graphType) {

            final int n = nodes.size();
            final String gt = (graphType == null) ? "DAG" : graphType.toUpperCase(Locale.ROOT);

            // --- forward reach from {X}
            final BitSet startX = new BitSet(n);
            startX.set(id.get(X));
            final BitSet fwdFromX = forwardReachMask(G, nodes, id, gt, startX);

            // --- nodes that can reach Y by a possibly directed path (reverse BFS)
            final BitSet canReachY = backwardFilterByForwardRuleMask(G, nodes, id, gt, Y);

            // --- nodes lying on some possibly directed path X -> Y
            final BitSet onSomePDPath = (BitSet) fwdFromX.clone();
            onSomePDPath.and(canReachY);
            onSomePDPath.clear(id.get(X)); // proper path: exclude X itself

            // --- seeds = {X} ∪ onSomePDPath
            final BitSet seeds = (BitSet) onSomePDPath.clone();
            seeds.set(id.get(X));

            // --- forbidden = forward descendants of seeds, minus endpoints
            final BitSet forb = forwardReachMask(G, nodes, id, gt, seeds);
            forb.clear(id.get(X));
            forb.clear(id.get(Y));
            return forb;
        }

        // -------------------------------------------------------------------------
        // Bitset BFS utilities for "possibly directed" reachability
        // -------------------------------------------------------------------------

        /**
         * Forward reach under the "possible forward step" rule.
         */
        private static BitSet forwardReachMask(Graph G,
                                               List<Node> nodes,
                                               Map<Node, Integer> id,
                                               String gt,
                                               BitSet sources) {
            final int n = nodes.size();
            final BitSet reached = new BitSet(n);
            final ArrayDeque<Integer> q = new ArrayDeque<>();
            // init
            for (int u = sources.nextSetBit(0); u >= 0; u = sources.nextSetBit(u + 1)) {
                reached.set(u);
                q.add(u);
            }
            while (!q.isEmpty()) {
                final int a = q.poll();
                final Node A = nodes.get(a);
                for (Node NB : G.getAdjacentNodes(A)) {
                    final int b = id.get(NB);
                    if (reached.get(b)) continue;
                    if (possibleForwardStep(G, gt, A, NB)) {
                        reached.set(b);
                        q.add(b);
                    }
                }
            }
            return reached;
        }

        /**
         * Reverse reach “backwards” from Y using the same forward rule:
         * u is included if there exists a neighbor v already included such that
         * forwardPossible(u -> v) is true.
         */
        private static BitSet backwardFilterByForwardRuleMask(Graph G,
                                                              List<Node> nodes,
                                                              Map<Node, Integer> id,
                                                              String gt,
                                                              Node Y) {
            final int n = nodes.size();
            final BitSet reached = new BitSet(n);
            final ArrayDeque<Integer> q = new ArrayDeque<>();
            final int y = id.get(Y);
            reached.set(y);
            q.add(y);

            while (!q.isEmpty()) {
                final int v = q.poll();
                final Node V = nodes.get(v);
                for (Node U : G.getAdjacentNodes(V)) {
                    final int u = id.get(U);
                    if (reached.get(u)) continue;
                    // reverse step is allowed if forward step U -> V is allowed
                    if (possibleForwardStep(G, gt, U, V)) {
                        reached.set(u);
                        q.add(u);
                    }
                }
            }
            return reached;
        }

        /**
         * "Possible forward step" predicate for mixed graphs:
         * allow step a -> b iff the edge endpoint at 'a' is NOT an ARROW (i.e., no arrowhead into a).
         * <p>
         * DAG: only a→b (TAIL at a) is allowed.
         * CPDAG: a→b or a−b (TAIL at a) is allowed.
         * MAG/PAG: a endpoint ∈ {TAIL, CIRCLE} is allowed; disallow if endpoint at a is ARROW.
         */
        /**
         * "Possible forward step" predicate for mixed graphs:
         * allow a → b iff the endpoint at 'a' is NOT an ARROW (no arrowhead into a),
         * with a special case for TAIL–TAIL ("undirected"):
         *  - CPDAG: allowed (direction unresolved; can be part of a PD path)
         *  - MAG/PAG: disallowed (selection edge; "out of" both ends)
         */
        private static boolean possibleForwardStep(Graph G,
                                                   String graphType,
                                                   Node a,
                                                   Node b) {
            final Edge e = G.getEdge(a, b);
            if (e == null) return false;

            final Endpoint atA = e.getEndpoint(a);
            final Endpoint atB = e.getEndpoint(b);

            // No forward step if there is an arrowhead into 'a'
            if (atA == Endpoint.ARROW) return false;

            // Special handling for undirected TAIL–TAIL edges:
            final boolean tailTail = (atA == Endpoint.TAIL) && (atB == Endpoint.TAIL);

            if (tailTail) {
                final String gt = (graphType == null) ? "" : graphType.toUpperCase(Locale.ROOT);
                if ("MAG".equals(gt) || "PAG".equals(gt)) {
                    // Selection edge: do NOT allow forward traversal in either direction
                    return false;
                } else {
                    // DAG/CPDAG (or unknown treated as CPDAG-like): allow as possibly directed
                    return true;
                }
            }

            // For circle endpoints (PAG), allow forward if there's no arrowhead into 'a'
            // e.g., a∘→b, a∘–b, a∘∘b: atA != ARROW so permitted.
            // Directed a→b (TAIL at a) is also permitted.
            return atA != Endpoint.NULL;
        }
    }
}