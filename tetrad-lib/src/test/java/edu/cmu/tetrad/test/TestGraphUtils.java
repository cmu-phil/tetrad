///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.DagToPag2;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author Joseph Ramsey
 */
public final class TestGraphUtils {

    @Test
    public void testCreateRandomDag() {
        final List<Node> nodes = new ArrayList<>();

        for (int i = 0; i < 50; i++) {
            nodes.add(new ContinuousVariable("X" + (i + 1)));
        }

        final Dag dag = new Dag(GraphUtils.randomGraph(nodes, 0, 50,
                4, 3, 3, false));

        assertEquals(50, dag.getNumNodes());
        assertEquals(50, dag.getNumEdges());
    }

    @Test
    public void testDirectedPaths() {
        final List<Node> nodes = new ArrayList<>();

        for (int i1 = 0; i1 < 6; i1++) {
            nodes.add(new ContinuousVariable("X" + (i1 + 1)));
        }

        final Graph graph = new Dag(GraphUtils.randomGraph(nodes, 0, 6,
                3, 3, 3, false));

        for (int i = 0; i < graph.getNodes().size(); i++) {
            for (int j = 0; j < graph.getNodes().size(); j++) {
                final Node node1 = graph.getNodes().get(i);
                final Node node2 = graph.getNodes().get(j);

                final List<List<Node>> directedPaths = GraphUtils.directedPathsFromTo(graph, node1, node2, -1);

                for (final List<Node> path : directedPaths) {
                    assertTrue(graph.isAncestorOf(path.get(0), path.get(path.size() - 1)));
                }
            }
        }
    }

    @Test
    public void testTreks() {
        final List<Node> nodes = new ArrayList<>();

        for (int i1 = 0; i1 < 10; i1++) {
            nodes.add(new ContinuousVariable("X" + (i1 + 1)));
        }

        final Graph graph = new Dag(GraphUtils.randomGraph(nodes, 0, 15,
                3, 3, 3, false));

        for (int i = 0; i < graph.getNodes().size(); i++) {
            for (int j = 0; j < graph.getNodes().size(); j++) {
                final Node node1 = graph.getNodes().get(i);
                final Node node2 = graph.getNodes().get(j);

                final List<List<Node>> treks = GraphUtils.treks(graph, node1, node2, -1);

                TREKS:
                for (int k = 0; k < treks.size(); k++) {
                    final List<Node> trek = treks.get(k);

                    final Node m0 = trek.get(0);
                    final Node m1 = trek.get(trek.size() - 1);

                    for (final Node n : trek) {

                        // Not quite it but good enough for a test.
                        if (graph.isAncestorOf(n, m0) && graph.isAncestorOf(n, m1)) {
                            continue TREKS;
                        }
                    }

                    fail("Some trek failed.");
                }
            }
        }
    }

    @Test
    public void testGraphToDot() {
        final long seed = 28583848283L;
        RandomUtil.getInstance().setSeed(seed);

        final List<Node> nodes = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            nodes.add(new ContinuousVariable("X" + (i + 1)));
        }

        final Graph g = new Dag(GraphUtils.randomGraph(nodes, 0, 5,
                30, 15, 15, false));

