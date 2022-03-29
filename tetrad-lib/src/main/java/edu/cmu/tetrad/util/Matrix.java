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

import cern.colt.matrix.impl.DenseDoubleMatrix2D;
import org.apache.commons.math3.linear.*;

import java.io.IOException;
import java.io.ObjectInputStream;

/**
 * Wraps the Apache math3 linear algebra library for most uses in Tetrad.
 * Specialized uses will still have to use the library directly. One issue
 * this fixes is that a BlockRealMatrix cannot represent a matrix with zero
 * rows; this uses an Array2DRowRealMatrix to represent that case.
 *
 * @author Joseph Ramsey
 */
public class Matrix implements TetradSerializable {
    static final long serialVersionUID = 23L;

    private final RealMatrix apacheData;
    private int m, n;

    public Matrix(final double[][] data) {
        if (data.length == 0) {
            this.apacheData = new Array2DRowRealMatrix();
        } else {
            this.apacheData = new BlockRealMatrix(data);
        }

        this.m = data.length;
        this.n = this.m == 0 ? 0 : data[0].length;
    }

    public Matrix(final int m, final int n) {
        if (m == 0 || n == 0) {
            this.apacheData = new Array2DRowRealMatrix();
        } else {
            this.apacheData = new BlockRealMatrix(m, n);
        }

        this.m = m;
        this.n = n;
    }

    public Matrix(final Matrix m) {
        this(m.apacheData.copy().getData());
    }

    public void assign(final Matrix matrix) {
        if (this.apacheData.getRowDimension() != matrix.rows() || this.apacheData.getColumnDimension() != matrix.columns()) {
            throw new IllegalArgumentException("Mismatched matrix size.");
        }

        for (int i = 0; i < this.apacheData.getRowDimension(); i++) {
            for (int j = 0; j < this.apacheData.getColumnDimension(); j++) {
                this.apacheData.setEntry(i, j, matrix.get(i, j));
            }
        }
    }

    public int columns() {
        return this.n;
    }

    public Vector diag() {
        final double[] diag = new double[this.apacheData.getRowDimension()];

        for (int i = 0; i < this.apacheData.getRowDimension(); i++) {
            diag[i] = this.apacheData.getEntry(i, i);
        }

        return new Vector(diag);
    }

    public Matrix getSelection(final int[] rows, final int[] cols) {
        if (rows.length == 0 || cols.length == 0) {
            return new Matrix(rows.length, cols.length);
        }

        final RealMatrix subMatrix = this.apacheData.getSubMatrix(rows, cols);
        return new Matrix(subMatrix.getData());
    }

    public Matrix copy() {
        if (zeroDimension()) return new Matrix(rows(), columns());
        return new Matrix(this.apacheData.copy().getData());
    }

    public Vector getColumn(final int j) {
        if (zeroDimension()) {
            return new Vector(rows());
        }

        return new Vector(this.apacheData.getColumn(j));
    }

    public Matrix times(final Matrix m) {
        if (this.zeroDimension() || m.zeroDimension())
            return new Matrix(this.rows(), m.columns());
        else {
            return new Matrix(this.apacheData.multiply(m.apacheData).getData());
        }
    }

    public Vector times(final Vector v) {
        if (v.size() != this.apacheData.getColumnDimension()) {
            throw new IllegalArgumentException("Mismatched dimensions.");
        }

        final double[] y = new double[this.apacheData.getRowDimension()];

        for (int i = 0; i < this.apacheData.getRowDimension(); i++) {
            double sum = 0.0;

            for (int j = 0; j < this.apacheData.getColumnDimension(); j++) {
                sum += this.apacheData.getEntry(i, j) * v.get(j);
            }

            y[i] = sum;
        }

        return new Vector(y);
    }

    public double[][] toArray() {
        return this.apacheData.getData();
    }

    public double get(final int i, final int j) {
        return this.apacheData.getEntry(i, j);
    }

    public Matrix like() {
        return new Matrix(this.apacheData.getRowDimension(), this.apacheData.getColumnDimension());
    }

    public void set(final int i, final int j, final double v) {
        this.apacheData.setEntry(i, j, v);
    }

    public Vector getRow(final int i) {
        if (zeroDimension()) {
            return new Vector(columns());
        }

        return new Vector(this.apacheData.getRow(i));
    }

    public Matrix getPart(final int i, final int j, final int k, final int l) {
        return new Matrix(this.apacheData.getSubMatrix(i, j, k, l).getData());
    }

    public Matrix inverse() throws SingularMatrixException {
        if (!isSquare()) throw new IllegalArgumentException("I can only invert square matrices.");

        if (rows() == 0) {
            return new Matrix(0, 0);
        }

        return new Matrix(new LUDecomposition(this.apacheData, 1e-10).getSolver().getInverse().getData());
    }

    public Matrix symmetricInverse() {
        if (!isSquare()) throw new IllegalArgumentException();
        if (rows() == 0) return new Matrix(0, 0);

        return new Matrix(new CholeskyDecomposition(this.apacheData).getSolver().getInverse().getData());
    }

