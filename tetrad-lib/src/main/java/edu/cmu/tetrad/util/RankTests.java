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
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.util;

import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.CommonOps_DDRM;
import org.ejml.dense.row.factory.DecompositionFactory_DDRM;
import org.ejml.interfaces.decomposition.EigenDecomposition_F64;
import org.ejml.interfaces.decomposition.SingularValueDecomposition_F64;
import org.ejml.simple.SimpleEVD;
import org.ejml.simple.SimpleMatrix;
import org.ejml.simple.SimpleSVD;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static edu.cmu.tetrad.util.StatUtils.erf;

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
     * A constant representing the minimum allowable eigenvalue threshold for numerical computations. It is used to
     * prevent operations like matrix inversion or decomposition on matrices with eigenvalues smaller than this
     * threshold, which could lead to numerical instability or inaccuracies.
     */
    private static final double MIN_EIG = 1e-12;
    private static final Map<Chi2Key, Double> CHI2_CRIT_CACHE = new ConcurrentHashMap<>();
    /**
     * A small constant value added as a ridge term during regularization to improve numerical stability. This helps
     * prevent issues such as singular matrices or poor conditioning in mathematical computations.
     */
    public static double RIDGE = 1e-6;
    /**
     * The RankTests class provides utility methods for ranking-related evaluations. This class is not meant to be
     * instantiated.
     * <p>
     * The existence of a private constructor ensures that the RankTests class cannot be instantiated or extended. It is
     * designed only to contain static methods related to ranking operations or tests.
     * <p>
     * Attempting to instantiate this class will result in an AssertionError.
     */
    private RankTests() {
        throw new AssertionError("This class is not meant to be instantiated.");
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

        // T = Îx^{-1/2} * (Qx^T Cxy Qy) * Îy^{-1/2}
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
            sorted[j] = TMath.max(vals[idx], EIG_FLOOR); // floor small/negatives
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
            double s = 1.0 / TMath.sqrt(eig[i]);
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
            double s = 1.0 / TMath.sqrt(eig[j]);
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
        int n = TMath.min(A.numRows, A.numCols);
        for (int i = 0; i < n; i++) {
            A.set(i, i, A.get(i, i) + lam);
        }
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
    public static int estimateWilksRank(SimpleMatrix Scond,
                                        int[] xIdxLocal, int[] yIdxLocal,
                                        int n, double alpha) {
        int minpq = TMath.min(xIdxLocal.length, yIdxLocal.length);

        for (int r = 0; r < minpq; r++) {
            if (rankLeByWilks(Scond, xIdxLocal, yIdxLocal, n, r) > alpha) {
                return r;
            }
        }

        return minpq;
    }

    /**
     * Estimates rank using the permutation Wilks test, suitable for linear
     * non-Gaussian data.  Sequentially tests rank &le; 0, 1, 2, ... and
     * returns the first r whose permutation p-value exceeds {@code alpha}.
     *
     * @param data  n-by-d raw data matrix.
     * @param xIdx  column indices for the X block.
     * @param yIdx  column indices for the Y block.
     * @param alpha significance level (e.g. 0.05).
     * @param B     number of permutations per test (e.g. 999).
     * @return      estimated rank.
     */
    public static int estimatePermutationRank(double[][] data,
                                              int[] xIdx, int[] yIdx,
                                              double alpha, int B) {
        int minpq = TMath.min(xIdx.length, yIdx.length);
        for (int r = 0; r < minpq; r++) {
            if (rankLeByPermutation(data, xIdx, yIdx, r, B) > alpha) {
                return r;
            }
        }
        return minpq;
    }

    /**
     * Estimates the rank of a matrix using the Wilks test and a Bartlett ÏÂ² approximation. This method employs an
     * optimization for fast computation.
     *
     * @param S     Covariance or scatter matrix (SimpleMatrix) of size (p + q) x (p + q).
     * @param xIdx  Indices for the x variables, representing the first group of variables.
     * @param yIdx  Indices for the y variables, representing the second group of variables.
     * @param n     Sample size used for the computation and statistical testing.
     * @param alpha Significance level for hypothesis testing (e.g., 0.05 for 5%).
     * @return Estimated rank of the matrix, computed based on the Wilks test criteria.
     */
    public static int estimateWilksRankFast(SimpleMatrix S, int[] xIdx, int[] yIdx,
                                            int n, double alpha) {
        final int p = xIdx.length, q = yIdx.length;
        if (p == 0 || q == 0) return 0;
        final int m = TMath.min(p, q);

        SimpleMatrix Sxx = submatrix(S, xIdx, xIdx);
        SimpleMatrix Syy = submatrix(S, yIdx, yIdx);
        SimpleMatrix Sxy = submatrix(S, xIdx, yIdx);
        SimpleMatrix Syx = Sxy.transpose();

        double eps = 1e-10;
        for (int i = 0; i < p; i++) Sxx.set(i, i, Sxx.get(i, i) + eps);
        for (int i = 0; i < q; i++) Syy.set(i, i, Syy.get(i, i) + eps);

        SimpleMatrix SxxInv = Sxx.invert();
        SimpleMatrix SyyInv = Syy.invert();

        // Use the smaller-dimension side for the eigen-decomposition.
        SimpleMatrix M = (p <= q)
                ? SxxInv.mult(Sxy).mult(SyyInv).mult(Syx)   // p x p
                : SyyInv.mult(Syx).mult(SxxInv).mult(Sxy);  // q x q

        double[] evals = eigSymmetricClamped(M);
        Arrays.sort(evals);
        // Reverse to descending order.
        for (int i = 0; i < evals.length / 2; i++) {
            double tmp = evals[i];
            evals[i] = evals[evals.length - 1 - i];
            evals[evals.length - 1 - i] = tmp;
        }
        int L = TMath.min(evals.length, m);
        double[] rho2 = Arrays.copyOf(evals, L);

        double c = n - 1.0 - (p + q + 1) / 2.0;

        // Sample too small to discriminate: conservatively report full rank.
        if (c <= 0) return m;

        for (int r = 0; r < m; r++) {
            double sum = 0.0;
            for (int i = r; i < L; i++) {
                sum += TMath.log(TMath.max(1e-15, 1.0 - rho2[i]));
            }
            double stat = -c * sum;
            int nu = (p - r) * (q - r);

            // Zero degrees of freedom: tail is degenerate, accept this rank.
            if (nu <= 0) return r;

            if (stat <= chi2CriticalWH(nu, alpha)) return r;
        }

        // All hypotheses rank <= r rejected: full rank.
        return m;
    }

    // helpers: symmetric eigvals with clamping
    private static double[] eigSymmetricClamped(SimpleMatrix A) {
        org.ejml.simple.SimpleEVD<SimpleMatrix> evd = A.eig();
        int n = A.numCols();
        double[] vals = new double[n];
        for (int i = 0; i < n; i++) {
            double v = evd.getEigenvalue(i).getReal();
            if (Double.isNaN(v) || v < 0) v = 0;
            if (v > 1) v = 1;
            vals[i] = v;
        }
        return vals;
    }

    private static double chi2CriticalWH(int nu, double alpha) {
        // Double.doubleToLongBits gives a collision-free 64-bit representation.
        // computeIfAbsent is safe here since the computation is deterministic;
        // at worst it runs twice under contention and both results are equal.
        return CHI2_CRIT_CACHE.computeIfAbsent(
                new Chi2Key(nu, Double.doubleToLongBits(alpha)),
                k -> {
                    double z = invNormal1mAlpha(alpha);
                    double dn = nu;
                    double a = 2.0 / (9.0 * dn);
                    return dn * TMath.pow(1.0 - a + z * TMath.sqrt(a), 3.0);
                });
    }

    /**
     * Inverse normal for tail 1-alpha: returns z_{1-alpha}.
     */
    private static double invNormal1mAlpha(double alpha) {
        // Moro/AS241 hybrid; good to ~1e-8 and branchless enough.
        // Convert to p in (0,1), then use probit(p).
        double p = 1.0 - alpha;

        // Coefficients for central region
        final double[] a = {-3.969683028665376e+01, 2.209460984245205e+02, -2.759285104469687e+02,
                1.383577518672690e+02, -3.066479806614716e+01, 2.506628277459239e+00};
        final double[] b = {-5.447609879822406e+01, 1.615858368580409e+02, -1.556989798598866e+02,
                6.680131188771972e+01, -1.328068155288572e+01};
        final double[] c = {-7.784894002430293e-03, -3.223964580411365e-01, -2.400758277161838e+00,
                -2.549732539343734e+00, 4.374664141464968e+00, 2.938163982698783e+00};
        final double[] d = {7.784695709041462e-03, 3.224671290700398e-01, 2.445134137142996e+00,
                3.754408661907416e+00};

        // Break-points
        final double plow = 0.02425;
        final double phigh = 1.0 - plow;
        double q, r, x;

        if (p < plow) {
            // lower tail
            q = TMath.sqrt(-2.0 * TMath.log(p));
            x = (((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5]) /
                    ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1.0);
        } else if (p > phigh) {
            // upper tail
            q = TMath.sqrt(-2.0 * TMath.log(1.0 - p));
            x = -(((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5]) /
                    ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1.0);
        } else {
            // central
            q = p - 0.5;
            r = q * q;
            x = (((((a[0] * r + a[1]) * r + a[2]) * r + a[3]) * r + a[4]) * r + a[5]) * q /
                    (((((b[0] * r + b[1]) * r + b[2]) * r + b[3]) * r + b[4]) * r + 1.0);
        }

        // One Newton step for polish
        // Î¦(x) via erf
        double err = 0.5 * (1.0 + erf(x / TMath.sqrt(2.0))) - p;
        double pdf = TMath.exp(-0.5 * x * x) / TMath.sqrt(2.0 * TMath.PI);
        x -= err / pdf;

        return x;
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
     * @return the p-value if the hypothesis that the rank is less than or equal to r is accepted.
     */
    public static double rankLeByWilks(SimpleMatrix Scond, int[] xLoc, int[] yLoc, int n, int r) {
        // Blocks
        SimpleMatrix Sxx = block(Scond, xLoc, xLoc);
        SimpleMatrix Syy = block(Scond, yLoc, yLoc);
        SimpleMatrix Sxy = block(Scond, xLoc, yLoc);

        int p = Sxx.getNumRows(), q = Syy.getNumRows();
        int minpq = TMath.min(p, q);
        if (r < 0 || r >= minpq) {
            throw new IllegalArgumentException("Rank r should be semIm 0 <= r <= minpq.");
        }

        // Whitening with PSD inverse sqrt (ridge inside)
        SimpleMatrix Wxx = invSqrtPSD(Sxx);
        SimpleMatrix Wyy = invSqrtPSD(Syy);

        // Canonical correlations are singular values of Wxx * Sxy * Wyy
        SimpleSVD<SimpleMatrix> svd = Wxx.mult(Sxy).mult(Wyy).svd();

        double[] s = new double[minpq];
        for (int i = 0; i < minpq; i++) {
            s[i] = svd.getSingleValue(i);
        }

        // Defensive clamp + ensure we only use the first minpq values
        double sumLog = 0.0; // log Î = Î£ log(1 - Ï_i^2) over i = r..k-1
        for (int i = r; i < minpq; i++) {
            double rho = TMath.max(0.0, TMath.min(1.0, s[i]));
            double oneMinus = TMath.max(1e-16, 1.0 - rho * rho);
            sumLog += TMath.log(oneMinus);
        }

        // Bartlettâs approx: -c * log Î  ~  ÏÂ²_df
        double c = (n - 1) - 0.5 * (p + q + 1);
//        if (c < 1) c = 1; // pragmatic floor; alternatively, treat as inconclusive
        double stat = -c * sumLog;
        int df = (p - r) * (q - r);

        return 1.0 - new ChiSquaredDistribution(df)
                .cumulativeProbability(stat);
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
            double eig = TMath.max(evd.getEigenvalue(i).getReal(), MIN_EIG);
            double invs = 1.0 / TMath.sqrt(eig);
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

    private static Whitening invSqrtPSD_rankAware(SimpleMatrix A, double relTol) {
        SimpleMatrix Asym = A.plus(A.transpose()).divide(2.0);
        int n = Asym.getNumRows();

        // ridge
        SimpleMatrix Areg = Asym.copy();
        for (int i = 0; i < n; i++) Areg.set(i, i, Areg.get(i, i) + RIDGE);

        var evd = Areg.eig();

        // collect eigenpairs (real parts)
        double[] d = new double[n];
        SimpleMatrix[] vec = new SimpleMatrix[n];
        double dmax = 0.0;
        for (int i = 0; i < n; i++) {
            d[i] = evd.getEigenvalue(i).getReal();
            vec[i] = evd.getEigenVector(i);
            if (Double.isFinite(d[i])) dmax = TMath.max(dmax, d[i]);
        }
        double tol = TMath.max(MIN_EIG, relTol * dmax);

        // build U_kept and D^{-1/2}_kept
        int r = 0;
        for (int i = 0; i < n; i++) if (d[i] > tol) r++;

        if (r == 0) {
            // fall back: treat as rank-0
            return new Whitening(new SimpleMatrix(0, n), 0);
        }

        SimpleMatrix U = new SimpleMatrix(n, r);
        SimpleMatrix Dinv = new SimpleMatrix(r, r);

        int k = 0;
        for (int i = 0; i < n; i++) {
            if (d[i] > tol) {
                double invs = 1.0 / TMath.sqrt(d[i]);
                Dinv.set(k, k, invs);
                SimpleMatrix vi = vec[i];
                for (int row = 0; row < n; row++) U.set(row, k, vi.get(row, 0));
                k++;
            }
        }

        // W = D^{-1/2} U^T  (r x n)
        SimpleMatrix W = Dinv.mult(U.transpose());
        return new Whitening(W, r);
    }

    /**
     * Estimates the Wilks rank for variables X and Y conditioned on variables Z using the given covariance matrix and
     * parameters.
     *
     * @param S       the covariance matrix representing the relationships between all variables
     * @param C       an array of indices representing the variables in set C
     * @param VminusC an array of indices representing the variables outside of set C
     * @param Z       an array of indices representing the variables in set Z on which to condition
     * @param n       the sample size used to calculate the covariance matrix S
     * @param alpha   the significance level for testing
     * @return the estimated Wilks rank for the variables in X and Y conditioned on Z
     */
    public static int estimateWilksRankConditioned(
            SimpleMatrix S, int[] C, int[] VminusC, int[] Z,
            int n, double alpha) {

        int[] X = diff(C, Z);
        int[] Y = diff(VminusC, Z);

        if (X.length == 0 || Y.length == 0) return 0;
        if (Z.length == 0) return estimateWilksRank(S, X, Y, n, alpha);

        SimpleMatrix Sxx = block(S, X, X);
        SimpleMatrix Syy = block(S, Y, Y);
        SimpleMatrix Sxy = block(S, X, Y);
        SimpleMatrix Sxz = block(S, X, Z);
        SimpleMatrix Syz = block(S, Y, Z);
        SimpleMatrix Szz = block(S, Z, Z);

        SimpleMatrix SzzInv = invPsdWithRidge(Szz, 1e-8);

        // Schur complements: partial covariances conditioned on Z.
        SimpleMatrix Sxx_c = Sxx.minus(Sxz.mult(SzzInv).mult(Sxz.transpose()));
        SimpleMatrix Syy_c = Syy.minus(Syz.mult(SzzInv).mult(Syz.transpose()));
        SimpleMatrix Sxy_c = Sxy.minus(Sxz.mult(SzzInv).mult(Syz.transpose()));

        // Symmetrize and stabilize the conditioned diagonal blocks.
        // These operations must be applied to the Schur complement results,
        // not the original blocks — the original blocks are no longer used
        // after this point.
        Sxx_c = Sxx_c.plus(Sxx_c.transpose()).divide(2.0);
        Syy_c = Syy_c.plus(Syy_c.transpose()).divide(2.0);

        double ridge = 1e-6;
        for (int i = 0; i < Sxx_c.numRows(); i++) {
            Sxx_c.set(i, i, Sxx_c.get(i, i) + ridge);
        }
        for (int i = 0; i < Syy_c.numRows(); i++) {
            Syy_c.set(i, i, Syy_c.get(i, i) + ridge);
        }

        int p = X.length, q = Y.length;
        SimpleMatrix Scond = new SimpleMatrix(p + q, p + q);
        Scond.insertIntoThis(0, 0, Sxx_c);
        Scond.insertIntoThis(0, p, Sxy_c);
        Scond.insertIntoThis(p, 0, Sxy_c.transpose());
        Scond.insertIntoThis(p, p, Syy_c);

        int[] xLoc = range(0, p);
        int[] yLoc = range(p, p + q);
        return estimateWilksRank(Scond, xLoc, yLoc, n - Z.length, alpha);
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
     * p-value for H0: rank(X â Y | Z) â¤ 0 using Wilks/Bartlett on partial CCA.
     *
     * @param S The covariance matrix of all variables.
     * @param X An array of indices representing the first subset of variables.
     * @param Y An array of indices representing the second subset of variables.
     * @param Z An array of indices representing the conditioning set of variables.
     * @param n The number of samples used in calculating the covariance matrix.
     * @return The p-value representing the probability of observing the computed test statistic under the null
     * hypothesis of conditional independence. Returns 1.0 if the size of X or Y is zero after exclusion of Z, or if
     * degrees of freedom (df) are less than or equal to zero.
     */
    public static double pValueIndepConditioned(SimpleMatrix S, int[] X, int[] Y, int[] Z, int n) {
        // Remove overlap with Z (same convention as your estimator)
        int[] X0 = diff(X, Z);
        int[] Y0 = diff(Y, Z);
        if (X0.length == 0 || Y0.length == 0) return 1.0;

        // Blocks + Schur complement (partial covariances)
        SimpleMatrix Sxx = block(S, X0, X0);
        SimpleMatrix Syy = block(S, Y0, Y0);
        SimpleMatrix Sxy = block(S, X0, Y0);

        if (Z.length > 0) {
            SimpleMatrix Sxz = block(S, X0, Z);
            SimpleMatrix Syz = block(S, Y0, Z);
            SimpleMatrix Szz = block(S, Z, Z);
            SimpleMatrix SzzInv = invPsdWithRidge(Szz, 1e-8);
            Sxx = Sxx.minus(Sxz.mult(SzzInv).mult(Sxz.transpose()));
            Syy = Syy.minus(Syz.mult(SzzInv).mult(Syz.transpose()));
            Sxy = Sxy.minus(Sxz.mult(SzzInv).mult(Syz.transpose()));
        }

        Sxx = Sxx.plus(Sxx.transpose()).divide(2.0);
        Syy = Syy.plus(Syy.transpose()).divide(2.0);

        Whitening Wx = invSqrtPSD_rankAware(Sxx, 1e-10);
        Whitening Wy = invSqrtPSD_rankAware(Syy, 1e-10);
        if (Wx.rank == 0 || Wy.rank == 0) return 1.0;

        // M is (rX x rY)
        SimpleMatrix M = Wx.W.mult(Sxy).mult(Wy.W.transpose());
        var svd = M.svd();
        int m = TMath.min(M.getNumRows(), M.getNumCols());

        double logLambda = 0.0;
        for (int i = 0; i < m; i++) {
            double rho = TMath.max(0.0, TMath.min(1.0, svd.getSingleValue(i)));
            logLambda += TMath.log(TMath.max(1e-16, 1.0 - rho * rho));
        }

        int pEff = Wx.rank, qEff = Wy.rank;
        int df = pEff * qEff;
        if (df <= 0) return 1.0;

        double kappa = (n - 1) - 0.5 * (pEff + qEff + 1);
        if (!Double.isFinite(kappa) || kappa < 1.0) kappa = TMath.max(1.0, n - 1);

        double stat = -kappa * logLambda;
        return 1.0 - new ChiSquaredDistribution(df).cumulativeProbability(stat);
    }

    /**
     * Retrieves or computes an RCCA (Regularized Canonical Correlation Analysis) entry for the given parameters. If the
     * entry is cached, it retrieves the result from the cache. Otherwise, it computes the result based on the provided
     * inputs.
     *
     * @param S         a SimpleMatrix representing the data matrix
     * @param xIdx      an array of indices corresponding to the X variables
     * @param yIdx      an array of indices corresponding to the Y variables
     * @param regLambda a regularization parameter value
     * @return an RccaEntry containing canonical correlation results including singular values and suffix logs for the
     * given inputs, or null if the computation fails
     */
    public static RccaEntry getRccaEntry(SimpleMatrix S,
                                         int[] xIdx, int[] yIdx,
                                         double regLambda) {
        RccaKey key = new RccaKey(xIdx, yIdx, regLambda);
        RccaEntry entry = cacheGet(key);
        if (entry != null) return entry;

        // compute via your existing hybrid path
        SvdResult sv = computeSvalsEigenWhiten(S, xIdx, yIdx, regLambda);
        if (sv == null) return null;

        // build suffix logs once (sum_{j=i}^{end} log(1 - s_j^2))
        int m = TMath.min(xIdx.length, yIdx.length);
        double[] svals = Arrays.copyOf(sv.svals, m);
        double[] suffix = new double[m + 1];
        for (int i = m - 1; i >= 0; i--) {
            double s = TMath.max(0.0, TMath.min(svals[i], 1.0 - 1e-12));
            suffix[i] = suffix[i + 1] + TMath.log(1.0 - s * s);
        }

        entry = new RccaEntry(svals, suffix);
        cachePut(key, entry);
        return entry;
    }

    /**
     * RCCA entry for (C, D) after partialing out Z: S_|Z = S - S_{.,Z} * inv(S_{Z,Z} + ridge*I) * S_{Z,.} Then run RCCA
     * on (C, D) blocks of S_|Z with the same ridge regularization on R_cc and R_dd that getRccaEntry(...) uses.
     *
     * @param S     correlation/covariance over observed variables
     * @param C     left index set
     * @param D     right index set
     * @param Z     conditioning index set
     * @param ridge small diagonal added to R_cc and R_dd (and to S_ZZ before inverting)
     * @return RccaEntry whose suffixLogs has suf[0] == 0 and suf[r] = sum_{i=1..r} log(1 - rho_i^2) in the order of
     * descending canonical correlations
     */
    public static RccaEntry getRccaEntryConditioned(SimpleMatrix S,
                                                    int[] C, int[] D, int[] Z,
                                                    double ridge) {
        if (C == null || D == null || Z == null) return null;
        if (C.length == 0 || D.length == 0) return new RccaEntry(new double[0], new double[]{0.0});

        // If no conditioning, defer to the unconditioned RCCA.
        if (Z.length == 0) {
            return getRccaEntry(S, C, D, ridge);
        }

        final int p = S.getNumCols();
        // --- Build S_|Z via Schur complement: S - S_{.,Z} inv(S_{Z,Z}+ridgeI) S_{Z,.}
        SimpleMatrix S_ZZ = submatrix(S, Z, Z).copy();
        // ridge on S_ZZ for numerical stability
        for (int i = 0; i < S_ZZ.getNumRows(); i++) {
            S_ZZ.set(i, i, S_ZZ.get(i, i) + ridge);
        }
        SimpleMatrix invS_ZZ;
        try {
            invS_ZZ = S_ZZ.invert();
        } catch (Exception e) {
            // Singular even after ridge: fall back to unconditioned RCCA
            return getRccaEntry(S, C, D, ridge);
        }

        SimpleMatrix S_XZ = submatrix(S, all(p), Z);
        SimpleMatrix S_ZX = S_XZ.transpose();
        SimpleMatrix S_cond = S.minus(S_XZ.mult(invS_ZZ).mult(S_ZX));

        // Extract C/D blocks on the conditioned matrix
        SimpleMatrix Rcc = submatrix(S_cond, C, C).copy();
        SimpleMatrix Rdd = submatrix(S_cond, D, D).copy();
        SimpleMatrix Rcd = submatrix(S_cond, C, D).copy();

        // Add ridge to the diagonals of Rcc / Rdd before inversion
        for (int i = 0; i < Rcc.getNumRows(); i++) Rcc.set(i, i, Rcc.get(i, i) + ridge);
        for (int i = 0; i < Rdd.getNumRows(); i++) Rdd.set(i, i, Rdd.get(i, i) + ridge);

        SimpleMatrix invRcc, invRdd;
        try {
            invRcc = Rcc.invert();
            invRdd = Rdd.invert();
        } catch (Exception e) {
            // If still singular, bail out gracefully
            return null;
        }

        // M = inv(Rcc) * Rcd * inv(Rdd) * Rdc ; eigenvalues are canonical rho^2
        SimpleMatrix M = invRcc.mult(Rcd).mult(invRdd).mult(Rcd.transpose());

        // Symmetrize to kill tiny asymmetries
        M = symmetrize(M);

        // Eigen-decomposition
        org.ejml.simple.SimpleEVD<SimpleMatrix> evd;
        try {
            evd = M.eig();
        } catch (Exception e) {
            return null;
        }

        int m = TMath.min(C.length, D.length);
        List<Double> rho2 = new ArrayList<>(m);
        for (int i = 0; i < TMath.min(m, M.getNumRows()); i++) {
            double val = evd.getEigenvalue(i).getReal();
            if (Double.isNaN(val) || Double.isInfinite(val)) continue;
            // clamp to [0,1] for safety
            val = TMath.max(0.0, TMath.min(1.0, val));
            rho2.add(val);
        }

        // ... inside getRccaEntryConditioned

        if (rho2.isEmpty()) {
            return new RccaEntry(new double[0], new double[]{0.0});
        }

        // Sort descending by rho (i.e., descending rho^2)
        rho2.sort(Comparator.reverseOrder());

        // Convert to canonical correlations (not squared)
        double[] svals = new double[rho2.size()];
        for (int i = 0; i < rho2.size(); i++) {
            svals[i] = TMath.sqrt(rho2.get(i));
        }

        // Build suffix logs: suffixLogs[i] = Î£_{j=i}^{end} log(1 - s_j^2)
        double[] suffixLogs = new double[svals.length + 1];
        suffixLogs[svals.length] = 0.0; // last element is 0
        for (int i = svals.length - 1; i >= 0; i--) {
            double oneMinus = TMath.max(1e-16, 1.0 - svals[i] * svals[i]);
            suffixLogs[i] = suffixLogs[i + 1] + TMath.log(oneMinus);
        }

        return new RccaEntry(svals, suffixLogs);
    }

    private static int[] all(int n) {
        int[] a = new int[n];
        for (int i = 0; i < n; i++) a[i] = i;
        return a;
    }

    // ==== Add this to RankTests ====

    private static SimpleMatrix submatrix(SimpleMatrix S, int[] rows, int[] cols) {
        SimpleMatrix out = new SimpleMatrix(rows.length, cols.length);
        for (int i = 0; i < rows.length; i++) {
            for (int j = 0; j < cols.length; j++) {
                out.set(i, j, S.get(rows[i], cols[j]));
            }
        }
        return out;
    }

    /* --------------------- small local helpers (keep private) --------------------- */

    private static SimpleMatrix symmetrize(SimpleMatrix A) {
        return A.plus(A.transpose()).scale(0.5);
    }

    /**
     * Permutation-based test for rank &le; r in the linear non-Gaussian setting.
     *
     * <p>The standard Wilks test uses a chi-square approximation that requires
     * Gaussianity of the data.  When the data are linear but non-Gaussian (e.g.
     * heavy-tailed, skewed, or copula-structured), the Bartlett chi-square null
     * can be badly mis-calibrated.  This method avoids that assumption entirely
     * by estimating the null distribution empirically:
     *
     * <ol>
     *   <li>Compute the observed Wilks statistic {@code T_obs = -c * sum log(1 - rho_i^2)}
     *       for the singular values beyond index r.</li>
     *   <li>For each of {@code B} permutations, shuffle the <em>rows</em> of the
     *       Y-block of the data matrix independently and uniformly at random.
     *       This breaks all X-Y dependence while preserving the marginal
     *       distributions of X and Y (and hence within-block dependence), which
     *       is exactly the null hypothesis rank(C_{XY}) &le; r.</li>
     *   <li>Recompute the Wilks statistic on the permuted covariance and collect
     *       the null distribution.</li>
     *   <li>Return the fraction of permutation statistics &ge; {@code T_obs}
     *       (the one-sided p-value).</li>
     * </ol>
     *
     * <p>The input is the raw data matrix (rows = observations, columns = all
     * variables), together with the x- and y-column index arrays.  The method
     * internally computes the sample covariance so it can re-compute it cheaply
     * on each permutation.
     *
     * @param data   n-by-d data matrix (rows = observations).
     * @param xIdx   column indices for the X block.
     * @param yIdx   column indices for the Y block.
     * @param r      rank threshold to test (null hypothesis: rank &le; r).
     * @param B      number of permutations (e.g. 999 or 1999).
     * @return       permutation p-value for H0: rank(C_XY) &le; r.
     */
    public static double rankLeByPermutation(double[][] data,
                                             int[] xIdx, int[] yIdx,
                                             int r, int B) {
        final int n = data.length;
        final int p = xIdx.length, q = yIdx.length;
        final int minpq = TMath.min(p, q);

        if (r < 0 || r >= minpq) {
            throw new IllegalArgumentException("r must satisfy 0 <= r < min(p,q).");
        }
        if (B < 1) throw new IllegalArgumentException("B must be >= 1.");

        // Extract X and Y sub-matrices (n x p and n x q).
        double[][] X = extractCols(data, xIdx, n, p);
        double[][] Y = extractCols(data, yIdx, n, q);

        // Observed statistic.
        double tObs = wilksStatFromData(X, Y, n, p, q, minpq, r);

        // Permutation loop: shuffle rows of Y, re-compute statistic.
        int[] rowPerm = new int[n];
        for (int i = 0; i < n; i++) rowPerm[i] = i;

        int exceed = 0;
        for (int b = 0; b < B; b++) {
            shuffleInPlace(rowPerm);
            double tPerm = wilksStatPermuted(X, Y, rowPerm, n, p, q, minpq, r);
            if (tPerm >= tObs) exceed++;
        }

        // +1/+1 continuity correction (Phipson & Smyth, 2010).
        return (exceed + 1.0) / (B + 1.0);
    }

    /**
     * Computes the Wilks test statistic for rank-leq-r from raw X and Y blocks.
     */
    private static double wilksStatFromData(double[][] X, double[][] Y,
                                            int n, int p, int q, int minpq, int r) {
        SimpleMatrix Sxx = sampleCov(X, X, n, p, p);
        SimpleMatrix Syy = sampleCov(Y, Y, n, q, q);
        SimpleMatrix Sxy = sampleCov(X, Y, n, p, q);
        return wilksStatFromBlocks(Sxx, Syy, Sxy, n, p, q, minpq, r);
    }

    /**
     * Computes the Wilks statistic after permuting the rows of Y via {@code perm}.
     * Avoids materializing the permuted matrix by passing {@code perm} directly
     * into the covariance accumulation.d
     */
    private static double wilksStatPermuted(double[][] X, double[][] Y,
                                            int[] perm,
                                            int n, int p, int q, int minpq, int r) {
        // Sxx is the same as in the observed case but it's cheap to recompute.
        SimpleMatrix Sxx = sampleCov(X, X, n, p, p);
        SimpleMatrix Syy = sampleCovPermuted(Y, Y, perm, n, q, q);
        SimpleMatrix Sxy = sampleCovPermuted(X, Y, perm, n, p, q);
        return wilksStatFromBlocks(Sxx, Syy, Sxy, n, p, q, minpq, r);
    }

    /**
     * Core statistic: -c * sum_{i=r}^{minpq-1} log(1 - rho_i^2).
     * Mirrors the logic in {@link #rankLeByWilks} exactly.
     */
    private static double wilksStatFromBlocks(SimpleMatrix Sxx, SimpleMatrix Syy,
                                              SimpleMatrix Sxy,
                                              int n, int p, int q, int minpq, int r) {
        SimpleMatrix Wxx = invSqrtPSD(Sxx);
        SimpleMatrix Wyy = invSqrtPSD(Syy);
        SimpleSVD<SimpleMatrix> svd = Wxx.mult(Sxy).mult(Wyy).svd();

        double sumLog = 0.0;
        for (int i = r; i < minpq; i++) {
            double rho = TMath.max(0.0, TMath.min(1.0, svd.getSingleValue(i)));
            sumLog += TMath.log(TMath.max(1e-16, 1.0 - rho * rho));
        }

        double c = (n - 1) - 0.5 * (p + q + 1);
        if (c <= 0) c = 1.0; // guard against tiny samples
        return -c * sumLog;
    }

    /**
     * Column-wise extraction from a row-major double[][] matrix.
     */
    private static double[][] extractCols(double[][] data, int[] cols, int n, int k) {
        double[][] out = new double[n][k];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < k; j++) {
                out[i][j] = data[i][cols[j]];
            }
        }
        return out;
    }

    /**
     * Sample covariance (unbiased, denom n-1) between column blocks A and B.
     * Both use natural row ordering.
     */
    private static SimpleMatrix sampleCov(double[][] A, double[][] B,
                                          int n, int rDim, int cDim) {
        // means
        double[] meanA = new double[rDim], meanB = new double[cDim];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < rDim; j++) meanA[j] += A[i][j];
            for (int j = 0; j < cDim; j++) meanB[j] += B[i][j];
        }
        for (int j = 0; j < rDim; j++) meanA[j] /= n;
        for (int j = 0; j < cDim; j++) meanB[j] /= n;

        SimpleMatrix S = new SimpleMatrix(rDim, cDim);
        for (int i = 0; i < n; i++) {
            for (int r = 0; r < rDim; r++) {
                double da = A[i][r] - meanA[r];
                for (int c = 0; c < cDim; c++) {
                    S.set(r, c, S.get(r, c) + da * (B[i][c] - meanB[c]));
                }
            }
        }
        S = S.divide(n - 1.0);
        return S;
    }

    /**
     * Same as {@link #sampleCov} but the rows of {@code B} are accessed via
     * the permutation index array, effectively computing Cov(A, B[perm, :]).
     * {@code A} rows are in natural order; only {@code B} rows are permuted.
     */
    private static SimpleMatrix sampleCovPermuted(double[][] A, double[][] B,
                                                  int[] perm,
                                                  int n, int rDim, int cDim) {
        double[] meanA = new double[rDim], meanB = new double[cDim];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < rDim; j++) meanA[j] += A[i][j];
            for (int j = 0; j < cDim; j++) meanB[j] += B[perm[i]][j];
        }
        for (int j = 0; j < rDim; j++) meanA[j] /= n;
        for (int j = 0; j < cDim; j++) meanB[j] /= n;

        SimpleMatrix S = new SimpleMatrix(rDim, cDim);
        for (int i = 0; i < n; i++) {
            for (int r = 0; r < rDim; r++) {
                double da = A[i][r] - meanA[r];
                for (int c = 0; c < cDim; c++) {
                    S.set(r, c, S.get(r, c) + da * (B[perm[i]][c] - meanB[c]));
                }
            }
        }
        S = S.divide(n - 1.0);
        return S;
    }

    // -------------------------------------------------------------------------
    // Private helpers for the permutation test.
    // These follow the same covariance-then-SVD logic as rankLeByWilks.
    // -------------------------------------------------------------------------

    /**
     * Fisher-Yates shuffle of an index array in-place.
     */
    private static void shuffleInPlace(int[] a) {
        for (int i = a.length - 1; i > 0; i--) {
            int j = RandomUtil.getInstance().nextInt(i + 1);
            int tmp = a[i];
            a[i] = a[j];
            a[j] = tmp;
        }
    }

    /**
     * Conditional permutation-based Wilks test for rank(C_{XY|Z}) &le; r,
     * valid in the linear non-Gaussian setting.
     *
     * <p>Uses the Freedman-Lane residual permutation scheme:
     * <ol>
     *   <li>Regress Z out of both X and Y (OLS), obtaining residuals X&#771; and Y&#771;.</li>
     *   <li>Compute the observed Wilks statistic from X&#771; and Y&#771;.</li>
     *   <li>For each permutation, shuffle the rows of Y&#771; and recompute the statistic.</li>
     *   <li>Return the fraction of permutation statistics &ge; the observed statistic.</li>
     * </ol>
     *
     * <p>The residual degrees of freedom are n - rank(Z) - 1, which replaces n - 1
     * in Bartlett's c-factor. This is used for the observed statistic and all
     * permutation statistics consistently, so it cancels in the p-value comparison
     * and only affects the relative weighting of singular values — it is kept for
     * consistency with the unconditional method.
     *
     * @param data  n-by-d raw data matrix (rows = observations).
     * @param xIdx  column indices for the X block.
     * @param yIdx  column indices for the Y block.
     * @param zIdx  column indices for the conditioning set Z.
     * @param r     rank threshold to test (null hypothesis: rank &le; r).
     * @param B     number of permutations (e.g. 999 or 1999).
     * @return      permutation p-value for H0: rank(C_{XY|Z}) &le; r.
     */
    public static double rankLeByConditionalPermutation(double[][] data,
                                                        int[] xIdx, int[] yIdx, int[] zIdx,
                                                        int r, int B) {
        final int n = data.length;
        final int p = xIdx.length, q = yIdx.length;
        final int minpq = TMath.min(p, q);

        if (r < 0 || r >= minpq) {
            throw new IllegalArgumentException("r must satisfy 0 <= r < min(p,q).");
        }
        if (B < 1) throw new IllegalArgumentException("B must be >= 1.");

        // Extract raw blocks.
        double[][] X = extractCols(data, xIdx, n, p);
        double[][] Y = extractCols(data, yIdx, n, q);

        // If Z is empty, fall back to the unconditional permutation test.
        if (zIdx == null || zIdx.length == 0) {
            return rankLeByPermutation(data, xIdx, yIdx, r, B);
        }

        double[][] Z = extractCols(data, zIdx, n, zIdx.length);

        // Effective degrees of freedom: n - rankZ - 1.
        // We use the column count of Z as a proxy for its rank (Z is assumed
        // full column rank after any preprocessing). Caller should ensure Z
        // is not rank-deficient; a ridge is applied inside invSqrtPSD anyway.
        int dof = n - zIdx.length - 1;
        if (dof < 1) {
            throw new IllegalArgumentException(
                    "Too few observations relative to conditioning set size.");
        }

        // Residuals: X_res = X - H*X, Y_res = Y - H*Y  where H = Z(Z'Z)^{-1}Z'.
        // We compute these via QR for numerical stability.
        double[][] Xres = residualsOLS(Z, X, n, zIdx.length, p);
        double[][] Yres = residualsOLS(Z, Y, n, zIdx.length, q);

        // Observed statistic on residuals, using dof in place of n-1.
        double tObs = wilksStatFromData(Xres, Yres, dof, p, q, minpq, r);

        // Permutation loop over residuals.
        int[] rowPerm = new int[n];
        for (int i = 0; i < n; i++) rowPerm[i] = i;

        int exceed = 0;
        for (int b = 0; b < B; b++) {
            shuffleInPlace(rowPerm);
            double tPerm = wilksStatPermuted(Xres, Yres, rowPerm, dof, p, q, minpq, r);
            if (tPerm >= tObs) exceed++;
        }

        return (exceed + 1.0) / (B + 1.0);
    }

