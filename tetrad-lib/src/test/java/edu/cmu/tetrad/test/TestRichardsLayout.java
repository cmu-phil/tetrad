package edu.cmu.tetrad.test;

import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.LayoutUtil;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import java.awt.geom.Line2D;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests Richard's layout on seeded random DAGs.
 */
public class TestRichardsLayout {

    /**
     * Every directed edge points down, no two node boxes overlap, the layout is the same when run again, and the
     * straight-line crossings over the ten graphs stay under a bound that the layout met once neighbors in a layer
     * were exchanged by their final coordinates (774 before that, 736 after).
     */
    @Test
    public void testLayersBoxesAndCrossings() {
        LayoutUtil.NodeSize size = LayoutUtil.estimatedNodeSize();
        int crossings = 0;

        for (int seed = 0; seed < 10; seed++) {
            RandomUtil.getInstance().setSeed(1000 + seed);
            Graph graph = RandomGraph.randomGraph(30, 0, 45, 100, 100, 100, false);
            LayoutUtil.richardsLayout(graph);

            List<Node> nodes = graph.getNodes();
            List<Edge> edges = new ArrayList<>(graph.getEdges());

            for (Edge edge : edges) {
                assertTrue(edge + " does not point down",
                        edge.getNode1().getCenterY() < edge.getNode2().getCenterY());
            }

            for (int i = 0; i < nodes.size(); i++) {
                for (int j = i + 1; j < nodes.size(); j++) {
                    Node n1 = nodes.get(i);
                    Node n2 = nodes.get(j);

                    assertTrue(n1 + " overlaps " + n2,
                            Math.abs(n1.getCenterX() - n2.getCenterX()) >= (size.width(n1) + size.width(n2)) / 2.
                            || Math.abs(n1.getCenterY() - n2.getCenterY()) >= (size.height(n1) + size.height(n2)) / 2.);
                }
            }

            int[] before = new int[2 * nodes.size()];

            for (int i = 0; i < nodes.size(); i++) {
                before[2 * i] = nodes.get(i).getCenterX();
                before[2 * i + 1] = nodes.get(i).getCenterY();
            }

            LayoutUtil.richardsLayout(graph);

            for (int i = 0; i < nodes.size(); i++) {
                assertEquals(before[2 * i], nodes.get(i).getCenterX());
                assertEquals(before[2 * i + 1], nodes.get(i).getCenterY());
            }

            for (int i = 0; i < edges.size(); i++) {
                for (int j = i + 1; j < edges.size(); j++) {
                    Node a = edges.get(i).getNode1(), b = edges.get(i).getNode2();
                    Node c = edges.get(j).getNode1(), d = edges.get(j).getNode2();

                    if (a == c || a == d || b == c || b == d) continue;

                    if (Line2D.linesIntersect(a.getCenterX(), a.getCenterY(), b.getCenterX(), b.getCenterY(),
                            c.getCenterX(), c.getCenterY(), d.getCenterX(), d.getCenterY())) {
                        crossings++;
                    }
                }
            }
        }

        assertTrue("Crossings: " + crossings, crossings <= 750);
    }
}
