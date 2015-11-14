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

import edu.cmu.tetrad.util.TetradMatrix;

/**
 * Stores a 2D array of short data. Note that the missing value marker for this
 * box is -99.
 */
public class ColtDataBox implements DataBox {
    static final long serialVersionUID = 23L;

    /**
     * The stored short data.
     */
    private TetradMatrix data;

    /**
     * Constructs an 2D COLT array consisting entirely of missing values
     * (Double.NaN).
     * @param rows
     * @param cols
     */
    public ColtDataBox(int rows, int cols) {
        this.data = new TetradMatrix(rows, cols);

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                data.set(i, j, Double.NaN);
            }
        }
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static BoxDataSet serializableInstance() {
        return new BoxDataSet(new ShortDataBox(4, 4), null);
    }

    /**
     * Constructs a new data box using the given 2D short data array as data.
     */
    public ColtDataBox(TetradMatrix data) {
        this.data = data;
    }

    /**
     * @return the number of rows in this data box.
     */
    public int numRows() {
        return data.rows();
    }

    /**
     * @return the number of columns in this data box.n
     */
    public int numCols() {
        return data.columns();
    }

    /**
     * Sets the value at the given row/column to the given Number value.
     * The value used is number.shortValue().
     */
    public void set(int row, int col, Number value) {
        if (value == null) {
            data.set(row, col, Double.NaN);
        } else {
            data.set(row, col, value.doubleValue());
        }
    }

    /**
     * @return the Number value at the given row and column. If the value
     * is missing (-99), null, is returned.
     */
    public Number get(int row, int col) {
        double datum = data.get(row, col);

        if (Double.isNaN(datum)) {
            return null;
        }
        else {
            return datum;
        }
    }

    /**
     * @return a copy of this data box.
     */
    public DataBox copy() {
        ColtDataBox box = new ColtDataBox(numRows(), numCols());

        for (int i = 0; i < numRows(); i++) {
            for (int j = 0; j < numCols(); j++) {
                box.set(i, j, get(i, j));
            }
        }

        return box;
    }

    /**
     * @return a DataBox of type ShortDataBox, but with the given dimensions.
     */
    public DataBox like(int rows, int cols) {
        return new ColtDataBox(rows, cols);
    }
}



