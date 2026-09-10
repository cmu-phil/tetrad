///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2026 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
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
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.data.LongToWide;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.model.DataWrapper;
import edu.cmu.tetradapp.model.LongToWideWrapper;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Parameter editor for {@link LongToWideWrapper}. The user chooses the row key variable(s) that identify a unit,
 * the discrete column key whose levels become column suffixes, which of the remaining variables to spread (all by
 * default), an aggregation per value variable for duplicated (unit, level) combinations (FAIL by default), and
 * naming options. Every choice is written to the parameters as it is made, so the editor has no OK button of its
 * own.
 *
 * @author josephramsey
 */
public class LongToWideParamsEditor extends JPanel implements ParameterEditor {

    private Parameters params;
    private DataSet sourceDataSet;

    private final List<Node> variables = new ArrayList<>();
    private JList<String> rowKeyList;
    private JComboBox<String> columnKeyBox;
    private ValueTableModel valueModel;
    private JComboBox<LongToWide.Aggregation> defaultAggregationBox;
    private JCheckBox sanitizeBox;
    private JCheckBox keepKeysBox;
    private JTextField separatorField;
    private JCheckBox includeValueNameBox;
    private JSpinner maxLengthSpinner;
    private LevelTableModel levelModel;
    private java.util.Map<String, String> savedLevelNames = new java.util.LinkedHashMap<>();

    /**
     * Constructs the editor.
     */
    public LongToWideParamsEditor() {
    }

    /**
     * {@inheritDoc}
     */
    public void setParams(Parameters params) {
        this.params = params;
    }

