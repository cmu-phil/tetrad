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

package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.search.score.PenaltyDiscountCalibration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A calibrated significance level together with what it costs on the recall side, per degrees-of-freedom class:
 * the level the false-edge budget allows, the smallest partial correlation each class of pair can then detect,
 * the level each class would need to detect a requested effect, whether the budget's level suffices for the
 * hardest class, the number of conditioning sets tried per pair, and the equivalent penalty discount.
 *
 * <p>This is the constraint-based counterpart of {@link edu.cmu.tetrad.search.score.PenaltyDiscountReport}, built
 * over the same block-size vector and the same df histogram: one parameter-block size per variable, a pair
 * costing the product. For Fisher z every size is one; for the conditional Gaussian and Degenerate Gaussian LRTs
 * a discrete variable's size is its categories minus one; for the Basis Function LRT it is the embedding block
 * size. It is a presentation layer over {@link AlphaCalibration} and performs no statistics of its own.</p>
 *
 * <p>The one structural difference from the score side is that the two criteria conflict here rather than
 * combining. A smaller alpha buys fewer spurious edges and less power at the same time, so there is no "take the
 * larger" rule; the report states both and says whether the budget's level is large enough for the requested
 * detection in every df class, which is a question about the sample size.</p>
 *
 * @author josephramsey
 * @see AlphaCalibration
 */
public final class AlphaReport {

    /**
     * The df histogram: df to number of null pairs.
     */
    private final Map<Integer, Long> dfPairCounts;

    /**
     * The number of variables.
     */
    private final int numVariables;

    /**
     * The sample size.
     */
    private final int sampleSize;

    /**
     * The assumed average degree.
     */
    private final double expectedDegree;

    /**
     * The tolerated ratio of spurious to true edges.
     */
    private final double fdr;

    /**
     * The conditioning-set size at which power is evaluated.
     */
    private final int conditioningSetSize;

    /**
     * The target power.
     */
    private final double power;

    /**
     * The smallest partial correlation worth an edge, or 0 to disable the effect criterion.
     */
    private final double minEffect;

    /**
     * The conditioning-set size cap; negative means uncapped.
     */
    private final int depth;

    /**
     * The level the false-edge budget allows.
     */
    private final double alphaFdr;

    /**
     * Per df class, the level that class needs to detect the requested effect; empty when disabled.
     */
    private final Map<Integer, Double> alphaNeededByDf;

    /**
     * One row of the sweep: a level and what it implies.
     *
     * @param alpha              The level.
     * @param expectedFalseEdges Upper bound on spurious adjacencies over the whole search.
     * @param falseToTrueRatio   That count over the expected number of true edges.
     * @param minDetectableRMinDf Smallest partial correlation detected in the smallest df class.
     * @param minDetectableRMaxDf Smallest partial correlation detected in the largest df class.
     * @param equivalentPenalty  The penalty discount sitting at the same threshold.
     */
    public record Row(double alpha, double expectedFalseEdges, double falseToTrueRatio, double minDetectableRMinDf,
                      double minDetectableRMaxDf, double equivalentPenalty) {
    }

    /**
     * Builds a report.
     *
     * @param blockSizes          One parameter-block size per variable; length is p, at least 2.
     * @param sampleSize          N, at least 4.
     * @param expectedDegree      A prior guess at the average degree; positive.
     * @param fdr                 The tolerated ratio of spurious to true edges, in (0, 1).
     * @param conditioningSetSize The |S| at which power is evaluated; a typical separating set size, not the cap.
     * @param power               The target power, in (0, 1).
     * @param minEffect           The smallest partial correlation worth detecting, in [0, 1); 0 disables the
     *                            effect criterion.
     * @param depth               The search's conditioning-set size cap, for the multiplicity count; negative
     *                            means uncapped.
     */
    public AlphaReport(int[] blockSizes, int sampleSize, double expectedDegree, double fdr, int conditioningSetSize,
                       double power, double minEffect, int depth) {
        if (blockSizes == null || blockSizes.length < 2) {
            throw new IllegalArgumentException("Need at least two variables.");
        }
        if (sampleSize < 4) throw new IllegalArgumentException("sampleSize must be at least 4: " + sampleSize);
        if (!(power > 0 && power < 1)) throw new IllegalArgumentException("power must be in (0, 1): " + power);
        if (!(minEffect >= 0 && minEffect < 1)) {
            throw new IllegalArgumentException("minEffect must be in [0, 1): " + minEffect);
        }

        this.numVariables = blockSizes.length;
        this.sampleSize = sampleSize;
        this.expectedDegree = expectedDegree;
        this.fdr = fdr;
        this.conditioningSetSize = conditioningSetSize;
        this.power = power;
        this.minEffect = minEffect;
        this.depth = depth;
        this.dfPairCounts = PenaltyDiscountCalibration.pairDofHistogram(blockSizes);

        this.alphaFdr = AlphaCalibration.alphaForFalseDiscoveryRate(this.numVariables, expectedDegree, fdr);

        this.alphaNeededByDf = new LinkedHashMap<>();
        if (minEffect > 0) {
            for (int df : this.dfPairCounts.keySet()) {
                this.alphaNeededByDf.put(df, AlphaCalibration.alphaForMinPartialCorrelation(
                        minEffect, df, sampleSize, conditioningSetSize, power));
            }
        }
    }

