package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.data.Histogram;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetradapp.model.DagFactorizationCompare;
import org.apache.commons.math3.util.FastMath;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.List;

/**
 * Compare-box component: given (DataSet D, DAG G) where D contains all variables in G,
 * resimulates data using the DAG factorization (via TrainedDagSimulatorGNM) and shows:
 * <p>
 * left:  original dataset D
 * right: resimulated dataset D~ (factorized by G)
 * <p>
 * The intent is *visual inspection* of whether the marginal/joint patterns in D are
 * plausibly compatible with the DAG factorization induced by G.
 * <p>
 * Optional/future: numerical distribution-equivalence metrics between left/right.
 * <p>
 * Drop-in notes:
 * - You MUST implement simulateWithGNM(...) to call your TrainedDagSimulatorGNM exactly
 * the way the Simulation editor does (3–10 lines).
 * - Everything else is self-contained and uses your existing histogram/scatter/conditioning UI.
 */
public final class DagFactorizationComparePanel extends JPanel {

    private final DataSet observed;
    private final Graph dag;

    private final JSpinner nSpinner;
    private final JButton resimulateButton = new JButton("Resimulate");
    private final JLabel status = new JLabel(" ");

    private final DualPlotMatrix dual;
    private final DagFactorizationCompare model;

    // last simulated (right)
    private DataSet simulated;

    public DagFactorizationComparePanel(DagFactorizationCompare model) {
        super(new BorderLayout(10, 10));

        this.observed = Objects.requireNonNull(model.getInputData(), "observed");
        this.dag = Objects.requireNonNull(model.getGraph(), "dag");
        this.model = model;

        int n0 = FastMath.max(1, observed.getNumRows());
        this.nSpinner = new JSpinner(new SpinnerNumberModel(n0, 1, 10_000_000, 50));

        // initial simulation (same n as observed)
        this.simulated = model.getSimulatedData();

        // UI
        add(buildHeader(), BorderLayout.NORTH);

        dual = new DualPlotMatrix(observed, simulated);
        add(dual, BorderLayout.CENTER);

        add(buildFooter(), BorderLayout.SOUTH);

        wire();
        setPreferredSize(new Dimension(1200, 750));
    }

    private JComponent buildHeader() {
        JPanel p = new JPanel(new BorderLayout(8, 8));
        p.setBorder(new TitledBorder("DAG Factorization Marginals"));

        String msg =
                "<html>"
                        + "<b>What you’re seeing:</b> Left is the given dataset. Right is a resimulation whose joint distribution "
                        + "is built by <i>factoring according to the DAG</i> (trained on the given data). "
                        + "<br/>This is meant for <b>visual inspection</b> (not a formal acceptance test) "
                        + "and shows marginals only; the data may have conditional distributions that are helpful to know."
                        + "</html>";

        JLabel instr = new JLabel(msg);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        controls.add(new JLabel("Sample size (right):"));
        controls.add(nSpinner);
        controls.add(resimulateButton);

        p.add(instr, BorderLayout.CENTER);
        p.add(controls, BorderLayout.SOUTH);
        return p;
    }

    private JComponent buildFooter() {
        JPanel p = new JPanel(new BorderLayout());
        p.add(status, BorderLayout.CENTER);
        return p;
    }

    private void wire() {
        resimulateButton.addActionListener(e -> {
            resimulateButton.setEnabled(false);
            status.setText("Resimulating...");
            SwingUtilities.invokeLater(() -> {
                try {
                    int n = ((Number) nSpinner.getValue()).intValue();
                    model.resimulate(n);
                    simulated = model.getSimulatedData();
                    dual.setRightData(simulated);
                    status.setText("Resimulated with n = " + n + ".");
                } catch (Throwable t) {
                    status.setText("Resimulation failed: " + (t.getMessage() != null ? t.getMessage() : t));
                } finally {
                    resimulateButton.setEnabled(true);
                }
            });

            firePropertyChange("modelChanged", null, null);
        });
    }

    // =============================================================================================
    // DualPlotMatrix: one shared control column, two plot matrices (left/right) side-by-side.
    // Uses the same underlying plot logic as your PlotMatrix, but externalizes the selectors/settings.
    // =============================================================================================

    private static final class DualPlotMatrix extends JPanel {

