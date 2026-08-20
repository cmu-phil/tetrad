package edu.cmu.tetrad.data;

import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.StatUtils;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Pins {@link DataTransforms#covarianceGaussianCopula(DataSet)} and its tau-b helper.
 *
 * @author josephramsey
 */
public class TestGaussianCopulaCorrelation {

    /**
     * Hand-computed tau-b on a tiny tied example. With x = [1, 1, 2, 3] and y = [1, 2, 2, 3] there are four
     * concordant pairs, no discordant pairs, one pair tied in x and one tied in y, so
     * tau_b = 4 / sqrt(5 * 5) = 0.8.
     */
    @Test
    public void testTauBHandComputed() {
        double[] x = {1, 1, 2, 3};
        double[] y = {1, 2, 2, 3};

        assertEquals(0.8, DataTransforms.kendallsTauB(x, y), 1e-12);
    }

    /**
     * Perfect discordance is exactly -1, perfect concordance exactly 1.
     */
    @Test
    public void testTauBExtremes() {
        assertEquals(-1.0, DataTransforms.kendallsTauB(new double[]{1, 2, 3}, new double[]{3, 2, 1}), 1e-12);
        assertEquals(1.0, DataTransforms.kendallsTauB(new double[]{1, 2, 3}, new double[]{5, 6, 7}), 1e-12);
    }

    /**
     * With no ties, tau-b reduces to tau-a, so the O(n log n) merge-sort implementation must agree exactly with
     * the existing O(n^2) pair enumeration in StatUtils. This is the correctness check on the inversion count.
     */
    @Test
    public void testTauBAgreesWithTauAWithoutTies() {
        Random random = new Random(7);
        int n = 400;

        double[] x = new double[n];
        double[] y = new double[n];

        for (int i = 0; i < n; i++) {
            double z = random.nextGaussian();
            x[i] = z;
            y[i] = 0.6 * z + 0.8 * random.nextGaussian();
        }

        assertEquals(StatUtils.kendallsTau(x, y), DataTransforms.kendallsTauB(x, y), 1e-12);
    }

    /**
     * With ties, tau-a is attenuated by the untied denominator and tau-b is not, so tau-b must be strictly larger
     * in magnitude on tied data with a positive association.
     */
    @Test
    public void testTauBCorrectsForTies() {
        Random random = new Random(7);
        int n = 400;

        double[] x = new double[n];
        double[] y = new double[n];

        for (int i = 0; i < n; i++) {
            double z = random.nextGaussian();
            x[i] = Math.round(z);
            y[i] = Math.round(0.6 * z + 0.8 * random.nextGaussian());
        }

        double tauA = StatUtils.kendallsTau(x, y);
        double tauB = DataTransforms.kendallsTauB(x, y);

        assertTrue("tau-b should exceed the tie-attenuated tau-a: " + tauB + " vs " + tauA, tauB > tauA);
    }

    /**
     * Pairwise-complete deletion: NaN rows are dropped and the answer matches the same data with those rows
     * physically removed.
     */
    @Test
    public void testTauBPairwiseComplete() {
        double[] x = {1, 1, Double.NaN, 2, 3};
        double[] y = {1, 2, 7, 2, 3};

        assertEquals(0.8, DataTransforms.kendallsTauB(x, y), 1e-12);
    }

    /**
     * A constant column leaves tau-b undefined rather than zero.
     */
    @Test
    public void testTauBUndefinedOnConstant() {
        double[] x = {1, 1, 1, 1};
        double[] y = {1, 2, 3, 4};

        assertTrue(Double.isNaN(DataTransforms.kendallsTauB(x, y)));
    }

    /**
     * The point of the transform: under a monotone marginal distortion the Pearson correlation is biased toward
     * zero and the copula estimator is not. Exponentiating one variable of a rho = 0.7 Gaussian pair pulls Pearson
     * well below the truth while the copula estimate stays close to it.
     */
    @Test
    public void testRecoversLatentCorrelationUnderMonotoneDistortion() {
        Random random = new Random(11);
        int n = 4000;
        double rho = 0.7;

        DataSet dataSet = continuousDataSet(n, "X", "Y");

        for (int i = 0; i < n; i++) {
            double z1 = random.nextGaussian();
            double z2 = random.nextGaussian();

            dataSet.setDouble(i, 0, z1);
            dataSet.setDouble(i, 1, Math.exp(rho * z1 + Math.sqrt(1 - rho * rho) * z2));
        }

        double copula = DataTransforms.covarianceGaussianCopula(dataSet).getValue(0, 1);
        double pearson = new CorrelationMatrix(new CovarianceMatrix(dataSet)).getValue(0, 1);

        assertEquals("copula estimate should recover the latent correlation", rho, copula, 0.03);
        assertTrue("Pearson should be visibly attenuated here, else the test proves nothing: " + pearson,
                pearson < rho - 0.1);
    }

    /**
     * Structural guarantees on the returned matrix: unit diagonal, symmetry, entries in range, and the source
     * sample size carried through.
     */
    @Test
    public void testMatrixShape() {
        Random random = new Random(3);
        int n = 500;
        int p = 5;

        String[] names = new String[p];

        for (int j = 0; j < p; j++) {
            names[j] = "V" + j;
        }

        DataSet dataSet = continuousDataSet(n, names);

        for (int i = 0; i < n; i++) {
            double common = random.nextGaussian();

            for (int j = 0; j < p; j++) {
                dataSet.setDouble(i, j, common + random.nextGaussian());
            }
        }

        ICovarianceMatrix cov = DataTransforms.covarianceGaussianCopula(dataSet);

        assertEquals(n, cov.getSampleSize());
        assertEquals(p, cov.getDimension());

        for (int i = 0; i < p; i++) {
            assertEquals(1.0, cov.getValue(i, i), 1e-12);

            for (int j = 0; j < p; j++) {
                assertEquals(cov.getValue(i, j), cov.getValue(j, i), 1e-12);
                assertTrue(Math.abs(cov.getValue(i, j)) <= 1.0 + 1e-12);
            }
        }
    }

    /**
     * A constant column yields no defined tau-b for any pair it takes part in; those entries are zeroed and the
     * transform still returns a usable matrix rather than throwing.
     */
    @Test
    public void testConstantColumnDoesNotThrow() {
        Random random = new Random(5);
        int n = 200;

        DataSet dataSet = continuousDataSet(n, "X", "C");

        for (int i = 0; i < n; i++) {
            dataSet.setDouble(i, 0, random.nextGaussian());
            dataSet.setDouble(i, 1, 4.0);
        }

        ICovarianceMatrix cov = DataTransforms.covarianceGaussianCopula(dataSet);

        assertEquals(0.0, cov.getValue(0, 1), 1e-12);
        assertEquals(1.0, cov.getValue(1, 1), 1e-12);
    }

    private static DataSet continuousDataSet(int n, String... names) {
        List<Node> variables = new ArrayList<>();

        for (String name : names) {
            variables.add(new ContinuousVariable(name));
        }

        return new BoxDataSet(new DoubleDataBox(n, variables.size()), variables);
    }
}
