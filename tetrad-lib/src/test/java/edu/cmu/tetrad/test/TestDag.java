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

import edu.cmu.tetrad.graph.Dag;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.Node;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests the functions of EndpointMatrixGraph and EdgeListGraph through the Graph interface.
 *
 * @author josephramsey
 */
public final class TestDag {

    @Test
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

        List<Node> children = graph.getChildren(x1);
        List<Node> parents = graph.getParents(x4);

        assertTrue(children.contains(x2));
        assertTrue(parents.contains(x3));
        assertTrue(parents.contains(x5));

        assertTrue(!graph.paths().isMSeparatedFrom(x1, x3, Collections.EMPTY_SET, false));

        assertTrue(graph.paths().existsDirectedPath(x1, x4));
        assertFalse(graph.paths().existsDirectedPath(x1, x5));

        assertTrue(graph.paths().isAncestorOf(x2, x4));
        assertFalse(graph.paths().isAncestorOf(x4, x2));

        assertTrue(graph.paths().isDescendentOf(x4, x2));
        assertFalse(graph.paths().isDescendentOf(x2, x4));
    }

    private void checkCopy(Graph graph) {
        Graph graph2 = new Dag(graph);
        assertEquals(graph, graph2);
    }
}






