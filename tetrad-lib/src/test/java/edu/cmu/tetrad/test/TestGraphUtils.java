///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
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
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import java.util.*;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author josephramsey
 */
public final class TestGraphUtils {

    @Test
    public void testCreateRandomDag() {
        List<Node> nodes = new ArrayList<>();

        for (int i = 0; i < 50; i++) {
            nodes.add(new ContinuousVariable("X" + (i + 1)));
        }

        Dag dag = new Dag(RandomGraph.randomGraph(nodes, 0, 50,
                4, 3, 3, false));

        assertEquals(50, dag.getNumNodes());
        assertEquals(50, dag.getNumEdges());
    }

    @Test
    public void testDirectedPaths() {
        List<Node> nodes = new ArrayList<>();

        for (int i1 = 0; i1 < 6; i1++) {
            nodes.add(new ContinuousVariable("X" + (i1 + 1)));
        }

        Graph graph = new Dag(RandomGraph.randomGraph(nodes, 0, 6,
                3, 3, 3, false));

        for (int i = 0; i < graph.getNodes().size(); i++) {
            for (int j = 0; j < graph.getNodes().size(); j++) {
                Node node1 = graph.getNodes().get(i);
                Node node2 = graph.getNodes().get(j);

                List<List<Node>> directedPaths = graph.paths().directedPathsFromTo(node1, node2, -1);

                for (List<Node> path : directedPaths) {
                    assertTrue(graph.paths().isAncestorOf(path.get(0), path.get(path.size() - 1)));
                }
            }
        }
    }

