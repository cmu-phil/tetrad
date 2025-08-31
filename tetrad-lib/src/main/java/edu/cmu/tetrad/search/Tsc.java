package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.CorrelationMatrix;
import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.RankTests;
import edu.cmu.tetrad.util.SublistGenerator;
import edu.cmu.tetrad.util.TetradLogger;
import org.ejml.simple.SimpleMatrix;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static edu.cmu.tetrad.util.RankTests.estimateWilksRank;

/**
 * The Tsc class provides methods and utilities for statistical computations, clustering, and rank-based analysis of
 * variables. This class manages significance levels, caching mechanisms, and structures to efficiently handle clusters
 * and their associated ranks.
 */
public class Tsc {
    private static final java.util.concurrent.ConcurrentHashMap<Long, long[][]> BINOM_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private final List<Node> nodes;
    private final List<Integer> variables;
    private final int sampleSize;
    private final SimpleMatrix S;
    private final Map<Key, Integer> rankCache = new ConcurrentHashMap<>();
    private int expectedSampleSize = -1;
    private double alpha = 0.01;
    private boolean verbose = true;
    private Map<Set<Integer>, Integer> clusterToRank;
//    private Map<Set<Integer>, Integer> reducedRank;

    /**
     * Constructs an instance of the TscScored class using the provided variables and covariance matrix.
     *
     * @param variables a list of Node elements representing variables to be included in the scoring process
     * @param cov       a CovarianceMatrix object representing the covariance matrix associated with the variables
     */
    public Tsc(List<Node> variables, CovarianceMatrix cov) {
        this.nodes = new ArrayList<>(variables);
        this.variables = new ArrayList<>(variables.size());
        for (int i = 0; i < variables.size(); i++) this.variables.add(i);
        this.S = new CorrelationMatrix(cov).getMatrix().getSimpleMatrix();
        this.sampleSize = cov.getSampleSize();
        setExpectedSampleSize(-1);
    }

