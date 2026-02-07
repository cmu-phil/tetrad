package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.Math.*;

/**
 * Minimax CI test: a robust, nonparametric conditional independence test for mixed data
 * (continuous + discrete), intended as a reliable “gold fallback” when model-based or
 * kernel-based tests are unavailable or brittle.
 *
 * <p><b>Key design goal.</b> For the continuous–continuous case, this implementation
 * follows the original “no-frills” minimax procedure (as in {@code MinimaxCITestOrig})
 * for speed and behavior parity:
 *
 * <ul>
 *   <li>Stratify on Z by quantile-binning continuous Z and exact-matching discrete Z.</li>
 *   <li>Within each stratum, measure dependence via {@code (m-1) r^2} (Pearson correlation).</li>
 *   <li>Aggregate across strata by summation.</li>
 *   <li>Calibrate by within-stratum permutation (shuffle Y within each stratum).</li>
 * </ul>
 *
 * <p>When discrete variables are involved (in X, Y, or Z), the test uses a stratified
 * contingency-table likelihood ratio (G-test) with within-stratum permutation, while
 * preserving the same stratification mechanism on Z.</p>
 *
 * <p><b>Practical notes.</b></p>
 * <ul>
 *   <li>If Z is empty, there is a single stratum containing all usable rows.</li>
 *   <li>Rows with missing values in any of {X, Y, Z} are dropped per test.</li>
 *   <li>Strata smaller than {@code minStratumSize} are discarded.</li>
 * </ul>
 */
public final class MinimaxCITest implements IndependenceTest, RowsSettable {

    // ---------------- data ----------------
    private final DataSet data;
    private final List<Node> variables;
    private final Map<String, Integer> indexMap;

    // global z-scored continuous columns (NaNs preserved). Discrete vars are NaN-filled.
    private final double[][] zCols;

    // cache for strata: (Z signature + useRows signature + knobs) -> groups of indices in useRows-space
    private final ConcurrentHashMap<StrataKey, int[][]> strataCache = new ConcurrentHashMap<>();

    // ---------------- knobs ----------------
    private double alpha = 0.01;

    // permutation
    private int permutations = 200;
    private long permSeed = 1L;

    // stratification on Z
    private int binsPerContZ = 6;
    private int minStratumSize = 6;

    // local discretization of X,Y within a stratum (mixed case)
    private int binsPerContXY = 6;

    // safety bounds (avoid pathological huge contingency tables)
    private int maxObservedLevelsPerVar = 32;
    private int maxCellsPerStratum = 1024;

    // aggregation for mixed-case G-test (kept for compatibility)
    // NOTE: continuous-continuous always uses SUM across strata (Orig behavior).
    private boolean useMaxAcrossStrata = false;

    // behavior
    private boolean verbose = false;

    // optional row restriction
    private List<Integer> rows = null;

    /**
     * Constructs a MinimaxCITest instance for performing conditional independence tests
     * using the minimax criterion on the provided data set and significance level.
     *
     * @param data the data set to be analyzed; must not be null
     * @param alpha the significance level (alpha) for the test, with typical values in the range (0, 1]
     * @throws NullPointerException if the provided data is null
     */
    public MinimaxCITest(DataSet data, double alpha) {
        if (data == null) throw new NullPointerException("data");
        this.data = data;
        this.variables = Collections.unmodifiableList(new ArrayList<>(data.getVariables()));
        this.indexMap = indexMap(this.variables);
        setAlpha(alpha);

        int p = variables.size();
        int n = data.getNumRows();

        double[][] raw = new double[p][n];
        for (int j = 0; j < p; j++) {
            if (isDiscrete(j)) {
                Arrays.fill(raw[j], Double.NaN);
            } else {
                for (int r = 0; r < n; r++) raw[j][r] = data.getDouble(r, j);
            }
        }

        this.zCols = new double[p][n];
        for (int j = 0; j < p; j++) {
            if (isDiscrete(j)) Arrays.fill(zCols[j], Double.NaN);
            else zscoreColumnPreserveNaN(raw[j], zCols[j]);
        }
    }

    // =========================================================
    // IndependenceTest
    // =========================================================

