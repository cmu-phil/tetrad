///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.unmix;

import edu.cmu.tetrad.util.StatUtils;

import java.util.Arrays;
import java.util.Random;

/**
 * Implements the K-Means clustering algorithm using the k-means++ initialization method and iterative refinement. The
 * algorithm partitions a dataset into a specified number of clusters by minimizing the sum of squared distances between
 * points and their respective cluster centroids.
 */
public final class KMeans {

    /**
     * Performs k-means clustering on a given dataset.
     *
     * @param X       The data points to be clustered, represented as a 2D array where each row corresponds to a data
     *                point and each column corresponds to a feature. The dataset must be non-null.
     * @param K       The number of clusters to create. If K is greater than the number of data points, it will be
     *                adjusted to the number of data points. K must be a positive integer.
     * @param maxIter The maximum number of iterations the algorithm will run. Must be a positive integer.
     * @param seed    The seed for the random number generator used to initialize the cluster centroids.
     * @return An instance of the {@code Result} class containing the cluster assignments (labels) for each data point
     * and the coordinates of the centroids of the clusters.
     */
    public static Result cluster(double[][] X, int K, int maxIter, long seed) {
        int n = X.length, d = (n == 0 ? 0 : X[0].length);
        if (n == 0 || K <= 0) return new Result(new int[0], new double[Math.max(K, 0)][d]);
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
            double sum = StatUtils.sum(dist2);

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
                    if (acc >= r) {
                        pick = i;
                        break;
                    }
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
                int best = 0;
                double bestD = sqDist(X[i], C[0]);
                for (int k = 1; k < K; k++) {
                    double d2 = sqDist(X[i], C[k]);
                    if (d2 < bestD) {
                        bestD = d2;
                        best = k;
                    }
                }
                if (z[i] != best) {
                    z[i] = best;
                    changed = true;
                }
            }

            // update
            double[][] sum = new double[K][d];
            int[] cnt = new int[K];
            for (int i = 0; i < n; i++) {
                int k = z[i];
                cnt[k]++;
                addInPlace(sum[k], X[i]);
            }
            for (int k = 0; k < K; k++) {
                if (cnt[k] == 0) continue; // keep old centroid if empty
                for (int j = 0; j < d; j++) C[k][j] = sum[k][j] / cnt[k];
            }
            if (!changed) break;
        }
        return new Result(z, C);
    }

    /**
     * Multiple restarts; returns best by within-cluster SSE.
     */
    public static Result clusterWithRestarts(double[][] X, int K, int maxIter, long seed, int restarts) {
        Result best = null;
        double bestSse = Double.POSITIVE_INFINITY;
        Random seeder = new Random(seed);
        for (int r = 0; r < Math.max(1, restarts); r++) {
            long s = seeder.nextLong();
            Result res = cluster(X, K, maxIter, s);
            double sse = withinSSE(X, res.labels, res.centroids);
            if (sse < bestSse) {
                bestSse = sse;
                best = res;
            }
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
        for (int j = 0; j < a.length; j++) {
            double d = a[j] - b[j];
            s += d * d;
        }
        return s;
    }

    private static void addInPlace(double[] a, double[] b) {
        for (int j = 0; j < a.length; j++) a[j] += b[j];
    }

    /**
     * Represents the result of a clustering operation using the KMeans algorithm. The result includes the assignments
     * of data points to clusters (labels) and the centroids of the clusters.
     */
    public static class Result {
        public final int[] labels;
        public final double[][] centroids;

        public Result(int[] labels, double[][] centroids) {
            this.labels = labels;
            this.centroids = centroids;
        }
    }
}
