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

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.Discretizer;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.search.Boss;
import edu.cmu.tetrad.search.PermutationSearch;
import edu.cmu.tetrad.search.score.BasisFunctionBgeScore;
import edu.cmu.tetrad.search.score.BgeScore;
import edu.cmu.tetrad.search.score.DegenerateGaussianBgeScore;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.utils.Embedding;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Regression tests for {@link BasisFunctionBgeScore} and {@link DegenerateGaussianBgeScore}.
 */
public class TestEmbeddedBgeScores {

    /**
     * On continuous data DG-BGe is exactly BGe (same columns, no scaling).
     */
    @Test
    public void testDgBgeEqualsBgeOnContinuousData() {
        DataSet data = continuous(300, 6, 1L);
        BgeScore bge = new BgeScore(data);
        DegenerateGaussianBgeScore dg = new DegenerateGaussianBgeScore(data);

        assertEquals(bge.localScore(3), dg.localScore(3), 1e-8);
        assertEquals(bge.localScore(3, 0, 1), dg.localScore(3, 0, 1), 1e-8);
        assertEquals(bge.localScoreDiff(2, 4, new int[]{0, 5}), dg.localScoreDiff(2, 4, new int[]{0, 5}), 1e-8);
    }

    /**
     * On continuous data BF-BGe with truncation 1 differs from BGe only by the [-1, 1] rescaling of each column,
     * which leaves score differences between DAGs unchanged.
     */
    @Test
    public void testBfBgeTruncationOneMatchesBgeUpToScale() {
        DataSet data = continuous(300, 5, 2L);
        BgeScore bge = new BgeScore(data);
        BasisFunctionBgeScore bf = new BasisFunctionBgeScore(data, 1);

        int[][] dagA = {{0}, {1, 0}, {2, 1}, {3, 2, 0}, {4, 3}};
        int[][] dagB = {{0}, {1}, {2, 0, 1}, {3, 2}, {4, 1, 3}};

        double dBge = total(bge, dagA) - total(bge, dagB);
        double dBf = total(bf, dagA) - total(bf, dagB);

        assertEquals(dBge, dBf, 1e-6 * Math.abs(dBge) + 1e-8);
    }

    /**
     * The block local score equals the chain-rule sum of single-column BGe scores on the embedded data, computed by
     * the independent {@link BgeScore} implementation.
     */
    @Test
    public void testBlockScoreEqualsChainRuleOverEmbeddedColumns() {
        DataSet data = mixed(400, 8, 3, 3L);
        int trunc = 3;
        BasisFunctionBgeScore bf = new BasisFunctionBgeScore(data, trunc);

        Embedding.EmbeddedData emb = Embedding.getEmbeddedData(data, trunc, 1, 1);
        BgeScore colScore = new BgeScore(emb.embeddedData());
        Map<Integer, List<Integer>> map = bf.getEmbedding();

        int[][] families = {{0}, {1, 0}, {5, 0, 2}, {7, 1, 6}, {6, 4, 5, 7}};

        for (int[] fam : families) {
            int y = fam[0];
            int[] pa = new int[fam.length - 1];
            System.arraycopy(fam, 1, pa, 0, pa.length);

            List<Integer> b = new ArrayList<>();
            for (int p : pa) b.addAll(map.get(p));
            double chain = 0.0;
            for (int col : map.get(y)) {
                int[] cond = new int[b.size()];
                for (int i = 0; i < cond.length; i++) cond[i] = b.get(i);
                chain += colScore.localScore(col, cond);
                b.add(col);
            }

            double block = bf.localScore(y, pa);
            assertEquals("family " + y, chain, block, 1e-6 * Math.abs(chain) + 1e-8);
        }
    }

    /**
     * Score equivalence on mixed data: chain, reverse chain, and fork agree; the collider differs.
     */
    @Test
    public void testScoreEquivalenceOnMixedTriples() {
        DataSet data = mixed(400, 3, 1, 4L);
        for (Score s : new Score[]{new DegenerateGaussianBgeScore(data), new BasisFunctionBgeScore(data, 3)}) {
            double chain = s.localScore(0) + s.localScore(1, 0) + s.localScore(2, 1);
            double reverse = s.localScore(2) + s.localScore(1, 2) + s.localScore(0, 1);
            double fork = s.localScore(1) + s.localScore(0, 1) + s.localScore(2, 1);
            double collider = s.localScore(0) + s.localScore(2) + s.localScore(1, 0, 2);

            assertEquals(s.toString(), chain, reverse, 1e-6 * Math.abs(chain));
            assertEquals(s.toString(), chain, fork, 1e-6 * Math.abs(chain));
            assertTrue(s.toString(), Math.abs(chain - collider) > 1e-3);
        }
    }

