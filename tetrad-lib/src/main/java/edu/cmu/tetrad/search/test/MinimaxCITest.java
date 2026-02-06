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
 * "Gold standard fallback" conditional independence test for mixed data (continuous + discrete).
 * <p>
 * Core idea (robust + nonparametric):
 * - Stratify on Z (exact match for discrete Z, quantile bins for continuous Z).
 * - Within each stratum, discretize X and Y locally:
 * * discrete vars: use observed levels (compressed)
 * * continuous vars: quantile bins within the stratum
 * - Compute a per-stratum dependence statistic using a contingency-table G-test (likelihood ratio).
 * - Aggregate by minimax: T = max_s T_s across strata (worst-case conditional dependence).
 * - Obtain p-value by within-stratum permutation of X (shuffling X within each stratum).
 * <p>
 * Why this is a great fallback:
 * - No regression, no optimization, no convergence knobs.
 * - Works under wild nonlinearities/general noise.
 * - Permutation gives finite-sample calibration under the stratified null.
 * <p>
 * Notes:
 * - If Z is empty, there is a single stratum containing all rows.
 * - Strata that are too small are dropped.
 * - Rows with missing values in {X,Y,Z} are dropped automatically per test.
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

    // local discretization of X,Y within a stratum
    private int binsPerContXY = 6;

    // safety bounds (avoid pathological huge contingency tables from high-cardinality discrete vars)
    private int maxObservedLevelsPerVar = 32;   // per stratum; beyond this, we down-bin discrete by hashing
    private int maxCellsPerStratum = 1024;      // cap Kx * Ky (post-compression)
    private boolean useMaxAcrossStrata = false;

    // behavior
    private boolean verbose = false;

    // optional row restriction
    private List<Integer> rows = null;

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

    private static int sum(int[] a) {
        int s = 0;
        for (int v : a) s += v;
        return s;
    }

    // =========================================================
    // Minimax statistic: max over strata of per-stratum G-test
    // =========================================================

    private static Map<String, Integer> indexMap(List<Node> vars) {
        Map<String, Integer> m = new HashMap<>();
        for (int i = 0; i < vars.size(); i++) m.put(vars.get(i).getName(), i);
        return m;
    }

    private static int[] identity(int n) {
        int[] p = new int[n];
        for (int i = 0; i < n; i++) p[i] = i;
        return p;
    }

    // ---------------- per-case G-tests ----------------

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

    // ---------------- fallback hashed down-binning for high-cardinality discrete ----------------

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
    // Contingency-table G-test
    // =========================================================

    public double getPValue(Node x, Node y, Set<Node> z) {
        Objects.requireNonNull(x);
        Objects.requireNonNull(y);
        Objects.requireNonNull(z);
        if (x.equals(y)) return 1.0;

        int ix = idx(x);
        int iy = idx(y);
        int[] iz = idxSorted(z);

        // drop rows with missing in {X,Y,Z}
        List<Integer> baseRows = listRows();
        List<Integer> useRows = rowsCompleteFor(ix, iy, iz, baseRows);

        int n = useRows.size();
        if (n < 20) return 0.0; // too little data: treat as dependent (conservative)

        int[][] strata = getStrata(iz, useRows);
        if (strata.length == 0) return 0.0;

        // observed statistic (minimax over strata)
        double tObs = minimaxStatistic(ix, iy, iz, useRows, strata, null);
        if (!Double.isFinite(tObs)) return 1.0;

        // permutation: shuffle X within each stratum
        SplittableRandom rng = new SplittableRandom(
                permSeed ^ (ix * 1315423911L) ^ (iy * 2654435761L) ^ Arrays.hashCode(iz));

        int B = Math.max(50, permutations);
        int ge = 0;
        int valid = 0;

        int[] permMap = identity(n);

        for (int b = 0; b < B; b++) {
            // reset identity
            for (int i = 0; i < n; i++) permMap[i] = i;

            // shuffle within each stratum (indices are in useRows-space)
            for (int[] g : strata) {
                for (int i = g.length - 1; i > 0; i--) {
                    int j = rng.nextInt(i + 1);
                    int ii = g[i], jj = g[j];
                    int tmp = permMap[ii];
                    permMap[ii] = permMap[jj];
                    permMap[jj] = tmp;
                }
            }

            double t = minimaxStatistic(ix, iy, iz, useRows, strata, permMap);
            if (!Double.isFinite(t)) continue;

            valid++;
            if (t >= tObs) ge++;

            // light early-stop (optional but helpful)
            if (valid >= 25) {
                double pLower = (ge + 1.0) / (valid + 1.0);
                double pUpper = (ge + (B - valid) + 1.0) / (B + 1.0);
                if (pLower > alpha) break;
                if (pUpper <= alpha) break;
            }
        }

        return (ge + 1.0) / (valid + 1.0);
    }

    /**
     * If permMap != null, X is read from permuted row indices within useRows-space.
     * Y is always read from the unpermuted row i.
     */
    private double minimaxStatistic(int xIdx, int yIdx, int[] zIdx,
                                    List<Integer> useRows, int[][] strata,
                                    int[] permMap) {

        if (useMaxAcrossStrata) {
            double maxT = 0.0;

            for (int[] g : strata) {
                double ts = stratumStatistic(xIdx, yIdx, useRows, g, permMap);
                if (!Double.isFinite(ts)) continue;
                if (ts > maxT) maxT = ts;
            }

            return maxT;
        } else {
            double sumT = 0.0;

            for (int[] g : strata) {
                double ts = stratumStatistic(xIdx, yIdx, useRows, g, permMap);
                if (!Double.isFinite(ts)) continue;

                // Optional mild weighting by stratum size (helps match “accumulate evidence” feel):
                // sumT += (g.length - 1.0) * ts;
                // If you don’t want weighting, just:
                sumT += ts;
            }

            return sumT;
        }
    }

    // =========================================================
    // Z-strata
    // =========================================================

    private double stratumStatistic(int xIdx, int yIdx,
                                    List<Integer> useRows,
                                    int[] groupUseRowsSpace,
                                    int[] permMap) {
        // Build (possibly permuted) paired samples within this stratum:
        // i indexes useRows-space.
        final int m = groupUseRowsSpace.length;
        if (m < minStratumSize) return Double.NaN;

        // Gather X and Y values for this stratum (with missing checks).
        // We will discretize locally.
        if (isDiscrete(xIdx)) {
            return isDiscrete(yIdx)
                    ? gTestDiscreteDiscrete(xIdx, yIdx, useRows, groupUseRowsSpace, permMap)
                    : gTestDiscreteContinuous(xIdx, yIdx, useRows, groupUseRowsSpace, permMap);
        } else {
            return isDiscrete(yIdx)
                    ? gTestContinuousDiscrete(xIdx, yIdx, useRows, groupUseRowsSpace, permMap)
                    : gTestContinuousContinuous(xIdx, yIdx, useRows, groupUseRowsSpace, permMap);
        }
    }

    private double gTestDiscreteDiscrete(int xIdx, int yIdx,
                                         List<Integer> useRows, int[] g, int[] permMap) {
        // compress observed discrete levels within this stratum
        LevelMap xm = new LevelMap(maxObservedLevelsPerVar);
        LevelMap ym = new LevelMap(maxObservedLevelsPerVar);

        // First pass: map levels
        int n = 0;
        for (int ii : g) {
            int rowY = useRows.get(ii);

            int rowX = rowY;
            if (permMap != null) rowX = useRows.get(permMap[ii]);

            int xLev = data.getInt(rowX, xIdx);
            int yLev = data.getInt(rowY, yIdx);
            if (xLev == DiscreteVariable.MISSING_VALUE) continue;
            if (yLev == DiscreteVariable.MISSING_VALUE) continue;

            xm.put(xLev);
            ym.put(yLev);
            n++;
        }
        if (n < minStratumSize) return Double.NaN;

        int Kx = xm.size();
        int Ky = ym.size();
        if (Kx < 2 || Ky < 2) return 0.0;

        // Cap table size
        if ((long) Kx * (long) Ky > maxCellsPerStratum) {
            // down-bin by hashing to binsPerContXY (small) to keep robustness
            return gTestHashedDiscreteDiscrete(xIdx, yIdx, useRows, g, permMap, binsPerContXY);
        }

        int[][] counts = new int[Kx][Ky];
        int[] rowS = new int[Kx];
        int[] colS = new int[Ky];

        for (int ii : g) {
            int rowY = useRows.get(ii);
            int rowX = rowY;
            if (permMap != null) rowX = useRows.get(permMap[ii]);

            int xLev = data.getInt(rowX, xIdx);
            int yLev = data.getInt(rowY, yIdx);
            if (xLev == DiscreteVariable.MISSING_VALUE) continue;
            if (yLev == DiscreteVariable.MISSING_VALUE) continue;

            int xi = xm.getIndex(xLev);
            int yi = ym.getIndex(yLev);
            if (xi < 0 || yi < 0) continue;

            counts[xi][yi]++;
            rowS[xi]++;
            colS[yi]++;
        }

        return gTestFromCounts(counts, rowS, colS, sum(rowS));
    }

    // =========================================================
    // Missingness / row filtering
    // =========================================================

    private double gTestDiscreteContinuous(int xDiscIdx, int yContIdx,
                                           List<Integer> useRows, int[] g, int[] permMap) {
        // Y is continuous: build local bins for Y within this stratum.
        double[] yVals = collectCont(yContIdx, useRows, g, null);
        if (yVals.length < minStratumSize) return Double.NaN;
        double[] yEdges = quantileEdges(yVals, binsPerContXY);

        LevelMap xm = new LevelMap(maxObservedLevelsPerVar);
        int n = 0;

        // First pass: collect observed X levels (X may be permuted, but that's fine;
        // it just changes which X pairs with each Y under permutation).
        for (int ii : g) {
            int rowY = useRows.get(ii);
            int rowX = rowY;
            if (permMap != null) rowX = useRows.get(permMap[ii]);

            int xLev = data.getInt(rowX, xDiscIdx);
            if (xLev == DiscreteVariable.MISSING_VALUE) continue;

            double yv = data.getDouble(rowY, yContIdx);
            if (Double.isNaN(yv)) continue;

            xm.put(xLev);
            n++;
        }
        if (n < minStratumSize) return Double.NaN;

        int Kx = xm.size();
        int Ky = binsPerContXY;

        if (Kx < 2) return 0.0;

        if ((long) Kx * (long) Ky > maxCellsPerStratum) {
            return gTestHashedDiscreteContinuous(xDiscIdx, yContIdx, useRows, g, permMap, binsPerContXY);
        }

        int[][] counts = new int[Kx][Ky];
        int[] rowS = new int[Kx];
        int[] colS = new int[Ky];

        for (int ii : g) {
            int rowY = useRows.get(ii);
            int rowX = rowY;
            if (permMap != null) rowX = useRows.get(permMap[ii]);

            int xLev = data.getInt(rowX, xDiscIdx);
            if (xLev == DiscreteVariable.MISSING_VALUE) continue;

            double yv = data.getDouble(rowY, yContIdx);
            if (Double.isNaN(yv)) continue;

            int xi = xm.getIndex(xLev);
            if (xi < 0) continue;

            int yi = binIndex(yEdges, yv);

            counts[xi][yi]++;
            rowS[xi]++;
            colS[yi]++;
        }

        return gTestFromCounts(counts, rowS, colS, sum(rowS));
    }

    private double gTestContinuousDiscrete(int xContIdx, int yDiscIdx,
                                           List<Integer> useRows, int[] g, int[] permMap) {
        // X is continuous (possibly permuted): collect X values with permutation applied
//        double[] xVals = collectContPermuted(xContIdx, useRows, g, permMap);
//        if (xVals.length < minStratumSize) return Double.NaN;
//        double[] xEdges = quantileEdges(xVals, binsPerContXY);

        double[] xValsObs = collectContPermuted(xContIdx, useRows, g, null); // OBSERVED, not permuted
        if (xValsObs.length < minStratumSize) return Double.NaN;
        double[] xEdges = quantileEdges(xValsObs, binsPerContXY);

        LevelMap ym = new LevelMap(maxObservedLevelsPerVar);
        int n = 0;
        for (int ii : g) {
            int rowY = useRows.get(ii);
            int yLev = data.getInt(rowY, yDiscIdx);
            if (yLev == DiscreteVariable.MISSING_VALUE) continue;

            int rowX = rowY;
            if (permMap != null) rowX = useRows.get(permMap[ii]);
            double xv = data.getDouble(rowX, xContIdx);
            if (Double.isNaN(xv)) continue;

            ym.put(yLev);
            n++;
        }
        if (n < minStratumSize) return Double.NaN;

        int Kx = binsPerContXY;
        int Ky = ym.size();

        if ((long) Kx * (long) Ky > maxCellsPerStratum) {
            return gTestHashedContinuousDiscrete(xContIdx, yDiscIdx, useRows, g, permMap, binsPerContXY);
        }

        int[][] counts = new int[Kx][Ky];
        int[] rowS = new int[Kx];
        int[] colS = new int[Ky];

        for (int ii : g) {
            int rowY = useRows.get(ii);

            int rowX = rowY;
            if (permMap != null) rowX = useRows.get(permMap[ii]);

            double xv = data.getDouble(rowX, xContIdx);
            if (Double.isNaN(xv)) continue;

            int yLev = data.getInt(rowY, yDiscIdx);
            if (yLev == DiscreteVariable.MISSING_VALUE) continue;

            int xi = binIndex(xEdges, xv);
            int yi = ym.getIndex(yLev);
            if (yi < 0) continue;

            counts[xi][yi]++;
            rowS[xi]++;
            colS[yi]++;
        }

        return gTestFromCounts(counts, rowS, colS, sum(rowS));
    }

    // =========================================================
    // Public setters / interface
    // =========================================================

    private double gTestContinuousContinuous(int xContIdx, int yContIdx,
                                             List<Integer> useRows, int[] g, int[] permMap) {
        // IMPORTANT:
        //  - X bin edges must be computed from OBSERVED X within stratum (permMap == null),
        //    otherwise permutations re-define bins and inflate p-values.
        //  - Y edges are always from observed Y (since Y is not permuted).

        double[] xValsObs = collectContPermuted(xContIdx, useRows, g, null); // OBSERVED
        double[] yVals = collectCont(yContIdx, useRows, g, null);
        if (xValsObs.length < minStratumSize || yVals.length < minStratumSize) return Double.NaN;

        double[] xEdges = quantileEdges(xValsObs, binsPerContXY);
        double[] yEdges = quantileEdges(yVals, binsPerContXY);

        int Kx = binsPerContXY;
        int Ky = binsPerContXY;

        int[][] counts = new int[Kx][Ky];
        int[] rowS = new int[Kx];
        int[] colS = new int[Ky];

        int n = 0;
        for (int ii : g) {
            int rowY = useRows.get(ii);
            int rowX = rowY;
            if (permMap != null) rowX = useRows.get(permMap[ii]);

            double xv = data.getDouble(rowX, xContIdx);
            double yv = data.getDouble(rowY, yContIdx);
            if (Double.isNaN(xv) || Double.isNaN(yv)) continue;

            int xi = binIndex(xEdges, xv);
            int yi = binIndex(yEdges, yv);

            counts[xi][yi]++;
            rowS[xi]++;
            colS[yi]++;
            n++;
        }

        if (n < minStratumSize) return Double.NaN;
        return gTestFromCounts(counts, rowS, colS, n);
    }

