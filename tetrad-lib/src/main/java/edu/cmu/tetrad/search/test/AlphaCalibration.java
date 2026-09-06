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
import org.apache.commons.math3.special.Gamma;

/**
 * Chooses a significance level for a constraint-based search the way
 * {@link edu.cmu.tetrad.search.score.PenaltyDiscountCalibration} chooses a penalty discount for a score: from a
 * budget on the expected number of spurious edges over the whole search, with an effect-size criterion alongside
 * it. Everything here is a first-moment calculation on the back of an envelope, and the caveats are as important
 * as the formulas.
 *
 * <p><b>The false-edge criterion, which is test-agnostic.</b> In PC and its relatives an adjacency between x and
 * y survives only if <i>every</i> conditioning set tried rejects independence. If x and y are non-adjacent in the
 * true graph, some tried set d-separates them (assuming the search reaches it before its depth cap), and that
 * test rejects with probability alpha for any test running at its nominal level. So the probability that a null
 * pair ends up adjacent is at most alpha, and by linearity of expectation the expected number of spurious
 * adjacencies over the search is at most M alpha, with M the number of null pairs. Setting M alpha equal to a
 * budget and solving is a division:</p>
 *
 * <pre>    alpha = budget / M,  and with a fractional budget,  alpha ~ fdr * degree / (p - 1).</pre>
 *
 * <p>This does not involve N -- N enters only through power -- and it is an upper bound rather than an estimate,
 * because a null pair is usually tested against several separating sets and needs only one to accept. The
 * realized count typically runs below the budget, which is the safe direction to be wrong in.</p>
 *
 * <p><b>The effect-size criterion, for the likelihood-ratio family.</b> Power is computed for any test whose
 * statistic is asymptotically chi-square on df degrees of freedom under the null and noncentral chi-square under
 * the alternative: Fisher z (df = 1, as z-squared), the conditional Gaussian and Degenerate Gaussian LRTs
 * (df from category counts), and the Basis Function LRT (df = the product of the two variables' embedding block
 * sizes), i.e. the same family whose penalty discount {@link PenaltyDiscountCalibration} calibrates, and with the
 * same df. A linear dependence of partial correlation r delivers noncentrality</p>
 *
 * <pre>    lambda = -(N - |S| - 3) ln(1 - r^2)</pre>
 *
 * <p>into ONE component of the block, while the null is charged for all df of them. That is the mechanism behind
 * the Basis Function score's conservatism on weak linear edges, seen from the test side: at fixed alpha, N and r,
 * power falls as df grows, so on mixed data a single level tests different variable-type pairs at very different
 * power, exactly as a single penalty discount tests them at different levels. The N - |S| - 3 is the Fisher z
 * effective sample size, applied uniformly; for the other tests it is a small conservative adjustment in the
 * direction the Bartlett corrections already push.</p>
 *
 * <p>Not covered: the kernel and random-feature tests (KCI, GCM, the RFF tests), whose null distributions are not
 * chi-square on a df read from the data. The false-edge criterion still applies to them; the power side does
 * not, and callers should say so rather than report a Fisher z number in their name.</p>
 *
 * <p><b>Why the criteria conflict here.</b> For a score, controlling false positives and ignoring small effects
 * both push the penalty discount the same way, so the two criteria combine by taking the larger. For a test they
 * pull in opposite directions: a smaller alpha buys fewer spurious edges and less power, and there is no value of
 * alpha that is conservative on both counts. Report both and leave the trade to the user, flagging the case where
 * the alpha the budget allows is too small to detect the effect asked for at the requested power, which is a
 * statement about N rather than about alpha.</p>
 *
 * <p><b>Multiplicity on the recall side.</b> The bound above says multiplicity across conditioning sets helps
 * precision. It hurts recall in the same measure: a true edge is removed if <i>any</i> of the sets tried fails to
 * reject, so the number of sets tried per pair, {@link #testsPerPair(double, int)}, multiplies the per-test miss
 * rate. That is the real cost of running at a large depth.</p>
 *
 * @author josephramsey
 * @see PenaltyDiscountCalibration
 */
