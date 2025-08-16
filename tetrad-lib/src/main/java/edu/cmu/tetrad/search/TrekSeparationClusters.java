/**
 * Implements Trek Separation algorithm for finding latent variable clusters. This class analyzes covariance matrices to
 * identify clusters of observed variables that share common latent parents. It uses rank-based tests to determine trek
 * separations between variable sets.
 * <p>
 * Copyright (C) 1998-2022 by Peter Spirtes, Richard Scheines, Joseph Ramsey, and Clark Glymour.
 * <p>
 * This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation; either version 2 of the License or (at your option) any later
 * version.
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

/**
 * The {@code TrekSeparationClusters} class is designed to analyze and identify clusters in data based on trek
 * separation principles.
 */
public class TrekSeparationClusters {
    /**
     * ---- Binomial cache (reuse across calls) -----------------------------------
     */
    private static final java.util.concurrent.ConcurrentHashMap<Long, long[][]> BINOM_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    /**
     * List of observed variables/nodes
     */
    private final List<Node> nodes;
    /**
     * List of variable indices
     */
    private final List<Integer> variables;
    /**
     * Cache of previously computed ranks
     */
    private final Map<Set<Integer>, Integer> rankCache = new ConcurrentHashMap<>();
    /**
     * Sample size for statistical tests
     */
    private final int sampleSize;
    /**
     * The covariance/correlation matrix
     */
    private final SimpleMatrix S;
    /**
     * Alpha level for rank tests
     */
    private double alpha = 0.01;
    /**
     * Whether to include all nodes in the output graph
     */
    private boolean includeAllNodes = false;
    /**
     * Whether to output verbose logging
     */
    private boolean verbose = false;
    /**
     * The most recent clusters found.
     */
    private List<List<Integer>> clusters = new ArrayList<>();
    /**
     * The latent names for the most recent clusters found.
     */
    private List<String> latentNames = new ArrayList<>();
    /**
     * A map that associates a set of integers representing a cluster to its corresponding rank. Each entry in the map
     * defines a cluster and its computed rank, where the rank is typically used to evaluate or compare clusters in the
     * context of hierarchical clustering or other rank-based analyses.
     */
    private Map<Set<Integer>, Integer> clusterToRank;
    /**
     * Represents a mapping between clusters (sets of integers) and their reduced ranks.
     * <p>
     * Each key in the map is a set of integers representing a cluster of variables. Each value in the map is an integer
     * that corresponds to the reduced rank of the associated cluster. This reduced rank may reflect a specific
     * computation or adjustment performed during the analysis, such as handling overlapping clusters or accounting for
     * rank deficiencies.
     * <p>
     * The variable is used as part of the hierarchical clustering and rank estimation processes within the context of
     * trek separation clusters.
     */
    private Map<Set<Integer>, Integer> reducedRank;

