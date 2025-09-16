package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.hybridcg.HybridCgModel.HybridCgPm;
import edu.cmu.tetradapp.model.HybridCgPmWrapper;
import edu.cmu.tetrad.util.Parameters;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.*;
import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Hybrid CG PM editor:
 * 1) Variables panel (edit type & categories, apply → rebuild PM)
 * 2) Cutpoint editor (for discrete children with continuous parents)
 */
public final class HybridCgPmEditor extends JPanel {

    private final HybridCgPmWrapper pmWrapper;
    private final Parameters params;

    // --------------- top: variable/type editing ----------------
    private final JTable varTable = new JTable();
    private VarTableModel varModel;

    private final JTextField nameField = new JTextField();
    private final JButton applyTypesBtn = new JButton("Apply Types");

    // --------------- cutpoints controls ------------------------
    private final JComboBox<String> methodCombo =
            new JComboBox<>(new String[]{"equal_frequency", "equal_interval", "none"});
    private final JSpinner binsSpinner = new JSpinner(new SpinnerNumberModel(3, 2, 50, 1));

    private final JTable cutTable = new JTable();
    private CutpointsTableModel cutModel;

    private final JComboBox<Node> childCombo = new JComboBox<>();
    private final JComboBox<Node> parentCombo = new JComboBox<>();

    public HybridCgPmEditor(HybridCgPmWrapper pmWrapper) {
        this(pmWrapper, null, new Parameters());
    }
    public HybridCgPmEditor(HybridCgPmWrapper pmWrapper, Parameters params) {
        this(pmWrapper, null, params);
    }
    public HybridCgPmEditor(HybridCgPmWrapper pmWrapper, DataSet dataOrNull, Parameters params) {
        this.pmWrapper = Objects.requireNonNull(pmWrapper, "pmWrapper");
        this.params = (params == null) ? new Parameters() : params;

        setLayout(new BorderLayout(10, 10));
        add(buildVariablesPanel(), BorderLayout.NORTH);
        add(buildCutpointPanel(dataOrNull), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);

        // init from wrapper
        nameField.setText(pmWrapper.getName());
        methodCombo.setSelectedItem(this.params.getString("hybridcg.cutMethod", "freq")
                .equalsIgnoreCase("intervals") ? "equal_interval" : "equal_frequency");
        binsSpinner.setValue(Math.max(2, this.params.getInt("hybridcg.cutBins", 3)));

        refreshChildParentSelectors();
        rebuildCutTable();
    }

    // ===================== UI: Variables ======================

    private JComponent buildVariablesPanel() {
        JPanel outer = new JPanel(new BorderLayout());
        outer.setBorder(new TitledBorder("Hybrid CG PM"));

        // name + apply-name
        JPanel top = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4,4,4,4);
        c.gridx = 0; c.gridy = 0; c.anchor = GridBagConstraints.WEST;
//        top.add(new JLabel("Name:"), c);
        c.gridx = 1; c.weightx = 1.0; c.fill = GridBagConstraints.HORIZONTAL;
//        top.add(nameField, c);
//        JButton applyName = new JButton("Apply Name");
//        applyName.addActionListener(ev -> pmWrapper.setName(nameField.getText()));
        c.gridx = 2; c.weightx = 0; c.fill = GridBagConstraints.NONE;
//        top.add(applyName, c);
        outer.add(top, BorderLayout.NORTH);

        // variable table
        varModel = new VarTableModel(pmWrapper.getHybridCgPm());
        varTable.setModel(varModel);
        varTable.setRowHeight(22);
        varTable.setPreferredScrollableViewportSize(new Dimension(720, 200));

        // editors/renderers
        // Type column: combo box
        JComboBox<String> typeCombo = new JComboBox<>(new String[]{"Continuous", "Discrete"});
        varTable.getColumnModel().getColumn(1).setCellEditor(new DefaultCellEditor(typeCombo));

        // #Categories column: spinner editor (2..999)
        varTable.getColumnModel().getColumn(2)
                .setCellEditor(new SpinnerCellEditor(new SpinnerNumberModel(3, 2, 999, 1)));

