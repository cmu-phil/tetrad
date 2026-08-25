/// ////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.search.ConditioningSetType;
import edu.cmu.tetrad.search.test.CachedIndependenceQueries;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.util.*;
import edu.cmu.tetradapp.model.VertexCheckIndTestModel;
import edu.cmu.tetradapp.ui.PaddingPanel;
import edu.cmu.tetradapp.ui.model.IndependenceTestModel;
import edu.cmu.tetradapp.ui.model.IndependenceTestModels;
import edu.cmu.tetradapp.util.*;
import edu.cmu.tetradapp.workbench.GraphWorkbench;
import edu.cmu.tetradapp.util.ExperimentalToggle;

import javax.swing.*;
import javax.swing.border.BevelBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.lang.reflect.InvocationTargetException;
import java.text.NumberFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

import static edu.cmu.tetradapp.util.ParameterComponents.toArray;

/**
 * Swing editor for the Vertex Checker.
 *
 * <p>The right-hand side uses a {@link JTabbedPane} with two tabs:
 * <ul>
 *   <li><b>Check</b>: "Tests implied for selected node" table plus the
 *       p-value histogram.</li>
 *   <li><b>Repair</b>: an embedded {@link VertexRepairPanelGlobalRepair}
 *       for the currently selected node, rebuilt each time the user selects
 *       the tab so it always reflects the current node and graph state.</li>
 * </ul>
 *
 * <p>Graph changes made in the Repair tab flow through the model's
 * property-change listener, keeping the overview table live with no
 * card-switching logic required.
 */
public class VertexCheckEditor extends JPanel {

    private static final int TAB_CHECK = 0;
    private static final int TAB_REPAIR = 1;
    private static final int COL_PV = 3;   // overview p-value column (label follows selected method)

    private final VertexCheckIndTestModel model;
    private final NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

    private final JComboBox<IndependenceTestModel> indTestCombo = new JComboBox<>();
    /**
     * Local switch for listing experimental tests in this editor. Added 2026-8-24.
     */
    private final ExperimentalToggle experimentalToggle = new ExperimentalToggle(this::refreshTestList);
    private final JComboBox<ConditioningSetType> conditioningCombo = new JComboBox<>();
    private final JCheckBox verbose = new JCheckBox("Verbose");
    private final JButton showIndepsForRow = new JButton("Independencies");
    private final JButton undoGraphButton = new JButton("Undo");
    private final JButton showGraphButton = new JButton("Graph");
    private final Deque<Graph> graphHistory = new ArrayDeque<>();
    private final JLabel modelPLabel = new JLabel("Model Uniformity P: (not computed)");
    private final JLabel modelNpLabel = new JLabel("# p-values (not computed): -");

    private final CachedIndependenceQueries Q;
    private final JPanel histogramPanel = new JPanel(new BorderLayout());
    private final JComboBox<String> modelUniformityTest;
    private final Knowledge knowledge;
    private final AtomicBoolean cancelRequested = new AtomicBoolean(false);
    private JTable overviewTable;
    private JTable factsTable;
    private AbstractTableModel overviewModel;
    private AbstractTableModel factsModel;
    private IndependenceWrapper independenceWrapper;
    private boolean initializing;
    private boolean applyingGraphProgrammatically = false;
    //    private volatile boolean runningAll = false;
    private SwingWorker<Void, Void> activeWorker = null;
    private Runnable pendingTask = null;
    /**
     * Right-hand tabbed pane ("Check" | "Repair").
     */
    private JTabbedPane rightTabs;
    /**
     * Wrapper panel inside the Repair tab — we swap a fresh
     * {@link VertexRepairPanelGlobalRepair} into this whenever the tab is selected.
     */
    private JPanel repairTabContent;
    /**
     * Live repair panel, or null when the Check tab is showing.
     */
    private VertexRepairPanelGlobalRepair repairPanel;

    // =========================================================================
    // Construction
    // =========================================================================
    private boolean useAndersonDarling = false;

    // =========================================================================
    // Public API
    // =========================================================================
    private JDialog progressDialog;   // new field

    public VertexCheckEditor(VertexCheckIndTestModel model) {
        if (model == null) throw new NullPointerException("Expecting a model.");
        this.model = model;
        Q = model.getCachedQueries();

        this.knowledge = model.getKnowledge() == null
                ? new Knowledge()
                : model.getKnowledge().copy();

        if (this.knowledge.isViolatedBy(model.getGraph()))
            throw new IllegalArgumentException("Knowledge conflicts with current graph structure.");

        // The wild bootstrap needs row data to residualize; with a covariance matrix it can
        // only ever return NaN, so it is not offered. If a session saved WB mode and the data
        // is now a covariance matrix, fall back to KS rather than presenting NaN columns.
        boolean rowData = model.getDataModel() instanceof DataSet;
        modelUniformityTest = new JComboBox<>(rowData
                ? new String[]{"Use KS", "Use AD", "Use WB"}
                : new String[]{"Use KS", "Use AD"});
        if (!rowData && VertexCheckIndTestModel.WILD_BOOTSTRAP.equals(model.getUniformityTest())) {
            model.setUniformityTest(VertexCheckIndTestModel.KOLMOGOROV_SMIRNOFF);
        }
        modelUniformityTest.setSelectedIndex(uniformityTestIndex(model.getUniformityTest()));
        modelUniformityTest.addActionListener(e -> {
            if (initializing) return;
            model.setUniformityTest(uniformityTestForIndex(modelUniformityTest.getSelectedIndex()));
            if (repairPanel != null) {
                repairPanel.setUseAndersonDarling(model.isUseAndersonDarling());
                repairPanel.setUseWildBootstrap(
                        VertexCheckIndTestModel.WILD_BOOTSTRAP.equals(model.getUniformityTest()));
            }
            runAllAndRefresh(null, null);
        });

        setPreferredSize(new Dimension(1100, 650));
        setLayout(new BorderLayout(10, 10));
        setBorder(new EmptyBorder(8, 8, 8, 8));

        initializing = true;
        buildControls();
        buildMainPanels();
        refreshTestList();
        applySavedSetType();
        setTestFromCombo();
        undoGraphButton.addActionListener(e -> undoGraph());
        showGraphButton.addActionListener(e -> showGraphDialog());
        updateUndoButtonEnabled();
        initializing = false;

        model.addPropertyChangeListener(evt -> {
            if (!VertexCheckIndTestModel.PROP_GRAPH.equals(evt.getPropertyName())) return;
            if (!applyingGraphProgrammatically) {
                Object oldV = evt.getOldValue();
                if (oldV instanceof Graph oldG) {
                    graphHistory.push(safeCopy(oldG));

                    SwingUtilities.invokeLater(() -> {
                        graphHistory.push(safeCopy(oldG));
                        updateUndoButtonEnabled();
                    });
                }
            }
            SwingUtilities.invokeLater(this::onModelGraphChanged);
        });

        runAllAndRefresh(null, null);
    }