    @Test
    public void testTreks() {
        List<Node> nodes = new ArrayList<>();

        for (int i1 = 0; i1 < 10; i1++) {
            nodes.add(new ContinuousVariable("X" + (i1 + 1)));
        }

        Graph graph = new Dag(RandomGraph.randomGraph(nodes, 0, 15,
                3, 3, 3, false));

        for (int i = 0; i < graph.getNodes().size(); i++) {
            for (int j = 0; j < graph.getNodes().size(); j++) {
                Node node1 = graph.getNodes().get(i);
                Node node2 = graph.getNodes().get(j);

                List<List<Node>> treks = graph.paths().treks(node1, node2, -1);

                TREKS:
                for (List<Node> trek : treks) {
                    Node m0 = trek.get(0);
                    Node m1 = trek.get(trek.size() - 1);

                    for (Node n : trek) {

                        // Not quite it but good enough for a test.
                        if (graph.paths().isAncestorOf(n, m0) && graph.paths().isAncestorOf(n, m1)) {
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

        List<Node> nodes = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            nodes.add(new ContinuousVariable("X" + (i + 1)));
        }

        Graph g = new Dag(RandomGraph.randomGraph(nodes, 0, 5,
                30, 15, 15, false));

        String x = GraphPersistence.graphToDot(g);
        String[] tokens = x.split("\n");
        int length = tokens.length;
        assertEquals(7, length);

    }

    @Test
    public void testTwoCycleErrors() {
        Node x1 = new GraphNode("X1");
        Node x2 = new GraphNode("X2");
        Node x3 = new GraphNode("X3");
        Node x4 = new GraphNode("X4");

        Graph trueGraph = new EdgeListGraph();
        trueGraph.addNode(x1);
        trueGraph.addNode(x2);
        trueGraph.addNode(x3);
        trueGraph.addNode(x4);

        Graph estGraph = new EdgeListGraph();
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

        GraphUtils.TwoCycleErrors errors = GraphUtils.getTwoCycleErrors(trueGraph, estGraph);

        assertEquals(1, errors.twoCycCor);
        assertEquals(2, errors.twoCycFp);
        assertEquals(1, errors.twoCycFn);
    }

    @Test
    public void testMsep() {
        Node a = new ContinuousVariable("A");
        Node b = new ContinuousVariable("B");
        Node x = new ContinuousVariable("X");
        Node y = new ContinuousVariable("Y");

        Graph graph = new EdgeListGraph();

        graph.addNode(a);
        graph.addNode(b);
        graph.addNode(x);
        graph.addNode(y);

        graph.addDirectedEdge(a, x);
        graph.addDirectedEdge(b, y);
        graph.addDirectedEdge(x, y);
        graph.addDirectedEdge(y, x);

        assertTrue(graph.paths().isAncestorOf(a, a));
        assertTrue(graph.paths().isAncestorOf(b, b));
        assertTrue(graph.paths().isAncestorOf(x, x));
        assertTrue(graph.paths().isAncestorOf(y, y));

        assertTrue(graph.paths().isAncestorOf(a, x));
        assertTrue(!graph.paths().isAncestorOf(x, a));
        assertTrue(graph.paths().isAncestorOf(a, y));
        assertTrue(!graph.paths().isAncestorOf(y, a));

        assertTrue(graph.paths().isAncestorOf(a, y));
        assertTrue(graph.paths().isAncestorOf(b, x));

        assertTrue(!graph.paths().isAncestorOf(a, b));
        assertTrue(!graph.paths().isAncestorOf(y, a));
        assertTrue(!graph.paths().isAncestorOf(x, b));

        assertTrue(graph.paths().isMConnectedTo(a, y, new HashSet<>()));
        assertTrue(graph.paths().isMConnectedTo(b, x, new HashSet<>()));

        assertTrue(graph.paths().isMConnectedTo(a, y, Collections.singleton(x)));
        assertTrue(graph.paths().isMConnectedTo(b, x, Collections.singleton(y)));

        assertTrue(graph.paths().isMConnectedTo(a, y, Collections.singleton(b)));
        assertTrue(graph.paths().isMConnectedTo(b, x, Collections.singleton(a)));

        assertTrue(graph.paths().isMConnectedTo(y, a, Collections.singleton(b)));
        assertTrue(graph.paths().isMConnectedTo(x, b, Collections.singleton(a)));
    }

    @Test
    public void testMsep2() {
        Node a = new ContinuousVariable("A");
        Node b = new ContinuousVariable("B");
        Node c = new ContinuousVariable("C");

        Graph graph = new EdgeListGraph();

        graph.addNode(a);
        graph.addNode(b);
        graph.addNode(c);

        graph.addDirectedEdge(a, b);
        graph.addDirectedEdge(b, c);
        graph.addDirectedEdge(c, b);

        assertTrue(graph.paths().isAncestorOf(a, b));
        assertTrue(graph.paths().isAncestorOf(a, c));

        assertTrue(graph.paths().isMConnectedTo(a, b, Collections.EMPTY_SET));
        assertTrue(graph.paths().isMConnectedTo(a, c, Collections.EMPTY_SET));

        assertTrue(graph.paths().isMConnectedTo(a, c, Collections.singleton(b)));
        assertTrue(graph.paths().isMConnectedTo(c, a, Collections.singleton(b)));
    }


    public void test8() {
        final int numNodes = 5;

        for (int i = 0; i < 100000; i++) {
            Graph graph = RandomGraph.randomGraphRandomForwardEdges(numNodes, 0, numNodes, 10, 10, 10, true);

            List<Node> nodes = graph.getNodes();
            Node x = nodes.get(RandomUtil.getInstance().nextInt(numNodes));
            Node y = nodes.get(RandomUtil.getInstance().nextInt(numNodes));
            Node z1 = nodes.get(RandomUtil.getInstance().nextInt(numNodes));
            Node z2 = nodes.get(RandomUtil.getInstance().nextInt(numNodes));

            if (graph.paths().isMSeparatedFrom(x, y, set(z1)) && graph.paths().isMSeparatedFrom(x, y, set(z2)) &&
                    !graph.paths().isMSeparatedFrom(x, y, set(z1, z2))) {
                System.out.println("x = " + x);
                System.out.println("y = " + y);
                System.out.println("z1 = " + z1);
                System.out.println("z2 = " + z2);
                System.out.println(graph);
                return;
            }
        }
    }

    private Set<Node> set(Node... z) {
        Set<Node> list = new HashSet<>();
        Collections.addAll(list, z);
        return list;
    }
}





