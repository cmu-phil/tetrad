package edu.cmu.tetrad.search.test.ffci_utils;

/**
 * Canonical preset configurations for RCIT.
 * <p>
 * This class exists to:
 * - encode the *author-intended* RCIT specification in one place
 * - avoid accidental drift from the published method
 * - provide safe, named starting points for experimentation
 * <p>
 * All returned configs are immutable.
 */
public final class RcitPresets {

    private RcitPresets() {
        // no instances
    }

    /**
     * RCIT as specified by the authors (baseline).
     * <p>
     * Characteristics:
     * - RCIT mode enabled (Y augmented with Z)
     * - Random Fourier Features (not ORF)
     * - Same feature count for XY and Z
     * - Ridge regularization on Czz
     * - Analytic quadratic-form p-values (Davies by default)
     * - No permutations
     */
    public static FfCiConfig authorSpec() {
        return builder()
                .doRcit(true)

                // Feature maps
                .featureMapXY(FeatureMaps.RFF_RBF)
                .featureMapZ(FeatureMaps.RFF_RBF)

                // Feature counts (typical RCIT defaults)
                .numFeatXY(500)
                .numFeatZ(500)

                // Regularization
                .lambda(1e-3)

                // Feature centering (important for moment approximations)
                .centerFeatures(true)

                // P-value approximation
                .approx(PValueMethod.DAVIES_IMHOF)
                .permutations(0)

                // Determinism
                .seed(1L)

                .bandwidth(new BandwidthPolicy.MedianHeuristic(500, 1000, 1.0))

                // Verbosity off by default
                .verbose(false)

                .build();
    }

    /**
     * RCIT with permutation p-values.
     * <p>
     * Useful for calibration checks and debugging.
     * Much slower but very robust.
     */
    public static FfCiConfig withPermutations(int permutations) {
        return authorSpec()
                .withApprox(PValueMethod.PERMUTATION)
                .withPermutations(permutations);
    }

    /**
     * Fast RCIT variant for development and large graphs.
     * <p>
     * Uses fewer features and a cheaper approximation.
     */
    public static FfCiConfig fastApprox() {
        return authorSpec()
                .withNumFeatXY(200)
                .withNumFeatZ(200)
                .withApprox(PValueMethod.GAMMA_SATTERTHWAITE);
    }

    /**
     * RCIT configured to behave as close as possible to FF-CI,
     * but still using conditional residualization.
     * <p>
     * This is mainly useful for comparison experiments.
     */
    public static FfCiConfig ffciLike() {
        return authorSpec()
                .withDoRcit(false);
    }

    // ============================================================
// Builder
// ============================================================

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int numFeatXY;
        private int numFeatZ;
        private double lambda;
        private boolean centerFeatures;
        private boolean doRcit;
        private BandwidthPolicy bandwidth;
        private FeatureMap featureMapXY;
        private FeatureMap featureMapZ;
        private PValueMethod pValueMethod;
        private int permutations;
        private long seed;
        private boolean cacheFeatures;
        private boolean cacheSolvers;
        private double alpha;
        private PValueMethod approx;
        private boolean verbose;

        private Builder() {
            // intentionally empty — caller must initialize explicitly
            // (presets will do this)
        }

        // ---------------- feature dimensions ----------------

        public Builder numFeatXY(int v) {
            this.numFeatXY = v;
            return this;
        }

        public Builder numFeatZ(int v) {
            this.numFeatZ = v;
            return this;
        }

        // ---------------- regularization / centering ----------------

        public Builder lambda(double v) {
            this.lambda = v;
            return this;
        }

        public Builder centerFeatures(boolean v) {
            this.centerFeatures = v;
            return this;
        }

        // ---------------- RCIT / FF-CI mode ----------------

        public Builder doRcit(boolean v) {
            this.doRcit = v;
            return this;
        }

        // ---------------- bandwidth / features ----------------

        public Builder bandwidth(BandwidthPolicy v) {
            this.bandwidth = v;
            return this;
        }

        public Builder featureMapXY(FeatureMap v) {
            this.featureMapXY = v;
            return this;
        }

        public Builder featureMapZ(FeatureMap v) {
            this.featureMapZ = v;
            return this;
        }

        // ---------------- p-values ----------------

        public Builder pValueMethod(PValueMethod v) {
            this.pValueMethod = v;
            return this;
        }

        public Builder approx(PValueMethod v) {
            this.approx = v;
            return this;
        }

        public Builder permutations(int v) {
            this.permutations = v;
            return this;
        }

        // ---------------- infra ----------------

        public Builder seed(long v) {
            this.seed = v;
            return this;
        }

        public Builder cacheFeatures(boolean v) {
            this.cacheFeatures = v;
            return this;
        }

        public Builder cacheSolvers(boolean v) {
            this.cacheSolvers = v;
            return this;
        }

        public Builder alpha(double v) {
            this.alpha = v;
            return this;
        }

        public Builder verbose(boolean v) {
            this.verbose = v;
            return this;
        }

        // ---------------- build ----------------

        public FfCiConfig build() {
            return new FfCiConfig(
                    numFeatXY,
                    numFeatZ,
                    lambda,
                    centerFeatures,
                    doRcit,
                    bandwidth,
                    featureMapXY,
                    featureMapZ,
                    pValueMethod,
                    permutations,
                    seed,
                    cacheFeatures,
                    cacheSolvers,
                    alpha,
                    approx,
                    verbose
            );
        }

        public FfCiConfig withSeed(long seed) {
            return new FfCiConfig(
                    numFeatXY,
                    numFeatZ,
                    lambda,
                    centerFeatures,
                    doRcit,
                    bandwidth,
                    featureMapXY,
                    featureMapZ,
                    pValueMethod,
                    permutations,
                    seed,
                    cacheFeatures,
                    cacheSolvers,
                    alpha,
                    approx,
                    verbose
            );
        }
    }
}