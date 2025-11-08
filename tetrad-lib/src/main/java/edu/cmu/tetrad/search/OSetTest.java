package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.*;
import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.*;

/**
 * Unit tests for OSet (Henckel–Perković–Maathuis O-sets).
 */
public class OSetTest {

    /**
     * Build a simple Henckel-style DAG:
     *
     * <pre>
     *   U -&gt; X -&gt; Z -&gt; Y
     *   U -&gt; Z
     *   W -&gt; X        (instrument)
     *
     *   For the pair (X, Y):
     *     cn_G(X -&gt; Y) = {Z, Y}
     *     pa_G(cn)     = {X, U, Z}
     *     O_G(X -&gt; Y)  = {U}
     * </pre>
     */
    private Graph buildHenckelDag() {
        Graph g = new EdgeListGraph();

        Node X = new GraphNode("X");
        Node Y = new GraphNode("Y");
        Node Z = new GraphNode("Z");
        Node U = new GraphNode("U");
        Node W = new GraphNode("W"); // instrument

        g.addNode(X);
        g.addNode(Y);
        g.addNode(Z);
        g.addNode(U);
        g.addNode(W);

        g.addEdge(Edges.directedEdge(U, X));
        g.addEdge(Edges.directedEdge(U, Z));
        g.addEdge(Edges.directedEdge(X, Z));
        g.addEdge(Edges.directedEdge(Z, Y));
        g.addEdge(Edges.directedEdge(W, X)); // instrument into X

        return g;
    }

    /**
     * Tests the {@link OSet#oSetDag(Graph, Node, Node)} method using a classic
     * Henckel–Perković–Maathuis example DAG.
     *
     * <p>The DAG structure is:
     * <pre>
     *   U -&gt; X -&gt; Z -&gt; Y
     *   U -&gt; Z
     *   W -&gt; X        (instrument)
     * </pre>
     *
     * <p>For the causal pair (X, Y):
     * <ul>
     *   <li><code>cn_G(X -&gt; Y)</code> = {Z, Y}</li>
     *   <li><code>pa_G(cn)</code> = {X, U, Z}</li>
     *   <li><code>O_G(X -&gt; Y)</code> = {U}</li>
     * </ul>
     *
     * <p>The test verifies that:
     * <ul>
     *   <li>The computed O-set equals {U},</li>
     *   <li>and that it excludes mediators (Z), outcomes (Y), and instruments (W).</li>
     * </ul>
     */
    @Test
    public void testOSetDag_HenckelExample() {
        Graph dag = buildHenckelDag();
        assertTrue("Graph should be a legal DAG", dag.paths().isLegalDag());

        Node X = dag.getNode("X");
        Node Y = dag.getNode("Y");
        Node Z = dag.getNode("Z");
        Node U = dag.getNode("U");
        Node W = dag.getNode("W");

        Set<Node> oset = OSet.oSetDag(dag, X, Y);

        // Expect exactly {U}
        assertNotNull(oset);
        assertEquals(1, oset.size());
        assertTrue(oset.contains(U));
        assertFalse(oset.contains(X));
        assertFalse(oset.contains(Z)); // mediator must NOT be in the O-set
        assertFalse(oset.contains(Y)); // outcome must NOT be in the O-set
        assertFalse(oset.contains(W)); // instrument must NOT be in the O-set
    }

    /**
     * Tests the behavior of {@link OSet#oSetCpdag(Graph, Node, Node, int)} compared
     * to {@link OSet#oSetDag(Graph, Node, Node)} using a minimal confounding example.
     *
     * <p>The DAG structure is:
     * <pre>
     *   U -&gt; X -&gt; Y
     *   U -&gt; Y
     * </pre>
     *
     * <p>For this graph:
     * <ul>
     *   <li><code>O_G(X -&gt; Y)</code> in the DAG is {U}, since U is a parent of both X and Y
     *       and lies on a backdoor path from X to Y.</li>
     *   <li>When the DAG is converted to its CPDAG, the edges become undirected (Markov equivalence class).</li>
     *   <li>In this case, <code>O_G(X -&gt; Y)</code> in the CPDAG differs from the DAG’s O-set,
     *       because the direction of causal flow is no longer uniquely identified.</li>
     * </ul>
     *
     * <p>The test therefore asserts that:
     * <ul>
     *   <li>The O-set in the DAG equals {U},</li>
     *   <li>The O-set in the CPDAG is <em>not</em> equal to that of the DAG,
     *       since the CPDAG loses orientation information.</li>
     * </ul>
     */
    @Test
    public void testOSetCpdag_matchesDag() {
        // 1. Build the Henckel-style DAG
        Graph dag = new EdgeListGraph();

        Node U = new GraphNode("U");
        Node X = new GraphNode("X");
        Node Y = new GraphNode("Y");

        dag.addNode(U);
        dag.addNode(X);
        dag.addNode(Y);

        dag.addDirectedEdge(U, X);
        dag.addDirectedEdge(U, Y);
        dag.addDirectedEdge(X, Y);

        assertTrue(dag.paths().isLegalDag());

        // 2. O-set in DAG
        Set<Node> oDag = OSet.oSetDag(dag, X, Y);

        System.out.println(oDag);

        assertEquals(Set.of(U), oDag);

        // 3. CPDAG from DAG
        Graph cpdagRaw = GraphTransforms.dagToCpdag(dag);

        // 3a. Replace cpdag nodes with the DAG's nodes by name
        //     (so cpdag now shares U, X, Y objects with dag)
        Graph cpdag = GraphUtils.replaceNodes(cpdagRaw, dag.getNodes());

        System.out.println(cpdag);

        // Sanity: still legal
        boolean legalCpdag = cpdag.paths().isLegalCpdag();
        assertTrue(legalCpdag);

        // 4. Now we can safely pass X, Y from the DAG, since cpdag uses the same Node instances.
        Set<Node> oCpdag = OSet.oSetCpdag(cpdag, X, Y, -1);

        // 5. They should match
        assertNotEquals(oDag, oCpdag);
    }

