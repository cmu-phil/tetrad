package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.test.MinimaxCITest;
import org.junit.Test;

import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.assertTrue;

/**
 * Regression pin for the adaptive Z-binning default in {@link MinimaxCITest}.
 *
 * <p><b>Behavior pinned.</b> Permutation within Z-strata calibrates the null
 * "X indep Y given the stratum," not "X indep Y given Z." With a fixed, coarse number of
 * quantile bins for continuous Z, residual within-bin variation of Z leaves within-stratum
 * X&ndash;Y dependence under the true conditional-independence null; that bias is a property of
 * the bin width and does not shrink with n, while the permutation reference tightens with n.
 * Consequently, a fixed-bin test rejects a plain confounded null (X &lt;- Z -&gt; Y) with
 * probability approaching 1 as n grows. The fix makes the bin count grow with n at the
 * Neykov, Balakrishnan &amp; Wasserman (2021) rate d ~ n^(2/5) by default
 * ({@code useAdaptiveZBins == true}), which restores approximate level control.</p>
 *
 * <p><b>Fails on the unpatched build</b> (fixed default of 6 bins: nearly all replications
 * reject the null at n = 4000); <b>passes on the patched build</b> (adaptive bins: most
 * replications correctly fail to reject). The alternative-power check guards against the
 * trivial "fix" of making the test reject nothing.</p>
 */
public class TestMinimaxAdaptiveZBins {

    /**
     * Constructs the test class.
     */
    public TestMinimaxAdaptiveZBins() {
    }

    /**
     * Under the confounded null X &lt;- Z -&gt; Y (linear Gaussian, strong confounder), the
     * default-configured test should retain the null in a clear majority of replications;
     * under the alternative (X -&gt; Y | Z added), it should reject in a clear majority.
     */
    @Test
    public void testAdaptiveBinsControlLevelUnderConfoundedNull() {
        final int n = 4000;
        final int reps = 10;
        final double alpha = 0.05;
        Random rng = new Random(38291L);

        int nullRetained = 0;
        int altRejected = 0;

        for (int r = 0; r < reps; r++) {
            DataSet d0 = confounded(n, false, rng);
            DataSet d1 = confounded(n, true, rng);

            if (pValue(d0, alpha) > alpha) nullRetained++;
            if (pValue(d1, alpha) <= alpha) altRejected++;
        }

        // Unpatched (fixed 6 bins): nullRetained is ~0-1 of 10 at n = 4000.
        // Patched (adaptive ~45 bins): nullRetained is ~9-10 of 10.
        assertTrue("Confounded null retained in only " + nullRetained + "/" + reps
                        + " replications; fixed coarse Z-binning is anticonservative "
                        + "(adaptive Z bins expected by default).",
                nullRetained >= 7);

        assertTrue("Alternative rejected in only " + altRejected + "/" + reps
                        + " replications; adaptive binning should not destroy power.",
                altRejected >= 7);
    }

    /**
     * X = Z + e1, Y = Z + (dep ? 0.3 X : 0) + e2, Z ~ N(0,1). Under dep == false,
     * X is independent of Y given Z.
     */
    private static DataSet confounded(int n, boolean dep, Random rng) {
        List<Node> vars = List.of(new ContinuousVariable("X"),
                new ContinuousVariable("Y"), new ContinuousVariable("Z"));
        DataSet d = new BoxDataSet(new DoubleDataBox(n, 3), vars);
        for (int i = 0; i < n; i++) {
            double z = rng.nextGaussian();
            double x = z + rng.nextGaussian();
            double y = z + (dep ? 0.3 * x : 0.0) + rng.nextGaussian();
            d.setDouble(i, 0, x);
            d.setDouble(i, 1, y);
            d.setDouble(i, 2, z);
        }
        return d;
    }

    private static double pValue(DataSet d, double alpha) {
        MinimaxCITest test = new MinimaxCITest(d, alpha);
        test.setPermutations(200);
        return test.getPValue(d.getVariable("X"), d.getVariable("Y"),
                Set.of(d.getVariable("Z")));
    }
}
