package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.Ccd;
import edu.cmu.tetrad.search.test.MsepTest;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.*;

public class TestCcdKnowledge {

    @Test
    public void testForbiddenEdgeInCollider() throws InterruptedException {
        // Create a graph: A -> B <- C
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

        // Forbid A -> B
        Knowledge k = new Knowledge();
        k.setForbidden("A", "B");
        ccd.setKnowledge(k);

        Graph pag = ccd.search();

        // If A -> B is forbidden, Ccd's Step B will fail to add the arrowhead at B from A.
        // As a result, A-B-C will not be a definite collider.
        // Therefore, Step D will not consider it for dotted underlines (though it's unshielded here,
        // so maybe it's less critical for this simple case).

        // Let's check if the edge A-B is still there and its orientation.
        Edge ab = pag.getEdge(a, b);
        assertNotNull(ab);

        // Since it was forbidden, it should stay as a circle-circle (FAS orients all as circles initially).
        assertEquals(Endpoint.CIRCLE, pag.getEndpoint(a, b));
        assertEquals(Endpoint.CIRCLE, pag.getEndpoint(b, a));

        // C -> B should be oriented if not forbidden.
        assertEquals(Endpoint.ARROW, pag.getEndpoint(c, b));
    }

    @Test
    public void testDottedUnderlineWithForbiddenEdge() throws InterruptedException {
        // Build the canonical graph: X1->X4, X2->X3, X3<->X4
        Node X1 = new GraphNode("X1");
        Node X2 = new GraphNode("X2");
        Node X3 = new GraphNode("X3");
        Node X4 = new GraphNode("X4");

        Graph g = new EdgeListGraph(java.util.Arrays.asList(X1, X2, X3, X4));
        g.addDirectedEdge(X1, X4);
        g.addDirectedEdge(X2, X3);
        g.addDirectedEdge(X3, X4);
        g.addDirectedEdge(X4, X3);

        MsepTest test = new MsepTest(g);
        Ccd ccd = new Ccd(test);
        ccd.setApplyR1(false);
        
        // Forbid X1 -> X4
        Knowledge k = new Knowledge();
        k.setForbidden("X1", "X4");
        ccd.setKnowledge(k);

        Graph pag = ccd.search();
        
        // X1 o-o X4 because X1->X4 is forbidden.
        // X2 -> X3 because not forbidden.
        // X3 o-o X4 because it's a 2-cycle.
        
        // Let's check dotted underlines.
        // In the normal case (no knowledge), <X1, X4, X2> and <X1, X3, X2> are dotted underlines.
        // Wait, X1-X4-X3: X4 is a collider because X1->X4 and X3->X4.
        // If X1->X4 is forbidden, X4 is NOT a def collider for (X1, X4, X3).
        
        System.out.println("Edges: " + pag.getEdges());
        System.out.println("Dotted underlines: " + pag.getDottedUnderlines());
        
        assertFalse(pag.getDottedUnderlines().isEmpty());
    }
}