    private static DataType guessDataType(DataModel dm) {
        if (dm instanceof DataSet ds) {
            boolean cont = ds.getVariables().stream().anyMatch(v -> v instanceof ContinuousVariable);
            boolean disc = ds.getVariables().stream().anyMatch(v -> v instanceof DiscreteVariable);
            if (cont && disc) return DataType.Mixed;
            if (disc) return DataType.Discrete;
            return DataType.Continuous;
        }
        return DataType.Continuous;
    }

    private static String factString(IndependenceFact fact) {
        List<Node> z = new ArrayList<>(fact.getZ());
        String zStr = z.stream().map(Node::getName).sorted().collect(Collectors.joining(", "));
        if (z.isEmpty())
            return "Ind(" + fact.getX().getName() + ", " + fact.getY().getName() + ")";
        return "Ind(" + fact.getX().getName() + ", " + fact.getY().getName() + " | " + zStr + ")";
    }

    /**
     * Combo index (0=KS, 1=AD, 2=WB) for a model uniformity-test mode string.
     */
    private static int uniformityTestIndex(String mode) {
        if (VertexCheckIndTestModel.ANDERSON_DARLING.equals(mode)) return 1;
        if (VertexCheckIndTestModel.WILD_BOOTSTRAP.equals(mode)) return 2;
        return 0;
    }

    // =========================================================================
    // UI construction
    // =========================================================================

    /**
     * Model uniformity-test mode string for a combo index (0=KS, 1=AD, 2=WB).
     */
    private static String uniformityTestForIndex(int idx) {
        return switch (idx) {
            case 1 -> VertexCheckIndTestModel.ANDERSON_DARLING;
            case 2 -> VertexCheckIndTestModel.WILD_BOOTSTRAP;
            default -> VertexCheckIndTestModel.KOLMOGOROV_SMIRNOFF;
        };
    }

    private static JPanel createParamsPanel(IndependenceWrapper iw, Parameters params) {
        return createParamsPanel(new HashSet<>(iw.getParameters()), params);
    }

    // =========================================================================
    // Repair tab lifecycle
    // =========================================================================

