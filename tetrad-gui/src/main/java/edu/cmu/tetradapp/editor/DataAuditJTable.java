///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2026 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
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

import edu.cmu.tetrad.data.audit.AuditFinding;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;

/**
 * A JTable for the Data Audit dialog's tables. The leftmost columns (a configurable number) are left-aligned and the
 * rest right-aligned. When the model is a {@link DataAuditFindingsModel}, rows for WARNING findings are shown in a
 * highlight color. Selected cells can be copied, with column headers, in tab-delimited form suitable for pasting
 * into a spreadsheet.
 *
 * @author josephramsey
 */
public class DataAuditJTable extends JTable {

    /**
     * The highlight color for WARNING findings.
     */
    private static final Color WARNING_COLOR = new Color(150, 30, 30);

    /**
     * Constructor.
     *
     * @param model           the table model.
     * @param leftAlignedCols the number of leftmost columns to left-align; the rest are right-aligned.
     */
    public DataAuditJTable(AbstractTableModel model, int leftAlignedCols) {
        setModel(model);
        setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

        FontMetrics metrics = getFontMetrics(getFont());
        setRowHeight(metrics.getHeight() + 3);

        setRowSelectionAllowed(true);
        getColumnModel().setColumnSelectionAllowed(true);

        for (int i = 0; i < getColumnModel().getColumnCount(); i++) {
            getColumnModel().getColumn(i).setCellRenderer(new AuditCellRenderer(i >= leftAlignedCols));
        }

        setTransferHandler(new DataAuditTransferHandler());
    }

    /**
     * Aligns cells and highlights WARNING findings.
     */
    private static class AuditCellRenderer extends DefaultTableCellRenderer {

        public AuditCellRenderer(boolean rightAlign) {
            setHorizontalAlignment(rightAlign ? JLabel.RIGHT : JLabel.LEFT);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            if (!isSelected) {
                boolean warning = table.getModel() instanceof DataAuditFindingsModel findingsModel
                        && findingsModel.getFinding(table.convertRowIndexToModel(row)).getSeverity()
                        == AuditFinding.Severity.WARNING;
                c.setForeground(warning ? WARNING_COLOR : table.getForeground());
            }

            return c;
        }
    }

    /**
     * Copies the selected cells, with column headers, in tab-delimited form.
     */
    static class DataAuditTransferHandler extends TransferHandler {

        /**
         * {@inheritDoc}
         */
        public int getSourceActions(JComponent c) {
            return TransferHandler.COPY;
        }

        /**
         * {@inheritDoc}
         */
        protected Transferable createTransferable(JComponent c) {
            if (!(c instanceof DataAuditJTable table)) {
                return null;
            }

            int[] rows = table.getSelectedRows();
            int[] cols = table.getSelectedColumns();

            if (rows.length == 0 || cols.length == 0) {
                return null;
            }

            StringBuilder buf = new StringBuilder();

            for (int j = 0; j < cols.length; j++) {
                buf.append(table.getModel().getColumnName(cols[j]));
                if (j < cols.length - 1) buf.append("\t");
            }

            buf.append("\n");

            for (int i = 0; i < rows.length; i++) {
                for (int j = 0; j < cols.length; j++) {
                    buf.append(table.getValueAt(rows[i], cols[j]));
                    if (j < cols.length - 1) buf.append("\t");
                }

                if (i < rows.length - 1) buf.append("\n");
            }

            return new StringSelection(buf.toString());
        }
    }
}
