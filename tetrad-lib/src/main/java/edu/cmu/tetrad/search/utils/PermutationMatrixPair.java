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

package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.search.IcaLingD;
import edu.cmu.tetrad.search.IcaLingam;
import edu.cmu.tetrad.util.Matrix;

import java.util.Arrays;

/**
 * Stores a matrix together with a row and column permutation. if either of these is null in the constructor, the
 * identity permtuation will be used. Returns the matrix permuted by these row and column permutations.
 *
 * @author josephramsey
 * @see IcaLingam
 * @see IcaLingD
 * @version $Id: $Id
 */
public class PermutationMatrixPair {
    private final int[] rowPerm;
    private final int[] colPerm;
    private final Matrix M;

    /**
     * Constructs with a given matrix M and a row and column permutation (which may be null).
     *
     * @param M       The matrix to be permuted.
     * @param rowPerm The row permutation for M; if null the identity permutation ([0 1 2...#rows]) will be used.
     * @param colPerm The row permutation for M; if null the identity permutation ([0 1 2...#cols]) will be used.
     */
    public PermutationMatrixPair(Matrix M, int[] rowPerm, int[] colPerm) {
        if (rowPerm == null) {
            rowPerm = new int[M.getNumRows()];
            for (int i = 0; i < M.getNumRows(); i++) rowPerm[i] = i;
        }

        if (colPerm == null) {
            colPerm = new int[M.getNumColumns()];
            for (int i = 0; i < M.getNumColumns(); i++) colPerm[i] = i;
        }

        this.rowPerm = Arrays.copyOf(rowPerm, rowPerm.length);
        this.colPerm = Arrays.copyOf(colPerm, colPerm.length);
        this.M = M.copy();
    }

    /**
     * Returns W, permuted rowwise by the permutation passed in through the constructor.
     *
     * @return The matrix, permuted columnwise and rowwise, by the specified column and row permutations.
     */
    public Matrix getPermutedMatrix() {
        return M.getSelection(rowPerm, colPerm);
    }

    /**
     * <p>Getter for the field <code>rowPerm</code>.</p>
     *
     * @return The row permutation.
     */
    public int[] getRowPerm() {
        return Arrays.copyOf(rowPerm, rowPerm.length);
    }

    /**
     * <p>Getter for the field <code>colPerm</code>.</p>
     *
     * @return The column permutation.
     */
    public int[] getColPerm() {
        return Arrays.copyOf(colPerm, colPerm.length);
    }

    /**
     * Prints a string representation of this.
     *
     * @return The row and columns permutations, followed by the permuted matrix.
     */
    public String toString() {
        return "Row perm " + Arrays.toString(this.rowPerm)
                + "\nCol perm = " + Arrays.toString(this.colPerm)
                + "\nPermuted matrix : " + getPermutedMatrix();
    }
}



