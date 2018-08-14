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
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.TetradMatrix;
import edu.cmu.tetrad.util.TetradSerializable;
import java.io.IOException;
import java.io.ObjectInputStream;
import static java.lang.Math.sqrt;
import java.text.NumberFormat;
import java.util.*;

/**
 * Wraps a DataBox in such a way that mixed data sets can be storeds. The type
 * of each column must be specified by a Variable object, which must be either a
 * <code>ContinuousVariable</code> or a <code>DiscreteVariable</code>. This
 * class violates object orientation in that the underlying data matrix is
 * retrievable using the getDoubleData() method. This is allowed so that
 * external calculations may be performed on large datasets without having to
 * allocate extra memory. If this matrix needs to be modified externally, please
 * consider making a copy of it first, using the TetradMatrix copy() method.
 * <p>
 * The data set may be given a name; this name is not used internally.
 * <p>
 * The data set has a list of variables associated with it, as described above.
 * This list is coordinated with the stored data, in that data for the i'th
 * variable will be in the i'th column.
 * <p>
 * A subset of variables in the data set may be designated as selected. This
 * selection set is stored with the data set and may be manipulated using the
 * <code>select</code> and <code>deselect</code> methods.
 * <p>
 * A multiplicity m_i may be associated with each case c_i in the dataset, which
 * is interpreted to mean that that c_i occurs m_i times in the dataset.
 * <p>
 * Knowledge may be associated with the data set, using the
 * <code>setKnowledge</code> method. This knowledge is not used internally to
 * the data set, but it may be retrieved by algorithms and used.
 * <p>
 *
 * @author Joseph Ramsey
 * @see edu.cmu.tetrad.data.Variable
 * @see edu.cmu.tetrad.data.Knowledge
 */
public final class BoxDataSet implements DataSet, TetradSerializable {

    static final long serialVersionUID = 23L;
    private Map<String, String> columnToTooltip;

    public Map<String, String> getColumnToTooltip() {
        return columnToTooltip;
    }

    public void setColumnToTooltip(Map<String, String> columnToTooltip) {
        this.columnToTooltip = columnToTooltip;
    }

    /**
     * The name of the data model. This is not used internally; it is only here
     * in case an external class wants this dataset to have a name.
     *
     * @serial
     */
    private String name = "";

    /**
     * The list of variables. These correspond columnwise to the columns of
     * <code>data</code>.
     *
     * @serial
     */
    private List<Node> variables = new ArrayList<>();

    /**
     * The container storing the data. Rows are cases; columns are variables.
     * The order of columns is coordinated with the order of variables in
     * getVariables().
     *
     * @serial
     */
    private DataBox dataBox;

    /**
     * The set of selected variables.
     *
     * @serial
     */
    private Set<Node> selection = new HashSet<>();

    /**
     * Case ID's. These are strings associated with some or all of the cases of
     * the dataset.
     *
     * @serial
     */
    private final Map<Integer, String> caseIds = new HashMap<>();

    /**
     * A map from cases to case multipliers. If a case is not in the domain of
     * this map, its case multiplier is by default 1. This is the number of
     * repetitions of the case in the dataset. The sample size is obtained by
     * summing over these multipliers.
     *
     * @serial
     */
    private Map<Integer, Integer> multipliers = new HashMap<>();

    /**
     * The knowledge associated with this data.
     *
     * @serial
     */
    private IKnowledge knowledge = new Knowledge2();

    /**
     * The number formatter used for printing out continuous values.
     */
    private transient NumberFormat nf;

    /**
     * The character used as a delimiter when the dataset is printed.
     */
    private char outputDelimiter = '\t';

    //============================CONSTRUCTORS==========================//
    public BoxDataSet(DataBox dataBox, List<Node> variables) {
        this.dataBox = dataBox;
        this.variables = variables;
    }

