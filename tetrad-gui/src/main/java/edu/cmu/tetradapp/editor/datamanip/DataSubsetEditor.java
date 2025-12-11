package edu.cmu.tetradapp.editor.datamanip;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetradapp.util.IntTextField;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Editor for creating a subset or resample of a {@link edu.cmu.tetrad.data.DataSet}.
 * <p>
 * Features:
 * <ul>
 *   <li>Two-list variable selector (available vs. selected).</li>
 *   <li>Row selection via comma-separated ranges (1-based),
 *       e.g. {@code "1-100, 150, 200-250"}.</li>
 *   <li>Sampling modes: use as-is, shuffle, subsample, or bootstrap.</li>
 *   <li>Sample size and random seed controls for reproducibility.</li>
 *   <li>"Paste variable list..." button to paste variable names and auto-select them.</li>
 * </ul>
 * <p>
 * The {@link #createSubset()} method returns a new {@link edu.cmu.tetrad.data.DataSet}
 * with the chosen variables (in the chosen order) and rows (possibly resampled).
 */
public class DataSubsetEditor extends JPanel {

    private final DataSet sourceDataSet;

    // Variable selection models and lists.
    private final DefaultListModel<Node> availableModel = new DefaultListModel<>();
    private final DefaultListModel<Node> selectedModel = new DefaultListModel<>();
    private final JList<Node> availableList = new JList<>(availableModel);
    private final JList<Node> selectedList = new JList<>(selectedModel);

    // Row & sampling controls.
    private final JTextField rowSpecField = new JTextField();
    private final JComboBox<SamplingMode> samplingModeCombo =
            new JComboBox<>(SamplingMode.values());
    private final JSpinner sampleSizeSpinner =
            new JSpinner(new SpinnerNumberModel(0, 0, Integer.MAX_VALUE, 1));
    private final JTextField seedField = new IntTextField(40, 6);
    private final List<Node> originalVarOrder;

    // Button to paste variable names.
    private final JButton pasteVarListButton = new JButton("Paste...");

    /**
     * Constructs a new DataSubsetEditor, initializing it with the provided data set.
     * This editor allows for defining a subset or resampling of the dataset by configuring
     * variables, row specifications, sampling modes, and sample sizes.
     *
     * @param dataSet the data set to be managed and edited. Must not be null.
     *                It provides the variables and data for the subset editor to work with.
     */
    public DataSubsetEditor(DataSet dataSet) {
        this.sourceDataSet = Objects.requireNonNull(dataSet, "dataSet");
        this.originalVarOrder = new ArrayList<>(dataSet.getVariables());

        setPreferredSize(new Dimension(600, 600));

        initVariableModels();
        initGui();
        updateSampleSizeDefault();
        updateSamplingControls();
    }

    // ------------------------------------------------------------------------
    // GUI construction
    // ------------------------------------------------------------------------

    private void initVariableModels() {
        List<Node> variables = sourceDataSet.getVariables();

        for (Node v : variables) {
            availableModel.addElement(v);
        }
    }

    private Box buildSortPopup() {
        Box popupBox = Box.createHorizontalBox();
        JButton sortButton = new JButton("Sort");
        sortButton.setFocusable(false);
        JPopupMenu popup = buildAvailablePopupMenu();
        sortButton.addActionListener(e ->
                popup.show(sortButton, 0, sortButton.getHeight()));
        popupBox.add(sortButton);
        return popupBox;
    }

    private JPopupMenu buildAvailablePopupMenu() {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem sortItem = new JMenuItem("Sort A–Z");
        sortItem.addActionListener(e -> sortAvailableAlphabetically());
        menu.add(sortItem);

        JMenuItem restoreItem = new JMenuItem("Restore dataset order");
        restoreItem.addActionListener(e -> restoreAvailableOriginalOrder());
        menu.add(restoreItem);

        return menu;
    }

    private void sortAvailableAlphabetically() {
        // Extract current available nodes into a list
        List<Node> avail = new ArrayList<>();
        for (int i = 0; i < availableModel.size(); i++) {
            avail.add(availableModel.get(i));
        }

        // Sort by name
        avail.sort(Comparator.comparing(Node::getName, String.CASE_INSENSITIVE_ORDER));

        // Rebuild the model
        availableModel.clear();
        for (Node v : avail) {
            availableModel.addElement(v);
        }
    }

    private void restoreAvailableOriginalOrder() {
        // Selected nodes should stay selected; we only reorder what's in Available.
        Set<Node> selectedNodes = new LinkedHashSet<>();
        for (int i = 0; i < selectedModel.size(); i++) {
            selectedNodes.add(selectedModel.get(i));
        }

        availableModel.clear();
        for (Node v : originalVarOrder) {
            if (!selectedNodes.contains(v)) {
                availableModel.addElement(v);
            }
        }
    }

    private void initGui() {
        setLayout(new BorderLayout(10, 10));

        JPanel variablesPanel = buildVariablesPanel();
        JPanel rowsPanel = buildRowsPanel();

        add(variablesPanel, BorderLayout.CENTER);
        add(rowsPanel, BorderLayout.SOUTH);
    }

    private JPanel buildVariablesPanel() {

        // Left list (available).
        availableList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        JScrollPane availableScroll = new JScrollPane(availableList);
        availableScroll.setPreferredSize(new Dimension(225, 600));
        availableScroll.setBorder(new TitledBorder("Available variables"));

        JPanel availablePanel = new JPanel();
        availablePanel.setLayout(new BorderLayout());
        availablePanel.add(availableScroll, BorderLayout.CENTER);
        availablePanel.setBorder(new TitledBorder("Variables"));

        // Right list (selected).
        selectedList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        JScrollPane selectedScroll = new JScrollPane(selectedList);
        selectedScroll.setPreferredSize(new Dimension(225, 600));
        selectedScroll.setBorder(new TitledBorder("Selected variables"));

        // Middle buttons.
        Box buttonPanel = Box.createVerticalBox();

        JButton addButton = new JButton(">");
        JButton removeButton = new JButton("<");
        JButton addAllButton = new JButton(">>");
        JButton removeAllButton = new JButton("<<");
        JButton upButton = new JButton("Move Up");
        JButton downButton = new JButton("Move Down");

        addButton.addActionListener(e -> moveSelected(availableList, availableModel, selectedModel));
        removeButton.addActionListener(e -> moveSelected(selectedList, selectedModel, availableModel));
        addAllButton.addActionListener(e -> moveAll(availableModel, selectedModel));
        removeAllButton.addActionListener(e -> moveAll(selectedModel, availableModel));
        upButton.addActionListener(e -> moveSelectedUp(selectedList, selectedModel));
        downButton.addActionListener(e -> moveSelectedDown(selectedList, selectedModel));

        pasteVarListButton.addActionListener(e -> showPasteVariableListDialog());

        buttonPanel.add(Box.createVerticalGlue());
        buttonPanel.add(center(addButton));
        buttonPanel.add(Box.createVerticalStrut(5));
        buttonPanel.add(center(removeButton));
        buttonPanel.add(Box.createVerticalStrut(5));
        buttonPanel.add(center(addAllButton));
        buttonPanel.add(Box.createVerticalStrut(5));
        buttonPanel.add(center(removeAllButton));
        buttonPanel.add(Box.createVerticalStrut(15));
        buttonPanel.add(center(upButton));
        buttonPanel.add(Box.createVerticalStrut(5));
        buttonPanel.add(center(downButton));
        buttonPanel.add(Box.createVerticalStrut(15));
        buttonPanel.add(center(pasteVarListButton));
        buttonPanel.add(Box.createVerticalGlue());

        Box centerPanel = Box.createHorizontalBox();

        Box available = Box.createVerticalBox();
        available.add(availableScroll);
        available.add(buildSortPopup());

        centerPanel.add(available);
        centerPanel.add(buttonPanel);
        centerPanel.add(selectedScroll);

        availablePanel.add(centerPanel, BorderLayout.CENTER);

        return availablePanel;
    }

    private Box center(JComponent component) {
        Box box = Box.createHorizontalBox();
        box.add(Box.createHorizontalGlue());
        box.add(component);
        box.add(Box.createHorizontalGlue());
        return box;
    }

    private JPanel buildRowsPanel() {
        JPanel panel = new JPanel();
        panel.setBorder(new TitledBorder("Rows and sampling"));
        panel.setLayout(new GridBagLayout());

        JLabel rowSpecLabel = new JLabel("Rows:");
        rowSpecField.setToolTipText("Comma-separated ranges, e.g. 1-100, 150, 200-250; blank = all rows");

        JLabel modeLabel = new JLabel("Sampling mode:");
        samplingModeCombo.addActionListener(e -> updateSamplingControls());

        JLabel sampleSizeLabel = new JLabel("Sample size:");
        JLabel seedLabel = new JLabel("Seed:");

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 3, 3, 3);

        // Row 0: rows
        c.gridx = 0;
        c.gridy = 0;
        c.anchor = GridBagConstraints.EAST;
        panel.add(rowSpecLabel, c);
        c.gridx = 1;
        c.gridy = 0;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;
        panel.add(rowSpecField, c);

        // Row 1: sampling mode
        c.gridx = 0;
        c.gridy = 1;
        c.anchor = GridBagConstraints.EAST;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        panel.add(modeLabel, c);
        c.gridx = 1;
        c.gridy = 1;
        c.anchor = GridBagConstraints.WEST;
        panel.add(samplingModeCombo, c);

        // Row 2: sample size
        c.gridx = 0;
        c.gridy = 2;
        c.anchor = GridBagConstraints.EAST;
        panel.add(sampleSizeLabel, c);
        c.gridx = 1;
        c.gridy = 2;
        c.anchor = GridBagConstraints.WEST;
        panel.add(sampleSizeSpinner, c);

        // Row 3: seed
        c.gridx = 0;
        c.gridy = 3;
        c.anchor = GridBagConstraints.EAST;
        panel.add(seedLabel, c);
        c.gridx = 1;
        c.gridy = 3;
        c.anchor = GridBagConstraints.WEST;
        panel.add(seedField, c);

        return panel;
    }

    private void updateSampleSizeDefault() {
        int n = sourceDataSet.getNumRows();
        sampleSizeSpinner.setValue(n);
    }

    private void updateSamplingControls() {
        SamplingMode mode = (SamplingMode) samplingModeCombo.getSelectedItem();
        boolean needsSize = (mode == SamplingMode.SUBSAMPLE || mode == SamplingMode.BOOTSTRAP);

        sampleSizeSpinner.setEnabled(needsSize);
        if (!needsSize) {
            updateSampleSizeDefault();
        }
    }

    // ------------------------------------------------------------------------
    // Selection helpers
    // ------------------------------------------------------------------------

    private void moveSelected(JList<Node> fromList,
                              DefaultListModel<Node> fromModel,
                              DefaultListModel<Node> toModel) {
        List<Node> selected = fromList.getSelectedValuesList();
        for (Node n : selected) {
            if (!containsNode(toModel, n)) {
                toModel.addElement(n);
            }
            fromModel.removeElement(n);
        }
    }

    private void moveAll(DefaultListModel<Node> fromModel,
                         DefaultListModel<Node> toModel) {
        for (int i = 0; i < fromModel.size(); i++) {
            Node n = fromModel.get(i);
            if (!containsNode(toModel, n)) {
                toModel.addElement(n);
            }
        }
        fromModel.clear();
    }

    private boolean containsNode(DefaultListModel<Node> model, Node node) {
        for (int i = 0; i < model.size(); i++) {
            if (model.get(i) == node) {
                return true;
            }
        }
        return false;
    }

    private void moveSelectedUp(JList<Node> list, DefaultListModel<Node> model) {
        int[] indices = list.getSelectedIndices();
        if (indices.length == 0) return;

        Arrays.sort(indices);
        for (int index : indices) {
            if (index > 0) {
                Node n = model.get(index);
                model.remove(index);
                model.add(index - 1, n);
            }
        }
        list.setSelectedIndices(Arrays.stream(indices).map(i -> Math.max(i - 1, 0)).toArray());
    }

    private void moveSelectedDown(JList<Node> list, DefaultListModel<Node> model) {
        int[] indices = list.getSelectedIndices();
        if (indices.length == 0) return;

        Arrays.sort(indices);
        for (int i = indices.length - 1; i >= 0; i--) {
            int index = indices[i];
            if (index < model.size() - 1) {
                Node n = model.get(index);
                model.remove(index);
                model.add(index + 1, n);
                indices[i] = index + 1;
            }
        }
        list.setSelectedIndices(indices);
    }

    // ------------------------------------------------------------------------
    // "Paste variable list..." behavior
    // ------------------------------------------------------------------------

    private void showPasteVariableListDialog() {
        JTextArea area = new JTextArea(10, 40);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);

        String message = "Paste comma-, tab-, space-separated, or line-separated variable names.\n" +
                         "Example: X1, X2, X3 or X1 X2 X3 or one per line.";

        int result = JOptionPane.showConfirmDialog(
                JOptionUtils.centeringComp(),
                new Object[]{message, new JScrollPane(area)},
                "Paste variable list",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );

        if (result != JOptionPane.OK_OPTION) {
            return;
        }

        String text = area.getText();
        if (text == null || text.trim().isEmpty()) {
            return;
        }

        // Normalize separators: commas, tabs, and newlines -> spaces.
        String normalized = text.replace(',', ' ')
                .replace('\t', ' ')
                .replace('\r', ' ')
                .replace('\n', ' ');

        String[] tokens = normalized.split("\\s+");
        List<String> pastedNames = Arrays.stream(tokens)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        if (pastedNames.isEmpty()) {
            return;
        }

        // Build lookup map from variable name to Node.
        Map<String, Node> byName = new LinkedHashMap<>();
        for (int i = 0; i < availableModel.size(); i++) {
            Node v = availableModel.get(i);
            byName.put(v.getName(), v);
        }
        for (int i = 0; i < selectedModel.size(); i++) {
            Node v = selectedModel.get(i);
            byName.putIfAbsent(v.getName(), v);
        }

        List<String> missing = new ArrayList<>();

        for (String name : pastedNames) {
            Node v = byName.get(name);
            if (v == null) {
                missing.add(name);
            } else {
                // Ensure it's in selectedModel once, in the pasted order.
                // Remove from available if present.
                availableModel.removeElement(v);

                // If already in selected, remove and re-add to enforce order.
                selectedModel.removeElement(v);
                selectedModel.addElement(v);
            }
        }

        if (!missing.isEmpty()) {
            StringBuilder msg = new StringBuilder();
            msg.append("The following variables were not found in the data:\n\n");
            for (String name : missing) {
                msg.append(name).append("\n");
            }
            JTextArea reportArea = new JTextArea(msg.toString(), 10, 40);
            reportArea.setEditable(false);
            JOptionPane.showMessageDialog(
                    JOptionUtils.centeringComp(),
                    new JScrollPane(reportArea),
                    "Variables not found",
                    JOptionPane.INFORMATION_MESSAGE
            );
        }
    }

    // ------------------------------------------------------------------------
    // Row spec parsing and sampling
    // ------------------------------------------------------------------------

    /**
     * Parse the row specification (1-based ranges) into a sorted, duplicate-free list of 0-based row indices.
     * <p>
     * If the spec is blank, returns all rows [0..numRows-1].
     * <p>
     * Throws IllegalArgumentException if the spec is invalid.
     */
    private List<Integer> parseRowSpec(String spec, int numRows) {
        if (spec == null || spec.trim().isEmpty()) {
            List<Integer> all = new ArrayList<>(numRows);
            for (int i = 0; i < numRows; i++) {
                all.add(i);
            }
            return all;
        }

        Set<Integer> indices = new TreeSet<>(); // sorted, deduped

        String[] parts = spec.split(",");
        for (String part : parts) {
            String p = part.trim();
            if (p.isEmpty()) continue;

            if (p.contains("-")) {
                String[] ab = p.split("-");
                if (ab.length != 2) {
                    throw new IllegalArgumentException("Invalid range: \"" + p + "\"");
                }
                String aStr = ab[0].trim();
                String bStr = ab[1].trim();
                if (aStr.isEmpty() || bStr.isEmpty()) {
                    throw new IllegalArgumentException("Invalid range: \"" + p + "\"");
                }

                int a = Integer.parseInt(aStr);
                int b = Integer.parseInt(bStr);
                if (a < 1 || b < 1 || a > b || b > numRows) {
                    throw new IllegalArgumentException("Row range out of bounds: \"" + p + "\"");
                }

                for (int r = a; r <= b; r++) {
                    indices.add(r - 1); // convert to 0-based
                }
            } else {
                int r = Integer.parseInt(p);
                if (r < 1 || r > numRows) {
                    throw new IllegalArgumentException("Row index out of bounds: " + r);
                }
                indices.add(r - 1);
            }
        }

        return new ArrayList<>(indices);
    }

    private List<Integer> applySampling(List<Integer> baseRows) {
        SamplingMode mode = (SamplingMode) samplingModeCombo.getSelectedItem();
        if (mode == null) {
            mode = SamplingMode.USE_AS_IS;
        }

        int n = baseRows.size();
        if (n == 0) {
            return baseRows;
        }

        int sampleSize = (Integer) sampleSizeSpinner.getValue();
        if (sampleSize <= 0) {
            sampleSize = n;
        }

        Long seed = null;
        String seedText = seedField.getText();
        if (seedText != null && !seedText.trim().isEmpty()) {
            try {
                seed = Long.parseLong(seedText.trim());
            } catch (NumberFormatException ignored) {
                // If seed is invalid, just ignore and use default randomness.
            }
        }
        Random random = (seed == null) ? new Random() : new Random(seed);

        switch (mode) {
            case USE_AS_IS:
                return new ArrayList<>(baseRows);

            case SHUFFLE: {
                List<Integer> shuffled = new ArrayList<>(baseRows);
                Collections.shuffle(shuffled, random);
                return shuffled;
            }

            case SUBSAMPLE: {
                // Subsample without replacement.
                if (sampleSize > n) {
                    sampleSize = n;
                }
                List<Integer> temp = new ArrayList<>(baseRows);
                Collections.shuffle(temp, random);
                return new ArrayList<>(temp.subList(0, sampleSize));
            }

            case BOOTSTRAP: {
                List<Integer> boot = new ArrayList<>(sampleSize);
                for (int i = 0; i < sampleSize; i++) {
                    int idx = random.nextInt(n);
                    boot.add(baseRows.get(idx));
                }
                return boot;
            }

            default:
                return new ArrayList<>(baseRows);
        }
    }

    // ------------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------------

    /**
     * Returns a new DataSet that is a subset / resample of the sourceDataSet, according to the current editor
     * settings.
     */
    public DataSet createSubset() {
        // 1. Determine selected variables.
        List<Node> selectedVars = new ArrayList<>();
        for (int i = 0; i < selectedModel.size(); i++) {
            selectedVars.add(selectedModel.get(i));
        }

        if (selectedVars.isEmpty()) {
            // If nothing selected, default to all variables.
            selectedVars.addAll(sourceDataSet.getVariables());
        }

        // 2. Determine row indices (0-based), including sampling.
        List<Integer> baseRows;
        try {
            baseRows = parseRowSpec(rowSpecField.getText(), sourceDataSet.getNumRows());
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(
                    JOptionUtils.centeringComp(),
                    "Invalid row specification:\n" + e.getMessage(),
                    "Row specification error",
                    JOptionPane.ERROR_MESSAGE
            );
            // Fallback: all rows, no sampling.
            baseRows = new ArrayList<>();
            for (int i = 0; i < sourceDataSet.getNumRows(); i++) {
                baseRows.add(i);
            }
        }

        List<Integer> finalRows = applySampling(baseRows);

        // 3. Create the subset DataSet.
        DataSet columnSubset = sourceDataSet.subsetColumns(selectedVars);
        return columnSubset.subsetRows(finalRows);
    }

    // ------------------------------------------------------------------------
    // Public API – state accessors
    // ------------------------------------------------------------------------

    /**
     * Names of selected variables, in the order shown in the Selected list.
     *
     * @return List of variable names
     */
    public java.util.List<String> getSelectedVariableNames() {
        java.util.List<String> names = new java.util.ArrayList<>();
        for (int i = 0; i < selectedModel.size(); i++) {
            names.add(selectedModel.get(i).getName());
        }
        return names;
    }

    /**
     * The raw row specification string, e.g. "1-100, 150, 200-250".
     *
     * @return The row specification string
     */
    public String getRowSpec() {
        return rowSpecField.getText();
    }

    /**
     * The currently selected sampling mode.
     *
     * @return The sampling mode
     */
    public SamplingMode getSamplingMode() {
        return (SamplingMode) samplingModeCombo.getSelectedItem();
    }

    /**
     * The current sample size from the spinner.
     *
     * @return The sample size.
     */
    public int getSampleSize() {
        Object value = sampleSizeSpinner.getValue();
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return sourceDataSet.getNumRows();
    }

    /**
     * The seed field contents as text.
     *
     * @return The seed text
     */
    public String getSeedText() {
        return seedField.getText();
    }

    /**
     * Apply previously saved state to the editor. Any argument may be null to
     * leave the corresponding control at its default.
     *
     * @param selectedVarNames names of variables that should appear in the
     *                         Selected list (in this order).
     * @param rowSpec          row specification string, or null.
     * @param samplingMode     sampling mode, or null.
     * @param sampleSize       sample size, or null.
     * @param seedText         seed as text, or null.
     */
    public void applyState(
            java.util.List<String> selectedVarNames,
            String rowSpec,
            SamplingMode samplingMode,
            Integer sampleSize,
            String seedText) {

        // Restore variable selection.
        if (selectedVarNames != null && !selectedVarNames.isEmpty()) {
            availableModel.clear();
            selectedModel.clear();

            java.util.List<Node> allVars = sourceDataSet.getVariables();
            java.util.Map<String, Node> byName = new java.util.LinkedHashMap<>();

            for (Node v : allVars) {
                byName.put(v.getName(), v);
            }

            java.util.Set<Node> selectedNodes = new java.util.LinkedHashSet<>();

            // Add selected in the saved order, skipping any that no longer exist.
            for (String name : selectedVarNames) {
                Node v = byName.get(name);
                if (v != null && !selectedNodes.contains(v)) {
                    selectedNodes.add(v);
                }
            }

            for (Node v : selectedNodes) {
                selectedModel.addElement(v);
            }

            // Everything else goes in Available, preserving dataset order.
            for (Node v : allVars) {
                if (!selectedNodes.contains(v)) {
                    availableModel.addElement(v);
                }
            }
        }

        if (rowSpec != null) {
            rowSpecField.setText(rowSpec);
        }

        if (samplingMode != null) {
            samplingModeCombo.setSelectedItem(samplingMode);
        }

        if (sampleSize != null && sampleSize > 0) {
            sampleSizeSpinner.setValue(sampleSize);
        }

        if (seedText != null) {
            seedField.setText(seedText);
        }
    }

    // ------------------------------------------------------------------------
    // Sampling mode enum
    // ------------------------------------------------------------------------

    /**
     * Sampling modes for subset creation.
     */
    public enum SamplingMode {

        /**
         * Sampling mode that uses rows as they are without applying any modifications.
         */
        USE_AS_IS("Use rows as-is"),

        /**
         * Sampling mode that randomizes the order of rows.
         */
        SHUFFLE("Shuffle rows"),

        /**
         * Represents a sampling mode that selects a subset of data without replacement.
         * This means each selected element is unique and will not appear more than once
         * in the sampled subset.
         */
        SUBSAMPLE("Subsample (without replacement)"),

        /**
         * Sampling mode that selects a subset of data with replacement.
         * This means elements can be selected multiple times, potentially leading to duplicates
         * in the sampled subset.
         */
        BOOTSTRAP("Bootstrap (with replacement)");

        private final String label;

        /**
         * Constructs a SamplingMode with the specified label.
         *
         * @param label the string label representing this sampling mode.
         */
        SamplingMode(String label) {
            this.label = label;
        }

        /**
         * Returns the string representation of this sampling mode.
         *
         * @return the string label associated with this sampling mode.
         */
        @Override
        public String toString() {
            return label;
        }
    }
}