package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.Fci;
import edu.cmu.tetrad.search.test.MsepTest;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class TestFciEagerness {

    @Test
    public void testEagerBidirectedEdge() throws InterruptedException {
        // Create a DAG: X -> Y <- Z, W -> X
        // In a PAG with no latents, we expect X -> Y <- Z and W -> X.
        // Wait, if we have X -> Y <- Z, Y is a collider.
        // If we also have W -> X, and no other info, X is not a collider.
        
        Graph dag = new EdgeListGraph();
        Node w = new GraphNode("W");
        Node x = new GraphNode("X");
        Node y = new GraphNode("Y");
        Node z = new GraphNode("Z");
        dag.addNode(w);
        dag.addNode(x);
        dag.addNode(y);
        dag.addNode(z);
        dag.addDirectedEdge(w, x);
        dag.addDirectedEdge(x, y);
        dag.addDirectedEdge(z, y);

        MsepTest test = new MsepTest(dag);
        Fci fci = new Fci(test);
        Graph result = fci.search();
        
        // Expected: W o-> X -> Y <- Z
    }
    
    @Test
    public void testTwoCollidersOnOneEdge() throws InterruptedException {
        // DAG: W -> X <- L, L -> Y <- Z
        // L is a latent.
        // If we don't include L in the nodes:
        // W -> X, Y <- Z, and X <-> Y because of latent L.
        // Actually, if L is latent: W -> X, Z -> Y, and X <-> Y.
        
        Graph dag = new EdgeListGraph();
        Node w = new GraphNode("W");
        Node x = new GraphNode("X");
        Node y = new GraphNode("Y");
        Node z = new GraphNode("Z");
        Node l = new GraphNode("L");
        dag.addNode(w);
        dag.addNode(x);
        dag.addNode(y);
        dag.addNode(z);
        dag.addNode(l);
        dag.addDirectedEdge(w, x);
        dag.addDirectedEdge(l, x);
        dag.addDirectedEdge(l, y);
        dag.addDirectedEdge(z, y);

        List<Node> observableNodes = List.of(w, x, y, z);
        MsepTest test = new MsepTest(dag);
        test = (MsepTest) test.indTestSubset(observableNodes);
        Fci fci = new Fci(test);
        Graph result = fci.search();

        // Expected: W *-> X <-> Y <-* Z
        
        assertTrue(result.isAdjacentTo(x, y));
        assertEquals(Endpoint.ARROW, result.getEndpoint(x, y));
        assertEquals(Endpoint.ARROW, result.getEndpoint(y, x));
    }

    @Test
    public void testForbiddenDirectEdgeCausesBidirected() throws InterruptedException {
        // DAG: X --- Y (confounded by L), W -> X, Z -> Y
        // We observe W, X, Y, Z.
        // If we use MsepTest, we need to make sure L is not observable.
        
        Graph dag = new EdgeListGraph();
        Node w = new GraphNode("W");
        Node x = new GraphNode("X");
        Node y = new GraphNode("Y");
        Node z = new GraphNode("Z");
        Node l = new GraphNode("L");
        dag.addNode(w); dag.addNode(x); dag.addNode(y); dag.addNode(z); dag.addNode(l);
        dag.addDirectedEdge(w, x);
        dag.addDirectedEdge(l, x);
        dag.addDirectedEdge(l, y);
        dag.addDirectedEdge(z, y);

        List<Node> observableNodes = List.of(w, x, y, z);
        
        MsepTest test = new MsepTest(dag);
        test = (MsepTest) test.indTestSubset(observableNodes);
        Fci fci = new Fci(test);
        
        Knowledge k = new Knowledge();
        k.setForbidden("X", "Y"); // X -> Y forbidden
        k.setForbidden("Y", "X"); // Y -> X forbidden
        fci.setKnowledge(k);
        
        Graph result = fci.search();
        
        // If X and Y are adjacent, they should NOT be X <-> Y because knowledge forbids arrowheads.
        if (result.isAdjacentTo(x, y)) {
            assertNotEquals(Endpoint.ARROW, result.getEndpoint(x, y));
            assertNotEquals(Endpoint.ARROW, result.getEndpoint(y, x));
        }
    }
}
