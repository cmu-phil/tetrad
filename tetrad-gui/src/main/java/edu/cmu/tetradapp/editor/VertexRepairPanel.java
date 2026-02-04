package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.test.CachedIndependenceQueries;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetradapp.model.VertexCheckIndTestModel;
import edu.cmu.tetradapp.workbench.GraphWorkbench;

import javax.swing.*;
import javax.swing.Timer;
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
 * Interactive panel for locally repairing a causal graph around a selected node {@code x}
 * using feedback from the Vertex Checker.
 *
 * <p>
 * The panel enumerates a conservative set of candidate edge edits involving {@code x}
 * (edge additions, removals, and replacements consistent with the chosen graph type),
 * applies each candidate to a copy of the current graph, and scores the result using
 * Markov-checker diagnostics derived from conditional independence tests.
 * </p>
 */
public final class VertexRepairPanel extends JPanel {

    private static final String CARD_TABLE = "table";
    private static final String CARD_NONE = "none";
    private static final DecimalFormat MODEL_P_FORMAT = new DecimalFormat("0.0000");
    private static final int DEFAULT_MODELP_TOP_K = 25;

    // ---- Preferences (persist α and model-P top-K) ----
    private static final Preferences PREFS = Preferences.userRoot().node("edu/cmu/tetradapp/editor/VertexRepairPanel");
    private static final String PREF_ALPHA = "markovAlpha";
    private static final String PREF_MODEL_P_TOP_K = "modelPTopK";
    private static final double DEFAULT_ALPHA = 0.01;

    private final VertexCheckIndTestModel baseModel;
    private Node x; // selected node (changes via dropdown)
    private final Deque<Graph> history = new ArrayDeque<>();

    // UI
    private final JComboBox<RepairGraphType> graphTypeCombo = new JComboBox<>(RepairGraphType.values());
    private final JButton searchButton = new JButton();              // label set after x is known
    private final JButton modelBestButton = new JButton("Do your model best!");
    private final JButton backButton = new JButton("Undo");
    private final JButton showGraphButton = new JButton("Graph");
    private final JLabel statusLabel = new JLabel(" ");
    private final JTable resultsTable = new JTable();
    private final CandidateTableModel resultsModel = new CandidateTableModel();
    private final JPanel resultsCard = new JPanel(new CardLayout());

    // Sorting/filtering UI
    private final JCheckBox markovAlphaFilter =
            new JCheckBox("Hide rows with Model-P or Node-P < α");
    private final JTextField alphaField = new JTextField("0.01", 6);
    private final JTextField modelPTopKField = new JTextField(String.valueOf(DEFAULT_MODELP_TOP_K), 5);

    // debounce timers so we don’t write prefs on every keystroke
    private final Timer alphaSaveTimer = new Timer(350, e -> saveAlphaPref());
    private final Timer topModelPaveTimer = new Timer(350, e -> saveTopKPref());

    private final CachedIndependenceQueries Q;
    private final VertexCheckIndTestModel model;
    private Graph workingGraph;
    private Knowledge knowledge = new Knowledge();

    // Keep a handle to the sorter so we can change filter/sort dynamically
    private TableRowSorter<CandidateTableModel> resultsSorter;

    private volatile int modelPTopK = DEFAULT_MODELP_TOP_K;

    // --- Watch dialog state (one at a time) ---
    private volatile SwingWorker<?, ?> activeWorker;
    private volatile JDialog watchDialog;

    // Node dropdown (replaces "Adjust Node" button)
    private final JComboBox<Node> nodeCombo = new JComboBox<>();

//    public VertexRepairPanel(VertexCheckEditor editor, Node x) {
//        super(new BorderLayout());
//        this.x = Objects.requireNonNull(x, "x");
//
//        this.baseModel = Objects.requireNonNull(editor.getIndTestModel(), "editor.getIndTestModel()");
//        this.Q = Objects.requireNonNull(editor.getCachedQueries(), "editor.getCachedQueries()");
//        this.workingGraph = safeCopy(baseModel.getGraph());
//        this.model = editor.getIndTestModel();
//
//        // Label buttons now that x is known
//        this.searchButton.setText("Adjust " + x.getName());
//
//        // Initialize graph type combo options from graph legality
//        initGraphTypeComboFromGraph(this.workingGraph);
//
//        buildUI();
//
//        initPrefTimers();
//        loadPrefsIntoUi();
//
//        wireActions();
//        updateButtons();
//
//        setPreferredSize(new Dimension(650, 600));
//    }

    public VertexRepairPanel(VertexCheckEditor editor, Node x) {
        super(new BorderLayout());

        this.baseModel = Objects.requireNonNull(editor.getIndTestModel(), "editor.getIndTestModel()");
        this.Q = Objects.requireNonNull(editor.getCachedQueries(), "editor.getCachedQueries()");
        this.model = editor.getIndTestModel();

        // Working graph
        this.workingGraph = safeCopy(baseModel.getGraph());

        // --- choose initial x ---
        this.x = resolveInitialNode(this.workingGraph, x);

        // Label buttons now that x is known
        this.searchButton.setText("Adjust " + this.x.getName());  // you may rename later if desired

        // Initialize graph type combo options from graph legality
        initGraphTypeComboFromGraph(this.workingGraph);

        buildUI();          // will populate nodeCombo based on workingGraph + x
        initPrefTimers();
        loadPrefsIntoUi();

        wireActions();
        updateButtons();

        // Auto-populate table for initially selected node
        SwingUtilities.invokeLater(() -> startWatched("Searching", this::runSearchWatched, null));

        setPreferredSize(new Dimension(650, 600));
    }

    private Node resolveInitialNode(Graph g, Node requested) {
        if (g == null) return requested; // nothing better we can do
        List<Node> nodes = new ArrayList<>(g.getNodes());
        nodes.sort(Comparator.comparing(Node::getName, Comparator.nullsLast(String::compareTo)));

        if (nodes.isEmpty()) return requested;

        // if null or not in graph, choose first node
        if (requested == null || requested.getName() == null) return nodes.get(0);

        Node inGraph = g.getNode(requested.getName());
        return (inGraph != null) ? inGraph : nodes.get(0);
    }

    // ---------------------------------------------------------------------
    // Keys / de-dup helpers
    // ---------------------------------------------------------------------

    /**
     * Canonical key for de-duping implied facts by names: (X,Y unordered; Z sorted).
     * IMPORTANT: This is independent of the CachedIndependenceQueries cache key; it is used
     * only for locality-based merging of per-vertex contributions.
     */
    public static String factKey(IndependenceFact f) {
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
        if (f.getZ() != null) {
            for (Node n : f.getZ()) {
                if (n != null && n.getName() != null) z.add(n.getName());
            }
        }
        Collections.sort(z);

        return a + "|" + b + "|" + String.join(",", z);
    }

    private static boolean edgeStructurallyEqual(Edge a, Edge b, Node x, Node y) {
        if (a == null || b == null) return false;

        Endpoint aX, aY;
        if (a.getNode1().equals(x) && a.getNode2().equals(y)) {
            aX = a.getEndpoint1();
            aY = a.getEndpoint2();
        } else if (a.getNode1().equals(y) && a.getNode2().equals(x)) {
            aX = a.getEndpoint2();
            aY = a.getEndpoint1();
        } else return false;

        Endpoint bX, bY;
        if (b.getNode1().equals(x) && b.getNode2().equals(y)) {
            bX = b.getEndpoint1();
            bY = b.getEndpoint2();
        } else if (b.getNode1().equals(y) && b.getNode2().equals(x)) {
            bX = b.getEndpoint2();
            bY = b.getEndpoint1();
        } else return false;

        return aX == bX && aY == bY;
    }

