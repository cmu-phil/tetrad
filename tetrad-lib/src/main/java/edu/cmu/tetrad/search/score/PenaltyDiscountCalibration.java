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

package edu.cmu.tetrad.search.score;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.CholeskyDecomposition;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.special.Gamma;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import static org.apache.commons.math3.util.FastMath.log;

/**
 * Chooses a BIC penalty discount so that the expected number of spurious edges over a whole search meets a budget,
 * for any score whose gain from adding one parent has the form
 *
 * <pre>    LRT - c * df * ln(N)</pre>
 *
 * where the likelihood-ratio statistic is asymptotically chi-square on df degrees of freedom under the null. Every
 * penalty-discount score in Tetrad has this form (SEM BIC with df = 1; discrete BIC with df = (r_x - 1)(r_y - 1);
 * Degenerate Gaussian and Basis Function BIC with df = the product of the two variables' embedding block sizes).
 *
 * <p>The argument is the one in {@link SemBicScore#penaltyDiscountForExpectedFalseEdges(int, int, double)}, made
 * df-aware. A null pair with df degrees of freedom becomes a spurious edge with probability
 * alpha(c, df) = P(chi-square(df) &gt; c df ln N). In a sparse true graph nearly every one of the p(p-1)/2 pairs is
 * null, so by linearity of expectation the expected number of spurious edges is the sum over pairs of alpha at
 * that pair's df. This class takes that pair population as a histogram, df to count, and inverts the sum by
 * bisection.</p>
 *
 * <p>Why df matters: the threshold c df ln N scales with df, but chi-square(df)/df concentrates at 1 with relative
 * spread sqrt(2/df), so at fixed c the tail collapses as df grows. At c = 2, N = 1000 a df = 1 pair is accepted
 * under the null with probability 2e-4 and a df = 9 pair (Basis Function BIC, truncation 3, two continuous
 * variables) with probability 2e-22. The calibrated c for a df = 9 score is therefore far below the one for
 * SEM BIC, and a mixed-type score tests different variable-type pairs at very different levels for any single c.
 * This routine gets the total count right; it does not equalize the per-type levels.</p>
 *
 * <p>Usage. Build the histogram from the per-variable block sizes with {@link #pairDofHistogram(int[])} (all ones
 * for SEM BIC; embedding block sizes for BF/DG; categories minus one for discrete) and call
 * {@link #penaltyDiscountForFalseDiscoveryRate(Map, int, int, double, double)}.</p>
 *
 * <p>Caveat. The chi-square null is an asymptotic Gaussian result. Under misspecification, which is the usual
 * reason to reach for a non-Gaussian score, the LRT is typically inflated and the true alpha at a given c is higher
 * than computed here, so for such scores the returned c is a floor rather than a target. Validate empirically
 * before relying on it.</p>
 *
 * @author josephramsey
 */
public final class PenaltyDiscountCalibration {

    private PenaltyDiscountCalibration() {
    }

    /**
     * The per-pair false-positive probability P(chi-square(df) &gt; c df ln N), computed from the upper regularized
     * incomplete gamma function so that it stays accurate far into the tail (1 - CDF would round to zero).
     *
     * @param penaltyDiscount c.
     * @param df              Degrees of freedom of the test, at least 1.
     * @param sampleSize      N.
     * @return alpha, in [0, 1].
     */
    public static double alpha(double penaltyDiscount, int df, int sampleSize) {
        if (df < 1) throw new IllegalArgumentException("df must be at least 1: " + df);
        if (sampleSize < 2) throw new IllegalArgumentException("sampleSize must be at least 2: " + sampleSize);
        if (penaltyDiscount <= 0) return 1.0;
        double x = penaltyDiscount * df * log(sampleSize);
        return Gamma.regularizedGammaQ(df / 2.0, x / 2.0);
    }

