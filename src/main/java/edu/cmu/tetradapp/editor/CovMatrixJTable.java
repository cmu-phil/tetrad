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
 * @author Joseph Ramsey
 */
public class CovMatrixJTable extends JTable implements DataModelContainer,
        PropertyChangeListener {
    private CovCellRenderer covCellRenderer;
    private CovCellEditor covCellEditor;

    /**
     * Construct a new JTable for the given CovarianceMatrix.
     *
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

        covCellEditor = new CovCellEditor();
        covCellRenderer = new CovCellRenderer(covMatrix);

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

    public TableCellEditor getCellEditor(int row, int col) {
        CovMatrixTable covMatrixTable = (CovMatrixTable) getModel();
        covCellEditor.setRed(false);

        if (row >= 4 && col >= 1) {
            java.util.List<String> varNames = covMatrixTable.getCovMatrix().getVariableNames();
            java.util.List<String> selectedVarNames = covMatrixTable.getCovMatrix().getSelectedVariableNames();
            ICovarianceMatrix subMatrix = covMatrixTable.getCovMatrix().getSubmatrix(selectedVarNames);

            if (selectedVarNames.contains(varNames.get(row - 4)) && selectedVarNames.contains(varNames.get(col - 1))) {
                if (!MatrixUtils.isPositiveDefinite(subMatrix.getMatrix())) {
//                    covCellEditor.setRed(!covMatrixTable.isEditingMatrixPositiveDefinite());
                    covCellEditor.setRed(true);
                }
            }
        }
        return covCellEditor;
    }

    public TableCellRenderer getCellRenderer(int row, int col) {
        CovMatrixTable covMatrixTable = (CovMatrixTable) getModel();
//        covCellRenderer.setPositiveDefinite(false);

        if (covMatrixTable.getColumnCount() <= 200) {
            java.util.List<String> varNames = covMatrixTable.getCovMatrix().getVariableNames();
            java.util.List<String> selectedVarNames = covMatrixTable.getCovMatrix().getSelectedVariableNames();
            ICovarianceMatrix subMatrix = covMatrixTable.getCovMatrix().getSubmatrix(selectedVarNames);

            covCellEditor.setRed(false);
            covCellRenderer.setPositiveDefinite(true);

            if (row >= 4 && row - 4 < varNames.size() && col >= 1 && col - 1 < varNames.size()) {
                if (selectedVarNames.contains(varNames.get(row - 4)) && selectedVarNames.contains(varNames.get(col - 1))) {
                    if (!MatrixUtils.isPositiveDefinite(subMatrix.getMatrix())) {
//                    covCellEditor.setRed(!covMatrixTable.isEditingMatrixPositiveDefinite());
                        covCellEditor.setRed(true);
                        covCellRenderer.setPositiveDefinite(false);
                    }
                }
            }
        }


//        covCellRenderer.setPositiveDefinite(
//                covMatrixTable.isEditingMatrixPositiveDefinite());
        return covCellRenderer;
    }

    public DataModel getDataModel() {
        CovMatrixTable covMatrixTable = (CovMatrixTable) getModel();
        return covMatrixTable.getCovMatrix();
    }

    public boolean isEditingMatrixPositiveDefinite() {
        CovMatrixTable covMatrixTable = (CovMatrixTable) getModel();
        return covMatrixTable.isEditingMatrixPositiveDefinite();
    }

    public void restore() {
        CovMatrixTable covMatrixTable = (CovMatrixTable) getModel();
        covMatrixTable.restore();
    }

    public void propertyChange(PropertyChangeEvent evt) {
        if ("modelChanged".equals(evt.getPropertyName())) {
            firePropertyChange("modelChanged", null, null);
        }
    }

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

class CovCellRenderer extends DefaultTableCellRenderer {
    private NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
    private boolean positiveDefinite = true;
    private ICovarianceMatrix covMatrix;
    private Color selectedColor = new Color(204, 204, 255);

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
            setText(nf.format(doubleValue));
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
            renderer.setHorizontalAlignment(JLabel.RIGHT);
        } else {
            renderer.setHorizontalAlignment(JLabel.LEFT);
        }

        java.util.List variables = covMatrix.getVariables();
        int rowVar = row - 4;
        int colVar = col - 1;
        int numVars = variables.size();

        if (colVar >= 0 && colVar < numVars && rowVar >= 0 &&
                rowVar < numVars && rowVar >= colVar) {
            boolean rowSelected =
                    covMatrix.isSelected((Node) variables.get(rowVar));
            boolean colSelected =
                    covMatrix.isSelected((Node) variables.get(colVar));

            if (rowSelected && colSelected) {
                renderer.setBackground(selectedColor);
            }
        }

        if (colVar == -1 && rowVar >= 0 && rowVar < numVars) {
            boolean rowSelected =
                    covMatrix.isSelected((Node) variables.get(rowVar));

            if (rowSelected) {
                renderer.setBackground(selectedColor);
            }
        }

        if (rowVar == -1 && colVar >= 0 && colVar < numVars) {
            boolean colSelected =
                    covMatrix.isSelected((Node) variables.get(colVar));

            if (colSelected) {
                renderer.setBackground(selectedColor);
            }
        }

        if (hasFocus) {
            renderer.setBackground(Color.WHITE);
            renderer.setBorder(new LineBorder(Color.BLACK));
        }

        return renderer;
    }

    private boolean isPositiveDefinite() {
        return positiveDefinite;
    }

    public void setPositiveDefinite(boolean positiveDefinite) {
        this.positiveDefinite = positiveDefinite;
    }

    public ICovarianceMatrix getCovMatrix() {
        return covMatrix;
    }

}

class CovCellEditor extends DefaultCellEditor {
    private NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
    private JTextField textField;

    /**
     * Constructs a new number cell editor.
     */
    public CovCellEditor() {
        super(new JTextField());

        textField = (JTextField) editorComponent;
        textField.setHorizontalAlignment(JTextField.LEFT);
        textField.setBorder(new LineBorder(Color.black));

        delegate = new EditorDelegate() {
            public void setValue(Object value) {
                if (value == null) {
                    textField.setText("");
                } else if (value instanceof String) {
                    textField.setText((String) value);
                } else if (value instanceof Integer) {
                    textField.setText(value.toString());
                } else if (value instanceof Double) {
                    double doubleValue = (Double) value;

                    if (Double.isNaN(doubleValue)) {
                        textField.setText("");
                    } else {
                        textField.setText(nf.format(doubleValue));
                    }
                }

                textField.selectAll();
            }

            /**
             * Overrides delegate; gets the text value from the cell to send
             * back to the model.
             *
             * @return this text value.
             */
            public Object getCellEditorValue() {
                return textField.getText();
            }
        };

        textField.addActionListener(delegate);
    }

    public void setRed(boolean red) {
        if (red) {
            textField.setForeground(Color.RED);
        } else {
            textField.setForeground(Color.BLACK);
        }
    }
}


