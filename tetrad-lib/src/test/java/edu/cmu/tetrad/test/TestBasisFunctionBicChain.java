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
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.Boss;
import edu.cmu.tetrad.search.PermutationSearch;
import edu.cmu.tetrad.search.score.BasisFunctionBicScore;
import edu.cmu.tetrad.search.score.BasisFunctionBicScoreFullSample;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertTrue;

/**
 * Verifies the 2026-8 restoration of the chain-rule conditioning in the basis function BIC
 * scores (BasisFunctionBicScore and BasisFunctionBicScoreFullSample). With the conditioning
 * restored, each embedded component of a variable's block is scored given the parents' blocks
 * AND the earlier components of its own block, so the summed log-likelihoods telescope to the
 * joint Gaussian log-likelihood of the block. The total DAG score is then a penalized joint
 * likelihood and is score-equivalent: all DAGs in a Markov equivalence class receive the same
 * total score.
 * <p>
 * The score-equivalence tests below fail against the pre-restoration classes, which scored each
 * component against the parents' blocks only (a diagonal-residual sum that is not a joint
 * likelihood). The remaining tests guard that the restored score still separates Markov
 * equivalence classes and supports structure recovery with BOSS.
 */
public class TestBasisFunctionBicChain {

    private static final double REL_TOL = 1e-6;

    /**
     * Two-variable score equivalence on nonlinear data: X -&gt; Y and Y -&gt; X are Markov
     * equivalent, so their total scores must be equal. Fails against the pre-2026-8 classes.
     */
    @Test
    public void testTwoVariableScoreEquivalence() {
        DataSet data = twoVarNonlinearData(new Random(52), 1500);

        BasisFunctionBicScore score = new BasisFunctionBicScore(data, 3, 0.0);
        score.setPenaltyDiscount(2);

        double xToY = score.localScore(0) + score.localScore(1, 0); // X source, Y | X
        double yToX = score.localScore(1) + score.localScore(0, 1); // Y source, X | Y

        double relDiff = relDiff(xToY, yToX);
        System.out.printf("Two-variable equivalence (covariance score): score(X->Y) = %.6f, "
                + "score(Y->X) = %.6f, relative difference = %.3g%n", xToY, yToX, relDiff);
        assertTrue("Markov-equivalent DAGs X->Y and Y->X should receive equal total scores; "
                + "relative difference = " + relDiff, relDiff < REL_TOL);
    }

    /**
     * Three-variable score equivalence on nonlinear data generated from the chain
     * X -&gt; Z -&gt; Y: the chain, the reversed chain, and the fork X &lt;- Z -&gt; Y form one
     * Markov equivalence class and must receive identical total scores. Fails against the
     * pre-2026-8 classes.
     */
    @Test
    public void testThreeVariableMecScoreEquivalence() {
        DataSet data = chainNonlinearData(new Random(53), 1500);

        BasisFunctionBicScore score = new BasisFunctionBicScore(data, 3, 0.0);
        score.setPenaltyDiscount(2);

        // Variables: 0 = X, 1 = Z, 2 = Y.
        double chain = score.localScore(0) + score.localScore(1, 0) + score.localScore(2, 1);
        double reversed = score.localScore(2) + score.localScore(1, 2) + score.localScore(0, 1);
        double fork = score.localScore(1) + score.localScore(0, 1) + score.localScore(2, 1);

        double d1 = relDiff(chain, reversed);
        double d2 = relDiff(chain, fork);
        System.out.printf("Three-variable MEC equivalence (covariance score): chain = %.6f, "
                + "reversed = %.6f, fork = %.6f%n", chain, reversed, fork);
        assertTrue("Chain and reversed chain should score equally; relative difference = " + d1,
                d1 < REL_TOL);
        assertTrue("Chain and fork should score equally; relative difference = " + d2,
                d2 < REL_TOL);
    }

    /**
     * Same two-variable equivalence check for the full-sample (tabular) score. Fails against
     * the pre-2026-8 classes.
     */
    @Test
    public void testTwoVariableScoreEquivalenceFullSample() {
        DataSet data = twoVarNonlinearData(new Random(54), 1500);

        BasisFunctionBicScoreFullSample score = new BasisFunctionBicScoreFullSample(data, 3, 0.0);
        score.setPenaltyDiscount(2);

        double xToY = score.localScore(0) + score.localScore(1, 0);
        double yToX = score.localScore(1) + score.localScore(0, 1);

        double relDiff = relDiff(xToY, yToX);
        System.out.printf("Two-variable equivalence (full-sample score): score(X->Y) = %.6f, "
                + "score(Y->X) = %.6f, relative difference = %.3g%n", xToY, yToX, relDiff);
        assertTrue("Markov-equivalent DAGs X->Y and Y->X should receive equal total scores; "
                + "relative difference = " + relDiff, relDiff < REL_TOL);
    }

