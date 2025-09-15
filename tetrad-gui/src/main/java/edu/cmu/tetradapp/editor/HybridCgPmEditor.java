package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.hybridcg.HybridCgModel.HybridCgPm;
import edu.cmu.tetradapp.model.HybridCgPmWrapper;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Mixed PM editor panel: lets you edit per-variable type (CONTINUOUS/DISCRETE) and (if discrete) the category labels as
 * a comma-separated list.
 * <p>
 * How to use: HybridCgPmEditor panel = HybridCgPmEditor.fromWrapper(mixedPmWrapper); HybridCgPmEditor.showDialog(panel,
 * "Edit Hybrid CG PM"); // modal dialog helper
 * <p>
 * This editor does not mutate HybridCgPm in place (it is effectively immutable). On Apply we *rebuild* a new HybridCgPm
 * with the chosen types/categories and set it back into the wrapper. Existing cutpoints for discrete children with
 * continuous parents are preserved when possible.
 */
public class HybridCgPmEditor extends JPanel {

    private final MixedPmAccess access;
    private final MixedPmTableModel model;

//    // (optional) keep the factory for convenience; now just delegates
//    public static HybridCgPmEditor fromWrapper(HybridCgPmWrapper wrapper) {
//        return new HybridCgPmEditor(wrapper);
//    }
    private final JTable table;

    // ADD: public ctor for reflection
    public HybridCgPmEditor(HybridCgPmWrapper wrapper) {
        this(buildAccess(wrapper));
    }

