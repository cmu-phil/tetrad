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
import edu.cmu.tetrad.util.TetradMatrix;
import edu.cmu.tetrad.util.TetradSerializable;

import java.text.NumberFormat;
import java.util.List;
import java.util.Map;

/**
 * Implements a rectangular data set, in the sense of being a dataset with
 * a fixed number of columns and a fixed number of rows, the length of each
 * column being constant.
 *
 * @author Joseph Ramsey
 */
public interface DataSet extends KnowledgeTransferable, DataModel, TetradSerializable {
    static final long serialVersionUID = 23L;

    /**
     * Adds the given variable to the data set.
     *
     * @throws IllegalArgumentException if the variable is neither continuous
     *                                  nor discrete.
     */
    void addVariable(Node variable);

    /**
     * Adds the given variable at the given index.
     */
    void addVariable(int index, Node variable);

    /**
     * Changes the variable for the given column from <code>from</code> to
     * <code>to</code>. Supported currently only for discrete variables.
     *
     * @throws IllegalArgumentException if the given change is not supported.
     */
    void changeVariable(Node from, Node to);

    /**
     * Marks all variables as deselected.
     */
    void clearSelection();

    /**
     * Ensures that the dataset has at least <code>columns</code> columns.
     * Used for pasting data into the dataset. When creating new columns,
     * names in the <code>excludedVarialbeNames</code> list may not be
     * used. The purpose of this is to allow these names to be set later
     * by the calling class, without incurring conflicts.
     */
    void ensureColumns(int columns, List<String> excludedVariableNames);

    /**
     * Ensures that the dataset has at least <code>rows</code> rows.
     * Used for pasting data into the dataset.
     */
    void ensureRows(int rows);

    /**
     * @return the case multiplier for the given case.
     */
    int getMultiplier(int caseNumber);

    /**
     * @return the column index of the given variable.
     */
    int getColumn(Node variable);

    /**
     * If this is a continuous data set, returns the correlation matrix.
     *
     * @throws IllegalStateException if this is not a continuous data set.
     */
    TetradMatrix getCorrelationMatrix();

    /**
     * If this is a continuous data set, returns the covariance matrix.
     *
     * @throws IllegalStateException if this is not a continuous data set.
     */
    TetradMatrix getCovarianceMatrix();

    /**
     * @return the value at the given row and column as a double. For
     * discrete data, returns the integer value cast to a double.
     */
    double getDouble(int row, int column);

    /**
     * @return the underlying data matrix as a TetradMatrix.
     * @throws IllegalStateException if this is not a continuous data set.
     */
    TetradMatrix getDoubleData();

    /**
     * @return the value at the given row and column as an int, rounding if
     * necessary. For discrete variables, this returns the category index
     * of the datum for the variable at that column. Returns
     * DiscreteVariable.MISSING_VALUE for missing values.
     */
    int getInt(int row, int column);

    /**
     * @return the name of the data set.
     */
    String getName();

    /**
     * @return the number of columns in the data set.
     */
    int getNumColumns();

    /**
     * @return the number of rows in the data set.
     */
    int getNumRows();

    /**
     * @param row The index of the case.
     * @param col The index of the variable.
     * @return the value at the given row and column as an Object. The type
     * returned is deliberately vague, allowing for variables of any type.
     * Primitives will be returned as corresponding wrapping objects (for
     * example, doubles as Doubles).
     */
    Object getObject(int row, int col);

    /**
     * @return the currently selected variables.
     */
    int[] getSelectedIndices();

    /**
     * @return the variable at the given column.
     */
    Node getVariable(int column);

    /**
     * @return the variable with the given name.
     */
    Node getVariable(String name);

    /**
     * @return (a copy of) the List of Variables for the data set, in the order
     * of their columns.
     */
    List<String> getVariableNames();

    /**
     * @return (a copy of) the List of Variables for the data set, in the order
     * of their columns.
     */
    List<Node> getVariables();

    /**
     * @return true if case multipliers are being used in this data set.
     */
    boolean isMulipliersCollapsed();

