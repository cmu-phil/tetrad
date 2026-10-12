package edu.cmu.tetrad.search;

import edu.cmu.tetrad.util.StatUtils;
import org.junit.Test;

import java.util.Random;

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
}
