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

package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.Variable;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.JOptionUtils;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableColumnModelListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.text.NumberFormat;
import java.util.EventObject;
import java.util.Hashtable;
import java.util.Map;

/**
 * Displays a DataSet object as a JTable.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class TabularDataJTable extends JTable implements DataModelContainer,
        PropertyChangeListener {

    /**
     * The underlying DataSet model.
     */
    private Map<String, String> columnToTooltip;

    /**
     * States whether edits are allowed.
     */
    private boolean editable = true;

    /**
     * <p>Constructor for TabularDataJTable.</p>
     *
     * @param model           a {@link edu.cmu.tetrad.data.DataSet} object
     * @param columnToTooltip a {@link java.util.Map} object
     */
    public TabularDataJTable(DataSet model, Map<String, String> columnToTooltip) {
        this(model);
        this.columnToTooltip = columnToTooltip;
    }

    /**
     * Constructor. Takes a DataSet as a model.
     *
     * @param model a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public TabularDataJTable(DataSet model) {

        TabularDataTable dataModel = new TabularDataTable(model);

        dataModel.addPropertyChangeListener(this);
        setModel(dataModel);
        setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        this.columnToTooltip
                = model.getColumnToTooltip() != null ? model.getColumnToTooltip() : new Hashtable<>();
        int rowCount = this.dataModel.getRowCount();
        int max = 0;

        while (rowCount > 0) {
            rowCount /= 10;
            max++;
        }

        // provide cell renderer the tooltip.
        addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                int row = rowAtPoint(e.getPoint());
                int col = columnAtPoint(e.getPoint());

                if (row == 0) {
                    setRowSelectionAllowed(false);
                    setColumnSelectionAllowed(true);
                } else if (col == 0) {
                    setRowSelectionAllowed(true);
                    setColumnSelectionAllowed(false);
                } else {
                    setRowSelectionAllowed(true);
                    setColumnSelectionAllowed(true);
                }
            }
        });

        FontMetrics metrics = getFontMetrics(getFont());

        getColumnModel().getColumn(0).setMaxWidth(9 * max);
        setRowHeight(metrics.getHeight() + 3);

        setRowSelectionAllowed(true);
        getColumnModel().setColumnSelectionAllowed(true);

        for (int i = 0; i < model.getNumColumns(); i++) {
            if (model.isSelected(model.getVariable(i))) {
                setRowSelectionAllowed(false);
                addColumnSelectionInterval(i + 1, i + 1);
            }
        }

        getColumnModel().addColumnModelListener(new TableColumnModelListener() {
            public void columnAdded(TableColumnModelEvent e) {
            }

            public void columnRemoved(TableColumnModelEvent e) {
            }

            public void columnMoved(TableColumnModelEvent e) {
            }

            public void columnMarginChanged(ChangeEvent e) {
            }

            /**
             * Sets the selection of columns in the model to what's in the
             * display.
             */
            public void columnSelectionChanged(ListSelectionEvent e) {
                if (e.getValueIsAdjusting()) {
                    return;
                }

                ListSelectionModel selectionModel = (ListSelectionModel) e
                        .getSource();
                DataSet dataSet = getDataSet();
                dataSet.clearSelection();

                if (!getRowSelectionAllowed()) {
                    for (int i = 0; i < dataSet.getNumColumns(); i++) {
                        if (selectionModel.isSelectedIndex(i + 1)) {
                            dataSet.setSelected(dataSet.getVariable(i), true);
                        }
                    }
                }
            }
        });

        setTransferHandler(new TabularDataTransferHandler());

        addFocusListener(new FocusAdapter() {
            public void focusLost(FocusEvent e) {
                JTable table = (JTable) e.getSource();
                TableCellEditor editor = table.getCellEditor();
                if (editor != null) {
                    editor.stopCellEditing();
                }
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Component prepareRenderer(TableCellRenderer renderer, int rowIndex, int vColIndex) {
        Component c = super.prepareRenderer(renderer, rowIndex, vColIndex);
        if (c instanceof JComponent jc) {

            Object o = getValueAt(rowIndex, vColIndex);

            if (o != null) {
                String tooltip = this.columnToTooltip.get(o.toString());
//				System.out.println("tooltip " + o + " "+ tooltip);
                if (tooltip != null) {
                    jc.setToolTipText(tooltip);
                }
            }
        }
        return c;
    }

    /**
     * <p>Setter for the field <code>editable</code>.</p>
     *
     * @param editable a boolean
     */
    public void setEditable(boolean editable) {
        this.editable = editable;
    }

    /**
     * {@inheritDoc}
     */
    public void setValueAt(Object aValue, int row, int column) {
        try {
            super.setValueAt(aValue, row, column);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(JOptionUtils.centeringComp(), e
                    .getMessage(), "Error", JOptionPane.WARNING_MESSAGE);
        }
    }

    /**
     * {@inheritDoc}
     */
    public TableCellEditor getCellEditor(int row, int column) {
        if (!this.editable) {
            return new DoNothingEditor();
        }
        if (row == 1) {
            return new VariableNameEditor();
        } //		else if (column == 1 && row >= 2) {
        //			return new MultiplierEditor();
        //		}
        else if (row > 1) {
            return new DataCellEditor();
        }

        return null;
    }

    /**
     * {@inheritDoc}
     */
    public TableCellRenderer getCellRenderer(int row, int column) {
        if (column == 0) {
            return new RowNumberRenderer();
        } //		else if (column == 1 && row >= 1) {
        //			return new MultiplierRenderer();
        //		}
        else {
            if (row == 0 || row == 1) {
                return new VariableNameRenderer();
            }

            return new DataCellRenderer(this, getNumLeadingCols());
        }
    }

    /**
     * <p>getDataSet.</p>
     *
     * @return the underlying DataSet model.
     */
    public DataSet getDataSet() {
        TabularDataTable tableModelTabularData = (TabularDataTable) getModel();
        return tableModelTabularData.getDataSet();
    }

    /**
     * <p>setDataSet.</p>
     *
     * @param data a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public void setDataSet(DataSet data) {
        TabularDataTable tableModelTabularData = (TabularDataTable) getModel();
        tableModelTabularData.setDataSet(data);
    }

    /**
     * <p>getDataModel.</p>
     *
     * @return a {@link edu.cmu.tetrad.data.DataModel} object
     */
    public DataModel getDataModel() {
        return getDataSet();
    }

    /**
     * <p>deleteSelected.</p>
     */
    public void deleteSelected() {
        TabularDataTable model = (TabularDataTable) getModel();
        DataSet dataSet = model.getDataSet();

        // When getRowSelectionAllowed() is false, getColumnSelectionAllowed() must be true, vise versa.
        // But both can be true since we can select a data cell - Zhou
        if (getColumnSelectionAllowed()) {
            int[] selectedCols = getSelectedColumns();

            TableCellEditor editor = getCellEditor();

            if (editor != null) {
                editor.stopCellEditing();
            }

            for (int i = selectedCols.length - 1; i >= 0; i--) {
//            for (int i = 0; i < selectedCols.length; i++) {
                // Adjust to 0 base
                selectedCols[i] -= getNumLeadingCols();
                // Then remove each individual column from model
                dataSet.removeColumn(selectedCols[i]);
            }

            // Using this causes error - Zhou
            //dataSet.removeCols(selectedCols);
        } else if (getRowSelectionAllowed()) {
            int[] selectedRows = getSelectedRows();

            TableCellEditor editor = getCellEditor();

            if (editor != null) {
                editor.stopCellEditing();
            }

            for (int i = 0; i < selectedRows.length; i++) {
                selectedRows[i] -= 2;
            }

            dataSet.removeRows(selectedRows);
        } else {
            throw new IllegalStateException("Only row deletion and column deltion supported.");
        }

        firePropertyChange("modelChanged", null, null);
        model.fireTableDataChanged();
    }

    /**
     * <p>clearSelected.</p>
     */
    public void clearSelected() {
        TabularDataTable model = (TabularDataTable) getModel();
        DataSet dataSet = model.getDataSet();

        if (!getRowSelectionAllowed()) {
            int[] selectedCols = getSelectedColumns();
            TableCellEditor editor = getCellEditor();

            if (editor != null) {
                editor.stopCellEditing();
            }

            for (int i = selectedCols.length - 1; i >= 0; i--) {
                if (selectedCols[i] < getNumLeadingCols()) {
                    continue;
                }

                int dataCol = selectedCols[i] - getNumLeadingCols();

                if (dataCol >= dataSet.getNumColumns()) {
                    continue;
                }

                Node variable = dataSet.getVariable(dataCol);
                Object missingValue = ((Variable) variable)
                        .getMissingValueMarker();

                for (int j = 0; j < dataSet.getNumRows(); j++) {
                    dataSet.setObject(j, dataCol, missingValue);
                }
            }
        } else if (!getColumnSelectionAllowed()) {
            int[] selectedRows = getSelectedRows();

            TableCellEditor editor = getCellEditor();
            if (editor != null) {
                editor.stopCellEditing();
            }

            for (int i = getColumnCount() - 1; i >= 0; i--) {
                if (i < getNumLeadingCols()) {
                    continue;
                }

                String colName = (String) (getValueAt(1, i));

                if (colName == null) {
                    continue;
                }

                int dataCol = i - getNumLeadingCols();

                Node variable = dataSet.getVariable(dataCol);
                Object missingValue = ((Variable) variable)
                        .getMissingValueMarker();

                for (int j = selectedRows.length - 1; j >= 0; j--) {
                    if (selectedRows[j] < 2) {
                        continue;
                    }

                    if (selectedRows[j] > dataSet.getNumRows() + 1) {
                        continue;
                    }

                    dataSet.setObject(selectedRows[j] - 2, dataCol,
                            missingValue);
                }
            }
        } else {
            int[] selectedRows = getSelectedRows();
            int[] selectedCols = getSelectedColumns();

            TableCellEditor editor = getCellEditor();

            if (editor != null) {
                editor.stopCellEditing();
            }

            for (int i = selectedCols.length - 1; i >= 0; i--) {
                if (selectedCols[i] < getNumLeadingCols()) {
                    continue;
                }

                int dataCol = selectedCols[i] - getNumLeadingCols();

                if (dataCol >= dataSet.getNumColumns()) {
                    continue;
                }

                String colName = (String) (getValueAt(1, selectedCols[i]));

                if (colName == null) {
                    continue;
                }

                Node variable = dataSet.getVariable(dataCol);
                Object missingValue = ((Variable) variable)
                        .getMissingValueMarker();

                for (int j = selectedRows.length - 1; j >= 0; j--) {
                    if (selectedRows[j] < 2) {
                        continue;
                    }

                    if (selectedRows[j] > dataSet.getNumRows() + 1) {
                        continue;
                    }

                    dataSet.setObject(selectedRows[j] - 2, dataCol,
                            missingValue);
                }
            }
        }

        firePropertyChange("modelChanged", null, null);
        model.fireTableDataChanged();
    }

    private int getNumLeadingCols() {
        /*
	  The number of initial "special" columns not used to display the data set.
         */
        return 1;
    }

    /**
     * @param token a {@link java.lang.String} object
     * @param col   a int
     * @return true iff the given token is a legitimate value for the cell at (row, col) in the table.
     */
    public boolean checkValueAt(String token, int col) {
        if (col < getNumLeadingCols()) {
            throw new IllegalArgumentException();
        }

        DataSet dataSet = getDataSet();
        int dataCol = col - getNumLeadingCols();
        // int dataCol = col;

        if (dataCol < dataSet.getNumColumns()) {
            Node variable = dataSet.getVariable(dataCol);
            /** {@inheritDoc} */
            return ((Variable) variable).checkValue(token);
        } else {
            return true;
        }
    }

    /**
     * <p>isShowCategoryNames.</p>
     *
     * @return a boolean
     */
    public boolean isShowCategoryNames() {
        TabularDataTable table = (TabularDataTable) getModel();
        return table.isCategoryNamesShown();
    }

    /**
     * <p>Constructor for DoNothingEditor.</p>
     */
    /**
     * <p>setShowCategoryNames.</p>
     *
     * @param selected a boolean
     */
    public void setShowCategoryNames(boolean selected) {
        TabularDataTable table = (TabularDataTable) getModel();
        table.setCategoryNamesShown(selected);
    }

    /**
     * {@inheritDoc}
     */
    public void propertyChange(PropertyChangeEvent evt) {
        firePropertyChange(evt.getPropertyName(), evt.getOldValue(), evt.getNewValue());
    }

}

class RowNumberRenderer extends DefaultTableCellRenderer {

    public Component getTableCellRendererComponent(JTable table, Object value,
                                                   boolean isSelected, boolean hasFocus, int row, int column) {
        JLabel label = (JLabel) super.getTableCellRendererComponent(table,
                value, isSelected, hasFocus, row, column);

        if (row > 1) {
            setText(Integer.toString(row - 1));
            label.setHorizontalAlignment(SwingConstants.CENTER);
            label.setFont(new Font("SansSerif", Font.BOLD, 12));
        }

        return label;
    }
}

class VariableNameRenderer extends DefaultTableCellRenderer {

    public void setValue(Object value) {
        if (!(value instanceof String)) {
            value = "";
        }

        if (((String) value).contains("\b")) {
            return;
        }

        setText((String) value);
        setFont(new Font("SansSerif", Font.BOLD, 12));
        setHorizontalAlignment(SwingConstants.CENTER);
    }
}

class DoNothingEditor extends DefaultCellEditor {

    public DoNothingEditor() {
        super(new JTextField());
        /**
         * <p>Constructor for DataCellRenderer.</p>
         *
         * @param tableTabular a {@link edu.cmu.tetradapp.editor.TabularDataJTable} object
         * @param numLeadingCols a int
         */
    }

    public boolean isCellEditable(EventObject anEvent) {
        return false;
    }
}

/**
 * {@inheritDoc}
 */
class VariableNameEditor extends DefaultCellEditor {

    private final JTextField textField;

    /**
     * Constructs a new number cell editor.
     */
    public VariableNameEditor() {
        super(new JTextField());

        this.textField = (JTextField) this.editorComponent;

        this.delegate = new EditorDelegate() {

            /**
             * Overrides delegate; sets the value of the textfield to the value
             * of the datum.
             *
             * @param value this value.
             */
            public void setValue(Object value) {
                if (!(value instanceof String)) {
                    value = "";
                }

                VariableNameEditor.this.textField.setText((String) value);
                VariableNameEditor.this.textField.setFont(new Font("SansSerif", Font.BOLD, 12));
                VariableNameEditor.this.textField.setHorizontalAlignment(SwingConstants.CENTER);
                VariableNameEditor.this.textField.selectAll();
            }

            /**
             * Overrides delegate; gets the text value from the cell to send
             * back to the model.
             *
             * @return this text value.
             */
            public Object getCellEditorValue() {
                return VariableNameEditor.this.textField.getText();
            }
        };

        this.textField.addActionListener(this.delegate);
    }
}

class DataCellRenderer extends DefaultTableCellRenderer {

    private final NumberFormat nf;
    private final DataSet dataSet;
    private final int numLeadingCols;

    public DataCellRenderer(TabularDataJTable tableTabular, int numLeadingCols) {
        this.dataSet = ((TabularDataTable) tableTabular.getModel()).getDataSet();
        this.numLeadingCols = numLeadingCols;
        this.nf = this.dataSet.getNumberFormat();
    }

    public void setValue(Object value) {
        if (value instanceof String) {
            setText((String) value);
        } else if (value instanceof Integer) {
            setText(value.toString());
        } else if (value instanceof Double) {
            setText(this.nf.format((double) (Double) value));
        } else {
            setText("");
        }
    }

    public Component getTableCellRendererComponent(JTable table, Object value,
                                                   boolean isSelected, boolean hasFocus, int row, int col) {

        // Have to set the alignment here, since this is the only place the col
        // index of the component is available...
        Component c = super.getTableCellRendererComponent(table, value,
                isSelected, hasFocus, row, col);
        DefaultTableCellRenderer renderer = (DefaultTableCellRenderer) c;

        if (this.dataSet.getNumColumns() > 0 && col >= getNumLeadingCols()
            && col < this.dataSet.getNumColumns() + getNumLeadingCols()) {
            renderer.setHorizontalAlignment(SwingConstants.RIGHT);
        }

        return renderer;
    }

    private int getNumLeadingCols() {
        return this.numLeadingCols;
    }
}

