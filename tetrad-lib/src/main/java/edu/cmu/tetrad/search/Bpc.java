package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.CorrelationMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.search.ntad_test.NtadTest;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.util.FastMath;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

import static org.apache.commons.math3.util.FastMath.abs;
import static org.apache.commons.math3.util.FastMath.sqrt;

/**
 * BuildPureClusters (BPC) inspired by Silva, Scheines, Glymour, Spirtes (JMLR 2006).
 * <p>
 * This implementation follows the spirit of the paper: 1) Identify candidate pure groups using tetrads (quartets) and
 * within-group dependence. 2) Grow each seed to a local maximal pure group, but DO NOT mark variables as used. 3)
 * Perform global purification/merging passes: - Merge groups when their union remains pure. - Resolve overlaps by
 * globally assigning shared variables to the most compatible group. - Iterate until convergence. 4) Drop groups with
 * fewer than 3 indicators (paper’s Step: remove latents with < 3 children).
 * <p>
 * Notes: - We keep pairwise dependence as a simple Fisher-Z check on correlations. - Purification/overlap resolution
 * uses correlation-based tie-breaking; the paper gives several logically equivalent global rules—this is a practical,
 * deterministic variant.
 * <p>
 * // Pattern‑lite prepass and parallel seed enumeration reduce runtime substantially for larger p.
 */
public class Bpc {
    /**
     * Minimum indicators per cluster per the JMLR paper (≥3).
     */
    private static final int MIN_CLUSTER_SIZE = 3;
    /**
     * Tetrad test
     */
    private final NtadTest ntadTest;
    /**
     * Alpha cutoff for tetrads and dependence
     */
    private final double alpha;
    /**
     * Number of variables
     */
    private final int numVars;
    /**
     * Variable names
     */
    private final List<String> variableNames;
    /**
     * Standard Normal for Fisher Z
     */
    private final NormalDistribution normal = new NormalDistribution(0, 1);
    /**
     * Correlation matrix
     */
    private final CorrelationMatrix corr;
    // Cache for set-level purity checks (tetrads) to avoid recomputation across threads
    private final ConcurrentHashMap<BitKey, Boolean> pureCache = new ConcurrentHashMap<>();
    // Looser pairwise screen than tetrads (paper-faithful; just a pre-prune)
    private final double alphaPairs;
    // Merge gate: allow union only if avg|r| doesn’t drop more than delta from either group
    private final double deltaMerge;
    /**
     * Resulting clusters (indices)
     */
    private List<List<Integer>> clusters = new ArrayList<>();
    // Pairwise dependence screen (pattern‑lite adjacency)
    private boolean[][] canLink; // set in buildPatternLite()

