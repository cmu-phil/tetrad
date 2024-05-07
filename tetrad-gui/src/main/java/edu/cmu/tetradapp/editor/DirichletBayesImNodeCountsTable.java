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

import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.bayes.DirichletBayesIm;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetrad.util.NumberFormatUtil;
import org.apache.commons.math3.util.FastMath;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

/**
 * This is the JTable which displays the getModel parameter set (an Model).
 *
 * @author josephramsey
 */
class DirichletBayesImNodeCountsTable extends JTable {
    private int focusRow;
    private int focusCol;
    private int lastX;
    private int lastY;

    /**
     * Constructs a new editing table from a given editing table model.
     *
     * @param node             a {@link edu.cmu.tetrad.graph.Node} object
     * @param dirichletBayesIm a {@link edu.cmu.tetrad.bayes.DirichletBayesIm} object
     */
    public DirichletBayesImNodeCountsTable(Node node,
                                           DirichletBayesIm dirichletBayesIm) {
        if (node == null) {
            throw new NullPointerException();
        }

        if (dirichletBayesIm == null) {
            throw new NullPointerException();
        }

        if (dirichletBayesIm.getNodeIndex(node) < 0) {
            throw new IllegalArgumentException("Node " + node +
                                               " is not a node" + " in this DirichletBayesIm.");
        }

        resetModel(node, dirichletBayesIm);

        setDefaultEditor(Number.class,
                new NumberCellEditor(NumberFormatUtil.getInstance().getNumberFormat()));
        setDefaultRenderer(Number.class,
                new NumberCellRenderer(NumberFormatUtil.getInstance().getNumberFormat()));
        getTableHeader().setReorderingAllowed(false);
        getTableHeader().setResizingAllowed(true);
        setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        setCellSelectionEnabled(true);

        ListSelectionModel rowSelectionModel = getSelectionModel();

        rowSelectionModel.addListSelectionListener(e -> {
            ListSelectionModel m = (ListSelectionModel) (e.getSource());
            setFocusRow(m.getAnchorSelectionIndex());
        });

        ListSelectionModel columnSelectionModel = getColumnModel()
                .getSelectionModel();

        columnSelectionModel.addListSelectionListener(
                e -> {
                    ListSelectionModel m =
                            (ListSelectionModel) (e.getSource());
                    setFocusColumn(m.getAnchorSelectionIndex());
                });

        addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e) || e.isControlDown()) {
                    showPopup(e);
                }
            }
        });

        addFocusListener(new FocusAdapter() {
            public void focusGained(FocusEvent e) {
                ((Model) getModel()).fireTableDataChanged();
            }

            public void focusLost(FocusEvent e) {
                ((Model) getModel()).fireTableDataChanged();
            }
        });

        setFocusRow(0);
        setFocusColumn(0);
    }

    private void resetModel(Node node, DirichletBayesIm dirichletBayesIm) {
        Model model = new Model(node, dirichletBayesIm, this);
        model.addPropertyChangeListener(evt -> {
            if ("editorValueChanged".equals(evt.getPropertyName())) {
                firePropertyChange("editorValueChanged", null, null);
            }
        });
        setModel(model);
    }

    /**
     * <p>createDefaultColumnsFromModel.</p>
     */
    public void createDefaultColumnsFromModel() {
        super.createDefaultColumnsFromModel();

        if (getModel() instanceof Model model) {
            FontMetrics fontMetrics = getFontMetrics(getFont());

            for (int i = 0; i < model.getColumnCount(); i++) {
                TableColumn column = getColumnModel().getColumn(i);
                String columnName = model.getColumnName(i);
                int currentWidth = column.getPreferredWidth();

                if (columnName != null) {
                    int minimumWidth = fontMetrics.stringWidth(columnName) + 8;

                    if (minimumWidth > currentWidth) {
                        column.setPreferredWidth(minimumWidth);
                    }
                }
            }
        }
    }

    private void showPopup(MouseEvent e) {
        JPopupMenu popup = new JPopupMenu();

        JMenuItem randomizeRow = new JMenuItem("Randomize this row");
        JMenuItem randomizeIncompleteRows =
                new JMenuItem("Randomize incomplete rows in table");
        JMenuItem randomizeEntireTable =
                new JMenuItem("Randomize entire table");
        JMenuItem randomizeAllTables = new JMenuItem("Randomize all tables");

        JMenuItem clearRow = new JMenuItem("Clear this row");
        JMenuItem clearEntireTable = new JMenuItem("Clear entire table");

        randomizeRow.addActionListener(e12 -> {
            int nodeIndex = getEditingTableModel().getNodeIndex();

            DirichletBayesImNodeCountsTable editingTable =
                    DirichletBayesImNodeCountsTable.this;
            TableCellEditor cellEditor = editingTable.getCellEditor();

            if (cellEditor != null) {
                cellEditor.cancelCellEditing();
            }

            Point point = new Point(getLastX(), getLastY());
            int rowIndex = editingTable.rowAtPoint(point);

            DirichletBayesIm dirichletBayesIm = getDirichletBayesIm();
//                requestRowTotal(dirichletBayesIm);
            dirichletBayesIm.randomizeRow(nodeIndex, rowIndex);

            getEditingTableModel().fireTableDataChanged();
        });

        randomizeIncompleteRows.addActionListener(e1 -> {
            int nodeIndex = getEditingTableModel().getNodeIndex();
            DirichletBayesIm dirichletBayesIm = getDirichletBayesIm();

            if (!existsIncompleteRow(dirichletBayesIm, nodeIndex)) {
                JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                        "There are no incomplete rows in this table.");
                return;
            }

            DirichletBayesImNodeCountsTable editingTable =
                    DirichletBayesImNodeCountsTable.this;
            TableCellEditor cellEditor = editingTable.getCellEditor();

            if (cellEditor != null) {
                cellEditor.cancelCellEditing();
            }

//                requestRowTotal(dirichletBayesIm);
            dirichletBayesIm.randomizeIncompleteRows(nodeIndex);
            getEditingTableModel().fireTableDataChanged();
        });

        randomizeEntireTable.addActionListener(e13 -> {
            int nodeIndex = getEditingTableModel().getNodeIndex();
            DirichletBayesIm dirichletBayesIm = getDirichletBayesIm();

            if (existsCompleteRow(dirichletBayesIm, nodeIndex)) {
                int ret = JOptionPane.showConfirmDialog(
                        JOptionUtils.centeringComp(),
                        "This will modify all values in the table. " +
                        "Continue?", "Warning",
                        JOptionPane.YES_NO_OPTION);

                if (ret == JOptionPane.NO_OPTION) {
                    return;
                }
            }

            DirichletBayesImNodeCountsTable editingTable =
                    DirichletBayesImNodeCountsTable.this;
            TableCellEditor cellEditor = editingTable.getCellEditor();

            if (cellEditor != null) {
                cellEditor.cancelCellEditing();
            }

//                requestRowTotal(dirichletBayesIm);
            dirichletBayesIm.randomizeTable(nodeIndex);
            getEditingTableModel().fireTableDataChanged();
        });

        randomizeAllTables.addActionListener(e14 -> {
            int ret = JOptionPane.showConfirmDialog(
                    JOptionUtils.centeringComp(),
                    "This will modify all values in the entire Dirichlet model! " +
                    "Continue?", "Warning",
                    JOptionPane.YES_NO_OPTION);

            if (ret == JOptionPane.NO_OPTION) {
                return;
            }

            DirichletBayesIm dirichletBayesIm = getDirichletBayesIm();
//                requestRowTotal(dirichletBayesIm);

            for (int nodeIndex = 0;
                 nodeIndex < dirichletBayesIm.getNumNodes(); nodeIndex++) {

                DirichletBayesImNodeCountsTable editingTable =
                        DirichletBayesImNodeCountsTable.this;
                TableCellEditor cellEditor = editingTable.getCellEditor();

                if (cellEditor != null) {
                    cellEditor.cancelCellEditing();
                }

                dirichletBayesIm.randomizeTable(nodeIndex);
                getEditingTableModel().fireTableDataChanged();
            }
        });

        clearRow.addActionListener(e15 -> {
            int nodeIndex = getEditingTableModel().getNodeIndex();

            DirichletBayesImNodeCountsTable editingTable =
                    DirichletBayesImNodeCountsTable.this;
            TableCellEditor cellEditor = editingTable.getCellEditor();

            if (cellEditor != null) {
                cellEditor.cancelCellEditing();
            }

            Point point = new Point(getLastX(), getLastY());
            int rowIndex = editingTable.rowAtPoint(point);

            DirichletBayesIm dirichletBayesIm = getDirichletBayesIm();
            dirichletBayesIm.clearRow(nodeIndex, rowIndex);

            getEditingTableModel().fireTableRowsUpdated(rowIndex, rowIndex);
        });

        clearEntireTable.addActionListener(e16 -> {
            int nodeIndex = getEditingTableModel().getNodeIndex();
            DirichletBayesIm dirichletBayesIm = getDirichletBayesIm();

            if (existsCompleteRow(dirichletBayesIm, nodeIndex)) {
                int ret = JOptionPane.showConfirmDialog(
                        JOptionUtils.centeringComp(),
                        "This will delete all values in the table. " +
                        "Continue?", "Warning",
                        JOptionPane.YES_NO_OPTION);

                if (ret == JOptionPane.NO_OPTION) {
                    return;
                }
            }

            DirichletBayesImNodeCountsTable editingTable =
                    DirichletBayesImNodeCountsTable.this;
            TableCellEditor cellEditor = editingTable.getCellEditor();

            if (cellEditor != null) {
                cellEditor.cancelCellEditing();
            }

            dirichletBayesIm.clearTable(nodeIndex);

            getEditingTableModel().fireTableDataChanged();
        });

        popup.add(randomizeRow);
        popup.add(randomizeIncompleteRows);
        popup.add(randomizeEntireTable);
        popup.add(randomizeAllTables);
        popup.addSeparator();
        popup.add(clearRow);
        popup.add(clearEntireTable);

        this.lastX = e.getX();
        this.lastY = e.getY();

        popup.show((Component) e.getSource(), e.getX(), e.getY());
    }

    private boolean existsCompleteRow(DirichletBayesIm dirichletBayesIm,
                                      int nodeIndex) {
        boolean existsCompleteRow = false;

        for (int rowIndex = 0;
             rowIndex < dirichletBayesIm.getNumRows(nodeIndex); rowIndex++) {
            if (!dirichletBayesIm.isIncomplete(nodeIndex, rowIndex)) {
                existsCompleteRow = true;
                break;
            }
        }
        return existsCompleteRow;
    }

    private boolean existsIncompleteRow(DirichletBayesIm dirichletBayesIm,
                                        int nodeIndex) {
        boolean existsCompleteRow = false;

        for (int rowIndex = 0;
             rowIndex < dirichletBayesIm.getNumRows(nodeIndex); rowIndex++) {
            if (dirichletBayesIm.isIncomplete(nodeIndex, rowIndex)) {
                existsCompleteRow = true;
                break;
            }
        }
        return existsCompleteRow;
    }

    /**
     * {@inheritDoc}
     */
    public void setModel(@NotNull TableModel model) {
        super.setModel(model);
    }

    /**
     * Sets the focus row to the anchor row currently being selected.
     */
    private void setFocusRow(int row) {
        Model editingTableModel = (Model) getModel();
        int failedRow = editingTableModel.getFailedRow();

        if (failedRow != -1) {
            row = failedRow;
            editingTableModel.resetFailedRow();
        }

        this.focusRow = row;

        if (this.focusCol < getRowCount()) {
            setRowSelectionInterval(this.focusRow, this.focusRow);
            editCellAt(this.focusRow, this.focusCol);
        }
    }

    /**
     * Sets the focus column to the anchor column currently being selected.
     */
    private void setFocusColumn(int col) {
        Model editingTableModel = (Model) getModel();
        int failedCol = editingTableModel.getFailedCol();

        if (failedCol != -1) {
            col = failedCol;
            editingTableModel.resetFailedCol();
        }

        if (col < getNumParents()) {
            col = getNumParents();
        }

        this.focusCol = FastMath.max(col, getNumParents());

        if (this.focusCol >= getNumParents() &&
            this.focusCol < getColumnCount()) {
            setColumnSelectionInterval(this.focusCol, this.focusCol);
            editCellAt(this.focusRow, this.focusCol);
        }
    }

    private int getNumParents() {
        Model editingTableModel = (Model) getModel();
        DirichletBayesIm dirichletBayesIm =
                editingTableModel.getDirichletBayesIm();
        int nodeIndex = editingTableModel.getNodeIndex();
        return dirichletBayesIm.getNumParents(nodeIndex);
    }

    private Model getEditingTableModel() {
        return (Model) getModel();
    }

    private DirichletBayesIm getDirichletBayesIm() {
        return getEditingTableModel().getDirichletBayesIm();
    }

    private int getLastX() {
        return this.lastX;
    }

    private int getLastY() {
        return this.lastY;
    }

    /**
     * The abstract table model containing the parameters to be edited for a given node.  Parameters for a given node N
     * with parents P1, P2, ..., are of the form P(N=v0 | P1=v1, P2=v2, ..., Pn = vn).  The first n columns of this
     * table for each row contains a combination of values for (P1, P2, ... Pn), such as (v0, v1, ..., vn).  If there
     * are m values for N, the next m columns contain numbers in the range [0.0, 1.0] representing conditional
     * probabilities that N takes on that corresponding value given this combination of parent values.  These
     * conditional probabilities may be edited.  As they are being edited for a given row, the only condition is that
     * they be greater than or equal to 0.0.
     *
     * @author josephramsey
     */
    static final class Model extends AbstractTableModel {

        /**
         * The BayesIm being edited.
         */
        private final DirichletBayesIm dirichletBayesIm;

        /**
         * This table can only display conditional probabilities for one node at at time. This is the node.
         */
        private final int nodeIndex;

        private int failedRow = -1;
        private int failedCol = -1;
        private PropertyChangeSupport pcs;

        /**
         * Constructs a new editing table model for a given a node in a given dirichletBayesIm.
         */
        public Model(Node node, DirichletBayesIm dirichletBayesIm,
                     JComponent messageAnchor) {
            if (node == null) {
                throw new NullPointerException("Node must not be null.");
            }

            if (dirichletBayesIm == null) {
                throw new NullPointerException("Bayes IM must not be null.");
            }

            if (messageAnchor == null) {
                throw new NullPointerException(
                        "Message anchor must not be null.");
            }

            this.dirichletBayesIm = dirichletBayesIm;
            this.nodeIndex = dirichletBayesIm.getNodeIndex(node);
        }

        /**
         * @return the name of the given column.
         */
        public String getColumnName(int col) {
            Node node = getDirichletBayesIm().getNode(getNodeIndex());
            int numParents =
                    getDirichletBayesIm().getNumParents(getNodeIndex());
            int numColumns =
                    getDirichletBayesIm().getNumColumns(getNodeIndex());
            int totalsColumn = numParents + numColumns;

            if (col < numParents) {
                int parent =
                        getDirichletBayesIm().getParent(getNodeIndex(), col);
                return getDirichletBayesIm().getNode(parent).getName();
            } else if (col < numParents + numColumns) {
                int valIndex = col - numParents;
                String value = getDirichletBayesIm().getBayesPm().getCategory(
                        node, valIndex);
                return node.getName() + "=" + value;
            } else if (col == totalsColumn) {
                return "TOTAL COUNT";
            } else {
                return null;
            }
        }

        /**
         * @return the number of rows in the table.
         */
        public int getRowCount() {
            return getDirichletBayesIm().getNumRows(getNodeIndex());
        }

        /**
         * @return the total number of columns in the table, which is equal to the number of parents for the node plus
         * the number of values for the node.
         */
        public int getColumnCount() {
            int numParents =
                    getDirichletBayesIm().getNumParents(getNodeIndex());
            int numColumns =
                    getDirichletBayesIm().getNumColumns(getNodeIndex());

            // Add an extra column to display row totals.
            return numParents + numColumns + 1;
        }

        /**
         * @return the value of the table at the given row and column. The type of value returned depends on the column.
         * If there are n parent values and m node values, then the first n columns have String values representing the
         * values of the parent nodes for a particular combination (row) and the next m columns have Double values
         * representing conditional probabilities of node values given parent value combinations.
         */
        public Object getValueAt(int tableRow, int tableCol) {
            int numParents =
                    getDirichletBayesIm().getNumParents(getNodeIndex());
            int numColumns =
                    getDirichletBayesIm().getNumColumns(getNodeIndex());
            int totalsColumn = numParents + numColumns;
            int[] parentVals = getDirichletBayesIm().getParentValues(
                    getNodeIndex(), tableRow);

            if (tableCol < numParents) {
                Node columnNode =
                        getDirichletBayesIm().getNode(getDirichletBayesIm()
                                .getParent(getNodeIndex(), tableCol));
                BayesPm bayesPm = getDirichletBayesIm().getBayesPm();
                return bayesPm.getCategory(columnNode, parentVals[tableCol]);
            } else if (tableCol < numParents + numColumns) {
                int colIndex = tableCol - numParents;
                double value = getDirichletBayesIm().getPseudocount(
                        getNodeIndex(), tableRow, colIndex);

                if (value == -1) {
                    return null;
                } else {
                    return value;
                }
            } else if (tableCol == totalsColumn) {
                return getDirichletBayesIm().getRowPseudocount(getNodeIndex(),
                        tableRow);
            } else {
                return null;
            }
        }

        /**
         * Determines whether a cell is in the column range to allow for editing.
         */
        public boolean isCellEditable(int row, int col) {
            int numParents =
                    getDirichletBayesIm().getNumParents(getNodeIndex());
            int numColumns =
                    getDirichletBayesIm().getNumColumns(getNodeIndex());

            return !(col < numParents) && col < numParents + numColumns + 1;
        }

        /**
         * Sets the value of the cell at (row, col) to 'aValue'.
         */
        public void setValueAt(Object aValue, int row, int col) {
            int numParents =
                    getDirichletBayesIm().getNumParents(getNodeIndex());
            int numColumns =
                    getDirichletBayesIm().getNumColumns(getNodeIndex());

            if (col == numParents + numColumns) {
                setTotal(row, aValue);
            } else {
                setPseudocount(row, col, aValue);
            }
        }

        public int findColumn(String columnName) {
            return super.findColumn(columnName);
        }

        private void setTotal(int row, Object aValue) {
            if ("".equals(aValue) || aValue == null) {
                return;
            }

            try {
                double total = Double.parseDouble((String) aValue);

                if (total < 0.0) {
                    JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                            "Total must be nonnegative.");
                    this.failedRow = row;
                } else {
                    double currentTotal = getDirichletBayesIm()
                            .getRowPseudocount(getNodeIndex(), row);
                    double ratio = total / currentTotal;

                    for (int i = 0; i < getDirichletBayesIm().getNumColumns(
                            getNodeIndex()); i++) {
                        double count = getDirichletBayesIm().getPseudocount(
                                getNodeIndex(), row, i);
                        getDirichletBayesIm().setPseudocount(getNodeIndex(),
                                row, i, count * ratio);
                    }

                    fireTableRowsUpdated(row, row);
                    getPcs().firePropertyChange("editorValueChanged", null,
                            null);
                }
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                        "Could not interpret '" + aValue + "'");
                this.failedRow = row;
            }
        }

        private void setPseudocount(int row, int col, Object aValue) {
            int numParents =
                    getDirichletBayesIm().getNumParents(getNodeIndex());
            int colIndex = col - numParents;

            if ("".equals(aValue) || aValue == null) {
                return;
            }

            try {
                double pseudocount = Double.parseDouble((String) aValue);

                if (countIsNegative(pseudocount)) {
                    JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                            "Counts must be nonnegative.");
                    this.failedRow = row;
                    this.failedCol = col;
                } else {
                    getDirichletBayesIm().setPseudocount(getNodeIndex(), row,
                            colIndex, pseudocount);
                    fireTableRowsUpdated(row, row);
                    getPcs().firePropertyChange("editorValueChanged", null,
                            null);
                }
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                        "Could not interpret '" + aValue + "'");
                this.failedRow = row;
                this.failedCol = col;
            }
        }

        public void addPropertyChangeListener(PropertyChangeListener l) {
            getPcs().addPropertyChangeListener(l);
        }

        private PropertyChangeSupport getPcs() {
            if (this.pcs == null) {
                this.pcs = new PropertyChangeSupport(this);
            }
            return this.pcs;
        }

        private boolean countIsNegative(double count) {
            return count < 0.0;
        }

        /**
         * @return the class of the column.
         */
        public Class getColumnClass(int col) {
            boolean isParent =
                    col < getDirichletBayesIm().getNumParents(getNodeIndex());
            return isParent ? Object.class : Number.class;
        }

        public DirichletBayesIm getDirichletBayesIm() {
            return this.dirichletBayesIm;
        }

        public int getNodeIndex() {
            return this.nodeIndex;
        }

        public int getFailedRow() {
            return this.failedRow;
        }

        public int getFailedCol() {
            return this.failedCol;
        }

        public void resetFailedRow() {
            this.failedRow = -1;
        }

        public void resetFailedCol() {
            this.failedCol = -1;
        }
    }
}






