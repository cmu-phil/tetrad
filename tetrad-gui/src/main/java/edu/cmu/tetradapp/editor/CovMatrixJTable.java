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
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.MatrixUtils;
import edu.cmu.tetrad.util.NumberFormatUtil;

import javax.swing.*;
import javax.swing.border.LineBorder;
import javax.swing.event.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.text.NumberFormat;

/**
 * Presents a covariance matrix as a JTable.
 *
 * @author josephramsey
 */
public class CovMatrixJTable extends JTable implements DataModelContainer,
        PropertyChangeListener {

    /**
     * The cell renderer for the table.
     */
    private final CovCellRenderer covCellRenderer;

    /**
     * The cell editor for the table.
     */
    private final CovCellEditor covCellEditor;

    /**
     * Construct a new JTable for the given CovarianceMatrix.
     *
     * @param covMatrix a {@link edu.cmu.tetrad.data.ICovarianceMatrix} object
     * @see edu.cmu.tetrad.data.CovarianceMatrix
     */
    public CovMatrixJTable(ICovarianceMatrix covMatrix) {
        if (covMatrix == null) {
            throw new NullPointerException();
        }

        CovMatrixTable dataModel = new CovMatrixTable(covMatrix);
        dataModel.addPropertyChangeListener(this);
        setModel(dataModel);
        setDefaultEditor(Number.class, new NumberCellEditor());
        setDefaultRenderer(Number.class, new NumberCellRenderer());
        setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

        this.covCellEditor = new CovCellEditor();
        this.covCellRenderer = new CovCellRenderer(covMatrix);

        setRowSelectionAllowed(true);
        setColumnSelectionAllowed(true);

        // Nix the table header.
        setTableHeader(null);

        dataModel.addTableModelListener(new TableModelListener() {
            public void tableChanged(TableModelEvent e) {
                firePropertyChange("tableChanged", null, null);
            }
        });

        addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                CovMatrixTable covMatrixTable = (CovMatrixTable) getModel();
                ICovarianceMatrix covMatrix = covMatrixTable.getCovMatrix();
                int row = rowAtPoint(e.getPoint());
                int col = columnAtPoint(e.getPoint());

                if (!(row >= 3 && row < 4 + covMatrix.getDimension() &&
                      col < 1 + covMatrix.getDimension())) {
                    ListSelectionModel rowSelectionModel = getSelectionModel();
                    ListSelectionModel colSelectionModel = getColumnModel()
                            .getSelectionModel();

                    rowSelectionModel.clearSelection();
                    colSelectionModel.clearSelection();
                }

                super.mousePressed(e);
            }
        });

        getSelectionModel().addListSelectionListener(
                new ListSelectionListener() {
                    public void valueChanged(ListSelectionEvent e) {
                        updateSelection();
                    }
                });

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
                updateSelection();
            }
        });
    }

    private void updateSelection() {
        ListSelectionModel rowSelectionModel = getSelectionModel();
        ListSelectionModel colSelectionModel = getColumnModel()
                .getSelectionModel();

        CovMatrixTable covMatrixTable = (CovMatrixTable) getModel();
        ICovarianceMatrix covMatrix = covMatrixTable.getCovMatrix();
        covMatrix.clearSelection();

        for (int i = 0; i < covMatrix.getDimension(); i++) {
            Node variable = covMatrix.getVariables().get(i);

            if (colSelectionModel.isSelectedIndex(i + 1)) {
                covMatrix.select(variable);
            }

            if (rowSelectionModel.isSelectedIndex(i + 4)) {
                covMatrix.select(variable);
            }
        }

        for (int i = -1; i < covMatrix.getDimension(); i++) {
            for (int j = -1; j < covMatrix.getDimension(); j++) {
                covMatrixTable.fireTableCellUpdated(i + 4, j + 1);
            }
        }

        firePropertyChange("modelChanged", null, null);
    }

    /**
     * Returns the covariance matrix.
     *
     * @return the covariance matrix.
     */
    public TableCellEditor getCellEditor(int row, int col) {
        CovMatrixTable covMatrixTable = (CovMatrixTable) getModel();
        this.covCellEditor.setRed(false);

        if (row >= 4 && col >= 1) {
            java.util.List<String> varNames = covMatrixTable.getCovMatrix().getVariableNames();
            java.util.List<String> selectedVarNames = covMatrixTable.getCovMatrix().getSelectedVariableNames();
            ICovarianceMatrix subMatrix = covMatrixTable.getCovMatrix().getSubmatrix(selectedVarNames);

            if (selectedVarNames.contains(varNames.get(row - 4)) && selectedVarNames.contains(varNames.get(col - 1))) {
                if (!MatrixUtils.isPositiveDefinite(subMatrix.getMatrix())) {
//                    covCellEditor.setRed(!covMatrixTable.isEditingMatrixPositiveDefinite());
                    this.covCellEditor.setRed(true);
                }
            }
        }
        return this.covCellEditor;
    }

    /**
     * Returns the covariance matrix.
     *
     * @return the covariance matrix.
     */
    public TableCellRenderer getCellRenderer(int row, int col) {
        CovMatrixTable covMatrixTable = (CovMatrixTable) getModel();
//        covCellRenderer.setPositiveDefinite(false);

        if (covMatrixTable.getColumnCount() <= 200) {
            java.util.List<String> varNames = covMatrixTable.getCovMatrix().getVariableNames();
            java.util.List<String> selectedVarNames = covMatrixTable.getCovMatrix().getSelectedVariableNames();
            ICovarianceMatrix subMatrix = covMatrixTable.getCovMatrix().getSubmatrix(selectedVarNames);

            this.covCellEditor.setRed(false);
            this.covCellRenderer.setPositiveDefinite(true);

            if (row >= 4 && row - 4 < varNames.size() && col >= 1 && col - 1 < varNames.size()) {
                if (selectedVarNames.contains(varNames.get(row - 4)) && selectedVarNames.contains(varNames.get(col - 1))) {
                    if (!MatrixUtils.isPositiveDefinite(subMatrix.getMatrix())) {
//                    covCellEditor.setRed(!covMatrixTable.isEditingMatrixPositiveDefinite());
                        this.covCellEditor.setRed(true);
                        this.covCellRenderer.setPositiveDefinite(false);
                    }
                }
            }
        }


        return this.covCellRenderer;
    }

    /**
     * Returns the covariance matrix.
     *
     * @return the covariance matrix.
     */
    public DataModel getDataModel() {
        CovMatrixTable covMatrixTable = (CovMatrixTable) getModel();
        return covMatrixTable.getCovMatrix();
    }

    /**
     * Returns true if the covariance matrix is positive definite.
     *
     * @return true if the covariance matrix is positive definite.
     */
    public boolean isEditingMatrixPositiveDefinite() {
        CovMatrixTable covMatrixTable = (CovMatrixTable) getModel();
        return covMatrixTable.isEditingMatrixPositiveDefinite();
    }

    /**
     * Restores the covariance matrix to its original state.
     */
    public void restore() {
        CovMatrixTable covMatrixTable = (CovMatrixTable) getModel();
        covMatrixTable.restore();
    }

    /**
     * Property change listener.
     */
    public void propertyChange(PropertyChangeEvent evt) {
        if ("modelChanged".equals(evt.getPropertyName())) {
            firePropertyChange("modelChanged", null, null);
        }
    }

    /**
     * Deletes the selected variables from the covariance matrix.
     */
    public void deleteSelected() {
        CovMatrixTable model = (CovMatrixTable) getModel();
        ICovarianceMatrix cov = model.getCovMatrix();

        java.util.List<String> selected = cov.getSelectedVariableNames();
        java.util.List<String> remaining = cov.getVariableNames();
        remaining.removeAll(selected);
        cov.removeVariables(remaining);

        firePropertyChange("modelChanged", null, null);
        model.fireTableDataChanged();
    }
}

