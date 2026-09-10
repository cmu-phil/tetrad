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

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.data.LongToWide;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.model.LongToWideWrapper;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The pages of the long-to-wide wizard, with Back / Next / Finish / Cancel navigation. The user must click through
 * every page to reach Finish, which is what makes a wizard preferable to tabs here: no configuration is skipped
 * unseen. The pages are Keys (row key variables and the column key), Value variables (which to spread, and how to
 * resolve duplicates), Column names (a suffix per level, with short-name suggestions), and Summary (naming
 * options and a preview of the resulting columns). Every choice is written to the parameters as it is made; the
 * owner is told when the user finishes or cancels.
 * <p>
 * This panel is independent of any window so that it can be exercised without a display; the
 * {@link LongToWideParamsEditor} dialog wraps it.
 *
 * @author josephramsey
 */
public class LongToWideWizardPanel extends JPanel {

    /**
     * Page indices.
     */
    public static final int PAGE_KEYS = 0, PAGE_VALUES = 1, PAGE_NAMES = 2, PAGE_SUMMARY = 3;
    private static final String[] TITLES = {"Step 1 of 4: Keys", "Step 2 of 4: Value variables",
            "Step 3 of 4: Column names", "Step 4 of 4: Summary"};

    private final Parameters params;
    private final DataSet sourceDataSet;
    private final Runnable onFinish;
    private final Runnable onCancel;

    private final List<Node> variables = new ArrayList<>();
    private final CardLayout cards = new CardLayout();
    private final JPanel pages = new JPanel(this.cards);
    private final JLabel title = new JLabel();
    private final JLabel status = new JLabel(" ");
    private final JButton back = new JButton("< Back");
    private final JButton next = new JButton("Next >");
    private final JButton finish = new JButton("Finish");
    private final JButton cancel = new JButton("Cancel");
    private int page = PAGE_KEYS;

    private JList<String> rowKeyList;
    private JComboBox<String> columnKeyBox;
    private ValueTableModel valueModel;
    private JComboBox<LongToWide.Aggregation> defaultAggregationBox;
    private LevelTableModel levelModel;
    private JSpinner maxLengthSpinner;
    private JCheckBox includeValueNameBox;
    private JTextField separatorField;
    private JCheckBox sanitizeBox;
    private JCheckBox keepKeysBox;
    private JTextArea summaryArea;
    private Map<String, String> savedLevelNames = new LinkedHashMap<>();

    /**
     * Constructs the wizard pages.
     *
     * @param params        The parameters to read initial choices from and write choices to.
     * @param sourceDataSet The long data set (for variable names and levels); may be null, in which case a message
     *                      is shown instead of pages.
     * @param onFinish      Called when the user clicks Finish on the last page.
     * @param onCancel      Called when the user clicks Cancel.
     */
    public LongToWideWizardPanel(Parameters params, DataSet sourceDataSet, Runnable onFinish, Runnable onCancel) {
        this.params = params;
        this.sourceDataSet = sourceDataSet;
        this.onFinish = onFinish;
        this.onCancel = onCancel;
        build();
    }

    /**
     * Returns the index of the page currently shown.
     *
     * @return The page index.
     */
    public int getPage() {
        return this.page;
    }

    /**
     * Whether the current page is complete enough to leave. Page 1 needs at least one row key and a column key
     * (which must not also be a row key); page 2 needs at least one kept value variable; the others always are.
     *
     * @return True if Next (or Finish) is allowed.
     */
    public boolean isPageComplete() {
        return pageProblem() == null;
    }

    /**
     * Advances to the next page if the current one is complete (or finishes on the last page). Exposed for
     * driving the wizard programmatically.
     *
     * @return True if the page changed (or the wizard finished).
     */
    public boolean next() {
        String problem = pageProblem();
        if (problem != null) {
            this.status.setText(problem);
            return false;
        }

        if (this.page == PAGE_SUMMARY) {
            if (this.onFinish != null) this.onFinish.run();
            return true;
        }

        show(this.page + 1);
        return true;
    }

