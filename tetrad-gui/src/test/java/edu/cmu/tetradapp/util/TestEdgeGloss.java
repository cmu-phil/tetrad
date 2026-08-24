/// ////////////////////////////////////////////////////////////////////////////
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
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.util;

import edu.cmu.tetrad.graph.*;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Pins the plain-language edge readings shown in the workbench edge tooltip (added 2026-8-24).
 */
public class TestEdgeGloss {

    private final Node x = new GraphNode("X");
    private final Node y = new GraphNode("Y");

    @Test
    public void directedEdgeReadsAsCause() {
        String s = EdgeGloss.describe(Edges.directedEdge(x, y));
        assertTrue(s, s.startsWith("X is a cause of Y"));
        assertTrue(s, s.contains("Y is not an ancestor of X"));
    }

    @Test
    public void directedEdgeReadingDoesNotDependOnNodeOrderInEdge() {
        // Y <-- X stored with X as node2.
        Edge e = new Edge(y, x, Endpoint.ARROW, Endpoint.TAIL);
        assertEquals(EdgeGloss.describe(Edges.directedEdge(x, y)), EdgeGloss.describe(e));
    }

    @Test
    public void partiallyOrientedEdgeNamesTheCircleSide() {
        String s = EdgeGloss.describe(Edges.partiallyOrientedEdge(x, y)); // X o-> Y
        assertTrue(s, s.startsWith("Y is not an ancestor of X"));
        assertTrue(s, s.contains("circle at X"));
        assertTrue(s, s.contains("latent confounder"));
        // Same edge stored the other way round gives the same reading.
        Edge flipped = new Edge(y, x, Endpoint.ARROW, Endpoint.CIRCLE);
        assertEquals(s, EdgeGloss.describe(flipped));
    }

    @Test
    public void bidirectedEdgeMentionsLatentConfounder() {
        String s = EdgeGloss.describe(Edges.bidirectedEdge(x, y));
        assertTrue(s, s.startsWith("Bidirected"));
        assertTrue(s, s.contains("latent confounder"));
    }

    @Test
    public void undirectedEdgeGivesBothCpdagAndPagReadings() {
        String s = EdgeGloss.describe(Edges.undirectedEdge(x, y));
        assertTrue(s, s.contains("CPDAG"));
        assertTrue(s, s.contains("PAG"));
        assertTrue(s, s.contains("selection bias"));
    }

    @Test
    public void nondirectedEdgeSaysBothEndsUndetermined() {
        String s = EdgeGloss.describe(Edges.nondirectedEdge(x, y));
        assertTrue(s, s.startsWith("Nondirected"));
        assertTrue(s, s.contains("both ends are undetermined"));
    }

    @Test
    public void nonstandardEndpointsGiveEmptyReading() {
        assertEquals("", EdgeGloss.describe(new Edge(x, y, Endpoint.NULL, Endpoint.ARROW)));
        assertEquals("", EdgeGloss.describe(new Edge(x, y, Endpoint.STAR, Endpoint.STAR)));
        assertEquals("", EdgeGloss.describe(null));
    }

    @Test
    public void readingsArePlainText() {
        for (Edge e : new Edge[]{Edges.directedEdge(x, y), Edges.undirectedEdge(x, y), Edges.bidirectedEdge(x, y),
                Edges.partiallyOrientedEdge(x, y), Edges.nondirectedEdge(x, y)}) {
            String s = EdgeGloss.describe(e);
            assertFalse(s, s.contains("<"));
            assertTrue(s, s.endsWith("."));
        }
    }
}