    public static JPanel createParamsPanel(Set<String> params, Parameters parameters) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Parameters"));
        Box paramsBox = Box.createVerticalBox();
        Box[] boxes = toArray(createParameterComponents(params, parameters));
        for (int i = 0; i < boxes.length - 1; i++) {
            paramsBox.add(boxes[i]);
            paramsBox.add(Box.createVerticalStrut(10));
        }
        paramsBox.add(boxes[boxes.length - 1]);
        panel.add(new PaddingPanel(paramsBox), BorderLayout.CENTER);
        return panel;
    }

    private static Map<String, Box> createParameterComponents(Set<String> params, Parameters parameters) {
        ParamDescriptions pd = ParamDescriptions.getInstance();
        return params.stream().collect(Collectors.toMap(
                Function.identity(),
                e -> createParameterComponent(e, parameters, pd.get(e)),
                (u, v) -> {
                    throw new IllegalStateException("Duplicate key: " + u);
                },
                TreeMap::new));
    }

    // =========================================================================
    // Overview selection
    // =========================================================================

    private static Box createParameterComponent(String parameter, Parameters parameters,
                                                ParamDescription pd) {
        JComponent comp;
        Object dv = pd.getDefaultValue();
        if (dv instanceof Double) comp = getDoubleField(parameter, parameters, (Double) dv,
                pd.getLowerBoundDouble(), pd.getUpperBoundDouble());
        else if (dv instanceof Integer) comp = getIntTextField(parameter, parameters, (Integer) dv,
                pd.getLowerBoundInt(), pd.getUpperBoundInt());
        else if (dv instanceof Long) comp = getLongTextField(parameter, parameters, (Long) dv,
                pd.getLowerBoundLong(), pd.getUpperBoundLong());
        else if (dv instanceof Boolean) comp = getBooleanSelectionBox(parameter, parameters, (Boolean) dv);
        else if (dv instanceof String) comp = getStringField(parameter, parameters, (String) dv);
        else throw new IllegalArgumentException("Unexpected type: " + dv.getClass());

        Box row = Box.createHorizontalBox();
        JLabel label = new JLabel(pd.getShortDescription());
        if (pd.getLongDescription() != null) label.setToolTipText(pd.getLongDescription());
        row.add(label);
        row.add(Box.createHorizontalGlue());
        row.add(comp);
        return row;
    }

    // =========================================================================
    // Detail refresh (Check tab only)
    // =========================================================================

    private static DoubleTextField getDoubleField(String p, Parameters ps,
                                                  double dv, double lo, double hi) {
        return ParameterComponents.getDoubleField(p, ps, dv, lo, hi);
    }

    private static IntTextField getIntTextField(String p, Parameters ps,
                                                int dv, double lo, double hi) {
        return ParameterComponents.getIntTextField(p, ps, dv, lo, hi);
    }

    private static LongTextField getLongTextField(String p, Parameters ps,
                                                  long dv, long lo, long hi) {
        LongTextField f = new LongTextField(ps.getLong(p, dv), 8);
        f.setFilter((value, old) -> {
            if (value >= lo && value <= hi) {
                ps.set(p, value);
                return value;
            }
            return old;
        });
        return f;
    }

    // =========================================================================
    // Graph-change handling
    // =========================================================================

    private static Box getBooleanSelectionBox(String p, Parameters ps, boolean dv) {
        Box box = Box.createHorizontalBox();
        JRadioButton yes = new JRadioButton("Yes");
        JRadioButton no = new JRadioButton("No");
        new ButtonGroup() {{
            add(yes);
            add(no);
        }};
        if (ps.getBoolean(p, dv)) yes.setSelected(true);
        else no.setSelected(true);
        box.add(yes);
        box.add(no);
        yes.addActionListener(e -> {
            if (yes.isSelected()) ps.set(p, true);
        });
        no.addActionListener(e -> {
            if (no.isSelected()) ps.set(p, false);
        });
        return box;
    }

    // =========================================================================
    // runAllAndRefresh
    // =========================================================================

    private static StringTextField getStringField(String p, Parameters ps, String dv) {
        return PathsAction.getStringField(p, ps, dv);
    }

    // =========================================================================
    // Misc UI helpers
    // =========================================================================

    private static String factKey(IndependenceFact f) {
        if (f == null || f.getX() == null || f.getY() == null) return UUID.randomUUID().toString();

        String a = f.getX().getName();
        String b = f.getY().getName();
        if (a == null) a = "";
        if (b == null) b = "";

        if (a.compareTo(b) > 0) {
            String t = a;
            a = b;
            b = t;
        }

        List<String> z = new ArrayList<>();
        for (Node n : f.getZ()) {
            if (n != null && n.getName() != null) z.add(n.getName());
        }
        z.sort(NaturalSort.naturalComparator());

        return a + "|" + b + "|" + String.join(",", z);
    }

    public VertexCheckIndTestModel getIndTestModel() {
        return model;
    }

    public CachedIndependenceQueries getCachedQueries() {
        return Q;
    }

    public Node getSelectedVertex() {
        List<Node> nodes = model.getGraph().getNodes();
        String selectedName = getSelectedVertexName();
        for (Node node : nodes)
            if (node.getName().equals(selectedName)) return node;
        return model.getGraph().getNodes().getFirst();
    }

    private void buildControls() {
        Box controls = Box.createHorizontalBox();
        controls.add(new JLabel("Independence Test:"));
        indTestCombo.setPreferredSize(new Dimension(280, 24));
        controls.add(indTestCombo);
        controls.add(experimentalToggle);

        JButton paramsButton = new JButton("Params");
        controls.add(paramsButton);
        controls.add(new JLabel("Conditioning Sets:"));

        for (ConditioningSetType type : ConditioningSetType.values()) {
            conditioningCombo.addItem(type);
        }

        controls.add(conditioningCombo);
        controls.add(verbose);
        controls.add(modelUniformityTest);
        controls.add(Box.createHorizontalGlue());

        Box layout = Box.createVerticalBox();
        Box controlBox = Box.createHorizontalBox();
        controlBox.add(Box.createHorizontalGlue());
        controlBox.add(controls);
        controlBox.add(Box.createHorizontalGlue());
        layout.add(controlBox);
        add(layout, BorderLayout.NORTH);

        indTestCombo.addActionListener(e -> {
            if (initializing) return;
            setTestFromCombo();
            resetResultsUI();
        });
        conditioningCombo.addActionListener(e -> {
            if (initializing) return;
            model.setConditioningSetType((ConditioningSetType) conditioningCombo.getSelectedItem());
            resetResultsUI();
        });
        verbose.addActionListener(e -> model.setVerbose(verbose.isSelected()));
        paramsButton.addActionListener(e -> {
            if (independenceWrapper == null) {
                JOptionPane.showMessageDialog(this, "Choose an independence test first.");
                return;
            }
            new JOptionPane(createParamsPanel(independenceWrapper, model.getParameters()),
                    JOptionPane.PLAIN_MESSAGE)
                    .createDialog(this, "Set Parameters").setVisible(true);
            setTestFromCombo();
            resetResultsUI();
        });
    }

    private void buildMainPanels() {

        // ---- Overview table ----
        overviewModel = new AbstractTableModel() {
            private final String[] cols =
                    {"Vertex", "CS", "#p", "PV", "Fish", "Bin", "frac≤q", "min", "med"};

            @Override
            public int getRowCount() {
                return model.getVertexNames().size();
            }

            @Override
            public int getColumnCount() {
                return cols.length;
            }

            @Override
            public String getColumnName(int col) {
                return cols[col];
            }

            @Override
            public Object getValueAt(int rowIndex, int columnIndex) {
                String v = model.getVertexNames().get(rowIndex);
                VertexCheckIndTestModel.VertexSummary s = model.getSummary(v);
                return switch (columnIndex) {
                    case 0 -> v;
                    case 1 -> {
                        int min = model.getMinConditioningSetSizeFast(v);
                        int max = model.getMaxConditioningSetSizeFast(v);
                        if (min < 0 || max < 0) yield "";
                        yield (min == max) ? String.valueOf(min) : min + "-" + max;
                    }
                    case 2 -> (s == null ? "" : s.numPValuesUsed());
                    case 3 -> (s == null ? "" : fmt(model.selectedVertexP(s)));
                    case 4 -> (s == null ? "" : fmt(s.fishP()));
                    case 5 -> (s == null ? "" : fmt(s.binP()));
                    case 6 -> (s == null ? "" : fmt(s.fractionReject()));
                    case 7 -> (s == null ? "" : fmt(s.minP()));
                    case 8 -> (s == null ? "" : fmt(s.medianP()));
                    default -> "";
                };
            }
        };

        overviewTable = new JTable(overviewModel);
        overviewTable.setTransferHandler(new DefaultTableTransferHandler(0));
        TableColumnModel ocm = overviewTable.getColumnModel();
        ocm.getColumn(0).setPreferredWidth(80);
        ocm.getColumn(1).setPreferredWidth(45);
        ocm.getColumn(2).setPreferredWidth(45);
        for (int c : new int[]{3, 4, 5, 6, 7, 8}) ocm.getColumn(c).setPreferredWidth(70);
        overviewTable.setRowSorter(new TableRowSorter<>(overviewModel));
        overviewTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        overviewTable.getSelectionModel().addListSelectionListener(this::overviewSelectionChanged);

        JScrollPane overviewScroll = new JScrollPane(overviewTable);
        overviewScroll.setPreferredSize(new Dimension(520, 500));

        JPanel modelPanel = new JPanel(new GridLayout(0, 1, 0, 2));
        modelPanel.setBorder(BorderFactory.createTitledBorder("Model diagnostics"));
        modelPanel.add(modelNpLabel);
        modelPanel.add(modelPLabel);

        JPanel left = new JPanel(new BorderLayout(6, 6));
        left.add(overviewScroll, BorderLayout.CENTER);
        left.add(modelPanel, BorderLayout.SOUTH);

        // ---- Facts table ----
        factsModel = new AbstractTableModel() {
            private final String[] cols = {"#", "Fact", "Result", "p-value"};

            @Override
            public int getRowCount() {
                String v = getSelectedVertexName();
                return (v == null) ? 0 : model.getResultsForVertex(v).size();
            }

            @Override
            public int getColumnCount() {
                return cols.length;
            }

            @Override
            public String getColumnName(int col) {
                return cols[col];
            }

            @Override
            public Object getValueAt(int rowIndex, int columnIndex) {
                String v = getSelectedVertexName();
                if (v == null) return "";
                List<IndependenceResult> rs = model.getResultsForVertex(v);
                if (rowIndex < 0 || rowIndex >= rs.size()) return "";
                IndependenceResult r = rs.get(rowIndex);
                return switch (columnIndex) {
                    case 0 -> (rowIndex + 1);
                    case 1 -> factString(r.getFact());
                    case 2 -> r.isIndependent() ? "INDEPENDENT" : "dependent";
                    case 3 -> fmt(r.getPValue());
                    default -> "";
                };
            }

            @Override
            public Class<?> getColumnClass(int col) {
                return (col == 0) ? Integer.class : String.class;
            }
        };

        factsTable = new JTable(factsModel);
        factsTable.setTransferHandler(new DefaultTableTransferHandler(0));
        TableColumnModel cm = factsTable.getColumnModel();
        cm.getColumn(0).setPreferredWidth(40);
        cm.getColumn(0).setMaxWidth(40);
        cm.getColumn(2).setPreferredWidth(100);
        cm.getColumn(2).setMaxWidth(100);
        cm.getColumn(3).setMinWidth(70);
        cm.getColumn(3).setPreferredWidth(70);
        cm.getColumn(1).setMinWidth(300);
        factsTable.setRowSorter(new TableRowSorter<>(factsModel));

        showIndepsForRow.setEnabled(false);
        factsTable.getSelectionModel().addListSelectionListener(evt -> {
            if (evt.getValueIsAdjusting()) return;
            showIndepsForRow.setEnabled(factsTable.getSelectedRowCount() == 1);
        });
        showIndepsForRow.addActionListener(e -> showIndependenciesForSelectedRow());

        // ---- Check tab ----
        JPanel factsPane = new JPanel(new BorderLayout(6, 6));
        factsPane.setBorder(BorderFactory.createTitledBorder("Tests implied for selected node"));
        factsPane.add(new JScrollPane(factsTable), BorderLayout.CENTER);

        JPanel factsButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        factsButtons.add(showIndepsForRow);
        factsButtons.add(undoGraphButton);
        factsButtons.add(showGraphButton);
        factsPane.add(factsButtons, BorderLayout.SOUTH);

        histogramPanel.setBorder(BorderFactory.createTitledBorder("P-value Histogram"));

        JPanel checkTab = new JPanel(new BorderLayout(8, 8));
        checkTab.add(factsPane, BorderLayout.CENTER);
        checkTab.add(histogramPanel, BorderLayout.SOUTH);

        // ---- Repair tab ----
        repairTabContent = new JPanel(new BorderLayout());
        repairTabContent.add(
                new JLabel("Select a node in the left table, then switch to this tab.",
                        SwingConstants.CENTER),
                BorderLayout.CENTER);

        // ---- Tabbed pane ----
        rightTabs = new JTabbedPane();
        rightTabs.addTab("Check", checkTab);
        rightTabs.addTab("Repair", repairTabContent);

        rightTabs.addChangeListener(e -> {
            if (rightTabs.getSelectedIndex() == TAB_REPAIR) openRepairTab();
            else closeRepairTab();
        });

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, rightTabs);
        split.setResizeWeight(0.45);
        add(split, BorderLayout.CENTER);
    }

    private void openRepairTab() {
        Node x = getSelectedVertex();
        if (x == null) return;

        repairPanel = new VertexRepairPanelGlobalRepair(this, x);
        repairPanel.setKnowledge(knowledge);
        repairPanel.setUseAndersonDarling(model.isUseAndersonDarling());
        repairPanel.setUseWildBootstrap(
                VertexCheckIndTestModel.WILD_BOOTSTRAP.equals(model.getUniformityTest()));

        repairTabContent.removeAll();
        repairTabContent.add(repairPanel, BorderLayout.CENTER);
        repairTabContent.revalidate();
        repairTabContent.repaint();
    }

    private void closeRepairTab() {
        if (repairPanel == null) return;

        Graph repaired = repairPanel.getGraph();
        if (repaired != null && !repaired.equals(model.getGraph())) {
            graphHistory.push(safeCopy(model.getGraph()));
            updateUndoButtonEnabled();
            applyingGraphProgrammatically = true;

            try {
                model.setGraph(repaired);
                firePropertyChange("modelChanged", null, null);
            } finally {
                applyingGraphProgrammatically = false;
            }
        }

        repairPanel = null;
        repairTabContent.removeAll();
        repairTabContent.add(
                new JLabel("Select a node in the left table, then switch to this tab.",
                        SwingConstants.CENTER),
                BorderLayout.CENTER);
        repairTabContent.revalidate();
        repairTabContent.repaint();

        String active = getActiveSelectedVertexName();
        if (active != null) refreshDetails(active);
    }

    private void updateTable(SelectedRows sel) {
        for (int mr : sel.modelRows()) overviewModel.fireTableRowsUpdated(mr, mr);
        refreshModelDiagnostics();
        String stillActive = getActiveSelectedVertexName();
        if (stillActive != null) refreshDetails(stillActive);
    }

    // =========================================================================
    // Selection helpers
    // =========================================================================

    private void refreshDetails(String v) {
        if (rightTabs.getSelectedIndex() == TAB_CHECK) {
            factsModel.fireTableDataChanged();
            updateHistogram(v);
        }
    }

    private void updateHistogram(String vertexName) {
        histogramPanel.removeAll();
        List<IndependenceResult> rs = model.getResultsForVertex(vertexName);
        List<Double> pvals = rs.stream()
                .map(IndependenceResult::getPValue)
                .filter(p -> !Double.isNaN(p) && p >= 0.0 && p <= 1.0)
                .collect(Collectors.toList());
        if (pvals.isEmpty())
            histogramPanel.add(new JLabel("(No valid p-values)"), BorderLayout.CENTER);
        else
            histogramPanel.add(buildHistogramPanel(pvals), BorderLayout.CENTER);
        histogramPanel.revalidate();
        histogramPanel.repaint();
    }

