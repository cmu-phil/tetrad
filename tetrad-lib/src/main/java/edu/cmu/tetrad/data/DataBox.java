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

package edu.cmu.tetrad.data;

import edu.cmu.tetrad.util.TetradSerializable;

/**
 * Stores a 2D array of data. Different implementations may store data in different ways, allowing for space or time
 * efficiency.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public interface DataBox extends TetradSerializable {
    /**
     * Constant <code>serialVersionUID=23L</code>
     */
    long serialVersionUID = 23L;

    /**
     * <p>numRows.</p>
     *
     * @return the (fixed) number of rows of the dataset.
     */
    int numRows();

    /**
     * <p>numCols.</p>
     *
     * @return the (fixed) number of columns of the dataset.
     */
    int numCols();

    /**
     * Sets the value at the given row and column to the given Number. This number may be interpreted differently
     * depending on how values are stored. A value of null is interpreted as a missing value.
     *
     * @param row   the row index.
     * @param col   the column index.
     * @param value the value to store.
     * @throws java.lang.IllegalArgumentException if the given value cannot be stored (because it's out of range or
     *                                            cannot be converted or whatever).
     */
    void set(int row, int col, Number value) throws IllegalArgumentException;

    /**
     * <p>get.</p>
     *
     * @param row the row index.
     * @param col the column index.
     * @return the value at the given row and column as a Number. If the value is missing, null is uniformly returned.
     */
    Number get(int row, int col);

    /**
     * <p>copy.</p>
     *
     * @return a copy of this data box.
     */
    DataBox copy();

    /**
     * <p>viewSelection.</p>
     *
     * @param rows the row indices.
     * @param cols the column indices.
     * @return this data box, restricted to the given rows and columns.
     */
    DataBox viewSelection(int[] rows, int[] cols);

    /**
     * Returns a data box of the same dimensions as this one, without setting any values.
     *
     * @return a new data box.
     */
    DataBox like();
}





