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

package edu.cmu.tetrad.gene.tetrad.gene.algorithm.util;

/**
 * Implements a space-efficient Lower Triangular Matrix of
 * elements of type <code>short</code>
 *
 * @author
 * <a href="http://www.eecs.tulane.edu/Saavedra" target="_TOP">Raul Saavedra</a>
 * (<a href="mailto:rsaavedr@ai.uwf.edu">rsaavedr@ai.uwf.edu</A>)
 */

import java.io.FileNotFoundException;
import java.io.IOException;

public class LTMatrix extends BasicLTMatrix {
    protected short[] A;

    /**
     * Creates a lower triangular matrix with <code>nrows</code> rows.
     */
    public LTMatrix(String mname, int nrows) {
        super(mname, nrows);
    }

    /**
     * Creates a lower triangular matrix reading it from file
     * <code>fname</code>.
     */
    public LTMatrix(String fname) throws FileNotFoundException, IOException {
        super(fname);
    }

    /**
     * Initializes the data structure used to hold the contents of the matrix
     */
    protected void initMatrixStorage() {
        this.A = new short[(this.n * (this.n + 1) / 2)];
    }

    /**
     * Casts double x to short and assigns it to element (r,c) This method
     * checks that x can be converted to a short without causing overflow.
     */
    public void setDoubleValue(int r, int c, double x) {
        if ((x < MIN_SHORT) || (x > MAX_SHORT)) {
            throw new IllegalArgumentException(
                    "Double " + x + " cannot be stored as a short");
        }
        setValue(r, c, (short) x);
    }

    /**
     * Assigns integer x to matrix element (r, c).  This method checks that x
     * can be converted to a short without causing overflow.
     */
    public void setValue(int r, int c, int x) {
        if ((x < MIN_SHORT) || (x > MAX_SHORT)) {
            throw new IllegalArgumentException(
                    "Double " + x + " cannot be stored as a short");
        }
        setValue(r, c, (short) x);
    }

    /**
     * Assigns short x to matrix element (r, c)
     */
    public void setValue(int r, int c, short x) {
        if (r < c) {
            upperTriangXcp(r, c);
        }
        if ((r >= this.n) || (c >= this.n) || (r < 0) || (c < 0)) {
            badIndexXcp(r, c);
        }
        this.A[(r * (r + 1) / 2 + c)] = x;
    }

    /**
     * Returns element (r,c) as a double
     */
    public double getDoubleValue(int r, int c) {
        if ((r >= n) || (c >= n) || (r < 0) || (c < 0)) {
            badIndexXcp(r, c);
        }
        return (double) (r >= c ? this.A[r * (r + 1) / 2 + c] : 0);
    }

    /**
     * Returns element (r,c)
     */
    public short getValue(int r, int c) {
        if ((r >= n) || (c >= n) || (r < 0) || (c < 0)) {
            badIndexXcp(r, c);
        }
        return (r >= c ? this.A[r * (r + 1) / 2 + c] : 0);
    }

    /**
     * Assign zero to all elements in the matrix
     */
    public void setAllValuesToZero() {
        for (int i = 0; i < this.A.length; i++) {
            A[i] = 0;
        }
    }

    /**
     * Returns a specially formatted string with all the contents of this
     * matrix
     */
    public String toString() {
        String s = this.getClass().getName() + " " + this.name + "\n" + this.n +
                " // <- Total # rows\n";
        for (int r = 0; r < this.n; r++) {
            //s = s + "/* "+r+" */  ";
            for (int c = 0; c <= r; c++) {
                s = s + this.getValue(r, c) + " ";
            }
            s = s + "\n";
        }
        return s;
    }

    private void upperTriangXcp(int r, int c) {
        throw new IllegalArgumentException("Trying to set a value in (" + r +
                "," + c + ") -> " + "Upper Triangular region ");
    }

}