    /**
     * The null distribution of the per-pair LRT for one degrees-of-freedom class, as a scaled chi-square
     * kappa * chi-square(nu) fitted to its first two moments (Satterthwaite), together with how many null pairs
     * belong to the class. The exact chi-square(df) null is the special case kappa = 1, nu = df.
     *
     * <p>Why a fitted null: for a score whose Gaussian likelihood is a working model rather than the truth -- Basis
     * Function BIC on any data, since the components of an embedded block are not jointly Gaussian even when the
     * variable is -- the LRT is a weighted sum of chi-square(1) variates, not chi-square(df). The weights sum to df,
     * so the mean is right, but their squares sum to more than df, so the variance and the tail are inflated.
     * Measured on Gaussian data at truncation 3 the null had mean 9.1 and variance 38.5 against chi-square(9)'s 9
     * and 18, and a calibration assuming chi-square(9) produced about a hundred times the budgeted false edges.
     * Matching two moments closes most of that gap; it can still underestimate the far tail when the weights are
     * very unequal, so a fitted calibration should be validated the same way an exact one would be.</p>
     *
     * @param pairs The number of null pairs in this class.
     * @param kappa The scale, var / (2 * mean).
     * @param nu    The effective degrees of freedom, 2 * mean^2 / var.
     */
    public record NullFit(long pairs, double kappa, double nu) {
        /**
         * The exact chi-square null for a class of the given df.
         *
         * @param pairs The number of pairs.
         * @param df    The degrees of freedom.
         * @return kappa = 1, nu = df.
         */
        public static NullFit exact(long pairs, int df) {
            return new NullFit(pairs, 1.0, df);
        }

        /**
         * Fits kappa and nu to a sample of null LRT values.
         *
         * @param pairs  The number of pairs in the class.
         * @param sample Null LRT values, at least 10.
         * @return The fit.
         */
        public static NullFit fromSample(long pairs, double[] sample) {
            if (sample.length < 10) throw new IllegalArgumentException("need at least 10 null samples");
            double mean = 0;
            for (double v : sample) mean += v;
            mean /= sample.length;
            double var = 0;
            for (double v : sample) var += (v - mean) * (v - mean);
            var /= (sample.length - 1);
            if (!(mean > 0) || !(var > 0)) throw new IllegalStateException("degenerate null sample");
            return new NullFit(pairs, var / (2.0 * mean), 2.0 * mean * mean / var);
        }
    }

    /**
     * P(kappa * chi-square(nu) &gt; c df ln N): the per-pair false-positive probability under a fitted null for a
     * class whose tests have df degrees of freedom in the penalty.
     *
     * @param penaltyDiscount c.
     * @param df              The df the penalty charges for this class.
     * @param fit             The fitted null.
     * @param sampleSize      N.
     * @return alpha.
     */
    public static double alpha(double penaltyDiscount, int df, NullFit fit, int sampleSize) {
        if (penaltyDiscount <= 0) return 1.0;
        double x = penaltyDiscount * df * log(sampleSize) / fit.kappa();
        return Gamma.regularizedGammaQ(fit.nu() / 2.0, x / 2.0);
    }

    /**
     * Expected spurious edges under fitted nulls: sum over df classes of pairs * alpha(c, df, fit).
     *
     * @param penaltyDiscount c.
     * @param fits            Map from the df the penalty charges to that class's fitted null.
     * @param sampleSize      N.
     * @return The expected count.
     */
    public static double expectedFalseEdgesFitted(double penaltyDiscount, Map<Integer, NullFit> fits, int sampleSize) {
        double total = 0.0;
        for (Map.Entry<Integer, NullFit> e : fits.entrySet()) {
            total += e.getValue().pairs() * alpha(penaltyDiscount, e.getKey(), e.getValue(), sampleSize);
        }
        return total;
    }

    /**
     * The penalty discount holding {@link #expectedFalseEdgesFitted} at the budget, by bisection.
     *
     * @param fits               Map from df to fitted null.
     * @param sampleSize         N.
     * @param expectedFalseEdges The budget.
     * @return c.
     */
    public static double penaltyDiscountForExpectedFalseEdgesFitted(Map<Integer, NullFit> fits, int sampleSize,
                                                                    double expectedFalseEdges) {
        if (fits.isEmpty()) throw new IllegalArgumentException("fits is empty");
        if (!(expectedFalseEdges > 0)) throw new IllegalArgumentException("budget must be positive: " + expectedFalseEdges);
        double totalPairs = 0;
        for (NullFit f : fits.values()) totalPairs += f.pairs();
        if (expectedFalseEdges >= totalPairs) return 0.0;
        double lo = 0.0, hi = 1.0;
        while (expectedFalseEdgesFitted(hi, fits, sampleSize) > expectedFalseEdges) {
            hi *= 2.0;
            if (hi > 1e6) throw new IllegalStateException("could not bracket the penalty discount");
        }
        for (int it = 0; it < 200 && hi - lo > 1e-7; it++) {
            double mid = 0.5 * (lo + hi);
            if (expectedFalseEdgesFitted(mid, fits, sampleSize) > expectedFalseEdges) lo = mid;
            else hi = mid;
        }
        return 0.5 * (lo + hi);
    }

