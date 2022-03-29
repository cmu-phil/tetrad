package edu.pitt.csb.mgm;


import org.apache.commons.math3.exception.MaxCountExceededException;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.NonSquareMatrixException;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.util.FastMath;
import org.apache.commons.math3.util.Precision;

/**
 * Class transforming a general real matrix to Schur form.
 * <p>A m &times; m matrix A can be written as the product of three matrices: A = P
 * &times; T &times; P<sup>T</sup> with P an orthogonal matrix and T an quasi-triangular
 * matrix. Both P and T are m &times; m matrices.</p>
 * <p>Transformation to Schur form is often not a goal by itself, but it is an
 * intermediate step in more general decomposition algorithms like
 * {@link EigenDecomposition eigen decomposition}. This class is therefore
 * intended for internal use by the library and is not public. As a consequence
 * of this explicitly limited scope, many methods directly returns references to
 * internal arrays, not copies.</p>
 * <p>This class is based on the method hqr2 in class EigenvalueDecomposition
 * from the <a href="http://math.nist.gov/javanumerics/jama/">JAMA</a> library.</p>
 *
 * @see <a href="http://mathworld.wolfram.com/SchurDecomposition.html">Schur Decomposition - MathWorld</a>
 * @see <a href="http://en.wikipedia.org/wiki/Schur_decomposition">Schur Decomposition - Wikipedia</a>
 * @see <a href="http://en.wikipedia.org/wiki/Householder_transformation">Householder Transformations</a>
 * @since 3.1
 */
class SchurTransformer {
    /**
     * Maximum allowed iterations for convergence of the transformation.
     */
    private static final int MAX_ITERATIONS = 100;

    /**
     * P matrix.
     */
    private final double[][] matrixP;
    /**
     * T matrix.
     */
    private final double[][] matrixT;
    /**
     * Cached value of P.
     */
    private RealMatrix cachedP;
    /**
     * Cached value of T.
     */
    private RealMatrix cachedT;
    /**
     * Cached value of PT.
     */
    private RealMatrix cachedPt;

    /**
     * Epsilon criteria taken from JAMA code (originally was 2^-52).
     */
    private final double epsilon = Precision.EPSILON;

    /**
     * Build the transformation to Schur form of a general real matrix.
     *
     * @param matrix matrix to transform
     * @throws NonSquareMatrixException if the matrix is not square
     */
    SchurTransformer(RealMatrix matrix) {
        if (!matrix.isSquare()) {
            throw new NonSquareMatrixException(matrix.getRowDimension(),
                    matrix.getColumnDimension());
        }

        HessenbergTransformer transformer = new HessenbergTransformer(matrix);
        this.matrixT = transformer.getH().getData();
        this.matrixP = transformer.getP().getData();
        this.cachedT = null;
        this.cachedP = null;
        this.cachedPt = null;

        // transform matrix
        transform();
    }

    /**
     * Returns the matrix P of the transform.
     * <p>P is an orthogonal matrix, i.e. its inverse is also its transpose.</p>
     *
     * @return the P matrix
     */
    public RealMatrix getP() {
        if (this.cachedP == null) {
            this.cachedP = MatrixUtils.createRealMatrix(this.matrixP);
        }
        return this.cachedP;
    }

    /**
     * Returns the transpose of the matrix P of the transform.
     * <p>P is an orthogonal matrix, i.e. its inverse is also its transpose.</p>
     *
     * @return the transpose of the P matrix
     */
    public RealMatrix getPT() {
        if (this.cachedPt == null) {
            this.cachedPt = getP().transpose();
        }

        // return the cached matrix
        return this.cachedPt;
    }

    /**
     * Returns the quasi-triangular Schur matrix T of the transform.
     *
     * @return the T matrix
     */
    public RealMatrix getT() {
        if (this.cachedT == null) {
            this.cachedT = MatrixUtils.createRealMatrix(this.matrixT);
        }

        // return the cached matrix
        return this.cachedT;
    }

