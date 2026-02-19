package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;

@FunctionalInterface
public interface DagMetric {
    DagMetricResult compute(DataSet data, Graph dag);

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

    @FunctionalInterface
    interface MetricComputer {
        double compute(DataSet data, Graph dag);
    }
}