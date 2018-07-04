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
import java.util.Arrays;
import java.util.List;

/**
 * Stores a 2D array of double continuousData. Note that the missing value
 * marker for this box is -99.
 */
public class MixedDataBox implements DataBox {

    private static final long serialVersionUID = 23L;

    private final List<Node> variables;
    private final int numRows;
    private double[][] continuousData;
    private int[][] discreteData;

    /**
     * The variables here are used only to determine which columns are discrete
     * and which are continuous; bounds checking is not done.
     *
     * @param variables
     * @param numRows
     */
    public MixedDataBox(List<Node> variables, int numRows) {
        this.variables = variables;
        this.numRows = numRows;

        this.continuousData = new double[variables.size()][];
        this.discreteData = new int[variables.size()][];

        for (int j = 0; j < variables.size(); j++) {
            if (variables.get(j) instanceof ContinuousVariable) {
                continuousData[j] = new double[numRows];
                Arrays.fill(continuousData[j], Double.NaN);
            } else if (variables.get(j) instanceof DiscreteVariable) {
                discreteData[j] = new int[numRows];
                Arrays.fill(discreteData[j], -99);
            }
        }
    }

    /**
     * This constructor allows other data readers to populate the fields
     * directly.
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
            if (!(continuousData[i] == null ^ discreteData[i] == null)) {
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
     * @return
     */
    public static BoxDataSet serializableInstance() {
        return new BoxDataSet(new ShortDataBox(4, 4), null);
    }

    /**
     * @return the number of rows in this continuousData box.
     */
    @Override
    public int numRows() {
        return numRows;
    }

    /**
     * @return the number of columns in this continuousData box.
     */
    @Override
    public int numCols() {
        return variables.size();
    }

    /**
     * Sets the value at the given row/column to the given Number value. The
     * value used is number.doubleValue().
     *
     * @param row
     * @param col
     * @param value
     */
    @Override
    public void set(int row, int col, Number value) {
        if (value == null) {
            if (continuousData[col] != null) {
                continuousData[col][row] = Double.NaN;
            } else if (discreteData[col] != null) {
                discreteData[col][row] = -99;
            } else {
                throw new IllegalArgumentException("Indices out of bounds or null value.");
            }
        } else {
            if (continuousData[col] != null) {
                continuousData[col][row] = value.doubleValue();
            } else if (discreteData[col] != null) {
                discreteData[col][row] = value.intValue();
            } else {
                throw new IllegalArgumentException("Indices out of bounds or null value.");
            }
        }
    }

    /**
     * @param row
     * @param col
     * @return the Number value at the given row and column. If the value is
     * missing (-99), null, is returned.
     */
    @Override
    public Number get(int row, int col) {
        if (continuousData[col] != null) {
            double v = continuousData[col][row];
            return v == Double.NaN ? null : v;
        } else if (discreteData[col] != null) {
            double v = discreteData[col][row];
            return v == -99 ? null : v;
        }

        throw new IllegalArgumentException("Indices out of range.");
    }

    /**
     * @return a copy of this continuousData box.
     */
    @Override
    public DataBox copy() {
        MixedDataBox box = new MixedDataBox(variables, numRows());

        for (int i = 0; i < numRows(); i++) {
            for (int j = 0; j < numCols(); j++) {
                box.set(i, j, get(i, j));
            }
        }

        return box;
    }

    /**
     * @return a DataBox of type DoubleDataBox, but with the given dimensions.
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

    public void addVariable(Node variable) {
        variables.add(variable);

        continuousData = Arrays.copyOf(continuousData, continuousData.length + 1);
        discreteData = Arrays.copyOf(discreteData, discreteData.length + 1);

        if (variable instanceof ContinuousVariable) {
            continuousData[continuousData.length - 1] = new double[numRows];
        } else if (variable instanceof DiscreteVariable) {
            discreteData[discreteData.length - 1] = new int[numRows];
        } else {
            throw new IllegalStateException();
        }
    }

    @Override
    public DataBox viewSelection(int[] rows, int[] cols) {
        List<Node> newVars = new ArrayList<>();

        for (int c : cols) {
            newVars.add(variables.get(c));
        }

        DataBox _dataBox = new MixedDataBox(newVars, numRows);

        for (int i = 0; i < rows.length; i++) {
            for (int j = 0; j < cols.length; j++) {
                _dataBox.set(i, j, get(rows[i], cols[j]));
            }
        }

        return _dataBox;
    }

}