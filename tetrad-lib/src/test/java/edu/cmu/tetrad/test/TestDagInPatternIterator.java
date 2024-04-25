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
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.utils.DagInCpcagIterator;
import edu.cmu.tetrad.util.RandomUtil;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author josephramsey
 */
public class TestDagInPatternIterator {

    //    @Test
    public void test1() {
        List<Node> nodes = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            nodes.add(new GraphNode("X" + (i + 1)));
        }

        RandomUtil.getInstance().setSeed(342233L);
        Graph graph = RandomGraph.randomGraphRandomForwardEdges(nodes, 0, 10, 3,
                3, 3, false, true);
        Graph p = new EdgeListGraph(graph);

        Dag dag = new Dag(graph);


        Graph CPDAG = GraphTransforms.dagToCpdag(graph);

        System.out.println(CPDAG);


        DagInCpcagIterator iterator = new DagInCpcagIterator(CPDAG);
        int count = 0;

        while (iterator.hasNext()) {
            iterator.next();
            count++;
        }

        assertEquals(6, count);
    }

    //    @Test
    public void test2() {
        Graph CPDAG = new EdgeListGraph();
        Node x = new GraphNode("X");
        Node y = new GraphNode("Y");
        CPDAG.addNode(x);
        CPDAG.addNode(y);
        CPDAG.addDirectedEdge(x, y);

        DagInCpcagIterator iterator = new DagInCpcagIterator(CPDAG);
        int count = 0;

        while (iterator.hasNext()) {
            iterator.next();
            count++;
        }

        assertEquals(1, count);
    }

    //    @Test
    public void test3() {
        Graph CPDAG = new EdgeListGraph();

        Node x1 = new GraphNode("X1");
        Node x2 = new GraphNode("X2");
        Node x3 = new GraphNode("X3");
        Node x4 = new GraphNode("X4");
        Node x5 = new GraphNode("X5");
        Node x6 = new GraphNode("X6");

        CPDAG.addNode(x1);
        CPDAG.addNode(x2);
        CPDAG.addNode(x3);
        CPDAG.addNode(x4);
        CPDAG.addNode(x5);
        CPDAG.addNode(x6);

        CPDAG.addDirectedEdge(x5, x1);
        CPDAG.addDirectedEdge(x3, x1);
        CPDAG.addDirectedEdge(x3, x4);
        CPDAG.addDirectedEdge(x6, x5);
        CPDAG.addUndirectedEdge(x1, x6);
        CPDAG.addUndirectedEdge(x4, x6);

        DagInCpcagIterator iterator = new DagInCpcagIterator(CPDAG);
        int count = 0;

        while (iterator.hasNext()) {
            iterator.next();
            count++;
        }

        assertEquals(1, count);
    }

    //    @Test
    public void test4() {
        Graph CPDAG = new EdgeListGraph();

        Node x1 = new GraphNode("X1");
        Node x2 = new GraphNode("X2");
        Node x3 = new GraphNode("X3");
        Node x4 = new GraphNode("X4");
        Node x5 = new GraphNode("X5");
        Node x6 = new GraphNode("X6");

        CPDAG.addNode(x1);
        CPDAG.addNode(x2);
        CPDAG.addNode(x3);
        CPDAG.addNode(x4);
        CPDAG.addNode(x5);
        CPDAG.addNode(x6);

        CPDAG.addDirectedEdge(x5, x1);
        CPDAG.addDirectedEdge(x3, x1);
        CPDAG.addDirectedEdge(x3, x4);
        CPDAG.addDirectedEdge(x6, x5);
        CPDAG.addUndirectedEdge(x1, x6);
        CPDAG.addUndirectedEdge(x4, x6);

        DagInCpcagIterator iterator = new DagInCpcagIterator(CPDAG);
        int count = 0;

        while (iterator.hasNext()) {
            iterator.next();
            count++;
        }

        assertEquals(1, count);
    }

    //    @Test
    public void test5() {
        RandomUtil.getInstance().setSeed(34828384L);

        List<Node> nodes1 = new ArrayList<>();

        for (int i = 0; i < 3; i++) {
            nodes1.add(new ContinuousVariable("X" + (i + 1)));
        }

        Dag dag1 = new Dag(RandomGraph.randomGraph(nodes1, 0, 3,
                30, 15, 15, false));

        Graph CPDAG = GraphTransforms.dagToCpdag(dag1);
        List<Node> nodes = CPDAG.getNodes();

        // Make random knowedge.
        final int numTiers = 6;
        Knowledge knowledge = new Knowledge();

        for (Node node : nodes) {
            int tier = RandomUtil.getInstance().nextInt(numTiers);
            if (tier < 2) continue;
            knowledge.addToTier(tier, node.getName());
        }

        if (!knowledge.isViolatedBy(CPDAG)) {
            DagInCpcagIterator iterator1 = new DagInCpcagIterator(CPDAG);
            Graph dag0 = null;

            while (iterator1.hasNext()) {
                Graph dag = iterator1.next();

                if (!knowledge.isViolatedBy(dag)) {
                    dag0 = dag;
                }
            }

            if (dag0 == null) {
                fail("Inconsistent knowledge.");
            }
        }

        if (!knowledge.isViolatedBy(CPDAG)) {
            DagInCpcagIterator iterator2 = new DagInCpcagIterator(CPDAG, knowledge);

            while (iterator2.hasNext()) {
                Graph dag = iterator2.next();

                if (knowledge.isViolatedBy(dag)) {
                    throw new IllegalArgumentException("Knowledge violated");
                }
            }
        }

        DagInCpcagIterator iterator3 = new DagInCpcagIterator(CPDAG);
        int count = 0;

        while (iterator3.hasNext()) {
            iterator3.next();
            count++;
        }

        assertEquals(6, count);
    }
}



