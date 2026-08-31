package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.TRffBicScore;
import edu.cmu.tetrad.search.test.TRffLrTest;
import org.junit.Test;

import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.assertTrue;

/**
 * Regression pins for {@link TRffLrTest} (the renamed, nested successor of
 * MinimaxTRffTest).
 *
 * <p><b>Behavior pinned.</b></p>
 * <ol>
 *   <li><b>Nesting.</b> {@link TRffBicScore#nestedLocalFits(int, int[], int, int[])} fits
 *   reduced and full models on a shared design in which the reduced columns are a prefix of
 *   the full columns, so the LR statistic {@code 2 (llFull - llRed)} is nonnegative up to
 *   profiling and convergence slack. (The predecessor compared fits over different random
 *   Fourier bases, which were not nested, so the statistic had no sign guarantee.)</li>
 *   <li><b>Level at multivariate Z.</b> For a continuous child the Student-t scale is
 *   profiled per model, and referring {@code D = n log(sigmaRed^2/sigmaFull^2)} to
 *   {@code ChiSq(ddf)} inflates badly once {@code ddf} is a nontrivial fraction of n:
 *   under a plain confounded null X &lt;- (Z0,Z1,Z2) -&gt; Y, the chi-square-referenced
 *   predecessor rejected in over half of replications. The test now uses the classical
 *   F-form {@code F = (exp(D/n) - 1)(n - edfFull)/ddf ~ F(ddf, n - edfFull)} for
 *   continuous children, together with explicit linear baseline columns in both designs,
 *   which restores approximate level control.</li>
 *   <li><b>Power.</b> The alternative check guards against trivially conservative "fixes."</li>
 * </ol>
 *
 * <p>This class does not compile against builds preceding the rename; the behavioral pins
 * above document the calibration properties the renamed implementation must keep.</p>
 */
public class TestTRffLrTest {

    /**
     * Constructs the test class.
     */
    public TestTRffLrTest() {
    }

    /**
     * Nesting pin: the LR statistic from nestedLocalFits is nonnegative (up to slack) on
     * null draws, where it fluctuates near zero and any sign violation would show.
     */
    @Test
    public void testNestedFitsGiveNonnegativeLr() {
        Random rng = new Random(90210L);
        int n = 600;

        for (int r = 0; r < 8; r++) {
            DataSet d = confoundedZ3(n, false, rng);
            TRffBicScore score = new TRffBicScore(d);
            score.setRffFeatures(64);

            int yi = 1;
            int[] zi = {2, 3, 4};
            int[] union = {0, 2, 3, 4};
            int[] rows = score.validRowsForUnion(yi, union);

            TRffBicScore.NestedFits fits = score.nestedLocalFits(yi, zi, 0, rows);
            double lr = 2.0 * (fits.full().logLik() - fits.reduced().logLik());

            assertTrue("Nested LR statistic should be finite; got " + lr, Double.isFinite(lr));
            assertTrue("Nested LR statistic should be nonnegative up to slack; got " + lr,
                    lr >= -0.5);
        }
    }

    /**
     * Calibration pin: under the confounded null X &lt;- (Z0,Z1,Z2) -&gt; Y the test retains
     * the null in a clear majority of replications, and under the alternative (X -&gt; Y | Z
     * added) it rejects in a clear majority.
     */
    @Test
    public void testLevelAndPowerAtMultivariateZ() {
        Random rng = new Random(772200L);
        int n = 600;
        int reps = 8;
        double alpha = 0.05;

        int nullRetained = 0;
        int altRejected = 0;

        for (int r = 0; r < reps; r++) {
            DataSet d0 = confoundedZ3(n, false, rng);
            DataSet d1 = confoundedZ3(n, true, rng);

            if (pValue(d0, alpha) > alpha) nullRetained++;
            if (pValue(d1, alpha) <= alpha) altRejected++;
        }

        // Chi-square-referenced predecessor: nullRetained ~ 3-4 of 8 in this regime.
        // F-referenced nested test: ~ 7-8 of 8.
        assertTrue("Confounded |Z|=3 null retained in only " + nullRetained + "/" + reps
                        + " replications; profiled-scale LR must use the F reference "
                        + "for continuous children.",
                nullRetained >= 6);

        assertTrue("Alternative rejected in only " + altRejected + "/" + reps
                        + " replications; calibration fixes should not destroy power.",
                altRejected >= 6);
    }

    /**
     * X = s + e1, Y = s + (dep ? 0.35 X : 0) + e2, with s = 0.6 (Z0 + Z1 + Z2) and
     * standard Gaussian noise. Under dep == false, X is independent of Y given Z.
     */
    private static DataSet confoundedZ3(int n, boolean dep, Random rng) {
        List<Node> vars = List.of(new ContinuousVariable("X"), new ContinuousVariable("Y"),
                new ContinuousVariable("Z0"), new ContinuousVariable("Z1"),
                new ContinuousVariable("Z2"));
        DataSet d = new BoxDataSet(new DoubleDataBox(n, 5), vars);
        for (int i = 0; i < n; i++) {
            double z0 = rng.nextGaussian(), z1 = rng.nextGaussian(), z2 = rng.nextGaussian();
            double s = 0.6 * (z0 + z1 + z2);
            double x = s + rng.nextGaussian();
            double y = s + (dep ? 0.35 * x : 0.0) + rng.nextGaussian();
            d.setDouble(i, 0, x);
            d.setDouble(i, 1, y);
            d.setDouble(i, 2, z0);
            d.setDouble(i, 3, z1);
            d.setDouble(i, 4, z2);
        }
        return d;
    }

    private static double pValue(DataSet d, double alpha) {
        TRffBicScore score = new TRffBicScore(d);
        score.setRffFeatures(64);
        TRffLrTest test = new TRffLrTest(score);
        test.setAlpha(alpha);
        return test.checkIndependence(d.getVariable("X"), d.getVariable("Y"),
                Set.of(d.getVariable("Z0"), d.getVariable("Z1"), d.getVariable("Z2"))).getPValue();
    }
}
