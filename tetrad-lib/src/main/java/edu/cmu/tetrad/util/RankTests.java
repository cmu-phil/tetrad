package edu.cmu.tetrad.util;

import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.CommonOps_DDRM;
import org.ejml.dense.row.factory.DecompositionFactory_DDRM;
import org.ejml.interfaces.decomposition.CholeskyDecomposition_F64;
import org.ejml.interfaces.decomposition.EigenDecomposition_F64;
import org.ejml.interfaces.decomposition.SingularValueDecomposition_F64;
import org.ejml.simple.SimpleEVD;
import org.ejml.simple.SimpleMatrix;
import org.ejml.simple.SimpleSVD;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public class RankTests {

    private static final int RCCA_CACHE_MAX = 10_000; // tune if needed
    private static final Map<RccaKey, RccaEntry> RCCA_CACHE =
            new LinkedHashMap<>(1024, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<RccaKey, RccaEntry> e) {
                    return size() > RCCA_CACHE_MAX;
                }
            };
    // ---- Eigen whitening path (from previous message), packaged to return svals
    private static final double EIG_FLOOR = 1e-12;

    /**
     * Computes the p-value for Canonical Correlation Analysis (CCA) based on the hypothesis H0: canonical correlation
     * rank ≤ r versus H1: rank > r. The method uses the log-likelihood ratio test on the remaining min(p, q) - r
     * canonical correlations.
     *
     * @param S        The input correlation matrix as a SimpleMatrix.
     * @param xIndices Indices of the first variable group.
     * @param yIndices Indices of the second variable group.
     * @param n        Sample size.
     * @param rank     The hypothesized maximum rank under the null hypothesis.
     * @return p-value from the chi-squared test.
     */
    public static double getCcaPValueRankLE(SimpleMatrix S, int[] xIndices, int[] yIndices, int n, int rank) {
        try {
            int p = xIndices.length;
            int q = yIndices.length;

            // Step 1: Extract submatrices
            SimpleMatrix Cxx = StatUtils.extractSubMatrix(S, xIndices, xIndices);
            SimpleMatrix Cyy = StatUtils.extractSubMatrix(S, yIndices, yIndices);
            SimpleMatrix Cxy = StatUtils.extractSubMatrix(S, xIndices, yIndices);

            // Inverse square roots of Cxx and Cyy using eigen-decomposition
            SimpleMatrix Cxx_inv_sqrt = invSqrtSymmetric(Cxx);
            SimpleMatrix Cyy_inv_sqrt = invSqrtSymmetric(Cyy);

            // Construct matrix M = Cxx^{-1/2} * Cxy * Cyy^{-1/2}
            SimpleMatrix M = Cxx_inv_sqrt.mult(Cxy).mult(Cyy_inv_sqrt);

            // Perform SVD on M
            SimpleSVD<SimpleMatrix> svd = M.svd();
            double[] singularValues = svd.getW().diag().getDDRM().getData();

            // Test statistic
            double stat = 0.0;
            for (int i = rank; i < Math.min(p, q); i++) {
                double s = singularValues[i];
                stat += Math.log(1 - s * s);
            }
            double scale = -(n - (p + q + 3) / 2.);
            stat *= scale;

            // Degrees of freedom
            int df = (p - rank) * (q - rank);

            // Compute p-value
            ChiSquaredDistribution chi2 = new ChiSquaredDistribution(df);
            return 1.0 - chi2.cumulativeProbability(stat);
        } catch (Exception e) {
            return 0.0;
        }
    }

    /**
     * Computes RCCA p-value from a covariance matrix.
     *
     * @param S        The (p+q) x (p+q) covariance matrix.
     * @param xIndices Number of variables in group A.
     * @param yIndices Number of variables in group B.
     * @param n        Sample size.
     * @param rank     Hypothesized rank (test is for rank > r).
     * @param regParam Regularization parameter (λ > 0).
     * @return The p-value.
     */
    public static double getRccaPValueRankLEOrig(SimpleMatrix S, int[] xIndices, int[] yIndices, int n, int rank, double regParam) {
        try {
            int p = xIndices.length;
            int q = yIndices.length;

            // Step 1: Extract submatrices
            SimpleMatrix Cxx = StatUtils.extractSubMatrix(S, xIndices, xIndices).plus(SimpleMatrix.identity(p).scale(regParam));
            SimpleMatrix Cyy = StatUtils.extractSubMatrix(S, yIndices, yIndices).plus(SimpleMatrix.identity(q).scale(regParam));
            SimpleMatrix Cxy = StatUtils.extractSubMatrix(S, xIndices, yIndices);

            // Inverse square roots of Cxx and Cyy using eigen-decomposition
            SimpleMatrix Cxx_inv_sqrt = invSqrtSymmetric(Cxx);
            SimpleMatrix Cyy_inv_sqrt = invSqrtSymmetric(Cyy);

            // Construct matrix M = Cxx^{-1/2} * Cxy * Cyy^{-1/2}
            SimpleMatrix M = Cxx_inv_sqrt.mult(Cxy).mult(Cyy_inv_sqrt);

            // Perform SVD on M
            SimpleSVD<SimpleMatrix> svd = M.svd();
            double[] singularValues = svd.getW().diag().getDDRM().getData();

            // Test statistic
            double stat = 0.0;
            for (int i = rank; i < Math.min(p, q); i++) {
                double s = singularValues[i];
                s = Math.max(0.0, Math.min(s, 1.0 - 1e-12));  // Avoid log(0)
                stat += Math.log(1 - s * s);
            }
            double scale = -(n - (p + q + 3) / 2.);
            stat *= scale;

            // Degrees of freedom
            int df = (p - rank) * (q - rank);

            // Compute p-value
            ChiSquaredDistribution chi2 = new ChiSquaredDistribution(df);
            return 1.0 - chi2.cumulativeProbability(stat);
        } catch (Exception e) {
            return 0.0;
        }
    }

    public static double getRccaPValueRankLE(SimpleMatrix S,
                                             int[] xIdx, int[] yIdx,
                                             int n, int rank, double regLambda, double condThreshold) {
        try {
            final int p = xIdx.length, q = yIdx.length, rmin = Math.min(p, q);
            if (rank < 0 || rank >= rmin) return 1.0;

            // 1) cache lookup
            RccaKey key = new RccaKey(xIdx, yIdx, regLambda);
            RccaEntry entry = cacheGet(key);

            if (entry == null) {
                // MISS: compute svals via your fast Cholesky+solves pipeline
                // (same as the last working version you committed)
                SvdResult sv = computeSvalsHybrid(S, xIdx, yIdx, regLambda, condThreshold); // below
                if (sv == null) return 0.0;

                double[] svals = sv.svals;
                // Build suffix sums of log(1 - s^2) from the end
                double[] suffix = new double[svals.length + 1]; // last is 0
                for (int i = svals.length - 1; i >= 0; i--) {
                    double s = Math.max(0.0, Math.min(svals[i], 1.0 - 1e-12));
                    suffix[i] = suffix[i + 1] + Math.log(1.0 - s * s);
                }
                entry = new RccaEntry(svals, suffix);
                cachePut(key, entry);
            }

            // 2) O(1) stat for any rank: sum_{i=rank}^{rmin-1} log(1 - s_i^2)
            double sumLogs = entry.suffixLogs[rank] - entry.suffixLogs[rmin]; // suffix difference
            double scale = -(n - (p + q + 3) / 2.0);
            double stat = scale * sumLogs;

            int df = (p - rank) * (q - rank);
            if (df <= 0) return 1.0;

            return 1.0 - new org.apache.commons.math3.distribution.ChiSquaredDistribution(df)
                    .cumulativeProbability(stat);
        } catch (Exception e) {
            return 0.0;
        }
    }

    // ---- Public: compute singular values via hybrid whitening (Cholesky -> Eigen fallback)
    static SvdResult computeSvalsHybrid(SimpleMatrix S,
                                        int[] xIdx, int[] yIdx, double reg, double condThreshold) {
        SvdResult sv = computeSvalsCholeskyWhiten_withGuard(S, xIdx, yIdx, reg, condThreshold);
        if (sv != null) return sv;
        return computeSvalsEigenWhiten(S, xIdx, yIdx, reg);
    }

    /**
     * Cholesky whitening with stability guard; return null to trigger fallback.
     *
     * @param S             Covariance matrix.
     * @param xIdx          The indices of the one cluster.
     * @param yIdx          The indices of the other cluster.
     * @param regLambda     The regularization lambda. This will be added as a ridge to correlation matrices.
     * @param condThreshold A trigger on matrix conditioning to return null, to trigger fallback to using eigenvalue
     *                      whitening.
     */
    private static SvdResult computeSvalsCholeskyWhiten_withGuard(SimpleMatrix S,
                                                                  int[] xIdx, int[] yIdx,
                                                                  double regLambda,
                                                                  double condThreshold) {
        if (regLambda < 0) {
            throw new IllegalArgumentException("regLambda must be >= 0");
        }

        if (condThreshold <= 0) {
            return null;
        }

        final int p = xIdx.length, q = yIdx.length;

        DMatrixRMaj Cxx = extract(S, xIdx, xIdx);
        DMatrixRMaj Cyy = extract(S, yIdx, yIdx);
        DMatrixRMaj Cxy = extract(S, xIdx, yIdx);
        addRidgeInPlace(Cxx, regLambda);
        addRidgeInPlace(Cyy, regLambda);

        CholeskyDecomposition_F64<DMatrixRMaj> cholX = DecompositionFactory_DDRM.chol(p, true);
        CholeskyDecomposition_F64<DMatrixRMaj> cholY = DecompositionFactory_DDRM.chol(q, true);
        if (!cholX.decompose(Cxx) || !cholY.decompose(Cyy)) return null; // fail → fallback

        DMatrixRMaj Lx = cholX.getT(null);
        DMatrixRMaj Ly = cholY.getT(null);

        // Condition guard
        if (cholDiagCondition(Lx) > condThreshold || cholDiagCondition(Ly) > condThreshold) return null;

        // Whitening: T = Lx^{-1} * Cxy * Ly^{-T}
        DMatrixRMaj X = Cxy.copy();
        forwardSolveLowerInPlace(Lx, X);
        DMatrixRMaj Xt = new DMatrixRMaj(X.numCols, X.numRows);
        CommonOps_DDRM.transpose(X, Xt);
        backwardSolveUpperFromLowerTransposeInPlace(Ly, Xt);
        DMatrixRMaj T = new DMatrixRMaj(X.numRows, X.numCols);
        CommonOps_DDRM.transpose(Xt, T);

        if (!isFiniteMatrix(T)) return null;

        SingularValueDecomposition_F64<DMatrixRMaj> svd =
                DecompositionFactory_DDRM.svd(T.numRows, T.numCols, false, false, true);
        if (!svd.decompose(T)) return null;

        double[] s = svd.getSingularValues();
        for (double v : s) if (v > 1.0 + 1e-6 || !Double.isFinite(v)) return null;

        return new SvdResult(s);
    }

    private static double cholDiagCondition(DMatrixRMaj L) {
        double dmin = Double.POSITIVE_INFINITY, dmax = 0.0;
        int n = L.numRows;
        for (int i = 0; i < n; i++) {
            double d = L.get(i, i);
            if (d <= 0 || !Double.isFinite(d)) return Double.POSITIVE_INFINITY;
            if (d < dmin) dmin = d;
            if (d > dmax) dmax = d;
        }
        double r = dmax / dmin;
        return r * r;
    }

    private static boolean isFiniteMatrix(DMatrixRMaj A) {
        double[] a = A.data;
        for (double v : a) {
            if (!Double.isFinite(v)) return false;
        }
        return true;
    }

    private static SvdResult computeSvalsEigenWhiten(SimpleMatrix S,
                                                     int[] xIdx, int[] yIdx, double reg) {
        DMatrixRMaj Cxx = extract(S, xIdx, xIdx);
        DMatrixRMaj Cyy = extract(S, yIdx, yIdx);
        DMatrixRMaj Cxy = extract(S, xIdx, yIdx);
        addRidgeInPlace(Cxx, reg);
        addRidgeInPlace(Cyy, reg);

        EigenSym ESx = eigSym(Cxx);
        EigenSym ESy = eigSym(Cyy);

        // T = Λx^{-1/2} * (Qx^T Cxy Qy) * Λy^{-1/2}
        DMatrixRMaj temp = new DMatrixRMaj(ESx.Q.numCols, Cxy.numCols);
        CommonOps_DDRM.multTransA(ESx.Q, Cxy, temp);
        DMatrixRMaj T = new DMatrixRMaj(temp.numRows, ESy.Q.numCols);
        CommonOps_DDRM.mult(temp, ESy.Q, T);

        scaleRowsInvSqrtInPlace(T, ESx.lambda);
        scaleColsInvSqrtInPlace(T, ESy.lambda);

        SingularValueDecomposition_F64<DMatrixRMaj> svd =
                DecompositionFactory_DDRM.svd(T.numRows, T.numCols, false, false, true);
        if (!svd.decompose(T)) return null;

        return new SvdResult(svd.getSingularValues());
    }

    private static EigenSym eigSym(DMatrixRMaj A) {
        final int n = A.numRows;
        EigenDecomposition_F64<DMatrixRMaj> eig = DecompositionFactory_DDRM.eig(n, true);
        if (!eig.decompose(A)) throw new RuntimeException("Eigen decomposition failed");
        // collect pairs
        double[] vals = new double[n];
        DMatrixRMaj Q = new DMatrixRMaj(n, n);
        int k = 0;
        for (int i = 0; i < n; i++) {
            double real = eig.getEigenvalue(i).getReal();
            // (symmetric SPD => imag=0; guard just in case)
            vals[k] = real;
            DMatrixRMaj v = eig.getEigenVector(i);
            // normalize column and copy into Q
            if (v == null) throw new RuntimeException("Null eigenvector (unexpected for symmetric)");
            // copy v into column k of Q
            for (int r = 0; r < n; r++) Q.set(r, k, v.get(r, 0));
            k++;
        }
        // sort by eigenvalue descending and permute columns of Q
        int[] order = argsortDesc(vals);
        double[] sorted = new double[n];
        DMatrixRMaj Qsorted = new DMatrixRMaj(n, n);
        for (int j = 0; j < n; j++) {
            int idx = order[j];
            sorted[j] = Math.max(vals[idx], EIG_FLOOR); // floor small/negatives
            for (int r = 0; r < n; r++) Qsorted.set(r, j, Q.get(r, idx));
        }
        return new EigenSym(Qsorted, sorted);
    }

    private static int[] argsortDesc(double[] a) {
        Integer[] idx = new Integer[a.length];
        for (int i = 0; i < a.length; i++) idx[i] = i;
        java.util.Arrays.sort(idx, (i, j) -> Double.compare(a[j], a[i]));
        int[] out = new int[a.length];
        for (int i = 0; i < a.length; i++) out[i] = idx[i];
        return out;
    }

    private static void scaleRowsInvSqrtInPlace(DMatrixRMaj A, double[] eig) {
        int n = A.numRows, m = A.numCols;
        for (int i = 0; i < n; i++) {
            double s = 1.0 / Math.sqrt(eig[i]);
            int rowStart = i * m;
            for (int j = 0; j < m; j++) {
                A.data[rowStart + j] *= s;
            }
        }
    }

    private static void scaleColsInvSqrtInPlace(DMatrixRMaj A, double[] eig) {
        int n = A.numRows, m = A.numCols;
        for (int j = 0; j < m; j++) {
            double s = 1.0 / Math.sqrt(eig[j]);
            for (int i = 0; i < n; i++) {
                A.set(i, j, A.get(i, j) * s);
            }
        }
    }

    // ====== Cache bits =========================================================

    // Thread-safe cache access
    private static RccaEntry cacheGet(RccaKey k) {
        synchronized (RCCA_CACHE) {
            return RCCA_CACHE.get(k);
        }
    }

    private static void cachePut(RccaKey k, RccaEntry v) {
        synchronized (RCCA_CACHE) {
            RCCA_CACHE.put(k, v);
        }
    }

    private static DMatrixRMaj extract(SimpleMatrix S, int[] rows, int[] cols) {
        DMatrixRMaj out = new DMatrixRMaj(rows.length, cols.length);
        var src = S.getDDRM();
        for (int i = 0; i < rows.length; i++) {
            int ri = rows[i];
            for (int j = 0; j < cols.length; j++) {
                out.set(i, j, src.get(ri, cols[j]));
            }
        }
        return out;
    }

    private static void addRidgeInPlace(DMatrixRMaj A, double lam) {
        int n = Math.min(A.numRows, A.numCols);
        for (int i = 0; i < n; i++) {
            A.set(i, i, A.get(i, i) + lam);
        }
    }

    /**
     * Solve L * X = B in-place (overwrite B with X), where L is lower-triangular with non-unit diagonal.
     */
    private static void forwardSolveLowerInPlace(DMatrixRMaj L, DMatrixRMaj B) {
        int n = L.numRows;
        int m = B.numCols;
        for (int col = 0; col < m; col++) {
            for (int i = 0; i < n; i++) {
                double sum = B.get(i, col);
                for (int k = 0; k < i; k++) {
                    sum -= L.get(i, k) * B.get(k, col);
                }
                B.set(i, col, sum / L.get(i, i));
            }
        }
    }

    /**
     * Solve (L^T) * X = B in-place (overwrite B with X), where L is lower-triangular. This is a backward substitution
     * using the implicit upper-triangular U = L^T.
     */
    private static void backwardSolveUpperFromLowerTransposeInPlace(DMatrixRMaj L, DMatrixRMaj B) {
        int n = L.numRows;   // also L.numCols
        int m = B.numCols;
        for (int col = 0; col < m; col++) {
            for (int i = n - 1; i >= 0; i--) {
                double sum = B.get(i, col);
                // U(i,j) = L(j,i), for j>i
                for (int j = i + 1; j < n; j++) {
                    sum -= L.get(j, i) * B.get(j, col);
                }
                B.set(i, col, sum / L.get(i, i)); // U(i,i)=L(i,i)
            }
        }
    }

    /**
     * Computes inverse square root of a symmetric positive definite matrix via eigendecomposition.
     */
    private static SimpleMatrix invSqrtSymmetric(SimpleMatrix S) {
        SimpleEVD<SimpleMatrix> eig = S.eig();
        int dim = S.getNumRows();
        SimpleMatrix D_inv_sqrt = new SimpleMatrix(dim, dim);
        SimpleMatrix V = new SimpleMatrix(dim, dim);

        for (int i = 0; i < dim; i++) {
            double val = eig.getEigenvalue(i).getReal();
            if (val <= 0) throw new RuntimeException("Non-positive eigenvalue encountered.");
            SimpleMatrix v = eig.getEigenVector(i);
            V.insertIntoThis(0, i, v);
            D_inv_sqrt.set(i, i, 1.0 / Math.sqrt(val));
        }

        return V.mult(D_inv_sqrt).mult(V.transpose());
    }

    public static int estimateRccaRank(SimpleMatrix S, int[] xIndices, int[] yIndices, int n, double alpha,
                                       double regLambda, double condThreshold) {
        for (int i = 0; i < yIndices.length; i++) {
            for (int j = i + 1; j < yIndices.length; j++) {
                if (yIndices[i] == yIndices[j]) {
                    throw new IllegalArgumentException("Duplicate values found in yIndices array");
                }
            }
        }

        int p = xIndices.length;
        int q = yIndices.length;
        int minpq = Math.min(p, q);

        for (int r = 0; r < minpq; r++) {
            double pVal = getRccaPValueRankLE(S, xIndices, yIndices, n, r, regLambda, condThreshold);

            if (pVal > alpha) {
                return r; // First non-rejected rank
            }
        }

        return minpq; // All tests rejected, full rank assumed
    }

    private static final class EigenSym {
        final DMatrixRMaj Q;     // orthonormal eigenvectors (columns)
        final double[] lambda;   // eigenvalues (sorted descending, floored)

        EigenSym(DMatrixRMaj Q, double[] lambda) {
            this.Q = Q;
            this.lambda = lambda;
        }
    }

    private static final class SvdResult {
        final double[] svals;

        SvdResult(double[] s) {
            this.svals = s;
        }
    }

    private static final class RccaKey {
        final int[] x, y;
        final long regBits; // quantized reg to avoid fp equality headaches

        RccaKey(int[] xIdx, int[] yIdx, double regLambda) {
            this.x = xIdx.clone();
            Arrays.sort(this.x);
            this.y = yIdx.clone();
            Arrays.sort(this.y);
            // quantize regLambda to ~1e-12 resolution
            this.regBits = Double.doubleToLongBits(Math.rint(regLambda * 1e12) / 1e12);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof RccaKey k)) return false;
            return regBits == k.regBits && Arrays.equals(x, k.x) && Arrays.equals(y, k.y);
        }

        @Override
        public int hashCode() {
            int h = Long.hashCode(regBits);
            h = 31 * h + Arrays.hashCode(x);
            h = 31 * h + Arrays.hashCode(y);
            return h;
        }
    }

    private static final class RccaEntry {
        final double[] svals;       // descending
        final double[] suffixLogs;  // suffixLogs[i] = sum_{j=i}^{end} log(1 - s_j^2)

        RccaEntry(double[] svals, double[] suffixLogs) {
            this.svals = svals;
            this.suffixLogs = suffixLogs;
        }
    }
}