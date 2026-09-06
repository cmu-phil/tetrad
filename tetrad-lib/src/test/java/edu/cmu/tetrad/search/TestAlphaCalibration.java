package edu.cmu.tetrad.search;

import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.test.AlphaCalibration;
import edu.cmu.tetrad.search.test.AlphaReport;
import org.apache.commons.math3.special.Gamma;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.Assert.*;

/**
 * Pins the behavior of {@link AlphaCalibration} and {@link AlphaReport}, the back-of-the-envelope significance
 * level calibration the Alpha Calculator dialog is built on. The relationships tested here are the ones the
 * dialog's claims rest on: that the false-edge level is a division independent of N and of the test; that the
 * noncentral chi-square is right (against Monte Carlo and against its central special case); that at df = 1 the
 * power side reproduces the Fisher z calculation; that higher-df pairs detect only larger effects at the same
 * level, which is the test-side face of the Basis Function score's conservatism; that power and precision trade
 * against each other rather than combining; and that the bridge to the penalty discount is an exact round trip.
 */
public class TestAlphaCalibration {

    /**
     * The false-edge level is budget over null pairs, and for a sparse graph it is close to the pocket formula
     * fdr * degree / (p - 1).
     */
    @Test
    public void testFalseEdgeLevelIsADivision() {
        int p = 100;
        double degree = 5.0, fdr = 0.01;

        double alpha = AlphaCalibration.alphaForFalseDiscoveryRate(p, degree, fdr);
        double nullPairs = AlphaCalibration.numNullPairs(p, degree);

        assertEquals(fdr * p * degree / 2.0, nullPairs * alpha, 1e-12);
        assertEquals(fdr * degree / (p - 1), alpha, 0.1 * alpha);
    }

    /**
     * The false-edge criterion does not involve the sample size or the test's df. N and df enter only through
     * power. This is the cleanest structural difference from the penalty discount, whose calibration divides by
     * ln N and depends on the df histogram.
     */
    @Test
    public void testFalseEdgeLevelIndependentOfNAndDf() {
        int[] ones = new int[50];
        Arrays.fill(ones, 1);
        int[] threes = new int[50];
        Arrays.fill(threes, 3);

        AlphaReport small = new AlphaReport(ones, 200, 4.0, 0.01, 2, 0.80, 0.0, 3);
        AlphaReport large = new AlphaReport(ones, 20000, 4.0, 0.01, 2, 0.80, 0.0, 3);
        AlphaReport bf = new AlphaReport(threes, 200, 4.0, 0.01, 2, 0.80, 0.0, 3);

        assertEquals(small.getAlphaFromFdr(), large.getAlphaFromFdr(), 1e-15);
        assertEquals(small.getAlphaFromFdr(), bf.getAlphaFromFdr(), 1e-15);
    }

    /**
     * The expected-false-edges calculation inverts the level calculation.
     */
    @Test
    public void testInverseRoundTrips() {
        int p = 40;
        double degree = 4.0, fdr = 0.02;
        double alpha = AlphaCalibration.alphaForFalseDiscoveryRate(p, degree, fdr);
        assertEquals(fdr * p * degree / 2.0, AlphaCalibration.expectedFalseEdges(p, degree, alpha), 1e-10);
    }

    /**
     * At zero noncentrality the noncentral tail is the central one, and the quantile inverts it.
     */
    @Test
    public void testNoncentralReducesToCentral() {
        for (int df : new int[]{1, 3, 9}) {
            for (double x : new double[]{0.5, 3.84, 12.0}) {
                assertEquals(Gamma.regularizedGammaQ(df / 2.0, x / 2.0),
                        AlphaCalibration.noncentralChiSquareUpperTail(df, 0.0, x), 1e-12);
            }
            double q = AlphaCalibration.chiSquareUpperQuantile(df, 0.01);
            assertEquals(0.01, Gamma.regularizedGammaQ(df / 2.0, q / 2.0), 1e-9);
        }

        assertEquals(3.8415, AlphaCalibration.chiSquareUpperQuantile(1, 0.05), 1e-3);
        assertEquals(27.877, AlphaCalibration.chiSquareUpperQuantile(9, 0.001), 1e-2);
    }

