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
import edu.cmu.tetrad.util.*;
import org.apache.commons.math3.util.FastMath;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.text.NumberFormat;
import java.util.*;


/**
 * Wraps a 2D array of Number objects in such a way that mixed data sets can be stored. The type of each column must be
 * specified by a Variable object, which must be either a <code>ContinuousVariable</code> or a
 * <code>DiscreteVariable</code>. This class violates object orientation in that the underlying data matrix is
 * retrievable using the getDoubleData() method. This is allowed so that external calculations may be performed on large
 * datasets without having to allocate extra memory. If this matrix needs to be modified externally, please consider
 * making a copy of it first, using the TetradMatrix copy() method.
 * <p>
 * The data set may be given a name; this name is not used internally.
 * <p>
 * The data set has a list of variables associated with it, as described above. This list is coordinated with the stored
 * data, in that data for the i'th variable will be in the i'th column.
 * <p>
 * A subset of variables in the data set may be designated as selected. This selection set is stored with the data set
 * and may be manipulated using the
 * <code>select</code> and <code>deselect</code> methods.
 * <p>
 * // * A multiplicity m_i may be associated with each case c_i in the dataset, which // * is interpreted to mean that
 * that c_i occurs m_i times in the dataset. // * <p> Knowledge may be associated with the data set, using the
 * <code>setKnowledge</code> method. This knowledge is not used internally to
 * the data set, but it may be retrieved by algorithm and used.
 * <p>
 * This data set replaces an earlier Minitab-style DataSet class. The reasons for replacement are as follows.
 * </p>
 * <ul> <li>COLT marices are optimized for double 2D matrix calculations in ways
 * that Java-style double[][] matrices are not. <li>The COLT library comes with
 * a wide range of linear algebra library methods that are better tested and
 * more flexible than that linear algebra methods used previously in Tetrad.
 * <li>Views of COLT matrices can often be used in places where copies of data
 * sets were being created. <li>The only place where data sets were being
 * manipulated for honest reasons was in the interface; everywhere else, it
 * turns out to have been sensible to calculate a list of variables and a sample
 * size in advance and allocate memory for a data set with these dimensions. For
 * very large data sets, it makes more sense to disallow memory-hogging
 * manipulations than to throw out-of-memory errors. </ul>
 *
 * @author josephramsey
 * @version $Id: $Id
 * @see edu.cmu.tetrad.data.Variable
 * @see Knowledge
 */
