package edu.cmu.tetrad.graph;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests for ReplicatingGraph with LagReplicationPolicy.
 *
 * Node naming convention: "X" = X at lag 0, "X:1" = X at lag 1, "X:2" = X at lag 2, etc.
 *
 * Key invariant: any mutation on one lag-slice (add, remove, orient) should
 * be automatically mirrored to all other lag-slices that have the same
 * base-variable pair with the same lag shift.
 */
public class ReplicatingGraphTest {

    // ------------------------------------------------------------------ nodes
    // Two variables (X, Y), three lags (0, 1, 2)
    private Node x0, x1, x2;
    private Node y0, y1, y2;
    private ReplicatingGraph g;

    @Before
    public void setUp() {
        x0 = new GraphNode("X");
        x1 = new GraphNode("X:1");
        x2 = new GraphNode("X:2");
        y0 = new GraphNode("Y");
        y1 = new GraphNode("Y:1");
        y2 = new GraphNode("Y:2");

        g = new ReplicatingGraph(
                List.of(x0, x1, x2, y0, y1, y2),
                new LagReplicationPolicy());
    }

    // ================================================================
    // 1. Adding contemporaneous edges (shift = 0)
    // ================================================================

    /**
     * Adding X:0 -- Y:0 (shift 0) should mirror to X:1 -- Y:1 and X:2 -- Y:2.
     */
    @Test
    public void addContemporaneousEdge_mirrorsToAllLags() {
        g.addUndirectedEdge(x0, y0);

        assertTrue("X:0 -- Y:0 should exist", g.isAdjacentTo(x0, y0));
        assertTrue("X:1 -- Y:1 should be mirrored", g.isAdjacentTo(x1, y1));
        assertTrue("X:2 -- Y:2 should be mirrored", g.isAdjacentTo(x2, y2));
    }

    /**
     * Adding X:1 -- Y:1 (shift 0 at lag 1) should also mirror to lag 0 and lag 2.
     */
    @Test
    public void addContemporaneousEdgeAtLag1_mirrorsToOtherLags() {
        g.addUndirectedEdge(x1, y1);

        assertTrue("X:0 -- Y:0 should be mirrored", g.isAdjacentTo(x0, y0));
        assertTrue("X:1 -- Y:1 should exist", g.isAdjacentTo(x1, y1));
        assertTrue("X:2 -- Y:2 should be mirrored", g.isAdjacentTo(x2, y2));
    }

    // ================================================================
    // 2. Adding cross-lag edges (shift != 0)
    // ================================================================

    /**
     * Adding X:1 --> Y:0 (shift -1, i.e. X at past lag drives Y now) should
     * mirror to X:2 --> Y:1.  There is no X:3 so no further mirror.
     */
    @Test
    public void addCrossLagEdge_mirrorsWhereNodesExist() {
        g.addDirectedEdge(x1, y0);   // X:1 -> Y:0, shift = 0 - 1 = -1

        assertTrue("X:1 -> Y:0 should exist", g.isAdjacentTo(x1, y0));
        assertTrue("X:2 -> Y:1 should be mirrored", g.isAdjacentTo(x2, y1));
        assertFalse("X:0 -> Y:-1 should NOT exist (Y:-1 not in graph)",
                g.isAdjacentTo(x0, y1));
    }

    // ================================================================
    // 3. Removing edges cascades mirrors
    // ================================================================

    /**
     * Removing one slice of a mirrored contemporaneous edge should remove all slices.
     */
    @Test
    public void removeEdge_removesAllMirrors() {
        g.addUndirectedEdge(x0, y0);   // creates x0--y0, x1--y1, x2--y2

        g.removeEdge(x0, y0);

        assertFalse("X:0 -- Y:0 should be removed", g.isAdjacentTo(x0, y0));
        assertFalse("X:1 -- Y:1 mirror should be removed", g.isAdjacentTo(x1, y1));
        assertFalse("X:2 -- Y:2 mirror should be removed", g.isAdjacentTo(x2, y2));
    }

    /**
     * Removing a cross-lag edge should remove its mirror too.
     */
    @Test
    public void removeCrossLagEdge_removesAllMirrors() {
        g.addDirectedEdge(x1, y0);   // creates x1->y0, x2->y1

        g.removeEdge(x1, y0);

        assertFalse("X:1 -> Y:0 should be removed", g.isAdjacentTo(x1, y0));
        assertFalse("X:2 -> Y:1 mirror should be removed", g.isAdjacentTo(x2, y1));
    }

    // ================================================================
    // 4. No phantom edges between unrelated variables
    // ================================================================

    /**
     * Adding X:0 -- Y:0 must not accidentally create X:0 -- X:1 or Y:0 -- Y:1,
     * i.e. same-variable cross-lag edges.
     */
    @Test
    public void addEdge_doesNotCreateSpuriousSameVariableEdges() {
        g.addUndirectedEdge(x0, y0);

        assertFalse("X:0 -- X:1 must NOT be created", g.isAdjacentTo(x0, x1));
        assertFalse("Y:0 -- Y:1 must NOT be created", g.isAdjacentTo(y0, y1));
        assertFalse("X:0 -- Y:1 must NOT be created", g.isAdjacentTo(x0, y1));
        assertFalse("X:1 -- Y:0 must NOT be created", g.isAdjacentTo(x1, y0));
    }

