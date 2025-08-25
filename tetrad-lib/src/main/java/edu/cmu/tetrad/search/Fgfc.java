package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.CorrelationMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.utils.ClusterSignificance;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.RankTests;
import edu.cmu.tetrad.util.TetradLogger;
import org.ejml.simple.SimpleMatrix;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.apache.commons.math3.special.Erf.erf;

/**
 * Find General Factor Clusters (FGFC). This generalized FOFC and FTFC to first find clusters using pure 2-tads (pure
 * tetrads) and then clusters using pure 3-tads (pure sextads) out of the remaining variables. We do not use an n-tad
 * test here since we need to check rank, so we will check rank directly. (This is equivqalent to using the CCA n-tad
 * test.)
 * <p>
 * Kummerfeld, E., & Ramsey, J. (2016, August). Causal clustering for 1-factor measurement models. In Proceedings of
 * the 22nd ACM SIGKDD international conference on knowledge discovery and data mining (pp. 1655-1664).
 * <p>
 * Spirtes, P., Glymour, C. N., Scheines, R., & Heckerman, D. (2000). Causation, prediction, and search. MIT press.
 *
 * @author erichkummerfeld
 * @author peterspirtes
 * @author josephramsey
 * @version $Id: $Id
 * @see Ftfc
 */
public class Fgfc {
    /** Correlation matrix (shared, read-only). */
    private final SimpleMatrix S;
    /** All variables (shared, read-only). */
    private final List<Node> variables;
    /** Significance level. */
    private final double alpha;
    /** Sample size. */
    private final int n;
    /** Verbosity. */
    private boolean verbose = true;

    /** Concurrent caches for purity (thread-safe). */
    private Set<Set<Integer>> pureTets;
    private Set<Set<Integer>> impureTets;

    /** Parallel execution pool. */
    private ForkJoinPool fjPool;

    /** Constructor. */
    public Fgfc(DataSet dataSet, double alpha) {
        this.variables = dataSet.getVariables();
        this.alpha = alpha;
        this.S = new CorrelationMatrix(dataSet).getMatrix().getSimpleMatrix();
        this.n = dataSet.getNumRows();
        int defaultPar = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        this.fjPool = new ForkJoinPool(defaultPar);
    }

    /** Optional: set max parallelism (>=1). */
    public void setParallelism(int parallelism) {
        int p = Math.max(1, parallelism);
        ForkJoinPool old = this.fjPool;
        this.fjPool = new ForkJoinPool(p);
        if (old != null) old.shutdown();
    }

    // Canonical, immutable key for clusters to avoid order/mutation hazards
    private static List<Integer> canonKey(Collection<Integer> xs) {
        List<Integer> s = new ArrayList<>(xs);
        Collections.sort(s);
        return Collections.unmodifiableList(s);
    }

    /** Runs the search (rank-1 then rank-2) and returns clusters → rank. */
    public Map<List<Integer>, Integer> findClusters() {
        // thread-safe sets
        this.pureTets = Collections.newSetFromMap(new ConcurrentHashMap<>());
        this.impureTets = Collections.newSetFromMap(new ConcurrentHashMap<>());

        Map<List<Integer>, Integer> clustersToRanks = new HashMap<>();

        for (int rank = 1; rank <= 2; rank++) {
            estimateClustersSag(rank, clustersToRanks);
        }
        return clustersToRanks;
    }

    public void setVerbose(boolean verbose) { this.verbose = verbose; }

    private List<Integer> allVariables() {
        List<Integer> _variables = new ArrayList<>();
        for (int i = 0; i < this.variables.size(); i++) _variables.add(i);
        return _variables;
    }

