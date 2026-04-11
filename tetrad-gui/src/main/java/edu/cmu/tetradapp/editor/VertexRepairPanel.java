package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.ConditioningSetType;
import edu.cmu.tetrad.search.MarkovCheck;
import edu.cmu.tetrad.search.test.CachedIndependenceQueries;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.util.NaturalSort;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TMath;
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

import static edu.cmu.tetrad.util.TMath.abs;


/**
 * Interactive panel for locally adjusting a causal graph around a selected node {@code x}
 * using feedback from the Vertex Checker.
 *
 * <h2>Candidate Enumeration</h2>
 * <p>
 * For the selected node {@code x}, the panel enumerates a conservative set of candidate
 * single-step edge edits (additions, removals, and replacements) that are consistent with
 * the chosen graph type (DAG, CPDAG, PDAG, MAG, or PAG). Each candidate is applied to a
 * copy of the current graph and scored using Markov-checker diagnostics derived from
 * conditional independence tests. For DAGs and CPDAGs, multi-edge orientation patterns
 * over the undirected edges incident to {@code x} are also enumerated, subject to a
 * combinatorial cap.
 *
 * <h2>Scoring and Table Order</h2>
 * <p>
 * Candidates are ranked by a priority chain: first by whether the edit constitutes genuine
 * progress (fewer Markov violations, or equal violations with fewer edges, or equal
 * violations and edges with a higher Model-P score); then within the passing group by
 * decreasing violation reduction, edge parsimony, local Node-P, and global Model-P. The
 * top row in the table is therefore the most conservative recommended edit for the current
 * node. Candidates that do not constitute progress are shown below, ordered by the same
 * structural criteria, for reference.
 *
 * <h2>Model Repair</h2>
 * <p>
 * The Repair button performs a two-phase automated sweep inspired by the GES algorithm.
 * <ul>
 *   <li><b>Phase 1 (Forward / Add):</b> Only addition moves are considered. The sweep
 *       repeats over all nodes until no addition move constitutes progress.</li>
 *   <li><b>Phase 2 (Backward / Remove+Reorient):</b> Only non-addition moves (removals
 *       and reorientations) are considered. The sweep repeats until convergence.</li>
 * </ul>
 * Both phases share a single inter-sweep cycle guard: if the graph returns to any
 * previously visited state across either phase, repair stops and the user is notified.
 * Because the repair is greedy and local, it is not guaranteed to find a global optimum;
 * the user is encouraged to inspect the results and consider whether alternative top moves
 * in the per-node tables might have been more appropriate.
 *
 * <h2>Knowledge Constraints</h2>
 * <p>
 * A {@link Knowledge} object may be supplied via {@link #setKnowledge(Knowledge)}. When
 * present, any candidate edit whose resulting graph violates the knowledge is silently
 * excluded from the table and will not be applied during repair.
 *
 * <h2>Graph Type Legality</h2>
 * <p>
 * On construction, the panel checks whether the supplied graph matches any recognized legal
 * graph type. If it does not, the user is given a warning.
 */
public final class VertexRepairPanel extends JPanel {

    private static final String CARD_TABLE = "table";
    private static final String CARD_NONE = "none";
    private static final DecimalFormat MODEL_P_FORMAT = new DecimalFormat("0.0000");
    private static final int DEFAULT_MODELP_TOP_K = 50;
    private static final Comparator<ScoredCandidate> CANONICAL_TABLE_ORDER = (a, b) -> {
        if (a == null && b == null) return 0;
        if (a == null) return 1;
        if (b == null) return -1;

        int c;
        ;

        // Otherwise, compare on which one has lower delta for fewer Markov violations.
        if (abs(a.delta()) >= 0 && abs(b.delta()) >= 0) {
            c = Integer.compare(a.delta(), b.delta());
            if (c != 0) return c;
        }

        double alpha1 = 0.05;
        int edges1 = a.modelPAfter() > alpha1 ? a.edgesAfter() : Integer.MAX_VALUE;
        int edges2 = b.modelPAfter() > alpha1 ? b.edgesAfter() : Integer.MAX_VALUE;

        // Otherwise, compare on which one has the fewer edges.
        c = Integer.compare(edges1, edges2);
        if (c != 0) return c;

        // Otherwise, compare on which one has the larger model P.
        c = -Double.compare(a.modelPAfter(), b.modelPAfter());
        if (c != 0) return c;

        return 0;


//        // Otherwise, compare on which one edits the smaller number of edges.
//        c = Integer.compare(editSize(a), editSize(b));
//        if (c != 0) return c;

//        // Otherwise, compare on which one has the larger node P.
//        c = -Double.compare(a.nodePAfter(), b.nodePAfter());
//        if (c != 0) return c;

//        // Otherwise, compare on which one has the fewer edges.
//        c = Integer.compare(a.edgesAfter(), b.edgesAfter());
//        if (c != 0) return c;

//        // If one passes the guards but the other doesn't, pick whichever passes the guards.
//        if (a.passesGuards() != b.passesGuards()) {
//            return a.passesGuards() ? -1 : 1;
//        }

//        return 0;


//        return stableTieBreak(a, b);
    };
    // ---- Preferences (persist α and model-P top-K) ----
    static double alpha = 0.01;
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
    private final CachedIndependenceQueries Q;
    private final VertexCheckIndTestModel model;
    private final JComboBox<Node> nodeCombo = new JComboBox<>();
    private final JSpinner seedSpinner;
    private Node x;
    private Graph workingGraph;
    private Knowledge knowledge = new Knowledge();
    private volatile SwingWorker<?, ?> activeWorker;
    private volatile JDialog watchDialog;
    private volatile boolean suppressHistory = false;
    private int repairSeed = 0;

    public VertexRepairPanel(VertexCheckEditor editor, Node x) {
        super(new BorderLayout());

        seedSpinner = new JSpinner(new SpinnerNumberModel(
                Preferences.userRoot().getInt("vertexRepairSeed", RandomUtil.getInstance().nextInt(50000)),
                0, Integer.MAX_VALUE, 1));

        Preferences.userRoot().putInt("vertexRepairSeed", ((SpinnerNumberModel) seedSpinner.getModel()).getNumber().intValue());

        this.baseModel = Objects.requireNonNull(editor.getIndTestModel(), "editor.getIndTestModel()");
        this.Q = Objects.requireNonNull(editor.getCachedQueries(), "editor.getCachedQueries()");
        this.model = editor.getIndTestModel();

        this.workingGraph = safeCopy(baseModel.getGraph());
        this.x = resolveInitialNode(this.workingGraph, x);
        this.searchButton.setText("Adjust " + this.x.getName());

        boolean graphIsLegal = initGraphTypeComboFromGraph(this.workingGraph);

        buildUI();
        wireActions();
        updateButtons();

        if (!graphIsLegal) {
            SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(
                            this,
                            "The supplied graph does not match any recognized legal graph type\n"
                                    + "(DAG, CPDAG, PDAG, MAG, or PAG).",
                            "Unrecognized Graph Type",
                            JOptionPane.WARNING_MESSAGE));
        }

        SwingUtilities.invokeLater(() -> startWatched("Searching", this::runSearchWatched, null));