        private final JList<Node> rowSelector;
        private final JList<Node> colSelector;
        private final JPanel chartsLeft = new JPanel();
        private final JPanel chartsRight = new JPanel();
        private DataSet left;
        private DataSet right;
        private List<Node> vars;       // nodes for selection list (names match both data sets)
        private Node[] varsArray;
        private int numBins = 9;
        private boolean addRegressionLines = false;
        private boolean removeZeroPointsPerPlot = false;
        private ScatterPlot.JitterStyle jitterStyle = ScatterPlot.JitterStyle.None;
        private Map<Node, VariableConditioningEditor.ConditioningPanel> conditioningPanelMap = new HashMap<>();
        private int[] lastRows = new int[]{0};
        private int[] lastCols = new int[]{0};

        DualPlotMatrix(DataSet left, DataSet right) {
            super(new BorderLayout(8, 8));
            this.left = Objects.requireNonNull(left, "left");
            this.right = Objects.requireNonNull(right, "right");

            // shared var list by name intersection (stable)
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
            add(buildCenter(), BorderLayout.CENTER);
            refreshCharts();
        }

        void setRightData(DataSet newRight) {
            // --- snapshot selection by NAME (stable across list rebuilds) ---
            Set<String> selectedRowNames = new LinkedHashSet<>();
            for (Node n : rowSelector.getSelectedValuesList()) {
                if (n != null && n.getName() != null) selectedRowNames.add(n.getName());
            }

            Set<String> selectedColNames = new LinkedHashSet<>();
            for (Node n : colSelector.getSelectedValuesList()) {
                if (n != null && n.getName() != null) selectedColNames.add(n.getName());
            }

            // snapshot click-to-zoom memory by NAME too (so it survives list changes)
            Set<String> lastRowNames = new LinkedHashSet<>();
            for (int idx : lastRows) {
                if (idx >= 0 && idx < varsArray.length) {
                    Node n = varsArray[idx];
                    if (n != null && n.getName() != null) lastRowNames.add(n.getName());
                }
            }

            Set<String> lastColNames = new LinkedHashSet<>();
            for (int idx : lastCols) {
                if (idx >= 0 && idx < varsArray.length) {
                    Node n = varsArray[idx];
                    if (n != null && n.getName() != null) lastColNames.add(n.getName());
                }
            }

            // --- swap data + rebuild variable list ---
            this.right = Objects.requireNonNull(newRight, "newRight");
            rebuildVarList();

            // update lists
            rowSelector.setListData(varsArray);
            colSelector.setListData(varsArray);

            if (varsArray.length == 0) {
                lastRows = new int[]{0};
                lastCols = new int[]{0};
                refreshCharts();
                return;
            }

            // --- restore selections by NAME ---
            rowSelector.setSelectedIndices(indicesForNames(selectedRowNames));
            colSelector.setSelectedIndices(indicesForNames(selectedColNames));

            // if nothing restored, keep a reasonable default
            if (rowSelector.getSelectedIndices().length == 0) rowSelector.setSelectedIndex(0);
            if (colSelector.getSelectedIndices().length == 0) colSelector.setSelectedIndex(0);

            // --- restore click-to-zoom memory by NAME (best-effort) ---
            int[] restoredLastRows = indicesForNames(lastRowNames);
            int[] restoredLastCols = indicesForNames(lastColNames);

            // if restoration fails, fall back to current selection
            lastRows = restoredLastRows.length > 0 ? restoredLastRows : rowSelector.getSelectedIndices();
            lastCols = restoredLastCols.length > 0 ? restoredLastCols : colSelector.getSelectedIndices();

            refreshCharts();
        }

        private int[] indicesForNames(Set<String> names) {
            if (names == null || names.isEmpty() || varsArray.length == 0) return new int[0];

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
            // intersection by name; preserve left’s ordering if possible
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

            // stable sort consistent with your PlotMatrix (Collections.sort(nodes))
            Collections.sort(out);

            this.vars = out;
            this.varsArray = out.toArray(new Node[0]);
        }

        private JMenuBar buildMenuBar() {
            JMenuBar menuBar = new JMenuBar();
            JMenu settings = new JMenu("Settings");
            menuBar.add(settings);

            JMenuItem addTrendLines = new JCheckBoxMenuItem("Add Trend Lines");
            addTrendLines.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK));
            addTrendLines.setSelected(false);
            settings.add(addTrendLines);

