package edu.cmu.tetrad.test;

import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.LayoutUtil;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import java.awt.geom.Line2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests that the causal order layout centers its rows on one another and orders them to reduce crossings.
 */
public class TestCausalOrderLayout {

    /**
     * A graph whose rows by causal order have one, four and two nodes: X1 causes X2 to X5, and X2 causes X6 and X7.
     * Each row should have the same middle, the rows should be in causal order from the top, and the nodes of a row
     * should not overlap.
     */
    @Test
    public void testRowsAreCentered() {
        Graph graph = new EdgeListGraph();
        List<Node> nodes = new ArrayList<>();

        for (int i = 1; i <= 7; i++) {
            Node node = new GraphNode("X" + i);
            nodes.add(node);
            graph.addNode(node);
        }

        for (int i = 1; i <= 4; i++) graph.addDirectedEdge(nodes.get(0), nodes.get(i));
        graph.addDirectedEdge(nodes.get(1), nodes.get(5));
        graph.addDirectedEdge(nodes.get(1), nodes.get(6));

        LayoutUtil.layoutByCausalOrder(graph);

        // The x coordinates in each row, by the row's y coordinate, top row first.
        Map<Integer, List<Integer>> rows = new TreeMap<>();
        for (Node node : nodes) rows.computeIfAbsent(node.getCenterY(), y -> new ArrayList<>()).add(node.getCenterX());

        assertEquals(3, rows.size());

        List<Integer> sizes = new ArrayList<>();
        List<Double> middles = new ArrayList<>();

        for (List<Integer> xs : rows.values()) {
            xs.sort(null);
            sizes.add(xs.size());
            middles.add((xs.get(0) + xs.get(xs.size() - 1)) / 2.0);

            for (int i = 1; i < xs.size(); i++) {
                assertTrue(xs.get(i) - xs.get(i - 1) >= 80);
            }

            assertTrue(xs.get(0) >= 40);
        }

        assertEquals(List.of(1, 4, 2), sizes);
        assertEquals(middles.get(0), middles.get(1), 1.0);
        assertEquals(middles.get(0), middles.get(2), 1.0);
    }

    /**
     * Nodes are spaced by their own widths: with long names in a row, neighboring boxes do not overlap, and the
     * rows are still centered on one another.
     */
    @Test
    public void testLongNamesDoNotOverlap() {
        Graph graph = new EdgeListGraph();
        Node cause = new GraphNode("Cause");
        graph.addNode(cause);

        List<Node> effects = new ArrayList<>();

        for (String name : new String[]{"SystolicBloodPressure", "X", "HouseholdIncomeLastYear", "Age"}) {
            Node node = new GraphNode(name);
            effects.add(node);
            graph.addNode(node);
            graph.addDirectedEdge(cause, node);
        }

        LayoutUtil.NodeSize size = LayoutUtil.estimatedNodeSize();
        LayoutUtil.layoutByCausalOrder(graph, size);

        effects.sort((a, b) -> Integer.compare(a.getCenterX(), b.getCenterX()));

        for (int i = 1; i < effects.size(); i++) {
            Node left = effects.get(i - 1), right = effects.get(i);
            double gap = (right.getCenterX() - size.width(right) / 2.0) - (left.getCenterX() + size.width(left) / 2.0);
            assertTrue(left.getName() + " and " + right.getName() + " are " + gap + " apart", gap >= 20.0);
        }

        Node first = effects.get(0), last = effects.get(effects.size() - 1);
        double rowLeft = first.getCenterX() - size.width(first) / 2.0;
        double rowRight = last.getCenterX() + size.width(last) / 2.0;

        assertEquals(cause.getCenterX(), (rowLeft + rowRight) / 2.0, 1.5);
        assertTrue(cause.getCenterY() < first.getCenterY());
    }