    private HybridCgPmEditor(MixedPmAccess access) {
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

    // --- helper that builds the adapter used by the editor ---
    private static RebuildableAccess buildAccess(HybridCgPmWrapper wrapper) {
        Objects.requireNonNull(wrapper, "wrapper");

        final HybridCgPm current = wrapper.getHybridCgPm();
        final Graph graph = current.getGraph();
        final List<Node> nodeOrder = new ArrayList<>(graph.getNodes());

        final Map<Node, VarType> typeMap = new LinkedHashMap<>();
        final Map<Node, List<String>> catsMap = new LinkedHashMap<>();

        // Initialize from existing PM
        for (Node v : nodeOrder) {
            int idx = current.indexOf(v);
            boolean isDisc = current.isDiscrete(idx);
            List<String> cats = isDisc ? current.getCategories(idx) : List.of();
            typeMap.put(v, isDisc ? VarType.DISCRETE : VarType.CONTINUOUS);
            catsMap.put(v, new ArrayList<>(cats == null ? List.of() : cats));
        }

        // Preserve existing cutpoints where applicable
        final Map<Node, Map<Node, double[]>> savedCuts = new HashMap<>();
        for (Node child : nodeOrder) {
            int y = current.indexOf(child);
            if (!current.isDiscrete(y)) continue;
            int[] cps = current.getContinuousParents(y);
            if (cps.length == 0) continue;
            Map<Node, double[]> m = new LinkedHashMap<>();
            current.getContParentCutpointsForDiscreteChild(y).ifPresent(cuts -> {
                for (int t = 0; t < cps.length; t++) {
                    m.put(current.getNodes()[cps[t]], cuts[t].clone());
                }
            });
            if (!m.isEmpty()) savedCuts.put(child, m);
        }

        return new RebuildableAccess() {
            @Override
            public Graph getGraph() {
                return graph;
            }

            @Override
            public List<Node> getVariables() {
                return nodeOrder;
            }

            @Override
            public VarType getType(Node v) {
                return typeMap.getOrDefault(v, VarType.CONTINUOUS);
            }

            @Override
            public void setType(Node v, VarType t) {
                typeMap.put(v, t == null ? VarType.CONTINUOUS : t);
            }

            @Override
            public List<String> getCategories(Node v) {
                return new ArrayList<>(catsMap.getOrDefault(v, List.of()));
            }

            @Override
            public void setCategories(Node v, List<String> labels) {
                catsMap.put(v, new ArrayList<>(labels == null ? List.of() : labels));
            }

            @Override
            public void commit() {
                Map<Node, Boolean> isDisc = new LinkedHashMap<>();
                Map<Node, List<String>> cats = new LinkedHashMap<>();
                for (Node v : nodeOrder) {
                    boolean d = typeMap.getOrDefault(v, VarType.CONTINUOUS) == VarType.DISCRETE;
                    isDisc.put(v, d);
                    cats.put(v, d ? new ArrayList<>(catsMap.getOrDefault(v, List.of())) : null);
                }

                HybridCgPm newPm = new HybridCgPm(graph, nodeOrder, isDisc, cats);

                // Re-apply preserved cutpoints where still compatible
                for (Map.Entry<Node, Map<Node, double[]>> e : savedCuts.entrySet()) {
                    Node child = e.getKey();
                    if (typeMap.get(child) != VarType.DISCRETE) continue;
                    int y = newPm.indexOf(child);
                    int[] cps = newPm.getContinuousParents(y);
                    if (cps.length == 0) continue;

                    Map<Node, double[]> cpMap = new LinkedHashMap<>();
                    for (int t = 0; t < cps.length; t++) {
                        Node p = newPm.getNodes()[cps[t]];
                        double[] keep = e.getValue().get(p);
                        if (keep != null) cpMap.put(p, keep.clone());
                    }
                    if (!cpMap.isEmpty()) {
                        try {
                            newPm.setContParentCutpointsForDiscreteChild(child, cpMap);
                        } catch (IllegalStateException | IllegalArgumentException ignored) {
                            // If incompatible, skip; user can set new cuts elsewhere.
                        }
                    }
                }

                // Requires a setter on the wrapper:
                wrapper.setHybridCgPm(newPm);
            }
        };
    }

    /**
     * Build an editor panel from your HybridCgPmWrapper. The adapter maintains an editable map of types/categories and
     * rebuilds the PM on Apply.
     */
    public static HybridCgPmEditor fromWrapper(HybridCgPmWrapper wrapper) {
        Objects.requireNonNull(wrapper, "wrapper");

        // Editable views of type/category state
        final HybridCgPm current = wrapper.getHybridCgPm();
        final Graph graph = current.getGraph();
        final List<Node> nodeOrder = new ArrayList<>(graph.getNodes());

        final Map<Node, HybridCgPmEditor.VarType> typeMap = new LinkedHashMap<>();
        final Map<Node, List<String>> catsMap = new LinkedHashMap<>();

        // Initialize from existing PM
        for (Node v : nodeOrder) {
            int idx = current.indexOf(v);
            boolean isDisc = current.isDiscrete(idx);
            List<String> cats = isDisc ? current.getCategories(idx) : List.of();
            typeMap.put(v, isDisc ? VarType.DISCRETE : VarType.CONTINUOUS);
            catsMap.put(v, new ArrayList<>(cats == null ? List.of() : cats));
        }

        // Preserve existing cutpoints if present
        final Map<Node, Map<Node, double[]>> savedCuts = new HashMap<>();
        for (Node child : nodeOrder) {
            int y = current.indexOf(child);
            if (!current.isDiscrete(y)) continue;
            int[] cps = current.getContinuousParents(y);
            if (cps.length == 0) continue;
            Map<Node, double[]> m = new LinkedHashMap<>();
            Optional<double[][]> maybe = current.getContParentCutpointsForDiscreteChild(y);
            if (maybe.isPresent()) {
                double[][] cuts = maybe.get();
                for (int t = 0; t < cps.length; t++) {
                    m.put(current.getNodes()[cps[t]], cuts[t].clone());
                }
            }
            if (!m.isEmpty()) savedCuts.put(child, m);
        }

        RebuildableAccess access = new RebuildableAccess() {
            @Override
            public Graph getGraph() {
                return graph;
            }

            @Override
            public List<Node> getVariables() {
                return nodeOrder;
            }

            @Override
            public VarType getType(Node v) {
                return typeMap.getOrDefault(v, VarType.CONTINUOUS);
            }

            @Override
            public void setType(Node v, VarType t) {
                typeMap.put(v, t == null ? VarType.CONTINUOUS : t);
            }

            @Override
            public List<String> getCategories(Node v) {
                return new ArrayList<>(catsMap.getOrDefault(v, List.of()));
            }

            @Override
            public void setCategories(Node v, List<String> labels) {
                catsMap.put(v, new ArrayList<>(labels == null ? List.of() : labels));
            }

            /** Rebuild a new PM from the edited maps and set it back into the wrapper. */
            @Override
            public void commit() {
                Map<Node, Boolean> isDisc = new LinkedHashMap<>();
                Map<Node, List<String>> cats = new LinkedHashMap<>();
                for (Node v : nodeOrder) {
                    boolean d = typeMap.getOrDefault(v, VarType.CONTINUOUS) == VarType.DISCRETE;
                    isDisc.put(v, d);
                    cats.put(v, d ? new ArrayList<>(catsMap.getOrDefault(v, List.of())) : null);
                }

                HybridCgPm newPm = new HybridCgPm(graph, nodeOrder, isDisc, cats);

                // Re-apply preserved cutpoints where the child remains discrete and parents remain continuous
                for (Map.Entry<Node, Map<Node, double[]>> e : savedCuts.entrySet()) {
                    Node child = e.getKey();
                    if (typeMap.get(child) != VarType.DISCRETE) continue; // no longer discrete
                    // Filter to those continuous parents still present
                    int y = newPm.indexOf(child);
                    int[] cps = newPm.getContinuousParents(y);
                    if (cps.length == 0) continue;

                    Map<Node, double[]> cpMap = new LinkedHashMap<>();
                    for (int t = 0; t < cps.length; t++) {
                        Node p = newPm.getNodes()[cps[t]];
                        double[] keep = e.getValue().get(p);
                        if (keep != null) cpMap.put(p, keep.clone());
                    }
                    if (!cpMap.isEmpty()) {
                        try {
                            newPm.setContParentCutpointsForDiscreteChild(child, cpMap);
                        } catch (IllegalStateException | IllegalArgumentException ignored) {
                            // If incompatible, skip silently; user can set new cuts later in a separate UI.
                        }
                    }
                }

                // Store back into the wrapper (requires setter in wrapper)
                wrapper.setHybridCgPm(newPm);
            }
        };

        return new HybridCgPmEditor(access);
    }

    /**
     * Show the panel in a modal dialog.
     */
    public static void showDialog(HybridCgPmEditor panel, String title) {
        JDialog d = new JDialog((Frame) null, title, true);
        d.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        d.setContentPane(panel);
        d.setSize(900, 500);
        d.setLocationRelativeTo(null);
        d.setVisible(true);
    }

    /**
     * Commit the table state back into the MixedPm via the adapter.
     */
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

        if (access instanceof RebuildableAccess rba) {
            rba.commit();
        }

        JOptionPane.showMessageDialog(this, "Hybrid CG PM updated.", "Apply", JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Re-read types & categories from the MixedPm and replace table rows.
     */
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

    public enum VarType {CONTINUOUS, DISCRETE}

    /**
     * Minimal adapter so the editor is agnostic to the exact MixedPm implementation.
     */
    public interface MixedPmAccess {
        Graph getGraph();

        List<Node> getVariables();

        VarType getType(Node v);

        void setType(Node v, VarType t);

        /**
         * Return the current category labels (empty for continuous).
         */
        List<String> getCategories(Node v);

        /**
         * Set category labels (ignored for continuous).
         */
        void setCategories(Node v, List<String> labels);
    }

    /**
     * Internal hook so Apply can trigger a rebuild/commit.
     */
    private interface RebuildableAccess extends MixedPmAccess {
        void commit();
    }

    // ---------- Helpers to construct the editor ----------

    /**
     * Row backing one variable’s editable state.
     */
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

    /**
     * Table model for the editor.
     */
    public static final class MixedPmTableModel extends AbstractTableModel {
        private static final String[] COLS = {"Variable", "Type", "Categories (comma-separated for discrete)"};
        private final List<Row> rows;

        MixedPmTableModel(List<Row> rows) {
            this.rows = rows;
        }

        @Override
        public int getRowCount() {
            return rows.size();
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
        public Class<?> getColumnClass(int columnIndex) {
            return switch (columnIndex) {
                case 0 -> String.class;
                case 1 -> VarType.class;
                case 2 -> String.class;
                default -> Object.class;
            };
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            if (columnIndex == 0) return false; // Variable name not editable here
            if (columnIndex == 2) return rows.get(rowIndex).type == VarType.DISCRETE;
            return true;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            Row r = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> r.node.getName();
                case 1 -> r.type;
                case 2 -> String.join(", ", r.categories);
                default -> null;
            };
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
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

        public List<Row> rows() {
            return rows;
        }
    }
}