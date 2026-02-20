package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;

/**
 * Represents a functional interface for computing metrics on directed acyclic graphs (DAGs).
 * This interface provides a mechanism to compute and encapsulate the results of metrics
 * related to a given dataset and graph.
 */
@FunctionalInterface
public interface DagMetric {

    /**
     * Computes and returns the result of a metric evaluation for a given dataset and directed acyclic graph (DAG).
     * This method calculates the metric and encapsulates the result in a {@code DagMetricResult}.
     *
     * @param data The dataset to be analyzed as part of the metric computation.
     * @param dag The directed acyclic graph (DAG) upon which the metric is calculated.
     * @return A {@code DagMetricResult} object containing the name, computed value, and any observations or
     *         exceptions related to the metric computation.
     */
    DagMetricResult compute(DataSet data, Graph dag);

    /**
     * Creates a new instance of a {@code DagMetric} that computes a metric with the specified name
     * using the provided {@code MetricComputer}. The {@code DagMetric} is responsible for computing
     * the value of the metric and handling any exceptions that occur during the computation, returning
     * an appropriate {@code DagMetricResult}.
     *
     * @param name The name of the metric to be computed.
     * @param fn A functional interface implementation that defines the computation logic for the metric,
     *           taking a {@code DataSet} and a {@code Graph} as input and producing a numerical result.
     * @return A {@code DagMetric} implementation that computes the specified metric and returns the result
     *         including the name, computed value, and any exception notes, if applicable.
     */
    static DagMetric of(String name, MetricComputer fn) {
        return (data, dag) -> {
            try {
                double v = fn.compute(data, dag);
                return new DagMetricResult(name, v, null);
            } catch (Throwable t) {
                return new DagMetricResult(name, Double.NaN, t.getClass().getSimpleName() + ": " + t.getMessage());
            }
        };
    }

    /**
     * Represents a functional interface for computing a metric value from a {@code DataSet} and {@code Graph}.
     */
    @FunctionalInterface
    interface MetricComputer {

        /**
         * Computes a metric value based on the given dataset and directed acyclic graph (DAG).
         *
         * @param data the dataset to be evaluated
         * @param dag the directed acyclic graph used for the computation
         * @return the computed metric value as a double
         */
        double compute(DataSet data, Graph dag);
    }
}