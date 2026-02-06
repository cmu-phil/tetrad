package edu.cmu.tetrad.search.score;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.Math.*;

/**
 * <h2>Minimax Histogram Score (Student-t, shared scale)</h2>
 *
 * <p>
 * Local score for structure learning based on a "minimax-style" conditioning idea:
 * given candidate parents Z for target Y, we condition on Z by quantile-binning the
 * rows in Z-space (so Z is approximately constant within each bin), then model
 * Y within each bin using a robust Student-t location model.
 * </p>
 *
 * <p>
 * <b>Model.</b> Let groups (bins) be formed from Z; each bin b has its own mean μ_b,
 * but all bins share a common scale s. With fixed degrees of freedom ν, the local
 * log-likelihood is:
 * </p>
 *
 * <pre>
 *   ℓ = Σ_b Σ_{i∈b} log t_ν( (y_i - μ_b) / s ) - n log s
 * </pre>
 *
 * <p>
 * μ_b and s are fit by a fast IRLS/EM-style iteration (weights w_i).
 * The returned score is BIC-style:
 * </p>
 *
 * <pre>
 *   score = ℓ - 0.5 * k * log(n),   k = (#bins) + 1
 * </pre>
 *
 * <p>
 * Notes:
 * <ul>
 *   <li>Continuous data only (uses {@link DataSet#getDouble(int, int)}).</li>
 *   <li>Missing values are handled by testwise deletion at the local-score level:
 *       only rows where Y and all Z columns are observed are used.</li>
 *   <li>Designed for repeated calls inside score-based searches; caches groupings and scores.</li>
 * </ul>
 * </p>
 */
public final class MinimaxHistogramScore implements Score {

    // -------------------- data --------------------

    private final DataSet data;
    private final List<Node> variables;
    private final Map<String, Integer> indexMap;
    private final int sampleSize;

    // -------------------- configuration knobs --------------------

    /** Number of quantile bins per parent dimension. */
    private volatile int binsPerDim = 4;

    /** Minimum bin size to be considered usable. */
    private volatile int minBinSize = 5;

    /** Student-t degrees of freedom (fixed). Smaller => heavier tails. */
    private volatile double nu = 5.0;

    /** Max IRLS iterations for (μ_b, shared s). */
    private volatile int maxIters = 7;

    /** Absolute minimum scale floor (applied after robust initialization too). */
    private volatile double sMin = 1e-8;

    /** Optional: restrict to a specified set of rows; null => all rows. */
    private volatile List<Integer> rows = null;

    /** If true, log some failures. */
    private volatile boolean verbose = false;

    // -------------------- caches --------------------

    /** Cache groups keyed by (parents, rowsSig, binsPerDim, minBinSize). */
    private final GroupCache groupCache = new GroupCache();

    /** Cache local scores keyed by (target, parents, rowsSig, all tuning knobs). */
    private final ConcurrentHashMap<ScoreKey, Double> localScoreCache = new ConcurrentHashMap<>();

    // -------------------- ctor --------------------

    public MinimaxHistogramScore(DataSet data) {
        if (data == null) throw new NullPointerException("data");
        if (!data.isContinuous()) {
            throw new IllegalArgumentException("MinimaxHistogramScore currently requires a continuous DataSet.");
        }
        this.data = data;
        this.variables = Collections.unmodifiableList(new ArrayList<>(data.getVariables()));
        this.indexMap = indexMap(this.variables);
        this.sampleSize = data.getNumRows();
    }

    // -------------------- Score interface --------------------

    @Override
    public double localScore(int node, int... parents) {
        if (node < 0 || node >= variables.size()) return Double.NaN;

        int[] zIdx = (parents == null) ? new int[0] : Arrays.copyOf(parents, parents.length);
        Arrays.sort(zIdx);

        List<Integer> baseRows = listRows();
        List<Integer> useRows = rowsCompleteFor(node, zIdx, baseRows);
        final int n = useRows.size();
        if (n < 10) return Double.NaN;

        long rowsSig = GroupCache.rowsSignature(useRows);

        ScoreKey key = new ScoreKey(
                node, zIdx, rowsSig,
                binsPerDim, minBinSize,
                nu, maxIters, sMin
        );

        return localScoreCache.computeIfAbsent(key, k -> {
            try {
                // Extract y aligned to 0..n-1 index space
                double[] y = new double[n];
                for (int i = 0; i < n; i++) y[i] = data.getDouble(useRows.get(i), node);

                // No parents: one group with all rows => Student-t location + shared scale reduces to single-mean t fit
                int[][] groups;
                if (zIdx.length == 0) {
                    groups = new int[][]{range(n)};
                } else {
                    groups = groupCache.getGroups(this, zIdx, useRows);
                }

                if (groups.length == 0) return Double.NaN;

                Fit fit = fitStudentTSharedScale(y, groups, nu, maxIters, sMin);
                if (!Double.isFinite(fit.s) || !(fit.s > 0)) return Double.NaN;

                double ll = studentTLogLikSharedScale(y, groups, fit.muByGroup, fit.s, nu);

                // BIC-like penalty: k = (#bins means) + (shared scale)
                int B = groups.length;
                int kParams = B + 1;
                double penalty = 0.5 * kParams * Math.log(n);

                double score = ll - penalty;
                if (!Double.isFinite(score)) return Double.NaN;
                return score;

            } catch (RuntimeException e) {
                if (verbose) TetradLogger.getInstance().log(e.getMessage());
                return Double.NaN;
            }
        });
    }

