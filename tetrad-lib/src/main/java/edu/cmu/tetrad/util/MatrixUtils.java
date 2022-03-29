///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////
package edu.cmu.tetrad.util;

import cern.colt.matrix.impl.DenseDoubleMatrix1D;
import cern.colt.matrix.linalg.Property;
import org.apache.commons.math3.exception.NotStrictlyPositiveException;
import org.apache.commons.math3.exception.OutOfRangeException;
import org.apache.commons.math3.linear.*;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.lang.Math.sqrt;

/**
 * Class Matrix includes several public static functions performing matrix
 * operations. These function include: determinant, GJinverse, inverse,
 * multiple, difference, transpose, trace, duplicate, minor, identity, mprint,
 * impliedCovar, SEMimpliedCovar.
 *
 * @author Tianjiao Chu
 * @author Joseph Ramsey
 * @author Kevin V. Bui
 */
public final class MatrixUtils {

    /**
     * Make a repeat copy of matrix mat.
     *
     * @param mat     matrix to copy
     * @param nRow    number of repeat copy of row
     * @param mColumn number of repeat copy of column
     * @return
     */
    public static double[][] repmat(final double[][] mat, int nRow, final int mColumn) {
        final int numOfRow = mat.length;
        final double[][] repMat = new double[numOfRow * nRow][];
        for (int row = 0; row < numOfRow; row++) {
            repMat[row] = MatrixUtils.repeatCopyVector(mat[row], mColumn);
        }

        MatrixUtils.repeatCopyRow(repMat, --nRow, numOfRow, numOfRow);

        return repMat;
    }

    /**
     * Make a n repeat copy of the rows and columns of the matrix mat.
     *
     * @param mat
     * @param n   number of repeat copy
     * @return
     */
    public static double[][] repmat(final double[][] mat, int n) {
        final int numOfRow = mat.length;
        final double[][] repMat = new double[numOfRow * n][];
        for (int row = 0; row < numOfRow; row++) {
            repMat[row] = MatrixUtils.repeatCopyVector(mat[row], n);
        }

        MatrixUtils.repeatCopyRow(repMat, --n, numOfRow, numOfRow);
        return repMat;
    }

    /**
     * Repeat copy of rows.
     *
     * @param mat         matrix
     * @param n           number of repeat copy
     * @param startRowPos starting row
     * @param numOfRow    number of rows to copy nth times
     */
    private static void repeatCopyRow(final double[][] mat, final int n, int startRowPos, final int numOfRow) {
        for (int i = 0; i < n; i++) {
            for (int row = 0; row < numOfRow; row++) {
                final double[] src = mat[row];
                final double[] dest = new double[src.length];
                System.arraycopy(src, 0, dest, 0, src.length);

                mat[startRowPos++] = dest;
            }
        }
    }

    /**
     * Repeat copy of a vector.
     *
     * @param src vector
     * @param n   number of repeat copy
     * @return a new vector of n copy of the vector
     */
    private static double[] repeatCopyVector(final double[] src, final int n) {
        final double[] dest = new double[src.length * n];
        for (int i = 0; i < n; i++) {
            System.arraycopy(src, 0, dest, i * src.length, src.length);
        }

        return dest;
    }

    //=========================PUBLIC METHODS===========================//

