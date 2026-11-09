package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.Node;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests the possible-collider predicate ICD uses to decide which subpaths extend a possible-D-SEP path.
 *
 * <p>The condition is the one in Spirtes, Glymour and Scheines (2000, p. 188), used by
 * {@code Paths.possibleDsep} and by ICD's reference implementation: a triple extends a PDS path just in
 * case it is a triangle, or its middle node is a definite collider on it. ICD previously used the
 * complement of {@code isDefNoncollider} instead, which is a different predicate; the two cases that
 * separate them are pinned below so the substitution cannot be made again silently.
 *
 * @author josephramsey
 */
public class TestIcdPossibleCollider {

    /**
     * Builds a-b-c with the given endpoint at b on each edge, optionally shielded by an a-c edge. The far
     * endpoints are left as circles throughout, since only the marks at b and the adjacency of a and c
     * enter the predicate.
     *
     * @param atBFromA  the endpoint at b on the a-b edge
     * @param atBFromC  the endpoint at b on the c-b edge
     * @param shielded  whether a and c are adjacent
     * @return the graph, whose nodes are named a, b and c
     */
    private static Graph triple(Endpoint atBFromA, Endpoint atBFromC, boolean shielded) {
        Node a = new GraphNode("a");
        Node b = new GraphNode("b");
        Node c = new GraphNode("c");

        Graph graph = new EdgeListGraph(Arrays.asList(a, b, c));

        graph.addEdge(new edu.cmu.tetrad.graph.Edge(a, b, Endpoint.CIRCLE, atBFromA));
        graph.addEdge(new edu.cmu.tetrad.graph.Edge(c, b, Endpoint.CIRCLE, atBFromC));

        if (shielded) {
            graph.addEdge(new edu.cmu.tetrad.graph.Edge(a, c, Endpoint.CIRCLE, Endpoint.CIRCLE));
        }

        return graph;
    }

    private static boolean predicate(Graph graph) {
        return Icd.isPossibleCollider(graph, graph.getNode("a"), graph.getNode("b"), graph.getNode("c"));
    }

    /**
     * A definite collider extends a PDS path whether or not the triple is shielded.
     */
    @Test
    public void testDefiniteColliderIsPossibleCollider() {
        assertTrue(predicate(triple(Endpoint.ARROW, Endpoint.ARROW, false)));
        assertTrue(predicate(triple(Endpoint.ARROW, Endpoint.ARROW, true)));
    }

    /**
     * Any shielded triple extends a PDS path, by the triangle clause, whatever the marks at the middle node.
     */
    @Test
    public void testShieldedTripleIsPossibleCollider() {
        assertTrue(predicate(triple(Endpoint.CIRCLE, Endpoint.CIRCLE, true)));
        assertTrue(predicate(triple(Endpoint.ARROW, Endpoint.CIRCLE, true)));
        assertTrue(predicate(triple(Endpoint.CIRCLE, Endpoint.ARROW, true)));
    }

    /**
     * The first case that separates this predicate from the complement of {@code isDefNoncollider}: a tail at
     * the middle node inside a triangle. The triangle clause makes it a possible collider, while a tail makes
     * it a definite noncollider, so the complement would exclude it and shrink the PDS set. ICD reaches
     * graphs with tails from its second round onward, since it applies R1 to R4 between rounds.
     */
    @Test
    public void testTailInTriangleIsStillPossibleCollider() {
        Graph graph = triple(Endpoint.TAIL, Endpoint.CIRCLE, true);

        assertTrue("a tail at the middle node of a triangle is a definite noncollider",
                graph.isDefNoncollider(graph.getNode("a"), graph.getNode("b"), graph.getNode("c")));
        assertTrue("but the triple is a triangle, so it extends a PDS path", predicate(graph));

        assertTrue(predicate(triple(Endpoint.CIRCLE, Endpoint.TAIL, true)));
        assertTrue(predicate(triple(Endpoint.TAIL, Endpoint.ARROW, true)));
    }

    /**
     * The second case that separates them: exactly one arrowhead at the middle node, with the ends not
     * adjacent. This is neither a triangle nor a definite collider, so it does not extend a PDS path, while
     * the complement of {@code isDefNoncollider} would admit it and enlarge the PDS set.
     */
    @Test
    public void testUnshieldedSingleArrowheadIsNotPossibleCollider() {
        Graph graph = triple(Endpoint.ARROW, Endpoint.CIRCLE, false);

        assertFalse("not a definite noncollider",
                graph.isDefNoncollider(graph.getNode("a"), graph.getNode("b"), graph.getNode("c")));
        assertFalse("and not a possible collider either", predicate(graph));

        assertFalse(predicate(triple(Endpoint.CIRCLE, Endpoint.ARROW, false)));
    }

    /**
     * An unshielded triple with circles at the middle node does not extend a PDS path. This is the case the
     * October 2026 correction to {@code isDefNoncollider} brought into line; it is kept here so that the two
     * predicates are pinned together where they do agree.
     */
    @Test
    public void testUnshieldedCirclesAreNotPossibleCollider() {
        assertFalse(predicate(triple(Endpoint.CIRCLE, Endpoint.CIRCLE, false)));
    }

    /**
     * A tail at the middle node of an unshielded triple does not extend a PDS path.
     */
    @Test
    public void testUnshieldedTailIsNotPossibleCollider() {
        assertFalse(predicate(triple(Endpoint.TAIL, Endpoint.CIRCLE, false)));
        assertFalse(predicate(triple(Endpoint.TAIL, Endpoint.ARROW, false)));
    }

    /**
     * Degenerate triples are rejected rather than throwing: a repeated endpoint, or a missing edge.
     */
    @Test
    public void testDegenerateTriples() {
        Graph graph = triple(Endpoint.ARROW, Endpoint.ARROW, false);
        Node a = graph.getNode("a");
        Node b = graph.getNode("b");

        assertFalse("a and c the same node", Icd.isPossibleCollider(graph, a, b, a));

        Node d = new GraphNode("d");
        graph.addNode(d);

        assertFalse("no edge from b to d", Icd.isPossibleCollider(graph, a, b, d));
    }
}
