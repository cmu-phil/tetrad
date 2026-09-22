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
 * <p>Each measure is the mean over {@link #numRepeats} independent passes,
 * with the standard deviation across passes reported alongside. A
 * refit-noise null ({@link #nullMmd2}) gives the MMD² that training
 * randomness alone produces for this child; {@link #isAboveNoise()} compares
 * the edge to it.
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

    /** Standard deviation of {@link #mmd2} across repeats; NaN if one repeat. */
    public final double mmd2Sd;

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

    /** Standard deviation of {@link #varianceDiffFrac} across repeats; NaN if one repeat or discrete. */
    public final double varianceDiffFracSd;

    /**
     * Mean over configurations and randomized draws of
     * KL(P(Y | pa) ‖ P(Y | pa, X randomized)) in bits.
     * NaN for continuous children.
     */
    public final double klDivBits;

    /** Standard deviation of {@link #klDivBits} across repeats; NaN if one repeat or continuous. */
    public final double klDivBitsSd;

    /**
     * Number of observed parent configurations the measures were averaged over,
     * per repeat.
     */
    public final int simulatedN;

    /**
     * Number of draws of the child per configuration, for each of the two
     * conditions.
     */
    public final int drawsPerConfig;

    /** Number of independent repeats the means and SDs are taken over. */
    public final int numRepeats;

    /**
     * Refit-noise null: mean over refits of the same conditional MMD² between
     * the child's original mechanism and a refit with the same parents under a
     * new seed. This is the MMD² that training randomness alone produces.
     * Shared by all edges into this child. NaN if no refits were run.
     */
    public final double nullMmd2;

    /** Standard deviation of {@link #nullMmd2} across refits; NaN if fewer than two. */
    public final double nullMmd2Sd;

    /** Number of refits the null is based on; 0 if skipped. */
    public final int nullRefits;

    // ── constructor ───────────────────────────────────────────────────────────

    EdgeStrengthResult(String parentName,
                       String childName,
                       boolean discreteChild,
                       double mmd2,
                       double mmd2Sd,
                       double varianceDiff,
                       double varianceDiffFrac,
                       double varianceDiffFracSd,
                       double klDivBits,
                       double klDivBitsSd,
                       int simulatedN,
                       int drawsPerConfig,
                       int numRepeats,
                       double nullMmd2,
                       double nullMmd2Sd,
                       int nullRefits) {
        this.parentName         = parentName;
        this.childName          = childName;
        this.discreteChild      = discreteChild;
        this.mmd2               = mmd2;
        this.mmd2Sd             = mmd2Sd;
        this.varianceDiff       = varianceDiff;
        this.varianceDiffFrac   = varianceDiffFrac;
        this.varianceDiffFracSd = varianceDiffFracSd;
        this.klDivBits          = klDivBits;
        this.klDivBitsSd        = klDivBitsSd;
        this.simulatedN         = simulatedN;
        this.drawsPerConfig     = drawsPerConfig;
        this.numRepeats         = numRepeats;
        this.nullMmd2           = nullMmd2;
        this.nullMmd2Sd         = nullMmd2Sd;
        this.nullRefits         = nullRefits;
    }

    /**
     * Whether the edge's MMD² clears the refit-noise band: the null mean plus
     * two null standard deviations (or just the null mean if only one refit
     * was run). If no null was computed this returns {@code true}, because
     * there is nothing to compare against, not because the edge is strong.
     *
     * @return true if the intervention MMD² is above the refit-noise band
     */
    public boolean isAboveNoise() {
        if (!Double.isFinite(nullMmd2)) return true;
        double band = nullMmd2 + (Double.isFinite(nullMmd2Sd) ? 2.0 * nullMmd2Sd : 0.0);
        return mmd2 > band;
    }

    // ── display ───────────────────────────────────────────────────────────────

    /**
     * One-line summary suitable for a status bar.
     *
     * @return a formatted summary line
     */
    public String toSummaryLine() {
        String sd = Double.isFinite(mmd2Sd) ? String.format(" ± %.4f", mmd2Sd) : "";
        String nul = Double.isFinite(nullMmd2)
                ? String.format("  |  null MMD² = %.4f%s", nullMmd2,
                isAboveNoise() ? "" : " (not above noise)")
                : "";
        if (!discreteChild) {
            return String.format(
                    "%s → %s  |  MMD² = %.4f%s  |  ΔVar/Var(Y) = %.4f%s  (configs = %d × %d)",
                    parentName, childName, mmd2, sd, varianceDiffFrac, nul, simulatedN, numRepeats);
        } else {
            return String.format(
                    "%s → %s  |  MMD² = %.4f%s  |  KL = %.4f bits%s  (configs = %d × %d)",
                    parentName, childName, mmd2, sd, klDivBits, nul, simulatedN, numRepeats);
        }
    }

    @Override
    public String toString() {
        return toSummaryLine();
    }
}
