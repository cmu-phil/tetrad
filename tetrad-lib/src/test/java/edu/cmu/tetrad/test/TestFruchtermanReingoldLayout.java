///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.test;

import edu.cmu.tetrad.graph.Dag;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.LayoutUtil;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests to make sure the Fruchterman Reingold layout will run.
 *
 * @author josephramsey
 */
public final class TestFruchtermanReingoldLayout {

    @Test
    public void testLayout() {
        //        Dag dag = DataGraphUtils.createRandomDag(40, 0, 80, 6, 6, 6, true);

        Dag dag = new Dag();

        GraphNode x1 = new GraphNode("X1");
        GraphNode x2 = new GraphNode("X2");
        GraphNode x3 = new GraphNode("X3");
        GraphNode x4 = new GraphNode("X4");
        GraphNode x5 = new GraphNode("X5");
        GraphNode x6 = new GraphNode("X6");
        GraphNode x7 = new GraphNode("X7");

        dag.addNode(x1);
        dag.addNode(x2);
        dag.addNode(x3);
        dag.addNode(x4);
        dag.addNode(x5);
        dag.addNode(x6);
        dag.addNode(x7);

        dag.addDirectedEdge(x1, x2);
        dag.addDirectedEdge(x2, x3);
        dag.addDirectedEdge(x4, x5);
        dag.addDirectedEdge(x5, x6);

        Dag dag2 = new Dag(dag);

        LayoutUtil.defaultLayout(dag);

        LayoutUtil.FruchtermanReingoldLayout layout = new LayoutUtil.FruchtermanReingoldLayout(dag);
        layout.doLayout();

        assertEquals(dag, dag2);
    }

    @Test
    public void testLayout2() {
        Dag dag = new Dag();

        GraphNode x1 = new GraphNode("X1");
        GraphNode x2 = new GraphNode("X2");

        x1.setCenter(40, 5);
        x2.setCenter(50, 5);

        dag.addNode(x1);
        dag.addNode(x2);

        dag.addDirectedEdge(x1, x2);

        Dag dag2 = new Dag(dag);

        LayoutUtil.FruchtermanReingoldLayout layout = new LayoutUtil.FruchtermanReingoldLayout(dag);
        layout.doLayout();

        assertEquals(dag, dag2);
    }

    /**
     * A dense core with leaves hung off it, plus two small components. Every
     * component must be laid out exactly once, no two node boxes may overlap,
     * and no leaf edge may be much longer than the typical edge.
     */
    @Test
    public void testSpacing() {
        Graph graph = new EdgeListGraph();
        List<Node> core = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            Node node = new GraphNode("C" + i);
            graph.addNode(node);
            core.add(node);
        }

        for (int i = 0; i < 10; i++) {
            for (int j = i + 1; j < 10; j++) {
                if ((i + j) % 3 != 0) {
                    graph.addDirectedEdge(core.get(i), core.get(j));
                }
            }
        }

        for (int i = 0; i < 6; i++) {
            Node leaf = new GraphNode("L" + i);
            graph.addNode(leaf);
            graph.addDirectedEdge(core.get(i), leaf);
        }

        Node a1 = new GraphNode("A1");
        Node a2 = new GraphNode("A2");
        Node b1 = new GraphNode("B1");
        Node b2 = new GraphNode("B2");
        Node b3 = new GraphNode("B3");

        for (Node node : List.of(a1, a2, b1, b2, b3)) {
            graph.addNode(node);
        }

        graph.addDirectedEdge(a1, a2);
        graph.addDirectedEdge(b1, b2);
        graph.addDirectedEdge(b2, b3);

        LayoutUtil.fruchtermanReingoldLayout(graph);

        List<Node> nodes = graph.getNodes();
        LayoutUtil.NodeSize size = LayoutUtil.estimatedNodeSize();

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                Node n1 = nodes.get(i);
                Node n2 = nodes.get(j);
                double dx = Math.abs(n1.getCenterX() - n2.getCenterX());
                double dy = Math.abs(n1.getCenterY() - n2.getCenterY());

