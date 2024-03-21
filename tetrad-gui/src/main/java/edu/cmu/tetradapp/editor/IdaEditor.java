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
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetradapp.model.IdaModel;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * A editor for the results of the IDA check. This editor can be sorted by clicking on the column headers, up or down.
 * The table can be copied and pasted into a text file or into Excel.
 * <p>
 * For an estimated graph, the table will have 3 columns: Pair, Min Est Effect, and Max Est Effect. For a true graph,
 * the table will have 5 columns: Pair, Min Est Effect, Max Est Effect, Min True Effect, and Max True Effect.
 *
 * @author josephramsey
 * @version $Id: $Id
 * @see IdaModel
 * @see edu.cmu.tetrad.search.Ida
 */
public class IdaEditor extends JPanel {

    /**
     * Constructs a new IDA editor for the given IDA model.
     *
     * @param idaModel the IDA model.
     */
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

        NumberFormatRenderer numberRenderer = new NumberFormatRenderer(NumberFormatUtil.getInstance().getNumberFormat());
        table.setDefaultRenderer(Double.class, numberRenderer);

        table.setAutoCreateRowSorter(true);

        this.add(new JScrollPane(table));

        // Create a TableRowSorter and set it to the JTable
        TableRowSorter<IdaTableModel> sorter = new TableRowSorter<>(tableModel);
        table.setRowSorter(sorter);

        // Create the text field
        JLabel label = new JLabel("Regexes (comma separated):");
        JTextField filterText = new JTextField(15);
        filterText.setMaximumSize(new Dimension(500, 20));
        label.setLabelFor(filterText);

        // Create a listener for the text field that will update the table's row sort
        filterText.getDocument().addDocumentListener(new DocumentListener() {

            /**
             * Filters the table based on the text in the text field.
             */
            private void filter() {
                String text = filterText.getText();
                if (text.trim().isEmpty()) {
                    sorter.setRowFilter(null);
                } else {
                    // process comma and space separated input
                    String[] textParts = text.split(",+");
                    List<RowFilter<Object, Object>> filters = new ArrayList<>(textParts.length);
                    for (String part : textParts) {
                        try {
                            Pattern compile = Pattern.compile(part.trim());
                            filters.add(RowFilter.regexFilter("(?i)" + compile));
                        } catch (PatternSyntaxException e) {
                            // ignore
                        }
                    }
                    sorter.setRowFilter(RowFilter.orFilter(filters));
                }
            }

            /**
             * Inserts text into the text field.
             *
             * @param e the document event.
             */
            @Override
            public void insertUpdate(DocumentEvent e) {
                filter();
            }

            /**
             * Removes text from the text field.
             *
             * @param e the document event.
             */
            @Override
            public void removeUpdate(DocumentEvent e) {
                filter();
            }

            /**
             * Changes text in the text field.
             *
             * @param e the document event.
             */
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
        table.setFillsViewportHeight(true);

        horiz.add(vert);

        JTabbedPane tabbedPane = new JTabbedPane(JTabbedPane.TOP);

        tabbedPane.addTab("Table", horiz);
        tabbedPane.addTab("Help", new JScrollPane(getDescription()));

        add(tabbedPane, BorderLayout.CENTER);

        if (idaCheckTrue == null) {
            setPreferredSize(new Dimension(400, 600));
        } else {
            setPreferredSize(new Dimension(600, 600));
        }

        revalidate();
        repaint();
    }

    /**
     * Returns a text area containing a description of the IDA checker.
     *
     * @return a text area containing a description of the IDA checker.
     */
    public static JTextArea getDescription() {
        JTextArea textArea = new JTextArea();
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setOpaque(true);
        textArea.setEditable(false);
        textArea.setText(
                """
                        IDA Check

                        The IDA check displays the results of the IDA algorithm, as given in this paper:

                        Maathuis, Marloes H., Markus Kalisch, and Peter Bühlmann. "Estimating high-dimensional intervention effects from observational data." The Annals of Statistics 37.6A (2009): 3133-3164.
                        \s
                        The IDA algorithm seeks to give a list of possible parents of a given variable Y and their corresponding absolute effects on Y. It regresses Y on X &cup; S, where X is a possible parent of Y and S is a set of possible parents of X. It reports the absolute values of the minimum regression coefficient, or zero if Y is in the regression set. This interface tool reports the minimum and maximum of this range for each pair of distinct nodes in the graph.

                        This procedure is carried out for an estimated graph, as, for instance, a graph from a search, which is assumed to be a CPDAG, as well as for the true DAG, which (since the minimum and maximum values will be identical) gives true effect sizes. It is then possible to assess whether the true effect size falls within the bounds given by the minimum and maximum from the estimated CPDAG (in which case zero is reported) or, if not, what the distance to the nearest endpoint of the range is. This distance squared is reported for each pair of distinct nodes.

                        Finally, the average of each column in the table is given at the bottom of the table.

                        The tables may be sorted in increasing order by clicking on the column header one wishes to sort or in descending order by clicking the column header twice. Also, a facility is provided to specify a comma-separated list of regexes to select rows in the table.

                        IDA Check is available in the Comparison box and can take the following combinations of parents:

                        An estimated CPDAG (as from a search) and a dataset. The variables in these must be the same, and the dataset needs to be continuous. In this case, columns compared to the true model will not be displayed.

                        A Simulation box (containing a true DAG and a continuous dataset) and an estimated CPDAG. In this case, extra columns comparing to the true model, as described above, will be displayed.

                        A SEM IM, a box containing data, and a box containing a graph. In this case, the true graph will be obtained from the given SEM IM.

                        The contents of the table may be selected and copied and pasted into Excel."""
        );

        return textArea;
    }

    /**
     * A table model for the results of the IDA check. This table can be sorted by clicking on the column headers, up or
     * down. The table can be copied and pasted into a text file or into Excel.
     */
    private static class IdaTableModel extends AbstractTableModel {

        /**
         * The column names for the table. If trueModel is null, then the table will have 3 columns. If trueModel is not
         * null, then the table will have 5 columns.
         */
        private final String[] columnNames = {"Pair", "Min Est Effect", "Max Est Effect", "True Effect", "Squared Distance"};
        /**
         * The data for the table.
         */
        private final Object[][] data;
        /**
         * The pairs of nodes.
         */
        private final List<OrderedPair<Node>> pairs;
        /**
         * The averages for the table.
         */
        private final double[] averages;

        /**
         * Constructs a new table estModel for the results of the IDA check.
         */
        public IdaTableModel(List<OrderedPair<Node>> pairs, IdaCheck estModel, IdaCheck trueModel) {
            this.pairs = pairs;

            // Create the data for the table
            this.data = trueModel == null ? new Object[pairs.size()][3] : new Object[pairs.size()][5];

            averages = new double[4];

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

                    averages[0] += minEst;
                    averages[1] += maxEst;
                } else {
                    double trueEffect = trueModel.getMinEffect(pair.getFirst(), pair.getSecond());
                    double squaredDistance = estModel.getSquaredDistance(pair.getFirst(), pair.getSecond(), trueEffect);
                    this.data[i][0] = edge;
                    this.data[i][1] = minEst;
                    this.data[i][2] = maxEst;
                    this.data[i][3] = trueEffect;
                    this.data[i][4] = squaredDistance;

                    averages[0] += minEst;
                    averages[1] += maxEst;
                    averages[2] += trueEffect;
                    averages[3] += squaredDistance;
                }
            }

            // Divide by the number of rows
            for (int i = 0; i < averages.length; i++) {
                averages[i] /= pairs.size();
            }
        }

