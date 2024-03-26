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
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetradapp.model.IdaModel;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.RowSorterEvent;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.PatternSyntaxException;

/**
 * An editor for the results of the IDA check. This editor can be sorted by clicking on the column headers, up or down.
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
     * The label for the average squared distance.
     */
    private JLabel avgSquaredDistLabel = null;

    /**
     * The label for the squared difference between minimum total effect and true total effect.
     */
    private JLabel squaredDiffMinTotalLabel = null;

    /**
     *
     */
    private JLabel squaredDiffMaxTotalLabel = null;

    /**
     * Constructs a new IDA editor for the given IDA model.
     *
     * @param idaModel the IDA model.
     */
    public IdaEditor(IdaModel idaModel) {
        IdaCheck idaCheckEst = idaModel.getIdaCheckEst();

        // Grab the legal ordered pairs (i.e., all possible pairs of distinct nodes)
        List<OrderedPair<Node>> pairs = idaCheckEst.getOrderedPairs();

        // Create a table idaCheckEst for the results of the IDA check
        IdaTableModel tableModel = new IdaTableModel(pairs, idaCheckEst, idaModel.getTrueSemIm());
        this.setLayout(new BorderLayout());

        // Add the table to the left
        JTable table = new JTable(tableModel);
        NumberFormat numberFormat = NumberFormatUtil.getInstance().getNumberFormat();
        NumberFormatRenderer numberRenderer = new NumberFormatRenderer(numberFormat);
        table.setDefaultRenderer(Double.class, numberRenderer);
        table.setAutoCreateRowSorter(true);
        table.setFillsViewportHeight(true);

        this.add(new JScrollPane(table));

        // Create a TableRowSorter and set it to the JTable
        TableRowSorter<IdaTableModel> sorter = new TableRowSorter<>(tableModel);
        table.setRowSorter(sorter);

        sorter.addRowSorterListener(e -> {
            if (e.getType() == RowSorterEvent.Type.SORTED) {
                List<OrderedPair<Node>> pairs1 = idaCheckEst.getOrderedPairs();

                List<OrderedPair<Node>> visiblePairs = new ArrayList<>();
                int rowCount = table.getRowCount();

                for (int i = 0; i < rowCount; i++) {
                    int modelIndex = table.convertRowIndexToModel(i);
                    visiblePairs.add(pairs1.get(modelIndex));
                }

                if (avgSquaredDistLabel != null) {
                    avgSquaredDistLabel.setText("Average Squared Distance: " + numberFormat.format(idaCheckEst.getAverageSquaredDistance(visiblePairs)));
                }

                if (squaredDiffMinTotalLabel != null) {
                    squaredDiffMinTotalLabel.setText("Min Squared Difference Est True: " + numberFormat.format(idaCheckEst.getAvgMinSquaredDiffEstTrue(visiblePairs)));
                }

                if (squaredDiffMaxTotalLabel != null) {
                    squaredDiffMaxTotalLabel.setText("Max Squared Difference Est True: " + numberFormat.format(idaCheckEst.getAvgMaxSquaredDiffEstTrue(visiblePairs)));
                }
            }
        });

        // Create the text field
        JLabel label = new JLabel("Regexes (semicolon separated):");
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
                    String[] textParts = text.split(";+");
                    List<RowFilter<Object, Object>> filters = new ArrayList<>(textParts.length);
                    for (String part : textParts) {
                        try {
                            String trim = part.trim();

                            // Swap escapes for parentheses and pipes
                            trim = trim.replace("\\(", "<+++<");
                            trim = trim.replace("\\)", ">+++>");
                            trim = trim.replace("\\|", "|+++|");
                            trim = trim.replace("(", "\\(");
                            trim = trim.replace(")", "\\)");
                            trim = trim.replace("|", "\\|");
                            trim = trim.replace("<+++<", "(");
                            trim = trim.replace(">+++>", ")");
                            trim = trim.replace("|+++|", "|");

                            filters.add(RowFilter.regexFilter(trim));
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
        Box statsBox = Box.createVerticalBox();
        vert.add(statsBox);

        if (idaModel.getTrueSemIm() != null) {
            avgSquaredDistLabel = new JLabel();
            avgSquaredDistLabel.setText("Average Squared Distance: " + numberFormat.format(idaCheckEst.getAverageSquaredDistance(pairs)));
            addStatToBox(avgSquaredDistLabel, statsBox);

            squaredDiffMinTotalLabel = new JLabel();
            squaredDiffMinTotalLabel.setText("Min Squared Difference Est True: " + numberFormat.format(idaCheckEst.getAvgMinSquaredDiffEstTrue(pairs)));
            addStatToBox(squaredDiffMinTotalLabel, statsBox);

            squaredDiffMaxTotalLabel = new JLabel();
            squaredDiffMaxTotalLabel.setText("Max Squared Difference Est True: " + numberFormat.format(idaCheckEst.getAvgMaxSquaredDiffEstTrue(pairs)));
            addStatToBox(squaredDiffMaxTotalLabel, statsBox);
        }

        horiz.add(vert);

        JTabbedPane tabbedPane = new JTabbedPane(JTabbedPane.TOP);

        tabbedPane.addTab("Table", horiz);
        tabbedPane.addTab("Help", new JScrollPane(getHelp()));

        add(tabbedPane, BorderLayout.CENTER);

        if (idaModel.getTrueSemIm() == null) {
            setPreferredSize(new Dimension(400, 600));
        } else {
            setPreferredSize(new Dimension(600, 600));
        }

        revalidate();
        repaint();
    }

    /**
     * Adds a stat to the stats box.
     *
     * @param stat1    the stat to add.
     * @param statsBox the stats box.
     */
    private static void addStatToBox(JLabel stat1, Box statsBox) {
        Box horiz3 = Box.createHorizontalBox();
        horiz3.add(stat1);
        horiz3.add(Box.createHorizontalGlue());
        statsBox.add(horiz3);
    }

    /**
     * Returns a text area containing a description of the IDA checker.
     *
     * @return a text area containing a description of the IDA checker.
     */
    public static JTextArea getHelp() {
        JTextArea textArea = new JTextArea();
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setOpaque(true);
        textArea.setEditable(false);
        textArea.setText("""
                This IDA check displays the results of the IDA algorithm. The original reference is the following:

                Maathuis, Marloes H., Markus Kalisch, and Peter Bühlmann. "Estimating high-dimensional intervention effects from observational data." The Annals of Statistics 37.6A (2009): 3133-3164.
                        					  \s
                The IDA algorithm seeks to give a list of possible parents for a given variable Y and their corresponding total effects and absolute total effects on Y. It regresses Y on X U S, where X is a possible parent of Y and S is a set of possible parents of X. It reports the absolute values of the minimum regression coefficient or zero if Y is in the regression set. This interface tool reports the minimum and maximum of this range for each pair of distinct nodes in the graph.

                This procedure is carried out for an estimated graph, as, for instance, a graph from a search, which is assumed to be an MPDAG (which can be a DAG, a CPDAG, or a CPDAG with extra knowledge orientations after applying the Meek rules). It also optionally takes a Simulation box as input instead of a Data box, which allows for calculating the true total effects. It is then possible to assess whether this true total effect falls within the bounds given by the minimum and maximum total effects from the estimated MPDAG (in which case zero is reported) or, if not, what the distance to the nearest endpoint of the range is. This distance squared is reported for each pair of distinct nodes.

                Finally, the average of each column in the table is given at the bottom of the table.

                The tables may be sorted in increasing order by clicking on the column header one wishes to sort or in descending order by clicking the column header twice. Also, a facility is provided to specify a comma-separated list of regexes to select rows in the table.

                IDA Check is available in the Comparison box and can take the following combinations of parents:

                An estimated MPDAG (as from a search) and a dataset. The variables in these must be the same, and the dataset needs to be continuous. In this case, columns compared to the true model will not be displayed.

                A Simulation box containing a true SEM IM and an estimated MPDAG. In this case, extra columns compared to the true model, as described above, will be displayed.

                The contents of the table may be selected, copied, and pasted into Excel.
                        						
                The abbreviation "TE" in the table headers stands for "Total Effect." The abbreviation "Abs TE" stands for "Absolute Total Effect." The abbreviation "Sq Dist" stands for "Squared Distance." effect, the third column is the maximum total effect, the fourth column is the minimum absolute total effect, the fifth column is the true total effect, and the sixth column is the squared distance from the true total effect, where if the true total effect falls between the minimum and maximum total effect zero is reported. If the true model is not given, the last two columns are not included.

                A field is provided, allowing the users to specify using regexes (regular expressions) to display only a subset of the rows in a table. The expressions used are slight deviations of usual regular expressions in that the characters '(', ')', and '|' do not need to be escaped '\\(', '\\)', '\\|") to match an expression with those characters. Rather, to use those characters to control the regexes, the escape sequences should be used. This is because independence facts like "Ind(X, Y | Z)" are common for Tetrad. Note that when a table is subsetted using regexes, the statistics at the bottom of the table will be updated to reflect the subsetted table. The regexes are separated by semicolons.                
                """);

        return textArea;
    }

    /**
     * A table model for the results of the IDA check. This table can be sorted by clicking on the column headers, up or
     * down. The table can be copied and pasted into a text file or into Excel.
     */
    private static class IdaTableModel extends AbstractTableModel {
        /**
         * The column names for the table. The first column is the pair of nodes, the second column is the minimum total
         * effect, the third column is the maximum total effect, the fourth column is the minimum absolute total effect,
         * the fifth column is the true total effect, and the sixth column is the squared distance from the true total
         * effect, where if the true total effect falls between the minimum and maximum total effect zero is reported.
         * If the true model is not given, the last two columns are not included.
         */
        private final String[] columnNames = {"Pair", "Min TE", "Max TE", "Min Abs TE", "True TE", "Sq Dist"};
        /**
         * The data for the table.
         */
        private final Object[][] data;

        /**
         * Constructs a new table estModel for the results of the IDA check.
         */
        public IdaTableModel(List<OrderedPair<Node>> pairs, IdaCheck estModel, SemIm trueSemIm) {

            // Create the data for the table
            this.data = new Object[pairs.size()][trueSemIm == null ? 4 : 6];

            // Fill in the data for the table
            for (int i = 0; i < pairs.size(); i++) {
                OrderedPair<Node> pair = pairs.get(i);
                String edge = pair.getSecond() + " <- " + pair.getFirst();
                double minTotalEffect = estModel.getMinTotalEffect(pair.getFirst(), pair.getSecond());
                double maxTotalEffect = estModel.getMaxTotalEffect(pair.getFirst(), pair.getSecond());
                double minAbsTotalEffect = estModel.getMinAbsTotalEffect(pair.getFirst(), pair.getSecond());

                if (trueSemIm == null) {
                    this.data[i][0] = edge;
                    this.data[i][1] = minTotalEffect;
                    this.data[i][2] = maxTotalEffect;
                    this.data[i][3] = minAbsTotalEffect;
                } else {
                    double trueTotalEffect = estModel.getTrueTotalEffect(pair);
                    double squaredDistance = estModel.getSquaredDistance(pair);
                    this.data[i][0] = edge;
                    this.data[i][1] = minTotalEffect;
                    this.data[i][2] = maxTotalEffect;
                    this.data[i][3] = minAbsTotalEffect;
                    this.data[i][4] = trueTotalEffect;
                    this.data[i][5] = squaredDistance;
                }
            }
        }

        /**
         * Returns the number of rows in the table.
         *
         * @return the number of rows in the table.
         */
        @Override
        public int getRowCount() {
            return this.data.length;
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
            return this.data[row][col];
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

