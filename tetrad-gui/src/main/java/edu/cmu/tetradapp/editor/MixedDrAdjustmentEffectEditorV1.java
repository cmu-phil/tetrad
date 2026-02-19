package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.estimate.v1.AdjustmentEffectEstimatorV1;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.model.MixedDrAdjustmentEffectEditorModelV1;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.text.DecimalFormat;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * v1: Swing editor for "Adjustment Effect".
 * v1: Manual selection of Z (adjustment set), with a helper to auto-fill using parents of X in the chosen graph.
 */
public final class MixedDrAdjustmentEffectEditorV1 extends JPanel {

    private final MixedDrAdjustmentEffectEditorModelV1 model;

    // v1: UI controls
    private final JComboBox<String> graphBox = new JComboBox<>();
    private final JComboBox<Node> xBox = new JComboBox<>();
    private final JComboBox<Node> yBox = new JComboBox<>();
    private final JList<Node> zList = new JList<>();
    private final DefaultListModel<Node> zListModel = new DefaultListModel<>();

    private final JButton useParentsButton = new JButton("Use Parents of X in Graph (v1)");
    private final JButton clearZButton = new JButton("Clear Z (v1)");
    private final JButton estimateButton = new JButton("Estimate (v1)");

    private final ResultsTableModelV1 tableModel = new ResultsTableModelV1();
    private final JTable table = new JTable(tableModel);

    // v1: formatting
    private static final DecimalFormat DF3 = new DecimalFormat("0.###");
    private static final DecimalFormat DF4 = new DecimalFormat("0.####");

    public MixedDrAdjustmentEffectEditorV1(MixedDrAdjustmentEffectEditorModelV1 model) {
        this.model = Objects.requireNonNull(model, "v1: model");
        buildUiV1();
        loadFromModelV1();
        wireActionsV1();
    }

