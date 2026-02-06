package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.search.utils.MinimaxBinningConfig;
import edu.cmu.tetrad.search.utils.MinimaxGroupCache;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.Math.log;

/**
 * Minimax Conditional Independence Test (Mixed Support)
 *
 * Supports:
 *   - continuous/continuous
 *   - discrete/discrete
 *   - discrete/continuous (either orientation)
 *
 * Conditioning set Z may be mixed; grouping is done by:
 *   - exact matching for discrete Z variables
 *   - minimax binning for continuous Z variables (via MinimaxGroupCache)
 *
 * Significance is calibrated by within-group permutation of Y.
 */
public final class MinimaxCITestMixed implements IndependenceTest, RowsSettable {

    private final DataSet data;
    private final List<Node> variables;
    private final Map<String, Integer> indexMap;

    private double alpha;
    private boolean verbose = false;
    private List<Integer> rows = null;

    // knobs
    private int permutations = 300;
    private long permSeed = 1L;

    // binning config for continuous Z variables
    private MinimaxBinningConfig binningCfg = new MinimaxBinningConfig(4, 3);
    private final MinimaxGroupCache groupCache = new MinimaxGroupCache();

    // --- (You had these; keep them around even if not used by getPValue right now)
    private final ResidualCache cache = new ResidualCache();
    private RegressorType regressorType = RegressorType.RFF_RIDGE;
    private double ridge = 1e-3;
    private int rffFeatures = 400;
    private double rffSigma = 1.0;
    private long rffSeed = 1L;
    private final double lastT = Double.NaN;
    private final double lastP = Double.NaN;
    private boolean crossFit = false;
    private int crossFitFolds = 2;
    private long crossFitSeed = 12345L;

    public MinimaxCITestMixed(DataSet data, double alpha) {
        // Allow continuous OR mixed. Discrete-only is also allowed (disc-disc tests will work).
        this.data = Objects.requireNonNull(data, "data");
        this.variables = Collections.unmodifiableList(new ArrayList<>(data.getVariables()));
        this.indexMap = indexMap(this.variables);
        setAlpha(alpha);
    }

    @Override
    public IndependenceTest indTestSubset(List<Node> vars) {
        MinimaxCITestMixed t = new MinimaxCITestMixed(this.data, this.alpha);
        t.setVerbose(this.verbose);
        t.setRows(this.rows);
        t.setPermutations(this.permutations);
        t.setPermSeed(this.permSeed);
        t.setBinningConfig(this.binningCfg);
        return t;
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

        if (n < 20) return 0.0; // conservative

        boolean xDisc = isDiscrete(ix);
        boolean yDisc = isDiscrete(iy);

        // Prepare X and Y arrays in the index space [0..n-1]
        double[] xCont = null, yCont = null;
        int[] xCat = null, yCat = null;

        if (!xDisc) {
            xCont = new double[n];
            for (int i = 0; i < n; i++) xCont[i] = data.getDouble(useRows.get(i), ix);
        } else {
            xCat = new int[n];
            for (int i = 0; i < n; i++) xCat[i] = data.getInt(useRows.get(i), ix);
        }

        if (!yDisc) {
            yCont = new double[n];
            for (int i = 0; i < n; i++) yCont[i] = data.getDouble(useRows.get(i), iy);
        } else {
            yCat = new int[n];
            for (int i = 0; i < n; i++) yCat[i] = data.getInt(useRows.get(i), iy);
        }

        // Build conditioning groups
        int[][] groups;
        if (iz.length == 0) {
            groups = new int[][]{range(n)};
        } else {
            groups = getGroupsMixed(iz, useRows); // mixed-discrete strata + binned continuous
            if (groups.length == 0) return 0.0; // conservative dependent
        }

        long seed = permSeed ^ ix ^ (iy * 1315423911L) ^ Arrays.hashCode(iz);

        if (!xDisc && !yDisc) {
            return permuteWithinGroupsPValue_ContCont(xCont, yCont, groups, permutations, seed);
        } else if (xDisc && yDisc) {
            int xLevels = numLevels(ix);
            int yLevels = numLevels(iy);
            return permuteWithinGroupsPValue_DiscDisc(xCat, yCat, xLevels, yLevels, groups, permutations, seed);
        } else {
            // disc-cont (either direction). We'll permute the CONTINUOUS Y variable by default,
            // but since we're doing within-group permutation, either choice is fine. We'll keep:
            // - if Y is continuous: permute Y
            // - else (Y discrete, X continuous): permute Y discrete and compute eta^2 the other way
            if (!yDisc) {
                int xLevels = numLevels(ix); // X is discrete
                return permuteWithinGroupsPValue_DiscCont(xCat, yCont, xLevels, groups, permutations, seed);
            } else {
                int yLevels = numLevels(iy); // Y is discrete
                // swap roles: treat Y(discrete) as the factor for X(continuous)
                return permuteWithinGroupsPValue_DiscCont(yCat, xCont, yLevels, groups, permutations, seed);
            }
        }
    }

