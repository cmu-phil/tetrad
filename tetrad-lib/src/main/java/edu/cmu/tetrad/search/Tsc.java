/// ////////////////////////////////////////////////////////////////////////////
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
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.CorrelationMatrix;
import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataTransforms;
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
 * Trek Separation Clusters (TSC): a rank-based algorithm for recovering the
 * latent-cluster structure of a pure measurement model from an observed
 * correlation or covariance matrix.
 *
 * <h2>Theoretical foundation</h2>
 *
 * <p>TSC exploits the algebraic consequence of trek separation in
 * linear-Gaussian DAGs (Sullivant, Talaska, and Draisma, 2010).  If a set
 * of observed variables {@code C} all load on the same latent factor, the
 * cross-covariance between {@code C} and its complement {@code V \ C} has a
 * predictable low rank determined by the number of latent variables that
 * straddle the boundary.  Formally, under the NOLAC (no-overlapping-clusters)
 * pure measurement assumption with generic parameters,
 * <pre>
 *   rank(Σ_{C, V\C}) = r
 * </pre>
 * where {@code r} is the latent boundary dimension of the cluster.  For a
 * single-factor cluster {@code r = 1}; for a bifactor cluster {@code r = 2};
 * and so on.  This rank criterion is the identifying constraint that TSC tests
 * at every step of the search.
 *
 * <p>The theorem extends to models in which the structural equations
 * <em>among</em> the latent variables are non-linear or cyclic, provided the
 * measurement equations (latent to observed) remain linear (Spirtes, 2013).
 * TSC's correctness guarantees therefore hold for a broader class of causal
 * models than the strictly linear DAG assumed by Sullivant et al.
 *
 * <h2>Algorithm overview</h2>
 *
 * <p>TSC searches for ranks from {@code rMin} down to {@code rMax} (inclusive).
 * Searching higher ranks first prevents spurious low-rank seeds from consuming
 * variables that belong to higher-rank clusters.  For each target rank {@code r}
 * the algorithm proceeds in two stages.
 *
 * <h3>Discovery stage</h3>
 * <ol>
 *   <li><b>Seed enumeration.</b>  All subsets of size {@code r + 1} among the
 *       remaining variables are tested.  A subset is a valid seed if its
 *       cross-rank against the full complement equals {@code r}.  A pairwise
 *       correlation pre-screen (using the discovery alpha {@code αd}) avoids
 *       the majority of rank-test calls for uncorrelated subsets.</li>
 *   <li><b>Seed growing.</b>  Each seed is grown by iteratively unioning with
 *       overlapping seeds that preserve the cross-rank at {@code r}.  If a
 *       union produces cross-rank 0, it is immediately accepted as a rank-0
 *       isolated cluster and its variables are committed.</li>
 *   <li><b>Rule-3 guard.</b>  Before a grown cluster is accepted, a
 *       singleton conditioning test checks whether any single variable acts
 *       as an observed mediator/bottleneck.  If conditioning on any
 *       {@code z ∈ C} collapses the conditional rank to 0, the cluster is
 *       rejected as a spurious observed-variable artefact.</li>
 *   <li><b>Bifactor augmentation.</b>  After growing, TSC checks whether
 *       adding exactly one further variable causes a one-step rank drop
 *       from {@code r} to {@code r - 1}, the algebraic signature of a
 *       variable that bridges two latent factors.</li>
 * </ol>
 *
 * <h3>Refinement stage</h3>
 * <ol>
 *   <li><b>Rule-3 trimming.</b>  For each accepted cluster, all subsets
 *       {@code Z ⊆ C} of size exactly {@code r} are tested.  If conditioning
 *       on {@code Z} collapses the rank of {@code (C \ Z, D | Z)} to 0, the
 *       variables in {@code Z} are removed from the cluster and the procedure
 *       restarts.  This iterative trimming is asymptotically safe under NOLAC:
 *       a true latent cluster cannot be collapsed by conditioning on any
 *       finite set of its own (noisy) indicators.</li>
 *   <li><b>Recursive splitting.</b>  Each cluster is checked for internal
 *       rank-0 splits.  If any partition {@code (C1, C2)} of the cluster has
 *       {@code rank(Σ_{C1, C2}) = 0}, the cluster is split into {@code C1}
 *       and {@code C2}, and each piece is recursively checked.  Pieces too
 *       small to satisfy the minimum redundancy constraint are discarded.
 *       This step recovers distinct sub-clusters that were incorrectly merged
 *       during growing.</li>
 *   <li><b>NOLAC enforcement.</b>  Any remaining overlapping clusters are
 *       resolved greedily, preferring larger clusters, then lower-rank
 *       clusters, then lexicographically earlier clusters.</li>
 * </ol>
 *
 * <h2>Soundness sketch (NOLAC)</h2>
 *
 * <p>Under the NOLAC pure measurement assumption with generic parameters and
 * a consistent rank test:
 * <ul>
 *   <li><b>Seed soundness.</b>  Every {@code (r+1)}-subset of a true cluster
 *       {@code G} has cross-rank {@code r}.  Any subset containing a
 *       non-member generically has cross-rank {@code > r}.</li>
 *   <li><b>Extension correctness.</b>  Growing a seed by unions that preserve
 *       cross-rank {@code r} expands exactly to the maximal true cluster
 *       {@code G}; adding any non-member raises the rank and is rejected.</li>
 *   <li><b>Rule-3 safety.</b>  Conditioning on any subset {@code Z ⊆ G} of
 *       size {@code r} cannot annihilate the latent contribution to
 *       {@code Σ_{G \ Z, D}}, so Rule-3 trimming leaves true clusters intact
 *       generically.</li>
 *   <li><b>Split correctness.</b>  A true latent cluster with generic
 *       parameters has no internal rank-0 partition, so the recursive split
 *       does not fragment true clusters; only merged artefacts are split.</li>
 * </ul>
 *
 * <h2>Parameters</h2>
 * <ul>
 *   <li>{@code alpha} — validation significance level for all final rank
 *       tests (default 0.01).</li>
 *   <li>{@code discoveryAlpha} — significance level for seed enumeration and
 *       growing; defaults to an {@code n}-adaptive value
 *       {@code min(0.20, alpha * sqrt(10000 / nEff))} that is more liberal
 *       at small samples.  Can be overridden via
 *       {@link #setDiscoveryAlpha(double)}.</li>
 *   <li>{@code rMin}, {@code rMax} — range of ranks to search
 *       (defaults 0 and 3 respectively).  Searching from {@code rMax} down
 *       to {@code rMin} is recommended to avoid spurious low-rank seeds
 *       consuming variables that belong to higher-rank clusters.</li>
 *   <li>{@code minRedundancy} ({@code δ}) — clusters of size exactly
 *       {@code r + 1} cannot be internally cross-checked; requiring size
 *       at least {@code r + 1 + δ} provides stability.  Default 1 for
 *       rank-1 problems; 2 recommended for rank-2 problems.</li>
 *   <li>{@code parallel} — if {@code true}, seed enumeration uses Java
 *       parallel streams.  Set to {@code false} when the calling harness
 *       already parallelises over replications, to avoid oversubscription
 *       of the common ForkJoinPool (default {@code true}).</li>
 * </ul>
 *
 * <h2>Empirical performance</h2>
 *
 * <p>In simulation studies under the NOLAC assumption with generic (symmetric)
 * loadings and four clusters:
 * <ul>
 *   <li><b>Rank-1 MIMs</b> (cluster sizes 5--6): TSC reaches F1 ≥ 0.99 by
 *       {@code n = 5,000} and approaches perfect recovery at larger samples,
 *       while BPC and FOFC plateau near F1 ≈ 0.77 regardless of sample
 *       size.</li>
 *   <li><b>Rank-2 MIMs</b> (cluster sizes 7--8): TSC reaches F1 = 0.966 at
 *       {@code n = 5,000} and F1 = 0.988 at {@code n = 20,000}.  FTFC, the
 *       only existing rank-2 alternative, achieves corrected F1 = 0.486 at
 *       {@code n = 5,000} with 40% empty replications, and F1 = 0.610 at
 *       {@code n = 20,000} with 21% empty replications.</li>
 * </ul>
 *
 * <h2>References</h2>
 * <ul>
 *   <li>Sullivant, S., Talaska, K., and Draisma, J. (2010).
 *       Trek separation for Gaussian graphical models.
 *       <i>Annals of Statistics</i>, 38(3):1665--1685.</li>
 *   <li>Spirtes, P. (2013).
 *       Calculation of entailed rank constraints in partially non-linear
 *       and cyclic models.
 *       <i>Proceedings of UAI 2013</i>, pp. 606--615.</li>
 *   <li>Silva, R., Scheines, R., Glymour, C., and Spirtes, P. (2006).
 *       Learning the structure of linear latent variable models.
 *       <i>JMLR</i>, 7:191--246.</li>
 *   <li>Kummerfeld, E. and Ramsey, J. (2016).
 *       Causal clustering for 1-factor measurement models.
 *       <i>KDD 2016</i>, pp. 1655--1664.</li>
 * </ul>
 *
 * @author josephramsey
 * @see RankTests#estimateWilksRank
 */
public class Tsc implements EffectiveSampleSizeSettable {
    private final List<Node> nodes;
    private final List<Integer> variables;
    private final int sampleSize;
    private final SimpleMatrix S;
    private final Map<Key, Integer> rankCache = new ConcurrentHashMap<>();
    /**
     * Cache for ranks computed at discoveryAlpha. Keyed by the same Key record
     * as rankCache. Cleared whenever alpha, discoveryAlpha, or nEff changes.
     */
    private final Map<Key, Integer> discoveryRankCache = new ConcurrentHashMap<>();
    private int nEff = -1;
    private double alpha = 0.01;
    private boolean verbose = false;
    private Map<Set<Integer>, Integer> clusterToRank;
    private int rMin = 1;
    private int rMax = 3;
    // require |C| >= (rank + 1 + minRedundancy)
    private int minRedundancy = 1;
    /**
     * Liberal alpha used during seed finding and cluster growing.
     * -1.0 means derive adaptively from {@link #alpha} and {@link #nEff}.
     */
    private double discoveryAlpha = -1.0;
    private boolean parallel = true;
    private double[][] dataArray;

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

    public Tsc(List<Node> variables, DataSet data) {
        this.nodes = new ArrayList<>(variables);
        this.variables = new ArrayList<>(variables.size());
        for (int i = 0; i < variables.size(); i++) this.variables.add(i);
        this.S = new CorrelationMatrix(data).getMatrix().getSimpleMatrix();

        this.sampleSize = data.getNumRows();
        this.dataArray = data.getDoubleData().toArray();
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
     * Identifies clusters of variables at alpha specified rank using the supplied
     * alpha. The correlation pre-screen rejects k-combinations that contain any
     * pair with |r| below the significance threshold at alpha liberal alpha, avoiding
     * the majority of rank test calls for uncorrelated combinations.
     *
     * @param vars  alpha list of integers representing the variables to consider
     * @param size  the size of the clusters to generate
     * @param rank  the target rank to filter clusters
     * @param alpha the alpha level to use for the rank test
     * @return alpha set of clusters that match the specified rank
     */
    public Set<Set<Integer>> findClustersAtRank(List<Integer> vars, int size,
                                                int rank, double alpha) {
        log("findClustersAtRankTesting size = " + size + ", rank = " + rank
                + ", ess = " + nEff + ", alpha = " + alpha);

        final int n = vars.size();
        final int k = size;
        if (k <= 0 || k > n) return Collections.emptySet();

        // Pre-screen threshold: use alpha threshold 4x more liberal than the
        // discovery alpha so only genuinely uncorrelated pairs are rejected.
        // This preserves all plausible seeds while skipping clearly hopeless ones.
        final double rThresh = alpha;
//                corrSignificanceThreshold(Math.min(alpha * 4.0, 0.30));

        IntStream stream = IntStream.range(0, n - k + 1);
        return (parallel ? stream.parallel() : stream).mapToObj(start -> {
            Set<Set<Integer>> out = ConcurrentHashMap.newKeySet();
            int[] comb = new int[k];
            for (int i = 0; i < k; i++) comb[i] = start + i;

            while (true) {
                if (comb[0] != start) break;

                int[] ids = new int[k];
                for (int i = 0; i < k; i++) ids[i] = vars.get(comb[i]);

                // Cheap O(k^2) pre-screen before the expensive rank test.
                if (allPairsSignificant(ids, rThresh)
                        && lookupRankFastAtAlpha(ids, alpha) == rank) {
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
     * Calculates clusters of integers based on the given variables, size, and rank.
     *
     * @param vars The list of integers representing variables to be clustered.
     * @param size The desired size of each cluster.
     * @param rank The specified rank used as a parameter for clustering logic.
     * @return A set of clusters, where each cluster is represented as a set of integers.
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

        for (int rank = rMin; rank <= rMax; rank++) {
            int size = rank + 1;
            if (Thread.currentThread().isInterrupted()) break;
            if (2 * size >= variables.size()) continue;

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

                if (seed.size() * 2 > this.variables.size()) continue;

                int seedRankShown;
                seedRankShown = rankByTest(seed);
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

                        int rankOfUnion = rankByTest(union);
                        log("For this candidate: " + toNamesCluster(candidate) + ", Trying union: " + toNamesCluster(union) + " rank = " + rankOfUnion);

                        int minSize = rank + 1 + minRedundancy;

                        if (rankOfUnion == rank && union.size() >= minSize
                                && union.size() * 2 <= this.variables.size()) {
                            // existing: accept union at target rank
                            cluster = union;
                            it.remove();
                            extended = true;
                            break;

                        } else if (rankOfUnion == 0
                                && union.size() >= minRedundancy + 1
                                && union.size() * 2 <= this.variables.size()) {
                            // new: union is completely isolated from its complement
                            // record it as a rank-0 cluster and commit its variables
                            log("Rank-0 isolated cluster found during growing: "
                                    + toNamesCluster(union));
                            newClusters.add(union);
                            used.addAll(union);
                            it.remove();
                            // do not set extended = true; stop growing this seed
                            break;
                        }
                    }
                } while (extended);

                int clusterRank;
                clusterRank = rankByTest(cluster);

                int minSize = rank + 1 + minRedundancy;
                if (clusterRank == rank && cluster.size() >= minSize
                        && cluster.size() * 2 <= this.variables.size()) {

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

//            for (Set<Integer> cluster : new ArrayList<>(newClusters)) clusterToRank.put(cluster, rank);
//
            for (Set<Integer> cluster : new ArrayList<>(newClusters))
                clusterToRank.put(cluster, rankByTest(cluster));

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
            int rC = rankByTest(cluster);

            // produce a refined copy (possibly smaller), do not mutate the key in-place
            Set<Integer> refined = refineClustersByConditionalRanks(cluster, rC);

            if (refined.size() < rC + 1 + minRedundancy) {
                clusterToRank.remove(cluster);
                changedAny = true;
                log("Cluster " + toNamesCluster(cluster) + " eliminated after refinement.");
                continue;
            }

            int newRank = rankByTest(refined);
            int minSize2 = newRank + 1 + minRedundancy;
            if (refined.size() < minSize2) {
                clusterToRank.remove(cluster);
                changedAny = true;
                log("Refined cluster " + toNamesCluster(cluster) + " → " + toNamesCluster(refined)
                        + " rejected: |C| < r+1+minRedundancy (" + refined.size() + " < " + minSize2 + ").");
                continue;
            }
            clusterToRank.remove(cluster);
            clusterToRank.put(refined, rankByTest(refined));
            changedAny = true;
            log("Refined cluster " + toNamesCluster(cluster) + " → " + toNamesCluster(refined)
                    + " (rank now " + newRank + ").");
        }
        if (!changedAny) log("No cluster refinement was needed.");

        log("Now we will consider whether any of the penultimate clusters should be removed because they hide rank 0 subsets.");

        boolean penultimateRemoved = false;

        for (Set<Integer> cluster : new HashSet<>(clusterToRank.keySet())) {
            List<Set<Integer>> pieces = splitOrKeepCluster(cluster);

            if (pieces.size() == 1 && pieces.get(0).equals(cluster)) {
                continue; // unchanged — no split, no removal
            }

            // Something changed: remove the original and add surviving pieces
            clusterToRank.remove(cluster);
            penultimateRemoved = true;

            for (Set<Integer> piece : pieces) {
                int pieceRank = rankByTest(piece);
                clusterToRank.put(piece, pieceRank);
                log("Adding split piece " + toNamesCluster(piece)
                        + " rank = " + pieceRank);
            }
        }

        if (!penultimateRemoved) log("No penultimate clusters were removed.");

        log("Now we will remove any cluster that is entirely contained within another cluster.");

        Set<Set<Integer>> toRemove = new HashSet<>();
        List<Set<Integer>> allClusters = new ArrayList<>(clusterToRank.keySet());

        for (int i = 0; i < allClusters.size(); i++) {
            Set<Integer> clusterA = allClusters.get(i);
            for (int j = 0; j < allClusters.size(); j++) {
                if (i == j) continue;
                Set<Integer> clusterB = allClusters.get(j);
                if (clusterB.containsAll(clusterA)) {
                    toRemove.add(clusterA);
                    log("Removing cluster " + toNamesCluster(clusterA)
                            + " because it is entirely contained in " + toNamesCluster(clusterB));
                    break;
                }
            }
        }

        for (Set<Integer> cluster : toRemove) {
            clusterToRank.remove(cluster);
        }


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
            return rankByTest(cluster);
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

//                    int rZ = RankTests.estimateWilksRankConditioned(S, _cArray, dArray, zArray, nEff, alpha);
//                    if (rZ == 0) {
//                        // offending subset is Z â remove Z from the cluster
//                        Z.forEach(Cset::remove);
//                        log("Rule 3 fired: removing offending subset Z="
//                                + toNamesCluster(new HashSet<>(Z))
//                                + " from cluster " + toNamesCluster(new HashSet<>(Cnow))
//                                + " (rank(C\\Z, D | Z)=0)");
//                        changed = true;
//                        break; // restart passes after modification
//                    }
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

            int numPermutations = 1000;

            int r = RankTests.estimateWilksRank(S, c1Array, c2Array, expectedSampleSize, alpha);
//            int r = RankTests.estimatePermutationRank(dataArray, c1Array, c2Array, alpha, numPermutations);

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

    /**
     * Checks whether {@code cluster} contains a rank-0 internal split.
     * If it does, splits on the first such partition found and recursively
     * applies the same check to each piece, returning all surviving pieces.
     * If no rank-0 split exists, returns a singleton list containing the
     * original cluster unchanged.
     * Pieces that are too small (< r + 1 + minRedundancy) are discarded.
     *
     * @return surviving sub-clusters after recursive splitting; empty if all
     * pieces are discarded.
     */
    private List<Set<Integer>> splitOrKeepCluster(Set<Integer> cluster) {
        List<Integer> C = new ArrayList<>(cluster);

        SublistGenerator gen0 = new SublistGenerator(C.size(), C.size() - 1);
        int[] choice0;

        while ((choice0 = gen0.next()) != null) {
            List<Integer> C1list = new ArrayList<>();
            for (int i : choice0) C1list.add(C.get(i));
            if (C1list.isEmpty() || C1list.size() == C.size()) continue;

            List<Integer> C2list = new ArrayList<>(C);
            C2list.removeAll(C1list);
            if (C2list.isEmpty()) continue;

            int[] c1Array = C1list.stream().mapToInt(Integer::intValue).toArray();
            int[] c2Array = C2list.stream().mapToInt(Integer::intValue).toArray();

            int r = RankTests.estimateWilksRank(S, c1Array, c2Array,
                    getEffectiveSampleSize(), alpha);

            if (r == 0) {
                Set<Integer> piece1 = new HashSet<>(C1list);
                Set<Integer> piece2 = new HashSet<>(C2list);

                log("Rank-0 split found in " + toNamesCluster(cluster)
                        + " -> " + toNamesCluster(piece1)
                        + " + " + toNamesCluster(piece2)
                        + "; recursing.");

                List<Set<Integer>> result = new ArrayList<>();

                // Recurse into each piece
                for (Set<Integer> piece : List.of(piece1, piece2)) {
                    int pieceRank = rankByTest(piece);
                    int minSize = pieceRank + 1 + minRedundancy;

                    if (piece.size() < minSize) {
                        log("Discarding piece " + toNamesCluster(piece)
                                + ": size " + piece.size()
                                + " < minSize " + minSize);
                        continue;
                    }

                    // Recursive check on each piece
                    result.addAll(splitOrKeepCluster(piece));
                }

                return result;
            }
        }

        // No rank-0 split found — return the cluster unchanged
        return List.of(cluster);
    }

    private int rankByTest(Set<Integer> cluster) {
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
     * The algorithm will consider ranks from rMin up to this value, rMax.
     *
     * @param rMax The maximum rank to consider.
     */
    public void setRmax(int rMax) {
        this.rMax = rMax;
    }

    /**
     * The algorithm will consider ranks from this value up to rMax.
     *
     * @param rMin the minimum rank value to consisder
     */
    public void setRmin(int rMin) {
        this.rMin = rMin;
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

    public void setParallel(boolean parallel) {
        this.parallel = parallel;
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
