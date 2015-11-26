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

import cern.colt.matrix.DoubleMatrix2D;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.StatUtils;
import edu.cmu.tetrad.util.TetradMatrix;
import edu.cmu.tetrad.util.TetradSerializable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.text.NumberFormat;
import java.util.*;

/**
 * Wraps a COLT 2D matrix in such a way that mixed tetradMatrix sets can be stored. The
 * type of each column must be specified by a Variable object, which must be
 * either a <code>ContinuousVariable</code> or a <code>DiscreteVariable</code>.
 * This class violates object orientation in that the underlying tetradMatrix matrix is
 * retrievable using the getDoubleData() method. This is allowed so that
 * external calculations may be performed on large datasets without having to
 * allocate extra memory. If this matrix needs to be modified externally, please
 * consider making a copy of it first, using the TetradMatrix copy() method.
 * <p/>                                                            n
 * The tetradMatrix set may be given a name; this name is not used internally.
 * <p/>
 * The tetradMatrix set has a list of variables associated with it, as described above.
 * This list is coordinated with the stored tetradMatrix, in that tetradMatrix for the i'th
 * variable will be in the i'th column.
 * <p/>
 * A subset of variables in the tetradMatrix set may be designated as selected. This
 * selection set is stored with the tetradMatrix set and may be manipulated using the
 * <code>select</code> and <code>deselect</code> methods.
 * <p/>
 * A multiplicity m_i may be associated with each case c_i in the dataset, which
 * is interpreted to mean that that c_i occurs m_i times in the dataset.
 * <p/>
 * Knowledge may be associated with the tetradMatrix set, using the
 * <code>setKnowledge</code> method. This knowledge is not used internally to
 * the tetradMatrix set, but it may be retrieved by algorithms and used.
 * <p/>
 * This tetradMatrix set replaces an earlier Minitab-style DataSet class. The reasons
 * for replacement are as follows.
 * <p/>
 * <ul> <li>COLT marices are optimized for double 2D matrix calculations in ways
 * that Java-style double[][] matrices are not. <li>The COLT library comes with
 * a wide range of linear algebra library methods that are better tested and
 * more flexible than that linear algebra methods used previously in Tetrad.
 * <li>Views of COLT matrices can often be used in places where copies of tetradMatrix
 * sets were being created. <li>The only place where tetradMatrix sets were being
 * manipulated for honest reasons was in the interface; everywhere else, it
 * turns out to have been sensible to calculate a list of variables and a sample
 * size in advance and allocate memory for a tetradMatrix set with these dimensions. For
 * very large tetradMatrix sets, it makes more sense to disallow memory-hogging
 * manipulations than to throw out-of-memory errors. </ul>
 *
 * @author Joseph Ramsey
 * @see Variable
 * @see Knowledge
 */
public final class ColtDataSet implements DataSet, TetradSerializable {
    static final long serialVersionUID = 23L;
    private Map<String, String> columnToTooltip;

    public Map<String, String> getColumnToTooltip() {
        return columnToTooltip;
    }

    public void setColumnToTooltip(Map<String, String> columnToTooltip) {
        this.columnToTooltip = columnToTooltip;
    }

    /**
     * The name of the tetradMatrix model. This is not used internally; it is only here
     * in case an external class wants this dataset to have a name.
     *
     * @serial
     */
    private String name = "";

    /**
     * The list of variables. These correspond columnwise to the columns of
     * <code>tetradMatrix</code>.
     *
     * @serial
     */
    private List<Node> variables = new ArrayList<Node>();

    /**
     * @deprecated
     */
    private DoubleMatrix2D data;

    /**
     * The container storing the data. Rows are cases; columns are variables.
     * The order of columns is coordinated with the order of variables in
     * getVariables().
     *
     * @serial
     */
    private TetradMatrix tetradMatrix;

    /**
     * The set of selected variables.
     *
     * @serial
     */
    private Set<Node> selection = new HashSet<Node>();

    /**
     * Case ID's. These are strings associated with some or all of the cases of
     * the dataset.
     *
     * @serial
     */
    private Map<Integer, String> caseIds = new HashMap<Integer, String>();

    /**
     * A map from cases to case multipliers. If a case is not in the domain of
     * this map, its case multiplier is by default 1. This is the number of
     * repetitions of the case in the dataset. The sample size is obtained by
     * summing over these multipliers.
     *
     * @serial
     */
    private Map<Integer, Integer> multipliers = new HashMap<Integer, Integer>();

    /**
     * The knowledge associated with this tetradMatrix.
     *
     * @serial
     */
    private IKnowledge knowledge = new Knowledge2();

    /**
     * True iff the column should adjust the discrete variable to accomodate new
     * categories added, false if new categories should be rejected.
     *
     * @serial
     * @deprecated Replaced by corresponding field on DiscreteVariable.
     */
    private boolean newCategoriesAccommodated = true;