    /**
     * Goes back one page.
     */
    public void back() {
        if (this.page > PAGE_KEYS) show(this.page - 1);
    }

    //================================= Private Methods ===============================//

    private void build() {
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        setPreferredSize(new Dimension(580, 480));

        this.title.setFont(this.title.getFont().deriveFont(Font.BOLD, this.title.getFont().getSize() + 2f));
        add(this.title, BorderLayout.NORTH);

        if (this.sourceDataSet == null) {
            this.pages.add(new JLabel("Long-to-wide requires a tabular data set as parent."), "none");
            add(this.pages, BorderLayout.CENTER);
            this.next.setEnabled(false);
            add(buttonBar(), BorderLayout.SOUTH);
            return;
        }

        this.variables.addAll(this.sourceDataSet.getVariables());
        this.savedLevelNames = LongToWideWrapper.decodeLevelNames(
                this.params.getString(LongToWideWrapper.LEVEL_NAMES, ""));

        this.pages.add(keysPage(), "keys");
        this.pages.add(valuesPage(), "values");
        this.pages.add(namesPage(), "names");
        this.pages.add(summaryPage(), "summary");
        add(this.pages, BorderLayout.CENTER);

        JPanel south = new JPanel(new BorderLayout());
        this.status.setForeground(new Color(150, 40, 40));
        south.add(this.status, BorderLayout.NORTH);
        south.add(buttonBar(), BorderLayout.SOUTH);
        add(south, BorderLayout.SOUTH);

        refreshValueRows();
        refreshLevelRows();
        show(PAGE_KEYS);
    }

