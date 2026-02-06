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

/**
 * Minimax-style CI test with:
 *  - continuous Z: minimax binning
 *  - discrete Z: exact stratification
 *  - within-group permutation of Y
 *
 * Supports mixed discrete/continuous variables for X, Y, and Z.
 */
public final class MinimaxCITest2 implements IndependenceTest, RowsSettable {

    private final DataSet data;
    private final List<Node> variables;
    private final Map<String, Integer> indexMap;

    private double alpha;
    private boolean verbose = false;
    private List<Integer> rows = null;

    private int permutations = 300;
    private long permSeed = 1L;

    // Binning config + caching for continuous Z bins
    private MinimaxBinningConfig binningCfg = new MinimaxBinningConfig(4, 3);
    private final MinimaxGroupCache groupCache = new MinimaxGroupCache();

    public MinimaxCITest2(DataSet data, double alpha) {
        // Now allow mixed; no continuous-only guard.
        this.data = Objects.requireNonNull(data);
        this.variables = Collections.unmodifiableList(new ArrayList<>(data.getVariables()));
        this.indexMap = indexMap(this.variables);
        setAlpha(alpha);
    }

    @Override
    public IndependenceTest indTestSubset(List<Node> vars) {
        MinimaxCITest2 t = new MinimaxCITest2(this.data, this.alpha);
        t.setVerbose(this.verbose);
        t.setRows(this.rows);
        t.setBinningConfig(this.binningCfg);
        t.setPermutations(this.permutations);
        t.setPermSeed(this.permSeed);
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

        int[] izAll = idxSorted(z);
        int[] izDisc = filterDiscrete(izAll);
        int[] izCont = filterContinuous(izAll);

        List<Integer> baseRows = listRows();
        List<Integer> useRows = rowsCompleteFor(ix, iy, izAll, baseRows);
        int n = useRows.size();

        if (n < 20) return 0.0; // conservative guard (same spirit as before)

        // Pull X/Y arrays in local index space [0..n)
        boolean xIsDisc = isDiscrete(ix);
        boolean yIsDisc = isDiscrete(iy);

        double[] xC = xIsDisc ? null : new double[n];
        double[] yC = yIsDisc ? null : new double[n];
        int[] xD = xIsDisc ? new int[n] : null;
        int[] yD = yIsDisc ? new int[n] : null;

        for (int i = 0; i < n; i++) {
            int r = useRows.get(i);
            if (xIsDisc) xD[i] = getDisc(r, ix); else xC[i] = data.getDouble(r, ix);
            if (yIsDisc) yD[i] = getDisc(r, iy); else yC[i] = data.getDouble(r, iy);
        }

        // Step 1: form base groups from continuous Z bins (or single group if none)
        int[][] baseGroups;
        if (izCont.length == 0) {
            baseGroups = new int[][]{range(n)};
        } else {
            baseGroups = groupCache.getGroups(data, izCont, useRows, binningCfg);
            if (baseGroups.length == 0) return 0.0; // conservative dependent
        }

        // Step 2: refine each base group by discrete Z strata
        int[][] groups = (izDisc.length == 0)
                ? baseGroups
                : refineByDiscreteZ(baseGroups, useRows, izDisc, binningCfg.minBinSize());

        if (groups.length == 0) return 0.0; // conservative dependent

        long seed = permSeed ^ ix ^ (iy * 1315423911L) ^ Arrays.hashCode(izAll);

        return permuteWithinGroupsPValueMixed(
                xIsDisc, yIsDisc,
                xC, yC, xD, yD,
                groups, permutations, seed
        );
    }

    // =================== Mixed-type stat + permutation ===================

