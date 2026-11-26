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
///////////////////////////////////////////////////////////////////////////////
package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.utils.RequiredEdgeOrder;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The repair of an order of variables with respect to required edges: afterwards no variable comes before one that
 * knowledge requires to precede it, and an order that was already sound is not touched.
 *
 * @author Claude
 */
public class TestRequiredEdgeOrder {

    private static List<Node> nodes(String... names) {
        List<Node> nodes = new ArrayList<>();
        for (String name : names) nodes.add(new ContinuousVariable(name));
        return nodes;
    }

    private static List<String> names(List<Node> nodes) {
        List<String> names = new ArrayList<>();
        for (Node node : nodes) names.add(node.getName());
        return names;
    }

    /**
     * The case the one-pass repair in the searches leaves broken. With C required before A and A before B, and the
     * order B, C, A, that pass moves A to the front and stops at A, B, C, with A ahead of C.
     */
    @Test
    public void testAChainLeftBrokenByOnePassIsPutRight() {
        Knowledge knowledge = new Knowledge();
        knowledge.setRequired("C", "A");
        knowledge.setRequired("A", "B");

        List<Node> order = nodes("A", "B", "C");
        assertTrue(RequiredEdgeOrder.breaksRequiredEdge(order, knowledge));

        RequiredEdgeOrder.repair(order, knowledge);

        assertEquals(List.of("C", "A", "B"), names(order));
        assertFalse(RequiredEdgeOrder.breaksRequiredEdge(order, knowledge));
    }

    @Test
    public void testASoundOrderIsLeftExactlyAsItIs() {
        Knowledge knowledge = new Knowledge();
        knowledge.setRequired("C", "A");
        knowledge.setRequired("A", "B");

        List<Node> order = nodes("X", "C", "Y", "A", "Z", "B");
        List<Node> before = new ArrayList<>(order);

        assertFalse(RequiredEdgeOrder.breaksRequiredEdge(order, knowledge));
        RequiredEdgeOrder.repair(order, knowledge);

        assertEquals(before, order);
    }

    @Test
    public void testVariablesNotInvolvedKeepTheirPlacesRelativeToEachOther() {
        Knowledge knowledge = new Knowledge();
        knowledge.setRequired("C", "A");

        List<Node> order = nodes("X", "A", "Y", "C", "Z");
        RequiredEdgeOrder.repair(order, knowledge);

        // A waits for C; everything else is taken in the order it stood.
        assertEquals(List.of("X", "Y", "C", "A", "Z"), names(order));
    }

    @Test
    public void testNoKnowledgeChangesNothing() {
        List<Node> order = nodes("B", "A", "C");
        List<Node> before = new ArrayList<>(order);

        RequiredEdgeOrder.repair(order, new Knowledge());
        RequiredEdgeOrder.repair(order, null);

        assertEquals(before, order);
    }

    /**
     * Required edges that go round in a circle cannot all be honored. Nothing is lost and nothing loops forever.
     */
    @Test
    public void testACircleOfRequiredEdgesKeepsEveryVariable() {
        Knowledge knowledge = new Knowledge();
        knowledge.setRequired("A", "B");
        knowledge.setRequired("B", "C");
        knowledge.setRequired("C", "A");

        List<Node> order = nodes("X", "A", "B", "C", "Y");
        RequiredEdgeOrder.repair(order, knowledge);

        assertEquals(5, order.size());
        assertEquals(new HashSet<>(List.of("X", "A", "B", "C", "Y")), new HashSet<>(names(order)));
    }

    /**
     * Many random sets of required edges with no circle among them, each against many random orders.
     */
    @Test
    public void testRandomRequiredEdgesAreAlwaysHonoredAfterward() {
        Random random = new Random(41L);

        for (int trial = 0; trial < 300; trial++) {
            int n = 4 + random.nextInt(9);
            String[] names = new String[n];
            for (int i = 0; i < n; i++) names[i] = "V" + i;

            // Required edges only from a lower-numbered variable to a higher one, so there is no circle.
            Knowledge knowledge = new Knowledge();
            int numEdges = 1 + random.nextInt(2 * n);
            for (int e = 0; e < numEdges; e++) {
                int i = random.nextInt(n), j = random.nextInt(n);
                if (i < j) knowledge.setRequired(names[i], names[j]);
                else if (j < i) knowledge.setRequired(names[j], names[i]);
            }

            List<Node> order = nodes(names);
            Collections.shuffle(order, random);
            List<Node> before = new ArrayList<>(order);
            boolean wasBroken = RequiredEdgeOrder.breaksRequiredEdge(order, knowledge);

            RequiredEdgeOrder.repair(order, knowledge);

            assertFalse("Trial " + trial, RequiredEdgeOrder.breaksRequiredEdge(order, knowledge));
            assertEquals("Trial " + trial, new HashSet<>(before), new HashSet<>(order));
            assertEquals("Trial " + trial, n, order.size());
            if (!wasBroken) assertEquals("Trial " + trial, before, order);
        }
    }
}
