package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.data.EmCovarianceEstimator;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Node;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests EM maximum-likelihood estimation of the covariance matrix under a saturated Gaussian model with missing data.
 * <p>
 * The tests check the properties that make the estimator usable as a drop-in replacement for a complete-data
 * covariance matrix: that it reduces to the ML covariance when nothing is missing, that the observed-data likelihood
 * increases monotonically as EM guarantees, that the returned estimate is a stationary point of that likelihood, and
 * that it is not biased by MAR missingness in the way that listwise deletion is.
 *
 * @author josephramsey
 */
public class TestEmCovarianceEstimator {

    private static final long SEED = 492833L;

    /**
     * Simulates n rows from a random linear-Gaussian DAG over p variables (lower-triangular coefficient matrix, so
     * the ordering 0..p-1 is causal).
     */
    private static double[][] simulate(Random rand, int n, int p, double edgeProb) {
        double[][] b = new double[p][p];
        double[] sd = new double[p];

        for (int i = 0; i < p; i++) {
            sd[i] = Math.sqrt(0.5 + rand.nextDouble());

            for (int j = 0; j < i; j++) {
                if (rand.nextDouble() < edgeProb) {
                    double c = 0.3 + 0.6 * rand.nextDouble();
                    b[i][j] = rand.nextBoolean() ? c : -c;
                }
            }
        }

        double[][] data = new double[n][p];

        for (int r = 0; r < n; r++) {
            for (int i = 0; i < p; i++) {
                double v = sd[i] * rand.nextGaussian();

                for (int j = 0; j < i; j++) {
                    v += b[i][j] * data[r][j];
                }

                data[r][i] = v;
            }
        }

        return data;
    }

    /**
     * Returns the maximum likelihood (divisor n) covariance matrix of complete data.
     */
    private static double[][] mlCovariance(double[][] data) {
        int n = data.length;
        int p = data[0].length;
        double[] mu = new double[p];

        for (double[] row : data) {
            for (int j = 0; j < p; j++) {
                mu[j] += row[j];
            }
        }

        for (int j = 0; j < p; j++) {
            mu[j] /= n;
        }

        double[][] s = new double[p][p];

        for (double[] row : data) {
            for (int j = 0; j < p; j++) {
                for (int k = 0; k < p; k++) {
                    s[j][k] += (row[j] - mu[j]) * (row[k] - mu[k]);
                }
            }
        }

        for (int j = 0; j < p; j++) {
            for (int k = 0; k < p; k++) {
                s[j][k] /= n;
            }
        }

        return s;
    }

    /**
     * Returns the largest absolute difference between two covariance matrices, scaled to correlation units by the
     * standard deviations of the reference, so the threshold is interpretable independently of the variables' scales.
     */
    private static double maxDeviation(double[][] a, double[][] reference) {
        int p = reference.length;
        double max = 0.0;

        for (int j = 0; j < p; j++) {
            for (int k = 0; k < p; k++) {
                double scale = Math.sqrt(reference[j][j] * reference[k][k]);
                max = Math.max(max, Math.abs(a[j][k] - reference[j][k]) / scale);
            }
        }

        return max;
    }

    private static double[][] copy(double[][] data) {
        double[][] c = new double[data.length][];

        for (int i = 0; i < data.length; i++) {
            c[i] = data[i].clone();
        }

        return c;
    }

    /**
     * Returns the ML covariance of the complete rows only, or null if there are too few to estimate.
     */
    private static double[][] listwise(double[][] data) {
        int p = data[0].length;
        List<double[]> rows = new ArrayList<>();

        outer:
        for (double[] row : data) {
            for (int j = 0; j < p; j++) {
                if (Double.isNaN(row[j])) continue outer;
            }

            rows.add(row);
        }

        if (rows.size() < p + 2) return null;

        return mlCovariance(rows.toArray(new double[0][]));
    }

    /**
     * With no missing values, EM must reproduce the ordinary ML covariance matrix, and should do so immediately
     * rather than iterating toward it: with no missing entries the E-step is the identity, so the first M-step
     * already lands on the answer.
     */
    @Test
    public void testCompleteDataIsAFixedPoint() {
        Random rand = new Random(SEED);
        double[][] data = simulate(rand, 2000, 8, 0.4);
        double[][] expected = mlCovariance(data);

        EmCovarianceEstimator.Result result = EmCovarianceEstimator.emEstimate(copy(data), 500, 1.0e-10, 0.0);

        assertTrue("EM should converge on complete data", result.converged);
        assertTrue("EM should converge immediately on complete data, took " + result.iterations,
                result.iterations <= 3);
        assertTrue("EM should reproduce the ML covariance on complete data",
                maxDeviation(result.sigma, expected) < 1.0e-10);
        assertEquals(2000, result.numRowsUsed);
    }