    @Override
    public List<Node> getVariables() {
        return variables;
    }

    @Override
    public int getSampleSize() {
        return sampleSize;
    }

    @Override
    public String toString() {
        return "Minimax Histogram Score (Student-t, shared scale)";
    }

    // -------------------- knobs --------------------

    public void setBinsPerDim(int b) {
        this.binsPerDim = Math.max(2, b);
        clearCaches();
    }

    public void setMinBinSize(int m) {
        this.minBinSize = Math.max(3, m);
        clearCaches();
    }

    public void setNu(double nu) {
        if (!(nu > 2.0) || !Double.isFinite(nu)) { // >2 gives finite variance; you can relax if you want
            throw new IllegalArgumentException("nu must be finite and > 2");
        }
        this.nu = nu;
        localScoreCache.clear();
    }

    public void setMaxIters(int iters) {
        this.maxIters = Math.max(1, iters);
        localScoreCache.clear();
    }

    public void setScaleFloor(double sMin) {
        if (!(sMin > 0) || !Double.isFinite(sMin)) throw new IllegalArgumentException("sMin must be finite and > 0");
        this.sMin = sMin;
        localScoreCache.clear();
    }

    public void setRows(List<Integer> rows) {
        this.rows = rows;
        clearCaches();
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    private void clearCaches() {
        groupCache.clear();
        localScoreCache.clear();
    }

    // -------------------- fitting: Student-t with bin means + shared scale --------------------

    private record Fit(double[] muByGroup, double s) {}

    /**
     * Fits μ_b per group and a shared scale s using an IRLS/EM-style iteration.
     * We use weights w_i = (ν+1)/(ν + (r_i/s)^2).
     */
    private static Fit fitStudentTSharedScale(double[] y, int[][] groups, double nu, int maxIters, double sMinAbs) {
        final int n = y.length;
        final int B = groups.length;

        // init mu per group as mean, and pooled scale as robust-ish (MAD of residuals)
        double[] mu = new double[B];
        for (int b = 0; b < B; b++) {
            int[] g = groups[b];
            double m = 0.0;
            for (int idx : g) m += y[idx];
            mu[b] = m / g.length;
        }

        double s = initialPooledScale(y, groups, mu);
        s = Math.max(s, sMinAbs);

        // iterate
        double[] w = new double[n];
        for (int iter = 0; iter < maxIters; iter++) {

            // E-step weights
            for (int b = 0; b < B; b++) {
                int[] g = groups[b];
                double mub = mu[b];
                for (int idx : g) {
                    double r = y[idx] - mub;
                    double u = r / s;
                    double denom = nu + u * u;
                    w[idx] = (nu + 1.0) / denom;
                }
            }

            // M-step: update mu_b as weighted mean
            for (int b = 0; b < B; b++) {
                int[] g = groups[b];
                double num = 0.0, den = 0.0;
                for (int idx : g) {
                    double wi = w[idx];
                    num += wi * y[idx];
                    den += wi;
                }
                if (den > 0) mu[b] = num / den;
            }

            // update shared scale (pooled)
            double ss = 0.0;
            for (int b = 0; b < B; b++) {
                int[] g = groups[b];
                double mub = mu[b];
                for (int idx : g) {
                    double r = y[idx] - mub;
                    ss += w[idx] * r * r;
                }
            }
            double sNew = Math.sqrt(Math.max(ss / Math.max(1, n), 0.0));
            sNew = Math.max(sNew, sMinAbs);

            // convergence check (cheap)
            if (Math.abs(sNew - s) <= 1e-6 * (s + 1.0)) {
                s = sNew;
                break;
            }
            s = sNew;
        }

        return new Fit(mu, s);
    }

    private static double initialPooledScale(double[] y, int[][] groups, double[] muByGroup) {
        // pooled MAD of residuals across all bins
        int n = y.length;
        double[] r = new double[n];
        for (int b = 0; b < groups.length; b++) {
            int[] g = groups[b];
            double mu = muByGroup[b];
            for (int idx : g) r[idx] = y[idx] - mu;
        }
        // MAD around median
        double med = medianCopy(r);
        for (int i = 0; i < n; i++) r[i] = Math.abs(r[i] - med);
        double mad = medianCopy(r);
        // 1.4826 converts MAD -> sigma for Normal; still a decent robust scale init
        double s = 1.4826 * mad;
        if (!Double.isFinite(s) || !(s > 0)) {
            // fallback to pooled sd
            double mean = 0.0;
            for (double v : y) mean += v;
            mean /= n;
            double ss = 0.0;
            for (double v : y) {
                double d = v - mean;
                ss += d * d;
            }
            s = Math.sqrt(ss / Math.max(1, n - 1));
        }
        return s;
    }

    private static double studentTLogLikSharedScale(
            double[] y, int[][] groups, double[] muByGroup, double s, double nu
    ) {
        final int n = y.length;
        if (!(s > 0) || !Double.isFinite(s)) return Double.NaN;

        // constant term per observation
        double c = logGamma((nu + 1.0) / 2.0) - logGamma(nu / 2.0) - 0.5 * Math.log(nu * Math.PI);

        double ll = 0.0;
        double logS = Math.log(s);

        for (int b = 0; b < groups.length; b++) {
            int[] g = groups[b];
            double mu = muByGroup[b];
            for (int idx : g) {
                double u = (y[idx] - mu) / s;
                double term = 1.0 + (u * u) / nu;
                ll += c - logS - 0.5 * (nu + 1.0) * Math.log(term);
            }
        }

        if (!Double.isFinite(ll)) return Double.NaN;
        return ll;
    }

    // -------------------- grouping: quantile binning --------------------

    private int[][] computeGroups(int[] zIdx, List<Integer> useRows) {
        int n = useRows.size();
        int d = zIdx.length;
        if (d == 0) return new int[][]{range(n)};

        // Pull Z into local array Z[n][d]
        double[][] Z = new double[n][d];
        for (int i = 0; i < n; i++) {
            int r = useRows.get(i);
            for (int j = 0; j < d; j++) Z[i][j] = data.getDouble(r, zIdx[j]);
        }

        // Quantile edges per dim: edges[j][k] for k=1..binsPerDim-1
        double[][] edges = new double[d][binsPerDim - 1];
        for (int j = 0; j < d; j++) {
            double[] col = new double[n];
            for (int i = 0; i < n; i++) col[i] = Z[i][j];
            Arrays.sort(col);
            for (int k = 1; k < binsPerDim; k++) {
                int q = (int) Math.floor((k * (n - 1.0)) / binsPerDim);
                edges[j][k - 1] = col[q];
            }
        }

        // Assign bin ids using mixed radix
        int[] binId = new int[n];
        int radix = binsPerDim;

        for (int i = 0; i < n; i++) {
            int id = 0;
            int mult = 1;
            for (int j = 0; j < d; j++) {
                double v = Z[i][j];
                double[] ej = edges[j];
                int b = upperBound(ej, v); // 0..binsPerDim-1
                id += b * mult;
                mult *= radix;
            }
            binId[i] = id;
        }

        // Group indices by binId
        Map<Integer, IntArrayList> map = new HashMap<>();
        for (int i = 0; i < n; i++) {
            map.computeIfAbsent(binId[i], k -> new IntArrayList()).add(i);
        }

        ArrayList<int[]> groups = new ArrayList<>();
        for (IntArrayList g : map.values()) {
            if (g.size() >= minBinSize) groups.add(g.toArray());
        }

        return groups.toArray(new int[0][]);
    }

    // -------------------- missingness row selection --------------------

    private List<Integer> rowsCompleteFor(int yIdx, int[] zIdx, List<Integer> baseRows) {
        List<Integer> out = new ArrayList<>(baseRows.size());
        for (int r : baseRows) {
            double vy = data.getDouble(r, yIdx);
            if (Double.isNaN(vy)) continue;

            boolean ok = true;
            for (int j : zIdx) {
                double vz = data.getDouble(r, j);
                if (Double.isNaN(vz)) { ok = false; break; }
            }
            if (ok) out.add(r);
        }
        return out;
    }

    private List<Integer> listRows() {
        if (rows != null) return rows;
        int n = data.getNumRows();
        List<Integer> r = new ArrayList<>(n);
        for (int i = 0; i < n; i++) r.add(i);
        return r;
    }

    // -------------------- utilities --------------------

    private static Map<String, Integer> indexMap(List<Node> vars) {
        Map<String, Integer> m = new HashMap<>();
        for (int i = 0; i < vars.size(); i++) m.put(vars.get(i).getName(), i);
        return m;
    }

    private static int upperBound(double[] edges, double v) {
        int lo = 0, hi = edges.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (v > edges[mid]) lo = mid + 1;
            else hi = mid;
        }
        return lo;
    }

