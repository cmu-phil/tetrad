///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.CorrelationMatrix;
import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.*;
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
 *
 * <p><b>Theory (NOLAC) soundness sketch.</b>
 * We assume a linear-Gaussian SEM with a latent DAG and pure measurement (each observed loads on exactly one latent),
 * independent unique errors across distinct clusters, and generic parameters (no exact cancellations). Under the NOLAC
 * (no overlapping clusters) assumption, the indicator sets for distinct latents are disjoint. With a consistent rank
 * test (e.g., Wilks LRT with a diminishing &alpha;), the following properties hold generically:
 *
 * <ul>
 *   <li><b>Seed soundness.</b> If G is a true cluster with latent-boundary dimension r (typically r=1), then every
 *       (r+1)-subset S&sube;G satisfies rank(S, V\S)=r. If S contains any nonmember, generically rank(S, V\S)&gt;r.</li>
 *   <li><b>Union/extension correctness.</b> Growing a seed by unions that preserve rank r expands exactly to the
 *       maximal G; adding a nonmember raises the rank and is rejected.</li>
 *   <li><b>Non-overlap.</b> Because each observed belongs to at most one true G, any attempt to reuse a committed
 *       variable either raises the rank earlier or is blocked by bookkeeping; accepted clusters are pairwise disjoint.</li>
 *   <li><b>Conditional-rank refinement (Rule 3).</b> For any Z&sube;C with |Z|&ge;r, if rank(C\Z, V\C | Z)=0 then Z acts
 *       as an observed bottleneck in a pure DAG-without-latents scenario; removing Z collapses spurious clusters.
 *       In a true latent cluster with noisy indicators, conditioning on any small Z cannot annihilate the latent
 *       contribution, so the refinement leaves true clusters intact generically.</li>
 * </ul>
 *
 * <p><b>Practical guidance.</b> Use &alpha; that decreases slowly with n (e.g., &alpha;=1/log n) or an
 * information-criterion cutoff to reduce Type-I rank errors with sample size. Ensure {@code expectedSampleSize}
 * reflects the covariance sample size.
 *
 * @author josephramsey
 */
public class Tsc implements EffectiveSampleSizeSettable {
    private final List<Node> nodes;
    private final List<Integer> variables;
    private final int sampleSize;
    private final SimpleMatrix S;
    private final Map<Key, Integer> rankCache = new ConcurrentHashMap<>();
    private int nEff = -1;
    private double alpha = 0.01;
    private boolean verbose = false;
    private Map<Set<Integer>, Integer> clusterToRank;
    private int rMax = 3;
    // require |C| >= (rank + 1 + minRedundancy)
    private int minRedundancy = 1;

    /**
     * Liberal alpha used during seed finding and cluster growing.
     * -1.0 means derive adaptively from {@link #alpha} and {@link #nEff}.
     */
    private double discoveryAlpha = -1.0;

