///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.test;

import edu.cmu.tetrad.graph.*;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Tests the GraphConverter class.
 *
 * @author josephramsey
 */
public class TestGraphConverter {

    /**
     * Contructs a graph the long way and using the string spec converter and compares them.
     */
    @Test
    public void testStringSpecConverter() {

        // Set up graph and node objects.
        Graph graph = new EdgeListGraph();
        Node x1 = new GraphNode("X1");
        Node x2 = new GraphNode("X2");
        Node x3 = new GraphNode("X3");
        Node x4 = new GraphNode("X4");
        Node x5 = new GraphNode("X5");

        // Construct graph from nodes.
        graph.addNode(x1);
        graph.addNode(x2);
        graph.addNode(x3);
        graph.addNode(x4);
        graph.addNode(x5);
        graph.addDirectedEdge(x1, x2);
        graph.addUndirectedEdge(x1, x3);
        graph.addBidirectedEdge(x2, x4);
        graph.addPartiallyOrientedEdge(x3, x4);
        graph.addDirectedEdge(x1, x5);
        graph.addNondirectedEdge(x5, x2);
        graph.addPartiallyOrientedEdge(x3, x5);

        Graph convertedGraph = GraphUtils.convert(
                "X1-->X2,X1---X3,X2<->X4,X3o->X4," + "X5<--X1,X5o-oX2,X5<-oX3");

        convertedGraph = GraphUtils.replaceNodes(convertedGraph, graph.getNodes());
        assertEquals(graph, convertedGraph);
    }
}