    /**
     * Constructs a StringBuilder containing a formatted string representation of the names of nodes corresponding to
     * the provided cluster indices.
     *
     * @param cluster a collection of integers representing indices of nodes to include in the cluster
     * @param nodes   a list of Node objects where each integer index in the cluster corresponds to a node
     * @return a StringBuilder containing the formatted names of the nodes in the specified cluster
     */
    public static @NotNull StringBuilder toNamesCluster(Collection<Integer> cluster, List<Node> nodes) { /* ... unchanged ... */
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
     * Converts a set of clusters represented as sets of integers into a string representation that associates cluster
     * IDs with node names.
     *
     * @param clusters a set of clusters, where each cluster is a set of integers representing node IDs
     * @param nodes    a list of Node objects representing the nodes, where the index corresponds to the node ID
     * @return a string containing the names of the nodes in each cluster, separated by "; " for different clusters
     */
    public static @NotNull String toNamesClusters(Set<Set<Integer>> clusters, List<Node> nodes) { /* ... unchanged ... */
        StringBuilder sb = new StringBuilder();
        int count0 = 0;
        for (Collection<Integer> cluster : clusters) {
            StringBuilder _sb = toNamesCluster(cluster, nodes);
            if (count0++ < clusters.size() - 1) _sb.append("; ");
            sb.append(_sb);
        }
        return sb.toString();
    }

    /**
     * Identifies clusters of variables at a specified rank. This method generates all possible clusters
     * based on the given variable list and size, computes their ranks, and filters those that match the
     * specified target rank.
     *
     * @param vars a list of integers representing the variables to consider
     * @param size the size of the clusters to generate
     * @param rank the target rank to filter clusters
     * @return a set of clusters that match the specified rank, where each cluster is represented as a set of integers
     */
    public Set<Set<Integer>> findClustersAtRank(List<Integer> vars, int size, int rank) {
        log("vars: " + vars);
        log("findClustersAtRankTesting size = " + size + ", rank = " + rank + ", ess = " + expectedSampleSize);

        final int n = vars.size();
        final int k = size;
        if (k <= 0 || k > n) return Collections.emptySet();

        // shard by first position for parallelism; avoids nCk overflow
        return IntStream.range(0, n - k + 1).parallel().mapToObj(start -> {
            Set<Set<Integer>> out = ConcurrentHashMap.newKeySet();
            int[] comb = new int[k];
            // initialize comb = [start, start+1, ..., start+k-1]
            for (int i = 0; i < k; i++) comb[i] = start + i;

            // standard lexicographic k-combination advance
            while (true) {
                // enforce shard: first element fixed to 'start'
                if (comb[0] != start) break;

                int[] ids = new int[k];
                for (int i = 0; i < k; i++) ids[i] = vars.get(comb[i]);

                if (lookupRankFast(ids) == rank) {
                    Set<Integer> cluster = new HashSet<>(k * 2);
                    for (int id : ids) cluster.add(id);
                    out.add(cluster);
                }

                int idx = k - 1;
                while (idx >= 0 && comb[idx] == n - k + idx) idx--;
                if (idx < 0) break;
                comb[idx]++;
                for (int j = idx + 1; j < k; j++) comb[j] = comb[j - 1] + 1;
            }
            return out;
        }).flatMap(Set::stream).collect(ConcurrentHashMap::newKeySet, Set::add, Set::addAll);
    }

    // Fast overload: takes primitive IDs and uses canonical Key (Wilks path)
    private int lookupRankFast(int[] ids) {
        // ids not guaranteed sorted; Key will sort once
        return rankCache.computeIfAbsent(new Key(ids), k -> {
            // build Y set directly without boxing if you prefer; here keep existing
            Set<Integer> s = new HashSet<>(ids.length * 2);
            for (int x : ids) s.add(x);
            return rank(s);
        });
    }

    /**
     * Identifies clusters of variables and associates each cluster with a rank.
     * <p>
     * This method computes clusters by calling an internal implementation and returns the results in the form of a map.
     * Each entry in the map represents a cluster (denoted as a set of integers, where each integer is an identifier for
     * a variable) associated with its respective rank.
     *
     * @return a map where the keys are sets of integers representing clusters of variables, and the values are integers
     * representing the rank associated with each cluster
     */
    public Map<Set<Integer>, Integer> findClusters() {
        List<Integer> variables = allVariables();
        if (new HashSet<>(variables).size() != variables.size()) {
            throw new IllegalArgumentException("Variables must be unique.");
        }

        List<Integer> remainingVars = new ArrayList<>(allVariables());
        clusterToRank = new HashMap<>();
//        reducedRank = new HashMap<>();

        for (int rank = 0; rank <= 3; rank++) {
            int size = rank + 1;
            if (Thread.currentThread().isInterrupted()) break;
            if (size >= remainingVars.size() - size) continue;

            log("EXAMINING SIZE " + size + " RANK = " + rank + " REMAINING VARS = " + remainingVars.size());
            Set<Set<Integer>> P = findClustersAtRank(remainingVars, size, rank);
            log("Base clusters for size " + size + " rank " + rank + ": " + (P.isEmpty() ? "NONE" : toNamesClusters(P, nodes)));
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

                if (seed.size() >= remainingVars.size() - seed.size()) continue;

                int seedRankShown;
                seedRankShown = ranksByTest(seed);
                log("Picking seed from the list: " + toNamesCluster(seed) + " rank = " + seedRankShown);

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

                        int rankOfUnion = ranksByTest(union);
                        log("For this candidate: " + toNamesCluster(candidate) + ", Trying union: " + toNamesCluster(union) + " rank = " + rankOfUnion);

                        if (rankOfUnion == rank) {

                            // Accept this union
                            cluster = union;
                            it.remove();
                            extended = true;
                            break;
                        }
                    }
                } while (extended);

                int clusterRank;
                clusterRank = ranksByTest(cluster);

                if (clusterRank == rank) {
                    newClusters.removeIf(cluster::containsAll);  // Avoid nesting
                    log("Adding cluster to new clusters: " + toNamesCluster(cluster) + " rank = " + clusterRank);
                    newClusters.add(cluster);
                    used.addAll(cluster);
//                    remainingVars.removeAll(cluster);
                }
            }

