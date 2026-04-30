package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.data.Histogram;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.sem.CVReport;
import edu.cmu.tetrad.sem.NodeCVSummary;
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
 * Side-by-side visual comparison panel for {@link NNEstimatorModel}, with an
 * additional cross-validation tab for honest OOS metrics.
 *
 * <p>The panel has two tabs:
 * <ol>
 *   <li><b>Observed vs. Resimulated</b> — the existing side-by-side plot matrix.
 *       "Resimulate" retrains on all data and redraws.</li>
 *   <li><b>Cross-Validation</b> — runs k-fold CV and displays per-node OOS metrics
 *       (R² for continuous, 0-1 loss for discrete) plus the whole-graph OOS MMD²,
 *       in a sortable table with a one-line summary at the top.</li>
 * </ol>
 *
 * <p>Both long-running operations (resimulate and CV) run on background threads
 * via {@link SwingWorker} so the UI remains responsive.
 */
public final class NNEstimatorComparePanel extends JPanel {

    // ── model ─────────────────────────────────────────────────────────────────

    private final NNEstimatorModel model;
    private final DataSet observed;
    private final Graph dag;

    // ── tab 1: plot matrix ────────────────────────────────────────────────────

    private final JSpinner nSpinner;
    private final JButton resimulateButton = new JButton("Resimulate");
    private DataSet simulated;
    private DualPlotMatrix dual;

    // ── tab 2: cross-validation ───────────────────────────────────────────────

    private final JSpinner kSpinner;
    private final JButton runCvButton = new JButton("Run Cross-Validation");
    private final JLabel cvSummaryLabel = new JLabel(" ");
    private final CVTableModel cvTableModel = new CVTableModel();
    private final JTable cvTable = new JTable(cvTableModel);

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

        this.simulated = model.getSimulatedData();

        // Build tabs.
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Observed vs. Resimulated", buildPlotTab());
        tabs.addTab("Cross-Validation",          buildCvTab());

        add(tabs,         BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);

        // Show initial adequacy summary if already available.
        refreshStatus(model);

        wireResimulate();
        wireCv();

