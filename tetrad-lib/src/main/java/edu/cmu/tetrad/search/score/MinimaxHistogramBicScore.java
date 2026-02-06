package edu.cmu.tetrad.search.score;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import org.apache.commons.math3.special.Gamma;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static java.lang.Math.*;

/**
 * <h2>Minimax Histogram BIC Score (Student-t, shared scale)</h2>
 *
 * <p>
 * Local score for Y | Pa(Y) based on a simple minimax-inspired conditioning strategy:
 * discretize Pa(Y) into quantile bins (so Z is approximately constant within each bin),
 * then model Y within each bin using a Student-t location model with a shared scale:
 * </p>
 *
 * <pre>
 *   y_i | bin g  ~  t_nu(mu_g, s)   (nu fixed; mu_g varies by bin; s shared across bins)
 * </pre>
 *
 * <p>
 * Parameters are fit by a fast IRLS procedure (few passes over the data). The local score is
 * a BIC-like objective (Tetrad convention):
 * </p>
 *
 * <pre>
 *   score = 2 * logLik - c * k * log(n)
 * </pre>
 *
 * <p>
 * where k = (#bins used) + 1 (bin means + shared scale), and n is the number of complete rows.
 * </p>
 *
 * <p>
 * This score is intended for continuous data and is designed to be cheap enough for repeated
 * evaluation in FGES/BOSS/GRaSP. Groupings are cached per (parent set, row subset signature, binning knobs).
 * </p>
 */
public final class MinimaxHistogramBicScore implements Score {

    // -------------------- data / indexing --------------------

    private final DataSet data;
    private final List<Node> variables;
    private final Map<String, Integer> indexMap;
    private final int sampleSize;

    // -------------------- knobs --------------------

    /** Student-t degrees of freedom (fixed). */
    private volatile double nu = 7.0;

    /** Bins per conditioning dimension. */
    private volatile int binsPerDim = 4;

    /** Minimum number of points required for a bin to count. */
    private volatile int minBinSize = 5;

    /** IRLS iterations for Student-t fit. */
    private volatile int maxIters = 15;

    /** Convergence tolerance for relative change in scale. */
    private volatile double tol = 1e-6;

    /** Small floor for scale^2 to avoid NaNs. */
    private volatile double scaleFloor = 1e-12;

    /** BIC penalty multiplier c in score = 2L - c*k*log(n). */
    private volatile double bicPenaltyMultiplier = 1.0;

    /** If missing values exist, score is computed on complete-case rows for (Y, parents). */
    private final boolean calculateRowSubsets;

    // -------------------- caches --------------------

    private final GroupCache groupCache = new GroupCache();

    private final AtomicReference<ConcurrentHashMap<Long, Double>> localScoreCacheRef =
            new AtomicReference<>(new ConcurrentHashMap<>());

    public MinimaxHistogramBicScore(DataSet data) {
        if (data == null) throw new NullPointerException("data");
        if (!data.isContinuous()) throw new IllegalArgumentException("MinimaxHistogramBicScore requires continuous DataSet.");
        this.data = data;
        this.variables = Collections.unmodifiableList(new ArrayList<>(data.getVariables()));
        this.indexMap = indexMap(this.variables);
        this.sampleSize = data.getNumRows();
        this.calculateRowSubsets = data.existsMissingValue();
    }

    // -------------------- Score interface --------------------

