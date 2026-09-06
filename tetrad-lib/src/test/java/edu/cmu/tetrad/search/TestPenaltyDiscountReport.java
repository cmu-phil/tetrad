package edu.cmu.tetrad.search;

import edu.cmu.tetrad.search.score.PenaltyDiscountCalibration;
import edu.cmu.tetrad.search.score.PenaltyDiscountReport;
import edu.cmu.tetrad.search.score.SemBicScore;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Pins the behavior of {@link PenaltyDiscountReport}, the presentation layer the penalty discount calculator
 * dialog is built on. The point of these tests is that the dialog and the automatic penalty applied inside the
 * score wrappers under {@code semBicAutoPenalty} cannot drift apart: the report must return exactly what
 * {@link PenaltyDiscountCalibration} returns for the same inputs.
 */
public class TestPenaltyDiscountReport {

    /**
     * All-ones block sizes must reproduce the SEM BIC convenience method exactly, since that method is defined as
     * the uniform-df-one case of the same calibration.
     */
    @Test
    public void testAgreesWithSemBicConvenienceMethod() {
        int p = 100, n = 1000;
        double degree = 5.0, fdr = 0.01;

        int[] ones = new int[p];
        Arrays.fill(ones, 1);

        PenaltyDiscountReport report = new PenaltyDiscountReport(ones, n, degree, fdr, 0.0, null);

        assertEquals(SemBicScore.penaltyDiscountForFalseDiscoveryRate(p, n, degree, fdr),
                report.getPenaltyDiscountFromFdr(), 1e-6);
    }

    /**
     * The calibration is an inversion, so evaluating the expected false-edge count at the returned discount must
     * return the budget it was calibrated to: fdr times p * degree / 2.
     */
    @Test
    public void testInversionRoundTrips() {
        int p = 50, n = 2000;
        double degree = 4.0, fdr = 0.02;

        int[] ones = new int[p];
        Arrays.fill(ones, 1);

        PenaltyDiscountReport report = new PenaltyDiscountReport(ones, n, degree, fdr, 0.0, null);
        double budget = fdr * p * degree / 2.0;

        assertEquals(budget, report.getExpectedTrueEdges() * fdr, 1e-9);
        assertEquals(budget, report.expectedFalseEdgesAt(report.getPenaltyDiscount()), 1e-4);
    }

    /**
     * The central fact the dialog exists to make visible: at the same p, N, degree and target FDR, a score whose
     * pairs cost more degrees of freedom needs a SMALLER penalty discount, because a chi-square concentrates as df
     * grows and the threshold sits at a fixed multiple of the mean.
     */
    @Test
    public void testHigherDfCalibratesToSmallerDiscount() {
        int p = 30, n = 1000;
        double degree = 4.0, fdr = 0.01;

        int[] ones = new int[p];
        Arrays.fill(ones, 1);

        int[] threes = new int[p];
        Arrays.fill(threes, 3);

        double cSem = new PenaltyDiscountReport(ones, n, degree, fdr, 0.0, null).getPenaltyDiscount();
        double cBf = new PenaltyDiscountReport(threes, n, degree, fdr, 0.0, null).getPenaltyDiscount();

        assertTrue("df 9 should calibrate below df 1: " + cBf + " vs " + cSem, cBf < cSem);
    }

    /**
     * The recommended discount is the larger of the two criteria, and the effect-size criterion is the one that
     * binds at large N, where the false-discovery criterion has shrunk like 1 / ln N.
     */
    @Test
    public void testEffectCriterionBindsAtLargeN() {
        int p = 20, n = 50000;

        int[] ones = new int[p];
        Arrays.fill(ones, 1);

        PenaltyDiscountReport report = new PenaltyDiscountReport(ones, n, 4.0, 0.01, 0.10, null);

        assertTrue(report.getPenaltyDiscountFromMinEffect() > report.getPenaltyDiscountFromFdr());
        assertEquals(report.getPenaltyDiscountFromMinEffect(), report.getPenaltyDiscount(), 1e-12);

        // At the chosen discount the rule accepts partial correlations down to about the requested floor.
        assertEquals(0.10, SemBicScore.minDetectablePartialCorrelation(report.getPenaltyDiscount(), n), 1e-3);
    }

    /**
     * A zero minimum effect disables the second criterion rather than forcing the discount to zero.
     */
    @Test
    public void testZeroMinEffectDisablesEffectCriterion() {
        int[] ones = new int[20];
        Arrays.fill(ones, 1);

        PenaltyDiscountReport report = new PenaltyDiscountReport(ones, 1000, 4.0, 0.01, 0.0, null);

        assertEquals(0.0, report.getPenaltyDiscountFromMinEffect(), 0.0);
        assertEquals(report.getPenaltyDiscountFromFdr(), report.getPenaltyDiscount(), 1e-12);
    }

