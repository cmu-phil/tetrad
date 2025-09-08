package edu.cmu.tetrad.unmix;

import edu.cmu.tetrad.data.DataBox;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class UnmixCausalProcesses {

    public static class Config {
        public int K;                 // number of clusters to produce
        public long seed = 13;
        public int kmeansIters = 50;
        public boolean doOneReassign = true;
        public boolean robustScaleResiduals = true;
    }

    /**
     * Main entry:
     *  1) pooled graph
     *  2) residual matrix
     *  3) k-means on residuals
     *  4) per-cluster search
     *  5) optional one hard reassignment and fast refit (no re-search)
     */
    public static UnmixResult run(
            DataSet data,
            Config cfg,
            ResidualRegressor regressor,
            Function<DataSet, Graph> pooledSearch,
            Function<DataSet, Graph> perClusterSearch
    ) {
        Objects.requireNonNull(data);
        Objects.requireNonNull(regressor);
        Graph Gpool = pooledSearch.apply(data);

        // residuals
        double[][] R = ResidualUtils.residualMatrix(data, Gpool, regressor);
        if (cfg.robustScaleResiduals) ResidualUtils.robustStandardizeInPlace(R);

        // cluster rows
        KMeans.Result km = KMeans.cluster(R, cfg.K, cfg.kmeansIters, cfg.seed);
        int[] z = km.labels;

        // build per-cluster datasets
        List<DataSet> parts = splitByLabels(data, z, cfg.K);

        // search per cluster
        List<Graph> graphs = parts.stream()
                .map(perClusterSearch)
                .collect(Collectors.toList());

        // optional one reassignment pass (cheap)
        if (cfg.doOneReassign) {
            int[] z2 = RowReassign.reassignByDiagGaussian(R, z, cfg.K);
            if (!Arrays.equals(z, z2)) {
                z = z2;
                parts = splitByLabels(data, z, cfg.K);
                // fast refit: keep graphs or re-search (toggle here as you like).
                graphs = parts.stream().map(perClusterSearch).collect(Collectors.toList());
            }
        }
        return new UnmixResult(z, cfg.K, parts, graphs);
    }

    private static List<DataSet> splitByLabels(DataSet data, int[] z, int K) {
        int n = data.getNumRows();
        List<List<Integer>> idx = new ArrayList<>();
        for (int k = 0; k < K; k++) idx.add(new ArrayList<>());
        for (int i = 0; i < n; i++) idx.get(z[i]).add(i);

        List<DataSet> out = new ArrayList<>();
        for (int k = 0; k < K; k++) {
            List<Integer> rows = idx.get(k);
            if (rows.isEmpty()) {
                // make an empty dataset with same vars
                DoubleDataBox dataBox = new DoubleDataBox(0, data.getVariables().size());
                out.add(new BoxDataSet(dataBox, data.getVariables()));
                continue;
            }

            int[] rowArray = rows.stream().mapToInt(Integer::intValue).toArray();
            DataSet dk = data.subsetRows(rowArray);
            out.add(dk);
        }
        return out;
    }

    /** Utility to try K=1..Kmax and pick by simple internal criterion (avg diag-Gaussian score). */
    public static UnmixResult selectK(
            DataSet data,
            int Kmin,
            int Kmax,
            ResidualRegressor regressor,
            Function<DataSet, Graph> pooledSearch,
            Function<DataSet, Graph> perClusterSearch,
            long seed
    ) {
        double bestScore = Double.POSITIVE_INFINITY;
        UnmixResult best = null;
        for (int K = Kmin; K <= Kmax; K++) {
            Config cfg = new Config();
            cfg.K = K; cfg.seed = seed;
            UnmixResult res = run(data, cfg, regressor, pooledSearch, perClusterSearch);
            // compute internal score: average row-wise score against its cluster
            double[][] R = ResidualUtils.residualMatrix(data, pooledSearch.apply(data), regressor);
            ResidualUtils.robustStandardizeInPlace(R);
            double score = avgDiagGaussianScore(R, res.labels, K);
            if (score < bestScore) { bestScore = score; best = res; }
        }
        return best;
    }

    private static double avgDiagGaussianScore(double[][] R, int[] z, int K) {
        // reuse RowReassign code to compute per-row scores
        int n = R.length, p = n == 0 ? 0 : R[0].length;
        // quick variance per cluster/column
        double[][] s2 = new double[K][p];
        int[] cnt = new int[K];
        double[][] sum = new double[K][p], sum2 = new double[K][p];
        for (int i = 0; i < n; i++) {
            int k = z[i]; cnt[k]++;
            for (int j = 0; j < p; j++) { sum[k][j]+=R[i][j]; sum2[k][j]+=R[i][j]*R[i][j]; }
        }
        for (int k = 0; k < K; k++) {
            for (int j = 0; j < p; j++) {
                double mean = cnt[k] > 0 ? sum[k][j]/cnt[k] : 0.0;
                double var = cnt[k] > 1 ? (sum2[k][j]-cnt[k]*mean*mean)/Math.max(cnt[k]-1,1) : 1.0;
                s2[k][j] = Math.max(var, 1e-6);
            }
        }
        double total = 0;
        for (int i = 0; i < n; i++) {
            int k = z[i];
            double s = 0;
            for (int j = 0; j < p; j++) s += (R[i][j]*R[i][j])/s2[k][j] + Math.log(s2[k][j]);
            total += s;
        }
        return total / Math.max(n,1);
    }
}