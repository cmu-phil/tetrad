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

import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.bayes.DirichletBayesIm;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetrad.util.NumberFormatUtil;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

/**
 * This is the JTable which displays the getModel parameter set (an Model).
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 */
class DirichletBayesImNodeCountsTable extends JTable {
    private int focusRow;
    private int focusCol;
    private int lastX;
    private int lastY;

    /**
     * Constructs a new editing table from a given editing table model.
     */
    public DirichletBayesImNodeCountsTable(final Node node,
                                           final DirichletBayesIm dirichletBayesIm) {
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

        final ListSelectionModel rowSelectionModel = getSelectionModel();

        rowSelectionModel.addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(final ListSelectionEvent e) {
                final ListSelectionModel m = (ListSelectionModel) (e.getSource());
                setFocusRow(m.getAnchorSelectionIndex());
            }
        });

        final ListSelectionModel columnSelectionModel = getColumnModel()
                .getSelectionModel();

        columnSelectionModel.addListSelectionListener(
                new ListSelectionListener() {
                    public void valueChanged(final ListSelectionEvent e) {
                        final ListSelectionModel m =
                                (ListSelectionModel) (e.getSource());
                        setFocusColumn(m.getAnchorSelectionIndex());
                    }
                });

        addMouseListener(new MouseAdapter() {
            public void mousePressed(final MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    showPopup(e);
                }
            }
        });

        addFocusListener(new FocusAdapter() {
            public void focusGained(final FocusEvent e) {
                ((Model) getModel()).fireTableDataChanged();
            }

            public void focusLost(final FocusEvent e) {
                ((Model) getModel()).fireTableDataChanged();
            }
        });

        setFocusRow(0);
        setFocusColumn(0);
    }

    private void resetModel(final Node node, final DirichletBayesIm dirichletBayesIm) {
        final Model model = new Model(node, dirichletBayesIm, this);
        model.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(final PropertyChangeEvent evt) {
                if ("editorValueChanged".equals(evt.getPropertyName())) {
                    firePropertyChange("editorValueChanged", null, null);
                }
            }
        });
        setModel(model);
    }

    public void createDefaultColumnsFromModel() {
        super.createDefaultColumnsFromModel();

        if (getModel() instanceof Model) {
            final FontMetrics fontMetrics = getFontMetrics(getFont());
            final Model model = (Model) getModel();

            for (int i = 0; i < model.getColumnCount(); i++) {
                final TableColumn column = getColumnModel().getColumn(i);
                final String columnName = model.getColumnName(i);
                final int currentWidth = column.getPreferredWidth();

                if (columnName != null) {
                    final int minimumWidth = fontMetrics.stringWidth(columnName) + 8;

                    if (minimumWidth > currentWidth) {
                        column.setPreferredWidth(minimumWidth);
                    }
                }
            }
        }
    }

    private void showPopup(final MouseEvent e) {
        final JPopupMenu popup = new JPopupMenu();

        final JMenuItem randomizeRow = new JMenuItem("Randomize this row");
        final JMenuItem randomizeIncompleteRows =
                new JMenuItem("Randomize incomplete rows in table");
        final JMenuItem randomizeEntireTable =
                new JMenuItem("Randomize entire table");
        final JMenuItem randomizeAllTables = new JMenuItem("Randomize all tables");

        final JMenuItem clearRow = new JMenuItem("Clear this row");
        final JMenuItem clearEntireTable = new JMenuItem("Clear entire table");

        randomizeRow.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent e) {
                final int nodeIndex = getEditingTableModel().getNodeIndex();

                final DirichletBayesImNodeCountsTable editingTable =
                        DirichletBayesImNodeCountsTable.this;
                final TableCellEditor cellEditor = editingTable.getCellEditor();

                if (cellEditor != null) {
                    cellEditor.cancelCellEditing();
                }

                final Point point = new Point(getLastX(), getLastY());
                final int rowIndex = editingTable.rowAtPoint(point);

                final DirichletBayesIm dirichletBayesIm = getDirichletBayesIm();
//                requestRowTotal(dirichletBayesIm);
                dirichletBayesIm.randomizeRow(nodeIndex, rowIndex);

                getEditingTableModel().fireTableDataChanged();
            }
        });

        randomizeIncompleteRows.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent e) {
                final int nodeIndex = getEditingTableModel().getNodeIndex();
                final DirichletBayesIm dirichletBayesIm = getDirichletBayesIm();

                if (!existsIncompleteRow(dirichletBayesIm, nodeIndex)) {
                    JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                            "There are no incomplete rows in this table.");
                    return;
                }

                final DirichletBayesImNodeCountsTable editingTable =
                        DirichletBayesImNodeCountsTable.this;
                final TableCellEditor cellEditor = editingTable.getCellEditor();

                if (cellEditor != null) {
                    cellEditor.cancelCellEditing();
                }

