package edu.cmu.tetrad.search.score;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.EffectiveSampleSizeSettable;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TMath;
import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.factory.DecompositionFactory_DDRM;
import org.ejml.interfaces.decomposition.CholeskyDecomposition_F64;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * <p><b>FF-ML: Feature-Function Marginal Likelihood score (GP form, continuous)</b></p>
 *
 * <p>
 * This class implements a fast, nonlinear local score for structure learning based on a
 * Gaussian-process (GP) marginal likelihood model with an RBF kernel, approximated using a
 * finite-dimensional random-feature map (Random Fourier Features or Orthogonal Random Features).
 * It is designed for use in score-based searches (FGES/BOSS-style) where thousands of local scores
 * must be evaluated efficiently.
 * </p>
 *
 * <p><b>Model and objective</b></p>
 * <p>
 * For a target variable {@code Y} and candidate parent set {@code Pa(Y)}, we model
 * {@code Y = f(Pa(Y)) + ε}, with {@code ε ~ N(0, σ² I)} and {@code f} drawn from a GP with RBF kernel.
 * The local score is the (log) GP marginal likelihood of {@code Y} given {@code Pa(Y)}, computed
 * in a random-feature approximation.
 * </p>
 *
 * <p><b>Random-feature approximation</b></p>
 * <p>
 * Instead of forming the full {@code n×n} kernel matrix, we approximate the RBF kernel using a
 * feature matrix {@code Φ ∈ R^{n×m}}:
 * </p>
 * <ul>
 *   <li>{@code Φ} is constructed from either <b>RFF</b> or <b>ORF</b> features over the parent columns.</li>
 *   <li>The RBF bandwidth is chosen by a median pairwise squared-distance heuristic on the (z-scored) parents,
 *       using at most {@code bwMaxRows} rows for speed, then scaled by {@code bandwidthMultiplier}.</li>
 *   <li>Random-feature generation is deterministic per (target, parent set) via a fixed seed derived from the cache key,
 *       ensuring reproducibility and stable caching across repeated calls.</li>
 * </ul>
 *
 * <p><b>Efficient GP log marginal likelihood (Woodbury form)</b></p>
 * <p>
 * With {@code C = ΦΦᵀ + σ²I}, the GP log marginal likelihood involves {@code yᵀ C^{-1} y} and {@code log|C|}.
 * Using the random-feature representation, these are computed via an {@code m×m} system:
 * {@code B = ΦᵀΦ + σ² I_m}, avoiding {@code n×n} matrix factorization.
 * </p>
 *
 * <p><b>Regularization / noise parameter</b></p>
 * <p>
 * The parameter {@code lambda} in this implementation is used as the noise variance {@code σ²} (must be &gt; 0).
 * It stabilizes the computation and controls smoothness/fit in the GP objective.
 * </p>
 *
 * <p><b>Missing data</b></p>
 * <p>
 * If missing values occur and {@code calculateRowSubsets} is enabled, each local score is computed on the
 * subset of rows where {@code Y} and its parent variables are all observed (testwise deletion at the local-score level).
 * </p>
 *
 * <p><b>Practical notes</b></p>
 * <ul>
 *   <li>This score is intended for <b>continuous</b> variables (columns are globally z-scored; NaNs preserved).</li>
 *   <li>Runtime is dominated by forming {@code ΦᵀΦ} and {@code Φᵀy}, i.e., roughly {@code O(n m d)} with
 *       parents dimension {@code d} and feature dimension {@code m}, plus an {@code O(m³)} Cholesky on {@code B}.</li>
 *   <li>Results can be sensitive to {@code numFeatures} and bandwidth selection; these control the speed/accuracy tradeoff.</li>
 * </ul>
 */
public final class FfMlContinuous implements Score, EffectiveSampleSizeSettable {

    /**
     * If true, use valid row subsets when missing exists.
     */
    private final boolean calculateRowSubsets;

    // -------------------- configuration knobs --------------------
    /**
     * The dataset containing the observations or measurements used for
     * computations in the KffMarginalLikelihoodScore class.
     * This dataset is central to various scoring and statistical
     * methods implemented in this class, serving as the underlying
     * data source for evaluating marginal likelihoods, determining
     * conditional dependencies, and other related operations.
     */
    private final DataSet dataSet;
    // Cached per-child phases b_child[k], length mFeatures.
    private final AtomicReference<ConcurrentHashMap<Long, double[]>> phaseCacheRef =
            new AtomicReference<>(new ConcurrentHashMap<>());
    // Cached per-parent base omegas g_parent[k] ~ N(0,1), length mFeatures.
// We later scale by wStd = sqrt(2/bw2Child).
    private final AtomicReference<ConcurrentHashMap<Long, double[]>> omegaCacheRef =
            new AtomicReference<>(new ConcurrentHashMap<>());
    /**
     * A list of variables represented as {@code Node} objects. These variables
     * are used in the context of scoring and computations within the
     * {@code KffMarginalLikelihoodScore} class.
     * <p>
     * The {@code variables} list typically holds the nodes or features that form
     * the underlying structure of the dataset being analyzed. It is a fundamental
     * component utilized for various methods and calculations within the class,
     * such as determining local scores, effective sample size, and evaluating
     * dependencies.
     */
    private final List<Node> variables;
    /**
     * Represents the total number of data points (or observations) used in the computations for this instance.
     * This value is fixed for the lifetime of the object and is initialized during object construction.
     * <p>
     * The sample size is an essential parameter in statistical modeling and influences the calculations
     * of marginal likelihood scores and other statistical measures within this class.
     */
    private final int sampleSize;
    /**
     * Standardized columns (z-scored globally, NaNs preserved). zCols[p][n].
     */
    private final double[][] zCols;
    /**
     * Cache: (target i, sorted parents) -> score.
     */
    private final AtomicReference<ConcurrentHashMap<Long, Double>> localScoreCacheRef =
            new AtomicReference<>(new ConcurrentHashMap<>());
    /**
     * Base ridge/noise knob. Used to form sigma^2. Must be > 0.
     */
    private volatile double lambda = 1.0;

