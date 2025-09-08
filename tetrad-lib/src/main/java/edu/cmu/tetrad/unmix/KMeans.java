package edu.cmu.tetrad.unmix;

import java.util.Arrays;
import java.util.Random;

public final class KMeans {
    public static class Result {
        public final int[] labels;
        public final double[][] centroids;
        public Result(int[] labels, double[][] centroids) {
            this.labels = labels; this.centroids = centroids;
        }
    }

    /** K-means with Euclidean distance. maxIter ~ 50 is plenty for residuals. */
    public static Result cluster(double[][] X, int K, int maxIter, long seed) {
        int n = X.length, d = n == 0 ? 0 : X[0].length;
        Random rnd = new Random(seed);
        double[][] C = new double[K][d];
        // k-means++ init (lightweight)
        int[] chosen = new int[K];
        chosen[0] = rnd.nextInt(n);
        C[0] = X[chosen[0]].clone();
        double[] dist2 = new double[n];
        Arrays.fill(dist2, Double.POSITIVE_INFINITY);
        for (int k = 1; k < K; k++) {
            for (int i = 0; i < n; i++) {
                double d2 = sqDist(X[i], C[k-1]);
                if (d2 < dist2[i]) dist2[i] = d2;
            }
            double sum = 0; for (double v : dist2) sum += v;
            double r = rnd.nextDouble() * sum;
            int pick = 0; double acc = 0;
            for (; pick < n-1; pick++) { acc += dist2[pick]; if (acc >= r) break; }
            chosen[k] = pick; C[k] = X[pick].clone();
        }

        int[] z = new int[n];
        for (int it = 0; it < maxIter; it++) {
            boolean changed = false;
            // assign
            for (int i = 0; i < n; i++) {
                int best = -1; double bestD = Double.POSITIVE_INFINITY;
                for (int k = 0; k < K; k++) {
                    double d2 = sqDist(X[i], C[k]);
                    if (d2 < bestD) { bestD = d2; best = k; }
                }
                if (z[i] != best) { z[i] = best; changed = true; }
            }
            // update
            double[][] sum = new double[K][d];
            int[] cnt = new int[K];
            for (int i = 0; i < n; i++) {
                int k = z[i]; cnt[k]++; addInPlace(sum[k], X[i]);
            }
            for (int k = 0; k < K; k++) {
                if (cnt[k] == 0) continue; // keep old centroid if empty
                for (int j = 0; j < d; j++) C[k][j] = sum[k][j] / cnt[k];
            }
            if (!changed) break;
        }
        return new Result(z, C);
    }

    private static double sqDist(double[] a, double[] b) {
        double s = 0;
        for (int j = 0; j < a.length; j++) { double d = a[j] - b[j]; s += d*d; }
        return s;
    }
    private static void addInPlace(double[] a, double[] b) {
        for (int j = 0; j < a.length; j++) a[j] += b[j];
    }
}