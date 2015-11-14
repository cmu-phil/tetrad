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

import edu.cmu.tetrad.bayes.BayesIm;
import edu.cmu.tetrad.bayes.BayesPm;
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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.text.NumberFormat;

/**
 * This is the JTable which displays the model parameter set.
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 */
class BayesImNodeEditingTable extends JTable {
    private int focusRow = 0;
    private int focusCol = 0;
    private int lastX;
    private int lastY;

    /**
     * Constructs a new editing table from a given editing table model.
     */
    public BayesImNodeEditingTable(Node node, BayesIm bayesIm) {
        if (node == null) {
            return;
        }

        if (bayesIm == null) {
            throw new NullPointerException();
        }

        if (bayesIm.getNodeIndex(node) < 0) {
            throw new IllegalArgumentException("Node " + node +
                    " is not a node" + " for BayesIm " + bayesIm + ".");
        }

        Model model = new Model(node, bayesIm, this);
        model.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                if ("modelChanged".equals(evt.getPropertyName())) {
                    firePropertyChange("modelChanged", null, null);
                }
            }
        });
        setModel(model);

        setDefaultEditor(Number.class, new NumberCellEditor());
        setDefaultRenderer(Number.class, new NumberCellRenderer());
        getTableHeader().setReorderingAllowed(false);
        getTableHeader().setResizingAllowed(true);
        setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        setCellSelectionEnabled(true);

        ListSelectionModel rowSelectionModel = getSelectionModel();

        rowSelectionModel.addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) {
                ListSelectionModel m = (ListSelectionModel) (e.getSource());
                setFocusRow(m.getAnchorSelectionIndex());
            }
        });

        ListSelectionModel columnSelectionModel = getColumnModel()
                .getSelectionModel();

        columnSelectionModel.addListSelectionListener(
                new ListSelectionListener() {
                    public void valueChanged(ListSelectionEvent e) {
                        ListSelectionModel m =
                                (ListSelectionModel) (e.getSource());
                        setFocusColumn(m.getAnchorSelectionIndex());
                    }
                });

        addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    showPopup(e);
                }
            }
        });

        setFocusRow(0);
        setFocusColumn(0);
    }

    public void createDefaultColumnsFromModel() {
        super.createDefaultColumnsFromModel();

        if (getModel() instanceof Model) {
            FontMetrics fontMetrics = getFontMetrics(getFont());
            Model model = (Model) getModel();

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

        randomizeRow.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                int nodeIndex = getEditingTableModel().getNodeIndex();

                BayesImNodeEditingTable editingTable =
                        BayesImNodeEditingTable.this;
                TableCellEditor cellEditor = editingTable.getCellEditor();

                if (cellEditor != null) {
                    cellEditor.cancelCellEditing();
                }

                Point point = new Point(getLastX(), getLastY());
                int rowIndex = editingTable.rowAtPoint(point);

                BayesIm bayesIm = getBayesIm();
                bayesIm.randomizeRow(nodeIndex, rowIndex);

                getEditingTableModel().fireTableDataChanged();
                firePropertyChange("modelChanged", null, null);
            }
        });

        randomizeIncompleteRows.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                int nodeIndex = getEditingTableModel().getNodeIndex();
                BayesIm bayesIm = getBayesIm();

                if (!existsIncompleteRow(bayesIm, nodeIndex)) {
                    JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                            "There are no incomplete rows in this table.");
                    return;
                }

                BayesImNodeEditingTable editingTable =
                        BayesImNodeEditingTable.this;
                TableCellEditor cellEditor = editingTable.getCellEditor();

                if (cellEditor != null) {
                    cellEditor.cancelCellEditing();
                }

                bayesIm.randomizeIncompleteRows(nodeIndex);
                getEditingTableModel().fireTableDataChanged();
                firePropertyChange("modelChanged", null, null);
            }
        });

        randomizeEntireTable.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                int nodeIndex = getEditingTableModel().getNodeIndex();
                BayesIm bayesIm = getBayesIm();

                if (existsCompleteRow(bayesIm, nodeIndex)) {
                    int ret = JOptionPane.showConfirmDialog(
                            JOptionUtils.centeringComp(),
                            "This will modify all values in the table. " +
                                    "Continue?", "Warning",
                            JOptionPane.YES_NO_OPTION);

                    if (ret == JOptionPane.NO_OPTION) {
                        return;
                    }
                }

                BayesImNodeEditingTable editingTable =
                        BayesImNodeEditingTable.this;
                TableCellEditor cellEditor = editingTable.getCellEditor();

                if (cellEditor != null) {
                    cellEditor.cancelCellEditing();
                }

                bayesIm.randomizeTable(nodeIndex);

                getEditingTableModel().fireTableDataChanged();
                firePropertyChange("modelChanged", null, null);
            }
        });

        randomizeAllTables.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                int ret = JOptionPane.showConfirmDialog(
                        JOptionUtils.centeringComp(),
                        "This will modify all values in the entire Bayes model! " +
                                "Continue?", "Warning",
                        JOptionPane.YES_NO_OPTION);

                if (ret == JOptionPane.NO_OPTION) {
                    return;
                }

                BayesIm bayesIm = getBayesIm();

                for (int nodeIndex = 0;
                        nodeIndex < getBayesIm().getNumNodes(); nodeIndex++) {

                    BayesImNodeEditingTable editingTable =
                            BayesImNodeEditingTable.this;
                    TableCellEditor cellEditor = editingTable.getCellEditor();

                    if (cellEditor != null) {
                        cellEditor.cancelCellEditing();
                    }

                    bayesIm.randomizeTable(nodeIndex);

                    getEditingTableModel().fireTableDataChanged();
                    firePropertyChange("modelChanged", null, null);
                }
            }
        });

        clearRow.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                int nodeIndex = getEditingTableModel().getNodeIndex();

                BayesImNodeEditingTable editingTable =
                        BayesImNodeEditingTable.this;
                TableCellEditor cellEditor = editingTable.getCellEditor();

                if (cellEditor != null) {
                    cellEditor.cancelCellEditing();
                }

                Point point = new Point(getLastX(), getLastY());
                int rowIndex = editingTable.rowAtPoint(point);

                BayesIm bayesIm = getBayesIm();
                bayesIm.clearRow(nodeIndex, rowIndex);

                getEditingTableModel().fireTableRowsUpdated(rowIndex, rowIndex);
                firePropertyChange("modelChanged", null, null);
            }
        });

        clearEntireTable.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                int nodeIndex = getEditingTableModel().getNodeIndex();
                BayesIm bayesIm = getBayesIm();

                if (existsCompleteRow(bayesIm, nodeIndex)) {
                    int ret = JOptionPane.showConfirmDialog(
                            JOptionUtils.centeringComp(),
                            "This will delete all values in the table. " +
                                    "Continue?", "Warning",
                            JOptionPane.YES_NO_OPTION);

                    if (ret == JOptionPane.NO_OPTION) {
                        return;
                    }
                }

                BayesImNodeEditingTable editingTable =
                        BayesImNodeEditingTable.this;
                TableCellEditor cellEditor = editingTable.getCellEditor();

                if (cellEditor != null) {
                    cellEditor.cancelCellEditing();
                }

                bayesIm.clearTable(nodeIndex);

                getEditingTableModel().fireTableDataChanged();
                firePropertyChange("modelChanged", null, null);
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

    private boolean existsCompleteRow(BayesIm bayesIm, int nodeIndex) {
        boolean existsCompleteRow = false;

        for (int rowIndex = 0;
                rowIndex < bayesIm.getNumRows(nodeIndex); rowIndex++) {
            if (!bayesIm.isIncomplete(nodeIndex, rowIndex)) {
                existsCompleteRow = true;
                break;
            }
        }
        return existsCompleteRow;
    }

    private boolean existsIncompleteRow(BayesIm bayesIm, int nodeIndex) {
        boolean existsCompleteRow = false;

        for (int rowIndex = 0;
                rowIndex < bayesIm.getNumRows(nodeIndex); rowIndex++) {
            if (bayesIm.isIncomplete(nodeIndex, rowIndex)) {
                existsCompleteRow = true;
                break;
            }
        }
        return existsCompleteRow;
    }

    public void setModel(TableModel model) {
        super.setModel(model);
    }

    /**
     * Sets the focus row to the anchor row currently being selected.
     */
    private void setFocusRow(int row) {
        if (row == -1) {
            return;
        }

        Model editingTableModel = (Model) getModel();
        int failedRow = editingTableModel.getFailedRow();

        if (failedRow != -1) {
            row = failedRow;
            editingTableModel.resetFailedRow();
        }

        this.focusRow = row;

        if (this.focusCol < getRowCount()) {
            setRowSelectionInterval(focusRow, focusRow);
            editCellAt(focusRow, focusCol);
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

        this.focusCol = col < getNumParents() ? getNumParents() : col;

        if (this.focusCol >= getNumParents() &&
                this.focusCol < getColumnCount()) {
            setColumnSelectionInterval(focusCol, focusCol);
            editCellAt(focusRow, focusCol);
        }
    }

    private int getNumParents() {
        Model editingTableModel = (Model) getModel();
        BayesIm bayesIm = editingTableModel.getBayesIm();
        int nodeIndex = editingTableModel.getNodeIndex();
        return bayesIm.getNumParents(nodeIndex);
    }

    private Model getEditingTableModel() {
        return (Model) getModel();
    }

    private BayesIm getBayesIm() {
        return getEditingTableModel().getBayesIm();
    }

    private int getLastX() {
        return lastX;
    }

    private int getLastY() {
        return lastY;
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
        private BayesIm bayesIm;

        /**
         * This table can only display conditional probabilities for one node at
         * at time. This is the node.
         */
        private int nodeIndex;

        /**
         * The messageAnchor that takes the user through the process of editing
         * the probability tables.
         */
        private JComponent messageAnchor;

        private int failedRow = -1;
        private int failedCol = -1;
        private PropertyChangeSupport pcs;

        /**
         * Constructs a new editing table model for a given a node in a given
         * bayesIm.
         */
        public Model(Node node, BayesIm bayesIm, JComponent messageAnchor) {
            if (node == null) {
                throw new NullPointerException("Node must not be null.");
            }

            if (bayesIm == null) {
                throw new NullPointerException("Bayes IM must not be null.");
            }

            if (messageAnchor == null) {
                throw new NullPointerException(
                        "Message anchor must not be null.");
            }

            this.bayesIm = bayesIm;
            this.nodeIndex = bayesIm.getNodeIndex(node);
            this.messageAnchor = messageAnchor;
        }

        /**
         * @return the name of the given column.
         */
        public String getColumnName(int col) {
            Node node = getBayesIm().getNode(getNodeIndex());

            if (col < getBayesIm().getNumParents(getNodeIndex())) {
                int parent = getBayesIm().getParent(getNodeIndex(), col);
                return getBayesIm().getNode(parent).getName();
            }
            else {
                int numNodeVals = getBayesIm().getNumColumns(getNodeIndex());
                int valIndex = col - getBayesIm().getNumParents(getNodeIndex());

                if (valIndex < numNodeVals) {
                    String value = getBayesIm().getBayesPm().getCategory(node,
                            valIndex);
                    return node.getName() + "=" + value;
                }

                return null;
            }
        }

        /**
         * @return the number of rows in the table.
         */
        public int getRowCount() {
            return getBayesIm().getNumRows(getNodeIndex());
        }

        /**
         * @return the total number of columns in the table, which is equal to
         * the number of parents for the node plus the number of values for the
         * node.
         */
        public int getColumnCount() {
            int numParents = getBayesIm().getNumParents(getNodeIndex());
            int numColumns = getBayesIm().getNumColumns(getNodeIndex());
            return numParents + numColumns;
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
        public Object getValueAt(int tableRow, int tableCol) {
            int[] parentVals =
                    getBayesIm().getParentValues(getNodeIndex(), tableRow);

            if (tableCol < parentVals.length) {
                Node columnNode = getBayesIm().getNode(
                        getBayesIm().getParent(getNodeIndex(), tableCol));
                BayesPm bayesPm = getBayesIm().getBayesPm();
                return bayesPm.getCategory(columnNode, parentVals[tableCol]);
            }
            else {
                int colIndex = tableCol - parentVals.length;

                if (colIndex < getBayesIm().getNumColumns(getNodeIndex())) {
                    return getBayesIm().getProbability(getNodeIndex(), tableRow,
                            colIndex);
                }

                return "null";
            }
        }

        /**
         * Determines whether a cell is in the column range to allow for
         * editing.
         */
        public boolean isCellEditable(int row, int col) {
            return !(col < getBayesIm().getNumParents(getNodeIndex()));
        }

        /**
         * Sets the value of the cell at (row, col) to 'aValue'.
         */
        public void setValueAt(Object aValue, int row, int col) {
            int numParents = getBayesIm().getNumParents(getNodeIndex());
            int colIndex = col - numParents;

            if ("".equals(aValue) || aValue == null) {
                getBayesIm().setProbability(getNodeIndex(), row, colIndex,
                        Double.NaN);
                fireTableRowsUpdated(row, row);
                getPcs().firePropertyChange("modelChanged", null, null);
                return;
            }

            try {
                NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

                double probability = Double.parseDouble((String) aValue);
                double sumInRow = sumInRow(row, colIndex) + probability;

                double oldProbability = getBayesIm().getProbability(nodeIndex, row, colIndex);

                if (!Double.isNaN(oldProbability)) {
                    oldProbability = Double.parseDouble(nf.format(oldProbability));
                }

                if (probability == oldProbability) {
                    return;
                }

                if (probabilityOutOfRange(probability)) {
                    JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                            "Probabilities must be in range [0.0, 1.0].");
                    failedRow = row;
                    failedCol = col;
                }
                else if (numNanCols(row) == 0) {
                    if (sumInRow < 0.99995 || sumInRow > 1.00005) {
                        emptyRow(row);
                        getBayesIm().setProbability(getNodeIndex(), row,
                                colIndex, probability);
                        if (bayesIm.getNumColumns((nodeIndex)) == 2) {
                            fillInSingleRemainingColumn(row);
                        }
                        fireTableRowsUpdated(row, row);
                        getPcs().firePropertyChange("modelChanged", null,
                                null);
                    }
                }
                else if (sumInRow > 1.00005) {
                    JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                            "Sum of probabilities in row must not exceed 1.0.");
                    failedRow = row;
                    failedCol = col;
                }
                else {
                    getBayesIm().setProbability(getNodeIndex(), row, colIndex,
                            probability);
                    fillInSingleRemainingColumn(row);
                    fillInZerosIfSumIsOne(row);
                    fireTableRowsUpdated(row, row);
                    getPcs().firePropertyChange("modelChanged", null,
                            null);
                }
            }
            catch (NumberFormatException e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                        "Could not interpret '" + aValue + "'");
                failedRow = row;
                failedCol = col;
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

        private void fillInSingleRemainingColumn(int rowIndex) {
            int leftOverColumn = uniqueNanCol(rowIndex);

            if (leftOverColumn != -1) {
                double difference = 1.0 - sumInRow(rowIndex, leftOverColumn);
                getBayesIm().setProbability(getNodeIndex(), rowIndex,
                        leftOverColumn, difference);
            }
        }

        private void fillInZerosIfSumIsOne(int rowIndex) {
            double sum = sumInRow(rowIndex, -1);

            if (sum > 0.9995 && sum < 1.0005) {
                int numColumns = getBayesIm().getNumColumns(getNodeIndex());

                for (int i = 0; i < numColumns; i++) {
                    double probability = getBayesIm().getProbability(
                            getNodeIndex(), rowIndex, i);

                    if (Double.isNaN(probability)) {
                        getBayesIm().setProbability(getNodeIndex(), rowIndex, i,
                                0.0);
                    }
                }
            }
        }

        private boolean probabilityOutOfRange(double value) {
            return value < 0.0 || value > 1.0;
        }

        private int uniqueNanCol(int rowIndex) {
            int numNanCols = 0;
            int lastNanCol = -1;

            for (int i = 0; i < getBayesIm().getNumColumns(getNodeIndex()); i++)
            {
                double probability = getBayesIm().getProbability(getNodeIndex(),
                        rowIndex, i);
                if (Double.isNaN(probability)) {
                    numNanCols++;
                    lastNanCol = i;
                }
            }

            return numNanCols == 1 ? lastNanCol : -1;
        }

        private int numNanCols(int rowIndex) {
            int numNanCols = 0;

            for (int i = 0; i < getBayesIm().getNumColumns(getNodeIndex()); i++)
            {
                double probability = getBayesIm().getProbability(getNodeIndex(),
                        rowIndex, i);
                if (Double.isNaN(probability)) {
                    numNanCols++;
                }
            }

            return numNanCols;
        }

        private void emptyRow(int rowIndex) {
            for (int i = 0; i < getBayesIm().getNumColumns(getNodeIndex()); i++)
            {
                getBayesIm().setProbability(getNodeIndex(), rowIndex, i,
                        Double.NaN);
            }
        }

        private double sumInRow(int rowIndex, int skipCol) {
            double sum = 0.0;

            for (int i = 0; i < getBayesIm().getNumColumns(getNodeIndex()); i++) {
                double probability = getBayesIm().getProbability(getNodeIndex(),
                        rowIndex, i);

                if (i != skipCol && !Double.isNaN(probability)) {

                    NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
                    probability = Double.parseDouble(nf.format(probability));

                    sum += probability;
                }
            }

            return sum;
        }

        /**
         * @return the class of the column.
         */
        public Class getColumnClass(int col) {
            boolean isParent = col < getBayesIm().getNumParents(getNodeIndex());
            return isParent ? Object.class : Number.class;
        }

        public BayesIm getBayesIm() {
            return bayesIm;
        }

        public int getNodeIndex() {
            return nodeIndex;
        }

        public JComponent getMessageAnchor() {
            return messageAnchor;
        }

        public int getFailedRow() {
            return failedRow;
        }

        public int getFailedCol() {
            return failedCol;
        }

        public void resetFailedRow() {
            failedRow = -1;
        }

        public void resetFailedCol() {
            failedCol = -1;
        }
    }
}






