package edu.cmu.tetrad.test;

import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.GraphSampling;
import edu.pitt.dbmi.algo.resampling.ResamplingEdgeEnsemble;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests the Endpointwise bootstrap composite, {@link GraphSampling#createEndpointwiseDisplayGraph}: the adjacency
 * and each endpoint mark are decided separately from the edge-type frequencies.
 *
 * @author Claude
 */
public class TestEndpointwiseEnsemble {

    private final Node x = new GraphNode("X");
    private final Node y = new GraphNode("Y");
    private final Node z = new GraphNode("Z");
    private final Node w = new GraphNode("W");

    /**
     * Ten member graphs.
     * <ul>
     * <li>X, Y: X --> Y in nine, X o-> Y in one. Arrowhead at Y in all ten, tail at X in nine.</li>
     * <li>Y, Z: Y <-> Z in six, Y --> Z in four. Arrowhead at Z in all ten; at Y an arrowhead in six, a tail in
     * four.</li>
     * <li>Z, W: Z --> W in four, not adjacent in six.</li>
     * <li>W, X: W --> X in eight, X --> W in two, so the pair's frequencies hold the same edge type in both
     * directions and the marks must be tallied against the right ends. Arrowhead at X and tail at W in eight.</li>
     * </ul>
     */
    private Graph samplingGraph() {
        List<Graph> members = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            Graph g = new EdgeListGraph(List.of(x, y, z, w));
            g.addEdge(i < 9 ? Edges.directedEdge(x, y) : Edges.partiallyOrientedEdge(x, y));
            g.addEdge(i < 6 ? Edges.bidirectedEdge(y, z) : Edges.directedEdge(y, z));
            if (i < 4) g.addEdge(Edges.directedEdge(z, w));
            g.addEdge(i < 8 ? Edges.directedEdge(w, x) : Edges.directedEdge(x, w));
            members.add(g);
        }

        return GraphSampling.createGraphWithHighProbabilityEdges(members);
    }

    private static Endpoint markAt(Graph g, String at, String other) {
        return g.getEndpoint(g.getNode(other), g.getNode(at));
    }

    @Test
    public void marksAreDecidedEndpointByEndpoint() {
        Graph g = GraphSampling.createEndpointwiseDisplayGraph(samplingGraph(), 0.5, 0.9);

        // Tail at X in exactly nine of ten: kept at 0.9, the threshold being inclusive.
        assertTrue(g.isAdjacentTo(g.getNode("X"), g.getNode("Y")));
        assertEquals(Endpoint.TAIL, markAt(g, "X", "Y"));
        assertEquals(Endpoint.ARROW, markAt(g, "Y", "X"));

        // The joint type is split six to four, but the arrowhead at Z is in all ten: the edge stays, the
        // arrowhead at Z stays, and only the unsettled mark at Y becomes a circle.
        assertTrue(g.isAdjacentTo(g.getNode("Y"), g.getNode("Z")));
        assertEquals(Endpoint.CIRCLE, markAt(g, "Y", "Z"));
        assertEquals(Endpoint.ARROW, markAt(g, "Z", "Y"));

        // Adjacent in four of ten: dropped at 0.5.
        assertFalse(g.isAdjacentTo(g.getNode("Z"), g.getNode("W")));

        // Directed one way in eight and the other way in two: adjacent in all ten, neither mark in nine. Tallying
        // the reversed type against the wrong ends would put an arrowhead at X in all ten.
        assertTrue(g.isAdjacentTo(g.getNode("W"), g.getNode("X")));
        assertEquals(Endpoint.CIRCLE, markAt(g, "W", "X"));
        assertEquals(Endpoint.CIRCLE, markAt(g, "X", "W"));
    }

    @Test
    public void thresholdsMoveTheDecisions() {
        // A mark threshold above nine tenths withholds the tail at X.
        Graph strict = GraphSampling.createEndpointwiseDisplayGraph(samplingGraph(), 0.5, 0.95);
        assertEquals(Endpoint.CIRCLE, markAt(strict, "X", "Y"));
        assertEquals(Endpoint.ARROW, markAt(strict, "Y", "X"));

        // A mark threshold of eight tenths settles W --> X.
        Graph eight = GraphSampling.createEndpointwiseDisplayGraph(samplingGraph(), 0.5, 0.8);
        assertEquals(Endpoint.TAIL, markAt(eight, "W", "X"));
        assertEquals(Endpoint.ARROW, markAt(eight, "X", "W"));

        // An adjacency threshold of four tenths keeps Z, W, but its marks, each in four of ten, are circles:
        // resamples lacking the edge count against a mark.
        Graph loose = GraphSampling.createEndpointwiseDisplayGraph(samplingGraph(), 0.4, 0.9);
        assertTrue(loose.isAdjacentTo(loose.getNode("Z"), loose.getNode("W")));
        assertEquals(Endpoint.CIRCLE, markAt(loose, "Z", "W"));
        assertEquals(Endpoint.CIRCLE, markAt(loose, "W", "Z"));
    }

    @Test
    public void differsFromTheJointTypeVote() {
        // Majority votes on the whole edge type, so it shows Y <-> Z, arrowhead at Y included, on six votes of ten.
        Graph majority = GraphSampling.createDisplayGraph(samplingGraph(), ResamplingEdgeEnsemble.Majority);
        assertEquals(Endpoint.ARROW, markAt(majority, "Y", "Z"));

        Graph endpointwise = GraphSampling.createEndpointwiseDisplayGraph(samplingGraph(), 0.5, 0.9);
        assertEquals(Endpoint.CIRCLE, markAt(endpointwise, "Y", "Z"));
    }

    @Test
    public void frequenciesSurviveAndStayAlignedThroughFixDirections() {
        Graph g = GraphUtils.fixDirections(GraphSampling.createEndpointwiseDisplayGraph(samplingGraph(), 0.5, 0.8));

        assertEquals(Endpoint.TAIL, markAt(g, "W", "X"));
        assertEquals(Endpoint.ARROW, markAt(g, "X", "W"));
        assertEquals(Endpoint.CIRCLE, markAt(g, "Y", "Z"));
        assertEquals(Endpoint.ARROW, markAt(g, "Z", "Y"));

        Edge yz = g.getEdge(g.getNode("Y"), g.getNode("Z"));
        assertEquals(2, yz.getEdgeTypeProbabilities().size());
        assertEquals(1.0, yz.getProbability(), 1e-12);

        // Each frequency is listed relative to the edge's own node order: the type with an arrowhead at both
        // ends has frequency 0.6, and the other, 0.4, has its arrowhead at Z.
        for (EdgeTypeProbability etp : yz.getEdgeTypeProbabilities()) {
            if (etp.getEdgeType() == EdgeTypeProbability.EdgeType.aa) {
                assertEquals(0.6, etp.getProbability(), 1e-12);
            } else {
                assertEquals(0.4, etp.getProbability(), 1e-12);
                boolean zIsNode2 = yz.getNode2().getName().equals("Z");
                assertEquals(zIsNode2 ? EdgeTypeProbability.EdgeType.ta : EdgeTypeProbability.EdgeType.at,
                        etp.getEdgeType());
            }
        }
    }

    @Test
    public void outOfRangeThresholdsAreRefused() {
        Graph sampling = samplingGraph();
        assertThrows(IllegalArgumentException.class,
                () -> GraphSampling.createEndpointwiseDisplayGraph(sampling, 0.5, 0.5));
        assertThrows(IllegalArgumentException.class,
                () -> GraphSampling.createEndpointwiseDisplayGraph(sampling, 0.5, 1.1));
        assertThrows(IllegalArgumentException.class,
                () -> GraphSampling.createEndpointwiseDisplayGraph(sampling, -0.1, 0.9));
        assertThrows(IllegalArgumentException.class,
                () -> GraphSampling.createEndpointwiseDisplayGraph(sampling, 1.5, 0.9));
    }
}
