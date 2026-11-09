package edu.cmu.tetrad.test;

import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.LayoutUtil;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * Tests that the Kamada-Kawai layout finishes and follows its natural edge length.
 */
public class TestKamadaKawaiLayout {

    private static double meanEdgeLength(double naturalEdgeLength, double springConstant) {
        RandomUtil.getInstance().setSeed(7);
        Graph graph = RandomGraph.randomGraph(15, 0, 20, 100, 100, 100, false);
        LayoutUtil.richardsLayout(graph);

        LayoutUtil.kamadaKawaiLayout(graph, false, naturalEdgeLength, springConstant, 1.0,
                new LayoutUtil.NoOpProgressListener());

        double sum = 0.0;

        for (Edge edge : graph.getEdges()) {
            sum += Math.hypot(edge.getNode1().getCenterX() - edge.getNode2().getCenterX(),
                    edge.getNode1().getCenterY() - edge.getNode2().getCenterY());
        }

        return sum / graph.getNumEdges();
    }

    /**
     * The layout finishes from the default settings on a graph where the settling used to go round in a cycle
     * without end, and the edges come out near the natural length asked for, so that four times the natural length
     * gives about four times the size.
     */
    @Test(timeout = 60000)
    public void testFinishesAndFollowsNaturalLength() {
        double at80 = meanEdgeLength(80.0, 0.2);
        double at320 = meanEdgeLength(320.0, 0.2);

        assertTrue("Mean edge length at 80: " + at80, at80 > 60.0 && at80 < 110.0);
        assertTrue("Mean edge length at 320: " + at320, at320 > 240.0 && at320 < 440.0);
    }

    /**
     * The strength of the springs multiplies every spring alike, so it does not change where the layout settles,
     * only how finely it settles for a given stopping energy; a hundredfold change leaves the size the same to
     * within a few percent.
     */
    @Test(timeout = 60000)
    public void testSpringStrengthDoesNotChangeSize() {
        double weak = meanEdgeLength(80.0, 0.2);
        double strong = meanEdgeLength(80.0, 20.0);

        assertTrue(weak + " vs " + strong, Math.abs(weak - strong) < 0.05 * weak);
    }
}
