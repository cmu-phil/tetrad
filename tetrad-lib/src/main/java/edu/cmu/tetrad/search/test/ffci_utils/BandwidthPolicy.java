package edu.cmu.tetrad.search.test.ffci_utils;

import org.ejml.simple.SimpleMatrix;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bandwidth (sigma) policies for RBF-based feature maps (RFF/ORF).
 *
 * Conventions:
 *  - input rawStd is n×d and is already standardized (z-scored) by the engine
 *  - returned sigma must be > 0 and finite
 *  - policies should be deterministic given (rawStd, ctx, state) unless explicitly randomized
 *
 * Caching:
 *  - use state.sigmaCache with a key that depends on: policy.id(), ctx.id(), nActive, rowsHash
 *    (and any policy knobs such as maxRows/maxPairs/multiplier)
 */
public interface BandwidthPolicy {

    double sigma(SimpleMatrix rawStd, BandwidthContext ctx, FfCiState state);

    /** Stable identifier for caching keys. */
    String id();

    // ============================================================
    // Implementations
    // ============================================================

    /**
     * Median pairwise Euclidean distance computed on up to maxRows rows
     * and up to maxPairs sampled pairs, then scaled by multiplier.
     *
     * This matches the spirit of causal-learn's RCIT: use the first min(n, 500)
     * rows for bandwidth estimation and the median of pairwise distances.
     *
     * NOTE: This implementation samples pairs (fast) instead of enumerating all pairs.
     */
    final class MedianHeuristic implements BandwidthPolicy {
        private final int maxRows;        // e.g., 500
        private final int maxPairs;       // e.g., 5000
        private final double multiplier;  // e.g., 1.0
        private final long sampleSeed;    // deterministic sampling seed (not cfg.seed)

        public MedianHeuristic(int maxRows, int maxPairs, double multiplier) {
            this(maxRows, maxPairs, multiplier, 12345L);
        }

        public MedianHeuristic(int maxRows, int maxPairs, double multiplier, long sampleSeed) {
            this.maxRows = Math.max(2, maxRows);
            this.maxPairs = Math.max(1, maxPairs);
            this.multiplier = (Double.isFinite(multiplier) && multiplier > 0) ? multiplier : 1.0;
            this.sampleSeed = sampleSeed;
        }

        @Override
        public double sigma(SimpleMatrix rawStd, BandwidthContext ctx, FfCiState state) {
            Objects.requireNonNull(rawStd, "rawStd");
            Objects.requireNonNull(ctx, "ctx");
            Objects.requireNonNull(state, "state");

            // If d==0 (empty conditioning set etc.), return 1.0 (unused anyway)
            if (rawStd.getNumCols() == 0 || rawStd.getNumRows() < 2) return 1.0;

            // Cache key
            String key = key(state, ctx);
            Double cached = state.sigmaCache.get(key);
            if (cached != null) return cached;

            int n = rawStd.getNumRows();
            int r = Math.min(n, maxRows);

            SimpleMatrix A = rawStd.rows(0, r); // first r rows (fast view in EJML SimpleMatrix)
            double sig = medianPairwiseDistanceSampled(A, maxPairs, new Random(sampleSeed));

            sig *= multiplier;
            sig = sanitizeSigma(sig);

            state.sigmaCache.putIfAbsent(key, sig);
            return sig;
        }

        @Override
        public String id() {
            return "medianHeuristic|maxRows=" + maxRows
                    + "|maxPairs=" + maxPairs
                    + "|mult=" + Double.doubleToLongBits(multiplier)
                    + "|seed=" + sampleSeed;
        }

        private String key(FfCiState state, BandwidthContext ctx) {
            // Include policy id + ctx id + active rows hash + nActive.
            // (If ctx.id already includes variable names and tags, that's enough.)
            return "bw|" + id()
                    + "|ctx=" + ctx.id()
                    + "|n=" + state.rowsView.nActive()
                    + "|rows=" + state.rowsView.rowsHash();
        }
    }

    /**
     * Fixed sigma (useful for debugging / controlled experiments).
     */
    final class Fixed implements BandwidthPolicy {
        private final double sigma;

        public Fixed(double sigma) {
            this.sigma = sanitizeSigma(sigma);
        }

        @Override
        public double sigma(SimpleMatrix rawStd, BandwidthContext ctx, FfCiState state) {
            return sigma;
        }

        @Override
        public String id() {
            return "fixed|" + Double.doubleToLongBits(sigma);
        }
    }

    /**
     * Multiply another policy's sigma by a constant.
     */
    final class Scaled implements BandwidthPolicy {
        private final BandwidthPolicy base;
        private final double multiplier;

        public Scaled(BandwidthPolicy base, double multiplier) {
            this.base = Objects.requireNonNull(base, "base");
            this.multiplier = (Double.isFinite(multiplier) && multiplier > 0) ? multiplier : 1.0;
        }

        @Override
        public double sigma(SimpleMatrix rawStd, BandwidthContext ctx, FfCiState state) {
            return sanitizeSigma(base.sigma(rawStd, ctx, state) * multiplier);
        }

        @Override
        public String id() {
            return "scaled|" + base.id() + "|mult=" + Double.doubleToLongBits(multiplier);
        }
    }

    // ============================================================
    // Helpers (private static in interface is ok in modern Java)
    // ============================================================

    private static double sanitizeSigma(double v) {
        if (!Double.isFinite(v) || v <= 0) return 1.0;
        // guard against absurdly tiny values that make W explode
        if (v < 1e-12) return 1e-12;
        return v;
    }

    /**
     * Median of sampled pairwise Euclidean distances.
     * Samples up to maxPairs pairs uniformly (with replacement) from rows.
     */
    private static double medianPairwiseDistanceSampled(SimpleMatrix A, int maxPairs, Random rng) {
        int n = A.getNumRows();
        int d = A.getNumCols();
        if (n <= 1 || d == 0) return 1.0;

        long allPairs = (long) n * (n - 1) / 2;
        int pairs = (int) Math.min((long) maxPairs, allPairs);
        if (pairs <= 0) return 1.0;

        double[] dist = new double[pairs];
        int filled = 0;

        for (int t = 0; t < pairs; t++) {
            int i = rng.nextInt(n);
            int j = rng.nextInt(n - 1);
            if (j >= i) j++;

            double ss = 0.0;
            for (int k = 0; k < d; k++) {
                double diff = A.get(i, k) - A.get(j, k);
                ss += diff * diff;
            }
            double dd = Math.sqrt(ss);
            if (dd > 0 && Double.isFinite(dd)) dist[filled++] = dd;
        }

        if (filled == 0) return 1.0;

        Arrays.sort(dist, 0, filled);
        int m = filled;
        return (m % 2 == 1) ? dist[m / 2] : 0.5 * (dist[m / 2 - 1] + dist[m / 2]);
    }
}