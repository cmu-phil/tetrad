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

import javax.swing.*;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;

/**
 * Implements basic cut-and-paste operations for DataDisplay.
 */
class DescriptiveStatisticsTransferHandler extends TransferHandler {

    public int getSourceActions(JComponent c) {
        return TransferHandler.COPY_OR_MOVE;
    }

    /**
     * Create a Transferable to use as the source for a data transfer.
     *
     * @param c The component holding the data to be transfered.  This argument
     *          is provided to enable sharing of TransferHandlers by multiple
     *          components.
     * @return The representation of the data to be transfered.
     */
    protected Transferable createTransferable(JComponent c) {
        if (c instanceof DescriptiveStatisticsJTable) {
            DescriptiveStatisticsJTable tabularData = (DescriptiveStatisticsJTable) c;

            StringBuilder buf = new StringBuilder();
            final int[] selectedRows = tabularData.getSelectedRows();
            final int[] selectedColumns = tabularData.getSelectedColumns();


//            JTableHeader header = tabularData.getTableHeader();
//            header.

            buf.append("\t");

            for (int j = 0; j < selectedColumns.length - 1; j++) {
                buf.append(tabularData.getModel().getColumnName(selectedColumns[j])).append("\t");
            }

            buf.append(tabularData.getModel().getColumnName(selectedColumns[selectedColumns.length - 1]));
            buf.append("\n");

            for (int i = 0; i < selectedRows.length; i++) {
                buf.append(tabularData.getValueAt(selectedRows[i], 0)).append("\t");

                for (int j = 0; j < selectedColumns.length - 1; j++) {
                    buf.append(tabularData.getValueAt(selectedRows[i], selectedColumns[j])).append("\t");
                }

                buf.append(tabularData.getValueAt(selectedRows[i], selectedColumns[selectedColumns.length - 1]));
                buf.append("\n");
            }

            return new StringSelection(buf.toString());
        }

        return null;
    }
}



