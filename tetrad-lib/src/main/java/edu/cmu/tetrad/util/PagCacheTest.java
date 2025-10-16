package edu.cmu.tetrad.util;

import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.utils.MagToPag;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.ejml.UtilEjml.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/**
 * Unit tests for PagCache:
 * 1) Repeated calls on same source return the SAME PAG object (identity preserved).
 * 2) If a caller mutates the returned PAG, a subsequent getPag() repairs it IN PLACE (identity unchanged).
 * 3) If the SOURCE graph changes structurally, getPag() rebuilds and returns a DIFFERENT PAG instance,
 *    and then preserves that new identity across further calls.
 */
public class PagCacheTest {

    // ----------------------- helpers -----------------------

    /** Simple 3-node DAG: A->B, B->C. */
    private static Graph makeDagABC() {
        Graph dag = new EdgeListGraph();
        Node A = new GraphNode("A");
        Node B = new GraphNode("B");
        Node C = new GraphNode("C");
        dag.addNode(A);
        dag.addNode(B);
        dag.addNode(C);
        dag.addEdge(Edges.directedEdge(A, B));
        dag.addEdge(Edges.directedEdge(B, C));
        return dag;
    }

    /** Deterministic, readable encoding of a PAG’s structure (edges + endpoints). */
    private static String canon(Graph g) {
        List<String> lines = new ArrayList<>();
        for (Edge e : g.getEdges()) {
            String a = e.getNode1().getName();
            String b = e.getNode2().getName();
            boolean swap = a.compareTo(b) > 0;
            Node x = swap ? e.getNode2() : e.getNode1();
            Node y = swap ? e.getNode1() : e.getNode2();
            Endpoint xy = g.getEndpoint(x, y);
            Endpoint yx = g.getEndpoint(y, x);
            lines.add(x.getName() + "|" + y.getName() + "|" +
                      (xy == null ? "N" : xy.name()) + "|" +
                      (yx == null ? "N" : yx.name()));
        }
        lines.sort(Comparator.naturalOrder());
        return String.join("\n", lines);
    }

    /** Reference PAG for a DAG, computed without the cache (mirrors PagCache.computePag(DAG)). */
    private static Graph referencePagFromDag(Graph dag) {
        Graph mag = GraphTransforms.dagToMag(dag);
        return new MagToPag(mag).convert(false);
    }

    // ----------------------- tests -----------------------

    @Test
    public void identityPreservedOnRepeatedCalls() {
        Graph dag = makeDagABC();
        PagCache cache = PagCache.getInstance();
        cache.clear();

        Graph pag1 = cache.getPag(dag);
        Graph pag2 = cache.getPag(dag);

        assertTrue(pag1 == pag2, "PagCache should return the SAME PAG object across calls for the same source graph");
        assertEquals("PAG structure should match reference conversion", canon(referencePagFromDag(dag)), canon(pag1));
    }

    @Test
    public void externalMutationIsRepairedInPlace() {
        Graph dag = makeDagABC();
        PagCache cache = PagCache.getInstance();
        cache.clear();

        Graph pag1 = cache.getPag(dag);
        String expected = canon(referencePagFromDag(dag));

        // Mutate the returned PAG (simulate a naughty caller changing endpoints)
        // Flip the endpoints on the first edge we find.
        Edge any = pag1.getEdges().iterator().next();
        Node u = any.getNode1();
        Node v = any.getNode2();
        // Toggle to something different to force a signature change:
        Endpoint uv = pag1.getEndpoint(u, v);
        Endpoint vu = pag1.getEndpoint(v, u);
        Endpoint uvNew = (uv == Endpoint.ARROW) ? Endpoint.TAIL : Endpoint.ARROW;
        Endpoint vuNew = (vu == Endpoint.ARROW) ? Endpoint.TAIL : Endpoint.ARROW;
        pag1.setEndpoint(u, v, uvNew);
        pag1.setEndpoint(v, u, vuNew);

        // Ask again: cache should detect mutation and repair the same object in place
        Graph pag2 = cache.getPag(dag);

        assertTrue(pag1 == pag2, "After external mutation, cache must repair IN PLACE (identity preserved)");
        assertEquals("Repaired PAG should match the reference structure", expected, canon(pag2));
    }

    @Test
    public void sourceMutationForcesRebuildAndNewIdentity() {
        Graph dag = makeDagABC();
        PagCache cache = PagCache.getInstance();
        cache.clear();

        Graph pag1 = cache.getPag(dag);
        String canon1 = canon(pag1);

        // Mutate the SOURCE graph structurally: add edge A->C
        Node A = dag.getNode("A");
        Node C = dag.getNode("C");
        dag.addEdge(Edges.directedEdge(A, C));

        // Now getPag should recompute and return a DIFFERENT object (entry replaced)
        Graph pag2 = cache.getPag(dag);
        assertTrue(pag1 != pag2, "Changing the SOURCE graph should replace the cached PAG instance");

        // Repeated calls after rebuild should preserve the new identity
        Graph pag3 = cache.getPag(dag);
        assertTrue(pag2 == pag3, "After rebuild, identity should be stable across calls");

        // And the new contents should differ from the old canonical structure
        assertNotEquals("PAG contents should reflect the source graph change", canon1, canon(pag2));
    }
}