/// ////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
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
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.util;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.math3.util.FastMath;
import org.ejml.dense.row.CommonOps_DDRM;
import org.ejml.dense.row.NormOps_DDRM;
import org.ejml.simple.SimpleMatrix;
import org.ejml.simple.SimpleSVD;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;

/**
 * Wraps the EJML linear algebra library for most uses in Tetrad. Specialized uses will still have to use the library
 * directly. One issue is that we need to be able to represent empty matrices gracefully; this case is handled
 * separately and incorporated into the class.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class Matrix implements TetradSerializable {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The `data` variable represents a matrix structure used for numerical computations and data representation. It is
     * an immutable instance of the `SimpleMatrix` class that supports various matrix operations.
     */
    private final SimpleMatrix data;

    /**
     * The number of rows.
     */
    private final int m;

    /**
     * The number of columns.
     */
    private final int n;

    /**
     * The view of the matrix. This is used to allow a subset of the matrix to be viewed and set.
     */
    private final MView matrixView;

    /**
     * <p>Constructor for Matrix.</p>
     *
     * @param data an array of  objects
     */
    public Matrix(double[][] data) {
        if (data.length == 0) {
            this.data = new SimpleMatrix(0, 0);
        } else {
            this.data = new SimpleMatrix(data);
        }

        this.m = data.length;
        this.n = this.m == 0 ? 0 : data[0].length;
        this.matrixView = new MView(this);
    }

    /**
     * Constructs a Matrix object based on the given SimpleMatrix data.
     *
     * @param data the SimpleMatrix object containing the data to initialize the matrix
     */
    public Matrix(SimpleMatrix data) {
        this.data = data.copy();

        this.m = data.getNumRows();
        this.n = data.getNumCols();
        this.matrixView = new MView(this);
    }

    /**
     * <p>Constructor for Matrix.</p>
     *
     * @param m a int
     * @param n a int
     */
    public Matrix(int m, int n) {
        if (m == 0 || n == 0) {
            this.data = new SimpleMatrix(0, 0);
        } else {
            this.data = new SimpleMatrix(m, n);
        }

        this.m = m;
        this.n = n;
        this.matrixView = new MView(this);
    }

    /**
     * <p>Constructor for Matrix.</p>
     *
     * @param m a {@link edu.cmu.tetrad.util.Matrix} object
     */
    public Matrix(Matrix m) {
        this(m.getData().copy());
    }

    /**
     * <p>identity.</p>
     *
     * @param rows a int
     * @return a {@link edu.cmu.tetrad.util.Matrix} object
     */
    public static Matrix identity(int rows) {
        Matrix m = new Matrix(rows, rows);
        for (int i = 0; i < rows; i++) m.set(i, i, 1);
        return m;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetrad.util.Matrix} object
     */
    public static Matrix serializableInstance() {
        return new Matrix(0, 0);
    }

    /**
     * Assigns a part of the given matrix to a specified submatrix while adding the values to the existing data. The
     * values from the 'from' matrix are added to the corresponding elements of the submatrix defined by the ranges.
     *
     * @param range1 an array of integers representing the row indices of the submatrix to which the values will be
     *               assigned
     * @param range2 an array of integers representing the column indices of the submatrix to which the values will be
     *               assigned
     * @param from   a Matrix object providing the values to be added to the specified submatrix
     */
    public void assignPart(int[] range1, int[] range2, Matrix from) {
        for (int j = 0; j < range1.length; j++) {
            for (int k = 0; k < range2.length; k++) {
                getData().set(range1[j], range2[k], from.get(j, k) + getData().get(range1[j], range2[k]));
            }
        }
    }

    /**
     * <p>assign.</p>
     *
     * @param matrix a {@link edu.cmu.tetrad.util.Matrix} object
     */
    public void assign(Matrix matrix) {
        if (getData().getNumRows() != matrix.getNumRows() || getData().getNumCols() != matrix.getNumColumns()) {
            throw new IllegalArgumentException("Mismatched matrix size.");
        }

        for (int i = 0; i < getData().getNumRows(); i++) {
            for (int j = 0; j < getData().getNumCols(); j++) {
                getData().set(i, j, matrix.get(i, j));
            }
        }
    }

    /**
     * <p>getNumColumns.</p>
     *
     * @return a int
     */
    public int getNumColumns() {
        return this.n;
    }

    /**
     * <p>diag.</p>
     *
     * @return a {@link edu.cmu.tetrad.util.Vector} object
     */
    public Vector diag() {
        double[] diag = new double[getData().getNumRows()];

        for (int i = 0; i < getData().getNumRows(); i++) {
            diag[i] = getData().get(i, i);
        }

        return new Vector(diag);
    }

    /**
     * <p>copy.</p>
     *
     * @return a {@link edu.cmu.tetrad.util.Matrix} object
     */
    public Matrix copy() {
        if (zeroDimension()) return new Matrix(getNumRows(), getNumColumns());
        return new Matrix(getData().copy());
    }

    /**
     * <p>getColumn.</p>
     *
     * @param j a int
     * @return a {@link edu.cmu.tetrad.util.Vector} object
     */
    public Vector getColumn(int j) {
        if (zeroDimension()) {
            return new Vector(getNumRows());
        }

        return new Vector(getData().getColumn(j));
    }

    /**
     * <p>times.</p>
     *
     * @param m a {@link edu.cmu.tetrad.util.Matrix} object
     * @return a {@link edu.cmu.tetrad.util.Matrix} object
     */
    public Matrix times(Matrix m) {
        if (this.zeroDimension() || m.zeroDimension())
            return new Matrix(this.getNumRows(), m.getNumColumns());
        else {
            return new Matrix(getData().mult(m.getData()));
        }
    }

    /**
     * <p>times.</p>
     *
     * @param v a {@link edu.cmu.tetrad.util.Vector} object
     * @return a {@link edu.cmu.tetrad.util.Vector} object
     */
    public Vector times(Vector v) {
        if (v.size() != getData().getNumCols()) {
            throw new IllegalArgumentException("Mismatched dimensions.");
        }

        double[] y = new double[getData().getNumRows()];

        for (int i = 0; i < getData().getNumRows(); i++) {
            double sum = 0.0;

            for (int j = 0; j < getData().getNumCols(); j++) {
                sum += getData().get(i, j) * v.get(j);
            }

            y[i] = sum;
        }

        return new Vector(y);
    }

    /**
     * <p>toArray.</p>
     *
     * @return an array of  objects
     */
    public double[][] toArray() {
        return getData().toArray2();
    }

    /**
     * <p>get.</p>
     *
     * @param i a int
     * @param j a int
     * @return a double
     */
    public double get(int i, int j) {
        return getData().get(i, j);
    }

    /**
     * <p>like.</p>
     *
     * @return a {@link edu.cmu.tetrad.util.Matrix} object
     */
    public Matrix like() {
        return new Matrix(getData().getNumRows(), getData().getNumCols());
    }

    /**
     * <p>set.</p>
     *
     * @param i a int
     * @param j a int
     * @param v a double
     */
    public void set(int i, int j, double v) {
        getData().set(i, j, v);
    }

    /**
     * <p>getRow.</p>
     *
     * @param i a int
     * @return a {@link edu.cmu.tetrad.util.Vector} object
     */
    public Vector row(int i) {
        MView mView = viewRow(i);
        return mView.vector();
    }

    /**
     * Retrieves the specified column from a data structure as a Vector object.
     *
     * @param i the index of the column to retrieve
     * @return a Vector representing the specified column
     */
    public Vector col(int i) {
        return viewColumn(i).vector();
    }

    /**
     * Extracts a submatrix from the current matrix based on the specified row and column ranges.
     *
     * @param i the starting row index (inclusive) of the submatrix
     * @param j the ending row index (inclusive) of the submatrix
     * @param k the starting column index (inclusive) of the submatrix
     * @param l the ending column index (inclusive) of the submatrix
     * @return a new Matrix instance representing the extracted submatrix
     */
    public Matrix getPart(int i, int j, int k, int l) {
        return viewPart(i, j, k, l).mat();
    }

    /**
     * Returns the inverse of the matrix. If the matrix is not square, an exception is thrown. If the matrix is
     * singular, an exception is thrown.
     *
     * @return a {@link edu.cmu.tetrad.util.Matrix} object
     */
    public Matrix inverse() {
        if (getData().getNumRows() != getData().getNumCols()) {
            throw new IllegalArgumentException("Matrix not square.");
        }

        if (getData().getNumRows() == 0) {
            return new Matrix(0, 0);
        }

        SimpleMatrix inverse = new SimpleMatrix(getNumRows(), getNumColumns());
        CommonOps_DDRM.invert(data.getDDRM(), inverse.getDDRM());

        return new Matrix(inverse);
    }

    /**
     * Returns the Moore-Penrose pseudoinverse of the matrix.
     *
     * @return a {@link edu.cmu.tetrad.util.Matrix} object
     */
    public Matrix pseudoinverse() {

        // Note to self: If this pseudoinverse throws an exception, check to make sure the matrix being inverted
        // does not contain NaNs. The pseudoinverse will not work if the matrix contains NaNs. jdramsey 2025-1-26

        if (zeroDimension()) return new Matrix(getNumColumns(), getNumRows());
        return new Matrix(getData().pseudoInverse());
    }

    /**
     * Calculates and returns the appropriate inverse of the matrix based on the provided lambda value:
     * <ul>
     *     <li>If lambda is <b>0.0</b>, the inverse of the matrix is computed using the standard inversion method.</li>
     *     <li>If lambda is <b>greater than 0.0</b>, the matrix is regularized by adding lambda to the diagonal,
     *         ensuring numerical stability for near-singular matrices, and then the inverse is computed.</li>
     *     <li>If lambda is <b>less than 0.0</b>, the Moore-Penrose pseudoinverse is computed, which is useful for
     *         non-square or singular matrices.</li>
     * </ul>
     * This method is designed to handle a variety of inverse computations depending on the requirements, such as
     * regularization or handling singular matrices.
     *
     * @param lambda A value controlling the method of inversion:
     *               <ul>
     *                   <li><code>0.0</code>: Standard matrix inversion.</li>
     *                   <li><code>&gt; 0.0</code>: Regularized inversion for numerical stabilit. A small value
     *                   should be chosen.</li>
     *                   <li><code>&lt; 0.0</code>: Moore-Penrose pseudoinverse.</li>
     *               </ul>
     * @return The calculated inverse of the matrix based on the specified lambda value.
     * @throws IllegalArgumentException If lambda is neither -1.0, 0.0, nor a positive value.
     */
    public Matrix chooseInverse(double lambda) {
        boolean isSquare = getNumRows() == getNumColumns();

        for (int i = 0; i < getData().getNumRows(); i++) {
            for (int j = 0; j < getData().getNumCols(); j++) {
                if (Double.isNaN(getData().get(i, j))) {
                    throw new IllegalArgumentException("Cannot invert a matrix with non-NaN values.");
                }
            }
        }

        if (lambda == 0.0 && isSquare) {
            try {
                return inverse();
            } catch (Exception e) {
                throw new RuntimeException("Trying to invert a matrix with singularities or NaNs; try regularizing or taking pseudoinverses", e);
            }
        } else if (lambda > 0.0 && isSquare) {
            return regularize(lambda).inverse();
        } else {
            return pseudoinverse();
        }
    }

    /**
     * Regularizes the diagonal of the matrix by adding a scaled identity matrix to it.
     *
     * @param lambda the scalar value used to scale the identity matrix before adding it to the current matrix
     * @return a new Matrix object representing the result of adding the scaled identity matrix to the current matrix
     */
    public Matrix regularize(double lambda) {
        Matrix identity = Matrix.identity(getNumRows());
        return this.plus(identity.scale(lambda));
    }

    /**
     * <p>assignRow.</p>
     *
     * @param row     a int
     * @param doubles a {@link edu.cmu.tetrad.util.Vector} object
     */
    public void assignRow(int row, Vector doubles) {
        getData().setRow(row, doubles.getSimpleMatrix().getColumn(0));
    }

    /**
     * <p>assignColumn.</p>
     *
     * @param col     a int
     * @param doubles a {@link edu.cmu.tetrad.util.Vector} object
     */
    public void assignColumn(int col, Vector doubles) {
        getData().setColumn(col, doubles.getSimpleMatrix());
    }

    /**
     * <p>trace.</p>
     *
     * @return a double
     */
    public double trace() {
        return getData().trace();
    }

    /**
     * <p>det.</p>
     *
     * @return a double
     */
    public double det() {
        if (zeroDimension()) return 0;
        return getData().determinant();
    }

    /**
     * <p>transpose.</p>
     *
     * @return a {@link edu.cmu.tetrad.util.Matrix} object
     */
    public Matrix transpose() {
        if (zeroDimension()) return new Matrix(getNumColumns(), getNumRows());
        return new Matrix(getData().transpose());
    }

    /**
     * <p>equals.</p>
     *
     * @param m         a {@link edu.cmu.tetrad.util.Matrix} object
     * @param tolerance a double
     * @return a boolean
     */
    public boolean equals(Matrix m, double tolerance) {
        for (int i = 0; i < getData().getNumRows(); i++) {
            for (int j = 0; j < getData().getNumCols(); j++) {
                if (FastMath.abs(getData().get(i, j) - m.getData().get(i, j)) > tolerance) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * <p>isSquare.</p>
     *
     * @return a boolean
     */
    public boolean isSquare() {
        return getNumRows() == getNumColumns();
    }

    /**
     * <p>minus.</p>
     *
     * @param mb a {@link edu.cmu.tetrad.util.Matrix} object
     * @return a {@link edu.cmu.tetrad.util.Matrix} object
     */
    public Matrix minus(Matrix mb) {
        if (mb.getNumRows() == 0 || mb.getNumColumns() == 0) return this;
        return new Matrix(getData().minus(mb.getData()));
    }

    /**
     * <p>norm1.</p>
     *
     * @return a double
     */
    public double norm1() {
        return NormOps_DDRM.normP1(getData().getDDRM());
    }


    /**
     * <p>plus.</p>
     *
     * @param mb a {@link edu.cmu.tetrad.util.Matrix} object
     * @return a {@link edu.cmu.tetrad.util.Matrix} object
     */
    public Matrix plus(Matrix mb) {
        if (mb.getNumRows() == 0 || mb.getNumColumns() == 0) return this;
        return new Matrix(getData().plus(mb.getData()));
    }

    /**
     * <p>rank.</p>
     *
     * @return a int
     */
    public int rank() {
        return getData().svd().rank();
    }

    /**
     * <p>getNumRows.</p>
     *
     * @return a int
     */
    public int getNumRows() {
        return this.m;
    }

    /**
     * <p>scalarMult.</p>
     *
     * @param scalar a double
     * @return a {@link edu.cmu.tetrad.util.Matrix} object
     */
    public Matrix scale(double scalar) {
        Matrix newMatrix = copy();
        for (int i = 0; i < getNumRows(); i++) {
            for (int j = 0; j < getNumColumns(); j++) {
                newMatrix.set(i, j, get(i, j) * scalar);
            }
        }

        return newMatrix;
    }

    /**
     * Adds the specified scalar value to each element of the matrix and returns the resulting matrix.
     *
     * @param scalar the scalar value to be added to each element of the matrix
     * @return a new Matrix instance where each element is the sum of the corresponding element in the original matrix
     * and the scalar value
     */
    public Matrix scalarPlus(double scalar) {
        Matrix newMatrix = copy();
        for (int i = 0; i < getNumRows(); i++) {
            for (int j = 0; j < getNumColumns(); j++) {
                newMatrix.set(i, j, get(i, j) + scalar);
            }
        }

        return newMatrix;
    }

    /**
     * <p>sqrt.</p>
     *
     * @return a {@link edu.cmu.tetrad.util.Matrix} object
     */
    public Matrix sqrt() {
        // Perform Singular Value Decomposition (SVD)
        SimpleSVD<SimpleMatrix> svd = getData().svd();

        // Get U, W, and V matrices from SVD
        SimpleMatrix U = svd.getU();
        SimpleMatrix W = svd.getW(); // Singular values
        SimpleMatrix V = svd.getV();

        // Compute square root of W (singular values)
        for (int i = 0; i < W.getNumRows(); i++) {
            W.set(i, i, Math.sqrt(W.get(i, i)));
        }

        // Reconstruct the square root matrix
        SimpleMatrix sqrtMatrix = U.mult(W).mult(V.transpose());

        return new Matrix(sqrtMatrix);
    }

    /**
     * <p>sum.</p>
     *
     * @param direction a int
     * @return a {@link edu.cmu.tetrad.util.Vector} object
     */
    public Vector sum(int direction) {
        if (direction == 1) {
            Vector sums = new Vector(getNumColumns());

            for (int j = 0; j < getNumColumns(); j++) {
                double sum = 0.0;

                for (int i = 0; i < getNumRows(); i++) {
                    sum += getData().get(i, j);
                }

                sums.set(j, sum);
            }

            return sums;
        } else if (direction == 2) {
            Vector sums = new Vector(getNumRows());

            for (int i = 0; i < getNumRows(); i++) {
                double sum = 0.0;

                for (int j = 0; j < getNumColumns(); j++) {
                    sum += getData().get(i, j);
                }

                sums.set(i, sum);
            }

            return sums;
        } else {
            throw new IllegalArgumentException("Expecting 1 (sum columns) or 2 (sum rows).");
        }
    }

    /**
     * Creates a view of a matrix using the specified row and column indices.
     *
     * @param range1 an array of row indices to include in the view. Each index must be within the range [0, number of
     *               rows).
     * @param range2 an array of column indices to include in the view. Each index must be within the range [0, number
     *               of columns).
     * @return a MatrixView object representing the specified subset of the matrix.
     * @throws IllegalArgumentException if any row index in range1 is out of bounds or any column index in range2 is out
     *                                  of bounds.
     */
    public MView view(int[] range1, int[] range2) {
        // Check that the ranges are valid.
        for (int i : range1) {
            if (i < 0 || i > getNumRows()) {
                throw new IllegalArgumentException("Invalid row index: " + i);
            }
        }

        for (int i : range2) {
            if (i < 0 || i > getNumColumns()) {
                throw new IllegalArgumentException("Invalid column index: " + i);
            }
        }

        return new MView(matrixView, range1, range2);
    }

    /**
     * Creates a view of the entire matrix.
     *
     * @return a MatrixView object representing the entire matrix.
     */
    public MView view() {
        return new MView(this);
    }

    /**
     * Creates a sub-view of the matrix representing a specific row.
     *
     * @param row the index of the row to be viewed
     * @return a MatrixView object representing the specified row
     */
    public MView viewRow(int row) {
        Pair<int[], int[]> ranges = ranges(row, row + 1, 0, getNumColumns());
        return new MView(matrixView, ranges.getLeft(), ranges.getRight());
    }


    /**
     * Creates a view of a specific column from the matrix.
     *
     * @param column the index of the column to be viewed
     * @return a MatrixView object representing the specified column
     */
    public MView viewColumn(int column) {
        Pair<int[], int[]> ranges = ranges(0, getNumRows(), column, column + 1);
        return new MView(matrixView, ranges.getLeft(), ranges.getRight());
    }

    /**
     * Creates a view of a specified submatrix defined by the given row and column ranges.
     *
     * @param fromRow    the starting row index (inclusive) of the submatrix
     * @param fromColumn the starting column index (inclusive) of the submatrix
     * @param toRow      the ending row index (exclusive) of the submatrix
     * @param toColumn   the ending column index (exclusive) of the submatrix
     * @return a MatrixView object representing the specified submatrix view
     */
    public MView viewPart(int fromRow, int toRow, int fromColumn, int toColumn) {
        Pair<int[], int[]> ranges = ranges(fromRow, toRow, fromColumn, toColumn);
        return new MView(matrixView, ranges.getLeft(), ranges.getRight());
    }

    /**
     * Generates an array of integers representing a range of values between two specified row indices, inclusive.
     *
     * @param from  the starting row index (inclusive) of the range
     * @param toRow the ending row index (inclusive) of the range
     * @return an array of integers containing the range of row indices
     * @throws IllegalArgumentException if either from or toRow is out of the valid row index range
     */
    private Pair<int[], int[]> ranges(int from, int toRow, int fromCol, int toCol) {

        // Check that the ranges are valid.
        if (from < 0 || from > getNumRows()) {
            throw new IllegalArgumentException("Invalid row index: " + from);
        }

        if (toRow < from || toRow > getNumRows()) {
            throw new IllegalArgumentException("Invalid row index: " + toRow);
        }

        if (fromCol < 0 || fromCol > getNumColumns()) {
            throw new IllegalArgumentException("Invalid column index: " + fromCol);
        }

        if (toCol < fromCol || toCol > getNumColumns()) {
            throw new IllegalArgumentException("Invalid column index: " + toCol);
        }

        int[] rangeRow = new int[toRow - from];
        for (int i = 0; i < rangeRow.length; i++) {
            rangeRow[i] = from + i;
        }

        int[] rangeCol = new int[toCol - fromCol];
        for (int i = 0; i < rangeCol.length; i++) {
            rangeCol[i] = fromCol + i;
        }

        return Pair.of(rangeRow, rangeCol);
    }

    private boolean zeroDimension() {
        return getNumRows() == 0 || getNumColumns() == 0;
    }

    /**
     * <p>toString.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String toString() {
        if (getNumRows() == 0) {
            return "Empty";
        } else {
            return MatrixUtils.toString(toArray());
        }
    }

    /**
     * Writes the object to the specified ObjectOutputStream.
     *
     * @param out The ObjectOutputStream to write the object to.
     * @throws IOException If an I/O error occurs.
     */
    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        try {
            out.defaultWriteObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to serialize object: " + getClass().getCanonicalName()
                                           + ", " + e.getMessage());
            throw e;
        }
    }

    /**
     * Reads the object from the specified ObjectInputStream. This method is used during deserialization to restore the
     * state of the object.
     *
     * @param in The ObjectInputStream to read the object from.
     * @throws IOException            If an I/O error occurs.
     * @throws ClassNotFoundException If the class of the serialized object cannot be found.
     */
    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to deserialize object: " + getClass().getCanonicalName()
                                           + ", " + e.getMessage());
            throw e;
        }
    }

    /**
     * Creates and returns a copy of the internal data matrix.
     *
     * @return A new SimpleMatrix instance that is a copy of the internal data.
     */
    public SimpleMatrix getSimpleMatrix() {
        return getData().copy();
    }

    /**
     * Returns the wrapped SimpleMatrix. Be careful with this.
     *
     * @return the wrapped SimpleMatrix instance.
     */
    public SimpleMatrix getData() {
        return data;
    }

    public Matrix solve(Matrix yzCovModel) {
        return yzCovModel.solve(this);
    }
}



