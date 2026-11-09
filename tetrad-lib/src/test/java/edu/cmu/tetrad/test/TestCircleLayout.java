package edu.cmu.tetrad.test;

import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.LayoutUtil;
import edu.cmu.tetrad.graph.Node;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertTrue;

/**
 * Tests that the circle layout of a few nodes leaves room for an edge between any two of them, and that the circle
 * layout of many nodes is no larger for it.
 */
public class TestCircleLayout {

    /**
     * The length of the edge between two nodes from box to box, for the estimated node boxes (60 wide by 30 high for
     * these short names), along the line between their centers.
     */
    private static double edgeLength(Node a, Node b) {
        double dx = Math.abs(a.getCenterX() - b.getCenterX());
        double dy = Math.abs(a.getCenterY() - b.getCenterY());
        double d = Math.hypot(dx, dy);
        double inside = Math.min(dx > 0 ? 30.0 / (dx / d) : Double.POSITIVE_INFINITY,
                dy > 0 ? 15.0 / (dy / d) : Double.POSITIVE_INFINITY);
        return d - 2.0 * inside;
    }

    @Test
    public void testMinimumEdgeLength() {
        for (int n : new int[]{2, 3, 4, 5, 6}) {
            Graph graph = new EdgeListGraph();
            for (int i = 1; i <= n; i++) graph.addNode(new GraphNode("X" + i));

            LayoutUtil.circleLayout(graph);

            List<Node> nodes = graph.getNodes();
            double shortest = Double.POSITIVE_INFINITY;

            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    shortest = Math.min(shortest, edgeLength(nodes.get(i), nodes.get(j)));
                }
            }

            // Positions are rounded to whole pixels, so allow two pixels under the 50 asked for.
            assertTrue("n = " + n + ", shortest edge " + shortest, shortest >= 48.0);
        }
    }

    /**
     * The minimum edge length is for small circles; a circle of 20 nodes is the size that keeps its boxes apart,
     * about 560 pixels across for these node boxes, and not the 800 or so the minimum would make it.
     */
    @Test
    public void testLargeCircleNotEnlarged() {
        Graph graph = new EdgeListGraph();
        for (int i = 1; i <= 20; i++) graph.addNode(new GraphNode("X" + i));

        LayoutUtil.circleLayout(graph);

        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;

        for (Node node : graph.getNodes()) {
            minX = Math.min(minX, node.getCenterX());
            maxX = Math.max(maxX, node.getCenterX());
        }

        assertTrue("width " + (maxX - minX), maxX - minX < 560);
    }
}
