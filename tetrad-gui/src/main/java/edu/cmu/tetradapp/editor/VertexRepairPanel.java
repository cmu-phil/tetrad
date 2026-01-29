package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.IndependenceTest;
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


/**
 * Panel that suggests local edits around a selected vertex x to reduce
 * VertexChecker "implied independence but judged dependent" violations.
 * <p>
 * Intended usage:
 * VertexRepairPanel p = new VertexRepairPanel(vertexCheckEditor, x);
 * show modal dialog containing p;
 * Graph newGraph = p.getGraph();
 */
public final class VertexRepairPanel extends JPanel {

    private static final String CARD_TABLE = "table";
    private static final String CARD_NONE = "none";
    private final VertexCheckIndTestModel baseModel;
    private final Node x;
    private final Deque<Graph> history = new ArrayDeque<>();

    // UI
    private final JComboBox<RepairGraphType> graphTypeCombo = new JComboBox<>(RepairGraphType.values());
    private final JButton searchButton = new JButton("Search for best node adjustments about x");
    private final JButton backButton = new JButton("Go Back to Previous Graph");
    private final JButton showGraphButton = new JButton("Show Graph");

    private final JLabel statusLabel = new JLabel(" ");
    private final JTable resultsTable = new JTable();
    private final CandidateTableModel resultsModel = new CandidateTableModel();

    private final JPanel resultsCard = new JPanel(new CardLayout());
    // Cache of CI test results for (X,Y|Z) queries.
// Key is canonicalized by variable names (X,Y unordered; Z sorted).
    private final Map<String, Boolean> indepCache = new HashMap<>();
    // Replace indepCache with:
    private final Map<String, Double> pvalCache = new HashMap<>();
    private IndependenceTest pvalCacheOwner = null;

    private Graph workingGraph;
    private IndependenceTest indepCacheOwner = null;

    private static final DecimalFormat KS_FORMAT = new DecimalFormat("0.0000");


