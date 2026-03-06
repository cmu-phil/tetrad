package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TMath;

import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.Double.NaN;
import static edu.cmu.tetrad.util.TMath.*;

/**
 * Neykov-style Minimax CI test (Neykov, Balakrishnan, Wasserman 2021):
 * a stratified conditional-independence test that:
 *
 *  1) Partitions Z into d bins (equal-length / quantile bins for continuous Z, exact match for discrete Z).
 *  2) In each Z-bin m, forms a "flattened" dependence statistic U_m using sample-splitting:
 *      - D_{m,X} estimates p_X
 *      - D_{m,Y} estimates p_Y
 *      - D_{m,XY} estimates p_{XY}
 *     and computes a chi-square-like divergence with flattening weights 1/(p_X p_Y).
 *  3) Aggregates across bins using the paper’s weighted sum form:
 *        T = sum_m 1(sigma_m >= 4) * sigma_m * omega_m * U_m
 *     where omega_m = sqrt(min(sigma_m, KxEff) * min(sigma_m, KyEff)).
 *  4) Calibrates with a stratified permutation: shuffle Y *within each Z-bin*.
 *
 * Notes:
 * - This implementation uses pragmatic discretization for continuous X/Y within each Z-bin via quantile binning.
 * - The per-bin statistic here is a practical "flattened chi-square" variant matching the paper’s spirit
 *   (flattening + sample splitting + weighted sum aggregation). It is designed to be stable and fast.
 * - If you want an even closer match to the paper’s U-statistic algebra, this class is the right place to refine
 *   U_m (replace flattenedChiSqFromSplit(...) with a closer U_W estimator).
 */
public final class NeykovMinimaxCITest implements IndependenceTest, RowsSettable {

    // ---------------- data ----------------
    private final DataSet data;
    private final List<Node> variables;
    private final Map<String, Integer> indexMap;

    // z-scored continuous columns (NaNs preserved). Discrete vars are NaN-filled.
    private final double[][] zCols;

    // strata cache: (Z signature + useRows signature + bins + minSize) -> groups (indices in useRows-space)
    private final ConcurrentHashMap<StrataKey, int[][]> strataCache = new ConcurrentHashMap<>();

    // ---------------- knobs ----------------
    private double alpha = 0.01;

    // permutation
    private int permutations = 200;
    private long permSeed = 1L;

    // Z stratification
    private int minStratumSize = 4;                 // paper uses sigma_m >= 4
    private int binsPerContZ = 6;                   // ignored if useAdaptiveZBins=true
    private boolean useAdaptiveZBins = true;        // d ~ n^(2/5) * 12^(1/5)

    // X/Y discretization within a stratum (for mixed + continuous)
    private int binsPerContXY = 6;

    // safety bounds (avoid pathological huge tables)
    private int maxObservedLevelsPerVar = 32;
    private int maxCellsPerStratum = 1024;

    // smoothing / numeric stability
    private double epsProb = 1e-12;                 // lower bound for probabilities

    // behavior
    private boolean verbose = false;

    // optional row restriction
    private List<Integer> rows = null;

