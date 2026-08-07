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

import edu.cmu.tetrad.algcomparison.independence.BasisFunctionLrt;
import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.test.IndTestBasisFunctionBlocks;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.assertTrue;

/**
 * Verifies the 2026-8 rerouting of the BF-LRT wrapper to the Wilks/Bartlett block test
 * (IndTestBasisFunctionBlocks / IndTestBlocksWilkes), and the accompanying effective-sample-size
 * fixes. The previous BF-LRT implementation (IndTestBasisFunctionLrt, deprecated) used a
 * trace-averaged residual-variance ratio with |Y-block| degrees of freedom, which is not a
 * likelihood ratio for the block regression and produces non-uniform null p-values.
 * <p>
 * The wrapper-resolution and effective-sample-size tests below fail against the pre-reroute
 * classes; running with the unpatched jar first on the classpath should produce exactly those
 * failures.
 */
public class TestBasisFunctionLrtWilks {

    /**
     * Null calibration: marginal test on independent Gaussians should reject near the nominal
     * rate, with mean p near 0.5. (The Wilks statistic is mildly anticonservative at moderate n
     * because the embedded polynomial columns are heavy-tailed; bounds are set accordingly.
     * The old trace-averaged statistic had mean p well below 0.5 and grew more conservative
     * with n.)
     */
    @Test
    public void testNullCalibrationMarginal() {
        Random rng = new Random(42);
        int reps = 300, n = 500;
        int rejects = 0;
        double sumP = 0.0;

        for (int r = 0; r < reps; r++) {
            double[][] d = new double[n][2];
            for (int i = 0; i < n; i++) {
                d[i][0] = rng.nextGaussian();
                d[i][1] = rng.nextGaussian();
            }
            double p = pValue(makeData(d, "X", "Y"), "X", "Y", null);
            sumP += p;
            if (p < 0.05) rejects++;
        }

        double rejRate = rejects / (double) reps;
        double meanP = sumP / reps;
        System.out.printf("Null marginal: reject@.05 = %.3f, mean p = %.3f%n", rejRate, meanP);

        assertTrue("Rejection rate at alpha=.05 should be near nominal, got " + rejRate,
                rejRate >= 0.02 && rejRate <= 0.15);
        assertTrue("Mean null p-value should be near 0.5, got " + meanP,
                Math.abs(meanP - 0.5) <= 0.08);
    }

    /**
     * Power: y = cos(2x) + noise has near-zero linear correlation with x; the basis-function
     * test should detect the dependence essentially always at n = 2000.
     */
    @Test
    public void testPowerUnderPureNonlinearity() {
        Random rng = new Random(43);
        int reps = 50, detected = 0;

        for (int r = 0; r < reps; r++) {
            double[][] d = new double[2000][2];
            for (int i = 0; i < 2000; i++) {
                double x = rng.nextGaussian();
                d[i][0] = x;
                d[i][1] = Math.cos(2 * x) + 0.7 * rng.nextGaussian();
            }
            if (pValue(makeData(d, "X", "Y"), "X", "Y", null) < 0.05) detected++;
        }

        System.out.printf("Power (y = cos(2x) + noise, n = 2000): %.3f%n", detected / (double) reps);
        assertTrue("Power should be near 1.0, got " + detected + "/" + reps, detected >= reps - 2);
    }

    /**
     * Conditional null: in x -> z -> y (with a nonlinear z -> y link), x _||_ y | z should be
     * accepted at roughly the nominal rate.
     */
    @Test
    public void testConditionalNull() {
        Random rng = new Random(44);
        int reps = 200, rejects = 0;

        for (int r = 0; r < reps; r++) {
            int m = 1000;
            double[][] d = new double[m][3];
            for (int i = 0; i < m; i++) {
                double x = rng.nextGaussian();
                double z = 0.8 * x + 0.6 * rng.nextGaussian();
                double y = Math.tanh(2 * z) + 0.6 * rng.nextGaussian();
                d[i][0] = x;
                d[i][1] = y;
                d[i][2] = z;
            }
            if (pValue(makeData(d, "X", "Y", "Z"), "X", "Y", "Z") < 0.05) rejects++;
        }

        double rate = rejects / (double) reps;
        System.out.printf("Conditional null (x _||_ y | z): reject@.05 = %.3f%n", rate);
        assertTrue("Conditional null rejection rate badly off nominal: " + rate, rate <= 0.25);
    }

    /**
     * setEffectiveSampleSize must change the reported p-value. Under weak dependence, shrinking
     * the effective sample size must increase the p-value. This fails against the pre-2026-8
     * classes twice over: IndTestBlocksWilkes computed p-values with the raw row count, and
     * IndTestBasisFunctionBlocks did not forward the setting to its delegate.
     */
    @Test
    public void testEffectiveSampleSizeAffectsPValues() {
        Random rng = new Random(45);
        double[][] d = new double[1000][2];
        for (int i = 0; i < 1000; i++) {
            double x = rng.nextGaussian();
            d[i][0] = x;
            d[i][1] = 0.15 * x + rng.nextGaussian();
        }
        DataSet data = makeData(d, "X", "Y");

        IndTestBasisFunctionBlocks tFull = new IndTestBasisFunctionBlocks(data, 3, 1);
        tFull.setEffectiveSampleSize(-1);
        double pFull = pOf(tFull, data);

        IndTestBasisFunctionBlocks tSmall = new IndTestBasisFunctionBlocks(data, 3, 1);
        tSmall.setEffectiveSampleSize(100);
        double pSmall = pOf(tSmall, data);

        System.out.printf("effN: p(effN = n) = %.4g, p(effN = 100) = %.4g%n", pFull, pSmall);
        assertTrue("Shrinking effN should increase the p-value; got " + pFull + " -> " + pSmall,
                pSmall > pFull + 1e-6);
    }