public final class AlphaCalibration {

    private AlphaCalibration() {
    }

    //====================== False-edge criterion (test-agnostic) ======================//

    /**
     * The number of pairs that are non-adjacent in the true graph, p(p-1)/2 minus the expected number of true
     * edges. These are the pairs that can become spurious adjacencies.
     *
     * @param numVariables   p, at least 2.
     * @param expectedDegree A prior guess at the average degree; positive.
     * @return The count, at least 1.
     */
    public static double numNullPairs(int numVariables, double expectedDegree) {
        if (numVariables < 2) throw new IllegalArgumentException("numVariables must be at least 2: " + numVariables);
        if (!(expectedDegree > 0)) {
            throw new IllegalArgumentException("expectedDegree must be positive: " + expectedDegree);
        }
        double pairs = numVariables * (numVariables - 1) / 2.0;
        return Math.max(1.0, pairs - numVariables * expectedDegree / 2.0);
    }

    /**
     * The level at which the expected number of spurious adjacencies is at most {@code expectedFalseEdges}.
     *
     * @param numVariables       p.
     * @param expectedDegree     A prior guess at the average degree.
     * @param expectedFalseEdges The budget, positive.
     * @return alpha, capped below 1.
     */
    public static double alphaForExpectedFalseEdges(int numVariables, double expectedDegree,
                                                    double expectedFalseEdges) {
        if (!(expectedFalseEdges > 0)) {
            throw new IllegalArgumentException("budget must be positive: " + expectedFalseEdges);
        }
        return Math.min(1.0 - 1e-12, expectedFalseEdges / numNullPairs(numVariables, expectedDegree));
    }

    /**
     * The level at which the expected number of spurious adjacencies is {@code fdr} times the expected number of
     * true edges, p * expectedDegree / 2. For a large sparse graph this is approximately
     * {@code fdr * expectedDegree / (p - 1)}.
     *
     * @param numVariables   p.
     * @param expectedDegree A prior guess at the average degree.
     * @param fdr            The tolerated ratio of spurious to true edges, in (0, 1).
     * @return alpha.
     */
    public static double alphaForFalseDiscoveryRate(int numVariables, double expectedDegree, double fdr) {
        if (!(fdr > 0 && fdr < 1)) throw new IllegalArgumentException("fdr must be in (0, 1): " + fdr);
        return alphaForExpectedFalseEdges(numVariables, expectedDegree, fdr * numVariables * expectedDegree / 2.0);
    }

    /**
     * The expected number of spurious adjacencies at a given level: M alpha. The inverse direction, for reading a
     * level already in use.
     *
     * @param numVariables   p.
     * @param expectedDegree A prior guess at the average degree.
     * @param alpha          The level.
     * @return The expected count, an upper bound.
     */
    public static double expectedFalseEdges(int numVariables, double expectedDegree, double alpha) {
        return numNullPairs(numVariables, expectedDegree) * alpha;
    }

    //====================== Noncentral chi-square ======================//

