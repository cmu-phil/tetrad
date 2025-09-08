package edu.cmu.tetrad.unmix;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import java.util.Arrays;
import java.util.List;

/**
 * Reassign rows by per-cluster Laplace (L1) residual likelihood.
 *
 * For each cluster k:
 *   - fit nodewise regressions on clusterData[k] given clusterGraphs[k],
 *   - compute residuals on FULL data under those fitted mechanisms,
 *   - estimate per-variable Laplace scales b_kj from the cluster's OWN residuals,
 *   - score each row i by sum_j (|r_ij| / b_kj + log(2 b_kj)) and assign to argmin_k.
 *
 * This uses non-Gaussian (L1) noise to separate regimes that differ in residual
 * scale/shape—often much stronger than L2 under LiNG settings.
 */
public final class RowReassignByClusterModelsLaplace {

    /** epsilon to avoid log(0) / div-zero */
    private static final double EPS = 1e-8;

    public static int[] reassign(
            DataSet fullData,
            List<DataSet> clusterData,
            List<Graph> clusterGraphs,
            ResidualRegressor regressor
    ) {
        int K = clusterData.size();
        int n = fullData.getNumRows();
        int p = fullData.getNumColumns();

        // Precompute residuals on FULL data under each cluster's fitted mechanisms
        double[][][] Rk = new double[K][][]; // K x n x p
        double[][] b = new double[K][p];     // Laplace scales per cluster/variable

        for (int k = 0; k < K; k++) {
            DataSet dk = clusterData.get(k);
            Graph Gk = (k < clusterGraphs.size()) ? clusterGraphs.get(k) : null;

            double[][] R_full = new double[n][p];
            Arrays.stream(R_full).forEach(row -> Arrays.fill(row, 0.0));
            double[] scales = new double[p];
            Arrays.fill(scales, 1.0);

            if (dk != null && dk.getNumRows() > 1 && Gk != null) {
                // Fit per-node on dk, residualize both dk and fullData
                for (int j = 0; j < p; j++) {
                    Node v = fullData.getVariables().get(j);
                    var parents = Gk.getParents(v);

                    // fit on cluster data
                    regressor.fit(dk, v, parents);

                    // residuals on full data (for scoring)
                    double[] rFull = regressor.residuals(fullData, v, parents);
                    for (int i = 0; i < n; i++) R_full[i][j] = rFull[i];

                    // residuals on dk (for scale estimate); Laplace b = mean|r|
                    double[] rK = regressor.residuals(dk, v, parents);
                    double mAbs = 0.0;
                    for (double rv : rK) mAbs += Math.abs(rv);
                    mAbs /= Math.max(1, rK.length);
                    // robust fallback: if too small, use median absolute deviation
                    if (!(mAbs > EPS)) {
                        double med = median(rK);
                        double mad = medianAbsDev(rK, med);
                        mAbs = Math.max(mad / Math.log(2.0), 1e-6); // MAD to Laplace scale (approx)
                    }
                    scales[j] = Math.max(mAbs, 1e-6);
                }
            }
            Rk[k] = R_full;
            b[k] = scales;
        }

        // Score each row under each cluster model: sum_j (|r_ij|/b_kj + log(2 b_kj))
        int[] z = new int[n];
        for (int i = 0; i < n; i++) {
            int best = 0;
            double bestScore = Double.POSITIVE_INFINITY;
            for (int k = 0; k < K; k++) {
                double s = 0.0;
                double[][] R = Rk[k];
                double[] bk = b[k];
                for (int j = 0; j < p; j++) {
                    double bj = Math.max(bk[j], 1e-6);
                    s += Math.abs(R[i][j]) / bj + Math.log(2.0 * bj);
                }
                if (s < bestScore) { bestScore = s; best = k; }
            }
            z[i] = best;
        }
        return z;
    }

    private static double median(double[] a) {
        double[] b = a.clone();
        Arrays.sort(b);
        int m = b.length >>> 1;
        return (b.length % 2 == 0) ? 0.5 * (b[m - 1] + b[m]) : b[m];
    }

    private static double medianAbsDev(double[] a, double med) {
        double[] d = new double[a.length];
        for (int i = 0; i < a.length; i++) d[i] = Math.abs(a[i] - med);
        return median(d) + EPS;
    }
}