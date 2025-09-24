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

import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.graph.*;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests the BayesPm.
 *
 * @author William Taysom
 */
public final class TestBayesPm {

    @Test
    public void testInitializeFixed() {
        Graph graph = GraphUtils.convert("X1-->X2,X1-->X3,X2-->X4,X3-->X4");
        Dag dag = new Dag(graph);
        BayesPm bayesPm = new BayesPm(dag, 3, 3);

        List<Node> nodes = dag.getNodes();

        for (Node node1 : nodes) {
            assertEquals(3, bayesPm.getNumCategories(node1));
        }
    }

    @Test
    public void testInitializeRandom() {
        Graph graph = GraphUtils.convert("X1-->X2,X1-->X3,X2-->X4,X3-->X4");
        Dag dag = new Dag(graph);
        BayesPm bayesPm = new BayesPm(dag, 2, 5);
        List<Node> nodes = dag.getNodes();

        for (Node node1 : nodes) {
            int numValues = bayesPm.getNumCategories(node1);
            assertTrue("Number of values out of range: " + numValues,
                    numValues >= 2 && numValues <= 5);
        }
    }

    @Test
    public void testChangeNumValues() {
        Graph graph = GraphUtils.convert("X1-->X2,X1-->X3,X2-->X4,X3-->X4");
        Dag dag = new Dag(graph);

        Node x1 = dag.getNode("X1");
        Node x2 = dag.getNode("X2");

        BayesPm bayesPm = new BayesPm(dag, 3, 3);
        bayesPm.setNumCategories(x1, 5);

        assertEquals(5, bayesPm.getNumCategories(x1));
        assertEquals(3, bayesPm.getNumCategories(x2));
    }

    @Test
    public void testEquals() {
        Graph graph = GraphUtils.convert("X1-->X2,X1-->X3,X2-->X4,X3-->X4");
        Dag dag = new Dag(graph);

        BayesPm bayesPm = new BayesPm(dag, 3, 3);

        assertEquals(bayesPm, bayesPm);
    }

    @Test
    public void testMeasuredNodes() {
        Dag dag = new Dag();

        Node x1 = new GraphNode("X1");
        Node x2 = new GraphNode("X2");
        Node x3 = new GraphNode("X3");
        Node x4 = new GraphNode("X4");

        x1.setNodeType(NodeType.LATENT);

        dag.addNode(x1);
        dag.addNode(x2);
        dag.addNode(x3);
        dag.addNode(x4);
        dag.addDirectedEdge(x1, x2);
        new BayesPm(dag, 3, 3);
    }
}






