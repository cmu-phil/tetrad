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

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.NtadExplorer;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetradapp.model.datamanip.NtadExplorerWrapper;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Editor for NtadExplorerWrapper. Provides:
 * <ul>
 *   <li>Two-list variable selection (available vs selected).</li>
 *   <li>Controls for blockSize m, maxResults, and alpha.</li>
 *   <li>A JTable showing the list of NtadResult objects from the wrapper.</li>
 * </ul>
 */
public class NtadExplorerEditor extends JPanel {

    private final NtadExplorerWrapper wrapper;

    private final DataModel dataSet;

    private final DefaultListModel<Node> availableModel = new DefaultListModel<>();
    private final DefaultListModel<Node> selectedModel = new DefaultListModel<>();

    private final JList<Node> availableList = new JList<>(availableModel);
    private final JList<Node> selectedList = new JList<>(selectedModel);

    private final JSpinner blockSizeSpinner;
    private final JSpinner maxResultsSpinner;
    private final JFormattedTextField alphaField;

    private final NtadResultTableModel tableModel;
    private final JTable resultsTable;

    public NtadExplorerEditor(NtadExplorerWrapper wrapper) {
        if (wrapper == null) {
            throw new NullPointerException("NtadExplorerWrapper is required.");
        }

        this.wrapper = wrapper;
        this.dataSet = wrapper.getDataModel();

        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(5, 5, 5, 5));

        // Populate available variables (sorted by name).
        List<Node> vars = new ArrayList<>(dataSet.getVariables());
        vars.sort(Comparator.comparing(Node::getName));
        for (Node v : vars) {
            availableModel.addElement(v);
        }

        // Build left-right selection panel.
        JPanel selectionPanel = buildSelectionPanel();

        // Build parameter panel (top-right).
        JPanel paramPanel = buildParameterPanel();

        // Build results table.
        this.tableModel = new NtadResultTableModel(wrapper);
        this.resultsTable = new JTable(tableModel);
        this.resultsTable.setAutoCreateRowSorter(true);
        JScrollPane tableScroll = new JScrollPane(resultsTable);

