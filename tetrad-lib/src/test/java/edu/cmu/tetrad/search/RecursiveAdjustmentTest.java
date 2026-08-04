package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.Node;
import org.junit.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Tests for RecursiveAdjustment against the generalized adjustment criterion
 * (Perkovic, Textor, Kalisch, Maathuis, JMLR 18(220), 2018).
 *
 * These tests assume the correctness fixes discussed against that paper:
 * (1) backdoor starts = any arrowhead at X (covers W &lt;-&gt; X, W o-&gt; X);
 * (2) SUPPRESS returns null, so non-amenable pairs emit no sets;
 * (3) X and Y are removed from the blocker pool;
 * (4) seed ("containing") nodes are screened against Forb;
 * (5) MAG amenability is routed through the visibility-checking PAG method,
 *     and getAmenablePathsPag with no forcing requires a strictly visible
 *     first edge (Def. 2).
 *
 * Note: tests expecting no output for non-amenable pairs assume the
 * no-amenable policy is SUPPRESS. If the default differs, set it explicitly
 * in sets() below (e.g. ra.setNoAmenablePolicy(...)).
 */
public class RecursiveAdjustmentTest {

    private static List<Set<Node>> sets(Graph g, Node x, Node y, String graphType, Set<Node> containing) {
        return new RecursiveAdjustment(g)
                .adjustmentSets(x, y, graphType, 4, -1, 3, -1,
                        RecursiveAdjustment.ColliderPolicy.NONCOLLIDER_FIRST,
                        false, null, containing, Collections.emptySet());
    }

    private static List<Set<Node>> sets(Graph g, Node x, Node y, String graphType) {
        return sets(g, x, y, graphType, Collections.emptySet());
    }

    // ---------------------------------------------------------------------
    // MAGs
    // ---------------------------------------------------------------------

    /**
     * X &lt;-&gt; W -&gt; Y confounding path with X -&gt; Y made VISIBLE by V -&gt; X
     * (V not adjacent to Y). Amenable per Def. 2; {W} is the unique minimal
     * GAC set. Exercises fix (1): pre-fix, X &lt;-&gt; W is never a backdoor
     * start and {} is returned as "valid".
     *
     * NB: the earlier version of this test omitted V. Without V the edge
     * X -&gt; Y is invisible (its only arrowhead-witness W is adjacent to Y),
     * the MAG is NOT amenable, and per Lemma 8 no adjustment set exists --
     * so the old expectation [{W}] was itself wrong. See magInvisibleFirstEdge.
     */
    @Test
    public void bidirectedBackdoorIsWitnessed() {
        Node x = new GraphNode("X"), y = new GraphNode("Y"),
                w = new GraphNode("W"), v = new GraphNode("V");
        Graph mag = new EdgeListGraph(List.of(x, y, w, v));
        mag.addDirectedEdge(x, y);
        mag.addBidirectedEdge(x, w);
        mag.addDirectedEdge(w, y);
        mag.addDirectedEdge(v, x);      // visibility witness for X -> Y

        List<Set<Node>> out = sets(mag, x, y, "MAG");

        assertEquals(List.of(Set.of(w)), out);
    }

    /**
     * Same MAG without the visibility witness: X -&gt; Y is invisible, so the
     * MAG is non-amenable (Def. 2; paper Example 3, MAG M1 is the analogous
     * case) and there is no adjustment set (Lemma 8). Exercises fixes (2)
     * and (5). Pre-fix this graph yielded [{W}] or [{}].
     */
    @Test
    public void magInvisibleFirstEdgeIsNotAmenable() {
        Node x = new GraphNode("X"), y = new GraphNode("Y"), w = new GraphNode("W");
        Graph mag = new EdgeListGraph(List.of(x, y, w));
        mag.addDirectedEdge(x, y);
        mag.addBidirectedEdge(x, w);
        mag.addDirectedEdge(w, y);

        assertTrue(sets(mag, x, y, "MAG").isEmpty());
    }

    /** Two-node MAG X -&gt; Y: no possible visibility witness, non-amenable, no sets. */
    @Test
    public void magTwoNodeInvisibleEdge() {
        Node x = new GraphNode("X"), y = new GraphNode("Y");
        Graph mag = new EdgeListGraph(List.of(x, y));
        mag.addDirectedEdge(x, y);

        assertTrue(sets(mag, x, y, "MAG").isEmpty());
    }

    // ---------------------------------------------------------------------
    // DAGs
    // ---------------------------------------------------------------------

    /** Classic confounder: X &lt;- C -&gt; Y, X -&gt; Y. Unique minimal set {C}. */
    @Test
    public void dagSimpleConfounder() {
        Node x = new GraphNode("X"), y = new GraphNode("Y"), c = new GraphNode("C");
        Graph dag = new EdgeListGraph(List.of(x, y, c));
        dag.addDirectedEdge(x, y);
        dag.addDirectedEdge(c, x);
        dag.addDirectedEdge(c, y);

        assertEquals(List.of(Set.of(c)), sets(dag, x, y, "DAG"));
    }

