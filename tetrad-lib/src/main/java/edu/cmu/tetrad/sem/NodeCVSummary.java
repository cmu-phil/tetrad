package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.util.TetradSerializable;

import java.io.Serial;
import java.util.Collections;
import java.util.List;

/**
 * Immutable per-node summary produced by k-fold cross-validation in
 * NNEstimator#crossValidate(int).
 *
 * <p>For continuous nodes the primary OOS metric is {@link #oosMse} and the
 * derived {@link #oosR2} (improvement over predicting the marginal mean).
 * For discrete nodes the primary metric is {@link #oosXent} (average
 * cross-entropy on held-out rows).
 *
 * <p>Root nodes (no parents) have no conditional prediction and carry
 * {@code Double.NaN} for all prediction metrics.
 */
public final class NodeCVSummary implements TetradSerializable {

    @Serial
    private static final long serialVersionUID = 23L;

    /** Variable name. */
    public final String node;

    /** {@code true} if the variable is discrete. */
    public final boolean discreteChild;

    /** Parent variable names (empty for root nodes). */
    public final List<String> parents;

    /** Number of folds used. */
    public final int numFolds;

    // ── continuous metrics (NaN if discrete or root) ──────────────────────────

    /**
     * Mean OOS MSE across folds: average of (observed - predicted)² on
     * held-out rows. Uses zero-noise prediction (conditional mean estimate).
     * NaN for discrete or root nodes.
     */
    public final double oosMse;

    /**
     * Baseline MSE: variance of the child variable estimated from the full
     * dataset. This is the MSE you would get by always predicting the
     * marginal mean.
     */
    public final double baselineMse;

    /**
     * OOS R²: 1 - oosMse / baselineMse.
     * Positive means the NN beats the mean baseline out of sample.
     * NaN if either component is NaN.
     */
    public final double oosR2;

    // ── discrete metrics (NaN if continuous or root) ──────────────────────────

    /**
     * Mean OOS cross-entropy across folds on held-out rows.
     * NaN for continuous or root nodes.
     */
    public final double oosXent;

    /**
     * Baseline cross-entropy: entropy of the empirical marginal distribution
     * of the child variable. This is the cross-entropy you would get by
     * always predicting the marginal class probabilities.
     */
    public final double baselineXent;

    // ── constructor ───────────────────────────────────────────────────────────

    NodeCVSummary(String node,
                  boolean discreteChild,
                  List<String> parents,
                  int numFolds,
                  double oosMse,
                  double baselineMse,
                  double oosXent,
                  double baselineXent) {
        this.node           = node;
        this.discreteChild  = discreteChild;
        this.parents        = Collections.unmodifiableList(parents);
        this.numFolds       = numFolds;
        this.oosMse         = oosMse;
        this.baselineMse    = baselineMse;
        this.oosR2          = (Double.isFinite(oosMse) && Double.isFinite(baselineMse) && baselineMse > 0)
                ? 1.0 - oosMse / baselineMse
                : Double.NaN;
        this.oosXent        = oosXent;
        this.baselineXent   = baselineXent;
    }

    @Override
    public String toString() {
        if (!Double.isFinite(oosMse) && !Double.isFinite(oosXent)) {
            return node + " [root — no conditional prediction]";
        }
        if (!discreteChild) {
            return String.format("%s  oosMSE=%.4f  baselineMSE=%.4f  R²=%.4f",
                    node, oosMse, baselineMse, oosR2);
        } else {
            return String.format("%s  oosXent=%.4f  baselineXent=%.4f",
                    node, oosXent, baselineXent);
        }
    }
}
