package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.ConditioningSetType;
import edu.cmu.tetrad.search.MarkovCheck;
import edu.cmu.tetrad.search.test.CachedIndependenceQueries;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetradapp.model.VertexCheckIndTestModel;
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
    private static final double EPS_NODEP = 1e-6;
    static double alpha = 0.01;
    // Canonical ordering that prioritizes REORIENTATION moves that IMPROVE MODEL SCORE.
    //
    // Summary:
    // 1) Primary objective still: reduce Markov violations (delta negative is good).
    // 2) Next: prefer edits that increase the *model score* (After - Baseline).
    // 3) Special preference: if it's a reorientation-only move AND it improves model score,
    //    give it an extra bonus so it rises above add/remove moves with similar stats.
    // 4) P-values + edges remain gentle tie-breakers.
    private static final Comparator<ScoredCandidate> CANONICAL_TABLE_ORDER = (a, b) -> {
        if (a == null && b == null) return 0;
        if (a == null) return 1;
        if (b == null) return -1;

        double ua = utility(a);
        double ub = utility(b);

        int c = -Double.compare(ua, ub);
        if (c != 0) return c;

        // Stable tie-breaker
        String ka = (a.edit() == null || a.edit().key() == null) ? "" : a.edit().key();
        String kb = (b.edit() == null || b.edit().key() == null) ? "" : b.edit().key();
        c = ka.compareTo(kb);
        if (c != 0) return c;

        String da = (a.edit() == null || a.edit().description() == null) ? "" : a.edit().description();
        String db = (b.edit() == null || b.edit().description() == null) ? "" : b.edit().description();
        return da.compareTo(db);
    };

    private final VertexCheckIndTestModel baseModel;
    // -------------------- move classification --------------------
