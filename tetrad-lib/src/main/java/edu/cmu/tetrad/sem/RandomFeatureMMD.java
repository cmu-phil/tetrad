package edu.cmu.tetrad.sem;

import java.util.Arrays;
import java.util.Random;

/**
 * Random Fourier feature approximation to RBF-kernel MMD^2 with a FAST unbiased estimator.
 *
 * Compared to the classic O(n^2) U-statistic loops, this uses identities in feature space:
 *
 *   Let z_i = phi(x_i) in R^D, w_j = phi(y_j).
 *
 *   (1) 1/(n(n-1)) sum_{i!=j} z_i·z_j
 *       = (||sum_i z_i||^2 - sum_i ||z_i||^2) / (n(n-1))
 *
 *   (2) 1/(m(m-1)) sum_{i!=j} w_i·w_j
 *       = (||sum_j w_j||^2 - sum_j ||w_j||^2) / (m(m-1))
 *
 *   (3) 1/(nm) sum_{i,j} z_i·w_j
 *       = (sum_i z_i) · (sum_j w_j) / (nm)
 *
 * Then:
 *   MMD^2_unbiased = termXX + termYY - 2*termXY
 *
 * Stability knobs:
 *  - replicate averaging (replicates > 1)
 *  - sigma median heuristic (sigma <= 0)
 *  - multi-sigma mixture (sigmaMultipliers)
 *  - deterministic row subsampling (maxRows)
 *
 * Notes:
 *  - If X and Y are already z-scored, median-heuristic sigma usually behaves well.
 *  - This returns max(0, mmd2) as a numerical guard.
 */
final class RandomFeatureMMD {

    private RandomFeatureMMD() {}

    // ----------------------------------------------------------------------
    // Backward-compatible entry point (keeps your original signature).
    // Uses: 1 replicate, single sigma (or 1.0 if sigma<=0), no multi-sigma mixture.
    // ----------------------------------------------------------------------
    static double compute(double[][] X,
                          double[][] Y,
                          int D,
                          long seed,
                          double sigma,
                          int maxRows) {

        // Preserve the old behavior where sigma<=0 -> 1.0
        final double sig = (sigma > 0.0) ? sigma : 1.0;

        return computeStable(
                X, Y,
                D,
                seed,
                sig,              // fixed sigma
                null,             // no mixture
                1,                // replicates
                maxRows,
                false,            // do not median-estimate
                0                 // (ignored)
        );
    }

    // ----------------------------------------------------------------------
    // Recommended stable scoring entry point.
    //
    // If sigma<=0 and useMedianHeuristic=true, sigma is estimated from pooled data.
    // If sigmaMultipliers != null, uses mixture of sigmas: sigma * multiplier[k].
    // If replicates>1, averages over independent RFF draws (different W,b).
    // ----------------------------------------------------------------------
    static double computeStable(double[][] X,
                                double[][] Y,
                                int D,
                                long seed,
                                double sigma,
                                double[] sigmaMultipliers,
                                int replicates,
                                int maxRows,
                                boolean useMedianHeuristic,
                                int medianPairs) {

        validateXY(X, Y, D);

        final int dim = X[0].length;

        // Deterministic subsampling indices (reused across all replicates/sigmas).
        final int[] xIdx = (maxRows > 0 && X.length > maxRows)
                ? sampleWithoutReplacement(X.length, maxRows, seed ^ 0xA5A5A5A5A5A5A5A5L)
                : null;

        final int[] yIdx = (maxRows > 0 && Y.length > maxRows)
                ? sampleWithoutReplacement(Y.length, maxRows, seed ^ 0x5A5A5A5A5A5A5A5AL)
                : null;

        final int n = (xIdx == null) ? X.length : xIdx.length;
        final int m = (yIdx == null) ? Y.length : yIdx.length;

        if (n < 2 || m < 2) throw new IllegalArgumentException("Need >=2 rows in each sample after subsampling.");

        // Choose base sigma:
        double baseSigma = sigma;

        if (useMedianHeuristic && !(baseSigma > 0.0)) {
            final int pairs = (medianPairs > 0) ? medianPairs : 4000; // decent default
            baseSigma = medianHeuristicSigma(X, Y, xIdx, yIdx, seed ^ 0xC0FFEE1234ABCDL, pairs);
        }

        if (!(baseSigma > 0.0)) {
            // Fallback if user passes sigma<=0 and disables heuristic.
            baseSigma = 1.0;
        }

        // Sigma list:
        final double[] sigmas;
        if (sigmaMultipliers != null && sigmaMultipliers.length > 0) {
            sigmas = new double[sigmaMultipliers.length];
            for (int i = 0; i < sigmaMultipliers.length; i++) {
                double mult = sigmaMultipliers[i];
                if (!(mult > 0.0) || !Double.isFinite(mult)) {
                    throw new IllegalArgumentException("sigmaMultipliers must be finite and > 0");
                }
                sigmas[i] = baseSigma * mult;
            }
        } else {
            sigmas = new double[] { baseSigma };
        }

        final int R = Math.max(1, replicates);

        // Accumulate over replicates and (optionally) over sigma mixture.
        double acc = 0.0;
        int count = 0;

        for (int r = 0; r < R; r++) {

            // Different W,b per replicate; deterministic sequence from seed.
            final long repSeed = mixSeed(seed, r);

            // Generate base (unit) Gaussian W0 and uniform b once per replicate.
            // For each sigma: W = W0 / sigma.
            final Random rng = new Random(repSeed);
            final double[][] W0 = new double[D][dim];
            final double[] b = new double[D];

            for (int k = 0; k < D; k++) {
                for (int j = 0; j < dim; j++) {
                    W0[k][j] = rng.nextGaussian(); // unit
                }
                b[k] = 2.0 * Math.PI * rng.nextDouble();
            }

            for (double sig : sigmas) {
                acc += computeOnceLinearUnbiased(X, Y, xIdx, yIdx, W0, b, sig);
                count++;
            }
        }

        final double mmd2 = (count > 0) ? (acc / (double) count) : Double.NaN;
        return Math.max(0.0, mmd2);
    }

