package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.util.TetradSerializable;

import java.io.Serial;

/**
 * Result of a partial (residualized) edge strength computation performed by
 * {@link NNEstimator#computePartialEdgeStrength(String, String, int)}.
 *
 * <p>Partial edge strength asks: after accounting for everything Y's other
 * parents explain, how much additional variance does X explain in the
 * residual? This is the nonparametric analog of partial R² in regression,
 * and is less contaminated by inter-parent correlations than the marginal
 * edge strength in {@link EdgeStrengthResult}.
 *
 * <p>The computation:
 * <ol>
 *   <li>Uses the fitted mechanism for Y to predict Ŷ from all parents.</li>
 *   <li>Computes residuals R = Y − Ŷ on the observed data.</li>
 *   <li>Fits a small NN of R ~ X (single parent) via k-fold CV.</li>
 *   <li>Reports the OOS R² of that residual regression.</li>
 * </ol>
 *
 * <p>A positive {@link #partialR2} means X explains additional variance in Y
 * beyond what the other parents already account for — strong evidence the
 * edge is real. A near-zero or negative value suggests X adds little once
 * the other parents are controlled for.
 *
 * <p>Note: for discrete children cross-entropy improvement is used instead
 * of R², since R² is not meaningful for discrete outcomes.
 */
public final class PartialEdgeStrengthResult implements TetradSerializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Name of the parent variable (tail of the edge). */
    public final String parentName;

    /** Name of the child variable (head of the edge). */
    public final String childName;

    /** {@code true} if the child variable is discrete. */
    public final boolean discreteChild;

    /**
     * OOS R² of the residual regression R ~ X, estimated by k-fold CV.
     * Positive = X explains variance in the residual beyond other parents.
     * NaN for discrete children.
     */
    public final double partialR2;

    /**
     * Baseline variance of the residuals R = Y − Ŷ.
     * This is the variance left unexplained by the other parents.
     * NaN for discrete children.
     */
    public final double residualVariance;

    /**
     * OOS cross-entropy improvement of predicting R from X vs. predicting
     * the marginal residual distribution. For discrete children only.
     * NaN for continuous children.
     */
    public final double partialXentImprovement;

    /** Number of CV folds used. */
    public final int numFolds;

    // ── constructor ───────────────────────────────────────────────────────────

    PartialEdgeStrengthResult(String parentName,
                              String childName,
                              boolean discreteChild,
                              double partialR2,
                              double residualVariance,
                              double partialXentImprovement,
                              int numFolds) {
        this.parentName             = parentName;
        this.childName              = childName;
        this.discreteChild          = discreteChild;
        this.partialR2              = partialR2;
        this.residualVariance       = residualVariance;
        this.partialXentImprovement = partialXentImprovement;
        this.numFolds               = numFolds;
    }

    // ── display ───────────────────────────────────────────────────────────────

    public String toSummaryLine() {
        if (!discreteChild) {
            return String.format(
                    "%s → %s  |  Partial R² = %.4f  |  Residual var = %.4f  (k=%d)",
                    parentName, childName, partialR2, residualVariance, numFolds);
        } else {
            return String.format(
                    "%s → %s  |  Partial Xent improvement = %.4f  (k=%d)",
                    parentName, childName, partialXentImprovement, numFolds);
        }
    }

    @Override
    public String toString() {
        return toSummaryLine();
    }
}