    /**
     * EM guarantees that the observed-data log likelihood is nondecreasing across iterations. A violation indicates
     * an error in the E-step, the M-step, or the likelihood itself, so this is the single most informative check on
     * the implementation.
     */
    @Test
    public void testLogLikelihoodIsMonotone() {
        Random rand = new Random(SEED);
        double[][] data = simulate(rand, 3000, 8, 0.4);

        // MAR: the last four variables go missing at a rate depending on the first, which is always observed.
        for (double[] row : data) {
            double pMissing = row[0] > 0 ? 0.55 : 0.08;

            for (int j = 4; j < 8; j++) {
                if (rand.nextDouble() < pMissing) row[j] = Double.NaN;
            }
        }

        EmCovarianceEstimator.Result result = EmCovarianceEstimator.emEstimate(data, 2000, 1.0e-9, 0.0);
        double[] trace = result.logLikelihoodTrace;

        assertTrue("Expecting more than one iteration on data with missing values", trace.length > 1);

        for (int i = 1; i < trace.length; i++) {
            double tolerance = 1.0e-6 * Math.max(1.0, Math.abs(trace[i - 1]));
            assertTrue("Log likelihood decreased at iteration " + i + ": " + trace[i - 1] + " -> " + trace[i],
                    trace[i] >= trace[i - 1] - tolerance);
        }

        assertEquals("Reported log likelihood should be the last trace entry",
                trace[trace.length - 1], result.logLikelihood, 1.0e-9);
    }

    /**
     * The reported log likelihood must be the likelihood at the returned parameters, not at the parameters entering
     * the final iteration; and no small perturbation of the returned estimate should increase it.
     */
    @Test
    public void testSolutionIsAStationaryPoint() {
        Random rand = new Random(SEED);
        int p = 6;
        double[][] data = simulate(rand, 3000, p, 0.5);

        for (double[] row : data) {
            double pMissing = row[0] > 0 ? 0.5 : 0.1;

            for (int j = 2; j < p; j++) {
                if (rand.nextDouble() < pMissing) row[j] = Double.NaN;
            }
        }

        EmCovarianceEstimator.Result result = EmCovarianceEstimator.emEstimate(data, 5000, 1.0e-12, 0.0);

        double best = EmCovarianceEstimator.observedLogLikelihood(data, result.mu, result.sigma);

        assertEquals("Reported log likelihood should be evaluated at the returned parameters",
                best, result.logLikelihood, 1.0e-8);

        Random perturb = new Random(31337L);

        for (int trial = 0; trial < 500; trial++) {
            double eps = Math.pow(10.0, -1.0 - 3.0 * perturb.nextDouble());
            double[] mu2 = result.mu.clone();
            double[][] sigma2 = new double[p][];

            for (int i = 0; i < p; i++) {
                sigma2[i] = result.sigma[i].clone();
            }

            for (int i = 0; i < p; i++) {
                mu2[i] += eps * perturb.nextGaussian() * Math.sqrt(result.sigma[i][i]);
            }

            for (int i = 0; i < p; i++) {
                for (int j = i; j < p; j++) {
                    double delta = eps * perturb.nextGaussian()
                            * Math.sqrt(result.sigma[i][i] * result.sigma[j][j]);
                    sigma2[i][j] += delta;
                    sigma2[j][i] = sigma2[i][j];
                }
            }

            double ll;

            try {
                ll = EmCovarianceEstimator.observedLogLikelihood(data, mu2, sigma2);
            } catch (IllegalArgumentException e) {
                continue; // Perturbation left the positive definite cone.
            }

            assertTrue("A perturbation increased the log likelihood by " + (ll - best)
                    + "; the solution is not a maximum", ll <= best + 1.0e-6);
        }
    }