    public Bpc(NtadTest test, DataSet dataSet, double alpha) {
        this.ntadTest = test;
        this.alpha = alpha;
        this.numVars = test.variables().size();
        this.variableNames = dataSet.getVariableNames();
        this.corr = new CorrelationMatrix(dataSet);

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

    // ----------------------------- helpers -----------------------------

    private static List<Integer> sortedList(Collection<Integer> c) {
        List<Integer> list = new ArrayList<>(c);
        Collections.sort(list);
        return list;
    }

    /**
     * Main entry: find clusters.
     */
    public List<List<Integer>> getClusters() {
        buildPatternLite();
        // ---- Stage A: enumerate tetrad seeds in parallel, grow locally WITHOUT marking variables as used
        ConcurrentHashMap<BitKey, List<Integer>> candMap = new ConcurrentHashMap<>();

        IntStream.range(0, numVars).parallel().forEach(i -> {
            for (int j = i + 1; j < numVars; j++) {
                if (!canLink[i][j]) continue;
                for (int k = j + 1; k < numVars; k++) {
                    if (!canLink[i][k] || !canLink[j][k]) continue;
                    for (int l = k + 1; l < numVars; l++) {
                        if (!canLink[i][l] || !canLink[j][l] || !canLink[k][l]) continue; // 6-pair screen
                        List<Integer> seed = Arrays.asList(i, j, k, l);
                        if (!isPure(seed)) continue;
                        if (!clusterDependent(seed)) continue;
                        List<Integer> grown = growMaximalPure(seed); // keep serial inside for determinism
                        candMap.putIfAbsent(new BitKey(grown), grown);
                    }
                }
            }
        });

        List<List<Integer>> candidates = new ArrayList<>(candMap.values());
        if (candidates.isEmpty()) {
            this.clusters = new ArrayList<>();
            return new ArrayList<>();
        }

        // ---- Stage B: global purification & merging until convergence
        List<List<Integer>> current = deepCopy(candidates);
        boolean changed;
        do {
            changed = false;

            // 1) Merge any pair whose UNION is still pure and dependent
            List<List<Integer>> merged = mergePurePairs(current);
            if (!sameFamily(current, merged)) {
                current = merged;
                changed = true;
            }

            // 2) Resolve overlaps by assigning shared variables to their best-fitting group
            List<List<Integer>> resolved = resolveOverlaps(current);
            if (!sameFamily(current, resolved)) {
                current = resolved;
                changed = true;
            }

            // 3) Drop groups below minimum size
            List<List<Integer>> filtered = new ArrayList<>();
            for (List<Integer> g : current) if (g.size() >= MIN_CLUSTER_SIZE) filtered.add(g);
            if (!sameFamily(current, filtered)) {
                current = filtered;
                changed = true;
            }

        } while (changed);

        return current;
    }

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
                if (isPure(candidate) && clusterDependent(candidate)) {
                    group.add(x);
                    expanded = true;
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
        if (cached != null) return cached;

        // Early abort: iterate tetrads and stop at first failure
        int m = vars.size();
        for (int i = 0; i < m; i++) {
            for (int j = i + 1; j < m; j++) {
                for (int k = j + 1; k < m; k++) {
                    for (int l = k + 1; l < m; l++) {
                        int a = vars.get(i), b = vars.get(j), c = vars.get(k), d = vars.get(l);
                        int[][] t1 = new int[][]{{a, b}, {c, d}};
                        int[][] t2 = new int[][]{{a, c}, {b, d}};
                        int[][] t3 = new int[][]{{a, d}, {b, c}};
                        if (!ntadTest.allGreaterThanAlpha(Collections.singletonList(t1), alpha)
                            || !ntadTest.allGreaterThanAlpha(Collections.singletonList(t2), alpha)
                            || !ntadTest.allGreaterThanAlpha(Collections.singletonList(t3), alpha)) {
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

    /**
     * Generate all tetrads for a set of variable indices.
     */
    private List<int[][]> generateTetrads(List<Integer> vars) {
        List<int[][]> tetrads = new ArrayList<>();
        if (vars.size() < 4) return tetrads;

        for (int i = 0; i < vars.size(); i++) {
            for (int j = i + 1; j < vars.size(); j++) {
                for (int k = j + 1; k < vars.size(); k++) {
                    for (int l = k + 1; l < vars.size(); l++) {
                        int a = vars.get(i), b = vars.get(j), c = vars.get(k), d = vars.get(l);
                        tetrads.add(new int[][]{{a, b}, {c, d}});
                        tetrads.add(new int[][]{{a, c}, {b, d}});
                        tetrads.add(new int[][]{{a, d}, {b, c}});
                    }
                }
            }
        }
        return tetrads;
    }

    /**
     * Pairwise dependence inside a cluster via Fisher Z on correlations. Returns true if ALL pairs are dependent.
     */
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
        int n = this.corr.getSampleSize();
        for (int i = 0; i < cluster.size(); i++) {
            for (int j = i + 1; j < cluster.size(); j++) {
                double r = this.corr.getValue(cluster.get(i), cluster.get(j));
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
        canLink = new boolean[numVars][numVars];
        int n = this.corr.getSampleSize();
        for (int i = 0; i < numVars; i++) {
            canLink[i][i] = true;
            for (int j = i + 1; j < numVars; j++) {
                double r = this.corr.getValue(i, j);
                double q = .5 * (FastMath.log(1.0 + abs(r)) - FastMath.log(1.0 - abs(r)));
                double df = n - 3.0;
                double fisherZ = sqrt(df) * q;
                double pTwoSided = 2 * (1.0 - this.normal.cumulativeProbability(Math.abs(fisherZ)));
                boolean dep = pTwoSided <= alphaPairs; // looser screen than tetrads
                canLink[i][j] = canLink[j][i] = dep;
            }
        }
    }

    /**
     * Merge any pair of groups whose union remains pure and dependent; iterate until no merges apply.
     */
    private List<List<Integer>> mergePurePairs(List<List<Integer>> groups) {
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
                    if (!isPure(u) || !clusterDependent(u)) continue;
                    // delta gate: union avg|r| must not drop too much from either group
                    double meanU = avgAbsCorrGroup(u);
                    double meanA = avgAbsCorrGroup(current.get(a));
                    double meanB = avgAbsCorrGroup(current.get(b));
                    if (meanU + deltaMerge >= Math.min(meanA, meanB)) {
                        candidates.add(new int[]{a, b});
                    }
                }
            });

            if (!candidates.isEmpty()) {
                // Greedy selection of non-overlapping merges by larger union first
                // Sort candidates by union size desc to encourage bigger merges
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
                    if (u.size() >= 4 && isPure(u) && clusterDependent(u)) {
                        double meanU = avgAbsCorrGroup(u);
                        double meanA = avgAbsCorrGroup(current.get(a));
                        double meanB = avgAbsCorrGroup(current.get(b));
                        if (meanU + deltaMerge >= Math.min(meanA, meanB)) {
                            current.set(a, u);
                            current.remove(b);
                            used[a] = true; // mark the merged slot; indexes shift for >b, but we prevent reuse
                            mergedSomething = true;
                            break; // restart outer do-while to recompute candidates
                        }
                    }
                }
            }
        } while (mergedSomething);
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
                if (!gs.isEmpty()) bestOwner.put(v, gs.get(0));
                return;
            }
            double bestScore = Double.NEGATIVE_INFINITY;
            int bestGi = gs.get(0);
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

        // Apply removals serially for determinism
        for (Map.Entry<Integer, List<Integer>> e : owners.entrySet()) {
            int v = e.getKey();
            List<Integer> gs = e.getValue();
            if (gs.size() <= 1) continue;
            int keep = bestOwner.get(v);
            for (int gi : gs) if (gi != keep) work.get(gi).remove(v);
        }

        // Rebuild list and drop groups that became too small or impure after removals
        List<List<Integer>> out = new ArrayList<>();
        for (Set<Integer> gset : work) {
            List<Integer> g = sortedList(gset);
            if (g.size() >= MIN_CLUSTER_SIZE && (g.size() < 4 || isPure(g)) && clusterDependent(g)) {
                out.add(g);
            }
        }
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
            s += Math.abs(corr.getValue(v, u));
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
                s += Math.abs(corr.getValue(vi, vj));
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
                    if (ntadTest.allGreaterThanAlpha(Collections.singletonList(t1), alpha)
                        && ntadTest.allGreaterThanAlpha(Collections.singletonList(t2), alpha)
                        && ntadTest.allGreaterThanAlpha(Collections.singletonList(t3), alpha)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    // Utility: add list if it's a new set (ignoring order)
    private void addIfNew(List<List<Integer>> family, List<Integer> g) {
        Set<Integer> gs = new HashSet<>(g);
        for (List<Integer> h : family) if (new HashSet<>(h).equals(gs)) return;
        family.add(g);
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
