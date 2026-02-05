package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;

import java.io.Closeable;
import java.util.List;
import java.util.Map;

/**
 * An interface for performing conditional independence tests using Python.
 * This service is designed to interact with a Python backend for statistical
 * operations, enabling users to calculate p-values for conditional independence
 * relationships in a dataset.
 */
public interface PythonCiService extends Closeable {

    /**
     * Prepares the service for operation if it has not been initialized yet. This method ensures
     * that the necessary state, resources, or configurations are set up based on the provided
     * dataset, variable list, and parameters.
     *
     * @param data the dataset on which the service operates, containing the data required for
     *             conditional independence tests.
     * @param vars the list of variables (nodes) relevant to the operations, representing
     *             the context in which tests or analyses are conducted.
     * @param params a map of configuration parameters used to customize the initialization,
     *               such as specific settings for the Python backend or statistical operations.
     */
    void initializeIfNeeded(DataSet data, List<Node> vars, Map<String, Object> params);

    /**
     * Computes the p-value for a conditional independence test based on the specified variables
     * and parameters. The method evaluates whether the variables indexed by xIndex and yIndex
     * are conditionally independent given the variables indexed by zIndices.
     *
     * @param xIndex the index of the first variable in the conditional independence test.
     * @param yIndex the index of the second variable in the conditional independence test.
     * @param zIndices an array of indices representing the conditioning set of variables.
     * @param alpha the significance level used for the test, helping to determine the threshold
     *              for rejecting the null hypothesis of independence.
     * @return a double representing the p-value of the conditional independence test. A smaller
     *         p-value indicates stronger evidence against the null hypothesis of independence.
     * @throws InterruptedException if the thread executing the method is interrupted during execution.
     */
    double pValue(int xIndex, int yIndex, int[] zIndices, double alpha) throws InterruptedException;

    /**
     * Sets the verbosity mode for the service. When verbosity is enabled, additional
     * logs or debug information may be emitted to assist in tracking the execution
     * flow or diagnosing issues.
     *
     * @param verbose a boolean flag indicating whether to enable or disable verbosity.
     *                If true, verbose output is enabled; if false, it is disabled.
     */
    default void setVerbose(boolean verbose) {}

    /**
     * Updates the parameters for the service with the provided map of key-value pairs.
     * This method allows customization or reconfiguration of the service
     * at runtime by changing its operational parameters.
     *
     * @param params a map containing key-value pairs representing the parameters to be updated.
     *               Each key is a string identifying the parameter, and the associated value
     *               determines the new configuration or setting to be applied.
     */
    default void updateParams(Map<String, Object> params) {}
}