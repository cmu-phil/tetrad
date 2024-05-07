package edu.pitt.csb.mgm;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.exception.DimensionMismatchException;
import org.apache.commons.math3.exception.MathArithmeticException;
import org.apache.commons.math3.exception.MathUnsupportedOperationException;
import org.apache.commons.math3.exception.MaxCountExceededException;
import org.apache.commons.math3.exception.util.LocalizedFormats;
import org.apache.commons.math3.linear.*;
import org.apache.commons.math3.util.FastMath;
import org.apache.commons.math3.util.Precision;

/**
 * Calculates the eigen decomposition of a real matrix.
 * <p>The eigen decomposition of matrix A is a set of two matrices:
 * V and D such that A = V &times; D &times; V<sup>T</sup>. A, V and D are all m &times; m matrices.&gt; 0
 * <p>This class is similar in spirit to the <code>EigenvalueDecomposition</code>
 * class from the <a href="http://math.nist.gov/javanumerics/jama/">JAMA</a> library, with the following changes:&gt; 0
 * <ul>
 *   <li>a {@link #getVT() getVt} method has been added,</li>
 *   <li>two {@link #getRealEigenvalue(int) getRealEigenvalue} and {@link #getImagEigenvalue(int)
 *   getImagEigenvalue} methods to pick up a single eigenvalue have been added,</li>
 *   <li>a {@link #getEigenvector(int) getEigenvector} method to pick up a single
 *   eigenvector has been added,</li>
 *   <li>a {@link #getDeterminant() getDeterminant} method has been added.</li>
 *   <li>a {@link #getSolver() getSolver} method has been added.</li>
 * </ul>
 * <p>
 * As of 3.1, this class supports general real matrices (both symmetric and non-symmetric):
 *
 * <p>
 * If A is symmetric, then A = V*D*V' where the eigenvalue matrix D is diagonal and the eigenvector
 * matrix V is orthogonal, i.e. A = V.multiply(D.multiply(V.transpose())) and
 * V.multiply(V.transpose()) equals the identity matrix.
 *
 * <p>
 * If A is not symmetric, then the eigenvalue matrix D is block diagonal with the real eigenvalues
 * in 1-by-1 blocks and any complex eigenvalues, lambda + i*mu, in 2-by-2 blocks:
 * <pre>
 *    [lambda, mu    ]
 *    [   -mu, lambda]
 * </pre>
 * The columns of V represent the eigenvectors in the sense that A*V = V*D,
 * i.e. A.multiply(V) equals V.multiply(D).
 * The matrix V may be badly conditioned, or even singular, so the validity of the equation
 * A = V*D*inverse(V) depends upon the condition of V.
 *
 * <p>
 * This implementation is based on the paper by A. Drubrulle, R.S. Martin and
 * J.H. Wilkinson "The Implicit QL Algorithm" in Wilksinson and Reinsch (1971)
 * Handbook for automatic computation, vol. 2, Linear algebra, Springer-Verlag,
 * New-York
 *
 * @author josephramsey
 * @version $Id: $Id
 * @see <a href="http://mathworld.wolfram.com/EigenDecomposition.html">MathWorld</a>
 * @see <a href="http://en.wikipedia.org/wiki/Eigendecomposition_of_a_matrix">Wikipedia</a>
 * @since 2.0 (changed to concrete class in 3.0)
 */
public class EigenDecomposition {


    /**
     * Internally used epsilon criteria.
     */
    private static final double EPSILON = 1e-12;
    /**
     * Whether the matrix is symmetric.
     */
    private final boolean isSymmetric;
    /**
     * Main diagonal of the tridiagonal matrix.
     */
    private double[] main;
    /**
     * Secondary diagonal of the tridiagonal matrix.
     */
    private double[] secondary;
    /**
     * Transformer to tridiagonal (may be null if matrix is already tridiagonal).
     */
    private TriDiagonalTransformer transformer;
    /**
     * Real part of the realEigenvalues.
     */
    private double[] realEigenvalues;
    /**
     * Imaginary part of the realEigenvalues.
     */
    private double[] imagEigenvalues;
    /**
     * Eigenvectors.
     */
    private ArrayRealVector[] eigenvectors;
    /**
     * Cached value of V.
     */
    private RealMatrix cachedV;
    /**
     * Cached value of D.
     */
    private RealMatrix cachedD;
    /**
     * Cached value of Vt.
     */
    private RealMatrix cachedVt;

