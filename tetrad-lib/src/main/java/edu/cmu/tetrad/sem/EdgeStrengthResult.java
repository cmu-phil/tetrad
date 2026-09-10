package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.util.TetradSerializable;

import java.io.Serial;

/**
 * Result of a single edge-strength computation performed by
 * {@link NNEstimator#computeEdgeStrength(String, String, int)}.
 *
 * <p>Edge strength here is the <em>intervention</em> strength of Janzing et
 * al. (2013), as implemented in DoWhy's {@code arrow_strength}: the child's
 * fitted mechanism is held fixed, and for each observed parent configuration
 * the child is drawn many times with the configuration as is and many times
 * again with the parent's input replaced by an independent draw from that
 * parent's marginal. The difference between the two conditional distributions
 * is measured and averaged over configurations. Nothing is retrained.
 *
 * <p>Because the mechanism is not refit, a parent that is redundant with
 * another parent still registers as strong if the mechanism actually uses
 * it. That is the intended contrast with
 * {@link NNEstimator#computePartialEdgeStrength}, which asks whether the
 * parent adds held-out predictive information beyond the other parents.
 *
 * <p>Three measures are reported:
 * <ul>
 *   <li><b>MMD²</b> — mean over configurations of the Maximum Mean
 *       Discrepancy between the two sets of conditional draws, with a
 *       continuous child standardized by its observed standard deviation so
 *       values are comparable across children. Higher = stronger edge.</li>
 *   <li><b>Variance difference</b> (continuous) — mean over configurations of
 *       var(Y | pa, X randomized) − var(Y | pa). DoWhy's default for
 *       continuous targets. Reported both raw ({@link #varianceDiff}) and
 *       divided by the child's observed variance ({@link #varianceDiffFrac}).</li>
 *   <li><b>KL divergence</b> (discrete) — mean over configurations and
 *       randomized draws of KL(P(Y | pa) ‖ P(Y | pa, X randomized)) in bits.
 *       DoWhy's default for categorical targets.</li>
 * </ul>
 */
public final class EdgeStrengthResult implements TetradSerializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Name of the parent variable (tail of the edge). */
    public final String parentName;

    /** Name of the child variable (head of the edge). */
    public final String childName;

    /** {@code true} if the child variable is discrete. */
    public final boolean discreteChild;

    /**
     * Mean over parent configurations of MMD² between draws of the child with
     * the configuration fixed and draws with the parent's input randomized.
     * Continuous children are standardized by their observed SD first.
     * Higher = stronger edge.
     */
    public final double mmd2;

    /**
     * Mean over parent configurations of
     * var(Y | pa, X randomized) − var(Y | pa), in the child's units squared.
     * NaN for discrete children.
     */
    public final double varianceDiff;

    /**
     * {@link #varianceDiff} divided by the child's observed marginal variance,
     * so it is comparable across children. NaN for discrete children.
     */
    public final double varianceDiffFrac;

    /**
     * Mean over configurations and randomized draws of
     * KL(P(Y | pa) ‖ P(Y | pa, X randomized)) in bits.
     * NaN for continuous children.
     */
    public final double klDivBits;

    /**
     * Number of observed parent configurations the measures were averaged over.
     */
    public final int simulatedN;

    /**
     * Number of draws of the child per configuration, for each of the two
     * conditions.
     */
    public final int drawsPerConfig;

    // ── constructor ───────────────────────────────────────────────────────────

    EdgeStrengthResult(String parentName,
                       String childName,
                       boolean discreteChild,
                       double mmd2,
                       double varianceDiff,
                       double varianceDiffFrac,
                       double klDivBits,
                       int simulatedN,
                       int drawsPerConfig) {
        this.parentName       = parentName;
        this.childName        = childName;
        this.discreteChild    = discreteChild;
        this.mmd2             = mmd2;
        this.varianceDiff     = varianceDiff;
        this.varianceDiffFrac = varianceDiffFrac;
        this.klDivBits        = klDivBits;
        this.simulatedN       = simulatedN;
        this.drawsPerConfig   = drawsPerConfig;
    }

    // ── display ───────────────────────────────────────────────────────────────

    /**
     * One-line summary suitable for a status bar.
     *
     * @return a formatted summary line
     */
    public String toSummaryLine() {
        if (!discreteChild) {
            return String.format(
                    "%s → %s  |  MMD² = %.4f  |  ΔVar/Var(Y) = %.4f  (configs = %d)",
                    parentName, childName, mmd2, varianceDiffFrac, simulatedN);
        } else {
            return String.format(
                    "%s → %s  |  MMD² = %.4f  |  KL = %.4f bits  (configs = %d)",
                    parentName, childName, mmd2, klDivBits, simulatedN);
        }
    }

    @Override
    public String toString() {
        return toSummaryLine();
    }
}