    /**
     * Constructs an instance of NeykovMinimaxCITest to perform Neykov's minimax conditional independence test
     * using the provided dataset and significance level.
     *
     * @param data the dataset to be used in the independence test; must not be null
     * @param alpha the significance level for the test; must be a value between 0 and 1
     * @throws NullPointerException if the provided dataset is null
     */
    public NeykovMinimaxCITest(DataSet data, double alpha) {
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

    /**
     * Performs a conditional independence test to determine whether two variables, x and y,
     * are independent given a set of conditioning variables, z.
     *
     * @param x the first variable in the independence test; must not be null
     * @param y the second variable in the independence test; must not be null
     * @param z the set of conditioning variables; must not be null but can be empty
     * @return an {@code IndependenceResult} object containing the result of the independence test,
     *         including the p-value, independence determination, and statistical details
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
     * Computes the p-value for a conditional independence test between two nodes, x and y,
     * given a set of conditioning nodes, z. The p-value reflects the probability of observing
     * test statistics under the null hypothesis of conditional independence.
     *
     * @param x the first node (variable) in the test; must not be null
     * @param y the second node (variable) in the test; must not be null
     * @param z the set of conditioning nodes (variables); must not be null but may be empty
     * @return the computed p-value for the test; a lower p-value suggests stronger evidence
     *         against the null hypothesis of conditional independence. Returns NaN if there
     *         is insufficient data for the computation or other preconditions are not met
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

        int dBins = useAdaptiveZBins ? adaptiveZBins(n) : binsPerContZ;

        int[][] strata = getStrata(iz, useRows, dBins);
        if (strata.length == 0) return NaN;

        long seed = permSeed ^ ix ^ (iy * 1315423911L) ^ Arrays.hashCode(iz);

        // Build per-stratum "plans" (observed X codes, observed Y codes) so we can:
        //   - compute observed T
        //   - compute permuted T by shuffling Y within each stratum
        GroupPlan[] plans = buildPlans(ix, iy, useRows, strata, seed);
        if (plans.length == 0) return NaN;

        // observed T
        double tObs = aggregateWeightedSum(plans);
        if (!Double.isFinite(tObs)) return 1.0;

        // permutation calibration: shuffle Y within each stratum, recompute T
        SplittableRandom rng = new SplittableRandom(seed);
        int BB = TMath.max(50, permutations);

        int ge = 0;
        int valid = 0;

        for (int b = 0; b < BB; b++) {
            double tPerm = aggregateWeightedSumPermuted(plans, rng);
            if (!Double.isFinite(tPerm)) continue;
            valid++;
            if (tPerm >= tObs) ge++;
        }

        if (valid == 0) return NaN;
        return (ge + 1.0) / (valid + 1.0);
    }

    // =========================================================
    // Neykov-style aggregation: weighted sum across Z-bins
    // =========================================================

    private double aggregateWeightedSum(GroupPlan[] plans) {
        double T = 0.0;
        for (GroupPlan gp : plans) {
            if (gp.sigma < 4) continue; // paper gate 1(sigma_m >= 4)
            double Um = gp.statisticObserved(); // flattened statistic from sample split
            if (!Double.isFinite(Um)) continue;
            // T += sigma_m * omega_m * U_m
            T += gp.sigma * gp.omega * Um;
        }
        return T;
    }

    private double aggregateWeightedSumPermuted(GroupPlan[] plans, SplittableRandom rng) {
        double T = 0.0;
        for (GroupPlan gp : plans) {
            if (gp.sigma < 4) continue;
            double Um = gp.statisticPermuted(rng);
            if (!Double.isFinite(Um)) continue;
            T += gp.sigma * gp.omega * Um;
        }
        return T;
    }

    // =========================================================
    // Build per-Z-bin plans (sample splitting + flattening)
    // =========================================================

    private GroupPlan[] buildPlans(int ix, int iy, List<Integer> useRows, int[][] strata, long seed) {
        ArrayList<GroupPlan> out = new ArrayList<>(strata.length);
        int binId = 0;

        for (int[] g : strata) {
            if (g.length < minStratumSize) continue;

            GroupPlan gp = buildGroupPlan(ix, iy, useRows, g, seed, binId++);
            if (gp != null) out.add(gp);
        }

        return out.toArray(new GroupPlan[0]);
    }

    private GroupPlan buildGroupPlan(int ix, int iy, List<Integer> useRows, int[] g, long seed, int binId) {
        final int m = g.length;
        if (m < minStratumSize) return null;

        // Adaptive bins for continuous X/Y within this stratum: min(global, floor(sqrt(m))) with floor 2.
        int binsXY = TMath.max(2, TMath.min(binsPerContXY, (int) TMath.floor(TMath.sqrt(m))));
        int maxBinsByCells = TMath.max(2, (int) TMath.floor(TMath.sqrt(maxCellsPerStratum)));
        binsXY = TMath.min(binsXY, maxBinsByCells);

        Cat xCat = buildCategories(ix, useRows, g, binsXY);
        Cat yCat = buildCategories(iy, useRows, g, binsXY);
        if (xCat == null || yCat == null) return null;

        int Kx = xCat.K;
        int Ky = yCat.K;

        if (Kx < 2 || Ky < 2) {
            // If either is constant in this stratum, U_m=0, keep but it contributes nothing.
            return new GroupPlan(xCat.codes, yCat.codes, Kx, Ky, binId, seed, epsProb);
        }

        long cells = (long) Kx * (long) Ky;
        if (cells > maxCellsPerStratum) {
            int K = TMath.max(2, (int) TMath.floor(TMath.sqrt(maxCellsPerStratum)));
            int[] x = (Kx > K) ? downBinDeterministic(xCat.codes, Kx, K) : xCat.codes;
            int[] y = (Ky > K) ? downBinDeterministic(yCat.codes, Ky, K) : yCat.codes;
            return new GroupPlan(x, y, TMath.min(Kx, K), TMath.min(Ky, K), binId, seed, epsProb);
        }

        return new GroupPlan(xCat.codes, yCat.codes, Kx, Ky, binId, seed, epsProb);
    }

    // =========================================================
    // Z-strata
    // =========================================================

    private int[][] getStrata(int[] zIdx, List<Integer> useRows, int binsContZ) {
        if (zIdx.length == 0) return new int[][]{range(useRows.size())};

        long rowsSig = signature(useRows);
        StrataKey key = new StrataKey(zIdx, rowsSig, binsContZ, minStratumSize);

        return strataCache.computeIfAbsent(key, kk -> buildStrata(zIdx, useRows, binsContZ, minStratumSize));
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

    // =========================================================
    // Adaptive choice of d (bins for continuous Z)
    // =========================================================

    private int adaptiveZBins(int n) {
        // d = floor( n^(2/5) * 12^(1/5) )
        // Keep sane caps because this is used repeatedly inside causal search.
        double d = pow(n, 0.4) * pow(12.0, 0.2);
        int di = (int) floor(d);
        di = TMath.max(2, di);
        di = TMath.min(di, 50); // practical cap; adjust if you like
        return di;
    }

    // =========================================================
    // Missingness / row filtering
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
     * Retrieves the list of variables associated with this instance.
     *
     * @return a {@code List} of {@code Node} objects representing the variables;
     *         the list may be empty but never null
     */
    @Override
    public List<Node> getVariables() {
        return variables;
    }

    /**
     * Retrieves the dataset associated with this instance.
     *
     * @return the {@code DataSet} object representing the dataset; never null
     */
    @Override
    public DataSet getData() {
        return data;
    }

    /**
     * Retrieves the list of datasets associated with this instance.
     *
     * @return a {@code List} of {@code DataSet} objects representing the
     *         datasets; the list may be empty but is guaranteed to be non-null
     */
    @Override
    public List<DataSet> getDataSets() {
        return List.of(data);
    }

    /**
     * Retrieves the significance level (alpha) used for the Neykov minimax conditional independence test.
     * This value represents the threshold for determining statistical significance and is expected
     * to be a number between 0 and 1.
     *
     * @return the alpha value, which indicates the significance level of the test
     */
    @Override
    public double getAlpha() {
        return alpha;
    }

    /**
     * Updates the significance level (alpha) for the Neykov minimax conditional independence test.
     * The alpha value determines the threshold for statistical significance, where lower values
     * reflect stricter criteria for rejecting the null hypothesis. The method ensures the provided
     * alpha is within the valid range [0, 1]. If the current number of permutations is insufficient
     * to achieve the specified alpha level, the number of permutations is increased. A log message
     * is generated if the permutations are adjusted.
     *
     * @param alpha the desired significance level; must be a value between 0 and 1
     * @throws IllegalArgumentException if the provided alpha is outside the valid range [0, 1]
     */
    public void setAlpha(double alpha) {
        if (alpha < 0 || alpha > 1) throw new IllegalArgumentException("alpha must be in [0,1]");
        this.alpha = alpha;

        int minB = (alpha > 0.0) ? ((int) TMath.ceil(1.0 / alpha) - 1) : Integer.MAX_VALUE;
        NumberFormat nf = NumberFormat.getNumberInstance();

        if (this.permutations < minB) {
            int oldB = this.permutations;
            this.permutations = minB;

            TetradLogger.getInstance().log(
                    "NeykovMinimaxCITest: increased permutations from " + oldB + " to " + this.permutations +
                            " so alpha=" + nf.format(alpha) + " is attainable (p-floor=1/(B+1))."
            );
        }
    }

    /**
     * Retrieves the sample size for the current dataset.
     *
     * @return the number of rows in the dataset, representing the sample size.
     */
    @Override
    public int getSampleSize() {
        return data.getNumRows();
    }

    /**
     * Determines if verbose output is enabled for the instance. Verbose mode often
     * provides detailed logging or diagnostic messages to facilitate debugging or
     * monitoring of the process.
     *
     * @return {@code true} if verbose output is enabled, {@code false} otherwise
     */
    @Override
    public boolean isVerbose() {
        return verbose;
    }

    /**
     * Sets the verbosity level for the instance. When verbosity is enabled,
     * detailed logging or diagnostic messages may be output to assist
     * with debugging or monitoring of the process.
     *
     * @param verbose {@code true} to enable verbose output, {@code false} to disable it
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Sets the number of permutations to be used, ensuring it meets the minimum
     * required threshold for achieving the specified alpha value. If the provided
     * number of permutations is below the minimum threshold, it will be increased
     * to the required value and a log message will be generated indicating the adjustment.
     *
     * @param B the number of permutations requested. The method ensures this value
     *          is at least the greater of 50 or the minimum required based on the
     *          current alpha value. If the provided value is too low, it is
     *          automatically adjusted to the required minimum.
     */
    public void setPermutations(int B) {
        int requested = TMath.max(50, B);

        int minB = (alpha > 0.0) ? ((int) TMath.ceil(1.0 / alpha) - 1) : Integer.MAX_VALUE;
        NumberFormat nf = NumberFormat.getNumberInstance();

        if (requested < minB) {
            int old = requested;
            requested = minB;

            TetradLogger.getInstance().log(
                    "NeykovMinimaxCITest: increased permutations from " + old + " to " + requested +
                            " so alpha=" + nf.format(alpha) + " is attainable (p-floor=1/(B+1))."
            );
        }

        this.permutations = requested;
    }

    /**
     * Sets the permutation seed value.
     *
     * @param s the seed value to be set for permutation operations
     */
    public void setPermSeed(long s) {
        this.permSeed = s;
    }

    /**
     * Sets whether to use adaptive z-binning for continuous variables.
     *
     * @param useAdaptiveZBins flag indicating whether to use adaptive z-binning
     */
    public void setUseAdaptiveZBins(boolean useAdaptiveZBins) {
        this.useAdaptiveZBins = useAdaptiveZBins;
        strataCache.clear();
    }

    /**
     * Sets the number of bins to be used per continuous variable along the Z-axis.
     * The value is constrained to be at least 2 to ensure meaningful binning.
     * The strata cache is cleared after updating this value to maintain consistency.
     *
     * @param b the desired number of bins for the Z-axis; must be 2 or greater. Values less than 2 will default to 2.
     */
    public void setBinsPerContZ(int b) {
        this.binsPerContZ = TMath.max(2, b);
        strataCache.clear();
    }

    /**
     * Sets the minimum allowable size for a stratum. The value provided
     * determines the smallest number of elements permitted in a stratum,
     * with an enforced minimum of 2.
     *
     * @param m the desired minimum stratum size. If the provided value is
     *          less than 2, it will default to 2.
     */
    public void setMinStratumSize(int m) {
        this.minStratumSize = TMath.max(2, m);
        strataCache.clear();
    }

    /**
     * Sets the number of bins to be used per container along the X and Y dimensions.
     * Ensures that the value is at least 2.
     *
     * @param b the desired number of bins per container along the X and Y dimensions
     */
    public void setBinsPerContXY(int b) {
        this.binsPerContXY = TMath.max(2, b);
    }

    /**
     * Sets the maximum number of observed levels allowed per variable.
     * The specified value will be compared to a minimum threshold of 4,
     * and the larger value will be used.
     *
     * @param m The proposed maximum number of observed levels per variable.
     *          If this value is less than 4, the threshold of 4 will be used instead.
     */
    public void setMaxObservedLevelsPerVar(int m) {
        this.maxObservedLevelsPerVar = TMath.max(4, m);
    }

    /**
     * Sets the maximum number of cells allowed per stratum. Ensures that the
     * minimum number of cells is 64.
     *
     * @param m the desired maximum number of cells per stratum. If the provided
     *          value is less than 64, it defaults to 64.
     */
    public void setMaxCellsPerStratum(int m) {
        this.maxCellsPerStratum = TMath.max(64, m);
    }

    /**
     * Sets the epsilon probability value used in the algorithm.
     *
     * @param epsProb the probability value to be set. It must be greater than 0.
     * @throws IllegalArgumentException if the provided epsProb is not greater than 0.
     */
    public void setEpsProb(double epsProb) {
        if (!(epsProb > 0)) throw new IllegalArgumentException("epsProb must be > 0");
        this.epsProb = epsProb;
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

    /**
     * Sets the list of row indices to be used. Clears the associated strata cache after updating the rows.
     *
     * @param rows a list of integers representing the row indices to set; must not contain null values,
     *             negative numbers, or indices out of bounds relative to the data. If null, the rows will
     *             be reset to null, and the strata cache will be cleared.
     * @throws NullPointerException if any row in the list is null.
     * @throws IllegalArgumentException if any row in the list is negative or out of bounds.
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
     * Returns the independence test for the provided subset of variables.
     *
     * @param vars the list of variables for which the independence test is to be returned
     * @return the independence test object for the specified subset of variables
     */
    @Override
    public IndependenceTest indTestSubset(List<Node> vars) {
        return this;
    }

    /**
     * Provides a string representation of the object, describing its nature
     * and purpose.
     *
     * @return A string representing the object, specifically describing it as
     *         "Neykov Minimax CI Test (flattened, stratified, permuted)".
     */
    @Override
    public String toString() {
        return "Neykov Minimax CI Test (flattened, stratified, permuted)";
    }

    // =========================================================
    // Index helpers
    // =========================================================

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
    // Core math utilities (binning, zscore, compression)
    // =========================================================

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

    private static Map<String, Integer> indexMap(List<Node> vars) {
        Map<String, Integer> m = new HashMap<>();
        for (int i = 0; i < vars.size(); i++) m.put(vars.get(i).getName(), i);
        return m;
    }

    // =========================================================
    // Categories for X/Y inside a Z-bin
    // =========================================================

    private Cat buildCategories(int varIdx, List<Integer> useRows, int[] g, int effBinsXY) {
        if (isDiscrete(varIdx)) {
            int[] lev = new int[g.length];
            int n = 0;
            for (int ii : g) {
                int row = useRows.get(ii);
                int v = data.getInt(row, varIdx);
                if (v == DiscreteVariable.MISSING_VALUE) continue;
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
            // Continuous: quantile binning within this Z-bin
            double[] vals = new double[g.length];
            int n = 0;
            for (int ii : g) {
                int row = useRows.get(ii);
                double v = data.getDouble(row, varIdx);
                if (Double.isNaN(v)) continue;
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
     * Per-Z-bin plan:
     * - Observed x codes and y codes (already in 0..K-1).
     * - Deterministic shuffle to split rows into three parts:
     *     A (estimate pX), B (estimate pY), C (estimate pXY).
     * - Observed statistic uses observed y codes.
     * - Permuted statistic shuffles y codes within this bin and recomputes the C joint only.
     */
    private static final class GroupPlan {
        final int[] xObs;
        final int[] yObs;
        final int Kx, Ky;

        final int sigma;
        final double omega;

        final int binId;
        final long seed;

        final double epsProb;

        // Split indices in [0..sigma) into A,B,C (in-bin positions)
        final int[] splitA;
        final int[] splitB;
        final int[] splitC;

        // Marginal estimates from A and B
        final double[] pXhat; // size Kx
        final double[] pYhat; // size Ky

        // scratch for permutation
        final int[] yPerm;

        GroupPlan(int[] xObs, int[] yObs, int Kx, int Ky, int binId, long seed, double epsProb) {
            this.xObs = xObs;
            this.yObs = yObs;
            this.Kx = Kx;
            this.Ky = Ky;
            this.binId = binId;
            this.seed = seed;
            this.epsProb = epsProb;

            this.sigma = xObs.length;

            // omega_m = sqrt(min(sigma_m,d1)*min(sigma_m,d2)) – use effective category counts as proxies for d1,d2
            this.omega = sqrt(min(sigma, Kx) * (double) min(sigma, Ky));

            // deterministic permutation of in-bin positions, to define the split
            int[] perm = new int[sigma];
            for (int i = 0; i < sigma; i++) perm[i] = i;

            SplittableRandom rng = new SplittableRandom(mix64(seed ^ (long) binId * 0x9E3779B97F4A7C15L));
            for (int i = sigma - 1; i > 0; i--) {
                int j = rng.nextInt(i + 1);
                int tmp = perm[i];
                perm[i] = perm[j];
                perm[j] = tmp;
            }

            // split roughly into thirds
            int aN = sigma / 3;
            int bN = sigma / 3;
            int cN = sigma - aN - bN;

            this.splitA = Arrays.copyOfRange(perm, 0, aN);
            this.splitB = Arrays.copyOfRange(perm, aN, aN + bN);
            this.splitC = Arrays.copyOfRange(perm, aN + bN, aN + bN + cN);

            // estimate pX from A, pY from B (with tiny smoothing for safety)
            this.pXhat = estimateMarginal(xObs, Kx, splitA, epsProb);
            this.pYhat = estimateMarginal(yObs, Ky, splitB, epsProb);

            this.yPerm = new int[sigma];
        }

        double statisticObserved() {
            if (Kx < 2 || Ky < 2) return 0.0;
            return flattenedChiSqFromSplit(xObs, yObs, Kx, Ky, splitC, pXhat, pYhat, epsProb);
        }

        double statisticPermuted(SplittableRandom rng) {
            if (Kx < 2 || Ky < 2) return 0.0;

            // yPerm = observed then shuffle within this bin
            System.arraycopy(yObs, 0, yPerm, 0, sigma);
            for (int i = sigma - 1; i > 0; i--) {
                int j = rng.nextInt(i + 1);
                int tmp = yPerm[i];
                yPerm[i] = yPerm[j];
                yPerm[j] = tmp;
            }

            return flattenedChiSqFromSplit(xObs, yPerm, Kx, Ky, splitC, pXhat, pYhat, epsProb);
        }

        private static double[] estimateMarginal(int[] v, int K, int[] idx, double epsProb) {
            double[] p = new double[K];
            if (idx.length == 0) {
                Arrays.fill(p, 1.0 / K);
                return p;
            }
            for (int t : idx) {
                int c = v[t];
                if (c >= 0 && c < K) p[c] += 1.0;
            }
            double sum = 0.0;
            for (int k = 0; k < K; k++) {
                // tiny additive smoothing to avoid zeros
                p[k] = p[k] + epsProb;
                sum += p[k];
            }
            for (int k = 0; k < K; k++) p[k] /= sum;
            return p;
        }

        /**
         * Practical "flattened chi-square" statistic for independence:
         *   U = sum_{i,j} (p_ij - pX_i pY_j)^2 / (pX_i pY_j)
         * where:
         *   pX, pY are estimated from split A/B,
         *   p_ij is estimated from split C.
         */
        private static double flattenedChiSqFromSplit(int[] x, int[] y, int Kx, int Ky,
                                                      int[] splitC,
                                                      double[] pX, double[] pY,
                                                      double epsProb) {
            int nC = splitC.length;
            if (nC < 2) return 0.0;

            double[][] pij = new double[Kx][Ky];
            for (int t : splitC) {
                int xi = x[t];
                int yi = y[t];
                if (xi < 0 || xi >= Kx || yi < 0 || yi >= Ky) continue;
                pij[xi][yi] += 1.0;
            }
            // normalize to probabilities on split C
            double inv = 1.0 / nC;
            for (int i = 0; i < Kx; i++) {
                for (int j = 0; j < Ky; j++) pij[i][j] *= inv;
            }

            double U = 0.0;
            for (int i = 0; i < Kx; i++) {
                double px = max(epsProb, pX[i]);
                for (int j = 0; j < Ky; j++) {
                    double py = max(epsProb, pY[j]);
                    double denom = px * py;
                    denom = max(epsProb, denom);

                    double diff = pij[i][j] - denom;
                    U += (diff * diff) / denom;
                }
            }

            return U;
        }

        // 64-bit mix for deterministic PRNG seeding
        private static long mix64(long z) {
            z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
            z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
            return z ^ (z >>> 33);
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