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

package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.data.Variable;
import edu.cmu.tetrad.graph.Node;

import javax.swing.table.AbstractTableModel;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

/**
 * Wraps a dataSet which is possibly smaller than the display window in a larger
 * AbstractTableModel which will fill the window.
 *
 * @author Joseph Ramsey
 */
class TabularDataTable extends AbstractTableModel {

    /**
     * The DataSet being displayed.
     */
    private DataSet dataSet;

    /**
     * The number of initial "special" columns not used to display the data
     * set.
     */
    private int numLeadingRows = 2;

    /**
     * The number of initial "special" columns not used to display the data
     * set.
     */
    private int numLeadingCols = 2;

    /**
     * True iff category names for discrete variables should be shown.
     */
    private boolean categoryNamesShown = true;

    /**
     * Fires property change events.
     */
    private PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    /**
     * Constructs a new DisplayTableModel to wrap the given dataSet.
     *
     * @param dataSet the dataSet.
     */
    public TabularDataTable(DataSet dataSet) {
        this.dataSet = dataSet;
    }

    /**
     * Note that returning null here has two effects. First, it
     */
    public String getColumnName(int col) {
        return null;
    }

    /**
     * @return the number of rows in the wrapper table model. Guarantees that
     * this number will be at least 100.
     *
     * @return the row count of the wrapped model or 100, whichever is larger.
     */
    public int getRowCount() {
        int maxRowCount = dataSet.getNumRows() + 3;
        return (maxRowCount < 100) ? 100 : maxRowCount;
    }

    /**
     * @return the number of columns in the wrapper table model. Guarantees that
     * this number will be at least 30.
     *
     * @return the column count of the wrapped model or 30, whichever is
     *         larger.
     */
    public int getColumnCount() {
        return (dataSet.getNumColumns() < 30) ? 30 :
                dataSet.getNumColumns() + getNumLeadingCols() + 1;
    }

    /**
     * @return the value at the given (row, col) coordinates of the table as
     * an Object.  If the variable for the col is a DiscreteVariable, the String
     * value (as opposed to the integer index value) is extracted and
     * returned. If the coordinates are out of range of the wrapped table model,
     * 'null' is returned. Otherwise, the value stored in the wrapped table
     * model at the given coordinates is returned.
     */
    public Object getValueAt(int row, int col) {
        int columnIndex = col - getNumLeadingCols();
        int rowIndex = row - 2;

        if (col == 1) {
            if (row == 1) {
                return "MULT";
            }
            else if (rowIndex >= 0 && rowIndex < dataSet.getNumRows()) {
                return dataSet.getMultiplier(rowIndex);
            }
        }
        else if (col >= getNumLeadingCols() &&
                col < dataSet.getNumColumns() + getNumLeadingCols()) {
            Node variable = dataSet.getVariable(columnIndex);

            if (row == 0) {
                boolean discrete = variable instanceof DiscreteVariable;
                return "C" + Integer.toString(columnIndex + 1) +
                        (discrete ? "-T" : "");
            }
            else if (row == 1) {
                return dataSet.getVariable(columnIndex).getName();
            }
            else if (rowIndex >= dataSet.getNumRows()) {
                return null;
            }
            else {
                if (variable instanceof DiscreteVariable) {
                    ((DiscreteVariable) variable).setCategoryNamesDisplayed(
                            isCategoryNamesShown());
                }

                Object value = dataSet.getObject(rowIndex, columnIndex);

                if (((Variable) variable).isMissingValue(value)) {
                    return "*";
                }
                else {
                    return value;
                }
            }
        }
        else if (col >= dataSet.getNumColumns() + getNumLeadingCols()) {
            if (row == 0) {
                return "C" + Integer.toString(columnIndex + 1);
            }
        }

        return null;
    }

    public boolean isCellEditable(int row, int col) {
        return row > 0 && col >= 1;
    }

    /**
     * @return the value at the given (row, col) coordinates of the table as
     * an Object.  If the variable for the col is a DiscreteVariable, the String
     * value (as opposed to the integer index value) is extracted and
     * returned. If the coordinates are out of range of the wrapped table model,
     * 'null' is returned. Otherwise, the value stored in the wrapped table
     * model at the given coordinates is returned.
     */
    public void setValueAt(Object value, int row, int col) {
        if (col == 0) {
            throw new IllegalArgumentException("Bad col index: " + col);
        }
        if (col == 1) {
            if (row >= 2) {
                try {
                    int multiplier = new Integer((String) value);
                    dataSet.setMultiplier(row - 2, multiplier);
                }
                catch (Exception e) {
                    dataSet.setMultiplier(row - 2, 1);
                }
            }
        }
        else if (col >= getNumLeadingCols() &&
                col < dataSet.getNumColumns() + getNumLeadingCols()) {
            if (row == 1) {
                setColumnName(col, value);
            }
            else if (row > 1) {
                try {
                    pasteIntoColumn(row, col, value);
                }
                catch (Exception e) {
                    e.printStackTrace();
                    pcs.firePropertyChange("modelChanged", null, null);
                    return;
                }
            }
        }
        else {
            addColumnsOutTo(col);

            if (row == 1) {
                setColumnName(col, newColumnName((String) value));
            }
            else if (row > 1) {
                try {
                    pasteIntoColumn(row, col, value);
                }
                catch (Exception e) {
                    e.printStackTrace();
                    pcs.firePropertyChange("modelChanged", null, null);
                    return;
                }
            }
        }

        fireTableDataChanged();
        pcs.firePropertyChange("modelChanged", null, null);

        // The only reason for this it to paste in columns or rows that go
        // out of bounds, but it doesn't work anyway. Need to fix. In any
        // case, definitely shouldn't call it every time a cell is set, since
        // this resets the entire model every time, reallocating all columns,
        // etc. Also, when calling it, need to somehow reset the sizes of the
        // initial columns (MULT, index) so they don't get reset to the width
        // of every other column. TODO.
//        fireTableStructureChanged();
    }

