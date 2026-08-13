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

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.GeneralAndersonDarlingTest;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.test.CachedIndependenceQueries;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.test.RowsSettable;
import edu.cmu.tetrad.search.utils.FciOrient;
import edu.cmu.tetrad.search.utils.MeekRules;
import edu.cmu.tetrad.search.utils.MagToPag;
import edu.cmu.tetrad.util.NaturalSort;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TMath;
import org.apache.commons.math3.distribution.UniformRealDistribution;
import org.apache.commons.math3.stat.inference.KolmogorovSmirnovTest;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Greedy local-search model-repair algorithm for causal graphs.
 *
 * <p>This class implements {@link IGraphSearch}, so its {@link #search()} method can be
 * called from simulation studies independently of any UI. It is the pure-logic
 * counterpart to {@code VertexRepairPanelGlobalRepair}: the panel delegates every
 * repair/search computation to this class and listens for progress via
 * {@link RepairListener}.
 * <p>
 * Candidate enumeration:
 * For each node {@code x}, single-step edge edits (additions, removals, replacements)
 * consistent with the chosen {@link AdjustmentGraphType} are enumerated. For DAG /
 * CPDAG / PDAG types, multi-edge incident-orientation patterns are also enumerated
 * subject to a combinatorial cap. Candidates are scored with Markov-checker
 * diagnostics derived from a {@link CachedIndependenceQueries} object.
 * <p>
 * Repair strategies:
 * <ul>
 *   <li>{@link RepairStrategy#LOCAL_SWEEP}: greedy node-by-node sweep repeated until
 *       convergence.</li>
 *   <li>{@link RepairStrategy#GLOBAL_QUEUE}: priority-queue driven; the best candidate
 *       across all nodes is always applied next, with lazy Model-P evaluation.</li>
 * </ul>
 * <p>
 * Progress events:
 * <p>
 * Register a {@link RepairListener} via {@link #addRepairListener} to receive
 * status strings, edit-applied notifications, and completion/cycle events.
 * <p>
 * Cancellation:
 * <p>
 * Call {@link #cancel()} from any thread to request early termination.
 * <p>
 * Performance (changes from the pre-2026-8-13 implementation):
 * <p>
 * The baseline evaluation bundle (canonicalized base graph, per-vertex Markov
 * contributions, baseline violation count, and baseline Model-P) is now computed once
 * per working-graph state and memoized, keyed on an internal graph-version counter,
 * instead of being rebuilt once per node per queue pass and once per queue poll. This
 * is a pure hoisting of identical computations and does not change which candidate is
 * selected. A side effect is that all consumers of the base graph within one graph
 * state see the same canonicalization, where previously each call to the (potentially
 * tie-breaking) canonicalizer could in principle produce a different member of the
 * equivalence class.
 * <p>
 * Optionally, {@link #setAffectedOnlyInvalidation(boolean)} restricts the
 * per-edit candidate recomputation in {@link RepairStrategy#GLOBAL_QUEUE} to the
 * vertices whose adjacency actually changed, rather than all vertices; see that
 * method's documentation for the exact semantics, including the lazy revalidation of
 * stale queue entries and the full verification sweep that certifies convergence.
 * The default is {@code true} (as of 2026-8-13); pass {@code false} to restore the
 * original full-invalidation behavior exactly.
 *
 * @author josephramsey (extracted from VertexRepairPanelGlobalRepair)
 */
public final class VertexRepairSearch implements IGraphSearch {

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    /**
     * Canonical ranking: best candidate sorts first.
     * Priority chain: (1) fewer Markov violations; (2) fewer edges when alpha > 0.01;
     * (3) higher Model-P; (4) stable key tie-break.
     */
//    /**
//     * Canonical ranking: best candidate sorts first.
//     * Priority chain:
//     *   (1) fewer Markov violations (smaller delta);
//     *   (2) fewer edges, but only for candidates that have "earned" the edges
//     *       comparison — removals, no-ops, or candidates whose Model-P clears alpha.
//     *       Candidates with NaN Model-P are treated as not having earned it (they
//     *       haven't been evaluated yet), so they sort after those that have.
//     *   (3) higher Model-P, with NaN sorting last (unknown is worse than known);
//     *   (4) stable key tie-break.
//     */
//    public static final Comparator<ScoredCandidate> CANONICAL_TABLE_ORDER = (a, b) -> {
//        if (a == null && b == null) return 0;
//        if (a == null) return 1;
//        if (b == null) return -1;
//
//        int c;
//
//        // (1) Fewer Markov violations wins.
//        c = Integer.compare(a.violationsAfter(), b.violationsAfter());
//        if (c != 0) return c;
//
//        // (2) Edges comparison — only candidates that are removals, no-ops, or
//        // whose Model-P clears alpha "earn" the edges comparison. Candidates with
//        // NaN Model-P have not been evaluated yet and so are treated as not earning
//        // it (MAX_VALUE sinks them relative to earned candidates but ties among
//        // themselves, so the next key breaks them).
//        int edges1 = earnsEdgesComparison(a) ? a.edgesAfter() : Integer.MAX_VALUE;
//        int edges2 = earnsEdgesComparison(b) ? b.edgesAfter() : Integer.MAX_VALUE;
//        c = Integer.compare(edges1, edges2);
//        if (c != 0) return c;
//
//        // (3) Higher Model-P wins. NaN sorts last (unknown/unevaluated is worse
//        // than any real value).
//        c = compareModelPDesc(a.modelPAfter(), b.modelPAfter());
//        if (c != 0) return c;
//
//        c = compareModelPDesc(a.nodePAfter(), b.nodePAfter());
//        if (c != 0) return c;
//
//        c  = compareModelPDesc(a.modelPAfter() - a.modelPBefore(), b.modelPAfter() - b.modelPBefore());
//        if (c != 0) return c;
//
//        // (4) Stable tie-break on keys and descriptions.
//        return stableTieBreak(a, b);
//    };


    /**
     * Canonical ranking: best candidate sorts first.
     * Priority chain:
     *   (0) Markov-passing beats non-passing. A candidate "passes" when its
     *       Model-P exceeds alpha. Within the non-passing group, a larger
     *       Model-P is preferred — this gives the search a gradient toward
     *       clearing alpha when it's stuck in a non-I-map region. NaN
     *       Model-P is treated as non-passing and sorts last within that group.
     *   (1) Fewer Markov violations (violationsAfter).
     *   (2) Fewer edges, but only for candidates that have "earned" the edges
     *       comparison — removals, no-ops, or candidates whose Model-P clears
     *       alpha. NaN Model-P does not earn it.
     *   (3) Higher Model-P.
     *   (4) Higher node-P.
     *   (5) Larger Model-P improvement (modelPAfter - modelPBefore).
     *   (6) Stable key/description tie-break.
     */
    public static final Comparator<ScoredCandidate> CANONICAL_TABLE_ORDER = (a, b) -> {
        if (a == null && b == null) return 0;
        if (a == null) return 1;
        if (b == null) return -1;

        int c;

        // (1) Fewer Markov violations wins.
        c = Integer.compare(a.violationsAfter(), b.violationsAfter());
        if (c != 0) return c;

        // (0) Markov-passing beats non-passing.
        boolean aPasses = passesMarkov(a);
        boolean bPasses = passesMarkov(b);
        if (aPasses != bPasses) return aPasses ? -1 : 1;

        // If BOTH fail to pass, prefer the one closer to passing (larger Model-P).
        // Within this group, NaN sorts last (unknown distance to alpha).
        // If BOTH pass, fall through — downstream tiers handle the ordering.
        if (!aPasses /* && !bPasses */) {
            c = compareModelPDesc(a.modelPAfter(), b.modelPAfter());
            if (c != 0) return c;
        }

        // (2) Edges comparison — only "earned" candidates participate; others are
        // pinned to MAX_VALUE so they tie among themselves and defer to later tiers.
        int edges1 = earnsEdgesComparison(a) ? a.edgesAfter() : Integer.MAX_VALUE;
        int edges2 = earnsEdgesComparison(b) ? b.edgesAfter() : Integer.MAX_VALUE;
        c = Integer.compare(edges1, edges2);
        if (c != 0) return c;

        // (3) Higher Model-P wins. NaN sorts last.
        c = compareModelPDesc(a.modelPAfter(), b.modelPAfter());
        if (c != 0) return c;

        // (4) Higher node-P wins. NaN sorts last.
        c = compareModelPDesc(a.nodePAfter(), b.nodePAfter());
        if (c != 0) return c;

        // (5) Larger Model-P improvement wins. NaN (from NaN arithmetic) sorts last.
        c = compareModelPDesc(
                a.modelPAfter() - a.modelPBefore(),
                b.modelPAfter() - b.modelPBefore());
        if (c != 0) return c;

        // (6) Stable tie-break.
        return stableTieBreak(a, b);
    };

    /**
     * Whether {@code sc} clears the Markov gate: Model-P strictly exceeds alpha.
     * NaN Model-P counts as not passing.
     */
    private static boolean passesMarkov(ScoredCandidate sc) {
        double mp = sc.modelPAfter();
        return !Double.isNaN(mp) && mp > sc.alpha();
    }

    /**
     * Whether {@code sc} has "earned" the edges comparison: removals and no-ops
     * always earn it; other moves earn it only when their Model-P is known and
     * clears alpha. A NaN Model-P means "not yet evaluated", so it does not earn it.
     */
    private static boolean earnsEdgesComparison(ScoredCandidate sc) {
        if (sc.edit().isNoOp()) return true;
        if (sc.edit().moveType() == MoveType.REMOVE_EDGE) return true;
        double mp = sc.modelPAfter();
        return !Double.isNaN(mp) && mp > sc.alpha();
    }

    /**
     * Descending comparison of Model-P values with explicit NaN handling.
     * NaN sorts last (i.e. NaN is "worse" than any real value). Two NaNs tie.
     */
    private static int compareModelPDesc(double a, double b) {
        boolean aNaN = Double.isNaN(a);
        boolean bNaN = Double.isNaN(b);
        if (aNaN && bNaN) return 0;
        if (aNaN) return 1;   // a worse → a sorts later
        if (bNaN) return -1;  // b worse → b sorts later
        return -Double.compare(a, b);
    }

    private static final int DEFAULT_MODELP_TOP_K = 50;

    // -------------------------------------------------------------------------
    // Configuration fields (set before calling search())
    // -------------------------------------------------------------------------
    private final CachedIndependenceQueries Q;
    private final ConditioningSetType type;
    private final List<RepairListener> listeners = new CopyOnWriteArrayList<>();
    private final Map<String, List<ScoredCandidate>> globalCandsByNode = new HashMap<>();
    private double pruneAlpha = 1.0;
    private Graph workingGraph;
    private Knowledge knowledge = new Knowledge();
    private AdjustmentGraphType graphType = AdjustmentGraphType.CPDAG;

    // -------------------------------------------------------------------------
    // Listener infrastructure
    // -------------------------------------------------------------------------
    private RepairStrategy repairStrategy = RepairStrategy.GLOBAL_QUEUE;

    // -------------------------------------------------------------------------
    // Cancellation
    // -------------------------------------------------------------------------
    private long seed = System.currentTimeMillis();

    // -------------------------------------------------------------------------
    // Global-queue state (populated only during a GLOBAL_QUEUE run)
    // -------------------------------------------------------------------------
    private volatile boolean cancelRequested = false;
    private PriorityQueue<QueueEntry> globalQueue = new PriorityQueue<>();
    private boolean useAndersonDarling = false;
    private boolean useWildBootstrap = false;
    private int wbNumBootstraps = 1000;
    private long wbSeed = 0L;
    private boolean verbose = false;
    /**
     * Monotone counter bumped whenever the working graph (or any setting that affects
     * baseline evaluation) changes. Used to key the memoized {@link BaselineBundle} and
     * to detect stale queue entries under affected-only invalidation.
     */
    private long graphVersion = 0L;
    /**
     * Memoized baseline bundle for the current {@link #graphVersion}; recomputed lazily
     * by {@link #baselineBundle()} when stale.
     */
    private BaselineBundle baselineBundle = null;
    /**
     * When true (the default as of 2026-8-13), GLOBAL_QUEUE recomputes candidates only
     * for vertices affected by each applied edit; see
     * {@link #setAffectedOnlyInvalidation(boolean)}.
     */
    private boolean affectedOnlyInvalidation = true;

    // =========================================================================
    // Construction
    // =========================================================================

    /**
     * Constructs a new instance of VertexRepairSearch.
     *
     * @param graph the graph to be repaired; must not be null
     * @param test  the independence test to be used during the repair process; must not be null
     * @param type  the type of conditioning set to be used; must not be null
     */
    public VertexRepairSearch(Graph graph, IndependenceTest test, ConditioningSetType type) {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(test, "test");
        Objects.requireNonNull(type, "type");
        this.Q = new CachedIndependenceQueries(test);
        this.workingGraph = safeCopy(graph);
        this.type = type;

        if (test instanceof RowsSettable) {
            ((RowsSettable) test).setRows(getSubsampleRows(1.0));
        }
    }

    // =========================================================================
    // IGraphSearch
    // =========================================================================

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
            Node a0 = e.getNode1(), b0 = e.getNode2();
            if (a0 == null || b0 == null) continue;
            String an0 = a0.getName(), bn0 = b0.getName();
            if (an0 == null || bn0 == null) continue;
            Node a = dag.getNode(an0), b = dag.getNode(bn0);
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

    private static Node resolveNode(Graph g, String name) {
        if (g == null || name == null) return null;
        return g.getNode(name);
    }

    // =========================================================================
    // Configuration setters / getters
    // =========================================================================

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

    private static boolean requiresEdgePresenceCheck(CandidateEdit cand) {
        if (cand == null || cand.isNoOp()) return false;
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

    private static boolean containsStructuralEdge(Graph g, Edge template) {
        if (g == null || template == null) return false;
        Edge reb = rebindEdgeToGraph(g, template);
        if (reb == null) return false;
        Edge inG = g.getEdge(reb.getNode1(), reb.getNode2());
        if (inG == null) return false;
        Endpoint a1 = inG.getProximalEndpoint(reb.getNode1());
        Endpoint b1 = inG.getProximalEndpoint(reb.getNode2());
        return a1 == reb.getProximalEndpoint(reb.getNode1())
                && b1 == reb.getProximalEndpoint(reb.getNode2());
    }

    private static Edge rebindEdgeToGraph(Graph g, Edge e) {
        if (g == null || e == null) return null;
        Node a0 = e.getNode1(), b0 = e.getNode2();
        if (a0 == null || b0 == null) return null;
        String an = a0.getName(), bn = b0.getName();
        if (an == null || bn == null) return null;
        Node a = g.getNode(an), b = g.getNode(bn);
        if (a == null || b == null) return null;
        Endpoint ea = e.getProximalEndpoint(a0);
        Endpoint eb = e.getProximalEndpoint(b0);
        return new Edge(a, b, ea, eb);
    }

    // =========================================================================
    // Listener management
    // =========================================================================

    private static Endpoint endpointAt(Edge e, Node n) {
        if (e == null || n == null) return null;
        return e.getProximalEndpoint(n);
    }

    /**
     * Builds a canonical fact-key for deduplication.
     *
     * @param f the fact to be deduplicated; may be {@code null}
     *          (in which case, a random UUID is returned)
     * @return a canonical fact-key for the given fact; never {@code null}
     */
    public static String factKey(IndependenceFact f) {
        if (f == null || f.getX() == null || f.getY() == null) return UUID.randomUUID().toString();
        String a = f.getX().getName(), b = f.getY().getName();
        if (a == null) a = "";
        if (b == null) b = "";
        if (a.compareTo(b) > 0) {
            String t = a;
            a = b;
            b = t;
        }
        List<String> z = new ArrayList<>();
        for (Node n : f.getZ()) if (n != null && n.getName() != null) z.add(n.getName());
        z.sort(NaturalSort.naturalComparator());
        return a + "|" + b + "|" + String.join(",", z);
    }

    private static MoveType moveType(CandidateEdit e) {
        return (e == null) ? MoveType.OTHER : e.moveType();
    }

    // =========================================================================
    // Per-node search (used interactively by the panel)
    // =========================================================================

    private static String safeLower(String s) {
        return s == null ? "" : s.toLowerCase();
    }

    private static boolean containsAny(String s, String... needles) {
        for (String n : needles) if (n != null && !n.isEmpty() && s.contains(n)) return true;
        return false;
    }

    // =========================================================================
    // Private: repair strategies
    // =========================================================================

    private static int stableTieBreak(ScoredCandidate a, ScoredCandidate b) {
        String ka = (a.edit() == null || a.edit().key() == null) ? "" : a.edit().key();
        String kb = (b.edit() == null || b.edit().key() == null) ? "" : b.edit().key();
        int c = ka.compareTo(kb);
        if (c != 0) return c;
        String da = (a.edit() == null || a.edit().description() == null) ? "" : a.edit().description();
        String db = (b.edit() == null || b.edit().description() == null) ? "" : b.edit().description();
        return da.compareTo(db);
    }

    private static Edge getEdgeByNames(Graph g, Edge e) {
        if (g == null || e == null) return null;
        String a = e.getNode1() == null ? null : e.getNode1().getName();
        String b = e.getNode2() == null ? null : e.getNode2().getName();
        if (a == null || b == null) return null;
        Node ga = g.getNode(a), gb = g.getNode(b);
        if (ga == null || gb == null) return null;
        return g.getEdge(ga, gb);
    }

    // =========================================================================
    // Private: queue management
    // =========================================================================

    private static double getKolomogorovP(List<Double> pvals) {
        double[] x = pvals.stream().mapToDouble(Double::doubleValue).toArray();
        KolmogorovSmirnovTest ks = new KolmogorovSmirnovTest();
        return ks.kolmogorovSmirnovTest(new UniformRealDistribution(0.0, 1.0), x);
    }

    /**
     * Tests a list of p-values against the Anderson-Darling Test.
     *
     * @param pValues the list of p-values to be tested
     * @return the p-value obtained from the Anderson-Darling Test
     */
    public static double getAndersonDarlingP(List<Double> pValues) {
        GeneralAndersonDarlingTest generalAndersonDarlingTest = new GeneralAndersonDarlingTest(pValues, new UniformRealDistribution(0, 1));
        // A-squared evaluated against getProbTail, not getP(). The null here is a fully specified Uniform(0, 1)
        // with no estimated parameters (case 0), but getP() returns Stephens' case-3 approximation, which both
        // inflates the statistic by (1 + 0.75/n + 2.25/n^2) and evaluates it with the piecewise formulas for
        // testing normality with an estimated mean and variance. On exactly uniform p-values that combination
        // rejects at roughly 52-63% against a nominal 5%, and unlike a small-sample correction the error does not
        // decay with n. See MarkovCheck.calcStats and TestAndersonDarlingCalibration.
        //
        // This value ranks candidate edits, so the miscalibration was not merely a shifted scale: candidates differ
        // in how many implied independencies they have, and the distortion depends on that count, so it altered the
        // ORDER of candidates rather than only their scores. Repair may therefore select different edits than it
        // did before this fix.
        return 1. - generalAndersonDarlingTest.getProbTail(pValues.size(), generalAndersonDarlingTest.getASquared());
    }

    // =========================================================================
    // Private: candidate computation
    // =========================================================================

    private void vlog(String fmt, Object... args) {
        if (this.verbose) {
            System.out.println("[VertexRepairSearch] " + String.format(fmt, args));
        }
    }

    /**
     * Returns a list of row indices for a subsample of the data set.
     *
     * @param v The fraction of the data set to use.
     * @return A list of row indices for a subsample of the data set.
     */
    private List<Integer> getSubsampleRows(double v) {
        int sampleSize = ((DataSet) Q.getTest().getData()).getNumRows();
        int subsampleSize = (int) TMath.floor(sampleSize * v);
        List<Integer> rows = new ArrayList<>(sampleSize);
        for (int i = 0; i < sampleSize; i++) {
            rows.add(i);
        }

        Collections.shuffle(rows);
        List<Integer> integers = rows.subList(0, subsampleSize);

        List<Integer> selectedRows = new ArrayList<>(integers.size());

        for (int row : rows) {
            if (integers.contains(row)) {
                selectedRows.add(row);
            }
        }

        return selectedRows;
    }

    /**
     * Runs the full repair sweep (using the configured strategy) and returns the
     * repaired graph.
     *
     * <p>This method is safe to call from a background thread. Cancellation is
     * honoured at interruption checkpoints; call {@link #cancel()} from another
     * thread to request early termination.
     *
     * @return the repaired graph (may equal the starting graph if nothing improved)
     * @throws InterruptedException if the calling thread is interrupted
     */
    @Override
    public Graph search() throws InterruptedException {
        cancelRequested = false;

        // Selection bias is out of scope for repair; reject such an input up front
        // rather than silently repairing around it. (Added 2026-8-13.)
        if (hasSelectionBias(workingGraph)) {
            throw new IllegalArgumentException(
                    "The graph to be repaired exhibits selection bias (a selection node, or a "
                            + "tail-tail or circle-tail edge under graph type " + graphType
                            + "). Vertex repair does not model selection bias; remove the "
                            + "selection structure or repair the graph under a graph type in "
                            + "which such edges are unoriented rather than selection-induced.");
        }

        long previousSeed = RandomUtil.getInstance().nextLong();
        RandomUtil.getInstance().setSeed(seed);

        try {
            fireStatus("Starting repair (strategy=" + repairStrategy + ", seed=" + seed + ")...");

            if (repairStrategy == RepairStrategy.GLOBAL_QUEUE) {
                runGlobalRepair();
            } else {
                List<String> cycleWarnings = new ArrayList<>();
                Set<String> seenSweepStates = new LinkedHashSet<>();
                runRepairPhase(seenSweepStates, cycleWarnings);
            }
        } finally {
            RandomUtil.getInstance().setSeed(previousSeed);
        }

        return safeCopy(workingGraph);
    }

    /**
     * Returns the independence test backing this search (delegates to the model).
     *
     * @return the independence test supplied at construction
     */
    @Override
    public IndependenceTest getTest() {
        return Q.getTest();
    }

    // =========================================================================
    // Private: candidate enumeration
    // =========================================================================

    /**
     * Returns the current working graph (may be updated during search).
     *
     * @return the current working graph
     */
    public Graph getGraph() {
        return workingGraph;
    }

    /**
     * Replaces the working graph used for repair. A defensive copy is stored, so later
     * mutations of the argument do not affect this search.
     *
     * @param graph the new graph to use; must not be {@code null}
     * @throws NullPointerException if {@code graph} is {@code null}
     */
    public void setGraph(Graph graph) {
        this.workingGraph = safeCopy(Objects.requireNonNull(graph, "graph"));
        bumpGraphVersion();
    }

    /**
     * Sets background knowledge constraints. Null is treated as empty knowledge.
     *
     * @param knowledge the knowledge to use; may be {@code null}
     */
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = (knowledge == null) ? new Knowledge() : knowledge;
        bumpGraphVersion();
    }

    /**
     * Returns true if the given candidate graph is inconsistent with the background knowledge. This is stricter than
     * {@link Knowledge#isViolatedBy(Graph)} alone: in addition to forbidden directed edges and undirected edges whose
     * orientations are both forbidden, it rejects candidates containing an undirected edge exactly one of whose
     * orientations is forbidden. Such an edge is not impossible, but it is under-oriented - a knowledge-respecting
     * CPDAG would have compiled it to the allowed direction - and accepting it would let repair "erase" orientations
     * that the knowledge determines. (A more permissive alternative would be to orient such edges rather than reject
     * the candidate; rejection is the conservative choice and keeps candidate enumeration unchanged.)
     *
     * <p>Note (2026-8-12): knowledge-compelled orientations are now restored during CPDAG canonicalization by
     * {@link #applyKnowledgeOrientations(Graph)}, so this check no longer rejects the canonicalized base graph or
     * canonicalized candidates merely because {@link GraphTransforms#dagToCpdag} discarded orientations that the
     * knowledge determines. It continues to reject genuinely under-oriented candidates (e.g., a proposed undirected
     * edge one of whose orientations is forbidden, before canonicalization).
     */
    private boolean violatesKnowledge(Graph g) {
        if (this.knowledge == null || this.knowledge.isEmpty()) return false;
        if (this.knowledge.isViolatedBy(g)) return true;

        // The under-orientation rejection below is deliberately NOT applied to the ancestral
        // types. (Added 2026-8-13.) In a CPDAG, an undirected edge one of whose orientations is
        // forbidden is genuinely under-oriented: the knowledge determines the direction. The
        // analogous inference fails in a PAG. A circle-circle edge x o-o y with x -> y forbidden
        // is NOT thereby determined, because x <-> y remains available -- the arrowhead at y is
        // still possible and the circle is honest about that. Rejecting such candidates would
        // discard legitimate PAGs on a CPDAG intuition that does not survive the move to
        // ancestral graphs. Knowledge-forced marks are instead installed positively by
        // applyKnowledgePagOrientations, which is where PAG knowledge handling belongs.
        if (graphType == AdjustmentGraphType.MAG || graphType == AdjustmentGraphType.PAG) {
            return false;
        }

        for (Edge edge : g.getEdges()) {
            if (Edges.isUndirectedEdge(edge)) {
                String a = edge.getNode1().getName();
                String b = edge.getNode2().getName();

                if (this.knowledge.isForbidden(a, b) != this.knowledge.isForbidden(b, a)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Sets the graph type that governs which edits are legal.
     *
     * @param graphType the graph type to use; must not be {@code null}
     */
    public void setGraphType(AdjustmentGraphType graphType) {
        this.graphType = Objects.requireNonNull(graphType, "graphType");
        bumpGraphVersion();
    }

    // =========================================================================
    // Private: graph preparation and apply
    // =========================================================================

    /**
     * Sets the repair strategy.
     *
     * @param repairStrategy the repair strategy to use; must not be {@code null}
     */
    public void setRepairStrategy(RepairStrategy repairStrategy) {
        this.repairStrategy = Objects.requireNonNull(repairStrategy, "repairStrategy");
    }

    /**
     * When true, {@link RepairStrategy#GLOBAL_QUEUE} recomputes candidate lists after
     * each applied edit only for the vertices plausibly affected by that edit
     * (endpoints of every edge changed between the before/after graphs — including
     * changes propagated by CPDAG canonicalization — closed under one hop of adjacency,
     * plus the edited node), rather than for every vertex in the graph. Since a full
     * recomputation costs roughly (number of vertices) x (candidates per vertex) x
     * (candidate-graph canonicalization + evaluation), this typically reduces per-edit
     * cost by a factor on the order of the ratio of graph size to edit-neighborhood
     * size.
     *
     * <p>Semantics under this mode (change from the pre-2026-8-13 implementation, which
     * always recomputed all vertices):
     * <ul>
     *   <li>Queue entries for untouched vertices survive across edits with scores
     *       computed against an older graph state. When such a stale entry reaches the
     *       head of the queue, it is re-evaluated fresh against the current graph, and
     *       is applied only if the fresh evaluation beats a fresh no-op under
     *       {@link #CANONICAL_TABLE_ORDER}; otherwise it is discarded. This check is
     *       slightly better informed than the full-invalidation head check, since both
     *       sides carry evaluated Model-P values.</li>
     *   <li>When the queue drains, one full candidate rebuild is performed at the
     *       current graph state ("verification sweep") before convergence is declared,
     *       so the fixed point is identical in kind to full invalidation: convergence
     *       means no single candidate anywhere improves on the current graph.</li>
     *   <li>The greedy <em>path</em> to the fixed point may differ from full
     *       invalidation, because stale scores can order the queue differently at ties;
     *       every applied edit is still verified fresh before application.</li>
     * </ul>
     *
     * <p>The default is {@code true} (as of 2026-8-13). Pass {@code false} to restore
     * the original full-invalidation behavior exactly.
     *
     * @param affectedOnlyInvalidation true to restrict per-edit recomputation to
     *                                 affected vertices
     */
    public void setAffectedOnlyInvalidation(boolean affectedOnlyInvalidation) {
        this.affectedOnlyInvalidation = affectedOnlyInvalidation;
    }

    /**
     * Sets the random seed used for node-traversal order during {@link #search()}. If this is
     * never called, the seed defaults to the construction-time value of
     * {@code System.currentTimeMillis()}, so runs are not reproducible by default; set an
     * explicit seed to make them so. The value is used as given, including 0.
     *
     * @param seed the random seed to use
     */
    public void setSeed(long seed) {
        this.seed = seed;
    }

    // =========================================================================
    // Private: graph evaluation
    // =========================================================================

    /**
     * Registers a listener for repair-progress events.
     *
     * @param listener the listener to register
     */
    public void addRepairListener(RepairListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /**
     * Removes a previously registered listener.
     *
     * @param listener the listener to remove
     */
    public void removeRepairListener(RepairListener listener) {
        listeners.remove(listener);
    }

    /**
     * Requests early termination of an in-progress {@link #search()} call.
     * The call will return cleanly at the next cancellation checkpoint.
     */
    public void cancel() {
        cancelRequested = true;
    }

    /**
     * Computes and returns the ranked list of scored candidate edits for the given
     * node in the current working graph. This is the same logic used by the panel's
     * "Adjust x" button.
     *
     * @param node the node to search around
     * @return scored, ranked candidates (best first); empty if nothing legal was found
     */
    public List<ScoredCandidate> searchForNode(Node node) {
        BaselineBundle bb = baselineBundle();
        if (bb == null) return List.of();
        Graph base = bb.base();
        if (violatesKnowledge(base)) return List.of();

        Node nodeInBase = base.getNode(node.getName());
        if (nodeInBase == null) return List.of();

        List<CandidateEdit> candidates = new ArrayList<>(enumerateCandidates(base, nodeInBase));
        if (candidates.stream().noneMatch(CandidateEdit::isNoOp)) {
            candidates.addFirst(CandidateEdit.noOp());
        }
        return scoreCandidates(bb, nodeInBase, candidates);
    }

    /**
     * Applies the given candidate edit to the working graph and returns the
     * resulting graph (or {@code null} if the edit could not be applied).
     *
     * <p>On success the internal working graph is updated.
     *
     * @param cand the candidate edit to apply
     * @return the resulting graph (or {@code null} if the edit could not be applied)
     */
    public Graph applyEdit(CandidateEdit cand) {
        if (cand == null || cand.isNoOp()) return workingGraph;
        applyCandidateInternal(cand);
        return workingGraph;
    }

    private void runRepairPhase(Set<String> seenSweepStates, List<String> cycleWarnings) {
        boolean anyChangeInSweep;

        do {
            if (stopRequested()) return;
            cycleWarnings.clear();
            anyChangeInSweep = false;

            List<Node> nodes = new ArrayList<>(workingGraph.getNodes());
            RandomUtil.shuffle(nodes);

            for (Node node : nodes) {
                if (stopRequested()) return;

                Node current = workingGraph.getNode(node.getName());
                if (current == null) continue;

                Set<String> attemptedKeys = new LinkedHashSet<>();

                while (true) {
                    if (stopRequested()) return;

                    BaselineBundle bb = baselineBundle();
                    if (bb == null) {
                        fireStatus("Canonicalization failed during repair.");
                        return;
                    }

                    Node currentInBase = bb.base().getNode(current.getName());
                    if (currentInBase == null) break;

                    List<ScoredCandidate> candidates = computeScoredCandidatesForNode(bb, currentInBase);
                    if (candidates.isEmpty()) break;

                    ScoredCandidate top = candidates.getFirst();
                    if (top.edit().isNoOp() || !top.passesGuards()) break;

                    String key = top.edit().key();
                    if (!attemptedKeys.add(key)) {
                        cycleWarnings.add(current.getName() + ": \"" + top.edit().description() + "\"");
                        break;
                    }

                    Graph before = safeCopy(workingGraph);
                    applyCandidateInternal(top.edit());

                    if (workingGraph.equals(before)) break;

                    anyChangeInSweep = true;
                    fireEditApplied(top.edit(), workingGraph);

                    Node refreshed = workingGraph.getNode(current.getName());
                    if (refreshed == null) break;
                    current = refreshed;
                }
            }

            if (anyChangeInSweep) {
                String state = workingGraph.toString();
                if (!seenSweepStates.add(state)) {
                    fireStatus("Inter-sweep cycle detected: graph returned to a previously visited state. Stopping.");
                    return;
                }
            }

        } while (anyChangeInSweep);

        fireRepairConverged(0, "Local sweep converged.");
    }

    private void runGlobalRepair() {
        Set<String> seenStates = new LinkedHashSet<>();
        int editsApplied = 0;

        if (pruneAlpha < 1) {
            fireStatus("Pruning obvious false-positive edges...");
            pruneObviousFalsePositives(workingGraph, 2); // depth 3 is a reasonable default
            Q.clearCaches(); // flush cached results since graph changed
            bumpGraphVersion();
        }

        fireStatus("Building global candidate queue...");
        if (!initGlobalQueue()) return;
        long fullRebuildAtVersion = graphVersion;

        while (true) {
            if (stopRequested()) return;

            if (globalQueue.isEmpty()) {
                // Under affected-only invalidation, entries for untouched vertices may
                // have gone stale and been dropped; before declaring convergence, do one
                // full rebuild at the current graph state to certify that no single
                // candidate anywhere improves. This restores exactly the fixed-point
                // guarantee of full invalidation, paying for a full pass only when the
                // queue drains rather than after every edit.
                if (!affectedOnlyInvalidation || fullRebuildAtVersion == graphVersion) break;
                fireStatus("Verification sweep: rebuilding full candidate queue...");
                if (!initGlobalQueue()) return;
                fullRebuildAtVersion = graphVersion;
                if (globalQueue.isEmpty()) break;
                continue;
            }

            QueueEntry entry = globalQueue.poll();
            if (entry == null) break;

            List<ScoredCandidate> currentForNode = globalCandsByNode.get(entry.nodeName());
            if (currentForNode == null || currentForNode.isEmpty()) continue;
            if (currentForNode.getFirst() != entry.scored()) continue; // stale

            ScoredCandidate sc = entry.scored();
            if (sc.edit().isNoOp()) continue;

            ScoredCandidate withMp = evalModelPForEntry(entry);

            if (withMp == null) {
                // Stale candidate no longer applies; refresh this node's candidates.
                if (!invalidateAndRecompute(Set.of(entry.nodeName()))) return;
                continue;
            }

            if (withMp.edit() == null || withMp.edit().isNoOp()) continue;

            // Lazy revalidation: an entry scored against an older graph state must
            // re-earn application by beating the fresh no-op for the current state.
            // (Entries at the current version have already beaten the no-op via the
            // head-of-list check, exactly as under full invalidation.) A dropped head
            // is removed from its node's list so the next-best candidate can surface.
            if (affectedOnlyInvalidation && entry.version() != graphVersion
                    && !applyWorthy(withMp, entry.nodeName())) {
                currentForNode.removeFirst();
                continue;
            }

            Graph before = safeCopy(workingGraph);
            applyCandidateInternal(withMp.edit());

            if (workingGraph.equals(before)) continue;

            editsApplied++;
            vlog("Global queue: applied edit #%d: %s", editsApplied, withMp.edit().description());
            fireEditApplied(withMp.edit(), workingGraph);

            String state = workingGraph.toString();
            if (!seenStates.add(state)) {
                fireStatus("Global repair: cycle detected after " + editsApplied + " edits. Stopping.");
                return;
            }

            Set<String> namesToRecompute;
            if (affectedOnlyInvalidation) {
                namesToRecompute = invalidationSet(before, workingGraph,
                        entry.nodeName(), withMp.edit());
            } else {
                namesToRecompute = workingGraph.getNodes().stream()
                        .map(Node::getName)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
            }

            if (!invalidateAndRecompute(namesToRecompute)) return;

            final int count = editsApplied;
            fireStatus("Global repair: " + count + " edits applied...");
        }

        fireRepairConverged(editsApplied,
                "Global repair converged after " + editsApplied + " edits.");
    }

    // =========================================================================
    // Private: guards and progress
    // =========================================================================

    private boolean initGlobalQueue() {
        globalCandsByNode.clear();
        globalQueue = new PriorityQueue<>();

        BaselineBundle bb = baselineBundle();
        if (bb == null) {
            fireStatus("Canonicalization failed during queue init.");
            return false;
        }

        List<Node> nodes = new ArrayList<>(workingGraph.getNodes());
        nodes.sort(Comparator.comparing(Node::getName, NaturalSort.NATURAL_NAME_COMPARATOR));

        for (Node node : nodes) {
            if (stopRequested()) return false;

            Node inBase = bb.base().getNode(node.getName());
            if (inBase == null) continue;

            List<ScoredCandidate> scored = computeScoredCandidatesForNodeNoModelP(bb, inBase);
            if (scored.isEmpty()) continue;

            globalCandsByNode.put(node.getName(), scored);
            for (ScoredCandidate sc : scored) {
                if (!sc.edit().isNoOp()) {
                    globalQueue.offer(new QueueEntry(node.getName(), sc, graphVersion));
                }
            }
        }
        return true;
    }

    private boolean invalidateAndRecompute(Set<String> affectedNames) {
        BaselineBundle bb = baselineBundle();
        if (bb == null) {
            fireStatus("Canonicalization failed during queue update.");
            return false;
        }

        for (String name : affectedNames) {
            if (stopRequested()) return false;
            if (name == null) continue;

            Node inGraph = workingGraph.getNode(name);
            Node inBase = (inGraph != null) ? bb.base().getNode(name) : null;

            if (inBase == null) {
                globalCandsByNode.remove(name);
                continue;
            }

            List<ScoredCandidate> fresh = computeScoredCandidatesForNodeNoModelP(bb, inBase);
            globalCandsByNode.put(name, fresh);

            for (ScoredCandidate sc : fresh) {
                if (!sc.edit().isNoOp()) {
                    globalQueue.offer(new QueueEntry(name, sc, graphVersion));
                }
            }
        }
        return true;
    }

    // =========================================================================
    // Private: graph type helpers
    // =========================================================================

    private List<ScoredCandidate> computeScoredCandidatesForNode(BaselineBundle bb, Node node) {
        List<CandidateEdit> candidates = new ArrayList<>(enumerateCandidates(bb.base(), node));
        if (candidates.stream().noneMatch(CandidateEdit::isNoOp)) {
            candidates.addFirst(CandidateEdit.noOp());
        }
        return scoreCandidates(bb, node, candidates);
    }

    private List<ScoredCandidate> computeScoredCandidatesForNodeNoModelP(BaselineBundle bb, Node node) {
        Graph base = bb.base();
        List<CandidateEdit> candidates = new ArrayList<>(enumerateCandidates(base, node));
        if (candidates.stream().noneMatch(CandidateEdit::isNoOp)) {
            candidates.addFirst(CandidateEdit.noOp());
        }

        GlobalEvalCache baseCache = bb.cache();
        int baseline = bb.violations();

        Map<String, Graph> candGraphByKey = new HashMap<>();
        List<ScoredCandidate> result = new ArrayList<>();

        for (CandidateEdit cand : candidates) {
            if (stopRequested()) return List.of();

            Graph g2 = candGraphByKey.computeIfAbsent(cand.key(),
                    k -> buildCandidateGraph(base, cand));
            if (g2 == null) continue;
            if (violatesKnowledge(g2)) continue;

            boolean useLocality = usesLocality();
            Set<String> affected = affectedVertices(base, node, g2);
            int after = useLocality
                    ? evalGraphLocality(baseCache, g2, affected, false).violations()
                    : evalViolationsOnly(g2);

            ScoredCandidate sc = new ScoredCandidate(
                    cand, baseline, after,
                    Double.NaN,
                    Double.NaN, Double.NaN,
                    g2.getNumEdges(), true,
                    Q.getAlpha());

            result.add(sc);
        }

        result.sort(CANONICAL_TABLE_ORDER);
        return result;
    }

    private List<ScoredCandidate> scoreCandidates(BaselineBundle bb, Node node, List<CandidateEdit> candidates) {
        Graph base = bb.base();
        GlobalEvalCache baseCache = bb.cache();
        int baseline = bb.violations();
        double mpBefore = bb.modelP();

        Map<String, Graph> candGraphByKey = new HashMap<>();
        List<ScoredCandidate> scored = new ArrayList<>();

        for (CandidateEdit cand : candidates) {
            if (stopRequested()) return List.of();

            Graph g2 = candGraphByKey.computeIfAbsent(cand.key(),
                    k -> buildCandidateGraph(base, cand));
            if (g2 == null) continue;
            if (violatesKnowledge(g2)) continue;

            Set<String> affected = affectedVertices(base, node, g2);
            int after = usesLocality()
                    ? evalGraphLocality(baseCache, g2, affected, false).violations()
                    : evalViolationsOnly(g2);

            scored.add(new ScoredCandidate(cand, baseline, after,
                    Double.NaN, Double.NaN, Double.NaN,
                    g2.getNumEdges(), true, Q.getAlpha()));
        }

        if (stopRequested()) return List.of();

        // Pick top-K for Model-P evaluation
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
        Map<String, Double> nodePAfterByKey = new HashMap<>();
        for (String key : keysToEval) {
            if (stopRequested()) return List.of();
            Graph g2 = candGraphByKey.get(key);
            if (g2 != null) {
                Set<String> affected = affectedVertices(base, node, g2);
                mpAfterByKey.put(key, evalModelPLocality(baseCache, g2, affected));
                nodePAfterByKey.put(key, nodePValue(g2, node));
            }
        }

        List<ScoredCandidate> result = new ArrayList<>(scored.size());
        for (ScoredCandidate sc : scored) {
            Double mpAfter = mpAfterByKey.get(sc.edit().key());
            Double nodePAfter = nodePAfterByKey.get(sc.edit().key());
            ScoredCandidate patched = new ScoredCandidate(
                    sc.edit(), sc.violationsBaseline(), sc.violationsAfter(),
                    (nodePAfter == null ? Double.NaN : nodePAfter), mpBefore,
                    (mpAfter == null ? Double.NaN : mpAfter),
                    sc.edgesAfter(), true, Q.getAlpha());

            boolean passes = !(patched.edit() == null || patched.edit().isNoOp());
            if (!passes && !patched.edit().isNoOp()) continue;

            result.add(new ScoredCandidate(
                    patched.edit(), patched.violationsBaseline(), patched.violationsAfter(),
                    patched.nodePAfter(), patched.modelPBefore(), patched.modelPAfter(),
                    patched.edgesAfter(), passes, Q.getAlpha()));
        }

        result.sort(CANONICAL_TABLE_ORDER);
        return result;
    }

    private ScoredCandidate evalModelPForEntry(QueueEntry entry) {
        BaselineBundle bb = baselineBundle();
        if (bb == null) return null;
        Graph base = bb.base();

        ScoredCandidate sc = entry.scored();
        CandidateEdit cand = sc.edit();

        Graph g2 = buildCandidateGraph(base, cand);
        if (g2 == null) return null;

        Node node = workingGraph.getNode(entry.nodeName());
        if (node == null) return null;

        GlobalEvalCache baseCache = bb.cache();
        Set<String> affected = affectedVertices(base, node, g2);

        double mpBefore = bb.modelP();
        double mpAfter = evalModelPLocality(baseCache, g2, affected);
        int baseline = bb.violations();
        int after = usesLocality()
                ? evalGraphLocality(baseCache, g2, affected, false).violations()
                : evalViolationsOnly(g2);

        // Under affected-only invalidation, a stale entry's node-P was computed against
        // an older graph state; refresh it so the applyWorthy comparison against the
        // fresh no-op is like-for-like all the way down the comparator tiers.
        double nodePAfter = (affectedOnlyInvalidation && entry.version() != graphVersion)
                ? nodePValue(g2, node)
                : sc.nodePAfter();

        return new ScoredCandidate(cand, baseline, after,
                nodePAfter, mpBefore, mpAfter, g2.getNumEdges(), true, Q.getAlpha());
    }

    private List<CandidateEdit> enumerateCandidates(Graph g, Node x) {
        if (g == null || x == null) return List.of(CandidateEdit.noOp());

        List<CandidateEdit> out = new ArrayList<>();
        out.add(CandidateEdit.noOp());

        List<Node> pool = new ArrayList<>(g.getNodes());
        pool.remove(x);
        RandomUtil.shuffle(pool);

        ArrayList<Edge> edges = new ArrayList<>(g.getEdges(x));
        RandomUtil.shuffle(edges);

        for (Edge e : edges) out.add(CandidateEdit.removeEdge(e));

        for (Edge e : edges) {
            Node y = e.getDistalNode(x);
            if (y == null) continue;
            for (Edge v : edgeMenuForPair(x, y)) {
                if (edgeStructurallyEqual(e, v, x, y)) continue;
                out.add(CandidateEdit.replaceEdge(e, v));
            }
        }

        for (Node y : pool) {
            if (y == null) continue;
            if (g.isAdjacentTo(x, y)) continue;
            for (Edge add : addMenuForPair(x, y)) out.add(CandidateEdit.addEdge(add));
        }

        if (graphType == AdjustmentGraphType.DAG
                || graphType == AdjustmentGraphType.CPDAG
                || graphType == AdjustmentGraphType.PDAG) {
            out.addAll(enumerateIncidentOrientationPatternMoves(g, x));
        }

        return dedupCandidateEdits(out);
    }

    // =========================================================================
    // Private: utilities
    // =========================================================================

    private List<CandidateEdit> enumerateIncidentOrientationPatternMoves(Graph g, Node x) {
        if (g == null || x == null) return List.of();

        List<Node> adj = new ArrayList<>(g.getAdjacentNodes(x));
        RandomUtil.shuffle(adj);

        List<Edge> freeEdges = new ArrayList<>();
        for (Node y : adj) {
            if (y == null) continue;
            Edge e = g.getEdge(x, y);
            if (e == null) continue;
            Endpoint ex = endpointAt(e, x);
            switch (graphType) {
                case DAG, CPDAG, PDAG -> {
                    if (ex == Endpoint.TAIL && endpointAt(e, y) == Endpoint.TAIL)
                        freeEdges.add(e);
                }
                case PAG -> {
                    if (ex == Endpoint.CIRCLE) freeEdges.add(e);
                }
                case MAG -> {
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
                if (graphType == AdjustmentGraphType.PAG) {
                    Endpoint eyKeep = endpointAt(old, y);
                    // Selection bias is excluded, so a tail at x is only compatible with
                    // an arrow at y: x --- y and x o-- y are inadmissible. Orienting the
                    // edge out of x therefore forces the arrowhead at y, rather than
                    // keeping y's existing circle or tail. Orienting into x can keep y's
                    // endpoint, since y o-> x, y <-> x and y --> x are all admissible.
                    // (Changed 2026-8-13; the previous code kept y's endpoint in both
                    // directions, silently emitting the selection-bias edge y o-- x for
                    // every o-o edge oriented out of x.)
                    ne = intoX
                            ? new Edge(y, x, eyKeep, Endpoint.ARROW)
                            : new Edge(y, x, Endpoint.ARROW, Endpoint.TAIL);
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
            RandomUtil.shuffle(parents);
            RandomUtil.shuffle(children);

            String label = "Orient incident edges at " + xName
                    + " | Pa={" + String.join(",", parents) + "}"
                    + " | Ch={" + String.join(",", children) + "}";
            out.add(CandidateEdit.replaceEdges(label, olds, news));
        }

        return out;
    }

    private List<Edge> edgeMenuForPair(Node x, Node y) {
        List<Edge> variants = new ArrayList<>();
        switch (graphType) {
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
                // Selection bias is excluded, so the admissible PAG edge types are
                // o-o, o->, --> and <->. The selection-bias-only types --- and o--
                // are deliberately not offered. (Changed 2026-8-13; --- was previously
                // offered while o-- was not, which was not a coherent restriction.)
                variants.add(new Edge(x, y, Endpoint.CIRCLE, Endpoint.CIRCLE));
                variants.add(new Edge(x, y, Endpoint.CIRCLE, Endpoint.ARROW));
                variants.add(new Edge(y, x, Endpoint.CIRCLE, Endpoint.ARROW));
                variants.add(new Edge(x, y, Endpoint.TAIL, Endpoint.ARROW));
                variants.add(new Edge(y, x, Endpoint.TAIL, Endpoint.ARROW));
                variants.add(new Edge(x, y, Endpoint.ARROW, Endpoint.ARROW));
            }
        }
        return variants;
    }

    private List<Edge> addMenuForPair(Node x, Node y) {
        List<Edge> adds = new ArrayList<>();
        switch (graphType) {
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
                // Selection bias excluded; see edgeMenuForPair. Admissible added edge
                // types are o-o, o->, --> and <->.
                adds.add(new Edge(x, y, Endpoint.CIRCLE, Endpoint.CIRCLE));
                adds.add(new Edge(x, y, Endpoint.CIRCLE, Endpoint.ARROW));
                adds.add(new Edge(y, x, Endpoint.CIRCLE, Endpoint.ARROW));
                adds.add(new Edge(x, y, Endpoint.TAIL, Endpoint.ARROW));
                adds.add(new Edge(y, x, Endpoint.TAIL, Endpoint.ARROW));
                adds.add(new Edge(x, y, Endpoint.ARROW, Endpoint.ARROW));
            }
        }
        return adds;
    }

    private Graph prepareBase() {
        Graph base = safeCopy(workingGraph);
        if (graphType == AdjustmentGraphType.CPDAG) {
            base = canonicalizeToCpdagOrNull(base);
        }
//        else if (graphType == AdjustmentGraphType.PAG) {
//            base = canonicalizeToPagOrNull(base);
//        }
        return base;
    }

    /**
     * Marks the memoized baseline bundle stale. Called whenever the working graph is
     * replaced or a setting that affects baseline evaluation changes.
     */
    private void bumpGraphVersion() {
        graphVersion++;
    }

    /**
     * Returns the baseline bundle for the current working-graph state, computing and
     * memoizing it if stale: the canonicalized base graph, the per-vertex Markov
     * contribution cache over that base, the baseline violation count, and the baseline
     * Model-P. All values are deterministic given the base graph (independence results
     * depend only on the data, and the wild bootstrap is seeded), so memoization is
     * exact. Returns {@code null} if canonicalization fails.
     */
    private BaselineBundle baselineBundle() {
        BaselineBundle bb = this.baselineBundle;
        if (bb != null && bb.version() == graphVersion) return bb;

        Graph base = prepareBase();
        if (base == null) {
            this.baselineBundle = null;
            return null;
        }

        GlobalEvalCache cache = buildBaselineCache(base);
        GraphEval eval = evalGraphLocality(cache, base, Set.of());
        double modelP = useWildBootstrap ? wildBootstrapModelP(base) : eval.modelP();

        bb = new BaselineBundle(base, cache, eval.violations(), modelP, graphVersion);
        this.baselineBundle = bb;
        return bb;
    }

    /**
     * Under affected-only invalidation, decides whether a freshly re-evaluated stale
     * queue entry still deserves application: the fresh candidate must beat a fresh
     * no-op for the current graph state under {@link #CANONICAL_TABLE_ORDER}. This is
     * the localized analogue of the head-of-list check that full invalidation gets for
     * free (the no-op is always in each node's candidate list, so under full
     * invalidation a non-no-op head has already beaten the no-op). Note the stale-entry
     * comparison here is better informed than the full-invalidation head check, since
     * both sides carry evaluated Model-P values rather than NaNs.
     */
    private boolean applyWorthy(ScoredCandidate fresh, String nodeName) {
        BaselineBundle bb = baselineBundle();
        if (bb == null) return false;

        Node node = bb.base().getNode(nodeName);
        double noOpNodeP = (node == null) ? Double.NaN : nodePValue(bb.base(), node);

        ScoredCandidate noOp = new ScoredCandidate(
                CandidateEdit.noOp(), bb.violations(), bb.violations(),
                noOpNodeP, bb.modelP(), bb.modelP(),
                bb.base().getNumEdges(), true, Q.getAlpha());

        return CANONICAL_TABLE_ORDER.compare(fresh, noOp) < 0;
    }

    private void applyCandidateInternal(CandidateEdit cand) {
        if (cand == null || cand.isNoOp()) return;

        vlog("Attempting move: %s", cand.description());

        Graph base = safeCopy(workingGraph);

        if (graphType == AdjustmentGraphType.CPDAG) {
            base = canonicalizeToCpdagOrNull(base);
            if (base == null) return;
        } else if (graphType == AdjustmentGraphType.PAG) {
            base = canonicalizeToPagOrNull(base);
            if (base == null) return;
        }

        Graph g2 = cand.applyTo(base);
        if (g2 == null) return;

        boolean intentAlreadyChecked = false;

        if (graphType == AdjustmentGraphType.CPDAG) {
            g2 = canonicalizeToCpdagOrNull(g2);
            if (g2 == null) return;
        } else if (graphType == AdjustmentGraphType.PAG) {
            // Project the applied graph back to canonical PAG form, checking the edit
            // was realized first; see buildCandidateGraph for the ordering rationale.
            // (Added 2026-8-13; previously the applied PAG was left in whatever form the
            // edit produced, so the working graph could drift out of canonical form.)
            if (requiresEdgePresenceCheck(cand) && !allIntendedNewEdgesPresent(g2, cand)) return;
            intentAlreadyChecked = true;
            g2 = canonicalizeToPagOrNull(g2);
            if (g2 == null) return;
        }

        if (g2.equals(base)) return;
        if (!intentAlreadyChecked
                && requiresEdgePresenceCheck(cand) && !allIntendedNewEdgesPresent(g2, cand)) return;

        workingGraph = g2;
        bumpGraphVersion();
        vlog("APPLIED successfully");
    }

    private GlobalEvalCache buildBaselineCache(Graph g) {
        if (g == null) return new GlobalEvalCache(Map.of());
        Map<String, VertexContribution> out = new HashMap<>();
        List<Node> nodes = g.getNodes();
        // Prepare the graph-level MAG transform once for all vertices of this graph
        // rather than once per vertex inside computeImpliedFactsForVertex. (Changed
        // from the pre-2026-8-13 implementation, which redid the legality checks and
        // CPDAG-to-DAG-to-MAG conversion for every vertex; the conversion is
        // deterministic, so the facts are identical.)
        Graph preparedMag = MarkovCheck.prepareMagForVertexFacts(g, type);
        for (Node v : nodes) {
            if (v == null) continue;
            out.put(v.getName(), evalVertexContribution(g, v, preparedMag));
        }
        return new GlobalEvalCache(out);
    }

    private VertexContribution evalVertexContribution(Graph g, Node vInGraph) {
        return evalVertexContribution(g, vInGraph, null);
    }

    /**
     * Per-vertex contribution with an optional MAG prepared once per graph via
     * {@link MarkovCheck#prepareMagForVertexFacts}. A null {@code preparedMag} behaves
     * exactly as before, preparing per call.
     */
    private VertexContribution evalVertexContribution(Graph g, Node vInGraph, Graph preparedMag) {
        if (g == null || vInGraph == null) return new VertexContribution(Map.of(), Map.of());
        Node v = g.getNode(vInGraph.getName());
        if (v == null) return new VertexContribution(Map.of(), Map.of());

        List<IndependenceFact> facts = MarkovCheck.computeImpliedFactsForVertex(g, v, type, preparedMag);
        if (facts.isEmpty()) return new VertexContribution(Map.of(), Map.of());

        Map<String, Boolean> viol = new LinkedHashMap<>();
        Map<String, Double> pByKey = new LinkedHashMap<>();

        for (IndependenceFact f : facts) {
            if (f == null) continue;
            String key = factKey(f);
            if (viol.containsKey(key)) continue;
            IndependenceResult r = checkIndependence(f);
            if (r == null) continue;
            viol.put(key, !r.isIndependent());
            double p = r.getPValue();
            if (!Double.isNaN(p) && p >= 0.0 && p <= 1.0) pByKey.put(key, p);
        }

        return new VertexContribution(viol, pByKey);
    }

    private GraphEval evalGraphLocality(GlobalEvalCache baseCache,
                                        Graph candidateGraph,
                                        Set<String> affectedVertexNames) {
        return evalGraphLocality(baseCache, candidateGraph, affectedVertexNames, true);
    }

    /**
     * Locality-aware graph evaluation. When {@code computeModelP} is false, the global
     * p-value map is not assembled and the uniformity (AD/KS) Model-P is not computed;
     * the returned eval carries {@code Double.NaN} for Model-P. Violation counting is
     * identical in both modes. Callers that immediately discard Model-P — the Pass-1
     * violation loops in {@code computeScoredCandidatesForNode(NoModelP)},
     * {@code scoreCandidates}, and {@code evalModelPForEntry} — use the false mode,
     * skipping an O(K log K) uniformity test per candidate over the K global implied
     * facts. (Changed from the pre-2026-8-13 implementation, which always computed
     * Model-P and discarded it at those call sites.)
     */
    private GraphEval evalGraphLocality(GlobalEvalCache baseCache,
                                        Graph candidateGraph,
                                        Set<String> affectedVertexNames,
                                        boolean computeModelP) {
        if (candidateGraph == null) return new GraphEval(0, Double.NaN, 0);

        Map<String, VertexContribution> contrib = new HashMap<>();
        if (baseCache != null && baseCache.contribByVertexName() != null) {
            contrib.putAll(baseCache.contribByVertexName());
        }

        if (affectedVertexNames != null && !affectedVertexNames.isEmpty()) {
            // Prepare the candidate graph's MAG transform once and share it across all
            // affected vertices, instead of redoing the legality checks and
            // CPDAG-to-DAG-to-MAG conversion per vertex. (Changed from the pre-2026-8-13
            // implementation; the conversion is deterministic, so the facts are identical.)
            Graph preparedMag = MarkovCheck.prepareMagForVertexFacts(candidateGraph, type);
            for (String name : affectedVertexNames) {
                if (name == null) continue;
                Node v = candidateGraph.getNode(name);
                if (v == null) {
                    contrib.remove(name);
                    continue;
                }
                contrib.put(name, evalVertexContribution(candidateGraph, v, preparedMag));
            }
        }

        Map<String, Boolean> globalViolationByKey = new HashMap<>();
        Map<String, Double> globalPByKey = new HashMap<>();

        // Iterate non-affected vertices first, then affected vertices, so that
        // fresh candidate-graph values (from affected vertex recomputation)
        // overwrite any stale values carried over from baseCache for shared facts.
        List<String> names = new ArrayList<>(contrib.keySet());
        Collections.sort(names);

        List<String> nonAffected = new ArrayList<>();
        List<String> affected = new ArrayList<>();
        Set<String> affectedSet = (affectedVertexNames == null)
                ? Set.of() : affectedVertexNames;
        for (String name : names) {
            if (affectedSet.contains(name)) affected.add(name);
            else nonAffected.add(name);
        }

        List<String> orderedNames = new ArrayList<>(nonAffected);
        orderedNames.addAll(affected);

        for (String name : orderedNames) {
            VertexContribution vc = contrib.get(name);
            if (vc == null) continue;

            // Use put (not putIfAbsent) so affected-vertex values win for shared facts.
            for (Map.Entry<String, Boolean> e : vc.violationByKey().entrySet()) {
                globalViolationByKey.put(e.getKey(), e.getValue());
            }
            if (computeModelP) {
                for (Map.Entry<String, Double> e : vc.pByKey().entrySet()) {
                    globalPByKey.put(e.getKey(), e.getValue());
                }
            }
        }

        int violations = 0;
        for (boolean isViol : globalViolationByKey.values()) if (isViol) violations++;

        double modelP = Double.NaN;
        if (computeModelP && globalPByKey.size() >= 2) {
            modelP = getUniformityP(new ArrayList<>(globalPByKey.values()));
        }

        return new GraphEval(violations, modelP, globalViolationByKey.size());
    }


    private double evalModelPLocality(GlobalEvalCache baseCache, Graph g, Set<String> affected) {
        if (useWildBootstrap) return wildBootstrapModelP(g);
        return evalGraphLocality(baseCache, g, affected).modelP();
    }

    /**
     * Whole-graph Model-P via the joint wild bootstrap. Unlike KS/AD this cannot be
     * reassembled from cached per-vertex p-values — the wild bootstrap is a joint
     * statistic over the full implied-fact set with a shared multiplier — so it is
     * recomputed from all implied facts of {@code g}. NaN if &lt;2 facts or the data is
     * not an all-continuous DataSet.
     */
    private double wildBootstrapModelP(Graph g) {
        if (g == null) return Double.NaN;
        Set<IndependenceFact> facts = MarkovCheck.computeAllImpliedFacts(g, type);
        if (facts == null || facts.size() < 2) return Double.NaN;
        return wildBootstrapP(new ArrayList<>(facts));
    }

    /**
     * Joint wild-bootstrap Markov check over an explicit fact list against the data set
     * backing the independence test; returns the max-|T| omnibus p-value (pSumSquares is
     * also on the Result). The max statistic is used because repair alternatives are
     * sparse — a single wrong edge perturbs only a few implied facts out of possibly
     * thousands — and the sum-T^2 statistic dilutes a sparse signal linearly in the
     * number of facts K: in simulation (one violated fact, shift d=0.2, rho=0.5, n=400)
     * pSumSquares' power collapses to its size (~0.03) by K=200 while pMax retains ~0.71
     * at K=1000. Residualization is OLS on Z, independent of the configured test, so the
     * data must be all-continuous; discrete or mixed data would be silently regressed on
     * category indices, so it is rejected here rather than mis-scored. NaN if the data is
     * not an all-continuous DataSet, there are &lt;2 facts, or the run is interrupted.
     */
    private double wildBootstrapP(List<IndependenceFact> facts) {
        if (facts == null || facts.size() < 2) return Double.NaN;
        if (Q == null || Q.getTest() == null) return Double.NaN;
        Object dm = Q.getTest().getData();
        if (!(dm instanceof DataSet ds)) return Double.NaN;
        if (!ds.isContinuous()) {
            vlog("Wild bootstrap Model-P requires an all-continuous data set (OLS "
                    + "residualization); data is discrete or mixed. Returning NaN.");
            return Double.NaN;
        }
        try {
            WildBootstrapMarkovCheck.Result r = new WildBootstrapMarkovCheck(ds)
                    .setNumBootstraps(wbNumBootstraps)
                    .setSeed(wbSeed)
                    .checkFacts(facts);
            return (r == null) ? Double.NaN : r.pMax;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Double.NaN;
        } catch (Exception e) {
            vlog("Wild bootstrap Model-P failed (%s: %s); returning NaN.",
                    e.getClass().getSimpleName(), e.getMessage());
            return Double.NaN;
        }
    }

    private int evalViolationsOnly(Graph g) {
        if (g == null) return 0;
        Set<IndependenceFact> facts = MarkovCheck.computeAllImpliedFacts(g, type);
        if (facts.isEmpty()) return 0;
        List<CachedIndependenceQueries.Eval> evals =
                Q.evalAll(facts, CachedIndependenceQueries.Dedup.BY_CACHE_KEY);
        int violations = 0;
        for (CachedIndependenceQueries.Eval e : evals) if (!e.independent()) violations++;
        return violations;
    }

    private double nodePValue(Graph g, Node vertexInOriginalGraph) {
        if (g == null || vertexInOriginalGraph == null) return Double.NaN;
        Node v = g.getNode(vertexInOriginalGraph.getName());
        if (v == null) return Double.NaN;
        List<IndependenceFact> facts = MarkovCheck.computeImpliedFactsForVertex(g, v, type);
        if (facts.isEmpty()) return Double.NaN;
        if (useWildBootstrap) return wildBootstrapP(facts);
        List<Double> pvals = Q.pValuesForFacts(facts, CachedIndependenceQueries.Dedup.WITHIN_INPUT);
        return getUniformityP(pvals);
    }

    private Graph buildCandidateGraph(Graph base, CandidateEdit cand) {
        if (base == null || cand == null) return null;
        Graph g2 = cand.applyTo(safeCopy(base));
        if (g2 == null) return null;
        if (graphType == AdjustmentGraphType.PAG) {
            // PAG candidates are projected back to canonical form, mirroring the CPDAG
            // branch below. (Added 2026-8-13; previously PAG candidates were only gated
            // on isLegalPag, so any edit whose consequences needed propagating by the
            // FCI orientation rules was discarded rather than completed.)
            //
            // The intended-edge check runs BEFORE projection here, unlike the CPDAG
            // branch, which runs it after. Rationale: the FCI rules routinely strengthen
            // an edited endpoint (o-> becomes --> when a tail is forced), and
            // containsStructuralEdge demands an exact endpoint match, so checking after
            // projection would discard precisely the orientation moves whose
            // consequences propagate -- the moves this change exists to enable. Checking
            // first asks the weaker and more apt question: was the edit actually
            // realized, before the rules were allowed to run? This is a contestable
            // choice, and it is deliberately NOT applied to the CPDAG branch, whose
            // check-after-canonicalization behavior is left exactly as it was.
            if (requiresEdgePresenceCheck(cand) && !allIntendedNewEdgesPresent(g2, cand)) return null;
            g2 = canonicalizeToPagOrNull(g2);
            if (g2 == null) return null;
            if (!cand.isNoOp() && g2.equals(base)) return null;
        } else {
            if (graphType == AdjustmentGraphType.CPDAG) {
                g2 = canonicalizeToCpdagOrNull(g2);
                if (g2 == null) return null;
                if (!cand.isNoOp() && g2.equals(base)) return null;
            }
            if (requiresEdgePresenceCheck(cand) && !allIntendedNewEdgesPresent(g2, cand)) return null;
        }
        try {
            if (graphType != null && !isLegalGraphType(g2)) return null;
        } catch (Exception ignored) {
            return null;
        }
        return g2;
    }

    private boolean isLegalGraphType(Graph g) {
        return switch (graphType) {
            case DAG -> g.paths().isLegalDag();
            case CPDAG -> g.paths().isLegalCpdag() || g.paths().isLegalPdag();
            case PDAG -> g.paths().isLegalPdag();
            case MAG -> g.paths().isLegalMag() && !hasSelectionBias(g);
            // The isLegalPag() conjunct is commented out as of 2026-8-13.
            // buildCandidateGraph now projects every PAG candidate through
            // canonicalizeToPagOrNull, which returns magToPag of a graph that has already
            // passed isLegalMag, so PAG legality holds by construction and the check is
            // redundant. It was also the only call on the candidate path with a
            // 20-second internal timeout (PagLegalityCheck), which under GC pressure or
            // on a slow machine could silently drop candidates and make the search
            // machine-dependent. Uncomment to restore the check if magToPag is ever
            // suspected of emitting a non-legal PAG.
            case PAG -> g.paths().isLegalPag() && !hasSelectionBias(g);
        };
    }

    /**
     * Returns true if {@code g} exhibits selection bias. Two things count: an explicit
     * selection node ({@link NodeType#SELECTION}), which is checked for every graph
     * type; and, for the ancestral types (MAG, PAG), an edge whose endpoints are
     * tail-tail ({@code ---}) or circle-tail ({@code o--}), which in a MAG or PAG arise
     * only under selection. The edge check is deliberately not applied to DAG, CPDAG or
     * PDAG, where a tail-tail edge is an ordinary unoriented edge and carries no
     * selection interpretation.
     *
     * <p>Selection bias is out of scope for repair as of 2026-8-13: the PAG and MAG edit
     * menus do not offer {@code ---} or {@code o--}, so no edit can introduce one, and
     * this predicate additionally rejects candidates and inputs that already contain
     * one. See {@link #search()}, which fails fast on such an input rather than
     * silently repairing around it.
     *
     * @param g the graph to test; null returns false
     * @return true if the graph exhibits selection bias
     */
    private boolean hasSelectionBias(Graph g) {
        return exhibitsSelectionBias(g, this.graphType);
    }

    /**
     * Returns true if {@code g} exhibits selection bias under {@code graphType}, the static form of
     * the predicate used internally by this search. Exposed (2026-8-13) so that user interfaces can
     * decline to offer an ancestral graph type for a graph carrying selection structure, rather than
     * offering it and letting {@link #search()} throw.
     *
     * @param g         the graph to test; null returns false
     * @param graphType the graph type under which to interpret the edges; null suppresses the edge
     *                  test and leaves only the selection-node test
     * @return true if the graph exhibits selection bias
     */
    public static boolean exhibitsSelectionBias(Graph g, AdjustmentGraphType graphType) {
        if (g == null) return false;

        for (Node n : g.getNodes()) {
            if (n != null && n.getNodeType() == NodeType.SELECTION) return true;
        }

        if (graphType != AdjustmentGraphType.MAG && graphType != AdjustmentGraphType.PAG) {
            return false;
        }

        for (Edge e : g.getEdges()) {
            if (e == null) continue;
            Endpoint e1 = e.getEndpoint1();
            Endpoint e2 = e.getEndpoint2();
            if (e1 == Endpoint.TAIL && e2 == Endpoint.TAIL) return true;
            if (e1 == Endpoint.TAIL && e2 == Endpoint.CIRCLE) return true;
            if (e1 == Endpoint.CIRCLE && e2 == Endpoint.TAIL) return true;
        }

        return false;
    }

    /**
     * Returns true if {@code g} is acceptable to this search as a PAG, given the background
     * knowledge currently set. (Added 2026-8-13.)
     *
     * <p>This is the predicate callers should use in place of {@link Paths#isLegalPag()} when
     * deciding whether a graph may be repaired as a PAG. Once knowledge is honored, the graphs this
     * search produces are knowledge-refined PAGs -- legal PAGs carrying the marks knowledge forces --
     * and such a graph is deliberately NOT strictly legal: {@code isLegalPag} reconstitutes the
     * class-canonical PAG, which has circles exactly where knowledge placed marks, and the equality
     * fails. A caller testing strict legality therefore rejects this search's own output, and in
     * particular rejects the output of a knowledge-aware *-FCI run.
     *
     * <p>A graph is accepted if it is strictly legal, or if it is legal modulo knowledge in the sense
     * of {@link #isPagModuloKnowledge(Graph)}. With empty knowledge the two coincide and
     * this reduces to the strict test.
     *
     * @param g the graph to test; null returns false
     * @return true if the graph may be repaired as a PAG under the current knowledge
     */
    public boolean isLegalPagGivenKnowledge(Graph g) {
        if (g == null) return false;

        if (this.knowledge != null && !this.knowledge.isEmpty()) {
            try {
                if (isPagModuloKnowledge(g)) return true;
            } catch (Throwable ignored) {
                // fall through to the strict test
            }
        }

        try {
            return g.paths().isLegalPag();
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean usesLocality() {
        return graphType == AdjustmentGraphType.DAG
                || graphType == AdjustmentGraphType.CPDAG
                || graphType == AdjustmentGraphType.PDAG;
    }

    private Graph canonicalizeToCpdagOrNull(Graph h) {
        if (h == null) return null;
        try {
            Graph h2 = new EdgeListGraph(h);
            if (h2.paths().isLegalDag()) return applyKnowledgeOrientations(GraphTransforms.dagToCpdag(h2));
            if (h2.paths().isLegalCpdag() || h2.paths().isLegalPdag()) {
                Graph dag = GraphTransforms.dagFromCpdag(h2);
                return applyKnowledgeOrientations(GraphTransforms.dagToCpdag(dag));
            }
            Graph seed = seedDagFromAnyGraph(h2);
            if (seed == null) return null;
            return applyKnowledgeOrientations(GraphTransforms.dagToCpdag(seed));
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Restores background-knowledge-compelled orientations after CPDAG compilation, returning a
     * knowledge-consistent PDAG in the same Markov equivalence class. (Change from the pre-2026-8-12
     * behavior, which returned the raw CPDAG.)
     *
     * <p>{@link GraphTransforms#dagToCpdag} is knowledge-blind: it un-orients every edge whose
     * direction is not compelled by unshielded colliders and the Meek rules. A graph whose
     * orientations were forced by background knowledge (tiers, forbidden edges) therefore comes back
     * under-oriented, and {@link #violatesKnowledge}, which deliberately rejects under-oriented
     * edges, then rejects the canonicalized base graph itself. Since {@code searchForNode} returns no
     * candidates when the base violates knowledge, repair with tier knowledge was a guaranteed no-op
     * whenever the knowledge compelled any orientation the Meek rules did not.
     *
     * <p>Here each undirected edge with a required orientation, or exactly one forbidden
     * orientation, is compiled to the knowledge-determined direction, and the Meek rules are then
     * run with knowledge (without reverting to unshielded colliders, so the restored orientations
     * persist) to close under the orientation rules. Undirected edges both of whose orientations are
     * forbidden are left for {@link Knowledge#isViolatedBy(Graph)} to reject, unchanged from the
     * previous behavior.
     */
    private Graph applyKnowledgeOrientations(Graph g) {
        if (g == null || this.knowledge == null || this.knowledge.isEmpty()) return g;

        for (Edge edge : new ArrayList<>(g.getEdges())) {
            if (!Edges.isUndirectedEdge(edge)) continue;

            Node a = edge.getNode1();
            Node b = edge.getNode2();
            String an = a.getName();
            String bn = b.getName();

            boolean abRequired = this.knowledge.isRequired(an, bn);
            boolean baRequired = this.knowledge.isRequired(bn, an);
            boolean abForbidden = this.knowledge.isForbidden(an, bn);
            boolean baForbidden = this.knowledge.isForbidden(bn, an);

            if (abRequired && !baRequired) {
                g.removeEdge(edge);
                g.addDirectedEdge(a, b);
            } else if (baRequired && !abRequired) {
                g.removeEdge(edge);
                g.addDirectedEdge(b, a);
            } else if (abForbidden != baForbidden) {
                g.removeEdge(edge);
                if (abForbidden) {
                    g.addDirectedEdge(b, a);
                } else {
                    g.addDirectedEdge(a, b);
                }
            }
        }

        MeekRules meek = new MeekRules();
        meek.setKnowledge(this.knowledge);
        meek.setRevertToUnshieldedColliders(false);
        meek.setVerbose(false);
        meek.orientImplied(g);

        return g;
    }

    private Graph canonicalizeToPagOrNull(Graph h) {
        try {
            Graph h2 = new EdgeListGraph(h);
            Graph mag = GraphTransforms.zhangMagFromPag(h2);
            if (!mag.paths().isLegalMag()) return null;
            return applyKnowledgePagOrientations(GraphTransforms.magToPag(mag, false, 15), mag);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Restores background-knowledge-forced orientations after PAG compilation, the ancestral-graph
     * analogue of {@link #applyKnowledgeOrientations(Graph)}. (Added 2026-8-13.)
     *
     * <p>The problem is the same one {@code applyKnowledgeOrientations} solves for CPDAGs.
     * {@link GraphTransforms#magToPag} is knowledge-blind: it returns the canonical PAG of the
     * equivalence class, in which every mark not forced by the class itself is a circle. Marks that
     * background knowledge had forced (tiers, required edges) are therefore erased on every
     * canonicalization, so with PAG canonicalization in place repair under knowledge would steadily
     * undo the knowledge rather than respect it.
     *
     * <p>The mechanism follows the one adopted for the Star-FCI family in
     * {@code StarFciKeepKnowledgeOrientations.refineWithKnowledge}: apply
     * {@link FciOrient#fciOrientbk} for the required/forbidden-edge marks, then close under the
     * complete FCI final rules with {@link FciOrient#finalOrientation} so the restored marks
     * propagate (R1-R4). {@code excludeSelectionBias} is passed true throughout, consistent with the
     * exclusion of selection bias adopted 2026-8-13. When knowledge is empty this is the identity
     * and costs nothing.
     *
     * <p>OUTPUT CONTRACT, and a change in what "canonical" means here. The returned graph is a legal
     * PAG <em>refined by background knowledge</em>: it is generally NOT the canonical PAG of the
     * equivalence class, but denotes the subset of that class consistent with the knowledge. This
     * matches the Star-FCI output contract, and it is the only coherent option once knowledge is
     * honored at all -- but callers that assume the working graph is the class-canonical PAG should
     * be aware that it is only so when knowledge is empty.
     *
     * <p>CONFLICT POLICY (contestable, so called out). When knowledge conflicts with the
     * data-derived invariant marks, the refined graph will fail to be recoverable as the
     * knowledge-refined canonical PAG of its own implied MAG. Here knowledge loses: the unrefined
     * canonical PAG is returned and the candidate proceeds without the knowledge marks. This is the
     * same conservative fallback {@code StarFciKeepKnowledgeOrientations} adopts. The alternative --
     * forcing knowledge through and repairing around it -- is a different and more invasive policy.
     */
    private Graph applyKnowledgePagOrientations(Graph pag, Graph mag) {
        if (pag == null || this.knowledge == null || this.knowledge.isEmpty()) return pag;

        try {
            FciOrient orient = buildKnowledgeFciOrient(mag);
            Graph refined = refinePagWithKnowledge(pag, orient);
            if (refined.equals(pag)) return pag;
            if (!isPagModuloKnowledge(refined)) return pag;
            return refined;
        } catch (Throwable t) {
            return pag;
        }
    }

    /**
     * Builds an FCI orientation engine for knowledge refinement, bound to m-separation in
     * {@code mag} rather than to the data.
     *
     * <p>(Changed 2026-8-13; the previous version used
     * {@code R0R4StrategyTestBased.specialConfiguration(Q, knowledge, false)}, i.e. the data test.
     * That was wrong. {@link GraphTransforms#magToPag} orients using {@code MsepTest} on the MAG --
     * a purely graph-based engine -- so refining its output with a data-based engine mixed two
     * different notions of separation. On simulated data drawn from the very DAG the PAG came from
     * the two agree and nothing looks amiss; on real data, R4's discriminating-path resolution
     * disagrees, the refinement picks up marks the reconstituted graph does not, and
     * {@link #isPagModuloKnowledge} then rejects perfectly good knowledge-refined PAGs. It also made
     * refinement a function of the data rather than of the graph and knowledge alone.)
     *
     * <p>This uses the same strategy {@code MagToPag} itself uses, so refining a {@code magToPag}
     * output is a fixed point when knowledge is empty and adds exactly the knowledge-forced marks
     * and their closure otherwise.
     */
    private FciOrient buildKnowledgeFciOrient(Graph mag) {
        Map<Node, Set<Node>> ancestorCache = new LinkedHashMap<>();
        for (Node n : mag.getNodes()) {
            ancestorCache.put(n, new LinkedHashSet<>(mag.paths().getAncestors(n)));
        }

        FciOrient orient = new FciOrient(
                MagToPag.getFinalStrategyUsingDsep(mag, this.knowledge, false, ancestorCache));
        orient.setKnowledge(this.knowledge);
        orient.setCompleteRuleSetUsed(true);
        orient.setUseR4(true);
        orient.setVerbose(false);
        return orient;
    }

    /**
     * Applies the knowledge marks and closes under the complete FCI final rules, on a copy. Can only
     * add arrow/tail marks to a graph already closed under the final rules; a no-op on a graph that
     * is already a knowledge-refined fixed point.
     */
    private Graph refinePagWithKnowledge(Graph pag, FciOrient orient) throws InterruptedException {
        Graph g = pag.copy();
        orient.fciOrientbk(this.knowledge, g, g.getNodes(), true);
        orient.finalOrientation(g, true);
        return g;
    }

    /**
     * The acceptance certificate for a knowledge-refined PAG: legal PAG <em>modulo knowledge</em>.
     * The implied MAG must be a legal MAG, and the graph must be recoverable as the
     * knowledge-refined canonical PAG of that MAG. This replaces the strict round-trip equality that
     * a knowledge-refined graph necessarily fails (it carries marks the class alone does not force)
     * with equality after the same refinement is applied to the reconstituted canonical PAG. A graph
     * that fails this carries marks forced neither by the equivalence class nor by knowledge, which
     * is the signature of a knowledge/data conflict.
     *
     * <p>This also restores the safety the commented-out {@code isLegalPag} check used to provide on
     * the candidate path. That check was dropped because {@code magToPag} output is a legal PAG by
     * construction; refining with knowledge afterwards breaks that argument, so the refined result
     * is certified here rather than taken on faith. The cost is paid only when knowledge is
     * non-empty and the refinement actually changed something.
     */
    private boolean isPagModuloKnowledge(Graph refined) throws InterruptedException {
        Graph mag = GraphTransforms.zhangMagFromPag(refined);
        if (!mag.paths().isLegalMag()) return false;
        Graph reconstituted = GraphTransforms.magToPag(mag, false, 15);
        return refined.equals(refinePagWithKnowledge(reconstituted, buildKnowledgeFciOrient(mag)));
    }

    private IndependenceResult checkIndependence(IndependenceFact f) {
        if (stopRequested()) return null;
        if (f == null || Q == null) return null;
        Set<Node> z = new LinkedHashSet<>(f.getZ());
        try {
            return Q.checkIndependence(f.getX(), f.getY(), z);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    // =========================================================================
    // Event firing
    // =========================================================================

    private boolean stopRequested() {
        return cancelRequested || Thread.currentThread().isInterrupted();
    }

    private Graph safeCopy(Graph g) {
        if (g == null) return null;
        try {
            return g.copy();
        } catch (Throwable t) {
            return new EdgeListGraph(g);
        }
    }

    /**
     * Computes the set of vertex names whose candidate lists must be recomputed after
     * an applied edit, for affected-only invalidation. This is deliberately broader
     * than {@link #affectedVertices(Graph, Node, Graph)} (which diffs only the edited
     * node's adjacency): CPDAG canonicalization can reorient edges far from the edit
     * via Meek propagation, and Markov-blanket conditioning reaches spouses. So this
     * takes the endpoints of every edge in the symmetric difference of the before/after
     * edge sets, closed under one hop of adjacency in the after graph, plus the edited
     * node and the edit's own endpoints. Any residual staleness (e.g., under the
     * ordered-local-Markov conditioning types, whose per-vertex facts depend on a
     * whole-graph MAG transform) is caught by the lazy revalidation at poll time and
     * the verification sweep at queue drain, so this set affects only the greedy path,
     * never the convergence guarantee.
     */
    /**
     * Canonical structural key for an edge: the two (name, endpoint-at-that-node) pairs
     * sorted by node name, so that structurally identical edges from different graphs
     * (or with reversed node1/node2 storage order) produce the same key.
     */
    private static String structuralEdgeKey(Edge e) {
        if (e == null) return "null";
        String a = (e.getNode1() == null || e.getNode1().getName() == null) ? "?" : e.getNode1().getName();
        String b = (e.getNode2() == null || e.getNode2().getName() == null) ? "?" : e.getNode2().getName();
        Endpoint ea = e.getEndpoint1();
        Endpoint eb = e.getEndpoint2();
        if (a.compareTo(b) <= 0) {
            return a + "[" + ea + "]--[" + eb + "]" + b;
        } else {
            return b + "[" + eb + "]--[" + ea + "]" + a;
        }
    }

    private Set<String> invalidationSet(Graph before, Graph after,
                                        String editedNodeName, CandidateEdit edit) {
        Set<String> out = new LinkedHashSet<>();
        if (editedNodeName != null) out.add(editedNodeName);

        if (edit != null && edit.getEdge() != null) {
            Node n1 = edit.getEdge().getNode1(), n2 = edit.getEdge().getNode2();
            if (n1 != null && n1.getName() != null) out.add(n1.getName());
            if (n2 != null && n2.getName() != null) out.add(n2.getName());
        }

        if (before != null && after != null) {
            Set<String> beforeKeys = new HashSet<>();
            for (Edge e : before.getEdges()) beforeKeys.add(structuralEdgeKey(e));
            Set<String> afterKeys = new HashSet<>();
            for (Edge e : after.getEdges()) afterKeys.add(structuralEdgeKey(e));

            Set<Node> touched = new LinkedHashSet<>();
            for (Edge e : before.getEdges()) {
                if (!afterKeys.contains(structuralEdgeKey(e))) {
                    touched.add(e.getNode1());
                    touched.add(e.getNode2());
                }
            }
            for (Edge e : after.getEdges()) {
                if (!beforeKeys.contains(structuralEdgeKey(e))) {
                    touched.add(e.getNode1());
                    touched.add(e.getNode2());
                }
            }

            for (Node n : touched) {
                if (n == null || n.getName() == null) continue;
                out.add(n.getName());
                Node inAfter = after.getNode(n.getName());
                if (inAfter != null) {
                    for (Node adj : after.getAdjacentNodes(inAfter)) {
                        if (adj != null && adj.getName() != null) out.add(adj.getName());
                    }
                }
            }
        }

        out.removeIf(name -> after == null || after.getNode(name) == null);
        return out;
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

    private void fireStatus(String message) {
        for (RepairListener l : listeners) l.statusUpdated(message);
    }

    private void fireEditApplied(CandidateEdit edit, Graph currentGraph) {
        for (RepairListener l : listeners) l.editApplied(edit, currentGraph);
    }

    private void fireRepairConverged(int totalEdits, String message) {
        for (RepairListener l : listeners) l.repairConverged(totalEdits, message);
    }

    /**
     * Sets whether to use the Anderson-Darling test during the repair process.
     * The Anderson-Darling test evaluates the goodness-of-fit for a distribution
     * and can influence decision-making in the repair strategy. If not, the
     * Kolmogorov-Smirnov test is used instead. The default is to use the
     * Kolmogorov-Smirnov test (false).
     *
     * @param useAndersonDarling true to enable the Anderson-Darling test, false to disable it
     */
    public void setUseAndersonDarling(boolean useAndersonDarling) {
        this.useAndersonDarling = useAndersonDarling;
        bumpGraphVersion();
    }

    /**
     * When true, Model-P and Node-P are the joint wild-bootstrap omnibus (sum T^2)
     * rather than KS/AD on the pooled p-values. Takes precedence over Anderson-Darling.
     * Requires the backing data to be a continuous DataSet (OLS residualization).
     *
     * @param useWildBootstrap true to use the joint wild bootstrap, false to use KS/AD
     */
    public void setUseWildBootstrap(boolean useWildBootstrap) {
        this.useWildBootstrap = useWildBootstrap;
        bumpGraphVersion();
    }

    /**
     * Sets the number of bootstrap replicates used when {@link #setUseWildBootstrap(boolean)}
     * is enabled. Ignored otherwise. The default is 1000.
     *
     * @param wbNumBootstraps the number of replicates; the smallest attainable p-value is
     *                        1 / (wbNumBootstraps + 1)
     */
    public void setWbNumBootstraps(int wbNumBootstraps) {
        this.wbNumBootstraps = wbNumBootstraps;
        bumpGraphVersion();
    }

    /**
     * Sets the random seed for the wild bootstrap, for reproducibility. Ignored unless
     * {@link #setUseWildBootstrap(boolean)} is enabled. The default is 0. This seed is
     * separate from the node-traversal seed set by {@link #setSeed(long)}.
     *
     * @param wbSeed the seed for the bootstrap multipliers
     */
    public void setWbSeed(long wbSeed) {
        this.wbSeed = wbSeed;
        bumpGraphVersion();
    }

    /**
     * Sets the prune alpha value, which must be within the range [0, 1].
     * The prune alpha is used to control pruning false positice edges froem the starting graph.
     *
     * @param pruneAlpha the prune alpha value; must be between 0 and 1 (inclusive)
     * @throws IllegalArgumentException if the prune alpha value is outside the valid range
     */
    public void setPruneAlpha(double pruneAlpha) {
        if (pruneAlpha < 0.0 || pruneAlpha > 1.0) {
            throw new IllegalArgumentException("Prune alpha must be between 0 and 1: " + pruneAlpha);
        }
        this.pruneAlpha = pruneAlpha;
    }

    /**
     * Calculates the uniformity p-value of a given list of p-values using either the Anderson-Darling
     * test or the Kolmogorov-Smirnov test based on the configured method.
     *
     * @param pvals a list of p-values to evaluate. The list must not be null, and should contain at least two elements.
     * @return the calculated uniformity p-value, or Double.NaN if the input list is null or contains fewer than two elements.
     */
    public double getUniformityP(List<Double> pvals) {
        if (pvals == null || pvals.size() < 2) return Double.NaN;

        if (useAndersonDarling) {
            return getAndersonDarlingP(pvals);
        } else {
            return getKolomogorovP(pvals);
        }
    }

    // ---- Records (package-accessible for use in the panel) -------------------

    /**
     * Searches for a separating set for x and y among subsets of their
     * combined neighbors in g, up to the given depth. Returns the sepset
     * if found, null otherwise.
     */
    private Set<Node> findSepset(Graph g, Node x, Node y, int maxDepth) {
        Set<Node> candidates = new LinkedHashSet<>();
        for (Node n : g.getAdjacentNodes(x)) if (!n.equals(y)) candidates.add(n);
        for (Node n : g.getAdjacentNodes(y)) if (!n.equals(x)) candidates.add(n);

        List<Node> candList = new ArrayList<>(candidates);
        int depth = Math.min(maxDepth, candList.size());

        for (int size = 0; size <= depth; size++) {
            int[] indices = new int[size];
            for (int i = 0; i < size; i++) indices[i] = i;

            while (true) {
                Set<Node> z = new LinkedHashSet<>();
                for (int idx : indices) z.add(candList.get(idx));

                IndependenceResult result = checkIndependenceAtAlpha(x, y, z, pruneAlpha);
                if (result != null && result.isIndependent()) return z;

                int i = size - 1;
                while (i >= 0 && indices[i] == candList.size() - size + i) i--;
                if (i < 0) break;
                indices[i]++;
                for (int j = i + 1; j < size; j++) indices[j] = indices[j - 1] + 1;
            }
        }
        return null;
    }

    private IndependenceResult checkIndependenceAtAlpha(Node x, Node y, Set<Node> z, double alpha) {
        if (stopRequested()) return null;
        try {
            IndependenceResult r = Q.checkIndependence(x, y, new LinkedHashSet<>(z));
            double p = r.getPValue();
            if (Double.isNaN(p)) return r;
            // Construct a new result with isIndependent() reflecting pruneAlpha,
            // preserving the original p-value and score.
            return new IndependenceResult(r.getFact(), p > alpha, p, r.getScore());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Removes edges from g whose endpoints are d-separated by some subset
     * of their neighbors. This targets false-positive edges that the starting
     * algorithm left in. Modifies g in place.
     */
    private void pruneObviousFalsePositives(Graph g, int maxDepth) {
        List<Edge> edges = new ArrayList<>(g.getEdges());
        RandomUtil.shuffle(edges);
        for (Edge e : edges) {
            if (stopRequested()) return;
            Node x = e.getNode1();
            Node y = e.getNode2();
            if (!g.isAdjacentTo(x, y)) continue;
            Set<Node> sep = findSepset(g, x, y, maxDepth);
            if (sep != null) {
                g.removeEdge(g.getEdge(x, y));
                fireStatus("Pruning false-positive edge " + x.getName() + " -- " + y.getName());
                vlog("Pruned false-positive edge %s -- %s | %s", x.getName(), y.getName(), sep);
            }
        }

        if (graphType == AdjustmentGraphType.CPDAG) {
            Graph canonicalized = canonicalizeToCpdagOrNull(g);
            if (canonicalized != null) {
                // Only assign back to workingGraph if g IS workingGraph
                if (g == workingGraph) workingGraph = canonicalized;
            } else {
                fireStatus("Warning: canonicalization after pruning failed, continuing with pruned graph.");
            }
        } else if (graphType == AdjustmentGraphType.PAG) {
            Graph canonicalized = canonicalizeToPagOrNull(g);
            if (canonicalized != null) {
                if (g == workingGraph) workingGraph = canonicalized;
            } else {
                fireStatus("Warning: canonicalization after pruning failed, continuing with pruned graph.");
            }
        }
    }

    /**
     * Configures the verbosity level for logging output.
     *
     * @param verbose a boolean flag where {@code true} enables verbose logging
     *                and {@code false} disables it.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Graph types supported by the repair search.
     */
    public enum AdjustmentGraphType {

        /**
         * A completed partially directed acyclic graph: the Markov-equivalence class of a DAG,
         * with compelled edges directed and the rest left undirected. Candidate graphs are
         * canonicalized back to a CPDAG after every edit.
         */
        CPDAG,

        /**
         * A partially directed acyclic graph: directed and undirected edges, no directed cycle.
         * Unlike {@link #CPDAG}, no canonicalization is applied, so the orientation of
         * non-compelled edges is preserved as edited.
         */
        PDAG,

        /**
         * A partial ancestral graph: the equivalence class of a MAG, admitting latent confounders
         * and using circle endpoints for undetermined orientations. Candidate graphs are
         * canonicalized back to a PAG after every edit.
         */
        PAG,

        /**
         * A directed acyclic graph: every edge is directed and there is no directed cycle.
         * Assumes causal sufficiency.
         */
        DAG,

        /**
         * A maximal ancestral graph: directed and bidirected edges, admitting latent confounders,
         * with an edge between every pair of vertices not m-separated by any set.
         */
        MAG
    }

    /**
     * Which repair sweep strategy to use.
     */
    public enum RepairStrategy {

        /**
         * Represents the local sweep strategy for repairs.
         * This strategy focuses on performing repairs in a localized manner,
         * targeting specific areas or regions as needed.
         */
        LOCAL_SWEEP("Local sweep"),

        /**
         * Represents the global queue strategy for repairs.
         * This strategy uses a global queue to manage and coordinate the repair process.
         */
        GLOBAL_QUEUE("Global queue");

        private final String label;

        RepairStrategy(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    enum MoveType {REORIENT_SIMPLE, REMOVE_EDGE, ADD_EDGE, OTHER}

    // ---- Static edge helpers (kept package-private for potential reuse) -------

    /**
     * Callback interface for repair-progress events fired by {@link VertexRepairSearch}.
     *
     * <p>All methods have default no-op implementations so implementors only need
     * to override the events they care about.
     */
    public interface RepairListener {

        /**
         * Called whenever the search emits a human-readable status message.
         *
         * @param message the status string (never null)
         */
        default void statusUpdated(String message) {
        }

        /**
         * Called each time a candidate edit is successfully applied to the graph.
         *
         * @param edit         the edit that was applied (never null)
         * @param currentGraph the graph state after applying the edit (a safe copy)
         */
        default void editApplied(CandidateEdit edit, Graph currentGraph) {
        }

        /**
         * Called when the repair terminates normally (convergence or empty queue).
         *
         * @param totalEdits number of edits applied during this run
         * @param message    human-readable summary
         */
        default void repairConverged(int totalEdits, String message) {
        }
    }

    /**
     * A single proposed graph modification, together with metadata needed for
     * display and deduplication.
     */
    public interface CandidateEdit {

        /**
         * Creates a no-operation CandidateEdit, indicating no changes to be applied to a graph.
         *
         * @return a CandidateEdit instance representing a no-op operation, with a fixed description,
         * key, and behavior that does not modify the input graph.
         */
        static CandidateEdit noOp() {
            return new CandidateEdit() {

                @Override
                public MoveType moveType() {
                    return MoveType.OTHER;
                }

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

        /**
         * Creates a {@code CandidateEdit} operation to add the specified edge to a graph.
         * This operation applies a change to the graph by adding the edge, ensuring it does not already exist
         * between the specified nodes. If the edge cannot be added due to conflicts (e.g., already adjacent nodes),
         * the operation will be invalid.
         *
         * @param edgeToAdd the edge to be added to the graph; must not be null.
         * @return a {@code CandidateEdit} instance representing the addition of the edge,
         * or null if the edge cannot be added to the graph.
         */
        static CandidateEdit addEdge(Edge edgeToAdd) {
            Objects.requireNonNull(edgeToAdd, "edgeToAdd");
            return new CandidateEdit() {
                @Override
                public MoveType moveType() {
                    return MoveType.ADD_EDGE;
                }

                @Override
                public String description() {
                    return "Add edge " + edgeToAdd;
                }

                @Override
                public Graph applyTo(Graph g) {
                    Graph g2 = new EdgeListGraph(g);
                    Edge rebound = rebindEdgeToGraph(g2, edgeToAdd);
                    if (rebound == null) return null;
                    if (g2.isAdjacentTo(rebound.getNode1(), rebound.getNode2())) return null;
                    g2.addEdge(rebound);
                    return g2;
                }

                @Override
                public boolean isNoOp() {
                    return false;
                }

                @Override
                public String key() {
                    return "ADD:" + edgeToAdd;
                }

                @Override
                public Edge getEdge() {
                    return edgeToAdd;
                }
            };
        }

        /**
         * Creates a {@code CandidateEdit} operation to remove the specified edge from a graph.
         * This operation applies a change to the graph by removing the edge if it exists.
         * If the edge does not exist in the graph, the operation will return null when applied.
         *
         * @param edgeToRemove the edge to be removed from the graph; must not be null.
         * @return a {@code CandidateEdit} instance representing the removal of the edge
         * from the graph, or null if the operation cannot be applied.
         */
        static CandidateEdit removeEdge(Edge edgeToRemove) {
            Objects.requireNonNull(edgeToRemove, "edgeToRemove");

            return new CandidateEdit() {
                @Override
                public MoveType moveType() {
                    return MoveType.REMOVE_EDGE;
                }

                @Override
                public String description() {
                    return "Remove edge " + edgeToRemove;
                }

                @Override
                public Graph applyTo(Graph g) {
                    Graph g2 = new EdgeListGraph(g);
                    Edge existing = getEdgeByNames(g2, edgeToRemove);
                    if (existing == null) return null;
                    g2.removeEdge(existing);
                    return g2;
                }

                @Override
                public boolean isNoOp() {
                    return false;
                }

                @Override
                public String key() {
                    return "REM:" + edgeToRemove;
                }

                @Override
                public Edge getEdge() {
                    return edgeToRemove;
                }
            };
        }

        /**
         * Replaces an existing edge in a graph with a new edge, creating a candidate edit operation.
         *
         * @param edgeToRemove the edge to be removed from the graph; must not be null.
         * @param edgeToAdd    the edge to be added to the graph; must not be null.
         * @return a {@code CandidateEdit} representing the operation of replacing the specified edge with the new edge.
         * Returns null if the replacement cannot be applied to the graph.
         */
        static CandidateEdit replaceEdge(Edge edgeToRemove, Edge edgeToAdd) {
            Objects.requireNonNull(edgeToRemove, "edgeToRemove");
            Objects.requireNonNull(edgeToAdd, "edgeToAdd");
            return new CandidateEdit() {

                @Override
                public MoveType moveType() {
                    return MoveType.REORIENT_SIMPLE;
                }

                /**
                 * Provides a textual description of the operation to replace an existing edge in the graph
                 * with a new edge.
                 *
                 * @return a string detailing the replacement operation in the format "Replace edgeToRemove → edgeToAdd".
                 */
                @Override
                public String description() {
                    return "Replace " + edgeToRemove + " → " + edgeToAdd;
                }

                /**
                 * Applies the modification to the given graph by replacing an existing edge
                 * with a new edge. If the modification cannot be successfully applied, returns null.
                 *
                 * @param g the graph to which the modification will be applied; must not be null.
                 * @return a new modified graph after applying the edge replacement, or null if the replacement fails.
                 */
                @Override
                public Graph applyTo(Graph g) {
                    Graph g2 = new EdgeListGraph(g);
                    Edge existing = getEdgeByNames(g2, edgeToRemove);
                    if (existing != null) g2.removeEdge(existing);
                    Edge rebound = rebindEdgeToGraph(g2, edgeToAdd);
                    if (rebound == null) return null;
                    if (!g2.isAdjacentTo(rebound.getNode1(), rebound.getNode2())) {
                        g2.addEdge(rebound);
                    } else {
                        Edge cur = g2.getEdge(rebound.getNode1(), rebound.getNode2());
                        if (cur != null) g2.removeEdge(cur);
                        g2.addEdge(rebound);
                    }
                    return g2;
                }

                /**
                 * Indicates whether this edit operation performs no modifications.
                 *
                 * @return {@code true} if the operation does not modify the graph; {@code false} otherwise.
                 */
                @Override
                public boolean isNoOp() {
                    return false;
                }

                @Override
                public String key() {
                    return "REP:" + edgeToRemove + "→" + edgeToAdd;
                }

                /**
                 * Retrieves the edge that is being added as part of this edit operation.
                 *
                 * @return the edge to be added to the graph.
                 */
                @Override
                public Edge getEdge() {
                    return edgeToAdd;
                }
            };
        }

        /**
         * Creates a {@code CandidateEdit} operation to replace multiple edges in a graph.
         * This operation applies the specified changes by first removing the edges in the
         * provided removal list and then adding the edges in the addition list.
         *
         * @param label         a descriptive label for the edit operation; must not be null.
         * @param edgesToRemove a list of edges to be removed from the graph; must not be null.
         * @param edgesToAdd    a list of edges to be added to the graph; must not be null.
         * @return a {@code CandidateEdit} instance representing the specified replacement
         * operation. The returned instance includes the provided label and applies
         * the removal and addition of edges as specified.
         */
        static CandidateEdit replaceEdges(String label, List<Edge> edgesToRemove, List<Edge> edgesToAdd) {
            Objects.requireNonNull(label);
            List<Edge> rem = List.copyOf(Objects.requireNonNull(edgesToRemove));
            List<Edge> add = List.copyOf(Objects.requireNonNull(edgesToAdd));
            return new CandidateEdit() {

                @Override
                public MoveType moveType() {
                    return MoveType.REORIENT_SIMPLE;
                }

                @Override
                public String description() {
                    return label;
                }

                @Override
                public Graph applyTo(Graph g) {
                    Graph g2 = new EdgeListGraph(g);
                    for (Edge e : rem) {
                        Edge ex = getEdgeByNames(g2, e);
                        if (ex != null) g2.removeEdge(ex);
                    }
                    for (Edge e : add) {
                        Edge rebound = rebindEdgeToGraph(g2, e);
                        if (rebound == null) continue;
                        if (!g2.isAdjacentTo(rebound.getNode1(), rebound.getNode2())) {
                            g2.addEdge(rebound);
                        } else {
                            Edge cur = g2.getEdge(rebound.getNode1(), rebound.getNode2());
                            if (cur != null) g2.removeEdge(cur);
                            g2.addEdge(rebound);
                        }
                    }
                    return g2;
                }

                @Override
                public boolean isNoOp() {
                    return false;
                }

                @Override
                public String key() {
                    return "MREPL:" + label;
                }

                @Override
                public Edge getEdge() {
                    return add.isEmpty() ? null : add.getFirst();
                }

                @Override
                public List<Edge> getEdges() {
                    return add;
                }
            };
        }

        /**
         * Returns the structural category of this edit (ADD_EDGE, REMOVE_EDGE,
         * REORIENT_SIMPLE, or OTHER). Used by ranking and progress checks
         * in place of fragile string matching on descriptions/keys.
         *
         * @return the move type of this edit; never null.
         */
        MoveType moveType();

        /**
         * Provides a textual description of the {@code CandidateEdit} operation. The description
         * typically explains the type of edit being performed (e.g., adding, removing, or replacing edges)
         * and may include additional details relevant to the operation.
         *
         * @return a string representing the description of this {@code CandidateEdit} instance.
         */
        String description();

        /**
         * Applies the specified {@code CandidateEdit} operation to the given graph.
         * This method modifies the provided graph according to the edit action defined
         * in the {@code CandidateEdit} instance, such as adding, removing, or replacing edges.
         *
         * @param g the graph to which the edit operation will be applied; must not be null.
         * @return the modified graph after applying the edit operation, or null if the
         * operation cannot be applied.
         */
        Graph applyTo(Graph g);

        /**
         * Indicates whether this {@code CandidateEdit} instance represents a no-operation (no-op).
         * A no-op edit implies that no changes will be applied to the graph, and the instance
         * is effectively a placeholder with no effect on the graph when executed.
         *
         * @return {@code true} if this {@code CandidateEdit} instance is a no-op; {@code false} otherwise.
         */
        boolean isNoOp();

        /**
         * Retrieves the unique key identifying this {@code CandidateEdit} instance.
         * The key is typically used to distinguish this edit from others or for lookup purposes.
         *
         * @return a string representing the unique key of this {@code CandidateEdit} instance.
         */
        String key();

        /**
         * Retrieves the edge associated with this {@code CandidateEdit}
         * operation, if applicable.
         *
         * @return the edge associated with this {@code CandidateEdit}, or
         * null if no edge is associated with the operation.
         */
        Edge getEdge();

        /**
         * Retrieves a list containing the edge associated with this {@code CandidateEdit}, if applicable.
         * If no edge is associated with the operation, an empty list is returned.
         *
         * @return a list of one edge if an edge is associated with this {@code CandidateEdit};
         * otherwise, an empty list.
         */
        default List<Edge> getEdges() {
            Edge e = getEdge();
            return (e == null) ? List.of() : List.of(e);
        }
    }

    /**
     * Represents a scored candidate with various metrics including violations, probabilities,
     * and whether it passes certain guards. This record is used to encapsulate the data required
     * to evaluate a candidate's quality according to a scoring model.
     *
     * @param edit               The candidate edit associated with the scored candidate.
     * @param violationsBaseline The initial number of violations in the baseline.
     * @param violationsAfter    The number of violations after applying the edit.
     * @param nodePAfter         The node-level probability after applying the edit.
     * @param modelPBefore       The model-level probability before applying the edit.
     * @param modelPAfter        The model-level probability after applying the edit.
     * @param edgesAfter         The number of edges present after applying the edit.
     * @param passesGuards       A flag indicating if the candidate passes predefined guards/criteria.
     * @param alpha              An additional parameter used in scoring computations.
     */
    public record ScoredCandidate(
            CandidateEdit edit,
            int violationsBaseline,
            int violationsAfter,
            double nodePAfter,
            double modelPBefore,
            double modelPAfter,
            int edgesAfter,
            boolean passesGuards,
            double alpha
    ) {

        /**
         * Computes the difference between the number of violations after and the baseline violations.
         *
         * @return The difference between violationsAfter and violationsBaseline.
         */
        public int delta() {
            return violationsAfter - violationsBaseline;
        }
    }

    private record QueueEntry(String nodeName, ScoredCandidate scored, long version)
            implements Comparable<QueueEntry> {
        @Override
        public int compareTo(QueueEntry other) {
            return CANONICAL_TABLE_ORDER.compare(this.scored(), other.scored());
        }
    }

    private record GraphEval(int violations, double modelP, int nFacts) {
    }

    private record VertexContribution(Map<String, Boolean> violationByKey, Map<String, Double> pByKey) {
    }

    private record GlobalEvalCache(Map<String, VertexContribution> contribByVertexName) {
    }

    /**
     * Memoized per-graph-state baseline evaluation: the canonicalized base graph, the
     * per-vertex Markov contribution cache over it, the baseline violation count, the
     * baseline Model-P, and the graph version this bundle was computed for.
     */
    private record BaselineBundle(Graph base, GlobalEvalCache cache, int violations,
                                  double modelP, long version) {
    }
}