                assertTrue(n1 + " overlaps " + n2,
                        dx >= (size.width(n1) + size.width(n2)) / 2.
                                || dy >= (size.height(n1) + size.height(n2)) / 2.);
            }
        }

        List<Double> lengths = new ArrayList<>();
        double longestLeafEdge = 0.0;

        for (Edge edge : graph.getEdges()) {
            Node n1 = edge.getNode1();
            Node n2 = edge.getNode2();

            if (!core.contains(n1) && !core.contains(n2)) {
                continue;
            }

            double length = Math.hypot(n1.getCenterX() - n2.getCenterX(),
                    n1.getCenterY() - n2.getCenterY());
            lengths.add(length);

            if (!core.contains(n1) || !core.contains(n2)) {
                longestLeafEdge = Math.max(longestLeafEdge, length);
            }
        }

        Collections.sort(lengths);
        double median = lengths.get(lengths.size() / 2);

        assertTrue("Leaf edge " + longestLeafEdge + " vs median " + median,
                longestLeafEdge <= 1.2 * median);

        // The largest component starts the first row, at the left margin, and
        // the small components wrap to a row below it.
        int coreLeft = Integer.MAX_VALUE;
        int coreBottom = Integer.MIN_VALUE;

        for (Node node : nodes) {
            if (node != a1 && node != a2 && node != b1 && node != b2
                    && node != b3) {
                coreLeft = Math.min(coreLeft, node.getCenterX());
                coreBottom = Math.max(coreBottom, node.getCenterY());
            }
        }

        assertEquals(50, coreLeft);
        assertTrue(a1.getCenterY() > coreBottom);
        assertTrue(b1.getCenterY() > coreBottom);
    }

    /**
     * The natural edge length must govern the spread of the layout monotonically: on a one-factor measurement model
     * with ten indicators, doubling it from 40 to 80 to 160 must lengthen the mean edge each time, by a factor
     * between 1.3 and 3 (it is about 2 in practice: edges settle near twice the natural length), and the zero
     * argument must reproduce the automatic layout exactly.
     */
    @Test
    public void testNaturalEdgeLengthGovernsSpread() {
        Graph auto = oneFactor(10);
        LayoutUtil.fruchtermanReingoldLayout(auto);
        Graph zero = oneFactor(10);
        LayoutUtil.fruchtermanReingoldLayout(zero, 0.0);
        assertEquals(meanEdgeLength(auto), meanEdgeLength(zero), 1e-9);

        double previous = -1;
        for (double length : new double[]{40, 80, 160}) {
            Graph g = oneFactor(10);
            LayoutUtil.fruchtermanReingoldLayout(g, length);
            double mean = meanEdgeLength(g);
            assertTrue("length " + length + " gave mean edge " + mean, mean > length && mean < 3 * length);
            if (previous > 0) {
                assertTrue("mean edge should grow with natural length: " + previous + " then " + mean,
                        mean > 1.3 * previous && mean < 3 * previous);
            }
            previous = mean;
        }
    }

    private static Graph oneFactor(int indicators) {
        Graph g = new EdgeListGraph();
        Node l = new GraphNode("L");
        l.setNodeType(NodeType.LATENT);
        g.addNode(l);
        for (int i = 1; i <= indicators; i++) {
            Node x = new GraphNode("X" + i);
            g.addNode(x);
            g.addDirectedEdge(l, x);
        }
        return g;
    }

    private static double meanEdgeLength(Graph g) {
        double sum = 0;
        for (Edge e : g.getEdges()) {
            Node a = e.getNode1(), b = e.getNode2();
            sum += Math.hypot(a.getCenterX() - b.getCenterX(), a.getCenterY() - b.getCenterY());
        }
        return sum / g.getNumEdges();
    }

}






