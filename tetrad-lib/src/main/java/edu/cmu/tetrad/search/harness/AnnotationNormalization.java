///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.harness;

import java.util.Arrays;
import java.util.Objects;

/**
 * How a raw per-(SNP, trait) annotation matrix is turned into the adjustment scores that
 * {@link EdgePriorFacade} and {@link EdgePriorScoreFacade} scale by tau.
 *
 * <p><b>Why this exists.</b> The original scheme, {@link #Z_SCORE}, standardises the pooled
 * annotation to mean zero and unit standard deviation. That keeps tau = 0 an honest baseline but
 * is <i>unbounded</i>: under toll scaling the effective penalty for an edge is
 * {@code lambda * log(n) * (1 - tau * a)}, so at tau = 0.5 a z-score of 2 zeroes the penalty and
 * anything beyond that turns it into an edge-addition reward. The forward phase then adds the
 * edge unconditionally and the backward phase can never remove it -- the required-knowledge limit
 * is crossed silently, driven by the annotation's tails rather than by a deliberate choice. For a
 * roughly normal annotation at tau = 0.5, about two percent of the pool crosses it. The bounded
 * modes below exist so that no data-driven adjustment can cross that limit at moderate tau, and
 * {@link EdgePriorScoreFacade#numForcedPairs(double, int)} exists so that a crossing, wherever it
 * comes from, is counted rather than silent.
 *
 * <p><b>Conventions shared by every mode.</b> An entry with no information -- {@code NaN} in the
 * input, and for the log modes any non-positive weight -- receives a {@code NaN} adjustment,
 * which the facades map to a zero prior; such entries do not enter the statistics. A degenerate
 * column (constant, or with fewer than two usable entries) receives neutral adjustments
 * throughout, so a constant annotation reproduces the unmodified search exactly. {@link #Z_SCORE}
 * pools the whole matrix, as the legacy code always has; the bounded modes normalise each trait
 * column separately over the full candidate pool, so one trait's scale cannot distort another's,
 * and the normalisation must be computed once over the full pool, never recomputed inside
 * subsample repeats, for the same fixed-family reason as
 * {@link edu.cmu.tetrad.search.utils.EdgePriors#normalizedToMeanOne()}.
 *
 * <p><b>Choosing a mode.</b> {@link #CENTERED_LOG_MIN_MAX} is the conservative default for
 * positive, right-skewed weights: bounded, zero mean, respects magnitude on the log scale. Its
 * weakness is that the two most extreme SNPs set the scale for everyone, so one huge weight
 * flattens every other adjustment toward zero, and for light-tailed log weights the typical
 * adjustment shrinks as the pool grows. {@link #ROBUST_CLIPPED} trades the exact zero mean for a
 * stable typical scale: outliers saturate at the clip instead of either dominating or flattening
 * the rest. {@link #NONE} is for adjustments computed elsewhere (for instance in Python) that
 * must pass through untouched -- the facades do <i>not</i> re-standardise them.
 *
 * @author josephramsey
 * @see EdgePriorFacade
 * @see EdgePriorScoreFacade
 */
public enum AnnotationNormalization {

    /**
     * The legacy scheme: the pooled finite entries are standardised to mean zero and unit sample
     * standard deviation (n - 1 denominator). Zero mean, <b>unbounded</b>. When this mode is
     * passed to a facade factory, the facade delegates to its original code path, so results are
     * bit for bit what the pre-mode factories produce.
     */
    Z_SCORE,

    /**
     * Centred min-max, per trait column: {@code a = (w - mean(w)) / (max(w) - min(w))} over the
     * column's finite entries. Zero mean and bounded in [-1, 1] by construction; the spread
     * {@code max(a) - min(a)} is exactly 1, so for a roughly symmetric column the values sit near
     * [-1/2, 1/2] and at tau = 0.5 the effective penalties stay within roughly 3/4 to 5/4 of
     * baseline.
     */
    CENTERED_MIN_MAX,

    /**
     * Centred log min-max, per trait column: {@link #CENTERED_MIN_MAX} applied to
     * {@code log(w)} over the column's entries that are finite <i>and strictly positive</i>;
     * non-positive weights are treated as having no information. Zero mean and bounded in
     * [-1, 1]; the natural choice for positive, right-skewed annotation weights. If a weight of
     * zero is meant as evidence <i>against</i> an edge rather than as missing, floor it to a
     * small positive value before calling, since this mode cannot tell the two apart.
     */
    CENTERED_LOG_MIN_MAX,

    /**
     * Robust clipped, per trait column, on the log scale: {@code a = clip((log(w) - median) /
     * (2 * IQR), -1, 1)} over the column's finite, strictly positive entries, with quartiles by
     * linear interpolation (type 7). The typical adjustment keeps a stable size regardless of
     * pool size or outliers -- for normal log weights about 0.7 percent of entries clip -- at the
     * price of an exact zero <i>median</i> rather than an exact zero mean; the mean is
     * approximately zero for roughly symmetric log weights. A column whose IQR is zero (heavy
     * ties) is treated as degenerate and receives neutral adjustments.
     */
    ROBUST_CLIPPED,