    /**
     * Covered-edge reversal on a random DAG with mixed data preserves the total score, for both embeddings.
     */
    @Test
    public void testCoveredEdgeReversalOnMixedData() {
        RandomUtil.getInstance().setSeed(5L);
        Graph dag = RandomGraph.randomGraph(8, 0, 14, 100, 100, 100, false);
        DataSet data = discretizeSome(simulate(dag, 400), 3, 3);

        for (Score s : new Score[]{new DegenerateGaussianBgeScore(data), new BasisFunctionBgeScore(data, 3)}) {
            int reversed = 0;
            for (Edge e : new ArrayList<>(dag.getEdges())) {
                Node x = e.getNode1(), y = e.getNode2();
                if (!dag.isParentOf(x, y)) {
                    Node t = x;
                    x = y;
                    y = t;
                }
                List<Node> paX = dag.getParents(x);
                List<Node> paY = new ArrayList<>(dag.getParents(y));
                paY.remove(x);
                if (!(paX.containsAll(paY) && paY.containsAll(paX))) continue;

                double before = scoreDag(s, dag, data.getVariables());
                Graph flipped = new EdgeListGraph(dag);
                flipped.removeEdge(x, y);
                flipped.addDirectedEdge(y, x);
                double after = scoreDag(s, flipped, data.getVariables());

                assertEquals(s + " covered edge " + x + " -> " + y, before, after, 1e-6 * Math.abs(before));
                reversed++;
            }
            assertTrue(reversed > 0);
        }
    }

    /**
     * BOSS with DG-BGe recovers the CPDAG of a linear Gaussian DAG in which three variables have been discretized.
     */
    @Test
    public void testBossDgBgeRecoversMixedCpdag() throws InterruptedException {
        RandomUtil.getInstance().setSeed(42L);
        Graph dag = RandomGraph.randomGraph(10, 0, 15, 100, 100, 100, false);
        DataSet data = discretizeSome(simulate(dag, 3000), 3, 3);

        DegenerateGaussianBgeScore score = new DegenerateGaussianBgeScore(data);
        Boss boss = new Boss(score);
        boss.setNumStarts(1);
        boss.setUseBes(true);
        Graph est = new PermutationSearch(boss).search();
        Graph truth = GraphTransforms.dagToCpdag(dag);

        double[] pr = adjacencyPrecisionRecall(est, truth);
        assertTrue("adjacency precision " + pr[0], pr[0] >= 0.85);
        assertTrue("adjacency recall " + pr[1], pr[1] >= 0.85);
    }

    // ---- helpers ----

    private static double total(Score s, int[][] dag) {
        double t = 0.0;
        for (int[] fam : dag) {
            int[] pa = new int[fam.length - 1];
            System.arraycopy(fam, 1, pa, 0, pa.length);
            t += s.localScore(fam[0], pa);
        }
        return t;
    }

    private static double scoreDag(Score s, Graph dag, List<Node> vars) {
        double total = 0.0;
        for (int i = 0; i < vars.size(); i++) {
            Node y = dag.getNode(vars.get(i).getName());
            List<Node> parents = dag.getParents(y);
            int[] pa = new int[parents.size()];
            for (int j = 0; j < pa.length; j++) {
                pa[j] = indexOf(vars, parents.get(j).getName());
            }
            total += s.localScore(i, pa);
        }
        return total;
    }

    private static int indexOf(List<Node> vars, String name) {
        for (int i = 0; i < vars.size(); i++) if (vars.get(i).getName().equals(name)) return i;
        throw new IllegalArgumentException(name);
    }

    static double[] adjacencyPrecisionRecall(Graph est, Graph truth) {
        int tp = 0, fp = 0, fn = 0;
        for (Edge e : est.getEdges()) {
            if (truth.isAdjacentTo(truth.getNode(e.getNode1().getName()), truth.getNode(e.getNode2().getName()))) tp++;
            else fp++;
        }
        for (Edge e : truth.getEdges()) {
            if (!est.isAdjacentTo(est.getNode(e.getNode1().getName()), est.getNode(e.getNode2().getName()))) fn++;
        }
        return new double[]{tp / (double) Math.max(1, tp + fp), tp / (double) Math.max(1, tp + fn)};
    }

    static DataSet simulate(Graph dag, int n) {
        SemPm pm = new SemPm(dag);
        Parameters params = new Parameters();
        params.set(Params.COEF_LOW, 0.4);
        params.set(Params.COEF_HIGH, 1.0);
        SemIm im = new SemIm(pm, params);
        try {
            return im.simulateData(n, false);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Discretizes the first {@code numDiscrete} variables into {@code cats} equal-count categories.
     */
    static DataSet discretizeSome(DataSet data, int numDiscrete, int cats) {
        Discretizer d = new Discretizer(data);
        for (int j = 0; j < numDiscrete; j++) d.equalCounts(data.getVariable(j), cats);
        return d.discretize();
    }

    private static DataSet continuous(int n, int p, long seed) {
        RandomUtil.getInstance().setSeed(seed);
        Graph dag = RandomGraph.randomGraph(p, 0, p + 2, 100, 100, 100, false);
        return simulate(dag, n);
    }

    private static DataSet mixed(int n, int p, int numDiscrete, long seed) {
        return discretizeSome(continuous(n, p, seed), numDiscrete, 3);
    }
}
