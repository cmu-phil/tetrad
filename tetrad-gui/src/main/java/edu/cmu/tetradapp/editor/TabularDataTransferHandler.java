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

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.RegexTokenizer;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.JOptionUtils;

import javax.swing.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.BufferedReader;
import java.io.CharArrayReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringTokenizer;
import java.util.regex.Pattern;

/**
 * Implements basic cut and paste operations for DataDisplay.
 */
class TabularDataTransferHandler extends TransferHandler {

    public int getSourceActions(final JComponent c) {
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
    protected Transferable createTransferable(final JComponent c) {
        if (c instanceof TabularDataJTable) {
            final TabularDataJTable tabularData = (TabularDataJTable) c;
            final DataSet dataSet = tabularData.getDataSet();

            final int[] rows;
            final int[] cols;

            if (!tabularData.getRowSelectionAllowed() &&
                    !tabularData.getColumnSelectionAllowed()) {
                return null;
            }

            // Column selection.
            if (!tabularData.getRowSelectionAllowed()) {
                final int rowCount = tabularData.getDataSet().getNumRows();
                rows = new int[rowCount + 1];

                // Need to include the variable names.
                for (int i = 0; i < rowCount + 1; i++) {
                    rows[i] = i + getNumLeadingRows() - 1;
                }
            } else {
                final int[] _rows = tabularData.getSelectedRows();

                if (Arrays.binarySearch(_rows, 1) == -1) {
                    rows = new int[_rows.length + 1];
                    rows[0] = 1;
                    System.arraycopy(_rows, 0, rows, 1, _rows.length);
                } else {
                    rows = _rows;
                }
            }

            // Row selection.
            if (!tabularData.getColumnSelectionAllowed()) {
                final int colCount = tabularData.getDataSet().getNumColumns();
                cols = new int[colCount];

                for (int j = 0; j < colCount; j++) {
                    cols[j] = j + getNumLeadingCols();
                }
            } else {
                cols = tabularData.getSelectedColumns();
            }

            if (rows == null || cols == null || rows.length == 0 ||
                    cols.length == 0) {
                return null;
            }

            final StringBuilder buf = new StringBuilder();

            for (final int displayRow : rows) {
                if (displayRow == 0) {
                    continue;
                }

                for (final int displayCol : cols) {
                    if (displayCol == 0) {
                        continue;
                    }

                    final String name = (String) (tabularData.getValueAt(1, displayCol));

                    if (name == null) {
                        continue;
                    }

                    if (displayRow == 1) {
                        String s = (String) tabularData.getValueAt(1, displayCol);

                        if (s.trim().equals("")) {
                            s = "C" + (displayCol - 1);
                        }

                        String val = "";

                        if (s != null) {
                            val = s;
                        }

                        buf.append(val).append("\t");
                    } else {
                        final int dataRow = displayRow - getNumLeadingRows();
                        final int dataCol = displayCol - getNumLeadingCols();

                        if (dataCol < 0) {
                            continue;
                        }

                        if (dataCol < dataSet.getNumColumns()) {
                            if (dataRow < dataSet.getNumRows()) {
                                final Object datumObj = dataSet.getObject(dataRow, dataCol);
                                String datumString = "";

                                if (datumObj != null) {
                                    if (datumObj instanceof Number) {
                                        datumString = datumObj.toString();
                                    } else if (datumObj instanceof String) {

                                        // Let's quote all Strings...
                                        datumString = "\"" + datumObj + "\"";

//                                        // This is done to prevent integer discrete
//                                        // columns from being reinterpreted,
//                                        // on paste, as continuous columns.
//                                        try {
//                                            Double.parseDouble((String) datumObj);
//                                            datumString = "\"" + datumObj.toString() + "\"";
//                                        }
//                                        catch (NumberFormatException e) {
//                                            datumString = datumObj.toString();
//                                        }
                                    } else {
                                        throw new IllegalArgumentException();
                                    }
                                }

                                buf.append(datumString).append("\t");
                            } else {
                                buf.append("\t");
                            }
                        }
                    }
                }

                // we want a newline at the end of each line and not a tab
                if (buf.length() - 1 > 0) {
                    buf.deleteCharAt(buf.length() - 1).append("\n");
                }
            }

            // remove the last newline
            if (buf.length() - 1 > 0) {
                buf.deleteCharAt(buf.length() - 1);
            }

            return new StringSelection(buf.toString());
        }

        return null;
    }

    public boolean importData(final JComponent c, final Transferable t) {
        if (c instanceof TabularDataJTable) {
            try {
                final TabularDataJTable tabularData = (TabularDataJTable) c;
                final String s = (String) t.getTransferData(DataFlavor.stringFlavor);

                int startRow = tabularData.getSelectedRow();
                int startCol = tabularData.getSelectedColumn();

                if (startRow == 0) {
                    startRow = 1;
                }

                if (startCol < getNumLeadingCols()) {
                    startCol = getNumLeadingCols();
                }

                if (!checkRanges(s, startCol, tabularData)) {
                    return false;
                }

                boolean shouldAsk = false;
                boolean shiftDown = true;

                final BufferedReader preReader = new BufferedReader(
                        new CharArrayReader(s.toCharArray()));

                final String preLine = preReader.readLine();
                final StringTokenizer preTokenizer =
                        new StringTokenizer(preLine, "\t");
                final int numTokens = preTokenizer.countTokens();

                for (int col = startCol; col < startCol + numTokens; col++) {
                    final Object value = tabularData.getValueAt(startRow, col);
                    if (!"".equals(value) && !(null == value)) {
                        shouldAsk = true;
                    }

                    if (startRow - getNumLeadingRows() >= tabularData.getDataSet().getNumRows() ||
                            startCol - getNumLeadingCols() >= tabularData.getDataSet().getNumColumns()) {
                        shouldAsk = false;
                        shiftDown = false;
                    }
                }

                if (shouldAsk) {
                    final String[] choices = {
                            "Shift corresponding cells down to make room",
                            "Replace corresponding cells"};

                    final Object choice = JOptionPane.showInputDialog(
                            JOptionUtils.centeringComp(),
                            "How should the clipboard contents be pasted?",
                            "Paste Contents", JOptionPane.INFORMATION_MESSAGE,
                            null, choices, choices[0]);

                    // Null means the user cancelled the input.
                    if (choice == null) {
                        return false;
                    }

                    shiftDown = choice.equals(choices[0]);
                }

                doPaste(s, startRow, startCol, shiftDown, tabularData);
            } catch (final UnsupportedFlavorException e) {
                e.printStackTrace();
            } catch (final IOException e) {
                e.printStackTrace();
            }

            return true;
        }

        return false;
    }

    private boolean checkRanges(final String s, final int startCol,
                                final TabularDataJTable tabularData) {
        final RegexTokenizer lines = new RegexTokenizer(s, Pattern.compile("\n"), '"');
        lines.nextToken();

        while (lines.hasMoreTokens()) {
            final String line = lines.nextToken();
            final RegexTokenizer tokens = new RegexTokenizer(line, Pattern.compile("\t"), '"');
            int col = startCol;

            while (tokens.hasMoreTokens()) {
                final String token = tokens.nextToken();

                if (!tabularData.checkValueAt(token, col)) {
                    final int dataCol = col - getNumLeadingCols();

                    JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                            "<html>" +
                                    "This paste cannot be completed, since the variable in " +
                                    "<br>column " + dataCol +
                                    " cannot accept the value '" + token +
                                    "'." + "</html>");
                    return false;
                }

                col++;
            }
        }

        return true;
    }