//    private void runAllAndRefresh(String preferredVertex, Runnable onDone) {
//        // If a worker is running, cancel it and queue this as the next task
//        if (activeWorker != null && !activeWorker.isDone()) {
//            activeWorker.cancel(true);
//            pendingTask = () -> runAllAndRefresh(preferredVertex, onDone);
//            return;
//        }
//
//        activeWorker = new SwingWorker<>() {
//            @Override
//            protected Void doInBackground() {
//                model.runAllVertices(true);
//                return null;
//            }
//
//            @Override
//            protected void done() {
//                if (!isCancelled()) {
//                    try {
//                        overviewModel.fireTableDataChanged();
//                        refreshModelDiagnostics();
//
//                        String active;
//                        if (preferredVertex != null) {
//                            restoreOverviewSelection(preferredVertex);
//                            active = preferredVertex;
//                        } else {
//                            selectFirstRowIfAny();
//                            active = getActiveSelectedVertexName();
//                        }
//                        if (active != null) refreshDetails(active);
//
//                        if (onDone != null) onDone.run();
//                    } catch (InterruptedException e) {
//                        throw new RuntimeException(e);
//                    }
//                }
//
//                // Always drain — even if cancelled, the pending task must run
//                Runnable next = pendingTask;
//                pendingTask = null;
//                if (next != null) next.run();
//            }
//        };
//        activeWorker.execute();
//    }

    private JPanel buildHistogramPanel(List<Double> pvals) {
        DataSet ds = new BoxDataSet(new VerticalDoubleDataBox(pvals.size(), 1),
                Collections.singletonList(new ContinuousVariable("p")));
        for (int i = 0; i < pvals.size(); i++) ds.setDouble(i, 0, pvals.get(i));
        Histogram histogram = new Histogram(ds, "p", false);
        histogram.setNumBins(10);
        HistogramPanel view = new HistogramPanel(histogram, true);
        view.setXAxisBounds(0.0, 1.0, true);
        view.setMinimumSize(new Dimension(420, 180));
        view.setPreferredSize(new Dimension(420, 180));
        JPanel p = new JPanel(new BorderLayout());
        p.add(view, BorderLayout.CENTER);
        return p;
    }

    private void onModelGraphChanged() {
        var selectedVertexNames = getSelectedOverviewVertexNames();
        var selectedFactKeys = getSelectedFactsKeys();
        String active = getActiveSelectedVertexName();

        model.clearResults();
        refreshModelDiagnostics();

        runAllAndRefresh(active, () -> {
            reselectOverviewVerticesByName(selectedVertexNames);
            reselectFactsByKey(selectedFactKeys);
            String stillActive = getActiveSelectedVertexName();
            if (stillActive != null) refreshDetails(stillActive);
        });

        firePropertyChange("modelChanged", null, null);
    }

    private void runAllAndRefresh(String preferredVertex, Runnable onDone) {
        if (activeWorker != null && !activeWorker.isDone()) {
            cancelRequested.set(true);          // stop the in-flight compute, not just interrupt
            activeWorker.cancel(true);
            pendingTask = () -> runAllAndRefresh(preferredVertex, onDone);
            return;
        }

        cancelRequested.set(false);             // fresh run
        showProgressDialog();

        activeWorker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                model.runAllVertices(true, cancelRequested::get);   // <-- pass the token
                return null;
            }

            @Override
            protected void done() {
                if (!isCancelled()) {
                    overviewModel.fireTableDataChanged();
                    refreshModelDiagnostics();
                    String active;
                    if (preferredVertex != null) {
                        restoreOverviewSelection(preferredVertex);
                        active = preferredVertex;
                    } else {
                        selectFirstRowIfAny();
                        active = getActiveSelectedVertexName();
                    }
                    if (active != null) refreshDetails(active);
                    if (onDone != null) onDone.run();
                }
                SwingWorker<?, ?> self = this;
                Runnable next = pendingTask;
                pendingTask = null;
                if (next != null) next.run();          // starts a successor, keeps dialog up
                if (activeWorker == self) hideProgressDialog();   // nobody took over
            }
        };
        activeWorker.execute();
    }

    private void showProgressDialog() {
        if (progressDialog != null && progressDialog.isVisible()) return;
        Window owner = SwingUtilities.getWindowAncestor(this);
        progressDialog = new JDialog(owner, "Computing", Dialog.ModalityType.APPLICATION_MODAL);
        progressDialog.setUndecorated(true);
        JButton stop = new JButton("Computing (click to stop)...");
        stop.addActionListener(e -> {
            pendingTask = null;
            cancelRequested.set(true);
            if (activeWorker != null) activeWorker.cancel(true);
            hideProgressDialog();
        });
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED, Color.BLACK, Color.BLACK));
        p.add(stop);
        progressDialog.getContentPane().add(p);
        progressDialog.pack();
        progressDialog.setLocationRelativeTo(owner);
        SwingUtilities.invokeLater(() -> {
            if (progressDialog != null) progressDialog.setVisible(true);
        });
    }

    private void hideProgressDialog() {
        if (progressDialog != null) {
            JDialog d = progressDialog;
            progressDialog = null;
            SwingUtilities.invokeLater(d::dispose);
        }
    }

    private void overviewSelectionChanged(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) return;
        if (activeWorker != null && !activeWorker.isDone()) return; // still running

        SelectedRows sel = getSelectedVertices();
        if (sel.vertices().isEmpty()) return;

        final String active = getActiveSelectedVertexName();
        if (active == null) return;

        List<String> toCompute = sel.vertices().stream()
                .filter(v -> !model.isVertexComputed(v))
                .collect(Collectors.toList());

        if (toCompute.isEmpty()) {
            refreshDetails(active);
            return;
        }

        // Compute off the EDT, then update
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                for (String v : toCompute) {
                    if (isCancelled()) break;
                    model.ensureVertexComputed(v);
                }
                return null;
            }

            @Override
            protected void done() {
                if (isCancelled()) return;
                updateTable(sel);
            }
        };
        worker.execute();
    }

    private void refreshTestList() {
        DataType dt = guessDataType(model.getDataModel());
        indTestCombo.removeAllItems();
        IndependenceTestModels registry = IndependenceTestModels.getInstance(experimentalToggle.includeExperimental());
        List<IndependenceTestModel> models = registry.getModels(dt);
        for (IndependenceTestModel m : models) indTestCombo.addItem(m);
        indTestCombo.setEnabled(indTestCombo.getItemCount() > 0);

        String savedClassName = model.getSavedClassName();// PREFS.get(PREF_KEY_TEST, null);
        IndependenceTestModel toSelect = null;
        if (savedClassName != null) {
            for (int i = 0; i < indTestCombo.getItemCount(); i++) {
                IndependenceTestModel m = indTestCombo.getItemAt(i);
                if (m.getIndependenceTest().clazz().getName().equals(savedClassName)) {
                    toSelect = m;
                    break;
                }
            }
        }
        if (toSelect == null && !models.isEmpty())
            toSelect = registry.getDefaultModel(dt);
        if (toSelect != null) indTestCombo.setSelectedItem(toSelect);
    }

    private void setTestFromCombo() {
        IndependenceTestModel sel = (IndependenceTestModel) indTestCombo.getSelectedItem();
        Class<IndependenceWrapper> clazz = (sel == null) ? null
                : (Class<IndependenceWrapper>) sel.getIndependenceTest().clazz();
        if (clazz == null) return;
        try {
            independenceWrapper = clazz.getDeclaredConstructor().newInstance();
            IndependenceTest test = independenceWrapper.getTest(model.getDataModel(), model.getParameters());
            model.setIndependenceTest(test);
            model.setSavedClassName(clazz.getName());
//            PREFS.put(PREF_KEY_TEST, clazz.getName());
            invalidate();
            repaint();
        } catch (InstantiationException | IllegalAccessException
                 | InvocationTargetException | NoSuchMethodException e) {
            TetradLogger.getInstance().log("Error: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private void applySavedSetType() {
        ConditioningSetType saved = model.getConditioningSetType();
        conditioningCombo.setSelectedItem(
                saved != null ? saved : ConditioningSetType.ORDERED_LOCAL_MARKOV_PROPERTY);
        model.setConditioningSetType((ConditioningSetType) conditioningCombo.getSelectedItem());
    }

    private void resetResultsUI() {
        model.clearResults();
        histogramPanel.removeAll();
        histogramPanel.add(new JLabel("(Select a vertex to compute results)"), BorderLayout.CENTER);
        factsModel.fireTableDataChanged();
        overviewModel.fireTableDataChanged();
        overviewTable.clearSelection();
        histogramPanel.revalidate();
        histogramPanel.repaint();
        runAllAndRefresh(null, null);
    }

    private void refreshModelDiagnostics() {
        String mode = model.getUniformityTest();
        boolean wb = VertexCheckIndTestModel.WILD_BOOTSTRAP.equals(mode);
        String type = switch (mode) {
            case VertexCheckIndTestModel.ANDERSON_DARLING -> "Anderson-Darling";
            case VertexCheckIndTestModel.WILD_BOOTSTRAP -> "Wild Bootstrap (sum T^2)";
            default -> "Kolmogorov-Smirnov";
        };
        updatePvColumnHeader(mode);
        String countLabel = wb ? "# constraints" : "# p-values";
        VertexCheckIndTestModel.ModelSummary ms = model.peekModelSummary();
        if (ms == null) {
            modelNpLabel.setText(countLabel + ": (not computed)");
            modelPLabel.setText(type + " Model Uniformity P: (not computed)");
            return;
        }
        modelNpLabel.setText(countLabel + ": " + ms.numPValues());
        modelPLabel.setText(type + " Model Uniformity P: " + fmt(ms.modelP()));
    }

    /**
     * Renames the overview p-value column header to the active method's abbreviation
     * (KS / AD / WB) so the single column self-identifies. Cheap and idempotent.
     */
    private void updatePvColumnHeader(String mode) {
        if (overviewTable == null) return;
        String abbrev = switch (mode) {
            case VertexCheckIndTestModel.ANDERSON_DARLING -> "AD";
            case VertexCheckIndTestModel.WILD_BOOTSTRAP -> "WB";
            default -> "KS";
        };
        var col = overviewTable.getColumnModel().getColumn(COL_PV);
        if (!abbrev.equals(col.getHeaderValue())) {
            col.setHeaderValue(abbrev);
            overviewTable.getTableHeader().repaint();
        }
    }

    private void showIndependenciesForSelectedRow() {
        String vName = getActiveSelectedVertexName();
        if (vName == null) return;
        int viewRow = factsTable.getSelectedRow();
        if (viewRow < 0) return;
        int modelRow = factsTable.convertRowIndexToModel(viewRow);
        List<IndependenceResult> rs = model.getResultsForVertex(vName);
        if (rs == null || modelRow < 0 || modelRow >= rs.size()) return;
        IndependenceFact fact = rs.get(modelRow).getFact();
        if (fact == null) return;
        Node x = nodeInTestByName(fact.getX().getName());
        Node y = nodeInTestByName(fact.getY().getName());
        if (x == null || y == null) return;
        ShowIndepsDialog dialog = new ShowIndepsDialog(SwingUtilities.getWindowAncestor(this), x, y);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void showGraphDialog() {
        Graph graph = model.getGraph();
        GraphWorkbench workbench = new GraphWorkbench(graph);
        workbench.setEnableEditing(false);
        JScrollPane renderScroll = new JScrollPane(workbench);
        renderScroll.setPreferredSize(new Dimension(820, 520));
        JTextArea ta = new JTextArea(String.valueOf(graph));
        ta.setEditable(false);
        ta.setCaretPosition(0);
        JScrollPane textScroll = new JScrollPane(ta);
        textScroll.setPreferredSize(new Dimension(820, 520));
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Graph", renderScroll);
        tabs.addTab("Text", textScroll);
        tabs.setTabPlacement(JTabbedPane.RIGHT);
        EditorWindow ew = new EditorWindow(tabs, "Current Graph", "OK", false, this);
        DesktopController.getInstance().addEditorWindow(ew, JLayeredPane.PALETTE_LAYER);
        ew.pack();
        ew.setVisible(true);
    }

    // =========================================================================
    // Independence-test utilities
    // =========================================================================

    private void undoGraph() {
        if (graphHistory.isEmpty()) return;
        Graph prev = graphHistory.pop();
        applyingGraphProgrammatically = true;
        try {
            model.setGraph(prev);
        } finally {
            applyingGraphProgrammatically = false;
            updateUndoButtonEnabled();
        }
    }

    private void updateUndoButtonEnabled() {
        undoGraphButton.setEnabled(!graphHistory.isEmpty());
    }

    private String getSelectedVertexName() {
        return getActiveSelectedVertexName();
    }

    private String getActiveSelectedVertexName() {
        int leadView = overviewTable.getSelectionModel().getLeadSelectionIndex();
        if (leadView >= 0) {
            int leadModel = overviewTable.convertRowIndexToModel(leadView);
            if (leadModel >= 0 && leadModel < model.getVertexNames().size())
                return model.getVertexNames().get(leadModel);
        }
        SelectedRows sel = getSelectedVertices();
        return sel.vertices().isEmpty() ? null : sel.vertices().get(0);
    }

    // =========================================================================
    // Misc
    // =========================================================================

    private SelectedRows getSelectedVertices() {
        int[] viewRows = overviewTable.getSelectedRows();
        if (viewRows == null || viewRows.length == 0)
            return new SelectedRows(List.of(), List.of());
        List<Integer> modelRows = new ArrayList<>(viewRows.length);
        List<String> vertices = new ArrayList<>(viewRows.length);
        for (int vr : viewRows) {
            int mr = overviewTable.convertRowIndexToModel(vr);
            modelRows.add(mr);
            vertices.add(model.getVertexNames().get(mr));
        }
        return new SelectedRows(modelRows, vertices);
    }

    private void selectFirstRowIfAny() {
        if (overviewTable.getRowCount() > 0) {
            overviewTable.setRowSelectionInterval(0, 0);
        } else {
            histogramPanel.removeAll();
            histogramPanel.add(new JLabel("(No results)"), BorderLayout.CENTER);
            factsModel.fireTableDataChanged();
        }
    }

    private void restoreOverviewSelection(String vertexName) {
        if (vertexName == null) return;
        for (int row = 0; row < overviewTable.getRowCount(); row++) {
            if (vertexName.equals(overviewTable.getValueAt(row, 0))) {
                overviewTable.getSelectionModel().setSelectionInterval(row, row);
                overviewTable.scrollRectToVisible(overviewTable.getCellRect(row, 0, true));
                return;
            }
        }
    }

    // =========================================================================
    // Static helpers
    // =========================================================================

    private Set<String> getSelectedOverviewVertexNames() {
        Set<String> names = new HashSet<>();
        for (int r : overviewTable.getSelectedRows()) {
            int m = overviewTable.convertRowIndexToModel(r);
            Node v = model.getGraph().getNode(model.getVertexNames().get(m));
            if (v != null) names.add(v.getName());
        }
        return names;
    }

    private Set<String> getSelectedFactsKeys() {
        Set<String> keys = new HashSet<>();
        for (int vr : factsTable.getSelectedRows()) {
            IndependenceFact f = getIndependenceFactFromFactsRow(vr);
            if (f != null) keys.add(factKey(f));
        }
        return keys;
    }

    private IndependenceFact getIndependenceFactFromFactsRow(int factsViewRow) {
        String v = getActiveSelectedVertexName();
        if (v == null) return null;
        int factsModelRow = factsTable.convertRowIndexToModel(factsViewRow);
        List<IndependenceResult> rs = model.getResultsForVertex(v);
        if (rs == null || factsModelRow < 0 || factsModelRow >= rs.size()) return null;
        IndependenceResult r = rs.get(factsModelRow);
        return (r == null ? null : r.getFact());
    }

    private void reselectOverviewVerticesByName(Set<String> names) {
        ListSelectionModel sel = overviewTable.getSelectionModel();
        sel.clearSelection();
        for (int mr = 0; mr < model.getVertexNames().size(); mr++) {
            if (names.contains(model.getVertexNames().get(mr))) {
                int vr = overviewTable.convertRowIndexToView(mr);
                sel.addSelectionInterval(vr, vr);
            }
        }
    }

    private void reselectFactsByKey(Set<String> keys) {
        ListSelectionModel sel = factsTable.getSelectionModel();
        sel.clearSelection();
        String v = getActiveSelectedVertexName();
        if (v == null) return;
        List<IndependenceResult> rs = model.getResultsForVertex(v);
        if (rs == null) return;
        for (int modelRow = 0; modelRow < rs.size(); modelRow++) {
            IndependenceFact f = rs.get(modelRow).getFact();
            if (f != null && keys.contains(factKey(f))) {
                int viewRow = factsTable.convertRowIndexToView(modelRow);
                sel.addSelectionInterval(viewRow, viewRow);
            }
        }
    }

    private Node nodeInTestByName(String name) {
        if (name == null) return null;
        IndependenceTest test = (Q != null ? Q.getTest() : null);
        if (test == null) test = model.getIndependenceTest();
        if (test == null) return null;
        for (Node n : test.getVariables())
            if (n != null && Objects.equals(n.getName(), name)) return n;
        return null;
    }

    private List<IndependenceResult> findIndependencies(Node x, Node y,
                                                        PoolChoice poolChoice, int maxSetSize) {
        IndependenceTest test = model.getIndependenceTest();
        if (test == null) return Collections.emptyList();
        Graph g = model.getGraph();
        if (g == null) return Collections.emptyList();

        Set<Node> pool = new LinkedHashSet<>();
        switch (poolChoice) {
            case MB_UNION -> {
                pool.addAll(GraphUtils.markovBlanket(x, g));
                pool.addAll(GraphUtils.markovBlanket(y, g));
            }
            case PARENTS_UNION -> {
                pool.addAll(g.getParents(x));
                pool.addAll(g.getParents(y));
            }
            case PARENTS_AND_NEIGHBORS_UNION -> {
                pool.addAll(parentsAndNeighbors(x, g));
                pool.addAll(parentsAndNeighbors(y, g));
            }
            case ALL_NODES -> pool.addAll(g.getNodes());
        }
        pool.remove(x);
        pool.remove(y);

        List<Node> poolList = new ArrayList<>(pool);
        poolList.sort(Comparator.comparing(Node::getName));
        if (poolList.size() > 30) poolList = poolList.subList(0, 30);

        List<IndependenceResult> found = new ArrayList<>();
        for (int k = 0; k <= maxSetSize; k++) {
            if (found.size() >= 200) break;
            enumerateSubsets(poolList, k, subset -> {
                if (found.size() >= 200) return false;
                try {
                    IndependenceResult r = (Q != null)
                            ? Q.checkIndependence(x, y, new LinkedHashSet<>(subset))
                            : test.checkIndependence(x, y, new LinkedHashSet<>(subset));
                    if (r != null && r.isIndependent()) found.add(r);
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
                return true;
            });
        }
        found.sort((a, b) -> Double.compare(b.getPValue(), a.getPValue()));
        return found;
    }

    private Set<Node> parentsAndNeighbors(Node x, Graph g) {
        Set<Node> z = new LinkedHashSet<>();
        for (Node w : g.getAdjacentNodes(x)) {
            Edge e = g.getEdge(w, x);
            if (e != null && Edges.isUndirectedEdge(e)) z.add(w);
            if (g.isParentOf(w, x)) z.add(w);
        }
        return z;
    }

    private void enumerateSubsets(List<Node> pool, int k, Function<List<Node>, Boolean> accept) {
        if (k == 0) {
            accept.apply(Collections.emptyList());
            return;
        }
        if (pool.isEmpty() || k > pool.size()) return;
        Node[] a = pool.toArray(new Node[0]);
        int n = a.length;
        int[] idx = new int[k];
        for (int i = 0; i < k; i++) idx[i] = i;
        while (true) {
            List<Node> subset = new ArrayList<>(k);
            for (int i = 0; i < k; i++) subset.add(a[idx[i]]);
            Boolean cont = accept.apply(subset);
            if (cont != null && !cont) return;
            int i = k - 1;
            while (i >= 0 && idx[i] == n - k + i) i--;
            if (i < 0) break;
            idx[i]++;
            for (int j = i + 1; j < k; j++) idx[j] = idx[j - 1] + 1;
        }
    }

    private String fmt(double x) {
        return Double.isNaN(x) ? "" : nf.format(x);
    }

    private Graph safeCopy(Graph g) {
        if (g == null) return null;
        try {
            return g.copy();
        } catch (Throwable t) {
            return new EdgeListGraph(g);
        }
    }

    // =========================================================================
    // Inner types
    // =========================================================================

    private enum PoolChoice {
        MB_UNION("MB(x) U MB(y)"),
        PARENTS_UNION("Parents(x) U Parents(y)"),
        PARENTS_AND_NEIGHBORS_UNION("Parents-and-Neighbors(x) U Parents-and-Neighbors(y)"),
        ALL_NODES("All nodes \\ {x, y}");
        private final String label;

        PoolChoice(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private record SelectedRows(List<Integer> modelRows, List<String> vertices) {
    }

    // ---- ShowIndepsDialog --------------------------------------------------

    private final class ShowIndepsDialog extends JDialog {
        private final Node x, y;
        private final JComboBox<PoolChoice> poolBox = new JComboBox<>(PoolChoice.values());
        private final JSpinner depthSpinner = new JSpinner(new SpinnerNumberModel(3, 0, 10, 1));
        private final JButton showButton = new JButton("Show independencies");
        private final JPanel resultsPanel = new JPanel(new BorderLayout(6, 6));
        private final JLabel emptyLabel =
                new JLabel("No independencies found under those constraints.");
        private final IndepTableModel tableModel = new IndepTableModel();
        private final JTable table = new JTable(tableModel);

        ShowIndepsDialog(Window owner, Node x, Node y) {
            super(owner, "Independencies for <" + x.getName() + ", " + y.getName() + ">",
                    Dialog.ModalityType.APPLICATION_MODAL);
            this.x = x;
            this.y = y;

            table.setTransferHandler(new DefaultTableTransferHandler(0));
            TableColumnModel cm = table.getColumnModel();
            cm.getColumn(0).setPreferredWidth(40);
            cm.getColumn(0).setMaxWidth(40);
            cm.getColumn(2).setPreferredWidth(100);
            cm.getColumn(2).setMaxWidth(100);
            cm.getColumn(3).setMinWidth(70);
            cm.getColumn(3).setPreferredWidth(70);
            cm.getColumn(1).setMinWidth(350);
            table.setRowSorter(new TableRowSorter<>(tableModel));
            setDefaultCloseOperation(DISPOSE_ON_CLOSE);

            JPanel controls = new JPanel(new GridBagLayout());
            controls.setBorder(new EmptyBorder(10, 10, 6, 10));
            GridBagConstraints gc = new GridBagConstraints();
            gc.insets = new Insets(3, 3, 3, 3);
            gc.anchor = GridBagConstraints.WEST;
            gc.gridx = 0;
            gc.gridy = 0;
            controls.add(new JLabel("Candidate pool:"), gc);
            gc.gridx = 1;
            gc.fill = GridBagConstraints.HORIZONTAL;
            gc.weightx = 1.0;
            controls.add(poolBox, gc);
            gc.gridx = 0;
            gc.gridy = 1;
            gc.fill = GridBagConstraints.NONE;
            gc.weightx = 0;
            controls.add(new JLabel("Depth:"), gc);
            gc.gridx = 1;
            controls.add(depthSpinner, gc);
            gc.gridx = 0;
            gc.gridy = 2;
            gc.gridwidth = 2;
            controls.add(showButton, gc);

            resultsPanel.setBorder(new EmptyBorder(0, 10, 10, 10));
            resultsPanel.add(new JLabel("(Click 'Show independencies' to run)"), BorderLayout.CENTER);
            table.setFillsViewportHeight(true);
            table.setAutoCreateRowSorter(true);
            showButton.addActionListener(e -> runSearch());

            getContentPane().setLayout(new BorderLayout());
            getContentPane().add(controls, BorderLayout.NORTH);
            getContentPane().add(resultsPanel, BorderLayout.CENTER);
            setSize(780, 520);
        }

        private void runSearch() {
            showButton.setEnabled(false);
            resultsPanel.removeAll();
            resultsPanel.add(new JLabel("Searching..."), BorderLayout.CENTER);
            resultsPanel.revalidate();
            resultsPanel.repaint();
            final PoolChoice choice = (PoolChoice) poolBox.getSelectedItem();
            final int k = (Integer) depthSpinner.getValue();
            new SwingWorker<List<IndependenceResult>, Void>() {
                @Override
                protected List<IndependenceResult> doInBackground() {
                    return findIndependencies(x, y, choice, k);
                }

                @Override
                protected void done() {
                    try {
                        List<IndependenceResult> found = get();
                        tableModel.setResults(found);
                        resultsPanel.removeAll();
                        resultsPanel.add(found.isEmpty()
                                ? emptyLabel : new JScrollPane(table), BorderLayout.CENTER);
                    } catch (Exception ex) {
                        resultsPanel.removeAll();
                        resultsPanel.add(new JLabel("Error: " + ex.getMessage()), BorderLayout.CENTER);
                    } finally {
                        showButton.setEnabled(true);
                        resultsPanel.revalidate();
                        resultsPanel.repaint();
                    }
                }
            }.execute();
        }
    }

    private final class IndepTableModel extends AbstractTableModel {
        private List<IndependenceResult> results = Collections.emptyList();

        void setResults(List<IndependenceResult> rs) {
            this.results = (rs == null) ? Collections.emptyList() : new ArrayList<>(rs);
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return results.size();
        }

        @Override
        public int getColumnCount() {
            return 4;
        }

        @Override
        public String getColumnName(int col) {
            return switch (col) {
                case 0 -> "#";
                case 1 -> "Fact";
                case 2 -> "Result";
                case 3 -> "P-value";
                default -> "";
            };
        }

        @Override
        public Object getValueAt(int row, int col) {
            IndependenceResult r = results.get(row);
            return switch (col) {
                case 0 -> row + 1;
                case 1 -> factString(r.getFact());
                case 2 -> "INDEPENDENT";
                case 3 -> fmt(r.getPValue());
                default -> "";
            };
        }
    }
}