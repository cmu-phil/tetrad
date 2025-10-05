///////////////////////////////////////////////////////////////////////////////
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

import java.io.Serial;
import java.util.Arrays;

/**
 * Allows a view of a matrix to be created that is a subset of the original matrix, to allow parts of a matrix to be set
 * using indices in the view. This is useful for the task, e.g., of constructing a matrix by consructing its parts. The
 * view is not a Matrix object, since it is only used for setting values in the original matrix, but a method is
 * included to construct a Matrix object for the submatrix being viewed.
 *
 * @author josephramsey
 */
public class MView implements TetradSerializable {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Represents a view of a matrix, typically used to access or modify a subset of the rows and columns of an original
     * matrix or another matrix view. This variable holds a reference to an immutable object of type {@code MView}. The
     * {@code matrixView} object can include functionalities such as retrieving specific elements, updating values, or
     * creating further sub-views of its data.
     */
    private final MView matrixView;
    /**
     * Represents an array of row indices included in the matrix view. This array determines which rows from the
     * original matrix are part of the current view. Immutable and specific to the representation of rows in a
     * submatrix.
     */
    private final int[] viewRows;
    /**
     * Represents the columns of the associated matrix view. Each element in this array corresponds to an index of a
     * column in the original matrix that is part of the view. This array defines the mapping of columns from the
     * original matrix to the matrix view. It is immutable.
     */
    private final int[] viewCols;
    /**
     * Represents the underlying Matrix object associated with this MatrixView. This field stores the original matrix or
     * a reference to it, which serves as the basis for any operations or sub-views created by this MatrixView instance.
     * It can be null if the MatrixView is not initialized with a specific matrix.
     */
    private Matrix matrix = null;

    /**
     * Constructs a MatrixView instance representing a sub-view of a matrix specified by the selected rows and columns.
     *
     * @param matrix the original Matrix object from which this view is created
     */
    protected MView(Matrix matrix) {
        this.matrix = matrix;
        this.matrixView = null;
        this.viewRows = range(0, matrix.getNumRows());
        this.viewCols = range(0, matrix.getNumColumns());
    }

    /**
     * Constructs a new MatrixView instance based on an existing MatrixView, with a specified selection of rows and
     * columns.
     *
     * @param matrixView the original MatrixView object from which this new view is created
     * @param viewRows   the array of row indices specifying the rows included in the new view
     * @param viewCols   the array of column indices specifying the columns included in the new view
     */
    protected MView(MView matrixView, int[] viewRows, int[] viewCols) {
        this.matrix = null;
        this.viewRows = viewRows;
        this.viewCols = viewCols;
        this.matrixView = matrixView;
    }

    /**
     * Creates and returns a new Matrix instance representing a submatrix based on the rows and columns specified in the
     * MatrixView.
     *
     * @return a Matrix object that is a submatrix extracted based on the specified rows and columns of the original
     * matrix.
     */
    public Matrix mat() {
        Matrix submatrix = new Matrix(viewRows.length, viewCols.length);

        if (matrixView == null) {
            for (int i = 0; i < viewRows.length; i++) {
                for (int j = 0; j < viewCols.length; j++) {
                    submatrix.set(i, j, matrix.get(viewRows[i], viewCols[j]));
                }
            }
        } else {
            for (int i = 0; i < viewRows.length; i++) {
                for (int j = 0; j < viewCols.length; j++) {
                    submatrix.set(i, j, matrixView.get(viewRows[i], viewCols[j]));
                }
            }
        }

        return submatrix;
    }

    /**
     * Returns the number of rows in the current matrix view.
     *
     * @return the number of rows in the matrix view.
     */
    public int getNumRows() {
        return viewRows.length;
    }

    /**
     * Returns the number of columns in the current matrix view.
     *
     * @return the number of columns in the matrix view.
     */
    public int getNumColumns() {
        return viewCols.length;
    }

    /**
     * Retrieves the value at the specified row and column in the matrix view.
     *
     * @param row    the row index in the view
     * @param column the column index in the view
     * @return the value at the specified row and column in the matrix view
     */
    public double get(int row, int column) {
        if (matrix == null) {
            return matrixView.get(viewRows[row], viewCols[column]);
        } else {
            return matrix.get(row, column);
        }
    }

    /**
     * Updates the value at a specified position in the matrix view.
     *
     * @param row    the row index in the matrix view where the value should be updated
     * @param column the column index in the matrix view where the value should be updated
     * @param value  the new value to set at the specified position
     */
    public void set(int row, int column, double value) {
        // check indices
        if (Arrays.binarySearch(viewRows, row) < 0) {
            throw new IllegalArgumentException("The row index is out of bounds");
        }

        if (Arrays.binarySearch(viewCols, column) < 0) {
            throw new IllegalArgumentException("The column index is out of bounds");
        }

        if (matrix == null) {
            matrixView.set(row, column, value);
        } else {
            matrix.set(row, column, value);
        }
    }

