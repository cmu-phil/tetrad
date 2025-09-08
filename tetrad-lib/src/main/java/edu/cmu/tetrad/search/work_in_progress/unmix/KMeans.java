package edu.cmu.tetrad.search.work_in_progress.unmix;

import java.util.Arrays;
import java.util.Random;

public final class KMeans {
    public static class Result {
        public final int[] labels;
        public final double[][] centroids;
        public Result(int[] labels, double[][] centroids) { this.labels = labels; this.centroids = centroids; }
    }

    public static Result cluster(double[][] X, int K, int maxIter, long seed) {
        int n = X.length, d = (n == 0 ? 0 : X[0].length);
        if (n == 0 || K <= 0) return new Result(new int[0], new double[Math.max(K,0)][d]);
        if (K > n) K = n;

        Random rnd = new Random(seed);
        double[][] C = new double[K][d];

        // --- k-means++ init ---
        int[] chosen = new int[K];
        chosen[0] = rnd.nextInt(n);
        C[0] = X[chosen[0]].clone();
        double[] dist2 = new double[n];
        Arrays.fill(dist2, Double.POSITIVE_INFINITY);

        for (int k = 1; k < K; k++) {
            // update min distance to any chosen center
            for (int i = 0; i < n; i++) {
                double d2 = sqDist(X[i], C[k - 1]);
                if (d2 < dist2[i]) dist2[i] = d2;
            }
            double sum = 0.0;
            for (double v : dist2) sum += v;

            int pick;
            if (sum == 0.0 || Double.isNaN(sum) || Double.isInfinite(sum)) {
                // All points are identical (or degenerate distances); pick uniformly
                pick = rnd.nextInt(n);
            } else {
                double r = rnd.nextDouble() * sum;
                double acc = 0.0;
                pick = n - 1;
                for (int i = 0; i < n - 1; i++) {
                    acc += dist2[i];
                    if (acc >= r) { pick = i; break; }
                }
            }
            chosen[k] = pick;
            C[k] = X[pick].clone();
        }

        int[] z = new int[n];
        Arrays.fill(z, -1);

        for (int it = 0; it < maxIter; it++) {
            boolean changed = false;

            // assign
            for (int i = 0; i < n; i++) {
                int best = 0; double bestD = sqDist(X[i], C[0]);
                for (int k = 1; k < K; k++) {
                    double d2 = sqDist(X[i], C[k]);
                    if (d2 < bestD) { bestD = d2; best = k; }
                }
                if (z[i] != best) { z[i] = best; changed = true; }
            }

            // update
            double[][] sum = new double[K][d];
            int[] cnt = new int[K];
            for (int i = 0; i < n; i++) { int k = z[i]; cnt[k]++; addInPlace(sum[k], X[i]); }
            for (int k = 0; k < K; k++) {
                if (cnt[k] == 0) continue; // keep old centroid if empty
                for (int j = 0; j < d; j++) C[k][j] = sum[k][j] / cnt[k];
            }
            if (!changed) break;
        }
        return new Result(z, C);
    }

    /** Multiple restarts; returns best by within-cluster SSE. */
    public static Result clusterWithRestarts(double[][] X, int K, int maxIter, long seed, int restarts) {
        Result best = null;
        double bestSse = Double.POSITIVE_INFINITY;
        Random seeder = new Random(seed);
        for (int r = 0; r < Math.max(1, restarts); r++) {
            long s = seeder.nextLong();
            Result res = cluster(X, K, maxIter, s);
            double sse = withinSSE(X, res.labels, res.centroids);
            if (sse < bestSse) { bestSse = sse; best = res; }
        }
        return best;
    }

    private static double withinSSE(double[][] X, int[] z, double[][] C) {
        double s = 0.0;
        for (int i = 0; i < X.length; i++) s += sqDist(X[i], C[z[i]]);
        return s;
    }

    private static double sqDist(double[] a, double[] b) {
        double s = 0;
        for (int j = 0; j < a.length; j++) { double d = a[j] - b[j]; s += d * d; }
        return s;
    }
    private static void addInPlace(double[] a, double[] b) {
        for (int j = 0; j < a.length; j++) a[j] += b[j];
    }
}