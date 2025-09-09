package edu.cmu.tetrad.search.unmix;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Function;

/**
 * Container for the outcome of unmixing: labels, per-cluster datasets, graphs,
 * and (optionally) the configs + graph functions used to produce them.
 */
public final class UnmixResult {

    // Core outputs
    public final int[] labels;                   // length n
    public final int K;
    public final List<DataSet> clusterData;      // size K
    public final List<Graph> clusterGraphs;      // size K
    public final GaussianMixtureEM.Model gmmModel;

    // Optional provenance (populate if you have them at call site)
    public final @Nullable EmUnmix.Config emCfg;                   // low-level EM cfg
    public @Nullable CausalUnmixer.Config uiCfg;                   // high-level facade cfg (set by CausalUnmixer)
    public final @Nullable Function<DataSet, Graph> pooledSearchFn;
    public final @Nullable Function<DataSet, Graph> perClusterSearchFn;

    public UnmixResult(
            @NotNull int[] labels,
            int K,
            @NotNull List<DataSet> clusterData,
            @NotNull List<Graph> clusterGraphs,
            @NotNull GaussianMixtureEM.Model gmmModel
    ) {
        this(labels, K, clusterData, clusterGraphs, gmmModel, null, null, null);
    }

    public UnmixResult(
            @NotNull int[] labels,
            int K,
            @NotNull List<DataSet> clusterData,
            @NotNull List<Graph> clusterGraphs,
            @NotNull GaussianMixtureEM.Model gmmModel,
            @Nullable EmUnmix.Config emCfg,
            @Nullable Function<DataSet, Graph> pooledSearchFn,
            @Nullable Function<DataSet, Graph> perClusterSearchFn
    ) {
        this.labels = labels;
        this.K = K;
        this.clusterData = clusterData;
        this.clusterGraphs = clusterGraphs;
        this.gmmModel = gmmModel;
        this.emCfg = emCfg;
        this.pooledSearchFn = pooledSearchFn;
        this.perClusterSearchFn = perClusterSearchFn;
    }

    /** Convenience accessors so existing code like r.pooled() keeps working. */
    public @Nullable Function<DataSet, Graph> pooled()        { return pooledSearchFn; }
    public @Nullable Function<DataSet, Graph> perCluster()    { return perClusterSearchFn; }
}