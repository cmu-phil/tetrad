package edu.cmu.tetrad.search.unmix;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;

import java.util.List;

/**
 * Container for the outcome of unmixing: labels, per-cluster datasets, and graphs.
 */
public class UnmixResult {
    public final int[] labels;            // length n
    public final int K;
    public final List<DataSet> clusterData;
    public final List<Graph> clusterGraphs;

    /**
     * Constructs an UnmixResult.
     *
     * @param labels        hard cluster assignment for each row (length n)
     * @param K             the number of clusters
     * @param clusterData   list of cluster-specific datasets (size K)
     * @param clusterGraphs list of learned graphs per cluster (size K)
     */
    public UnmixResult(int[] labels, int K, List<DataSet> clusterData, List<Graph> clusterGraphs) {
        this.labels = labels;
        this.K = K;
        this.clusterData = clusterData;
        this.clusterGraphs = clusterGraphs;
    }
}