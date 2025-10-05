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

import javax.swing.*;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;

/**
 * Implements basic cut-and-paste operations for DataDisplay.
 */
class DescriptiveStatisticsTransferHandler extends TransferHandler {

    /**
     * {@inheritDoc}
     */
    public int getSourceActions(JComponent c) {
        return TransferHandler.COPY_OR_MOVE;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Create a Transferable to use as the source for a data transfer.
     */
    protected Transferable createTransferable(JComponent c) {
        if (c instanceof DescriptiveStatisticsJTable tabularData) {

            StringBuilder buf = new StringBuilder();
            final int[] selectedRows = tabularData.getSelectedRows();
            final int[] selectedColumns = tabularData.getSelectedColumns();

            buf.append("\t");

            for (int j = 0; j < selectedColumns.length - 1; j++) {
                buf.append(tabularData.getModel().getColumnName(selectedColumns[j])).append("\t");
            }

            buf.append(tabularData.getModel().getColumnName(selectedColumns[selectedColumns.length - 1]));
            buf.append("\n");

            for (int i = 0; i < selectedRows.length; i++) {
                int selectedRow = selectedRows[i];
                buf.append(tabularData.getValueAt(selectedRow, 0)).append("\t");

                for (int j = 0; j < selectedColumns.length - 1; j++) {
                    buf.append(tabularData.getValueAt(selectedRow, selectedColumns[j])).append("\t");
                }

                buf.append(tabularData.getValueAt(selectedRow, selectedColumns[selectedColumns.length - 1]));

                if (i < selectedRows.length - 1) {
                    buf.append("\n");
                }
            }

            return new StringSelection(buf.toString());
        }

        return null;
    }
}