    /**
     * Transform original matrix to Schur form.
     *
     * @throws MaxCountExceededException if the transformation does not converge
     */
    private void transform() {
        int n = this.matrixT.length;

        // compute matrix norm
        double norm = getNorm();

        // shift information
        ShiftInfo shift = new ShiftInfo();

        // Outer loop over eigenvalue index
        int iteration = 0;
        int iu = n - 1;
        while (iu >= 0) {

            // Look for single small sub-diagonal element
            int il = findSmallSubDiagonalElement(iu, norm);

            // Check for convergence
            if (il == iu) {
                // One root found
                this.matrixT[iu][iu] += shift.exShift;
                iu--;
                iteration = 0;
            } else if (il == iu - 1) {
                // Two roots found
                double p = (this.matrixT[iu - 1][iu - 1] - this.matrixT[iu][iu]) / 2.0;
                double q = p * p + this.matrixT[iu][iu - 1] * this.matrixT[iu - 1][iu];
                this.matrixT[iu][iu] += shift.exShift;
                this.matrixT[iu - 1][iu - 1] += shift.exShift;

                if (q >= 0) {
                    double z = FastMath.sqrt(FastMath.abs(q));
                    if (p >= 0) {
                        z = p + z;
                    } else {
                        z = p - z;
                    }
                    double x = this.matrixT[iu][iu - 1];
                    double s = FastMath.abs(x) + FastMath.abs(z);
                    p = x / s;
                    q = z / s;
                    double r = FastMath.sqrt(p * p + q * q);
                    p /= r;
                    q /= r;

                    // Row modification
                    for (int j = iu - 1; j < n; j++) {
                        z = this.matrixT[iu - 1][j];
                        this.matrixT[iu - 1][j] = q * z + p * this.matrixT[iu][j];
                        this.matrixT[iu][j] = q * this.matrixT[iu][j] - p * z;
                    }

                    // Column modification
                    for (int i = 0; i <= iu; i++) {
                        z = this.matrixT[i][iu - 1];
                        this.matrixT[i][iu - 1] = q * z + p * this.matrixT[i][iu];
                        this.matrixT[i][iu] = q * this.matrixT[i][iu] - p * z;
                    }

                    // Accumulate transformations
                    for (int i = 0; i <= n - 1; i++) {
                        z = this.matrixP[i][iu - 1];
                        this.matrixP[i][iu - 1] = q * z + p * this.matrixP[i][iu];
                        this.matrixP[i][iu] = q * this.matrixP[i][iu] - p * z;
                    }
                }
                iu -= 2;
                iteration = 0;
            } else {
                // No convergence yet
                computeShift(il, iu, iteration, shift);

                // stop transformation after too many iterations
                if (++iteration > SchurTransformer.MAX_ITERATIONS) {
                    return;
//                    throw new MaxCountExceededException(LocalizedFormats.CONVERGENCE_FAILED,
//                            MAX_ITERATIONS);
                }

                // the initial houseHolder vector for the QR step
                double[] hVec = new double[3];

                int im = initQRStep(il, iu, shift, hVec);
                performDoubleQRStep(il, im, iu, shift, hVec);
            }
        }
    }

    /**
     * Computes the L1 norm of the (quasi-)triangular matrix T.
     *
     * @return the L1 norm of matrix T
     */
    private double getNorm() {
        double norm = 0.0;
        for (int i = 0; i < this.matrixT.length; i++) {
            // as matrix T is (quasi-)triangular, also take the sub-diagonal element into account
            for (int j = FastMath.max(i - 1, 0); j < this.matrixT.length; j++) {
                norm += FastMath.abs(this.matrixT[i][j]);
            }
        }
        return norm;
    }

    /**
     * Find the first small sub-diagonal element and returns its index.
     *
     * @param startIdx the starting index for the search
     * @param norm     the L1 norm of the matrix
     * @return the index of the first small sub-diagonal element
     */
    private int findSmallSubDiagonalElement(int startIdx, double norm) {
        int l = startIdx;
        while (l > 0) {
            double s = FastMath.abs(this.matrixT[l - 1][l - 1]) + FastMath.abs(this.matrixT[l][l]);
            if (s == 0.0) {
                s = norm;
            }
            if (FastMath.abs(this.matrixT[l][l - 1]) < this.epsilon * s) {
                break;
            }
            l--;
        }
        return l;
    }

    /**
     * Compute the shift for the current iteration.
     *
     * @param l         the index of the small sub-diagonal element
     * @param idx       the current eigenvalue index
     * @param iteration the current iteration
     * @param shift     holder for shift information
     */
    private void computeShift(int l, int idx, int iteration, ShiftInfo shift) {
        // Form shift
        shift.x = this.matrixT[idx][idx];
        shift.y = shift.w = 0.0;
        if (l < idx) {
            shift.y = this.matrixT[idx - 1][idx - 1];
            shift.w = this.matrixT[idx][idx - 1] * this.matrixT[idx - 1][idx];
        }

        // Wilkinson's original ad hoc shift
        if (iteration == 10) {
            shift.exShift += shift.x;
            for (int i = 0; i <= idx; i++) {
                this.matrixT[i][i] -= shift.x;
            }
            double s = FastMath.abs(this.matrixT[idx][idx - 1]) + FastMath.abs(this.matrixT[idx - 1][idx - 2]);
            shift.x = 0.75 * s;
            shift.y = 0.75 * s;
            shift.w = -0.4375 * s * s;
        }

        // MATLAB's new ad hoc shift
        if (iteration == 30) {
            double s = (shift.y - shift.x) / 2.0;
            s = s * s + shift.w;
            if (s > 0.0) {
                s = FastMath.sqrt(s);
                if (shift.y < shift.x) {
                    s = -s;
                }
                s = shift.x - shift.w / ((shift.y - shift.x) / 2.0 + s);
                for (int i = 0; i <= idx; i++) {
                    this.matrixT[i][i] -= s;
                }
                shift.exShift += s;
                shift.x = shift.y = shift.w = 0.964;
            }
        }
    }

