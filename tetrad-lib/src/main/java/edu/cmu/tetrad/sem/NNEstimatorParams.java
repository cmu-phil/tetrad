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

    /**
     * Number of draws of the child per parent configuration when computing
     * edge strength ({@link NNEstimator#computeEdgeStrength}). Each
     * configuration is drawn this many times with the mechanism as fitted, and
     * this many times again with the parent's input randomized.
     * Default: 200.
     */
    public int edgeDrawsPerConfig = 200;

    /**
     * Number of independent repeats of the edge-strength computation, each
     * with its own draw of parent configurations, noise, and randomized parent
     * values. The reported values are the mean over repeats and the standard
     * deviation across them is reported alongside. Default: 3.
     */
    public int edgeRepeats = 3;

    /**
     * Number of refits used for the refit-noise null: the child's mechanism is
     * retrained with the <em>same</em> parents under a new seed, and the same
     * conditional MMD² is computed between the original and the refit. This
     * is the MMD² produced by training randomness alone; an edge whose
     * intervention MMD² does not clear it is not distinguishable from zero.
     * Computed once per child and shared by all edges into that child.
     * Set to 0 to skip. Default: 3.
     */
    public int edgeNullRefits = 3;

    /**
     * If true, rows are randomly permuted (using {@link #seed}) before being
     * cut into k contiguous folds for cross-validation. If false, folds are
     * contiguous blocks in file order, which is the right choice for serially
     * dependent rows. Default: false.
     */
    public boolean shuffleFolds = false;

    /** Hidden units in each node's network (single hidden layer). Default: 48. */
    public int hidden = 48;

    /** Training passes over the data per node. Default: 200. */
    public int epochs = 200;

    /** SGD learning rate. Default: 0.01. */
    public double lr = 0.01;

    /** L2 weight decay. Default: 1e-4. */
    public double l2 = 1e-4;

    /** Creates a parameter object with all defaults. */
    public NNEstimatorParams() {}
}
