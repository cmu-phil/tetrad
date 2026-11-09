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

import edu.cmu.tetrad.search.utils.NonlinearityTests;
import edu.cmu.tetrad.search.utils.NonlinearityTests.TestResult;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import java.util.Arrays;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Regression tests for {@link NonlinearityTests}, pinning two fixes verified by the calibration harness:
 * <ul>
 * <li><b>Internal centering</b>: the tests fit without an intercept column, so before the fix any linear
 * relationship with a nonzero intercept (the typical case for raw real data) was misspecified under the null and all
 * of RESET, the conditional-moment test, and the additive-hinge test rejected "linearity" with probability near 1.
 * The tests now center y and X internally, on copies.</li>
 * <li><b>Honored seeds</b>: rffCosSin previously ignored its seed and drew random features from the global
 * RandomUtil stream, so the training and test matrices in cvLinearVsNonlinear were embedded with different features;
 * predictions were noise, the nonlinear model always lost the cross-validated comparison, and the test had zero
 * power everywhere. Seeds are now honored with local generators throughout, which also makes the CV tests
 * deterministic given their inputs.</li>
 * </ul>
 * Known limitation, deliberately not asserted here: heteroskedastic but linear-in-mean data inflates the type I
 * error of the three F/LM tests above nominal (roughly 10-35 percent at alpha = 0.05 in the harness); see the class
 * Javadoc of NonlinearityTests.
 *
 * @author josephramsey
 */
public class TestNonlinearityTests {

    /**
     * Constructs a new test.
     */
    public TestNonlinearityTests() {
    }

    /**
     * A linear relationship with a large intercept, passed raw (uncentered), must not be flagged nonlinear. Before
     * the centering fix, RESET, conditional-moment, and additive-hinge all rejected here with probability near 1.
     * The alpha of 0.01 with a fixed seed keeps this test stable against the nominal false-rejection rate.
     */
    @Test
    public void testLinearWithInterceptNotFlagged() {
        Random rng = new Random(88);
        int n = 500;
        double[] y = new double[n];
        double[][] X = new double[n][1];

        for (int i = 0; i < n; i++) {
            double x = rng.nextGaussian();
            X[i][0] = x;
            y[i] = 10 + x + rng.nextGaussian();
        }

        double alpha = 0.01;
        assertFalse("RESET must not reject raw linear-with-intercept",
                NonlinearityTests.resetTest(y, X, alpha).reject);
        assertFalse("Conditional-moment must not reject raw linear-with-intercept",
                NonlinearityTests.conditionalMomentTest(y, X, alpha).reject);
        assertFalse("Additive-hinge must not reject raw linear-with-intercept",
                NonlinearityTests.additiveHingeTest(y, X, alpha).reject);
        assertFalse("CV linear-vs-RFF must not reject raw linear-with-intercept",
                NonlinearityTests.cvLinearVsNonlinear(y, X, 5, alpha).reject);
    }

    /**
     * A strong quadratic must be flagged by all four tests. The CV assertion is the regression test for the
     * rffCosSin seed fix: before it, cvLinearVsNonlinear had zero power on this scenario.
     */
    @Test
    public void testQuadraticFlaggedByAllFour() {
        Random rng = new Random(89);
        int n = 500;
        double[] y = new double[n];
        double[][] X = new double[n][1];

        for (int i = 0; i < n; i++) {
            double x = rng.nextGaussian();
            X[i][0] = x;
            y[i] = x * x + rng.nextGaussian();
        }

        double alpha = 0.05;
        assertTrue("RESET must reject quadratic",
                NonlinearityTests.resetTest(y, X, alpha).reject);
        assertTrue("Conditional-moment must reject quadratic",
                NonlinearityTests.conditionalMomentTest(y, X, alpha).reject);
        assertTrue("Additive-hinge must reject quadratic",
                NonlinearityTests.additiveHingeTest(y, X, alpha).reject);
        assertTrue("CV linear-vs-RFF must reject quadratic (seed fix)",
                NonlinearityTests.cvLinearVsNonlinear(y, X, 5, alpha).reject);
    }

