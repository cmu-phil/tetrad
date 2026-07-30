package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.*;
import java.util.concurrent.*;

/**
 * The PagLegalityCheck class provides utility methods for validating the legality of Partial Ancestral Graphs (PAGs)
 * and Maximal Ancestral Graphs (MAGs). It includes methods to assess legality both verbosely, with detailed reasons,
 * and quietly, returning only a boolean outcome.
 * <p>
 * The quiet methods are staged for speed: cheap necessary conditions are checked first (endpoint syntax, ancestrality
 * of the definite directed part, closure under the sepset-free FCI orientation rules), and the expensive arbiter
 * (Zhang MAG construction, MAG legality, MAG-to-PAG round trip) runs only if all prefilters pass. In a search loop
 * where most candidates are illegal, most candidates are rejected before the round trip.
 * <p>
 * The verbose methods retain the original (slower) logic so that diagnostic messages are unchanged.
 */
public class PagLegalityCheck {

    /**
     * Shared executor for the timeout wrappers. Daemon threads so it never blocks JVM exit; cached so we do not pay
     * thread creation per call. NOTE: cancellation is cooperative -- the maximality loop polls interruption, but the
     * MagToPag round trip will run to completion in the background if it does not.
     */
    private static final ExecutorService TIMEOUT_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "pag-legality-check");
        t.setDaemon(true);
        return t;
    });

    /**
     * Default private constructor to prevent instantiation of the PagLegalityCheck class.
     * <p>
     * This class provides static utility methods for checking the legality of Partial Ancestral Graphs (PAG) and
     * Maximal Ancestral Graphs (MAG). Since the class is utility-based, it should not be instantiated.
     */
    private PagLegalityCheck() {

    }

    // ============================================================
    // Timeout wrappers
    // ============================================================

    /**
     * Runs {@link #isLegalPag(Graph, Set)} with a timeout.
     *
     * @param pag            the PAG to check
     * @param selection      the selection set
     * @param timeoutSeconds maximum seconds to wait
     * @return A LegalPagRet object indicating whether the PAG is legal or not, along with a reason if it is not legal,
     * if the check completed within the timeout
     * @throws RuntimeException if the check fails, is interrupted, or times out (a timeout should be treated by the
     *                          caller as a failed legality check, i.e. the surgery is reverted)
     */
    public static LegalPagRet isLegalPag(Graph pag, Set<Node> selection, int timeoutSeconds) {
        Future<LegalPagRet> future = TIMEOUT_EXECUTOR.submit(() -> isLegalPag(pag, selection));
        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            TetradLogger.getInstance().log("Timeout on PAG legality check.");
            throw new RuntimeException("Timeout");
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted");
        } catch (ExecutionException e) {
            future.cancel(true);
            throw new RuntimeException("Execution failed");
        }
    }

    /**
     * Runs {@link #isLegalMag(Graph, Set)} with a timeout.
     *
     * @param mag            the MAG to check
     * @param selection      the selection set
     * @param timeoutSeconds maximum seconds to wait
     * @return A LegalMagRet object indicating whether the MAG is legal or not, along with a reason if it is not legal,
     * if the check completed within the timeout
     * @throws RuntimeException if the check fails, is interrupted, or times out
     */
    public static LegalMagRet isLegalMag(Graph mag, Set<Node> selection, int timeoutSeconds) {
        Future<LegalMagRet> future = TIMEOUT_EXECUTOR.submit(() -> isLegalMag(mag, selection));
        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            TetradLogger.getInstance().log("Timeout on MAG legality check.");
            throw new RuntimeException("Timeout");
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted");
        } catch (ExecutionException e) {
            future.cancel(true);
            throw new RuntimeException("Execution failed");
        }
    }

    /**
     * Runs {@link #isLegalPagQuiet(Graph, Set)} with a timeout.
     *
     * @param pag            the PAG to check
     * @param selection      the selection set
     * @param timeoutSeconds maximum seconds to wait
     * @return true if legal and completed within the timeout
     * @throws RuntimeException if the check fails, is interrupted, or times out
     */
    public static boolean isLegalPagQuiet(Graph pag, Set<Node> selection, int timeoutSeconds) {
        Future<Boolean> future = TIMEOUT_EXECUTOR.submit(() -> isLegalPagQuiet(pag, selection));
        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new RuntimeException("Timeout");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            throw new RuntimeException("Interrupted");
        } catch (ExecutionException e) {
            future.cancel(true);
            throw new RuntimeException("Execution failed");
        }
    }

    // ============================================================
    // Verbose checks (original logic, diagnostics unchanged)
    // ============================================================

    /**
     * Checks if the provided Partial Ancestral Graph (PAG) is a legal PAG.
     * <p>
     * This is the verbose (diagnostic) path. It intentionally uses the exact, unbounded MAG-to-PAG round trip so that
     * a "legal" verdict is a certificate; there is no bounded variant, since bounding the discriminating path search
     * makes the check approximate.
     *
     * @param pag       The Partial Ancestral Graph (PAG) to be checked
     * @param selection The set of nodes to be conditioned on
     * @return A LegalPagRet object indicating whether the PAG is legal or not, along with a reason if it is not legal.
     */
    public static LegalPagRet isLegalPag(Graph pag, Set<Node> selection) {
        for (Node n : pag.getNodes()) {
            if (n.getNodeType() != NodeType.MEASURED) {
                return new LegalPagRet(false, "Node " + n + " is not measured");
            }
        }

        Graph mag;
        try {
            mag = GraphTransforms.zhangMagFromPag(pag);
        } catch (Exception e) {
            return new LegalPagRet(false, "PAG to MAG failed");
        }
        LegalMagRet legalMag = isLegalMag(mag, selection);

        if (!legalMag.isLegalMag()) {
            return new LegalPagRet(false, legalMag.getReason() + " in a MAG implied by this graph");
        }

        Graph pag2;
        try {
            MagToPag magToPag = new MagToPag(mag);
            pag2 = magToPag.convert(false, false);
        } catch (IllegalStateException e) {
            String reason = "Legal PAG status could not be determined";
            return new LegalPagRet(false, reason);
        }

        if (!pag.equals(pag2)) {
            String edgeMismatch = "";

            for (Edge e : pag.getEdges()) {
                Edge e2 = pag2.getEdge(e.getNode1(), e.getNode2());
                if (!e.equals(e2)) {
                    edgeMismatch = "For example, the original PAG has edge " + e + " whereas the reconstituted graph has edge " + e2;
                    break;
                }
            }

            String reason = "The MAG implied by this graph was a legal MAG, but one cannot recover the original graph "
                    + "by finding the PAG of an implied MAG -- this graph may lie between a MAG and a PAG";

            if (!edgeMismatch.isEmpty()) {
                reason += ". " + edgeMismatch;
                return new LegalPagRet(false, reason);
            }
        }

        return new LegalPagRet(true, "This is a legal PAG");
    }

    /**
     * Determines whether the given graph is a legal Maximal Ancestral Graph (MAG). This is the verbose (diagnostic)
     * path; use {@link #isLegalMagQuiet(Graph, Set)} in inner loops.
     *
     * @param mag       the graph to be checked
     * @param selection the set of nodes to be conditioned on
     * @return a LegalMagRet object indicating whether the graph is legal and providing an error message if it is not
     */
    public static LegalMagRet isLegalMag(Graph mag, Set<Node> selection) {
        for (Node n : mag.getNodes()) {
            if (n.getNodeType() == NodeType.LATENT) {
                return new LegalMagRet(false, "Node " + n + " is not measured");
            }
        }

        List<Node> nodes = mag.getNodes();

        for (Edge edge : mag.getEdges()) {
            Node x = edge.getNode1();
            Node y = edge.getNode2();

            if (!mag.isAdjacentTo(x, y)) continue;

            if (mag.getEdges(x, y).size() > 1) {
                return new LegalMagRet(false, "There is more than one edge between " + x + " and " + y);
            }

            if (!(Edges.isDirectedEdge(edge) || Edges.isBidirectedEdge(edge) || Edges.isUndirectedEdge(edge))) {
                return new LegalMagRet(false, "Edge " + edge + " should be directed, bidirected, or undirected");
            }
        }

        for (Node n : mag.getNodes()) {
            if (mag.paths().existsDirectedPath(n, n)) {
                return new LegalMagRet(false, "Acyclicity violated: There is a directed cyclic path from " + n + " to itself");
            }
        }

        for (Edge e : mag.getEdges()) {
            Node x = e.getNode1();
            Node y = e.getNode2();

            if (Edges.isBidirectedEdge(e)) {
                if (mag.paths().existsDirectedPath(x, y)) {
                    return new LegalMagRet(false, "Bidirected edge semantics is violated: Directed path exists from " + x + " to " + y + ".");
                }

                if (mag.paths().existsDirectedPath(y, x)) {
                    return new LegalMagRet(false, "Bidirected edge semantics is violated: Directed path exists from " + y + " to " + x + ".");
                }
            }
        }

        Set<Node> sel = (selection == null) ? Collections.emptySet() : new HashSet<>(selection);

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                Node x = nodes.get(i);
                Node y = nodes.get(j);

                if (!mag.isAdjacentTo(x, y)) {
                    if (mag.paths().existsInducingPath(x, y, sel)) {
                        List<Node> path = mag.paths().getInducingPath(x, y, selection);

                        return new LegalMagRet(false, "Not maximal: Inducing path exists between non-adjacent " + x + " and " + y + " " + path);
                    }
                }
            }
        }

        for (Edge edge : mag.getEdges()) {
            Node x = edge.getNode1();
            Node y = edge.getNode2();

            if (Edges.isUndirectedEdge(edge)) {
                for (Node z : mag.getAdjacentNodes(x)) {
                    Edge zx = mag.getEdge(z, x);
                    if (mag.isParentOf(z, x) || Edges.isBidirectedEdge(zx)) {
                        return new LegalMagRet(false, "Undirected edge constraint violated: " + z + " is a parent or spouse of " + x);
                    }
                }

                for (Node z : mag.getAdjacentNodes(y)) {
                    Edge zy = mag.getEdge(z, y);
                    if (mag.isParentOf(z, y) || Edges.isBidirectedEdge(zy)) {
                        return new LegalMagRet(false, "Undirected edge constraint violated: " + z + " is a parent or spouse of " + y);
                    }
                }
            }
        }

        return new LegalMagRet(true, "This is a legal MAG");
    }

    // ============================================================
    // Quiet checks (staged, fast)
    // ============================================================

    /**
     * Determines whether the provided Partial Ancestral Graph (PAG) is a legal PAG without providing detailed error
     * messages. Staged for speed:
     * <ol>
     * <li>Stage 0: endpoint syntax on the PAG itself (measured nodes; single edge per pair; all endpoints in
     * {tail, arrow, circle}).</li>
     * <li>Stage 1: ancestrality of the definite directed part (acyclic --&gt; subgraph; no arrowhead into a definite
     * ancestor, which covers directed and almost-directed cycles; no arrowhead at an endpoint of an undirected
     * edge).</li>
     * <li>Stage 2: closure under the sepset-free FCI orientation rules R1, R2, R3, R8. If any rule would change a
     * mark, the graph is under-oriented ("between a MAG and a PAG") and is rejected without building the MAG.</li>
     * <li>Stage 3 (arbiter): Zhang MAG construction, fast MAG legality, exact MAG-to-PAG round trip, equality.</li>
     * </ol>
     * Stages 0-2 are necessary conditions only; Stage 3 is the exact arbiter and its verdict is final. Note that both
     * {@code zhangMagFromPag} and {@code MagToPag} preserve adjacencies, so skeletons of {@code pag} and the
     * reconstituted PAG always agree and a plain equality test suffices in Stage 3.
     *
     * @param pag       The Partial Ancestral Graph (PAG) to be checked.
     * @param selection The set of nodes to be conditioned on.
     * @return true if the PAG is legal, false otherwise.
     */
    public static boolean isLegalPagQuiet(Graph pag, Set<Node> selection) {
        List<Node> nodes = pag.getNodes();

        // ---------- Stage 0: node types and endpoint syntax. O(V + E). ----------
        for (Node node : nodes) {
            if (node.getNodeType() != NodeType.MEASURED) return false;
        }

        for (Edge e : pag.getEdges()) {
            if (pag.getEdges(e.getNode1(), e.getNode2()).size() > 1) return false;

            Endpoint p1 = e.getEndpoint1();
            Endpoint p2 = e.getEndpoint2();

            boolean ok1 = p1 == Endpoint.TAIL || p1 == Endpoint.ARROW || p1 == Endpoint.CIRCLE;
            boolean ok2 = p2 == Endpoint.TAIL || p2 == Endpoint.ARROW || p2 == Endpoint.CIRCLE;
            if (!ok1 || !ok2) return false;

            // NOTE: tail-circle edges (x --o y) are deliberately NOT rejected here. Without selection they
            // cannot occur in a complete PAG, but under selection bias o-- edges are a legitimate PAG edge
            // type, and this method takes a selection set. The Stage 3 arbiter handles them exactly.
        }

        Map<Node, Integer> idx = indexMap(nodes);

        // ---------- Stage 1: ancestrality of the definite directed part. ----------
        // Ancestor closure over --> edges only. If the --> subgraph is cyclic, illegal.
        BitSet[] anc = definiteAncestorSets(pag, nodes, idx);
        if (anc == null) return false; // directed cycle among --> edges

        // No arrowhead into a definite ancestor: x *-> y with y in An(x) is a directed or almost-directed cycle
        // in every MAG in the class.
        for (Edge e : pag.getEdges()) {
            int i1 = idx.get(e.getNode1());
            int i2 = idx.get(e.getNode2());
            if (e.getEndpoint2() == Endpoint.ARROW && anc[i1].get(i2)) return false; // node2 in An(node1)
            if (e.getEndpoint1() == Endpoint.ARROW && anc[i2].get(i1)) return false; // node1 in An(node2)
        }

        // No arrowhead at an endpoint of an undirected edge: if x --- y, an invariant arrowhead at x would
        // give x a parent or spouse in every MAG, contradicting the MAG undirected-edge constraint.
        for (Edge e : pag.getEdges()) {
            if (!Edges.isUndirectedEdge(e)) continue;
            for (Node end : new Node[]{e.getNode1(), e.getNode2()}) {
                for (Node z : pag.getAdjacentNodes(end)) {
                    if (pag.getEndpoint(z, end) == Endpoint.ARROW) return false;
                }
            }
        }

        // ---------- Stage 2: closure under sepset-free orientation rules. ----------
        // A complete PAG is a fixed point of the FCI rules; if R1, R2, R3, or R8 would change a mark, reject.
        // (R4 needs the class/sepsets, R5-R7 need selection handling, R9-R10 need path searches; all of those
        // failures are caught by the Stage 3 arbiter.)
        if (someOrientationRuleFires(pag)) return false;

        // ---------- Stage 3: exact arbiter. ----------
        Graph mag;
        try {
            mag = GraphTransforms.zhangMagFromPag(pag);
        } catch (Exception e) {
            return false;
        }

        if (!isLegalMagQuiet(mag, selection)) return false;

        Graph pag2;
        try {
            MagToPag magToPag = new MagToPag(mag);
            pag2 = magToPag.convert(false, false);
        } catch (IllegalStateException e) {
            return false;
        }

        return pag.equals(pag2);
    }

    /**
     * Determines whether the given graph is a legal Maximal Ancestral Graph (MAG) without providing detailed error
     * messages. Single-pass structural check: one topological sort for acyclicity, one ancestor-bitset closure reused
     * by the bidirected-semantics check and the maximality check, and maximality via per-pair arrow-state reachability
     * (walk-to-path splicing makes walk reachability equivalent to inducing-path existence).
     *
     * @param mag       the graph to be checked
     * @param selection the set of nodes to be conditioned on
     * @return true if the graph is a legal MAG, false otherwise
     */
    public static boolean isLegalMagQuiet(Graph mag, Set<Node> selection) {
        List<Node> nodes = mag.getNodes();
        int n = nodes.size();

        // 1) No LATENT nodes. O(V).
        for (Node node : nodes) {
            if (node.getNodeType() == NodeType.LATENT) return false;
        }

        // 2) Edge sanity: at most one edge per pair; only directed, bidirected, undirected. O(E).
        for (Edge edge : mag.getEdges()) {
            if (mag.getEdges(edge.getNode1(), edge.getNode2()).size() > 1) return false;
            if (!(Edges.isDirectedEdge(edge) || Edges.isBidirectedEdge(edge) || Edges.isUndirectedEdge(edge))) {
                return false;
            }
        }

        Map<Node, Integer> idx = indexMap(nodes);

        // 3-4) Acyclicity via one topological sort of the directed part, then ancestor bitsets in topo order.
        //      O(V + E) for the sort, O(V*E/64) for the closure.
        BitSet[] anc = definiteAncestorSets(mag, nodes, idx);
        if (anc == null) return false; // directed cycle

        // 5) Bidirected semantics: no directed path in either direction between spouses. O(#bidirected) lookups.
        for (Edge e : mag.getEdges()) {
            if (!Edges.isBidirectedEdge(e)) continue;
            int x = idx.get(e.getNode1());
            int y = idx.get(e.getNode2());
            if (anc[x].get(y) || anc[y].get(x)) return false;
        }

        // 6) Undirected-edge constraint: endpoints have no parents/spouses, i.e. no arrowhead at the endpoint.
        for (Edge edge : mag.getEdges()) {
            if (!Edges.isUndirectedEdge(edge)) continue;
            for (Node end : new Node[]{edge.getNode1(), edge.getNode2()}) {
                for (Node z : mag.getAdjacentNodes(end)) {
                    if (mag.getEndpoint(z, end) == Endpoint.ARROW) return false;
                }
            }
        }

        // 7) Maximality: no inducing path between non-adjacent nodes given selection.
        //    Allowed interior set per pair is a bitset OR of precomputed ancestor sets; reachability per pair is a
        //    BFS over (node, arrived-with-arrowhead) states, O(E) per pair.

        // Ancestors of the selection set (including the selection nodes themselves).
        BitSet ancSel = new BitSet(n);
        if (selection != null) {
            for (Node s : selection) {
                Integer si = idx.get(s);
                if (si == null) continue;
                ancSel.or(anc[si]);
                ancSel.set(si);
            }
        }

        // Flat adjacency structure: for each node u, neighbors with (arrowAtU, arrowAtNeighbor) flags.
        int[][] nbr = new int[n][];
        boolean[][] arrowAtSelf = new boolean[n][];   // arrowhead at u on the edge u - nbr
        boolean[][] arrowAtOther = new boolean[n][];  // arrowhead at nbr on the edge u - nbr
        for (int u = 0; u < n; u++) {
            Node nu = nodes.get(u);
            List<Node> adj = mag.getAdjacentNodes(nu);
            int m = adj.size();
            nbr[u] = new int[m];
            arrowAtSelf[u] = new boolean[m];
            arrowAtOther[u] = new boolean[m];
            for (int k = 0; k < m; k++) {
                Node nv = adj.get(k);
                nbr[u][k] = idx.get(nv);
                arrowAtSelf[u][k] = mag.getEndpoint(nv, nu) == Endpoint.ARROW;
                arrowAtOther[u][k] = mag.getEndpoint(nu, nv) == Endpoint.ARROW;
            }
        }

        BitSet allowed = new BitSet(n);
        int[] queue = new int[2 * n];
        boolean[][] visited = new boolean[2][n]; // [arrivedWithArrowheadAtNode ? 1 : 0][node]

        for (int i = 0; i < n; i++) {
            if (Thread.currentThread().isInterrupted()) {
                throw new RuntimeException("Interrupted during MAG maximality check");
            }
            Node x = nodes.get(i);
            for (int j = i + 1; j < n; j++) {
                Node y = nodes.get(j);
                if (mag.isAdjacentTo(x, y)) continue;

                allowed.clear();
                allowed.or(anc[i]);
                allowed.or(anc[j]);
                allowed.or(ancSel);

                if (existsInducingWalk(i, j, allowed, nbr, arrowAtSelf, arrowAtOther, queue, visited)) {
                    return false;
                }
            }
        }

        return true;
    }

    // ============================================================
    // Private helpers
    // ============================================================

    private static Map<Node, Integer> indexMap(List<Node> nodes) {
        Map<Node, Integer> idx = new HashMap<>(nodes.size() * 2);
        for (int i = 0; i < nodes.size(); i++) idx.put(nodes.get(i), i);
        return idx;
    }

    /**
     * Ancestor bitsets over the fully directed (--&gt;) edges of {@code g}. anc[i] contains j iff there is a directed
     * path from node j to node i (proper ancestors only; a node is not in its own set). Returns null if the directed
     * subgraph is cyclic.
     */
    private static BitSet[] definiteAncestorSets(Graph g, List<Node> nodes, Map<Node, Integer> idx) {
        int n = nodes.size();

        List<List<Integer>> children = new ArrayList<>(n);
        for (int i = 0; i < n; i++) children.add(new ArrayList<>());
        int[] indeg = new int[n];

        for (Edge e : g.getEdges()) {
            if (!Edges.isDirectedEdge(e)) continue;
            int tail, head;
            if (e.getEndpoint2() == Endpoint.ARROW) {
                tail = idx.get(e.getNode1());
                head = idx.get(e.getNode2());
            } else {
                tail = idx.get(e.getNode2());
                head = idx.get(e.getNode1());
            }
            children.get(tail).add(head);
            indeg[head]++;
        }

        // Kahn's algorithm.
        int[] order = new int[n];
        int qh = 0, qt = 0;
        for (int i = 0; i < n; i++) if (indeg[i] == 0) order[qt++] = i;
        while (qh < qt) {
            int u = order[qh++];
            for (int c : children.get(u)) {
                if (--indeg[c] == 0) order[qt++] = c;
            }
        }
        if (qt < n) return null; // cycle in the directed part

        // Closure in topological order: when u is processed, anc[u] is complete, so push to children.
        BitSet[] anc = new BitSet[n];
        for (int i = 0; i < n; i++) anc[i] = new BitSet(n);
        for (int k = 0; k < n; k++) {
            int u = order[k];
            for (int c : children.get(u)) {
                anc[c].or(anc[u]);
                anc[c].set(u);
            }
        }
        return anc;
    }

    /**
     * BFS over (node, arrived-with-arrowhead) states testing whether an inducing walk exists from node {@code src} to
     * node {@code dst} whose interior vertices all lie in {@code allowed} and are colliders on the walk. By the
     * standard splicing argument (all edge-ends at a repeated interior vertex carry arrowheads, so cutting the loop
     * preserves the collider and allowed-set conditions), an inducing walk exists iff an inducing path exists.
     */
    private static boolean existsInducingWalk(int src, int dst, BitSet allowed,
                                              int[][] nbr, boolean[][] arrowAtSelf, boolean[][] arrowAtOther,
                                              int[] queue, boolean[][] visited) {
        int n = nbr.length;
        for (int b = 0; b < 2; b++) Arrays.fill(visited[b], false);

        int qh = 0, qt = 0;

        // First edge: no constraint at src's own end.
        for (int k = 0; k < nbr[src].length; k++) {
            int v = nbr[src][k];
            if (v == dst) return true;
            int a = arrowAtOther[src][k] ? 1 : 0;
            if (!visited[a][v]) {
                visited[a][v] = true;
                queue[qt++] = (v << 1) | a;
            }
        }

        while (qh < qt) {
            int state = queue[qh++];
            int w = state >> 1;
            boolean arrowIn = (state & 1) == 1;

            // To pass through w it must be a collider on the walk and in the allowed interior set.
            if (!arrowIn || !allowed.get(w)) continue;

            for (int k = 0; k < nbr[w].length; k++) {
                if (!arrowAtSelf[w][k]) continue; // outgoing edge must also have an arrowhead at w
                int u = nbr[w][k];
                if (u == dst) return true;
                if (u == src) continue;
                int a = arrowAtOther[w][k] ? 1 : 0;
                if (!visited[a][u]) {
                    // At most 2n distinct (node, arrowIn) states, and the caller allocates queue of length 2n,
                    // so the queue cannot overflow.
                    visited[a][u] = true;
                    queue[qt++] = (u << 1) | a;
                }
            }
        }

        return false;
    }

    /**
     * Detection-only check of the sepset-free FCI orientation rules R1, R2, R3, R8 (Zhang 2008). Returns true if any
     * rule would change a mark, in which case the graph is not a complete PAG. Endpoint convention:
     * {@code g.getEndpoint(a, b)} is the mark at {@code b} on the edge between {@code a} and {@code b}.
     */
    private static boolean someOrientationRuleFires(Graph g) {
        List<Node> nodes = g.getNodes();

        for (Node b : nodes) {
            List<Node> adjB = g.getAdjacentNodes(b);

            // R1: a *-> b o-* c, a and c not adjacent  =>  orient b -> c (fires iff circle at b on the b-c edge).
            // R3: a *-> b <-* c, a *-o d o-* c, a and c not adjacent, d *-o b  =>  orient d *-> b.
            for (int i = 0; i < adjB.size(); i++) {
                Node a = adjB.get(i);
                boolean arrowAtB_fromA = g.getEndpoint(a, b) == Endpoint.ARROW;

                for (int j = 0; j < adjB.size(); j++) {
                    if (i == j) continue;
                    Node c = adjB.get(j);
                    if (g.isAdjacentTo(a, c)) continue;

                    // R1
                    if (arrowAtB_fromA && g.getEndpoint(c, b) == Endpoint.CIRCLE) return true;

                    // R3 (a, c symmetric in the antecedent; iterate d over common neighbors of a and c adjacent to b)
                    if (i < j && arrowAtB_fromA && g.getEndpoint(c, b) == Endpoint.ARROW) {
                        for (Node d : adjB) {
                            if (d == a || d == c) continue;
                            if (g.getEndpoint(d, b) != Endpoint.CIRCLE) continue;
                            if (!g.isAdjacentTo(d, a) || !g.isAdjacentTo(d, c)) continue;
                            if (g.getEndpoint(a, d) == Endpoint.CIRCLE && g.getEndpoint(c, d) == Endpoint.CIRCLE) {
                                return true;
                            }
                        }
                    }
                }
            }
        }

        // R2: (a -> b *-> c or a *-> b -> c) and a *-o c  =>  orient a *-> c (fires iff circle at c on the a-c edge).
        // R8: (a -> b -> c or a --o b -> c) and a o-> c  =>  orient a -> c (fires iff circle at a on the a-c edge
        //     with an arrowhead at c).
        for (Edge e : g.getEdges()) {
            for (int dir = 0; dir < 2; dir++) {
                Node a = (dir == 0) ? e.getNode1() : e.getNode2();
                Node c = (dir == 0) ? e.getNode2() : e.getNode1();

                boolean circleAtC = g.getEndpoint(a, c) == Endpoint.CIRCLE;
                boolean r8Head = g.getEndpoint(a, c) == Endpoint.ARROW && g.getEndpoint(c, a) == Endpoint.CIRCLE;

                if (!circleAtC && !r8Head) continue;

                for (Node b : g.getAdjacentNodes(a)) {
                    if (b == c || !g.isAdjacentTo(b, c)) continue;

                    boolean aDirB = g.getEndpoint(a, b) == Endpoint.ARROW && g.getEndpoint(b, a) == Endpoint.TAIL; // a -> b
                    boolean bDirC = g.getEndpoint(b, c) == Endpoint.ARROW && g.getEndpoint(c, b) == Endpoint.TAIL; // b -> c
                    boolean aArrB = g.getEndpoint(a, b) == Endpoint.ARROW;                                          // a *-> b
                    boolean bArrC = g.getEndpoint(b, c) == Endpoint.ARROW;                                          // b *-> c
                    boolean aCoB = g.getEndpoint(b, a) == Endpoint.TAIL && g.getEndpoint(a, b) == Endpoint.CIRCLE;  // a --o b

                    // R2
                    if (circleAtC && ((aDirB && bArrC) || (aArrB && bDirC))) return true;

                    // R8
                    if (r8Head && bDirC && (aDirB || aCoB)) return true;
                }
            }
        }

        return false;
    }

    /**
     * Stores a result for checking whether a graph is a legal MAG--(a) whether it is (a boolean), and (b) the reason
     * why it is not, if it is not (a String).
     */
    public static class LegalMagRet {

        /**
         * Whether the graph is a legal MAG.
         */
        private final boolean legalMag;

        /**
         * The reason why the graph is not a legal MAG, if not.
         */
        private final String reason;

        /**
         * Constructs a new LegalMagRet object.
         *
         * @param legalMag Whether the graph is a legal MAG.
         * @param reason   The reason why the graph is not a legal MAG, if not.
         */
        public LegalMagRet(boolean legalMag, String reason) {
            if (reason == null) throw new NullPointerException("Reason must be given.");
            this.legalMag = legalMag;
            this.reason = reason;
        }

        /**
         * Returns whether the graph is a legal MAG.
         *
         * @return Whether the graph is a legal MAG.
         */
        public boolean isLegalMag() {
            return legalMag;
        }

        /**
         * Returns the reason why the graph is not a legal MAG, if not.
         *
         * @return The reason why the graph is not a legal MAG, if not.
         */
        public String getReason() {
            return reason;
        }
    }

    /**
     * Stores a result for checking whether a graph is a legal PAG--(a) whether it is (a boolean), and (b) the reason
     * why it is not, if it is not (a String).
     */
    public static class LegalPagRet {

        /**
         * Whether the graph is a legal PAG.
         */
        private final boolean legalPag;

        /**
         * The reason why the graph is not a legal PAG, if not.
         */
        private final String reason;

        /**
         * Constructs a new LegalPagRet object.
         *
         * @param legalPag Whether the graph is a legal PAG.
         * @param reason   The reason why the graph is not a legal PAG, if not.
         */
        public LegalPagRet(boolean legalPag, String reason) {
            if (reason == null) throw new NullPointerException("Reason must be given.");
            this.legalPag = legalPag;
            this.reason = reason;
        }

        /**
         * Returns whether the graph is a legal PAG.
         *
         * @return Whether the graph is a legal PAG.
         */
        public boolean isLegalPag() {
            return legalPag;
        }

        /**
         * Returns the reason why the graph is not a legal PAG, if not.
         *
         * @return The reason why the graph is not a legal PAG, if not.
         */
        public String getReason() {
            return reason;
        }
    }
}