    /**
     * Sets the values of a specific row in the matrix view using the provided vector. The vector's values are converted
     * to an array internally.
     *
     * @param row the index of the row in the matrix view to be updated.
     * @param v   a vector containing the values to assign to the specified row, where each value corresponds to a
     *            column in the matrix view.
     */
    public void setRow(int row, Vector v) {
        setRow(row, v.toArray());
    }

    /**
     * Sets the values of a specific column in the matrix view using the provided vector.
     *
     * @param col the index of the column in the matrix view to be updated
     * @param v   a vector containing the values to assign to the specified column
     */
    public void setColumn(int col, Vector v) {
        setColumn(col, v.toArray());
    }

    /**
     * Sets the values of a specific row in the matrix view.
     *
     * @param row    the index of the row in the matrix view to be updated
     * @param values an array of values to assign to the specified row where each value corresponds to a column in the
     *               matrix view
     */
    public void setRow(int row, double[] values) {
        // Check indices
        if (Arrays.binarySearch(viewRows, row) < 0) {
            throw new IllegalArgumentException("The row index is out of bounds");
        }

        if (values.length != viewCols.length) {
            throw new IllegalArgumentException("The number of values does not match the number of columns in the view");
        }

        for (int i = 0; i < viewCols.length; i++) {
            set(row, viewCols[i], values[i]);
        }
    }

    /**
     * Sets the values of a specific column in the matrix view using the provided array of values.
     *
     * @param column the index of the column in the matrix view to be updated
     * @param values an array of values to assign to the specified column, where each value corresponds to a row in the
     *               matrix view
     * @throws IllegalArgumentException if the column index is out of bounds
     * @throws IllegalArgumentException if the length of the provided values array does not match the number of rows in
     *                                  the matrix view
     */
    public void setColumn(int column, double[] values) {
        // check indices.
        if (Arrays.binarySearch(viewCols, column) < 0) {
            throw new IllegalArgumentException("The column index is out of bounds");
        }

        if (values.length != viewRows.length) {
            throw new IllegalArgumentException("The number of values does not match the number of rows in the view");
        }

        for (int i = 0; i < viewRows.length; i++) {
            set(viewRows[i], column, values[i]);
        }
    }

    /**
     * Sets all elements in the specified rows and columns of the matrix view to the given value.
     *
     * @param value the value to set for all the elements in the specified rows and columns
     */
    public void set(double value) {
        for (int i = 0; i < viewRows.length; i++) {
            for (int j = 0; j < viewCols.length; j++) {
                matrixView.set(viewRows[i], viewCols[j], value);
            }
        }
    }

    /**
     * Sets all elements in the current matrix view to the corresponding values from the given matrix. The dimensions of
     * the provided matrix must match the dimensions of the matrix view.
     *
     * @param arr the array of values to set in the matrix view. The length of the array must match the number of
     * @throws IllegalArgumentException if the dimensions of the provided matrix do not match the dimensions of the
     *                                  matrix view.
     */
    public void set(double[] arr) {
        if (arr.length != matrixView.viewCols.length) {
            throw new IllegalArgumentException("Matrix dimensions do not match view dimensions");
        }

        if (viewRows.length == 1) {
            for (int j = 0; j < matrixView.viewCols.length; j++) {
                set(viewRows[0], viewCols[j], arr[j]);
            }
        } else if (viewCols.length == 1) {
            for (int i = 0; i < matrixView.viewRows.length; i++) {
                set(viewRows[i], viewCols[0], arr[i]);
            }
        }
    }

    /**
     * Sets all elements in the current matrix view to the corresponding values from the given matrix view.
     *
     * @param matrixView the matrix view containing the values to be set in the view. It must have the same
     */
    public void set(MView matrixView) {
        int numRows = getNumRows();
        int rowsLength = viewRows.length;
        int numColumns = getNumColumns();
        int colsLength = viewCols.length;
        if (numRows != rowsLength || numColumns != colsLength) {
            throw new IllegalArgumentException("Matrix dimensions do not match view dimensions");
        }

        for (int i = 0; i < viewRows.length; i++) {
            for (int j = 0; j < viewCols.length; j++) {
                this.matrixView.set(viewRows[i], viewCols[j], matrixView.get(i, j));
            }
        }
    }

