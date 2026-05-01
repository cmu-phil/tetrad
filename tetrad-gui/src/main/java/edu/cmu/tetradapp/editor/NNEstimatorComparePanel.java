package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.data.Histogram;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.sem.CVReport;
import edu.cmu.tetrad.sem.EdgeStrengthResult;
import edu.cmu.tetrad.sem.NodeCVSummary;
import edu.cmu.tetrad.sem.PartialEdgeStrengthResult;
import edu.cmu.tetrad.util.NaturalSort;
import edu.cmu.tetrad.util.TMath;
import edu.cmu.tetradapp.model.NNEstimatorModel;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Side-by-side visual comparison panel for {@link NNEstimatorModel}.
 *
 * <p>The panel has three tabs:
 * <ol>
 *   <li><b>Cross-Validation</b> — k-fold OOS metrics per node plus whole-graph
 *       MMD². Results are restored from the model on relaunch.</li>
 *   <li><b>Edge Strength</b> — select a child node and compute the marginal
 *       and partial strength of each of its parent edges. Results appear
 *       progressively as each parent is computed.</li>
 *   <li><b>Observed vs. Resimulated</b> — side-by-side plot matrix.</li>
 * </ol>
 *
 * <p>All long-running operations run on background threads via
 * {@link SwingWorker} so the UI remains responsive.
 */
public final class NNEstimatorComparePanel extends JPanel {

    // ── model ─────────────────────────────────────────────────────────────────

    private final NNEstimatorModel model;
    private final DataSet observed;
    private final Graph dag;

    // ── tab 1: cross-validation ───────────────────────────────────────────────

    private final JSpinner kSpinner;
    private final JButton runCvButton = new JButton("Run Cross-Validation");
    private final JLabel cvSummaryLabel = new JLabel(" ");
    private final CVTableModel cvTableModel = new CVTableModel();
    private final JTable cvTable = new JTable(cvTableModel);

    // ── tab 2: edge strength ──────────────────────────────────────────────────

    private final JComboBox<String> childCombo = new JComboBox<>();
    private final JSpinner edgeSimNSpinner =
            new JSpinner(new SpinnerNumberModel(5000, 100, 1_000_000, 500));
    private final JButton computeEdgeButton = new JButton("Compute Parent Strengths");
    private final JLabel edgeProgressLabel = new JLabel(" ");
    private final JLabel edgeResultLabel   = new JLabel(" ");
    private final EdgeStrengthTableModel edgeTableModel = new EdgeStrengthTableModel();
    private final JTable edgeTable = new JTable(edgeTableModel);

    // ── tab 3: plot matrix ────────────────────────────────────────────────────

    private final JSpinner nSpinner;
    private final JButton resimulateButton = new JButton("Resimulate");
    private DataSet simulated;
    private DualPlotMatrix dual;

    // ── shared status ─────────────────────────────────────────────────────────

    private final JLabel status = new JLabel(" ");

    // ── constructor ───────────────────────────────────────────────────────────

    public NNEstimatorComparePanel(NNEstimatorModel model) {
        super(new BorderLayout(10, 10));

        this.model    = Objects.requireNonNull(model, "model");
        this.observed = Objects.requireNonNull(model.getInputData(), "observed");
        this.dag      = Objects.requireNonNull(model.getGraph(), "dag");

        int n0 = TMath.max(1, observed.getNumRows());
        this.nSpinner = new JSpinner(new SpinnerNumberModel(n0, 1, 10_000_000, 50));
        this.kSpinner = new JSpinner(new SpinnerNumberModel(5, 2, TMath.min(20, n0), 1));

        // Fallback to observed data if no simulation exists yet (e.g. after reload).
        this.simulated = model.getSimulatedData() != null
                ? model.getSimulatedData() : observed;

        boolean fitted = model.getEstimator() != null;
        runCvButton.setEnabled(fitted);
        computeEdgeButton.setEnabled(fitted);

        // Build tabs.
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Cross-Validation",         buildCvTab());
        tabs.addTab("Edge Strength",            buildEdgeStrengthTab());
        tabs.addTab("Observed vs. Resimulated", buildPlotTab());

        add(tabs,          BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);

        // Restore status and CV report if available.
        refreshAdequacyStatus(model);
        CVReport existingCv = model.getCvReport();
        if (existingCv != null) {
            cvTableModel.setReport(existingCv);
            cvSummaryLabel.setText(existingCv.toStatusLine());
            status.setText(existingCv.toStatusLine());
        }

        if (!fitted) {
            status.setText("Ready. Click Resimulate to fit the NN estimator "
                    + "before running CV or computing edge strengths.");
        }

        wireResimulate();
        wireCv();
        wireEdgeStrength();

        setPreferredSize(new Dimension(1200, 820));
    }

    // ── tab builders ──────────────────────────────────────────────────────────