            JMenuItem removeZeroPoints = new JCheckBoxMenuItem("Remove Zero Points Per Plot");
            removeZeroPoints.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK));
            removeZeroPoints.setSelected(false);
            settings.add(removeZeroPoints);

            addTrendLines.addActionListener(e -> {
                addRegressionLines = !addRegressionLines;
                refreshCharts();
            });

            removeZeroPoints.addActionListener(e -> {
                removeZeroPointsPerPlot = !removeZeroPointsPerPlot;
                refreshCharts();
            });

            JMenu binsMenu = new JMenu("Set number of Bins for Histograms");
            ButtonGroup binsGroup = new ButtonGroup();
            for (int i = 2; i <= 30; i++) {
                int _i = i;
                JMenuItem comp = new JCheckBoxMenuItem(String.valueOf(i));
                binsMenu.add(comp);
                binsGroup.add(comp);
                if (i == numBins) comp.setSelected(true);

                comp.addActionListener(e -> {
                    numBins = _i;
                    refreshCharts();
                });
            }
            settings.add(binsMenu);

            JMenu jitterMenu = new JMenu("Jitter Style (Display Only)");

            final JMenuItem j1 = new JCheckBoxMenuItem(ScatterPlot.JitterStyle.Gaussian.toString());
            final JMenuItem j2 = new JCheckBoxMenuItem(ScatterPlot.JitterStyle.Uniform.toString());
            final JMenuItem j3 = new JCheckBoxMenuItem(ScatterPlot.JitterStyle.None.toString());

            j1.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_U, InputEvent.CTRL_DOWN_MASK));
            j2.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_I, InputEvent.CTRL_DOWN_MASK));
            j3.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));

            ButtonGroup jitterGroup = new ButtonGroup();
            jitterGroup.add(j1);
            jitterGroup.add(j2);
            jitterGroup.add(j3);

            j3.setSelected(true);

            jitterMenu.add(j1);
            jitterMenu.add(j2);
            jitterMenu.add(j3);

            j1.addActionListener(e -> {
                jitterStyle = ScatterPlot.JitterStyle.Gaussian;
                refreshCharts();
            });
            j2.addActionListener(e -> {
                jitterStyle = ScatterPlot.JitterStyle.Uniform;
                refreshCharts();
            });
            j3.addActionListener(e -> {
                jitterStyle = ScatterPlot.JitterStyle.None;
                refreshCharts();
            });

            settings.add(jitterMenu);

            JMenuItem editConditioning = new JMenuItem("Edit Conditioning Variables...");
            editConditioning.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_P, InputEvent.CTRL_DOWN_MASK));
            editConditioning.addActionListener(e -> {
                // Conditioning editor needs a dataset for variable types/ranges.
                // We use the LEFT dataset as canonical for the variable metadata.
                VariableConditioningEditor conditioningEditor
                        = new VariableConditioningEditor(left, conditioningPanelMap);
                conditioningEditor.setPreferredSize(new Dimension(320, 320));
                JOptionPane.showMessageDialog(this, new JScrollPane(conditioningEditor));
                conditioningPanelMap = conditioningEditor.getConditioningPanelMap();
                refreshCharts();
            });
            settings.add(editConditioning);

            return menuBar;
        }

        private JComponent buildCenter() {
            // left chart scroll
            JScrollPane leftScroll = new JScrollPane(chartsLeft);
            leftScroll.setPreferredSize(new Dimension(520, 520));
            leftScroll.setBorder(new TitledBorder("Observed"));

            // right chart scroll
            JScrollPane rightScroll = new JScrollPane(chartsRight);
            rightScroll.setPreferredSize(new Dimension(520, 520));
            rightScroll.setBorder(new TitledBorder("Resimulated"));

            JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftScroll, rightScroll);
            split.setResizeWeight(0.5);

            // shared selectors (one column) on right
            Box selectors = Box.createVerticalBox();
            selectors.add(new JLabel("Rows"));
            selectors.add(new JScrollPane(rowSelector));
            selectors.add(Box.createVerticalStrut(6));
            selectors.add(new JLabel("Cols"));
            selectors.add(new JScrollPane(colSelector));
            selectors.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
            selectors.setPreferredSize(new Dimension(220, 520));

            JPanel center = new JPanel(new BorderLayout());
            center.add(split, BorderLayout.CENTER);
            center.add(selectors, BorderLayout.EAST);
            return center;
        }

        private void refreshCharts() {
            constructPlotMatrix(chartsLeft, left, vars, rowSelector, colSelector, removeZeroPointsPerPlot);
            constructPlotMatrix(chartsRight, right, vars, rowSelector, colSelector, removeZeroPointsPerPlot);
        }

        // Copied/adapted from your PlotMatrix with minimal changes:
        private void constructPlotMatrix(JPanel charts,
                                         DataSet dataSet,
                                         List<Node> nodes,
                                         JList<Node> rowSelector,
                                         JList<Node> colSelector,
                                         boolean removeZeroPointsPerPlot) {

            int[] rowIndices = rowSelector.getSelectedIndices();
            int[] colIndices = colSelector.getSelectedIndices();

            charts.removeAll();
            if (rowIndices.length == 0 || colIndices.length == 0) {
                charts.setLayout(new BorderLayout());
                charts.add(new JLabel("Select at least one row and one column.", SwingConstants.CENTER),
                        BorderLayout.CENTER);
                charts.revalidate();
                charts.repaint();
                return;
            }

            charts.setLayout(new GridLayout(rowIndices.length, colIndices.length));

            for (int rowIndex : rowIndices) {
                for (int colIndex : colIndices) {

                    if (rowIndex == colIndex) {
                        Histogram histogram = new Histogram(dataSet, nodes.get(rowIndex).getName(), removeZeroPointsPerPlot);

                        for (Node node : conditioningPanelMap.keySet()) {
                            if (node instanceof ContinuousVariable var) {
                                VariableConditioningEditor.ContinuousConditioningPanel panel
                                        = (VariableConditioningEditor.ContinuousConditioningPanel)
                                        conditioningPanelMap.get(var);
                                histogram.addConditioningVariable(var.getName(), panel.getLow(), panel.getHigh());
                            } else if (node instanceof DiscreteVariable var) {
                                VariableConditioningEditor.DiscreteConditioningPanel panel
                                        = (VariableConditioningEditor.DiscreteConditioningPanel)
                                        conditioningPanelMap.get(var);
                                histogram.addConditioningVariable(var.getName(), panel.getIndex());
                            }
                        }

                        if (!(nodes.get(rowIndex) instanceof DiscreteVariable)) {
                            histogram.setNumBins(numBins);
                        }

                        HistogramPanel panel = new HistogramPanel(histogram,
                                rowIndices.length == 1 && colIndices.length == 1);
                        panel.setMinimumSize(new Dimension(10, 10));

                        addPanelListener(charts, dataSet, nodes, rowIndex, colIndex, panel);
                        charts.add(panel);

                    } else {
                        ScatterPlot scatterPlot = new ScatterPlot(
                                dataSet,
                                addRegressionLines,
                                nodes.get(rowIndex).getName(),
                                nodes.get(colIndex).getName(),
                                removeZeroPointsPerPlot
                        );

                        for (Node node : conditioningPanelMap.keySet()) {
                            if (node instanceof ContinuousVariable var) {
                                VariableConditioningEditor.ContinuousConditioningPanel panel
                                        = (VariableConditioningEditor.ContinuousConditioningPanel)
                                        conditioningPanelMap.get(var);
                                scatterPlot.addConditioningVariable(var.getName(), panel.getLow(), panel.getHigh());
                            } else if (node instanceof DiscreteVariable var) {
                                VariableConditioningEditor.DiscreteConditioningPanel panel
                                        = (VariableConditioningEditor.DiscreteConditioningPanel)
                                        conditioningPanelMap.get(var);
                                scatterPlot.addConditioningVariable(var.getName(), panel.getIndex());
                            }
                        }

                        scatterPlot.setJitterStyle(jitterStyle);

                        ScatterplotPanel panel = new ScatterplotPanel(scatterPlot, removeZeroPointsPerPlot);
                        panel.setDrawAxes(rowIndices.length == 1 && colIndices.length == 1);
                        panel.setMinimumSize(new Dimension(10, 10));

                        int pointSize = 5;
                        if (rowIndices.length > 2 || colIndices.length > 2) pointSize = 4;
                        if (rowIndices.length > 3 || colIndices.length > 3) pointSize = 3;
                        if (rowIndices.length > 5 || colIndices.length > 5) pointSize = 2;
                        panel.setPointSize(pointSize);

                        addPanelListener(charts, dataSet, nodes, rowIndex, colIndex, panel);
                        charts.add(panel);
                    }
                }
            }

            charts.revalidate();
            charts.repaint();
        }

        private void addPanelListener(JPanel charts, DataSet dataSet, List<Node> nodes,
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
                        refreshCharts();
                    } else {
                        lastRows = rowSelector.getSelectedIndices();
                        lastCols = colSelector.getSelectedIndices();
                        rowSelector.setSelectedIndex(rowIndex);
                        colSelector.setSelectedIndex(colIndex);
                        refreshCharts();
                    }
                }
            });
        }
    }
}