package edu.cmu.tetrad.graph;

import java.util.List;
import java.util.Objects;

/**
 * EdgeListGraph subclass that applies an {@link EdgeReplicationPolicy} whenever edges are added/removed or endpoints
 * are oriented.
 * <p>
 * Only the mutating operations are overridden; everything else uses the standard {@link EdgeListGraph} behavior.
 */
public class ReplicatingGraph extends EdgeListGraph {

    /**
     * Prevent recursive mirroring from re-entering the mutators.
     */
    private static final ThreadLocal<Boolean> IN_REPLICATION =
            ThreadLocal.withInitial(() -> Boolean.FALSE);
    /**
     * The edge replication policy used for determining which edges should be mirrored and kept in sync in the graph.
     * This policy defines the behavior for ensuring consistency across related edges, enabling algorithms to handle
     * replication and updates in a uniform and predictable manner. The specified policy must adhere to rules of the
     * {@code EdgeReplicationPolicy} interface and is applied during operations that modify edges in the graph.
     */
    private final EdgeReplicationPolicy policy;

    /* -------------------- Constructors -------------------- */

    /**
     * Creates an empty repeating graph using the given policy.
     *
     * @param policy The policy.
     */
    public ReplicatingGraph(EdgeReplicationPolicy policy) {
        super();
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    /**
     * Copies nodes+edges from g and installs the policy.
     *
     * @param g the graph to copy.
     */
    public ReplicatingGraph(ReplicatingGraph g) {
        super();
        this.policy = Objects.requireNonNull(g.policy, "policy");
        try {
            IN_REPLICATION.set(Boolean.TRUE);         // avoid re-mirroring during copy
            super.transferNodesAndEdges(g);
            super.transferAttributes(g);
            super.setUnderLineTriples(g.getUnderLines());
            super.setDottedUnderLineTriples(g.getDottedUnderlines());
            super.setAmbiguousTriples(g.getAmbiguousTriples());
        } finally {
            IN_REPLICATION.set(Boolean.FALSE);
        }
    }

    /**
     * Creates a new {@code ReplicatingGraph} by copying the structure and attributes from the provided graph and
     * applying the specified edge replication policy.
     *
     * @param g      the original graph to be replicated; must not be null
     * @param policy the edge replication policy to apply to the new graph; must not be null
     */
    public ReplicatingGraph(Graph g, EdgeReplicationPolicy policy) {
        super();
        this.policy = Objects.requireNonNull(policy, "policy");
        try {
            IN_REPLICATION.set(Boolean.TRUE);         // raw copy, no mirroring
            super.transferNodesAndEdges(g);
            super.transferAttributes(g);
            if (g instanceof EdgeListGraph elg) {
                super.setUnderLineTriples(elg.getUnderLines());
                super.setDottedUnderLineTriples(elg.getDottedUnderlines());
                super.setAmbiguousTriples(elg.getAmbiguousTriples());
            }
        } finally {
            IN_REPLICATION.set(Boolean.FALSE);
        }
    }

    /**
     * Constructs a new ReplicatingGraph using the provided list of nodes and edge replication policy.
     *
     * @param nodes  the list of nodes to initialize the graph with
     * @param policy the edge replication policy to be applied in the graph; must not be null
     */
    public ReplicatingGraph(List<Node> nodes, EdgeReplicationPolicy policy) {
        super(nodes);
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    /**
     * Constructs a ReplicatingGraph using the given list of nodes and applies a default LagReplicationPolicy for edge
     * replication.
     *
     * @param nodes the list of nodes to initialize the graph with; must not be null
     * @return a new instance of ReplicatingGraph with the provided nodes and a default edge replication policy
     */
    public static ReplicatingGraph svar(List<Node> nodes) {
        return new ReplicatingGraph(nodes, new LagReplicationPolicy());
    }

    /**
     * Creates a new instance of {@code ReplicatingGraph} using a default {@code LagReplicationPolicy}.
     *
     * @return a new {@code ReplicatingGraph} instance with the default {@code LagReplicationPolicy}.
     */
    public static ReplicatingGraph svar() {
        return new ReplicatingGraph(new LagReplicationPolicy());
    }

    /* -------------------- Mutators (mirrored) -------------------- */

    /**
     * Adds an edge to the graph and performs additional processing to mirror edges based on the defined edge
     * replication policy.
     *
     * @param e the edge to be added to the graph; must not be null
     * @return true if the graph was modified as a result of this operation, false otherwise
     * @throws IllegalArgumentException if the provided edge is invalid or does not meet the requirements
     */
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

    /**
     * Removes an edge from the graph, and if applicable, removes its mirrored counterparts according to the edge
     * replication policy.
     *
     * @param e the edge to be removed; must not be null
     * @return true if the graph was modified as a result of this operation, false otherwise
     */
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

    /**
     * Removes an edge between the specified nodes, if such an edge exists in the graph. This operation is synchronized
     * to ensure thread safety.
     *
     * @param a the first node of the edge
     * @param b the second node of the edge
     * @return true if the edge was successfully removed and the graph was modified, false otherwise
     */
    @Override
    public synchronized boolean removeEdge(Node a, Node b) {
        Edge e = getEdge(a, b);
        if (e == null) return false;
        return removeEdge(e);
    }

    /**
     * Mirrors endpoint changes across all policy-mirrored edges. This keeps algorithms that orient by setEndpoint()
     * fully SVAR-aware without needing a custom endpoint strategy.
     *
     * @param from the node from which the edge originates
     * @param to   the node to which the edge points
     * @return true if the endpoint was successfully set and the graph was modified, false otherwise
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
     * Reorients all applicable edges in the graph to align with the specified endpoint. This method leverages the
     * superclass implementation to perform the reorientation and ensures that any custom mirroring behavior required by
     * this class is preserved.
     *
     * @param ep the endpoint configuration to apply to all applicable edges; must not be null
     */
    @Override
    public void reorientAllWith(Endpoint ep) {
        // Let super remove/add; our addEdge will mirror as needed.
        super.reorientAllWith(ep);
    }

    /**
     * Retrieves the current edge replication policy for the graph.
     *
     * @return the {@code EdgeReplicationPolicy} used by the graph for determining how edges should be mirrored and
     * synchronized.
     */
    public EdgeReplicationPolicy getReplicationPolicy() {
        return policy;
    }
}