    // -------------------- new caches for nested RFF --------------------
    /**
     * Bandwidth multiplier on the median heuristic.
     * 1.0 is default; try 0.5 or 2.0 if things feel too smooth/too spiky.
     */
    private volatile double bandwidthMultiplier = 1.0;
    /**
     * Max rows used to estimate median bandwidth (subsample for speed).
     * Kernel itself still uses all rows.
     */
    private volatile int bwMaxRows = 400;
    /**
     * Represents the number of random features used for certain kernel approximations
     * like Random Fourier Features (RFF) or Orthogonal Random Features (ORF).
     * The value of this variable may impact the accuracy and computational
     * efficiency of approximate methods for evaluating kernel-based models.
     * <p>
     * This variable is volatile to ensure thread-safe read and write
     * operations in a concurrent environment.
     */
    private volatile int numFeatures = 256;
    /**
     * Represents the effective sample size, which is a measure of the
     * equivalent number of independent observations in a statistical model.
     * This variable is used to adjust computations to account for dependencies
     * or other factors that reduce the effective sample count.
     * <p>
     * The value of this variable may affect various calculations in the
     * {@code KffMarginalLikelihoodScore} class, including likelihood scores
     * and related model evaluations.
     * <p>
     * This field is declared as {@code volatile} to ensure thread-safe
     * read and write operations in a concurrent environment.
     */
    private volatile int nEff;
    /**
     * The feature type utilized in the current instance for random feature mapping.
     * This variable determines the specific method applied for generating random features
     * within the associated statistical models or machine learning algorithms.
     * <p>
     * The supported feature types are:
     * <ol>
     * <li>{@code FeatureType.RFF}: Random Fourier Features, a method for kernel approximation through random projections.
     * <li>{@code FeatureType.ORF}: Orthogonal Random Features, an extension of Random Fourier Features
     * /ol>
     * ensuring orthogonality in the generated projections, often enhancing stability and performance.
     * <p>
     * The default value is set to {@code FeatureType.ORF}, indicating the use of Orthogonal Random Features.
     */
    private FeatureType featureType = FeatureType.ORF;
    // Cached per-child bandwidth^2 (computed once per child from "all other variables").
    private final double[] childBw2Cache;  // length p, lazily filled with NaNs

    /**
     * Constructs a KffMl instance for performing kernel-based statistical analysis on the given dataset.
     *
     * @param dataSet The input dataset on which the kernel-based methods will operate.
     *                Must not be null, and is expected to contain variables and rows with numeric data.
     *                If any missing values exist in the dataset, the class will appropriately flag it during instantiation.
     * @throws NullPointerException If the provided {@code dataSet} is null.
     */
    public FfMlContinuous(DataSet dataSet) {
        if (dataSet == null) throw new NullPointerException("dataSet");
        this.dataSet = dataSet;
        this.variables = dataSet.getVariables();
        this.sampleSize = dataSet.getNumRows();
        setEffectiveSampleSize(-1);

        this.calculateRowSubsets = dataSet.existsMissingValue();

        // Extract + z-score each column once (globally). Keeps kernels sane.
        int p = variables.size();
        double[][] cols = new double[p][sampleSize];
        for (int j = 0; j < p; j++) {
            for (int r = 0; r < sampleSize; r++) cols[j][r] = dataSet.getDouble(r, j);
        }

        this.zCols = new double[p][sampleSize];
        for (int j = 0; j < p; j++) {
            zscoreColumnPreserveNaN(cols[j], zCols[j]);
        }

        this.childBw2Cache = new double[p];
        Arrays.fill(this.childBw2Cache, Double.NaN);
    }

    /**
     * Exact sigma-only case: C = sigma2 I.
     */
    private static double gpLogMarginalLikelihoodSigmaOnly(double[] yCentered, double sigma2) {
        int n = yCentered.length;
        if (n == 0) return Double.NaN;
        if (!(sigma2 > 0) || !Double.isFinite(sigma2)) return Double.NaN;

        double yTy = 0.0;
        for (double v : yCentered) yTy += v * v;

        double quad = yTy / sigma2;
        double logDet = n * TMath.log(sigma2);

        return -0.5 * quad - 0.5 * logDet;
    }

    // -------------------- Score interface --------------------

    private static DMatrixRMaj buildSigmaOnlyC(int n, double sigma2) {
        if (n <= 0) throw new IllegalArgumentException("n must be > 0");
        if (!(sigma2 > 0) || !Double.isFinite(sigma2)) {
            throw new IllegalArgumentException("sigma2 must be finite and > 0");
        }

        DMatrixRMaj C = new DMatrixRMaj(n, n);
        for (int i = 0; i < n; i++) {
            C.set(i, i, sigma2);
        }
        return C;
    }

    // same mix64 you used elsewhere (or paste this one)
    private static long mix64(long z) {
        z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
        z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return z ^ (z >>> 33);
    }

    private static final class SigmaEval {
        final double sigma2;
        final double logLik;
        final double dlds;     // derivative w.r.t log(sigma2)
        final double logDetB;
        final double vTBInvV;
        final double trBInv;   // optional if you compute it
        SigmaEval(double sigma2, double logLik, double dlds, double logDetB, double vTBInvV, double trBInv) {
            this.sigma2 = sigma2; this.logLik = logLik; this.dlds = dlds;
            this.logDetB = logDetB; this.vTBInvV = vTBInvV; this.trBInv = trBInv;
        }
    }

