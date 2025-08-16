package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.utils.ClusterUtils;
import edu.cmu.tetrad.util.RankTests;
import edu.cmu.tetrad.util.SublistGenerator;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.math3.util.Pair;
import org.ejml.simple.SimpleMatrix;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static edu.cmu.tetrad.util.RankTests.estimateWilksRank;

/**
 * Implements Trek Separation algorithm for finding latent variable clusters.
 */
public class TrekSeparationClusters {

    // ---- existing fields (unchanged) ----
    private static final java.util.concurrent.ConcurrentHashMap<Long, long[][]> BINOM_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private final List<Node> nodes;
    private final List<Integer> variables;
    private final int sampleSize;
    private final SimpleMatrix S;
    private final Map<Key, Integer> rankCache = new ConcurrentHashMap<>();
    private double alpha = 0.01;
    private boolean includeAllNodes = false;
    private boolean verbose = false;
    private List<List<Integer>> clusters = new ArrayList<>();
    private List<String> latentNames = new ArrayList<>();
    private Map<Set<Integer>, Integer> clusterToRank;
    private Map<Set<Integer>, Integer> reducedRank;

    // ---- NEW: optional RLCD-style guard (off by default) -----------------------
    private boolean useAtomicCoverGuard = false;

    public void setUseAtomicCoverGuard(boolean b) { this.useAtomicCoverGuard = b; }

    // ---- ctor ----
    public TrekSeparationClusters(List<Node> variables, CovarianceMatrix cov, int sampleSize) {
        this.nodes = new ArrayList<>(variables);
        this.sampleSize = sampleSize;
        this.variables = new ArrayList<>(variables.size());
        for (int i = 0; i < variables.size(); i++) this.variables.add(i);
        this.S = new CovarianceMatrix(cov).getMatrix().getSimpleMatrix();
    }

    // ---- binom machinery, combinadic, etc. (unchanged) ----
    static long[][] precomputeBinom(int n, int k) { /* ... unchanged ... */
        long[][] C = new long[n + 1][k + 1];
        for (int i = 0; i <= n; i++) {
            C[i][0] = 1;
            int maxj = Math.min(i, k);
            for (int j = 1; j <= maxj; j++) {
                long v = C[i - 1][j - 1] + C[i - 1][j];
                if (v < 0 || v < C[i - 1][j - 1]) v = Long.MAX_VALUE;
                C[i][j] = v;
            }
        }
        return C;
    }
    static long choose(long[][] C, int x, int j) { if (x < j || j < 0) return 0L; return C[x][j]; }
    private static long[][] binom(int n, int k) {
        long key = (((long) n) << 32) ^ k;
        return BINOM_CACHE.computeIfAbsent(key, binom -> precomputeBinom(n, k));
    }
    private static void combinadicDecodeColex(long m, int n, int k, long[][] C, int[] out) { /* ... unchanged ... */
        long r = m;
        int bound = n;
        for (int i = k; i >= 1; i--) {
            int lo = 0, hi = bound - 1, v = 0;
            while (lo <= hi) {
                int mid = (lo + hi) >>> 1;
                long c = choose(C, mid, i);
                if (c <= r) { v = mid; lo = mid + 1; } else { hi = mid - 1; }
            }
            out[i - 1] = v;
            r -= choose(C, v, i);
            bound = v;
        }
    }

    // ---- New fast variant: enumerate k-combos of vars and test ranks -----------
    private Set<Set<Integer>> findClustersAtRank(List<Integer> vars, int size, int rank) {
        final int n = vars.size();
        final int k = size;

        if (rank + 1 >= n - (rank + 1)) {
            throw new IllegalArgumentException("rank too high for clusters at rank");
        }

        final int[] varIds = new int[n];
        for (int i = 0; i < n; i++) varIds[i] = vars.get(i);

        final long[][] C = binom(n, k);
        final long total = C[n][k];

        final ThreadLocal<int[]> tlIdxs = ThreadLocal.withInitial(() -> new int[k]);
        final ThreadLocal<int[]> tlIds = ThreadLocal.withInitial(() -> new int[k]);

        AtomicInteger count = new AtomicInteger();

        return LongStream.range(0, total).parallel().mapToObj(m -> {
            if (Thread.currentThread().isInterrupted()) return null;

            int _count = count.getAndIncrement();
            if (_count % 1000 == 0) {
                log("Count = " + count.get() + " of total = " + total);
            }

            int[] idxs = tlIdxs.get();
            combinadicDecodeColex(m, n, k, C, idxs);

            int[] ids = tlIds.get();
            for (int i = 0; i < k; i++) ids[i] = varIds[idxs[i]];

            int r = lookupRankFast(ids);
            if (r != rank) return null;

            Set<Integer> cluster = new HashSet<>(k * 2);
            for (int i = 0; i < k; i++) cluster.add(ids[i]);
            return cluster;
        }).filter(Objects::nonNull).collect(java.util.stream.Collectors.toCollection(java.util.concurrent.ConcurrentHashMap::newKeySet));
    }

