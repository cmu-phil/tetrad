package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.hybridcg.HybridCgModel.HybridCgIm;
import edu.cmu.tetrad.hybridcg.HybridCgModel.HybridCgPm;
import edu.cmu.tetradapp.model.HybridCgImWrapper;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.text.DecimalFormat;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Hybrid CG IM Editor
 *
 * Left: variable list (+ filter)
 * Right (card):
 *   - Discrete: full CPT table (all strata) + Normalize/Randomize actions
 *   - Continuous: full regression table (all strata) + ShareVariance/Randomize actions
 *
 * All numeric cells use 0.### formatting (renderer + editor).
 */
public final class HybridCgImEditor extends JPanel {

    private final HybridCgIm im;
    private final HybridCgPm pm;
    private final Node[] nodes;

    // LEFT
    private final DefaultListModel<Node> varListModel = new DefaultListModel<>();
    private final JList<Node> varList = new JList<>(varListModel);
    private final JTextField filterField = new JTextField();

    // RIGHT
    private final CardLayout cards = new CardLayout();
    private final JPanel right = new JPanel(cards);

    // Discrete card
    private final JPanel discCard = new JPanel(new BorderLayout(8,8));
    private final JLabel discInfo = new JLabel(
            "Rows are strata (combinations of discrete parent values). Columns are the child’s categories.");
    private JScrollPane discScroll; // created per-selection
    private final JButton discNormalizeAll = new JButton("Normalize All Rows");
    private final JButton discRandomizeAll = new JButton("Randomize Table");

    // Continuous card
    private final JPanel contCard = new JPanel(new BorderLayout(8,8));
    private final JLabel contInfo = new JLabel(
            "Regression parameters by stratum. Intercept / one column per continuous parent / Variance.");
    private JScrollPane contScroll; // created per-selection
    private final JButton contShareVar = new JButton("Share Variance Across Rows");
    private final JButton contRandomizeAll = new JButton("Randomize Table");

    // state
    private int currentY = -1;

    // number formatting
    private static final DecimalFormat DF3 = new DecimalFormat("0.###");

    public HybridCgImEditor(HybridCgImWrapper wrapper) { this(wrapper.getHybridCgIm()); }

    public HybridCgImEditor(HybridCgIm im) {
        this.im = Objects.requireNonNull(im, "im");
        this.pm = im.getPm();
        this.nodes = pm.getNodes();

        setLayout(new BorderLayout(10,10));
        add(buildLeft(), BorderLayout.WEST);
        add(buildRight(), BorderLayout.CENTER);

        loadVariableList(null);
        if (!varListModel.isEmpty()) varList.setSelectedIndex(0);
    }

    // ============================ LEFT ============================

