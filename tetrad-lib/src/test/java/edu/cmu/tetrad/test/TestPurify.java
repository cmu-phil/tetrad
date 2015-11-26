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
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.search.Mimbuild2;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests the BooleanFunction class.
 *
 * @author Joseph Ramsey
 */
public class TestPurify extends TestCase {

    /**
     * Standard constructor for JUnit test cases.
     */
    public TestPurify(String name) {
        super(name);
    }


    public void test1() {
        SemGraph graph = new SemGraph();

        Node l1 = new GraphNode("L1");
        l1.setNodeType(NodeType.LATENT);

        Node l2 = new GraphNode("L2");
        l2.setNodeType(NodeType.LATENT);

        Node l3 = new GraphNode("L3");
        l3.setNodeType(NodeType.LATENT);

        Node x1 = new GraphNode("X1");
        Node x2 = new GraphNode("X2");
        Node x3 = new GraphNode("X3");
        Node x4 = new GraphNode("X4");
        Node x4b = new GraphNode("X4b");

        Node x5 = new GraphNode("X5");
        Node x6 = new GraphNode("X6");
        Node x7 = new GraphNode("X7");
        Node x8 = new GraphNode("X8");
        Node x8b = new GraphNode("X8b");

        Node x9 = new GraphNode("X9");
        Node x10 = new GraphNode("X10");
        Node x11 = new GraphNode("X11");
        Node x12 = new GraphNode("X12");
        Node x12b = new GraphNode("X12b");

        graph.addNode(l1);
        graph.addNode(l2);
        graph.addNode(l3);

        graph.addNode(x1);
        graph.addNode(x2);
        graph.addNode(x3);
        graph.addNode(x4);
        graph.addNode(x4b);

        graph.addNode(x5);
        graph.addNode(x6);
        graph.addNode(x7);
        graph.addNode(x8);
        graph.addNode(x8b);

        graph.addNode(x9);
        graph.addNode(x10);
        graph.addNode(x11);
        graph.addNode(x12);
        graph.addNode(x12b);


        // edges

        graph.addDirectedEdge(l1, x1);
        graph.addDirectedEdge(l1, x2);
        graph.addDirectedEdge(l1, x3);
        graph.addDirectedEdge(l1, x4);
        graph.addDirectedEdge(l1, x4b);
//        graph.addDirectedEdge(l2, x4);
        graph.addDirectedEdge(l1, x5);

        graph.addDirectedEdge(l2, x5);
        graph.addDirectedEdge(l2, x6);
        graph.addDirectedEdge(l2, x7);
        graph.addDirectedEdge(l2, x8);
        graph.addDirectedEdge(l2, x8b);

        graph.addDirectedEdge(l3, x9);
        graph.addDirectedEdge(l3, x10);
        graph.addDirectedEdge(l3, x11);
        graph.addDirectedEdge(l3, x12);
        graph.addDirectedEdge(l3, x12b);

        graph.addDirectedEdge(x1, x4);
//        graph.addDirectedEdge(x1, x8);
//        graph.addBidirectedEdge(x3, x12);

//        graph.addDirectedEdge(l1, l2);
//        graph.addDirectedEdge(l2, l3);

        System.out.println(graph);


        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);
        DataSet data = im.simulateData(1000, false);

//        List<Node> nodes = graph.getNodes();
//
//        List<List<Node>> partition = new ArrayList<List<Node>>();
//        partition.add(new ArrayList<Node>());
//        partition.add(new ArrayList<Node>());
//
//        for (int i = 0; i < nodes.size(); i++) {
//            if (nodes.get(i).getNodeType() == NodeType.LATENT) continue;
//            partition.get(RandomUtil.getInstance().nextInt(2)).add(nodes.get(i));
//        }

        List<List<Node>> partition = new ArrayList<List<Node>>();

