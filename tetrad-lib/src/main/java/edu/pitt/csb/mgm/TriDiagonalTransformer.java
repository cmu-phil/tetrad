package edu.pitt.csb.mgm;

import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.NonSquareMatrixException;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.util.FastMath;

import java.util.Arrays;


/**
 * Class transforming a symmetrical matrix to tridiagonal shape.
 * <p>A symmetrical m &times; m matrix A can be written as the product of three matrices:
 * A = Q &times; T &times; Q<sup>T</sup> with Q an orthogonal matrix and T a symmetrical tridiagonal matrix. Both Q and
 * T are m &times; m matrices.&gt; 0
 * <p>This implementation only uses the upper part of the matrix, the part below the
 * diagonal is not accessed at all.&gt; 0
 * <p>Transformation to tridiagonal shape is often not a goal by itself, but it is
 * an intermediate step in more general decomposition algorithms like eigen decomposition.
 * This class is therefore intended for internal use by the library and is not public. As a consequence of this
 * explicitly limited scope, many methods directly returns references to internal arrays, not copies.&gt; 0
 *
 * @since 2.0
 */
class TriDiagonalTransformer {
    /**
     * Householder vectors.
     */
    private final double[][] householderVectors;
    /**
     * Main diagonal.
     */
    private final double[] main;
    /**
     * Secondary diagonal.
     */
    private final double[] secondary;
    /**
     * Cached value of Q.
     */
    private RealMatrix cachedQ;
    /**
     * Cached value of Qt.
     */
    private RealMatrix cachedQt;
    /**
     * Cached value of T.
     */
    private RealMatrix cachedT;

    /**
     * Build the transformation to tridiagonal shape of a symmetrical matrix.
     * <p>The specified matrix is assumed to be symmetrical without any check.
     * Only the upper triangular part of the matrix is used.&gt; 0
     *
     * @param matrix Symmetrical matrix to transform.
     * @throws NonSquareMatrixException if the matrix is not square.
     */
    TriDiagonalTransformer(RealMatrix matrix) {
        if (!matrix.isSquare()) {
            throw new NonSquareMatrixException(matrix.getRowDimension(),
                    matrix.getColumnDimension());
        }

        int m = matrix.getRowDimension();
        this.householderVectors = matrix.getData();
        this.main = new double[m];
        this.secondary = new double[m - 1];
        this.cachedQ = null;
        this.cachedQt = null;
        this.cachedT = null;

        // transform matrix
        transform();
    }

    /**
     * Returns the matrix Q of the transform.
     * <p>Q is an orthogonal matrix, i.e. its transpose is also its inverse.&gt; 0
     *
     * @return the Q matrix
     */
    public RealMatrix getQ() {
        if (this.cachedQ == null) {
            this.cachedQ = getQT().transpose();
        }
        return this.cachedQ;
    }

    /**
     * Returns the transpose of the matrix Q of the transform.
     * <p>Q is an orthogonal matrix, i.e. its transpose is also its inverse.&gt; 0
     *
     * @return the Q matrix
     */
    public RealMatrix getQT() {
        if (this.cachedQt == null) {
            int m = this.householderVectors.length;
            double[][] qta = new double[m][m];

            // build up first part of the matrix by applying Householder transforms
            for (int k = m - 1; k >= 1; --k) {
                double[] hK = this.householderVectors[k - 1];
                qta[k][k] = 1;
                if (hK[k] != 0.0) {
                    double inv = 1.0 / (this.secondary[k - 1] * hK[k]);
                    double beta = 1.0 / this.secondary[k - 1];
                    qta[k][k] = 1 + beta * hK[k];
                    for (int i = k + 1; i < m; ++i) {
                        qta[k][i] = beta * hK[i];
                    }
                    for (int j = k + 1; j < m; ++j) {
                        beta = 0;
                        for (int i = k + 1; i < m; ++i) {
                            beta += qta[j][i] * hK[i];
                        }
                        beta *= inv;
                        qta[j][k] = beta * hK[k];
                        for (int i = k + 1; i < m; ++i) {
                            qta[j][i] += beta * hK[i];
                        }
                    }
                }
            }
            qta[0][0] = 1;
            this.cachedQt = MatrixUtils.createRealMatrix(qta);
        }

        // return the cached matrix
        return this.cachedQt;
    }

