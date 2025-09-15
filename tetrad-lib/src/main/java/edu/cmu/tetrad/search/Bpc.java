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
import edu.cmu.tetrad.util.RankTests;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.util.FastMath;
import org.ejml.simple.SimpleMatrix;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.IntStream;

import static edu.cmu.tetrad.search.Tsc.toNamesClusters;
import static org.apache.commons.math3.util.FastMath.abs;
import static org.apache.commons.math3.util.FastMath.sqrt;

/**
 * BuildPureClusters (BPC) inspired by Silva, Scheines, Glymour, Spirtes (JMLR 2006).
 * <p>
 * This implementation follows the spirit of the paper: 1) Identify candidate pure groups using tetrads (quartets) and
 * within-group dependence. 2) Grow each seed to a local maximal pure group, but DO NOT mark variables as used. 3)
 * Perform global purification/merging passes: - Merge groups when their union remains pure. - Resolve overlaps by
 * globally assigning shared variables to the most compatible group. - Iterate until convergence. 4) Drop groups with
 * fewer than 3 indicators (paperâs Step: remove latents with &lt; 3 children).
 * <p>
 * Notes: - We keep pairwise dependence as a simple Fisher-Z check on correlations. - Purification/overlap resolution
 * uses correlation-based tie-breaking; the paper gives several logically equivalent global rulesâthis is a practical,
 * deterministic variant.
 */
public class Bpc {
    // Minimum indicators per cluster per the JMLR paper (â¥3).
    private static final int MIN_CLUSTER_SIZE = 3;
    // Alpha cutoff for tetrads and dependence
    private final double alpha;
    // Number of variables
    private final int numVars;
    // Standard Normal for Fisher Z
    private final NormalDistribution normal = new NormalDistribution(0, 1);
    // Correlation matrix as a SimpleMatrix.
    private final SimpleMatrix S;
    // Cache for set-level purity checks (tetrads) to avoid recomputation across threads
    private final ConcurrentHashMap<BitKey, Boolean> pureCache = new ConcurrentHashMap<>();
    // Looser pairwise screen than tetrads (paper-faithful; just a pre-prune)
    private final double alphaPairs;
    // Merge gate: allow union only if avg|r| doesnât drop more than delta from either group
    private final double deltaMerge;
    // The effective sample size (ESS) which can be set to -1 (indicating sample size) or a positive
    private final int ess;
    // The sample size
    private final int sampleSize;
    // The nodes of the dataset.
    private final List<Node> nodes;
    // Thread-safe counters (parallel sections)
    private final LongAdder tetradTests = new LongAdder();
    private final LongAdder purityCacheHits = new LongAdder();

    // ----------------------------- instrumentation -----------------------------
    private final LongAdder purityCacheMisses = new LongAdder();
    private final LongAdder seedsEnumerated = new LongAdder();
    private final LongAdder seedsPure = new LongAdder();
    private final LongAdder mergesConsidered = new LongAdder();
    private final LongAdder mergesAccepted = new LongAdder();
    private final LongAdder reassignments = new LongAdder();
    // set in buildPatternLite()
    private boolean[][] canLink;
    // True if verbose logging is enabled
    private boolean verbose;
    // Timings (ns)
    private long tPatternNs = 0L, tSeedsNs = 0L, tMergeNs = 0L, tResolveNs = 0L;
    // Non-parallel summary (computed serially)
    private int grownDistinct = 0;

    /**
     * Constructor for the Bpc class.
     *
     * @param cov   A CovarianceMatrix containing the data to be used for clustering and correlation analysis.
     * @param alpha The significance level for tetrad tests, used to determine the tolerance for statistical
     *              independence.
     * @param ess   The effective sample size (ESS) which can be set to -1 (indicating sample size) or a positive
     *              integer. The chosen ESS impacts statistical decisions during the analysis.
     * @throws IllegalArgumentException if the ess parameter is not -1 or a positive integer.
     */
    public Bpc(CovarianceMatrix cov, double alpha, int ess) {
        this.alpha = alpha;
        this.numVars = cov.getVariables().size();
        this.sampleSize = cov.getSampleSize();
        this.S = new CorrelationMatrix(cov).getMatrix().getSimpleMatrix();
        this.nodes = cov.getVariables();

        if (!(ess == -1 || ess > 0)) {
            throw new IllegalArgumentException("esses must be -1 (sample size) or a positive integer");
        }

        this.ess = ess == -1 ? sampleSize : ess;

        // Implementation knobs (paper-faithful defaults)
        this.alphaPairs = Math.min(this.alpha * 2.0, 0.20); // looser than tetrad alpha
        this.deltaMerge = 0.02; // small allowed drop in avg|r| when merging
    }

