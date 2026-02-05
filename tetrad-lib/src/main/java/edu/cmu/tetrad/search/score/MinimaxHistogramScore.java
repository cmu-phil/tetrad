package edu.cmu.tetrad.search.score;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.EffectiveSampleSizeSettable;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static java.lang.Math.*;

/**
 * <p><b>Minimax-Histogram Score (piecewise-constant conditional mean, continuous)</b></p>
 *
 * <p>
 * Local score for Y given Pa(Y)=Z using a minimax-style histogram (quantile grid) approximation:
 * partition the parent space into bins where Z is approximately constant, then fit a constant mean
 * for Y within each bin. The score is a Gaussian pseudo-log-likelihood with a BIC-like penalty
 * for bin complexity.
 * </p>
 *
 * <p><b>Model (pseudo)</b>:</p>
 * <pre>
 * For each bin g:  Y | (Z in bin g) ~ N(mu_g, sigma_g^2)
 * </pre>
 *
 * <p><b>Local score (up to additive constants)</b>:</p>
 * <pre>
 * score(Y | Z) = -0.5 * sum_g m_g * log(s_g^2)  - 0.5 * k * log(n)
 * </pre>
 * where s_g^2 is the within-bin sample variance of Y, m_g is bin size, and k is the number of
 * free parameters (by default 2 per used bin: mean + variance).
 *
 * <p>
 * Practical: this is fast, robust, and “minimax flavored” (histogram estimator is classical for
 * Hölder-smooth regression), but it’s a pseudo-likelihood score—useful for search even when the
 * true errors are not Gaussian.
 * </p>
 */
public final class MinimaxHistogramScore implements Score, EffectiveSampleSizeSettable {

    // -------------------- data --------------------
    private final DataSet data;
    private final List<Node> variables;
    private final int sampleSize;

    // Missing handling: if any missing exists, score each parent set on complete-case rows for {Y ∪ Pa(Y)}.
    private final boolean calculateRowSubsets;

    // effective sample size
    private volatile int nEff;

    // -------------------- binning knobs --------------------
    private volatile int binsPerDim = 4;     // >=2
    private volatile int minBinSize = 3;     // >=3

    // penalty knobs
    private volatile double penaltyMultiplier = 1.0; // 1.0 = BIC-ish
    private volatile boolean countVarianceParam = true; // if false: 1 param per bin (mean only), else 2

    // caches
    private final AtomicReference<ConcurrentHashMap<Long, Double>> localScoreCacheRef =
            new AtomicReference<>(new ConcurrentHashMap<>());

    private final GroupCache groupCache = new GroupCache();

    public MinimaxHistogramScore(DataSet data) {
        if (data == null) throw new NullPointerException("data");
        if (!data.isContinuous()) throw new IllegalArgumentException("MinimaxHistogramScore requires continuous DataSet.");

        this.data = data;
        this.variables = data.getVariables();
        this.sampleSize = data.getNumRows();
        this.calculateRowSubsets = data.existsMissingValue();
        setEffectiveSampleSize(-1);
    }

    // -------------------- Score interface --------------------

    @Override
    public double localScoreDiff(int x, int y, int[] z) {
        return localScore(y, append(z, x)) - localScore(y, z);
    }

    @Override
    public double localScore(int target, int... parents) {
        Arrays.sort(parents);

        long key = cacheKey(target, parents,
                binsPerDim, minBinSize,
                penaltyMultiplier, countVarianceParam,
                nEff);

        final ConcurrentHashMap<Long, Double> cache = localScoreCacheRef.get();
        return cache.computeIfAbsent(key, k -> {
            try {
                int[] all = concat(target, parents);

                // row selection (complete-case for target+parents), if missing exists
                int[] rows = calculateRowSubsets ? validRows(all) : null;
                int n = (rows == null) ? nEff : rows.length;
                if (n < 10) return Double.NaN;

                // extract Y (in the index-space of the chosen rows)
                double[] y = extract1D(target, rows, n);

                // no parents => single bin over all rows
                if (parents.length == 0) {
                    double v = sampleVariance(y);
                    if (!(v > 0) || !Double.isFinite(v)) v = 1e-12;
                    // score = -0.5 * n * log(v)  - 0.5 * k log n
                    int binsUsed = 1;
                    int kParams = binsUsed * (countVarianceParam ? 2 : 1);
                    return -0.5 * n * log(v) - 0.5 * penaltyMultiplier * kParams * log(n);
                }

                // build groups for Z = parents
                int[][] groups = groupCache.getGroups(this, parents, rows, n);

                // if no usable bins, be conservative
                if (groups.length == 0) return Double.NaN;

                double ll = 0.0;
                int binsUsed = 0;

                for (int[] g : groups) {
                    int m = g.length;
                    if (m < minBinSize) continue;

                    // variance within bin
                    double v = sampleVarianceOnIndexSet(y, g);
                    if (!(v > 0) || !Double.isFinite(v)) v = 1e-12;

                    ll += -0.5 * m * log(v);
                    binsUsed++;
                }

                if (binsUsed == 0) return Double.NaN;

                int kParams = binsUsed * (countVarianceParam ? 2 : 1);
                double pen = 0.5 * penaltyMultiplier * kParams * log(n);

                return ll - pen;

            } catch (RuntimeException e) {
                TetradLogger.getInstance().log(e.getMessage());
                return Double.NaN;
            }
        });
    }

