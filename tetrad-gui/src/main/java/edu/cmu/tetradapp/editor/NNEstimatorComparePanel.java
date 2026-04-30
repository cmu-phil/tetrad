package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.data.Histogram;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.sem.AdequacyReport;
import edu.cmu.tetrad.util.NaturalSort;
import edu.cmu.tetrad.util.TMath;
import edu.cmu.tetradapp.model.NNEstimatorModel;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Side-by-side visual comparison panel for {@link NNEstimatorModel}.
 *
 * <p>Left panel shows the observed dataset; right panel shows data simulated
 * from the fitted NN factorization of the DAG. The user can change the
 * simulated sample size and click "Resimulate" to refit and redraw.
 *
 * <p>Resimulation runs on a background thread (via {@link SwingWorker}) so
 * the UI remains responsive during NN training.
 *
 * <p>After each resimulation the status bar shows a one-line summary of the
 * {@link AdequacyReport}: the global MMD² and per-node improvement statistics.
 */
public final class NNEstimatorComparePanel extends JPanel {

    // ── model ─────────────────────────────────────────────────────────────────

    private final NNEstimatorModel model;
    private final DataSet observed;
    private final Graph dag;

    // ── UI components ─────────────────────────────────────────────────────────

    private final JSpinner nSpinner;
    private final JButton resimulateButton = new JButton("Resimulate");
    private final JLabel status = new JLabel(" ");
    private final DualPlotMatrix dual;

    // ── simulated data (updated after each resimulation) ──────────────────────

    private DataSet simulated;

    // ── constructor ───────────────────────────────────────────────────────────

    public NNEstimatorComparePanel(NNEstimatorModel model) {
        super(new BorderLayout(10, 10));

        this.model = Objects.requireNonNull(model, "model");
        this.observed = Objects.requireNonNull(model.getInputData(), "observed");
        this.dag = Objects.requireNonNull(model.getGraph(), "dag");

        int n0 = TMath.max(1, observed.getNumRows());
        this.nSpinner = new JSpinner(new SpinnerNumberModel(n0, 1, 10_000_000, 50));

        this.simulated = model.getSimulatedData();

        add(buildHeader(), BorderLayout.NORTH);
        dual = new DualPlotMatrix(observed, simulated);
        add(dual, BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);

        // Show initial adequacy summary if already available.
        refreshStatus(model.getAdequacyReport(), n0);

        wire();
        setPreferredSize(new Dimension(1200, 780));
    }

    // ── UI builders ───────────────────────────────────────────────────────────

    private JComponent buildHeader() {
        JPanel p = new JPanel(new BorderLayout(8, 8));
        p.setBorder(new TitledBorder("NN Estimator — Observed vs. Resimulated"));

        JLabel instr = new JLabel(
                "<html>"
                        + "<b>What you're seeing:</b> Left is the observed dataset. Right is a resimulation "
                        + "whose joint distribution is learned by training a small neural network for each "
                        + "variable given its parents in the DAG (parameter-agnostic estimation)."
                        + "<br/>This panel is for <b>visual inspection</b>; marginals are shown by default. "
                        + "Use the variable selectors on the right to view pairwise scatter plots."
                        + "</html>");

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        controls.add(new JLabel("Sample size (right):"));
        controls.add(nSpinner);
        controls.add(resimulateButton);

        p.add(instr, BorderLayout.CENTER);
        p.add(controls, BorderLayout.SOUTH);
        return p;
    }

    private JComponent buildFooter() {
        status.setFont(status.getFont().deriveFont(Font.PLAIN, 11f));
        status.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        JPanel p = new JPanel(new BorderLayout());
        p.add(status, BorderLayout.CENTER);
        return p;
    }

    // ── wiring ────────────────────────────────────────────────────────────────

