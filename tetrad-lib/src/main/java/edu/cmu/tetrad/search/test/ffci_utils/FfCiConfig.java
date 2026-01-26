package edu.cmu.tetrad.search.test.ffci_utils;

public record FfCiConfig(
        int numFeatXY,
        int numFeatZ,
        double lambda,
        boolean centerFeatures,
        boolean doRcit,              // RCIT augments Y with Z
        BandwidthPolicy bandwidth,
        FeatureMap featureMapXY,     // typically RFF-RBF
        FeatureMap featureMapZ,
        PValueMethod pValueMethod,
        int permutations,
        long seed,
        boolean cacheFeatures,
        boolean cacheSolvers,
        double alpha,
        PValueMethod approx,
        boolean verbose
) {

    // ============================================================
    // Feature dimensions
    // ============================================================

    public FfCiConfig withNumFeatXY(int d) {
        return new FfCiConfig(
                d, numFeatZ, lambda, centerFeatures, doRcit,
                bandwidth, featureMapXY, featureMapZ,
                pValueMethod, permutations, seed,
                cacheFeatures, cacheSolvers, alpha, approx, verbose
        );
    }

    public FfCiConfig withNumFeatZ(int d) {
        return new FfCiConfig(
                numFeatXY, d, lambda, centerFeatures, doRcit,
                bandwidth, featureMapXY, featureMapZ,
                pValueMethod, permutations, seed,
                cacheFeatures, cacheSolvers, alpha, approx, verbose
        );
    }

    // ============================================================
    // Regularization / centering
    // ============================================================

    public FfCiConfig withLambda(double lambda) {
        return new FfCiConfig(
                numFeatXY, numFeatZ, lambda, centerFeatures, doRcit,
                bandwidth, featureMapXY, featureMapZ,
                pValueMethod, permutations, seed,
                cacheFeatures, cacheSolvers, alpha, approx, verbose
        );
    }

    public FfCiConfig withCenterFeatures(boolean center) {
        return new FfCiConfig(
                numFeatXY, numFeatZ, lambda, center, doRcit,
                bandwidth, featureMapXY, featureMapZ,
                pValueMethod, permutations, seed,
                cacheFeatures, cacheSolvers, alpha, approx, verbose
        );
    }

    // ============================================================
    // RCIT / FF-CI mode
    // ============================================================

    public FfCiConfig withDoRcit(boolean doRcit) {
        return new FfCiConfig(
                numFeatXY, numFeatZ, lambda, centerFeatures, doRcit,
                bandwidth, featureMapXY, featureMapZ,
                pValueMethod, permutations, seed,
                cacheFeatures, cacheSolvers, alpha, approx, verbose
        );
    }

    // ============================================================
    // Bandwidth / feature maps
    // ============================================================

    public FfCiConfig withBandwidth(BandwidthPolicy bandwidth) {
        return new FfCiConfig(
                numFeatXY, numFeatZ, lambda, centerFeatures, doRcit,
                bandwidth, featureMapXY, featureMapZ,
                pValueMethod, permutations, seed,
                cacheFeatures, cacheSolvers, alpha, approx, verbose
        );
    }

    public FfCiConfig withFeatureMapXY(FeatureMap map) {
        return new FfCiConfig(
                numFeatXY, numFeatZ, lambda, centerFeatures, doRcit,
                bandwidth, map, featureMapZ,
                pValueMethod, permutations, seed,
                cacheFeatures, cacheSolvers, alpha, approx, verbose
        );
    }

    public FfCiConfig withFeatureMapZ(FeatureMap map) {
        return new FfCiConfig(
                numFeatXY, numFeatZ, lambda, centerFeatures, doRcit,
                bandwidth, featureMapXY, map,
                pValueMethod, permutations, seed,
                cacheFeatures, cacheSolvers, alpha, approx, verbose
        );
    }

    // ============================================================
    // P-value computation
    // ============================================================

    public FfCiConfig withPValueMethod(PValueMethod method) {
        return new FfCiConfig(
                numFeatXY, numFeatZ, lambda, centerFeatures, doRcit,
                bandwidth, featureMapXY, featureMapZ,
                method, permutations, seed,
                cacheFeatures, cacheSolvers, alpha, approx, verbose
        );
    }

    public FfCiConfig withApprox(PValueMethod approx) {
        return new FfCiConfig(
                numFeatXY, numFeatZ, lambda, centerFeatures, doRcit,
                bandwidth, featureMapXY, featureMapZ,
                pValueMethod, permutations, seed,
                cacheFeatures, cacheSolvers, alpha, approx, verbose
        );
    }

    public FfCiConfig withPermutations(int permutations) {
        return new FfCiConfig(
                numFeatXY, numFeatZ, lambda, centerFeatures, doRcit,
                bandwidth, featureMapXY, featureMapZ,
                pValueMethod, permutations, seed,
                cacheFeatures, cacheSolvers, alpha, approx, verbose
        );
    }

    // ============================================================
    // Misc / infrastructure
    // ============================================================

    public FfCiConfig withSeed(long seed) {
        return new FfCiConfig(
                numFeatXY, numFeatZ, lambda, centerFeatures, doRcit,
                bandwidth, featureMapXY, featureMapZ,
                pValueMethod, permutations, seed,
                cacheFeatures, cacheSolvers, alpha, approx, verbose
        );
    }

    public FfCiConfig withCacheFeatures(boolean cache) {
        return new FfCiConfig(
                numFeatXY, numFeatZ, lambda, centerFeatures, doRcit,
                bandwidth, featureMapXY, featureMapZ,
                pValueMethod, permutations, seed,
                cache, cacheSolvers, alpha, approx, verbose
        );
    }

    public FfCiConfig withCacheSolvers(boolean cache) {
        return new FfCiConfig(
                numFeatXY, numFeatZ, lambda, centerFeatures, doRcit,
                bandwidth, featureMapXY, featureMapZ,
                pValueMethod, permutations, seed,
                cacheFeatures, cache, alpha, approx, verbose
        );
    }

    public FfCiConfig withAlpha(double alpha) {
        return new FfCiConfig(
                numFeatXY, numFeatZ, lambda, centerFeatures, doRcit,
                bandwidth, featureMapXY, featureMapZ,
                pValueMethod, permutations, seed,
                cacheFeatures, cacheSolvers, alpha, approx, verbose
        );
    }

    public FfCiConfig withVerbose(boolean verbose) {
        return new FfCiConfig(
                numFeatXY, numFeatZ, lambda, centerFeatures, doRcit,
                bandwidth, featureMapXY, featureMapZ,
                pValueMethod, permutations, seed,
                cacheFeatures, cacheSolvers, alpha, approx, verbose
        );
    }
}