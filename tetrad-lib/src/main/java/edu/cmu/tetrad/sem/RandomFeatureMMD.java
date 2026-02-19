package edu.cmu.tetrad.sem;

import java.util.Random;

/**
 * Random Fourier feature approximation to RBF-kernel MMD^2 using the UNBIASED estimator.
 *
 * Steps:
 *  1) Build RFF map phi(x) in R^D for an RBF kernel with bandwidth sigma.
 *  2) Compute unbiased MMD^2 in feature space:
 *
 *     MMD^2 = 1/(n(n-1)) sum_{i!=j} <phi(x_i),phi(x_j)>
 *          + 1/(m(m-1)) sum_{i!=j} <phi(y_i),phi(y_j)>
 *          - 2/(nm)     sum_{i,j}  <phi(x_i),phi(y_j)>
 *
 * Notes:
 *  - If sigma<=0, defaults to 1.0.
 *  - If maxRows>0, deterministically subsamples without replacement from each dataset.
 *  - Use z-scored columns (based on real) for best behavior; then sigma≈1 is often fine.
 */
final class RandomFeatureMMD{

    private RandomFeatureMMD() {}

    static double compute(double[][] X,
                          double[][] Y,
                          int D,
                          long seed,
                          double sigma,
                          int maxRows) {

        if (X == null || Y == null) throw new NullPointerException("X/Y");
        if (D <= 0) throw new IllegalArgumentException("D must be > 0");
        if (X.length < 2 || Y.length < 2) throw new IllegalArgumentException("Need >=2 rows in each sample");
        if (X[0] == null || Y[0] == null) throw new NullPointerException("X[0]/Y[0]");
        int dim = X[0].length;
        if (dim <= 0) throw new IllegalArgumentException("dim must be > 0");

        int[] xIdx = (maxRows > 0 && X.length > maxRows)
                ? sampleWithoutReplacement(X.length, maxRows, seed ^ 0xA5A5A5A5A5A5A5A5L)
                : null;
        int[] yIdx = (maxRows > 0 && Y.length > maxRows)
                ? sampleWithoutReplacement(Y.length, maxRows, seed ^ 0x5A5A5A5A5A5A5A5AL)
                : null;

        int n = (xIdx == null) ? X.length : xIdx.length;
        int m = (yIdx == null) ? Y.length : yIdx.length;

        double sig = (sigma > 0.0) ? sigma : 1.0;
        double wScale = 1.0 / sig;

        Random rng = new Random(seed);

        // RFF params for RBF kernel
        double[][] W = new double[D][dim];
        double[] b = new double[D];
        for (int k = 0; k < D; k++) {
            for (int j = 0; j < dim; j++) W[k][j] = rng.nextGaussian() * wScale;
            b[k] = 2.0 * Math.PI * rng.nextDouble();
        }

        // phi(x) = sqrt(2/D) cos(Wx + b)
        double phiScale = Math.sqrt(2.0 / (double) D);

        // Compute feature matrices implicitly via dot products accumulation.
        // We'll build Zx: n x D and Zy: m x D (memory is fine for n<=2000, D<=1024).
        double[][] Zx = new double[n][D];
        double[][] Zy = new double[m][D];

        for (int ii = 0; ii < n; ii++) {
            int i = (xIdx == null) ? ii : xIdx[ii];
            double[] row = X[i];
            if (row == null || row.length != dim) throw new IllegalArgumentException("X row dim mismatch at " + i);
            double[] out = Zx[ii];
            for (int k = 0; k < D; k++) {
                double dot = 0.0;
                double[] wk = W[k];
                for (int j = 0; j < dim; j++) dot += wk[j] * row[j];
                out[k] = phiScale * Math.cos(dot + b[k]);
            }
        }

        for (int ii = 0; ii < m; ii++) {
            int i = (yIdx == null) ? ii : yIdx[ii];
            double[] row = Y[i];
            if (row == null || row.length != dim) throw new IllegalArgumentException("Y row dim mismatch at " + i);
            double[] out = Zy[ii];
            for (int k = 0; k < D; k++) {
                double dot = 0.0;
                double[] wk = W[k];
                for (int j = 0; j < dim; j++) dot += wk[j] * row[j];
                out[k] = phiScale * Math.cos(dot + b[k]);
            }
        }

        // Helper: dot product in feature space
        // (could micro-opt, but D<=1024 makes it fine)
        double sumXX = 0.0;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                sumXX += dot(Zx[i], Zx[j]);
            }
        }
        sumXX *= 2.0 / (n * (n - 1.0)); // average over i!=j

        double sumYY = 0.0;
        for (int i = 0; i < m; i++) {
            for (int j = i + 1; j < m; j++) {
                sumYY += dot(Zy[i], Zy[j]);
            }
        }
        sumYY *= 2.0 / (m * (m - 1.0));

        double sumXY = 0.0;
        for (int i = 0; i < n; i++) {
            double[] zi = Zx[i];
            for (int j = 0; j < m; j++) {
                sumXY += dot(zi, Zy[j]);
            }
        }
        sumXY *= 2.0 / (n * (double) m);

        double mmd2 = sumXX + sumYY - sumXY;

        // Numerical guard: due to approximation, tiny negatives can happen.
        return Math.max(0.0, mmd2);
    }

    private static double dot(double[] a, double[] b) {
        double s = 0.0;
        for (int i = 0; i < a.length; i++) s += a[i] * b[i];
        return s;
    }

    private static int[] sampleWithoutReplacement(int N, int K, long seed) {
        if (K >= N) {
            int[] all = new int[N];
            for (int i = 0; i < N; i++) all[i] = i;
            return all;
        }

        int[] idx = new int[N];
        for (int i = 0; i < N; i++) idx[i] = i;

        Random rng = new Random(seed);
        for (int i = 0; i < K; i++) {
            int j = i + rng.nextInt(N - i);
            int t = idx[i];
            idx[i] = idx[j];
            idx[j] = t;
        }

        int[] out = new int[K];
        System.arraycopy(idx, 0, out, 0, K);
        return out;
    }
}