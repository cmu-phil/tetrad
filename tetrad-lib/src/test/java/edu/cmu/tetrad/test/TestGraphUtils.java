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

import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.RandomUtil;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.util.List;

/**
 * Tests the functions of EndpointMatrixGraph and EdgeListGraph through the
 * Graph interface.
 *
 * @author Joseph Ramsey
 */
public final class TestGraphUtils extends TestCase {

    /**
     * Standard constructor for JUnit test cases.
     */
    public TestGraphUtils(String name) {
        super(name);
    }

    public void testCreateRandomDag() {
        //        while (true) {
        Dag dag = new Dag(GraphUtils.randomGraph(50, 0, 50, 4,
                3, 3, false));
        System.out.println(dag);
        //        }
    }

    public void testDirectedPaths() {
        Graph graph = new Dag(GraphUtils.randomGraph(6, 0, 10, 3,
                3, 3, false));

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

    public void testTreks() {
        Graph graph = new Dag(GraphUtils.randomGraph(10, 0, 15, 3,
                3, 3, false));

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

    public void testGraphToDot() {
        long seed = 28583848283L;
        RandomUtil.getInstance().setSeed(seed);

        Graph g = new Dag(GraphUtils.randomGraph(5, 0, 5, 30, 15, 15, false));

        System.out.println(g);

        System.out.println(GraphUtils.graphToDot(g));

    }

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

    /**
     * This method uses reflection to collect up all of the test methods from
     * this class and return them to the test runner.
     */
    public static Test suite() {

        // Edit the name of the class in the parens to match the name
        // of this class.
        return new TestSuite(TestGraphUtils.class);
    }
}





