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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * <p><b>FF-ML-Legendre: Feature-Function Marginal Likelihood using Legendre polynomial features</b></p>
 *
 * <p>
 * This is a "deterministic FFML" variant: replace random Fourier features (RFF/ORF) with a deterministic
 * Legendre-basis expansion of continuous parents (after mapping z-scored values to (-1,1)).
 * </p>
 *
 * <p><b>Kernel model (mixed parents)</b></p>
 * <ul>
 *   <li><b>Continuous part</b>: additive Legendre feature map over parents, then
 *       {@code Kcont ≈ Phi Phi^T} where {@code Phi} is the (n×m) feature matrix.</li>
 *   <li><b>Discrete part</b>: simple categorical kernel per discrete parent:
 *       {@code k_cat(c,c)=1} and {@code k_cat(c,c')=rho} for {@code c!=c'}; combined across
 *       multiple discrete parents by multiplication (Hadamard in Gram matrix).</li>
 *   <li><b>Mixed kernel</b>: {@code K = Kcont ⊙ Kcat} (if no continuous parents, {@code Kcont} is all-ones;
 *       if no discrete parents, {@code Kcat} is all-ones).</li>
 * </ul>
 *
 * <p><b>Score objective</b></p>
 * <p>
 * For continuous targets, localScore is (up to additive constants):
 * {@code -0.5 * (y^T C^{-1} y + log|C|)} where {@code C = K + sigma^2 I}.
 * </p>
 * <p>
 * For discrete targets, we one-hot encode the child (observed levels in active rows), center each column,
 * score each column with the same GP expression, and sum across columns (Gaussian surrogate).
 * </p>
 *
 * <p><b>Complexity control / "discounting" basis functions</b></p>
 * <p>
 * Degree-weighted shrinkage is implemented by scaling {@code P_k(x)} by {@code 1/k^alpha}.
 * Larger alpha downweights higher degrees globally.
 * </p>
 */
public final class FfMlLegendre implements Score, EffectiveSampleSizeSettable {

    // -------------------- configuration knobs --------------------

    /** Base noise/ridge; used as sigma^2. Must be > 0. */
    private volatile double lambda = 1.0;

    /** Legendre degree per continuous parent (uses P1..Pt; does not include P0). */
    private volatile int legendreDegree = 5;

    /**
     * Degree discount exponent alpha >= 0. Feature for degree k is multiplied by 1 / k^alpha.
     * alpha=0 => no discount; alpha=1 or 2 => stronger shrinkage of higher degrees.
     */
    private volatile double legendreAlpha = 1.0;

    /** Categorical kernel off-diagonal similarity rho in [0,1). */
    private volatile double catRho = 0.5;

    /** Effective sample size. */
    private volatile int nEff;

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

    /** If true, use valid row subsets when missing exists. */
    private final boolean calculateRowSubsets;

    /** Cache: (target i, sorted parents) -> score. */
    private final AtomicReference<ConcurrentHashMap<Long, Double>> localScoreCacheRef =
            new AtomicReference<>(new ConcurrentHashMap<>());

    public FfMlLegendre(DataSet dataSet) {
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
                for (int r = 0; r < sampleSize; r++) dCols[j][r] = readDiscreteValue(dataSet, r, j);
                Arrays.fill(raw[j], Double.NaN);
            } else {
                dCols[j] = null;
                for (int r = 0; r < sampleSize; r++) raw[j][r] = dataSet.getDouble(r, j);
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

        resetCache();
    }

    // -------------------- knobs --------------------

    public void setLambda(double lambda) {
        if (!(lambda > 0) || !Double.isFinite(lambda)) throw new IllegalArgumentException("lambda must be > 0");
        this.lambda = lambda;
        resetCache();
    }

    public double getLambda() {
        return lambda;
    }

    public void setLegendreDegree(int t) {
        if (t < 1) throw new IllegalArgumentException("legendreDegree must be >= 1");
        this.legendreDegree = t;
        resetCache();
    }

    public int getLegendreDegree() {
        return legendreDegree;
    }

    public void setLegendreAlpha(double alpha) {
        if (!(alpha >= 0.0) || !Double.isFinite(alpha)) {
            throw new IllegalArgumentException("legendreAlpha must be >= 0");
        }
        this.legendreAlpha = alpha;
        resetCache();
    }

    public double getLegendreAlpha() {
        return legendreAlpha;
    }

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

                // Split parents
                int[] contParents = new int[parents.length];
                int[] discParents = new int[parents.length];
                int nc = 0, nd = 0;
                for (int pIdx : parents) {
                    if (isDiscrete[pIdx]) discParents[nd++] = pIdx;
                    else contParents[nc++] = pIdx;
                }
                contParents = Arrays.copyOf(contParents, nc);
                discParents = Arrays.copyOf(discParents, nd);

                // Continuous target
                if (!isDiscrete[i]) {
                    double[] y = extract1DContinuous(i, rows, n);
                    centerInPlace(y);

                    // no parents => sigma-only
                    if (parents.length == 0) {
                        return gpLogMarginalLikelihoodSigmaOnly(y, sigma2);
                    }

                    return gpLogML_mixedKernelNxN_Legendre(
                            y, contParents, discParents, rows, n, sigma2
                    );
                }

                // Discrete target: one-hot centered, sum GP-ML per column
                int[] yDisc = extractDiscrete(i, rows, n);
                double[][] Y = oneHotCentered(yDisc); // n x L centered
                if (Y == null || Y.length == 0 || Y[0].length == 0) return Double.NaN;

                // no parents => sigma-only per column
                if (parents.length == 0) {
                    return gpLogMarginalLikelihoodSigmaOnlyMulti(Y, sigma2);
                }

                double total = 0.0;
                for (int j = 0; j < Y[0].length; j++) {
                    double[] yj = new double[n];
                    for (int r = 0; r < n; r++) yj[r] = Y[r][j];
                    // already centered
                    double ll = gpLogML_mixedKernelNxN_Legendre(
                            yj, contParents, discParents, rows, n, sigma2
                    );
                    if (!Double.isFinite(ll)) return Double.NaN;
                    total += ll;
                }
                return total;

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
    public String toString() {
        return "FFML-Legendre (Legendre features + categorical product-kernel)";
    }

    // -------------------- EffectiveSampleSizeSettable --------------------

    @Override
    public int getEffectiveSampleSize() {
        return nEff;
    }

    @Override
    public void setEffectiveSampleSize(int nEff) {
        this.nEff = (nEff < 0) ? this.sampleSize : nEff;
        resetCache();
    }

    // -------------------- core GP-ML with mixed kernel (n×n) --------------------

    private double gpLogML_mixedKernelNxN_Legendre(
            double[] yCentered,     // length n, centered
            int[] contParents,
            int[] discParents,
            int[] rows,             // null or length n mapping to original rows
            int n,
            double sigma2
    ) {
        if (n < 5) return Double.NaN;
        if (!(sigma2 > 0) || !Double.isFinite(sigma2)) return Double.NaN;

        // 1) Build K = Kcont first.
        DMatrixRMaj K = new DMatrixRMaj(n, n);

        final int dc = contParents.length;

        if (dc == 0) {
            // No continuous parents => k_cont = 1, so Kcont = all-ones.
            for (int i = 0; i < n; i++) {
                for (int j = 0; j <= i; j++) K.set(i, j, 1.0);
            }
            mirrorLowerToUpper(K);
        } else {
            // Build Phi (n×m), where m = dc * degree, and center each feature column.
            final int deg = legendreDegree;
            final int m = dc * deg;

            double[][] Phi = new double[n][m];

            // Fill raw Phi
            for (int ii = 0; ii < n; ii++) {
                int row = (rows == null) ? ii : rows[ii];
                int pos = 0;
                for (int k = 0; k < dc; k++) {
                    double z = zCols[contParents[k]][row];
                    double x = toMinusOneOne(z);
                    legendreP1toT_weighted(x, deg, legendreAlpha, Phi[ii], pos);
                    pos += deg;
                }
            }

            // Center columns of Phi (recommended since we omit P0)
            centerColumnsInPlace(Phi);

            // Scale to keep K in a reasonable range as m changes
            final double scale = 1.0 / Math.sqrt(Math.max(1, m));
            for (int ii = 0; ii < n; ii++) {
                double[] rowPhi = Phi[ii];
                for (int j = 0; j < m; j++) rowPhi[j] *= scale;
            }

            // Kcont = Phi Phi^T
            for (int i = 0; i < n; i++) {
                double[] phiI = Phi[i];
                for (int j = 0; j <= i; j++) {
                    double[] phiJ = Phi[j];
                    double dot = 0.0;
                    for (int t = 0; t < m; t++) dot += phiI[t] * phiJ[t];
                    K.set(i, j, dot);
                }
            }
            mirrorLowerToUpper(K);
        }

        // 2) Multiply by Kcat (Hadamard) across discrete parents
        if (discParents.length > 0) {
            final double rho = catRho;
            for (int dp : discParents) {
                int[] codes = extractDiscrete(dp, rows, n);
                for (int i = 0; i < n; i++) {
                    int ci = codes[i];
                    for (int j = 0; j <= i; j++) {
                        int cj = codes[j];
                        double mult = (ci == cj) ? 1.0 : rho;
                        K.set(i, j, K.get(i, j) * mult);
                    }
                }
                mirrorLowerToUpper(K);
            }
        }

        // 3) C = K + sigma2 I
        for (int i = 0; i < n; i++) K.add(i, i, sigma2);

        // 4) Cholesky(C)
        CholeskyDecomposition_F64<DMatrixRMaj> chol = DecompositionFactory_DDRM.chol(true);
        if (!chol.decompose(K)) return Double.NaN;
        DMatrixRMaj L = chol.getT(null);

        // logdet(C) = 2*sum(log(diag(L)))
        double logDetC = 0.0;
        for (int i = 0; i < n; i++) {
            double di = L.get(i, i);
            if (!(di > 0) || !Double.isFinite(di)) return Double.NaN;
            logDetC += Math.log(di);
        }
        logDetC *= 2.0;

        // Solve C^{-1} y via forward/backward substitution
        double[] alpha = Arrays.copyOf(yCentered, n);

        // forward: L u = y
        for (int i = 0; i < n; i++) {
            double sum = alpha[i];
            for (int j = 0; j < i; j++) sum -= L.get(i, j) * alpha[j];
            alpha[i] = sum / L.get(i, i);
        }
        // back: L^T alpha = u
        for (int i = n - 1; i >= 0; i--) {
            double sum = alpha[i];
            for (int j = i + 1; j < n; j++) sum -= L.get(j, i) * alpha[j];
            alpha[i] = sum / L.get(i, i);
        }

        // y^T C^{-1} y
        double quad = 0.0;
        for (int i = 0; i < n; i++) quad += yCentered[i] * alpha[i];

        if (!Double.isFinite(quad) || !Double.isFinite(logDetC)) return Double.NaN;

        // Match FFML convention: omit additive constants
        return -0.5 * quad - 0.5 * logDetC;
    }

    private static void mirrorLowerToUpper(DMatrixRMaj M) {
        int n = M.numRows;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < i; j++) {
                M.set(j, i, M.get(i, j));
            }
        }
    }

    // -------------------- utilities: Legendre --------------------

    private static double toMinusOneOne(double z) {
        if (!Double.isFinite(z)) return 0.0;
        double x = Math.tanh(z / 1.5);
        final double eps = 1e-12;
        if (x <= -1 + eps) x = -1 + eps;
        if (x >=  1 - eps) x =  1 - eps;
        return x;
    }

    /**
     * Writes weighted Legendre features P1..Pt into out[offset..offset+t-1].
     * Weight: Pk scaled by 1/k^alpha (alpha>=0).
     */
    private static void legendreP1toT_weighted(double x, int t, double alpha, double[] out, int offset) {
        if (t <= 0) return;

        // P0=1, P1=x
        double Pkm1 = 1.0;
        double Pk = x;

        // k=1
        out[offset] = scaleDegree(1, alpha) * Pk;

        for (int k = 1; k < t; k++) {
            // compute P_{k+1}
            double kD = (double) k;
            double Pkp1 = ((2.0 * kD + 1.0) * x * Pk - kD * Pkm1) / (kD + 1.0);
            int deg = k + 1;
            out[offset + k] = scaleDegree(deg, alpha) * Pkp1;
            Pkm1 = Pk;
            Pk = Pkp1;
        }
    }

    private static double scaleDegree(int k, double alpha) {
        if (alpha == 0.0) return 1.0;
        // guard against NaN/Inf
        double w = 1.0 / Math.pow((double) k, alpha);
        return Double.isFinite(w) ? w : 1.0;
    }

    private static void centerColumnsInPlace(double[][] X) {
        int n = X.length;
        if (n == 0) return;
        int m = X[0].length;
        if (m == 0) return;

        double[] mean = new double[m];
        for (int i = 0; i < n; i++) {
            double[] row = X[i];
            for (int j = 0; j < m; j++) mean[j] += row[j];
        }
        for (int j = 0; j < m; j++) mean[j] /= n;

        for (int i = 0; i < n; i++) {
            double[] row = X[i];
            for (int j = 0; j < m; j++) row[j] -= mean[j];
        }
    }

    // -------------------- utilities: discrete target one-hot --------------------

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

        // Center columns
        for (int j = 0; j < L; j++) {
            double sum = 0.0;
            for (int r = 0; r < n; r++) sum += Y[r][j];
            double mean = sum / n;
            for (int r = 0; r < n; r++) Y[r][j] -= mean;
        }

        return Y;
    }

    // -------------------- utilities: sigma-only likelihood --------------------

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

    private static void centerInPlace(double[] y) {
        double m = 0.0;
        for (double v : y) m += v;
        m /= y.length;
        for (int i = 0; i < y.length; i++) y[i] -= m;
    }

    // -------------------- utilities: missingness row selection (mixed) --------------------

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

    // -------------------- utilities: extraction + preprocessing --------------------

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

    // -------------------- utilities: cache keys --------------------

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

    // -------------------- utilities: discrete reading --------------------

    private static boolean isMissingDiscrete(int v) {
        return v == DiscreteVariable.MISSING_VALUE || v == Integer.MIN_VALUE;
    }

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

    public int[] append(int[] z, int x) {
        int[] out = Arrays.copyOf(z, z.length + 1);
        out[z.length] = x;
        return out;
    }
}