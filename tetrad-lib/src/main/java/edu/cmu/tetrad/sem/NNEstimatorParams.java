package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.util.TetradSerializable;

import java.io.Serial;

/**
 * Tuning parameters for {@link NNEstimator}.
 *
 * <p>All fields have sensible defaults so that callers that don't care about
 * tuning can simply use {@code new NNEstimatorParams()} and move on.
 */
public final class NNEstimatorParams implements TetradSerializable {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Random seed for the GNM simulator.
     * Defaults to a time-based value so successive runs differ by default.
     */
    public long seed = System.nanoTime();

    /**
     * Number of random Fourier features used by the MMD² approximation.
     * Larger values give a more accurate estimate at the cost of speed.
     * Default: 512.
     */
    public int mmdFeatures = 512;

    /**
     * Random seed for the MMD² random feature approximation.
     * Fix this to get reproducible adequacy scores.
     * Default: 42.
     */
    public long mmdSeed = 42L;

    /**
     * Maximum number of rows used when computing MMD².
     * Set to a smaller value to keep adequacy computation fast on large datasets.
     * Default: 5000.
     */
    public int mmdMaxRows = 5000;

    /** Creates a parameter object with all defaults. */
    public NNEstimatorParams() {}
}
