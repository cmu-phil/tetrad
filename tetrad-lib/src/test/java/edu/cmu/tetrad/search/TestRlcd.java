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
import edu.cmu.tetrad.search.rlcd.RlcdStabilitySummary;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.data.KnowledgeTierStructure;
import edu.cmu.tetrad.data.missing.MissingDataSpec;
import edu.cmu.tetrad.data.missing.MvnImputer;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.rlcd.Chi2RankTest;
import edu.cmu.tetrad.search.rlcd.PooledRankTest;
import edu.cmu.tetrad.search.rlcd.TestwiseDeletionRankTest;
import edu.cmu.tetrad.search.rlcd.EmCovarianceRankTest;
import edu.cmu.tetrad.data.missing.CensoredMvnCovarianceEstimator;
import edu.cmu.tetrad.data.EmCovarianceEstimator;
import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.search.rlcd.TierPrecedence;
import edu.cmu.tetrad.search.rlcd.TwoIndicatorTest;
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
     * Temporal tiers constrain ancestry through latents, not only direct edges. The true model is F --> L1 --> {E1,
     * E2, E3} and F --> L2 --> {M1, M2, M3}, which RLCD recovers without knowledge on this seed (F --> L2 found by a
     * rank test with F as a non-sink, F --> L1 placed by the finish step). Tier structures putting F after the E's
     * and after the M's must keep F from being an ancestor of any of them in the output, whichever step would
     * otherwise have done it.
     */
    @Test
    public void testTiersConstrainAncestryThroughLatents() throws Exception {
        Graph g = new EdgeListGraph();
        Node f = new GraphNode("F");
        g.addNode(f);
        Node l1 = new GraphNode("L1"), l2 = new GraphNode("L2");
        l1.setNodeType(NodeType.LATENT);
        l2.setNodeType(NodeType.LATENT);
        g.addNode(l1);
        g.addDirectedEdge(f, l1);
        g.addNode(l2);
        g.addDirectedEdge(f, l2);
        for (int i = 1; i <= 3; i++) {
            Node e = new GraphNode("E" + i);
            g.addNode(e);
            g.addDirectedEdge(l1, e);
        }
        for (int i = 1; i <= 3; i++) {
            Node m = new GraphNode("M" + i);
            g.addNode(m);
            g.addDirectedEdge(l2, m);
        }
        DataSet data = simulate(g, 3000, 3L);

        // With the default qualification of observed causes, F --> L1 and F --> L2 are placements the data cannot
        // support (F has no observed parent), so they come out undirected.
        Rlcd qualified = new Rlcd(data);
        qualified.setMaxK(2);
        qualified.setSeed(1L);
        Graph q = qualified.search();
        for (Node l : q.getNodes()) {
            if (l.getNodeType() != NodeType.LATENT) continue;
            Edge e = q.getEdge(q.getNode("F"), l);
            assertTrue("F to " + l + " should be undirected, was " + e + " in " + q, e != null
                                                                                      && Edges.isUndirectedEdge(e));
        }

        // The rest of the test is about tiers constraining ancestry, which needs the raw placements.
        Rlcd plain = new Rlcd(data);
        plain.setMaxK(2);
        plain.setSeed(1L);
        plain.setQualifyObservedCauses(false);
        Graph unconstrained = plain.search();
        assertTrue("Precondition: without knowledge F should be an ancestor of E1: " + unconstrained,
                unconstrained.paths().isAncestorOf(unconstrained.getNode("F"), unconstrained.getNode("E1")));

        Knowledge knowledge = new Knowledge();
        KnowledgeTierStructure exams = new KnowledgeTierStructure("E");
        KnowledgeTierStructure modules = new KnowledgeTierStructure("M");
        for (int i = 1; i <= 3; i++) {
            exams.addToTier(i - 1, "E" + i);
            modules.addToTier(i - 1, "M" + i);
        }
        exams.addToTier(3, "F");
        modules.addToTier(3, "F");
        knowledge.addTierStructure(exams);
        knowledge.addTierStructure(modules);

        Rlcd rlcd = new Rlcd(data);
        rlcd.setMaxK(2);
        rlcd.setSeed(1L);
        rlcd.setKnowledge(knowledge);
        rlcd.setQualifyObservedCauses(false);
        Graph out = rlcd.search();

        for (Node a : out.getNodes()) {
            if (a.getNodeType() == NodeType.LATENT) continue;
            for (Node b : out.getNodes()) {
                if (a == b || b.getNodeType() == NodeType.LATENT) continue;
                if (TierPrecedence.isLater(knowledge, a.getName(), b.getName())) {
                    assertFalse(a + " is an ancestor of " + b + " against the tiers: " + out,
                            out.paths().isAncestorOf(a, b));
                }
            }
        }
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
     * A latent all of whose children are latent: T0 over T1, ..., T4, each with three pure indicators. Finding T0
     * takes a rank test on a set of latent covers, which the Python never makes. The four lower latents should each
     * be adjacent to one further latent that has no observed neighbors, and to no other latent.
     */
    @Test
    public void testLatentOverLatents() throws Exception {
        Graph g = new EdgeListGraph();
        Node top = new GraphNode("T0");
        top.setNodeType(NodeType.LATENT);
        g.addNode(top);
        int x = 1;
        for (int i = 1; i <= 4; i++) {
            Node t = new GraphNode("T" + i);
            t.setNodeType(NodeType.LATENT);
            g.addNode(t);
            g.addDirectedEdge(top, t);
            for (int j = 0; j < 3; j++) {
                Node o = new GraphNode("X" + x++);
                g.addNode(o);
                g.addDirectedEdge(t, o);
            }
        }

        DataSet data = simulate(g, 10000, 31L);
        Rlcd rlcd = new Rlcd(data);
        rlcd.setMaxK(2);
        rlcd.setAlpha(0.01);
        rlcd.setSeed(1L);
        Graph out = rlcd.search();

        List<Node> lower = new ArrayList<>();
        List<Node> upper = new ArrayList<>();
        for (Node node : out.getNodes()) {
            if (node.getNodeType() != NodeType.LATENT) continue;
            boolean hasObserved = false;
            for (Node adj : out.getAdjacentNodes(node)) {
                if (adj.getNodeType() != NodeType.LATENT) hasObserved = true;
            }
            (hasObserved ? lower : upper).add(node);
        }

        assertEquals("Four latents with indicators expected: " + out, 4, lower.size());
        assertEquals("One latent over them expected: " + out, 1, upper.size());

        for (Node node : lower) {
            assertTrue("Each lower latent should be adjacent to the upper one: " + out,
                    out.isAdjacentTo(node, upper.getFirst()));
            for (Node other : lower) {
                if (other != node) assertFalse("Lower latents should not be adjacent: " + out,
                        out.isAdjacentTo(node, other));
            }
        }
    }

    /**
     * Three latents in a chain, T1 --> T2 --> T3, with four pure indicators each. The Markov equivalence class of
     * that structure has no collider, so neither edge between latents has a determined direction, and neither may
     * come out directed. (Stage 2 builds one of them as a cover over a cluster, which used to be reported as a
     * directed edge, pointing against the truth.)
     */
    @Test
    public void testLatentEdgesNotDirectedWithoutCollider() throws Exception {
        Graph g = new EdgeListGraph();
        Node[] t = new Node[3];
        int x = 1;
        for (int i = 0; i < 3; i++) {
            t[i] = new GraphNode("T" + (i + 1));
            t[i].setNodeType(NodeType.LATENT);
            g.addNode(t[i]);
            if (i > 0) g.addDirectedEdge(t[i - 1], t[i]);
            for (int j = 0; j < 4; j++) {
                Node o = new GraphNode("X" + x++);
                g.addNode(o);
                g.addDirectedEdge(t[i], o);
            }
        }

        DataSet data = simulate(g, 5000, 99L);
        Rlcd rlcd = new Rlcd(data);
        rlcd.setMaxK(2);
        rlcd.setAlpha(0.01);
        rlcd.setSeed(1L);
        Graph out = rlcd.search();

        int latentEdges = 0;
        for (Edge e : out.getEdges()) {
            if (e.getNode1().getNodeType() == NodeType.LATENT && e.getNode2().getNodeType() == NodeType.LATENT) {
                latentEdges++;
                assertTrue("Edge between latents should be undirected: " + e + " in " + out,
                        Edges.isUndirectedEdge(e));
            } else if (e.getNode1().getNodeType() == NodeType.LATENT
                       || e.getNode2().getNodeType() == NodeType.LATENT) {
                assertTrue("Edge from a latent to an observed variable should stay directed: " + e,
                        Edges.isDirectedEdge(e));
            }
        }
        assertEquals("Two edges between latents expected: " + out, 2, latentEdges);
    }

    /**
     * A DAG over observed variables only, with three triangles sharing an edge, so that stage 1 gives stage 2 a
     * partition to search. No latent is behind it. With the check for whether a latent is indicated, none should be
     * reported; without it, stage 2 reports one on this data.
     */
    @Test
    public void testNoLatentWhereNoneIsIndicated() throws Exception {
        Graph g = new EdgeListGraph();
        Node[] x = new Node[8];
        for (int i = 1; i <= 7; i++) {
            x[i] = new GraphNode("X" + i);
            g.addNode(x[i]);
        }
        int[][] edges = {{1, 2}, {1, 3}, {1, 6}, {1, 7}, {2, 6}, {3, 4}, {3, 6}, {6, 7}};
        for (int[] e : edges) g.addDirectedEdge(x[e[0]], x[e[1]]);

        DataSet data = simulate(g, 20000, 11L);

        for (boolean gate : new boolean[]{true, false}) {
            Rlcd rlcd = new Rlcd(data);
            rlcd.setMaxK(3);
            rlcd.setAlpha(0.01);
            rlcd.setSeed(1L);
            rlcd.setLatentGate(gate);
            Graph out = rlcd.search();

            boolean latent = false;
            for (Node node : out.getNodes()) latent = latent || node.getNodeType() == NodeType.LATENT;

            if (gate) assertFalse("No latent expected with the check: " + out, latent);
            else assertTrue("This data is expected to produce a spurious latent without the check: " + out, latent);
        }
    }

    /**
     * A cover of two latents: T1 and T2 are jointly the parents of ten indicators. The output should have two
     * latents whose indicators are, between them, all ten, and no edge between observed variables. Which of the
     * two latents a given indicator is drawn under is not checked, since the two can be rotated. (Before the
     * clusters of a round were added best-supported first, an observed variable was made a parent in place of one
     * of the latents on this data.)
     */
    @Test
    public void testCoverOfTwoLatents() throws Exception {
        Graph g = new EdgeListGraph();
        Node t1 = new GraphNode("T1"), t2 = new GraphNode("T2");
        t1.setNodeType(NodeType.LATENT);
        t2.setNodeType(NodeType.LATENT);
        g.addNode(t1);
        g.addNode(t2);
        for (int i = 1; i <= 10; i++) {
            Node x = new GraphNode("X" + i);
            g.addNode(x);
            g.addDirectedEdge(t1, x);
            g.addDirectedEdge(t2, x);
        }

        DataSet data = simulate(g, 10000, 3L);
        Rlcd rlcd = new Rlcd(data);
        rlcd.setMaxK(3);
        rlcd.setAlpha(0.01);
        rlcd.setSeed(1L);
        Graph out = rlcd.search();

        int latents = 0;
        Set<String> indicators = new HashSet<>();
        for (Node node : out.getNodes()) {
            if (node.getNodeType() != NodeType.LATENT) continue;
            latents++;
            for (Node adj : out.getAdjacentNodes(node)) {
                if (adj.getNodeType() != NodeType.LATENT) indicators.add(adj.getName());
            }
        }
        assertEquals("Two latents expected: " + out, 2, latents);
        assertEquals("All ten indicators should be under them: " + out, 10, indicators.size());

        for (Edge e : out.getEdges()) {
            assertTrue("No edge between observed variables expected: " + e + " in " + out,
                    e.getNode1().getNodeType() == NodeType.LATENT || e.getNode2().getNodeType() == NodeType.LATENT);
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

    // ---------------------------------------------------------------- two-indicator latents

    private static double[] skewOrGauss(Random r, int n, boolean gauss) {
        double[] o = new double[n];
        for (int i = 0; i < n; i++) o[i] = 0.8 * (gauss ? r.nextGaussian() : -Math.log(1 - r.nextDouble()) - 1);
        return o;
    }

    /**
     * The cumulant test alone, with three references of the shared factor L, N = 5000, skewed errors. A direct
     * edge X1 --&gt; X2 is recognized; a latent over {X1, X2} is recognized as one; with Gaussian errors the answer
     * is always undecided. The seeds are fixed; on other seeds the edge is often undecided (the test needs positive
     * evidence for an edge), but a latent should not be read as an edge.
     */
    @Test
    public void testTwoIndicatorTest() {
        int n = 5000;
        for (String kind : new String[]{"edge", "latent", "gauss"}) {
            for (long seed = 1; seed <= (kind.equals("edge") ? 1 : 3); seed++) {
                Random r = new Random(seed);
                boolean gauss = kind.equals("gauss");
                double[] l = skewOrGauss(r, n, gauss);
                for (int i = 0; i < n; i++) l[i] /= 0.8;
                double[][] refs = new double[3][n];
                double[] c = {1.0, 0.9, 1.1};
                for (int k = 0; k < 3; k++) {
                    double[] e = skewOrGauss(r, n, gauss);
                    for (int i = 0; i < n; i++) refs[k][i] = c[k] * l[i] + e[i];
                }
                double[] e1 = skewOrGauss(r, n, gauss), e2 = skewOrGauss(r, n, gauss), u = skewOrGauss(r, n, gauss);
                double[] x1 = new double[n], x2 = new double[n];
                for (int i = 0; i < n; i++) {
                    if (kind.equals("latent")) {
                        double lp = l[i] + u[i];
                        x1[i] = lp + e1[i];
                        x2[i] = 0.8 * lp + e2[i];
                    } else {
                        x1[i] = l[i] + e1[i];
                        x2[i] = 0.8 * l[i] + x1[i] + e2[i];
                    }
                }
                TwoIndicatorTest.Answer expected = switch (kind) {
                    case "edge" -> TwoIndicatorTest.Answer.X1_TO_X2;
                    case "latent" -> TwoIndicatorTest.Answer.LATENT;
                    default -> TwoIndicatorTest.Answer.UNDECIDED;
                };
                assertEquals(kind + ", seed " + seed, expected,
                        TwoIndicatorTest.test(x1, x2, refs, 0.1, 400, seed).answer());
                // Swapping the roles of X1 and X2 swaps the answer.
                if (kind.equals("edge")) {
                    assertEquals(TwoIndicatorTest.Answer.X2_TO_X1,
                            TwoIndicatorTest.test(x2, x1, refs, 0.1, 400, seed).answer());
                }
            }
        }
    }

    /**
     * L1 --&gt; X1..X5 with X1 --&gt; X2, L2 --&gt; X6..X9, L1 --&gt; L2; or the same with a latent Lp over X1, X2
     * in place of the edge.
     */
    private static DataSet twoIndicatorData(long seed, boolean edge, boolean gauss, int n) {
        Random r = new Random(seed);
        double[] l1 = skewOrGauss(r, n, gauss), l2 = new double[n], lp = new double[n];
        double[] e = skewOrGauss(r, n, gauss), u = skewOrGauss(r, n, gauss);
        for (int i = 0; i < n; i++) {
            l2[i] = 0.9 * l1[i] + e[i];
            lp[i] = l1[i] + u[i];
        }
        double[] c = {1.0, 0.8, 1.1, 0.9, 1.2, 1.0, 0.8, 1.1, 0.9};
        double[][] x = new double[9][n];
        for (int j = 0; j < 9; j++) {
            double[] parent = j < 5 ? (!edge && j < 2 ? lp : l1) : l2;
            double[] ej = skewOrGauss(r, n, gauss);
            for (int i = 0; i < n; i++) x[j][i] = c[j] * parent[i] + ej[i];
        }
        if (edge) for (int i = 0; i < n; i++) x[1][i] += x[0][i];
        List<Node> vars = new ArrayList<>();
        for (int j = 0; j < 9; j++) vars.add(new ContinuousVariable("X" + (j + 1)));
        DoubleDataBox box = new DoubleDataBox(n, 9);
        for (int i = 0; i < n; i++) for (int j = 0; j < 9; j++) box.set(i, j, x[j][i]);
        return new BoxDataSet(box, vars);
    }

    private static Graph runTwoIndicator(DataSet data, boolean resolve) throws Exception {
        Rlcd rlcd = new Rlcd(data);
        rlcd.setSeed(1L);
        rlcd.setResolveTwoIndicatorLatents(resolve);
        return rlcd.search();
    }

    /**
     * End to end. Without the step, RLCD splits L1's cluster into a latent over {X1, X2} and one over {X3, X4, X5}.
     * With it, on this seed, the two-indicator latent is replaced by X1 --&gt; X2 with X1 and X2 under the latent
     * of X3..X5, which is the true structure. With a real latent over {X1, X2}, or with Gaussian errors, the step
     * changes nothing; nor does it with knowledge forbidding the edge.
     */
    @Test
    public void testResolveTwoIndicatorLatents() throws Exception {
        DataSet data = twoIndicatorData(2L, true, false, 5000);
        Graph off = runTwoIndicator(data, false);
        Graph on = runTwoIndicator(data, true);

        Node x1 = on.getNode("X1"), x2 = on.getNode("X2"), x3 = on.getNode("X3");
        assertFalse("Precondition: plain RLCD should not have the edge: " + off,
                off.isAdjacentTo(off.getNode("X1"), off.getNode("X2")));
        assertTrue("X1 --> X2 expected: " + on, on.isParentOf(x1, x2));
        Node m = on.getParents(x3).getFirst();
        assertEquals("X1 should be under X3's latent: " + on, List.of(m), on.getParents(x1));
        assertTrue("X2 should be under X3's latent: " + on, on.isParentOf(m, x2));
        int latentsOff = 0, latentsOn = 0;
        for (Node v : off.getNodes()) if (v.getNodeType() == NodeType.LATENT) latentsOff++;
        for (Node v : on.getNodes()) if (v.getNodeType() == NodeType.LATENT) latentsOn++;
        assertEquals("One latent fewer: " + on, latentsOff - 1, latentsOn);

        // Knowledge forbidding the edge in that direction keeps the latent.
        Knowledge knowledge = new Knowledge();
        knowledge.setForbidden("X1", "X2");
        Rlcd rlcd = new Rlcd(data);
        rlcd.setSeed(1L);
        rlcd.setResolveTwoIndicatorLatents(true);
        rlcd.setKnowledge(knowledge);
        Graph forbidden = rlcd.search();
        assertFalse("Forbidden edge added: " + forbidden,
                forbidden.isAdjacentTo(forbidden.getNode("X1"), forbidden.getNode("X2")));

        for (long seed = 1; seed <= 3; seed++) {
            DataSet latent = twoIndicatorData(seed, false, false, 5000);
            assertEquals("A real latent should be kept, seed " + seed, runTwoIndicator(latent, false),
                    runTwoIndicator(latent, true));
            DataSet gauss = twoIndicatorData(seed, true, true, 5000);
            assertEquals("Gaussian data should be left alone, seed " + seed, runTwoIndicator(gauss, false),
                    runTwoIndicator(gauss, true));
        }
    }

    /**
     * Testwise deletion with no missing values must reproduce the complete-data test exactly: same decisions, same
     * p-values, since every query's complete rows are all the rows.
     */
    @Test
    public void testTestwiseDeletionMatchesCompleteData() throws Exception {
        DataSet data = simulate(twoLatentGraph(), 2000, 777L);
        Chi2RankTest full = new Chi2RankTest(data);
        TestwiseDeletionRankTest twd = new TestwiseDeletionRankTest(data);

        int[][] ps = {{0, 1}, {0, 1, 2}, {3, 4}, {0, 3}};
        int[][] qs = {{2, 3, 4, 5}, {3, 4, 5}, {0, 1, 2}, {1, 2, 4, 5}};
        for (int i = 0; i < ps.length; i++) {
            for (int r = 0; r < 2; r++) {
                assertEquals("decision, query " + i + ", rank " + r,
                        full.failToReject(ps[i], qs[i], r, 0.01),
                        twd.failToReject(ps[i], qs[i], r, 0.01));
                assertEquals("p-value, query " + i + ", rank " + r,
                        full.pValue(ps[i], qs[i], r), twd.pValue(ps[i], qs[i], r), 1e-9);
            }
        }
        assertEquals(0, twd.getSkippedQueries());
    }

    /**
     * With a fifth of the values missing completely at random, testwise deletion should still recover the two-latent
     * measurement model, and should report using fewer rows per query than the data have.
     */
    @Test
    public void testTestwiseDeletionRecoversTwoLatents() throws Exception {
        DataSet full = simulate(twoLatentGraph(), 3000, 4242L);
        DataSet holes = full.copy();
        Random random = new Random(4242L);
        for (int i = 0; i < holes.getNumRows(); i++) {
            for (int j = 1; j < holes.getNumColumns(); j++) {
                if (random.nextDouble() < 0.2) holes.setDouble(i, j, Double.NaN);
            }
        }

        Rlcd rlcd = new Rlcd(holes);
        rlcd.setTestwiseDeletion(true);
        rlcd.setMaxK(2);
        rlcd.setAlpha(0.01);
        rlcd.setSeed(1L);
        Graph out = rlcd.search();

        List<Node> latents = new ArrayList<>();
        for (Node v : out.getNodes()) if (v.getNodeType() == NodeType.LATENT) latents.add(v);
        assertEquals(2, latents.size());

        Set<Set<String>> clusters = new HashSet<>();
        for (Node l : latents) {
            Set<String> kids = new HashSet<>();
            for (Node c : out.getChildren(l)) if (c.getNodeType() != NodeType.LATENT) kids.add(c.getName());
            clusters.add(kids);
        }
        assertTrue("clusters were " + clusters, clusters.contains(Set.of("X1", "X2", "X3")));
        assertTrue("clusters were " + clusters, clusters.contains(Set.of("X4", "X5", "X6")));
    }

    /**
     * The guards: a query whose columns have too few complete rows between them, and a query whose Lawley multiplier
     * would be non-positive, must both come back as rejections rather than as free rank deficiencies.
     */
    @Test
    public void testTestwiseDeletionGuardsReject() throws Exception {
        DataSet full = simulate(twoLatentGraph(), 400, 99L);
        DataSet holes = full.copy();

        // X1 and X4 are observed on disjoint halves of the rows, so no row has both.
        int half = holes.getNumRows() / 2;
        for (int i = 0; i < holes.getNumRows(); i++) {
            if (i < half) holes.setDouble(i, 0, Double.NaN);
            else holes.setDouble(i, 3, Double.NaN);
        }

        TestwiseDeletionRankTest twd = new TestwiseDeletionRankTest(holes);
        assertFalse(twd.failToReject(new int[]{0, 1}, new int[]{3, 4}, 1, 0.01));
        assertEquals(0.0, twd.pValue(new int[]{0, 1}, new int[]{3, 4}, 1), 0.0);
        assertEquals(2, twd.getSkippedQueries());

        // A tiny minimum sample size lets the no-complete-rows union through the first guard; the multiplier guard
        // still has to catch it.
        TestwiseDeletionRankTest small = new TestwiseDeletionRankTest(holes);
        small.setMinSampleSize(10);
        assertFalse(small.failToReject(new int[]{0, 1}, new int[]{3, 4}, 1, 0.01));
        assertTrue(small.getSkippedQueries() > 0);
        assertTrue(small.getSampleSizeSummary().contains("column unions tested"));
    }

    /**
     * Testwise deletion and pooled imputations are different modes and must not be combined silently.
     */
    @Test
    public void testTestwiseDeletionRefusesImputations() throws Exception {
        DataSet full = simulate(twoLatentGraph(), 500, 5L);
        Rlcd rlcd = new Rlcd(List.of(full, full.copy()));
        try {
            rlcd.setTestwiseDeletion(true);
            fail("Expected an IllegalArgumentException.");
        } catch (IllegalArgumentException expected) {
            // Wanted.
        }
    }


    /**
     * With no missing values, the EM-covariance rank test on the sample covariance with full N must reproduce the
     * complete-data test exactly, and the mean-pairwise rule must give the same N as full N.
     */
    @Test
    public void testEmRankTestMatchesCompleteData() throws Exception {
        DataSet data = simulate(twoLatentGraph(), 1500, 31L);
        Chi2RankTest full = new Chi2RankTest(data);
        EmCovarianceEstimator em = new EmCovarianceEstimator(data);
        EmCovarianceRankTest emTest = new EmCovarianceRankTest(em.estimate(), em.getPairwiseCounts(),
                data.getNumRows());
        int[][] ps = {{0, 1}, {0, 1, 2}, {3, 4}};
        int[][] qs = {{2, 3, 4, 5}, {3, 4, 5}, {0, 1, 2}};
        for (int i = 0; i < ps.length; i++) {
            assertEquals(data.getNumRows(), emTest.sampleSize(ps[i], qs[i]), 1e-9);
            for (int r = 0; r < 2; r++) {
                assertEquals(full.pValue(ps[i], qs[i], r), emTest.pValue(ps[i], qs[i], r), 1e-6);
            }
        }
    }

    /**
     * The EM policy should recover the two-latent model at 30 percent missing completely at random, where the
     * testwise policy's queries have too few complete rows to be sure of it.
     */
    @Test
    public void testEmPolicyRecoversTwoLatents() throws Exception {
        DataSet full = simulate(twoLatentGraph(), 3000, 4242L);
        DataSet holes = full.copy();
        Random random = new Random(4242L);
        for (int i = 0; i < holes.getNumRows(); i++) {
            for (int j = 1; j < holes.getNumColumns(); j++) {
                if (random.nextDouble() < 0.3) holes.setDouble(i, j, Double.NaN);
            }
        }
        Rlcd rlcd = new Rlcd(holes);
        rlcd.setMissingDataSpec(edu.cmu.tetrad.data.missing.MissingDataSpec.emCovariance()
                .withEssMode(edu.cmu.tetrad.data.missing.MissingDataSpec.EffectiveSampleSizeMode.MEAN_PAIRWISE));
        rlcd.setMaxK(2);
        rlcd.setAlpha(0.01);
        rlcd.setSeed(1L);
        Graph out = rlcd.search();
        Set<Set<String>> clusters = new HashSet<>();
        for (Node l : out.getNodes()) {
            if (l.getNodeType() != NodeType.LATENT) continue;
            Set<String> kids = new HashSet<>();
            for (Node c : out.getChildren(l)) if (c.getNodeType() != NodeType.LATENT) kids.add(c.getName());
            clusters.add(kids);
        }
        assertTrue("clusters were " + clusters, clusters.contains(Set.of("X1", "X2", "X3")));
        assertTrue("clusters were " + clusters, clusters.contains(Set.of("X4", "X5", "X6")));
    }

    /**
     * The censored estimator with nothing censored must return the sample covariance (to Monte Carlo noise of the
     * draws there are none of, so exactly), and with the top 30 percent of each indicator censored at a known bound
     * it must recover the correlation matrix far better than treating those values as missing at random.
     */
    @Test
    public void testCensoredEstimatorRecoversCorrelations() throws Exception {
        DataSet full = simulate(twoLatentGraph(), 3000, 77L);
        int p = full.getNumColumns();

        CensoredMvnCovarianceEstimator none = new CensoredMvnCovarianceEstimator(full, null, null);
        none.setIterations(10, 5);
        double[][] exact = new CovarianceMatrix(full).getMatrix().toArray();
        double[][] got = none.estimate().getMatrix().toArray();
        for (int a = 0; a < p; a++) {
            for (int b = 0; b < p; b++) assertEquals(exact[a][b] * (full.getNumRows() - 1.0) / full.getNumRows(),
                    got[a][b], 1e-9);
        }
        assertEquals(0, none.getCensoredCells());

        double[] upper = new double[p];
        DataSet clipped = full.copy();
        for (int j = 0; j < p; j++) {
            double[] col = new double[full.getNumRows()];
            for (int i = 0; i < col.length; i++) col[i] = full.getDouble(i, j);
            java.util.Arrays.sort(col);
            upper[j] = col[(int) (0.7 * (col.length - 1))];
            for (int i = 0; i < col.length; i++) {
                if (clipped.getDouble(i, j) >= upper[j]) clipped.setDouble(i, j, upper[j]);   // recorded at the bound
            }
        }
        double[][] truth = corr(new CovarianceMatrix(full).getMatrix().toArray());
        double[][] naive = corr(new CovarianceMatrix(clipped).getMatrix().toArray());
        CensoredMvnCovarianceEstimator est = new CensoredMvnCovarianceEstimator(clipped, upper, null);
        double[][] censored = corr(est.estimate().getMatrix().toArray());
        assertEquals(full.getNumRows() * p * 3 / 10, est.getCensoredCells(), full.getNumRows() * p / 100);
        assertTrue("deterministic form should converge before the cap", est.getNumIterationsRun() < 60);

        // Deterministic: a second estimator over the same data gives the identical matrix.
        double[][] again = new CensoredMvnCovarianceEstimator(clipped, upper, null).estimate().getMatrix().toArray();
        for (int a = 0; a < p; a++) for (int b = 0; b < p; b++) assertEquals(again[a][b],
                est.estimate().getMatrix().toArray()[a][b], 0.0);

        // The stochastic form agrees with it closely.
        CensoredMvnCovarianceEstimator stoch = new CensoredMvnCovarianceEstimator(clipped, upper, null);
        stoch.setStochastic(true);
        stoch.setSeed(3L);
        double[][] stochastic = corr(stoch.estimate().getMatrix().toArray());

        double naiveErr = 0, censoredErr = 0, stochErr = 0, disagreement = 0;
        int pairs = 0;
        for (int a = 0; a < p; a++) {
            for (int b = a + 1; b < p; b++) {
                naiveErr += Math.abs(naive[a][b] - truth[a][b]);
                censoredErr += Math.abs(censored[a][b] - truth[a][b]);
                stochErr += Math.abs(stochastic[a][b] - truth[a][b]);
                disagreement += Math.abs(stochastic[a][b] - censored[a][b]);
                pairs++;
            }
        }
        naiveErr /= pairs;
        censoredErr /= pairs;
        stochErr /= pairs;
        disagreement /= pairs;
        assertTrue("naive " + naiveErr + " censored " + censoredErr, censoredErr < naiveErr / 3);
        assertTrue("censored mean abs error " + censoredErr, censoredErr < 0.03);
        assertTrue("stochastic mean abs error " + stochErr, stochErr < 0.03);
        assertTrue("forms disagree by " + disagreement, disagreement < 0.02);
    }

    private static double[][] corr(double[][] cov) {
        int p = cov.length;
        double[][] out = new double[p][p];
        for (int a = 0; a < p; a++) {
            for (int b = 0; b < p; b++) out[a][b] = cov[a][b] / Math.sqrt(cov[a][a] * cov[b][b]);
        }
        return out;
    }


    /**
     * The qualification of observed causes. With P --> O known, P --> O --> L --> {X1..X4} gives O --> L: P depends
     * on the indicators marginally and not given O. P --> O <-- L --> {X1..X4} gives L --> O: P is independent of
     * the indicators marginally and dependent given O. Which placement stage 2 makes is immaterial: the check reads
     * the arrow off the output and corrects it from P.
     */
    @Test
    public void testQualifyObservedCauses() throws Exception {
        for (boolean oCauses : new boolean[]{true, false}) {
            Graph g = new EdgeListGraph();
            Node p = new GraphNode("P"), o = new GraphNode("O"), l = new GraphNode("L");
            l.setNodeType(NodeType.LATENT);
            g.addNode(p);
            g.addNode(o);
            g.addNode(l);
            g.addDirectedEdge(p, o);
            if (oCauses) g.addDirectedEdge(o, l);
            else g.addDirectedEdge(l, o);
            for (int i = 1; i <= 4; i++) {
                Node x = new GraphNode("X" + i);
                g.addNode(x);
                g.addDirectedEdge(l, x);
            }
            DataSet data = simulate(g, 4000, 11L);

            Rlcd rlcd = new Rlcd(data);
            rlcd.setMaxK(2);
            rlcd.setSeed(1L);
            if (oCauses) {
                // Without a known direction on P -- O the chain P - O - L - X is rank-equivalent either way;
                // the tiers supply P --> O, and the check then identifies O --> L from it.
                Knowledge k = new Knowledge();
                k.addToTier(0, "P");
                k.addToTier(1, "O");
                for (int i = 1; i <= 4; i++) k.addToTier(1, "X" + i);
                rlcd.setKnowledge(k);
            }
            Graph out = rlcd.search();
            Node oo = out.getNode("O");
            Node latent = null;
            for (Node v : out.getNodes()) {
                if (v.getNodeType() == NodeType.LATENT && out.getChildren(v).contains(out.getNode("X1"))) latent = v;
            }
            assertNotNull("a latent over the X's: " + out, latent);
            Edge e = out.getEdge(oo, latent);
            assertNotNull("O adjacent to the latent: " + out, e);
            if (oCauses) {
                assertTrue("O --> L should be kept: " + out, out.isParentOf(oo, latent));
            } else {
                assertTrue("L --> O should hold (O is a collider child): " + out, out.isParentOf(latent, oo));
            }
        }
    }


    /**
     * The stability summary is stated over observed variables only, since latent names are not comparable across
     * runs. Two runs agree with the main graph on one cluster and split the other.
     */
    @Test
    public void testStabilitySummaryCounts() {
        Graph main = latentGraph(new String[][]{{"A", "B", "C"}, {"D", "E", "F"}}, "A-D");
        Graph run1 = latentGraph(new String[][]{{"A", "B", "C"}, {"D", "E", "F"}}, "A-D");
        Graph run2 = latentGraph(new String[][]{{"A", "B", "C"}, {"D", "E"}, {"F"}}, "");
        RlcdStabilitySummary s = new RlcdStabilitySummary(main, List.of(run1, run2));
        assertEquals(2, s.getNumRuns());
        assertEquals(1.0, s.coClusterFrequency("A", "B"), 1e-12);
        assertEquals(1.0, s.coClusterFrequency("D", "E"), 1e-12);
        assertEquals(0.5, s.coClusterFrequency("E", "F"), 1e-12);
        assertEquals(0.0, s.coClusterFrequency("A", "D"), 1e-12);
        assertEquals(0.5, s.adjacencyFrequency("A", "D"), 1e-12);
        String report = s.report();
        assertTrue(report, report.contains("F 0.50"));
        assertTrue(report, report.contains("A --- D 0.50"));
        assertTrue(report, report.contains("2 latents in 1 run") && report.contains("3 latents in 1 run"));
    }

    /**
     * The wrapper's stability runs: three bootstrap reruns on a two-latent model, main graph unchanged, attribute
     * attached. Also checks that a time limit of zero stops after the first rerun.
     */
    @Test
    public void testStabilityRunsThroughWrapper() throws Exception {
        Graph g = new EdgeListGraph();
        Node l1 = new GraphNode("L1"), l2 = new GraphNode("L2");
        l1.setNodeType(NodeType.LATENT);
        l2.setNodeType(NodeType.LATENT);
        g.addNode(l1);
        g.addNode(l2);
        g.addDirectedEdge(l1, l2);
        for (int i = 1; i <= 4; i++) {
            Node x = new GraphNode("X" + i), y = new GraphNode("Y" + i);
            g.addNode(x);
            g.addNode(y);
            g.addDirectedEdge(l1, x);
            g.addDirectedEdge(l2, y);
        }
        DataSet data = simulate(g, 1500, 3L);

        Parameters params = new Parameters();
        params.set(Params.ALPHA, 0.01);
        params.set(Params.RLCD_MAX_K, 2);
        params.set(Params.SEED, 5L);
        params.set(Params.RLCD_STABILITY_RUNS, 3);
        edu.cmu.tetrad.algcomparison.algorithm.other.Rlcd wrapper = new edu.cmu.tetrad.algcomparison.algorithm.other.Rlcd();
        Graph out = wrapper.search(data, params);
        Object attr = out.getAllAttributes().get("RLCD stability");
        assertNotNull(attr);
        assertTrue(attr.toString(), attr.toString().contains("over 3 bootstrap runs"));

        params.set(Params.RLCD_STABILITY_TIME_LIMIT, 0.0);
        out = wrapper.search(data, params);
        attr = out.getAllAttributes().get("RLCD stability");
        assertTrue(attr.toString(), attr.toString().contains("1 of 3 requested runs completed"));
    }

    /**
     * A covariance matrix is all RLCD needs: over the latent-over-latents model, which exercises stage 1, the stage-2
     * cluster search, and the rank-test orientation of the latent edges (the block test over covers), the search over
     * the sample covariance matrix returns the same graph as the search over the data, and so does the search over
     * the correlation matrix, since neither stage depends on the scale of the variables.
     */
    @Test
    public void testCovarianceInputMatchesData() throws Exception {
        Graph g = new EdgeListGraph();
        Node top = new GraphNode("T0");
        top.setNodeType(NodeType.LATENT);
        g.addNode(top);
        int x = 1;
        for (int i = 1; i <= 4; i++) {
            Node t = new GraphNode("T" + i);
            t.setNodeType(NodeType.LATENT);
            g.addNode(t);
            g.addDirectedEdge(top, t);
            for (int j = 0; j < 3; j++) {
                Node o = new GraphNode("X" + x++);
                g.addNode(o);
                g.addDirectedEdge(t, o);
            }
        }
        DataSet data = simulate(g, 10000, 31L);

        Graph fromData = covarianceRun(new Rlcd(data));
        Graph fromCov = covarianceRun(new Rlcd(new CovarianceMatrix(data)));
        Graph fromCorr = covarianceRun(new Rlcd(new edu.cmu.tetrad.data.CorrelationMatrix(data)));

        assertEquals("Covariance input should give the data's graph:\n" + fromData + "\n" + fromCov,
                fromData, fromCov);
        assertEquals("Correlation input should give the data's graph:\n" + fromData + "\n" + fromCorr,
                fromData, fromCorr);

        int latents = 0;
        for (Node node : fromCov.getNodes()) if (node.getNodeType() == NodeType.LATENT) latents++;
        assertEquals("Five latents expected: " + fromCov, 5, latents);

        // The wrapper takes the matrix too, and agrees with itself on the data.
        edu.cmu.tetrad.algcomparison.algorithm.other.Rlcd wrapper
                = new edu.cmu.tetrad.algcomparison.algorithm.other.Rlcd();
        Parameters params = new Parameters();
        params.set(Params.RLCD_MAX_K, 2);
        params.set(Params.ALPHA, 0.01);
        params.set(Params.SEED, 1L);
        params.set(Params.RLCD_STABILITY_RUNS, 2); // skipped over a matrix, with a log line, not an error
        Graph wrapped = wrapper.search(new CovarianceMatrix(data), params);
        params.set(Params.RLCD_STABILITY_RUNS, 0);
        assertEquals(wrapper.search(data, params), wrapped);
        assertNull("No stability summary can be computed from a matrix", wrapped.getAllAttributes().get("RLCD stability"));
    }

    private static Graph covarianceRun(Rlcd rlcd) throws Exception {
        rlcd.setMaxK(2);
        rlcd.setAlpha(0.01);
        rlcd.setSeed(1L);
        return rlcd.search();
    }

    /**
     * Over a covariance matrix, the options that need rows are refused by name rather than silently ignored.
     */
    @Test
    public void testCovarianceInputRefusesRowOptions() throws Exception {
        DataSet data = simulate(twoLatentGraph(), 500, 7L);
        CovarianceMatrix cov = new CovarianceMatrix(data);

        Rlcd rlcd = new Rlcd(cov);
        try {
            rlcd.setMissingDataSpec(MissingDataSpec.emCovariance());
            fail("A missing-data policy needs rows");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("covariance matrix"));
        }
        rlcd.setMissingDataSpec(null); // clearing is always allowed

        edu.cmu.tetrad.algcomparison.algorithm.other.Rlcd wrapper
                = new edu.cmu.tetrad.algcomparison.algorithm.other.Rlcd();
        Parameters params = new Parameters();
        params.set(Params.STRATIFY_BY_DOMAIN, true);
        params.set(Params.MISSING_DATA_POLICY, "em");
        try {
            wrapper.search(cov, params);
            fail("Stratification and a missing-data policy need rows");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("stratifyByDomain"));
            assertTrue(e.getMessage(), e.getMessage().contains("missingDataPolicy"));
        }

        // A matrix without a sample size cannot calibrate the rank tests.
        try {
            new Rlcd(new CovarianceMatrix(cov.getVariables(), cov.getMatrix(), 1));
            fail("Sample size of 1 should be refused");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("sample size"));
        }
    }

    private static Graph latentGraph(String[][] clusters, String observedEdge) {
        Graph g = new EdgeListGraph();
        int k = 0;
        for (String[] cluster : clusters) {
            Node l = new GraphNode("L" + (++k));
            l.setNodeType(NodeType.LATENT);
            g.addNode(l);
            for (String name : cluster) {
                if (g.getNode(name) == null) g.addNode(new GraphNode(name));
                g.addDirectedEdge(l, g.getNode(name));
            }
        }
        if (!observedEdge.isEmpty()) {
            String[] ab = observedEdge.split("-");
            g.addUndirectedEdge(g.getNode(ab[0]), g.getNode(ab[1]));
        }
        return g;
    }

}
