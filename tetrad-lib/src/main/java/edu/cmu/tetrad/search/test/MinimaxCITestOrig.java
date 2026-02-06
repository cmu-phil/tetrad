package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.search.utils.MinimaxBinningConfig;
import edu.cmu.tetrad.search.utils.MinimaxGroupCache;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <h2>Minimax Conditional Independence Test</h2>
 *
 * Continuous-only version:
 *  - conditions by binning Z (MinimaxGroupCache)
 *  - tests dependence in bins via sum (m-1) r^2
 *  - calibrates by within-bin permutation of Y
 */
public final class MinimaxCITestOrig implements IndependenceTest, RowsSettable {
    private final DataSet data;
    private final List<Node> variables;
    private final Map<String, Integer> indexMap;
    private final ResidualCache cache = new ResidualCache();
    private double alpha;
    private boolean verbose = false;
    private List<Integer> rows = null;

    private RegressorType regressorType = RegressorType.RFF_RIDGE;
    private double ridge = 1e-3;
    private int rffFeatures = 200;     // D
    private double rffSigma = 1.0;     // lengthscale-ish
    private long rffSeed = 1L;

    private final double lastT = Double.NaN;
    private final double lastP = Double.NaN;

    private boolean crossFit = true;
    private int crossFitFolds = 2;
    private long crossFitSeed = 12345L;

    private int permutations = 300;
    private long permSeed = 1L;

    // Binning config + caching for groups
    private MinimaxBinningConfig binningCfg = new MinimaxBinningConfig(4, 3);
    private final MinimaxGroupCache groupCache = new MinimaxGroupCache();

    public MinimaxCITestOrig(DataSet data, double alpha) {
        if (!data.isContinuous()) throw new IllegalArgumentException("MinimaxCITest currently requires continuous DataSet.");
        this.data = data;
        this.variables = Collections.unmodifiableList(new ArrayList<>(data.getVariables()));
        this.indexMap = indexMap(this.variables);
        setAlpha(alpha);
    }

    @Override
    public IndependenceTest indTestSubset(List<Node> vars) {
        MinimaxCITestOrig t = new MinimaxCITestOrig(this.data, this.alpha);
        t.setVerbose(this.verbose);
        t.setRows(this.rows);
//        t.setRegressorType(this.regressorType);
//        t.setRidge(this.ridge);
//        t.setRffFeatures(autoRffFeatures(this.data.getNumRows(), vars.size()));
//        t.setRffSigma(this.rffSigma);
//        t.setRffSeed(this.rffSeed);

        t.setBinningConfig(this.binningCfg);
        t.setPermutations(this.permutations);
        t.setPermSeed(this.permSeed);

        t.setCrossFit(this.crossFit);
        t.setCrossFitFolds(this.crossFitFolds);
        t.setCrossFitSeed(this.crossFitSeed);

        return t;
    }

    private int autoRffFeatures(int n, int p) {
        int d = (int) Math.ceil(4.0 * Math.sqrt(n));
        d = Math.max(d, 200);
        d = Math.min(d, 2000);
        d = Math.min(2000, d + 50 * p);
        return d;
    }

