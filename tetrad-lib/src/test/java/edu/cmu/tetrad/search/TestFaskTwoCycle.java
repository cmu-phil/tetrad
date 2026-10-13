package edu.cmu.tetrad.search;

import edu.cmu.tetrad.util.StatUtils;
import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Deterministic tests of the static FASK two-cycle test on linear non-Gaussian (Exp(1)) data,
 * exercising the cyclic cases: an isolated two-cycle (no conditioning candidates), a mixed-sign
 * two-cycle (the conditional-correlation shifts oppose), and acyclic controls. Data are simulated
 * in closed form from the reduced form of the two-variable system, so no search machinery is
 * involved.
 */
public class TestFaskTwoCycle {

    private static final int N = 4000;
    private static final double CUTOFF = StatUtils.getZForAlpha(0.05);

    private static double exp1(Random rng) {
        return -Math.log(1.0 - rng.nextDouble()) - 1.0; // centered Exp(1)
    }

    /** Reduced form of x = b*y + e1, y = a*x + e2 (a stable two-cycle when |ab| < 1). */
    private static double[][] twoCycleData(double a, double b, long seed) {
        Random rng = new Random(seed);
        double[] x = new double[N], y = new double[N];
        double det = 1.0 - a * b;
        for (int r = 0; r < N; r++) {
            double e1 = exp1(rng), e2 = exp1(rng);
            x[r] = (e1 + b * e2) / det;
            y[r] = (a * e1 + e2) / det;
        }
        Fask.standardize(x);
        Fask.standardize(y);
        return new double[][]{x, y};
    }

    @Test
    public void isolatedTwoCycleIsDetectedWithNoCandidates() {
        double[][] d = twoCycleData(0.6, 0.4, 11L);
        assertTrue("An isolated two-cycle (no conditioning candidates) should be detected",
                Fask.twoCycleTest(d[0], d[1], new double[0][], CUTOFF));
    }

    @Test
    public void mixedSignTwoCycleIsDetected() {
        double[][] d = twoCycleData(0.6, -0.4, 13L);
        assertTrue("A mixed-sign two-cycle should be detected (its shifts oppose)",
                Fask.twoCycleTest(d[0], d[1], new double[0][], CUTOFF));
    }

    @Test
    public void acyclicPairIsNotCalledATwoCycle() {
        Random rng = new Random(17L);
        double[] x = new double[N], y = new double[N];
        for (int r = 0; r < N; r++) {
            x[r] = exp1(rng);
            y[r] = 0.8 * x[r] + exp1(rng);
        }
        Fask.standardize(x);
        Fask.standardize(y);
        assertFalse("A one-directional pair should not pass the two-cycle test",
                Fask.twoCycleTest(x, y, new double[0][], CUTOFF));
    }

    @Test
    public void commonCausePairIsNotCalledATwoCycleGivenTheCause() {
        Random rng = new Random(19L);
        double[] z = new double[N], x = new double[N], y = new double[N];
        for (int r = 0; r < N; r++) {
            z[r] = exp1(rng);
            x[r] = z[r] + exp1(rng);
            y[r] = z[r] + exp1(rng);
        }
        Fask.standardize(z);
        Fask.standardize(x);
        Fask.standardize(y);
        assertFalse("A confounded pair should not pass the two-cycle test given its common cause",
                Fask.twoCycleTest(x, y, new double[][]{z}, CUTOFF));
    }

    /**
     * As {@link #twoCycleData(double, double, long)}, but at a caller-chosen sample size.
     */
    private static double[][] twoCycleData(double a, double b, long seed, int n) {
        Random rng = new Random(seed);
        double[] x = new double[n], y = new double[n];
        double det = 1.0 - a * b;
        for (int r = 0; r < n; r++) {
            double e1 = exp1(rng), e2 = exp1(rng);
            x[r] = (e1 + b * e2) / det;
            y[r] = (a * e1 + e2) / det;
        }
        Fask.standardize(x);
        Fask.standardize(y);
        return new double[][]{x, y};
    }

    /**
     * The contract of {@link Fask.TwoCycleRule#SLOPE_SHIFT}. On a plain acyclic edge with
     * Gaussian noise and a large sample, the published correlation rule accepts a two-cycle -
     * its shift is a nonzero population quantity under acyclicity, so its false-positive rate
     * grows with n - while the slope rule, whose null acyclicity actually implies, rejects.
     */
    @Test
    public void slopeRuleRejectsAnAcyclicEdgeWhereTheCorrelationRuleAcceptsIt() {
        Random rng = new Random(23L);
        final int n = 20000;

        double[] x = new double[n], y = new double[n];
        for (int r = 0; r < n; r++) {
            x[r] = rng.nextGaussian();
            y[r] = 0.6 * x[r] + rng.nextGaussian();
        }
        Fask.standardize(x);
        Fask.standardize(y);

        assertTrue("The correlation rule is expected to false-positive here; if this starts "
                        + "failing, the correlation rule's calibration has changed",
                Fask.twoCycleTest(x, y, new double[0][], CUTOFF, Fask.TwoCycleRule.CORRELATION_SHIFT));
        assertFalse("The slope rule must not call an acyclic edge a two-cycle",
                Fask.twoCycleTest(x, y, new double[0][], CUTOFF, Fask.TwoCycleRule.SLOPE_SHIFT));
    }

    /**
     * The slope rule keeps power on genuine two-cycles with skewed noise, same-sign and
     * mixed-sign alike, at a sample size where it has power (see {@code FaskTwoCycleRuleHarness}
     * for the full size/power tables).
     */
    @Test
    public void slopeRuleStillDetectsTrueTwoCycles() {
        double[][] sameSign = twoCycleData(0.6, 0.4, 29L, 20000);
        assertTrue("A same-sign two-cycle should be detected by the slope rule",
                Fask.twoCycleTest(sameSign[0], sameSign[1], new double[0][], CUTOFF,
                        Fask.TwoCycleRule.SLOPE_SHIFT));

        double[][] mixedSign = twoCycleData(0.6, -0.4, 31L, 20000);
        assertTrue("A mixed-sign two-cycle should be detected by the slope rule",
                Fask.twoCycleTest(mixedSign[0], mixedSign[1], new double[0][], CUTOFF,
                        Fask.TwoCycleRule.SLOPE_SHIFT));
    }

    /** The unparameterized entry point must keep reproducing the published rule exactly. */
    @Test
    public void defaultRuleIsTheCorrelationRule() {
        double[][] d = twoCycleData(0.6, 0.4, 11L);
        assertEquals(Fask.twoCycleTest(d[0], d[1], new double[0][], CUTOFF),
                Fask.twoCycleTest(d[0], d[1], new double[0][], CUTOFF,
                        Fask.TwoCycleRule.CORRELATION_SHIFT));
    }
}
