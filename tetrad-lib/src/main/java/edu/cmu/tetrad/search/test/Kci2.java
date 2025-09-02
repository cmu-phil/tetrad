 package edu.cmu.tetrad.search.test;

 import edu.cmu.tetrad.data.DataModel;
 import edu.cmu.tetrad.data.DataSet;
 import edu.cmu.tetrad.graph.IndependenceFact;
 import edu.cmu.tetrad.graph.Node;
 import edu.cmu.tetrad.search.IndependenceTest;
 import org.apache.commons.math3.distribution.GammaDistribution;
 import org.ejml.data.DMatrixRMaj;
 import org.ejml.dense.row.CommonOps_DDRM;
 import org.ejml.dense.row.factory.LinearSolverFactory_DDRM;
 import org.ejml.interfaces.linsol.LinearSolverDense;
 import org.ejml.simple.SimpleMatrix;

 import java.util.*;

 /**
  * Fast KCI (Kernel-based Conditional Independence) scaffold tuned for EJML 0.44.0.
  *
  * Key optimizations:
  *  - O(n^2) centering (no H K H multiplies)
  *  - Fast Gaussian kernel via one X·Xᵀ GEMM + vectorized exp
  *  - Cache of RZ = eps * (KZ + eps I)^{-1} per (Z, rows, eps) key
  *
  * Null:  X ⟂ Y | Z
  * Test:  S = (1/n) * tr(RZ*K_[X,Z]*RZ * RZ*K_Y*RZ) with Gamma tail approx or permutation fallback.
  *
  * Notes:
  *  - This is a focused class; integrate/rename fields/methods as needed for your codebase.
  *  - For large n, consider Nyström downsampling before forming kernels.
  */
 public class Kci2 implements IndependenceTest {
     private DataSet dataSet;
     private List<Node> variables;
     private boolean verbose = false;
     private double alpha = 0.01;

     @Override
     public double getAlpha() {
         return alpha;
     }

     @Override
     public void setAlpha(double alpha) {
         this.alpha = alpha;
     }

 // ---------------------- configuration hooks ----------------------

     public enum KernelType { GAUSSIAN, LINEAR }

     /** Kernel type (default Gaussian). */
     public KernelType kernelType = KernelType.GAUSSIAN;

     /** Additive diagonal jitter before inversion of KZ (must be > 0). */
     public double epsilon = 1e-3;

     /** Scaling for Gaussian bandwidth heuristic (sigma *= scalingFactor). */
     public double scalingFactor = 1.0;

     /** If true, use Gamma approximation; else run permutation test. */
     public boolean approximate = false;

     /** Permutation count if approximate=false. */
     public int numPermutations = 1000;

     /** RNG for permutations; can be null (seeded later). */
     public Random rng = new Random(0);

     /** Optional: last computed p-value. */
     public double lastPValue = Double.NaN;

     // ---------------------- data / indices ----------------------

     /** Data matrix in "variables x samples" layout to match common Tetrad use. */
     private final SimpleMatrix dataVxN;

     /** Optional bandwidth hints (not required). */
     private final SimpleMatrix hHint;

     /** Map variable Node -> column index in dataVxN (row in matrix terms). */
     private Map<Node, Integer> varToRow;

     /** Active row indices (samples) used in this test run. */
     private List<Integer> rows;

     // ---------------------- caches ----------------------

     /** LRU-ish cache for RZ matrices keyed by (Z, rows, eps). */
     private final Map<String, DMatrixRMaj> rzCache =
             new LinkedHashMap<>(128, 0.75f, true) {
                 @Override
                 protected boolean removeEldestEntry(Map.Entry<String, DMatrixRMaj> e) {
                     return size() > 64;
                 }
             };

     /** Optional small cache for Ky per Y (helps inside PC/FCI loops). */
     private final Map<Integer, SimpleMatrix> kyCache =
             new LinkedHashMap<>(64, 0.75f, true) {
                 @Override
                 protected boolean removeEldestEntry(Map.Entry<Integer, SimpleMatrix> e) {
                     return size() > 64;
                 }
             };

     // ---------------------- ctor ----------------------

     public Kci2(DataSet dataSet) {
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

     /**
      * @param dataVxN variables x samples matrix (each row = variable, each column = sample)
      * @param varToRow mapping from Node to its row index in dataVxN
      *  @param hHint optional bandwidth hint matrix; may be null (median heuristic used otherwise)
      * @param rows sample indices to use (0..N-1)
      */
     public Kci2(SimpleMatrix dataVxN,
                 Map<Node, Integer> varToRow,
                 SimpleMatrix hHint,
                 List<Integer> rows) {
         this.dataVxN = dataVxN;
         this.varToRow = varToRow;
         this.hHint = hHint;
         this.rows = rows;
     }

     // ---------------------- public API ----------------------

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
         final String zKey = keyForZ(z, rows, varToRow, epsilon);
         DMatrixRMaj RZ_d = rzCache.get(zKey);
         if (RZ_d == null) {
             // KZ + eps I
             DMatrixRMaj KzEps = KZ.copy().plus(SimpleMatrix.identity(n).scale(epsilon)).getDDRM();
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
             CommonOps_DDRM.scale(epsilon, KzEps);
             RZ_d = KzEps;
             rzCache.put(zKey, RZ_d);
         }
         final SimpleMatrix RZ = SimpleMatrix.wrap(RZ_d);

         // 3) Centered kernels for [X,Z] and Y
         SimpleMatrix KXZ = centerKernel(kernelMatrix(x, z));
         SimpleMatrix KY  = getCenteredKy(y); // cached per Y

         // 4) Residualized kernels
         SimpleMatrix RX = RZ.mult(KXZ).mult(RZ);
         RX = symmetrize(RX);

         SimpleMatrix RY = RZ.mult(KY).mult(RZ);
         RY = symmetrize(RY);

         // 5) Test statistic
         final double stat = RX.elementMult(RY).elementSum() / n;

         double p;
         if (approximate) {
             p = pValueGammaConditional(RX, RY, stat, n);
         } else {
             p = permutationPValueConditional(RX, RY, stat, n, numPermutations, rng);
         }

         System.out.println(new IndependenceFact(x, y, new HashSet<>(z)) + " p = " + p);

         this.lastPValue = p;
         return p;
     }

     // ---------------------- kernels & helpers ----------------------

     /** Returns centered Ky for variable y (cached per y row index and current rows). */
     private SimpleMatrix getCenteredKy(Node y) {
         int ry = varToRow.get(y);
         SimpleMatrix cached = kyCache.get(ry);
         if (cached != null) return cached;
         SimpleMatrix ky = centerKernel(kernelMatrixSingle(ry));
         kyCache.put(ry, ky);
         return ky;
     }

     /** Build K for [x]+z (if x==null, it's just Kz). */
     private SimpleMatrix kernelMatrix(Node x, List<Node> z) {
         List<Integer> cols = new ArrayList<>( (x==null?0:1) + z.size());
         if (x != null) cols.add(varToRow.get(x));
         for (Node nz : z) cols.add(varToRow.get(nz));

         switch (kernelType) {
             case GAUSSIAN -> {
                 double sigma = bandwidthGaussian(cols);
                 return gaussianKernelMatrix(cols, sigma);
             }
             case LINEAR -> {
                 return linearKernelMatrix(cols);
             }
             default -> throw new IllegalStateException("Unknown kernel: " + kernelType);
         }
     }

     /** Build K for a single variable row index (fast path for Ky). */
     private SimpleMatrix kernelMatrixSingle(int rowIdx) {
         switch (kernelType) {
             case GAUSSIAN -> {
                 // sigma from per-dim variance heuristic
                 double sigma = bandwidthGaussian(Collections.singletonList(rowIdx));
                 return gaussianKernelMatrix(Collections.singletonList(rowIdx), sigma);
             }
             case LINEAR -> {
                 return linearKernelMatrix(Collections.singletonList(rowIdx));
             }
             default -> throw new IllegalStateException("Unknown kernel: " + kernelType);
         }
     }

 //    /** Fast Gaussian kernel for a set of variable rows (cols = variables, rows = samples). */
 //    private SimpleMatrix gaussianKernelMatrix(List<Integer> varRows, double sigma) {
 //        final int n = rows.size();
 //        final int d = varRows.size();
 //
 //        // Build X (n x d): each row is a sample restricted to selected variable rows
 //        DMatrixRMaj X = new DMatrixRMaj(n, d);
 //        for (int c = 0; c < d; c++) {
 //            int vr = varRows.get(c);
 //            for (int r = 0; r < n; r++) {
 //                X.set(r, c, dataVxN.get(vr, rows.get(r)));
 //            }
 //        }
 //
 //        // G = X * X^T
 //        DMatrixRMaj G = new DMatrixRMaj(n, n);
 //        CommonOps_DDRM.multInner(X, G); // (n x d) * (n x d)^T
 //
 //        // dist^2_ij = G_ii + G_jj - 2 G_ij
 //        DMatrixRMaj K = new DMatrixRMaj(n, n);
 //        double[] gd = G.data, kd = K.data;
 //        double[] diag = new double[n];
 //        for (int i = 0; i < n; i++) diag[i] = G.get(i, i);
 //        double inv2s2 = 1.0 / Math.max( (2.0 * sigma * sigma), 1e-24 );
 //        int p = 0;
 //        for (int i = 0; i < n; i++) {
 //            double di = diag[i];
 //            for (int j = 0; j < n; j++, p++) {
 //                double v = di + diag[j] - 2.0 * G.get(i, j);
 //                kd[p] = Math.exp(-v * inv2s2);
 //            }
 //        }
 //        return SimpleMatrix.wrap(K);
 //    }

     /** Fast Gaussian kernel for a set of variable rows (cols = variables, rows = samples). */
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

     /** Linear kernel (X Xᵀ) with same layout as the Gaussian helper. */
     private SimpleMatrix linearKernelMatrix(List<Integer> varRows) {
         final int n = rows.size();
         final int d = varRows.size();

         DMatrixRMaj X = new DMatrixRMaj(n, d);
         for (int c = 0; c < d; c++) {
             int vr = varRows.get(c);
             for (int r = 0; r < n; r++) {
                 X.set(r, c, dataVxN.get(vr, rows.get(r)));
             }
         }
         DMatrixRMaj K = new DMatrixRMaj(n, n);
         CommonOps_DDRM.multInner(X, K);
         return SimpleMatrix.wrap(K);
     }

     /** O(n^2) centering: Kc = K - rowMean - colMean + grandMean. */
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

     /** Simple numeric symmetrization: (A + Aᵀ)/2. */
     private static SimpleMatrix symmetrize(SimpleMatrix A) {
         return A.plus(A.transpose()).scale(0.5);
     }

     /** Cache key for RZ using sorted Z variable rows + n + eps. */
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

     // ---------------------- bandwidth heuristic ----------------------

     /**
      * Median pairwise distance heuristic for Gaussian sigma, scaled by scalingFactor.
      * Uses a light subsample for speed when n is large.
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
         sigma *= scalingFactor;
         return sigma;
     }

     private static int[] uniformSample(int n, int m, Random rng) {
         int[] idx = new int[n];
         for (int i = 0; i < n; i++) idx[i] = i;
         // Partial Fisher–Yates
         for (int i = 0; i < m; i++) {
             int j = i + rng.nextInt(n - i);
             int t = idx[i]; idx[i] = idx[j]; idx[j] = t;
         }
         return Arrays.copyOf(idx, m);
     }

     // ---------------------- p-values ----------------------

     /**
      * Gamma-approx p-value for conditional KCI statistic.
      * S = (1/n) * tr(RX * RY) ~ Gamma(k, theta) by moment matching.
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
                 int t = idx[i]; idx[i] = idx[j]; idx[j] = t;
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
         if (!(var  > 0.0) || !Double.isFinite(var )) var  = EPS * mean * mean;

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
      * Permutation p-value for conditional KCI.
      * Permute Y (equivalently, conjugate RY by P) and recompute S_perm = (1/n) tr(RX * P RY Pᵀ).
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
                 int t = idx[i]; idx[i] = idx[j]; idx[j] = t;
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
 }