package edu.cmu.tetrad.algcomparison.algorithm.other;

import edu.cmu.tetrad.util.Parameters;

// Thin wrapper over Parameters to keep keys centralized and discoverable.
public record RlcdParams(
        double alpha,
        String stage1Method,     // e.g. "all" (mirror Python CLI), or your chosen options
        int maxSamples,          // -1 => all
        String rankTestMethod,   // "svd" (built-in) or "wilks" (hook to RankTests)
        double svdTau,           // numerical threshold for rank (singular value cutoff)
        boolean useGin           // orient leftover edges with GIN
) {
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