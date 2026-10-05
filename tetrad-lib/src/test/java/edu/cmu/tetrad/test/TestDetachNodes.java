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

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.*;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests GraphUtils.detachNodes and EdgeListGraph(Graph, true): the copy has fresh nodes, equal to the originals (so
 * they index the same data columns) and in the same positions, with the structure carried over, and positions set
 * afterwards on either side are not seen on the other.
 *
 * @author josephramsey
 */
public class TestDetachNodes {

    @Test
    public void testDetach() {
        ContinuousVariable x = new ContinuousVariable("X");
        DiscreteVariable y = new DiscreteVariable("Y", List.of("a", "b", "c"));
        GraphNode l = new GraphNode("L");
        l.setNodeType(NodeType.LATENT);

        x.setCenter(10, 20);
        y.setCenter(30, 40);
        l.setCenter(50, 60);

        Graph graph = new EdgeListGraph(List.of(x, y, l));
        graph.addDirectedEdge(x, y);
        graph.addEdge(Edges.bidirectedEdge(y, l));
        graph.addAmbiguousTriple(x, y, l);
        graph.addAttribute("BIC", 1.5);

        Graph detached = GraphUtils.detachNodes(graph);

        // Fresh objects, equal to the originals, in the same positions.
        for (Node node : graph.getNodes()) {
            Node copy = detached.getNode(node.getName());
            assertNotNull(copy);
            assertNotSame(node, copy);
            assertEquals(node, copy);
            assertEquals(node.getNodeType(), copy.getNodeType());
            assertEquals(node.getCenterX(), copy.getCenterX());
            assertEquals(node.getCenterY(), copy.getCenterY());
        }

        // The latent is copied too, unlike replaceNodes.
        assertNotSame(l, detached.getNode("L"));

        // Discrete categories survive, so equality with the data's variable holds.
        assertEquals(y.getCategories(), ((DiscreteVariable) detached.getNode("Y")).getCategories());

        // Structure and attributes.
        assertEquals(2, detached.getNumEdges());
        assertTrue(detached.isDirectedFromTo(detached.getNode("X"), detached.getNode("Y")));
        assertTrue(Edges.isBidirectedEdge(detached.getEdge(detached.getNode("Y"), detached.getNode("L"))));
        assertEquals(1, detached.getAmbiguousTriples().size());
        assertEquals(1.5, detached.getAttribute("BIC"));

        // Positions set on the copy do not reach the originals, and vice versa.
        detached.getNode("X").setCenter(99, 99);
        assertEquals(10, x.getCenterX());
        y.setCenter(77, 77);
        assertEquals(30, detached.getNode("Y").getCenterX());
    }

    @Test
    public void testConstructor() {
        GraphNode a = new GraphNode("A");
        GraphNode b = new GraphNode("B");
        a.setCenter(1, 2);
        Graph graph = new EdgeListGraph(List.of(a, b));
        graph.addDirectedEdge(a, b);

        Graph shared = new EdgeListGraph(graph, false);
        Graph own = new EdgeListGraph(graph, true);

        assertSame(a, shared.getNode("A"));
        assertNotSame(a, own.getNode("A"));
        assertEquals(a, own.getNode("A"));
        assertEquals(1, own.getNode("A").getCenterX());
        assertTrue(own.isDirectedFromTo(own.getNode("A"), own.getNode("B")));

        own.getNode("A").setCenter(50, 50);
        assertEquals(1, a.getCenterX());
    }
}