    private static List<List<Integer>> deepCopy(List<List<Integer>> src) {
        List<List<Integer>> out = new ArrayList<>();
        for (List<Integer> g : src) out.add(new ArrayList<>(g));
        return out;
    }

    private static boolean sameFamily(List<List<Integer>> a, List<List<Integer>> b) {
        if (a.size() != b.size()) return false;
        List<Set<Integer>> as = new ArrayList<>();
        List<Set<Integer>> bs = new ArrayList<>();
        for (List<Integer> g : a) as.add(new HashSet<>(g));
        for (List<Integer> g : b) bs.add(new HashSet<>(g));
        return as.containsAll(bs) && bs.containsAll(as);
    }

    private static List<Integer> sortedList(Collection<Integer> c) {
        List<Integer> list = new ArrayList<>(c);
        Collections.sort(list);
        return list;
    }

    // ----------------------------- logging helpers -----------------------------

    private void log(String s) {
        if (verbose) TetradLogger.getInstance().log(s);
    }

    private void logParams() {
        log(String.format(Locale.US,
                "BPC params: alpha=%.4g alphaPairs=%.4g deltaMerge=%.3f ess=%d N=%d p=%d",
                alpha, alphaPairs, deltaMerge, ess, sampleSize, numVars));
    }

    private void logPatternStats() {
        int depPairs = 0, totalPairs = numVars * (numVars - 1) / 2;
        for (int i = 0; i < numVars; i++)
            for (int j = i + 1; j < numVars; j++)
                if (canLink[i][j]) depPairs++;
        double pct = totalPairs == 0 ? 0 : 100.0 * depPairs / totalPairs;
        log(String.format(Locale.US,
                "Pattern: dependent pairs = %d/%d (%.1f%%)", depPairs, totalPairs, pct));
    }

    private void logIteration(int iter, List<List<Integer>> current,
                              int mergesThisIter, int reassignThisIter, int dropsThisIter) {
        log(String.format(Locale.US,
                "Iter %d: groups=%d merges=%d reassign=%d drops=%d",
                iter, current.size(), mergesThisIter, reassignThisIter, dropsThisIter));
    }

    private void logSummary(List<List<Integer>> finalGroups) {
        log(String.format(Locale.US,
                "Summary: groups=%d seedsEnumerated=%d seedsPure=%d grownDistinct=%d mergesConsidered=%d mergesAccepted=%d reassignments=%d",
                finalGroups.size(),
                seedsEnumerated.sum(), seedsPure.sum(), grownDistinct,
                mergesConsidered.sum(), mergesAccepted.sum(), reassignments.sum()));

        log(String.format(Locale.US,
                "Purity cache: size=%d hits=%d misses=%d tetradTests~=%d",
                pureCache.size(),
                purityCacheHits.sum(), purityCacheMisses.sum(), tetradTests.sum()));

        log(String.format(Locale.US,
                "Timing(ms): pattern=%.1f seeds=%.1f merge=%.1f resolve=%.1f total=%.1f",
                tPatternNs / 1e6, tSeedsNs / 1e6, tMergeNs / 1e6, tResolveNs / 1e6,
                (tPatternNs + tSeedsNs + tMergeNs + tResolveNs) / 1e6));
    }

    // ----------------------------- main entry -----------------------------

