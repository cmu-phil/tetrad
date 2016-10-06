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
 * Implements a space-efficient symmetric matrix
 * (of elements of type <code>short</code>),
 * storing only the lower triangular portion of it
 *
 * @author
 * <a href="http://www.eecs.tulane.edu/Saavedra" target="_TOP">Raul Saavedra</a>
 * (<a href="mailto:rsaavedr@ai.uwf.edu">rsaavedr@ai.uwf.edu</A>)
 */

import java.io.FileNotFoundException;
import java.io.IOException;

public class SymMatrixF extends LTMatrixF {

    /**
     * Creates a symmetric matrix with <code>nrows</code> rows.
     */
    public SymMatrixF(String mname, int nrows) {
        super(mname, nrows);
    }

    /**
     * Creates a symmetric matrix reading it from file <code>fname</code>.
     */
    public SymMatrixF(String fname) throws FileNotFoundException, IOException {
        super(fname);
    }

    /**
     * Sets the value of element (<code>row</code>,<code>col</code>) to
     * <code>x</code>
     */
    public void setValue(int row, int col, double x) {
        if (row >= col) {
            super.setValue(row, col, x);
        }
        else {
            super.setValue(col, row, x);
        }
    }

    /**
     * Sets the value of element (<code>row</code>,<code>col</code>) to
     * <code>x</code>
     */
    public void setValue(int row, int col, float x) {
        if (row >= col) {
            super.setValue(row, col, x);
        }
        else {
            super.setValue(col, row, x);
        }
    }

    /**
     * Returns the value of element at (<code>row</code>,<code>col</code>)
     */
    public float getValue(int row, int col) {
        return (row >= col ? super.getValue(row, col) : super.getValue(col,
                row));
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
                s = s + this.getValue(r, c) + " ";
            }
            s = s + "\n";
        }
        return s;
    }
}




