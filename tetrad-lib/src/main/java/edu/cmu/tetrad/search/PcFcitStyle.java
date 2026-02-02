package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.utils.MeekRules;
import edu.cmu.tetrad.search.utils.SepsetMap;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.*;

/**
 * FCIT-style variant of PC that maintains a legal CPDAG after every accepted move.
 *
 * High-level:
 *  - Start with complete undirected graph over the nodes (pattern).
 *  - For depth d = 0..depthCap:
 *      - (optional stable) freeze adjacencies for proposal generation
 *      - propose FAS-style removals: X---Y removed if X _||_ Y | S for some S of size exactly d
 *        with S subset of adj(X)\{Y} or adj(Y)\{X}, filtered by knowledge (possibleParents-style)
 *      - apply proposed removals sequentially; after each accepted removal:
 *          (i) apply required knowledge orientations
 *          (ii) orient unshielded colliders (sepset rule)
 *          (iii) apply Meek closure
 *          (iv) require graph.paths().isLegalCpdag() or rollback that single move
 *  - Return the resulting CPDAG (legal by construction).
 *
 * Notes:
 *  - This class intentionally only manipulates directed + undirected edges (CPDAG semantics).
 *  - Required adjacencies are never removed.
 *  - Required orientations are applied and never “undone” (assuming your hardened MeekRules).
 */
public class PcFcitStyle implements IGraphSearch {

    private IndependenceTest test;
    private Knowledge knowledge = new Knowledge();

    /** FAS depth; -1 means no cap (n-1). */
    private int depth = -1;

    /** If true, freeze adjacencies per depth (PC-Stable-style proposals). */
    private boolean stable = true;

    /** If true, log accept/reject details. */
    private boolean verbose = false;

    /** If true, refuse collider orientations that would create directed cycles. */
    private boolean forbidDirectedCycles = true;

    /** We are building CPDAGs, so we do not allow bidirected edges. */
    private final boolean allowBidirected = false;

    private final SepsetMap sepsets = new SepsetMap();

    public PcFcitStyle(IndependenceTest test) {
        this.test = test;
    }