    /**
     * Identifies clusters of variables based on tetrad purity and pairwise dependence. Constructs initial clusters as
     * locally maximal pure groups and refines them using global purification, merging, and overlap resolution until
     * convergence.
     *
     * @return A list of clusters where each cluster is represented as a list of variable indices. Each cluster
     * satisfies purity and pairwise dependence constraints, and all groups are disjoint after resolving overlaps.
     * Returns an empty list if no valid clusters exist.
     */
    public List<List<Integer>> getClusters() {
        logParams();

        buildPatternLite();
        logPatternStats();

        // ---- Stage A: enumerate tetrad seeds in parallel, grow locally WITHOUT marking variables as used
        long t0Seeds = System.nanoTime();

        ConcurrentHashMap<BitKey, List<Integer>> candMap = new ConcurrentHashMap<>();

        IntStream.range(0, numVars).parallel().forEach(i -> {
            for (int j = i + 1; j < numVars; j++) {
                if (!canLink[i][j]) continue;
                for (int k = j + 1; k < numVars; k++) {
                    if (!canLink[i][k] || !canLink[j][k]) continue;
                    for (int l = k + 1; l < numVars; l++) {
                        if (!canLink[i][l] || !canLink[j][l] || !canLink[k][l]) continue; // 6-pair screen
                        List<Integer> seed = Arrays.asList(i, j, k, l);
                        seedsEnumerated.increment();

                        if (!isPure(seed)) continue;
                        seedsPure.increment();

                        List<Integer> grown = growMaximalPure(seed); // keep serial inside for determinism
                        candMap.putIfAbsent(new BitKey(grown), grown);
                    }
                }
            }
        });

        List<List<Integer>> candidates = new ArrayList<>(candMap.values());
        grownDistinct = candidates.size();
        tSeedsNs += System.nanoTime() - t0Seeds;

        if (candidates.isEmpty()) {
            log("No pure seeds found; returning empty cluster set.");
            return new ArrayList<>();
        }

        // ---- Stage B: global purification & merging until convergence
        List<List<Integer>> current = deepCopy(candidates);
        boolean changed;
        int iter = 0;

        do {
            iter++;
            long mergesBefore = mergesAccepted.sum();
            long reassignBefore = reassignments.sum();
            int dropsThisIter = 0;

            // 1) Merge any pair whose UNION is still pure and dependent
            List<List<Integer>> merged = mergePurePairs(current);
            boolean changed1 = !sameFamily(current, merged);
            if (changed1) current = merged;

            // 2) Resolve overlaps by assigning shared variables to their best-fitting group
            List<List<Integer>> resolved = resolveOverlaps(current);
            boolean changed2 = !sameFamily(current, resolved);
            if (changed2) current = resolved;

            // 3) Drop groups below minimum size
            int before = current.size();
            List<List<Integer>> filtered = new ArrayList<>();
            for (List<Integer> g : current) if (g.size() >= MIN_CLUSTER_SIZE) filtered.add(g);
            boolean changed3 = !sameFamily(current, filtered);
            if (changed3) {
                dropsThisIter = before - filtered.size();
                current = filtered;
            }

            int mergesThisIter = (int) (mergesAccepted.sum() - mergesBefore);
            int reassignThisIter = (int) (reassignments.sum() - reassignBefore);
            logIteration(iter, current, mergesThisIter, reassignThisIter, dropsThisIter);

            changed = changed1 || changed2 || changed3;
        } while (changed);

        Set<Set<Integer>> _current = new HashSet<>();
        for (List<Integer> g : current) {
            _current.add(new HashSet<>(g));
        }

        log("Final clusters: " + toNamesClusters(_current, nodes));
        logSummary(current);

        return current;
    }

    // ----------------------------- core helpers -----------------------------

    /**
     * Grow a seed to a locally maximal pure group: greedily add variables whose inclusion preserves purity (all tetrads
     * pass) and pairwise dependence.
     */
    private List<Integer> growMaximalPure(List<Integer> seed) {
        Set<Integer> group = new HashSet<>(seed);
        boolean expanded;
        do {
            expanded = false;
            for (int x = 0; x < numVars; x++) {
                if (group.contains(x)) continue;
                List<Integer> candidate = new ArrayList<>(group);
                candidate.add(x);
                if (isPure(candidate)) { // && clusterDependent(candidate)) {
                    group.add(x);
                    expanded = true;
                    // Optional trace:
                    // log("Grow: added " + nodes.get(x).getName() + " -> " + toNamesClusters(Set.of(new HashSet<>(group)), nodes));
                }
            }
        } while (expanded);
        return sortedList(group);
    }