    private static int[] range(int n) {
        int[] r = new int[n];
        for (int i = 0; i < n; i++) r[i] = i;
        return r;
    }

    // helper list
    private static final class IntArrayList {
        private int[] a = new int[16];
        private int n = 0;
        void add(int v) { if (n == a.length) a = Arrays.copyOf(a, a.length * 2); a[n++] = v; }
        int size() { return n; }
        int[] toArray() { return Arrays.copyOf(a, n); }
    }

    // -------------------- logGamma (Lanczos) --------------------

    private static double logGamma(double x) {
        // Lanczos approximation, good enough for stats usage
        double[] p = {
                676.5203681218851,
                -1259.1392167224028,
                771.32342877765313,
                -176.61502916214059,
                12.507343278686905,
                -0.13857109526572012,
                9.9843695780195716e-6,
                1.5056327351493116e-7
        };
        if (x < 0.5) {
            // reflection
            return Math.log(Math.PI) - Math.log(Math.sin(Math.PI * x)) - logGamma(1.0 - x);
        }
        x -= 1.0;
        double a = 0.99999999999980993;
        for (int i = 0; i < p.length; i++) a += p[i] / (x + i + 1.0);
        double t = x + p.length - 0.5;
        return 0.5 * Math.log(2.0 * Math.PI) + (x + 0.5) * Math.log(t) - t + Math.log(a);
    }

