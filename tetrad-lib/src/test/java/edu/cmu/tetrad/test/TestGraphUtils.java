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

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import java.io.*;
import java.text.DecimalFormat;
import java.util.*;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author Joseph Ramsey
 */
public final class TestGraphUtils {

    @Test
    public void testCreateRandomDag() {
        List<Node> nodes = new ArrayList<Node>();

        for (int i = 0; i < 50; i++) {
            nodes.add(new ContinuousVariable("X" + (i + 1)));
        }

        Dag dag = new Dag(GraphUtils.randomGraph(nodes, 0, 50,
                4, 3, 3, false));

        assertEquals(50, dag.getNumNodes());
        assertEquals(50, dag.getNumEdges());
    }

    @Test
    public void testDirectedPaths() {
        List<Node> nodes = new ArrayList<Node>();

        for (int i1 = 0; i1 < 6; i1++) {
            nodes.add(new ContinuousVariable("X" + (i1 + 1)));
        }

        Graph graph = new Dag(GraphUtils.randomGraph(nodes, 0, 6,
                3, 3, 3, false));

        for (int i = 0; i < graph.getNodes().size(); i++) {
            for (int j = 0; j < graph.getNodes().size(); j++) {
                Node node1 = graph.getNodes().get(i);
                Node node2 = graph.getNodes().get(j);

                List<List<Node>> directedPaths = GraphUtils.directedPathsFromTo(graph, node1, node2, -1);

                for (List<Node> path : directedPaths) {
                    assertTrue(graph.isAncestorOf(path.get(0), path.get(path.size() - 1)));
                }
            }
        }
    }

    @Test
    public void testTreks() {
        List<Node> nodes = new ArrayList<Node>();

        for (int i1 = 0; i1 < 10; i1++) {
            nodes.add(new ContinuousVariable("X" + (i1 + 1)));
        }

        Graph graph = new Dag(GraphUtils.randomGraph(nodes, 0, 15,
                3, 3, 3, false));

        for (int i = 0; i < graph.getNodes().size(); i++) {
            for (int j = 0; j < graph.getNodes().size(); j++) {
                Node node1 = graph.getNodes().get(i);
                Node node2 = graph.getNodes().get(j);

                List<List<Node>> treks = GraphUtils.treks(graph, node1, node2, -1);

                TREKS:
                for (int k = 0; k < treks.size(); k++) {
                    List<Node> trek = treks.get(k);

                    Node m0 = trek.get(0);
                    Node m1 = trek.get(trek.size() - 1);

                    for (Node n : trek) {

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
        long seed = 28583848283L;
        RandomUtil.getInstance().setSeed(seed);

        List<Node> nodes = new ArrayList<Node>();

        for (int i = 0; i < 5; i++) {
            nodes.add(new ContinuousVariable("X" + (i + 1)));
        }

        Graph g = new Dag(GraphUtils.randomGraph(nodes, 0, 5,
                30, 15, 15, false));

        String x = GraphUtils.graphToDot(g);
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
    public void testDsep() {
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

        assertTrue(graph.isAncestorOf(a, b));
        assertTrue(graph.isAncestorOf(a, c));

        assertTrue(graph.isDConnectedTo(a, b, Collections.EMPTY_LIST));
        assertTrue(graph.isDConnectedTo(a, c, Collections.EMPTY_LIST));

        assertTrue(graph.isDConnectedTo(a, c, Collections.singletonList(b)));
        assertTrue(graph.isDConnectedTo(c, a, Collections.singletonList(b)));
    }


    public void test8() {
        int numNodes = 5;

        for (int i = 0; i < 100000; i++) {
            Graph graph = GraphUtils.randomGraphRandomForwardEdges(numNodes, 0, numNodes, 10, 10, 10, true);

            List<Node> nodes = graph.getNodes();
            Node x = nodes.get(RandomUtil.getInstance().nextInt(numNodes));
            Node y = nodes.get(RandomUtil.getInstance().nextInt(numNodes));
            Node z1 = nodes.get(RandomUtil.getInstance().nextInt(numNodes));
            Node z2 = nodes.get(RandomUtil.getInstance().nextInt(numNodes));

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

    private List<Node> list(Node... z) {
        List<Node> list = new ArrayList<>();
        Collections.addAll(list, z);
        return list;
    }
}





