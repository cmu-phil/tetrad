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
 * Implements a space-efficient symmetric matrix (of elements of type <code>short</code>), storing only the lower
 * triangular portion of it
 *
 * @author <a href="http://www.eecs.tulane.edu/Saavedra" target="_TOP">Raul Saavedra</a>
 * (<a href="mailto:rsaavedr@ai.uwf.edu">rsaavedr@ai.uwf.edu</A>)
 * @version $Id: $Id
 */
public class SymMatrixF extends LTMatrixF {

    /**
     * Creates a symmetric matrix with <code>nrows</code> rows.
     *
     * @param mname a {@link java.lang.String} object
     * @param nrows a int
     */
    public SymMatrixF(String mname, int nrows) {
        super(mname, nrows);
    }

    /**
     * Creates a symmetric matrix reading it from file <code>fname</code>.
     *
     * @param fname a {@link java.lang.String} object
     * @throws java.io.IOException if any.
     */
    public SymMatrixF(String fname) throws IOException {
        super(fname);
    }

    /**
     * Sets the value of element (<code>row</code>,<code>col</code>) to
     * <code>x</code>
     *
     * @param row a int
     * @param col a int
     * @param x   a double
     */
    public void setValue(int row, int col, double x) {
        if (row >= col) {
            super.setValue(row, col, x);
        } else {
            super.setValue(col, row, x);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Sets the value of element (<code>row</code>,<code>col</code>) to
     * <code>x</code>
     */
    public void setValue(int row, int col, float x) {
        if (row >= col) {
            super.setValue(row, col, x);
        } else {
            super.setValue(col, row, x);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the value of element at (<code>row</code>,<code>col</code>)
     */
    public float getValue(int row, int col) {
        return (row >= col ? super.getValue(row, col) : super.getValue(col,
                row));
    }

    /**
     * Returns a specially formatted string with all the contents of this matrix
     *
     * @return a {@link java.lang.String} object
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