    /**
     * Pure mediation X -&gt; M -&gt; Y: no backdoor paths, {} is the valid set,
     * and the mediator (in Forb) must not appear anywhere.
     */
    @Test
    public void dagMediatorYieldsEmptySetOnly() {
        Node x = new GraphNode("X"), y = new GraphNode("Y"), m = new GraphNode("M");
        Graph dag = new EdgeListGraph(List.of(x, y, m));
        dag.addDirectedEdge(x, m);
        dag.addDirectedEdge(m, y);

        List<Set<Node>> out = sets(dag, x, y, "DAG");

        assertEquals(1, out.size());
        assertTrue(out.get(0).isEmpty());
    }

    /**
     * M-bias graph (paper Sec. 1): A -&gt; X, A -&gt; M &lt;- B, B -&gt; Y, X -&gt; Y.
     * The backdoor path X &lt;- A -&gt; M &lt;- B -&gt; Y is blocked by {} at the
     * collider M, so {} is valid and M must not be conditioned on.
     * Exercises the collider-closed branch of tripleKeepsOpen.
     */
    @Test
    public void dagMBiasEmptySetSuffices() {
        Node x = new GraphNode("X"), y = new GraphNode("Y"),
                a = new GraphNode("A"), m = new GraphNode("M"), b = new GraphNode("B");
        Graph dag = new EdgeListGraph(List.of(x, y, a, m, b));
        dag.addDirectedEdge(x, y);
        dag.addDirectedEdge(a, x);
        dag.addDirectedEdge(a, m);
        dag.addDirectedEdge(b, m);
        dag.addDirectedEdge(b, y);

        List<Set<Node>> out = sets(dag, x, y, "DAG");

        assertEquals(1, out.size());
        assertTrue(out.get(0).isEmpty());
    }

    /**
     * M-bias with M forced into Z via "containing": conditioning on the
     * collider opens X &lt;- A -&gt; M &lt;- B -&gt; Y, which must then be re-blocked
     * by A or B. Every returned set must contain M plus at least one
     * noncollider repair.
     */
    @Test
    public void dagMBiasForcedColliderIsRepaired() {
        Node x = new GraphNode("X"), y = new GraphNode("Y"),
                a = new GraphNode("A"), m = new GraphNode("M"), b = new GraphNode("B");
        Graph dag = new EdgeListGraph(List.of(x, y, a, m, b));
        dag.addDirectedEdge(x, y);
        dag.addDirectedEdge(a, x);
        dag.addDirectedEdge(a, m);
        dag.addDirectedEdge(b, m);
        dag.addDirectedEdge(b, y);

        List<Set<Node>> out = sets(dag, x, y, "DAG", Set.of(m));

        assertFalse(out.isEmpty());
        for (Set<Node> z : out) {
            assertTrue("forced node missing: " + z, z.contains(m));
            assertTrue("collider not repaired: " + z, z.contains(a) || z.contains(b));
        }
    }

    /**
     * Forbidden-set discipline: C -&gt; X, C -&gt; M, X -&gt; M -&gt; Y. The witness
     * X &lt;- C -&gt; M -&gt; Y carries two noncolliders, but M lies on the causal
     * path (M in Forb), so only {C} is legal.
     */
    @Test
    public void dagForbiddenMediatorIsNeverPicked() {
        Node x = new GraphNode("X"), y = new GraphNode("Y"),
                c = new GraphNode("C"), m = new GraphNode("M");
        Graph dag = new EdgeListGraph(List.of(x, y, c, m));
        dag.addDirectedEdge(x, m);
        dag.addDirectedEdge(m, y);
        dag.addDirectedEdge(c, x);
        dag.addDirectedEdge(c, m);

        List<Set<Node>> out = sets(dag, x, y, "DAG");

        assertEquals(List.of(Set.of(c)), out);
    }

    /**
     * Fix (4): a forbidden node passed via "containing" must be dropped,
     * not smuggled into every output set.
     */
    @Test
    public void dagForbiddenSeedIsDropped() {
        Node x = new GraphNode("X"), y = new GraphNode("Y"),
                c = new GraphNode("C"), m = new GraphNode("M");
        Graph dag = new EdgeListGraph(List.of(x, y, c, m));
        dag.addDirectedEdge(x, m);
        dag.addDirectedEdge(m, y);
        dag.addDirectedEdge(c, x);
        dag.addDirectedEdge(c, m);

        List<Set<Node>> out = sets(dag, x, y, "DAG", Set.of(m));

        assertFalse(out.isEmpty());
        for (Set<Node> z : out) assertFalse("Forb violated: " + z, z.contains(m));
    }