    // median of a copy
    private static double medianCopy(double[] x) {
        double[] a = Arrays.copyOf(x, x.length);
        Arrays.sort(a);
        int n = a.length;
        if (n == 0) return Double.NaN;
        if ((n & 1) == 1) return a[n / 2];
        return 0.5 * (a[n / 2 - 1] + a[n / 2]);
    }

    // -------------------- caches --------------------

    private static final class GroupCache {
        private final ConcurrentHashMap<Key, int[][]> cache = new ConcurrentHashMap<>();

        int[][] getGroups(MinimaxHistogramScore owner, int[] zIdx, List<Integer> rows) {
            long rowsSig = rowsSignature(rows);
            Key key = new Key(zIdx, rowsSig, owner.binsPerDim, owner.minBinSize);
            return cache.computeIfAbsent(key, k -> owner.computeGroups(zIdx, rows));
        }

        void clear() { cache.clear(); }

        static long rowsSignature(List<Integer> rows) {
            long h = 1469598103934665603L; // FNV-1a 64
            for (int r : rows) {
                h ^= (r * 0x9E3779B97F4A7C15L);
                h *= 1099511628211L;
            }
            h ^= rows.size();
            h *= 1099511628211L;
            return h;
        }

        private record Key(int[] z, long rowsSig, int binsPerDim, int minBinSize) {
            Key { z = (z == null ? new int[0] : Arrays.copyOf(z, z.length)); }
            @Override public int hashCode() {
                int h = Arrays.hashCode(z);
                h = 31 * h + Long.hashCode(rowsSig);
                h = 31 * h + Integer.hashCode(binsPerDim);
                h = 31 * h + Integer.hashCode(minBinSize);
                return h;
            }
            @Override public boolean equals(Object o) {
                if (!(o instanceof Key k)) return false;
                return rowsSig == k.rowsSig
                        && binsPerDim == k.binsPerDim
                        && minBinSize == k.minBinSize
                        && Arrays.equals(z, k.z);
            }
        }
    }

    private record ScoreKey(
            int target, int[] z, long rowsSig,
            int binsPerDim, int minBinSize,
            double nu, int maxIters, double sMin
    ) {
        ScoreKey {
            z = (z == null ? new int[0] : Arrays.copyOf(z, z.length));
        }

        @Override public int hashCode() {
            int h = Integer.hashCode(target);
            h = 31 * h + Arrays.hashCode(z);
            h = 31 * h + Long.hashCode(rowsSig);
            h = 31 * h + Integer.hashCode(binsPerDim);
            h = 31 * h + Integer.hashCode(minBinSize);
            h = 31 * h + Double.hashCode(nu);
            h = 31 * h + Integer.hashCode(maxIters);
            h = 31 * h + Double.hashCode(sMin);
            return h;
        }

        @Override public boolean equals(Object o) {
            if (!(o instanceof ScoreKey k)) return false;
            return target == k.target
                    && rowsSig == k.rowsSig
                    && binsPerDim == k.binsPerDim
                    && minBinSize == k.minBinSize
                    && Double.compare(nu, k.nu) == 0
                    && maxIters == k.maxIters
                    && Double.compare(sMin, k.sMin) == 0
                    && Arrays.equals(z, k.z);
        }
    }
}