    @Override
    public List<Node> getVariables() {
        return new ArrayList<>(variables);
    }

    @Override
    public int getSampleSize() {
        return data.getNumRows();
    }

    @Override
    public int getMaxDegree() {
        return (int) Math.ceil(Math.log(Math.max(5, nEff)));
    }

    @Override
    public boolean determines(List<Node> z, Node y) {
        int i = variables.indexOf(y);
        int[] parents = new int[z.size()];
        for (int t = 0; t < z.size(); t++) parents[t] = variables.indexOf(z.get(t));
        double s = localScore(i, parents);
        return Double.isNaN(s) || Double.isInfinite(s);
    }

    @Override
    public boolean isEffectEdge(double bump) {
        return bump > 0;
    }

    public DataModel getDataModel() {
        return data;
    }

    @Override
    public int getEffectiveSampleSize() {
        return nEff;
    }

    @Override
    public void setEffectiveSampleSize(int nEff) {
        this.nEff = (nEff < 0) ? this.sampleSize : nEff;
        resetCache();
    }

    @Override
    public String toString() {
        return "Minimax Histogram Score (piecewise-constant, continuous)";
    }

    // -------------------- tuning knobs --------------------

    public void setBinsPerDim(int b) {
        this.binsPerDim = Math.max(2, b);
        resetCache();
        groupCache.clear();
    }

    public void setMinBinSize(int m) {
        this.minBinSize = Math.max(3, m);
        resetCache();
        groupCache.clear();
    }

    /** 1.0 ~ BIC-ish. Larger => stronger penalty => sparser graphs. */
    public void setPenaltyMultiplier(double c) {
        if (!(c > 0) || !Double.isFinite(c)) throw new IllegalArgumentException("penaltyMultiplier must be > 0");
        this.penaltyMultiplier = c;
        resetCache();
    }

    /** If false: penalize 1 param/bin (mean). If true: 2 params/bin (mean+variance). */
    public void setCountVarianceParam(boolean on) {
        this.countVarianceParam = on;
        resetCache();
    }

    // -------------------- internals --------------------

    private void resetCache() {
        localScoreCacheRef.set(new ConcurrentHashMap<>());
    }

    private static long cacheKey(int target, int[] parents,
                                 int binsPerDim, int minBinSize,
                                 double penaltyMultiplier, boolean countVar,
                                 int nEff) {
        long h = 1469598103934665603L;
        h = (h ^ target) * 1099511628211L;
        for (int p : parents) h = (h ^ p) * 1099511628211L;
        h = (h ^ binsPerDim) * 1099511628211L;
        h = (h ^ minBinSize) * 1099511628211L;
        h = (h ^ Double.doubleToLongBits(penaltyMultiplier)) * 1099511628211L;
        h = (h ^ (countVar ? 1L : 0L)) * 1099511628211L;
        h = (h ^ nEff) * 1099511628211L;
        return h;
    }

    private int[] validRows(int[] vars) {
        int n = sampleSize;
        int[] tmp = new int[n];
        int m = 0;

        outer:
        for (int r = 0; r < n; r++) {
            for (int v : vars) {
                double val = data.getDouble(r, v);
                if (Double.isNaN(val)) continue outer;
            }
            tmp[m++] = r;
        }
        return Arrays.copyOf(tmp, m);
    }

