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

package edu.pitt.dbmi.cg;

import edu.cmu.tetrad.sem.ParamType;

import edu.cmu.tetrad.util.TetradSerializable;

import java.io.IOException;
import java.io.ObjectInputStream;

/**
 * <p>Maps a parameter to the matrix element where its value is stored in the
 * model.</p>
 *
 * @author Chirayu Wongchokprasitti, PhD
 */
public class CgMapping implements TetradSerializable {
    static final long serialVersionUID = 23L;

    /**
     * The SemIm for which this is a mapping.
     *
     * @serial Can't be null.
     */
    private CgIm cgIm;

    /**
     * The parameter this mapping maps.
     *
     * @serial Can't be null.
     */
    private CgParameter parameter;

    /**
     * The 3D double array whose element at (i, j, k) to be manipulated.
     *
     * @serial Can't be null.
     */
    private double[][][][] tensor;

    /**
     * The left-hand coordinate of a[i][j][k][l].
     *
     * @serial Any value.
     */
    private int i;

    /**
     * The middle-left coordinate of a[i][j][k][i].
     *
     * @serial Any value.
     */
    private int j;

    /**
     * The middle-right coordinate of a[i][j][k][l].
     *
     * @serial Any value.
     */
    private int k;

    /**
     * The right-hand coordinate of a[i][j][k][l].
     *
     * @serial Any value.
     */
    private int l;

    /**
     * Constructs matrix new mapping using the given freeParameters.
     *
     * @param parameter The parameter that this maps.
     * @param matrix    The array containing matrix[i][j], the element to be
     *                  manipulated.
     * @param i         Left coordinates of matrix[i][j].
     * @param j         Right coordinate of matrix[i][j].
     */
    public CgMapping(CgIm semIm, CgParameter parameter, double[][][][] tensor,
                   int i, int j, int k, int l) {
        if (semIm == null) {
            throw new NullPointerException("SemIm must not be null.");
        }

        if (parameter == null) {
            throw new NullPointerException("Parameter must not be null.");
        }

        if (tensor == null) {
            throw new NullPointerException("Supplied array must not be null.");
        }

        if (i < 0 || j < 0) {
            throw new IllegalArgumentException("Indices must be non-negative");
        }

        this.cgIm = semIm;
        this.parameter = parameter;
        this.tensor = tensor;
        this.i = i;
        this.j = j;
        this.k = k;
        this.l = l;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static CgMapping serializableInstance() {
        return new CgMapping(CgIm.serializableInstance(),
                CgParameter.serializableInstance(), new double[0][][][],
                1, 1, 0, 0);
    }

    /**
     * Sets the value of the array element at the stored coordinates (i, j).
     * If the array is symmetric sets two elements.
     */
    public void setValue(double x) {
        if (this.cgIm.isParameterBoundsEnforced() &&
                getParameter().getType() == ParamType.VAR && x < 0.0) {
            throw new IllegalArgumentException(
                    "Variances cannot " + "have values <= 0.0: " + x);
        }

        tensor[i][j][k][l] = x;

        if (getParameter().getType() == ParamType.VAR ||
                getParameter().getType() == ParamType.COVAR) {
        	tensor[j][i][k][l] = x;
        }
    }

    /**
     * @return the value of the array element at (i, j).
     */
    public double getValue() {
        return tensor[i][j][k][l];
    }

    /**
     * @return the parameter that this mapping maps.
     */
    public CgParameter getParameter() {
        return this.parameter;
    }

    /**
     * @return a String containing information (array name and values of
     * subscripts) about the array element associated with this mapping.
     */
    public String toString() {
        return "<" + getParameter().getName() + " " + getParameter().getType() +
                "[" + i + "][" + j + "][" + k + "][" + l + "]>";
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

        if (cgIm == null) {
            throw new NullPointerException();
        }

        if (parameter == null) {
            throw new NullPointerException();
        }
    }
}