    private static double statMixed(boolean xIsDisc, boolean yIsDisc,
                                    double[] xC, double[] yC, int[] xD, int[] yD,
                                    int[][] groups) {
        double T = 0.0;

        if (!xIsDisc && !yIsDisc) {
            // continuous-continuous: sum (m-1) r^2
            for (int[] g : groups) {
                int m = g.length;
                if (m < 3) continue;

                double mx = 0, my = 0;
                for (int idx : g) { mx += xC[idx]; my += yC[idx]; }
                mx /= m; my /= m;

                double sxx = 0, syy = 0, sxy = 0;
                for (int idx : g) {
                    double dx = xC[idx] - mx;
                    double dy = yC[idx] - my;
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

        if (xIsDisc && yIsDisc) {
            // discrete-discrete: sum Pearson chi-square over groups
            for (int[] g : groups) {
                int m = g.length;
                if (m < 5) continue;

                // build counts using hash maps (levels can be sparse)
                Map<Integer, Integer> xMap = new HashMap<>();
                Map<Integer, Integer> yMap = new HashMap<>();
                for (int idx : g) {
                    xMap.put(xD[idx], 0);
                    yMap.put(yD[idx], 0);
                }
                int[] xLevels = xMap.keySet().stream().mapToInt(Integer::intValue).toArray();
                int[] yLevels = yMap.keySet().stream().mapToInt(Integer::intValue).toArray();

                // remap to compact indices
                for (int i = 0; i < xLevels.length; i++) xMap.put(xLevels[i], i);
                for (int j = 0; j < yLevels.length; j++) yMap.put(yLevels[j], j);

                int I = xLevels.length, J = yLevels.length;
                if (I < 2 || J < 2) continue;

                int[][] nij = new int[I][J];
                int[] ni = new int[I];
                int[] nj = new int[J];

                for (int idx : g) {
                    int ii = xMap.get(xD[idx]);
                    int jj = yMap.get(yD[idx]);
                    nij[ii][jj]++;
                    ni[ii]++;
                    nj[jj]++;
                }

                double chi2 = 0.0;
                for (int ii = 0; ii < I; ii++) {
                    for (int jj = 0; jj < J; jj++) {
                        double e = (double) ni[ii] * (double) nj[jj] / (double) m;
                        if (e <= 1e-12) continue;
                        double d = nij[ii][jj] - e;
                        chi2 += (d * d) / e;
                    }
                }
                T += chi2;
            }
            return T;
        }

        // mixed: one discrete, one continuous. Use m * eta^2 (correlation ratio)
        // Define discrete as "A", continuous as "Y".
        for (int[] g : groups) {
            int m = g.length;
            if (m < 5) continue;

            int[] a = xIsDisc ? xD : yD;
            double[] yy = xIsDisc ? yC : xC;

            // overall mean
            double mean = 0.0;
            for (int idx : g) mean += yy[idx];
            mean /= m;

            double sst = 0.0;
            for (int idx : g) {
                double d = yy[idx] - mean;
                sst += d * d;
            }
            if (sst <= 1e-12) continue;

            // per-level sums
            Map<Integer, double[]> acc = new HashMap<>(); // level -> {sum, count}
            for (int idx : g) {
                int lev = a[idx];
                double[] sc = acc.computeIfAbsent(lev, k -> new double[2]);
                sc[0] += yy[idx];
                sc[1] += 1.0;
            }
            if (acc.size() < 2) continue;

            double ssb = 0.0;
            for (double[] sc : acc.values()) {
                double mu = sc[0] / sc[1];
                double c = sc[1];
                double d = mu - mean;
                ssb += c * d * d;
            }

            double eta2 = ssb / sst;
            T += m * eta2;
        }
        return T;
    }

    private static double permuteWithinGroupsPValueMixed(
            boolean xIsDisc, boolean yIsDisc,
            double[] xC, double[] yC, int[] xD, int[] yD,
            int[][] groups, int B, long seed
    ) {
        double obs = statMixed(xIsDisc, yIsDisc, xC, yC, xD, yD, groups);

        SplittableRandom rng = new SplittableRandom(seed);

        // permutable copy of Y only
        double[] yCperm = (!yIsDisc ? Arrays.copyOf(yC, yC.length) : null);
        int[] yDperm = (yIsDisc ? Arrays.copyOf(yD, yD.length) : null);

        int ge = 0;
        for (int b = 0; b < B; b++) {

            // reset perm arrays
            if (!yIsDisc) System.arraycopy(yC, 0, yCperm, 0, yC.length);
            else System.arraycopy(yD, 0, yDperm, 0, yD.length);

            // shuffle Y within each group
            for (int[] g : groups) {
                for (int i = g.length - 1; i > 0; i--) {
                    int j = rng.nextInt(i + 1);
                    int ii = g[i], jj = g[j];

                    if (!yIsDisc) {
                        double tmp = yCperm[ii];
                        yCperm[ii] = yCperm[jj];
                        yCperm[jj] = tmp;
                    } else {
                        int tmp = yDperm[ii];
                        yDperm[ii] = yDperm[jj];
                        yDperm[jj] = tmp;
                    }
                }
            }

            double t = statMixed(xIsDisc, yIsDisc, xC,
                    (!yIsDisc ? yCperm : null),
                    xD,
                    (yIsDisc ? yDperm : null),
                    groups);

            if (t >= obs) ge++;
        }

        // smoothing to avoid p=0
        return (ge + 1.0) / (B + 1.0);
    }

    // =================== grouping refinement for discrete Z ===================

    private int[][] refineByDiscreteZ(int[][] baseGroups,
                                      List<Integer> useRows,
                                      int[] zDisc,
                                      int minSize) {

        ArrayList<int[]> out = new ArrayList<>();

        for (int[] g : baseGroups) {
            // map: key(tuple of discrete levels) -> indices in local space
            HashMap<KeyTuple, IntArrayList> buckets = new HashMap<>();

            for (int localIdx : g) {
                int row = useRows.get(localIdx);

                int[] levs = new int[zDisc.length];
                for (int j = 0; j < zDisc.length; j++) {
                    levs[j] = getDisc(row, zDisc[j]);
                }

                KeyTuple key = new KeyTuple(levs);
                buckets.computeIfAbsent(key, k -> new IntArrayList()).add(localIdx);
            }

            for (IntArrayList lst : buckets.values()) {
                if (lst.size() >= minSize) out.add(lst.toArray());
            }
        }

        return out.toArray(new int[0][]);
    }

    private static final class KeyTuple {
        private final int[] a;
        KeyTuple(int[] a) { this.a = a; }
        @Override public int hashCode() { return Arrays.hashCode(a); }
        @Override public boolean equals(Object o) {
            return (o instanceof KeyTuple kt) && Arrays.equals(a, kt.a);
        }
    }

    private static final class IntArrayList {
        private int[] a = new int[16];
        private int n = 0;
        void add(int v) { if (n == a.length) a = Arrays.copyOf(a, a.length * 2); a[n++] = v; }
        int size() { return n; }
        int[] toArray() { return Arrays.copyOf(a, n); }
    }

    // =================== rows / indexing / types ===================

    private List<Integer> rowsCompleteFor(int ix, int iy, int[] izAll, List<Integer> baseRows) {
        List<Integer> out = new ArrayList<>(baseRows.size());

        boolean xDisc = isDiscrete(ix);
        boolean yDisc = isDiscrete(iy);

        for (int r : baseRows) {
            if (xDisc) {
                int vx = getDisc(r, ix);
                if (vx == DiscreteVariable.MISSING_VALUE) continue;
            } else {
                double vx = data.getDouble(r, ix);
                if (Double.isNaN(vx)) continue;
            }

            if (yDisc) {
                int vy = getDisc(r, iy);
                if (vy == DiscreteVariable.MISSING_VALUE) continue;
            } else {
                double vy = data.getDouble(r, iy);
                if (Double.isNaN(vy)) continue;
            }

            boolean ok = true;
            for (int j : izAll) {
                if (isDiscrete(j)) {
                    int vz = getDisc(r, j);
                    if (vz == DiscreteVariable.MISSING_VALUE) { ok = false; break; }
                } else {
                    double vz = data.getDouble(r, j);
                    if (Double.isNaN(vz)) { ok = false; break; }
                }
            }
            if (ok) out.add(r);
        }
        return out;
    }

    private boolean isDiscrete(int col) {
        Node v = variables.get(col);
        return v instanceof DiscreteVariable;
    }

    private int getDisc(int row, int col) {
        // Most Tetrad DataSet impls support this for discrete columns.
        return data.getInt(row, col);
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

    private int[] filterDiscrete(int[] cols) {
        int c = 0;
        for (int col : cols) if (isDiscrete(col)) c++;
        int[] out = new int[c];
        int k = 0;
        for (int col : cols) if (isDiscrete(col)) out[k++] = col;
        return out;
    }

    private int[] filterContinuous(int[] cols) {
        int c = 0;
        for (int col : cols) if (!isDiscrete(col)) c++;
        int[] out = new int[c];
        int k = 0;
        for (int col : cols) if (!isDiscrete(col)) out[k++] = col;
        return out;
    }

    private static int[] range(int n) {
        int[] r = new int[n];
        for (int i = 0; i < n; i++) r[i] = i;
        return r;
    }

    private List<Integer> listRows() {
        if (rows != null) return rows;
        int n = data.getNumRows();
        ArrayList<Integer> r = new ArrayList<>(n);
        for (int i = 0; i < n; i++) r.add(i);
        return r;
    }

    private static Map<String, Integer> indexMap(List<Node> vars) {
        Map<String, Integer> m = new HashMap<>();
        for (int i = 0; i < vars.size(); i++) m.put(vars.get(i).getName(), i);
        return m;
    }

    // =================== required interface methods + knobs ===================

    @Override public List<Node> getVariables() { return variables; }
    @Override public double getAlpha() { return alpha; }
    public void setAlpha(double alpha) {
        if (alpha < 0 || alpha > 1) throw new IllegalArgumentException("alpha must be in [0,1]");
        this.alpha = alpha;
    }
    @Override public DataSet getData() { return data; }
    @Override public boolean isVerbose() { return verbose; }
    public void setVerbose(boolean verbose) { this.verbose = verbose; }
    @Override public int getSampleSize() { return data.getNumRows(); }

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

    @Override public List<DataSet> getDataSets() { return List.of(data); }

    @Override public List<Integer> getRows() { return rows; }

    @Override
    public void setRows(List<Integer> rows) {
        if (rows == null) {
            this.rows = null;
            return;
        }
        for (int i = 0; i < rows.size(); i++) {
            Integer r = rows.get(i);
            if (r == null) throw new NullPointerException("Row " + i + " is null.");
            if (r < 0) throw new IllegalArgumentException("Row " + i + " is negative.");
            if (r >= data.getNumRows()) throw new IllegalArgumentException("Row " + i + " out of bounds: " + r);
        }
        this.rows = new ArrayList<>(rows);
    }
}