    // ================================================================
    // 5. Edge count sanity
    // ================================================================

    /**
     * With 3 lag-slices and shift=0, one addUndirectedEdge call should produce
     * exactly 3 edges total (one per slice).
     */
    @Test
    public void edgeCount_contemporaneous_exactlyThree() {
        g.addUndirectedEdge(x0, y0);
        assertEquals("Expected exactly 3 mirrored contemporaneous edges",
                3, g.getEdges().size());
    }

    /**
     * With 3 lags, a cross-lag edge (shift=-1) starting at lag 1 can mirror
     * to lag 2 only → 2 edges total.
     */
    @Test
    public void edgeCount_crossLag_exactlyTwo() {
        g.addDirectedEdge(x1, y0);
        assertEquals("Expected exactly 2 mirrored cross-lag edges",
                2, g.getEdges().size());
    }

    // ================================================================
    // 6. Orientation mirroring via setEndpoint
    // ================================================================

    /**
     * Orienting one slice of a contemporaneous undirected edge should orient
     * all slices the same way.
     */
    @Test
    public void setEndpoint_mirrorsOrientationToAllSlices() {
        g.addUndirectedEdge(x0, y0);  // all slices start undirected

        // Orient x0 -> y0
        g.setEndpoint(x0, y0, Endpoint.ARROW);
        g.setEndpoint(y0, x0, Endpoint.TAIL);

        // Check all mirrors have same orientation
        Edge e1 = g.getEdge(x1, y1);
        Edge e2 = g.getEdge(x2, y2);

        assertNotNull("X:1 -- Y:1 should still exist", e1);
        assertNotNull("X:2 -- Y:2 should still exist", e2);

        assertEquals("Y:1 end of X:1->Y:1 should be ARROW",
                Endpoint.ARROW, e1.getProximalEndpoint(y1));
        assertEquals("Y:2 end of X:2->Y:2 should be ARROW",
                Endpoint.ARROW, e2.getProximalEndpoint(y2));
    }

    // ================================================================
    // 7. Recursion guard — no infinite loop
    // ================================================================

    /**
     * Adding many edges should not cause a StackOverflowError.
     * The IN_REPLICATION guard must prevent recursive re-entry.
     */
    @Test
    public void addEdge_noInfiniteRecursion() {
        // If IN_REPLICATION guard is broken this will StackOverflow
        g.addUndirectedEdge(x0, y0);
        g.addDirectedEdge(x1, y0);
        g.addDirectedEdge(x2, y1);
        // reaching here without exception is the assertion
    }

    // ================================================================
    // 8. Copy constructor preserves edges without re-mirroring
    // ================================================================

    /**
     * The copy constructor ReplicatingGraph(ReplicatingGraph) should produce
     * an identical graph without doubling up edges.
     */
    @Test
    public void copyConstructor_preservesEdgesExactly() {
        g.addUndirectedEdge(x0, y0);
        g.addDirectedEdge(x1, y0);

        ReplicatingGraph copy = new ReplicatingGraph(g);

        assertEquals("Copy should have the same number of edges as the original",
                g.getEdges().size(), copy.getEdges().size());
        for (Edge e : g.getEdges()) {
            Node n1 = copy.getNode(e.getNode1().getName());
            Node n2 = copy.getNode(e.getNode2().getName());
            assertTrue("Copy should contain edge " + e,
                    copy.isAdjacentTo(n1, n2));
        }
    }

    // ================================================================
    // 9. Non-lag-named nodes — no mirroring, no crash
    // ================================================================

    /**
     * Nodes whose names don't follow the "X" / "X:N" convention should be
     * treated as unrelated singletons — no mirroring, no exception.
     */
    @Test
    public void noLagConvention_noMirroringNoException() {
        Node a = new GraphNode("alpha");
        Node b = new GraphNode("beta");
        ReplicatingGraph g2 = new ReplicatingGraph(
                List.of(a, b), new LagReplicationPolicy());

        g2.addUndirectedEdge(a, b);
        assertEquals("Non-lag nodes: only the one explicit edge should exist",
                1, g2.getEdges().size());
    }

    // ================================================================
    // 10. GraphFactoryUtil integration
    // ================================================================

    /**
     * GraphFactoryUtil.newGraph(nodes, true) should return a ReplicatingGraph.
     */
    @Test
    public void factoryUtil_replicatingFlagTrue_returnsReplicatingGraph() {
        Graph g2 = GraphFactoryUtil.newGraph(List.of(x0, x1, y0, y1), true);
        assertTrue("Factory should return a ReplicatingGraph when replicating=true",
                g2 instanceof ReplicatingGraph);
    }

    /**
     * GraphFactoryUtil.newGraph(nodes, false) should return a plain EdgeListGraph.
     */
    @Test
    public void factoryUtil_replicatingFlagFalse_returnsEdgeListGraph() {
        Graph g2 = GraphFactoryUtil.newGraph(List.of(x0, x1, y0, y1), false);
        assertFalse("Factory should NOT return a ReplicatingGraph when replicating=false",
                g2 instanceof ReplicatingGraph);
    }
}
