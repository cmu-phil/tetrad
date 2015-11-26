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
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.sem.LargeSemSimulator;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests the MeasurementSimulator class using diagnostics devised by Richard
 * Scheines. The diagnostics are described in the Javadocs, below.
 *
 * @author Joseph Ramsey
 */
public class TestLargeSemSimulator extends TestCase {

    /**
     * Standard constructor for JUnit test cases.
     */
    public TestLargeSemSimulator(String name) {
        super(name);
    }

    public void test1() {
        List<Node> nodes = new ArrayList<Node>();
        for (int i = 1; i <= 10; i++) nodes.add(new ContinuousVariable("X" + i));

        Graph graph = GraphUtils.randomGraph(nodes, 0, 10, 5, 5, 5, false);

        List<Node> nodes2 = graph.getCausalOrdering();
        int[] tierIndices = new int[nodes2.size()];
        for (int j = 0; j < nodes.size(); j++) {
            tierIndices[j] = j;
        }
        LargeSemSimulator simulator = new LargeSemSimulator(graph);
        DataSet dataset = simulator.simulateDataAcyclicConcurrent(1000);

        System.out.println(dataset);
    }

    private Dag constructGraph1() {
        Dag graph = new Dag();

        Node x1 = new ContinuousVariable("X1");
        Node x2 = new ContinuousVariable("X2");
        Node x3 = new ContinuousVariable("X3");
        Node x4 = new ContinuousVariable("X4");
        Node x5 = new ContinuousVariable("X5");

        x1.setNodeType(NodeType.LATENT);
        x2.setNodeType(NodeType.LATENT);

        graph.addNode(x1);
        graph.addNode(x2);
        graph.addNode(x3);
        graph.addNode(x4);
        graph.addNode(x5);

        graph.addDirectedEdge(x1, x2);
        graph.addDirectedEdge(x2, x3);
        graph.addDirectedEdge(x3, x4);
        graph.addDirectedEdge(x1, x4);
        graph.addDirectedEdge(x4, x5);

        return graph;
    }

//    private Graph constructGraph2() {
//        Graph graph = new EdgeListGraph();
//
//        Node x1 = new GraphNode("X1");
//        Node x2 = new GraphNode("X2");
//        Node x3 = new GraphNode("X3");
//        Node x4 = new GraphNode("X4");
//        Node x5 = new GraphNode("X5");
//
//        graph.addIndex(x1);
//        graph.addIndex(x2);
//        graph.addIndex(x3);
//        graph.addIndex(x4);
//        graph.addIndex(x5);
//
//        graph.addDirectedEdge(x1, x2);
//        graph.addDirectedEdge(x2, x3);
//        graph.addDirectedEdge(x3, x4);
//        graph.addDirectedEdge(x1, x4);
//        graph.addDirectedEdge(x4, x5);
//
//        return graph;
//    }

    /**
     * This method uses reflection to collect up all of the test methods from
     * this class and return them to the test runner.
     */
    public static Test suite() {

        // Edit the name of the class in the parens to match the name
        // of this class.
        return new TestSuite(TestLargeSemSimulator.class);
    }
}