    /**
     * Makes of copy of the given data set.
     */
    public BoxDataSet(BoxDataSet dataSet) {
        name = dataSet.name;
        variables = new LinkedList<>(dataSet.variables);
        dataBox = dataSet.dataBox.copy();
        selection = new HashSet<>(dataSet.selection);
        multipliers = new HashMap<>(dataSet.multipliers);
        knowledge = dataSet.knowledge.copy();
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static BoxDataSet serializableInstance() {
        return new BoxDataSet(new ShortDataBox(4, 4), null);
    }

    //============================PUBLIC METHODS========================//
    /**
     * Gets the name of the data set.
     */
    public final String getName() {
        return this.name;
    }

    /**
     * Sets the name of the data set.
     */
    public final void setName(String name) {
        if (name == null) {
            return;
//            throw new NullPointerException("Name must not be null.");
        }
        this.name = name;
    }

    /**
     * @return the number of variables in the data set.
     */
    public final int getNumColumns() {
        return variables.size();
    }

    /**
     * @return the number of rows in the rectangular data set, which is the
     * maximum of the number of rows in the list of wrapped columns.
     */
    public final int getNumRows() {
        return dataBox.numRows();
    }

    /**
     * Sets the value at the given (row, column) to the given int value,
     * assuming the variable for the column is discrete.
     *
     * @param row The index of the case.
     * @param column The index of the variable.
     */
    public final void setInt(int row, int column, int value) {
        Node variable = getVariable(column);

        if (!(variable instanceof DiscreteVariable)) {
            throw new IllegalArgumentException(
                    "Can only set ints for discrete columns.");
        }

        DiscreteVariable _variable = (DiscreteVariable) variable;

        if (value < 0 && value != -99) {
            throw new IllegalArgumentException(
                    "Value must be a positive integer: " + value);
        }

        if (value >= _variable.getNumCategories()) {
            if (_variable.isAccommodateNewCategories()) {
                accomodateIndex(_variable, value);
            } else {
                throw new IllegalArgumentException(
                        "Not a value for that variable: " + value);
            }
        }

        try {
            setIntPrivate(row, column, value);
        } catch (Exception e) {
            if (row < 0 || column < 0) {
                throw new IllegalArgumentException(
                        "Row and column must be >= 0.");
            }

            int newRows = Math.max(row + 1, dataBox.numRows());
            int newCols = Math.max(column + 1, dataBox.numCols());
            resize(newRows, newCols);
            setIntPrivate(row, column, value);
        }
    }

    /**
     * Sets the value at the given (row, column) to the given double value,
     * assuming the variable for the column is continuous.
     *
     * @param row The index of the case.
     * @param column The index of the variable.
     */
    public final void setDouble(int row, int column, double value) {
        try {
            dataBox.set(row, column, value);
        } catch (Exception e) {
            if (row < 0 || column < 0) {
                throw new IllegalArgumentException(
                        "Row and column must be >= 0.");
            }

            int newRows = Math.max(row + 1, dataBox.numRows());
            int newCols = Math.max(column + 1, dataBox.numCols());
            resize(newRows, newCols);
            dataBox.set(row, column, value);
        }
    }

    /**
     * @param row The index of the case.
     * @param col The index of the variable.
     * @return the value at the given row and column as an Object. The type
     * returned is deliberately vague, allowing for variables of any type.
     * Primitives will be returned as corresponding wrapping objects (for
     * example, doubles as Doubles).
     */
    public final Object getObject(int row, int col) {
        Object variable = getVariable(col);

        if (variable instanceof ContinuousVariable) {
            return getDouble(row, col);
        } else if (variable instanceof DiscreteVariable) {
            DiscreteVariable _variable = (DiscreteVariable) variable;

            if (_variable.isCategoryNamesDisplayed()) {
                return _variable.getCategory(getInt(row, col));
            } else {
                return getInt(row, col);
            }

        }

        throw new IllegalArgumentException("Not a row/col in this data set: " + row + ", " + col);
    }

    /**
     * @param row The index of the case.
     * @param col The index of the variable.
     */
    public final void setObject(int row, int col, Object value) {
        Object variable = getVariable(col);

        if (variable instanceof ContinuousVariable) {
            setDouble(row, col, getValueFromObjectContinuous(value));
        } else if (variable instanceof DiscreteVariable) {
            setInt(row, col, getValueFromObjectDiscrete(value,
                    (DiscreteVariable) variable));
        } else {
            throw new IllegalArgumentException(
                    "Expecting either a continuous "
                    + "or a discrete variable.");
        }
    }

    /**
     * @return the indices of the currently selected variables.
     */
    public final int[] getSelectedIndices() {
        List<Node> variables = getVariables();
        Set<Node> selection = getSelection();

        int[] indices = new int[selection.size()];

        int j = -1;
        for (int i = 0; i < variables.size(); i++) {
            if (selection.contains(variables.get(i))) {
                indices[++j] = i;
            }
        }

        return indices;
    }

//    /**
//     * @return the set of currently selected variables.
//     */
//    public final Set<Node> getSelectedVariables() {
//        return new HashSet<>(selection);
//    }
    /**
     * Adds the given variable to the data set, increasing the number of columns
     * by one, moving columns i >= <code>index</code> to column i + 1, and
     * inserting a column of missing values at column i.
     *
     * @throws IllegalArgumentException if the variable already exists in the
     * dataset.
     */
    public final void addVariable(Node variable) {
        if (variables.contains(variable)) {
            throw new IllegalArgumentException("Expecting a new variable: " + variable);
        }

        variables.add(variable);

        if (dataBox instanceof MixedDataBox) {
            ((MixedDataBox) dataBox).addVariable(variable);
        } else {
            resize(dataBox.numRows(), variables.size());
            int col = dataBox.numCols() - 1;

            for (int i = 0; i < dataBox.numRows(); i++) {
                dataBox.set(i, col, null);
            }
        }
    }

    /**
     * Adds the given variable to the dataset, increasing the number of columns
     * by one, moving columns i >= <code>index</code> to column i + 1, and
     * inserting a column of missing values at column i.
     */
    public final void addVariable(int index, Node variable) {
        if (variables.contains(variable)) {
            throw new IllegalArgumentException("Expecting a new variable.");
        }

        if (index < 0 || index > variables.size()) {
            throw new IndexOutOfBoundsException("Index must in (0, #vars).");
        }

        variables.add(index, variable);
        resize(dataBox.numRows(), variables.size());

        Number[][] _data
                = new Number[dataBox.numRows()][dataBox.numCols()];

        for (int j = 0; j < dataBox.numCols() + 1; j++) {
            if (j < index) {
                for (int i = 0; i < dataBox.numRows(); i++) {
                    _data[i][j] = dataBox.get(i, j);
                }
            } else if (j == index) {
                for (int i = 0; i < dataBox.numRows(); i++) {
                    _data[i][j] = null;
                }
            } else {
                for (int i = 0; i < dataBox.numRows(); i++) {
                    _data[i][j] = dataBox.get(i, j - 1);
                }
            }
        }
    }

    /**
     * @return the variable at the given column.
     */
    public final Node getVariable(int col) {
        return variables.get(col);
    }

    /**
     * @return the index of the column of the given variable. You can also get
     * this by calling getVariables().indexOf(variable).
     */
    public final int getColumn(Node variable) {
        return variables.indexOf(variable);
    }

    /**
     * Changes the variable for the given column from <code>from</code> to
     * <code>to</code>. Supported currently only for discrete variables.
     *
     * @throws IllegalArgumentException if the given change is not supported.
     */
    @SuppressWarnings({"ConstantConditions"})
    public final void changeVariable(Node from, Node to) {
        if (!(from instanceof DiscreteVariable
                && to instanceof DiscreteVariable)) {
            throw new IllegalArgumentException(
                    "Only discrete variables supported.");
        }

        DiscreteVariable _from = (DiscreteVariable) from;
        DiscreteVariable _to = (DiscreteVariable) to;

        int col = variables.indexOf(_from);

        List<String> oldCategories = _from.getCategories();
        List<String> newCategories = _to.getCategories();

        int[] indexArray = new int[oldCategories.size()];

        for (int i = 0; i < oldCategories.size(); i++) {
            indexArray[i] = newCategories.indexOf(oldCategories.get(i));
        }

        for (int i = 0; i < getNumRows(); i++) {
            if (dataBox.get(i, col) == null) {
                break;
            }

            int value = getInt(i, col);
            int newIndex = 0;
            try {
                newIndex = indexArray[value];
            } catch (Exception e) {
                e.printStackTrace();
            }

            if (newIndex == -1) {
                dataBox.set(i, col, null);
            } else {
                setInt(i, col, newIndex);
            }
        }

        variables.set(col, _to);
    }

    /**
     * @return the variable with the given name.
     */
    public final Node getVariable(String varName) {
        for (Node variable1 : variables) {
            if (variable1.getName().equals(varName)) {
                return variable1;
            }
        }

        return null;
    }

    /**
     * @return (a copy of) the List of Variables for the data set, in the order
     * of their columns.
     */
    public final List<Node> getVariables() {
        return new LinkedList<>(variables);
    }

    /**
     * @return a copy of the knowledge associated with this data set. (Cannot be
     * null.)
     */
    public final IKnowledge getKnowledge() {
        return this.knowledge.copy();
    }

    /**
     * Sets knowledge to be associated with this data set. May not be null.
     */
    public final void setKnowledge(IKnowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException();
        }

        this.knowledge = knowledge.copy();
    }