    /**
     * No normalisation: the entries <i>are</i> the adjustments, passed through unchanged
     * ({@code NaN} still means no information). This is the mode for adjustments precomputed
     * outside -- the facades do not re-standardise them, which silently undoes any bounding done
     * upstream. Nothing bounds these values, so under toll scaling any entry with
     * {@code tau * a >= 1} is a forced edge; the toll-scaled factories count such pairs and warn,
     * and {@link EdgePriorScoreFacade#numForcedPairs(double, int)} reports the count on demand.
     */
    NONE;

    /**
     * Computes the adjustment matrix for an annotation under this mode. The input is not
     * modified; the result has the same shape, with {@code NaN} wherever the entry carries no
     * information under this mode's conventions (see the class Javadoc).
     *
     * @param annotation An n_snp x n_trait matrix; rows are SNPs, columns are traits. Higher
     *                   means more prior support. {@code NaN} means no information.
     * @return The adjustments, same shape; {@code NaN} for entries carrying no adjustment.
     * @throws IllegalArgumentException If the matrix is ragged, or contains an infinite entry.
     * @throws NullPointerException     If the annotation or any of its rows is null.
     */
    public double[][] adjust(double[][] annotation) {
        Objects.requireNonNull(annotation, "annotation");

        int nRows = annotation.length;
        int nCols = (nRows > 0) ? Objects.requireNonNull(annotation[0], "annotation[0]").length : 0;

        for (int j = 0; j < nRows; j++) {
            Objects.requireNonNull(annotation[j], "annotation[" + j + "]");

            if (annotation[j].length != nCols) {
                throw new IllegalArgumentException("annotation is ragged: row " + j + " has length "
                        + annotation[j].length + "; expected " + nCols + ".");
            }

            for (int k = 0; k < nCols; k++) {
                double v = annotation[j][k];

                if (Double.isInfinite(v)) {
                    throw new IllegalArgumentException("annotation[" + j + "][" + k
                            + "] is infinite; use NaN for a missing entry.");
                }
            }
        }

        double[][] a = new double[nRows][nCols];

        switch (this) {
            case NONE -> {
                for (int j = 0; j < nRows; j++) {
                    System.arraycopy(annotation[j], 0, a[j], 0, nCols);
                }
            }
            case Z_SCORE -> adjustPooledZ(annotation, a, nRows, nCols);
            case CENTERED_MIN_MAX -> {
                for (int k = 0; k < nCols; k++) {
                    adjustColumnMinMax(annotation, a, nRows, k, false);
                }
            }
            case CENTERED_LOG_MIN_MAX -> {
                for (int k = 0; k < nCols; k++) {
                    adjustColumnMinMax(annotation, a, nRows, k, true);
                }
            }
            case ROBUST_CLIPPED -> {
                for (int k = 0; k < nCols; k++) {
                    adjustColumnRobust(annotation, a, nRows, k);
                }
            }
        }

        return a;
    }

    /**
     * The legacy pooled z-scoring: mean zero, unit sample SD over all finite entries. Degenerate
     * (constant or fewer than two finite entries) gives all-NaN.
     */
    private static void adjustPooledZ(double[][] annotation, double[][] a, int nRows, int nCols) {
        double sum = 0.0;
        long count = 0;

        for (double[] row : annotation) {
            for (double v : row) {
                if (Double.isFinite(v)) {
                    sum += v;
                    count++;
                }
            }
        }

        double mean = (count > 0) ? sum / count : 0.0;
        double ss = 0.0;

        for (double[] row : annotation) {
            for (double v : row) {
                if (Double.isFinite(v)) {
                    ss += (v - mean) * (v - mean);
                }
            }
        }

        double sd = (count > 1) ? Math.sqrt(ss / (count - 1)) : 0.0;
        boolean degenerate = !(sd > 0.0);

        for (int j = 0; j < nRows; j++) {
            for (int k = 0; k < nCols; k++) {
                double v = annotation[j][k];
                a[j][k] = (degenerate || !Double.isFinite(v)) ? Double.NaN : (v - mean) / sd;
            }
        }
    }

    /**
     * Centred min-max for one column, optionally on the log scale. Fills column k of {@code a};
     * a degenerate column (range zero or fewer than two usable entries) is all-NaN.
     */
    private static void adjustColumnMinMax(double[][] annotation, double[][] a, int nRows, int k,
                                           boolean logScale) {
        double[] t = usableColumn(annotation, nRows, k, logScale);

        double sum = 0.0;
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        int count = 0;

        for (int j = 0; j < nRows; j++) {
            double v = t[j];

            if (!Double.isNaN(v)) {
                sum += v;
                min = Math.min(min, v);
                max = Math.max(max, v);
                count++;
            }
        }

        double range = max - min;
        boolean degenerate = count < 2 || !(range > 0.0);
        double mean = (count > 0) ? sum / count : 0.0;

        for (int j = 0; j < nRows; j++) {
            double v = t[j];
            a[j][k] = (degenerate || Double.isNaN(v)) ? Double.NaN : (v - mean) / range;
        }
    }

