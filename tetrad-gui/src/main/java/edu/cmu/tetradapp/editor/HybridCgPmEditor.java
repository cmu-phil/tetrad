package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.hybridcg.HybridCgModel.HybridCgPm;
import edu.cmu.tetradapp.model.HybridCgPmWrapper;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.util.*;
import java.util.List;

public class HybridCgPmEditor extends JPanel {

    public enum VarType { CONTINUOUS, DISCRETE }

    // --- Reflection-friendly ctor ---
    public HybridCgPmEditor(HybridCgPmWrapper wrapper) {
        this(buildAccess(wrapper));
    }

    // Optional helper if you still want it
    public static HybridCgPmEditor fromWrapper(HybridCgPmWrapper wrapper) {
        return new HybridCgPmEditor(wrapper);
    }

    /** Per-row editable state. */
    public static final class Row {
        final Node node;
        VarType type;
        int catCount;              // desired number of categories (if DISCRETE)
        List<String> categories;   // labels; must match catCount for validity
        boolean catsValid;         // derived: categories.size() == catCount

        Row(Node node, VarType type, int catCount, List<String> categories) {
            this.node = node;
            this.type = type;
            this.catCount = Math.max(0, catCount);
            this.categories = new ArrayList<>(categories == null ? List.of() : categories);
            this.catsValid = (type == VarType.CONTINUOUS) || (this.categories.size() == this.catCount);
        }
    }

    /** Adapter into PM/wrapper, with immediate commit. */
    private interface RebuildableAccess {
        Graph getGraph();
        List<Node> getVariables();
        VarType getType(Node v);
        int getCatCount(Node v);
        List<String> getCategories(Node v);

        void setType(Node v, VarType t);
        void setCatCount(Node v, int n);
        void setCategories(Node v, List<String> labels);

        /** Rebuild a new PM from current maps and set into wrapper; may preserve cutpoints. */
        void commit();
    }

    private final RebuildableAccess access;
    private final MixedPmTableModel model;
    private final JTable table;

    private HybridCgPmEditor(RebuildableAccess access) {
        super(new BorderLayout());
        this.access = access;

        // Top instructions
        JLabel instructions = new JLabel(
                "<html><body style='padding:6px'>" +
                "<b>How to use:</b> Choose <i>Type</i>. For <i>DISCRETE</i>, set <i># Categories</i>." +
                "<br>Editing <i>#Cat</i> auto-fills default labels (cat1..catN). " +
                "<br>You may edit labels; the text turns <span style='color:#b00020'>red</span> " +
                "until the number of labels matches <i>#Cat</i>. " +
                "<br>Changes are applied automatically." +
                "</body></html>");
        instructions.setBorder(BorderFactory.createEmptyBorder(4, 8, 6, 8));
        add(instructions, BorderLayout.NORTH);

        // Seed rows from access
        List<Row> rows = new ArrayList<>();
        for (Node v : access.getVariables()) {
            VarType t = access.getType(v);
            int n = access.getCatCount(v);
            List<String> cats = access.getCategories(v);
            rows.add(new Row(v, t == null ? VarType.CONTINUOUS : t, n, cats));
        }

        this.model = new MixedPmTableModel(rows, access, this);
        this.table = new JTable(model);
        this.table.setRowHeight(22);
        this.table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);

        // Adjust column widths
        SwingUtilities.invokeLater(() -> {
            int padding = 16;
            for (int col = 0; col < table.getColumnCount(); col++) {
                TableColumn column = table.getColumnModel().getColumn(col);

                if (col < 3) {
                    // measure header and content widths
                    int maxWidth = 0;
                    TableCellRenderer headerRenderer = table.getTableHeader().getDefaultRenderer();
                    Component headerComp = headerRenderer.getTableCellRendererComponent(
                            table, column.getHeaderValue(), false, false, 0, col);
                    maxWidth = Math.max(maxWidth, headerComp.getPreferredSize().width);

                    for (int row = 0; row < table.getRowCount(); row++) {
                        TableCellRenderer cellRenderer = table.getCellRenderer(row, col);
                        Component c = table.prepareRenderer(cellRenderer, row, col);
                        maxWidth = Math.max(maxWidth, c.getPreferredSize().width);
                    }

                    column.setPreferredWidth(maxWidth + padding);
                    column.setMinWidth(maxWidth + padding / 2);
                    column.setMaxWidth(maxWidth + padding * 2);
                } else {
                    // Categories column: flexible
                    column.setPreferredWidth(400);
                }
            }
            table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        });