    /**
     * Whether the set passes all tetrad tests (purity).
     */
    private boolean isPure(List<Integer> vars) {
        if (vars.size() < 4) return false;
        BitKey key = new BitKey(vars);
        Boolean cached = pureCache.get(key);
        if (cached != null) {
            purityCacheHits.increment();
            return cached;
        }
        purityCacheMisses.increment();

        // Early abort: iterate tetrads and stop at first failure
        int m = vars.size();
        for (int i = 0; i < m; i++) {
            for (int j = i + 1; j < m; j++) {
                for (int k = j + 1; k < m; k++) {
                    for (int l = k + 1; l < m; l++) {
                        // 3 tetrads per 4-tuple
                        tetradTests.add(3);

                        int a = vars.get(i), b = vars.get(j), c = vars.get(k), d = vars.get(l);
                        int[][] t1 = new int[][]{{a, b}, {c, d}};
                        int[][] t2 = new int[][]{{a, c}, {b, d}};
                        int[][] t3 = new int[][]{{a, d}, {b, c}};

                        int rank1 = rank(t1);
                        int rank2 = rank(t2);
                        int rank3 = rank(t3);

                        if (!(rank1 == 1 && rank2 == 1 && rank3 == 1)) {
                            pureCache.put(key, Boolean.FALSE);
                            return false;
                        }
                    }
                }
            }
        }
        pureCache.put(key, Boolean.TRUE);
        return true;
    }

    private int rank(int[][] t) {
        return RankTests.estimateWilksRank(S, t[0], t[1], sampleSize, alpha);
    }

    /**
     * Pairwise dependence inside a cluster via Fisher Z on correlations. Returns true if ALL pairs are dependent.
     */
    @SuppressWarnings("unused")
    private boolean clusterDependent(List<Integer> cluster) {
        if (cluster.size() <= 1) return true;
        if (canLink != null) {
            for (int i = 0; i < cluster.size(); i++) {
                int vi = cluster.get(i);
                for (int j = i + 1; j < cluster.size(); j++) {
                    int vj = cluster.get(j);
                    if (!canLink[vi][vj]) return false;
                }
            }
            return true;
        }
        // Fallback: direct Fisher-Z checks
        int n = ess;
        for (int i = 0; i < cluster.size(); i++) {
            for (int j = i + 1; j < cluster.size(); j++) {
                double r = S.get(cluster.get(i), cluster.get(j));
                double q = .5 * (FastMath.log(1.0 + abs(r)) - FastMath.log(1.0 - abs(r)));
                double df = n - 3.0; // no conditioning
                double fisherZ = sqrt(df) * q;
                double pTwoSided = 2 * (1.0 - this.normal.cumulativeProbability(Math.abs(fisherZ)));
                if (pTwoSided > alpha) return false;
            }
        }
        return true;
    }

    /**
     * Build a lightweight measurement pattern: canLink[i][j] = true iff pair (i,j) is significantly dependent.
     */
    private void buildPatternLite() {
        long t0 = System.nanoTime();

        canLink = new boolean[numVars][numVars];
        int n = ess;
        for (int i = 0; i < numVars; i++) {
            canLink[i][i] = true;
            for (int j = i + 1; j < numVars; j++) {
                double r = S.get(i, j);
                double q = .5 * (FastMath.log(1.0 + abs(r)) - FastMath.log(1.0 - abs(r)));
                double df = n - 3.0;
                double fisherZ = sqrt(df) * q;
                double pTwoSided = 2 * (1.0 - this.normal.cumulativeProbability(Math.abs(fisherZ)));
                boolean dep = pTwoSided <= alphaPairs; // looser screen than tetrads
                canLink[i][j] = canLink[j][i] = dep;
            }
        }

        tPatternNs += System.nanoTime() - t0;
    }