    /**
     * The BF-LRT wrapper must resolve to the Wilks blocks test, not the deprecated
     * trace-averaged LRT.
     */
    @Test
    public void testWrapperResolvesToBlocksTest() {
        Random rng = new Random(46);
        double[][] d = new double[100][2];
        for (int i = 0; i < 100; i++) {
            d[i][0] = rng.nextGaussian();
            d[i][1] = rng.nextGaussian();
        }
        DataSet data = makeData(d, "X", "Y");

        Parameters params = new Parameters();
        params.set(Params.TRUNCATION_LIMIT, 3);
        params.set(Params.ALPHA, 0.05);
        params.set(Params.EFFECTIVE_SAMPLE_SIZE, -1);

        IndependenceTest wrapped = new BasisFunctionLrt().getTest(data, params);
        System.out.printf("Wrapper resolves to: %s%n", wrapped.getClass().getSimpleName());
        assertTrue("BF-LRT wrapper should resolve to IndTestBasisFunctionBlocks, got "
                        + wrapped.getClass().getSimpleName(),
                wrapped instanceof IndTestBasisFunctionBlocks);
    }

    /**
     * Continuous-vs-binary with purely nonlinear dependence: y = 1{|x| > 1} (10% label noise).
     * The linear correlation between x and y is ~0 by symmetry, so the dependence is only
     * visible through the higher-order basis columns of x. Prior to the 2026-8 fix,
     * robustifyXY in IndTestBlocksWilkes truncated the continuous block (truncation-limit
     * columns) to the binary block's single indicator column, reducing the test to an
     * effectively linear one; this test fails against those classes.
     */
    @Test
    public void testMixedNonlinearDependenceNotTruncated() {
        Random rng = new Random(47);
        int n = 1000;

        DataSet data = makeMixedData(n);
        for (int i = 0; i < n; i++) {
            double x = rng.nextGaussian();
            int y = (Math.abs(x) > 1.0) ? 1 : 0;
            if (rng.nextDouble() < 0.10) y = 1 - y; // label noise; avoids determinism
            data.setDouble(i, 0, x);
            data.setInt(i, 1, y);
        }

        IndTestBasisFunctionBlocks test = new IndTestBasisFunctionBlocks(data, 3, 1);
        double p = pOf(test, data);
        System.out.printf("Mixed nonlinear (y = 1{|x| > 1}): p = %.4g%n", p);
        assertTrue("Nonlinear dependence of a binary on a continuous variable should be "
                + "detected (p < .01), got p = " + p, p < 0.01);
    }

    /**
     * Continuous-vs-binary null: independent Gaussian x and Bernoulli y. Guards that removing
     * the block truncation does not inflate false positives on mixed data.
     */
    @Test
    public void testMixedNullContinuousVsBinary() {
        Random rng = new Random(48);
        int reps = 200, n = 500;
        int rejects = 0;

        for (int r = 0; r < reps; r++) {
            DataSet data = makeMixedData(n);
            for (int i = 0; i < n; i++) {
                data.setDouble(i, 0, rng.nextGaussian());
                data.setInt(i, 1, rng.nextDouble() < 0.4 ? 1 : 0);
            }
            IndTestBasisFunctionBlocks test = new IndTestBasisFunctionBlocks(data, 3, 1);
            if (pOf(test, data) < 0.05) rejects++;
        }

        double rate = rejects / (double) reps;
        System.out.printf("Mixed null (continuous vs binary): reject@.05 = %.3f%n", rate);
        assertTrue("Mixed null rejection rate should be near nominal, got " + rate,
                rate >= 0.005 && rate <= 0.15);
    }

    // ------------------------------------------------------------------------------------------

    private static DataSet makeMixedData(int n) {
        List<Node> vars = new ArrayList<>();
        vars.add(new ContinuousVariable("X"));
        vars.add(new edu.cmu.tetrad.data.DiscreteVariable("Y", 2));
        return new BoxDataSet(new edu.cmu.tetrad.data.MixedDataBox(vars, n), vars);
    }

    private static DataSet makeData(double[][] d, String... names) {
        List<Node> vars = new ArrayList<>();
        for (String name : names) vars.add(new ContinuousVariable(name));
        return new BoxDataSet(new DoubleDataBox(d), vars);
    }

    private static double pValue(DataSet data, String xName, String yName, String zName) {
        Parameters params = new Parameters();
        params.set(Params.TRUNCATION_LIMIT, 3);
        params.set(Params.ALPHA, 0.05);
        params.set(Params.EFFECTIVE_SAMPLE_SIZE, -1);
        IndependenceTest test = new BasisFunctionLrt().getTest(data, params);
        Node x = data.getVariable(xName);
        Node y = data.getVariable(yName);
        Set<Node> z = new HashSet<>();
        if (zName != null) z.add(data.getVariable(zName));
        try {
            return test.checkIndependence(x, y, z).getPValue();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static double pOf(IndTestBasisFunctionBlocks test, DataSet data) {
        try {
            return test.checkIndependence(data.getVariable("X"), data.getVariable("Y"),
                    new HashSet<>()).getPValue();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Manual runner (the harness is not yet wired into the build).
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        TestBasisFunctionLrtWilks t = new TestBasisFunctionLrtWilks();
        t.testNullCalibrationMarginal();
        t.testPowerUnderPureNonlinearity();
        t.testConditionalNull();
        t.testEffectiveSampleSizeAffectsPValues();
        t.testWrapperResolvesToBlocksTest();
        t.testMixedNonlinearDependenceNotTruncated();
        t.testMixedNullContinuousVsBinary();
        System.out.println("ALL TESTS PASSED");
    }
}