    // Convenience “good defaults” for your regime (n≈1000, dim≈10):
    // - median heuristic sigma
    // - 3-sigma mixture
    // - 5 replicates
    // - keep maxRows as supplied
    static double computeStableDefaults(double[][] X,
                                        double[][] Y,
                                        int D,
                                        long seed,
                                        int maxRows) {
        return computeStable(
                X, Y,
                D,
                seed,
                -1.0,                         // sigma<=0 => estimate
                new double[] {0.5, 1.0, 2.0}, // mixture
                5,                            // replicates
                maxRows,
                true,                         // median heuristic
                4000                          // median pairs
        );
    }

    // ----------------------------------------------------------------------
    // Core: linear-time unbiased MMD^2 in feature space for one replicate and one sigma.
    // ----------------------------------------------------------------------
    private static double computeOnceLinearUnbiased(double[][] X,
                                                    double[][] Y,
                                                    int[] xIdx,
                                                    int[] yIdx,
                                                    double[][] W0,
                                                    double[] b,
                                                    double sigma) {

        final int n = (xIdx == null) ? X.length : xIdx.length;
        final int m = (yIdx == null) ? Y.length : yIdx.length;

        final int D = W0.length;
        final int dim = W0[0].length;

        // phi(x) = sqrt(2/D) cos((W0/sigma) x + b)
        final double phiScale = Math.sqrt(2.0 / (double) D);
        final double wScale = 1.0 / sigma;

        final double[] sumX = new double[D];
        final double[] sumY = new double[D];

        double sumNormSqX = 0.0;
        double sumNormSqY = 0.0;

        // Build features row-by-row (no Z matrices), accumulate sums and norm squares.
        final double[] z = new double[D];

        // X
        for (int ii = 0; ii < n; ii++) {
            final int i = (xIdx == null) ? ii : xIdx[ii];
            final double[] row = X[i];
            if (row == null || row.length != dim) throw new IllegalArgumentException("X row dim mismatch at " + i);

            fillPhi(z, row, W0, b, phiScale, wScale);

            double ns = 0.0;
            for (int k = 0; k < D; k++) {
                double v = z[k];
                sumX[k] += v;
                ns += v * v;
            }
            sumNormSqX += ns;
        }

        // Y
        for (int ii = 0; ii < m; ii++) {
            final int i = (yIdx == null) ? ii : yIdx[ii];
            final double[] row = Y[i];
            if (row == null || row.length != dim) throw new IllegalArgumentException("Y row dim mismatch at " + i);

            fillPhi(z, row, W0, b, phiScale, wScale);

            double ns = 0.0;
            for (int k = 0; k < D; k++) {
                double v = z[k];
                sumY[k] += v;
                ns += v * v;
            }
            sumNormSqY += ns;
        }

        // ||sum||^2
        final double normSumX2 = dot(sumX, sumX);
        final double normSumY2 = dot(sumY, sumY);

        // cross (sumX · sumY)
        final double cross = dot(sumX, sumY);

        // Unbiased within terms:
        final double termXX = (normSumX2 - sumNormSqX) / (n * (n - 1.0));
        final double termYY = (normSumY2 - sumNormSqY) / (m * (m - 1.0));

        // Cross mean:
        final double termXY = cross / (n * (double) m);

        // MMD^2 = EXX + EYY - 2 EXY
        final double mmd2 = termXX + termYY - 2.0 * termXY;

        // Numerical guard
        return Math.max(0.0, mmd2);
    }

