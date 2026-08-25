package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.test.IndTestConditionalGaussianLrt;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.assertTrue;

/**
 * Pins the degrees-of-freedom rule of the CG likelihood-ratio test on sparse discrete tables.
 *
 * <p>Design: X, Y, Z all discrete with 4 levels and skewed marginals (probabilities .55/.30/.10/.05), Y depends on Z,
 * X depends on Z, X _||_ Y | Z. At n = 1500 the 64 (x, y, z) cells are sparse: many are empty by chance (sampling
 * zeros). The pre-2026-8-25 rule charged dof only for target levels observed in each conditioning cell, so the
 * chi-square reference shrank with the sparsity and the test rejected this true null at roughly 20-25%; charging
 * (r - 1) per observed conditioning cell brings it to nominal. The threshold below is loose enough to be stable
 * across seeds and tight enough that the old rule fails it decisively.</p>
 */
public class TestCgLrtSparseTableDof {

    private static final double[] CUM = {.55, .85, .95, 1.0};

    private static int draw(Random rng, double[] cum) {
        double u = rng.nextDouble();
        int c = 0;
        while (u > cum[c]) c++;
        return c;
    }

    /** Draws a 4-level variable whose distribution is shifted by the level of z, keeping the skew. */
    private static int drawGivenZ(Random rng, int z) {
        double[] p = {.55, .30, .10, .05};
        // rotate the skew toward higher levels as z increases: level (i + z) mod 4 gets p[i]
        double[] q = new double[4];
        for (int i = 0; i < 4; i++) q[(i + z) % 4] = p[i];
        double[] cum = new double[4];
        double s = 0;
        for (int i = 0; i < 4; i++) { s += q[i]; cum[i] = s; }
        cum[3] = 1.0;
        return draw(rng, cum);
    }

    public static DataSet sparseNull(int n, Random rng) {
        int[] x = new int[n], y = new int[n], z = new int[n];
        for (int i = 0; i < n; i++) {
            z[i] = draw(rng, CUM);
            x[i] = drawGivenZ(rng, z[i]);
            y[i] = drawGivenZ(rng, (z[i] + 1) % 4);
        }
        List<Node> vars = new ArrayList<>();
        List<String> cats = List.of("c0", "c1", "c2", "c3");
        vars.add(new DiscreteVariable("X", cats));
        vars.add(new DiscreteVariable("Y", cats));
        vars.add(new DiscreteVariable("Z", cats));
        DataSet d = new BoxDataSet(new MixedDataBox(vars, n), vars);
        for (int i = 0; i < n; i++) {
            d.setInt(i, 0, x[i]);
            d.setInt(i, 1, y[i]);
            d.setInt(i, 2, z[i]);
        }
        return d;
    }

    public static double rejectionRate(int R, int n, long seed) {
        Random rng = new Random(seed);
        int rej = 0;
        for (int r = 0; r < R; r++) {
            DataSet d = sparseNull(n, rng);
            IndTestConditionalGaussianLrt test = new IndTestConditionalGaussianLrt(d, 0.05, true);
            Node x = d.getVariable("X"), y = d.getVariable("Y"), z = d.getVariable("Z");
            if (!test.checkIndependence(x, y, Collections.singleton(z)).isIndependent()) rej++;
        }
        return rej / (double) R;
    }

    @Test
    public void testSparseTableNullIsNearNominal() {
        double rate = rejectionRate(400, 1500, 20260825L);
        // Old rule (target levels counted per observed conditioning cell): 0.21-0.26 across seeds at n = 1500
        // (0.37-0.42 at n = 600). New rule: 0.065-0.08 (0.07-0.08 at n = 600).
        assertTrue("CG-LRT rejects a true discrete null on a sparse table at rate " + rate + " (want <= 0.12)",
                rate <= 0.12);
    }
}