            log("New clusters for rank " + rank + " size = " + size + ": " + (newClusters.isEmpty() ? "NONE" : toNamesClusters(newClusters, nodes)));

            Set<Set<Integer>> P2 = new HashSet<>(P);
            log("Now we will try to augment each cluster by one new variable by looking at cluster overlaps again.");
            log("We will repeat this for ranks rank - 1 down to rank 1.");

            boolean didAugment = false;

            for (int _reducedRank = rank - 1; _reducedRank >= 0; _reducedRank--) {
                if (Thread.currentThread().isInterrupted()) break;

                for (Set<Integer> C1 : new HashSet<>(newClusters)) {
                    if (Thread.currentThread().isInterrupted()) break;

                    int _size = C1.size();

                    for (Set<Integer> _C : P2) {
                        if (Thread.currentThread().isInterrupted()) break;

                        Set<Integer> C2 = new HashSet<>(C1);
                        C2.addAll(_C);

                        if (C2.size() >= this.variables.size() - C2.size()) continue;

                        int newRank;
                        newRank = ranksByTest(C2);

                        if (C2.size() == _size + 1 && newRank < rank && newRank >= 1) {
                            if (newClusters.contains(C2)) continue;

                            newClusters.remove(C1);
                            newClusters.add(C2);
//                            reducedRank.put(C2, newRank);
                            used.addAll(C2);
                            log("Augmenting cluster " + toNamesCluster(C1) + " to cluster " + toNamesCluster(C2) + " (rank " + _reducedRank + ").");
                            didAugment = true;
                        }
                    }
                }
            }

            if (!didAugment) log("No augmentations were needed.");
            log("New clusters after the augmentation step = " + (newClusters.isEmpty() ? "NONE" : toNamesClusters(newClusters, nodes)));

            for (Set<Integer> cluster : new ArrayList<>(newClusters)) clusterToRank.put(cluster, rank);

            for (Set<Integer> _C : newClusters) used.addAll(_C);
            remainingVars.removeAll(used);
        }

        log("Removing clusters of size 1, as these shouldn't be assigned latents.");
        for (Set<Integer> cluster : new HashSet<>(clusterToRank.keySet())) {
            if (cluster.size() == 1) {
                clusterToRank.remove(cluster);
//                reducedRank.remove(cluster);
                log("Removing cluster " + toNamesCluster(cluster));
            }
        }

        log("Penultimate clusters = " + toNamesClusters(clusterToRank.keySet(), nodes));
//        log("Now we will consider whether any of the penultimate clusters should be discarded (as from a non-latent DAG, e.g.).");
//
//        boolean penultimateRemoved = false;
//
//        // Try to split instead of outright reject (Dong-style refinement)
//        for (Set<Integer> cluster : new HashSet<>(clusterToRank.keySet())) {
//            if (failsSubsetTest(S, cluster, expectedSampleSize, alpha)) {
//                clusterToRank.remove(cluster);
////                reducedRank.remove(cluster);
//                penultimateRemoved = true;
//            }
//        }

        log("Now we will consider whether any of the penultimate clusters should be discarded (as from a non-latent DAG, e.g.).");

        log("Now we will refine penultimate clusters by removing only offending subsets (Rules 2 & 3).");

        boolean changedAny = false;
        for (Set<Integer> cluster : new HashSet<>(clusterToRank.keySet())) {
            int rC = Math.max(0, clusterToRank.getOrDefault(cluster, 0));

            // produce a refined copy (possibly smaller), do not mutate the key in-place
            Set<Integer> refined = refineClusterByRules2And3(cluster, rC);

            if (refined.size() < 2) {
                clusterToRank.remove(cluster);
                changedAny = true;
                log("Cluster " + toNamesCluster(cluster) + " eliminated after refinement.");
                continue;
            }

            if (!refined.equals(cluster)) {
                clusterToRank.remove(cluster);

                // you can keep rC, or recompute a safer displayed rank
                // Option A (strict): recompute rank against complement
//                int newRank = ranksByTest(refined);

                // Option B (conservative): keep min(rC, |refined|-1)
                 int newRank = Math.min(rC, Math.max(0, refined.size() - 1));

                clusterToRank.put(refined, newRank);
                changedAny = true;
                log("Refined cluster " + toNamesCluster(cluster) + " → " + toNamesCluster(refined)
                    + " (rank now " + newRank + ").");
            }
        }
        if (!changedAny) log("No cluster refinement was needed.");