// Prefer SIMPLE reorientation (single-edge REP:) over collider moves (multi-edge).
// We also treat collider moves as their own type so we can *penalize* them.
    private final Deque<Graph> history = new ArrayDeque<>();
    // UI
    private final JComboBox<RepairGraphType> graphTypeCombo = new JComboBox<>(RepairGraphType.values());
    private final JButton searchButton = new JButton();              // label set violationsAfter x is known
    private final JButton modelBestButton = new JButton("One Node Sweep");
    // -------------------- numeric helpers --------------------
    private final JButton backButton = new JButton("Undo");
    private final JButton showGraphButton = new JButton("Graph");
    private final JLabel statusLabel = new JLabel(" ");
    private final JTable resultsTable = new JTable();
    private final CandidateTableModel resultsModel = new CandidateTableModel();
    private final JPanel resultsCard = new JPanel(new CardLayout());
    private final CachedIndependenceQueries Q;
    private final VertexCheckIndTestModel model;
    private final JComboBox<Node> nodeCombo = new JComboBox<>();
    private Node x; // selected node (changes via dropdown)
    private Graph workingGraph;
    private Knowledge knowledge = new Knowledge();
    private TableRowSorter<CandidateTableModel> resultsSorter;
    private volatile SwingWorker<?, ?> activeWorker;
    private volatile JDialog watchDialog;

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

        wireActions();
        updateButtons();

        // Auto-populate table for initially selected node
        SwingUtilities.invokeLater(() -> startWatched("Searching", this::runSearchWatched, null));

        setPreferredSize(new Dimension(650, 600));
    }

    private static double utility(ScoredCandidate s) {
        if (s == null) return Double.NEGATIVE_INFINITY;
        if (!s.passesGuards()) return Double.NEGATIVE_INFINITY;

        final int delta = s.delta();        // negative good
        final int edgesAfter = s.edgesAfter();

        // Treat missing p-values as NEUTRAL, not catastrophic.
        final double mpAfter = s.modelPAfter();
        final double npAfter = s.nodePAfter();

        final double mpLogOdds = Double.isFinite(mpAfter) ? alphaLogOdds(mpAfter, alpha) : 0.0;
        final double npLogOdds = Double.isFinite(npAfter) ? alphaLogOdds(npAfter, alpha) : 0.0;

        // Model-P change: if unknown, treat as 0 (neutral), NOT -Infinity.
        final double mpBefore = s.modelPBefore();
        final double dMp = (Double.isFinite(mpBefore) && Double.isFinite(mpAfter)) ? (mpAfter - mpBefore) : 0.0;

        final MoveType mt = moveType(s.edit());

        int editSize = 1;
        try {
            if (s.edit() != null && s.edit().getEdges() != null) {
                editSize = Math.max(1, s.edit().getEdges().size());
            }
        } catch (Throwable ignored) {
            // keep editSize=1
        }

        // ---- weights ----
        final double W_DELTA = 2.0;    // dominates
        final double W_DMP   = 1.5;    // encourage better Model-P vs baseline
        final double W_NODE  = 0.35;
        final double W_MODEL = 0.35;
        final double W_EDGES = 1.0;
        final double W_EDIT_SZ = 0.90;

        final double BONUS_SIMPLE_REORIENT_IMPROVE = 5.0;
        final double PENALTY_COLLIDER_IMPROVE = 1.0;

        double bonus = 0.0;
        if (Double.isFinite(dMp) && dMp > 0.0) {
            if (mt == MoveType.REORIENT_SIMPLE) bonus += BONUS_SIMPLE_REORIENT_IMPROVE;
            if (mt == MoveType.COLLIDER_FIX)    bonus -= PENALTY_COLLIDER_IMPROVE;
        } else if (!Double.isFinite(mpAfter)) {
            // If Model-P wasn't computed, gently prefer simple reorients over collider fixes.
            if (mt == MoveType.REORIENT_SIMPLE) bonus += 0.25;
            if (mt == MoveType.COLLIDER_FIX)    bonus -= 0.25;
        }

        return (-W_DELTA * delta)
                + (W_DMP * dMp)
                + bonus
                + (W_MODEL * mpLogOdds)
                + (W_NODE * npLogOdds)
                - (W_EDGES * edgesAfter)
                - (W_EDIT_SZ * (editSize - 1));
    }

    private static MoveType moveType(CandidateEdit e) {
        if (e == null) return MoveType.OTHER;

        String k = safeLower(e.key());
        String d = safeLower(e.description());
        String s = (k + " " + d).trim();

        // Explicit add/remove first (unambiguous)
        if (containsAny(s, "rem:") || containsAny(s, "remove", "delete")) return MoveType.REMOVE_EDGE;
        if (containsAny(s, "add:") || containsAny(s, "add", "insert")) return MoveType.ADD_EDGE;

        // Collider fixes (usually MULTI:... and description starts with "Orient collider" / "Orient away from collider")
        if (containsAny(s, "orient collider", "orient away from collider")) {
            return MoveType.COLLIDER_FIX;
        }

        // Simple reorientation: typically REP:... and/or "replace" with same endpoints (orientation change)
        // We don’t try to prove it’s “orientation-only” here; we just prioritize these moves over collider moves.
        if (containsAny(s, "rep:") || containsAny(s, "replace", "reorient", "orient", "flip", "reverse", "endpoint")) {
            return MoveType.REORIENT_SIMPLE;
        }

        return MoveType.OTHER;
    }

    private static String safeLower(String s) {
        return s == null ? "" : s.toLowerCase();
    }

    private static boolean containsAny(String s, String... needles) {
        for (String n : needles) if (n != null && !n.isEmpty() && s.contains(n)) return true;
        return false;
    }

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
        for (Node n : f.getZ()) {
            if (n != null && n.getName() != null) z.add(n.getName());
        }

        Collections.sort(z);

        return a + "|" + b + "|" + String.join(",", z);
    }

    // ---------------------------------------------------------------------
    // UI
    // ---------------------------------------------------------------------

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

    // ---------------------------------------------------------------------
    // Search logic (watched, background)
    // ---------------------------------------------------------------------

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

    // ---------------------------------------------------------------------
    // Auto model best
    // ---------------------------------------------------------------------

    private static void vlog(String fmt, Object... args) {
        System.out.println("[VertexAutoRepair] " + String.format(fmt, args));
    }

    private static Endpoint endpointAt(Edge e, Node n) {
        if (e == null || n == null) return null;
        return e.getEndpoint(n);
    }

    private static Edge rebindEdgeToGraph(Graph g, Edge e) {
        if (g == null || e == null) return null;

        Node a0 = e.getNode1();
        Node b0 = e.getNode2();
        if (a0 == null || b0 == null) return null;

        String an = a0.getName();
        String bn = b0.getName();
        if (an == null || bn == null) return null;

        Node a = g.getNode(an);
        Node b = g.getNode(bn);
        if (a == null || b == null) return null;

        // Preserve endpoint-at-node semantics, regardless of node order
        Endpoint ea = e.getEndpoint(a0);
        Endpoint eb = e.getEndpoint(b0);
        return new Edge(a, b, ea, eb);
    }

    private static Edge getEdgeByNames(Graph g, Edge e) {
        if (g == null || e == null) return null;
        String a = e.getNode1() == null ? null : e.getNode1().getName();
        String b = e.getNode2() == null ? null : e.getNode2().getName();
        if (a == null || b == null) return null;
        Node ga = g.getNode(a);
        Node gb = g.getNode(b);
        if (ga == null || gb == null) return null;
        return g.getEdge(ga, gb);
    }

    // True iff graph contains an edge between the same named endpoints with the same endpoint types.
    private static boolean containsStructuralEdge(Graph g, Edge template) {
        if (g == null || template == null) return false;

        // rebind by names so node identity doesn't matter
        Edge reb = rebindEdgeToGraph(g, template);
        if (reb == null) return false;

        Edge inG = g.getEdge(reb.getNode1(), reb.getNode2());
        if (inG == null) return false;

        // Compare endpoints at each named node (order-independent)
        Endpoint a1 = inG.getEndpoint(reb.getNode1());
        Endpoint b1 = inG.getEndpoint(reb.getNode2());
        return a1 == reb.getEndpoint(reb.getNode1()) && b1 == reb.getEndpoint(reb.getNode2());
    }

    private static boolean requiresEdgePresenceCheck(CandidateEdit cand) {
        if (cand == null) return false;
        if (cand.isNoOp()) return false;
        if (cand.getEdges().size() >= 2) return false;
        // Removes don't have a "new edge" to verify.
        String k = cand.key();
        return k == null || !(k.startsWith("REM:"));
    }

    // ---------------------------------------------------------------------
    // Auto model best: "do no harm" sweep that greedily takes the TOP
    // table row for each node until the TOP row becomes NO-OP, then moves on.
    // One sweep only.
    // ---------------------------------------------------------------------

    private static boolean allIntendedNewEdgesPresent(Graph g, CandidateEdit cand) {
        if (g == null || cand == null) return false;
        List<Edge> intended = cand.getEdges();
        if (intended == null || intended.isEmpty()) return true; // nothing to verify
        for (Edge e : intended) {
            if (!containsStructuralEdge(g, e)) return false;
        }
        return true;
    }

    private static Graph seedDagFromAnyGraph(Graph g) {
        if (g == null) return null;

        // 1) Nodes in a stable order (natural sort)
        List<Node> nodes = new ArrayList<>(g.getNodes());
        nodes.sort(Comparator.comparing(Node::getName,
                Comparator.nullsLast(VertexCheckIndTestModel.NATURAL_NAME_COMPARATOR)));

        if (nodes.isEmpty()) return null;

        Map<String, Integer> idx = new HashMap<>();
        for (int i = 0; i < nodes.size(); i++) {
            String name = nodes.get(i).getName();
            if (name != null) idx.put(name, i);
        }

        // 2) Build a DAG with the same adjacencies (ignore endpoints), orienting by order
        Graph dag = new EdgeListGraph(nodes);

        Set<String> seenPairs = new HashSet<>();
        for (Edge e : g.getEdges()) {
            Node a0 = e.getNode1();
            Node b0 = e.getNode2();
            if (a0 == null || b0 == null) continue;

            String an0 = a0.getName();
            String bn0 = b0.getName();
            if (an0 == null || bn0 == null) continue;

            Node a = dag.getNode(an0);
            Node b = dag.getNode(bn0);
            if (a == null || b == null || a.equals(b)) continue;

            String key = (an0.compareTo(bn0) <= 0) ? (an0 + "|" + bn0) : (bn0 + "|" + an0);
            if (!seenPairs.add(key)) continue;

            int ia = idx.getOrDefault(a.getName(), 0);
            int ib = idx.getOrDefault(b.getName(), 0);

            if (ia <= ib) dag.addEdge(new Edge(a, b, Endpoint.TAIL, Endpoint.ARROW));
            else dag.addEdge(new Edge(b, a, Endpoint.TAIL, Endpoint.ARROW));
        }

        return dag.paths().isLegalDag() ? dag : null;
    }

    // ---------------------------------------------------------------------
    // Apply / undo / graph view
    // ---------------------------------------------------------------------

    private static int comparePDescNaNLast(double a, double b) {
        boolean aNaN = Double.isNaN(a);
        boolean bNaN = Double.isNaN(b);
        if (aNaN && bNaN) return 0;
        if (aNaN) return 1;     // NaN last
        if (bNaN) return -1;
        return -Double.compare(a, b); // DESC
    }

    private static double finiteOrDefault(double x, double dflt) {
        return Double.isFinite(x) ? x : dflt;
    }

    private static double logOdds(double p) {
        // Clamp to avoid infinities and to keep NaN/invalid p from exploding.
        if (!Double.isFinite(p)) return -50.0; // very bad
        if (p <= 0.0) return -50.0;
        if (p >= 1.0) return 50.0;
        final double eps = 1e-12;
        double q = Math.min(1.0 - eps, Math.max(eps, p));
        return Math.log(q) - Math.log(1.0 - q);
    }

    private static double alphaLogOdds(double p, double alpha) {
        if (!Double.isFinite(p)) return -50.0;
        if (!Double.isFinite(alpha) || alpha <= 0.0 || alpha >= 1.0)
            throw new IllegalArgumentException("alpha must be in (0,1)");

        final double eps = 1e-12;

        double q = Math.min(1.0 - eps, Math.max(eps, p));
        double a = Math.min(1.0 - eps, Math.max(eps, alpha));

        // log(p/(1-p)) - log(alpha/(1-alpha))
        return (Math.log(q) - Math.log(1.0 - q))
                - (Math.log(a) - Math.log(1.0 - a));
    }

    // Accept if:
