package edu.cmu.tetrad.search.work_in_progress.unmix;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.unmix.*;

import java.util.*;
import java.util.function.Function;

/**
 * Procedures to unmix heterogeneous datasets into clusters of causal processes.
 * Main entry: run(...). Includes selectK(...) for simple model selection.
 */
public class UnmixCausalProcesses {

    /**
     * Unmixes the dataset into K clusters of causal processes.
     * Steps:
     *  1) Build per-row residual signatures (pooled graph or parent-superset initializer)
     *  2) K-means on residuals
     *  3) Per-cluster structure search
     *  4) Optional multi-pass reassignment using cluster mechanisms
     */
    public static UnmixResult run(
            DataSet data,
            Config cfg,
            ResidualRegressor regressor,
            Function<DataSet, Graph> pooledSearch,
            Function<DataSet, Graph> perClusterSearch
    ) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(regressor, "regressor");
        Objects.requireNonNull(perClusterSearch, "perClusterSearch");

        // 1) residual signatures
        double[][] R = buildResiduals(data, cfg, regressor, pooledSearch);
        if (cfg.robustScaleResiduals) ResidualUtils.robustStandardizeInPlace(R);

        // 2) cluster rows (GMM or KMeans)
        GaussianMixtureEM.Model gmmModel = null;
        int[] z;

        if (cfg.useGmmClustering) {
            GaussianMixtureEM.Config gcfg = new GaussianMixtureEM.Config();
            gcfg.K = cfg.K;
            gcfg.seed = cfg.seed;

            // Fit on residual features R
            gmmModel = GaussianMixtureEM.fit(R, gcfg);
            z = EmUtils.mapLabels(gmmModel.responsibilities);
        } else {
            // KMeans path (what you had)
            KMeans.Result km = KMeans.clusterWithRestarts(R, cfg.K, cfg.kmeansIters, cfg.seed, 10);
            z = km.labels;
        }

        // 3) split and per-cluster search (guard empty clusters)
        List<DataSet> parts = splitByLabels(data, z, cfg.K);
        List<Graph> graphs = searchPerCluster(parts, perClusterSearch);

        // Normalize legacy flag
        int maxPasses = Math.max(cfg.reassignMaxPasses, cfg.doOneReassign ? 1 : 0);

        // 4) multi-pass reassignment using cluster mechanisms
        for (int pass = 0; pass < maxPasses; pass++) {
            int[] z2;
            if (cfg.useLaplaceReassign) {
                z2 = RowReassignByClusterModelsLaplace.reassign(data, parts, graphs, regressor);
            } else {
                z2 = RowReassignByClusterModels.reassign(data, parts, graphs, regressor);
            }
            boolean changed = !java.util.Arrays.equals(z, z2);
            if (!changed && cfg.reassignStopIfNoChange) break;

            z = z2;
            parts = splitByLabels(data, z, cfg.K);
            graphs = searchPerCluster(parts, perClusterSearch);
        }

