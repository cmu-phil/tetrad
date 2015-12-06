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

import java.util.Collections;
import java.util.List;

/**
 * Tests the functions of EndpointMatrixGraph and EdgeListGraph through the
 * Graph interface.
 *
 * @author Joseph Ramsey
 */
public final class TestDag extends TestCase {

    /**
     * Standard constructor for JUnit test cases.
     */
    public TestDag(String name) {
        super(name);
    }

    public void testSearchGraph() {
        checkGraph(new Dag());
    }

    public void testDag() {
        checkGraph(new Dag());
    }

    private void checkGraph(Dag graph) {
        checkAddRemoveNodes(graph);
        checkCopy(graph);
    }

    private void checkAddRemoveNodes(Dag graph) {
        Node x1 = new GraphNode("x1");
        Node x2 = new GraphNode("x2");
        Node x3 = new GraphNode("x3");
        Node x4 = new GraphNode("x4");
        Node x5 = new GraphNode("x5");

        graph.addNode(x1);
        graph.addNode(x2);
        graph.addNode(x3);
        graph.addNode(x4);
        graph.addNode(x5);

        graph.addDirectedEdge(x1, x2);
        graph.addDirectedEdge(x2, x3);
        graph.addDirectedEdge(x3, x4);
        graph.addDirectedEdge(x5, x4);

        System.out.println(graph);

        List<Node> children = graph.getChildren(x1);
        List<Node> parents = graph.getParents(x4);

        System.out.println("Children of x1 = " + children);
        System.out.println("Parents of x4 = " + parents);

        assertTrue(graph.isDConnectedTo(x1, x3, Collections.EMPTY_LIST));

        //graph.removeNode(x2);
        //
        //System.out.println("Without x2: " + graph);


        assertTrue(graph.existsDirectedPathFromTo(x1, x4));
        assertTrue(!graph.existsDirectedPathFromTo(x1, x5));

        assertTrue(graph.isAncestorOf(x2, x4));
        assertTrue(!graph.isAncestorOf(x4, x2));

        assertTrue(graph.isDescendentOf(x4, x2));
        assertTrue(!graph.isDescendentOf(x2, x4));

    }

    private void checkCopy(Graph graph) {
        Graph graph2 = new Dag(graph);
        assertEquals(graph, graph2);
    }

    /**
     * This method uses reflection to collect up all of the test methods from
     * this class and return them to the test runner.
     */
    public static Test suite() {

        // Edit the name of the class in the parens to match the name
        // of this class.
        return new TestSuite(TestDag.class);
    }
}