    /**
     * The noncentral chi-square upper tail against Monte Carlo, including a large-noncentrality case where a
     * naive series from j = 0 would underflow.
     */
    @Test
    public void testNoncentralChiSquareAgainstMonteCarlo() {
        Random rnd = new Random(1);
        int[][] cases = {{1, 5}, {3, 10}, {9, 20}, {9, 300}};
        double[] xs = {3.84, 12.0, 30.0, 400.0};
        int m = 200000;

        for (int[] c : cases) {
            int df = c[0];
            double lambda = c[1];
            for (double x : xs) {
                int hits = 0;
                for (int i = 0; i < m; i++) {
                    double s = 0;
                    for (int k = 0; k < df; k++) {
                        double z = rnd.nextGaussian() + (k == 0 ? Math.sqrt(lambda) : 0.0);
                        s += z * z;
                    }
                    if (s > x) hits++;
                }
                double mc = hits / (double) m;
                double analytic = AlphaCalibration.noncentralChiSquareUpperTail(df, lambda, x);
                double se = Math.sqrt(Math.max(mc * (1 - mc), 1e-6) / m);
                assertEquals("df=" + df + " lambda=" + lambda + " x=" + x, mc, analytic, 5 * se + 1e-4);
            }
        }
    }

    /**
     * At df = 1 the chi-square power reproduces the Fisher z power to within the atanh-vs-log approximation, so
     * the generalization contains the Fisher z calculation as its special case rather than replacing it.
     */
    @Test
    public void testDfOneMatchesFisherZ() {
        int n = 1000, s = 2;
        double r = 0.12, alpha = 0.01;

        double lambda = AlphaCalibration.noncentrality(r, n, s);
        double powerChi = AlphaCalibration.power(alpha, 1, lambda);

        org.apache.commons.math3.distribution.NormalDistribution normal =
                new org.apache.commons.math3.distribution.NormalDistribution(0, 1);
        double z = Math.sqrt(n - s - 3) * 0.5 * Math.log((1 + r) / (1 - r));
        double zAlpha = normal.inverseCumulativeProbability(1 - alpha / 2);
        double powerZ = 1 - normal.cumulativeProbability(zAlpha - z) + normal.cumulativeProbability(-zAlpha - z);

        assertEquals(powerZ, powerChi, 2e-3);
    }

    /**
     * Power and detectable effect invert each other in both directions.
     */
    @Test
    public void testPowerCriterionRoundTrips() {
        int n = 1000, s = 2;
        double power = 0.80, r = 0.12;

        for (int df : new int[]{1, 4, 9}) {
            double alpha = AlphaCalibration.alphaForMinPartialCorrelation(r, df, n, s, power);
            assertTrue(alpha > 0 && alpha < 1);
            assertEquals(r, AlphaCalibration.minDetectablePartialCorrelation(alpha, df, n, s, power), 1e-5);
            assertEquals(power, AlphaCalibration.power(alpha, df, AlphaCalibration.noncentrality(r, n, s)), 1e-6);
        }
    }

    /**
     * The test-side face of the Basis Function score's conservatism: at the same level, N and effect, a
     * higher-df pair has less power, so it detects only larger effects and needs a larger level to detect the
     * same one. The effect lands in one component; the null is charged for all of them.
     */
    @Test
    public void testHigherDfDetectsOnlyLargerEffects() {
        int n = 1000, s = 2;
        double alpha = 0.01, power = 0.80, r = 0.12;
        double lambda = AlphaCalibration.noncentrality(r, n, s);

        double previousR = 0.0;
        double previousAlpha = 0.0;
        double previousPower = 1.0;

        for (int df : new int[]{1, 2, 4, 9, 16}) {
            double detectable = AlphaCalibration.minDetectablePartialCorrelation(alpha, df, n, s, power);
            double needed = AlphaCalibration.alphaForMinPartialCorrelation(r, df, n, s, power);
            double pw = AlphaCalibration.power(alpha, df, lambda);

            assertTrue("detectable r must rise with df", detectable > previousR);
            assertTrue("needed alpha must rise with df", needed > previousAlpha);
            assertTrue("power must fall with df", pw < previousPower);

            previousR = detectable;
            previousAlpha = needed;
            previousPower = pw;
        }
    }