    private static List<CandidateEdit> dedupCandidateEdits(List<CandidateEdit> edits) {
        if (edits == null || edits.isEmpty()) return List.of();
        Map<String, CandidateEdit> seen = new LinkedHashMap<>();
        for (CandidateEdit ce : edits) {
            if (ce == null) continue;
            String key = ce.key();
            if (key == null) key = UUID.randomUUID().toString();
            seen.putIfAbsent(key, ce);
        }
        return new ArrayList<>(seen.values());
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

    private static double parseAlpha(String s, double fallback) {
        try {
            double a = Double.parseDouble(s.trim());
            if (Double.isNaN(a) || a <= 0 || a >= 1) return fallback;
            return a;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static int parseTopK(String s, int fallback) {
        try {
            int k = Integer.parseInt(s.trim());
            return (k <= 0 ? fallback : k);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    /**
     * Caller reads this after dialog closes.
     */
    public Graph getGraph() {
        return workingGraph;
    }

    // ---------------------------------------------------------------------
    // UI
    // ---------------------------------------------------------------------

    private void buildUI() {
        JPanel controls = new JPanel(new GridBagLayout());
//        controls.setBorder(new TitledBorder("Repair Node: " + x.getName()));
        controls.setBorder(new TitledBorder("Repair Model"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
//        c.gridx = 0;
//        c.gridy = 0;
//        c.anchor = GridBagConstraints.WEST;
//
//        controls.add(new JLabel("Graph type:"), c);
//
//        c.gridx = 1;
//        c.fill = GridBagConstraints.HORIZONTAL;
//        c.weightx = 1;
//        controls.add(graphTypeCombo, c);

//        // Row: Node Dropdown
//        c.gridx = 0;
//        c.gridy = 1;
//        c.gridwidth = 1;
//        c.fill = GridBagConstraints.NONE;
//        c.weightx = 0;
//        controls.add(new JLabel("Node:"), c);
//
//        c.gridx = 1;
//        c.fill = GridBagConstraints.HORIZONTAL;
//        c.weightx = 1;
//        populateNodeCombo();     // fills nodeCombo and selects this.x if present
//        controls.add(nodeCombo, c);
//
//        // Row 2: Markov alpha filter
//        c.gridx = 0;
//        c.gridy = 1;
//        c.gridwidth = 1;
//        c.fill = GridBagConstraints.NONE;
//        c.weightx = 0;
//        controls.add(markovAlphaFilter, c);
//
//        c.gridx = 1;
//        c.fill = GridBagConstraints.HORIZONTAL;
//        c.weightx = 1;
//
//        JPanel alphaPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
//        alphaPanel.add(new JLabel("α:"));
//        alphaPanel.add(alphaField);
//
//        alphaPanel.add(new JLabel("Model-P top-K:"));
//        alphaPanel.add(modelPTopKField);
//
//        controls.add(alphaPanel, c);

        // Row 1: Node Dropdown
        c.gridx = 0;
        c.gridy = 1;
        c.gridwidth = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 0;
        controls.add(new JLabel("Node:"), c);

        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        populateNodeCombo();     // fills nodeCombo and selects this.x if present
        controls.add(nodeCombo, c);

        c.gridx = 2;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        controls.add(graphTypeCombo, c);

        // Row 2: Markov alpha filter
        c.gridx = 0;
        c.gridy = 2;             // <-- FIX: was 1
        c.gridwidth = 1;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        controls.add(markovAlphaFilter, c);

        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        JPanel alphaPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        alphaPanel.add(new JLabel("α:"));
        alphaPanel.add(alphaField);
        alphaPanel.add(new JLabel("Model-P top-K:"));
        alphaPanel.add(modelPTopKField);
        controls.add(alphaPanel, c);

        // Buttons row
        JPanel topButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        topButtons.add(backButton);
        topButtons.add(showGraphButton);
        topButtons.add(searchButton);
        topButtons.add(modelBestButton);

        JPanel north = new JPanel(new BorderLayout());
        north.add(controls, BorderLayout.CENTER);
        north.add(topButtons, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);

        // Results area
        resultsTable.setModel(resultsModel);
        resultsTable.setRowHeight(24);
        resultsTable.setFillsViewportHeight(true);

        resultsTable.getColumnModel().getColumn(CandidateTableModel.COL_APPLY)
                .setCellRenderer(new ButtonRenderer());

        resultsTable.getColumnModel().getColumn(CandidateTableModel.COL_APPLY)
                .setCellEditor(new ButtonEditor(row -> {
                    if (row < 0) return;
                    int modelRow = resultsTable.convertRowIndexToModel(row);
                    CandidateEdit cand = resultsModel.getCandidate(modelRow);
                    applyCandidate(cand);
                    startWatched("Searching", this::runSearchWatched, null);
                }));

        resultsTable.setTransferHandler(new DefaultTableTransferHandler(0));

        TableColumnModel cm = resultsTable.getColumnModel();

        resultsTable.getColumnModel().getColumn(CandidateTableModel.COL_MODEL_P).setCellRenderer(modelPRenderer());
        resultsTable.getColumnModel().getColumn(CandidateTableModel.COL_NODE_P).setCellRenderer(modelPRenderer());

        // Column indices assumed; adjust if needed
        TableColumn editIndex = cm.getColumn(0);
        TableColumn baselineIndex = cm.getColumn(1);
        TableColumn afterIndex = cm.getColumn(2);
        TableColumn deltaIndex = cm.getColumn(3);
        TableColumn nodePIndex = cm.getColumn(4);
        TableColumn modelPIndex = cm.getColumn(5);
        TableColumn edgesIndex = cm.getColumn(6);
        TableColumn applyIndex = cm.getColumn(7);

        // numeric columns
        baselineIndex.setPreferredWidth(50);
        baselineIndex.setMinWidth(50);
        baselineIndex.setMaxWidth(50);

        afterIndex.setPreferredWidth(50);
        afterIndex.setMinWidth(50);
        afterIndex.setMaxWidth(50);

        deltaIndex.setMinWidth(50);
        deltaIndex.setMaxWidth(50);
        deltaIndex.setPreferredWidth(50);

        nodePIndex.setMinWidth(70);
        nodePIndex.setMaxWidth(70);
        nodePIndex.setPreferredWidth(70);

        modelPIndex.setMinWidth(70);
        modelPIndex.setMaxWidth(70);
        modelPIndex.setPreferredWidth(70);

        edgesIndex.setMinWidth(50);
        edgesIndex.setMaxWidth(50);
        edgesIndex.setPreferredWidth(50);

        applyIndex.setMinWidth(70);

        // Fact column: stretch
        editIndex.setPreferredWidth(1000);

        resultsSorter = new TableRowSorter<>(resultsModel);
        resultsTable.setRowSorter(resultsSorter);

        // Model-P comparator with NaN last
        resultsSorter.setComparator(CandidateTableModel.COL_MODEL_P, (a, b) -> {
            double da = (a instanceof Number na) ? na.doubleValue() : Double.NaN;
            double db = (b instanceof Number nb) ? nb.doubleValue() : Double.NaN;

            boolean aNaN = Double.isNaN(da);
            boolean bNaN = Double.isNaN(db);

            if (aNaN && bNaN) return 0;
            if (aNaN) return 1;
            if (bNaN) return -1;

            return Double.compare(da, db);
        });

        resultsSorter.setComparator(CandidateTableModel.COL_NODE_P, (a, b) -> {
            double da = (a instanceof Number na) ? na.doubleValue() : Double.NaN;
            double db = (b instanceof Number nb) ? nb.doubleValue() : Double.NaN;
            boolean aNaN = Double.isNaN(da);
            boolean bNaN = Double.isNaN(db);
            if (aNaN && bNaN) return 0;
            if (aNaN) return 1;
            if (bNaN) return -1;
            return Double.compare(da, db);
        });

        resultsSorter.setComparator(CandidateTableModel.COL_EDGES, Comparator.comparingInt(a -> ((Number) a).intValue()));
        resultsSorter.setComparator(CandidateTableModel.COL_AFTER, Comparator.comparingInt(a -> ((Number) a).intValue()));
        resultsSorter.setComparator(CandidateTableModel.COL_DELTA, Comparator.comparingInt(a -> ((Number) a).intValue()));

        applySortAndFilter();

        JPanel tablePanel = new JPanel(new BorderLayout());
        tablePanel.add(new JScrollPane(resultsTable), BorderLayout.CENTER);
        tablePanel.add(statusLabel, BorderLayout.SOUTH);

        JPanel nonePanel = new JPanel(new BorderLayout());
        JLabel none = new JLabel("No candidate repairs computed yet.", SwingConstants.CENTER);
        nonePanel.add(none, BorderLayout.CENTER);

        resultsCard.add(nonePanel, CARD_NONE);
        resultsCard.add(tablePanel, CARD_TABLE);
        ((CardLayout) resultsCard.getLayout()).show(resultsCard, CARD_NONE);

        add(resultsCard, BorderLayout.CENTER);
    }

    private void populateNodeCombo() {
        DefaultComboBoxModel<Node> m = new DefaultComboBoxModel<>();

        if (workingGraph != null) {
            List<Node> nodes = new ArrayList<>(workingGraph.getNodes());
            nodes.sort(Comparator.comparing(Node::getName, Comparator.nullsLast(String::compareTo)));
            for (Node n : nodes) m.addElement(n);
        }

        nodeCombo.setModel(m);

        // select x if possible
        if (x != null && x.getName() != null && workingGraph != null) {
            Node inGraph = workingGraph.getNode(x.getName());
            if (inGraph != null) nodeCombo.setSelectedItem(inGraph);
            else if (m.getSize() > 0) nodeCombo.setSelectedIndex(0);
        } else if (m.getSize() > 0) {
            nodeCombo.setSelectedIndex(0);
        }

        // keep x in sync
        Object sel = nodeCombo.getSelectedItem();
        if (sel instanceof Node n) {
            x = n;
            searchButton.setText("Adjust " + x.getName());
        }
    }

    private void applySortAndFilter() {
        if (resultsSorter == null) return;

        // 1) Filter (optional)
        if (markovAlphaFilter.isSelected()) {
            double alpha = parseAlpha(alphaField.getText(), DEFAULT_ALPHA);

            resultsSorter.setRowFilter(new RowFilter<>() {
                @Override
                public boolean include(Entry<? extends CandidateTableModel, ? extends Integer> e) {

                    double modelP = ((Double) e.getValue(CandidateTableModel.COL_MODEL_P)).doubleValue();
                    double nodeP  = ((Double) e.getValue(CandidateTableModel.COL_NODE_P)).doubleValue();

                    // Keep if either wasn't computed (NaN); otherwise require BOTH >= alpha.
                    return (Double.isNaN(modelP) || modelP >= alpha)
                            && (Double.isNaN(nodeP)  || nodeP  >= alpha);
                }
            });
        } else {
            resultsSorter.setRowFilter(null);
        }

        // 2) Sort keys (lexicographic)
        resultsSorter.setSortKeys(List.of(
                new RowSorter.SortKey(CandidateTableModel.COL_EDGES, SortOrder.ASCENDING),
                new RowSorter.SortKey(CandidateTableModel.COL_MODEL_P, SortOrder.DESCENDING),
                new RowSorter.SortKey(CandidateTableModel.COL_AFTER, SortOrder.ASCENDING),
                new RowSorter.SortKey(CandidateTableModel.COL_DELTA, SortOrder.ASCENDING)
        ));

        resultsSorter.sort();
    }

    private void wireActions() {
        backButton.addActionListener(e -> goBack());
        showGraphButton.addActionListener(e -> showGraphDialog());

        // Adjust selected node x (the panel’s focus node)
        searchButton.addActionListener(e -> startWatched("Searching", this::runSearchWatched, null));

        nodeCombo.addActionListener(e -> {
            Object sel = nodeCombo.getSelectedItem();
            if (!(sel instanceof Node n)) return;

            // Resolve to node in current workingGraph (important after edits/canonicalization)
            Node inGraph = (workingGraph != null && n.getName() != null)
                    ? workingGraph.getNode(n.getName())
                    : null;

            if (inGraph == null) {
                // fallback: pick first node
                x = resolveInitialNode(workingGraph, null);
                populateNodeCombo(); // sync UI
            } else {
                x = inGraph;
            }

            searchButton.setText("Adjust " + x.getName());

            // Auto-recompute table whenever node changes
            if (activeWorker == null) {
                startWatched("Searching", this::runSearchWatched, null);
            }
        });

        // Auto model best
        modelBestButton.addActionListener(e -> startWatched("Auto-repairing", this::runModelBestWatched, null));

        markovAlphaFilter.addActionListener(e -> applySortAndFilter());
        markovAlphaFilter.setSelected(true);

        alphaField.addActionListener(e -> {
            applySortAndFilter();
            saveAlphaPref();
        });

        alphaField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                applySortAndFilter();
                alphaSaveTimer.restart();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                applySortAndFilter();
                alphaSaveTimer.restart();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                applySortAndFilter();
                alphaSaveTimer.restart();
            }
        });

        modelPTopKField.addActionListener(e -> {
            modelPTopK = parseTopK(modelPTopKField.getText(), DEFAULT_MODELP_TOP_K);
            applySortAndFilter();
            saveTopKPref();
        });

        modelPTopKField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                modelPTopK = parseTopK(modelPTopKField.getText(), DEFAULT_MODELP_TOP_K);
                topModelPaveTimer.restart();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                modelPTopK = parseTopK(modelPTopKField.getText(), DEFAULT_MODELP_TOP_K);
                topModelPaveTimer.restart();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                modelPTopK = parseTopK(modelPTopKField.getText(), DEFAULT_MODELP_TOP_K);
                topModelPaveTimer.restart();
            }
        });
    }

    private void updateButtons() {
        backButton.setEnabled(!history.isEmpty());
        boolean busy = (activeWorker != null);
        searchButton.setEnabled(!busy);
        modelBestButton.setEnabled(!busy);
    }

    // ---------------------------------------------------------------------
    // Search logic (watched, background)
    // ---------------------------------------------------------------------

    private void runSearchWatched() {
        RepairGraphType gt = (RepairGraphType) graphTypeCombo.getSelectedItem();
        Graph base = safeCopy(workingGraph);

        if (stopRequested()) return;

        if (gt == RepairGraphType.CPDAG || gt == RepairGraphType.PDAG) {
            base = canonicalizeToCpdagOrNull(base);
            if (base == null) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Current graph has no consistent CPDAG extension.");
                    ((CardLayout) resultsCard.getLayout()).show(resultsCard, CARD_NONE);
                });
                return;
            }
        } else if (gt == RepairGraphType.PAG) {
            base = canonicalizeToPagOrNull(base);
            if (base == null) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Current graph has no consistent PAG extension.");
                    ((CardLayout) resultsCard.getLayout()).show(resultsCard, CARD_NONE);
                });
                return;
            }
        }

        if (knowledge != null && knowledge.isViolatedBy(base)) {
            SwingUtilities.invokeLater(() -> {
                statusLabel.setText("Current graph violates the knowledge base.");
                ((CardLayout) resultsCard.getLayout()).show(resultsCard, CARD_NONE);
            });
            return;
        }

        List<CandidateEdit> candidates = enumerateCandidates(base, x, gt);
        candidates = new ArrayList<>(candidates);
        if (candidates.stream().noneMatch(CandidateEdit::isNoOp)) {
            candidates.add(0, CandidateEdit.noOp());
        }

        if (stopRequested()) return;

        GlobalEvalCache baseCache = buildBaselineCache(base);
        GraphEval baseEval = evalGraphLocality(base, baseCache, base, Set.of(), true);

        int baseline = baseEval.violations();
        double baselineModelP = baseEval.modelP();

        Map<String, Graph> candGraphByKey = new HashMap<>();
        List<ScoredCandidate> scored = new ArrayList<>();

        // PASS 1: after + Node-P + edges for all candidates
        for (CandidateEdit cand : candidates) {
            if (stopRequested()) return;

            Graph finalBase = base;
            Graph g2 = candGraphByKey.computeIfAbsent(cand.key(), k -> buildCandidateGraph(finalBase, cand, gt));
            if (g2 == null) continue;

            boolean useLocality = (gt == RepairGraphType.DAG || gt == RepairGraphType.CPDAG || gt == RepairGraphType.PDAG);
            Set<String> affected = affectedVertices(base, cand, x, g2);

            int after = useLocality
                    ? evalGraphLocality(base, baseCache, g2, affected, false).violations()
                    : evalViolationsOnly(g2);

            double nodePAfter = nodePValue(g2, x);
            int edgesAfter = g2.getNumEdges();

            scored.add(new ScoredCandidate(cand, baseline, after, nodePAfter, Double.NaN, edgesAfter));
        }

        if (stopRequested()) return;

        // PASS 2: compute Model-P for the top-K rows *as the table would surface them*
        // when Model-P is mostly unknown (i.e., table order ignoring Model-P).
        List<ScoredCandidate> rankedForTopK = new ArrayList<>(scored);
        rankedForTopK.sort(tableOrderIgnoringModelP());

        if (!rankedForTopK.isEmpty()) {
            int k = Math.min(modelPTopK, rankedForTopK.size());
            Map<String, Double> modelPByEditKey = new HashMap<>(k * 2);

            for (int i = 0; i < k; i++) {
                if (stopRequested()) return;

                ScoredCandidate sc = rankedForTopK.get(i);
                CandidateEdit cand = sc.edit();

                Graph g2 = candGraphByKey.get(cand.key());
                if (g2 == null) continue;

                double modelPAfter = evalGraphOnce(g2).modelP();
                modelPByEditKey.put(cand.key(), modelPAfter);
            }

            if (!modelPByEditKey.isEmpty()) {
                List<ScoredCandidate> patched = new ArrayList<>(scored.size());
                for (ScoredCandidate sc : scored) {
                    Double mp = modelPByEditKey.get(sc.edit().key());
                    patched.add(mp == null ? sc : new ScoredCandidate(
                            sc.edit(), sc.baseline(), sc.after(), sc.nodePAfter(), mp, sc.edgesAfter()
                    ));
                }
                scored = patched;
            }
        }

        List<ScoredCandidate> rankedForStatus = new ArrayList<>(scored);
