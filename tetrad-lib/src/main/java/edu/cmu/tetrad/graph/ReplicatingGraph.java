package edu.cmu.tetrad.graph;

import java.util.*;

/**
 * EdgeListGraph subclass that applies an {@link EdgeReplicationPolicy}
 * whenever edges are added/removed or endpoints are oriented.
 *
 * Only the mutating operations are overridden; everything else uses
 * the standard {@link EdgeListGraph} behavior.
 */
public class ReplicatingGraph extends EdgeListGraph {

    private final EdgeReplicationPolicy policy;

    /** Prevent recursive mirroring from re-entering the mutators. */
    private static final ThreadLocal<Boolean> IN_REPLICATION =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    /* -------------------- Constructors -------------------- */

    /** Creates an empty repeating graph using the given policy. */
    public ReplicatingGraph(EdgeReplicationPolicy policy) {
        super();
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    /** Copies nodes+edges from g and installs the policy. */
    public ReplicatingGraph(Graph g, EdgeReplicationPolicy policy) {
        super();
        this.policy = Objects.requireNonNull(policy, "policy");
        transferNodesAndEdges(g);
        transferAttributes(g);
        this.setUnderLineTriples(g.getUnderLines());
        this.setDottedUnderLineTriples(g.getDottedUnderlines());
        this.setAmbiguousTriples(g.getAmbiguousTriples());
    }

    /** Creates a graph over 'nodes' and installs the policy. */
    public ReplicatingGraph(List<Node> nodes, EdgeReplicationPolicy policy) {
        super(nodes);
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    /** Convenience: SVAR-style lag mirroring. */
    public static ReplicatingGraph svar(Graph g) {
        return new ReplicatingGraph(g, new LagReplicationPolicy());
    }
    public static ReplicatingGraph svar(List<Node> nodes) {
        return new ReplicatingGraph(nodes, new LagReplicationPolicy());
    }
    public static ReplicatingGraph svar() {
        return new ReplicatingGraph(new LagReplicationPolicy());
    }

    /* -------------------- Mutators (mirrored) -------------------- */

    @Override
    public synchronized boolean addEdge(Edge e) throws IllegalArgumentException {
        if (Boolean.TRUE.equals(IN_REPLICATION.get())) {
            return super.addEdge(e);
        }
        // Add requested edge first
        boolean changed = super.addEdge(e);

        // Then mirror across policy
        try {
            IN_REPLICATION.set(Boolean.TRUE);
            for (Edge m : policy.mirrorsFor(this, e)) {
                if (!m.equals(e)) {
                    super.addEdge(m);
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
            return super.removeEdge(e);
        }
        // Remove requested edge first
        boolean changed = super.removeEdge(e);

        // Then remove mirrored counterparts
        try {
            IN_REPLICATION.set(Boolean.TRUE);
            for (Edge m : policy.mirrorsFor(this, e)) {
                if (!m.equals(e)) {
                    super.removeEdge(m);
                }
            }
        } finally {
            IN_REPLICATION.set(Boolean.FALSE);
        }
        return changed;
    }

    @Override
    public synchronized boolean removeEdge(Node a, Node b) {
        Edge e = getEdge(a, b);
        if (e == null) return false;
        return removeEdge(e);
    }

    /**
     * Mirrors endpoint changes across all policy-mirrored edges. This keeps
     * algorithms that orient by setEndpoint() fully SVAR-aware without needing
     * a custom endpoint strategy.
     */
    @Override
    public synchronized boolean setEndpoint(Node from, Node to, Endpoint ep)
            throws IllegalArgumentException {
        if (Boolean.TRUE.equals(IN_REPLICATION.get())) {
            return super.setEndpoint(from, to, ep);
        }

        // First orient the requested endpoint.
        boolean ok = super.setEndpoint(from, to, ep);

        // Mirror endpoint to all existing mirrored edges (do not create new ones here).
        try {
            IN_REPLICATION.set(Boolean.TRUE);

            Edge seed = getEdge(from, to);
            if (seed == null) return ok; // nothing to mirror

            for (Edge m : policy.mirrorsFor(this, seed)) {
                if (m.equals(seed)) continue;

                Node m1 = m.getNode1();
                Node m2 = m.getNode2();
                Edge existing = getEdge(m1, m2);
                if (existing != null) {
                    // We only set the endpoint at 'm2' (analogous to 'to') to 'ep',
                    // retaining the proximal endpoint at 'm1' as-is.
                    super.setEndpoint(m1, m2, ep);
                }
            }
        } finally {
            IN_REPLICATION.set(Boolean.FALSE);
        }
        return ok;
    }

    /**
     * Keep the bulk reorientation consistent by delegating to setEndpoint,
     * guarded so we don’t explode work quadratically.
     */
    @Override
    public void reorientAllWith(Endpoint ep) {
        if (Boolean.TRUE.equals(IN_REPLICATION.get())) {
            super.reorientAllWith(ep);
            return;
        }
        try {
            IN_REPLICATION.set(Boolean.TRUE);
            super.reorientAllWith(ep);
        } finally {
            IN_REPLICATION.set(Boolean.FALSE);
        }
    }

    public EdgeReplicationPolicy getReplicationPolicy() {
        return policy;
    }
}