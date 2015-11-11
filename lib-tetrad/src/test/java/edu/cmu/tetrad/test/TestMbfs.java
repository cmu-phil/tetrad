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
import edu.cmu.tetrad.search.IndTestDSep;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.MbUtils;
import edu.cmu.tetrad.search.Mbfs;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradLogger;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.io.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TestMbfs extends TestCase {
    static Graph testGraphSub;
    static Graph testGraphSubCorrect;

    public TestMbfs(String name) {
        super(name);
    }

    public void setUp() throws Exception {
        TetradLogger.getInstance().addOutputStream(System.out);
        TetradLogger.getInstance().setForceLog(true);
    }


    public void tearDown() {
        TetradLogger.getInstance().setForceLog(false);
        TetradLogger.getInstance().removeOutputStream(System.out);
    }


    public static void testSubgraph1() {
        TetradLogger.getInstance().addOutputStream(System.out);
        TetradLogger.getInstance().setForceLog(true);

        Graph graph = GraphConverter.convert("T-->X,X-->Y,W-->X,W-->Y");
        IndTestDSep test = new IndTestDSep(graph);
        Mbfs mbSearch = new Mbfs(test, -1);
        mbSearch.search("T");

        // Watch printout.
    }

    public static void testSubgraph2() {
        Graph graph = GraphConverter.convert("P1-->T,P2-->T,T-->C1,T-->C2," +
                "T-->C3,PC1a-->C1,PC1b-->C1,PC2a-->C2,PC2b<--C2,PC3a-->C3," +
                "PC3b-->C3,PC1b-->PC2a,PC1a<--PC3b,U,V");

        System.out.println("True graph is: " + graph);
        IndTestDSep test = new IndTestDSep(graph);
        Mbfs mbSearch = new Mbfs(test, -1);
        mbSearch.search("T");

        // Watch printout.
    }

    /**
     * Tests to make sure the algorithm for generating MB DAGs from an MB Pattern works, at least for one kind of tricky
     * case.
     */
    public static void testGenerateDaglist() {
        Graph graph =
                GraphConverter.convert("T-->X1,T-->X2,X1-->X2,T-->X3,X4-->T");

        System.out.println("True graph is: " + graph);
        IndTestDSep test = new IndTestDSep(graph);
        Mbfs search = new Mbfs(test, -1);
        Graph resultGraph = search.search("T");

        System.out.println("\n\n################### MB DAGS #################");
        List mbDags = MbUtils.generateMbDags(resultGraph, true,
                search.getTest(), search.getDepth(), search.getTarget());
        System.out.println("Number of dags = " + mbDags.size());
        System.out.println(mbDags);

        assertTrue(mbDags.size() == 9);
        assertTrue(mbDags.contains(graph));
    }

    public static void testRandom() {
        RandomUtil.getInstance().setSeed(8388428832L);

        Dag dag = new Dag(GraphUtils.randomGraph(10, 0, 10, 5,
                5, 5, false));

//        SemPm semPm = new SemPm(dag);
//        SemIm semIm = new SemIm(semPm);
//        RectangularDataSet dataSet = semIm.simulateData(1000, false);
//        IndependenceTest test = new IndTestFisherZ(dataSet, 0.05);

        IndependenceTest test = new IndTestDSep(dag);
        Mbfs search = new Mbfs(test, -1);

        System.out.println("INDEPENDENT GRAPH: " + dag);

        List<Node> nodes = dag.getNodes();

        for (Node node : nodes) {
            Graph resultMb = search.search(node.getName());
            Graph trueMb = GraphUtils.markovBlanketDag(node, dag);

            List<Node> resultNodes = resultMb.getNodes();
            List<Node> trueNodes = trueMb.getNodes();

            Set<String> resultNames = new HashSet<String>();

            for (Node resultNode : resultNodes) {
                resultNames.add(resultNode.getName());
            }

            Set<String> trueNames = new HashSet<String>();

            for (Node v : trueNodes) {
                trueNames.add(v.getName());
            }

            System.out.println("result names = " + resultNames);
            System.out.println("true names = " + trueNames);

            assertTrue(resultNames.equals(trueNames));

            Set<Edge> resultEdges = resultMb.getEdges();

            for (Edge resultEdge : resultEdges) {
                if (Edges.isDirectedEdge(resultEdge)) {
                    String name1 = resultEdge.getNode1().getName();
                    String name2 = resultEdge.getNode2().getName();

                    Node node1 = trueMb.getNode(name1);
                    Node node2 = trueMb.getNode(name2);

                    // If one of these nodes is null, probably it's because some
                    // parent of the target could not be oriented as such, and
                    // extra nodes and edges are being included to cover the
                    // possibility that the node is actually a child.
                    if (node1 == null) {
                        System.err.println(
                                "Node " + name1 + " is not in the true graph.");
                        continue;
                    }

                    if (node2 == null) {
                        System.err.println(
                                "Node " + name2 + " is not in the true graph.");
                        continue;
                    }

                    Edge trueEdge = trueMb.getEdge(node1, node2);

                    if (trueEdge == null) {
                        Node resultNode1 = resultMb.getNode(node1.getName());
                        Node resultNode2 = resultMb.getNode(node2.getName());
                        Node resultTarget = resultMb.getNode(node.getName());

                        Edge a = resultMb.getEdge(resultNode1, resultTarget);
                        Edge b = resultMb.getEdge(resultNode2, resultTarget);

                        if (a == null || b == null) {
                            continue;
                        }

                        if ((Edges.isDirectedEdge(a) &&
                                Edges.isUndirectedEdge(b)) || (
                                Edges.isUndirectedEdge(a) &&
                                        Edges.isDirectedEdge(b))) {
                            continue;
                        }

                        fail("EXTRA EDGE: Edge in result MB but not true MB = " +
                                resultEdge);
                    }

                    assertEquals(resultEdge.getEndpoint1(),
                            trueEdge.getEndpoint1());
                    assertEquals(resultEdge.getEndpoint2(),
                            trueEdge.getEndpoint2());

                    System.out.println("Result edge = " + resultEdge +
                            ", true edge = " + trueEdge);
                }
            }

            // TODO
//            // Make sure that if adj(X, Y) in the true graph that adj(X, Y)
//            // in the result graph.
//            Set<Edge> trueEdges = trueMb.getEdges();
//
//            for (Edge trueEdge : trueEdges) {
//                Node node1 = trueEdge.getNode1();
//                Node node2 = trueEdge.getNode2();
//
//                Node resultNode1 = resultMb.getNode(node1.getName());
//                Node resultNode2 = resultMb.getNode(node2.getName());
//
//                assertTrue("Expected adjacency " + resultNode1 + "---" +
//                        resultNode2,
//                        resultMb.isAdjacentTo(resultNode1, resultNode2));
//            }
        }
    }

    /**
     * This method uses reflection to collect up all of the test methods from this class and return them to the test
     * runner.
     */
    public static Test suite() {

        // Edit the name of the class in the parens to match the name
        // of this class.
        return new TestSuite(TestMbfs.class);
    }
}





