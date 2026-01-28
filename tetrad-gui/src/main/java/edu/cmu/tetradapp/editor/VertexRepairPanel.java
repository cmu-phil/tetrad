package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetradapp.model.VertexCheckIndTestModel;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.function.Supplier;

/**
 * Panel that suggests local edits around a selected vertex x to reduce
 * VertexChecker "implied independence but judged dependent" violations.
 *
 * Intended usage:
 *   VertexRepairPanel p = new VertexRepairPanel(vertexCheckEditor, x);
 *   show modal dialog containing p;
 *   Graph newGraph = p.getGraph();
 */
public final class VertexRepairPanel extends JPanel {

    /**
     * IMPORTANT: do NOT name this GraphType, since edu.cmu.tetrad.graph.GraphType exists.
     * Keeping this local avoids accidental import/name clashes.
     */
    public enum RepairGraphType { DAG, CPDAG, MAG, PAG }

    private final VertexCheckEditor editor;
    private final VertexCheckIndTestModel baseModel;

    private final Node x;
    private Graph workingGraph;

    private final Deque<Graph> history = new ArrayDeque<>();

    // UI
    private final JComboBox<RepairGraphType> graphTypeCombo = new JComboBox<>(RepairGraphType.values());
    private final JSpinner hopsSpinner = new JSpinner(new SpinnerNumberModel(2, -1, 100, 1));
    private final JButton searchButton = new JButton("Search for best node adjustments about x");
    private final JButton backButton = new JButton("Go Back to Previous Graph");
    private final JButton showGraphButton = new JButton("Show Graph");

    private final JLabel statusLabel = new JLabel(" ");
    private final JTable resultsTable = new JTable();
    private final CandidateTableModel resultsModel = new CandidateTableModel();

    private final JPanel resultsCard = new JPanel(new CardLayout());
    private static final String CARD_TABLE = "table";
    private static final String CARD_NONE = "none";

    public VertexRepairPanel(VertexCheckEditor editor, Node x) {
        super(new BorderLayout());
        this.editor = Objects.requireNonNull(editor, "editor");
        this.x = Objects.requireNonNull(x, "x");

        // Assumes VertexCheckEditor has:
        //   public VertexCheckIndTestModel getIndTestModel()
        this.baseModel = Objects.requireNonNull(editor.getIndTestModel(), "editor.getIndTestModel()");
        this.workingGraph = safeCopy(baseModel.getGraph());

        buildUI();
        wireActions();
        updateButtons();
    }

    /** Caller reads this after dialog closes. */
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

        c.gridx = 0;
        c.gridy = 1;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        controls.add(new JLabel("Hops from x:"), c);

        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        controls.add(hopsSpinner, c);

        c.gridx = 0;
        c.gridy = 2;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL;
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

        // Apply button column renderer/editor
        resultsTable.getColumnModel().getColumn(CandidateTableModel.COL_APPLY)
                .setCellRenderer(new ButtonRenderer());

//        resultsTable.getColumnModel().getColumn(CandidateTableModel.COL_APPLY)
//                .setCellEditor(new ButtonEditor(() -> {
//                    int row = resultsTable.getSelectedRow();
//                    if (row < 0) return;
//                    CandidateEdit cand = resultsModel.getCandidate(row);
//                    applyCandidate(cand);
//                }));
        resultsTable.getColumnModel().getColumn(CandidateTableModel.COL_APPLY)
                .setCellEditor(new ButtonEditor(row -> {
                    if (row < 0) return;
                    int modelRow = resultsTable.convertRowIndexToModel(row);
                    CandidateEdit cand = resultsModel.getCandidate(modelRow);
                    applyCandidate(cand);
                    runSearch();
                }));

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
        int hops = (Integer) hopsSpinner.getValue();

        // 1) enumerate candidate edits around x
        List<CandidateEdit> candidates = enumerateCandidates(workingGraph, x, gt);

        // Ensure “no-op” is present at top (baseline)
        candidates = new ArrayList<>(candidates);
        if (candidates.stream().noneMatch(CandidateEdit::isNoOp)) {
            candidates.add(0, CandidateEdit.noOp());
        }

