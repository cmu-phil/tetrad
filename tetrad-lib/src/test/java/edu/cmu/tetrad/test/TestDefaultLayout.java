package edu.cmu.tetrad.test;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.LayoutUtil;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/**
 * Tests that the default layout can be chosen.
 */
public class TestDefaultLayout {

    private static Graph unpositioned() {
        RandomUtil.getInstance().setSeed(3);
        Graph graph = RandomGraph.randomGraph(10, 0, 12, 100, 100, 100, false);
        for (Node node : graph.getNodes()) node.setCenter(-1, -1);
        return graph;
    }

    private static String positions(Graph graph) {
        StringBuilder b = new StringBuilder();
        for (Node node : graph.getNodes()) b.append(node.getCenterX()).append(',').append(node.getCenterY()).append(' ');
        return b.toString();
    }

    /**
     * Each choice of default lays out an unpositioned graph exactly as that layout does, starting from Richard's
     * layout; and the force-directed default, which starts from a layout of its own, does not call itself to get
     * one.
     */
    @Test(timeout = 60000)
    public void testDefaultFollowsTheChoice() {
        LayoutUtil.DefaultLayout saved = LayoutUtil.getDefaultLayout();

        try {
            LayoutUtil.setDefaultLayout(null);
            assertEquals(LayoutUtil.DefaultLayout.RICHARDS, LayoutUtil.getDefaultLayout());

            Graph richards = unpositioned();
            LayoutUtil.richardsLayout(richards);
            Graph byDefault = unpositioned();
            LayoutUtil.defaultLayoutConditional(byDefault);
            assertEquals(positions(richards), positions(byDefault));

            LayoutUtil.setDefaultLayout(LayoutUtil.DefaultLayout.CIRCLE);
            Graph circle = unpositioned();
            LayoutUtil.circleLayout(circle);
            byDefault = unpositioned();
            LayoutUtil.defaultLayoutConditional(byDefault);
            assertEquals(positions(circle), positions(byDefault));
            assertNotEquals(positions(richards), positions(byDefault));

            LayoutUtil.setDefaultLayout(LayoutUtil.DefaultLayout.FRUCHTERMAN_REINGOLD);
            Graph fr = unpositioned();
            LayoutUtil.fruchtermanReingoldLayout(fr);
            byDefault = unpositioned();
            LayoutUtil.defaultLayoutConditional(byDefault);
            assertEquals(positions(fr), positions(byDefault));
        } finally {
            LayoutUtil.setDefaultLayout(saved);
        }
    }
}
