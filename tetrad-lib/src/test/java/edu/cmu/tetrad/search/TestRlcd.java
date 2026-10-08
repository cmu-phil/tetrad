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

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.data.missing.MissingDataSpec;
import edu.cmu.tetrad.data.missing.MvnImputer;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.rlcd.Chi2RankTest;
import edu.cmu.tetrad.search.rlcd.PooledRankTest;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
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

        // That edge comes from the finish step, which joins the two leftover latent roots without anything deciding
        // which is the parent; rank constraints cannot tell L1 --> L2 from L2 --> L1 here. It must not be directed.
        assertTrue("The edge between the latents should be undirected: " + out,
                Edges.isUndirectedEdge(out.getEdge(latents.get(0), latents.get(1))));

        // No observed-observed edges should remain inside the measurement model.
        for (Edge e : out.getEdges()) {
            boolean bothObserved = e.getNode1().getNodeType() != NodeType.LATENT
                                   && e.getNode2().getNodeType() != NodeType.LATENT;
            assertFalse("Unexpected observed-observed edge " + e + " in " + out, bothObserved);
        }
    }

    /**
     * An observed DAG with no latents, dense enough that RLCD finds spurious clusters with observed non-sinks.
     */
    private static Graph observedDag() {
        Graph g = new EdgeListGraph();
        Node[] x = new Node[6];
        for (int i = 0; i < 6; i++) {
            x[i] = new GraphNode("X" + (i + 1));
            g.addNode(x[i]);
        }
        g.addDirectedEdge(x[0], x[1]);
        g.addDirectedEdge(x[0], x[2]);
        g.addDirectedEdge(x[1], x[2]);
        g.addDirectedEdge(x[2], x[3]);
        g.addDirectedEdge(x[1], x[4]);
        g.addDirectedEdge(x[3], x[4]);
        return g;
    }

    /**
     * Knowledge over the observed variables must be honored in the output: no forbidden observed-to-observed edge
     * may appear (whether from stage 1 or from a stage-2 non-sink), and required observed edges must be present.
     * Knowledge cannot refer to latents, so edges at latent nodes are unconstrained.
     */
    @Test
    public void testKnowledgeIsHonored() throws Exception {
        DataSet data = simulate(observedDag(), 2000, 31L);

        Knowledge knowledge = new Knowledge();
        knowledge.addToTier(0, "X1");
        for (int i = 2; i <= 6; i++) knowledge.addToTier(1, "X" + i);
        knowledge.setForbidden("X4", "X2");
        knowledge.setForbidden("X4", "X5");
        knowledge.setRequired("X1", "X2");

        Rlcd rlcd = new Rlcd(data);
        rlcd.setMaxK(2);
        rlcd.setSeed(1L);
        rlcd.setKnowledge(knowledge);
        Graph out = rlcd.search();

        Node x1 = out.getNode("X1"), x2 = out.getNode("X2");
        assertTrue("Required edge X1 --> X2 missing: " + out, out.isParentOf(x1, x2));

        for (Edge e : out.getEdges()) {
            Node a = e.getNode1(), b = e.getNode2();
            if (a.getNodeType() == NodeType.LATENT || b.getNodeType() == NodeType.LATENT) continue;
            if (Edges.isDirectedEdge(e)) {
                Node tail = Edges.getDirectedEdgeTail(e), head = Edges.getDirectedEdgeHead(e);
                assertFalse("Forbidden edge in output: " + e + " in " + out,
                        knowledge.isForbidden(tail.getName(), head.getName()));
            } else {
                // An undirected observed edge is allowed only if at least one orientation is permitted.
                assertFalse("Edge forbidden in both directions: " + e,
                        knowledge.isForbidden(a.getName(), b.getName())
                        && knowledge.isForbidden(b.getName(), a.getName()));
            }
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
     * The rule for pooling chi-square statistics over imputations: with imputations that agree it is the ordinary
     * chi-square test, and disagreement among them, at the same mean statistic, makes it harder to reject.
     */
    @Test
    public void testPooledPValue() {
        double chi2 = 1.0 - new ChiSquaredDistribution(3).cumulativeProbability(12.0);
        assertEquals(chi2, PooledRankTest.pooledPValue(new double[]{12.0}, 3), 1e-12);
        assertEquals(chi2, PooledRankTest.pooledPValue(new double[]{12.0, 12.0, 12.0, 12.0}, 3), 1e-12);

        double mild = PooledRankTest.pooledPValue(new double[]{10.0, 11.0, 13.0, 14.0}, 3);
        double wild = PooledRankTest.pooledPValue(new double[]{2.0, 6.0, 16.0, 24.0}, 3);
        assertTrue(mild > chi2);
        assertTrue(wild > mild);
        assertTrue(wild <= 1.0);
    }

    /**
     * One search over several imputations of data with missing values recovers the two clusters of the two-latent
     * model, and gives the same answer as the single-data-set search when there is only one data set.
     */
    @Test
    public void testPooledImputations() throws Exception {
        DataSet full = simulate(twoLatentGraph(), 3000, 4242L);
        DataSet holes = full.copy();
        Random random = new Random(4242L);

        // A fifth of the values missing completely at random, the first column kept whole.
        for (int i = 0; i < holes.getNumRows(); i++) {
            for (int j = 1; j < holes.getNumColumns(); j++) {
                if (random.nextDouble() < 0.2) holes.setDouble(i, j, Double.NaN);
            }
        }

        List<DataSet> imputations = new MvnImputer(MissingDataSpec.multipleImputation(5)).impute(holes, 5, 4242L);

        Rlcd rlcd = new Rlcd(imputations);
        rlcd.setMaxK(2);
        rlcd.setAlpha(0.01);
        rlcd.setSeed(1L);
        Graph out = rlcd.search();

        Set<Set<String>> childSets = new HashSet<>();
        for (Node node : out.getNodes()) {
            if (node.getNodeType() != NodeType.LATENT) continue;
            Set<String> children = new HashSet<>();
            for (Node c : out.getChildren(node)) {
                if (c.getNodeType() != NodeType.LATENT) children.add(c.getName());
            }
            childSets.add(children);
        }
        assertEquals("Two clusters expected, got " + out, Set.of(Set.of("X1", "X2", "X3"), Set.of("X4", "X5", "X6")),
                childSets);

        Rlcd one = new Rlcd(List.of(full));
        one.setMaxK(2);
        one.setAlpha(0.01);
        one.setSeed(1L);
        Rlcd single = new Rlcd(full);
        single.setMaxK(2);
        single.setAlpha(0.01);
        single.setSeed(1L);
        assertEquals(single.search(), one.search());
    }

    /**
     * A set of variables that the rank test does not find dependent on the rest at all (rank 0) is not a cluster.
     * Here six independent variables are given a stage-1 graph that wrongly makes them one clique, with the collider
     * check off so that the rank-0 sets reach the point of being queued. This used to throw "A cover must have at
     * least one variable"; no latent should be introduced.
     */
    @Test
    public void testRankZeroSetIsNotACluster() throws Exception {
        List<Node> vars = new ArrayList<>();
        for (int i = 1; i <= 6; i++) vars.add(new ContinuousVariable("X" + i));
        DataSet data = new BoxDataSet(new DoubleDataBox(500, 6), vars);
        Random random = new Random(3L);
        for (int i = 0; i < 500; i++) {
            for (int j = 0; j < 6; j++) data.setDouble(i, j, random.nextGaussian());
        }

        Graph stage1 = new EdgeListGraph(vars);
        for (int i = 0; i < 6; i++) {
            for (int j = i + 1; j < 6; j++) stage1.addUndirectedEdge(vars.get(i), vars.get(j));
        }

        Rlcd rlcd = new Rlcd(data);
        rlcd.setStage1Graph(stage1);
        rlcd.setMaxK(2);
        rlcd.setCheckV(false);
        Graph out = rlcd.search();

        for (Node node : out.getNodes()) {
            assertNotEquals("No latent expected: " + out, NodeType.LATENT, node.getNodeType());
        }
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
