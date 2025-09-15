/// ////////////////////////////////////////////////////////////////////////////
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
import edu.cmu.tetrad.util.Matrix;

import java.text.NumberFormat;
import java.util.List;
import java.util.Map;

/**
 * Implements a rectangular data set, in the sense of being a dataset with a fixed number of columns and a fixed number
 * of rows, the length of each column being constant.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public interface DataSet extends DataModel {
    /**
     * Constant <code>serialVersionUID=23L</code>
     */
    long serialVersionUID = 23L;

    /**
     * Adds the given variable to the data set.
     *
     * @param variable The variable to add.
     * @throws java.lang.IllegalArgumentException if the variable is neither continuous nor discrete.
     */
    void addVariable(Node variable);

    /**
     * Adds the given variable at the given index.
     *
     * @param index    The index at which to add the variable.
     * @param variable The variable to add.
     */
    void addVariable(int index, Node variable);

    /**
     * Changes the variable for the given column from <code>from</code> to
     * <code>to</code>. Supported currently only for discrete variables.
     *
     * @param from The variable to change.
     * @param to   The variable to change to.
     * @throws java.lang.IllegalArgumentException if the given change is not supported.
     */
    void changeVariable(Node from, Node to);

    /**
     * Marks all variables as deselected.
     */
    void clearSelection();

    /**
     * Ensures that the dataset has at least <code>columns</code> columns. Used for pasting data into the dataset. When
     * creating new columns, names in the <code>excludedVariableNames</code> list may not be used. The purpose of this
     * is to allow these names to be set later by the calling class, without incurring conflicts.
     *
     * @param columns               The number of columns to ensure.
     * @param excludedVariableNames The names of variables that should not be used for new columns.
     */
    void ensureColumns(int columns, List<String> excludedVariableNames);

    /**
     * <p>existsMissingValue.</p>
     *
     * @return true if and only if this data set contains at least one missing value.
     */
    boolean existsMissingValue();

    /**
     * Ensures that the dataset has at least <code>rows</code> rows. Used for pasting data into the dataset.
     *
     * @param rows The number of rows to ensure.
     */
    void ensureRows(int rows);

    /**
     * <p>getColumn.</p>
     *
     * @param variable The variable to check.
     * @return the column index of the given variable.
     */
    int getColumn(Node variable);

    /**
     * If this is a continuous data set, returns the correlation matrix.
     *
     * @return the correlation matrix.
     * @throws java.lang.IllegalStateException if this is not a continuous data set.
     */
    Matrix getCorrelationMatrix();

    /**
     * If this is a continuous data set, returns the covariance matrix.
     *
     * @return the covariance matrix.
     * @throws java.lang.IllegalStateException if this is not a continuous data set.
     */
    Matrix getCovarianceMatrix();

    /**
     * <p>getDouble.</p>
     *
     * @param row    The index of the case.
     * @param column The index of the variable.
     * @return the value at the given row and column as a double. For discrete data, returns the integer value cast to a
     * double.
     */
    double getDouble(int row, int column);

    /**
     * <p>getDoubleData.</p>
     *
     * @return the underlying data matrix as a TetradMatrix.
     * @throws java.lang.IllegalStateException if this is not a continuous data set.
     */
    Matrix getDoubleData();

    /**
     * <p>getInt.</p>
     *
     * @param row    The index of the case.
     * @param column The index of the variable.
     * @return the value at the given row and column as an int, rounding if necessary. For discrete variables, this
     * returns the category index of the datum for the variable at that column. Returns DiscreteVariable.MISSING_VALUE
     * for missing values.
     */
    int getInt(int row, int column);

    /**
     * <p>getName.</p>
     *
     * @return the name of the data set.
     */
    String getName();

    /**
     * <p>getNumColumns.</p>
     *
     * @return the number of columns in the data set.
     */
    int getNumColumns();

    /**
     * <p>getNumRows.</p>
     *
     * @return the number of rows in the data set.
     */
    int getNumRows();

    /**
     * <p>getObject.</p>
     *
     * @param row The index of the case.
     * @param col The index of the variable.
     * @return the value at the given row and column as an Object. The type returned is deliberately vague, allowing for
     * variables of any type. Primitives will be returned as corresponding wrapping objects (for example, doubles as
     * Doubles).
     */
    Object getObject(int row, int col);

    /**
     * <p>getSelectedIndices.</p>
     *
     * @return the currently selected variables.
     */
    int[] getSelectedIndices();

    /**
     * <p>getVariable.</p>
     *
     * @param column The index of the variable.
     * @return the variable at the given column.
     */
    Node getVariable(int column);

    /**
     * {@inheritDoc}
     */
    Node getVariable(String name);

    /**
     * <p>getVariableNames.</p>
     *
     * @return (a copy of) the List of Variables for the data set, in the order of their columns.
     */
    List<String> getVariableNames();

    /**
     * <p>getVariables.</p>
     *
     * @return (a copy of) the List of Variables for the data set, in the order of their columns.
     */
    List<Node> getVariables();

    /**
     * <p>isContinuous.</p>
     *
     * @return true if this is a continuous data set--that is, if it contains at least one column and all the columns
     * are continuous.
     */
    boolean isContinuous();

    /**
     * <p>isDiscrete.</p>
     *
     * @return true if this is a discrete data set--that is, if it contains at least one column and all the columns are
     * discrete.
     */
    boolean isDiscrete();

    /**
     * <p>isMixed.</p>
     *
     * @return true if this is a continuous data set--that is, if it contains at least one continuous column and one
     * discrete column.
     */
    boolean isMixed();

    /**
     * <p>isSelected.</p>
     *
     * @param variable The variable to check.
     * @return true iff the given column has been marked as selected.
     */
    boolean isSelected(Node variable);

    /**
     * Removes the variable (and data) at the given index.
     *
     * @param index The index of the variable to remove.
     */
    void removeColumn(int index);

    /**
     * Removes the given variable, along with all of its data.
     *
     * @param variable The variable to remove.
     */
    void removeColumn(Node variable);

    /**
     * Removes the given columns from the data set.
     *
     * @param selectedCols The indices of the columns to remove.
     */
    void removeCols(int[] selectedCols);

    /**
     * Removes the given rows from the data set.
     *
     * @param selectedRows The indices of the rows to remove.
     */
    void removeRows(int[] selectedRows);

    /**
     * Sets the value at the given (row, column) to the given double value, assuming the variable for the column is
     * continuous.
     *
     * @param row    The index of the case.
     * @param column The index of the variable.
     * @param value  The value to set.
     */
    void setDouble(int row, int column, double value);

    /**
     * Sets the value at the given (row, column) to the given int value, assuming the variable for the column is
     * discrete.
     *
     * @param row   The index of the case.
     * @param col   The index of the variable.
     * @param value The value to set.
     */
    void setInt(int row, int col, int value);

    /**
     * Sets the value at the given (row, column) to the given value.
     *
     * @param row   The index of the case.
     * @param col   The index of the variable.
     * @param value The value to set.
     */
    void setObject(int row, int col, Object value);

    /**
     * Marks the given column as selected if 'selected' is true or deselected if 'selected' is false.
     *
     * @param variable The variable to select or deselect.
     * @param selected True to select the variable, false to deselect it.
     */
    void setSelected(Node variable, boolean selected);

    /**
     * Generates a subset of the current DataSet by selecting specified rows and columns.
     *
     * @param rows an array of row indices to include in the subset
     * @param columns an array of column indices to include in the subset
     * @return a new DataSet object containing only the specified rows and columns
     */
    DataSet subsetRowsColumns(int[] rows, int[] columns);

    /**
     * Creates and returns a dataset consisting of those variables in the list vars.  Vars must be a subset of the
     * variables of this DataSet. The ordering of the elements of vars will be the same as in the list of variables in
     * this DataSet.
     *
     * @param vars The variables to include in the new data set.
     * @return a new data set consisting of the variables in the list vars.
     */
    DataSet subsetColumns(List<Node> vars);

    /**
     * <p>subsetColumns.</p>
     *
     * @param columns The indices of the columns to include in the new data set.
     * @return a new data set in which the column at indices[i] is placed at index i, for i = 0 to indices.length - 1.
     * (View instead?)
     */
    DataSet subsetColumns(int[] columns);

    /**
     * <p>subsetRows.</p>
     *
     * @param rows The indices of the rows to include in the new data set.
     * @return a new data set in which the row at indices[i] is placed at index i, for i = 0 to indices.length - 1.
     * (View instead?)
     */
    DataSet subsetRows(int[] rows);

    DataSet subsetRows(List<Integer> rows);

    /**
     * <p>toString.</p>
     *
     * @return a string representation of this dataset.
     */
    String toString();

    /**
     * The number format of the dataset.
     *
     * @return The number format of the dataset.
     */
    NumberFormat getNumberFormat();

    /**
     * The number formatter used to print out continuous values.
     *
     * @param nf The number formatter used to print out continuous values.
     */
    void setNumberFormat(NumberFormat nf);

    /**
     * The character used a delimiter when the dataset is output
     *
     * @param character The character used as a delimiter when the dataset is output
     */
    void setOutputDelimiter(Character character);

    /**
     * Randomizes the rows of the data set.
     */
    void permuteRows();

    /**
     * Returns the map of column names to tooltips.
     *
     * @return The map of column names to tooltips.
     */
    Map<String, String> getColumnToTooltip();

    /**
     * Checks if the given object is equal to this dataset.
     *
     * @param o The object to check.
     * @return True if the given object is equal to this dataset.
     */
    boolean equals(Object o);

    /**
     * Returns a copy of this dataset.
     *
     * @return A copy of this dataset.
     */
    DataSet copy();

    /**
     * Returns a dataset with the same dimensions as this dataset, but with no data.
     *
     * @return a dataset with the same dimensions as this dataset, but with no data.
     */
    DataSet like();
}