    private JComponent buildLeft() {
        JPanel left = new JPanel(new BorderLayout(6,6));
        left.setBorder(new TitledBorder("Variables"));

        varList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        varList.setVisibleRowCount(18);
        varList.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                                    boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Node n) setText(n.getName());
                return this;
            }
        });
        varList.addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) onSelectChild(varList.getSelectedValue()); });

        filterField.setToolTipText("Filter by name (press Enter)");
        filterField.addActionListener(e -> loadVariableList(filterField.getText().trim()));

        left.add(filterField, BorderLayout.NORTH);
        left.add(new JScrollPane(varList), BorderLayout.CENTER);
        left.setPreferredSize(new Dimension(220, 480));
        return left;
    }

    private void loadVariableList(String filter) {
        varListModel.clear();
        for (Node n : nodes) {
            if (filter == null || filter.isEmpty()
                || n.getName().toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT))) {
                varListModel.addElement(n);
            }
        }
    }

    // ============================ RIGHT ============================

    private JComponent buildRight() {
        // Discrete card scaffold
        discInfo.setBorder(BorderFactory.createEmptyBorder(4,8,0,8));
        JPanel discBtns = new JPanel(new FlowLayout(FlowLayout.LEFT));
        discBtns.add(discNormalizeAll);
        discBtns.add(discRandomizeAll);
        discCard.add(discInfo, BorderLayout.NORTH);
        discCard.add(new JPanel(), BorderLayout.CENTER); // placeholder replaced per selection
        discCard.add(discBtns, BorderLayout.SOUTH);

        discNormalizeAll.addActionListener(e -> normalizeAllRows(currentY));
        discRandomizeAll.addActionListener(e -> { randomizeDiscreteTable(currentY); refreshActiveTable(); });

        // Continuous card scaffold
        contInfo.setBorder(BorderFactory.createEmptyBorder(4,8,0,8));
        JPanel contBtns = new JPanel(new FlowLayout(FlowLayout.LEFT));
        contBtns.add(contShareVar);
        contBtns.add(contRandomizeAll);
        contCard.add(contInfo, BorderLayout.NORTH);
        contCard.add(new JPanel(), BorderLayout.CENTER); // placeholder replaced per selection
        contCard.add(contBtns, BorderLayout.SOUTH);

        contShareVar.addActionListener(e -> shareVarianceAcrossRows(currentY));
        contRandomizeAll.addActionListener(e -> { randomizeContinuousTable(currentY); refreshActiveTable(); });

        right.setLayout(cards);
        right.add(new JPanel(), "blank");
        right.add(discCard, "disc");
        right.add(contCard, "cont");
        return right;
    }

    // =========================== Selection ===========================

    private void onSelectChild(Node child) {
        if (child == null) { cards.show(right, "blank"); return; }

        currentY = pm.indexOf(child);

        if (pm.isDiscrete(currentY)) {
            // Build Bayes-style CPT table for this discrete child
            HybridCgCptEditingTable table = new HybridCgCptEditingTable(child, im);
            installDoubleFormatting(table); // numeric columns inside will use Double.class
            discScroll = new JScrollPane(table);
            discCard.remove(1);
            discCard.add(discScroll, BorderLayout.CENTER);

            // Update instructions
            int[] dps = pm.getDiscreteParents(currentY);
            if (dps.length == 0) {
                discInfo.setText("No discrete parents — single row with probabilities for the child’s categories.");
            } else {
                List<String> names = Arrays.stream(dps).mapToObj(i -> pm.getNodes()[i].getName()).collect(Collectors.toList());
                discInfo.setText("Rows are strata of " + names + "; columns are categories of " + child.getName() + ".");
            }

            cards.show(right, "disc");
        } else {
            // Full regression table
            HybridCgRegEditingTable table = new HybridCgRegEditingTable(im, pm, currentY);
            installDoubleFormatting(table); // ensure 0.### in-place
            contScroll = new JScrollPane(table);
            contCard.remove(1);
            contCard.add(contScroll, BorderLayout.CENTER);

            // Update instructions
            int[] dps = pm.getDiscreteParents(currentY);
            if (dps.length == 0) {
                contInfo.setText("No discrete parents — single stratum (one row). Columns: Intercept, parents, Variance.");
            } else {
                List<String> names = Arrays.stream(dps).mapToObj(i -> pm.getNodes()[i].getName()).collect(Collectors.toList());
                contInfo.setText("Rows are strata of " + names + ". Columns: Intercept, parents, Variance.");
            }

            cards.show(right, "cont");
        }
        revalidate(); repaint();
    }