    /**
     * The point of the exercise: under MAR, listwise deletion conditions on the missingness mechanism and is biased,
     * while EM is not. The margin here is large--listwise deletion is off by tenths of a correlation unit--so this
     * test is about the qualitative difference, not a tuned threshold.
     */
    @Test
    public void testEmIsUnbiasedUnderMarWhereListwiseDeletionIsNot() {
        Random rand = new Random(SEED);
        double[][] complete = simulate(rand, 5000, 8, 0.4);
        double[][] target = mlCovariance(complete);

        double[][] data = copy(complete);

        for (double[] row : data) {
            double pMissing = row[0] > 0 ? 0.55 : 0.08;

            for (int j = 4; j < 8; j++) {
                if (rand.nextDouble() < pMissing) row[j] = Double.NaN;
            }
        }

        EmCovarianceEstimator.Result result = EmCovarianceEstimator.emEstimate(copy(data), 2000, 1.0e-9, 0.0);
        double[][] complete_case = listwise(data);

        double emDeviation = maxDeviation(result.sigma, target);
        double listwiseDeviation = maxDeviation(complete_case, target);

        assertTrue("EM should recover the complete-data covariance closely, deviation was " + emDeviation,
                emDeviation < 0.05);
        assertTrue("Listwise deletion should be visibly biased under MAR, deviation was " + listwiseDeviation,
                listwiseDeviation > 0.2);
        assertTrue("EM should be substantially closer to the truth than listwise deletion",
                emDeviation < 0.25 * listwiseDeviation);
    }

    /**
     * Under MCAR, listwise deletion is unbiased but wasteful. EM should still win, by using the partially observed
     * rows that deletion discards.
     */
    @Test
    public void testEmIsMoreEfficientThanListwiseDeletionUnderMcar() {
        Random rand = new Random(SEED);
        double[][] complete = simulate(rand, 5000, 8, 0.4);
        double[][] target = mlCovariance(complete);

        double[][] data = copy(complete);

        for (double[] row : data) {
            for (int j = 0; j < 8; j++) {
                if (rand.nextDouble() < 0.2) row[j] = Double.NaN;
            }
        }

        EmCovarianceEstimator.Result result = EmCovarianceEstimator.emEstimate(copy(data), 2000, 1.0e-9, 0.0);

        double emDeviation = maxDeviation(result.sigma, target);
        double listwiseDeviation = maxDeviation(listwise(data), target);

        assertTrue("EM (" + emDeviation + ") should beat listwise deletion (" + listwiseDeviation + ") under MCAR",
                emDeviation < listwiseDeviation);
    }

    /**
     * Checks the Tetrad-facing wrapper: the returned covariance matrix should carry the dataset's variables and the
     * number of rows used, and should agree with the numerical core.
     */
    @Test
    public void testWrapperAgreesWithCore() {
        Random rand = new Random(SEED);
        int p = 6;
        double[][] raw = simulate(rand, 1500, p, 0.5);

        for (double[] row : raw) {
            for (int j = 2; j < p; j++) {
                if (rand.nextDouble() < 0.25) row[j] = Double.NaN;
            }
        }

        List<Node> variables = new ArrayList<>();

        for (int j = 1; j <= p; j++) {
            variables.add(new ContinuousVariable("X" + j));
        }

        DataSet dataSet = new BoxDataSet(new DoubleDataBox(copy(raw)), variables);

        EmCovarianceEstimator estimator = new EmCovarianceEstimator(dataSet);
        ICovarianceMatrix covariance = estimator.estimate();

        assertEquals(1500, covariance.getSampleSize());
        assertEquals(variables, covariance.getVariables());
        assertTrue(estimator.isConverged());

        EmCovarianceEstimator.Result core = EmCovarianceEstimator.emEstimate(copy(raw), 500, 1.0e-7, 0.0);

        for (int j = 0; j < p; j++) {
            for (int k = 0; k < p; k++) {
                assertEquals(core.sigma[j][k], covariance.getValue(j, k), 1.0e-10);
            }
        }

        // Every pair should be observed together on some rows, and fewer than all of them.
        assertTrue(estimator.getMinPairwiseCount() > 0);
        assertTrue(estimator.getMinPairwiseCount() < 1500);
    }

    /**
     * A dataset in which some row is entirely missing should still estimate, with that row dropped from the count.
     */
    @Test
    public void testAllMissingRowsAreDropped() {
        Random rand = new Random(SEED);
        double[][] data = simulate(rand, 1000, 5, 0.5);

        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 5; j++) {
                data[i][j] = Double.NaN;
            }
        }

        EmCovarianceEstimator.Result result = EmCovarianceEstimator.emEstimate(data, 500, 1.0e-8, 0.0);
        assertEquals(990, result.numRowsUsed);
    }
}