//        rankedForStatus.sort(candidateRankingForTopK());
        rankedForStatus.sort(tableOrderFull());
        ScoredCandidate bestCand = rankedForStatus.isEmpty() ? null : rankedForStatus.get(0);

        List<ScoredCandidate> finalScored = scored;
        ScoredCandidate finalBest = bestCand;
        SwingUtilities.invokeLater(() -> {
            resultsModel.set(finalScored);
            applySortAndFilter();

            NumberFormat fmt = new DecimalFormat("0.0000");
            if (finalScored.isEmpty()) {
                statusLabel.setText("No legal candidate edits found.");
                ((CardLayout) resultsCard.getLayout()).show(resultsCard, CARD_NONE);
            } else {
                int best = (finalBest == null) ? baseline : finalBest.violationsAfter();
                String modelPBestStr = (finalBest == null || Double.isNaN(finalBest.modelPAfter()))
                        ? "n/a"
                        : fmt.format(finalBest.modelPAfter());

                statusLabel.setText(
                        "Baseline violations: " + baseline +
                                " | Best: " + best +
                                " | Model-P: " + fmt.format(baselineModelP) + " → " + modelPBestStr
                );
                ((CardLayout) resultsCard.getLayout()).show(resultsCard, CARD_TABLE);
            }
        });
    }

    private Comparator<ScoredCandidate> candidateRankingForTopK() {
        // Keep exactly your existing “rankedForTopK” behavior:
        return Comparator
                .comparingInt(ScoredCandidate::violationsAfter)
                .thenComparingInt(ScoredCandidate::edgesAfter)
                .thenComparing(Comparator.comparingDouble(ScoredCandidate::nodePAfter).reversed())
                .thenComparingInt(ScoredCandidate::delta);
    }

    // ---------------------------------------------------------------------
    // Auto model best
    // ---------------------------------------------------------------------

    private void runModelBestWatched() {
        // Single-undo checkpoint for the whole run
        Graph checkpoint = safeCopy(workingGraph);

        final double alpha = parseAlpha(alphaField.getText(), DEFAULT_ALPHA);
        final RepairGraphType gt = (RepairGraphType) graphTypeCombo.getSelectedItem();

        int editsApplied = 0;
        final int MAX_EDITS = 500;            // global safety cap
        final int MAX_STEPS_PER_NODE = 200;   // per-node safety cap

        vlog("==================================================");
        vlog("AUTO-REPAIR (single sweep) (alpha=%.4g, type=%s)", alpha, String.valueOf(gt));
        vlog("==================================================");

        // One sweep only
        List<Node> nodes = new ArrayList<>(workingGraph.getNodes());
        nodes.sort(Comparator.comparing(Node::getName, Comparator.nullsLast(String::compareTo)));

        for (Node v0 : nodes) {
            if (stopRequested()) return;
            if (editsApplied >= MAX_EDITS) break;

            Node center = (v0 == null || v0.getName() == null) ? null : workingGraph.getNode(v0.getName());
            if (center == null) continue;

            // Rule 0: if it's not broke, don't fix it
            if (nodeLooksOk(workingGraph, center, alpha)) {
                vlog("Skipping node %s (nodeP >= alpha in current graph).", center.getName());
                continue;
            }

            vlog("--------------------------------------------------");
            vlog("Editing node: %s", center.getName());

            // Per-node loop: keep making moves until rule says stop
            Set<String> seenSignatures = new HashSet<>();
            int nodeSteps = 0;

            while (editsApplied < MAX_EDITS) {
                if (stopRequested()) return;

                nodeSteps++;
                if (nodeSteps > MAX_STEPS_PER_NODE) {
                    vlog("STOP node %s: hit MAX_STEPS_PER_NODE=%d", center.getName(), MAX_STEPS_PER_NODE);
                    break;
                }

                // Prevent infinite oscillation on a single node
                String sig = graphSignature(workingGraph);
                if (!seenSignatures.add(sig)) {
                    vlog("STOP node %s: detected cycle (graph signature repeated).", center.getName());
                    break;
                }

                SearchPack pack = computeCandidatesForNode(workingGraph, center, gt);
                if (pack == null || pack.scored == null || pack.scored.isEmpty()) {
                    vlog("No candidates for node %s", center.getName());
                    break;
                }

                // 1) Alpha-filtered presorted list (table order)
                List<ScoredCandidate> alphaList = alphaFilteredPresortedList(pack, alpha);

                vlog("Alpha-filtered rows for %s: %d", center.getName(), alphaList.size());

                if (!alphaList.isEmpty()) {

                    // Only accept alpha-filtered moves that don't worsen violations.
                    // Prefer strict improvement first; otherwise allow tie-with-sparser if you want.
                    ScoredCandidate chosen = pickBestNonWorsening(alphaList);

                    if (chosen == null) {
                        vlog("STOP node %s: alpha-filtered rows exist but none are non-worsening (delta<=0).", center.getName());
                        break;
                    }

                    vlog("Chosen filtered move: %s | after=%d delta=%d edges=%d nodeP=%.4g modelP=%.4g",
                            chosen.edit().description(),
                            chosen.after(),
                            chosen.delta(),
                            chosen.edgesAfter(),
                            chosen.nodePAfter(),
                            chosen.modelPAfter());

//                    boolean ok = applyCandidateInternal(chosen.edit(), false, false);

                    boolean ok = tryMoveWithGuards(workingGraph, center, chosen.edit(), gt, alpha);

                    if (!ok) {
                        vlog("STOP node %s: chosen filtered move did NOT change graph.", center.getName());
                        break;
                    }

                    editsApplied++;
                    int finalEditsApplied = editsApplied;
                    SwingUtilities.invokeLater(() ->
                            statusLabel.setText("Auto-repair: applied " + finalEditsApplied + " edits..."));

                    vlog("APPLIED. Recomputing table for %s...", center.getName());
                    continue;
                }

                // 2) If filtered list empty, do fallback loop:
                // (1) min delta (if <0) apply
                // (2) max nodeP (if >0) apply
                // (3) max modelP (if >0) apply
                // Repeat until max nodeP>0 AND max modelP>0 OR no move possible.

                boolean moved = false;

                ScoredCandidate minDelta = pickMinDelta(pack);
                if (minDelta != null) {
                    vlog("Fallback min-delta candidate: %s | delta=%d nodeP=%s modelP=%s",
                            minDelta.edit().description(),
                            minDelta.delta(),
                            fmtP(minDelta.nodePAfter()),
                            fmtP(minDelta.modelPAfter()));

                    if (minDelta.delta() < 0) {
//                        boolean ok = applyCandidateInternal(minDelta.edit(), false, false);

                        boolean ok = tryMoveWithGuards(workingGraph, center, minDelta.edit(), gt, alpha);

                        if (ok) {
                            moved = true;
                            editsApplied++;
                            vlog("APPLIED fallback delta<0 move.");
                        } else {
                            vlog("Rejected fallback delta<0 move (no change).");
                        }
                    }
                }

                boolean allowPChasing = false; // start false; you can add a UI toggle later

                if (!moved && allowPChasing) {
                    ScoredCandidate maxNodeP = pickMaxNodeP(pack);
                    if (maxNodeP != null) {
                        vlog("Fallback max-nodeP candidate: %s | nodeP=%s",
                                maxNodeP.edit().description(),
                                fmtP(maxNodeP.nodePAfter()));

                        // NaN does NOT qualify as > 0
                        if (isUsableP(maxNodeP.nodePAfter(), alpha)) {
                            boolean ok = applyCandidateInternal(maxNodeP.edit(), false, false);
                            if (ok) {
                                moved = true;
                                editsApplied++;
                                vlog("APPLIED fallback nodeP>0 move.");
                            } else {
                                vlog("Rejected fallback nodeP>0 move (no change).");
                            }
                        }
                    }
                }

                if (!moved) {
                    ScoredCandidate maxModelP = pickMaxModelP(pack);
                    if (maxModelP != null) {
                        vlog("Fallback max-modelP candidate: %s | modelP=%s",
                                maxModelP.edit().description(),
                                fmtP(maxModelP.modelPAfter()));

                        if (isUsableP(maxModelP.modelPAfter(), alpha)) {
                            boolean ok = applyCandidateInternal(maxModelP.edit(), false, false);
                            if (ok) {
                                moved = true;
                                editsApplied++;
                                vlog("APPLIED fallback modelP>0 move.");
                            } else {
                                vlog("Rejected fallback modelP>0 move (no change).");
                            }
                        }
                    }
                }

                if (!moved) {
                    vlog("STOP node %s: no fallback move possible.", center.getName());
                    break;
                }

                // After a fallback move, check fallback stop condition
                SearchPack pack2 = computeCandidatesForNode(workingGraph, center, gt);
                if (pack2 == null || pack2.scored == null || pack2.scored.isEmpty()) {
                    vlog("STOP node %s after fallback: no candidates after move.", center.getName());
                    break;
                }

                double maxNode = maxNodePValue(pack2);
                double maxModel = maxModelPValue(pack2);

                vlog("After fallback move, maxima: maxNodeP=%.4g maxModelP=%.4g", maxNode, maxModel);

                // Stop fallback once either criterion is “usable” under alpha,
                // or if you simply can’t apply any further fallback move.
                if (isUsableP(maxNode, alpha) && isUsableP(maxModel, alpha)) {
                    vlog("STOP fallback for node %s: both maxNodeP>=alpha and maxModelP>=alpha.", center.getName());
                    break;
                }

                vlog("Continuing fallback loop for node %s...", center.getName());
            }

            vlog("Finished node: %s", center.getName());
        }

        final int finalEdits = editsApplied;
        SwingUtilities.invokeLater(() -> {
            history.clear();
            history.push(checkpoint);
            updateButtons();
            statusLabel.setText("Auto-repair applied " + finalEdits + " edits.");
            // Refresh panel table for the panel's x (nice UX)
            startWatched("Searching", this::runSearchWatched, null);
        });
    }

    private ScoredCandidate pickBestNonWorsening(List<ScoredCandidate> list) {
        if (list == null || list.isEmpty()) return null;

        // Prefer delta < 0 moves; otherwise allow delta == 0 with fewer edges.
        List<ScoredCandidate> improving = new ArrayList<>();
        List<ScoredCandidate> tie = new ArrayList<>();

        for (ScoredCandidate sc : list) {
            if (sc == null || sc.edit().isNoOp()) continue;
            if (sc.delta() < 0) improving.add(sc);
            else if (sc.delta() == 0) tie.add(sc);
        }

        Comparator<ScoredCandidate> cmp =
                Comparator.comparingInt(ScoredCandidate::violationsAfter)   // after ASC
                        .thenComparingInt(ScoredCandidate::edgesAfter)    // edges ASC
                        .thenComparing((a,b) -> compareModelPDescNaNLast(a.modelPAfter(), b.modelPAfter()))
                        .thenComparingInt(ScoredCandidate::delta);

        if (!improving.isEmpty()) {
            improving.sort(cmp);
            return improving.get(0);
        }

        if (!tie.isEmpty()) {
            tie.sort(cmp);
            return tie.get(0);
        }

        return null;
    }

    private static boolean isUsableP(double p, double alpha) {
        return !Double.isNaN(p) && p >= alpha;
    }

    // Helpers used above (if you don't already have them)
    private static String graphSignature(Graph g) {
        if (g == null) return "null";
        List<String> es = new ArrayList<>();
        for (Edge e : g.getEdges()) {
            Node a = e.getNode1(), b = e.getNode2();
            String an = (a == null || a.getName() == null) ? "?" : a.getName();
            String bn = (b == null || b.getName() == null) ? "?" : b.getName();
            es.add(an + ":" + e.getEndpoint1() + "--" + e.getEndpoint2() + ":" + bn);
        }
        Collections.sort(es);
        return String.join("|", es);
    }

    private static String fmtP(double p) {
        if (Double.isNaN(p)) return "NaN";
        return String.format("%.4g", p);
    }