public final class NumberObjectDataSet
        implements DataSet {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The map from column names to tooltips.
     */
    private Map<String, String> columnToTooltip = new HashMap<>();

    /**
     * The name of the data model. This is not used internally; it is only here in case an external class wants this
     * dataset to have a name.
     */
    private String name;

    /**
     * The list of variables. These correspond columnwise to the columns of
     * <code>data</code>.
     */
    private List<Node> variables;

    /**
     * The container storing the data. Rows are cases; columns are variables. The order of columns is coordinated with
     * the order of variables in getVariable().
     */
    private Number[][] data;

    /**
     * The set of selected variables.
     */
    private Set<Node> selection = new HashSet<>();

    /**
     * The knowledge associated with this data.
     */
    private Knowledge knowledge = new Knowledge();

    /**
     * The number formatter used for printing out continuous values.
     */
    private transient NumberFormat nf;
    /**
     * The character used as a delimiter when the dataset is printed.
     */
    private char outputDelimiter = '\t';

    /**
     * Constructs a data set with the given number of rows (cases) and the given list of variables. The number of
     * columns will be equal to the number of cases.
     */
    private NumberObjectDataSet(int rows, List<Node> variables) {
        this.data = new Number[rows][variables.size()];
        this.variables = new LinkedList<>(variables);

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < variables.size(); j++) {
                this.data[i][j] = null;
            }
        }
    }

    /**
     * <p>Constructor for NumberObjectDataSet.</p>
     *
     * @param data      an array of {@link java.lang.Number} objects
     * @param variables a {@link java.util.List} object
     */
    public NumberObjectDataSet(Number[][] data, List<Node> variables) {
        for (Number[] _data : data) {
            if (_data.length != variables.size()) {
                throw new IllegalArgumentException("All rows must have a number " +
                                                   "of elements in them equal to the number of variables.");
            }
        }

        this.data = data;
        this.variables = variables;
    }

    /**
     * Makes of copy of the given data set.
     */
    private NumberObjectDataSet(NumberObjectDataSet dataSet) {
        this.name = dataSet.name;
        this.variables = new LinkedList<>(dataSet.variables);

        this.data = new Number[dataSet.data.length][dataSet.data[0].length];

        for (int i = 0; i < dataSet.data.length; i++) {
            System.arraycopy(dataSet.data[i], 0, this.data[i], 0, dataSet.data[0].length);
        }

        this.selection = new HashSet<>(dataSet.selection);
        this.knowledge = dataSet.knowledge.copy();
        this.columnToTooltip = new HashMap<>(dataSet.columnToTooltip);
        this.outputDelimiter = dataSet.outputDelimiter;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetrad.data.NumberObjectDataSet} object
     */
    public static NumberObjectDataSet serializableInstance() {
        return new NumberObjectDataSet(0, new LinkedList<>());
    }

    /**
     * Adds semantic checks to the default deserialization method. This method must have the standard signature for a
     * readObject method, and the body of the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from version to version. A readObject method of
     * this form may be added to any class, even if Tetrad sessions were previously saved out using a version of the
     * class that didn't include it. (That's what the "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for
     * help.
     */
    private static void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();
    }

    /**
     * Attempts to translate <code>element</code> into a double value, returning it if successful, otherwise throwing an
     * exception. To be successful, the object must be either a Number or a String.
     *
     * @throws IllegalArgumentException if the translation cannot be made. The reason is in the message.
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
     * <p>Getter for the field <code>columnToTooltip</code>.</p>
     *
     * @return a {@link java.util.Map} object
     */
    public Map<String, String> getColumnToTooltip() {
        return this.columnToTooltip;
    }

    /**
     * Gets the name of the data set.
     *
     * @return a {@link java.lang.String} object
     */
    public String getName() {
        return this.name;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Sets the name of the data set.
     */
    public void setName(String name) {
        if (name == null) {
            throw new NullPointerException("Name must not be null.");
        }
        this.name = name;
    }

    /**
     * <p>getNumColumns.</p>
     *
     * @return the number of variables in the data set.
     */
    public int getNumColumns() {
        return this.variables.size();
    }

    /**
     * <p>getNumRows.</p>
     *
     * @return the number of rows in the rectangular data set, which is the maximum of the number of rows in the list of
     * wrapped columns.
     */
    public int getNumRows() {
        return this.data.length;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Sets the value at the given (row, column) to the given int value, assuming the variable for the column is
     * discrete.
     */
    public void setInt(int row, int column, int value) {
        Node variable = getVariable(column);

        if (!(variable instanceof DiscreteVariable _variable)) {
            throw new IllegalArgumentException(
                    "Can only set ints for discrete columns.");
        }

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

            int newRows = FastMath.max(row + 1, this.data.length);
            int newCols = FastMath.max(column + 1, this.data[0].length);
            resize(newRows, newCols);
            setIntPrivate(row, column, value);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Sets the value at the given (row, column) to the given double value, assuming the variable for the column is
     * continuous.
     */
    public void setDouble(int row, int column, double value) {
        if (!(getVariable(column) instanceof ContinuousVariable)) {
            throw new IllegalArgumentException(
                    "Can only set ints for discrete columns: " + getVariable(column));
        }

        try {
            this.data[row][column] = value;
        } catch (Exception e) {
            if (row < 0 || column < 0) {
                throw new IllegalArgumentException(
                        "Row and column must be >= 0.");
            }

            assert this.data != null;
            int newRows = FastMath.max(row + 1, this.data.length);
            int newCols = FastMath.max(column + 1, this.data[0].length);
            resize(newRows, newCols);
            this.data[row][column] = value;
        }
    }

    /**
     * {@inheritDoc}
     */
    public Object getObject(int row, int col) {
        Object variable = getVariable(col);

        if (variable instanceof ContinuousVariable) {
            return getDouble(row, col);
        } else if (variable instanceof DiscreteVariable _variable) {

            if (_variable.isCategoryNamesDisplayed()) {
                return _variable.getCategory(getInt(row, col));
            } else {
                return getInt(row, col);
            }

        }

        throw new IllegalArgumentException("Not a row/col in this data set.");
    }

    /**
     * {@inheritDoc}
     */
    public void setObject(int row, int col, Object value) {
        Object variable = getVariable(col);

        if (variable instanceof ContinuousVariable) {
            setDouble(row, col, NumberObjectDataSet.getValueFromObjectContinuous(value));
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
     * <p>getSelectedIndices.</p>
     *
     * @return the indices of the currently selected variables.
     */
    public int[] getSelectedIndices() {
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
     * <p>getSelectedVariables.</p>
     *
     * @return the set of currently selected variables.
     */
    public Set<Node> getSelectedVariables() {
        return new HashSet<>(this.selection);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Adds the given variable to the data set, increasing the number of columns by one, moving columns i &gt;=
     * <code>index</code> to column i + 1, and inserting a column of missing values at column i.
     */
    public void addVariable(Node variable) {
        if (this.variables.contains(variable)) {
            throw new IllegalArgumentException("Expecting a new variable: " + variable);
        }

        this.variables.add(variable);

        resize(this.data.length, this.variables.size());
        int col = this.data[0].length - 1;

        for (int i = 0; i < this.data.length; i++) {
            this.data[i][col] = null;
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Adds the given variable to the dataset, increasing the number of columns by one, moving columns i &gt;=
     * <code>index</code> to column i + 1, and inserting a column of missing values at column i.
     */
    public void addVariable(int index, Node variable) {
        if (this.variables.contains(variable)) {
            throw new IllegalArgumentException("Expecting a new variable.");
        }

        if (index < 0 || index > this.variables.size()) {
            throw new IndexOutOfBoundsException("Index must in (0, #vars).");
        }

        this.variables.add(index, variable);
        resize(this.data.length, this.variables.size());

        Number[][] _data = new Number[this.data.length][this.data[0].length + 1];

        for (int j = 0; j < this.data[0].length + 1; j++) {
            if (j < index) {
                for (int i = 0; i < this.data.length; i++) {
                    _data[i][j] = this.data[i][j];
                }
            } else if (j == index) {
                for (int i = 0; i < this.data.length; i++) {
                    _data[i][j] = null;
                }
            } else {
                for (int i = 0; i < this.data.length; i++) {
                    _data[i][j] = this.data[i][j - 1];
                }
            }
        }

        this.data = _data;
    }

    /**
     * {@inheritDoc}
     *
     * <p>getVariable.</p>
     */
    public Node getVariable(int col) {
        return this.variables.get(col);
    }

    /**
     * {@inheritDoc}
     */
    public int getColumn(Node variable) {
        return this.variables.indexOf(variable);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Changes the variable for the given column from <code>from</code> to
     * <code>to</code>. Supported currently only for discrete variables.
     */
    public void changeVariable(Node from, Node to) {
        if (!(from instanceof DiscreteVariable _from &&
              to instanceof DiscreteVariable _to)) {
            throw new IllegalArgumentException(
                    "Only discrete variables supported.");
        }

        int col = this.variables.indexOf(_from);

        List<String> oldCategories = _from.getCategories();
        List<String> newCategories = _to.getCategories();

        int[] indexArray = new int[oldCategories.size()];

        for (int i = 0; i < oldCategories.size(); i++) {
            indexArray[i] = newCategories.indexOf(oldCategories.get(i));
        }

        for (int i = 0; i < getNumRows(); i++) {
            if (this.data[i][col] == null) {
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
                this.data[i][col] = null;
            } else {
                setInt(i, col, newIndex);
            }
        }

        this.variables.set(col, _to);
    }

    /**
     * {@inheritDoc}
     */
    public Node getVariable(String varName) {
        for (Node variable1 : this.variables) {
            if (variable1.getName().equals(varName)) {
                return variable1;
            }
        }

        return null;
    }

    /**
     * <p>Getter for the field <code>variables</code>.</p>
     *
     * @return (a copy of) the List of Variables for the data set, in the order of their columns.
     */
    public List<Node> getVariables() {
        return new LinkedList<>(this.variables);
    }

    /**
     * <p>Getter for the field <code>knowledge</code>.</p>
     *
     * @return a copy of the knowledge associated with this data set. (Cannot be null.)
     */
    public Knowledge getKnowledge() {
        return this.knowledge.copy();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Sets knowledge to be associated with this data set. May not be null.
     */
    public void setKnowledge(Knowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException();
        }

        this.knowledge = knowledge.copy();
    }

    /**
     * <p>getVariableNames.</p>
     *
     * @return (a copy of) the List of Variables for the data set, in the order of their columns.
     */
    public List<String> getVariableNames() {
        List<Node> vars = getVariables();
        List<String> names = new ArrayList<>();

        for (Node variable : vars) {
            String name = variable.getName();
            names.add(name);
        }

        return names;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Marks the given column as selected if 'selected' is true or deselected if 'selected' is false.
     */
    public void setSelected(Node variable, boolean selected) {
        if (selected) {
            if (this.variables.contains(variable)) {
                getSelection().add(variable);
            }
        } else {
            if (this.variables.contains(variable)) {
                getSelection().remove(variable);
            }
        }
    }

    /**
     * Marks all variables as deselected.
     */
    public void clearSelection() {
        getSelection().clear();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Ensures that the dataset has at least the number of rows, adding rows if necessary to make that the case. The new
     * rows will be filled with missing values.
     */
    public void ensureRows(int rows) {
        if (rows > getNumRows()) {
            resize(rows, getNumColumns());
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Ensures that the dataset has at least the given number of columns, adding continuous variables with unique names
     * until that is true. The new columns will be filled with missing values.
     */
    public void ensureColumns(int columns, List<String> excludedVariableNames) {
        for (int col = getNumColumns(); col < columns; col++) {
            int i = 0;
            String _name;

            do {
                _name = "X" + (++i);
            } while (getVariable(_name) != null ||
                     excludedVariableNames.contains(_name));

            ContinuousVariable variable = new ContinuousVariable(_name);
            addVariable(variable);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean existsMissingValue() {
        for (int i = 0; i < getNumRows(); i++) {
            for (int j = 0; j < getNumColumns(); j++) {
                if (this.variables.get(j) instanceof ContinuousVariable) {
                    if (Double.isNaN(getDouble(i, j))) return true;
                }

                if (this.variables.get(j) instanceof DiscreteVariable) {
                    if (getInt(i, j) == -99) return true;
                }
            }
        }

        return false;
    }

    /**
     * {@inheritDoc}
     */
    public boolean isSelected(Node variable) {
        return getSelection().contains(variable);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Removes the column for the variable at the given index, reducing the number of columns by one.
     */
    public void removeColumn(int index) {
        if (index < 0 || index >= this.variables.size()) {
            throw new IllegalArgumentException(
                    "Not a column in this data set: " + index);
        }

        this.variables.remove(index);

        int[] rows = new int[this.data.length];

        for (int i = 0; i < this.data.length; i++) {
            rows[i] = i;
        }

        int[] cols = new int[this.data[0].length - 1];

        int m = -1;

        for (int i = 0; i < this.data[0].length; i++) {
            if (i != index) {
                cols[++m] = i;
            }
        }

        this.data = viewSelection(rows, cols);
    }

    private Number[][] viewSelection(int[] rows, int[] cols) {
        Number[][] _data = new Number[rows.length][cols.length];

        for (int i = 0; i < rows.length; i++) {
            for (int j = 0; j < cols.length; j++) {
                _data[i][j] = this.data[rows[i]][cols[j]];
            }
        }
        return _data;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Removes the columns for the given variable from the dataset, reducing the number of columns by one.
     *
     * @param variable a {@link edu.cmu.tetrad.graph.Node} object
     */
    public void removeColumn(Node variable) {
        int index = this.variables.indexOf(variable);

        if (index != -1) {
            removeColumn(index);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Creates and returns a dataset consisting of those variables in the list vars.  Vars must be a subset of the
     * variables of this DataSet. The ordering of the elements of vars will be the same as in the list of variables in
     * this DataSet.
     */
    public DataSet subsetColumns(List<Node> vars) {

        if (!(getVariables().containsAll(vars))) {
            List<Node> missingVars = new ArrayList<>(vars);
            missingVars.removeAll(getVariables());

            throw new IllegalArgumentException(
                    "All vars must be original vars: " + missingVars);
        }

        int[] rows = new int[this.data.length];

        for (int i = 0; i < rows.length; i++) {
            rows[i] = i;
        }

        int[] cols = new int[vars.size()];

        for (int j = 0; j < cols.length; j++) {
            cols[j] = getVariables().indexOf(vars.get(j));
        }

        Number[][] _data = viewSelection(rows, cols);

        NumberObjectDataSet _dataSet = new NumberObjectDataSet(0, new LinkedList<>());
        _dataSet.data = _data;

//        _dataSet.name = name + "_copy";
        _dataSet.variables = vars;
        _dataSet.selection = new HashSet<>();
//        _dataSet.multipliers = new HashMap<>(multipliers);

        // Might have to delete some knowledge.
        _dataSet.knowledge = this.knowledge.copy();

        return _dataSet;
    }

    /**
     * <p>isContinuous.</p>
     *
     * @return true iff this is a continuous data set--that is, if every column in it is continuous. (By implication,
     * empty datasets are both discrete and continuous.)
     */
    public boolean isContinuous() {
        for (int i = 0; i < getNumColumns(); i++) {
            Node variable = this.variables.get(i);

            if (!(variable instanceof ContinuousVariable)) {
                return false;
            }
        }

        return true;
    }

    /**
     * <p>isDiscrete.</p>
     *
     * @return true iff this is a discrete data set--that is, if every column in it is discrete. (By implication, empty
     * datasets are both discrete and continuous.)
     */
    public boolean isDiscrete() {
        for (int i = 0; i < getNumColumns(); i++) {
            Node column = this.variables.get(i);

            if (!(column instanceof DiscreteVariable)) {
                return false;
            }
        }

        return true;
    }

    /**
     * <p>isMixed.</p>
     *
     * @return true if this is a mixed data set--that is, if it contains at least one continuous column and one discrete
     * columnn.
     */
    public boolean isMixed() {
        int numContinuous = 0;
        int numDiscrete = 0;

        for (int i = 0; i < getNumColumns(); i++) {
            Node column = this.variables.get(i);

            if (column instanceof ContinuousVariable) {
                numContinuous++;
            } else if (column instanceof DiscreteVariable) {
                numDiscrete++;
            } else {
                throw new IllegalArgumentException(
                        "Column not of type continuous" +
                        "or of type discrete; can't classify this data set.");
            }
        }

        return numContinuous > 0 && numDiscrete > 0;
    }

    /**
     * <p>getCorrelationMatrix.</p>
     *
     * @return the correlation matrix for this dataset. Defers to
     * <code>Statistic.covariance()</code> in the COLT matrix library, so it
     * inherits the handling of missing values from that library--that is, any off-diagonal correlation involving a
     * column with a missing value is Double.NaN, although all of the on-diagonal elements are 1.0. If that's not the
     * desired behavior, missing values can be removed or imputed first.
     */
    public Matrix getCorrelationMatrix() {
        if (!isContinuous()) {
            throw new IllegalStateException("Not a continuous data set.");
        }

        return MatrixUtils.convertCovToCorr(getCovarianceMatrix());
    }

    /**
     * <p>getCovarianceMatrix.</p>
     *
     * @return the covariance matrix for this dataset. Defers to
     * <code>Statistic.covariance()</code> in the COLT matrix library, so it
     * inherits the handling of missing values from that library--that is, any covariance involving a column with a
     * missing value is Double.NaN. If that's not the desired behavior, missing values can be removed or imputed first.
     */
    public Matrix getCovarianceMatrix() {
        if (!isContinuous()) {
            throw new IllegalStateException("Not a continuous data set.");
        }

        Matrix cov = new Matrix(this.data[0].length, this.data[0].length);

        double[] x = new double[this.data.length];
        double[] y = new double[this.data.length];

        for (int i = 0; i < this.data[0].length; i++) {
            for (int j = 0; j < this.data[0].length; j++) {
                for (int k = 0; k < this.data.length; k++) {
                    x[k] = this.data[k][i].doubleValue();
                    y[k] = this.data[k][j].doubleValue();

                    cov.set(i, j, StatUtils.covariance(x, y));
                }
            }
        }

        return cov;
    }

    /**
     * {@inheritDoc}
     */
    public int getInt(int row, int column) {
        Number value = this.data[row][column];

        if (value == null) {
            return DiscreteVariable.MISSING_VALUE;
        } else {
            return value.intValue();
        }
    }

    /**
     * {@inheritDoc}
     */
    public double getDouble(int row, int column) {
        Number value = this.data[row][column];

        if (value == null) {
            return ContinuousVariable.getDoubleMissingValue();
        } else {
            return value.doubleValue();
        }
    }

    /**
     * <p>toString.</p>
     *
     * @return a string, suitable for printing, of the dataset. Lines are separated by '\n', tokens in the line by
     * whatever character is set in the
     * <code>setOutputDelimiter()</code> method. The list of variables is printed
     * first, followed by one line for each case. This method should probably not be used for saving to files. If that's
     * your goal, use the DataSavers class instead.
     * @see #setOutputDelimiter(Character)
     * @see DataWriter
     */
    public String toString() {
        StringBuilder buf = new StringBuilder();
        List<Node> variables = getVariables();

        buf.append("\n");

        for (int i = 0; i < getNumColumns(); i++) {
            buf.append(variables.get(i));

            if (i < getNumColumns() - 1) {
                buf.append(this.outputDelimiter);
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
                        buf.append(this.outputDelimiter);
                    }
                } else if (variable instanceof DiscreteVariable _variable) {
                    int value = getInt(i, j);

                    if (value == -99) {
                        buf.append("*");
                    } else {
                        String category = _variable.getCategory(value);

                        if (category.indexOf(this.outputDelimiter) == -1) {
                            buf.append(category);
                        } else {
                            buf.append("\"").append(category).append("\"");
                        }
                    }

                    if (j < getNumColumns() - 1) {
                        buf.append(this.outputDelimiter);
                    }
                } else {
                    throw new IllegalStateException(
                            "Expecting either a continuous " +
                            "variable or a discrete variable.");
                }
            }

            buf.append("\n");
        }

        buf.append("\n");

        if (this.knowledge != null && !this.knowledge.isEmpty()) {
            buf.append(this.knowledge);
        }

        return buf.toString();
    }

    /**
     * <p>getDoubleData.</p>
     *
     * @return a copy of the underlying COLT TetradMatrix matrix, containing all of the data in this dataset, discrete
     * data included. Discrete data will be represented by ints cast to doubles. Rows in this matrix are cases, and
     * columns are variables. The list of variable, in the order in which they occur in the matrix, is given by
     * getVariable(). //     * <p> //     * If isMultipliersCollapsed() returns false, multipliers in the dataset are //
     * * first expanded before returning the matrix, so the number of rows in the //     * returned matrix may not be
     * the same as the number of rows in this //     * dataset.
     * @throws java.lang.IllegalStateException if this is not a continuous data set.
     * @see #getVariables //     * @see #isMulipliersCollapsed()
     */
    public Matrix getDoubleData() {
        Matrix copy = new Matrix(this.data.length, this.data[0].length);

        for (int i = 0; i < this.data.length; i++) {
            for (int j = 0; j < this.data[0].length; j++) {
                copy.set(i, j, this.data[i][j].doubleValue());
            }
        }

        return copy;
    }

    /**
     * <p>subsetColumns.</p>
     *
     * @param indices an array of {@link int} objects
     * @return a new data set in which the the column at indices[i] is placed at index i, for i = 0 to indices.length -
     * 1. (Moved over from Purify.)
     */
    public DataSet subsetColumns(int[] indices) {
        List<Node> variables = getVariables();
        List<Node> _variables = new LinkedList<>();

        for (int index : indices) {
            _variables.add(variables.get(index));
        }

        int[] rows = new int[this.data.length];

        for (int i = 0; i < rows.length; i++) {
            rows[i] = i;
        }

        Number[][] _data = viewSelection(rows, indices);

        NumberObjectDataSet _dataSet = new NumberObjectDataSet(0, new LinkedList<>());
        _dataSet.data = _data;

//        _dataSet.name = name + "_copy";
        _dataSet.name = this.name;
        _dataSet.variables = _variables;
        _dataSet.selection = new HashSet<>();
//        _dataSet.multipliers = new HashMap<>(multipliers);

        // Might have to delete some knowledge.
        _dataSet.knowledge = this.knowledge.copy();
        return _dataSet;
    }

    /**
     * <p>subsetRows.</p>
     *
     * @param rows an array of {@link int} objects
     * @return a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public DataSet subsetRows(int[] rows) {
        int[] cols = new int[this.data[0].length];

        for (int i = 0; i < cols.length; i++) {
            cols[i] = i;
        }

        NumberObjectDataSet _data = new NumberObjectDataSet(this);


        _data.data = new Number[rows.length][cols.length];

        for (int i = 0; i < rows.length; i++) {
            for (int j = 0; j < cols.length; j++) {
                _data.data[i][j] = this.data[rows[i]][cols[j]];
            }
        }

        return _data;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataSet subsetRowsColumns(int[] rows, int[] columns) {
        DataSet dataSet = subsetRows(rows);
        dataSet = dataSet.subsetColumns(columns);
        return dataSet;
    }

    /**
     * Removes the given columns from the data set.
     *
     * @param cols an array of {@link int} objects
     */
    public void removeCols(int[] cols) {

        int[] rows = new int[this.data.length];

        for (int i = 0; i < this.data.length; i++) {
            rows[i] = i;
        }

        int[] retainedCols = new int[this.variables.size() - cols.length];
        int i = -1;

        for (int j = 0; j < this.variables.size(); j++) {
            if (Arrays.binarySearch(cols, j) < 0) {
                retainedCols[++i] = j;
            }
        }

        List<Node> retainedVars = new LinkedList<>();

        for (int retainedCol : retainedCols) {
            retainedVars.add(this.variables.get(retainedCol));
        }

        this.data = viewSelection(rows, cols);
        this.variables = retainedVars;
        this.selection = new HashSet<>();
//        multipliers = new HashMap<>(multipliers);
        this.knowledge = this.knowledge.copy(); // Might have to delete some knowledge.
    }

    /**
     * Removes the given rows from the data set.
     *
     * @param selectedRows an array of {@link int} objects
     */
    public void removeRows(int[] selectedRows) {

        int[] cols = new int[this.data[0].length];

        for (int i = 0; i < this.data[0].length; i++) {
            cols[i] = i;
        }

        int[] retainedRows = new int[this.data.length - selectedRows.length];
        int i = -1;

        for (int j = 0; j < this.data.length; j++) {
            if (Arrays.binarySearch(selectedRows, j) < 0) {
                retainedRows[++i] = j;
            }
        }

        this.data = viewSelection(retainedRows, cols);
        this.selection = new HashSet<>();
//        multipliers = new HashMap<>(multipliers);
        this.knowledge = this.knowledge.copy(); // Might have to delete some knowledge.
    }

    /**
     * {@inheritDoc}
     */
    public boolean equals(Object obj) {
        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

        if (obj == this) {
            return true;
        }

        if (!(obj instanceof DataSet _dataSet)) {
            return false;
        }

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

                    if (FastMath.abs(value - _value) > 0.0) {
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

    /**
     * {@inheritDoc}
     */
    @Override
    public DataSet copy() {
        return new NumberObjectDataSet(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataSet like() {
        return new NumberObjectDataSet(getNumRows(), this.variables);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Sets the character ('\t', ' ', ',', for instance) that is used to delimit tokens when the data set is printed out
     * using the toString() method.
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

        RandomUtil.shuffle(permutation);

        Number[][] data2 = new Number[this.data.length][this.data[0].length];

        for (int i = 0; i < getNumRows(); i++) {
            if (getNumColumns() >= 0) System.arraycopy(this.data[permutation.get(i)], 0, data2[i], 0, getNumColumns());
        }

        this.data = data2;
    }

    private void setIntPrivate(int row, int col, int value) {
        if (value == -99) {
            this.data[row][col] = null;
        } else {
            this.data[row][col] = value;
        }
    }

    /**
     * Resizes the data to the given dimensions. Data that does not fall within the new dimensions is lost, and
     * positions in the redimensioned data that have no correlates in the old data are set to missing (that is,
     * Double.NaN).
     *
     * @param rows The number of rows in the redimensioned data.
     * @param cols The number of columns in the redimensioned data.
     */
    private void resize(int rows, int cols) {
        Number[][] _data = new Number[rows][cols];

        for (int i = 0; i < _data.length; i++) {
            for (int j = 0; j < _data[0].length; j++) {
                if (i < this.data.length && j < this.data[0].length) {
                    _data[i][j] = this.data[i][j];
                } else {
                    _data[i][j] = null;
                }
            }
        }

        this.data = _data;
    }

    /**
     * @return the set of selected nodes, creating a new set if necessary.
     */
    private Set<Node> getSelection() {
        if (this.selection == null) {
            this.selection = new HashSet<>();
        }
        return this.selection;
    }

    /**
     * Attempts to translate <code>element</code> into an int value, returning it if successful, otherwise throwing an
     * exception. To be successful, the object must be either a Number or a String.
     *
     * @throws IllegalArgumentException if the translation cannot be made. The reason is in the message.
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
            } else if (element instanceof String label) {

                variable = accomodateCategory(variable, label);
                int index = variable.getIndex(label);

                if (index == -1) {
                    throw new IllegalArgumentException(
                            "Not a category for this variable: " + -1);
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
            } else if (element instanceof String label) {

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
     * If the given category is not already a category for a cagetory, augments the range of category by one and sets
     * the category of the new value to the given category.
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
            DiscreteVariable newVariable =
                    new DiscreteVariable(variable.getName(), newCategories);
            changeVariable(variable, newVariable);
            return newVariable;
        }

        return variable;
    }

    /**
     * Increases the number of categories if necessary to make sure that this variable has the given index.
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
     * Adjusts the size of the categories list to match the getModel number of categories. If the list is too short, it
     * is padded with default categories. If it is too long, the extra categories are removed.
     */
    private void adjustCategories(DiscreteVariable variable,
                                  int numCategories) {
        List<String> categories =
                new LinkedList<>(variable.getCategories());
        List<String> newCategories = new LinkedList<>(categories);

        if (categories.size() > numCategories) {
            if (variable.getCategories().size() > 0) {
                newCategories.subList(0, variable.getCategories().size()).clear();
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
     * <p>getNumberFormat.</p>
     *
     * @return the number format, which by default is the one at
     * <code>NumberFormatUtil.getInstance().getNumberFormat()</code>, but can be
     * set by the user if desired.
     * @see #setNumberFormat(java.text.NumberFormat)
     */
    public NumberFormat getNumberFormat() {
        if (this.nf == null) {
            this.nf = NumberFormatUtil.getInstance().getNumberFormat();
        }

        return this.nf;
    }

    /**
     * {@inheritDoc}
     */
    public void setNumberFormat(NumberFormat nf) {
        if (nf == null) {
            throw new NullPointerException();
        }

        this.nf = nf;
    }
}


