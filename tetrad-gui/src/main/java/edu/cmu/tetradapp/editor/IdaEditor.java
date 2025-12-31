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

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.OrderedPair;
import edu.cmu.tetrad.search.IdaCheck;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetradapp.model.IdaModel;

import javax.swing.*;
import javax.swing.event.RowSorterEvent;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * An editor for the results of the IDA check. This editor can be sorted by clicking on the column headers, up or down.
 * The table can be copied and pasted into a text file or into Excel.
 * <p>
 * For an estimated graph, the table will have 3 columns: Pair, Min Est Effect, and Max Est Effect. For a true graph,
 * the table will have 5 columns: Pair, Min Est Effect, Max Est Effect, Min True Effect, and Max True Effect.
 *
 * <p>
 * The revised version of this editor adds explicit controls for specifying:
 * <ul>
 *     <li>a set of treatment variables X, and</li>
 *     <li>a set of outcome variables Y</li>
 * </ul>
 * The IDA table is then restricted to ordered pairs (X, Y) with X in the treatment set and Y in the outcome set.
 * Variable names may include simple wildcards "*" and "?" as in the Adjustment / Total Effects component.
 *
 * @author josephramsey
 * @see IdaModel
 * @see edu.cmu.tetrad.search.Ida
 */
public class IdaEditor extends JPanel {

    /**
     * The underlying model.
     */
    private final IdaModel idaModel;

    /**
     * The underlying IDA checker over the estimated graph.
     */
    private final IdaCheck idaCheckEst;

    /**
     * All legal ordered pairs for the current IDA checker (all distinct pairs of nodes).
     */
    private final List<OrderedPair<Node>> allPairs;
    /**
     * Swing components that need to be accessed from listeners.
     */
    private final JTable table;
    private final NumberFormat numberFormat;
    /**
     * Text fields for specifying treatments (X) and outcomes (Y).
     * These support simple wildcards "*", "?".
     */
    private final JTextField treatmentsField = new JTextField();
    private final JTextField outcomesField = new JTextField();
    /**
     * "Run" button to recompute the table for the current X/Y selection.
     */
    private final JButton runButton = new JButton("Run");
    /**
     * Checkbox to toggle use of Optimal IDA (if available).
     */
    private final JCheckBox showOptimalIda = new JCheckBox("Show Optimal IDA");
    /**
     * The subset of ordered pairs currently displayed in the table
     * (respecting the X/Y selections).
     */
    private List<OrderedPair<Node>> currentPairs = new ArrayList<>();
    private IdaTableModel tableModel;
    private TableRowSorter<IdaTableModel> sorter;
    /**
     * The label for the average squared distance.
     */
    private JLabel avgSquaredDistLabel;

    /**
     * The label for the squared difference between minimum total effect and true total effect.
     */
    private JLabel squaredDiffMinTotalLabel;

    /**
     * The label for the squared difference between maximum total effect and true total effect.
     */
    private JLabel squaredDiffMaxTotalLabel;

