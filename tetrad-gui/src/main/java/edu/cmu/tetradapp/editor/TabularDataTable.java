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
package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.data.Variable;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeVariableType;
import org.apache.commons.math3.util.FastMath;

import javax.swing.table.AbstractTableModel;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;

/**
 * Wraps a dataSet which is possibly smaller than the display window in a larger AbstractTableModel which will fill the
 * window.
 *
 * @author josephramsey
 */
class TabularDataTable extends AbstractTableModel {

    private static final long serialVersionUID = 8832459230421410126L;
    /**
     * Fires property change events.
     */
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    /**
     * The DataSet being displayed.
     */
    private DataSet dataSet;
    /**
     * True iff category names for discrete variables should be shown.
     */
    private boolean categoryNamesShown = true;

    /**
     * Constructs a new DisplayTableModel to wrap the given dataSet.
     *
     * @param dataSet the dataSet.
     */
    public TabularDataTable(DataSet dataSet) {
        this.dataSet = dataSet;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Note that returning null here has two effects. First, it
     */
    public String getColumnName(int col) {
        return null;
    }

    /**
     * <p>getRowCount.</p>
     *
     * @return the number of rows in the wrapper table model. Guarantees that this number will be at least 100.
     */
    public int getRowCount() {
        int maxRowCount = this.dataSet.getNumRows() + 3;
        return FastMath.max(maxRowCount, 100);
    }

    /**
     * <p>getColumnCount.</p>
     *
     * @return the number of columns in the wrapper table model. Guarantees that this number will be at least 30.
     */
    public int getColumnCount() {
        return (this.dataSet.getNumColumns() < 30) ? 30
                : this.dataSet.getNumColumns() + getNumLeadingCols() + 1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object getValueAt(int row, int col) {
        int columnIndex = col - getNumLeadingCols();
        int rowIndex = row - 2;

//        if (col == 1) {
//            if (row == 1) {
//                return "MULT";
//            }
//            else if (rowIndex >= 0 && rowIndex < dataSet.getNumRows()) {
//                return dataSet.getMultiplier(rowIndex);
//            }
//        }
//        else
        if (col >= getNumLeadingCols()
            && col < this.dataSet.getNumColumns() + getNumLeadingCols()) {
            Node variable = this.dataSet.getVariable(columnIndex);

            if (row == 0) {
                // Append "-D" notation to discrete variables, "-C" for continuous
                // and append additional "-I" for those added interventional variables - Zhou
                String columnHeaderNotationDefault = "C";
                String columnHeader = columnHeaderNotationDefault + (columnIndex + 1);

                if (variable instanceof DiscreteVariable) {
                    String columnHeaderNotationDiscrete = "-D";
                    columnHeader += columnHeaderNotationDiscrete;
                } else if (variable instanceof ContinuousVariable) {
                    String columnHeaderNotationContinuous = "-C";
                    columnHeader += columnHeaderNotationContinuous;
                }

                // Add header notations for interventional status and value
                if (variable.getNodeVariableType() == NodeVariableType.INTERVENTION_STATUS) {
                    String columnHeaderNotationInterventionStatus = "-I_S";
                    columnHeader += columnHeaderNotationInterventionStatus;
                } else if (variable.getNodeVariableType() == NodeVariableType.INTERVENTION_VALUE) {
                    String columnHeaderNotationInterventionValue = "-I_V";
                    columnHeader += columnHeaderNotationInterventionValue;
                }

                return columnHeader;
            } else if (row == 1) {
                return this.dataSet.getVariable(columnIndex).getName();
            } else if (rowIndex >= this.dataSet.getNumRows()) {
                return null;
            } else {
                if (variable instanceof DiscreteVariable) {
                    ((DiscreteVariable) variable).setCategoryNamesDisplayed(
                            isCategoryNamesShown());
                }

                Object value = this.dataSet.getObject(rowIndex, columnIndex);

                if (((Variable) variable).isMissingValue(value)) {
                    return "*";
                } else {
                    return value;
                }
            }
        } else if (col >= this.dataSet.getNumColumns() + getNumLeadingCols()) {
            if (row == 0) {
                return "C" + (columnIndex + 1);
            }
        }

        return null;
    }

    /**
     * {@inheritDoc}
     */
    public boolean isCellEditable(int row, int col) {
        return row > 0 && col >= 1;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Sets the value at the given (row, col) coordinates of the table as an Object. If the variable for the col is a
     * DiscreteVariable, the String value (as opposed to the integer index value) is extracted and returned. If the
     * coordinates are out of range of the wrapped table model, 'null' is returned. Otherwise, the value stored in the
     * wrapped table model at the given coordinates is returned.
     */
    public void setValueAt(Object value, int row, int col) {
        this.dataSet.ensureColumns(col - getNumLeadingCols() + 1, new ArrayList<>());
        this.dataSet.ensureRows(row - getNumLeadingRows() + 1);

        if (col == 0) {
            throw new IllegalArgumentException("Bad col index: " + 0);
        }

        if (col >= getNumLeadingCols()
            && col < this.dataSet.getNumColumns() + getNumLeadingCols()) {
            if (row == 1) {
                setColumnName(col, value);
            } else if (row > 1) {
                try {
                    pasteIntoColumn(row, col, value);
                } catch (Exception e) {
                    e.printStackTrace();
                    this.pcs.firePropertyChange("modelChanged", null, null);
                    return;
                }
            }
        }

        fireTableDataChanged();
        this.pcs.firePropertyChange("modelChanged", null, null);
    }

    private void setColumnName(int col, Object value) {
        String oldName = this.dataSet.getVariable(col - getNumLeadingCols()).getName();
        String newName = (String) value;

        if (oldName.equals(newName)) {
            return;
        }

        this.dataSet.getVariable(col - getNumLeadingCols()).setName(newName);
        this.pcs.firePropertyChange("modelChanged", null, null);
        this.pcs.firePropertyChange("variableNameChanged", oldName, newName);
    }

    /**
     * The row and column indices are JTable indices.
     */
    private void pasteIntoColumn(int row, int col, Object value) {
        int dataRow = row - getNumLeadingRows();
        int dataCol = col - getNumLeadingCols();
        Node variable = this.dataSet.getVariable(dataCol);

        if (variable instanceof ContinuousVariable && value instanceof Number) {
            this.dataSet.setObject(dataRow, dataCol, value);
            return;
        }

        if ("".equals(value) || value == null) {
            return;
        }

        String valueTrimmed = ((String) value).trim();
        boolean quoted = false;

        if (valueTrimmed.startsWith("\"") && valueTrimmed.endsWith("\"")) {
            value = valueTrimmed.substring(1, valueTrimmed.length() - 1);
            quoted = true;
        }

        if (!(variable instanceof DiscreteVariable)
            && isEmpty(this.dataSet, dataCol)
            && (quoted || !isNumber((String) value))) {
            variable = swapDiscreteColumnForContinuous(col);
        }

        if (((String) value).trim().equals("*")) {
            value = ((Variable) variable).getMissingValueMarker();
        }

        this.dataSet.setObject(dataRow, dataCol, value);

        this.pcs.firePropertyChange("modelChanged", null, null);
    }

    private boolean isEmpty(DataSet dataSet, int column) {
        Node variable = dataSet.getVariable(column);
        Object missingValue = ((Variable) variable).getMissingValueMarker();

        for (int i = 0; i < dataSet.getNumRows(); i++) {
            if (!(dataSet.getObject(i, column).equals(missingValue))) {
                return false;
            }
        }

        return true;
    }

    private Node swapDiscreteColumnForContinuous(int col) {
        Node variable = this.dataSet.getVariable(col - getNumLeadingCols());
        if (variable == null) {
            throw new NullPointerException();
        }
        if (!isEmpty(this.dataSet, col - getNumLeadingCols())) {
            throw new IllegalArgumentException("Old column not empty.");
        }
        String name = variable.getName();
        DiscreteVariable var = new DiscreteVariable(name);
        var.setCategoryNamesDisplayed(true);
        this.dataSet.removeColumn(col - getNumLeadingCols());
        this.dataSet.addVariable(col - getNumLeadingCols(), var);
        this.pcs.firePropertyChange("modelChanged", null, null);
        return var;
    }

    private boolean isNumber(String value) {
        try {
            Double.parseDouble(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * <p>Getter for the field <code>dataSet</code>.</p>
     *
     * @return the DataSet being presented.
     */
    public DataSet getDataSet() {
        return this.dataSet;
    }

    /**
     * <p>Setter for the field <code>dataSet</code>.</p>
     *
     * @param data a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public void setDataSet(DataSet data) {
        if (data == null) {
            throw new NullPointerException("Data set was null.");
        }
        this.dataSet = data;
    }

    private int getNumLeadingRows() {
        /*
      The number of initial "special" columns not used to display the data
      set.
         */
        return 2;
    }

    private int getNumLeadingCols() {
        /*
      The number of initial "special" columns not used to display the data
      set.
         */

        return 1;
    }

    /**
     * <p>isCategoryNamesShown.</p>
     *
     * @return a boolean
     */
    public boolean isCategoryNamesShown() {
        return this.categoryNamesShown;
    }

    /**
     * <p>Setter for the field <code>categoryNamesShown</code>.</p>
     *
     * @param selected a boolean
     */
    public void setCategoryNamesShown(boolean selected) {
        this.categoryNamesShown = selected;
        fireTableDataChanged();
    }

    /**
     * <p>addPropertyChangeListener.</p>
     *
     * @param listener a {@link java.beans.PropertyChangeListener} object
     */
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        this.pcs.addPropertyChangeListener(listener);
    }
}