    /**
     * @return (a copy of) the List of Variables for the data set, in the order
     * of their columns.
     */
    public final List<String> getVariableNames() {
        List<Node> vars = getVariables();
        List<String> names = new ArrayList<>();

        for (Node variable : vars) {
            String name = variable.getName();
            names.add(name);
        }

        return names;
    }

    /**
     * Marks the given column as selected if 'selected' is true or deselected if
     * 'selected' is false.
     */
    public final void setSelected(Node variable, boolean selected) {
        if (selected) {
            if (variables.contains(variable)) {
                getSelection().add(variable);
            }
        } else if (variables.contains(variable)) {
            getSelection().remove(variable);
        }
    }

    /**
     * Marks all variables as deselected.
     */
    public final void clearSelection() {
        getSelection().clear();
    }

    /**
     * Ensures that the dataset has at least the number of rows, adding rows if
     * necessary to make that the case. The new rows will be filled with missing
     * values.
     */
    public void ensureRows(int rows) {
        if (rows > getNumRows()) {
            resize(rows, getNumColumns());
        }
    }

    /**
     * Ensures that the dataset has at least the given number of columns, adding
     * continuous variables with unique names until that is true. The new
     * columns will be filled with missing values.
     */
    public void ensureColumns(int columns, List<String> excludedVariableNames) {
        for (int col = getNumColumns(); col < columns; col++) {
            int i = 0;
            String _name;

            while (true) {
                _name = "X" + (++i);
                if (getVariable(_name) == null
                        && !excludedVariableNames.contains(_name)) {
                    break;
                }
            }

            ContinuousVariable variable = new ContinuousVariable(_name);
            addVariable(variable);
        }
    }

    /**
     * @return true iff the given column has been marked as selected.
     */
    public final boolean isSelected(Node variable) {
        return getSelection().contains(variable);
    }

    /**
     * Removes the column for the variable at the given index, reducing the
     * number of columns by one.
     */
    public final void removeColumn(int index) {
        if (index < 0 || index >= variables.size()) {
            throw new IllegalArgumentException("Not a column in this data set: " + index);
        }

        variables.remove(index);

        int[] rows = new int[dataBox.numRows()];

        for (int i = 0; i < dataBox.numRows(); i++) {
            rows[i] = i;
        }

        int[] cols = new int[dataBox.numCols() - 1];

        int m = -1;

        for (int i = 0; i < dataBox.numCols(); i++) {
            if (i != index) {
                cols[++m] = i;
            }
        }

        dataBox = viewSelection(rows, cols);
    }