    /**
     * Guard: equivalence must not come from the score being degenerate. On data generated from
     * the chain X -&gt; Z -&gt; Y, the collider X -&gt; Z &lt;- Y (a different Markov equivalence
     * class over the same adjacencies) must score strictly worse than the chain.
     */
    @Test
    public void testColliderScoresWorseOnChainData() {
        DataSet data = chainNonlinearData(new Random(55), 1500);

        BasisFunctionBicScore score = new BasisFunctionBicScore(data, 3, 0.0);
        score.setPenaltyDiscount(2);

        double chain = score.localScore(0) + score.localScore(1, 0) + score.localScore(2, 1);
        double collider = score.localScore(0) + score.localScore(2) + score.localScore(1, 0, 2);

        System.out.printf("MEC separation: chain = %.6f, collider = %.6f (chain should be "
                + "higher)%n", chain, collider);
        assertTrue("On chain-generated data the chain should outscore the collider; got chain = "
                + chain + ", collider = " + collider, chain > collider);
    }

    /**
     * Structure recovery sanity: BOSS with the restored score on additive nonlinear data from a
     * known five-node DAG should recover the skeleton essentially exactly at n = 2000.
     */
    @Test
    public void testBossRecoveryWithRestoredScore() {
        Random rng = new Random(56);
        int n = 2000;

        // DAG: X1 -> X2 -> X4, X1 -> X3 -> X4, X4 -> X5 (five edges).
        double[][] d = new double[n][5];
        for (int i = 0; i < n; i++) {
            double x1 = rng.nextGaussian();
            double x2 = x1 * x1 + 0.7 * rng.nextGaussian();
            double x3 = Math.sin(2 * x1) + 0.7 * rng.nextGaussian();
            double x4 = 0.5 * x2 * x2 + Math.tanh(2 * x3) + 0.7 * rng.nextGaussian();
            double x5 = Math.cos(2 * x4) + 0.7 * rng.nextGaussian();
            d[i][0] = x1;
            d[i][1] = x2;
            d[i][2] = x3;
            d[i][3] = x4;
            d[i][4] = x5;
        }
        List<Node> vars = new ArrayList<>();
        for (int j = 1; j <= 5; j++) vars.add(new ContinuousVariable("X" + j));
        DataSet data = new BoxDataSet(new DoubleDataBox(d), vars);

        BasisFunctionBicScore score = new BasisFunctionBicScore(data, 3, 0.0);
        score.setPenaltyDiscount(2);

        Graph cpdag;
        try {
            cpdag = new PermutationSearch(new Boss(score)).search();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        String[][] trueAdj = {{"X1", "X2"}, {"X1", "X3"}, {"X2", "X4"}, {"X3", "X4"}, {"X4", "X5"}};
        int tp = 0;
        for (String[] pair : trueAdj) {
            if (cpdag.isAdjacentTo(cpdag.getNode(pair[0]), cpdag.getNode(pair[1]))) tp++;
        }
        int estEdges = cpdag.getNumEdges();
        int fp = estEdges - tp;
        System.out.printf("BOSS recovery: %d/5 true adjacencies found, %d extra edges; "
                + "estimated CPDAG:%n", tp, fp);
        for (Edge e : cpdag.getEdges()) System.out.println("    " + e);

        assertTrue("BOSS with the restored score should recover at least 4/5 true adjacencies, "
                + "got " + tp, tp >= 4);
        assertTrue("BOSS with the restored score should add at most 2 spurious adjacencies, "
                + "got " + fp, fp <= 2);
    }

    // ------------------------------------------------------------------------------------------

    private static double relDiff(double a, double b) {
        return Math.abs(a - b) / Math.max(1.0, Math.max(Math.abs(a), Math.abs(b)));
    }

    /**
     * Two variables with a nonlinear link: y = x^2 + x + noise. The quadratic term makes the
     * higher-order embedded components load, so the chain conditioning matters.
     */
    private static DataSet twoVarNonlinearData(Random rng, int n) {
        double[][] d = new double[n][2];
        for (int i = 0; i < n; i++) {
            double x = rng.nextGaussian();
            d[i][0] = x;
            d[i][1] = x * x + x + 0.7 * rng.nextGaussian();
        }
        List<Node> vars = new ArrayList<>();
        vars.add(new ContinuousVariable("X"));
        vars.add(new ContinuousVariable("Y"));
        return new BoxDataSet(new DoubleDataBox(d), vars);
    }

    /**
     * Chain X -&gt; Z -&gt; Y with nonlinear links. Variable order: 0 = X, 1 = Z, 2 = Y.
     */
    private static DataSet chainNonlinearData(Random rng, int n) {
        double[][] d = new double[n][3];
        for (int i = 0; i < n; i++) {
            double x = rng.nextGaussian();
            double z = x * x + 0.7 * rng.nextGaussian();
            double y = Math.tanh(2 * z) + 0.7 * rng.nextGaussian();
            d[i][0] = x;
            d[i][1] = z;
            d[i][2] = y;
        }
        List<Node> vars = new ArrayList<>();
        vars.add(new ContinuousVariable("X"));
        vars.add(new ContinuousVariable("Z"));
        vars.add(new ContinuousVariable("Y"));
        return new BoxDataSet(new DoubleDataBox(d), vars);
    }

    /**
     * Manual runner (the harness is not yet wired into the build).
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        TestBasisFunctionBicChain t = new TestBasisFunctionBicChain();
        t.testTwoVariableScoreEquivalence();
        t.testThreeVariableMecScoreEquivalence();
        t.testTwoVariableScoreEquivalenceFullSample();
        t.testColliderScoresWorseOnChainData();
        t.testBossRecoveryWithRestoredScore();
        System.out.println("ALL TESTS PASSED");
    }
}
