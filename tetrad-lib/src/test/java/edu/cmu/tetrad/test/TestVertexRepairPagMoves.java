package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.ConditioningSetType;
import edu.cmu.tetrad.search.VertexRepairSearch;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Regression tests for the PAG move set of {@link VertexRepairSearch}, from the
 * 2026-9-9 review.
 *
 * <p>Three repairs are pinned jointly. (1) The incident-orientation pattern moves are
 * enumerated for PAGs: under PAG canonicalization a single-edge move installing one new
 * arrowhead at x is erased unless that arrowhead is class-forced on its own, so a new
 * unshielded collider y *-> x <-* z is reachable only through the joint move. (2) The
 * final comparator tie-break ranks move types (removals, then reorientations, then
 * additions) before the lexical key: the global queue is built without Model-P, so
 * equal-violation candidates tie through every substantive tier, and the old lexical
 * break polled "ADD:" keys first, densifying instead of orienting. (3) The candidate
 * path certifies PAGs with the knowledge-aware legality predicate: a knowledge-refined
 * PAG always fails the strict {@code isLegalPag} round trip, so with knowledge set the
 * strict check silently dropped every refined candidate.
 *
 * <p>Both tests fail on the pre-review build (repair densifies or freezes and the
 * collider is never installed) and pass on the patched one.
 */
public class TestVertexRepairPagMoves {

    private static final long DATA_SEED = 77123L;
    private static final long SEARCH_SEED = 29L;
    private static final int SAMPLE_SIZE = 2000;
    private static final double ALPHA = 0.01;

    /**
     * True DAG y -> x <- z, x -> w. Start PAG: the all-circles star y o-o x o-o z,
     * x o-o w, whose class wrongly implies y _||_ z | x. The repaired graph must carry
     * the collider at x and must NOT have densified (no y-z adjacency), which pins both
     * the PAG pattern moves and the parsimony-leaning tie-break.
     */
    @Test
    public void testNewColliderReachableInPag() throws Exception {
        Problem prob = makeProblem();

        VertexRepairSearch search = new VertexRepairSearch(prob.start(),
                new IndTestFisherZ(prob.data(), ALPHA),
                ConditioningSetType.ORDERED_LOCAL_MARKOV_PROPERTY);
        search.setGraphType(VertexRepairSearch.AdjustmentGraphType.PAG);
        search.setRepairStrategy(VertexRepairSearch.RepairStrategy.GLOBAL_QUEUE);
        search.setSeed(SEARCH_SEED);

        Graph out = search.search();
        assertCollider(out);
        assertParsimony(out);
    }

    /**
     * Same problem with the edge y -> x required by knowledge. Refinement then sharpens
     * the class-canonical y o-> x to y --> x on every collider candidate -- a mark the
     * class alone does not force, which the strict round-trip certificate always
     * rejects. On the patched build the knowledge-aware certificate accepts the refined
     * candidates, and the output carries both the collider and the required tail at y.
     */
    @Test
    public void testKnowledgeRefinedCandidatesSurviveCertification() throws Exception {
        Problem prob = makeProblem();

        Knowledge knowledge = new Knowledge();
        knowledge.setRequired("y", "x");

        VertexRepairSearch search = new VertexRepairSearch(prob.start(),
                new IndTestFisherZ(prob.data(), ALPHA),
                ConditioningSetType.ORDERED_LOCAL_MARKOV_PROPERTY);
        search.setGraphType(VertexRepairSearch.AdjustmentGraphType.PAG);
        search.setRepairStrategy(VertexRepairSearch.RepairStrategy.GLOBAL_QUEUE);
        search.setKnowledge(knowledge);
        search.setSeed(SEARCH_SEED);

        Graph out = search.search();
        assertCollider(out);
        assertParsimony(out);
        assertEquals("Required edge y -> x: tail at y after knowledge refinement",
                Endpoint.TAIL, out.getEdge(out.getNode("y"), out.getNode("x"))
                        .getProximalEndpoint(out.getNode("y")));
    }

    private record Problem(Graph start, DataSet data) {
    }

    private Problem makeProblem() throws Exception {
        RandomUtil.getInstance().setSeed(DATA_SEED);

        Node y = new GraphNode("y"), x = new GraphNode("x"),
                z = new GraphNode("z"), w = new GraphNode("w");
        Graph trueDag = new EdgeListGraph();
        trueDag.addNode(y);
        trueDag.addNode(x);
        trueDag.addNode(z);
        trueDag.addNode(w);
        trueDag.addDirectedEdge(y, x);
        trueDag.addDirectedEdge(z, x);
        trueDag.addDirectedEdge(x, w);

        SemPm pm = new SemPm(trueDag);
        SemIm im = new SemIm(pm);
        im.setEdgeCoef(trueDag.getNode("y"), trueDag.getNode("x"), 0.8);
        im.setEdgeCoef(trueDag.getNode("z"), trueDag.getNode("x"), 0.8);
        im.setEdgeCoef(trueDag.getNode("x"), trueDag.getNode("w"), 0.8);
        DataSet data = im.simulateData(SAMPLE_SIZE, false);

        Graph start = new EdgeListGraph();
        for (String name : new String[]{"y", "x", "z", "w"}) start.addNode(new GraphNode(name));
        start.addNondirectedEdge(start.getNode("y"), start.getNode("x"));
        start.addNondirectedEdge(start.getNode("z"), start.getNode("x"));
        start.addNondirectedEdge(start.getNode("x"), start.getNode("w"));

        return new Problem(start, data);
    }

    private void assertCollider(Graph out) {
        Node y = out.getNode("y"), x = out.getNode("x"), z = out.getNode("z");
        assertTrue("y and x should remain adjacent", out.isAdjacentTo(y, x));
        assertTrue("z and x should remain adjacent", out.isAdjacentTo(z, x));
        assertEquals("Arrowhead at x on the y-x edge (collider installed)",
                Endpoint.ARROW, out.getEdge(y, x).getProximalEndpoint(x));
        assertEquals("Arrowhead at x on the z-x edge (collider installed)",
                Endpoint.ARROW, out.getEdge(z, x).getProximalEndpoint(x));
    }

    private void assertParsimony(Graph out) {
        Node y = out.getNode("y"), z = out.getNode("z"), w = out.getNode("w");
        assertFalse("Repair should orient, not densify: no y-z edge",
                out.isAdjacentTo(y, z));
        assertFalse("Repair should orient, not densify: no y-w edge",
                out.isAdjacentTo(y, w));
        assertFalse("Repair should orient, not densify: no z-w edge",
                out.isAdjacentTo(z, w));
        assertEquals("Exactly the three true adjacencies", 3, out.getNumEdges());
    }
}