    /**
     * Fractional-budget version of {@link #penaltyDiscountForExpectedFalseEdgesFitted}.
     *
     * @param fits           Map from df to fitted null.
     * @param sampleSize     N.
     * @param numVariables   p.
     * @param expectedDegree A prior guess at the average degree.
     * @param fdr            Tolerated ratio of spurious to true edges.
     * @return c.
     */
    public static double penaltyDiscountForFalseDiscoveryRateFitted(Map<Integer, NullFit> fits, int sampleSize,
                                                                    int numVariables, double expectedDegree, double fdr) {
        if (!(expectedDegree > 0)) throw new IllegalArgumentException("expectedDegree must be positive: " + expectedDegree);
        if (!(fdr > 0 && fdr < 1)) throw new IllegalArgumentException("fdr must be in (0, 1): " + fdr);
        return penaltyDiscountForExpectedFalseEdgesFitted(fits, sampleSize, fdr * numVariables * expectedDegree / 2.0);
    }

    /**
     * Estimates the null of the per-pair LRT for every df class by permutation, for a score built on a Gaussian
     * likelihood over blocks of columns (SEM BIC with one column per variable; Basis Function or Degenerate
     * Gaussian BIC with one embedded block per variable). For a sampled pair (x, y) the rows of x's block are
     * permuted jointly, which severs any dependence with y while preserving x's marginal distribution and hence
     * whatever non-Gaussianity its block carries; the chain-rule LRT for adding x's block to y's is then computed
     * exactly as the score computes it. Each class is fitted with {@link NullFit#fromSample}.
     *
     * @param columns         The data, column-major: columns[j][i] is row i of column j. Columns need not be
     *                        standardized.
     * @param blocks          One int[] of column indices per variable, in variable order.
     * @param samplesPerClass Null draws per df class; a few hundred is plenty.
     * @param seed            Seed for pair selection and permutations; fix it for reproducible calibration.
     * @return Map from df = size[x] * size[y] to the fitted null and its pair count.
     */
    public static Map<Integer, NullFit> fitNullsByPermutation(double[][] columns, List<int[]> blocks,
                                                              int samplesPerClass, long seed) {
        int p = blocks.size();
        if (p < 2) throw new IllegalArgumentException("need at least two variables");
        int n = columns[0].length;
        Random rnd = new Random(seed);

        // Pair counts per df class, and a pool of variable indices per block size for sampling.
        int[] sizes = new int[p];
        for (int v = 0; v < p; v++) sizes[v] = blocks.get(v).length;
        Map<Integer, Long> counts = pairDofHistogram(sizes);

        Map<Integer, List<Double>> samples = new TreeMap<>();
        for (int df : counts.keySet()) samples.put(df, new ArrayList<>());

        int[] perm = new int[n];
        for (int i = 0; i < n; i++) perm[i] = i;

        // Draw random pairs until every class has its quota (or we give up on very rare classes).
        int needed = counts.size() * samplesPerClass;
        int attempts = 0, maxAttempts = 200 * needed;
        while (attempts++ < maxAttempts) {
            boolean done = true;
            for (List<Double> l : samples.values()) if (l.size() < samplesPerClass) done = false;
            if (done) break;
            int x = rnd.nextInt(p), y = rnd.nextInt(p);
            if (x == y) continue;
            int df = sizes[x] * sizes[y];
            List<Double> l = samples.get(df);
            if (l.size() >= samplesPerClass) continue;
            shuffle(perm, rnd);
            l.add(chainRuleLrt(columns, blocks.get(y), blocks.get(x), perm));
        }

        Map<Integer, NullFit> fits = new TreeMap<>();
        for (Map.Entry<Integer, Long> e : counts.entrySet()) {
            List<Double> l = samples.get(e.getKey());
            if (l.size() < 10) {  // too rare to fit; fall back to the exact chi-square for that class
                fits.put(e.getKey(), NullFit.exact(e.getValue(), e.getKey()));
                continue;
            }
            double[] arr = new double[l.size()];
            for (int i = 0; i < arr.length; i++) arr[i] = l.get(i);
            fits.put(e.getKey(), NullFit.fromSample(e.getValue(), arr));
        }
        return fits;
    }