    /** Rank phase: pure seeds then mixed seeds. */
    private void estimateClustersSag(int rank, Map<List<Integer>, Integer> clustersToRanks) {
        List<Integer> variables = allVariables();
        if (new HashSet<>(variables).size() != variables.size()) {
            throw new IllegalArgumentException("Variables must be unique.");
        }

        findPureClusters(rank, clustersToRanks);
        findMixedClusters(rank, clustersToRanks);

        TetradLogger.getInstance().log(
                "clusters rank " + rank + " = " +
                ClusterSignificance.variablesForIndices(clustersToRanks.keySet(), this.variables)
        );
    }

    /** Find seeds of size 2*(rank+1) among remaining variables, grow, accept, and lock out. */
    private void findPureClusters(int rank, Map<List<Integer>, Integer> clustersToRanks) {
        List<Integer> variables = allVariables();
        int clusterSize = 2 * (rank + 1);

        List<Integer> unclustered = new ArrayList<>(variables);
        unclustered.removeAll(union(clustersToRanks.keySet()));

        if (unclustered.size() < clusterSize) return;

        final List<Integer> pool = new ArrayList<>(unclustered);
        ChoiceGenerator gen = new ChoiceGenerator(pool.size(), clusterSize);
        int[] choice;

        while ((choice = gen.next()) != null) {
            if (Thread.currentThread().isInterrupted()) break;

            // Build candidate seed from snapshot 'pool'; ensure still unclustered
            List<Integer> cluster = new ArrayList<>(clusterSize);
            boolean ok = true;
            for (int c : choice) {
                int v = pool.get(c);
                if (!unclustered.contains(v)) { ok = false; break; }
                cluster.add(v);
            }
            if (!ok) continue;

            if (pure(cluster) == Purity.PURE) {
                growCluster(cluster, rank, clustersToRanks);

                if (this.verbose) {
                    log("Cluster found: " + ClusterSignificance.variablesForIndices(cluster, this.variables));
                }

                clustersToRanks.put(canonKey(cluster), rank);
                unclustered.removeAll(cluster);
            }
        }
    }

    /** Grow a seed by testing all (k)-subsets + o for purity; k = min(|cluster|, tadSize-1). */
    private void growCluster(List<Integer> cluster, int rank, Map<List<Integer>, Integer> clustersToRanks) {
        final int tadSize = 2 * (rank + 1);

        // Unclustered candidates
        List<Integer> unclustered = allVariables();
        unclustered.removeAll(union(clustersToRanks.keySet()));
        unclustered.removeAll(cluster);

        // Pre-enumerate k-subsets of the current cluster once (read-only)
        final int k = Math.min(cluster.size(), tadSize - 1);
        final List<List<Integer>> subsets = new ArrayList<>();
        if (k > 0) {
            ChoiceGenerator gen = new ChoiceGenerator(cluster.size(), k);
            int[] choice;
            while ((choice = gen.next()) != null) {
                if (Thread.currentThread().isInterrupted()) return;
                List<Integer> sub = new ArrayList<>(k);
                for (int j : choice) sub.add(cluster.get(j));
                subsets.add(sub);
            }
        } else {
            subsets.add(Collections.emptyList());
        }

        // Parallel: for each o, require all subsets∪{o} be PURE
        List<Integer> toAdd = submit(() ->
                unclustered.parallelStream()
                        .filter(o -> {
                            // early exit inside sequential loop per o
                            for (List<Integer> sub : subsets) {
                                List<Integer> tad = new ArrayList<>(sub.size() + 1);
                                tad.addAll(sub);
                                tad.add(o);
                                if (pure(tad) != Purity.PURE) return false;
                            }
                            return true;
                        })
                        .collect(Collectors.toList())
        );

        cluster.addAll(toAdd);
    }