    private DataBox viewSelection(int[] rows, int[] cols) {
        return dataBox.viewSelection(rows, cols);

    }

    /**
     * Removes the columns for the given variable from the dataset, reducing the
     * number of columns by one.
     *
     * @param variable
     */
    @Override
    public final void removeColumn(Node variable) {
        int index = variables.indexOf(variable);

        if (index != -1) {
            removeColumn(index);
        }
    }

    /**
     * Creates and returns a dataset consisting of those variables in the list
     * vars. Vars must be a subset of the variables of this DataSet. The
     * ordering of the elements of vars will be the same as in the list of
     * variables in this DataSet.
     */
    public final DataSet subsetColumns(List<Node> vars) {
//        if (vars.isEmpty()) {
//            throw new IllegalArgumentException("Subset must not be empty.");
//        }

        if (!(getVariables().containsAll(vars))) {
            List<Node> missingVars = new ArrayList<>(vars);
            missingVars.removeAll(getVariables());

            throw new IllegalArgumentException(
                    "All vars must be original vars: " + missingVars);
        }

        int[] rows = new int[dataBox.numRows()];

        for (int i = 0; i < rows.length; i++) {
            rows[i] = i;
        }

        int[] cols = new int[vars.size()];

        for (int j = 0; j < cols.length; j++) {
            cols[j] = getVariables().indexOf(vars.get(j));
        }

        DataBox _dataBox = viewSelection(rows, cols);

        BoxDataSet _dataSet = new BoxDataSet(_dataBox, vars);

//        _dataSet.name = name + "_copy";
        _dataSet.variables = vars;
        _dataSet.selection = new HashSet<>();
        _dataSet.multipliers = new HashMap<>(multipliers);

        // Might have to delete some knowledge.
        _dataSet.knowledge = knowledge.copy();

        return _dataSet;
    }

//    /**
//     * @return true if case multipliers are being used for this data set.
//     */
//    public final boolean isMulipliersCollapsed() {
//        return !getMultipliers().keySet().isEmpty();
//    }
//    /**
//     * @return the case multiplise for the given case (i.e. row) in the data
//     * set. Is this is n > 1, the interpretation is that the data set
//     * effectively contains n copies of that case.
//     */
//    public final int getMultiplier(int caseNumber) {
//        Integer multiplierInt = getMultipliers().get(caseNumber);
//        return multiplierInt == null ? 1 : multiplierInt;
//    }
    /**
     * Sets the case ID fo the given case numnber to the given value.
     *
     * @throws IllegalArgumentException if the given case ID is already used.
     */
    public final void setCaseId(int caseNumber, String id) {
        if (id == null) {
            caseIds.remove(caseNumber);
        } else if (caseIds.values().contains(id)) {
            throw new IllegalArgumentException("Case ID's must be unique; that one "
                    + "has already been used: " + id);
        } else {
            caseIds.put(caseNumber, id);
        }
    }

    /**
     * @return the case ID for the given case number.
     */
    public final String getCaseId(int caseNumber) {
        return caseIds.get(caseNumber);
    }

    /**
     * @return true iff this is a continuous data set--that is, if every column
     * in it is continuous. (By implication, empty datasets are both discrete
     * and continuous.)
     */
    public final boolean isContinuous() {
        for (int i = 0; i < getNumColumns(); i++) {
            Node variable = variables.get(i);

            if (!(variable instanceof ContinuousVariable)) {
                return false;
            }
        }

        return true;
    }

    /**
     * @return true iff this is a discrete data set--that is, if every column in
     * it is discrete. (By implication, empty datasets are both discrete and
     * continuous.)
     */
    public final boolean isDiscrete() {
        for (int i = 0; i < getNumColumns(); i++) {
            Node column = variables.get(i);

            if (!(column instanceof DiscreteVariable)) {
                return false;
            }
        }

        return true;
    }

    /**
     * @return true if this is a mixed data set--that is, if it contains at
     * least one continuous column and one discrete columnn.
     */
    public final boolean isMixed() {
        int numContinuous = 0;
        int numDiscrete = 0;

        for (int i = 0; i < getNumColumns(); i++) {
            Node column = variables.get(i);

            if (column instanceof ContinuousVariable) {
                numContinuous++;
            } else if (column instanceof DiscreteVariable) {
                numDiscrete++;
            } else {
                throw new IllegalArgumentException(
                        "Column not of type continuous"
                        + "or of type discrete; can't classify this data set.");
            }
        }

        return numContinuous > 0 && numDiscrete > 0;
    }