    /**
     * Rows given directly are centered on the widest, top row first, and an empty row takes no space.
     */
    @Test
    public void testCenteredRowsGivenDirectly() {
        List<List<Node>> rows = new ArrayList<>();
        int[] sizes = {2, 0, 5, 1};
        int count = 0;

        for (int size : sizes) {
            List<Node> row = new ArrayList<>();
            for (int i = 0; i < size; i++) row.add(new GraphNode("X" + (++count)));
            rows.add(row);
        }

        LayoutUtil.layoutInCenteredRows(rows, LayoutUtil.estimatedNodeSize());

        double middle = Double.NaN;
        int lastY = Integer.MIN_VALUE;
        int rowPitch = -1;

        for (List<Node> row : rows) {
            if (row.isEmpty()) continue;

            int y = row.get(0).getCenterY();
            for (Node node : row) assertEquals(y, node.getCenterY());

            double rowMiddle = (row.get(0).getCenterX() + row.get(row.size() - 1).getCenterX()) / 2.0;
            if (Double.isNaN(middle)) middle = rowMiddle;
            assertEquals(middle, rowMiddle, 1.0);

            if (lastY != Integer.MIN_VALUE) {
                assertTrue(y > lastY);
                if (rowPitch < 0) rowPitch = y - lastY;
                assertEquals(rowPitch, y - lastY);
            }

            lastY = y;
        }
    }

    /**
     * Every directed edge points down, the layout is the same when run again, and the straight-line crossings over
     * the ten graphs stay under a bound that the layout met once the rows were ordered to reduce crossings (1893
     * before that, 743 after).
     */
    @Test
    public void testRowsAndCrossings() {
        int crossings = 0;

        for (int seed = 0; seed < 10; seed++) {
            RandomUtil.getInstance().setSeed(1000 + seed);
            Graph graph = RandomGraph.randomGraph(30, 0, 45, 100, 100, 100, false);
            LayoutUtil.layoutByCausalOrder(graph);

            List<Node> nodes = graph.getNodes();
            List<Edge> edges = new ArrayList<>(graph.getEdges());

            for (Edge edge : edges) {
                assertTrue(edge + " does not point down",
                        edge.getNode1().getCenterY() < edge.getNode2().getCenterY());
            }

            int[] before = new int[2 * nodes.size()];

            for (int i = 0; i < nodes.size(); i++) {
                before[2 * i] = nodes.get(i).getCenterX();
                before[2 * i + 1] = nodes.get(i).getCenterY();
            }

            LayoutUtil.layoutByCausalOrder(graph);

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

        assertTrue("Crossings: " + crossings, crossings <= 900);
    }

    /**
     * Two rows whose edges cross, A to D and B to C with A left of B and C left of D, come back ordered so that
     * they do not, with the same nodes in each row, and the given rows are left as they were.
     */
    @Test
    public void testOrderRowsToReduceCrossings() {
        Graph graph = new EdgeListGraph();
        Node a = new GraphNode("A"), b = new GraphNode("B"), c = new GraphNode("C"), d = new GraphNode("D");
        for (Node node : List.of(a, b, c, d)) graph.addNode(node);
        graph.addDirectedEdge(a, d);
        graph.addDirectedEdge(b, c);

        List<List<Node>> rows = List.of(new ArrayList<>(List.of(a, b)), new ArrayList<>(List.of(c, d)));
        List<List<Node>> ordered = LayoutUtil.orderRowsToReduceCrossings(graph, rows);

        assertEquals(List.of(a, b), rows.get(0));
        assertEquals(List.of(c, d), rows.get(1));

        assertEquals(2, ordered.size());
        assertEquals(java.util.Set.of(a, b), new java.util.HashSet<>(ordered.get(0)));
        assertEquals(java.util.Set.of(c, d), new java.util.HashSet<>(ordered.get(1)));

        // No crossing: the node joined to the left one above is on the left below.
        Node topLeft = ordered.get(0).get(0);
        Node bottomLeft = ordered.get(1).get(0);
        assertTrue(graph.isAdjacentTo(topLeft, bottomLeft));
    }
}
