package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.RawMarginalIndependenceTest;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.math3.distribution.GammaDistribution;
import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.CommonOps_DDRM;
import org.ejml.dense.row.factory.LinearSolverFactory_DDRM;
import org.ejml.interfaces.linsol.LinearSolverDense;
import org.ejml.simple.SimpleMatrix;

import java.util.*;

/**
 * Fast KCI (Kernel-based Conditional Independence) scaffold tuned for EJML 0.44.0.
 * <p>
 * Key optimizations: - O(n^2) centering (no H K H multiplies) - Fast Gaussian kernel via one X·Xᵀ GEMM + vectorized exp
 * - Cache of RZ = eps * (KZ + eps I)^{-1} per (Z, rows, eps) key
 * <p>
 * Null:  X ⟂ Y | Z Test:  S = (1/n) * tr(RZ*K_[X,Z]*RZ * RZ*K_Y*RZ) with Gamma tail approx or permutation fallback.
 * <p>
 * Notes: - This is a focused class; integrate/rename fields/methods as needed for your codebase. - For large n,
 * consider Nyström downsampling before forming kernels.
 */
public class Kci implements IndependenceTest, RawMarginalIndependenceTest {
    /**
     * Data matrix in "variables x samples" layout.
     */
    private final SimpleMatrix dataVxN;
    /**
     * Optional bandwidth hints (not required).
     */
    private final SimpleMatrix hHint;
    /**
     * LRU-ish cache for RZ matrices keyed by (Z, rows, eps).
     */
    private final Map<String, DMatrixRMaj> rzCache =
            new LinkedHashMap<>(128, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, DMatrixRMaj> e) {
                    return size() > 64;
                }
            };
    /**
     * Optional small cache for Ky per Y (helps inside PC/FCI loops).
     */
    private final Map<Integer, SimpleMatrix> kyCache =
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Integer, SimpleMatrix> e) {
                    return size() > 64;
                }
            };
    /**
     * RNG for permutations; can be null (seeded later).
     */
    public Random rng = new Random(0);
    /**
     * Optional: last computed p-value.
     */
    public double lastPValue = Double.NaN;
    private int polyDegree = 2;
    private double polyCoef0 = 1.0;
    private double polyGamma = 1.0;   // set yourself (e.g., 1.0/d) if you want automatic scaling
    private KernelType kernelType = KernelType.GAUSSIAN;
    private double epsilon = 1e-3;
    // ---------------------- configuration hooks ----------------------
    private double scalingFactor = 1.0;
    private boolean approximate = true;
    private int numPermutations = 1000;
    /**
     * Represents the dataset used for analysis within the Kci class. It contains the data matrix and associated
     * information required to perform conditional independence tests, build kernel matrices, and compute statistical
     * measures. The `dataSet` field is a core data structure that drives the computations and algorithms implemented in
     * the Kci class.
     */
    private DataSet dataSet;
    /**
     * A list of Node objects representing variables in the context of the Kci class. These variables are used in
     * various kernel computation tasks, independence testing, and other statistical analysis procedures within the KCI
     * framework.
     */
    private List<Node> variables;
    /**
     * Indicates whether verbose output is enabled for the Kci class. When set to true, additional debugging or
     * informational messages may be logged or displayed to provide more detailed insights into the computations and
     * processes within the class.
     */
    private boolean verbose = false;

    // ---------------------- data / indices ----------------------
    private double alpha = 0.01;
    /**
     * Map variable Node -> column index in dataVxN (row in matrix terms).
     */
    private Map<Node, Integer> varToRow;
    /**
     * Active row indices (samples) used in this test run.
     */
    private List<Integer> rows;

    /**
     * Constructs a Kci instance with the given DataSet.
     *
     * @param dataSet the dataset containing the data to be analyzed. It is used to initialize the data matrix, variable
     *                list, and other attributes.
     */
    public Kci(DataSet dataSet) {
        this.dataSet = dataSet;

        this.varToRow = new HashMap<>();
        this.rows = new ArrayList<>();
        this.dataVxN = dataSet.getDoubleData().getData().transpose();
        this.variables = dataSet.getVariables();
        this.varToRow = new HashMap<>();
        for (int i = 0; i < variables.size(); i++) {
            varToRow.put(variables.get(i), i);
        }
        this.hHint = null;
        this.rows = new ArrayList<>();
        for (int i = 0; i < dataSet.getNumRows(); i++) {
            rows.add(i);
        }
    }

    /**
     * @param dataVxN  variables x samples matrix (each row = variable, each column = sample)
     * @param varToRow mapping from Node to its row index in dataVxN
     * @param hHint    optional bandwidth hint matrix; may be null (median heuristic used otherwise)
     * @param rows     sample indices to use (0..N-1)
     */
    public Kci(SimpleMatrix dataVxN,
               Map<Node, Integer> varToRow,
               SimpleMatrix hHint,
               List<Integer> rows) {
        this.dataVxN = dataVxN;
        this.varToRow = varToRow;
        this.hHint = hHint;
        this.rows = rows;
    }

    /**
     * O(n^2) centering: Kc = K - rowMean - colMean + grandMean.
     */
    private static SimpleMatrix centerKernel(SimpleMatrix K) {
        DMatrixRMaj A = K.getDDRM();
        int n = A.getNumRows();
        double[] a = A.data;

        double[] rowSum = new double[n];
        double[] colSum = new double[n];
        double grand = 0.0;

        int idx = 0;
        for (int i = 0; i < n; i++) {
            double rs = 0.0;
            for (int j = 0; j < n; j++, idx++) {
                double v = a[idx];
                rs += v;
                colSum[j] += v;
                grand += v;
            }
            rowSum[i] = rs;
        }

        double invN = 1.0 / n;
        double invN2 = invN * invN;

        DMatrixRMaj C = new DMatrixRMaj(n, n);
        double[] c = C.data;
        idx = 0;
        for (int i = 0; i < n; i++) {
            double ri = rowSum[i] * invN;
            for (int j = 0; j < n; j++, idx++) {
                double cj = colSum[j] * invN;
                c[idx] = a[idx] - ri - cj + grand * invN2;
            }
        }
        return SimpleMatrix.wrap(C);
    }

    // ---------------------- ctor ----------------------

    /**
     * Simple numeric symmetrization: (A + Aᵀ)/2.
     */
    private static SimpleMatrix symmetrize(SimpleMatrix A) {
        return A.plus(A.transpose()).scale(0.5);
    }

    /**
     * Cache key for RZ using sorted Z variable rows + n + eps.
     */
    private static String keyForZ(List<Node> z,
                                  List<Integer> rows,
                                  Map<Node, Integer> varToRow,
                                  double eps) {
        int[] cols = new int[z.size()];
        for (int i = 0; i < z.size(); i++) cols[i] = varToRow.get(z.get(i));
        Arrays.sort(cols);
        StringBuilder sb = new StringBuilder(64);
        sb.append("eps=").append(eps).append("|c=");
        for (int c : cols) sb.append(c).append(',');
        sb.append("|n=").append(rows.size());
        return sb.toString();
    }

    private static int[] uniformSample(int n, int m, Random rng) {
        int[] idx = new int[n];
        for (int i = 0; i < n; i++) idx[i] = i;
        // Partial Fisher–Yates
        for (int i = 0; i < m; i++) {
            int j = i + rng.nextInt(n - i);
            int t = idx[i];
            idx[i] = idx[j];
            idx[j] = t;
        }
        return Arrays.copyOf(idx, m);
    }

    /**
     * Gamma-approx p-value for conditional KCI statistic. S = (1/n) * tr(RX * RY) ~ Gamma(k, theta) by moment
     * matching.
     */
    private static double pValueGammaConditional(SimpleMatrix RX,
                                                 SimpleMatrix RY,
                                                 double stat,
                                                 int n) {
        if (stat <= 0.0 || n <= 1) return 1.0;

        final int N = n;
        final double[] rx = RX.getDDRM().data;
        final double[] ry = RY.getDDRM().data;

        // --- 1) Estimate null mean and variance via a small number of permutations
        final int Bmom = 200;               // 128–512 is a good range
        final Random rng = new Random(7);   // fixed seed for stability in tests

        double mean = 0.0, m2 = 0.0;
        int[] idx = new int[N];
        for (int i = 0; i < N; i++) idx[i] = i;

        for (int b = 0; b < Bmom; b++) {
            // Fisher–Yates shuffle of idx
            for (int i = N - 1; i > 0; i--) {
                int j = rng.nextInt(i + 1);
                int t = idx[i];
                idx[i] = idx[j];
                idx[j] = t;
            }
            // s_b = (1/N) * sum_{i,j} RX[i,j] * RY[idx[i], idx[j]]
            double sb = 0.0;
            int base_i = 0;
            for (int i = 0; i < N; i++, base_i += N) {
                final int ii = idx[i] * N;
                for (int j = 0; j < N; j++) {
                    sb += rx[base_i + j] * ry[ii + idx[j]];
                }
            }
            sb /= N;

            // Welford update for mean/variance
            double delta = sb - mean;
            mean += delta / (b + 1);
            m2 += delta * (sb - mean);
        }
        double var = (Bmom > 1) ? m2 / (Bmom - 1) : 1e-12;

        // --- 2) Moment-matched Gamma(k, theta) with guards
        final double EPS = 1e-12;
        if (!(mean > 0.0) || !Double.isFinite(mean)) mean = EPS;
        if (!(var > 0.0) || !Double.isFinite(var)) var = EPS * mean * mean;

        double k = (mean * mean) / var;   // shape
        double theta = var / mean;        // scale
        if (!Double.isFinite(k) || k <= 0.0) k = 1e-6;
        if (!Double.isFinite(theta) || theta <= 0.0) theta = EPS;

        // --- 3) Right-tail p-value under fitted Gamma
        GammaDistribution gd = new GammaDistribution(k, theta);
        double p = 1.0 - gd.cumulativeProbability(stat);
        if (p < 0.0) p = 0.0;
        if (p > 1.0) p = 1.0;
        return p;
    }

    /**
     * Permutation p-value for conditional KCI. Permute Y (equivalently, conjugate RY by P) and recompute S_perm = (1/n)
     * tr(RX * P RY Pᵀ).
     */
    private static double permutationPValueConditional(SimpleMatrix RX,
                                                       SimpleMatrix RY,
                                                       double stat,
                                                       int n,
                                                       int numPermutations,
                                                       Random rng) {
        if (n <= 1 || numPermutations <= 0) return 1.0;
        if (rng == null) rng = new Random(0);

        final int N = n;
        final double[] rx = RX.getDDRM().data;
        final double[] ry = RY.getDDRM().data;

        int[] idx = new int[N];
        for (int i = 0; i < N; i++) idx[i] = i;

        int geCount = 0;

        for (int b = 0; b < numPermutations; b++) {
            // Shuffle idx
            for (int i = N - 1; i > 0; i--) {
                int j = rng.nextInt(i + 1);
                int t = idx[i];
                idx[i] = idx[j];
                idx[j] = t;
            }

            // stat_perm = (1/N) * sum_{i,j} RX[i,j] * RY[idx[i], idx[j]]
            double s = 0.0;
            int base_i = 0;
            for (int i = 0; i < N; i++, base_i += N) {
                final int ii = idx[i] * N;
                for (int j = 0; j < N; j++) {
                    s += rx[base_i + j] * ry[ii + idx[j]];
                }
            }
            s /= N;

            if (s >= stat) geCount++;
        }

        return (geCount + 1.0) / (numPermutations + 1.0); // +1 smoothing
    }

    private static DataSet twoColumnDataSet(String nameX, double[] x,
                                            String nameY, double[] y) {
        int n = x.length;
        double[][] m = new double[n][2];
        for (int i = 0; i < n; i++) {
            m[i][0] = x[i];
            m[i][1] = y[i];
        }
        List<Node> vars = new ArrayList<>(2);
        vars.add(new ContinuousVariable(nameX));
        vars.add(new ContinuousVariable(nameY));

        DoubleDataBox dataBox = new DoubleDataBox(m);

        return new BoxDataSet(dataBox, vars);
    }

    // Gaussian (RBF) kernel for a single vector
    private static SimpleMatrix gaussianKernel1D(double[] v, double sigma) {
        int n = v.length;
        DMatrixRMaj K = new DMatrixRMaj(n, n);
        double[] kd = K.data;
        double inv2s2 = 1.0 / Math.max(2.0 * sigma * sigma, 1e-24);
        int p = 0;
        for (int i = 0; i < n; i++) {
            double vi = v[i];
            for (int j = 0; j < n; j++, p++) {
                double d = vi - v[j];
                kd[p] = Math.exp(-(d * d) * inv2s2);
            }
        }
        return SimpleMatrix.wrap(K);
    }

    // ---------------------- public API ----------------------

    // Linear kernel for a single vector: K = v v^T
    private static SimpleMatrix linearKernel1D(double[] v) {
        int n = v.length;
        DMatrixRMaj K = new DMatrixRMaj(n, n);
        double[] kd = K.data;
        int p = 0;
        for (int i = 0; i < n; i++) {
            double vi = v[i];
            for (int j = 0; j < n; j++, p++) {
                kd[p] = vi * v[j];
            }
        }
        return SimpleMatrix.wrap(K);
    }

    // ---------------------- kernels & helpers ----------------------

    // Polynomial kernel for a single vector: K = (gamma * (v v^T) + coef0)^degree
    private static SimpleMatrix polynomialKernel1D(double[] v, double gamma, double coef0, int degree) {
        int n = v.length;
        DMatrixRMaj K = new DMatrixRMaj(n, n);
        double[] kd = K.data;
        int p = 0;
        if (degree == 1) {
            for (int i = 0; i < n; i++) {
                double vi = v[i];
                for (int j = 0; j < n; j++, p++) {
                    kd[p] = gamma * (vi * v[j]) + coef0;
                }
            }
        } else if (degree == 2) {
            for (int i = 0; i < n; i++) {
                double vi = v[i];
                for (int j = 0; j < n; j++, p++) {
                    double base = gamma * (vi * v[j]) + coef0;
                    kd[p] = base * base;
                }
            }
        } else {
            for (int i = 0; i < n; i++) {
                double vi = v[i];
                for (int j = 0; j < n; j++, p++) {
                    kd[p] = Math.pow(gamma * (vi * v[j]) + coef0, degree);
                }
            }
        }
        return SimpleMatrix.wrap(K);
    }

    // Median-distance bandwidth for 1-D vectors (Silverman-ish but robust)
    private static double bandwidth1D(double[] v) {
        int n = v.length;
        if (n < 2) return 1.0;
        // Collect pairwise squared distances (can sample for large n)
        // For simplicity, subsample up to 512 points to keep O(n^2) modest
        int m = Math.min(n, 512);
        // uniform sub-sample without replacement
        int[] idx = new int[n];
        for (int i = 0; i < n; i++) idx[i] = i;
        Random r = new Random(7);
        for (int i = 0; i < m; i++) {
            int j = i + r.nextInt(n - i);
            int t = idx[i];
            idx[i] = idx[j];
            idx[j] = t;
        }
        List<Double> d2 = new ArrayList<>(m * (m - 1) / 2);
        for (int a = 0; a < m; a++) {
            int i = idx[a];
            for (int b = a + 1; b < m; b++) {
                int j = idx[b];
                double d = v[i] - v[j];
                d2.add(d * d);
            }
        }
        if (d2.isEmpty()) return 1.0;
        Collections.sort(d2);
        double med2 = d2.get(d2.size() / 2);
        double sigma = Math.sqrt(med2 / 2.0);
        if (!(sigma > 0.0) || !Double.isFinite(sigma)) sigma = 1.0;
        return sigma;
    }

    @Override
    public double getAlpha() {
        return alpha;
    }

    @Override
    public void setAlpha(double alpha) {
        this.alpha = alpha;
    }

    @Override
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z) throws InterruptedException {

        try {
            double p = isIndependenceConditional(x, y, new ArrayList<>(z), this.getAlpha());
            return new IndependenceResult(new IndependenceFact(x, y, z), p > alpha, p, getAlpha() - p);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<Node> getVariables() {
        return new ArrayList<>(variables);
    }

    @Override
    public DataModel getData() {
        return this.dataSet;
    }

    @Override
    public boolean isVerbose() {
        return this.verbose;
    }

    @Override
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    // ---------------------- bandwidth heuristic ----------------------

    /**
     * Conditional KCI test: returns true iff we fail to reject independence at alpha.
     */
    public double isIndependenceConditional(Node x,
                                            Node y,
                                            List<Node> z,
                                            double alpha) {
        Objects.requireNonNull(x, "x");
        Objects.requireNonNull(y, "y");
        if (z == null) z = Collections.emptyList();
        if (rows == null || rows.isEmpty()) {
            this.lastPValue = 1.0;
            return 1.0;
        }
        final int n = rows.size();
        if (n < 2) {
            this.lastPValue = 1.0;
            return 1.0;
        }

        // 1) Centered KZ
        SimpleMatrix KZ = centerKernel(
                kernelMatrix(/*x*/ null, /*z*/ z));

        // 2) RZ = eps * (KZ + eps I)^-1  (cache by Z+rows+eps)
        final String zKey = keyForZ(z, rows, varToRow, getEpsilon());
        DMatrixRMaj RZ_d = rzCache.get(zKey);
        if (RZ_d == null) {
            // KZ + eps I
            DMatrixRMaj KzEps = KZ.copy().plus(SimpleMatrix.identity(n).scale(getEpsilon())).getDDRM();
            // Invert via Cholesky
            LinearSolverDense<DMatrixRMaj> solver = LinearSolverFactory_DDRM.chol(n);
            if (!solver.setA(KzEps)) {
                // Fallback to generic inverse if Cholesky fails (should be rare because of +eps I)
                CommonOps_DDRM.invert(KzEps);
            } else {
                DMatrixRMaj Inv = CommonOps_DDRM.identity(n);
                solver.invert(Inv);
                KzEps = Inv;
            }
            CommonOps_DDRM.scale(getEpsilon(), KzEps);
            RZ_d = KzEps;
            rzCache.put(zKey, RZ_d);
        }
        final SimpleMatrix RZ = SimpleMatrix.wrap(RZ_d);

        // 3) Centered kernels for [X,Z] and Y
        SimpleMatrix KXZ = centerKernel(kernelMatrix(x, z));
        SimpleMatrix KY = getCenteredKy(y); // cached per Y

        // 4) Residualized kernels
        SimpleMatrix RX = RZ.mult(KXZ).mult(RZ);
        RX = symmetrize(RX);

        SimpleMatrix RY = RZ.mult(KY).mult(RZ);
        RY = symmetrize(RY);

        // 5) Test statistic
        final double stat = RX.elementMult(RY).elementSum() / n;

        double p;
        if (isApproximate()) {
            p = pValueGammaConditional(RX, RY, stat, n);
        } else {
            p = permutationPValueConditional(RX, RY, stat, n, getNumPermutations(), rng);
        }

        if (verbose) {
            TetradLogger.getInstance().log(new IndependenceFact(x, y, new HashSet<>(z)) + " p = " + p);
        }

        this.lastPValue = p;
        return p;
    }

    /**
     * Returns centered Ky for variable y (cached per y row index and current rows).
     */
    private SimpleMatrix getCenteredKy(Node y) {
        int ry = varToRow.get(y);
        SimpleMatrix cached = kyCache.get(ry);
        if (cached != null) return cached;
        SimpleMatrix ky = centerKernel(kernelMatrixSingle(ry));
        kyCache.put(ry, ky);
        return ky;
    }

    // ---------------------- p-values ----------------------

    /**
     * Build K for [x]+z (if x==null, it's just Kz).
     */
    private SimpleMatrix kernelMatrix(Node x, List<Node> z) {
        List<Integer> cols = new ArrayList<>((x == null ? 0 : 1) + z.size());
        if (x != null) cols.add(varToRow.get(x));
        for (Node nz : z) cols.add(varToRow.get(nz));

        switch (getKernelType()) {
            case GAUSSIAN -> {
                double sigma = bandwidthGaussian(cols);
                return gaussianKernelMatrix(cols, sigma);
            }
            case LINEAR -> {
                return linearKernelMatrix(cols);
            }
            case POLYNOMIAL -> {
                return polynomialKernelMatrix(cols, getPolyGamma(), getPolyCoef0(), getPolyDegree());
            }
            default -> throw new IllegalStateException("Unknown kernel: " + getKernelType());
        }
    }

//    @Override
//    // === Public API: marginal (unconditional) HSIC p-value for two 1-D arrays ===
//    public double computePValue(double[] x, double[] y) {
//        if (x == null || y == null) throw new IllegalArgumentException("null input");
//        int n = x.length;
//        if (y.length != n) throw new IllegalArgumentException("Length mismatch");
//        if (n <= 1) return 1.0;
//
//        // 1) Build kernels per your selected kernelType
//        SimpleMatrix Kx, Ky;
//        switch (getKernelType()) {
//            case GAUSSIAN -> {
//                double sigmaX = bandwidth1D(x) * getScalingFactor();
//                double sigmaY = bandwidth1D(y) * getScalingFactor();
//                Kx = gaussianKernel1D(x, sigmaX);
//                Ky = gaussianKernel1D(y, sigmaY);
//            }
//            case LINEAR -> {
//                Kx = linearKernel1D(x);
//                Ky = linearKernel1D(y);
//            }
//            case POLYNOMIAL -> {
//                // Use polyGamma if >0, else default to 1/d with d=1 ⇒ 1.0
//                double gamma = (this.getPolyGamma() > 0.0) ? this.getPolyGamma() : 1.0;
//                Kx = polynomialKernel1D(x, gamma, this.getPolyCoef0(), this.getPolyDegree());
//                Ky = polynomialKernel1D(y, gamma, this.getPolyCoef0(), this.getPolyDegree());
//            }
//            default -> throw new IllegalStateException("Unknown kernel type: " + getKernelType());
//        }
//
//        // 2) Center the kernels (same centering you use elsewhere)
//        SimpleMatrix Kxc = centerKernel(Kx);
//        SimpleMatrix Kyc = centerKernel(Ky);
//
//        // 3) HSIC statistic (unconditional): S = (1/n) * tr(Kxc * Kyc)
//        double stat = Kxc.elementMult(Kyc).elementSum() / n;
//
//        // 4) p-value using the same paths as conditional (RZ = I here)
//        if (isApproximate()) {
//            return pValueGammaConditional(Kxc, Kyc, stat, n);
//        } else {
//            return permutationPValueConditional(Kxc, Kyc, stat, n, getNumPermutations(), rng);
//        }
//    }

    /**
     * Build K for a single variable row index (fast path for Ky).
     */
    private SimpleMatrix kernelMatrixSingle(int rowIdx) {
        switch (getKernelType()) {
            case GAUSSIAN -> {
                double sigma = bandwidthGaussian(Collections.singletonList(rowIdx));
                return gaussianKernelMatrix(Collections.singletonList(rowIdx), sigma);
            }
            case LINEAR -> {
                return linearKernelMatrix(Collections.singletonList(rowIdx));
            }
            case POLYNOMIAL -> {
                return polynomialKernelMatrix(Collections.singletonList(rowIdx), getPolyGamma(), getPolyCoef0(), getPolyDegree());
            }
            default -> throw new IllegalStateException("Unknown kernel: " + getKernelType());
        }
    }

    /**
     * Fast Gaussian kernel for a set of variable rows (cols = variables, rows = samples).
     */
    private SimpleMatrix gaussianKernelMatrix(List<Integer> varRows, double sigma) {
        final int n = rows.size();
        final int d = varRows.size();

        // Edge case: no variables → constant kernel (all ones).
        if (d == 0) {
            DMatrixRMaj K = new DMatrixRMaj(n, n);
            Arrays.fill(K.data, 1.0);
            return SimpleMatrix.wrap(K);
        }

        // Build X (n x d): each row is a sample restricted to selected variable rows
        DMatrixRMaj X = new DMatrixRMaj(n, d);
        for (int c = 0; c < d; c++) {
            int vr = varRows.get(c);
            for (int r = 0; r < n; r++) {
                int col = rows.get(r);              // sample index (column in dataVxN)
                X.set(r, c, dataVxN.get(vr, col));  // vr = variable row
            }
        }

        // G = X * X^T  (n x n)   <-- correct EJML call
        DMatrixRMaj G = new DMatrixRMaj(n, n);
        CommonOps_DDRM.multTransB(X, X, G);

        // dist^2_ij = G_ii + G_jj - 2 G_ij
        DMatrixRMaj K = new DMatrixRMaj(n, n);
        double[] kd = K.data;
        double[] diag = new double[n];
        for (int i = 0; i < n; i++) diag[i] = G.get(i, i);

        double inv2s2 = 1.0 / Math.max(2.0 * sigma * sigma, 1e-24);
        int p = 0;
        for (int i = 0; i < n; i++) {
            double di = diag[i];
            for (int j = 0; j < n; j++, p++) {
                double v = di + diag[j] - 2.0 * G.get(i, j);
                kd[p] = Math.exp(-v * inv2s2);
            }
        }
        return SimpleMatrix.wrap(K);
    }

    /**
     * Linear kernel (X Xᵀ) with same layout as the Gaussian helper.
     */
    private SimpleMatrix linearKernelMatrix(List<Integer> varRows) {
        final int n = rows.size();
        final int d = varRows.size();

        // Edge case: no variables → linear kernel is identically 0
        if (d == 0) {
            return SimpleMatrix.wrap(new DMatrixRMaj(n, n)); // all zeros
        }

        // Build X (n × d)
        DMatrixRMaj X = new DMatrixRMaj(n, d);
        for (int c = 0; c < d; c++) {
            int vr = varRows.get(c);
            for (int r = 0; r < n; r++) {
                X.set(r, c, dataVxN.get(vr, rows.get(r)));
            }
        }

        // K = X Xᵀ  (n × n)
        DMatrixRMaj K = new DMatrixRMaj(n, n);
        CommonOps_DDRM.multTransB(X, X, K);  // <-- correct call for X * X^T

        return SimpleMatrix.wrap(K);
    }

// === 1-D kernel builders (fast and allocation-light) ===

    /**
     * Polynomial kernel: K = (gamma * (X Xᵀ) + coef0) ^ degree, with X built as (n × d).
     */
    private SimpleMatrix polynomialKernelMatrix(List<Integer> varRows,
                                                double gamma, double coef0, int degree) {
        final int n = rows.size();
        final int d = varRows.size();

        // Edge case: no variables → constant kernel (coef0^degree) * 1
        if (d == 0) {
            DMatrixRMaj K = new DMatrixRMaj(n, n);
            Arrays.fill(K.data, Math.pow(coef0, degree));
            return SimpleMatrix.wrap(K);
        }

        // Build X (n × d)
        DMatrixRMaj X = new DMatrixRMaj(n, d);
        for (int c = 0; c < d; c++) {
            int vr = varRows.get(c);
            for (int r = 0; r < n; r++) {
                X.set(r, c, dataVxN.get(vr, rows.get(r)));
            }
        }

        // G = X Xᵀ (n × n)
        DMatrixRMaj G = new DMatrixRMaj(n, n);
        CommonOps_DDRM.multTransB(X, X, G);

        // K = (gamma * G + coef0)^degree  (elementwise power)
        DMatrixRMaj K = new DMatrixRMaj(n, n);
        double[] gd = G.data, kd = K.data;
        final double a = gamma;
        final double b = coef0;
        if (degree == 1) {
            // Fast path: linear + bias
            for (int i = 0; i < kd.length; i++) kd[i] = a * gd[i] + b;
        } else if (degree == 2) {
            for (int i = 0; i < kd.length; i++) {
                double v = a * gd[i] + b;
                kd[i] = v * v;
            }
        } else {
            for (int i = 0; i < kd.length; i++) {
                kd[i] = Math.pow(a * gd[i] + b, degree);
            }
        }
        return SimpleMatrix.wrap(K);
    }

    /**
     * Median pairwise distance heuristic for Gaussian sigma, scaled by scalingFactor. Uses a light subsample for speed
     * when n is large.
     */
    private double bandwidthGaussian(List<Integer> varRows) {
        // If a hint matrix is provided and you have your own convention, you can read it here.
        // Otherwise compute from data.
        final int n = rows.size();
        final int d = varRows.size();

        // Build X (n x d)
        DMatrixRMaj X = new DMatrixRMaj(n, d);
        for (int c = 0; c < d; c++) {
            int vr = varRows.get(c);
            for (int r = 0; r < n; r++) {
                X.set(r, c, dataVxN.get(vr, rows.get(r)));
            }
        }

        // Subsample if n is large
        int m = Math.min(n, 256);
        int[] idx = uniformSample(n, m, rng);

        // Collect pairwise squared distances for the subsample
        List<Double> dists = new ArrayList<>(m * (m - 1) / 2);
        for (int a = 0; a < m; a++) {
            int i = idx[a];
            for (int b = a + 1; b < m; b++) {
                int j = idx[b];
                double s = 0.0;
                for (int c = 0; c < d; c++) {
                    double diff = X.get(i, c) - X.get(j, c);
                    s += diff * diff;
                }
                dists.add(s);
            }
        }
        if (dists.isEmpty()) return 1.0; // degenerate

        Collections.sort(dists);
        double med2 = dists.get(dists.size() / 2); // median of squared distance
        double sigma = Math.sqrt(med2 / 2.0);
        if (!(sigma > 0.0) || !Double.isFinite(sigma)) sigma = 1.0;
        sigma *= getScalingFactor();
        return sigma;
    }

    public double computePValue(double[] x, double[] y) {
        if (x == null || y == null) return 1.0;
        int n = x.length;
        if (y.length != n || n < 3) return 1.0;

        // Build a tiny 2-column dataset for BFIT
        DataSet ds = twoColumnDataSet("X", x, "Y", y);

        // Build the BFIT test bound to this dataset
        Kci test = new Kci(ds);
        test.setAlpha(alpha);
        test.setVerbose(verbose);

        // Resolve nodes and run the marginal test
        Node X = ds.getVariable("X");
        Node Y = ds.getVariable("Y");

        IndependenceResult r = null;
        try {
            r = test.checkIndependence(X, Y, Collections.emptySet());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        double p = (r != null) ? r.getPValue() : 1.0;

        // Clamp for numeric robustness
        if (!Double.isFinite(p)) return 1.0;
        return Math.max(0.0, Math.min(p, 1.0));

//        throw new UnsupportedOperationException(
//                "Use checkIndependence(Node,Node,Set) with the dataset; array version is unsupported for block tests.");
    }

    // === Optional: if you like your previous factoring ===
    public double computePValueFromCenteredKernels(SimpleMatrix centeredKx,
                                                   SimpleMatrix centeredKy) {
        int n = centeredKx.getNumRows();
        if (n != centeredKx.getNumCols() || n != centeredKy.getNumRows() || n != centeredKy.getNumCols())
            throw new IllegalArgumentException("Centered kernels must be n×n");
        double stat = centeredKx.elementMult(centeredKy).elementSum() / n;
        if (isApproximate()) {
            return pValueGammaConditional(centeredKx, centeredKy, stat, n);
        } else {
            return permutationPValueConditional(centeredKx, centeredKy, stat, n, getNumPermutations(), rng);
        }
    }

    /**
     * Polynomial kernel params: k(u,v) = (polyGamma * u·v + polyCoef0)^polyDegree
     */
    public int getPolyDegree() {
        return polyDegree;
    }

    public void setPolyDegree(int polyDegree) {
        this.polyDegree = polyDegree;
    }

    /**
     * Polynomial kernel params: k(u,v) = (polyGamma * u·v + polyCoef0)^polyDegree
     */
    public double getPolyCoef0() {
        return polyCoef0;
    }

    public void setPolyCoef0(double polyCoef0) {
        this.polyCoef0 = polyCoef0;
    }

    /**
     * Polynomial kernel params: k(u,v) = (polyGamma * u·v + polyCoef0)^polyDegree
     */
    public double getPolyGamma() {
        return polyGamma;
    }

    public void setPolyGamma(double polyGamma) {
        this.polyGamma = polyGamma;
    }

    /**
     * Kernel type (default Gaussian).
     */
    public KernelType getKernelType() {
        return kernelType;
    }

    public void setKernelType(KernelType kernelType) {
        this.kernelType = kernelType;
    }

    /**
     * Additive diagonal jitter before inversion of KZ (must be > 0).
     */
    public double getEpsilon() {
        return epsilon;
    }

    public void setEpsilon(double epsilon) {
        this.epsilon = epsilon;
    }

    /**
     * Scaling for Gaussian bandwidth heuristic (sigma *= scalingFactor).
     */
    public double getScalingFactor() {
        return scalingFactor;
    }

    public void setScalingFactor(double scalingFactor) {
        this.scalingFactor = scalingFactor;
    }

    /**
     * If true, use Gamma approximation; else run permutation test.
     */
    public boolean isApproximate() {
        return approximate;
    }

    public void setApproximate(boolean approximate) {
        this.approximate = approximate;
    }

    /**
     * Permutation count if approximate=false.
     */
    public int getNumPermutations() {
        return numPermutations;
    }

    public void setNumPermutations(int numPermutations) {
        this.numPermutations = numPermutations;
    }

    public enum KernelType {GAUSSIAN, LINEAR, POLYNOMIAL}
}