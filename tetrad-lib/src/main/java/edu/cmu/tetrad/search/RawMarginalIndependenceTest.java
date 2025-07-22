package edu.cmu.tetrad.search;

/**
 * Functional interface for performing a raw marginal independence test.
 * <p>
 * This interface provides a method to compute the p-value for the statistical test
 * of marginal independence between two variables, represented as double arrays. The test
 * evaluates the null hypothesis that the two variables are statistically independent.
 */
@FunctionalInterface
public interface RawMarginalIndependenceTest {

    /**
     * Computes the p-value for the statistical test of marginal independence between
     * the two given variables represented by the input arrays.
     *
     * @param x the first variable, represented as an array of doubles
     * @param y the second variable, represented as an array of doubles
     * @return the computed p-value for the test of marginal independence
     * @throws InterruptedException if the computation is interrupted
     */
    double computePValue(double[] x, double[] y) throws InterruptedException;
}
