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
import edu.cmu.tetrad.search.test.RankConditionalIndependenceTest;
import edu.cmu.tetrad.search.utils.ClusterUtils;
import edu.cmu.tetrad.util.RankTests;
import edu.cmu.tetrad.util.SublistGenerator;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.math3.util.Pair;
import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.factory.DecompositionFactory_DDRM;
import org.ejml.interfaces.decomposition.EigenDecomposition_F64;
import org.ejml.simple.SimpleMatrix;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static edu.cmu.tetrad.util.RankTests.estimateRccaRank;
import static java.lang.Math.sqrt;

/**
 * The {@code TrekSeparationClusters} class is designed to analyze and identify clusters in data based on trek
 * separation principles. It offers robust functionalities for working with graph structures, covariance matrices, and
 * clustering methodologies. The class utilizes concepts like rank evaluation, structure models, and canonical
 * correlation analysis (CCA) to identify latent variables and interrelationships in data.
 * <p>
 * This class provides capabilities for: - Precomputation of binomial coefficients for efficiency. - Conversion
 * utilities for matrix representations. - Handling and merging of overlapping clusters. - Performing sequential
 * clustering with rank and size constraints. - Integration of latent structure edges into a graph.
 * <p>
 * The class is designed to address complex analytical tasks related to clusters and latent variable modeling.
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
     * Represents the depth value, which is initialized to -1 (unlimited). This variable can be used to indicate a
     * hierarchical level, a measurement, or to track recursion depth where applicable.
     */
    private int depth = -1;
    /**
     * Alpha level for rank tests
     */
    private double alpha = 0.01;
    /**
     * Whether to include a structure model between latents
     */
    private boolean includeStructureModel = false;
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

    /**
     * Converts a SimpleMatrix object into a two-dimensional array of doubles.
     *
     * @param matrix the SimpleMatrix object to be converted
     * @return a two-dimensional array of doubles representing the content of the input matrix
     */
    public static double[][] toDoubleArray(SimpleMatrix matrix) {
        int numRows = matrix.getNumRows();
        int numCols = matrix.getNumCols();
        double[][] result = new double[numRows][numCols];

        for (int i = 0; i < numRows; i++) {
            for (int j = 0; j < numCols; j++) {
                result[i][j] = matrix.get(i, j);
            }
        }

        return result;
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

        return LongStream.range(0, total).parallel()
                .mapToObj(m -> {
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
                    int r = lookupRankFast(ids, vars);   // <-- implement/bridge below
                    if (r != rank) return null;

                    // only now build the Set<Integer> to return
                    Set<Integer> cluster = new HashSet<>(k * 2);
                    for (int i = 0; i < k; i++) cluster.add(ids[i]);
                    return cluster;
                })
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(java.util.concurrent.ConcurrentHashMap::newKeySet));
    }

    /**
     * Fast overload: takes primitive IDs. For now this just wraps the old method. Replace the body with a true
     * primitive-based implementation when ready.
     */
    private int lookupRankFast(int[] ids, List<Integer> vars) {
        // Temporary bridge: minimal allocation, one small set per match check.
        // (If you can, reimplement lookupRank to consume int[] directly.)
        Set<Integer> s = new java.util.HashSet<>(ids.length * 2);
        for (int x : ids) s.add(x);
        return lookupRank(s, vars);
    }

    /**
     * Searches for latent clusters using specified size and rank parameters.
     *
     * @param clusterSpecs int[i][0] is the ith size, int[i][1] is th ith rank.
     * @return Graph containing identified latent structure
     */
    public Graph search(int[][] clusterSpecs, Mode mode) {
        Pair<Map<Set<Integer>, Integer>, Map<Set<Integer>, Integer>> ret = estimateClusters(clusterSpecs, mode);
        Map<Set<Integer>, Integer> clusterToRank = ret.getFirst();
        Map<Set<Integer>, Integer> reducedRank = ret.getSecond();

        List<Set<Integer>> clusters = new ArrayList<>(clusterToRank.keySet());

        List<Node> latents = defineLatents(clusters, clusterToRank, reducedRank);
        Graph graph = convertSearchGraphClusters(clusters, latents, includeAllNodes);

        if (includeStructureModel) {
            addStructureEdges(clusters, latents, graph);
        }

        this.latentNames = new ArrayList<>();
        for (Node latent : latents) {latentNames.add(latent.getName());}

        return graph;
    }

    /**
     * Estimates clusters based on the provided specifications, processes overlapping clusters, and returns a set of
     * merged unique clusters.
     *
     * @param clusterSpecs a 2D array where each row defines cluster specifications. The first element in each row
     *                     specifies the size of the cluster, and the second element specifies the rank.
     * @return a map of sets, where each inner set represents a unique cluster identified and merged according to the
     * given specifications.
     * @throws IllegalArgumentException if the variables used for clustering are not unique.
     */
    private Pair<Map<Set<Integer>, Integer>, Map<Set<Integer>, Integer>> estimateClusters(int[][] clusterSpecs, Mode mode) {
        List<Integer> variables = allVariables();
        if (new HashSet<>(variables).size() != variables.size()) {
            throw new IllegalArgumentException("Variables must be unique.");
        }

        List<Set<Set<Integer>>> clusterList = new ArrayList<>();
        Map<Set<Integer>, Integer> clusterToRanks = new HashMap<>();
        Map<Set<Integer>, Integer> reducedRanks = new HashMap<>();

        if (mode == Mode.METALOOP) {
            Pair<Map<Set<Integer>, Integer>, Map<Set<Integer>, Integer>> ret = clusterSearchMetaLoop();
            clusterToRanks = ret.getFirst();
            reducedRanks = ret.getSecond();
        } else if (mode == Mode.SIZE_RANK) {
            particularSizeAndRank(clusterSpecs, variables, clusterList, clusterToRanks);
            log("Clusters = " + toNamesClusters(new HashSet<>(clusterToRanks.keySet())));
        } else {
            throw new IllegalArgumentException("Mode must be METALOOP or SIZE_RANK");
        }

        this.clusters = new ArrayList<>();

        for (Set<Integer> cluster : clusterToRanks.keySet()) {
            this.clusters.add(new ArrayList<>(cluster));
        }

        return new Pair<>(clusterToRanks, reducedRanks);
    }

    /**
     * Processes cluster specifications to determine clusters of a specified size and rank. This method updates the
     * provided list of clusters and maps each cluster to its associated rank.
     *
     * @param clusterSpecs   a 2D array where each row specifies the size and rank of the clusters to be processed.
     * @param variables      a list of variables used to form the clusters.
     * @param clusterList    a list to store the resulting sets of clusters, where each set contains clusters of a
     *                       specific size and rank.
     * @param clusterToRanks a map that associates each cluster with its corresponding rank.
     */
    private void particularSizeAndRank(int[][] clusterSpecs, List<Integer> variables, List<Set<Set<Integer>>> clusterList, Map<Set<Integer>, Integer> clusterToRanks) {
        for (int i = 0; i < clusterSpecs.length; i++) {
            log("cluster spec: " + Arrays.toString(clusterSpecs[i]));
            int size = clusterSpecs[i][0];
            int rank = clusterSpecs[i][1];
            Map<Set<Integer>, Integer> _clusters = getRunSequentialClusterSearch(variables, size, rank);
            Set<Set<Integer>> baseClusters = new HashSet<>(_clusters.keySet());

            log("For " + Arrays.toString(clusterSpecs[i]) + "\nFound clusters: " + toNamesClusters(_clusters.keySet()));
            clusterList.add(mergeOverlappingClusters(_clusters.keySet(), baseClusters, clusterSpecs[i][0], clusterSpecs[i][1]));
            log("For " + Arrays.toString(clusterSpecs[i]) + "\nMerged clusters: " +
                toNamesClusters(mergeOverlappingClusters(_clusters.keySet(), baseClusters, clusterSpecs[i][0], clusterSpecs[i][1])));

            for (int j = 0; j < i; j++) {
                if (clusterSpecs[j][1] == clusterSpecs[i][1]) {
                    clusterList.get(j).addAll(clusterList.get(i));
                    clusterList.set(j, mergeOverlappingClusters(clusterList.get(j), baseClusters, clusterSpecs[i][0], clusterSpecs[i][1]));
                    log("For " + Arrays.toString(clusterSpecs[i]) + "\nMerging rank " + clusterSpecs[j][1] + ": " + toNamesClusters(clusterList.get(j)));
                    clusterList.get(i).clear();
                }
            }

            for (Set<Set<Integer>> cluster2 : clusterList) {
                for (Set<Integer> cluster3 : cluster2) {
                    clusterToRanks.put(cluster3, rank);
                }
            }
        }
    }

    /**
     * Merges overlapping clusters based on the given criteria and parameters. The merging continues iteratively until
     * no further merging is possible.
     *
     * @param clusters     the initial set of clusters to be merged
     * @param baseClusters the base set of clusters for reference during the merging process
     * @param size         the minimum acceptable size for a cluster to qualify for merging
     * @param rank         the upper limit for the rank of any resulting merged cluster
     * @return a set of merged clusters, with any overlapping clusters combined
     */
    private Set<Set<Integer>> mergeOverlappingClusters(Set<Set<Integer>> clusters,
                                                       Set<Set<Integer>> baseClusters,
                                                       int size, int rank) {
        log("Base clusters: " + toNamesClusters(baseClusters));
        boolean merged;

        do {
            merged = false;
            Set<Set<Integer>> newClusters = new HashSet<>();

            for (Set<Integer> cluster1 : clusters) {
                Set<Integer> mergedCluster = new HashSet<>(cluster1);
                boolean localMerged = false;

                for (Set<Integer> cluster2 : clusters) {
                    if (cluster1 == cluster2) continue;

                    Set<Integer> intersection = new HashSet<>(cluster1);
                    intersection.retainAll(cluster2);

                    if (!intersection.isEmpty() && !mergedCluster.containsAll(cluster2)) {
                        mergedCluster.addAll(cluster2);

                        if (mergedCluster.size() >= variables.size() - mergedCluster.size()) {
                            continue;
                        }

                        if (lookupRank(mergedCluster, variables) != rank) {
                            continue;
                        }

                        localMerged = true;
                    }
                }

                if (mergedCluster.size() >= size) {
                    newClusters.add(mergedCluster);
                }
                if (localMerged) {
                    merged = true;
                }
            }

            if (!newClusters.equals(clusters)) {
                clusters = newClusters;
            } else {
                merged = false;
            }
        } while (merged);

        return clusters;
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
     * Sets whether to include structure models in the analysis or computation.
     *
     * @param includeStructureModel A boolean value indicating whether structure models should be included. If true,
     *                              structure models will be considered in the process; if false, they will be
     *                              excluded.
     */
    public void setIncludeStructureModel(boolean includeStructureModel) {
        this.includeStructureModel = includeStructureModel;
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
     * Performs a sequential cluster search within the given variables, using the specified size and rank criteria. The
     * method iteratively finds clusters that meet the rank condition, merges overlapping or related clusters, and
     * ensures that no nested clusters exist in the final result.
     *
     * @param vars A list of integers representing the variables to analyze.
     * @param size The size of the initial clusters to consider during the search.
     * @param rank The target rank used to determine cluster validity and merging criteria.
     * @return A map from sets to their ranks.
     */
    private @NotNull Map<Set<Integer>, Integer> getRunSequentialClusterSearch(List<Integer> vars, int size, int rank) {
        Set<Set<Integer>> P = findClustersAtRank(vars, size, rank);

        log("P1 = " + toNamesClusters(P));
        Set<Set<Integer>> P1 = new HashSet<>(P);

        Set<Set<Integer>> newClusters = new HashSet<>();
        Set<Integer> used = new HashSet<>();
        Map<Set<Integer>, Integer> clusterToRank = new HashMap<>();

        while (!P1.isEmpty()) {
            Set<Integer> seed = P1.iterator().next();
            P1.remove(seed);

            if (!Collections.disjoint(used, seed)) {
                continue;
            }

            Set<Integer> cluster = new HashSet<>(seed);

            boolean extended;

            do {
                extended = false;
                Iterator<Set<Integer>> it = P1.iterator();

                while (it.hasNext()) {
                    Set<Integer> candidate = it.next();
                    if (!Collections.disjoint(used, candidate)) continue;
                    if (Collections.disjoint(candidate, cluster)) continue;

                    Set<Integer> union = new HashSet<>(cluster);
                    union.addAll(candidate);

                    if (union.size() != cluster.size() + 1) continue;

                    Set<Integer> complement = new HashSet<>(variables);
                    complement.removeAll(union);

                    int minpq = Math.min(union.size(), complement.size());

                    if (minpq != union.size()) {
                        continue;
                    }

                    int rankOfUnion = lookupRank(union, vars);
                    log("Candidate = " + toNamesCluster(candidate) + ", Trying union: " + toNamesCluster(union)
                        + " rank = " + rankOfUnion);

                    if (rankOfUnion == rank) {

                        // Accept this union, grow cluster
                        cluster = union;
                        it.remove();
                        extended = true;
                        break;
                    }
                }
            } while (extended);

            int finalRank = lookupRank(cluster, vars);
            if (finalRank == rank) {
                newClusters.removeIf(cluster::containsAll);  // Avoid nesting
                log("Adding cluster: " + toNamesCluster(cluster) + " rank = " + finalRank);
                newClusters.add(cluster);
                used.addAll(cluster);
            }
        }

        Set<Set<Integer>> P2 = new HashSet<>(P);

        for (Set<Integer> C1 : new HashSet<>(newClusters)) {
            int _size = C1.size();

            // Look for a cluster in P2 that extends C1 to a cluster C2 of size _size + 1 where the
            // rank of C2 is 1.
            for (Set<Integer> _C : P2) {
                Set<Integer> C2 = new HashSet<>(C1);
                C2.addAll(_C);

                if (C2.size() == _size + 1 && lookupRank(C2, vars) == 1) {
                    newClusters.remove(C1);
                    newClusters.add(C2);
                }
            }
        }

        for (Set<Integer> C1 : newClusters) {
            clusterToRank.put(C1, rank);
        }

        removeNested(newClusters);
        log("Merged clusters = " + toNamesClusters(newClusters));
        return clusterToRank;
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
        Map<Set<Integer>, Integer> clusterToRank = new HashMap<>();
        Map<Set<Integer>, Integer> reducedRank = new HashMap<>();

        for (int rank = 0; rank <= 4; rank++) {
            int size = rank + 1;

            if (size >= remainingVars.size() - size) {
                continue;
            }

            log("EXAMINING SIZE " + size + " RANK = " + rank + " REMAINING VARS = " + remainingVars.size());
            Set<Set<Integer>> P = findClustersAtRank(remainingVars, size, rank);
            log("Base clusters for size " + size + " rank " + rank + ": " +
                (P.isEmpty() ? "NONE" : toNamesClusters(P)));
            Set<Set<Integer>> P1 = new HashSet<>(P);

            Set<Set<Integer>> newClusters = new HashSet<>();
            Set<Integer> used = new HashSet<>();

            while (!P1.isEmpty()) {
                Set<Integer> seed = P1.iterator().next();
                P1.remove(seed);

                if (!Collections.disjoint(used, seed)) {
                    continue;
                }

                Set<Integer> cluster = new HashSet<>(seed);

                if (seed.size() >= variables.size() - seed.size()) {
                    continue;
                }

                log("Picking seed from the list: " + toNamesCluster(seed)
                    + " rank = " + lookupRank(seed, variables));

                boolean extended;

                do {
                    extended = false;
                    Iterator<Set<Integer>> it = new HashSet<>(P1).iterator();

                    while (it.hasNext()) {
                        Set<Integer> candidate = it.next();
                        if (!Collections.disjoint(used, candidate)) continue;
                        if (Collections.disjoint(candidate, cluster)) continue;
                        if (cluster.containsAll(candidate)) continue;

                        Set<Integer> union = new HashSet<>(cluster);
                        union.addAll(candidate);

                        if (union.size() <= cluster.size()) continue;

                        Set<Integer> complement = new HashSet<>(variables);
                        complement.removeAll(union);

                        int minpq = Math.min(union.size(), complement.size());

//                        if (minpq != union.size()) {
//                            continue;
//                        }

//                        if (union.size() >= variables.size() - union.size()) {
//                            continue;
//                        }

                        int rankOfUnion = lookupRank(union, variables);
                        log("For this candidate: " + toNamesCluster(candidate) + ", Trying union: " + toNamesCluster(union)
                            + " rank = " + rankOfUnion);

                        if (rankOfUnion <= rank) {

                            // Accept this union, grow cluster
                            cluster = union;
                            it.remove();
                            extended = true;
                            break;
                        }
                    }
                } while (extended);

                int finalRank = lookupRank(cluster, variables);
                if (finalRank == rank) {
                    newClusters.removeIf(cluster::containsAll);  // Avoid nesting
                    log("Adding cluster to new clusters: " + toNamesCluster(cluster) + " rank = " + finalRank);
                    newClusters.add(cluster);
                    used.addAll(cluster);
                    remainingVars.removeAll(cluster);
                }
            }

            log("New clusters for rank " + rank + " size = " + size + ": "
                + (newClusters.isEmpty() ? "NONE" : toNamesClusters(newClusters)));

            Set<Set<Integer>> P2 = new HashSet<>(P);

            log("Now we will try to augment each cluster by one new variable by looking at cluster overlaps again.");
            log("We will repeat this for ranks rank - 1 down to rank 1.");

            boolean didAugment = false;

            for (int _reducedRank = rank - 1; _reducedRank >= 1; _reducedRank--) {

                for (Set<Integer> C1 : new HashSet<>(newClusters)) {
                    int _size = C1.size();

                    // Look for a cluster in P2 that extends C1 to a cluster C2 of size _size + 1 where the
                    // rank of C2 is 1.
                    for (Set<Integer> _C : P2) {
                        Set<Integer> C2 = new HashSet<>(C1);
                        C2.addAll(_C);

                        if (C2.size() >= variables.size() - C2.size()) {
                            continue;
                        }

                        int newRank = lookupRank(C2, variables);

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

            log("New clusters after the augmentation step = " +
                (newClusters.isEmpty() ? "NONE" : toNamesClusters(newClusters)));

            for (Set<Integer> cluster : new ArrayList<>(newClusters)) {
                clusterToRank.put(cluster, rank);
            }

            log("Now we will add all of the new clusters to the set of all discovered clusters.");
            log("All clusters at this point: "
                + (newClusters.isEmpty() ? "NONE" : toNamesClusters(newClusters)));

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

    private static final double EPS_RHO2 = .001; // can tune

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

        int _depth = depth == -1 ? C.size() : depth;
        double epsRho2 = adaptiveEpsRho2(sampleSize, C.size(), D.size());

        SublistGenerator gen0 = new SublistGenerator(C.size(), C.size() / 2);
        int[] choice0;

        while ((choice0 = gen0.next()) != null) {
            if (choice0.length == 0) continue;

            List<Integer> C0 = new  ArrayList<>();
            for (int i : choice0) {C0.add(C.get(i));}
            List<Integer> D0 = new  ArrayList<>(C);
            D0.removeAll(C0);

            int[] c0Array = C0.stream().mapToInt(Integer::intValue).toArray();
            int[] d0Array = D0.stream().mapToInt(Integer::intValue).toArray();

            int rank = RankTests.estimateRccaRank(S, c0Array, d0Array, sampleSize, alpha);

            if (rank == 0) {
                log("Subset " + toNamesCluster(C0) + " compared to rest of cluster " + toNamesCluster(C0) + " has rank 0; removing cluster.");
                return true;
            }
        }

        SublistGenerator gen = new SublistGenerator(C.size(), Math.min(C.size() - 1, _depth));
        int[] choice;

        while ((choice = gen.next()) != null) {
            if (choice.length == 0) continue;

            List<Integer> W = new ArrayList<>();
            for (int i : choice) {
                W.add(C.get(i));
            }

            List<Integer> _C = new ArrayList<>(C);
            _C.removeAll(W);

            int[] _cArray = _C.stream().mapToInt(Integer::intValue).toArray();
            int[] dArray = D.stream().mapToInt(Integer::intValue).toArray();

            int rank = RankTests.estimateRccaRank(S, _cArray, dArray, sampleSize, alpha);

            if (rank == 0) {
                log("Subset " + toNamesCluster(_C) + " of cluster " + toNamesCluster(C) + " has rank 0; removing cluster.");
                return true;
            }

//            boolean tinyEffect = RankTests.maxCanonicalCorrSq(
//                    S, _cArray, dArray) <= epsRho2;
//
//            if (tinyEffect) {
//                log("Subset " + toNamesCluster(_C) + " of cluster " + toNamesCluster(C)
//                    + " has tiny effect" + (" (ρ²≤" + epsRho2 + ")") + ".");
//                return true;
//            }
        }

        SublistGenerator gen2 = new SublistGenerator(C.size(), Math.min(C.size() - 1, _depth));
        int[] choice2;

        while ((choice2 = gen2.next()) != null) {
            if (choice2.length == 0) continue;

            List<Integer> Z = new ArrayList<>();
            for (int i : choice2) {
                Z.add(C.get(i));
            }

            List<Integer> _C = new ArrayList<>(C);
            _C.removeAll(Z);

            int[] _cArray = _C.stream().mapToInt(Integer::intValue).toArray();
            int[] dArray = D.stream().mapToInt(Integer::intValue).toArray();
            int[] zArray = Z.stream().mapToInt(Integer::intValue).toArray();

            double rZ = RankTests.estimateRccaRankConditioned(S, _cArray, dArray, zArray, sampleSize, alpha);

            if (rZ == 0) {
                log("Subset " + toNamesCluster(_C) + " of cluster " + toNamesCluster(C)
                    + " has rank 0 conditional on " + toNamesCluster(Z) + "; removing cluster.");
                return true;
            }

//            boolean tinyEffectZ = RankTests.maxCanonicalCorrSqConditioned(
//                    S, _cArray, dArray, zArray) <= epsRho2;
//
//            if (tinyEffectZ) {
//                log("Subset " + toNamesCluster(_C) + " of cluster " + toNamesCluster(C)
//                    + " has rank tiny effect conditional on " + toNamesCluster(Z)
//                    + (" (ρ²≤" + epsRho2 + ")") + ".");
//                return true;
//            }
        }

        return false;
    }

    /**
     * Removes nested clusters from a set of merged clusters. A cluster is considered nested if it is a subset of
     * another cluster within the set. The method iteratively checks and removes such nested clusters until no changes
     * occur.
     *
     * @param mergedClusters A set of sets, where each inner set represents a cluster of integers. The input is expected
     *                       to potentially contain nested clusters, which will be removed to leave only non-nested
     *                       clusters.
     */
    private void removeNested(Set<Set<Integer>> mergedClusters) {
        boolean _changed;
        do {
            _changed = mergedClusters.removeIf(
                    sub -> mergedClusters.stream()
                            .anyMatch(cluster -> !cluster.equals(sub) && cluster.containsAll(sub))
            );
        } while (_changed);
    }

    /**
     * Adds structure edges to the given graph based on provided clusters and latent nodes. The method processes
     * clusters, derives a structure graph using a permutation search, and adds the resulting edges to the specified
     * graph.
     *
     * @param clusters The list of sets where each set represents a cluster of integers that denote related elements.
     * @param latents  The list of latent nodes to be used for building the latent structure and covariance matrix.
     * @param graph    The graph to which the derived structure edges will be added.
     */
    private void addStructureEdges(List<Set<Integer>> clusters, List<Node> latents, Graph graph) {
        try {
            List<List<Integer>> _clusters = new ArrayList<>();
            for (Set<Integer> cluster : clusters) {
                _clusters.add(new ArrayList<>(cluster));
            }

            List<SimpleMatrix> eigenvectors = LatentGraphBuilder.extractFirstEigenvectors(S, _clusters);
            SimpleMatrix latentsCov = LatentGraphBuilder.latentLatentCorrelationMatrix(S, _clusters, eigenvectors);
            CovarianceMatrix cov = new CovarianceMatrix(latents, toDoubleArray(latentsCov), sampleSize);
            Pc pc = new Pc(new RankConditionalIndependenceTest(cov, alpha));
            pc.setDepth(depth);
            Graph structureGraph = pc.search();

            for (Edge edge : structureGraph.getEdges()) {
                graph.addEdge(edge);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Retrieves the rank of a specified cluster. The method first checks if the rank for the given cluster is already
     * computed and stored in a cache. If not, it computes the rank using the defined rank computation method and
     * updates the cache.
     *
     * @param cluster A set of integers representing the cluster for which the rank is to be determined.
     * @param vars    A reference list of variables to check the size of the cluster against. It should be the case that
     *                |C| < |V \ C|.
     * @return An integer representing the calculated or cached rank of the given cluster.
     * @throws IllegalArgumentException if |C| >= |V \ C}.
     */
    private int lookupRank(Set<Integer> cluster, List<Integer> vars) {
//        if (cluster.size() >= vars.size() - cluster.size()) {
//            throw new IllegalArgumentException("Cluster is too large.");
//        }

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

        return estimateRccaRank(S, xIndices, yIndices, sampleSize, alpha);
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
    private List<Node> defineLatents(List<Set<Integer>> clusters, Map<Set<Integer>, Integer> ranks,
                                     Map<Set<Integer>, Integer> reducedRank) {
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
        return cluster.stream()
                .map(i -> nodes.get(i).getName())
                .collect(Collectors.joining(" ", "{", "}"));
    }

    /**
     * Sets the depth value for this object.
     *
     * @param depth the depth value to be set, represented as an integer
     */
    public void setDepth(int depth) {
        this.depth = depth;
    }

    public List<List<Integer>> getClusters() {
        return new ArrayList<>(this.clusters);
    }

    public List<String> getLatentNames() {
        return new ArrayList<>(this.latentNames);
    }

    /**
     * The Mode enum represents different operational modes.
     * It defines constants that can be used to specify particular behaviors or configurations.
     *
     * Enum Constants:
     * METALOOP - Represents a specific mode for looping operations or settings.
     * SIZE_RANK - Represents a mode related to size or ranking configurations.
     */
    public enum Mode {
        /**
         * Represents a specific operational mode used for looping operations or settings.
         * This constant is part of the Mode enum and is utilized to define behavior or configuration
         * associated with looping functionality within the application.
         */
        METALOOP,

        /**
         * Represents a specific operational mode associated with size or ranking configurations.
         * This constant is part of the Mode enum and is utilized to define behavior
         * or configurations related to size or ranking within the application.
         */
        SIZE_RANK}


    // ===== Greedy builder for Z (size ≤ m) that minimizes rank({a},{b} | Z) =====

    /**
     * The LatentGraphBuilder class provides methods for processing and analyzing latent structures in matrices using
     * eigenvector-based techniques. These methods are particularly useful for extracting important patterns and
     * relationships within data, such as through clustering and correlation matrix computation.
     */
    private static class LatentGraphBuilder {
        public static List<SimpleMatrix> extractFirstEigenvectors(SimpleMatrix S, List<List<Integer>> clusters) {
            List<SimpleMatrix> eigenvectors = new ArrayList<>();

            for (List<Integer> cluster : clusters) {
                SimpleMatrix submatrix = extractSubmatrix(S, cluster);

                EigenDecomposition_F64<DMatrixRMaj> eig = DecompositionFactory_DDRM.eig(submatrix.getNumCols(), true);
                eig.decompose(submatrix.getDDRM());

                // Get eigenvector corresponding to largest eigenvalue
                double maxEigenvalue = Double.NEGATIVE_INFINITY;
                SimpleMatrix principalEigenvector = null;

                for (int i = 0; i < eig.getNumberOfEigenvalues(); i++) {
                    if (eig.getEigenvalue(i).isReal()) {
                        double value = eig.getEigenvalue(i).getReal();
                        if (value > maxEigenvalue) {
                            maxEigenvalue = value;
                            principalEigenvector = SimpleMatrix.wrap(eig.getEigenVector(i));
                        }
                    }
                }

                eigenvectors.add(principalEigenvector);
            }

            return eigenvectors;
        }

        /**
         * Computes the latent-latent correlation matrix for a given similarity matrix, clusters, and corresponding
         * eigenvectors. The method calculates pairwise correlations between latent variables associated with different
         * clusters.
         *
         * @param S            The similarity matrix, assumed to be square and symmetric.
         * @param clusters     A list of clusters, where each cluster is represented as a list of indices indicating the
         *                     rows and columns of the similarity matrix that belong to the cluster.
         * @param eigenvectors A list of eigenvector matrices, where each matrix corresponds to the eigenvectors
         *                     calculated for each cluster.
         * @return A symmetric matrix representing the pairwise correlations between latents associated with the
         * specified clusters.
         */
        public static SimpleMatrix latentLatentCorrelationMatrix(
                SimpleMatrix S,
                List<List<Integer>> clusters,
                List<SimpleMatrix> eigenvectors) {

            int K = clusters.size();
            SimpleMatrix R = new SimpleMatrix(K, K);

            for (int i = 0; i < K; i++) {
                for (int j = i; j < K; j++) {
                    List<Integer> ci = clusters.get(i);
                    List<Integer> cj = clusters.get(j);
                    SimpleMatrix vi = eigenvectors.get(i);
                    SimpleMatrix vj = eigenvectors.get(j);

                    SimpleMatrix Sij = extractCrossBlock(S, ci, cj);
                    SimpleMatrix Sii = extractSubmatrix(S, ci);
                    SimpleMatrix Sjj = extractSubmatrix(S, cj);

                    double numerator = vi.transpose().mult(Sij).mult(vj).get(0);
                    double denomLeft = vi.transpose().mult(Sii).mult(vi).get(0);
                    double denomRight = vj.transpose().mult(Sjj).mult(vj).get(0);

                    double corr = numerator / sqrt(denomLeft * denomRight);
                    R.set(i, j, corr);
                    R.set(j, i, corr); // symmetric
                }
            }

            return R;
        }

        /**
         * Helper: Extract a submatrix of S with rows in A and cols in B
         */
        private static SimpleMatrix extractCrossBlock(SimpleMatrix S, List<Integer> rows, List<Integer> cols) {
            SimpleMatrix result = new SimpleMatrix(rows.size(), cols.size());
            for (int i = 0; i < rows.size(); i++) {
                for (int j = 0; j < cols.size(); j++) {
                    result.set(i, j, S.get(rows.get(i), cols.get(j)));
                }
            }
            return result;
        }

        /**
         * Helper: Extract submatrix of S using indices
         */
        private static SimpleMatrix extractSubmatrix(SimpleMatrix S, List<Integer> indices) {
            return extractCrossBlock(S, indices, indices);
        }
    }

    /** Adaptive ε for the tiny-effect gate on ρ_max^2.
     *  Heuristic: ε = clamp( c * sqrt((|C| + |D|) / max(50, n)), [ε_min, ε_max] ).
     *  - Grows when n is small or D is large (more mercy).
     *  - Shrinks when n is large (stricter).
     */
    public static double adaptiveEpsRho2(int n, int cSize, int dSize) {
        return adaptiveEpsRho2(n, cSize, dSize, /*c=*/1.0, /*epsMin=*/0.02, /*epsMax=*/0.08);
    }

    /** Fully parameterized version. */
    public static double adaptiveEpsRho2(int n, int cSize, int dSize,
                                         double c, double epsMin, double epsMax) {
        if (n <= 0) n = 50;                  // guard
        if (c <= 0) c = 1.0;
        if (epsMin > epsMax) { double t = epsMin; epsMin = epsMax; epsMax = t; }

        double denom = Math.max(50.0, (double) n);
        double base  = Math.sqrt(((double)cSize + (double)dSize) / denom);
        double eps   = c * base;

        // clamp
        if (eps < epsMin) eps = epsMin;
        if (eps > epsMax) eps = epsMax;
        return eps;
    }
}

