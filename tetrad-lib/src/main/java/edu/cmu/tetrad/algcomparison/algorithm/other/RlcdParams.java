package edu.cmu.tetrad.algcomparison.algorithm.other;

import edu.cmu.tetrad.util.Parameters;

/**
 * The RlcdParams record encapsulates the configuration parameters for RLCD (Rank-based Local Causal Discovery). It
 * provides various options for controlling the behavior and methods used in RLCD analysis.
 *
 * @param alpha          the significance level for statistical tests, typically in the range [0, 1]. A smaller value
 *                       (e.g., 0.05) indicates stricter criteria for rejecting the null hypothesis.
 * @param stage1Method   a string specifying the approach used in stage 1. Common values include "all", but other custom
 *                       methods can also be specified as needed.
 * @param maxSamples     an integer specifying the maximum number of samples to process. A value of -1 indicates that
 *                       all available samples should be used.
 * @param rankTestMethod a string indicating the method for rank testing. Supported methods include "svd" (for singular
 *                       value decomposition) and "wilks" (for Wilks' lambda test via a hook to rank tests).
 * @param svdTau         a numerical threshold (double) used in singular value decomposition for determining the rank.
 *                       Singular values below this threshold are considered negligible.
 * @param useGin         a boolean parameter that specifies whether the GIN (Generalized Independent Noise) method will
 *                       be applied to orient leftover edges in the causal graph.
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
     * @param ps the Parameters object containing configuration values. Keys and their default values include: -
     *           "alpha": a double value with a default of 0.05. - "rlcd.stage1_method": a string specifying the stage 1
     *           method, defaulting to "all". - "rlcd.max_samples": an integer representing the maximum number of
     *           samples, defaulting to -1 (interpreted as all samples). - "rlcd.rank_test": a string specifying the
     *           rank test method, defaulting to "svd". - "rlcd.svd_tau": a double numerical threshold for singular
     *           value decomposition, defaulting to 1e-7. - "rlcd.use_gin": a boolean indicating whether to orient
     *           leftover edges using GIN, defaulting to false.
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