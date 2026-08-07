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

import edu.cmu.tetrad.data.GeneralAndersonDarlingTest;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.search.ConditioningSetType;
import edu.cmu.tetrad.search.MarkovCheck;
import edu.cmu.tetrad.search.VertexRepairSearch;
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.util.UniformityTest;
import org.apache.commons.math3.distribution.UniformRealDistribution;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Calibration tests for the uniformity statistics used by the Markov check.
 * <p>
 * The Markov check tests whether the p-values of a graph's implied conditional independencies are distributed
 * Uniform(0, 1). That null is fully specified: no parameters are estimated from the p-values. The Anderson-Darling
 * A-squared-star statistic, by contrast, carries Stephens' case-3 correction for a mean and variance estimated from
 * the sample, inflating A-squared by (1 + 0.75/n + 2.25/n^2). Evaluating that inflated statistic against
 * {@code getProbTail}, which is the asymptotic tail for the uninflated statistic, made the reported p-values
 * systematically too small - rejecting a genuinely Markov graph about 9% of the time at n = 5 against a nominal 5%.
 * Because a Markov check often has only a handful of implied independencies to test, that small-n regime is the
 * common case rather than a corner case.
 * <p>
 * These tests are simulation based and therefore approximate; the tolerances are wide enough to be stable across
 * seeds while still failing decisively if the case-3 statistic is reintroduced.
 *
 * @author josephramsey
 */
public class TestAndersonDarlingCalibration {

    /**
     * The number of simulated draws per sample size.
     */
    private static final int REPS = 20000;

    /**
     * The nominal test level.
     */
    private static final double ALPHA = 0.05;

    /**
     * Returns the empirical rejection rate at ALPHA of the Anderson-Darling uniformity test applied to iid
     * Uniform(0, 1) samples of the given size, using the uninflated statistic if useStar is false.
     */
    private static double rejectionRate(int n, boolean useStar, long seed) {
        Random rng = new Random(seed);
        int rejections = 0;

        for (int r = 0; r < REPS; r++) {
            List<Double> pValues = new ArrayList<>();
            for (int i = 0; i < n; i++) pValues.add(rng.nextDouble());

            GeneralAndersonDarlingTest test =
                    new GeneralAndersonDarlingTest(pValues, new UniformRealDistribution(0, 1));

            double statistic = useStar ? test.getASquaredStar() : test.getASquared();
            double p = 1.0 - test.getProbTail(pValues.size(), statistic);

            if (p < ALPHA) rejections++;
        }

        return rejections / (double) REPS;
    }

    /**
     * With the uninflated statistic, the test is calibrated even for very small numbers of implied independencies.
     */
    @Test
    public void testAndersonDarlingCalibratedAtSmallN() {
        for (int n : new int[]{5, 6, 10, 20, 50}) {
            double rate = rejectionRate(n, false, 1234L);
            assertEquals("Anderson-Darling rejection rate at n = " + n
                         + " should be near the nominal level; was " + rate, ALPHA, rate, 0.015);
        }
    }

    /**
     * Pins the defect being fixed: the case-3 statistic over-rejects badly at small n. If this ever stops holding,
     * the underlying implementation has changed and the fix in MarkovCheck should be revisited.
     */
    @Test
    public void testAsquaredStarOverRejectsAtSmallN() {
        double rate = rejectionRate(5, true, 1234L);
        assertTrue("The case-3 statistic is expected to over-reject at n = 5 (was " + rate + ")",
                rate > 0.075);
    }

    /**
     * The Kolmogorov-Smirnov statistic reported alongside Anderson-Darling is calibrated, which is why it appeared
     * to disagree with Anderson-Darling on graphs that were in fact Markov.
     */
    @Test
    public void testKolmogorovSmirnovCalibratedAtSmallN() {
        for (int n : new int[]{5, 6, 20}) {
            Random rng = new Random(1234L);
            int rejections = 0;

            for (int r = 0; r < REPS; r++) {
                List<Double> pValues = new ArrayList<>();
                for (int i = 0; i < n; i++) pValues.add(rng.nextDouble());
                if (UniformityTest.getKsPValue(pValues, 0, 1) < ALPHA) rejections++;
            }

            double rate = rejections / (double) REPS;
            assertEquals("Kolmogorov-Smirnov rejection rate at n = " + n + " was " + rate,
                    ALPHA, rate, 0.015);
        }
    }