    private static void fillPhi(double[] out,
                                double[] x,
                                double[][] W0,
                                double[] b,
                                double phiScale,
                                double wScale) {

        final int D = W0.length;
        final int dim = x.length;

        for (int k = 0; k < D; k++) {
            final double[] wk = W0[k];
            double dot = 0.0;
            for (int j = 0; j < dim; j++) dot += (wk[j] * wScale) * x[j];
            out[k] = phiScale * Math.cos(dot + b[k]);
        }
    }

    // ----------------------------------------------------------------------
    // Median heuristic sigma (deterministic).
    // We estimate median of squared distances over random pairs from pooled (subsampled) data.
    // Then set sigma^2 = medianSq / 2  => sigma = sqrt(medianSq/2).
    // ----------------------------------------------------------------------
    private static double medianHeuristicSigma(double[][] X,
                                               double[][] Y,
                                               int[] xIdx,
                                               int[] yIdx,
                                               long seed,
                                               int pairs) {

        final int dim = X[0].length;

        // Build pooled index view without copying rows:
        final int n = (xIdx == null) ? X.length : xIdx.length;
        final int m = (yIdx == null) ? Y.length : yIdx.length;

        final int N = n + m;
        if (N < 2) return 1.0;

        final Random rng = new Random(seed);
        final int P = Math.max(1000, pairs);

        double[] d2 = new double[P];

        for (int t = 0; t < P; t++) {
            int a = rng.nextInt(N);
            int b = rng.nextInt(N - 1);
            if (b >= a) b++;

            double[] ra = pooledRow(X, Y, xIdx, yIdx, a, n);
            double[] rb = pooledRow(X, Y, xIdx, yIdx, b, n);

            double s = 0.0;
            for (int j = 0; j < dim; j++) {
                double d = ra[j] - rb[j];
                s += d * d;
            }
            d2[t] = s;
        }

        Arrays.sort(d2);
        double medianSq = d2[P / 2];

        // Guard against degenerate case:
        if (!(medianSq > 0.0) || !Double.isFinite(medianSq)) return 1.0;

        double sigma2 = medianSq / 2.0;
        double sigma = Math.sqrt(sigma2);

        if (!(sigma > 0.0) || !Double.isFinite(sigma)) return 1.0;
        return sigma;
    }

    private static double[] pooledRow(double[][] X,
                                      double[][] Y,
                                      int[] xIdx,
                                      int[] yIdx,
                                      int pooledIndex,
                                      int nX) {
        if (pooledIndex < nX) {
            int i = (xIdx == null) ? pooledIndex : xIdx[pooledIndex];
            return X[i];
        } else {
            int j0 = pooledIndex - nX;
            int j = (yIdx == null) ? j0 : yIdx[j0];
            return Y[j];
        }
    }

    // ----------------------------------------------------------------------
    // Utilities
    // ----------------------------------------------------------------------
    private static void validateXY(double[][] X, double[][] Y, int D) {
        if (X == null || Y == null) throw new NullPointerException("X/Y");
        if (D <= 0) throw new IllegalArgumentException("D must be > 0");
        if (X.length < 2 || Y.length < 2) throw new IllegalArgumentException("Need >=2 rows in each sample");
        if (X[0] == null || Y[0] == null) throw new NullPointerException("X[0]/Y[0]");
        int dim = X[0].length;
        if (dim <= 0) throw new IllegalArgumentException("dim must be > 0");
        for (int i = 1; i < X.length; i++) {
            if (X[i] == null || X[i].length != dim) throw new IllegalArgumentException("X row dim mismatch at " + i);
        }
        for (int i = 1; i < Y.length; i++) {
            if (Y[i] == null || Y[i].length != dim) throw new IllegalArgumentException("Y row dim mismatch at " + i);
        }
    }

    private static double dot(double[] a, double[] b) {
        double s = 0.0;
        for (int i = 0; i < a.length; i++) s += a[i] * b[i];
        return s;
    }

    private static long mixSeed(long seed, int r) {
        // A simple, deterministic mixer for replicate seeds.
        long z = seed + 0x9E3779B97F4A7C15L * (r + 1L);
        z ^= (z >>> 33);
        z *= 0xff51afd7ed558ccdL;
        z ^= (z >>> 33);
        z *= 0xc4ceb9fe1a85ec53L;
        z ^= (z >>> 33);
        return z;
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

        return Arrays.copyOf(idx, K);
    }
}