    /**
     * A smaller level detects only larger effects: the direction of the trade the dialog reports.
     */
    @Test
    public void testSmallerLevelDetectsOnlyLargerEffects() {
        int n = 1000, s = 2;
        double rLoose = AlphaCalibration.minDetectablePartialCorrelation(0.05, 1, n, s, 0.8);
        double rStrict = AlphaCalibration.minDetectablePartialCorrelation(1e-4, 1, n, s, 0.8);
        assertTrue(rStrict > rLoose);
    }

    /**
     * The two criteria conflict when the effect asked for needs a larger level than the budget allows, and the
     * report says so; on mixed-df data the binding class is the largest df, and the per-class map shows it.
     */
    @Test
    public void testCriteriaCanConflictAndBindingClassIsLargestDf() {
        int[] ones200 = new int[200];
        Arrays.fill(ones200, 1);
        AlphaReport conflicted = new AlphaReport(ones200, 200, 4.0, 0.01, 2, 0.80, 0.15, 3);
        assertFalse(conflicted.isCompatible());
        assertTrue(conflicted.getAlphaFromMinEffect() > conflicted.getAlphaFromFdr());
        assertTrue(conflicted.report().contains("CONFLICT"));

        int[] ones10 = new int[10];
        Arrays.fill(ones10, 1);
        AlphaReport comfortable = new AlphaReport(ones10, 20000, 4.0, 0.01, 2, 0.80, 0.15, 3);
        assertTrue(comfortable.isCompatible());
        assertTrue(comfortable.report().contains("compatible"));

        int[] mixed = {1, 1, 1, 2, 3, 3};
        AlphaReport report = new AlphaReport(mixed, 1000, 3.0, 0.05, 2, 0.80, 0.10, 3);
        Map<Integer, Double> needed = report.getAlphaNeededByDf();
        int largestDf = report.getDfPairCounts().keySet().stream().max(Integer::compare).orElseThrow();
        assertEquals(needed.get(largestDf), report.getAlphaFromMinEffect(), 0.0);
        double previous = 0.0;
        for (double a : needed.values()) {
            assertTrue(a > previous);
            previous = a;
        }
    }

    /**
     * A zero minimum effect disables the second criterion, and a report with it disabled is always compatible.
     */
    @Test
    public void testZeroMinEffectDisablesEffectCriterion() {
        int[] ones = new int[50];
        Arrays.fill(ones, 1);
        AlphaReport report = new AlphaReport(ones, 1000, 4.0, 0.01, 2, 0.80, 0.0, 3);
        assertEquals(0.0, report.getAlphaFromMinEffect(), 0.0);
        assertTrue(report.getAlphaNeededByDf().isEmpty());
        assertTrue(report.isCompatible());
    }

    /**
     * The multiplicity count is the number of subsets of size at most depth, and it is monotone in depth.
     */
    @Test
    public void testTestsPerPair() {
        assertEquals(11.0, AlphaCalibration.testsPerPair(4.0, 2), 1e-9);   // 1 + 4 + 6
        assertEquals(1.0, AlphaCalibration.testsPerPair(4.0, 0), 1e-9);
        assertTrue(AlphaCalibration.testsPerPair(6.0, 3) > AlphaCalibration.testsPerPair(6.0, 2));
        assertEquals(16.0, AlphaCalibration.testsPerPair(4.0, -1), 1e-9);  // 2^4
    }

