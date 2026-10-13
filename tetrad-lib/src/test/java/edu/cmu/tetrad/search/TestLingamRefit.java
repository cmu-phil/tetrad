package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Matrix;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests of Lingam.regressAndPrune (the Shimizu et al. 2006 step-5 refit): coefficients are
 * replaced by regression estimates, spurious edges are pruned by the t-test, true edges the
 * threshold stage missed are restored when the order allows, and non-DAG input passes through
 * unchanged.
 */
public class TestLingamRefit {

    /** x0 -> x1 (1.0), x1 -> x2 (0.8), Exp(1) errors; N rows; seeded. */
    private static DataSet chainData(int n, long seed) {
        Random rng = new Random(seed);
        double[][] d = new double[n][3];
        for (int r = 0; r < n; r++) {
            double e0 = exp1(rng), e1 = exp1(rng), e2 = exp1(rng);
            double x0 = e0;
            double x1 = 1.0 * x0 + e1;
            double x2 = 0.8 * x1 + e2;
            d[r][0] = x0;
            d[r][1] = x1;
            d[r][2] = x2;
        }
        return wrap(d);
    }

    private static double exp1(Random rng) {
        return -Math.log(1.0 - rng.nextDouble());
    }

    private static DataSet wrap(double[][] d) {
        List<Node> vars = new ArrayList<>();
        for (int j = 0; j < d[0].length; j++) vars.add(new ContinuousVariable("x" + j));
        return new BoxDataSet(new DoubleDataBox(d), vars);
    }

    @Test
    public void refitRecoversCoefficientsAndPrunesSpuriousEdge() {
        DataSet data = chainData(10000, 31L);

        // Input: the true structure with badly perturbed ICA-style coefficients, plus a
        // spurious x0 -> x2 edge (true partial coefficient zero given x1).
        Matrix bHat = new Matrix(3, 3);
        bHat.set(1, 0, 1.3);  // x0 -> x1, truth 1.0
        bHat.set(2, 1, 0.55); // x1 -> x2, truth 0.8
        bHat.set(2, 0, 0.3);  // spurious

        Lingam lingam = new Lingam();
        Matrix out = lingam.regressAndPrune(bHat, data);

        assertEquals(1.0, out.get(1, 0), 0.05);
        assertEquals(0.8, out.get(2, 1), 0.05);
        assertEquals("Spurious edge should be pruned by the t-test", 0.0, out.get(2, 0), 0.0);
    }

    @Test
    public void refitRestoresTrueEdgeTheThresholdStageMissed() {
        // x0 -> x1 (1.0), x0 -> x2 (0.06), x1 -> x2 (1.0); the 0.06 edge is far below the
        // 0.1 threshold, so the trimming stage would never keep it, but given the order
        // x0 < x1 < x2 the regression finds it highly significant at N = 20000.
        Random rng = new Random(7L);
        int n = 20000;
        double[][] d = new double[n][3];
        for (int r = 0; r < n; r++) {
            double x0 = exp1(rng);
            double x1 = 1.0 * x0 + exp1(rng);
            double x2 = 0.06 * x0 + 1.0 * x1 + exp1(rng);
            d[r][0] = x0;
            d[r][1] = x1;
            d[r][2] = x2;
        }
        DataSet data = wrap(d);

        Matrix bHat = new Matrix(3, 3);
        bHat.set(1, 0, 1.0); // x0 -> x1
        bHat.set(2, 1, 1.0); // x1 -> x2; the weak x0 -> x2 edge is absent from the input.

        Lingam lingam = new Lingam();
        Matrix out = lingam.regressAndPrune(bHat, data);

        assertTrue("The weak true edge should be restored by the regression",
                out.get(2, 0) != 0.0);
        assertEquals(0.06, out.get(2, 0), 0.03);
        assertEquals(1.0, out.get(2, 1), 0.05);
    }

    @Test
    public void cyclicInputIsReturnedUnchanged() {
        DataSet data = chainData(500, 5L);

        Matrix cyclic = new Matrix(3, 3);
        cyclic.set(0, 1, 0.5);
        cyclic.set(1, 0, 0.5);

        Matrix out = new Lingam().regressAndPrune(cyclic, data);

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                assertEquals(cyclic.get(i, j), out.get(i, j), 0.0);
            }
        }
    }
}
