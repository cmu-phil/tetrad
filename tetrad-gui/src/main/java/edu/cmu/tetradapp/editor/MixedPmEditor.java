// Adjust the package to match your GUI module (often: edu.cmu.tetradapp.editor or .gui)
package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetradapp.model.MixedPmWrapper;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Mixed PM editor panel: lets you edit per-variable type (CONTINUOUS/DISCRETE) and
 * (if discrete) the category labels as a comma-separated list.
 *
 * How to use:
 *   MixedPmEditor panel = MixedPmEditor.fromWrapper(mixedPmWrapper);
 *   MixedPmEditor.showDialog(panel, "Edit Mixed PM"); // modal dialog helper
 *
 * Wiring to your MixedPm:
 * - Implement the adapter in the fromWrapper(...) method below to match your MixedPm API.
 * - If your MixedPm already knows variable types and categories, forward get/set calls.
 * - If not, you can store these in MixedPm’s state or serialize alongside the graph.
 */
public class MixedPmEditor extends JPanel {

    public enum VarType { CONTINUOUS, DISCRETE }

    /** Row backing one variable’s editable state. */
    public static final class Row {
        final Node node;
        VarType type;
        List<String> categories; // only used if DISCRETE

        Row(Node node, VarType type, List<String> categories) {
            this.node = node;
            this.type = type;
            this.categories = new ArrayList<>(categories == null ? List.of() : categories);
        }
    }

    /** Minimal adapter so the editor is agnostic to the exact MixedPm implementation. */
    public interface MixedPmAccess {
        Graph getGraph();
        List<Node> getVariables();

        VarType getType(Node v);
        void setType(Node v, VarType t);

        /** Return the current category labels (empty for continuous). */
        List<String> getCategories(Node v);

        /** Set category labels (ignored for continuous). */
        void setCategories(Node v, List<String> labels);
    }

    /** Table model for the editor. */
    public static final class MixedPmTableModel extends AbstractTableModel {
        private final List<Row> rows;
        private static final String[] COLS = {"Variable", "Type", "Categories (comma-separated for discrete)"};

        MixedPmTableModel(List<Row> rows) {
            this.rows = rows;
        }

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return COLS.length; }
        @Override public String getColumnName(int column) { return COLS[column]; }

        @Override public Class<?> getColumnClass(int columnIndex) {
            return switch (columnIndex) {
                case 0 -> String.class;
                case 1 -> VarType.class;
                case 2 -> String.class;
                default -> Object.class;
            };
        }

        @Override public boolean isCellEditable(int rowIndex, int columnIndex) {
            if (columnIndex == 0) return false; // Variable name not editable here
            if (columnIndex == 2) return rows.get(rowIndex).type == VarType.DISCRETE;
            return true;
        }