    // Fast overload: takes primitive IDs and uses canonical Key
    private int lookupRankFast(int[] ids) {
        Key k = new Key(ids);
        Integer cached = rankCache.get(k);
        if (cached != null) return cached;
        Set<Integer> s = new HashSet<>(ids.length * 2);
        for (int x : ids) s.add(x);
        int r = rank(s);
        rankCache.put(k, r);
        return r;
    }

    /** Searches for latent clusters using specified size and rank parameters. */
    public Graph search() {
        Pair<Map<Set<Integer>, Integer>, Map<Set<Integer>, Integer>> ret = estimateClusters();
        clusterToRank = ret.getFirst();
        reducedRank = ret.getSecond();

        List<Set<Integer>> clusterSets = clusterToRank.keySet().stream()
                .sorted(Comparator.<Set<Integer>>comparingInt(Set::size).reversed()
                        .thenComparing(this::toNamesCluster))
                .collect(Collectors.toList());

        List<Node> latents = defineLatents(clusterSets, clusterToRank, reducedRank);
        Graph graph = convertSearchGraphClusters(clusterSets, latents, includeAllNodes);

        this.latentNames = new ArrayList<>();
        for (Node latent : latents) latentNames.add(latent.getName());
        return graph;
    }

    private Pair<Map<Set<Integer>, Integer>, Map<Set<Integer>, Integer>> estimateClusters() {
        List<Integer> variables = allVariables();
        if (new HashSet<>(variables).size() != variables.size()) {
            throw new IllegalArgumentException("Variables must be unique.");
        }

        Pair<Map<Set<Integer>, Integer>, Map<Set<Integer>, Integer>> ret = clusterSearchMetaLoop();
        Map<Set<Integer>, Integer> clusterToRanks = ret.getFirst();
        Map<Set<Integer>, Integer> reducedRanks = ret.getSecond();

        this.clusters = new ArrayList<>();
        for (Set<Integer> cluster : clusterToRanks.keySet()) this.clusters.add(new ArrayList<>(cluster));
        return new Pair<>(clusterToRanks, reducedRanks);
    }

    private List<Integer> allVariables() {
        List<Integer> _variables = new ArrayList<>();
        for (int i = 0; i < this.variables.size(); i++) _variables.add(i);
        return _variables;
    }

    public void setAlpha(double alpha) { this.alpha = alpha; }
    public void setIncludeAllNodes(boolean includeAllNodes) { this.includeAllNodes = includeAllNodes; }
    public void setVerbose(boolean verbose) { this.verbose = verbose; }

