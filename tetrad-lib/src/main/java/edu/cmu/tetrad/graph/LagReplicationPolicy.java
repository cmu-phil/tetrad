package edu.cmu.tetrad.graph;

import edu.cmu.tetrad.util.TetradSerializable;

import java.io.Serial;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SVAR-style lag mirroring policy.
 * <p>
 * Node naming: base at lag 0 is "X"; lagged versions are "X:1", "X:2", ... When edge A@la —?→ B@lb mutates, mirror for
 * all t where A@t and B@(t + (lb - la)) both exist.
 */
public final class LagReplicationPolicy implements EdgeReplicationPolicy, TetradSerializable {

    @Serial
    private static final long serialVersionUID = 23L;

    private static final Pattern LAG = Pattern.compile("^(.*?)(?::(\\d+))?$");

    /**
     * Represents the minimum allowable lag value for the lag-based replication policy in the
     * {@code LagReplicationPolicy} class.
     * <p>
     * This field defines the lowest lag threshold that will be utilized during the evaluation and processing of edges
     * in graph data. It ensures that replication policies are applied with a lag no less than this specified value.
     * <p>
     * The value of {@code minLag} is immutable and must be provided at the time of construction of the
     * {@code LagReplicationPolicy} instance.
     */
    private final Integer minLag;
    /**
     * The maximum allowable lag in the replication policy. This variable defines the upper boundary for lag values
     * permitted by the LagReplicationPolicy. It is immutable and is set during the initialization of the object.
     */
    private final Integer maxLag;
    /**
     * Optional: stride for seasonal mirroring (e.g., step=12 for monthly-to-yearly).
     */
    private final int step;

    /**
     * Default constructor for the LagReplicationPolicy class. Initializes the policy with default values for minimum
     * lag, maximum lag, and step size. The default values are: - minLag: null - maxLag: null - step: 1
     */
    public LagReplicationPolicy() {
        this(null, null, 1);
    }

    /**
     * Constructs a LagReplicationPolicy object with specified minimum lag, maximum lag, and lag step size.
     *
     * @param minLag the minimum lag value. Can be null to specify no lower bound.
     * @param maxLag the maximum lag value. Can be null to specify no upper bound.
     * @param step the step size to increment lag values. Must be greater than or equal to 1.
     * @throws IllegalArgumentException if the step is less than 1.
     */
    public LagReplicationPolicy(Integer minLag, Integer maxLag, int step) {
        if (step < 1) throw new IllegalArgumentException("step must be >= 1");
        this.minLag = minLag;
        this.maxLag = maxLag;
        this.step = step;
    }

    /**
     * Parses the given string to create a {@code Lag} object. The string must match a specific pattern
     * defined by the {@code LAG} regular expression. If the input string does not match the pattern,
     * the method returns {@code null}.
     *
     * @param name the input string to parse, representing the base name and lag value
     * @return a {@code Lag} object constructed from the parsed base name and lag value, or {@code null}
     *         if the input string does not match the expected pattern
     */
    private static Lag parse(String name) {
        Matcher m = LAG.matcher(name);
        if (!m.matches()) return null;
        String base = m.group(1);
        String lagStr = m.group(2);
        int lag = (lagStr == null || lagStr.isEmpty()) ? 0 : Integer.parseInt(lagStr);
        return new Lag(base, lag);
    }

    /* -------------------- helpers -------------------- */

    private static Map<String, Map<Integer, Node>> indexByBaseAndLag(List<Node> nodes) {
        Map<String, Map<Integer, Node>> map = new HashMap<>();
        for (Node n : nodes) {
            Lag ln = parse(n.getName());
            if (ln == null) continue;
            map.computeIfAbsent(ln.base, k -> new HashMap<>()).put(ln.lag, n);
        }
        return map;
    }

    /**
     * Generates a set of mirrored edges for a given edge in the graph, based on the lagging conventions
     * of the node names. The method computes potential edges by taking into account the specified lag shifts
     * between connected nodes and a defined lagging policy (minimum lag, maximum lag, and step size). If
     * lagging conventions are not met, the original edge is returned as the only element in the result.
     *
     * @param g the graph containing the nodes and edges
     * @param e the edge for which mirrored edges are to be computed
     * @return a set of edges that represent the mirrors of the provided edge based on lag conventions
     */
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

    /**
     * Represents a lagging configuration with a base name and an associated lag value.
     * The {@code Lag} record is an immutable data structure used to model the lag state
     * in replication policies or related computations.
     *
     * The {@code base} refers to a string identifier or name, and the {@code lag} is an
     * integer that quantifies the lag value associated with the base.
     */
    private record Lag(String base, int lag) {
    }
}