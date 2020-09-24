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
public class TetradMatrix implements TetradSerializable {
    static final long serialVersionUID = 23L;

    private final RealMatrix apacheData;
    private int m, n;

    public TetradMatrix(double[][] data) {
        if (data.length == 0) {
            this.apacheData = new Array2DRowRealMatrix();
        } else {
            this.apacheData = new BlockRealMatrix(data);
        }

        this.m = data.length;
        this.n = m == 0 ? 0 : data[0].length;
    }

    public TetradMatrix(int m, int n) {
        if (m == 0 || n == 0) {
            this.apacheData = new Array2DRowRealMatrix();
        } else {
            this.apacheData = new BlockRealMatrix(m, n);
        }

        this.m = m;
        this.n = n;
    }

    public TetradMatrix(TetradMatrix m) {
        this(m.apacheData.getData());
    }

    public TetradMatrix(double[][] matrix, int rows, int columns) {
        if (matrix == null) {
            throw new IllegalArgumentException("Null matrix.");
        }

        this.apacheData = new BlockRealMatrix(matrix);
        this.m = rows;
        this.n = columns;
    }

    public static TetradMatrix sparseMatrix(int m, int n) {
        return new TetradMatrix(new OpenMapRealMatrix(m, n).getData());
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static TetradMatrix serializableInstance() {
        return new TetradMatrix(0, 0);
    }

    public TetradMatrix sqrt() {
        SingularValueDecomposition svd = new SingularValueDecomposition(apacheData);
        RealMatrix U = svd.getU();
        RealMatrix V = svd.getV();
        double[] s = svd.getSingularValues();
        for (int i = 0; i < s.length; i++) s[i] = 1.0 / s[i];
        RealMatrix S = new BlockRealMatrix(s.length, s.length);
        for (int i = 0; i < s.length; i++) S.setEntry(i, i, s[i]);
        RealMatrix sqrt = U.multiply(S).multiply(V);
        return new TetradMatrix(sqrt.getData());
    }

    public int rows() {
        return m;
    }

    public int columns() {
        return n;
    }

    public TetradMatrix getSelection(int[] rows, int[] cols) {
        if (rows.length == 0 || cols.length == 0) {
            return new TetradMatrix(rows.length, cols.length);
        }

        RealMatrix subMatrix = apacheData.getSubMatrix(rows, cols);
        return new TetradMatrix(subMatrix.getData(), rows.length, cols.length);
    }

    public TetradMatrix copy() {
        if (zeroDimension()) return new TetradMatrix(rows(), columns());
        return new TetradMatrix(apacheData.copy().getData(), rows(), columns());
    }

    public TetradVector getColumn(int j) {
        if (zeroDimension()) {
            return new TetradVector(rows());
        }

        return new TetradVector(apacheData.getColumn(j));
    }

    public TetradMatrix times(TetradMatrix m) {
        if (this.zeroDimension() || m.zeroDimension())
            return new TetradMatrix(this.rows(), m.columns());
        else {
            return new TetradMatrix(apacheData.multiply(m.apacheData).getData(), this.rows(), m.columns());
        }
    }

    public TetradVector times(TetradVector v) {
        if (v.size() != apacheData.getColumnDimension()) {
            throw new IllegalArgumentException("Mismatched dimensions.");
        }

        double[] y = new double[apacheData.getRowDimension()];

        for (int i = 0; i < apacheData.getRowDimension(); i++) {
            double sum = 0.0;

            for (int j = 0; j < apacheData.getColumnDimension(); j++) {
                sum += apacheData.getEntry(i, j) * v.get(j);
            }

            y[i] = sum;
        }

        return new TetradVector(y);
    }

    public double[][] toArray() {
        return apacheData.getData();
    }

    public double get(int i, int j) {
        return apacheData.getEntry(i, j);
    }

    public TetradMatrix like() {
        return new TetradMatrix(apacheData.getRowDimension(), apacheData.getColumnDimension());
    }

    public void set(int i, int j, double v) {
        apacheData.setEntry(i, j, v);
    }

    public TetradVector getRow(int i) {
        if (zeroDimension()) {
            return new TetradVector(columns());
        }

        return new TetradVector(apacheData.getRow(i));
    }

    public TetradMatrix getPart(int i, int j, int k, int l) {
        return new TetradMatrix(apacheData.getSubMatrix(i, j, k, l).getData());
    }

    public TetradMatrix inverse() throws SingularMatrixException {
        if (!isSquare()) throw new IllegalArgumentException("I can only invert square matrices.");

        if (rows() == 0) {
            return new TetradMatrix(0, 0);
        }

        return new TetradMatrix(new LUDecomposition(apacheData, 1e-9).getSolver().getInverse().getData());

    }

    public TetradMatrix symmetricInverse() {
        if (!isSquare()) throw new IllegalArgumentException();
        if (rows() == 0) return new TetradMatrix(0, 0);

        return new TetradMatrix(new CholeskyDecomposition(apacheData).getSolver().getInverse().getData());
    }

    public TetradMatrix ginverse() {
        final double[][] data = apacheData.getData();

        if (data.length == 0 || data[0].length == 0) {
            return new TetradMatrix(data);
        }

        return new TetradMatrix(MatrixUtils.pseudoInverse(data));
    }

    public static TetradMatrix identity(int rows) {
        TetradMatrix m = new TetradMatrix(rows, rows);
        for (int i = 0; i < rows; i++) m.set(i, i, 1);
        return m;
    }

    public void assignRow(int row, TetradVector doubles) {
        apacheData.setRow(row, doubles.toArray());
    }

    public void assignColumn(int col, TetradVector doubles) {
        apacheData.setColumn(col, doubles.toArray());
    }

    public double trace() {
        return apacheData.getTrace();
    }

    public double det() {
        return new LUDecomposition(apacheData).getDeterminant();
    }

    public TetradMatrix transpose() {
        if (zeroDimension()) return new TetradMatrix(columns(), rows());
        return new TetradMatrix(apacheData.transpose().getData(), columns(), rows());
    }

    private boolean zeroDimension() {
        return rows() == 0 || columns() == 0;
    }

    public boolean equals(TetradMatrix m, double tolerance) {
        for (int i = 0; i < apacheData.getRowDimension(); i++) {
            for (int j = 0; j < apacheData.getColumnDimension(); j++) {
                if (Math.abs(apacheData.getEntry(i, j) - m.apacheData.getEntry(i, j)) > tolerance) {
                    return false;
                }
            }
        }

        return true;
    }

    public boolean isSquare() {
        return rows() == columns();
    }

    public boolean isSymmetric(double tolerance) {
        return edu.cmu.tetrad.util.MatrixUtils.isSymmetric(apacheData.getData(), tolerance);
    }

    public double zSum() {
        return new DenseDoubleMatrix2D(apacheData.getData()).zSum();
    }

    public TetradMatrix minus(TetradMatrix mb) {
        if (mb.rows() == 0 || mb.columns() == 0) return this;
        return new TetradMatrix(apacheData.subtract(mb.apacheData).getData(), rows(), columns());
    }

    public TetradMatrix plus(TetradMatrix mb) {
        if (mb.rows() == 0 || mb.columns() == 0) return this;
        return new TetradMatrix(apacheData.add(mb.apacheData).getData(), rows(), columns());
    }

    public TetradMatrix scalarMult(double scalar) {
        TetradMatrix newMatrix = copy();
        for (int i = 0; i < rows(); i++) {
            for (int j = 0; j < columns(); j++) {
                newMatrix.set(i, j, get(i, j) * scalar);
            }
        }

        return newMatrix;
    }

    public int rank() {
        SingularValueDecomposition singularValueDecomposition = new SingularValueDecomposition(apacheData);
        return singularValueDecomposition.getRank();
    }

    public double norm1() {
        return apacheData.getNorm();
    }

    public TetradVector diag() {
        double[] diag = new double[apacheData.getRowDimension()];

        for (int i = 0; i < apacheData.getRowDimension(); i++) {
            diag[i] = apacheData.getEntry(i, i);
        }

        return new TetradVector(diag);
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
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (m == 0) m = apacheData.getRowDimension();
        if (n == 0) n = apacheData.getColumnDimension();
    }

    public void assign(TetradMatrix matrix) {
        if (apacheData.getRowDimension() != matrix.rows() || apacheData.getColumnDimension() != matrix.columns()) {
            throw new IllegalArgumentException("Mismatched matrix size.");
        }

        for (int i = 0; i < apacheData.getRowDimension(); i++) {
            for (int j = 0; j < apacheData.getColumnDimension(); j++) {
                apacheData.setEntry(i, j, matrix.get(i, j));
            }
        }
    }

    public TetradVector sum(int direction) {
        if (direction == 1) {
            TetradVector sums = new TetradVector(columns());

            for (int j = 0; j < columns(); j++) {
                double sum = 0.0;

                for (int i = 0; i < rows(); i++) {
                    sum += apacheData.getEntry(i, j);
                }

                sums.set(j, sum);
            }

            return sums;
        } else if (direction == 2) {
            TetradVector sums = new TetradVector(rows());

            for (int i = 0; i < rows(); i++) {
                double sum = 0.0;

                for (int j = 0; j < columns(); j++) {
                    sum += apacheData.getEntry(i, j);
                }

                sums.set(i, sum);
            }

            return sums;
        } else {
            throw new IllegalArgumentException("Expecting 1 (sum columns) or 2 (sum rows).");
        }
    }
}



