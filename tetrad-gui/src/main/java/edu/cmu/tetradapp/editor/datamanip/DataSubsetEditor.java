package edu.cmu.tetradapp.editor.datamanip;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetradapp.model.datamanip.DataSubsetter;
import edu.cmu.tetradapp.util.IntTextField;
import edu.cmu.tetrad.util.TMath;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
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
 *   <li>Two-list variable selector (available vs. selected). All variables start in the Selected list, so
 *       subsetting by dropping a few variables is a one-step removal.</li>
 *   <li>Sort button (below the lists) to sort both lists A–Z or restore both to dataset order; membership is
 *       unchanged, but the Selected list's order is the column order of the created subset.</li>
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
    private final JTextField conditionField = new JTextField();
    private final JLabel conditionCountLabel = new JLabel(" ");
    private final JComboBox<DataSubsetter.SamplingMode> samplingModeCombo =
            new JComboBox<>(DataSubsetter.SamplingMode.values());
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

    /**
     * Populates the variable lists. All variables start in the Selected list, in dataset order. This is a deliberate
     * design decision: the dominant use of this editor is to drop a small number of variables (e.g., ones flagged by
     * the data audit) while keeping the rest, and starting with everything selected makes that a one-step removal
     * rather than requiring the user to first move all variables across. Subsetting down to a small keep-list is
     * still one step via the "&lt;&lt;" button followed by selection or Paste.
     */
    private void initVariableModels() {
        List<Node> variables = sourceDataSet.getVariables();

        for (Node v : variables) {
            selectedModel.addElement(v);
        }
    }

    private Box buildSortPopup() {
        Box popupBox = Box.createHorizontalBox();
        JButton sortButton = new JButton("Sort");
        sortButton.setFocusable(false);
        JPopupMenu popup = buildSortPopupMenu();
        sortButton.addActionListener(e ->
                popup.show(sortButton, 0, sortButton.getHeight()));
        popupBox.add(Box.createHorizontalGlue());
        popupBox.add(sortButton);
        popupBox.add(Box.createHorizontalGlue());
        return popupBox;
    }

    private JPopupMenu buildSortPopupMenu() {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem sortItem = new JMenuItem("Sort A–Z (both lists)");
        sortItem.addActionListener(e -> sortBothAlphabetically());
        menu.add(sortItem);

        JMenuItem restoreItem = new JMenuItem("Restore dataset order (both lists)");
        restoreItem.addActionListener(e -> restoreBothOriginalOrder());
        menu.add(restoreItem);

        return menu;
    }

    /**
     * Sorts both the Available and Selected lists alphabetically by variable name, case-insensitively. Membership is
     * unchanged: no variable moves between lists. Note that the order of the Selected list is the column order of the
     * created subset, so sorting reorders the output columns.
     */
    void sortBothAlphabetically() {
        Comparator<Node> byName = Comparator.comparing(Node::getName, String.CASE_INSENSITIVE_ORDER);
        reorderModel(availableModel, byName);
        reorderModel(selectedModel, byName);
    }

    /**
     * Restores both the Available and Selected lists to the original dataset variable order. Membership is unchanged:
     * no variable moves between lists. Note that the order of the Selected list is the column order of the created
     * subset, so restoring reorders the output columns.
     */
    void restoreBothOriginalOrder() {
        Map<Node, Integer> index = new HashMap<>();

        for (int i = 0; i < originalVarOrder.size(); i++) {
            index.put(originalVarOrder.get(i), i);
        }

        Comparator<Node> byDatasetOrder =
                Comparator.comparingInt(v -> index.getOrDefault(v, Integer.MAX_VALUE));
        reorderModel(availableModel, byDatasetOrder);
        reorderModel(selectedModel, byDatasetOrder);
    }

    /**
     * Reorders the contents of the given list model by the given comparator, in place.
     */
    private static void reorderModel(DefaultListModel<Node> model, Comparator<Node> order) {
        List<Node> items = new ArrayList<>();

        for (int i = 0; i < model.size(); i++) {
            items.add(model.get(i));
        }

        items.sort(order);
        model.clear();

        for (Node v : items) {
            model.addElement(v);
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

        centerPanel.add(availableScroll);
        centerPanel.add(buttonPanel);
        centerPanel.add(selectedScroll);

        availablePanel.add(centerPanel, BorderLayout.CENTER);

        // Sort/restore acts on both lists, so it sits below the whole two-list panel rather than under one list.
        availablePanel.add(buildSortPopup(), BorderLayout.SOUTH);

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

        JLabel conditionLabel = new JLabel("Conditions:");
        conditionField.setToolTipText(
                "<html>Restrict to rows satisfying all conditions, joined by 'and'.<br>"
                        + "A = cat1&nbsp;&nbsp;A != cat1&nbsp;&nbsp;A in {cat1, cat2}&nbsp;&nbsp;A not in {cat1}<br>"
                        + "X = 1&nbsp;&nbsp;X &lt; 1&nbsp;&nbsp;X &lt;= 1&nbsp;&nbsp;X &gt; 1&nbsp;&nbsp;X &gt;= 1<br>"
                        + "X in (1, 2)&nbsp;&nbsp;X in [1, 2]&nbsp;&nbsp;X not in (1, 2]<br>"
                        + "Quote names or values containing spaces. Blank = no conditions.</html>");

        JLabel modeLabel = new JLabel("Sampling mode:");
        samplingModeCombo.addActionListener(e -> updateSamplingControls());

        DocumentListener countUpdater = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateConditionCount();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateConditionCount();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateConditionCount();
            }
        };
        conditionField.getDocument().addDocumentListener(countUpdater);
        rowSpecField.getDocument().addDocumentListener(countUpdater);
        updateConditionCount();

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

        // Row 1: conditions
        c.gridx = 0;
        c.gridy = 1;
        c.anchor = GridBagConstraints.EAST;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        panel.add(conditionLabel, c);
        c.gridx = 1;
        c.gridy = 1;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;
        panel.add(conditionField, c);
        c.gridx = 2;
        c.gridy = 1;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        panel.add(conditionCountLabel, c);

        // Row 2: sampling mode
        c.gridx = 0;
        c.gridy = 2;
        c.anchor = GridBagConstraints.EAST;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        panel.add(modeLabel, c);
        c.gridx = 1;
        c.gridy = 2;
        c.anchor = GridBagConstraints.WEST;
        panel.add(samplingModeCombo, c);

        // Row 3: sample size
        c.gridx = 0;
        c.gridy = 3;
        c.anchor = GridBagConstraints.EAST;
        panel.add(sampleSizeLabel, c);
        c.gridx = 1;
        c.gridy = 3;
        c.anchor = GridBagConstraints.WEST;
        panel.add(sampleSizeSpinner, c);

        // Row 4: seed
        c.gridx = 0;
        c.gridy = 4;
        c.anchor = GridBagConstraints.EAST;
        panel.add(seedLabel, c);
        c.gridx = 1;
        c.gridy = 4;
        c.anchor = GridBagConstraints.WEST;
        panel.add(seedField, c);

        return panel;
    }

    private void updateSampleSizeDefault() {
        int n = sourceDataSet.getNumRows();
        sampleSizeSpinner.setValue(n);
    }

    private void updateSamplingControls() {
        DataSubsetter.SamplingMode mode = (DataSubsetter.SamplingMode) samplingModeCombo.getSelectedItem();
        boolean needsSize = (mode == DataSubsetter.SamplingMode.SUBSAMPLE || mode == DataSubsetter.SamplingMode.BOOTSTRAP);

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
        list.setSelectedIndices(Arrays.stream(indices).map(i -> TMath.max(i - 1, 0)).toArray());
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
    // Row count feedback (parsing and subsetting live in DataSubsetter)
    // ------------------------------------------------------------------------

    /**
     * Updates the label beside the condition field with the number of rows surviving the row
     * specification and the conditions, or with a short indication that the specification does not
     * yet parse.
     */
    private void updateConditionCount() {
        try {
            int[] counts = DataSubsetter.countRows(sourceDataSet, rowSpecField.getText(), conditionField.getText());
            conditionCountLabel.setText(counts[0] + " of " + counts[1] + " rows");
            conditionCountLabel.setToolTipText(null);
        } catch (IllegalArgumentException e) {
            conditionCountLabel.setText("\u2014");
            conditionCountLabel.setToolTipText(e.getMessage());
        }
    }

    // ------------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------------

    /**
     * Returns the current editor settings as a {@link DataSubsetter.Spec}. This is what {@code DataSubsetModel}
     * stores and later re-applies to whatever data set is upstream at propagation time.
     *
     * @return the spec.
     */
    public DataSubsetter.Spec getSpec() {
        return new DataSubsetter.Spec(getSelectedVariableNames(), getRowSpec(), getConditionSpec(),
                getSamplingMode(), getSampleSize(), getSeedText());
    }

    /**
     * Returns a new DataSet that is a subset / resample of the sourceDataSet, according to the current editor
     * settings. Delegates to {@link DataSubsetter#subset(DataSet, DataSubsetter.Spec)}.
     * <p>
     * An invalid row specification is reported in a dialog and then tolerated by falling back to all rows. An
     * invalid condition is NOT tolerated and propagates as an {@link IllegalArgumentException}: falling back to all
     * rows there would silently analyze the whole data set while the user believed it had been restricted.
     *
     * @return the subset.
     */
    public DataSet createSubset() {
        DataSubsetter.Spec spec = getSpec();

        try {
            DataSubsetter.parseRowSpec(spec.rowSpec(), sourceDataSet.getNumRows());
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(
                    JOptionUtils.centeringComp(),
                    "Invalid row specification:\n" + e.getMessage(),
                    "Row specification error",
                    JOptionPane.ERROR_MESSAGE
            );
            spec = new DataSubsetter.Spec(spec.selectedVarNames(), "", spec.conditionSpec(),
                    spec.samplingMode(), spec.sampleSize(), spec.seedText());
        }

        return DataSubsetter.subset(sourceDataSet, spec);
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
     * The raw condition specification string, e.g. "A = cat1 and X in (1, 2]".
     *
     * @return The condition specification string
     */
    public String getConditionSpec() {
        return conditionField.getText();
    }

    /**
     * Sets the condition specification string.
     *
     * @param spec The condition specification string
     */
    public void setConditionSpec(String spec) {
        conditionField.setText(spec == null ? "" : spec);
        updateConditionCount();
    }

    /**
     * The currently selected sampling mode.
     *
     * @return The sampling mode
     */
    public DataSubsetter.SamplingMode getSamplingMode() {
        return (DataSubsetter.SamplingMode) samplingModeCombo.getSelectedItem();
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
            DataSubsetter.SamplingMode samplingMode,
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

}