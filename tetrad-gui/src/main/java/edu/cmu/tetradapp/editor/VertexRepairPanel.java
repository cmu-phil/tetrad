package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.test.CachedIndependenceQueries;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetradapp.model.VertexCheckIndTestModel;
import edu.cmu.tetradapp.workbench.GraphWorkbench;
import org.apache.commons.math3.distribution.UniformRealDistribution;
import org.apache.commons.math3.stat.inference.KolmogorovSmirnovTest;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.*;
import java.awt.*;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;
import java.util.List;

import java.util.prefs.Preferences;
import javax.swing.Timer;

import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;


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
 * <ul>
 *   <li><b>Baseline</b> and <b>After</b>: the number of implied conditional independencies
 *       (deduplicated across vertices) that are judged dependent by the data;</li>
 *   <li><b>Δ</b>: the change in the number of such violations relative to the baseline;</li>
 *   <li><b>N-KS</b>: a Kolmogorov–Smirnov uniformity p-value for the collection of
 *       p-values implied by the local Markov properties of the repaired node {@code x};</li>
 *   <li><b>M-KS</b>: a Kolmogorov–Smirnov uniformity p-value computed over all implied
 *       conditional independence p-values in the model (deduplicated);</li>
 *   <li><b>Edges</b>: the total number of edges in the candidate graph.</li>
 * </ul>
 * </p>
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
    private static final DecimalFormat KS_FORMAT = new DecimalFormat("0.0000");
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
    private final JCheckBox markovAlphaFilter = new JCheckBox("Hide rows with M-KS < α");
    private final JTextField alphaField = new JTextField("0.01", 6);
    CachedIndependenceQueries Q;
    private Graph workingGraph;
    private Knowledge knowledge = new Knowledge();
    // Keep a handle to the sorter so we can change filter/sort dynamically
    private TableRowSorter<CandidateTableModel> resultsSorter;
    private static final int DEFAULT_KS_TOP_K = 25;
    private final JTextField ksTopKField = new JTextField(String.valueOf(DEFAULT_KS_TOP_K), 5);
    private volatile int ksTopK = DEFAULT_KS_TOP_K;

    // ---- Preferences (persist α and KS top-K) ----
    private static final Preferences PREFS = Preferences.userRoot().node("edu/cmu/tetradapp/editor/VertexRepairPanel");

    // keys
    private static final String PREF_ALPHA = "markovAlpha";
    private static final String PREF_KS_TOP_K = "ksTopK";

    // defaults (keep these aligned with UI defaults)
    private static final double DEFAULT_ALPHA = 0.01;

    // debounce timers so we don’t write prefs on every keystroke
    private final Timer alphaSaveTimer = new Timer(350, e -> saveAlphaPref());
    private final Timer topKSaveTimer  = new Timer(350, e -> saveTopKPref());

    // --- Watch dialog state (one at a time) ---
    private volatile SwingWorker<?, ?> activeWorker;
    private volatile JDialog watchDialog;

    public VertexRepairPanel(VertexCheckEditor editor, Node x) {
        super(new BorderLayout());
        this.x = Objects.requireNonNull(x, "x");

        this.baseModel = Objects.requireNonNull(editor.getIndTestModel(), "editor.getIndTestModel()");
        this.Q = Objects.requireNonNull(editor.getCachedQueries(), "editor.getCachedQueries()");
        this.workingGraph = safeCopy(baseModel.getGraph());

        // ADD THIS LINE:
        initGraphTypeComboFromGraph(this.workingGraph);

        buildUI();

        initPrefTimers();
        loadPrefsIntoUi();

        wireActions();
        updateButtons();

        wireActions();
        updateButtons();

        setPreferredSize(new Dimension(650, 600));
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

    /**
     * Canonical key for de-duping implied facts by names: (X,Y unordered; Z sorted).
     */
    public static String factKey(IndependenceFact f) {
        return queryKey(f);
    }

    /**
     * Canonical key for caching CI queries: (X,Y unordered; Z sorted).
     */
    private static String queryKey(IndependenceFact f) {
        String a = f.getX().getName();
        String b = f.getY().getName();
        if (a.compareTo(b) > 0) {
            String t = a;
            a = b;
            b = t;
        }

        List<String> z = new ArrayList<>();
        for (Node n : f.getZ()) z.add(n.getName());
        Collections.sort(z);

        return a + "|" + b + "|" + String.join(",", z);
    }

    private static TableCellRenderer ksRenderer() {
        return new DefaultTableCellRenderer() {
            @Override
            public void setValue(Object value) {
                if (value instanceof Number n) {
                    double d = n.doubleValue();
                    setText(Double.isNaN(d) ? "" : KS_FORMAT.format(d));
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

    private double ksUniformPValue(List<Double> pvals) {
        if (pvals == null || pvals.size() < 2) return Double.NaN;

        double[] x = pvals.stream().mapToDouble(Double::doubleValue).toArray();
        KolmogorovSmirnovTest ks = new KolmogorovSmirnovTest();
        return ks.kolmogorovSmirnovTest(new UniformRealDistribution(0.0, 1.0), x);
    }

    /**
     * KS uniformity p-value for the p-values implied by the local Markov facts for a single vertex.
     * Uses the same p-value cache as the rest of the repair panel.
     */
//    private double nodeKsPValue(Graph g, Node vertexInOriginalGraph) {
//        if (g == null || vertexInOriginalGraph == null) return Double.NaN;
//
//        // IMPORTANT: g is usually a copy; re-find the vertex by name inside g.
//        Node v = g.getNode(vertexInOriginalGraph.getName());
//        if (v == null) return Double.NaN;
//
//        // Local Markov implied facts for this vertex (under current ConditioningSetType etc.)
//        List<IndependenceFact> facts = baseModel.computeImpliedFactsForVertex(g, v);
//        if (facts == null || facts.isEmpty()) return Double.NaN;
//
//        // Dedup within-node exactly the same way you dedup elsewhere: by factKey
//        Map<String, Double> keyToP = new LinkedHashMap<>();
//        for (IndependenceFact f : facts) {
//            double p = getPValueCached(baseModel.getIndependenceTest(), f);
//            if (Double.isNaN(p)) continue;
//            keyToP.putIfAbsent(factKey(f), p);
//        }
//
//        if (keyToP.isEmpty()) return Double.NaN;
//        return ksUniformPValue(new ArrayList<>(keyToP.values()));
//    }
    private double nodeKsPValue(Graph g, Node vertexInOriginalGraph) {
        if (g == null || vertexInOriginalGraph == null) return Double.NaN;

        // Re-find by name inside g (since g is often a copy).
        Node v = g.getNode(vertexInOriginalGraph.getName());
        if (v == null) return Double.NaN;

        List<IndependenceFact> facts = baseModel.computeImpliedFactsForVertex(g, v);
        if (facts == null || facts.isEmpty()) return Double.NaN;

        Map<String, Double> keyToP = new LinkedHashMap<>();
        for (IndependenceFact f : facts) {
            if (f == null) continue;
            double p = pValueOf(f);
            if (Double.isNaN(p)) continue;
            keyToP.putIfAbsent(factKey(f), p);
        }

        if (keyToP.isEmpty()) return Double.NaN;
        return ksUniformPValue(new ArrayList<>(keyToP.values()));
    }

    private List<Double> collectAllImpliedPValuesDedup(Graph g) {
        if (g == null) return List.of();

        List<IndependenceFact> facts = baseModel.computeAllImpliedFacts(g);
        if (facts == null || facts.isEmpty()) return List.of();

        Map<String, Double> keyToP = new LinkedHashMap<>();

        for (IndependenceFact f : facts) {
            if (f == null) continue;
            double p = pValueOf(f);
            if (Double.isNaN(p)) continue;
            keyToP.putIfAbsent(factKey(f), p);
        }

        return new ArrayList<>(keyToP.values());
    }

    /**
     * Caller reads this after dialog closes.
     */
    public Graph getGraph() {
        return workingGraph;
    }

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

        alphaPanel.add(new JLabel("KS top-K:"));
        alphaPanel.add(ksTopKField);

        controls.add(alphaPanel, c);

        // Search button now directly under graph type row
        c.gridx = 0;
        c.gridy = 1;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;

        c.gridy = 2;

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
//                    applyCandidate(cand);
//                    runSearch();
                    applyCandidate(cand);
                    startWatched("Searching", this::runSearchWatched, null);
                }));

        resultsTable.setTransferHandler(new DefaultTableTransferHandler(0));

        TableColumnModel cm = resultsTable.getColumnModel();

        TableColumn ksCol =
                resultsTable.getColumnModel().getColumn(CandidateTableModel.COL_KS);

        ksCol.setCellRenderer(ksRenderer());


        resultsTable.getColumnModel().getColumn(CandidateTableModel.COL_KS)
                .setCellRenderer(new DefaultTableCellRenderer() {
                    @Override
                    public void setValue(Object value) {
                        if (value instanceof Number n) {
                            double d = n.doubleValue();
                            setHorizontalAlignment(SwingConstants.RIGHT);
                            setText(Double.isNaN(d) ? "" : KS_FORMAT.format(d));
                        } else setText("");
                    }
                });

        resultsTable.getColumnModel().getColumn(CandidateTableModel.COL_NKS)
                .setCellRenderer(new DefaultTableCellRenderer() {
                    @Override
                    public void setValue(Object value) {
                        if (value instanceof Number n) {
                            double d = n.doubleValue();
                            setHorizontalAlignment(SwingConstants.RIGHT);
                            setText(Double.isNaN(d) ? "" : KS_FORMAT.format(d));
                        } else setText("");
                    }
                });


//         Column indices assumed; adjust if needed
        TableColumn editIndex = cm.getColumn(0);
        TableColumn baselineIndex = cm.getColumn(1);
        TableColumn afterIndex = cm.getColumn(2);
        TableColumn deltaIndex = cm.getColumn(3);
        TableColumn nodeKsIndex = cm.getColumn(4);
        TableColumn kstestIndex = cm.getColumn(5);
        TableColumn edgesIndex = cm.getColumn(6);
        TableColumn applyIndex = cm.getColumn(7);

        // # column: very skinny
        baselineIndex.setPreferredWidth(50);
        baselineIndex.setMinWidth(50);
        baselineIndex.setMaxWidth(50);

        // Result column: "INDEPENDENT"
        afterIndex.setPreferredWidth(50);
        afterIndex.setMinWidth(50);
        afterIndex.setMaxWidth(50);

        // p-value column: ~6–8 chars
        deltaIndex.setMinWidth(50);
        afterIndex.setMaxWidth(50);
        deltaIndex.setPreferredWidth(50);

        // node ks delta columns
        nodeKsIndex.setMinWidth(70);
        nodeKsIndex.setMaxWidth(70);
        nodeKsIndex.setPreferredWidth(70);

        // ks delta columns
        kstestIndex.setMinWidth(70);
        kstestIndex.setMaxWidth(70);
        kstestIndex.setPreferredWidth(70);

        // edges columns
        edgesIndex.setMinWidth(50);
        edgesIndex.setMaxWidth(50);
        edgesIndex.setPreferredWidth(50);

//        applyIndex.setWidth(80);
//        applyIndex.setMaxWidth(80);
        applyIndex.setMinWidth(70);

        // Fact column: stretch
        editIndex.setPreferredWidth(1000);
        // no max width → absorbs remaining space

//        resultsTable.setRowSorter(new TableRowSorter<>(resultsModel));

        resultsSorter = new TableRowSorter<>(resultsModel);
        resultsTable.setRowSorter(resultsSorter);

        // Ensure numeric columns sort numerically (defensive if model ever returns Strings)
//        resultsSorter.setComparator(CandidateTableModel.COL_KS, Comparator.comparingDouble(a -> ((Number) a).doubleValue()));


        resultsSorter.setComparator(CandidateTableModel.COL_KS, (a, b) -> {
            double da = (a instanceof Number na) ? na.doubleValue() : Double.NaN;
            double db = (b instanceof Number nb) ? nb.doubleValue() : Double.NaN;

            boolean aNaN = Double.isNaN(da);
            boolean bNaN = Double.isNaN(db);

            if (aNaN && bNaN) return 0;
            if (aNaN) return 1;   // NaN last
            if (bNaN) return -1;  // NaN last

            return Double.compare(da, db);
        });

        resultsSorter.setComparator(CandidateTableModel.COL_NKS, Comparator.comparingDouble(a -> ((Number) a).doubleValue()));
        resultsSorter.setComparator(CandidateTableModel.COL_EDGES, Comparator.comparingInt(a -> ((Number) a).intValue()));
        resultsSorter.setComparator(CandidateTableModel.COL_AFTER, Comparator.comparingInt(a -> ((Number) a).intValue()));
        resultsSorter.setComparator(CandidateTableModel.COL_DELTA, Comparator.comparingInt(a -> ((Number) a).intValue()));

//        resultsSorter.setSortKeys(List.of(
//                new RowSorter.SortKey(CandidateTableModel.COL_EDGES, SortOrder.ASCENDING)
//        ));

        resultsSorter.setSortKeys(List.of(
                new RowSorter.SortKey(CandidateTableModel.COL_EDGES, SortOrder.ASCENDING), // <-- first
                new RowSorter.SortKey(CandidateTableModel.COL_KS, SortOrder.DESCENDING),  // then M-KS
                new RowSorter.SortKey(CandidateTableModel.COL_AFTER, SortOrder.ASCENDING),
                new RowSorter.SortKey(CandidateTableModel.COL_DELTA, SortOrder.ASCENDING)
        ));

        // Default multi-key sort: M-KS desc, Edges asc, After asc (tweak as you like)
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
            double alpha = parseAlpha(alphaField.getText(), 0.01);
            resultsSorter.setRowFilter(new RowFilter<>() {
                @Override
                public boolean include(Entry<? extends CandidateTableModel, ? extends Integer> e) {
                    Object v = e.getValue(CandidateTableModel.COL_KS); // M-KS column
                    if (!(v instanceof Number n)) return false;
                    double p = n.doubleValue();
//                    return !Double.isNaN(p) && p >= alpha;
                    return Double.isNaN(p) || p >= alpha;
                }
            });
        } else {
            resultsSorter.setRowFilter(null);
        }

        // 2) Sort keys (lexicographic)
        resultsSorter.setSortKeys(List.of(
                new RowSorter.SortKey(CandidateTableModel.COL_EDGES, SortOrder.ASCENDING), // fewer edges
                new RowSorter.SortKey(CandidateTableModel.COL_KS, SortOrder.DESCENDING),   // M-KS high first
                new RowSorter.SortKey(CandidateTableModel.COL_AFTER, SortOrder.ASCENDING), // fewer violations
                new RowSorter.SortKey(CandidateTableModel.COL_DELTA, SortOrder.ASCENDING)  // more improvement (more negative)
        ));

        resultsSorter.sort();
    }

    private void wireActions() {
        backButton.addActionListener(e -> goBack());
        showGraphButton.addActionListener(e -> showGraphDialog());

//        searchButton.addActionListener(e -> {
//            searchButton.setEnabled(false);
//            statusLabel.setText("Searching...");
//
//            // NOTE: first-shot: run on EDT (simple). If it becomes slow, replace with SwingWorker.
//            SwingUtilities.invokeLater(() -> {
//                try {
//                    runSearch();
//                } finally {
//                    searchButton.setEnabled(true);
//                    updateButtons();
//                }
//            });
//        });

        searchButton.addActionListener(e -> {
            startWatched("Searching", () -> {
                runSearchWatched();   // new method below
            }, () -> {
                // nothing extra; runSearchWatched updates the table itself (on EDT via invokeLater inside)
            });
        });

        markovAlphaFilter.addActionListener(e -> applySortAndFilter());
        markovAlphaFilter.setSelected(true);

//        alphaField.addActionListener(e -> applySortAndFilter());
//        // Enter key
//
//        alphaField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
//            @Override
//            public void insertUpdate(javax.swing.event.DocumentEvent e) {
//                applySortAndFilter();
//            }
//
//            @Override
//            public void removeUpdate(javax.swing.event.DocumentEvent e) {
//                applySortAndFilter();
//            }
//
//            @Override
//            public void changedUpdate(javax.swing.event.DocumentEvent e) {
//                applySortAndFilter();
//            }
//        });

        alphaField.addActionListener(e -> {
            applySortAndFilter();
            saveAlphaPref(); // Enter key = commit immediately
        });

        alphaField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { applySortAndFilter(); alphaSaveTimer.restart(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { applySortAndFilter(); alphaSaveTimer.restart(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { applySortAndFilter(); alphaSaveTimer.restart(); }
        });

//        ksTopKField.addActionListener(e -> {
//            ksTopK = parseTopK(ksTopKField.getText(), DEFAULT_KS_TOP_K);
//            applySortAndFilter(); // optional; mostly for consistency
//        });
//
//        ksTopKField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
//            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { ksTopK = parseTopK(ksTopKField.getText(), DEFAULT_KS_TOP_K); }
//            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { ksTopK = parseTopK(ksTopKField.getText(), DEFAULT_KS_TOP_K); }
//            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { ksTopK = parseTopK(ksTopKField.getText(), DEFAULT_KS_TOP_K); }
//        });

        ksTopKField.addActionListener(e -> {
            ksTopK = parseTopK(ksTopKField.getText(), DEFAULT_KS_TOP_K);
            applySortAndFilter();
            saveTopKPref();                 // <-- add this (Enter commits immediately)
        });

        ksTopKField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) {
                ksTopK = parseTopK(ksTopKField.getText(), DEFAULT_KS_TOP_K);
                topKSaveTimer.restart();     // <-- add this
            }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) {
                ksTopK = parseTopK(ksTopKField.getText(), DEFAULT_KS_TOP_K);
                topKSaveTimer.restart();     // <-- add this
            }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) {
                ksTopK = parseTopK(ksTopKField.getText(), DEFAULT_KS_TOP_K);
                topKSaveTimer.restart();     // <-- add this
            }
        });
    }

    private void updateButtons() {
        backButton.setEnabled(!history.isEmpty());
    }

    private void runSearch() {
        RepairGraphType gt = (RepairGraphType) graphTypeCombo.getSelectedItem();
        Graph base = safeCopy(workingGraph);

        if (gt == RepairGraphType.CPDAG || gt == RepairGraphType.PDAG) {
            base = canonicalizeToCpdagOrNull(base);
            if (base == null) {
                statusLabel.setText("Current graph has no consistent CPDAG extension.");
                ((CardLayout) resultsCard.getLayout()).show(resultsCard, CARD_NONE);
                return;
            }
        } else if (gt == RepairGraphType.PAG) {
            base = canonicalizeToPagOrNull(base);
            if (base == null) {
                statusLabel.setText("Current graph has no consistent PAG extension.");
                ((CardLayout) resultsCard.getLayout()).show(resultsCard, CARD_NONE);
                return;
            }
        }

        if (knowledge.isViolatedBy(base)) {
            statusLabel.setText("Current graph violates the knowledge base.");
            ((CardLayout) resultsCard.getLayout()).show(resultsCard, CARD_NONE);
            return;
        }

        // 1) enumerate candidate edits around x
        List<CandidateEdit> candidates = enumerateCandidates(base, x, gt);

        // Ensure “no-op” is present at top (baseline)
        candidates = new ArrayList<>(candidates);
        if (candidates.stream().noneMatch(CandidateEdit::isNoOp)) {
            candidates.addFirst(CandidateEdit.noOp());
        }

        // 3) score candidates
        List<ScoredCandidate> scored = new ArrayList<>();
        resultsModel.set(scored);

        GlobalEvalCache baseCache = buildBaselineCache(base);

        GraphEval baseEval = evalGraphLocality(base, baseCache, base, Set.of(), true);
        int baseline = baseEval.violations();
        double baselineKs = baseEval.ksP();

        Map<String, Graph> candGraphByKey = new HashMap<>();

        // ------------------------------
        // PASS 1: compute After + N-KS + edges for ALL candidates, but do NOT compute global M-KS yet
        // ------------------------------
        for (CandidateEdit cand : candidates) {
//            Graph g2 = buildCandidateGraph(base, cand, gt);
            Graph finalBase = base;
            Graph g2 = candGraphByKey.computeIfAbsent(cand.key(), k -> buildCandidateGraph(finalBase, cand, gt));
            if (g2 == null) continue;

            boolean useLocality = (gt == RepairGraphType.DAG || gt == RepairGraphType.CPDAG || gt == RepairGraphType.PDAG);

//            int after = evalViolationsOnly(g2);     // cheap-ish pass

            Set<String> affected = affectedVertices(base, cand, x, g2);
//            int after = evalGraphLocality(base, baseCache, g2, affected, false).violations();

            int after = useLocality
                    ? evalGraphLocality(base, baseCache, g2, affected, false).violations()
                    : evalViolationsOnly(g2);

            double nodeKsAfter = nodeKsPValue(g2, x); // local only
            int edgesAfter = g2.getNumEdges();

            // M-KS intentionally left as NaN for now; we’ll fill it in for top K only
            scored.add(new ScoredCandidate(cand, baseline, after, nodeKsAfter, Double.NaN, edgesAfter));
        }

        // ------------------------------
        // PASS 2: compute global M-KS only for top K candidates
        // ------------------------------
        if (!scored.isEmpty()) {
            // Provisional ranking to choose which candidates deserve expensive global M-KS.
            // Tune this comparator if you want different “top K” semantics.
            List<ScoredCandidate> ranked = new ArrayList<>(scored);
            ranked.sort(Comparator
                    .comparingInt(ScoredCandidate::violationsAfter)                  // fewer violations first
                    .thenComparingInt(ScoredCandidate::edgesAfter)                   // fewer edges next
                    .thenComparing(Comparator.comparingDouble(ScoredCandidate::nodeKsAfter).reversed()) // higher N-KS better
                    .thenComparingInt(ScoredCandidate::delta)                        // more improvement (more negative) earlier
            );

            int k = Math.min(ksTopK, ranked.size());

            // Compute KS for top K only; key by CandidateEdit.key() so we can patch back into the main list.
            Map<String, Double> ksByEditKey = new HashMap<>(k * 2);

            for (int i = 0; i < k; i++) {
                ScoredCandidate sc = ranked.get(i);
                CandidateEdit cand = sc.edit();

//                Graph g2 = buildCandidateGraph(base, cand, gt);
//                if (g2 == null) continue;

                Graph g2 = candGraphByKey.get(sc.edit().key());
                if (g2 == null) continue;

                // Expensive part: global M-KS
//                double ksAfter = evalGraphOnce(g2).ksP();

                Set<String> affected = affectedVertices(base, cand, x, g2);
                double ksAfter = evalGraphLocality(base, baseCache, g2, affected, true).ksP();

                ksByEditKey.put(cand.key(), ksAfter);
            }

            // Patch M-KS back into the full scored list by rebuilding list with updated ksAfter where available.
            if (!ksByEditKey.isEmpty()) {
                List<ScoredCandidate> patched = new ArrayList<>(scored.size());
                for (ScoredCandidate sc : scored) {
                    Double ks = ksByEditKey.get(sc.edit().key());
                    if (ks == null) {
                        patched.add(sc);
                    } else {
                        patched.add(new ScoredCandidate(
                                sc.edit(),
                                sc.baseline(),
                                sc.after(),
                                sc.nodeKsAfter(),
                                ks,
                                sc.edgesAfter()
                        ));
                    }
                }
                scored = patched;
            }
        }

//        // Sort by improvement (most negative delta first), then by absolute violations
//        scored.sort(Comparator
//                .comparingInt(ScoredCandidate::violationsAfter)          // lower better
//                .thenComparing(Comparator.comparingDouble(ScoredCandidate::ksAfter).reversed()) // higher better
//                .thenComparingInt(ScoredCandidate::edgesAfter)           // lower better
//        );

        resultsModel.set(scored);

        applySortAndFilter();

        NumberFormat fmt = new DecimalFormat("0.0000");

        if (scored.isEmpty()) {
            statusLabel.setText("No legal candidate edits found.");
            ((CardLayout) resultsCard.getLayout()).show(resultsCard, CARD_NONE);
        } else {
            int best = scored.getFirst().violationsAfter();
            statusLabel.setText(
                    "Baseline violations: " + baseline +
                            " | Best: " + best +
                            " | KS(all): " + fmt.format(baselineKs) + " → " + fmt.format(scored.getFirst().ksAfter())
            );
            ((CardLayout) resultsCard.getLayout()).show(resultsCard, CARD_TABLE);
        }
    }

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

        if (knowledge.isViolatedBy(base)) {
            SwingUtilities.invokeLater(() -> {
                statusLabel.setText("Current graph violates the knowledge base.");
                ((CardLayout) resultsCard.getLayout()).show(resultsCard, CARD_NONE);
            });
            return;
        }

        List<CandidateEdit> candidates = enumerateCandidates(base, x, gt);
        candidates = new ArrayList<>(candidates);
        if (candidates.stream().noneMatch(CandidateEdit::isNoOp)) candidates.addFirst(CandidateEdit.noOp());

        if (stopRequested()) return;

        GlobalEvalCache baseCache = buildBaselineCache(base);
        GraphEval baseEval = evalGraphLocality(base, baseCache, base, Set.of(), true);

        int baseline = baseEval.violations();
        double baselineKs = baseEval.ksP();

        Map<String, Graph> candGraphByKey = new HashMap<>();
        List<ScoredCandidate> scored = new ArrayList<>();

        // PASS 1
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

            double nodeKsAfter = nodeKsPValue(g2, x);
            int edgesAfter = g2.getNumEdges();

            scored.add(new ScoredCandidate(cand, baseline, after, nodeKsAfter, Double.NaN, edgesAfter));
        }

        if (stopRequested()) return;

        // PASS 2 (top-K KS)
        if (!scored.isEmpty()) {
            List<ScoredCandidate> ranked = new ArrayList<>(scored);
            ranked.sort(Comparator
                    .comparingInt(ScoredCandidate::violationsAfter)
                    .thenComparingInt(ScoredCandidate::edgesAfter)
                    .thenComparing(Comparator.comparingDouble(ScoredCandidate::nodeKsAfter).reversed())
                    .thenComparingInt(ScoredCandidate::delta)
            );

            int k = Math.min(ksTopK, ranked.size());
            Map<String, Double> ksByEditKey = new HashMap<>(k * 2);

            for (int i = 0; i < k; i++) {
                if (stopRequested()) return;

                ScoredCandidate sc = ranked.get(i);
                CandidateEdit cand = sc.edit();

                Graph g2 = candGraphByKey.get(cand.key());
                if (g2 == null) continue;

                Set<String> affected = affectedVertices(base, cand, x, g2);
                double ksAfter = evalGraphLocality(base, baseCache, g2, affected, true).ksP();
                ksByEditKey.put(cand.key(), ksAfter);
            }

            if (!ksByEditKey.isEmpty()) {
                List<ScoredCandidate> patched = new ArrayList<>(scored.size());
                for (ScoredCandidate sc : scored) {
                    Double ks = ksByEditKey.get(sc.edit().key());
                    patched.add(ks == null ? sc : new ScoredCandidate(
                            sc.edit(), sc.baseline(), sc.after(), sc.nodeKsAfter(), ks, sc.edgesAfter()
                    ));
                }
                scored = patched;
            }
        }

        // Now apply to Swing on EDT
        List<ScoredCandidate> finalScored = scored;
        SwingUtilities.invokeLater(() -> {
            resultsModel.set(finalScored);
            applySortAndFilter();

            NumberFormat fmt = new DecimalFormat("0.0000");
            if (finalScored.isEmpty()) {
                statusLabel.setText("No legal candidate edits found.");
                ((CardLayout) resultsCard.getLayout()).show(resultsCard, CARD_NONE);
            } else {
                int best = finalScored.getFirst().violationsAfter();
                statusLabel.setText(
                        "Baseline violations: " + baseline +
                                " | Best: " + best +
                                " | KS(all): " + fmt.format(baselineKs) + " → " + fmt.format(finalScored.getFirst().ksAfter())
                );
                ((CardLayout) resultsCard.getLayout()).show(resultsCard, CARD_TABLE);
            }
        });
    }

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