    /**
     * Merge any pair of groups whose union remains pure and dependent; iterate until no merges apply.
     */
    private List<List<Integer>> mergePurePairs(List<List<Integer>> groups) {
        long t0 = System.nanoTime();

        List<List<Integer>> current = deepCopy(groups);
        boolean mergedSomething;
        do {
            mergedSomething = false;

            final int n = current.size();
            // Build candidate merges in parallel
            List<int[]> candidates = Collections.synchronizedList(new ArrayList<>());
            IntStream.range(0, n).parallel().forEach(a -> {
                for (int b = a + 1; b < n; b++) {
                    Set<Integer> union = new HashSet<>(current.get(a));
                    union.addAll(current.get(b));
                    if (union.size() < 4) continue;
                    List<Integer> u = sortedList(union);
                    if (!isPure(u) /*|| !clusterDependent(u)*/) continue;
                    // delta gate: union avg|r| must not drop too much from either group
                    double meanU = avgAbsCorrGroup(u);
                    double meanA = avgAbsCorrGroup(current.get(a));
                    double meanB = avgAbsCorrGroup(current.get(b));
                    if (meanU + deltaMerge >= Math.min(meanA, meanB)) {
                        candidates.add(new int[]{a, b});
                        mergesConsidered.increment();
                    }
                }
            });

            if (!candidates.isEmpty()) {
                // Greedy selection of non-overlapping merges by larger union first
                candidates.sort((x, y) -> Integer.compare(
                        sizeOfUnion(y[0], y[1], current),
                        sizeOfUnion(x[0], x[1], current)));

                boolean[] used = new boolean[current.size()];
                for (int[] pair : candidates) {
                    int a = pair[0], b = pair[1];
                    if (a >= current.size() || b >= current.size()) continue;
                    if (used[a] || used[b]) continue;
                    Set<Integer> union = new HashSet<>(current.get(a));
                    union.addAll(current.get(b));
                    List<Integer> u = sortedList(union);
                    if (u.size() >= 4 && isPure(u)) { // && clusterDependent(u)) {
                        double meanU = avgAbsCorrGroup(u);
                        double meanA = avgAbsCorrGroup(current.get(a));
                        double meanB = avgAbsCorrGroup(current.get(b));
                        if (meanU + deltaMerge >= Math.min(meanA, meanB)) {
                            current.set(a, u);
                            current.remove(b);
                            used[a] = true; // mark the merged slot; indexes shift for >b, but we prevent reuse
                            mergesAccepted.increment();
                            mergedSomething = true;
                            break; // restart outer do-while to recompute candidates
                        }
                    }
                }
            }
        } while (mergedSomething);

        tMergeNs += System.nanoTime() - t0;
        return current;
    }

    private int sizeOfUnion(int a, int b, List<List<Integer>> current) {
        if (a >= current.size() || b >= current.size()) return 0;
        Set<Integer> union = new HashSet<>(current.get(a));
        union.addAll(current.get(b));
        return union.size();
    }

