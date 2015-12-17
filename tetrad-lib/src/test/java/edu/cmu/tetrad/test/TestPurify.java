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
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author Joseph Ramsey
 */
public class TestPurify {

    @Test
    public void test1() {
        RandomUtil.getInstance().setSeed(48290483L);

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

        graph.addDirectedEdge(l1, x1);
        graph.addDirectedEdge(l1, x2);
        graph.addDirectedEdge(l1, x3);
        graph.addDirectedEdge(l1, x4);
        graph.addDirectedEdge(l1, x4b);
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

        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);
        DataSet data = im.simulateData(1000, false);

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

        TetradTest test = new ContinuousTetradTest(data, TestType.TETRAD_WISHART, 0.05);
        IPurify purify = new PurifyTetradBased2(test);
        purify.setTrueGraph(graph);

        List<List<Node>> partition2 = purify.purify(partition);

        assertEquals(3, partition2.get(0).size());
        assertEquals(2, partition2.get(1).size());
        assertEquals(5, partition2.get(2).size());
    }

    @Test
    public void test1b() {
        RandomUtil.getInstance().setSeed(48290483L);

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

        List<Node> cluster2 = new ArrayList<Node>();
        cluster2.add(x7);
        cluster2.add(x8);
        cluster2.add(x9);
        cluster2.add(x10);
        cluster2.add(x11);
        cluster2.add(x12);

        partition.add(cluster1);
        partition.add(cluster2);

        TetradTest test = new ContinuousTetradTest(data, TestType.TETRAD_WISHART, 0.0001);
        IPurify purify = new PurifyTetradBased2(test);
        purify.setTrueGraph(graph);

        List<List<Node>> clustering = purify.purify(partition);

        assertEquals(4, clustering.get(0).size());
        assertEquals(5, clustering.get(1).size());

    }

    @Test
    public void test2() {
        RandomUtil.getInstance().setSeed(48290483L);

        Graph graph = new EdgeListGraph(DataGraphUtils.randomSingleFactorModel(3, 3, 5, 0, 0, 0));

        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);
        DataSet data = im.simulateData(1000, false);

        List<Node> latents = new ArrayList<Node>();

        for (Node node : graph.getNodes()) {
            if (node.getNodeType() == NodeType.LATENT) latents.add(node);
        }

        Graph structuralGraph = graph.subgraph(latents);

        List<List<Node>> clustering = new ArrayList<List<Node>>();

        for (Node node : latents) {
            List<Node> adj = graph.getAdjacentNodes(node);
            adj.removeAll(latents);

            clustering.add(adj);
        }

        ContinuousTetradTest test = new ContinuousTetradTest(data, TestType.TETRAD_WISHART, 0.001);

        IPurify purify = new PurifyTetradBased2(test);

        List<List<Node>> purifiedClustering = purify.purify(clustering);
        List<String> latentsNames = new ArrayList<String>();

        for (int i = 0; i < latents.size(); i++) {
            latentsNames.add(latents.get(i).getName());
        }

        Mimbuild2 mimbuild = new Mimbuild2();
        mimbuild.setAlpha(0.0001);
        Graph _graph = mimbuild.search(purifiedClustering, latentsNames, new CovarianceMatrix(data));

        List<Node> _latents = new ArrayList<Node>();

        for (Node node : _graph.getNodes()) {
            if (node.getNodeType() == NodeType.LATENT) _latents.add(node);
        }

        Graph _structuralGraph = _graph.subgraph(_latents);

        assertEquals(3, _structuralGraph.getNumEdges());


    }
}