//  (A) violations decrease, OR
//  (B) violations tie and edges decrease, OR
//  (C) violations tie and edges tie and Model-P increases by at least MIN_MP_GAIN.
    private static boolean isProgress(int baselineViol,
                                      int afterViol,
                                      int currentEdges,
                                      int afterEdges,
                                      double mpBefore,
                                      double mpAfter) {

        if (afterViol < baselineViol) return true;

        if (afterViol == baselineViol) {
            if (afterEdges < currentEdges) return true;

            // NEW: allow pure "quality" improvement when structure doesn't worsen.
            final double MIN_MP_GAIN = 1e-3; // tune; 0.001 is usually safe
            if (afterEdges == currentEdges
                    && Double.isFinite(mpBefore)
                    && Double.isFinite(mpAfter)
                    && (mpAfter - mpBefore) >= MIN_MP_GAIN) {
                return true;
            }
        }

        return false;
    }

    private Node resolveInitialNode(Graph g, Node requested) {
        if (g == null) return requested; // nothing better we can do
        List<Node> nodes = new ArrayList<>(g.getNodes());
        nodes.sort(Comparator.comparing(Node::getName, Comparator.nullsLast(String::compareTo)));

        if (nodes.isEmpty()) return requested;

        // if null or not in graph, choose first node
        if (requested == null || requested.getName() == null) return nodes.getFirst();

        Node inGraph = g.getNode(requested.getName());
        return (inGraph != null) ? inGraph : nodes.getFirst();
    }

    /**
     * Caller reads this violationsAfter dialog closes.
     */
    public Graph getGraph() {
        return workingGraph;
    }

    private void buildUI() {
        JPanel controls = new JPanel(new GridBagLayout());
        controls.setBorder(new TitledBorder("Repair Model"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);

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
        c.gridy = 2;
        c.gridwidth = 1;

        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;

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
                    CandidateEdit cand = resultsModel.getCandidate(row); // no sorter => row == model row
                    applyCandidate(cand);
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

    // ---------------------------------------------------------------------
    // Canonicalization / legality / copies
    // ---------------------------------------------------------------------

    private void populateNodeCombo() {
        DefaultComboBoxModel<Node> m = new DefaultComboBoxModel<>();

        if (workingGraph != null) {
            List<Node> nodes = new ArrayList<>(workingGraph.getNodes());
            nodes.sort(Comparator.comparing(Node::getName, VertexCheckIndTestModel.NATURAL_NAME_COMPARATOR));
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
        resultsModel.sortByCanonicalOrder();
    }

    private void wireActions() {
        backButton.addActionListener(e -> goBack());
        showGraphButton.addActionListener(e -> showGraphDialog());

        // Adjust selected node x (the panel’s focus node)
        searchButton.addActionListener(e -> startWatched("Searching", this::runSearchWatched, null));

        nodeCombo.addActionListener(e -> {
            Object sel = nodeCombo.getSelectedItem();
            if (!(sel instanceof Node n)) return;

            // Resolve to node in current workingGraph (important violationsAfter edits/canonicalization)
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
        modelBestButton.addActionListener(e ->
                startWatched("Auto-repairing", this::runModelBestWatched,
                        () -> startWatched("Searching", this::runSearchWatched, null)));
    }

    private void updateButtons() {
        backButton.setEnabled(!history.isEmpty());
        boolean busy = (activeWorker != null);
        searchButton.setEnabled(!busy);
        modelBestButton.setEnabled(!busy);
    }

    /**
     * One sweep over nodes. For each node, repeatedly:
     * - compute the candidate table for that node (same 2-pass scoring behavior as UI),
     * - sort exactly like the JTable,
     * - take the top row,
     * - apply it (guarded: progress + do-no-harm),
     * until the top row is "No change" (or no applicable move), then advance to next node.
     * <p>
     * Stops violationsAfter one pass through the nodes (no outer repetition).
     */

    // ---------------------------------------------------------------------
// Search logic (watched, background)
// ---------------------------------------------------------------------
    private void runSearchWatched() {
        RepairGraphType gt = (RepairGraphType) graphTypeCombo.getSelectedItem();
        Graph base = safeCopy(workingGraph);

        if (stopRequested()) return;

        if (gt == RepairGraphType.CPDAG) {
            base = canonicalizeToCpdagOrNull(base);
            if (base == null) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Could not canonicalize to CPDAG (unexpected).");
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
            candidates.addFirst(CandidateEdit.noOp());
        }

        if (stopRequested()) return;

        GlobalEvalCache baseCache = buildBaselineCache(base);

        // Baseline violations via locality
        GraphEval baseEval = evalGraphLocality(baseCache, base, Set.of(), false);
        int baseline = baseEval.violations();

        // Baseline Model-P (mpBefore for all rows in this run)
        double mpBefore = evalGraphOnce(base).modelP();

        Map<String, Graph> candGraphByKey = new HashMap<>();
        List<ScoredCandidate> scored = new ArrayList<>();

        // PASS 1: violationsAfter + Node-P + edges for all candidates (Model-P deferred)
        for (CandidateEdit cand : candidates) {
            if (stopRequested()) return;

            Graph finalBase = base;
            Graph g2 = candGraphByKey.computeIfAbsent(cand.key(), k -> buildCandidateGraph(finalBase, cand, gt));
            if (g2 == null) continue;

            if (knowledge != null && knowledge.isViolatedBy(g2)) continue;

            boolean useLocality = (gt == RepairGraphType.DAG || gt == RepairGraphType.CPDAG || gt == RepairGraphType.PDAG);
            Set<String> affected = affectedVertices(base, x, g2);

            int after = useLocality
                    ? evalGraphLocality(baseCache, g2, affected, false).violations()
                    : evalViolationsOnly(g2);

            double nodePAfter = nodePValue(g2, x);
            int edgesAfter = g2.getNumEdges();

//            scored.add(new ScoredCandidate(cand, baseline, after, nodePAfter, Double.NaN, Double.NaN, edgesAfter, true));

            scored.add(new ScoredCandidate(cand, baseline, after, nodePAfter,
                    Double.NaN, Double.NaN, edgesAfter, true));
        }

        if (stopRequested()) return;

        // PASS 2: compute Model-P for (top-K rows) UNION (all REORIENT_ONLY moves)
        // This ensures "reorientation improves Model-P" can actually surface to the top.
        List<ScoredCandidate> ranked = new ArrayList<>(scored);
        ranked.sort(CANONICAL_TABLE_ORDER);

        final int topK = Math.min(DEFAULT_MODELP_TOP_K, ranked.size());
        final LinkedHashSet<String> keysToEval = new LinkedHashSet<>();

        // 2a) always compute for top-K rows (table-surfaced set)
        for (int i = 0; i < topK; i++) {
            ScoredCandidate sc = ranked.get(i);
            if (sc == null || sc.edit() == null) continue;
            keysToEval.add(sc.edit().key());
        }

        // 2b) additionally compute for *all* reorientation-only candidates
        for (ScoredCandidate sc : scored) {
            if (sc == null || sc.edit() == null) continue;
            if (moveType(sc.edit()) == MoveType.REORIENT_SIMPLE) {
                keysToEval.add(sc.edit().key());
            }
        }

        Map<String, Double> mpAfterByKey = new HashMap<>(keysToEval.size() * 2);

        for (String key : keysToEval) {
            if (stopRequested()) return;
            if (key == null) continue;

            Graph g2 = candGraphByKey.get(key);
            if (g2 == null) continue;

            double mpAfter = evalGraphOnce(g2).modelP();
            mpAfterByKey.put(key, mpAfter);
        }

        // Patch mpBefore/mpAfter into rows (mpBefore constant for this run)
        {
            List<ScoredCandidate> patched = new ArrayList<>(scored.size());
            for (ScoredCandidate sc : scored) {
                Double mpAfter = mpAfterByKey.get(sc.edit().key());
                patched.add(new ScoredCandidate(
                        sc.edit(),
                        sc.violationsBaseline(),
                        sc.violationsAfter(),
                        sc.nodePAfter(),
                        mpBefore,
                        (mpAfter == null ? Double.NaN : mpAfter),
                        sc.edgesAfter(),
                        true
                ));
            }
            scored = patched;
        }

        {
            List<ScoredCandidate> patched2 = new ArrayList<>(scored.size());
            for (ScoredCandidate sc : scored) {
                boolean ok = wouldPassGuards(base, x, sc, gt);

                patched2.add(new ScoredCandidate(
                        sc.edit(),
                        sc.violationsBaseline(),
                        sc.violationsAfter(),
                        sc.nodePAfter(),
                        sc.modelPBefore(),
                        sc.modelPAfter(),
                        sc.edgesAfter(),
                        ok
                ));
            }
            scored = patched2;
        }

        // Determine best candidate for status line
        List<ScoredCandidate> rankedForStatus = new ArrayList<>(scored);
        rankedForStatus.sort(CANONICAL_TABLE_ORDER);
        ScoredCandidate bestCand = rankedForStatus.isEmpty() ? null : rankedForStatus.getFirst();

        List<ScoredCandidate> finalScored = scored;
        SwingUtilities.invokeLater(() -> {
            resultsModel.set(finalScored);
            applySortAndFilter();

            NumberFormat fmt = new DecimalFormat("0.0000");
            if (finalScored.isEmpty()) {
                statusLabel.setText("No legal candidate edits found.");
                ((CardLayout) resultsCard.getLayout()).show(resultsCard, CARD_NONE);
            } else {
                int bestViol = (bestCand == null) ? baseline : bestCand.violationsAfter();
                String mpBestStr = (bestCand == null || Double.isNaN(bestCand.modelPAfter()))
                        ? "n/a"
                        : fmt.format(bestCand.modelPAfter());

                statusLabel.setText(
                        "Baseline violations: " + baseline +
                                " | Best: " + bestViol +
                                " | Model-P: " + fmt.format(mpBefore) + " \u2192 " + mpBestStr
                );
                ((CardLayout) resultsCard.getLayout()).show(resultsCard, CARD_TABLE);
            }
        });
    }

    // ---------------------------------------------------------------------
    // Knowledge
    // ---------------------------------------------------------------------

    /**
     * One sweep over nodes. For each node, repeatedly:
     * - compute the candidate table for that node (same 2-pass scoring behavior as UI),
     * - sort exactly like the JTable (i.e., by the *table values*),
     * - try the ranked rows in order until one passes guards,
     * until the top row becomes NO-OP (or nothing passes guards), then advance to next node.
     * <p>
     * Stops violationsAfter one pass through the nodes (no outer repetition).
     */
    private void runModelBestWatched() {
        // Single-undo checkpoint for the whole run
        Graph checkpoint = safeCopy(workingGraph);

        final RepairGraphType gt = (RepairGraphType) graphTypeCombo.getSelectedItem();

        int editsApplied = 0;
        final int MAX_EDITS = 500;           // global safety cap
        final int MAX_STEPS_PER_NODE = 4; // per-node s  afety cap

        vlog("==================================================");
        vlog("AUTO-REPAIR (greedy table-order, one sweep) (type=%s)", String.valueOf(gt));
        vlog("==================================================");

        // One sweep only, but order nodes by increasing Node-P (NaN last), then by name for stability
        List<Node> nodes = new ArrayList<>(workingGraph.getNodes());
        Map<String, Double> nodePOrder = new HashMap<>();

        for (Node n : nodes) {
            if (n == null || n.getName() == null) continue;
            // nodePValue resolves by name inside g, so passing n is fine
            double p = nodePValue(workingGraph, n);
            nodePOrder.put(n.getName(), p);
        }

        nodes.sort((a, b) -> {
            if (a == null && b == null) return 0;
            if (a == null) return 1;
            if (b == null) return -1;

            String an = a.getName();
            String bn = b.getName();

            double pa = (an == null) ? Double.NaN : nodePOrder.getOrDefault(an, Double.NaN);
            double pb = (bn == null) ? Double.NaN : nodePOrder.getOrDefault(bn, Double.NaN);

            boolean aNaN = Double.isNaN(pa);
            boolean bNaN = Double.isNaN(pb);

            // NaN last
            if (aNaN && bNaN) {
                return VertexCheckIndTestModel.NATURAL_NAME_COMPARATOR.compare(an, bn);
            }
            if (aNaN) return 1;
            if (bNaN) return -1;

            int c = Double.compare(pa, pb); // ASC (increasing node-p)
            if (c != 0) return c;

            // stable tiebreak
            return VertexCheckIndTestModel.NATURAL_NAME_COMPARATOR.compare(an, bn);
        });

        for (Node v0 : nodes) {
            if (stopRequested()) return;
            if (editsApplied >= MAX_EDITS) break;

            if (v0 == null || v0.getName() == null) continue;

            // Re-resolve each time from current workingGraph (edits/canonicalization can replace node objects)
            Node center = workingGraph.getNode(v0.getName());
            if (center == null) continue;

            vlog("--------------------------------------------------");
            vlog("Editing node: %s", center.getName());

            Set<String> seenSignatures = new HashSet<>();
            int nodeSteps = 0;

            while (editsApplied < MAX_EDITS) {
                if (stopRequested()) return;

                nodeSteps++;
                if (nodeSteps > MAX_STEPS_PER_NODE) {
                    vlog("STOP node %s: hit MAX_STEPS_PER_NODE=%d", center.getName(), MAX_STEPS_PER_NODE);
                    break;
                }

                // Refresh center in case the graph swapped node instances
                center = workingGraph.getNode(center.getName());
                if (center == null) {
                    vlog("STOP node %s: center vanished from graph.", v0.getName());
                    break;
                }

                String sig = graphSignature(workingGraph);
                if (!seenSignatures.add(sig)) {
                    vlog("STOP node %s: detected cycle (graph signature repeated).", center.getName());
                    break;
                }

                SearchPack pack = computeCandidatesForNode(workingGraph, center, gt);
                if (pack == null || pack.scored == null || pack.scored.isEmpty()) {
                    vlog("STOP node %s: no candidates.", center.getName());
                    break;
                }

                // Rank exactly like the JTable (given current table model)
                List<ScoredCandidate> ranked = new ArrayList<>(pack.scored);
                ranked.sort(CANONICAL_TABLE_ORDER);

                // If the top row is NO-OP, we're done with this node.
                ScoredCandidate top = ranked.getFirst();
                if (top == null || top.edit() == null || top.edit().isNoOp()) {
                    vlog("STOP node %s: top row is NO-OP.", center.getName());
                    break;
                }

                // Try rows in ranked order until one passes guards; if none do, stop this node.
                boolean moved = false;

                for (ScoredCandidate sc : ranked) {
                    if (sc == null || sc.edit() == null) continue;
                    if (sc.edit().isNoOp()) break; // once we hit NO-OP, nothing below it matters

                    vlog("Consider move: %s | base=%d violationsAfter=%d delta=%d edges=%d nodeP=%s modelP=%s",
                            sc.edit().description(),
                            sc.violationsBaseline(),
                            sc.violationsAfter(),
                            sc.delta(),
                            sc.edgesAfter(),
                            fmtP(sc.nodePAfter()),
                            fmtP(sc.modelPAfter()));

                    if (tryMoveWithGuards(workingGraph, center, sc, gt)) {
//                    if (tryMoveWithGuards(workingGraph, center, sc.edit(), gt)) {
                        editsApplied++;
                        int finalEditsApplied = editsApplied;
                        SwingUtilities.invokeLater(() ->
                                statusLabel.setText("Auto-repair: applied " + finalEditsApplied + " edits..."));

                        vlog("APPLIED move for node %s: %s", center.getName(), sc.edit().description());
                        moved = true;
                        break; // recompute pack for same node
                    } else {
                        vlog("Rejected by guards: %s", sc.edit().description());
                    }
                }

                if (!moved) {
                    vlog("STOP node %s: no ranked move passed guards.", center.getName());
                    break;
                }
            }

            vlog("Finished node: %s", v0.getName());
        }

        vlog("Checkpoint signature: %s", graphSignature(checkpoint));

        final int finalEdits = editsApplied;
        SwingUtilities.invokeLater(() -> {
            history.clear();
            history.push(checkpoint);
            updateButtons();
            statusLabel.setText("Auto-repair applied " + finalEdits + " edits.");
            startWatched("Searching", this::runSearchWatched, null);
        });
    }

    // ---------------------------------------------------------------------
    // Cached CI access
    // ---------------------------------------------------------------------

    /**
     * Compute candidates for a *given* node center (like the panel does for x),
     * but returned as a SearchPack for auto-selection.
     * <p>
     * This intentionally mirrors the panel’s 2-pass approach:
     * - pass 1: After + Node-P for all
     * - pass 2: Model-P for top-K only (so NaNs behave the same as the UI)
     */
//    private SearchPack computeCandidatesForNode(Graph g, Node center, RepairGraphType gt) {
//        if (g == null || center == null) return null;
//
//        Graph base = safeCopy(g);
//
//        if (stopRequested()) return null;
//
//        if (gt == RepairGraphType.CPDAG || gt == RepairGraphType.PDAG) {
//            base = canonicalizeToCpdagOrNull(base);
//            if (base == null) return null;
//        } else if (gt == RepairGraphType.PAG) {
//            base = canonicalizeToPagOrNull(base);
//            if (base == null) return null;
//        }
//
//        if (knowledge != null && knowledge.isViolatedBy(base)) {
//            return null;
//        }
//
//        List<CandidateEdit> candidates = enumerateCandidates(base, center, gt);
//        candidates = new ArrayList<>(candidates);
//        if (candidates.stream().noneMatch(CandidateEdit::isNoOp)) {
//            candidates.addFirst(CandidateEdit.noOp());
//        }
//
//        GlobalEvalCache baseCache = buildBaselineCache(base);
//
//        // Baseline violations only via locality
//        GraphEval baseEval = evalGraphLocality(baseCache, base, Set.of(), false);
//        int violationsBaseline = baseEval.violations();
//
//        // (Optional) if you ever want violationsBaseline Model-P in SearchPack, compute it like this:
//        // double baselineModelP = evalGraphOnce(base).modelP();
//
//        Map<String, Graph> candGraphByKey = new HashMap<>();
//        List<ScoredCandidate> scored = new ArrayList<>();
//
//        // PASS 1: violationsAfter + nodeP + edges
//        for (CandidateEdit cand : candidates) {
//            if (stopRequested()) return null;
//
//            Graph finalBase = base;
//            Graph g2 = candGraphByKey.computeIfAbsent(cand.key(), k -> buildCandidateGraph(finalBase, cand, gt));
//            if (g2 == null) continue;
//
//            if (knowledge != null && knowledge.isViolatedBy(g2)) continue;
//
//            boolean useLocality = (gt == RepairGraphType.DAG || gt == RepairGraphType.CPDAG || gt == RepairGraphType.PDAG);
//            Set<String> affected = affectedVertices(base, center, g2);
//
//            int violationsAfter = useLocality
//                    ? evalGraphLocality(baseCache, g2, affected, false).violations()
//                    : evalViolationsOnly(g2);
//
//            double nodePAfter = nodePValue(g2, center);
//            int edgesAfter = g2.getNumEdges();
//
//            scored.add(new ScoredCandidate(cand, violationsBaseline, violationsAfter, nodePAfter, Double.NaN, Double.NaN, edgesAfter));
//        }
//
//        if (stopRequested()) return null;
//
//        // PASS 2: compute Model-P for top-K only (same as UI behavior)
//        List<ScoredCandidate> ranked = new ArrayList<>(scored);
//        ranked.sort(CANONICAL_TABLE_ORDER);
//
//        int k = ranked.size();//Math.min(modelPTopK, ranked.size());
//        Map<String, Double> modelPByKey = new HashMap<>(k * 2);
//
//        for (ScoredCandidate scoredCandidate : ranked) {
//            if (stopRequested()) return null;
//
//            Graph g2 = candGraphByKey.get(scoredCandidate.edit().key());
//            if (g2 == null) continue;
//
//            double mp = evalGraphOnce(g2).modelP();
//            modelPByKey.put(scoredCandidate.edit().key(), mp);
//        }
//
//        if (!modelPByKey.isEmpty()) {
//            List<ScoredCandidate> patched = new ArrayList<>(scored.size());
//            for (ScoredCandidate sc : scored) {
//                Double mpAfter = modelPByKey.get(sc.edit().key());
//                patched.add(mpAfter == null ? sc : new ScoredCandidate(
//                        sc.edit(), sc.violationsBaseline(), sc.violationsAfter(), sc.nodePAfter(), mpBefore, mpAfter, sc.edgesAfter()
//                ));
//            }
//            scored = patched;
//        }
//
//        return new SearchPack(center.getName(), violationsBaseline, scored);
//    }
    // ---------------------------------------------------------------------
// Auto-selection helper: compute candidates for a given node center
// Mirrors UI behavior but ALSO forces Model-P for all reorientation moves.
// ---------------------------------------------------------------------
    // ---------------------------------------------------------------------
// Auto-selection helper: compute candidates for a given node center
// Mirrors UI behavior and forces Model-P for (top-K rows) ∪ (all simple reorients),
// then computes passesGuards consistently with the UI.
// ---------------------------------------------------------------------
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
            candidates.addFirst(CandidateEdit.noOp());
        }

        GlobalEvalCache baseCache = buildBaselineCache(base);

        // Baseline violations via locality (consistent with your locality merges)
        GraphEval baseEval = evalGraphLocality(baseCache, base, Set.of(), false);
        int baseline = baseEval.violations();

        // Baseline Model-P (mpBefore constant within this pack)
        double mpBefore = evalGraphOnce(base).modelP();

        Map<String, Graph> candGraphByKey = new HashMap<>();
        List<ScoredCandidate> scored = new ArrayList<>();

        // PASS 1: violationsAfter + nodeP + edges (Model-P deferred)
        for (CandidateEdit cand : candidates) {
            if (stopRequested()) return null;

            Graph finalBase = base;
            Graph g2 = candGraphByKey.computeIfAbsent(cand.key(), k -> buildCandidateGraph(finalBase, cand, gt));
            if (g2 == null) continue;

            if (knowledge != null && knowledge.isViolatedBy(g2)) continue;

            boolean useLocality = (gt == RepairGraphType.DAG || gt == RepairGraphType.CPDAG || gt == RepairGraphType.PDAG);
            Set<String> affected = affectedVertices(base, center, g2);

            int after = useLocality
                    ? evalGraphLocality(baseCache, g2, affected, false).violations()
                    : evalViolationsOnly(g2);

            double nodePAfter = nodePValue(g2, center);
            int edgesAfter = g2.getNumEdges();

            // passesGuards patched later
            scored.add(new ScoredCandidate(cand, baseline, after, nodePAfter,
                    Double.NaN, Double.NaN, edgesAfter, true));
        }

        if (stopRequested()) return null;

        // PASS 2: compute Model-P for (top-K rows) UNION (all REORIENT_SIMPLE moves)
        List<ScoredCandidate> ranked = new ArrayList<>(scored);
        ranked.sort(CANONICAL_TABLE_ORDER);

        final int topK = Math.min(DEFAULT_MODELP_TOP_K, ranked.size());
        final LinkedHashSet<String> keysToEval = new LinkedHashSet<>();

        // 2a) top-K (table-surfaced set)
        for (int i = 0; i < topK; i++) {
            ScoredCandidate sc = ranked.get(i);
            if (sc == null || sc.edit() == null) continue;
            keysToEval.add(sc.edit().key());
        }

        // 2b) all simple reorientation moves
        for (ScoredCandidate sc : scored) {
            if (sc == null || sc.edit() == null) continue;
            if (moveType(sc.edit()) == MoveType.REORIENT_SIMPLE) {
                keysToEval.add(sc.edit().key());
            }
        }

        Map<String, Double> mpAfterByKey = new HashMap<>(keysToEval.size() * 2);

        for (String key : keysToEval) {
            if (stopRequested()) return null;
            if (key == null) continue;

            Graph g2 = candGraphByKey.get(key);
            if (g2 == null) continue;

            double mpAfter = evalGraphOnce(g2).modelP();
            mpAfterByKey.put(key, mpAfter);
        }

        // Patch mpBefore/mpAfter into scored rows (mpBefore constant for this pack)
        {
            List<ScoredCandidate> patched = new ArrayList<>(scored.size());
            for (ScoredCandidate sc : scored) {
                Double mpAfter = (sc.edit() == null) ? null : mpAfterByKey.get(sc.edit().key());

                patched.add(new ScoredCandidate(
                        sc.edit(),
                        sc.violationsBaseline(),
                        sc.violationsAfter(),
                        sc.nodePAfter(),
                        mpBefore,
                        (mpAfter == null ? Double.NaN : mpAfter),
                        sc.edgesAfter(),
                        true // patched next
                ));
            }
            scored = patched;
        }

        // PASS 3: compute passesGuards consistently with the UI path
        {
            List<ScoredCandidate> patched2 = new ArrayList<>(scored.size());
            for (ScoredCandidate sc : scored) {
                boolean ok = wouldPassGuards(base, center, sc, gt);

                patched2.add(new ScoredCandidate(
                        sc.edit(),
                        sc.violationsBaseline(),
                        sc.violationsAfter(),
                        sc.nodePAfter(),
                        sc.modelPBefore(),
                        sc.modelPAfter(),
                        sc.edgesAfter(),
                        ok
                ));
            }
            scored = patched2;
        }

        return new SearchPack(center.getName(), baseline, scored);
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

    // ---------------------------------------------------------------------
    // Global evaluation
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
                if (updateStatus)
                    statusLabel.setText("Failed to apply (CPDAG canonicalization): " + cand.description());
                if (pushHistory && !history.isEmpty()) history.pop();
                return false;
            }
        } else if (gt == RepairGraphType.PAG) {
            // you currently “keep as-is” for PAG; that’s fine if your edits always produce legal PAGs
            // otherwise you might want to canonicalize here too.
        }

        // **CRITICAL**: if the move does not change the graph, treat it as “no move”
        if (g2.equals(base)) {
            vlog("REJECTED (no graph change violationsAfter canonicalization)");
            if (updateStatus) statusLabel.setText("No-op violationsAfter canonicalization: " + cand.description());
            if (pushHistory && !history.isEmpty()) history.pop();
            return false;
        }

        if (requiresEdgePresenceCheck(cand) && !allIntendedNewEdgesPresent(g2, cand)) {
            vlog("REJECTED (intended new edge(s) not present violationsAfter apply/canonicalization)");
            if (updateStatus) statusLabel.setText("Skipped (edge vanished): " + cand.description());
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

        // 4) CPDAG-only: 2-edge collider fixes (unshielded triples)
        if (gt == RepairGraphType.CPDAG) {
            out.addAll(enumerateCpdagColliderPairMoves(g, x));
        }

        return dedupCandidateEdits(out);
    }

    private List<CandidateEdit> enumerateCpdagColliderPairMoves(Graph g, Node x) {
        if (g == null || x == null) return List.of();

        List<CandidateEdit> out = new ArrayList<>();

        List<Node> adj = new ArrayList<>(g.getAdjacentNodes(x));
        // (optional) stable order for repeatability
        adj.sort(Comparator.comparing(Node::getName, Comparator.nullsLast(String::compareTo)));

        for (int i = 0; i < adj.size(); i++) {
            Node y = adj.get(i);
            if (y == null) continue;

            Edge exy = g.getEdge(x, y);
            if (exy == null) continue;

            for (int j = i + 1; j < adj.size(); j++) {
                Node z = adj.get(j);
                if (z == null) continue;

                if (g.isAdjacentTo(y, z)) continue; // only unshielded triples

                Edge exz = g.getEdge(x, z);
                if (exz == null) continue;

                Endpoint endXy = endpointAt(exy, x);
                Endpoint endXz = endpointAt(exz, x);

                // Case A: noncollider y *-* x *-* z. Propose orient into X: Y->X<-Z.
                if (!(endXy == Endpoint.ARROW && endXz == Endpoint.ARROW)) {
                    Edge yToX = new Edge(y, x, Endpoint.TAIL, Endpoint.ARROW);
                    Edge zToX = new Edge(z, x, Endpoint.TAIL, Endpoint.ARROW);

                    String label = "Orient collider " + y.getName() + "->" + x.getName() + "<-" + z.getName();
                    out.add(CandidateEdit.replaceEdges(
                            label,
                            List.of(exy, exz),
                            List.of(yToX, zToX)
                    ));
                }

                // Case B: collider already (two arrows at X). Propose orient away: X->Y and X->Z.
                if (endXy == Endpoint.ARROW && endXz == Endpoint.ARROW) {
                    Edge xToY = new Edge(x, y, Endpoint.TAIL, Endpoint.ARROW);
                    Edge xToZ = new Edge(x, z, Endpoint.TAIL, Endpoint.ARROW);

                    String label = "Orient away from collider " + y.getName() + "<-" + x.getName() + "->" + z.getName();
                    out.add(CandidateEdit.replaceEdges(
                            label,
                            List.of(exy, exz),
                            List.of(xToY, xToZ)
                    ));
                }
            }
        }

        return out;
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

    // ---------------------------------------------------------------------
    // Preferences
    // ---------------------------------------------------------------------

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

    private Graph canonicalizeToCpdagOrNull(Graph h) {
        if (h == null) return null;

        try {
            Graph h2 = new EdgeListGraph(h);

            // Case 1: already a legal DAG → project to CPDAG
            if (h2.paths().isLegalDag()) {
                return GraphTransforms.dagToCpdag(h2);
            }

            // Case 2: legal CPDAG/PDAG → pick an extension and project back
            if (h2.paths().isLegalCpdag() || h2.paths().isLegalPdag()) {
                Graph dag = GraphTransforms.dagFromCpdag(h2);
                return GraphTransforms.dagToCpdag(dag);
            }

            // Case 3: arbitrary / illegal PDAG → seed a DAG from the adjacency skeleton
            Graph seed = seedDagFromAnyGraph(h2);
            if (seed == null) return null; // only null if nodes empty or something truly broken
            return GraphTransforms.dagToCpdag(seed);

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

    // ---------------------------------------------------------------------
    // Worker/watch dialog
    // ---------------------------------------------------------------------

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

    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = (knowledge == null) ? new Knowledge() : knowledge;

        if (workingGraph != null && this.knowledge.isViolatedBy(workingGraph)) {
            throw new IllegalArgumentException("The given Knowledge object is violated by the current graph.");
        }
    }

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

    // ---------------------------------------------------------------------
    // Types
    // ---------------------------------------------------------------------

    private double nodePValue(Graph g, Node vertexInOriginalGraph) {
        if (g == null || vertexInOriginalGraph == null) return Double.NaN;

        Node v = g.getNode(vertexInOriginalGraph.getName());
        if (v == null) return Double.NaN;

        ConditioningSetType type = baseModel.getConditioningSetType();

        List<IndependenceFact> facts = MarkovCheck.computeImpliedFactsForVertex(g, v, type);
        if (facts.isEmpty()) return Double.NaN;

        List<Double> pvals = Q.pValuesForFacts(facts, CachedIndependenceQueries.Dedup.WITHIN_INPUT);
        return model.getUniformityP(pvals);
    }

    private GraphEval evalGraphOnce(Graph g) {
        if (g == null) return new GraphEval(0, Double.NaN, 0);

        ConditioningSetType type = baseModel.getConditioningSetType();

        List<IndependenceFact> facts = MarkovCheck.computeAllImpliedFacts(g, type);
        if (facts.isEmpty()) return new GraphEval(0, Double.NaN, 0);

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

        ConditioningSetType type = baseModel.getConditioningSetType();

        List<IndependenceFact> facts = MarkovCheck.computeAllImpliedFacts(g, type);
        if (facts.isEmpty()) return 0;

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

        ConditioningSetType type = baseModel.getConditioningSetType();

        List<IndependenceFact> facts = MarkovCheck.computeImpliedFactsForVertex(g, v, type);
        if (facts.isEmpty()) return new VertexContribution(Map.of(), Map.of());

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

    private GraphEval evalGraphLocality(GlobalEvalCache baseCache,
                                        Graph candidateGraph,
                                        Set<String> affectedVertexNames,
                                        boolean computeModelP) {
        if (candidateGraph == null) return new GraphEval(0, Double.NaN, 0);

        // 1) Start from violationsBaseline vertex contributions (shallow copy map)
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
        if (computeModelP && globalPByKey.size() >= 2) {
            List<Double> pvals = new ArrayList<>(globalPByKey.values());

            pvals.sort(Double::compareTo);
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

        if (requiresEdgePresenceCheck(cand) && !allIntendedNewEdgesPresent(g2, cand)) {
            return null;
        }

        try {
            if (gt != null && !isLegalGraphType(g2, gt)) return null;
        } catch (Exception ignored) {
            return null;
        }

        return g2;
    }

    private Set<String> affectedVertices(Graph base, Node x, Graph candidate) {
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
                } catch (Exception ex) {
                    statusLabel.setText("Error: " + (ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage()));
                } finally {
                    closeWatchDialog();
                    activeWorker = null;
                    updateButtons();

                    // ✅ now it's safe; the guard won't block it
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

    // ---------------------------------------------------------------------
    // Table-order aware comparators (match the JTable ordering)
    // ---------------------------------------------------------------------

    private void closeWatchDialog() {
        JDialog dlg = this.watchDialog;
        this.watchDialog = null;
        if (dlg != null) dlg.dispose();
    }

    private boolean stopRequested() {
        SwingWorker<?, ?> w = activeWorker;
        return (w != null && w.isCancelled()) || Thread.currentThread().isInterrupted();
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
            double eps = EPS_NODEP;
//            if (p1 < p0 - eps) return false;

            if (p1 < p0 - eps) {
                vlog("Do-no-harm fail: %s p0=%s p1=%s (eps=%g)", name, fmtP(p0), fmtP(p1), eps);
                return false;
            }
        }
        return true;
    }

//    // Progress gate: must improve violations, or tie violations and reduce edges
//    private boolean isProgress(int baselineViol, int afterViol, int currentEdges, int afterEdges) {
//        if (afterViol < baselineViol) return true;
//        return afterViol == baselineViol && afterEdges < currentEdges;
//    }

    /// /        // Do-no-harm on affected vertices
    /// /        Set<String> affected = affectedVertices(base, center, cand);
    /// /        Map<String, Double> pBefore = nodePMap(base, affected);
    /// /        Map<String, Double> pAfter = nodePMap(cand, affected);
    /// /
    /// /        if (!respectsDoNoHarm(pBefore, pAfter, center.getName())) {
    /// /            vlog("Rejected: violates do-no-harm on affected nodes %s.", affected);
    /// /            return false;
    /// /        }
//
//        // Actually apply to workingGraph using your normal applier (so Graph button / editor sees it)
//        vlog("Attempting guarded move: %s", edit.description());
//        boolean ok = applyCandidateInternal(edit, false, false);
//        vlog(ok ? "APPLIED successfully" : "Rejected (no change)");
//        return ok;
//    }
    private boolean tryMoveWithGuards(Graph base, Node center, ScoredCandidate sc, RepairGraphType gt) {
        if (sc == null || sc.edit() == null || sc.edit().isNoOp()) return false;

        CandidateEdit edit = sc.edit();

        int currentEdges = base.getNumEdges();
        Graph cand = buildCandidateGraph(base, edit, gt);
        if (cand == null) {
            vlog("Rejected: buildCandidateGraph returned null.");
            return false;
        }

        // Use the SAME numbers the table computed.
        int baselineViol = sc.violationsBaseline();
        int afterViol = sc.violationsAfter();
        int afterEdges = sc.edgesAfter();

        double mpBefore = sc.modelPBefore();
        double mpAfter = sc.modelPAfter();

        // Option 2: stored guard decision is authoritative.
        if (!sc.passesGuards()) {
            vlog("Rejected: fails guards (violationsBaseline=%d violationsAfter=%d edges %d->%d modelP %s->%s).",
                    baselineViol, afterViol, currentEdges, afterEdges, fmtP(mpBefore), fmtP(mpAfter));
            return false;
        }

        vlog("Attempting guarded move: %s", edit.description());
        boolean ok = applyCandidateInternal(edit, false, false);
        vlog(ok ? "APPLIED successfully" : "Rejected (no change)");
        return ok;
    }

//    private boolean tryMoveWithGuards(Graph base, Node center, CandidateEdit edit, RepairGraphType gt) {
//        if (edit == null || edit.isNoOp()) return false;
//
//        int currentEdges = base.getNumEdges();
//
//        // Build candidate graph exactly the same way as candidate evaluation does
//        Graph cand = buildCandidateGraph(base, edit, gt);
//        if (cand == null) {
//            vlog("Rejected: buildCandidateGraph returned null.");
//            return false;
//        }
//
//        // Use the same violationsBaseline notion you're using in pack
//        int baselineViol = evalViolationsOnly(base);
//        int afterViol    = evalViolationsOnly(cand);
//        int afterEdges   = cand.getNumEdges();
//
//        // NEW: model-p comparison
//        double mpBefore = evalGraphOnce(base).modelP();
//        double mpAfter  = evalGraphOnce(cand).modelP();
//
//        // Progress gate (now includes Model-P improvements when violations/edges tie)
//        if (!isProgress(baselineViol, afterViol, currentEdges, afterEdges, mpBefore, mpAfter)) {
//            vlog("Rejected: not progress (violationsBaseline=%d violationsAfter=%d edges %d->%d modelP %s->%s).",
//                    baselineViol, afterViol, currentEdges, afterEdges, fmtP(mpBefore), fmtP(mpAfter));
//            return false;
//        }
//

    /// /        // Progress gate
    /// /        if (!isProgress(baselineViol, afterViol, currentEdges, afterEdges)) {
    /// /            vlog("Rejected: not progress (violationsBaseline=%d violationsAfter=%d edges %d->%d).",
    /// /                    baselineViol, afterViol, currentEdges, afterEdges);
    /// /            return false;
    /// /        }
//
    private boolean wouldPassGuards(Graph base, Node center, ScoredCandidate sc, RepairGraphType gt) {
        if (sc == null || sc.edit() == null || sc.edit().isNoOp()) return false;

        int currentEdges = base.getNumEdges();

        Graph cand = buildCandidateGraph(base, sc.edit(), gt);
        if (cand == null) return false;

        // Use the SAME table numbers already computed:
        int baselineViol = sc.violationsBaseline();
        int afterViol = sc.violationsAfter();
        int afterEdges = sc.edgesAfter();

        double mpBefore = sc.modelPBefore();
        double mpAfter = sc.modelPAfter();

        return isProgress(baselineViol, afterViol, currentEdges, afterEdges, mpBefore, mpAfter);
    }

    private enum MoveType {
        REORIENT_SIMPLE,   // single-edge replace/orient/flip (low-risk)
        COLLIDER_FIX,      // multi-edge "Orient collider..." / "Orient away..." (higher-risk)
        REMOVE_EDGE,
        ADD_EDGE,
        OTHER
    }

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

                @Override
                public Edge getEdge() {
                    return null;
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

                    Edge rebound = rebindEdgeToGraph(g2, edgeToAdd);
                    if (rebound == null) return g2;

                    g2.addEdge(rebound);
                    return g2;
                }

                @Override
                public String key() {
                    return "ADD:" + stableEdgeKey(edgeToAdd);
                }

                @Override
                public Edge getEdge() {
                    return edgeToAdd;
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

                    Edge e = getEdgeByNames(g2, edgeToRemove);
                    if (e != null) g2.removeEdge(e);

                    return g2;
                }

                @Override
                public String key() {
                    return "REM:" + stableEdgeKey(edgeToRemove);
                }

                @Override
                public Edge getEdge() {
                    return edgeToRemove;
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

                    Edge eOld = getEdgeByNames(g2, oldEdge);
                    if (eOld != null) g2.removeEdge(eOld);

                    Edge eNew = rebindEdgeToGraph(g2, newEdge);
                    if (eNew != null) g2.addEdge(eNew);

                    return g2;
                }

                @Override
                public String key() {
                    return "REP:" + stableEdgeKey(oldEdge) + "->" + stableEdgeKey(newEdge);
                }

                @Override
                public Edge getEdge() {
                    return newEdge;
                }
            };
        }

        /**
         * Multi-edge replace: removes every old edge’s pair, then adds every new edge.
         */
        static CandidateEdit replaceEdges(String label, List<Edge> oldEdges, List<Edge> newEdges) {
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(oldEdges, "oldEdges");
            Objects.requireNonNull(newEdges, "newEdges");

            // defensively copy for stable key/description
            List<Edge> olds = List.copyOf(oldEdges);
            List<Edge> news = List.copyOf(newEdges);

            return new CandidateEdit() {
                @Override
                public String description() {
                    return label;
                }

                @Override
                public Graph applyTo(Graph g) {
                    Graph g2 = new EdgeListGraph(g);

                    // remove by *names* (node identity differs across graph copies)
                    for (Edge oe : olds) {
                        if (oe == null) continue;

                        Node a0 = oe.getNode1();
                        Node b0 = oe.getNode2();
                        if (a0 == null || b0 == null) continue;

                        String an = a0.getName();
                        String bn = b0.getName();
                        if (an == null || bn == null) continue;

                        Node a = g2.getNode(an);
                        Node b = g2.getNode(bn);
                        if (a == null || b == null) continue;

                        Edge e = g2.getEdge(a, b);
                        if (e != null) g2.removeEdge(e);
                    }

                    for (Edge ne : news) {
                        if (ne == null) continue;
                        Edge rebound = rebindEdgeToGraph(g2, ne);
                        if (rebound != null) g2.addEdge(rebound);
                    }

                    return g2;
                }

                @Override
                public String key() {
                    List<String> parts = new ArrayList<>();
                    for (Edge oe : olds) parts.add("O:" + stableEdgeKey(oe));
                    for (Edge ne : news) parts.add("N:" + stableEdgeKey(ne));
                    Collections.sort(parts);
                    return "MULTI:" + label + ":" + String.join("|", parts);
                }

                /** For legacy code paths; return first “new” edge if any. */
                @Override
                public Edge getEdge() {
                    return news.isEmpty() ? null : news.getFirst();
                }

                @Override
                public List<Edge> getEdges() {
                    return news;
                }
            };
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

        String description();

        Graph applyTo(Graph g);

        default boolean isNoOp() {
            return false;
        }

        default String key() {
            return description();
        }

        /**
         * Legacy single-edge accessor.
         */
        Edge getEdge();

        /**
         * New multi-edge accessor (defaults to singleton or empty).
         */
        default List<Edge> getEdges() {
            Edge e = getEdge();
            return (e == null) ? List.of() : List.of(e);
        }
    }

    private record ScoredCandidate(
            CandidateEdit edit,
            int violationsBaseline,
            int violationsAfter,
            double nodePAfter,
            double modelPBefore,
            double modelPAfter,
            int edgesAfter,
            boolean passesGuards
    ) {
        int delta() {
            return violationsAfter - violationsBaseline;
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

        //        private List<ScoredCandidate> rows = List.of();
        private List<ScoredCandidate> rows = new ArrayList<>();

        void set(List<ScoredCandidate> rows) {
            this.rows = (rows == null) ? new ArrayList<>() : new ArrayList<>(rows);
            sortByCanonicalOrder();
        }

        void sortByCanonicalOrder() {
            this.rows.sort(CANONICAL_TABLE_ORDER);
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
            double alpha = PREFS.getDouble(PREF_ALPHA, 0.01);

            return switch (columnIndex) {
                case COL_EDIT -> r.edit().description();
                case COL_BASE -> r.violationsBaseline();
                case COL_AFTER -> r.violationsAfter();
                case COL_DELTA -> r.delta();
                case COL_NODE_P -> r.nodePAfter();// > alpha ? 1.0 : 0.0;
                case COL_MODEL_P -> r.modelPAfter();// > alpha ? 1.0 : 0.0;
                case COL_EDGES -> r.edgesAfter();
                case COL_APPLY -> r.edit().isNoOp() ? "" : "Accept";
                default -> "";
            };
        }

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

    // ---------------------------------------------------------------------
    // Canonical table ordering (single source of truth)
    // Matches applySortAndFilter() intent:
    //   Model-P DESC (NaN last), Δ ASC, Edges ASC, Node-P DESC (NaN last), stable tiebreak
    // ---------------------------------------------------------------------

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

//    private static final Comparator<ScoredCandidate> CANONICAL_TABLE_ORDER = (a, b) -> {
//        if (a == null && b == null) return 0;
//        if (a == null) return 1;
//        if (b == null) return -1;
//
//        int c;
//
//        // Model-P DESC (NaN last)
//        c = comparePDescNaNLast(a.modelPAfter(), b.modelPAfter());
//        if (c != 0) return c;
//
//        // Δ ASC
//        c = Integer.compare(a.delta(), b.delta());
//        if (c != 0) return c;
//
//        // Edges ASC
//        c = Integer.compare(a.edgesAfter(), b.edgesAfter());
//        if (c != 0) return c;
//
//        // Node-P DESC (NaN last)
//        c = comparePDescNaNLast(a.nodePAfter(), b.nodePAfter());
//        if (c != 0) return c;
//
//        // Stable tie-breaker (prevents jitter)
//        String ka = (a.edit() == null || a.edit().key() == null) ? "" : a.edit().key();
//        String kb = (b.edit() == null || b.edit().key() == null) ? "" : b.edit().key();
//        c = ka.compareTo(kb);
//        if (c != 0) return c;
//
//        // Last-ditch stable tie-breaker
//        String da = (a.edit() == null) ? "" : a.edit().description();
//        String db = (b.edit() == null) ? "" : b.edit().description();
//        return da.compareTo(db);
//    };

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
    private record SearchPack(String centerName, int baseline, List<ScoredCandidate> scored) {

        private Graph seedDagFromAnyGraph(Graph g) {
            if (g == null) return null;

            // 1) Nodes in a stable order
            List<Node> nodes = new ArrayList<>(g.getNodes());
            nodes.sort(Comparator.comparing(Node::getName, Comparator.nullsLast(VertexCheckIndTestModel.NATURAL_NAME_COMPARATOR)));

            Map<String, Integer> idx = new HashMap<>();
            for (int i = 0; i < nodes.size(); i++) idx.put(nodes.get(i).getName(), i);

            // 2) Build a DAG that has exactly the same adjacencies (ignore endpoints)
            Graph dag = new EdgeListGraph(nodes);

            Set<String> seenPairs = new HashSet<>();
            for (Edge e : g.getEdges()) {
                Node a0 = e.getNode1();
                Node b0 = e.getNode2();
                if (a0 == null || b0 == null) continue;

                Node a = dag.getNode(a0.getName());
                Node b = dag.getNode(b0.getName());
                if (a == null || b == null || a.equals(b)) continue;

                String key = a.getName().compareTo(b.getName()) < 0 ? a.getName() + "|" + b.getName() : b.getName() + "|" + a.getName();
                if (!seenPairs.add(key)) continue;

                int ia = idx.getOrDefault(a.getName(), 0);
                int ib = idx.getOrDefault(b.getName(), 0);

                // orient forward in the order => guarantees DAG
                if (ia <= ib) dag.addEdge(new Edge(a, b, Endpoint.TAIL, Endpoint.ARROW));
                else dag.addEdge(new Edge(b, a, Endpoint.TAIL, Endpoint.ARROW));
            }

            return dag.paths().isLegalDag() ? dag : null;
        }
    }
}