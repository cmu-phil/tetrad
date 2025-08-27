package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.CorrelationMatrix;
import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.RankTests;
import edu.cmu.tetrad.util.SublistGenerator;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.ejml.simple.SimpleMatrix;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static edu.cmu.tetrad.util.RankTests.estimateWilksRank;

/**
 * The TscScored class provides methods and mechanisms to perform rank-based cluster search operations under statistical
 * constraints. This class supports scoring and enumeration of clusters using RCCA methods and provides a combination of
 * cached computation and configurable scoring parameters to optimize search efficiency. It is designed for use in
 * latent variable modeling and cluster discovery in high-dimensional datasets.
 */
public class Tsc {
    private static final java.util.concurrent.ConcurrentHashMap<Long, long[][]> BINOM_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private final List<Node> nodes;
    private final List<Integer> variables;
    private final int sampleSize;
    private final SimpleMatrix S;
    private final Map<Key, Integer> rankCache = new ConcurrentHashMap<>();
    private final Map<Key, Integer> scoredRankCache = new ConcurrentHashMap<>();
    private final Map<Long, ScoreSweep> sweepCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final boolean prefilterByWilkes = true;
    private int expectedSampleSize = -1;
    private double alpha = 0.01;
    private boolean verbose = true;
    private Map<Set<Integer>, Integer> clusters = new HashMap<>();
    private Map<Set<Integer>, Integer> clusterToRank;
//    private Map<Set<Integer>, Integer> reducedRank;

    /**
     * RCCA ridge regularizer used in RankTests.getRccaEntry.
     */
    private double ridge = 1e-8;
    /**
     * BIC penalty discount c (1.0 = standard BIC).
     */
    private double penaltyDiscount = 1.0;
    /**
     * EBIC gamma (0 disables EBIC term).
     */
    private double ebicGamma = 0.0;
    /**
     * Optional margin: require sc(k*) >= sc(k*±1) + margin.
     */
    private double scoreMargin = 0.0;
    /**
     * Represents the operational mode of the TscScored class.
     * <p>
     * The `mode` variable determines whether the algorithm functions in a testing mode or a scoring mode. In
     * `Mode.Testing`, the operations focus on hypothesis tests, while in `Mode.Scoring`, the focus is on scoring
     * clusters based on their statistical properties.
     * <p>
     * Default value: `Mode.Testing`.
     */
    private Mode mode = Mode.Testing;

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
     * Precomputes the binomial coefficients using dynamic programming. The method generates a table of binomial
     * coefficients C[n][k] for 0 <= n <= input n and 0 <= k <= input k. It ensures values are capped at Long.MAX_VALUE
     * to handle potential overflow scenarios.
     *
     * @param n the maximum row index for which binomial coefficients are to be computed
     * @param k the maximum column index for which binomial coefficients are to be computed
     * @return a 2D array where the value at C[i][j] represents the binomial coefficient "i choose j"
     */
    private static long[][] precomputeBinom(int n, int k) { /* ... unchanged ... */
        long[][] C = new long[n + 1][k + 1];
        for (int i = 0; i <= n; i++) {
            C[i][0] = 1;
            int maxj = Math.min(i, k);
            for (int j = 1; j <= maxj; j++) {
                long v = C[i - 1][j - 1] + C[i - 1][j];
                if (v < 0 || v < C[i - 1][j - 1]) v = Long.MAX_VALUE;
                C[i][j] = v;
            }
        }
        return C;
    }

    /**
     * Computes a binomial coefficient using a precomputed table.
     *
     * @param C a precomputed 2D array representing binomial coefficients, where C[i][j] = "i choose j"
     * @param x the first parameter of the binomial coefficient
     * @param j the second parameter of the binomial coefficient
     * @return the binomial coefficient "x choose j" if 0 <= j <= x; otherwise, returns 0
     */
    private static long choose(long[][] C, int x, int j) {
        if (x < j || j < 0) return 0L;
        return C[x][j];
    }