    /**
     * Robust clipped for one column: log scale, centred at the median, scaled by twice the IQR,
     * clipped to [-1, 1]. A column with IQR zero or fewer than two usable entries is all-NaN.
     */
    private static void adjustColumnRobust(double[][] annotation, double[][] a, int nRows, int k) {
        double[] t = usableColumn(annotation, nRows, k, true);
        double[] sorted = Arrays.stream(t).filter(v -> !Double.isNaN(v)).sorted().toArray();

        int count = sorted.length;
        double median = (count > 0) ? quantileType7(sorted, 0.5) : 0.0;
        double iqr = (count > 0) ? quantileType7(sorted, 0.75) - quantileType7(sorted, 0.25) : 0.0;
        boolean degenerate = count < 2 || !(iqr > 0.0);

        for (int j = 0; j < nRows; j++) {
            double v = t[j];

            if (degenerate || Double.isNaN(v)) {
                a[j][k] = Double.NaN;
            } else {
                a[j][k] = Math.max(-1.0, Math.min(1.0, (v - median) / (2.0 * iqr)));
            }
        }
    }

    /**
     * Extracts column k as a length-nRows vector with NaN for unusable entries: non-finite
     * always, and non-positive as well when {@code logScale}, in which case usable entries are
     * returned as their logs.
     */
    private static double[] usableColumn(double[][] annotation, int nRows, int k, boolean logScale) {
        double[] t = new double[nRows];

        for (int j = 0; j < nRows; j++) {
            double v = annotation[j][k];

            if (!Double.isFinite(v) || (logScale && !(v > 0.0))) {
                t[j] = Double.NaN;
            } else {
                t[j] = logScale ? Math.log(v) : v;
            }
        }

        return t;
    }

    /**
     * The type-7 (linear interpolation) sample quantile of an already-sorted, NaN-free array.
     */
    private static double quantileType7(double[] sorted, double p) {
        int n = sorted.length;

        if (n == 1) {
            return sorted[0];
        }

        double h = (n - 1) * p;
        int lo = (int) Math.floor(h);
        int hi = Math.min(lo + 1, n - 1);
        return sorted[lo] + (h - lo) * (sorted[hi] - sorted[lo]);
    }

    /**
     * Builds the full symmetric (n_snp + n_trait)-square log-odds matrix from an annotation under
     * this mode, scaling each adjustment by {@code scale} and leaving SNP-SNP, trait-trait, and
     * no-information pairs at zero. Package-private: this is the shared assembly step behind the
     * mode-taking facade factories; the legacy (pre-mode) factories keep their own untouched
     * code paths.
     *
     * @param snpNames   Names of the SNP nodes, length n_snp.
     * @param traitNames Names of the trait nodes, length n_trait.
     * @param annotation An n_snp x n_trait matrix; {@code NaN} means no information.
     * @param scale      The factor each adjustment is multiplied by (tau, or its toll-scaled
     *                   version).
     * @return The (n_snp + n_trait)-square symmetric beta matrix.
     * @throws IllegalArgumentException If shapes disagree with the name arrays, the scale is not
     *                                  finite, or the annotation fails {@link #adjust}'s checks.
     * @throws NullPointerException     If any argument is null.
     */
    double[][] betaFromAnnotation(String[] snpNames, String[] traitNames, double[][] annotation,
                                  double scale) {
        Objects.requireNonNull(snpNames, "snpNames");
        Objects.requireNonNull(traitNames, "traitNames");
        Objects.requireNonNull(annotation, "annotation");

        int nSnp = snpNames.length;
        int nTrait = traitNames.length;

        if (!Double.isFinite(scale)) {
            throw new IllegalArgumentException("tau (or its scaling) must be finite: " + scale);
        }

        if (annotation.length != nSnp) {
            throw new IllegalArgumentException("annotation has " + annotation.length
                    + " rows but " + nSnp + " SNP names were given.");
        }

        for (int j = 0; j < nSnp; j++) {
            if (annotation[j].length != nTrait) {
                throw new IllegalArgumentException("annotation row " + j + " has length "
                        + annotation[j].length + "; expected " + nTrait + ".");
            }
        }

        double[][] a = adjust(annotation);

        int p = nSnp + nTrait;
        double[][] beta = new double[p][p];

        for (int j = 0; j < nSnp; j++) {
            for (int k = 0; k < nTrait; k++) {
                double v = a[j][k];
                double b = Double.isNaN(v) ? 0.0 : scale * v;
                beta[j][nSnp + k] = b;
                beta[nSnp + k][j] = b;
            }
        }

        return beta;
    }
}