//    /** Stable-ish graph signature for cheap cycle detection in auto-repair loops. */
//    private static String graphSignature(Graph g) {
//        if (g == null) return "null";
//        List<String> es = new ArrayList<>();
//        for (Edge e : g.getEdges()) {
//            Node a = e.getNode1(), b = e.getNode2();
//            String an = (a == null || a.getName() == null) ? "?" : a.getName();
//            String bn = (b == null || b.getName() == null) ? "?" : b.getName();
//            es.add(an + ":" + e.getEndpoint1() + "--" + e.getEndpoint2() + ":" + bn);
//        }
//        Collections.sort(es);
//        return String.join("|", es);
//    }

    private List<ScoredCandidate> alphaFilteredPresortedList(SearchPack pack, double alpha) {
        List<ScoredCandidate> out = new ArrayList<>();
        for (ScoredCandidate sc : pack.scored) {
            if (sc == null) continue;
            if (sc.edit() == null || sc.edit().isNoOp()) continue;
            if (survivesAlphaFilter(sc, alpha)) out.add(sc);
        }
        out.sort(tableSortComparator());
        return out;
    }

    private ScoredCandidate pickMinDelta(SearchPack pack) {
        ScoredCandidate best = null;
        for (ScoredCandidate sc : pack.scored) {
            if (sc == null || sc.edit() == null || sc.edit().isNoOp()) continue;
            if (best == null || sc.delta() < best.delta()) best = sc;
        }
        return best;
    }

    private ScoredCandidate pickMaxNodeP(SearchPack pack) {
        ScoredCandidate best = null;
        for (ScoredCandidate sc : pack.scored) {
            if (sc == null || sc.edit() == null || sc.edit().isNoOp()) continue;
            double p = sc.nodePAfter();
            if (Double.isNaN(p)) continue;
            if (best == null || p > best.nodePAfter()) best = sc;
        }
        return best;
    }

    private ScoredCandidate pickMaxModelP(SearchPack pack) {
        ScoredCandidate best = null;
        for (ScoredCandidate sc : pack.scored) {
            if (sc == null || sc.edit() == null || sc.edit().isNoOp()) continue;
            double p = sc.modelPAfter();
            if (Double.isNaN(p)) continue;
            if (best == null || p > best.modelPAfter()) best = sc;
        }
        return best;
    }

    private double maxNodePValue(SearchPack pack) {
        double best = Double.NEGATIVE_INFINITY;
        boolean any = false;
        for (ScoredCandidate sc : pack.scored) {
            if (sc == null || sc.edit() == null || sc.edit().isNoOp()) continue;
            double p = sc.nodePAfter();
            if (Double.isNaN(p)) continue;
            any = true;
            if (p > best) best = p;
        }
        return any ? best : Double.NaN;
    }

    private double maxModelPValue(SearchPack pack) {
        double best = Double.NEGATIVE_INFINITY;
        boolean any = false;
        for (ScoredCandidate sc : pack.scored) {
            if (sc == null || sc.edit() == null || sc.edit().isNoOp()) continue;
            double p = sc.modelPAfter();
            if (Double.isNaN(p)) continue;
            any = true;
            if (p > best) best = p;
        }
        return any ? best : Double.NaN;
    }

    /**
     * Compute candidates for a *given* node center (like the panel does for x),
     * but returned as a SearchPack for auto-selection.
     *
     * This intentionally mirrors the panel’s 2-pass approach:
     * - pass 1: After + Node-P for all
     * - pass 2: Model-P for top-K only (so NaNs behave the same as the UI)
     */
    private SearchPack computeCandidatesForNode(Graph g, Node center, RepairGraphType gt) {
        if (g == null || center == null) return null;

        Graph base = safeCopy(g);

        if (stopRequested()) return null;

        if (gt == RepairGraphType.CPDAG || gt == RepairGraphType.PDAG) {
            base = canonicalizeToCpdagOrNull(base);
            if (base == null) return null;
        } else if (gt == RepairGraphType.PAG) {
            base = canonicalizeToPagOrNull(base);
            if (base == null) return null;
        }

        if (knowledge != null && knowledge.isViolatedBy(base)) {
            return null;
        }

        List<CandidateEdit> candidates = enumerateCandidates(base, center, gt);
        candidates = new ArrayList<>(candidates);
        if (candidates.stream().noneMatch(CandidateEdit::isNoOp)) {
            candidates.add(0, CandidateEdit.noOp());
        }

        GlobalEvalCache baseCache = buildBaselineCache(base);
        GraphEval baseEval = evalGraphLocality(base, baseCache, base, Set.of(), true);

        int baseline = baseEval.violations();

        Map<String, Graph> candGraphByKey = new HashMap<>();
        List<ScoredCandidate> scored = new ArrayList<>();

        // PASS 1: after + nodeP + edges
        for (CandidateEdit cand : candidates) {
            if (stopRequested()) return null;

            Graph finalBase = base;
            Graph g2 = candGraphByKey.computeIfAbsent(cand.key(), k -> buildCandidateGraph(finalBase, cand, gt));
            if (g2 == null) continue;

            boolean useLocality = (gt == RepairGraphType.DAG || gt == RepairGraphType.CPDAG || gt == RepairGraphType.PDAG);
            Set<String> affected = affectedVertices(base, cand, center, g2);

            int after = useLocality
                    ? evalGraphLocality(base, baseCache, g2, affected, false).violations()
                    : evalViolationsOnly(g2);

            double nodePAfter = nodePValue(g2, center);
            int edgesAfter = g2.getNumEdges();

            scored.add(new ScoredCandidate(cand, baseline, after, nodePAfter, Double.NaN, edgesAfter));
        }

        if (stopRequested()) return null;

        // PASS 2: compute Model-P for top-K only (same as UI behavior)
//        List<ScoredCandidate> ranked = new ArrayList<>(scored);
//        ranked.sort(candidateRankingForTopK());

        List<ScoredCandidate> ranked = new ArrayList<>(scored);
        ranked.sort(tableOrderIgnoringModelP());

        int k = Math.min(modelPTopK, ranked.size());
        Map<String, Double> modelPByKey = new HashMap<>(k * 2);

        for (int i = 0; i < k; i++) {
            if (stopRequested()) return null;

            ScoredCandidate sc = ranked.get(i);
            Graph g2 = candGraphByKey.get(sc.edit().key());
            if (g2 == null) continue;

            double mp = evalGraphOnce(g2).modelP();
            modelPByKey.put(sc.edit().key(), mp);
        }

        if (!modelPByKey.isEmpty()) {
            List<ScoredCandidate> patched = new ArrayList<>(scored.size());
            for (ScoredCandidate sc : scored) {
                Double mp = modelPByKey.get(sc.edit().key());
                patched.add(mp == null ? sc : new ScoredCandidate(
                        sc.edit(), sc.baseline(), sc.after(), sc.nodePAfter(), mp, sc.edgesAfter()
                ));
            }
            scored = patched;
        }

        return new SearchPack(center.getName(), baseline, scored);
    }

    /**
     * Choose a move using the exact priority you described.
     */
    private CandidateEdit chooseMoveForNode(SearchPack pack, double alpha, boolean useAlphaFilter) {
        if (pack == null || pack.scored == null || pack.scored.isEmpty()) return null;

        // 1) If there are moves that are NOT filtered out, choose the top move
        // by the same ordering the table uses (Edges ASC, Model-P DESC (NaN last), After ASC, Δ ASC).
        List<ScoredCandidate> filteredIn = new ArrayList<>();
        for (ScoredCandidate sc : pack.scored) {
            if (sc == null) continue;
            if (!useAlphaFilter || survivesAlphaFilter(sc, alpha)) {
                filteredIn.add(sc);
            }
        }

        Comparator<ScoredCandidate> tableOrder = tableSortComparator();

        if (!filteredIn.isEmpty()) {
            filteredIn.sort(tableOrder);
            for (ScoredCandidate sc : filteredIn) {
                if (sc != null && !sc.edit().isNoOp()) {
                    return sc.edit();
                }
            }
            // If only No-Op survives, don’t waste an edit
            return null;
        }

        // 2) If no such moves, choose a move that maximizes model p (if possible)
        ScoredCandidate bestModelP = null;
        for (ScoredCandidate sc : pack.scored) {
            if (sc == null || sc.edit().isNoOp()) continue;
            if (Double.isNaN(sc.modelPAfter())) continue; // “not possible” per your spec
            if (bestModelP == null) {
                bestModelP = sc;
            } else {
                int cmp = compareByModelPThenSparsity(sc, bestModelP);
                if (cmp < 0) bestModelP = sc;
            }
        }
        if (bestModelP != null) return bestModelP.edit();

        // 3) If that is not possible, choose a move that maximizes node p (if possible)
        ScoredCandidate bestNodeP = null;
        for (ScoredCandidate sc : pack.scored) {
            if (sc == null || sc.edit().isNoOp()) continue;
            if (Double.isNaN(sc.nodePAfter())) continue;
            if (bestNodeP == null) {
                bestNodeP = sc;
            } else {
                int cmp = compareByNodePThenSparsity(sc, bestNodeP);
                if (cmp < 0) bestNodeP = sc;
            }
        }
        if (bestNodeP != null) return bestNodeP.edit();

        // 4) If that is not possible, choose a move with delta < 0 (most negative)
        ScoredCandidate bestDeltaNeg = null;
        for (ScoredCandidate sc : pack.scored) {
            if (sc == null || sc.edit().isNoOp()) continue;
            if (sc.delta() >= 0) continue;
            if (bestDeltaNeg == null) {
                bestDeltaNeg = sc;
            } else {
                int cmp = compareByDeltaThenSparsity(sc, bestDeltaNeg);
                if (cmp < 0) bestDeltaNeg = sc;
            }
        }
        if (bestDeltaNeg != null) return bestDeltaNeg.edit();

        // 5) otherwise, move on
        return null;
    }

