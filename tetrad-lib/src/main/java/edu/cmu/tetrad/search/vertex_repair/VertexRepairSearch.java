///////////////////////////////////////////////////////////////////////////////
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
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.vertex_repair;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.ConditioningSetType;
import edu.cmu.tetrad.search.IGraphSearch;
import edu.cmu.tetrad.search.MarkovCheck;
import edu.cmu.tetrad.search.test.CachedIndependenceQueries;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.math3.distribution.UniformRealDistribution;
import org.apache.commons.math3.stat.inference.KolmogorovSmirnovTest;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Algorithm-only extraction of the VertexRepairPanel logic.
 *
 * <p>Given a graph and an independence test, enumerates conservative local edit candidates
 * around a node, scores them using Markov-check implied facts, and greedily applies edits
 * subject to "progress" guards.</p>
 *
 * <p>This class is UI-free and suitable for programmatic use and integration into editors.</p>
 */
public final class VertexRepairSearch implements IGraphSearch {

    // -------------------- configuration --------------------

    /** Default limit for Model-P evaluation set (top-K + all simple reorients). */
    private static final int DEFAULT_MODEL_P_TOP_K = 25;

    /** Progress guard: allow pure Model-P improvement if violations and edges tie. */
    private static final double MIN_MP_GAIN = 1e-3;

    private boolean verbose = false;

    private Graph graph; // working graph
    private IndependenceTest test;
    private CachedIndependenceQueries queries;

    private Knowledge knowledge = new Knowledge();
    private RepairGraphType graphType = RepairGraphType.CPDAG;

    /** Required for MarkovCheck implied facts; if null, we fail fast with a clear error. */
    private ConditioningSetType conditioningSetType;

    // Auto-repair controls
    private int maxEdits = 500;
    private int maxStepsPerNodePerSweep = 4;
    private int maxSweeps = 50;
    private int modelPTopK = DEFAULT_MODEL_P_TOP_K;

    private final AtomicBoolean stop = new AtomicBoolean(false);

    // -------------------- constructors --------------------

    public VertexRepairSearch(IndependenceTest test) {
        setTest(Objects.requireNonNull(test, "test"));
    }

    public VertexRepairSearch(Graph graph, IndependenceTest test) {
        setGraph(Objects.requireNonNull(graph, "graph"));
        setTest(Objects.requireNonNull(test, "test"));
    }

    // -------------------- IGraphSearch --------------------

    @Override
    public Graph search() throws InterruptedException {
        ensureReady();
        stop.set(false);
        autoRepair();
        return getGraph();
    }

    @Override
    public IndependenceTest getTest() {
        return this.test;
    }

    @Override
    public void setTest(IndependenceTest test) {
        Objects.requireNonNull(test, "test");
        // If already set, enforce variable list equality like other searches.
        if (this.test != null) {
            List<Node> a = this.test.getVariables();
            List<Node> b = test.getVariables();
            if (!sameVariableNames(a, b)) {
                throw new IllegalArgumentException("New test variables must match existing test variables (by name).");
            }
        }
        this.test = test;
        this.queries = new CachedIndependenceQueries(test);
    }

    // -------------------- public API --------------------

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public void requestStop() {
        stop.set(true);
    }

    public Graph getGraph() {
        return safeCopy(graph);
    }

