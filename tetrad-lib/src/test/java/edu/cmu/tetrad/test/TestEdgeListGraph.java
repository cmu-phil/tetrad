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
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests the functions of EndpointMatrixGraph and EdgeListGraph through the
 * Graph interface.
 *
 * @author Joseph Ramsey
 */
public final class TestEdgeListGraph {
    private Node x1, x2, x3, x4, x5;
    private Graph graph;

    public void setUp() {
        x1 = new GraphNode("x1");
        x2 = new GraphNode("x2");
        x3 = new GraphNode("x3");
        x4 = new GraphNode("x4");
        x5 = new GraphNode("x5");
        graph = new EdgeListGraph();
        //        graph = new EndpointMatrixGraph();
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
        Graph graph2 = new EdgeListGraph(graph);
        assertEquals(graph, graph2);

        Graph graph3 = new EdgeListGraph(graph);
        assertEquals(graph, graph3);
    }

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

        graph.setEndpoint(x4, x3, Endpoint.ARROW);
        graph.setEndpoint(x3, x4, Endpoint.ARROW);

        assertTrue(graph.existsDirectedCycle());
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
    }

    @Test
    public void test5() {
        Graph graph1 = GraphUtils.emptyGraph(3);

        List<Node> nodes = graph1.getNodes();

        graph1.addDirectedEdge(nodes.get(0), nodes.get(1));
        graph1.addDirectedEdge(nodes.get(1), nodes.get(2));
        graph1.addDirectedEdge(nodes.get(0), nodes.get(2));

        Graph graph2 = new EdgeListGraph(graph1);

        graph2.removeEdge(nodes.get(0), nodes.get(1));

        int shd = SearchGraphUtils.structuralHammingDistance(graph1, graph2);

        assertEquals(2, shd);
    }
}





