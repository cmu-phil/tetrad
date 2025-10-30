// DROP-IN REPLACEMENT FOR YOUR CURRENT CLASS (keeps IdentityHashMap and API)

package edu.cmu.tetrad.util;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.utils.MagToPag;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * A thread-safe, identity-preserving cache that converts DAGs or MAGs to PAGs.
 *
 * <p><b>Design rationale.</b>
 * <ul>
 *   <li><b>Preserves identity.</b>  For a given input {@link Graph} object, {@code getPag(Graph)}
 *       always returns the <em>same</em> PAG instance.  This is required because higher-level
 *       algorithms (e.g., FCI, GFCI) retain {@link Node} and {@link Edge} references from the
 *       returned PAG and assume their object identity remains stable across calls.</li>
 *
 *   <li><b>Detects and repairs external mutation.</b>  Callers may unintentionally mutate the
 *       cached PAG (for example, by adjusting endpoints).  To guard against this, each cached
 *       entry stores a lightweight structural signature of the PAG.  On every cache hit,
 *       {@code PagCache} recomputes this signature; if it has changed, the PAG is rebuilt from
 *       the original DAG/MAG and the cached instance is <em>refreshed in place</em> so that
 *       existing {@link Node} and {@link Edge} identities are preserved but the structure is
 *       restored to a legal state.</li>
 *
 *   <li><b>Source change detection.</b>  The cache also fingerprints the source graph’s structure
 *       (node names, edges, and endpoints).  If that signature changes, the PAG is recomputed
 *       from scratch and the cache entry replaced.</li>
 *
 *   <li><b>No weak references.</b>  This implementation uses an {@link IdentityHashMap} rather
 *       than a {@link java.util.WeakHashMap}.  Weak keys caused unpredictable evictions in
 *       long-running batch jobs; identity-based keys avoid that while keeping the semantics
 *       simple and deterministic.</li>
 * </ul>
 *
 * <p>In summary, {@code PagCache} ensures that each unique source graph maps to a single,
 * internally consistent PAG object, automatically repaired if modified.  This behavior is
 * essential for FCI-style algorithms that rely on persistent object identity while still
 * guaranteeing deterministic, legal PAGs across repeated calls.</p>
 */
public final class PagCache {

    private static volatile PagCache instance;

    private final Map<Graph, Entry> cache =
            Collections.synchronizedMap(new IdentityHashMap<Graph, Entry>());

    private PagCache() {
    }

    /**
     * Provides access to the singleton instance of the PagCache class.
     * If the instance does not yet exist, it is created in a thread-safe manner.
     *
     * @return the singleton instance of PagCache
     */
    public static PagCache getInstance() {
        PagCache local = instance;
        if (local == null) {
            synchronized (PagCache.class) {
                local = instance;
                if (local == null) instance = local = new PagCache();
            }
        }
        return local;
    }

    private static Graph computePag(Graph graph) {
        if (graph.paths().isLegalMag()) {
            return new MagToPag(graph).convert(false);
        } else if (graph.paths().isLegalDag()) {
            Graph mag = GraphTransforms.dagToMag(graph);
            return new MagToPag(mag).convert(false);
        } else {
            Graph mag = GraphTransforms.zhangMagFromPag(graph);
            return new MagToPag(mag).convert(true);
        }
    }

    /**
     * Signature of the SOURCE graph (nodes + edges + endpoints, order-independent).
     */
    private static long signatureOfSource(Graph g) {
        long h = 0xcbf29ce484222325L; // FNV-1a
        List<String> names = new ArrayList<>();
        for (Node n : g.getNodes()) names.add(n.getName());
        Collections.sort(names);
        for (String s : names) h = fnv1a(h, s);

        List<String> es = new ArrayList<>();
        for (Edge e : g.getEdges()) {
            String a = e.getNode1().getName();
            String b = e.getNode2().getName();
            boolean swap = a.compareTo(b) > 0;
            Node x = swap ? e.getNode2() : e.getNode1();
            Node y = swap ? e.getNode1() : e.getNode2();
            Endpoint xy = g.getEndpoint(x, y);
            Endpoint yx = g.getEndpoint(y, x);
            es.add(x.getName() + "|" + y.getName() + "|" +
                   (xy == null ? "N" : xy.name().charAt(0)) + "|" +
                   (yx == null ? "N" : yx.name().charAt(0)));
        }
        Collections.sort(es);
        for (String s : es) h = fnv1a(h, s);
        return h;
    }

    /**
     * Lightweight signature of a PAG’s *current contents* (to detect external mutation).
     */
    private static long signatureOfPag(Graph pag) {
        long h = 0xcbf29ce484222325L;
        // Assume node names stable; we only need endpoints/edges to detect mutation.
        List<String> es = new ArrayList<>();
        for (Edge e : pag.getEdges()) {
            String a = e.getNode1().getName();
            String b = e.getNode2().getName();
            boolean swap = a.compareTo(b) > 0;
            Node x = swap ? e.getNode2() : e.getNode1();
            Node y = swap ? e.getNode1() : e.getNode2();
            Endpoint xy = pag.getEndpoint(x, y);
            Endpoint yx = pag.getEndpoint(y, x);
            es.add(x.getName() + "|" + y.getName() + "|" +
                   (xy == null ? "N" : xy.name().charAt(0)) + "|" +
                   (yx == null ? "N" : yx.name().charAt(0)));
        }
        Collections.sort(es);
        for (String s : es) h = fnv1a(h, s);
        return h;
    }