    /**
     * The CV tests must be deterministic given their inputs (local seeded generators), independent of the global
     * RandomUtil stream's state. Perturbs the global stream between calls and requires identical statistics.
     */
    @Test
    public void testCvTestsDeterministic() {
        Random rng = new Random(90);
        int n = 300;
        double[] y = new double[n];
        double[][] X = new double[n][2];

        for (int i = 0; i < n; i++) {
            double x1 = rng.nextGaussian(), x2 = rng.nextGaussian();
            X[i][0] = x1;
            X[i][1] = x2;
            y[i] = x1 * x2 + rng.nextGaussian();
        }

        TestResult cv1 = NonlinearityTests.cvLinearVsNonlinear(y, X, 5, 0.05);
        TestResult addit1 = NonlinearityTests.cvAdditiveVsRff(y, X, 5, 0.05);

        // Perturb the global stream; the tests must not care.
        for (int i = 0; i < 1000; i++) RandomUtil.getInstance().nextDouble();

        TestResult cv2 = NonlinearityTests.cvLinearVsNonlinear(y, X, 5, 0.05);
        TestResult addit2 = NonlinearityTests.cvAdditiveVsRff(y, X, 5, 0.05);

        assertEquals("cvLinearVsNonlinear must be deterministic", cv1.statistic, cv2.statistic, 0.0);
        assertEquals("cvAdditiveVsRff must be deterministic", addit1.statistic, addit2.statistic, 0.0);
    }

    /**
     * cvAdditiveVsRff must have power on its one job: a pure interaction, which no additive model can represent.
     * Before the redesign (nested models on a shared additive base, a single GCV-tuned ridge selected on the full
     * model, and pooled per-observation signed-rank inference), the full RFF model had more features than training
     * rows at near-interpolation regularization, lost the CV comparison even here, and the test rejected in only
     * about 5-12 percent of replications; after it, rejection is essentially certain at this n and effect size.
     */
    @Test
    public void testAdditivityCheckFlagsPureInteraction() {
        Random rng = new Random(92);
        int n = 400;
        double[] y = new double[n];
        double[][] X = new double[n][2];

        for (int i = 0; i < n; i++) {
            double x1 = rng.nextGaussian(), x2 = rng.nextGaussian();
            X[i][0] = x1;
            X[i][1] = x2;
            y[i] = x1 * x2 + rng.nextGaussian();
        }

        TestResult res = NonlinearityTests.cvAdditiveVsRff(y, X, 5, 0.05);
        assertTrue("cvAdditiveVsRff must flag a pure interaction", res.reject);
        assertTrue("statistic must favor the full model on an interaction", res.statistic > 0);
    }

    /**
     * cvAdditiveVsRff must NOT flag additive-but-smooth truths. This pins the per-variable RFF enrichment of the
     * additive design: with a hinge-only additive base, the joint RFF block absorbed the hinge basis's approximation
     * bias on smooth additive functions and this scenario was falsely flagged non-additive about 45 percent of the
     * time. The alpha of 0.01 with a fixed seed keeps this test stable against the nominal false-rejection rate.
     */
    @Test
    public void testAdditivityCheckNotFooledBySmoothAdditive() {
        Random rng = new Random(93);
        int n = 400;
        double[] y = new double[n];
        double[][] X = new double[n][2];

        for (int i = 0; i < n; i++) {
            double x1 = rng.nextGaussian(), x2 = rng.nextGaussian();
            X[i][0] = x1;
            X[i][1] = x2;
            y[i] = x1 * x1 + Math.sin(2 * x2) + 0.5 * rng.nextGaussian();
        }

        TestResult res = NonlinearityTests.cvAdditiveVsRff(y, X, 5, 0.01);
        assertFalse("cvAdditiveVsRff must not flag smooth additive nonlinearity", res.reject);
    }

    /**
     * Internal centering must operate on copies: caller arrays are not modified.
     */
    @Test
    public void testCallerArraysNotModified() {
        Random rng = new Random(91);
        int n = 100;
        double[] y = new double[n];
        double[][] X = new double[n][1];

        for (int i = 0; i < n; i++) {
            double x = 5 + rng.nextGaussian();
            X[i][0] = x;
            y[i] = 10 + x + rng.nextGaussian();
        }

        double[] yCopy = Arrays.copyOf(y, n);
        double[][] xCopy = new double[n][1];
        for (int i = 0; i < n; i++) xCopy[i][0] = X[i][0];

        NonlinearityTests.resetTest(y, X, 0.05);
        NonlinearityTests.conditionalMomentTest(y, X, 0.05);
        NonlinearityTests.additiveHingeTest(y, X, 0.05);

        for (int i = 0; i < n; i++) {
            assertEquals("y must not be modified", yCopy[i], y[i], 0.0);
            assertEquals("X must not be modified", xCopy[i][0], X[i][0], 0.0);
        }
    }
}
