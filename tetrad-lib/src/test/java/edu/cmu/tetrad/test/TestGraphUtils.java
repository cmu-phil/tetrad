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
import edu.cmu.tetrad.util.RandomUtil;
import junit.framework.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests the functions of EndpointMatrixGraph and EdgeListGraph through the
 * Graph interface.
 *
 * @author Joseph Ramsey
 */
public final class TestGraphUtils {

    @Test
    public void testCreateRandomDag() {
        //        while (true) {
        List<Node> nodes = new ArrayList<Node>();

        for (int i = 0; i < 50; i++) {
            nodes.add(new ContinuousVariable("X" + (i + 1)));
        }

        Dag dag = new Dag(GraphUtils.randomGraph(nodes, 0, 50,
                4, 3, 3, false));
        System.out.println(dag);
        //        }
    }

    @Test
    public void testDirectedPaths() {
        List<Node> nodes = new ArrayList<Node>();

        for (int i1 = 0; i1 < 6; i1++) {
            nodes.add(new ContinuousVariable("X" + (i1 + 1)));
        }

        Graph graph = new Dag(GraphUtils.randomGraph(nodes, 0, 6,
                3, 3, 3, false));

        System.out.println("Graph = " + graph);

        for (int i = 0; i < graph.getNodes().size(); i++) {
            for (int j = 0; j < graph.getNodes().size(); j++) {
                Node node1 = graph.getNodes().get(i);
                Node node2 = graph.getNodes().get(j);

                System.out.println("Node1 = " + node1 + " Node2 = " + node2);

                List<List<Node>> directedPaths = GraphUtils.directedPathsFromTo(graph, node1, node2, -1);

                for (int k = 0; k < directedPaths.size(); k++) {
                    System.out.println("Path " + k + ": " + directedPaths.get(k));
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

        System.out.println("Graph = " + graph);

        for (int i = 0; i < graph.getNodes().size(); i++) {
            for (int j = 0; j < graph.getNodes().size(); j++) {
                Node node1 = graph.getNodes().get(i);
                Node node2 = graph.getNodes().get(j);

                System.out.println("Node1 = " + node1 + " Node2 = " + node2);

                List<List<Node>> treks = GraphUtils.treks(graph, node1, node2, -1);

                for (int k = 0; k < treks.size(); k++) {
                    System.out.print("Trek " + k + ": ");
                    List<Node> trek = treks.get(k);

                    System.out.print(trek.get(0));

                    for (int m = 1; m < trek.size(); m++) {
                        Node n0 = trek.get(m - 1);
                        Node n1 = trek.get(m);

                        Edge edge = graph.getEdge(n0, n1);

                        Endpoint endpoint0 = edge.getProximalEndpoint(n0);
                        Endpoint endpoint1 = edge.getProximalEndpoint(n1);

                        System.out.print(endpoint0 == Endpoint.ARROW ? "<" : "-");
                        System.out.print("-");
                        System.out.print(endpoint1 == Endpoint.ARROW ? ">" : "-");

                        System.out.print(n1);
                    }

                    System.out.println();
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

        System.out.println(g);

        System.out.println(GraphUtils.graphToDot(g));

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

        System.out.println("Correct = " + errors.twoCycCor);
        System.out.println("FP = " + errors.twoCycFp);
        System.out.println("FN = " + errors.twoCycFn);

        assertEquals(1, errors.twoCycCor);
        assertEquals(2, errors.twoCycFp);
        assertEquals(1, errors.twoCycFn);
    }

    //    public void rtestMaxPathLength() {
    //        int numTests = 10;
    //        int n = 40;
    //        int k = 80;
    //
    //        System.out.println("numTests = " + numTests);
    //        System.out.println("n = " + n);
    //        System.out.println("k = " + k);
    //
    //        int sum = 0;
    //        int min = Integer.MAX_VALUE;
    //        int max = 0;
    //
    //        for (int i = 0; i < numTests; i++) {
    //            Dag dag = DataGraphUtils.createRandomDagC(n, 0, k);
    //            List tiers = dag.getTiers();
    //            sum += tiers.size();
    //            if (tiers.size() < min) {
    //                min = tiers.size();
    //            }
    //            if (tiers.size() > max) {
    //                max = tiers.size();
    //            }
    //        }
    //
    //        double ave = sum / (double) numTests;
    //
    //        System.out.println("OLD: Min = " + min + ", Max = " + max +
    //                ", average = " + ave);
    //
    //        sum = max = 0;
    //        min = Integer.MAX_VALUE;
    //
    //        for (int i = 0; i < numTests; i++) {
    //            Dag dag = DataGraphUtils.createRandomDagB(n, 0, k, 0.0, 0.0, 0.0);
    //            List tiers = dag.getTiers();
    //            sum += tiers.size();
    //            if (tiers.size() < min) {
    //                min = tiers.size();
    //            }
    //            if (tiers.size() > max) {
    //                max = tiers.size();
    //            }
    //        }
    //
    //        ave = sum / (double) numTests;
    //
    //        System.out.println("1: Min = " + min + ", Max = " + max +
    //                ", average = " + ave);
    //
    //        sum = max = 0;
    //        min = Integer.MAX_VALUE;
    //        int totK = 0;
    //
    //        for (int i = 0; i < numTests; i++) {
    ////            System.out.print(".");
    //            Dag dag = DataGraphUtils.createRandomDagC(n, 0, k, 0.0, 0.0, 0.0);
    //            System.out.println("test " + (i + 1) + ": num edges = " + dag.getNumEdges());
    //            System.out.flush();
    //
    //            List tiers = dag.getTiers();
    //            sum += tiers.size();
    //            if (tiers.size() < min) {
    //                min = tiers.size();
    //            }
    //            if (tiers.size() > max) {
    //                max = tiers.size();
    //            }
    //
    //            totK += dag.getNumEdges();
    //        }
    //
    //        ave = sum / (double) numTests;
    //
    //        System.out.println("\n2: Min = " + min + ", Max = " + max +
    //                ", average = " + ave + ", avenumedges = " + totK / (double) numTests);
    //    }

    public void testScaleFree() {
        Graph graph = GraphUtils.scaleFreeGraph(1000, 0, 0.2, 0.4, 3, 3);

        System.out.println(graph);
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

}





