package edu.cmu.tetrad.graph;

import edu.cmu.tetrad.util.TetradSerializable;

import java.io.Serial;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SVAR-style lag mirroring policy.
 *
 * Node naming: base at lag 0 is "X"; lagged versions are "X:1", "X:2", ...
 * When edge A@la —?→ B@lb mutates, mirror for all t where A@t and B@(t + (lb - la)) both exist.
 */
public final class LagReplicationPolicy implements EdgeReplicationPolicy, TetradSerializable {

    @Serial
    private static final long serialVersionUID = 23L;

    private static final Pattern LAG = Pattern.compile("^(.*?)(?::(\\d+))?$");

    /** Optional: constrain allowed mirrored lags (inclusive); null = no bound. */
    private final Integer minLag;
    private final Integer maxLag;

    /** Optional: stride for seasonal mirroring (e.g., step=12 for monthly-to-yearly). */
    private final int step;

    public LagReplicationPolicy() {
        this(null, null, 1);
    }

    public LagReplicationPolicy(Integer minLag, Integer maxLag, int step) {
        if (step < 1) throw new IllegalArgumentException("step must be >= 1");
        this.minLag = minLag;
        this.maxLag = maxLag;
        this.step = step;
    }

    @Override
    public Set<Edge> mirrorsFor(EdgeListGraph g, Edge e) {
        Node a = e.getNode1();
        Node b = e.getNode2();

        Lag la = parse(a.getName());
        Lag lb = parse(b.getName());

        if (la == null || lb == null) {
            // If names don't follow lag convention, no special mirroring.
            return Collections.singleton(e);
        }

        int shift = lb.lag - la.lag;

        Map<String, Map<Integer, Node>> byBase = indexByBaseAndLag(g.getNodes());
        Map<Integer, Node> aLags = byBase.get(la.base);
        Map<Integer, Node> bLags = byBase.get(lb.base);
        if (aLags == null || bLags == null) {
            return Collections.singleton(e);
        }

        Set<Edge> out = new LinkedHashSet<>();

        // For every available lag t of A (respecting optional bounds/step),
        // connect to B at t + shift if it exists.
        for (Map.Entry<Integer, Node> ent : aLags.entrySet()) {
            int t = ent.getKey();
            if ((minLag != null && t < minLag) || (maxLag != null && t > maxLag)) continue;
            if ((t % step) != 0 && t != 0) continue; // simple seasonal gating

            Node aT = ent.getValue();
            Node bT = bLags.get(t + shift);
            if (bT == null) continue;

            out.add(new Edge(aT, bT, e.getEndpoint1(), e.getEndpoint2()));
        }

        if (out.isEmpty()) out.add(e);
        return out;
    }

    /* -------------------- helpers -------------------- */

    private static Lag parse(String name) {
        Matcher m = LAG.matcher(name);
        if (!m.matches()) return null;
        String base = m.group(1);
        String lagStr = m.group(2);
        int lag = (lagStr == null || lagStr.isEmpty()) ? 0 : Integer.parseInt(lagStr);
        return new Lag(base, lag);
    }

    private static Map<String, Map<Integer, Node>> indexByBaseAndLag(List<Node> nodes) {
        Map<String, Map<Integer, Node>> map = new HashMap<>();
        for (Node n : nodes) {
            Lag ln = parse(n.getName());
            if (ln == null) continue;
            map.computeIfAbsent(ln.base, k -> new HashMap<>()).put(ln.lag, n);
        }
        return map;
    }

    private record Lag(String base, int lag) {}
}