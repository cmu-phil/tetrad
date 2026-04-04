package edu.cmu.tetrad.search.utils;

/**
 * Configuration for minimax-style quantile-grid binning used to approximate conditioning on Z.
 *
 * @param binsPerDim number of quantile bins per conditioning dimension (>= 2)
 * @param minBinSize minimum number of samples required for a bin to be used (>= 3)
 */
public record MinimaxBinningConfig(int binsPerDim, int minBinSize) {

    /**
     * Constructs a configuration for minimax-style quantile-grid binning, enforcing validity of parameters.
     *
     * @param binsPerDim the number of quantile bins per conditioning dimension. Must be greater than or equal to 2.
     * @param minBinSize the minimum number of samples required for a bin to be valid. Must be greater than or equal to 3.
     * @throws IllegalArgumentException if binsPerDim is less than 2 or minBinSize is less than 3.
     */
    public MinimaxBinningConfig {
        if (binsPerDim < 2) throw new IllegalArgumentException("binsPerDim must be >= 2");
        if (minBinSize < 3) throw new IllegalArgumentException("minBinSize must be >= 3");
    }
}