    /**
     * Calculates the eigen decomposition of the given real matrix.
     * <p>
     * Supports decomposition of a general matrix since 3.1.
     *
     * @param matrix Matrix to decompose.
     * @throws org.apache.commons.math3.exception.MathArithmeticException if the decomposition of a general matrix
     *                                                                    results in a matrix with zero norm
     * @since 3.1
     */
    public EigenDecomposition(RealMatrix matrix)
            throws MathArithmeticException {
        double symTol = 10 * matrix.getRowDimension() * matrix.getColumnDimension() * Precision.EPSILON;
        this.isSymmetric = MatrixUtils.isSymmetric(matrix, symTol);
        if (this.isSymmetric) {
            transformToTridiagonal(matrix);
            findEigenVectors(this.transformer.getQ().getData());
        } else {
            SchurTransformer t = transformToSchur(matrix);
            findEigenVectorsFromSchur(t);
        }
    }

    /**
     * Calculates the eigen decomposition of the given real matrix.
     *
     * @param matrix         Matrix to decompose.
     * @param splitTolerance Dummy parameter (present for backward compatibility only).
     * @throws org.apache.commons.math3.exception.MathArithmeticException if the decomposition of a general matrix
     *                                                                    results in a matrix with zero norm
     * @deprecated in 3.1 (to be removed in 4.0) due to unused parameter
     */
    @Deprecated
    public EigenDecomposition(RealMatrix matrix,
                              double splitTolerance)
            throws MathArithmeticException {
        this(matrix);
    }

    /**
     * Calculates the eigen decomposition of the symmetric tridiagonal matrix.  The Householder matrix is assumed to be
     * the identity matrix.
     *
     * @param main      Main diagonal of the symmetric tridiagonal form.
     * @param secondary Secondary of the tridiagonal form.
     * @since 3.1
     */
    public EigenDecomposition(double[] main, double[] secondary) {
        this.isSymmetric = true;
        this.main = main.clone();
        this.secondary = secondary.clone();
        this.transformer = null;
        int size = main.length;
        double[][] z = new double[size][size];
        for (int i = 0; i < size; i++) {
            z[i][i] = 1.0;
        }
        findEigenVectors(z);
    }

    /**
     * Calculates the eigen decomposition of the symmetric tridiagonal matrix.  The Householder matrix is assumed to be
     * the identity matrix.
     *
     * @param main           Main diagonal of the symmetric tridiagonal form.
     * @param secondary      Secondary of the tridiagonal form.
     * @param splitTolerance Dummy parameter (present for backward compatibility only).
     * @deprecated in 3.1 (to be removed in 4.0) due to unused parameter
     */
    @Deprecated
    public EigenDecomposition(double[] main, double[] secondary,
                              double splitTolerance) {
        this(main, secondary);
    }

    /**
     * Gets the matrix V of the decomposition. V is an orthogonal matrix, i.e. its transpose is also its inverse. The
     * columns of V are the eigenvectors of the original matrix. No assumption is made about the orientation of the
     * system axes formed by the columns of V (e.g. in a 3-dimension space, V can form a left- or right-handed system).
     *
     * @return the V matrix.
     */
    public RealMatrix getV() {

        if (this.cachedV == null) {
            int m = this.eigenvectors.length;
            this.cachedV = MatrixUtils.createRealMatrix(m, m);
            for (int k = 0; k < m; ++k) {
                this.cachedV.setColumnVector(k, this.eigenvectors[k]);
            }
        }
        // return the cached matrix
        return this.cachedV;
    }

    /**
     * Gets the block diagonal matrix D of the decomposition. D is a block diagonal matrix. Real eigenvalues are on the
     * diagonal while complex values are on 2x2 blocks { {real +imaginary}, {-imaginary, real} }.
     *
     * @return the D matrix.
     * @see #getRealEigenvalues()
     * @see #getImagEigenvalues()
     */
    public RealMatrix getD() {

        if (this.cachedD == null) {
            // cache the matrix for subsequent calls
            this.cachedD = MatrixUtils.createRealDiagonalMatrix(this.realEigenvalues);

            for (int i = 0; i < this.imagEigenvalues.length; i++) {
                if (Precision.compareTo(this.imagEigenvalues[i], 0.0, EigenDecomposition.EPSILON) > 0) {
                    this.cachedD.setEntry(i, i + 1, this.imagEigenvalues[i]);
                } else if (Precision.compareTo(this.imagEigenvalues[i], 0.0, EigenDecomposition.EPSILON) < 0) {
                    this.cachedD.setEntry(i, i - 1, this.imagEigenvalues[i]);
                }
            }
        }
        return this.cachedD;
    }