        setPreferredSize(new Dimension(650, 600));
    }

    // -------------------------------------------------------------------------
    // RepairPhase: controls which move types are visible during each repair phase
    // -------------------------------------------------------------------------

    private static int finiteFirst(double a, double b) {
        boolean fa = Double.isFinite(a);
        boolean fb = Double.isFinite(b);
        if (fa == fb) return 0;
        return fa ? -1 : 1;
    }

    private static double modelDeltaValueOrNaN(ScoredCandidate s) {
        if (s == null) return Double.NaN;
        double before = s.modelPBefore();
        double after = s.modelPAfter();
        return (Double.isFinite(before) && Double.isFinite(after)) ? (after - before) : Double.NaN;
    }

    private static double modelDelta(ScoredCandidate s) {
        if (s == null) return 0.0;
        double before = s.modelPBefore();
        double after = s.modelPAfter();
        if (Double.isFinite(before) && Double.isFinite(after)) {
            return after - before;
        }
        return 0.0;
    }

    private static double modelLogOdds(ScoredCandidate s) {
        double p = s.modelPAfter();
        return Double.isFinite(p) ? alphaLogOdds(p, alpha) : 0.0;
    }

    private static double nodeLogOdds(ScoredCandidate s) {
        double p = s.nodePAfter();
        return Double.isFinite(p) ? alphaLogOdds(p, alpha) : 0.0;
    }

    private static int editSize(ScoredCandidate s) {
        try {
            if (s.edit() != null && s.edit().getEdges() != null) {
                return TMath.max(1, s.edit().getEdges().size());
            }
        } catch (Throwable ignored) {
        }
        return 1;
    }

