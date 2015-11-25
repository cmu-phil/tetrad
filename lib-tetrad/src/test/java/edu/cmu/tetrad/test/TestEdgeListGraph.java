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
import edu.cmu.tetrad.search.SearchGraphUtils;
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
public final class TestEdgeListGraph extends TestCase {
    private Node x1, x2, x3, x4, x5;
    private Graph graph;

    /**
     * Standard constructor for JUnit test cases.
     */
    public TestEdgeListGraph(String name) {
        super(name);
    }

    public void setUp() {
        x1 = new GraphNode("x1");
        x2 = new GraphNode("x2");
        x3 = new GraphNode("x3");
        x4 = new GraphNode("x4");
        x5 = new GraphNode("x5");
        graph = new EdgeListGraph();
        //        graph = new EndpointMatrixGraph();
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
        Graph graph2 = new EdgeListGraph(graph);
        assertEquals(graph, graph2);

        Graph graph3 = new EdgeListGraph(graph);
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

        try {
            graph.addDirectedEdge(x1, x3);
        }
        catch (IllegalArgumentException e) {
            fail("This should have been ignored.");
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

//    public void testTemp() {
////        IndTestChiSquare indTest = new IndTestChiSquare(dataSet, 0.05);
//
//        Graph graph = DataGraphUtils.randomDag(5, 5, false);
//
//        SemPm pm = new SemPm(graph);
//        SemIm im = new SemIm(pm);
//        DataSet data = im.simulateData(1000, false);
//
//        graph = DataGraphUtils.undirectedGraph(graph);
//
//        System.out.println(graph);
//
//        IndependenceTest test = new IndTestFisherZ(data, 0.05);
//
//        Pc pcSearch = new Pc(test);
//        Graph g = pcSearch.search();
//
//        for (Edge edge : g.getEdges()) {
//            boolean adj = g.isAdjacentTo(edge.getNode1(), edge.getNode2());
//            System.out.println(adj);
//        }
//
//        List<Node> n = g.getNodes();
//
//        for (int i = 0; i < n.size(); i++) {
//            for (int j = i + 1; j < n.size();j++) {
//                System.out.println(n.get(i) + " " + n.get(j) + " " + g.isAdjacentTo(n.get(i), n.get(j)));
//            }
//        }
//
//
////        List n = g.getNodes();
////
////        String output = g.toString();
////        System.out.println("Result for window " + window + " " + numCasesInWindow);
////        System.out.print(output);
////        if(window == 0) System.out.println
////                ("Edge between " + nodes[1] + " and " + nodes[9] + " = " + g.isAdjacentTo(nodes[1], nodes[9]));
////        System.out.println();
//
//    }

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

    public void test5() {
        Graph graph1 = GraphUtils.emptyGraph(3);

        List<Node> nodes = graph1.getNodes();

        graph1.addDirectedEdge(nodes.get(0), nodes.get(1));
        graph1.addDirectedEdge(nodes.get(1), nodes.get(2));
        graph1.addDirectedEdge(nodes.get(0), nodes.get(2));

        Graph graph2 = new EdgeListGraph(graph1);

        graph2.removeEdge(nodes.get(0), nodes.get(1));

        System.out.println(graph1.toString());

        System.out.println("graph1 = " + graph1);
        System.out.println("graph2 = " + graph2);

        System.out.println(graph1.equals(graph2));

        int shd = SearchGraphUtils.structuralHammingDistance(graph1, graph2);
        System.out.println(Integer.toString(shd));

        assertEquals(2, shd);

    }

    /**
     * This method uses reflection to collect up all of the test methods from
     * this class and return them to the test runner.
     */
    public static Test suite() {

        // Edit the name of the class in the parens to match the name
        // of this class.
        return new TestSuite(TestEdgeListGraph.class);
    }
}





