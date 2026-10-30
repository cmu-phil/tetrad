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
import edu.cmu.tetrad.data.KnowledgeTierStructure;
import edu.cmu.tetrad.data.missing.MissingDataSpec;
import edu.cmu.tetrad.data.missing.MvnImputer;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.rlcd.Chi2RankTest;
import edu.cmu.tetrad.search.rlcd.PooledRankTest;
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

        Rlcd plain = new Rlcd(data);
        plain.setMaxK(2);
        plain.setSeed(1L);
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
}