    /**
     * Updates the matrix view with the values from the specified 2D array. The dimensions of the input array must match
     * the dimensions of the current matrix view.
     *
     * @param m a two-dimensional array containing the values to set in the matrix view. The number of rows in the array
     *          must match the number of rows in the view, and the number of columns in the array must match the number
     *          of columns in the view.
     * @throws IllegalArgumentException if the dimensions of the provided array do not match the dimensions of the
     *                                  matrix view.
     */
    public void set(double[][] m) {
        if (m.length != viewRows.length || m[0].length != viewCols.length) {
            throw new IllegalArgumentException("Matrix dimensions do not match view dimensions");
        }

        for (int i = 0; i < viewRows.length; i++) {
            for (int j = 0; j < viewCols.length; j++) {
                this.matrixView.set(viewRows[i], viewCols[j], m[i][j]);
            }
        }
    }

    /**
     * Sets the values of the current matrix view to match the provided matrix. The dimensions of the input matrix must
     * match the dimensions of this matrix view.
     *
     * @param m the matrix whose values will be used to update the matrix view. The number of rows and columns in the
     *          input matrix must match the number of rows and columns in the matrix view.
     * @throws IllegalArgumentException if the dimensions of the provided matrix do not match the dimensions of the
     *                                  matrix view.
     */
    public void set(Matrix m) {
        if (m.getNumRows() != viewRows.length || m.getNumColumns() != viewCols.length) {
            throw new IllegalArgumentException("Matrix dimensions do not match view dimensions");
        }

        for (int i = 0; i < viewRows.length; i++) {
            for (int j = 0; j < viewCols.length; j++) {
                this.matrixView.set(viewRows[i], viewCols[j], m.get(i, j));
            }
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
            if (i < 0 || i >= getNumRows()) {
                throw new IllegalArgumentException("Invalid row index: " + i);
            }
        }

        for (int i : range2) {
            if (i < 0 || i >= getNumColumns()) {
                throw new IllegalArgumentException("Invalid column index: " + i);
            }
        }

        return new MView(matrixView, range1, range2);
    }

    /**
     * Creates a view of the entire matrix.
     *
     * @return a MatrixView object representing the entire matrix
     */
    public MView view() {
        return new MView(matrixView, range(0, getNumRows()), range(0, getNumColumns()));
    }

    /**
     * Creates a sub-view of the matrix representing a specific row.
     *
     * @param row the index of the row to be viewed
     * @return a MatrixView object representing the specified row
     */
    public MView viewRow(int row) {
        return new MView(matrixView, range(row, row + 1), range(0, getNumColumns()));
    }

    /**
     * Creates a view of a specific column from the matrix.
     *
     * @param column the index of the column to be viewed
     * @return a MatrixView object representing the specified column
     */
    public MView viewColumn(int column) {
        return new MView(matrixView, range(0, getNumRows()), range(column, column + 1));
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
    public MView viewPart(int fromRow, int fromColumn, int toRow, int toColumn) {
        return new MView(matrixView, range(fromRow, toRow), range(fromColumn, toColumn));
    }

    /**
     * Generates an array of integers representing a range of values between two specified row indices, inclusive.
     *
     * @param fromRow the starting row index (inclusive) of the range
     * @param toRow   the ending row index (inclusive) of the range
     * @return an array of integers containing the range of row indices
     * @throws IllegalArgumentException if either fromRow or toRow is out of the valid row index range
     */
    private int[] range(int fromRow, int toRow) {
        int[] range = new int[toRow - fromRow];
        for (int i = 0; i < range.length; i++) {
            range[i] = fromRow + i;
        }
        return range;
    }

    /**
     * Converts a matrix view to a vector representation if the view corresponds to either a single row or a single
     * column. If the matrix view has exactly one row, a vector containing all the columns in that row is returned.
     * Similarly, if the matrix view has exactly one column, a vector containing all the rows in that column is
     * returned.
     *
     * @return a Vector representing the single row or single column of the matrix view.
     * @throws IllegalArgumentException if the matrix view is null or does not correspond to a single row or column.
     */
    public Vector vector() {
        if (matrixView == null) {
            throw new IllegalArgumentException("Matrix view is null");
        }

        if (viewRows.length == 1) {
            Vector vector = new Vector(getNumColumns());
            for (int i = 0; i < getNumColumns(); i++) {
                vector.set(i, get(0, i));
            }
            return vector;
        } else if (viewCols.length == 1) {
            Vector vector = new Vector(getNumRows());
            for (int i = 0; i < getNumRows(); i++) {
                vector.set(i, get(i, 0));
            }
            return vector;
        } else {
            throw new IllegalArgumentException("Matrix view is not a vector");
        }
    }

    public String toString() {
        return mat().toString();
    }
}

