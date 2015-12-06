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

    private RealMatrix apacheData;
    private int m, n;

    public TetradMatrix(double[][] data) {
        if (data.length == 0) {
            this.apacheData = new Array2DRowRealMatrix();
        } else {
//            this.apacheData = new OpenMapRealMatrix(data.length, data[0].length);
//
//            for (int i = 0; i < data.length; i++) {
//                for (int j = 0; j < data[0].length; j++) {
//                    apacheData.setEntry(i, j, data[i][j]);
//                }
//            }
            this.apacheData = new BlockRealMatrix(data);
        }

        this.m = data.length;
        this.n = m == 0 ? 0 : data[0].length;
    }

    public TetradMatrix(int m, int n) {
        if (m == 0 || n == 0) {
            this.apacheData = new Array2DRowRealMatrix();
        } else {
//            this.apacheData = new OpenMapRealMatrix(m, n);
            this.apacheData = new BlockRealMatrix(m, n);
        }

        this.m = m;
        this.n = n;
    }

    public TetradMatrix(TetradMatrix m) {
        this(m.apacheData.getData());
    }

    public TetradMatrix(RealMatrix matrix) {
        if (matrix == null) {
            throw new IllegalArgumentException("Null matrix.");
        }

        this.apacheData = matrix;
        this.m = matrix.getRowDimension();
        this.n = matrix.getColumnDimension();
    }

    public TetradMatrix(RealMatrix matrix, int rows, int columns) {
        if (matrix == null) {
            throw new IllegalArgumentException("Null matrix.");
        }

        this.apacheData = matrix;
        this.m = rows;
        this.n = columns;

        int _rows = matrix.getRowDimension();
        int _cols = matrix.getColumnDimension();
        if (_rows != 0 && _rows != rows) throw new IllegalArgumentException();
        if (_cols != 0 && _cols != columns) throw new IllegalArgumentException();
    }

    public static TetradMatrix sparseMatrix(int m, int n) {
        return new TetradMatrix(new OpenMapRealMatrix(m, n));
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static TetradMatrix serializableInstance() {
        return new TetradMatrix(0, 0);
    }

    public TetradMatrix sqrt() {
        SingularValueDecomposition svd = new SingularValueDecomposition(getRealMatrix());
        RealMatrix U = svd.getU();
        RealMatrix V = svd.getV();
        double[] s = svd.getSingularValues();
        for (int i = 0; i < s.length; i++) s[i] = 1.0 / s[i];
        RealMatrix S = new BlockRealMatrix(s.length, s.length);
        for (int i = 0; i < s.length; i++) S.setEntry(i, i, s[i]);
        RealMatrix sqrt = U.multiply(S).multiply(V);
        return new TetradMatrix(sqrt);
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

        for (int col : cols) {
            if (col == -1) {
                System.out.println();
            }
        }

        RealMatrix subMatrix = apacheData.getSubMatrix(rows, cols);
        return new TetradMatrix(subMatrix, rows.length, cols.length);
    }

    public TetradMatrix copy() {
        if (zeroDimension()) return new TetradMatrix(rows(), columns());
        return new TetradMatrix(apacheData.copy(), rows(), columns());
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
            return new TetradMatrix(apacheData.multiply(m.apacheData), this.rows(), m.columns());
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
        return new TetradMatrix(apacheData.getSubMatrix(i, j, k, l));
    }

    public TetradMatrix inverse() {
        if (!isSquare()) throw new IllegalArgumentException("Input is not a square matrix.");

        // Trying for a speedup by not having to construct the matrix factorization.
        if (rows() == 0) {
            return new TetradMatrix(0, 0);
        } else if (rows() == 1) {
            TetradMatrix m = new TetradMatrix(1, 1);
            m.set(0, 0, 1.0 / apacheData.getEntry(0, 0));
            return m;
        } else if (rows() == 2) {
            double a = apacheData.getEntry(0, 0);
            double b = apacheData.getEntry(0, 1);
            double c = apacheData.getEntry(1, 0);
            double d = apacheData.getEntry(1, 1);

            double delta = a * d - b * c;

            TetradMatrix inverse = new TetradMatrix(2, 2);
            inverse.set(0, 0, d);
            inverse.set(0, 1, -b);
            inverse.set(1, 0, -c);
            inverse.set(1, 1, a);

            return inverse.scalarMult(1.0 / delta);

        } else if (rows() == 3) {
            RealMatrix m = apacheData;

            double a11 = m.getEntry(0, 0);
            double a12 = m.getEntry(0, 1);
            double a13 = m.getEntry(0, 2);

            double a21 = m.getEntry(1, 0);
            double a22 = m.getEntry(1, 1);
            double a23 = m.getEntry(1, 2);

            double a31 = m.getEntry(2, 0);
            double a32 = m.getEntry(2, 1);
            double a33 = m.getEntry(2, 2);

            final double denom = -a12 * a21 * a33 + a11 * a22 * a33 - a13 * a22 * a31 +
                    a12 * a23 * a31 + a13 * a21 * a32 - a11 * a23 * a32;

            double[][] inverse = new double[][]
                    {
                            {(a22 * a33 - a23 * a32) / denom,
                                    (-a12 * a33 + a13 * a32) / denom,
                                    (-a13 * a22 + a12 * a23) / denom},

                            {(-a21 * a33 + a23 * a31) / denom,
                                    (a11 * a33 - a13 * a31) / denom,
                                    (a13 * a21 - a11 * a23) / denom},

                            {(-a22 * a31 + a21 * a32) / denom,
                                    (a12 * a31 - a11 * a32) / denom,
                                    (-a12 * a21 + a11 * a22) / denom}
                    };

            return new TetradMatrix(inverse);
        } else if (rows() == 4) {
            RealMatrix m = apacheData;

            double a11 = m.getEntry(0, 0);
            double a12 = m.getEntry(0, 1);
            double a13 = m.getEntry(0, 2);
            double a14 = m.getEntry(0, 3);

            double a21 = m.getEntry(1, 0);
            double a22 = m.getEntry(1, 1);
            double a23 = m.getEntry(1, 2);
            double a24 = m.getEntry(1, 3);

            double a31 = m.getEntry(2, 0);
            double a32 = m.getEntry(2, 1);
            double a33 = m.getEntry(2, 2);
            double a34 = m.getEntry(2, 3);

            double a41 = m.getEntry(3, 0);
            double a42 = m.getEntry(3, 1);
            double a43 = m.getEntry(3, 2);
            double a44 = m.getEntry(3, 3);

            final double denom = a14 * a23 * a32 * a41 - a13 * a24 * a32 * a41 -
                    a14 * a22 * a33 * a41 + a12 * a24 * a33 * a41 + a13 * a22 * a34 * a41 -
                    a12 * a23 * a34 * a41 - a14 * a23 * a31 * a42 + a13 * a24 * a31 * a42 +
                    a14 * a21 * a33 * a42 - a11 * a24 * a33 * a42 - a13 * a21 * a34 * a42 +
                    a11 * a23 * a34 * a42 + a14 * a22 * a31 * a43 - a12 * a24 * a31 * a43 -
                    a14 * a21 * a32 * a43 + a11 * a24 * a32 * a43 + a12 * a21 * a34 * a43 -
                    a11 * a22 * a34 * a43 - a13 * a22 * a31 * a44 + a12 * a23 * a31 * a44 +
                    a13 * a21 * a32 * a44 - a11 * a23 * a32 * a44 - a12 * a21 * a33 * a44 +
                    a11 * a22 * a33 * a44;

            double[][] inverse = new double[][]

                    {{(-a24 * a33 * a42 + a23 * a34 * a42 + a24 * a32 * a43 - a22 * a34 * a43 -
                            a23 * a32 * a44 + a22 * a33 * a44) / denom,
                            (a14 * a33 * a42 - a13 * a34 * a42 - a14 * a32 * a43 +
                                    a12 * a34 * a43 + a13 * a32 * a44 - a12 * a33 * a44) / denom,
                            (-a14 * a23 * a42 + a13 * a24 * a42 +
                                    a14 * a22 * a43 - a12 * a24 * a43 - a13 * a22 * a44 +
                                    a12 * a23 * a44) / denom,
                            (a14 * a23 * a32 - a13 * a24 * a32 - a14 * a22 * a33 +
                                    a12 * a24 * a33 + a13 * a22 * a34 - a12 * a23 * a34) / denom},
                            {(a24 * a33 * a41 - a23 * a34 * a41 - a24 * a31 * a43 + a21 * a34 * a43 + a23 * a31 * a44 -
                                    a21 * a33 * a44) / denom,
                                    (-a14 * a33 * a41 + a13 * a34 * a41 + a14 * a31 * a43 -
                                            a11 * a34 * a43 - a13 * a31 * a44 + a11 * a33 * a44) / denom,
                                    (a14 * a23 * a41 - a13 * a24 * a41 -
                                            a14 * a21 * a43 + a11 * a24 * a43 + a13 * a21 * a44 -
                                            a11 * a23 * a44) / denom,
                                    (-a14 * a23 * a31 + a13 * a24 * a31 + a14 * a21 * a33 -
                                            a11 * a24 * a33 - a13 * a21 * a34 + a11 * a23 * a34) / denom},
                            {(-a24 * a32 * a41 +
                                    a22 * a34 * a41 + a24 * a31 * a42 - a21 * a34 * a42 - a22 * a31 * a44 +
                                    a21 * a32 * a44) / denom,
                                    (a14 * a32 * a41 - a12 * a34 * a41 - a14 * a31 * a42 +
                                            a11 * a34 * a42 + a12 * a31 * a44 - a11 * a32 * a44) / denom,
                                    (-a14 * a22 * a41 + a12 * a24 * a41 +
                                            a14 * a21 * a42 - a11 * a24 * a42 - a12 * a21 * a44 +
                                            a11 * a22 * a44) / denom,
                                    (a14 * a22 * a31 - a12 * a24 * a31 - a14 * a21 * a32 +
                                            a11 * a24 * a32 + a12 * a21 * a34 - a11 * a22 * a34) / denom},
                            {(a23 * a32 * a41 -
                                    a22 * a33 * a41 - a23 * a31 * a42 + a21 * a33 * a42 + a22 * a31 * a43 -
                                    a21 * a32 * a43) / denom,
                                    (-a13 * a32 * a41 + a12 * a33 * a41 + a13 * a31 * a42 -
                                            a11 * a33 * a42 - a12 * a31 * a43 + a11 * a32 * a43) / denom,
                                    (a13 * a22 * a41 - a12 * a23 * a41 -
                                            a13 * a21 * a42 + a11 * a23 * a42 + a12 * a21 * a43 -
                                            a11 * a22 * a43) / denom,
                                    (-a13 * a22 * a31 + a12 * a23 * a31 + a13 * a21 * a32 -
                                            a11 * a23 * a32 - a12 * a21 * a33 + a11 * a22 * a33) / denom}};

            return new TetradMatrix(inverse);
        } else {

            // Using LUDecomposition.
            // other options: QRDecomposition, CholeskyDecomposition, EigenDecomposition, QRDecomposition,
            // RRQRDDecomposition, SingularValueDecomposition. Very cool. Also MatrixUtils.blockInverse,
            // though that can't handle matrices of size 1. Many ways to invert.

            // Note CholeskyDecomposition only takes inverses of symmetric matrices.
//        return new TetradMatrix(new CholeskyDecomposition(apacheData).getSolver().getInverse());
//        return new TetradMatrix(new EigenDecomposition(apacheData).getSolver().getInverse());
//        return new TetradMatrix(new QRDecomposition(apacheData).getSolver().getInverse());
//
//            return new TetradMatrix(new SingularValueDecomposition(apacheData).getSolver().getInverse());
            return new TetradMatrix(new LUDecomposition(apacheData).getSolver().getInverse());
        }

    }

    public TetradMatrix symmetricInverse() {
        if (!isSquare()) throw new IllegalArgumentException();
        if (rows() == 0) return new TetradMatrix(0, 0);

        // Using LUDecomposition.
        // other options: QRDecomposition, CholeskyDecomposition, EigenDecomposition, QRDecomposition,
        // RRQRDDecomposition, SingularValueDecomposition. Very cool. Also MatrixUtils.blockInverse,
        // though that can't handle matrices of size 1. Many ways to invert.

        // Note CholeskyDecomposition only takes inverses of symmetric matrices.
        return new TetradMatrix(new CholeskyDecomposition(apacheData).getSolver().getInverse());
//        return new TetradMatrix(new EigenDecomposition(apacheData).getSolver().getInverse());
//        return new TetradMatrix(new QRDecomposition(apacheData).getSolver().getInverse());

//        return new TetradMatrix(new SingularValueDecomposition(apacheData).getSolver().getInverse());
//        return new TetradMatrix(new LUDecomposition(apacheData).getSolver().getInverse());
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

    public void assignColumn(int row, TetradVector doubles) {
        apacheData.setColumn(row, doubles.toArray());
    }

    public double trace() {
        return apacheData.getTrace();
    }

    public double det() {
        return new LUDecomposition(apacheData).getDeterminant();
    }

    public TetradMatrix transpose() {
        if (zeroDimension()) return new TetradMatrix(columns(), rows());
        return new TetradMatrix(apacheData.transpose(), columns(), rows());
    }

    public TetradMatrix transposeWithoutCopy() {
        RealMatrix transpose = MatrixUtils.transposeWithoutCopy(apacheData);
        return new TetradMatrix(transpose);
    }

    private boolean zeroDimension() {
        return rows() == 0 || columns() == 0;
    }

    public boolean equals(TetradMatrix m, double tolerance) {
        RealMatrix n = m.apacheData;

        for (int i = 0; i < apacheData.getRowDimension(); i++) {
            for (int j = 0; j < apacheData.getColumnDimension(); j++) {
                if (Math.abs(apacheData.getEntry(i, j) - n.getEntry(i, j)) > tolerance) {
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
        return new TetradMatrix(apacheData.subtract(mb.apacheData), rows(), columns());
    }

    public TetradMatrix plus(TetradMatrix mb) {
        return new TetradMatrix(apacheData.add(mb.apacheData), rows(), columns());
    }

    public TetradMatrix scalarMult(double scalar) {
        return new TetradMatrix(apacheData.scalarMultiply(scalar), rows(), columns());
    }

    public int rank() {
//        return new RRQRDecomposition(apacheData).getRank(10);
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
     *
     * @throws java.io.IOException
     * @throws ClassNotFoundException
     */
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (m == 0) m = apacheData.getRowDimension();
        if (n == 0) n = apacheData.getColumnDimension();
    }

    public RealMatrix getRealMatrix() {
        return apacheData;
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
}