    /**
     * Constructs a new IDA editor for the given IDA model.
     *
     * @param idaModel the IDA model.
     */
    public IdaEditor(IdaModel idaModel) {
        this.idaModel = idaModel;
        this.idaCheckEst = idaModel.getIdaCheckEst();
        this.allPairs = new ArrayList<>(idaCheckEst.getOrderedPairs());
        this.numberFormat = NumberFormatUtil.getInstance().getNumberFormat();

        setLayout(new BorderLayout());

        // Initial empty selection, user must choose X and Y then click Run.
        this.currentPairs = new ArrayList<>();

        // Table and sorter
        this.tableModel = new IdaTableModel(currentPairs, idaCheckEst, idaModel.getTrueSemIm());
        this.table = new JTable(tableModel);
        NumberFormatRenderer numberRenderer = new NumberFormatRenderer(numberFormat);
        table.setDefaultRenderer(Double.class, numberRenderer);
        table.setAutoCreateRowSorter(true);
        table.setFillsViewportHeight(true);

        this.sorter = new TableRowSorter<>(tableModel);
        table.setRowSorter(sorter);

        // When the user sorts, recompute the summary statistics using only visible rows.
        sorter.addRowSorterListener(e -> {
            if (e.getType() == RowSorterEvent.Type.SORTED) {
                List<OrderedPair<Node>> visiblePairs = new ArrayList<>();
                int rowCount = table.getRowCount();

                for (int i = 0; i < rowCount; i++) {
                    int modelIndex = table.convertRowIndexToModel(i);
                    if (modelIndex >= 0 && modelIndex < currentPairs.size()) {
                        visiblePairs.add(currentPairs.get(modelIndex));
                    }
                }

                if (idaModel.getTrueSemIm() != null && !visiblePairs.isEmpty()) {
                    setSummaryText(numberFormat, idaCheckEst, visiblePairs);
                }
            }
        });

        // Show Optimal IDA checkbox – only relevant if the estimated graph is a legal PDAG.
        if (idaCheckEst.getGraph().paths().isLegalPdag()) {
            showOptimalIda.setSelected(idaCheckEst.isShowOptimalIda());
//            showOptimalIda.addfActionListener(e -> {
//                idaCheckEst.setShowOptimalIda(showOptimalIda.isSelected());
//                idaCheckEst.recompute();
//                recomputeTable();
//            });
        } else {
            showOptimalIda.setEnabled(false);
        }

        treatmentsField.setText(idaModel.getTreatmentsText());
        outcomesField.setText(idaModel.getOutcomesText());

        treatmentsField.addFocusListener(new FocusAdapter() {
            public void focusLost(FocusEvent e) {
                idaModel.setTreatmentsText(treatmentsField.getText());
            }
        });

        outcomesField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                idaModel.setOutcomesText(outcomesField.getText());
            }
        });

        showOptimalIda.setSelected(idaModel.isOptimalIdaSelected());

        showOptimalIda.addActionListener(e -> {
            idaModel.setOptimalIdaSelected(!idaModel.isOptimalIdaSelected());
        });

        // Top control panel: X/Y fields, Run button, and (if available) Optimal IDA checkbox.
        Box controlsBox = Box.createVerticalBox();

        Box xys = Box.createVerticalBox();

        Box xyRow1 = Box.createHorizontalBox();
        xyRow1.add(new JLabel("Treatments (X):"));
        xyRow1.add(Box.createHorizontalGlue());
        xyRow1.add(treatmentsField);

        Box xyRow2 = Box.createHorizontalBox();
        xyRow2.add(new JLabel("Outcomes (Y):"));
        xyRow2.add(Box.createHorizontalStrut(5));
        xyRow2.add(outcomesField);

        xys.add(xyRow1);
        xys.add(xyRow2);
        controlsBox.add(xys);

        // After fields are constructed
        lockTextFieldHeight(treatmentsField);
        lockTextFieldHeight(outcomesField);

        Box buttonRow = Box.createHorizontalBox();
        buttonRow.add(runButton);
        buttonRow.add(Box.createHorizontalStrut(15));
        buttonRow.add(showOptimalIda);
        buttonRow.add(Box.createHorizontalGlue());
        controlsBox.add(Box.createVerticalStrut(5));
        controlsBox.add(buttonRow);

        // Stats box
        Box statsBox = Box.createVerticalBox();
        if (idaModel.getTrueSemIm() != null) {
            avgSquaredDistLabel = new JLabel();
            addStatToBox(avgSquaredDistLabel, statsBox);

            squaredDiffMinTotalLabel = new JLabel();
            addStatToBox(squaredDiffMinTotalLabel, statsBox);

            squaredDiffMaxTotalLabel = new JLabel();
            addStatToBox(squaredDiffMaxTotalLabel, statsBox);
        }

        // Main "Table" tab layout
        Box mainBox = Box.createVerticalBox();
        mainBox.add(controlsBox);
        mainBox.add(Box.createVerticalStrut(5));
        mainBox.add(new JScrollPane(table));
        if (idaModel.getTrueSemIm() != null) {
            mainBox.add(Box.createVerticalStrut(5));
            mainBox.add(statsBox);
        }

        // Wire up "Run" button
        runButton.addActionListener(e -> {
            try {
                idaCheckEst.setShowOptimalIda(showOptimalIda.isSelected());
                idaCheckEst.recompute();
                recomputeTable();
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(this,
                        ex.getMessage(),
                        "Invalid selection",
                        JOptionPane.ERROR_MESSAGE);
            }
        });

        // Tabbed pane with Table + Help
        JTabbedPane tabbedPane = new JTabbedPane(JTabbedPane.TOP);
        tabbedPane.addTab("Table", mainBox);
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
     * Convert a shell-style wildcard pattern (*, ?) into a proper regular expression.
     */
    private static String wildcardToRegex(String pattern) {
        StringBuilder sb = new StringBuilder();
        sb.append("^");
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            switch (c) {
                case '*':
                    sb.append(".*");
                    break;
                case '?':
                    sb.append(".");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '.':
                case '[':
                case ']':
                case '{':
                case '}':
                case '(':
                case ')':
                case '+':
                case '-':
                case '^':
                case '$':
                case '|':
                    sb.append("\\").append(c);
                    break;
                default:
                    sb.append(c);
            }
        }
        sb.append("$");
        return sb.toString();
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

    private static void lockTextFieldHeight(JTextField field) {
        Dimension pref = field.getPreferredSize();
        // Allow infinite width, but fix the height to the preferred height
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, pref.height));
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
                The original reference for the IDA algorithm is:
                
                Maathuis, Marloes H., Markus Kalisch, and Peter Bühlmann (2009).
                “Estimating high-dimensional intervention effects from observational data.”
                The Annals of Statistics 37(6A): 3133–3164.
                
                This panel implements IDA-style total effect estimation for a given estimated graph and data set. Each row of the table corresponds to an ordered pair (X, Y), where X is a treatment (intervention) variable and Y is an outcome variable.
                
                For each (X, Y) pair, the table reports the range of estimated total effects of X on Y obtained by linear regression over all valid adjustment sets implied by the graph. Specifically, the minimum and maximum regression coefficient for X across these adjustment sets are shown.
                
                If the graph implies that there is no potentially directed path from X to Y, then the total effect is necessarily zero, and the table reflects this without running any regressions. If the graph is not amenable for estimating the effect of X on Y, the corresponding row is marked as “Not amenable.”
                
                Treatments (X) and outcomes (Y) are configured at the top of the Table tab. You may enter a comma- or space-separated list of variable names (for example, X1, X2, X3), or use wildcard patterns with “*” and “?” (for example, X* for all variables whose names start with “X”, or ?bar for any single-character prefix followed by “bar”).
                
                The IDA table is restricted to ordered pairs (X, Y) with X in the treatment set and Y in the outcome set. If either set is empty, no table is produced.
                
                Press “Run” after changing the X or Y fields to recompute the results. If “Show Optimal IDA” is available and checked, the panel uses the Optimal IDA variant based on the O-set of Witte et al. (2020), “On efficient adjustment in causal graphs,” JMLR 21(246): 1–45; otherwise, it uses the standard IDA procedure.
                
                The summary statistics at the bottom of the panel are computed using only the rows currently visible in the table (for example, after sorting or filtering). These statistics are intended primarily for simulation studies and benchmarking, rather than for substantive data analysis.
                """);
        ;
        return textArea;
    }

    /**
     * Recomputes {@link #currentPairs}, rebuilds the table model, and refreshes the summary
     * statistics based on the current X/Y selections.
     */
    private void recomputeTable() {
        Graph graph = idaCheckEst.getGraph();

        Set<Node> X = parseNodeList(graph, treatmentsField.getText().trim());
        Set<Node> Y = parseNodeList(graph, outcomesField.getText().trim());

        if (X.isEmpty() || Y.isEmpty()) {
            throw new IllegalArgumentException("Treatments (X) and outcomes (Y) sets must not be empty.");
        }

        // Restrict allPairs to those matching X x Y.
        List<OrderedPair<Node>> newPairs = new ArrayList<>();
        for (OrderedPair<Node> pair : allPairs) {
            Node x = pair.getFirst();
            Node y = pair.getSecond();
            if (X.contains(x) && Y.contains(y)) {
                newPairs.add(pair);
            }
        }

        if (newPairs.isEmpty()) {
            throw new IllegalArgumentException("No ordered pairs (X, Y) matched the given treatments/outcomes.");
        }

        this.currentPairs = newPairs;

        this.tableModel = new IdaTableModel(currentPairs, idaCheckEst, idaModel.getTrueSemIm());
        table.setModel(tableModel);

        this.sorter = new TableRowSorter<>(tableModel);
        table.setRowSorter(sorter);

        // Reattach the sorter listener for statistics on visible rows.
        sorter.addRowSorterListener(e -> {
            if (e.getType() == RowSorterEvent.Type.SORTED) {
                List<OrderedPair<Node>> visiblePairs = new ArrayList<>();
                int rowCount = table.getRowCount();

                for (int i = 0; i < rowCount; i++) {
                    int modelIndex = table.convertRowIndexToModel(i);
                    if (modelIndex >= 0 && modelIndex < currentPairs.size()) {
                        visiblePairs.add(currentPairs.get(modelIndex));
                    }
                }

                if (idaModel.getTrueSemIm() != null && !visiblePairs.isEmpty()) {
                    setSummaryText(numberFormat, idaCheckEst, visiblePairs);
                }
            }
        });

        if (idaModel.getTrueSemIm() != null) {
            setSummaryText(numberFormat, idaCheckEst, currentPairs);
        }

        table.revalidate();
        table.repaint();
    }

    /**
     * Parse a comma/whitespace separated list of node names, allowing "*" and "?" wildcards,
     * relative to the given graph.
     */
    private Set<Node> parseNodeList(Graph graph, String text) {
        LinkedHashSet<Node> nodes = new LinkedHashSet<>();
        if (text == null || text.isEmpty()) return nodes;

        String[] tokens = text.split("[,\\s]+");
        for (String tok : tokens) {
            String name = tok.trim();
            if (name.isEmpty()) continue;

            boolean hasWildcard = name.contains("*") || name.contains("?");

            if (!hasWildcard) {
                Node n = graph.getNode(name);
                if (n == null) {
                    throw new IllegalArgumentException("Unknown variable: " + name);
                }
                nodes.add(n);
            } else {
                String regex = wildcardToRegex(name);
                Pattern p = Pattern.compile(regex);

                boolean matchedAny = false;
                for (Node n : graph.getNodes()) {
                    if (p.matcher(n.getName()).matches()) {
                        nodes.add(n);
                        matchedAny = true;
                    }
                }
                if (!matchedAny) {
                    throw new IllegalArgumentException(
                            "Wildcard pattern \"" + name + "\" matched no variables.");
                }
            }
        }
        return nodes;
    }

    /**
     * Updates the three summary statistics labels in the stats box.
     */
    private void setSummaryText(NumberFormat numberFormat, IdaCheck idaCheckEst, List<OrderedPair<Node>> pairs) {
        if (avgSquaredDistLabel != null) {
            avgSquaredDistLabel.setText("Average Squared Distance: "
                                        + numberFormat.format(idaCheckEst.getAverageSquaredDistance(pairs)));
        }
        if (squaredDiffMinTotalLabel != null) {
            squaredDiffMinTotalLabel.setText("Average Min Squared Diff (est vs true): "
                                             + numberFormat.format(idaCheckEst.getAvgMinSquaredDiffEstTrue(pairs)));
        }
        if (squaredDiffMaxTotalLabel != null) {
            squaredDiffMaxTotalLabel.setText("Average Max Squared Diff (est vs true): "
                                             + numberFormat.format(idaCheckEst.getAvgMaxSquaredDiffEstTrue(pairs)));
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

/**
 * Table model for the IDA results.
 *
 * This is unchanged in spirit from the previous implementation: rows correspond
 * to ordered pairs in the given list, and columns contain the various IDA
 * effect summaries supplied by {@link IdaCheck}.
 */
class IdaTableModel extends AbstractTableModel {

    /**
     * The column names for the table. The first column is the pair of nodes, the second column is the minimum total
     * effect, the third column is the maximum total effect, the fourth column is the minimum absolute total effect,
     * the fifth column is the true total effect, and the sixth column is the squared distance from the true total
     * effect, where if the true total effect falls between the minimum and maximum total effect zero is reported.
     * If the true model is not given, the last two columns are not included.
     */
    private final String[] columnNames = {"Pair", "Min TE", "Max TE", "IDA Min Effect", "True TE", "Sq Dist"};

    /**
     * The data for the table.
     */
    private final Object[][] data;

    /**
     * Constructs a new table model for the results of the IDA check.
     *
     * @param pairs      the ordered pairs of nodes in the graph.
     * @param estModel   the IDA check on the estimated graph.
     * @param trueSemIm  the true SEM instantiated model, or null if not available.
     */
    IdaTableModel(List<OrderedPair<Node>> pairs, IdaCheck estModel, SemIm trueSemIm) {
        boolean hasTrue = trueSemIm != null;
        data = new Object[pairs.size()][hasTrue ? 6 : 4];

        for (int i = 0; i < pairs.size(); i++) {
            OrderedPair<Node> pair = pairs.get(i);
            String edge = pair.getFirst().getName() + " ~~> " + pair.getSecond().getName();
            double minTotalEffect = estModel.getMinTotalEffect(pair.getFirst(), pair.getSecond());
            double maxTotalEffect = estModel.getMaxTotalEffect(pair.getFirst(), pair.getSecond());
            double minAbsTotalEffect = estModel.getIdaMinEffect(pair.getFirst(), pair.getSecond());

            data[i][0] = edge;
            data[i][1] = minTotalEffect;
            data[i][2] = maxTotalEffect;
            data[i][3] = minAbsTotalEffect;

            if (hasTrue) {
                double trueTotalEffect = estModel.getTrueTotalEffect(pair);
                double squaredDistance = estModel.getSquaredDistance(pair);

                data[i][4] = trueTotalEffect;
                data[i][5] = squaredDistance;
            }
        }
    }

    @Override
    public int getRowCount() {
        return data.length;
    }

    @Override
    public int getColumnCount() {
        return data.length == 0 ? 4 : data[0].length;
    }

    @Override
    public String getColumnName(int col) {
        return columnNames[col];
    }

    @Override
    public Class<?> getColumnClass(int col) {
        if (col == 0) return String.class;
        return Double.class;
    }

    @Override
    public Object getValueAt(int row, int col) {
        return data[row][col];
    }
}