    private @NotNull Pair<Map<Set<Integer>, Integer>, Map<Set<Integer>, Integer>> clusterSearchMetaLoop() {
        List<Integer> remainingVars = new ArrayList<>(allVariables());
        clusterToRank = new HashMap<>();
        reducedRank = new HashMap<>();

        for (int rank = 0; rank <= 3; rank++) {
            int size = rank + 1;
            if (Thread.currentThread().isInterrupted()) break;
            if (size >= remainingVars.size() - size) continue;

            log("EXAMINING SIZE " + size + " RANK = " + rank + " REMAINING VARS = " + remainingVars.size());
            Set<Set<Integer>> P = findClustersAtRank(remainingVars, size, rank);
            log("Base clusters for size " + size + " rank " + rank + ": " + (P.isEmpty() ? "NONE" : toNamesClusters(P)));
            Set<Set<Integer>> P1 = new HashSet<>(P);

            Set<Set<Integer>> newClusters = new HashSet<>();
            Set<Integer> used = new HashSet<>();

            while (!P1.isEmpty()) {
                if (Thread.currentThread().isInterrupted()) break;

                Iterator<Set<Integer>> seedIt = P1.iterator();
                Set<Integer> seed = seedIt.next();
                seedIt.remove();

                if (!Collections.disjoint(used, seed)) continue;

                Set<Integer> cluster = new HashSet<>(seed);

                if (seed.size() >= this.variables.size() - seed.size()) continue;

                log("Picking seed from the list: " + toNamesCluster(seed) + " rank = " + lookupRank(seed));

                boolean extended;
                do {
                    extended = false;
                    for (Iterator<Set<Integer>> it = P1.iterator(); it.hasNext(); ) {
                        if (Thread.currentThread().isInterrupted()) break;

                        Set<Integer> candidate = it.next();
                        if (!Collections.disjoint(used, candidate)) continue;
                        if (Collections.disjoint(candidate, cluster)) continue;
                        if (cluster.containsAll(candidate)) continue;

                        Set<Integer> union = new HashSet<>(cluster);
                        union.addAll(candidate);

                        if (union.size() == cluster.size()) continue;

                        int rankOfUnion = lookupRank(union);
                        log("For this candidate: " + toNamesCluster(candidate) + ", Trying union: " + toNamesCluster(union) + " rank = " + rankOfUnion);

                        if (rankOfUnion == rank) {
                            cluster = union;
                            it.remove();
                            extended = true;
                            break;
                        }
                    }
                } while (extended);

                int finalRank = lookupRank(cluster);

                // ---- NEW: optional RLCD-style atomic-cover equality check right after discovery
                if (useAtomicCoverGuard && finalRank == rank && cluster.size() > 1) {
                    // Partition cluster into C (left) and X (right) minimally to test equality.
                    // Simple choice: pick one element into X, rest into C (works for k = rank).
                    int k = rank; // expected rank
                    int[] all = cluster.stream().mapToInt(Integer::intValue).toArray();
                    int[] X = new int[]{ all[0] };
                    int[] C = Arrays.copyOfRange(all, 1, all.length);

                    int p = variables.size();
                    int[] D = makeRightSide(p, C, X); // (V \ C) ∪ X, disjoint from left
                    boolean ok = RankEqualities.atomicCoverEquality(S, C, X, D, k, sampleSize, alpha);
                    if (!ok) {
                        log("Atomic-cover guard failed for cluster " + toNamesCluster(cluster) + "; discarding.");
                        finalRank = -1; // force discard below
                    }
                }
                // ---- end optional guard

                if (finalRank == rank) {
                    newClusters.removeIf(cluster::containsAll);  // Avoid nesting
                    log("Adding cluster to new clusters: " + toNamesCluster(cluster) + " rank = " + finalRank);
                    newClusters.add(cluster);
                    used.addAll(cluster);
                    remainingVars.removeAll(cluster);
                }
            }

            log("New clusters for rank " + rank + " size = " + size + ": " + (newClusters.isEmpty() ? "NONE" : toNamesClusters(newClusters)));

            Set<Set<Integer>> P2 = new HashSet<>(P);
            log("Now we will try to augment each cluster by one new variable by looking at cluster overlaps again.");
            log("We will repeat this for ranks rank - 1 down to rank 1.");

            boolean didAugment = false;

            for (int _reducedRank = rank - 1; _reducedRank >= 1; _reducedRank--) {
                if (Thread.currentThread().isInterrupted()) break;

                for (Set<Integer> C1 : new HashSet<>(newClusters)) {
                    if (Thread.currentThread().isInterrupted()) break;

                    int _size = C1.size();

                    for (Set<Integer> _C : P2) {
                        if (Thread.currentThread().isInterrupted()) break;

                        Set<Integer> C2 = new HashSet<>(C1);
                        C2.addAll(_C);

                        if (C2.size() >= this.variables.size() - C2.size()) continue;

                        int newRank = lookupRank(C2);

                        if (C2.size() == _size + 1 && newRank < rank && newRank >= 1) {
                            if (newClusters.contains(C2)) continue;

                            newClusters.remove(C1);
                            newClusters.add(C2);
                            reducedRank.put(C2, newRank);
                            used.addAll(C2);
                            log("Augmenting cluster " + toNamesCluster(C1) + " to cluster " + toNamesCluster(C2) + " (rank " + _reducedRank + ").");
                            didAugment = true;
                        }
                    }
                }
            }

            if (!didAugment) log("No augmentations were needed.");
            log("New clusters after the augmentation step = " + (newClusters.isEmpty() ? "NONE" : toNamesClusters(newClusters)));

            for (Set<Integer> cluster : new ArrayList<>(newClusters)) clusterToRank.put(cluster, rank);

            for (Set<Integer> _C : newClusters) used.addAll(_C);
            remainingVars.removeAll(used);
        }

        log("Removing clusters of size 1, as these shouldn't be assigned latents.");
        for (Set<Integer> cluster : new HashSet<>(clusterToRank.keySet())) {
            if (cluster.size() == 1) {
                clusterToRank.remove(cluster);
                reducedRank.remove(cluster);
                log("Removing cluster " + toNamesCluster(cluster));
            }
        }

        log("Penultimate clusters = " + toNamesClusters(clusterToRank.keySet()));
        log("Now we will consider whether any of the penultimate clusters should be discarded (as from a non-latent DAG, e.g.).");

        boolean penultimateRemoved = false;
        for (Set<Integer> cluster : new HashSet<>(clusterToRank.keySet())) {
            if (failsSubsetTest(S, cluster, sampleSize, alpha)) {
                clusterToRank.remove(cluster);
                reducedRank.remove(cluster);
                penultimateRemoved = true;
            }
        }
        if (!penultimateRemoved) log("No penultimate clusters were removed.");

        log("Final clusters = " + toNamesClusters(clusterToRank.keySet()));
        return new Pair<>(clusterToRank, reducedRank);
    }