    private SigmaEval evalSigma2(
            double sigma2,
            int n,
            int m,
            double yTy,
            DMatrixRMaj G,
            double[] v
    ) {
        sigma2 = TMath.max(1e-10, sigma2);
        if (!Double.isFinite(sigma2)) return null;

        // B = G + sigma2 I
        DMatrixRMaj B = G.copy();
        for (int k = 0; k < m; k++) B.add(k, k, sigma2);

        CholeskyDecomposition_F64<DMatrixRMaj> chol = DecompositionFactory_DDRM.chol(true);
        if (!chol.decompose(B)) return null;
        DMatrixRMaj L = chol.getT(null);

        // logdet(B)
        double ld = 0.0;
        for (int k = 0; k < m; k++) {
            double di = L.get(k, k);
            if (!(di > 0) || !Double.isFinite(di)) return null;
            ld += TMath.log(di);
        }
        double logDetB = 2.0 * ld;

        // t = B^{-1} v via two triangular solves (reuse your code style)
        double[] t = Arrays.copyOf(v, m);
        // forward
        for (int i = 0; i < m; i++) {
            double sum = t[i];
            for (int j = 0; j < i; j++) sum -= L.get(i, j) * t[j];
            t[i] = sum / L.get(i, i);
        }
        // back
        for (int i = m - 1; i >= 0; i--) {
            double sum = t[i];
            for (int j = i + 1; j < m; j++) sum -= L.get(j, i) * t[j];
            t[i] = sum / L.get(i, i);
        }

        double vTBInvV = 0.0;
        for (int k = 0; k < m; k++) vTBInvV += v[k] * t[k];

        // quad + logdetC
        double invSig = 1.0 / sigma2;
        double quad = invSig * yTy - (invSig * invSig) * vTBInvV;
        double logDetC = (n - m) * TMath.log(sigma2) + logDetB;
        double ll = -0.5 * quad - 0.5 * logDetC;

        // ---- derivative dℓ/d log(sigma2) ----
        // compute t^T G t
        double[] Gt = new double[m];
        for (int i = 0; i < m; i++) {
            double s = 0.0;
            for (int j = 0; j < m; j++) s += G.get(i, j) * t[j];
            Gt[i] = s;
        }
        double tTGt = 0.0;
        for (int i = 0; i < m; i++) tTGt += t[i] * Gt[i];

        double a = invSig;
        double b = invSig * invSig;

        // y^T C^{-2} y = a^2 yTy - 2 a b v^T t + b^2 t^T G t
        double yTCm2y = (a * a) * yTy - 2.0 * (a * b) * vTBInvV + (b * b) * tTGt;

        // tr(B^{-1}) exact (m triangular solves). With m=256 and only ~2 evals this is usually fine.
        double trBInv = traceInvFromCholeskyLower(L);

        // tr(C^{-1}) = n/sigma2 - (1/sigma2^2) * (m - sigma2*tr(B^{-1}))
        double trGBInv = m - sigma2 * trBInv;
        double trCInv = (n * invSig) - (invSig * invSig) * trGBInv;

        double dldSigma2 = 0.5 * (yTCm2y - trCInv);
        double dlds = sigma2 * dldSigma2;

        return new SigmaEval(sigma2, ll, dlds, logDetB, vTBInvV, trBInv);
    }

    private static double traceInvFromCholeskyLower(DMatrixRMaj L) {
        // Exact trace(inv(A)) where A = L L^T and L is lower-triangular.
        // Compute columns of inv(A) via solves A x = e_i, sum diag = sum_i e_i^T x.
        int n = L.numRows;
        double tr = 0.0;
        double[] x = new double[n];

        for (int col = 0; col < n; col++) {
            Arrays.fill(x, 0.0);
            x[col] = 1.0;

            // forward solve: L u = e_col (u stored in x)
            for (int i = 0; i < n; i++) {
                double sum = x[i];
                for (int j = 0; j < i; j++) sum -= L.get(i, j) * x[j];
                x[i] = sum / L.get(i, i);
            }

            // diag element of inv(A) at (col,col) is ||u||^2 because inv(A)=L^{-T}L^{-1}
            double ss = 0.0;
            for (int i = 0; i < n; i++) ss += x[i] * x[i];
            tr += ss;
        }

        return tr;
    }

    private double profileSigma2Secant(
            double sigma2Init,
            int n,
            int m,
            double yTy,
            DMatrixRMaj G,
            double[] v
    ) {
        double s0 = TMath.log(TMath.max(1e-10, sigma2Init));
        // a nearby second point for secant
        double s1 = s0 + 0.25;

        SigmaEval e0 = evalSigma2(TMath.exp(s0), n, m, yTy, G, v);
        SigmaEval e1 = evalSigma2(TMath.exp(s1), n, m, yTy, G, v);
        if (e0 == null) return sigma2Init;
        if (e1 == null) return e0.sigma2;

        // 2 secant steps for root of dℓ/ds = 0
        for (int it = 0; it < 2; it++) {
            double f0 = e0.dlds;
            double f1 = e1.dlds;

            double denom = (f1 - f0);
            if (TMath.abs(denom) < 1e-12) break;

            double s2 = s1 - f1 * (s1 - s0) / denom;

            // clamp to sane range so we don’t explode
            s2 = TMath.max(TMath.log(1e-10), TMath.min(TMath.log(1e6), s2));

            SigmaEval e2 = evalSigma2(TMath.exp(s2), n, m, yTy, G, v);
            if (e2 == null) break;

            // shift
            s0 = s1; e0 = e1;
            s1 = s2; e1 = e2;

            if (TMath.abs(e1.dlds) < 1e-3) break;
        }

        return e1.sigma2;
    }

