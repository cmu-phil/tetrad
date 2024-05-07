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

import java.io.Serial;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Stores a 2D array of double continuousData. Note that the missing value marker for this box is -99.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class MixedDataBox implements DataBox {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The variables in the dataset.
     */
    private final List<Node> variables;

    /**
     * The number of rows in the dataset.
     */
    private final int numRows;

    /**
     * The continuous data.
     */
    private double[][] continuousData;

    /**
     * The discrete data.
     */
    private int[][] discreteData;

    /**
     * The variables here are used only to determine which columns are discrete and which are continuous; bounds
     * checking is not done.
     *
     * @param variables a {@link java.util.List} object
     * @param numRows   a int
     */
    public MixedDataBox(List<Node> variables, int numRows) {
        this.variables = variables;
        this.numRows = numRows;

        this.continuousData = new double[variables.size()][];
        this.discreteData = new int[variables.size()][];

        for (int j = 0; j < variables.size(); j++) {
            if (variables.get(j) instanceof ContinuousVariable) {
                this.continuousData[j] = new double[numRows];
                Arrays.fill(this.continuousData[j], Double.NaN);
            } else if (variables.get(j) instanceof DiscreteVariable) {
                this.discreteData[j] = new int[numRows];
                Arrays.fill(this.discreteData[j], -99);
            }
        }


    }

    /**
     * This constructor allows other data readers to populate the fields directly.
     *
     * @param variables      list of discrete and continuous variables
     * @param numRows        number of cases in the dataset
     * @param continuousData continuous data
     * @param discreteData   discrete data
     */
    public MixedDataBox(List<Node> variables, int numRows, double[][] continuousData, int[][] discreteData) {
        this.variables = variables;
        this.numRows = numRows;
        this.continuousData = continuousData;
        this.discreteData = discreteData;

        if (variables == null) {
            throw new IllegalArgumentException("Parameter variables cannot be null.");
        }
        if (numRows < 0) {
            throw new IllegalArgumentException("Parameter numRows cannot be negative.");
        }
        if (continuousData == null) {
            throw new IllegalArgumentException("Parameter continuousData cannot be null.");
        }
        if (discreteData == null) {
            throw new IllegalArgumentException("Parameter discreteData cannot be null.");
        }

        // ensure the number of variables for both datasets are the same
        int numOfVars = variables.size();
        if (continuousData.length != numOfVars) {
            throw new IllegalArgumentException(String.format("Continuous Data: expect %d variables but found %d.", numOfVars, continuousData.length));
        }
        if (discreteData.length != numOfVars) {
            throw new IllegalArgumentException(String.format("Discrete Data: expect %d variables but found %d.", numOfVars, discreteData.length));
        }

        for (int i = 0; i < numOfVars; i++) {
            // ensure there is data for either dataset, not both
            if ((continuousData[i] == null) == (discreteData[i] == null)) {
                String errMsg = String.format("Variable at index %d either has data for both discrete and continuous or has no data for both.", i);
                throw new IllegalArgumentException(errMsg);
            }

            // ensure the number of rows for both dataset is consistent
            if (continuousData[i] != null && continuousData[i].length != numRows) {
                String errMsg = String.format("Continuous Data: Inconsistent row number at index %d.  Expect %d rows but found %d.", i, numRows, continuousData[i].length);
                throw new IllegalArgumentException(errMsg);
            }
            if (discreteData[i] != null && discreteData[i].length != numRows) {
                String errMsg = String.format("Discrete Data: Inconsistent row number at index %d.  Expect %d rows but found %d.", i, numRows, discreteData[i].length);
                throw new IllegalArgumentException(errMsg);
            }
        }
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
     * {@inheritDoc}
     */
    @Override
    public int numRows() {
        return this.numRows;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int numCols() {
        return this.variables.size();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Sets the value at the given row/column to the given Number value. The value used is number.doubleValue().
     */
    @Override
    public void set(int row, int col, Number value) {
        if (value == null) {
            if (this.continuousData[col] != null) {
                this.continuousData[col][row] = Double.NaN;
            } else if (this.discreteData[col] != null) {
                this.discreteData[col][row] = -99;
            } else {
                throw new IllegalArgumentException("Indices out of bounds or null value.");
            }
        } else {
            if (this.continuousData[col] != null) {
                this.continuousData[col][row] = value.doubleValue();
            } else if (this.discreteData[col] != null) {
                this.discreteData[col][row] = value.intValue();
            } else {
                throw new IllegalArgumentException("Indices out of bounds or null value.");
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Number get(int row, int col) {
        if (col >= this.continuousData.length || row >= numRows()) {
            return null;
        }

        if (this.continuousData[col] != null) {
            double v = this.continuousData[col][row];
            return Double.isNaN(v) ? null : v;
        } else if (this.discreteData[col] != null) {
            double v = this.discreteData[col][row];
            return v == -99 ? null : v;
        }

        throw new IllegalArgumentException("Indices out of range.");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataBox copy() {
        MixedDataBox box = new MixedDataBox(this.variables, numRows());

        for (int i = 0; i < numRows(); i++) {
            for (int j = 0; j < numCols(); j++) {
                box.set(i, j, get(i, j));
            }
        }

        return box;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataBox like() {
        int[] rows = new int[numRows()];
        int[] cols = new int[numCols()];

        for (int i = 0; i < numRows(); i++) {
            rows[i] = i;
        }
        for (int j = 0; j < numCols(); j++) {
            cols[j] = j;
        }

        return viewSelection(rows, cols);
    }

    /**
     * <p>addVariable.</p>
     *
     * @param variable a {@link edu.cmu.tetrad.graph.Node} object
     */
    public void addVariable(Node variable) {
        this.variables.add(variable);

        this.continuousData = Arrays.copyOf(this.continuousData, this.continuousData.length + 1);
        this.discreteData = Arrays.copyOf(this.discreteData, this.discreteData.length + 1);

        if (variable instanceof ContinuousVariable) {
            this.continuousData[this.continuousData.length - 1] = new double[this.numRows];
        } else if (variable instanceof DiscreteVariable) {
            this.discreteData[this.discreteData.length - 1] = new int[this.numRows];
        } else {
            throw new IllegalStateException();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataBox viewSelection(int[] rows, int[] cols) {
        List<Node> newVars = new ArrayList<>();

        for (int c : cols) {
            newVars.add(this.variables.get(c));
        }

        int row_num = rows.length;
        int col_num = cols.length;

        DataBox _dataBox = new MixedDataBox(newVars, row_num);

        for (int i = 0; i < row_num; i++) {
            for (int j = 0; j < col_num; j++) {
                _dataBox.set(i, j, get(rows[i], cols[j]));
            }
        }

        return _dataBox;
    }

    /**
     * <p>Getter for the field <code>continuousData</code>.</p>
     *
     * @return an array of {@link double} objects
     */
    public double[][] getContinuousData() {
        return this.continuousData;
    }

    /**
     * <p>Getter for the field <code>discreteData</code>.</p>
     *
     * @return an array of {@link int} objects
     */
    public int[][] getDiscreteData() {
        return this.discreteData;
    }

}
