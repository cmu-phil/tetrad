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

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.search.Boss;
import edu.cmu.tetrad.search.PermutationSearch;
import edu.cmu.tetrad.search.score.BgeScore;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;
import org.apache.commons.math3.special.Gamma;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Regression tests for {@link BgeScore}.
 */
public class TestBgeScore {

    private static final double TOL = 1e-8;

    /**
     * The telescoped local score must equal the difference of the subset marginal likelihoods
     * log p(D_{Pa,Y}) - log p(D_{Pa}) computed independently from the full multivariate-gamma formula of Kuipers,
     * Moffa, and Heckerman (2014), eq. (10).
     */
    @Test
    public void testLocalScoreMatchesSubsetFormula() {
        DataSet data = randomData(200, 5, 11L);
        BgeScore score = new BgeScore(data);
        score.setAlphaMu(1.0);
        score.setAlphaWOffset(2.0);

        CovarianceMatrix cov = new CovarianceMatrix(data);
        int p = data.getNumColumns();
        int n = data.getNumRows();
        double alphaMu = 1.0;
        double alphaW = p + 2.0;
        double t = alphaMu * (alphaW - p - 1) / (alphaMu + 1);

        int[][] families = {{0}, {1, 0}, {2, 0, 1}, {4, 1, 2, 3}, {3, 4}};

        for (int[] fam : families) {
            int y = fam[0];
            int[] pa = new int[fam.length - 1];
            System.arraycopy(fam, 1, pa, 0, pa.length);
            int[] paY = new int[fam.length];
            System.arraycopy(pa, 0, paY, 0, pa.length);
            paY[pa.length] = y;

            double expected = logMarginal(cov, paY, n, p, alphaMu, alphaW, t)
                    - logMarginal(cov, pa, n, p, alphaMu, alphaW, t);
            double actual = score.localScore(y, pa);

            assertEquals("family " + y + " | " + java.util.Arrays.toString(pa), expected, actual, 1e-6);
        }
    }

    /**
     * Score equivalence: Markov-equivalent DAGs receive the same total score, and a collider does not.
     */
    @Test
    public void testScoreEquivalenceOnChain() {
        DataSet data = randomData(300, 3, 5L);
        BgeScore score = new BgeScore(data);

        double chain = score.localScore(0) + score.localScore(1, 0) + score.localScore(2, 1);   // 0 -> 1 -> 2
        double reverse = score.localScore(2) + score.localScore(1, 2) + score.localScore(0, 1); // 0 <- 1 <- 2
        double fork = score.localScore(1) + score.localScore(0, 1) + score.localScore(2, 1);    // 0 <- 1 -> 2
        double collider = score.localScore(0) + score.localScore(2) + score.localScore(1, 0, 2); // 0 -> 1 <- 2

        assertEquals(chain, reverse, TOL);
        assertEquals(chain, fork, TOL);
        assertTrue(Math.abs(chain - collider) > 1e-3);
    }

    /**
     * Score equivalence on a random DAG: reversing a covered edge leaves the total score unchanged.
     */
    @Test
    public void testCoveredEdgeReversalPreservesScore() {
        RandomUtil.getInstance().setSeed(17L);
        Graph dag = RandomGraph.randomGraph(8, 0, 14, 100, 100, 100, false);
        DataSet data = simulate(dag, 400);
        BgeScore score = new BgeScore(data);
        List<Node> vars = data.getVariables();

        int reversed = 0;

        for (Edge e : new ArrayList<>(dag.getEdges())) {
            Node x = e.getNode1();
            Node y = e.getNode2();
            if (!dag.isParentOf(x, y)) {
                Node tmp = x;
                x = y;
                y = tmp;
            }

            List<Node> paX = dag.getParents(x);
            List<Node> paY = new ArrayList<>(dag.getParents(y));
            paY.remove(x);

            if (!(paX.containsAll(paY) && paY.containsAll(paX))) continue; // not covered

            double before = scoreDag(score, dag, vars);
            Graph flipped = new EdgeListGraph(dag);
            flipped.removeEdge(x, y);
            flipped.addDirectedEdge(y, x);
            double after = scoreDag(score, flipped, vars);

            assertEquals("covered edge " + x + " -> " + y, before, after, 1e-6 * Math.abs(before));
            reversed++;
        }

        assertTrue("expected at least one covered edge in the random DAG", reversed > 0);
    }

    /**
     * The DAG ranking is invariant to the units in which variables are measured.
     */
    @Test
    public void testScaleInvarianceOfRanking() {
        DataSet data = randomData(250, 4, 3L);
        DataSet scaled = data.copy();
        double[] c = {3.0, 0.2, 10.0, 0.5};

        for (int j = 0; j < 4; j++) {
            for (int i = 0; i < scaled.getNumRows(); i++) {
                scaled.setDouble(i, j, c[j] * scaled.getDouble(i, j));
            }
        }

        BgeScore s1 = new BgeScore(data);
        BgeScore s2 = new BgeScore(scaled);

        int[][] dagA = {{0}, {1, 0}, {2, 1}, {3, 2, 0}};
        int[][] dagB = {{0}, {1}, {2, 0, 1}, {3, 2}};

        double diff1 = totalScore(s1, dagA) - totalScore(s1, dagB);
        double diff2 = totalScore(s2, dagA) - totalScore(s2, dagB);

        assertEquals(diff1, diff2, 1e-6 * Math.abs(diff1) + 1e-8);
    }