    /**
     * Performs a conditional independence test to determine if two variables, {@code x} and {@code y},
     * are independent given a set of conditioning variables {@code z}. The test computes a p-value and
     * compares it to the significance level {@code alpha} to decide independence.
     *
     * @param x the first variable being tested for independence; must not be null
     * @param y the second variable being tested for independence; must not be null
     * @param z the set of conditioning variables; may be empty but must not be null
     * @return an {@code IndependenceResult} containing the independence determination, the p-value,
     *         and additional information about the test
     */
    @Override
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z) {
        double p = getPValue(x, y, z);
        boolean indep = p > alpha;

        IndependenceResult r = new IndependenceResult(
                new IndependenceFact(x, y, z),
                indep,
                p,
                alpha - p
        );

        if (verbose && indep) {
            TetradLogger.getInstance().log(LogUtilsSearch.independenceFactMsg(x, y, z, p));
        }
        return r;
    }

    /**
     * Calculates the p-value for testing the statistical independence of two nodes
     * conditioned on a set of nodes in a dataset.
     *
     * @param x the first variable (node) to test for independence; must not be null.
     * @param y the second variable (node) to test for independence; must not be null.
     * @param z the set of conditioning variables (nodes); must not be null.
     * @return the p-value indicating the statistical independence between x and y
     *         conditioned on z. A value closer to 0 indicates stronger evidence
     *         against independence, while a value closer to 1 supports independence.
     */
    public double getPValue(Node x, Node y, Set<Node> z) {
        Objects.requireNonNull(x);
        Objects.requireNonNull(y);
        Objects.requireNonNull(z);
        if (x.equals(y)) return 1.0;

        int ix = idx(x);
        int iy = idx(y);
        int[] iz = idxSorted(z);

        // Drop rows with missing in {X,Y,Z}
        List<Integer> baseRows = listRows();
        List<Integer> useRows = rowsCompleteFor(ix, iy, iz, baseRows);
        int n = useRows.size();

        if (n < 20) return 0.0; // conservative guard

        int[][] strata = getStrata(iz, useRows);
        if (strata.length == 0) return 0.0;

        // Fast path: continuous-continuous with continuous-only conditioning:
        // EXACTLY the Orig-style stat + permutation.
        if (isContContContZ(ix, iy, iz)) {
            return pValueContContOrig(ix, iy, useRows, strata, permutations,
                    permSeed ^ ix ^ (iy * 1315423911L) ^ Arrays.hashCode(iz));
        }

        // Mixed/general path: stratified G-test with within-stratum permutation
        return pValueMixedGTest(ix, iy, useRows, strata, permutations,
                permSeed ^ ix ^ (iy * 1315423911L) ^ Arrays.hashCode(iz));
    }

    // =========================================================
    // Continuous-continuous case: Orig behavior (speed + parity)
    // =========================================================

    private boolean isContContContZ(int ix, int iy, int[] iz) {
        if (isDiscrete(ix) || isDiscrete(iy)) return false;
        for (int j : iz) if (isDiscrete(j)) return false;
        return true;
    }

    private static double statCorrSqGroups(double[] x, double[] y, int[][] groups) {
        double T = 0.0;
        for (int[] g : groups) {
            int m = g.length;
            if (m < 3) continue;

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

            double r = sxy / sqrt(sxx * syy);
            T += (m - 1.0) * r * r;
        }
        return T;
    }

    private double pValueContContOrig(int ix, int iy,
                                      List<Integer> useRows, int[][] strata,
                                      int B, long seed) {

        final int n = useRows.size();

        // Build x,y arrays in useRows-space (z-scored is fine; r is affine-invariant)
        double[] xArr = new double[n];
        double[] yArr = new double[n];
        for (int i = 0; i < n; i++) {
            int r = useRows.get(i);
            xArr[i] = zCols[ix][r];
            yArr[i] = zCols[iy][r];
        }

        double obs = statCorrSqGroups(xArr, yArr, strata);

        SplittableRandom rng = new SplittableRandom(seed);
        double[] yPerm = Arrays.copyOf(yArr, n);

        int ge = 0;
        int BB = Math.max(50, B);

        for (int b = 0; b < BB; b++) {
            System.arraycopy(yArr, 0, yPerm, 0, n);

            // shuffle Y within each stratum (Orig does Y; we do the same here)
            for (int[] g : strata) {
                for (int i = g.length - 1; i > 0; i--) {
                    int j = rng.nextInt(i + 1);
                    int ii = g[i], jj = g[j];
                    double tmp = yPerm[ii];
                    yPerm[ii] = yPerm[jj];
                    yPerm[jj] = tmp;
                }
            }

            double t = statCorrSqGroups(xArr, yPerm, strata);
            if (t >= obs) ge++;
        }

        // smoothing (Orig)
        return (ge + 1.0) / (BB + 1.0);
    }

    // =========================================================
    // Mixed/general case: stratified G-test with cached plans
    // =========================================================

    private double pValueMixedGTest(int ix, int iy,
                                    List<Integer> useRows, int[][] strata,
                                    int B, long seed) {

        // Precompute per-stratum discretizations once (critical for speed)
        GroupPlan[] plans = buildPlans(ix, iy, useRows, strata);
        if (plans.length == 0) return 0.0;

        double tObs = aggregate(plans, null);
        if (!Double.isFinite(tObs)) return 1.0;

        SplittableRandom rng = new SplittableRandom(seed);
        int BB = Math.max(50, B);

        int ge = 0;
        int valid = 0;

        for (int b = 0; b < BB; b++) {
            double t = aggregate(plans, rng);
            if (!Double.isFinite(t)) continue;
            valid++;
            if (t >= tObs) ge++;
        }

        return (ge + 1.0) / (valid + 1.0);
    }

    /**
     * If rng != null, for each plan we shuffle X categories within the stratum before computing.
     */
    private double aggregate(GroupPlan[] plans, SplittableRandom rng) {
        if (useMaxAcrossStrata) {
            double maxT = 0.0;
            for (GroupPlan gp : plans) {
                double ts = gp.statistic(rng);
                if (!Double.isFinite(ts)) continue;
                if (ts > maxT) maxT = ts;
            }
            return maxT;
        } else {
            double sumT = 0.0;
            for (GroupPlan gp : plans) {
                double ts = gp.statistic(rng);
                if (!Double.isFinite(ts)) continue;
                sumT += ts;
            }
            return sumT;
        }
    }

    private GroupPlan[] buildPlans(int ix, int iy, List<Integer> useRows, int[][] strata) {
        ArrayList<GroupPlan> out = new ArrayList<>(strata.length);

        for (int[] g : strata) {
            if (g.length < minStratumSize) continue;
            GroupPlan gp = buildGroupPlan(ix, iy, useRows, g);
            if (gp != null) out.add(gp);
        }

        return out.toArray(new GroupPlan[0]);
    }

    private GroupPlan buildGroupPlan(int ix, int iy, List<Integer> useRows, int[] g) {
        final int m = g.length;
        if (m < minStratumSize) return null;

        // Adaptive bins for continuous X/Y: min(global, floor(sqrt(m))) with a floor of 2.
        int binsXY = Math.max(2, Math.min(binsPerContXY, (int) Math.floor(Math.sqrt(m))));

        // Also enforce maxCellsPerStratum for contingency tables
        int maxBinsByCells = Math.max(2, (int) Math.floor(Math.sqrt(maxCellsPerStratum)));
        binsXY = Math.min(binsXY, maxBinsByCells);

        Cat xCat = buildCategories(ix, useRows, g, binsXY);
        Cat yCat = buildCategories(iy, useRows, g, binsXY);
        if (xCat == null || yCat == null) return null;

        if (xCat.K < 2 || yCat.K < 2) {
            return new GroupPlan(xCat.codes, yCat.codes, xCat.K, yCat.K, true);
        }

        long cells = (long) xCat.K * (long) yCat.K;
        if (cells > maxCellsPerStratum) {
            // Prefer structured reductions over hashing:
            // - If continuous bins got us here, shrink bins further.
            // - Otherwise, fall back to a small shared K via deterministic down-binning.
            int K = Math.max(2, (int) Math.floor(Math.sqrt(maxCellsPerStratum)));

            int[] x = (xCat.K > K) ? downBinDeterministic(xCat.codes, xCat.K, K) : xCat.codes;
            int[] y = (yCat.K > K) ? downBinDeterministic(yCat.codes, yCat.K, K) : yCat.codes;

            return new GroupPlan(x, y, Math.min(xCat.K, K), Math.min(yCat.K, K), false);
        }

        return new GroupPlan(xCat.codes, yCat.codes, xCat.K, yCat.K, false);
    }

    /**
     * Deterministically down-bin categorical codes in 0..oldK-1 to 0..newK-1 by grouping
     * adjacent codes. This assumes codes are already "ordered" in a meaningful way for
     * continuous (quantile) bins; for arbitrary discrete codes it's a reasonable fallback.
     */
    private static int[] downBinDeterministic(int[] codes, int oldK, int newK) {
        if (newK >= oldK) return codes;
        int[] out = new int[codes.length];
        for (int i = 0; i < codes.length; i++) {
            int c = codes[i];
            // map [0..oldK-1] -> [0..newK-1]
            int b = (int) ((long) c * (long) newK / (long) oldK);
            if (b >= newK) b = newK - 1;
            out[i] = b;
        }
        return out;
    }

    private static int[] hashDown(int[] codes, int K) {
        int[] out = new int[codes.length];
        for (int i = 0; i < codes.length; i++) out[i] = floorMod(mix32(codes[i]), K);
        return out;
    }

    private Cat buildCategories(int varIdx, List<Integer> useRows, int[] g, int effBinsXY) {
        if (isDiscrete(varIdx)) {
            // Collect levels
            int[] lev = new int[g.length];
            int n = 0;
            for (int ii : g) {
                int row = useRows.get(ii);
                int v = data.getInt(row, varIdx);
                if (v == DiscreteVariable.MISSING_VALUE) continue; // should not happen after filtering
                lev[n++] = v;
            }
            if (n < minStratumSize) return null;
            if (n != lev.length) lev = Arrays.copyOf(lev, n);

            // If too many unique levels, hash to effBinsXY (small & stable)
            int unique = approxUniqueCount(lev, maxObservedLevelsPerVar + 1);
            if (unique > maxObservedLevelsPerVar) {
                int K = Math.max(2, effBinsXY);
                int[] codes = new int[lev.length];
                for (int i = 0; i < lev.length; i++) codes[i] = floorMod(mix32(lev[i]), K);
                return new Cat(codes, K);
            }

            // Otherwise compress deterministically, with Top-L + OTHER if needed.
            int[] codes;
            int K;

            if (approxUniqueCount(lev, maxObservedLevelsPerVar + 1) > maxObservedLevelsPerVar) {
                // Top-(L-1) + OTHER
                K = Math.max(2, maxObservedLevelsPerVar);
                codes = compressTopLPlusOther(lev, K);
                // compressTopLPlusOther returns exactly K categories when compression happens
            } else {
                // Compress to 0..K-1 in encounter order
                HashMap<Integer, Integer> map = new HashMap<>();
                int next = 0;
                codes = new int[lev.length];
                for (int i = 0; i < lev.length; i++) {
                    Integer idx = map.get(lev[i]);
                    if (idx == null) {
                        idx = next++;
                        map.put(lev[i], idx);
                    }
                    codes[i] = idx;
                }
                K = next;
            }

            return new Cat(codes, K);
        } else {
            // Continuous: quantile binning within this stratum (observed, fixed edges)
            double[] vals = new double[g.length];
            int n = 0;
            for (int ii : g) {
                int row = useRows.get(ii);
                double v = data.getDouble(row, varIdx);
                if (Double.isNaN(v)) continue; // should not happen after filtering
                vals[n++] = v;
            }
            if (n < minStratumSize) return null;
            if (n != vals.length) vals = Arrays.copyOf(vals, n);

            int bins = Math.max(2, effBinsXY);
            double[] edges = quantileEdges(vals, bins);

            int[] codes = new int[vals.length];
            for (int i = 0; i < vals.length; i++) codes[i] = binIndex(edges, vals[i]);
            return new Cat(codes, bins);
        }
    }

    /**
     * Compress observed discrete levels to at most {@code maxLevels} categories by keeping
     * the top-(maxLevels-1) most frequent levels and mapping all others to a single OTHER bucket.
     *
     * Returns codes in 0..K-1 where K <= maxLevels.
     */
    private static int[] compressTopLPlusOther(int[] levels, int maxLevels) {
        if (maxLevels < 2) throw new IllegalArgumentException("maxLevels must be >= 2");

        // Count frequencies
        HashMap<Integer, Integer> freq = new HashMap<>();
        for (int v : levels) freq.merge(v, 1, Integer::sum);

        // If already small enough, just compress deterministically (encounter order)
        if (freq.size() <= maxLevels) {
            HashMap<Integer, Integer> map = new HashMap<>(freq.size() * 2);
            int next = 0;
            int[] codes = new int[levels.length];
            for (int i = 0; i < levels.length; i++) {
                Integer idx = map.get(levels[i]);
                if (idx == null) {
                    idx = next++;
                    map.put(levels[i], idx);
                }
                codes[i] = idx;
            }
            return codes; // categories = next
        }

        // Keep top-(maxLevels-1) as distinct; last category is OTHER
        int keep = maxLevels - 1;

        // Select top levels by frequency
        ArrayList<Map.Entry<Integer, Integer>> entries = new ArrayList<>(freq.entrySet());
        entries.sort((a, b) -> Integer.compare(b.getValue(), a.getValue())); // descending freq

        HashMap<Integer, Integer> map = new HashMap<>(maxLevels * 2);
        for (int i = 0; i < keep; i++) {
            map.put(entries.get(i).getKey(), i);
        }
        final int OTHER = keep;

        int[] codes = new int[levels.length];
        for (int i = 0; i < levels.length; i++) {
            Integer idx = map.get(levels[i]);
            codes[i] = (idx == null) ? OTHER : idx;
        }
        return codes; // categories = maxLevels (exactly)
    }

    private static int approxUniqueCount(int[] a, int stopAfter) {
        // cheap-ish unique counter with early stop
        HashSet<Integer> s = new HashSet<>();
        for (int v : a) {
            s.add(v);
            if (s.size() >= stopAfter) return s.size();
        }
        return s.size();
    }

    private static final class Cat {
        final int[] codes; // 0..K-1
        final int K;
        Cat(int[] codes, int K) { this.codes = codes; this.K = K; }
    }

    /**
     * Per-stratum plan: fixed observed categories for X,Y; permutation shuffles X categories.
     */
    private static final class GroupPlan {
        final int[] xObs;     // observed codes
        final int[] yObs;     // observed codes
        final int Kx, Ky;
        final boolean constant; // if either side is constant

        // scratch buffers for permutation (allocated once)
        final int[] xPerm;
        final int[][] counts;
        final int[] rowS;
        final int[] colS;

        GroupPlan(int[] xObs, int[] yObs, int Kx, int Ky, boolean constant) {
            this.xObs = xObs;
            this.yObs = yObs;
            this.Kx = Kx;
            this.Ky = Ky;
            this.constant = constant;

            this.xPerm = new int[xObs.length];

            this.counts = new int[Kx][Ky];
            this.rowS = new int[Kx];
            this.colS = new int[Ky];
        }

        double statistic(SplittableRandom rng) {
            if (constant) return 0.0;

            final int n = xObs.length;

            // Choose X codes: observed or permuted within-stratum
            if (rng == null) {
                System.arraycopy(xObs, 0, xPerm, 0, n);
            } else {
                System.arraycopy(xObs, 0, xPerm, 0, n);
                // Fisher–Yates shuffle xPerm (within this group)
                for (int i = n - 1; i > 0; i--) {
                    int j = rng.nextInt(i + 1);
                    int tmp = xPerm[i];
                    xPerm[i] = xPerm[j];
                    xPerm[j] = tmp;
                }
            }

            // Reset margins/counts
            for (int i = 0; i < Kx; i++) {
                Arrays.fill(counts[i], 0);
                rowS[i] = 0;
            }
            Arrays.fill(colS, 0);

            // Fill contingency
            for (int i = 0; i < n; i++) {
                int xi = xPerm[i];
                int yi = yObs[i];
                counts[xi][yi]++;
                rowS[xi]++;
                colS[yi]++;
            }

            return gTestFromCounts(counts, rowS, colS, n);
        }
    }

    // =========================================================
    // Z-strata (same mechanism as before)
    // =========================================================

    private int[][] getStrata(int[] zIdx, List<Integer> useRows) {
        if (zIdx.length == 0) return new int[][]{range(useRows.size())};

        long rowsSig = signature(useRows);
        StrataKey key = new StrataKey(zIdx, rowsSig, binsPerContZ, minStratumSize);

        return strataCache.computeIfAbsent(key, kk -> buildStrata(zIdx, useRows, binsPerContZ, minStratumSize));
    }

    private int[][] buildStrata(int[] zIdx, List<Integer> useRows, int binsPerCont, int minSize) {
        int n = useRows.size();

        int[] zDisc = filterDiscrete(zIdx);
        int[] zCont = filterContinuous(zIdx);

        // precompute bin edges per continuous Z on useRows
        double[][] edges = new double[zCont.length][];
        for (int j = 0; j < zCont.length; j++) {
            double[] vals = new double[n];
            for (int i = 0; i < n; i++) vals[i] = zCols[zCont[j]][useRows.get(i)];
            edges[j] = quantileEdges(vals, binsPerCont);
        }

        HashMap<StratumSignature, IntArrayList> buckets = new HashMap<>();

        for (int i = 0; i < n; i++) {
            int row = useRows.get(i);

            int[] parts = new int[zDisc.length + zCont.length];
            int k = 0;

            for (int v : zDisc) {
                int lev = data.getInt(row, v);
                parts[k++] = lev;
            }

            for (int j = 0; j < zCont.length; j++) {
                double val = zCols[zCont[j]][row];
                int b = binIndex(edges[j], val);
                parts[k++] = b;
            }

            StratumSignature sig = new StratumSignature(parts);
            buckets.computeIfAbsent(sig, s -> new IntArrayList()).add(i);
        }

        ArrayList<int[]> groups = new ArrayList<>();
        for (IntArrayList lst : buckets.values()) {
            if (lst.size() >= minSize) groups.add(lst.toArray());
        }

        return groups.toArray(new int[0][]);
    }

    // =========================================================
    // Missingness / row filtering (mixed-aware)
    // =========================================================

    private List<Integer> rowsCompleteFor(int ix, int iy, int[] iz, List<Integer> baseRows) {
        List<Integer> out = new ArrayList<>(baseRows.size());
        for (int r : baseRows) {
            if (!isValuePresent(r, ix)) continue;
            if (!isValuePresent(r, iy)) continue;

            boolean ok = true;
            for (int j : iz) {
                if (!isValuePresent(r, j)) {
                    ok = false;
                    break;
                }
            }
            if (ok) out.add(r);
        }
        return out;
    }

    private boolean isValuePresent(int row, int col) {
        if (isDiscrete(col)) {
            return data.getInt(row, col) != DiscreteVariable.MISSING_VALUE;
        } else {
            return !Double.isNaN(data.getDouble(row, col));
        }
    }

    // =========================================================
    // Public API / setters / interface
    // =========================================================

    /**
     * Retrieves the list of variable nodes managed by this instance.
     * These variables typically represent elements or features involved
     * in statistical or algorithmic processes.
     *
     * @return a {@code List<Node>} containing the variable nodes
     */
    @Override
    public List<Node> getVariables() {
        return variables;
    }

    /**
     * Retrieves the underlying dataset associated with this instance.
     * The dataset is used for performing conditional independence tests
     * and other statistical analyses.
     *
     * @return the {@code DataSet} object containing the data managed by this instance
     */
    @Override
    public DataSet getData() {
        return data;
    }

    /**
     * Retrieves the list of datasets associated with this instance.
     * This method returns a list containing the primary dataset used for
     * conditional independence tests and other statistical analyses.
     *
     * @return a {@code List<DataSet>} containing the dataset managed by this instance
     */
    @Override
    public List<DataSet> getDataSets() {
        return List.of(data);
    }

    /**
     * Retrieves the significance level (alpha) used for conditional independence tests.
     * This value represents the threshold for determining statistical significance.
     *
     * @return the significance level (alpha) as a {@code double}
     */
    @Override
    public double getAlpha() {
        return alpha;
    }

    /**
     * Sets the significance level (alpha) for conditional independence tests.
     * This value represents the threshold for determining statistical significance.
     *
     * @param alpha the significance level (alpha) to set, must be in the range [0,1]
     */
    public void setAlpha(double alpha) {
        if (alpha < 0 || alpha > 1) throw new IllegalArgumentException("alpha must be in [0,1]");
        this.alpha = alpha;
    }

    /**
     * Retrieves the sample size of the dataset associated with this instance.
     * The sample size is equivalent to the number of rows present in the dataset.
     *
     * @return the sample size as an {@code int}.
     */
    @Override
    public int getSampleSize() {
        return data.getNumRows();
    }

    /**
     * Retrieves the verbosity setting for this instance.
     * When set to true, the instance will provide detailed output during operations.
     *
     * @return the verbosity setting as a {@code boolean}
     */
    @Override
    public boolean isVerbose() {
        return verbose;
    }

    /**
     * Sets the verbosity level for this instance.
     * When verbosity is enabled, detailed information about the
     * execution process may be logged or displayed.
     *
     * @param verbose true to enable verbose output, false to disable it
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Sets the number of permutations to be used in conditional independence tests.
     * This value determines the number of random permutations performed to assess statistical significance.
     *
     * @param B the number of permutations, must be at least 50
     */
    public void setPermutations(int B) {
        this.permutations = Math.max(50, B);
    }

    /**
     * Sets the permutation seed value used for randomization or related operations.
     *
     * @param s the seed value to set for permSeed
     */
    public void setPermSeed(long s) {
        this.permSeed = s;
    }

    /**
     * Sets the number of bins to be used per container along the Z-axis.
     * Ensures that the value is at least 2 to maintain valid binning.
     * Clears the strata cache to refresh any dependent data.
     *
     * @param b the desired number of bins per container along the Z-axis;
     *          must be 2 or more. If a value less than 2 is provided, it
     *          will default to 2.
     */
    public void setBinsPerContZ(int b) {
        this.binsPerContZ = Math.max(2, b);
        strataCache.clear();
    }

    /**
     * Sets the minimum stratum size used in the computation.
     * The minimum value is constrained to be at least 2.
     * Updates to this value will clear the existing stratum cache.
     *
     * @param m the requested minimum stratum size. If the given value is less than 2,
     *          the minimum stratum size will default to 2.
     */
    public void setMinStratumSize(int m) {
        this.minStratumSize = Math.max(2, m);
        strataCache.clear();
    }

    /**
     * Sets the number of bins per container along the X and Y axes.
     * Ensures that the value is at least 2.
     *
     * @param b the desired number of bins per container. If the value is less than 2, it defaults to 2.
     */
    public void setBinsPerContXY(int b) {
        this.binsPerContXY = Math.max(2, b);
    }

    /**
     * Sets the maximum number of observed levels allowed per variable.
     * Ensures that the value is at least 4.
     *
     * @param m the proposed maximum number of observed levels per variable. If the
     *          provided value is less than 4, the method will set it to 4.
     */
    public void setMaxObservedLevelsPerVar(int m) {
        this.maxObservedLevelsPerVar = Math.max(4, m);
    }

    /**
     * Sets the maximum number of cells allowed per stratum. The provided value
     * is constrained to be no less than 64.
     *
     * @param m the proposed maximum number of cells per stratum. If the
     *          provided value is less than 64, the method will set it to 64.
     */
    public void setMaxCellsPerStratum(int m) {
        this.maxCellsPerStratum = Math.max(64, m);
    }

    /**
     * Sets whether the maximum value across strata should be used.
     *
     * @param useMaxAcrossStrata true to use the maximum value across strata, false otherwise
     */
    public void setUseMaxAcrossStrata(boolean useMaxAcrossStrata) {
        this.useMaxAcrossStrata = useMaxAcrossStrata;
    }

    /**
     * Retrieves the list of row indices.
     *
     * @return a list of integers representing the row indices
     */
    @Override
    public List<Integer> getRows() {
        return rows;
    }

    /**
     * Sets the rows to be used in the current operation. Validates each row
     * to ensure it meets the required conditions and updates the internal
     * state accordingly.
     *
     * @param rows a list of integers representing the rows. Each row must be:
     *             non-null, non-negative, and within the bounds of available data rows.
     *             If the input list is null, the rows will be set to null and the
     *             cache will be cleared.
     * @throws NullPointerException if any row in the list is null.
     * @throws IllegalArgumentException if any row is negative or out of bounds.
     */
    @Override
    public void setRows(List<Integer> rows) {
        if (rows == null) {
            this.rows = null;
            strataCache.clear();
            return;
        }
        for (int i = 0; i < rows.size(); i++) {
            Integer r = rows.get(i);
            if (r == null) throw new NullPointerException("Row " + i + " is null.");
            if (r < 0) throw new IllegalArgumentException("Row " + i + " is negative.");
            if (r >= data.getNumRows()) throw new IllegalArgumentException("Row " + i + " out of bounds: " + r);
        }
        this.rows = new ArrayList<>(rows);
        strataCache.clear();
    }

    /**
     * Performs an independence test on a subset of variables.
     *
     * @param vars a list of variables (nodes) representing the subset to test for independence.
     * @return an IndependenceTest instance representing the result or state of the independence test for the specified subset.
     */
    @Override
    public IndependenceTest indTestSubset(List<Node> vars) {
        // Same behavior as prior version (safe fallback)
        return this;
    }

    // =========================================================
    // Core math utilities
    // =========================================================

    private static double gTestFromCounts(int[][] counts, int[] rowS, int[] colS, int n) {
        if (n <= 0) return Double.NaN;

        double llr = 0.0;
        int Kx = counts.length;
        int Ky = counts[0].length;

        for (int i = 0; i < Kx; i++) {
            int ri = rowS[i];
            if (ri == 0) continue;

            for (int j = 0; j < Ky; j++) {
                int nij = counts[i][j];
                if (nij == 0) continue;

                int cj = colS[j];
                if (cj == 0) continue;

                double e = (ri * (double) cj) / (double) n;
                if (e <= 0) continue;

                llr += nij * log(nij / e);
            }
        }

        return 2.0 * llr;
    }

    private static Map<String, Integer> indexMap(List<Node> vars) {
        Map<String, Integer> m = new HashMap<>();
        for (int i = 0; i < vars.size(); i++) m.put(vars.get(i).getName(), i);
        return m;
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
        ArrayList<Integer> r = new ArrayList<>(n);
        for (int i = 0; i < n; i++) r.add(i);
        return r;
    }

    private boolean isDiscrete(int col) {
        return variables.get(col) instanceof DiscreteVariable;
    }

    private int[] filterContinuous(int[] cols) {
        int c = 0;
        for (int v : cols) if (!isDiscrete(v)) c++;
        int[] out = new int[c];
        int k = 0;
        for (int v : cols) if (!isDiscrete(v)) out[k++] = v;
        return out;
    }

    private int[] filterDiscrete(int[] cols) {
        int c = 0;
        for (int v : cols) if (isDiscrete(v)) c++;
        int[] out = new int[c];
        int k = 0;
        for (int v : cols) if (isDiscrete(v)) out[k++] = v;
        return out;
    }

    private static int[] range(int n) {
        int[] r = new int[n];
        for (int i = 0; i < n; i++) r[i] = i;
        return r;
    }

    private static long signature(List<Integer> rows) {
        long h = 1469598103934665603L;
        for (int r : rows) {
            h ^= r;
            h *= 1099511628211L;
        }
        return h;
    }

    private static int binIndex(double[] edges, double v) {
        int lo = 0, hi = edges.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (v > edges[mid]) lo = mid + 1;
            else hi = mid;
        }
        return lo;
    }

    private static double[] quantileEdges(double[] x, int bins) {
        bins = max(2, bins);
        double[] a = Arrays.copyOf(x, x.length);
        Arrays.sort(a);

        double[] edges = new double[bins - 1];
        int n = a.length;

        for (int b = 1; b < bins; b++) {
            double q = b / (double) bins;
            int idx = (int) floor(q * (n - 1));
            idx = min(max(0, idx), n - 1);
            edges[b - 1] = a[idx];
        }
        return edges;
    }

    private static void zscoreColumnPreserveNaN(double[] in, double[] out) {
        double sum = 0.0, sum2 = 0.0;
        int n = 0;
        for (double v : in) {
            if (Double.isNaN(v)) continue;
            sum += v;
            sum2 += v * v;
            n++;
        }
        if (n < 2) {
            System.arraycopy(in, 0, out, 0, in.length);
            return;
        }
        double mean = sum / n;
        double var = (sum2 - n * mean * mean) / (n - 1.0);
        double sd = sqrt(max(1e-12, var));
        for (int i = 0; i < in.length; i++) {
            double v = in[i];
            out[i] = Double.isNaN(v) ? Double.NaN : (v - mean) / sd;
        }
    }

    // fast-ish mixing for hashing discrete levels
    private static int mix32(int x) {
        x ^= (x >>> 16);
        x *= 0x7feb352d;
        x ^= (x >>> 15);
        x *= 0x846ca68b;
        x ^= (x >>> 16);
        return x;
    }

    // =========================================================
    // Small utility classes (strata cache)
    // =========================================================

    private record StrataKey(int[] zIdx, long rowsSig, int bins, int minSize) {
        StrataKey {
            zIdx = (zIdx == null) ? new int[0] : Arrays.copyOf(zIdx, zIdx.length);
        }

        @Override
        public int hashCode() {
            int h = Arrays.hashCode(zIdx);
            h = 31 * h + Long.hashCode(rowsSig);
            h = 31 * h + Integer.hashCode(bins);
            h = 31 * h + Integer.hashCode(minSize);
            return h;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof StrataKey k)) return false;
            return rowsSig == k.rowsSig && bins == k.bins && minSize == k.minSize && Arrays.equals(zIdx, k.zIdx);
        }
    }

    private static final class StratumSignature {
        final int[] parts;
        final int hash;

        StratumSignature(int[] parts) {
            this.parts = parts;
            this.hash = Arrays.hashCode(parts);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object o) {
            return (o instanceof StratumSignature s) && Arrays.equals(parts, s.parts);
        }
    }

    private static final class IntArrayList {
        private int[] a = new int[16];
        private int n = 0;

        void add(int v) {
            if (n == a.length) a = Arrays.copyOf(a, a.length * 2);
            a[n++] = v;
        }

        int size() {
            return n;
        }

        int[] toArray() {
            return Arrays.copyOf(a, n);
        }
    }
}