    /**
     * The level the false-edge budget allows.
     *
     * @return alpha.
     */
    public double getAlphaFromFdr() {
        return this.alphaFdr;
    }

    /**
     * The level the effect-size criterion demands, taken over the hardest df class (the one needing the largest
     * level), or 0 when disabled.
     *
     * @return alpha.
     */
    public double getAlphaFromMinEffect() {
        double max = 0.0;
        for (double a : this.alphaNeededByDf.values()) max = Math.max(max, a);
        return max;
    }

    /**
     * The level each df class needs to detect the requested effect at the requested power. Empty when the effect
     * criterion is disabled.
     *
     * @return Map from df to alpha, in df order.
     */
    public Map<Integer, Double> getAlphaNeededByDf() {
        return new LinkedHashMap<>(this.alphaNeededByDf);
    }

    /**
     * Whether the budget's level is large enough to detect the requested effect at the requested power in every df
     * class. When false, the shortfall is in the sample size, not in the level.
     *
     * @return True when compatible, and true when the effect criterion is disabled.
     */
    public boolean isCompatible() {
        return !(this.minEffect > 0) || this.alphaFdr >= getAlphaFromMinEffect();
    }

    /**
     * The expected number of true edges, p * expectedDegree / 2.
     *
     * @return The count.
     */
    public double getExpectedTrueEdges() {
        return this.numVariables * this.expectedDegree / 2.0;
    }

    /**
     * The df histogram: df to the number of null pairs with that df.
     *
     * @return The histogram, in df order.
     */
    public Map<Integer, Long> getDfPairCounts() {
        return new LinkedHashMap<>(this.dfPairCounts);
    }

    /**
     * The number of conditioning sets tried per adjacent pair at the stated depth.
     *
     * @return The count.
     */
    public double getTestsPerPair() {
        return AlphaCalibration.testsPerPair(this.expectedDegree, this.depth);
    }

    /**
     * The smallest partial correlation a pair in the given df class detects at a level, at the report's power.
     *
     * @param alpha The level.
     * @param df    The df class.
     * @return r.
     */
    public double minDetectableRAt(double alpha, int df) {
        return AlphaCalibration.minDetectablePartialCorrelation(alpha, df, this.sampleSize,
                this.conditioningSetSize, this.power);
    }

    /**
     * Everything the report knows about one level.
     *
     * @param alpha The level.
     * @return The row.
     */
    public Row rowAt(double alpha) {
        int smallestDf = Integer.MAX_VALUE, largestDf = 1;
        for (int df : this.dfPairCounts.keySet()) {
            smallestDf = Math.min(smallestDf, df);
            largestDf = Math.max(largestDf, df);
        }

        double expected = AlphaCalibration.expectedFalseEdges(this.numVariables, this.expectedDegree, alpha);

        return new Row(alpha, expected, expected / getExpectedTrueEdges(),
                minDetectableRAt(alpha, smallestDf), minDetectableRAt(alpha, largestDf),
                AlphaCalibration.equivalentPenaltyDiscount(alpha, this.sampleSize));
    }

    /**
     * A sweep over the levels usually reached for, in decades and half decades from 0.1 down to 1e-6, plus the
     * calibrated levels themselves so that they are always on screen.
     *
     * @return The rows, largest level first.
     */
    public List<Row> sweep() {
        double[] alphas = {0.1, 0.05, 0.02, 0.01, 0.005, 0.002, 0.001, 5e-4, 2e-4, 1e-4, 1e-5, 1e-6};
        List<Double> levels = new ArrayList<>();

        for (double a : alphas) levels.add(a);
        levels.add(this.alphaFdr);
        double effect = getAlphaFromMinEffect();
        if (effect > 0 && effect < 1) levels.add(effect);

        levels.sort((a, b) -> Double.compare(b, a));

        List<Row> rows = new ArrayList<>();
        double last = Double.NaN;

        for (double a : levels) {
            if (a <= 0 || a >= 1) continue;
            if (!Double.isNaN(last) && Math.abs(a - last) < 1e-12) continue;
            rows.add(rowAt(a));
            last = a;
        }

        return rows;
    }

