package edu.cmu.tetrad.search.score;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.EffectiveSampleSizeSettable;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TMath;
import org.ejml.data.DMatrixRMaj;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * <p><b>KML: Kernel Marginal Likelihood score for mixed (continuous + discrete) parents</b></p>
 *
 * <p>
 * This is the exact-kernel analogue of {@link FfMl}. It extends the exact Gaussian-process
 * marginal-likelihood KML score (see {@link KernelMarginalLikelihoodScoreContinuous}) to parent
 * sets containing both continuous and discrete variables, using the same modelling recipe as FFML:
 * a <b>product kernel</b> between a continuous RBF kernel and a categorical kernel.
 * </p>
 *
 * <p><b>Product-kernel model</b></p>
 * <p>
 * For a parent vector {@code Z = (Z_c, Z_d)} with continuous part {@code Z_c} and discrete part
 * {@code Z_d}, the kernel is
 * </p>
 * <pre>
 *   k((z_c, z_d), (z'_c, z'_d)) = k_cont(z_c, z'_c) · k_cat(z_d, z'_d)
 * </pre>
 * <ul>
 *   <li>{@code k_cont} is the <b>exact</b> RBF kernel over the continuous parents (median-heuristic
 *       bandwidth), reusing {@link KernelMarginalLikelihoodScoreContinuous#rbfGramContinuous}. Unlike
 *       FFML this uses the full {@code n×n} Gram matrix rather than a random-feature approximation, so
 *       there is no random basis, no seeding, and no bandwidth grid-search.</li>
 *   <li>{@code k_cat} is the same positive-semidefinite categorical kernel used by FFML: it returns
 *       {@code 1} on a level match and {@code ρ} on a mismatch ({@code 0 ≤ ρ < 1}); over several
 *       discrete parents it multiplies, giving {@code ρ^(#mismatches)}.</li>
 * </ul>
 *
 * <p>
 * The two kernels are combined as a Hadamard (elementwise) product {@code K = K_cont ⊙ K_cat}, which
 * is positive semidefinite by the Schur product theorem, and the marginal covariance is
 * {@code C = K + σ² I}. The local score is the GP log marginal likelihood
 * {@code -0.5·yᵀC⁻¹y - 0.5·log|C|}, matching Tetrad's score convention (additive constants omitted).
 * </p>
 *
 * <p><b>Routing</b></p>
 * <ul>
 *   <li><b>Continuous target with no discrete parents</b> (including the no-parent case): delegated
 *       directly to {@link KernelMarginalLikelihoodScoreContinuous}. This is the "KML calls
 *       KML-Continuous for continuous variables only" path and reproduces the original continuous
 *       KML behaviour exactly.</li>
 *   <li><b>Continuous target with at least one discrete parent</b>: the exact product kernel is built
 *       here and scored as a single-output GP marginal likelihood.</li>
 *   <li><b>Discrete target</b>: handled via a Gaussian multi-output surrogate (identical in spirit to
 *       FFML). The target is expanded into centered one-hot columns and each column is scored under
 *       the <em>same</em> product kernel; because the kernel is independent of the output column, the
 *       {@code n×n} covariance is Cholesky-factorized <b>once</b> and reused across all one-hot columns.
 *       With no parents this reduces to the closed-form {@code C = σ² I} sigma-only case.</li>
 * </ul>
 *
 * <p><b>Notes</b></p>
 * <ul>
 *   <li>The discrete-target score is a Gaussian multi-output surrogate, not a true multinomial
 *       likelihood, but (as in FFML) it is effective for structure scoring.</li>
 *   <li>When there are continuous parents present alongside discrete parents, the RBF bandwidth is
 *       estimated on the continuous parents only, over the rows actually used for scoring.</li>
 *   <li>Cost is {@code O(n³)} from the single Cholesky factorization (small to moderate {@code n}),
 *       the same regime as the continuous KML.</li>
 * </ul>
 *
 * @see FfMl
 * @see KernelMarginalLikelihoodScoreContinuous
 */
public final class KernelMarginalLikelihoodScore implements Score, EffectiveSampleSizeSettable {

    /**
     * The minimum number of samples required to calculate a local score.
     */
    private static final int MIN_SAMPLES_FOR_SCORE = 5;

    /**
     * If true, use valid row subsets when missing exists.
     */
    private final boolean calculateRowSubsets;

    /**
     * The dataset used for scoring.
     */
    private final DataSet dataSet;

    /**
     * The variables associated with the dataset.
     */
    private final List<Node> variables;

    /**
     * Number of rows in the dataset.
     */
    private final int sampleSize;

    /**
     * Standardized columns for continuous variables (NaNs preserved); NaN-filled for discrete columns.
     */
    private final double[][] zCols;

    /**
     * Discrete values (int codes) per variable per row; null for continuous vars.
     */
    private final int[][] dCols;

    /**
     * Which variables are discrete.
     */
    private final boolean[] isDiscrete;

    /**
     * Continuous engine. Owns the exact RBF Gram construction and the GP solvers, and handles the
     * pure-continuous local-score path.
     */
    private final KernelMarginalLikelihoodScoreContinuous cont;

    /**
     * Base ridge/noise knob. Used as sigma^2. Must be > 0.
     */
    private volatile double lambda = 1e-3;

    /**
     * Jitter escalation base for Cholesky stabilization. Must be > 0.
     */
    private volatile double jitter = 1e-10;

    /**
     * Bandwidth multiplier on the median heuristic. Forwarded to the continuous engine.
     */
    private volatile double bandwidthMultiplier = 1.0;

    /**
     * Max rows used to estimate median bandwidth. Forwarded to the continuous engine.
     */
    private volatile int bwMaxRows = 400;

    /**
     * Categorical kernel off-diagonal similarity rho in [0, 1).
     * k_cat(c,c)=1, k_cat(c,c')=rho for c!=c'. Larger rho pools across levels; smaller keeps them distinct.
     */
    private volatile double catRho = 0.5;

    /**
     * Effective sample size.
     */
    private volatile int nEff;

    /**
     * Cache: (target i, sorted parents) -> score.
     */
    private final AtomicReference<ConcurrentHashMap<Long, Double>> localScoreCacheRef =
            new AtomicReference<>(new ConcurrentHashMap<>());

    /**
     * Constructs a mixed KML score for the given dataset. Continuous columns are z-scored (NaNs
     * preserved) and discrete columns are read as integer codes, mirroring FFML. A continuous engine
     * is constructed over the same dataset to handle purely continuous local scores.
     *
     * @param dataSet the dataset. Must not be null.
     * @throws NullPointerException if the dataset is null.
     */
    public KernelMarginalLikelihoodScore(DataSet dataSet) {
        if (dataSet == null) throw new NullPointerException("dataSet");
        this.dataSet = dataSet;
        this.variables = dataSet.getVariables();
        this.sampleSize = dataSet.getNumRows();
        this.calculateRowSubsets = dataSet.existsMissingValue();

        int p = variables.size();
        this.isDiscrete = new boolean[p];
        this.dCols = new int[p][];
        double[][] raw = new double[p][sampleSize];

        for (int j = 0; j < p; j++) {
            Node v = variables.get(j);
            boolean disc = (v instanceof DiscreteVariable);
            isDiscrete[j] = disc;

            if (disc) {
                dCols[j] = new int[sampleSize];
                for (int r = 0; r < sampleSize; r++) {
                    dCols[j][r] = readDiscreteValue(dataSet, r, j);
                }
                Arrays.fill(raw[j], Double.NaN);
            } else {
                for (int r = 0; r < sampleSize; r++) {
                    raw[j][r] = dataSet.getDouble(r, j);
                }
                dCols[j] = null;
            }
        }

        this.zCols = new double[p][sampleSize];
        for (int j = 0; j < p; j++) {
            if (isDiscrete[j]) Arrays.fill(zCols[j], Double.NaN);
            else zscoreColumnPreserveNaN(raw[j], zCols[j]);
        }

        this.cont = new KernelMarginalLikelihoodScoreContinuous(dataSet);

        // Sync engine knobs to our defaults, then set effective sample size (which propagates).
        this.cont.setLambda(lambda);
        this.cont.setJitter(jitter);
        this.cont.setBandwidthMultiplier(bandwidthMultiplier);
        this.cont.setBwMaxRows(bwMaxRows);

        setEffectiveSampleSize(-1);
    }

    // -------------------- Score interface --------------------

    @Override
    public double localScoreDiff(int x, int y, int[] z) {
        return localScore(y, append(z, x)) - localScore(y, z);
    }

    @Override
    public double localScore(int i, int... parents) {
        Arrays.sort(parents);

        final long key = cacheKey(i, parents);
        final ConcurrentHashMap<Long, Double> cache = localScoreCacheRef.get();

        return cache.computeIfAbsent(key, k -> {
            try {
                // Split parents into continuous and discrete.
                int[] contP = new int[parents.length];
                int[] discP = new int[parents.length];
                int nc = 0, nd = 0;
                for (int pIdx : parents) {
                    if (isDiscrete[pIdx]) discP[nd++] = pIdx;
                    else contP[nc++] = pIdx;
                }
                contP = Arrays.copyOf(contP, nc);
                discP = Arrays.copyOf(discP, nd);

                // ---- Pure-continuous path: delegate to the continuous engine. ----
                // (Also covers the no-parent case for continuous targets, which the engine
                //  handles as the closed-form sigma-only C = sigma^2 I.)
                if (!isDiscrete[i] && nd == 0) {
                    return cont.localScore(i, parents);
                }

                // ---- Mixed / discrete-target path. ----
                int[] all = concat(i, parents);
                int[] rows = calculateRowSubsets ? validRowsMixed(all) : null;

                int n = (rows == null) ? nEff : rows.length;
                if (n < MIN_SAMPLES_FOR_SCORE) return Double.NaN;

                double sigma2 = lambda;
                if (!(sigma2 > 0) || !Double.isFinite(sigma2)) return Double.NaN;

                // No parents here implies a discrete target (continuous+no-parent was delegated).
                // Correct null model is C = sigma^2 I (NOT ones + sigma^2 I): use closed form.
                if (parents.length == 0) {
                    int[] yDisc = extractDiscrete(i, rows, n);
                    double[][] Y = oneHotCentered(yDisc);
                    if (Y == null || Y[0].length == 0) return Double.NaN;
                    return sigmaOnlyMulti(Y, sigma2);
                }

                // Build the exact product covariance C = (K_cont ⊙ K_cat) + sigma^2 I.
                // rbfGramContinuous returns the all-ones matrix when contP is empty, which is the
                // multiplicative identity for the Hadamard product, so the categorical-only case
                // reduces to K = K_cat automatically.
                DMatrixRMaj C = cont.rbfGramContinuous(contP, rows, n);
                applyCategoricalHadamard(C, discP, rows, n);
                addDiagonalInPlace(C, sigma2);

                if (!isDiscrete[i]) {
                    // Continuous target, single output.
                    double[] y = extract1DContinuous(i, rows, n);
                    centerInPlace(y);
                    return KernelMarginalLikelihoodScoreContinuous
                            .gpLogMarginalLikelihood(y, C, jitter);
                } else {
                    // Discrete target: centered one-hot multi-output, factor C once.
                    int[] yDisc = extractDiscrete(i, rows, n);
                    double[][] Y = oneHotCentered(yDisc);
                    if (Y == null || Y[0].length == 0) return Double.NaN;
                    return KernelMarginalLikelihoodScoreContinuous
                            .gpLogMarginalLikelihoodMulti(Y, C, jitter);
                }

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
        return dataSet.getNumRows();
    }

    @Override
    public int getMaxDegree() {
        return (int) TMath.ceil(TMath.log(TMath.max(5, nEff)));
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

    /**
     * Retrieves the data model used for scoring.
     *
     * @return the data model.
     */
    public DataModel getDataModel() {
        return dataSet;
    }

    @Override
    public int getEffectiveSampleSize() {
        return nEff;
    }

    @Override
    public void setEffectiveSampleSize(int nEff) {
        this.nEff = (nEff < 0) ? this.sampleSize : nEff;
        if (cont != null) cont.setEffectiveSampleSize(nEff);
        resetCache();
    }

    @Override
    public String toString() {
        return "Kernel Marginal Likelihood (product-kernel, mixed continuous+categorical)";
    }

    // -------------------- tuning knobs --------------------

    /**
     * Sets the lambda (sigma^2) hyperparameter and forwards it to the continuous engine.
     *
     * @param lambda the noise variance; must be > 0.
     */
    public void setLambda(double lambda) {
        if (lambda <= 0) throw new IllegalArgumentException("lambda must be > 0");
        this.lambda = lambda;
        if (cont != null) cont.setLambda(lambda);
        resetCache();
    }

    /**
     * Sets the Cholesky jitter base and forwards it to the continuous engine.
     *
     * @param jitter the jitter base; must be > 0.
     */
    public void setJitter(double jitter) {
        if (jitter <= 0) throw new IllegalArgumentException("jitter must be > 0");
        this.jitter = jitter;
        if (cont != null) cont.setJitter(jitter);
        resetCache();
    }

    /**
     * Sets the bandwidth multiplier and forwards it to the continuous engine.
     *
     * @param bandwidthMultiplier the multiplier; must be > 0 and finite.
     */
    public void setBandwidthMultiplier(double bandwidthMultiplier) {
        if (!(bandwidthMultiplier > 0) || !Double.isFinite(bandwidthMultiplier)) {
            throw new IllegalArgumentException("bandwidthMultiplier must be > 0");
        }
        this.bandwidthMultiplier = bandwidthMultiplier;
        if (cont != null) cont.setBandwidthMultiplier(bandwidthMultiplier);
        resetCache();
    }

    /**
     * Sets the maximum number of rows for bandwidth estimation (min 50) and forwards it.
     *
     * @param bwMaxRows the maximum number of rows.
     */
    public void setBwMaxRows(int bwMaxRows) {
        this.bwMaxRows = TMath.max(50, bwMaxRows);
        if (cont != null) cont.setBwMaxRows(this.bwMaxRows);
        resetCache();
    }

    /**
     * Retrieves the categorical similarity rho.
     *
     * @return catRho in [0, 1).
     */
    public double getCatRho() {
        return catRho;
    }

    /**
     * Sets the categorical similarity rho in [0, 1). k_cat(c,c)=1, k_cat(c,c')=rho.
     *
     * @param rho the categorical similarity; must be in [0, 1).
     */
    public void setCatRho(double rho) {
        if (!(rho >= 0.0 && rho < 1.0) || !Double.isFinite(rho)) {
            throw new IllegalArgumentException("catRho must be in [0,1)");
        }
        this.catRho = rho;
        resetCache();
    }

    // -------------------- categorical kernel --------------------

    /**
     * Applies the categorical kernel as an in-place Hadamard multiplier on {@code K}.
     * <p>
     * For each pair (i, j), multiplies {@code K(i,j)} by {@code Π_d [codes_d[i]==codes_d[j] ? 1 : rho]}.
     * The diagonal is left untouched (always a match). No-op when there are no discrete parents.
     */
    private void applyCategoricalHadamard(DMatrixRMaj K, int[] discParents, int[] rows, int n) {
        if (discParents == null || discParents.length == 0) return;

        final double rho = catRho;
        final int[][] codes = new int[discParents.length][];
        for (int t = 0; t < discParents.length; t++) {
            codes[t] = extractDiscrete(discParents[t], rows, n);
        }

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < i; j++) {
                double mult = 1.0;
                for (int[] code : codes) {
                    if (code[i] != code[j]) mult *= rho;
                }
                if (mult != 1.0) {
                    double v = K.get(i, j) * mult;
                    K.set(i, j, v);
                    K.set(j, i, v);
                }
            }
        }
    }

    /**
     * Closed-form sigma-only multi-output log marginal likelihood: C = sigma^2 I.
     * Sums -0.5*(colᵀcol / sigma^2 + n*log sigma^2) over the centered one-hot columns.
     */
    private static double sigmaOnlyMulti(double[][] Ycentered, double sigma2) {
        int n = Ycentered.length;
        if (n == 0) return Double.NaN;
        int L = Ycentered[0].length;
        if (L == 0) return Double.NaN;
        if (!(sigma2 > 0) || !Double.isFinite(sigma2)) return Double.NaN;

        double sumSq = 0.0;
        for (int r = 0; r < n; r++) {
            double[] row = Ycentered[r];
            for (int c = 0; c < L; c++) sumSq += row[c] * row[c];
        }

        double quad = sumSq / sigma2;
        double logDet = (double) L * n * TMath.log(sigma2);
        return -0.5 * quad - 0.5 * logDet;
    }

    // -------------------- one-hot expansion --------------------

    /**
     * Build a one-hot matrix for a discrete variable and center each column.
     * Levels are the distinct observed values in the current row subset.
     * Returns Y (n x L) with each column mean 0, or null if there are no levels.
     */
    private static double[][] oneHotCentered(int[] vals) {
        int n = vals.length;
        if (n == 0) return null;

        int[] uniq = Arrays.stream(vals)
                .filter(v -> v != DiscreteVariable.MISSING_VALUE && v != Integer.MIN_VALUE)
                .distinct().sorted().toArray();

        int L = uniq.length;
        if (L <= 0) return null;

        double[][] Y = new double[n][L];

        for (int r = 0; r < n; r++) {
            int v = vals[r];
            if (v == DiscreteVariable.MISSING_VALUE || v == Integer.MIN_VALUE) continue;
            int pos = Arrays.binarySearch(uniq, v);
            if (pos < 0) continue;
            Y[r][pos] = 1.0;
        }

        for (int j = 0; j < L; j++) {
            double sum = 0.0;
            for (int r = 0; r < n; r++) sum += Y[r][j];
            double mean = sum / n;
            for (int r = 0; r < n; r++) Y[r][j] -= mean;
        }

        return Y;
    }

    // -------------------- missingness row selection (mixed) --------------------

    private int[] validRowsMixed(int[] vars) {
        int n = sampleSize;
        int[] tmp = new int[n];
        int m = 0;

        outer:
        for (int r = 0; r < n; r++) {
            for (int v : vars) {
                if (isDiscrete[v]) {
                    int dv = dCols[v][r];
                    if (isMissingDiscrete(dv)) continue outer;
                } else {
                    double val = zCols[v][r];
                    if (Double.isNaN(val)) continue outer;
                }
            }
            tmp[m++] = r;
        }
        return Arrays.copyOf(tmp, m);
    }

    // -------------------- extraction + preprocessing --------------------

    private double[] extract1DContinuous(int varIndex, int[] rows, int n) {
        double[] x = new double[n];
        if (rows == null) {
            for (int r = 0; r < n; r++) x[r] = zCols[varIndex][r];
        } else {
            for (int r = 0; r < n; r++) x[r] = zCols[varIndex][rows[r]];
        }
        return x;
    }

    private int[] extractDiscrete(int varIndex, int[] rows, int n) {
        int[] x = new int[n];
        if (rows == null) {
            System.arraycopy(dCols[varIndex], 0, x, 0, n);
        } else {
            for (int r = 0; r < n; r++) x[r] = dCols[varIndex][rows[r]];
        }
        return x;
    }

    private static void centerInPlace(double[] y) {
        double m = 0.0;
        for (double v : y) m += v;
        m /= y.length;
        for (int i = 0; i < y.length; i++) y[i] -= m;
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
        double sd = TMath.sqrt(TMath.max(1e-12, var));

        for (int i = 0; i < in.length; i++) {
            double v = in[i];
            out[i] = Double.isNaN(v) ? Double.NaN : (v - mean) / sd;
        }
    }

    private static void addDiagonalInPlace(DMatrixRMaj M, double v) {
        int n = TMath.min(M.numRows, M.numCols);
        for (int i = 0; i < n; i++) M.add(i, i, v);
    }

    // -------------------- discrete reading --------------------

    private static boolean isMissingDiscrete(int v) {
        return v == DiscreteVariable.MISSING_VALUE || v == Integer.MIN_VALUE;
    }

    private static int readDiscreteValue(DataSet ds, int row, int col) {
        try {
            return ds.getInt(row, col);
        } catch (Throwable t) {
            double v = ds.getDouble(row, col);
            if (!Double.isFinite(v)) return Integer.MIN_VALUE;
            return (int) TMath.rint(v);
        }
    }

    // -------------------- cache + utilities --------------------

    /**
     * Appends an integer to an int array, returning a new array.
     *
     * @param z the original array.
     * @param x the value to append.
     * @return a new array with x appended.
     */
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

    private static long cacheKey(int i, int[] parents) {
        long h = 1469598103934665603L;
        h = (h ^ i) * 1099511628211L;
        for (int p : parents) h = (h ^ p) * 1099511628211L;
        return h;
    }

    private void resetCache() {
        localScoreCacheRef.set(new ConcurrentHashMap<>());
    }
}