    /**
     * The df histogram over a mixed set of block sizes must sum to p(p-1)/2, and the per-class levels must be
     * ordered: a class with more degrees of freedom runs at a strictly smaller level at any fixed discount. This
     * is the ordering the Result tab's table displays.
     */
    @Test
    public void testMixedHistogramAndPerClassLevels() {
        int[] sizes = {1, 1, 1, 2, 3};  // 5 variables: three continuous, one binary-ish, one three-level
        PenaltyDiscountReport report = new PenaltyDiscountReport(sizes, 1000, 3.0, 0.05, 0.0, null);

        long total = report.getDfPairCounts().values().stream().mapToLong(Long::longValue).sum();
        assertEquals(5 * 4 / 2, total);

        double c = 2.0;
        double previous = Double.MAX_VALUE;

        for (int df : report.getDfPairCounts().keySet()) {
            double alpha = report.alphaAt(c, df);
            assertTrue("levels must decrease with df", alpha < previous);
            previous = alpha;
        }

        assertFalse(report.isFitted());
    }

    /**
     * The sweep is ordered by discount and monotone in what matters: expected false edges fall, the smallest
     * detectable partial correlation rises.
     */
    @Test
    public void testSweepIsMonotone() {
        int[] ones = new int[40];
        Arrays.fill(ones, 1);

        PenaltyDiscountReport report = new PenaltyDiscountReport(ones, 1000, 4.0, 0.01, 0.0, null);
        List<PenaltyDiscountReport.Row> rows = report.sweep(0.5, 4.0, 0.25);

        assertEquals(15, rows.size());

        for (int i = 1; i < rows.size(); i++) {
            assertTrue(rows.get(i).penaltyDiscount() > rows.get(i - 1).penaltyDiscount());
            assertTrue(rows.get(i).expectedFalseEdges() < rows.get(i - 1).expectedFalseEdges());
            assertTrue(rows.get(i).minDetectableR() > rows.get(i - 1).minDetectableR());
        }
    }

    /**
     * A fitted null with an inflated variance must calibrate to a LARGER discount than the exact chi-square null,
     * since the inflated tail puts more null pairs over any given threshold. This is the correction the
     * permutation option in the dialog exists to apply, and getting its sign wrong would silently under-penalize
     * Basis Function scores on the min-max embedding.
     */
    @Test
    public void testFittedNullWithInflatedVarianceRaisesDiscount() {
        int p = 30, n = 1000;
        int[] threes = new int[p];
        Arrays.fill(threes, 3);

        PenaltyDiscountReport exact = new PenaltyDiscountReport(threes, n, 4.0, 0.01, 0.0, null);

        // Same mean (9) but twice the variance: kappa = var / (2 mean) = 2, nu = 2 mean^2 / var = 4.5.
        java.util.Map<Integer, PenaltyDiscountCalibration.NullFit> fits = new java.util.TreeMap<>();
        fits.put(9, new PenaltyDiscountCalibration.NullFit((long) p * (p - 1) / 2, 2.0, 4.5));

        PenaltyDiscountReport fitted = new PenaltyDiscountReport(threes, n, 4.0, 0.01, 0.0, fits);

        assertTrue(fitted.isFitted());
        assertTrue("an inflated null tail must raise the calibrated discount",
                fitted.getPenaltyDiscount() > exact.getPenaltyDiscount());
    }

    /**
     * Bad inputs are rejected rather than silently producing a number, since the dialog passes user-typed values
     * straight through.
     */
    @Test
    public void testInputValidation() {
        int[] ones = new int[10];
        Arrays.fill(ones, 1);

        assertThrows(IllegalArgumentException.class,
                () -> new PenaltyDiscountReport(new int[]{1}, 1000, 4.0, 0.01, 0.0, null));
        assertThrows(IllegalArgumentException.class,
                () -> new PenaltyDiscountReport(ones, 1, 4.0, 0.01, 0.0, null));
        assertThrows(IllegalArgumentException.class,
                () -> new PenaltyDiscountReport(ones, 1000, 0.0, 0.01, 0.0, null));
        assertThrows(IllegalArgumentException.class,
                () -> new PenaltyDiscountReport(ones, 1000, 4.0, 1.0, 0.0, null));
        assertThrows(IllegalArgumentException.class,
                () -> new PenaltyDiscountReport(ones, 1000, 4.0, 0.01, 1.0, null));
    }

    /**
     * The report text mentions both criteria and the chosen value, which is what gets copied out of the dialog.
     */
    @Test
    public void testReportText() {
        int[] ones = new int[25];
        Arrays.fill(ones, 1);

        String text = new PenaltyDiscountReport(ones, 1000, 4.0, 0.01, 0.05, null).report();

        assertTrue(text.contains("False-discovery criterion"));
        assertTrue(text.contains("Effect-size criterion"));
        assertTrue(text.contains("Recommended"));
        assertTrue(text.contains("degrees of freedom"));
    }
}
