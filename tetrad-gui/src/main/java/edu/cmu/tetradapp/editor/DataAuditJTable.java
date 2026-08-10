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
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;

/**
 * A JTable for the Data Audit dialog's tables. The leftmost columns (a configurable number) are left-aligned and the
 * rest right-aligned. When the model is a {@link DataAuditFindingsModel}, rows for WARNING findings are shown in a
 * highlight color. Selected cells can be copied, with column headers, in tab-delimited form suitable for pasting
 * into a spreadsheet.
 * <p>
 * Sizing: the table stretches to fill its scroll pane's viewport when its columns' preferred widths sum to less than
 * the viewport width (the slack going to the last column), and scrolls horizontally when they sum to more. Combined
 * with {@link #sizeColumnToContents(int, int)}, this lets a long text column (such as the findings table's Message
 * column) take up the remainder of the dialog's width when its content is short, and extend beyond the viewport -
 * readable by scrolling right - when its content is long, rather than being truncated at a fixed width.
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

        // OFF preserves the columns' preferred widths and classic drag semantics; the doLayout override below
        // routes viewport slack to the last column when the table is narrower than the viewport (see
        // getScrollableTracksViewportWidth), and the enclosing scroll pane provides a horizontal scrollbar when
        // it is wider.
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
     * Stretches the table to fill the viewport when narrower than it, and lets it exceed the viewport (producing a
     * horizontal scrollbar) when wider.
     *
     * @return true if the table's preferred width is less than the viewport's width.
     */
    @Override
    public boolean getScrollableTracksViewportWidth() {
        return getParent() == null || getPreferredSize().width < getParent().getWidth();
    }

    /**
     * Lays out columns at their preferred widths, giving any viewport slack to the last column. This is deliberate:
     * {@code JTable.doLayout()} otherwise distributes slack proportionally across all columns regardless of the
     * auto-resize mode (the mode governs interactive header drags only), which would widen every column instead of
     * letting the last (in the findings table, Message) column take up the remainder of the width. Interactive
     * drags are left to the superclass.
     */
    @Override
    public void doLayout() {
        boolean dragging = getTableHeader() != null && getTableHeader().getResizingColumn() != null;
        int count = getColumnModel().getColumnCount();

        if (!dragging && count > 0 && getScrollableTracksViewportWidth()) {
            int preferred = 0;

            for (int i = 0; i < count; i++) {
                preferred += getColumnModel().getColumn(i).getPreferredWidth();
            }

            int slack = getWidth() - preferred;

            if (slack >= 0) {
                for (int i = 0; i < count; i++) {
                    TableColumn column = getColumnModel().getColumn(i);
                    column.setWidth(column.getPreferredWidth() + (i == count - 1 ? slack : 0));
                }

                return;
            }
        }

        super.doLayout();
    }

    /**
     * Sets the given column's preferred width to fit its widest rendered content (header included), with the given
     * minimum. For a text column this makes the full text reachable by scrolling right rather than truncated at a
     * fixed width.
     *
     * @param col      the column index (view order).
     * @param minWidth the minimum preferred width in pixels.
     */
    public void sizeColumnToContents(int col, int minWidth) {
        int width = minWidth;

        Component header = getTableHeader().getDefaultRenderer().getTableCellRendererComponent(
                this, getColumnModel().getColumn(col).getHeaderValue(), false, false, -1, col);
        width = Math.max(width, header.getPreferredSize().width);

        for (int row = 0; row < getRowCount(); row++) {
            Component c = prepareRenderer(getCellRenderer(row, col), row, col);
            width = Math.max(width, c.getPreferredSize().width + getIntercellSpacing().width);
        }

        // A little breathing room so the last characters don't sit against the cell edge.
        getColumnModel().getColumn(col).setPreferredWidth(width + 12);
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