        List<Node> cluster1 = new ArrayList<Node>();
        cluster1.add(x1);
        cluster1.add(x2);
        cluster1.add(x3);
        cluster1.add(x4);
        cluster1.add(x4b);
        cluster1.add(x5);

        List<Node> cluster2 = new ArrayList<Node>();
        cluster2.add(x5);
        cluster2.add(x6);
        cluster2.add(x7);
        cluster2.add(x8);
        cluster2.add(x8b);

        List<Node> cluster3 = new ArrayList<Node>();
        cluster3.add(x9);
        cluster3.add(x10);
        cluster3.add(x11);
        cluster3.add(x12);
        cluster3.add(x12b);

        partition.add(cluster1);
        partition.add(cluster2);
        partition.add(cluster3);

        System.out.println(partition);

        TetradTest test = new ContinuousTetradTest(data, TestType.TETRAD_WISHART, 0.05);
        IPurify purify = new PurifyTetradBased3(test);
//        IPurify purify = new PurifyTetradBasedH(test, 10);
        purify.setTrueGraph(graph);

        System.out.println(purify.purify(partition));
    }

    public void test1b() {
        SemGraph graph = new SemGraph();

        Node l1 = new GraphNode("L1");
        l1.setNodeType(NodeType.LATENT);

        Node l2 = new GraphNode("L2");
        l2.setNodeType(NodeType.LATENT);

        Node x1 = new GraphNode("X1");
        Node x2 = new GraphNode("X2");
        Node x3 = new GraphNode("X3");
        Node x4 = new GraphNode("X4");
        Node x5 = new GraphNode("X5");
        Node x6 = new GraphNode("X6");

        Node x7 = new GraphNode("X7");
        Node x8 = new GraphNode("X8");
        Node x9 = new GraphNode("X9");
        Node x10 = new GraphNode("X10");
        Node x11 = new GraphNode("X11");
        Node x12 = new GraphNode("X12");

        graph.addNode(l1);
        graph.addNode(l2);

        graph.addNode(x1);
        graph.addNode(x2);
        graph.addNode(x3);
        graph.addNode(x4);
        graph.addNode(x5);
        graph.addNode(x6);

        graph.addNode(x7);
        graph.addNode(x8);
        graph.addNode(x9);
        graph.addNode(x10);
        graph.addNode(x11);
        graph.addNode(x12);


        // edges

        graph.addDirectedEdge(l1, x1);
        graph.addDirectedEdge(l1, x2);
        graph.addDirectedEdge(l1, x3);
        graph.addDirectedEdge(l1, x4);
        graph.addDirectedEdge(l1, x5);
        graph.addDirectedEdge(l1, x5);
        graph.addDirectedEdge(l1, x6);

        graph.addDirectedEdge(l2, x6);
        graph.addDirectedEdge(l2, x7);
        graph.addDirectedEdge(l2, x8);
        graph.addDirectedEdge(l2, x9);
        graph.addDirectedEdge(l2, x10);
        graph.addDirectedEdge(l2, x11);
        graph.addDirectedEdge(l2, x12);

        graph.addDirectedEdge(x3, x4);
        graph.addDirectedEdge(x9, x10);

        System.out.println(graph);

        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);
        DataSet data = im.simulateData(3000, false);

        List<List<Node>> partition = new ArrayList<List<Node>>();

        List<Node> cluster1 = new ArrayList<Node>();
        cluster1.add(x1);
        cluster1.add(x2);
        cluster1.add(x3);
        cluster1.add(x4);
        cluster1.add(x5);
//        cluster1.add(x6);

        List<Node> cluster2 = new ArrayList<Node>();
//        cluster2.add(x6);
        cluster2.add(x7);
        cluster2.add(x8);
        cluster2.add(x9);
        cluster2.add(x10);
        cluster2.add(x11);
        cluster2.add(x12);

        partition.add(cluster1);
        partition.add(cluster2);

        System.out.println(partition);

        TetradTest test = new ContinuousTetradTest(data, TestType.TETRAD_WISHART, 0.0001);
        IPurify purify = new PurifyTetradBased3(test);