    private void buildUiV1() {
        setLayout(new BorderLayout(8, 8));
        setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel top = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;

        // v1: Graph selector
        c.gridx = 0; c.gridy = row; c.weightx = 0;
        top.add(new JLabel("Graph (v1):"), c);
        c.gridx = 1; c.gridy = row; c.weightx = 1;
        top.add(graphBox, c);

        // v1: X/Y selectors
        row++;
        c.gridx = 0; c.gridy = row; c.weightx = 0;
        top.add(new JLabel("Treatment X (v1):"), c);
        c.gridx = 1; c.gridy = row; c.weightx = 1;
        top.add(xBox, c);

        row++;
        c.gridx = 0; c.gridy = row; c.weightx = 0;
        top.add(new JLabel("Outcome Y (v1):"), c);
        c.gridx = 1; c.gridy = row; c.weightx = 1;
        top.add(yBox, c);

        // v1: Z list + buttons
        JPanel zPanel = new JPanel(new BorderLayout(6, 6));
        zPanel.add(new JLabel("Adjustment set Z (multi-select) (v1):"), BorderLayout.NORTH);

        zList.setModel(zListModel);
        zList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        JScrollPane zScroll = new JScrollPane(zList);
        zScroll.setPreferredSize(new Dimension(260, 180));
        zPanel.add(zScroll, BorderLayout.CENTER);

        JPanel zButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        zButtons.add(useParentsButton);
        zButtons.add(clearZButton);
        zButtons.add(estimateButton);
        zPanel.add(zButtons, BorderLayout.SOUTH);

        JPanel left = new JPanel(new BorderLayout(8, 8));
        left.add(top, BorderLayout.NORTH);
        left.add(zPanel, BorderLayout.CENTER);

        // v1: results table
        JScrollPane tableScroll = new JScrollPane(table);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setFillsViewportHeight(true);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, tableScroll);
        split.setResizeWeight(0.35);
        add(split, BorderLayout.CENTER);
    }

    private void loadFromModelV1() {
        // v1: populate graph names
        graphBox.removeAllItems();
        for (String name : model.getGraphNames()) graphBox.addItem(name);
        if (graphBox.getItemCount() > 0) graphBox.setSelectedIndex(0);

        // v1: node lists based on dataset variables (not the graph, so estimation always matches data columns)
        DataSet ds = model.getDataSet();
        List<Node> vars = ds.getVariables();

        // v1: Treatment X must be binary DiscreteVariable
        List<Node> xCandidates = vars.stream()
                .filter(v -> v instanceof DiscreteVariable dv && dv.getNumCategories() == 2)
                .sorted(Comparator.comparing(Node::getName))
                .collect(Collectors.toList());

        // v1: Outcome Y must be continuous (v1 restriction)
        List<Node> yCandidates = vars.stream()
                .filter(v -> v instanceof ContinuousVariable)
                .sorted(Comparator.comparing(Node::getName))
                .collect(Collectors.toList());

        xBox.setModel(new DefaultComboBoxModel<>(xCandidates.toArray(new Node[0])));
        yBox.setModel(new DefaultComboBoxModel<>(yCandidates.toArray(new Node[0])));

        // v1: Z list can be anything except X/Y
        // We'll fill Z after X/Y selection changes.
        refreshZListV1();
        autoSizeColumnsV1();
    }

    private void wireActionsV1() {
        graphBox.addActionListener(e -> {
            // v1: graph changes only affect the parents heuristic; leave selections unchanged
        });

        xBox.addActionListener(e -> refreshZListV1());
        yBox.addActionListener(e -> refreshZListV1());

        clearZButton.addActionListener(e -> zList.clearSelection());

        useParentsButton.addActionListener(e -> {
            Node x = (Node) xBox.getSelectedItem();
            if (x == null) return;

            Graph g = getSelectedGraphV1();
            if (g == null) return;

            // v1: heuristic — "parents of X" = nodes with an arrow into X
            Set<String> parentNames = new HashSet<>();
            for (Edge edge : g.getEdges()) {
                Node a = edge.getNode1();
                Node b = edge.getNode2();
                Endpoint ea = edge.getEndpoint1();
                Endpoint eb = edge.getEndpoint2();

                if (a != null && b != null) {
                    // if b is X and endpoint at b is ARROW, then a -> X (or o->X etc)
                    if (b.getName().equals(x.getName()) && eb == Endpoint.ARROW) parentNames.add(a.getName());
                    // if a is X and endpoint at a is ARROW, then b -> X
                    if (a.getName().equals(x.getName()) && ea == Endpoint.ARROW) parentNames.add(b.getName());
                }
            }

            // v1: select those in the Z list
            ListModel<Node> lm = zList.getModel();
            List<Integer> toSelect = new ArrayList<>();
            for (int i = 0; i < lm.getSize(); i++) {
                if (parentNames.contains(lm.getElementAt(i).getName())) toSelect.add(i);
            }
            int[] idx = toSelect.stream().mapToInt(Integer::intValue).toArray();
            zList.setSelectedIndices(idx);
        });

        estimateButton.addActionListener(e -> runEstimateV1());
    }

    private void refreshZListV1() {
        DataSet ds = model.getDataSet();
        Node x = (Node) xBox.getSelectedItem();
        Node y = (Node) yBox.getSelectedItem();

        zListModel.clear();

        for (Node v : ds.getVariables().stream().sorted(Comparator.comparing(Node::getName)).toList()) {
            if (x != null && v.getName().equals(x.getName())) continue;
            if (y != null && v.getName().equals(y.getName())) continue;
            zListModel.addElement(v);
        }
    }

    private Graph getSelectedGraphV1() {
        int idx = graphBox.getSelectedIndex();
        if (idx < 0 || idx >= model.getGraphs().size()) return null;
        return model.getGraphs().get(idx);
    }

    private String getSelectedGraphNameV1() {
        int idx = graphBox.getSelectedIndex();
        if (idx < 0 || idx >= model.getGraphNames().size()) return "Graph";
        return model.getGraphNames().get(idx);
    }

    private void runEstimateV1() {
        Node x = (Node) xBox.getSelectedItem();
        Node y = (Node) yBox.getSelectedItem();
        if (x == null || y == null) return;

        // v1: collect Z selection
        List<Node> zSel = zList.getSelectedValuesList();
        Set<Node> zSet = new LinkedHashSet<>(zSel);

        // v1: build cfg from Parameters or defaults
        AdjustmentEffectEstimatorV1.ConfigV1 cfg = new AdjustmentEffectEstimatorV1.ConfigV1();
        Parameters p = model.getParameters();

        // v1: if you want, define parameter keys; otherwise keep defaults:
        // cfg.basisDegree = p.getInt("adjustEffect.basisDegree", cfg.basisDegree);
        // cfg.bootstrapB   = p.getInt("adjustEffect.bootstrapB", cfg.bootstrapB);
        // cfg.propensityClipEps = p.getDouble("adjustEffect.propClip", cfg.propensityClipEps);
        // cfg.includeTreatmentInteractions = p.getBoolean("adjustEffect.interactions", cfg.includeTreatmentInteractions);

        try {
            AdjustmentEffectEstimatorV1.EffectEstimateResultV1 res =
                    AdjustmentEffectEstimatorV1.estimateAteV1(model.getDataSet(), x, y, zSet, cfg);

            tableModel.addRow(new ResultRowV1(
                    getSelectedGraphNameV1(),
                    x.getName(),
                    y.getName(),
                    String.join(", ", res.adjustmentSet),
                    res.ateOr, res.ateDr,
                    res.seOrBoot, res.seDrBoot,
                    res.ciLoOr, res.ciHiOr,
                    res.ciLoDr, res.ciHiDr,
                    res.minProp, res.maxProp, res.fracClipped,
                    res.n
            ));
            autoSizeColumnsV1();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "v1: Estimation failed:\n" + ex.getMessage(),
                    "Adjustment Effect (v1)",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void autoSizeColumnsV1() {
        // v1: basic column widths; adjust as needed
        int[] widths = new int[]{
                120, 80, 80, 220, 90, 90, 90, 90, 110, 110, 110, 110, 80, 80, 90, 60
        };
        for (int i = 0; i < widths.length && i < table.getColumnModel().getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
    }

    // =========================
    // v1: Results table model
    // =========================

    private static final class ResultRowV1 {
        final String graph;
        final String x, y;
        final String z;
        final double ateOr, ateDr;
        final double seOr, seDr;
        final double ciLoOr, ciHiOr;
        final double ciLoDr, ciHiDr;
        final double minProp, maxProp, fracClipped;
        final int n;

        ResultRowV1(String graph, String x, String y, String z,
                    double ateOr, double ateDr,
                    double seOr, double seDr,
                    double ciLoOr, double ciHiOr,
                    double ciLoDr, double ciHiDr,
                    double minProp, double maxProp, double fracClipped,
                    int n) {
            this.graph = graph;
            this.x = x;
            this.y = y;
            this.z = z;
            this.ateOr = ateOr;
            this.ateDr = ateDr;
            this.seOr = seOr;
            this.seDr = seDr;
            this.ciLoOr = ciLoOr;
            this.ciHiOr = ciHiOr;
            this.ciLoDr = ciLoDr;
            this.ciHiDr = ciHiDr;
            this.minProp = minProp;
            this.maxProp = maxProp;
            this.fracClipped = fracClipped;
            this.n = n;
        }
    }

    private static final class ResultsTableModelV1 extends AbstractTableModel {

        private final List<ResultRowV1> rows = new ArrayList<>();

        private final String[] cols = new String[]{
                "Graph (v1)", "X (v1)", "Y (v1)", "Z (v1)",
                "ATE_OR (v1)", "ATE_DR (v1)",
                "SE_OR (boot) (v1)", "SE_DR (boot) (v1)",
                "CI_OR lo (v1)", "CI_OR hi (v1)",
                "CI_DR lo (v1)", "CI_DR hi (v1)",
                "min e (v1)", "max e (v1)", "clipped (v1)",
                "n (v1)"
        };

        void addRow(ResultRowV1 r) {
            rows.add(r);
            fireTableRowsInserted(rows.size() - 1, rows.size() - 1);
        }

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return cols.length; }
        @Override public String getColumnName(int column) { return cols[column]; }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            ResultRowV1 r = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> r.graph;
                case 1 -> r.x;
                case 2 -> r.y;
                case 3 -> r.z;
                case 4 -> fmt(r.ateOr);
                case 5 -> fmt(r.ateDr);
                case 6 -> fmt(r.seOr);
                case 7 -> fmt(r.seDr);
                case 8 -> fmt(r.ciLoOr);
                case 9 -> fmt(r.ciHiOr);
                case 10 -> fmt(r.ciLoDr);
                case 11 -> fmt(r.ciHiDr);
                case 12 -> fmt4(r.minProp);
                case 13 -> fmt4(r.maxProp);
                case 14 -> fmt4(r.fracClipped);
                case 15 -> r.n;
                default -> "";
            };
        }

        private static String fmt(double v) {
            if (!Double.isFinite(v)) return "";
            return DF3.format(v);
        }

        private static String fmt4(double v) {
            if (!Double.isFinite(v)) return "";
            return DF4.format(v);
        }
    }
}
