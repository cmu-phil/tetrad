package edu.cmu.tetrad.search;

import java.util.List;

/**
 * The NTadTest interface defines methods for conducting statistical tetrad-based tests, often used in the context of
 * evaluating structural relationships between variables or assessing model constraints.
 * <p>
 * These methods calculate p-values for specific configurations of variables, represented as tetrads, which are sets of
 * indices specifying relationships among variables.
 * <p>
 * This interface supports p x p tetrads for p >= 2, though the implementations may place more restrictive limits on p.
 */
public interface NTadTest {

    /**
     * Computes the value of a statistical test based on the input tetrad configuration. A tetrad is a set of indices
     * specifying structural relationships between variables, and this method evaluates the statistical consistency of
     * such configurations.
     *
     * @param tet a 2D integer array where each inner array defines a tetrad configuration. Each tetrad specifies
     *            indices representing a structural relationship among variables.
     * @return a double value representing the computed result of the statistical tetrad test.
     */
    double tetrad(int[][] tet);

    /**
     * Computes the statistical test results for multiple sets of tetrad configurations. A tetrad is a set of indices
     * specifying structural relationships among variables, and this method evaluates the statistical consistency for
     * each provided configuration.
     *
     * @param tets a series of 2D integer arrays, where each array contains multiple tetrad configurations. Each
     *             configuration specifies a set of indices representing structural relationships among variables.
     * @return a double value representing the aggregated or combined result of the statistical tests applied to the
     * provided tetrad configurations.
     */
    double tetrads(int[][]... tets);

    /**
     * Computes a statistical measure based on the input list of tetrad configurations. Each tetrad configuration
     * represents a set of structural relationships among variables. This method evaluates and combines the statistical
     * results of all provided configurations.
     *
     * @param tets a list of 2D integer arrays where each array contains multiple tetrad configurations. Each
     *             configuration is a set of integer indices representing structural relationships.
     * @return a double value representing the combined statistical measure for the provided tetrad configurations.
     */
    double tetrads(List<int[][]> tets);
}