    private JPanel buttonBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        this.back.addActionListener(e -> back());
        this.next.addActionListener(e -> next());
        this.finish.addActionListener(e -> next());
        this.cancel.addActionListener(e -> {
            if (this.onCancel != null) this.onCancel.run();
        });
        bar.add(this.back);
        bar.add(this.next);
        bar.add(this.finish);
        bar.add(this.cancel);
        return bar;
    }

    private void show(int index) {
        this.page = index;
        this.cards.show(this.pages, new String[]{"keys", "values", "names", "summary"}[index]);
        this.title.setText(TITLES[index]);
        this.status.setText(" ");
        this.back.setEnabled(index > PAGE_KEYS);
        this.next.setVisible(index < PAGE_SUMMARY);
        this.finish.setVisible(index == PAGE_SUMMARY);
        if (index == PAGE_SUMMARY) refreshSummary();
    }

    /**
     * A description of what stops the user from leaving the current page, or null if nothing does.
     */
    private String pageProblem() {
        if (this.sourceDataSet == null) return "No data set.";

        if (this.page == PAGE_KEYS) {
            List<String> rowKeys = this.rowKeyList.getSelectedValuesList();
            Object columnKey = this.columnKeyBox.getSelectedItem();
            if (rowKeys.isEmpty()) return "Choose at least one row key variable.";
            if (columnKey == null) return "Choose a column key variable (the data has no discrete variable).";
            if (rowKeys.contains((String) columnKey)) return "The column key cannot also be a row key.";
        } else if (this.page == PAGE_VALUES) {
            boolean any = false;
            for (ValueRow r : this.valueModel.rows) any |= r.keep;
            if (!any) return "Keep at least one value variable.";
            for (ValueRow r : this.valueModel.rows) {
                if (r.keep && r.discrete) {
                    LongToWide.Aggregation agg = r.aggregation == null ? currentDefaultAggregation() : r.aggregation;
                    if (!(agg == LongToWide.Aggregation.FAIL || agg == LongToWide.Aggregation.FIRST
                            || agg == LongToWide.Aggregation.LAST || agg == LongToWide.Aggregation.COUNT)) {
                        return "'" + r.name + "' is discrete: its aggregation must be FAIL, FIRST, LAST, or COUNT.";
                    }
                }
            }
        }
        return null;
    }

    // ---- Page 1: Keys ----

    private JPanel keysPage() {
        List<String> names = new ArrayList<>();
        for (Node v : this.variables) names.add(v.getName());

        List<String> savedRowKeys = LongToWideWrapper.split(this.params.getString(LongToWideWrapper.ROW_KEYS, ""));
        String savedColumnKey = this.params.getString(LongToWideWrapper.COLUMN_KEY, "");

        this.rowKeyList = new JList<>(names.toArray(new String[0]));
        this.rowKeyList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) if (savedRowKeys.contains(names.get(i))) idx.add(i);
        this.rowKeyList.setSelectedIndices(idx.stream().mapToInt(Integer::intValue).toArray());

        List<String> discreteNames = new ArrayList<>();
        for (Node v : this.variables) if (v instanceof DiscreteVariable) discreteNames.add(v.getName());
        this.columnKeyBox = new JComboBox<>(discreteNames.toArray(new String[0]));
        if (discreteNames.contains(savedColumnKey)) {
            this.columnKeyBox.setSelectedItem(savedColumnKey);
        } else if (!discreteNames.isEmpty()) {
            this.columnKeyBox.setSelectedIndex(0);
        }

        JScrollPane rowKeyScroll = new JScrollPane(this.rowKeyList);
        rowKeyScroll.setPreferredSize(new Dimension(300, 160));
        rowKeyScroll.setMinimumSize(new Dimension(200, 120));

        JPanel rowKeyPanel = new JPanel(new BorderLayout(4, 4));
        rowKeyPanel.add(new JLabel("Row key variable(s) identifying a unit (ctrl-click to select several):"), BorderLayout.NORTH);
        rowKeyPanel.add(rowKeyScroll, BorderLayout.CENTER);

        JPanel columnKeyRow = new JPanel(new BorderLayout(8, 0));
        columnKeyRow.add(new JLabel("Column key variable (discrete; levels become column suffixes):"), BorderLayout.NORTH);
        columnKeyRow.add(this.columnKeyBox, BorderLayout.CENTER);

        JPanel south = new JPanel(new BorderLayout(4, 8));
        south.add(columnKeyRow, BorderLayout.NORTH);
        south.add(wrappedNote("Each distinct combination of row key values becomes one wide row; each level of the "
                + "column key becomes a column suffix. Absent (unit, level) combinations become missing values."), BorderLayout.CENTER);

        JPanel keys = new JPanel(new BorderLayout(4, 12));
        keys.add(rowKeyPanel, BorderLayout.CENTER);
        keys.add(south, BorderLayout.SOUTH);

        this.rowKeyList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) refreshValueRows();
        });
        this.columnKeyBox.addActionListener(e -> {
            refreshValueRows();
            refreshLevelRows();
        });
        return keys;
    }

    // ---- Page 2: Value variables ----

    private JPanel valuesPage() {
        this.valueModel = new ValueTableModel();
        JTable valueTable = new JTable(this.valueModel);
        valueTable.setRowHeight(22);
        TableColumn aggCol = valueTable.getColumnModel().getColumn(2);
        aggCol.setCellEditor(new DefaultCellEditor(new JComboBox<>(LongToWide.Aggregation.values())));
        valueTable.getColumnModel().getColumn(0).setMaxWidth(60);
        valueTable.getColumnModel().getColumn(2).setMaxWidth(110);

        JScrollPane valueScroll = new JScrollPane(valueTable);
        valueScroll.setPreferredSize(new Dimension(460, 200));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton all = new JButton("Select all");
        JButton none = new JButton("Select none");
        all.addActionListener(e -> this.valueModel.setAllKept(true));
        none.addActionListener(e -> this.valueModel.setAllKept(false));
        buttons.add(all);
        buttons.add(none);
        buttons.add(new JLabel("   Default for duplicates:"));
        this.defaultAggregationBox = new JComboBox<>(LongToWide.Aggregation.values());
        this.defaultAggregationBox.setSelectedItem(LongToWide.Aggregation.valueOf(
                this.params.getString(LongToWideWrapper.DEFAULT_AGGREGATION, LongToWide.Aggregation.FAIL.name())));
        this.defaultAggregationBox.addActionListener(e -> {
            this.valueModel.fireTableDataChanged();
            writeParams();
        });
        buttons.add(this.defaultAggregationBox);

        JPanel south = new JPanel(new BorderLayout());
        south.add(buttons, BorderLayout.NORTH);
        south.add(wrappedNote("Each kept variable gives one wide column per level. A (unit, level) combination with "
                + "more than one long row is resolved by its Duplicates aggregation; FAIL stops with a count so "
                + "nothing is collapsed silently. Discrete variables allow FIRST, LAST, and COUNT. COUNT gives the "
                + "number of long rows as a continuous column (0 where a unit has none)."), BorderLayout.CENTER);

        JPanel values = new JPanel(new BorderLayout(4, 4));
        values.add(new JLabel("Value variables to spread (one wide column per level of the column key):"), BorderLayout.NORTH);
        values.add(valueScroll, BorderLayout.CENTER);
        values.add(south, BorderLayout.SOUTH);
        return values;
    }

    // ---- Page 3: Column names ----

    private JPanel namesPage() {
        this.levelModel = new LevelTableModel();
        JTable levelTable = new JTable(this.levelModel);
        levelTable.setRowHeight(22);
        JScrollPane levelScroll = new JScrollPane(levelTable);
        levelScroll.setPreferredSize(new Dimension(460, 200));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton suggest = new JButton("Suggest short names");
        JButton fullNames = new JButton("Use full names");
        this.maxLengthSpinner = new JSpinner(new SpinnerNumberModel(24, 4, 200, 1));
        suggest.addActionListener(e -> this.levelModel.suggest((Integer) this.maxLengthSpinner.getValue()));
        fullNames.addActionListener(e -> this.levelModel.clearNames());
        buttons.add(suggest);
        buttons.add(new JLabel("max length:"));
        buttons.add(this.maxLengthSpinner);
        buttons.add(fullNames);

        this.includeValueNameBox = new JCheckBox("Prefix with value variable name",
                this.params.getBoolean(LongToWideWrapper.INCLUDE_VALUE_NAME, true));
        this.includeValueNameBox.setToolTipText("Name columns value.level rather than level alone; always done when "
                + "more than one value variable is spread, since the level alone would not be unique.");
        this.includeValueNameBox.addActionListener(e -> writeParams());
        this.separatorField = new JTextField(this.params.getString(LongToWideWrapper.SEPARATOR, "."), 3);
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
        JPanel prefixRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        prefixRow.add(this.includeValueNameBox);
        prefixRow.add(new JLabel("separator:"));
        prefixRow.add(this.separatorField);

        JPanel south = new JPanel(new BorderLayout());
        south.add(buttons, BorderLayout.NORTH);
        south.add(prefixRow, BorderLayout.CENTER);
        south.add(wrappedNote("Edit the right-hand column to choose the suffix used for each level; blank means the "
                + "level's own name. 'Suggest short names' drops words shared by most levels and keeps the "
                + "distinguishing ones; review the result."), BorderLayout.SOUTH);

        JPanel names = new JPanel(new BorderLayout(4, 4));
        names.add(new JLabel("Column suffix per level of the column key:"), BorderLayout.NORTH);
        names.add(levelScroll, BorderLayout.CENTER);
        names.add(south, BorderLayout.SOUTH);
        return names;
    }

    // ---- Page 4: Summary ----

    private JPanel summaryPage() {
        this.sanitizeBox = new JCheckBox("Sanitize names (punctuation becomes _)",
                this.params.getBoolean(LongToWideWrapper.SANITIZE_NAMES, true));
        this.keepKeysBox = new JCheckBox("Keep row key columns",
                this.params.getBoolean(LongToWideWrapper.KEEP_ROW_KEYS, false));
        this.sanitizeBox.addActionListener(e -> {
            writeParams();
            refreshSummary();
        });
        this.keepKeysBox.addActionListener(e -> {
            writeParams();
            refreshSummary();
        });

        JPanel options = new JPanel(new FlowLayout(FlowLayout.LEFT));
        options.add(this.sanitizeBox);
        options.add(this.keepKeysBox);

        this.summaryArea = new JTextArea();
        this.summaryArea.setEditable(false);
        this.summaryArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scroll = new JScrollPane(this.summaryArea);
        scroll.setPreferredSize(new Dimension(460, 220));

        JPanel summary = new JPanel(new BorderLayout(4, 4));
        summary.add(options, BorderLayout.NORTH);
        summary.add(scroll, BorderLayout.CENTER);
        summary.add(wrappedNote("Review the configuration and the columns it will produce, then click Finish to "
                + "run the transform. The findings report (units, levels, fill rate per column, duplicates) is "
                + "written to the log."), BorderLayout.SOUTH);
        return summary;
    }

    /**
     * Rewrites the summary text from the current parameters, including a preview of the wide column names.
     */
    private void refreshSummary() {
        if (this.summaryArea == null) return;
        writeParams();

        StringBuilder b = new StringBuilder();
        b.append("Row key(s):     ").append(this.params.getString(LongToWideWrapper.ROW_KEYS, "")).append('\n');
        b.append("Column key:     ").append(this.params.getString(LongToWideWrapper.COLUMN_KEY, "")).append('\n');

        List<String> kept = new ArrayList<>();
        for (ValueRow r : this.valueModel.rows) if (r.keep) kept.add(r.name);
        b.append("Value variables: ").append(kept.size()).append(" of ").append(this.valueModel.rows.size())
                .append(kept.size() <= 6 ? " " + kept : "").append('\n');
        b.append("Duplicates:     default ").append(currentDefaultAggregation());
        String aggs = this.params.getString(LongToWideWrapper.AGGREGATIONS, "");
        if (!aggs.isEmpty()) b.append("; overrides ").append(aggs);
        b.append('\n');

        try {
            LongToWide t = LongToWideWrapper.fromParameters(this.params);
            List<String> names = t.columnNames(this.sourceDataSet);
            b.append("Wide columns:   ").append(names.size()).append('\n');
            int shown = 0;
            for (String name : names) {
                b.append("  ").append(name).append('\n');
                if (++shown == 60 && names.size() > 60) {
                    b.append("  ... and ").append(names.size() - 60).append(" more\n");
                    break;
                }
            }
        } catch (RuntimeException e) {
            b.append("Cannot preview columns: ").append(e.getMessage()).append('\n');
        }

        this.summaryArea.setText(b.toString());
        this.summaryArea.setCaretPosition(0);
    }

    /**
     * Returns the current summary text (for checks without a display).
     *
     * @return The text, or null before the summary page has been built.
     */
    public String getSummaryText() {
        return this.summaryArea == null ? null : this.summaryArea.getText();
    }

    // ---- Shared ----

    private LongToWide.Aggregation currentDefaultAggregation() {
        return (LongToWide.Aggregation) this.defaultAggregationBox.getSelectedItem();
    }

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
     * Rebuilds the value-variable rows as the non-key variables, preserving saved keep/aggregation choices.
     */
    private void refreshValueRows() {
        if (this.valueModel == null) return;
        List<String> rowKeys = this.rowKeyList.getSelectedValuesList();
        String columnKey = (String) this.columnKeyBox.getSelectedItem();

        List<String> savedValues = LongToWideWrapper.split(this.params.getString(LongToWideWrapper.VALUE_VARIABLES, ""));
        List<String> savedAggs = LongToWideWrapper.split(this.params.getString(LongToWideWrapper.AGGREGATIONS, ""));
        Map<String, ValueRow> current = new LinkedHashMap<>();
        for (ValueRow r : this.valueModel.rows) current.put(r.name, r);

        List<ValueRow> rows = new ArrayList<>();
        for (Node v : this.variables) {
            String name = v.getName();
            if (rowKeys.contains(name) || name.equals(columnKey)) continue;

            ValueRow row = current.get(name);
            if (row == null) {
                row = new ValueRow();
                row.name = name;
                row.discrete = v instanceof DiscreteVariable;
                row.keep = savedValues.isEmpty() || savedValues.contains(name);
                for (String pair : savedAggs) {
                    if (pair.startsWith(name + "=")) {
                        try {
                            row.aggregation = LongToWide.Aggregation.valueOf(pair.substring(name.length() + 1));
                        } catch (IllegalArgumentException ignored) {
                            // Stale value; leave as default.
                        }
                    }
                }
            }
            rows.add(row);
        }

        this.valueModel.setRows(rows);
        writeParams();
    }

    /**
     * Rebuilds the level rows for the current column key, preserving saved and session short names.
     */
    private void refreshLevelRows() {
        if (this.levelModel == null) return;
        String columnKey = (String) this.columnKeyBox.getSelectedItem();
        List<String> levels = new ArrayList<>();
        for (Node v : this.variables) {
            if (v.getName().equals(columnKey) && v instanceof DiscreteVariable dv) levels.addAll(dv.getCategories());
        }
        Map<String, String> saved = new LinkedHashMap<>(this.savedLevelNames);
        saved.putAll(this.levelModel.names());
        this.levelModel.setLevels(levels, saved);
        writeParams();
    }

    private void writeParams() {
        if (this.params == null || this.valueModel == null || this.levelModel == null || this.sanitizeBox == null) return;

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
        this.params.set(LongToWideWrapper.DEFAULT_AGGREGATION, currentDefaultAggregation().name());
        this.params.set(LongToWideWrapper.SANITIZE_NAMES, this.sanitizeBox.isSelected());
        this.params.set(LongToWideWrapper.KEEP_ROW_KEYS, this.keepKeysBox.isSelected());
        String sep = this.separatorField.getText();
        this.params.set(LongToWideWrapper.SEPARATOR, sep.isEmpty() ? "." : sep);
        this.params.set(LongToWideWrapper.INCLUDE_VALUE_NAME, this.includeValueNameBox.isSelected());
        this.params.set(LongToWideWrapper.LEVEL_NAMES, LongToWideWrapper.encodeLevelNames(this.levelModel.names()));
    }

    // ---- Table models ----

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
                default -> r.aggregation == null ? currentDefaultAggregation() : r.aggregation;
            };
        }

        public void setValueAt(Object value, int row, int col) {
            ValueRow r = this.rows.get(row);
            if (col == 0) {
                r.keep = Boolean.TRUE.equals(value);
            } else if (col == 2 && value instanceof LongToWide.Aggregation agg) {
                r.aggregation = agg == currentDefaultAggregation() ? null : agg;
            }
            fireTableRowsUpdated(row, row);
            writeParams();
        }
    }

    private final class LevelTableModel extends AbstractTableModel {
        private final List<String> levels = new ArrayList<>();
        private final List<String> shortNames = new ArrayList<>(); // "" means: use the level itself

        void setLevels(List<String> newLevels, Map<String, String> saved) {
            this.levels.clear();
            this.shortNames.clear();
            for (String level : newLevels) {
                this.levels.add(level);
                this.shortNames.add(saved.getOrDefault(level, ""));
            }
            fireTableDataChanged();
        }

        void suggest(int maxLength) {
            Map<String, String> s = LongToWide.suggestShortLevelNames(this.levels, maxLength);
            for (int i = 0; i < this.levels.size(); i++) this.shortNames.set(i, s.get(this.levels.get(i)));
            fireTableDataChanged();
            writeParams();
        }

        void clearNames() {
            java.util.Collections.fill(this.shortNames, "");
            fireTableDataChanged();
            writeParams();
        }

        Map<String, String> names() {
            Map<String, String> m = new LinkedHashMap<>();
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
}
