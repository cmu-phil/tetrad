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
 * Basic functionality of a Matrix
 *
 * @author
 * <a href="http://www.eecs.tulane.edu/Saavedra" target="_TOP">Raul Saavedra</a>
 * (<a href="mailto:rsaavedr@ai.uwf.edu">rsaavedr@ai.uwf.edu</A>)
 */

import java.io.*;

public abstract class BasicMatrix {
    protected String name;
    protected int n;

    /**
     * Maximum short value
     */
    public static final short MAX_SHORT = Short.MAX_VALUE;

    /**
     * Minimum short getValue
     */
    public static final short MIN_SHORT = Short.MIN_VALUE;

    /**
     * Maximum int value
     */
    public static final int MAX_INT = Integer.MAX_VALUE;

    /**
     * Minimum int value
     */
    public static final int MIN_INT = Integer.MIN_VALUE;

    /**
     * Maximum float value
     */
    public static final float MAX_FLOAT = Float.MAX_VALUE;

    /**
     * Minimum float value
     */
    public static final float MIN_FLOAT = -MAX_FLOAT;

    /**
     * No parameters constructor, only used within the package
     */
    protected BasicMatrix() {
    }

    /**
     * Creates a matrix with <code>nrows</code> rows, and with name
     * <code>mname</code>.
     */
    public BasicMatrix(String mname, int nrows) {
        this.name = mname;
        if (nrows <= 0) {
            throw new IllegalArgumentException("Invalid # nodes " + nrows);
        }
        this.n = nrows;
        this.initMatrixStorage();
    }

    /**
     * Creates a matrix reading it from a file <code>fname</code>. The file has
     * to be an ascii one and follow a particular format:<p> *MATRIX*  [matrix
     * name] <br> [n]<br> [0, 0 ] [ 0, 1] ...  [ 0, n-1]<br> [1, 0 ] [ 1, 1] ...
     * [ 1, n-1]<br> :<br> [n-1,0] [n-1,1] ...  [n-1,n-1]<p> </p> First token
     * should be a word with "MATRIX" as a substring (case insens.), followed by
     * the name of the matrix (one word). [n] is the number of rows and columns
     * in the matrix, and [i,j] is element at position i,j in the matrix.<br>
     * For example, a 3x3 identity matrix could be represented as follows:<p>
     * </p> MATRIX Identity3x3<br> <br> 3  // # rows and columns<br> <br> //
     * Matrix elements:<br> 1 0 0<br> 0 1 0<br> 0 0 1<p> </p> Notice there can
     * be slash-slash (and also slash-star) style comments anywhere in the file.
     * Numbers can be separated by any number of blank delimiters: tabs, spaces,
     * carriage returns.  In the examples above they appear in different lines
     * for more readability of the file. The file may have less elements than
     * the total needed to fill the matrix.  If it has more elements an illegal
     * argument exception will be generated.
     */
    public BasicMatrix(String fname) throws FileNotFoundException, IOException {
        // Create and prepare stream tokenizer
        File f = new File(fname);
        BufferedReader in = new BufferedReader(new FileReader(f));
        StreamTokenizer strmTok = new StreamTokenizer(in);
        strmTok.slashStarComments(true);
        strmTok.slashSlashComments(true);
        strmTok.parseNumbers();
        strmTok.wordChars('_', '_'); // underscore is a word char

        // Read matrix name
        int nt = strmTok.nextToken();
        if ((strmTok.sval == null) ||
                (strmTok.sval.toUpperCase().indexOf("MATRIX") < 0)) {
            throw new IllegalArgumentException(
                    "First token does not contain 'MATRIX': " + strmTok.sval);
        }
        nt = strmTok.nextToken();
        this.name = strmTok.sval;

        // Read from file # of rows in the matrix
        nt = strmTok.nextToken();
        if (nt != strmTok.TT_NUMBER) {
            throw new IllegalArgumentException(
                    "Error parsing # of rows: " + strmTok.sval);
        }
        int vnrows = (int) strmTok.nval;
        if (vnrows <= 0) {
            throw new IllegalArgumentException("Invalid # rows " + vnrows);
        }
        this.n = vnrows;
        this.initMatrixStorage();

        // Now read elements from the file
        int row = 0;
        int col = 0;
        int val = 0;
        while (true) {
            try {
                nt = strmTok.nextToken();
            }
            catch (IOException e) {
                break;
            }
            if (nt == strmTok.TT_EOF) {
                break;
            }
            if (nt == strmTok.TT_NUMBER) {
                this.setDoubleValue(row, col, strmTok.nval);
                col++;
                if (col == vnrows) {
                    col = 0;
                    row++;
                }
            }
            else {
                throw new IllegalArgumentException("Error parsing element [" +
                        row + "," + col + "]: " + strmTok.sval);
            }
        }
        in.close();
    }

    /**
     * Returns # rows ( == # columns) of this matrix
     */
    public int getSize() {
        return this.n;
    }

    /**
     * Sets the name of this matrix
     */
    public void setName(String newName) {
        this.name = newName;
    }

    /**
     * Returns name of this matrix
     */
    public String getName() {
        return this.name;
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
            for (int c = 0; c < this.n; c++) {
                s = s + this.getDoubleValue(r, c) + " ";
            }
            s = s + "\n";
        }
        return s;
    }

    protected void badIndexXcp(int r, int c) {
        throw new IllegalArgumentException(
                "Bad index (" + r + "," + c + ") for matrix of size " + this.n);
    }

    /**
     * Initializes the data structure used to hold the contents of the matrix
     */
    protected abstract void initMatrixStorage();

    /**
     * Returns the value stored at element (r,c) as a double
     */
    public abstract double getDoubleValue(int r, int c);

    /**
     * Assigns double value x in matrix element (r, c). Notice the presence
     * of this method does not really force the implementing class to actually
     * store doubles.
     */
    public abstract void setDoubleValue(int r, int c, double x);

    /**
     * Assign zero to all elements in the matrix
     */
    public abstract void setAllValuesToZero();

}





