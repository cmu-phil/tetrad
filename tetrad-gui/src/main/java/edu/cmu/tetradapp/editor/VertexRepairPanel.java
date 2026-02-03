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
 *
 * <p>
 * For each candidate edit, the panel reports:
 * </p>
 *
 * <ul>
 *   <li><b>Baseline</b> and <b>After</b>: the number of implied conditional independencies
 *       (deduplicated across vertices) that are judged dependent by the data;</li>
 *   <li><b>Δ</b>: the change in the number of such violations relative to the baseline;</li>
 *   <li><b>Node-P</b>: a uniformity p-value for the collection of
 *       p-values implied by the local Markov properties of the repaired node {@code x};</li>
 *   <li><b>Model-P</b>: a uniformity p-value computed over all implied
 *       conditional independence p-values in the model (deduplicated);</li>
 *   <li><b>Edges</b>: the total number of edges in the candidate graph.</li>
 * </ul>
 *
 * <p>
 * Independence test results are cached and reused across candidates to ensure that
 * scoring is efficient even when many candidate edits are evaluated.
 * Graph legality (DAG, CPDAG, MAG, or PAG) is enforced after each edit, and illegal
 * candidates are discarded.
 * </p>
 *
 * <p>
 * The panel maintains an internal edit history, allowing the user to step backward
 * through previously accepted candidate graphs during an interactive repair session.
 * The graph returned by {@link #getGraph()} reflects the final state when the dialog
 * is closed.
 * </p>
 *
 * <p><b>Intended usage:</b></p>
 * <pre>{@code
 * VertexRepairPanel p = new VertexRepairPanel(vertexCheckEditor, x);
 * // show p in a modal dialog
 * Graph repaired = p.getGraph();
 * }</pre>
 *
 * <p>
 * This component is intended as an interactive diagnostic and repair aid.
 * It does not attempt to enforce global optimality or score equivalence,
 * and is most effective when used to explore local modifications suggested
 * by Markov-checker feedback.
 * </p>
 */
public final class VertexRepairPanel extends JPanel {

    private static final String CARD_TABLE = "table";
    private static final String CARD_NONE = "none";
    private static final DecimalFormat MODEL_P_FORMAT = new DecimalFormat("0.0000");
    private static final int DEFAULT_MODELP_TOP_K = 25;

    // ---- Preferences (persist α and model-P top-K) ----
    private static final Preferences PREFS = Preferences.userRoot().node("edu/cmu/tetradapp/editor/VertexRepairPanel");
    // keys
    private static final String PREF_ALPHA = "markovAlpha";
    private static final String PREF_MODEL_P_TOP_K = "modelPTopK";
    // defaults (keep these aligned with UI defaults)
    private static final double DEFAULT_ALPHA = 0.01;

    private final VertexCheckIndTestModel baseModel;
    private final Node x;
    private final Deque<Graph> history = new ArrayDeque<>();

    // UI
    private final JComboBox<RepairGraphType> graphTypeCombo = new JComboBox<>(RepairGraphType.values());
    private final JButton searchButton = new JButton("Search for best node adjustments about x");
    private final JButton backButton = new JButton("Undo");
    private final JButton showGraphButton = new JButton("Show Graph");
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

    public VertexRepairPanel(VertexCheckEditor editor, Node x) {
        super(new BorderLayout());
        this.x = Objects.requireNonNull(x, "x");

        this.baseModel = Objects.requireNonNull(editor.getIndTestModel(), "editor.getIndTestModel()");
        this.Q = Objects.requireNonNull(editor.getCachedQueries(), "editor.getCachedQueries()");
        this.workingGraph = safeCopy(baseModel.getGraph());
        this.model = editor.getIndTestModel();

        // Initialize graph type combo options from graph legality
        initGraphTypeComboFromGraph(this.workingGraph);

        buildUI();

        initPrefTimers();
        loadPrefsIntoUi();

        wireActions();
        updateButtons();

        setPreferredSize(new Dimension(650, 600));
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
        controls.setBorder(new TitledBorder("Repair Node: " + x.getName()));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.gridx = 0;
        c.gridy = 0;
        c.anchor = GridBagConstraints.WEST;

        controls.add(new JLabel("Graph type:"), c);

        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        controls.add(graphTypeCombo, c);

        // Row 2: Markov alpha filter
        c.gridx = 0;
        c.gridy = 1;
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

        TableColumn modelPCol = resultsTable.getColumnModel().getColumn(CandidateTableModel.COL_MODEL_P);
        modelPCol.setCellRenderer(modelPRenderer());

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

    private void applySortAndFilter() {
        if (resultsSorter == null) return;

        // 1) Filter (optional)
        if (markovAlphaFilter.isSelected()) {
            double alpha = parseAlpha(alphaField.getText(), DEFAULT_ALPHA);

            resultsSorter.setRowFilter(new RowFilter<CandidateTableModel, Integer>() {
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

        searchButton.addActionListener(e -> startWatched("Searching", this::runSearchWatched, null));

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
    }

    // ---------------------------------------------------------------------
    // Search logic (watched, background)
    // ---------------------------------------------------------------------

    private void runSearchWatched() {
        // IMPORTANT: do NOT touch Swing components from background thread.
        // We'll compute in the background and then apply results on EDT.

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

        // PASS 2: compute Model-P for top-K only
        List<ScoredCandidate> rankedForTopK = new ArrayList<>(scored);
        rankedForTopK.sort(Comparator
                .comparingInt(ScoredCandidate::violationsAfter)
                .thenComparingInt(ScoredCandidate::edgesAfter)
                .thenComparing(Comparator.comparingDouble(ScoredCandidate::nodePAfter).reversed())
                .thenComparingInt(ScoredCandidate::delta)
        );

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
                    Double modelP = modelPByEditKey.get(sc.edit().key());
                    patched.add(modelP == null ? sc : new ScoredCandidate(
                            sc.edit(), sc.baseline(), sc.after(), sc.nodePAfter(), modelP, sc.edgesAfter()
                    ));
                }
                scored = patched;
            }
        }

        // For status line: choose "best" by the same comparator we used to select top-K
        List<ScoredCandidate> rankedForStatus = new ArrayList<>(scored);
        rankedForStatus.sort(Comparator
                .comparingInt(ScoredCandidate::violationsAfter)
                .thenComparingInt(ScoredCandidate::edgesAfter)
                .thenComparing(Comparator.comparingDouble(ScoredCandidate::nodePAfter).reversed())
                .thenComparingInt(ScoredCandidate::delta)
        );

        ScoredCandidate bestCand = rankedForStatus.isEmpty() ? null : rankedForStatus.get(0);

        // Apply to Swing on EDT
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

    // ---------------------------------------------------------------------
    // Apply / undo / graph view
    // ---------------------------------------------------------------------

    private void applyCandidate(CandidateEdit cand) {
        history.push(safeCopy(workingGraph));

        RepairGraphType gt = (RepairGraphType) graphTypeCombo.getSelectedItem();

        Graph base = safeCopy(workingGraph);
        if (gt == RepairGraphType.CPDAG) {
            base = canonicalizeToCpdagOrNull(base);
            if (base == null) {
                statusLabel.setText("Current graph has no consistent CPDAG extension.");
                if (!history.isEmpty()) history.pop();
                return;
            }
        }

        Graph g2 = cand.applyTo(base);
        if (g2 != null && gt == RepairGraphType.CPDAG) {
            g2 = canonicalizeToCpdagOrNull(g2);
        }

        if (g2 != null) {
            workingGraph = g2;
            statusLabel.setText("Applied: " + cand.description());
        } else {
            statusLabel.setText("Failed to apply: " + cand.description());
            if (!history.isEmpty()) history.pop();
        }

        updateButtons();
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

    /**
     * Endpoint/orientation variants you’re willing to consider for an existing adjacency x—y.
     * For replacement edits, it’s fine if some variants are “nonsense” for the graph type—
     * legality filtering later can reject them—but keep the menu small.
     */
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
                variants.add(new Edge(x, y, Endpoint.TAIL, Endpoint.TAIL));     // x---y (selection-bias adjacency, if you use it)
            }
        }

        return variants;
    }

    /**
     * For ADD candidates: keep it conservative.
     */
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

    /**
     * Sets the knowledge object for the VertexRepairPanel.
     *
     * @param knowledge the new Knowledge object to be assigned to the panel (null treated as empty Knowledge)
     */
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

    // ---------- Locality-based global evaluation ----------

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

    /**
     * Build + canonicalize + legality-check a candidate graph, returning null if illegal.
     * IMPORTANT: this is used in BOTH passes so the result is consistent.
     */
    private Graph buildCandidateGraph(Graph base, CandidateEdit cand, RepairGraphType gt) {
        if (base == null || cand == null) return null;

        Graph g2 = cand.applyTo(safeCopy(base));
        if (g2 == null) return null;

        if (gt == RepairGraphType.CPDAG) {
            g2 = canonicalizeToCpdagOrNull(g2);
            if (g2 == null) return null;

            if (!cand.isNoOp() && g2.equals(base)) return null;
        } else if (gt == RepairGraphType.PAG) {
            // keep as-is (no candidate canonicalization by default)
        } else if (gt == RepairGraphType.PDAG) {
            // keep as-is (you may optionally canonicalize here too)
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

        // also recompute current neighbors (optional)
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
        // Prevent parallel runs.
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
                    searchButton.setEnabled(true);
                    updateButtons();
                }
            }
        };

        this.activeWorker = worker;
        searchButton.setEnabled(false);
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

    /**
     * Convenience: treat either SwingWorker cancel or interrupt as "stop requested".
     */
    private boolean stopRequested() {
        SwingWorker<?, ?> w = activeWorker;
        return (w != null && w.isCancelled()) || Thread.currentThread().isInterrupted();
    }

    // ---------------------------------------------------------------------
    // Types
    // ---------------------------------------------------------------------

    /**
     * IMPORTANT: do NOT name this GraphType, since edu.cmu.tetrad.graph.GraphType exists.
     */
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
                    // return a defensive copy so later mutations can't surprise you
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

        // ---- factories ----

        String description();

        /**
         * Apply this edit to the given graph and return the modified graph.
         * Implementations should NOT mutate the input graph.
         */
        Graph applyTo(Graph g);

        default boolean isNoOp() {
            return false;
        }

        /**
         * Optional key for de-dup.
         */
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
        private static final int COL_NODE_P = 4;   // node p
        private static final int COL_MODEL_P = 5;    // model p
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

    /**
     * Per-vertex evaluated contribution:
     * - dedup facts by factKey within the vertex
     * - keep for each fact key: (isViolation, pValue)
     */
    private record VertexContribution(
            Map<String, Boolean> violationByKey,
            Map<String, Double> pByKey
    ) {
    }

    private record GlobalEvalCache(
            Map<String, VertexContribution> contribByVertexName
    ) {
    }
}