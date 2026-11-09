package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.Ccd;
import edu.cmu.tetrad.search.test.MsepTest;
import org.junit.Test;

import java.util.Arrays;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Detailed tests for Ccd algorithm.
 */
public class TestCcdDetailed {

    /**
     * Test CCD on a simple DAG: A -> B <- C, with B -> D.
     * Expected PAG should have colliders at B.
     */
    @Test
    public void testSimpleDag() throws InterruptedException {
        Graph dag = new EdgeListGraph();
        Node a = new GraphNode("A");
        Node b = new GraphNode("B");
        Node c = new GraphNode("C");
        Node d = new GraphNode("D");
        dag.addNode(a);
        dag.addNode(b);
        dag.addNode(c);
        dag.addNode(d);
        dag.addDirectedEdge(a, b);
        dag.addDirectedEdge(c, b);
        dag.addDirectedEdge(b, d);

        MsepTest test = new MsepTest(dag);
        Ccd ccd = new Ccd(test);
        Graph pag = ccd.search();

        // Adjacencies: A-B, C-B, B-D
        assertTrue(pag.isAdjacentTo(a, b));
        assertTrue(pag.isAdjacentTo(c, b));
        assertTrue(pag.isAdjacentTo(b, d));
        assertFalse(pag.isAdjacentTo(a, c));

        // Orientation: A -> B <- C
        assertEquals(Endpoint.ARROW, pag.getEndpoint(a, b));
        assertEquals(Endpoint.TAIL, pag.getEndpoint(b, a));
        assertEquals(Endpoint.ARROW, pag.getEndpoint(c, b));
        assertEquals(Endpoint.TAIL, pag.getEndpoint(b, c));

        // Orientation: B -> D (or B o-> D depending on rules, but B-D is oriented in CCD)
        // Actually, if B->D is in the DAG, then {B} separates A and D? No, {B} doesn't.
        // A _||_ D | B. So B-D should be oriented.
        assertEquals(Endpoint.ARROW, pag.getEndpoint(b, d));
        assertEquals(Endpoint.TAIL, pag.getEndpoint(d, b));
    }

    /**
     * Test CCD with a 2-cycle: X -> Y, Y -> X.
     * In cyclic models, X and Y are adjacent and it's a 2-cycle.
     */
    @Test
    public void testTwoCycle() throws InterruptedException {
        Graph g = new EdgeListGraph();
        Node x = new GraphNode("X");
        Node y = new GraphNode("Y");
        g.addNode(x);
        g.addNode(y);
        g.addDirectedEdge(x, y);
        g.addDirectedEdge(y, x);

        MsepTest test = new MsepTest(g);
        Ccd ccd = new Ccd(test);
        Graph pag = ccd.search();

        assertTrue(pag.isAdjacentTo(x, y));
        // In a 2-cycle, usually they are oriented as circles if no other info.
        // But FAS orients as circles. Step B only orients unshielded triples.
        // So X o-o Y is expected.
        assertEquals(Endpoint.CIRCLE, pag.getEndpoint(x, y));
        assertEquals(Endpoint.CIRCLE, pag.getEndpoint(y, x));
    }

    /**
     * Test CCD with latents: X -> L -> Y, X -> Y. L is latent.
     * The graph on {X, Y} is X -> Y.
     * If we have X <- L -> Y, it's X <-> Y.
     */
    @Test
    public void testLatentConfounder() throws InterruptedException {
        Graph g = new EdgeListGraph();
        Node x = new GraphNode("X");
        Node y = new GraphNode("Y");
        Node l = new GraphNode("L");
        g.addNode(x);
        g.addNode(y);
        g.addNode(l);
        g.addDirectedEdge(l, x);
        g.addDirectedEdge(l, y);

        // Test only on X, Y
        MsepTest test = new MsepTest(g);
        Ccd ccd = new Ccd(test.indTestSubset(Arrays.asList(x, y)));
        Graph pag = ccd.search();

        assertTrue(pag.isAdjacentTo(x, y));
        // X and Y are dependent, but no unshielded triples to orient anything.
        assertEquals(Endpoint.CIRCLE, pag.getEndpoint(x, y));
        assertEquals(Endpoint.CIRCLE, pag.getEndpoint(y, x));
    }

    /**
     * Test CCD with background knowledge (forbidden edges).
     */
    @Test
    public void testKnowledge() throws InterruptedException {
        Graph dag = new EdgeListGraph();
        Node a = new GraphNode("A");
        Node b = new GraphNode("B");
        Node c = new GraphNode("C");
        dag.addNode(a);
        dag.addNode(b);
        dag.addNode(c);
        dag.addDirectedEdge(a, b);
        dag.addDirectedEdge(c, b);

        MsepTest test = new MsepTest(dag);
        Ccd ccd = new Ccd(test);

        Knowledge k = new Knowledge();
        k.setForbidden("A", "B");
        ccd.setKnowledge(k);

        Graph pag = ccd.search();

        // A-B is adjacent but NOT oriented as A->B
        assertEquals(Endpoint.CIRCLE, pag.getEndpoint(a, b));
        // C-B IS oriented as C->B
        assertEquals(Endpoint.ARROW, pag.getEndpoint(c, b));
    }

    /**
     * Test Step D and E (dotted underlines and propagation).
     * We need a structure where a dotted underline is formed.
     * In the simple cycle sanity test: X1->X4, X2->X3, X3<->X4.
     * Dotted underlines: <X1, X4, X2> and <X1, X3, X2>.
     */
    @Test
    public void testDottedUnderlinePropagation() throws InterruptedException {
        Node X1 = new GraphNode("X1");
        Node X2 = new GraphNode("X2");
        Node X3 = new GraphNode("X3");
        Node X4 = new GraphNode("X4");

        Graph g = new EdgeListGraph(Arrays.asList(X1, X2, X3, X4));
        g.addDirectedEdge(X1, X4);
        g.addDirectedEdge(X2, X3);
        g.addDirectedEdge(X3, X4);
        g.addDirectedEdge(X4, X3);

        MsepTest test = new MsepTest(g);
        Ccd ccd = new Ccd(test);
        ccd.setApplyR1(false);
        Graph pag = ccd.search();

        Set<Triple> dotted = pag.getDottedUnderlines();
        assertFalse("Should have dotted underlines", dotted.isEmpty());
        
        // Check if orientations from Step E/F occurred.
        // In the sanity test, X1->X4 and X2->X3 are recovered.
        assertEquals(Endpoint.ARROW, pag.getEndpoint(X1, X4));
        assertEquals(Endpoint.ARROW, pag.getEndpoint(X2, X3));
        
        // X3-X4 should be tail-tail (non-oriented circle nodes in CCD often end up as tail-tail if they are part of a cycle and no other info)
        // Wait, the sanity test says X3 --- X4 (tail-tail).
        assertEquals(Endpoint.TAIL, pag.getEndpoint(X3, X4));
        assertEquals(Endpoint.TAIL, pag.getEndpoint(X4, X3));
    }
}
