package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.util.TetradSerializable;

import java.io.Serial;

/**
 * Result of a partial edge strength computation performed by
 * {@link NNEstimator#computePartialEdgeStrength(String, String, int)}.
 *
 * <p>Partial edge strength asks: once Y's other parents are accounted for,
 * does X add anything to held-out prediction of Y? It is computed on the
 * same k folds as {@link NNEstimator#crossValidate(int)}: on each fold the
 * full model (all parents) and a reduced model (Y's mechanism retrained on
 * the fold's training rows without X) both predict the held-out rows, and
 * the difference in held-out score is reported.
 *
 * <ul>
 *   <li><b>Continuous child:</b> {@link #partialR2} = R²_full − R²_reduced,
 *       with R² = 1 − OOS MSE / marginal variance, matching the
 *       Cross-Validation table. {@link #residualVariance} holds the reduced
 *       model's held-out MSE.</li>
 *   <li><b>Discrete child:</b> {@link #partialXentImprovement} =
 *       xent_reduced − xent_full in nats.</li>
 * </ul>
 *
 * <p>Positive means X carries information about Y that the other parents do
 * not. Near zero means X is redundant given the other parents, which is
 * not the same as X not being a cause; see {@link EdgeStrengthResult} for
 * the complementary intervention measure, which stays large for a redundant
 * parent the fitted mechanism actually uses.
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
     * Held-out R² of the full model minus held-out R² of the model with X
     * removed from Y's parents, on the same k folds as the CV table.
     * Positive = X adds predictive information beyond the other parents.
     * NaN for discrete children.
     */
    public final double partialR2;

    /**
     * Held-out MSE of the reduced model (Y predicted from its other parents,
     * without X). Kept under its original field name for session
     * compatibility. NaN for discrete children.
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

    /**
     * Returns a human-readable one-line summary.
     * @return a human-readable one-line summary
     */
    public String toSummaryLine() {
        if (!discreteChild) {
            return String.format(
                    "%s → %s  |  Partial ΔR² = %.4f  |  Reduced OOS MSE = %.4f  (k=%d)",
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