    // ----------------- internals -----------------

    /**
     * Refresh target graph IN PLACE so its nodes keep identity.
     */
    private static void syncInPlace(Graph target, Graph source) {
        // Ensure both have the same node set by name
        // (add any missing nodes, though in practice names should match)
        for (Node src : source.getNodes()) {
            if (target.getNode(src.getName()) == null) {
                target.addNode(new GraphNode(src.getName()));
            }
        }
        // Remove edges not in source
        Set<String> want = new HashSet<>();
        for (Edge e : source.getEdges()) {
            String a = e.getNode1().getName(), b = e.getNode2().getName();
            boolean swap = a.compareTo(b) > 0;
            String x = swap ? b : a, y = swap ? a : b;
            want.add(x + "|" + y);
        }
        List<Edge> toRemove = new ArrayList<>();
        for (Edge e : target.getEdges()) {
            String a = e.getNode1().getName(), b = e.getNode2().getName();
            boolean swap = a.compareTo(b) > 0;
            String x = swap ? b : a, y = swap ? a : b;
            if (!want.contains(x + "|" + y)) toRemove.add(e);
        }
        for (Edge e : toRemove) target.removeEdge(e);

        // Ensure all edges exist
        for (Edge e : source.getEdges()) {
            Node aT = target.getNode(e.getNode1().getName());
            Node bT = target.getNode(e.getNode2().getName());
            if (target.getEdge(aT, bT) == null) {
                target.addEdge(Edges.undirectedEdge(aT, bT));
            }
        }
        // Copy endpoints
        for (Edge e : source.getEdges()) {
            Node aS = e.getNode1(), bS = e.getNode2();
            Node aT = target.getNode(aS.getName()), bT = target.getNode(bS.getName());
            target.setEndpoint(aT, bT, source.getEndpoint(aS, bS));
            target.setEndpoint(bT, aT, source.getEndpoint(bS, aS));
        }
    }

    private static long fnv1a(long h, String s) {
        for (int i = 0; i < s.length(); i++) {
            h ^= (s.charAt(i) & 0xff);
            h *= 0x100000001b3L;
        }
        return h;
    }

    /**
     * Clears the internal cache, removing all stored entries.
     * This method should be called when the cache needs to be reset or discarded.
     */
    public void clear() {
        cache.clear();
    }

    /**
     * Retrieves or computes a PAG (Partial Ancestral Graph) from the given input graph.
     * If the graph is already in the cache and hasn't been externally modified,
     * the cached version is returned. Otherwise, a new PAG is computed, stored in the cache, and returned.
     * The input graph must be either a DAG (Directed Acyclic Graph) or a MAG (Maximal Ancestral Graph).
     *
     * @param g the input graph, which must be either a DAG or a MAG
     * @return the corresponding PAG for the provided graph
     * @throws IllegalArgumentException if the input graph is neither a DAG nor a MAG
     */
    public @NotNull Graph getPag(Graph g) {
        if (!(g.paths().isLegalDag() || g.paths().isLegalMag())) {
            throw new IllegalArgumentException("Graph must be a DAG or a MAG.");
        }
        final long srcSig = signatureOfSource(g);

        synchronized (cache) {
            Entry e = cache.get(g);
            if (e != null && e.srcSig == srcSig) {
                // Guard against external mutation of the cached PAG
                long currentPagSig = signatureOfPag(e.pag);
                if (currentPagSig != e.pagSig) {
                    Graph rebuilt = computePag(g);
                    syncInPlace(e.pag, rebuilt);               // preserve identity
                    e.pagSig = signatureOfPag(e.pag);          // update sig after sync
                }
                return e.pag;
            }
        }

        // Miss or source changed: build fresh
        final Graph pag = computePag(g);
        synchronized (cache) {
            cache.put(g, new Entry(pag, srcSig, signatureOfPag(pag)));
            return pag;
        }
    }

    /**
     * Retrieves or computes a PAG (Partial Ancestral Graph) from the given input graph.
     * This method operates based on the input graph, knowledge, and verbosity setting.
     * If the graph is already in the cache and hasn't been externally modified, the cached version is returned.
     * Otherwise, a new PAG is computed and returned.
     *
     * @param g the input graph, which must be either a DAG (Directed Acyclic Graph) or a MAG (Maximal Ancestral Graph)
     * @param knowledge additional knowledge, which may influence the computation of the PAG
     * @param verbose a boolean flag indicating whether verbose output should be enabled during the process
     * @return the corresponding PAG for the provided graph and knowledge
     * @throws IllegalArgumentException if the input graph is neither a DAG nor a MAG
     */
    public @NotNull Graph getPag(Graph g, Knowledge knowledge, boolean verbose) {
        return getPag(g);
    }

    private static final class Entry {
        final Graph pag;      // the object we always return (identity preserved)
        long srcSig;          // signature of SOURCE graph when built
        long pagSig;          // signature of the cached PAG contents

        Entry(Graph pag, long srcSig, long pagSig) {
            this.pag = pag;
            this.srcSig = srcSig;
            this.pagSig = pagSig;
        }
    }
}