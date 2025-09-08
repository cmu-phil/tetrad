package edu.cmu.tetrad.unmix;

import java.util.Arrays;

public final class RowReassign {

    /** returns new labels minimizing sum_j r_ij^2 / s2_kj + log s2_kj (diagonal Gaussian). */
    public static int[] reassignByDiagGaussian(double[][] R, int[] labels, int K) {
        int n = R.length, p = n == 0 ? 0 : R[0].length;
        // estimate cluster variances per column
        double[][] s2 = new double[K][p];
        int[] cnt = new int[K];
        for (int i = 0; i < n; i++) cnt[labels[i]]++;
        for (int k = 0; k < K; k++) Arrays.fill(s2[k], 1.0); // fallback
        for (int j = 0; j < p; j++) {
            double[] sum = new double[K];
            double[] sum2 = new double[K];
            for (int i = 0; i < n; i++) {
                int k = labels[i];
                sum[k] += R[i][j];
                sum2[k] += R[i][j] * R[i][j];
            }
            for (int k = 0; k < K; k++) {
                if (cnt[k] > 1) {
                    double mean = sum[k] / cnt[k];
                    double var = (sum2[k] - cnt[k]*mean*mean) / Math.max(cnt[k]-1,1);
                    s2[k][j] = Math.max(var, 1e-6);
                }
            }
        }
        int[] z = new int[n];
        for (int i = 0; i < n; i++) {
            int best = -1; double bestScore = Double.POSITIVE_INFINITY;
            for (int k = 0; k < K; k++) {
                double s = 0;
                for (int j = 0; j < p; j++) {
                    s += (R[i][j]*R[i][j]) / s2[k][j] + Math.log(s2[k][j]);
                }
                if (s < bestScore) { bestScore = s; best = k; }
            }
            z[i] = best;
        }
        return z;
    }
}