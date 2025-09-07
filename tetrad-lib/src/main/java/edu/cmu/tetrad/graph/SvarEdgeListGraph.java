package edu.cmu.tetrad.graph;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A Graph implementation (via EdgeListGraph) that enforces SVAR-style lag mirroring:
 * whenever an edge between A@lag_a and B@lag_b is added (or removed), the same edge
 * type is added (or removed) for all lag-aligned pairs A@t and B@(t + (lag_b - lag_a))
 * that exist in the node set.
 *
 * Node naming convention:
 *   - Base (lag 0) is "X"
 *   - Lagged versions are "X:1", "X:2", ...
 *
 * Examples:
 *   add X:1 -> Y  ⇒ also add X:2 -> Y:1, X:3 -> Y:2, ... if nodes exist
 *   remove Z:2 --- W:1  ⇒ also remove Z:1 --- W, Z:3 --- W:2, ...
 *
 * Only addEdge / removeEdge are overridden; everything else is inherited from EdgeListGraph.
 */
public class SvarEdgeListGraph extends EdgeListGraph {

    /** Prevent recursive mirroring during internal add/remove operations. */
    private static final ThreadLocal<Boolean> IN_REPLICATION =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    public SvarEdgeListGraph() {
        super();
    }

    /** Copy constructor: builds an EdgeListGraph copy first, then augments with SVAR behavior. */
    public SvarEdgeListGraph(Graph g) {
        super(g);
    }

    /** Construct from a collection of nodes. */
    public SvarEdgeListGraph(List<Node> nodes) {
        super(nodes);
    }

    @Override
    public synchronized boolean addEdge(Edge e) throws IllegalArgumentException {
        if (Boolean.TRUE.equals(IN_REPLICATION.get())) {
            // During replication, just do the raw add.
            return super.addEdge(e);
        }

        // First add the requested edge.
        boolean changed = super.addEdge(e);

        // Then mirror across all lags that exist.
        try {
            IN_REPLICATION.set(Boolean.TRUE);
            for (Edge mirror : buildMirroredEdges(e)) {
                if (!mirror.equals(e)) {
                    super.addEdge(mirror);
                }
            }
        } finally {
            IN_REPLICATION.set(Boolean.FALSE);
        }

        return changed;
    }

    @Override
    public synchronized boolean removeEdge(Edge e) {
        if (Boolean.TRUE.equals(IN_REPLICATION.get())) {
            // During replication, just do the raw remove.
            return super.removeEdge(e);
        }

        // Remove the requested edge first.
        boolean changed = super.removeEdge(e);

        // Then remove all mirrored counterparts.
        try {
            IN_REPLICATION.set(Boolean.TRUE);
            for (Edge mirror : buildMirroredEdges(e)) {
                if (!mirror.equals(e)) {
                    super.removeEdge(mirror);
                }
            }
        } finally {
            IN_REPLICATION.set(Boolean.FALSE);
        }

        return changed;
    }

    /* ======================= Helpers ======================= */

    private static final Pattern LAG_PATTERN = Pattern.compile("^(.*?)(?::(\\d+))?$");

    private static Lagged parseLagged(String name) {
        Matcher m = LAG_PATTERN.matcher(name);
        if (!m.matches()) return null;
        String base = m.group(1);
        String lagStr = m.group(2);
        int lag = (lagStr == null || lagStr.isEmpty()) ? 0 : Integer.parseInt(lagStr);
        return new Lagged(base, lag);
    }

    private Map<String, Map<Integer, Node>> indexByBaseAndLag() {
        Map<String, Map<Integer, Node>> map = new HashMap<>();
        for (Node n : getNodes()) {
            Lagged ln = parseLagged(n.getName());
            if (ln == null) continue; // ignore non-conforming names
            map.computeIfAbsent(ln.base, k -> new HashMap<>()).put(ln.lag, n);
        }
        return map;
    }

    /**
     * Build the full set of mirrored edges corresponding to {@code e}, including {@code e} itself
     * if lag parsing succeeds. If parsing fails, returns a singleton set {e}.
     */
    private Set<Edge> buildMirroredEdges(Edge e) {
        Node a = e.getNode1();
        Node b = e.getNode2();

        Lagged la = parseLagged(a.getName());
        Lagged lb = parseLagged(b.getName());

        if (la == null || lb == null) {
            // If names don't follow the lag convention, do nothing special.
            return Collections.singleton(e);
        }

        int shift = lb.lag - la.lag;

        Map<String, Map<Integer, Node>> byBase = indexByBaseAndLag();
        Map<Integer, Node> aLags = byBase.get(la.base);
        Map<Integer, Node> bLags = byBase.get(lb.base);
        if (aLags == null || bLags == null) {
            return Collections.singleton(e);
        }

        Set<Edge> out = new LinkedHashSet<>();

        // For every available lag t of A, connect to B at t + shift if it exists.
        for (Map.Entry<Integer, Node> ent : aLags.entrySet()) {
            int t = ent.getKey();
            Node aT = ent.getValue();
            Node bT = bLags.get(t + shift);
            if (bT == null) continue;

            // Preserve endpoint roles relative to node1/node2.
            Edge mirror = new Edge(aT, bT, e.getEndpoint1(), e.getEndpoint2());
            out.add(mirror);
        }

        // Fallback: ensure the original edge is present if we found nothing else.
        if (out.isEmpty()) {
            out.add(e);
        }

        return out;
    }

    /** Simple record to hold parsed base name and lag. */
    private record Lagged(String base, int lag) {}
}