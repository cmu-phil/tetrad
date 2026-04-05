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

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.editor.FinalizingParameterEditor;
import edu.cmu.tetradapp.model.DataWrapper;
import edu.cmu.tetradapp.model.datamanip.SelectionWrapper.SelectionSpec;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;
import java.util.List;

/**
 * Parameter editor that lets the user specify row-selection constraints for a
 * {@link DataSet}. Constraints are stored into the supplied {@link Parameters}
 * object under the key {@code "selectionSpecs"} as a
 * {@code Map<Node, SelectionSpec>} and are later consumed by
 * {@link edu.cmu.tetradapp.model.datamanip.SelectionWrapper}.
 *
 * Layout
 * <pre>
 * ┌─────────────────────────────────────────────────────────────────────────┐
 * │ [x] Select on SELECTION-type variables only                             │
 * │ [ ] Remove selection variables from output                              │
 * ├─────────────────────┬───────────────────────────────────────────────────┤
 * │ Variables           │  Constraint editor for the selected variable       │
 * │ ─────────────────── │  ─────────────────────────────────────────────────│
 * │  X1  (continuous)   │  (ContinuousSelectionEditor or                    │
 * │  X2  (discrete)     │   DiscreteSelectionEditor shown here)             │
 * │  S   (SELECTION)    │                                                   │
 * │  …                  │                                                   │
 * └─────────────────────┴───────────────────────────────────────────────────┘
 * </pre>
 *
 * Continuous constraint editor
 * The user builds a union of closed intervals {@code [lo, hi]}.  Each interval
 * is a row with two validated {@link JTextField}s and a "Remove" button.  An
 * "Add interval" button appends a new row pre-filled with the variable's
 * observed [min, max].
 *
 * Discrete constraint editor
 * One {@link JCheckBox} per category value.  At least one box must be checked.
 *
 * @author josephramsey
 */
public class SelectionParamsEditor extends JPanel implements FinalizingParameterEditor {

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    /** The dataset whose rows will be filtered. */
    private DataSet sourceDataSet;

    /** Parameters object into which specs are written by {@link #finalizeEdit()}. */
    private Parameters parameters;

    /**
     * One editor panel per variable.  Populated in {@link #setup()} and updated
     * when the user changes settings.
     */
    private final Map<Node, JPanel> nodeEditors = new LinkedHashMap<>();

    /** Right-hand panel that swaps in the editor for the currently selected variable. */
    private JPanel editorArea;

    /** The list widget showing the candidate variables. */
    private JList<Node> variableList;

    /** Model backing {@link #variableList}. */
    private DefaultListModel<Node> listModel;

    /** All variables in the source dataset (used for list refresh). */
    private List<Node> allVariables;

    /** Checkbox controlling whether only SELECTION-typed nodes are shown. */
    private JCheckBox selectionOnlyCheckBox;

    /**
     * Checkbox controlling whether the constrained variables are dropped from
     * the output dataset after row-filtering.
     */
    private JCheckBox removeSelectionVarsCheckBox;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /** No-arg constructor required by the Tetrad plugin framework. */
    public SelectionParamsEditor() {
        // intentionally empty – setup() is called separately
    }

    // =========================================================================
    // FinalizingParameterEditor contract
    // =========================================================================

