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
 * Tests the functions of EndpointMatrixGraph through the
 * Graph interface.
 *
 * @author Joseph Ramsey
 */
public final class TestEndpointMatrixGraph {
    private Node x1, x2, x3, x4, x5;
    private Graph graph;

    public void setUp() {
        this.x1 = new GraphNode("x1");
        this.x2 = new GraphNode("x2");
        this.x3 = new GraphNode("x3");
        this.x4 = new GraphNode("x4");
        this.x5 = new GraphNode("x5");
//        graph = new EdgeListGraph();
        this.graph = new EndpointMatrixGraph();
    }

    @Test
    public void testSequence1() {
        setUp();

        this.graph.clear();

        // Add and remove some nodes.
        this.graph.addNode(this.x1);
        this.graph.addNode(this.x2);
        this.graph.addNode(this.x3);
        this.graph.addNode(this.x4);
        this.graph.addNode(this.x5);

        this.graph.addDirectedEdge(this.x1, this.x2);
        this.graph.addDirectedEdge(this.x2, this.x3);
        this.graph.addDirectedEdge(this.x3, this.x4);

        final List<Node> children = this.graph.getChildren(this.x1);
        final List<Node> parents = this.graph.getParents(this.x4);

        assertEquals(children, Collections.singletonList(this.x2));
        assertEquals(parents, Collections.singletonList(this.x3));

        assertTrue(this.graph.isDConnectedTo(this.x1, this.x3, Collections.EMPTY_LIST));
        this.graph.removeNode(this.x2);

        // No cycles.
        assertTrue(!this.graph.existsDirectedCycle());

        // Copy the graph.
        final Graph graph2 = new EndpointMatrixGraph(this.graph);
        assertEquals(this.graph, graph2);

        final Graph graph3 = new EndpointMatrixGraph(this.graph);
        assertEquals(this.graph, graph3);
    }

    public void testSequence2() {
        setUp();

        this.graph.clear();

        // Add some edges in a cycle.
        this.graph.addNode(this.x1);
        this.graph.addNode(this.x2);
        this.graph.addNode(this.x3);
        this.graph.addNode(this.x4);
        this.graph.addNode(this.x5);

        assertTrue(!this.graph.existsDirectedCycle());

        this.graph.addDirectedEdge(this.x1, this.x3);

        try {
            this.graph.addDirectedEdge(this.x1, this.x3);
        } catch (final IllegalArgumentException e) {
            fail("This should have been ignored.");
        }

        this.graph.addDirectedEdge(this.x3, this.x4);
        this.graph.addDirectedEdge(this.x4, this.x1);
        this.graph.addDirectedEdge(this.x1, this.x2);
        this.graph.addDirectedEdge(this.x2, this.x3);
        this.graph.addDirectedEdge(this.x3, this.x5);
        this.graph.addDirectedEdge(this.x5, this.x2);

        this.graph.setEndpoint(this.x4, this.x3, Endpoint.ARROW);
        this.graph.setEndpoint(this.x3, this.x4, Endpoint.ARROW);

        assertTrue(this.graph.existsDirectedCycle());
    }

    @Test
    public void testSequence4() {
        setUp();

        this.graph.clear();

        // Add some edges in a cycle.
        this.graph.addNode(this.x1);
        this.graph.addNode(this.x2);

        this.graph.addUndirectedEdge(this.x1, this.x2);

        final List<Edge> edges = new ArrayList<>(this.graph.getEdges());

        final Edge e1 = edges.get(0);

        final Edge e2 = new Edge(this.x2, this.x1, Endpoint.TAIL, Endpoint.TAIL);

        assertTrue(e1.equals(e2));

        assertTrue(e1.hashCode() == e2.hashCode());
    }

    @Test
    public void test5() {
        final Graph graph1 = GraphUtils.emptyGraph(3);

        final List<Node> nodes = graph1.getNodes();

        graph1.addDirectedEdge(nodes.get(0), nodes.get(1));
        graph1.addDirectedEdge(nodes.get(1), nodes.get(2));
        graph1.addDirectedEdge(nodes.get(0), nodes.get(2));

        final Graph graph2 = new EndpointMatrixGraph(graph1);

        graph2.removeEdge(nodes.get(0), nodes.get(1));

        final int shd = SearchGraphUtils.structuralHammingDistance(graph1, graph2);

        assertEquals(3, shd);
    }
}