    /**
     * Initialize the householder vectors for the QR step.
     *
     * @param il    the index of the small sub-diagonal element
     * @param iu    the current eigenvalue index
     * @param shift shift information holder
     * @param hVec  the initial houseHolder vector
     * @return the start index for the QR step
     */
    private int initQRStep(int il, int iu, ShiftInfo shift, double[] hVec) {
        // Look for two consecutive small sub-diagonal elements
        int im = iu - 2;
        while (im >= il) {
            double z = this.matrixT[im][im];
            double r = shift.x - z;
            double s = shift.y - z;
            hVec[0] = (r * s - shift.w) / this.matrixT[im + 1][im] + this.matrixT[im][im + 1];
            hVec[1] = this.matrixT[im + 1][im + 1] - z - r - s;
            hVec[2] = this.matrixT[im + 2][im + 1];

            if (im == il) {
                break;
            }

            double lhs = FastMath.abs(this.matrixT[im][im - 1]) * (FastMath.abs(hVec[1]) + FastMath.abs(hVec[2]));
            double rhs = FastMath.abs(hVec[0]) * (FastMath.abs(this.matrixT[im - 1][im - 1]) +
                    FastMath.abs(z) +
                    FastMath.abs(this.matrixT[im + 1][im + 1]));

            if (lhs < this.epsilon * rhs) {
                break;
            }
            im--;
        }

        return im;
    }

    /**
     * Perform a double QR step involving rows l:idx and columns m:n
     *
     * @param il    the index of the small sub-diagonal element
     * @param im    the start index for the QR step
     * @param iu    the current eigenvalue index
     * @param shift shift information holder
     * @param hVec  the initial houseHolder vector
     */
    private void performDoubleQRStep(int il, int im, int iu,
                                     ShiftInfo shift, double[] hVec) {

        int n = this.matrixT.length;
        double p = hVec[0];
        double q = hVec[1];
        double r = hVec[2];

        for (int k = im; k <= iu - 1; k++) {
            boolean notlast = k != (iu - 1);
            if (k != im) {
                p = this.matrixT[k][k - 1];
                q = this.matrixT[k + 1][k - 1];
                r = notlast ? this.matrixT[k + 2][k - 1] : 0.0;
                shift.x = FastMath.abs(p) + FastMath.abs(q) + FastMath.abs(r);
                if (Precision.equals(shift.x, 0.0, this.epsilon)) {
                    continue;
                }
                p /= shift.x;
                q /= shift.x;
                r /= shift.x;
            }
            double s = FastMath.sqrt(p * p + q * q + r * r);
            if (p < 0.0) {
                s = -s;
            }
            if (s != 0.0) {
                if (k != im) {
                    this.matrixT[k][k - 1] = -s * shift.x;
                } else if (il != im) {
                    this.matrixT[k][k - 1] = -this.matrixT[k][k - 1];
                }
                p += s;
                shift.x = p / s;
                shift.y = q / s;
                double z = r / s;
                q /= p;
                r /= p;

                // Row modification
                for (int j = k; j < n; j++) {
                    p = this.matrixT[k][j] + q * this.matrixT[k + 1][j];
                    if (notlast) {
                        p += r * this.matrixT[k + 2][j];
                        this.matrixT[k + 2][j] -= p * z;
                    }
                    this.matrixT[k][j] -= p * shift.x;
                    this.matrixT[k + 1][j] -= p * shift.y;
                }

                // Column modification
                for (int i = 0; i <= FastMath.min(iu, k + 3); i++) {
                    p = shift.x * this.matrixT[i][k] + shift.y * this.matrixT[i][k + 1];
                    if (notlast) {
                        p += z * this.matrixT[i][k + 2];
                        this.matrixT[i][k + 2] -= p * r;
                    }
                    this.matrixT[i][k] -= p;
                    this.matrixT[i][k + 1] -= p * q;
                }

                // Accumulate transformations
                int high = this.matrixT.length - 1;
                for (int i = 0; i <= high; i++) {
                    p = shift.x * this.matrixP[i][k] + shift.y * this.matrixP[i][k + 1];
                    if (notlast) {
                        p += z * this.matrixP[i][k + 2];
                        this.matrixP[i][k + 2] -= p * r;
                    }
                    this.matrixP[i][k] -= p;
                    this.matrixP[i][k + 1] -= p * q;
                }
            }  // (s != 0)
        }  // k loop

        // clean up pollution due to round-off errors
        for (int i = im + 2; i <= iu; i++) {
            this.matrixT[i][i - 2] = 0.0;
            if (i > im + 2) {
                this.matrixT[i][i - 3] = 0.0;
            }
        }
    }

    /**
     * Internal data structure holding the current shift information.
     * Contains variable names as present in the original JAMA code.
     */
    private static class ShiftInfo {
        // CHECKSTYLE: stop all

        /**
         * x shift info
         */
        double x;
        /**
         * y shift info
         */
        double y;
        /**
         * w shift info
         */
        double w;
        /**
         * Indicates an exceptional shift.
         */
        double exShift;

        // CHECKSTYLE: resume all
    }
}