package edu.cmu.tetrad.search.utils;

/**
 * Configuration for minimax-style quantile-grid binning used to approximate conditioning on Z.
 *
 * @param binsPerDim number of quantile bins per conditioning dimension (>= 2)
 * @param minBinSize minimum number of samples required for a bin to be used (>= 3)
 */
public record MinimaxBinningConfig(int binsPerDim, int minBinSize) {

    public MinimaxBinningConfig {
        if (binsPerDim < 2) throw new IllegalArgumentException("binsPerDim must be >= 2");
        if (minBinSize < 3) throw new IllegalArgumentException("minBinSize must be >= 3");
    }
}