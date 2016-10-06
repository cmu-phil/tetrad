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

import java.io.*;

public abstract class BasicLTMatrix extends BasicMatrix {

    /**
     * Creates a lower triangular matrix with <code>nrows</code> rows.
     */
    public BasicLTMatrix(String mname, int nrows) {
        super(mname, nrows);
    }

    /**
     * Creates a lower triangular matrix reading it from file
     * <code>fname</code>. The file has to be an ascii one and follow a
     * particular format:<p> *LtMatrix* [name] [n]<br> [0,0]<br> [1,0] [1,1]<br>
     * [2,0] [2,1] [2,2]<br> :<br> [n-1,0] [n-1,1] ...  [n-1,n-1]<p> </p> Where
     * [n] is the number of rows and columns in the matrix, and [i,j] is element
     * at position i,j in the matrix.<br> For example, a 3x3 identity matrix
     * could be represented as follows:<p> </p> LtMatrix Identity3x3<br> <br> 3
     * // # rows and columns<br> <br> // Matrix elements:<br> 1<br> 0 1<br> 0 0
     * 1<p> </p> Notice there can be slash-slash (and also slash-star) style
     * comments anywhere in the file.  Numbers can be separated by any number of
     * blank delimiters: tabs, spaces, carriage returns.  In the examples above
     * they appear in different lines for more readability of the file. The file
     * may have less elements than the total needed to fill the matrix.  If it
     * has more elements an illegal argument exception will be generated.
     */
    public BasicLTMatrix(String fname)
            throws FileNotFoundException, IOException {
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
                (strmTok.sval.toUpperCase().indexOf("LTMATRIX") < 0)) {
            throw new IllegalArgumentException(
                    "First token does not contain 'LTMATRIX': " + strmTok.sval);
        }
        nt = strmTok.nextToken();
        this.name = strmTok.sval;

        // Read from file # of rows in the matrix
        nt = strmTok.nextToken();
        if (nt != strmTok.TT_NUMBER) {
            throw new IllegalArgumentException(
                    "Error parsing # of rows: " + strmTok.sval);
        }
        this.n = (int) strmTok.nval;
        if (this.n <= 0) {
            throw new IllegalArgumentException("Invalid # nodes " + this.n);
        }

        this.initMatrixStorage();
        // Now read elements from the file
        int row = 0;
        int col = 0;
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
                if (col < row) {
                    col++;
                }
                else {
                    col = 0;
                    row++;
                }
            }
            else {
                throw new IllegalArgumentException("Error parsing element (" +
                        row + "," + col + "): " + strmTok.sval);
            }
        }
        in.close();
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
                s = s + this.getDoubleValue(r, c) + " ";
            }
            s = s + "\n";

        }
        return s;
    }

}





