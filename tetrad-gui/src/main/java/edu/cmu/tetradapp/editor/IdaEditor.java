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

import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.OrderedPair;
import edu.cmu.tetrad.search.IdaCheck;
import edu.cmu.tetradapp.model.IdaModel;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A editor for the results of the IDA check. This editor can be sorted by clicking on the column headers, up or down.
 * The table can be copied and pasted into a text file or into Excel.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class IdaEditor extends JPanel {

    public IdaEditor(IdaModel idaModel) {
        IdaCheck idaCheckEst = idaModel.getIdaCheckEst();
        IdaCheck idaCheckTrue = idaModel.getIdaCheckTrue();

        // Grab the legal ordered pairs (i.e. all possible pairs of distinct nodes)
        List<OrderedPair<Node>> pairs = idaCheckEst.getOrderedPairs();

        // Create a table idaCheckEst for the results of the IDA check
        IdaTableModel tableModel = new IdaTableModel(pairs, idaCheckEst, idaCheckTrue);
        this.setLayout(new BorderLayout());

        // Add the table to the left
        JTable table = new JTable(tableModel);
        table.setAutoCreateRowSorter(true);

        this.add(new JScrollPane(table));

        // Create a TableRowSorter and set it to the JTable
        TableRowSorter<IdaTableModel> sorter = new TableRowSorter<>(tableModel);
        table.setRowSorter(sorter);

        // Create the text field
        JLabel label = new JLabel("Select:");
        JTextField filterText = new JTextField(15);
        filterText.setMaximumSize(new Dimension(500, 20));
        label.setLabelFor(filterText);

        // Create a listener for the text field that will update the table's row sort
        filterText.getDocument().addDocumentListener(new DocumentListener() {

            private void filter() {
                String text = filterText.getText();
                if (text.trim().isEmpty()) {
                    sorter.setRowFilter(null);
                } else {
                    // process comma and space separated input
                    String[] textParts = text.split("[,\\s]+");
                    List<RowFilter<Object, Object>> filters = new ArrayList<>(textParts.length);
                    for (String part : textParts) {
                        filters.add(RowFilter.regexFilter("(?i)" + Pattern.quote(part.trim())));
                    }
                    sorter.setRowFilter(RowFilter.orFilter(filters));
                }
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                filter();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                filter();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                // this method won't be called for plain text fields
            }
        });

        // Add your label and text field to a panel
        Box horiz = Box.createHorizontalBox();
        Box vert = Box.createVerticalBox();

        Box horiz2 = Box.createHorizontalBox();
        horiz2.add(label);
        horiz2.add(filterText);

        vert.add(horiz2);
        vert.add(new JScrollPane(table));

        horiz.add(vert);

        JPanel rightPanel = new JPanel();
        horiz.add(rightPanel);

        // Then you can add your panel to the top of your frame
        add(horiz, BorderLayout.CENTER);

        if (idaCheckTrue == null) {
            setPreferredSize(new Dimension(400, 600));
        } else {
            setPreferredSize(new Dimension(600, 600));
        }
    }


    /**
     * A table model for the results of the IDA check. This table can be sorted by clicking on the column headers,
     * up or down. The table can be copied and pasted into a text file or into Excel.
     */
    private static class IdaTableModel extends AbstractTableModel {

        /**
         * The column names for the table. If trueModel is null, then the table will have 3 columns. If trueModel is not
         * null, then the table will have 5 columns.
         */
        private final String[] columnNames = {"Pair", "Min Est Effect", "Max Est Effect", "Min True Effect", "Max True Effect"};

        /**
         * The data for the table.
         */
        private final Object[][] data;

        /**
         * Constructs a new table estModel for the results of the IDA check.
         */
        public IdaTableModel(List<OrderedPair<Node>> pairs, IdaCheck estModel, IdaCheck trueModel) {

            // Create the data for the table
            this.data = trueModel == null ? new Object[pairs.size()][3] : new Object[pairs.size()][5];

            // Fill in the data for the table
            for (int i = 0; i < pairs.size(); i++) {
                OrderedPair<Node> pair = pairs.get(i);
                String edge = pair.getSecond() + " <- " + pair.getFirst();
                double minEst = estModel.getMinEffect(pair.getFirst(), pair.getSecond());
                double maxEst = estModel.getMaxEffect(pair.getFirst(), pair.getSecond());

               if (trueModel == null) {
                    this.data[i][0] = edge;
                    this.data[i][1] = minEst;
                    this.data[i][2] = maxEst;
                } else {
                    double minTrue = trueModel.getMinEffect(pair.getFirst(), pair.getSecond());
                    double maxTrue = trueModel.getMaxEffect(pair.getFirst(), pair.getSecond());
                    this.data[i][0] = edge;
                    this.data[i][1] = minEst;
                    this.data[i][2] = maxEst;
                    this.data[i][3] = minTrue;
                    this.data[i][4] = maxTrue;
                }
            }
        }

        @Override
        public int getRowCount() {
            return this.data.length;
        }

        @Override
        public int getColumnCount() {
            return data[0].length;
        }

        @Override
        public String getColumnName(int col) {
            return this.columnNames[col];
        }

        @Override
        public Object getValueAt(int row, int col) {
            return this.data[row][col];
        }

        @Override
        public Class<?> getColumnClass(int c) {
            return getValueAt(0, c).getClass();
        }
    }
}

