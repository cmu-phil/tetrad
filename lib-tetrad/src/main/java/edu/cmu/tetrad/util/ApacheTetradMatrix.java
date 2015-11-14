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

import cern.colt.matrix.DoubleMatrix2D;
import cern.colt.matrix.impl.DenseDoubleMatrix2D;
import org.apache.commons.math3.linear.*;

import java.io.IOException;
import java.io.ObjectInputStream;

/**
 * Wraps the Apache math3 linear algebra library for most uses in Tetrad. Specialized uses will still have to use
 * the library directly.
 * @author Joseph Ramsey
 */
public class ApacheTetradMatrix implements TetradSerializable {
    static final long serialVersionUID = 23L;

    private DoubleMatrix2D data;
    private RealMatrix apacheData;

    private ApacheTetradMatrix(double[][] apacheData) {
        if (apacheData.length == 0) {
            this.apacheData = new Array2DRowRealMatrix();
        }
        else {
            this.apacheData = new BlockRealMatrix(apacheData);
        }
    }

    private ApacheTetradMatrix(int m, int n) {
        if (m == 0) {
            this.apacheData = new Array2DRowRealMatrix();
        } else {
            this.apacheData = new BlockRealMatrix(m, n);
        }
    }

    public static ApacheTetradMatrix instance(double[][] data) {
        return new ApacheTetradMatrix(data);
    }

    public static ApacheTetradMatrix instance(int m, int n) {
        return new ApacheTetradMatrix(m, n);
    }

    private ApacheTetradMatrix(RealMatrix m) {
        this.apacheData = m;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static ApacheTetradMatrix serializableInstance() {
        return new ApacheTetradMatrix(0, 0);
    }

    public int columns() {
        return apacheData.getColumnDimension();
    }

    public ApacheTetradMatrix viewSelection(int[] rows, int[] cols) {
        double[][] desination = new double[rows.length][cols.length];
        apacheData.copySubMatrix(rows, cols, desination);
        return new ApacheTetradMatrix(desination);
    }

    public ApacheTetradMatrix copy() {
        return new ApacheTetradMatrix(apacheData.copy());
    }

    public TetradVector viewColumn(int i) {
        return new TetradVector(apacheData.getColumn(i));
    }

    public ApacheTetradMatrix times(ApacheTetradMatrix m) {
        return new ApacheTetradMatrix(apacheData.multiply(m.apacheData));
    }

    public TetradVector times(TetradVector v) {
        double[] v1 = v.toArray();
        double[] w1 = apacheData.transpose().preMultiply(v1);
        return new TetradVector(w1);
    }

    public double[][] toArray() {
        return apacheData.getData();
    }

    public int rows() {
        return apacheData.getRowDimension();
    }

    public double get(int i, int j) {
        return apacheData.getEntry(i, j);
    }

    public ApacheTetradMatrix like() {
        return new ApacheTetradMatrix(apacheData.getRowDimension(), apacheData.getColumnDimension());
    }

    public void set(int i, int j, double v) {
        apacheData.setEntry(i, j, v);
    }

    public TetradVector viewRow(int i) {
        return new TetradVector(apacheData.getRow(i));
    }

    public ApacheTetradMatrix viewPart(int i, int j, int k, int l) {
        return new ApacheTetradMatrix(apacheData.getSubMatrix(i, j, k, l));
    }

    public ApacheTetradMatrix inverse() {

        // Using LUDecomposition.
        // other options: QRDecomposition, CholeskyDecomposition, EigenDecomposition, QRDecomposition,
        // RRQRDDecomposition, SingularValueDecomposition. Very cool. Also MatrixUtils.blockInverse,
        // though that can't handle matrices of size 1. Many ways to invert.
        return new ApacheTetradMatrix(new LUDecomposition(apacheData).getSolver().getInverse());
    }

    public ApacheTetradMatrix identity(int rows) {
        ApacheTetradMatrix m = ApacheTetradMatrix.instance(rows, rows);
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

    public ApacheTetradMatrix transpose() {
        return new ApacheTetradMatrix(apacheData.transpose());
    }

    public boolean equals(ApacheTetradMatrix m, double tolerance) {
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
        return apacheData.getRowDimension() == apacheData.getColumnDimension();
    }

    public boolean isSymmetric(double tolerance) {
        return edu.cmu.tetrad.util.MatrixUtils.isSymmetric(apacheData.getData(), tolerance);
    }


    public double zSum() {
        return new DenseDoubleMatrix2D(apacheData.getData()).zSum();
    }

    public ApacheTetradMatrix minus(ApacheTetradMatrix mb) {
        return new ApacheTetradMatrix(apacheData.subtract(mb.apacheData));
    }

    public ApacheTetradMatrix plus(ApacheTetradMatrix mb) {
        return new ApacheTetradMatrix((apacheData.add(mb.apacheData)));
    }

    public ApacheTetradMatrix scalarMult(double scalar) {
        return new ApacheTetradMatrix(apacheData.scalarMultiply(scalar));
    }

    public int rank() {
        return new RRQRDecomposition(apacheData).getRank(0.1);
    }

    public double norm1() {
        return apacheData.getNorm();
    }

    public ApacheTetradMatrix appendRows(ApacheTetradMatrix rows) {
        RealMatrix m1 = new BlockRealMatrix(apacheData.getRowDimension() + rows.apacheData.getRowDimension(),
                apacheData.getColumnDimension());

        for (int i = 0; i < apacheData.getRowDimension(); i++) {
            for (int j = 0; j < apacheData.getColumnDimension(); j++) {
                m1.setEntry(i, j, apacheData.getEntry(i, j));
            }
        }

        for (int i = 0; i < rows.apacheData.getRowDimension(); i++) {
            for (int j = 0; j < rows.apacheData.getColumnDimension(); j++) {
                m1.setEntry(apacheData.getRowDimension() + i, j, rows.apacheData.getEntry(i, j));
            }
        }

        return new ApacheTetradMatrix(m1);
    }

    public ApacheTetradMatrix toPower(int i) {
        return new ApacheTetradMatrix(apacheData.power(i));
    }

    public TetradVector diag() {
        double[] diag = new double[apacheData.getRowDimension()];

        for (int i = 0; i < apacheData.getRowDimension(); i++) {
            diag[i] = apacheData.getEntry(i, i);
        }

        return new TetradVector(diag);
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

        if (this.data != null) {
            double[][] d = data.toArray();

            if (d.length == 0) {
                this.apacheData = new Array2DRowRealMatrix();
            } else {
                this.apacheData = new BlockRealMatrix(d);
            }

            this.data = null;
        }
    }
}



