package edu.cmu.tetrad.util;

import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.CommonOps_DDRM;
import org.ejml.dense.row.factory.DecompositionFactory_DDRM;
import org.ejml.interfaces.decomposition.CholeskyDecomposition_F64;
import org.ejml.interfaces.decomposition.SingularValueDecomposition_F64;
import org.ejml.simple.SimpleEVD;
import org.ejml.simple.SimpleMatrix;
import org.ejml.simple.SimpleSVD;

public class RankTests {

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

    public static SimpleMatrix inverseSqrt(SimpleMatrix A) {
        if (!A.isIdentical(A.transpose(), 1e-10)) {
            throw new IllegalArgumentException("Matrix must be symmetric");
        }

        SimpleEVD<SimpleMatrix> eig = A.eig();

        SimpleMatrix Q = new SimpleMatrix(A.getNumRows(), A.getNumCols());
        SimpleMatrix L_invSqrt = new SimpleMatrix(A.getNumRows(), A.getNumCols());

        for (int i = 0; i < A.getNumRows(); i++) {
            double eigenvalue = eig.getEigenvalue(i).getReal();
            if (eigenvalue <= 0) {
                throw new RuntimeException("Matrix not positive definite");
            }

            SimpleMatrix eigenvector = eig.getEigenVector(i);
            Q.setColumn(i, 0, eigenvector.getDDRM().getData());
            L_invSqrt.set(i, i, 1.0 / Math.sqrt(eigenvalue));
        }

        return Q.mult(L_invSqrt).mult(Q.transpose());
    }

    /**
     * Tests whether the canonical correlation rank is exactly r, at level alpha. That is: • if r == 0: returns true
     * only if p-value_rankLE(0) > alpha; • else if r == min(xVars, yVars): (only the upper bound test doesn’t exist in
     * this case), return ( p-value_rankLE(r–1) <= alpha ). • else: require p-value_rankLE(r) > alpha  AND
     * p-value_rankLE(r–1) <= alpha
     *
     * @param S        Correlation matrix of all variables.
     * @param xIndices indices belonging to one side
     * @param yIndices indices for the other side
     * @param n        sample size
     * @param r        hypothesized rank
     * @param alpha    significance level (e.g. 0.05)
     * @return true iff rank = r at given alpha
     */
    public static boolean isCcaRankEqualTo(
            SimpleMatrix S,
            int[] xIndices,
            int[] yIndices,
            int n,
            int r,
            double alpha) {
        if (r < 0) throw new IllegalArgumentException("Rank must be non-negative.");
        final int maxRank = Math.min(xIndices.length, yIndices.length);
        if (r > maxRank) {
            throw new IllegalArgumentException("Rank r=" + r +
                                               " exceeds maximum possible (" + maxRank + ")");
        }

        if (r == 0) {
            double pVal0 = getCcaPValueRankLE(S, xIndices, yIndices, n, 0);
            return pVal0 > alpha;
        }

        double pValRm1 = getCcaPValueRankLE(S, xIndices, yIndices, n, r - 1);

        if (r == maxRank) {
            // No rank > r, so simply require rejection of ≤ r–1
            return pValRm1 <= alpha;
        } else {
            double pValR = getCcaPValueRankLE(S, xIndices, yIndices, n, r);
            return (pValR > alpha) && (pValRm1 <= alpha);
        }
    }

    /**
     * Estimates the canonical correlation rank of the cross-correlation matrix using sequential likelihood ratio
     * tests.
     *
     * @param S        The correlation matrix.
     * @param xIndices Indices of the first variable set.
     * @param yIndices Indices of the second variable set.
     * @param n        Sample size.
     * @param alpha    Significance level (e.g., 0.05).
     * @return The estimated rank (0 ≤ rank ≤ min(p, q)).
     */
    public static int estimateCcaRank(SimpleMatrix S, int[] xIndices, int[] yIndices, int n, double alpha) {
        int p = xIndices.length;
        int q = yIndices.length;
        int minpq = Math.min(p, q);

        // Rank 0 always gives NaN though... but the inequality will fail then. jdramsey 2025-8-1
        for (int r = 0; r < minpq; r++) {
            double pVal = getCcaPValueRankLE(S, xIndices, yIndices, n, r);

            if (pVal > alpha) {
                return r; // First non-rejected rank
            }
        }

        return minpq; // All tests rejected, full rank assumed
    }

    /**
     * Computes RCCA p-value from a covariance matrix.
     *
     * @param S        The (p+q) x (p+q) covariance matrix.
     * @param xIdx Number of variables in group A.
     * @param yIdx Number of variables in group B.
     * @param n        Sample size.
     * @param rank     Hypothesized rank (test is for rank > r).
     * @param reg Regularization parameter (λ > 0).
     * @return The p-value.
     */
//    public static double getRccaPValueRankLE(SimpleMatrix S, int[] xIndices, int[] yIndices, int n, int rank, double regParam) {
//        try {
//            int p = xIndices.length;
//            int q = yIndices.length;
//
//            // Step 1: Extract submatrices
//            SimpleMatrix Cxx = StatUtils.extractSubMatrix(S, xIndices, xIndices).plus(SimpleMatrix.identity(p).scale(regParam));
//            SimpleMatrix Cyy = StatUtils.extractSubMatrix(S, yIndices, yIndices).plus(SimpleMatrix.identity(q).scale(regParam));
//            SimpleMatrix Cxy = StatUtils.extractSubMatrix(S, xIndices, yIndices);
//
//            // Inverse square roots of Cxx and Cyy using eigen-decomposition
//            SimpleMatrix Cxx_inv_sqrt = invSqrtSymmetric(Cxx);
//            SimpleMatrix Cyy_inv_sqrt = invSqrtSymmetric(Cyy);
//
//            // Construct matrix M = Cxx^{-1/2} * Cxy * Cyy^{-1/2}
//            SimpleMatrix M = Cxx_inv_sqrt.mult(Cxy).mult(Cyy_inv_sqrt);
//
//            // Perform SVD on M
//            SimpleSVD<SimpleMatrix> svd = M.svd();
//            double[] singularValues = svd.getW().diag().getDDRM().getData();
//
//            // Test statistic
//            double stat = 0.0;
//            for (int i = rank; i < Math.min(p, q); i++) {
//                double s = singularValues[i];
//                s = Math.max(0.0, Math.min(s, 1.0 - 1e-12));  // Avoid log(0)
//                stat += Math.log(1 - s * s);
//            }
//            double scale = -(n - (p + q + 3) / 2.);
//            stat *= scale;
//
//            // Degrees of freedom
//            int df = (p - rank) * (q - rank);
//
//            // Compute p-value
//            ChiSquaredDistribution chi2 = new ChiSquaredDistribution(df);
//            return 1.0 - chi2.cumulativeProbability(stat);
//        } catch (Exception e) {
//            return 0.0;
//        }
//    }

