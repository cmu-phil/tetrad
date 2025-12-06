package edu.cmu.tetrad.search.rlcd;

/**
 * Rank test interface mirroring Python ranktest_method.test(...).
 */
public interface RankTest {
    /**
     * @param pCols indices of A∪X columns
     * @param qCols indices of B∪X columns
     * @param k     hypothesized rank
     * @param alpha significance level
     * @return true if we FAIL to reject H0: rank <= k (same convention as Python).
     */
    boolean test(int[] pCols, int[] qCols, int k, double alpha);
}