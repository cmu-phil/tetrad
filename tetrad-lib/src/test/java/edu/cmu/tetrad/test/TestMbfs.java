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
import edu.cmu.tetrad.search.IndTestDSep;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.MbUtils;
import edu.cmu.tetrad.search.Mbfs;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestMbfs {

    /**
     * Tests to make sure the algorithm for generating MB DAGs from an MB Pattern works, at least for one kind of tricky
     * case.
     */
    @Test
    public void testGenerateDaglist() {
        Graph graph = GraphConverter.convert("T-->X1,T-->X2,X1-->X2,T-->X3,X4-->T");

        IndTestDSep test = new IndTestDSep(graph);
        Mbfs search = new Mbfs(test, -1);
        Graph resultGraph = search.search("T");

        List mbDags = MbUtils.generateMbDags(resultGraph, true,
                search.getTest(), search.getDepth(), search.getTarget());

        assertTrue(mbDags.size() == 9);
        assertTrue(mbDags.contains(graph));
    }

    @Test
    public void testRandom() {
        RandomUtil.getInstance().setSeed(8388428832L);

        List<Node> nodes1 = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            nodes1.add(new ContinuousVariable("X" + (i + 1)));
        }

        Dag dag = new Dag(GraphUtils.randomGraph(nodes1, 0, 10,
                5, 5, 5, false));

        IndependenceTest test = new IndTestDSep(dag);
        Mbfs search = new Mbfs(test, -1);

        List<Node> nodes = dag.getNodes();

        for (Node node : nodes) {
            Graph resultMb = search.search(node.getName());
            Graph trueMb = GraphUtils.markovBlanketDag(node, dag);

            List<Node> resultNodes = resultMb.getNodes();
            List<Node> trueNodes = trueMb.getNodes();

            Set<String> resultNames = new HashSet<>();

            for (Node resultNode : resultNodes) {
                resultNames.add(resultNode.getName());
            }

            Set<String> trueNames = new HashSet<>();

            for (Node v : trueNodes) {
                trueNames.add(v.getName());
            }

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
                        fail("Node " + name1 + " is not in the true graph.");
                    }

                    if (node2 == null) {
                        fail("Node " + name2 + " is not in the true graph.");
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
                }
            }
        }
    }
}





