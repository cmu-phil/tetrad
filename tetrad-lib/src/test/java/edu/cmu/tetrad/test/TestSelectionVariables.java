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
import edu.cmu.tetrad.util.PagCache;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Tests the functions of EndpointMatrixGraph and EdgeListGraph through the Graph interface.
 *
 * @author josephramsey
 */
public final class TestSelectionVariables {

    @Test
    public void testDag() {
        System.out.println("Checking Evie's selection bias graph unit test.");

        // Make a list of dag nodes named "X1", X2", "X3", "X4", "S"
        Node x1 = new GraphNode("X1");
        Node x2 = new GraphNode("X2");
        Node x3 = new GraphNode("X3");
        Node x4 = new GraphNode("X4");
        Node s = new GraphNode("S");

        List<Node> nodes = new java.util.ArrayList<>();
        nodes.add(x1);
        nodes.add(x2);
        nodes.add(x3);
        nodes.add(x4);
        nodes.add(s);

        s.setNodeType(NodeType.SELECTION);

        Graph dag = new EdgeListGraph(nodes);
        dag.addDirectedEdge(x1, x2);
        dag.addDirectedEdge(x2, x3);
        dag.addDirectedEdge(x3, x4);
        dag.addDirectedEdge(x1, s);
        dag.addDirectedEdge(x4, s);

//        DagToPag dagToPag = new DagToPag(dag);
//        Graph pag = dagToPag.convert();

        Graph pag = PagCache.getInstance().getPag(GraphTransforms.dagToMag(dag));

        nodes.remove(s);

        Graph truth = new EdgeListGraph(nodes);

        truth.addUndirectedEdge(x1, x2);
        truth.addUndirectedEdge(x2, x3);
        truth.addUndirectedEdge(x3, x4);
        truth.addUndirectedEdge(x1, x4);

        assertEquals(truth, pag);
    }
}






