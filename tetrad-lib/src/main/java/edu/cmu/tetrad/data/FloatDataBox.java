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

/**
 * Stores a 2D array of float data. Note that the missing value marker for this
 * box is Float.NaN.
 */
public class FloatDataBox implements DataBox {
    static final long serialVersionUID = 23L;

    /**
     * The stored float data.
     */
    private float[][] data;

    /**
     * Constructs an 2D float array consisting entirely of missing values (-99).
     *
     * @param rows
     * @param cols
     */
    public FloatDataBox(int rows, int cols) {
        this.data = new float[rows][cols];

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                data[i][j] = Float.NaN;
            }
        }
    }

    /**
     * Constructs a new data box using the given 2D float data array as data.
     */
    public FloatDataBox(float[][] data) {
        int length = data[0].length;

        for (float[] datum : data) {
            if (datum.length != length) {
                throw new IllegalArgumentException("All rows must have same length.");
            }
        }

        this.data = data;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static BoxDataSet serializableInstance() {
        return new BoxDataSet(new ShortDataBox(4, 4), null);
    }

    /**
     * @return the number of rows in this data box.
     */
    public int numRows() {
        return data.length;
    }

    /**
     * @return the number of columns in this data box.n
     */
    public int numCols() {
        return data[0].length;
    }

    /**
     * Sets the value at the given row/column to the given Number value.
     * The value used is number.floatValue().
     */
    public void set(int row, int col, Number value) {
        if (value == null) {
            data[row][col] = Float.NaN;
        } else {
            data[row][col] = value.floatValue();
        }
    }

    /**
     * @return the Number value at the given row and column. If the value
     * is missing (-99), null, is returned.
     */
    public Number get(int row, int col) {
        float datum = data[row][col];

        if (datum == Float.NaN) {
            return null;
        } else {
            return datum;
        }
    }

    /**
     * @return a copy of this data box.
     */
    public DataBox copy() {
        FloatDataBox box = new FloatDataBox(numRows(), numCols());

        for (int i = 0; i < numRows(); i++) {
            for (int j = 0; j < numCols(); j++) {
                box.set(i, j, get(i, j));
            }
        }

        return box;
    }

    /**
     * @return a DataBox of type FloatDataBox, but with the given dimensions.
     */
    public DataBox like(int rows, int cols) {
        return new FloatDataBox(rows, cols);
    }
}