    /**
     * Ban-driven enumeration: A -&gt; X, A -&gt; B -&gt; Y, X -&gt; Y. The path
     * X &lt;- A -&gt; B -&gt; Y is blockable by A or by B, and both singletons
     * should be enumerated.
     */
    @Test
    public void dagEnumeratesAlternativeBlockers() {
        Node x = new GraphNode("X"), y = new GraphNode("Y"),
                a = new GraphNode("A"), b = new GraphNode("B");
        Graph dag = new EdgeListGraph(List.of(x, y, a, b));
        dag.addDirectedEdge(x, y);
        dag.addDirectedEdge(a, x);
        dag.addDirectedEdge(a, b);
        dag.addDirectedEdge(b, y);

        List<Set<Node>> out = sets(dag, x, y, "DAG");

        assertEquals(Set.of(Set.of(a), Set.of(b)), new HashSet<>(out));
    }

    /** Determinism: repeated runs return identical results in identical order. */
    @Test
    public void resultsAreDeterministic() {
        Node x = new GraphNode("X"), y = new GraphNode("Y"),
                a = new GraphNode("A"), b = new GraphNode("B");
        Graph dag = new EdgeListGraph(List.of(x, y, a, b));
        dag.addDirectedEdge(x, y);
        dag.addDirectedEdge(a, x);
        dag.addDirectedEdge(a, b);
        dag.addDirectedEdge(b, y);

        List<Set<Node>> first = sets(dag, x, y, "DAG");
        for (int i = 0; i < 3; i++) assertEquals(first, sets(dag, x, y, "DAG"));
    }

    // ---------------------------------------------------------------------
    // PAGs (paper Example 4, trimmed: V1 kept as visibility witness for X -> V4)
    // ---------------------------------------------------------------------

    /**
     * Paper Example 4, P1 (trimmed): V3 o-&gt; X, V3 -&gt; Y, X -&gt; V4,
     * V3 -&gt; V4, V4 o-&gt; Y, plus V1 o-&gt; X (V1 not adjacent to V4, making
     * X -&gt; V4 visible, hence amenable). The paper's minimal GAC set is {V3}:
     * it blocks X &lt;-o V3 -&gt; Y directly and leaves the collider V4 closed on
     * X -&gt; V4 &lt;- V3 -&gt; Y. Exercises fix (1) for o-&gt; starts.
     */
    @Test
    public void pagExample4P1FindsV3() {
        Node x = new GraphNode("X"), y = new GraphNode("Y"), v1 = new GraphNode("V1"),
                v3 = new GraphNode("V3"), v4 = new GraphNode("V4");
        Graph pag = new EdgeListGraph(List.of(x, y, v1, v3, v4));
        pag.addEdge(Edges.partiallyOrientedEdge(v3, x));   // V3 o-> X
        pag.addDirectedEdge(v3, y);
        pag.addDirectedEdge(x, v4);
        pag.addDirectedEdge(v3, v4);
        pag.addEdge(Edges.partiallyOrientedEdge(v4, y));   // V4 o-> Y
        pag.addEdge(Edges.partiallyOrientedEdge(v1, x));   // visibility witness

        assertEquals(List.of(Set.of(v3)), sets(pag, x, y, "PAG"));
    }

    /**
     * Paper Example 4, P2 (trimmed): X &lt;-&gt; V3, V3 -&gt; Y, X -&gt; V4,
     * V3 &lt;-&gt; V4, V4 -&gt; Y, plus V1 o-&gt; X for visibility of X -&gt; V4.
     * Blocking X &lt;-&gt; V3 -&gt; Y requires V3, which opens the collider V3 on
     * X &lt;-&gt; V3 &lt;-&gt; V4 -&gt; Y; the only repair V4 is in Forb. Per the
     * paper, no set satisfies the GAC. Exercises fix (1) for &lt;-&gt; starts,
     * collider-in-Z opening, and Forb exclusion together.
     */
    @Test
    public void pagExample4P2HasNoAdjustmentSet() {
        Node x = new GraphNode("X"), y = new GraphNode("Y"), v1 = new GraphNode("V1"),
                v3 = new GraphNode("V3"), v4 = new GraphNode("V4");
        Graph pag = new EdgeListGraph(List.of(x, y, v1, v3, v4));
        pag.addBidirectedEdge(x, v3);
        pag.addDirectedEdge(v3, y);
        pag.addDirectedEdge(x, v4);
        pag.addBidirectedEdge(v3, v4);
        pag.addDirectedEdge(v4, y);
        pag.addEdge(Edges.partiallyOrientedEdge(v1, x));

        assertTrue(sets(pag, x, y, "PAG").isEmpty());
    }

    /**
     * Paper Example 3, PAG P analogue: X o-o Y is a possibly directed path
     * with no visible first edge, so the PAG is non-amenable and nothing is
     * emitted. Exercises fix (5): pre-fix, PAG amenability was vacuous.
     */
    @Test
    public void pagNondirectedEdgeIsNotAmenable() {
        Node x = new GraphNode("X"), y = new GraphNode("Y");
        Graph pag = new EdgeListGraph(List.of(x, y));
        pag.addEdge(Edges.nondirectedEdge(x, y));

        assertTrue(sets(pag, x, y, "PAG").isEmpty());
    }
}
