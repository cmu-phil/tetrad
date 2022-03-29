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

import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.TimeLagGraph;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;


/**
 * @author Joseph Ramsey
 */
public final class TestTimeLagGraph {

    @Test
    public void test1() {
        TimeLagGraph graph = new TimeLagGraph();
        Node x0 = new GraphNode("X");
        Node y0 = new GraphNode("Y");
        assertTrue(graph.addNode(x0));
        assertTrue(graph.addNode(y0));
        assertFalse(graph.setMaxLag(2));
        Node x1 = graph.getNode("X", 1);
        assertTrue(graph.addDirectedEdge(x1, y0));
        assertTrue(graph.setMaxLag(4));
        assertTrue(graph.setNumInitialLags(2));
        assertFalse(graph.setMaxLag(3));
        assertTrue(graph.setMaxLag(5));
        Node y1 = graph.getNode("Y", 1);
        assertTrue(graph.addDirectedEdge(y1, x0));
        assertTrue(graph.setMaxLag(1));
        assertFalse(graph.setMaxLag(0));
        assertFalse(graph.removeHighLagEdges(0));
        assertTrue(graph.addDirectedEdge(x0, y0));
    }
}