    // ---------------------------------------------------------------------
    // Grouping for mixed Z:
    //   - discrete Z => exact strata (tuple of discrete values)
    //   - continuous Z => bin using MinimaxGroupCache over continuous Z indices
    //
    // We combine by taking intersections: (discStratumKey, contBinId) -> group rows.
    // ---------------------------------------------------------------------
    private int[][] getGroupsMixed(int[] iz, List<Integer> useRowsGlobal) {
        // Split Z indices into discrete vs continuous
        IntArrayList zDisc = new IntArrayList();
        IntArrayList zCont = new IntArrayList();
        for (int j : iz) {
            if (isDiscrete(j)) zDisc.add(j);
            else zCont.add(j);
        }
        int[] zDiscIdx = zDisc.toArray();
        int[] zContIdx = zCont.toArray();

        // If there are continuous Zs, get their binned groups in the LOCAL index space [0..n-1]
        // NOTE: MinimaxGroupCache expects original DataSet and row indices (global). It returns groups
        // in LOCAL indices (aligned to the x/y arrays we build from useRows).
        int[][] contGroups;
        int n = useRowsGlobal.size();
        if (zContIdx.length == 0) {
            contGroups = new int[][]{range(n)};
        } else {
            contGroups = groupCache.getGroups(data, zContIdx, useRowsGlobal, binningCfg);
            if (contGroups.length == 0) return new int[0][];
        }

        // Map each local row -> contBinId
        int[] contBinOf = new int[n];
        Arrays.fill(contBinOf, -1);
        for (int b = 0; b < contGroups.length; b++) {
            for (int local : contGroups[b]) contBinOf[local] = b;
        }

        // If there are no discrete Zs, we’re done.
        if (zDiscIdx.length == 0) {
            // Filter out any locals not assigned to a bin (should not happen, but be safe)
            return filterAssigned(contGroups, contBinOf);
        }

        // Combine: (discKey, contBin) -> IntArrayList of locals
        Map<DiscKey, IntArrayList> map = new HashMap<>();

        for (int local = 0; local < n; local++) {
            int bin = contBinOf[local];
            if (bin < 0) continue;

            int globalRow = useRowsGlobal.get(local);
            int[] vals = new int[zDiscIdx.length];
            for (int t = 0; t < zDiscIdx.length; t++) vals[t] = data.getInt(globalRow, zDiscIdx[t]);

            DiscKey key = new DiscKey(vals, bin);
            IntArrayList list = map.computeIfAbsent(key, k -> new IntArrayList());
            list.add(local);
        }

        // Convert and enforce minBinSize (from cfg)
        int minSize = Math.max(2, binningCfg.minBinSize());
        List<int[]> groups = new ArrayList<>();
        for (IntArrayList lst : map.values()) {
            if (lst.size() >= minSize) groups.add(lst.toArray());
        }

        // If everything got too small, be conservative
        if (groups.isEmpty()) return new int[0][];

        return groups.toArray(new int[0][]);
    }

    private static int[][] filterAssigned(int[][] groups, int[] binOf) {
        List<int[]> out = new ArrayList<>(groups.length);
        for (int[] g : groups) {
            IntArrayList keep = new IntArrayList();
            for (int i : g) if (binOf[i] >= 0) keep.add(i);
            if (keep.size() >= 2) out.add(keep.toArray());
        }
        return out.toArray(new int[0][]);
    }

    private record DiscKey(int[] vals, int bin) {
        DiscKey {
            vals = Arrays.copyOf(vals, vals.length);
        }
        @Override public int hashCode() {
            return 31 * Arrays.hashCode(vals) + Integer.hashCode(bin);
        }
        @Override public boolean equals(Object o) {
            if (!(o instanceof DiscKey k)) return false;
            return bin == k.bin && Arrays.equals(vals, k.vals);
        }
    }

    // ---------------------------------------------------------------------
    // Stats + Permutation
    // ---------------------------------------------------------------------