//    private static int moveBiasScore(ScoredCandidate s) {
//        MoveType mt = moveType(s.edit());
//        double dMp = modelDelta(s);
//
//        if (Double.isFinite(dMp) && dMp > 0.0) {
//            if (mt == MoveType.REORIENT_SIMPLE) return 2;
//            if (mt == MoveType.COLLIDER_FIX) return -1;
//        } else if (!Double.isFinite(s.modelPAfter())) {
//            if (mt == MoveType.REORIENT_SIMPLE) return 1;
//            if (mt == MoveType.COLLIDER_FIX) return -1;
//        }
//
//        return 0;
//    }

    private static int stableTieBreak(ScoredCandidate a, ScoredCandidate b) {
        String ka = (a.edit() == null || a.edit().key() == null) ? "" : a.edit().key();
        String kb = (b.edit() == null || b.edit().key() == null) ? "" : b.edit().key();
        int c = ka.compareTo(kb);
        if (c != 0) return c;

        String da = (a.edit() == null || a.edit().description() == null) ? "" : a.edit().description();
        String db = (b.edit() == null || b.edit().description() == null) ? "" : b.edit().description();
        return da.compareTo(db);
    }

    private static MoveType moveType(CandidateEdit e) {
        if (e == null) return MoveType.OTHER;

        String k = safeLower(e.key());
        String d = safeLower(e.description());
        String s = (k + " " + d).trim();

        if (containsAny(s, "rem:") || containsAny(s, "remove", "delete")) return MoveType.REMOVE_EDGE;
        if (containsAny(s, "add:") || containsAny(s, "add", "insert")) return MoveType.ADD_EDGE;

        if (containsAny(s, "orient collider", "orient away from collider")) {
            return MoveType.COLLIDER_FIX;
        }

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

        z.sort(NaturalSort.naturalComparator());

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

    private static void vlog(String fmt, Object... args) {
        System.out.println("[VertexAutoRepair] " + String.format(fmt, args));
    }

    private static Endpoint endpointAt(Edge e, Node n) {
        if (e == null || n == null) return null;
        return e.getProximalEndpoint(n);
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

        Endpoint ea = e.getProximalEndpoint(a0);
        Endpoint eb = e.getProximalEndpoint(b0);
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

    private static boolean containsStructuralEdge(Graph g, Edge template) {
        if (g == null || template == null) return false;

        Edge reb = rebindEdgeToGraph(g, template);
        if (reb == null) return false;

        Edge inG = g.getEdge(reb.getNode1(), reb.getNode2());
        if (inG == null) return false;

        Endpoint a1 = inG.getProximalEndpoint(reb.getNode1());
        Endpoint b1 = inG.getProximalEndpoint(reb.getNode2());
        return a1 == reb.getProximalEndpoint(reb.getNode1()) && b1 == reb.getProximalEndpoint(reb.getNode2());
    }

    private static boolean requiresEdgePresenceCheck(CandidateEdit cand) {
        if (cand == null) return false;
        if (cand.isNoOp()) return false;

        String k = cand.key();
        if (k != null && k.startsWith("REM:")) return false;

        List<Edge> intended = cand.getEdges();
        return intended != null && !intended.isEmpty();
    }

    private static boolean allIntendedNewEdgesPresent(Graph g, CandidateEdit cand) {
        if (g == null || cand == null) return false;
        List<Edge> intended = cand.getEdges();
        if (intended == null || intended.isEmpty()) return true;
        for (Edge e : intended) {
            if (!containsStructuralEdge(g, e)) return false;
        }
        return true;
    }

    private static Graph seedDagFromAnyGraph(Graph g) {
        if (g == null) return null;

        List<Node> nodes = new ArrayList<>(g.getNodes());
        nodes.sort(Comparator.comparing(Node::getName,
                Comparator.nullsLast(NaturalSort.NATURAL_NAME_COMPARATOR)));

        if (nodes.isEmpty()) return null;

        Map<String, Integer> idx = new HashMap<>();
        for (int i = 0; i < nodes.size(); i++) {
            String name = nodes.get(i).getName();
            if (name != null) idx.put(name, i);
        }

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

    private static double alphaLogOdds(double p, double alpha) {
        if (!Double.isFinite(p)) return -50.0;
        if (!Double.isFinite(alpha) || alpha <= 0.0 || alpha >= 1.0)
            throw new IllegalArgumentException("alpha must be in (0,1)");

        final double eps = 1e-12;

        double q = TMath.min(1.0 - eps, TMath.max(eps, p));
        double a = TMath.min(1.0 - eps, TMath.max(eps, alpha));

        return (TMath.log(q) - TMath.log(1.0 - q))
                - (TMath.log(a) - TMath.log(1.0 - a));
    }

    private static boolean isProgress(int baselineViol,
                                      int afterViol,
                                      int currentEdges,
                                      int afterEdges,
                                      double mpBefore,
                                      double mpAfter) {

        if (afterViol < baselineViol) return true;

        if (afterViol == baselineViol) {
            if (afterEdges < currentEdges) return true;

            final double MIN_MP_GAIN = 0;
            return afterEdges == currentEdges
                    && Double.isFinite(mpBefore)
                    && Double.isFinite(mpAfter)
                    && (mpAfter - mpBefore) >= MIN_MP_GAIN;
        }

        return false;
    }

    private static boolean hasDirectedPath(Graph g, Node from, Node to) {
        if (g == null || from == null || to == null) return false;
        try {
            return g.paths().existsDirectedPath(from, to);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Returns a human-readable label for a phase, used in status/warning messages.
     */
    private static String phaseName(RepairPhase phase) {
        return switch (phase) {
            case ADD_ONLY -> "Phase 1 (add)";
            case NON_ADD -> "Phase 2 (remove/reorient)";
            case ALL -> "all-moves";
        };
    }

    private Node resolveInitialNode(Graph g, Node requested) {
        if (g == null) return requested;
        List<Node> nodes = new ArrayList<>(g.getNodes());
        nodes.sort(Comparator.comparing(Node::getName, Comparator.nullsLast(String::compareTo)));

        if (nodes.isEmpty()) return requested;

        if (requested == null || requested.getName() == null) return nodes.getFirst();

        Node inGraph = g.getNode(requested.getName());
        return (inGraph != null) ? inGraph : nodes.getFirst();
    }

    public Graph getGraph() {
        return workingGraph;
    }

    private void buildUI() {
        JPanel controls = new JPanel(new GridBagLayout());
        controls.setBorder(new TitledBorder("Repair Model"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);

        c.gridx = 0;
        c.gridy = 1;
        c.gridwidth = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 0;
        controls.add(new JLabel("Node:"), c);

        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        populateNodeCombo();
        controls.add(nodeCombo, c);

        c.gridx = 2;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        controls.add(graphTypeCombo, c);

        c.gridx = 0;
        c.gridy = 2;
        c.gridwidth = 1;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        controls.add(new JLabel("Seed:"), c);

        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 0.5;
        ((JSpinner.DefaultEditor) seedSpinner.getEditor()).getTextField().setColumns(6);
        controls.add(seedSpinner, c);

        c.gridx = 0;
        c.gridy = 2;
        c.gridwidth = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;

        JPanel topButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        topButtons.add(backButton);
        topButtons.add(showGraphButton);
        topButtons.add(searchButton);
        topButtons.add(repairButton);

        JPanel north = new JPanel(new BorderLayout());
        north.add(controls, BorderLayout.CENTER);
        north.add(topButtons, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);

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

        TableColumn editIndex = cm.getColumn(0);
        TableColumn baselineIndex = cm.getColumn(1);
        TableColumn afterIndex = cm.getColumn(2);
        TableColumn deltaIndex = cm.getColumn(3);
        TableColumn nodePIndex = cm.getColumn(4);
        TableColumn modelPIndex = cm.getColumn(5);
        TableColumn edgesIndex = cm.getColumn(6);
        TableColumn applyIndex = cm.getColumn(7);

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

    private void populateNodeCombo() {
        DefaultComboBoxModel<Node> m = new DefaultComboBoxModel<>();

        if (workingGraph != null) {
            List<Node> nodes = new ArrayList<>(workingGraph.getNodes());
            nodes.sort(Comparator.comparing(Node::getName, NaturalSort.NATURAL_NAME_COMPARATOR));
            for (Node n : nodes) m.addElement(n);
        }

        nodeCombo.setModel(m);

        if (x != null && x.getName() != null && workingGraph != null) {
            Node inGraph = workingGraph.getNode(x.getName());
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
    }

    private void applySortAndFilter() {
        resultsModel.sortByCanonicalOrder();
    }

    private void wireActions() {
        backButton.addActionListener(e -> goBack());
        showGraphButton.addActionListener(e -> showGraphDialog());
        searchButton.addActionListener(e -> startWatched("Searching", this::runSearchWatched, null));

        nodeCombo.addActionListener(e -> {
            Object sel = nodeCombo.getSelectedItem();
            if (!(sel instanceof Node n)) return;

            Node inGraph = (workingGraph != null && n.getName() != null)
                    ? workingGraph.getNode(n.getName())
                    : null;

            if (inGraph == null) {
                x = resolveInitialNode(workingGraph, null);
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
            repairSeed = (Integer) seedSpinner.getValue();
            Preferences.userRoot().putInt("vertexRepairSeed", repairSeed);
        });
    }

    private void updateButtons() {
        backButton.setEnabled(!history.isEmpty());
        boolean busy = (activeWorker != null);
        searchButton.setEnabled(!busy);
        repairButton.setEnabled(!busy);
    }

    // -------------------------------------------------------------------------
    // Search logic
    // -------------------------------------------------------------------------

    private void runSearchWatched() {
        AdjustmentGraphType gt = (AdjustmentGraphType) graphTypeCombo.getSelectedItem();

        Graph base = prepareBase(gt);
        if (base == null) {
            SwingUtilities.invokeLater(() -> {
                statusLabel.setText(gt == AdjustmentGraphType.CPDAG
                        ? "Could not canonicalize to CPDAG (unexpected)."
                        : "Current graph has no consistent PAG extension.");
                ((CardLayout) resultsCard.getLayout()).show(resultsCard, CARD_NONE);
            });
            return;
        }

        if (stopRequested()) return;

        if (knowledge != null && knowledge.isViolatedBy(base)) {
            SwingUtilities.invokeLater(() -> {
                statusLabel.setText("Current graph violates the knowledge base.");
                ((CardLayout) resultsCard.getLayout()).show(resultsCard, CARD_NONE);
            });
            return;
        }

        // Interactive search always uses the full move set
        List<CandidateEdit> candidates = new ArrayList<>(enumerateCandidates(base, x, gt, RepairPhase.ALL));
        if (candidates.stream().noneMatch(CandidateEdit::isNoOp)) {
            candidates.addFirst(CandidateEdit.noOp());
        }

        if (stopRequested()) return;

        List<ScoredCandidate> scored = scoreCandidates(base, x, gt, candidates);

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
                        "Baseline violations: " + baseline +
                                " | Best: " + bestViol +
                                " | Model-P: " + fmt.format(mpBefore) + " \u2192 " + mpBestStr
                );
                ((CardLayout) resultsCard.getLayout()).show(resultsCard, CARD_TABLE);
            }
        });
    }

    private Graph prepareBase(AdjustmentGraphType gt) {
        Graph base = safeCopy(workingGraph);

        if (gt == AdjustmentGraphType.CPDAG) {
            base = canonicalizeToCpdagOrNull(base);
        } else if (gt == AdjustmentGraphType.PAG) {
            base = canonicalizeToPagOrNull(base);
        }

        return base;
    }

    private List<ScoredCandidate> computeScoredCandidatesForNode(Graph base, Node node,
                                                                 AdjustmentGraphType gt,
                                                                 RepairPhase phase) {
        List<CandidateEdit> candidates = new ArrayList<>(enumerateCandidates(base, node, gt, phase));
        if (candidates.stream().noneMatch(CandidateEdit::isNoOp)) {
            candidates.addFirst(CandidateEdit.noOp());
        }
        return scoreCandidates(base, node, gt, candidates);
    }

    // -------------------------------------------------------------------------
    // Two-phase GES-style repair
    // -------------------------------------------------------------------------

    /**
     * Runs the two-phase repair sweep.
     *
     * <p><b>Phase 1 (Forward/Add):</b> sweeps all nodes considering only addition moves
     * until no node can make progress with an add.
     *
     * <p><b>Phase 2 (Backward/Non-Add):</b> sweeps all nodes considering only removal and
     * reorientation moves until no node can make progress.
     *
     * <p>Both phases share one {@code seenSweepStates} set. If the graph returns to any
     * state seen in either phase, repair halts with a cycle warning.
     */
    private void runRepairWatched() {
        AdjustmentGraphType gt = (AdjustmentGraphType) graphTypeCombo.getSelectedItem();
        List<String> cycleWarnings = new ArrayList<>();
        Set<String> seenSweepStates = new LinkedHashSet<>();

        repairSeed = (Integer) seedSpinner.getValue();
        long previousSeed = RandomUtil.getInstance().nextLong();
        RandomUtil.getInstance().setSeed(repairSeed);

        history.push(safeCopy(workingGraph));
        suppressHistory = true;

        try {
//            // ---- Phase 1: add-only forward sweep --------------------------------
//            SwingUtilities.invokeLater(() -> statusLabel.setText("Phase 1 (add edges)..."));
//            runRepairPhase(gt, RepairPhase.ADD_ONLY, seenSweepStates, cycleWarnings);
//            if (stopRequested()) return;
//
//            // ---- Phase 2: remove/reorient backward sweep ------------------------
//            SwingUtilities.invokeLater(() -> statusLabel.setText("Phase 2 (remove/reorient)..."));
//            runRepairPhase(gt, RepairPhase.NON_ADD, seenSweepStates, cycleWarnings);

            SwingUtilities.invokeLater(() -> statusLabel.setText("..."));
            runRepairPhase(gt, RepairPhase.ALL, seenSweepStates, cycleWarnings);


        } finally {
            suppressHistory = false;
            RandomUtil.getInstance().setSeed(previousSeed);
            baseModel.setGraph(workingGraph);
            SwingUtilities.invokeLater(() -> {
                populateNodeCombo();
                statusLabel.setText("Repair complete. (seed=" + repairSeed + ")");
            });
        }

//        if (!cycleWarnings.isEmpty()) {
//            String message = "Repair completed, but cycles were detected and skipped:\n\n"
//                    + String.join("\n", cycleWarnings)
//                    + "\n\nThese nodes or states may need manual review.";
//            SwingUtilities.invokeLater(() ->
//                    JOptionPane.showMessageDialog(
//                            VertexRepairPanel.this,
//                            message,
//                            "Cycle Detected During Repair",
//                            JOptionPane.WARNING_MESSAGE));
//        }
    }

    /**
     * Executes one phase of the repair sweep (forward or backward).
     *
     * <p>Sweeps all nodes repeatedly until a full pass produces no change. Uses the
     * shared {@code seenSweepStates} set so cycles that span phases are detected.
     *
     * @param gt              graph type (controls legality and canonicalization)
     * @param phase           which move types are permitted this phase
     * @param seenSweepStates shared set of graph-state fingerprints (mutated in place)
     * @param cycleWarnings   list to append human-readable cycle descriptions to
     */
    private void runRepairPhase(AdjustmentGraphType gt,
                                RepairPhase phase,
                                Set<String> seenSweepStates,
                                List<String> cycleWarnings) {
        boolean anyChangeInSweep;

        do {
            if (stopRequested()) return;
            cycleWarnings.clear();

            anyChangeInSweep = false;

//            List<Node> nodes = new ArrayList<>(workingGraph.getNodes());
//            nodes.sort(Comparator.comparing(Node::getName, NaturalSort.NATURAL_NAME_COMPARATOR));

            List<Node> nodes = new ArrayList<>(workingGraph.getNodes());
            nodes.sort(Comparator.comparing(Node::getName, NaturalSort.NATURAL_NAME_COMPARATOR));
            RandomUtil.shuffle(nodes);

            for (Node node : nodes) {
                if (stopRequested()) return;

                Node current = workingGraph.getNode(node.getName());
                if (current == null) continue;

                Set<String> attemptedKeys = new LinkedHashSet<>();

                while (true) {
                    if (stopRequested()) return;

                    Graph base = prepareBase(gt);
                    if (base == null) {
                        SwingUtilities.invokeLater(() ->
                                statusLabel.setText("Canonicalization failed during repair."));
                        return;
                    }

                    List<ScoredCandidate> candidates =
                            computeScoredCandidatesForNode(base, current, gt, phase);
                    if (candidates.isEmpty()) break;

                    ScoredCandidate top = candidates.getFirst();
                    if (top.edit().isNoOp() || !top.passesGuards()) break;

                    String key = top.edit().key();
                    if (!attemptedKeys.add(key)) {
                        cycleWarnings.add(current.getName()
                                + " (" + phaseName(phase) + "): \""
                                + top.edit().description() + "\"");
                        break;
                    }

                    Graph before = safeCopy(workingGraph);
                    applyCandidateInternal(top.edit());

                    if (workingGraph.equals(before)) break;

                    anyChangeInSweep = true;

                    Node refreshed = workingGraph.getNode(current.getName());
                    if (refreshed == null) break;
                    current = refreshed;
                }
            }

            // Only check for cycles when the graph actually changed —
            // convergence (no change) is normal termination, not a cycle.
            if (anyChangeInSweep) {
                String state = workingGraph.toString();
                if (!seenSweepStates.add(state)) {
                    cycleWarnings.add("Inter-sweep cycle detected during "
                            + phaseName(phase)
                            + ": the graph returned to a previously visited state. Stopping this phase.");
                    return;
                }
            }

        } while (anyChangeInSweep);
    }

    private List<ScoredCandidate> scoreCandidates(Graph base, Node node,
                                                  AdjustmentGraphType gt,
                                                  List<CandidateEdit> candidates) {
        GlobalEvalCache baseCache = buildBaselineCache(base);
        int baseline = evalGraphLocality(baseCache, base, Set.of()).violations();
        double mpBefore = evalGraphLocality(baseCache, base, Set.of()).modelP();

        Map<String, Graph> candGraphByKey = new HashMap<>();
        List<ScoredCandidate> scored = new ArrayList<>();

        for (CandidateEdit cand : candidates) {
            if (stopRequested()) return List.of();

            Graph g2 = candGraphByKey.computeIfAbsent(cand.key(),
                    k -> buildCandidateGraph(base, cand, gt));
            if (g2 == null) continue;
            if (knowledge != null && knowledge.isViolatedBy(g2)) continue;

            boolean useLocality = (gt == AdjustmentGraphType.DAG
                    || gt == AdjustmentGraphType.CPDAG
                    || gt == AdjustmentGraphType.PDAG);

            Set<String> affected = affectedVertices(base, node, g2);
            int after = useLocality
                    ? evalGraphLocality(baseCache, g2, affected).violations()
                    : evalViolationsOnly(g2);

            scored.add(new ScoredCandidate(cand, baseline, after,
                    nodePValue(g2, node), Double.NaN, Double.NaN,
                    g2.getNumEdges(), true));
        }

        if (stopRequested()) return List.of();

        List<ScoredCandidate> ranked = new ArrayList<>(scored);
        RandomUtil.shuffle(ranked);
        ranked.sort(CANONICAL_TABLE_ORDER);

        LinkedHashSet<String> keysToEval = new LinkedHashSet<>();
        for (int i = 0; i < TMath.min(DEFAULT_MODELP_TOP_K, ranked.size()); i++) {
            ScoredCandidate sc = ranked.get(i);
            if (sc != null && sc.edit() != null) keysToEval.add(sc.edit().key());
        }
        for (ScoredCandidate sc : scored) {
            if (sc != null && sc.edit() != null
                    && moveType(sc.edit()) == MoveType.REORIENT_SIMPLE) {
                keysToEval.add(sc.edit().key());
            }
        }

        Map<String, Double> mpAfterByKey = new HashMap<>();
        for (String key : keysToEval) {
            if (stopRequested()) return List.of();
            Graph g2 = candGraphByKey.get(key);

            if (g2 != null) {
                Set<String> affected = affectedVertices(base, node, g2);
                mpAfterByKey.put(key, evalModelPLocality(baseCache, g2, affected));
            }
        }

        List<ScoredCandidate> result = new ArrayList<>(scored.size());
        for (ScoredCandidate sc : scored) {
            Double mpAfter = mpAfterByKey.get(sc.edit().key());
            ScoredCandidate patched = new ScoredCandidate(
                    sc.edit(), sc.violationsBaseline(), sc.violationsAfter(),
                    sc.nodePAfter(), mpBefore,
                    (mpAfter == null ? Double.NaN : mpAfter),
                    sc.edgesAfter(), true);

            result.add(new ScoredCandidate(
                    patched.edit(), patched.violationsBaseline(), patched.violationsAfter(),
                    patched.nodePAfter(), patched.modelPBefore(), patched.modelPAfter(),
                    patched.edgesAfter(), wouldPassGuards(base, patched)));
        }

        result.sort(CANONICAL_TABLE_ORDER);
        return result;
    }

    // -------------------------------------------------------------------------
    // Scoring
    // -------------------------------------------------------------------------

    private void applyCandidate(CandidateEdit cand) {
        applyCandidateInternal(cand);
        updateButtons();
    }

    private void applyCandidateInternal(CandidateEdit cand) {
        if (cand == null) return;
        if (cand.isNoOp()) return;

        vlog("Attempting move: %s", cand.description());

        if (!suppressHistory) history.push(safeCopy(workingGraph));

        AdjustmentGraphType gt = (AdjustmentGraphType) graphTypeCombo.getSelectedItem();

        Graph base = safeCopy(workingGraph);

        if (gt == AdjustmentGraphType.CPDAG) {
            base = canonicalizeToCpdagOrNull(base);
            if (base == null) {
                statusLabel.setText("Current graph has no consistent CPDAG extension.");
                if (!suppressHistory && !history.isEmpty()) history.pop();
                return;
            }
        } else if (gt == AdjustmentGraphType.PAG) {
            base = canonicalizeToPagOrNull(base);
            if (base == null) {
                statusLabel.setText("Current graph has no consistent PAG extension.");
                if (!suppressHistory && !history.isEmpty()) history.pop();
                return;
            }
        }

        Graph g2 = cand.applyTo(base);
        if (g2 == null) {
            statusLabel.setText("Failed to apply: " + cand.description());
            if (!suppressHistory && !history.isEmpty()) history.pop();
            return;
        }

        if (gt == AdjustmentGraphType.CPDAG) {
            g2 = canonicalizeToCpdagOrNull(g2);
            if (g2 == null) {
                statusLabel.setText("Failed to apply (CPDAG canonicalization): " + cand.description());
                if (!suppressHistory && !history.isEmpty()) history.pop();
                return;
            }
        }

        if (g2.equals(base)) {
            vlog("REJECTED (no graph change after canonicalization)");
            statusLabel.setText("No-op after canonicalization: " + cand.description());
            if (!suppressHistory && !history.isEmpty()) history.pop();
            return;
        }

        if (requiresEdgePresenceCheck(cand) && !allIntendedNewEdgesPresent(g2, cand)) {
            vlog("REJECTED (intended new edge(s) not present after apply/canonicalization)");
            statusLabel.setText("Skipped (edge vanished): " + cand.description());
            if (!suppressHistory && !history.isEmpty()) history.pop();
            return;
        }

        workingGraph = g2;

        // Write the repaired graph back to the model so it persists across panel open/close
        // During repair (suppressHistory=true), we batch this to avoid hammering the calling panel
        if (!suppressHistory) {
            baseModel.setGraph(workingGraph);
        }

        if (x != null && x.getName() != null) {
            Node inGraph = workingGraph.getNode(x.getName());
            if (inGraph != null) x = inGraph;
            else x = resolveInitialNode(workingGraph, null);
            if (!suppressHistory) SwingUtilities.invokeLater(this::populateNodeCombo);
        }

        vlog("APPLIED successfully");
//        if (!suppressHistory)
        SwingUtilities.invokeLater(() -> statusLabel.setText("Applied: " + cand.description()));
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

    /**
     * Enumerates candidate edits for node {@code x} in graph {@code g}, filtered to
     * the move types permitted by {@code phase}.
     *
     * <ul>
     *   <li>{@link RepairPhase#ADD_ONLY} – only {@code ADD:} moves (plus NO_OP).</li>
     *   <li>{@link RepairPhase#NON_ADD}  – everything except {@code ADD:} moves.</li>
     *   <li>{@link RepairPhase#ALL}      – the full move set (interactive default).</li>
     * </ul>
     */
    private List<CandidateEdit> enumerateCandidates(Graph g, Node x,
                                                    AdjustmentGraphType gt,
                                                    RepairPhase phase) {
        if (g == null || x == null) return List.of(CandidateEdit.noOp());

        List<CandidateEdit> out = new ArrayList<>();
        out.add(CandidateEdit.noOp());

//        Set<Node> pool = new LinkedHashSet<>(g.getNodes());
//        pool.remove(x);
//
        List<Node> pool = new ArrayList<>(g.getNodes());
        pool.remove(x);
        pool.sort(Comparator.comparing(Node::getName, NaturalSort.NATURAL_NAME_COMPARATOR));
        RandomUtil.shuffle(pool);
//        poo
//        pool.sort(Comparator.comparing(Node::getName, NaturalSort.NATURAL_NAME_COMPARATOR));

        // ---- Remove existing edge incident to x ----
        ArrayList<Edge> edges = new ArrayList<>(g.getEdges(x));
        Collections.sort(edges);

        if (phase != RepairPhase.ADD_ONLY) {
            for (Edge e : edges) {
                out.add(CandidateEdit.removeEdge(e));
            }
        }

        // ---- Replace existing edge x—y with type-specific orientation variants ----
        if (phase != RepairPhase.ADD_ONLY) {
            for (Edge e : edges) {
                Node y = e.getDistalNode(x);
                if (y == null) continue;

                for (Edge v : edgeMenuForPair(x, y, gt)) {
                    if (edgeStructurallyEqual(e, v, x, y)) continue;
                    out.add(CandidateEdit.replaceEdge(e, v));
                }
            }
        }

        // ---- Add edges x—y for non-adjacent y ----
        if (phase != RepairPhase.NON_ADD) {
            for (Node y : pool) {
                if (y == null) continue;
                if (g.isAdjacentTo(x, y)) continue;

                for (Edge add : addMenuForPair(x, y, gt)) {
                    out.add(CandidateEdit.addEdge(add));
                }
            }
        }

        // ---- Multi-edge incident orientation patterns ----
        if (phase != RepairPhase.ADD_ONLY
                && (gt == AdjustmentGraphType.DAG
                || gt == AdjustmentGraphType.CPDAG
                || gt == AdjustmentGraphType.PDAG)) {
            out.addAll(enumerateIncidentOrientationPatternMoves(g, x, gt));
        }

//        // ---- CPDAG-only: 2-edge collider fixes ----
//        if (phase != RepairPhase.ADD_ONLY && gt == AdjustmentGraphType.CPDAG) {
//            out.addAll(enumerateCpdagColliderPairMoves(g, x));
//        }

        return dedupCandidateEdits(out);
    }

    // -------------------------------------------------------------------------
    // Candidate enumeration
    // -------------------------------------------------------------------------
//
//    private List<CandidateEdit> enumerateIncidentOrientationPatternMoves(Graph g, Node x, AdjustmentGraphType gt) {
//        if (g == null || x == null) return List.of();
//
//        List<Node> adj = new ArrayList<>(g.getAdjacentNodes(x));
//        adj.sort(Comparator.comparing(Node::getName, Comparator.nullsLast(String::compareTo)));
//
//        List<Edge> freeEdges = new ArrayList<>();
//
//        for (Node y : adj) {
//            if (y == null) continue;
//            Edge e = g.getEdge(x, y);
//            if (e == null) continue;
//
//            Endpoint ex = endpointAt(e, x);
//            Endpoint ey = endpointAt(e, y);
//
//            if (gt == AdjustmentGraphType.DAG) {
//                if (ex == Endpoint.TAIL && ey == Endpoint.ARROW) continue;
//                if (ex == Endpoint.ARROW && ey == Endpoint.TAIL) continue;
//                freeEdges.add(e);
//                continue;
//            }
//
//            if (ex == Endpoint.TAIL && ey == Endpoint.TAIL) {
//                freeEdges.add(e);
//            } else if ((ex == Endpoint.TAIL && ey == Endpoint.ARROW) || (ex == Endpoint.ARROW && ey == Endpoint.TAIL)) {
//                // directed — compelled, skip
//            }
//        }
//
//        if (freeEdges.isEmpty()) return List.of();
//
//        final int MAX_FREE = 12;
//        final int MAX_PARENTS = 6;
//        final int MAX_MOVES = 5000;
//
//        if (freeEdges.size() > MAX_FREE) return List.of();
//
//        List<CandidateEdit> out = new ArrayList<>();
//
//        int m = freeEdges.size();
//        int total = 1 << m;
//        String xName = (x.getName() == null) ? "?" : x.getName();
//
//        for (int mask = 0; mask < total; mask++) {
//            if (out.size() >= MAX_MOVES) break;
//            if (Integer.bitCount(mask) > MAX_PARENTS) continue;
//
//            List<Edge> olds = new ArrayList<>(m);
//            List<Edge> news = new ArrayList<>(m);
//            List<String> parents = new ArrayList<>();
//            List<String> children = new ArrayList<>();
//            boolean earlyReject = false;
//
//            for (int i = 0; i < m; i++) {
//                Edge old = freeEdges.get(i);
//                if (old == null) continue;
//
//                Node y = old.getDistalNode(x);
//                if (y == null) continue;
//
//                olds.add(old);
//
//                boolean intoX = ((mask & (1 << i)) != 0);
//
//                Edge ne = intoX
//                        ? new Edge(y, x, Endpoint.TAIL, Endpoint.ARROW)
//                        : new Edge(x, y, Endpoint.TAIL, Endpoint.ARROW);
//
//                if (gt == AdjustmentGraphType.DAG) {
//                    if (intoX && hasDirectedPath(g, x, y)) {
//                        earlyReject = true;
//                        break;
//                    }
//                    if (!intoX && hasDirectedPath(g, y, x)) {
//                        earlyReject = true;
//                        break;
//                    }
//                }
//
//                news.add(ne);
//
//                String yn = (y.getName() == null) ? "?" : y.getName();
//                if (intoX) parents.add(yn);
//                else children.add(yn);
//            }
//
//            if (earlyReject) continue;
//            if (news.isEmpty()) continue;
//

    /// /            RandomUtil.shuffle(parents);
    /// /            RandomUtil.shuffle(children);
//
//            parents.sort(NaturalSort.naturalComparator());
//            children.sort(NaturalSort.naturalComparator());
//
//            String label =
//                    "Orient incident edges at " + xName +
//                            " | Pa={" + String.join(",", parents) + "}" +
//                            " | Ch={" + String.join(",", children) + "}";
//
//            out.add(CandidateEdit.replaceEdges(label, olds, news));
//        }
//
//        return out;
//    }
    private List<CandidateEdit> enumerateIncidentOrientationPatternMoves(Graph g, Node x, AdjustmentGraphType gt) {
        if (g == null || x == null) return List.of();

        List<Node> adj = new ArrayList<>(g.getAdjacentNodes(x));
        adj.sort(Comparator.comparing(Node::getName, Comparator.nullsLast(String::compareTo)));

        List<Edge> freeEdges = new ArrayList<>();

        for (Node y : adj) {
            if (y == null) continue;
            Edge e = g.getEdge(x, y);
            if (e == null) continue;

            Endpoint ex = endpointAt(e, x);

            switch (gt) {
                case DAG, CPDAG, PDAG -> {
                    // Only undirected (tail-tail) edges are orientable
                    if (ex == Endpoint.TAIL && endpointAt(e, y) == Endpoint.TAIL)
                        freeEdges.add(e);
                }
                case PAG -> {
                    // Circle endpoint at x means orientable
                    if (ex == Endpoint.CIRCLE)
                        freeEdges.add(e);
                }
                case MAG -> {
                    // Undirected edges only
                    if (ex == Endpoint.TAIL && endpointAt(e, y) == Endpoint.TAIL)
                        freeEdges.add(e);
                }
            }
        }

        if (freeEdges.isEmpty()) return List.of();

        final int MAX_FREE = 12;
        final int MAX_MOVES = 5000;

        if (freeEdges.size() > MAX_FREE) return List.of();

        List<CandidateEdit> out = new ArrayList<>();

        int m = freeEdges.size();
        int total = 1 << m;
        String xName = (x.getName() == null) ? "?" : x.getName();

        for (int mask = 0; mask < total; mask++) {
            if (out.size() >= MAX_MOVES) break;

            List<Edge> olds = new ArrayList<>(m);
            List<Edge> news = new ArrayList<>(m);
            List<String> parents = new ArrayList<>();
            List<String> children = new ArrayList<>();

            for (int i = 0; i < m; i++) {
                Edge old = freeEdges.get(i);
                if (old == null) continue;

                Node y = old.getDistalNode(x);
                if (y == null) continue;

                olds.add(old);

                boolean intoX = ((mask & (1 << i)) != 0);
                String yn = (y.getName() == null) ? "?" : y.getName();

                Edge ne;
                if (gt == AdjustmentGraphType.PAG) {
                    Endpoint eyKeep = endpointAt(old, y);
                    ne = intoX
                            ? new Edge(y, x, eyKeep, Endpoint.ARROW)
                            : new Edge(y, x, eyKeep, Endpoint.TAIL);
                } else {
                    ne = intoX
                            ? new Edge(y, x, Endpoint.TAIL, Endpoint.ARROW)
                            : new Edge(x, y, Endpoint.TAIL, Endpoint.ARROW);
                }

                news.add(ne);

                if (intoX) parents.add(yn);
                else children.add(yn);
            }

            if (news.isEmpty()) continue;

            parents.sort(NaturalSort.naturalComparator());
            children.sort(NaturalSort.naturalComparator());

            String label =
                    "Orient incident edges at " + xName +
                            " | Pa={" + String.join(",", parents) + "}" +
                            " | Ch={" + String.join(",", children) + "}";

            out.add(CandidateEdit.replaceEdges(label, olds, news));
        }

        return out;
    }

    private List<CandidateEdit> enumerateCpdagColliderPairMoves(Graph g, Node x) {
        if (g == null || x == null) return List.of();

        List<CandidateEdit> out = new ArrayList<>();
        List<Node> adj = new ArrayList<>(g.getAdjacentNodes(x));
        adj.sort(Comparator.comparing(Node::getName, Comparator.nullsLast(String::compareTo)));

        for (int i = 0; i < adj.size(); i++) {
            Node y = adj.get(i);
            if (y == null) continue;

            Edge exy = g.getEdge(x, y);
            if (exy == null) continue;

            for (int j = i + 1; j < adj.size(); j++) {
                Node z = adj.get(j);
                if (z == null) continue;
                if (g.isAdjacentTo(y, z)) continue;

                Edge exz = g.getEdge(x, z);
                if (exz == null) continue;

                Endpoint endXy = endpointAt(exy, x);
                Endpoint endXz = endpointAt(exz, x);

                if (!(endXy == Endpoint.ARROW && endXz == Endpoint.ARROW)) {
                    Edge yToX = new Edge(y, x, Endpoint.TAIL, Endpoint.ARROW);
                    Edge zToX = new Edge(z, x, Endpoint.TAIL, Endpoint.ARROW);
                    String label = "Orient collider " + y.getName() + "->" + x.getName() + "<-" + z.getName();
                    out.add(CandidateEdit.replaceEdges(label, List.of(exy, exz), List.of(yToX, zToX)));
                }

                if (endXy == Endpoint.ARROW && endXz == Endpoint.ARROW) {
                    Edge xToY = new Edge(x, y, Endpoint.TAIL, Endpoint.ARROW);
                    Edge xToZ = new Edge(x, z, Endpoint.TAIL, Endpoint.ARROW);
                    String label = "Orient away from collider " + y.getName() + "<-" + x.getName() + "->" + z.getName();
                    out.add(CandidateEdit.replaceEdges(label, List.of(exy, exz), List.of(xToY, xToZ)));
                }
            }
        }

        return out;
    }

    private List<Edge> edgeMenuForPair(Node x, Node y, AdjustmentGraphType gt) {
        List<Edge> variants = new ArrayList<>();

        switch (gt) {
            case DAG -> {
                variants.add(new Edge(x, y, Endpoint.TAIL, Endpoint.ARROW));
                variants.add(new Edge(y, x, Endpoint.TAIL, Endpoint.ARROW));
            }
            case CPDAG, PDAG -> {
                variants.add(new Edge(x, y, Endpoint.TAIL, Endpoint.TAIL));
                variants.add(new Edge(x, y, Endpoint.TAIL, Endpoint.ARROW));
                variants.add(new Edge(y, x, Endpoint.TAIL, Endpoint.ARROW));
            }
            case MAG -> {
                variants.add(new Edge(x, y, Endpoint.TAIL, Endpoint.ARROW));
                variants.add(new Edge(y, x, Endpoint.TAIL, Endpoint.ARROW));
                variants.add(new Edge(x, y, Endpoint.ARROW, Endpoint.ARROW));
            }
            case PAG -> {
                variants.add(new Edge(x, y, Endpoint.CIRCLE, Endpoint.CIRCLE));
                variants.add(new Edge(x, y, Endpoint.CIRCLE, Endpoint.ARROW));
                variants.add(new Edge(y, x, Endpoint.CIRCLE, Endpoint.ARROW));
                variants.add(new Edge(x, y, Endpoint.TAIL, Endpoint.ARROW));
                variants.add(new Edge(y, x, Endpoint.TAIL, Endpoint.ARROW));
                variants.add(new Edge(x, y, Endpoint.ARROW, Endpoint.ARROW));
                variants.add(new Edge(x, y, Endpoint.TAIL, Endpoint.TAIL));
            }
        }

        return variants;
    }

    private List<Edge> addMenuForPair(Node x, Node y, AdjustmentGraphType gt) {
        List<Edge> adds = new ArrayList<>();

        switch (gt) {
            case DAG -> {
                adds.add(new Edge(x, y, Endpoint.TAIL, Endpoint.ARROW));
                adds.add(new Edge(y, x, Endpoint.TAIL, Endpoint.ARROW));
            }
            case CPDAG -> {
                adds.add(new Edge(x, y, Endpoint.TAIL, Endpoint.TAIL));
                adds.add(new Edge(x, y, Endpoint.TAIL, Endpoint.ARROW));
                adds.add(new Edge(y, x, Endpoint.TAIL, Endpoint.ARROW));
            }
            case PDAG -> {
                adds.add(new Edge(x, y, Endpoint.TAIL, Endpoint.TAIL));
                adds.add(new Edge(x, y, Endpoint.TAIL, Endpoint.ARROW));
                adds.add(new Edge(y, x, Endpoint.TAIL, Endpoint.ARROW));
            }
            case MAG -> {
                adds.add(new Edge(x, y, Endpoint.TAIL, Endpoint.ARROW));
                adds.add(new Edge(y, x, Endpoint.TAIL, Endpoint.ARROW));
                adds.add(new Edge(x, y, Endpoint.ARROW, Endpoint.ARROW));
            }
            case PAG -> {
                adds.add(new Edge(x, y, Endpoint.CIRCLE, Endpoint.CIRCLE));
                adds.add(new Edge(x, y, Endpoint.CIRCLE, Endpoint.ARROW));
                adds.add(new Edge(y, x, Endpoint.CIRCLE, Endpoint.ARROW));
                adds.add(new Edge(x, y, Endpoint.TAIL, Endpoint.ARROW));
                adds.add(new Edge(y, x, Endpoint.TAIL, Endpoint.ARROW));
                adds.add(new Edge(x, y, Endpoint.ARROW, Endpoint.ARROW));
                adds.add(new Edge(x, y, Endpoint.TAIL, Endpoint.TAIL));
            }
        }

        return adds;
    }

    private Graph canonicalizeToCpdagOrNull(Graph h) {
        if (h == null) return null;

        try {
            Graph h2 = new EdgeListGraph(h);

            if (h2.paths().isLegalDag()) {
                return GraphTransforms.dagToCpdag(h2);
            }

            if (h2.paths().isLegalCpdag() || h2.paths().isLegalPdag()) {
                Graph dag = GraphTransforms.dagFromCpdag(h2);
                return GraphTransforms.dagToCpdag(dag);
            }

            Graph seed = seedDagFromAnyGraph(h2);
            if (seed == null) return null;
            return GraphTransforms.dagToCpdag(seed);

        } catch (Throwable t) {
            return null;
        }
    }

    private Graph canonicalizeToPagOrNull(Graph h) {
        try {
            Graph h2 = new EdgeListGraph(h);
            Graph mag = GraphTransforms.zhangMagFromPag(h2);

            if (!mag.paths().isLegalMag()) return null;

            return GraphTransforms.magToPag(mag, false);
        } catch (Throwable t) {
            return null;
        }
    }

    private boolean isLegalGraphType(Graph g, AdjustmentGraphType gt) {
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

    private boolean initGraphTypeComboFromGraph(Graph g) {
        List<AdjustmentGraphType> plausible = new ArrayList<>();
        for (AdjustmentGraphType gt : AdjustmentGraphType.values()) {
            try {
                if (g != null && isLegalGraphType(g, gt)) plausible.add(gt);
            } catch (Exception ignored) {
            }
        }

        boolean legal = !plausible.isEmpty();
        if (!legal) plausible = Arrays.asList(AdjustmentGraphType.values());

        graphTypeCombo.setModel(new DefaultComboBoxModel<>(
                plausible.toArray(new AdjustmentGraphType[0])));
        graphTypeCombo.setSelectedIndex(0);

        return legal;
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
        List<Node> nodes = g.getNodes();
        nodes.sort(NaturalSort.naturalComparator());

        for (Node v : nodes) {
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

        Map<String, Boolean> viol = new LinkedHashMap<>();
        Map<String, Double> pByKey = new LinkedHashMap<>();

        for (IndependenceFact f : facts) {
            if (f == null) continue;
            String key = factKey(f);
            if (viol.containsKey(key)) continue;

            IndependenceResult r = check(f);
            if (r == null) continue;

            boolean isViolation = !r.isIndependent();
            viol.put(key, isViolation);

            double p = r.getPValue();
            if (!Double.isNaN(p) && p >= 0.0 && p <= 1.0) pByKey.put(key, p);
        }

        return new VertexContribution(viol, pByKey);
    }

    private double evalModelPLocality(GlobalEvalCache baseCache,
                                      Graph candidateGraph,
                                      Set<String> affectedVertexNames) {
        return evalGraphLocality(baseCache, candidateGraph, affectedVertexNames).modelP();
    }

    private GraphEval evalGraphLocality(GlobalEvalCache baseCache,
                                        Graph candidateGraph,
                                        Set<String> affectedVertexNames) {
        if (candidateGraph == null) return new GraphEval(0, Double.NaN, 0);

        Map<String, VertexContribution> contrib = new HashMap<>();
        if (baseCache != null && baseCache.contribByVertexName() != null) {
            contrib.putAll(baseCache.contribByVertexName());
        }

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

        Map<String, Boolean> globalViolationByKey = new HashMap<>();
        Map<String, Double> globalPByKey = new HashMap<>();

//        List<String> names = new ArrayList<>(contrib.keySet());
//        names.sort(NaturalSort.naturalComparator());
//
//        for (String name : names) {
//            VertexContribution vc = contrib.get(name);
//            if (vc == null) continue;
//
//            for (Map.Entry<String, Boolean> e : vc.violationByKey().entrySet()) {
//                String key = e.getKey();
//                if (key == null) continue;
//                globalViolationByKey.putIfAbsent(key, e.getValue());
//            }
//
//            for (Map.Entry<String, Double> e : vc.pByKey().entrySet()) {
//                String key = e.getKey();
//                if (key == null) continue;
//                globalPByKey.putIfAbsent(key, e.getValue());
//            }
//        }

        // Already sorted:
        List<String> names = new ArrayList<>(contrib.keySet());
        names.sort(NaturalSort.naturalComparator());

        for (String name : names) {
            VertexContribution vc = contrib.get(name);
            if (vc == null) continue;

            // Sort the fact keys within each vertex too, for full determinism
            List<String> violKeys = new ArrayList<>(vc.violationByKey().keySet());
            violKeys.sort(NaturalSort.naturalComparator());
            for (String key : violKeys) {
                globalViolationByKey.putIfAbsent(key, vc.violationByKey().get(key));
            }

            List<String> pKeys = new ArrayList<>(vc.pByKey().keySet());
            pKeys.sort(NaturalSort.naturalComparator());
            for (String key : pKeys) {
                globalPByKey.putIfAbsent(key, vc.pByKey().get(key));
            }
        }

        int violations = 0;
        for (boolean isViol : globalViolationByKey.values()) {
            if (isViol) violations++;
        }

        double modelP = Double.NaN;
        if (globalPByKey.size() >= 2) {
            List<Double> pvals = new ArrayList<>(globalPByKey.values());
            pvals.sort(Double::compareTo);
            modelP = model.getUniformityP(pvals);
        }

        return new GraphEval(violations, modelP, globalViolationByKey.size());
    }

    private Graph buildCandidateGraph(Graph base, CandidateEdit cand, AdjustmentGraphType gt) {
        if (base == null || cand == null) return null;

        Graph g2 = cand.applyTo(safeCopy(base));
        if (g2 == null) return null;

        if (gt == AdjustmentGraphType.CPDAG) {
            g2 = canonicalizeToCpdagOrNull(g2);
            if (g2 == null) return null;
            if (!cand.isNoOp() && g2.equals(base)) return null;
        } else if (gt == AdjustmentGraphType.PAG) {
            // keep as-is
        } else if (gt == AdjustmentGraphType.PDAG) {
            // keep as-is
        }

        if (requiresEdgePresenceCheck(cand) && !allIntendedNewEdgesPresent(g2, cand)) return null;

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

        Set<String> nb = new LinkedHashSet<>();
        for (Node n : base.getAdjacentNodes(xb)) if (n != null) nb.add(n.getName());

        Set<String> nc = new LinkedHashSet<>();
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

    private void closeWatchDialog() {
        JDialog dlg = this.watchDialog;
        this.watchDialog = null;
        if (dlg != null) dlg.dispose();
    }

    private boolean stopRequested() {
        SwingWorker<?, ?> w = activeWorker;
        return (w != null && w.isCancelled()) || Thread.currentThread().isInterrupted();
    }

    private boolean wouldPassGuards(Graph base, ScoredCandidate sc) {
        if (sc == null || sc.edit() == null || sc.edit().isNoOp()) return false;

        return isProgress(
                sc.violationsBaseline(),
                sc.violationsAfter(),
                base.getNumEdges(),
                sc.edgesAfter(),
                sc.modelPBefore(),
                sc.modelPAfter()
        );
    }

    /**
     * Controls which move types {@link #enumerateCandidates} will produce during
     * the two-phase GES-style repair sweep.
     *
     * <ul>
     *   <li>{@code ADD_ONLY}  – Phase 1: forward pass, only edge additions.</li>
     *   <li>{@code NON_ADD}   – Phase 2: backward pass, removals and reorientations
     *       but no new additions.</li>
     *   <li>{@code ALL}       – Interactive/manual use: the full move set (default).</li>
     * </ul>
     */
    private enum RepairPhase {
        ADD_ONLY,
        NON_ADD,
        ALL
    }

    private enum MoveType {
        REORIENT_SIMPLE,
        COLLIDER_FIX,
        REMOVE_EDGE,
        ADD_EDGE,
        OTHER
    }

    public enum AdjustmentGraphType {CPDAG, PDAG, PAG, DAG, MAG}

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

        static CandidateEdit replaceEdges(String label, List<Edge> oldEdges, List<Edge> newEdges) {
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(oldEdges, "oldEdges");
            Objects.requireNonNull(newEdges, "newEdges");

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
                    for (Edge oe : olds) {
                        if (oe == null) continue;
                        Node a0 = oe.getNode1(), b0 = oe.getNode2();
                        if (a0 == null || b0 == null) continue;
                        String an = a0.getName(), bn = b0.getName();
                        if (an == null || bn == null) continue;
                        Node a = g2.getNode(an), b = g2.getNode(bn);
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
                    parts.sort(Comparator.naturalOrder());
                    return "MULTI:" + label + ":" + String.join("|", parts);
                }

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
            Node a = e.getNode1(), b = e.getNode2();
            String an = (a == null || a.getName() == null) ? "?" : a.getName();
            String bn = (b == null || b.getName() == null) ? "?" : b.getName();
            Endpoint ea = e.getEndpoint1(), eb = e.getEndpoint2();
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

        Edge getEdge();

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
            return switch (columnIndex) {
                case COL_EDIT -> r.edit().description();
                case COL_BASE -> r.violationsBaseline();
                case COL_AFTER -> r.violationsAfter();
                case COL_DELTA -> r.delta();
                case COL_NODE_P -> r.nodePAfter();
                case COL_MODEL_P -> r.modelPAfter();
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
}
