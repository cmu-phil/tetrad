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

import edu.cmu.tetrad.data.AndersonDarlingTest;
import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.data.GeneralAndersonDarlingTest;
import edu.cmu.tetrad.data.MultiGeneralAndersonDarlingTest;
import edu.cmu.tetrad.data.audit.AuditFinding;
import edu.cmu.tetrad.data.audit.DataAudit;
import edu.cmu.tetrad.data.audit.FindingCode;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.graph.Node;
import org.apache.commons.math3.distribution.RealDistribution;
import org.apache.commons.math3.distribution.UniformRealDistribution;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Regression tests for the Anderson-Darling p-value at very large statistics.
 * <p>
 * The Stephens interpolation used for A-squared-star at or above 0.6 is a local quadratic fit with a positive
 * quadratic coefficient. Uncapped, it turns around outside its fitted range: the reported p-value climbs back above
 * any alpha near aa ~ 306 and overflows to +Infinity near aa ~ 402. On large datasets (e.g., the 500,000-row Cover
 * Type data) every skewed column produces an aa in the hundreds to thousands, so the most extreme non-Gaussianity
 * yielded p = Infinity and DataAudit's NON_GAUSSIAN check, which tests p &lt; alpha, silently failed to fire.
 * <p>
 * The fix follows R's nortest::ad.test: for aa &gt;= 10 the p-value is the hard floor 3.7e-24, which is the
 * interpolation's value at aa = 10 and hence continuous at the boundary. These tests fail on the unpatched code
 * (where the p-values below are Infinity) and pass on the patched code.
 *
 * @author josephramsey
 */
public class TestAndersonDarlingLargeStatistic {

    /**
     * An exponential column at n = 20,000 gives an A-squared-star in the hundreds; the p-value must be the nortest
     * floor, not Infinity.
     */
    @Test
    public void testMarginalPValueCappedForExtremeStatistic() {
        Random rng = new Random(42);
        int n = 20000;
        double[] x = new double[n];
        for (int i = 0; i < n; i++) x[i] = -Math.log(1.0 - rng.nextDouble());

        AndersonDarlingTest test = new AndersonDarlingTest(x);

        assertTrue("aa should be far beyond the interpolation's fitted range",
                test.getASquaredStar() > 100.0);
        assertTrue("p must be finite", Double.isFinite(test.getP()));
        assertEquals("p must be the nortest floor for aa >= 10", 3.7e-24, test.getP(), 1e-30);
    }

    /**
     * The p-value must be non-increasing across the aa = 10 boundary rather than jumping upward.
     */
    @Test
    public void testFloorContinuousAtBoundary() {
        double pJustBelow = Math.exp(1.2937 - 5.709 * 9.999 + 0.0186 * 9.999 * 9.999);
        assertTrue("floor must not exceed the interpolated value just below the boundary",
                3.7e-24 <= pJustBelow * 1.01);
    }

    /**
     * DataAudit must flag NON_GAUSSIAN for a heavily non-Gaussian column at large n. On the unpatched code the
     * p-value is Infinity and the finding silently does not fire.
     */
    @Test
    public void testDataAuditFlagsNonGaussianAtLargeN() {
        Random rng = new Random(7);
        int n = 20000;
        double[][] d = new double[n][2];
        for (int i = 0; i < n; i++) {
            d[i][0] = -Math.log(1.0 - rng.nextDouble()); // exponential: heavily non-Gaussian
            d[i][1] = rng.nextGaussian();
        }
        List<Node> vars = new ArrayList<>();
        vars.add(new ContinuousVariable("E1"));
        vars.add(new ContinuousVariable("G1"));
        DataSet ds = new BoxDataSet(new DoubleDataBox(d), vars);

        DataAudit audit = new DataAudit(ds);

        assertTrue("NON_GAUSSIAN must fire for the exponential column",
                audit.getFindings(FindingCode.NON_GAUSSIAN).stream()
                        .anyMatch(f -> f.getVariables().contains("E1")));

        double pE1 = audit.getAdPValues().get("E1");
        assertTrue("stored AD p-value must be finite", Double.isFinite(pE1));
    }

    /**
     * GeneralAndersonDarlingTest (used by the Markov check) must also return a finite, floor-capped p-value when the
     * data are extremely non-uniform relative to the reference distribution.
     */
    @Test
    public void testGeneralPValueCappedForExtremeStatistic() {
        Random rng = new Random(11);
        RealDistribution unif = new UniformRealDistribution(0, 1);
        List<Double> data = new ArrayList<>();
        for (int i = 0; i < 5000; i++) data.add(0.49 + 0.02 * rng.nextDouble()); // clustered near 0.5

        GeneralAndersonDarlingTest test = new GeneralAndersonDarlingTest(data, unif);

        assertTrue("aa should be far beyond the interpolation's fitted range",
                test.getASquaredStar() > 100.0);
        assertTrue("p must be finite", Double.isFinite(test.getP()));
        assertEquals(3.7e-24, test.getP(), 1e-30);
    }

    /**
     * MultiGeneralAndersonDarlingTest must behave the same way under an extreme pooled statistic.
     */
    @Test
    public void testMultiGeneralPValueCappedForExtremeStatistic() {
        Random rng = new Random(13);
        List<List<Double>> data = new ArrayList<>();
        List<RealDistribution> dists = new ArrayList<>();
        for (int g = 0; g < 2; g++) {
            List<Double> col = new ArrayList<>();
            for (int i = 0; i < 2500; i++) col.add(0.49 + 0.02 * rng.nextDouble());
            data.add(col);
            dists.add(new UniformRealDistribution(0, 1));
        }

        MultiGeneralAndersonDarlingTest test = new MultiGeneralAndersonDarlingTest(data, dists);

        assertTrue("p must be finite", Double.isFinite(test.getP()));
        assertEquals(3.7e-24, test.getP(), 1e-30);
    }
}
