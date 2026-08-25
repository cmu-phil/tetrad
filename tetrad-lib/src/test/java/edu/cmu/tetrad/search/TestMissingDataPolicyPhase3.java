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

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.algcomparison.independence.MinimaxCITest;
import edu.cmu.tetrad.algcomparison.independence.NeykovMinimaxCITest;
import edu.cmu.tetrad.algcomparison.independence.PoissonBicTest;
import edu.cmu.tetrad.algcomparison.independence.ProbabilisticTest;
import edu.cmu.tetrad.algcomparison.score.BdeuScore;
import edu.cmu.tetrad.algcomparison.score.ConditionalGaussianBicScore;
import edu.cmu.tetrad.algcomparison.score.DegenerateGaussianBicScore;
import edu.cmu.tetrad.algcomparison.score.DiscreteBicScore;
import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.data.VerticalIntDataBox;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Pins the Phase 3 settlement of missing-data support across the remaining tests and scores: components discovered
 * to be natively test-wise are declared and gated as such and exercised end-to-end (Probabilistic, Minimax,
 * Neykov-Minimax); the native-policy sets of the original spec-aware score wrappers are corrected to what their
 * components actually implement (BDeu, Discrete BIC, and CG-BIC are test-wise only; DG-BIC is listwise-only since
 * its indicator embedding is undefined for missing values); and the Poisson Prior Test gains the same EM-covariance
 * route as its score. Everything else supports listwise (and fail) through the gate, which is the honest offering
 * for kernel, feature-embedding, and block components whose machinery has no principled per-test row treatment.
 *
 * @author josephramsey
 */
public class TestMissingDataPolicyPhase3 {

    private static DataSet continuousWithMissing() {
        int n = 300;
        List<Node> vars = new ArrayList<>();
        for (int j = 0; j < 4; j++) vars.add(new ContinuousVariable("X" + (j + 1)));

        Random rng = new Random(38472L);
        double[][] data = new double[n][4];

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < 4; j++) {
                data[i][j] = rng.nextGaussian();
            }
        }

        for (int k = 0; k < 30; k++) {
            data[rng.nextInt(n)][rng.nextInt(4)] = Double.NaN;
        }

        return new BoxDataSet(new DoubleDataBox(data), vars);
    }

    private static DataSet discreteWithMissing() {
        int n = 300;
        List<Node> vars = new ArrayList<>();
        for (int j = 0; j < 3; j++) vars.add(new DiscreteVariable("D" + (j + 1), 3));

        Random rng = new Random(91827L);
        int[][] columns = new int[3][n];

        for (int j = 0; j < 3; j++) {
            for (int i = 0; i < n; i++) {
                columns[j][i] = rng.nextInt(3);
            }
        }

        for (int k = 0; k < 20; k++) {
            columns[rng.nextInt(3)][rng.nextInt(n)] = DiscreteVariable.MISSING_VALUE;
        }

        return new BoxDataSet(new VerticalIntDataBox(columns), vars);
    }

    private static Parameters policy(String policy) {
        Parameters parameters = new Parameters();
        parameters.set(Params.MISSING_DATA_POLICY, policy);
        return parameters;
    }

    /**
     * Runs one unconditional and one conditional independence check on the given test, asserting the p-value (where
     * defined) is not NaN-poisoned into a crash; the point is that the test-wise machinery actually executes on
     * incomplete data.
     */
    private static void exercise(IndependenceTest test) throws InterruptedException {
        List<Node> v = test.getVariables();
        assertNotNull(test.checkIndependence(v.get(0), v.get(1), Collections.emptySet()));
        assertNotNull(test.checkIndependence(v.get(0), v.get(1), Collections.singleton(v.get(2))));
    }

    /**
     * The Probabilistic (BC-inference) test performs test-wise deletion natively (per-test complete rows) and runs
     * end-to-end on incomplete data.
     */
    @Test
    public void testProbabilisticNativelyTestwise() throws InterruptedException {
        DataSet disc = discreteWithMissing();

        IndependenceTest test = new ProbabilisticTest().getTest(disc, policy("testwise"));
        exercise(test);

        assertThrows(IllegalArgumentException.class, () -> new ProbabilisticTest().getTest(disc, policy("em")));
        assertThrows(IllegalArgumentException.class, () -> new ProbabilisticTest().getTest(disc, new Parameters()));
    }

    /**
     * The minimax tests filter each test's rows to those complete on the involved variables (rowsCompleteFor) and
     * run end-to-end on incomplete data.
     */
    @Test
    public void testMinimaxTestsNativelyTestwise() throws InterruptedException {
        DataSet cont = continuousWithMissing();

        exercise(new MinimaxCITest().getTest(cont, policy("testwise")));
        exercise(new NeykovMinimaxCITest().getTest(cont, policy("testwise")));

        assertThrows(IllegalArgumentException.class, () -> new MinimaxCITest().getTest(cont, policy("em")));
    }

    /**
     * The discrete and conditional-Gaussian score wrappers advertise exactly what their components implement:
     * test-wise succeeds, em is refused at the gate with the standard message.
     */
    @Test
    public void testDiscreteAndCgScoreNativeSetsCorrected() {
        DataSet disc = discreteWithMissing();

        assertNotNull(new BdeuScore().getScore(disc, policy("testwise")));
        assertNotNull(new DiscreteBicScore().getScore(disc, policy("testwise")));

        IllegalArgumentException e1 = assertThrows(IllegalArgumentException.class,
                () -> new BdeuScore().getScore(disc, policy("em")));
        assertTrue(e1.getMessage().contains("natively 'testwise'"));

        assertThrows(IllegalArgumentException.class, () -> new DiscreteBicScore().getScore(disc, policy("em")));
        assertThrows(IllegalArgumentException.class, () -> new ConditionalGaussianBicScore().getScore(disc, policy("em")));
        assertNotNull(new ConditionalGaussianBicScore().getScore(disc, policy("testwise")));
    }

    /**
     * DG-BIC is listwise-only (its indicator embedding is undefined for missing values): the gate now refuses
     * test-wise and em up front, while listwise succeeds.
     */
    @Test
    public void testDgBicListwiseOnly() {
        DataSet disc = discreteWithMissing();

        assertNotNull(new DegenerateGaussianBicScore().getScore(disc, policy("listwise")));
        assertThrows(IllegalArgumentException.class,
                () -> new DegenerateGaussianBicScore().getScore(disc, policy("testwise")));
        assertThrows(IllegalArgumentException.class,
                () -> new DegenerateGaussianBicScore().getScore(disc, policy("em")));
    }

    /**
     * The Poisson Prior Test gains the same EM-covariance route as its score wrapper, and the resulting test runs
     * end-to-end.
     */
    @Test
    public void testPoissonBicTestEmRoute() throws InterruptedException {
        DataSet cont = continuousWithMissing();

        IndependenceTest test = new PoissonBicTest().getTest(cont, policy("em"));
        exercise(test);

        assertNotNull(new PoissonBicTest().getTest(cont, policy("listwise")));
        assertThrows(IllegalArgumentException.class, () -> new PoissonBicTest().getTest(cont, policy("testwise")));
        assertThrows(IllegalArgumentException.class, () -> new PoissonBicTest().getTest(cont, new Parameters()));
    }
}