    /**
     * The chain-rule LRT for adding x's block to y's block with no other parents, as Basis Function BIC computes
     * it: sum over the components y_i of y's block of N * ln( var(y_i | y_&lt;i) / var(y_i | y_&lt;i, x-block) ),
     * with x's rows read through {@code perm}. With one column per block this is -N ln(1 - r^2).
     *
     * @param columns Column-major data.
     * @param yBlock  Column indices of y's block.
     * @param xBlock  Column indices of x's block.
     * @param perm    Row permutation applied to x's columns (identity for the observed statistic).
     * @return The LRT.
     */
    public static double chainRuleLrt(double[][] columns, int[] yBlock, int[] xBlock, int[] perm) {
        int n = columns[0].length;
        int kx = xBlock.length, ky = yBlock.length;

        // Assemble the (ky + kx) columns involved, x permuted, then center and take the Gram matrix once.
        double[][] m = new double[ky + kx][];
        for (int j = 0; j < ky; j++) m[j] = columns[yBlock[j]];
        for (int j = 0; j < kx; j++) {
            double[] src = columns[xBlock[j]], dst = new double[n];
            for (int i = 0; i < n; i++) dst[i] = src[perm[i]];
            m[ky + j] = dst;
        }
        int k = ky + kx;
        double[] mean = new double[k];
        for (int j = 0; j < k; j++) {
            double s = 0;
            for (int i = 0; i < n; i++) s += m[j][i];
            mean[j] = s / n;
        }
        double[][] cov = new double[k][k];
        for (int a = 0; a < k; a++) {
            for (int b = a; b < k; b++) {
                double s = 0;
                for (int i = 0; i < n; i++) s += (m[a][i] - mean[a]) * (m[b][i] - mean[b]);
                cov[a][b] = cov[b][a] = s / n;
            }
        }

        double lrt = 0.0;
        for (int i = 0; i < ky; i++) {
            int[] before = new int[i];
            for (int j = 0; j < i; j++) before[j] = j;
            int[] after = new int[i + kx];
            for (int j = 0; j < i; j++) after[j] = j;
            for (int j = 0; j < kx; j++) after[i + j] = ky + j;
            double v0 = residualVariance(cov, i, before);
            double v1 = residualVariance(cov, i, after);
            if (v0 > 0 && v1 > 0) lrt += n * log(v0 / v1);
        }
        return lrt;
    }

    /** var(t | P) = cov[t][t] - cov[t,P] Cov(P)^-1 cov[P,t], from a covariance matrix. */
    private static double residualVariance(double[][] cov, int t, int[] pred) {
        if (pred.length == 0) return cov[t][t];
        RealMatrix cpp = new Array2DRowRealMatrix(pred.length, pred.length);
        double[] cpt = new double[pred.length];
        for (int a = 0; a < pred.length; a++) {
            cpt[a] = cov[pred[a]][t];
            for (int b = 0; b < pred.length; b++) cpp.setEntry(a, b, cov[pred[a]][pred[b]]);
        }
        double[] sol;
        try {
            sol = new CholeskyDecomposition(cpp, 1e-10, 1e-14).getSolver().solve(
                    new Array2DRowRealMatrix(cpt)).getColumn(0);
        } catch (RuntimeException e) {
            return Double.NaN;  // rank-deficient predictor block; caller skips this component
        }
        double q = 0;
        for (int a = 0; a < pred.length; a++) q += cpt[a] * sol[a];
        return cov[t][t] - q;
    }

    private static void shuffle(int[] a, Random rnd) {
        for (int i = a.length - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            int t = a[i];
            a[i] = a[j];
            a[j] = t;
        }
    }