//    private void goBack() {
//        if (history.isEmpty()) return;
//        workingGraph = history.pop();
//        statusLabel.setText("Reverted to previous graph.");
//        updateButtons();
//        searchButton.setEnabled(false);
//        statusLabel.setText("Searching...");
//
//        // NOTE: first-shot: run on EDT (simple). If it becomes slow, replace with SwingWorker.
//        SwingUtilities.invokeLater(() -> {
//            try {
//                runSearch();
//            } finally {
//                searchButton.setEnabled(true);
//                updateButtons();
//            }
//        });
//    }

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

//        EditorWindow editorWindow = new EditorWindow(tabs, "Graph", "OK", false, this);
//
//        DesktopController.getInstance().addEditorWindow(editorWindow, JLayeredPane.PALETTE_LAYER);
//        editorWindow.pack();
//        editorWindow.setVisible(true);

        JOptionPane.showMessageDialog(
                this,
                tabs,
                "Current Graph",
                JOptionPane.INFORMATION_MESSAGE
        );
    }

    private List<CandidateEdit> enumerateCandidates(Graph g, Node x, RepairGraphType gt) {
        if (g == null || x == null) return List.of(CandidateEdit.noOp());

        List<CandidateEdit> out = new ArrayList<>();
        out.add(CandidateEdit.noOp());

        // ------------------------
        // 0) Build the add-edge pool
        // ------------------------
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
                // Only directed edges.
                variants.add(new Edge(x, y, Endpoint.TAIL, Endpoint.ARROW)); // x->y
                variants.add(new Edge(y, x, Endpoint.TAIL, Endpoint.ARROW)); // y->x
            }
            case CPDAG, PDAG -> {
                // Directed or undirected.
                variants.add(new Edge(x, y, Endpoint.TAIL, Endpoint.TAIL));  // x---y
                variants.add(new Edge(x, y, Endpoint.TAIL, Endpoint.ARROW)); // x->y
                variants.add(new Edge(y, x, Endpoint.TAIL, Endpoint.ARROW)); // y->x
            }
            case MAG -> {
                // MAG endpoints typically: ->, <-, <->
                variants.add(new Edge(x, y, Endpoint.TAIL, Endpoint.ARROW));   // x->y
                variants.add(new Edge(y, x, Endpoint.TAIL, Endpoint.ARROW));   // y->x
                variants.add(new Edge(x, y, Endpoint.ARROW, Endpoint.ARROW));  // x<->y

                // Optional: some MAG codebases allow “---” temporarily; if yours doesn't, omit.
                // variants.add(edge(x, y, Endpoint.TAIL, Endpoint.TAIL));  // x---y (usually not MAG-legal)
            }
            case PAG -> {
                // PAG endpoints can be CIRCLE/TAIL/ARROW. Keep a small “useful” menu.
                variants.add(new Edge(x, y, Endpoint.CIRCLE, Endpoint.CIRCLE)); // o-o
                variants.add(new Edge(x, y, Endpoint.CIRCLE, Endpoint.ARROW));  // x o-> y
                variants.add(new Edge(y, x, Endpoint.CIRCLE, Endpoint.ARROW));  // y o-> x  (i.e., x <-o y)
                variants.add(new Edge(x, y, Endpoint.TAIL, Endpoint.ARROW));    // x->y
                variants.add(new Edge(y, x, Endpoint.TAIL, Endpoint.ARROW));    // y->x
                variants.add(new Edge(x, y, Endpoint.ARROW, Endpoint.ARROW));   // x<->y (allowed in PAGs)
                variants.add(new Edge(x, y, Endpoint.TAIL, Endpoint.TAIL));     // x---y (if you use this for selection-bias adjacency)
            }
        }

        return variants;
    }

    // ---------------- data model classes ----------------

    /**
     * For ADD candidates: keep it *even more conservative* than replacement.
     * In particular: for CPDAG, prefer undirected addition; for MAG, prefer -> and <->
     * only if you know you handle it; for PAG, prefer o-o and o-> / <-o.
     */
    private List<Edge> addMenuForPair(Node x, Node y, RepairGraphType gt) {
        List<Edge> adds = new ArrayList<>();

        switch (gt) {
            case DAG -> {
                adds.add(new Edge(x, y, Endpoint.TAIL, Endpoint.ARROW)); // x->y
                adds.add(new Edge(y, x, Endpoint.TAIL, Endpoint.ARROW)); // y->x
            }
            case CPDAG -> {
                // Conservative: add as undirected; you can let later rules orient.
                adds.add(new Edge(x, y, Endpoint.TAIL, Endpoint.TAIL));  // x---y

                // Optional: if you want, include directed adds too.
                adds.add(new Edge(x, y, Endpoint.TAIL, Endpoint.ARROW)); // x->y
                adds.add(new Edge(y, x, Endpoint.TAIL, Endpoint.ARROW)); // y->x
            }
            case MAG -> {
                // MAG: adjacency is directed or bidirected. Adding tail-tail is usually illegal.
                adds.add(new Edge(x, y, Endpoint.TAIL, Endpoint.ARROW));   // x->y
                adds.add(new Edge(y, x, Endpoint.TAIL, Endpoint.ARROW));   // y->x
                adds.add(new Edge(x, y, Endpoint.ARROW, Endpoint.ARROW));  // x<->y
            }
            case PAG -> {
                // Conservative PAG additions:
                adds.add(new Edge(x, y, Endpoint.CIRCLE, Endpoint.CIRCLE)); // o-o
                adds.add(new Edge(x, y, Endpoint.CIRCLE, Endpoint.ARROW));  // x o-> y
                adds.add(new Edge(y, x, Endpoint.CIRCLE, Endpoint.ARROW));  // y o-> x  (x <-o y)

                // Optional: include definite orientations too.
                adds.add(new Edge(x, y, Endpoint.TAIL, Endpoint.ARROW));    // x->y
                adds.add(new Edge(y, x, Endpoint.TAIL, Endpoint.ARROW));    // y->x
                adds.add(new Edge(x, y, Endpoint.ARROW, Endpoint.ARROW));   // x<->y
                adds.add(new Edge(x, y, Endpoint.TAIL, Endpoint.TAIL));     // x---y
            }
        }

        return adds;
    }

    private Graph canonicalizeToCpdagOrNull(Graph h) {
        try {
            Graph h2 = new EdgeListGraph(h);
            Graph dag = GraphTransforms.dagFromCpdag(h2);
            return GraphTransforms.dagToCpdag(dag);
        } catch (Throwable t) {
            return null; // no consistent extension => can't be a CPDAG candidate
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
            return null; // no consistent extension => can't be a CPDAG candidate
        }
    }

    private boolean isLegalGraphType(Graph g, RepairGraphType gt) {
        return switch (gt) {
            case DAG -> g.paths().isLegalDag();
            case CPDAG -> g.paths().isLegalCpdag();
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
            // fallback if copy() isn't implemented
            return new EdgeListGraph(g);
        }
    }

    private void initGraphTypeComboFromGraph(Graph g) {
        List<RepairGraphType> plausible = new ArrayList<>();
        for (RepairGraphType gt : RepairGraphType.values()) {
            if (isLegalGraphType(g, gt)) {
                plausible.add(gt);
            }
        }

        // If the graph is not legal for any type, offer all types.
        if (plausible.isEmpty()) {
            plausible = Arrays.asList(RepairGraphType.values());
        }

        graphTypeCombo.setModel(new DefaultComboBoxModel<>(plausible.toArray(new RepairGraphType[0])));
        graphTypeCombo.setSelectedIndex(0);
    }

    /**
     * Counts implied-independence violations across ALL nodes, caching CI test results so
     * repeated scoring across candidates doesn't re-run the same checkIndependence(X,Y|Z).
     * <p>
     * - De-duplicates facts across vertices (each unique (X,Y|Z) evaluated once).
     * <p>
     * NOTE: Cache key is by variable NAMES. This assumes stable variable naming.
     */
    private int countImpliedViolationsAllNodesCached(Graph g) {
        if (g == null) return 0;

        // Collect all implied facts, dedup by the same key used elsewhere.
        List<IndependenceFact> facts = baseModel.computeAllImpliedFacts(g);
        if (facts == null || facts.isEmpty()) return 0;

        Map<String, IndependenceFact> dedup = new LinkedHashMap<>();
        for (IndependenceFact f : facts) {
            if (f == null) continue;
            dedup.putIfAbsent(factKey(f), f);
        }

        int numReject = 0;
        for (IndependenceFact f : dedup.values()) {
            // “Violation” = implied independent but judged dependent by test
            if (!independentOf(f)) {
                numReject++;
            }
        }
        return numReject;
    }

    /**
     * Sets the knowledge object for the VertexRepairPanel.
     *
     * @param knowledge the new Knowledge object to be assigned to the panel
     */
    public void setKnowledge(Knowledge knowledge) {
        if (knowledge.isViolatedBy(workingGraph)) {
            throw new IllegalArgumentException("The given Knowledge object is violated by the current graph.");
        }

        this.knowledge = knowledge;
    }