    /**
     * The true-edge loss bound rises with the number of tests, which is the point it exists to make.
     */
    @Test
    public void testTrueEdgeLossBoundRisesWithMultiplicity() {
        assertEquals(0.20, AlphaCalibration.trueEdgeLossBound(0.20, 1), 1e-9);
        assertTrue(AlphaCalibration.trueEdgeLossBound(0.20, 11) > 0.9);
    }

    /**
     * The bridge to the penalty discount is an exact round trip in both directions, so the two calculators cannot
     * disagree about what a level and a discount have in common.
     */
    @Test
    public void testPenaltyDiscountBridgeRoundTrips() {
        int n = 506;
        double alpha = 1e-3;
        double c = AlphaCalibration.equivalentPenaltyDiscount(alpha, n);
        assertEquals(alpha, AlphaCalibration.equivalentAlpha(c, n), 1e-9);
        assertEquals(c, SemBicScore.penaltyDiscountForAlpha(alpha, n), 1e-9);
    }

    /**
     * The sweep is ordered by level and monotone in both consequences, in both df columns.
     */
    @Test
    public void testSweepIsMonotone() {
        int[] mixed = {1, 1, 1, 1, 2, 3, 3};
        AlphaReport report = new AlphaReport(mixed, 1000, 4.0, 0.01, 2, 0.80, 0.0, 3);
        List<AlphaReport.Row> rows = report.sweep();

        assertTrue(rows.size() >= 12);

        for (int i = 1; i < rows.size(); i++) {
            assertTrue(rows.get(i).alpha() < rows.get(i - 1).alpha());
            assertTrue(rows.get(i).expectedFalseEdges() < rows.get(i - 1).expectedFalseEdges());
            assertTrue(rows.get(i).minDetectableRMinDf() > rows.get(i - 1).minDetectableRMinDf());
            assertTrue(rows.get(i).minDetectableRMaxDf() > rows.get(i - 1).minDetectableRMaxDf());
            assertTrue(rows.get(i).equivalentPenalty() > rows.get(i - 1).equivalentPenalty());
            assertTrue(rows.get(i).minDetectableRMaxDf() >= rows.get(i).minDetectableRMinDf());
        }
    }

    /**
     * Bad inputs are rejected rather than silently producing a number, since the dialog passes user-typed values
     * straight through.
     */
    @Test
    public void testInputValidation() {
        int[] ones = new int[10];
        Arrays.fill(ones, 1);

        assertThrows(IllegalArgumentException.class, () -> AlphaCalibration.numNullPairs(1, 4.0));
        assertThrows(IllegalArgumentException.class, () -> AlphaCalibration.numNullPairs(10, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> AlphaCalibration.alphaForFalseDiscoveryRate(10, 4.0, 1.0));
        assertThrows(IllegalArgumentException.class,
                () -> AlphaCalibration.minDetectablePartialCorrelation(0.0, 1, 100, 2, 0.8));
        assertThrows(IllegalArgumentException.class,
                () -> AlphaCalibration.minDetectablePartialCorrelation(0.05, 1, 100, 2, 1.0));
        assertThrows(IllegalArgumentException.class,
                () -> AlphaCalibration.minDetectablePartialCorrelation(0.05, 1, 10, 20, 0.8));
        assertThrows(IllegalArgumentException.class,
                () -> AlphaCalibration.noncentralChiSquareUpperTail(0, 1.0, 1.0));
        assertThrows(IllegalArgumentException.class,
                () -> new AlphaReport(new int[]{1}, 1000, 4.0, 0.01, 2, 0.8, 0.0, 3));
        assertThrows(IllegalArgumentException.class, () -> new AlphaReport(ones, 3, 4.0, 0.01, 2, 0.8, 0.0, 3));
        assertThrows(IllegalArgumentException.class, () -> new AlphaReport(ones, 1000, 4.0, 0.01, 2, 0.8, 1.0, 3));
        assertThrows(IllegalArgumentException.class, () -> new AlphaReport(ones, 1000, 4.0, 0.01, 2, 1.0, 0.0, 3));
    }
}