    public Matrix ginverse() {
        final double[][] data = this.apacheData.getData();

        if (data.length == 0 || data[0].length == 0) {
            return new Matrix(data);
        }

        return new Matrix(MatrixUtils.pseudoInverse(data));
    }

    public static Matrix identity(final int rows) {
        final Matrix m = new Matrix(rows, rows);
        for (int i = 0; i < rows; i++) m.set(i, i, 1);
        return m;
    }

    public void assignRow(final int row, final Vector doubles) {
        this.apacheData.setRow(row, doubles.toArray());
    }

    public void assignColumn(final int col, final Vector doubles) {
        this.apacheData.setColumn(col, doubles.toArray());
    }

    public double trace() {
        return this.apacheData.getTrace();
    }

    public double det() {
        return new LUDecomposition(this.apacheData, 1e-6D).getDeterminant();
    }

    public Matrix transpose() {
        if (zeroDimension()) return new Matrix(columns(), rows());
        return new Matrix(this.apacheData.transpose().getData());
    }


    public boolean equals(final Matrix m, final double tolerance) {
        for (int i = 0; i < this.apacheData.getRowDimension(); i++) {
            for (int j = 0; j < this.apacheData.getColumnDimension(); j++) {
                if (Math.abs(this.apacheData.getEntry(i, j) - m.apacheData.getEntry(i, j)) > tolerance) {
                    return false;
                }
            }
        }

        return true;
    }

    public boolean isSquare() {
        return rows() == columns();
    }

    public boolean isSymmetric(final double tolerance) {
        return edu.cmu.tetrad.util.MatrixUtils.isSymmetric(this.apacheData.getData(), tolerance);
    }


    public Matrix minus(final Matrix mb) {
        if (mb.rows() == 0 || mb.columns() == 0) return this;
        return new Matrix(this.apacheData.subtract(mb.apacheData).getData());
    }

    public double norm1() {
        return this.apacheData.getNorm();
    }

    public Matrix plus(final Matrix mb) {
        if (mb.rows() == 0 || mb.columns() == 0) return this;
        return new Matrix(this.apacheData.add(mb.apacheData).getData());
    }

    public int rank() {
        final SingularValueDecomposition singularValueDecomposition = new SingularValueDecomposition(this.apacheData);
        return singularValueDecomposition.getRank();
    }

    public int rows() {
        return this.m;
    }

    public Matrix scalarMult(final double scalar) {
        final Matrix newMatrix = copy();
        for (int i = 0; i < rows(); i++) {
            for (int j = 0; j < columns(); j++) {
                newMatrix.set(i, j, get(i, j) * scalar);
            }
        }

        return newMatrix;
    }

    public Matrix sqrt() {
        final SingularValueDecomposition svd = new SingularValueDecomposition(this.apacheData);
        final RealMatrix U = svd.getU();
        final RealMatrix V = svd.getV();
        final double[] s = svd.getSingularValues();
        for (int i = 0; i < s.length; i++) s[i] = 1.0 / s[i];
        final RealMatrix S = new BlockRealMatrix(s.length, s.length);
        for (int i = 0; i < s.length; i++) S.setEntry(i, i, s[i]);
        final RealMatrix sqrt = U.multiply(S).multiply(V);
        return new Matrix(sqrt.getData());
    }


    public static Matrix sparseMatrix(final int m, final int n) {
        return new Matrix(new OpenMapRealMatrix(m, n).getData());
    }

    public Vector sum(final int direction) {
        if (direction == 1) {
            final Vector sums = new Vector(columns());

            for (int j = 0; j < columns(); j++) {
                double sum = 0.0;

                for (int i = 0; i < rows(); i++) {
                    sum += this.apacheData.getEntry(i, j);
                }

                sums.set(j, sum);
            }

            return sums;
        } else if (direction == 2) {
            final Vector sums = new Vector(rows());

            for (int i = 0; i < rows(); i++) {
                double sum = 0.0;

                for (int j = 0; j < columns(); j++) {
                    sum += this.apacheData.getEntry(i, j);
                }

                sums.set(i, sum);
            }

            return sums;
        } else {
            throw new IllegalArgumentException("Expecting 1 (sum columns) or 2 (sum rows).");
        }
    }

    public double zSum() {
        return new DenseDoubleMatrix2D(this.apacheData.getData()).zSum();
    }

    private boolean zeroDimension() {
        return rows() == 0 || columns() == 0;
    }

    public String toString() {
        if (rows() == 0) {
            return "Empty";
        } else {
            return MatrixUtils.toString(toArray());
        }
    }

    /**
     * Adds semantic checks to the default deserialization method. This method
     * must have the standard signature for a readObject method, and the body of
     * the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from
     * version to version. A readObject method of this form may be added to any
     * class, even if Tetrad sessions were previously saved out using a version
     * of the class that didn't include it. (That's what the
     * "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for help.
     */
    private void readObject(final ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (this.m == 0) this.m = this.apacheData.getRowDimension();
        if (this.n == 0) this.n = this.apacheData.getColumnDimension();
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static Matrix serializableInstance() {
        return new Matrix(0, 0);
    }


}



