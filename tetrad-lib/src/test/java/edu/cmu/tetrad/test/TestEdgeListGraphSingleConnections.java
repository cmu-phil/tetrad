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
import edu.cmu.tetrad.util.RandomUtil;
import junit.framework.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Joseph Ramsey
 */
public final class TestEdgeListGraphSingleConnections {
    private Node x1, x2, x3, x4, x5;
    private Graph graph;

    public void setUp() {
        x1 = new GraphNode("x1");
        x2 = new GraphNode("x2");
        x3 = new GraphNode("x3");
        x4 = new GraphNode("x4");
        x5 = new GraphNode("x5");
        graph = new EdgeListGraphSingleConnections();
    }

    @Test
    public void testSequence1() {
        setUp();

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

    @Test
    public void testSequence2() {
        setUp();

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
            fail("Shouldn't have been able to add an edge already in the graph.");
        } catch (Exception e1) {
        }

        graph.addDirectedEdge(x3, x4);
        graph.addDirectedEdge(x4, x1);
        graph.addDirectedEdge(x1, x2);
        graph.addDirectedEdge(x2, x3);
        graph.addDirectedEdge(x3, x5);
        graph.addDirectedEdge(x5, x2);

        graph.setEndpoint(x4, x3, Endpoint.ARROW);
        graph.setEndpoint(x3, x4, Endpoint.ARROW);


        assertTrue(graph.existsDirectedCycle());

        graph.removeEdge(x1, x3);
        graph.removeEdge(graph.getEdge(x5, x2));
    }

    @Test
    public void testSequence3() {
        List<Node> nodes = new ArrayList<Node>();

        for (int i1 = 0; i1 < 50; i1++) {
            nodes.add(new ContinuousVariable("X" + (i1 + 1)));
        }

        Graph graph = new Dag(GraphUtils.randomGraph(nodes, 0, 50,
                30, 15, 15, false));

        Node node1 = graph.getNodes().get(0);
        Node node2 = graph.getNodes().get(1);
        List<Node> cond = new ArrayList<Node>();
        for (int i= 2; i < 5; i++) {
            cond.add(graph.getNodes().get(i));
        }

        boolean dsep = graph.isDSeparatedFrom(node1, node2, cond);
    }

    @Test
    public void testSequence4() {
        setUp();
        graph.clear();

        // Add some edges in a cycle.
        graph.addNode(x1);
        graph.addNode(x2);

        graph.addUndirectedEdge(x1, x2);

        List<Edge> edges = new ArrayList<Edge>(graph.getEdges());

        Edge e1 = edges.get(0);

        Edge e2 = new Edge(x2, x1, Endpoint.TAIL, Endpoint.TAIL);

        assertTrue(e1.equals(e2));

        assertTrue(e1.hashCode() == e2.hashCode());

        graph.removeEdge(e2);

        edges = new ArrayList<Edge>(graph.getEdges());
    }

    @Test
    public void testSepsets() {
        List<Node> nodes = new ArrayList<Node>();
        for (int i = 0; i < 50; i++) nodes.add(new ContinuousVariable("X" + (i + 1)));

        Graph graph = GraphUtils.randomGraphRandomForwardEdges(nodes, 0, nodes.size(), 30, 15, 15, false);

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

    @Test
    public void stressTest() {
        int numNodes = 30;
        int numEdges = 100;

        List<Node> nodes = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            nodes.add(new GraphNode("X" + (i + 1)));
        }

        EdgeListGraphSingleConnections graph1 = new EdgeListGraphSingleConnections(nodes);
        EdgeListGraph graph2 = new EdgeListGraph(nodes);

        for (int i = 0; i < numEdges; i++) {
            int t1 = RandomUtil.getInstance().nextInt(numNodes);
            int t2 = RandomUtil.getInstance().nextInt(numNodes);
            if (t1 == t2) continue;

            if (graph1.isAdjacentTo(nodes.get(t1), nodes.get(t2))) {
                continue;
            }

            Edge edge = Edges.directedEdge(nodes.get(t1), nodes.get(t2));

            graph1.addEdge(edge);
            graph2.addEdge(edge);
            assertEquals(graph1, graph2);
        }
    }
}





