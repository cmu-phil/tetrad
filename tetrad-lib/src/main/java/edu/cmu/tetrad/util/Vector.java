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

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;

/**
 * Vector wrapping matrix library.
 */
public class Vector implements TetradSerializable {
    static final long serialVersionUID = 23L;

    private final RealVector data;

    public Vector(double[] data) {
        this.data = new ArrayRealVector(data);
    }

    public Vector(int size) {
        this.data = new ArrayRealVector(size);
    }

    public void assign(double value) {
        for (int i = 0; i < data.getDimension(); i++) {
            data.setEntry(i, value);
        }
    }

    public Vector copy() {
        return new Vector(data.copy().toArray());
    }

    public Matrix diag() {
        Matrix m = new Matrix(data.getDimension(), data.getDimension());

        for (int i = 0; i < data.getDimension(); i++) {
            m.set(i, i, data.getEntry(i));
        }

        return m;
    }

    public double dotProduct(Vector v2) {
        return data.dotProduct(v2.data);
    }

    public double get(int i) {
        return data.getEntry(i);
    }

    public Vector like() {
        return new Vector(size());
    }

    public Vector minus(Vector mb) {
        return new Vector(data.subtract(mb.data).toArray());
    }

    public Vector plus(Vector mb) {
        return new Vector(data.add(mb.data).toArray());
    }

    public Vector scalarMult(double scalar) {
        Vector newMatrix = copy();
        for (int i = 0; i < size(); i++) {
            newMatrix.set(i, get(i) * scalar);
        }

        return newMatrix;
    }

    public void set(int j, double v) {
        data.setEntry(j, v);
    }

    public int size() {
        return data.getDimension();
    }

    public double[] toArray() {
        return data.toArray();
    }

    public String toString() {
        return MatrixUtils.toString(data.toArray());
    }

    public Vector viewSelection(int[] selection) {
        double[] _selection = new double[selection.length];

        for (int i = 0; i < selection.length; i++) {
            _selection[i] = data.getEntry(selection[i]);
        }

        return new Vector(_selection);
    }

    public boolean equals(Object o) {
        if (o == this) return true;

        if (!(o instanceof Vector)) return false;

        Vector v = (Vector) o;

        return MatrixUtils.equals(v.toArray(), this.toArray());
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static Vector serializableInstance() {
        return new Vector(0);
    }

}