//    /**
//     * Estimates the conditional rank using sequential conditional permutation tests.
//     * Mirrors {@link #estimatePermutationRank} but conditions on Z.
//     *
//     * @param data  n-by-d raw data matrix.
//     * @param xIdx  column indices for the X block.
//     * @param yIdx  column indices for the Y block.
//     * @param zIdx  column indices for the conditioning set.
//     * @param alpha significance level.
//     * @param B     number of permutations per test.
//     * @return      estimated conditional rank.
//     */
//    public static int estimateConditionalPermutationRank(double[][] data,
//                                                         int[] xIdx, int[] yIdx, int[] zIdx,
//                                                         double alpha, int B) {
//        int minpq = TMath.min(xIdx.length, yIdx.length);
//        for (int r = 0; r < minpq; r++) {
//            if (rankLeByConditionalPermutation(data, xIdx, yIdx, zIdx, r, B) > alpha) {
//                return r;
//            }
//        }
//        return minpq;
//    }

    /**
     * Computes OLS residuals of regressing each column of Y on Z.
     *
     * <p>Solves min ||Y - Z*B||_F via the normal equations (Z'Z + ridge)^{-1} Z'Y,
     * then returns R = Y - Z * Bhat.  A small ridge is applied for stability,
     * consistent with the ridge used elsewhere in this class.
     *
     * @param Z    n-by-k design matrix (the conditioning variables).
     * @param Y    n-by-q response matrix.
     * @param n    number of observations.
     * @param k    number of conditioning variables (columns of Z).
     * @param q    number of response variables (columns of Y).
     * @return     n-by-q residual matrix.
     */
    private static double[][] residualsOLS(double[][] Z, double[][] Y,
                                           int n, int k, int q) {
        // ZtZ (k x k) and ZtY (k x q).
        double[][] ZtZ = new double[k][k];
        double[][] ZtY = new double[k][q];

        for (int i = 0; i < n; i++) {
            for (int a = 0; a < k; a++) {
                for (int b = 0; b < k; b++) ZtZ[a][b] += Z[i][a] * Z[i][b];
                for (int c = 0; c < q; c++) ZtY[a][c] += Z[i][a] * Y[i][c];
            }
        }

        // Ridge ZtZ for stability.
        for (int a = 0; a < k; a++) ZtZ[a][a] += RIDGE;

        // Solve (ZtZ) * Bhat = ZtY via SimpleMatrix inversion.
        SimpleMatrix mZtZ = new SimpleMatrix(k, k);
        for (int a = 0; a < k; a++)
            for (int b = 0; b < k; b++)
                mZtZ.set(a, b, ZtZ[a][b]);

        SimpleMatrix mZtY = new SimpleMatrix(k, q);
        for (int a = 0; a < k; a++)
            for (int c = 0; c < q; c++)
                mZtY.set(a, c, ZtY[a][c]);

        SimpleMatrix Bhat = mZtZ.invert().mult(mZtY);  // k x q

        // Residuals R = Y - Z * Bhat.
        double[][] R = new double[n][q];
        for (int i = 0; i < n; i++) {
            for (int c = 0; c < q; c++) {
                double fitted = 0.0;
                for (int a = 0; a < k; a++) {
                    fitted += Z[i][a] * Bhat.get(a, c);
                }
                R[i][c] = Y[i][c] - fitted;
            }
        }
        return R;
    }

    // --- Fast Chi-square critical via WilsonâHilferty with cached normal z ---
    private record Chi2Key(int nu, long alphaBits) {
    }

    /**
     * Rank-aware PSD inverse square root.
     * Returns W = D^{-1/2} U^T where U contains kept eigenvectors (columns),
     * so W has shape (r x n). Also returns r.
     */
    private static final class Whitening {
        final SimpleMatrix W;  // r x n
        final int rank;

        Whitening(SimpleMatrix W, int rank) {
            this.W = W;
            this.rank = rank;
        }
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
            this.regBits = Double.doubleToLongBits(TMath.rint(regLambda * 1e20) / 1e20);
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

// -------------------------------------------------------------------------
// OLS residual projection.
// -------------------------------------------------------------------------

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
    public static final class RccaEntry {
        /**
         * An array of singular values sorted in descending order. These values represent the singular values of a
         * matrix in the context of Regularized Canonical Correlation Analysis (RCCA). This array is typically used for
         * computations related to matrix decomposition or transformations.
         */
        public final double[] svals;       // descending
        /**
         * An array where each element at index `i` represents the cumulative sum of the logarithms of (1 - squared
         * singular values) from index `i` to the end of the corresponding singular values array. Specifically,
         * `suffixLogs[i]` = Î£ log(1 - s<sub>j</sub>Â²) for all `j` from `i` to the end. This array is precomputed for
         * efficiency in mathematical or analytical operations related to matrix decomposition or canonical correlation
         * analysis.
         */
        public final double[] suffixLogs;  // suffixLogs[i] = sum_{j=i}^{end} log(1 - s_j^2)

        RccaEntry(double[] svals, double[] suffixLogs) {
            this.svals = svals;
            this.suffixLogs = suffixLogs;
        }
    }
}
