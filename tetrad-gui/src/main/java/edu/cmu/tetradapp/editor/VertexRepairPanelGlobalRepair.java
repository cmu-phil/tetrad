package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.VertexRepairSearch;
import edu.cmu.tetrad.search.VertexRepairSearch.AdjustmentGraphType;
import edu.cmu.tetrad.search.VertexRepairSearch.CandidateEdit;
import edu.cmu.tetrad.search.VertexRepairSearch.RepairListener;
import edu.cmu.tetrad.search.VertexRepairSearch.RepairStrategy;
import edu.cmu.tetrad.search.VertexRepairSearch.ScoredCandidate;
import edu.cmu.tetrad.search.test.CachedIndependenceQueries;
import edu.cmu.tetrad.util.NaturalSort;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetradapp.model.VertexCheckIndTestModel;
import edu.cmu.tetradapp.util.DesktopController;
import edu.cmu.tetradapp.workbench.GraphWorkbench;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.*;
import java.awt.*;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.prefs.Preferences;


/**
 * Interactive panel for locally adjusting a causal graph around a selected node {@code x}
 * using feedback from the Markov checker.
 *
 * <p>All search and repair computation is delegated to {@link VertexRepairSearch}, which
 * implements {@link edu.cmu.tetrad.search.IGraphSearch} and can therefore be used
 * independently in simulation studies. This panel constructs a {@code VertexRepairSearch},
 * registers itself as a {@link RepairListener}, and translates the events it receives into
 * UI updates.
 *
 * <p>The panel's behaviour is identical to the previous monolithic version; only the
 * structural separation has changed.
 */
public final class VertexRepairPanelGlobalRepair extends JPanel {

    private static final String CARD_TABLE = "table";
    private static final String CARD_NONE = "none";
    private static final DecimalFormat MODEL_P_FORMAT = new DecimalFormat("0.0000");

    // ---- Preferences (persist α and model-P top-K) ----
    private final VertexCheckIndTestModel baseModel;
    private final Deque<Graph> history = new ArrayDeque<>();

    // UI
    private final JComboBox<AdjustmentGraphType> graphTypeCombo = new JComboBox<>(AdjustmentGraphType.values());
    private final JButton searchButton = new JButton();
    private final JButton backButton = new JButton("Undo");
    private final JButton showGraphButton = new JButton("Graph");
    private final JButton repairButton = new JButton("Attempt Repair");
    private final JLabel statusLabel = new JLabel(" ");
    private final JTable resultsTable = new JTable();
    private final CandidateTableModel resultsModel = new CandidateTableModel();
    private final JPanel resultsCard = new JPanel(new CardLayout());
    private final JComboBox<Node> nodeCombo = new JComboBox<>();
    private final JSpinner seedSpinner;
    private final JComboBox<RepairStrategy> repairStrategyCombo =
            new JComboBox<>(RepairStrategy.values());
    private boolean populatingNodeCombo = false;
    private final JSpinner pruneAlphaSpinner;

    private Node x;

    /** The shared search object that owns the working graph and all repair logic. */
    private final VertexRepairSearch repairSearch;

    private volatile SwingWorker<?, ?> activeWorker;
    private volatile JDialog watchDialog;
    private boolean useAndersonDarling = false;

    // =========================================================================
    // Construction
    // =========================================================================

    public VertexRepairPanelGlobalRepair(VertexCheckEditor editor, Node x) {
        super(new BorderLayout());

        seedSpinner = new JSpinner(new SpinnerNumberModel(
                Preferences.userRoot().getInt("vertexRepairSeed", RandomUtil.getInstance().nextInt(50000)),
                0, Integer.MAX_VALUE, 1));
        pruneAlphaSpinner = new JSpinner(new SpinnerNumberModel(
                Preferences.userRoot().getDouble("vertexRepairPruneAlpha", 0.2),
                0.0, 1.0, 0.01));

        Preferences.userRoot().putInt("vertexRepairSeed",
                ((SpinnerNumberModel) seedSpinner.getModel()).getNumber().intValue());

        this.baseModel = Objects.requireNonNull(editor.getIndTestModel(), "editor.getIndTestModel()");
        CachedIndependenceQueries Q = Objects.requireNonNull(editor.getCachedQueries(), "editor.getCachedQueries()");

        // Build the search object and configure it
        this.repairSearch = new VertexRepairSearch(editor.getIndTestModel().getGraph(), editor.getIndTestModel().getIndependenceTest(),
                editor.getIndTestModel().getConditioningSetType());
        this.repairSearch.setUseAndersonDarling(useAndersonDarling);
        this.repairSearch.addRepairListener(new PanelRepairListener(resultsModel));

        // Resolve the initial node inside the working graph
        Graph wg = repairSearch.getGraph();
        this.x = resolveInitialNode(wg, x);
        searchButton.setText("Adjust " + this.x.getName());

        boolean graphIsLegal = initGraphTypeComboFromGraph(wg);
        syncSearchFromUI();  // push initial UI selections into the search object

        buildUI();
        wireActions();
        updateButtons();

        if (!graphIsLegal) {
            SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(
                            this,
                            "The supplied graph does not match any recognised legal graph type\n"
                                    + "(DAG, CPDAG, PDAG, MAG, or PAG).",
                            "Unrecognised Graph Type",
                            JOptionPane.WARNING_MESSAGE));
        }