//                requestRowTotal(dirichletBayesIm);
                dirichletBayesIm.randomizeIncompleteRows(nodeIndex);
                getEditingTableModel().fireTableDataChanged();
            }
        });

        randomizeEntireTable.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent e) {
                final int nodeIndex = getEditingTableModel().getNodeIndex();
                final DirichletBayesIm dirichletBayesIm = getDirichletBayesIm();

                if (existsCompleteRow(dirichletBayesIm, nodeIndex)) {
                    final int ret = JOptionPane.showConfirmDialog(
                            JOptionUtils.centeringComp(),
                            "This will modify all values in the table. " +
                                    "Continue?", "Warning",
                            JOptionPane.YES_NO_OPTION);

                    if (ret == JOptionPane.NO_OPTION) {
                        return;
                    }
                }

                final DirichletBayesImNodeCountsTable editingTable =
                        DirichletBayesImNodeCountsTable.this;
                final TableCellEditor cellEditor = editingTable.getCellEditor();

                if (cellEditor != null) {
                    cellEditor.cancelCellEditing();
                }

//                requestRowTotal(dirichletBayesIm);
                dirichletBayesIm.randomizeTable(nodeIndex);
                getEditingTableModel().fireTableDataChanged();
            }
        });

        randomizeAllTables.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent e) {
                final int ret = JOptionPane.showConfirmDialog(
                        JOptionUtils.centeringComp(),
                        "This will modify all values in the entire Dirichlet model! " +
                                "Continue?", "Warning",
                        JOptionPane.YES_NO_OPTION);

                if (ret == JOptionPane.NO_OPTION) {
                    return;
                }

                final DirichletBayesIm dirichletBayesIm = getDirichletBayesIm();
//                requestRowTotal(dirichletBayesIm);

                for (int nodeIndex = 0;
                     nodeIndex < dirichletBayesIm.getNumNodes(); nodeIndex++) {

                    final DirichletBayesImNodeCountsTable editingTable =
                            DirichletBayesImNodeCountsTable.this;
                    final TableCellEditor cellEditor = editingTable.getCellEditor();

                    if (cellEditor != null) {
                        cellEditor.cancelCellEditing();
                    }

                    dirichletBayesIm.randomizeTable(nodeIndex);
                    getEditingTableModel().fireTableDataChanged();
                }
            }
        });

        clearRow.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent e) {
                final int nodeIndex = getEditingTableModel().getNodeIndex();

                final DirichletBayesImNodeCountsTable editingTable =
                        DirichletBayesImNodeCountsTable.this;
                final TableCellEditor cellEditor = editingTable.getCellEditor();

                if (cellEditor != null) {
                    cellEditor.cancelCellEditing();
                }

                final Point point = new Point(getLastX(), getLastY());
                final int rowIndex = editingTable.rowAtPoint(point);

                final DirichletBayesIm dirichletBayesIm = getDirichletBayesIm();
                dirichletBayesIm.clearRow(nodeIndex, rowIndex);

                getEditingTableModel().fireTableRowsUpdated(rowIndex, rowIndex);
            }
        });

        clearEntireTable.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent e) {
                final int nodeIndex = getEditingTableModel().getNodeIndex();
                final DirichletBayesIm dirichletBayesIm = getDirichletBayesIm();

                if (existsCompleteRow(dirichletBayesIm, nodeIndex)) {
                    final int ret = JOptionPane.showConfirmDialog(
                            JOptionUtils.centeringComp(),
                            "This will delete all values in the table. " +
                                    "Continue?", "Warning",
                            JOptionPane.YES_NO_OPTION);

                    if (ret == JOptionPane.NO_OPTION) {
                        return;
                    }
                }

                final DirichletBayesImNodeCountsTable editingTable =
                        DirichletBayesImNodeCountsTable.this;
                final TableCellEditor cellEditor = editingTable.getCellEditor();

                if (cellEditor != null) {
                    cellEditor.cancelCellEditing();
                }

                dirichletBayesIm.clearTable(nodeIndex);

                getEditingTableModel().fireTableDataChanged();
            }
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