    /**
     * Tests two matrices for equality.
     *
     * @param ma The first 2D matrix to check.
     * @param mb The second 2D matrix to check.
     * @return True iff the first and second matrices are equal.
     */
    public static boolean equals(final double[][] ma, final double[][] mb) {
        if (ma.length != mb.length) {
            return false;
        }
        for (int i = 0; i < ma.length; i++) {
            final double[] _ma = ma[i];
            final double[] _mb = mb[i];
            if (!Arrays.equals(_ma, _mb)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Tests two vectors for equality.
     *
     * @param va The first 1D matrix to check.
     * @param vb The second 1D matrix to check.
     * @return True iff the first and second matrices are equal.
     */
    public static boolean equals(final double[] va, final double[] vb) {
        return Arrays.equals(va, vb);
    }

    /**
     * Tests to see whether two matrices are equal within the given tolerance.
     * If any two corresponding elements differ by more than the given
     * tolerance, false is returned.
     *
     * @param ma        The first 2D matrix to check.
     * @param mb        The second 2D matrix to check.
     * @param tolerance A double >= 0.
     * @return Ibid.
     */
    public static boolean equals(final double[][] ma, final double[][] mb,
                                 final double tolerance) {
        return new Matrix(ma).equals(new Matrix(mb), tolerance);
        //new Property(tolerance).equals(TetradMatrix.instance(ma),
        //     TetradMatrix.instance(mb));
    }

    /**
     * Tests to see whether two vectors are equal within the given tolerance. If
     * any two corresponding elements differ by more than the given tolerance,
     * false is returned.
     *
     * @param va        The first matrix to check.
     * @param vb        The second matrix to check.
     * @param tolerance A double >= 0.
     * @return Ibid.
     */
    public static boolean equals(final double[] va, final double[] vb, final double tolerance) {
        return new Property(tolerance).equals(new DenseDoubleMatrix1D(va),
                new DenseDoubleMatrix1D(vb));
    }

    /**
     * @param m A 2D double matrix.
     * @return Ibid.
     */
    public static boolean isSquare(final double[][] m) {
        return new Matrix(m).isSquare();

    }

    /**
     * @param m         The matrix to check.
     * @param tolerance A double >= 0.
     * @return Ibid.
     */
    @SuppressWarnings({"SameParameterValue", "BooleanMethodIsAlwaysInverted"})
    public static boolean isSymmetric(final double[][] m, final double tolerance) {
        for (int i = 0; i < m.length; i++) {
            for (int j = i; j < m[0].length; j++) {
                if (Math.abs(m[i][j] - m[j][i]) > tolerance) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * @param m The matrix whose determinant is sought. Must be square.
     * @return Ibid.
     */
    public static double determinant(final double[][] m) {
        return new Matrix(m).det();
    }

    /**
     * A copy of the original (square) matrix with the stated index row/column
     * removed
     */
    public static double[][] submatrix(final double[][] m, final int rem) {
        final int[] indices = new int[m.length];

        int j = -1;
        for (int i = 0; i < m.length; i++) {
            j++;
            if (j == rem) {
                j++;
            }
            indices[i] = j;
        }

        return new Matrix(m).getSelection(indices,
                indices).toArray();
    }

    /**
     * @return the inverse of the given square matrix if it is nonsingular,
     * otherwise the pseudoinverse.
     */
    public static double[][] inverse(final double[][] m) {
        final Matrix mm = new Matrix(m);
        return mm.inverse().toArray();
    }

    public static double[][] pseudoInverse(final double[][] x) {
        final SingularValueDecomposition svd
                = new SingularValueDecomposition(new BlockRealMatrix(x));

        final RealMatrix U = svd.getU();
        final RealMatrix V = svd.getV();
        final RealMatrix S = svd.getS();

        for (int i = 0; i < S.getRowDimension(); i++) {
            for (int j = 0; j < S.getColumnDimension(); j++) {
                final double v = S.getEntry(i, j);
                S.setEntry(i, j, v == 0 ? 0.0 : 1.0 / v);
            }
        }

        return V.multiply(S.multiply(U.transpose())).getData();
    }

    /**
     * @return the outerProduct of ma and mb. The dimensions of ma and mb must
     * be compatible for multiplication.
     */
    public static double[][] product(final double[][] ma, final double[][] mb) {
        final Matrix d = new Matrix(ma);
        final Matrix e = new Matrix(mb);
        return d.times(e).toArray();
    }

    public static double[] product(final double[] ma, final double[][] mb) {
        return new Matrix(mb).transpose().times(new Vector(ma)).toArray();
    }

    public static Vector product(final Vector ma, final Matrix mb) {
        return mb.transpose().times(ma);
    }

    public static double[] product(final double[][] ma, final double[] mb) {
        return new Matrix(ma).times(new Vector(mb)).toArray();
    }

    public static double[][] outerProduct(final double[] ma, final double[] mb) {
        return TetradAlgebra.multOuter(new Vector(ma), new Vector(mb)).toArray();
    }

    public static double innerProduct(final double[] ma, final double[] mb) {
        return new Vector(ma).dotProduct(new Vector(mb));
    }

    /**
     * @return the transpose of the given matrix.
     */
    public static double[][] transpose(final double[][] m) {
        return new Matrix(m).transpose().toArray();
    }

    /**
     * @return the trace of the given (square) m.
     */
    public static double trace(final double[][] m) {
        return new Matrix(m).trace();
    }

    //Returns the sum of all values in a double matrix.
    public static double zSum(final double[][] m) {
        return new Matrix(m).zSum();
    }

    /**
     * @return the identity matrix of the given order.
     */
    public static double[][] identity(final int size) {
        return TetradAlgebra.identity(size).toArray();
    }

    /**
     * @return the sum of ma and mb.
     */
    public static double[][] sum(final double[][] ma, final double[][] mb) {
        Matrix _ma = new Matrix(ma);
        final Matrix _mb = new Matrix(mb);
        _ma = _ma.plus(_mb);
        return _ma.toArray();
    }

    public static double[] sum(final double[] ma, final double[] mb) {
        Vector _ma = new Vector(ma);
        final Vector _mb = new Vector(mb);
        _ma = _ma.plus(_mb);
        return _ma.toArray();
    }

    public static double[][] subtract(final double[][] ma, final double[][] mb) {
        Matrix _ma = new Matrix(ma);
        final Matrix _mb = new Matrix(mb);
        _ma = _ma.minus(_mb);
        return _ma.toArray();
    }

    public static double[] subtract(final double[] ma, final double[] mb) {
        Vector _ma = new Vector(ma);
        final Vector _mb = new Vector(mb);
        _ma = _ma.minus(_mb);
        return _ma.toArray();
    }

    /**
     * Computes the direct (Kronecker) outerProduct.
     */
    public static double[][] directProduct(final double[][] ma, final double[][] mb) {
        final int arow = ma.length;
        final int brow = mb.length;
        final int acol = ma[0].length;
        final int bcol = mb[0].length;

        final double[][] product = new double[arow * brow][acol * bcol];

        for (int i1 = 0; i1 < arow; i1++) {
            for (int j1 = 0; j1 < acol; j1++) {
                for (int i2 = 0; i2 < brow; i2++) {
                    for (int j2 = 0; j2 < bcol; j2++) {
                        final int i = i1 * brow + i2;
                        final int j = j1 * bcol + j2;
                        product[i][j] = ma[i1][j1] * mb[i2][j2];
                    }
                }
            }
        }

        return product;
    }

    /**
     * Multiplies the given matrix through by the given scalar.
     */
    public static double[][] scalarProduct(final double scalar, final double[][] m) {
        final Matrix _m = new Matrix(m);
        return _m.scalarMult(scalar).toArray();
    }

    public static double[] scalarProduct(final double scalar, final double[] m) {
        Vector _m = new Vector(m);
        _m = _m.scalarMult(scalar);
        return _m.toArray();
    }

    /**
     * Concatenates the vectors rows[i], i = 0...rows.length, into a single
     * vector.
     */
    public static double[] concatenate(final double[][] vectors) {
        final int numVectors = vectors.length;
        final int length = vectors[0].length;
        final double[] concat = new double[numVectors * length];

        for (int i = 0; i < vectors.length; i++) {
            System.arraycopy(vectors[i], 0, concat, i * length, length);
        }

        return concat;
    }

    /**
     * @return the vector as a 1 x n row matrix.
     */
    public static double[][] asRow(final double[] v) {
        final double[][] arr = new double[1][v.length];
        System.arraycopy(v, 0, arr[0], 0, v.length);
        return arr;
    }

    /**
     * @return the vector as an n x 1 column matrix.
     */
    private static double[][] asCol(final double[] v) {
        final double[][] arr = new double[v.length][1];
        for (int i = 0; i < v.length; i++) {
            arr[i][0] = v[i];
        }
        return arr;
    }

    /**
     * @param edgeCoef The edge covariance matrix. edgeCoef(i, j) is a parameter
     *                 in this matrix just in case i-->j is an edge in the model. All other
     *                 entries in the matrix are zero.
     * @param errCovar The error covariance matrix. errCovar(i, i) is the
     *                 variance of i; off-diagonal errCovar(i, j) are covariance parameters that
     *                 are specified in the model. All other matrix entries are zero.
     * @return The implied covariance matrix, which is the covariance matrix
     * over the measured variables that is implied by all the given information.
     * @throws IllegalArgumentException if edgeCoef or errCovar contains an
     *                                  undefined value (Double.NaN).
     */
    public static Matrix impliedCovar2(final Matrix edgeCoef, final Matrix errCovar) {
        if (MatrixUtils.containsNaN(edgeCoef)) {
            throw new IllegalArgumentException("Edge coefficient matrix must not "
                    + "contain undefined values. Probably the search put them "
                    + "there.");
        }

        if (MatrixUtils.containsNaN(errCovar)) {
            throw new IllegalArgumentException("Error covariance matrix must not "
                    + "contain undefined values. Probably the search put them "
                    + "there.");
        }

        final int sampleSize = 10000;

        final Matrix iMinusBInverse = TetradAlgebra.identity(edgeCoef.rows()).minus(edgeCoef).inverse();

        final Matrix sample = new Matrix(sampleSize, edgeCoef.columns());
        final Vector e = new Vector((edgeCoef.columns()));

        for (int i = 0; i < sampleSize; i++) {
            for (int j = 0; j < e.size(); j++) {
                e.set(j, RandomUtil.getInstance().nextNormal(0, errCovar.get(j, j)));
            }

            sample.assignRow(i, iMinusBInverse.times(e));
        }

        return sample.transpose().times(sample).scalarMult(1.0 / sampleSize);
    }

    public static Matrix impliedCovar(final Matrix edgeCoef, final Matrix errCovar) {
        if (MatrixUtils.containsNaN(edgeCoef)) {
            System.out.println(edgeCoef);
            throw new IllegalArgumentException("Edge coefficient matrix must not "
                    + "contain undefined values. Probably the search put them "
                    + "there.");
        }

        if (MatrixUtils.containsNaN(errCovar)) {
            throw new IllegalArgumentException("Error covariance matrix must not "
                    + "contain undefined values. Probably the search put them "
                    + "there.");
        }

//        TetradMatrix g = TetradMatrix.identity(edgeCoef.rows()).minus(edgeCoef);
//        return g.times(errCovar).times(g.transpose());
//        return g.transpose().times(errCovar).times(g);
        // I - B
        final Matrix m1 = Matrix.identity(edgeCoef.rows()).minus(edgeCoef);
//
//        // (I - B) ^ -1
        final Matrix m3 = m1.inverse();
//
//        // ((I - B) ^ -1)'
        final Matrix m4 = m3.transpose();
//
//        // ((I - B) ^ -1) Cov(e)
        final Matrix m5 = m3.times(errCovar);
//
//        // ((I - B) ^ -1) Cov(e) ((I - B) ^ -1)'
//
        return m5.times(m4);
    }

    private static boolean containsNaN(final Matrix m) {
        for (int i = 0; i < m.rows(); i++) {
            for (int j = 0; j < m.columns(); j++) {
                if (Double.isNaN(m.get(i, j))) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * @return vech of the given array. (This is what you get when you stack all
     * of the elements of m in the lower triangular of m to form a vector. The
     * elements are stacked in columns left to right, top to bottom.)
     */
    public static double[][] vech(final double[][] m) {
        if (!MatrixUtils.isSymmetric(m, 1.e-5)) {
            throw new IllegalArgumentException("m must be a symmetric matrix.");
        }

        final int order = m.length;
        final int vechSize = MatrixUtils.sum0ToN(order);
        final double[] vech = new double[vechSize];

        int index = -1;
        for (int i = 0; i < order; i++) {
            for (int j = i; j < order; j++) {
                vech[++index] = m[i][j];
            }
        }

        return MatrixUtils.asCol(vech);
    }

    /**
     * @return the symmetric matrix for which the given array is the vech.
     */
    public static double[][] invVech(final double[] vech) {

        final int order = MatrixUtils.vechOrder(vech);

        // Recreate the symmetric matrix.
        final double[][] m = new double[order][order];

        int index = -1;
        for (int i = 0; i < order; i++) {
            for (int j = i; j < order; j++) {
                ++index;
                m[i][j] = vech[index];
                m[j][i] = vech[index];
            }
        }

        return m;
    }

    /**
     * @return vech of the given array. (This is what you get when you stack all
     * of the elements of m to form a vector. The elements are stacked in
     * columns left to right, top to bottom.)
     */
    public static double[][] vec(final double[][] m) {
        assert MatrixUtils.isSquare(m);

        final int order = m.length;
        final int vecSize = order * order;
        final double[] vec = new double[vecSize];

        int index = -1;
        for (final double[] aM : m) {
            for (int j = 0; j < order; j++) {
                vec[++index] = aM[j];
            }
        }

        return MatrixUtils.asCol(vec);
    }

    /**
     * @return the sum of integers from 0 up to n.
     */
    public static int sum0ToN(final int n) {
        if (n < 0) {
            throw new IllegalArgumentException("Argument must be >= 0: " + n);
        }

        return n * (n + 1) / 2;
    }

    /**
     * The matrix which, when postmultiplied by vech, return vec.
     *
     * @param n the size of the square matrix that vec and vech come from.
     */
    public static double[][] vechToVecLeft(final int n) {
        final int row = n * n;
        final int col = MatrixUtils.sum0ToN(n);
        final double[][] m = new double[row][col];

        int index = -1;
        for (int i = 0; i < n; i++) {
            for (int j = i; j < n; j++) {
                int _row = i * n + j;
                int _col = ++index;
                m[_row][_col] = 1.0;
                _row = j * n + i;
                _col = index;
                m[_row][_col] = 1.0;
            }
        }

        return m;
    }

    /**
     * @return true just in case the given matrix has the given dimensions
     * --that is, just in case m.length == i and m[0].length == j.
     */
    public static boolean hasDimensions(final double[][] m, final int i, final int j) {
        final Matrix _m = new Matrix(m);
        return _m.rows() == i && _m.columns() == j;
    }

    public static double[][] zeros(final int rows, final int cols) {
        return new Matrix(rows, cols).toArray();
    }

    /**
     * Return true if the given matrix is symmetric positive definite--that is,
     * if it would make a valid covariance matrix.
     */
    @SuppressWarnings({"BooleanMethodIsAlwaysInverted"})
    public static boolean isPositiveDefinite(final Matrix matrix) {
//        DoubleMatrix2D _matrix = new DenseDoubleMatrix2D(matrix.toArray());
//        System.out.println(MatrixUtils.toString(new CholeskyDecomposition(_matrix).getL().toArray()));
//        return new CholeskyDecomposition(_matrix).isSymmetricPositiveDefinite();

        try {
            new RectangularCholeskyDecomposition(new BlockRealMatrix(matrix.toArray()));
        } catch (final NonPositiveDefiniteMatrixException e) {
            return false;
        }

        return true;
    }

    public static Matrix cholesky(final Matrix covar) {
        final RealMatrix L = new org.apache.commons.math3.linear.CholeskyDecomposition(new BlockRealMatrix(covar.toArray())).getL();
        return new Matrix(L.getData());

//        DoubleMatrix2D _covar = new DenseDoubleMatrix2D(covar.toArray());
//        DoubleMatrix2D l = new CholeskyDecomposition(_covar).getL();
//        return new TetradMatrix(l.toArray());
    }

    /**
     * Converts a covariance matrix to a correlation matrix in place; the same
     * matrix is returned for convenience, but m is modified in the process.
     */
    public static Matrix convertCovToCorr(final Matrix m) {
        if (m.rows() != m.columns()) throw new IllegalArgumentException("Not a square matrix.");
        if (!MatrixUtils.isSymmetric(m.toArray(), 0.001)) {
            throw new IllegalArgumentException("Not symmetric with tolerance " + 0.001);
        }

        final Matrix corr = m.like();

        for (int i = 0; i < m.rows(); i++) {
            for (int j = i + 1; j < m.columns(); j++) {
                final double v = m.get(i, j) / sqrt(m.get(i, i) * m.get(j, j));
                corr.set(i, j, v);
                corr.set(j, i, v);
            }
        }

        for (int i = 0; i < m.columns(); i++) {
            corr.set(i, i, 1.0);
        }

        return corr;
    }

    /**
     * Converts a matrix in lower triangular form to a symmetric matrix in
     * square form. The lower triangular matrix need not contain matrix elements
     * to represent elements in the upper triangle.
     */
    public static double[][] convertLowerTriangleToSymmetric(final double[][] arr) {
        final int size = arr.length;
        final double[][] m = new double[size][size];

        for (int i = 0; i < size; i++) {
            for (int j = 0; j <= i; j++) {
                m[i][j] = arr[i][j];
                m[j][i] = arr[i][j];
            }
        }

        return m;
    }

    /**
     * Copies the given array, using a standard scientific notation number
     * formatter and beginning each line with a tab character. The number format
     * is DecimalFormat(" 0.0000;-0.0000").
     */
    public static String toString(final double[][] m) {
        final NumberFormat nf = new DecimalFormat(" 0.0000;-0.0000");
        return MatrixUtils.toString(m, nf);
    }

    public static String toString(final double[][] m, final List<String> variables) {
        final NumberFormat nf = new DecimalFormat(" 0.0000;-0.0000");
        return MatrixUtils.toString(m, nf, variables);
    }

    private static String toString(final double[][] m, final NumberFormat nf) {
        return MatrixUtils.toString(m, nf, null);
    }

    /**
     * Copies the given array, using a standard scientific notation number
     * formatter and beginning each line with the given lineInit. The number
     * format is DecimalFormat(" 0.0000;-0.0000").
     */
    private static String toString(final double[][] m, final NumberFormat nf, List<String> variables) {
        final String result;
        if (nf == null) {
            throw new NullPointerException("NumberFormat must not be null.");
        }

        if (variables == null) {
            variables = new ArrayList<>();

            if (m.length > 0) {
                for (int i = 0; i < m[0].length; i++) {
                    variables.add("V" + (i + 1));
                }
            }
        }

        if (m == null) {
            result = MatrixUtils.nullMessage();
        } else if (m.length > 0) {
            final TextTable textTable = new TextTable(m.length + 1, m[0].length);

            for (int i = 0; i < variables.size(); i++) {
                textTable.setToken(0, i, variables.get(i));
            }

            for (int i = 0; i < m.length; i++) {
                for (int j = 0; j < m[0].length; j++) {
                    textTable.setToken(i + 1, j, m[i][j] == 0 ? " " : nf.format(m[i][j]));
                }
            }

            result = "\n" + textTable;
        } else {
            result = MatrixUtils.nullMessage();
        }

        return result;
    }

    public static String toStringSquare(final double[][] m, final List<String> variables) {
        final NumberFormat nf = new DecimalFormat(" 0.0000;-0.0000");
        return MatrixUtils.toStringSquare(m, nf, variables);
    }

    public static String toStringSquare(final double[][] m, final NumberFormat nf, List<String> variables) {
        final String result;
        if (nf == null) {
            throw new NullPointerException("NumberFormat must not be null.");
        }

        if (variables == null) {
            variables = new ArrayList<>();

            for (int i = 0; i < m.length; i++) {
                variables.add("V" + (i + 1));
            }
        }

        if (m == null) {
            result = MatrixUtils.nullMessage();
        } else {
            final TextTable textTable = new TextTable(m.length + 1, m[0].length + 1);

            for (int i = 0; i < variables.size(); i++) {
                textTable.setToken(0, i + 1, variables.get(i));
            }

            for (int i = 0; i < m.length; i++) {
                textTable.setToken(i + 1, 0, variables.get(i));

                for (int j = 0; j < m[0].length; j++) {
                    textTable.setToken(i + 1, j + 1, nf.format(m[i][j]));
                }
            }

            result = "\n" + textTable;
        }
        return result;
    }

    public static String toString(final int[] m) {
        final StringBuilder buf = new StringBuilder();

        for (final int aM : m) {
            buf.append(aM).append("\t");
        }

        return buf.toString();
    }

    public static String toString(final int[][] m, List<String> variables) {
        final String result;

        if (variables == null) {
            variables = new ArrayList<>();

            for (int i = 0; i < m.length; i++) {
                variables.add("V" + (i + 1));
            }
        }

        if (m == null) {
            result = MatrixUtils.nullMessage();
        } else {
            final TextTable textTable = new TextTable(m.length + 1, m[0].length);

            for (int i = 0; i < variables.size(); i++) {
                textTable.setToken(0, i, variables.get(i));
            }

            for (int i = 0; i < m.length; i++) {
                for (int j = 0; j < m[0].length; j++) {
                    textTable.setToken(i + 1, j, Integer.toString(m[i][j]));
                }
            }

            result = "\n" + textTable;
        }

        return result;
    }

    public static String toStringSquare(final int[][] m, List<String> variables) {
        final String result;

        if (m == null) {
            result = MatrixUtils.nullMessage();
        } else {
            if (variables == null) {
                variables = new ArrayList<>();

                for (int i = 0; i < m.length; i++) {
                    variables.add("V" + (i + 1));
                }
            }

            final TextTable textTable = new TextTable(m.length + 1, m[0].length + 1);

            for (int i = 0; i < variables.size(); i++) {
                textTable.setToken(0, i + 1, variables.get(i));
            }

            for (int i = 0; i < m.length; i++) {
                textTable.setToken(i + 1, 0, variables.get(i));

                for (int j = 0; j < m[0].length; j++) {
                    textTable.setToken(i + 1, j + 1, Integer.toString(m[i][j]));
                }
            }

            result = "\n" + textTable;
        }
        return result;
    }

    public static String toString(final double[] m) {
        final StringBuilder buf = new StringBuilder();

        for (final double aM : m) {
            buf.append(aM).append("\t");
        }

        return buf.toString();
    }

    public static String toString(final double[] m, final NumberFormat nf) {
        final StringBuilder buf = new StringBuilder();

        for (final double aM : m) {
            buf.append(nf.format(aM)).append("\t");
        }

        return buf.toString();
    }

    public static String toString(final int[][] m) {
        final TextTable textTable = new TextTable(m.length, m[0].length);

        for (int i = 0; i < m.length; i++) {
            for (int j = 0; j < m[0].length; j++) {
                textTable.setToken(i, j, Integer.toString(m[i][j]));
            }
        }

        return "\n" + textTable;
    }

    /**
     * Copies the given array, starting each line with a tab character..
     */
    public static String toString(final boolean[][] m) {
        final String result;

        if (m == null) {
            result = MatrixUtils.nullMessage();
        } else {
            final TextTable textTable = new TextTable(m.length, m[0].length);

            for (int i = 0; i < m.length; i++) {
                for (int j = 0; j < m[0].length; j++) {
                    textTable.setToken(i, j, Boolean.toString(m[i][j]));
                }
            }

            result = "\n" + textTable;
        }
        return result;
    }

    //=========================PRIVATE METHODS===========================//
    private static String nullMessage() {
        return "\n"
                + "\t"
                + "<Matrix is null>";
    }

    /**
     * @return the order of the matrix for which this is the vech.
     * @throws IllegalArgumentException in case this matrix does not have
     *                                  dimension n x 1 for some n = 0 + 1 + 2 + ... + k for some k.
     */
    private static int vechOrder(final double[] vech) {
        int difference = vech.length;
        int order = 0;
        while (difference > 0) {
            difference -= (++order);
            if (difference < 0) {
                throw new IllegalArgumentException(
                        "Illegal length for vech: " + vech.length);
            }
        }
        return order;
    }

    public static int[] copyOf(final int[] arr, final int length) {
        final int[] copy = new int[arr.length];
        System.arraycopy(arr, 0, copy, 0, length);
        return copy;
    }

    public static double[][] copyOf(final double[][] arr) {
        final double[][] copy = new double[arr.length][arr[0].length];

        for (int i = 0; i < arr.length; i++) {
            System.arraycopy(arr[i], 0, copy[i], 0, arr[0].length);
        }

        return copy;
    }

    public static RealMatrix transposeWithoutCopy(final RealMatrix apacheData) {
        return new AbstractRealMatrix(apacheData.getColumnDimension(), apacheData.getRowDimension()) {
            @Override
            public int getRowDimension() {
                return apacheData.getColumnDimension();
            }

            @Override
            public int getColumnDimension() {
                return apacheData.getRowDimension();
            }

            @Override
            public RealMatrix createMatrix(final int rowDimension, final int columnDimension) throws NotStrictlyPositiveException {
                return apacheData.createMatrix(rowDimension, columnDimension);
            }

            @Override
            public RealMatrix copy() {
                throw new IllegalArgumentException("Can't copy");
            }

            @Override
            public double getEntry(final int i, final int j) throws OutOfRangeException {
                //                throw new UnsupportedOperationException();
                return apacheData.getEntry(j, i);
            }

            @Override
            public void setEntry(final int i, final int j, final double v) throws OutOfRangeException {
                throw new UnsupportedOperationException();
            }
        };
    }
}