    /**
     * Gets the transpose of the matrix V of the decomposition. V is an orthogonal matrix, i.e. its transpose is also
     * its inverse. The columns of V are the eigenvectors of the original matrix. No assumption is made about the
     * orientation of the system axes formed by the columns of V (e.g. in a 3-dimension space, V can form a left- or
     * right-handed system).
     *
     * @return the transpose of the V matrix.
     */
    public RealMatrix getVT() {

        if (this.cachedVt == null) {
            int m = this.eigenvectors.length;
            this.cachedVt = MatrixUtils.createRealMatrix(m, m);
            for (int k = 0; k < m; ++k) {
                this.cachedVt.setRowVector(k, this.eigenvectors[k]);
            }
        }

        // return the cached matrix
        return this.cachedVt;
    }

    /**
     * Returns whether the calculated eigen values are complex or real.
     * <p>The method performs a zero check for each element of the
     * {@link #getImagEigenvalues()} array and returns {@code true} if any element is not equal to zero.
     *
     * @return {@code true} if the eigen values are complex, {@code false} otherwise
     * @since 3.1
     */
    public boolean hasComplexEigenvalues() {
        for (double imagEigenvalue : this.imagEigenvalues) {
            if (!Precision.equals(imagEigenvalue, 0.0, EigenDecomposition.EPSILON)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets a copy of the real parts of the eigenvalues of the original matrix.
     *
     * @return a copy of the real parts of the eigenvalues of the original matrix.
     * @see #getD()
     * @see #getRealEigenvalue(int)
     * @see #getImagEigenvalues()
     */
    public double[] getRealEigenvalues() {
        return this.realEigenvalues.clone();
    }

    /**
     * Returns the real part of the i<sup>th</sup> eigenvalue of the original matrix.
     *
     * @param i index of the eigenvalue (counting from 0)
     * @return real part of the i<sup>th</sup> eigenvalue of the original matrix.
     * @see #getD()
     * @see #getRealEigenvalues()
     * @see #getImagEigenvalue(int)
     */
    public double getRealEigenvalue(int i) {
        return this.realEigenvalues[i];
    }

    /**
     * Gets a copy of the imaginary parts of the eigenvalues of the original matrix.
     *
     * @return a copy of the imaginary parts of the eigenvalues of the original matrix.
     * @see #getD()
     * @see #getImagEigenvalue(int)
     * @see #getRealEigenvalues()
     */
    public double[] getImagEigenvalues() {
        return this.imagEigenvalues.clone();
    }

    /**
     * Gets the imaginary part of the i<sup>th</sup> eigenvalue of the original matrix.
     *
     * @param i Index of the eigenvalue (counting from 0).
     * @return the imaginary part of the i<sup>th</sup> eigenvalue of the original matrix.
     * @see #getD()
     * @see #getImagEigenvalues()
     * @see #getRealEigenvalue(int)
     */
    public double getImagEigenvalue(int i) {
        return this.imagEigenvalues[i];
    }

    /**
     * Gets a copy of the i<sup>th</sup> eigenvector of the original matrix.
     *
     * @param i Index of the eigenvector (counting from 0).
     * @return a copy of the i<sup>th</sup> eigenvector of the original matrix.
     * @see #getD()
     */
    public RealVector getEigenvector(int i) {
        return this.eigenvectors[i].copy();
    }

    /**
     * Computes the determinant of the matrix.
     *
     * @return the determinant of the matrix.
     */
    public double getDeterminant() {
        double determinant = 1;
        for (double lambda : this.realEigenvalues) {
            determinant *= lambda;
        }
        return determinant;
    }

    /**
     * Computes the square-root of the matrix. This implementation assumes that the matrix is symmetric and positive
     * definite.
     *
     * @return the square-root of the matrix.
     * @since 3.1
     */
    public RealMatrix getSquareRoot() {
        if (!this.isSymmetric) {
            throw new MathUnsupportedOperationException();
        }

        double[] sqrtEigenValues = new double[this.realEigenvalues.length];
        for (int i = 0; i < this.realEigenvalues.length; i++) {
            double eigen = this.realEigenvalues[i];
            if (eigen <= 0) {
                throw new MathUnsupportedOperationException();
            }
            sqrtEigenValues[i] = FastMath.sqrt(eigen);
        }
        RealMatrix sqrtEigen = MatrixUtils.createRealDiagonalMatrix(sqrtEigenValues);
        RealMatrix v = getV();
        RealMatrix vT = getVT();

        return v.multiply(sqrtEigen).multiply(vT);
    }

    /**
     * Gets a solver for finding the A &times; X = B solution in exact linear sense.
     * <p>
     * Since 3.1, eigen decomposition of a general matrix is supported, but the
     * {@link org.apache.commons.math3.linear.DecompositionSolver} only supports real eigenvalues.
     *
     * @return a solver
     */
    public DecompositionSolver getSolver() {
        if (hasComplexEigenvalues()) {
            throw new MathUnsupportedOperationException();
        }
        return new Solver(this.realEigenvalues, this.imagEigenvalues, this.eigenvectors);
    }

    /**
     * Transforms the matrix to tridiagonal form.
     *
     * @param matrix Matrix to transform.
     */
    private void transformToTridiagonal(RealMatrix matrix) {
        // transform the matrix to tridiagonal
        this.transformer = new TriDiagonalTransformer(matrix);
        this.main = this.transformer.getMainDiagonalRef();
        this.secondary = this.transformer.getSecondaryDiagonalRef();
    }

    /**
     * Find eigenvalues and eigenvectors (Dubrulle et al., 1971)
     *
     * @param householderMatrix Householder matrix of the transformation to tridiagonal form.
     */
    private void findEigenVectors(double[][] householderMatrix) {
        double[][] z = householderMatrix.clone();
        int n = this.main.length;
        this.realEigenvalues = new double[n];
        this.imagEigenvalues = new double[n];
        double[] e = new double[n];
        for (int i = 0; i < n - 1; i++) {
            this.realEigenvalues[i] = this.main[i];
            e[i] = this.secondary[i];
        }
        this.realEigenvalues[n - 1] = this.main[n - 1];
        e[n - 1] = 0;

        // Determine the largest main and secondary value in absolute term.
        double maxAbsoluteValue = 0;
        for (int i = 0; i < n; i++) {
            if (FastMath.abs(this.realEigenvalues[i]) > maxAbsoluteValue) {
                maxAbsoluteValue = FastMath.abs(this.realEigenvalues[i]);
            }
            if (FastMath.abs(e[i]) > maxAbsoluteValue) {
                maxAbsoluteValue = FastMath.abs(e[i]);
            }
        }
        // Make null any main and secondary value too small to be significant
        if (maxAbsoluteValue != 0) {
            for (int i = 0; i < n; i++) {
                if (FastMath.abs(this.realEigenvalues[i]) <= Precision.EPSILON * maxAbsoluteValue) {
                    this.realEigenvalues[i] = 0;
                }
                if (FastMath.abs(e[i]) <= Precision.EPSILON * maxAbsoluteValue) {
                    e[i] = 0;
                }
            }
        }

        for (int j = 0; j < n; j++) {
            int its = 0;
            int m;
            do {
                for (m = j; m < n - 1; m++) {
                    double delta = FastMath.abs(this.realEigenvalues[m]) +
                                   FastMath.abs(this.realEigenvalues[m + 1]);
                    if (FastMath.abs(e[m]) + delta == delta) {
                        break;
                    }
                }
                if (m != j) {
                    /**
                     * Maximum number of iterations accepted in the implicit QL transformation
                     */
                    short maxIter = 500;
                    if (its == maxIter) {
                        throw new MaxCountExceededException(LocalizedFormats.CONVERGENCE_FAILED,
                                maxIter);
                    }
                    its++;
                    double q = (this.realEigenvalues[j + 1] - this.realEigenvalues[j]) / (2 * e[j]);
                    double t = FastMath.sqrt(1 + q * q);
                    if (q < 0.0) {
                        q = this.realEigenvalues[m] - this.realEigenvalues[j] + e[j] / (q - t);
                    } else {
                        q = this.realEigenvalues[m] - this.realEigenvalues[j] + e[j] / (q + t);
                    }
                    double u = 0.0;
                    double s = 1.0;
                    double c = 1.0;
                    int i;
                    for (i = m - 1; i >= j; i--) {
                        double p = s * e[i];
                        double h = c * e[i];
                        if (FastMath.abs(p) >= FastMath.abs(q)) {
                            c = q / p;
                            t = FastMath.sqrt(c * c + 1.0);
                            e[i + 1] = p * t;
                            s = 1.0 / t;
                            c *= s;
                        } else {
                            s = p / q;
                            t = FastMath.sqrt(s * s + 1.0);
                            e[i + 1] = q * t;
                            c = 1.0 / t;
                            s *= c;
                        }
                        if (e[i + 1] == 0.0) {
                            this.realEigenvalues[i + 1] -= u;
                            e[m] = 0.0;
                            break;
                        }
                        q = this.realEigenvalues[i + 1] - u;
                        t = (this.realEigenvalues[i] - q) * s + 2.0 * c * h;
                        u = s * t;
                        this.realEigenvalues[i + 1] = q + u;
                        q = c * t - h;
                        for (int ia = 0; ia < n; ia++) {
                            p = z[ia][i + 1];
                            z[ia][i + 1] = s * z[ia][i] + c * p;
                            z[ia][i] = c * z[ia][i] - s * p;
                        }
                    }
                    if (t == 0.0 && i >= j) {
                        continue;
                    }
                    this.realEigenvalues[j] -= u;
                    e[j] = q;
                    e[m] = 0.0;
                }
            } while (m != j);
        }

        //Sort the eigen values (and vectors) in increase order
        for (int i = 0; i < n; i++) {
            int k = i;
            double p = this.realEigenvalues[i];
            for (int j = i + 1; j < n; j++) {
                if (this.realEigenvalues[j] > p) {
                    k = j;
                    p = this.realEigenvalues[j];
                }
            }
            if (k != i) {
                this.realEigenvalues[k] = this.realEigenvalues[i];
                this.realEigenvalues[i] = p;
                for (int j = 0; j < n; j++) {
                    p = z[j][i];
                    z[j][i] = z[j][k];
                    z[j][k] = p;
                }
            }
        }

        // Determine the largest eigen value in absolute term.
        maxAbsoluteValue = 0;
        for (int i = 0; i < n; i++) {
            if (FastMath.abs(this.realEigenvalues[i]) > maxAbsoluteValue) {
                maxAbsoluteValue = FastMath.abs(this.realEigenvalues[i]);
            }
        }
        // Make null any eigen value too small to be significant
        if (maxAbsoluteValue != 0.0) {
            for (int i = 0; i < n; i++) {
                if (FastMath.abs(this.realEigenvalues[i]) < Precision.EPSILON * maxAbsoluteValue) {
                    this.realEigenvalues[i] = 0;
                }
            }
        }
        this.eigenvectors = new ArrayRealVector[n];
        double[] tmp = new double[n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                tmp[j] = z[j][i];
            }
            this.eigenvectors[i] = new ArrayRealVector(tmp);
        }
    }

    /**
     * Transforms the matrix to Schur form and calculates the eigenvalues.
     *
     * @param matrix Matrix to transform.
     * @return the {@link SchurTransformer Shur transform} for this matrix
     */
    private SchurTransformer transformToSchur(RealMatrix matrix) {
        SchurTransformer schurTransform = new SchurTransformer(matrix);
        double[][] matT = schurTransform.getT().getData();

        this.realEigenvalues = new double[matT.length];
        this.imagEigenvalues = new double[matT.length];

        for (int i = 0; i < this.realEigenvalues.length; i++) {
            if (i == (this.realEigenvalues.length - 1) ||
                Precision.equals(matT[i + 1][i], 0.0, EigenDecomposition.EPSILON)) {
                this.realEigenvalues[i] = matT[i][i];
            } else {
                double x = matT[i + 1][i + 1];
                double p = 0.5 * (matT[i][i] - x);
                double z = FastMath.sqrt(FastMath.abs(p * p + matT[i + 1][i] * matT[i][i + 1]));
                this.realEigenvalues[i] = x + p;
                this.imagEigenvalues[i] = z;
                this.realEigenvalues[i + 1] = x + p;
                this.imagEigenvalues[i + 1] = -z;
                i++;
            }
        }
        return schurTransform;
    }

    /**
     * Performs a division of two complex numbers.
     *
     * @param xr real part of the first number
     * @param xi imaginary part of the first number
     * @param yr real part of the second number
     * @param yi imaginary part of the second number
     * @return result of the complex division
     */
    private Complex cdiv(double xr, double xi,
                         double yr, double yi) {
        return new Complex(xr, xi).divide(new Complex(yr, yi));
    }

    /**
     * Find eigenvectors from a matrix transformed to Schur form.
     *
     * @param schur the schur transformation of the matrix
     * @throws MathArithmeticException if the Schur form has a norm of zero
     */
    private void findEigenVectorsFromSchur(SchurTransformer schur)
            throws MathArithmeticException {
        double[][] matrixT = schur.getT().getData();
        double[][] matrixP = schur.getP().getData();

        int n = matrixT.length;

        // compute matrix norm
        double norm = 0.0;
        for (int i = 0; i < n; i++) {
            for (int j = FastMath.max(i - 1, 0); j < n; j++) {
                norm += FastMath.abs(matrixT[i][j]);
            }
        }

        // we can not handle a matrix with zero norm
        if (Precision.equals(norm, 0.0, EigenDecomposition.EPSILON)) {
            throw new MathArithmeticException(LocalizedFormats.ZERO_NORM);
        }

        // Backsubstitute to find vectors of upper triangular form

        double r = 0.0;
        double s = 0.0;
        double z = 0.0;

        for (int idx = n - 1; idx >= 0; idx--) {
            double p = this.realEigenvalues[idx];
            double q = this.imagEigenvalues[idx];

            if (Precision.equals(q, 0.0)) {
                // Real vector
                int l = idx;
                matrixT[idx][idx] = 1.0;
                for (int i = idx - 1; i >= 0; i--) {
                    double w = matrixT[i][i] - p;
                    r = 0.0;
                    for (int j = l; j <= idx; j++) {
                        r += matrixT[i][j] * matrixT[j][idx];
                    }
                    if (Precision.compareTo(this.imagEigenvalues[i], 0.0, EigenDecomposition.EPSILON) < 0) {
                        z = w;
                        s = r;
                    } else {
                        l = i;
                        if (Precision.equals(this.imagEigenvalues[i], 0.0)) {
                            if (w != 0.0) {
                                matrixT[i][idx] = -r / w;
                            } else {
                                matrixT[i][idx] = -r / (Precision.EPSILON * norm);
                            }
                        } else {
                            // Solve real equations
                            double x = matrixT[i][i + 1];
                            double y = matrixT[i + 1][i];
                            q = (this.realEigenvalues[i] - p) * (this.realEigenvalues[i] - p) +
                                this.imagEigenvalues[i] * this.imagEigenvalues[i];
                            double t = (x * s - z * r) / q;
                            matrixT[i][idx] = t;
                            if (FastMath.abs(x) > FastMath.abs(z)) {
                                matrixT[i + 1][idx] = (-r - w * t) / x;
                            } else {
                                matrixT[i + 1][idx] = (-s - y * t) / z;
                            }
                        }

                        // Overflow control
                        double t = FastMath.abs(matrixT[i][idx]);
                        if ((Precision.EPSILON * t) * t > 1) {
                            for (int j = i; j <= idx; j++) {
                                matrixT[j][idx] /= t;
                            }
                        }
                    }
                }
            } else if (q < 0.0) {
                // Complex vector
                int l = idx - 1;

                // Last vector component imaginary so matrix is triangular
                if (FastMath.abs(matrixT[idx][idx - 1]) > FastMath.abs(matrixT[idx - 1][idx])) {
                    matrixT[idx - 1][idx - 1] = q / matrixT[idx][idx - 1];
                    matrixT[idx - 1][idx] = -(matrixT[idx][idx] - p) / matrixT[idx][idx - 1];
                } else {
                    Complex result = cdiv(0.0, -matrixT[idx - 1][idx],
                            matrixT[idx - 1][idx - 1] - p, q);
                    matrixT[idx - 1][idx - 1] = result.getReal();
                    matrixT[idx - 1][idx] = result.getImaginary();
                }

                matrixT[idx][idx - 1] = 0.0;
                matrixT[idx][idx] = 1.0;

                for (int i = idx - 2; i >= 0; i--) {
                    double ra = 0.0;
                    double sa = 0.0;
                    for (int j = l; j <= idx; j++) {
                        ra += matrixT[i][j] * matrixT[j][idx - 1];
                        sa += matrixT[i][j] * matrixT[j][idx];
                    }
                    double w = matrixT[i][i] - p;

                    if (Precision.compareTo(this.imagEigenvalues[i], 0.0, EigenDecomposition.EPSILON) < 0) {
                        z = w;
                        r = ra;
                        s = sa;
                    } else {
                        l = i;
                        if (Precision.equals(this.imagEigenvalues[i], 0.0)) {
                            Complex c = cdiv(-ra, -sa, w, q);
                            matrixT[i][idx - 1] = c.getReal();
                            matrixT[i][idx] = c.getImaginary();
                        } else {
                            // Solve complex equations
                            double x = matrixT[i][i + 1];
                            double y = matrixT[i + 1][i];
                            double vr = (this.realEigenvalues[i] - p) * (this.realEigenvalues[i] - p) +
                                        this.imagEigenvalues[i] * this.imagEigenvalues[i] - q * q;
                            double vi = (this.realEigenvalues[i] - p) * 2.0 * q;
                            if (Precision.equals(vr, 0.0) && Precision.equals(vi, 0.0)) {
                                vr = Precision.EPSILON * norm *
                                     (FastMath.abs(w) + FastMath.abs(q) + FastMath.abs(x) +
                                      FastMath.abs(y) + FastMath.abs(z));
                            }
                            Complex c = cdiv(x * r - z * ra + q * sa,
                                    x * s - z * sa - q * ra, vr, vi);
                            matrixT[i][idx - 1] = c.getReal();
                            matrixT[i][idx] = c.getImaginary();

                            if (FastMath.abs(x) > (FastMath.abs(z) + FastMath.abs(q))) {
                                matrixT[i + 1][idx - 1] = (-ra - w * matrixT[i][idx - 1] +
                                                           q * matrixT[i][idx]) / x;
                                matrixT[i + 1][idx] = (-sa - w * matrixT[i][idx] -
                                                       q * matrixT[i][idx - 1]) / x;
                            } else {
                                Complex c2 = cdiv(-r - y * matrixT[i][idx - 1],
                                        -s - y * matrixT[i][idx], z, q);
                                matrixT[i + 1][idx - 1] = c2.getReal();
                                matrixT[i + 1][idx] = c2.getImaginary();
                            }
                        }

                        // Overflow control
                        double t = FastMath.max(FastMath.abs(matrixT[i][idx - 1]),
                                FastMath.abs(matrixT[i][idx]));
                        if ((Precision.EPSILON * t) * t > 1) {
                            for (int j = i; j <= idx; j++) {
                                matrixT[j][idx - 1] /= t;
                                matrixT[j][idx] /= t;
                            }
                        }
                    }
                }
            }
        }

        // Back transformation to get eigenvectors of original matrix
        for (int j = n - 1; j >= 0; j--) {
            for (int i = 0; i <= n - 1; i++) {
                z = 0.0;
                for (int k = 0; k <= FastMath.min(j, n - 1); k++) {
                    z += matrixP[i][k] * matrixT[k][j];
                }
                matrixP[i][j] = z;
            }
        }

        this.eigenvectors = new ArrayRealVector[n];
        double[] tmp = new double[n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                tmp[j] = matrixP[j][i];
            }
            this.eigenvectors[i] = new ArrayRealVector(tmp);
        }
    }

    /**
     * Specialized solver.
     */
    private static class Solver implements DecompositionSolver {
        /**
         * Real part of the realEigenvalues.
         */
        private final double[] realEigenvalues;
        /**
         * Imaginary part of the realEigenvalues.
         */
        private final double[] imagEigenvalues;
        /**
         * Eigenvectors.
         */
        private final ArrayRealVector[] eigenvectors;

        /**
         * Builds a solver from decomposed matrix.
         *
         * @param realEigenvalues Real parts of the eigenvalues.
         * @param imagEigenvalues Imaginary parts of the eigenvalues.
         * @param eigenvectors    Eigenvectors.
         */
        private Solver(double[] realEigenvalues,
                       double[] imagEigenvalues,
                       ArrayRealVector[] eigenvectors) {
            this.realEigenvalues = realEigenvalues;
            this.imagEigenvalues = imagEigenvalues;
            this.eigenvectors = eigenvectors;
        }

        /**
         * Solves the linear equation A &times; X = B for symmetric matrices A.
         * <p>
         * This method only finds exact linear solutions, i.e. solutions for which ||A &times; X - B|| is exactly 0.
         *
         * @param b Right-hand side of the equation A &times; X = B.
         * @return a Vector X that minimizes the two norm of A &times; X - B.
         * @throws DimensionMismatchException if the matrices dimensions do not match.
         * @throws SingularMatrixException    if the decomposed matrix is singular.
         */
        public RealVector solve(RealVector b) {
            if (!isNonSingular()) {
                throw new SingularMatrixException();
            }

            int m = this.realEigenvalues.length;
            if (b.getDimension() != m) {
                throw new DimensionMismatchException(b.getDimension(), m);
            }

            double[] bp = new double[m];
            for (int i = 0; i < m; ++i) {
                ArrayRealVector v = this.eigenvectors[i];
                double[] vData = v.getDataRef();
                double s = v.dotProduct(b) / this.realEigenvalues[i];
                for (int j = 0; j < m; ++j) {
                    bp[j] += s * vData[j];
                }
            }

            return new ArrayRealVector(bp, false);
        }

        /**
         * {@inheritDoc}
         */
        public RealMatrix solve(RealMatrix b) {

            if (!isNonSingular()) {
                throw new SingularMatrixException();
            }

            int m = this.realEigenvalues.length;
            if (b.getRowDimension() != m) {
                throw new DimensionMismatchException(b.getRowDimension(), m);
            }

            int nColB = b.getColumnDimension();
            double[][] bp = new double[m][nColB];
            double[] tmpCol = new double[m];
            for (int k = 0; k < nColB; ++k) {
                for (int i = 0; i < m; ++i) {
                    tmpCol[i] = b.getEntry(i, k);
                    bp[i][k] = 0;
                }
                for (int i = 0; i < m; ++i) {
                    ArrayRealVector v = this.eigenvectors[i];
                    double[] vData = v.getDataRef();
                    double s = 0;
                    for (int j = 0; j < m; ++j) {
                        s += v.getEntry(j) * tmpCol[j];
                    }
                    s /= this.realEigenvalues[i];
                    for (int j = 0; j < m; ++j) {
                        bp[j][k] += s * vData[j];
                    }
                }
            }

            return new Array2DRowRealMatrix(bp, false);

        }

        /**
         * Checks whether the decomposed matrix is non-singular.
         *
         * @return true if the decomposed matrix is non-singular.
         */
        public boolean isNonSingular() {
            double largestEigenvalueNorm = 0.0;
            // Looping over all values (in case they are not sorted in decreasing
            // order of their norm).
            for (int i = 0; i < this.realEigenvalues.length; ++i) {
                largestEigenvalueNorm = FastMath.max(largestEigenvalueNorm, eigenvalueNorm(i));
            }
            // Corner case: zero matrix, all exactly 0 eigenvalues
            if (largestEigenvalueNorm == 0.0) {
                return false;
            }
            for (int i = 0; i < this.realEigenvalues.length; ++i) {
                // Looking for eigenvalues that are 0, where we consider anything much much smaller
                // than the largest eigenvalue to be effectively 0.
                if (Precision.equals(eigenvalueNorm(i) / largestEigenvalueNorm, 0, EigenDecomposition.EPSILON)) {
                    return false;
                }
            }
            return true;
        }

        /**
         * @param i which eigenvalue to find the norm of
         * @return the norm of ith (complex) eigenvalue.
         */
        private double eigenvalueNorm(int i) {
            double re = this.realEigenvalues[i];
            double im = this.imagEigenvalues[i];
            return FastMath.sqrt(re * re + im * im);
        }

        /**
         * Get the inverse of the decomposed matrix.
         *
         * @return the inverse matrix.
         * @throws SingularMatrixException if the decomposed matrix is singular.
         */
        public RealMatrix getInverse() {
            if (!isNonSingular()) {
                throw new SingularMatrixException();
            }

            int m = this.realEigenvalues.length;
            double[][] invData = new double[m][m];

            for (int i = 0; i < m; ++i) {
                double[] invI = invData[i];
                for (int j = 0; j < m; ++j) {
                    double invIJ = 0;
                    for (int k = 0; k < m; ++k) {
                        double[] vK = this.eigenvectors[k].getDataRef();
                        invIJ += vK[i] * vK[j] / this.realEigenvalues[k];
                    }
                    invI[j] = invIJ;
                }
            }
            return MatrixUtils.createRealMatrix(invData);
        }
    }
}
