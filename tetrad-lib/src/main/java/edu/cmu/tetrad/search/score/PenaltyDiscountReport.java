///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2026 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
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

package edu.cmu.tetrad.search.score;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A calibrated penalty discount together with everything needed to interpret it: the degrees-of-freedom histogram
 * it was computed from, the per-class false-positive levels it implies, the recall-side effect floor, and a sweep
 * of the same quantities over a range of penalty discounts.
 *
 * <p>This is a presentation layer over {@link PenaltyDiscountCalibration}. It performs no statistics of its own;
 * every number here comes from that class or from
 * {@link SemBicScore#minDetectablePartialCorrelation(double, int)}, so a report and the automatic penalty applied
 * by the score wrappers under {@code semBicAutoPenalty} agree by construction. It exists so that the calculation
 * can be driven from a dialog, from py-tetrad, or from a test without duplicating the assembly logic in each.</p>
 *
 * <p><b>The two criteria.</b> The false-discovery criterion asks how many <i>null</i> pairs become edges: it holds
 * the expected number of spurious edges at {@code fdr} times the expected number of true edges,
 * {@code p * expectedDegree / 2}. It shrinks like 1 / ln N, so at large N it admits any real dependence however
 * small. The effect-size criterion instead asks what the smallest dependence worth an edge is, and grows like
 * N / ln N. This class reports both and takes the larger, which controls false positives at small N and effect
 * size at large N. Setting {@code minEffect} to zero disables the second criterion.</p>
 *
 * <p><b>Reading the per-class table.</b> A single penalty discount does not test all variable pairs at the same
 * level. A pair costs {@code size[x] * size[y]} degrees of freedom, the threshold is {@code c * df * ln N}, and a
 * chi-square concentrates as df grows, so on mixed data the continuous-continuous pairs can be running at a level
 * many orders of magnitude smaller than the binary-binary pairs at the same c. The calibration gets the expected
 * <i>total</i> right; it does not equalize the per-class levels, and the table is where that shows.</p>
 *
 * @author josephramsey
 * @see PenaltyDiscountCalibration
 */
public final class PenaltyDiscountReport {

    /**
     * The df histogram the report was computed from: df to number of null pairs.
     */
    private final Map<Integer, Long> dfPairCounts;

    /**
     * Permutation-fitted nulls per df class, or null when the exact chi-square null is used.
     */
    private final Map<Integer, PenaltyDiscountCalibration.NullFit> fits;

    /**
     * The number of variables, p.
     */
    private final int numVariables;

    /**
     * The sample size N used in the penalty.
     */
    private final int sampleSize;

    /**
     * The assumed average degree of the true graph.
     */
    private final double expectedDegree;

    /**
     * The tolerated ratio of spurious to true edges.
     */
    private final double fdr;

    /**
     * The smallest partial correlation worth an edge, or 0 to disable the effect criterion.
     */
    private final double minEffect;

    /**
     * The penalty discount from the false-discovery criterion.
     */
    private final double cFdr;

    /**
     * The penalty discount from the effect-size criterion, or 0 when disabled.
     */
    private final double cEffect;

    /**
     * One row of the sweep: a penalty discount and what it implies.
     *
     * @param penaltyDiscount        c.
     * @param expectedFalseEdges     Expected spurious edges over the whole search at this c.
     * @param falseToTrueRatio       That count divided by the expected number of true edges.
     * @param minDetectableR         Smallest per-parameter partial correlation accepted at this c.
     * @param alphaSmallestDf        Per-pair false-positive level in the smallest df class.
     * @param alphaLargestDf         Per-pair false-positive level in the largest df class.
     */
    public record Row(double penaltyDiscount, double expectedFalseEdges, double falseToTrueRatio,
                      double minDetectableR, double alphaSmallestDf, double alphaLargestDf) {
    }

    /**
     * Builds a report.
     *
     * @param blockSizes     One parameter-block size per variable: all ones for SEM BIC, categories minus one for
     *                       Degenerate Gaussian and discrete BIC, the embedding block size for Basis Function BIC
     *                       (see {@code BasisFunctionBicScore.embeddingBlockSizes()}). Length is p.
     * @param sampleSize     N, the sample size the penalty will use.
     * @param expectedDegree A prior guess at the average degree of the true graph; positive.
     * @param fdr            The tolerated ratio of spurious to true edges, in (0, 1).
     * @param minEffect      The smallest per-parameter partial correlation worth an edge, in [0, 1); 0 disables
     *                       the effect-size criterion.
     * @param fits           Permutation-fitted nulls per df class, or null to use the exact chi-square null. Pass
     *                       fits for a Basis Function score on the min-max embedding, whose null is not
     *                       chi-square; see {@link PenaltyDiscountCalibration#fitNullsByPermutation}.
     */
    public PenaltyDiscountReport(int[] blockSizes, int sampleSize, double expectedDegree, double fdr,
                                 double minEffect, Map<Integer, PenaltyDiscountCalibration.NullFit> fits) {
        if (blockSizes == null || blockSizes.length < 2) {
            throw new IllegalArgumentException("Need at least two variables.");
        }
        if (sampleSize < 2) throw new IllegalArgumentException("sampleSize must be at least 2: " + sampleSize);
        if (!(expectedDegree > 0)) {
            throw new IllegalArgumentException("expectedDegree must be positive: " + expectedDegree);
        }
        if (!(fdr > 0 && fdr < 1)) throw new IllegalArgumentException("fdr must be in (0, 1): " + fdr);
        if (!(minEffect >= 0 && minEffect < 1)) {
            throw new IllegalArgumentException("minEffect must be in [0, 1): " + minEffect);
        }

        this.numVariables = blockSizes.length;
        this.sampleSize = sampleSize;
        this.expectedDegree = expectedDegree;
        this.fdr = fdr;
        this.minEffect = minEffect;
        this.dfPairCounts = PenaltyDiscountCalibration.pairDofHistogram(blockSizes);
        this.fits = fits;

        this.cFdr = fits == null
                ? PenaltyDiscountCalibration.penaltyDiscountForFalseDiscoveryRate(
                this.dfPairCounts, sampleSize, this.numVariables, expectedDegree, fdr)
                : PenaltyDiscountCalibration.penaltyDiscountForFalseDiscoveryRateFitted(
                fits, sampleSize, this.numVariables, expectedDegree, fdr);

        this.cEffect = minEffect > 0
                ? PenaltyDiscountCalibration.penaltyDiscountForMinPartialCorrelation(minEffect, sampleSize) : 0.0;
    }

    /**
     * The penalty discount from the false-discovery criterion alone.
     *
     * @return c.
     */
    public double getPenaltyDiscountFromFdr() {
        return this.cFdr;
    }

    /**
     * The penalty discount from the effect-size criterion alone, or 0 when {@code minEffect} is 0.
     *
     * @return c.
     */
    public double getPenaltyDiscountFromMinEffect() {
        return this.cEffect;
    }

    /**
     * The recommended penalty discount: the larger of the two criteria.
     *
     * @return c.
     */
    public double getPenaltyDiscount() {
        return Math.max(this.cFdr, this.cEffect);
    }

    /**
     * The expected number of true edges the budget is a fraction of, p * expectedDegree / 2.
     *
     * @return The count.
     */
    public double getExpectedTrueEdges() {
        return this.numVariables * this.expectedDegree / 2.0;
    }

    /**
     * The df histogram: df to the number of null pairs with that df. Sums to p(p-1)/2.
     *
     * @return The histogram, in df order.
     */
    public Map<Integer, Long> getDfPairCounts() {
        return new LinkedHashMap<>(this.dfPairCounts);
    }

    /**
     * Whether the report used permutation-fitted nulls rather than the exact chi-square null.
     *
     * @return True if fitted.
     */
    public boolean isFitted() {
        return this.fits != null;
    }

    /**
     * The expected number of spurious edges over the whole search at a given penalty discount. This is the inverse
     * direction of the calculation: it says what a discount already in use is buying.
     *
     * @param penaltyDiscount c.
     * @return The expected count.
     */
    public double expectedFalseEdgesAt(double penaltyDiscount) {
        return this.fits == null
                ? PenaltyDiscountCalibration.expectedFalseEdges(penaltyDiscount, this.dfPairCounts, this.sampleSize)
                : PenaltyDiscountCalibration.expectedFalseEdgesFitted(penaltyDiscount, this.fits, this.sampleSize);
    }

    /**
     * The per-pair false-positive level for one df class at a given penalty discount.
     *
     * @param penaltyDiscount c.
     * @param df              The df class; must be a key of {@link #getDfPairCounts()}.
     * @return alpha.
     */
    public double alphaAt(double penaltyDiscount, int df) {
        return this.fits == null
                ? PenaltyDiscountCalibration.alpha(penaltyDiscount, df, this.sampleSize)
                : PenaltyDiscountCalibration.alpha(penaltyDiscount, df, this.fits.get(df), this.sampleSize);
    }

    /**
     * Everything the report knows about one penalty discount, for the inverse direction and for the sweep.
     *
     * @param penaltyDiscount c.
     * @return The row.
     */
    public Row rowAt(double penaltyDiscount) {
        int smallestDf = Integer.MAX_VALUE, largestDf = 1;

        for (int df : this.dfPairCounts.keySet()) {
            smallestDf = Math.min(smallestDf, df);
            largestDf = Math.max(largestDf, df);
        }

        double expected = expectedFalseEdgesAt(penaltyDiscount);

        return new Row(penaltyDiscount, expected, expected / getExpectedTrueEdges(),
                SemBicScore.minDetectablePartialCorrelation(penaltyDiscount, this.sampleSize),
                alphaAt(penaltyDiscount, smallestDf), alphaAt(penaltyDiscount, largestDf));
    }

    /**
     * A sweep of {@link #rowAt(double)} over a range of penalty discounts, for seeing where the false-discovery
     * and effect-size criteria cross.
     *
     * @param lo   The smallest discount, positive.
     * @param hi   The largest discount, at least lo.
     * @param step The increment, positive.
     * @return The rows, lo first.
     */
    public List<Row> sweep(double lo, double hi, double step) {
        if (!(lo > 0)) throw new IllegalArgumentException("lo must be positive: " + lo);
        if (!(hi >= lo)) throw new IllegalArgumentException("hi must be at least lo: " + hi);
        if (!(step > 0)) throw new IllegalArgumentException("step must be positive: " + step);

        List<Row> rows = new ArrayList<>();

        for (double c = lo; c <= hi + 1e-9; c += step) {
            rows.add(rowAt(c));
        }

        return rows;
    }

    /**
     * A plain-text summary: the inputs, the two criteria, the chosen discount, and the per-class levels at it.
     *
     * @return The report.
     */
    public String report() {
        StringBuilder b = new StringBuilder();
        double c = getPenaltyDiscount();

        b.append(String.format("p = %d, N = %d, expected degree = %.2f, target FDR = %.4f%n",
                this.numVariables, this.sampleSize, this.expectedDegree, this.fdr));
        b.append(String.format("Expected true edges (p * degree / 2) = %.1f; spurious-edge budget = %.3f%n%n",
                getExpectedTrueEdges(), this.fdr * getExpectedTrueEdges()));

        b.append(String.format("False-discovery criterion:  c = %.4f%n", this.cFdr));

        if (this.minEffect > 0) {
            b.append(String.format("Effect-size criterion (r >= %.3f):  c = %.4f%n", this.minEffect, this.cEffect));
            b.append(String.format("Recommended (the larger):  c = %.4f%n", c));
        } else {
            b.append("Effect-size criterion: disabled (minimum partial correlation = 0)\n");
            b.append(String.format("Recommended:  c = %.4f%n", c));
        }

        b.append(String.format("%nAt c = %.4f: expected spurious edges = %.3f; smallest per-parameter partial "
                               + "correlation accepted = %.4f%n", c, expectedFalseEdgesAt(c),
                SemBicScore.minDetectablePartialCorrelation(c, this.sampleSize)));

        b.append(String.format("%nPer-pair levels at c = %.4f, by degrees of freedom:%n", c));
        b.append(String.format("  %8s  %10s  %14s  %14s%n", "df", "pairs", "alpha", "expected"));

        for (Map.Entry<Integer, Long> e : this.dfPairCounts.entrySet()) {
            double alpha = alphaAt(c, e.getKey());
            b.append(String.format("  %8d  %10d  %14.3e  %14.4f%n",
                    e.getKey(), e.getValue(), alpha, e.getValue() * alpha));
        }

        if (this.fits != null) {
            b.append("\nNulls were fitted by permutation (scale * chi-square, matched on two moments):\n");

            for (Map.Entry<Integer, PenaltyDiscountCalibration.NullFit> e : this.fits.entrySet()) {
                b.append(String.format("  df = %d: %.3f * chi-square(%.2f)%n",
                        e.getKey(), e.getValue().kappa(), e.getValue().nu()));
            }
        }

        return b.toString();
    }
}
