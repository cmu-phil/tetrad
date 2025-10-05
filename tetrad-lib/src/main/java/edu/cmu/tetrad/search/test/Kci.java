/// ////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
/// ////////////////////////////////////////////////////////////////////////////

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
 * The Kci class implements the Kernel-based Conditional Independence (KCI) test for statistical independence between
 * variables. It supports various kernel types (e.g., Gaussian, Polynomial, Linear) and provides both Gamma
 * approximation as well as permutation-based p-value computation. This class utilizes kernel matrices and bandwidth
 * selection heuristics for efficient statistical test computation.
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
    private final Map<String, DMatrixRMaj> rzCache = new LinkedHashMap<>(128, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, DMatrixRMaj> e) {
            return size() > 64;
        }
    };
    /**
     * Optional small cache for Ky per Y (helps inside PC/FCI loops).
     */
    private final Map<Integer, SimpleMatrix> kyCache = new LinkedHashMap<>(64, 0.75f, true) {
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
     * Specifies the degree of the polynomial in the polynomial kernel function.
     * <p>
     * The polynomial kernel is defined as: k(u, v) = (polyGamma * (u · v) + polyCoef0) ^ polyDegree. This variable
     * determines the exponent applied to the kernel computation.
     * <p>
     * A higher degree increases the capacity of the kernel to model relationships between data points but may also
     * increase the risk of overfitting.
     * <p>
     * By default, polyDegree is initialized to 2, which represents a quadratic polynomial kernel.
     */
    private int polyDegree = 2;
    /**
     * Represents the polynomial coefficient "coef0" used in the polynomial kernel function. It is an additive constant
     * in the kernel formula defined as: k(u, v) = (polyGamma * (u · v) + polyCoef0)^polyDegree. This value influences
     * the behavior of the kernel, particularly its non-linearity. The default value is initialized to 1.0.
     */
    private double polyCoef0 = 1.0;
    /**
     * The scaling factor for the polynomial kernel in the form k(u, v) = (polyGamma * u·v + polyCoef0)^polyDegree.
     * <p>
     * This parameter acts as a multiplier for the dot product of the two vectors (u and v) in the polynomial kernel
     * computation. Adjusting this value changes the influence of the inner product in the overall kernel function.
     * <p>
     * Default value is 1.0, but it can be set for automatic scaling (e.g., 1.0/d, where d is the dimension of the input
     * space).
     */
    private double polyGamma = 1.0;   // set yourself (e.g., 1.0/d) if you want automatic scaling
    /**
     * Specifies the type of kernel function used for kernel-based computations in the Kci class. The kernel type
     * determines how input data is transformed or modeled to compute similarity or relationships between data points.
     * By default, it is set to the Gaussian (RBF) kernel.
     */
    private KernelType kernelType = KernelType.GAUSSIAN;
    /**
     * A small constant value used to add jitter before the inversion of a kernel matrix (e.g., KZ). This parameter is
     * essential to ensure numerical stability in computations, particularly when matrices are close to singular or have
     * extremely small eigenvalues.
     * <p>
     * The default value for this constant is set to 1e-3.
     */
    private double epsilon = 1e-3;
    // ---------------------- configuration hooks ----------------------
    /**
     * A scaling factor used to modify the bandwidth in Gaussian kernel calculations by scaling it multiplicatively
     * (sigma *= scalingFactor). This value is primarily utilized within the Gaussian bandwidth heuristic to adjust the
     * spread or sensitivity of the kernel.
     * <p>
     * The default value is 1.0, meaning no scaling is applied to the bandwidth unless explicitly modified. A
     * user-specified value can be set to customize the scaling behavior for specific kernels or data sets.
     */
    private double scalingFactor = 1.0;
    /**
     * Indicates whether the Gamma approximation should be used for statistical tests. If set to true, the Gamma
     * approximation is employed to compute p-values. If set to false, a permutation test is performed as an alternative
     * method.
     */
    private boolean approximate = true;
    /**
     * Specifies the number of permutations to be used in permutation-based statistical tests. This variable is used
     * when conducting tests that involve random shuffling of data to approximate a distribution. It determines the
     * number of random permutations to perform during the computation.
     */
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
    /**
     * The significance level (alpha) used in statistical hypothesis testing
     * to determine the threshold for rejecting the null hypothesis.
     * A smaller value indicates a stricter threshold.
     *
     * Default value is 0.01.
     */
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
     * Constructs a Kci instance using specified data, variable-to-row mapping, an optional hint matrix,
     * and a list of row indices.
     * This constructor initializes the internal fields required for kernel-based independence testing.
     *
     * @param dataVxN a SimpleMatrix representing the data matrix where rows correspond to variables
     *                and columns correspond to observations.
     * @param varToRow a map from Node instances to integer indices, specifying the row mapping for variables.
     * @param hHint a SimpleMatrix used as a hint for the kernel computation, often representing
     *              precomputed or auxiliary data; can be null if not applicable.
     * @param rows a list of integers representing the indices of rows to be used in the computation.
     */
    public Kci(SimpleMatrix dataVxN, Map<Node, Integer> varToRow, SimpleMatrix hHint, List<Integer> rows) {
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
     * Simple numeric symmetrization: (A + Aáµ)/2.
     */
    private static SimpleMatrix symmetrize(SimpleMatrix A) {
        return A.plus(A.transpose()).scale(0.5);
    }

    /**
     * Cache key for RZ using sorted Z variable rows + n + eps.
     */
    private static String keyForZ(List<Node> z, List<Integer> rows, Map<Node, Integer> varToRow, double eps) {
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
        // Partial FisherâYates
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
    private static double pValueGammaConditional(SimpleMatrix RX, SimpleMatrix RY, double stat, int n) {
        if (stat <= 0.0 || n <= 1) return 1.0;

        final int N = n;
        final double[] rx = RX.getDDRM().data;
        final double[] ry = RY.getDDRM().data;

        // --- 1) Estimate null mean and variance via a small number of permutations
        final int Bmom = 200;               // 128â512 is a good range
        final Random rng = new Random(7);   // fixed seed for stability in tests

        double mean = 0.0, m2 = 0.0;
        int[] idx = new int[N];
        for (int i = 0; i < N; i++) idx[i] = i;

        for (int b = 0; b < Bmom; b++) {
            // FisherâYates shuffle of idx
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
     * tr(RX * P RY Páµ).
     */
    private static double permutationPValueConditional(SimpleMatrix RX, SimpleMatrix RY, double stat, int n, int numPermutations, Random rng) {
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

    private static DataSet twoColumnDataSet(String nameX, double[] x, String nameY, double[] y) {
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

    /**
     * Retrieves the value of the alpha threshold, which is generally used for
     * statistical tests to determine the significance or rejection criteria.
     *
     * @return the value of alpha as a double.
     */
    @Override
    public double getAlpha() {
        return alpha;
    }

    /**
     * Sets the value of the alpha threshold, which is typically used for statistical testing
     * to determine the significance level or rejection criteria.
     *
     * @param alpha the value of alpha to set, represented as a double.
     */
    @Override
    public void setAlpha(double alpha) {
        this.alpha = alpha;
    }

    /**
     * Tests the conditional independence of two given variables (x and y) with respect to a set of conditioning
     * variables (z) using the KCI (Kernel-based Conditional Independence) method. This method evaluates
     * whether x and y are independent given z by calculating a p-value and comparing it against the alpha threshold.
     *
     * @param x the first variable to be tested for independence, represented as a Node.
     * @param y the second variable to be tested for independence, represented as a Node.
     * @param z the set of conditioning variables, represented as a Set of Node objects.
     * @return an IndependenceResult object containing the results of the independence test, including the
     *         independence fact, the p-value, and additional statistical details.
     * @throws InterruptedException if the thread executing the method is interrupted during execution.
     */
    @Override
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z) throws InterruptedException {

        try {
            double p = isIndependenceConditional(x, y, new ArrayList<>(z), this.getAlpha());
            return new IndependenceResult(new IndependenceFact(x, y, z), p > alpha, p, getAlpha() - p);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Retrieves the list of variables associated with the current instance.
     * This method returns a new list containing the variables, ensuring
     * that modifications to the returned list do not affect the original list.
     *
     * @return a List of Node objects representing the variables.
     */
    @Override
    public List<Node> getVariables() {
        return new ArrayList<>(variables);
    }

    /**
     * Retrieves the data model associated with the current instance.
     *
     * @return the DataModel object representing the dataset being analyzed.
     */
    @Override
    public DataModel getData() {
        return this.dataSet;
    }

    /**
     * Indicates whether verbose mode is enabled.
     *
     * @return true if verbose mode is enabled, false otherwise
     */
    @Override
    public boolean isVerbose() {
        return this.verbose;
    }

    /**
     * Sets the verbose mode for the current instance.
     *
     * @param verbose True, if so.
     */
    @Override
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    // ---------------------- bandwidth heuristic ----------------------

    /**
     * Tests for conditional independence between two variables given a set of conditioning variables.
     * This method computes a test statistic and its corresponding p-value using either an approximate
     * method or a permutation-based method depending on the configuration.
     *
     * @param x The first variable to test for independence.
     * @param y The second variable to test for independence.
     * @param z The list of conditioning variables.
     * @param alpha The significance level used for the independence test.
     * @return The p-value of the conditional independence test. A small p-value (less than alpha)
     * indicates that x and y are not conditionally independent given z.
     * @throws NullPointerException If x or y is null.
     */
    public double isIndependenceConditional(Node x, Node y, List<Node> z, double alpha) {
        Objects.requireNonNull(x, "x");
        Objects.requireNonNull(y, "y");
        if (z == null) z = Collections.emptyList();
        if (rows == null || rows.isEmpty()) {
            return 1.0;
        }
        final int n = rows.size();
        if (n < 2) {
            return 1.0;
        }

        // 1) Centered KZ
        SimpleMatrix KZ = centerKernel(kernelMatrix(/*x*/ null, /*z*/ z));

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

        return p;
    }

    /**
     * Computes and returns the centered kernel matrix for the given node.
     *
     * @param y the input node for which the centered kernel matrix is computed
     * @return the centered kernel matrix corresponding to the specified node
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

    /**
     * Computes the kernel matrix for a single row index using the specified kernel type.
     *
     * @param rowIdx the index of the row for which the kernel matrix is computed
     * @return the computed kernel matrix for the given row index
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
     * Computes the Gaussian kernel matrix for the given rows and bandwidth parameter sigma.
     * The Gaussian kernel matrix is calculated using the Radial Basis Function (RBF) kernel,
     * which measures similarity between data points in a multidimensional feature space.
     *
     * @param varRows A list of indices representing the selected variable rows to include in the computation.
     * @param sigma The bandwidth parameter of the Gaussian kernel. It controls the width of the kernel function.
     * @return A SimpleMatrix representing the computed Gaussian kernel matrix, where each entry (i, j) corresponds
     *         to the kernel similarity between the i-th and j-th data points.
     */
    private SimpleMatrix gaussianKernelMatrix(List<Integer> varRows, double sigma) {
        final int n = rows.size();
        final int d = varRows.size();

        // Edge case: no variables â constant kernel (all ones).
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
     * Computes the linear kernel matrix for the given rows of variables.
     *
     * @param varRows a list of integers representing the indices of the variables to include
     *                in the linear kernel computation.
     * @return a SimpleMatrix object representing the linear kernel matrix, where each entry
     *         is computed as the dot product of the corresponding rows of the variable subset.
     */
    private SimpleMatrix linearKernelMatrix(List<Integer> varRows) {
        final int n = rows.size();
        final int d = varRows.size();

        // Edge case: no variables â linear kernel is identically 0
        if (d == 0) {
            return SimpleMatrix.wrap(new DMatrixRMaj(n, n)); // all zeros
        }

        // Build X (n Ã d)
        DMatrixRMaj X = new DMatrixRMaj(n, d);
        for (int c = 0; c < d; c++) {
            int vr = varRows.get(c);
            for (int r = 0; r < n; r++) {
                X.set(r, c, dataVxN.get(vr, rows.get(r)));
            }
        }

        // K = X Xáµ  (n Ã n)
        DMatrixRMaj K = new DMatrixRMaj(n, n);
        CommonOps_DDRM.multTransB(X, X, K);  // <-- correct call for X * X^T

        return SimpleMatrix.wrap(K);
    }

// === 1-D kernel builders (fast and allocation-light) ===

    /**
     * Computes the polynomial kernel matrix for a given set of variable rows, using the specified
     * kernel parameters gamma, coef0, and degree. The kernel matrix is calculated as
     * (gamma * G + coef0) ^ degree, where G = X * Xᵀ, and X is constructed from the input variable rows.
     *
     * @param varRows A list of indices representing the variable rows used to construct the matrix X.
     * @param gamma The scalar factor by which the dot product matrix G is scaled within the kernel computation.
     * @param coef0 An additive constant applied before raising the result to the specified degree.
     * @param degree The degree of the polynomial kernel.
     * @return A SimpleMatrix representing the computed polynomial kernel matrix.
     */
    private SimpleMatrix polynomialKernelMatrix(List<Integer> varRows, double gamma, double coef0, int degree) {
        final int n = rows.size();
        final int d = varRows.size();

        // Edge case: no variables â constant kernel (coef0^degree) * 1
        if (d == 0) {
            DMatrixRMaj K = new DMatrixRMaj(n, n);
            Arrays.fill(K.data, Math.pow(coef0, degree));
            return SimpleMatrix.wrap(K);
        }

        // Build X (n Ã d)
        DMatrixRMaj X = new DMatrixRMaj(n, d);
        for (int c = 0; c < d; c++) {
            int vr = varRows.get(c);
            for (int r = 0; r < n; r++) {
                X.set(r, c, dataVxN.get(vr, rows.get(r)));
            }
        }

        // G = X Xáµ (n Ã n)
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
     * Computes the Gaussian kernel bandwidth based on a subset of the pairwise distances between data points.
     * The method estimates the bandwidth using the median of the pairwise squared distances,
     * following a subsampling approach for computational efficiency when the dataset is large.
     *
     * @param varRows a list of integer indices representing the variable rows to be included in the calculation.
     * @return the computed Gaussian kernel bandwidth (standard deviation), adjusted by a scaling factor.
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

    /**
     * Computes the p-value for testing the independence of two variables
     * represented by the input arrays. The method utilizes a kernel-based
     * conditional independence test (KCI) provided by BFIT.
     *
     * @param x the first array of observed values representing one variable.
     *          It must not be null and should contain at least three elements.
     * @param y the second array of observed values representing another variable.
     *          It must not be null, should contain at least three elements,
     *          and have the same length as the first array.
     * @return the computed p-value as a double. A result closer to 0 suggests
     *         stronger evidence against the null hypothesis of independence,
     *         while a value close to 1 supports independence. If the input
     *         arrays are invalid or if an error occurs, the method returns 1.0.
     */
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

    /**
     * Computes the p-value from two centered kernel matrices using statistical methods.
     * Depending on whether an approximate or exact method is specified, it calculates
     * the p-value using a gamma distribution or a permutation test.
     *
     * @param centeredKx A centered kernel matrix (n x n) representing one dataset.
     * @param centeredKy A centered kernel matrix (n x n) representing another dataset.
     * @return The computed p-value indicating the statistical relationship between the two datasets.
     * @throws IllegalArgumentException If the provided matrices are not square and of the same dimensions (n x n).
     */
    public double computePValueFromCenteredKernels(SimpleMatrix centeredKx, SimpleMatrix centeredKy) {
        int n = centeredKx.getNumRows();
        if (n != centeredKx.getNumCols() || n != centeredKy.getNumRows() || n != centeredKy.getNumCols())
            throw new IllegalArgumentException("Centered kernels must be nÃn");
        double stat = centeredKx.elementMult(centeredKy).elementSum() / n;
        if (isApproximate()) {
            return pValueGammaConditional(centeredKx, centeredKy, stat, n);
        } else {
            return permutationPValueConditional(centeredKx, centeredKy, stat, n, getNumPermutations(), rng);
        }
    }

    /**
     * Retrieves the degree of the polynomial.
     *
     * @return the degree of the polynomial as an integer
     */
    public int getPolyDegree() {
        return polyDegree;
    }

    /**
     * Sets the degree of the polynomial.
     *
     * @param polyDegree the degree of the polynomial to be set
     */
    public void setPolyDegree(int polyDegree) {
        this.polyDegree = polyDegree;
    }

    /**
     * Retrieves the coefficient of the polynomial for the term of degree 0.
     *
     * @return the value of the polynomial coefficient for the term of degree 0
     */
    public double getPolyCoef0() {
        return polyCoef0;
    }

    /**
     * Sets the value of the polynomial coefficient at index 0.
     *
     * @param polyCoef0 the value to set for the polynomial coefficient at index 0
     */
    public void setPolyCoef0(double polyCoef0) {
        this.polyCoef0 = polyCoef0;
    }

    /**
     * Retrieves the gamma parameter for the polynomial kernel.
     *
     * @return the gamma parameter for the polynomial kernel
     */
    public double getPolyGamma() {
        return polyGamma;
    }

    /**
     * Sets the polyGamma value.
     *
     * @param polyGamma the value to set for the polyGamma property
     */
    public void setPolyGamma(double polyGamma) {
        this.polyGamma = polyGamma;
    }

    /**
     * Retrieves the kernel type.
     *
     * @return the kernel type
     */
    public KernelType getKernelType() {
        return kernelType;
    }

    /**
     * Sets the kernel type.
     *
     * @param kernelType the kernel type to set
     */
    public void setKernelType(KernelType kernelType) {
        this.kernelType = kernelType;
    }

    /**
     * Retrieves the epsilon value.
     *
     * @return the epsilon value
     */
    public double getEpsilon() {
        return epsilon;
    }

    /**
     * Sets the epsilon value.
     *
     * @param epsilon the epsilon value to set
     */
    public void setEpsilon(double epsilon) {
        this.epsilon = epsilon;
    }

    /**
     * Retrieves the scaling factor for the Gaussian bandwidth heuristic.
     *
     * @return the scaling factor
     */
    public double getScalingFactor() {
        return scalingFactor;
    }

    /**
     * Sets the scaling factor for the Gaussian bandwidth heuristic. The scaling factor is used to modify the bandwidth
     * by scaling it multiplicatively (sigma *= scalingFactor).
     *
     * @param scalingFactor the scaling factor to set; a multiplier for the Gaussian bandwidth heuristic.
     */
    public void setScalingFactor(double scalingFactor) {
        this.scalingFactor = scalingFactor;
    }

    /**
     * Retrieves whether the method should use an approximate approach or a permutation test.
     *
     * @return true if approximate method is used, false if permutation test is used
     */
    public boolean isApproximate() {
        return approximate;
    }

    /**
     * Sets whether the method should use an approximate approach or a permutation test.
     *
     * @param approximate true to use approximate method, false to use permutation test
     */
    public void setApproximate(boolean approximate) {
        this.approximate = approximate;
    }

    /**
     * Retrieves the number of permutations to be used in permutation tests.
     *
     * @return the number of permutations to be used in permutation tests
     */
    public int getNumPermutations() {
        return numPermutations;
    }

    /**
     * Sets the number of permutations to be used in permutation tests.
     *
     * @param numPermutations the number of permutations to set, typically used when conducting statistical tests that
     *                        involve random shuffling of data to approximate a distribution.
     */
    public void setNumPermutations(int numPermutations) {
        this.numPermutations = numPermutations;
    }

    /**
     * Enum representing the type of kernel function used in kernel-based computations. The kernel type determines how
     * the input data is transformed or structured to measure similarity or relationships.
     */
    public enum KernelType {

        /**
         * Represents the Gaussian (RBF) kernel, commonly used for non-linear transformations.
         */
        GAUSSIAN,

        /**
         * Represents the linear kernel, useful for linear relationships.
         */
        LINEAR,

        /**
         * Represents the polynomial kernel, which generalizes the linear kernel by introducing polynomial terms.
         */
        POLYNOMIAL
    }
}
