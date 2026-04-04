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

        setBackground(uiColor("Table.background", Color.WHITE));
        setForeground(uiColor("Table.foreground", Color.BLACK));
        setSelectionBackground(uiColor("Table.selectionBackground", new Color(204, 204, 255)));
        setSelectionForeground(uiColor("Table.selectionForeground", Color.BLACK));
        setGridColor(uiColor("Table.gridColor", new Color(220, 220, 220)));

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

    private static Color uiColor(String key, Color fallback) {
        Color c = UIManager.getColor(key);
        return c != null ? c : fallback;
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

    @Override
    public void updateUI() {
        super.updateUI();

        if (covCellRenderer != null) {
            repaint();
        }

        setBackground(uiColor("Table.background", Color.WHITE));
        setForeground(uiColor("Table.foreground", Color.BLACK));
        setSelectionBackground(uiColor("Table.selectionBackground", new Color(204, 204, 255)));
        setSelectionForeground(uiColor("Table.selectionForeground", Color.BLACK));
        setGridColor(uiColor("Table.gridColor", new Color(220, 220, 220)));
    }
}

class CovCellRenderer extends DefaultTableCellRenderer {
    private final NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
    private final ICovarianceMatrix covMatrix;
    private boolean positiveDefinite = true;

    public CovCellRenderer(ICovarianceMatrix covMatrix) {
        if (covMatrix == null) {
            throw new NullPointerException();
        }

        this.covMatrix = covMatrix;
        setOpaque(true);
    }

    private static Color uiColor(String key, Color fallback) {
        Color c = UIManager.getColor(key);
        return c != null ? c : fallback;
    }

    private static Color blend(Color a, Color b, double t) {
        t = Math.max(0.0, Math.min(1.0, t));
        int r = (int) Math.round((1.0 - t) * a.getRed() + t * b.getRed());
        int g = (int) Math.round((1.0 - t) * a.getGreen() + t * b.getGreen());
        int bb = (int) Math.round((1.0 - t) * a.getBlue() + t * b.getBlue());
        return new Color(r, g, bb);
    }

    private static Color getBaseBackground() {
        return uiColor("Table.background", Color.WHITE);
    }

    private static Color getBaseForeground() {
        return uiColor("Table.foreground", Color.BLACK);
    }

    private static Color getSelectionBackground() {
        Color c = UIManager.getColor("Table.selectionBackground");
        if (c != null) return c;

        return new Color(204, 204, 255);
    }

    private static Color getSelectionForeground() {
        return uiColor("Table.selectionForeground", getBaseForeground());
    }

    private static Color getProblemForeground() {
        Color c = UIManager.getColor("Component.error.focusedBorderColor");
        if (c != null) return c;

        c = UIManager.getColor("Actions.Red");
        if (c != null) return c;

        return Color.RED;
    }

    private static Color getFocusBorderColor() {
        Color c = UIManager.getColor("Component.focusColor");
        if (c != null) return c;

        c = UIManager.getColor("Table.selectionBackground");
        if (c != null) return c;

        return Color.BLACK;
    }

    private static Color getSelectedCellBackground() {
        Color base = getBaseBackground();
        Color sel = getSelectionBackground();
        return blend(base, sel, 0.35);
    }

    @Override
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

    @Override
    public Component getTableCellRendererComponent(
            JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {

        Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
        DefaultTableCellRenderer renderer = (DefaultTableCellRenderer) c;

        renderer.setBackground(getBaseBackground());
        renderer.setForeground(getBaseForeground());
        renderer.setBorder(noFocusBorder);

        if (!isPositiveDefinite() && row >= 4 && col >= 1) {
            renderer.setForeground(getProblemForeground());
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
            boolean rowSelected = this.covMatrix.isSelected((Node) variables.get(rowVar));
            boolean colSelected = this.covMatrix.isSelected((Node) variables.get(colVar));

            if (rowSelected && colSelected) {
                renderer.setBackground(getSelectedCellBackground());
                renderer.setForeground(getSelectionForeground());
            }
        }

        if (colVar == -1 && rowVar >= 0 && rowVar < numVars) {
            boolean rowSelected = this.covMatrix.isSelected((Node) variables.get(rowVar));

            if (rowSelected) {
                renderer.setBackground(getSelectedCellBackground());
                renderer.setForeground(getSelectionForeground());
            }
        }

        if (rowVar == -1 && colVar >= 0 && colVar < numVars) {
            boolean colSelected = this.covMatrix.isSelected((Node) variables.get(colVar));

            if (colSelected) {
                renderer.setBackground(getSelectedCellBackground());
                renderer.setForeground(getSelectionForeground());
            }
        }

        if (hasFocus) {
            renderer.setBorder(new LineBorder(getFocusBorderColor()));
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

    public CovCellEditor() {
        super(new JTextField());

        this.textField = (JTextField) this.editorComponent;
        this.textField.setHorizontalAlignment(SwingConstants.LEFT);
        refreshTheme(false);

        this.delegate = new EditorDelegate() {
            @Override
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

            @Override
            public Object getCellEditorValue() {
                return CovCellEditor.this.textField.getText();
            }
        };

        this.textField.addActionListener(this.delegate);
    }

    private static Color uiColor(String key, Color fallback) {
        Color c = UIManager.getColor(key);
        return c != null ? c : fallback;
    }

    private static Color getEditorBackground() {
        return uiColor("TextField.background", Color.WHITE);
    }

    private static Color getEditorForeground() {
        return uiColor("TextField.foreground", Color.BLACK);
    }

    private static Color getProblemForeground() {
        Color c = UIManager.getColor("Component.error.focusedBorderColor");
        if (c != null) return c;

        c = UIManager.getColor("Actions.Red");
        if (c != null) return c;

        return Color.RED;
    }

    private static Color getBorderColor(boolean red) {
        if (red) {
            return getProblemForeground();
        }

        Color c = UIManager.getColor("Component.borderColor");
        if (c != null) return c;

        c = UIManager.getColor("TextField.borderColor");
        if (c != null) return c;

        c = UIManager.getColor("Component.focusColor");
        if (c != null) return c;

        return Color.BLACK;
    }

    private void refreshTheme(boolean red) {
        this.textField.setBackground(getEditorBackground());
        this.textField.setForeground(red ? getProblemForeground() : getEditorForeground());
        this.textField.setCaretColor(this.textField.getForeground());
        this.textField.setBorder(new LineBorder(getBorderColor(red)));
    }

    public void setRed(boolean red) {
        refreshTheme(red);
    }
}