    /**
     * Cache for ranks computed at discoveryAlpha. Keyed by the same Key record
     * as rankCache. Cleared whenever alpha, discoveryAlpha, or nEff changes.
     */
    private final Map<Key, Integer> discoveryRankCache = new ConcurrentHashMap<>();


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
        setEffectiveSampleSize(-1);
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
     * Identifies clusters of variables at a specified rank using the supplied
     * alpha. The correlation pre-screen rejects k-combinations that contain any
     * pair with |r| below the significance threshold at a liberal alpha, avoiding
     * the majority of rank test calls for uncorrelated combinations.
     *
     * @param vars a list of integers representing the variables to consider
     * @param size the size of the clusters to generate
     * @param rank the target rank to filter clusters
     * @param a    the alpha level to use for the rank test
     * @return a set of clusters that match the specified rank
     */
    public Set<Set<Integer>> findClustersAtRank(List<Integer> vars, int size,
                                                int rank, double a) {
        log("findClustersAtRankTesting size = " + size + ", rank = " + rank
                + ", ess = " + nEff + ", alpha = " + a);

        final int n = vars.size();
        final int k = size;
        if (k <= 0 || k > n) return Collections.emptySet();

        // Pre-screen threshold: use a threshold 4x more liberal than the
        // discovery alpha so only genuinely uncorrelated pairs are rejected.
        // This preserves all plausible seeds while skipping clearly hopeless ones.
        final double rThresh =
                corrSignificanceThreshold(Math.min(a * 4.0, 0.30));

        return IntStream.range(0, n - k + 1).parallel().mapToObj(start -> {
            Set<Set<Integer>> out = ConcurrentHashMap.newKeySet();
            int[] comb = new int[k];
            for (int i = 0; i < k; i++) comb[i] = start + i;

            while (true) {
                if (comb[0] != start) break;

                int[] ids = new int[k];
                for (int i = 0; i < k; i++) ids[i] = vars.get(comb[i]);

                // Cheap O(k^2) pre-screen before the expensive rank test.
                if (allPairsSignificant(ids, rThresh)
                        && lookupRankFastAtAlpha(ids, a) == rank) {
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

    /**
     * Backward-compatible overload. Delegates to the alpha-parameterised version
     * using the instance validation alpha.
     */
    public Set<Set<Integer>> findClustersAtRank(List<Integer> vars, int size, int rank) {
        return findClustersAtRank(vars, size, rank, this.alpha);
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
        final double da = getDiscoveryAlpha();

        for (int rank = 1; rank <= rMax; rank++) {
            int size = rank + 1;
            if (Thread.currentThread().isInterrupted()) break;
//            if (size >= remainingVars.size() - size) continue;
            if (size > remainingVars.size()) continue; // only require non-empty complement


            log("EXAMINING SIZE " + size + " RANK = " + rank + " REMAINING VARS = " + remainingVars.size());
            Set<Set<Integer>> P = findClustersAtRank(remainingVars, size, rank, da);

            if (verbose) {
                TetradLogger.getInstance().log("Base clusters for size " + size + " rank " + rank + ": " + (P.isEmpty() ? "NONE" : toNamesClusters(P, nodes)));
            }

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

                if (seed.size() > remainingVars.size() - seed.size()) continue;

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

                        int minSize = rank + 1 + minRedundancy;
                        if (rankOfUnion == rank && union.size() >= minSize) {

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

                int minSize = rank + 1 + minRedundancy;
                if (clusterRank == rank && cluster.size() >= minSize) {

                    // --- Rule 3-lite (observed-mediator guard).
                    // If ∃ z ∈ C such that rank(C\{z}, D | z) = 0, the cross-block dependence collapses when conditioning
                    // on z. This is typical for pure DAGs without latents where z is a mediator/bottleneck. In true latent
                    // clusters with noisy indicators, conditioning on a single indicator cannot remove the latent effect
                    // generically, so this check is asymptotically safe under NOLAC.
                    boolean collapses = false;
                    if (rank > 0) {
                        List<Integer> Clist = new ArrayList<>(cluster);
                        List<Integer> D = allVariables();
                        D.removeAll(Clist);
                        int[] dArray = D.stream().mapToInt(Integer::intValue).toArray();

                        for (int z : Clist) {
                            List<Integer> Cmz = new ArrayList<>(Clist);
                            Cmz.remove((Integer) z);
                            if (Cmz.isEmpty()) continue;

                            int[] cmz = Cmz.stream().mapToInt(Integer::intValue).toArray();
                            int[] zArr = new int[]{z};

                            int rZ = RankTests.estimateWilksRankConditioned(S, cmz, dArray, zArr, nEff, alpha);
                            if (rZ == 0) {
                                collapses = true;
                                break;
                            }
                        }
                    }
                    if (collapses) {
                        log("Skipping cluster " + toNamesCluster(cluster) + " because a singleton conditioning set collapses rank to 0.");
                    } else {
                        newClusters.removeIf(cluster::containsAll);  // Avoid nesting
                        log("Adding cluster to new clusters: " + toNamesCluster(cluster) + " rank = " + clusterRank);
                        newClusters.add(cluster);
                        used.addAll(cluster);
                    }
                }
            }

            log("New clusters for rank " + rank + " size = " + size + ": " + (newClusters.isEmpty() ? "NONE" : toNamesClusters(newClusters, nodes)));

            Set<Set<Integer>> P2 = new HashSet<>(P);
            log("Now we will try to augment each cluster by one new variable by looking at cluster overlaps again.");
            log("We will repeat this for ranks rank - 1 down to rank 1.");

            boolean didAugment = false;

            for (Set<Integer> C1 : new HashSet<>(newClusters)) {
                if (Thread.currentThread().isInterrupted()) break;

                int _size = C1.size();

                // Build a snapshot of used that excludes the elements of C1 (we are allowed to reuse C1 itself)
                Set<Integer> usedMinusC1 = new HashSet<>(used);
                usedMinusC1.removeAll(C1);

                for (Set<Integer> _C : P2) {
                    if (Thread.currentThread().isInterrupted()) break;

                    // Do not augment with anything that collides with variables already committed to other clusters
                    if (!Collections.disjoint(_C, usedMinusC1)) continue;

                    Set<Integer> C2 = new HashSet<>(C1);
                    C2.addAll(_C);

                    if (C2.size() > this.variables.size() - C2.size()) continue;

                    // Ensure the *new* elements being added do not collide with usedMinusC1
                    Set<Integer> delta = new HashSet<>(C2);
                    delta.removeAll(C1);
                    if (!Collections.disjoint(delta, usedMinusC1)) continue;

                    int newRank = ranksByDiscovery(C2);

                    // Augmentation targets bifactor: base subsets show rank = r (often 2);
                    // adding exactly one indicator that spans the factors should reduce the
                    // cross-rank to r-1 (often 1). We accept only that exact one-step drop.
                    int rankC1 = ranksByDiscovery(C1);
                    if (C2.size() == _size + 1
                            && rankC1 == rank
                            && newRank == rank - 1
                            && !removeClustersBecauseOfRank0Internally(S, C2, nEff, alpha)) {

                        if (newClusters.contains(C2)) continue;

                        newClusters.remove(C1);
                        newClusters.add(C2);
                        //  reducedRank.put(C2, newRank);

                        // Update `used`: remove old C1 contribution, then add C2
                        used.removeAll(C1);
                        used.addAll(C2);

                        log("Augmenting cluster " + toNamesCluster(C1) + " to " + toNamesCluster(C2)
                                + " (rank drop " + rank + "→" + newRank + " — bifactor signature).");
                        didAugment = true;
                        break;
                    }
                }
            }

            if (!didAugment) log("No augmentations were needed.");
            log("New clusters after the augmentation step = " + (newClusters.isEmpty() ? "NONE" : toNamesClusters(newClusters, nodes)));

            for (Set<Integer> cluster : new ArrayList<>(newClusters)) clusterToRank.put(cluster, rank);

            for (Set<Integer> _C : newClusters) used.addAll(_C);
            remainingVars.removeAll(used);
        }

        log("Removing clusters of size <= rank + 1 + minRedundancy.");
        for (Set<Integer> cluster : new HashSet<>(clusterToRank.keySet())) {
            int r = TMath.max(0, clusterToRank.getOrDefault(cluster, 0));
            int minSize = r + 1 + minRedundancy;
            if (cluster.size() < minSize) {
                clusterToRank.remove(cluster);
                log("Removing cluster " + toNamesCluster(cluster) + " for insufficient redundancy: |C|="
                        + cluster.size() + " < " + minSize + " = r+1+minRedundancy.");
            }
        }

        log("Penultimate clusters = " + toNamesClusters(clusterToRank.keySet(), nodes));
        log("Now we will refine penultimate clusters by conditional ranks.");

        boolean changedAny = false;

        for (Set<Integer> cluster : new HashSet<>(clusterToRank.keySet())) {
            int rC = ranksByTest(cluster);

            // produce a refined copy (possibly smaller), do not mutate the key in-place
            Set<Integer> refined = refineClustersByConditionalRanks(cluster, rC);

            if (refined.size() < 2) {
                clusterToRank.remove(cluster);
                changedAny = true;
                log("Cluster " + toNamesCluster(cluster) + " eliminated after refinement.");
                continue;
            }

            int newRank = ranksByTest(refined);
            int minSize2 = newRank + 1 + minRedundancy;
            if (refined.size() < minSize2) {
                clusterToRank.remove(cluster);
                changedAny = true;
                log("Refined cluster " + toNamesCluster(cluster) + " → " + toNamesCluster(refined)
                        + " rejected: |C| < r+1+minRedundancy (" + refined.size() + " < " + minSize2 + ").");
                continue;
            }
            clusterToRank.put(refined, newRank);
            changedAny = true;
            log("Refined cluster " + toNamesCluster(cluster) + " → " + toNamesCluster(refined)
                    + " (rank now " + newRank + ").");
        }
        if (!changedAny) log("No cluster refinement was needed.");

        log("Now we will consider whether any of the penultimate clusters should be removed because they hide rank 0 subsets.");

        boolean penultimateRemoved = false;

        // Try to split instead of outright reject (Dong-style refinement)
        for (Set<Integer> cluster : new HashSet<>(clusterToRank.keySet())) {
            if (removeClustersBecauseOfRank0Internally(S, cluster, nEff, alpha)) {
                clusterToRank.remove(cluster);
                penultimateRemoved = true;
            }
        }
        if (!penultimateRemoved) log("No penultimate clusters were removed.");

        // Narrow fallback: rescue isolated rank-1 triples only if the original algorithm found nothing.
        if (clusterToRank.isEmpty()) {
            rescueAndAddTriples(clusterToRank);
        }

        clusterToRank = removeOverlappingClusters(clusterToRank);

        log("Final clusters = " + toNamesClusters(clusterToRank.keySet(), nodes));
        return clusterToRank;
    }

    /**
     * Rescues isolated rank-1 triples (last-resort fallback when no clusters were found),
     * applying the same Rule-3 refinement and internal rank-0 check that regular clusters
     * pass through before adding any survivors to {@code clusterToRank}.
     *
     * @param clusterToRank the live cluster map, mutated in place
     */
    private void rescueAndAddTriples(Map<Set<Integer>, Integer> clusterToRank) {
        Set<Set<Integer>> candidates = rescueIsolatedRank1Triples(allVariables());

        for (Set<Integer> triple : candidates) {

            // Register temporarily so removeClustersBecauseOfRank0Internally can
            // look up the rank if it needs to (and Fix 1 now runs even without it).
            clusterToRank.put(triple, 1);

            // Rule-3 refinement — same path as regular clusters.
            Set<Integer> refined = refineClustersByConditionalRanks(triple, 1);

            if (refined.size() < 2) {
                clusterToRank.remove(triple);
                log("Rescued triple " + toNamesCluster(triple)
                        + " eliminated after Rule-3 refinement.");
                continue;
            }

            int newRank = ranksByTest(refined);
            int minSize = newRank + 1 + minRedundancy;

            if (refined.size() < minSize) {
                clusterToRank.remove(triple);
                log("Rescued triple " + toNamesCluster(triple) + " → " + toNamesCluster(refined)
                        + " rejected: |C|=" + refined.size()
                        + " < r+1+minRedundancy=" + minSize + ".");
                continue;
            }

            // Swap refined in if it differs from the original triple.
            if (!refined.equals(triple)) {
                clusterToRank.remove(triple);
                clusterToRank.put(refined, newRank);
            } else {
                clusterToRank.put(triple, newRank);
            }

            // Internal rank-0 check — same as the penultimate-cluster pass.
            if (removeClustersBecauseOfRank0Internally(S, refined, nEff, alpha)) {
                clusterToRank.remove(refined);
                log("Rescued triple " + toNamesCluster(refined)
                        + " removed: internal rank-0 split detected.");
            } else {
                log("Rescued triple " + toNamesCluster(refined)
                        + " accepted (rank " + newRank + ").");
            }
        }
    }

    /**
     * Removes overlapping clusters greedily, keeping the better of any two overlapping clusters.
     *
     * <p>The preference rule is:
     * <ol>
     *     <li>Prefer larger clusters.</li>
     *     <li>If sizes tie, prefer lower-rank clusters.</li>
     *     <li>If still tied, prefer the lexicographically smaller cluster for determinism.</li>
     * </ol>
     *
     * <p>This method is intended as a final cleanup step when the cluster search has produced
     * many overlapping small clusters, especially overlapping triples. It returns a new map
     * and does not mutate the input map.</p>
     *
     * @param clustersToRank map from cluster to rank
     * @return a new map with overlaps removed
     */
    private Map<Set<Integer>, Integer> removeOverlappingClusters(Map<Set<Integer>, Integer> clustersToRank) {
        if (clustersToRank == null || clustersToRank.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Set<Integer>> ordered = new ArrayList<>(clustersToRank.keySet());

        ordered.sort((a, b) -> {
            // Larger clusters first.
            int c = Integer.compare(b.size(), a.size());
            if (c != 0) return c;

            // Lower rank first.
            int ra = clustersToRank.getOrDefault(a, Integer.MAX_VALUE);
            int rb = clustersToRank.getOrDefault(b, Integer.MAX_VALUE);
            c = Integer.compare(ra, rb);
            if (c != 0) return c;

            // Deterministic lexical tie-break.
            return compareClustersLex(a, b);
        });

        Map<Set<Integer>, Integer> kept = new LinkedHashMap<>();
        Set<Integer> used = new HashSet<>();

        for (Set<Integer> cluster : ordered) {
            if (Collections.disjoint(cluster, used)) {
                Set<Integer> copy = new HashSet<>(cluster);
                kept.put(copy, clustersToRank.get(cluster));
                used.addAll(cluster);
            } else {
                log("Removing overlapping cluster " + toNamesCluster(cluster)
                        + " rank = " + clustersToRank.get(cluster));
            }
        }

        return kept;
    }

    /**
     * Lexicographically compares two clusters after sorting their members increasingly.
     *
     * @param a first cluster
     * @param b second cluster
     * @return negative, zero, or positive according to lexical order
     */
    private int compareClustersLex(Set<Integer> a, Set<Integer> b) {
        List<Integer> aa = new ArrayList<>(a);
        List<Integer> bb = new ArrayList<>(b);
        Collections.sort(aa);
        Collections.sort(bb);

        int n = Math.min(aa.size(), bb.size());
        for (int i = 0; i < n; i++) {
            int c = Integer.compare(aa.get(i), bb.get(i));
            if (c != 0) return c;
        }

        return Integer.compare(aa.size(), bb.size());
    }

    private Set<Set<Integer>> rescueIsolatedRank1Triples(List<Integer> vars) {
        Set<Set<Integer>> rescued = new HashSet<>();

        if (vars.size() < 3) return rescued;

        SublistGenerator gen = new SublistGenerator(vars.size(), 3);
        int[] choice;

        while ((choice = gen.next()) != null) {
            Set<Integer> triple = new HashSet<>();
            for (int i : choice) {
                triple.add(vars.get(i));
            }

            if (!allPairsAreRank1Seeds(triple)) continue;
            if (!allSingletonSplitsHavePositiveInternalRank(triple)) continue;

            rescued.add(triple);
        }

        return rescued;
    }

    private boolean allPairsAreRank1Seeds(Set<Integer> triple) {
        if (triple == null || triple.size() != 3) return false;

        List<Integer> t = new ArrayList<>(triple);

        for (int i = 0; i < t.size(); i++) {
            for (int j = i + 1; j < t.size(); j++) {
                Set<Integer> pair = new HashSet<>();
                pair.add(t.get(i));
                pair.add(t.get(j));

                if (ranksByTest(pair) != 1) {
                    return false;
                }
            }
        }

        return true;
    }

    private boolean allSingletonSplitsHavePositiveInternalRank(Set<Integer> cluster) {
        if (cluster == null || cluster.size() < 3) return false;

        List<Integer> c = new ArrayList<>(cluster);

        for (int v : c) {
            List<Integer> rest = new ArrayList<>(c);
            rest.remove((Integer) v);

            if (rest.isEmpty()) return false;

            int[] left = new int[]{v};
            int[] right = rest.stream().mapToInt(Integer::intValue).toArray();

            int r = RankTests.estimateWilksRank(S, left, right, nEff, alpha);
            if (r == 0) {
                return false;
            }
        }

        return true;
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
        rankCache.clear();
        discoveryRankCache.clear(); // derived discovery alpha depends on alpha
    }

    /**
     * Sets the verbose mode for the application or process.
     *
     * @param verbose a boolean value where {@code true} enables verbose mode and {@code false} disables it.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Returns the discovery alpha to use for seed finding and cluster growing.
     * If set explicitly, returns that value. Otherwise derives an N-adaptive
     * value: scale = sqrt(10000 / nEff), capped so the result never exceeds 0.20.
     * At nEff=10000 this returns alpha; at nEff=1000 roughly 3.2x alpha;
     * at nEff=500 roughly 4.5x alpha.
     */
    private double getDiscoveryAlpha() {
        if (discoveryAlpha > 0.0) {
            return discoveryAlpha;
        }
        double scale = Math.sqrt(10000.0 / Math.max(nEff, 50));
        return Math.min(0.20, alpha * scale);
    }

    /**
     * Sets an explicit discovery alpha, overriding the adaptive default.
     *
     * @param discoveryAlpha the discovery alpha; must be in (0, 1)
     */
    public void setDiscoveryAlpha(double discoveryAlpha) {
        if (discoveryAlpha <= 0.0 || discoveryAlpha >= 1.0) {
            throw new IllegalArgumentException("discoveryAlpha must be in (0, 1).");
        }
        this.discoveryAlpha = discoveryAlpha;
        discoveryRankCache.clear();
    }

    /**
     * Computes the rank of a cluster against its complement at an explicit
     * alpha level, bypassing the instance alpha field.
     */
    private int rankWithAlpha(Set<Integer> cluster, double a) {
        List<Integer> ySet = new ArrayList<>(cluster);
        List<Integer> xSet = new ArrayList<>(variables);
        xSet.removeAll(ySet);
        int[] xIndices = xSet.stream().mapToInt(Integer::intValue).toArray();
        int[] yIndices = ySet.stream().mapToInt(Integer::intValue).toArray();
        return estimateWilksRank(S, xIndices, yIndices, nEff, a);
    }

    /**
     * Cached rank lookup at discovery alpha. Reuses the main cache when
     * discovery alpha equals validation alpha (large-N case).
     */
    private int ranksByDiscovery(Set<Integer> cluster) {
        double da = getDiscoveryAlpha();
        if (da == alpha) {
            return ranksByTest(cluster);
        }
        return discoveryRankCache.computeIfAbsent(
                new Key(cluster), _k -> rankWithAlpha(cluster, da));
    }

    /**
     * Fast rank lookup at an explicit alpha, routing to the appropriate cache.
     * Used inside the parallel stream in findClustersAtRank.
     */
    private int lookupRankFastAtAlpha(int[] ids, double a) {
        if (a == alpha) {
            return lookupRankFast(ids);
        }
        return discoveryRankCache.computeIfAbsent(new Key(ids), _k -> {
            Set<Integer> s = new HashSet<>(ids.length * 2);
            for (int x : ids) s.add(x);
            return rankWithAlpha(s, a);
        });
    }

    /**
     * Returns the minimum absolute pairwise correlation threshold for a pair
     * to be considered significantly correlated at significance level {@code a}.
     * Uses Fisher's z-test. Pairs with |r| below this threshold cannot be
     * rank-1 seeds and are rejected by the pre-screen without a rank test.
     */
    private double corrSignificanceThreshold(double a) {
        double z = StatUtils.getZForAlpha(a);
        return Math.tanh(z / Math.sqrt(Math.max(nEff - 3, 1)));
    }

    /**
     * Returns true iff every pair within {@code ids} has |r| >= rThresh.
     * O(k^2) and cheap relative to a rank test.
     */
    private boolean allPairsSignificant(int[] ids, double rThresh) {
        for (int i = 0; i < ids.length; i++) {
            for (int j = i + 1; j < ids.length; j++) {
                if (Math.abs(S.get(ids[i], ids[j])) < rThresh) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Refine a cluster via conditional-rank trimming.
     *
     * <p><b>Rule 3 (implemented).</b> Remove a subset Z â C with |Z| â¥ r_C if rank(C\Z, D | Z) = 0. Interpretation:
     * Z acts as an observed bottleneck that d-separates C\Z from D in a pure DAG without latents. In true latent
     * clusters (with noisy indicators), conditioning on small Z cannot annihilate the latent contribution generically,
     * so genuine clusters survive this trimming asymptotically.
     *
     * <p><b>Notes.</b> (i) We iterate until no removal fires; (ii) We do not mutate the input set; (iii) We cap |Z| by
     * r_C which matches the latent boundary dimension and is both theoretically natural and computationally efficient.
     *
     * @param original the candidate cluster (not mutated)
     * @param rC       intended rank of the cluster (â¥0)
     * @return refined cluster (possibly smaller); empty set if eliminated
     */
    private Set<Integer> refineClustersByConditionalRanks(Set<Integer> original, int rC) {
        if (original == null || original.isEmpty()) return Collections.emptySet();
        if (rC < 0) rC = 0;


        // Work on a copy to avoid mutating keys already in maps
        Set<Integer> Cset = new HashSet<>(original);
        boolean changed;

        do {
            changed = false;

            // Recompute lists each pass
            List<Integer> D = allVariables();
            D.removeAll(Cset);

            // --- Rule 3: find Z with |Z| â¥ rC and rank(C\Z, D | Z) = 0, remove Z
            if (!Cset.isEmpty() && rC > 0) {
                List<Integer> Cnow = new ArrayList<>(Cset);
                List<Integer> Dnow = allVariables();
                Dnow.removeAll(Cset);

                SublistGenerator gen2 = new SublistGenerator(Cnow.size(), TMath.min(Cnow.size() - 1, rC));
                int[] choice2;
                while ((choice2 = gen2.next()) != null) {
                    if (choice2.length < rC) continue;

                    List<Integer> Z = new ArrayList<>();
                    for (int i : choice2) Z.add(Cnow.get(i));

                    List<Integer> _C = new ArrayList<>(Cnow);
                    _C.removeAll(Z);

                    if (_C.isEmpty()) continue;

                    int[] _cArray = _C.stream().mapToInt(Integer::intValue).toArray();
                    int[] dArray = Dnow.stream().mapToInt(Integer::intValue).toArray();
                    int[] zArray = Z.stream().mapToInt(Integer::intValue).toArray();

                    int rZ = RankTests.estimateWilksRankConditioned(S, _cArray, dArray, zArray, nEff, alpha);
                    if (rZ == 0) {
                        // offending subset is Z â remove Z from the cluster
                        Z.forEach(Cset::remove);
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

    private boolean removeClustersBecauseOfRank0Internally(SimpleMatrix S, Set<Integer> cluster,
                                                           int expectedSampleSize, double alpha) {
        List<Integer> C = new ArrayList<>(cluster);

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

            int r = RankTests.estimateWilksRank(S, c1Array, c2Array, expectedSampleSize, alpha);

            if (r == 0) {
                // l is only used for the log message; a missing entry means the cluster
                // is being checked before registration (e.g., during augmentation).
                Integer registeredRank = clusterToRank.get(cluster);
                int minpq = TMath.min(c1Array.length, c2Array.length);
                String lStr = (registeredRank != null)
                        ? String.valueOf(TMath.min(minpq, TMath.max(0, registeredRank)))
                        : "unregistered";

                log("Deficient! rank(" + toNamesCluster(C1, nodes) + ", "
                        + toNamesCluster(C2, nodes) + ") = " + r + " < " + lStr
                        + "; removing " + toNamesCluster(cluster));
                return true;
            }
        }

        return false;
    }

    private int ranksByTest(Set<Integer> cluster) {
        return rankCache.computeIfAbsent(new Key(cluster), _k -> rank(cluster));
    }

    private int rank(Set<Integer> cluster) {
        List<Integer> ySet = new ArrayList<>(cluster);
        List<Integer> xSet = new ArrayList<>(variables);
        xSet.removeAll(ySet);

        int[] xIndices = new int[xSet.size()];
        int[] yIndices = new int[ySet.size()];
        for (int i = 0; i < xSet.size(); i++) xIndices[i] = xSet.get(i);
        for (int i = 0; i < ySet.size(); i++) yIndices[i] = ySet.get(i);

        return estimateWilksRank(S, xIndices, yIndices, nEff, alpha);
    }

    private void log(String s) {
        if (verbose) TetradLogger.getInstance().log(s);
    }

    private String toNamesCluster(Set<Integer> cluster) {
        return cluster.stream().map(i -> nodes.get(i).getName()).collect(Collectors.joining(" ", "{", "}"));
    }

    /**
     * Returns the effective sample size.
     *
     * @return the effective sample size
     */
    public int getEffectiveSampleSize() {
        return nEff;
    }

    /**
     * Sets the expected sample size used in calculations. The expected sample size must be either -1, indicating it
     * should default to the current sample size, or a positive integer greater than 0.
     *
     * @param nEff the expected sample size to be set. Must be -1 or a positive integer greater than 0.
     * @throws IllegalArgumentException if the provided expected sample size is not -1 and less than or equal to 0.
     */
    public void setEffectiveSampleSize(int nEff) {
        this.nEff = nEff < 0 ? sampleSize : nEff;
        rankCache.clear();
        discoveryRankCache.clear(); // derived discovery alpha depends on nEff
    }

    /**
     * The algorithm will consider ranks from 0 up to this value, rMax.
     *
     * @param rMax The maximum rank to consider.
     */
    public void setRmax(int rMax) {
        this.rMax = rMax;
    }

    /**
     * Sets the minimum redundancy value. Clusters of size rank + 1 can be unstable, as cross-checking is not possible
     * for them. Setting this value to a number minRedundancy greater or equal to than 0 will tell the algorithm to not
     * include clusters of size less than rank + 1 + minRedundancy.
     *
     * @param minRedundancy the minimum redundancy value; if less than 0, it is automatically set to 0.
     */
    public void setMinRedundancy(int minRedundancy) {
        if (minRedundancy < 0) throw new IllegalArgumentException("Min redundancy must be >= 0");
        this.minRedundancy = minRedundancy;
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