        JPanel rightPanel = new JPanel(new BorderLayout(5, 5));
        rightPanel.add(paramPanel, BorderLayout.NORTH);
        rightPanel.add(tableScroll, BorderLayout.CENTER);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                selectionPanel, rightPanel);
        splitPane.setResizeWeight(0.35);

        add(splitPane, BorderLayout.CENTER);

        // Initialize controls
        blockSizeSpinner = createBlockSizeSpinner();
        maxResultsSpinner = createMaxResultsSpinner();
        alphaField = createAlphaField();

        // Insert controls into the parameter panel
        paramPanel.add(labeled(blockSizeSpinner, "Block size m:"), BorderLayout.WEST);
        JPanel rightParamBox = new JPanel(new GridLayout(2, 1, 5, 5));
        rightParamBox.add(labeled(maxResultsSpinner, "Max results:"));
        rightParamBox.add(labeled(alphaField, "Alpha:"));
        paramPanel.add(rightParamBox, BorderLayout.CENTER);

        // Button wired at the bottom of paramPanel
        JButton runButton = new JButton("Find N-tads");
        runButton.addActionListener(e -> runNtadSearchFromUI());
        paramPanel.add(runButton, BorderLayout.SOUTH);

        blockSizeSpinner.setValue(wrapper.getBlockSize());
        maxResultsSpinner.setValue(wrapper.getMaxResults());
        alphaField.setValue(wrapper.getAlpha());

        for (Node s : wrapper.getLastSelectedVars()) {
            selectedModel.addElement(s);
            availableModel.removeElement(s);
        }
    }

    /**
     * Build the left/right variable selection panel.
     */
    private JPanel buildSelectionPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));

        availableList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        selectedList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        JScrollPane leftScroll = new JScrollPane(availableList);
        JScrollPane rightScroll = new JScrollPane(selectedList);

        // >>> Make the two lists the same width <<<
        Dimension listSize = new Dimension(120, 260);
        leftScroll.setPreferredSize(listSize);
        rightScroll.setPreferredSize(listSize);

        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new GridLayout(4, 1, 5, 5));

        JButton addButton = new JButton(">");
        JButton removeButton = new JButton("<");
        JButton addAllButton = new JButton(">>");
        JButton clearButton = new JButton("<<");

        // >>> Give buttons and the button panel a minimal size so they don't vanish <<<
        Dimension buttonSize = new Dimension(50, 25);
        addButton.setMinimumSize(buttonSize);
        removeButton.setMinimumSize(buttonSize);
        addAllButton.setMinimumSize(buttonSize);
        clearButton.setMinimumSize(buttonSize);

        addButton.setPreferredSize(buttonSize);
        removeButton.setPreferredSize(buttonSize);
        addAllButton.setPreferredSize(buttonSize);
        clearButton.setPreferredSize(buttonSize);

        buttonPanel.setMinimumSize(new Dimension(60, 100));

        addButton.addActionListener(e -> moveSelected(availableList, availableModel, selectedModel));
        removeButton.addActionListener(e -> moveSelected(selectedList, selectedModel, availableModel));
        addAllButton.addActionListener(e -> moveAll(availableModel, selectedModel));
        clearButton.addActionListener(e -> moveAll(selectedModel, availableModel));

        buttonPanel.add(addButton);
        buttonPanel.add(removeButton);
        buttonPanel.add(addAllButton);
        buttonPanel.add(clearButton);

        panel.add(new JLabel("Available variables"), BorderLayout.WEST);
        panel.add(new JLabel("Selected variables"), BorderLayout.EAST);

        JPanel center = new JPanel(new BorderLayout(5, 5));
        center.add(leftScroll, BorderLayout.WEST);
        center.add(buttonPanel, BorderLayout.CENTER);
        center.add(rightScroll, BorderLayout.EAST);

        panel.add(center, BorderLayout.CENTER);

        return panel;
    }

    /**
     * Build the panel that holds parameter controls and the Run button.
     */
    private JPanel buildParameterPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createTitledBorder("N-tad parameters"));
        return panel;
    }

    private JSpinner createBlockSizeSpinner() {
        SpinnerNumberModel model = new SpinnerNumberModel(2, 1, 10, 1);
        return new JSpinner(model);
    }

    private JSpinner createMaxResultsSpinner() {
        SpinnerNumberModel model = new SpinnerNumberModel(100, 1, 100000, 10);
        return new JSpinner(model);
    }

    private JFormattedTextField createAlphaField() {
        NumberFormat format = NumberFormatUtil.getInstance().getNumberFormat();
        JFormattedTextField field = new JFormattedTextField(format);
        field.setValue(0.05);
        return field;
    }

    private JPanel labeled(JComponent comp, String label) {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.add(new JLabel(label), BorderLayout.WEST);
        panel.add(comp, BorderLayout.CENTER);
        return panel;
    }

    /**
     * Move selected elements from one list model to another.
     */
    private void moveSelected(JList<Node> fromList, DefaultListModel<Node> fromModel, DefaultListModel<Node> toModel) {
        List<Node> selected = fromList.getSelectedValuesList();
        for (Node n : selected) {
            if (!containsNode(toModel, n)) {
                toModel.addElement(n);
            }
            fromModel.removeElement(n);
        }
    }

    /**
     * Move all elements from one model to another.
     */
    private void moveAll(DefaultListModel<Node> fromModel, DefaultListModel<Node> toModel) {
        List<Node> all = new ArrayList<>();
        for (int i = 0; i < fromModel.size(); i++) {
            all.add(fromModel.get(i));
        }
        for (Node n : all) {
            if (!containsNode(toModel, n)) {
                toModel.addElement(n);
            }
            fromModel.removeElement(n);
        }
    }

    private boolean containsNode(DefaultListModel<Node> model, Node node) {
        for (int i = 0; i < model.size(); i++) {
            if (model.get(i).equals(node)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Triggered by the "Find N-tads" button: pull parameters from the UI,
     * run the wrapper's search, and refresh the table.
     */
    private void runNtadSearchFromUI() {
        int blockSize = (Integer) blockSizeSpinner.getValue();
        int maxResults = (Integer) maxResultsSpinner.getValue();

        double alpha;
        Object val = alphaField.getValue();
        if (val instanceof Number) {
            alpha = ((Number) val).doubleValue();
        } else {
            try {
                alpha = Double.parseDouble(alphaField.getText());
            } catch (NumberFormatException e) {
                alpha = 0.05;
                alphaField.setValue(alpha);
            }
        }

        // Collect selected variables
        List<Node> selectedVars = new ArrayList<>();
        for (int i = 0; i < selectedModel.size(); i++) {
            selectedVars.add(selectedModel.get(i));
        }

        wrapper.runNtadSearch(selectedVars, blockSize, maxResults, alpha);
        tableModel.fireTableDataChanged();

        wrapper.setBlockSize(blockSize);
        wrapper.setMaxResults(maxResults);
        wrapper.setAlpha(alpha);
        wrapper.setLastSelectedVars(selectedVars);
    }

    /**
     * Table model for showing NtadResult objects.
     */
    private static class NtadResultTableModel extends AbstractTableModel {

        private final NtadExplorerWrapper wrapper;
        private static final String[] COLS = {
                "Block A", "Block B", "Block size", "Rank", "p-value"
        };

        // >>> Decimal formatter for p-values <<<
        private static final DecimalFormat PV_FORMAT = new DecimalFormat("0.0000");

        public NtadResultTableModel(NtadExplorerWrapper wrapper) {
            this.wrapper = wrapper;
        }

        @Override
        public int getRowCount() {
            return wrapper.getResults().size();
        }

        @Override
        public int getColumnCount() {
            return COLS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLS[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            if (rowIndex < 0 || rowIndex >= getRowCount()) return null;

            NtadExplorer.NtadResult r = wrapper.getResults().get(rowIndex);

            return switch (columnIndex) {
                case 0 -> joinNames(r.getBlockA());
                case 1 -> joinNames(r.getBlockB());
                case 2 -> r.getBlockSize();
                case 3 -> r.getRank();
                case 4 -> PV_FORMAT.format(r.getPValue());   // formatted p-value
                default -> null;
            };
        }

        private String joinNames(List<Node> nodes) {
            if (nodes == null || nodes.isEmpty()) return "";
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < nodes.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(nodes.get(i).getName());
            }
            return sb.toString();
        }
    }
}