    /**
     * Orthogonal Random Features (ORF) weights for RBF kernel.
     * <p>
     * Produces W with (block-)orthogonal rows:
     * W_row = (wStd * r) * q_row
     * where q_row are orthonormal directions and r ~ chi(d).
     * <p>
     * If mFeatures > d, rows are generated in blocks of size d; orthogonality holds within each block.
     */
    private static double[][] sampleOrthogonalW(int mFeatures, int d, double wStd) {
        double[][] W = new double[mFeatures][d];
        if (d <= 0) return W;

        int filled = 0;

        // Generate in blocks of size d (or remaining rows).
        while (filled < mFeatures) {
            int block = TMath.min(d, mFeatures - filled);

            // Step 1: Gaussian block G (block x d)
            double[][] Q = new double[block][d];
            for (int i = 0; i < block; i++) {
                for (int j = 0; j < d; j++) {
                    Q[i][j] = RandomUtil.getInstance().nextGaussian();
                }
            }

            // Step 2: Orthonormalize rows of Q (Gram-Schmidt on rows)
            for (int i = 0; i < block; i++) {
                // subtract projections on previous rows
                for (int k = 0; k < i; k++) {
                    double dot = 0.0;
                    for (int j = 0; j < d; j++) dot += Q[i][j] * Q[k][j];
                    for (int j = 0; j < d; j++) Q[i][j] -= dot * Q[k][j];
                }
                // normalize
                double norm2 = 0.0;
                for (int j = 0; j < d; j++) norm2 += Q[i][j] * Q[i][j];
                double norm = TMath.sqrt(TMath.max(1e-18, norm2));
                for (int j = 0; j < d; j++) Q[i][j] /= norm;
            }

            // Step 3: scale each row by chi(d) radius (approximate Gaussian row norm)
            for (int i = 0; i < block; i++) {
                double r = chiRadius(d);     // ~ ||N(0,I_d)||

                double s = wStd * r;
                int outRow = filled + i;
                for (int j = 0; j < d; j++) {
                    W[outRow][j] = s * Q[i][j];
                }
            }

            filled += block;
        }

        return W;
    }