    public static double getRccaPValueRankLE(SimpleMatrix S,
                                             int[] xIdx, int[] yIdx,
                                             int n, int rank, double reg) {
        try {
            final int p = xIdx.length, q = yIdx.length;
            final int rmin = Math.min(p, q);

            // --- Extract submatrices into DMatrixRMaj
            DMatrixRMaj Cxx = extract(S, xIdx, xIdx);
            DMatrixRMaj Cyy = extract(S, yIdx, yIdx);
            DMatrixRMaj Cxy = extract(S, xIdx, yIdx);

            // --- Add ridge (λI)
            addRidgeInPlace(Cxx, reg);
            addRidgeInPlace(Cyy, reg);

            // --- Cholesky: Cxx = Lx Lx^T (lower), Cyy = Ly Ly^T (lower)
            CholeskyDecomposition_F64<DMatrixRMaj> cholX = DecompositionFactory_DDRM.chol(p, true);
            CholeskyDecomposition_F64<DMatrixRMaj> cholY = DecompositionFactory_DDRM.chol(q, true);
            if (!cholX.decompose(Cxx) || !cholY.decompose(Cyy)) return 0.0;

            DMatrixRMaj Lx = cholX.getT(null); // lower triangular
            DMatrixRMaj Ly = cholY.getT(null); // lower triangular

            // --- T = Lx^{-1} * Cxy * Ly^{-T}
            // Solve Lx * X = Cxy  => X (forward-substitution)
            DMatrixRMaj X = Cxy.copy();
            forwardSolveLowerInPlace(Lx, X); // X <- Lx^{-1} * Cxy

            // Solve Ly^T * Z^T = X^T  => Z = X * Ly^{-T}
            // Do it without forming Ly^T explicitly:
            DMatrixRMaj Xt = new DMatrixRMaj(X.numCols, X.numRows);
            CommonOps_DDRM.transpose(X, Xt);
            // Xt <- (Ly^T)^{-1} * Xt   i.e., solve (Ly^T) * Y = Xt for Y, in-place into Xt
            backwardSolveUpperFromLowerTransposeInPlace(Ly, Xt);
            DMatrixRMaj T = new DMatrixRMaj(X.numRows, X.numCols);
            CommonOps_DDRM.transpose(Xt, T);

            // --- SVD of T: values only
            SingularValueDecomposition_F64<DMatrixRMaj> svd =
                    DecompositionFactory_DDRM.svd(T.numRows, T.numCols, false, false, true);
            if (!svd.decompose(T)) return 0.0;
            double[] svals = svd.getSingularValues(); // descending

            // --- Test statistic
            double stat = 0.0;
            for (int i = rank; i < rmin; i++) {
                double s = Math.max(0.0, Math.min(svals[i], 1.0 - 1e-12));
                stat += Math.log(1.0 - s * s);
            }
            double scale = -(n - (p + q + 3) / 2.0);
            stat *= scale;

            int df = (p - rank) * (q - rank);
            if (df <= 0) return 1.0;

            ChiSquaredDistribution chi2 = new ChiSquaredDistribution(df);
            return 1.0 - chi2.cumulativeProbability(stat);
        } catch (Exception e) {
            return 0.0;
        }
    }

// ----------------- helpers (version-agnostic) -----------------

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

    /** Solve L * X = B in-place (overwrite B with X), where L is lower-triangular with non-unit diagonal. */
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
     * Solve (L^T) * X = B in-place (overwrite B with X), where L is lower-triangular.
     * This is a backward substitution using the implicit upper-triangular U = L^T.
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

    public static int estimateRccaRank(SimpleMatrix S, int[] xIndices, int[] yIndices, int n, double alpha, double regParam) {
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
            double pVal = getRccaPValueRankLE(S, xIndices, yIndices, n, r, regParam);

            if (pVal > alpha) {
                return r; // First non-rejected rank
            }
        }

        return minpq; // All tests rejected, full rank assumed
    }

//    public static int estimateRccaRank(SimpleMatrix S, int[] xIndices, int[] yIndices, int n, double alpha, double regParam) {
//        int p = xIndices.length;
//        int q = yIndices.length;
//        int minpq = Math.min(p, q);
//
//        int lastAcceptedRank = -1;
//
//        for (int r = 0; r < minpq; r++) {
//            double pVal = getRccaPValueRankLE(S, xIndices, yIndices, n, r, regParam);
//
//            if (pVal > alpha) {
//                lastAcceptedRank = r;
//            }
//        }
//
//        if (lastAcceptedRank >= 0) {
//            return lastAcceptedRank;
//        } else {
//            return minpq; // All tests rejected
//        }
//    }
}