    private static long[][] binom(int n, int k) {
        long key = (((long) n) << 32) ^ k;
        return BINOM_CACHE.computeIfAbsent(key, binom -> precomputeBinom(n, k));
    }

    private static void combinadicDecodeColex(long m, int n, int k, long[][] C, int[] out) { /* ... unchanged ... */
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

    public static int[][] dependencyMatrix(SimpleMatrix S, double sampleSize, double alpha) {
        if (S.numRows() != S.numCols()) throw new IllegalArgumentException("S must be square");
        final int p = S.numRows();

        // Two-sided cutoff: P(|Z| > c) = alpha => c = Phi^{-1}(1 - alpha/2)
        final double cutoff = new NormalDistribution(0, 1)
                .inverseCumulativeProbability(1.0 - alpha / 2.0);

        final double scale = sampleSize > 3.0 ? Math.sqrt(sampleSize - 3.0) : 0.0;

        int[][] A = new int[p][p];

        for (int i = 0; i < p; i++) {
            A[i][i] = 0; // or 1 if you prefer self-dependence
            for (int j = i + 1; j < p; j++) {
                double r = S.get(i, j);
                if (Double.isNaN(r)) r = 0.0;

                // Clamp and stable atanh
                double rc = Math.max(-0.999999, Math.min(0.999999, r));
                double q = 0.5 * Math.log1p(2.0 * rc / (1.0 - rc)); // = atanh(rc)
                double z = scale * q;

                int dep = (Math.abs(z) > cutoff) ? 1 : 0;
                A[i][j] = dep;
                A[j][i] = dep;
            }
        }
        return A;
    }

    /**
     * Sets the mode for the TscScored instance. The mode determines the operational behavior of the TscScored class,
     * selecting between Testing or Scoring modes.
     *
     * @param mode the operational mode to be set, where mode must be an instance of the {@code Mode} enum (either
     *             {@code Mode.Testing} or {@code Mode.Scoring})
     */
    public void setMode(Mode mode) {
        this.mode = mode;
    }

    /**
     * Simple 64-bit key from the two arrays; stable across calls for same content.
     */
    private long pairKey(int[] C, int[] D) {
        int h1 = Arrays.hashCode(C);
        int h2 = Arrays.hashCode(D);
        long k = 1469598103934665603L;       // FNV offset basis
        k ^= h1;
        k *= 1099511628211L;                 // FNV prime
        k ^= h2;
        k *= 1099511628211L;
        return k;
    }

    /**
     * Cached version of the RCCA-BIC sweep.
     */
    private ScoreSweep rccaScoreSweepCached(int[] C, int[] D) {
        long k = pairKey(C, D);
        ScoreSweep s = sweepCache.get(k);
        if (s != null) return s;
        s = rccaScoreSweep(S, C, D, expectedSampleSize, ridge, penaltyDiscount, ebicGamma);
        sweepCache.put(k, s);
        return s;
    }

    // ---- test-based enumerator (kept for reference) ----------------------------
    public Set<Set<Integer>> findClustersAtRankTesting(List<Integer> vars, int size, int rank) {
        log("vars: " + vars);
        log("findClustersAtRankTesting size = " + size + ", rank = " + rank + ", ess = " + expectedSampleSize);

        final int n = vars.size();
        final int k = size;

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

            int[] idxs = tlIdxs.get();
            combinadicDecodeColex(m, n, k, C, idxs);

            int[] ids = tlIds.get();
            for (int i = 0; i < k; i++) ids[i] = varIds[idxs[i]];

            int r = lookupRankFast(ids);
            if (r != rank) return null;

            Set<Integer> cluster = new HashSet<>(k * 2);
            for (int i = 0; i < k; i++) cluster.add(ids[i]);

            return cluster;
        }).filter(Objects::nonNull).collect(java.util.stream.Collectors.toCollection(java.util.concurrent.ConcurrentHashMap::newKeySet));
    }

    // ---- scored enumerator (this is what the meta-loop calls) ------------------
    private Set<Set<Integer>> findClustersAtRankScoring(List<Integer> vars, int size, int targetRank) {
        final int n = vars.size();
        final int k = size;

        if (targetRank < 0) throw new IllegalArgumentException("targetRank must be >= 0");
        if (k <= 0 || k > n) return Collections.emptySet();

        final int[] varIds = new int[n];
        for (int i = 0; i < n; i++) varIds[i] = vars.get(i);

        final long[][] Cbin = binom(n, k);
        final long total = Cbin[n][k];

        final ThreadLocal<int[]> tlIdxs = ThreadLocal.withInitial(() -> new int[k]);
        final ThreadLocal<int[]> tlIds = ThreadLocal.withInitial(() -> new int[k]);

        final Set<Set<Integer>> accepted = java.util.concurrent.ConcurrentHashMap.newKeySet();
        final java.util.concurrent.atomic.AtomicInteger counter = new java.util.concurrent.atomic.AtomicInteger();

        LongStream.range(0, total).parallel().forEach(m -> {
            if (Thread.currentThread().isInterrupted()) return;

            int _c = counter.incrementAndGet();
            if (verbose && (_c % Math.max(20000, total / 50 + 1) == 0))
                log("Scored find: examined " + _c + " / " + total);

            // decode k-combination
            int[] idxs = tlIdxs.get();
            combinadicDecodeColex(m, n, k, Cbin, idxs);

            // map to global ids (sorted)
            int[] Carr = tlIds.get();
            for (int i = 0; i < k; i++) Carr[i] = varIds[idxs[i]];
            Arrays.sort(Carr);

            // D = V \ C
            Set<Integer> Cset = new HashSet<>(k * 2);
            for (int v : Carr) Cset.add(v);
            int[] Darr = complementOf(Cset);
            if (Darr.length == 0) return;

            if (prefilterByWilkes) {
                // --- NEW: fast Wilks pre-filter to avoid expensive RCCA when impossible
                int rWilks = RankTests.estimateWilksRank(S, Carr, Darr, expectedSampleSize, Math.min(0.05, alpha));
                if (rWilks != targetRank) return;
            }

            // RCCA-BIC sweep (cached)
            ScoreSweep sw = rccaScoreSweepCached(Carr, Darr);
            if (sw.mMax < 0) return;

            boolean okMargins =
                    (Double.isNaN(sw.scKm1) || sw.scBest >= sw.scKm1 + scoreMargin) &&
                    (Double.isNaN(sw.scKp1) || sw.scBest >= sw.scKp1 + scoreMargin);

            if (sw.rStar == targetRank && okMargins) {
                accepted.add(Cset);
            }
        });

        return accepted;
    }

    private boolean isAtomic(Set<Integer> C, int k) {
        // Reject if ANY (|C|-1)-subset has the same rank k
        if (C.size() <= k + 1) return true; // smallest possible witness size is k+1
        List<Integer> L = new ArrayList<>(C);
        SublistGenerator gen = new SublistGenerator(L.size(), L.size() - 1);
        int[] choice;
        while ((choice = gen.next()) != null) {
            if (choice.length == 0 || choice.length == L.size()) continue;
            // Build the (|C|-1)-subset
            Set<Integer> sub = new HashSet<>(choice.length * 2);
            for (int idx : choice) sub.add(L.get(idx));
            int r = rank(sub);
            if (r == k) return false; // non-atomic: subset already witnesses rank k
        }
        return true;
    }

    private ScoreSweep rccaScoreSweep(SimpleMatrix S,
                                      int[] C, int[] D,
                                      int n, double ridge,
                                      double c, double gamma) {
        RankTests.RccaEntry ent = RankTests.getRccaEntry(S, C, D, ridge);
        if (ent == null || ent.suffixLogs == null)
            return new ScoreSweep(-1, -1, Double.NEGATIVE_INFINITY, Double.NaN, Double.NaN);

        int p = C.length, q = D.length;
        int m = Math.min(Math.min(p, q), n - 1);
        m = Math.min(m, ent.suffixLogs.length - 1);
        if (m < 0) return new ScoreSweep(-1, -1, Double.NEGATIVE_INFINITY, Double.NaN, Double.NaN);
        if (m == 0) return new ScoreSweep(0, 0, 0.0, Double.NaN, Double.NaN);

        // Bartlett-ish effective n (same spirit as BlocksBicScore)
        double nEff = n - 1.0 - 0.5 * (p + q + 1.0);
        if (nEff < 1.0) nEff = 1.0;

        // EBIC pool: treat |D| as proxy for available predictors
        int Ppool = Math.max(q, 2);

        double[] suf = ent.suffixLogs;  // suf[0] == 0 by contract
        double base = suf[0];

        int rStar = 0;
        double scStar = -1e300;
        double[] sc = new double[m + 1];

        for (int r = 0; r <= m; r++) {
            double sumLogsTopR = base - suf[r];      // sum_{i=1..r} log(1 - rho_i^2)
            double fit = -nEff * sumLogsTopR;
            int kParams = r * (p + q - r);
            double pen = c * kParams * Math.log(n);
            if (gamma > 0.0) pen += 2.0 * gamma * kParams * Math.log(Ppool);
            double scR = fit - pen;
            sc[r] = scR;
            if (scR > scStar) {
                scStar = scR;
                rStar = r;
            }
        }

        double scKm1 = (rStar - 1 >= 0) ? sc[rStar - 1] : Double.NaN;
        double scKp1 = (rStar + 1 <= m) ? sc[rStar + 1] : Double.NaN;
        return new ScoreSweep(m, rStar, scStar, scKm1, scKp1);
    }

    private int rankOf(Set<Integer> C) {
        return (mode == Mode.Scoring) ? rankByScore(C) : ranksByTest(C);
    }

    private Set<Integer> shrinkToAtomicCore(Set<Integer> C, int k) {
        // Make a modifiable copy
        Set<Integer> cur = new HashSet<>(C);
        boolean changed;
        do {
            changed = false;
            // Try removing each element; if rank stays k, drop it
            for (Integer v : new ArrayList<>(cur)) {
                if (cur.size() <= k) return cur; // can’t go smaller than k elements
                cur.remove(v);
                if (rankOf(cur) == k) {
                    changed = true;     // keep v removed and restart scan
                    break;
                } else {
                    cur.add(v);         // needed; put it back
                }
            }
        } while (changed);
        return cur;
    }

    // ---- scored-rank helper (C vs D = V\C) + cache -----------------------------
    private int rankByScore(Set<Integer> C) {
        Key key = new Key(C);
        Integer cached = scoredRankCache.get(key);
        if (cached != null) return cached;

        int[] Carr = C.stream().mapToInt(Integer::intValue).sorted().toArray();
        int[] Darr = complementOf(C);
        if (Carr.length == 0 || Darr.length == 0) {
            scoredRankCache.put(key, 0);
            return 0;
        }

        ScoreSweep sw = rccaScoreSweepCached(Carr, Darr);
        int r = Math.max(0, sw.rStar);
        scoredRankCache.put(key, r);
        return r;
    }

    // Fast overload: takes primitive IDs and uses canonical Key (Wilks path)
    private int lookupRankFast(int[] ids) {
        Key k = new Key(ids);
        Integer cached = rankCache.get(k);
        if (cached != null) return cached;
        Set<Integer> s = new HashSet<>(ids.length * 2);
        for (int x : ids) s.add(x);
        int r = rank(s);
        rankCache.put(k, r);
        return r;
    }

    /**
     * Identifies and returns clusters of variables based on a predefined scoring or testing mechanism. This method
     * processes the output of the clustering procedure and formats it as a list of lists of integers, where each inner
     * list represents a single cluster of variables.
     *
     * @return a list of clusters, with each cluster represented as a list of integers. The clusters are sorted first by
     * size in descending order and then lexicographically by their variable names or identifiers.
     */
    public Map<Set<Integer>, Integer> findClusters() {
        return estimateClusters();
    }

    private List<List<Integer>> convertToLists(Map<Set<Integer>, Integer> clusters) {
        List<List<Integer>> ret = new ArrayList<>(clusters.size());
        for (Map.Entry<Set<Integer>, Integer> entry : clusters.entrySet()) {
            ret.add(new ArrayList<>(entry.getValue()));
        }
        return ret;
    }

    private Map<Set<Integer>, Integer> estimateClusters() {
        List<Integer> variables = allVariables();
        if (new HashSet<>(variables).size() != variables.size()) {
            throw new IllegalArgumentException("Variables must be unique.");
        }

        Map<Set<Integer>, Integer> setIntegerMap = clusterSearchMetaLoop();
//        printFractionPairwiseDependent(setIntegerMap.keySet());

        return setIntegerMap;
    }