        /**
         * Returns the number of rows in the table.
         *
         * @return the number of rows in the table.
         */
        @Override
        public int getRowCount() {
            return this.data.length + 1;
        }

        /**
         * Returns the number of columns in the table.
         *
         * @return the number of columns in the table.
         */
        @Override
        public int getColumnCount() {
            return data[0].length;
        }

        /**
         * Returns the name of the column at the given index.
         *
         * @param col the index of the column.
         * @return the name of the column at the given index.
         */
        @Override
        public String getColumnName(int col) {
            return this.columnNames[col];
        }

        /**
         * Returns the value at the given row and column.
         *
         * @param row the row.
         * @param col the column.
         * @return the value at the given row and column.
         */
        @Override
        public Object getValueAt(int row, int col) {
            if (row < this.pairs.size()) {
                return this.data[row][col];
            } else {
                if (col == 0) {
                    return "Average";
                } else {
                    return averages[col - 1];
                }
            }
        }

        /**
         * Returns the class of the column at the given index.
         *
         * @param c the index of the column.
         * @return the class of the column at the given index.
         */
        @Override
        public Class<?> getColumnClass(int c) {
            return getValueAt(0, c).getClass();
        }
    }

    /**
     * A renderer for numbers in the table. This renderer formats numbers using a NumberFormat object.
     */
    public static class NumberFormatRenderer extends DefaultTableCellRenderer {
        /**
         * The formatter for the numbers.
         */
        private final NumberFormat formatter;

        /**
         * Constructs a new renderer with the given formatter.
         *
         * @param formatter the formatter for the numbers.
         */
        public NumberFormatRenderer(NumberFormat formatter) {
            this.formatter = formatter;
            setHorizontalAlignment(JLabel.RIGHT);
        }

        /**
         * Sets the value of the cell.
         *
         * @param value the value of the cell.
         */
        @Override
        public void setValue(Object value) {
            if (value instanceof Number) {
                value = formatter.format(value);
            }
            super.setValue(value);
        }
    }
}

