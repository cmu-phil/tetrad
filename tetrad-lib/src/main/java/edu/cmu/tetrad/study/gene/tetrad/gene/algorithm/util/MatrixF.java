///////////////////////////////////////////////////////////////////////////////
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

package edu.cmu.tetrad.study.gene.tetrad.gene.algorithm.util;

import java.io.IOException;

/**
 * Implements a Matrix of elements of type <code>float</code>
 *
 * @author <a href="http://www.eecs.tulane.edu/Saavedra" target="_TOP">Raul Saavedra</a>
 * (<a href="mailto:rsaavedr@ai.uwf.edu">rsaavedr@ai.uwf.edu</A>)
 * @version $Id: $Id
 */
public class MatrixF extends BasicMatrix {
    protected float[][] A;

    /**
     * Creates a matrix with name <code>mname</code>, and <code>nrows</code> rows.
     *
     * @param mname a {@link java.lang.String} object
     * @param nrows a int
     */
    public MatrixF(String mname, int nrows) {
        super(mname, nrows);
    }

    /**
     * Creates a matrix reading it from a file <code>fname</code>. The file has to be an ascii one and follow a
     * particular format:<p> *MATRIX*  [matrix name] <br> [n]<br> [0, 0 ] [ 0, 1] ...  [ 0, n-1]<br> [1, 0 ] [ 1, 1] ...
     * [ 1, n-1]<br> :<br> [n-1,0] [n-1,1] ...  [n-1,n-1]<p> First token should be a word with "MATRIX" as a substring
     * (case insens.), followed by the name of the matrix (one word). [n] is the number of rows and columns in the
     * matrix, and [i,j] is element at position i,j in the matrix.<br> For example, a 3x3 identity matrix could be
     * represented as follows:<p> MATRIX Identity3x3<br> <br> 3  // # rows and columns<br> <br> // Matrix elements:<br>
     * 1 0 0<br> 0 1 0<br> 0 0 1<p> Notice there can be slash-slash (and also slash-star) style comments anywhere in the
     * file. Numbers can be separated by any number of blank delimiters: tabs, spaces, carriage returns.  In the
     * examples above they appear in different lines for more readability of the file. The file may have less elements
     * than the total needed to fill the matrix.  If it has more elements an illegal argument exception will be
     * generated.
     *
     * @param fname a {@link java.lang.String} object
     * @throws java.io.IOException if any.
     */
    public MatrixF(String fname) throws IOException {
        super(fname);
    }

    /**
     * Initializes the data structure used to hold the contents of the matrix
     */
    protected void initMatrixStorage() {
        this.A = new float[this.n][this.n];
    }

    /**
     * {@inheritDoc}
     * <p>
     * Casts double value x to float and assigns it to element (r,c)
     */
    public void setDoubleValue(int r, int c, double x) {
        if ((r >= this.n) || (c >= this.n) || (r < 0) || (c < 0)) {
            badIndexXcp(r, c);
        }
        if ((x < MIN_FLOAT) || (x > MAX_FLOAT)) {
            throw new IllegalArgumentException(
                    "Integer " + x + " cannot be stored as a float");
        }
        this.A[r][c] = (float) x;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the value stored at element (r,c) as a double
     */
    public double getDoubleValue(int r, int c) {
        if ((r >= this.n) || (c >= this.n) || (r < 0) || (c < 0)) {
            badIndexXcp(r, c);
        }
        return this.A[r][c];
    }

    /**
     * Assigns float x to matrix element at (r, c)
     *
     * @param r a int
     * @param c a int
     * @param x a float
     */
    public void setValue(int r, int c, float x) {
        if ((r >= this.n) || (c >= this.n) || (r < 0) || (c < 0)) {
            badIndexXcp(r, c);
        }
        this.A[r][c] = x;
    }

    /**
     * Assigns double x to matrix element at (r, c).  This method checks that the double x can be converted to a float
     * without causing overflow.
     *
     * @param r a int
     * @param c a int
     * @param x a double
     */
    public void setValue(int r, int c, double x) {
        if ((x < MIN_FLOAT) || (x > MAX_FLOAT)) {
            throw new IllegalArgumentException(
                    "Integer " + x + " cannot be stored as a float");
        }
        if ((r >= this.n) || (c >= this.n) || (r < 0) || (c < 0)) {
            badIndexXcp(r, c);
        }
        this.A[r][c] = (float) x;
    }

    /**
     * Returns the value stored at element (r,c)
     *
     * @param r a int
     * @param c a int
     * @return a float
     */
    public float getValue(int r, int c) {
        if ((r >= this.n) || (c >= this.n) || (r < 0) || (c < 0)) {
            badIndexXcp(r, c);
        }
        return this.A[r][c];
    }

    /**
     * Assign zero to all elements in the matrix
     */
    public void setAllValuesToZero() {
        for (int i = 0; i < this.n; i++) {
            for (int j = 0; j < this.n; j++) {
                this.A[i][j] = 0;
            }
        }
    }

}





