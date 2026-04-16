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
import edu.cmu.tetrad.util.NaturalSort;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TMath;
import org.apache.commons.math3.distribution.UniformRealDistribution;
import org.apache.commons.math3.stat.inference.KolmogorovSmirnovTest;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import static edu.cmu.tetrad.util.TMath.abs;

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
    public static final Comparator<ScoredCandidate> CANONICAL_TABLE_ORDER = (a, b) -> {
        if (a == null && b == null) return 0;
        if (a == null) return 1;
        if (b == null) return -1;

        int c;

        if (abs(a.delta()) >= 0 && abs(b.delta()) >= 0) {
            c = Integer.compare(a.delta(), b.delta());
            if (c != 0) return c;
        }

        // Removals always use actual edge count; additions only get credit
        // if modelP already clears alpha — otherwise they sort to the back.
        boolean aIsRemoval = moveType(a.edit()) == MoveType.REMOVE_EDGE;
        boolean bIsRemoval = moveType(b.edit()) == MoveType.REMOVE_EDGE;
        int edges1 = (aIsRemoval || a.modelPAfter() > a.alpha()) ? a.edgesAfter() : Integer.MAX_VALUE;
        int edges2 = (bIsRemoval || b.modelPAfter() > b.alpha()) ? b.edgesAfter() : Integer.MAX_VALUE;
        c = Integer.compare(edges1, edges2);
        if (c != 0) return c;

        c = -Double.compare(a.modelPAfter(), b.modelPAfter());
        if (c != 0) return c;

        return stableTieBreak(a, b);
    };
    private static final int DEFAULT_MODELP_TOP_K = 50;

    // -------------------------------------------------------------------------
    // Configuration fields (set before calling search())
    // -------------------------------------------------------------------------
    private final CachedIndependenceQueries Q;
    private final ConditioningSetType type;
    private final List<RepairListener> listeners = new CopyOnWriteArrayList<>();
    private final Map<String, List<ScoredCandidate>> globalCandsByNode = new HashMap<>();
    private double pruneAlpha = 0.2;
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
    private int seed = 0;

    // -------------------------------------------------------------------------
    // Global-queue state (populated only during a GLOBAL_QUEUE run)
    // -------------------------------------------------------------------------
    private volatile boolean cancelRequested = false;
    private PriorityQueue<QueueEntry> globalQueue = new PriorityQueue<>();
    private boolean useAndersonDarling = false;
    private boolean verbose = false;

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

    private static boolean isProgress(int baselineViol, int afterViol,
                                      int currentEdges, int afterEdges,
                                      double mpBefore, double mpAfter,
                                      MoveType moveType) {
        if (afterViol < baselineViol) return true;
        if (afterEdges < currentEdges) return true;
        double minGain = (moveType == MoveType.REMOVE_EDGE) ? 0.0 : 0.001;
        return Double.isFinite(mpBefore) && Double.isFinite(mpAfter)
                && (mpAfter - mpBefore) >= minGain;
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

    // =========================================================================
    // Cancellation
    // =========================================================================

    private static MoveType moveType(CandidateEdit e) {
        if (e == null) return MoveType.OTHER;
        String s = (safeLower(e.key()) + " " + safeLower(e.description())).trim();
        if (containsAny(s, "rem:") || containsAny(s, "remove", "delete")) return MoveType.REMOVE_EDGE;
        if (containsAny(s, "add:") || containsAny(s, "add", "insert")) return MoveType.ADD_EDGE;
        if (containsAny(s, "rep:") || containsAny(s, "replace", "reorient", "orient", "flip", "reverse", "endpoint"))
            return MoveType.REORIENT_SIMPLE;
        return MoveType.OTHER;
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

    private void vlog(String fmt, Object... args) {
        if (this.verbose) {
            System.out.println("[VertexRepairSearch] " + String.format(fmt, args));
        }
    }

    // =========================================================================
    // Private: queue management
    // =========================================================================

    private static Edge getEdgeByNames(Graph g, Edge e) {
        if (g == null || e == null) return null;
        String a = e.getNode1() == null ? null : e.getNode1().getName();
        String b = e.getNode2() == null ? null : e.getNode2().getName();
        if (a == null || b == null) return null;
        Node ga = g.getNode(a), gb = g.getNode(b);
        if (ga == null || gb == null) return null;
        return g.getEdge(ga, gb);
    }

    private static double getKolomogorovP(List<Double> pvals) {
        double[] x = pvals.stream().mapToDouble(Double::doubleValue).toArray();
        KolmogorovSmirnovTest ks = new KolmogorovSmirnovTest();
        return ks.kolmogorovSmirnovTest(new UniformRealDistribution(0.0, 1.0), x);
    }

    // =========================================================================
    // Private: candidate computation
    // =========================================================================

    /**
     * Tests a list of p-values against the Anderson-Darling Test.
     *
     * @param pValues the list of p-values to be tested
     * @return the p-value obtained from the Anderson-Darling Test
     */
    public static double getAndersonDarlingP(List<Double> pValues) {
        GeneralAndersonDarlingTest generalAndersonDarlingTest = new GeneralAndersonDarlingTest(pValues, new UniformRealDistribution(0, 1));
        return generalAndersonDarlingTest.getP();
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
     */
    public Graph getGraph() {
        return workingGraph;
    }

    /**
     * Replaces the working graph used for repair.
     */
    public void setGraph(Graph graph) {
        this.workingGraph = safeCopy(Objects.requireNonNull(graph, "graph"));
    }

    /**
     * Sets background knowledge constraints. Null is treated as empty knowledge.
     */
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = (knowledge == null) ? new Knowledge() : knowledge;
    }

    /**
     * Sets the graph type that governs which edits are legal.
     */
    public void setGraphType(AdjustmentGraphType graphType) {
        this.graphType = Objects.requireNonNull(graphType, "graphType");
    }

    // =========================================================================
    // Private: graph preparation and apply
    // =========================================================================

    /**
     * Sets the repair strategy.
     */
    public void setRepairStrategy(RepairStrategy repairStrategy) {
        this.repairStrategy = Objects.requireNonNull(repairStrategy, "repairStrategy");
    }

    /**
     * Sets the random seed used for node-traversal order.
     */
    public void setSeed(int seed) {
        this.seed = seed;
    }

    // =========================================================================
    // Private: graph evaluation
    // =========================================================================

    /**
     * Registers a listener for repair-progress events.
     */
    public void addRepairListener(RepairListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /**
     * Removes a previously registered listener.
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
        Graph base = prepareBase();
        if (base == null) return List.of();
        if (knowledge != null && knowledge.isViolatedBy(base)) return List.of();

        Node nodeInBase = base.getNode(node.getName());
        if (nodeInBase == null) return List.of();

        List<CandidateEdit> candidates = new ArrayList<>(enumerateCandidates(base, nodeInBase));
        if (candidates.stream().noneMatch(CandidateEdit::isNoOp)) {
            candidates.addFirst(CandidateEdit.noOp());
        }
        return scoreCandidates(base, nodeInBase, candidates);
    }

    /**
     * Applies the given candidate edit to the working graph and returns the
     * resulting graph (or {@code null} if the edit could not be applied).
     *
     * <p>On success the internal working graph is updated.
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

                    Graph base = prepareBase();
                    if (base == null) {
                        fireStatus("Canonicalization failed during repair.");
                        return;
                    }

                    List<ScoredCandidate> candidates = computeScoredCandidatesForNode(base, current);
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
        }

        fireStatus("Building global candidate queue...");
        if (!initGlobalQueue()) return;
        // ... rest unchanged

        while (!globalQueue.isEmpty()) {
            if (stopRequested()) return;

            QueueEntry entry = globalQueue.poll();
            if (entry == null) break;

            List<ScoredCandidate> currentForNode = globalCandsByNode.get(entry.nodeName());
            if (currentForNode == null || currentForNode.isEmpty()) continue;
            if (currentForNode.getFirst() != entry.scored()) continue; // stale

            ScoredCandidate sc = entry.scored();
            if (sc.edit().isNoOp()) continue;

            ScoredCandidate withMp = evalModelPForEntry(entry);
            if (withMp == null) continue;

            if (!wouldPassGuards(workingGraph, withMp)) continue;

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

            Set<String> affected = affectedVertices(before,
                    resolveNode(before, entry.nodeName()), workingGraph)
                    .stream().filter(n -> workingGraph.getNode(n) != null)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            CandidateEdit edit = withMp.edit();
            Edge editedEdge = edit.getEdge();
            if (editedEdge != null) {
                Node n1 = editedEdge.getNode1(), n2 = editedEdge.getNode2();
                if (n1 != null && n1.getName() != null) affected.add(n1.getName());
                if (n2 != null && n2.getName() != null) affected.add(n2.getName());
            }

            if (!invalidateAndRecompute(affected)) return;

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

        Graph base = prepareBase();
        if (base == null) {
            fireStatus("Canonicalization failed during queue init.");
            return false;
        }

        List<Node> nodes = new ArrayList<>(workingGraph.getNodes());
        nodes.sort(Comparator.comparing(Node::getName, NaturalSort.NATURAL_NAME_COMPARATOR));

        for (Node node : nodes) {
            if (stopRequested()) return false;

            Node inBase = base.getNode(node.getName());
            if (inBase == null) continue;

            List<ScoredCandidate> scored = computeScoredCandidatesForNodeNoModelP(base, inBase);
            if (scored.isEmpty()) continue;

            globalCandsByNode.put(node.getName(), scored);
            for (ScoredCandidate sc : scored) {
                if (!sc.edit().isNoOp()) globalQueue.offer(new QueueEntry(node.getName(), sc));
            }
        }
        return true;
    }

    private boolean invalidateAndRecompute(Set<String> affectedNames) {
        Graph base = prepareBase();
        if (base == null) {
            fireStatus("Canonicalization failed during queue update.");
            return false;
        }

        for (String name : affectedNames) {
            if (stopRequested()) return false;
            if (name == null) continue;

            Node inGraph = workingGraph.getNode(name);
            Node inBase = (inGraph != null) ? base.getNode(name) : null;

            if (inBase == null) {
                globalCandsByNode.remove(name);
                continue;
            }

            List<ScoredCandidate> fresh = computeScoredCandidatesForNodeNoModelP(base, inBase);
            globalCandsByNode.put(name, fresh);

            for (ScoredCandidate sc : fresh) {
                if (!sc.edit().isNoOp()) globalQueue.offer(new QueueEntry(name, sc));
            }
        }
        return true;
    }

    // =========================================================================
    // Private: graph type helpers
    // =========================================================================

    private List<ScoredCandidate> computeScoredCandidatesForNode(Graph base, Node node) {
        List<CandidateEdit> candidates = new ArrayList<>(enumerateCandidates(base, node));
        if (candidates.stream().noneMatch(CandidateEdit::isNoOp)) {
            candidates.addFirst(CandidateEdit.noOp());
        }
        return scoreCandidates(base, node, candidates);
    }

    private List<ScoredCandidate> computeScoredCandidatesForNodeNoModelP(Graph base, Node node) {
        List<CandidateEdit> candidates = new ArrayList<>(enumerateCandidates(base, node));
        if (candidates.stream().noneMatch(CandidateEdit::isNoOp)) {
            candidates.addFirst(CandidateEdit.noOp());
        }

        GlobalEvalCache baseCache = buildBaselineCache(base);
        int baseline = evalGraphLocality(baseCache, base, Set.of()).violations();

        Map<String, Graph> candGraphByKey = new HashMap<>();
        List<ScoredCandidate> result = new ArrayList<>();

        for (CandidateEdit cand : candidates) {
            if (stopRequested()) return List.of();

            Graph g2 = candGraphByKey.computeIfAbsent(cand.key(),
                    k -> buildCandidateGraph(base, cand));
            if (g2 == null) continue;
            if (knowledge != null && knowledge.isViolatedBy(g2)) continue;

            boolean useLocality = usesLocality();
            Set<String> affected = affectedVertices(base, node, g2);
            int after = useLocality
                    ? evalGraphLocality(baseCache, g2, affected).violations()
                    : evalViolationsOnly(g2);

            ScoredCandidate sc = new ScoredCandidate(
                    cand, baseline, after,
                    nodePValue(g2, node),
                    Double.NaN, Double.NaN,
                    g2.getNumEdges(), true,
                    Q.getAlpha());

//            if (!cand.isNoOp()) {
//                boolean couldProgress = after < baseline || g2.getNumEdges() < base.getNumEdges();
//                if (!couldProgress) continue;
//            }

            // TODO keep?
            if (!cand.isNoOp()) {
                if (moveType(cand) == MoveType.REMOVE_EDGE) {
                    // Always keep removals — they can't add false structure,
                    // and ModelP evaluation will filter bad ones out.
                } else {
                    boolean couldProgress = after < baseline
                            || g2.getNumEdges() < base.getNumEdges();
                    if (!couldProgress) continue;
                }
            }

            result.add(sc);
        }

        result.sort(CANONICAL_TABLE_ORDER);
        return result;
    }

    private List<ScoredCandidate> scoreCandidates(Graph base, Node node, List<CandidateEdit> candidates) {
        GlobalEvalCache baseCache = buildBaselineCache(base);
        int baseline = evalGraphLocality(baseCache, base, Set.of()).violations();
        double mpBefore = evalGraphLocality(baseCache, base, Set.of()).modelP();

        Map<String, Graph> candGraphByKey = new HashMap<>();
        List<ScoredCandidate> scored = new ArrayList<>();

        for (CandidateEdit cand : candidates) {
            if (stopRequested()) return List.of();

            Graph g2 = candGraphByKey.computeIfAbsent(cand.key(),
                    k -> buildCandidateGraph(base, cand));
            if (g2 == null) continue;
            if (knowledge != null && knowledge.isViolatedBy(g2)) continue;

            Set<String> affected = affectedVertices(base, node, g2);
            int after = usesLocality()
                    ? evalGraphLocality(baseCache, g2, affected).violations()
                    : evalViolationsOnly(g2);

            scored.add(new ScoredCandidate(cand, baseline, after,
                    nodePValue(g2, node), Double.NaN, Double.NaN,
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
                    sc.edgesAfter(), true, Q.getAlpha());

            boolean passes = wouldPassGuards(base, patched);
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
        Graph base = prepareBase();
        if (base == null) return null;

        ScoredCandidate sc = entry.scored();
        CandidateEdit cand = sc.edit();

        Graph g2 = buildCandidateGraph(base, cand);
        if (g2 == null) return null;

        Node node = workingGraph.getNode(entry.nodeName());
        if (node == null) return null;

        GlobalEvalCache baseCache = buildBaselineCache(base);
        Set<String> affected = affectedVertices(base, node, g2);

        double mpBefore = evalGraphLocality(baseCache, base, Set.of()).modelP();
        double mpAfter = evalModelPLocality(baseCache, g2, affected);
        int baseline = evalGraphLocality(baseCache, base, Set.of()).violations();
        int after = usesLocality()
                ? evalGraphLocality(baseCache, g2, affected).violations()
                : evalViolationsOnly(g2);

        boolean passes = isProgress(baseline, after,
                base.getNumEdges(), g2.getNumEdges(), mpBefore, mpAfter,
                moveType(cand));

        return new ScoredCandidate(cand, baseline, after,
                sc.nodePAfter(), mpBefore, mpAfter, g2.getNumEdges(), passes, Q.getAlpha());
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

    private Graph prepareBase() {
        Graph base = safeCopy(workingGraph);
        if (graphType == AdjustmentGraphType.CPDAG) {
            base = canonicalizeToCpdagOrNull(base);
        } else if (graphType == AdjustmentGraphType.PAG) {
            base = canonicalizeToPagOrNull(base);
        }
        return base;
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

        if (graphType == AdjustmentGraphType.CPDAG) {
            g2 = canonicalizeToCpdagOrNull(g2);
            if (g2 == null) return;
        }

        if (g2.equals(base)) return;
        if (requiresEdgePresenceCheck(cand) && !allIntendedNewEdgesPresent(g2, cand)) return;

        workingGraph = g2;
        vlog("APPLIED successfully");
    }

    private GlobalEvalCache buildBaselineCache(Graph g) {
        if (g == null) return new GlobalEvalCache(Map.of());
        Map<String, VertexContribution> out = new HashMap<>();
        List<Node> nodes = g.getNodes();
        RandomUtil.shuffle(nodes);
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

        List<IndependenceFact> facts = MarkovCheck.computeImpliedFactsForVertex(g, v, type);
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

        List<String> names = new ArrayList<>(contrib.keySet());
        Collections.sort(names);

        for (String name : names) {
            VertexContribution vc = contrib.get(name);
            if (vc == null) continue;

            List<String> violKeys = new ArrayList<>(vc.violationByKey().keySet());
            Collections.sort(violKeys);
            for (String key : violKeys) globalViolationByKey.putIfAbsent(key, vc.violationByKey().get(key));

            List<String> pKeys = new ArrayList<>(vc.pByKey().keySet());
            Collections.sort(pKeys);
            for (String key : pKeys) globalPByKey.putIfAbsent(key, vc.pByKey().get(key));
        }

        int violations = 0;
        for (boolean isViol : globalViolationByKey.values()) if (isViol) violations++;

        List<IndependenceFact> allFacts = MarkovCheck.computeAllImpliedFacts(candidateGraph, type);
        List<Double> allPValues = new ArrayList<>();

        for (IndependenceFact f : allFacts) {
            try {
                allPValues.add(Q.checkIndependence(f.getX(), f.getY(), f.getZ()).getPValue());
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        double modelP = Double.NaN;

        if (allPValues.size() > 2) {
            modelP = getUniformityP(allPValues);
        }
//
//        double modelP = Double.NaN;
//        if (globalPByKey.size() >= 2) {
//            List<Double> pvals = new ArrayList<>(globalPByKey.values());
//            pvals.sort(Double::compareTo);
//            modelP = getUniformityP(pvals);
//        }

        return new GraphEval(violations, modelP, globalViolationByKey.size());
    }


    private double evalModelPLocality(GlobalEvalCache baseCache, Graph g, Set<String> affected) {
        return evalGraphLocality(baseCache, g, affected).modelP();
    }

    private int evalViolationsOnly(Graph g) {
        if (g == null) return 0;
        List<IndependenceFact> facts = MarkovCheck.computeAllImpliedFacts(g, type);
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
        List<Double> pvals = Q.pValuesForFacts(facts, CachedIndependenceQueries.Dedup.WITHIN_INPUT);
        return getUniformityP(pvals);
    }

    private Graph buildCandidateGraph(Graph base, CandidateEdit cand) {
        if (base == null || cand == null) return null;
        Graph g2 = cand.applyTo(safeCopy(base));
        if (g2 == null) return null;
        if (graphType == AdjustmentGraphType.CPDAG) {
            g2 = canonicalizeToCpdagOrNull(g2);
            if (g2 == null) return null;
            if (!cand.isNoOp() && g2.equals(base)) return null;
        }
        if (requiresEdgePresenceCheck(cand) && !allIntendedNewEdgesPresent(g2, cand)) return null;
        try {
            if (graphType != null && !isLegalGraphType(g2)) return null;
        } catch (Exception ignored) {
            return null;
        }
        return g2;
    }

    private boolean wouldPassGuards(Graph base, ScoredCandidate sc) {
        if (sc == null || sc.edit() == null || sc.edit().isNoOp()) return false;
        return isProgress(sc.violationsBaseline(), sc.violationsAfter(),
                base.getNumEdges(), sc.edgesAfter(),
                sc.modelPBefore(), sc.modelPAfter(),
                moveType(sc.edit()));
    }

    private boolean isLegalGraphType(Graph g) {
        return switch (graphType) {
            case DAG -> g.paths().isLegalDag();
            case CPDAG -> g.paths().isLegalCpdag() || g.paths().isLegalPdag();
            case PDAG -> g.paths().isLegalPdag();
            case MAG -> g.paths().isLegalMag();
            case PAG -> g.paths().isLegalPag();
        };
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
            if (h2.paths().isLegalDag()) return GraphTransforms.dagToCpdag(h2);
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

    // =========================================================================
    // Public types
    // =========================================================================

    // ---- Enums ---------------------------------------------------------------

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
    }

    // ---- Listener ------------------------------------------------------------

    public void setPruneAlpha(double pruneAlpha) {
        if (pruneAlpha < 0.0 || pruneAlpha > 1.0) {
            throw new IllegalArgumentException("Prune alpha must be between 0 and 1: " + pruneAlpha);
        }
        this.pruneAlpha = pruneAlpha;
    }

    // ---- CandidateEdit -------------------------------------------------------

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

    // ---- Private types -------------------------------------------------------

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

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Graph types supported by the repair search.
     */
    public enum AdjustmentGraphType {CPDAG, PDAG, PAG, DAG, MAG}

    /**
     * Which repair sweep strategy to use.
     */
    public enum RepairStrategy {
        LOCAL_SWEEP("Local sweep"),
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

    private enum MoveType {REORIENT_SIMPLE, REMOVE_EDGE, ADD_EDGE, OTHER}

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

        static CandidateEdit replaceEdge(Edge edgeToRemove, Edge edgeToAdd) {
            Objects.requireNonNull(edgeToRemove, "edgeToRemove");
            Objects.requireNonNull(edgeToAdd, "edgeToAdd");
            return new CandidateEdit() {
                @Override
                public String description() {
                    return "Replace " + edgeToRemove + " → " + edgeToAdd;
                }

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

                @Override
                public boolean isNoOp() {
                    return false;
                }

                @Override
                public String key() {
                    return "REP:" + edgeToRemove + "→" + edgeToAdd;
                }

                @Override
                public Edge getEdge() {
                    return edgeToAdd;
                }
            };
        }

        static CandidateEdit replaceEdges(String label, List<Edge> edgesToRemove, List<Edge> edgesToAdd) {
            Objects.requireNonNull(label);
            List<Edge> rem = List.copyOf(Objects.requireNonNull(edgesToRemove));
            List<Edge> add = List.copyOf(Objects.requireNonNull(edgesToAdd));
            return new CandidateEdit() {
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

        String description();

        Graph applyTo(Graph g);

        boolean isNoOp();

        String key();

        Edge getEdge();

        default List<Edge> getEdges() {
            Edge e = getEdge();
            return (e == null) ? List.of() : List.of(e);
        }
    }

    /**
     * A scored candidate edit ready for display or application.
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
        public int delta() {
            return violationsAfter - violationsBaseline;
        }
    }

    private record QueueEntry(String nodeName, ScoredCandidate scored)
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
}