/**
 * A table model for the covariance matrix.
 */
class CovCellRenderer extends DefaultTableCellRenderer {
    private final NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
    private final ICovarianceMatrix covMatrix;
    private final Color selectedColor = new Color(204, 204, 255);
    private boolean positiveDefinite = true;

    public CovCellRenderer(ICovarianceMatrix covMatrix) {
        if (covMatrix == null) {
            throw new NullPointerException();
        }

        this.covMatrix = covMatrix;
    }

    public void setValue(Object value) {
        if (value instanceof String) {
            setText((String) value);
        } else if (value instanceof Integer) {
            setText(value.toString());
        } else if (value instanceof Double) {
            double doubleValue = (Double) value;
            setText(this.nf.format(doubleValue));
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

        renderer.setBackground(Color.WHITE);
        renderer.setForeground(Color.BLACK);

        if (!isPositiveDefinite() && row >= 4 && col >= 1) {
            renderer.setForeground(Color.RED);
        }

        if (value instanceof Number) {
            renderer.setHorizontalAlignment(SwingConstants.RIGHT);
        } else {
            renderer.setHorizontalAlignment(SwingConstants.LEFT);
        }

        java.util.List variables = this.covMatrix.getVariables();
        int rowVar = row - 4;
        int colVar = col - 1;
        int numVars = variables.size();

        if (colVar >= 0 && colVar < numVars && rowVar >= 0 &&
            rowVar < numVars && rowVar >= colVar) {
            boolean rowSelected =
                    this.covMatrix.isSelected((Node) variables.get(rowVar));
            boolean colSelected =
                    this.covMatrix.isSelected((Node) variables.get(colVar));

            if (rowSelected && colSelected) {
                renderer.setBackground(this.selectedColor);
            }
        }

        if (colVar == -1 && rowVar >= 0 && rowVar < numVars) {
            boolean rowSelected =
                    this.covMatrix.isSelected((Node) variables.get(rowVar));

            if (rowSelected) {
                renderer.setBackground(this.selectedColor);
            }
        }

        if (rowVar == -1 && colVar >= 0 && colVar < numVars) {
            boolean colSelected =
                    this.covMatrix.isSelected((Node) variables.get(colVar));

            if (colSelected) {
                renderer.setBackground(this.selectedColor);
            }
        }

        if (hasFocus) {
            renderer.setBackground(Color.WHITE);
            renderer.setBorder(new LineBorder(Color.BLACK));
        }

        return renderer;
    }

    private boolean isPositiveDefinite() {
        return this.positiveDefinite;
    }

    public void setPositiveDefinite(boolean positiveDefinite) {
        this.positiveDefinite = positiveDefinite;
    }

    public ICovarianceMatrix getCovMatrix() {
        return this.covMatrix;
    }

}

/**
 * A cell editor for the covariance matrix.
 */
class CovCellEditor extends DefaultCellEditor {
    private final NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
    private final JTextField textField;

