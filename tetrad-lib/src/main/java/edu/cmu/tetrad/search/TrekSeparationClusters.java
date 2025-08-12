/**
 * Implements Trek Separation algorithm for finding latent variable clusters. This class analyzes covariance matrices to
 * identify clusters of observed variables that share common latent parents. It uses rank-based tests to determine trek
 * separations between variable sets.
 * <p>
 * Copyright (C) 1998-2022 by Peter Spirtes, Richard Scheines, Joseph Ramsey, and Clark Glymour.
 * <p>
 * This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any later
 * version.
 */
package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.test.RankConditionalIndependenceTest;
import edu.cmu.tetrad.search.utils.ClusterUtils;
import edu.cmu.tetrad.search.utils.SepsetMap;
import edu.cmu.tetrad.util.*;
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
 * The TrekSeparationClusters2 class implements methods for detecting and analyzing clusters of variables using trek
 * separation tests. This class is designed to identify latent structure in a given covariance matrix with capabilities
 * for clustering, ranking, and graph construction.
 * <p>
 * It uses various parameters such as rank, penalties, and testing settings to guide the process and adjust the behavior
 * of the clustering algorithm. The main functionalities include searching for latent clusters, generating random
 * clusters, identifying disjoint clusters, and constructing resulting graphical models.
 */
public class TrekSeparationClusters {
    // ---- Binomial cache (reuse across calls) -----------------------------------
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
    private final Graph cpdag;
    /**
     * The covariance/correlation matrix
     */
    private final SimpleMatrix S;
    Map<Set<Integer>, Integer> residualRankByCluster = new HashMap<>();
    /**
     * The sepsets from the rank PC search.
     */
    private SepsetMap sepsets;
    private int depth = -1;
    /**
     * Alpha level for rank tests
     */
    private double alpha = 0.01;
    /**
     * Whether to include structure model between latents
     */
    private boolean includeStructureModel = false;
    /**
     * Whether to include all nodes in output graph
     */
    private boolean includeAllNodes = false;
    /**
     * Whether to output verbose logging
     */
    private boolean verbose = false;


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

