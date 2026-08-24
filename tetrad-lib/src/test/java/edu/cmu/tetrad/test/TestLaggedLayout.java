package edu.cmu.tetrad.test;

import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.LayoutUtil;
import edu.cmu.tetrad.graph.Node;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Pins the lag-aware layout helpers: lag indices are parsed tolerantly (no exception on non-integer suffixes),
 * lagged graphs are recognized by their node names, and layoutByKnowledgeIndices puts the current slice in the
 * bottom row with each earlier lag in a row above.
 */
public class TestLaggedLayout {

    @Test
    public void testLagIndexParsing() {
        assertEquals(0, LayoutUtil.lagIndex("X"));
        assertEquals(1, LayoutUtil.lagIndex("X:1"));
        assertEquals(12, LayoutUtil.lagIndex("Temp:12"));
        assertEquals(2, LayoutUtil.lagIndex("a:b:2"));
        assertEquals(0, LayoutUtil.lagIndex("a:b"));      // non-integer suffix: unlagged, no exception
        assertEquals(0, LayoutUtil.lagIndex("X:"));
        assertEquals(0, LayoutUtil.lagIndex("X:-1"));
    }

    @Test
    public void testIsLaggedGraph() {
        Graph plain = new EdgeListGraph();
        plain.addNode(new GraphNode("X"));
        plain.addNode(new GraphNode("Y"));
        assertFalse(LayoutUtil.isLaggedGraph(plain));

        Graph lagged = new EdgeListGraph();
        lagged.addNode(new GraphNode("X"));
        lagged.addNode(new GraphNode("X:1"));
        assertTrue(LayoutUtil.isLaggedGraph(lagged));

        Graph colonButNotLag = new EdgeListGraph();
        colonButNotLag.addNode(new GraphNode("a:b"));
        assertFalse(LayoutUtil.isLaggedGraph(colonButNotLag));
    }

    @Test
    public void testLayoutRowsByLag() {
        Graph g = new EdgeListGraph();
        for (String n : new String[]{"X", "Y", "X:1", "Y:1", "X:2", "Y:2"}) g.addNode(new GraphNode(n));
        LayoutUtil.layoutByKnowledgeIndices(g);
        Node x0 = g.getNode("X"), x1 = g.getNode("X:1"), x2 = g.getNode("X:2");
        assertTrue("current slice should be below lag 1", x0.getCenterY() > x1.getCenterY());
        assertTrue("lag 1 should be below lag 2", x1.getCenterY() > x2.getCenterY());
        assertEquals("same-lag nodes share a row", g.getNode("Y:1").getCenterY(), x1.getCenterY());
    }
}
