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
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.util;

import org.apache.commons.math3.linear.RealVector;
import org.ejml.simple.SimpleMatrix;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;

/**
 * Vector wrapping matrix library.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class Vector implements TetradSerializable {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The data.
     */
    private final SimpleMatrix data;

    /**
     * Constructs a new Vector object from an array of double values.
     *
     * @param data the array of double values used to initialize the Vector object
     */
    public Vector(double[] data) {
        this.data = new SimpleMatrix(data);
    }

    /**
     * Creates a new Vector object from a RealVector object.
     *
     * @param v the RealVector object to be used for creating the Vector object
     */
    public Vector(RealVector v) {
        this.data = new SimpleMatrix(v.toArray());
    }

    public Vector(SimpleMatrix v) {
        if (v.getNumCols() != 1) {
            throw new IllegalArgumentException("SimpleMatrix must have one column.");
        }

        this.data = v;
    }

    /**
     * Constructs a new Vector object with the specified size.
     *
     * @param size the size of the vector
     * @throws IllegalArgumentException if the size is negative
     */
    public Vector(int size) {
        this.data = new SimpleMatrix(size, 1);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetrad.util.Vector} object
     */
    public static Vector serializableInstance() {
        return new Vector(0);
    }

    /**
     * <p>assign.</p>
     *
     * @param value a double
     */
    public void assign(double value) {
        for (int i = 0; i < this.data.getNumRows(); i++) {
            this.data.set(i, value);
        }
    }

    /**
     * <p>assign.</p>
     *
     * @param vector a {@link edu.cmu.tetrad.util.Vector} object
     */
    public void assign(Vector vector) {
        for (int i = 0; i < this.data.getNumRows(); i++) {
            this.data.set(i, vector.get(i));
        }
    }

    /**
     * <p>copy.</p>
     *
     * @return a {@link edu.cmu.tetrad.util.Vector} object
     */
    public Vector copy() {
        return new Vector(this.data.copy());
    }

    /**
     * <p>diag.</p>
     *
     * @return a {@link edu.cmu.tetrad.util.Matrix} object
     */
    public Matrix diag() {
        Matrix m = new Matrix(this.data.getNumRows(), this.data.getNumRows());

        for (int i = 0; i < this.data.getNumRows(); i++) {
            m.set(i, i, this.data.get(i));
        }

        return m;
    }

    /**
     * <p>dotProduct.</p>
     *
     * @param v2 a {@link edu.cmu.tetrad.util.Vector} object
     * @return a double
     */
    public double dotProduct(Vector v2) {
        return this.data.dot(v2.data);
    }

    /**
     * <p>get.</p>
     *
     * @param i a int
     * @return a double
     */
    public double get(int i) {
        return this.data.get(i);
    }

    /**
     * <p>like.</p>
     *
     * @return a {@link edu.cmu.tetrad.util.Vector} object
     */
    public Vector like() {
        return new Vector(size());
    }

    /**
     * <p>minus.</p>
     *
     * @param mb a {@link edu.cmu.tetrad.util.Vector} object
     * @return a {@link edu.cmu.tetrad.util.Vector} object
     */
    public Vector minus(Vector mb) {
        return new Vector(this.data.minus(mb.data));
    }

    /**
     * <p>plus.</p>
     *
     * @param mb a {@link edu.cmu.tetrad.util.Vector} object
     * @return a {@link edu.cmu.tetrad.util.Vector} object
     */
    public Vector plus(Vector mb) {
        return new Vector(this.data.plus(mb.data));
    }

    /**
     * <p>scalarMult.</p>
     *
     * @param scalar a double
     * @return a {@link edu.cmu.tetrad.util.Vector} object
     */
    public Vector scalarMult(double scalar) {
        Vector newMatrix = copy();
        for (int i = 0; i < size(); i++) {
            newMatrix.set(i, get(i) * scalar);
        }

        return newMatrix;
    }

    /**
     * <p>set.</p>
     *
     * @param j a int
     * @param v a double
     */
    public void set(int j, double v) {
        this.data.set(j, v);
    }

    /**
     * <p>size.</p>
     *
     * @return a int
     */
    public int size() {
        return this.data.getNumRows();
    }

    /**
     * <p>toArray.</p>
     *
     * @return an array of  objects
     */
    public double[] toArray() {
        return this.data.getColumn(0).getDDRM().data;
    }

    /**
     * <p>toString.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String toString() {
        return this.data.toString();
    }

    /**
     * <p>viewSelection.</p>
     *
     * @param selection an array of  objects
     * @return a {@link edu.cmu.tetrad.util.Vector} object
     */
    public Vector getSelection(int[] selection) {
        double[] _selection = new double[selection.length];

        for (int i = 0; i < selection.length; i++) {
            _selection[i] = this.data.get(selection[i]);
        }

        return new Vector(_selection);
    }

    /**
     * {@inheritDoc}
     */
    public boolean equals(Object o) {
        if (o == this) return true;

        if (!(o instanceof Vector v)) return false;

        return MatrixUtils.equals(v.toArray(), this.toArray());
    }

    /**
     * <p>dot.</p>
     *
     * @param v2 a {@link edu.cmu.tetrad.util.Vector} object
     * @return a double
     */
    public double dot(Vector v2) {
        double sum = 0;
        for (int i = 0; i < size(); i++) {
            sum += get(i) * v2.get(i);
        }
        return sum;
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
     * Reads the object from the specified ObjectInputStream. This method is used during deserialization
     * to restore the state of the object.
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

    public SimpleMatrix getSimpleMatrix() {
        return data;
    }
}