//        IPurify purify = new PurifyTetradBasedH(test, 10);
        purify.setTrueGraph(graph);

        List<List<Node>> clustering = purify.purify(partition);
        System.out.println(clustering);

//        PurifyTetradBasedG purify2 = new PurifyTetradBasedG(test);
//        System.out.println(purify2.purify(mimClustering));

//        Clusters clusters = new Clusters();
//
//        for (int i = 0; i < partition.size(); i++) {
//            for (Node node : partition.get(i)) {
//                clusters.addToCluster(i, node.getName());
//            }
//        }
//
//        Purify purify2 = new Purify(test, clusters);
//        List<List<Node>> _partition = purify.purify(partition);
//
//        System.out.println(_partition);
    }

    public void test2() {
        Graph graph = new EdgeListGraph(DataGraphUtils.randomSingleFactorModel(3, 3, 5, 0, 0, 0));
//        Graph graph = DataGraphUtils.randomMim(10, 10, 5, 0, 0, 0);

        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);
        DataSet data = im.simulateData(1000, false);

        List<Node> latents = new ArrayList<Node>();

        for (Node node : graph.getNodes()) {
            if (node.getNodeType() == NodeType.LATENT) latents.add(node);
        }

        Graph structuralGraph = graph.subgraph(latents);
        System.out.println("True structural graph = " + structuralGraph);

        List<List<Node>> clustering = new ArrayList<List<Node>>();

        for (Node node : latents) {
            List<Node> adj = graph.getAdjacentNodes(node);
            adj.removeAll(latents);

            clustering.add(adj);
        }

        System.out.println("Purify");
        ContinuousTetradTest test = new ContinuousTetradTest(data, TestType.TETRAD_WISHART, 0.001);

        IPurify purify = new PurifyTetradBased3(test);

        List<List<Node>> purifiedClustering = purify.purify(clustering);
        List<String> latentsNames = new ArrayList<String>();

        for (int i = 0; i < latents.size(); i++) {
            latentsNames.add(latents.get(i).getName());
        }

        Mimbuild2 mimbuild = new Mimbuild2();
        mimbuild.setAlpha(0.0001);
        Graph _graph = mimbuild.search(purifiedClustering, latentsNames, new CovarianceMatrix(data));
//        Graph _structuralModel = mimbuild.getStructuralModel();

        System.out.println(_graph);

        List<Node> _latents = new ArrayList<Node>();

        for (Node node : _graph.getNodes()) {
            if (node.getNodeType() == NodeType.LATENT) _latents.add(node);
        }

        Graph _structuralGraph = _graph.subgraph(_latents);
        System.out.println("Estimated structural graph = " + _structuralGraph);


//        System.out.println(_structuralModel);

        System.out.println("Done!");
    }

    public void testTest() {
        List<Node> nodes = new ArrayList<Node>();
        GraphNode x1 = new GraphNode("X1");
        GraphNode x2 = new GraphNode("X2");
        GraphNode x3 = new GraphNode("X3");
        GraphNode x4 = new GraphNode("X4");
        GraphNode x5 = new GraphNode("X5");

        nodes.add(x1);
        nodes.add(x2);
        nodes.add(x3);
        nodes.add(x4);
        nodes.add(x5);

        System.out.println(nodes);

        Edge edge = Edges.directedEdge(x2, x4);

        Node node1 = edge.getNode1();
        Node node2 = edge.getNode2();

        int i = nodes.indexOf(node1);
        int j = nodes.indexOf(node2);
        nodes.set(i, node2);
        nodes.set(j, node1);

        System.out.println(nodes);
    }


    /**
     * This method uses reflection to collect up all of the test methods from this class and return them to the test
     * runner.
     */
    public static Test suite() {

        // Edit the name of the class in the parens to match the name
        // of this class.
        return new TestSuite(TestPurify.class);
    }
}


