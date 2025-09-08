package edu.cmu.tetrad.unmix;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import java.util.Arrays;
import java.util.List;

public final class RowReassignByClusterModels {

    /**
     * Fit nodewise mechanisms on each cluster's dataset using its graph,
     * score every row of the FULL dataset under each cluster model,
     * and reassign to the lowest diagonal-Gaussian score.
     *
     * Variances are estimated from residuals on the cluster's OWN data.
     */
    public static int[] reassign(
            DataSet fullData,
            List<DataSet> clusterData,
            List<Graph> clusterGraphs,
            ResidualRegressor regressor
    ) {
        int K = clusterData.size();
        int n = fullData.getNumRows();
        int p = fullData.getNumColumns();

        // Precompute residuals on full data under each cluster's fitted mechanisms
        double[][][] Rk = new double[K][][]; // K x n x p
        double[][] s2 = new double[K][p];    // per-cluster residual variances (by column)

        for (int k = 0; k < K; k++) {
            DataSet dk = clusterData.get(k);
            Graph Gk = (k < clusterGraphs.size()) ? clusterGraphs.get(k) : null;

            double[][] R_full = new double[n][p];
            Arrays.stream(R_full).forEach(row -> Arrays.fill(row, 0.0));
            double[] varCol = new double[p];
            Arrays.fill(varCol, 1.0);

            if (dk != null && dk.getNumRows() > 1 && Gk != null) {
                // Fit per-node on dk, then residualize BOTH dk and fullData
                for (int j = 0; j < p; j++) {
                    Node v = fullData.getVariables().get(j);
                    var parents = Gk.getParents(v);

                    // fit on cluster data
                    regressor.fit(dk, v, parents);

                    // residuals on full data (for scoring)
                    double[] rFull = regressor.residuals(fullData, v, parents);
                    for (int i = 0; i < n; i++) R_full[i][j] = rFull[i];

                    // residuals on dk (for variance estimate)
                    double[] rK = regressor.residuals(dk, v, parents);
                    double mean = Arrays.stream(rK).average().orElse(0.0);
                    double sumsq = 0.0;
                    for (double rv : rK) { double d = rv - mean; sumsq += d * d; }
                    double var = (rK.length > 1) ? Math.max(sumsq / (rK.length - 1), 1e-6) : 1.0;
                    varCol[j] = var;
                }
            }
            Rk[k] = R_full;
            s2[k] = varCol;
        }

        // Score each row under each cluster model and reassign
        int[] z = new int[n];
        for (int i = 0; i < n; i++) {
            int best = 0; double bestScore = Double.POSITIVE_INFINITY;
            for (int k = 0; k < K; k++) {
                double s = 0.0;
                double[][] R = Rk[k];
                double[] var = s2[k];
                for (int j = 0; j < p; j++) s += (R[i][j] * R[i][j]) / var[j] + Math.log(var[j]);
                if (s < bestScore) { bestScore = s; best = k; }
            }
            z[i] = best;
        }
        return z;
    }
}