    /**
     * Data set and covariance matrix constructors agree.
     */
    @Test
    public void testCovarianceConstructorAgrees() {
        DataSet data = randomData(150, 4, 23L);
        BgeScore fromData = new BgeScore(data);
        BgeScore fromCov = new BgeScore(new CovarianceMatrix(data));

        assertEquals(fromData.localScore(2, 0, 1), fromCov.localScore(2, 0, 1), TOL);
        assertEquals(fromData.localScore(3), fromCov.localScore(3), TOL);
    }

    /**
     * BOSS with BGe recovers the CPDAG of a moderately sized linear Gaussian DAG.
     */
    @Test
    public void testBossRecoversCpdag() throws InterruptedException {
        RandomUtil.getInstance().setSeed(42L);
        Graph dag = RandomGraph.randomGraph(10, 0, 15, 100, 100, 100, false);
        DataSet data = simulate(dag, 2000);

        BgeScore score = new BgeScore(data);
        Boss boss = new Boss(score);
        boss.setNumStarts(1);
        boss.setUseBes(true);
        PermutationSearch search = new PermutationSearch(boss);
        Graph est = search.search();

        Graph truth = GraphTransforms.dagToCpdag(dag);

        int tp = 0, fp = 0, fn = 0;
        for (Edge e : est.getEdges()) {
            if (truth.isAdjacentTo(truth.getNode(e.getNode1().getName()), truth.getNode(e.getNode2().getName()))) tp++;
            else fp++;
        }
        for (Edge e : truth.getEdges()) {
            if (!est.isAdjacentTo(est.getNode(e.getNode1().getName()), est.getNode(e.getNode2().getName()))) fn++;
        }

        double precision = tp / (double) (tp + fp);
        double recall = tp / (double) (tp + fn);

        assertTrue("adjacency precision " + precision, precision >= 0.95);
        assertTrue("adjacency recall " + recall, recall >= 0.95);
    }

    // ---- helpers ----

    /**
     * log p(D_S) from Kuipers et al. (2014), eq. (10), with T_S = t diag(s_j^2), prior mean = sample mean.
     */
    private static double logMarginal(CovarianceMatrix cov, int[] s, int n, int p, double alphaMu, double alphaW,
                                      double t) {
        int k = s.length;
        if (k == 0) return 0.0;

        Matrix sub = cov.getSelection(s, s);
        double[][] tS = new double[k][k];
        double[][] rS = new double[k][k];
        for (int i = 0; i < k; i++) {
            for (int j = 0; j < k; j++) {
                rS[i][j] = (n - 1.0) * sub.get(i, j);
            }
            tS[i][i] = t * cov.getValue(s[i], s[i]);
            rS[i][i] += tS[i][i];
        }

        double aK = (alphaW - p + k) / 2.0;

        return -(k * n / 2.0) * Math.log(Math.PI)
                + (k / 2.0) * Math.log(alphaMu / (n + alphaMu))
                + logMultiGamma(k, aK + n / 2.0) - logMultiGamma(k, aK)
                + aK * Math.log(new Matrix(tS).det())
                - (aK + n / 2.0) * Math.log(new Matrix(rS).det());
    }

    private static double logMultiGamma(int k, double a) {
        double v = (k * (k - 1) / 4.0) * Math.log(Math.PI);
        for (int j = 1; j <= k; j++) {
            v += Gamma.logGamma(a + (1 - j) / 2.0);
        }
        return v;
    }

    private static double totalScore(BgeScore score, int[][] dag) {
        double total = 0.0;
        for (int[] fam : dag) {
            int[] pa = new int[fam.length - 1];
            System.arraycopy(fam, 1, pa, 0, pa.length);
            total += score.localScore(fam[0], pa);
        }
        return total;
    }

    private static double scoreDag(BgeScore score, Graph dag, List<Node> vars) {
        double total = 0.0;
        for (int i = 0; i < vars.size(); i++) {
            Node y = dag.getNode(vars.get(i).getName());
            List<Node> parents = dag.getParents(y);
            int[] pa = new int[parents.size()];
            for (int j = 0; j < pa.length; j++) {
                pa[j] = indexOf(vars, parents.get(j).getName());
            }
            total += score.localScore(i, pa);
        }
        return total;
    }

    private static int indexOf(List<Node> vars, String name) {
        for (int i = 0; i < vars.size(); i++) {
            if (vars.get(i).getName().equals(name)) return i;
        }
        throw new IllegalArgumentException(name);
    }

    private static DataSet simulate(Graph dag, int n) {
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
     * Correlated Gaussian data with a simple triangular dependence so that scores are nontrivial.
     */
    private static DataSet randomData(int n, int p, long seed) {
        Random rnd = new Random(seed);
        double[][] d = new double[n][p];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < p; j++) {
                double v = rnd.nextGaussian();
                if (j > 0) v += 0.6 * d[i][j - 1];
                d[i][j] = v;
            }
        }
        List<Node> vars = new ArrayList<>();
        for (int j = 0; j < p; j++) vars.add(new ContinuousVariable("X" + (j + 1)));
        return new BoxDataSet(new DoubleDataBox(d), vars);
    }
}