        // 2) baseline score
        int baseline = countImpliedViolations(workingGraph, x, hops);

        // 3) score candidates
        List<ScoredCandidate> scored = new ArrayList<>();
        for (CandidateEdit cand : candidates) {
            Graph g2;
            try {
                g2 = cand.applyTo(safeCopy(workingGraph));
            } catch (Throwable t) {
                continue;
            }
            if (g2 == null) continue;

            if (!isLegalGraphType(g2, gt)) continue;

            int v = countImpliedViolations(g2, x, hops);
            scored.add(new ScoredCandidate(cand, baseline, v));
        }

        // Sort by improvement (most negative delta first), then by absolute violations
        scored.sort(Comparator
                .comparingInt(ScoredCandidate::delta)
                .thenComparingInt(ScoredCandidate::violationsAfter));

        resultsModel.set(scored);

        if (scored.isEmpty()) {
            statusLabel.setText("No legal candidate edits found.");
            ((CardLayout) resultsCard.getLayout()).show(resultsCard, CARD_NONE);
        } else {
            int best = scored.get(0).violationsAfter();
            statusLabel.setText("Baseline violations: " + baseline + " | Best candidate: " + best);
            ((CardLayout) resultsCard.getLayout()).show(resultsCard, CARD_TABLE);
        }
    }

    /**
     * Counts how many implied independences (per VertexChecker’s conditioning rule)
     * are judged dependent by the data, restricted to within 'hops' if hops >= 0.
     */
    private int countImpliedViolations(Graph g, Node x, int hops) {
        // You said you'll wire this in via a method on VertexCheckIndTestModel.
        // For now we assume it exists:
        //   List<IndependenceFact> computeImpliedFactsForVertex(Graph g, Node x)
        List<IndependenceFact> implied = baseModel.computeImpliedFactsForVertex(g, x);

        if (hops >= 0) {
            implied = restrictByHops(g, x, implied, hops);
        }

        IndependenceTest test = baseModel.getIndependenceTest();
        int violations = 0;

        for (IndependenceFact fact : implied) {
            try {
                Node X = test.getVariable(fact.getX().getName());
                Node Y = test.getVariable(fact.getY().getName());
                if (X == null || Y == null) continue;

                Set<Node> Z = new HashSet<>();
                for (Node z : fact.getZ()) {
                    Node zz = test.getVariable(z.getName());
                    if (zz != null) Z.add(zz);
                }

                IndependenceResult r = test.checkIndependence(X, Y, Z);
                if (!r.isIndependent()) violations++;
            } catch (Throwable ignored) {
                // First-shot policy: ignore errors (do not count as violations).
                // If you prefer conservative behavior, change to: violations++;
            }
        }

        return violations;
    }

    private List<IndependenceFact> restrictByHops(Graph g, Node x, List<IndependenceFact> facts, int hops) {
        Set<Node> allowed = nodesWithinHops(g, x, hops);
        List<IndependenceFact> out = new ArrayList<>();
        for (IndependenceFact f : facts) {
            // Typical implied-fact convention: fact is <x, y | S> with x fixed and y varying.
            if (allowed.contains(f.getY())) out.add(f);
        }
        return out;
    }

    private Set<Node> nodesWithinHops(Graph g, Node start, int hops) {
//        if (true) return new HashSet<>(g.getNodes());


        Set<Node> seen = new HashSet<>();
        ArrayDeque<Node> q = new ArrayDeque<>();
        Map<Node, Integer> dist = new HashMap<>();

        seen.add(start);
        dist.put(start, 0);
        q.add(start);

        while (!q.isEmpty()) {
            Node cur = q.removeFirst();
            int d = dist.get(cur);
            if (d == hops) continue;

            for (Node nb : g.getAdjacentNodes(cur)) {
                if (seen.add(nb)) {
                    dist.put(nb, d + 1);
                    q.addLast(nb);
                }
            }
        }

        return seen;
    }

    private void applyCandidate(CandidateEdit cand) {
        history.push(safeCopy(workingGraph));

        Graph g2;
        try {
            g2 = cand.applyTo(safeCopy(workingGraph));
        } catch (Throwable t) {
            g2 = null;
        }

        if (g2 != null) {
            workingGraph = g2;
            statusLabel.setText("Applied: " + cand.description());
        } else {
            statusLabel.setText("Failed to apply: " + cand.description());
            // Revert the push, since we didn't actually change:
            if (!history.isEmpty()) history.pop();
        }

        updateButtons();
    }

    private void goBack() {
        if (history.isEmpty()) return;
        workingGraph = history.pop();
        statusLabel.setText("Reverted to previous graph.");
        updateButtons();
    }

    private void showGraphDialog() {
        JTextArea ta = new JTextArea(String.valueOf(workingGraph));
        ta.setEditable(false);
        ta.setCaretPosition(0);
        JScrollPane sp = new JScrollPane(ta);
        sp.setPreferredSize(new Dimension(700, 450));
        JOptionPane.showMessageDialog(this, sp, "Current Graph", JOptionPane.INFORMATION_MESSAGE);
    }

    // ---------------- candidate generation & legality ----------------

    private List<CandidateEdit> enumerateCandidates(Graph g, Node x, RepairGraphType gt) {
        // Conservative enumerator supporting all graph types:
        //  - NO-OP baseline
        //  - remove any edge incident to x
        //  - replace any existing x—y edge with a small menu of endpoint variants (per graph type)
        //  - add x—y edges for y in a bounded pool using a small menu (per graph type)
        //
        // IMPORTANT: Enumerates edits only. runSearch() should:
        //  - apply edit to a COPY
        //  - align nodes (if needed)
        //  - check legality (graph-type + cycles, etc.)
        //  - score via VertexChecker implied-facts pipeline

        if (g == null || x == null) return List.of(CandidateEdit.noOp());

        List<CandidateEdit> out = new ArrayList<>();
        out.add(CandidateEdit.noOp());

        // ------------------------
        // 0) Build a bounded pool
        // ------------------------
        // Default: nodes within 2 hops of x (excluding x).
        // You can later parameterize this (1-hop vs 2-hop) or cap pool size.
        Set<Node> pool = new LinkedHashSet<>();
        pool.addAll(g.getAdjacentNodes(x));
        for (Node n1 : g.getAdjacentNodes(x)) {
            pool.addAll(g.getAdjacentNodes(n1));
        }
        pool.remove(x);

        // --------------------------------------
        // 1) Remove any existing edge incident to x
        // --------------------------------------
        for (Edge e : new ArrayList<>(g.getEdges(x))) {
            out.add(CandidateEdit.removeEdge(e));
        }

        // ----------------------------------------------------
        // 2) Replace existing edge x—y with type-specific variants
        // ----------------------------------------------------
        for (Edge e : new ArrayList<>(g.getEdges(x))) {
            Node y = e.getDistalNode(x);
            if (y == null) continue;

            for (Edge v : edgeMenuForPair(x, y, gt)) {
                if (edgeStructurallyEqual(e, v, x, y)) continue;
                out.add(CandidateEdit.replaceEdge(e, v));
            }
        }

        // --------------------------------------
        // 3) Add edges x—y for non-adjacent y in pool
        // --------------------------------------
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
                variants.add(edge(x, y, Endpoint.TAIL, Endpoint.ARROW)); // x->y
                variants.add(edge(y, x, Endpoint.TAIL, Endpoint.ARROW)); // y->x
            }
            case CPDAG -> {
                // Directed or undirected.
                variants.add(edge(x, y, Endpoint.TAIL, Endpoint.TAIL));  // x---y
                variants.add(edge(x, y, Endpoint.TAIL, Endpoint.ARROW)); // x->y
                variants.add(edge(y, x, Endpoint.TAIL, Endpoint.ARROW)); // y->x
            }
            case MAG -> {
                // MAG endpoints typically: ->, <-, <->
                variants.add(edge(x, y, Endpoint.TAIL, Endpoint.ARROW));   // x->y
                variants.add(edge(y, x, Endpoint.TAIL, Endpoint.ARROW));   // y->x
                variants.add(edge(x, y, Endpoint.ARROW, Endpoint.ARROW));  // x<->y

                // Optional: some MAG codebases allow “---” temporarily; if yours doesn't, omit.
                // variants.add(edge(x, y, Endpoint.TAIL, Endpoint.TAIL));  // x---y (usually not MAG-legal)
            }
            case PAG -> {
                // PAG endpoints can be CIRCLE/TAIL/ARROW. Keep a small “useful” menu.
                variants.add(edge(x, y, Endpoint.CIRCLE, Endpoint.CIRCLE)); // o-o
                variants.add(edge(x, y, Endpoint.CIRCLE, Endpoint.ARROW));  // x o-> y
                variants.add(edge(y, x, Endpoint.CIRCLE, Endpoint.ARROW));  // y o-> x  (i.e., x <-o y)
                variants.add(edge(x, y, Endpoint.TAIL, Endpoint.ARROW));    // x->y
                variants.add(edge(y, x, Endpoint.TAIL, Endpoint.ARROW));    // y->x
                variants.add(edge(x, y, Endpoint.ARROW, Endpoint.ARROW));   // x<->y (allowed in PAGs)
                variants.add(edge(x, y, Endpoint.TAIL, Endpoint.TAIL));     // x---y (if you use this for selection-bias adjacency)
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
                adds.add(edge(x, y, Endpoint.TAIL, Endpoint.ARROW)); // x->y
                adds.add(edge(y, x, Endpoint.TAIL, Endpoint.ARROW)); // y->x
            }
            case CPDAG -> {
                // Conservative: add as undirected; you can let later rules orient.
                adds.add(edge(x, y, Endpoint.TAIL, Endpoint.TAIL));  // x---y

                // Optional: if you want, include directed adds too.
                // adds.add(edge(x, y, Endpoint.TAIL, Endpoint.ARROW)); // x->y
                // adds.add(edge(y, x, Endpoint.TAIL, Endpoint.ARROW)); // y->x
            }
            case MAG -> {
                // MAG: adjacency is directed or bidirected. Adding tail-tail is usually illegal.
                adds.add(edge(x, y, Endpoint.TAIL, Endpoint.ARROW));   // x->y
                adds.add(edge(y, x, Endpoint.TAIL, Endpoint.ARROW));   // y->x
                adds.add(edge(x, y, Endpoint.ARROW, Endpoint.ARROW));  // x<->y
            }
            case PAG -> {
                // Conservative PAG additions:
                adds.add(edge(x, y, Endpoint.CIRCLE, Endpoint.CIRCLE)); // o-o
                adds.add(edge(x, y, Endpoint.CIRCLE, Endpoint.ARROW));  // x o-> y
                adds.add(edge(y, x, Endpoint.CIRCLE, Endpoint.ARROW));  // y o-> x  (x <-o y)

                // Optional: include definite orientations too.
                // adds.add(edge(x, y, Endpoint.TAIL, Endpoint.ARROW));    // x->y
                // adds.add(edge(y, x, Endpoint.TAIL, Endpoint.ARROW));    // y->x
                // adds.add(edge(x, y, Endpoint.ARROW, Endpoint.ARROW));   // x<->y
                // adds.add(edge(x, y, Endpoint.TAIL, Endpoint.TAIL));     // x---y
            }
        }

        return adds;
    }

    /** Small helper so we don’t accidentally swap endpoints wrong. */
    private static Edge edge(Node n1, Node n2, Endpoint e1, Endpoint e2) {
        return new Edge(n1, n2, e1, e2);
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

    // ---------------- data model classes ----------------

    private record ScoredCandidate(CandidateEdit edit, int baseline, int after) {
        int violationsAfter() { return after; }
        int delta() { return after - baseline; } // negative is improvement
    }

    public interface CandidateEdit {

        String description();

        /**
         * Apply this edit to the given graph and return the modified graph.
         * Implementations should NOT mutate the input graph.
         */
        Graph applyTo(Graph g);

        default boolean isNoOp() { return false; }

        /** Optional key for de-dup. */
        default String key() { return description(); }

        // ---- factories ----

        /** Alias you asked for. */
        static CandidateEdit noOp() {
            return new CandidateEdit() {
                @Override public String description() { return "No change"; }
                @Override public Graph applyTo(Graph g) { return g; }
                @Override public boolean isNoOp() { return true; }
                @Override public String key() { return "NO_OP"; }
            };
        }

        static CandidateEdit addEdge(Edge edgeToAdd) {
            Objects.requireNonNull(edgeToAdd, "edgeToAdd");
            return new CandidateEdit() {
                @Override public String description() { return "Add edge " + edgeToAdd; }

                @Override public Graph applyTo(Graph g) {
                    Graph g2 = new EdgeListGraph(g);
                    g2.addEdge(edgeToAdd);
                    return g2;
                }

                @Override public String key() { return "ADD:" + edgeToAdd; }
            };
        }

        static CandidateEdit removeEdge(Edge edgeToRemove) {
            Objects.requireNonNull(edgeToRemove, "edgeToRemove");
            return new CandidateEdit() {
                @Override public String description() { return "Remove edge " + edgeToRemove; }

                @Override public Graph applyTo(Graph g) {
                    Graph g2 = new EdgeListGraph(g);
                    Edge e = g2.getEdge(edgeToRemove.getNode1(), edgeToRemove.getNode2());
                    if (e != null) g2.removeEdge(e);
                    return g2;
                }

                @Override public String key() { return "REM:" + edgeToRemove; }
            };
        }

        static CandidateEdit replaceEdge(Edge oldEdge, Edge newEdge) {
            Objects.requireNonNull(oldEdge, "oldEdge");
            Objects.requireNonNull(newEdge, "newEdge");
            return new CandidateEdit() {
                @Override public String description() { return "Replace " + oldEdge + " with " + newEdge; }

                @Override public Graph applyTo(Graph g) {
                    Graph g2 = new EdgeListGraph(g);
                    Edge e = g2.getEdge(oldEdge.getNode1(), oldEdge.getNode2());
                    if (e != null) g2.removeEdge(e);
                    g2.addEdge(newEdge);
                    return g2;
                }

                @Override public String key() { return "REP:" + oldEdge + "->" + newEdge; }
            };
        }
    }

    private static final class CandidateTableModel extends AbstractTableModel {
        static final int COL_DESC = 0;
        static final int COL_BASE = 1;
        static final int COL_AFTER = 2;
        static final int COL_DELTA = 3;
        static final int COL_APPLY = 4;

        private final String[] cols = {"Edit", "Baseline", "After", "Δ", "Apply"};
        private List<ScoredCandidate> rows = List.of();

        void set(List<ScoredCandidate> rows) {
            this.rows = rows == null ? List.of() : rows;
            fireTableDataChanged();
        }

        CandidateEdit getCandidate(int row) {
            return rows.get(row).edit();
        }

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return cols.length; }
        @Override public String getColumnName(int column) { return cols[column]; }

        @Override public Object getValueAt(int rowIndex, int columnIndex) {
            ScoredCandidate r = rows.get(rowIndex);
            return switch (columnIndex) {
                case COL_DESC -> r.edit().description();
                case COL_BASE -> r.baseline();
                case COL_AFTER -> r.violationsAfter();
                case COL_DELTA -> r.delta();
                case COL_APPLY -> r.edit().isNoOp() ? "" : "Accept";
                default -> "";
            };
        }

        @Override public boolean isCellEditable(int rowIndex, int columnIndex) {
            // Don't allow clicking apply on no-op row
            return columnIndex == COL_APPLY && !rows.get(rowIndex).edit().isNoOp();
        }
    }

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
        private final RowAction onClick;
        private int editingRow = -1;

        interface RowAction { void run(int row); }

        ButtonEditor(RowAction onClick) {
            super(new JTextField());
            this.onClick = onClick;

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

        @Override public Object getCellEditorValue() {
            return button.getText();
        }
    }
}