//    private IndependenceResult check(IndependenceFact f) {
//        if (f == null || Q == null) return null;
//
//        // Use the cached query engine; it should canonicalize / align internally as needed.
//        Set<Node> z = new LinkedHashSet<>(f.getZ());
//        return Q.checkIndependence(f.getX(), f.getY(), z);
//    }

    private IndependenceResult check(IndependenceFact f) {
        if (stopRequested()) return null;   // <-- add this line
        if (f == null || Q == null) return null;

        Set<Node> z = new LinkedHashSet<>(f.getZ());
        return Q.checkIndependence(f.getX(), f.getY(), z);
    }

    private double pValueOf(IndependenceFact f) {
        IndependenceResult r = check(f);
        return (r == null ? Double.NaN : r.getPValue());
    }

    private boolean independentOf(IndependenceFact f) {
        IndependenceResult r = check(f);
        return r != null && r.isIndependent();
    }

    /**
     * IMPORTANT: do NOT name this GraphType, since edu.cmu.tetrad.graph.GraphType exists.
     * Keeping this local avoids accidental import/name clashes.
     */
    public enum RepairGraphType {DAG, CPDAG, PDAG, MAG, PAG}

    public interface CandidateEdit {

        /**
         * Alias you asked for.
         */
        static CandidateEdit noOp() {
            return new CandidateEdit() {
                @Override
                public String description() {
                    return "No change";
                }

                @Override
                public Graph applyTo(Graph g) {
                    return g;
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
                    return "ADD:" + edgeToAdd;
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
                    return "REM:" + edgeToRemove;
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
                    return "REP:" + oldEdge + "->" + newEdge;
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
    }

    private record ScoredCandidate(
            CandidateEdit edit,
            int baseline,
            int after,
            double nodeKsAfter,
            double ksAfter,
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
        private static final int COL_NKS = 4;   // NEW: node KS
        private static final int COL_KS = 5;   // model KS
        private static final int COL_EDGES = 6;
        private static final int COL_APPLY = 7;

        private final String[] cols = {
                "Edit", "Baseline", "After", "Δ", "N-KS", "M-KS", "Edges", "Apply"
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
                case COL_NKS -> r.nodeKsAfter();
                case COL_KS -> r.ksAfter();
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
                case COL_NKS -> Double.class;
                case COL_KS -> Double.class;
                case COL_EDGES -> Integer.class;
                case COL_APPLY -> Object.class;
                default -> Object.class;
            };
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            // Don't allow clicking apply on no-op row
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

            setClickCountToStart(1);   // <--- critical

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

    private record GraphEval(int violations, double ksP, int nFacts) { }

    private GraphEval evalGraphOnce(Graph g) {
        if (g == null) return new GraphEval(0, Double.NaN, 0);

        List<IndependenceFact> facts = baseModel.computeAllImpliedFacts(g);
        if (facts == null || facts.isEmpty()) return new GraphEval(0, Double.NaN, 0);

        // Dedup by the same key you already use
        Map<String, IndependenceFact> dedup = new LinkedHashMap<>();
        for (IndependenceFact f : facts) {
            if (f == null) continue;
            dedup.putIfAbsent(factKey(f), f);
        }

        int violations = 0;
        List<Double> pvals = new ArrayList<>(dedup.size());

        for (IndependenceFact f : dedup.values()) {
            IndependenceResult r = check(f);   // uses Q cache
            if (r == null) continue;

            if (!r.isIndependent()) violations++;

            double p = r.getPValue();
            if (!Double.isNaN(p) && p >= 0.0 && p <= 1.0) {
                pvals.add(p);
            }
        }

        double ks = ksUniformPValue(pvals);
        return new GraphEval(violations, ks, dedup.size());
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

        // IMPORTANT: baseModel.computeImpliedFactsForVertex expects Node that exists in g.
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

            IndependenceResult r = check(f);  // uses Q cache
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
                                        boolean computeKs) {
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
                    // Vertex missing shouldn't happen, but be defensive.
                    contrib.remove(name);
                    continue;
                }
                contrib.put(name, evalVertexContribution(candidateGraph, v));
            }
        }

        // 3) Merge to global dedup by factKey
        Map<String, Boolean> globalViolationByKey = new HashMap<>();
        Map<String, Double> globalPByKey = computeKs ? new HashMap<>() : null;

//        for (VertexContribution vc : contrib.values()) {

        List<String> names = new ArrayList<>(contrib.keySet());
        Collections.sort(names);

        for (String name : names) {
            VertexContribution vc = contrib.get(name);
            if (vc == null) continue;

            // violations
            for (Map.Entry<String, Boolean> e : vc.violationByKey().entrySet()) {
                String key = e.getKey();
                if (key == null) continue;
                globalViolationByKey.putIfAbsent(key, e.getValue());
            }

            // p-values only needed if computing KS
            if (computeKs) {
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

        double ks = Double.NaN;
        if (computeKs && globalPByKey != null && globalPByKey.size() >= 2) {
            // Optional top-K downselect: keep largest p-values? smallest? (You probably want "most informative":
            // easiest is: use them all but if huge, trim to ksTopK by random or by closeness to 0.5. Here we do random stable.)
            List<Double> pvals = new ArrayList<>(globalPByKey.values());

            if (ksTopK > 0 && pvals.size() > ksTopK) {
                // deterministic shuffle so UI doesn't flicker
                pvals.sort(Double::compareTo);
                // take evenly spaced sample across [0,1]
                List<Double> sampled = new ArrayList<>(ksTopK);
                for (int i = 0; i < ksTopK; i++) {
                    int idx = (int) Math.floor((i + 0.5) * pvals.size() / ksTopK);
                    idx = Math.min(Math.max(idx, 0), pvals.size() - 1);
                    sampled.add(pvals.get(idx));
                }
                pvals = sampled;
            }

            ks = ksUniformPValue(pvals);
        }

        return new GraphEval(violations, ks, globalViolationByKey.size());
    }

    private int evalViolationsOnly(Graph g) {
        if (g == null) return 0;

        List<IndependenceFact> facts = baseModel.computeAllImpliedFacts(g);
        if (facts == null || facts.isEmpty()) return 0;

        Map<String, IndependenceFact> dedup = new LinkedHashMap<>();
        for (IndependenceFact f : facts) {
            if (f == null) continue;
            dedup.putIfAbsent(factKey(f), f);
        }

        int violations = 0;
        for (IndependenceFact f : dedup.values()) {
            IndependenceResult r = check(f); // uses Q cache
            if (r == null) continue;
            if (!r.isIndependent()) violations++;
        }

        return violations;
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

            // Optional: skip “no-change” candidates (except no-op) after canonicalization.
            if (!cand.isNoOp() && g2.equals(base)) return null;
        } else if (gt == RepairGraphType.PAG) {
            // You only canonicalize PAG for base currently; keep candidate as-is unless you want to do more.
            // If you DO want canonicalization here too, add it (but it may be expensive).
        }

        try {
            if (gt != null && !isLegalGraphType(g2, gt)) return null;
        } catch (Exception ignored) {
            return null;
        }

        return g2;
    }

    private Set<String> affectedVertices(Graph base, CandidateEdit cand, Node x, Graph candidate) {
        // Conservative and safe:
        // - always recompute x
        // - recompute any node whose adjacency to x changed (before vs after)
        Set<String> affected = new LinkedHashSet<>();
        if (x != null) affected.add(x.getName());

        if (base == null || candidate == null || x == null) return affected;

        Node xb = base.getNode(x.getName());
        Node xc = candidate.getNode(x.getName());
        if (xb == null || xc == null) return affected;

        Set<String> nb = new HashSet<>();
        for (Node n : base.getAdjacentNodes(xb)) nb.add(n.getName());

        Set<String> nc = new HashSet<>();
        for (Node n : candidate.getAdjacentNodes(xc)) nc.add(n.getName());

        // symmetric difference
        for (String name : nb) if (!nc.contains(name)) affected.add(name);
        for (String name : nc) if (!nb.contains(name)) affected.add(name);

        // ALSO recompute current neighbors of x (optional but often worth it; comment out if too expensive)
        affected.addAll(nc);

        return affected;
    }

    private static int parseTopK(String s, int fallback) {
        try {
            int k = Integer.parseInt(s.trim());
            return (k <= 0 ? fallback : k);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    // ---------- Locality-based global evaluation ----------

    /**
     * Per-vertex evaluated contribution:
     * - dedup facts by factKey within the vertex
     * - keep for each fact key: (isViolation, pValue)
     */
    private record VertexContribution(
            Map<String, Boolean> violationByKey,
            Map<String, Double> pByKey
    ) { }

    private record GlobalEvalCache(
            Map<String, VertexContribution> contribByVertexName
    ) { }

    private void loadPrefsIntoUi() {
        double a = PREFS.getDouble(PREF_ALPHA, DEFAULT_ALPHA);
        int k = PREFS.getInt(PREF_KS_TOP_K, DEFAULT_KS_TOP_K);

        // sanitize
        if (!(a > 0.0 && a < 1.0)) a = DEFAULT_ALPHA;
        if (k <= 0) k = DEFAULT_KS_TOP_K;

        alphaField.setText(String.valueOf(a));
        ksTopKField.setText(String.valueOf(k));
        ksTopK = k;
    }

    private void initPrefTimers() {
        alphaSaveTimer.setRepeats(false);
        topKSaveTimer.setRepeats(false);
    }

    private void saveAlphaPref() {
        double a = parseAlpha(alphaField.getText(), DEFAULT_ALPHA);
        PREFS.putDouble(PREF_ALPHA, a);
    }

    private void saveTopKPref() {
        int k = parseTopK(ksTopKField.getText(), DEFAULT_KS_TOP_K);
        PREFS.putInt(PREF_KS_TOP_K, k);
    }

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
                    // triggers exception if cancelled / failed
                    get();
                    if (onDoneEdt != null) onDoneEdt.run();
                } catch (CancellationException ce) {
                    statusLabel.setText("Cancelled.");
                } catch (Exception ex) {
                    // Preserve something visible but don't explode the UI
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
            // cancel(true) interrupts the worker thread
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
}