package edu.cmu.tetrad.test;

import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.LayoutUtil;
import edu.cmu.tetrad.graph.Node;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests that the causal order layout centers its rows on one another.
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
}