    private void wire() {
        resimulateButton.addActionListener(e -> {
            int n = ((Number) nSpinner.getValue()).intValue();
            resimulateButton.setEnabled(false);
            status.setText("Fitting NN estimator and simulating " + n + " rows…");

            new SwingWorker<DataSet, Void>() {
                @Override
                protected DataSet doInBackground() {
                    model.resimulate(n);
                    return model.getSimulatedData();
                }

                @Override
                protected void done() {
                    try {
                        simulated = get();
                        dual.setRightData(simulated);
                        refreshStatus(model.getAdequacyReport(), n);
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

    /**
     * Updates the status bar with a one-line summary of the adequacy report.
     * Falls back to a plain "done" message if the report is null.
     */
    private void refreshStatus(AdequacyReport report, int n) {
        if (report == null) {
            status.setText("Resimulated with n = " + n + ".");
            return;
        }

        String summary = String.format(
                "n = %d  |  MMD² = %.4f  |  Mean node improvement = %.4f  |  Nodes improved = %.0f%%",
                n,
                report.getMmd2(),
                report.getMeanImprovement(),
                report.getFracImproved() * 100.0);
        status.setText(summary);
    }

    // =========================================================================
    // DualPlotMatrix — shared selectors, two plot matrices side-by-side
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
        private boolean addRegressionLines       = false;
        private boolean removeZeroPointsPerPlot  = false;
        private ScatterPlot.JitterStyle jitterStyle =
                ScatterPlot.JitterStyle.None;
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

        // ── public ───────────────────────────────────────────────────────────

        void setRightData(DataSet newRight) {
            // Snapshot selections by name so they survive a list rebuild.
            Set<String> selRows  = selectedNames(rowSelector);
            Set<String> selCols  = selectedNames(colSelector);
            Set<String> lastRN   = namesForIndices(lastRows);
            Set<String> lastCN   = namesForIndices(lastCols);

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

        // ── private helpers ───────────────────────────────────────────────────

        private Set<String> selectedNames(JList<Node> list) {
            Set<String> names = new LinkedHashSet<>();
            for (Node n : list.getSelectedValuesList()) {
                if (n != null && n.getName() != null) names.add(n.getName());
            }
            return names;
        }

        private Set<String> namesForIndices(int[] indices) {
            Set<String> names = new LinkedHashSet<>();
            for (int idx : indices) {
                if (idx >= 0 && idx < varsArray.length) {
                    Node n = varsArray[idx];
                    if (n != null && n.getName() != null) names.add(n.getName());
                }
            }
            return names;
        }

        private int[] indicesForNames(Set<String> names) {
            if (names == null || names.isEmpty()) return new int[0];
            List<Integer> idxs = new ArrayList<>();
            for (int i = 0; i < varsArray.length; i++) {
                Node n = varsArray[i];
                if (n != null && n.getName() != null && names.contains(n.getName())) {
                    idxs.add(i);
                }
            }
            int[] out = new int[idxs.size()];
            for (int i = 0; i < idxs.size(); i++) out[i] = idxs.get(i);
            return out;
        }

        private void rebuildVarList() {
            Map<String, Node> leftByName = new LinkedHashMap<>();
            for (Node n : left.getVariables()) {
                if (n != null && n.getName() != null) leftByName.put(n.getName(), n);
            }
            Set<String> rightNames = new HashSet<>();
            for (Node n : right.getVariables()) {
                if (n != null && n.getName() != null) rightNames.add(n.getName());
            }
            List<Node> out = new ArrayList<>();
            for (Map.Entry<String, Node> e : leftByName.entrySet()) {
                if (rightNames.contains(e.getKey())) out.add(e.getValue());
            }
            out.sort(NaturalSort.naturalComparator());
            this.vars      = out;
            this.varsArray = out.toArray(new Node[0]);
        }

        // ── layout ────────────────────────────────────────────────────────────

        private JMenuBar buildMenuBar() {
            JMenuBar menuBar = new JMenuBar();
            JMenu settings = new JMenu("Settings");
            menuBar.add(settings);

            JMenuItem addTrendLines = new JCheckBoxMenuItem("Add Trend Lines");
            addTrendLines.setAccelerator(
                    KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK));
            settings.add(addTrendLines);
            addTrendLines.addActionListener(e -> {
                addRegressionLines = !addRegressionLines;
                refreshCharts();
            });

            JMenuItem removeZeroPoints =
                    new JCheckBoxMenuItem("Remove Zero Points Per Plot");
            removeZeroPoints.setAccelerator(
                    KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK));
            settings.add(removeZeroPoints);
            removeZeroPoints.addActionListener(e -> {
                removeZeroPointsPerPlot = !removeZeroPointsPerPlot;
                refreshCharts();
            });

            JMenu binsMenu = new JMenu("Set number of Bins for Histograms");
            ButtonGroup binsGroup = new ButtonGroup();
            for (int i = 2; i <= 30; i++) {
                int _i = i;
                JMenuItem item = new JCheckBoxMenuItem(String.valueOf(i));
                binsGroup.add(item);
                if (i == numBins) item.setSelected(true);
                item.addActionListener(e -> { numBins = _i; refreshCharts(); });
                binsMenu.add(item);
            }
            settings.add(binsMenu);

            JMenu jitterMenu = new JMenu("Jitter Style (Display Only)");
            ButtonGroup jitterGroup = new ButtonGroup();
            JMenuItem j1 = new JCheckBoxMenuItem(ScatterPlot.JitterStyle.Gaussian.toString());
            JMenuItem j2 = new JCheckBoxMenuItem(ScatterPlot.JitterStyle.Uniform.toString());
            JMenuItem j3 = new JCheckBoxMenuItem(ScatterPlot.JitterStyle.None.toString());
            j1.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_U, InputEvent.CTRL_DOWN_MASK));
            j2.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_I, InputEvent.CTRL_DOWN_MASK));
            j3.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));
            jitterGroup.add(j1); jitterGroup.add(j2); jitterGroup.add(j3);
            j3.setSelected(true);
            jitterMenu.add(j1); jitterMenu.add(j2); jitterMenu.add(j3);
            j1.addActionListener(e -> { jitterStyle = ScatterPlot.JitterStyle.Gaussian; refreshCharts(); });
            j2.addActionListener(e -> { jitterStyle = ScatterPlot.JitterStyle.Uniform;  refreshCharts(); });
            j3.addActionListener(e -> { jitterStyle = ScatterPlot.JitterStyle.None;     refreshCharts(); });
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