    /**
     * The other uniformity entry points must be calibrated too. {@code VertexRepairSearch.getAndersonDarlingP} and
     * {@code MarkovCheck.checkAgainstAndersonDarlingTest} previously returned
     * {@code GeneralAndersonDarlingTest.getP()}, which is Stephens' case-3 approximation for testing normality with
     * an estimated mean and variance; against a fully specified Uniform(0, 1) it rejected 52% to 63% of the time at
     * a nominal 5%, and unlike a small-sample correction the error did not decay with n.
     */
    @Test
    public void testOtherEntryPointsCalibrated() {
        for (int n : new int[]{5, 20, 200}) {
            Random rng = new Random(1234L);
            int rejRepair = 0, rejCheck = 0;

            for (int r = 0; r < REPS; r++) {
                List<Double> pValues = new ArrayList<>();
                for (int i = 0; i < n; i++) pValues.add(rng.nextDouble());

                if (VertexRepairSearch.getAndersonDarlingP(pValues) < ALPHA) rejRepair++;
                if (new MarkovCheck(new EdgeListGraph(), new MsepTest(new EdgeListGraph()),
                        ConditioningSetType.ORDERED_LOCAL_MARKOV_PROPERTY)
                        .checkAgainstAndersonDarlingTest(pValues) < ALPHA) rejCheck++;
            }

            assertEquals("VertexRepairSearch.getAndersonDarlingP rejection rate at n = " + n
                         + " was " + rejRepair / (double) REPS, ALPHA, rejRepair / (double) REPS, 0.015);
            assertEquals("MarkovCheck.checkAgainstAndersonDarlingTest rejection rate at n = " + n
                         + " was " + rejCheck / (double) REPS, ALPHA, rejCheck / (double) REPS, 0.015);
        }
    }

    /**
     * The distortion depended on the number of p-values, so it altered the ORDER in which VertexRepairSearch ranked
     * candidate edits with differing numbers of implied independencies, not merely their scale. This pins the
     * corrected ordering: with the same degree of non-uniformity, a candidate offering more implied independencies
     * must score no higher than one offering fewer.
     */
    @Test
    public void testUniformityPOrderingAcrossTestCounts() {
        double previous = Double.MAX_VALUE;

        for (int n : new int[]{4, 6, 8, 12, 20}) {
            List<Double> pValues = new ArrayList<>();
            for (int i = 0; i < n; i++) pValues.add((i + 0.5) / n * 0.6);

            double p = VertexRepairSearch.getAndersonDarlingP(pValues);
            assertTrue("More evidence of the same non-uniformity must not raise the uniformity p-value"
                       + " (n = " + n + ", p = " + p + ")", p <= previous);
            previous = p;
        }

        assertTrue("At n = 20 the same non-uniformity should be clearly detected", previous < 0.05);
    }

    /**
     * With genuinely non-uniform p-values - the case a Markov check is meant to catch - the corrected
     * Anderson-Darling test still has power, so the fix has not simply made the test permissive.
     */
    @Test
    public void testRetainsPowerAgainstNonUniform() {
        Random rng = new Random(1234L);
        int rejections = 0;
        int reps = 2000;

        for (int r = 0; r < reps; r++) {
            List<Double> pValues = new ArrayList<>();
            // p-values crowded toward zero, as when implied independencies fail.
            for (int i = 0; i < 20; i++) pValues.add(rng.nextDouble() * 0.10);

            GeneralAndersonDarlingTest test =
                    new GeneralAndersonDarlingTest(pValues, new UniformRealDistribution(0, 1));
            double p = 1.0 - test.getProbTail(pValues.size(), test.getASquared());

            if (p < ALPHA) rejections++;
        }

        double rate = rejections / (double) reps;
        assertTrue("Corrected Anderson-Darling should reject clearly non-uniform p-values; rate was " + rate,
                rate > 0.95);
    }
}