    /**
     * @return the correlation matrix for this dataset. Defers to
     * <code>Statistic.covariance()</code> in the COLT matrix library, so it
     * inherits the handling of missing values from that library--that is, any
     * off-diagonal correlation involving a column with a missing value is
     * Double.NaN, although all of the on-diagonal elements are 1.0. If that's
     * not the desired behavior, missing values can be removed or imputed first.
     */
    public final TetradMatrix getCorrelationMatrix() {
        if (!isContinuous()) {
            throw new IllegalStateException("Not a continuous data set.");
        }

        TetradMatrix corr = getCovarianceMatrix();

        for (int i = 0; i < corr.columns(); i++) {
            for (int j = 0; j < corr.columns(); j++) {
                if (i == j) {
                    continue;
                }
                corr.set(i, j, corr.get(i, j) / sqrt(corr.get(i, i) * corr.get(j, j)));
            }
        }

        for (int i = 0; i < corr.columns(); i++) {
            corr.set(i, i, 1.0);
        }

//        TetradMatrix cov = new TetradMatrix(dataBox.numCols(), dataBox.numCols());
//
//        double[] x = new double[dataBox.numRows()];
//        double[] y = new double[dataBox.numRows()];
//
//        for (int i = 0; i < dataBox.numCols(); i++) {
//            for (int j = 0; j < dataBox.numCols(); j++) {
//                for (int k = 0; k < dataBox.numRows(); k++) {
//                    x[k] = dataBox.get(k, i).doubleValue();
//                    y[k] = dataBox.get(k, j).doubleValue();
//
//                    cov.set(i, j, StatUtils.correlation(x, y));
//                }
//            }
//        }
        return corr;
    }

    /**
     * @return the covariance matrix for this dataset. Defers to
     * <code>Statistic.covariance()</code> in the COLT matrix library, so it
     * inherits the handling of missing values from that library--that is, any
     * covariance involving a column with a missing value is Double.NaN. If
     * that's not the desired behavior, missing values can be removed or imputed
     * first.
     */
    public final TetradMatrix getCovarianceMatrix() {
        if (!isContinuous()) {
            throw new IllegalStateException("Not a continuous data set.");
        }

        return new CovarianceMatrix(this).getMatrix();

//        TetradMatrix cov = new TetradMatrix(dataBox.numCols(), dataBox.numCols());
//
//        double[] x = new double[dataBox.numRows()];
//        double[] y = new double[dataBox.numRows()];
//
//        for (int i = 0; i < dataBox.numCols(); i++) {
//            for (int j = 0; j < dataBox.numCols(); j++) {
//                for (int k = 0; k < dataBox.numRows(); k++) {
//                    x[k] = dataBox.get(k, i).doubleValue();
//                    y[k] = dataBox.get(k, j).doubleValue();
//
//                    cov.set(i, j, StatUtils.covariance(x, y));
//                }
//            }
//        }
//
//        return cov;
    }

    /**
     * @return the value at the given row and column, rounded to the nearest
     * integer, or DiscreteVariable.MISSING_VALUE if the value is missing.
     */
    public final int getInt(int row, int column) {
        Number value = dataBox.get(row, column);

        if (value == null) {
            return DiscreteVariable.MISSING_VALUE;
        } else {
            return value.intValue();
        }
    }

    /**
     * @return the double value at the given row and column. For discrete
     * variables, this returns an int cast to a double. The double value at the
     * given row and column may be missing, in which case Double.NaN is
     * returned.
     */
    public final double getDouble(int row, int column) {
        Number value = dataBox.get(row, column);

        if (value == null) {
            return ContinuousVariable.getDoubleMissingValue();
        } else {
            return value.doubleValue();
        }
    }

//    /**
//     * Sets the case multiplier for the given case to the given number (must be
//     * >= 1).
//     */
//    public final void setMultiplier(int caseNumber, int multiplier) {
//        if (caseNumber < 0) {
//            throw new IllegalArgumentException(
//                    "Case numbers must be >= 0: " + caseNumber);
//        }
//
//        if (multiplier < 0) {
//            throw new IllegalArgumentException(
//                    "Multipliers must be >= 0: " + multiplier);
//        }
//
//        if (multiplier == 1) {
//            getMultipliers().remove(caseNumber);
//        } else {
//            getMultipliers().put(caseNumber, multiplier);
//        }
//    }
    /**
     * @return a string, suitable for printing, of the dataset. Lines are
     * separated by '\n', tokens in the line by whatever character is set in the      <code>setOutputDelimiter()<code> method. The list of variables is printed
     * first, followed by one line for each case.
     * <p>
     * This method should probably not be used for saving to files. If that's
     * your goal, use the DataSavers class instead.
     * @see #setOutputDelimiter(Character)
     * @see DataWriter
     */
    public final String toString() {
        StringBuilder buf = new StringBuilder();
        List<Node> variables = getVariables();

//        buf.append("\n");
//
        for (int i = 0; i < getNumColumns(); i++) {
            buf.append(variables.get(i));

            if (i < getNumColumns() - 1) {
                buf.append(outputDelimiter);
            }
        }

        buf.append("\n");

        for (int i = 0; i < getNumRows(); i++) {
            for (int j = 0; j < getNumColumns(); j++) {
                Node variable = getVariable(j);

                if (variable instanceof ContinuousVariable) {
                    if (Double.isNaN(getDouble(i, j))) {
                        buf.append("*");
                    } else {
                        buf.append(getNumberFormat().format(getDouble(i, j)));
                    }

                    if (j < getNumColumns() - 1) {
                        buf.append(outputDelimiter);
                    }
                } else if (variable instanceof DiscreteVariable) {
                    DiscreteVariable _variable = (DiscreteVariable) variable;
                    int value = getInt(i, j);

                    if (value == -99) {
                        buf.append("*");
                    } else {
                        String category = _variable.getCategory(value);

                        if (category.indexOf((int) outputDelimiter) == -1) {
                            buf.append(category);
                        } else {
                            buf.append("\"").append(category).append("\"");
                        }
                    }

                    if (j < getNumColumns() - 1) {
                        buf.append(outputDelimiter);
                    }
                } else {
                    throw new IllegalStateException(
                            "Expecting either a continuous "
                            + "variable or a discrete variable: variable = " + variable
                            + " type = " + variable.getClass());
                }
            }

            if (i < getNumRows() - 1) {
                buf.append("\n");
            }
        }

        if (knowledge != null && !knowledge.isEmpty()) {
            buf.append(knowledge);
        }

        return buf.toString();
    }