    /**
     * The number formatter used for printing out continuous values.
     */
    private NumberFormat nf;

    /**
     * The character used as a delimiter when the dataset is printed.
     */
    private char outputDelimiter = '\t';

    /**
     * True iff line numbers should be written by calls to toString().
     */
    private boolean lineNumbersWritten;

    //============================CONSTRUCTORS==========================//

    /**
     * Constructs a tetradMatrix set with the given number of rows (cases) and the given
     * list of variables. The number of columns will be equal to the number of
     * cases.
     */
    public ColtDataSet(int rows, List<Node> variables) {
        tetradMatrix = new TetradMatrix(rows, variables.size());
        this.variables = new LinkedList<Node>(variables);

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < variables.size(); j++) {
                tetradMatrix.set(i, j, Double.NaN);
            }
        }

        this.multipliers = new HashMap<Integer, Integer>();
        for (int i = 0; i < rows; i++) {
            multipliers.put(i, 1);
        }
    }

    /**
     * The data should have one row per case.
     */
    public ColtDataSet(int rows, List<Node> variables, double[][] data) {
        tetradMatrix = new TetradMatrix(rows, variables.size());
        this.variables = new LinkedList<Node>(variables);

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < variables.size(); j++) {
                tetradMatrix.set(i, j, Double.NaN);
            }
        }

        this.multipliers = new HashMap<Integer, Integer>();
        for (int i = 0; i < rows; i++) {
            multipliers.put(i, 1);
        }

        if (data[0].length != variables.size() || data.length != rows) {
            throw new IllegalArgumentException("Wrong shape for data.");
        }

        this.tetradMatrix = new TetradMatrix(data);
    }

    /**
     * Makes of copy of the given tetradMatrix set. TODO Might consider making the
     * argument a RectangularDataSet instead.
     */
    public ColtDataSet(ColtDataSet dataSet) {
        name = dataSet.name;
        variables = new LinkedList<Node>(dataSet.variables);
        tetradMatrix = dataSet.tetradMatrix.copy();
        selection = new HashSet<Node>(dataSet.selection);
        multipliers = new HashMap<Integer, Integer>(dataSet.multipliers);
        knowledge = dataSet.knowledge.copy();
        newCategoriesAccommodated = dataSet.newCategoriesAccommodated;
        this.nf = dataSet.nf;
    }


    /**
     * Creates a continuous tetradMatrix set from the given tetradMatrix. The matrix must be in
     * column-variable form.
     *
     * @param variables a list of ContinuousVariable's.
     * @param data      A 2D tetradMatrix set containing the tetradMatrix. The number of columns
     *                  must equal the number of variables.
     */
    public static DataSet makeContinuousData(List<Node> variables, TetradMatrix data) {
        return makeContinuousData(variables, data.toArray());
    }


    public static DataSet makeContinuousData(List<Node> variables, double[][] data) {

        if (variables.size() != data[0].length) {
            throw new IllegalArgumentException("# vars = " + variables.size() + " but # columns = " + data[0].length);
        }

        List<Node> convertedVars = new ArrayList<Node>();

        for (Node node : variables) {
            if (!(node instanceof ContinuousVariable)) {
                ContinuousVariable continuousVariable = new ContinuousVariable(node.getName());
                continuousVariable.setNodeType(node.getNodeType());
                convertedVars.add(continuousVariable);
//                throw new IllegalArgumentException("Expecting all continuous variables: " + variables);
            } else {
                convertedVars.add(node);
            }
        }

        List<Node> nodes = new ArrayList<Node>();

        for (Node variable : convertedVars) {
            nodes.add(variable);
        }

        for (Node node : convertedVars) {
            if (!(node instanceof ContinuousVariable)) {
                throw new IllegalArgumentException();
            }
        }

        ColtDataSet dataSet = new ColtDataSet(data.length, nodes, data);
//
//        BoxDataSet dataSet = new BoxDataSet(new DoubleDataBox(data), nodes);

        return dataSet;
    }

    public static ColtDataSet makeData(List<Node> variables,
                                       TetradMatrix data) {
        if (variables.size() != data.columns()) {
            throw new IllegalArgumentException();
        }

        List<Node> convertedVars = new ArrayList<Node>(variables);

//        for (Node node : variables) {
//            if (!(node instanceof ContinuousVariable)) {
//                convertedVars.add(new ContinuousVariable(node.getName()));
//                throw new IllegalArgumentException("Expecting all continuous variables: " + variables);
//            } else {
//                convertedVars.add(node);
//            }
//        }

        List<Node> nodes = new ArrayList<Node>();

        for (Node variable : convertedVars) {
            nodes.add(variable);
        }

/*        for (Node node : convertedVars) {
            if (!(node instanceof ContinuousVariable)) {
                throw new IllegalArgumentException();
            }
        }
*/
        ColtDataSet dataSet = new ColtDataSet(data.rows(), nodes);
        dataSet.tetradMatrix = data.copy();

        return dataSet;
    }

    public DataSet concatenateDataRowwise(ColtDataSet dataSet1, ColtDataSet dataSet2) {
        if (!(dataSet1.variables.equals(dataSet2.variables))) {
            throw new IllegalArgumentException();
        }

        int rows1 = dataSet1.getNumRows();
        int rows2 = dataSet2.getNumRows();
        int cols = dataSet1.getNumColumns();

        ColtDataSet concat = new ColtDataSet(rows1 + rows2, dataSet1.variables);

        TetradMatrix concatMatrix = concat.tetradMatrix;
        TetradMatrix matrix1 = dataSet1.tetradMatrix;
        TetradMatrix matrix2 = dataSet2.tetradMatrix;

        for (int i = 0; i < cols; i++) {
            for (int j = 0; j < rows1; j++) {
                concatMatrix.set(j, i, matrix1.get(j, i));
            }
            for (int j = 0; j < rows2; j++) {
                concatMatrix.set(j + rows1, i, matrix2.get(j, i));
            }
        }

        return concat;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static DataSet serializableInstance() {
        List<Node> variables = new ArrayList<Node>();
        variables.add(new ContinuousVariable("X1"));
        ColtDataSet data = new ColtDataSet(2, variables);
        data.setDouble(0, 0, 1.0);
        data.setDouble(1, 0, 1.0);
        return data;
    }

    //============================PUBLIC METHODS========================//

    /**
     * Gets the name of the tetradMatrix set.
     */
    public final String getName() {
        return this.name;
    }

    /**
     * Sets the name of the tetradMatrix set.
     */
    public final void setName(String name) {
        if (name == null) {
            return;
//            throw new NullPointerException("Name must not be null.");
        }
        this.name = name;
    }

    /**
     * @return the number of variables in the tetradMatrix set.
     */
    public final int getNumColumns() {
        return variables.size();
    }

    /**
     * @return the number of rows in the rectangular tetradMatrix set, which is the
     * maximum of the number of rows in the list of wrapped columns.
     */
    public final int getNumRows() {
        return tetradMatrix.rows();
    }

    /**
     * Sets the value at the given (row, column) to the given int value,
     * assuming the variable for the column is discrete.
     *
     * @param row    The index of the case.
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

            int newRows = Math.max(row + 1, tetradMatrix.rows());
            int newCols = Math.max(column + 1, tetradMatrix.columns());
            newCols = Math.max(newCols, variables.size());
            resize(newRows, newCols);
            setIntPrivate(row, column, value);
        }
    }

    /**
     * Sets the value at the given (row, column) to the given double value,
     * assuming the variable for the column is continuous.
     *
     * @param row    The index of the case.
     * @param column The index of the variable.
     */
    public final void setDouble(int row, int column, double value) {
        if ((getVariable(column) instanceof DiscreteVariable)) {
//            if (!(getVariable(column) instanceof ContinuousVariable)) {
            throw new IllegalArgumentException(
                    "Can only set doubles for continuous columns: " + getVariable(column));
        }

        try {
            tetradMatrix.set(row, column, value);
        } catch (Exception e) {
            if (row < 0 || column < 0) {
                throw new IllegalArgumentException(
                        "Row and column must be >= 0.");
            }

            int newRows = Math.max(row + 1, tetradMatrix.rows());
            int newCols = Math.max(column + 1, tetradMatrix.columns());
            resize(newRows, newCols);
            tetradMatrix.set(row, column, value);
        }
    }

    /**
     * @return the value at the given row and column as an Object. The type
     * returned is deliberately vague, allowing for variables of any type.
     * Primitives will be returned as corresponding wrapping objects (for
     * example, doubles as Doubles).
     *
     * @param row The index of the case.
     * @param col The index of the variable.
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

        throw new IllegalArgumentException("Not a row/col in this tetradMatrix set.");
    }

    /**
     * @return the value at the given row and column as an Object. The type
     * returned is deliberately vague, allowing for variables of any type.
     * Primitives will be returned as corresponding wrapping objects (for
     * example, doubles as Doubles).
     *
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
                    "Expecting either a continuous " +
                            "or a discrete variable.");
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

    /**
     * @return the set of currently selected variables.
     */
    public final Set<Node> getSelectedVariables() {
        return new HashSet<Node>(selection);
    }

    /**
     * Adds the given variable to the tetradMatrix set, increasing the number of
     * columns by one, moving columns i >= <code>index</code> to column i + 1,
     * and inserting a column of missing values at column i.
     *
     * @throws IllegalArgumentException if the variable already exists in the
     *                                  dataset.
     */
    public final void addVariable(Node variable) {
        if (variables.contains(variable)) {
            throw new IllegalArgumentException("Expecting a new variable: " + variable);
        }

        variables.add(variable);

        resize(tetradMatrix.rows(), variables.size());
        int col = tetradMatrix.columns() - 1;

        for (int i = 0; i < tetradMatrix.rows(); i++) {
            tetradMatrix.set(i, col, Double.NaN);
        }
    }

    /**
     * Adds the given variable to the dataset, increasing the number of
     * columns by one, moving columns i >= <code>index</code> to column i + 1,
     * and inserting a column of missing values at column i.
     */
    public final void addVariable(int index, Node variable) {
        if (variables.contains(variable)) {
            throw new IllegalArgumentException("Expecting a new variable.");
        }

        if (index < 0 || index > variables.size()) {
            throw new IndexOutOfBoundsException("Index must in (0, #vars).");
        }

        variables.add(index, variable);
        resize(tetradMatrix.rows(), variables.size());

        TetradMatrix _data =
                new TetradMatrix(tetradMatrix.rows(), tetradMatrix.columns() + 1);

        for (int j = 0; j < tetradMatrix.columns() + 1; j++) {
            if (j < index) {
                for (int i = 0; i < tetradMatrix.rows(); i++) {
                    _data.set(i, j, tetradMatrix.get(i, j));
                }
            } else if (j == index) {
                for (int i = 0; i < tetradMatrix.rows(); i++) {
                    _data.set(i, j, Double.NaN);
                }
            } else {
                for (int i = 0; i < tetradMatrix.rows(); i++) {
                    _data.set(i, j, tetradMatrix.get(i, j - 1));
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
        if (!(from instanceof DiscreteVariable &&
                to instanceof DiscreteVariable)) {
            throw new IllegalArgumentException(
                    "Only discrete variables supported.");
        }

        DiscreteVariable _from = (DiscreteVariable) from;
        DiscreteVariable _to = (DiscreteVariable) to;

        int col = variables.indexOf(_from);

        List<String> oldCategories = _from.getCategories();
        List newCategories = _to.getCategories();

        int[] indexArray = new int[oldCategories.size()];

        for (int i = 0; i < oldCategories.size(); i++) {
            indexArray[i] = newCategories.indexOf(oldCategories.get(i));
        }

        for (int i = 0; i < getNumRows(); i++) {
            if (Double.isNaN(tetradMatrix.get(i, col))) {
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
                tetradMatrix.set(i, col, Double.NaN);
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
     * @return (a copy of) the List of Variables for the tetradMatrix set, in the order
     * of their columns.
     */
    public final List<Node> getVariables() {
        return new LinkedList<Node>(variables);
    }


    /**
     * @return a copy of the knowledge associated with this tetradMatrix set. (Cannot be
     * null.)
     */
    public final IKnowledge getKnowledge() {
        return this.knowledge.copy();
    }

    /**
     * Sets knowledge to be associated with this tetradMatrix set. May not be null.
     */
    public final void setKnowledge(IKnowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException();
        }

        this.knowledge = knowledge.copy();
    }

    /**
     * @return (a copy of) the List of Variables for the tetradMatrix set, in the order
     * of their columns.
     */
    public final List<String> getVariableNames() {
        List<Node> vars = getVariables();
        List<String> names = new ArrayList<String>();

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
        } else {
            if (variables.contains(variable)) {
                getSelection().remove(variable);
            }
        }
    }

    /**
     * Marks all variables as deselected.
     */
    public final void clearSelection() {
        getSelection().clear();
    }

    /**
     * Ensures that the dataset has at least the number of rows, adding rows
     * if necessary to make that the case. The new rows will be filled with
     * missing values.
     */
    public void ensureRows(int rows) {
        if (rows > getNumRows()) {
            resize(rows, getNumColumns());
        }
    }

    /**
     * Ensures that the dataset has at least the given number of columns,
     * adding continuous variables with unique names until that is true.
     * The new columns will be filled with missing values.
     */
    public void ensureColumns(int columns, List<String> excludedVariableNames) {
        for (int col = getNumColumns(); col < columns; col++) {
            int i = 0;
            String _name;

            while (true) {
                _name = "X" + (++i);
                if (getVariable(_name) == null &&
                        !excludedVariableNames.contains(_name)) break;
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
            throw new IllegalArgumentException(
                    "Not a column in this tetradMatrix set: " + index);
        }

        variables.remove(index);

        int[] rows = new int[tetradMatrix.rows()];

        for (int i = 0; i < tetradMatrix.rows(); i++) {
            rows[i] = i;
        }

        int[] cols = new int[tetradMatrix.columns() - 1];

        int m = -1;

        for (int i = 0; i < tetradMatrix.columns(); i++) {
            if (i != index) {
                cols[++m] = i;
            }
        }

        tetradMatrix = tetradMatrix.getSelection(rows, cols).copy();
    }

    /**
     * Removes the columns for the given variable from the dataset, reducing
     * the number of columns by one.
     */
    public final void removeColumn(Node variable) {
        int index = variables.indexOf(variable);

        if (index == -1) {
            throw new IllegalArgumentException("Not a variables in this data set: " + variable);
        }

        removeColumn(index);
    }

    /**
     * Creates and returns a dataset consisting of those variables in the list
     * vars.  Vars must be a subset of the variables of this DataSet. The
     * ordering of the elements of vars will be the same as in the list of
     * variables in this DataSet.
     */
    public final DataSet subsetColumns(List<Node> vars) {
//        if (vars.isEmpty()) {
//            throw new IllegalArgumentException("Subset must not be empty.");
//        }

        vars = GraphUtils.replaceNodes(vars, getVariables());

        if (!(getVariables().containsAll(vars))) {
            List<Node> missingVars = new ArrayList<Node>(vars);
            missingVars.removeAll(getVariables());

            throw new IllegalArgumentException(
                    "All vars must be original vars: " + missingVars);
        }

        int[] rows = new int[tetradMatrix.rows()];

        for (int i = 0; i < rows.length; i++) {
            rows[i] = i;
        }

        int[] columns = new int[vars.size()];

        for (int j = 0; j < columns.length; j++) {
            columns[j] = getVariables().indexOf(vars.get(j));
        }

        TetradMatrix _data = tetradMatrix.getSelection(rows, columns).copy();

        ColtDataSet _dataSet = new ColtDataSet(0, new LinkedList<Node>());
        _dataSet.tetradMatrix = _data;

//        _dataSet.name = name + "_copy";
        _dataSet.variables = vars;
        _dataSet.selection = new HashSet<Node>();
        _dataSet.multipliers = new HashMap<Integer, Integer>(multipliers);

        // Might have to delete some knowledge.
        _dataSet.knowledge = knowledge.copy();

        return _dataSet;
    }

    /**
     * @return true if case multipliers are being used for this tetradMatrix set.
     */
    public final boolean isMulipliersCollapsed() {
        for (int i : getMultipliers().keySet()) {
            if (getMultipliers().get(i) != 1) {
                return false;
            }
        }

        return true;

//
//        return !getMultipliers().keySet().isEmpty();
    }

    /**
     * @return the case multiplise for the given case (i.e. row) in the tetradMatrix
     * set. Is this is n > 1, the interpretation is that the tetradMatrix set
     * effectively contains n copies of that case.
     */
    public final int getMultiplier(int caseNumber) {
        Integer multiplierInt = getMultipliers().get(caseNumber);
        return multiplierInt == null ? 1 : multiplierInt;
    }

    /**
     * Sets the case ID fo the given case numnber to the given value.
     *
     * @throws IllegalArgumentException if the given case ID is already used.
     */
    public final void setCaseId(int caseNumber, String id) {
        if (id == null) {
            caseIds.remove(caseNumber);
//        } else if (caseIds.values().contains(id)) {
//            throw new IllegalArgumentException("Case ID's must be unique; that one " +
//                    "has already been used: " + id);
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
     * @return true iff this is a continuous tetradMatrix set--that is, if every column
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
     * @return true iff this is a discrete tetradMatrix set--that is, if every column in
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
     * @return true if this is a mixed tetradMatrix set--that is, if it contains at
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
                        "Column not of type continuous" +
                                "or of type discrete; can't classify this tetradMatrix set.");
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
     * not the desired behavior, missing values can be removed or imputed
     * first.
     */
    public final TetradMatrix getCorrelationMatrix() {
        if (!isContinuous()) {
            throw new IllegalStateException("Not a continuous tetradMatrix set.");
        }

        TetradMatrix cov = new TetradMatrix(tetradMatrix.columns(), tetradMatrix.columns());

        for (int i = 0; i < tetradMatrix.columns(); i++) {
            for (int j = i; j < tetradMatrix.columns(); j++) {
                double correlation = StatUtils.correlation(tetradMatrix.getColumn(i).toArray(), tetradMatrix.getColumn(j).toArray());
                cov.set(i, j, correlation);
                cov.set(j, i, correlation);
            }
        }

        return cov;



//        return Statistic.correlation(Statistic.covariance(tetradMatrix));
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

        return DataUtils.cov(tetradMatrix);
//        return DataUtils.covErich(tetradMatrix);
    }

    /**
     * @return the value at the given row and column, rounded to the nearest
     * integer, or DiscreteVariable.MISSING_VALUE if the value is missing.
     */
    public final int getInt(int row, int column) {
        double value = tetradMatrix.get(row, column);

        if (Double.isNaN(value)) {
            return DiscreteVariable.MISSING_VALUE;
        } else {
            return (int) Math.round(value);
        }
    }

    /**
     * @return the double value at the given row and column. For discrete
     * variables, this returns an int cast to a double. The double value at the
     * given row and column may be missing, in which case Double.NaN is
     * returned.
     */
    public final double getDouble(int row, int column) {
        return tetradMatrix.get(row, column);
    }

    /**
     * Sets the case multiplier for the given case to the given number (must be
     * >= 1).
     */
    public final void setMultiplier(int caseNumber, int multiplier) {
        if (caseNumber < 0) {
            throw new IllegalArgumentException(
                    "Case numbers must be >= 0: " + caseNumber);
        }

        if (multiplier < 0) {
            throw new IllegalArgumentException(
                    "Multipliers must be >= 0: " + multiplier);
        }

        if (multiplier == 1) {
            getMultipliers().remove(caseNumber);
        } else {
            getMultipliers().put(caseNumber, multiplier);
        }
    }

    /**
     * @return a string, suitable for printing, of the dataset. Lines are
     * separated by '\n', tokens in the line by whatever character is set in the
     * <code>setOutputDelimiter()<code> method. The list of variables is printed
     * first, followed by one line for each case.
     * <p/>
     * This method should probably not be used for saving to files. If that's
     * your goal, use the DataSavers class instead.
     *
     * @see #setOutputDelimiter(Character)
     * @see DataWriter
     */
    public final String toString() {
        StringBuilder buf = new StringBuilder();
        List<Node> variables = getVariables();

        buf.append("\n");

        if (isLineNumbersWritten()) {
            buf.append("\t");
        }

        for (int i = 0; i < getNumColumns(); i++) {
            buf.append(variables.get(i));

            if (i < getNumColumns() - 1) {
                buf.append(outputDelimiter);
            }
        }

        buf.append("\n");

        for (int i = 0; i < getNumRows(); i++) {

            if (isLineNumbersWritten()) {
                buf.append((i + 1) + ".\t");
            }

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
                            buf.append("\"" + category + "\"");
                        }
                    }

                    if (j < getNumColumns() - 1) {
                        buf.append(outputDelimiter);
                    }
                } else {
                    throw new IllegalStateException(
                            "Expecting either a continuous " +
                                    "variable or a discrete variable: " + variable);
                }
            }

            buf.append("\n");
        }

        buf.append("\n");

        if (knowledge != null && !knowledge.isEmpty()) {
            buf.append(knowledge);
        }

        return buf.toString();
    }

    /**
     * @return a copy of the underlying COLT TetradMatrix matrix, containing
     * all of the tetradMatrix in this dataset, discrete tetradMatrix included. Discrete tetradMatrix
     * will be represented by ints cast to doubles. Rows in this matrix are
     * cases, and columns are variables. The list of variable, in the order in
     * which they occur in the matrix, is given by getVariables().
     * <p/>
     * If isMultipliersCollapsed() returns false, multipliers in the dataset are
     * first expanded before returning the matrix, so the number of rows in the
     * returned matrix may not be the same as the number of rows in this
     * dataset.
     *
     * @throws IllegalStateException if this is not a continuous tetradMatrix set.
     * @see #getVariables
     * @see #isMulipliersCollapsed()
     */
    public final TetradMatrix getDoubleData() {
//        return tetradMatrix.copy();

        if (!isMulipliersCollapsed()) {
            CaseExpander expander = new CaseExpander();
            return ((ColtDataSet) expander.filter(this)).tetradMatrix;
        } else {
            return tetradMatrix;
        }
    }

    public final TetradMatrix getDoubleDataNoCopy() {
        return tetradMatrix;
    }

    /**
     * @return a new tetradMatrix set in which the the column at indices[i] is placed at
     * index i, for i = 0 to indices.length - 1. (Moved over from Purify.)
     */
    public final DataSet subsetColumns(int indices[]) {
        List<Node> variables = getVariables();
        List<Node> _variables = new LinkedList<Node>();

        for (int index : indices) {
            if (index != -1) {
                _variables.add(variables.get(index));
            }
        }

        int[] rows = new int[tetradMatrix.rows()];

        for (int i = 0; i < rows.length; i++) {
            rows[i] = i;
        }

        TetradMatrix _data = tetradMatrix.getSelection(rows, indices).copy();

        ColtDataSet _dataSet = new ColtDataSet(0, new LinkedList<Node>());
        _dataSet.tetradMatrix = _data;

//        _dataSet.name = name + "_copy";
        _dataSet.name = name;
        _dataSet.variables = _variables;
        _dataSet.selection = new HashSet<Node>();
        _dataSet.multipliers = new HashMap<Integer, Integer>(multipliers);

        // Might have to delete some knowledge.
        _dataSet.knowledge = knowledge.copy();
        return _dataSet;
    }

    public final DataSet subsetRows(int rows[]) {
        int cols[] = new int[this.tetradMatrix.columns()];

        for (int i = 0; i < cols.length; i++) {
            cols[i] = i;
        }

        ColtDataSet _data = new ColtDataSet(this);
        _data.tetradMatrix = this.tetradMatrix.getSelection(rows, cols);
        return _data;
    }

    /**
     * Shifts the given column
     */
    public final void shiftColumnDown(int row, int col, int numRowsShifted) {

        // Find last row that does not consist entirely of missing values.
        if (row >= getNumRows() || col >= getNumColumns()) {
            throw new IllegalArgumentException("Out of range:  row = " + row + " col = " + col);
        }

        int lastRow = -1;

        for (int i = getNumRows() - 1; i >= row; i--) {
            if (!Double.isNaN(tetradMatrix.get(i, col))) {
                lastRow = i;
                break;
            }
        }

        if (lastRow == -1) {
            return;
        }

        resize(getNumRows() + numRowsShifted, getNumColumns());

        for (int i = getNumRows() - 1; i >= row + numRowsShifted; i--) {
            tetradMatrix.set(i, col, tetradMatrix.get(i - numRowsShifted, col));
            tetradMatrix.set(i - numRowsShifted, col, Double.NaN);
        }
    }

    /**
     * Removes the given columns from the tetradMatrix set.
     */
    public final void removeCols(int[] cols) {

        // TODO Check sanity of values in cols.
        int[] rows = new int[tetradMatrix.rows()];

        for (int i = 0; i < tetradMatrix.rows(); i++) {
            rows[i] = i;
        }

        int[] retainedCols = new int[variables.size() - cols.length];
        int i = -1;

        for (int j = 0; j < variables.size(); j++) {
            if (Arrays.binarySearch(cols, j) < 0) {
                retainedCols[++i] = j;
            }
        }

        List<Node> retainedVars = new LinkedList<Node>();

        for (int retainedCol : retainedCols) {
            retainedVars.add(variables.get(retainedCol));
        }

        tetradMatrix = tetradMatrix.getSelection(rows, retainedCols).copy();
        variables = retainedVars;
        selection = new HashSet<Node>();
        multipliers = new HashMap<Integer, Integer>(multipliers);
        knowledge = knowledge.copy(); // Might have to delete some knowledge.
    }

    /**
     * Removes the given rows from the tetradMatrix set.
     */
    public final void removeRows(int[] selectedRows) {

        // TODO Check sanity of values in cols.
        int[] cols = new int[tetradMatrix.columns()];

        for (int i = 0; i < tetradMatrix.columns(); i++) {
            cols[i] = i;
        }

        int[] retainedRows = new int[tetradMatrix.rows() - selectedRows.length];
        int i = -1;

        for (int j = 0; j < tetradMatrix.rows(); j++) {
            if (Arrays.binarySearch(selectedRows, j) < 0) {
                retainedRows[++i] = j;
            }
        }

        tetradMatrix = tetradMatrix.getSelection(retainedRows, cols).copy();
        selection = new HashSet<Node>();
        multipliers = new HashMap<Integer, Integer>(multipliers);
        knowledge = knowledge.copy(); // Might have to delete some knowledge.
    }

    /**
     * @return true iff <code>obj</code> is a continuous RectangularDataSet with
     * corresponding variables of the same name and corresponding tetradMatrix values
     * equal, when rendered using the number format at <code>NumberFormatUtil.getInstance().getNumberFormat()</code>.
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

        List<Node> dataVars = _dataSet.getVariables();

        if (getVariables().size() != dataVars.size()) {
            return false;
        }

        dataVars = GraphUtils.replaceNodes(dataVars, getVariables());

        for (int i = 0; i < getVariables().size(); i++) {
            Node node = getVariables().get(i);
            Node _node = dataVars.get(i);
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
                    final double d1 = getDouble(i, j);
                    final double d2 = _dataSet.getDouble(i, j);

                    if (Double.isNaN(d1) && !Double.isNaN(d2)) {
                        return false;
                    }

                    if (!Double.isNaN(d1) && Double.isNaN(d2)) {
                        return false;
                    }

                    if (Double.isNaN(d1) && Double.isNaN(d2)) {
                        return true;
                    }

                    if (Math.abs(d1 - d2) > Math.pow(10, -(nf.getMaximumFractionDigits()))) {
                        return false;
                    }

//                    Parsing and rendering is expensive.
//                    double value = Double.parseDouble(nf.format(getDouble(i, j)));
//                    double _value = Double.parseDouble(nf.format(_dataSet.getDouble(i, j)));
//
//                    if (Math.abs(value - _value) > 0.0) {
//                        return false;
//                    }
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
        return new ColtDataSet(this);
    }

    @Override
    public DataSet like() {
        return new ColtDataSet(getNumRows(), variables);
    }

    /**
     * @return true iff this variable is set to accomodate new categories
     * encountered.
     *
     * @deprecated This is set in DiscreteVariable now.
     */
    public boolean isNewCategoriesAccommodated() {
        return this.newCategoriesAccommodated;
    }

    /**
     * Sets whether this variable should accomodate new categories encountered.
     *
     * @deprecated This is set in DiscreteVariable now.
     */
    public final void setNewCategoriesAccommodated(
            boolean newCategoriesAccommodated) {
        this.newCategoriesAccommodated = newCategoriesAccommodated;
    }

    public void setNumberFormat(NumberFormat nf) {
        if (nf == null) {
            throw new NullPointerException();
        }

        this.nf = nf;
    }

    /**
     * Sets the character ('\t', ' ', ',', for instance) that is used to delimit
     * tokens when the tetradMatrix set is printed out using the toString() method.
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
        List<Integer> permutation = new ArrayList<Integer>();

        for (int i = 0; i < getNumRows(); i++) {
            permutation.add(i);
        }

        Collections.shuffle(permutation);

        TetradMatrix data2 = tetradMatrix.like();

        for (int i = 0; i < getNumRows(); i++) {
            for (int j = 0; j < getNumColumns(); j++) {
                data2.set(i, j, tetradMatrix.get(permutation.get(i), j));
            }
        }

        this.tetradMatrix = data2;
    }

    //===============================PRIVATE METHODS=====================//

    private void setIntPrivate(int row, int col, int value) {
        if (value == -99) {
            tetradMatrix.set(row, col, Double.NaN);
        } else {
            tetradMatrix.set(row, col, value);
        }
    }

    /**
     * Resizes the tetradMatrix to the given dimensions. Data that does not fall within
     * the new dimensions is lost, and positions in the redimensioned tetradMatrix that
     * have no correlates in the old tetradMatrix are set to missing (that is,
     * Double.NaN).
     *
     * @param rows The number of rows in the redimensioned tetradMatrix.
     * @param cols The number of columns in the redimensioned tetradMatrix.
     */
    private void resize(int rows, int cols) {
        TetradMatrix _data = new TetradMatrix(rows, cols);

        for (int i = 0; i < _data.rows(); i++) {
            for (int j = 0; j < _data.columns(); j++) {
                if (i < tetradMatrix.rows() && j < tetradMatrix.columns()) {
                    _data.set(i, j, tetradMatrix.get(i, j));
                } else {
                    _data.set(i, j, Double.NaN);
                }
            }
        }

        tetradMatrix = _data;
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
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (this.data != null) {
            this.tetradMatrix = new TetradMatrix(this.data.toArray());
        }

//        if (this.nf == null) {
//            this.nf = new DecimalFormat("0");
//        }

    }

    /**
     * @return the set of selected nodes, creating a new set if necessary.
     */
    private Set<Node> getSelection() {
        if (selection == null) {
            selection = new HashSet<Node>();
        }
        return selection;
    }

    /**
     * Attempts to translate <code>element</code> into a double value, returning
     * it if successful, otherwise throwing an exception. To be successful, the
     * object must be either a Number or a String.
     *
     * @throws IllegalArgumentException if the translation cannot be made. The
     *                                  reason is in the message.
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
                    "The argument 'element' must be " +
                            "either a Number or a String.");
        }
    }

    /**
     * Attempts to translate <code>element</code> into an int value, returning
     * it if successful, otherwise throwing an exception. To be successful, the
     * object must be either a Number or a String.
     *
     * @throws IllegalArgumentException if the translation cannot be made. The
     *                                  reason is in the message.
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
                        throw new IllegalArgumentException("Variable " + variable +
                                " is not accepting " +
                                "new categories. Problem category is " + ".");
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
                        "The argument 'element' must be " +
                                "either a Number or a String.");
            }
        } else {
            if (element instanceof Number) {
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
                        "The argument 'element' must be " +
                                "either a Number or a String.");
            }
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
            List<String> newCategories = new LinkedList<String>(categories);
            newCategories.add(category);
            DiscreteVariable newVariable =
                    new DiscreteVariable(variable.getName(), newCategories);
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
            throw new IllegalArgumentException("This variable is not set " +
                    "to accomodate new categories.");
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
        List<String> categories =
                new LinkedList<String>(variable.getCategories());
        List<String> newCategories = new LinkedList<String>(categories);

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

        DiscreteVariable to =
                new DiscreteVariable(variable.getName(), newCategories);
        changeVariable(variable, to);
    }

    /**
     * @return the number format, which by default is the one at
     * <code>NumberFormatUtil.getInstance().getNumberFormat()</code>, but can be
     * set by the user if desired.
     *
     * @see #setNumberFormat(java.text.NumberFormat)
     */
    public NumberFormat getNumberFormat() {
        if (nf == null) {
            nf = NumberFormatUtil.getInstance().getNumberFormat();
        }

        return nf;
//        return new DecimalFormat("0");
    }

    public boolean isLineNumbersWritten() {
        return lineNumbersWritten;
    }

    public void setLineNumbersWritten(boolean lineNumbersWritten) {
        this.lineNumbersWritten = lineNumbersWritten;
    }


}





