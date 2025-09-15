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

import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.Node;
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
        checkGraph(new EdgeListGraph());
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






