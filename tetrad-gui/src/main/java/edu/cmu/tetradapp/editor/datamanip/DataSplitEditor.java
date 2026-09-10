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

package edu.cmu.tetradapp.editor.datamanip;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetradapp.model.datamanip.DataSplitter;
import edu.cmu.tetradapp.model.datamanip.DataSplitter.BinMode;
import edu.cmu.tetradapp.model.datamanip.DataSplitter.Preview;
import edu.cmu.tetradapp.model.datamanip.DataSplitter.Spec;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Editor for the Split Data tool. The user either writes split conditions directly, one per line, or selects
 * variables and has conditions generated for them (one per category, or one per bin of a continuous variable, or
 * the product over several variables), then edits the result. A preview reports how many rows each split would get,
 * how many rows match no condition, and how many match more than one.
 * <p>
 * The condition language is that of the Data Subset tool; see {@link DataSplitter} for the line syntax.
 */
public class DataSplitEditor extends JPanel {

    private final DataSet dataSet;

    private final JList<Node> variableList;
    private final JRadioButton equalCountButton = new JRadioButton("Equal count", true);
    private final JRadioButton equalWidthButton = new JRadioButton("Equal width");
    private final JRadioButton cutPointsButton = new JRadioButton("Cut points:");
    private final JSpinner numBinsSpinner = new JSpinner(new SpinnerNumberModel(2, 2, 1000, 1));
    private final JTextField cutPointsField = new JTextField(14);

    private final JTextArea linesArea = new JTextArea(10, 50);
    private final JTextField dropVarsField = new JTextField(30);
    private final JCheckBox remainderCheck = new JCheckBox("Add a data set for rows matching no condition, named");
    private final JCheckBox keepCategoriesCheck = new JCheckBox(
            "Discrete variables keep every category of the full data (uncheck to keep only those observed in each split)",
            true);
    private final JTextField remainderNameField = new JTextField(DataSplitter.DEFAULT_REMAINDER_NAME, 12);