    /**
     * A plain-text summary.
     *
     * @return The report.
     */
    public String report() {
        StringBuilder b = new StringBuilder();

        b.append(String.format("p = %d, N = %d, expected degree = %.2f, target FDR = %.4f%n",
                this.numVariables, this.sampleSize, this.expectedDegree, this.fdr));
        b.append(String.format("Null pairs = %.0f; expected true edges = %.1f; spurious-edge budget = %.3f%n%n",
                AlphaCalibration.numNullPairs(this.numVariables, this.expectedDegree),
                getExpectedTrueEdges(), this.fdr * getExpectedTrueEdges()));

        b.append(String.format("False-edge criterion:  alpha = %.3e%n", this.alphaFdr));
        b.append("  (test-agnostic, and an upper bound: a null pair is usually tested against several\n");
        b.append("   separating sets and needs only one to accept, so the realized count runs below the budget)\n");
        b.append(String.format("  Equivalent SEM BIC penalty discount at this N:  c = %.3f%n",
                AlphaCalibration.equivalentPenaltyDiscount(this.alphaFdr, this.sampleSize)));

        b.append(String.format("%nDetection at alpha = %.3e, |S| = %d, power %.2f, by degrees of freedom of the "
                               + "pair's test:%n", this.alphaFdr, this.conditioningSetSize, this.power));
        b.append(String.format("  %8s  %10s  %16s", "df", "pairs", "smallest r seen"));
        if (this.minEffect > 0) b.append(String.format("  %22s", "alpha needed for r_min"));
        b.append(String.format("%n"));

        for (Map.Entry<Integer, Long> e : this.dfPairCounts.entrySet()) {
            b.append(String.format("  %8d  %10d  %16.4f", e.getKey(), e.getValue(),
                    minDetectableRAt(this.alphaFdr, e.getKey())));
            if (this.minEffect > 0) b.append(String.format("  %22.3e", this.alphaNeededByDf.get(e.getKey())));
            b.append(String.format("%n"));
        }

        if (this.dfPairCounts.size() > 1) {
            b.append("  A linear effect lands in one component of a block the null is charged all df for, so\n");
            b.append("  higher-df pairs need a larger effect at the same level. One alpha tests the classes at\n");
            b.append("  different power, as one penalty discount tests them at different levels.\n");
        }

        if (this.minEffect > 0) {
            double effect = getAlphaFromMinEffect();
            b.append(String.format("%nEffect-size criterion (detect r >= %.3f at power %.2f in every class):  "
                                   + "alpha = %.3e%n", this.minEffect, this.power, effect));

            if (effect > 0.1) {
                b.append("  That level is not a usable one: at this sample size the effect is not practically\n");
                b.append("  detectable at this power in the hardest class. The shortfall is in N, not in alpha.\n");
            }

            b.append(this.isCompatible()
                    ? "\nThe two criteria are compatible: the budget's level is large enough for the detection\n"
                      + "asked for. Any level between them satisfies both.\n"
                    : "\nThe two criteria CONFLICT: detecting the effect asked for needs a larger level than the\n"
                      + "false-edge budget allows. Unlike the score case there is no conservative choice -- a\n"
                      + "smaller alpha costs power and a larger one costs precision. Raise N, relax the budget,\n"
                      + "or accept the larger effect floor.\n");
        } else {
            b.append("\nEffect-size criterion: disabled (minimum partial correlation = 0)\n");
        }

        double tests = getTestsPerPair();
        b.append(String.format("%nMultiplicity: about %.0f conditioning sets tried per adjacent pair at depth %s%n",
                tests, this.depth < 0 ? "unlimited" : String.valueOf(this.depth)));
        b.append(String.format("  A true edge is removed if ANY of them fails to reject. At a per-test miss rate "
                               + "of 0.20%n  that is about %.1f failures per pair in expectation (bound on the "
                               + "removal probability: %.2f,%n  which saturates and stops being informative once "
                               + "the count exceeds a few). The bound is%n  loose because the tests share data, "
                               + "but the direction is not in doubt: multiplicity helps%n  precision and hurts "
                               + "recall in the same measure, and depth is not free.%n",
                0.20 * tests, AlphaCalibration.trueEdgeLossBound(0.20, tests)));

        return b.toString();
    }
}
