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

package edu.cmu.tetrad.util;


/**
 * @author Joseph Ramsey
 * @return values of the given square matrix, where the indices are remapped via
 * the given indices array. If the supplied matrix is 6 x 6, for example, and
 * the indices set are [5 4 2 1], then getValue(1, 2) will return element [4][2]
 * of the given matrix.
 */
public final class IndexedMatrix {

    /**
     * A square matrix.
     */
    private final double[][] matrix;

    /**
     * Indices into this matrix, implicitly specifying a submatrix.
     */
    private int[] indices;

    /**
     * Constructs a new IndexedMatrix for the given matrix.
     *
     * @param matrix The wrapped matrix.
     */
    public IndexedMatrix(double[][] matrix) {
        assert MatrixUtils.isSquare(matrix);
        this.matrix = new TetradMatrix(matrix).toArray();
        setIndices(new int[0]);
    }

    /**
     * @return Ibid.
     */
    public final int[] getIndices() {
        return indices;
    }

    /**
     * Sets the index array. The index array must be of length <= the order of
     * the matrix, each element of which is distinct and < the order of the
     * matrix.
     *
     * @param indices The indices of the submatrix desired.
     */
    public final void setIndices(int[] indices) {
        if (indices == null) {
            throw new NullPointerException("Permutation must not be null.");
        }
        if (!isLegal(indices)) {
            throw new IllegalArgumentException(
                    "Illegal index array: " + ArrUtils.toString(indices));
        }
        this.indices = ArrUtils.copy(indices);
    }

    /**
     * @param i The row value in the remapped indices of the cell desired.
     * @param j The column value in teh remapped indices of the cell desired.
     * @return Ibid.
     */
    public final double getValue(int i, int j) {
        return matrix[indices[i]][indices[j]];
    }

    /**
     * @param indices The array indices to check.
     * @return Ibid.
     */
    @SuppressWarnings({"BooleanMethodIsAlwaysInverted"})
    private boolean isLegal(int[] indices) {
        int[] check = new int[matrix.length];
        for (int indice : indices) {
            if (indice < 0 || indice >= matrix.length) {
                return false;
            }
            check[indice]++;
        }

        for (int i = 0; i < matrix.length; i++) {
            if (check[i] > 1) {
                return false;
            }
        }

        return true;
    }
}