    private JPanel buildCvTab() {
        JPanel tab = new JPanel(new BorderLayout(8, 8));
        tab.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        controls.add(new JLabel("Number of folds (k):"));
        controls.add(kSpinner);
        controls.add(runCvButton);

        cvSummaryLabel.setFont(cvSummaryLabel.getFont().deriveFont(Font.BOLD, 12f));
        JPanel summaryPanel = new JPanel(new BorderLayout());
        summaryPanel.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        summaryPanel.add(cvSummaryLabel, BorderLayout.CENTER);

        JPanel top = new JPanel(new BorderLayout());
        top.add(controls,     BorderLayout.NORTH);
        top.add(summaryPanel, BorderLayout.SOUTH);

        cvTable.setAutoCreateRowSorter(true);
        cvTable.setFillsViewportHeight(true);
        cvTable.setRowHeight(22);
        styleCvTable();
        JScrollPane scroll = new JScrollPane(cvTable);
        scroll.setBorder(new TitledBorder("Per-node OOS results (non-root nodes only)"));

        JLabel note = new JLabel(
                "<html><i>"
                        + "Continuous nodes: OOS R² (higher = better; positive = beats marginal mean). "
                        + "Discrete nodes: OOS cross-entropy improvement over marginal baseline in nats "
                        + "(higher = better; positive = beats marginal class frequencies). "
                        + "Root nodes are omitted — they have no parents to condition on."
                        + "</i></html>");
        note.setFont(note.getFont().deriveFont(Font.PLAIN, 11f));
        note.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));

        tab.add(top,    BorderLayout.NORTH);
        tab.add(scroll, BorderLayout.CENTER);
        tab.add(note,   BorderLayout.SOUTH);
        return tab;
    }

    private JPanel buildEdgeStrengthTab() {
        JPanel tab = new JPanel(new BorderLayout(8, 8));
        tab.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        populateChildCombo();

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        controls.add(new JLabel("Child node:"));
        controls.add(childCombo);
        controls.add(new JLabel("Simulated n:"));
        controls.add(edgeSimNSpinner);
        controls.add(new JLabel("  CV k:"));
        controls.add(kSpinner);
        controls.add(computeEdgeButton);

        edgeProgressLabel.setFont(
                edgeProgressLabel.getFont().deriveFont(Font.PLAIN, 11f));
        edgeResultLabel.setFont(
                edgeResultLabel.getFont().deriveFont(Font.BOLD, 12f));

        JPanel labelPanel = new JPanel(new GridLayout(2, 1, 0, 2));
        labelPanel.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        labelPanel.add(edgeProgressLabel);
        labelPanel.add(edgeResultLabel);

        JPanel top = new JPanel(new BorderLayout());
        top.add(controls,   BorderLayout.NORTH);
        top.add(labelPanel, BorderLayout.SOUTH);

        edgeTable.setFillsViewportHeight(true);
        edgeTable.setRowHeight(22);
        edgeTable.setAutoCreateRowSorter(true);
        styleEdgeTable();
        JScrollPane scroll = new JScrollPane(edgeTable);
        scroll.setBorder(new TitledBorder(
                "Parent strength results (history — sortable by MMD²)"));

        JLabel note = new JLabel(
                "<html><i>"
                        + "MMD² and ΔVar: marginal effect of removing the edge. "
                        + "Partial R²: OOS R² of residual regression R ~ X after controlling for "
                        + "other parents — positive (green/bold) = X explains variance beyond other parents. "
                        + "KL divergence in bits for discrete nodes."
                        + "</i></html>");
        note.setFont(note.getFont().deriveFont(Font.PLAIN, 11f));
        note.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));

        tab.add(top,    BorderLayout.NORTH);
        tab.add(scroll, BorderLayout.CENTER);
        tab.add(note,   BorderLayout.SOUTH);
        return tab;
    }

    private JPanel buildPlotTab() {
        JPanel tab = new JPanel(new BorderLayout(8, 8));

        JPanel header = new JPanel(new BorderLayout(8, 8));
        header.setBorder(new TitledBorder("NN Estimator — Observed vs. Resimulated"));
        JLabel instr = new JLabel(
                "<html>"
                        + "<b>What you're seeing:</b> Left is the observed dataset. Right is a "
                        + "resimulation whose joint distribution is learned by training a small "
                        + "neural network for each variable given its parents in the DAG "
                        + "(parameter-agnostic estimation)."
                        + "<br/>Use the variable selectors on the right to view pairwise scatter plots."
                        + "</html>");
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        controls.add(new JLabel("Sample size (right):"));
        controls.add(nSpinner);
        controls.add(resimulateButton);
        header.add(instr,    BorderLayout.CENTER);
        header.add(controls, BorderLayout.SOUTH);

        dual = new DualPlotMatrix(observed, simulated);

        tab.add(header, BorderLayout.NORTH);
        tab.add(dual,   BorderLayout.CENTER);
        return tab;
    }

    private JComponent buildFooter() {
        status.setFont(status.getFont().deriveFont(Font.PLAIN, 11f));
        status.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        JPanel p = new JPanel(new BorderLayout());
        p.add(status, BorderLayout.CENTER);
        return p;
    }

    // ── child combo ───────────────────────────────────────────────────────────

    private void populateChildCombo() {
        childCombo.removeAllItems();
        List<String> names = new ArrayList<>();
        for (Node n : dag.getNodes())
            if (!dag.getParents(n).isEmpty()) names.add(n.getName());
        Collections.sort(names);
        for (String name : names) childCombo.addItem(name);
        computeEdgeButton.setEnabled(
                model.getEstimator() != null && childCombo.getItemCount() > 0);
    }

    // ── wiring ────────────────────────────────────────────────────────────────

    private void wireResimulate() {
        resimulateButton.addActionListener(e -> {
            int n = ((Number) nSpinner.getValue()).intValue();
            resimulateButton.setEnabled(false);
            runCvButton.setEnabled(false);
            computeEdgeButton.setEnabled(false);
            status.setText("Fitting NN estimator and simulating " + n + " rows…");

            new SwingWorker<DataSet, Void>() {
                @Override protected DataSet doInBackground() {
                    model.resimulate(n);
                    return model.getSimulatedData();
                }
                @Override protected void done() {
                    try {
                        simulated = get();
                        dual.setRightData(simulated);
                        refreshAdequacyStatus(model);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        status.setText("Resimulation interrupted.");
                    } catch (ExecutionException ex) {
                        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                        status.setText("Resimulation failed: " + cause.getMessage());
                    } finally {
                        resimulateButton.setEnabled(true);
                        runCvButton.setEnabled(true);
                        computeEdgeButton.setEnabled(childCombo.getItemCount() > 0);
                    }
                    firePropertyChange("modelChanged", null, null);
                }
            }.execute();
        });
    }

    private void wireCv() {
        runCvButton.addActionListener(e -> {
            int k = ((Number) kSpinner.getValue()).intValue();
            runCvButton.setEnabled(false);
            resimulateButton.setEnabled(false);
            computeEdgeButton.setEnabled(false);
            cvSummaryLabel.setText("Running " + k + "-fold cross-validation…");
            status.setText("Running " + k + "-fold cross-validation…");

            new SwingWorker<CVReport, Void>() {
                @Override protected CVReport doInBackground() {
                    model.runCrossValidate(k);
                    return model.getCvReport();
                }
                @Override protected void done() {
                    try {
                        CVReport report = get();
                        cvTableModel.setReport(report);
                        cvSummaryLabel.setText(report != null ? report.toStatusLine() : " ");
                        status.setText(report != null ? report.toStatusLine() : "CV complete.");
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        cvSummaryLabel.setText("Cross-validation interrupted.");
                        status.setText("Cross-validation interrupted.");
                    } catch (ExecutionException ex) {
                        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                        cvSummaryLabel.setText("CV failed: " + cause.getMessage());
                        status.setText("CV failed: " + cause.getMessage());
                    } finally {
                        runCvButton.setEnabled(true);
                        resimulateButton.setEnabled(true);
                        computeEdgeButton.setEnabled(childCombo.getItemCount() > 0);
                    }
                    firePropertyChange("modelChanged", null, null);
                }
            }.execute();
        });
    }

    /**
     * Pair holder for SwingWorker publish/process — carries both the marginal
     * and partial results for one parent edge so they arrive together.
     */
    private record EdgePair(EdgeStrengthResult edge,
                            PartialEdgeStrengthResult partial) {}

    private void wireEdgeStrength() {
        computeEdgeButton.addActionListener(e -> {
            String childName = (String) childCombo.getSelectedItem();
            if (childName == null) return;

            Node childNode = dag.getNode(childName);
            if (childNode == null) return;

            List<Node> parents = new ArrayList<>(dag.getParents(childNode));
            parents.sort(Comparator.comparing(Node::getName));

            if (parents.isEmpty()) {
                edgeResultLabel.setText(childName + " has no parents.");
                return;
            }

            int simN  = ((Number) edgeSimNSpinner.getValue()).intValue();
            int cvK   = ((Number) kSpinner.getValue()).intValue();
            int total = parents.size();

            computeEdgeButton.setEnabled(false);
            resimulateButton.setEnabled(false);
            runCvButton.setEnabled(false);
            edgeProgressLabel.setText("Computing 1 of " + total + " parents…");
            edgeResultLabel.setText(" ");
            status.setText("Computing parent strengths for " + childName + "…");

            new SwingWorker<List<EdgePair>, EdgePair>() {

                @Override
                protected List<EdgePair> doInBackground() {
                    List<EdgePair> results = new ArrayList<>();
                    for (int i = 0; i < parents.size(); i++) {
                        Node parent = parents.get(i);
                        final int idx = i;
                        SwingUtilities.invokeLater(() ->
                                edgeProgressLabel.setText(
                                        "Computing " + (idx + 1) + " of " + total
                                                + ": " + parent.getName()
                                                + " \u2192 " + childName + "…"));

                        EdgeStrengthResult edge = model.getEstimator()
                                .computeEdgeStrength(
                                        parent.getName(), childName, simN);

                        PartialEdgeStrengthResult partial = model.getEstimator()
                                .computePartialEdgeStrength(
                                        parent.getName(), childName, cvK);

                        EdgePair pair = new EdgePair(edge, partial);
                        results.add(pair);
                        publish(pair);
                    }
                    return results;
                }

                @Override
                protected void process(List<EdgePair> chunks) {
                    for (EdgePair pair : chunks) {
                        edgeTableModel.addResult(pair.edge(), pair.partial());
                    }
                }

                @Override
                protected void done() {
                    try {
                        List<EdgePair> results = get();
                        // Highlight the strongest edge by MMD².
                        results.stream()
                                .max(Comparator.comparingDouble(p -> p.edge().mmd2))
                                .ifPresent(strongest ->
                                        edgeResultLabel.setText(
                                                "Strongest: "
                                                        + strongest.edge().toSummaryLine()));
                        edgeProgressLabel.setText(
                                "Done — " + results.size()
                                        + " parent(s) computed for " + childName + ".");
                        status.setText(
                                "Edge strengths computed for " + childName + ".");
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        edgeProgressLabel.setText("Interrupted.");
                        status.setText("Edge strength computation interrupted.");
                    } catch (ExecutionException ex) {
                        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                        edgeProgressLabel.setText("Failed: " + cause.getMessage());
                        status.setText("Edge strength failed: " + cause.getMessage());
                    } finally {
                        computeEdgeButton.setEnabled(true);
                        resimulateButton.setEnabled(true);
                        runCvButton.setEnabled(true);
                    }
                    firePropertyChange("modelChanged", null, null);
                }
            }.execute();
        });
    }

    // ── status helpers ────────────────────────────────────────────────────────

    private void refreshAdequacyStatus(NNEstimatorModel model) {
        var report = model.getAdequacyReport();
        if (report == null) {
            status.setText("Ready. Click Resimulate to fit the NN estimator "
                    + "before running CV or computing edge strengths.");
            return;
        }
        status.setText(String.format(
                "n = %d  |  MMD² = %.4f  |  Mean node improvement = %.4f"
                        + "  |  Nodes improved = %.0f%%",
                model.getSampleSize(),
                report.getMmd2(),
                report.getMeanImprovement(),
                report.getFracImproved() * 100.0));
    }

    // ── CV table styling ──────────────────────────────────────────────────────

    private void styleCvTable() {
        cvTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable table, Object value, boolean isSelected,
                    boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(
                        table, value, isSelected, hasFocus, row, column);
                setHorizontalAlignment(SwingConstants.RIGHT);

                int modelCol = table.convertColumnIndexToModel(column);
                if (modelCol == CVTableModel.COL_OOS_R2 && value instanceof String s) {
                    if (s.equals("—")) {
                        setForeground(Color.GRAY);
                        setFont(getFont().deriveFont(Font.PLAIN));
                    } else {
                        try {
                            double v = Double.parseDouble(s);
                            if (v > 0) {
                                setForeground(new Color(0, 130, 0));
                                setFont(getFont().deriveFont(Font.BOLD));
                            } else {
                                setForeground(Color.RED);
                                setFont(getFont().deriveFont(Font.PLAIN));
                            }
                        } catch (NumberFormatException ignored) {
                            setForeground(table.getForeground());
                            setFont(getFont().deriveFont(Font.PLAIN));
                        }
                    }
                } else {
                    setForeground(isSelected
                            ? table.getSelectionForeground()
                            : table.getForeground());
                    setFont(getFont().deriveFont(Font.PLAIN));
                }
                return c;
            }
        });
    }

    // ── edge table styling ────────────────────────────────────────────────────

    private void styleEdgeTable() {
        edgeTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable table, Object value, boolean isSelected,
                    boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(
                        table, value, isSelected, hasFocus, row, column);
                int modelCol = table.convertColumnIndexToModel(column);

                // Left-align the edge name column; right-align everything else.
                setHorizontalAlignment(modelCol == EdgeStrengthTableModel.COL_EDGE
                        ? SwingConstants.LEFT : SwingConstants.RIGHT);

                // Color-code the partial R² column the same way as OOS R² in CV.
                if (modelCol == EdgeStrengthTableModel.COL_PARTIAL
                        && value instanceof String s && !s.equals("—")) {
                    try {
                        double v = Double.parseDouble(s);
                        if (v > 0) {
                            setForeground(new Color(0, 130, 0));
                            setFont(getFont().deriveFont(Font.BOLD));
                        } else {
                            setForeground(Color.RED);
                            setFont(getFont().deriveFont(Font.PLAIN));
                        }
                    } catch (NumberFormatException ignored) {
                        setForeground(isSelected
                                ? table.getSelectionForeground()
                                : table.getForeground());
                        setFont(getFont().deriveFont(Font.PLAIN));
                    }
                } else {
                    setForeground(isSelected
                            ? table.getSelectionForeground()
                            : table.getForeground());
                    setFont(getFont().deriveFont(Font.PLAIN));
                }
                return c;
            }
        });
    }

    // =========================================================================
    // CVTableModel
    // =========================================================================

    private static final class CVTableModel extends AbstractTableModel {

        static final int COL_NODE     = 0;
        static final int COL_TYPE     = 1;
        static final int COL_PARENTS  = 2;
        static final int COL_BASELINE = 3;
        static final int COL_OOS_R2   = 4;
        static final int COL_OOS_MAIN = 5;

        private static final String[] COLUMNS =
                {"Node", "Type", "Parents", "Baseline",
                        "OOS R² / Xent Improv.", "OOS MSE / Xent"};

        private final List<NodeCVSummary> rows = new ArrayList<>();

        void setReport(CVReport report) {
            rows.clear();
            if (report != null) rows.addAll(report.nodeSummaries);
            fireTableDataChanged();
        }

        @Override public int getRowCount()    { return rows.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int col) { return COLUMNS[col]; }

        @Override
        public Object getValueAt(int row, int col) {
            NodeCVSummary s = rows.get(row);
            return switch (col) {
                case COL_NODE     -> s.node;
                case COL_TYPE     -> s.discreteChild ? "Discrete" : "Continuous";
                case COL_PARENTS  -> String.join(", ", s.parents);
                case COL_BASELINE -> s.discreteChild
                        ? fmt(s.baselineXent) : fmt(s.baselineMse);
                case COL_OOS_R2   -> s.discreteChild
                        ? (Double.isFinite(s.oosXent) && Double.isFinite(s.baselineXent)
                           ? fmt(s.baselineXent - s.oosXent) : "—")
                        : (Double.isFinite(s.oosR2) ? fmt(s.oosR2) : "—");
                case COL_OOS_MAIN -> s.discreteChild
                        ? fmt(s.oosXent) : fmt(s.oosMse);
                default -> "";
            };
        }

        private static String fmt(double v) {
            return Double.isFinite(v) ? String.format("%.4f", v) : "—";
        }
    }

    // =========================================================================
    // EdgeStrengthTableModel
    // =========================================================================

    private static final class EdgeStrengthTableModel extends AbstractTableModel {

        private static final String[] COLUMNS =
                {"Edge", "MMD²", "ΔVar / KL (bits)",
                        "Partial R² / Xent Improv.", "Type", "Sim n"};

        static final int COL_EDGE    = 0;
        static final int COL_MMD2    = 1;
        static final int COL_DELTA   = 2;
        static final int COL_PARTIAL = 3;
        static final int COL_TYPE    = 4;
        static final int COL_N       = 5;

        private record EdgeRow(EdgeStrengthResult edge,
                               PartialEdgeStrengthResult partial) {}

        private final List<EdgeRow> rows = new ArrayList<>();

        void addResult(EdgeStrengthResult edge, PartialEdgeStrengthResult partial) {
            rows.add(0, new EdgeRow(edge, partial));   // newest first
            fireTableDataChanged();
        }

        @Override public int getRowCount()    { return rows.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int col) { return COLUMNS[col]; }

        @Override
        public Object getValueAt(int row, int col) {
            EdgeRow er = rows.get(row);
            EdgeStrengthResult        e = er.edge();
            PartialEdgeStrengthResult p = er.partial();
            return switch (col) {
                case COL_EDGE  -> e.parentName + " \u2192 " + e.childName;
                case COL_MMD2  -> fmt(e.mmd2);
                case COL_DELTA -> e.discreteChild
                        ? fmt(e.klDivBits) + " bits"
                        : fmt(e.varianceDiff);
                case COL_PARTIAL -> {
                    if (p == null) yield "—";
                    yield p.discreteChild
                            ? (Double.isFinite(p.partialXentImprovement)
                               ? fmt(p.partialXentImprovement) : "—")
                            : (Double.isFinite(p.partialR2)
                               ? fmt(p.partialR2) : "—");
                }
                case COL_TYPE -> e.discreteChild ? "Discrete" : "Continuous";
                case COL_N    -> e.simulatedN;
                default -> "";
            };
        }

        private static String fmt(double v) {
            return Double.isFinite(v) ? String.format("%.4f", v) : "—";
        }
    }

    // =========================================================================
    // DualPlotMatrix
    // =========================================================================

    private static final class DualPlotMatrix extends JPanel {

        private final JList<Node> rowSelector;
        private final JList<Node> colSelector;
        private final JPanel chartsLeft  = new JPanel();
        private final JPanel chartsRight = new JPanel();

        private DataSet left;
        private DataSet right;
        private List<Node> vars;
        private Node[] varsArray;

        private int numBins = 9;
        private boolean addRegressionLines      = false;
        private boolean removeZeroPointsPerPlot = false;
        private ScatterPlot.JitterStyle jitterStyle = ScatterPlot.JitterStyle.None;
        private Map<Node, VariableConditioningEditor.ConditioningPanel>
                conditioningPanelMap = new HashMap<>();
        private int[] lastRows = {0};
        private int[] lastCols = {0};

        DualPlotMatrix(DataSet left, DataSet right) {
            super(new BorderLayout(8, 8));
            this.left  = Objects.requireNonNull(left,  "left");
            this.right = Objects.requireNonNull(right, "right");

            rebuildVarList();

            this.rowSelector = new JList<>(varsArray);
            this.colSelector = new JList<>(varsArray);

            if (varsArray.length > 0) {
                rowSelector.setSelectedIndex(0);
                colSelector.setSelectedIndex(0);
            }

            rowSelector.addListSelectionListener(e -> refreshCharts());
            colSelector.addListSelectionListener(e -> refreshCharts());

            add(buildMenuBar(), BorderLayout.NORTH);
            add(buildCenter(),  BorderLayout.CENTER);
            refreshCharts();
        }

        void setRightData(DataSet newRight) {
            Set<String> selRows = selectedNames(rowSelector);
            Set<String> selCols = selectedNames(colSelector);
            Set<String> lastRN  = namesForIndices(lastRows);
            Set<String> lastCN  = namesForIndices(lastCols);

            this.right = Objects.requireNonNull(newRight, "newRight");
            rebuildVarList();
            rowSelector.setListData(varsArray);
            colSelector.setListData(varsArray);

            if (varsArray.length == 0) {
                lastRows = new int[]{0};
                lastCols = new int[]{0};
                refreshCharts();
                return;
            }

            rowSelector.setSelectedIndices(indicesForNames(selRows));
            colSelector.setSelectedIndices(indicesForNames(selCols));
            if (rowSelector.getSelectedIndices().length == 0) rowSelector.setSelectedIndex(0);
            if (colSelector.getSelectedIndices().length == 0) colSelector.setSelectedIndex(0);

            int[] rr = indicesForNames(lastRN);
            int[] rc = indicesForNames(lastCN);
            lastRows = rr.length > 0 ? rr : rowSelector.getSelectedIndices();
            lastCols = rc.length > 0 ? rc : colSelector.getSelectedIndices();

            refreshCharts();
        }

        private Set<String> selectedNames(JList<Node> list) {
            Set<String> names = new LinkedHashSet<>();
            for (Node n : list.getSelectedValuesList())
                if (n != null && n.getName() != null) names.add(n.getName());
            return names;
        }

        private Set<String> namesForIndices(int[] indices) {
            Set<String> names = new LinkedHashSet<>();
            for (int idx : indices)
                if (idx >= 0 && idx < varsArray.length && varsArray[idx] != null)
                    names.add(varsArray[idx].getName());
            return names;
        }

        private int[] indicesForNames(Set<String> names) {
            if (names == null || names.isEmpty()) return new int[0];
            List<Integer> idxs = new ArrayList<>();
            for (int i = 0; i < varsArray.length; i++)
                if (varsArray[i] != null && names.contains(varsArray[i].getName()))
                    idxs.add(i);
            int[] out = new int[idxs.size()];
            for (int i = 0; i < idxs.size(); i++) out[i] = idxs.get(i);
            return out;
        }

        private void rebuildVarList() {
            Map<String, Node> leftByName = new LinkedHashMap<>();
            for (Node n : left.getVariables())
                if (n != null && n.getName() != null) leftByName.put(n.getName(), n);
            Set<String> rightNames = new HashSet<>();
            for (Node n : right.getVariables())
                if (n != null && n.getName() != null) rightNames.add(n.getName());
            List<Node> out = new ArrayList<>();
            for (Map.Entry<String, Node> e : leftByName.entrySet())
                if (rightNames.contains(e.getKey())) out.add(e.getValue());
            out.sort(NaturalSort.naturalComparator());
            this.vars      = out;
            this.varsArray = out.toArray(new Node[0]);
        }

        private JMenuBar buildMenuBar() {
            JMenuBar menuBar  = new JMenuBar();
            JMenu    settings = new JMenu("Settings");
            menuBar.add(settings);

            JMenuItem addTrendLines = new JCheckBoxMenuItem("Add Trend Lines");
            addTrendLines.setAccelerator(
                    KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK));
            settings.add(addTrendLines);
            addTrendLines.addActionListener(e -> {
                addRegressionLines = !addRegressionLines; refreshCharts();
            });

            JMenuItem removeZeroPoints =
                    new JCheckBoxMenuItem("Remove Zero Points Per Plot");
            removeZeroPoints.setAccelerator(
                    KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK));
            settings.add(removeZeroPoints);
            removeZeroPoints.addActionListener(e -> {
                removeZeroPointsPerPlot = !removeZeroPointsPerPlot; refreshCharts();
            });

            JMenu binsMenu = new JMenu("Set number of Bins for Histograms");
            ButtonGroup bg = new ButtonGroup();
            for (int i = 2; i <= 30; i++) {
                int _i = i;
                JMenuItem item = new JCheckBoxMenuItem(String.valueOf(i));
                bg.add(item);
                if (i == numBins) item.setSelected(true);
                item.addActionListener(e -> { numBins = _i; refreshCharts(); });
                binsMenu.add(item);
            }
            settings.add(binsMenu);

            JMenu jitterMenu = new JMenu("Jitter Style (Display Only)");
            ButtonGroup jg   = new ButtonGroup();
            JMenuItem j1 = new JCheckBoxMenuItem(
                    ScatterPlot.JitterStyle.Gaussian.toString());
            JMenuItem j2 = new JCheckBoxMenuItem(
                    ScatterPlot.JitterStyle.Uniform.toString());
            JMenuItem j3 = new JCheckBoxMenuItem(
                    ScatterPlot.JitterStyle.None.toString());
            j1.setAccelerator(
                    KeyStroke.getKeyStroke(KeyEvent.VK_U, InputEvent.CTRL_DOWN_MASK));
            j2.setAccelerator(
                    KeyStroke.getKeyStroke(KeyEvent.VK_I, InputEvent.CTRL_DOWN_MASK));
            j3.setAccelerator(
                    KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));
            jg.add(j1); jg.add(j2); jg.add(j3); j3.setSelected(true);
            jitterMenu.add(j1); jitterMenu.add(j2); jitterMenu.add(j3);
            j1.addActionListener(e -> {
                jitterStyle = ScatterPlot.JitterStyle.Gaussian; refreshCharts();
            });
            j2.addActionListener(e -> {
                jitterStyle = ScatterPlot.JitterStyle.Uniform; refreshCharts();
            });
            j3.addActionListener(e -> {
                jitterStyle = ScatterPlot.JitterStyle.None; refreshCharts();
            });
            settings.add(jitterMenu);

            JMenuItem editConditioning = new JMenuItem("Edit Conditioning…");
            editConditioning.setAccelerator(
                    KeyStroke.getKeyStroke(KeyEvent.VK_P, InputEvent.CTRL_DOWN_MASK));
            editConditioning.addActionListener(e -> {
                VariableConditioningEditor ce =
                        new VariableConditioningEditor(left, conditioningPanelMap);
                ce.setPreferredSize(new Dimension(320, 320));
                JOptionPane.showMessageDialog(this, new JScrollPane(ce));
                conditioningPanelMap = ce.getConditioningPanelMap();
                refreshCharts();
            });
            settings.add(editConditioning);

            return menuBar;
        }

        private JComponent buildCenter() {
            JScrollPane leftScroll = new JScrollPane(chartsLeft);
            leftScroll.setPreferredSize(new Dimension(520, 520));
            leftScroll.setBorder(new TitledBorder("Observed"));

            JScrollPane rightScroll = new JScrollPane(chartsRight);
            rightScroll.setPreferredSize(new Dimension(520, 520));
            rightScroll.setBorder(new TitledBorder("Resimulated"));

            JSplitPane split = new JSplitPane(
                    JSplitPane.HORIZONTAL_SPLIT, leftScroll, rightScroll);
            split.setResizeWeight(0.5);

            Box selectors = Box.createVerticalBox();
            selectors.add(new JLabel("Rows"));
            selectors.add(new JScrollPane(rowSelector));
            selectors.add(Box.createVerticalStrut(6));
            selectors.add(new JLabel("Cols"));
            selectors.add(new JScrollPane(colSelector));
            selectors.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
            selectors.setPreferredSize(new Dimension(220, 520));

            JPanel center = new JPanel(new BorderLayout());
            center.add(split,     BorderLayout.CENTER);
            center.add(selectors, BorderLayout.EAST);
            return center;
        }

        private void refreshCharts() {
            constructPlotMatrix(chartsLeft,  left,  rowSelector, colSelector);
            constructPlotMatrix(chartsRight, right, rowSelector, colSelector);
        }

        private void constructPlotMatrix(JPanel charts, DataSet dataSet,
                                         JList<Node> rowSel, JList<Node> colSel) {
            int[] rowIndices = rowSel.getSelectedIndices();
            int[] colIndices = colSel.getSelectedIndices();

            charts.removeAll();
            if (rowIndices.length == 0 || colIndices.length == 0) {
                charts.setLayout(new BorderLayout());
                charts.add(new JLabel("Select at least one row and one column.",
                        SwingConstants.CENTER), BorderLayout.CENTER);
                charts.revalidate(); charts.repaint();
                return;
            }

            charts.setLayout(new GridLayout(rowIndices.length, colIndices.length));
            for (int rowIndex : rowIndices) {
                for (int colIndex : colIndices) {
                    JPanel cell = (rowIndex == colIndex)
                            ? buildHistogram(dataSet, rowIndex, rowIndices, colIndices)
                            : buildScatter(dataSet, rowIndex, colIndex,
                            rowIndices, colIndices);
                    addPanelListener(rowIndex, colIndex, cell);
                    charts.add(cell);
                }
            }
            charts.revalidate(); charts.repaint();
        }

        private JPanel buildHistogram(DataSet dataSet, int index,
                                      int[] rowIndices, int[] colIndices) {
            Histogram h = new Histogram(
                    dataSet, vars.get(index).getName(), removeZeroPointsPerPlot);
            applyConditioning(h);
            if (!(vars.get(index) instanceof DiscreteVariable)) h.setNumBins(numBins);
            HistogramPanel panel = new HistogramPanel(
                    h, rowIndices.length == 1 && colIndices.length == 1);
            panel.setMinimumSize(new Dimension(10, 10));
            return panel;
        }

        private JPanel buildScatter(DataSet dataSet, int rowIndex, int colIndex,
                                    int[] rowIndices, int[] colIndices) {
            ScatterPlot sp = new ScatterPlot(
                    dataSet, addRegressionLines,
                    vars.get(rowIndex).getName(),
                    vars.get(colIndex).getName(),
                    removeZeroPointsPerPlot);
            applyConditioning(sp);
            sp.setJitterStyle(jitterStyle);

            ScatterplotPanel panel = new ScatterplotPanel(sp, removeZeroPointsPerPlot);
            panel.setDrawAxes(rowIndices.length == 1 && colIndices.length == 1);
            panel.setMinimumSize(new Dimension(10, 10));

            int pointSize = 5;
            if (rowIndices.length > 2 || colIndices.length > 2) pointSize = 4;
            if (rowIndices.length > 3 || colIndices.length > 3) pointSize = 3;
            if (rowIndices.length > 5 || colIndices.length > 5) pointSize = 2;
            panel.setPointSize(pointSize);
            return panel;
        }

        private void applyConditioning(Histogram h) {
            for (Node node : conditioningPanelMap.keySet()) {
                if (node instanceof ContinuousVariable var) {
                    var p = (VariableConditioningEditor.ContinuousConditioningPanel)
                            conditioningPanelMap.get(var);
                    h.addConditioningVariable(var.getName(), p.getLow(), p.getHigh());
                } else if (node instanceof DiscreteVariable var) {
                    var p = (VariableConditioningEditor.DiscreteConditioningPanel)
                            conditioningPanelMap.get(var);
                    h.addConditioningVariable(var.getName(), p.getIndex());
                }
            }
        }

        private void applyConditioning(ScatterPlot sp) {
            for (Node node : conditioningPanelMap.keySet()) {
                if (node instanceof ContinuousVariable var) {
                    var p = (VariableConditioningEditor.ContinuousConditioningPanel)
                            conditioningPanelMap.get(var);
                    sp.addConditioningVariable(var.getName(), p.getLow(), p.getHigh());
                } else if (node instanceof DiscreteVariable var) {
                    var p = (VariableConditioningEditor.DiscreteConditioningPanel)
                            conditioningPanelMap.get(var);
                    sp.addConditioningVariable(var.getName(), p.getIndex());
                }
            }
        }

        private void addPanelListener(int rowIndex, int colIndex, JPanel panel) {
            panel.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                    if (rowSelector.getSelectedIndices().length == 1
                            && colSelector.getSelectedIndices().length == 1) {
                        rowSelector.setSelectedIndices(lastRows);
                        colSelector.setSelectedIndices(lastCols);
                        lastRows = new int[]{rowIndex};
                        lastCols = new int[]{colIndex};
                    } else {
                        lastRows = rowSelector.getSelectedIndices();
                        lastCols = colSelector.getSelectedIndices();
                        rowSelector.setSelectedIndex(rowIndex);
                        colSelector.setSelectedIndex(colIndex);
                    }
                    refreshCharts();
                }
            });
        }
    }
}