    // ---- existing failsSubsetTest, rank(), logging, etc. (unchanged) ----
    private boolean failsSubsetTest(SimpleMatrix S, Set<Integer> cluster, int sampleSize, double alpha) { /* ... unchanged ... */
        List<Integer> C = new ArrayList<>(cluster);
        List<Integer> D = allVariables();
        D.removeAll(cluster);

        { // Rule 1
            SublistGenerator gen0 = new SublistGenerator(C.size(), C.size() - 1);
            int[] choice0;
            while ((choice0 = gen0.next()) != null) {
                List<Integer> C1 = new ArrayList<>();
                for (int i : choice0) C1.add(C.get(i));
                if (C1.isEmpty() || C1.size() == C.size()) continue;

                List<Integer> C2 = new ArrayList<>(C);
                C2.removeAll(C1);
                if (C2.isEmpty()) continue;

                int[] c1Array = C1.stream().mapToInt(Integer::intValue).toArray();
                int[] c2Array = C2.stream().mapToInt(Integer::intValue).toArray();

                int minpq = Math.min(c1Array.length, c2Array.length);
                Integer l = clusterToRank.get(cluster);
                if (l == null) continue;
                l = Math.min(minpq, Math.max(0, l));

                int r = RankTests.estimateWilksRank(S, c1Array, c2Array, sampleSize, alpha);
                if (r < l) return true;
            }
        }
        { // Rule 2
            SublistGenerator gen0 = new SublistGenerator(C.size(), C.size() - 1);
            int[] choice;
            while ((choice = gen0.next()) != null) {
                if (choice.length < 1) continue;
                List<Integer> _C = new ArrayList<>();
                for (int i : choice) _C.add(C.get(i));
                int[] _cArray = _C.stream().mapToInt(Integer::intValue).toArray();
                int[] dArray = D.stream().mapToInt(Integer::intValue).toArray();

                int minpq = Math.min(_cArray.length, dArray.length);
                Integer l = Optional.ofNullable(reducedRank.get(cluster)).orElse(clusterToRank.getOrDefault(cluster, 0));
                l = Math.min(minpq, Math.max(0, l));

                int r = RankTests.estimateWilksRank(S, _cArray, dArray, sampleSize, alpha);
                if (r < l) return true;
            }
        }
        { // Rule 3
            Integer rC = clusterToRank.get(cluster);
            if (rC == null) rC = 0;

            SublistGenerator gen2 = new SublistGenerator(C.size(), Math.min(C.size() - 1, rC));
            int[] choice2;
            while ((choice2 = gen2.next()) != null) {
                if (choice2.length < rC) continue;

                List<Integer> Z = new ArrayList<>();
                for (int i : choice2) Z.add(C.get(i));

                List<Integer> _C = new ArrayList<>(C);
                _C.removeAll(Z);

                int[] _cArray = _C.stream().mapToInt(Integer::intValue).toArray();
                int[] dArray = D.stream().mapToInt(Integer::intValue).toArray();
                int[] zArray = Z.stream().mapToInt(Integer::intValue).toArray();

                int rZ = RankTests.estimateWilksRankConditioned(S, _cArray, dArray, zArray, sampleSize, alpha);
                if (rZ == 0) return true;
            }
        }
        return false;
    }

