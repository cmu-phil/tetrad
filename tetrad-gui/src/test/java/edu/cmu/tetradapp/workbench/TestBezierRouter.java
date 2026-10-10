package edu.cmu.tetradapp.workbench;

import org.junit.Test;

import java.awt.*;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests the choice of sides for curved edges.
 */
public class TestBezierRouter {

    /**
     * Two edges leave one node for two nodes side by side below it. Each on its own bows to its left, which makes
     * the one on the right swing across the one on the left; chosen together they do not cross.
     */
    @Test
    public void testFanDoesNotCross() {
        Rectangle[] nodes = {box(200, 0), box(100, 300), box(140, 300)};
        int[][] edges = {{1, 0}, {0, 2}};
        double[] offsets = new double[2];

        double[] solo = {BezierRouter.soloApex(nodes, 1, 0, 0.0), BezierRouter.soloApex(nodes, 0, 2, 0.0)};
        double[] routed = BezierRouter.route(nodes, edges, offsets);

        assertEquals(0, BezierRouter.countCrossings(nodes, edges, new double[2]));
        assertEquals(1, BezierRouter.countCrossings(nodes, edges, solo));
        assertEquals(0, BezierRouter.countCrossings(nodes, edges, routed));
    }

    /**
     * On layered arrangements of nodes with random edges, choosing the sides together never gives more crossings
     * than each edge choosing on its own, and gives fewer in total. An edge that is one of several between the
     * same two nodes keeps the side its offset gives it.
     */
    @Test
    public void testRoutedNeverWorseThanSolo() {
        Random random = new Random(11);
        int soloTotal = 0;
        int routedTotal = 0;

        for (int trial = 0; trial < 30; trial++) {
            int rows = 4, perRow = 5;
            Rectangle[] nodes = new Rectangle[rows * perRow];

            for (int i = 0; i < nodes.length; i++) {
                nodes[i] = box(100 * (i % perRow) + random.nextInt(30), 90 * (i / perRow));
            }

            int[][] edges = new int[28][];
            double[] offsets = new double[edges.length];

            for (int e = 0; e < edges.length; e++) {
                int from = random.nextInt(nodes.length);
                int to = random.nextInt(nodes.length);
                while (to == from) to = random.nextInt(nodes.length);
                edges[e] = new int[]{from, to};
            }

            offsets[0] = 12.0;
            offsets[1] = -12.0;

            double[] solo = new double[edges.length];

            for (int e = 0; e < edges.length; e++) {
                solo[e] = BezierRouter.soloApex(nodes, edges[e][0], edges[e][1], offsets[e]);
            }

            double[] routed = BezierRouter.route(nodes, edges, offsets);

            assertTrue(routed[0] > 0.0);
            assertTrue(routed[1] < 0.0);

            int soloCrossings = BezierRouter.countCrossings(nodes, edges, solo);
            int routedCrossings = BezierRouter.countCrossings(nodes, edges, routed);

            assertTrue(routedCrossings <= soloCrossings);

            soloTotal += soloCrossings;
            routedTotal += routedCrossings;
        }

        assertTrue(routedTotal < soloTotal);
    }

    private static Rectangle box(int centerX, int centerY) {
        return new Rectangle(centerX - 30, centerY - 15, 60, 30);
    }
}
