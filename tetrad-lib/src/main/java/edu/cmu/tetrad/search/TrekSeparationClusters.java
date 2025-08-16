/**
 * Implements Trek Separation algorithm for finding latent variable clusters. This class analyzes covariance matrices to
 * identify clusters of observed variables that share common latent parents. It uses rank-based tests to determine trek
 * separations between variable sets.
 */
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

public class TrekSeparationClusters {

    // ---- Binomial cache (reuse across calls) -----------------------------------
    private static final java.util.concurrent.ConcurrentHashMap<Long, long[][]> BINOM_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    // List of observed variables/nodes
    private final List<Node> nodes;

    // Variable indices (0..n-1)
    private final List<Integer> variables;

    // Sample size for statistical tests
    private final int sampleSize;

    // Covariance/correlation matrix
    private final SimpleMatrix S;
    // Cache of previously computed ranks
    private final Map<Key, Integer> rankCache = new ConcurrentHashMap<>();
    // Alpha level for rank tests
    private double alpha = 0.01;
    // Whether to include all nodes in the output graph
    private boolean includeAllNodes = false;
    // Verbose logging
    private boolean verbose = false;
    // Most recent clusters found
    private List<List<Integer>> clusters = new ArrayList<>();
    // Latent names for the most recent clusters found
    private List<String> latentNames = new ArrayList<>();
    // Maps for final and reduced ranks
    private Map<Set<Integer>, Integer> clusterToRank;
    private Map<Set<Integer>, Integer> reducedRank;
    // ---- Hierarchy (new) --------------------------------------------------------

    /**
     * Constructs a TrekSeparationClusters object.
     */
    public TrekSeparationClusters(List<Node> variables, CovarianceMatrix cov, int sampleSize) {
        this.nodes = new ArrayList<>(variables);
        this.sampleSize = sampleSize;

        this.variables = new ArrayList<>(variables.size());
        for (int i = 0; i < variables.size(); i++) {
            this.variables.add(i);
        }

        this.S = new CovarianceMatrix(cov).getMatrix().getSimpleMatrix();
    }

    // Pascal triangle, up to n choose k (inclusive on n)
    static long[][] precomputeBinom(int n, int k) {
        long[][] C = new long[n + 1][k + 1];
        for (int i = 0; i <= n; i++) {
            C[i][0] = 1;
            int maxj = Math.min(i, k);
            for (int j = 1; j <= maxj; j++) {
                long v = C[i - 1][j - 1] + C[i - 1][j];
                if (v < 0 || v < C[i - 1][j - 1]) v = Long.MAX_VALUE; // clamp on overflow
                C[i][j] = v;
            }
        }
        return C;
    }

    // choose(x, j) with the convention C(x, j) = 0 when x < j
    static long choose(long[][] C, int x, int j) {
        if (x < j || j < 0) return 0L;
        return C[x][j];
    }

    private static long[][] binom(int n, int k) {
        long key = (((long) n) << 32) ^ k;
        return BINOM_CACHE.computeIfAbsent(key, binom -> precomputeBinom(n, k));
    }

    // ---- Colex unranking: m -> k-combination of {0..n-1} -----------------------
    private static void combinadicDecodeColex(long m, int n, int k, long[][] C, int[] out) {
        long r = m;
        int bound = n;
        for (int i = k; i >= 1; i--) {
            int lo = 0, hi = bound - 1, v = 0;
            while (lo <= hi) {
                int mid = (lo + hi) >>> 1;
                long c = choose(C, mid, i);
                if (c <= r) {
                    v = mid;
                    lo = mid + 1;
                } else {
                    hi = mid - 1;
                }
            }
            out[i - 1] = v;
            r -= choose(C, v, i);
            bound = v;
        }
    }

    private static int[] minus(int[] universe, int[] remove) {
        // universe and remove are small; simple scan is fine
        BitSet rm = new BitSet();
        for (int v : remove) rm.set(v);
        int cnt = 0;
        for (int v : universe) if (!rm.get(v)) cnt++;
        int[] out = new int[cnt];
        int i = 0;
        for (int v : universe) if (!rm.get(v)) out[i++] = v;
        return out;
    }

    // ---- Enumerate k-combos of vars and test ranks -----------------------------
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

            // decode indices of the combination into tlIdxs
            int[] idxs = tlIdxs.get();
            combinadicDecodeColex(m, n, k, C, idxs);