//    private boolean survivesAlphaFilter(ScoredCandidate sc, double alpha) {
//        double modelP = sc.modelPAfter();
//        double nodeP = sc.nodePAfter();
//        return (Double.isNaN(modelP) || modelP >= alpha)
//                && (Double.isNaN(nodeP)  || nodeP  >= alpha);
//    }

    private boolean survivesAlphaFilter(ScoredCandidate sc, double alpha) {
        double modelP = sc.modelPAfter();
        double nodeP  = sc.nodePAfter();
        // Require both computed and >= alpha:
        return !Double.isNaN(modelP) && modelP >= alpha
                && !Double.isNaN(nodeP)  && nodeP  >= alpha;
    }

    private Comparator<ScoredCandidate> tableSortComparator() {
        // Must match applySortAndFilter’s keys:
        // Edges ASC, Model-P DESC (NaN last), After ASC, Δ ASC
        return (a, b) -> {
            if (a == null && b == null) return 0;
            if (a == null) return 1;
            if (b == null) return -1;

            int c;

            c = Integer.compare(a.edgesAfter(), b.edgesAfter());
            if (c != 0) return c;

            c = compareModelPDescNaNLast(a.modelPAfter(), b.modelPAfter());
            if (c != 0) return c;

            c = Integer.compare(a.violationsAfter(), b.violationsAfter());
            if (c != 0) return c;

            return Integer.compare(a.delta(), b.delta());
        };
    }

    private int compareModelPDescNaNLast(double pa, double pb) {
        boolean aNaN = Double.isNaN(pa);
        boolean bNaN = Double.isNaN(pb);
        if (aNaN && bNaN) return 0;
        if (aNaN) return 1;   // NaN last
        if (bNaN) return -1;
        // DESC:
        return -Double.compare(pa, pb);
    }

    private int compareByModelPThenSparsity(ScoredCandidate a, ScoredCandidate b) {
        // prefer higher modelP; tie-break toward sparsity and fewer violations
        double pa = a.modelPAfter();
        double pb = b.modelPAfter();
        int c = -Double.compare(pa, pb); // DESC
        if (c != 0) return c;
        c = Integer.compare(a.edgesAfter(), b.edgesAfter());
        if (c != 0) return c;
        c = Integer.compare(a.violationsAfter(), b.violationsAfter());
        if (c != 0) return c;
        return Integer.compare(a.delta(), b.delta());
    }

    private int compareByNodePThenSparsity(ScoredCandidate a, ScoredCandidate b) {
        double pa = a.nodePAfter();
        double pb = b.nodePAfter();
        int c = -Double.compare(pa, pb); // DESC
        if (c != 0) return c;
        c = Integer.compare(a.edgesAfter(), b.edgesAfter());
        if (c != 0) return c;
        c = Integer.compare(a.violationsAfter(), b.violationsAfter());
        if (c != 0) return c;
        return Integer.compare(a.delta(), b.delta());
    }

    private int compareByDeltaThenSparsity(ScoredCandidate a, ScoredCandidate b) {
        // prefer more negative delta (smaller), then fewer edges
        int c = Integer.compare(a.delta(), b.delta()); // ASC (more negative first)
        if (c != 0) return c;
        c = Integer.compare(a.edgesAfter(), b.edgesAfter());
        if (c != 0) return c;
        c = Integer.compare(a.violationsAfter(), b.violationsAfter());
        if (c != 0) return c;
        return compareModelPDescNaNLast(a.modelPAfter(), b.modelPAfter());
    }

    // ---------------------------------------------------------------------
    // Apply / undo / graph view
    // ---------------------------------------------------------------------

    private void applyCandidate(CandidateEdit cand) {
        applyCandidateInternal(cand, true, true);
        updateButtons();
    }

    /**
     * Internal apply that lets auto-repair avoid per-step history pushes.
     */
    private boolean applyCandidateInternal(CandidateEdit cand, boolean pushHistory, boolean updateStatus) {
        if (cand == null) return false;
        if (cand.isNoOp()) return false;

        vlog("Attempting move: %s", cand.description());

        if (pushHistory) {
            history.push(safeCopy(workingGraph));
        }

        RepairGraphType gt = (RepairGraphType) graphTypeCombo.getSelectedItem();

        Graph base = safeCopy(workingGraph);

        // Canonicalize base if needed (same as you already do)
        if (gt == RepairGraphType.CPDAG) {
            base = canonicalizeToCpdagOrNull(base);
            if (base == null) {
                if (updateStatus) statusLabel.setText("Current graph has no consistent CPDAG extension.");
                if (pushHistory && !history.isEmpty()) history.pop();
                return false;
            }
        } else if (gt == RepairGraphType.PAG) {
            base = canonicalizeToPagOrNull(base);
            if (base == null) {
                if (updateStatus) statusLabel.setText("Current graph has no consistent PAG extension.");
                if (pushHistory && !history.isEmpty()) history.pop();
                return false;
            }
        }

        Graph g2 = cand.applyTo(base);
        if (g2 == null) {
            if (updateStatus) statusLabel.setText("Failed to apply: " + cand.description());
            if (pushHistory && !history.isEmpty()) history.pop();
            return false;
        }

        // Canonicalize result if needed
        if (gt == RepairGraphType.CPDAG) {
            g2 = canonicalizeToCpdagOrNull(g2);
            if (g2 == null) {
                if (updateStatus) statusLabel.setText("Failed to apply (CPDAG canonicalization): " + cand.description());
                if (pushHistory && !history.isEmpty()) history.pop();
                return false;
            }
        } else if (gt == RepairGraphType.PAG) {
            // you currently “keep as-is” for PAG; that’s fine if your edits always produce legal PAGs
            // otherwise you might want to canonicalize here too.
        }

        // **CRITICAL**: if the move does not change the graph, treat it as “no move”
        if (g2.equals(base)) {
            vlog("REJECTED (no graph change after canonicalization)");
            if (updateStatus) statusLabel.setText("No-op after canonicalization: " + cand.description());
            if (pushHistory && !history.isEmpty()) history.pop();
            return false;
        }

        // Commit
        workingGraph = g2;

        // resync selected node object to the instance in the updated graph
        if (x != null && x.getName() != null) {
            Node inGraph = workingGraph.getNode(x.getName());
            if (inGraph != null) x = inGraph;
            else x = resolveInitialNode(workingGraph, null);
            SwingUtilities.invokeLater(this::populateNodeCombo);
        }

        vlog("APPLIED successfully");

        if (updateStatus) statusLabel.setText("Applied: " + cand.description());
        return true;
    }

    private void goBack() {
        if (history.isEmpty()) return;
        workingGraph = history.pop();
        statusLabel.setText("Reverted to previous graph.");
        updateButtons();

        startWatched("Searching", this::runSearchWatched, null);
    }

    private void showGraphDialog() {
        Graph graph = workingGraph;

        // --- Tab 1: Text ---
        JTextArea ta = new JTextArea(String.valueOf(graph));
        ta.setEditable(false);
        ta.setCaretPosition(0);
        JScrollPane textScroll = new JScrollPane(ta);
        textScroll.setPreferredSize(new Dimension(820, 520));

        // --- Tab 2: Render ---
        GraphWorkbench workbench = new GraphWorkbench(graph);
        workbench.setEnableEditing(false);
        JScrollPane renderScroll = new JScrollPane(workbench);
        renderScroll.setPreferredSize(new Dimension(820, 520));

        // --- Tabs ---
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Graph", renderScroll);
        tabs.addTab("Text", textScroll);
        tabs.setTabPlacement(JTabbedPane.RIGHT);

        JOptionPane.showMessageDialog(
                this,
                tabs,
                "Current Graph",
                JOptionPane.INFORMATION_MESSAGE
        );
    }

    // ---------------------------------------------------------------------
    // Candidate enumeration
    // ---------------------------------------------------------------------

    private List<CandidateEdit> enumerateCandidates(Graph g, Node x, RepairGraphType gt) {
        if (g == null || x == null) return List.of(CandidateEdit.noOp());

        List<CandidateEdit> out = new ArrayList<>();
        out.add(CandidateEdit.noOp());

        // 0) Build the add-edge pool
        Set<Node> pool = new LinkedHashSet<>(g.getNodes());
        pool.remove(x);

        // 1) Remove any existing edge incident to x
        for (Edge e : new ArrayList<>(g.getEdges(x))) {
            out.add(CandidateEdit.removeEdge(e));
        }

        // 2) Replace existing edge x—y with type-specific variants
        for (Edge e : new ArrayList<>(g.getEdges(x))) {
            Node y = e.getDistalNode(x);
            if (y == null) continue;

            for (Edge v : edgeMenuForPair(x, y, gt)) {
                if (edgeStructurallyEqual(e, v, x, y)) continue;
                out.add(CandidateEdit.replaceEdge(e, v));
            }
        }

        // 3) Add edges x—y for non-adjacent y in pool
        for (Node y : pool) {
            if (y == null) continue;
            if (g.isAdjacentTo(x, y)) continue;

            for (Edge add : addMenuForPair(x, y, gt)) {
                out.add(CandidateEdit.addEdge(add));
            }
        }

        return dedupCandidateEdits(out);
    }

    private List<Edge> edgeMenuForPair(Node x, Node y, RepairGraphType gt) {
        List<Edge> variants = new ArrayList<>();

        switch (gt) {
            case DAG -> {
                variants.add(new Edge(x, y, Endpoint.TAIL, Endpoint.ARROW)); // x->y
                variants.add(new Edge(y, x, Endpoint.TAIL, Endpoint.ARROW)); // y->x
            }
            case CPDAG, PDAG -> {
                variants.add(new Edge(x, y, Endpoint.TAIL, Endpoint.TAIL));  // x---y
                variants.add(new Edge(x, y, Endpoint.TAIL, Endpoint.ARROW)); // x->y
                variants.add(new Edge(y, x, Endpoint.TAIL, Endpoint.ARROW)); // y->x
            }
            case MAG -> {
                variants.add(new Edge(x, y, Endpoint.TAIL, Endpoint.ARROW));   // x->y
                variants.add(new Edge(y, x, Endpoint.TAIL, Endpoint.ARROW));   // y->x
                variants.add(new Edge(x, y, Endpoint.ARROW, Endpoint.ARROW));  // x<->y
            }
            case PAG -> {
                variants.add(new Edge(x, y, Endpoint.CIRCLE, Endpoint.CIRCLE)); // o-o
                variants.add(new Edge(x, y, Endpoint.CIRCLE, Endpoint.ARROW));  // x o-> y
                variants.add(new Edge(y, x, Endpoint.CIRCLE, Endpoint.ARROW));  // y o-> x
                variants.add(new Edge(x, y, Endpoint.TAIL, Endpoint.ARROW));    // x->y
                variants.add(new Edge(y, x, Endpoint.TAIL, Endpoint.ARROW));    // y->x
                variants.add(new Edge(x, y, Endpoint.ARROW, Endpoint.ARROW));   // x<->y
                variants.add(new Edge(x, y, Endpoint.TAIL, Endpoint.TAIL));     // x---y
            }
        }

        return variants;
    }

    private List<Edge> addMenuForPair(Node x, Node y, RepairGraphType gt) {
        List<Edge> adds = new ArrayList<>();

        switch (gt) {
            case DAG -> {
                adds.add(new Edge(x, y, Endpoint.TAIL, Endpoint.ARROW)); // x->y
                adds.add(new Edge(y, x, Endpoint.TAIL, Endpoint.ARROW)); // y->x
            }
            case CPDAG -> {
                adds.add(new Edge(x, y, Endpoint.TAIL, Endpoint.TAIL));  // x---y
                adds.add(new Edge(x, y, Endpoint.TAIL, Endpoint.ARROW)); // x->y (optional)
                adds.add(new Edge(y, x, Endpoint.TAIL, Endpoint.ARROW)); // y->x (optional)
            }
            case PDAG -> {
                adds.add(new Edge(x, y, Endpoint.TAIL, Endpoint.TAIL));  // x---y
                adds.add(new Edge(x, y, Endpoint.TAIL, Endpoint.ARROW)); // x->y
                adds.add(new Edge(y, x, Endpoint.TAIL, Endpoint.ARROW)); // y->x
            }
            case MAG -> {
                adds.add(new Edge(x, y, Endpoint.TAIL, Endpoint.ARROW));   // x->y
                adds.add(new Edge(y, x, Endpoint.TAIL, Endpoint.ARROW));   // y->x
                adds.add(new Edge(x, y, Endpoint.ARROW, Endpoint.ARROW));  // x<->y
            }
            case PAG -> {
                adds.add(new Edge(x, y, Endpoint.CIRCLE, Endpoint.CIRCLE)); // o-o
                adds.add(new Edge(x, y, Endpoint.CIRCLE, Endpoint.ARROW));  // x o-> y
                adds.add(new Edge(y, x, Endpoint.CIRCLE, Endpoint.ARROW));  // y o-> x
                adds.add(new Edge(x, y, Endpoint.TAIL, Endpoint.ARROW));    // x->y (optional)
                adds.add(new Edge(y, x, Endpoint.TAIL, Endpoint.ARROW));    // y->x (optional)
                adds.add(new Edge(x, y, Endpoint.ARROW, Endpoint.ARROW));   // x<->y (optional)
                adds.add(new Edge(x, y, Endpoint.TAIL, Endpoint.TAIL));     // x---y (optional)
            }
        }

        return adds;
    }

    // ---------------------------------------------------------------------
    // Canonicalization / legality / copies
    // ---------------------------------------------------------------------

    private Graph canonicalizeToCpdagOrNull(Graph h) {
        try {
            Graph h2 = new EdgeListGraph(h);
            Graph dag = GraphTransforms.dagFromCpdag(h2);
            return GraphTransforms.dagToCpdag(dag);
        } catch (Throwable t) {
            return null;
        }
    }

    private Graph canonicalizeToPagOrNull(Graph h) {
        try {
            Graph h2 = new EdgeListGraph(h);
            Graph mag = GraphTransforms.zhangMagFromPag(h2);

            if (!mag.paths().isLegalMag()) {
                return null;
            }

            return GraphTransforms.magToPag(mag, false);
        } catch (Throwable t) {
            return null;
        }
    }

    private boolean isLegalGraphType(Graph g, RepairGraphType gt) {
        return switch (gt) {
            case DAG -> g.paths().isLegalDag();
            case CPDAG -> g.paths().isLegalCpdag() || g.paths().isLegalPdag();
            case PDAG -> g.paths().isLegalPdag();
            case MAG -> g.paths().isLegalMag();
            case PAG -> g.paths().isLegalPag();
        };
    }

    private Graph safeCopy(Graph g) {
        if (g == null) return null;
        try {
            return g.copy();
        } catch (Throwable t) {
            return new EdgeListGraph(g);
        }
    }

    private void initGraphTypeComboFromGraph(Graph g) {
        List<RepairGraphType> plausible = new ArrayList<>();
        for (RepairGraphType gt : RepairGraphType.values()) {
            try {
                if (g != null && isLegalGraphType(g, gt)) {
                    plausible.add(gt);
                }
            } catch (Exception ignored) {
                // ignore
            }
        }

        if (plausible.isEmpty()) {
            plausible = Arrays.asList(RepairGraphType.values());
        }

        graphTypeCombo.setModel(new DefaultComboBoxModel<>(plausible.toArray(new RepairGraphType[0])));
        graphTypeCombo.setSelectedIndex(0);
    }

    // ---------------------------------------------------------------------
    // Knowledge
    // ---------------------------------------------------------------------

    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = (knowledge == null) ? new Knowledge() : knowledge;

        if (workingGraph != null && this.knowledge.isViolatedBy(workingGraph)) {
            throw new IllegalArgumentException("The given Knowledge object is violated by the current graph.");
        }
    }

    // ---------------------------------------------------------------------
    // Cached CI access
    // ---------------------------------------------------------------------

    private IndependenceResult check(IndependenceFact f) {
        if (stopRequested()) return null;
        if (f == null || Q == null) return null;

        Set<Node> z = new LinkedHashSet<>(f.getZ());
        try {
            return Q.checkIndependence(f.getX(), f.getY(), z);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private double nodePValue(Graph g, Node vertexInOriginalGraph) {
        if (g == null || vertexInOriginalGraph == null) return Double.NaN;

        Node v = g.getNode(vertexInOriginalGraph.getName());
        if (v == null) return Double.NaN;

        List<IndependenceFact> facts = baseModel.computeImpliedFactsForVertex(g, v);
        if (facts == null || facts.isEmpty()) return Double.NaN;

        List<Double> pvals = Q.pValuesForFacts(facts, CachedIndependenceQueries.Dedup.WITHIN_INPUT);
        return model.getUniformityP(pvals);
    }

    // ---------------------------------------------------------------------
    // Global evaluation
    // ---------------------------------------------------------------------

    private GraphEval evalGraphOnce(Graph g) {
        if (g == null) return new GraphEval(0, Double.NaN, 0);

        List<IndependenceFact> facts = baseModel.computeAllImpliedFacts(g);
        if (facts == null || facts.isEmpty()) return new GraphEval(0, Double.NaN, 0);

        List<CachedIndependenceQueries.Eval> evals =
                Q.evalAll(facts, CachedIndependenceQueries.Dedup.BY_CACHE_KEY);

        int violations = 0;
        List<Double> pvals = new ArrayList<>(evals.size());

        for (CachedIndependenceQueries.Eval e : evals) {
            if (!e.independent()) violations++;
            double p = e.pValue();
            if (!Double.isNaN(p) && p >= 0.0 && p <= 1.0) pvals.add(p);
        }

        double p = model.getUniformityP(pvals);
        return new GraphEval(violations, p, evals.size());
    }

    private int evalViolationsOnly(Graph g) {
        if (g == null) return 0;

        List<IndependenceFact> facts = baseModel.computeAllImpliedFacts(g);
        if (facts == null || facts.isEmpty()) return 0;

        List<CachedIndependenceQueries.Eval> evals =
                Q.evalAll(facts, CachedIndependenceQueries.Dedup.BY_CACHE_KEY);

        int violations = 0;
        for (CachedIndependenceQueries.Eval e : evals) {
            if (!e.independent()) violations++;
        }
        return violations;
    }

    private GlobalEvalCache buildBaselineCache(Graph g) {
        if (g == null) return new GlobalEvalCache(Map.of());

        Map<String, VertexContribution> out = new HashMap<>();

        for (Node v : g.getNodes()) {
            if (v == null) continue;
            out.put(v.getName(), evalVertexContribution(g, v));
        }

        return new GlobalEvalCache(out);
    }

    private VertexContribution evalVertexContribution(Graph g, Node vInGraph) {
        if (g == null || vInGraph == null) return new VertexContribution(Map.of(), Map.of());

        Node v = g.getNode(vInGraph.getName());
        if (v == null) return new VertexContribution(Map.of(), Map.of());

        List<IndependenceFact> facts = baseModel.computeImpliedFactsForVertex(g, v);
        if (facts == null || facts.isEmpty()) return new VertexContribution(Map.of(), Map.of());

        Map<String, Boolean> viol = new HashMap<>();
        Map<String, Double> pByKey = new HashMap<>();

        for (IndependenceFact f : facts) {
            if (f == null) continue;

            String key = factKey(f);

            // de-dup within vertex: first wins
            if (viol.containsKey(key)) continue;

            IndependenceResult r = check(f);
            if (r == null) continue;

            boolean isViolation = !r.isIndependent();
            viol.put(key, isViolation);

            double p = r.getPValue();
            if (!Double.isNaN(p) && p >= 0.0 && p <= 1.0) {
                pByKey.put(key, p);
            }
        }

        return new VertexContribution(viol, pByKey);
    }

    private GraphEval evalGraphLocality(Graph baseGraph,
                                        GlobalEvalCache baseCache,
                                        Graph candidateGraph,
                                        Set<String> affectedVertexNames,
                                        boolean computeModelP) {
        if (candidateGraph == null) return new GraphEval(0, Double.NaN, 0);

        // 1) Start from baseline vertex contributions (shallow copy map)
        Map<String, VertexContribution> contrib = new HashMap<>();
        if (baseCache != null && baseCache.contribByVertexName() != null) {
            contrib.putAll(baseCache.contribByVertexName());
        }

        // 2) Overwrite affected vertices with freshly evaluated contributions under candidateGraph
        if (affectedVertexNames != null) {
            for (String name : affectedVertexNames) {
                if (name == null) continue;
                Node v = candidateGraph.getNode(name);
                if (v == null) {
                    contrib.remove(name);
                    continue;
                }
                contrib.put(name, evalVertexContribution(candidateGraph, v));
            }
        }

        // 3) Merge to global dedup by factKey (stable traversal for repeatability)
        Map<String, Boolean> globalViolationByKey = new HashMap<>();
        Map<String, Double> globalPByKey = computeModelP ? new HashMap<>() : null;

        List<String> names = new ArrayList<>(contrib.keySet());
        Collections.sort(names);

        for (String name : names) {
            VertexContribution vc = contrib.get(name);
            if (vc == null) continue;

            for (Map.Entry<String, Boolean> e : vc.violationByKey().entrySet()) {
                String key = e.getKey();
                if (key == null) continue;
                globalViolationByKey.putIfAbsent(key, e.getValue());
            }

            if (computeModelP) {
                for (Map.Entry<String, Double> e : vc.pByKey().entrySet()) {
                    String key = e.getKey();
                    if (key == null) continue;
                    globalPByKey.putIfAbsent(key, e.getValue());
                }
            }
        }

        int violations = 0;
        for (boolean isViol : globalViolationByKey.values()) {
            if (isViol) violations++;
        }

        double modelP = Double.NaN;
        if (computeModelP && globalPByKey != null && globalPByKey.size() >= 2) {
            List<Double> pvals = new ArrayList<>(globalPByKey.values());

            // Optional top-K downselect for model-p computation inside locality mode
            if (modelPTopK > 0 && pvals.size() > modelPTopK) {
                pvals.sort(Double::compareTo);
                List<Double> sampled = new ArrayList<>(modelPTopK);
                for (int i = 0; i < modelPTopK; i++) {
                    int idx = (int) Math.floor((i + 0.5) * pvals.size() / modelPTopK);
                    idx = Math.min(Math.max(idx, 0), pvals.size() - 1);
                    sampled.add(pvals.get(idx));
                }
                pvals = sampled;
            }

            modelP = model.getUniformityP(pvals);
        }

        return new GraphEval(violations, modelP, globalViolationByKey.size());
    }

    private Graph buildCandidateGraph(Graph base, CandidateEdit cand, RepairGraphType gt) {
        if (base == null || cand == null) return null;

        Graph g2 = cand.applyTo(safeCopy(base));
        if (g2 == null) return null;

        if (gt == RepairGraphType.CPDAG) {
            g2 = canonicalizeToCpdagOrNull(g2);
            if (g2 == null) return null;

            if (!cand.isNoOp() && g2.equals(base)) return null;
        } else if (gt == RepairGraphType.PAG) {
            // keep as-is
        } else if (gt == RepairGraphType.PDAG) {
            // keep as-is
        }

        try {
            if (gt != null && !isLegalGraphType(g2, gt)) return null;
        } catch (Exception ignored) {
            return null;
        }

        return g2;
    }

    private Set<String> affectedVertices(Graph base, CandidateEdit cand, Node x, Graph candidate) {
        Set<String> affected = new LinkedHashSet<>();
        if (x != null) affected.add(x.getName());

        if (base == null || candidate == null || x == null) return affected;

        Node xb = base.getNode(x.getName());
        Node xc = candidate.getNode(x.getName());
        if (xb == null || xc == null) return affected;

        Set<String> nb = new HashSet<>();
        for (Node n : base.getAdjacentNodes(xb)) if (n != null) nb.add(n.getName());

        Set<String> nc = new HashSet<>();
        for (Node n : candidate.getAdjacentNodes(xc)) if (n != null) nc.add(n.getName());

        for (String name : nb) if (!nc.contains(name)) affected.add(name);
        for (String name : nc) if (!nb.contains(name)) affected.add(name);

        affected.addAll(nc);
        return affected;
    }

    // ---------------------------------------------------------------------
    // Preferences
    // ---------------------------------------------------------------------

    private void loadPrefsIntoUi() {
        double a = PREFS.getDouble(PREF_ALPHA, DEFAULT_ALPHA);
        int k = PREFS.getInt(PREF_MODEL_P_TOP_K, DEFAULT_MODELP_TOP_K);

        if (!(a > 0.0 && a < 1.0)) a = DEFAULT_ALPHA;
        if (k <= 0) k = DEFAULT_MODELP_TOP_K;

        alphaField.setText(String.valueOf(a));
        modelPTopKField.setText(String.valueOf(k));
        modelPTopK = k;
    }

    private void initPrefTimers() {
        alphaSaveTimer.setRepeats(false);
        topModelPaveTimer.setRepeats(false);
    }

    private void saveAlphaPref() {
        double a = parseAlpha(alphaField.getText(), DEFAULT_ALPHA);
        PREFS.putDouble(PREF_ALPHA, a);
    }

    private void saveTopKPref() {
        int k = parseTopK(modelPTopKField.getText(), DEFAULT_MODELP_TOP_K);
        PREFS.putInt(PREF_MODEL_P_TOP_K, k);
    }

    // ---------------------------------------------------------------------
    // Worker/watch dialog
    // ---------------------------------------------------------------------

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
                    if (onDoneEdt != null) onDoneEdt.run();
                } catch (CancellationException ce) {
                    statusLabel.setText("Cancelled.");
                } catch (Exception ex) {
                    statusLabel.setText("Error: " + (ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage()));
                } finally {
                    closeWatchDialog();
                    activeWorker = null;
                    updateButtons();
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

    private boolean stopRequested() {
        SwingWorker<?, ?> w = activeWorker;
        return (w != null && w.isCancelled()) || Thread.currentThread().isInterrupted();
    }

    // ---------------------------------------------------------------------
    // Types
    // ---------------------------------------------------------------------

    public enum RepairGraphType {DAG, CPDAG, PDAG, MAG, PAG}

    public interface CandidateEdit {

        static CandidateEdit noOp() {
            return new CandidateEdit() {
                @Override
                public String description() {
                    return "No change";
                }

                @Override
                public Graph applyTo(Graph g) {
                    return (g == null) ? null : new EdgeListGraph(g);
                }

                @Override
                public boolean isNoOp() {
                    return true;
                }

                @Override
                public String key() {
                    return "NO_OP";
                }
            };
        }

        static CandidateEdit addEdge(Edge edgeToAdd) {
            Objects.requireNonNull(edgeToAdd, "edgeToAdd");
            return new CandidateEdit() {
                @Override
                public String description() {
                    return "Add edge " + edgeToAdd;
                }

                @Override
                public Graph applyTo(Graph g) {
                    Graph g2 = new EdgeListGraph(g);
                    g2.addEdge(edgeToAdd);
                    return g2;
                }

                @Override
                public String key() {
                    return "ADD:" + stableEdgeKey(edgeToAdd);
                }
            };
        }

        static CandidateEdit removeEdge(Edge edgeToRemove) {
            Objects.requireNonNull(edgeToRemove, "edgeToRemove");
            return new CandidateEdit() {
                @Override
                public String description() {
                    return "Remove edge " + edgeToRemove;
                }

                @Override
                public Graph applyTo(Graph g) {
                    Graph g2 = new EdgeListGraph(g);
                    Edge e = g2.getEdge(edgeToRemove.getNode1(), edgeToRemove.getNode2());
                    if (e != null) g2.removeEdge(e);
                    return g2;
                }

                @Override
                public String key() {
                    return "REM:" + stableEdgeKey(edgeToRemove);
                }
            };
        }

        static CandidateEdit replaceEdge(Edge oldEdge, Edge newEdge) {
            Objects.requireNonNull(oldEdge, "oldEdge");
            Objects.requireNonNull(newEdge, "newEdge");
            return new CandidateEdit() {
                @Override
                public String description() {
                    return "Replace " + oldEdge + " with " + newEdge;
                }

                @Override
                public Graph applyTo(Graph g) {
                    Graph g2 = new EdgeListGraph(g);
                    Edge e = g2.getEdge(oldEdge.getNode1(), oldEdge.getNode2());
                    if (e != null) g2.removeEdge(e);
                    g2.addEdge(newEdge);
                    return g2;
                }

                @Override
                public String key() {
                    return "REP:" + stableEdgeKey(oldEdge) + "->" + stableEdgeKey(newEdge);
                }
            };
        }

        String description();

        Graph applyTo(Graph g);

        default boolean isNoOp() {
            return false;
        }

        default String key() {
            return description();
        }

        private static String stableEdgeKey(Edge e) {
            if (e == null) return "null";
            Node a = e.getNode1();
            Node b = e.getNode2();
            String an = (a == null || a.getName() == null) ? "?" : a.getName();
            String bn = (b == null || b.getName() == null) ? "?" : b.getName();
            Endpoint ea = e.getEndpoint1();
            Endpoint eb = e.getEndpoint2();
            return an + ":" + bn + ":" + ea + ":" + eb;
        }
    }

    private record ScoredCandidate(
            CandidateEdit edit,
            int baseline,
            int after,
            double nodePAfter,
            double modelPAfter,
            int edgesAfter
    ) {
        int violationsAfter() {
            return after;
        }

        int delta() {
            return after - baseline;
        }
    }

    private static final class CandidateTableModel extends AbstractTableModel {
        private static final int COL_EDIT = 0;
        private static final int COL_BASE = 1;
        private static final int COL_AFTER = 2;
        private static final int COL_DELTA = 3;
        private static final int COL_NODE_P = 4;
        private static final int COL_MODEL_P = 5;
        private static final int COL_EDGES = 6;
        private static final int COL_APPLY = 7;

        private final String[] cols = {
                "Edit", "Baseline", "After", "Δ", "Node-P", "Model-P", "Edges", "Apply"
        };

        private List<ScoredCandidate> rows = List.of();

        void set(List<ScoredCandidate> rows) {
            this.rows = rows == null ? List.of() : rows;
            fireTableDataChanged();
        }

        CandidateEdit getCandidate(int row) {
            return rows.get(row).edit();
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return cols.length;
        }

        @Override
        public String getColumnName(int column) {
            return cols[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            ScoredCandidate r = rows.get(rowIndex);
            return switch (columnIndex) {
                case COL_EDIT -> r.edit().description();
                case COL_BASE -> r.baseline();
                case COL_AFTER -> r.violationsAfter();
                case COL_DELTA -> r.delta();
                case COL_NODE_P -> r.nodePAfter();
                case COL_MODEL_P -> r.modelPAfter();
                case COL_EDGES -> r.edgesAfter();
                case COL_APPLY -> r.edit().isNoOp() ? "" : "Accept";
                default -> "";
            };
        }

        @Override
        public Class<?> getColumnClass(int col) {
            return switch (col) {
                case COL_EDIT -> String.class;
                case COL_BASE -> Integer.class;
                case COL_AFTER -> Integer.class;
                case COL_DELTA -> Integer.class;
                case COL_NODE_P -> Double.class;
                case COL_MODEL_P -> Double.class;
                case COL_EDGES -> Integer.class;
                case COL_APPLY -> Object.class;
                default -> Object.class;
            };
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == COL_APPLY && !rows.get(rowIndex).edit().isNoOp();
        }
    }

    private static final class ButtonRenderer extends JButton implements TableCellRenderer {
        ButtonRenderer() {
            setOpaque(true);
        }

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

        ButtonEditor(RowAction onClick) {
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

        @Override
        public Object getCellEditorValue() {
            return button.getText();
        }

        interface RowAction {
            void run(int row);
        }
    }

    private record GraphEval(int violations, double modelP, int nFacts) {
    }

    private record VertexContribution(
            Map<String, Boolean> violationByKey,
            Map<String, Double> pByKey
    ) {
    }

    private record GlobalEvalCache(
            Map<String, VertexContribution> contribByVertexName
    ) {
    }

    /**
     * Lightweight container for per-node auto selection.
     */
    private static final class SearchPack {
        final String centerName;
        final int baseline;
        final List<ScoredCandidate> scored;

        SearchPack(String centerName, int baseline, List<ScoredCandidate> scored) {
            this.centerName = centerName;
            this.baseline = baseline;
            this.scored = scored;
        }
    }

    // ---------------------------------------------------------------------
// Table-order aware comparators (match the JTable ordering)
// ---------------------------------------------------------------------

    /**
     * Comparator that matches the table's sort keys, BUT treats Model-P as "unknown"
     * (i.e., ignores it). This is what you want for deciding which rows are worth
     * computing Model-P for.
     *
     * The table is: Edges ASC, Model-P DESC, After ASC, Δ ASC.
     * If Model-P is unknown for most rows, then the practical order is:
     * Edges ASC, After ASC, Δ ASC.
     */
    private Comparator<ScoredCandidate> tableOrderIgnoringModelP() {
        return Comparator
                .comparingInt(ScoredCandidate::edgesAfter)
                .thenComparingInt(ScoredCandidate::violationsAfter)
                .thenComparingInt(ScoredCandidate::delta);
    }

    /**
     * Comparator that matches the table sort fully:
     * Edges ASC, Model-P DESC (NaN last), After ASC, Δ ASC.
     */
    private Comparator<ScoredCandidate> tableOrderFull() {
        return (a, b) -> {
            if (a == null && b == null) return 0;
            if (a == null) return 1;
            if (b == null) return -1;

            int c;

            c = Integer.compare(a.edgesAfter(), b.edgesAfter());
            if (c != 0) return c;

            c = compareModelPDescNaNLast(a.modelPAfter(), b.modelPAfter());
            if (c != 0) return c;

            c = Integer.compare(a.violationsAfter(), b.violationsAfter());
            if (c != 0) return c;

            return Integer.compare(a.delta(), b.delta());
        };
    }

//    private CandidateEdit chooseMoveExactlyLikeYou(Node center, SearchPack pack, double alpha) {
//        // 0) We only ever consider the same rows the table would show:
//        //    pack.scored corresponds to enumerateCandidates() + scoring, just like the table.
//
//        // A) First, act as if alpha filter is ON and we sort exactly like the table
//        List<ScoredCandidate> filtered = new ArrayList<>();
//        for (ScoredCandidate sc : pack.scored) {
//            if (sc == null || sc.edit() == null || sc.edit().isNoOp()) continue;
//            if (survivesAlphaFilter(sc, alpha)) filtered.add(sc);
//        }
//
//        // IMPORTANT: "top move that would be listed in the node repair table"
//        // This comparator MUST match your applySortAndFilter() sort keys:
//        //   Edges ASC, Model-P DESC (NaN last), After ASC, Δ ASC
//        Comparator<ScoredCandidate> tableOrder = tableSortComparator();
//
//        if (!filtered.isEmpty()) {
//            filtered.sort(tableOrder);
//
//            ScoredCandidate top = filtered.get(0);
//            return top.edit();
//        }
//
//        // B) If no filtered rows, consider unfiltered rows and:
//        //    1) max Model-P (if max != 0)
//        ScoredCandidate bestMp = null;
//        for (ScoredCandidate sc : pack.scored) {
//            if (sc == null || sc.edit() == null || sc.edit().isNoOp()) continue;
//            double mp = sc.modelPAfter();
//            if (Double.isNaN(mp)) continue;
//            if (bestMp == null || mp > bestMp.modelPAfter()) bestMp = sc;
//        }
//        if (bestMp != null && bestMp.modelPAfter() != 0.0) return bestMp.edit();
//
//        //    2) max Node-P (if max != 0)
//        ScoredCandidate bestNp = null;
//        for (ScoredCandidate sc : pack.scored) {
//            if (sc == null || sc.edit() == null || sc.edit().isNoOp()) continue;
//            double np = sc.nodePAfter();
//            if (Double.isNaN(np)) continue;
//            if (bestNp == null || np > bestNp.nodePAfter()) bestNp = sc;
//        }
//        if (bestNp != null && bestNp.nodePAfter() != 0.0) return bestNp.edit();
//
//        //    3) min delta (if min < 0)
//        ScoredCandidate bestDelta = null;
//        for (ScoredCandidate sc : pack.scored) {
//            if (sc == null || sc.edit() == null || sc.edit().isNoOp()) continue;
//            int d = sc.delta();
//            if (bestDelta == null || d < bestDelta.delta()) bestDelta = sc;
//        }
//        if (bestDelta != null && bestDelta.delta() < 0) return bestDelta.edit();
//
//        return null;
//    }

    private CandidateEdit chooseMoveExactlyLikeYou(
            Node center, SearchPack pack, double alpha) {

        vlog("Selecting move for node %s (alpha=%.4g)", center.getName(), alpha);

        Comparator<ScoredCandidate> tableOrder = tableSortComparator();

        // A) alpha-filtered, table-sorted
        List<ScoredCandidate> filtered = new ArrayList<>();
        for (ScoredCandidate sc : pack.scored) {
            if (sc == null || sc.edit().isNoOp()) continue;
            if (survivesAlphaFilter(sc, alpha)) filtered.add(sc);
        }

        if (!filtered.isEmpty()) {
            filtered.sort(tableOrder);
            ScoredCandidate top = filtered.get(0);
            vlog("→ Using ALPHA-FILTERED TOP ROW: %s", top.edit().description());
            return top.edit();
        }

        vlog("No rows survive alpha filter");

        // B1) max model-P
        ScoredCandidate bestMp = null;
        for (ScoredCandidate sc : pack.scored) {
            if (sc == null || sc.edit().isNoOp()) continue;
            if (Double.isNaN(sc.modelPAfter())) continue;
            if (bestMp == null || sc.modelPAfter() > bestMp.modelPAfter()) {
                bestMp = sc;
            }
        }
        if (bestMp != null && bestMp.modelPAfter() != 0.0) {
            vlog("→ Using MAX MODEL-P: %s (%.4g)",
                    bestMp.edit().description(), bestMp.modelPAfter());
            return bestMp.edit();
        }

        // B2) max node-P
        ScoredCandidate bestNp = null;
        for (ScoredCandidate sc : pack.scored) {
            if (sc == null || sc.edit().isNoOp()) continue;
            if (Double.isNaN(sc.nodePAfter())) continue;
            if (bestNp == null || sc.nodePAfter() > bestNp.nodePAfter()) {
                bestNp = sc;
            }
        }
        if (bestNp != null && bestNp.nodePAfter() != 0.0) {
            vlog("→ Using MAX NODE-P: %s (%.4g)",
                    bestNp.edit().description(), bestNp.nodePAfter());
            return bestNp.edit();
        }

        // B3) min delta < 0
        ScoredCandidate bestDelta = null;
        for (ScoredCandidate sc : pack.scored) {
            if (sc == null || sc.edit().isNoOp()) continue;
            if (bestDelta == null || sc.delta() < bestDelta.delta()) {
                bestDelta = sc;
            }
        }
        if (bestDelta != null && bestDelta.delta() < 0) {
            vlog("→ Using NEGATIVE DELTA: %s (delta=%d)",
                    bestDelta.edit().description(), bestDelta.delta());
            return bestDelta.edit();
        }

        vlog("→ No legal move found for node %s", center.getName());
        return null;
    }

    private static void vlog(String fmt, Object... args) {
        System.out.println("[VertexAutoRepair] " + String.format(fmt, args));
    }

    private static final double EPS_NODEP = 1e-6;

    // "If it's not broke, don't fix it" check for a node
    private boolean nodeLooksOk(Graph g, Node v, double alpha) {
        double p = nodePValue(g, v);
        return !Double.isNaN(p) && p >= alpha;
    }

    // Compute nodeP map for a set of vertex names in a graph
    private Map<String, Double> nodePMap(Graph g, Set<String> vertexNames) {
        Map<String, Double> out = new HashMap<>();
        if (g == null || vertexNames == null) return out;

        for (String name : vertexNames) {
            if (name == null) continue;
            Node v = g.getNode(name);
            if (v == null) continue;
            double p = nodePValue(g, v);
            out.put(name, p);
        }
        return out;
    }

    // Do-no-harm: for all vertices where both old/new are finite, forbid degradation
    private boolean respectsDoNoHarm(Map<String, Double> before, Map<String, Double> after, String centerName) {
        for (Map.Entry<String, Double> e : before.entrySet()) {
            String name = e.getKey();
            double p0 = e.getValue();
            Double p1Obj = after.get(name);
            if (p1Obj == null) continue;
            double p1 = p1Obj;

            if (Double.isNaN(p0) || Double.isNaN(p1)) continue; // can't compare

            // Strictest on center, slightly looser on neighbors (optional)
            double eps = (name != null && name.equals(centerName)) ? EPS_NODEP : EPS_NODEP;
            if (p1 < p0 - eps) return false;
        }
        return true;
    }

    // Progress gate: must improve violations, or tie violations and reduce edges
    private boolean isProgress(int baselineViol, int afterViol, int currentEdges, int afterEdges) {
        if (afterViol < baselineViol) return true;
        return afterViol == baselineViol && afterEdges < currentEdges;
    }

    private boolean tryMoveWithGuards(Graph base, Node center, CandidateEdit edit, RepairGraphType gt, double alpha) {
        if (edit == null || edit.isNoOp()) return false;

        int currentEdges = base.getNumEdges();

        // Build candidate graph exactly the same way as candidate evaluation does
        Graph cand = buildCandidateGraph(base, edit, gt);
        if (cand == null) {
            vlog("Rejected: buildCandidateGraph returned null.");
            return false;
        }

        // Use the same baseline notion you're using in pack
        int baselineViol = evalViolationsOnly(base);
        int afterViol = evalViolationsOnly(cand);
        int afterEdges = cand.getNumEdges();

        // Progress gate
        if (!isProgress(baselineViol, afterViol, currentEdges, afterEdges)) {
            vlog("Rejected: not progress (baseline=%d after=%d edges %d->%d).",
                    baselineViol, afterViol, currentEdges, afterEdges);
            return false;
        }

        // Do-no-harm on affected vertices
        Set<String> affected = affectedVertices(base, edit, center, cand);
        Map<String, Double> pBefore = nodePMap(base, affected);
        Map<String, Double> pAfter  = nodePMap(cand, affected);

        if (!respectsDoNoHarm(pBefore, pAfter, center.getName())) {
            vlog("Rejected: violates do-no-harm on affected nodes %s.", affected);
            return false;
        }

        // Actually apply to workingGraph using your normal applier (so Graph button / editor sees it)
        vlog("Attempting guarded move: %s", edit.description());
        boolean ok = applyCandidateInternal(edit, false, false);
        vlog(ok ? "APPLIED successfully" : "Rejected (no change)");
        return ok;
    }
}