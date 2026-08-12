///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2026 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
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

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.ConditioningSetType;
import edu.cmu.tetrad.search.VertexRepairSearch;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Regression tests for background-knowledge handling in graph repair. Historically,
 * {@link Knowledge#isViolatedBy(Graph)} inspected directed edges only, so an undirected edge between two variables
 * of a forbidden-within tier - or an undirected replacement of a knowledge-oriented cross-tier edge - passed the
 * check, and {@link VertexRepairSearch} (which screens its candidate edits with that method) could emit graphs
 * violating the knowledge. Observed in the wild on the airfoil-self-noise dataset with its ground-truth tiers.
 *
 * @author josephramsey
 */
public class TestRepairKnowledge {

    /**
     * Knowledge with tier 0 = {A, B} (forbidden within) and tier 1 = {C}.
     */
    private static Knowledge tieredKnowledge() {
        Knowledge knowledge = new Knowledge();
        knowledge.addToTier(0, "A");
        knowledge.addToTier(0, "B");
        knowledge.addToTier(1, "C");
        knowledge.setTierForbiddenWithin(0, true);
        return knowledge;
    }

    /**
     * An undirected edge whose orientations are BOTH forbidden (within a forbidden-within tier) violates the
     * knowledge; an undirected edge with exactly one forbidden orientation does not (it is under-oriented, not
     * impossible); directed edges behave as before.
     */
    @Test
    public void testIsViolatedByUndirectedEdges() {
        Knowledge knowledge = tieredKnowledge();

        Node a = new GraphNode("A");
        Node b = new GraphNode("B");
        Node c = new GraphNode("C");

        // A --- B: both directions forbidden (within tier 0, forbidden within) -> violation.
        Graph g1 = new EdgeListGraph(List.of(a, b, c));
        g1.addUndirectedEdge(a, b);
        assertTrue("Undirected edge within a forbidden-within tier must violate knowledge",
                knowledge.isViolatedBy(g1));

        // C --> A: directed against the tiers -> violation (pre-existing behavior).
        Graph g2 = new EdgeListGraph(List.of(a, b, c));
        g2.addDirectedEdge(c, a);
        assertTrue("Directed edge against the tiers must violate knowledge",
                knowledge.isViolatedBy(g2));

        // A --> C: allowed by the tiers -> no violation.
        Graph g3 = new EdgeListGraph(List.of(a, b, c));
        g3.addDirectedEdge(a, c);
        assertFalse("Tier-consistent directed edge must not violate knowledge",
                knowledge.isViolatedBy(g3));

        // A --- C: only C->A is forbidden -> under-oriented, but not a violation of the class.
        Graph g4 = new EdgeListGraph(List.of(a, b, c));
        g4.addUndirectedEdge(a, c);
        assertFalse("Undirected edge with exactly one forbidden orientation is under-oriented, not violating",
                knowledge.isViolatedBy(g4));
    }

    /**
     * VertexRepairSearch, given knowledge, must not emit a graph that (a) contains an edge within a forbidden-within
     * tier, (b) orients an edge against the tiers, or (c) leaves a cross-tier edge undirected when the knowledge
     * determines its orientation.
     */
    @Test
    public void testRepairRespectsKnowledge() throws InterruptedException {
        int n = 500;
        Random rng = new Random(42);

        List<Node> vars = new ArrayList<>();
        for (String name : new String[]{"A", "B", "C"}) vars.add(new ContinuousVariable(name));
        DataSet data = new BoxDataSet(new DoubleDataBox(n, 3), vars);

        // A and B exogenous (tier 0); C = A + B + noise (tier 1).
        for (int i = 0; i < n; i++) {
            double aV = rng.nextGaussian();
            double bV = rng.nextGaussian();
            data.setDouble(i, 0, aV);
            data.setDouble(i, 1, bV);
            data.setDouble(i, 2, aV + bV + 0.5 * rng.nextGaussian());
        }

        Knowledge knowledge = tieredKnowledge();

        // Seed graph deliberately wrong but knowledge-consistent: A --> C only.
        Graph seed = new EdgeListGraph(new ArrayList<>(data.getVariables()));
        seed.addDirectedEdge(data.getVariable("A"), data.getVariable("C"));

        IndTestFisherZ test = new IndTestFisherZ(data, 0.01);

        VertexRepairSearch repair = new VertexRepairSearch(seed, test,
                ConditioningSetType.ORDERED_LOCAL_MARKOV_PROPERTY);
        repair.setKnowledge(knowledge);
        repair.setSeed(42L);
        Graph out = repair.search();

        for (Edge edge : out.getEdges()) {
            String x = edge.getNode1().getName();
            String y = edge.getNode2().getName();
            boolean xTier0 = x.equals("A") || x.equals("B");
            boolean yTier0 = y.equals("A") || y.equals("B");

            assertFalse("Repair emitted an edge within the forbidden-within tier: " + edge,
                    xTier0 && yTier0);

            if (edge.isDirected()) {
                String head = Edges.getDirectedEdgeHead(edge).getName();
                assertFalse("Repair oriented an edge against the tiers: " + edge,
                        (head.equals("A") || head.equals("B")));
            } else if (Edges.isUndirectedEdge(edge) && (xTier0 ^ yTier0)) {
                throw new AssertionError(
                        "Repair left a knowledge-determined cross-tier edge undirected: " + edge);
            }
        }
    }
}
