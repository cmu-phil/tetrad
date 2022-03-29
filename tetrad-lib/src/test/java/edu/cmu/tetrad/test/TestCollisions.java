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
import org.junit.Test;

import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.assertTrue;


/**
 * Tests the functions of the (non)/collider detection methods in Graph.
 *
 * @author Erin Korber
 */
public final class TestCollisions {

    @Test
    public void testMatrixGraph() {
        checkGraph(new EndpointMatrixGraph());
    }

    @Test
    public void testListGraph() {
        checkGraph(new EdgeListGraph());
    }

    private void checkGraph(Graph graph) {
        Node x1 = new GraphNode("x1");
        Node x2 = new GraphNode("x2");
        Node x3 = new GraphNode("x3");

        graph.addNode(x1);
        graph.addNode(x2);
        graph.addNode(x3);

        graph.addDirectedEdge(x1, x2);
        graph.addDirectedEdge(x3, x2);

        //basic unshielded collider
        assertTrue(graph.isDefCollider(x1, x2, x3));

        graph.clear();

        graph.addNode(x1);
        graph.addNode(x2);
        graph.addNode(x3);

        graph.addDirectedEdge(x2, x1);
        graph.addUndirectedEdge(x2, x3);

        //one edge out from x2
        assertTrue(graph.isDefNoncollider(x1, x2, x3));

        graph.clear();

        graph.addNode(x1);
        graph.addNode(x2);
        graph.addNode(x3);

        graph.addUndirectedEdge(x1, x3);
        graph.addDirectedEdge(x1, x2);
        graph.addUndirectedEdge(x3, x2);

        //shielded, one edge in
        assertFalse(graph.isDefNoncollider(x1, x2, x3));
        assertFalse(graph.isDefCollider(x1, x2, x3));

    }
}