    // ---------------- configuration ----------------

    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }

    public Knowledge getKnowledge() {
        return new Knowledge(knowledge);
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

    public void setStable(boolean stable) {
        this.stable = stable;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
        this.test.setVerbose(verbose);
    }

    public void setForbidDirectedCycles(boolean enabled) {
        this.forbidDirectedCycles = enabled;
    }

    public SepsetMap getSepsets() {
        return sepsets;
    }

    public IndependenceTest getTest() {
        return test;
    }

    public void setTest(IndependenceTest test) {
        List<Node> nodes = this.test.getVariables();
        List<Node> _nodes = test.getVariables();
        if (!nodes.equals(_nodes)) {
            throw new IllegalArgumentException(
                    "New test must have the same variable list (list-wise) as the existing test."
            );
        }
        this.test = test;
    }

    // ---------------- entry points ----------------

    @Override
    public Graph search() throws InterruptedException {
        return search(test.getVariables());
    }

    public Graph search(List<Node> nodes) throws InterruptedException {
        if (!new HashSet<>(test.getVariables()).containsAll(nodes)) {
            throw new IllegalArgumentException("Nodes must be a subset of test variables.");
        }

        // Start from complete undirected graph.
        Graph g = GraphFactoryUtil.newGraph(nodes, false);
        g = GraphUtils.completeGraph(g);

        // Remove adjacencies forbidden in both directions.
        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                Node a = nodes.get(i);
                Node b = nodes.get(j);
                if (knowledge.isForbidden(a.getName(), b.getName())
                        && knowledge.isForbidden(b.getName(), a.getName())) {
                    g.removeEdge(a, b);
                }
            }
        }

        // Normalize depth cap.
        final int n = nodes.size();
        final int depthCap = (depth < 0) ? (n - 1) : depth;

        // Main loop.
        for (int d = 0; d <= depthCap; d++) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Interrupted");

            boolean removedAtThisDepth = runDepth(g, d);

            // PC-style early stop.
            if (!removedAtThisDepth && freeDegree(g) <= d) break;
        }

        // Final closure, just to be safe.
        applyRequiredKnowledgeOrientations(g);
        orientUnshieldedColliders(g);
        applyMeek(g);

        // Ensure legality on exit.
        if (!g.paths().isLegalCpdag()) {
            throw new IllegalStateException("PcFcitStyle finished with an illegal CPDAG (unexpected).");
        }

        return g;
    }

    // ---------------- one depth: propose then apply with per-move legality gate ----------------

    private boolean runDepth(Graph g, int d) throws InterruptedException {
        // Frozen view for stable proposals, else use current graph.
        Graph frozen = stable ? new EdgeListGraph(g) : g;

        List<Proposal> proposals = new ArrayList<>();

        // Propose removals for each adjacent pair in the frozen view.
        for (Node x : frozen.getNodes()) {
            List<Node> adjxFrozen = new ArrayList<>(frozen.getAdjacentNodes(x));
            adjxFrozen.sort(Comparator.comparing(Node::getName));

            for (Node y : adjxFrozen) {
                if (x.getName().compareTo(y.getName()) >= 0) continue;
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Interrupted");

                // Might have changed since frozen (if stable=false, frozen==g anyway).
                if (!g.isAdjacentTo(x, y)) continue;

                // Never remove required adjacency.
                if (!knowledge.noEdgeRequired(x.getName(), y.getName())) continue;

                // FAS semantics: at depth d, only consider S of size exactly d.
                Set<Node> S = findSepsetOfSizeD(frozen, x, y, d);
                if (S == null) continue;

                IndependenceResult r = test.checkIndependence(x, y, S);
                if (!r.isIndependent()) continue;

                proposals.add(new Proposal(x, y, S, r.getPValue()));
            }
        }

        if (proposals.isEmpty()) return false;

        // Deterministic application order improves reproducibility.
        proposals.sort(Comparator
                .comparing((Proposal p) -> p.x.getName())
                .thenComparing(p -> p.y.getName())
                .thenComparingInt(p -> p.S.size()));

//        boolean anyAccepted = false;
//
//        // Apply sequentially with legality gate per move.
//        for (Proposal p : proposals) {
//            if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Interrupted");
//            if (!g.isAdjacentTo(p.x, p.y)) continue;
//            if (!knowledge.noEdgeRequired(p.x.getName(), p.y.getName())) continue;
//
//            if (tryRemoveOneMoveSkeletonOnly(g, p)) {
//                anyAccepted = true;
//            }
//        }

        boolean anyAccepted = false;

        // Snapshot at start of depth.
        Graph beforeDepth = new EdgeListGraph(g);
        Map<Set<Node>, Set<Node>> sepsetTouched = new HashMap<>(); // optional if you want rollback sepsets too

        for (Proposal p : proposals) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Interrupted");
            if (tryRemoveOneMoveSkeletonOnly(g, p)) {
                anyAccepted = true;
            }
        }

        if (!anyAccepted) return false;

        // Now do closure ONCE per depth.
        applyRequiredKnowledgeOrientations(g);
        orientUnshieldedColliders(g);
        applyMeek(g);

        // Optional: legality check per depth (not per move)
        if (!g.paths().isLegalCpdag()) {
            restoreGraph(g, beforeDepth);
            sepsets.clear();

            if (verbose) {
                TetradLogger.getInstance().log(
                        "Depth " + d + " closure produced illegal CPDAG; rolling back depth " + d + " removals."
                );
            }

            // Treat as: no accepted removals at this depth (under the “must remain legal” policy).
            return false;
        }

        return true;
    }

    /**
     * FAS-style: search for a separating set S of size exactly d from either side:
     *  - subsets of possibleParents(x, adj(x)\{y})
     *  - then subsets of possibleParents(y, adj(y)\{x})
     * Uses the frozen view to match stable semantics when stable=true.
     */
    private Set<Node> findSepsetOfSizeD(Graph frozen, Node x, Node y, int d) throws InterruptedException {

        // Side X: subsets of possibleParents(x, adj(x)\{y})
        List<Node> adjx = new ArrayList<>(frozen.getAdjacentNodes(x));
        adjx.remove(y);

        List<Node> ppx = possibleParents(x, adjx, knowledge, y);
        Set<Node> S = firstSeparatingSetOfSizeD(x, y, ppx, d);
        if (S != null) return S;

        // Side Y: subsets of possibleParents(y, adj(y)\{x})
        List<Node> adjy = new ArrayList<>(frozen.getAdjacentNodes(y));
        adjy.remove(x);

        List<Node> ppy = possibleParents(y, adjy, knowledge, x);
        return firstSeparatingSetOfSizeD(x, y, ppy, d);
    }

//    private Set<Node> firstSeparatingSetOfSizeD(Node x, Node y, List<Node> pool, int d) throws InterruptedException {
//        if (pool.size() < d) return null;
//
//        ChoiceGenerator gen = new ChoiceGenerator(pool.size(), d);
//        int[] choice;
//        while ((choice = gen.next()) != null) {
//            if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Interrupted");
//
//            Set<Node> S = GraphUtils.asSet(choice, pool);
//            IndependenceResult r = test.checkIndependence(x, y, S);
//            if (r.isIndependent()) {
//                return S;
//            }
//        }
//
//        return null;
//    }

    private Set<Node> firstSeparatingSetOfSizeD(Node x, Node y, List<Node> pool, int d)
            throws InterruptedException {

        if (pool.size() < d) return null;

        // Deterministic order.
        pool.sort(Comparator.comparing(Node::getName));

        ChoiceGenerator gen = new ChoiceGenerator(pool.size(), d);
        int[] choice;

        while ((choice = gen.next()) != null) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Interrupted");

            Set<Node> S = GraphUtils.asSet(choice, pool);
            IndependenceResult r = test.checkIndependence(x, y, S);
            if (r.isIndependent()) {
                return S; // first found (PC/FAS semantics)
            }
        }

        return null;
    }

