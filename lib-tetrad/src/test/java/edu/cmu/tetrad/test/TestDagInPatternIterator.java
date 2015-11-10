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

import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.DagInPatternIterator;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradLogger;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.util.List;

/**
 * Tests the BooleanFunction class.
 *
 * @author Joseph Ramsey
 */
public class TestDagInPatternIterator extends TestCase {

    /**
     * Standard constructor for JUnit test cases.
     */
    public TestDagInPatternIterator(String name) {
        super(name);
    }

    public void test1() {
        Dag dag = new Dag(GraphUtils.randomGraph(10, 0, 10, 3,
                3, 3, false));

        System.out.println("DAG " + dag);

        Graph pattern = SearchGraphUtils.patternFromDag(dag);

        System.out.println("Pattern " + pattern);

        DagInPatternIterator iterator = new DagInPatternIterator(pattern);

        while (iterator.hasNext()) {
            System.out.println(iterator.next());
        }

    }

    public void test2() {
        Graph pattern = new EdgeListGraph();
        Node x = new GraphNode("X");
        Node y = new GraphNode("Y");
        pattern.addNode(x);
        pattern.addNode(y);
        pattern.addDirectedEdge(x, y);

        DagInPatternIterator iterator = new DagInPatternIterator(pattern);

        while (iterator.hasNext()) {
            System.out.println(iterator.next());
        }
    }

    public void test3() {
        TetradLogger.getInstance().addOutputStream(System.out);
        TetradLogger.getInstance().setForceLog(true);

        Graph pattern = new EdgeListGraph();

        Node x1 = new GraphNode("X1");
        Node x2 = new GraphNode("X2");
        Node x3 = new GraphNode("X3");
        Node x4 = new GraphNode("X4");
        Node x5 = new GraphNode("X5");
        Node x6 = new GraphNode("X6");

        pattern.addNode(x1);
        pattern.addNode(x2);
        pattern.addNode(x3);
        pattern.addNode(x4);
        pattern.addNode(x5);
        pattern.addNode(x6);

        pattern.addDirectedEdge(x5, x1);
        pattern.addDirectedEdge(x3, x1);
        pattern.addDirectedEdge(x3, x4);
        pattern.addDirectedEdge(x6, x5);
        pattern.addUndirectedEdge(x1, x6);
        pattern.addUndirectedEdge(x4, x6);

        DagInPatternIterator iterator = new DagInPatternIterator(pattern);

        while (iterator.hasNext()) {
            System.out.println(iterator.next());
        }
    }

    public void test4() {
        TetradLogger.getInstance().addOutputStream(System.out);
        TetradLogger.getInstance().setForceLog(true);

        Graph pattern = new EdgeListGraph();

        Node x1 = new GraphNode("X1");
        Node x2 = new GraphNode("X2");
        Node x3 = new GraphNode("X3");
        Node x4 = new GraphNode("X4");
        Node x5 = new GraphNode("X5");
        Node x6 = new GraphNode("X6");

        pattern.addNode(x1);
        pattern.addNode(x2);
        pattern.addNode(x3);
        pattern.addNode(x4);
        pattern.addNode(x5);
        pattern.addNode(x6);

        pattern.addDirectedEdge(x5, x1);
        pattern.addDirectedEdge(x3, x1);
        pattern.addDirectedEdge(x3, x4);
        pattern.addDirectedEdge(x6, x5);
        pattern.addUndirectedEdge(x1, x6);
        pattern.addUndirectedEdge(x4, x6);

        DagInPatternIterator iterator = new DagInPatternIterator(pattern);

        while (iterator.hasNext()) {
            System.out.println(iterator.next());
        }
    }

    public void test5() {
        Dag dag1 = new Dag(GraphUtils.randomGraph(3, 0, 3, 30, 15, 15, false));

        System.out.println(dag1);

        Graph pattern = SearchGraphUtils.patternForDag(dag1);
        System.out.println(pattern);
        List<Node> nodes = pattern.getNodes();

        // Make random knowedge.
        int numTiers = 6;
        IKnowledge knowledge = new Knowledge2();

        for (Node node : nodes) {
            int tier = RandomUtil.getInstance().nextInt(numTiers);
            if (tier < 2) continue;
            knowledge.addToTier(tier, node.getName());
        }

        System.out.println(knowledge);

        if (!knowledge.isViolatedBy(pattern)) {
            DagInPatternIterator iterator1 = new DagInPatternIterator(pattern);
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

        if (!knowledge.isViolatedBy(pattern)) {
            DagInPatternIterator iterator2 = new DagInPatternIterator(pattern, knowledge);

            while (iterator2.hasNext()) {
                Graph dag = iterator2.next();

                if (knowledge.isViolatedBy(dag)) {
                    System.out.println(dag);
                    throw new IllegalArgumentException("Knowledge violated");
                }
            }
        }

        System.out.println("Pattern " + pattern);

        DagInPatternIterator iterator3 = new DagInPatternIterator(pattern);
        int count = 0;

        while (iterator3.hasNext()) {
            Graph dag = iterator3.next();
            System.out.println(dag);
            count++;
        }

        assertEquals(6, count);
    }

    /**
     * This method uses reflection to collect up all of the test methods from this class and return them to the test
     * runner.
     */
    public static Test suite() {

        // Edit the name of the class in the parens to match the name
        // of this class.
        return new TestSuite(TestDagInPatternIterator.class);
    }
}