    /**
     * Constructs a TrekSeparationClusters2 object, initializes the node and variable lists, and adjusts the covariance
     * matrix with a small scaling factor to ensure numerical stability.
     *
     * @param variables  The list of Node objects representing the variables to be analyzed.
     * @param cov        The covariance matrix of the observed variables.
     * @param sampleSize The number of samples in the dataset.
     */
    public TrekSeparationClusters(List<Node> variables, CovarianceMatrix cov, int sampleSize) {
        this.nodes = new ArrayList<>(variables);
        this.sampleSize = sampleSize;

        this.variables = new ArrayList<>(variables.size());
        for (int i = 0; i < variables.size(); i++) {
            this.variables.add(i);
        }

        this.S = new CovarianceMatrix(cov).getMatrix().getSimpleMatrix();

        System.setProperty("java.util.concurrent.ForkJoinPool.common.parallelism", "<cores>");
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

    // ---- New fast variant of your method ---------------------------------------
    private Set<Set<Integer>> findClustersAtRank(List<Integer> vars, int size, int rank) {
        if (rank + 1 >= variables.size() - (rank + 1)) {
            throw new IllegalArgumentException("rank too high for clusters at rank");
        }

        final int n = vars.size();
        final int k = size;

        // Map List<Integer> -> primitive array for O(1) int access
        final int[] varIds = new int[n];
        for (int i = 0; i < n; i++) varIds[i] = vars.get(i);

        final long[][] C = binom(n, k);
        final long total = C[n][k];

        // Thread-local buffers to avoid per-combination allocations
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

            // fast path rank check (no Set boxing)
            int r = lookupRankFast(ids);   // <-- implement/bridge below
            if (r != rank) return null;

            // only now build the Set<Integer> to return
            Set<Integer> cluster = new HashSet<>(k * 2);
            for (int i = 0; i < k; i++) cluster.add(ids[i]);
            return cluster;
        }).filter(Objects::nonNull).collect(java.util.stream.Collectors.toCollection(java.util.concurrent.ConcurrentHashMap::newKeySet));
    }

    /**
     * Fast overload: takes primitive IDs. For now this just wraps the old method. Replace the body with a true
     * primitive-based implementation when ready.
     */
    private int lookupRankFast(int[] ids) {
        // Temporary bridge: minimal allocation, one small set per match check.
        // (If you can, reimplement lookupRank to consume int[] directly.)
        Set<Integer> s = new java.util.HashSet<>(ids.length * 2);
        for (int x : ids) s.add(x);
        return lookupRank(s);
    }

    /**
     * Searches for latent clusters using specified size and rank parameters.
     *
     * @return Graph containing identified latent structure
     */
    public Graph search() {
        Pair<Map<Set<Integer>, Integer>, Map<Set<Integer>, Integer>> ret = estimateClusters();
        clusterToRank = ret.getFirst();
        reducedRank = ret.getSecond();

        List<Set<Integer>> clusters = new ArrayList<>(clusterToRank.keySet());

        List<Node> latents = defineLatents(clusters, clusterToRank, reducedRank);
        Graph graph = convertSearchGraphClusters(clusters, latents, includeAllNodes);

        this.latentNames = new ArrayList<>();
        for (Node latent : latents) {
            latentNames.add(latent.getName());
        }

        return graph;
    }

    /**
     * Estimates clusters based on the provided specifications, processes overlapping clusters, and returns a set of
     * merged unique clusters.
     *
     * @return a map of sets, where each inner set represents a unique cluster identified and merged according to the
     * given specifications.
     * @throws IllegalArgumentException if the variables used for clustering are not unique.
     */
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

    /**
     * Retrieves a list of all variables.
     *
     * @return A list of integers representing all variables.
     */
    private List<Integer> allVariables() {
        List<Integer> _variables = new ArrayList<>();
        for (int i = 0; i < this.variables.size(); i++) _variables.add(i);
        return _variables;
    }

    /**
     * Sets the alpha value, which may be used as a significance level or parameter threshold in the underlying analysis
     * or computation within the class.
     *
     * @param alpha The alpha value to be set.
     */
    public void setAlpha(double alpha) {
        this.alpha = alpha;
    }

    /**
     * Sets whether all nodes should be included or not.
     *
     * @param includeAllNodes a boolean value where true indicates that all nodes should be included, and false
     *                        indicates otherwise.
     */
    public void setIncludeAllNodes(boolean includeAllNodes) {
        this.includeAllNodes = includeAllNodes;
    }

    /**
     * Sets the verbosity mode for the current operation or process.
     *
     * @param verbose a boolean value where true enables verbose mode, providing detailed log or output information, and
     *                false disables it.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * This method performs a hierarchical clustering process to identify clusters of variables and associate ranks to
     * them. The method iteratively explores clusters of different sizes and ranks, attempting to augment clusters by
     * including overlapping elements or by performing subset evaluations. It maintains two maps: one for the final
     * clusters with their associated ranks and another for reduced-rank clusters obtained through augmentations.
     *
     * @return A pair where the first element is a map of clusters (sets of integers) to their respective ranks, and the
     * second element is a map of clusters to their reduced ranks after augmentations.
     */
    private @NotNull Pair<Map<Set<Integer>, Integer>, Map<Set<Integer>, Integer>> clusterSearchMetaLoop() {
        List<Integer> remainingVars = new ArrayList<>(allVariables());
        clusterToRank = new HashMap<>();
        reducedRank = new HashMap<>();

        for (int rank = 0; rank <= 3; rank++) {
            int size = rank + 1;

            if (Thread.currentThread().isInterrupted()) {
                break;
            }

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
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                Set<Integer> seed = P1.iterator().next();
                P1.remove(seed);

                if (!Collections.disjoint(used, seed)) {
                    continue;
                }

                Set<Integer> cluster = new HashSet<>(seed);

                if (seed.size() >= variables.size() - seed.size()) {
                    continue;
                }

                log("Picking seed from the list: " + toNamesCluster(seed) + " rank = " + lookupRank(seed));

                boolean extended;

                do {
                    extended = false;
                    Iterator<Set<Integer>> it = new HashSet<>(P1).iterator();

                    while (it.hasNext()) {
                        if (Thread.currentThread().isInterrupted()) {
                            break;
                        }

                        Set<Integer> candidate = it.next();
                        if (!Collections.disjoint(used, candidate)) continue;
                        if (Collections.disjoint(candidate, cluster)) continue;
                        if (cluster.containsAll(candidate)) continue;

                        Set<Integer> union = new HashSet<>(cluster);
                        union.addAll(candidate);

                        if (union.size() <= cluster.size()) continue;

                        int rankOfUnion = lookupRank(union);
                        log("For this candidate: " + toNamesCluster(candidate) + ", Trying union: " + toNamesCluster(union) + " rank = " + rankOfUnion);

                        if (rankOfUnion <= rank) {

                            // Accept this union, grow cluster
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
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                for (Set<Integer> C1 : new HashSet<>(newClusters)) {
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }

                    int _size = C1.size();

                    // Look for a cluster in P2 that extends C1 to a cluster C2 of size _size + 1 where the
                    // rank of C2 is 1.
                    for (Set<Integer> _C : P2) {
                        if (Thread.currentThread().isInterrupted()) {
                            break;
                        }

                        Set<Integer> C2 = new HashSet<>(C1);
                        C2.addAll(_C);

                        if (C2.size() >= variables.size() - C2.size()) {
                            continue;
                        }

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
            List<Integer> complement = allVariables();
            complement.removeAll(cluster);

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
     *
     * @param S          the matrix containing the input data for rank testing.
     * @param cluster    the set of integers representing the cluster to be tested.
     * @param sampleSize the sample size to be used in the rank estimation tests.
     * @param alpha      the significance level used in the rank tests.
     * @return true if the cluster fails the subset test according to the rank conditions, false otherwise.
     */
    private boolean failsSubsetTest(SimpleMatrix S, Set<Integer> cluster, int sampleSize, double alpha) {

        List<Integer> C = new ArrayList<>(cluster);

        List<Integer> D = allVariables();
        D.removeAll(cluster);

        { // Rule 1
            SublistGenerator gen0 = new SublistGenerator(C.size(), C.size() / 2);
            int[] choice0;

            while ((choice0 = gen0.next()) != null) {
                List<Integer> C1 = new ArrayList<>();
                for (int i : choice0) {
                    C1.add(C.get(i));
                }

                if (C1.isEmpty()) {
                    continue;
                }

                List<Integer> C2 = new ArrayList<>(C);
                C2.removeAll(C1);

                if (C2.isEmpty()) {
                    continue;
                }

                int[] c1Array = C1.stream().mapToInt(Integer::intValue).toArray();
                int[] c2Array = C2.stream().mapToInt(Integer::intValue).toArray();

                int minpq = Math.min(c1Array.length, c2Array.length);

                int l = clusterToRank.get(cluster);
                l = Math.min(minpq, l);

                if (l < 0) {
                    continue;
                }

                int rank = RankTests.estimateWilksRank(S, c1Array, c2Array, sampleSize, alpha);

                if (rank < l) {
                    log("Deficient! rank(" + toNamesCluster(C1) + ", " + toNamesCluster(C2) + ") has rank "
                        + rank + " < " + l + "; removing " + toNamesCluster(cluster));
                    return true;
                }
            }
        }

        { // Rule 2
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

                Integer l = reducedRank.get(cluster);
                if (l == null) {
                    l = clusterToRank.get(cluster);
                }
                l = Math.min(minpq, l);

                int rank = RankTests.estimateWilksRank(S, _cArray, dArray, sampleSize, alpha);

                if (rank < l) {
                    log("rank(" + toNamesCluster(_C) + " D) = " + rank + " < r = " + l
                        + "; removing cluster " + toNamesCluster(cluster));
                    return true;
                }
            }
        }

        { // Rule 3
            int r = clusterToRank.get(cluster);

            SublistGenerator gen2 = new SublistGenerator(C.size(), Math.min(C.size() - 1, r));
            int[] choice2;

            while ((choice2 = gen2.next()) != null) {
                if (choice2.length < r) continue;

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
                    log("rank(_C = " + toNamesCluster(_C) + ", D | Z = " + toNamesCluster(Z) + ") = " + rZ + "; removing cluster " + toNamesCluster(cluster) + ".");
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Retrieves the rank of a specified cluster. The method first checks if the rank for the given cluster is already
     * computed and stored in a cache. If not, it computes the rank using the defined rank computation method and
     * updates the cache.
     *
     * @param cluster A set of integers representing the cluster for which the rank is to be determined.
     * @return An integer representing the calculated or cached rank of the given cluster.
     * @throws IllegalArgumentException if |C| >= |V \ C}.
     */
    private int lookupRank(Set<Integer> cluster) {
        if (!rankCache.containsKey(cluster)) {
            rankCache.put(cluster, rank(cluster));
        }

        return rankCache.get(cluster);
    }

    /**
     * Converts a collection of integer cluster indices to their corresponding names based on the node mappings and
     * returns them as a string in a formatted name cluster.
     *
     * @param cluster A collection of integer indices representing the cluster elements. Each index corresponds to a
     *                specific node in the nodes mapping.
     * @return A {@code StringBuilder} containing the formatted names cluster as a string. The names are enclosed in
     * square brackets and separated by commas.
     */
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

    /**
     * Converts a set of clusters, where each cluster is represented as a set of integer indices, to a formatted string
     * representation using their corresponding names. This method combines the names of all clusters into a single
     * string, with individual clusters separated by a semicolon.
     *
     * @param clusters A set of sets where each inner set represents a cluster of integers. Each integer corresponds to
     *                 a specific node in the nodes mapping.
     * @return A non-null string containing the formatted cluster names. Each cluster is enclosed in square brackets,
     * its elements are separated by commas, and clusters are separated by semicolons.
     */
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

    /**
     * Computes the rank of the specified cluster using Canonical Correlation Analysis (CCA). This method evaluates the
     * association between the supplied cluster and the complement of the cluster within the given set of variables. The
     * computed rank is determined based on the input covariance matrix, sample size, and alpha level for significance
     * testing.
     *
     * @param cluster A set of integers representing the cluster for which the rank is to be calculated. Each integer
     *                corresponds to a variable index in the analysis.
     * @return An integer representing the estimated rank of the provided cluster.
     */
    private int rank(Set<Integer> cluster) {
        List<Integer> ySet = new ArrayList<>(cluster);
        List<Integer> xSet = new ArrayList<>(variables);
        xSet.removeAll(ySet);

        int[] xIndices = new int[xSet.size()];
        int[] yIndices = new int[ySet.size()];

        for (int i = 0; i < xSet.size(); i++) {
            xIndices[i] = xSet.get(i);
        }

        for (int i = 0; i < ySet.size(); i++) {
            yIndices[i] = ySet.get(i);
        }

        return estimateWilksRank(S, xIndices, yIndices, sampleSize, alpha);
    }

    /**
     * Converts search graph nodes to a Graph object.
     *
     * @param clusters The set of sets of Node objects representing the clusters.
     * @return A Graph object representing the search graph nodes.
     */
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

    /**
     * Defines and creates a list of latent nodes based on the given clusters. Each latent node is assigned a unique
     * identifier and marked as a latent node type.
     *
     * @param clusters A list of sets, where each set represents a cluster of integers. The size of the list determines
     *                 the number of latent nodes to be created.
     * @return A list of Node objects, each representing a latent variable corresponding to a cluster.
     */
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

    /**
     * Logs the provided message if verbose logging is enabled.
     *
     * @param s the message to be logged
     */
    private void log(String s) {
        if (verbose) {
            TetradLogger.getInstance().log(s);
        }
    }

    /**
     * Converts a set of cluster indices into a formatted string representation of the cluster names.
     *
     * @param cluster a set of integer indices representing the cluster
     * @return a string with the cluster names enclosed in curly braces and separated by spaces
     */
    private String toNamesCluster(Set<Integer> cluster) {
        return cluster.stream().map(i -> nodes.get(i).getName()).collect(Collectors.joining(" ", "{", "}"));
    }

    public List<List<Integer>> getClusters() {
        return new ArrayList<>(this.clusters);
    }

    // ===== Greedy builder for Z (size ≤ m) that minimizes rank({a},{b} | Z) =====

    public List<String> getLatentNames() {
        return new ArrayList<>(this.latentNames);
    }
}

