package edu.cmu.tetrad.search.unmix;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * Container for unmixing output (labels, per-cluster datasets, optional graphs).
 */
public class UnmixResult {
    public final int[] labels;            // length n
    public final int K;
    public final List<DataSet> clusterData;
    public final List<Graph> clusterGraphs;  // may be null or empty
    public final GaussianMixtureEM.Model gmmModel;

    /** Full constructor (graphs may be null). */
    public UnmixResult(int[] labels, int K, List<DataSet> clusterData,
                       List<Graph> clusterGraphs, GaussianMixtureEM.Model gmmModel) {
        this.labels = labels;
        this.K = K;
        this.clusterData = clusterData;
        this.clusterGraphs = clusterGraphs;
        this.gmmModel = gmmModel;
    }

    /** Convenience: no graphs. */
    public UnmixResult(int[] labels, int K, List<DataSet> clusterData,
                       GaussianMixtureEM.Model gmmModel) {
        this(labels, K, clusterData, Collections.emptyList(), gmmModel);
    }
}