    private final DefaultTableModel previewModel = new DefaultTableModel(new Object[]{"Split", "Rows"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JLabel summaryLabel = new JLabel(" ");
    private final JLabel errorLabel = new JLabel(" ");

    /**
     * Constructs an editor for splitting the given data set.
     *
     * @param dataSet the data set to split.
     */
    public DataSplitEditor(DataSet dataSet) {
        this.dataSet = dataSet;

        DefaultListModel<Node> listModel = new DefaultListModel<>();
        for (Node v : dataSet.getVariables()) listModel.addElement(v);
        this.variableList = new JList<>(listModel);

        setPreferredSize(new Dimension(720, 680));
        initGui();
        updateBinControls();
    }

    // ------------------------------------------------------------------------
    // GUI construction
    // ------------------------------------------------------------------------

    private void initGui() {
        setLayout(new BorderLayout(8, 8));

        add(buildGeneratePanel(), BorderLayout.NORTH);
        add(buildLinesPanel(), BorderLayout.CENTER);
        add(buildBottomPanel(), BorderLayout.SOUTH);
    }

    private JPanel buildGeneratePanel() {
        variableList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        variableList.setVisibleRowCount(6);
        variableList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof DiscreteVariable dv) {
                    setText(dv.getName() + "   (discrete, " + dv.getNumCategories() + " categories)");
                } else if (value instanceof Node n) {
                    setText(n.getName() + "   (continuous)");
                }
                return this;
            }
        });

        JScrollPane listScroll = new JScrollPane(variableList);
        listScroll.setPreferredSize(new Dimension(300, 120));

        ButtonGroup group = new ButtonGroup();
        group.add(equalCountButton);
        group.add(equalWidthButton);
        group.add(cutPointsButton);
        equalCountButton.addActionListener(e -> updateBinControls());
        equalWidthButton.addActionListener(e -> updateBinControls());
        cutPointsButton.addActionListener(e -> updateBinControls());

        cutPointsField.setToolTipText("Comma-separated numbers, e.g. 10, 20, 30");

        Box binBox = Box.createVerticalBox();
        binBox.add(leftAligned(new JLabel("For continuous variables, cut into bins by:")));

        Box countRow = Box.createHorizontalBox();
        countRow.add(equalCountButton);
        countRow.add(equalWidthButton);
        countRow.add(new JLabel("  Number of bins: "));
        numBinsSpinner.setMaximumSize(new Dimension(70, numBinsSpinner.getPreferredSize().height));
        countRow.add(numBinsSpinner);
        countRow.add(Box.createHorizontalGlue());
        binBox.add(countRow);

        Box cutRow = Box.createHorizontalBox();
        cutRow.add(cutPointsButton);
        cutPointsField.setMaximumSize(new Dimension(220, cutPointsField.getPreferredSize().height));
        cutRow.add(cutPointsField);
        cutRow.add(Box.createHorizontalGlue());
        binBox.add(cutRow);

        JButton replaceButton = new JButton("Generate (replace)");
        replaceButton.setToolTipText("Replace the conditions below with generated ones for the selected variables");
        replaceButton.addActionListener(e -> generate(true));

        JButton appendButton = new JButton("Generate (append)");
        appendButton.setToolTipText("Add generated conditions for the selected variables below the existing ones");
        appendButton.addActionListener(e -> generate(false));

        Box buttonRow = Box.createHorizontalBox();
        buttonRow.add(replaceButton);
        buttonRow.add(Box.createHorizontalStrut(6));
        buttonRow.add(appendButton);
        buttonRow.add(Box.createHorizontalGlue());
        binBox.add(Box.createVerticalStrut(8));
        binBox.add(buttonRow);
        binBox.add(Box.createVerticalGlue());

        JPanel right = new JPanel(new BorderLayout());
        right.add(binBox, BorderLayout.NORTH);

        JPanel panel = new JPanel(new BorderLayout(10, 0));
        panel.setBorder(new TitledBorder("Generate conditions from variables (select one or more; "
                + "several variables give one split per combination)"));
        panel.add(listScroll, BorderLayout.WEST);
        panel.add(right, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildLinesPanel() {
        linesArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, linesArea.getFont().getSize()));
        linesArea.setLineWrap(false);

        JLabel help = new JLabel("<html>One split per line. A line is a row condition in the Data Subset syntax, "
                + "e.g. <tt>Region = Bejaia</tt>, <tt>Age in [20, 30)</tt>, <tt>Sex = F and Age &gt;= 30</tt>. "
                + "Put <tt>name |</tt> in front of a condition to name its data set. "
                + "A line that is just a variable name (or several, comma-separated) gives one split per "
                + "observed value (combination) and removes that variable from the splits. "
                + "Lines starting with <tt>#</tt> are ignored.</html>");

        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setBorder(new TitledBorder("Split conditions"));
        panel.add(help, BorderLayout.NORTH);
        panel.add(new JScrollPane(linesArea), BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildBottomPanel() {
        Box options = Box.createVerticalBox();

        Box dropRow = Box.createHorizontalBox();
        dropRow.add(new JLabel("Remove these variables from every split (comma-separated): "));
        dropVarsField.setToolTipText("Filled in with the discrete splitting variables when conditions are generated; "
                + "a variable constant within a split has zero variance there");
        dropVarsField.setMaximumSize(new Dimension(Integer.MAX_VALUE, dropVarsField.getPreferredSize().height));
        dropRow.add(dropVarsField);
        options.add(dropRow);
        options.add(Box.createVerticalStrut(4));

        Box remainderRow = Box.createHorizontalBox();
        remainderRow.add(remainderCheck);
        remainderNameField.setMaximumSize(new Dimension(160, remainderNameField.getPreferredSize().height));
        remainderRow.add(remainderNameField);
        remainderRow.add(Box.createHorizontalGlue());
        remainderCheck.addActionListener(e -> remainderNameField.setEnabled(remainderCheck.isSelected()));
        remainderNameField.setEnabled(false);
        options.add(remainderRow);

        keepCategoriesCheck.setToolTipText("Multi-data-set searches such as IMaGES need the same categories in every "
                + "data set; separate per-split analyses may prefer to drop categories a split never takes");
        keepCategoriesCheck.addActionListener(e -> preview());
        options.add(leftAligned(keepCategoriesCheck));

        JButton previewButton = new JButton("Preview");
        previewButton.addActionListener(e -> preview());

        JTable previewTable = new JTable(previewModel);
        previewTable.getColumnModel().getColumn(1).setMaxWidth(80);
        JScrollPane tableScroll = new JScrollPane(previewTable);
        tableScroll.setPreferredSize(new Dimension(400, 110));

        errorLabel.setForeground(new Color(160, 0, 0));

        Box previewHeader = Box.createHorizontalBox();
        previewHeader.add(previewButton);
        previewHeader.add(Box.createHorizontalStrut(10));
        previewHeader.add(summaryLabel);
        previewHeader.add(Box.createHorizontalGlue());

        JPanel previewPanel = new JPanel(new BorderLayout(0, 4));
        previewPanel.setBorder(new TitledBorder("Preview"));
        previewPanel.add(previewHeader, BorderLayout.NORTH);
        previewPanel.add(tableScroll, BorderLayout.CENTER);
        previewPanel.add(errorLabel, BorderLayout.SOUTH);

        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.add(options, BorderLayout.NORTH);
        panel.add(previewPanel, BorderLayout.CENTER);
        return panel;
    }

    private static Box leftAligned(JComponent c) {
        Box b = Box.createHorizontalBox();
        b.add(c);
        b.add(Box.createHorizontalGlue());
        return b;
    }

    private void updateBinControls() {
        boolean cuts = cutPointsButton.isSelected();
        cutPointsField.setEnabled(cuts);
        numBinsSpinner.setEnabled(!cuts);
    }

    // ------------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------------

    private void generate(boolean replace) {
        List<Node> selected = variableList.getSelectedValuesList();
        if (selected.isEmpty()) {
            showError("Select one or more variables to generate conditions for.");
            return;
        }

        BinMode mode = equalCountButton.isSelected() ? BinMode.EQUAL_COUNT
                : equalWidthButton.isSelected() ? BinMode.EQUAL_WIDTH : BinMode.CUT_POINTS;
        int numBins = (Integer) numBinsSpinner.getValue();

        double[] cutPoints = null;
        if (mode == BinMode.CUT_POINTS) {
            try {
                cutPoints = parseCutPoints(cutPointsField.getText());
            } catch (IllegalArgumentException e) {
                showError(e.getMessage());
                return;
            }
        }

        List<String> lines;
        try {
            lines = DataSplitter.generateLines(dataSet, selected, mode, numBins, cutPoints);
        } catch (IllegalArgumentException e) {
            showError(e.getMessage());
            return;
        }

        if (lines.isEmpty()) {
            showError("None of the selected variables has two or more distinct values to split on.");
            return;
        }

        String existing = linesArea.getText();
        if (replace || existing.isBlank()) {
            linesArea.setText(String.join("\n", lines));
        } else {
            linesArea.setText(existing.stripTrailing() + "\n" + String.join("\n", lines));
        }

        // Discrete splitting variables are constant within their splits; queue them for removal.
        Set<String> drop = new LinkedHashSet<>(parseNames(dropVarsField.getText()));
        for (Node v : selected) {
            if (v instanceof DiscreteVariable) drop.add(v.getName());
        }
        dropVarsField.setText(String.join(", ", drop));

        preview();
    }

    /**
     * Refreshes the preview table from the current text. Returns true if the spec is valid.
     *
     * @return true if the current spec parses and evaluates without error.
     */
    public boolean preview() {
        previewModel.setRowCount(0);
        summaryLabel.setText(" ");
        errorLabel.setText(" ");

        Spec spec = getSpec();
        Preview p;
        try {
            p = DataSplitter.preview(dataSet, spec);
        } catch (IllegalArgumentException e) {
            showError(e.getMessage());
            return false;
        }

        for (int i = 0; i < p.splits().size(); i++) {
            previewModel.addRow(new Object[]{p.splits().get(i).name(), p.rowCounts()[i]});
        }

        int empty = p.splits().size() - p.numNonEmpty();
        StringBuilder sb = new StringBuilder();
        sb.append(p.numNonEmpty()).append(" non-empty split").append(p.numNonEmpty() == 1 ? "" : "s");
        if (empty > 0) sb.append(" (").append(empty).append(" empty, omitted)");
        sb.append(", ").append(p.numRows()).append(" rows: ");
        sb.append(p.unmatched()).append(" match no condition");
        if (spec.includeRemainder() && p.unmatched() > 0) sb.append(" (kept as \"")
                .append(spec.effectiveRemainderName()).append("\")");
        sb.append(", ").append(p.multiplyMatched()).append(" match more than one.");
        if (!p.impliedDropVarNames().isEmpty()) {
            sb.append(" Also removing: ").append(String.join(", ", p.impliedDropVarNames())).append(".");
        }
        if (p.numSplitsWithUnobservedCategories() > 0) {
            sb.append(spec.keepAllCategories() ? " Unobserved categories kept in " : " Categories trimmed in ")
                    .append(p.numSplitsWithUnobservedCategories()).append(" split")
                    .append(p.numSplitsWithUnobservedCategories() == 1 ? "." : "s.");
        }
        summaryLabel.setText(sb.toString());

        if (p.splits().isEmpty()) {
            showError("No conditions given.");
            return false;
        }
        if (p.numNonEmpty() == 0) {
            showError("No condition matches any row.");
            return false;
        }

        return true;
    }

    private void showError(String message) {
        errorLabel.setText("<html>" + escape(message) + "</html>");
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ------------------------------------------------------------------------
    // Spec in and out
    // ------------------------------------------------------------------------

    /**
     * Reads the spec currently shown in the editor.
     *
     * @return the spec.
     */
    public Spec getSpec() {
        List<String> lines = new ArrayList<>(List.of(linesArea.getText().split("\n", -1)));
        return new Spec(lines, parseNames(dropVarsField.getText()), remainderCheck.isSelected(),
                remainderNameField.getText(), keepCategoriesCheck.isSelected());
    }

    /**
     * Shows the given spec in the editor.
     *
     * @param spec the spec; null clears the editor.
     */
    public void applySpec(Spec spec) {
        if (spec == null) {
            linesArea.setText("");
            dropVarsField.setText("");
            remainderCheck.setSelected(false);
            remainderNameField.setText(DataSplitter.DEFAULT_REMAINDER_NAME);
            keepCategoriesCheck.setSelected(true);
        } else {
            linesArea.setText(spec.lines() == null ? "" : String.join("\n", spec.lines()));
            dropVarsField.setText(spec.dropVarNames() == null ? "" : String.join(", ", spec.dropVarNames()));
            remainderCheck.setSelected(spec.includeRemainder());
            remainderNameField.setText(spec.effectiveRemainderName());
            keepCategoriesCheck.setSelected(spec.keepAllCategories());
        }
        remainderNameField.setEnabled(remainderCheck.isSelected());
        if (!linesArea.getText().isBlank()) preview();
    }

    private static List<String> parseNames(String text) {
        List<String> names = new ArrayList<>();
        if (text == null) return names;
        for (String part : text.split(",")) {
            String t = part.trim();
            if (!t.isEmpty()) names.add(t);
        }
        return names;
    }

    private static double[] parseCutPoints(String text) {
        List<Double> values = new ArrayList<>();
        if (text != null) {
            for (String part : text.split("[,;\\s]+")) {
                String t = part.trim();
                if (t.isEmpty()) continue;
                try {
                    values.add(Double.parseDouble(t));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Cut point \"" + t + "\" is not a number.");
                }
            }
        }
        if (values.isEmpty()) {
            throw new IllegalArgumentException("Enter at least one cut point, e.g. 10, 20, 30.");
        }
        double[] out = new double[values.size()];
        for (int i = 0; i < out.length; i++) out[i] = values.get(i);
        return out;
    }
}
