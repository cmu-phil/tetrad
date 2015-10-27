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
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Tests the GraphConverter class.
 *
 * @author Joseph Ramsey
 */
public class TestGraphConverter extends TestCase {

    /**
     * Standard constructor for JUnit test cases.
     */
    public TestGraphConverter(String name) {
        super(name);
    }

    /**
     * Contructs a graph the long way and using the string spec converter and compares them.
     */
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

        Graph convertedGraph = GraphConverter.convert(
                "X1-->X2,X1---X3,X2<->X4,X3o->X4," + "X5<--X1,X5o-oX2,X5<-oX3");

        convertedGraph = GraphUtils.replaceNodes(convertedGraph, graph.getNodes());
        //
        //        System.out.println(graph);
        //        System.out.println(convertedGraph);
        assertTrue(graph.equals(convertedGraph));
    }

    /**
     * This method uses reflection to collect up all of the test methods from this class and return them to the test
     * runner.
     */
    public static Test suite() {

        // Edit the name of the class in the parens to match the name
        // of this class.
        return new TestSuite(TestGraphConverter.class);
    }
}