    /**
     * @return true if this is a continuous data set--that is, if it contains at
     * least one column and all of the columns are continuous.
     */
    boolean isContinuous();

    /**
     * @return true if this is a discrete data set--that is, if it contains at
     * least one column and all of the columns are discrete.
     */
    boolean isDiscrete();

    /**
     * @return true if this is a continuous data set--that is, if it contains at
     * least one continuous column and one discrete columnn.
     */
    boolean isMixed();

    /**
     * @return true iff the given column has been marked as selected.
     */
    boolean isSelected(Node variable);

    /**
     * Removes the variable (and data) at the given index.
     */
    void removeColumn(int index);

    /**
     * Removes the given variable, along with all of its data.
     */
    void removeColumn(Node variable);

    /**
     * Removes the given columns from the data set.
     */
    void removeCols(int[] selectedCols);

    /**
     * Removes the given rows from the data set.
     */
    void removeRows(int[] selectedRows);

    /**
     * Sets the case multiplier for the given case to the given number (must be
     * >= 1).
     */
    void setMultiplier(int caseNumber, int multiplier);

    /**
     * Sets the case ID fo the given case numnber to the given value.
     *
     * @throws IllegalArgumentException if the given case ID is already used.
     */
    void setCaseId(int caseNumber, String id);

    /**
     * @return the case ID for the given case number.
     */
    String getCaseId(int caseNumber);

    /**
     * Sets the value at the given (row, column) to the given double value,
     * assuming the variable for the column is continuous.
     *
     * @param row    The index of the case.
     * @param column The index of the variable.
     */
    void setDouble(int row, int column, double value);

    /**
     * Sets the value at the given (row, column) to the given int value,
     * assuming the variable for the column is discrete.
     *
     * @param row The index of the case.
     * @param col The index of the variable.
     */
    void setInt(int row, int col, int value);

    /**
     * Sets the value at the given (row, column) to the given value.
     *
     * @param row The index of the case.
     * @param col The index of the variable.
     */
    void setObject(int row, int col, Object value);

    /**
     * Marks the given column as selected if 'selected' is true or deselected if
     * 'selected' is false.
     */
    void setSelected(Node variable, boolean selected);

    /**
     * Shifts the given column down one.
     */
    void shiftColumnDown(int row, int col, int numRowsShifted);

    /**
     * Creates and returns a dataset consisting of those variables in the list
     * vars.  Vars must be a subset of the variables of this DataSet. The
     * ordering of the elements of vars will be the same as in the list of
     * variables in this DataSet.
     */
    DataSet subsetColumns(List<Node> vars);

    /**
     * @return a new data set in which the the column at indices[i] is placed at
     * index i, for i = 0 to indices.length - 1. (View instead?)
     */
    DataSet subsetColumns(int columns[]);

    /**
     * @return a new data set in which the the row at indices[i] is placed at
     * index i, for i = 0 to indices.length - 1. (View instead?)
     */
    DataSet subsetRows(int rows[]);

    /**
     * @return a string representation of this dataset.
     */
    String toString();

    /**
     * @return true iff this variable is set to accomodate new categories
     * encountered.
     */
    boolean isNewCategoriesAccommodated();

    /**
     * Sets whether this variable should accomodate new categories encountered.
     */
    void setNewCategoriesAccommodated(boolean newCategoriesAccomodated);

    /**
     * The number formatter used to print out continuous values.
     */
    void setNumberFormat(NumberFormat nf);

    /**
     * The number format of the dataset.
     */
    NumberFormat getNumberFormat();

    /**
     * The character used a delimiter when the dataset is output.
     */
    void setOutputDelimiter(Character character);

    /**
     * Randomizes the rows of the data set.
     */
    void permuteRows();

    void setColumnToTooltip(Map<String, String> columnToTooltip);

    Map<String, String> getColumnToTooltip();


    /**
     * Equals
     */
    public boolean equals(Object o);

    DataSet copy();

    DataSet like();
}