        // Type column as a combobox
        JComboBox<VarType> typeCombo = new JComboBox<>(VarType.values());
        TableCellEditor typeEditor = new DefaultCellEditor(typeCombo);
        table.getColumnModel().getColumn(1).setCellEditor(typeEditor);

        // Categories column renderer colors red when invalid
        table.getColumnModel().getColumn(3).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                           boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                Row r = model.rows().get(table.convertRowIndexToModel(row));
                boolean editable = r.type == VarType.DISCRETE;
                c.setEnabled(editable);
                if (!isSelected) {
                    c.setForeground(!editable ? Color.GRAY : (r.catsValid ? Color.BLACK : new Color(0xB00020)));
                }
                return c;
            }
        });

        add(new JScrollPane(table), BorderLayout.CENTER);
    }

    /** TableModel with auto-commit on each edit. */
    public static final class MixedPmTableModel extends AbstractTableModel {
        private final List<Row> rows;
        private final RebuildableAccess access;

        private static final String[] COLS = {
                "Variable", "Type", "# Categories", "Categories (comma-separated)"
        };
        private final JComponent owner;

        MixedPmTableModel(List<Row> rows, RebuildableAccess access, JComponent owner) {
            this.rows = rows;
            this.access = access;
            this.owner = owner;
        }

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return COLS.length; }
        @Override public String getColumnName(int column) { return COLS[column]; }

        @Override public Class<?> getColumnClass(int columnIndex) {
            return switch (columnIndex) {
                case 0 -> String.class;
                case 1 -> VarType.class;
                case 2 -> Integer.class;
                case 3 -> String.class;
                default -> Object.class;
            };
        }

        @Override public boolean isCellEditable(int rowIndex, int columnIndex) {
            Row r = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> false; // name
                case 1 -> true;  // type
                case 2 -> r.type == VarType.DISCRETE; // #Cat only for discrete
                case 3 -> r.type == VarType.DISCRETE; // cats only for discrete
                default -> false;
            };
        }

        @Override public Object getValueAt(int rowIndex, int columnIndex) {
            Row r = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> r.node.getName();
                case 1 -> r.type;
                case 2 -> r.type == VarType.DISCRETE ? r.catCount : 0;
                case 3 -> String.join(", ", r.categories);
                default -> null;
            };
        }

        @Override public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            Row r = rows.get(rowIndex);
            try {
                switch (columnIndex) {
                    case 1 -> { // Type
                        VarType t = (aValue instanceof VarType) ? (VarType) aValue
                                : VarType.valueOf(String.valueOf(aValue));
                        if (t == r.type) return;
                        r.type = t;
                        if (t == VarType.CONTINUOUS) {
                            r.catCount = 0;
                            r.categories.clear();
                            r.catsValid = true;
                        } else {
                            // ensure at least 2 by default (up to you; 1 also works)
                            if (r.catCount < 2) {
                                r.catCount = Math.max(r.catCount, 2);
                                r.categories = defaultCats(r.catCount);
                            }
                            r.catsValid = (r.categories.size() == r.catCount);
                        }

                        // Apply immediately
                        applyRowToModel(r);

                        fireTableRowsUpdated(rowIndex, rowIndex);
                    }
                    case 2 -> { // #Cat
                        if (r.type != VarType.DISCRETE) return;
                        int n = parsePositiveInt(aValue);
                        if (n < 1) throw new IllegalArgumentException("#Cat must be ≥ 1");
                        r.catCount = n;
                        // Regenerate default labels to help the user
                        r.categories = defaultCats(n);
                        r.catsValid = true;

                        // Apply immediately
                        applyRowToModel(r);

                        fireTableRowsUpdated(rowIndex, rowIndex);
                    }
                    case 3 -> { // Categories
                        if (r.type != VarType.DISCRETE) return;
                        String s = aValue == null ? "" : aValue.toString().trim();
                        List<String> parsed = parseCats(s);
                        r.categories = parsed;
                        r.catsValid = (parsed.size() == r.catCount);

                        // Only commit if valid; otherwise just repaint (red)
                        if (r.catsValid) {
                            applyRowToModel(r);
                        }
                        fireTableRowsUpdated(rowIndex, rowIndex);
                    }
                }
            } catch (Exception ex) {
                // keep current values; show friendly message
                JOptionPane.showMessageDialog(null, ex.getMessage(), "Edit Error", JOptionPane.ERROR_MESSAGE);
            }
        }

        private void applyRowToModel(Row r) {
            // Push row state to adapter maps
            access.setType(r.node, r.type);
            if (r.type == VarType.DISCRETE) {
                access.setCatCount(r.node, r.catCount);
                access.setCategories(r.node, r.categories);
            } else {
                access.setCatCount(r.node, 0);
                access.setCategories(r.node, List.of());
            }
            // Rebuild & set into wrapper immediately
            access.commit();

            owner.firePropertyChange("modelChanged", true, false);
        }

        private static List<String> defaultCats(int n) {
            List<String> out = new ArrayList<>(n);
            for (int i = 1; i <= n; i++) out.add("cat" + i);
            return out;
        }

        private static int parsePositiveInt(Object v) {
            if (v == null) return 0;
            if (v instanceof Integer i) return i;
            return Integer.parseInt(v.toString().trim());
        }

        private static List<String> parseCats(String s) {
            if (s.isEmpty()) return List.of();
            String[] parts = s.split(",");
            List<String> list = new ArrayList<>();
            for (String p : parts) {
                String t = p.trim();
                if (!t.isEmpty()) list.add(t);
            }
            return list;
        }

        public List<Row> rows() { return rows; }
    }

    // ===== Adapter builder that rebuilds the PM and writes it back to the wrapper on each edit =====
    private static RebuildableAccess buildAccess(HybridCgPmWrapper wrapper) {
        Objects.requireNonNull(wrapper, "wrapper");
        final HybridCgPm current = wrapper.getHybridCgPm();
        final Graph graph = current.getGraph();
        final List<Node> nodeOrder = new ArrayList<>(graph.getNodes());

        // Live editable maps
        final Map<Node, VarType> typeMap = new LinkedHashMap<>();
        final Map<Node, Integer> catCountMap = new LinkedHashMap<>();
        final Map<Node, List<String>> catsMap = new LinkedHashMap<>();

        // Seed from current PM
        for (Node v : nodeOrder) {
            int idx = current.indexOf(v);
            boolean isDisc = current.isDiscrete(idx);
            List<String> cats = isDisc ? current.getCategories(idx) : List.of();
            typeMap.put(v, isDisc ? VarType.DISCRETE : VarType.CONTINUOUS);
            catsMap.put(v, new ArrayList<>(cats == null ? List.of() : cats));
            catCountMap.put(v, isDisc ? catsMap.get(v).size() : 0);
        }

        // Preserve cutpoints across commits when possible
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
            @Override public Graph getGraph() { return graph; }
            @Override public List<Node> getVariables() { return nodeOrder; }
            @Override public VarType getType(Node v) { return typeMap.getOrDefault(v, VarType.CONTINUOUS); }
            @Override public int getCatCount(Node v) { return Math.max(0, catCountMap.getOrDefault(v, 0)); }
            @Override public List<String> getCategories(Node v) { return new ArrayList<>(catsMap.getOrDefault(v, List.of())); }

            @Override public void setType(Node v, VarType t) {
                typeMap.put(v, t == null ? VarType.CONTINUOUS : t);
            }
            @Override public void setCatCount(Node v, int n) {
                catCountMap.put(v, Math.max(0, n));
            }
            @Override public void setCategories(Node v, List<String> labels) {
                catsMap.put(v, new ArrayList<>(labels == null ? List.of() : labels));
            }

            @Override public void commit() {
                // Build PM inputs
                Map<Node, Boolean> isDisc = new LinkedHashMap<>();
                Map<Node, List<String>> cats = new LinkedHashMap<>();
                for (Node v : nodeOrder) {
                    boolean d = typeMap.getOrDefault(v, VarType.CONTINUOUS) == VarType.DISCRETE;
                    isDisc.put(v, d);
                    cats.put(v, d ? new ArrayList<>(catsMap.getOrDefault(v, List.of())) : null);
                }

                HybridCgPm newPm = new HybridCgPm(graph, nodeOrder, isDisc, cats);

                // Re-apply saved cutpoints where compatible
                for (Map.Entry<Node, Map<Node, double[]>> e : savedCuts.entrySet()) {
                    Node child = e.getKey();
                    if (getType(child) != VarType.DISCRETE) continue;
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
                        try { newPm.setContParentCutpointsForDiscreteChild(child, cpMap); }
                        catch (IllegalStateException | IllegalArgumentException ignored) { }
                    }
                }

                // Write back into wrapper
                wrapper.setHybridCgPm(newPm);
            }
        };
    }
}