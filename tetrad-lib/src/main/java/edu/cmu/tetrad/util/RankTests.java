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

import java.util.*;

/**
 * The RankTests class provides a suite of methods and utilities for performing rank estimation and hypothesis testing
 * in Canonical Correlation Analysis (CCA) and Regularized Canonical Correlation Analysis (RCCA). This includes
 * computation of p-values, matrix operations, singular value decomposition, and rank estimation with various methods
 * and regularization approaches.
 * <p>
 * The class also incorporates caching mechanisms for efficiency and includes mathematical utilities that are
 * foundational to the CCA and RCCA computations.
 */
public class RankTests {
    /**
     * The maximum number of entries allowed in the RCCA cache. This is used to control memory usage and performance for
     * caching results during Canonical Correlation Analysis (CCA) computations.
     */
    private static final int RCCA_CACHE_MAX = 10_000; // tune if needed
    /**
     * A static, thread-safe cache used to store and manage entries of Canonical Correlation Analysis (CCA) results,
     * mapped by uniquely identified keys. The cache is implemented as a linked hash map with a size-sensitive eviction
     * policy, where the least recently accessed entry is removed when the cache size exceeds the predefined maximum
     * limit.
     * <p>
     * Key characteristics: - Uses {@link RccaKey} objects as keys, which uniquely identify CCA computation
     * configurations. - Stores {@link RccaEntry} objects as values, which contain computed results for corresponding
     * keys. - Maintains an access-order to enable efficient eviction of the least recently used entries. - The maximum
     * size of the cache is determined by the {@code RCCA_CACHE_MAX} constant.
     * <p>
     * Eviction behavior: When a new entry is added such that the size of the cache exceeds {@code RCCA_CACHE_MAX}, the
     * eldest entry in the cache is removed automatically to maintain the maximum size constraint.
     */
    private static final Map<RccaKey, RccaEntry> RCCA_CACHE =
            new LinkedHashMap<>(1024, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<RccaKey, RccaEntry> e) {
                    return size() > RCCA_CACHE_MAX;
                }
            };
    /**
     * ---- Eigen whitening path (from a previous message), packaged to return svals
     */
    private static final double EIG_FLOOR = 1e-12;
    /**
     * A small constant value added as a ridge term during regularization to improve numerical stability. This helps
     * prevent issues such as singular matrices or poor conditioning in mathematical computations.
     */
    private static final double RIDGE = 1e-10;
    /**
     * A constant representing the minimum allowable eigenvalue threshold for numerical computations. It is used to
     * prevent operations like matrix inversion or decomposition on matrices with eigenvalues smaller than this
     * threshold, which could lead to numerical instability or inaccuracies.
     */
    private static final double MIN_EIG = 1e-12;

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
     * Computes the p-value for regularized Canonical Correlation Analysis (RCCA) rank test based on the null hypothesis
     * H0: canonical correlation rank ≤ r versus the alternative hypothesis H1: rank > r. The method applies a
     * chi-squared test using log-likelihood ratios for the remaining canonical correlations.
     *
     * @param S             The input covariance matrix as a SimpleMatrix.
     * @param xIdx          Indices representing the first group of variables.
     * @param yIdx          Indices representing the second group of variables.
     * @param n             The sample size.
     * @param rank          The hypothesized maximum rank under the null hypothesis.
     * @param regLambda     Regularization parameter (ridge) applied to the covariance matrix.
     * @param condThreshold Threshold for matrix conditioning to determine numerical stability.
     * @return The p-value associated with the chi-squared test. Returns 1.0 when rank is not valid or the degrees of
     * freedom are non-positive, and 0.0 in case of an exception or invalid computation.
     */
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

    /**
     * ---- Public: compute singular values via hybrid whitening (Cholesky -> Eigen fallback)
     */
    private static SvdResult computeSvalsHybrid(SimpleMatrix S,
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

    /**
     * Computes the squared condition number of the diagonal of a Cholesky factor matrix. This method evaluates the
     * ratio of the maximum to the minimum diagonal elements of a lower triangular Cholesky factor matrix and returns
     * its square as the condition number. If any diagonal element is non-positive or non-finite, it returns positive
     * infinity.
     *
     * @param L The lower triangular Cholesky factor matrix represented as a DMatrixRMaj. The matrix's diagonal must
     *          contain positive and finite entries.
     * @return The squared condition number defined as (max diagonal / min diagonal)^2, or Double.POSITIVE_INFINITY if
     * the input is invalid.
     */
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

    /**
     * Computes the singular values using eigenvalue-based whitening for the given sub-matrices of a covariance matrix.
     * This method extracts sub-matrices corresponding to the given indices, applies regularization, performs eigenvalue
     * decomposition, and calculates a transformation matrix for singular value decomposition.
     *
     * @param S    The input covariance matrix as a SimpleMatrix.
     * @param xIdx Indices representing the first group of variables.
     * @param yIdx Indices representing the second group of variables.
     * @param reg  Regularization parameter (ridge) to ensure numerical stability.
     * @return An SvdResult object containing the singular values, or null if decomposition fails.
     */
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

    /**
     * Performs symmetric eigenvalue decomposition on the input matrix and returns an {@code EigenSym} object
     * encapsulating the eigenvalues and eigenvectors. Assumes the input matrix is symmetric. If decomposition fails or
     * an unexpected condition is encountered, a runtime exception is thrown.
     *
     * @param A the input symmetric square matrix to decompose. Must not be null and should have valid dimensions.
     * @return an {@code EigenSym} object containing eigenvalues (sorted in descending order) and corresponding
     * eigenvectors.
     * @throws RuntimeException if the eigen decomposition fails or unexpected conditions such as null eigenvectors
     *                          occur.
     */
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

    /**
     * Returns the indices that would sort the input array in descending order. The sorting is performed indirectly,
     * without modifying the input array itself.
     *
     * @param a the input array of doubles to be sorted.
     * @return an array of integers representing the indices of the elements in the input array, ordered such that the
     * values at those indices are sorted in descending order.
     */
    private static int[] argsortDesc(double[] a) {
        Integer[] idx = new Integer[a.length];
        for (int i = 0; i < a.length; i++) idx[i] = i;
        java.util.Arrays.sort(idx, (i, j) -> Double.compare(a[j], a[i]));
        int[] out = new int[a.length];
        for (int i = 0; i < a.length; i++) out[i] = idx[i];
        return out;
    }

    /**
     * ====== Cache bits =========================================================
     */
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

    /**
     * Scales the columns of the matrix in place using the inverse square root of the provided eigenvalues. Each column
     * of the matrix is multiplied by the inverse square root of the corresponding eigenvalue.
     *
     * @param A   The matrix whose columns will be scaled. The modifications are performed in place.
     * @param eig An array of eigenvalues used for scaling. Must be non-null and of length equal to the number of
     *            columns in the matrix.
     */
    private static void scaleColsInvSqrtInPlace(DMatrixRMaj A, double[] eig) {
        int n = A.numRows, m = A.numCols;
        for (int j = 0; j < m; j++) {
            double s = 1.0 / Math.sqrt(eig[j]);
            for (int i = 0; i < n; i++) {
                A.set(i, j, A.get(i, j) * s);
            }
        }
    }

    /**
     * Thread-safe cache access
     */
    private static RccaEntry cacheGet(RccaKey k) {
        synchronized (RCCA_CACHE) {
            return RCCA_CACHE.get(k);
        }
    }

    /**
     * Adds a key-value pair to the RCCA_CACHE in a thread-safe manner.
     *
     * @param k the key to be added to the cache
     * @param v the value associated with the key to be added to the cache
     */
    private static void cachePut(RccaKey k, RccaEntry v) {
        synchronized (RCCA_CACHE) {
            RCCA_CACHE.put(k, v);
        }
    }

    /**
     * Extracts a submatrix from the specified rows and columns of the input matrix.
     *
     * @param S    the input matrix from which the submatrix will be extracted
     * @param rows an array of row indices specifying which rows to include in the submatrix
     * @param cols an array of column indices specifying which columns to include in the submatrix
     * @return a new {@code DMatrixRMaj} containing the elements specified by the rows and columns
     */
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

    /**
     * Adds a ridge (scalar value) to the diagonal elements of the given matrix in-place. This operation modifies the
     * input matrix by adding the specified value to its diagonal entries.
     *
     * @param A   The matrix to be modified in-place. It must not be null.
     * @param lam The scalar value to add to the diagonal elements of the matrix.
     */
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

    /**
     * Estimates the regularized canonical correlation analysis (rCCA) rank by sequentially testing the rank using
     * Wilks' Lambda statistic.
     *
     * @param Scond     A matrix representing the conditioned covariance or correlation structure of the input data.
     * @param xIdxLocal An array of indices corresponding to the local x-variables involved in the calculation.
     * @param yIdxLocal An array of indices corresponding to the local y-variables involved in the calculation.
     * @param n         The total number of observations in the dataset.
     * @param alpha     The significance level for the rank testing, typically between 0 and 1.
     * @return The estimated rank for the rCCA, which is the number of canonical correlations deemed statistically
     * significant, constrained by the dimensions of the input data.
     */
    public static int estimateRccaRank(SimpleMatrix Scond,
                                       int[] xIdxLocal, int[] yIdxLocal,
                                       int n, double alpha) {
        for (int r = 0; r < yIdxLocal.length; r++) {
            if (acceptRankLeByWilks(Scond, xIdxLocal, yIdxLocal, n, r, alpha)) {
                return r;
            }
        }

        return Math.min(xIdxLocal.length, yIdxLocal.length);
    }

    /**
     * Determines whether the rank is less than or equal to a specified value r using a Wilks' lambda test. This method
     * performs hypothesis testing on the rank condition of a block matrix.
     *
     * @param Scond The conditioned covariance matrix or a similar input matrix.
     * @param xLoc  An array of integers representing the indices of the x-block variables.
     * @param yLoc  An array of integers representing the indices of the y-block variables.
     * @param n     The number of observations or sample size.
     * @param r     The rank condition to test (non-negative integer).
     * @param alpha The significance level for the statistical test, between 0 and 1.
     * @return true if the hypothesis that the rank is less than or equal to r is accepted, false otherwise.
     */
    private static boolean acceptRankLeByWilks(
            SimpleMatrix Scond, int[] xLoc, int[] yLoc, int n, int r, double alpha) {

        // Blocks
        SimpleMatrix Sxx = block(Scond, xLoc, xLoc);
        SimpleMatrix Syy = block(Scond, yLoc, yLoc);
        SimpleMatrix Sxy = block(Scond, xLoc, yLoc);

        int p = Sxx.getNumRows(), q = Syy.getNumRows();
        int minpq = Math.min(p, q);
        if (r < 0 || r >= minpq) return false; // invalid r

        // Whitening with PSD inverse sqrt (ridge inside)
        SimpleMatrix Wxx = invSqrtPSD(Sxx);
        SimpleMatrix Wyy = invSqrtPSD(Syy);

        // Canonical correlations are singular values of Wxx * Sxy * Wyy
        SimpleSVD<SimpleMatrix> svd = Wxx.mult(Sxy).mult(Wyy).svd();

        double[] s = new double[minpq];
        for (int i = 0; i < minpq; i++) {
            s[i] = svd.getSingleValue(i);
        }

//        double[] s = svd.getSingularValues();

        // Defensive clamp + ensure we only use the first minpq values
        int k = Math.min(minpq, s.length);
        double sumLog = 0.0; // log Λ = Σ log(1 - ρ_i^2) over i = r..k-1
        for (int i = r; i < k; i++) {
            double rho = Math.max(0.0, Math.min(1.0, s[i]));
            double oneMinus = Math.max(1e-16, 1.0 - rho * rho);
            sumLog += Math.log(oneMinus);
        }

        // Bartlett’s approx: -c * log Λ  ~  χ²_df
        double c = (n - 1) - 0.5 * (p + q + 1);
        if (c < 1) c = 1; // pragmatic floor; alternatively, treat as inconclusive
        double stat = -c * sumLog;
        int df = (p - r) * (q - r);

        double pval = 1.0 - new org.apache.commons.math3.distribution.ChiSquaredDistribution(df)
                .cumulativeProbability(stat);

        // Accept H0: rank ≤ r  iff pval > alpha.
        return pval > alpha;
    }

    /**
     * Extract block S[rows, cols]
     */
    private static SimpleMatrix block(SimpleMatrix S, int[] rows, int[] cols) {
        SimpleMatrix out = new SimpleMatrix(rows.length, cols.length);
        for (int i = 0; i < rows.length; i++) {
            int ri = rows[i];
            for (int j = 0; j < cols.length; j++) {
                out.set(i, j, S.get(ri, cols[j]));
            }
        }
        return out;
    }

    /**
     * Symmetric PSD inverse square root with eigen floor + ridge
     */
    private static SimpleMatrix invSqrtPSD(SimpleMatrix A) {
        SimpleMatrix Asym = A.plus(A.transpose()).divide(2.0); // symmetrize
        // small ridge to avoid negative/zero eigs
        int n = Asym.getNumRows();
        SimpleMatrix Areg = Asym.copy();
        for (int i = 0; i < n; i++) {
            Areg.set(i, i, Areg.get(i, i) + RIDGE);
        }
        SimpleEVD<SimpleMatrix> evd = Areg.eig();
        SimpleMatrix V = new SimpleMatrix(n, n);
        SimpleMatrix DinvSqrt = new SimpleMatrix(n, n);
        for (int i = 0; i < n; i++) {
            double eig = Math.max(evd.getEigenvalue(i).getReal(), MIN_EIG);
            double invs = 1.0 / Math.sqrt(eig);
            DinvSqrt.set(i, i, invs);
            // eigenvectors are columns of V
            SimpleMatrix vi = evd.getEigenVector(i);
            for (int r = 0; r < n; r++) {
                assert vi != null;
                V.set(r, i, vi.get(r, 0));
            }
        }
        // V * D^{-1/2} * V^T
        return V.mult(DinvSqrt).mult(V.transpose());
    }

    /**
     * Build a Scond over [X | Y] that is *conditioned on* Z, then call your estimator.
     */
    public static int estimateRccaRankConditioned(
            SimpleMatrix S, int[] C, int[] VminusC, int[] Z,
            int n, double alpha) {

        int[] X = diff(C, Z);
        int[] Y = diff(VminusC, Z);
        if (X.length == 0 || Y.length == 0) return 0;           // nothing left to test
        if (Z.length == 0) return estimateRccaRank(S, X, Y, n, alpha);

        // Extract blocks
        SimpleMatrix Sxx = block(S, X, X);
        SimpleMatrix Syy = block(S, Y, Y);
        SimpleMatrix Sxy = block(S, X, Y);
        SimpleMatrix Sxz = block(S, X, Z);
        SimpleMatrix Syz = block(S, Y, Z);
        SimpleMatrix Szz = block(S, Z, Z);

        // Invert Szz robustly (use your ridge/floor)
        SimpleMatrix SzzInv = invPsdWithRidge(Szz, /*ridge*/1e-8);

        // Schur complements (condition on Z)
        SimpleMatrix Sxx_c = Sxx.minus(Sxz.mult(SzzInv).mult(Sxz.transpose()));
        SimpleMatrix Syy_c = Syy.minus(Syz.mult(SzzInv).mult(Syz.transpose()));
        SimpleMatrix Sxy_c = Sxy.minus(Sxz.mult(SzzInv).mult(Syz.transpose()));

        // Reassemble a (|X|+|Y|)×(|X|+|Y|) covariance conditioned on Z:
        int p = X.length, q = Y.length;
        SimpleMatrix Scond = new SimpleMatrix(p + q, p + q);
        Scond.insertIntoThis(0, 0, Sxx_c);
        Scond.insertIntoThis(0, p, Sxy_c);
        Scond.insertIntoThis(p, 0, Sxy_c.transpose());
        Scond.insertIntoThis(p, p, Syy_c);

        // Now reuse your existing estimator on [X | Y] with Scond
        int[] xLoc = range(0, p);
        int[] yLoc = range(p, p + q);
        return estimateRccaRank(Scond, xLoc, yLoc, n, alpha);
    }

    /**
     * Helpers you likely already have; sketched for completeness.
     */
    private static int[] range(int a, int b) {
        int[] result = new int[b - a];
        for (int i = 0; i < b - a; i++) {
            result[i] = a + i;
        }
        return result;
    }

    /**
     * Computes the pseudo-inverse of a positive semi-definite matrix with an added ridge value on its diagonal for
     * regularization. This is useful for stabilizing the inversion of matrices that are ill-conditioned or nearly
     * singular.
     *
     * @param Szz   the positive semi-definite matrix to be inverted
     * @param ridge the ridge value to be added to the diagonal of the matrix
     * @return the pseudo-inverse of the regularized matrix
     */
    private static SimpleMatrix invPsdWithRidge(SimpleMatrix Szz, double ridge) {
        SimpleMatrix A = Szz.copy();
        for (int i = 0; i < A.getNumRows(); i++) A.set(i, i, A.get(i, i) + ridge);
        return A.pseudoInverse();
    }

    /**
     * Computes the difference between two arrays, returning an array of elements that are present in the first array
     * but not in the second.
     *
     * @param A the first array of integers
     * @param B the second array of integers
     * @return an array of integers containing elements from the first array that are not present in the second array
     */
    public static int[] diff(int[] A, int[] B) {
        Set<Integer> setB = new HashSet<>();
        for (int b : B) setB.add(b);
        List<Integer> result = new ArrayList<>();
        for (int a : A) {
            if (!setB.contains(a)) {
                result.add(a);
            }
        }
        return result.stream().mapToInt(x -> x).toArray();
    }

    /**
     * Computes the union of two integer arrays and returns the result as an array.
     *
     * @param A the first array of integers
     * @param B the second array of integers
     * @return an array containing the union of the elements from both input arrays
     */
    public static int[] union(int[] A, int[] B) {
        Set<Integer> _A = new HashSet<>();
        Set<Integer> _B = new HashSet<>();
        for (int j : A) _A.add(j);
        for (int j : B) _B.add(j);
        Set<Integer> union = new HashSet<>();
        union.addAll(_A);
        union.addAll(_B);
        return union.stream().mapToInt(x -> x).toArray();
    }

    /**
     * Computes the union of a list of integers and a single integer. The union operation adds the integer to the set of
     * elements in the list, ensuring no duplicates.
     *
     * @param A the list of integers to be included in the union
     * @param b the integer to be added to the union
     * @return an array representing the union of the input list and the single integer
     */
    public static int[] union(List<Integer> A, int b) {
        Set<Integer> _A = new HashSet<>(A);
        Set<Integer> union = new HashSet<>(_A);
        union.add(b);
        return union.stream().mapToInt(x -> x).toArray();
    }

    /**
     * Converts a List of Integer objects into an array of primitive int values.
     *
     * @param Z the List of Integer objects to be converted into an int array
     * @return an array of int containing the values from the input List in the same order
     */
    public static int[] toArray(List<Integer> Z) {
        return Z.stream().mapToInt(x -> x).toArray();
    }

    /**
     * Computes the union of the elements from the given array and a single integer value. The union is returned as an
     * array of unique integers.
     *
     * @param A an array of integers whose elements will contribute to the union set
     * @param b a single integer that will also be included in the union set
     * @return an array of integers containing the union of the input array and the single integer, with all duplicate
     * elements removed
     */
    public int[] union(int[] A, int b) {
        Set<Integer> _A = new HashSet<>();
        Set<Integer> _B = new HashSet<>();
        for (int j : A) _A.add(j);
        _B.add(b);
        Set<Integer> union = new HashSet<>();
        union.addAll(_A);
        union.addAll(_B);
        return union.stream().mapToInt(x -> x).toArray();
    }

    /**
     * A private static final class representing the result of eigenvalue decomposition of a symmetric matrix. This
     * includes an orthonormal matrix of eigenvectors and a sorted array of eigenvalues.
     * <p>
     * The eigenvalues are floored and sorted in descending order. The orthonormal matrix (Q) contains the corresponding
     * eigenvectors as its columns.
     */
    private static final class EigenSym {
        final DMatrixRMaj Q;     // orthonormal eigenvectors (columns)
        final double[] lambda;   // eigenvalues (sorted descending, floored)

        EigenSym(DMatrixRMaj Q, double[] lambda) {
            this.Q = Q;
            this.lambda = lambda;
        }
    }

    /**
     * A private static final class representing the result of a Singular Value Decomposition (SVD). This class
     * encapsulates the singular values obtained from the decomposition.
     */
    private static final class SvdResult {
        final double[] svals;

        SvdResult(double[] s) {
            this.svals = s;
        }
    }

    /**
     * A helper class used to encapsulate and uniquely identify specific configurations defined by two integer arrays
     * and a regularization factor. This class is immutable and provides methods for equality checks and hash code
     * generation.
     * <p>
     * The class is used for handling configurations where two sets of indices and a quantized regularization value are
     * required to determine equality and uniqueness.
     */
    private static final class RccaKey {
        final int[] x, y;
        final long regBits; // quantized reg to avoid fp equality headaches

        RccaKey(int[] xIdx, int[] yIdx, double regLambda) {
            this.x = xIdx.clone();
            Arrays.sort(this.x);
            this.y = yIdx.clone();
            Arrays.sort(this.y);
            // quantize regLambda to ~1e-12 resolution
            this.regBits = Double.doubleToLongBits(Math.rint(regLambda * 1e20) / 1e20);
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

    /**
     * Represents an entry in the RCCA (Regularized Canonical Correlation Analysis) data structure. The entry contains
     * singular values in descending order and precomputed logarithmic suffix sums.
     * <p>
     * This class is private and static, designed to be utilized internally within its enclosing class.
     * <p>
     * Attributes: - svals: An array of singular values sorted in descending order. - suffixLogs: An array where each
     * element at index `i` represents the sum of logarithms of (1 - squared singular value) from index `i` to the end
     * of the `svals` array.
     */
    private static final class RccaEntry {
        final double[] svals;       // descending
        final double[] suffixLogs;  // suffixLogs[i] = sum_{j=i}^{end} log(1 - s_j^2)

        RccaEntry(double[] svals, double[] suffixLogs) {
            this.svals = svals;
            this.suffixLogs = suffixLogs;
        }
    }
}