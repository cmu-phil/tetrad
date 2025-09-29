package edu.cmu.tetrad.graph;

import java.util.List;

/**
 * Backward-compatible SVAR-mirroring graph.
 *
 * This class is a thin shim over {@link ReplicatingGraph} with a
 * {@link LagReplicationPolicy}. Use it anywhere the old
 * SvarEdgeListGraph was used.
 */
public final class SvarEdgeListGraph extends ReplicatingGraph {

    /** Empty graph, SVAR policy. */
    public SvarEdgeListGraph() {
        super(new LagReplicationPolicy());
    }

    /** Copy constructor, SVAR policy. */
    public SvarEdgeListGraph(Graph g) {
        // NOTE: relies on the fixed RepeatingGraph(Graph, EdgeReplicationPolicy)
        // that does a two-phase copy (super(); then transfer) to avoid NPE.
        super(g, new LagReplicationPolicy());
    }

    /** From nodes only, SVAR policy. */
    public SvarEdgeListGraph(List<Node> nodes) {
        super(nodes, new LagReplicationPolicy());
    }
}