        final String x = GraphUtils.graphToDot(g);
        final String[] tokens = x.split("\n");
        final int length = tokens.length;
        assertEquals(7, length);

    }

    @Test
    public void testTwoCycleErrors() {
        final Node x1 = new GraphNode("X1");
        final Node x2 = new GraphNode("X2");
        final Node x3 = new GraphNode("X3");
        final Node x4 = new GraphNode("X4");

        final Graph trueGraph = new EdgeListGraph();
        trueGraph.addNode(x1);
        trueGraph.addNode(x2);
        trueGraph.addNode(x3);
        trueGraph.addNode(x4);

        final Graph estGraph = new EdgeListGraph();
        estGraph.addNode(x1);
        estGraph.addNode(x2);
        estGraph.addNode(x3);
        estGraph.addNode(x4);

        trueGraph.addDirectedEdge(x1, x2);
        trueGraph.addDirectedEdge(x2, x1);
        trueGraph.addDirectedEdge(x2, x3);
        trueGraph.addDirectedEdge(x3, x2);

        estGraph.addDirectedEdge(x1, x2);
        estGraph.addDirectedEdge(x2, x1);
        estGraph.addDirectedEdge(x3, x4);
        estGraph.addDirectedEdge(x4, x3);
        estGraph.addDirectedEdge(x4, x1);
        estGraph.addDirectedEdge(x1, x4);

        final GraphUtils.TwoCycleErrors errors = GraphUtils.getTwoCycleErrors(trueGraph, estGraph);

        assertEquals(1, errors.twoCycCor);
        assertEquals(2, errors.twoCycFp);
        assertEquals(1, errors.twoCycFn);
    }

    @Test
    public void testDsep() {
        final Node a = new ContinuousVariable("A");
        final Node b = new ContinuousVariable("B");
        final Node x = new ContinuousVariable("X");
        final Node y = new ContinuousVariable("Y");

        final Graph graph = new EdgeListGraph();

        graph.addNode(a);
        graph.addNode(b);
        graph.addNode(x);
        graph.addNode(y);

        graph.addDirectedEdge(a, x);
        graph.addDirectedEdge(b, y);
        graph.addDirectedEdge(x, y);
        graph.addDirectedEdge(y, x);

        assertTrue(graph.isAncestorOf(a, a));
        assertTrue(graph.isAncestorOf(b, b));
        assertTrue(graph.isAncestorOf(x, x));
        assertTrue(graph.isAncestorOf(y, y));

        assertTrue(graph.isAncestorOf(a, x));
        assertTrue(!graph.isAncestorOf(x, a));
        assertTrue(graph.isAncestorOf(a, y));
        assertTrue(!graph.isAncestorOf(y, a));

        assertTrue(graph.isAncestorOf(a, y));
        assertTrue(graph.isAncestorOf(b, x));

        assertTrue(!graph.isAncestorOf(a, b));
        assertTrue(!graph.isAncestorOf(y, a));
        assertTrue(!graph.isAncestorOf(x, b));

        assertTrue(graph.isDConnectedTo(a, y, new ArrayList<Node>()));
        assertTrue(graph.isDConnectedTo(b, x, new ArrayList<Node>()));

        assertTrue(graph.isDConnectedTo(a, y, Collections.singletonList(x)));
        assertTrue(graph.isDConnectedTo(b, x, Collections.singletonList(y)));

        assertTrue(graph.isDConnectedTo(a, y, Collections.singletonList(b)));
        assertTrue(graph.isDConnectedTo(b, x, Collections.singletonList(a)));

        assertTrue(graph.isDConnectedTo(y, a, Collections.singletonList(b)));
        assertTrue(graph.isDConnectedTo(x, b, Collections.singletonList(a)));
    }

    @Test
    public void testDsep2() {
        final Node a = new ContinuousVariable("A");
        final Node b = new ContinuousVariable("B");
        final Node c = new ContinuousVariable("C");

        final Graph graph = new EdgeListGraph();

        graph.addNode(a);
        graph.addNode(b);
        graph.addNode(c);

        graph.addDirectedEdge(a, b);
        graph.addDirectedEdge(b, c);
        graph.addDirectedEdge(c, b);

        assertTrue(graph.isAncestorOf(a, b));
        assertTrue(graph.isAncestorOf(a, c));

        assertTrue(graph.isDConnectedTo(a, b, Collections.EMPTY_LIST));
        assertTrue(graph.isDConnectedTo(a, c, Collections.EMPTY_LIST));

        assertTrue(graph.isDConnectedTo(a, c, Collections.singletonList(b)));
        assertTrue(graph.isDConnectedTo(c, a, Collections.singletonList(b)));
    }


    public void test8() {
        final int numNodes = 5;

        for (int i = 0; i < 100000; i++) {
            final Graph graph = GraphUtils.randomGraphRandomForwardEdges(numNodes, 0, numNodes, 10, 10, 10, true);

            final List<Node> nodes = graph.getNodes();
            final Node x = nodes.get(RandomUtil.getInstance().nextInt(numNodes));
            final Node y = nodes.get(RandomUtil.getInstance().nextInt(numNodes));
            final Node z1 = nodes.get(RandomUtil.getInstance().nextInt(numNodes));
            final Node z2 = nodes.get(RandomUtil.getInstance().nextInt(numNodes));

            if (graph.isDSeparatedFrom(x, y, list(z1)) && graph.isDSeparatedFrom(x, y, list(z2)) &&
                    !graph.isDSeparatedFrom(x, y, list(z1, z2))) {
                System.out.println("x = " + x);
                System.out.println("y = " + y);
                System.out.println("z1 = " + z1);
                System.out.println("z2 = " + z2);
                System.out.println(graph);
                return;
            }
        }
    }

    @Test
    public void testPagColoring() {
        final Graph dag = GraphUtils.randomGraph(30, 5, 50, 10, 10, 10, false);
        final Graph pag = new DagToPag2(dag).convert();

        GraphUtils.addPagColoring(pag);

        for (final Edge edge : pag.getEdges()) {
            final Node x1 = edge.getNode1();
            final Node x2 = edge.getNode2();

            if (edge.getLineColor() == Color.green) {
                System.out.println("Green");

                for (final Node L : pag.getNodes()) {
                    if (L == x1 || L == x2) continue;

                    if (L.getNodeType() == NodeType.LATENT) {
                        if (TestGraphUtils.existsLatentPath(dag, L, x1) && TestGraphUtils.existsLatentPath(dag, L, x2)) {
                            System.out.println("Edge " + edge + " falsely colored green.");
                        }
                    }
                }
            }

            if (edge.isBold()) {
                System.out.println("Bold");

                if (!TestGraphUtils.existsLatentPath(dag, x1, x2)) {
                    System.out.println("Edge " + edge + " is falsely bold.");
                }
            }
        }
    }

    public static boolean existsLatentPath(final Graph graph, final Node b, final Node y) {
        if (b == y) return false;
        return TestGraphUtils.existsLatentPath(graph, b, y, new LinkedList<Node>());
    }

    public static boolean existsLatentPath(final Graph graph, final Node b, final Node y, final LinkedList<Node> path) {
        if (path.contains(b)) {
            return false;
        }

        path.addLast(b);

        for (final Node c : graph.getChildren(b)) {
            if (c == y) return true;

            if (c.getNodeType() != NodeType.LATENT) {
                continue;
            }

            if (!TestGraphUtils.existsLatentPath(graph, c, y, path)) {
                return false;
            }
        }

        path.removeLast();
        return true;
    }

    private List<Node> list(final Node... z) {
        final List<Node> list = new ArrayList<>();
        Collections.addAll(list, z);
        return list;
    }
}