    /**
     * Constructs a new number cell editor.
     */
    public CovCellEditor() {
        super(new JTextField());

        this.textField = (JTextField) this.editorComponent;
        this.textField.setHorizontalAlignment(SwingConstants.LEFT);
        this.textField.setBorder(new LineBorder(Color.black));

        this.delegate = new EditorDelegate() {
            public void setValue(Object value) {
                if (value == null) {
                    CovCellEditor.this.textField.setText("");
                } else if (value instanceof String) {
                    CovCellEditor.this.textField.setText((String) value);
                } else if (value instanceof Integer) {
                    CovCellEditor.this.textField.setText(value.toString());
                } else if (value instanceof Double) {
                    double doubleValue = (Double) value;

                    if (Double.isNaN(doubleValue)) {
                        CovCellEditor.this.textField.setText("");
                    } else {
                        CovCellEditor.this.textField.setText(CovCellEditor.this.nf.format(doubleValue));
                    }
                }

                CovCellEditor.this.textField.selectAll();
            }

            /**
             * Overrides delegate; gets the text value from the cell to send
             * back to the model.
             *
             * @return this text value.
             */
            public Object getCellEditorValue() {
                return CovCellEditor.this.textField.getText();
            }
        };

        this.textField.addActionListener(this.delegate);
    }

    /**
     * Sets the text of the editor to red if red is true, black otherwise.
     *
     * @param red if true, the text is set to red; otherwise, it is set to black.
     */
    public void setRed(boolean red) {
        if (red) {
            this.textField.setForeground(Color.RED);
        } else {
            this.textField.setForeground(Color.BLACK);
        }
    }
}