    /**
     * P(chi-square(df, lambda) &gt; x) for the noncentral chi-square, by the Poisson mixture
     * sum over j of Poisson(j; lambda/2) * P(chi-square(df + 2j) &gt; x). Terms are accumulated in weight order
     * around the Poisson mode with log-space weights, so the sum is accurate for large noncentrality and far into
     * the tail. Exact up to truncation; the truncation error is below 1e-12 in total weight.
     *
     * @param df     Degrees of freedom, at least 1.
     * @param lambda Noncentrality, nonnegative.
     * @param x      The threshold.
     * @return The upper tail probability.
     */
    public static double noncentralChiSquareUpperTail(int df, double lambda, double x) {
        if (df < 1) throw new IllegalArgumentException("df must be at least 1: " + df);
        if (!(lambda >= 0)) throw new IllegalArgumentException("lambda must be nonnegative: " + lambda);
        if (!(x > 0)) return 1.0;
        if (lambda == 0) return Gamma.regularizedGammaQ(df / 2.0, x / 2.0);

        double half = lambda / 2.0;
        double logHalf = Math.log(half);
        int mode = (int) Math.floor(half);
        double logMax = -half + mode * logHalf - Gamma.logGamma(mode + 1.0);

        double total = 0.0;

        // Upward from the mode, then downward, stopping when the weight has fallen 40 nats below the peak.
        for (int j = mode; j < mode + 100000; j++) {
            double logW = -half + j * logHalf - Gamma.logGamma(j + 1.0);
            if (logW < logMax - 40) break;
            total += Math.exp(logW) * Gamma.regularizedGammaQ((df + 2.0 * j) / 2.0, x / 2.0);
        }

        for (int j = mode - 1; j >= 0; j--) {
            double logW = -half + j * logHalf - Gamma.logGamma(j + 1.0);
            if (logW < logMax - 40) break;
            total += Math.exp(logW) * Gamma.regularizedGammaQ((df + 2.0 * j) / 2.0, x / 2.0);
        }

        return Math.min(1.0, total);
    }

    /**
     * The upper-tail quantile of the central chi-square(df), i.e. the x with P(chi-square(df) &gt; x) = alpha, by
     * bisection on the regularized gamma function so that it is exactly consistent with
     * {@link PenaltyDiscountCalibration#alpha(double, int, int)} far into the tail.
     *
     * @param df    Degrees of freedom.
     * @param alpha The tail probability, in (0, 1).
     * @return The quantile.
     */
    public static double chiSquareUpperQuantile(int df, double alpha) {
        if (!(alpha > 0 && alpha < 1)) throw new IllegalArgumentException("alpha must be in (0, 1): " + alpha);
        double lo = 0.0, hi = Math.max(1.0, df);
        while (Gamma.regularizedGammaQ(df / 2.0, hi / 2.0) > alpha) {
            hi *= 2.0;
            if (hi > 1e9) throw new IllegalStateException("could not bracket the quantile");
        }
        for (int it = 0; it < 200 && hi - lo > 1e-10 * Math.max(1.0, hi); it++) {
            double mid = 0.5 * (lo + hi);
            if (Gamma.regularizedGammaQ(df / 2.0, mid / 2.0) > alpha) lo = mid;
            else hi = mid;
        }
        return 0.5 * (lo + hi);
    }

    //====================== Effect-size criterion (LRT family) ======================//

    /**
     * The effective sample size of the test statistic, N - |S| - 3.
     */
    private static double effectiveN(int sampleSize, int conditioningSetSize) {
        if (conditioningSetSize < 0) {
            throw new IllegalArgumentException("conditioning set size must be nonnegative: " + conditioningSetSize);
        }
        double d = sampleSize - conditioningSetSize - 3.0;
        if (!(d > 0)) {
            throw new IllegalArgumentException("Sample size too small for a conditioning set of size "
                                               + conditioningSetSize + ": N - |S| - 3 = " + d);
        }
        return d;
    }

    /**
     * The noncentrality a linear dependence of partial correlation r delivers to the likelihood-ratio statistic,
     * -(N - |S| - 3) ln(1 - r^2). This is the same quantity the score side's effect criterion uses; see
     * {@link PenaltyDiscountCalibration#penaltyDiscountForMinPartialCorrelation(double, int)}.
     *
     * @param partialCorrelation  r, in [0, 1).
     * @param sampleSize          N.
     * @param conditioningSetSize |S|.
     * @return lambda.
     */
    public static double noncentrality(double partialCorrelation, int sampleSize, int conditioningSetSize) {
        if (!(partialCorrelation >= 0 && partialCorrelation < 1)) {
            throw new IllegalArgumentException("partial correlation must be in [0, 1): " + partialCorrelation);
        }
        return -effectiveN(sampleSize, conditioningSetSize) * Math.log1p(-partialCorrelation * partialCorrelation);
    }