    /**
     * Returns the tridiagonal matrix T of the transform.
     *
     * @return the T matrix
     */
    public RealMatrix getT() {
        if (this.cachedT == null) {
            int m = this.main.length;
            double[][] ta = new double[m][m];
            for (int i = 0; i < m; ++i) {
                ta[i][i] = this.main[i];
                if (i > 0) {
                    ta[i][i - 1] = this.secondary[i - 1];
                }
                if (i < this.main.length - 1) {
                    ta[i][i + 1] = this.secondary[i];
                }
            }
            this.cachedT = MatrixUtils.createRealMatrix(ta);
        }

        // return the cached matrix
        return this.cachedT;
    }

    /**
     * Get the Householder vectors of the transform.
     * <p>Note that since this class is only intended for internal use,
     * it returns directly a reference to its internal arrays, not a copy.&gt; 0
     *
     * @return the main diagonal elements of the B matrix
     */
    double[][] getHouseholderVectorsRef() {
        return this.householderVectors;
    }

    /**
     * Get the main diagonal elements of the matrix T of the transform.
     * <p>Note that since this class is only intended for internal use,
     * it returns directly a reference to its internal arrays, not a copy.&gt; 0
     *
     * @return the main diagonal elements of the T matrix
     */
    double[] getMainDiagonalRef() {
        return this.main;
    }

    /**
     * Get the secondary diagonal elements of the matrix T of the transform.
     * <p>Note that since this class is only intended for internal use,
     * it returns directly a reference to its internal arrays, not a copy.&gt; 0
     *
     * @return the secondary diagonal elements of the T matrix
     */
    double[] getSecondaryDiagonalRef() {
        return this.secondary;
    }

    /**
     * Transform original matrix to tridiagonal form.
     * <p>Transformation is done using Householder transforms.&gt; 0
     */
    private void transform() {
        int m = this.householderVectors.length;
        double[] z = new double[m];
        for (int k = 0; k < m - 1; k++) {

            //zero-out a row and a column simultaneously
            double[] hK = this.householderVectors[k];
            this.main[k] = hK[k];
            double xNormSqr = 0;
            for (int j = k + 1; j < m; ++j) {
                double c = hK[j];
                xNormSqr += c * c;
            }
            double a = (hK[k + 1] > 0) ? -FastMath.sqrt(xNormSqr) : FastMath.sqrt(xNormSqr);
            this.secondary[k] = a;
            if (a != 0.0) {
                // apply Householder transform from left and right simultaneously

                hK[k + 1] -= a;
                double beta = -1 / (a * hK[k + 1]);

                // compute a = beta A v, where v is the Householder vector
                // this loop is written in such a way
                //   1) only the upper triangular part of the matrix is accessed
                //   2) access is cache-friendly for a matrix stored in rows
                Arrays.fill(z, k + 1, m, 0);
                for (int i = k + 1; i < m; ++i) {
                    double[] hI = this.householderVectors[i];
                    double hKI = hK[i];
                    double zI = hI[i] * hKI;
                    for (int j = i + 1; j < m; ++j) {
                        double hIJ = hI[j];
                        zI += hIJ * hK[j];
                        z[j] += hIJ * hKI;
                    }
                    z[i] = beta * (z[i] + zI);
                }

                // compute gamma = beta vT z / 2
                double gamma = 0;
                for (int i = k + 1; i < m; ++i) {
                    gamma += z[i] * hK[i];
                }
                gamma *= beta / 2;

                // compute z = z - gamma v
                for (int i = k + 1; i < m; ++i) {
                    z[i] -= gamma * hK[i];
                }

                // update matrix: A = A - v zT - z vT
                // only the upper triangular part of the matrix is updated
                for (int i = k + 1; i < m; ++i) {
                    double[] hI = this.householderVectors[i];
                    for (int j = i; j < m; ++j) {
                        hI[j] -= hK[i] * z[j] + z[i] * hK[j];
                    }
                }
            }
        }
        this.main[m - 1] = this.householderVectors[m - 1][m - 1];
    }
}