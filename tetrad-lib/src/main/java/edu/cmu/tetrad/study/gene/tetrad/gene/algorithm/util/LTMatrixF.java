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

package edu.cmu.tetrad.study.gene.tetrad.gene.algorithm.util;

import java.io.IOException;

/**
 * Implements a space-efficient Lower Triangular Matrix of elements of type <code>float</code>
 *
 * @author <a href="http://www.eecs.tulane.edu/Saavedra" target="_TOP">Raul Saavedra</a>
 * (<a href="mailto:rsaavedr@ai.uwf.edu">rsaavedr@ai.uwf.edu</A>)
 * @version $Id: $Id
 */
public class LTMatrixF extends BasicLTMatrix {
    protected float[] A;

    /**
     * Creates a lower triangular matrix with <code>nrows</code> rows.
     *
     * @param mname a {@link java.lang.String} object
     * @param nrows a int
     */
    public LTMatrixF(String mname, int nrows) {
        super(mname, nrows);
    }

    /**
     * Creates a lower triangular matrix reading it from file
     * <code>fname</code>.
     *
     * @param fname a {@link java.lang.String} object
     * @throws java.io.IOException if any.
     */
    public LTMatrixF(String fname) throws IOException {
        super(fname);
    }

    /**
     * Initializes the data structure used to hold the contents of the matrix
     */
    protected void initMatrixStorage() {
        this.A = new float[(this.n * (this.n + 1) / 2)];
    }

    /**
     * {@inheritDoc}
     * <p>
     * Casts double value x to float and assigns it to element (r,c) This method checks that x can be converted to a
     * float without causing overflow.
     */
    public void setDoubleValue(int r, int c, double x) {
        setValue(r, c, x);
    }

    /**
     * Assigns double x to matrix element (r, c).  This method checks that x can be converted to a float without causing
     * overflow.
     *
     * @param r a int
     * @param c a int
     * @param x a double
     */
    public void setValue(int r, int c, double x) {
        if ((x < BasicMatrix.MIN_FLOAT) || (x > BasicMatrix.MAX_FLOAT)) {
            throw new IllegalArgumentException(
                    "Double " + x + " cannot be stored as a float");
        }
        setValue(r, c, (float) x);
    }

    /**
     * Assigns float x to matrix element (r, c)
     *
     * @param r a int
     * @param c a int
     * @param x a float
     */
    public void setValue(int r, int c, float x) {
        if (r < c) {
            upperTriangXcp(r, c);
        }
        if ((r >= this.n) || (c >= this.n) || (r < 0) || (c < 0)) {
            badIndexXcp(r, c);
        }
        this.A[(r * (r + 1) / 2 + c)] = x;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns element (r,c) as a double
     */
    public double getDoubleValue(int r, int c) {
        if ((r >= this.n) || (c >= this.n) || (r < 0) || (c < 0)) {
            badIndexXcp(r, c);
        }
        return r >= c ? this.A[r * (r + 1) / 2 + c] : 0;
    }

    /**
     * Returns element (r,c)
     *
     * @param r a int
     * @param c a int
     * @return a float
     */
    public float getValue(int r, int c) {
        if ((r >= this.n) || (c >= this.n) || (r < 0) || (c < 0)) {
            badIndexXcp(r, c);
        }
        return (r >= c ? this.A[r * (r + 1) / 2 + c] : 0);
    }

    /**
     * Assign zero to all elements in the matrix
     */
    public void setAllValuesToZero() {
        for (int i = 0; i < this.A.length; i++) {
            this.A[i] = 0;
        }
    }

    private void upperTriangXcp(int r, int c) {
        throw new IllegalArgumentException("Trying to set a value in (" + r +
                                           "," + c + ") -> " + "Upper Triangular region ");
    }

}






