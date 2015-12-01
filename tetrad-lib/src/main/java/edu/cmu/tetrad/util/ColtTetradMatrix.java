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

import cern.colt.matrix.DoubleFactory2D;
import cern.colt.matrix.DoubleMatrix2D;
import cern.colt.matrix.impl.DenseDoubleMatrix1D;
import cern.colt.matrix.impl.DenseDoubleMatrix2D;
import cern.colt.matrix.linalg.Algebra;
import cern.colt.matrix.linalg.Property;
import cern.jet.math.Functions;
import org.apache.commons.math3.linear.RealMatrix;

import java.io.IOException;
import java.io.ObjectInputStream;

/**
 * Wraps the linear algebra library being used.
 */
public class ColtTetradMatrix implements TetradSerializable {
    static final long serialVersionUID = 23L;

    private DoubleMatrix2D data;
    private RealMatrix apacheMatrix;
    private static final Algebra algebra = new Algebra();

    private ColtTetradMatrix(double[][] data) {
        this.data = new DenseDoubleMatrix2D(data);
    }

    private ColtTetradMatrix(int n, int m) {
        this.data = new DenseDoubleMatrix2D(n, m);
    }

    public static ColtTetradMatrix instance(double[][] data) {
        return new ColtTetradMatrix(data);
    }

    public static ColtTetradMatrix instance(int n, int m) {
        return new ColtTetradMatrix(n, m);
    }

    private ColtTetradMatrix(DoubleMatrix2D m) {
        this.data = m;
    }

    public int columns() {
        return data.columns();
    }

    public ColtTetradMatrix viewSelection(int[] rows, int[] cols) {
        return new ColtTetradMatrix(data.viewSelection(rows, cols));
    }

    public ColtTetradMatrix copy() {
        return new ColtTetradMatrix(data.copy());
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static ColtTetradMatrix serializableInstance() {
        return new ColtTetradMatrix(0, 0);
    }

    public TetradVector viewColumn(int i) {
        return new TetradVector(data.viewColumn(i).toArray());
    }

    public ColtTetradMatrix times(ColtTetradMatrix m) {
        return new ColtTetradMatrix(algebra.mult(data, m.data));
    }

    public TetradVector times(TetradVector v) {
        return new TetradVector(algebra.mult(data, new DenseDoubleMatrix1D(v.toArray())).toArray());
    }

    private double[][] toArray() {
        return data.toArray();
    }

    public int rows() {
        return data.rows();
    }

    public double get(int i, int j) {
        return data.get(i, j);
    }

    public ColtTetradMatrix like() {
        return new ColtTetradMatrix(data.like());
    }

    public void set(int i, int j, double v) {
        data.set(i, j, v);
    }

    public TetradVector viewRow(int i) {
        return new TetradVector(data.viewRow(i).toArray());
    }

    public ColtTetradMatrix viewPart(int i, int j, int k, int l) {
        return new ColtTetradMatrix(data.viewPart(i, j, k, l));
    }

    public ColtTetradMatrix inverse() {
        return new ColtTetradMatrix(algebra.inverse(data));
    }

    public ColtTetradMatrix identity(int rows) {
        return new ColtTetradMatrix(DoubleFactory2D.dense.identity(rows).toArray());
    }

    public void assignRow(int row, TetradVector vector) {
        data.viewRow(row).assign(vector.toArray());
    }

    public void assignColumn(int row, TetradVector vector) {
        data.viewColumn(row).assign(vector.toArray());
    }

    public double trace() {
        return algebra.trace(data);
    }

    public double det() {
        return algebra.det(data);
    }

    public ColtTetradMatrix transpose() {
        return new ColtTetradMatrix(algebra.transpose(data));
    }

    public boolean equals(ColtTetradMatrix m, double tolerance) {
        return new Property(tolerance).equals(data, m.data);
    }

    public boolean isSquare() {
        return new Property(0.).isSquare(data);
    }

    public boolean isSymmetric(double tolerance) {
        return new Property(tolerance).isSymmetric(data);
    }

    public double zSum() {
        return data.zSum();
    }

    public ColtTetradMatrix minus(ColtTetradMatrix mb) {
        DoubleMatrix2D mc = data.copy();
        return new ColtTetradMatrix(mc.assign(mb.data, Functions.minus).toArray());
    }

    public ColtTetradMatrix plus(ColtTetradMatrix mb) {
        DoubleMatrix2D mc = data.copy();
        return new ColtTetradMatrix(mc.assign(mb.data, Functions.plus));
    }

    public ColtTetradMatrix scalarMult(double scalar) {
        DoubleMatrix2D mc = data.copy();
        mc.assign(Functions.mult(scalar));
        return new ColtTetradMatrix(mc);
    }

    public int rank() {
        return algebra.rank(data);
    }

    public double norm1() {
        return algebra.norm1(data);
    }

    public ColtTetradMatrix appendRows(ColtTetradMatrix rows) {
        DoubleMatrix2D doubleMatrix2D = DoubleFactory2D.dense.appendRows(data,
                new DenseDoubleMatrix2D(rows.toArray()));
        return new ColtTetradMatrix(doubleMatrix2D);
    }

    public ColtTetradMatrix toPower(int i) {
        return new ColtTetradMatrix(algebra.pow(data, i));
    }

    public TetradVector diag() {
        return new TetradVector(DoubleFactory2D.dense.diagonal(data).toArray());
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

        if (apacheMatrix != null) {
            data = new DenseDoubleMatrix2D(apacheMatrix.getData());
            apacheMatrix = null;
        }
    }
}