//    private void requestRowTotal(DirichletBayesIm dirichletBayesIm) {
//        double rowTotal = dirichletBayesIm.getNextRowTotal();
//
//        RowTotalEditor editor = new RowTotalEditor(rowTotal);
//        JOptionPane.showMessageDialog(JOptionUtils.centeringComp(), editor);
//
//        rowTotal = editor.getRowTotal();
//        dirichletBayesIm.setNextRowTotal(rowTotal);
//    }

    private boolean existsCompleteRow(final DirichletBayesIm dirichletBayesIm,
                                      final int nodeIndex) {
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

    private boolean existsIncompleteRow(final DirichletBayesIm dirichletBayesIm,
                                        final int nodeIndex) {
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

    public void setModel(final TableModel model) {
        super.setModel(model);
    }

    /**
     * Sets the focus row to the anchor row currently being selected.
     */
    private void setFocusRow(int row) {
        final Model editingTableModel = (Model) getModel();
        final int failedRow = editingTableModel.getFailedRow();

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
        final Model editingTableModel = (Model) getModel();
        final int failedCol = editingTableModel.getFailedCol();

        if (failedCol != -1) {
            col = failedCol;
            editingTableModel.resetFailedCol();
        }

        if (col < getNumParents()) {
            col = getNumParents();
        }

        this.focusCol = col < getNumParents() ? getNumParents() : col;

        if (this.focusCol >= getNumParents() &&
                this.focusCol < getColumnCount()) {
            setColumnSelectionInterval(this.focusCol, this.focusCol);
            editCellAt(this.focusRow, this.focusCol);
        }
    }

    private int getNumParents() {
        final Model editingTableModel = (Model) getModel();
        final DirichletBayesIm dirichletBayesIm =
                editingTableModel.getDirichletBayesIm();
        final int nodeIndex = editingTableModel.getNodeIndex();
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
     * The abstract table model containing the parameters to be edited for a
     * given node.  Parameters for a given node N with parents P1, P2, ..., are
     * of the form P(N=v0 | P1=v1, P2=v2, ..., Pn = vn).  The first n columns of
     * this table for each row contains a combination of values for (P1, P2, ...
     * Pn), such as (v0, v1, ..., vn).  If there are m values for N, the next m
     * columns contain numbers in the range [0.0, 1.0] representing conditional
     * probabilities that N takes on that corresponding value given this
     * combination of parent values.  These conditional probabilities may be
     * edited.  As they are being edited for a given row, the only condition is
     * that they be greater than or equal to 0.0.
     *
     * @author Joseph Ramsey jdramsey@andrew.cmu.edu
     */
    static final class Model extends AbstractTableModel {

        /**
         * The BayesIm being edited.
         */
        private final DirichletBayesIm dirichletBayesIm;

        /**
         * This table can only display conditional probabilities for one node at
         * at time. This is the node.
         */
        private final int nodeIndex;

        /**
         * The messageAnchor that takes the user through the process of editing
         * the probability tables.
         */
        private final JComponent messageAnchor;

        private int failedRow = -1;
        private int failedCol = -1;
        private PropertyChangeSupport pcs;

        /**
         * Constructs a new editing table model for a given a node in a given
         * dirichletBayesIm.
         */
        public Model(final Node node, final DirichletBayesIm dirichletBayesIm,
                     final JComponent messageAnchor) {
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
            this.messageAnchor = messageAnchor;
        }

        /**
         * @return the name of the given column.
         */
        public String getColumnName(final int col) {
            final Node node = getDirichletBayesIm().getNode(getNodeIndex());
            final int numParents =
                    getDirichletBayesIm().getNumParents(getNodeIndex());
            final int numColumns =
                    getDirichletBayesIm().getNumColumns(getNodeIndex());
            final int totalsColumn = numParents + numColumns;

            if (col < numParents) {
                final int parent =
                        getDirichletBayesIm().getParent(getNodeIndex(), col);
                return getDirichletBayesIm().getNode(parent).getName();
            } else if (col < numParents + numColumns) {
                final int valIndex = col - numParents;
                final String value = getDirichletBayesIm().getBayesPm().getCategory(
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
         * @return the total number of columns in the table, which is equal to
         * the number of parents for the node plus the number of values for the
         * node.
         */
        public int getColumnCount() {
            final int numParents =
                    getDirichletBayesIm().getNumParents(getNodeIndex());
            final int numColumns =
                    getDirichletBayesIm().getNumColumns(getNodeIndex());

            // Add an extra column to display row totals.
            return numParents + numColumns + 1;
        }

        /**
         * @return the value of the table at the given row and column. The
         * type of value returned depends on the column.  If there are n
         * parent values and m node values, then the first n columns have String
         * values representing the values of the parent nodes for a particular
         * combination (row) and the next m columns have Double values
         * representing conditional probabilities of node values given parent
         * value combinations.
         */
        public Object getValueAt(final int tableRow, final int tableCol) {
            final int numParents =
                    getDirichletBayesIm().getNumParents(getNodeIndex());
            final int numColumns =
                    getDirichletBayesIm().getNumColumns(getNodeIndex());
            final int totalsColumn = numParents + numColumns;
            final int[] parentVals = getDirichletBayesIm().getParentValues(
                    getNodeIndex(), tableRow);

            if (tableCol < numParents) {
                final Node columnNode =
                        getDirichletBayesIm().getNode(getDirichletBayesIm()
                                .getParent(getNodeIndex(), tableCol));
                final BayesPm bayesPm = getDirichletBayesIm().getBayesPm();
                return bayesPm.getCategory(columnNode, parentVals[tableCol]);
            } else if (tableCol < numParents + numColumns) {
                final int colIndex = tableCol - numParents;
                final double value = getDirichletBayesIm().getPseudocount(
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
         * Determines whether a cell is in the column range to allow for
         * editing.
         */
        public boolean isCellEditable(final int row, final int col) {
            final int numParents =
                    getDirichletBayesIm().getNumParents(getNodeIndex());
            final int numColumns =
                    getDirichletBayesIm().getNumColumns(getNodeIndex());

            return !(col < numParents) && col < numParents + numColumns + 1;
        }

        /**
         * Sets the value of the cell at (row, col) to 'aValue'.
         */
        public void setValueAt(final Object aValue, final int row, final int col) {
            final int numParents =
                    getDirichletBayesIm().getNumParents(getNodeIndex());
            final int numColumns =
                    getDirichletBayesIm().getNumColumns(getNodeIndex());

            if (col == numParents + numColumns) {
                setTotal(row, aValue);
            } else {
                setPseudocount(row, col, aValue);
            }
        }

        public int findColumn(final String columnName) {
            return super.findColumn(columnName);
        }

        private void setTotal(final int row, final Object aValue) {
            if ("".equals(aValue) || aValue == null) {
                return;
            }

            try {
                final double total = Double.parseDouble((String) aValue);

                if (total < 0.0) {
                    JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                            "Total must be nonnegative.");
                    this.failedRow = row;
                } else {
                    final double currentTotal = getDirichletBayesIm()
                            .getRowPseudocount(getNodeIndex(), row);
                    final double ratio = total / currentTotal;

                    for (int i = 0; i < getDirichletBayesIm().getNumColumns(
                            getNodeIndex()); i++) {
                        final double count = getDirichletBayesIm().getPseudocount(
                                getNodeIndex(), row, i);
                        getDirichletBayesIm().setPseudocount(getNodeIndex(),
                                row, i, count * ratio);
                    }

                    fireTableRowsUpdated(row, row);
                    getPcs().firePropertyChange("editorValueChanged", null,
                            null);
                }
            } catch (final NumberFormatException e) {
                JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                        "Could not interpret '" + aValue + "'");
                this.failedRow = row;
            }
        }

        private void setPseudocount(final int row, final int col, final Object aValue) {
            final int numParents =
                    getDirichletBayesIm().getNumParents(getNodeIndex());
            final int colIndex = col - numParents;

            if ("".equals(aValue) || aValue == null) {
                return;
            }

            try {
                final double pseudocount = Double.parseDouble((String) aValue);

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
            } catch (final NumberFormatException e) {
                JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                        "Could not interpret '" + aValue + "'");
                this.failedRow = row;
                this.failedCol = col;
            }
        }

        public void addPropertyChangeListener(final PropertyChangeListener l) {
            getPcs().addPropertyChangeListener(l);
        }

        private PropertyChangeSupport getPcs() {
            if (this.pcs == null) {
                this.pcs = new PropertyChangeSupport(this);
            }
            return this.pcs;
        }

        private boolean countIsNegative(final double count) {
            return count < 0.0;
        }

        /**
         * @return the class of the column.
         */
        public Class getColumnClass(final int col) {
            final boolean isParent =
                    col < getDirichletBayesIm().getNumParents(getNodeIndex());
            return isParent ? Object.class : Number.class;
        }

        public DirichletBayesIm getDirichletBayesIm() {
            return this.dirichletBayesIm;
        }

        public int getNodeIndex() {
            return this.nodeIndex;
        }

        public JComponent getMessageAnchor() {
            return this.messageAnchor;
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






