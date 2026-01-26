package edu.cmu.tetrad.search.test.ffci_utils;

/**
 * Canonical preset configurations for FF-CI.
 *
 * Same infrastructure as RCIT presets, but FF-CI mode:
 *  - doRcit(false) so Y is NOT augmented with Z.
 */
public final class FfCiPresets {

    private FfCiPresets() { }

    /**
     * FF-CI baseline (author-style defaults; tuned to be comparable to RCIT baseline).
     */
    public static FfCiConfig authorSpec() {
        return RcitPresets.authorSpec()
                .withDoRcit(false);
    }

    /** FF-CI with permutation p-values (robust, slower). */
    public static FfCiConfig withPermutations(int permutations) {
        return authorSpec()
                .withApprox(PValueMethod.PERMUTATION)
                .withPermutations(permutations);
    }

    /** Fast FF-CI for dev / large graphs. */
    public static FfCiConfig fastApprox() {
        return authorSpec()
                .withNumFeatXY(200)
                .withNumFeatZ(200)
                .withApprox(PValueMethod.GAMMA_SATTERTHWAITE);
    }
}