    private int lookupRank(Set<Integer> cluster) {
        Key k = new Key(cluster);
        Integer cached = rankCache.get(k);
        if (cached != null) return cached;
        int r = rank(cluster);
        rankCache.put(k, r);
        return r;
    }

    private @NotNull StringBuilder toNamesCluster(Collection<Integer> cluster) { /* ... unchanged ... */
        StringBuilder _sb = new StringBuilder();
        _sb.append("[");
        int count = 0;
        for (Integer var : cluster) {
            _sb.append(nodes.get(var));
            if (count++ < cluster.size() - 1) _sb.append(", ");
        }
        _sb.append("]");
        return _sb;
    }

    private @NotNull String toNamesClusters(Set<Set<Integer>> clusters) { /* ... unchanged ... */
        StringBuilder sb = new StringBuilder();
        int count0 = 0;
        for (Collection<Integer> cluster : clusters) {
            StringBuilder _sb = toNamesCluster(cluster);
            if (count0++ < clusters.size() - 1) _sb.append("; ");
            sb.append(_sb);
        }
        return sb.toString();
    }

    private int rank(Set<Integer> cluster) {
        List<Integer> ySet = new ArrayList<>(cluster);
        List<Integer> xSet = new ArrayList<>(variables);
        xSet.removeAll(ySet);

        int[] xIndices = new int[xSet.size()];
        int[] yIndices = new int[ySet.size()];
        for (int i = 0; i < xSet.size(); i++) xIndices[i] = xSet.get(i);
        for (int i = 0; i < ySet.size(); i++) yIndices[i] = ySet.get(i);

        return estimateWilksRank(S, xIndices, yIndices, sampleSize, alpha);
    }

    private Graph convertSearchGraphClusters(List<Set<Integer>> clusters, List<Node> latents, boolean includeAllNodes) {
        Graph graph = includeAllNodes ? new EdgeListGraph(this.nodes) : new EdgeListGraph();
        for (int i = 0; i < clusters.size(); i++) {
            graph.addNode(latents.get(i));
            for (int j : clusters.get(i)) {
                if (!graph.containsNode(nodes.get(j))) graph.addNode(nodes.get(j));
                graph.addDirectedEdge(latents.get(i), nodes.get(j));
            }
        }
        return graph;
    }

    private List<Node> defineLatents(List<Set<Integer>> clusters, Map<Set<Integer>, Integer> ranks, Map<Set<Integer>, Integer> reducedRank) {
        List<Node> latents = new ArrayList<>();
        for (int i = 0; i < clusters.size(); i++) {
            int rank = ranks.get(clusters.get(i));
            Integer reduced = reducedRank.get(clusters.get(i));
            String rankSpec = rank + (reduced == null ? "" : ";" + reduced);
            Node latent = new GraphNode(ClusterUtils.LATENT_PREFIX + (i + 1) + "(" + rankSpec + ")");
            latent.setNodeType(NodeType.LATENT);
            latents.add(latent);
        }
        return latents;
    }

    private void log(String s) { if (verbose) TetradLogger.getInstance().log(s); }
    private String toNamesCluster(Set<Integer> cluster) { return cluster.stream().map(i -> nodes.get(i).getName()).collect(Collectors.joining(" ", "{", "}")); }
    public List<List<Integer>> getClusters() { return new ArrayList<>(this.clusters); }
    public List<String> getLatentNames() { return new ArrayList<>(this.latentNames); }

    // ---- Canonical key for caching ranks (immutable, sorted) -------------------
    private static final class Key {
        final int[] a;
        Key(Collection<Integer> s) { this.a = s.stream().mapToInt(Integer::intValue).sorted().toArray(); }
        Key(int[] ids) { this.a = Arrays.stream(ids).sorted().toArray(); }
        @Override public int hashCode() { return Arrays.hashCode(a); }
        @Override public boolean equals(Object o) { return (o instanceof Key) && Arrays.equals(a, ((Key) o).a); }
    }

    // ---- NEW helpers for optional guard ---------------------------------------
    static int[] makeRightSide(int p, int[] C, int[] X) {
        BitSet bs = new BitSet(); // start with V \ C
        for (int i = 0; i < p; i++) bs.set(i);
        for (int v : C) bs.clear(v);
        // union X
        for (int v : X) bs.set(v);
        int n = bs.cardinality();
        int[] out = new int[n];
        for (int i = bs.nextSetBit(0), k = 0; i >= 0; i = bs.nextSetBit(i + 1)) out[k++] = i;
        return out;
    }
}