    /**
     * Radius r ~ chi(d) via sqrt(sum_k g_k^2), g_k ~ N(0,1).
     */
    private static double chiRadius(int d) {
        double ss = 0.0;
        for (int k = 0; k < d; k++) {
            double g = RandomUtil.getInstance().nextGaussian();
            ss += g * g;
        }
        return TMath.sqrt(TMath.max(1e-18, ss));
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

    // Median of ||zi-zj||^2 using a subsample of rows for speed.
    private static double medianDistanceSquaredND(double[][] Z, int maxRows) {
        int n = Z.length;
        int d = Z[0].length;
        if (n < 3) return 1.0;

        int m = TMath.min(n, maxRows);

        // Take evenly spaced rows (deterministic, no RNG).
        int[] idx = new int[m];
        if (m == n) {
            for (int i = 0; i < m; i++) idx[i] = i;
        } else {
            for (int i = 0; i < m; i++) idx[i] = (int) TMath.floor((i * (long) (n - 1)) / (double) (m - 1));
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

    /**
     * Calculates the difference in local scores between two configurations of variables.
     * Specifically, this method computes the difference between the local score of variable {@code y}
     * conditioned on {@code z} and {@code x}, and the local score of {@code y} conditioned only on {@code z}.
     *
     * @param x the variable being added to the parent set of {@code y}.
     * @param y the target variable whose local score is being computed.
     * @param z an array of integers representing the current set of parent variables of {@code y}.
     * @return the difference in local score resulting from adding {@code x} to the parent set {@code z} of {@code y}.
     */
    @Override
    public double localScoreDiff(int x, int y, int[] z) {
        return localScore(y, append(z, x)) - localScore(y, z);
    }

    /**
     * Computes the local score for a given variable and its parent set in a Bayesian network.
     *
     * @param i       The index of the target variable for which the local score is to be computed.
     * @param parents The indices of the parent variables of the target variable.
     * @return The computed local score as a double value. If the score cannot be computed due to invalid input
     * or other issues, returns Double.NaN.
     */
    @Override
    public double localScore(int i, int... parents) {
        Arrays.sort(parents);
        long key = cacheKey(i, parents);

        final ConcurrentHashMap<Long, Double> cache = localScoreCacheRef.get();

        return cache.computeIfAbsent(key, k -> {
            try {
                int[] all = concat(i, parents);
                int[] rows = calculateRowSubsets ? validRows(all) : null;

                int n = (rows == null) ? nEff : rows.length;
                if (n < 5) return Double.NaN;

                double[] y = extract1D(i, rows, n);
                centerInPlace(y);

                double sigma2 = lambda;
                if (!(sigma2 > 0) || !Double.isFinite(sigma2)) return Double.NaN;

                // Exact closed-form for no parents: C = sigma2 I
                if (parents.length == 0) {
                    return gpLogMarginalLikelihoodSigmaOnly(y, sigma2);
                }

                // Bandwidth (median heuristic on standardized parents)
                double[][] Z = new double[n][parents.length];
                for (int r = 0; r < n; r++) {
                    int row = (rows == null) ? r : rows[r];
                    for (int j = 0; j < parents.length; j++) {
                        Z[r][j] = zCols[parents[j]][row];
                    }
                }

                double bw2 = getChildBandwidth2(i);
                bw2 *= (bandwidthMultiplier * bandwidthMultiplier);

                // Deterministic seed per (target, parent set).
                long seed = key ^ 0x9E3779B97F4A7C15L;

                return gpLogMarginalLikelihoodRFF_profiledNested(
                        y, parents, rows, n, numFeatures,
                        bw2, /*sigma2Init=*/ lambda, seed
                );

            } catch (RuntimeException e) {
                TetradLogger.getInstance().log(e.getMessage());
                return Double.NaN;
            }
        });
    }

    private void resetCache() {
        localScoreCacheRef.set(new ConcurrentHashMap<>());
        phaseCacheRef.set(new ConcurrentHashMap<>());
        omegaCacheRef.set(new ConcurrentHashMap<>());
        // keep childBw2Cache; it's dataset-derived and stable unless you want to reset on knob changes
    }

    // -------------------- public tuning knobs --------------------

    /**
     * Retrieves the list of variables in the current instance.
     *
     * @return a new list containing the variables of type {@code Node}.
     */
    @Override
    public List<Node> getVariables() {
        return new ArrayList<>(variables);
    }

    /**
     * Retrieves the sample size, which corresponds to the number of rows in the associated data set.
     *
     * @return the sample size as an integer.
     */
    @Override
    public int getSampleSize() {
        return dataSet.getNumRows();
    }

    /**
     * Computes the maximum degree based on a logarithmic function
     * that evaluates the current effective sample size.
     * The result is the ceiling of the logarithm of the larger value
     * between 5 and the effective sample size {@code nEff}.
     *
     * @return the maximum degree as an integer, calculated as the ceiling
     * of the logarithm of the larger value between 5 and {@code nEff}.
     */
    @Override
    public int getMaxDegree() {
        return (int) TMath.ceil(TMath.log(TMath.max(5, nEff)));
    }

    /**
     * Determines whether a given node is conditionally independent of a set of nodes
     * based on the local score calculation.
     *
     * @param z a list of nodes representing the conditional set.
     * @param y the node whose conditional independence is being evaluated.
     * @return true if the local score calculation results in NaN or infinity, indicating
     * that the conditional independence cannot be determined reliably; false otherwise.
     */
    @Override
    public boolean determines(List<Node> z, Node y) {
        int i = variables.indexOf(y);
        int[] parents = new int[z.size()];
        for (int t = 0; t < z.size(); t++) parents[t] = variables.indexOf(z.get(t));

        double s = localScore(i, parents);
        return Double.isNaN(s) || Double.isInfinite(s);
    }

    // -------------------- GP marginal likelihood core --------------------

    /**
     * Determines if the given bump value represents an "effect edge."
     *
     * @param bump the bump value to evaluate
     * @return true if the bump value is greater than 0, otherwise false
     */
    @Override
    public boolean isEffectEdge(double bump) {
        return bump > 0;
    }

    /**
     * Retrieves the data model associated with the current instance.
     *
     * @return the data model object of type DataModel
     */
    public DataModel getDataModel() {
        return dataSet;
    }

    /**
     * Returns the effective sample size, which represents the number of observations
     * adjusted for correlations or weighting within the sample data.
     *
     * @return the effective sample size as an integer
     */
    @Override
    public int getEffectiveSampleSize() {
        return nEff;
    }

    /**
     * Sets the effective sample size to be used in calculations.
     * If the provided sample size is negative, it will default to the current sample size.
     *
     * @param nEff the effective sample size to set; defaults to the current sample size if negative
     */
    @Override
    public void setEffectiveSampleSize(int nEff) {
        this.nEff = (nEff < 0) ? this.sampleSize : nEff;
        resetCache();
    }

    /**
     * Returns a string representation of this object.
     * The string provides a descriptive label for this kernel,
     * indicating its type and mathematical form.
     *
     * @return a string describing this kernel, including its name and characteristics
     */
    @Override
    public String toString() {
        return "Huang Kernel Marginal Score (GP form, continuous)";
    }

    /**
     * Sets the value of the lambda parameter in the current instance. Lambda is
     * a positive scalar value that influences specific scoring computations.
     * If the provided value is not greater than zero, an
     * IllegalArgumentException is thrown.
     *
     * @param lambda the new value to be set for the lambda parameter. Must be
     *               greater than zero.
     * @throws IllegalArgumentException if the input lambda is less than or
     *                                  equal to zero.
     */
    public void setLambda(double lambda) {
        if (lambda <= 0) throw new IllegalArgumentException("lambda must be > 0");
        this.lambda = lambda;
        resetCache();
    }

    /**
     * Sets the value of the bandwidth multiplier parameter in the current instance.
     * The bandwidth multiplier is a positive scalar that scales the bandwidth
     * parameter in the kernel function. The value must be greater than zero.
     *
     * @param bandwidthMultiplier the new value to be set for the bandwidth multiplier parameter.
     */
    public void setBandwidthMultiplier(double bandwidthMultiplier) {
        if (!(bandwidthMultiplier > 0) || !Double.isFinite(bandwidthMultiplier)) {
            throw new IllegalArgumentException("bandwidthMultiplier must be > 0");
        }
        this.bandwidthMultiplier = bandwidthMultiplier;
        resetCache();
    }

    /**
     * Sets the maximum number of rows to consider for bandwidth estimation in the current instance.
     * This parameter limits the number of rows used for bandwidth estimation to prevent excessive computation.
     * Default 50.
     *
     * @param bwMaxRows the maximum number of rows to set. Must be a positive integer.
     */
    public void setBwMaxRows(int bwMaxRows) {
        this.bwMaxRows = TMath.max(50, bwMaxRows);
        resetCache();
    }

    /**
     * Sets the number of features for computations in the current instance.
     * This parameter defines the dimensionality of the random feature mappings.
     * Default 256.
     *
     * @param numFeatures the number of features to set. Must be a positive integer.
     */
    public void setNumFeatures(int numFeatures) {
        this.numFeatures = numFeatures;
    }

    /**
     * Retrieves the feature type for the current instance.
     * The feature type determines the type of random feature mapping being utilized.
     *
     * @return an integer representing the feature type:
     * <ul>
     *    <li>1: if the feature type to {@code FeatureType.RFF} (Random Fourier Features).</li>
     *    <li>2: if the feature type to {@code FeatureType.ORF} (Orthogonal Random Features).</li>
     * </ul>
     */
    public FeatureType getFeatureType() {
        return featureType;
    }

    /**
     * Sets the feature type for the current instance based on the provided integer value.
     * The feature type determines which type of random feature mapping is utilized.
     * Valid feature types are defined in the {@code FeatureType} enumeration.
     *
     * @param featureType an integer representing the desired feature type.
     *                    Acceptable values are:
     *                    <ul>
     *                       <li>1: Sets the feature type to {@code FeatureType.RFF} (Random Fourier Features).</li>
     *                       <li>2: Sets the feature type to {@code FeatureType.ORF} (Orthogonal Random Features).</li>
     *                    </ul>
     * @throws IllegalArgumentException if the input {@code featureType} is not 1 or 2.
     */
    public void setFeatureType(FeatureType featureType) {
        if (featureType == null) {
            throw new IllegalArgumentException("featureType cannot be null");
        }
        this.featureType = featureType;
    }

    // -------------------- kernels --------------------

    private double gpLogMarginalLikelihoodRFF(
            double[] yCentered,          // length n
            int[] parentIdx,             // d parents
            int[] rows,                  // null or length n, mapping into original rows
            int n,
            int mFeatures,               // m
            double bw2,                  // your median ||z-z'||^2 * multiplier^2
            double sigma2,
            long seed
    ) {
        final int d = parentIdx.length;
        if (n < 5) return Double.NaN;
        if (!(sigma2 > 0) || !Double.isFinite(sigma2)) return Double.NaN;
        if (!(bw2 > 0) || !Double.isFinite(bw2)) bw2 = 1.0;

        // RFF parameters for k(x,x') = exp(-||x-x'||^2 / bw2)
        // => w ~ N(0, 2/bw2 I)
        final double wStd = TMath.sqrt(2.0 / bw2);
        final double scale = TMath.sqrt(2.0 / mFeatures);

        // Sample W (m x d) and b (m)
        // Store as [m][d] for fast dot(row,d) per feature.
        double[][] W;
        double[] b;

        if (featureType == FeatureType.RFF) {

            W = new double[mFeatures][d];
            b = new double[mFeatures];

            for (int j = 0; j < mFeatures; j++) {
                for (int k = 0; k < d; k++) {
                    W[j][k] = wStd * RandomUtil.getInstance().nextGaussian();
                }
                b[j] = 2.0 * TMath.PI * RandomUtil.getInstance().nextDouble();
            }
        } else if (featureType == FeatureType.ORF) {
            W = sampleOrthogonalW(mFeatures, d, wStd);

            b = new double[mFeatures];
            for (int j = 0; j < mFeatures; j++) {
                b[j] = 2.0 * TMath.PI * RandomUtil.getInstance().nextDouble();
            }
        } else {
            throw new IllegalArgumentException("featureType must be RFF or ORF");
        }

        // Accumulate G = Phi^T Phi (m x m, symmetric) and v = Phi^T y (m)
        DMatrixRMaj G = new DMatrixRMaj(mFeatures, mFeatures);
        double[] v = new double[mFeatures];

        double yTy = 0.0;

        // temp feature vector for one row (length m)
        double[] phi = new double[mFeatures];

        for (int ii = 0; ii < n; ii++) {
            int row = (rows == null) ? ii : rows[ii];

            // Build x (d) on the fly from zCols
            // Compute phi_j = sqrt(2/m) cos(w_j^T x + b_j)
            for (int j = 0; j < mFeatures; j++) {
                double dot = 0.0;
                double[] wj = W[j];
                for (int k = 0; k < d; k++) {
                    dot += wj[k] * zCols[parentIdx[k]][row];
                }
                phi[j] = scale * TMath.cos(dot + b[j]);
            }

            double yi = yCentered[ii];
            yTy += yi * yi;

            // v += phi * yi
            for (int j = 0; j < mFeatures; j++) {
                v[j] += phi[j] * yi;
            }

            // G += phi^T phi  (rank-1 outer product, symmetric fill lower triangle)
            for (int j = 0; j < mFeatures; j++) {
                double pj = phi[j];
                for (int k = 0; k <= j; k++) {
                    G.add(j, k, pj * phi[k]);
                }
            }
        }

        // Mirror lower -> upper
        for (int j = 0; j < mFeatures; j++) {
            for (int k = 0; k < j; k++) {
                G.set(k, j, G.get(j, k));
            }
        }

        // B = G + sigma2 I
        DMatrixRMaj B = G;
        for (int j = 0; j < mFeatures; j++) {
            B.add(j, j, sigma2);
        }

        // Cholesky(B)
        CholeskyDecomposition_F64<DMatrixRMaj> chol = DecompositionFactory_DDRM.chol(true);
        if (!chol.decompose(B)) return Double.NaN;

        DMatrixRMaj L = chol.getT(null); // lower triangular

        // logdet(B) = 2 * sum log diag(L)
        double logDetB = 0.0;
        for (int i = 0; i < mFeatures; i++) {
            double di = L.get(i, i);
            if (!(di > 0) || !Double.isFinite(di)) return Double.NaN;
            logDetB += TMath.log(di);
        }
        logDetB *= 2.0;

        // Solve B^{-1} v via two triangular solves:
        // L u = v, L^T w = u
        double[] u = Arrays.copyOf(v, mFeatures);

        // forward solve
        for (int i = 0; i < mFeatures; i++) {
            double sum = u[i];
            for (int j = 0; j < i; j++) sum -= L.get(i, j) * u[j];
            u[i] = sum / L.get(i, i);
        }
        // back solve
        for (int i = mFeatures - 1; i >= 0; i--) {
            double sum = u[i];
            for (int j = i + 1; j < mFeatures; j++) sum -= L.get(j, i) * u[j];
            u[i] = sum / L.get(i, i);
        }

        // v^T B^{-1} v = v^T w  (w stored in u)
        double vTBInvV = 0.0;
        for (int j = 0; j < mFeatures; j++) vTBInvV += v[j] * u[j];

        // quad = (1/sigma2) yTy - (1/sigma2^2) v^T B^{-1} v
        double invSig = 1.0 / sigma2;
        double quad = invSig * yTy - (invSig * invSig) * vTBInvV;

        // log|C| = (n - m) log sigma2 + log|B|
        double logDetC = (n - mFeatures) * TMath.log(sigma2) + logDetB;

        if (!Double.isFinite(quad) || !Double.isFinite(logDetC)) return Double.NaN;

        return -0.5 * quad - 0.5 * logDetC;
    }

    // -------------------- missingness row selection --------------------

// Drop-in replacement for your gpLogMarginalLikelihoodRFF_profiledNested(...)
// Changes vs your version:
//  (1) keeps the same nested/coupled features and single-pass accumulation of G, v, yTy
//  (2) replaces the "rssLike/n" sigma2 update with 2-step secant root-finding on dℓ/dlog(sigma2)
//      using exact GP marginal-likelihood derivatives computed in feature space.
//
// Requirements:
//  - uses your existing: getPhaseForChild(seedBase,m), getBaseOmegaForParent(parent,m), zCols[]
//  - uses EJML Cholesky (same as your code)
//  - includes a local traceInvFromCholeskyLower(L) helper below (exact trace(B^{-1})).

    private double gpLogMarginalLikelihoodRFF_profiledNested(
            double[] yCentered,          // length n
            int[] parentIdx,             // d parents
            int[] rows,                  // null or length n
            int n,
            int mFeatures,               // m
            double bw2Child,             // stable bw2 for this child (already includes multiplier^2)
            double sigma2Init,
            long seedBase                // derived from cache key
    ) {
        final int d = parentIdx.length;
        if (n < 5) return Double.NaN;
        if (d <= 0) return Double.NaN;

        if (!(bw2Child > 0) || !Double.isFinite(bw2Child)) bw2Child = 1.0;

        // Nested features:
        // phi_k(x) = sqrt(2/m) cos( sum_j (wStd * omega_j[k]) * z_j + phase[k] )
        final double wStd = TMath.sqrt(2.0 / bw2Child);
        final double scale = TMath.sqrt(2.0 / mFeatures);

        final double[] phase = getPhaseForChild(seedBase, mFeatures);

        final double[][] omegaByParent = new double[d][];
        for (int j = 0; j < d; j++) {
            omegaByParent[j] = getBaseOmegaForParent(parentIdx[j], mFeatures); // length mFeatures
        }

        // Accumulate G = Phi^T Phi and v = Phi^T y ONCE (independent of sigma2)
        final DMatrixRMaj G = new DMatrixRMaj(mFeatures, mFeatures);
        final double[] v = new double[mFeatures];

        double yTy = 0.0;
        final double[] phi = new double[mFeatures];

        for (int ii = 0; ii < n; ii++) {
            final int row = (rows == null) ? ii : rows[ii];

            for (int k = 0; k < mFeatures; k++) {
                double dot = 0.0;
                for (int j = 0; j < d; j++) {
                    dot += (wStd * omegaByParent[j][k]) * zCols[parentIdx[j]][row];
                }
                phi[k] = scale * TMath.cos(dot + phase[k]);
            }

            final double yi = yCentered[ii];
            yTy += yi * yi;

            for (int k = 0; k < mFeatures; k++) {
                v[k] += phi[k] * yi;
            }

            // lower triangle
            for (int a = 0; a < mFeatures; a++) {
                final double pa = phi[a];
                for (int b = 0; b <= a; b++) {
                    G.add(a, b, pa * phi[b]);
                }
            }
        }

        // Symmetrize G
        for (int a = 0; a < mFeatures; a++) {
            for (int b = 0; b < a; b++) {
                G.set(b, a, G.get(a, b));
            }
        }

        // ---- Profile sigma2 by 2-step secant on dℓ/dlog(sigma2) ----
        double sigma2Hat = profileSigma2Secant(sigma2Init, n, mFeatures, yTy, G, v);
        if (!(sigma2Hat > 0) || !Double.isFinite(sigma2Hat)) return Double.NaN;

        SigmaEval eval = evalSigma2(sigma2Hat, n, mFeatures, yTy, G, v);
        if (eval == null) return Double.NaN;

        return eval.logLik;
    }

    // -------------------- extraction + preprocessing --------------------

    private double[] getPhaseForChild(long seedBase, int mFeatures) {
        // Key only depends on child (encoded in seedBase) + mFeatures.
        // If you want it strictly per-child, incorporate child index explicitly instead.
        final long key = mix64(seedBase ^ 0xD1B54A32D192ED03L) ^ ((long) mFeatures << 1);

        final ConcurrentHashMap<Long, double[]> cache = phaseCacheRef.get();
        return cache.computeIfAbsent(key, k -> {
            double[] b = new double[mFeatures];
            for (int j = 0; j < mFeatures; j++) b[j] = 2.0 * TMath.PI * RandomUtil.getInstance().nextDouble();
            return b;
        });
    }

    private double[] getBaseOmegaForParent(int parentVar, int mFeatures) {
        final long key = (((long) parentVar) << 32) ^ (mFeatures & 0xffffffffL) ^ 0x9E3779B97F4A7C15L;
        final ConcurrentHashMap<Long, double[]> cache = omegaCacheRef.get();

        return cache.computeIfAbsent(key, k -> {
            double[] g = new double[mFeatures];
            for (int j = 0; j < mFeatures; j++) g[j] = RandomUtil.getInstance().nextGaussian(); // N(0,1)
            return g;
        });
    }

    private double getChildBandwidth2(int child) {
        double bw2 = childBw2Cache[child];
        if (bw2 > 0 && Double.isFinite(bw2)) return bw2;

        // Build a design matrix using ALL other variables as candidate inputs, on up to bwMaxRows rows.
        // This is computed once per child and reused across parent sets => stable nesting.
        int p = variables.size();
        int d = p - 1;
        if (d <= 0) {
            childBw2Cache[child] = 1.0;
            return 1.0;
        }

        // Use first m rows (or all if smaller) for bandwidth estimation; deterministic.
        int m = TMath.min(nEff, bwMaxRows);
        m = TMath.max(5, m);

        double[][] Z = new double[m][d];

        int col = 0;
        for (int v = 0; v < p; v++) {
            if (v == child) continue;
            for (int r = 0; r < m; r++) {
                Z[r][col] = zCols[v][r]; // assumes no missing; if missing-heavy, you can add a local validRows subset here
            }
            col++;
        }

        bw2 = medianDistanceSquaredND(Z, m);
        if (!(bw2 > 0) || !Double.isFinite(bw2)) bw2 = 1.0;

        childBw2Cache[child] = bw2;
        return bw2;
    }

    /**
     * RBF kernel Gram matrix on standardized parents:
     * K_ij = exp(-||z_i - z_j||^2 / bw2)
     * where bw2 is median ||zi-zj||^2 times multiplier.
     */
    private DMatrixRMaj rbfGramND(int[] parentIdx, int[] rows, int n) {
        int d = parentIdx.length;

        // Extract Z (n x d)
        double[][] Z = new double[n][d];
        for (int r = 0; r < n; r++) {
            int row = (rows == null) ? r : rows[r];
            for (int j = 0; j < d; j++) Z[r][j] = zCols[parentIdx[j]][row];
        }

        double bw2 = medianDistanceSquaredND(Z, TMath.min(n, bwMaxRows));
        if (!(bw2 > 0) || !Double.isFinite(bw2)) bw2 = 1.0;
        bw2 *= (bandwidthMultiplier * bandwidthMultiplier);

        double invBw = 1.0 / bw2;

        DMatrixRMaj K = new DMatrixRMaj(n, n);

        // fill diagonal (RBF has k(x,x)=1)
        for (int i = 0; i < n; i++) K.set(i, i, 1.0);

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < i; j++) {
                double dist2 = 0.0;
                for (int k = 0; k < d; k++) {
                    double diff = Z[i][k] - Z[j][k];
                    dist2 += diff * diff;
                }
                double v = TMath.exp(-dist2 * invBw);
                K.set(i, j, v);
                K.set(j, i, v);
            }
        }
        return K;
    }

    private int[] validRows(int[] vars) {
        int n = sampleSize;
        int[] tmp = new int[n];
        int m = 0;

        outer:
        for (int r = 0; r < n; r++) {
            for (int v : vars) {
                double val = zCols[v][r]; // zCols preserves NaN
                if (Double.isNaN(val)) continue outer;
            }
            tmp[m++] = r;
        }
        return Arrays.copyOf(tmp, m);
    }

    // -------------------- small utilities --------------------

    private double[] extract1D(int varIndex, int[] rows, int n) {
        double[] x = new double[n];
        if (rows == null) {
            for (int r = 0; r < n; r++) x[r] = zCols[varIndex][r];
        } else {
            for (int r = 0; r < n; r++) x[r] = zCols[varIndex][rows[r]];
        }
        return x;
    }

    public int[] append(int[] z, int x) {
        int[] out = Arrays.copyOf(z, z.length + 1);
        out[z.length] = x;
        return out;
    }

    /**
     * Represents the types of features that can be used in random feature mappings.
     * This enumeration is utilized to distinguish between different methods for
     * generating random features in machine learning or statistical models.
     */
    public enum FeatureType {

        /**
         * Denotes the type of Random Fourier Features (RFF) used in random feature
         * mappings for machine learning or statistical models.
         * RFF is a method to approximate kernel functions through random projections,
         * enabling scalable computation for high-dimensional data.
         * It is primarily used to facilitate efficient data transformations in non-linear models.
         */
        RFF,

        /**
         * Denotes the type of Orthogonal Random Features (ORF) used in random feature
         * mappings for machine learning or statistical models.
         * ORF is a variation of Random Fourier Features that ensures orthogonality
         * in the generated random projections, improving numerical stability,
         * reducing redundancy, and enhancing the quality of feature representations
         * in high-dimensional data transformations.
         */
        ORF
    }
}