package edu.cmu.tetrad.search.rlcd;

/**
 * Rank test interface mirroring Python ranktest_method.test(...).
 */
public interface RankTest {
    /**
     * Tests the validity of specific rank conditions between two sets of columns.
     *
     * @param pCols an array of integers representing the indices of the first set of columns.
     * @param qCols an array of integers representing the indices of the second set of columns.
     * @param k an integer parameter indicating the threshold or rank condition to validate.
     * @param alpha a double value representing the significance level or threshold for the test.
     * @return {@code true} if the test passes based on the specified parameters, {@code false} otherwise.
     */
    boolean test(int[] pCols, int[] qCols, int k, double alpha);
}