package edu.cmu.tetrad.test;

import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.GraphSampling;
import edu.pitt.dbmi.algo.resampling.ResamplingEdgeEnsemble;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * {@link GraphUtils#detachNodes} is applied to every search result the interface shows, and again when a Graph box
 * takes its graph from another box. It used to drop the ancillary graphs, so a bootstrap result arrived without its
 * sampling graph and the Ensemble Display menu had nothing to work from.
 *
 * @author Claude
 */
public class TestDetachNodesKeepsSamplingGraph {

    private final Node x = new GraphNode("X");
    private final Node y = new GraphNode("Y");
    private final Node z = new GraphNode("Z");

    /**
     * Ten member graphs. X, Y is X --> Y in the first member and Y --> X in the other nine, so the sampling graph
     * lists the pair as (X, Y) with its most frequent type pointing left: the case in which an edge must not be
     * flipped without also reversing its frequencies. Y --> Z is in all ten.
     */
    private List<Graph> members() {
        List<Graph> members = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            Graph g = new EdgeListGraph(List.of(x, y, z));
            g.addEdge(i == 0 ? Edges.directedEdge(x, y) : Edges.directedEdge(y, x));
            g.addEdge(Edges.directedEdge(y, z));
            members.add(g);
        }

        return members;
    }

    /**
     * A bootstrap-style result: a member graph carrying the sampling graph, which is passed through fixDirections
     * before it is stored, as a bootstrap search does.
     */
    private EdgeListGraph result() {
        List<Graph> members = members();
        EdgeListGraph result = new EdgeListGraph(members.get(1));
        result.setAncillaryGraph("samplingGraph",
                GraphUtils.fixDirections(GraphSampling.createGraphWithHighProbabilityEdges(members)));
        return result;
    }

    private static void assertYCausesXCausesNothingElse(Graph sampling) {
        // Y --> X in nine of ten, Y --> Z in all ten. Frequencies read against the wrong ends would give X --> Y.
        for (Graph composite : List.of(
                GraphSampling.createEndpointwiseDisplayGraph(sampling, 0.5, 0.9),
                GraphSampling.createDisplayGraph(sampling, ResamplingEdgeEnsemble.Highest))) {
            assertEquals(Endpoint.TAIL, markAt(composite, "Y", "X"));
            assertEquals(Endpoint.ARROW, markAt(composite, "X", "Y"));
            assertEquals(Endpoint.TAIL, markAt(composite, "Y", "Z"));
            assertEquals(Endpoint.ARROW, markAt(composite, "Z", "Y"));
        }
    }

    private static Endpoint markAt(Graph g, String at, String other) {
        return g.getEndpoint(g.getNode(other), g.getNode(at));
    }

    @Test
    public void theSamplingGraphComesAlongOnTheSameNodeCopies() {
        EdgeListGraph result = result();
        Graph detached = GraphUtils.detachNodes(result);
        Graph sampling = ((EdgeListGraph) detached).getAncillaryGraph("samplingGraph");

        assertNotNull("detachNodes dropped the sampling graph", sampling);

        for (Node node : detached.getNodes()) {
            // Its own node objects, not the original's, and the same ones in the sampling graph, so that switching
            // the display to a composite keeps the box's layout and does not reattach it to the shared nodes.
            assertNotSame(result.getNode(node.getName()), node);
            assertSame(node, sampling.getNode(node.getName()));
        }

        assertYCausesXCausesNothingElse(sampling);
    }

    @Test
    public void frequenciesStayOnTheRightEnds() {
        // A sampling graph as first built, before fixDirections: the pair is stored pointing left.
        Graph fresh = GraphSampling.createGraphWithHighProbabilityEdges(members());
        Edge stored = fresh.getEdge(fresh.getNode("X"), fresh.getNode("Y"));
        assertEquals("X", stored.getNode1().getName());
        assertEquals(Endpoint.ARROW, stored.getEndpoint1());
        assertEquals(Endpoint.TAIL, stored.getEndpoint2());

        assertYCausesXCausesNothingElse(fresh);
        assertYCausesXCausesNothingElse(GraphUtils.detachNodes(fresh));
    }

    @Test
    public void aGraphBoxCopyOfASearchBoxCopyStillHasIt() {
        // The interface's path: the Search box detaches the result, and a Graph box detaches it again.
        Graph searchBox = GraphUtils.detachNodes(result());
        EdgeListGraph graphBox = new EdgeListGraph(searchBox, true);

        Graph sampling = graphBox.getAncillaryGraph("samplingGraph");
        assertNotNull(sampling);
        assertSame(graphBox.getNode("X"), sampling.getNode("X"));
        assertNotSame(searchBox.getNode("X"), graphBox.getNode("X"));
    }

    @Test
    public void aGraphWithNoAncillaryGraphsIsDetachedAsBefore() {
        Graph g = new EdgeListGraph(List.of(x, y, z));
        g.addEdge(Edges.directedEdge(x, y));
        g.addEdge(Edges.partiallyOrientedEdge(z, y));

        EdgeListGraph detached = (EdgeListGraph) GraphUtils.detachNodes(g);
        assertTrue(detached.getAncillaryGraphNames().isEmpty());
        assertEquals(2, detached.getNumEdges());
        assertEquals(Endpoint.ARROW, markAt(detached, "Y", "X"));
        assertEquals(Endpoint.TAIL, markAt(detached, "X", "Y"));
        assertEquals(Endpoint.CIRCLE, markAt(detached, "Z", "Y"));
    }
}