//    private void printFractionPairwiseDependent(Set<Set<Integer>> sets) {
//
//        int[][] dependency = dependencyMatrix(S, expectedSampleSize, alpha);
//
////        System.out.println("dependency = ");
////
////        for (int i = 0; i < dependency.length; i++) {
////            for (int j = 0; j < dependency[i].length; j++) {
////                System.out.print(dependency[i][j] + " ");
////            }
////            System.out.println();
////        }
//
//        for (Set<Integer> set : sets) {
//            List<Integer> list = new ArrayList<>(set);
//            if (list.size() < 2) {
//                System.out.println("Set: " + set + ", Fraction: NaN (size < 2)");
//                continue;
//            }
//
//            int count = 0, total = 0;
//
//            for (int i = 0; i < list.size(); i++) {
//                for (int j = i + 1; j < list.size(); j++) {
//                    double dep = dependency[i][j];
//

    /// /                    System.out.printf("r = %.3f |Z| = %.3f%n", r, Math.abs(fisherZ));
//                    if (dep == 1.0) count++;
//                    total++;
//                }
//            }
//
//            System.out.println("Set: " + set + ", Fraction unconditionally dependent: "
//                               + (total > 0 ? (double) count / total : Double.NaN));
//        }
//    }
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

    /**
     * Sets the ridge parameter used in computations. The ridge parameter is typically employed for regularization
     * purposes to ensure numerical stability or to prevent overfitting. Updating this parameter clears the scored rank
     * cache and the sweep cache to maintain consistency with the new ridge value.
     *
     * @param ridge the ridge value to be set, a non-negative double that influences the regularization strength in the
     *              computations.
     */
    public void setRidge(double ridge) {
        this.ridge = ridge;
        scoredRankCache.clear();
        sweepCache.clear();
    }

    /**
     * Sets the penalty discount used in scoring computations. The penalty discount adjusts the penalization term in the
     * scoring process, influencing the trade-off between model complexity and fit. Updating this parameter clears the
     * scored rank cache and the sweep cache to ensure consistency with the new setting.
     *
     * @param c the penalty discount value to be set, a double representing the adjustment factor for the penalization
     *          term. It can take any valid numeric value, depending on the specific requirements of the scoring
     *          method.
     */
    public void setPenaltyDiscount(double c) {
        this.penaltyDiscount = c;
        scoredRankCache.clear();
        sweepCache.clear();
    }

    /**
     * Sets the EBIC gamma parameter used in scoring computations. The gamma parameter affects the penalization term in
     * the Extended Bayesian Information Criterion (EBIC), where higher values of gamma impose a stronger penalty on
     * model complexity. Updating this parameter clears the scored rank cache and the sweep cache to ensure consistency
     * with the new gamma setting.
     *
     * @param gamma the EBIC gamma value to be set, a non-negative double typically in the range [0, 1], where 0
     *              corresponds to the standard BIC and higher values prioritize simpler models.
     */
    public void setEbicGamma(double gamma) {
        this.ebicGamma = gamma;
        scoredRankCache.clear();
        sweepCache.clear();
    }

    /**
     * Sets the score margin to the specified value. The margin represents a non-negative threshold used in scoring
     * computations. If the provided margin is negative, it defaults to 0.0.
     *
     * @param margin the margin value to be set. A non-negative double value is expected. If a negative value is
     *               provided, it will be adjusted to 0.0.
     */
    public void setScoreMargin(double margin) {
        this.scoreMargin = Math.max(0.0, margin);
    }

    private @NotNull Map<Set<Integer>, Integer> clusterSearchMetaLoop() {
        List<Integer> remainingVars = new ArrayList<>(allVariables());
        clusterToRank = new HashMap<>();
//        reducedRank = new HashMap<>();

        for (int rank = 0; rank <= 3; rank++) {
            int size = rank + 1;
            if (Thread.currentThread().isInterrupted()) break;
            if (size >= remainingVars.size() - size) continue;

            log("EXAMINING SIZE " + size + " RANK = " + rank + " REMAINING VARS = " + remainingVars.size());
            Set<Set<Integer>> P = mode == Mode.Scoring ? findClustersAtRankScoring(remainingVars, size, rank)
                    : findClustersAtRankTesting(remainingVars, size, rank);
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

                if (seed.size() >= this.variables.size() - seed.size()) continue;

                int seedRankShown = mode == Mode.Scoring ? rankByScore(seed) : ranksByTest(seed);
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

                        // --- IMPORTANT: rank of the union by SCORE to match the seeding criterion ---
                        int rankOfUnion = rankByScore(union);
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

                int clusterRank = mode == Mode.Scoring ? rankByScore(cluster) : ranksByTest(cluster);

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

                        int newRank = mode == Mode.Scoring ? rankByScore(C2) : ranksByTest(C2);

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
        log("Now we will consider whether any of the penultimate clusters should be discarded (as from a non-latent DAG, e.g.).");

        boolean penultimateRemoved = false;

        // Try to split instead of outright reject (Dong-style refinement)
        for (Set<Integer> cluster : new HashSet<>(clusterToRank.keySet())) {
            if (failsSubsetTest(S, cluster, expectedSampleSize, alpha)) {
                clusterToRank.remove(cluster);
//                reducedRank.remove(cluster);
                penultimateRemoved = true;
            }
        }
        if (!penultimateRemoved) log("No penultimate clusters were removed.");

        // --- Atomic-core postprocess (keep large clusters, group by atomic cores) ---
        if (false) { // TODO Keep?
            Map<Key, Set<Integer>> coreToMax = new LinkedHashMap<>();

            for (Set<Integer> C : new ArrayList<>(clusterToRank.keySet())) {
                int k = clusterToRank.getOrDefault(C, 0);
                if (k <= 0 || C.size() <= k) {
                    // trivial case; nothing to shrink
                    coreToMax.putIfAbsent(new Key(C), new HashSet<>(C));
                    continue;
                }

                Set<Integer> core = shrinkToAtomicCore(C, k);

                Key ck = new Key(core);
                Set<Integer> maxForCore = coreToMax.get(ck);
                if (maxForCore == null) {
                    coreToMax.put(ck, new HashSet<>(C));
                } else {
                    maxForCore.addAll(C);  // unify multiple grown clusters sharing the same core
                }
            }

            // Replace clusterToRank with the maximal unions per core (keeping rank k)
            Map<Set<Integer>, Integer> collapsed = new LinkedHashMap<>();
            for (Map.Entry<Key, Set<Integer>> e : coreToMax.entrySet()) {
                Set<Integer> maxC = e.getValue();
                // Rank is the same k as the core’s (recompute or get from any representative)
                int k = rankOf(maxC);
                collapsed.put(maxC, k);
            }

            // Swap in the collapsed set (optional: only if you *want* “one latent per core”)
            clusterToRank.clear();
            clusterToRank.putAll(collapsed);
        }

        log("Final clusters = " + toNamesClusters(clusterToRank.keySet(), nodes));
        return clusterToRank;
    }

    /**
     * Try to split a cluster C into two smaller clusters C1, C2 if Rule 1 fails.
     */
    private Optional<List<Set<Integer>>> trySplitByRule1(Set<Integer> cluster) {
        final List<Integer> C = new ArrayList<>(cluster);
        final int rC = clusterToRank.getOrDefault(cluster, 0);

        // non-empty proper subsets
        SublistGenerator gen = new SublistGenerator(C.size(), C.size() - 1);
        int[] choice;

        int bestGain = Integer.MIN_VALUE;
        Set<Integer> bestC1 = null, bestC2 = null;

        while ((choice = gen.next()) != null) {
            if (choice.length == 0 || choice.length == C.size()) continue;

            Set<Integer> C1 = new HashSet<>(choice.length * 2);
            for (int i : choice) C1.add(C.get(i));
            Set<Integer> C2 = new HashSet<>(cluster);
            C2.removeAll(C1);
            if (C2.isEmpty()) continue;

            if (C1.size() >= variables.size() - C1.size()) continue;
            if (C2.size() >= variables.size() - C2.size()) continue;

            int[] c1 = C1.stream().mapToInt(Integer::intValue).toArray();
            int[] c2 = C2.stream().mapToInt(Integer::intValue).toArray();

            int minpq = Math.min(c1.length, c2.length);
            int l = Math.min(minpq, Math.max(0, rC));

            int r = RankTests.estimateWilksRank(S, c1, c2, expectedSampleSize, alpha);

            if (r < l) {
                int gain = l - r;
                if (gain > bestGain) {
                    bestGain = gain;
                    bestC1 = C1;
                    bestC2 = C2;
                }
            }
        }

        if (bestC1 != null && bestC2 != null) {
            return Optional.of(Arrays.asList(bestC1, bestC2));
        } else {
            return Optional.empty();
        }
    }

    // ---- subset tests (unchanged: still Wilks-based by design) -----------------
    private boolean failsSubsetTest(SimpleMatrix S, Set<Integer> cluster, int expectedSampleSize, double alpha) { /* ... unchanged ... */
        List<Integer> C = new ArrayList<>(cluster);
        List<Integer> D = allVariables();
        D.removeAll(cluster);

        { // Rule 1
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

                int minpq = Math.min(c1Array.length, c2Array.length);
                Integer l = clusterToRank.get(cluster);
                if (l == null) continue;
                l = Math.min(minpq, Math.max(0, l));

                int r = RankTests.estimateWilksRank(S, c1Array, c2Array, expectedSampleSize, alpha);
                if (r < l) {
                    log("Deficient! rank(" + toNamesCluster(C1, nodes) + ", " + toNamesCluster(C2, nodes) + ") = "
                        + r + " < " + l + "; removing " + toNamesCluster(cluster));
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
                for (int i : choice) _C.add(C.get(i));
                int[] _cArray = _C.stream().mapToInt(Integer::intValue).toArray();
                int[] dArray = D.stream().mapToInt(Integer::intValue).toArray();

                int minpq = Math.min(_cArray.length, dArray.length);
                Integer l = clusterToRank.get(cluster);
//                Integer l = Optional.ofNullable(reducedRank.get(cluster)).orElse(clusterToRank.getOrDefault(cluster, 0));
                l = Math.min(minpq, Math.max(0, l));

                int r = RankTests.estimateWilksRank(S, _cArray, dArray, expectedSampleSize, alpha);
                if (r < l) {
                    log("rank(" + toNamesCluster(_C, nodes) + ", D) = " + r + " < r = " + l
                        + "; removing cluster " + toNamesCluster(cluster));
                    return true;
                }
            }
        }
        { // Rule 3
            Integer rC = clusterToRank.get(cluster);
            if (rC == null) rC = 0;

            SublistGenerator gen2 = new SublistGenerator(C.size(), Math.min(C.size() - 1, rC));
            int[] choice2;
            while ((choice2 = gen2.next()) != null) {
                if (choice2.length < rC) continue;

                List<Integer> Z = new ArrayList<>();
                for (int i : choice2) Z.add(C.get(i));

                List<Integer> _C = new ArrayList<>(C);
                _C.removeAll(Z);

                int[] _cArray = _C.stream().mapToInt(Integer::intValue).toArray();
                int[] dArray = D.stream().mapToInt(Integer::intValue).toArray();
                int[] zArray = Z.stream().mapToInt(Integer::intValue).toArray();

                int rZ = RankTests.estimateWilksRankConditioned(S, _cArray, dArray, zArray, expectedSampleSize, alpha);
                if (rZ == 0) {
                    log("rank(_C = " + toNamesCluster(_C, nodes) + ", D | Z = " + toNamesCluster(Z, nodes) + ") = 0; removing cluster " + toNamesCluster(cluster) + ".");
                    return true;
                }
            }
        }
        return false;
    }

    private int ranksByTest(Set<Integer> cluster) {
        if (mode == Mode.Scoring) return rankByScore(cluster);
        Key k = new Key(cluster);
        Integer cached = rankCache.get(k);
        if (cached != null) return cached;
        int r = rank(cluster);
        rankCache.put(k, r);
        return r;
    }

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
            Node latent = new GraphNode("L" + (i + 1) + "(" + rankSpec + ")");
            latent.setNodeType(NodeType.LATENT);
            latents.add(latent);
        }
        return latents;
    }

    private int[] complementOf(Set<Integer> C) {
        // V \ C
        int n = variables.size();
        BitSet inC = new BitSet(n);
        for (int v : C) inC.set(v);
        int[] out = new int[n - C.size()];
        int k = 0;
        for (int i = 0; i < n; i++) if (!inC.get(i)) out[k++] = i;
        return out;
    }

    private void log(String s) {
        if (verbose) TetradLogger.getInstance().log(s);
    }

    private String toNamesCluster(Set<Integer> cluster) {
        return cluster.stream().map(i -> nodes.get(i).getName()).collect(Collectors.joining(" ", "{", "}"));
    }

    /**
     * Retrieves the list of clusters. Each cluster is represented as a list of integers, where each integer corresponds
     * to a variable identifier.
     *
     * @return a list of clusters, where each cluster is represented as a list of integers
     */
    public Map<Set<Integer>, Integer> getClusters() {
        return new HashMap<>(this.clusters);
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

    /**
     * Represents the operational modes available for the Tsc class. The mode determines the behavior of corresponding
     * processing methods, which can perform either testing or scoring operations.
     */
    public enum Mode {

        /**
         * Represents the testing mode for the Tsc class. This mode configures the object's behavior to perform
         * operations related to testing logic.
         */
        Testing,

        /**
         * Represents the scoring mode for the Tsc class. This mode configures the object's behavior to perform
         * operations related to scoring logic.
         */
        Scoring
    }

    /**
     * @param mMax   max admissible rank
     * @param rStar  argmax rank
     * @param scBest score at rStar
     * @param scKm1  score at rStar-1 (NaN if not defined)
     * @param scKp1  score at rStar+1 (NaN if not defined)
     */ // ---- helper: RCCA-BIC sweep ------------------------------------------------
    private record ScoreSweep(int mMax, int rStar, double scBest, double scKm1, double scKp1) {
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