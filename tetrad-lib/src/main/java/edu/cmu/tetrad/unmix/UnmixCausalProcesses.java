package edu.cmu.tetrad.unmix;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class UnmixCausalProcesses {

    public static class Config {
        public int K;                 // number of clusters to produce
        public long seed = 13;
        public int kmeansIters = 50;
        public boolean doOneReassign = true;
        public boolean robustScaleResiduals = true;

        // NEW: parent-superset initializer for very different component graphs
        public boolean useParentSuperset = false;
        public ParentSupersetBuilder.Config supersetCfg = new ParentSupersetBuilder.Config();
    }

    /**
     * Main entry:
     *  1) build row signatures (residuals) either from pooled graph or parent-superset
     *  2) k-means on residuals
     *  3) per-cluster search
     *  4) optional one hard reassignment and fast refit (no re-search)
     */
    public static UnmixResult run(
            DataSet data,
            Config cfg,
            ResidualRegressor regressor,
            Function<DataSet, Graph> pooledSearch,   // may be null if useParentSuperset=true
            Function<DataSet, Graph> perClusterSearch
    ) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(regressor, "regressor");
        Objects.requireNonNull(perClusterSearch, "perClusterSearch");

        // 1) residual signatures
        double[][] R = buildResiduals(data, cfg, regressor, pooledSearch);
        if (cfg.robustScaleResiduals) ResidualUtils.robustStandardizeInPlace(R);

        // 2) cluster rows
        KMeans.Result km = KMeans.cluster(R, cfg.K, cfg.kmeansIters, cfg.seed);
        int[] z = km.labels;

        // 3) build per-cluster datasets
        List<DataSet> parts = splitByLabels(data, z, cfg.K);

        // 4) search per cluster
        List<Graph> graphs = parts.stream()
                .map(perClusterSearch)
                .collect(Collectors.toList());

        // 5) optional one reassignment pass (cheap)
        if (cfg.doOneReassign) {
            int[] z2 = RowReassign.reassignByDiagGaussian(R, z, cfg.K);
            if (!Arrays.equals(z, z2)) {
                z = z2;
                parts = splitByLabels(data, z, cfg.K);
                graphs = parts.stream().map(perClusterSearch).collect(Collectors.toList());
            }
        }
        return new UnmixResult(z, cfg.K, parts, graphs);
    }

    /** Build residual matrix according to the chosen initializer. */
    private static double[][] buildResiduals(
            DataSet data,
            Config cfg,
            ResidualRegressor regressor,
            Function<DataSet, Graph> pooledSearch // may be null when useParentSuperset=true
    ) {
        if (cfg.useParentSuperset) {
            // Parent-superset path: no pooled DAG assumption
            Map<Node, List<Node>> paSuperset = ParentSupersetBuilder.build(data, cfg.supersetCfg);
            return ResidualUtils.residualMatrix(data, paSuperset, regressor);
        } else {
            // Pooled DAG path
            Objects.requireNonNull(pooledSearch, "pooledSearch must be provided when useParentSuperset=false");
            Graph gPool = pooledSearch.apply(data);
            return ResidualUtils.residualMatrix(data, gPool, regressor);
        }
    }

    /** Split a dataset into K parts based on cluster labels (bounds-safe, preserves var metadata). */
    private static List<DataSet> splitByLabels(DataSet data, int[] z, int K) {
        int n = data.getNumRows();
        List<List<Integer>> idx = new ArrayList<>(K);
        for (int k = 0; k < K; k++) idx.add(new ArrayList<>());

        for (int i = 0; i < n; i++) {
            int lab = z[i];
            if (lab >= 0 && lab < K) {
                idx.get(lab).add(i);
            }
        }

        List<DataSet> out = new ArrayList<>(K);
        for (int k = 0; k < K; k++) {
            List<Integer> rows = idx.get(k);
            if (rows.isEmpty()) {
                // empty cluster → return empty dataset with same variables
                DoubleDataBox box = new DoubleDataBox(0, data.getVariables().size());
                out.add(new BoxDataSet(box, data.getVariables()));
            } else {
                int[] rowArray = rows.stream().mapToInt(Integer::intValue).toArray();
                out.add(data.subsetRows(rowArray));
            }
        }
        return out;
    }

    /** Try K=Kmin..Kmax and pick the one with the best internal diag-Gaussian score under the SAME initializer as run(). */
    public static UnmixResult selectK(
            DataSet data,
            int Kmin,
            int Kmax,
            ResidualRegressor regressor,
            Function<DataSet, Graph> pooledSearch,   // may be null if using parent-superset
            Function<DataSet, Graph> perClusterSearch,
            long seed,
            Config baseCfg // we copy initializer knobs from here
    ) {
        Objects.requireNonNull(baseCfg, "baseCfg");
        double bestScore = Double.POSITIVE_INFINITY;
        UnmixResult best = null;

        for (int K = Kmin; K <= Kmax; K++) {
            Config cfg = new Config();
            // copy initializer knobs
            cfg.useParentSuperset = baseCfg.useParentSuperset;
            cfg.supersetCfg = baseCfg.supersetCfg;
            cfg.robustScaleResiduals = baseCfg.robustScaleResiduals;
            cfg.doOneReassign = baseCfg.doOneReassign; // OK to keep same
            cfg.kmeansIters = baseCfg.kmeansIters;
            cfg.seed = seed;
            cfg.K = K;

            UnmixResult res = run(data, cfg, regressor, pooledSearch, perClusterSearch);

            // Rebuild residuals the SAME way for scoring
            double[][] R = buildResiduals(data, cfg, regressor, pooledSearch);
            if (cfg.robustScaleResiduals) ResidualUtils.robustStandardizeInPlace(R);
            double score = avgDiagGaussianScore(R, res.labels, K);

            if (score < bestScore) { bestScore = score; best = res; }
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
}