package edu.cmu.tetrad.algcomparison.algorithm.other;

import edu.cmu.tetrad.util.Parameters;

/**
 * Thin wrapper over Parameters to keep keys centralized and discoverable.
 */
public record RlcdParams(
        double alpha,
        String stage1Method,     // e.g. "all" (mirror Python CLI), or your chosen options
        int maxSamples,          // -1 => all
        String rankTestMethod,   // "svd" (built-in) or "wilks" (hook to RankTests)
        double svdTau,           // numerical threshold for rank (singular value cutoff)
        boolean useGin           // orient leftover edges with GIN
) {
    /**
     * Constructs an instance of RlcdParams using the provided Parameters object.
     *
     * @param ps the Parameters object containing configuration values. Keys and their default values include:
     *           - "alpha": a double value with a default of 0.05.
     *           - "rlcd.stage1_method": a string specifying the stage 1 method, defaulting to "all".
     *           - "rlcd.max_samples": an integer representing the maximum number of samples, defaulting to -1 (interpreted as all samples).
     *           - "rlcd.rank_test": a string specifying the rank test method, defaulting to "svd".
     *           - "rlcd.svd_tau": a double numerical threshold for singular value decomposition, defaulting to 1e-7.
     *           - "rlcd.use_gin": a boolean indicating whether to orient leftover edges using GIN, defaulting to false.
     */
    public RlcdParams(Parameters ps) {
        this(
                ps.getDouble("alpha", 0.05),
                ps.getString("rlcd.stage1_method", "all"),
                ps.getInt("rlcd.max_samples", -1),
                ps.getString("rlcd.rank_test", "svd"),
                ps.getDouble("rlcd.svd_tau", 1e-7),
                ps.getBoolean("rlcd.use_gin", false)
        );
    }
}