        return new UnmixResult(z, cfg.K, parts, graphs, gmmModel);
    }

    /** Build residual matrix according to the chosen initializer. */
    private static double[][] buildResiduals(
            DataSet data,
            Config cfg,
            ResidualRegressor regressor,
            Function<DataSet, Graph> pooledSearch // may be null when useParentSuperset=true
    ) {
        if (cfg.useParentSuperset) {
            Map<Node, List<Node>> paSuperset = ParentSupersetBuilder.build(data, cfg.supersetCfg);
            return ResidualUtils.residualMatrix(data, paSuperset, regressor);
        } else {
            Objects.requireNonNull(pooledSearch, "pooledSearch must be provided when useParentSuperset=false");
            Graph gPool = pooledSearch.apply(data);
            return ResidualUtils.residualMatrix(data, gPool, regressor);
        }
    }

    /** Guarded per-cluster search (handles empty clusters). */
    private static List<Graph> searchPerCluster(List<DataSet> parts, Function<DataSet, Graph> perClusterSearch) {
        List<Graph> graphs = new ArrayList<>(parts.size());
        for (DataSet dk : parts) {
            if (dk == null || dk.getNumRows() == 0) {
                graphs.add(null); // or reuse a pooled graph if you prefer
            } else {
                graphs.add(perClusterSearch.apply(dk));
            }
        }
        return graphs;
    }

    /** Split a dataset into K parts based on cluster labels (bounds-safe, preserves var metadata). */
    private static List<DataSet> splitByLabels(DataSet data, int[] z, int K) {
        int n = data.getNumRows();
        List<List<Integer>> idx = new ArrayList<>(K);
        for (int k = 0; k < K; k++) idx.add(new ArrayList<>());

        for (int i = 0; i < n; i++) {
            int lab = z[i];
            if (lab >= 0 && lab < K) idx.get(lab).add(i);
        }

        List<DataSet> out = new ArrayList<>(K);
        for (int k = 0; k < K; k++) {
            List<Integer> rows = idx.get(k);
            if (rows.isEmpty()) {
                DoubleDataBox box = new DoubleDataBox(0, data.getVariables().size());
                out.add(new BoxDataSet(box, data.getVariables()));
            } else {
                int[] rowsArray = rows.stream().mapToInt(Integer::intValue).toArray();
                out.add(data.subsetRows(rowsArray));
            }
        }
        return out;
    }

    /**
     * Try K in [Kmin, Kmax] and select the best according to an internal
     * diagonal-Gaussian residual score, using the SAME initializer as run().
     */
    public static UnmixResult selectK(
            DataSet data,
            int Kmin,
            int Kmax,
            ResidualRegressor regressor,
            Function<DataSet, Graph> pooledSearch,   // may be null if using parent-superset
            Function<DataSet, Graph> perClusterSearch,
            long seed,
            Config baseCfg
    ) {
        Objects.requireNonNull(baseCfg, "baseCfg");
        double bestScore = Double.POSITIVE_INFINITY;
        UnmixResult best = null;

        for (int K = Kmin; K <= Kmax; K++) {
            Config cfg = new Config();
            // copy initializer & loop knobs
            cfg.useParentSuperset = baseCfg.useParentSuperset;
            cfg.supersetCfg = copy(baseCfg.supersetCfg);
            cfg.robustScaleResiduals = baseCfg.robustScaleResiduals;
            cfg.kmeansIters = baseCfg.kmeansIters;
            cfg.reassignMaxPasses = baseCfg.reassignMaxPasses;
            cfg.reassignStopIfNoChange = baseCfg.reassignStopIfNoChange;
            cfg.seed = seed;
            cfg.K = K;

            UnmixResult res = run(data, cfg, regressor, pooledSearch, perClusterSearch);

            // Score using SAME residuals path
            double[][] R = buildResiduals(data, cfg, regressor, pooledSearch);
            if (cfg.robustScaleResiduals) ResidualUtils.robustStandardizeInPlace(R);
            double score = avgDiagGaussianScore(R, res.labels, K);

            if (score < bestScore) {
                bestScore = score;
                best = res;
            }
        }
        return best;
    }

    /** Internal: average per-row diagonal-Gaussian residual score within assigned cluster. */
    private static double avgDiagGaussianScore(double[][] R, int[] z, int K) {
        int n = R.length, p = n == 0 ? 0 : R[0].length;
        double[][] s2 = new double[K][p];
        int[] cnt = new int[K];
        double[][] sum = new double[K][p], sum2 = new double[K][p];

        for (int i = 0; i < n; i++) {
            int k = z[i];
            if (k < 0 || k >= K) continue;
            cnt[k]++;
            for (int j = 0; j < p; j++) {
                double rij = R[i][j];
                sum[k][j]  += rij;
                sum2[k][j] += rij * rij;
            }
        }

        for (int k = 0; k < K; k++) {
            for (int j = 0; j < p; j++) {
                double mean = cnt[k] > 0 ? sum[k][j] / cnt[k] : 0.0;
                double var  = cnt[k] > 1 ? (sum2[k][j] - cnt[k] * mean * mean) / Math.max(cnt[k] - 1, 1) : 1.0;
                s2[k][j] = Math.max(var, 1e-6);
            }
        }

        double total = 0.0;
        int used = 0;
        for (int i = 0; i < n; i++) {
            int k = z[i];
            if (k < 0 || k >= K) continue;
            double s = 0.0;
            for (int j = 0; j < p; j++) {
                s += (R[i][j] * R[i][j]) / s2[k][j] + Math.log(s2[k][j]);
            }
            total += s;
            used++;
        }
        return used == 0 ? Double.POSITIVE_INFINITY : total / used;
    }

    /** Shallow copy for superset config to avoid cross-trial mutation in selectK. */
    private static ParentSupersetBuilder.Config copy(ParentSupersetBuilder.Config c) {
        ParentSupersetBuilder.Config d = new ParentSupersetBuilder.Config();
        d.topM = c.topM;
        d.scoreType = c.scoreType;
        d.useBagging = c.useBagging;
        d.bags = c.bags;
        d.bagFraction = c.bagFraction;
        d.seed = c.seed;
        d.shallowSearch = c.shallowSearch;
        return d;
    }

    // ---------------------------- Config ----------------------------

    public static class Config {
        public int K;                 // number of clusters to produce
        public long seed = 13L;
        public int kmeansIters = 50;
        public boolean doOneReassign = true;         // legacy; ensures >=1 pass when true
        public boolean robustScaleResiduals = true;

        // Reassignment control
        public int reassignMaxPasses = 3;            // a few passes are plenty
        public boolean reassignStopIfNoChange = true;

        // Reassignment likelihood: Laplace for LiNG (non-Gaussian) mixtures
        public boolean useLaplaceReassign = false;

        // Robust initializer (when component graphs differ a lot)
        public boolean useParentSuperset = false;
        public ParentSupersetBuilder.Config supersetCfg = new ParentSupersetBuilder.Config();

        // Clustering & feature engineering
        public boolean useGmmClustering = true; // use GaussianMixtureEM on residual features instead of KMeans
        public GaussianMixtureEM.CovarianceType gmmCovType = GaussianMixtureEM.CovarianceType.DIAGONAL;

        public enum FeatureMode { RAW, RAW_PLUS_ABS, RAW_PLUS_ABS_SQ }
        public enum ScaleMode  { NONE, Z, ROBUST_IQR }

        public FeatureMode featureMode = FeatureMode.RAW;
        public ScaleMode  featureScale = ScaleMode.NONE;
    }
}