    // cont-cont: sum (m-1) r^2 across groups (your original)
    private static double stat_ContCont(double[] x, double[] y, int[][] groups) {
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

    private static double permuteWithinGroupsPValue_ContCont(
            double[] x, double[] y, int[][] groups, int B, long seed
    ) {
        double obs = stat_ContCont(x, y, groups);

        SplittableRandom rng = new SplittableRandom(seed);
        double[] yPerm = Arrays.copyOf(y, y.length);

        int ge = 0;
        for (int b = 0; b < B; b++) {
            System.arraycopy(y, 0, yPerm, 0, y.length);

            for (int[] g : groups) shuffleInPlace(yPerm, g, rng);

            double t = stat_ContCont(x, yPerm, groups);
            if (t >= obs) ge++;
        }
        return (ge + 1.0) / (B + 1.0);
    }

    // disc-disc: G-test (likelihood ratio chi-square) summed across groups
    private static double stat_DiscDisc_G(
            int[] x, int[] y, int xLevels, int yLevels, int[][] groups
    ) {
        double T = 0.0;

        for (int[] g : groups) {
            int m = g.length;
            if (m < 2) continue;

            int[][] nij = new int[xLevels][yLevels];
            int[] ni = new int[xLevels];
            int[] nj = new int[yLevels];

            for (int idx : g) {
                int xi = x[idx];
                int yj = y[idx];
                if (xi < 0 || xi >= xLevels) continue;
                if (yj < 0 || yj >= yLevels) continue;
                nij[xi][yj]++;
                ni[xi]++;
                nj[yj]++;
            }

            int n = 0;
            for (int v : ni) n += v;
            if (n < 2) continue;

            // G = 2 * sum_{ij} nij * log( nij * n / (ni*nj) )
            double G = 0.0;
            for (int i = 0; i < xLevels; i++) {
                if (ni[i] == 0) continue;
                for (int j = 0; j < yLevels; j++) {
                    int c = nij[i][j];
                    if (c == 0) continue;
                    if (nj[j] == 0) continue;
                    double expectedDen = (double) ni[i] * (double) nj[j];
                    double val = (double) c * (double) n / expectedDen;
                    G += 2.0 * c * log(val);
                }
            }
            T += G;
        }

        return T;
    }

    private static double permuteWithinGroupsPValue_DiscDisc(
            int[] x, int[] y, int xLevels, int yLevels, int[][] groups, int B, long seed
    ) {
        double obs = stat_DiscDisc_G(x, y, xLevels, yLevels, groups);

        SplittableRandom rng = new SplittableRandom(seed);
        int[] yPerm = Arrays.copyOf(y, y.length);

        int ge = 0;
        for (int b = 0; b < B; b++) {
            System.arraycopy(y, 0, yPerm, 0, y.length);

            for (int[] g : groups) shuffleInPlace(yPerm, g, rng);

            double t = stat_DiscDisc_G(x, yPerm, xLevels, yLevels, groups);
            if (t >= obs) ge++;
        }
        return (ge + 1.0) / (B + 1.0);
    }

    // disc-cont: correlation ratio (eta^2) style statistic within groups, summed
    private static double stat_DiscCont_Eta(
            int[] xCat, double[] y, int xLevels, int[][] groups
    ) {
        double T = 0.0;

        for (int[] g : groups) {
            int m = g.length;
            if (m < 3) continue;

            double overallMean = 0.0;
            for (int idx : g) overallMean += y[idx];
            overallMean /= m;

            double totalSS = 0.0;
            for (int idx : g) {
                double d = y[idx] - overallMean;
                totalSS += d * d;
            }
            if (totalSS <= 0) continue;

            double[] sum = new double[xLevels];
            int[] cnt = new int[xLevels];

            for (int idx : g) {
                int k = xCat[idx];
                if (k < 0 || k >= xLevels) continue;
                sum[k] += y[idx];
                cnt[k]++;
            }

            double betweenSS = 0.0;
            for (int k = 0; k < xLevels; k++) {
                if (cnt[k] == 0) continue;
                double mk = sum[k] / cnt[k];
                double d = mk - overallMean;
                betweenSS += cnt[k] * d * d;
            }

            double eta2 = betweenSS / totalSS; // in [0,1]
            // scale similarly to (m-1) r^2:
            T += (m - 1.0) * eta2;
        }

        return T;
    }

    private static double permuteWithinGroupsPValue_DiscCont(
            int[] xCat, double[] y, int xLevels, int[][] groups, int B, long seed
    ) {
        double obs = stat_DiscCont_Eta(xCat, y, xLevels, groups);

        SplittableRandom rng = new SplittableRandom(seed);
        double[] yPerm = Arrays.copyOf(y, y.length);

        int ge = 0;
        for (int b = 0; b < B; b++) {
            System.arraycopy(y, 0, yPerm, 0, y.length);

            for (int[] g : groups) shuffleInPlace(yPerm, g, rng);

            double t = stat_DiscCont_Eta(xCat, yPerm, xLevels, groups);
            if (t >= obs) ge++;
        }

        return (ge + 1.0) / (B + 1.0);
    }

    private static void shuffleInPlace(double[] a, int[] idx, SplittableRandom rng) {
        for (int i = idx.length - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int ii = idx[i], jj = idx[j];
            double tmp = a[ii];
            a[ii] = a[jj];
            a[jj] = tmp;
        }
    }

    private static void shuffleInPlace(int[] a, int[] idx, SplittableRandom rng) {
        for (int i = idx.length - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int ii = idx[i], jj = idx[j];
            int tmp = a[ii];
            a[ii] = a[jj];
            a[jj] = tmp;
        }
    }

    // ---------------------------------------------------------------------
    // Simple utilities
    // ---------------------------------------------------------------------

    @Override
    public List<Node> getVariables() {
        return variables;
    }

    @Override
    public double getAlpha() {
        return alpha;
    }

    public void setAlpha(double alpha) {
        if (alpha < 0 || alpha > 1) throw new IllegalArgumentException("alpha must be in [0,1]");
        this.alpha = alpha;
    }

    @Override
    public DataSet getData() {
        return data;
    }

    @Override
    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    @Override
    public int getSampleSize() {
        return data.getNumRows();
    }

    public void setPermutations(int B) {
        this.permutations = Math.max(50, B);
    }

    public void setPermSeed(long s) {
        this.permSeed = s;
    }

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
    public List<DataSet> getDataSets() {
        return List.of(data);
    }

    @Override
    public List<Integer> getRows() {
        return rows;
    }

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

    private List<Integer> listRows() {
        if (rows != null) return rows;
        int n = data.getNumRows();
        List<Integer> r = new ArrayList<>(n);
        for (int i = 0; i < n; i++) r.add(i);
        return r;
    }

    private List<Integer> rowsCompleteFor(int ix, int iy, int[] iz, List<Integer> baseRows) {
        List<Integer> out = new ArrayList<>(baseRows.size());
        boolean xDisc = isDiscrete(ix);
        boolean yDisc = isDiscrete(iy);

        for (int r : baseRows) {
            // missing for X/Y
            if (!xDisc) {
                double vx = data.getDouble(r, ix);
                if (Double.isNaN(vx)) continue;
            }
            if (!yDisc) {
                double vy = data.getDouble(r, iy);
                if (Double.isNaN(vy)) continue;
            }

            boolean ok = true;
            for (int j : iz) {
                if (!isDiscrete(j)) {
                    double vz = data.getDouble(r, j);
                    if (Double.isNaN(vz)) { ok = false; break; }
                } else {
                    // discrete missing is usually -99 in Tetrad datasets; if you use another missing code,
                    // adjust this check.
                    int v = data.getInt(r, j);
                    if (v == DiscreteVariable.MISSING_VALUE) { ok = false; break; }
                }
            }
            if (ok) out.add(r);
        }
        return out;
    }

    private boolean isDiscrete(int col) {
        Node v = data.getVariable(col);
        return v instanceof DiscreteVariable;
    }

    private int numLevels(int col) {
        Node v = data.getVariable(col);
        if (v instanceof DiscreteVariable dv) return dv.getNumCategories();
        // fallback: treat as 2-level if something odd happened
        return 2;
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

    private static int[] range(int n) {
        int[] r = new int[n];
        for (int i = 0; i < n; i++) r[i] = i;
        return r;
    }

    private static final class IntArrayList {
        private int[] a = new int[16];
        private int n = 0;
        void add(int v) { if (n == a.length) a = Arrays.copyOf(a, a.length * 2); a[n++] = v; }
        int size() { return n; }
        int[] toArray() { return Arrays.copyOf(a, n); }
    }

    private static Map<String, Integer> indexMap(List<Node> vars) {
        Map<String, Integer> m = new HashMap<>();
        for (int i = 0; i < vars.size(); i++) m.put(vars.get(i).getName(), i);
        return m;
    }

    // ---------------------------------------------------------------------
    // Keep your existing regressor machinery below (unchanged).
    // (Not used by getPValue in this version, but you may want it later.)
    // ---------------------------------------------------------------------

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
}