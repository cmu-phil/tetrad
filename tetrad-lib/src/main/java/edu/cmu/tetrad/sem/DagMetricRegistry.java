package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.data.DataSet;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Registry class that provides utility methods for working with DAG metrics.
 * This class is final and cannot be instantiated.
 */
public final class DagMetricRegistry {

    private DagMetricRegistry() {
    }

    /**
     * Returns the default metric list for a dataset type, in display order.
     *
     * @param data the dataset
     * @return the default metric list for the dataset type
     */
    public static @NotNull List<DagMetric> defaultMetricsFor(@NotNull DataSet data) {
        List<DagMetric> metrics = new ArrayList<>();

        if (data.isContinuous()) {
            metrics.add(DagMetrics.semBic());
            metrics.add(DagMetrics.lgChiSquare());
            metrics.add(DagMetrics.cfi());
            metrics.add(DagMetrics.lgModelP());
            metrics.add(DagMetrics.rmsea());
            metrics.add(DagMetrics.ffml());
            metrics.add(DagMetrics.legendreBic());
            metrics.add(DagMetrics.minimaxTrffBic());
            metrics.add(DagMetrics.mmd2());
        } else if (data.isMixed()) {
            metrics.add(DagMetrics.ffml());
            metrics.add(DagMetrics.legendreBic());
            metrics.add(DagMetrics.minimaxTrffBic());
            metrics.add(DagMetrics.mmd2());
        } else {
            // Discrete-only (if you want):
            metrics.add(DagMetrics.mmd2());
        }

        return metrics;
    }
}