/// ////////////////////////////////////////////////////////////////////////////
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
/// ////////////////////////////////////////////////////////////////////////////

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
        if (c instanceof TabularDataJTable tabularData) {
            DataSet dataSet = tabularData.getDataSet();

            int[] rows;
            int[] cols;

            if (!tabularData.getRowSelectionAllowed() &&
                !tabularData.getColumnSelectionAllowed()) {
                return null;
            }

            // Column selection.
            if (!tabularData.getRowSelectionAllowed()) {
                int rowCount = tabularData.getDataSet().getNumRows();
                rows = new int[rowCount + 1];

                // Need to include the variable names.
                for (int i = 0; i < rowCount + 1; i++) {
                    rows[i] = i + getNumLeadingRows() - 1;
                }
            } else {
                int[] _rows = tabularData.getSelectedRows();

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
                int colCount = tabularData.getDataSet().getNumColumns();
                cols = new int[colCount];

                for (int j = 0; j < colCount; j++) {
                    cols[j] = j + getNumLeadingCols();
                }
            } else {
                cols = tabularData.getSelectedColumns();
            }

            if (cols == null || rows.length == 0 || cols.length == 0) {
                return null;
            }

            StringBuilder buf = new StringBuilder();

            for (int displayRow : rows) {
                if (displayRow == 0) {
                    continue;
                }

                for (int displayCol : cols) {
                    if (displayCol == 0) {
                        continue;
                    }

                    // Always treat null header as empty string so we don't drop the column.
                    String name = (String) tabularData.getValueAt(1, displayCol);
                    if (name == null) {
                        name = "";
                    }

                    if (displayRow == 1) {
                        String s = name;

                        if (s.trim().equals("")) {
                            s = "C" + (displayCol - 1);
                        }

                        buf.append(s).append("\t");
                    } else {
                        int dataRow = displayRow - getNumLeadingRows();
                        int dataCol = displayCol - getNumLeadingCols();

                        if (dataCol < 0) {
                            continue;
                        }

                        if (dataCol < dataSet.getNumColumns()) {
                            if (dataRow < dataSet.getNumRows()) {
                                Object datumObj = dataSet.getObject(dataRow, dataCol);
                                String datumString = "";

                                if (datumObj != null) {
                                    if (datumObj instanceof Number) {
                                        datumString = datumObj.toString();
                                    } else if (datumObj instanceof String) {
                                        // Quote all Strings.
                                        datumString = "\"" + datumObj + "\"";
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

    /**
     * {@inheritDoc}
     */
    public boolean importData(JComponent c, Transferable t) {
        if (c instanceof TabularDataJTable) {
            try {
                TabularDataJTable tabularData = (TabularDataJTable) c;
                String s = (String) t.getTransferData(DataFlavor.stringFlavor);

                int selectedRow = tabularData.getSelectedRow();
                int selectedCol = tabularData.getSelectedColumn();

                // Header/column paste if user targets the header row (1) or above (0).
                boolean headerPaste = selectedRow <= 1;

                int startRow = selectedRow;
                int startCol = selectedCol;

                if (startRow == 0) {
                    startRow = 1;
                }

                if (startCol < getNumLeadingCols()) {
                    startCol = getNumLeadingCols();
                }

                if (!checkRanges(s, startCol, tabularData)) {
                    return false;
                }

                BufferedReader preReader = new BufferedReader(
                        new CharArrayReader(s.toCharArray()));

                String preLine = preReader.readLine();
                StringTokenizer preTokenizer =
                        new StringTokenizer(preLine, "\t");
                int numTokens = preTokenizer.countTokens();

                for (int col = startCol; col < startCol + numTokens; col++) {

                    if (startRow - getNumLeadingRows() >= tabularData.getDataSet().getNumRows() ||
                        startCol - getNumLeadingCols() >= tabularData.getDataSet().getNumColumns()) {
                    }
                }

                // NEW: pass headerPaste into doPaste
                doPaste(s, startRow, startCol, false, headerPaste, tabularData);
            } catch (UnsupportedFlavorException | IOException e) {
                e.printStackTrace();
            }

            return true;
        }

        return false;
    }

    private boolean checkRanges(String s, int startCol,
                                TabularDataJTable tabularData) {
        RegexTokenizer lines = new RegexTokenizer(s, Pattern.compile("\n"), '"');
        lines.nextToken();

        while (lines.hasMoreTokens()) {
            String line = lines.nextToken();
            RegexTokenizer tokens = new RegexTokenizer(line, Pattern.compile("\t"), '"');
            int col = startCol;

            while (tokens.hasMoreTokens()) {
                String token = tokens.nextToken();

                if (!tabularData.checkValueAt(token, col)) {
                    int dataCol = col - getNumLeadingCols();

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

    private void doPaste(String s, int startRow, int startCol,
                         boolean shiftDown, boolean headerPaste,
                         TabularDataJTable tabularData) {

        // Convert to DataSet (0-based) indices.
        startRow -= getNumLeadingRows();
        startCol -= getNumLeadingCols();

        if (startRow < 0) startRow = 0;
        if (startCol < 0) startCol = 0;

        // Determine the number of rows and columns in the string s.
        int pasteRows = 0;
        int pasteCols = -1;

        RegexTokenizer lines = new RegexTokenizer(s, Pattern.compile("\n"), '"');
        lines.setQuoteSensitive(false);

        // Read the variable names (header line).
        String line = lines.nextToken();
        RegexTokenizer _names = new RegexTokenizer(line, Pattern.compile("\t"), '"');
        List<String> varNames = new ArrayList<>();

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

            RegexTokenizer numbers = new RegexTokenizer(line, Pattern.compile("\t"), '"');
            int _cols = 0;

            while (numbers.hasMoreTokens()) {
                numbers.nextToken();
                _cols++;
            }

            if (pasteCols == -1) {
                pasteCols = _cols;
            }
        }

        if (varNames.size() != pasteCols) {
            throw new IllegalArgumentException("Number of variable names must " +
                                               "match the number of columns.");
        }

        DataSet dataSet = tabularData.getDataSet();
        int originalCols = dataSet.getNumColumns();

        // === NEW: confirmation if header paste will overwrite differently named columns ===
        if (headerPaste && originalCols > 0) {
            int firstCol = startCol;
            int lastCol = startCol + pasteCols - 1;
            int overlapLastCol = Math.min(lastCol, originalCols - 1);

            boolean hasMismatch = false;

            for (int col = firstCol; col <= overlapLastCol; col++) {
                String existingName = dataSet.getVariable(col).getName();
                String newName = varNames.get(col - firstCol);
                if (existingName == null) existingName = "";
                if (newName == null) newName = "";
                if (!existingName.equals(newName)) {
                    hasMismatch = true;
                    break;
                }
            }

            if (hasMismatch) {
                Object[] options = {"Yes", "No"};
                int choice = JOptionPane.showOptionDialog(
                        JOptionUtils.centeringComp(),
                        "The new columns have different names from the old columns. Do you want to overwrite them?",
                        "Overwrite columns?",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE,
                        null,
                        options,
                        options[1]  // "No" is the default
                );

                if (choice != JOptionPane.YES_OPTION) {
                    // User chose "No" or closed the dialog: abort paste.
                    return;
                }
            }
        }

        // Resize the dataset if necessary to accommodate the new data.
        dataSet.ensureColumns(startCol + pasteCols, varNames);

        if (shiftDown) {
            dataSet.ensureRows(startRow + 2 * pasteRows);
        } else {
            dataSet.ensureRows(startRow + pasteRows);
        }

        int newCols = dataSet.getNumColumns();

        // === NEW: set variable names for all pasted columns in header-paste mode ===
        if (headerPaste) {
            for (int j = 0; j < pasteCols; j++) {
                int colIndex = startCol + j;
                if (colIndex >= newCols) break;

                String newName = varNames.get(j);
                if (newName == null) newName = "";
                newName = newName.trim();

                Node node = dataSet.getVariable(colIndex);

                if (!newName.isEmpty()) {
                    // Avoid duplicate names pointing to different variables.
                    String finalName = newName;
                    Node existing = dataSet.getVariable(finalName);
                    if (existing != null && existing != node) {
                        int i = 0;
                        String candidate;
                        do {
                            candidate = newName + "_" + (++i);
                        } while (dataSet.getVariable(candidate) != null);
                        finalName = candidate;
                    }
                    node.setName(finalName);
                }
                // If the new header is empty, you can either leave the old name
                // or assign a default like "C<index>". For now, leave old name.
            }
        } else {
            // Original behavior: only name newly created columns, do not touch existing ones.
            for (int j = originalCols; j < newCols; j++) {
                Node node = dataSet.getVariable(j);
                int index = (j - (originalCols - 1)) + ((originalCols - 1) - startCol);

                if (index < 0 || index >= varNames.size()) {
                    continue;
                }

                String name = varNames.get(index);

                if (dataSet.getVariable(name) == null) {
                    node.setName(name);
                } else {
                    int i = 0;
                    String _name;

                    do {
                        _name = name + "_" + (++i);
                    } while (dataSet.getVariable(_name) != null);

                    node.setName(_name);
                }
            }
        }

        // Copy existing data down, if requested. (unchanged)
        if (shiftDown) {
            for (int i = pasteRows - 1; i >= 0; i--) {
                for (int j = 0; j < pasteCols; j++) {
                    int oldRow = startRow + i;
                    int newRow = oldRow + pasteRows;
                    int col = startCol + j;

                    int numRows = dataSet.getNumRows();
                    if (newRow < numRows) {
                        Object value = tabularData.getValueAt(
                                oldRow + getNumLeadingRows(),
                                col + getNumLeadingCols());
                        tabularData.setValueAt(value,
                                newRow + getNumLeadingRows(),
                                col + getNumLeadingCols());
                    }
                }
            }
        }

        // Now actually paste the new values.
        lines = new RegexTokenizer(s, Pattern.compile("\n"), '"');
        lines.setQuoteSensitive(false);
        lines.nextToken(); // skip header

        for (int i = 0; i < pasteRows; i++) {
            line = lines.nextToken();
            if (line.length() == 0) continue;

            RegexTokenizer tokens = new RegexTokenizer(line, Pattern.compile("\t"), '"');

            for (int j = 0; j < pasteCols; j++) {
                int row = startRow + i;
                int col = startCol + j;
                String token = tokens.nextToken();

                tabularData.setValueAt(token, row + getNumLeadingRows(),
                        col + getNumLeadingCols());
            }
        }

        TabularDataTable tableModel = (TabularDataTable) tabularData.getModel();
        tableModel.fireTableDataChanged();
    }

    /**
     * {@inheritDoc}
     */
    public void exportDone(JComponent source, Transferable data, int action) {
        if (action == TransferHandler.MOVE && source instanceof TabularDataJTable tableTabular) {
            tableTabular.deleteSelected();
        }
    }

    private int getNumLeadingCols() {
        /*
      The number of initial "special" columns not used to display the data
      set.
     */
        return 1;
    }

    /**
     * The number of initial "special" rows not used to display the data set.
     */
    private int getNumLeadingRows() {

        return 2;
    }
}