    public VertexRepairPanel(VertexCheckEditor editor, Node x) {
        super(new BorderLayout());
        this.x = Objects.requireNonNull(x, "x");

        this.baseModel = Objects.requireNonNull(editor.getIndTestModel(), "editor.getIndTestModel()");
        this.workingGraph = safeCopy(baseModel.getGraph());

        // ADD THIS LINE:
        initGraphTypeComboFromGraph(this.workingGraph);

        buildUI();
        wireActions();
        updateButtons();

        setPreferredSize(new Dimension(600, 600));
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

    private double ksUniformPValue(List<Double> pvals) {
        if (pvals == null || pvals.size() < 2) return Double.NaN;

        double[] x = pvals.stream().mapToDouble(Double::doubleValue).toArray();
        KolmogorovSmirnovTest ks = new KolmogorovSmirnovTest();
        return ks.kolmogorovSmirnovTest(new UniformRealDistribution(0.0, 1.0), x);
    }

    private void resetPvalCacheIfNeeded(IndependenceTest test) {
        if (test == null) {
            pvalCache.clear();
            pvalCacheOwner = null;
            return;
        }
        if (pvalCacheOwner != test) {
            pvalCache.clear();
            pvalCacheOwner = test;
        }
    }

    private double getPValueCached(IndependenceTest test, IndependenceFact fact) {
        String qKey = queryKey(fact);

        Double cached = pvalCache.get(qKey);
        if (cached != null) return cached;

        double p;
        try {
            Node X = test.getVariable(fact.getX().getName());
            Node Y = test.getVariable(fact.getY().getName());
            if (X == null || Y == null) {
                p = Double.NaN;
            } else {
                Set<Node> Z = new HashSet<>();
                for (Node z : fact.getZ()) {
                    Node zz = test.getVariable(z.getName());
                    if (zz != null) Z.add(zz);
                }
                IndependenceResult r = test.checkIndependence(X, Y, Z);
                p = r.getPValue();
            }
        } catch (Throwable t) {
            p = Double.NaN; // match your current “ignore errors” policy
        }

        pvalCache.put(qKey, p);
        return p;

    }

    private List<Double> collectAllImpliedPValuesDedup(Graph g) {
        IndependenceTest test = baseModel.getIndependenceTest();
        if (test == null) return List.of();

        resetPvalCacheIfNeeded(test);

        List<Double> pvals = new ArrayList<>();
        Set<String> seenFacts = new HashSet<>();

        for (Node v : g.getNodes()) {
            List<IndependenceFact> implied = baseModel.computeImpliedFactsForVertex(g, v);

            for (IndependenceFact fact : implied) {
                String fk = factKey(fact);
                if (!seenFacts.add(fk)) continue;

                double p = getPValueCached(test, fact);
                if (!Double.isNaN(p) && p >= 0.0 && p <= 1.0) {
                    pvals.add(p);
                }
            }
        }
        return pvals;
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

        // Search button now directly under graph type row
        c.gridx = 0;
        c.gridy = 1;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        controls.add(searchButton, c);

        JPanel topButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        topButtons.add(backButton);
        topButtons.add(showGraphButton);

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
                    runSearch();
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


//         Column indices assumed; adjust if needed
        TableColumn editIndex = cm.getColumn(0);
        TableColumn baselineIndex = cm.getColumn(1);
        TableColumn afterIndex = cm.getColumn(2);
        TableColumn deltaIndex = cm.getColumn(3);
        TableColumn kstestIndex = cm.getColumn(4);
        TableColumn edgesIndex = cm.getColumn(5);
        TableColumn applyIndex = cm.getColumn(6);

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

        resultsTable.setRowSorter(new TableRowSorter<>(resultsModel));

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

    private void wireActions() {
        backButton.addActionListener(e -> goBack());
        showGraphButton.addActionListener(e -> showGraphDialog());

        searchButton.addActionListener(e -> {
            searchButton.setEnabled(false);
            statusLabel.setText("Searching...");

            // NOTE: first-shot: run on EDT (simple). If it becomes slow, replace with SwingWorker.
            SwingUtilities.invokeLater(() -> {
                try {
                    runSearch();
                } finally {
                    searchButton.setEnabled(true);
                    updateButtons();
                }
            });
        });
    }

    private void updateButtons() {
        backButton.setEnabled(!history.isEmpty());
    }

    private void runSearch() {
        RepairGraphType gt = (RepairGraphType) graphTypeCombo.getSelectedItem();
        Graph base = safeCopy(workingGraph);

        if (gt == RepairGraphType.CPDAG) {
            base = canonicalizeToCpdagOrNull(base);
            if (base == null) {
                statusLabel.setText("Current graph has no consistent CPDAG extension.");
                ((CardLayout) resultsCard.getLayout()).show(resultsCard, CARD_NONE);
                return;
            }
        }

        // 1) enumerate candidate edits around x
        List<CandidateEdit> candidates = enumerateCandidates(base, x, gt);

        // Ensure “no-op” is present at top (baseline)
        candidates = new ArrayList<>(candidates);
        if (candidates.stream().noneMatch(CandidateEdit::isNoOp)) {
            candidates.add(0, CandidateEdit.noOp());
        }

        // 3) score candidates
        List<ScoredCandidate> scored = new ArrayList<>();

        int baseline = countImpliedViolationsAllNodesCached(base);

        List<Double> baseP = collectAllImpliedPValuesDedup(base);
        double baselineKs = ksUniformPValue(baseP);

        for (CandidateEdit cand : candidates) {
            Graph g2 = cand.applyTo(safeCopy(base));
            if (g2 == null) continue;

            if (gt == RepairGraphType.CPDAG) {
                g2 = canonicalizeToCpdagOrNull(g2);
                if (g2 == null) continue;
            }
            if (!isLegalGraphType(g2, gt)) continue;

            int after = countImpliedViolationsAllNodesCached(g2);
            List<Double> p2 = collectAllImpliedPValuesDedup(g2);
            double ksAfter = ksUniformPValue(p2);
            int edgesAfter = g2.getNumEdges();
            scored.add(new ScoredCandidate(cand, baseline, after, ksAfter, edgesAfter));
        }

        // Sort by improvement (most negative delta first), then by absolute violations
        scored.sort(Comparator
                .comparingInt(ScoredCandidate::violationsAfter)          // lower better
                .thenComparing(Comparator.comparingDouble(ScoredCandidate::ksAfter).reversed()) // higher better
                .thenComparingInt(ScoredCandidate::edgesAfter)           // lower better
        );

        resultsModel.set(scored);

        NumberFormat fmt = new DecimalFormat("0.0000");

        if (scored.isEmpty()) {
            statusLabel.setText("No legal candidate edits found.");
            ((CardLayout) resultsCard.getLayout()).show(resultsCard, CARD_NONE);
        } else {
            int best = scored.get(0).violationsAfter();
            statusLabel.setText(
                    "Baseline violations: " + baseline +
                            " | Best: " + best +
                            " | KS(all): " + fmt.format(baselineKs) + " → " + fmt.format(scored.get(0).ksAfter())
            );
            ((CardLayout) resultsCard.getLayout()).show(resultsCard, CARD_TABLE);
        }


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

    private void goBack() {
        if (history.isEmpty()) return;
        workingGraph = history.pop();
        statusLabel.setText("Reverted to previous graph.");
        updateButtons();
        searchButton.setEnabled(false);
        statusLabel.setText("Searching...");

        // NOTE: first-shot: run on EDT (simple). If it becomes slow, replace with SwingWorker.
        SwingUtilities.invokeLater(() -> {
            try {
                runSearch();
            } finally {
                searchButton.setEnabled(true);
                updateButtons();
            }
        });
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
        tabs.addTab("Text", textScroll);
        tabs.addTab("Graph", renderScroll);

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
            case CPDAG -> {
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
                // adds.add(edge(x, y, Endpoint.TAIL, Endpoint.ARROW));    // x->y
                // adds.add(edge(y, x, Endpoint.TAIL, Endpoint.ARROW));    // y->x
                // adds.add(edge(x, y, Endpoint.ARROW, Endpoint.ARROW));   // x<->y
                // adds.add(edge(x, y, Endpoint.TAIL, Endpoint.TAIL));     // x---y
            }
        }

        return adds;
    }

    // ---------------- data model classes ----------------

    private Graph canonicalizeToCpdagOrNull(Graph h) {
        try {
            Graph h2 = new EdgeListGraph(h);
            Graph dag = GraphTransforms.dagFromCpdag(h2);
            return GraphTransforms.dagToCpdag(dag);
        } catch (Throwable t) {
            return null; // no consistent extension => can't be a CPDAG candidate
        }
    }

    private boolean isLegalGraphType(Graph g, RepairGraphType gt) {
        return switch (gt) {
            case DAG -> g.paths().isLegalDag();
            case CPDAG -> g.paths().isLegalCpdag();
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

    private void resetIndepCacheIfNeeded(IndependenceTest test) {
        if (test == null) {
            indepCache.clear();
            indepCacheOwner = null;
            return;
        }
        if (indepCacheOwner != test) {
            indepCache.clear();
            indepCacheOwner = test;
        }
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
        IndependenceTest test = baseModel.getIndependenceTest();
        if (test == null) return 0;

        resetIndepCacheIfNeeded(test);

        int violations = 0;
        Set<String> seenFacts = new HashSet<>();

        for (Node x : g.getNodes()) {
            List<IndependenceFact> implied = baseModel.computeImpliedFactsForVertex(g, x);

            for (IndependenceFact fact : implied) {
                // De-dupe the implied fact (so we don't even do cache lookups repeatedly)
                String factKey = factKey(fact);
                if (!seenFacts.add(factKey)) continue;

                // Cached CI query: returns true if independent, false if dependent (or error-policy dependent)
                boolean independent = isIndependentCached(test, fact);
                if (!independent) violations++;
            }
        }

        return violations;
    }

    /**
     * Cached CI query; returns true if independent, false if dependent (errors are treated as independent here).
     */
    private boolean isIndependentCached(IndependenceTest test, IndependenceFact fact) {
        String qKey = queryKey(fact);

        Boolean cached = indepCache.get(qKey);
        if (cached != null) return cached;

        boolean independent;
        try {
            Node X = test.getVariable(fact.getX().getName());
            Node Y = test.getVariable(fact.getY().getName());
            if (X == null || Y == null) {
                independent = true; // treat missing vars as non-violations
            } else {
                Set<Node> Z = new HashSet<>();
                for (Node z : fact.getZ()) {
                    Node zz = test.getVariable(z.getName());
                    if (zz != null) Z.add(zz);
                }
                IndependenceResult r = test.checkIndependence(X, Y, Z);
                independent = r.isIndependent();
            }
        } catch (Throwable t) {
            // Match your existing policy: ignore errors (do not count as violations).
            independent = true;
            // If you want conservative: independent = false;
        }

        indepCache.put(qKey, independent);
        return independent;
    }

    private int countViolationsOnFixedFactSet(Graph g, List<IndependenceFact> fixedFacts) {
        IndependenceTest test = baseModel.getIndependenceTest();
        resetIndepCacheIfNeeded(test);

        int violations = 0;
        for (IndependenceFact f : fixedFacts) {
            if (!isIndependentCached(test, f)) violations++;
        }
        return violations;
    }

    private Set<String> baselineViolatedKeys(Graph base) {
        IndependenceTest test = baseModel.getIndependenceTest();
        resetIndepCacheIfNeeded(test);

        Set<String> violated = new HashSet<>();
        Set<String> seen = new HashSet<>();

        for (Node v : base.getNodes()) {
            List<IndependenceFact> implied = baseModel.computeImpliedFactsForVertex(base, v);

            for (IndependenceFact f : implied) {
                String k = factKey(f);
                if (!seen.add(k)) continue;

                if (!isIndependentCached(test, f)) {
                    violated.add(k);
                }
            }
        }
        return violated;
    }

    /**
     * IMPORTANT: do NOT name this GraphType, since edu.cmu.tetrad.graph.GraphType exists.
     * Keeping this local avoids accidental import/name clashes.
     */
    public enum RepairGraphType {DAG, CPDAG, MAG, PAG}

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
            double ksAfter,
            int edgesAfter
    ) {
        int violationsAfter() { return after; }
        int delta() { return after - baseline; }
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

    private static final class CandidateTableModel extends AbstractTableModel {
        static final int COL_DESC  = 0;
        static final int COL_BASE  = 1;
        static final int COL_AFTER = 2;
        static final int COL_DELTA = 3;
        static final int COL_KS    = 4;
        static final int COL_EDGES = 5;
        static final int COL_APPLY = 6;

        private final String[] cols = {"Edit", "Baseline", "After", "Δ", "Mod-KS", "Edges", "Apply"};

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
                case COL_DESC -> r.edit().description();
                case COL_BASE -> r.baseline();
                case COL_AFTER -> r.violationsAfter();
                case COL_DELTA -> r.delta();
                case COL_KS -> r.ksAfter();
                case COL_EDGES -> r.edgesAfter();
                case COL_APPLY -> r.edit().isNoOp() ? "" : "Accept";
                default -> "";
            };
        }

        @Override
        public Class<?> getColumnClass(int col) {
            return switch (col) {
                case COL_DESC -> String.class;
                case COL_BASE -> Double.class;
                case COL_AFTER -> Double.class;
                case COL_DELTA -> Double.class;
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
        private final RowAction onClick;
        private int editingRow = -1;

        ButtonEditor(RowAction onClick) {
            super(new JTextField());
            this.onClick = onClick;

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
}