    private void doPaste(final String s, int startRow, int startCol,
                         final boolean shiftDown, final TabularDataJTable tabularData) {

        startRow -= getNumLeadingRows();
        startCol -= getNumLeadingCols();

        if (startRow < 0) startRow = 0;
        if (startCol < 0) startCol = 0;

        // Determine the number of rows and columns in the string s.
        int pasteRows = 0;
        int pasteCols = -1;

        RegexTokenizer lines = new RegexTokenizer(s, Pattern.compile("\n"), '"');
        lines.setQuoteSensitive(false);

        // Read the variable names.
        String line = lines.nextToken();
        final RegexTokenizer _names = new RegexTokenizer(line, Pattern.compile("\t"), '"');
        final List<String> varNames = new ArrayList<>();

        while (_names.hasMoreTokens()) {
            varNames.add(_names.nextToken());
        }

        System.out.println("varnames = " + varNames);

        // Scan the rest of the data to determine dimensions.
        while (lines.hasMoreTokens()) {
            line = lines.nextToken();

            if (line.length() == 0) continue;

            System.out.println("line = " + line);
            pasteRows++;

            final RegexTokenizer numbers = new RegexTokenizer(line, Pattern.compile("\t"), '"');
            int _cols = 0;

            while (numbers.hasMoreTokens()) {
                numbers.nextToken();
                _cols++;
            }

            if (pasteCols == -1) {
                pasteCols = _cols;
            }
//            } else if (pasteCols != _cols) {
////                throw new IllegalArgumentException("Number of tokens per row not uniform.");
//            }
        }

        if (varNames.size() != pasteCols) {
            throw new IllegalArgumentException("Number of variable names must " +
                    "match the number of columns.");
        }

        // Resize the dataset if necessary to accomodate the new data.
        final DataSet dataSet = tabularData.getDataSet();
        final int originalCols = dataSet.getNumColumns();

        // Make the dataset big enough, making sure not to use the parsed
        // variable names to create new columns.
        dataSet.ensureColumns(startCol + pasteCols, varNames);

        if (shiftDown) {
            dataSet.ensureRows(startRow + 2 * pasteRows);
        } else {
            dataSet.ensureRows(startRow + pasteRows);
        }

        final int newCols = dataSet.getNumColumns();

        // Use variable names from the paste where possible, without changing
        // any existing variable names. If necessary, append numbers.
        for (int j = originalCols; j < newCols; j++) {
            final Node node = dataSet.getVariable(j);
            final int index = (j - (originalCols - 1)) + ((originalCols - 1) - startCol);

            if (index < 0) {
                continue;
            }

            final String name = varNames.get(index);

            if (dataSet.getVariable(name) == null) {
                node.setName(name);
            } else {
                int i = 0;
                String _name;

                while (true) {
                    _name = name + "_" + (++i);
                    if (dataSet.getVariable(_name) == null) break;
                }

                node.setName(_name);
            }
        }

        // Copy existing data down, if requested.
        if (shiftDown) {
            for (int i = pasteRows - 1; i >= 0; i--) {
                for (int j = 0; j < pasteCols; j++) {
                    final int oldRow = startRow + i;
                    final int newRow = oldRow + pasteRows;
                    final int col = startCol + j;

                    final int numRows = dataSet.getNumRows();
                    if (newRow < numRows) {
                        final Object value = tabularData.getValueAt(
                                oldRow + getNumLeadingRows(),
                                col + getNumLeadingCols());
                        tabularData.setValueAt(value,
                                newRow + getNumLeadingRows(),
                                col + getNumLeadingCols());
                    }
                }
            }
        }

        lines = new RegexTokenizer(s, Pattern.compile("\n"), '"');
        lines.setQuoteSensitive(false);
        lines.nextToken();

        for (int i = 0; i < pasteRows; i++) {
            line = lines.nextToken();
            if (line.length() == 0) continue;

            final RegexTokenizer tokens = new RegexTokenizer(line, Pattern.compile("\t"), '"');

            for (int j = 0; j < pasteCols; j++) {
                final int row = startRow + i;
                final int col = startCol + j;
                final String token = tokens.nextToken();

                tabularData.setValueAt(token, row + getNumLeadingRows(),
                        col + getNumLeadingCols());
            }
        }

        final TabularDataTable tableModel = (TabularDataTable) tabularData.getModel();
        tableModel.fireTableDataChanged();
    }

    public void exportDone(final JComponent source, final Transferable data, final int action) {
        if (action == TransferHandler.MOVE && source instanceof TabularDataJTable) {
            final TabularDataJTable tableTabular = (TabularDataJTable) source;
            tableTabular.deleteSelected();
        }
    }

    private int getNumLeadingCols() {
        /*
      The number of initial "special" columns not used to display the data
      set.
     */
        final int numLeadingCols = 1;
        return numLeadingCols;
    }

    private int getNumLeadingRows() {
        /*
      The number of initial "special" rows not used to display the data
      set.
     */
        final int numLeadingRows = 2;
        return numLeadingRows;
    }
}




