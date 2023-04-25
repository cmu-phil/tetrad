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

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.util.Matrix;

import java.util.Arrays;

public class PermutationMatrixPair {

    private final int[] rowPerm;
    private final int[] colPerm;
    private final Matrix w;

    public PermutationMatrixPair(int[] rowPerm, int[] colPerm, Matrix w) {
        if (rowPerm == null) {
            rowPerm = new int[w.rows()];
            for (int i = 0; i < w.rows(); i++) rowPerm[i] = i;
        }

        if (colPerm == null) {
            colPerm = new int[w.columns()];
            for (int i = 0; i < w.columns(); i++) colPerm[i] = i;
        }

        this.rowPerm = Arrays.copyOf(rowPerm, rowPerm.length);
        this.colPerm = Arrays.copyOf(colPerm, colPerm.length);
        this.w = w.getSelection(rowPerm, colPerm);
    }

    /**
     * Returns W, permuted rowwise by the permutation passed in throught he constructor.
     * @return The row-permuted W.
     */
    public Matrix getPermutedMatrix() {
        return this.w;
    }

    public String toString() {
        return "Row perm " + Arrays.toString(this.rowPerm)
                + "\nCol perm = " + Arrays.toString(this.colPerm)
                + "\nmatrix W : " + this.w;
    }

    public int[] getRowPerm() {
        return Arrays.copyOf(rowPerm, rowPerm.length);
    }

    public int[] getColPerm() {
        return Arrays.copyOf(colPerm, colPerm.length);
    }
}