        @Override public Object getValueAt(int rowIndex, int columnIndex) {
            Row r = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> r.node.getName();
                case 1 -> r.type;
                case 2 -> String.join(", ", r.categories);
                default -> null;
            };
        }

        @Override public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            Row r = rows.get(rowIndex);
            switch (columnIndex) {
                case 1 -> {
                    VarType t = (aValue instanceof VarType) ? (VarType) aValue
                            : VarType.valueOf(String.valueOf(aValue));
                    r.type = t;
                    if (t == VarType.CONTINUOUS) r.categories.clear();
                    fireTableRowsUpdated(rowIndex, rowIndex);
                }
                case 2 -> {
                    String s = aValue == null ? "" : aValue.toString().trim();
                    if (s.isEmpty()) r.categories = new ArrayList<>();
                    else {
                        String[] parts = Arrays.stream(s.split(","))
                                .map(String::trim)
                                .filter(p -> !p.isEmpty())
                                .toArray(String[]::new);
                        r.categories = new ArrayList<>(Arrays.asList(parts));
                    }
                    fireTableCellUpdated(rowIndex, columnIndex);
                }
            }
        }

        public List<Row> rows() { return rows; }
    }

    private final MixedPmAccess access;
    private final MixedPmTableModel model;
    private final JTable table;

    private MixedPmEditor(MixedPmAccess access) {
        super(new BorderLayout());
        this.access = Objects.requireNonNull(access, "access");

        List<Row> rows = new ArrayList<>();
        for (Node v : access.getVariables()) {
            VarType t = access.getType(v);
            List<String> cats = access.getCategories(v);
            rows.add(new Row(v, t == null ? VarType.CONTINUOUS : t, cats));
        }
        this.model = new MixedPmTableModel(rows);

        this.table = new JTable(model);
        this.table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        this.table.setRowHeight(22);

        // Type column as a combobox
        JComboBox<VarType> typeCombo = new JComboBox<>(VarType.values());
        TableCellEditor typeEditor = new DefaultCellEditor(typeCombo);
        table.getColumnModel().getColumn(1).setCellEditor(typeEditor);

        // Grey out categories when continuous
        table.getColumnModel().getColumn(2).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                           boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                VarType t = (VarType) table.getValueAt(row, 1);
                c.setEnabled(t == VarType.DISCRETE);
                if (!isSelected) {
                    c.setForeground(t == VarType.DISCRETE ? Color.DARK_GRAY : Color.GRAY);
                }
                return c;
            }
        });

        // Toolbar
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        JButton apply = new JButton("Apply to Model");
        JButton revert = new JButton("Revert");
        bar.add(apply);
        bar.add(revert);

        apply.addActionListener(e -> applyToModel());
        revert.addActionListener(e -> revertFromModel());

        add(bar, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);

        JLabel hint = new JLabel("Tip: Set Type=DISCRETE to enable category editing. Categories are comma-separated.");
        hint.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        add(hint, BorderLayout.SOUTH);
    }

    /** Commit the table state back into the MixedPm via the adapter. */
    public void applyToModel() {
        // stop any active edit first
        if (table.isEditing()) table.getCellEditor().stopCellEditing();

        for (Row r : model.rows()) {
            access.setType(r.node, r.type);
            if (r.type == VarType.DISCRETE) {
                access.setCategories(r.node, r.categories);
            } else {
                access.setCategories(r.node, List.of()); // clear / ignore
            }
        }
        JOptionPane.showMessageDialog(this, "Mixed PM updated.", "Apply", JOptionPane.INFORMATION_MESSAGE);
    }

    /** Re-read types & categories from the MixedPm and replace table rows. */
    public void revertFromModel() {
        List<Row> rows = model.rows();
        rows.clear();
        for (Node v : access.getVariables()) {
            VarType t = access.getType(v);
            List<String> cats = access.getCategories(v);
            rows.add(new Row(v, t == null ? VarType.CONTINUOUS : t, cats));
        }
        model.fireTableDataChanged();
    }

    // ---------- Helpers to construct the editor ----------

    /**
     * Build an editor panel from your MixedPmWrapper.
     * Wire the adapter to your actual MixedPm API in the marked section.
     */
    public static MixedPmEditor fromWrapper(MixedPmWrapper wrapper) {
        Objects.requireNonNull(wrapper, "wrapper");
        MixedPmAccess access = new MixedPmAccess() {
            // TODO: Replace MixedPmWrapper.MixedPm with your real MixedPm type and its API.
            private final MixedPmWrapper.MixedPm pm = wrapper.getMixedPm();

            @Override public Graph getGraph() { return pm.getGraph(); }
            @Override public List<Node> getVariables() { return new ArrayList<>(pm.getGraph().getNodes()); }

            @Override public VarType getType(Node v) {
                // TODO wire to your MixedPm: e.g., pm.isDiscrete(v) ? VarType.DISCRETE : VarType.CONTINUOUS
                // Temporary default: treat nodes with non-empty categories as DISCRETE.
                List<String> cats = getCategories(v);
                return (cats != null && !cats.isEmpty()) ? VarType.DISCRETE : VarType.CONTINUOUS;
            }

            @Override public void setType(Node v, VarType t) {
                // TODO wire to your MixedPm: e.g., pm.setDiscrete(v, t==DISCRETE)
                // If your MixedPm stores categories only for discrete, you may want to clear on CONTINUOUS.
            }

            @Override public List<String> getCategories(Node v) {
                // TODO wire to your MixedPm: e.g., pm.getCategories(v)
                // Placeholder: return empty => all start continuous
                return List.of();
            }

            @Override public void setCategories(Node v, List<String> labels) {
                // TODO wire to your MixedPm: e.g., pm.setCategories(v, labels)
            }
        };
        return new MixedPmEditor(access);
    }

    /** Show the panel in a modal dialog. */
    public static void showDialog(MixedPmEditor panel, String title) {
        JDialog d = new JDialog((Frame) null, title, true);
        d.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        d.setContentPane(panel);
        d.setSize(900, 500);
        d.setLocationRelativeTo(null);
        d.setVisible(true);
    }
}