//    private double gTestContinuousContinuous(int xContIdx, int yContIdx,
//                                             List<Integer> useRows, int[] g, int[] permMap) {
//        // Use z-scored columns for stability (matches original).
//        int m = g.length;
//        if (m < minStratumSize) return Double.NaN;
//
//        double[] x = new double[m];
//        double[] y = new double[m];
//        int k = 0;
//
//        for (int ii : g) {
//            int rowY = useRows.get(ii);
//            int rowX = rowY;
//            if (permMap != null) rowX = useRows.get(permMap[ii]);
//
//            double xv = zCols[xContIdx][rowX];
//            double yv = zCols[yContIdx][rowY];
//            if (Double.isNaN(xv) || Double.isNaN(yv)) continue; // should be rare due to useRows filtering
//
//            x[k] = xv;
//            y[k] = yv;
//            k++;
//        }
//
//        if (k < minStratumSize) return Double.NaN;
//        if (k != m) { x = Arrays.copyOf(x, k); y = Arrays.copyOf(y, k); }
//
//        // correlation^2 is your original “sweet spot” on general noise
//        return statCorrSq(x, y);
//    }

    private static double statCorrSq(double[] x, double[] y) {
        int n = x.length;
        if (n < 3) return Double.NaN;

        double sx = 0, sy = 0;
        for (int i = 0; i < n; i++) { sx += x[i]; sy += y[i]; }
        double mx = sx / n, my = sy / n;

        double sxx = 0, syy = 0, sxy = 0;
        for (int i = 0; i < n; i++) {
            double dx = x[i] - mx;
            double dy = y[i] - my;
            sxx += dx * dx;
            syy += dy * dy;
            sxy += dx * dy;
        }

        if (sxx <= 0 || syy <= 0) return 0.0;
        double r = sxy / sqrt(sxx * syy);
        return r * r;
    }

    private double gTestHashedDiscreteDiscrete(int xIdx, int yIdx,
                                               List<Integer> useRows, int[] g, int[] permMap,
                                               int bins) {
        int Kx = bins;
        int Ky = bins;

        int[][] counts = new int[Kx][Ky];
        int[] rowS = new int[Kx];
        int[] colS = new int[Ky];

        int n = 0;
        for (int ii : g) {
            int rowY = useRows.get(ii);
            int rowX = rowY;
            if (permMap != null) rowX = useRows.get(permMap[ii]);

            int xLev = data.getInt(rowX, xIdx);
            int yLev = data.getInt(rowY, yIdx);
            if (xLev == DiscreteVariable.MISSING_VALUE) continue;
            if (yLev == DiscreteVariable.MISSING_VALUE) continue;

            int xi = floorMod(mix32(xLev), Kx);
            int yi = floorMod(mix32(yLev), Ky);

            counts[xi][yi]++;
            rowS[xi]++;
            colS[yi]++;
            n++;
        }
        if (n < minStratumSize) return Double.NaN;
        return gTestFromCounts(counts, rowS, colS, n);
    }

    private double gTestHashedDiscreteContinuous(int xDiscIdx, int yContIdx,
                                                 List<Integer> useRows, int[] g, int[] permMap,
                                                 int binsY) {
        double[] yVals = collectCont(yContIdx, useRows, g, null);
        if (yVals.length < minStratumSize) return Double.NaN;
        double[] yEdges = quantileEdges(yVals, binsY);

        int Kx = binsPerContXY; // hash discrete to small
        int Ky = binsY;

        int[][] counts = new int[Kx][Ky];
        int[] rowS = new int[Kx];
        int[] colS = new int[Ky];

        int n = 0;
        for (int ii : g) {
            int rowY = useRows.get(ii);
            int rowX = rowY;
            if (permMap != null) rowX = useRows.get(permMap[ii]);

            int xLev = data.getInt(rowX, xDiscIdx);
            if (xLev == DiscreteVariable.MISSING_VALUE) continue;

            double yv = data.getDouble(rowY, yContIdx);
            if (Double.isNaN(yv)) continue;

            int xi = floorMod(mix32(xLev), Kx);
            int yi = binIndex(yEdges, yv);

            counts[xi][yi]++;
            rowS[xi]++;
            colS[yi]++;
            n++;
        }
        if (n < minStratumSize) return Double.NaN;
        return gTestFromCounts(counts, rowS, colS, n);
    }

    private double gTestHashedContinuousDiscrete(int xContIdx, int yDiscIdx,
                                                 List<Integer> useRows, int[] g, int[] permMap,
                                                 int binsX) {
//        double[] xVals = collectContPermuted(xContIdx, useRows, g, permMap);
//        if (xVals.length < minStratumSize) return Double.NaN;
//        double[] xEdges = quantileEdges(xVals, binsX);

        double[] xValsObs = collectContPermuted(xContIdx, useRows, g, null); // OBSERVED
        if (xValsObs.length < minStratumSize) return Double.NaN;
        double[] xEdges = quantileEdges(xValsObs, binsX);

        int Kx = binsX;
        int Ky = binsPerContXY; // hash discrete

        int[][] counts = new int[Kx][Ky];
        int[] rowS = new int[Kx];
        int[] colS = new int[Ky];

        int n = 0;
        for (int ii : g) {
            int rowY = useRows.get(ii);
            int rowX = rowY;
            if (permMap != null) rowX = useRows.get(permMap[ii]);

            double xv = data.getDouble(rowX, xContIdx);
            if (Double.isNaN(xv)) continue;

            int yLev = data.getInt(rowY, yDiscIdx);
            if (yLev == DiscreteVariable.MISSING_VALUE) continue;

            int xi = binIndex(xEdges, xv);
            int yi = floorMod(mix32(yLev), Ky);

            counts[xi][yi]++;
            rowS[xi]++;
            colS[yi]++;
            n++;
        }
        if (n < minStratumSize) return Double.NaN;
        return gTestFromCounts(counts, rowS, colS, n);
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
            buckets.computeIfAbsent(sig, s -> new IntArrayList()).add(i);
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

    @Override
    public List<Node> getVariables() {
        return variables;
    }

    @Override
    public DataSet getData() {
        return data;
    }

    @Override
    public List<DataSet> getDataSets() {
        return List.of(data);
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
    public int getSampleSize() {
        return data.getNumRows();
    }

    @Override
    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public void setPermutations(int B) {
        this.permutations = Math.max(50, B);
    }

    public void setPermSeed(long s) {
        this.permSeed = s;
    }

    // =========================================================
    // Helpers
    // =========================================================

    public void setBinsPerContZ(int b) {
        this.binsPerContZ = Math.max(2, b);
        strataCache.clear();
    }

    public void setMinStratumSize(int m) {
        this.minStratumSize = Math.max(2, m);
        strataCache.clear();
    }

    public void setBinsPerContXY(int b) {
        this.binsPerContXY = Math.max(2, b);
    }

    public void setMaxObservedLevelsPerVar(int m) {
        this.maxObservedLevelsPerVar = Math.max(4, m);
    }

    public void setMaxCellsPerStratum(int m) {
        this.maxCellsPerStratum = Math.max(64, m);
    }

    // RowsSettable
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

    // Subset: safe fallback behavior (same as your earlier pattern)
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

    // Collect continuous values for a variable within a stratum (no permutation).
    private double[] collectCont(int var, List<Integer> useRows, int[] g, int[] permMapUnused) {
        double[] tmp = new double[g.length];
        int n = 0;
        for (int ii : g) {
            int row = useRows.get(ii);
            double v = data.getDouble(row, var);
            if (Double.isNaN(v)) continue;
            tmp[n++] = v;
        }
        return Arrays.copyOf(tmp, n);
    }

    // Collect continuous values for X within a stratum with permutation applied to X if permMap != null.
    private double[] collectContPermuted(int var, List<Integer> useRows, int[] g, int[] permMap) {
        double[] tmp = new double[g.length];
        int n = 0;
        for (int ii : g) {
            int row = useRows.get(ii);
            if (permMap != null) row = useRows.get(permMap[ii]); // X permuted
            double v = data.getDouble(row, var);
            if (Double.isNaN(v)) continue;
            tmp[n++] = v;
        }
        return Arrays.copyOf(tmp, n);
    }

    public void setUseMaxAcrossStrata(boolean useMaxAcrossStrata) {
        this.useMaxAcrossStrata = useMaxAcrossStrata;
    }

    // =========================================================
    // Small utility classes
    // =========================================================

    /**
     * Compress observed levels to 0..K-1, with optional hashing fallback when unique levels get large.
     */
    private static final class LevelMap {
        private final int cap;
        private final HashMap<Integer, Integer> map = new HashMap<>();
        private int n = 0;

        LevelMap(int cap) {
            this.cap = Math.max(4, cap);
        }

        void put(int level) {
            if (map.containsKey(level)) return;
            if (n < cap) {
                map.put(level, n++);
            } else {
                // once we exceed cap, we keep n fixed and rely on hashed fallbacks upstream
                // (we still store a few more to avoid oscillation)
                map.put(level, n++);
            }
        }

        int getIndex(int level) {
            Integer i = map.get(level);
            return i == null ? -1 : i;
        }

        int size() {
            return map.size();
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