    /**
     * {@inheritDoc}
     */
    public void setParentModels(Object[] parentModels) {
        if (parentModels == null) return;

        for (Object parent : parentModels) {
            if (parent instanceof DataWrapper data) {
                DataModel model = data.getSelectedDataModel();
                if (model instanceof DataSet dataSet) {
                    this.sourceDataSet = dataSet;
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public void setup() {
        buildGui();
    }

    /**
     * {@inheritDoc}
     */
    public boolean mustBeShown() {
        return true;
    }

    //================================= Private Methods ===============================//

    private void buildGui() {
        setLayout(new BorderLayout());

        if (this.sourceDataSet == null) {
            add(new JLabel("Long-to-wide requires a tabular data set as parent."), BorderLayout.CENTER);
            return;
        }

        this.variables.addAll(this.sourceDataSet.getVariables());
        List<String> names = new ArrayList<>();
        for (Node v : this.variables) names.add(v.getName());

        // Read the saved level names before anything writes the parameters (the value table's first refresh does).
        this.savedLevelNames = LongToWideWrapper.decodeLevelNames(
                this.params.getString(LongToWideWrapper.LEVEL_NAMES, ""));

        // ---- Tab 1: Keys ----
        List<String> savedRowKeys = LongToWideWrapper.split(this.params.getString(LongToWideWrapper.ROW_KEYS, ""));
        String savedColumnKey = this.params.getString(LongToWideWrapper.COLUMN_KEY, "");

        this.rowKeyList = new JList<>(names.toArray(new String[0]));
        this.rowKeyList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        this.rowKeyList.setVisibleRowCount(8);
        selectAll(this.rowKeyList, names, savedRowKeys);

        List<String> discreteNames = new ArrayList<>();
        for (Node v : this.variables) if (v instanceof DiscreteVariable) discreteNames.add(v.getName());
        this.columnKeyBox = new JComboBox<>(discreteNames.toArray(new String[0]));
        if (discreteNames.contains(savedColumnKey)) {
            this.columnKeyBox.setSelectedItem(savedColumnKey);
        } else if (!discreteNames.isEmpty()) {
            this.columnKeyBox.setSelectedIndex(0);
        }

        // BorderLayout rather than GridBagLayout: when a GridBag's preferred width exceeds the dialog, it falls
        // back to minimum sizes and the list collapses to one row. Here the list is CENTER and gets whatever
        // height remains after the label above and the column-key block below.
        JScrollPane rowKeyScroll = new JScrollPane(this.rowKeyList);
        rowKeyScroll.setPreferredSize(new Dimension(300, 160));
        rowKeyScroll.setMinimumSize(new Dimension(200, 120));

        JPanel rowKeyPanel = new JPanel(new BorderLayout(4, 4));
        rowKeyPanel.add(new JLabel("Row key variable(s) identifying a unit (ctrl-click to select several):"),
                BorderLayout.NORTH);
        rowKeyPanel.add(rowKeyScroll, BorderLayout.CENTER);

        JPanel columnKeyRow = new JPanel(new BorderLayout(8, 0));
        columnKeyRow.add(new JLabel("Column key variable (discrete; levels become column suffixes):"),
                BorderLayout.NORTH);
        columnKeyRow.add(this.columnKeyBox, BorderLayout.CENTER);

        JPanel keysSouth = new JPanel(new BorderLayout(4, 8));
        keysSouth.add(columnKeyRow, BorderLayout.NORTH);
        keysSouth.add(wrappedNote("Absent (unit, level) combinations become missing values in the wide data. The "
                + "findings report (units, levels, fill rate per column, duplicates) is written to the log."),
                BorderLayout.CENTER);

        JPanel keys = new JPanel(new BorderLayout(4, 12));
        keys.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        keys.add(rowKeyPanel, BorderLayout.CENTER);
        keys.add(keysSouth, BorderLayout.SOUTH);

        // ---- Tab 2: Value variables ----
        this.valueModel = new ValueTableModel();
        JTable valueTable = new JTable(this.valueModel);
        valueTable.setRowHeight(22);
        TableColumn aggCol = valueTable.getColumnModel().getColumn(2);
        aggCol.setCellEditor(new DefaultCellEditor(new JComboBox<>(LongToWide.Aggregation.values())));
        valueTable.getColumnModel().getColumn(0).setMaxWidth(60);
        valueTable.getColumnModel().getColumn(2).setMaxWidth(110);

        JPanel values = new JPanel(new BorderLayout(4, 4));
        values.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JScrollPane valueScroll = new JScrollPane(valueTable);
        valueScroll.setPreferredSize(new Dimension(460, 220));
        values.add(valueScroll, BorderLayout.CENTER);

        JPanel valueButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton all = new JButton("Select all");
        JButton none = new JButton("Select none");
        all.addActionListener(e -> this.valueModel.setAllKept(true));
        none.addActionListener(e -> this.valueModel.setAllKept(false));
        valueButtons.add(all);
        valueButtons.add(none);
        valueButtons.add(new JLabel("   Default for duplicates:"));
        this.defaultAggregationBox = new JComboBox<>(LongToWide.Aggregation.values());
        this.defaultAggregationBox.setSelectedItem(LongToWide.Aggregation.valueOf(
                this.params.getString(LongToWideWrapper.DEFAULT_AGGREGATION, LongToWide.Aggregation.FAIL.name())));
        this.defaultAggregationBox.addActionListener(e -> writeParams());
        valueButtons.add(this.defaultAggregationBox);

        JPanel valuesSouth = new JPanel(new BorderLayout());
        valuesSouth.add(valueButtons, BorderLayout.NORTH);
        valuesSouth.add(wrappedNote("Each kept variable gives one wide column per level. A (unit, level) "
                + "combination with more than one long row is resolved by its Duplicates aggregation; FAIL stops "
                + "with a count so nothing is collapsed silently. Discrete variables allow FIRST, LAST, and COUNT. "
                + "COUNT gives the number of long rows as a continuous column (0 where a unit has none)."), BorderLayout.CENTER);
        values.add(valuesSouth, BorderLayout.SOUTH);

        // ---- Tab 3: Column names ----
        this.levelModel = new LevelTableModel();
        JTable levelTable = new JTable(this.levelModel);
        levelTable.setRowHeight(22);
        JScrollPane levelScroll = new JScrollPane(levelTable);
        levelScroll.setPreferredSize(new Dimension(460, 220));

        JPanel levelButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton suggest = new JButton("Suggest short names");
        JButton fullNames = new JButton("Use full names");
        this.maxLengthSpinner = new JSpinner(new SpinnerNumberModel(24, 4, 200, 1));
        suggest.addActionListener(e -> this.levelModel.suggest((Integer) this.maxLengthSpinner.getValue()));
        fullNames.addActionListener(e -> this.levelModel.clearNames());
        levelButtons.add(suggest);
        levelButtons.add(new JLabel("max length:"));
        levelButtons.add(this.maxLengthSpinner);
        levelButtons.add(fullNames);

        this.includeValueNameBox = new JCheckBox("Prefix with value variable name",
                this.params.getBoolean(LongToWideWrapper.INCLUDE_VALUE_NAME, true));
        this.includeValueNameBox.setToolTipText("Name columns value.level rather than level alone; always done when "
                + "more than one value variable is spread, since the level alone would not be unique.");
        this.includeValueNameBox.addActionListener(e -> writeParams());
        this.separatorField = new JTextField(this.params.getString(LongToWideWrapper.SEPARATOR, "."), 3);
        JPanel prefixRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        prefixRow.add(this.includeValueNameBox);
        prefixRow.add(new JLabel("separator:"));
        prefixRow.add(this.separatorField);

        JPanel namesSouth = new JPanel(new BorderLayout());
        namesSouth.add(levelButtons, BorderLayout.NORTH);
        namesSouth.add(prefixRow, BorderLayout.CENTER);
        namesSouth.add(wrappedNote("Edit the right-hand column to choose the suffix used for each level; blank "
                + "means the level's own name. 'Suggest short names' drops words shared by most levels and keeps "
                + "the distinguishing ones; review the result."), BorderLayout.SOUTH);

        JPanel levelsPanel = new JPanel(new BorderLayout(4, 4));
        levelsPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        levelsPanel.add(levelScroll, BorderLayout.CENTER);
        levelsPanel.add(namesSouth, BorderLayout.SOUTH);

        // ---- Tab 4: Options ----
        this.sanitizeBox = new JCheckBox("Sanitize names (spaces and punctuation become _)",
                this.params.getBoolean(LongToWideWrapper.SANITIZE_NAMES, true));
        this.keepKeysBox = new JCheckBox("Keep row key columns",
                this.params.getBoolean(LongToWideWrapper.KEEP_ROW_KEYS, false));
        this.sanitizeBox.addActionListener(e -> writeParams());
        this.keepKeysBox.addActionListener(e -> writeParams());

        JPanel options = new JPanel();
        options.setLayout(new BoxLayout(options, BoxLayout.Y_AXIS));
        options.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        this.sanitizeBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        this.keepKeysBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        options.add(this.sanitizeBox);
        options.add(this.keepKeysBox);
        options.add(Box.createVerticalStrut(8));
        JComponent optionsNote = wrappedNote("Sanitized names survive whitespace-delimited contexts such as "
                + "knowledge files. Row keys are dropped by default: a unit identifier has one category per row and "
                + "is of no use to a search; the keys remain available in the log report.");
        optionsNote.setAlignmentX(Component.LEFT_ALIGNMENT);
        optionsNote.setMaximumSize(new Dimension(520, 120));
        options.add(optionsNote);
        options.add(Box.createVerticalGlue());

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Keys", keys);
        tabs.addTab("Value variables", values);
        tabs.addTab("Column names", levelsPanel);
        tabs.addTab("Options", options);
        tabs.setPreferredSize(new Dimension(560, 440));
        add(tabs, BorderLayout.CENTER);

        // ---- Listeners: keys change the candidate value variables and levels; everything writes params. ----
        this.rowKeyList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) refreshValueRows();
        });
        this.columnKeyBox.addActionListener(e -> {
            refreshValueRows();
            refreshLevelRows();
        });
        this.separatorField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                writeParams();
            }

            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                writeParams();
            }

            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                writeParams();
            }
        });

        refreshValueRows();
        refreshLevelRows();
    }

    /**
     * A read-only, word-wrapped note whose preferred size is bounded (a bare JTextArea reports the width of its
     * unwrapped text as its preferred width, which stretches the option dialog).
     */
    private static JComponent wrappedNote(String text) {
        JTextArea note = new JTextArea(text);
        note.setLineWrap(true);
        note.setWrapStyleWord(true);
        note.setEditable(false);
        note.setOpaque(false);
        note.setFocusable(false);
        note.setFont(new JLabel().getFont());
        note.setColumns(40);
        note.setRows(3);
        return note;
    }

    /**
     * Rebuilds the level rows for the current column key, preserving saved short names.
     */
    private void refreshLevelRows() {
        String columnKey = (String) this.columnKeyBox.getSelectedItem();
        List<String> levels = new ArrayList<>();
        for (Node v : this.variables) {
            if (v.getName().equals(columnKey) && v instanceof DiscreteVariable dv) levels.addAll(dv.getCategories());
        }
        // Names entered so far in this session take precedence over those saved with the session.
        java.util.Map<String, String> saved = new java.util.LinkedHashMap<>(this.savedLevelNames);
        saved.putAll(this.levelModel.names());
        this.levelModel.setLevels(levels, saved);
        writeParams();
    }

    private static void selectAll(JList<String> list, List<String> names, List<String> selected) {
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) if (selected.contains(names.get(i))) idx.add(i);
        int[] arr = new int[idx.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = idx.get(i);
        list.setSelectedIndices(arr);
    }

    /**
     * Rebuilds the value-variable rows as the non-key variables, preserving saved keep/aggregation choices.
     */
    private void refreshValueRows() {
        List<String> rowKeys = this.rowKeyList.getSelectedValuesList();
        String columnKey = (String) this.columnKeyBox.getSelectedItem();

        List<String> savedValues = LongToWideWrapper.split(this.params.getString(LongToWideWrapper.VALUE_VARIABLES, ""));
        List<String> savedAggs = LongToWideWrapper.split(this.params.getString(LongToWideWrapper.AGGREGATIONS, ""));

        List<ValueRow> rows = new ArrayList<>();
        for (Node v : this.variables) {
            String name = v.getName();
            if (rowKeys.contains(name) || name.equals(columnKey)) continue;

            ValueRow row = new ValueRow();
            row.name = name;
            row.discrete = v instanceof DiscreteVariable;
            row.keep = savedValues.isEmpty() || savedValues.contains(name);
            row.aggregation = null;
            for (String pair : savedAggs) {
                if (pair.startsWith(name + "=")) {
                    try {
                        row.aggregation = LongToWide.Aggregation.valueOf(pair.substring(name.length() + 1));
                    } catch (IllegalArgumentException ignored) {
                        // Stale value; leave as default.
                    }
                }
            }
            rows.add(row);
        }

        this.valueModel.setRows(rows);
        writeParams();
    }

    private void writeParams() {
        if (this.params == null || this.valueModel == null) return;

        this.params.set(LongToWideWrapper.ROW_KEYS, LongToWideWrapper.join(this.rowKeyList.getSelectedValuesList()));
        Object columnKey = this.columnKeyBox.getSelectedItem();
        this.params.set(LongToWideWrapper.COLUMN_KEY, columnKey == null ? "" : (String) columnKey);

        List<String> kept = new ArrayList<>();
        List<String> aggs = new ArrayList<>();
        boolean allKept = true;
        for (ValueRow row : this.valueModel.rows) {
            if (row.keep) kept.add(row.name);
            else allKept = false;
            if (row.aggregation != null) aggs.add(row.name + "=" + row.aggregation.name());
        }

        // An empty list means "all non-key variables", which also keeps the parameter valid if the data changes.
        this.params.set(LongToWideWrapper.VALUE_VARIABLES, allKept ? "" : LongToWideWrapper.join(kept));
        this.params.set(LongToWideWrapper.AGGREGATIONS, LongToWideWrapper.join(aggs));
        this.params.set(LongToWideWrapper.DEFAULT_AGGREGATION,
                ((LongToWide.Aggregation) this.defaultAggregationBox.getSelectedItem()).name());
        this.params.set(LongToWideWrapper.SANITIZE_NAMES, this.sanitizeBox.isSelected());
        this.params.set(LongToWideWrapper.KEEP_ROW_KEYS, this.keepKeysBox.isSelected());
        String sep = this.separatorField.getText();
        this.params.set(LongToWideWrapper.SEPARATOR, sep.isEmpty() ? "." : sep);
        this.params.set(LongToWideWrapper.INCLUDE_VALUE_NAME, this.includeValueNameBox.isSelected());
        if (this.levelModel != null) {
            this.params.set(LongToWideWrapper.LEVEL_NAMES, LongToWideWrapper.encodeLevelNames(this.levelModel.names()));
        }
    }

    private final class LevelTableModel extends AbstractTableModel {
        private final List<String> levels = new ArrayList<>();
        private final List<String> shortNames = new ArrayList<>(); // "" means: use the level itself

        void setLevels(List<String> newLevels, java.util.Map<String, String> saved) {
            this.levels.clear();
            this.shortNames.clear();
            for (String level : newLevels) {
                this.levels.add(level);
                this.shortNames.add(saved.getOrDefault(level, ""));
            }
            fireTableDataChanged();
        }

        void suggest(int maxLength) {
            java.util.Map<String, String> s = LongToWide.suggestShortLevelNames(this.levels, maxLength);
            for (int i = 0; i < this.levels.size(); i++) this.shortNames.set(i, s.get(this.levels.get(i)));
            fireTableDataChanged();
            writeParams();
        }

        void clearNames() {
            java.util.Collections.fill(this.shortNames, "");
            fireTableDataChanged();
            writeParams();
        }

        java.util.Map<String, String> names() {
            java.util.Map<String, String> m = new java.util.LinkedHashMap<>();
            for (int i = 0; i < this.levels.size(); i++) {
                if (!this.shortNames.get(i).isEmpty()) m.put(this.levels.get(i), this.shortNames.get(i));
            }
            return m;
        }

        public int getRowCount() {
            return this.levels.size();
        }

        public int getColumnCount() {
            return 2;
        }

        public String getColumnName(int col) {
            return col == 0 ? "Level" : "Column suffix (blank = level)";
        }

        public boolean isCellEditable(int row, int col) {
            return col == 1;
        }

        public Object getValueAt(int row, int col) {
            return col == 0 ? this.levels.get(row) : this.shortNames.get(row);
        }

        public void setValueAt(Object value, int row, int col) {
            if (col == 1) {
                this.shortNames.set(row, value == null ? "" : value.toString().trim());
                fireTableRowsUpdated(row, row);
                writeParams();
            }
        }
    }

    private static final class ValueRow {
        String name;
        boolean discrete;
        boolean keep;
        LongToWide.Aggregation aggregation; // null: use the default
    }

    private final class ValueTableModel extends AbstractTableModel {
        private final List<ValueRow> rows = new ArrayList<>();

        void setRows(List<ValueRow> newRows) {
            this.rows.clear();
            this.rows.addAll(newRows);
            fireTableDataChanged();
        }

        void setAllKept(boolean keep) {
            for (ValueRow r : this.rows) r.keep = keep;
            fireTableDataChanged();
            writeParams();
        }

        public int getRowCount() {
            return this.rows.size();
        }

        public int getColumnCount() {
            return 3;
        }

        public String getColumnName(int col) {
            return switch (col) {
                case 0 -> "Keep";
                case 1 -> "Variable";
                default -> "Duplicates";
            };
        }

        public Class<?> getColumnClass(int col) {
            return switch (col) {
                case 0 -> Boolean.class;
                case 2 -> LongToWide.Aggregation.class;
                default -> String.class;
            };
        }

        public boolean isCellEditable(int row, int col) {
            return col == 0 || (col == 2 && this.rows.get(row).keep);
        }

        public Object getValueAt(int row, int col) {
            ValueRow r = this.rows.get(row);
            return switch (col) {
                case 0 -> r.keep;
                case 1 -> r.name + (r.discrete ? "  (discrete)" : "");
                default -> r.aggregation == null
                        ? (LongToWide.Aggregation) LongToWideParamsEditor.this.defaultAggregationBox.getSelectedItem()
                        : r.aggregation;
            };
        }

        public void setValueAt(Object value, int row, int col) {
            ValueRow r = this.rows.get(row);
            if (col == 0) {
                r.keep = Boolean.TRUE.equals(value);
            } else if (col == 2 && value instanceof LongToWide.Aggregation agg) {
                LongToWide.Aggregation dflt = (LongToWide.Aggregation) LongToWideParamsEditor.this.defaultAggregationBox.getSelectedItem();
                r.aggregation = agg == dflt ? null : agg;
            }
            fireTableRowsUpdated(row, row);
            writeParams();
        }
    }
}
