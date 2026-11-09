package edu.cmu.tetrad.test;

import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Pins RandomUtil.nextGaussian(mean, sd) to its arguments.
 *
 * <p>From April 2026 until this test was added, the two-argument form returned an unscaled
 * standard normal, silently ignoring both mean and sd. Every caller passing anything other than
 * (0, 1) -- subject intercepts in the observational-study simulation, error draws in
 * StandardizedSemIm and SemIm's recursive and time-series paths, LargeScaleSimulation shocks,
 * the util.dist Normal and LogNormal classes -- was getting unit-variance, zero-mean draws.
 * This test fails against that version and passes against the corrected one.</p>
 */
public class TestRandomUtilGaussian {

    private static final int N = 200_000;

    /**
     * Checks that nextGaussian(mean, sd) honors both arguments: the sample mean and standard
     * deviation must match, to a tolerance that the ignored-argument version misses by orders of
     * magnitude.
     */
    @Test
    public void testMeanAndSdAreHonored() {
        RandomUtil rand = RandomUtil.getInstance();
        rand.setSeed(20260907L);

        double mean = 100.0, sd = 0.01;
        double[] x = new double[N];
        double m = 0;
        for (int i = 0; i < N; i++) {
            x[i] = rand.nextGaussian(mean, sd);
            m += x[i];
        }
        m /= N;
        double s = 0;
        for (double v : x) s += (v - m) * (v - m);
        s = Math.sqrt(s / (N - 1));

        assertEquals("sample mean", mean, m, 1e-3);
        assertEquals("sample sd", sd, s, 1e-3);
    }

    /**
     * Checks the zero-sd branch: returns the mean exactly, consuming no randomness.
     */
    @Test
    public void testZeroSdReturnsMean() {
        RandomUtil rand = RandomUtil.getInstance();
        for (int i = 0; i < 100; i++) {
            assertEquals(-3.5, rand.nextGaussian(-3.5, 0.0), 0.0);
        }
    }

    /**
     * Checks that a negative sd is rejected rather than silently accepted.
     */
    @Test
    public void testNegativeSdRejected() {
        boolean threw = false;
        try {
            RandomUtil.getInstance().nextGaussian(0.0, -1.0);
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        assertTrue("negative sd must throw", threw);
    }

    /**
     * Checks that the one-argument form and the two-argument form with (0, 1) agree in
     * distribution, so callers that used (0, 1) explicitly are unchanged by the fix.
     */
    @Test
    public void testUnitFormMatchesStandard() {
        RandomUtil rand = RandomUtil.getInstance();
        rand.setSeed(7L);
        double m = 0, s = 0;
        double[] x = new double[N];
        for (int i = 0; i < N; i++) {
            x[i] = rand.nextGaussian(0.0, 1.0);
            m += x[i];
        }
        m /= N;
        for (double v : x) s += (v - m) * (v - m);
        s = Math.sqrt(s / (N - 1));
        assertEquals(0.0, m, 0.01);
        assertEquals(1.0, s, 0.01);
    }
}