    /**
     * Inverse of {@link #alpha(double, int, int)}: the penalty discount at which a df-degree-of-freedom test runs
     * at level {@code alpha}, i.e. the c solving P(chi-square(df) &gt; c df ln N) = alpha. Found by bisection on
     * {@link #alpha}, so it is exactly consistent with it far into the tail.
     *
     * @param alpha      The per-test level, in (0, 1).
     * @param df         Degrees of freedom, at least 1.
     * @param sampleSize N.
     * @return c, to about 1e-7.
     */
    public static double penaltyDiscountForAlpha(double alpha, int df, int sampleSize) {
        if (!(alpha > 0 && alpha < 1)) throw new IllegalArgumentException("alpha must be in (0, 1): " + alpha);
        if (df < 1) throw new IllegalArgumentException("df must be at least 1: " + df);
        if (sampleSize < 2) throw new IllegalArgumentException("sampleSize must be at least 2: " + sampleSize);
        double lo = 0.0, hi = 1.0;
        while (alpha(hi, df, sampleSize) > alpha) {
            hi *= 2.0;
            if (hi > 1e6) throw new IllegalStateException("could not bracket the penalty discount");
        }
        for (int it = 0; it < 200 && hi - lo > 1e-8; it++) {
            double mid = 0.5 * (lo + hi);
            if (alpha(mid, df, sampleSize) > alpha) lo = mid;
            else hi = mid;
        }
        return 0.5 * (lo + hi);
    }

    /**
     * The expected number of spurious edges, sum over df of count(df) * alpha(c, df).
     *
     * @param penaltyDiscount c.
     * @param dfPairCounts    Histogram from df to the number of null pairs with that df.
     * @param sampleSize      N.
     * @return The expected count.
     */
    public static double expectedFalseEdges(double penaltyDiscount, Map<Integer, Long> dfPairCounts, int sampleSize) {
        double total = 0.0;
        for (Map.Entry<Integer, Long> e : dfPairCounts.entrySet()) {
            total += e.getValue() * alpha(penaltyDiscount, e.getKey(), sampleSize);
        }
        return total;
    }

    /**
     * The penalty discount c at which {@link #expectedFalseEdges} equals {@code expectedFalseEdges}, found by
     * bisection; the expected count is strictly decreasing in c so the root is unique.
     *
     * @param dfPairCounts       Histogram from df to the number of null pairs with that df.
     * @param sampleSize         N.
     * @param expectedFalseEdges The budget, positive.
     * @return c, to about 1e-6.
     */
    public static double penaltyDiscountForExpectedFalseEdges(Map<Integer, Long> dfPairCounts, int sampleSize,
                                                              double expectedFalseEdges) {
        if (dfPairCounts.isEmpty()) throw new IllegalArgumentException("dfPairCounts is empty");
        if (!(expectedFalseEdges > 0)) throw new IllegalArgumentException("budget must be positive: " + expectedFalseEdges);

        double totalPairs = 0;
        for (long n : dfPairCounts.values()) totalPairs += n;
        if (expectedFalseEdges >= totalPairs) return 0.0;  // every pair allowed; no penalty needed

        double lo = 0.0, hi = 1.0;
        while (expectedFalseEdges(hi, dfPairCounts, sampleSize) > expectedFalseEdges) {
            hi *= 2.0;
            if (hi > 1e6) throw new IllegalStateException("could not bracket the penalty discount");
        }
        for (int it = 0; it < 200 && hi - lo > 1e-7; it++) {
            double mid = 0.5 * (lo + hi);
            if (expectedFalseEdges(mid, dfPairCounts, sampleSize) > expectedFalseEdges) lo = mid;
            else hi = mid;
        }
        return 0.5 * (lo + hi);
    }