        setPreferredSize(new Dimension(1200, 820));
    }

    // ── tab builders ──────────────────────────────────────────────────────────

    private JPanel buildPlotTab() {
        JPanel tab = new JPanel(new BorderLayout(8, 8));

        // Header.
        JPanel header = new JPanel(new BorderLayout(8, 8));
        header.setBorder(new TitledBorder("NN Estimator — Observed vs. Resimulated"));
        JLabel instr = new JLabel(
                "<html>"
                        + "<b>What you're seeing:</b> Left is the observed dataset. Right is a resimulation "
                        + "whose joint distribution is learned by training a small neural network for each "
                        + "variable given its parents in the DAG (parameter-agnostic estimation)."
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

    private JPanel buildCvTab() {
        JPanel tab = new JPanel(new BorderLayout(8, 8));
        tab.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // Controls at top.
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        controls.add(new JLabel("Number of folds (k):"));
        controls.add(kSpinner);
        controls.add(runCvButton);

        // Summary line.
        cvSummaryLabel.setFont(cvSummaryLabel.getFont().deriveFont(Font.BOLD, 12f));
        JPanel summaryPanel = new JPanel(new BorderLayout());
        summaryPanel.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        summaryPanel.add(cvSummaryLabel, BorderLayout.CENTER);

        JPanel top = new JPanel(new BorderLayout());
        top.add(controls,     BorderLayout.NORTH);
        top.add(summaryPanel, BorderLayout.SOUTH);

        // Table.
        cvTable.setAutoCreateRowSorter(true);
        cvTable.setFillsViewportHeight(true);
        cvTable.setRowHeight(22);
        styleTable();
        JScrollPane scroll = new JScrollPane(cvTable);
        scroll.setBorder(new TitledBorder("Per-node OOS results (non-root nodes only)"));

        // Explanation.
        JLabel note = new JLabel(
                "<html><i>Continuous nodes: OOS R² (higher = better; positive = beats marginal mean). "
                        + "Discrete nodes: OOS 0-1 loss (lower = better). "
                        + "Root nodes are omitted — they have no parents to condition on.</i></html>");
        note.setFont(note.getFont().deriveFont(Font.PLAIN, 11f));
        note.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));

        tab.add(top,    BorderLayout.NORTH);
        tab.add(scroll, BorderLayout.CENTER);
        tab.add(note,   BorderLayout.SOUTH);
        return tab;
    }

    private JComponent buildFooter() {
        status.setFont(status.getFont().deriveFont(Font.PLAIN, 11f));
        status.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        JPanel p = new JPanel(new BorderLayout());
        p.add(status, BorderLayout.CENTER);
        return p;
    }

    // ── wiring ────────────────────────────────────────────────────────────────

    private void wireResimulate() {
        resimulateButton.addActionListener(e -> {
            int n = ((Number) nSpinner.getValue()).intValue();
            resimulateButton.setEnabled(false);
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
                        refreshStatus(model);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        status.setText("Resimulation interrupted.");
                    } catch (ExecutionException ex) {
                        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                        status.setText("Resimulation failed: " + cause.getMessage());
                    } finally {
                        resimulateButton.setEnabled(true);
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
                    }
                    firePropertyChange("modelChanged", null, null);
                }
            }.execute();
        });
    }

    // ── status helpers ────────────────────────────────────────────────────────

    private void refreshStatus(NNEstimatorModel model) {
        var report = model.getAdequacyReport();
        if (report == null) {
            status.setText("Ready.");
            return;
        }
        status.setText(String.format(
                "n = %d  |  MMD² = %.4f  |  Mean node improvement = %.4f  |  Nodes improved = %.0f%%",
                model.getSampleSize(),
                report.getMmd2(),
                report.getMeanImprovement(),
                report.getFracImproved() * 100.0));
    }

    // ── CV table styling ──────────────────────────────────────────────────────

    private void styleTable() {
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

    // =========================================================================
    // CVTableModel
    // =========================================================================

    private static final class CVTableModel extends AbstractTableModel {

        static final int COL_NODE      = 0;
        static final int COL_TYPE      = 1;
        static final int COL_PARENTS   = 2;
        static final int COL_BASELINE  = 3;
        static final int COL_OOS_R2    = 4;   // R² for continuous, loss for discrete
        static final int COL_OOS_MAIN  = 5;   // raw OOS MSE or 0-1 loss

        private static final String[] COLUMNS =
                {"Node", "Type", "Parents", "Baseline", "OOS R² / Improv.", "OOS MSE / Loss"};

        private final List<NodeCVSummary> rows = new ArrayList<>();
        private double mmd2 = Double.NaN;

        void setReport(CVReport report) {
            rows.clear();
            mmd2 = Double.NaN;
            if (report != null) {
                rows.addAll(report.nodeSummaries);
                mmd2 = report.meanOosMmd2;
            }
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
                        ? fmt(s.baselineXent)
                        : fmt(s.baselineMse);
                case COL_OOS_R2   -> s.discreteChild
                        ? (Double.isFinite(s.oosXent) && Double.isFinite(s.baselineXent)
                           ? fmt(s.baselineXent - s.oosXent)   // improvement
                           : "—")
                        : (Double.isFinite(s.oosR2) ? fmt(s.oosR2) : "—");
                case COL_OOS_MAIN -> s.discreteChild
                        ? fmt(s.oosXent)
                        : fmt(s.oosMse);
                default -> "";
            };
        }

        private static String fmt(double v) {
            return Double.isFinite(v) ? String.format("%.4f", v) : "—";
        }
    }

    // =========================================================================
    // DualPlotMatrix — unchanged from previous version
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
            addTrendLines.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK));
            settings.add(addTrendLines);
            addTrendLines.addActionListener(e -> { addRegressionLines = !addRegressionLines; refreshCharts(); });

            JMenuItem removeZeroPoints = new JCheckBoxMenuItem("Remove Zero Points Per Plot");
            removeZeroPoints.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK));
            settings.add(removeZeroPoints);
            removeZeroPoints.addActionListener(e -> { removeZeroPointsPerPlot = !removeZeroPointsPerPlot; refreshCharts(); });

            JMenu binsMenu  = new JMenu("Set number of Bins for Histograms");
            ButtonGroup bg  = new ButtonGroup();
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
            JMenuItem j1 = new JCheckBoxMenuItem(ScatterPlot.JitterStyle.Gaussian.toString());
            JMenuItem j2 = new JCheckBoxMenuItem(ScatterPlot.JitterStyle.Uniform.toString());
            JMenuItem j3 = new JCheckBoxMenuItem(ScatterPlot.JitterStyle.None.toString());
            j1.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_U, InputEvent.CTRL_DOWN_MASK));
            j2.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_I, InputEvent.CTRL_DOWN_MASK));
            j3.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));
            jg.add(j1); jg.add(j2); jg.add(j3); j3.setSelected(true);
            jitterMenu.add(j1); jitterMenu.add(j2); jitterMenu.add(j3);
            j1.addActionListener(e -> { jitterStyle = ScatterPlot.JitterStyle.Gaussian; refreshCharts(); });
            j2.addActionListener(e -> { jitterStyle = ScatterPlot.JitterStyle.Uniform;  refreshCharts(); });
            j3.addActionListener(e -> { jitterStyle = ScatterPlot.JitterStyle.None;     refreshCharts(); });
            settings.add(jitterMenu);

            JMenuItem editConditioning = new JMenuItem("Edit Conditioning…");
            editConditioning.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_P, InputEvent.CTRL_DOWN_MASK));
            editConditioning.addActionListener(e -> {
                VariableConditioningEditor ce = new VariableConditioningEditor(left, conditioningPanelMap);
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

            JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftScroll, rightScroll);
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
                            : buildScatter(dataSet, rowIndex, colIndex, rowIndices, colIndices);
                    addPanelListener(rowIndex, colIndex, cell);
                    charts.add(cell);
                }
            }
            charts.revalidate(); charts.repaint();
        }

        private JPanel buildHistogram(DataSet dataSet, int index,
                                      int[] rowIndices, int[] colIndices) {
            Histogram h = new Histogram(dataSet, vars.get(index).getName(), removeZeroPointsPerPlot);
            applyConditioning(h);
            if (!(vars.get(index) instanceof DiscreteVariable)) h.setNumBins(numBins);
            HistogramPanel panel = new HistogramPanel(h, rowIndices.length == 1 && colIndices.length == 1);
            panel.setMinimumSize(new Dimension(10, 10));
            return panel;
        }

        private JPanel buildScatter(DataSet dataSet, int rowIndex, int colIndex,
                                    int[] rowIndices, int[] colIndices) {
            ScatterPlot sp = new ScatterPlot(dataSet, addRegressionLines,
                    vars.get(rowIndex).getName(), vars.get(colIndex).getName(),
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
