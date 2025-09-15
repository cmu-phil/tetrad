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

import edu.cmu.tetrad.graph.Node;

import java.util.ArrayList;
import java.util.List;

/**
 * Stores a 2D array of float data. Note that the missing value marker for this box is Float.NaN.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class FloatDataBox implements DataBox {
    private static final long serialVersionUID = 23L;

    /**
     * The stored float data.
     */
    private final float[][] data;

    /**
     * Constructs an 2D float array consisting entirely of missing values (-99).
     */
    private FloatDataBox(int rows, int cols) {
        this.data = new float[rows][cols];

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                this.data[i][j] = Float.NaN;
            }
        }
    }

    /**
     * Constructs a new data box using the given 2D float data array as data.
     *
     * @param data an array of  objects
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
        return this.data.length;
    }

    /**
     * <p>numCols.</p>
     *
     * @return the number of columns in this data box.n
     */
    public int numCols() {
        return this.data[0].length;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Sets the value at the given row/column to the given Number value. The value used is number.floatValue().
     */
    public void set(int row, int col, Number value) {
        if (value == null) {
            synchronized (this.data) {
                this.data[row][col] = Float.NaN;
            }
        } else {
            synchronized (this.data) {
                this.data[row][col] = value.floatValue();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public Number get(int row, int col) {
        float datum = this.data[row][col];

        if (Double.isNaN(datum)) {
            return null;
        } else {
            return datum;
        }
    }

    /**
     * <p>copy.</p>
     *
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
     * <p>like.</p>
     *
     * @return a DataBox of type FloatDataBox, but with the given dimensions.
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
        DataBox _dataBox = new FloatDataBox(rows.length, cols.length);

        for (int i = 0; i < rows.length; i++) {
            for (int j = 0; j < cols.length; j++) {
                _dataBox.set(i, j, get(rows[i], cols[j]));
            }
        }

        return _dataBox;
    }
}