    /**
     * Col index here is JTable index.
     */
    private void addColumnsOutTo(int col) {
        for (int i = dataSet.getNumColumns() + getNumLeadingCols();
             i <= col; i++) {
            ContinuousVariable var = new ContinuousVariable("");
            dataSet.addVariable(var);

            System.out.println("Adding " + var + " col " + dataSet.getColumn(var));
        }

        pcs.firePropertyChange("modelChanged", null, null);
    }

    private String newColumnName(String suggestedName) {
        if (!existsColByName(suggestedName)) {
            return suggestedName;
        }

        int i = 0;

        while (true) {
            String proposedName = suggestedName + "-" + (++i);
            if (!existsColByName(proposedName)) {
                return proposedName;
            }
        }
    }

    private boolean existsColByName(String proposedName) {
        for (int i = 0; i < dataSet.getNumColumns(); i++) {
            String name = dataSet.getVariable(i).getName();
            if (name.equals(proposedName)) {
                return true;
            }
        }
        return false;
    }

    private void setColumnName(int col, Object value) {
        String oldName = dataSet.getVariable(col - getNumLeadingCols()).getName();
        String newName = (String) value;

        if (oldName.equals(newName)) return;

//        try {
//            pcs.firePropertyChange("propesedVariableNameChange", oldName, newName);
//        } catch (IllegalArgumentException e) {
//            JOptionPane.showMessageDialog(JOptionUtils.centeringComp(), e.getMessage());
//            return;
//        }
//
//        pcs.firePropertyChange("variableNameChange", oldName, newName);

        dataSet.getVariable(col - getNumLeadingCols()).setName(newName);
        pcs.firePropertyChange("modelChanged", null, null);
        pcs.firePropertyChange("variableNameChanged", oldName, newName);
    }

    /**
     * The row and column indices are JTable indices.
     */
    private void pasteIntoColumn(int row, int col, Object value) {
        int dataRow = row - getNumLeadingRows();
        int dataCol = col - getNumLeadingCols();
        Node variable = dataSet.getVariable(dataCol);

        if (variable instanceof ContinuousVariable && value instanceof Number) {
            dataSet.setObject(dataRow, dataCol, value);
//            dataSet.setDouble(dataRow, dataCol, (Double) value);
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

        if (!(variable instanceof DiscreteVariable) &&
                isEmpty(dataSet, dataCol) &&
                (quoted || !isNumber((String) value))) {
            variable = swapDiscreteColumnForContinuous(col);
        }

        if (value instanceof String && ((String) value).trim().equals("*")) {
            value = ((Variable) variable).getMissingValueMarker();
        }

        dataSet.setObject(dataRow, dataCol, value);

        pcs.firePropertyChange("modelChanged", null, null);
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
        Node variable = dataSet.getVariable(col - getNumLeadingCols());
        if (variable == null) {
            throw new NullPointerException();
        }
        if (!isEmpty(dataSet, col - getNumLeadingCols())) {
            throw new IllegalArgumentException("Old column not empty.");
        }
        String name = variable.getName();
        DiscreteVariable var = new DiscreteVariable(name);
        var.setCategoryNamesDisplayed(true);
        dataSet.removeColumn(col - getNumLeadingCols());
        dataSet.addVariable(col - getNumLeadingCols(), var);
        pcs.firePropertyChange("modelChanged", null, null);
        return var;
    }

    private boolean isNumber(String value) {
        try {
            Double.parseDouble(value);
            return true;
        }
        catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * @return the DataSet being presented.
     */
    public DataSet getDataSet() {
        return dataSet;
    }


    public void setDataSet(DataSet data){
        if(data == null){
            throw new NullPointerException("Data set was null.");
        }
        this.dataSet = data;
    }

    private int getNumLeadingRows() {
        return numLeadingRows;
    }

    private int getNumLeadingCols() {
        return numLeadingCols;
    }

    public void setCategoryNamesShown(boolean selected) {
        this.categoryNamesShown = selected;
        fireTableDataChanged();
    }

    public boolean isCategoryNamesShown() {
        return categoryNamesShown;
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }
}