        // ── chart construction ────────────────────────────────────────────────

        private void refreshCharts() {
            constructPlotMatrix(chartsLeft,  left,  rowSelector, colSelector);
            constructPlotMatrix(chartsRight, right, rowSelector, colSelector);
        }

        private void constructPlotMatrix(JPanel charts,
                                         DataSet dataSet,
                                         JList<Node> rowSel,
                                         JList<Node> colSel) {
            int[] rowIndices = rowSel.getSelectedIndices();
            int[] colIndices = colSel.getSelectedIndices();

            charts.removeAll();
            if (rowIndices.length == 0 || colIndices.length == 0) {
                charts.setLayout(new BorderLayout());
                charts.add(new JLabel("Select at least one row and one column.",
                        SwingConstants.CENTER), BorderLayout.CENTER);
                charts.revalidate();
                charts.repaint();
                return;
            }

            charts.setLayout(new GridLayout(rowIndices.length, colIndices.length));

            for (int rowIndex : rowIndices) {
                for (int colIndex : colIndices) {
                    JPanel cell = rowIndex == colIndex
                            ? buildHistogram(dataSet, rowIndex, rowIndices, colIndices)
                            : buildScatter(dataSet, rowIndex, colIndex, rowIndices, colIndices);
                    addPanelListener(charts, dataSet, rowIndex, colIndex, cell);
                    charts.add(cell);
                }
            }

            charts.revalidate();
            charts.repaint();
        }

        private JPanel buildHistogram(DataSet dataSet, int index,
                                      int[] rowIndices, int[] colIndices) {
            Histogram histogram = new Histogram(
                    dataSet, vars.get(index).getName(), removeZeroPointsPerPlot);
            applyConditioning(histogram);
            if (!(vars.get(index) instanceof DiscreteVariable)) {
                histogram.setNumBins(numBins);
            }
            HistogramPanel panel = new HistogramPanel(
                    histogram, rowIndices.length == 1 && colIndices.length == 1);
            panel.setMinimumSize(new Dimension(10, 10));
            return panel;
        }

        private JPanel buildScatter(DataSet dataSet, int rowIndex, int colIndex,
                                    int[] rowIndices, int[] colIndices) {
            ScatterPlot sp = new ScatterPlot(
                    dataSet,
                    addRegressionLines,
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

        private void applyConditioning(Histogram histogram) {
            for (Node node : conditioningPanelMap.keySet()) {
                if (node instanceof ContinuousVariable var) {
                    VariableConditioningEditor.ContinuousConditioningPanel p =
                            (VariableConditioningEditor.ContinuousConditioningPanel)
                                    conditioningPanelMap.get(var);
                    histogram.addConditioningVariable(var.getName(), p.getLow(), p.getHigh());
                } else if (node instanceof DiscreteVariable var) {
                    VariableConditioningEditor.DiscreteConditioningPanel p =
                            (VariableConditioningEditor.DiscreteConditioningPanel)
                                    conditioningPanelMap.get(var);
                    histogram.addConditioningVariable(var.getName(), p.getIndex());
                }
            }
        }

        private void applyConditioning(ScatterPlot sp) {
            for (Node node : conditioningPanelMap.keySet()) {
                if (node instanceof ContinuousVariable var) {
                    VariableConditioningEditor.ContinuousConditioningPanel p =
                            (VariableConditioningEditor.ContinuousConditioningPanel)
                                    conditioningPanelMap.get(var);
                    sp.addConditioningVariable(var.getName(), p.getLow(), p.getHigh());
                } else if (node instanceof DiscreteVariable var) {
                    VariableConditioningEditor.DiscreteConditioningPanel p =
                            (VariableConditioningEditor.DiscreteConditioningPanel)
                                    conditioningPanelMap.get(var);
                    sp.addConditioningVariable(var.getName(), p.getIndex());
                }
            }
        }

        private void addPanelListener(JPanel charts, DataSet dataSet,
                                      int rowIndex, int colIndex, JPanel panel) {
            panel.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent e) {
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
