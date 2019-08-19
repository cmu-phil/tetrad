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

import edu.cmu.tetrad.graph.Node;

import java.util.ArrayList;
import java.util.List;

/**
 * Stores a 2D array of int data. Note that the missing value marker for this
 * box is -99.
 */
public class VerticalIntDataBox implements DataBox {
    static final long serialVersionUID = 23L;

    /**
     * The stored int data.
     */
    private final int[][] data;


    /**
     * The number of rows (tracked because it may be zero).
     */
    private int numRows = 0;

    /**
     * The number of columns (tracked because it may be zero).
     */
    private int numCols = 0;

    /**
     * Constructs an 2D int array consisting entirely of missing values
     * (int.NaN).
     */
    public VerticalIntDataBox(int rows, int cols) {
        this.data = new int[cols][rows];

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                data[j][i] = -99;
            }
        }

        this.numRows = rows;
        this.numCols = cols;
    }

    /**
     * Constructs a new data box using the given 2D int data array as data.
     */
    public VerticalIntDataBox(int[][] data) {
        int length = data[0].length;

        for (int[] datum : data) {
            if (datum.length != length) {
                throw new IllegalArgumentException("All columns must have same length.");
            }
        }

        this.numRows = data[0].length;
        this.numCols = data.length;

        this.data = data;
    }

    public VerticalIntDataBox(DataBox dataBox) {
        data = new int[dataBox.numCols()][dataBox.numRows()];

        for (int i = 0; i < dataBox.numRows(); i++) {
            for (int j = 0; j < dataBox.numCols(); j++) {
                data[j][i] = dataBox.get(i, j).intValue();
            }
        }

        numRows = dataBox.numRows();
        numCols = dataBox.numCols();
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static BoxDataSet serializableInstance() {
        List<Node> vars = new ArrayList<>();
        for (int i = 0; i < 4; i++) vars.add(new ContinuousVariable("X" + i));
        return new BoxDataSet(new ShortDataBox(4, 4), vars);
    }

    /**
     * @return the number of rows in this data box.
     */
    public int numRows() {
        return numRows;
    }

    /**
     * @return the number of columns in this data box.
     */
    public int numCols() {
        return numCols;
    }

    /**
     * Sets the value at the given row/column to the given Number value.
     * The value used is number.intValue().
     */
    public void set(int row, int col, Number value) {
        int[] ints = data[col];

        if (value == null) {
            synchronized (ints) {
                ints[row] = -99;
            }
        } else {
            synchronized (ints) {
                ints[row] = value.intValue();
            }
        }
    }

    /**
     * @return the Number value at the given row and column. If the value
     * is missing (-99), null, is returned.
     */
    public Number get(int row, int col) {
        int datum = data[col][row];

        if (datum == -99) {
            return null;
        } else {
            return datum;
        }
    }

    public int[][] getVariableVectors() {
        return data;
    }

    /**
     * @return a copy of this data box.
     */
    public DataBox copy() {
        double[][] copy = new double[numCols()][numRows()];

        for (int i = 0; i < numRows(); i++) {
            for (int j = 0; j < numCols(); j++) {
                copy[j][i] = data[j][i];
            }
        }

        return new VerticalDoubleDataBox(copy);
    }

    /**
     * @return a DataBox of type intDataBox, but with the given dimensions.
     */
    public DataBox like() {
        return new VerticalIntDataBox(numRows, numCols);
    }

    @Override
    public DataBox viewSelection(int[] rows, int[] cols) {
        DataBox _dataBox = new VerticalIntDataBox(rows.length, cols.length);

        for (int i = 0; i < rows.length; i++) {
            for (int j = 0; j < cols.length; j++) {
                _dataBox.set(i, j, get(rows[i], cols[j]));
            }
        }

        return _dataBox;
    }
}