        try {
            RankConditionalIndependenceTest test = new RankConditionalIndependenceTest(cov, alpha);
            Pc pc = new Pc(test);
            pc.setDepth(depth);
            pc.setStable(false);
            pc.setUseMaxPOrientation(false);
            pc.setDepth(depth);
            cpdag = pc.search();
            sepsets = pc.getSepsets();

//            SemBicScore score = new SemBicScore(cov, 2);
//            cpdag = new PermutationSearch(new Boss(score)).search();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

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

    // Pascal triangle up to n choose k (inclusive on n)
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

    // --- Helper: max |corr(i, j)| over j in J ---
    private static double maxAbsCorrToSet(SimpleMatrix S, int i, int[] J) {
        double max = 0.0;
        double sii = S.get(i, i);
        for (int j : J) {
            double s = Math.abs(S.get(i, j) / Math.sqrt(Math.max(1e-16, sii * S.get(j, j))));
            if (s > max) max = s;
        }
        return max;
    }

    /**
     * Prune observed-observed edges INSIDE a latent cluster C that are explained by the latent(s).
     *
     * @param S     covariance over all variables
     * @param C     indices (int[]) for the cluster
     * @param rhat  residual rank (latent dimensionality estimate), usually 1
     * @param graph CPDAG/skeleton over observed nodes (edges will be removed here)
     * @param nodes node list aligned with S indices
     * @param n     sample size
     * @param alpha test size for rank-0 Wilks test
     * @return list of removed edges (as pairs of node indices)
     */
    public static List<int[]> pruneWithinClusterEdgesByLatent(
            SimpleMatrix S, int[] C, int rhat, Graph graph, List<Node> nodes,
            int n, double alpha) {

        final double EPS_RHO2 = 0.05; // effect-size gate; set <=0 to disable
        List<int[]> removed = new ArrayList<>();
        if (C.length < 2) return removed;

        for (int i = 0; i < C.length; i++) {
            int xi = C[i];
            Node X = nodes.get(xi);
            Set<Node> adjX = new HashSet<>(graph.getAdjacentNodes(X));

            for (int j = i + 1; j < C.length; j++) {
                int yi = C[j];
                Node Y = nodes.get(yi);
                if (!adjX.contains(Y)) continue; // no edge present

                // Candidate pool Z = C \ {X,Y}
                int[] pool = Arrays.stream(C).filter(v -> v != xi && v != yi).toArray();
                if (pool.length == 0) continue;

                // Required size target; adapt to what we have
                int mTarget = Math.max(1, rhat);           // A: relaxed
                int mAvail = Math.min(pool.length, mTarget);

                boolean delete = false;

                // 1) Singletons
                for (int z : pool) {
                    int rank = RankTests.estimateRccaRankConditioned(S,
                            new int[]{xi}, new int[]{yi}, new int[]{z}, n, alpha);
                    if (rank == 0 || passesEffectGate(S, xi, yi, new int[]{z}, n, EPS_RHO2)) {
                        delete = true;
                        break;
                    }
                }
                if (delete) {
                    graph.removeEdge(X, Y);
                    removed.add(new int[]{xi, yi});
                    continue;
                }

                // 2) Pairs (if we wanted m >= 2 and have at least 2 in pool)
                if (mAvail >= 2 && pool.length >= 2) {
                    // C: exhaustive over pairs (small pools), not greedy
                    for (int p = 0; p < pool.length && !delete; p++) {
                        for (int q = p + 1; q < pool.length && !delete; q++) {
                            int[] Z = new int[]{pool[p], pool[q]};
                            int rank = RankTests.estimateRccaRankConditioned(S,
                                    new int[]{xi}, new int[]{yi}, Z, n, alpha);
                            if (rank == 0 || passesEffectGate(S, xi, yi, Z, n, EPS_RHO2)) {
                                delete = true;
                            }
                        }
                    }
                    if (delete) {
                        graph.removeEdge(X, Y);
                        removed.add(new int[]{xi, yi});
                    }
                }
            }
        }
        return removed;
    }

    // Effect-size gate: require max residual canonical correlation^2 <= eps
    private static boolean passesEffectGate(SimpleMatrix S, int a, int b, int[] Z, int n, double epsRho2) {
        if (epsRho2 <= 0) return false;
        // You need a variant that returns canonical correlations after conditioning.
        // Quick hack: treat Wilks’ lambda with tiny stat as "small effect". If you can, expose the largest rho^2.
        double rho2 = RankTests.maxCanonicalCorrSqConditioned(S, new int[]{a}, new int[]{b}, Z, n);
        return rho2 <= epsRho2;
    }

    /**
     * Prune observed-observed edges BETWEEN cluster C and complement D that are explained by the latent(s).
     *
     * @param S     covariance over all variables
     * @param C     indices (int[]) for the cluster
     * @param D     indices (int[]) for V \ C
     * @param rhat  residual rank (latent dimensionality estimate), usually 1
     * @param graph CPDAG/skeleton over observed nodes (edges will be removed here)
     * @param nodes node list aligned with S indices
     * @param n     sample size
     * @param alpha test size for rank-0 Wilks test
     * @return list of removed edges (as pairs of node indices)
     */
    public static List<int[]> pruneCrossBoundaryEdgesByLatent(
            SimpleMatrix S, int[] C, int[] D, int rhat, Graph graph, List<Node> nodes,
            int n, double alpha) {

        int m = Math.max(2, rhat); // size of Z from C\{X}
        List<int[]> removed = new ArrayList<>();
        if (C.length < 2) return removed;

        // Precompute adjacency sets for quick edge checks
        Map<Node, Set<Node>> adj = new HashMap<>();
        for (Node u : nodes) adj.put(u, new HashSet<>(graph.getAdjacentNodes(u)));

        for (int xi : C) {
            Node X = nodes.get(xi);
            // candidate pool Z ⊆ C \ {X}
            int[] pool = Arrays.stream(C).filter(v -> v != xi).toArray();
            if (pool.length < m) continue;

            for (int wi : D) {
                Node W = nodes.get(wi);
                if (!adj.get(X).contains(W)) continue; // no edge

                // Greedy Z from C\{X}, size up to m, to reduce rank({X},{W} | Z)
                int[] Zgreedy = greedyZForPair(S, xi, wi, pool, m, n, alpha);

                int rank = RankTests.estimateRccaRankConditioned(S,
                        new int[]{xi}, new int[]{wi}, Zgreedy, n, alpha);

                if (rank == 0) {
                    graph.removeEdge(X, W);
                    removed.add(new int[]{xi, wi});
                }
            }
        }
        return removed;
    }

    private static int[] greedyZForPair(SimpleMatrix S, int a, int b, int[] pool, int m, int n, double alpha) {
        // Order pool deterministically: by degree proxy (max|corr| to {a,b}) then index
        int[] ordered = Arrays.stream(pool).boxed()
                .sorted((i, j) -> {
                    double si = maxAbsCorrToSet(S, i, new int[]{a, b});
                    double sj = maxAbsCorrToSet(S, j, new int[]{a, b});
                    int cmp = Double.compare(sj, si); // desc
                    if (cmp != 0) return cmp;
                    return Integer.compare(i, j);
                }).mapToInt(x -> x).toArray();

        List<Integer> Z = new ArrayList<>();
        int bestRank = RankTests.estimateRccaRankConditioned(S, new int[]{a}, new int[]{b}, new int[]{}, n, alpha);

        while (Z.size() < m && bestRank > 0) {
            int pick = -1, pickRank = bestRank;
            for (int z : ordered) {
                if (Z.contains(z)) continue;
                int[] trial = new int[Z.size() + 1];
                for (int i = 0; i < Z.size(); i++) trial[i] = Z.get(i);
                trial[trial.length - 1] = z;
                int rZ = RankTests.estimateRccaRankConditioned(S, new int[]{a}, new int[]{b}, trial, n, alpha);
                if (rZ < pickRank || (rZ == pickRank && (pick == -1 || z < pick))) {
                    pickRank = rZ;
                    pick = z;
                    if (pickRank == 0) break; // early win
                }
            }
            if (pick < 0) break; // no improvement
            Z.add(pick);
            bestRank = pickRank;
        }
        return Z.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * Checks if a TSC cluster C survives explain-away conditioning using PC sepsets.
     *
     * @param S       full covariance matrix over all vars
     * @param C       indices (int[]) for cluster variables
     * @param D       indices (int[]) for complement variables (V \ C)
     * @param n       sample size
     * @param alpha   significance level for rank test
     * @param nodes   list of Node objects in consistent index order
     * @param sepsets PC sepset map: (Node, Node) -> List<Node>
     * @param kmax    maximum size of sepset to try
     * @param cpdag
     * @return true if no PC sepset kills the rank, false otherwise
     */
    public boolean survivesSepsetExplainAway(SimpleMatrix S,
                                             int[] C,
                                             int[] D,
                                             int n,
                                             double alpha,
                                             List<Node> nodes,
                                             SepsetMap sepsets,
                                             int kmax, Graph cpdag, int dummy) {

        // Baseline rank
        int rBase = estimateRccaRank(S, C, D, n, alpha);
        residualRankByCluster.put(getIntSet(C), rBase);
        if (rBase == 0) return false; // already dead

        // Try each PC sepset crossing the C / D boundary
        for (int uPrime : C) {
            Node uNode = nodes.get(uPrime);

            for (int vPrime : D) {
                Node vNode = nodes.get(vPrime);

                Set<Node> sep = sepsets.get(uNode, vNode);
                if (sep == null) continue; // adjacent: no separating set

                // Convert sepset Nodes to integer indices
                int[] Z = sep.stream()
                        .mapToInt(nodes::indexOf)
                        .toArray();

                if (Z.length > kmax) continue; // skip if too big

//                int rCond = RccaSetUtils.rankSetVsSet(S, C, D, Z, n, alpha);
                int rCond = RankTests.estimateRccaRankConditioned(S, C, D, Z, n, alpha);

                if (rCond < rBase) {
                    rBase = rCond;
                    residualRankByCluster.put(getIntSet(C), rBase);
                }

                if (rCond == 0) {
                    // Found a small observed set that kills the rank: reject cluster
                    return false;
                }
            }
        }

        // Survived all explain-away attempts
        return true;
    }

    public boolean survivesSepsetExplainAway2(SimpleMatrix S,
                                              int[] C,
                                              int[] D,
                                              int n,
                                              double alpha,
                                              List<Node> nodes,
                                              SepsetMap sepsets,
                                              int kmax,
                                              Graph cpdag,
                                              int poolCapOutside) {

        // ---- Baseline rank ----
        int rBase = RccaSetUtils.rankSetVsSet(S, C, D, /*Z=*/new int[0], n, alpha);
        residualRankByCluster.put(getIntSet(C), rBase);
        if (rBase == 0) return false;

        // ---- Build OUTSIDE-C pool: neighbors from CPDAG + union of sepsets across the boundary ----
        int[] poolOutside = buildOutsidePool(C, D, nodes, cpdag, sepsets, S, poolCapOutside);

        if (poolOutside.length == 0) {
            // Nothing to condition on outside C; fall back to your current behavior
            return true;
        }

        // ---- Step 2: singletons from outside pool ----
        int bestZ = -1;
        int rBest = rBase;
        List<Integer> improvers = new ArrayList<>();

        for (int z : poolOutside) {
            int rZ = RccaSetUtils.rankSetVsSet(S, C, D, new int[]{z}, n, alpha);
            if (rZ < rBase) improvers.add(z);
            if (rZ < rBest || (rZ == rBest && (bestZ < 0 || z < bestZ))) {
                rBest = rZ;
                bestZ = z;
            }
            if (rZ == 0) {
                residualRankByCluster.put(getIntSet(C), 0);
                return false;
            }
        }

        residualRankByCluster.put(getIntSet(C), rBest);
        if (kmax <= 1 || rBest == 0) return (rBest > 0);

        // ---- Step 3: greedy add up to kmax from the *outside* improvers ----
        int[] searchPool = (improvers.isEmpty() ? poolOutside : improvers.stream().mapToInt(Integer::intValue).toArray());

        List<Integer> Z = new ArrayList<>();
        if (bestZ >= 0) Z.add(bestZ);
        int rRes = rBest;

        while (Z.size() < kmax && rRes > 0) {
            int pick = -1, best = rRes;
            for (int z : searchPool) {
                if (Z.contains(z)) continue;
                int[] Zplus = new int[Z.size() + 1];
                for (int i = 0; i < Z.size(); i++) Zplus[i] = Z.get(i);
                Zplus[Zplus.length - 1] = z;

                int rZ = RccaSetUtils.rankSetVsSet(S, C, D, Zplus, n, alpha);
                if (rZ < best || (rZ == best && (pick < 0 || z < pick))) {
                    best = rZ;
                    pick = z;
                }
                if (rZ == 0) {
                    residualRankByCluster.put(getIntSet(C), 0);
                    return false;
                }
            }
            if (pick < 0) break;
            Z.add(pick);
            rRes = best;
            residualRankByCluster.put(getIntSet(C), rRes);
        }

        // Survived all explain-away attempts with outside candidates
        return (rRes > 0);
    }

    private int[] buildOutsidePool(int[] C,
                                   int[] D,
                                   List<Node> nodes,
                                   Graph cpdag,
                                   SepsetMap sepsets,
                                   SimpleMatrix S,
                                   int cap) {

        Set<Integer> inC = Arrays.stream(C).boxed().collect(Collectors.toSet());
        Set<Integer> inD = Arrays.stream(D).boxed().collect(Collectors.toSet());
        Set<Integer> pool = new LinkedHashSet<>();

        // (a) CPDAG neighbors of C that lie in D (outside)
        for (int u : C) {
            Node uNode = nodes.get(u);
            for (Node nb : cpdag.getAdjacentNodes(uNode)) {
                int z = nodes.indexOf(nb);
                if (!inC.contains(z)) pool.add(z);  // outside C (mostly in D)
            }
        }

        // (b) Union of sepsets across the C–D boundary
        for (int u : C) {
            Node uNode = nodes.get(u);
            for (int v : D) {
                Node vNode = nodes.get(v);
                Set<Node> sep = sepsets.get(uNode, vNode);
                if (sep == null) continue;
                for (Node zNode : sep) {
                    int z = nodes.indexOf(zNode);
                    if (!inC.contains(z)) pool.add(z); // keep outside C
                }
            }
        }

        if (pool.isEmpty()) return new int[0];

        // Rank candidates by max |corr| to C∪D (desc), then by index (asc)
        int[] side = new int[C.length + D.length];
        System.arraycopy(C, 0, side, 0, C.length);
        System.arraycopy(D, 0, side, C.length, D.length);

        List<Integer> ranked = pool.stream()
                .sorted((i, j) -> {
                    double si = maxAbsCorrToSet(S, i, side);
                    double sj = maxAbsCorrToSet(S, j, side);
                    int cmp = Double.compare(sj, si); // desc
                    if (cmp != 0) return cmp;
                    return Integer.compare(i, j);
                })
                .limit(Math.max(0, cap))
                .collect(Collectors.toList());

        return ranked.stream().mapToInt(Integer::intValue).toArray();
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

//                    {
//                        List<Integer> complement = allVariables();
//                        complement.removeAll(cluster);
//                        int[] _cluster = cluster.stream().mapToInt(Integer::intValue).toArray();
//                        int[] _complement = complement.stream().mapToInt(Integer::intValue).toArray();
//
//                        if (!survivesSepsetExplainAway(S, _cluster, _complement, sampleSize, alpha, nodes, sepsets, depth == -1 ? 100 : depth)) {
//                            return null;
//                        }
//                    }

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

//        for (Edge edge : cpdag.getEdges()) {
//            graph.addEdge(edge);
//        }

//        for (Set<Integer> cluster : clusters) {
//            int[] C = cluster.stream().mapToInt(Integer::intValue).toArray();
//
//            int rhat = residualRankByCluster.get(cluster); // <- from DHC; or however you stored it
//            // Prune within-cluster observed edges (removes in-place; returns list for logging)
//            List<int[]> removedWithin = pruneWithinClusterEdgesByLatent(
//                    S, C, rhat, graph, nodes, sampleSize, alpha);
//
//            // (optional) log
//            // removedWithin.forEach(e -> System.out.println("Removed within: " + nodes.get(e[0]) + "—" + nodes.get(e[1])));
//        }
//
//        // Build D = V \ C for each cluster (observed-only)
//        Set<Integer> allObserved = new HashSet<>(allVariables()); // ensure latents are excluded
//
//        for (Set<Integer> cluster : clusters) {
//            int[] C = cluster.stream().mapToInt(Integer::intValue).toArray();
//
//            List<Integer> comp = new ArrayList<>(allObserved);
//            comp.removeAll(cluster);
//            int[] D = comp.stream().mapToInt(Integer::intValue).toArray();
//
//            int rhat = residualRankByCluster.get(cluster);
//            List<int[]> removedCross = pruneCrossBoundaryEdgesByLatent(
//                    S, C, D, rhat, graph, nodes, sampleSize, alpha);
//
//            // (optional) log
//            // removedCross.forEach(e -> System.out.println("Removed cross: " + nodes.get(e[0]) + "—" + nodes.get(e[1])));
//        }

//        new MeekRules().orientImplied(graph);

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

            for (int i = 0; i < clusterSpecs.length; i++) {
                log("cluster spec: " + Arrays.toString(clusterSpecs[i]));
                int size = clusterSpecs[i][0];
                int rank = clusterSpecs[i][1];
                Map<Set<Integer>, Integer> _clusters = getRunSequentialClusterSearch(variables, size, rank);
                Set<Set<Integer>> baseClusters = new HashSet<>(_clusters.keySet());

                log("For " + Arrays.toString(clusterSpecs[i]) + "\nFound clusters: " + toNamesClusters(_clusters.keySet()));
                clusterList.add(mergeOverlappingClusters(_clusters.keySet(), baseClusters, clusterSpecs[i][0], clusterSpecs[i][1], variables));
                log("For " + Arrays.toString(clusterSpecs[i]) + "\nMerged clusters: " +
                    toNamesClusters(mergeOverlappingClusters(_clusters.keySet(), baseClusters, clusterSpecs[i][0], clusterSpecs[i][1], variables)));

                for (int j = 0; j < i; j++) {
                    if (clusterSpecs[j][1] == clusterSpecs[i][1]) {
                        clusterList.get(j).addAll(clusterList.get(i));
                        clusterList.set(j, mergeOverlappingClusters(clusterList.get(j), baseClusters, clusterSpecs[i][0], clusterSpecs[i][1], variables));
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

            log("Clusters = " + toNamesClusters(new HashSet<>(clusterToRanks.keySet())));
        } else {
            throw new IllegalArgumentException("Mode must be METALOOP or SIZE_RANK");
        }

        return new Pair<>(clusterToRanks, reducedRanks);
    }

    /**
     * Merges overlapping clusters based on the given criteria and parameters. The merging continues iteratively until
     * no further merging is possible.
     *
     * @param clusters     the initial set of clusters to be merged
     * @param baseClusters the base set of clusters for reference during the merging process
     * @param size         the minimum acceptable size for a cluster to qualify for merging
     * @param rank         the upper limit for the rank of any resulting merged cluster
     * @param remaining    The set of unused variables.
     * @return a set of merged clusters, with any overlapping clusters combined
     */
    private Set<Set<Integer>> mergeOverlappingClusters(Set<Set<Integer>> clusters,
                                                       Set<Set<Integer>> baseClusters,
                                                       int size, int rank, List<Integer> remaining) {
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
     * @param alpha The alpha value to be set. It should be provided as a double, and typically represents a probability
     *              level or tuning parameter depending on the context of its use.
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

    private @NotNull Pair<Map<Set<Integer>, Integer>, Map<Set<Integer>, Integer>> clusterSearchMetaLoop() {
        List<Integer> remainingVars = new ArrayList<>(allVariables());
        Map<Set<Integer>, Integer> clusterToRank = new HashMap<>();
        Map<Set<Integer>, Integer> reducedRank = new HashMap<>();

        for (Node node : cpdag.getNodes()) {
            if (cpdag.getAdjacentNodes(node).isEmpty()) {
                remainingVars.remove((Integer) nodes.indexOf(node));
            }
        }

        for (int rank = 0; rank <= 3; rank++) {
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

//                        {
//                            int[] _cluster = cluster.stream().mapToInt(Integer::intValue).toArray();
//                            int[] _complement = complement.stream().mapToInt(Integer::intValue).toArray();
//
//                            if (!survivesSepsetExplainAway(S, _cluster, _complement, sampleSize, alpha, nodes, sepsets, depth == -1 ? 100 : depth, cpdag, 5)) {
//                                continue;
//                            }
//                        }

                        int minpq = Math.min(union.size(), complement.size());

                        if (minpq != union.size()) {
                            continue;
                        }

                        if (union.size() >= variables.size() - union.size()) {
                            continue;
                        }

                        int rankOfUnion = lookupRank(union, variables);
                        log("For this candidate: " + toNamesCluster(candidate) + ", Trying union: " + toNamesCluster(union)
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

//                            {
//                                List<Integer> complement = allVariables();
//                                complement.removeAll(C2);
//
//                                int[] _cluster = C2.stream().mapToInt(Integer::intValue).toArray();
//                                int[] _complement = complement.stream().mapToInt(Integer::intValue).toArray();
//
//                                if (!survivesSepsetExplainAway(S, _cluster, _complement, sampleSize, alpha, nodes, sepsets, depth == -1 ? 100 : depth, cpdag, 5)) {
//                                    continue;
//                                }
//                            }

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

        for (Set<Integer> cluster : new HashSet<>(clusterToRank.keySet())) {
            List<Integer> complement = allVariables();
            complement.removeAll(cluster);
//            int[] _cluster = cluster.stream().mapToInt(Integer::intValue).toArray();
//            int[] _complement = complement.stream().mapToInt(Integer::intValue).toArray();
//
//            int _depth = depth == -1 ? 100 : depth;
////            if (!survivesSepsetExplainAway(S, _cluster, _complement, sampleSize, alpha, nodes, sepsets, 2, cpdag, 10)) {
////                clusterToRank.remove(cluster);
////                reducedRank.remove(cluster);
////            }

//            if (!rejectBySmallRemoval(S, cluster, variables, sampleSize, alpha)) {
//                clusterToRank.remove(cluster);
//                reducedRank.remove(cluster);
//            }

            if (failsSubsetTest(S, cluster, sampleSize, alpha)) {
                clusterToRank.remove(cluster);
                reducedRank.remove(cluster);
            }
        }

        log("Final clusters = " + toNamesClusters(clusterToRank.keySet()));
        return new Pair<>(clusterToRank, reducedRank);
    }

    private boolean failsSubsetTest(SimpleMatrix S, Set<Integer> cluster, int sampleSize, double alpha) {
        List<Integer> C = new ArrayList<>(cluster);

        List<Integer> D = allVariables();
        D.removeAll(cluster);

        SublistGenerator gen = new SublistGenerator(C.size(), C.size() - 1);
        int[] choice;

        while ((choice = gen.next()) != null) {
            if (choice.length < C.size() - 2) continue;

            List<Integer> _C = new ArrayList<>();
            for (int i : choice) {
                _C.add(C.get(i));
            }

            int[] clusterArray = _C.stream().mapToInt(Integer::intValue).toArray();
            int[] complementArray = D.stream().mapToInt(Integer::intValue).toArray();

            int rank = RankTests.estimateRccaRank(S, clusterArray, complementArray, sampleSize, alpha);

            if (rank == 0) {
                return true;
            }
        }

        SublistGenerator gen2 = new SublistGenerator(C.size(), 2);
        int[] choice2;

        while ((choice2 = gen2.next()) != null) {
            if (choice2.length < 1) continue;
            if (choice2.length == C.size()) continue;

            List<Integer> Z = new ArrayList<>();
            for (int i : choice2) {
                Z.add(C.get(i));
            }

            List<Integer> _C = new ArrayList<>(C);
            _C.removeAll(Z);

            int[] clusterArray = _C.stream().mapToInt(Integer::intValue).toArray();
            int[] complementArray = D.stream().mapToInt(Integer::intValue).toArray();
            int[] zArray = Z.stream().mapToInt(Integer::intValue).toArray();

            double r = RankTests.estimateRccaRankConditioned(S, clusterArray, complementArray, zArray, sampleSize, alpha);

            if (r == 0) {
                return true;
            }
        }

        return false;
    }

    private boolean subsetRank0b(SimpleMatrix S, Set<Integer> cluster, int sampleSize, double alpha) {
        List<Integer> C = new ArrayList<>(cluster);

        List<Integer> Dlist = allVariables(); // observed-only
        Dlist.removeAll(cluster);
        if (Dlist.isEmpty() || C.size() < 2) return false;

        int[] D = Dlist.stream().mapToInt(Integer::intValue).toArray();

        // remove-one: test all size n-1 subsets (i.e., drop each c once)
        for (int drop = 0; drop < C.size(); drop++) {
            List<Integer> Cminus = new ArrayList<>(C);
            int removed = Cminus.remove(drop); // value removed (by position)
            int[] X = Cminus.stream().mapToInt(Integer::intValue).toArray();

            int r = RccaSetUtils.rankSetVsSet(S, X, D, new int[0], sampleSize, alpha);
            // optional effect-size gate:
            // double rho2 = maxCanonicalCorrSqConditioned(S, X, D, new int[0]);

            if (r == 0) {
                System.out.println("subsetRank0: dropping " + removed + " zeros the rank");
                return true; // i.e., reject cluster as hallucinated
            }
        }
        return false; // no single removal killed the channel
    }

    boolean subsetRank0c(SimpleMatrix S, Set<Integer> Cset, int n, double alpha) {
        List<Integer> C = new ArrayList<>(Cset);
        List<Integer> Dlist = allVariables();  // observed-only
        Dlist.removeAll(Cset);
        if (Dlist.isEmpty()) return false;
        int[] D = Dlist.stream().mapToInt(Integer::intValue).toArray();

        for (int ci : C) {
            int[] Xi = new int[]{ci};
            int r = RccaSetUtils.rankSetVsSet(S, Xi, D, new int[0], n, alpha);
            if (r == 0) {
                // optional: System.out.println("singleton kills: " + name(ci));
                return true; // reject latent for C
            }
        }
        return false; // pass stage 1
    }

    /**
     * Returns true if the cluster should be rejected based on the
     * "small-removal zero-rank" rule.
     *
     * @param S           Covariance/correlation matrix
     * @param cluster     Set of indices for cluster C
     * @param allVars     List of all variable indices [0..p-1]
     * @param sampleSize  n
     * @param alpha       Significance level for rank test
     * @return            true if cluster should be rejected
     */
    private boolean rejectBySmallRemoval(SimpleMatrix S,
                                         Set<Integer> cluster,
                                         List<Integer> allVars,
                                         int sampleSize,
                                         double alpha) {
        List<Integer> C = new ArrayList<>(cluster);

        // D = V \ C
        List<Integer> D = new ArrayList<>(allVars);
        D.removeAll(cluster);

        // Compute full cross-cut rank rhat = rank(C,D)
        int[] Carr = C.stream().mapToInt(Integer::intValue).toArray();
        int[] Darr = D.stream().mapToInt(Integer::intValue).toArray();
        int[] clusterArray1 = C.stream().mapToInt(Integer::intValue).toArray();
        int[] complementArray1 = D.stream().mapToInt(Integer::intValue).toArray();

        int rhat = RankTests.estimateRccaRank(S, clusterArray1, complementArray1, sampleSize, alpha);

        if (rhat == 0) return true; // Already degenerate

        // Max size of removal set to check
        int maxRemoval = Math.min(rhat, C.size() - 1);

        // Check all removal sets up to size maxRemoval
        for (int size = 1; size <= maxRemoval; size++) {
            SublistGenerator gen = new SublistGenerator(C.size(), C.size() - size);
            int[] choice;
            while ((choice = gen.next()) != null) {
                // choice indexes variables to KEEP in C'
                List<Integer> Cprime = new ArrayList<>();
                for (int idx : choice) {
                    Cprime.add(C.get(idx));
                }
                int[] clusterArray = Cprime.stream().mapToInt(Integer::intValue).toArray();
                int[] complementArray = D.stream().mapToInt(Integer::intValue).toArray();

                int rank = RankTests.estimateRccaRank(S, clusterArray, complementArray, sampleSize, alpha);
                if (rank == 0) {
                    return true; // Found small removal set that kills the rank
                }
            }
        }
        return false; // Survived all small removals
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
     * @param vars    A reference list of variables to check the size of cluster against. It should be the case that |C|
     *                < |V \ C|.
     * @return An integer representing the calculated or cached rank of the given cluster.
     * @throws IllegalArgumentException if |C| >= |V \ C}.
     */
    private int lookupRank(Set<Integer> cluster, List<Integer> vars) {
        if (cluster.size() >= vars.size() - cluster.size()) {
            throw new IllegalArgumentException("Cluster is too large.");
        }

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

    // --- Keep-final hook (unchanged signature) ---
    boolean keepFinalCluster(SimpleMatrix S, int[] C, int[] VminusC,
                             Graph skeleton, int n, double alpha) {
        // Deterministic, capped, in-cluster candidates (degree ↓, then corr ↓, then index ↑)
        List<Integer> inClusterCand = clusterCandidatesOrderedByDegreeStable(C, VminusC, skeleton, S, /*cap=*/5);
        int rDiag = diagnoseChannel(S, C, VminusC, n, alpha, /*kmax=*/2, inClusterCand);
        return rDiag > 0;
    }

    // --- Deterministic in-cluster ordering with tie-breaks ---
    private List<Integer> clusterCandidatesOrderedByDegreeStable(
            int[] C, int[] D, Graph skeleton, SimpleMatrix S, int cap) {

        // Precompute degree and a tie-break score (max |corr| to D)
        class Entry {
            final int v;
            final int degree;
            final double corrScore; // max |corr(v, d)| over d in D

            Entry(int v, int degree, double corrScore) {
                this.v = v;
                this.degree = degree;
                this.corrScore = corrScore;
            }
        }

        List<Entry> entries = new ArrayList<>(C.length);
        for (int v : C) {
            int deg = skeleton.getAdjacentNodes(nodes.get(v)).size();
            double score = maxAbsCorrToSet(S, v, D);
            entries.add(new Entry(v, deg, score));
        }

        // Sort: degree desc, then corrScore desc, then index asc (deterministic)
        entries.sort((a, b) -> {
            int cmp = Integer.compare(b.degree, a.degree);
            if (cmp != 0) return cmp;
            cmp = Double.compare(b.corrScore, a.corrScore);
            if (cmp != 0) return cmp;
            return Integer.compare(a.v, b.v);
        });

        // Cap
        int k = Math.min(cap, entries.size());
        List<Integer> ordered = new ArrayList<>(k);
        for (int i = 0; i < k; i++) ordered.add(entries.get(i).v);
        return ordered;
    }

    private int diagnoseChannel(SimpleMatrix S, int[] C, int[] D, int n, double alpha,
                                int kmax, Collection<Integer> inClusterCandidates) {

        int r = estimateRccaRank(S, C, D, n, alpha);

        residualRankByCluster.put(getIntSet(C), r);

        if (r == 0) return 0;

        // --- Step 2: singleton conditioning over in-cluster candidates ---
        int rBest = r;
        int bestZ = -1;

        // Track which candidates improved the rank at least once
        List<Integer> improvers = new ArrayList<>();

        for (int z : inClusterCandidates) {
            int rZ = RankTests.estimateRccaRankConditioned(S, C, D, new int[]{z}, n, alpha);
            if (rZ == 0) return 0; // early exit: single variable explains away the link
            if (rZ < r) improvers.add(z);                 // qualifies for Step 3
            if (rZ < rBest || (rZ == rBest && z < bestZ)) // deterministic tie-break by index
            {
                rBest = rZ;
                bestZ = z;
            }
        }

        // If nobody helped and we’re not allowed to add more, return current best
        if (kmax <= 1) return rBest;

        // --- Step 3: greedy add up to kmax, but ONLY from the improvers set ---
        if (improvers.isEmpty()) return rBest; // nothing to add

        List<Integer> Z = new ArrayList<>();
        if (bestZ >= 0 && improvers.contains(bestZ)) Z.add(bestZ);
        int rRes = rBest;

        while (Z.size() < kmax && rRes > 0) {
            int pick = -1, best = rRes;
            for (int z : improvers)
                if (!Z.contains(z)) {
                    int[] added = new int[Z.size() + 1];
                    for (int i = 0; i < Z.size(); i++) added[i] = Z.get(i);
                    added[added.length - 1] = z;

                    int rZ = RankTests.estimateRccaRankConditioned(S, C, D, added, n, alpha);

                    // Strict improvement; tie-break deterministically by smaller index
                    if (rZ < best || (rZ == best && pick >= 0 && z < pick)) {
                        best = rZ;
                        pick = z;
                    }
                }
            if (pick < 0) break; // no improvement
            Z.add(pick);
            rRes = best;
        }

        residualRankByCluster.put(getIntSet(C), rRes);

        return rRes;
    }

    private Set<Integer> getIntSet(int[] c) {
        Set<Integer> set = new HashSet<>();
        for (int j : c) set.add(j);
        return set;
    }

    private boolean allSubTwoClustersExist(Set<Integer> cluster) {
        System.out.println("Checking 2-clusters: " + toNamesCluster(cluster));

        List<Integer> _cluster = new ArrayList<>(cluster);

        for (int i = 0; i < _cluster.size(); i++) {
            for (int j = i + 1; j < _cluster.size(); j++) {
                int x = _cluster.get(i);
                int y = _cluster.get(j);

                Set<Integer> sub = new HashSet<>();
                sub.add(x);
                sub.add(y);

                if (lookupRank(sub, variables) != 1) {
                    return false;
                }
            }
        }

        return true;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

    public enum Mode {METALOOP, SIZE_RANK}

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

        // Helper: Extract a submatrix of S with rows in A and cols in B
        private static SimpleMatrix extractCrossBlock(SimpleMatrix S, List<Integer> rows, List<Integer> cols) {
            SimpleMatrix result = new SimpleMatrix(rows.size(), cols.size());
            for (int i = 0; i < rows.size(); i++) {
                for (int j = 0; j < cols.size(); j++) {
                    result.set(i, j, S.get(rows.get(i), cols.get(j)));
                }
            }
            return result;
        }

        // Helper: Extract submatrix of S using indices
        private static SimpleMatrix extractSubmatrix(SimpleMatrix S, List<Integer> indices) {
            return extractCrossBlock(S, indices, indices);
        }
    }
}