    /**
     * Resolve overlaps: for any variable that appears in multiple groups, assign it to the group where it has highest
     * average absolute correlation with the rest of that group (compatibility), then remove from others.
     */
    private List<List<Integer>> resolveOverlaps(List<List<Integer>> groups) {
        long t0 = System.nanoTime();

        Map<Integer, List<Integer>> owners = new HashMap<>();
        for (int gi = 0; gi < groups.size(); gi++) {
            for (int v : groups.get(gi)) owners.computeIfAbsent(v, k -> new ArrayList<>()).add(gi);
        }
        List<Set<Integer>> work = new ArrayList<>();
        for (List<Integer> g : groups) work.add(new HashSet<>(g));

        // Decide best owner for each overlapping variable in parallel
        Map<Integer, Integer> bestOwner = new ConcurrentHashMap<>();
        owners.entrySet().parallelStream().forEach(e -> {
            int v = e.getKey();
            List<Integer> gs = e.getValue();
            if (gs.size() <= 1) {
                if (!gs.isEmpty()) bestOwner.put(v, gs.getFirst());
                return;
            }
            double bestScore = Double.NEGATIVE_INFINITY;
            int bestGi = gs.getFirst();
            int bestT = -1;
            for (int gi : gs) {
                double score = avgAbsCorr(v, work.get(gi));
                int tie = 0;
                // tie-break by number of passing tetrads involving v in this group
                if (Double.compare(score, bestScore) == 0.0) {
                    tie = countPassingTetradsWithVar(v, work.get(gi));
                }
                if (score > bestScore || (Double.compare(score, bestScore) == 0.0 && tie > bestT)) {
                    bestScore = score;
                    bestT = tie;
                    bestGi = gi;
                }
            }
            bestOwner.put(v, bestGi);
        });

        long overlapVars = owners.entrySet().stream().filter(e -> e.getValue().size() > 1).count();

        // Apply removals serially for determinism
        for (Map.Entry<Integer, List<Integer>> e : owners.entrySet()) {
            int v = e.getKey();
            List<Integer> gs = e.getValue();
            if (gs.size() <= 1) continue;
            int keep = bestOwner.get(v);
            for (int gi : gs)
                if (gi != keep) {
                    boolean removed = work.get(gi).remove(v);
                    if (removed) reassignments.increment();
                }
        }

        // Rebuild list and drop groups that became too small or impure after removals
        List<List<Integer>> out = new ArrayList<>();
        for (Set<Integer> gset : work) {
            List<Integer> g = sortedList(gset);
            if (g.size() >= MIN_CLUSTER_SIZE && (g.size() < 4 || isPure(g))) { // && clusterDependent(g)) {
                out.add(g);
            }
        }

        tResolveNs += System.nanoTime() - t0;
        log(String.format(Locale.US, "Overlap vars=%d reassignments(total)=%d",
                overlapVars, reassignments.sum()));
        return out;
    }

    /**
     * Average absolute correlation between v and others in the group (excluding v).
     */
    private double avgAbsCorr(int v, Set<Integer> group) {
        if (!group.contains(v)) return Double.NEGATIVE_INFINITY;
        if (group.size() <= 1) return Double.NEGATIVE_INFINITY;
        double s = 0.0;
        int c = 0;
        for (int u : group) {
            if (u == v) continue;
            s += Math.abs(S.get(v, u));
            c++;
        }
        return c == 0 ? Double.NEGATIVE_INFINITY : s / c;
    }

    private double avgAbsCorrGroup(Collection<Integer> group) {
        if (group.size() <= 1) return 0.0;
        double s = 0.0;
        int c = 0;
        List<Integer> list = (group instanceof List) ? (List<Integer>) group : new ArrayList<>(group);
        for (int i = 0; i < list.size(); i++) {
            int vi = list.get(i);
            for (int j = i + 1; j < list.size(); j++) {
                int vj = list.get(j);
                s += Math.abs(S.get(vi, vj));
                c++;
            }
        }
        return c == 0 ? 0.0 : s / c;
    }

    private int countPassingTetradsWithVar(int v, Set<Integer> group) {
        if (!group.contains(v) || group.size() < 4) return 0;
        List<Integer> list = new ArrayList<>(group);
        int idxV = list.indexOf(v);
        int m = list.size();
        int count = 0;
        for (int i = 0; i < m; i++) {
            if (i == idxV) continue;
            for (int j = i + 1; j < m; j++) {
                if (j == idxV) continue;
                for (int k = j + 1; k < m; k++) {
                    if (k == idxV) continue;
                    int a = list.get(idxV), b = list.get(i), c = list.get(j), d = list.get(k);

                    int[][] t1 = new int[][]{{a, b}, {c, d}};
                    int[][] t2 = new int[][]{{a, c}, {b, d}};
                    int[][] t3 = new int[][]{{a, d}, {b, c}};

                    int rank1 = rank(t1);
                    int rank2 = rank(t2);
                    int rank3 = rank(t3);

                    if ((rank1 == 1 && rank2 == 1 && rank3 == 1)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * BitSet-based key for caching set-level purity
     */
    private static final class BitKey {
        private final BitSet bits;

        BitKey(Collection<Integer> idxs) {
            BitSet b = new BitSet();
            for (Integer v : idxs) if (v != null) b.set(v);
            this.bits = b;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof BitKey other)) return false;
            return this.bits.equals(other.bits);
        }

        @Override
        public int hashCode() {
            return bits.hashCode();
        }
    }
}
