package edu.cmu.tetrad.test;

import edu.cmu.tetrad.graph.*;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Regression test for stranded deep-tier edges in ReplicatingGraph. Endpoint marks are not
 * mirrored across lag slices, so homologous edges may legitimately carry different marks; edge
 * removal must therefore mirror at the level of the node pair, not edge equality. Before the
 * fix, removing an edge whose deep-tier homolog had drifted marks silently left that homolog in
 * place -- visible in practice as extra edges in the earliest (deepest-lag) tier that do not
 * occur at subsequent lags, since the deepest tier's own separators lie outside the lag window
 * and it is never cleaned directly by the search.
 */
public class TestReplicatingGraphPairRemoval {

    @Test
    public void testRemovalMirrorsAcrossDriftedMarks() {
        Node x0 = new GraphNode("X");
        Node x1 = new GraphNode("X:1");
        Node x2 = new GraphNode("X:2");
        Node y0 = new GraphNode("Y");
        Node y1 = new GraphNode("Y:1");
        Node y2 = new GraphNode("Y:2");

        ReplicatingGraph g = new ReplicatingGraph(new LagReplicationPolicy());
        for (Node n : new Node[]{x0, x1, x2, y0, y1, y2}) g.addNode(n);

        // Adding the lag-0 edge mirrors it to all three slices.
        g.addEdge(new Edge(x0, y0, Endpoint.TAIL, Endpoint.ARROW));
        assertEquals(3, g.getNumEdges());

        // Simulate orientation drift at the deepest tier: marks are not mirrored, so this is a
        // legitimate state during search (e.g., boundary orientations from dagToPag).
        g.setEndpoint(x2, y2, Endpoint.CIRCLE);
        g.setEndpoint(y2, x2, Endpoint.CIRCLE);

        // Removing the lag-0 edge must clear the whole homolog class, drifted marks included.
        Edge e0 = g.getEdge(x0, y0);
        g.removeEdge(e0);

        assertEquals("Homolog removal must not strand differently-marked deep-tier copies.",
                0, g.getNumEdges());
    }

    @Test
    public void testAddDoesNotStackOntoOccupiedMirrorSlot() {
        Node x0 = new GraphNode("X");
        Node x1 = new GraphNode("X:1");
        Node y0 = new GraphNode("Y");
        Node y1 = new GraphNode("Y:1");

        ReplicatingGraph g = new ReplicatingGraph(new LagReplicationPolicy());
        for (Node n : new Node[]{x0, x1, y0, y1}) g.addNode(n);

        // Adding at lag 1 mirrors a copy to lag 0; both slots hold circle-circle edges.
        g.addEdge(new Edge(x1, y1, Endpoint.CIRCLE, Endpoint.CIRCLE));
        assertEquals(1, g.getEdges(x1, y1).size());

        // The caller now explicitly adds a differently-marked edge at lag 0. Base
        // EdgeListGraph semantics permit a second differently-marked edge on the REQUESTED
        // pair (that is the caller's act, e.g., directed alongside bidirected); the invariant
        // owed by REPLICATION is that the mirror step must not stack a templated copy onto
        // the already-occupied lag-1 slot.
        g.addEdge(new Edge(x0, y0, Endpoint.TAIL, Endpoint.ARROW));

        assertEquals("Replication must not stack a mirrored copy onto an occupied slot.",
                1, g.getEdges(x1, y1).size());
    }
}
