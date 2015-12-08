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

package edu.cmu.tetrad.data;

import edu.cmu.tetrad.util.TetradSerializable;

/**
 * Stores a 2D array of data. Different implementations may store data in
 * different ways, allowing for space or time efficiency.
 *
 * @author Joseph Ramsey
 */
public interface DataBox extends TetradSerializable {
    long serialVersionUID = 23L;

    /**
     * @return the (fixed) number of rows of the dataset.
     */
    int numRows();

    /**
     * @return the (fixed) number of columns of the dataset.
     */
    int numCols();

    /**
     * Sets the value at the given row and column to the given Number. This
     * number may be interpreted differently depending on how values are
     * stored. A value of null is interpreted as a missing value.
     *
     * @throws IllegalArgumentException if the given value cannot be stored
     *                                  (because it's out of range or cannot be converted or whatever).
     */
    void set(int row, int col, Number value) throws IllegalArgumentException;

    /**
     * @return the value at the given row and column as a Number. If the
     * value is missing, null is uniformly returned.
     */
    Number get(int row, int col);

    /**
     * @return a copy of this data box.
     */
    DataBox copy();

    /**
     * @return a new data box of the same type as this one with the given
     * dimensions.
     */
    DataBox like(int rows, int cols);
}



