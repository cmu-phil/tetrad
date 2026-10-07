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

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.rlcd.Chi2RankTest;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Tests for {@link Rlcd}, and an example of how to run it. The main method prints the result for hand inspection.
 *
 * @author josephramsey
 */
public class TestRlcd {

    /**
     * A measurement model with two causally related latents, L1 -&gt; L2, each with three pure indicators.
     */
    private static Graph twoLatentGraph() {
        Graph g = new EdgeListGraph();
        Node l1 = new GraphNode("L1");
        Node l2 = new GraphNode("L2");
        l1.setNodeType(NodeType.LATENT);
        l2.setNodeType(NodeType.LATENT);
        g.addNode(l1);
        g.addNode(l2);
        g.addDirectedEdge(l1, l2);
        for (int i = 1; i <= 6; i++) {
            Node x = new GraphNode("X" + i);
            g.addNode(x);
            g.addDirectedEdge(i <= 3 ? l1 : l2, x);
        }
        return g;
    }

    private static DataSet simulate(Graph g, int n, long seed) throws Exception {
        RandomUtil.getInstance().setSeed(seed);
        Parameters params = new Parameters();
        params.set(Params.COEF_LOW, 0.8);
        params.set(Params.COEF_HIGH, 1.5);
        params.set(Params.VAR_LOW, 0.1);
        params.set(Params.VAR_HIGH, 0.3);
        SemPm pm = new SemPm(g);
        SemIm im = new SemIm(pm, params);
        // Latents are dropped from the simulated data by default.
        return im.simulateData(n, false);
    }

    /**
     * On a two-latent measurement model, RLCD should introduce two latents, place the indicators of each true latent
     * under the same new latent, and connect the two latents.
     */
    @Test
    public void testTwoLatents() throws Exception {
        DataSet data = simulate(twoLatentGraph(), 3000, 12345L);

        Rlcd rlcd = new Rlcd(data);
        rlcd.setMaxK(2);
        rlcd.setAlpha(0.01);
        rlcd.setSeed(1L);
        Graph out = rlcd.search();

        List<Node> latents = new ArrayList<>();
        for (Node node : out.getNodes()) {
            if (node.getNodeType() == NodeType.LATENT) latents.add(node);
        }
        assertEquals("Two latents expected, got " + out, 2, latents.size());

        Set<Set<String>> childSets = new HashSet<>();
        for (Node latent : latents) {
            Set<String> children = new HashSet<>();
            for (Node c : out.getChildren(latent)) {
                if (c.getNodeType() != NodeType.LATENT) children.add(c.getName());
            }
            childSets.add(children);
        }
        assertTrue("Cluster {X1,X2,X3} not found: " + out, childSets.contains(Set.of("X1", "X2", "X3")));
        assertTrue("Cluster {X4,X5,X6} not found: " + out, childSets.contains(Set.of("X4", "X5", "X6")));
        assertTrue("Latents should be adjacent: " + out, out.isAdjacentTo(latents.get(0), latents.get(1)));

        // No observed-observed edges should remain inside the measurement model.
        for (Edge e : out.getEdges()) {
            boolean bothObserved = e.getNode1().getNodeType() != NodeType.LATENT
                                   && e.getNode2().getNodeType() != NodeType.LATENT;
            assertFalse("Unexpected observed-observed edge " + e + " in " + out, bothObserved);
        }
    }

    /**
     * The rank test should accept rank 1 and reject rank 0 for one pure cluster against the other.
     */
    @Test
    public void testChi2RankTest() throws Exception {
        DataSet data = simulate(twoLatentGraph(), 3000, 777L);
        Chi2RankTest test = new Chi2RankTest(data);
        int[] a = {0, 1, 2}, b = {3, 4, 5};
        assertTrue(test.failToReject(a, b, 1, 0.01));
        assertFalse(test.failToReject(a, b, 0, 0.01));
        // Overlapping column sets (the non-sink device): rank(Σ[A ∪ C, B ∪ C]) with C = {X1}.
        int[] ac = {0, 1, 2}, bc = {0, 3, 4, 5};
        assertTrue(test.failToReject(ac, bc, 2, 0.01));
        assertFalse(test.failToReject(ac, bc, 1, 0.01));
    }

    /**
     * Example run with verbose trace; hand-run from the IDE.
     *
     * @param args ignored.
     * @throws Exception if anything goes wrong.
     */
    public static void main(String[] args) throws Exception {
        DataSet data = simulate(twoLatentGraph(), 3000, 12345L);
        Rlcd rlcd = new Rlcd(data);
        rlcd.setMaxK(2);
        rlcd.setVerbose(true);
        Graph out = rlcd.search();
        System.out.println("Stage 1: " + rlcd.getStage1Graph());
        System.out.println("Partition: " + rlcd.getPartition());
        System.out.println("Result: " + out);
    }
}
