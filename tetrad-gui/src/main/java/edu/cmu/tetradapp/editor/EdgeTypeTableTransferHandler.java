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
import java.util.List;
import java.util.StringTokenizer;
import java.util.regex.Pattern;

/**
 * Implements basic cut and paste operations for DataDisplay.
 */
class EdgeTypeTableTransferHandler extends TransferHandler {

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
        if (c instanceof JTable tabularData) {
            tabularData.setColumnSelectionAllowed(false);
            tabularData.setRowSelectionAllowed(true);

            StringBuilder builder = new StringBuilder();

            for (int col = 1; col < tabularData.getColumnCount(); col++) {
                String columnName = tabularData.getColumnName(col);
                builder.append(columnName == null ? "" : columnName);

                if (col != tabularData.getColumnCount() - 1) {
                    builder.append("\t");
                }
            }

            for (int row = 0; row < tabularData.getRowCount(); row++) {
                if (tabularData.isRowSelected(row)) {// && row != tabularData.getRowCount() - 1) {
                    builder.append("\n");
                }

                for (int col = 1; col < tabularData.getColumnCount(); col++) {
                    if (!tabularData.isRowSelected(row)) {
                        continue;
                    }

                    Object valueAt = tabularData.getValueAt(row, col);
                    builder.append(valueAt == null ? "" : valueAt.toString());

                    if (col != tabularData.getColumnCount() - 1) {
                        builder.append("\t");
                    }
                }
            }

            return new StringSelection(builder.toString());
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

                BufferedReader preReader = new BufferedReader(
                        new CharArrayReader(s.toCharArray()));

                String preLine = preReader.readLine();
                StringTokenizer preTokenizer =
                        new StringTokenizer(preLine, "\t");
                int numTokens = preTokenizer.countTokens();

                for (int col = startCol; col < startCol + numTokens; col++) {
                    Object value = tabularData.getValueAt(startRow, col);
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
                    String[] choices = {
                            "Shift corresponding cells down to make room",
                            "Replace corresponding cells"};

                    Object choice = JOptionPane.showInputDialog(
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
                         boolean shiftDown, TabularDataJTable tabularData) {

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
//            } else if (pasteCols != _cols) {
////                throw new IllegalArgumentException("Number of tokens per row not uniform.");
//            }
        }

        if (varNames.size() != pasteCols) {
            throw new IllegalArgumentException("Number of variable names must " +
                                               "match the number of columns.");
        }

        // Resize the dataset if necessary to accomodate the new data.
        DataSet dataSet = tabularData.getDataSet();
        int originalCols = dataSet.getNumColumns();

        // Make the dataset big enough, making sure not to use the parsed
        // variable names to create new columns.
        dataSet.ensureColumns(startCol + pasteCols, varNames);

        if (shiftDown) {
            dataSet.ensureRows(startRow + 2 * pasteRows);
        } else {
            dataSet.ensureRows(startRow + pasteRows);
        }

        int newCols = dataSet.getNumColumns();

        // Use variable names from the paste where possible, without changing
        // any existing variable names. If necessary, append numbers.
        for (int j = originalCols; j < newCols; j++) {
            Node node = dataSet.getVariable(j);
            int index = (j - (originalCols - 1)) + ((originalCols - 1) - startCol);

            if (index < 0) {
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

        // Copy existing data down, if requested.
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

        lines = new RegexTokenizer(s, Pattern.compile("\n"), '"');
        lines.setQuoteSensitive(false);
        lines.nextToken();

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

    private int getNumLeadingRows() {
        /*
      The number of initial "special" rows not used to display the data
      set.
     */
        return 2;
    }
}





