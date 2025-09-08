package edu.cmu.tetrad.unmix;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;

import java.util.List;

public class UnmixResult {
    public final int[] labels;            // length n
    public final int K;
    public final List<DataSet> clusterData;
    public final List<Graph> clusterGraphs;

    public UnmixResult(int[] labels, int K, List<DataSet> clusterData, List<Graph> clusterGraphs) {
        this.labels = labels; this.K = K;
        this.clusterData = clusterData; this.clusterGraphs = clusterGraphs;
    }
}