    private double[] extract1D(int varIndex, int[] rows, int n) {
        double[] x = new double[n];
        if (rows == null) {
            for (int r = 0; r < n; r++) x[r] = data.getDouble(r, varIndex);
        } else {
            for (int i = 0; i < n; i++) x[i] = data.getDouble(rows[i], varIndex);
        }
        return x;
    }

    private static double sampleVariance(double[] y) {
        int n = y.length;
        double mean = 0.0;
        for (double v : y) mean += v;
        mean /= n;

        double ss = 0.0;
        for (double v : y) {
            double d = v - mean;
            ss += d * d;
        }
        return ss / Math.max(1, (n - 1));
    }

    private static double sampleVarianceOnIndexSet(double[] y, int[] idx) {
        int n = idx.length;
        double mean = 0.0;
        for (int i : idx) mean += y[i];
        mean /= n;

        double ss = 0.0;
        for (int i : idx) {
            double d = y[i] - mean;
            ss += d * d;
        }
        return ss / Math.max(1, (n - 1));
    }

    public int[] append(int[] z, int x) {
        int[] out = Arrays.copyOf(z, z.length + 1);
        out[z.length] = x;
        return out;
    }

    private static int[] concat(int i, int[] parents) {
        int[] all = new int[parents.length + 1];
        all[0] = i;
        System.arraycopy(parents, 0, all, 1, parents.length);
        return all;
    }

    // -------------------- group cache + grouping (quantile grid) --------------------

    private static final class GroupCache {
        private final ConcurrentHashMap<Key, int[][]> cache = new ConcurrentHashMap<>();

        int[][] getGroups(MinimaxHistogramScore owner, int[] zIdx, int[] rows, int n) {
            long rowsSig = rowsSignature(rows, n);
            Key key = new Key(zIdx, rowsSig, owner.binsPerDim, owner.minBinSize);
            return cache.computeIfAbsent(key, k -> computeGroups(owner.data, zIdx, rows, n, owner.binsPerDim, owner.minBinSize));
        }

        void clear() { cache.clear(); }

        private static long rowsSignature(int[] rows, int n) {
            if (rows == null) return 0xCAFEBABEL ^ n;
            long h = 1469598103934665603L;
            for (int r : rows) {
                h ^= (r * 0x9E3779B97F4A7C15L);
                h *= 1099511628211L;
            }
            h ^= rows.length;
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

        private static int[][] computeGroups(
                DataSet data, int[] zIdx, int[] rows, int n,
                int binsPerDim, int minBinSize
        ) {
            int d = zIdx.length;

            // Pull Z into local array Z[n][d]
            double[][] Z = new double[n][d];
            for (int i = 0; i < n; i++) {
                int r = (rows == null) ? i : rows[i];
                for (int j = 0; j < d; j++) Z[i][j] = data.getDouble(r, zIdx[j]);
            }

            // quantile edges per dim
            double[][] edges = new double[d][binsPerDim - 1];
            for (int j = 0; j < d; j++) {
                double[] col = new double[n];
                for (int i = 0; i < n; i++) col[i] = Z[i][j];
                Arrays.sort(col);
                for (int k = 1; k < binsPerDim; k++) {
                    int q = (int) floor((k * (n - 1.0)) / binsPerDim);
                    edges[j][k - 1] = col[q];
                }
            }

            int[] binId = new int[n];
            int radix = binsPerDim;

            for (int i = 0; i < n; i++) {
                int id = 0;
                int mult = 1;
                for (int j = 0; j < d; j++) {
                    double v = Z[i][j];
                    int b = upperBound(edges[j], v); // 0..binsPerDim-1
                    id += b * mult;
                    mult *= radix;
                }
                binId[i] = id;
            }

            Map<Integer, IntArrayList> map = new HashMap<>();
            for (int i = 0; i < n; i++) {
                map.computeIfAbsent(binId[i], kk -> new IntArrayList()).add(i);
            }

            ArrayList<int[]> groups = new ArrayList<>();
            for (IntArrayList g : map.values()) {
                if (g.size() >= minBinSize) groups.add(g.toArray());
            }

            return groups.toArray(new int[0][]);
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

        private static final class IntArrayList {
            private int[] a = new int[16];
            private int n = 0;
            void add(int v) { if (n == a.length) a = Arrays.copyOf(a, a.length * 2); a[n++] = v; }
            int size() { return n; }
            int[] toArray() { return Arrays.copyOf(a, n); }
        }
    }
}