        // Categories column: text editor; validate count; red if mismatch
        varTable.getColumnModel().getColumn(3).setCellRenderer(new CategoriesRenderer(varModel));
        varTable.getColumnModel().getColumn(3).setCellEditor(new CategoriesEditor());

        // apply types button
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        applyTypesBtn.addActionListener(ev -> applyTypesAndRebuildPm());
        btns.add(applyTypesBtn);

        JScrollPane sc = new JScrollPane(varTable);
        outer.add(sc, BorderLayout.CENTER);
        outer.add(btns, BorderLayout.SOUTH);

        SwingUtilities.invokeLater(() -> adjustVarTableColumnWidths(varTable));
        return outer;
    }

    private void applyTypesAndRebuildPm() {
        // enforce categories-count validity
        List<String> problems = varModel.validateAll();
        if (!problems.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Please fix category lists:\n- " + String.join("\n- ", problems),
                    "Invalid categories", JOptionPane.ERROR_MESSAGE);
            return;
        }

        Map<Node, Boolean> isDisc = new LinkedHashMap<>();
        Map<Node, List<String>> cats = new LinkedHashMap<>();
        for (VarRow r : varModel.rows) {
            isDisc.put(r.node, r.discrete);
            cats.put(r.node, r.discrete ? new ArrayList<>(r.categories) : null);
        }

        HybridCgPm oldPm = pmWrapper.getHybridCgPm();
        Graph g = oldPm.getGraph();
        List<Node> order = Arrays.asList(oldPm.getNodes());

        HybridCgPm newPm = new HybridCgPm(g, order, isDisc, cats);
        preserveCompatibleCutpoints(oldPm, newPm);
        pmWrapper.setHybridCgPm(newPm);

        refreshChildParentSelectors();
        rebuildCutTable();
    }

    private void preserveCompatibleCutpoints(HybridCgPm oldPm, HybridCgPm newPm) {
        Node[] nodes = oldPm.getNodes();
        for (Node child : nodes) {
            int yo = oldPm.indexOf(child), yn = newPm.indexOf(child);
            if (yo < 0 || yn < 0) continue;
            if (!oldPm.isDiscrete(yo) || !newPm.isDiscrete(yn)) continue;
            Set<String> oldC = Arrays.stream(oldPm.getContinuousParents(yo))
                    .mapToObj(i -> oldPm.getNodes()[i].getName()).collect(Collectors.toSet());
            Set<String> newC = Arrays.stream(newPm.getContinuousParents(yn))
                    .mapToObj(i -> newPm.getNodes()[i].getName()).collect(Collectors.toSet());
            if (!oldC.equals(newC)) continue;

            Map<Node,double[]> map = new LinkedHashMap<>();
            oldPm.getContParentCutpointsForDiscreteChild(yo).ifPresent(cuts -> {
                int[] cps = oldPm.getContinuousParents(yo);
                for (int t = 0; t < cps.length; t++)
                    map.put(oldPm.getNodes()[cps[t]], cuts[t].clone());
            });
            if (!map.isEmpty()) {
                try { newPm.setContParentCutpointsForDiscreteChild(child, map); } catch (Exception ignore) {}
            }
        }
    }

    // ===================== UI: Cutpoints ======================

    private JComponent buildCutpointPanel(DataSet dataOrNull) {
        JPanel outer = new JPanel(new BorderLayout(8,8));
        outer.setBorder(new TitledBorder("Cutpoint Editor"));

        JPanel top = new JPanel(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4,4,4,4);
        g.anchor = GridBagConstraints.WEST;

        g.gridx = 0; g.gridy = 0;
        top.add(new JLabel("Cut method:"), g);
        g.gridx = 1; top.add(methodCombo, g);

        g.gridx = 2; top.add(new JLabel("Bins:"), g);
        g.gridx = 3; top.add(binsSpinner, g);

        g.gridx = 0; g.gridy = 1; top.add(new JLabel("Discrete child:"), g);
        g.gridx = 1; top.add(childCombo, g);

        g.gridx = 2; top.add(new JLabel("Continuous parent:"), g);
        g.gridx = 3; top.add(parentCombo, g);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton applyData = new JButton("Apply from Data…");
        JButton seedDefaults = new JButton("Seed Defaults");
        JButton clear = new JButton("Clear");
        JButton addRow = new JButton("+ row");
        JButton delRow = new JButton("− row");

        applyData.addActionListener(ev -> {
            String method = (String) methodCombo.getSelectedItem();
            int bins = (int) binsSpinner.getValue();
            if (dataOrNull == null) {
                JOptionPane.showMessageDialog(this, "No DataSet available in this editor context.",
                        "Info", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            HybridCgPmWrapper.CutMethod m = "equal_interval".equals(method)
                    ? HybridCgPmWrapper.CutMethod.EQUAL_INTERVALS
                    : HybridCgPmWrapper.CutMethod.EQUAL_FREQUENCY;
            pmWrapper.applyCutpointsFromData(dataOrNull, bins, m);
            refreshChildParentSelectors();
            rebuildCutTable();
        });

        seedDefaults.addActionListener(ev -> {
            new DefaultSeeding().run(pmWrapper.getHybridCgPm(), (int) binsSpinner.getValue());
            rebuildCutTable();
        });

        clear.addActionListener(ev -> {
            HybridCgPm pm = pmWrapper.getHybridCgPm();
            Node[] nodes = pm.getNodes();
            for (int y = 0; y < nodes.length; y++) {
                if (!pm.isDiscrete(y)) continue;
                int[] cps = pm.getContinuousParents(y);
                if (cps.length == 0) continue;
                Map<Node,double[]> empty = new LinkedHashMap<>();
                for (int t = 0; t < cps.length; t++) empty.put(nodes[cps[t]], new double[0]);
                pm.setContParentCutpointsForDiscreteChild(nodes[y], empty);
            }
            rebuildCutTable();
        });

        addRow.addActionListener(ev -> cutModel.addRow());
        delRow.addActionListener(ev -> cutModel.removeSelectedRow(cutTable.getSelectedRow()));

        if (dataOrNull != null) {
            btns.add(applyData);
        }

        btns.add(seedDefaults); btns.add(clear); btns.add(addRow); btns.add(delRow);

        childCombo.addActionListener(ev -> rebuildCutTable());
        parentCombo.addActionListener(ev -> rebuildCutTable());

        outer.add(top, BorderLayout.NORTH);

        cutModel = new CutpointsTableModel(pmWrapper.getHybridCgPm(), null, null);
        cutTable.setModel(cutModel);
        JScrollPane scroll = new JScrollPane(cutTable);
        scroll.setPreferredSize(new Dimension(520, 120));

        outer.add(btns, BorderLayout.CENTER);
        outer.add(scroll, BorderLayout.SOUTH);
        return outer;
    }

    private JComponent buildFooter() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton done = new JButton("Done");
        done.addActionListener(e -> SwingUtilities.getWindowAncestor(this).dispose());
        p.add(done);
        return p;
    }

    // ================= Helpers: selectors & tables =================

    private void refreshChildParentSelectors() {
        childCombo.removeAllItems();
        HybridCgPm pm = pmWrapper.getHybridCgPm();
        for (Node n : pm.getNodes()) {
            int i = pm.indexOf(n);
            if (pm.isDiscrete(i) && pm.getContinuousParents(i).length > 0) childCombo.addItem(n);
        }
        if (childCombo.getItemCount() > 0) childCombo.setSelectedIndex(0);
        refreshParentSelector();
    }

    private void refreshParentSelector() {
        parentCombo.removeAllItems();
        HybridCgPm pm = pmWrapper.getHybridCgPm();
        Node child = (Node) childCombo.getSelectedItem();
        if (child == null) return;
        int y = pm.indexOf(child);
        for (int p : pm.getContinuousParents(y)) parentCombo.addItem(pm.getNodes()[p]);
        if (parentCombo.getItemCount() > 0) parentCombo.setSelectedIndex(0);
    }

    private void rebuildCutTable() {
        HybridCgPm pm = pmWrapper.getHybridCgPm();
        Node child = (Node) childCombo.getSelectedItem();
        Node parent = (Node) parentCombo.getSelectedItem();
        cutModel = new CutpointsTableModel(pm, child, parent);
        cutTable.setModel(cutModel);

        // ----- Renderer: formatted display when NOT editing -----
        cutTable.setDefaultRenderer(Double.class, new DefaultTableCellRenderer() {
            private final java.text.DecimalFormat fmt = new java.text.DecimalFormat("0.###");
            @Override
            protected void setValue(Object value) {
                if (value instanceof Number) {
                    setText(fmt.format(((Number) value).doubleValue()));
                } else {
                    super.setValue(value);
                }
            }
        });

        // ----- Editor: formatted display WHEN editing -----
        final java.text.DecimalFormat fmt = new java.text.DecimalFormat("0.###");
        final JFormattedTextField ftf = new JFormattedTextField(fmt);
        ftf.setBorder(null);
        ftf.setFocusLostBehavior(JFormattedTextField.COMMIT_OR_REVERT);

        TableCellEditor editor = new DefaultCellEditor(ftf) {
            @Override
            public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
                // Show formatted text immediately
                if (value instanceof Number) {
                    ftf.setValue(((Number) value).doubleValue());
                    ftf.setText(fmt.format(((Number) value).doubleValue()));
                } else if (value != null) {
                    ftf.setText(value.toString());
                } else {
                    ftf.setText("");
                }
                SwingUtilities.invokeLater(ftf::selectAll);
                return ftf;
            }
            @Override
            public Object getCellEditorValue() {
                Object v = ftf.getValue();
                if (v instanceof Number) return ((Number) v).doubleValue();
                try {
                    return fmt.parse(ftf.getText()).doubleValue();
                } catch (Exception e) {
                    return Double.NaN; // or keep previous value if you prefer
                }
            }
        };

        cutTable.setDefaultEditor(Double.class, editor);
    }

    // ===================== Models & editors ======================

    private static final class VarRow {
        final Node node;
        boolean discrete;
        int catCount;                 // shown in spinner
        List<String> categories;      // labels
        boolean countMismatch;        // render red when true

        VarRow(Node node, boolean discrete, List<String> cats) {
            this.node = node;
            this.discrete = discrete;
            if (discrete) {
                this.categories = new ArrayList<>(cats == null ? List.of("c1","c2") : cats);
                this.catCount = Math.max(2, this.categories.size());
            } else {
                this.categories = null;
                this.catCount = 0;
            }
            this.countMismatch = false;
        }

        void enforceCount() {
            if (!discrete) return;
            if (categories == null) categories = new ArrayList<>();
            if (categories.size() < catCount) {
                categories.clear();
                for (int i = 0; i < catCount; i++) categories.add("c" + (i + 1));
            } else if (categories.size() > catCount) {
                categories = new ArrayList<>(categories.subList(0, catCount));
            }
            countMismatch = (categories.size() != catCount);
        }
    }

    private final class VarTableModel extends AbstractTableModel {
        final java.util.List<VarRow> rows;
        final String[] cols = {"Variable", "Type", "#Categories", "Categories"};

        VarTableModel(HybridCgPm pm) {
            rows = new ArrayList<>();
            Node[] order = pm.getNodes();
            for (int i = 0; i < order.length; i++) {
                boolean disc = pm.isDiscrete(i);
                List<String> cats = disc ? pm.getCategories(i) : null;
                rows.add(new VarRow(order[i], disc, cats));
            }
        }

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return cols.length; }
        @Override public String getColumnName(int c) { return cols[c]; }
        @Override public Class<?> getColumnClass(int c) {
            return switch (c) {
                case 0,1,3 -> String.class;
                case 2 -> Integer.class;
                default -> Object.class;
            };
        }
        @Override public boolean isCellEditable(int r, int c) {
            if (c == 0) return false;
            VarRow row = rows.get(r);
            if (c == 2 || c == 3) return row.discrete; // enable only for discrete
            return true; // Type
        }

        @Override public Object getValueAt(int r, int c) {
            VarRow row = rows.get(r);
            return switch (c) {
                case 0 -> row.node.getName();
                case 1 -> row.discrete ? "Discrete" : "Continuous";
                case 2 -> row.discrete ? row.catCount : 0;
                case 3 -> row.discrete ? String.join(", ", row.categories) : "";
                default -> null;
            };
        }

        @Override public void setValueAt(Object v, int r, int c) {
            VarRow row = rows.get(r);
            if (c == 1) {
                String s = String.valueOf(v);
                boolean toDisc = s.equalsIgnoreCase("Discrete");
                if (toDisc != row.discrete) {
                    row.discrete = toDisc;
                    if (toDisc) {
                        if (row.categories == null) {
                            row.categories = new ArrayList<>(List.of("c1", "c2", "c3"));
                        }
                        row.catCount = Math.max(2, row.categories.size());
                    }  else {
                        row.categories = null;
                        row.catCount = 0;
                        row.countMismatch = false;
                    }
                    fireTableRowsUpdated(r, r);
                    SwingUtilities.invokeLater(() -> adjustVarTableColumnWidths(varTable));
                }
            } else if (c == 2 && row.discrete) {
                int k = Math.max(2, ((Number) v).intValue());
                row.catCount = k;
                row.enforceCount();
                fireTableRowsUpdated(r, r);
            } else if (c == 3 && row.discrete) {
                String s = String.valueOf(v).trim();
                List<String> labels = Arrays.stream(s.split(","))
                        .map(String::trim).filter(t -> !t.isEmpty()).collect(Collectors.toList());
                if (labels.isEmpty()) labels = new ArrayList<>();
                row.categories = labels;
                row.countMismatch = (labels.size() != row.catCount);
                fireTableCellUpdated(r, c);
            }
        }

        List<String> validateAll() {
            List<String> problems = new ArrayList<>();
            for (VarRow r : rows) {
                if (r.discrete) {
                    if (r.categories == null || r.categories.size() != r.catCount) {
                        problems.add(r.node.getName() + ": needs " + r.catCount + " category label(s).");
                    }
                }
            }
            return problems;
        }
    }

    // Renderer: paint categories cell red when mismatch
    private static final class CategoriesRenderer extends JLabel implements TableCellRenderer {
        private final VarTableModel model;
        CategoriesRenderer(VarTableModel model) {
            this.model = model;
            setOpaque(true);
        }
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int column) {
            setText(String.valueOf(value));
            VarRow r = model.rows.get(row);
            if (isSelected) {
                setBackground(table.getSelectionBackground());
                setForeground(r.countMismatch ? Color.PINK.darker() : table.getSelectionForeground());
            } else {
                setBackground(table.getBackground());
                setForeground(r.countMismatch ? Color.RED.darker() : table.getForeground());
            }
            return this;
        }
    }

    // Text editor for categories column
    private static final class CategoriesEditor extends DefaultCellEditor {
        CategoriesEditor() { super(new JTextField()); }
    }

    // Spinner editor helper
    private static final class SpinnerCellEditor extends AbstractCellEditor implements TableCellEditor {
        private final JSpinner spinner;
        SpinnerCellEditor(SpinnerNumberModel model) { this.spinner = new JSpinner(model); }
        @Override public Object getCellEditorValue() { return spinner.getValue(); }
        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            spinner.setValue(value instanceof Number ? value : 3);
            return spinner;
        }
    }

    // Column width policy: fit Var/Type/#Cats minimally; Categories elastic
    private void adjustVarTableColumnWidths(JTable table) {
        FontMetrics fm = table.getFontMetrics(table.getFont());
        int pad = 24;

        // col 0: variable names
        int maxVar = 0;
        for (int r = 0; r < table.getRowCount(); r++)
            maxVar = Math.max(maxVar, fm.stringWidth(String.valueOf(table.getValueAt(r,0))));
        int varW = Math.min(Math.max(80, maxVar + pad), 240);

        // col 1: "Continuous" or "Discrete"
        int typeW = fm.stringWidth("Continuous") + pad;

        // col 2: 3 digits
        int numW = fm.stringWidth("999") + pad;

        TableColumn c0 = table.getColumnModel().getColumn(0);
        TableColumn c1 = table.getColumnModel().getColumn(1);
        TableColumn c2 = table.getColumnModel().getColumn(2);
        TableColumn c3 = table.getColumnModel().getColumn(3);

        c0.setMinWidth(varW); c0.setPreferredWidth(varW); c0.setMaxWidth(varW + 6);
        c1.setMinWidth(typeW); c1.setPreferredWidth(typeW); c1.setMaxWidth(typeW + 6);
        c2.setMinWidth(numW);  c2.setPreferredWidth(numW);  c2.setMaxWidth(numW + 6);
        // c3 elastic – leave preferred, allow to stretch
    }

    // ---- Cutpoint table model (unchanged except add/remove rows helpers) ----
    private static final class CutpointsTableModel extends AbstractTableModel {
        private final HybridCgPm pm;
        private final Node child;
        private final Node parent;
        private double[] edges;

        CutpointsTableModel(HybridCgPm pm, Node child, Node parent) {
            this.pm = pm; this.child = child; this.parent = parent;
            loadEdges();
        }
        private void loadEdges() {
            edges = new double[0];
            if (child == null || parent == null) return;
            int y = pm.indexOf(child);
            var opt = pm.getContParentCutpointsForDiscreteChild(y);
            if (opt.isEmpty()) return;
            int[] cps = pm.getContinuousParents(y);
            int idx = -1;
            for (int t = 0; t < cps.length; t++)
                if (pm.getNodes()[cps[t]].equals(parent)) { idx = t; break; }
            if (idx >= 0) edges = opt.get()[idx].clone();
        }

        @Override public int getRowCount() { return edges.length; }
        @Override public int getColumnCount() { return 1; }
        @Override public String getColumnName(int c) { return "Cutpoint"; }
        @Override public Class<?> getColumnClass(int c) { return Double.class; }
        @Override public boolean isCellEditable(int r, int c) { return true; }
        @Override public Object getValueAt(int r, int c) { return edges[r]; }

        @Override public void setValueAt(Object aValue, int r, int c) {
            try {
                double v = ((Number) aValue).doubleValue();
                edges[r] = v;
                fireTableCellUpdated(r, c);
                persistBack();
            } catch (Exception ignore) {}
        }

        void addRow() {
            edges = Arrays.copyOf(edges, edges.length + 1);
            edges[edges.length - 1] = (edges.length == 1) ? 0.0 : Math.nextUp(edges[edges.length - 2]);
            fireTableDataChanged();
            persistBack();
        }

        void removeSelectedRow(int r) {
            if (r < 0 || r >= edges.length) return;
            double[] next = new double[edges.length - 1];
            int k = 0;
            for (int i = 0; i < edges.length; i++) if (i != r) next[k++] = edges[i];
            edges = next;
            fireTableDataChanged();
            persistBack();
        }

        private void persistBack() {
            if (child == null || parent == null) return;
            int y = pm.indexOf(child);
            int[] cps = pm.getContinuousParents(y);
            double[][] all = pm.getContParentCutpointsForDiscreteChild(y).orElse(new double[cps.length][]);
            int idx = -1;
            for (int t = 0; t < cps.length; t++)
                if (pm.getNodes()[cps[t]].equals(parent)) { idx = t; break; }
            if (idx >= 0) {
                all[idx] = edges.clone();
                Map<Node,double[]> map = new LinkedHashMap<>();
                for (int t = 0; t < cps.length; t++)
                    map.put(pm.getNodes()[cps[t]], all[t] == null ? new double[0] : all[t]);
                pm.setContParentCutpointsForDiscreteChild(child, map);
            }
        }
    }

    // Small helper: seed simple defaults
    private static final class DefaultSeeding {
        void run(HybridCgPm pm, int bins) {
            Node[] nodes = pm.getNodes();
            for (int y = 0; y < nodes.length; y++) {
                if (!pm.isDiscrete(y)) continue;
                int[] cps = pm.getContinuousParents(y);
                if (cps.length == 0) continue;
                Map<Node,double[]> m = new LinkedHashMap<>();
                if (bins <= 2) {
                    for (int t = 0; t < cps.length; t++) m.put(nodes[cps[t]], new double[]{0.0});
                } else {
                    double lo = -0.5, hi = 0.5, step = (hi - lo) / bins;
                    double[] edges = new double[bins - 1];
                    for (int i = 0; i < edges.length; i++) edges[i] = lo + (i + 1) * step;
                    for (int t = 0; t < cps.length; t++) m.put(nodes[cps[t]], edges.clone());
                }
                try { pm.setContParentCutpointsForDiscreteChild(nodes[y], m); } catch (Exception ignore) {}
            }
        }
    }
}