    /**
     * The inverse of {@link #noncentrality(double, int, int)}: r = sqrt(1 - exp(-lambda / (N - |S| - 3))).
     *
     * @param lambda              Noncentrality, nonnegative.
     * @param sampleSize          N.
     * @param conditioningSetSize |S|.
     * @return r.
     */
    public static double partialCorrelationForNoncentrality(double lambda, int sampleSize,
                                                            int conditioningSetSize) {
        return Math.sqrt(1.0 - Math.exp(-lambda / effectiveN(sampleSize, conditioningSetSize)));
    }

    /**
     * The power of a level-alpha test with a chi-square(df) null against noncentrality lambda.
     *
     * @param alpha  The level, in (0, 1).
     * @param df     Degrees of freedom of the test.
     * @param lambda Noncentrality.
     * @return P(chi-square(df, lambda) &gt; Q_chi2(df)(1 - alpha)).
     */
    public static double power(double alpha, int df, double lambda) {
        return noncentralChiSquareUpperTail(df, lambda, chiSquareUpperQuantile(df, alpha));
    }

    /**
     * The smallest partial correlation a df-degree-of-freedom test detects at level alpha with the requested power,
     * for a linear dependence carried by one component of the block. Found by bisection on the noncentrality,
     * since power is increasing in it.
     *
     * @param alpha               The level, in (0, 1).
     * @param df                  Degrees of freedom of the test, at least 1.
     * @param sampleSize          N.
     * @param conditioningSetSize |S|.
     * @param targetPower         The target power, in (0, 1).
     * @return r.
     */
    public static double minDetectablePartialCorrelation(double alpha, int df, int sampleSize,
                                                         int conditioningSetSize, double targetPower) {
        if (!(alpha > 0 && alpha < 1)) throw new IllegalArgumentException("alpha must be in (0, 1): " + alpha);
        if (!(targetPower > 0 && targetPower < 1)) {
            throw new IllegalArgumentException("power must be in (0, 1): " + targetPower);
        }
        double q = chiSquareUpperQuantile(df, alpha);
        double lo = 0.0, hi = 1.0;
        while (noncentralChiSquareUpperTail(df, hi, q) < targetPower) {
            hi *= 2.0;
            if (hi > 1e9) throw new IllegalStateException("could not bracket the noncentrality");
        }
        for (int it = 0; it < 200 && hi - lo > 1e-9 * Math.max(1.0, hi); it++) {
            double mid = 0.5 * (lo + hi);
            if (noncentralChiSquareUpperTail(df, mid, q) < targetPower) lo = mid;
            else hi = mid;
        }
        return partialCorrelationForNoncentrality(0.5 * (lo + hi), sampleSize, conditioningSetSize);
    }

    /**
     * The level at which a df-degree-of-freedom test attains the requested power against a partial correlation of
     * {@code minPartialCorrelation}. The inverse of
     * {@link #minDetectablePartialCorrelation(double, int, int, int, double)}. Power is increasing in alpha and
     * reaches one as alpha approaches one, so a level always exists; a returned value above about 0.1 means the
     * effect is not practically detectable at this N, and the caller should say so.
     *
     * @param minPartialCorrelation r, in (0, 1).
     * @param df                    Degrees of freedom of the test.
     * @param sampleSize            N.
     * @param conditioningSetSize   |S|.
     * @param targetPower           The target power, in (0, 1).
     * @return alpha, in (0, 1).
     */
    public static double alphaForMinPartialCorrelation(double minPartialCorrelation, int df, int sampleSize,
                                                       int conditioningSetSize, double targetPower) {
        if (!(minPartialCorrelation > 0 && minPartialCorrelation < 1)) {
            throw new IllegalArgumentException("minPartialCorrelation must be in (0, 1): " + minPartialCorrelation);
        }
        if (!(targetPower > 0 && targetPower < 1)) {
            throw new IllegalArgumentException("power must be in (0, 1): " + targetPower);
        }
        double lambda = noncentrality(minPartialCorrelation, sampleSize, conditioningSetSize);
        double lo = 1e-15, hi = 1.0 - 1e-12;
        if (power(lo, df, lambda) >= targetPower) return lo;
        for (int it = 0; it < 300 && hi / lo > 1.0 + 1e-9; it++) {
            double mid = Math.sqrt(lo * hi);  // geometric bisection: alpha spans many decades
            if (power(mid, df, lambda) < targetPower) lo = mid;
            else hi = mid;
        }
        return Math.sqrt(lo * hi);
    }