    /**
     * @return a copy of the underlying COLT TetradMatrix matrix, containing all
     * of the data in this dataset, discrete data included. Discrete data will
     * be represented by ints cast to doubles. Rows in this matrix are cases,
     * and columns are variables. The list of variable, in the order in which
     * they occur in the matrix, is given by getVariables().
     * <p>
     * If isMultipliersCollapsed() returns false, multipliers in the dataset are
     * first expanded before returning the matrix, so the number of rows in the
     * returned matrix may not be the same as the number of rows in this
     * dataset.
     * @throws IllegalStateException if this is not a continuous data set.
     * @see #getVariables
     */
    public final TetradMatrix getDoubleData() {
        TetradMatrix copy = new TetradMatrix(dataBox.numRows(), dataBox.numCols());

        for (int i = 0; i < dataBox.numRows(); i++) {
            for (int j = 0; j < dataBox.numCols(); j++) {
                Number number = dataBox.get(i, j);
                if (number == null) {
                    copy.set(i, j, Double.NaN);
                } else {
                    copy.set(i, j, number.doubleValue());
                }
            }
        }

        return copy;
    }

    /**
     * @return a new data set in which the the column at indices[i] is placed at
     * index i, for i = 0 to indices.length - 1. (Moved over from Purify.)
     */
    public final DataSet subsetColumns(int indices[]) {
        List<Node> variables = getVariables();
        List<Node> _variables = new LinkedList<>();

        for (int index : indices) {
            _variables.add(variables.get(index));
        }

        int[] rows = new int[dataBox.numRows()];

        for (int i = 0; i < rows.length; i++) {
            rows[i] = i;
        }

        DataBox _data = viewSelection(rows, indices);
        BoxDataSet _dataSet = new BoxDataSet(_data, _variables);

//        _dataSet.name = name + "_copy";
        _dataSet.name = name;
        _dataSet.variables = _variables;
        _dataSet.selection = new HashSet<>();
        _dataSet.multipliers = new HashMap<>(multipliers);

        // Might have to delete some knowledge.
        _dataSet.knowledge = knowledge.copy();
        return _dataSet;
    }

    public final DataSet subsetRows(int rows[]) {
        int cols[] = new int[this.dataBox.numCols()];

        for (int i = 0; i < cols.length; i++) {
            cols[i] = i;
        }

        DataBox newBox = this.dataBox.viewSelection(rows, cols);

        BoxDataSet _data = new BoxDataSet(this);
        _data.dataBox = newBox;

        return _data;
    }

    /**
     * Shifts the given column
     */
    public final void shiftColumnDown(int row, int col, int numRowsShifted) {

        if (row >= getNumRows() || col >= getNumColumns()) {
            throw new IllegalArgumentException("Out of range:  row = " + row + " col = " + col);
        }

        int lastRow = -1;

        for (int i = getNumRows() - 1; i >= row; i--) {
            if (dataBox.get(i, col) != null) {
                lastRow = i;
                break;
            }
        }

        if (lastRow == -1) {
            return;
        }

        resize(getNumRows() + numRowsShifted, getNumColumns());

        for (int i = getNumRows() - 1; i >= row + numRowsShifted; i--) {
            dataBox.set(i, col, dataBox.get(i - numRowsShifted, col));
            dataBox.set(i - numRowsShifted, col, null);
        }
    }

    /**
     * Removes the given columns from the data set.
     *
     * @param cols
     */
    @Override
    public final void removeCols(int[] cols) {
        int[] rows = new int[dataBox.numRows()];

        for (int i = 0; i < dataBox.numRows(); i++) {
            rows[i] = i;
        }

        int[] retainedCols = new int[variables.size() - cols.length];
        int i = -1;

        for (int j = 0; j < variables.size(); j++) {
            if (Arrays.binarySearch(cols, j) < 0) {
                retainedCols[++i] = j;
            }
        }

        List<Node> retainedVars = new LinkedList<>();

        for (int retainedCol : retainedCols) {
            retainedVars.add(variables.get(retainedCol));
        }

        dataBox = viewSelection(rows, retainedCols);
        variables = retainedVars;
        selection = new HashSet<>();
        multipliers = new HashMap<>(multipliers);
        knowledge = knowledge.copy(); // Might have to delete some knowledge.
    }

