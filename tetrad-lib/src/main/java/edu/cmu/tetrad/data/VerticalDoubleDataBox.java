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

package edu.cmu.tetrad.data;

import edu.cmu.tetrad.graph.Node;

import java.util.ArrayList;
import java.util.List;

/**
 * Stores a 2D array of double data.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class VerticalDoubleDataBox implements DataBox {
    private static final long serialVersionUID = 23L;

    /**
     * The stored double data.
     */
    private final double[][] data;

    /**
     * The number of rows (tracked because it may be zero).
     */
    private final int numRows;

    /**
     * The number of columns (tracked because it may be zero).
     */
    private final int numCols;

    /**
     * Constructs an 2D double array consisting entirely of missing values (Double.NaN).
     *
     * @param rows a int
     * @param cols a int
     */
    public VerticalDoubleDataBox(int rows, int cols) {
        this.data = new double[cols][rows];

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                this.data[j][i] = Double.NaN;
            }
        }

        this.numRows = rows;
        this.numCols = cols;
    }

    /**
     * Constructs a new data box using the given 2D double data array as data.
     *
     * @param data an array of {@link double} objects
     */
    public VerticalDoubleDataBox(double[][] data) {
        int length = data[0].length;

        for (double[] datum : data) {
            if (datum.length != length) {
                throw new IllegalArgumentException("All columns must have same length.");
            }
        }

        this.data = data;
        this.numRows = data[0].length;
        this.numCols = data.length;
    }

    /**
     * Copies the data from the given data box into this one.
     *
     * @param dataBox a {@link edu.cmu.tetrad.data.DataBox} object
     */
    public VerticalDoubleDataBox(DataBox dataBox) {
        this.data = new double[dataBox.numCols()][dataBox.numRows()];

        for (int i = 0; i < dataBox.numRows(); i++) {
            for (int j = 0; j < dataBox.numCols(); j++) {
                this.data[j][i] = dataBox.get(i, j).doubleValue();
            }
        }

        this.numRows = dataBox.numRows();
        this.numCols = dataBox.numCols();
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetrad.data.BoxDataSet} object
     */
    public static BoxDataSet serializableInstance() {
        List<Node> vars = new ArrayList<>();
        for (int i = 0; i < 4; i++) vars.add(new ContinuousVariable("X" + i));
        return new BoxDataSet(new ShortDataBox(4, 4), vars);
    }

    /**
     * <p>numRows.</p>
     *
     * @return the number of rows in this data box.
     */
    public int numRows() {
        return this.numRows;
    }

    /**
     * <p>numCols.</p>
     *
     * @return the number of columns in this data box.
     */
    public int numCols() {
        return this.numCols;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Sets the value at the given row/column to the given Number value. The value used is number.doubleValue().
     */
    public void set(int row, int col, Number value) {
        if (value == null) {
            synchronized (this.data[col]) {
                this.data[col][row] = Double.NaN;
            }
        } else {
            synchronized (this.data[col]) {
                this.data[col][row] = value.doubleValue();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public Number get(int row, int col) {
        return this.data[col][row];
    }

    /**
     * <p>getVariableVectors.</p>
     *
     * @return an array of {@link double} objects
     */
    public double[][] getVariableVectors() {
        if (this.numCols == 0 || this.numRows == 0) {
            return new double[0][0];
        }

        return this.data;
    }

    /**
     * <p>copy.</p>
     *
     * @return a copy of this data box.
     */
    public DataBox copy() {
        double[][] copy = new double[this.data.length][this.data[0].length];

        for (int i = 0; i < this.data.length; i++) {
            System.arraycopy(this.data[i], 0, copy[i], 0, this.data[0].length);
        }

        return new VerticalDoubleDataBox(copy);
    }

    /**
     * <p>like.</p>
     *
     * @return a DataBox of type DoubleDataBox, but with the given dimensions.
     */
    public DataBox like() {
        int[] rows = new int[numRows()];
        int[] cols = new int[numCols()];

        for (int i = 0; i < numRows(); i++) rows[i] = i;
        for (int j = 0; j < numCols(); j++) cols[j] = j;

        return viewSelection(rows, cols);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataBox viewSelection(int[] rows, int[] cols) {
        DataBox _dataBox = new VerticalDoubleDataBox(rows.length, cols.length);

        for (int i = 0; i < rows.length; i++) {
            for (int j = 0; j < cols.length; j++) {
                _dataBox.set(i, j, get(rows[i], cols[j]));
            }
        }

        return _dataBox;
    }
}