//        if (!penultimateRemoved) log("No penultimate clusters were removed.");

//        if (!penultimateRemoved) log("No penultimate clusters were removed.");

        log("Final clusters = " + toNamesClusters(clusterToRank.keySet(), nodes));
        return clusterToRank;
    }

    private List<Integer> allVariables() {
        List<Integer> _variables = new ArrayList<>();
        for (int i = 0; i < this.variables.size(); i++) _variables.add(i);
        return _variables;
    }

    /**
     * Sets the significance level alpha used in statistical computations. The significance level determines the
     * threshold for hypothesis testing and affects the resulting ranks or scores. Updating this parameter clears the
     * cached ranks as they depend on the current alpha value.
     *
     * @param alpha the significance level to be set, typically a value between 0 and 1, where lower values indicate
     *              stricter thresholds.
     */
    public void setAlpha(double alpha) {
        this.alpha = alpha;
        rankCache.clear(); // Wilks rank depends on alpha
    }

    /**
     * Sets the verbose mode for the application or process.
     *
     * @param verbose a boolean value where {@code true} enables verbose mode and {@code false} disables it.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    // ---- subset tests (unchanged: still Wilks-based by design) -----------------
    //    private boolean failsSubsetTest(SimpleMatrix S, Set<Integer> cluster, int expectedSampleSize, double alpha) { /* ... unchanged ... */
    //        List<Integer> C = new ArrayList<>(cluster);
    //        List<Integer> D = allVariables();
    //        D.removeAll(cluster);
    //
    ////        { // Rule 1
    ////            SublistGenerator gen0 = new SublistGenerator(C.size(), C.size() - 1);
    ////            int[] choice0;
    ////            while ((choice0 = gen0.next()) != null) {
    ////                List<Integer> C1 = new ArrayList<>();
    ////                for (int i : choice0) C1.add(C.get(i));
    ////                if (C1.isEmpty() || C1.size() == C.size()) continue;
    ////
    ////                List<Integer> C2 = new ArrayList<>(C);
    ////                C2.removeAll(C1);
    ////                if (C2.isEmpty()) continue;
    ////
    ////                int[] c1Array = C1.stream().mapToInt(Integer::intValue).toArray();
    ////                int[] c2Array = C2.stream().mapToInt(Integer::intValue).toArray();
    ////
    ////                int minpq = Math.min(c1Array.length, c2Array.length);
    ////                Integer l = clusterToRank.get(cluster);
    ////                if (l == null) continue;
    ////                l = Math.min(minpq, Math.max(0, l));
    ////
    ////                int r = RankTests.estimateWilksRank(S, c1Array, c2Array, expectedSampleSize, alpha);
    ////                if (r < l) {
    ////                    log("Deficient! rank(" + toNamesCluster(C1, nodes) + ", " + toNamesCluster(C2, nodes) + ") = "
    ////                        + r + " < " + l + "; removing " + toNamesCluster(cluster));
    ////                    return true;
    ////                }
    ////            }
    ////        }
    //        { // Rule 2
    //            SublistGenerator gen0 = new SublistGenerator(C.size(), C.size() - 1);
    //            int[] choice;
    //            while ((choice = gen0.next()) != null) {
    //                if (choice.length < 1) continue;
    //                List<Integer> _C = new ArrayList<>();
    //                for (int i : choice) _C.add(C.get(i));
    //                int[] _cArray = _C.stream().mapToInt(Integer::intValue).toArray();
    //                int[] dArray = D.stream().mapToInt(Integer::intValue).toArray();
    //
    //                int minpq = Math.min(_cArray.length, dArray.length);
    //                Integer l = clusterToRank.get(cluster);
    //                l = Math.min(minpq, Math.max(0, l));
    //
    //                int r = RankTests.estimateWilksRank(S, _cArray, dArray, expectedSampleSize, alpha);
    //                if (r < l) {
    //                    log("rank(" + toNamesCluster(_C, nodes) + ", D) = " + r + " < r = " + l
    //                        + "; removing cluster " + toNamesCluster(cluster));
    //                    return true;
    //                }
    //            }
    //        }
    //        { // Rule 3
    //            Integer rC = clusterToRank.get(cluster);
    //            if (rC == null) rC = 0;
    //
    //            SublistGenerator gen2 = new SublistGenerator(C.size(), Math.min(C.size() - 1, rC));
    //            int[] choice2;
    //            while ((choice2 = gen2.next()) != null) {
    //                if (choice2.length < rC) continue;
    //
    //                List<Integer> Z = new ArrayList<>();
    //                for (int i : choice2) Z.add(C.get(i));
    //
    //                List<Integer> _C = new ArrayList<>(C);
    //                _C.removeAll(Z);
    //
    //                int[] _cArray = _C.stream().mapToInt(Integer::intValue).toArray();
    //                int[] dArray = D.stream().mapToInt(Integer::intValue).toArray();
    //                int[] zArray = Z.stream().mapToInt(Integer::intValue).toArray();
    //
    //                int rZ = RankTests.estimateWilksRankConditioned(S, _cArray, dArray, zArray, expectedSampleSize, alpha);
    //                if (rZ == 0) {
    //                    log("rank(_C = " + toNamesCluster(_C, nodes) + ", D | Z = " + toNamesCluster(Z, nodes) + ") = 0; removing cluster " + toNamesCluster(cluster) + ".");
    //                    return true;
    //                }
    //            }
    //        }
    //        return false;
    //    }

    /**
     * Refine a cluster by applying subset-based Rules 2 and 3:
     * - Rule 2: if rank(_C, D) < r_C, REMOVE the offending subset _C from the cluster
     * - Rule 3: if rank(_C, D | Z) = 0 (with _C = C \ Z), REMOVE the offending subset Z from the cluster
     *
     * This method iterates until no rule fires. Returns the refined cluster (may be the same object),
     * or an empty set if the cluster collapses. Does not mutate the input set; always works on a copy.
     *
     * @param original     the cluster to refine (will not be mutated)
     * @param rC           the cluster's intended rank
     * @return refined cluster (possibly smaller); empty set if eliminated
     */
    private Set<Integer> refineClusterByRules2And3(Set<Integer> original, int rC) {
        if (original == null || original.isEmpty()) return Collections.emptySet();
        if (rC < 0) rC = 0;

        // Work on a copy to avoid mutating keys already in maps
        Set<Integer> Cset = new HashSet<>(original);
        boolean changed;

        do {
            changed = false;

            // Recompute lists each pass
            List<Integer> C = new ArrayList<>(Cset);
            List<Integer> D = allVariables();
            D.removeAll(Cset);

            // --- Rule 2: find an offending _C with rank(_C, D) < rC, remove that _C
            if (C.size() >= 2 && rC > 0) {
                SublistGenerator gen0 = new SublistGenerator(C.size(), C.size() - 1);
                int[] choice;
                while ((choice = gen0.next()) != null) {
                    if (choice.length < 1 || choice.length >= C.size()) continue;

                    List<Integer> _C = new ArrayList<>();
                    for (int i : choice) _C.add(C.get(i));
                    int[] _cArray = _C.stream().mapToInt(Integer::intValue).toArray();
                    int[] dArray  = D.stream().mapToInt(Integer::intValue).toArray();

                    int minpq = Math.min(_cArray.length, dArray.length);
                    int l = Math.min(minpq, Math.max(0, rC));
                    int r = RankTests.estimateWilksRank(S, _cArray, dArray, expectedSampleSize, alpha);

                    if (r < l) {
                        // offending subset is _C → remove _C from the cluster
                        Cset.removeAll(_C);
                        log("Rule 2 fired: removing offending subset "
                            + toNamesCluster(new HashSet<>(_C))
                            + " from cluster " + toNamesCluster(new HashSet<>(C))
                            + " (rank(_C,D)=" + r + " < " + l + ")");
                        changed = true;
                        break; // restart passes after modification
                    }
                }
                if (changed) continue; // restart loop after Rule 2 removal
            }

            // --- Rule 3: find Z with |Z| ≥ rC and rank(C\Z, D | Z) = 0, remove Z
            if (!Cset.isEmpty() && rC > 0) {
                List<Integer> Cnow = new ArrayList<>(Cset);
                List<Integer> Dnow = allVariables();
                Dnow.removeAll(Cset);

                SublistGenerator gen2 = new SublistGenerator(Cnow.size(), Math.min(Cnow.size() - 1, rC));
                int[] choice2;
                while ((choice2 = gen2.next()) != null) {
                    if (choice2.length < rC) continue;

                    List<Integer> Z = new ArrayList<>();
                    for (int i : choice2) Z.add(Cnow.get(i));

                    List<Integer> _C = new ArrayList<>(Cnow);
                    _C.removeAll(Z);

                    if (_C.isEmpty()) continue;

                    int[] _cArray = _C.stream().mapToInt(Integer::intValue).toArray();
                    int[] dArray  = Dnow.stream().mapToInt(Integer::intValue).toArray();
                    int[] zArray  = Z.stream().mapToInt(Integer::intValue).toArray();

                    int rZ = RankTests.estimateWilksRankConditioned(S, _cArray, dArray, zArray, expectedSampleSize, alpha);
                    if (rZ == 0) {
                        // offending subset is Z → remove Z from the cluster
                        Cset.removeAll(Z);
                        log("Rule 3 fired: removing offending subset Z="
                            + toNamesCluster(new HashSet<>(Z))
                            + " from cluster " + toNamesCluster(new HashSet<>(Cnow))
                            + " (rank(C\\Z, D | Z)=0)");
                        changed = true;
                        break; // restart passes after modification
                    }
                }
            }

        } while (changed && Cset.size() >= 2);

        return Cset;
    }

    private int ranksByTest(Set<Integer> cluster) {
        Key k = new Key(cluster);
        Integer cached = rankCache.get(k);
        if (cached != null) return cached;
        int r = rank(cluster);
        rankCache.put(k, r);
        return r;
    }

    private int rank(Set<Integer> cluster) {
        List<Integer> ySet = new ArrayList<>(cluster);
        List<Integer> xSet = new ArrayList<>(variables);
        xSet.removeAll(ySet);

        int[] xIndices = new int[xSet.size()];
        int[] yIndices = new int[ySet.size()];
        for (int i = 0; i < xSet.size(); i++) xIndices[i] = xSet.get(i);
        for (int i = 0; i < ySet.size(); i++) yIndices[i] = ySet.get(i);

        return estimateWilksRank(S, xIndices, yIndices, expectedSampleSize, alpha);
    }

    private void log(String s) {
        if (verbose) TetradLogger.getInstance().log(s);
    }

    private String toNamesCluster(Set<Integer> cluster) {
        return cluster.stream().map(i -> nodes.get(i).getName()).collect(Collectors.joining(" ", "{", "}"));
    }

    /**
     * Sets the expected sample size used in calculations. The expected sample size must be either -1, indicating it
     * should default to the current sample size, or a positive integer greater than 0.
     *
     * @param expectedSampleSize the expected sample size to be set. Must be -1 or a positive integer greater than 0.
     * @throws IllegalArgumentException if the provided expected sample size is not -1 and less than or equal to 0.
     */
    public void setExpectedSampleSize(int expectedSampleSize) {
        if (!(expectedSampleSize == -1 || expectedSampleSize > 0))
            throw new IllegalArgumentException("Expected sample size = -1 or > 0");
        this.expectedSampleSize = expectedSampleSize == -1 ? sampleSize : expectedSampleSize;
    }

    // ---- Canonical key for caching ranks (immutable, sorted) -------------------
    private record Key(int[] a) {
        Key(Collection<Integer> s) {
            this(s.stream().mapToInt(Integer::intValue).sorted().toArray());
        }

        private Key(int[] a) {
            this.a = Arrays.stream(a).sorted().toArray();
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