    /**
     * Removes the given rows from the data set.
     */
    public final void removeRows(int[] selectedRows) {

        int[] cols = new int[dataBox.numCols()];

        for (int i = 0; i < dataBox.numCols(); i++) {
            cols[i] = i;
        }

        int[] retainedRows = new int[dataBox.numRows() - selectedRows.length];
        int i = -1;

        for (int j = 0; j < dataBox.numRows(); j++) {
            if (Arrays.binarySearch(selectedRows, j) < 0) {
                retainedRows[++i] = j;
            }
        }

        dataBox = viewSelection(retainedRows, cols);
        selection = new HashSet<>();
        multipliers = new HashMap<>(multipliers);
        knowledge = knowledge.copy(); // Might have to delete some knowledge.
    }

    /**
     * @return true iff <code>obj</code> is a continuous RectangularDataSet with
     * corresponding variables of the same name and corresponding data values
     * equal, when rendered using the number format at
     * <code>NumberFormatUtil.getInstance().getNumberFormat()</code>.
     */
    public final boolean equals(Object obj) {
        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

        if (obj == this) {
            return true;
        }

        if (!(obj instanceof DataSet)) {
            return false;
        }

        DataSet _dataSet = (DataSet) obj;

        for (int i = 0; i < getVariables().size(); i++) {
            Node node = getVariables().get(i);
            Node _node = _dataSet.getVariables().get(i);
            if (!node.equals(_node)) {
                return false;
            }
        }

        if (!(_dataSet.getNumRows() == getNumRows())) {
            return false;
        }

        for (int i = 0; i < getNumRows(); i++) {
            for (int j = 0; j < getNumColumns(); j++) {
                Node variable = getVariable(j);

                if (variable instanceof ContinuousVariable) {
                    double value = Double.parseDouble(nf.format(getDouble(i, j)));
                    double _value = Double.parseDouble(nf.format(_dataSet.getDouble(i, j)));

                    if (Math.abs(value - _value) > 0.0) {
                        return false;
                    }
                } else {
                    double value = getInt(i, j);
                    double _value = _dataSet.getInt(i, j);

                    if (!(value == _value)) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    @Override
    public DataSet copy() {
        return new BoxDataSet(this);
    }

    @Override
    public DataSet like() {
        return new BoxDataSet(dataBox.like(), variables);
    }

    public void setNumberFormat(NumberFormat nf) {
        if (nf == null) {
            throw new NullPointerException();
        }

        this.nf = nf;
    }

    /**
     * Sets the character ('\t', ' ', ',', for instance) that is used to delimit
     * tokens when the data set is printed out using the toString() method.
     *
     * @see #toString
     */
    public void setOutputDelimiter(Character character) {
        this.outputDelimiter = character;
    }

    /**
     * Randomly permutes the rows of the dataset.
     */
    public void permuteRows() {
        List<Integer> permutation = new ArrayList<>();

        for (int i = 0; i < getNumRows(); i++) {
            permutation.add(i);
        }

        Collections.shuffle(permutation);

        DataBox data2 = dataBox.like();

        for (int i = 0; i < getNumRows(); i++) {
            for (int j = 0; j < getNumColumns(); j++) {
                data2.set(i, j, dataBox.get(permutation.get(i), j));
            }
        }

        this.dataBox = data2;
    }

    //===============================PRIVATE METHODS=====================//
    private void setIntPrivate(int row, int col, int value) {
        if (value == -99) {
            dataBox.set(row, col, null);
        } else {
            dataBox.set(row, col, value);
        }
    }

    /**
     * Resizes the data to the given dimensions. Data that does not fall within
     * the new dimensions is lost, and positions in the redimensioned data that
     * have no correlates in the old data are set to missing (that is,
     * Double.NaN).
     *
     * @param rows The number of rows in the redimensioned data.
     * @param cols The number of columns in the redimensioned data.
     */
    private void resize(int rows, int cols) {
        int[] _rows = new int[rows];
        int[] _cols = new int[cols];

        for (int i = 0; i < _rows.length; i++) _rows[i] = i;
        for (int j = 0; j < _cols.length; j++) _cols[j] = j;

        DataBox _data = dataBox.viewSelection(_rows, _cols);

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                if (i < dataBox.numRows() && j < dataBox.numCols()) {
                    _data.set(i, j, dataBox.get(i, j));
                } else {
                    _data.set(i, j, null);
                }
            }
        }

        dataBox = _data;
    }

    /**
     * @return the set of case multipliers..
     */
    private Map<Integer, Integer> getMultipliers() {
        return multipliers;
    }

    /**
     * Adds semantic checks to the default deserialization method. This method
     * must have the standard signature for a readObject method, and the body of
     * the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from
     * version to version. A readObject method of this form may be added to any
     * class, even if Tetrad sessions were previously saved out using a version
     * of the class that didn't include it. (That's what the
     * "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for help.
     *
     * @throws java.io.IOException
     * @throws ClassNotFoundException
     */
    private static void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();
    }

    /**
     * @return the set of selected nodes, creating a new set if necessary.
     */
    private Set<Node> getSelection() {
        if (selection == null) {
            selection = new HashSet<>();
        }
        return selection;
    }

    /**
     * Attempts to translate <code>element</code> into a double value, returning
     * it if successful, otherwise throwing an exception. To be successful, the
     * object must be either a Number or a String.
     *
     * @throws IllegalArgumentException if the translation cannot be made. The
     * reason is in the message.
     */
    private static double getValueFromObjectContinuous(Object element) {
        if ("*".equals(element) || "".equals(element)) {
            return ContinuousVariable.getDoubleMissingValue();
        } else if (element instanceof Number) {
            return ((Number) element).doubleValue();
        } else if (element instanceof String) {
            try {
                return Double.parseDouble((String) element);
            } catch (NumberFormatException e) {
                return ContinuousVariable.getDoubleMissingValue();
            }
        } else {
            throw new IllegalArgumentException(
                    "The argument 'element' must be "
                    + "either a Number or a String.");
        }
    }

    /**
     * Attempts to translate <code>element</code> into an int value, returning
     * it if successful, otherwise throwing an exception. To be successful, the
     * object must be either a Number or a String.
     *
     * @throws IllegalArgumentException if the translation cannot be made. The
     * reason is in the message.
     */
    private int getValueFromObjectDiscrete(Object element,
            DiscreteVariable variable) {
        if ("*".equals(element) || "".equals(element)) {
            return DiscreteVariable.MISSING_VALUE;
        }

        if (variable.isAccommodateNewCategories()) {
            if (element instanceof Number) {
                int index = ((Number) element).intValue();

                if (!variable.checkValue(index)) {
                    if (index >= variable.getNumCategories()) {
                        accomodateIndex(variable, index);
                    } else {
                        throw new IllegalArgumentException("Variable " + variable
                                + " is not accepting "
                                + "new categories. Problem category is " + ".");
                    }
                }

                return index;
            } else if (element instanceof String) {
                String label = (String) element;

                if ("".equals(label)) {
                    throw new IllegalArgumentException(
                            "Blank category names not permitted.");
                }

                variable = accomodateCategory(variable, label);
                int index = variable.getIndex(label);

                if (index == -1) {
                    throw new IllegalArgumentException(
                            "Not a category for this variable: " + index);
                }

                return index;
            } else {
                throw new IllegalArgumentException(
                        "The argument 'element' must be "
                        + "either a Number or a String.");
            }
        } else if (element instanceof Number) {
            int index = ((Number) element).intValue();

            if (!variable.checkValue(index)) {
                return DiscreteVariable.MISSING_VALUE;
            }

            return index;
        } else if (element instanceof String) {
            String label = (String) element;

            int index = variable.getIndex(label);

            if (index == -1) {
                return DiscreteVariable.MISSING_VALUE;
            }

            return index;
        } else {
            throw new IllegalArgumentException(
                    "The argument 'element' must be "
                    + "either a Number or a String.");
        }
    }

    /**
     * If the given category is not already a category for a cagetory, augments
     * the range of category by one and sets the category of the new value to
     * the given category.
     */
    private DiscreteVariable accomodateCategory(DiscreteVariable variable,
            String category) {
        if (category == null) {
            throw new NullPointerException();
        }

        List<String> categories = variable.getCategories();

        if (!categories.contains(category)) {
            List<String> newCategories = new LinkedList<>(categories);
            newCategories.add(category);
            DiscreteVariable newVariable
                    = new DiscreteVariable(variable.getName(), newCategories);
            changeVariable(variable, newVariable);
            return newVariable;
        }

        return variable;
    }

    /**
     * Increases the number of categories if necessary to make sure that this
     * variable has the given index.
     */
    private void accomodateIndex(DiscreteVariable variable, int index) {
        if (!variable.isAccommodateNewCategories()) {
            throw new IllegalArgumentException("This variable is not set "
                    + "to accomodate new categories.");
        }

        if (index >= variable.getNumCategories()) {
            adjustCategories(variable, index + 1);
        }
    }

    /**
     * Adjusts the size of the categories list to match the getModel number of
     * categories. If the list is too short, it is padded with default
     * categories. If it is too long, the extra categories are removed.
     */
    private void adjustCategories(DiscreteVariable variable,
            int numCategories) {
        List<String> categories
                = new LinkedList<>(variable.getCategories());
        List<String> newCategories = new LinkedList<>(categories);

        if (categories.size() > numCategories) {
            for (int i = variable.getCategories().size() - 1;
                    i >= numCategories; i++) {
                newCategories.remove(i);
            }
        } else if (categories.size() < numCategories) {
            for (int i = categories.size(); i < numCategories; i++) {
                String category = DataUtils.defaultCategory(i);

                if (categories.contains(category)) {
                    continue;
                }

                newCategories.add(category);
            }
        }

        DiscreteVariable to
                = new DiscreteVariable(variable.getName(), newCategories);
        changeVariable(variable, to);
    }

    /**
     * @return the number format, which by default is the one at
     * <code>NumberFormatUtil.getInstance().getNumberFormat()</code>, but can be
     * set by the user if desired.
     * @see #setNumberFormat(java.text.NumberFormat)
     */
    public NumberFormat getNumberFormat() {
        if (nf == null) {
            nf = NumberFormatUtil.getInstance().getNumberFormat();
        }

        return nf;
    }

    public DataBox getDataBox() {
        return dataBox;
    }
}
