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
import edu.cmu.tetrad.data.missing.MissingDataSpec;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.test.FfCi;
import edu.cmu.tetrad.search.test.FfCiContinuous;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Verifies the test-wise deletion behavior of the FFCI tests. The identity checked is the one that must hold for any
 * per-test computation: with missing values confined to one column, a test whose variables do not include that
 * column is evaluated on the same rows as on complete data and must return the same p-value, and a test that does
 * include the column must run on its complete rows and return a finite p-value in [0, 1]. The wrapper checks fail
 * on a jar whose FFCI wrappers reject the 'testwise' policy, and the end-to-end check reproduces the reported
 * failure path: BFCI with FFCI as the test on data with missing values under the 'testwise' policy.
 *
 * @author josephramsey
 */
public class TestFfCiTestwiseDeletion {

    private static final int N = 300;

    /**
     * Constructs a new test.
     */
    public TestFfCiTestwiseDeletion() {
    }

    /**
     * Continuous data over X0..X3; the "missing" copy has NaNs confined to X3.
     */
    private static DataSet[] continuousWithMissingLast(long seed, double missingRate) {
        Random rand = new Random(seed);
        List<Node> vars = new ArrayList<>();
        for (int j = 0; j < 4; j++) vars.add(new ContinuousVariable("X" + j));

        DataSet full = new BoxDataSet(new DoubleDataBox(N, 4), vars);
        for (int i = 0; i < N; i++) {
            double x0 = rand.nextGaussian();
            double x1 = 0.7 * x0 + rand.nextGaussian();
            double x2 = 0.5 * x1 + rand.nextGaussian();
            double x3 = 0.6 * x2 - 0.3 * x0 + rand.nextGaussian();
            full.setDouble(i, 0, x0);
            full.setDouble(i, 1, x1);
            full.setDouble(i, 2, x2);
            full.setDouble(i, 3, x3);
        }

        DataSet missing = full.copy();
        for (int i = 0; i < N; i++) {
            if (rand.nextDouble() < missingRate) missing.setDouble(i, 3, Double.NaN);
        }

        return new DataSet[]{full, missing};
    }

    /**
     * With missing values confined to X3, FFCI-Continuous under the testwise spec returns the complete-data p-value
     * for a test not involving X3, and a finite p-value in [0, 1] for a test involving it.
     */
    @Test
    public void ffciContinuousTestwiseIdentity() throws InterruptedException {
        DataSet[] pair = continuousWithMissingLast(11L, 0.2);
        DataSet full = pair[0], missing = pair[1];

        FfCiContinuous tFull = new FfCiContinuous(full);
        FfCiContinuous tMiss = new FfCiContinuous(missing, MissingDataSpec.testwise());

        Node x0 = missing.getVariable("X0");
        Node x1 = missing.getVariable("X1");
        Node x2 = missing.getVariable("X2");
        Node x3 = missing.getVariable("X3");

        IndependenceResult rFull = tFull.checkIndependence(full.getVariable("X0"),
                full.getVariable("X1"), Set.of(full.getVariable("X2")));
        IndependenceResult rMiss = tMiss.checkIndependence(x0, x1, Set.of(x2));

        assertEquals(rFull.getPValue(), rMiss.getPValue(), 1e-12);

        IndependenceResult rInvolving = tMiss.checkIndependence(x2, x3, Set.of(x1));
        assertTrue(Double.isFinite(rInvolving.getPValue()));
        assertTrue(rInvolving.getPValue() >= 0.0 && rInvolving.getPValue() <= 1.0);

        // The temporary row override must be restored: the uninvolved test still matches complete data afterwards.
        IndependenceResult rAgain = tMiss.checkIndependence(x0, x1, Set.of(x2));
        assertEquals(rFull.getPValue(), rAgain.getPValue(), 1e-12);
    }

