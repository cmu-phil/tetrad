package edu.cmu.tetradapp.editor.datamanip;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetrad.util.RandomUtil;
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

        switch (mode) {
            case USE_AS_IS:
                return new ArrayList<>(baseRows);

            case SHUFFLE: {
                List<Integer> shuffled = new ArrayList<>(baseRows);
                RandomUtil.shuffle(shuffled);
                return shuffled;
            }

            case SUBSAMPLE: {
                // Subsample without replacement.
                if (sampleSize > n) {
                    sampleSize = n;
                }
                List<Integer> temp = new ArrayList<>(baseRows);
                RandomUtil.shuffle(temp);
                return new ArrayList<>(temp.subList(0, sampleSize));
            }

            case BOOTSTRAP: {
                List<Integer> boot = new ArrayList<>(sampleSize);
                for (int i = 0; i < sampleSize; i++) {
                    int idx = RandomUtil.getInstance().nextInt(n);
                    boot.add(baseRows.get(idx));
                }
                return boot;
            }

            default:
                return new ArrayList<>(baseRows);
        }
    }

    // ------------------------------------------------------------------------
    // Row conditions
    // ------------------------------------------------------------------------

    /**
     * The comparison used by a single row condition.
     */
    private enum ConditionOp {
        EQ, NE, LT, LE, GT, GE, IN_SET, NOT_IN_SET, IN_INTERVAL, NOT_IN_INTERVAL
    }

    /**
     * A single parsed row condition, bound to a column of the source data set.
     *
     * <p>Rows whose value for the condition's variable is missing never satisfy the condition, for
     * any operator -- including the negated ones. A missing value is not knowably outside a set or a
     * range, so {@code A != cat1} excludes missing rows just as {@code A = cat1} does.
     */
    private static final class RowCondition {
        private final Node variable;
        private final int column;
        private final ConditionOp op;
        private final boolean discrete;

        /**
         * Category indices, for the discrete operators.
         */
        private final Set<Integer> categoryIndices;

        /**
         * Comparison values, for the continuous scalar and set operators.
         */
        private final double[] values;

        /**
         * Interval bounds and closure, for the interval operators.
         */
        private final double low;
        private final double high;
        private final boolean lowClosed;
        private final boolean highClosed;

        private RowCondition(Node variable, int column, ConditionOp op, boolean discrete,
                             Set<Integer> categoryIndices, double[] values,
                             double low, double high, boolean lowClosed, boolean highClosed) {
            this.variable = variable;
            this.column = column;
            this.op = op;
            this.discrete = discrete;
            this.categoryIndices = categoryIndices;
            this.values = values;
            this.low = low;
            this.high = high;
            this.lowClosed = lowClosed;
            this.highClosed = highClosed;
        }

        static RowCondition discrete(Node variable, int column, ConditionOp op, Set<Integer> indices) {
            return new RowCondition(variable, column, op, true, indices, null, 0, 0, false, false);
        }

        static RowCondition continuous(Node variable, int column, ConditionOp op, double[] values) {
            return new RowCondition(variable, column, op, false, null, values, 0, 0, false, false);
        }

        static RowCondition interval(Node variable, int column, ConditionOp op,
                                     double low, double high, boolean lowClosed, boolean highClosed) {
            return new RowCondition(variable, column, op, false, null, null,
                    low, high, lowClosed, highClosed);
        }

        boolean holds(DataSet data, int row) {
            if (discrete) {
                int v = data.getInt(row, column);
                if (v == DiscreteVariable.MISSING_VALUE || v < 0) return false;

                boolean in = categoryIndices.contains(v);
                return (op == ConditionOp.NOT_IN_SET || op == ConditionOp.NE) != in;
            }

            double v = data.getDouble(row, column);
            if (Double.isNaN(v)) return false;

            switch (op) {
                case EQ:
                    return v == values[0];
                case NE:
                    return v != values[0];
                case LT:
                    return v < values[0];
                case LE:
                    return v <= values[0];
                case GT:
                    return v > values[0];
                case GE:
                    return v >= values[0];
                case IN_SET:
                case NOT_IN_SET: {
                    boolean in = false;
                    for (double value : values) {
                        if (v == value) {
                            in = true;
                            break;
                        }
                    }
                    return (op == ConditionOp.NOT_IN_SET) != in;
                }
                case IN_INTERVAL:
                case NOT_IN_INTERVAL: {
                    boolean in = (lowClosed ? v >= low : v > low)
                            && (highClosed ? v <= high : v < high);
                    return (op == ConditionOp.NOT_IN_INTERVAL) != in;
                }
                default:
                    return false;
            }
        }
    }

    /**
     * Parses a condition specification into a list of conditions, all of which must hold for a row to
     * be kept.
     *
     * <p>Conditions are joined by the keyword {@code and}; {@code in}, {@code not in} and {@code and}
     * are recognized in any capitalization. Variable names and category values are matched as typed,
     * falling back to a case-insensitive match when that is unambiguous. Names or values containing
     * spaces, commas or keywords may be double-quoted.
     *
     * <pre>
     *   A = cat1                     discrete equality
     *   A != cat1                    discrete inequality
     *   A in {cat1, cat2}            discrete set membership
     *   A not in {cat1, cat2}        discrete set exclusion
     *   X = 1                        continuous equality (exact)
     *   X &lt; 1   X &lt;= 1  X &gt; 1  X &gt;= 1  continuous comparison
     *   X in (1, 2)                  open interval; [ or ] closes an endpoint
     *   X not in [1, 2]              interval exclusion
     *   X in {1, 2.5}                exact match against any listed value
     *   A = cat1 and X in (1, 2]     conjunction
     * </pre>
     *
     * @param spec the specification; blank means no conditions
     * @return the parsed conditions, possibly empty
     * @throws IllegalArgumentException if the specification cannot be parsed, names an unknown
     *                                  variable or category, or applies an operator to a variable of
     *                                  the wrong type
     */
    private List<RowCondition> parseConditions(String spec) {
        List<RowCondition> conditions = new ArrayList<>();
        if (spec == null || spec.trim().isEmpty()) return conditions;

        for (String clause : splitOnAnd(spec)) {
            String c = clause.trim();
            if (c.isEmpty()) continue;
            conditions.add(parseCondition(c));
        }

        return conditions;
    }

    /**
     * Splits a specification on the keyword {@code and}, ignoring occurrences inside quotes or inside
     * a bracketed set or interval, and requiring word boundaries so that a variable named e.g.
     * "brand" is not mistaken for a separator.
     */
    private static List<String> splitOnAnd(String spec) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        int depth = 0;
        boolean inQuotes = false;

        for (int i = 0; i < spec.length(); i++) {
            char ch = spec.charAt(i);

            if (ch == '"') {
                inQuotes = !inQuotes;
                current.append(ch);
                continue;
            }

            if (!inQuotes) {
                if (ch == '{' || ch == '(' || ch == '[') depth++;
                else if (ch == '}' || ch == ')' || ch == ']') depth--;

                if (depth == 0 && (ch == 'a' || ch == 'A') && matchesKeywordAt(spec, i, "and")) {
                    parts.add(current.toString());
                    current.setLength(0);
                    i += 2;
                    continue;
                }
            }

            current.append(ch);
        }

        if (inQuotes) {
            throw new IllegalArgumentException("Unbalanced quotation marks.");
        }
        if (depth != 0) {
            throw new IllegalArgumentException("Unbalanced brackets.");
        }

        parts.add(current.toString());
        return parts;
    }

    /**
     * True if the keyword occurs at the given index, case-insensitively, bounded by non-word
     * characters on both sides.
     */
    private static boolean matchesKeywordAt(String s, int i, String keyword) {
        int end = i + keyword.length();
        if (end > s.length()) return false;
        if (!s.substring(i, end).equalsIgnoreCase(keyword)) return false;
        if (i > 0 && isWordChar(s.charAt(i - 1))) return false;
        return end >= s.length() || !isWordChar(s.charAt(end));
    }

    private static boolean isWordChar(char ch) {
        return Character.isLetterOrDigit(ch) || ch == '_' || ch == '.';
    }

    /**
     * Parses one condition clause.
     */
    private RowCondition parseCondition(String clause) {
        // Locate the operator: the earliest one occurring outside quotes. Two-character
        // operators are tested before their one-character prefixes.
        int opIndex = -1;
        int opLength = 0;
        ConditionOp op = null;
        boolean negatedKeyword = false;

        boolean inQuotes = false;

        for (int i = 0; i < clause.length() && opIndex < 0; i++) {
            char ch = clause.charAt(i);

            if (ch == '"') {
                inQuotes = !inQuotes;
                continue;
            }
            if (inQuotes) continue;

            if (clause.startsWith("!=", i)) {
                opIndex = i;
                opLength = 2;
                op = ConditionOp.NE;
            } else if (clause.startsWith("<=", i)) {
                opIndex = i;
                opLength = 2;
                op = ConditionOp.LE;
            } else if (clause.startsWith(">=", i)) {
                opIndex = i;
                opLength = 2;
                op = ConditionOp.GE;
            } else if (ch == '=') {
                opIndex = i;
                opLength = 1;
                op = ConditionOp.EQ;
            } else if (ch == '<') {
                opIndex = i;
                opLength = 1;
                op = ConditionOp.LT;
            } else if (ch == '>') {
                opIndex = i;
                opLength = 1;
                op = ConditionOp.GT;
            } else if (matchesKeywordAt(clause, i, "not")) {
                int j = i + 3;
                while (j < clause.length() && Character.isWhitespace(clause.charAt(j))) j++;
                if (!matchesKeywordAt(clause, j, "in")) {
                    throw new IllegalArgumentException(
                            "Expected \"not in\" in condition: \"" + clause + "\"");
                }
                opIndex = i;
                opLength = (j + 2) - i;
                op = ConditionOp.IN_SET;   // refined below by operand shape
                negatedKeyword = true;
            } else if (matchesKeywordAt(clause, i, "in")) {
                opIndex = i;
                opLength = 2;
                op = ConditionOp.IN_SET;   // refined below by operand shape
            }
        }

        if (opIndex < 0) {
            throw new IllegalArgumentException(
                    "No comparison found in condition: \"" + clause + "\". Expected one of "
                            + "=, !=, <, <=, >, >=, in, not in.");
        }

        String nameText = clause.substring(0, opIndex).trim();
        String operandText = clause.substring(opIndex + opLength).trim();

        if (nameText.isEmpty()) {
            throw new IllegalArgumentException("Missing variable name in condition: \"" + clause + "\"");
        }
        if (operandText.isEmpty()) {
            throw new IllegalArgumentException("Missing value in condition: \"" + clause + "\"");
        }

        Node variable = lookUpVariable(unquote(nameText));
        int column = sourceDataSet.getColumnIndex(variable);
        boolean isDiscrete = variable instanceof DiscreteVariable;

        boolean keywordOp = (opLength >= 2 && (op == ConditionOp.IN_SET))
                && (operandText.startsWith("{") || operandText.startsWith("(")
                || operandText.startsWith("["));

        if (op == ConditionOp.IN_SET && !keywordOp) {
            throw new IllegalArgumentException(
                    "After \"in\", expected a set in braces or an interval in brackets: \""
                            + clause + "\"");
        }

        if (operandText.startsWith("{")) {
            List<String> items = parseBracketedList(operandText, '{', '}', clause);
            ConditionOp setOp = negatedKeyword ? ConditionOp.NOT_IN_SET : ConditionOp.IN_SET;

            if (isDiscrete) {
                Set<Integer> indices = new LinkedHashSet<>();
                for (String item : items) {
                    indices.add(lookUpCategory((DiscreteVariable) variable, unquote(item)));
                }
                return RowCondition.discrete(variable, column, setOp, indices);
            } else {
                double[] values = new double[items.size()];
                for (int i = 0; i < items.size(); i++) {
                    values[i] = parseNumber(unquote(items.get(i)), clause);
                }
                return RowCondition.continuous(variable, column, setOp, values);
            }
        }

        if (operandText.startsWith("(") || operandText.startsWith("[")) {
            if (isDiscrete) {
                throw new IllegalArgumentException(
                        "Interval conditions do not apply to the discrete variable \""
                                + variable.getName() + "\"; use = , != , in {...} or not in {...}.");
            }

            boolean lowClosed = operandText.startsWith("[");
            char close = operandText.charAt(operandText.length() - 1);
            if (close != ')' && close != ']') {
                throw new IllegalArgumentException(
                        "Interval must end with ) or ]: \"" + clause + "\"");
            }
            boolean highClosed = close == ']';

            List<String> bounds = parseBracketedList(operandText,
                    operandText.charAt(0), close, clause);
            if (bounds.size() != 2) {
                throw new IllegalArgumentException(
                        "An interval needs exactly two endpoints: \"" + clause + "\"");
            }

            double low = parseNumber(unquote(bounds.get(0)), clause);
            double high = parseNumber(unquote(bounds.get(1)), clause);

            if (low > high) {
                throw new IllegalArgumentException(
                        "Interval lower bound exceeds upper bound: \"" + clause + "\"");
            }

            ConditionOp intervalOp = negatedKeyword
                    ? ConditionOp.NOT_IN_INTERVAL : ConditionOp.IN_INTERVAL;
            return RowCondition.interval(variable, column, intervalOp, low, high, lowClosed, highClosed);
        }

        // Scalar comparison.
        String value = unquote(operandText);

        if (isDiscrete) {
            if (op != ConditionOp.EQ && op != ConditionOp.NE) {
                throw new IllegalArgumentException(
                        "The operator in \"" + clause + "\" does not apply to the discrete variable \""
                                + variable.getName() + "\"; use = , != , in {...} or not in {...}.");
            }
            Set<Integer> indices = new LinkedHashSet<>();
            indices.add(lookUpCategory((DiscreteVariable) variable, value));
            return RowCondition.discrete(variable, column, op, indices);
        }

        return RowCondition.continuous(variable, column, op, new double[]{parseNumber(value, clause)});
    }

    /**
     * Splits the interior of a bracketed set or interval on commas, respecting quotes.
     */
    private static List<String> parseBracketedList(String text, char open, char close, String clause) {
        if (text.length() < 2 || text.charAt(0) != open || text.charAt(text.length() - 1) != close) {
            throw new IllegalArgumentException("Malformed list or interval: \"" + clause + "\"");
        }

        String inner = text.substring(1, text.length() - 1);
        List<String> items = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < inner.length(); i++) {
            char ch = inner.charAt(i);
            if (ch == '"') {
                inQuotes = !inQuotes;
                current.append(ch);
            } else if (ch == ',' && !inQuotes) {
                items.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        items.add(current.toString().trim());

        for (String item : items) {
            if (item.isEmpty()) {
                throw new IllegalArgumentException("Empty entry in: \"" + clause + "\"");
            }
        }

        return items;
    }

    private static String unquote(String s) {
        String t = s.trim();
        if (t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"")) {
            return t.substring(1, t.length() - 1);
        }
        return t;
    }

    private static double parseNumber(String s, String clause) {
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Expected a number but found \"" + s + "\" in: \""
                    + clause + "\"");
        }
    }

    /**
     * Resolves a variable name against the source data set: exact match first, then a unique
     * case-insensitive match.
     */
    private Node lookUpVariable(String name) {
        Node exact = sourceDataSet.getVariable(name);
        if (exact != null) return exact;

        Node found = null;
        for (Node node : sourceDataSet.getVariables()) {
            if (node.getName().equalsIgnoreCase(name)) {
                if (found != null) {
                    throw new IllegalArgumentException(
                            "The variable name \"" + name + "\" is ambiguous apart from case.");
                }
                found = node;
            }
        }

        if (found == null) {
            throw new IllegalArgumentException("No such variable in this data set: \"" + name + "\"");
        }

        return found;
    }

    /**
     * Resolves a category name against a discrete variable: exact match first, then a unique
     * case-insensitive match.
     */
    private static int lookUpCategory(DiscreteVariable variable, String category) {
        List<String> categories = variable.getCategories();

        int index = categories.indexOf(category);
        if (index >= 0) return index;

        int found = -1;
        for (int i = 0; i < categories.size(); i++) {
            if (categories.get(i).equalsIgnoreCase(category)) {
                if (found >= 0) {
                    throw new IllegalArgumentException("The category \"" + category
                            + "\" of \"" + variable.getName() + "\" is ambiguous apart from case.");
                }
                found = i;
            }
        }

        if (found < 0) {
            throw new IllegalArgumentException("\"" + category + "\" is not a category of \""
                    + variable.getName() + "\". Categories are: " + categories);
        }

        return found;
    }

    /**
     * Keeps the rows of {@code baseRows} satisfying every condition.
     */
    private List<Integer> applyConditions(List<Integer> baseRows, List<RowCondition> conditions) {
        if (conditions.isEmpty()) return baseRows;

        List<Integer> kept = new ArrayList<>(baseRows.size());

        outer:
        for (int row : baseRows) {
            for (RowCondition condition : conditions) {
                if (!condition.holds(sourceDataSet, row)) continue outer;
            }
            kept.add(row);
        }

        return kept;
    }

    /**
     * Updates the label beside the condition field with the number of rows surviving the row
     * specification and the conditions, or with a short indication that the specification does not
     * yet parse.
     */
    private void updateConditionCount() {
        try {
            List<Integer> base = parseRowSpec(rowSpecField.getText(), sourceDataSet.getNumRows());
            List<RowCondition> conditions = parseConditions(conditionField.getText());
            int kept = applyConditions(base, conditions).size();

            conditionCountLabel.setText(kept + " of " + base.size() + " rows");
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

        // Apply row conditions before sampling, so that a requested sample size is drawn from
        // the rows satisfying the conditions rather than being thinned by them afterwards.
        // Unlike an invalid row specification, an invalid condition is NOT tolerated: falling
        // back to all rows here would silently analyze the whole data set while the user
        // believed it had been restricted.
        List<Integer> conditionedRows;
        try {
            conditionedRows = applyConditions(baseRows, parseConditions(conditionField.getText()));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid condition: " + e.getMessage(), e);
        }

        List<Integer> finalRows = applySampling(conditionedRows);

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