    //====================== Multiplicity and the bridge to the penalty discount ======================//

    /**
     * The number of conditioning sets tried per adjacent pair up to a given depth, sum over k of C(a, k) for k = 0
     * to depth, with a the size of the adjacency set the sets are drawn from. This is the multiplicity that costs
     * recall: a true edge is removed if any one of these tests fails to reject.
     *
     * @param adjacencySetSize a, the number of other variables adjacent to one endpoint; the expected degree is a
     *                         reasonable stand-in.
     * @param depth            The conditioning-set size cap; a negative value means uncapped, taken as a.
     * @return The number of sets, at least 1.
     */
    public static double testsPerPair(double adjacencySetSize, int depth) {
        double a = Math.max(0.0, adjacencySetSize);
        int cap = depth < 0 ? (int) Math.round(a) : (int) Math.min(depth, Math.round(a));
        double total = 0.0;
        double term = 1.0;  // C(a, 0)

        for (int k = 0; k <= cap; k++) {
            if (k > 0) term *= (a - (k - 1)) / k;
            total += Math.max(0.0, term);
        }

        return Math.max(1.0, total);
    }

    /**
     * An upper bound on the probability that a true edge is removed, given a per-test probability of failing to
     * reject and the number of sets tried: 1 - (1 - beta)^T. The tests share data and are strongly dependent, so
     * this overstates the loss, sometimes badly, and it saturates at one quickly; the expected number of failing
     * tests, beta * T, stays informative where this does not.
     *
     * @param perTestMissRate beta, in [0, 1).
     * @param testsPerPair    T, at least 1.
     * @return The bound, in [0, 1).
     */
    public static double trueEdgeLossBound(double perTestMissRate, double testsPerPair) {
        if (!(perTestMissRate >= 0 && perTestMissRate < 1)) {
            throw new IllegalArgumentException("miss rate must be in [0, 1): " + perTestMissRate);
        }
        return 1.0 - Math.pow(1.0 - perTestMissRate, Math.max(1.0, testsPerPair));
    }

    /**
     * The penalty discount whose one-degree-of-freedom acceptance threshold sits at the same level, so that a
     * score-based and a test-based search are being run at comparable strictness per pair:
     * c = Q_chi2(1)(1 - alpha) / ln N.
     *
     * @param alpha      The level.
     * @param sampleSize N.
     * @return c.
     */
    public static double equivalentPenaltyDiscount(double alpha, int sampleSize) {
        return PenaltyDiscountCalibration.penaltyDiscountForAlpha(alpha, 1, sampleSize);
    }

    /**
     * The level at which a penalty discount's threshold sits, the inverse of
     * {@link #equivalentPenaltyDiscount(double, int)}: alpha = P(chi-square(1) &gt; c ln N).
     *
     * @param penaltyDiscount c.
     * @param sampleSize      N.
     * @return alpha.
     */
    public static double equivalentAlpha(double penaltyDiscount, int sampleSize) {
        return PenaltyDiscountCalibration.alpha(penaltyDiscount, 1, sampleSize);
    }
}
