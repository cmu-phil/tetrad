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

package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.search.GRegression;
import edu.cmu.tetrad.search.utils.MeekRules;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for the G-regression estimator of Guo and Perković (2022).
 *
 * @author josephramsey
 */
public class TestGRegression {

    /**
     * The MPDAG of Fig. 1(a) in Guo and Perković (2022): 1 -> 2, 1 -> 3, 1 -> 4, 2 - 3, 3 - 4, 4 -> 5, 4 -> 6,
     * 5 - 6, with buckets {1}, {2, 3, 4}, {5, 6}; the between-bucket edges match the coefficients listed in the
     * paper's Section 6.2.1 (lambda_12, lambda_13, lambda_14, lambda_45, lambda_46). The within-bucket edges are
     * the path 2 - 3 - 4 (no 2 - 4 edge) and 5 - 6.
     */
    private static Graph fig1a() {
        List<Node> nodes = new ArrayList<>();
        for (int i = 1; i <= 6; i++) nodes.add(new GraphNode("X" + i));
        Graph g = new EdgeListGraph(nodes);
        Node x1 = g.getNode("X1"), x2 = g.getNode("X2"), x3 = g.getNode("X3"),
                x4 = g.getNode("X4"), x5 = g.getNode("X5"), x6 = g.getNode("X6");
        g.addDirectedEdge(x1, x2);
        g.addDirectedEdge(x1, x3);
        g.addDirectedEdge(x1, x4);
        g.addUndirectedEdge(x2, x3);
        g.addUndirectedEdge(x3, x4);
        g.addDirectedEdge(x4, x5);
        g.addDirectedEdge(x4, x6);
        g.addUndirectedEdge(x5, x6);
        return g;
    }

    @Test
    public void testFig1aStructure() {
        Graph g = fig1a();
        assertNull(GRegression.mpdagProblem(g));

        List<Set<Node>> buckets = GRegression.bucketDecomposition(g);
        assertEquals(3, buckets.size());

        Set<Node> b3 = null;
        for (Set<Node> b : buckets) if (b.contains(g.getNode("X5"))) b3 = b;
        assertNotNull(b3);
        assertEquals(new HashSet<>(List.of(g.getNode("X5"), g.getNode("X6"))), b3);
        assertEquals(Collections.singleton(g.getNode("X4")), GRegression.externalParents(g, b3));

        for (Set<Node> b : buckets) assertTrue(GRegression.hasRestrictiveProperty(g, b));

        // Identification (Theorem 2).
        Node x1 = g.getNode("X1"), x2 = g.getNode("X2"), x3 = g.getNode("X3"), x4 = g.getNode("X4"),
                x5 = g.getNode("X5"), x6 = g.getNode("X6");
        assertTrue(GRegression.isIdentified(g, Set.of(x1), x5));   // 1's only edges are directed.
        assertTrue(GRegression.isIdentified(g, Set.of(x4), x5));   // 4 -> 5 directed; 4 - 3 - 2 don't reach 5.
        assertFalse(GRegression.isIdentified(g, Set.of(x2), x5));  // 2 - 3 - 4 -> 5 starts undirected.
        assertTrue(GRegression.isIdentified(g, Set.of(x2, x4), x5)); // joint: 2 - 3 - 4 -> 5 is not proper.
        assertFalse(GRegression.isIdentified(g, Set.of(x2, x3), x5)); // joint: 3 - 4 -> 5 is proper and undirected.
        assertFalse(GRegression.isIdentified(g, Set.of(x5), x6));  // 5 - 6.
        assertTrue(GRegression.isIdentified(g, Set.of(x6), x1));   // Not a possible descendant; effect 0.
    }