    /**
     * Writes the current editor state into {@link #parameters} under
     * {@code "selectionSpecs"} and {@code "removeSelectionVariables"}, then
     * returns {@code true} unless there are no editors.
     */
    @Override
    public boolean finalizeEdit() {
        if (nodeEditors.isEmpty()) {
            return false;
        }
        Map<Node, SelectionSpec> specs = new HashMap<>();
        for (Map.Entry<Node, JPanel> entry : nodeEditors.entrySet()) {
            Node node = entry.getKey();
            JPanel editor = entry.getValue();
            SelectionSpec spec = extractSpec(node, editor);
            if (spec != null) {
                specs.put(node, spec);
            }
        }
        parameters.set("selectionSpecs", specs);
        parameters.set("removeSelectionVariables", removeSelectionVarsCheckBox.isSelected());
        return true;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Stores the parameters object and initialises default values so that
     * {@link #getSpecs()} never returns {@code null}.
     */
    @Override
    public void setParams(Parameters params) {
        this.parameters = params;
        if (params.get("selectionSpecs") == null) {
            params.set("selectionSpecs", new HashMap<Node, SelectionSpec>());
        }
        if (params.get("removeSelectionVariables") == null) {
            params.set("removeSelectionVariables", false);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Extracts the {@link DataSet} from the parent {@link DataWrapper}.
     */
    @Override
    public void setParentModels(Object[] parentModels) {
        if (parentModels == null || parentModels.length == 0) {
            throw new IllegalArgumentException("A parent DataWrapper is required.");
        }
        DataWrapper dataWrapper = null;
        for (Object parent : parentModels) {
            if (parent instanceof DataWrapper dw) {
                dataWrapper = dw;
                break;
            }
        }
        if (dataWrapper == null) {
            throw new IllegalArgumentException("No DataWrapper found among parent models.");
        }
        DataModel model = dataWrapper.getSelectedDataModel();
        if (!(model instanceof DataSet ds)) {
            throw new IllegalArgumentException("The dataset must be a rectangular (tabular) dataset.");
        }
        this.sourceDataSet = ds;
    }

    /** @return {@code true} – this editor must always be shown to the user. */
    @Override
    public boolean mustBeShown() {
        return true;
    }

    // =========================================================================
    // Setup
    // =========================================================================

    /**
     * Builds and displays the full editor UI.  Called by the Tetrad framework
     * after {@link #setParams} and {@link #setParentModels}.
     */
    @Override
    public void setup() {
        setLayout(new BorderLayout());

        allVariables = new ArrayList<>(sourceDataSet.getVariables());

        // Build an editor panel for every variable up-front.
        for (Node node : allVariables) {
            nodeEditors.put(node, buildEditorPanel(node));
        }

        // -- Top bar: option checkboxes ----------------------------------------
        selectionOnlyCheckBox = new JCheckBox("Select on SELECTION-type variables only");
        selectionOnlyCheckBox.setSelected(true);
        selectionOnlyCheckBox.addActionListener(this::onSelectionOnlyToggled);

        removeSelectionVarsCheckBox = new JCheckBox("Remove selection variables from output");
        removeSelectionVarsCheckBox.setSelected(
                Boolean.TRUE.equals(parameters.get("removeSelectionVariables")));
        // Persist the checkbox state immediately whenever it is toggled so that
        // finalizeEdit() always reflects the latest value even if the dialog is
        // closed via a keyboard shortcut that bypasses the normal OK path.
        removeSelectionVarsCheckBox.addActionListener(e ->
                parameters.set("removeSelectionVariables", removeSelectionVarsCheckBox.isSelected()));

        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        topBar.add(selectionOnlyCheckBox);
        topBar.add(Box.createHorizontalStrut(16));
        topBar.add(removeSelectionVarsCheckBox);
        add(topBar, BorderLayout.NORTH);

        // -- Left panel: variable list -----------------------------------------
        // NOTE: variableList must be created before refreshListModel() is called,
        // because refreshListModel() reads variableList.getSelectedValue() and
        // also calls variableList.setSelectedIndex().
        listModel = new DefaultListModel<>();
        variableList = new JList<>(listModel);
        variableList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        variableList.setCellRenderer(new VariableRenderer());
        refreshListModel(false);
        variableList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                onVariableSelected(variableList.getSelectedValue());
            }
        });

        JScrollPane listScroll = new JScrollPane(variableList);
        listScroll.setPreferredSize(new Dimension(160, 350));

        Box leftBox = Box.createVerticalBox();
        leftBox.add(new JLabel("Variables:"));
        leftBox.add(Box.createVerticalStrut(4));
        leftBox.add(listScroll);

        // -- Right panel: constraint editor area --------------------------------
        editorArea = new JPanel(new BorderLayout());
        editorArea.setBorder(new EmptyBorder(0, 8, 0, 0));
        editorArea.setPreferredSize(new Dimension(420, 350));

        JLabel placeholder = new JLabel("Select a variable on the left to configure its constraint.",
                SwingConstants.CENTER);
        placeholder.setForeground(Color.GRAY);
        editorArea.add(placeholder, BorderLayout.CENTER);

        // -- Centre split -------------------------------------------------------
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftBox, editorArea);
        split.setDividerLocation(170);
        split.setBorder(new EmptyBorder(6, 6, 6, 6));

        add(split, BorderLayout.CENTER);

        // Select the first visible variable automatically.
        if (!listModel.isEmpty()) {
            variableList.setSelectedIndex(0);
        }

        onSelectionOnlyToggled(null);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    // --- List management -----------------------------------------------------

    private void refreshListModel(boolean selectionOnly) {
        Node previously = variableList != null ? variableList.getSelectedValue() : null;
        listModel.clear();
        for (Node node : allVariables) {
            if (selectionOnly && node.getNodeType() != NodeType.SELECTION) {
                continue;
            }
            listModel.addElement(node);
        }
        // Restore selection if the previously-selected node is still visible.
        if (previously != null) {
            int idx = listModel.indexOf(previously);
            if (idx >= 0) {
                variableList.setSelectedIndex(idx);
            } else if (!listModel.isEmpty()) {
                variableList.setSelectedIndex(0);
            }
        } else if (!listModel.isEmpty()) {
            variableList.setSelectedIndex(0);
        }
    }

    private void onSelectionOnlyToggled(ActionEvent e) {
        refreshListModel(selectionOnlyCheckBox.isSelected());
    }

    // --- Variable selection --------------------------------------------------

    private void onVariableSelected(Node node) {
        editorArea.removeAll();
        if (node == null) {
            editorArea.revalidate();
            editorArea.repaint();
            return;
        }
        JPanel editor = nodeEditors.get(node);
        if (editor == null) {
            editor = buildEditorPanel(node);
            nodeEditors.put(node, editor);
        }
        editorArea.add(editor, BorderLayout.CENTER);
        editorArea.revalidate();
        editorArea.repaint();
    }

    // --- Editor factories ----------------------------------------------------

    private JPanel buildEditorPanel(Node node) {
        if (node instanceof ContinuousVariable cv) {
            return new ContinuousSelectionEditor(cv);
        } else if (node instanceof DiscreteVariable dv) {
            return new DiscreteSelectionEditor(dv);
        } else {
            JPanel placeholder = new JPanel();
            placeholder.add(new JLabel("No constraint available for this variable type."));
            return placeholder;
        }
    }

    // --- Spec extraction -----------------------------------------------------

    /**
     * Reads the current editor state for {@code node} and produces a
     * {@link SelectionSpec}, or {@code null} if the constraint is "accept all"
     * (i.e. no effective restriction).
     */
    private SelectionSpec extractSpec(Node node, JPanel editor) {
        if (editor instanceof ContinuousSelectionEditor cse) {
            return cse.buildSpec();
        } else if (editor instanceof DiscreteSelectionEditor dse) {
            return dse.buildSpec();
        }
        return null;
    }

    // --- Accessors -----------------------------------------------------------

    @SuppressWarnings("unchecked")
    private Map<Node, SelectionSpec> getSpecs() {
        Object selectionSpecs = parameters.get("selectionSpecs");
        if (selectionSpecs == null || !(selectionSpecs instanceof Map<?, ?>)) {
            return new HashMap<>();
        }
        return (Map<Node, SelectionSpec>) selectionSpecs;
    }

    // =========================================================================
    // Inner class: VariableRenderer
    // =========================================================================

    /**
     * List-cell renderer that appends a parenthetical type hint after each
     * variable name and italicises SELECTION-type nodes.
     */
    private static final class VariableRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(
                JList<?> list, Object value, int index,
                boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof Node node) {
                String typeHint = (node instanceof ContinuousVariable) ? "continuous"
                        : (node instanceof DiscreteVariable) ? "discrete"
                          : "other";
                boolean isSel = node.getNodeType() == NodeType.SELECTION;
                String label = node.getName() + "  (" + typeHint + (isSel ? ", SELECTION" : "") + ")";
                setText(label);
                if (isSel) {
                    setFont(getFont().deriveFont(Font.ITALIC));
                }
            }
            return this;
        }
    }

    // =========================================================================
    // Inner class: ContinuousSelectionEditor
    // =========================================================================

    /**
     * Editor for a continuous variable.  Maintains a dynamic list of
     * {@code [lo, hi]} interval rows.  The union of all intervals is used
     * as the row-selection criterion.
     */
    private final class ContinuousSelectionEditor extends JPanel {

        private final ContinuousVariable variable;
        private final double observedMin;
        private final double observedMax;

        /**
         * Each element is a two-element array: {@code {loField, hiField}}.
         * These are the live text fields; we read them in {@link #buildSpec()}.
         */
        private final List<JTextField[]> intervalRows = new ArrayList<>();

        /** Panel that holds the interval rows (rebuilt on add/remove). */
        private final JPanel rowsPanel;

        private static final NumberFormat FMT = new DecimalFormat("0.######");

        ContinuousSelectionEditor(ContinuousVariable variable) {
            this.variable = variable;
            setLayout(new BorderLayout(0, 6));
            setBorder(new TitledBorder("Continuous constraint: " + variable.getName()));

            // Compute observed range from the dataset.
            double min = Double.MAX_VALUE;
            double max = -Double.MAX_VALUE;
            int col = sourceDataSet.getColumnIndex(variable);
            for (int row = 0; row < sourceDataSet.getNumRows(); row++) {
                double v = sourceDataSet.getDouble(row, col);
                if (!Double.isNaN(v)) {
                    if (v < min) min = v;
                    if (v > max) max = v;
                }
            }
            this.observedMin = (min == Double.MAX_VALUE) ? 0.0 : min;
            this.observedMax = (max == -Double.MAX_VALUE) ? 1.0 : max;

            // Header row
            JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
            header.add(new JLabel(String.format(
                    "Observed range: [%s, %s]    Union of intervals below is kept.",
                    FMT.format(observedMin), FMT.format(observedMax))));
            add(header, BorderLayout.NORTH);

            // Scrollable interval-row panel
            rowsPanel = new JPanel();
            rowsPanel.setLayout(new BoxLayout(rowsPanel, BoxLayout.Y_AXIS));
            JScrollPane scroll = new JScrollPane(rowsPanel);
            scroll.setPreferredSize(new Dimension(380, 220));
            add(scroll, BorderLayout.CENTER);

            // "Add interval" button
            JButton addBtn = new JButton("Add interval");
            addBtn.addActionListener(e -> {
                addIntervalRow(observedMin, observedMax);
                refreshRowsPanel();
            });
            JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT));
            south.add(addBtn);
            add(south, BorderLayout.SOUTH);

            // Seed with a single default interval covering the full range.
            addIntervalRow(observedMin, observedMax);
            refreshRowsPanel();

            // Restore previously saved intervals if they exist.
            SelectionSpec saved = getSpecs() != null ? getSpecs().get(variable) : null;
            if (saved != null && saved.isContinuous() && saved.getContinuousIntervals() != null) {
                intervalRows.clear();
                for (double[] iv : saved.getContinuousIntervals()) {
                    addIntervalRow(iv[0], iv[1]);
                }
                refreshRowsPanel();
            }
        }

        /** Appends a new interval row with the given defaults. */
        private void addIntervalRow(double lo, double hi) {
            JTextField loField = new JTextField(FMT.format(lo), 10);
            JTextField hiField = new JTextField(FMT.format(hi), 10);
            installValidationHighlight(loField);
            installValidationHighlight(hiField);
            intervalRows.add(new JTextField[]{loField, hiField});
        }

        /** Rebuilds {@link #rowsPanel} from {@link #intervalRows}. */
        private void refreshRowsPanel() {
            rowsPanel.removeAll();
            for (int i = 0; i < intervalRows.size(); i++) {
                final int idx = i;
                JTextField[] pair = intervalRows.get(i);
                JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
                row.add(new JLabel("["));
                row.add(pair[0]);
                row.add(new JLabel(","));
                row.add(pair[1]);
                row.add(new JLabel("]"));
                JButton removeBtn = new JButton("Remove");
                removeBtn.addActionListener(e -> {
                    intervalRows.remove(idx);
                    refreshRowsPanel();
                });
                removeBtn.setEnabled(intervalRows.size() > 1);
                row.add(removeBtn);
                rowsPanel.add(row);
            }
            rowsPanel.revalidate();
            rowsPanel.repaint();
        }

        /** Turns the field red when its content is not a valid double. */
        private void installValidationHighlight(JTextField field) {
            field.getDocument().addDocumentListener(new DocumentListener() {
                private void check() {
                    boolean ok = tryParse(field.getText()) != null;
                    field.setBackground(ok ? Color.WHITE : new Color(255, 200, 200));
                }
                @Override public void insertUpdate(DocumentEvent e) { check(); }
                @Override public void removeUpdate(DocumentEvent e) { check(); }
                @Override public void changedUpdate(DocumentEvent e) { check(); }
            });
        }

        private Double tryParse(String text) {
            try { return Double.parseDouble(text.trim()); }
            catch (NumberFormatException e) { return null; }
        }

        /**
         * Reads the live text fields and returns a {@link SelectionSpec}, or
         * {@code null} if no valid intervals are present.
         */
        SelectionSpec buildSpec() {
            List<double[]> intervals = new ArrayList<>();
            for (JTextField[] pair : intervalRows) {
                Double lo = tryParse(pair[0].getText());
                Double hi = tryParse(pair[1].getText());
                if (lo != null && hi != null) {
                    double actualLo = Math.min(lo, hi);
                    double actualHi = Math.max(lo, hi);
                    intervals.add(new double[]{actualLo, actualHi});
                }
            }
            return intervals.isEmpty() ? null : SelectionSpec.continuous(intervals);
        }
    }

    // =========================================================================
    // Inner class: DiscreteSelectionEditor
    // =========================================================================

    /**
     * Editor for a discrete variable.  One checkbox per category value.
     * The accepted set is the union of all checked categories.
     */
    private final class DiscreteSelectionEditor extends JPanel {

        private final DiscreteVariable variable;
        private final List<JCheckBox> categoryBoxes = new ArrayList<>();

        DiscreteSelectionEditor(DiscreteVariable variable) {
            this.variable = variable;
            setLayout(new BorderLayout(0, 6));
            setBorder(new TitledBorder("Discrete constraint: " + variable.getName()));

            JPanel checkPanel = new JPanel();
            checkPanel.setLayout(new BoxLayout(checkPanel, BoxLayout.Y_AXIS));

            List<String> cats = variable.getCategories();

            // Restore previously saved selection if available.
            Set<Integer> saved = null;
            SelectionSpec savedSpec = getSpecs() != null ? getSpecs().get(variable) : null;
            if (savedSpec != null && !savedSpec.isContinuous()) {
                saved = savedSpec.getAcceptedCategories();
            }

            for (int i = 0; i < cats.size(); i++) {
                boolean checked = (saved == null) || saved.contains(i);  // default: all checked
                JCheckBox cb = new JCheckBox(cats.get(i), checked);
                categoryBoxes.add(cb);
                checkPanel.add(cb);
            }

            JScrollPane scroll = new JScrollPane(checkPanel);
            scroll.setPreferredSize(new Dimension(380, 250));
            add(new JLabel("  Accepted values (rows where variable equals one of the checked categories):"),
                    BorderLayout.NORTH);
            add(scroll, BorderLayout.CENTER);

            // Select-all / Deselect-all buttons
            JButton selectAll = new JButton("Select all");
            JButton deselectAll = new JButton("Deselect all");
            selectAll.addActionListener(e -> categoryBoxes.forEach(cb -> cb.setSelected(true)));
            deselectAll.addActionListener(e -> categoryBoxes.forEach(cb -> cb.setSelected(false)));

            JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT));
            south.add(selectAll);
            south.add(deselectAll);
            add(south, BorderLayout.SOUTH);
        }

        /**
         * Returns a {@link SelectionSpec} for the checked categories, or
         * {@code null} if all categories are checked (no effective restriction).
         */
        SelectionSpec buildSpec() {
            Set<Integer> accepted = new HashSet<>();
            for (int i = 0; i < categoryBoxes.size(); i++) {
                if (categoryBoxes.get(i).isSelected()) {
                    accepted.add(i);
                }
            }
            // If all categories are accepted, omit the spec (pass-through).
            if (accepted.size() == variable.getCategories().size()) {
                return null;
            }
            return accepted.isEmpty() ? null : SelectionSpec.discrete(accepted);
        }
    }
}