    @Override
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z) {
        double p = getPValue(x, y, z);
        boolean indep = p > alpha;

        IndependenceResult result = new IndependenceResult(
                new IndependenceFact(x, y, z),
                indep,
                p,
                alpha - p
        );

        if (verbose && indep) {
            TetradLogger.getInstance().log(LogUtilsSearch.independenceFactMsg(x, y, z, p));
        }
        return result;
    }

    public double getPValue(Node x, Node y, Set<Node> z) {
        Objects.requireNonNull(x);
        Objects.requireNonNull(y);
        Objects.requireNonNull(z);
        if (x.equals(y)) return 1.0;

        int ix = idx(x);
        int iy = idx(y);
        int[] iz = idxSorted(z);

        List<Integer> baseRows = listRows();
        List<Integer> useRows = rowsCompleteFor(ix, iy, iz, baseRows);
        int n = useRows.size();

        if (n < 20) return 0.0; // conservative guard

        // Extract x,y arrays in the same index space as useRows
        double[] xArr = new double[n];
        double[] yArr = new double[n];
        for (int i = 0; i < n; i++) {
            int r = useRows.get(i);
            xArr[i] = data.getDouble(r, ix);
            yArr[i] = data.getDouble(r, iy);
        }

        // No conditioning: one group = all rows
        if (iz.length == 0) {
            return permuteWithinGroupsPValue(xArr, yArr, new int[][]{range(n)},
                    permutations, permSeed ^ ix ^ (iy * 1315423911L));
        }

        // Conditioning: group by binned Z
        int[][] groups = groupCache.getGroups(data, iz, useRows, binningCfg);

        // If binning produced no usable groups, be conservative (dependent)
        if (groups.length == 0) return 0.0;

        long seed = permSeed ^ ix ^ (iy * 1315423911L) ^ Arrays.hashCode(iz);
        return permuteWithinGroupsPValue(xArr, yArr, groups, permutations, seed);
    }

    @Override
    public List<Node> getVariables() { return variables; }

    @Override
    public double getAlpha() { return alpha; }

    public void setAlpha(double alpha) {
        if (alpha < 0 || alpha > 1) throw new IllegalArgumentException("alpha must be in [0,1]");
        this.alpha = alpha;
    }

    @Override
    public DataSet getData() { return data; }

    @Override
    public boolean isVerbose() { return verbose; }

    public void setVerbose(boolean verbose) { this.verbose = verbose; }

    @Override
    public int getSampleSize() { return data.getNumRows(); }

    public void setCrossFit(boolean crossFit) { this.crossFit = crossFit; cache.clear(); }
    public void setCrossFitSeed(long seed) { this.crossFitSeed = seed; cache.clear(); }
    public void setCrossFitFolds(int k) {
        if (k < 2) throw new IllegalArgumentException("crossFitFolds must be >= 2");
        this.crossFitFolds = k;
        cache.clear();
    }

    public void setPermutations(int B) { this.permutations = Math.max(50, B); }
    public void setPermSeed(long s) { this.permSeed = s; }

    public void setBinsPerDim(int b) {
        this.binningCfg = new MinimaxBinningConfig(Math.max(2, b), binningCfg.minBinSize());
        groupCache.clear();
    }

    public void setMinBinSize(int m) {
        this.binningCfg = new MinimaxBinningConfig(binningCfg.binsPerDim(), Math.max(3, m));
        groupCache.clear();
    }

    public void setBinningConfig(MinimaxBinningConfig cfg) {
        this.binningCfg = Objects.requireNonNull(cfg, "cfg");
        groupCache.clear();
    }

    @Override
    public List<DataSet> getDataSets() { return List.of(data); }

    @Override
    public List<Integer> getRows() { return rows; }

    @Override
    public void setRows(List<Integer> rows) {
        if (rows == null) {
            this.rows = null;
            cache.clear();
            return;
        }

        for (int i = 0; i < rows.size(); i++) {
            Integer r = rows.get(i);
            if (r == null) throw new NullPointerException("Row " + i + " is null.");
            if (r < 0) throw new IllegalArgumentException("Row " + i + " is negative.");
            if (r >= data.getNumRows()) throw new IllegalArgumentException("Row " + i + " out of bounds: " + r);
        }

        this.rows = new ArrayList<>(rows);
        cache.clear();
    }

    // ======================= core stat + permutation =======================

    private static double statCorrSq(double[] x, double[] y, int[][] groups) {
        double T = 0.0;
        for (int[] g : groups) {
            int m = g.length;
            double mx = 0, my = 0;
            for (int idx : g) { mx += x[idx]; my += y[idx]; }
            mx /= m; my /= m;

            double sxx = 0, syy = 0, sxy = 0;
            for (int idx : g) {
                double dx = x[idx] - mx;
                double dy = y[idx] - my;
                sxx += dx * dx;
                syy += dy * dy;
                sxy += dx * dy;
            }
            if (sxx <= 0 || syy <= 0) continue;
            double r = sxy / Math.sqrt(sxx * syy);
            T += (m - 1.0) * r * r;
        }
        return T;
    }

    private static double permuteWithinGroupsPValue(
            double[] x, double[] y, int[][] groups, int B, long seed
    ) {
        double obs = statCorrSq(x, y, groups);

        SplittableRandom rng = new SplittableRandom(seed);
        double[] yPerm = Arrays.copyOf(y, y.length);

        int ge = 0;
        for (int b = 0; b < B; b++) {
            System.arraycopy(y, 0, yPerm, 0, y.length);

            // shuffle y within each group
            for (int[] g : groups) {
                for (int i = g.length - 1; i > 0; i--) {
                    int j = rng.nextInt(i + 1);
                    int ii = g[i], jj = g[j];
                    double tmp = yPerm[ii];
                    yPerm[ii] = yPerm[jj];
                    yPerm[jj] = tmp;
                }
            }

            double t = statCorrSq(x, yPerm, groups);
            if (t >= obs) ge++;
        }

        // smoothing helps avoid p=0
        return (ge + 1.0) / (B + 1.0);
    }

    // ======================= indexing / rows =======================

    private static int[] range(int n) {
        int[] r = new int[n];
        for (int i = 0; i < n; i++) r[i] = i;
        return r;
    }

    private List<Integer> rowsCompleteFor(int ix, int iy, int[] iz, List<Integer> baseRows) {
        List<Integer> out = new ArrayList<>(baseRows.size());
        for (int r : baseRows) {
            double vx = data.getDouble(r, ix);
            double vy = data.getDouble(r, iy);
            if (Double.isNaN(vx) || Double.isNaN(vy)) continue;

            boolean ok = true;
            for (int j : iz) {
                double vz = data.getDouble(r, j);
                if (Double.isNaN(vz)) { ok = false; break; }
            }
            if (ok) out.add(r);
        }
        return out;
    }

    private int idx(Node v) {
        Integer i = indexMap.get(v.getName());
        if (i == null) throw new IllegalArgumentException("Unknown variable: " + v);
        return i;
    }

    private int[] idxSorted(Set<Node> z) {
        int[] out = new int[z.size()];
        int k = 0;
        for (Node v : z) out[k++] = idx(v);
        Arrays.sort(out);
        return out;
    }

    private List<Integer> listRows() {
        if (rows != null) return rows;
        int n = data.getNumRows();
        List<Integer> r = new ArrayList<>(n);
        for (int i = 0; i < n; i++) r.add(i);
        return r;
    }

    private static Map<String, Integer> indexMap(List<Node> vars) {
        Map<String, Integer> m = new HashMap<>();
        for (int i = 0; i < vars.size(); i++) m.put(vars.get(i).getName(), i);
        return m;
    }

    // ==================== regression machinery (kept as-is) ====================

    public enum RegressorType {LINEAR_RIDGE, RFF_RIDGE}

    private static final class ResidualCache {
        private final ConcurrentHashMap<Key, double[]> cache = new ConcurrentHashMap<>();
        void clear() { cache.clear(); }

        private record Key(int target, int[] z, long rowsSig,
                           boolean crossFit, int kFolds, long cfSeed,
                           RegressorType regType, double ridge,
                           int rffFeatures, double rffSigma, long rffSeed) {
            Key { z = (z == null ? new int[0] : Arrays.copyOf(z, z.length)); }
        }
    }

    // (Your fitAndResidualize / regressors can live below if you still want them.
    //  They weren’t used by getPValue in this particular minimax implementation.)
}