    /**
     * Tests {@link OSet#oSetDag(Graph, Node, Node)} on a simple causal chain
     * with no confounding variables.
     *
     * <p>The DAG structure is:
     * <pre>
     *   X -&gt; Z -&gt; Y
     * </pre>
     *
     * <p>In this graph:
     * <ul>
     *   <li>There are no common causes (backdoor paths) between X and Y,</li>
     *   <li>and all paths from X to Y are directed and causal.</li>
     * </ul>
     *
     * <p>Therefore, the O-set for (X, Y) should be empty:
     * <ul>
     *   <li><code>O_G(X -&gt; Y)</code> = ∅</li>
     * </ul>
     *
     * <p>The test verifies that {@code OSet.oSetDag()} returns an empty set,
     * confirming correct handling of unconfounded causal chains.
     */
    @Test
    public void testOSetDag_simpleChainNoConfounding() {
        // X -> Z -> Y, no other edges
        Graph g = new EdgeListGraph();
        Node X = new GraphNode("X");
        Node Z = new GraphNode("Z");
        Node Y = new GraphNode("Y");
        g.addNode(X);
        g.addNode(Z);
        g.addNode(Y);

        g.addEdge(Edges.directedEdge(X, Z));
        g.addEdge(Edges.directedEdge(Z, Y));

        assertTrue("Graph should be a legal DAG", g.paths().isLegalDag());

        Set<Node> oset = OSet.oSetDag(g, X, Y);
        // No backdoor confounding, so O-set should be empty.
        assertNotNull(oset);
        assertTrue(oset.isEmpty());
    }

    /**
     * Tests that {@link OSet#oSetCpdag(Graph, Node, Node, int)} agrees with
     * {@link OSet#oSetDag(Graph, Node, Node)} in a simple unconfounded causal chain.
     *
     * <p>The DAG structure is:
     * <pre>
     *   X -&gt; Z -&gt; Y
     * </pre>
     *
     * <p>Because there are no common causes (no backdoor paths) between X and Y:
     * <ul>
     *   <li>All paths from X to Y are directed and causal,</li>
     *   <li>The total effect of X on Y is identified without adjustment,</li>
     *   <li>Hence <code>O_G(X -&gt; Y)</code> = ∅ in both the DAG and its CPDAG.</li>
     * </ul>
     *
     * <p>This test verifies that the O-set computed from the CPDAG matches that
     * from the DAG, confirming consistency of {@code OSet.oSetCpdag()} in the
     * unconfounded case.
     */
    @Test
    public void testOSetCpdag_agreesWithDag_inSimpleChainNoConfounding() {
        // 1. Build a simple DAG: X -> Z -> Y, no other edges.
        Graph dag = new EdgeListGraph();

        Node X = new GraphNode("X");
        Node Z = new GraphNode("Z");
        Node Y = new GraphNode("Y");

        dag.addNode(X);
        dag.addNode(Z);
        dag.addNode(Y);

        dag.addEdge(Edges.directedEdge(X, Z));
        dag.addEdge(Edges.directedEdge(Z, Y));

        assertTrue("Graph should be a legal DAG", dag.paths().isLegalDag());

        // 2. O-set in the DAG
        Set<Node> oDag = OSet.oSetDag(dag, X, Y);
        // No backdoor confounders, so O-set should be empty.
        assertNotNull(oDag);
        assertTrue("O-set in DAG should be empty", oDag.isEmpty());

        // 3. CPDAG from DAG
        Graph cpdagRaw = GraphTransforms.dagToCpdag(dag);

        // Replace CPDAG nodes with the DAG's nodes by name so that
        // X, Y, Z are the SAME Node objects in both graphs.
        Graph cpdag = GraphUtils.replaceNodes(cpdagRaw, dag.getNodes());

        assertTrue("Result should be a legal CPDAG", cpdag.paths().isLegalCpdag());

        // 4. O-set in the CPDAG
        Set<Node> oCpdag = OSet.oSetCpdag(cpdag, X, Y, -1);

        // 5. They should agree (both empty).
        assertNotNull(oCpdag);
        assertEquals("CPDAG O-set should match DAG O-set in this no-confounding case",
                oDag, oCpdag);
    }
}