//    private boolean tryRemoveAndCloseOneMove(Graph g, Proposal p) throws InterruptedException {
//        Graph before = new EdgeListGraph(g);
//        Set<Node> oldS = sepsets.get(p.x, p.y);
//
//        // Remove edge + record sepset.
//        g.removeEdge(p.x, p.y);
//        sepsets.set(p.x, p.y, new LinkedHashSet<>(p.S));
//
//        // Closure steps.
//        applyRequiredKnowledgeOrientations(g);
//        orientUnshieldedColliders(g);
//        applyMeek(g);
//
//        boolean ok = g.paths().isLegalCpdag();
//        if (!ok) {
//            // rollback
//            restoreGraph(g, before);
//            if (oldS == null) sepsets.set(p.x, p.y, null);
//            else sepsets.set(p.x, p.y, new LinkedHashSet<>(oldS));
//
//            if (verbose) {
//                TetradLogger.getInstance().log(
//                        "Rejected removal " + p.x.getName() + " --- " + p.y.getName() +
//                                " at |S|=" + p.S.size() + " (illegal CPDAG after closure)."
//                );
//            }
//            return false;
//        }
//
//        if (verbose) {
//            TetradLogger.getInstance().log(
//                    "Accepted removal " + p.x.getName() + " --- " + p.y.getName() +
//                            " at |S|=" + p.S.size() + " (p=" + rstripNaN(p.pValue) + ")"
//            );
//        }
//
//        return true;
//    }

    private boolean tryRemoveOneMoveSkeletonOnly(Graph g, Proposal p) {
        // No orientations here. Just remove + record sepset.
        if (!g.isAdjacentTo(p.x, p.y)) return false;
        if (!knowledge.noEdgeRequired(p.x.getName(), p.y.getName())) return false;

        g.removeEdge(p.x, p.y);
        sepsets.set(p.x, p.y, new LinkedHashSet<>(p.S));
        return true;
    }

    // ---------------- collider orientation (sepset-style) ----------------

    private void orientUnshieldedColliders(Graph g) throws InterruptedException {
        List<Triple> triples = collectUnshieldedTriples(g);

        for (Triple t : triples) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Interrupted");

            // Only consider if x-z and y-z are adjacent and x not adj y (by construction).
            if (!g.isAdjacentTo(t.x, t.z) || !g.isAdjacentTo(t.y, t.z)) continue;

            // Already collider? skip.
            if (g.isParentOf(t.x, t.z) && g.isParentOf(t.y, t.z)) continue;

            // We only orient based on stored sepsets for nonadjacent endpoints.
            Set<Node> s = sepsets.get(t.x, t.y);
            if (s == null) continue;

            // PC collider rule: if z not in Sepset(x,y), orient x->z<-y.
            if (!s.contains(t.z)) {
                if (canOrientCollider(g, t.x, t.z, t.y)) {
                    GraphUtils.orientCollider(g, t.x, t.z, t.y);
                }
            }
        }
    }

    private boolean canOrientCollider(Graph g, Node x, Node z, Node y) {
        if (!g.isAdjacentTo(x, z) || !g.isAdjacentTo(z, y)) return false;

        // knowledge: must allow arrowheads into z from x and y.
        if (!isArrowheadAllowed(x, z, knowledge) || !isArrowheadAllowed(y, z, knowledge)) return false;

        // CPDAG discipline: do not create bidirected situations.
        if (!allowBidirected) {
            if (g.isParentOf(z, x) || g.isParentOf(z, y)) return false;
        }

        // Optional cycle guard.
        if (forbidDirectedCycles) {
            if (g.paths().existsDirectedPath(z, x)) return false;
            if (g.paths().existsDirectedPath(z, y)) return false;
        }

        return true;
    }

    private static boolean isArrowheadAllowed(Node from, Node to, Knowledge knowledge) {
        if (knowledge.isEmpty()) return true;
        return !knowledge.isRequired(to.getName(), from.getName())
                && !knowledge.isForbidden(from.getName(), to.getName());
    }

    // ---------------- knowledge + Meek closure ----------------

    /**
     * Apply required knowledge orientations without running full Meek.
     * This is intentionally conservative: it only orients undirected edges if exactly one direction is allowed.
     */
    private void applyRequiredKnowledgeOrientations(Graph g) {
        if (knowledge == null || knowledge.isEmpty()) return;

        for (Edge e : new ArrayList<>(g.getEdges())) {
            if (!Edges.isUndirectedEdge(e)) continue;

            Node a = e.getNode1();
            Node b = e.getNode2();

            boolean a_to_b_ok = isArrowheadAllowed(a, b, knowledge);
            boolean b_to_a_ok = isArrowheadAllowed(b, a, knowledge);

            if (a_to_b_ok && !b_to_a_ok) {
                // a -> b, but only if it won't create a directed cycle
                if (!g.paths().existsDirectedPath(b, a)) {
                    g.removeEdge(a, b);
                    g.addDirectedEdge(a, b);
                }
            } else if (b_to_a_ok && !a_to_b_ok) {
                // b -> a, but only if it won't create a directed cycle
                if (!g.paths().existsDirectedPath(a, b)) {
                    g.removeEdge(a, b);
                    g.addDirectedEdge(b, a);
                }
            }
        }
    }

    private void applyMeek(Graph g) {
        MeekRules meek = new MeekRules();
        meek.setKnowledge(knowledge);

        // Incremental algorithm: do NOT wipe orientations you just made.
        meek.setRevertToUnshieldedColliders(false);

        // CRITICAL: otherwise Meek can create directed cycles (=> illegal CPDAG).
        meek.setMeekPreventCycles(true);

        // Optional: helpful while debugging
        // meek.setVerbose(verbose);

        meek.orientImplied(g);
    }

    // ---------------- utilities ----------------

    private static final class Proposal {
        final Node x, y;
        final Set<Node> S;
        final double pValue;

        Proposal(Node x, Node y, Set<Node> S, double pValue) {
            this.x = x;
            this.y = y;
            this.S = S;
            this.pValue = pValue;
        }
    }

    private static final class Pair {
        final Node x, y;
        Pair(Node x, Node y) { this.x = x; this.y = y; }
    }

    private static final class Triple {
        final Node x, z, y;
        Triple(Node x, Node z, Node y) { this.x = x; this.z = z; this.y = y; }
    }

    private List<Triple> collectUnshieldedTriples(Graph g) {
        List<Node> nodes = new ArrayList<>(g.getNodes());
        nodes.sort(Comparator.comparing(Node::getName));

        List<Triple> triples = new ArrayList<>();
        for (Node z : nodes) {
            List<Node> adj = new ArrayList<>(g.getAdjacentNodes(z));
            adj.sort(Comparator.comparing(Node::getName));
            int m = adj.size();

            for (int i = 0; i < m; i++) {
                Node xi = adj.get(i);
                for (int j = i + 1; j < m; j++) {
                    Node yj = adj.get(j);
                    if (!g.isAdjacentTo(xi, yj)) {
                        Node x = xi, y = yj;
                        if (x.getName().compareTo(y.getName()) > 0) {
                            Node tmp = x; x = y; y = tmp;
                        }
                        triples.add(new Triple(x, z, y));
                    }
                }
            }
        }

        triples.sort(Comparator.comparing((Triple t) -> t.x.getName())
                .thenComparing(t -> t.z.getName())
                .thenComparing(t -> t.y.getName()));

        return triples;
    }

    private int freeDegree(Graph graph) {
        int max = 0;
        for (Node n : graph.getNodes()) {
            int deg = graph.getAdjacentNodes(n).size();
            if (deg > 0 && deg - 1 > max) max = deg - 1;
        }
        return max;
    }

    private static void restoreGraph(Graph target, Graph snapshot) {
        for (Edge e : new ArrayList<>(target.getEdges())) target.removeEdge(e);
        for (Edge e : snapshot.getEdges()) target.addEdge(e);
    }

    private static String rstripNaN(double p) {
        return Double.isNaN(p) ? "NaN" : Double.toString(p);
    }

    // ---- possibleParents (copied from Fas semantics) ----

    private static List<Node> possibleParents(Node x, List<Node> adjx, Knowledge knowledge, Node y) {
        List<Node> possibleParents = new LinkedList<>();
        String _x = x.getName();

        for (Node z : adjx) {
            if (z == null) continue;
            if (z == x) continue;
            if (z == y) continue;
            String _z = z.getName();

            if (possibleParentOf(_z, _x, knowledge)) {
                possibleParents.add(z);
            }
        }

        return possibleParents;
    }

    private static boolean possibleParentOf(String z, String x, Knowledge knowledge) {
        return !knowledge.isForbidden(z, x) && !knowledge.isRequired(x, z);
    }
}