        SwingUtilities.invokeLater(() -> startWatched("Searching", this::runSearchWatched, null));
        setPreferredSize(new Dimension(650, 600));
    }

    // =========================================================================
    // Public API
    // =========================================================================

    public Graph getGraph() {
        return repairSearch.getGraph();
    }

    public void setKnowledge(Knowledge knowledge) {
        repairSearch.setKnowledge(knowledge);
    }

    public void setUseAndersonDarling(boolean useAndersonDarling) {
        this.useAndersonDarling = useAndersonDarling;
        repairSearch.setUseAndersonDarling(useAndersonDarling);  // add this
    }

    // =========================================================================
    // Inner listener — translates search events into EDT UI updates
    // =========================================================================

    /**
     * Bridges {@link RepairListener} callbacks (fired on a background thread) into
     * EDT-safe Swing updates. This is the only place in the panel that handles
     * search events.
     */
//    private final class PanelRepairListener implements RepairListener {
//
//        @Override
//        public void statusUpdated(String message) {
//            SwingUtilities.invokeLater(() -> statusLabel.setText(message));
//        }
//
//        @Override
//        public void editApplied(CandidateEdit edit, Graph currentGraph) {
//            // Keep the model's persisted graph in sync during batch repair
//            baseModel.setGraph(currentGraph);
//            SwingUtilities.invokeLater(() ->
//                    statusLabel.setText("Applied: " + edit.description()));
//        }
//
//        @Override
//        public void repairConverged(int totalEdits, String message) {
//            SwingUtilities.invokeLater(() -> {
//                populateNodeCombo();
//                statusLabel.setText(message);
//                baseModel.setGraph(repairSearch.getGraph());
//            });
//        }
//    }

    private final class PanelRepairListener implements RepairListener {

        private final CandidateTableModel tableModel;

        public PanelRepairListener(CandidateTableModel tableModel) {
            this.tableModel = tableModel;
        }

        @Override
        public void statusUpdated(String message) {
            SwingUtilities.invokeLater(() -> statusLabel.setText(message));
        }

        @Override
        public void editApplied(CandidateEdit edit, Graph currentGraph) {
            populateNodeCombo();
//            baseModel.setGraph(safeCopy(repairSearch.getGraph()));
            tableModel.fireTableDataChanged();
            SwingUtilities.invokeLater(() ->
                    statusLabel.setText("Applied: " + edit.description()));
        }

        @Override
        public void repairConverged(int totalEdits, String message) {
            SwingUtilities.invokeLater(() -> {
                populateNodeCombo();
                statusLabel.setText(message);
                // Pass a distinct instance so PROP_GRAPH actually fires in the base model.
//                baseModel.setGraph(safeCopy(repairSearch.getGraph()));
            });
        }
    }

    // =========================================================================
    // UI construction
    // =========================================================================

    private void buildUI() {
        JPanel controls = new JPanel(new GridBagLayout());
        controls.setBorder(new TitledBorder("Repair Model"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);

        c.gridx = 0; c.gridy = 1; c.gridwidth = 1;
        c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 0;
        controls.add(new JLabel("Node:"), c);

        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1;
        populateNodeCombo();
        controls.add(nodeCombo, c);

        c.gridx = 2; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1;
        controls.add(graphTypeCombo, c);

        c.gridx = 2; c.gridy = 2; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1;
        repairStrategyCombo.setSelectedItem(
                RepairStrategy.valueOf(
                        Preferences.userRoot().get("vertexRepairStrategy",
                                RepairStrategy.LOCAL_SWEEP.name())));
        controls.add(repairStrategyCombo, c);

        repairStrategyCombo.addActionListener(e -> {
            RepairStrategy s = (RepairStrategy) repairStrategyCombo.getSelectedItem();
            if (s != null) {
                Preferences.userRoot().put("vertexRepairStrategy", s.name());
                repairSearch.setRepairStrategy(s);
            }
        });

        c.gridx = 0; c.gridy = 2; c.gridwidth = 1; c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        controls.add(new JLabel("Seed:"), c);

        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 0.5;
        ((JSpinner.DefaultEditor) seedSpinner.getEditor()).getTextField().setColumns(6);
        controls.add(seedSpinner, c);

        c.gridx = 0; c.gridy = 3; c.gridwidth = 1; c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        controls.add(new JLabel("Prune alpha:"), c);

        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 0.5;
        ((JSpinner.DefaultEditor) pruneAlphaSpinner.getEditor()).getTextField().setColumns(6);
        controls.add(pruneAlphaSpinner, c);

        JPanel topButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        topButtons.add(backButton);
        topButtons.add(showGraphButton);
        topButtons.add(searchButton);
        topButtons.add(repairButton);

        JPanel north = new JPanel(new BorderLayout());
        north.add(controls, BorderLayout.CENTER);
        north.add(topButtons, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);

        // Results table
        resultsTable.setModel(resultsModel);
        resultsTable.setRowHeight(24);
        resultsTable.setFillsViewportHeight(true);

        resultsTable.getColumnModel().getColumn(CandidateTableModel.COL_APPLY)
                .setCellRenderer(new ButtonRenderer());
        resultsTable.getColumnModel().getColumn(CandidateTableModel.COL_APPLY)
                .setCellEditor(new ButtonEditor(row -> {
                    if (row < 0) return;
                    CandidateEdit cand = resultsModel.getCandidate(row);
                    applyCandidate(cand);
                }));

        resultsTable.setTransferHandler(new DefaultTableTransferHandler(0));

        TableColumnModel cm = resultsTable.getColumnModel();
        resultsTable.getColumnModel().getColumn(CandidateTableModel.COL_MODEL_P).setCellRenderer(modelPRenderer());
        resultsTable.getColumnModel().getColumn(CandidateTableModel.COL_NODE_P).setCellRenderer(modelPRenderer());

        configureColumn(cm, 0, -1, -1, 1000);   // Edit — elastic
        configureColumn(cm, 1, 50, 50, 50);      // Baseline
        configureColumn(cm, 2, 50, 50, 50);      // After
        configureColumn(cm, 3, 50, 50, 50);      // Δ
        configureColumn(cm, 4, 70, 70, 70);      // Node-P
        configureColumn(cm, 5, 70, 70, 70);      // Model-P
        configureColumn(cm, 6, 50, 50, 50);      // Edges
        cm.getColumn(7).setMinWidth(70);          // Apply

        applySortAndFilter();

        JPanel tablePanel = new JPanel(new BorderLayout());
        tablePanel.add(new JScrollPane(resultsTable), BorderLayout.CENTER);
        tablePanel.add(statusLabel, BorderLayout.SOUTH);

        JPanel nonePanel = new JPanel(new BorderLayout());
        nonePanel.add(new JLabel("No candidate repairs computed yet.", SwingConstants.CENTER),
                BorderLayout.CENTER);

        resultsCard.add(nonePanel, CARD_NONE);
        resultsCard.add(tablePanel, CARD_TABLE);
        ((CardLayout) resultsCard.getLayout()).show(resultsCard, CARD_NONE);
        add(resultsCard, BorderLayout.CENTER);
    }

    private static void configureColumn(TableColumnModel cm, int col, int min, int max, int pref) {
        TableColumn tc = cm.getColumn(col);
        if (min > 0) tc.setMinWidth(min);
        if (max > 0) tc.setMaxWidth(max);
        if (pref > 0) tc.setPreferredWidth(pref);
    }

    // =========================================================================
    // Wiring
    // =========================================================================

    private void wireActions() {
        backButton.addActionListener(e -> goBack());
        showGraphButton.addActionListener(e -> showGraphWindow());
        searchButton.addActionListener(e -> startWatched("Searching", this::runSearchWatched, null));

        nodeCombo.addActionListener(e -> {
            if (populatingNodeCombo) return;   // <-- add this
            Object sel = nodeCombo.getSelectedItem();
            if (!(sel instanceof Node n)) return;

            Graph wg = repairSearch.getGraph();
            Node inGraph = (wg != null && n.getName() != null) ? wg.getNode(n.getName()) : null;

            if (inGraph == null) {
                x = resolveInitialNode(wg, null);
                populateNodeCombo();
            } else {
                x = inGraph;
            }

            searchButton.setText("Adjust " + x.getName());

            if (activeWorker == null) {
                startWatched("Searching", this::runSearchWatched, null);
            }
        });

        repairButton.addActionListener(e ->
                startWatched("Repairing", this::runRepairWatched,
                        () -> startWatched("Searching", this::runSearchWatched, null)));

        seedSpinner.addChangeListener(e -> {
            int seed = (Integer) seedSpinner.getValue();
            Preferences.userRoot().putInt("vertexRepairSeed", seed);
            repairSearch.setSeed(seed);
        });

        pruneAlphaSpinner.addChangeListener(e -> {
            double pruneAlpha = ((Number) pruneAlphaSpinner.getValue()).doubleValue();
            Preferences.userRoot().putDouble("vertexRepairPruneAlpha", pruneAlpha);
            repairSearch.setPruneAlpha(pruneAlpha);
        });

        graphTypeCombo.addActionListener(e -> syncSearchFromUI());
    }

    /** Pushes the current UI selections (graph type, strategy, seed) into the search object. */
    private void syncSearchFromUI() {
        AdjustmentGraphType gt = (AdjustmentGraphType) graphTypeCombo.getSelectedItem();
        if (gt != null) repairSearch.setGraphType(gt);

        RepairStrategy rs = (RepairStrategy) repairStrategyCombo.getSelectedItem();
        if (rs != null) repairSearch.setRepairStrategy(rs);

        repairSearch.setSeed((Integer) seedSpinner.getValue());
//        repairSearch.setSeed(System.currentTimeMillis());
        repairSearch.setPruneAlpha(((Number) pruneAlphaSpinner.getValue()).doubleValue());
    }

    // =========================================================================
    // Background work: search
    // =========================================================================

    private void runSearchWatched() {
        // Ensure the search object is up-to-date
        syncSearchFromUI();

        List<ScoredCandidate> scored = repairSearch.searchForNode(x);

        double mpBefore = scored.isEmpty() ? Double.NaN : scored.getFirst().modelPBefore();
        int baseline = scored.isEmpty() ? 0 : scored.getFirst().violationsBaseline();
        ScoredCandidate bestCand = scored.isEmpty() ? null : scored.getFirst();

        SwingUtilities.invokeLater(() -> {
            resultsModel.set(scored);
            applySortAndFilter();

            NumberFormat fmt = new DecimalFormat("0.0000");
            if (scored.isEmpty()) {
                statusLabel.setText("No legal candidate edits found.");
                ((CardLayout) resultsCard.getLayout()).show(resultsCard, CARD_NONE);
            } else {
                int bestViol = (bestCand == null) ? baseline : bestCand.violationsAfter();
                String mpBestStr = (bestCand == null || Double.isNaN(bestCand.modelPAfter()))
                        ? "n/a"
                        : fmt.format(bestCand.modelPAfter());
                statusLabel.setText(
                        "Baseline violations: " + baseline
                                + " | Best: " + bestViol
                                + " | Model-P: " + fmt.format(mpBefore) + " \u2192 " + mpBestStr);
                ((CardLayout) resultsCard.getLayout()).show(resultsCard, CARD_TABLE);
            }
        });
    }

    // =========================================================================
    // Background work: repair
    // =========================================================================

    private void runRepairWatched() {
        syncSearchFromUI();

        // Snapshot for undo
        history.push(safeCopy(repairSearch.getGraph()));

        try {
            repairSearch.search();
            baseModel.setGraph(safeCopy(repairSearch.getGraph()));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    // =========================================================================
    // Candidate application (interactive, single edit from the table)
    // =========================================================================

//    private void applyCandidate(CandidateEdit cand) {
//        if (cand == null || cand.isNoOp()) return;
//
//        history.push(safeCopy(repairSearch.getGraph()));
//        repairSearch.applyEdit(cand);
//        baseModel.setGraph(repairSearch.getGraph());
//
//        // Refresh the node reference and node combo
//        Graph wg = repairSearch.getGraph();
//        if (x != null && x.getName() != null) {
//            Node inGraph = wg.getNode(x.getName());
//            x = (inGraph != null) ? inGraph : resolveInitialNode(wg, null);
//        }
//        SwingUtilities.invokeLater(this::populateNodeCombo);
//
//        updateButtons();
//        statusLabel.setText("Applied: " + cand.description());
//    }

    private void applyCandidate(CandidateEdit cand) {
        if (cand == null || cand.isNoOp()) return;

        history.push(safeCopy(repairSearch.getGraph()));
        repairSearch.applyEdit(cand);

        // Publish to the enclosing model. We MUST pass a distinct Graph instance here,
        // otherwise VertexCheckIndTestModel.setGraph may short-circuit (same reference
        // or .equals()) and never fire PROP_GRAPH — which is what VertexCheckEditor
        // listens to in order to refresh its overview/facts tables.
//        baseModel.setGraph(safeCopy(repairSearch.getGraph()));

        // Refresh the node reference and node combo
        Graph wg = repairSearch.getGraph();
        if (x != null && x.getName() != null) {
            Node inGraph = wg.getNode(x.getName());
            x = (inGraph != null) ? inGraph : resolveInitialNode(wg, null);
        }
        SwingUtilities.invokeLater(this::populateNodeCombo);

        updateButtons();
        statusLabel.setText("Applied: " + cand.description());

        // Re-score candidates against the updated graph so the table reflects the
        // new state instead of the stale pre-edit candidates.
        startWatched("Searching", this::runSearchWatched, null);
    }

    // =========================================================================
    // Undo / history
    // =========================================================================

//    private void goBack() {
//        if (history.isEmpty()) return;
//        Graph prev = history.pop();
//        repairSearch.setGraph(prev);
//        baseModel.setGraph(prev);
//        statusLabel.setText("Reverted to previous graph.");
//        updateButtons();
//        startWatched("Searching", this::runSearchWatched, null);
//    }

    private void goBack() {
        if (history.isEmpty()) return;
        Graph prev = history.pop();
        repairSearch.setGraph(prev);
        baseModel.setGraph(safeCopy(prev));  // force a distinct instance
        statusLabel.setText("Reverted to previous graph.");
        updateButtons();
        startWatched("Searching", this::runSearchWatched, null);
    }

    // =========================================================================
    // Misc UI helpers
    // =========================================================================

    private void showGraphDialog() {
        Graph graph = repairSearch.getGraph();

        JTextArea ta = new JTextArea(String.valueOf(graph));
        ta.setEditable(false);
        ta.setCaretPosition(0);
        JScrollPane textScroll = new JScrollPane(ta);
        textScroll.setPreferredSize(new Dimension(820, 520));

        GraphWorkbench workbench = new GraphWorkbench(graph);
        workbench.setEnableEditing(false);
        JScrollPane renderScroll = new JScrollPane(workbench);
        renderScroll.setPreferredSize(new Dimension(820, 520));

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Graph", renderScroll);
        tabs.addTab("Text", textScroll);
        tabs.setTabPlacement(JTabbedPane.RIGHT);

        JOptionPane.showMessageDialog(this, tabs, "Current Graph", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showGraphWindow() {
        Graph graph = repairSearch.getGraph();
        GraphWorkbench workbench = new GraphWorkbench(graph);
        workbench.setEnableEditing(false);
        JScrollPane renderScroll = new JScrollPane(workbench);
        renderScroll.setPreferredSize(new Dimension(820, 520));
        JTextArea ta = new JTextArea(String.valueOf(graph));
        ta.setEditable(false); ta.setCaretPosition(0);
        JScrollPane textScroll = new JScrollPane(ta);
        textScroll.setPreferredSize(new Dimension(820, 520));
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Graph", renderScroll);
        tabs.addTab("Text", textScroll);
        tabs.setTabPlacement(JTabbedPane.RIGHT);
        EditorWindow editorWindow = new EditorWindow(tabs, "Current Graph", "OK", false, this);
        DesktopController.getInstance().addEditorWindow(editorWindow, JLayeredPane.PALETTE_LAYER);
        editorWindow.pack();
        editorWindow.setVisible(true);
    }

    private void populateNodeCombo() {
        populatingNodeCombo = true;
        try {
            Graph wg = repairSearch.getGraph();
            DefaultComboBoxModel<Node> m = new DefaultComboBoxModel<>();
            if (wg != null) {
                List<Node> nodes = new ArrayList<>(wg.getNodes());
                nodes.sort(Comparator.comparing(Node::getName, NaturalSort.NATURAL_NAME_COMPARATOR));
                for (Node n : nodes) m.addElement(n);
            }
            nodeCombo.setModel(m);

            if (x != null && x.getName() != null && wg != null) {
                Node inGraph = wg.getNode(x.getName());
                if (inGraph != null) nodeCombo.setSelectedItem(inGraph);
                else if (m.getSize() > 0) nodeCombo.setSelectedIndex(0);
            } else if (m.getSize() > 0) {
                nodeCombo.setSelectedIndex(0);
            }

            Object sel = nodeCombo.getSelectedItem();
            if (sel instanceof Node n) {
                x = n;
                searchButton.setText("Adjust " + x.getName());
            }
        } finally {
            populatingNodeCombo = false;
        }
    }

    private void applySortAndFilter() {
        resultsModel.sortByCanonicalOrder();
    }

    private void updateButtons() {
        backButton.setEnabled(!history.isEmpty());
        boolean busy = (activeWorker != null);
        searchButton.setEnabled(!busy);
        repairButton.setEnabled(!busy);
    }

    private void startWatched(String title, Runnable backgroundWork, Runnable onDoneEdt) {
        if (activeWorker != null) return;

        SwingWorker<Void, String> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                backgroundWork.run();
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                } catch (CancellationException ce) {
                    statusLabel.setText("Cancelled.");
                    repairSearch.cancel();
                } catch (Exception ex) {
                    statusLabel.setText("Error: " + (ex.getCause() != null
                            ? ex.getCause().getMessage() : ex.getMessage()));
                } finally {
                    closeWatchDialog();
                    activeWorker = null;
                    updateButtons();
                    if (onDoneEdt != null) SwingUtilities.invokeLater(onDoneEdt);
                }
            }
        };

        this.activeWorker = worker;
        updateButtons();
        statusLabel.setText(title + "...");

        showWatchDialog(title, worker);
        worker.execute();
    }

    private void showWatchDialog(String title, SwingWorker<?, ?> worker) {
        closeWatchDialog();
        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dlg = new JDialog(owner, title, Dialog.ModalityType.MODELESS);
        dlg.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

        JPanel p = new JPanel(new BorderLayout(10, 10));
        p.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        JLabel msg = new JLabel("Running. Click Cancel to stop (may stop between tests).");
        JProgressBar bar = new JProgressBar();
        bar.setIndeterminate(true);

        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> {
            worker.cancel(true);
            repairSearch.cancel();
            statusLabel.setText("Cancelling...");
            cancel.setEnabled(false);
        });

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.add(cancel);

        p.add(msg, BorderLayout.NORTH);
        p.add(bar, BorderLayout.CENTER);
        p.add(south, BorderLayout.SOUTH);

        dlg.setContentPane(p);
        dlg.pack();
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);

        this.watchDialog = dlg;
    }

    private void closeWatchDialog() {
        JDialog dlg = this.watchDialog;
        this.watchDialog = null;
        if (dlg != null) dlg.dispose();
    }

    // =========================================================================
    // Graph type combo initialisation
    // =========================================================================

    private boolean initGraphTypeComboFromGraph(Graph g) {
        List<AdjustmentGraphType> plausible = new ArrayList<>();
        for (AdjustmentGraphType gt : AdjustmentGraphType.values()) {
            try {
                if (g != null && isLegalGraphType(g, gt)) plausible.add(gt);
            } catch (Exception ignored) {}
        }

        boolean legal = !plausible.isEmpty();
        if (!legal) plausible = Arrays.asList(AdjustmentGraphType.values());

        graphTypeCombo.setModel(new DefaultComboBoxModel<>(
                plausible.toArray(new AdjustmentGraphType[0])));
        graphTypeCombo.setSelectedIndex(0);
        return legal;
    }

    private static boolean isLegalGraphType(Graph g, AdjustmentGraphType gt) {
        return switch (gt) {
//            case DAG -> g.paths().isLegalDag();
            case CPDAG -> g.paths().isLegalCpdag() || g.paths().isLegalPdag();
//            case PDAG -> g.paths().isLegalPdag();
//            case MAG -> g.paths().isLegalMag();
            case PAG -> g.paths().isLegalPag();
            default -> false;
        };
    }

    // =========================================================================
    // Utilities
    // =========================================================================

    private static Node resolveInitialNode(Graph g, Node requested) {
        if (g == null) return requested;
        List<Node> nodes = new ArrayList<>(g.getNodes());
        nodes.sort(Comparator.comparing(Node::getName, Comparator.nullsLast(String::compareTo)));
        if (nodes.isEmpty()) return requested;
        if (requested == null || requested.getName() == null) return nodes.getFirst();
        Node inGraph = g.getNode(requested.getName());
        return (inGraph != null) ? inGraph : nodes.getFirst();
    }

    private static Graph safeCopy(Graph g) {
        if (g == null) return null;
        try { return g.copy(); } catch (Throwable t) { return new EdgeListGraph(g); }
    }

    private static TableCellRenderer modelPRenderer() {
        return new DefaultTableCellRenderer() {
            @Override
            public void setValue(Object value) {
                if (value instanceof Number n) {
                    double d = n.doubleValue();
                    setText(Double.isNaN(d) ? "" : MODEL_P_FORMAT.format(d));
                    setHorizontalAlignment(SwingConstants.RIGHT);
                } else {
                    setText("");
                }
            }
        };
    }

    // =========================================================================
    // Inner table model
    // =========================================================================

    private static final class CandidateTableModel extends AbstractTableModel {
        static final int COL_EDIT = 0, COL_BASE = 1, COL_AFTER = 2, COL_DELTA = 3,
                COL_NODE_P = 4, COL_MODEL_P = 5, COL_EDGES = 6, COL_APPLY = 7;

        private final String[] cols = {"Edit", "Baseline", "After", "Δ", "Node-P", "Model-P", "Edges", "Apply"};
        private List<ScoredCandidate> rows = new ArrayList<>();

        void set(List<ScoredCandidate> rows) {
            this.rows = (rows == null) ? new ArrayList<>() : new ArrayList<>(rows);
            sortByCanonicalOrder();
        }

        void sortByCanonicalOrder() {
            this.rows.sort(VertexRepairSearch.CANONICAL_TABLE_ORDER);
            fireTableDataChanged();
        }

        CandidateEdit getCandidate(int row) { return rows.get(row).edit(); }

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return cols.length; }
        @Override public String getColumnName(int col) { return cols[col]; }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            ScoredCandidate r = rows.get(rowIndex);
            return switch (columnIndex) {
                case COL_EDIT    -> r.edit().description();
                case COL_BASE    -> r.violationsBaseline();
                case COL_AFTER   -> r.violationsAfter();
                case COL_DELTA   -> r.delta();
                case COL_NODE_P  -> r.nodePAfter();
                case COL_MODEL_P -> r.modelPAfter();
                case COL_EDGES   -> r.edgesAfter();
                case COL_APPLY   -> r.edit().isNoOp() ? "" : "Accept";
                default -> "";
            };
        }

        @Override
        public Class<?> getColumnClass(int col) {
            return switch (col) {
                case COL_EDIT -> String.class;
                case COL_BASE, COL_AFTER, COL_DELTA, COL_EDGES -> Integer.class;
                case COL_NODE_P, COL_MODEL_P -> Double.class;
                default -> Object.class;
            };
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == COL_APPLY && !rows.get(rowIndex).edit().isNoOp();
        }
    }

    // =========================================================================
    // Inner button renderer / editor
    // =========================================================================

    private static final class ButtonRenderer extends JButton implements TableCellRenderer {
        ButtonRenderer() { setOpaque(true); }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int column) {
            setText(value == null ? "" : value.toString());
            setEnabled(value != null && !value.toString().isEmpty());
            return this;
        }
    }

    private static final class ButtonEditor extends DefaultCellEditor {
        private final JButton button = new JButton();
        private int editingRow = -1;

        ButtonEditor(ButtonEditor.RowAction onClick) {
            super(new JTextField());
            button.setBackground(Color.WHITE);
            setClickCountToStart(1);
            button.addActionListener(e -> {
                final int row = editingRow;
                fireEditingStopped();
                SwingUtilities.invokeLater(() -> onClick.run(row));
            });
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value,
                                                     boolean isSelected, int row, int column) {
            this.editingRow = row;
            button.setText(value == null ? "" : value.toString());
            button.setEnabled(value != null && !value.toString().isEmpty());
            return button;
        }

        @Override public Object getCellEditorValue() { return button.getText(); }

        interface RowAction { void run(int row); }
    }
}