    public void setGraph(Graph graph) {
        this.graph = safeCopy(Objects.requireNonNull(graph, "graph"));
    }

    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = (knowledge == null) ? new Knowledge() : knowledge;
    }

    public Knowledge getKnowledge() {
        return knowledge;
    }

    public void setGraphType(RepairGraphType graphType) {
        this.graphType = Objects.requireNonNull(graphType, "graphType");
    }

    public RepairGraphType getGraphType() {
        return graphType;
    }

    public void setConditioningSetType(ConditioningSetType conditioningSetType) {
        this.conditioningSetType = Objects.requireNonNull(conditioningSetType, "conditioningSetType");
    }

    public ConditioningSetType getConditioningSetType() {
        return conditioningSetType;
    }

    public void setMaxEdits(int maxEdits) {
        this.maxEdits = Math.max(1, maxEdits);
    }

    public void setMaxStepsPerNodePerSweep(int maxStepsPerNodePerSweep) {
        this.maxStepsPerNodePerSweep = Math.max(1, maxStepsPerNodePerSweep);
    }

    public void setMaxSweeps(int maxSweeps) {
        this.maxSweeps = Math.max(1, maxSweeps);
    }

    public void setModelPTopK(int modelPTopK) {
        this.modelPTopK = Math.max(0, modelPTopK);
    }

    /**
     * Compute scored candidate edits for a given center node (no mutation).
     * Returned list is already sorted by canonical order.
     */
    public List<ScoredCandidate> scoreCandidatesForNode(Node center) throws InterruptedException {
        ensureReady();
        Node c = resolveNodeInGraph(center);
        if (c == null) return List.of();

        Graph base = canonicalizeBaseIfNeeded(safeCopy(graph));
        if (base == null) return List.of();
        if (knowledge != null && knowledge.isViolatedBy(base)) return List.of();

        List<CandidateEdit> candidates = enumerateCandidates(base, c, graphType);
        if (candidates.stream().noneMatch(CandidateEdit::isNoOp)) {
            candidates = new ArrayList<>(candidates);
            candidates.add(0, CandidateEdit.noOp());
        }

        GlobalEvalCache baseCache = buildBaselineCache(base);

        GraphEval baseEval = evalGraphLocality(baseCache, base, Set.of(), false);
        int baselineViol = baseEval.violations();

        double mpBefore = evalGraphOnce(base).modelP();

        Map<String, Graph> candGraphByKey = new HashMap<>();
        List<ScoredCandidate> scored = new ArrayList<>();

        // PASS 1: after-violations + nodeP + edges; modelP deferred
        for (CandidateEdit cand : candidates) {
            checkStop();

            Graph g2 = candGraphByKey.computeIfAbsent(cand.key(), k -> buildCandidateGraph(base, cand, graphType));
            if (g2 == null) continue;
            if (knowledge != null && knowledge.isViolatedBy(g2)) continue;

            boolean useLocality = (graphType == RepairGraphType.DAG || graphType == RepairGraphType.CPDAG || graphType == RepairGraphType.PDAG);
            Set<String> affected = affectedVertices(base, c, g2);

            int afterViol = useLocality
                    ? evalGraphLocality(baseCache, g2, affected, false).violations()
                    : evalViolationsOnly(g2);

            double nodePAfter = nodePValue(g2, c);
            int edgesAfter = g2.getNumEdges();

            scored.add(new ScoredCandidate(cand, baselineViol, afterViol, nodePAfter, mpBefore, Double.NaN, edgesAfter, true));
        }

        // Decide which candidates get Model-P evaluated:
        List<ScoredCandidate> ranked = new ArrayList<>(scored);
        ranked.sort(CANONICAL_TABLE_ORDER);

        LinkedHashSet<String> keysToEval = new LinkedHashSet<>();
        int topK = Math.min(modelPTopK, ranked.size());
        for (int i = 0; i < topK; i++) {
            CandidateEdit e = ranked.get(i).edit();
            if (e != null) keysToEval.add(e.key());
        }
        for (ScoredCandidate sc : scored) {
            if (sc.edit() != null && moveType(sc.edit()) == MoveType.REORIENT_SIMPLE) {
                keysToEval.add(sc.edit().key());
            }
        }

        Map<String, Double> mpAfterByKey = new HashMap<>(keysToEval.size() * 2);
        for (String key : keysToEval) {
            checkStop();
            Graph g2 = candGraphByKey.get(key);
            if (g2 == null) continue;
            mpAfterByKey.put(key, evalGraphOnce(g2).modelP());
        }

        // Patch mpAfter + passesGuards
        List<ScoredCandidate> patched = new ArrayList<>(scored.size());
        for (ScoredCandidate sc : scored) {
            Double mpAfter = (sc.edit() == null) ? null : mpAfterByKey.get(sc.edit().key());
            double mpA = (mpAfter == null) ? Double.NaN : mpAfter;

            ScoredCandidate sc2 = new ScoredCandidate(
                    sc.edit(),
                    sc.violationsBaseline(),
                    sc.violationsAfter(),
                    sc.nodePAfter(),
                    mpBefore,
                    mpA,
                    sc.edgesAfter(),
                    true
            );

            boolean ok = wouldPassGuards(base, c, sc2, graphType);
            patched.add(sc2.withPassesGuards(ok));
        }

        patched.sort(CANONICAL_TABLE_ORDER);
        return patched;
    }

    /**
     * Applies the first ranked candidate that passes guards for this node.
     * Returns true if an edit was applied.
     */
    public boolean applyBestMoveForNode(Node center) throws InterruptedException {
        ensureReady();
        Node c = resolveNodeInGraph(center);
        if (c == null) return false;

        List<ScoredCandidate> ranked = scoreCandidatesForNode(c);
        if (ranked.isEmpty()) return false;

        for (ScoredCandidate sc : ranked) {
            checkStop();
            if (sc.edit() == null || sc.edit().isNoOp()) return false;
            if (!sc.passesGuards()) continue;

            if (verbose) {
                log(String.format("APPLY node=%s move=%s Δ=%d edgesAfter=%d modelP=%s",
                        c.getName(), sc.edit().description(), sc.delta(), sc.edgesAfter(), fmtP(sc.modelPAfter())));
            }
            boolean ok = applyCandidate(sc.edit());
            if (ok) return true;
        }

        return false;
    }

    /**
     * Greedy auto-repair: sweeps nodes worst-first by nodeP, repeating until fixed point or caps hit.
     */
    public void autoRepair() throws InterruptedException {
        ensureReady();

        int editsApplied = 0;
        int sweep = 0;

        while (editsApplied < maxEdits) {
            checkStop();
            sweep++;
            if (sweep > maxSweeps) {
                if (verbose) log("STOP: hit maxSweeps=" + maxSweeps);
                break;
            }

            String startSig = graphSignature(graph);
            int editsThisSweep = 0;

            List<Node> nodes = new ArrayList<>(graph.getNodes());
            Map<String, Double> nodePOrder = new HashMap<>();
            for (Node n : nodes) {
                if (n == null || n.getName() == null) continue;
                nodePOrder.put(n.getName(), nodePValue(graph, n));
            }

            nodes.sort((a, b) -> {
                if (a == null && b == null) return 0;
                if (a == null) return 1;
                if (b == null) return -1;
                double pa = nodePOrder.getOrDefault(a.getName(), Double.NaN);
                double pb = nodePOrder.getOrDefault(b.getName(), Double.NaN);

                boolean aNaN = Double.isNaN(pa);
                boolean bNaN = Double.isNaN(pb);
                if (aNaN && bNaN) return naturalName(a, b);
                if (aNaN) return 1;
                if (bNaN) return -1;

                int c = Double.compare(pa, pb); // ASC: worst first
                if (c != 0) return c;
                return naturalName(a, b);
            });

            if (verbose) log("SWEEP " + sweep + " startSig=" + startSig);

            for (Node v0 : nodes) {
                checkStop();
                if (editsApplied >= maxEdits) break;
                if (v0 == null || v0.getName() == null) continue;

                Node center = graph.getNode(v0.getName());
                if (center == null) continue;

                int steps = 0;
                Set<String> seenSigs = new HashSet<>();

                while (editsApplied < maxEdits) {
                    checkStop();
                    steps++;
                    if (steps > maxStepsPerNodePerSweep) break;

                    String sig = graphSignature(graph);
                    if (!seenSigs.add(sig)) break;

                    boolean moved = applyBestMoveForNode(center);
                    if (!moved) break;

                    editsApplied++;
                    editsThisSweep++;
                    // refresh center identity if node objects were replaced
                    center = graph.getNode(v0.getName());
                    if (center == null) break;
                }
            }

            String endSig = graphSignature(graph);
            if (verbose) log("SWEEP " + sweep + " edits=" + editsThisSweep + " endSig=" + endSig);

            if (editsThisSweep == 0 || endSig.equals(startSig)) {
                if (verbose) log("STOP: fixed point reached at sweep " + sweep);
                break;
            }
        }

        if (verbose) log("AUTO-REPAIR done. totalEdits=" + editsApplied);
    }

    // -------------------- internal core (ported from panel) --------------------

    public enum RepairGraphType {DAG, CPDAG, PDAG, MAG, PAG}

    public interface CandidateEdit {
        String description();
        Graph applyTo(Graph g);
        default boolean isNoOp() { return false; }
        default String key() { return description(); }
        default Edge getEdge() { return null; }
        default List<Edge> getEdges() {
            Edge e = getEdge();
            return (e == null) ? List.of() : List.of(e);
        }

        static CandidateEdit noOp() {
            return new CandidateEdit() {
                @Override public String description() { return "No change"; }
                @Override public Graph applyTo(Graph g) { return (g == null) ? null : new EdgeListGraph(g); }
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
                    Edge rebound = rebindEdgeToGraph(g2, edgeToAdd);
                    if (rebound != null) g2.addEdge(rebound);
                    return g2;
                }
                @Override public String key() { return "ADD:" + stableEdgeKey(edgeToAdd); }
                @Override public Edge getEdge() { return edgeToAdd; }
            };
        }

        static CandidateEdit removeEdge(Edge edgeToRemove) {
            Objects.requireNonNull(edgeToRemove, "edgeToRemove");
            return new CandidateEdit() {
                @Override public String description() { return "Remove edge " + edgeToRemove; }
                @Override public Graph applyTo(Graph g) {
                    Graph g2 = new EdgeListGraph(g);
                    Edge e = getEdgeByNames(g2, edgeToRemove);
                    if (e != null) g2.removeEdge(e);
                    return g2;
                }
                @Override public String key() { return "REM:" + stableEdgeKey(edgeToRemove); }
                @Override public Edge getEdge() { return edgeToRemove; }
            };
        }

        static CandidateEdit replaceEdge(Edge oldEdge, Edge newEdge) {
            Objects.requireNonNull(oldEdge, "oldEdge");
            Objects.requireNonNull(newEdge, "newEdge");
            return new CandidateEdit() {
                @Override public String description() { return "Replace " + oldEdge + " with " + newEdge; }
                @Override public Graph applyTo(Graph g) {
                    Graph g2 = new EdgeListGraph(g);
                    Edge eOld = getEdgeByNames(g2, oldEdge);
                    if (eOld != null) g2.removeEdge(eOld);
                    Edge eNew = rebindEdgeToGraph(g2, newEdge);
                    if (eNew != null) g2.addEdge(eNew);
                    return g2;
                }
                @Override public String key() { return "REP:" + stableEdgeKey(oldEdge) + "->" + stableEdgeKey(newEdge); }
                @Override public Edge getEdge() { return newEdge; }
            };
        }

        static CandidateEdit replaceEdges(String label, List<Edge> oldEdges, List<Edge> newEdges) {
            Objects.requireNonNull(label, "label");
            List<Edge> olds = List.copyOf(oldEdges == null ? List.of() : oldEdges);
            List<Edge> news = List.copyOf(newEdges == null ? List.of() : newEdges);

            return new CandidateEdit() {
                @Override public String description() { return label; }
                @Override public Graph applyTo(Graph g) {
                    Graph g2 = new EdgeListGraph(g);

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
                @Override public String key() {
                    List<String> parts = new ArrayList<>();
                    for (Edge oe : olds) parts.add("O:" + stableEdgeKey(oe));
                    for (Edge ne : news) parts.add("N:" + stableEdgeKey(ne));
                    Collections.sort(parts);
                    return "MULTI:" + label + ":" + String.join("|", parts);
                }
                @Override public Edge getEdge() { return news.isEmpty() ? null : news.get(0); }
                @Override public List<Edge> getEdges() { return news; }
            };
        }

        static String stableEdgeKey(Edge e) {
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

    public record ScoredCandidate(
            CandidateEdit edit,
            int violationsBaseline,
            int violationsAfter,
            double nodePAfter,
            double modelPBefore,
            double modelPAfter,
            int edgesAfter,
            boolean passesGuards
    ) {
        public int delta() { return violationsAfter - violationsBaseline; }
        public ScoredCandidate withPassesGuards(boolean v) {
            return new ScoredCandidate(edit, violationsBaseline, violationsAfter, nodePAfter, modelPBefore, modelPAfter, edgesAfter, v);
        }
    }

    private record GraphEval(int violations, double modelP, int nFacts) {}
    private record VertexContribution(Map<String, Boolean> violationByKey, Map<String, Double> pByKey) {}
    private record GlobalEvalCache(Map<String, VertexContribution> contribByVertexName) {}

    // -------------------- ordering (ported) --------------------

    private enum MoveType { REORIENT_SIMPLE, COLLIDER_FIX, REMOVE_EDGE, ADD_EDGE, OTHER }

    private static final Comparator<ScoredCandidate> CANONICAL_TABLE_ORDER = (a, b) -> {
        if (a == null && b == null) return 0;
        if (a == null) return 1;
        if (b == null) return -1;

        // Guards first
        if (a.passesGuards() != b.passesGuards()) return a.passesGuards() ? -1 : 1;
        if (!a.passesGuards()) return stableTieBreak(a, b);

        int c;

        // Δ violations ASC (more negative better)
        c = Integer.compare(a.delta(), b.delta());
        if (c != 0) return c;

        // Edges ASC
        c = Integer.compare(a.edgesAfter(), b.edgesAfter());
        if (c != 0) return c;

        // Node-P finite first then DESC
        c = finiteFirst(a.nodePAfter(), b.nodePAfter());
        if (c != 0) return c;
        c = -Double.compare(alphaLogOdds(a.nodePAfter(), 0.01), alphaLogOdds(b.nodePAfter(), 0.01));
        if (c != 0) return c;

        // Model-P finite first then DESC
        c = finiteFirst(a.modelPAfter(), b.modelPAfter());
        if (c != 0) return c;
        c = -Double.compare(alphaLogOdds(a.modelPAfter(), 0.01), alphaLogOdds(b.modelPAfter(), 0.01));
        if (c != 0) return c;

        return stableTieBreak(a, b);
    };

    private static int stableTieBreak(ScoredCandidate a, ScoredCandidate b) {
        String ka = (a.edit() == null || a.edit().key() == null) ? "" : a.edit().key();
        String kb = (b.edit() == null || b.edit().key() == null) ? "" : b.edit().key();
        int c = ka.compareTo(kb);
        if (c != 0) return c;

        String da = (a.edit() == null || a.edit().description() == null) ? "" : a.edit().description();
        String db = (b.edit() == null || b.edit().description() == null) ? "" : b.edit().description();
        return da.compareTo(db);
    }

    private static int finiteFirst(double a, double b) {
        boolean fa = Double.isFinite(a);
        boolean fb = Double.isFinite(b);
        if (fa == fb) return 0;
        return fa ? -1 : 1;
    }

    // -------------------- candidate enumeration (ported) --------------------

    private List<CandidateEdit> enumerateCandidates(Graph g, Node x, RepairGraphType gt) {
        if (g == null || x == null) return List.of(CandidateEdit.noOp());

        List<CandidateEdit> out = new ArrayList<>();
        out.add(CandidateEdit.noOp());

        Set<Node> pool = new LinkedHashSet<>(g.getNodes());
        pool.remove(x);

        // removals
        for (Edge e : new ArrayList<>(g.getEdges(x))) out.add(CandidateEdit.removeEdge(e));

        // single-edge replacements
        for (Edge e : new ArrayList<>(g.getEdges(x))) {
            Node y = e.getDistalNode(x);
            if (y == null) continue;
            for (Edge v : edgeMenuForPair(x, y, gt)) {
                if (edgeStructurallyEqual(e, v, x, y)) continue;
                out.add(CandidateEdit.replaceEdge(e, v));
            }
        }

        // additions
        for (Node y : pool) {
            if (y == null) continue;
            if (g.isAdjacentTo(x, y)) continue;
            for (Edge add : addMenuForPair(x, y, gt)) out.add(CandidateEdit.addEdge(add));
        }

        // multi-edge incident patterns (DAG/CPDAG/PDAG)
        if (gt == RepairGraphType.DAG || gt == RepairGraphType.CPDAG || gt == RepairGraphType.PDAG) {
            out.addAll(enumerateIncidentOrientationPatternMoves(g, x, gt));
        }

        // CPDAG-only collider pair flips
        if (gt == RepairGraphType.CPDAG) {
            out.addAll(enumerateCpdagColliderPairMoves(g, x));
        }

        return dedupCandidateEdits(out);
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

    private static boolean edgeStructurallyEqual(Edge a, Edge b, Node x, Node y) {
        if (a == null || b == null) return false;

        Endpoint aX, aY;
        if (a.getNode1().equals(x) && a.getNode2().equals(y)) {
            aX = a.getEndpoint1(); aY = a.getEndpoint2();
        } else if (a.getNode1().equals(y) && a.getNode2().equals(x)) {
            aX = a.getEndpoint2(); aY = a.getEndpoint1();
        } else return false;

        Endpoint bX, bY;
        if (b.getNode1().equals(x) && b.getNode2().equals(y)) {
            bX = b.getEndpoint1(); bY = b.getEndpoint2();
        } else if (b.getNode1().equals(y) && b.getNode2().equals(x)) {
            bX = b.getEndpoint2(); bY = b.getEndpoint1();
        } else return false;

        return aX == bX && aY == bY;
    }

    private List<Edge> edgeMenuForPair(Node x, Node y, RepairGraphType gt) {
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

    private List<Edge> addMenuForPair(Node x, Node y, RepairGraphType gt) {
        // same as edgeMenu but you may want to restrict optional adds; kept identical to panel
        return edgeMenuForPair(x, y, gt);
    }

    private List<CandidateEdit> enumerateIncidentOrientationPatternMoves(Graph g, Node x, RepairGraphType gt) {
        if (g == null || x == null) return List.of();

        List<Node> adj = new ArrayList<>(g.getAdjacentNodes(x));
        adj.sort(Comparator.comparing(Node::getName, Comparator.nullsLast(String::compareTo)));

        List<Edge> freeEdges = new ArrayList<>();
        for (Node y : adj) {
            Edge e = g.getEdge(x, y);
            if (e == null) continue;

            Endpoint ex = e.getEndpoint(x);
            Endpoint ey = e.getEndpoint(y);

            if (gt == RepairGraphType.DAG) {
                // in a true DAG there should be no free edges; be defensive
                if (!((ex == Endpoint.TAIL && ey == Endpoint.ARROW) || (ex == Endpoint.ARROW && ey == Endpoint.TAIL))) {
                    freeEdges.add(e);
                }
            } else {
                // CPDAG/PDAG: free if undirected tail-tail
                if (ex == Endpoint.TAIL && ey == Endpoint.TAIL) freeEdges.add(e);
            }
        }

        if (freeEdges.isEmpty()) return List.of();

        final int MAX_FREE = 12;
        final int MAX_PARENTS = 6;
        final int MAX_MOVES = 5000;

        if (freeEdges.size() > MAX_FREE) return List.of();

        List<CandidateEdit> out = new ArrayList<>();
        int m = freeEdges.size();
        int total = 1 << m;

        String xName = (x.getName() == null) ? "?" : x.getName();

        for (int mask = 0; mask < total && out.size() < MAX_MOVES; mask++) {
            if (Integer.bitCount(mask) > MAX_PARENTS) continue;

            List<Edge> olds = new ArrayList<>(m);
            List<Edge> news = new ArrayList<>(m);

            List<String> parents = new ArrayList<>();
            List<String> children = new ArrayList<>();

            boolean earlyReject = false;

            for (int i = 0; i < m; i++) {
                Edge old = freeEdges.get(i);
                Node y = old.getDistalNode(x);
                if (y == null) continue;

                boolean intoX = ((mask & (1 << i)) != 0);

                Edge ne = intoX
                        ? new Edge(y, x, Endpoint.TAIL, Endpoint.ARROW)
                        : new Edge(x, y, Endpoint.TAIL, Endpoint.ARROW);

                // cheap DAG cycle pre-prune if applicable
                if (gt == RepairGraphType.DAG) {
                    if (intoX && hasDirectedPath(g, x, y)) { earlyReject = true; break; }
                    if (!intoX && hasDirectedPath(g, y, x)) { earlyReject = true; break; }
                }

                olds.add(old);
                news.add(ne);

                String yn = (y.getName() == null) ? "?" : y.getName();
                if (intoX) parents.add(yn); else children.add(yn);
            }

            if (earlyReject || news.isEmpty()) continue;

            Collections.sort(parents);
            Collections.sort(children);

            String label = "Orient incident edges at " + xName
                    + " | Pa={" + String.join(",", parents) + "}"
                    + " | Ch={" + String.join(",", children) + "}";

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
            Edge exy = g.getEdge(x, y);
            if (exy == null) continue;

            for (int j = i + 1; j < adj.size(); j++) {
                Node z = adj.get(j);
                if (g.isAdjacentTo(y, z)) continue;

                Edge exz = g.getEdge(x, z);
                if (exz == null) continue;

                Endpoint endXy = exy.getEndpoint(x);
                Endpoint endXz = exz.getEndpoint(x);

                if (!(endXy == Endpoint.ARROW && endXz == Endpoint.ARROW)) {
                    Edge yToX = new Edge(y, x, Endpoint.TAIL, Endpoint.ARROW);
                    Edge zToX = new Edge(z, x, Endpoint.TAIL, Endpoint.ARROW);
                    out.add(CandidateEdit.replaceEdges(
                            "Orient collider " + y.getName() + "->" + x.getName() + "<-" + z.getName(),
                            List.of(exy, exz),
                            List.of(yToX, zToX)
                    ));
                }

                if (endXy == Endpoint.ARROW && endXz == Endpoint.ARROW) {
                    Edge xToY = new Edge(x, y, Endpoint.TAIL, Endpoint.ARROW);
                    Edge xToZ = new Edge(x, z, Endpoint.TAIL, Endpoint.ARROW);
                    out.add(CandidateEdit.replaceEdges(
                            "Orient away from collider " + y.getName() + "<-" + x.getName() + "->" + z.getName(),
                            List.of(exy, exz),
                            List.of(xToY, xToZ)
                    ));
                }
            }
        }

        return out;
    }

    // -------------------- evaluation (ported, UI-free) --------------------

    private double nodePValue(Graph g, Node vertexInOriginalGraph) throws InterruptedException {
        if (g == null || vertexInOriginalGraph == null) return Double.NaN;
        Node v = g.getNode(vertexInOriginalGraph.getName());
        if (v == null) return Double.NaN;

        ConditioningSetType type = requireConditioningSetType();
        List<IndependenceFact> facts = MarkovCheck.computeImpliedFactsForVertex(g, v, type);
        if (facts.isEmpty()) return Double.NaN;

        List<Double> pvals = pValuesForFacts(facts, CachedIndependenceQueries.Dedup.WITHIN_INPUT);
        return uniformityP(pvals);
    }

    private GraphEval evalGraphOnce(Graph g) throws InterruptedException {
        if (g == null) return new GraphEval(0, Double.NaN, 0);

        ConditioningSetType type = requireConditioningSetType();
        List<IndependenceFact> facts = MarkovCheck.computeAllImpliedFacts(g, type);
        if (facts.isEmpty()) return new GraphEval(0, Double.NaN, 0);

        List<CachedIndependenceQueries.Eval> evals =
                queries.evalAll(facts, CachedIndependenceQueries.Dedup.BY_CACHE_KEY);

        int violations = 0;
        List<Double> pvals = new ArrayList<>(evals.size());
        for (CachedIndependenceQueries.Eval e : evals) {
            if (!e.independent()) violations++;
            double p = e.pValue();
            if (!Double.isNaN(p) && p >= 0.0 && p <= 1.0) pvals.add(p);
        }

        return new GraphEval(violations, uniformityP(pvals), evals.size());
    }

    private int evalViolationsOnly(Graph g) throws InterruptedException {
        if (g == null) return 0;

        ConditioningSetType type = requireConditioningSetType();
        List<IndependenceFact> facts = MarkovCheck.computeAllImpliedFacts(g, type);
        if (facts.isEmpty()) return 0;

        List<CachedIndependenceQueries.Eval> evals =
                queries.evalAll(facts, CachedIndependenceQueries.Dedup.BY_CACHE_KEY);

        int violations = 0;
        for (CachedIndependenceQueries.Eval e : evals) {
            if (!e.independent()) violations++;
        }
        return violations;
    }

    private GlobalEvalCache buildBaselineCache(Graph g) throws InterruptedException {
        if (g == null) return new GlobalEvalCache(Map.of());

        Map<String, VertexContribution> out = new HashMap<>();
        for (Node v : g.getNodes()) {
            checkStop();
            if (v == null || v.getName() == null) continue;
            out.put(v.getName(), evalVertexContribution(g, v));
        }
        return new GlobalEvalCache(out);
    }

    private VertexContribution evalVertexContribution(Graph g, Node vInGraph) throws InterruptedException {
        if (g == null || vInGraph == null) return new VertexContribution(Map.of(), Map.of());

        Node v = g.getNode(vInGraph.getName());
        if (v == null) return new VertexContribution(Map.of(), Map.of());

        ConditioningSetType type = requireConditioningSetType();
        List<IndependenceFact> facts = MarkovCheck.computeImpliedFactsForVertex(g, v, type);
        if (facts.isEmpty()) return new VertexContribution(Map.of(), Map.of());

        Map<String, Boolean> viol = new HashMap<>();
        Map<String, Double> pByKey = new HashMap<>();

        for (IndependenceFact f : facts) {
            checkStop();
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

    private GraphEval evalGraphLocality(GlobalEvalCache baseCache,
                                        Graph candidateGraph,
                                        Set<String> affectedVertexNames,
                                        boolean computeModelP) throws InterruptedException {
        if (candidateGraph == null) return new GraphEval(0, Double.NaN, 0);

        Map<String, VertexContribution> contrib = new HashMap<>();
        if (baseCache != null && baseCache.contribByVertexName() != null) contrib.putAll(baseCache.contribByVertexName());

        if (affectedVertexNames != null) {
            for (String name : affectedVertexNames) {
                checkStop();
                if (name == null) continue;
                Node v = candidateGraph.getNode(name);
                if (v == null) { contrib.remove(name); continue; }
                contrib.put(name, evalVertexContribution(candidateGraph, v));
            }
        }

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
        for (boolean isViol : globalViolationByKey.values()) if (isViol) violations++;

        double modelP = Double.NaN;
        if (computeModelP && globalPByKey != null && globalPByKey.size() >= 2) {
            modelP = uniformityP(new ArrayList<>(globalPByKey.values()));
        }

        return new GraphEval(violations, modelP, globalViolationByKey.size());
    }

    // -------------------- guards + apply --------------------

    private boolean wouldPassGuards(Graph base, Node center, ScoredCandidate sc, RepairGraphType gt) throws InterruptedException {
        if (sc == null || sc.edit() == null || sc.edit().isNoOp()) return false;

        int currentEdges = base.getNumEdges();
        Graph cand = buildCandidateGraph(base, sc.edit(), gt);
        if (cand == null) return false;

        int baselineViol = sc.violationsBaseline();
        int afterViol = sc.violationsAfter();
        int afterEdges = sc.edgesAfter();

        double mpBefore = sc.modelPBefore();
        double mpAfter = sc.modelPAfter();

        return isProgress(baselineViol, afterViol, currentEdges, afterEdges, mpBefore, mpAfter);
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

            if (afterEdges == currentEdges
                    && Double.isFinite(mpBefore)
                    && Double.isFinite(mpAfter)
                    && (mpAfter - mpBefore) >= MIN_MP_GAIN) {
                return true;
            }
        }

        return false;
    }

    private boolean applyCandidate(CandidateEdit cand) throws InterruptedException {
        if (cand == null || cand.isNoOp()) return false;

        Graph base = canonicalizeBaseIfNeeded(safeCopy(graph));
        if (base == null) return false;

        Graph g2 = cand.applyTo(base);
        if (g2 == null) return false;

        // Canonicalize if needed
        if (graphType == RepairGraphType.CPDAG) {
            g2 = canonicalizeToCpdagOrNull(g2);
            if (g2 == null) return false;
        } else if (graphType == RepairGraphType.PAG) {
            // keep as-is (like panel)
        }

        if (g2.equals(base)) return false;

        // legality check
        if (!isLegalGraphType(g2, graphType)) return false;

        if (knowledge != null && knowledge.isViolatedBy(g2)) return false;

        this.graph = g2;
        return true;
    }

    // -------------------- canonicalization + legality --------------------

    private Graph canonicalizeBaseIfNeeded(Graph base) {
        if (base == null) return null;

        if (graphType == RepairGraphType.CPDAG) {
            return canonicalizeToCpdagOrNull(base);
        } else if (graphType == RepairGraphType.PAG) {
            return canonicalizeToPagOrNull(base);
        }
        return base;
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

    private boolean isLegalGraphType(Graph g, RepairGraphType gt) {
        if (g == null || gt == null) return false;
        return switch (gt) {
            case DAG -> g.paths().isLegalDag();
            case CPDAG -> g.paths().isLegalCpdag() || g.paths().isLegalPdag();
            case PDAG -> g.paths().isLegalPdag();
            case MAG -> g.paths().isLegalMag();
            case PAG -> g.paths().isLegalPag();
        };
    }

    // -------------------- fact key + CI --------------------

    /** Canonical key by names: (X,Y unordered; Z sorted). */
    public static String factKey(IndependenceFact f) {
        if (f == null || f.getX() == null || f.getY() == null) return UUID.randomUUID().toString();

        String a = Objects.toString(f.getX().getName(), "");
        String b = Objects.toString(f.getY().getName(), "");
        if (a.compareTo(b) > 0) { String t = a; a = b; b = t; }

        List<String> z = new ArrayList<>();
        for (Node n : f.getZ()) if (n != null && n.getName() != null) z.add(n.getName());
        Collections.sort(z);

        return a + "|" + b + "|" + String.join(",", z);
    }

    private IndependenceResult check(IndependenceFact f) throws InterruptedException {
        if (f == null) return null;
        Set<Node> z = new LinkedHashSet<>(f.getZ());
        return queries.checkIndependence(f.getX(), f.getY(), z);
    }

    private List<Double> pValuesForFacts(List<IndependenceFact> facts, CachedIndependenceQueries.Dedup dedup) throws InterruptedException {
        if (facts == null || facts.isEmpty()) return List.of();
        List<CachedIndependenceQueries.Eval> evals = queries.evalAll(facts, dedup);
        List<Double> p = new ArrayList<>(evals.size());
        for (CachedIndependenceQueries.Eval e : evals) {
            double pv = e.pValue();
            if (!Double.isNaN(pv) && pv >= 0.0 && pv <= 1.0) p.add(pv);
        }
        return p;
    }

    /**
     * UI-free uniformity p-value using a KS test against U(0,1).
     * Returns NaN if too few p-values.
     */
    private static double uniformityP(List<Double> pvals) {
        if (pvals == null) return Double.NaN;
        List<Double> clean = new ArrayList<>();
        for (Double d : pvals) {
            if (d == null) continue;
            double x = d;
            if (!Double.isNaN(x) && x >= 0.0 && x <= 1.0) clean.add(x);
        }
        if (clean.size() < 2) return Double.NaN;

        double[] sample = new double[clean.size()];
        for (int i = 0; i < clean.size(); i++) sample[i] = clean.get(i);

        KolmogorovSmirnovTest ks = new KolmogorovSmirnovTest();
        // commons-math has "kolmogorovSmirnovTest(RealDistribution, sample)"
        // Use UniformRealDistribution if you have it; else use the CDF-based method.
        // To avoid extra imports, we use the CDF callback form:
//        return ks.kolmogorovSmirnovTest(x -> x, sample);
        return ks.kolmogorovSmirnovTest(new UniformRealDistribution(0.0, 1.0), sample);
    }

    // -------------------- small helpers (ported) --------------------

    private static Graph safeCopy(Graph g) {
        if (g == null) return null;
        try { return g.copy(); }
        catch (Throwable t) { return new EdgeListGraph(g); }
    }

    private Node resolveNodeInGraph(Node n) {
        if (n == null || graph == null || n.getName() == null) return null;
        return graph.getNode(n.getName());
    }

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

    private static int naturalName(Node a, Node b) {
        String an = a == null ? "" : Objects.toString(a.getName(), "");
        String bn = b == null ? "" : Objects.toString(b.getName(), "");
        return an.compareTo(bn);
    }

    private static double alphaLogOdds(double p, double alpha) {
        if (!Double.isFinite(p)) return -50.0;
        if (!Double.isFinite(alpha) || alpha <= 0.0 || alpha >= 1.0)
            throw new IllegalArgumentException("alpha must be in (0,1)");
        final double eps = 1e-12;
        double q = Math.min(1.0 - eps, Math.max(eps, p));
        double a = Math.min(1.0 - eps, Math.max(eps, alpha));
        return (Math.log(q) - Math.log(1.0 - q)) - (Math.log(a) - Math.log(1.0 - a));
    }

    private static boolean hasDirectedPath(Graph g, Node from, Node to) {
        if (g == null || from == null || to == null) return false;
        try { return g.paths().existsDirectedPath(from, to); }
        catch (Throwable t) { return false; }
    }

    private static Edge rebindEdgeToGraph(Graph g, Edge e) {
        if (g == null || e == null) return null;
        Node a0 = e.getNode1();
        Node b0 = e.getNode2();
        if (a0 == null || b0 == null) return null;
        String an = a0.getName(), bn = b0.getName();
        if (an == null || bn == null) return null;
        Node a = g.getNode(an), b = g.getNode(bn);
        if (a == null || b == null) return null;
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

    private static Set<String> affectedVertices(Graph base, Node x, Graph candidate) {
        Set<String> affected = new LinkedHashSet<>();
        if (x != null && x.getName() != null) affected.add(x.getName());
        if (base == null || candidate == null || x == null || x.getName() == null) return affected;

        Node xb = base.getNode(x.getName());
        Node xc = candidate.getNode(x.getName());
        if (xb == null || xc == null) return affected;

        Set<String> nb = new HashSet<>();
        for (Node n : base.getAdjacentNodes(xb)) if (n != null && n.getName() != null) nb.add(n.getName());

        Set<String> nc = new HashSet<>();
        for (Node n : candidate.getAdjacentNodes(xc)) if (n != null && n.getName() != null) nc.add(n.getName());

        for (String name : nb) if (!nc.contains(name)) affected.add(name);
        for (String name : nc) if (!nb.contains(name)) affected.add(name);

        affected.addAll(nc);
        return affected;
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
        }

        try {
            if (!isLegalGraphType(g2, gt)) return null;
        } catch (Exception ignored) {
            return null;
        }

        return g2;
    }

    private static Graph seedDagFromAnyGraph(Graph g) {
        if (g == null) return null;

        List<Node> nodes = new ArrayList<>(g.getNodes());
        nodes.sort(Comparator.comparing(Node::getName, Comparator.nullsLast(String::compareTo)));
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

            Node a = dag.getNode(an0);
            Node b = dag.getNode(bn0);
            if (a == null || b == null || a.equals(b)) continue;

            String key = (an0.compareTo(bn0) <= 0) ? (an0 + "|" + bn0) : (bn0 + "|" + an0);
            if (!seenPairs.add(key)) continue;

            int ia = idx.getOrDefault(an0, 0);
            int ib = idx.getOrDefault(bn0, 0);

            if (ia <= ib) dag.addEdge(new Edge(a, b, Endpoint.TAIL, Endpoint.ARROW));
            else dag.addEdge(new Edge(b, a, Endpoint.TAIL, Endpoint.ARROW));
        }

        return dag.paths().isLegalDag() ? dag : null;
    }

    private static MoveType moveType(CandidateEdit e) {
        if (e == null) return MoveType.OTHER;
        String s = ((e.key() == null ? "" : e.key()) + " " + (e.description() == null ? "" : e.description())).toLowerCase();

        if (s.contains("rem:") || s.contains("remove") || s.contains("delete")) return MoveType.REMOVE_EDGE;
        if (s.contains("add:") || s.contains("add") || s.contains("insert")) return MoveType.ADD_EDGE;
        if (s.contains("orient collider") || s.contains("orient away from collider")) return MoveType.COLLIDER_FIX;
        if (s.contains("rep:") || s.contains("replace") || s.contains("reorient") || s.contains("orient") || s.contains("flip") || s.contains("reverse")) {
            return MoveType.REORIENT_SIMPLE;
        }
        return MoveType.OTHER;
    }

    private ConditioningSetType requireConditioningSetType() {
        if (conditioningSetType == null) {
            throw new IllegalStateException("ConditioningSetType not set. Call setConditioningSetType(...) before running.");
        }
        return conditioningSetType;
    }

    private void ensureReady() {
        if (test == null) throw new IllegalStateException("IndependenceTest not set.");
        if (queries == null) queries = new CachedIndependenceQueries(test);
        if (graph == null) throw new IllegalStateException("Graph not set.");
        if (conditioningSetType == null) {
            throw new IllegalStateException("ConditioningSetType not set. Call setConditioningSetType(...) before running.");
        }
    }

    private static boolean sameVariableNames(List<Node> a, List<Node> b) {
        if (a == null || b == null) return false;
        if (a.size() != b.size()) return false;

        for (int i = 0; i < a.size(); i++) {
            String an = a.get(i) == null ? null : a.get(i).getName();
            String bn = b.get(i) == null ? null : b.get(i).getName();
            if (!Objects.equals(an, bn)) return false;
        }
        return true;
    }

    private void checkStop() throws InterruptedException {
        if (stop.get() || Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("VertexRepairSearch interrupted/stopped.");
        }
    }

    private void log(String msg) {
        if (!verbose) return;
        TetradLogger.getInstance().log(msg);
    }
}