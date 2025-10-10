package edu.cmu.tetrad.util;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.utils.MagToPag;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Minimal cache: given a DAG/MAG, compute its PAG once per (graph identity, structure).
 * - No normalization, no auditing, no copies.
 * - If the *same* Graph object is later mutated, a structural signature detects it and forces recompute.
 */
public final class PagCache {

    private static volatile PagCache instance;

    /** Cache by identity; entries auto-GC when keys are not referenced elsewhere. */
    private final Map<Graph, Entry> cache = Collections.synchronizedMap(new IdentityHashMap<Graph, Entry>());

    private PagCache() {}

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

    /** Clear cache (handy in tests/benchmarks). */
    public void clear() { cache.clear(); }

    /** Main entry: accept DAG or MAG and return PAG. (Returns the cached instance; do not mutate it.) */
    public @NotNull Graph getPag(Graph g) {
        if (!(g.paths().isLegalDag() || g.paths().isLegalMag())) {
            throw new IllegalArgumentException("Graph must be a DAG or a MAG.");
        }
        final long sig = signature(g);

        synchronized (cache) {
            Entry e = cache.get(g);
            if (e != null && e.sig == sig) {
                return e.pag; // return cached instance as-is (FCI expects identical behavior)
            }
        }

        // Compute outside synchronized block to keep lock short
        final Graph pag = computePag(g);

        synchronized (cache) {
            cache.put(g, new Entry(pag, sig));
        }
        return pag;
    }

    /** Overload kept for API compatibility; intentionally ignores knowledge/verbose (original did not apply it here). */
    public @NotNull Graph getPag(Graph g, Knowledge knowledge, boolean verbose) {
        return getPag(g);
    }

    // ----------------- internals -----------------

    private static final class Entry {
        final Graph pag;
        final long sig;
        Entry(Graph pag, long sig) { this.pag = pag; this.sig = sig; }
    }

    /** Compute PAG using the same semantics you already had (no extra massaging). */
    private static Graph computePag(Graph graph) {
        if (graph.paths().isLegalMag()) {
            return new MagToPag(graph).convert(false);
        } else if (graph.paths().isLegalDag()) {
            Graph mag = GraphTransforms.dagToMag(graph);
            return new MagToPag(mag).convert(false);
        } else {
            // Fallback: PAG -> MAG (Zhang) -> PAG (rare path, kept to match original)
            Graph mag = GraphTransforms.zhangMagFromPag(graph);
            return new MagToPag(mag).convert(true);
        }
    }

    /**
     * Fast, stable structural signature of the input graph.
     * Recompute the PAG if this changes (i.e., the same Graph object was mutated).
     */
    private static long signature(Graph g) {
        // FNV-1a 64-bit
        long h = 0xcbf29ce484222325L;

        // include node names (order-independent)
        List<String> names = new ArrayList<>();
        for (Node n : g.getNodes()) names.add(n.getName());
        Collections.sort(names);
        for (String s : names) h = fnv1a(h, s);

        // include edges with endpoints (order-independent)
        List<String> es = new ArrayList<>();
        for (Edge e : g.getEdges()) {
            Node a = e.getNode1(), b = e.getNode2();
            String na = a.getName(), nb = b.getName();
            // canonicalize node order
            boolean swap = na.compareTo(nb) > 0;
            Node x = swap ? b : a, y = swap ? a : b;
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

    private static long fnv1a(long h, String s) {
        for (int i = 0; i < s.length(); i++) {
            h ^= (s.charAt(i) & 0xff);
            h *= 0x100000001b3L;
        }
        return h;
    }
}