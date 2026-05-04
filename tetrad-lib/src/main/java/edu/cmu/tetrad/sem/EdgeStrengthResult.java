package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.util.TetradSerializable;

import java.io.Serial;

/**
 * Result of a single edge-strength computation performed by
 * {@link NNEstimator#computeEdgeStrength(String, String, int)}.
 *
 * <p>Edge strength is defined as the change in the marginal distribution of
 * the child variable when the edge from parent to child is removed from the
 * DAG and the child's mechanism is retrained without that parent.
 *
 * <p>Two complementary measures are reported:
 * <ul>
 *   <li><b>MMD²</b> — Maximum Mean Discrepancy between the marginal
 *       distribution of the child under the original model and under the
 *       edge-removed model. This is nonparametric and captures shape changes
 *       as well as variance changes. Higher = stronger edge.</li>
 *   <li><b>Variance difference</b> (continuous nodes only) — the increase in
 *       marginal variance of the child when the edge is removed:
 *       var(Y_removed) − var(Y_original). Positive means the edge was
 *       explaining variance. Analogous to DoWhy's arrow_strength default
 *       metric.</li>
 *   <li><b>KL divergence</b> (discrete nodes only) — KL(P_removed ‖ P_original)
 *       in bits, where P is the empirical marginal class distribution.
 *       Analogous to DoWhy's arrow_strength for categorical targets.</li>
 * </ul>
 */
public final class EdgeStrengthResult implements TetradSerializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Name of the parent variable (tail of the removed edge). */
    public final String parentName;

    /** Name of the child variable (head of the removed edge). */
    public final String childName;

    /** {@code true} if the child variable is discrete. */
    public final boolean discreteChild;

    /**
     * MMD² between the marginal distribution of the child under the original
     * model and under the edge-removed model. Valid for both continuous and
     * discrete children. Higher = stronger edge.
     */
    public final double mmd2;

    /**
     * Increase in marginal variance of the child when the edge is removed:
     * var(Y_removed) − var(Y_original).
     * NaN for discrete children.
     */
    public final double varianceDiff;

    /**
     * KL divergence KL(P_removed ‖ P_original) in bits, where P is the
     * empirical marginal class distribution of the child.
     * NaN for continuous children.
     */
    public final double klDivBits;

    /**
     * Number of rows simulated from each model for the comparison.
     */
    public final int simulatedN;

    // ── constructor ───────────────────────────────────────────────────────────

    EdgeStrengthResult(String parentName,
                       String childName,
                       boolean discreteChild,
                       double mmd2,
                       double varianceDiff,
                       double klDivBits,
                       int simulatedN) {
        this.parentName    = parentName;
        this.childName     = childName;
        this.discreteChild = discreteChild;
        this.mmd2          = mmd2;
        this.varianceDiff  = varianceDiff;
        this.klDivBits     = klDivBits;
        this.simulatedN    = simulatedN;
    }

    // ── display ───────────────────────────────────────────────────────────────

    /**
     * Returns a human-readable one-line summary.
     * @return a human-readable one-line summary
     */
    public String toSummaryLine() {
        if (!discreteChild) {
            return String.format(
                    "%s → %s  |  MMD² = %.4f  |  ΔVar = %.4f  (n = %d)",
                    parentName, childName, mmd2, varianceDiff, simulatedN);
        } else {
            return String.format(
                    "%s → %s  |  MMD² = %.4f  |  KL = %.4f bits  (n = %d)",
                    parentName, childName, mmd2, klDivBits, simulatedN);
        }
    }

    @Override
    public String toString() {
        return toSummaryLine();
    }
}
