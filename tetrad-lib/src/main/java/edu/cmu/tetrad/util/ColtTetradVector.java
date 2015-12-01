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
import cern.colt.matrix.DoubleMatrix1D;
import cern.colt.matrix.impl.DenseDoubleMatrix1D;
import cern.colt.matrix.linalg.Algebra;
import cern.jet.math.Functions;

/**
 * Vector wrapping matrix library.
 */
public class ColtTetradVector implements TetradSerializable {
    static final long serialVersionUID = 23L;

    private final DoubleMatrix1D data;

    private ColtTetradVector(double[] data) {
        this.data = new DenseDoubleMatrix1D(data);
    }

    private ColtTetradVector(int size) {
        this.data = new DenseDoubleMatrix1D(size);
    }

    private ColtTetradVector(DoubleMatrix1D fdata) {
        this.data = fdata;
    }

    public static ColtTetradVector instance(double[] data) {
        return new ColtTetradVector(data);
    }

    private static ColtTetradVector instance(int size) {
        return new ColtTetradVector(size);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static ColtTetradVector serializableInstance() {
        return new ColtTetradVector(0);
    }

    public double[] toArray() {
        return this.data.toArray();
    }

    public int size() {
        return data.size();
    }

    public void set(int j, double v) {
        data.set(j, v);
    }

    public double times(ColtTetradVector v2) {
        return new Algebra().mult(data, v2.data);
    }

    public ColtTetradVector like() {
        return ColtTetradVector.instance(data.size());
    }

    public double get(int i) {
        return data.get(i);
    }

    public ColtTetradVector copy() {
        return new ColtTetradVector(data.copy());
    }

    public ColtTetradVector viewSelection(int[] selection) {
        DoubleMatrix1D doubleMatrix1D = data.viewSelection(selection);
        return new ColtTetradVector(doubleMatrix1D);
    }

    public ColtTetradVector minus(ColtTetradVector mb) {
        DoubleMatrix1D mc = data.copy();
        return new ColtTetradVector(mc.assign(mb.data, Functions.minus));
    }

    public ColtTetradVector plus(ColtTetradVector mb) {
        DoubleMatrix1D mc = data.copy();
        return new ColtTetradVector(mc.assign(mb.data, Functions.plus));
    }

    public ColtTetradVector scalarMult(double scalar) {
        DoubleMatrix1D mc = data.copy();
        mc.assign(Functions.mult(scalar));
        return new ColtTetradVector(mc);
    }

    public TetradMatrix diag() {
        return new TetradMatrix(DoubleFactory2D.dense.diagonal(data).toArray());
    }
}



