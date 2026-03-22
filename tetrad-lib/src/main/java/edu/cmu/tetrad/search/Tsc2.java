/// ////////////////////////////////////////////////////////////////////////////
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software; see the GNU General Public License v3+.   //
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
 * Tsc2 extends the TSC (Trek Separation Clustering) algorithm to handle more
 * general latent-variable graphs, including:
 *
 * <ul>
 *   <li>Shared children: observed variables that are children of two or more
 *       latent variables simultaneously (e.g., X10 in Figure 1 of Dong 2024).</li>
 *   <li>Observed parents: observed variables that are causes of latent variables
 *       (e.g., X1 → L2 in Figure 1).</li>
 * </ul>
 *
 * <p>Key changes from {@link Tsc}:
 * <ol>
 *   <li>The {@code remainingVars.removeAll(used)} step is replaced with a
 *       per-variable load-bearing check: a variable is only excluded from
 *       future cluster search if removing it from its cluster reduces that
 *       cluster's rank to zero. Variables that are merely correlated with a
 *       cluster (but not load-bearing) remain available as candidates for
 *       additional clusters.</li>
 *   <li>{@code removeOverlappingClusters} identifies shared children (variables
 *       appearing in 2+ clusters) and does not exclude them from kept clusters,
 *       only committing non-shared-child variables to {@code used}.</li>
 *   <li>A new {@link #detectSharedChildren} post-processing step scans all
 *       variables and adds them to any cluster for which they have rank-1
 *       cross-covariance.</li>
 *   <li>A new {@link #detectObservedParents} method identifies observed
 *       variables that are parents of latents (rank-1 with a cluster but
 *       rank-0 with all others after partialling).</li>
 * </ol>
 *
 * @author josephramsey
 */
public class Tsc2 implements EffectiveSampleSizeSettable {
    private final List<Node> nodes;
    private final List<Integer> variables;
    private final int sampleSize;
    private final SimpleMatrix S;
    private final Map<Key, Integer> rankCache = new ConcurrentHashMap<>();
    private final Map<Key, Integer> discoveryRankCache = new ConcurrentHashMap<>();
    private int nEff = -1;
    private double alpha = 0.01;
    private boolean verbose = false;
    private Map<Set<Integer>, Integer> clusterToRank;
    private int rMax = 3;
    private int minRedundancy = 1;
    private double discoveryAlpha = -1.0;

    // ---- New fields for extended results ----
    /**
     * Variables identified as shared children (members of 2+ clusters).
     * Populated after {@link #findClusters()}.
     */
    private Set<Integer> sharedChildren = new LinkedHashSet<>();

    /**
     * Map from observed variable index to the list of clusters it is a parent of.
     * Populated by {@link #detectObservedParents(Map)}.
     */
    private Map<Integer, List<Set<Integer>>> observedParents = new LinkedHashMap<>();

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Constructs a Tsc2 instance.
     *
     * @param variables the variables
     * @param cov       the covariance matrix
     */
    public Tsc2(List<Node> variables, CovarianceMatrix cov) {
        this.nodes = new ArrayList<>(variables);
        this.variables = new ArrayList<>(variables.size());
        for (int i = 0; i < variables.size(); i++) this.variables.add(i);
        this.S = new CorrelationMatrix(cov).getMatrix().getSimpleMatrix();
        this.sampleSize = cov.getSampleSize();
        setEffectiveSampleSize(-1);
    }

    // -------------------------------------------------------------------------
    // Public static utilities (unchanged from Tsc)
    // -------------------------------------------------------------------------

    /**
     * Formats a cluster as a bracketed node-name string.
     */
    public static @NotNull StringBuilder toNamesCluster(Collection<Integer> cluster,
                                                        List<Node> nodes) {
        StringBuilder sb = new StringBuilder("[");
        int count = 0;
        for (Integer var : cluster) {
            sb.append(nodes.get(var));
            if (count++ < cluster.size() - 1) sb.append(", ");
        }
        sb.append("]");
        return sb;
    }

    /**
     * Formats a set of clusters as a semicolon-separated string.
     */
    public static @NotNull String toNamesClusters(Set<Set<Integer>> clusters,
                                                  List<Node> nodes) {
        StringBuilder sb = new StringBuilder();
        int count0 = 0;
        for (Collection<Integer> cluster : clusters) {
            StringBuilder _sb = toNamesCluster(cluster, nodes);
            if (count0++ < clusters.size() - 1) _sb.append("; ");
            sb.append(_sb);
        }
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Core cluster search (modified from Tsc)
    // -------------------------------------------------------------------------

    /**
     * Finds clusters at a given rank using parallel enumeration.
     */
    public Set<Set<Integer>> findClustersAtRank(List<Integer> vars, int size,
                                                int rank, double a) {
        log("findClustersAtRank size=" + size + " rank=" + rank
                + " ess=" + nEff + " alpha=" + a);

        final int n = vars.size();
        final int k = size;
        if (k <= 0 || k > n) return Collections.emptySet();

        final double rThresh = corrSignificanceThreshold(Math.min(a * 4.0, 0.30));

        return IntStream.range(0, n - k + 1).parallel().mapToObj(start -> {
            Set<Set<Integer>> out = ConcurrentHashMap.newKeySet();
            int[] comb = new int[k];
            for (int i = 0; i < k; i++) comb[i] = start + i;

            while (true) {
                if (comb[0] != start) break;

                int[] ids = new int[k];
                for (int i = 0; i < k; i++) ids[i] = vars.get(comb[i]);

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
     * Finds clusters at rank using the instance alpha.
     */
    public Set<Set<Integer>> findClustersAtRank(List<Integer> vars, int size, int rank) {
        return findClustersAtRank(vars, size, rank, this.alpha);
    }

    /**
     * Main entry point: finds clusters, then detects shared children and
     * observed parents.
     *
     * @return map from cluster to rank
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
            if (size >= remainingVars.size()) continue;

            log("EXAMINING SIZE " + size + " RANK=" + rank
                    + " REMAINING=" + remainingVars.size());

            Set<Set<Integer>> P = findClustersAtRank(remainingVars, size, rank, da);

            if (verbose) {
                TetradLogger.getInstance().log("Base clusters size=" + size
                        + " rank=" + rank + ": "
                        + (P.isEmpty() ? "NONE" : toNamesClusters(P, nodes)));
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
                if (seed.size() > remainingVars.size() - seed.size()) continue;

                Set<Integer> cluster = new HashSet<>(seed);
                int seedRankShown = ranksByTest(seed);
                log("Seed: " + toNamesCluster(seed) + " rank=" + seedRankShown);

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
                        int minSize = rank + 1 + minRedundancy;

                        if (rankOfUnion == rank && union.size() >= minSize) {
                            cluster = union;
                            it.remove();
                            extended = true;
                            break;
                        }
                    }
                } while (extended);

                int clusterRank = ranksByTest(cluster);
                int minSize = rank + 1 + minRedundancy;

                if (clusterRank == rank && cluster.size() >= minSize) {
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
                            int rZ = RankTests.estimateWilksRankConditioned(
                                    S, cmz, dArray, zArr, nEff, alpha);
                            if (rZ == 0) {
                                collapses = true;
                                break;
                            }
                        }
                    }

                    if (collapses) {
                        log("Skipping cluster (singleton conditioning collapses rank): "
                                + toNamesCluster(cluster));
                    } else {
                        newClusters.removeIf(cluster::containsAll);
                        log("Adding cluster: " + toNamesCluster(cluster)
                                + " rank=" + clusterRank);
                        newClusters.add(cluster);
                        used.addAll(cluster);
                    }
                }
            }

            // ---- Augmentation step (unchanged from Tsc) ----
            Set<Set<Integer>> P2 = new HashSet<>(P);
            boolean didAugment = false;

            for (Set<Integer> C1 : new HashSet<>(newClusters)) {
                if (Thread.currentThread().isInterrupted()) break;
                int _size = C1.size();

                Set<Integer> usedMinusC1 = new HashSet<>(used);
                usedMinusC1.removeAll(C1);

                for (Set<Integer> _C : P2) {
                    if (Thread.currentThread().isInterrupted()) break;
                    if (!Collections.disjoint(_C, usedMinusC1)) continue;

                    Set<Integer> C2 = new HashSet<>(C1);
                    C2.addAll(_C);
                    if (C2.size() > this.variables.size() - C2.size()) continue;

                    Set<Integer> delta = new HashSet<>(C2);
                    delta.removeAll(C1);
                    if (!Collections.disjoint(delta, usedMinusC1)) continue;

                    int newRank = ranksByDiscovery(C2);
                    int rankC1 = ranksByDiscovery(C1);

                    if (C2.size() == _size + 1
                            && rankC1 == rank
                            && newRank == rank - 1
                            && !removeClustersBecauseOfRank0Internally(S, C2, nEff, alpha)) {

                        if (newClusters.contains(C2)) continue;
                        newClusters.remove(C1);
                        newClusters.add(C2);
                        used.removeAll(C1);
                        used.addAll(C2);
                        log("Augmenting " + toNamesCluster(C1) + " → "
                                + toNamesCluster(C2)
                                + " (rank drop " + rank + "→" + newRank + ").");
                        didAugment = true;
                        break;
                    }
                }
            }

            if (!didAugment) log("No augmentations were needed.");

            for (Set<Integer> cluster : new ArrayList<>(newClusters))
                clusterToRank.put(cluster, rank);

            for (Set<Integer> _C : newClusters) used.addAll(_C);

            // ---- CHANGE 1: load-bearing check instead of removeAll(used) ----
            // A variable is excluded only if removing it from its cluster
            // drops that cluster's rank to 0. Variables that are merely
            // correlated (but not structurally essential to their cluster)
            // remain available as shared-child candidates.
            for (Set<Integer> cluster : newClusters) {
                int clusterRank = ranksByTest(cluster);
                for (Integer v : cluster) {
                    Set<Integer> withoutV = new HashSet<>(cluster);
                    withoutV.remove(v);
                    if (withoutV.isEmpty()) {
                        remainingVars.remove(v);
                        continue;
                    }
                    int rankWithoutV = ranksByTest(withoutV);
                    if (rankWithoutV == 0) {
                        // v is structurally essential — dropping it kills the cluster
                        remainingVars.remove(v);
                    }
                    // otherwise leave v available (potential shared child)
                }
            }
        }

        // ---- Refinement passes (unchanged from Tsc) ----
        log("Removing clusters with size <= rank + 1 + minRedundancy.");
        for (Set<Integer> cluster : new HashSet<>(clusterToRank.keySet())) {
            int r = TMath.max(0, clusterToRank.getOrDefault(cluster, 0));
            int minSize = r + 1 + minRedundancy;
            if (cluster.size() < minSize) {
                clusterToRank.remove(cluster);
                log("Removing cluster (insufficient redundancy): "
                        + toNamesCluster(cluster));
            }
        }

        log("Refining clusters by conditional ranks.");
        boolean changedAny = false;
        for (Set<Integer> cluster : new HashSet<>(clusterToRank.keySet())) {
            int rC = ranksByTest(cluster);
            Set<Integer> refined = refineClustersByConditionalRanks(cluster, rC);

            if (refined.size() < 2) {
                clusterToRank.remove(cluster);
                changedAny = true;
                log("Cluster eliminated after refinement: " + toNamesCluster(cluster));
                continue;
            }

            int newRank = ranksByTest(refined);
            int minSize2 = newRank + 1 + minRedundancy;
            if (refined.size() < minSize2) {
                clusterToRank.remove(cluster);
                changedAny = true;
                log("Refined cluster rejected (too small): "
                        + toNamesCluster(cluster) + " → " + toNamesCluster(refined));
                continue;
            }
            clusterToRank.put(refined, newRank);
            changedAny = true;
        }
        if (!changedAny) log("No cluster refinement needed.");

        boolean penultimateRemoved = false;
        for (Set<Integer> cluster : new HashSet<>(clusterToRank.keySet())) {
            if (removeClustersBecauseOfRank0Internally(S, cluster, nEff, alpha)) {
                clusterToRank.remove(cluster);
                penultimateRemoved = true;
            }
        }
        if (!penultimateRemoved) log("No penultimate clusters removed.");

        if (clusterToRank.isEmpty()) rescueAndAddTriples(clusterToRank);

        // ---- CHANGE 2/3: shared-child-aware overlap removal ----
        clusterToRank = removeOverlappingClusters(clusterToRank);

// ---- NEW: detect shared children ----
// Use a plain list of (clusterSet, rank) pairs throughout so we never
// mutate a live HashMap key. Only build the final map at the very end.
        List<Map.Entry<Set<Integer>, Integer>> entries = new ArrayList<>();
        for (Map.Entry<Set<Integer>, Integer> entry : clusterToRank.entrySet()) {
            entries.add(new AbstractMap.SimpleEntry<>(
                    new HashSet<>(entry.getKey()), entry.getValue()));
        }

        Set<Integer> allClusterVars = new HashSet<>();
        for (Map.Entry<Set<Integer>, Integer> e : entries) allClusterVars.addAll(e.getKey());

        sharedChildren = new LinkedHashSet<>();
        Set<Integer> parentCandidates = new HashSet<>();

// First pass: identify parent candidates so we don't misclassify them as children.
        for (Integer v : allVariables()) {
            if (allClusterVars.contains(v)) continue;
            for (Map.Entry<Set<Integer>, Integer> e : entries) {
                int[] vArr = {v};
                int[] cArr = e.getKey().stream().mapToInt(Integer::intValue).toArray();
                if (estimateWilksRank(S, vArr, cArr, nEff, alpha) != 1) continue;

                List<Integer> complement = allVariables();
                complement.removeAll(e.getKey());
                complement.remove(v);
                if (complement.isEmpty()) continue;

                int[] compArr = complement.stream().mapToInt(Integer::intValue).toArray();
                int rCond = RankTests.estimateWilksRankConditioned(
                        S, vArr, compArr, cArr, nEff, alpha);
                if (rCond == 0) {
                    parentCandidates.add(v);
                    break;
                }
            }
        }

// Second pass: shared/late child detection, skipping parent candidates.
        for (Integer v : allVariables()) {
            if (allClusterVars.contains(v)) continue;
            if (parentCandidates.contains(v)) continue;
            List<Set<Integer>> matching = new ArrayList<>();
            for (Map.Entry<Set<Integer>, Integer> e : entries) {
                int[] vArr = {v};
                int[] cArr = e.getKey().stream().mapToInt(Integer::intValue).toArray();
                if (estimateWilksRank(S, vArr, cArr, nEff, alpha) == 1) matching.add(e.getKey());
            }
            if (matching.size() >= 2) {
                matching.forEach(c -> c.add(v));
                sharedChildren.add(v);
                log("Shared child: " + nodes.get(v).getName());
            } else if (matching.size() == 1) {
                matching.get(0).add(v);
                log("Late child: " + nodes.get(v).getName());
            }
        }

// Now build the final map from fresh copies of the (now fully populated) sets.
        Map<Set<Integer>, Integer> rebuiltMap = new LinkedHashMap<>();
        for (Map.Entry<Set<Integer>, Integer> e : entries) {
            rebuiltMap.put(new HashSet<>(e.getKey()), e.getValue());
        }
        clusterToRank = rebuiltMap;

// ---- NEW: detect observed parents ----
        observedParents = detectObservedParents(clusterToRank);
        if (!observedParents.isEmpty()) {
            log("Observed parents detected:");
            for (Map.Entry<Integer, List<Set<Integer>>> e : observedParents.entrySet()) {
                log("  " + nodes.get(e.getKey()).getName()
                        + " → "
                        + e.getValue().stream()
                        .map(c -> toNamesCluster(c).toString())
                        .collect(Collectors.joining(", ")));
            }
        }

        log("Final clusters: " + toNamesClusters(clusterToRank.keySet(), nodes));
        return clusterToRank;
    }

    // -------------------------------------------------------------------------
    // CHANGE 2/3: shared-child-aware removeOverlappingClusters
    // -------------------------------------------------------------------------

    private Map<Set<Integer>, Integer> removeOverlappingClusters(
            Map<Set<Integer>, Integer> clustersToRank) {

        if (clustersToRank == null || clustersToRank.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Set<Integer>> ordered = new ArrayList<>(clustersToRank.keySet());
        ordered.sort((a, b) -> {
            int c = Integer.compare(b.size(), a.size());
            if (c != 0) return c;
            int ra = clustersToRank.getOrDefault(a, Integer.MAX_VALUE);
            int rb = clustersToRank.getOrDefault(b, Integer.MAX_VALUE);
            c = Integer.compare(ra, rb);
            if (c != 0) return c;
            return compareClustersLex(a, b);
        });

        // Identify shared children: variables appearing in 2+ clusters.
        Map<Integer, Integer> varClusterCount = new HashMap<>();
        for (Set<Integer> cluster : ordered) {
            for (Integer v : cluster) varClusterCount.merge(v, 1, Integer::sum);
        }
        Set<Integer> shared = new HashSet<>();
        for (Map.Entry<Integer, Integer> e : varClusterCount.entrySet()) {
            if (e.getValue() >= 2) shared.add(e.getKey());
        }

        Map<Set<Integer>, Integer> kept = new LinkedHashMap<>();
        Set<Integer> used = new HashSet<>();

        for (Set<Integer> cluster : ordered) {
            // Core variables: non-shared-child members that must be unique.
            Set<Integer> coreVars = new HashSet<>(cluster);
            coreVars.removeAll(shared);

            if (Collections.disjoint(coreVars, used)) {
                Set<Integer> copy = new HashSet<>(cluster);
                kept.put(copy, clustersToRank.get(cluster));
                // Only commit core variables — shared children remain available.
                used.addAll(coreVars);
            } else {
                log("Removing overlapping cluster " + toNamesCluster(cluster)
                        + " rank=" + clustersToRank.get(cluster));
            }
        }

        return kept;
    }

    // -------------------------------------------------------------------------
    // NEW: detectSharedChildren
    // -------------------------------------------------------------------------

    /**
     * Scans all variables and, for each one that is not yet in a cluster,
     * checks whether it has rank-1 cross-covariance with the members of any
     * existing cluster. Variables with rank-1 cross-covariance to 2+ clusters
     * are added to each such cluster and recorded as shared children.
     *
     * @param clusterToRank the current cluster map (mutated in place)
     * @return the set of shared-child variable indices
     */
    private Set<Integer> detectSharedChildren(Map<Set<Integer>, Integer> clusterToRank) {
        Set<Integer> allClusterVars = new HashSet<>();
        for (Set<Integer> c : clusterToRank.keySet()) allClusterVars.addAll(c);

        List<Set<Integer>> clusterList = new ArrayList<>(clusterToRank.keySet());
        Set<Integer> shared = new LinkedHashSet<>();

        for (Integer v : allVariables()) {
            if (allClusterVars.contains(v)) continue; // already in a cluster

            List<Set<Integer>> matchingClusters = new ArrayList<>();
            for (Set<Integer> cluster : clusterList) {
                int[] vArr = {v};
                int[] cArr = cluster.stream().mapToInt(Integer::intValue).toArray();
                int r = estimateWilksRank(S, vArr, cArr, nEff, alpha);
                if (r == 1) matchingClusters.add(cluster);
            }

            if (matchingClusters.size() >= 2) {
                // v is a shared child of 2+ latents
                for (Set<Integer> c : matchingClusters) c.add(v);
                shared.add(v);
                log("Shared child: " + nodes.get(v).getName()
                        + " added to " + matchingClusters.size() + " clusters");
            } else if (matchingClusters.size() == 1) {
                // Ordinary child missed in the main search — add it quietly.
                matchingClusters.get(0).add(v);
                log("Late child: " + nodes.get(v).getName()
                        + " added to " + toNamesCluster(matchingClusters.get(0)));
            }
        }

        return shared;
    }

    // -------------------------------------------------------------------------
    // NEW: detectObservedParents
    // -------------------------------------------------------------------------

    /**
     * Identifies observed variables that are parents (causes) of latent clusters.
     *
     * <p>A variable v is considered an observed parent of cluster C if:
     * <ol>
     *   <li>v is not a member of C.</li>
     *   <li>rank(v, C) == 1 — v has a single-channel relationship with C's
     *       indicators (it drives them through one latent).</li>
     *   <li>rank(v, C | children(C)) drops to 0 when conditioning on the
     *       other members of C — i.e., once we account for what the cluster
     *       already explains, v adds nothing extra, consistent with v being
     *       upstream rather than a peer indicator.</li>
     * </ol>
     *
     * <p>This heuristic correctly identifies upstream causes in MIMIC-style
     * and Figure-1-style graphs while avoiding false positives from mere
     * correlated children.
     *
     * @param clusterToRank the cluster map
     * @return map from variable index to list of clusters it parents
     */
    public Map<Integer, List<Set<Integer>>> detectObservedParents(
            Map<Set<Integer>, Integer> clusterToRank) {

        Map<Integer, List<Set<Integer>>> parents = new LinkedHashMap<>();
        Set<Integer> allClusterVars = new HashSet<>();
        for (Set<Integer> c : clusterToRank.keySet()) allClusterVars.addAll(c);

        for (Integer v : allVariables()) {
            if (allClusterVars.contains(v)) continue;

            List<Set<Integer>> parentOf = new ArrayList<>();

            for (Set<Integer> cluster : clusterToRank.keySet()) {
                int[] vArr = {v};
                int[] cArr = cluster.stream().mapToInt(Integer::intValue).toArray();

                // Condition 1: v has rank-1 relationship with the cluster.
                int r = estimateWilksRank(S, vArr, cArr, nEff, alpha);
                if (r != 1) continue;

                // Condition 2: conditioning on the cluster members drives
                // the rank of (v, complement) down — v's correlation with
                // the rest of the graph is explained by this cluster.
                List<Integer> complement = allVariables();
                complement.removeAll(cluster);
                complement.remove(v);

                if (complement.isEmpty()) continue;

                int[] compArr = complement.stream().mapToInt(Integer::intValue).toArray();
                int rCond = RankTests.estimateWilksRankConditioned(
                        S, vArr, compArr, cArr, nEff, alpha);

                if (rCond == 0) {
                    parentOf.add(cluster);
                }
            }

            if (!parentOf.isEmpty()) {
                parents.put(v, parentOf);
            }
        }

        return parents;
    }

    // -------------------------------------------------------------------------
    // New public getters for extended results
    // -------------------------------------------------------------------------

    /**
     * Returns the set of variable indices identified as shared children after
     * the last call to {@link #findClusters()}.
     *
     * @return shared child indices
     */
    public Set<Integer> getSharedChildren() {
        return Collections.unmodifiableSet(sharedChildren);
    }

    /**
     * Returns the map of observed-parent variable indices to the clusters they
     * parent, after the last call to {@link #findClusters()}.
     *
     * @return observed parent map
     */
    public Map<Integer, List<Set<Integer>>> getObservedParents() {
        return Collections.unmodifiableMap(observedParents);
    }

    /**
     * Convenience: returns the node for a variable index.
     *
     * @param idx variable index
     * @return the node
     */
    public Node getNode(int idx) {
        return nodes.get(idx);
    }

    /**
     * Returns all variable indices.
     */
    public List<Integer> getAllVariableIndices() {
        return allVariables();
    }

    // -------------------------------------------------------------------------
    // Setters (unchanged from Tsc)
    // -------------------------------------------------------------------------

    /** Sets alpha. */
    public void setAlpha(double alpha) {
        this.alpha = alpha;
        rankCache.clear();
        discoveryRankCache.clear();
    }

    /** Sets verbose. */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /** Returns effective sample size. */
    public int getEffectiveSampleSize() {
        return nEff;
    }

    /** Sets effective sample size. */
    @Override
    public void setEffectiveSampleSize(int nEff) {
        this.nEff = nEff < 0 ? sampleSize : nEff;
        rankCache.clear();
        discoveryRankCache.clear();
    }

    /** Sets maximum rank. */
    public void setRmax(int rMax) {
        this.rMax = rMax;
    }

    /** Sets minimum redundancy. */
    public void setMinRedundancy(int minRedundancy) {
        if (minRedundancy < 0) throw new IllegalArgumentException("Min redundancy must be >= 0");
        this.minRedundancy = minRedundancy;
    }

    private int lookupRankFast(int[] ids) {
        return rankCache.computeIfAbsent(new Key(ids), k -> {
            Set<Integer> s = new HashSet<>(ids.length * 2);
            for (int x : ids) s.add(x);
            return rank(s);
        });
    }

    // -------------------------------------------------------------------------
    // Private methods (unchanged from Tsc except where noted)
    // -------------------------------------------------------------------------

    private int rankWithAlpha(Set<Integer> cluster, double a) {
        List<Integer> ySet = new ArrayList<>(cluster);
        List<Integer> xSet = new ArrayList<>(variables);
        xSet.removeAll(ySet);
        int[] xIndices = xSet.stream().mapToInt(Integer::intValue).toArray();
        int[] yIndices = ySet.stream().mapToInt(Integer::intValue).toArray();
        return estimateWilksRank(S, xIndices, yIndices, nEff, a);
    }

    private int ranksByDiscovery(Set<Integer> cluster) {
        double da = getDiscoveryAlpha();
        if (da == alpha) return ranksByTest(cluster);
        return discoveryRankCache.computeIfAbsent(
                new Key(cluster), _k -> rankWithAlpha(cluster, da));
    }

    private int lookupRankFastAtAlpha(int[] ids, double a) {
        if (a == alpha) return lookupRankFast(ids);
        return discoveryRankCache.computeIfAbsent(new Key(ids), _k -> {
            Set<Integer> s = new HashSet<>(ids.length * 2);
            for (int x : ids) s.add(x);
            return rankWithAlpha(s, a);
        });
    }

    private double corrSignificanceThreshold(double a) {
        double z = StatUtils.getZForAlpha(a);
        return Math.tanh(z / Math.sqrt(Math.max(nEff - 3, 1)));
    }

    private boolean allPairsSignificant(int[] ids, double rThresh) {
        for (int i = 0; i < ids.length; i++)
            for (int j = i + 1; j < ids.length; j++)
                if (Math.abs(S.get(ids[i], ids[j])) < rThresh) return false;
        return true;
    }

    private Set<Integer> refineClustersByConditionalRanks(Set<Integer> original, int rC) {
        if (original == null || original.isEmpty()) return Collections.emptySet();
        if (rC < 0) rC = 0;

        Set<Integer> Cset = new HashSet<>(original);
        boolean changed;

        do {
            changed = false;
            List<Integer> Cnow = new ArrayList<>(Cset);
            List<Integer> Dnow = allVariables();
            Dnow.removeAll(Cset);

            SublistGenerator gen2 = new SublistGenerator(Cnow.size(),
                    TMath.min(Cnow.size() - 1, rC));
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

                int rZ = RankTests.estimateWilksRankConditioned(
                        S, _cArray, dArray, zArray, nEff, alpha);
                if (rZ == 0) {
                    Z.forEach(Cset::remove);
                    log("Rule 3: removing Z=" + toNamesCluster(new HashSet<>(Z))
                            + " from " + toNamesCluster(new HashSet<>(Cnow)));
                    changed = true;
                    break;
                }
            }
        } while (changed && Cset.size() >= 2);

        return Cset;
    }

    private boolean removeClustersBecauseOfRank0Internally(SimpleMatrix S,
                                                           Set<Integer> cluster,
                                                           int n, double alpha) {
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
            int r = RankTests.estimateWilksRank(S, c1Array, c2Array, n, alpha);

            if (r == 0) {
                log("Internal rank-0 detected in " + toNamesCluster(cluster)
                        + " — removing.");
                return true;
            }
        }
        return false;
    }

    private void rescueAndAddTriples(Map<Set<Integer>, Integer> clusterToRank) {
        Set<Set<Integer>> candidates = rescueIsolatedRank1Triples(allVariables());
        for (Set<Integer> triple : candidates) {
            clusterToRank.put(triple, 1);
            Set<Integer> refined = refineClustersByConditionalRanks(triple, 1);
            if (refined.size() < 2) {
                clusterToRank.remove(triple);
                continue;
            }
            int newRank = ranksByTest(refined);
            int minSize = newRank + 1 + minRedundancy;
            if (refined.size() < minSize) {
                clusterToRank.remove(triple);
                continue;
            }
            if (!refined.equals(triple)) {
                clusterToRank.remove(triple);
                clusterToRank.put(refined, newRank);
            } else {
                clusterToRank.put(triple, newRank);
            }
            if (removeClustersBecauseOfRank0Internally(S, refined, nEff, alpha)) {
                clusterToRank.remove(refined);
            }
        }
    }

    private Set<Set<Integer>> rescueIsolatedRank1Triples(List<Integer> vars) {
        Set<Set<Integer>> rescued = new HashSet<>();
        if (vars.size() < 3) return rescued;
        SublistGenerator gen = new SublistGenerator(vars.size(), 3);
        int[] choice;
        while ((choice = gen.next()) != null) {
            Set<Integer> triple = new HashSet<>();
            for (int i : choice) triple.add(vars.get(i));
            if (!allPairsAreRank1Seeds(triple)) continue;
            if (!allSingletonSplitsHavePositiveInternalRank(triple)) continue;
            rescued.add(triple);
        }
        return rescued;
    }

    private boolean allPairsAreRank1Seeds(Set<Integer> triple) {
        if (triple == null || triple.size() != 3) return false;
        List<Integer> t = new ArrayList<>(triple);
        for (int i = 0; i < t.size(); i++)
            for (int j = i + 1; j < t.size(); j++) {
                Set<Integer> pair = new HashSet<>();
                pair.add(t.get(i));
                pair.add(t.get(j));
                if (ranksByTest(pair) != 1) return false;
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
            if (r == 0) return false;
        }
        return true;
    }

    private List<Integer> allVariables() {
        List<Integer> _variables = new ArrayList<>();
        for (int i = 0; i < this.variables.size(); i++) _variables.add(i);
        return _variables;
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

    private int compareClustersLex(Set<Integer> a, Set<Integer> b) {
        List<Integer> aa = new ArrayList<>(a);
        Collections.sort(aa);
        List<Integer> bb = new ArrayList<>(b);
        Collections.sort(bb);
        int n = Math.min(aa.size(), bb.size());
        for (int i = 0; i < n; i++) {
            int c = Integer.compare(aa.get(i), bb.get(i));
            if (c != 0) return c;
        }
        return Integer.compare(aa.size(), bb.size());
    }

    private double getDiscoveryAlpha() {
        if (discoveryAlpha > 0.0) return discoveryAlpha;
        double scale = Math.sqrt(10000.0 / Math.max(nEff, 50));
        return Math.min(0.20, alpha * scale);
    }

    /** Sets explicit discovery alpha. */
    public void setDiscoveryAlpha(double discoveryAlpha) {
        if (discoveryAlpha <= 0.0 || discoveryAlpha >= 1.0) {
            throw new IllegalArgumentException("discoveryAlpha must be in (0, 1).");
        }
        this.discoveryAlpha = discoveryAlpha;
        discoveryRankCache.clear();
    }

    private void log(String s) {
        if (verbose) TetradLogger.getInstance().log(s);
    }

    private String toNamesCluster(Set<Integer> cluster) {
        return cluster.stream()
                .map(i -> nodes.get(i).getName())
                .collect(Collectors.joining(" ", "{", "}"));
    }

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