    /**
     * Removing 4 -> 6 from Fig. 1(a) leaves 4 -> 5 - 6 with 4, 6 nonadjacent, which Meek R1 would orient, so the
     * graph is not an MPDAG and the constructor must refuse it.
     */
    @Test
    public void testRejectsNonMeekClosedGraph() {
        Graph g = fig1a();
        g.removeEdge(g.getNode("X4"), g.getNode("X6"));
        assertNotNull(GRegression.mpdagProblem(g));

        try {
            new GRegression(g, new CovarianceMatrix(g.getNodes(), Matrix.identity(6), 100));
            throw new AssertionError("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    /**
     * Two small MPDAGs that distinguish the strong definition of "possibly causal" (no v_l &lt;- v_r for any
     * l &lt; r) from the successive-edge one.
     */
    @Test
    public void testIdentificationEdgeCases() {
        // (i) a - u, u -> w, a -> w. The path a - u -> w is possibly causal and starts undirected, so the
        // effect of a on w is not identified (it equals g_aw + g_au g_uw in one DAG and g_aw in the other).
        Graph g1 = new EdgeListGraph(List.of(new GraphNode("a"), new GraphNode("u"), new GraphNode("w")));
        g1.addUndirectedEdge(g1.getNode("a"), g1.getNode("u"));
        g1.addDirectedEdge(g1.getNode("u"), g1.getNode("w"));
        g1.addDirectedEdge(g1.getNode("a"), g1.getNode("w"));
        assertNull(GRegression.mpdagProblem(g1));
        assertFalse(GRegression.isIdentified(g1, Set.of(g1.getNode("a")), g1.getNode("w")));

        // (ii) a - u, u - v, v -> a. The successive-edge path a - u - v is NOT possibly causal because of
        // a <- v, and there is no other path, so the effect of a on v is identified (it is zero: every DAG in
        // the class has v -> a).
        Graph g2 = new EdgeListGraph(List.of(new GraphNode("a"), new GraphNode("u"), new GraphNode("v")));
        g2.addUndirectedEdge(g2.getNode("a"), g2.getNode("u"));
        g2.addUndirectedEdge(g2.getNode("u"), g2.getNode("v"));
        g2.addDirectedEdge(g2.getNode("v"), g2.getNode("a"));
        assertNull(GRegression.mpdagProblem(g2));
        assertTrue(GRegression.isIdentified(g2, Set.of(g2.getNode("a")), g2.getNode("v")));

        // (iii) Same skeleton as (ii) but with w -> u instead: a - u - v with w -> u, w -> a, w -> v (w a common
        // parent of the whole bucket). a - u - v is possibly causal, so a on v is not identified.
        Graph g3 = new EdgeListGraph(List.of(new GraphNode("a"), new GraphNode("u"), new GraphNode("v"),
                new GraphNode("w")));
        g3.addUndirectedEdge(g3.getNode("a"), g3.getNode("u"));
        g3.addUndirectedEdge(g3.getNode("u"), g3.getNode("v"));
        for (String s : List.of("a", "u", "v")) g3.addDirectedEdge(g3.getNode("w"), g3.getNode(s));
        assertNull(GRegression.mpdagProblem(g3));
        assertFalse(GRegression.isIdentified(g3, Set.of(g3.getNode("a")), g3.getNode("v")));
        // Jointly, {a, u} on v is still not identified: u - v is a proper possibly causal path from A that
        // starts undirected.
        assertFalse(GRegression.isIdentified(g3, Set.of(g3.getNode("a"), g3.getNode("u")), g3.getNode("v")));
        // w on v is identified: w's edges are all directed.
        assertTrue(GRegression.isIdentified(g3, Set.of(g3.getNode("w")), g3.getNode("v")));
    }

    /**
     * With the population covariance of a linear SEM, G-regression must recover every identified total effect
     * exactly, for the DAG itself, for its CPDAG, and for the CPDAG with some background knowledge. Point and
     * joint interventions are both exercised.
     */
    @Test
    public void testPopulationExactness() {
        RandomUtil.getInstance().setSeed(3849283L);
        int checked = 0;

        for (int rep = 0; rep < 40; rep++) {
            Graph dag = RandomGraph.randomDag(12, 0, 22, 100, 100, 100, false);
            SemPm pm = new SemPm(dag);
            SemIm im = new SemIm(pm);
            List<Node> vars = im.getVariableNodes();
            Matrix gamma = im.getEdgeCoef();               // gamma[i][j] = coef of i -> j
            ICovarianceMatrix cov = new CovarianceMatrix(vars, im.getImplCovar(vars), 1000);

            Graph cpdag = GraphTransforms.dagToCpdag(dag);
            Graph mpdag = withSomeKnowledge(dag, cpdag);

            for (Graph g : List.of(dag, cpdag, mpdag)) {
                GRegression greg = new GRegression(g, cov);

                for (int trial = 0; trial < 30; trial++) {
                    int sizeA = 1 + RandomUtil.getInstance().nextInt(3);
                    List<Node> shuffled = new ArrayList<>(vars);
                    Collections.shuffle(shuffled, new java.util.Random(RandomUtil.getInstance().nextInt()));
                    List<Node> a = new ArrayList<>(shuffled.subList(0, sizeA));
                    Node y = shuffled.get(sizeA);

                    if (!greg.isIdentified(a, y)) continue;

                    double[] est = greg.totalEffect(a, y);
                    double[] truth = trueTotalEffect(gamma, vars, a, y);
                    assertArrayEquals("rep " + rep + " A=" + a + " Y=" + y, truth, est, 1e-8);
                    checked++;
                }
            }
        }

        assertTrue("Too few identified effects were checked: " + checked, checked > 500);
    }

    /**
     * Finite-sample sanity check: with n = 20000 the estimate should be close to the truth for identified
     * joint effects from the CPDAG, and the bootstrap standard error should be of the right order.
     */
    @Test
    public void testFiniteSample() throws Exception {
        RandomUtil.getInstance().setSeed(129837L);
        Graph dag = RandomGraph.randomDag(10, 0, 18, 100, 100, 100, false);
        SemIm im = new SemIm(new SemPm(dag));
        List<Node> vars = im.getVariableNodes();
        Matrix gamma = im.getEdgeCoef();
        DataSet data = im.simulateData(20000, false);
        Graph cpdag = GraphTransforms.dagToCpdag(dag);
        GRegression greg = new GRegression(cpdag, new CovarianceMatrix(data));

        int checked = 0;
        for (int trial = 0; trial < 200 && checked < 10; trial++) {
            List<Node> shuffled = new ArrayList<>(vars);
            Collections.shuffle(shuffled, new java.util.Random(RandomUtil.getInstance().nextInt()));
            List<Node> a = new ArrayList<>(shuffled.subList(0, 2));
            Node y = shuffled.get(2);
            if (!greg.isIdentified(a, y)) continue;

            double[] truth = trueTotalEffect(gamma, vars, a, y);
            GRegression.BootstrapResult r = GRegression.bootstrap(cpdag, data, a, y, 50);
            double[] se = r.standardErrors();

            for (int i = 0; i < 2; i++) {
                assertEquals("A=" + a + " Y=" + y, truth[i], r.effect()[i], 0.1);
                assertTrue("Bootstrap SE unreasonable: " + se[i], se[i] < 0.1);
            }
            checked++;
        }

        assertTrue(checked >= 3);
    }


    /**
     * In a CPDAG (as opposed to a general MPDAG) directed edges only run between chain components, so the strong
     * and successive-edge definitions of "possibly causal" coincide, and Theorem 2 reduces to plain reachability
     * from an undirected neighbor of a treatment through -&gt; and - edges avoiding the treatment set. This
     * cross-checks the unshielded-path search against that simpler computation on random CPDAGs.
     */
    @Test
    public void testIdentificationAgreesWithReachabilityOnCpdags() {
        RandomUtil.getInstance().setSeed(55512L);
        int identified = 0, unidentified = 0;

        for (int rep = 0; rep < 200; rep++) {
            Graph dag = RandomGraph.randomDag(10, 0, 20, 100, 100, 100, false);
            Graph cpdag = GraphTransforms.dagToCpdag(dag);
            List<Node> vars = cpdag.getNodes();

            for (int trial = 0; trial < 20; trial++) {
                List<Node> shuffled = new ArrayList<>(vars);
                Collections.shuffle(shuffled, new java.util.Random(RandomUtil.getInstance().nextInt()));
                int sizeA = 1 + RandomUtil.getInstance().nextInt(3);
                Set<Node> a = new HashSet<>(shuffled.subList(0, sizeA));
                Node y = shuffled.get(sizeA);

                boolean expected = true;
                for (Node ai : a) {
                    for (Node u : cpdag.getAdjacentNodes(ai)) {
                        if (a.contains(u)) continue;
                        if (!edu.cmu.tetrad.graph.Edges.isUndirectedEdge(cpdag.getEdge(ai, u))) continue;
                        if (reachableForward(cpdag, u, y, a)) expected = false;
                    }
                }

                assertEquals("A=" + a + " Y=" + y + "\n" + cpdag, expected, GRegression.isIdentified(cpdag, a, y));
                if (expected) identified++; else unidentified++;
            }
        }

        assertTrue(identified > 100);
        assertTrue(unidentified > 100);
    }

    /** Reachability from 'from' to 'to' along -&gt; and - edges through vertices not in 'avoid'. */
    private static boolean reachableForward(Graph g, Node from, Node to, Set<Node> avoid) {
        Set<Node> seen = new HashSet<>();
        java.util.Deque<Node> queue = new java.util.ArrayDeque<>();
        queue.add(from);
        seen.add(from);
        while (!queue.isEmpty()) {
            Node x = queue.remove();
            if (x == to) return true;
            for (Node w : g.getAdjacentNodes(x)) {
                if (avoid.contains(w) || seen.contains(w)) continue;
                edu.cmu.tetrad.graph.Edge e = g.getEdge(x, w);
                boolean forward = edu.cmu.tetrad.graph.Edges.isUndirectedEdge(e)
                                  || edu.cmu.tetrad.graph.Edges.getDirectedEdgeHead(e) == w;
                if (forward) { seen.add(w); queue.add(w); }
            }
        }
        return false;
    }

    /**
     * True total effect of A on Y from the SEM coefficient matrix: zero the columns of A (cut all edges into
     * the treatments, i.e., the joint intervention), then read off rows A, column Y of (I - Gamma)^{-1}.
     */
    private static double[] trueTotalEffect(Matrix gamma, List<Node> vars, List<Node> a, Node y) {
        int p = vars.size();
        Matrix g = new Matrix(gamma);
        for (Node ai : a) {
            int c = vars.indexOf(ai);
            for (int r = 0; r < p; r++) g.set(r, c, 0.0);
        }
        Matrix t = Matrix.identity(p).minus(g).inverse();
        double[] out = new double[a.size()];
        int yi = vars.indexOf(y);
        for (int i = 0; i < a.size(); i++) out[i] = t.get(vars.indexOf(a.get(i)), yi);
        return out;
    }

    /**
     * Orients up to two undirected edges of the CPDAG in the direction they have in the true DAG (as background
     * knowledge) and closes under Meek's rules, giving an MPDAG that still contains the true DAG.
     */
    private static Graph withSomeKnowledge(Graph dag, Graph cpdag) {
        Graph mpdag = new EdgeListGraph(cpdag);
        Knowledge knowledge = new Knowledge();
        int oriented = 0;

        for (edu.cmu.tetrad.graph.Edge e : new ArrayList<>(mpdag.getEdges())) {
            if (oriented >= 2) break;
            if (!edu.cmu.tetrad.graph.Edges.isUndirectedEdge(e)) continue;
            Node x = e.getNode1(), z = e.getNode2();
            Node tail = dag.isParentOf(dag.getNode(x.getName()), dag.getNode(z.getName())) ? x : z;
            Node head = tail == x ? z : x;
            mpdag.removeEdge(e);
            mpdag.addDirectedEdge(tail, head);
            knowledge.setRequired(tail.getName(), head.getName());
            oriented++;
        }

        MeekRules meek = new MeekRules();
        meek.setKnowledge(knowledge);
        meek.setRevertToUnshieldedColliders(false);
        meek.setVerbose(false);
        meek.orientImplied(mpdag);
        assertNull(GRegression.mpdagProblem(mpdag));
        return mpdag;
    }
}