//    private void refreshActiveTable() {
//        // Simple repaint of current card’s table after bulk operations
//        if (currentY < 0) return;
//        if (pm.isDiscrete(currentY)) {
//            if (discScroll != null && discScroll.getViewport().getView() instanceof JComponent jc) {
//                jc.repaint();
//            }
//        } else {
//            if (contScroll != null && contScroll.getViewport().getView() instanceof JComponent jc) {
//                jc.repaint();
//            }
//        }
//    }

    /** After bulk ops, refresh the active table more robustly (model event if possible). */
    private void refreshActiveTable() {
        if (currentY < 0) return;

        JScrollPane sc = pm.isDiscrete(currentY) ? discScroll : contScroll;
        if (sc == null) return;

        Component view = sc.getViewport().getView();

        if (view instanceof JTable jt) {
            if (jt.getModel() instanceof AbstractTableModel atm) {
                atm.fireTableDataChanged();  // tell the model to refresh
                return;
            }
            jt.revalidate();
            jt.repaint();
        } else if (view instanceof JComponent jc) {
            jc.revalidate();
            jc.repaint();
        }
    }

    // =========================== Actions ===========================

    private void normalizeAllRows(int y) {
        if (y < 0 || !pm.isDiscrete(y)) return;
        int rows = pm.getNumRows(y);
        for (int r = 0; r < rows; r++) im.normalizeRow(y, r);
        refreshActiveTable();
    }

    private void shareVarianceAcrossRows(int y) {
        if (y < 0 || pm.isDiscrete(y)) return;
        int rows = pm.getNumRows(y);
        double s = 0.0; int cnt = 0;
        for (int r = 0; r < rows; r++) { s += Math.max(1e-12, im.getVariance(y, r)); cnt++; }
        double pooled = (cnt > 0) ? (s / cnt) : 1.0;
        for (int r = 0; r < rows; r++) im.setVariance(y, r, pooled);
        refreshActiveTable();
    }

    private void randomizeDiscreteTable(int y) {
        if (y < 0 || !pm.isDiscrete(y)) return;
        Random rng = new Random();
        int rows = pm.getNumRows(y);
        int K = pm.getCardinality(y);
        for (int r = 0; r < rows; r++) {
            double[] e = new double[K];
            double sum = 0.0;
            for (int k = 0; k < K; k++) { e[k] = -Math.log(1.0 - rng.nextDouble()); sum += e[k]; }
            for (int k = 0; k < K; k++) im.setProbability(y, r, k, e[k] / sum);
        }
    }

    private void randomizeContinuousTable(int y) {
        if (y < 0 || pm.isDiscrete(y)) return;
        Random rng = new Random();
        int rows = pm.getNumRows(y);
        int m = pm.getContinuousParents(y).length;
        for (int r = 0; r < rows; r++) {
            im.setIntercept(y, r, rng.nextGaussian() * 0.25);
            for (int j = 0; j < m; j++) im.setCoefficient(y, r, j, rng.nextGaussian() * 0.15);
            im.setVariance(y, r, 0.25 + 0.75 * rng.nextDouble());
        }
    }

    // ======================= Formatting helpers =======================

    /** Install 0.### renderer/editor on a JTable (or a JTable wrapped in our helper components). */
//    private void installDoubleFormatting(JTable table) {
//        if (table == null) return;
//        table.setDefaultRenderer(Double.class, new DefaultTableCellRenderer() {
//            @Override protected void setValue(Object value) {
//                if (value instanceof Number n) setText(DF3.format(n.doubleValue()));
//                else super.setValue(value);
//            }
//        });
//        table.setDefaultEditor(Double.class, new DoubleCellEditor("0.###"));
//        table.setRowHeight(22);
//    }

    /** Install 0.### renderer/editor on a JTable (works for Double.class and Number.class). */
    private void installDoubleFormatting(JTable table) {
        if (table == null) return;

        DefaultTableCellRenderer numRenderer = new DefaultTableCellRenderer() {
            @Override protected void setValue(Object value) {
                if (value instanceof Number n) setText(DF3.format(n.doubleValue()));
                else super.setValue(value);
            }
        };

        // Apply to both Double and Number to be safe
        table.setDefaultRenderer(Double.class, numRenderer);
        table.setDefaultRenderer(Number.class, numRenderer);

        // Reuse our formatted editor for both keys
        TableCellEditor numEditor = new DoubleCellEditor("0.###");
        table.setDefaultEditor(Double.class, numEditor);
        table.setDefaultEditor(Number.class, numEditor);

        table.setRowHeight(22);
    }

    // Simple re-usable editor with formatted seed text
    public static final class DoubleCellEditor extends DefaultCellEditor {
        private final DecimalFormat fmt;
        public DoubleCellEditor(String pattern) {
            super(new JTextField());
            this.fmt = new DecimalFormat(pattern);
        }
        @Override public Object getCellEditorValue() {
            String s = ((JTextField)getComponent()).getText().trim();
            try { return Double.valueOf(s); } catch (Exception ex) { return Double.NaN; }
        }
        @Override public Component getTableCellEditorComponent(JTable t, Object v, boolean sel, int r, int c) {
            ((JTextField)getComponent()).setText((v instanceof Number) ? fmt.format(((Number) v).doubleValue()) : "");
            return getComponent();
        }
    }

    // (kept for completeness if you ever need a tiny model table inline)
    private static double parseDouble(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(v).trim()); }
        catch (Exception e) { return Double.NaN; }
    }

    // ======================= (Optional) tiny models =======================
    // You no longer need the old single-row table models; the dedicated
    // HybridCgCptEditingTable and HybridCgRegEditingTable show the full tables.
}