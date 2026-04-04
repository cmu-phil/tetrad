package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TMath;
import edu.cmu.tetrad.util.TetradLogger;

import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static edu.cmu.tetrad.util.TMath.*;
import static java.lang.Double.NaN;

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
 *   <li>Aggregate across strata by quantile across strata (soft minimax); q=1 recovers max.</li>
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
 * <p>
 * Neykov, M., Balakrishnan, S., &amp; Wasserman, L. (2021). Minimax optimal conditional independence testing. The Annals of Statistics, 49(4), 2151-2177.
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

    // kept for compatibility; not used in quantile aggregation
    private boolean useMaxAcrossStrata = false;

    // behavior
    private boolean verbose = false;

    // optional row restriction
    private List<Integer> rows = null;

    // soften minimax by using a high quantile across strata (q=1.0 recovers max)
    private double qMinimax = 0.90;   // default: 90th percentile

    /**
     * Constructs a MinimaxCITest object using the given dataset and significance level (alpha).
     *
     * @param data  the dataset to be used for independence testing; must not be null.
     * @param alpha the significance level for testing; determines the threshold below which a dependency is considered
     *              statistically significant.
     * @throws NullPointerException if the provided data is null.
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
                Arrays.fill(raw[j], NaN);
            } else {
                for (int r = 0; r < n; r++) raw[j][r] = data.getDouble(r, j);
            }
        }

        this.zCols = new double[p][n];
        for (int j = 0; j < p; j++) {
            if (isDiscrete(j)) Arrays.fill(zCols[j], NaN);
            else zscoreColumnPreserveNaN(raw[j], zCols[j]);
        }
    }

    // =========================================================
    // IndependenceTest
    // =========================================================

    private static double statCorrSqGroupsQuantile(double[] xArr,
                                                   double[] yArr,
                                                   int[][] groups,
                                                   double q) {

        if (groups.length == 0) return 0.0;

        double[] stats = new double[groups.length];
        int k = 0;

        for (int[] g : groups) {
            int m = g.length;
            if (m < 3) continue;

            double mx = 0.0, my = 0.0;
            for (int idx : g) {
                mx += xArr[idx];
                my += yArr[idx];
            }
            mx /= m;
            my /= m;

            double sxx = 0.0, syy = 0.0, sxy = 0.0;
            for (int idx : g) {
                double dx = xArr[idx] - mx;
                double dy = yArr[idx] - my;
                sxx += dx * dx;
                syy += dy * dy;
                sxy += dx * dy;
            }
            if (sxx <= 0 || syy <= 0) continue;

            double r = sxy / sqrt(sxx * syy);
            double T = (m - 1.0) * r * r;
            if (Double.isFinite(T)) stats[k++] = T;
        }

        if (k == 0) return 0.0;
        if (k == 1) return stats[0];

        double qq = TMath.min(1.0, TMath.max(0.0, q));
        int idx = (int) TMath.floor(qq * (k - 1));

        // nth-element (QuickSelect)
        return quickSelect(stats, 0, k - 1, idx);
    }

    private static double quickSelect(double[] a, int left, int right, int k) {
        while (true) {
            if (left == right) return a[left];

            int pivotIndex = partition(a, left, right, (left + right) >>> 1);

            if (k == pivotIndex) return a[k];
            if (k < pivotIndex) right = pivotIndex - 1;
            else left = pivotIndex + 1;
        }
    }

    private static int partition(double[] a, int left, int right, int pivotIndex) {
        double pivotValue = a[pivotIndex];
        swap(a, pivotIndex, right);
        int storeIndex = left;

        for (int i = left; i < right; i++) {
            if (a[i] < pivotValue) {
                swap(a, storeIndex, i);
                storeIndex++;
            }
        }
        swap(a, right, storeIndex);
        return storeIndex;
    }

    // =========================================================
    // Continuous-continuous case: Orig behavior (stratify + corr^2)
    // =========================================================

    private static void swap(double[] a, int i, int j) {
        double tmp = a[i];
        a[i] = a[j];
        a[j] = tmp;
    }

    private static double quantile(double[] a, double q) {
        int n = a.length;
        if (n == 0) return NaN;
        if (n == 1) return a[0];

        q = TMath.max(0.0, TMath.min(1.0, q));
        int k = (int) TMath.floor(q * (n - 1)); // 0..n-1

        // quickselect instead of full sort
        return quickSelect(a, 0, n - 1, k);
    }

    private static double gTestFromCounts(int[][] counts, int[] rowS, int[] colS, int n) {
        if (n <= 0) return NaN;

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
            out[i] = Double.isNaN(v) ? NaN : (v - mean) / sd;
        }
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

    // =========================================================
    // Mixed/general case: stratified G-test with cached plans
    // =========================================================

    private static double[] quantileEdges(double[] x, int bins) {
        bins = TMath.max(2, bins);
        double[] a = Arrays.copyOf(x, x.length);
        Arrays.sort(a);

        int n = a.length;
        double[] edges = new double[bins - 1];

        double last = Double.NEGATIVE_INFINITY;
        for (int b = 1; b < bins; b++) {
            double q = b / (double) bins;
            int idx = (int) TMath.floor(q * (n - 1));
            idx = TMath.min(TMath.max(0, idx), n - 1);

            double e = a[idx];

            // nondecreasing + nudge on ties
            if (e <= last) {
                int j = idx;
                while (j + 1 < n && a[j] <= last) j++;
                e = a[j];
            }

            edges[b - 1] = e;
            last = e;
        }

        return edges;
    }

    private static int approxUniqueCount(int[] a, int stopAfter) {
        HashSet<Integer> s = new HashSet<>();
        for (int v : a) {
            s.add(v);
            if (s.size() >= stopAfter) return s.size();
        }
        return s.size();
    }

    /**
     * Compress observed discrete levels to at most {@code maxLevels} categories by keeping
     * the top-(maxLevels-1) most frequent levels and mapping all others to a single OTHER bucket.
     * Returns codes in 0..K-1 where K == maxLevels (when compression happens).
     */
    private static int[] compressTopLPlusOther(int[] levels, int maxLevels) {
        if (maxLevels < 2) throw new IllegalArgumentException("maxLevels must be >= 2");

        HashMap<Integer, Integer> freq = new HashMap<>();
        for (int v : levels) freq.merge(v, 1, Integer::sum);

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
            return codes;
        }

        int keep = maxLevels - 1;

        ArrayList<Map.Entry<Integer, Integer>> entries = new ArrayList<>(freq.entrySet());
        entries.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

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
        return codes;
    }

    /**
     * Deterministically down-bin categorical codes in 0..oldK-1 to 0..newK-1 by grouping
     * adjacent codes.
     */
    private static int[] downBinDeterministic(int[] codes, int oldK, int newK) {
        if (newK >= oldK) return codes;
        int[] out = new int[codes.length];
        for (int i = 0; i < codes.length; i++) {
            int c = codes[i];
            int b = (int) ((long) c * (long) newK / (long) oldK);
            if (b >= newK) b = newK - 1;
            out[i] = b;
        }
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

    // =========================================================
    // Z-strata
    // =========================================================

    private static Map<String, Integer> indexMap(List<Node> vars) {
        Map<String, Integer> m = new HashMap<>();
        for (int i = 0; i < vars.size(); i++) m.put(vars.get(i).getName(), i);
        return m;
    }

    /**
     * Checks the conditional independence between two variables given a conditioning set of variables
     * using statistical testing. The test determines whether the association between the variables
     * is statistically significant or not, considering a specified significance level.
     *
     * @param x the first variable (Node) involved in the independence test; must not be null.
     * @param y the second variable (Node) involved in the independence test; must not be null.
     * @param z the conditioning set of variables used to test conditional independence;
     *          may be empty, but must not be null.
     * @return an IndependenceResult containing details of the independence test, such as whether
     * the variables are independent, the p-value of the test, and other related statistics.
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

    // =========================================================
    // Missingness / row filtering
    // =========================================================

    /**
     * Computes the p-value for testing the conditional independence between two variables (x and y)
     * given a conditioning set of variables (z). The method implements statistical testing,
     * taking into account the data structure, variable types, and the specified constraints.
     * The p-value indicates the strength of the evidence against the null hypothesis
     * of conditional independence.
     *
     * @param x the first variable (Node) involved in the test; must not be null.
     * @param y the second variable (Node) involved in the test; must not be null.
     * @param z the conditioning set of variables used for the test;
     *          may be empty, but must not be null.
     * @return the computed p-value as a double. A lower p-value suggests stronger evidence
     * against the null hypothesis of conditional independence.
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

        if (n < 20) return NaN; // conservative guard

        int[][] strata = getStrata(iz, useRows);
        if (strata.length == 0) return NaN;

        long seed = permSeed ^ ix ^ (iy * 1315423911L) ^ Arrays.hashCode(iz);

        // Fast path: continuous-continuous with continuous-only conditioning
        if (isContContContZ(ix, iy, iz)) {
            return pValueContContOrig(ix, iy, useRows, strata, permutations, seed);
        }

        // Mixed/general path: stratified G-test with within-stratum permutation
        return pValueMixedGTest(ix, iy, useRows, strata, permutations, seed);
    }

    private boolean isContContContZ(int ix, int iy, int[] iz) {
        if (isDiscrete(ix) || isDiscrete(iy)) return false;
        for (int j : iz) if (isDiscrete(j)) return false;
        return true;
    }

    // =========================================================
    // Public API / setters / interface
    // =========================================================

    private double pValueContContOrig(int ix, int iy,
                                      List<Integer> useRows, int[][] strata,
                                      int B, long seed) {

        final int n = useRows.size();

        double[] xArr = new double[n];
        double[] yArr = new double[n];
        for (int i = 0; i < n; i++) {
            int r = useRows.get(i);
            xArr[i] = zCols[ix][r];
            yArr[i] = zCols[iy][r];
        }

        // observed: quantile aggregation across strata
        double obs = statCorrSqGroupsQuantile(xArr, yArr, strata, qMinimax);

        double[] yPerm = Arrays.copyOf(yArr, n);

        int ge = 0;
        int BB = TMath.max(50, B);

        for (int b = 0; b < BB; b++) {
            System.arraycopy(yArr, 0, yPerm, 0, n);

            // shuffle Y within each stratum
            for (int[] g : strata) {
                for (int i = g.length - 1; i > 0; i--) {
                    int j = RandomUtil.getInstance().nextInt(i + 1);
                    int ii = g[i], jj = g[j];
                    double tmp = yPerm[ii];
                    yPerm[ii] = yPerm[jj];
                    yPerm[jj] = tmp;
                }
            }

            double t = statCorrSqGroupsQuantile(xArr, yPerm, strata, qMinimax);
            if (t >= obs) ge++;
        }

        return (ge + 1.0) / (BB + 1.0);
    }

    private double pValueMixedGTest(int ix, int iy,
                                    List<Integer> useRows, int[][] strata,
                                    int B, long seed) {

        GroupPlan[] plans = buildPlans(ix, iy, useRows, strata);
        if (plans.length == 0) return NaN;

        // Observed statistic: quantile across strata (soft minimax)
        double tObs = aggregateQuantile(plans, qMinimax);
        if (!Double.isFinite(tObs)) return 1.0;

        int BB = TMath.max(50, B);

        int ge = 0;
        int valid = 0;

        for (int b = 0; b < BB; b++) {
            double tPerm = aggregateQuantile(plans, qMinimax);
            if (!Double.isFinite(tPerm)) continue;
            valid++;
            if (tPerm >= tObs) ge++;
        }

//        if (valid == 0) {
//            TetradLogger.getInstance().log(
//                    "MinimaxCITest: valid==0 in permutation test for (" +
//                            variables.get(ix).getName() + " _||_ " + variables.get(iy).getName() +
//                            " | Z=" + Arrays.toString(strataSummaryOrZIdxHere) + "). Returning 1.0"
//            );
//        }

        if (valid == 0) return Double.NaN;
        return (ge + 1.0) / (valid + 1.0);

//        return (ge + 1.0) / (valid + 1.0);
    }

    private double aggregateQuantile(GroupPlan[] plans, double q) {
        double[] stats = new double[plans.length];
        int m = 0;

        for (GroupPlan gp : plans) {
            double ts = gp.statistic(); // observed if rng==null; permuted if rng!=null
            if (!Double.isFinite(ts)) continue;
            stats[m++] = ts;
        }

        if (m == 0) return NaN;
        if (m < stats.length) stats = Arrays.copyOf(stats, m);

        return quantile(stats, q);
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
        int binsXY = TMath.max(2, TMath.min(binsPerContXY, (int) TMath.floor(TMath.sqrt(m))));

        // Also enforce maxCellsPerStratum for contingency tables
        int maxBinsByCells = TMath.max(2, (int) TMath.floor(TMath.sqrt(maxCellsPerStratum)));
        binsXY = TMath.min(binsXY, maxBinsByCells);

        Cat xCat = buildCategories(ix, useRows, g, binsXY);
        Cat yCat = buildCategories(iy, useRows, g, binsXY);
        if (xCat == null || yCat == null) return null;

        if (xCat.K < 2 || yCat.K < 2) {
            return new GroupPlan(xCat.codes, yCat.codes, xCat.K, yCat.K, true);
        }

        long cells = (long) xCat.K * (long) yCat.K;
        if (cells > maxCellsPerStratum) {
            // Prefer structured reductions over hashing:
            int K = TMath.max(2, (int) TMath.floor(TMath.sqrt(maxCellsPerStratum)));

            int[] x = (xCat.K > K) ? downBinDeterministic(xCat.codes, xCat.K, K) : xCat.codes;
            int[] y = (yCat.K > K) ? downBinDeterministic(yCat.codes, yCat.K, K) : yCat.codes;

            return new GroupPlan(x, y, TMath.min(xCat.K, K), TMath.min(yCat.K, K), false);
        }

        return new GroupPlan(xCat.codes, yCat.codes, xCat.K, yCat.K, false);
    }

    private Cat buildCategories(int varIdx, List<Integer> useRows, int[] g, int effBinsXY) {
        if (isDiscrete(varIdx)) {
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

            int unique = approxUniqueCount(lev, maxObservedLevelsPerVar + 1);

            if (unique > maxObservedLevelsPerVar) {
                int K = TMath.max(2, maxObservedLevelsPerVar);
                int[] codes = compressTopLPlusOther(lev, K);
                return new Cat(codes, K);
            }

            HashMap<Integer, Integer> map = new HashMap<>(unique * 2);
            int next = 0;
            int[] codes = new int[lev.length];
            for (int i = 0; i < lev.length; i++) {
                Integer idx = map.get(lev[i]);
                if (idx == null) {
                    idx = next++;
                    map.put(lev[i], idx);
                }
                codes[i] = idx;
            }
            return new Cat(codes, next);
        } else {
            // Continuous: quantile binning within this stratum
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

            int bins = TMath.max(2, effBinsXY);
            double[] edges = quantileEdges(vals, bins);

            int[] codes = new int[vals.length];
            for (int i = 0; i < vals.length; i++) codes[i] = binIndex(edges, vals[i]);
            return new Cat(codes, bins);
        }
    }

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
            buckets.computeIfAbsent(sig, s -> new IntArrayList()).add(i); // i is index in useRows-space
        }

        ArrayList<int[]> groups = new ArrayList<>();
        for (IntArrayList lst : buckets.values()) {
            if (lst.size() >= minSize) groups.add(lst.toArray());
        }

        return groups.toArray(new int[0][]);
    }

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

    /**
     * Retrieves the list of variables associated with this object.
     *
     * @return a list of variables, where each variable is represented as a Node.
     */
    @Override
    public List<Node> getVariables() {
        return variables;
    }

    /**
     * Retrieves the dataset associated with this object.
     *
     * @return the dataset used for independence testing, represented as a {@code DataSet} object.
     */
    @Override
    public DataSet getData() {
        return data;
    }

    /**
     * Retrieves the list of datasets associated with this object.
     *
     * @return a list of datasets, where each dataset is represented as a {@code DataSet} object.
     */
    @Override
    public List<DataSet> getDataSets() {
        return List.of(data);
    }

    /**
     * Retrieves the significance level (alpha) used for statistical testing in this object.
     * Alpha represents the threshold below which a dependency is considered statistically significant.
     *
     * @return the significance level as a double.
     */
    @Override
    public double getAlpha() {
        return alpha;
    }

    /**
     * Sets the significance level (alpha) for statistical testing in this object.
     * Alpha represents the threshold below which a dependency is considered statistically significant.
     *
     * @param alpha This level.
     */
//    public void setAlpha(double alpha) {
//        if (alpha < 0 || alpha > 1) throw new IllegalArgumentException("alpha must be in [0,1]");
//        this.alpha = alpha;
//    }
    public void setAlpha(double alpha) {
        if (alpha < 0 || alpha > 1) throw new IllegalArgumentException("alpha must be in [0,1]");
        this.alpha = alpha;

        int minB = (alpha > 0.0) ? ((int) TMath.ceil(1.0 / alpha) - 1) : Integer.MAX_VALUE;
        NumberFormat nf = NumberFormat.getNumberInstance();

        if (this.permutations < minB) {
            int oldB = this.permutations;
            this.permutations = minB;

            TetradLogger.getInstance().log(
                    "MinimaxCITest: increased permutations from " + oldB + " to " + this.permutations +
                            " so alpha=" + nf.format(alpha) + " is attainable (p-floor=1/(B+1))."
            );
        }
    }

    /**
     * Retrieves the sample size from the dataset associated with this object.
     *
     * @return the number of rows in the dataset, representing the sample size.
     */
    @Override
    public int getSampleSize() {
        return data.getNumRows();
    }

    /**
     * Determines whether verbose mode is enabled for the current object.
     * When verbose mode is enabled, additional information or logging
     * may be provided during execution.
     *
     * @return true if verbose mode is enabled; false otherwise.
     */
    @Override
    public boolean isVerbose() {
        return verbose;
    }

    /**
     * Sets the verbosity mode of the application or process.
     *
     * @param verbose a boolean value where {@code true} enables verbose output
     *                and {@code false} disables it.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Sets the value of permutations based on the input parameter.
     * Ensures that the value is at least 50.
     *
     * @param B the input value used to set the permutations.
     *          If B is less than 50, the permutations will be set to 50.
     *          Otherwise, it will be set to the value of B.
     */
//    public void setPermutations(int B) {
//        this.permutations = TMath.max(50, B);
//    }
    public void setPermutations(int B) {
        // keep your existing floor
        int requested = TMath.max(50, B);

        // enforce p-value resolution for current alpha
        int minB = (alpha > 0.0) ? ((int) TMath.ceil(1.0 / alpha) - 1) : Integer.MAX_VALUE;
        NumberFormat nf = NumberFormat.getNumberInstance();

        if (requested < minB) {
            int old = requested;
            requested = minB;

            TetradLogger.getInstance().log(
                    "MinimaxCITest: increased permutations from " + old + " to " + requested +
                            " so alpha=" + nf.format(alpha) + " is attainable (p-floor=1/(B+1))."
            );
        }

        this.permutations = requested;
    }

    /**
     * Sets the permutation seed to the specified value.
     *
     * @param s the new value for the permutation seed
     */
    public void setPermSeed(long s) {
        this.permSeed = s;
    }

    // =========================================================
    // Index helpers
    // =========================================================

    /**
     * Sets the number of bins per contour layer (Z dimension).
     * Ensures that the number of bins is at least 2.
     * Also clears the cached strata data to reflect the updated configuration.
     *
     * @param b the number of bins to be set for each contour layer (Z dimension)
     */
    public void setBinsPerContZ(int b) {
        this.binsPerContZ = TMath.max(2, b);
        strataCache.clear();
    }

    /**
     * Sets the minimum size for a stratum. If the provided size is less than 2,
     * the minimum size will default to 2. This method also clears the strata cache.
     *
     * @param m the desired minimum size for a stratum. Values less than 2 will default to 2.
     */
    public void setMinStratumSize(int m) {
        this.minStratumSize = TMath.max(2, m);
        strataCache.clear();
    }

    /**
     * Sets the number of bins per container along the X and Y axes.
     * The value will be constrained to a minimum of 2.
     *
     * @param b the desired number of bins per container along the X and Y axes
     */
    public void setBinsPerContXY(int b) {
        this.binsPerContXY = TMath.max(2, b);
    }

    // =========================================================
    // Core math utilities (G-test, binning, zscore)
    // =========================================================

    /**
     * Sets the qMinimax value, ensuring it is clamped between 0.50 and 1.0.
     *
     * @param q the input value to set as qMinimax. Values less than 0.50 will be adjusted to 0.50,
     *          and values greater than 1.0 will be adjusted to 1.0.
     */
    public void setQMinimax(double q) {
        this.qMinimax = TMath.max(0.50, TMath.min(1.0, q));
    }

    /**
     * Sets the maximum number of observed levels allowed per variable.
     * The value is constrained to a minimum of 4.
     *
     * @param m The desired maximum number of observed levels per variable.
     *          If the specified value is less than 4, it will default to 4.
     */
    public void setMaxObservedLevelsPerVar(int m) {
        this.maxObservedLevelsPerVar = TMath.max(4, m);
    }

    /**
     * Sets the maximum number of cells allowed per stratum.
     * The value is constrained to a minimum of 64.
     *
     * @param m The desired maximum number of cells per stratum.
     *          If the specified value is less than 64, it will default to 64.
     */
    public void setMaxCellsPerStratum(int m) {
        this.maxCellsPerStratum = TMath.max(64, m);
    }

    /**
     * Sets the maximum number of cells allowed per stratum.
     * The value is constrained to a minimum of 64.
     *
     * @param useMaxAcrossStrata The desired setting for using the maximum across strata.
     */
    public void setUseMaxAcrossStrata(boolean useMaxAcrossStrata) {
        this.useMaxAcrossStrata = useMaxAcrossStrata;
    }

    /**
     * Retrieves the list of rows.
     *
     * @return a list of integers representing the rows
     */
    @Override
    public List<Integer> getRows() {
        return rows;
    }

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
     * Performs an independence test on a subset of variables and returns the result.
     * This method tests the independence of the specified subset of variables.
     *
     * @param vars the list of variables to be tested for independence
     * @return the result of the independence test
     */
    @Override
    public IndependenceTest indTestSubset(List<Node> vars) {
        return this;
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

    // =========================================================
    // Type utilities
    // =========================================================

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

    // =========================================================
    // Small utility classes (categories, group plan, strata cache)
    // =========================================================

    private static final class Cat {
        final int[] codes; // 0..K-1
        final int K;

        Cat(int[] codes, int K) {
            this.codes = codes;
            this.K = K;
        }
    }

    /**
     * Per-stratum plan: fixed observed categories for X,Y; permutation shuffles Y categories
     * within the stratum (i.e., within this group).
     */
    private static final class GroupPlan {
        final int[] xObs;     // observed X codes (0..Kx-1)
        final int[] yObs;     // observed Y codes (0..Ky-1)
        final int Kx, Ky;
        final boolean constant;

        // scratch buffers (allocated once)
        final int[] yPerm;
        final int[][] counts;
        final int[] rowS;
        final int[] colS;

        GroupPlan(int[] xObs, int[] yObs, int Kx, int Ky, boolean constant) {
            this.xObs = xObs;
            this.yObs = yObs;
            this.Kx = Kx;
            this.Ky = Ky;
            this.constant = constant;

            this.yPerm = new int[yObs.length];
            this.counts = new int[Kx][Ky];
            this.rowS = new int[Kx];
            this.colS = new int[Ky];
        }

        double statistic() {
            if (constant) return 0.0;

            final int n = xObs.length;

            // yPerm := observed or permuted
            System.arraycopy(yObs, 0, yPerm, 0, n);
            for (int i = n - 1; i > 0; i--) {
                int j = RandomUtil.getInstance().nextInt(i + 1);
                int tmp = yPerm[i];
                yPerm[i] = yPerm[j];
                yPerm[j] = tmp;
            }

            // clear
            for (int i = 0; i < Kx; i++) {
                Arrays.fill(counts[i], 0);
                rowS[i] = 0;
            }
            Arrays.fill(colS, 0);

            // fill contingency of (xObs, yPerm)
            for (int i = 0; i < n; i++) {
                int xi = xObs[i];
                int yi = yPerm[i];
                if (xi < 0 || xi >= Kx || yi < 0 || yi >= Ky) continue;
                counts[xi][yi]++;
                rowS[xi]++;
                colS[yi]++;
            }

            return gTestFromCounts(counts, rowS, colS, n);
        }
    }

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