    /** Find mixed clusters: size (2*(rank+1)-1) subsets C with ∀o∉C, vanishes(C∪{o}). */
    private void findMixedClusters(int rank, Map<List<Integer>, Integer> clustersToRanks) {
        final int tadSize = 2 * (rank + 1);

        if (union(clustersToRanks.keySet()).isEmpty()) return;

        List<Integer> unclustered = new ArrayList<>(allVariables());
        unclustered.removeAll(new HashSet<>(union(clustersToRanks.keySet())));

        if (unclustered.size() < tadSize - 1) return;

        final List<Integer> variables = new ArrayList<>(unclustered);
        ChoiceGenerator gen = new ChoiceGenerator(variables.size(), tadSize - 1);
        int[] choice;

        CHOICE:
        while ((choice = gen.next()) != null) {
            if (Thread.currentThread().isInterrupted()) break;

            List<Integer> cluster = new ArrayList<>();
            for (int c : choice) {
                int v = variables.get(c);
                if (!unclustered.contains(v)) continue CHOICE;
                cluster.add(v);
            }

            // Parallel: check if ANY o violates vanishing ⇒ reject;
            // otherwise accept the mixed cluster.
            boolean violates = submit(() ->
                    IntStream.range(0, this.variables.size()).parallel()
                            .filter(o -> !cluster.contains(o))
                            .anyMatch(o -> {
                                List<Integer> _cluster = new ArrayList<>(cluster);
                                _cluster.add(o);
                                return !vanishes(_cluster);
                            })
            );

            if (violates) continue;

            clustersToRanks.put(canonKey(cluster), rank);
            unclustered.removeAll(cluster);

            if (this.verbose) {
                log((2 * (rank + 1) - 1) + "-cluster found: " +
                    ClusterSignificance.variablesForIndices(cluster, this.variables));
            }
        }
    }

    /** Parallel-aware purity check with concurrent caching. */
    private Purity pure(List<Integer> tad) {
        Set<Integer> key = new HashSet<>(tad);
        if (pureTets.contains(key))  return Purity.PURE;
        if (impureTets.contains(key)) return Purity.IMPURE;

        if (!vanishes(tad)) {
            impureTets.add(key);
            return Purity.IMPURE;
        }

        // Substitution test (parallel): if ANY substitution fails to vanish ⇒ IMPURE
        final int p = this.variables.size();
        final Set<Integer> tadSet = new HashSet<>(tad);
        final int len = tad.size();

        boolean bad = submit(() ->
                IntStream.range(0, p).parallel()
                        .filter(o -> !tadSet.contains(o))
                        .anyMatch(o ->
                                IntStream.range(0, len)
                                        .anyMatch(j -> {
                                            List<Integer> _tad = new ArrayList<>(tad);
                                            _tad.set(j, o);
                                            return !vanishes(_tad);
                                        })
                        )
        );

        if (bad) {
            impureTets.add(key);
            return Purity.IMPURE;
        } else {
            pureTets.add(key);
            return Purity.PURE;
        }
    }

    /** Vanishing: all bipartitions X|Y of equal/remaining sizes must have estimated rank = r. */
    private boolean vanishes(List<Integer> cluster) {
        int leftSize = cluster.size() / 2;
        ChoiceGenerator gen = new ChoiceGenerator(cluster.size(), leftSize);
        int[] choice;

        while ((choice = gen.next()) != null) {
            if (Thread.currentThread().isInterrupted()) break;

            int[] x = new int[leftSize];
            for (int i = 0; i < leftSize; i++) x[i] = cluster.get(choice[i]);

            int[] y = new int[cluster.size() - leftSize];
            int yIndex = 0;
            for (int value : cluster) {
                boolean found = false;
                for (int xv : x) { if (xv == value) { found = true; break; } }
                if (!found) y[yIndex++] = value;
            }

            int r = Math.min(x.length, y.length) - 1;
//            int rank = RankTests.estimateWilksRank(S, x, y, n, alpha);
            int rank = estimateWilksRankFast(S, x, y, n, alpha);
            if (rank != r) return false;
        }
        return true;
    }

    /** Union helper. */
    private Set<Integer> union(Set<List<Integer>> pureClusters) {
        Set<Integer> unionPure = new HashSet<>();
        for (Collection<Integer> cluster : pureClusters) unionPure.addAll(cluster);
        return unionPure;
    }