    /**
     * The mixed FFCI with a discrete column carrying discrete missing values: the continuous-only test matches
     * complete data, and tests involving the discrete column run on its complete rows.
     */
    @Test
    public void ffciMixedTestwiseIdentity() throws InterruptedException {
        DataSet[] pair = continuousWithMissingLast(13L, 0.0);
        DataSet full = pair[0];

        // Append a discrete child of X2.
        List<Node> vars = new ArrayList<>(full.getVariables());
        DiscreteVariable d = new DiscreteVariable("D", 3);
        vars.add(d);
        DataSet mixedFull = new BoxDataSet(new MixedDataBox(vars, N), vars);
        Random rand = new Random(17);
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < 4; j++) mixedFull.setDouble(i, j, full.getDouble(i, j));
            double u = full.getDouble(i, 2) + 0.5 * rand.nextGaussian();
            mixedFull.setInt(i, 4, u < -0.5 ? 0 : (u < 0.5 ? 1 : 2));
        }

        DataSet mixedMiss = mixedFull.copy();
        for (int i = 0; i < N; i++) {
            if (rand.nextDouble() < 0.2) mixedMiss.setInt(i, 4, DiscreteVariable.MISSING_VALUE);
        }
        assertTrue(mixedMiss.existsMissingValue());

        FfCi tFull = new FfCi(mixedFull);
        FfCi tMiss = new FfCi(mixedMiss, new Parameters(), MissingDataSpec.testwise());

        IndependenceResult rFull = tFull.checkIndependence(mixedFull.getVariable("X0"),
                mixedFull.getVariable("X1"), Set.of(mixedFull.getVariable("X2")));
        IndependenceResult rMiss = tMiss.checkIndependence(mixedMiss.getVariable("X0"),
                mixedMiss.getVariable("X1"), Set.of(mixedMiss.getVariable("X2")));

        assertEquals(rFull.getPValue(), rMiss.getPValue(), 1e-12);

        IndependenceResult rInvolving = tMiss.checkIndependence(mixedMiss.getVariable("X1"),
                mixedMiss.getVariable("D"), Set.of(mixedMiss.getVariable("X2")));
        assertTrue(Double.isFinite(rInvolving.getPValue()));
        assertTrue(rInvolving.getPValue() >= 0.0 && rInvolving.getPValue() <= 1.0);
    }

    /**
     * The wrappers accept 'testwise' on data with missing values, refuse with no policy, and refuse 'em'.
     */
    @Test
    public void wrappersGateTestwise() {
        DataSet missing = continuousWithMissingLast(19L, 0.2)[1];

        Parameters testwise = new Parameters();
        testwise.set(Params.MISSING_DATA_POLICY, "testwise");
        assertNotNull(new edu.cmu.tetrad.algcomparison.independence.FfCi().getTest(missing, testwise));
        assertNotNull(new edu.cmu.tetrad.algcomparison.independence.FfCiContinuous().getTest(missing, testwise));

        try {
            new edu.cmu.tetrad.algcomparison.independence.FfCi().getTest(missing, new Parameters());
            fail("FFCI wrapper should refuse missing data with no policy.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("missing"));
        }

        Parameters em = new Parameters();
        em.set(Params.MISSING_DATA_POLICY, "em");
        try {
            new edu.cmu.tetrad.algcomparison.independence.FfCiContinuous().getTest(missing, em);
            fail("FFCI-Continuous wrapper should refuse the 'em' policy.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("'em'"));
        }
    }

    /**
     * Searches such as StarFci call checkIndependence from parallel streams on one test instance, and the
     * test-wise row override mutates shared state, so the override must serialize concurrent calls. This hammers
     * one testwise instance from a parallel stream and requires (a) no exceptions and (b) every p-value equal to
     * the one a fresh instance computes sequentially — each test's result is deterministic given the fact, so any
     * difference means a test was computed on the wrong rows. On the unsynchronized version this fails with
     * IndexOutOfBoundsException from activeRowIndex (the reported BFCI + FFCI crash).
     */
    @Test
    public void ffciTestwiseIsSafeUnderParallelCalls() throws InterruptedException {
        DataSet missing = wideMissingData(5L);
        List<Node> vars = missing.getVariables();
        List<int[]> facts = allFactsOneConditioner(vars.size());

        FfCiContinuous seq = new FfCiContinuous(missing, MissingDataSpec.testwise());
        seq.setNumFeatXY(6);
        seq.setNumFeatZ(12);
        double[] expected = new double[facts.size()];
        for (int k = 0; k < facts.size(); k++) {
            int[] f = facts.get(k);
            expected[k] = seq.checkIndependence(vars.get(f[0]), vars.get(f[1]),
                    Set.of(vars.get(f[2]))).getPValue();
        }

        FfCiContinuous par = new FfCiContinuous(missing, MissingDataSpec.testwise());
        par.setNumFeatXY(6);
        par.setNumFeatZ(12);
        for (int rep = 0; rep < 3; rep++) {
            double[] actual = new double[facts.size()];
            java.util.stream.IntStream.range(0, facts.size()).parallel().forEach(k -> {
                int[] f = facts.get(k);
                try {
                    actual[k] = par.checkIndependence(vars.get(f[0]), vars.get(f[1]),
                            Set.of(vars.get(f[2]))).getPValue();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            });
            assertArrayEquals(expected, actual, 1e-12);
        }
    }

    /**
     * RCIT shares the row-override pattern and had the same latent race; same check.
     */
    @Test
    public void rcitTestwiseIsSafeUnderParallelCalls() throws InterruptedException {
        DataSet missing = wideMissingData(7L);
        List<Node> vars = missing.getVariables();
        List<int[]> facts = allFactsOneConditioner(vars.size());

        edu.cmu.tetrad.search.test.Rcit seq = new edu.cmu.tetrad.search.test.Rcit(missing,
                new Parameters(), MissingDataSpec.testwise());
        seq.setNumFeaturesXY(5);
        seq.setNumFeaturesZ(10);
        double[] expected = new double[facts.size()];
        for (int k = 0; k < facts.size(); k++) {
            int[] f = facts.get(k);
            expected[k] = seq.checkIndependence(vars.get(f[0]), vars.get(f[1]),
                    Set.of(vars.get(f[2]))).getPValue();
        }

        edu.cmu.tetrad.search.test.Rcit par = new edu.cmu.tetrad.search.test.Rcit(missing,
                new Parameters(), MissingDataSpec.testwise());
        par.setNumFeaturesXY(5);
        par.setNumFeaturesZ(10);
        double[] actual = new double[facts.size()];
        java.util.stream.IntStream.range(0, facts.size()).parallel().forEach(k -> {
            int[] f = facts.get(k);
            try {
                actual[k] = par.checkIndependence(vars.get(f[0]), vars.get(f[1]),
                        Set.of(vars.get(f[2]))).getPValue();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        });
        assertArrayEquals(expected, actual, 1e-12);
    }

    /**
     * Gaussian noise over 8 variables with 10 percent MCAR everywhere.
     */
    private static DataSet wideMissingData(long seed) {
        int n = 400, p = 8;
        Random rand = new Random(seed);
        List<Node> vars = new ArrayList<>();
        for (int j = 0; j < p; j++) vars.add(new ContinuousVariable("X" + j));
        DataSet data = new BoxDataSet(new DoubleDataBox(n, p), vars);
        for (int i = 0; i < n; i++)
            for (int j = 0; j < p; j++)
                data.setDouble(i, j, rand.nextGaussian());
        for (int i = 0; i < n; i++)
            for (int j = 0; j < p; j++)
                if (rand.nextDouble() < 0.1) data.setDouble(i, j, Double.NaN);
        assertTrue(data.existsMissingValue());
        return data;
    }

    /**
     * All ordered facts x _||_ y | {c} over p variables.
     */
    private static List<int[]> allFactsOneConditioner(int p) {
        List<int[]> facts = new ArrayList<>();
        for (int a = 0; a < p; a++)
            for (int b = a + 1; b < p; b++)
                for (int c = 0; c < p; c++)
                    if (c != a && c != b) facts.add(new int[]{a, b, c});
        return facts;
    }

    /**
     * The reported failure path: BFCI with FFCI as the test on data with missing values runs to completion under
     * the 'testwise' policy.
     */
    @Test
    public void bfciWithFfciRunsTestwise() throws InterruptedException {
        DataSet missing = continuousWithMissingLast(23L, 0.1)[1];

        Parameters parameters = new Parameters();
        parameters.set(Params.MISSING_DATA_POLICY, "testwise");
        parameters.set(Params.RCIT_NUM_FEATURES_XY, 8);
        parameters.set(Params.RCIT_NUM_FEATURES_Z, 25);

        Graph out = new edu.cmu.tetrad.algcomparison.algorithm.oracle.pag.Bfci(
                new edu.cmu.tetrad.algcomparison.independence.FfCi(),
                new edu.cmu.tetrad.algcomparison.score.SemBicScore())
                .search(missing, parameters);

        assertNotNull(out);
        assertEquals(missing.getNumColumns(), out.getNumNodes());
    }
}
