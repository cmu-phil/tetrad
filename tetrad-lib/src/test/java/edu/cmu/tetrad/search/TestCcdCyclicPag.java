package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.test.MsepTest;
import org.junit.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The cyclic PAG CCD is meant to recover, and the properties the CCD fixes rest on. Everything
 * here runs against an m-separation oracle on a fixed graph, so the tests are deterministic and
 * involve no sampling.
 *
 * <p>The graph is Richardson's canonical motif: A -&gt; X, B -&gt; Y and a 2-cycle X &lt;-&gt; Y,
 * with Y -&gt; Z hanging off it. The cycle makes A inseparable from Y and B inseparable from X
 * although neither pair is adjacent, so the PAG carries "virtual edges" the true graph does not.
 */
public class TestCcdCyclicPag {

    private static Graph motif() {
        Node a = new GraphNode("A");
        Node b = new GraphNode("B");
        Node x = new GraphNode("X");
        Node y = new GraphNode("Y");
        Node z = new GraphNode("Z");

        Graph g = new EdgeListGraph(List.of(a, b, x, y, z));
        g.addDirectedEdge(a, x);
        g.addDirectedEdge(b, y);
        g.addDirectedEdge(x, y);
        g.addDirectedEdge(y, x);
        g.addDirectedEdge(y, z);
        return g;
    }

    /**
     * The comparison graph must carry the virtual edges, since those are what CCD is supposed to
     * find and what the true graph lacks.
     */
    @Test
    public void ccdPagCarriesTheVirtualEdges() {
        Graph g = motif();
        Graph pag = GraphTransforms.dcgToCcdPag(g);

        assertTrue("A and Y are inseparable given the cycle, so the PAG must join them",
                pag.isAdjacentTo(pag.getNode("A"), pag.getNode("Y")));
        assertTrue("B and X are inseparable given the cycle, so the PAG must join them",
                pag.isAdjacentTo(pag.getNode("B"), pag.getNode("X")));
        assertTrue("the PAG must have more edges than the true graph",
                pag.getNumEdges() > g.getNumEdges());
    }

    /**
     * Grading CCD against the comparison graph rather than the true graph is the whole point of
     * {@link GraphTransforms#dcgToCcdPag(Graph)}: an oracle run must score perfectly against it.
     */
    @Test
    public void oracleCcdExactlyMatchesTheComparisonGraph() throws Exception {
        Graph g = motif();
        Graph oracle = new Ccd(new MsepTest(g)).search();
        Graph comparison = GraphTransforms.dcgToCcdPag(g);

        assertEquals("same adjacencies", comparison.getNumEdges(), oracle.getNumEdges());

        for (Edge edge : comparison.getEdges()) {
            Node x = oracle.getNode(edge.getNode1().getName());
            Node y = oracle.getNode(edge.getNode2().getName());
            assertTrue("edge missing from the oracle output: " + edge, oracle.isAdjacentTo(x, y));
            assertEquals("endpoint at " + x, edge.getProximalEndpoint(edge.getNode1()),
                    oracle.getEdge(x, y).getProximalEndpoint(x));
            assertEquals("endpoint at " + y, edge.getProximalEndpoint(edge.getNode2()),
                    oracle.getEdge(x, y).getProximalEndpoint(y));
        }

        assertEquals("same dotted underlines",
                comparison.getDottedUnderlines().size(), oracle.getDottedUnderlines().size());
    }

    /**
     * BOSS-CCD closes its superstructure under skeleton distance 2 because that is where the
     * virtual edges lie. If a virtual edge were ever further away, the closure would exclude it
     * and CCD, which can only remove edges from a superstructure, could never recover it.
     */
    @Test
    public void virtualEdgesLieAtSkeletonDistanceTwo() {
        Graph g = motif();
        Graph pag = GraphTransforms.dcgToCcdPag(g);

        Graph skeleton = new EdgeListGraph(g.getNodes());
        for (Edge edge : g.getEdges()) {
            if (!skeleton.isAdjacentTo(edge.getNode1(), edge.getNode2())) {
                skeleton.addUndirectedEdge(edge.getNode1(), edge.getNode2());
            }
        }

        int virtual = 0;

        for (Edge edge : pag.getEdges()) {
            Node x = g.getNode(edge.getNode1().getName());
            Node y = g.getNode(edge.getNode2().getName());
            if (g.isAdjacentTo(x, y)) continue;

            virtual++;
            assertEquals("virtual edge " + x + "..." + y + " must be two steps apart", 2,
                    distance(skeleton, skeleton.getNode(x.getName()), skeleton.getNode(y.getName())));
        }

        assertTrue("the motif should produce virtual edges at all", virtual > 0);
    }

    /** Every collider rule must still give the right answer when the test is an oracle. */
    @Test
    public void allColliderRulesAgreeUnderAnOracle() throws Exception {
        Graph g = motif();
        Graph expected = GraphTransforms.dcgToCcdPag(g);

        for (Ccd.ColliderRule rule : Ccd.ColliderRule.values()) {
            Ccd ccd = new Ccd(new MsepTest(g));
            ccd.setColliderRule(rule);
            Graph out = ccd.search();

            assertEquals(rule + " should recover the same adjacencies",
                    expected.getNumEdges(), out.getNumEdges());

            for (Edge edge : expected.getEdges()) {
                Node x = out.getNode(edge.getNode1().getName());
                Node y = out.getNode(edge.getNode2().getName());
                assertNotNull(rule + " dropped " + edge, out.getEdge(x, y));
            }
        }
    }

    @Test
    public void greedyIsTheDefaultColliderRule() {
        assertEquals(Ccd.ColliderRule.GREEDY, new Ccd(new MsepTest(motif())).getColliderRule());
    }

    @Test
    public void nullColliderRuleIsRejected() {
        try {
            new Ccd(new MsepTest(motif())).setColliderRule(null);
            fail("expected a NullPointerException");
        } catch (NullPointerException expected) {
            // wanted
        }
    }

    /** Breadth-first distance in an undirected graph, or -1 if unreachable. */
    private static int distance(Graph g, Node source, Node target) {
        Map<Node, Integer> seen = new HashMap<>();
        Deque<Node> queue = new ArrayDeque<>();
        seen.put(source, 0);
        queue.add(source);

        while (!queue.isEmpty()) {
            Node u = queue.poll();
            if (u == target) return seen.get(u);

            for (Node v : new ArrayList<>(g.getAdjacentNodes(u))) {
                if (!seen.containsKey(v)) {
                    seen.put(v, seen.get(u) + 1);
                    queue.add(v);
                }
            }
        }

        return -1;
    }
}