    /** Logging helper. */
    private void log(String s) {
        if (this.verbose) TetradLogger.getInstance().log(s);
    }

    /** Submit a parallel task to our pool and get the result. */
    private <T> T submit(java.util.concurrent.Callable<T> task) {
        try {
            return fjPool.submit(task).get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // --- Fast Bartlett rank estimator (no Apache Chi-square; no CDF) ---
    private int estimateWilksRankFast(SimpleMatrix S, int[] xIdx, int[] yIdx, int n, double alpha) {
        final int p = xIdx.length, q = yIdx.length;
        if (p == 0 || q == 0) return 0;
        final int m = Math.min(p, q);

        // submatrices
        SimpleMatrix Sxx = submatrix(S, xIdx, xIdx);
        SimpleMatrix Syy = submatrix(S, yIdx, yIdx);
        SimpleMatrix Sxy = submatrix(S, xIdx, yIdx);
        SimpleMatrix Syx = Sxy.transpose();

        // tiny ridge for numerical stability
        double eps = 1e-10;
        for (int i = 0; i < p; i++) Sxx.set(i, i, Sxx.get(i, i) + eps);
        for (int i = 0; i < q; i++) Syy.set(i, i, Syy.get(i, i) + eps);

        SimpleMatrix SxxInv = Sxx.invert();
        SimpleMatrix SyyInv = Syy.invert();

        // M has eigenvalues = rho^2 (on the smaller side)
        // Use the smaller dimension side for eigen-decomp
        SimpleMatrix M;
        boolean xSmall = (p <= q);
        if (xSmall) {
            M = SxxInv.mult(Sxy).mult(SyyInv).mult(Syx); // p x p
        } else {
            M = SyyInv.mult(Syx).mult(SxxInv).mult(Sxy); // q x q
        }

        // eigenvalues (non-negative), sort descending, clamp to [0,1)
        double[] evals = eigSymmetricClamped(M);
        Arrays.sort(evals);
        // reverse to descending
        for (int i = 0; i < evals.length / 2; i++) {
            double tmp = evals[i];
            evals[i] = evals[evals.length - 1 - i];
            evals[evals.length - 1 - i] = tmp;
        }
        int L = Math.min(evals.length, m);
        double[] rho2 = Arrays.copyOf(evals, L);

        // For r = 0..m-1, test H0: rank ≤ r using Bartlett χ² approx
        // stat_r = - (n - 1 - (p+q+1)/2) * sum_{i=r+1..m} ln(1 - rho_i^2)
        // ν_r = (p - r)(q - r)
        double c = (n - 1 - (p + q + 1) / 2.0);
        int hat = 0;
        for (int r = 0; r < m; r++) {
            double sum = 0.0;
            for (int i = r; i < L; i++) {
                double oneMinus = Math.max(1e-15, 1.0 - rho2[i]);
                sum += Math.log(oneMinus);
            }
            double stat = -c * sum;
            int nu = (p - r) * (q - r);
            if (nu <= 0) { hat = Math.max(hat, r); continue; }

            double crit = chi2CriticalWH(nu, alpha);
            if (stat <= crit) {
                hat = Math.max(hat, r); // fail to reject ⇒ rank ≤ r plausible
            } else {
                // reject H0(rank ≤ r); continue increasing r won’t help acceptance
                // (stat decreases with larger r), but we still compute loop to keep it simple
            }
        }
        return hat;
    }

    // helpers: symmetric eigvals with clamping
    private static double[] eigSymmetricClamped(SimpleMatrix A) {
        org.ejml.simple.SimpleEVD<SimpleMatrix> evd = A.eig();
        int n = A.numCols();
        double[] vals = new double[n];
        for (int i = 0; i < n; i++) {
            double v = evd.getEigenvalue(i).getReal();
            if (Double.isNaN(v) || v < 0) v = 0;
            if (v > 1) v = 1;
            vals[i] = v;
        }
        return vals;
    }

    // submatrix utility (row/col index arrays)
    private static SimpleMatrix submatrix(SimpleMatrix S, int[] rows, int[] cols) {
        SimpleMatrix M = new SimpleMatrix(rows.length, cols.length);
        for (int i = 0; i < rows.length; i++)
            for (int j = 0; j < cols.length; j++)
                M.set(i, j, S.get(rows[i], cols[j]));
        return M;
    }

    // --- Fast Chi-square critical via Wilson–Hilferty with cached normal z ---
    private static final Map<Long, Double> CHI2_CRIT_CACHE = new ConcurrentHashMap<>();

    private static double chi2CriticalWH(int nu, double alpha) {
        // key (nu, alpha*1e9) packed into a long for small cache
        long key = (((long) nu) << 32) ^ Double.valueOf(alpha).hashCode();
        Double cv = CHI2_CRIT_CACHE.get(key);
        if (cv != null) return cv;

        // z_{1-alpha} using a very good rational approximation (AS241 style)
        double z = invNormal1mAlpha(alpha);

        // Wilson–Hilferty: Q_{1-a,ν} ≈ ν * [1 - 2/(9ν) + z * sqrt(2/(9ν))]^3
        double n = nu;
        double a = 2.0 / (9.0 * n);
        double q = n * Math.pow(1.0 - a + z * Math.sqrt(a), 3.0);

        CHI2_CRIT_CACHE.put(key, q);
        return q;
    }

    /** Inverse normal for tail 1-alpha: returns z_{1-alpha}.  */
    private static double invNormal1mAlpha(double alpha) {
        // Moro/AS241 hybrid; good to ~1e-8 and branchless enough.
        // Convert to p in (0,1), then use probit(p).
        double p = 1.0 - alpha;

        // Coefficients for central region
        final double[] a = { -3.969683028665376e+01, 2.209460984245205e+02, -2.759285104469687e+02,
                1.383577518672690e+02, -3.066479806614716e+01, 2.506628277459239e+00 };
        final double[] b = { -5.447609879822406e+01, 1.615858368580409e+02, -1.556989798598866e+02,
                6.680131188771972e+01, -1.328068155288572e+01 };
        final double[] c = { -7.784894002430293e-03, -3.223964580411365e-01, -2.400758277161838e+00,
                -2.549732539343734e+00, 4.374664141464968e+00, 2.938163982698783e+00 };
        final double[] d = { 7.784695709041462e-03,  3.224671290700398e-01,  2.445134137142996e+00,
                3.754408661907416e+00 };

        // Break-points
        final double plow = 0.02425;
        final double phigh = 1.0 - plow;
        double q, r, x;

        if (p < plow) {
            // lower tail
            q = Math.sqrt(-2.0 * Math.log(p));
            x = (((((c[0]*q + c[1])*q + c[2])*q + c[3])*q + c[4])*q + c[5]) /
                ((((d[0]*q + d[1])*q + d[2])*q + d[3])*q + 1.0);
        } else if (p > phigh) {
            // upper tail
            q = Math.sqrt(-2.0 * Math.log(1.0 - p));
            x = -(((((c[0]*q + c[1])*q + c[2])*q + c[3])*q + c[4])*q + c[5]) /
                ((((d[0]*q + d[1])*q + d[2])*q + d[3])*q + 1.0);
        } else {
            // central
            q = p - 0.5;
            r = q * q;
            x = (((((a[0]*r + a[1])*r + a[2])*r + a[3])*r + a[4])*r + a[5]) * q /
                (((((b[0]*r + b[1])*r + b[2])*r + b[3])*r + b[4])*r + 1.0);
        }

        // One Newton step for polish
        // Φ(x) via erf
        double err = 0.5 * (1.0 + erf(x / Math.sqrt(2.0))) - p;
        double pdf = Math.exp(-0.5 * x * x) / Math.sqrt(2.0 * Math.PI);
        x -= err / pdf;

        return x;
    }

    private enum Purity {PURE, IMPURE}
}

