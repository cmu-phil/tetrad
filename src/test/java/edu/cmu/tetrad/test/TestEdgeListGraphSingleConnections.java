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
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tests the functions of EndpointMatrixGraph and EdgeListGraph through the
 * Graph interface.
 *
 * @author Joseph Ramsey
 */
public final class TestEdgeListGraphSingleConnections extends TestCase {
    private Node x1, x2, x3, x4, x5;
    private Graph graph;

    /**
     * Standard constructor for JUnit test cases.
     */
    public TestEdgeListGraphSingleConnections(String name) {
        super(name);
    }

    public void setUp() {
        x1 = new GraphNode("x1");
        x2 = new GraphNode("x2");
        x3 = new GraphNode("x3");
        x4 = new GraphNode("x4");
        x5 = new GraphNode("x5");
        graph = new EdgeListGraphSingleConnections();
    }

    public void testSequence1() {
        graph.clear();

        // Add and remove some nodes.
        graph.addNode(x1);
        graph.addNode(x2);
        graph.addNode(x3);
        graph.addNode(x4);
        graph.addNode(x5);

        graph.addDirectedEdge(x1, x2);
        graph.addDirectedEdge(x2, x3);
        graph.addDirectedEdge(x3, x4);

        List<Node> children = graph.getChildren(x1);
        List<Node> parents = graph.getParents(x4);

        assertEquals(children, Collections.singletonList(x2));
        assertEquals(parents, Collections.singletonList(x3));

        assertTrue(graph.isDConnectedTo(x1, x3, Collections.EMPTY_LIST));
        graph.removeNode(x2);

        // No cycles.
        assertTrue(!graph.existsDirectedCycle());

        // Copy the graph.
//        Graph graph2 = new EdgeListGraphSingleConnections(graph);
        Graph graph2 = EdgeListGraphSingleConnections.shallowCopy((EdgeListGraphSingleConnections) graph);
        assertEquals(graph, graph2);

        Graph graph3 = new EdgeListGraphSingleConnections(graph);
        assertEquals(graph, graph3);
    }

    public void testSequence2() {
        graph.clear();

        // Add some edges in a cycle.
        graph.addNode(x1);
        graph.addNode(x2);
        graph.addNode(x3);
        graph.addNode(x4);
        graph.addNode(x5);

        assertTrue(!graph.existsDirectedCycle());

        graph.addDirectedEdge(x1, x3);

        if (graph.addDirectedEdge(x1, x3)) {
            fail("Shouldn't have been able to add an edge already in the graph.");
        }

        graph.addDirectedEdge(x3, x4);
        graph.addDirectedEdge(x4, x1);
        graph.addDirectedEdge(x1, x2);
        graph.addDirectedEdge(x2, x3);
        graph.addDirectedEdge(x3, x5);
        graph.addDirectedEdge(x5, x2);

        System.out.println("@1 " + graph);

        graph.setEndpoint(x4, x3, Endpoint.ARROW);
        System.out.println("@2 " + graph);
        graph.setEndpoint(x3, x4, Endpoint.ARROW);


        assertTrue(graph.existsDirectedCycle());

        graph.removeEdge(x1, x3);
        graph.removeEdge(graph.getEdge(x5, x2));

        System.out.println(graph);
    }

    public void testSequence3() {
        Graph graph = new Dag(GraphUtils.randomGraph(50, 0, 50, 30, 15, 15, false));

        Node node1 = graph.getNodes().get(0);
        Node node2 = graph.getNodes().get(1);
        List<Node> cond = new ArrayList<Node>();
        for (int i= 2; i < 5; i++) {
            cond.add(graph.getNodes().get(i));
        }

        boolean dsep = graph.isDSeparatedFrom(node1, node2, cond);

        System.out.println(dsep);
    }

    public void testSequence4() {
        graph.clear();

        // Add some edges in a cycle.
        graph.addNode(x1);
        graph.addNode(x2);

        graph.addUndirectedEdge(x1, x2);

        List<Edge> edges = new ArrayList<Edge>(graph.getEdges());

        System.out.println(edges);

        Edge e1 = edges.get(0);

        Edge e2 = new Edge(x2, x1, Endpoint.TAIL, Endpoint.TAIL);

        assertTrue(e1.equals(e2));

        assertTrue(e1.hashCode() == e2.hashCode());

        graph.removeEdge(e2);

        edges = new ArrayList<Edge>(graph.getEdges());

        System.out.println(edges);
    }

    public void testSepsets() {
        List<Node> nodes = new ArrayList<Node>();
        for (int i = 0; i < 50; i++) nodes.add(new ContinuousVariable("X" + (i + 1)));

        Graph graph = GraphUtils.randomGraphRandomForwardEdges(nodes, 0, nodes.size());

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                Node x = nodes.get(i);
                Node y = nodes.get(j);

                List<Node> sepset = graph.getSepset(x, y);

                if (!graph.isAdjacentTo(x, y)) {
                    if (sepset == null) {
                        fail("Expecting a sepset.");
                    }
                }
                else {
                    if (sepset != null) {
                        fail("Not expecting a sepset.");
                    }
                }
            }
        }

        for (Edge e : graph.getEdges()) {
            Node x = e.getNode1();
            Node y = e.getNode2();

            graph.removeEdge(e);

            List<Node> sepset = graph.getSepset(x, y);

            if (sepset == null) {
                fail("Expecting a sepset.");
            }

            graph.addEdge(e);
        }
    }

    /**
     * This method uses reflection to collect up all of the test methods from
     * this class and return them to the test runner.
     */
    public static Test suite() {

        // Edit the name of the class in the parens to match the name
        // of this class.
        return new TestSuite(TestEdgeListGraphSingleConnections.class);
    }
}