    /**
     * The penalty discount that holds the expected number of spurious edges at {@code fdr} times the expected
     * number of true edges, p * expectedDegree / 2. See {@link SemBicScore#penaltyDiscountForFalseDiscoveryRate}
     * for the choice of a fractional budget and its relation to EBIC.
     *
     * @param dfPairCounts   Histogram from df to the number of null pairs with that df.
     * @param sampleSize     N.
     * @param numVariables   p.
     * @param expectedDegree A prior guess at the average degree of the true graph.
     * @param fdr            The tolerated ratio of spurious to true edges, e.g. 0.01.
     * @return c.
     */
    public static double penaltyDiscountForFalseDiscoveryRate(Map<Integer, Long> dfPairCounts, int sampleSize,
                                                              int numVariables, double expectedDegree, double fdr) {
        if (!(expectedDegree > 0)) throw new IllegalArgumentException("expectedDegree must be positive: " + expectedDegree);
        if (!(fdr > 0 && fdr < 1)) throw new IllegalArgumentException("fdr must be in (0, 1): " + fdr);
        double expectedTrueEdges = numVariables * expectedDegree / 2.0;
        return penaltyDiscountForExpectedFalseEdges(dfPairCounts, sampleSize, fdr * expectedTrueEdges);
    }

    /**
     * The penalty discount at which the sign rule ignores any parameter whose partial correlation with the child
     * is below {@code minPartialCorrelation}: the c solving -N ln(1 - r^2) = c ln N, i.e. the inverse of
     * {@link SemBicScore#minDetectablePartialCorrelation(double, int)}.
     *
     * <p>This is an effect-size criterion, complementary to the false-discovery criterion. The FDR calibration
     * controls how many <i>null</i> pairs become edges and shrinks like 1 / ln N, so at large N it admits any real
     * dependence however small -- at N = 20000 and c = 1 the rule accepts partial correlations of 0.02, and on real
     * data that is nearly everything. This criterion instead sets the smallest effect worth an edge and grows like
     * N / ln N. Taking the larger of the two discounts controls false positives at small N and effect size at
     * large N, and the crossover is where -N ln(1 - r^2) equals the FDR chi-square quantile.</p>
     *
     * <p>For scores whose parents cost more than one parameter (discrete, DG, BF-BIC) the penalty is per parameter
     * and so is this floor: a parent's block must contribute at least this much per parameter on average, i.e.
     * df times the single-parameter floor in total. A real but purely linear effect carried by a block with unused
     * higher-order columns is charged for those columns, so adaptive basis selection, which prunes them, is the
     * natural companion.</p>
     *
     * @param minPartialCorrelation r_min, in (0, 1).
     * @param sampleSize            N.
     * @return c = -N ln(1 - r_min^2) / ln N.
     */
    public static double penaltyDiscountForMinPartialCorrelation(double minPartialCorrelation, int sampleSize) {
        if (!(minPartialCorrelation > 0 && minPartialCorrelation < 1)) {
            throw new IllegalArgumentException("minPartialCorrelation must be in (0, 1): " + minPartialCorrelation);
        }
        if (sampleSize < 2) throw new IllegalArgumentException("sampleSize must be at least 2: " + sampleSize);
        double r2 = minPartialCorrelation * minPartialCorrelation;
        return -sampleSize * Math.log1p(-r2) / log(sampleSize);
    }

    /**
     * Builds the df histogram over unordered pairs from per-variable parameter block sizes, taking the df of the
     * pair (x, y) to be size[x] * size[y]. For SEM BIC every size is 1; for Basis Function and Degenerate Gaussian
     * BIC it is the embedding block size; for discrete BIC it is the number of categories minus one.
     *
     * @param blockSizes One entry per variable, each at least 1.
     * @return Histogram from df to the number of pairs with that df; sums to p(p-1)/2.
     */
    public static Map<Integer, Long> pairDofHistogram(int[] blockSizes) {
        Map<Integer, Long> hist = new TreeMap<>();
        for (int i = 0; i < blockSizes.length; i++) {
            if (blockSizes[i] < 1) throw new IllegalArgumentException("block size must be at least 1 at " + i);
            for (int j = i + 1; j < blockSizes.length; j++) {
                hist.merge(blockSizes[i] * blockSizes[j], 1L, Long::sum);
            }
        }
        return hist;
    }

    /**
     * Convenience for a homogeneous population: p variables all with the same block size.
     *
     * @param numVariables p.
     * @param blockSize    The common block size (df per pair is its square).
     * @return A one-entry histogram.
     */
    public static Map<Integer, Long> uniformPairDofHistogram(int numVariables, int blockSize) {
        Map<Integer, Long> hist = new TreeMap<>();
        hist.put(blockSize * blockSize, (long) numVariables * (numVariables - 1) / 2);
        return hist;
    }
}
