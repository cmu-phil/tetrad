package edu.cmu.tetrad.search.score;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.EffectiveSampleSizeSettable;
import edu.cmu.tetrad.util.TetradLogger;
import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.factory.DecompositionFactory_DDRM;
import org.ejml.interfaces.decomposition.CholeskyDecomposition_F64;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * KFF-ML Mixed score: continuous + discrete parents.
 *
 * <p>This variant extends KFF-ML to handle mixed continuous/discrete parent sets by using a
 * product kernel:
 *
 * <pre>
 *   k((z_c, z_d), (z'_c, z'_d)) = k_cont(z_c, z'_c) * k_cat(z_d, z'_d)
 * </pre>
 *
 * where:
 * <ul>
 *   <li>{@code k_cont} is an RBF kernel approximated with RFF/ORF as in KFF-ML.</li>
 *   <li>{@code k_cat} is a simple PSD categorical kernel on the discrete levels:
 *       {@code k_cat(c,c)=1} and {@code k_cat(c,c')=rho} for {@code c!=c'}.</li>
 * </ul>
 *
 * <p>The product kernel is represented via a Kronecker feature map:
 *
 * <pre>
 *   phi_mix(z_c, z_d) = phi_cat(z_d) ⊗ phi_cont(z_c)
 * </pre>
 *
 * For Auto-MPG's Origin (3 levels), this multiplies the feature dimension by 3 and avoids
 * treating discrete codes as continuous numeric inputs to an RBF kernel.
 */
public final class KffMlMixed implements Score, EffectiveSampleSizeSettable {

    public enum FeatureType { RFF, ORF }

    // -------------------- configuration knobs --------------------

    /** Base ridge/noise knob. Used as sigma^2. Must be > 0. */
    private volatile double lambda = 1.0;

    /** If true, use valid row subsets when missing exists. */
    private final boolean calculateRowSubsets;

    /** Bandwidth multiplier on the median heuristic (continuous part only). */
    private volatile double bandwidthMultiplier = 1.0;

    /** Max rows used to estimate median bandwidth (subsample for speed). */
    private volatile int bwMaxRows = 400;

    /** Number of random features for continuous kernel approximation (m). */
    private volatile int numFeatures = 256;

    /** Effective sample size. */
    private volatile int nEff;

    /** RFF vs ORF for continuous features. */
    private FeatureType featureType = FeatureType.ORF;

    /**
     * Categorical kernel off-diagonal similarity rho in [0, 1).
     * k_cat(c,c)=1, k_cat(c,c')=rho for c!=c'.
     *
     * For 3-level Origin, rho ~ 0.3..0.8 is a reasonable range.
     * rho closer to 0 => categories treated as very distinct blocks.
     * rho closer to 1 => categories treated as nearly identical.
     */
    private volatile double catRho = 0.8;

    // -------------------- data --------------------

    private final DataSet dataSet;
    private final List<Node> variables;
    private final int sampleSize;

    /** Standardized columns for continuous variables only (NaNs preserved). */
    private final double[][] zCols;

    /** Discrete values (int codes) per variable per row; null for continuous vars. */
    private final int[][] dCols;

    /** Which variables are discrete. */
    private final boolean[] isDiscrete;

    /** Cache: (target i, sorted parents) -> score. */
    private final AtomicReference<ConcurrentHashMap<Long, Double>> localScoreCacheRef =
            new AtomicReference<>(new ConcurrentHashMap<>());

    public KffMlMixed(DataSet dataSet) {
        if (dataSet == null) throw new NullPointerException("dataSet");
        this.dataSet = dataSet;
        this.variables = dataSet.getVariables();
        this.sampleSize = dataSet.getNumRows();
        setEffectiveSampleSize(-1);

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
                // raw not used for discrete
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
            if (isDiscrete[j]) {
                Arrays.fill(zCols[j], Double.NaN);
            } else {
                zscoreColumnPreserveNaN(raw[j], zCols[j]);
            }
        }
    }

    // -------------------- Score interface --------------------

    @Override
    public double localScoreDiff(int x, int y, int[] z) {
        return localScore(y, append(z, x)) - localScore(y, z);
    }

    @Override
    public double localScore(int i, int... parents) {
        Arrays.sort(parents);
        long key = cacheKey(i, parents);

        final ConcurrentHashMap<Long, Double> cache = localScoreCacheRef.get();

        return cache.computeIfAbsent(key, k -> {
            try {
                int[] all = concat(i, parents);
                int[] rows = calculateRowSubsets ? validRowsMixed(all) : null;

                int n = (rows == null) ? nEff : rows.length;
                if (n < 5) return Double.NaN;

                double sigma2 = lambda;
                if (!(sigma2 > 0) || !Double.isFinite(sigma2)) return Double.NaN;

                // Split parents into continuous and discrete
                int[] contParents = new int[parents.length];
                int[] discParents = new int[parents.length];
                int nc = 0, nd = 0;

                for (int pIdx : parents) {
                    if (isDiscrete[pIdx]) discParents[nd++] = pIdx;
                    else contParents[nc++] = pIdx;
                }

                contParents = Arrays.copyOf(contParents, nc);
                discParents = Arrays.copyOf(discParents, nd);

                // Bandwidth from continuous parents only; if none, default bw2=1.
                double bw2 = 1.0;
                if (nc > 0) {
                    double[][] Zc = new double[n][nc];
                    for (int r = 0; r < n; r++) {
                        int row = (rows == null) ? r : rows[r];
                        for (int j = 0; j < nc; j++) {
                            Zc[r][j] = zCols[contParents[j]][row];
                        }
                    }
                    bw2 = medianDistanceSquaredND(Zc, Math.min(n, bwMaxRows));
                    if (!(bw2 > 0) || !Double.isFinite(bw2)) bw2 = 1.0;
                    bw2 *= (bandwidthMultiplier * bandwidthMultiplier);
                }

                // Deterministic seed per (target, parent set)
                long seed = key ^ 0x9E3779B97F4A7C15L;

                // ----------------------------
                // CASE A: continuous target (your existing behavior)
                // ----------------------------
                if (!isDiscrete[i]) {
                    double[] y = extract1DContinuous(i, rows, n);
                    centerInPlace(y);

                    // Exact closed-form for no parents: C = sigma2 I
                    if (parents.length == 0) {
                        return gpLogMarginalLikelihoodSigmaOnly(y, sigma2);
                    }

                    return gpLogMarginalLikelihoodRFFMixed(
                            y, contParents, discParents, rows, n,
                            numFeatures, bw2, sigma2, seed
                    );
                }

                // ----------------------------
                // CASE B: discrete target (new)
                // One-hot encode child, center columns, score each column with GP-ML and sum.
                // ----------------------------

                int[] yDisc = extractDiscrete(i, rows, n);
                double[][] Y = oneHotCentered(yDisc); // n x L, each col centered

                if (Y == null || Y[0].length == 0) return Double.NaN;

                // If no parents: sigma-only per column (fast)
                if (parents.length == 0) {
                    return gpLogMarginalLikelihoodSigmaOnlyMulti(Y, sigma2);
                }

                // With parents: sum GP-ML across columns
                return gpLogMarginalLikelihoodRFFMixedMultiOutput(
                        Y, contParents, discParents, rows, n,
                        numFeatures, bw2, sigma2, seed
                );

            } catch (RuntimeException e) {
                TetradLogger.getInstance().log(e.getMessage());
                return Double.NaN;
            }
        });
    }

    /**
     * Build a one-hot matrix for a discrete variable and center each column.
     * Levels are taken as the distinct observed values in the current row subset.
     *
     * Returns Y (n x L) where each column has mean 0.
     */
    private static double[][] oneHotCentered(int[] vals) {
        int n = vals.length;
        if (n == 0) return null;

        // Use observed levels in this subset (important when some levels are absent after filtering)
        int[] uniq = Arrays.stream(vals).distinct().sorted().toArray();
        int L = uniq.length;
        if (L <= 0) return null;

        // Map each value -> level index 0..L-1
        double[][] Y = new double[n][L];

        for (int r = 0; r < n; r++) {
            int v = vals[r];
            int pos = Arrays.binarySearch(uniq, v);
            if (pos < 0) {
                // Shouldn't happen, but defensively clamp
                pos = 0;
            }
            Y[r][pos] = 1.0;
        }

        // Center each column
        for (int j = 0; j < L; j++) {
            double sum = 0.0;
            for (int r = 0; r < n; r++) sum += Y[r][j];
            double mean = sum / n;
            for (int r = 0; r < n; r++) Y[r][j] -= mean;
        }

        return Y;
    }

    /**
     * Sigma-only multi-output: sum the sigma-only GP marginal likelihood across columns.
     * Each column is treated as an independent output with the same sigma^2 I covariance.
     */
    private static double gpLogMarginalLikelihoodSigmaOnlyMulti(double[][] Ycentered, double sigma2) {
        int n = Ycentered.length;
        if (n == 0) return Double.NaN;
        int L = Ycentered[0].length;
        if (L == 0) return Double.NaN;

        double sum = 0.0;
        for (int j = 0; j < L; j++) {
            double[] col = new double[n];
            for (int r = 0; r < n; r++) col[r] = Ycentered[r][j];
            sum += gpLogMarginalLikelihoodSigmaOnly(col, sigma2);
        }
        return sum;
    }

    /**
     * Mixed multi-output GP-ML surrogate for discrete targets:
     * score each centered one-hot column using your existing mixed-parent GP-ML,
     * and sum across columns.
     *
     * Note: This is a Gaussian multi-output surrogate (not a true multinomial likelihood),
     * but it is often very effective for structure scoring (detecting dependencies).
     */
    private double gpLogMarginalLikelihoodRFFMixedMultiOutput(
            double[][] Ycentered,          // n x L, columns already centered
            int[] contParents,
            int[] discParents,
            int[] rows,
            int n,
            int mFeatures,
            double bw2,
            double sigma2,
            long seed
    ) {
        int L = Ycentered[0].length;
        if (L == 0) return Double.NaN;

        double total = 0.0;

        // Simple deterministic per-output seed derivation
        long s = seed;
        for (int j = 0; j < L; j++) {
            double[] yj = new double[n];
            for (int r = 0; r < n; r++) yj[r] = Ycentered[r][j];

            // Slightly perturb seed per column so the RFF sampling isn't identical across outputs
            s = mix64(s + 0x9E3779B97F4A7C15L + j);

            double ll = gpLogMarginalLikelihoodRFFMixed(
                    yj, contParents, discParents, rows, n,
                    mFeatures, bw2, sigma2, s
            );
            if (!Double.isFinite(ll)) return Double.NaN;
            total += ll;
        }

        return total;
    }

    /** A tiny 64-bit mixing function for seed diversification. */
    private static long mix64(long z) {
        z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
        z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return z ^ (z >>> 33);
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
        return dataSet;
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
        return "KFF-ML Mixed (continuous+categorical product-kernel)";
    }

    // -------------------- tuning knobs --------------------

    public void setLambda(double lambda) {
        if (lambda <= 0) throw new IllegalArgumentException("lambda must be > 0");
        this.lambda = lambda;
        resetCache();
    }

    public void setBandwidthMultiplier(double bandwidthMultiplier) {
        if (!(bandwidthMultiplier > 0) || !Double.isFinite(bandwidthMultiplier)) {
            throw new IllegalArgumentException("bandwidthMultiplier must be > 0");
        }
        this.bandwidthMultiplier = bandwidthMultiplier;
        resetCache();
    }

    public void setBwMaxRows(int bwMaxRows) {
        this.bwMaxRows = Math.max(50, bwMaxRows);
        resetCache();
    }

    public void setNumFeatures(int numFeatures) {
        this.numFeatures = numFeatures;
        resetCache();
    }

    public void setFeatureType(FeatureType featureType) {
        if (featureType == null) throw new IllegalArgumentException("featureType cannot be null");
        this.featureType = featureType;
        resetCache();
    }

    public FeatureType getFeatureType() {
        return featureType;
    }

    /**
     * Set categorical similarity rho in [0,1).
     * Try 0.3, 0.5, 0.7 for Auto-MPG Origin.
     */
    public void setCatRho(double rho) {
        if (!(rho >= 0.0 && rho < 1.0) || !Double.isFinite(rho)) {
            throw new IllegalArgumentException("catRho must be in [0,1)");
        }
        this.catRho = rho;
        resetCache();
    }

    public double getCatRho() {
        return catRho;
    }

    // -------------------- mixed GP-ML core (RFF/ORF + categorical kernel) --------------------

    /**
     * Mixed parent set version:
     * - Continuous parents approximated with RFF/ORF producing phi_cont in R^m.
     * - Each discrete parent uses a small exact feature map phi_cat in R^{L} such that
     *   phi_cat(c)^T phi_cat(c') = k_cat(c,c').
     * - Combined feature map is Kronecker product across discrete parents and continuous features.
     *
     * For one discrete parent with L=3 and m=256, total feature dimension is 768.
     */
    private double gpLogMarginalLikelihoodRFFMixed(
            double[] yCentered,          // length n
            int[] contParents,           // continuous parent indices
            int[] discParents,           // discrete parent indices
            int[] rows,                  // null or length n, mapping into original rows
            int n,
            int mFeatures,               // m (continuous features)
            double bw2,                  // bandwidth^2 for continuous part
            double sigma2,
            long seed
    ) {
        if (n < 5) return Double.NaN;
        if (!(sigma2 > 0) || !Double.isFinite(sigma2)) return Double.NaN;

        final int dc = contParents.length;

        // ---------- continuous RFF/ORF sampler ----------
        double[][] W = null;
        double[] b = null;

        double wStd = 1.0;
        if (dc > 0) {
            if (!(bw2 > 0) || !Double.isFinite(bw2)) bw2 = 1.0;
            wStd = Math.sqrt(2.0 / bw2);
        }
        final double contScale = Math.sqrt(2.0 / mFeatures);

        SplittableRandom rng = new SplittableRandom(seed);

        if (dc > 0) {
            if (featureType == FeatureType.RFF) {
                W = new double[mFeatures][dc];
                b = new double[mFeatures];
                for (int j = 0; j < mFeatures; j++) {
                    for (int k = 0; k < dc; k++) W[j][k] = wStd * nextGaussian(rng);
                    b[j] = 2.0 * Math.PI * rng.nextDouble();
                }
            } else {
                W = sampleOrthogonalW(mFeatures, dc, wStd, rng);
                b = new double[mFeatures];
                for (int j = 0; j < mFeatures; j++) b[j] = 2.0 * Math.PI * rng.nextDouble();
            }
        } else {
            // no continuous inputs => phi_cont is constant cos(b)
            b = new double[mFeatures];
            for (int j = 0; j < mFeatures; j++) b[j] = 2.0 * Math.PI * rng.nextDouble();
        }

        // ---------- categorical feature maps (exact) ----------
        // For each discrete parent, compute levels L and a Cholesky-based feature map A (LxL) s.t. A A^T = K_levels.
        final CatFeatureMap[] catMaps = new CatFeatureMap[discParents.length];
        for (int t = 0; t < discParents.length; t++) {
            int var = discParents[t];
            int[] vals = extractDiscrete(var, rows, n);
            catMaps[t] = buildCatMap(vals, catRho);
            if (catMaps[t] == null) return Double.NaN;
        }

        // total feature dimension: mFeatures * product(L_t)
        int catDim = 1;
        for (CatFeatureMap fm : catMaps) {
            // guard: don’t explode feature space
            if (fm.L > 50) return Double.NaN;
            long prod = (long) catDim * (long) fm.L;
            if (prod > 200_000L) return Double.NaN; // defensive cap
            catDim *= fm.L;
        }
        final int mTotal = mFeatures * catDim;

        // Accumulate G = Phi^T Phi (mTotal x mTotal) and v = Phi^T y (mTotal)
        DMatrixRMaj G = new DMatrixRMaj(mTotal, mTotal);
        double[] v = new double[mTotal];

        double yTy = 0.0;

        // temp vectors
        double[] phiCont = new double[mFeatures];
        double[] phiMix = new double[mTotal]; // used as scratch per row

        for (int ii = 0; ii < n; ii++) {
            int row = (rows == null) ? ii : rows[ii];

            // ----- build continuous features -----
            if (dc == 0) {
                for (int j = 0; j < mFeatures; j++) phiCont[j] = contScale * Math.cos(b[j]);
            } else {
                for (int j = 0; j < mFeatures; j++) {
                    double dot = 0.0;
                    double[] wj = W[j];
                    for (int k = 0; k < dc; k++) dot += wj[k] * zCols[contParents[k]][row];
                    phiCont[j] = contScale * Math.cos(dot + b[j]);
                }
            }

            // ----- build categorical Kronecker expansion -----
            // Start with base = phiCont, then expand by each discrete parent’s cat features.
            // We fill phiMix as length mTotal.
            // Work arrays: we do iterative expansion into phiMix.
            // Step 1: initialize with phiCont as current block of length mFeatures.
            // Then for each cat parent, expand: newLen = oldLen * L, with elementwise products.
            int curLen = mFeatures;
            // copy phiCont into the beginning of phiMix as the current vector
            System.arraycopy(phiCont, 0, phiMix, 0, mFeatures);

            for (CatFeatureMap fm : catMaps) {
                double[] catFeat = fm.featureForRow(ii); // length L
                int L = fm.L;

                int newLen = curLen * L;

                // Expand into a scratch region at the end; easiest is to use a temp array.
                double[] tmp = new double[newLen];
                int pos = 0;
                for (int a = 0; a < L; a++) {
                    double ca = catFeat[a];
                    for (int j = 0; j < curLen; j++) {
                        tmp[pos++] = ca * phiMix[j];
                    }
                }
                // copy back
                System.arraycopy(tmp, 0, phiMix, 0, newLen);
                curLen = newLen;
            }

            if (curLen != mTotal) return Double.NaN;

            double yi = yCentered[ii];
            yTy += yi * yi;

            // v += phiMix * yi
            for (int j = 0; j < mTotal; j++) v[j] += phiMix[j] * yi;

            // G += phiMix^T phiMix (rank-1 outer product, symmetric fill lower triangle)
            for (int j = 0; j < mTotal; j++) {
                double pj = phiMix[j];
                for (int k = 0; k <= j; k++) {
                    G.add(j, k, pj * phiMix[k]);
                }
            }
        }

        // Mirror lower -> upper
        for (int j = 0; j < mTotal; j++) {
            for (int k = 0; k < j; k++) {
                G.set(k, j, G.get(j, k));
            }
        }

        // B = G + sigma2 I
        DMatrixRMaj B = G;
        for (int j = 0; j < mTotal; j++) B.add(j, j, sigma2);

        // Cholesky(B)
        CholeskyDecomposition_F64<DMatrixRMaj> chol = DecompositionFactory_DDRM.chol(true);
        if (!chol.decompose(B)) return Double.NaN;

        DMatrixRMaj L = chol.getT(null);

        // logdet(B) = 2 * sum log diag(L)
        double logDetB = 0.0;
        for (int i = 0; i < mTotal; i++) {
            double di = L.get(i, i);
            if (!(di > 0) || !Double.isFinite(di)) return Double.NaN;
            logDetB += Math.log(di);
        }
        logDetB *= 2.0;

        // Solve B^{-1} v: two triangular solves in-place on u
        double[] u = Arrays.copyOf(v, mTotal);

        // forward solve: L u = v
        for (int i = 0; i < mTotal; i++) {
            double sum = u[i];
            for (int j = 0; j < i; j++) sum -= L.get(i, j) * u[j];
            u[i] = sum / L.get(i, i);
        }
        // back solve: L^T w = u  (store w back in u)
        for (int i = mTotal - 1; i >= 0; i--) {
            double sum = u[i];
            for (int j = i + 1; j < mTotal; j++) sum -= L.get(j, i) * u[j];
            u[i] = sum / L.get(i, i);
        }

        double vTBInvV = 0.0;
        for (int j = 0; j < mTotal; j++) vTBInvV += v[j] * u[j];

        double invSig = 1.0 / sigma2;
        double quad = invSig * yTy - (invSig * invSig) * vTBInvV;

        // log|C| = (n - mTotal) log sigma2 + log|B|
        double logDetC = (n - mTotal) * Math.log(sigma2) + logDetB;

        if (!Double.isFinite(quad) || !Double.isFinite(logDetC)) return Double.NaN;

        return -0.5 * quad - 0.5 * logDetC;
    }

    // -------------------- categorical kernel helper --------------------

    private static final class CatFeatureMap {
        final int L;
        final int[] levelOfRow;     // maps each row (0..n-1) to 0..L-1
        final double[][] A;         // L x L, rows are features for each level

        CatFeatureMap(int L, int[] levelOfRow, double[][] A) {
            this.L = L;
            this.levelOfRow = levelOfRow;
            this.A = A;
        }

        double[] featureForRow(int rowIndexWithinActive) {
            int lev = levelOfRow[rowIndexWithinActive];
            return A[lev];
        }
    }

    /**
     * Build an exact feature map for k_cat where diag=1 and offdiag=rho.
     * We:
     *  - find unique levels among vals
     *  - map them to 0..L-1
     *  - form K_levels (LxL)
     *  - Cholesky factor K_levels = A A^T
     *  - feature(level)=row of A
     */
    private static CatFeatureMap buildCatMap(int[] vals, double rho) {
        // map distinct values to compact 0..L-1
        int n = vals.length;

        int[] uniq = Arrays.stream(vals).distinct().sorted().toArray();
        int L = uniq.length;
        if (L <= 0) return null;

        int[] levelOfRow = new int[n];
        for (int i = 0; i < n; i++) {
            int v = vals[i];
            int pos = Arrays.binarySearch(uniq, v);
            if (pos < 0) pos = 0;
            levelOfRow[i] = pos;
        }

        // K_levels
        double[][] K = new double[L][L];
        for (int i = 0; i < L; i++) {
            for (int j = 0; j < L; j++) {
                K[i][j] = (i == j) ? 1.0 : rho;
            }
        }

        // Cholesky: K = A A^T (A lower-tri)
        double[][] A = choleskyLower(K);
        if (A == null) return null;

        // We want features as rows of A.
        // A is lower triangular; that’s fine: row i is a length-L feature vector.
        return new CatFeatureMap(L, levelOfRow, A);
    }

    private static double[][] choleskyLower(double[][] M) {
        int n = M.length;
        double[][] L = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j <= i; j++) {
                double sum = M[i][j];
                for (int k = 0; k < j; k++) sum -= L[i][k] * L[j][k];

                if (i == j) {
                    if (sum <= 1e-15) return null;
                    L[i][j] = Math.sqrt(sum);
                } else {
                    L[i][j] = sum / L[j][j];
                }
            }
        }
        return L;
    }

    // -------------------- sigma-only case --------------------

    private static double gpLogMarginalLikelihoodSigmaOnly(double[] yCentered, double sigma2) {
        int n = yCentered.length;
        if (n == 0) return Double.NaN;
        if (!(sigma2 > 0) || !Double.isFinite(sigma2)) return Double.NaN;

        double yTy = 0.0;
        for (double v : yCentered) yTy += v * v;

        double quad = yTy / sigma2;
        double logDet = n * Math.log(sigma2);

        return -0.5 * quad - 0.5 * logDet;
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
                    // For discrete, treat Integer.MIN_VALUE as missing if present.
                    int dv = dCols[v][r];
                    if (dv == Integer.MIN_VALUE) continue outer;
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
        if (isDiscrete[varIndex]) {
            // If someone scores a discrete target with this, we refuse (for now).
            return new double[n]; // will lead to nonsense; better to return NaN upstream if desired.
        }
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
        double sd = Math.sqrt(Math.max(1e-12, var));

        for (int i = 0; i < in.length; i++) {
            double v = in[i];
            out[i] = Double.isNaN(v) ? Double.NaN : (v - mean) / sd;
        }
    }

    // -------------------- ORF / Gaussian helpers --------------------

    private static double[][] sampleOrthogonalW(int mFeatures, int d, double wStd, SplittableRandom rng) {
        double[][] W = new double[mFeatures][d];
        if (d <= 0) return W;

        int filled = 0;
        while (filled < mFeatures) {
            int block = Math.min(d, mFeatures - filled);

            double[][] Q = new double[block][d];
            for (int i = 0; i < block; i++) {
                for (int j = 0; j < d; j++) Q[i][j] = nextGaussian(rng);
            }

            for (int i = 0; i < block; i++) {
                for (int k = 0; k < i; k++) {
                    double dot = 0.0;
                    for (int j = 0; j < d; j++) dot += Q[i][j] * Q[k][j];
                    for (int j = 0; j < d; j++) Q[i][j] -= dot * Q[k][j];
                }
                double norm2 = 0.0;
                for (int j = 0; j < d; j++) norm2 += Q[i][j] * Q[i][j];
                double norm = Math.sqrt(Math.max(1e-18, norm2));
                for (int j = 0; j < d; j++) Q[i][j] /= norm;
            }

            for (int i = 0; i < block; i++) {
                double r = chiRadius(d, rng);
                double s = wStd * r;
                int outRow = filled + i;
                for (int j = 0; j < d; j++) W[outRow][j] = s * Q[i][j];
            }

            filled += block;
        }

        return W;
    }

    private static double chiRadius(int d, SplittableRandom rng) {
        double ss = 0.0;
        for (int k = 0; k < d; k++) {
            double g = nextGaussian(rng);
            ss += g * g;
        }
        return Math.sqrt(Math.max(1e-18, ss));
    }

    private static double nextGaussian(SplittableRandom rng) {
        double u, v, s;
        do {
            u = 2.0 * rng.nextDouble() - 1.0;
            v = 2.0 * rng.nextDouble() - 1.0;
            s = u * u + v * v;
        } while (s >= 1.0 || s == 0.0);
        return u * Math.sqrt(-2.0 * Math.log(s) / s);
    }

    // -------------------- median heuristic on continuous part --------------------

    private static double medianDistanceSquaredND(double[][] Z, int maxRows) {
        int n = Z.length;
        int d = Z[0].length;
        if (n < 3) return 1.0;

        int m = Math.min(n, maxRows);

        int[] idx = new int[m];
        if (m == n) {
            for (int i = 0; i < m; i++) idx[i] = i;
        } else {
            for (int i = 0; i < m; i++) idx[i] = (int) Math.floor((i * (long) (n - 1)) / (double) (m - 1));
        }

        int cnt = m * (m - 1) / 2;
        double[] d2 = new double[cnt];
        int t = 0;

        for (int a = 1; a < m; a++) {
            int i = idx[a];
            for (int b = 0; b < a; b++) {
                int j = idx[b];
                double dist2 = 0.0;
                for (int k = 0; k < d; k++) {
                    double diff = Z[i][k] - Z[j][k];
                    dist2 += diff * diff;
                }
                d2[t++] = dist2;
            }
        }

        Arrays.sort(d2, 0, t);

        int firstPos = 0;
        while (firstPos < t && d2[firstPos] <= 0) firstPos++;
        if (firstPos >= t) return 1.0;

        int mid = firstPos + (t - firstPos) / 2;
        return d2[mid];
    }

    // -------------------- cache + utilities --------------------

    private void resetCache() {
        localScoreCacheRef.set(new ConcurrentHashMap<>());
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

    private static long cacheKey(int i, int[] parents) {
        long h = 1469598103934665603L;
        h = (h ^ i) * 1099511628211L;
        for (int p : parents) h = (h ^ p) * 1099511628211L;
        return h;
    }

    // -------------------- discrete reading --------------------

    private static int readDiscreteValue(DataSet ds, int row, int col) {
        try {
            return ds.getInt(row, col);
        } catch (Throwable t) {
            // Fallback if dataset stores codes as doubles
            double v = ds.getDouble(row, col);
            if (!Double.isFinite(v)) return Integer.MIN_VALUE;
            return (int) Math.rint(v);
        }
    }
}