            // map to variable IDs into tlIds
            int[] ids = tlIds.get();
            for (int i = 0; i < k; i++) ids[i] = varIds[idxs[i]];

            // fast rank check via canonical key
            int r = lookupRankFast(ids);
            if (r != rank) return null;

            // only now build the Set<Integer> to return
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
        // Build a set once for the actual rank computation
        Set<Integer> s = new HashSet<>(ids.length * 2);
        for (int x : ids) s.add(x);
        int r = rank(s);
        rankCache.put(k, r);
        return r;
    }

    /**
     * Searches for latent clusters using specified size and rank parameters.
     */
    public Graph search() {
        Pair<Map<Set<Integer>, Integer>, Map<Set<Integer>, Integer>> ret = estimateClusters();
        clusterToRank = ret.getFirst();
        reducedRank = ret.getSecond();

        // Stable order: larger clusters first, then lexical by names
        List<Set<Integer>> clusterSets = clusterToRank.keySet().stream()
                .sorted(Comparator.<Set<Integer>>comparingInt(Set::size).reversed()
                        .thenComparing(this::toNamesCluster))
                .collect(Collectors.toList());

        List<Node> latents = defineLatents(clusterSets, clusterToRank, reducedRank);
        Graph graph = convertSearchGraphClusters(clusterSets, latents, includeAllNodes);

        this.latentNames = new ArrayList<>();
        for (Node latent : latents) {
            latentNames.add(latent.getName());
        }

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
        for (Set<Integer> cluster : clusterToRanks.keySet()) {
            this.clusters.add(new ArrayList<>(cluster));
        }

        return new Pair<>(clusterToRanks, reducedRanks);
    }

    private List<Integer> allVariables() {
        List<Integer> _variables = new ArrayList<>();
        for (int i = 0; i < this.variables.size(); i++) _variables.add(i);
        return _variables;
    }

    public void setAlpha(double alpha) {
        this.alpha = alpha;
    }

    public void setIncludeAllNodes(boolean includeAllNodes) {
        this.includeAllNodes = includeAllNodes;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    private @NotNull Pair<Map<Set<Integer>, Integer>, Map<Set<Integer>, Integer>> clusterSearchMetaLoop() {
        List<Integer> remainingVars = new ArrayList<>(allVariables());
        clusterToRank = new HashMap<>();
        reducedRank = new HashMap<>();

        for (int rank = 0; rank <= 3; rank++) {
            int size = rank + 1;

            if (Thread.currentThread().isInterrupted()) break;

            if (size >= remainingVars.size() - size) {
                continue;
            }

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

                        // Only skip if union adds *no new elements* (i.e. union == cluster).
                        // Using "==" instead of "<=" avoids blocking valid growth steps.
                        // With this choice, the only case we give up is forming a single cluster
                        // that covers *all* observed variables, since then the complement is empty.
                        if (union.size() == cluster.size()) continue;

                        int rankOfUnion = lookupRank(union);
                        log("For this candidate: " + toNamesCluster(candidate) + ", Trying union: " + toNamesCluster(union) + " rank = " + rankOfUnion);

                        if (rankOfUnion == rank) {
                            // Accept this union, grow cluster and consume candidate from P1
                            cluster = union;
                            it.remove();
                            extended = true;
                            break;
                        }
                    }
                } while (extended);

                int finalRank = lookupRank(cluster);
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

            if (!didAugment) {
                log("No augmentations were needed.");
            }

            log("New clusters after the augmentation step = " + (newClusters.isEmpty() ? "NONE" : toNamesClusters(newClusters)));

            for (Set<Integer> cluster : new ArrayList<>(newClusters)) {
                clusterToRank.put(cluster, rank);
            }

            log("Now we will add all of the new clusters to the set of all discovered clusters.");
            log("All clusters at this point: " + (newClusters.isEmpty() ? "NONE" : toNamesClusters(newClusters)));

            log("Now we will remove clustered variables from further consideration.");

            for (Set<Integer> _C : newClusters) {
                used.addAll(_C);
            }

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

        if (!penultimateRemoved) {
            log("No penultimate clusters were removed.");
        }

        log("Final clusters = " + toNamesClusters(clusterToRank.keySet()));
        return new Pair<>(clusterToRank, reducedRank);
    }

    /**
     * Evaluates whether a given cluster fails the subset test based on rank conditions derived from the matrix S.
     */
    private boolean failsSubsetTest(SimpleMatrix S, Set<Integer> cluster, int sampleSize, double alpha) {

        List<Integer> C = new ArrayList<>(cluster);
        List<Integer> D = allVariables();
        D.removeAll(cluster);

        // Rule 1: all non-empty proper bipartitions of C
        {
            SublistGenerator gen0 = new SublistGenerator(C.size(), C.size() - 1); // iterates all non-empty sublists
            int[] choice0;
            while ((choice0 = gen0.next()) != null) {
                List<Integer> C1 = new ArrayList<>();
                for (int i : choice0) {
                    C1.add(C.get(i));
                }
                if (C1.isEmpty() || C1.size() == C.size()) continue;

                List<Integer> C2 = new ArrayList<>(C);
                C2.removeAll(C1);
                if (C2.isEmpty()) continue;

                int[] c1Array = C1.stream().mapToInt(Integer::intValue).toArray();
                int[] c2Array = C2.stream().mapToInt(Integer::intValue).toArray();

                int minpq = Math.min(c1Array.length, c2Array.length);
                Integer l = clusterToRank.get(cluster);
                if (l == null) continue; // safety
                l = Math.min(minpq, Math.max(0, l));

                int r = RankTests.estimateWilksRank(S, c1Array, c2Array, sampleSize, alpha);
                if (r < l) {
                    log("Deficient! rank(" + toNamesCluster(C1) + ", " + toNamesCluster(C2) + ") = "
                        + r + " < " + l + "; removing " + toNamesCluster(cluster));
                    return true;
                }
            }
        }

        // Rule 2: remove single element from C and test with D
        {
            SublistGenerator gen0 = new SublistGenerator(C.size(), C.size() - 1);
            int[] choice;
            while ((choice = gen0.next()) != null) {
                if (choice.length < 1) continue;

                List<Integer> _C = new ArrayList<>();
                for (int i : choice) {
                    _C.add(C.get(i));
                }

                int[] _cArray = _C.stream().mapToInt(Integer::intValue).toArray();
                int[] dArray = D.stream().mapToInt(Integer::intValue).toArray();

                int minpq = Math.min(_cArray.length, dArray.length);

                Integer l = Optional.ofNullable(reducedRank.get(cluster))
                        .orElse(clusterToRank.getOrDefault(cluster, 0));
                l = Math.min(minpq, Math.max(0, l));

                int r = RankTests.estimateWilksRank(S, _cArray, dArray, sampleSize, alpha);
                if (r < l) {
                    log("rank(" + toNamesCluster(_C) + ", D) = " + r + " < r = " + l
                        + "; removing cluster " + toNamesCluster(cluster));
                    return true;
                }
            }
        }

        // Rule 3: conditioning on subsets of C
        {
            Integer rC = clusterToRank.get(cluster);
            if (rC == null) rC = 0;

            SublistGenerator gen2 = new SublistGenerator(C.size(), Math.min(C.size() - 1, rC));
            int[] choice2;

            while ((choice2 = gen2.next()) != null) {
                if (choice2.length < rC) continue;

                List<Integer> Z = new ArrayList<>();
                for (int i : choice2) {
                    Z.add(C.get(i));
                }

                List<Integer> _C = new ArrayList<>(C);
                _C.removeAll(Z);

                int[] _cArray = _C.stream().mapToInt(Integer::intValue).toArray();
                int[] dArray = D.stream().mapToInt(Integer::intValue).toArray();
                int[] zArray = Z.stream().mapToInt(Integer::intValue).toArray();

                int rZ = RankTests.estimateWilksRankConditioned(S, _cArray, dArray, zArray, sampleSize, alpha);
                if (rZ == 0) {
                    log("rank(_C = " + toNamesCluster(_C) + ", D | Z = " + toNamesCluster(Z) + ") = 0; removing cluster " + toNamesCluster(cluster) + ".");
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Add latent->latent edges when conditioning on parent indicators lowers the Wilks rank between child indicators
     * and the rest of the observed variables by at least minRankDrop.
     * <p>
     * For each ordered pair (La, Lb), let Ca, Cb be their indicator sets and D = V \ Cb. If rank(Cb, D | Ca) <=
     * rank(Cb, D) - minRankDrop, add La -> Lb, avoiding directed cycles.
     */
    public List<Edge> getHierarchyEdges(List<List<Integer>> blocks,
                                         List<Node> metaVars,
                                         SimpleMatrix S,
                                         int sampleSize,
                                         double alpha, int minRankDrop) {

        List<Edge> edges = new ArrayList<>();

        // Consider only latent blocks (size > 1) as candidates
        List<Integer> latentIdx = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i++) if (blocks.get(i).size() > 1) latentIdx.add(i);
        final int m = latentIdx.size();
        if (m <= 1) return new ArrayList<>();

        // Universe of observed variable indices
        int p = variables.size();
        int[] all = new int[p];
        for (int j = 0; j < p; j++) all[j] = j;

        // Candidate edges with rank drops
        class Cand {
            final int ia, ib; // indices in 'blocks' / 'metaVars'
            final int r0, r1, drop;

            Cand(int ia, int ib, int r0, int r1) {
                this.ia = ia;
                this.ib = ib;
                this.r0 = r0;
                this.r1 = r1;
                this.drop = r0 - r1;
            }
        }
        List<Cand> cands = new ArrayList<>();

        for (int aPos = 0; aPos < m; aPos++) {
            int ia = latentIdx.get(aPos);
            int[] Ca = blocks.get(ia).stream().mapToInt(Integer::intValue).toArray();

            for (int bPos = 0; bPos < m; bPos++) {
                int ib = latentIdx.get(bPos);
                if (ia == ib) continue;

                int[] Cb = blocks.get(ib).stream().mapToInt(Integer::intValue).toArray();
                if (Cb.length == 0) continue;

                int[] D = minus(all, Cb);
                if (D.length == 0) continue;

                int r0 = RankTests.estimateWilksRank(S, Cb, D, sampleSize, alpha);
                if (r0 <= 0) continue;

                int r1 = RankTests.estimateWilksRankConditioned(S, Cb, D, Ca, sampleSize, alpha);

                if (r0 - r1 >= minRankDrop) {
                    cands.add(new Cand(ia, ib, r0, r1));
                }
            }
        }

        // Greedy: biggest rank drop first; tiebreak by names to be deterministic
        cands.sort(Comparator.<Cand>comparingInt(c -> c.drop).reversed()
                .thenComparing(c -> metaVars.get(c.ia).getName())
                .thenComparing(c -> metaVars.get(c.ib).getName()));

        for (Cand c : cands) {
            Node from = metaVars.get(c.ia);
            Node to = metaVars.get(c.ib);
            edges.add(Edges.directedEdge(from, to));

            if (verbose) {
                System.out.printf("Hierarchy: %s -> %s (drop=%d; r0=%d, r1=%d)%n",
                        from.getName(), to.getName(), (c.r0 - c.r1), c.r0, c.r1);
            }
        }

        return edges;
    }

    /**
     * Cached rank lookup via canonical key
     */
    private int lookupRank(Set<Integer> cluster) {
        Key k = new Key(cluster);
        Integer cached = rankCache.get(k);
        if (cached != null) return cached;
        int r = rank(cluster);
        rankCache.put(k, r);
        return r;
    }
    // ===== END hierarchy glue ===================================================

    private @NotNull StringBuilder toNamesCluster(Collection<Integer> cluster) {
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

    private @NotNull String toNamesClusters(Set<Set<Integer>> clusters) {
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

    private void log(String s) {
        if (verbose) {
            TetradLogger.getInstance().log(s);
        }
    }

    private String toNamesCluster(Set<Integer> cluster) {
        return cluster.stream().map(i -> nodes.get(i).getName()).collect(Collectors.joining(" ", "{", "}"));
    }

    public List<List<Integer>> getClusters() {
        return new ArrayList<>(this.clusters);
    }

    public List<String> getLatentNames() {
        return new ArrayList<>(this.latentNames);
    }

    // ---- Canonical key for caching ranks (immutable, sorted) -------------------
    private static final class Key {
        final int[] a;

        Key(Collection<Integer> s) {
            this.a = s.stream().mapToInt(Integer::intValue).sorted().toArray();
        }

        Key(int[] ids) {
            this.a = Arrays.stream(ids).sorted().toArray();
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(a);
        }

        @Override
        public boolean equals(Object o) {
            return (o instanceof Key) && Arrays.equals(a, ((Key) o).a);
        }
    }
}