    @Override
    public double localScore(int y, int... parents) {
        Arrays.sort(parents);

        // key must include knobs that affect the computed score
        long key = cacheKey(y, parents, nu, binsPerDim, minBinSize, maxIters, tol, scaleFloor, bicPenaltyMultiplier);

        final ConcurrentHashMap<Long, Double> cache = localScoreCacheRef.get();
        return cache.computeIfAbsent(key, k -> {
            try {
                // choose rows (complete-case for Y and parents)
                int[] all = concat(y, parents);
                List<Integer> baseRows = listAllRows();
                List<Integer> useRows = calculateRowSubsets ? rowsCompleteFor(all, baseRows) : baseRows;

                int n = useRows.size();
                if (n < max(20, 2 * minBinSize)) return Double.NaN;

                // extract Y in local row index space 0..n-1
                double[] yArr = new double[n];
                for (int i = 0; i < n; i++) yArr[i] = data.getDouble(useRows.get(i), y);

                // no parents => single-bin t location+scale (k=2: mu + s)
                if (parents.length == 0) {
                    Fit fit = fitStudentTSingleBin(yArr, nu, maxIters, tol, scaleFloor);
                    if (!Double.isFinite(fit.logLik)) return Double.NaN;

                    int kParams = 2;
                    return 2.0 * fit.logLik - bicPenaltyMultiplier * kParams * log(n);
                }

                // get groups in local index space (0..n-1)
                int[][] groups = groupCache.getGroups(this, parents, useRows);
                if (groups.length == 0) {
                    // fallback: treat as one bin
                    Fit fit = fitStudentTSingleBin(yArr, nu, maxIters, tol, scaleFloor);
                    if (!Double.isFinite(fit.logLik)) return Double.NaN;

                    int kParams = 2;
                    return 2.0 * fit.logLik - bicPenaltyMultiplier * kParams * log(n);
                }

                // map each obs -> group id
                int G = groups.length;
                int[] binOf = new int[n];
                Arrays.fill(binOf, -1);
                for (int g = 0; g < G; g++) {
                    for (int idx : groups[g]) binOf[idx] = g;
                }

                // some rows might be in bins smaller than minBinSize and got dropped; filter them out
                int nUsed = 0;
                for (int i = 0; i < n; i++) if (binOf[i] >= 0) nUsed++;
                if (nUsed < max(20, 2 * minBinSize)) return Double.NaN;

                double[] yUsed = new double[nUsed];
                int[] binUsed = new int[nUsed];
                for (int i = 0, t = 0; i < n; i++) {
                    int b = binOf[i];
                    if (b >= 0) {
                        yUsed[t] = yArr[i];
                        binUsed[t] = b;
                        t++;
                    }
                }

                Fit fit = fitStudentTGrouped(yUsed, binUsed, G, nu, maxIters, tol, scaleFloor);
                if (!Double.isFinite(fit.logLik)) return Double.NaN;

                int kParams = G + 1; // mu per bin + shared scale
                return 2.0 * fit.logLik - bicPenaltyMultiplier * kParams * log(nUsed);

            } catch (RuntimeException e) {
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
        return "Minimax Histogram BIC Score (Student-t, shared scale)";
    }

    // -------------------- public knobs --------------------

    public void setNu(double nu) {
        if (!(nu > 2.0) || !Double.isFinite(nu)) throw new IllegalArgumentException("nu must be finite and > 2");
        this.nu = nu;
        resetCaches();
    }

    public void setBinsPerDim(int b) {
        this.binsPerDim = max(2, b);
        groupCache.clear();
        resetLocalScoreCache();
    }

    public void setMinBinSize(int m) {
        this.minBinSize = max(3, m);
        groupCache.clear();
        resetLocalScoreCache();
    }

    public void setMaxIters(int iters) {
        this.maxIters = max(1, iters);
        resetLocalScoreCache();
    }

    public void setTol(double tol) {
        this.tol = max(0.0, tol);
        resetLocalScoreCache();
    }

    public void setScaleFloor(double floor) {
        this.scaleFloor = max(0.0, floor);
        resetLocalScoreCache();
    }

    public void setBicPenaltyMultiplier(double c) {
        if (!(c > 0) || !Double.isFinite(c)) throw new IllegalArgumentException("bicPenaltyMultiplier must be finite and > 0");
        this.bicPenaltyMultiplier = c;
        resetLocalScoreCache();
    }

    private void resetCaches() {
        groupCache.clear();
        resetLocalScoreCache();
    }

    private void resetLocalScoreCache() {
        localScoreCacheRef.set(new ConcurrentHashMap<>());
    }

    // ==================== Student-t fitting ====================

    private record Fit(double logLik, double[] mu, double s2) { }

    private static Fit fitStudentTSingleBin(double[] y, double nu, int maxIters, double tol, double s2Floor) {
        // treat as one group (G=1)
        int n = y.length;
        int[] bin = new int[n];
        Arrays.fill(bin, 0);
        return fitStudentTGrouped(y, bin, 1, nu, maxIters, tol, s2Floor);
    }

    /**
     * IRLS for fixed-nu Student-t:
     *  y_i | bin g ~ t_nu(mu_g, s) with shared s across bins.
     *
     * Returns logLik under fitted params.
     */
    private static Fit fitStudentTGrouped(
            double[] y,
            int[] binOf,
            int G,
            double nu,
            int maxIters,
            double tol,
            double s2Floor
    ) {
        int n = y.length;
        if (n == 0) return new Fit(Double.NaN, new double[G], Double.NaN);

        // init mu_g = mean in each bin
        double[] mu = new double[G];
        double[] cnt = new double[G];
        for (int i = 0; i < n; i++) {
            int g = binOf[i];
            mu[g] += y[i];
            cnt[g] += 1.0;
        }
        for (int g = 0; g < G; g++) {
            if (cnt[g] > 0) mu[g] /= cnt[g];
        }

        // init s2 = pooled variance around mu_g
        double s2 = 0.0;
        for (int i = 0; i < n; i++) {
            double r = y[i] - mu[binOf[i]];
            s2 += r * r;
        }
        s2 = max(s2 / max(1.0, n), s2Floor);

        // IRLS loop
        double[] w = new double[n];
        double prevS2 = s2;

        for (int it = 0; it < maxIters; it++) {

            // weights
            double invS2 = 1.0 / s2;
            for (int i = 0; i < n; i++) {
                double r2 = (y[i] - mu[binOf[i]]);
                r2 = r2 * r2;
                double u = r2 * invS2;
                w[i] = (nu + 1.0) / (nu + u);
            }

            // update mu per bin (weighted mean)
            Arrays.fill(mu, 0.0);
            double[] wsum = new double[G];

            for (int i = 0; i < n; i++) {
                int g = binOf[i];
                double wi = w[i];
                mu[g] += wi * y[i];
                wsum[g] += wi;
            }
            for (int g = 0; g < G; g++) {
                if (wsum[g] > 0) mu[g] /= wsum[g];
            }

            // update shared s2 (weighted residual variance)
            double num = 0.0;
            for (int i = 0; i < n; i++) {
                double r = y[i] - mu[binOf[i]];
                num += w[i] * r * r;
            }
            s2 = max(num / max(1.0, n), s2Floor);

            // converge?
            double rel = abs(s2 - prevS2) / max(1e-18, prevS2);
            prevS2 = s2;
            if (rel <= tol) break;
        }

        double logLik = studentTLogLikGrouped(y, binOf, mu, s2, nu);
        return new Fit(logLik, mu, s2);
    }

    private static double studentTLogLikGrouped(double[] y, int[] binOf, double[] mu, double s2, double nu) {
        double s = sqrt(max(1e-300, s2));
        if (!(s > 0) || !Double.isFinite(s)) return Double.NaN;

        // constant part per observation
        double c =
                Gamma.logGamma((nu + 1.0) / 2.0)
                        - Gamma.logGamma(nu / 2.0)
                        - 0.5 * log(nu * PI)
                        - log(s);

        double sum = 0.0;
        double inv = 1.0 / (nu * s2);

        for (int i = 0; i < y.length; i++) {
            double r = y[i] - mu[binOf[i]];
            double t = 1.0 + (r * r) * inv;
            sum += c - 0.5 * (nu + 1.0) * log(t);
        }
        return sum;
    }

    // ==================== grouping ====================

    private int[][] computeGroups(int[] zIdx, List<Integer> useRows) {
        int n = useRows.size();
        int d = zIdx.length;

        // pull Z into local Z[n][d]
        double[][] Z = new double[n][d];
        for (int i = 0; i < n; i++) {
            int r = useRows.get(i);
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

        // mixed-radix bin id
        int[] binId = new int[n];
        int radix = binsPerDim;
        for (int i = 0; i < n; i++) {
            int id = 0;
            int mult = 1;
            for (int j = 0; j < d; j++) {
                int b = upperBound(edges[j], Z[i][j]);
                id += b * mult;
                mult *= radix;
            }
            binId[i] = id;
        }

        // group indices by binId
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

    private static final class GroupCache {
        private final ConcurrentHashMap<Key, int[][]> cache = new ConcurrentHashMap<>();

        int[][] getGroups(MinimaxHistogramBicScore owner, int[] zIdx, List<Integer> rows) {
            long rowsSig = rowsSignature(rows);
            Key key = new Key(zIdx, rowsSig, owner.binsPerDim, owner.minBinSize);
            return cache.computeIfAbsent(key, k -> owner.computeGroups(zIdx, rows));
        }

        void clear() { cache.clear(); }

        private static long rowsSignature(List<Integer> rows) {
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

    // ==================== rows / indexing ====================

    private List<Integer> listAllRows() {
        int n = data.getNumRows();
        ArrayList<Integer> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) out.add(i);
        return out;
    }

    private List<Integer> rowsCompleteFor(int[] vars, List<Integer> baseRows) {
        ArrayList<Integer> out = new ArrayList<>(baseRows.size());
        for (int r : baseRows) {
            boolean ok = true;
            for (int v : vars) {
                double val = data.getDouble(r, v);
                if (Double.isNaN(val)) { ok = false; break; }
            }
            if (ok) out.add(r);
        }
        return out;
    }

    private static int[] concat(int y, int[] parents) {
        int[] all = new int[parents.length + 1];
        all[0] = y;
        System.arraycopy(parents, 0, all, 1, parents.length);
        return all;
    }

    private static Map<String, Integer> indexMap(List<Node> vars) {
        Map<String, Integer> m = new HashMap<>();
        for (int i = 0; i < vars.size(); i++) m.put(vars.get(i).getName(), i);
        return m;
    }

    // -------------------- cache keys --------------------

    private static long cacheKey(int y, int[] parents,
                                 double nu, int binsPerDim, int minBinSize,
                                 int maxIters, double tol, double scaleFloor, double bicC) {
        long h = 1469598103934665603L;
        h = (h ^ y) * 1099511628211L;
        for (int p : parents) h = (h ^ p) * 1099511628211L;

        h = (h ^ Double.doubleToLongBits(nu)) * 1099511628211L;
        h = (h ^ binsPerDim) * 1099511628211L;
        h = (h ^ minBinSize) * 1099511628211L;
        h = (h ^ maxIters) * 1099511628211L;
        h = (h ^ Double.doubleToLongBits(tol)) * 1099511628211L;
        h = (h ^ Double.doubleToLongBits(scaleFloor)) * 1099511628211L;
        h = (h ^ Double.doubleToLongBits(bicC)) * 1099511628211L;

        return h;
    }
}