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

package edu.cmu.tetrad.search.rlcd;

import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.data.missing.MissingDataSpec;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;

import java.util.Arrays;

/**
 * A rank test over a covariance matrix estimated from incomplete data as a whole -- by EM under the missing-at-random
 * assumption ({@link edu.cmu.tetrad.data.EmCovarianceEstimator}), or by pairwise deletion -- with a sample size chosen
 * per query from the pairwise-complete counts of the columns that query mentions. The statistic is the
 * Lawley-corrected canonical-correlation chi-square of {@link Chi2RankTest}; only the sample size differs from query
 * to query.
 * <p>
 * <b>Why this exists.</b> For data with missing values RLCD had two options, and both fail in a characteristic way as
 * the number of variables grows. Pooling rank tests over multiple imputations by the D2 rule
 * ({@link PooledRankTest}) returns a p-value of 1 whenever the between-imputation spread of the statistic is large
 * relative to its mean, which at five imputations and the large degrees of freedom of a rank test happens often; every
 * set then looks rank deficient and the search returns one latent over nearly everything. Testwise deletion
 * ({@link TestwiseDeletionRankTest}) answers each query from the rows complete on all of that query's columns, and a
 * query over u columns keeps only (1 − rate)^u of the rows, so wide rank queries are left with nothing. The EM
 * covariance uses every observed value in every row once, gives one matrix for the whole search (so stage 1 and every
 * stage-2 decision see the same estimate), is positive semidefinite, and is consistent under MAR. What it does not do
 * is tell the chi-square approximation how much information went into each entry; that is the sample size question
 * below.
 * <p>
 * <b>Sample size per query.</b> Lawley's multiplier needs an N. The full row count overstates the information in a
 * covariance estimated from incomplete data and makes the test reject too readily, which for RLCD means missing
 * clusters. The modes of {@link MissingDataSpec.EffectiveSampleSizeMode} are applied here per query, over the pairs of
 * columns the query mentions: {@code FULL_N} uses the row count; {@code MEAN_PAIRWISE} the mean number of rows on which
 * both columns of a pair are observed, averaged over the query's pairs; {@code MIN_PAIRWISE} the least such count.
 * The mean is the recommended choice and the default of the convenience constructor. None of these is exact; the
 * exact answer is the observed information of the canonical correlations under the missingness pattern, which has no
 * closed form here.
 * <p>
 * Limitation: EM assumes approximately multivariate normal data and MAR missingness. Under self-masking (a value
 * missing because of its own size, as a ceiling-truncated grade is) neither EM nor any other handling of the holes
 * recovers the lost information, and the estimate is biased.
 *
 * @author josephramsey
 * @see PooledRankTest
 * @see TestwiseDeletionRankTest
 */
public final class EmCovarianceRankTest implements RankTester {

    private final Chi2RankTest test;
    private final int[][] pairwiseCounts;
    private final int numRows;
    private final MissingDataSpec.EffectiveSampleSizeMode essMode;
    private final double nScaling;

    /**
     * Constructs the test over an estimated covariance matrix with the mean-pairwise sample-size rule.
     *
     * @param cov            the covariance (or correlation) matrix estimated from the incomplete data.
     * @param pairwiseCounts for each pair of columns, the number of rows on which both are observed; the diagonal
     *                       holds the number of rows on which the column is observed.
     * @param numRows        the number of rows of the data.
     */
    public EmCovarianceRankTest(ICovarianceMatrix cov, int[][] pairwiseCounts, int numRows) {
        this(cov, pairwiseCounts, numRows, MissingDataSpec.EffectiveSampleSizeMode.MEAN_PAIRWISE, 1.0);
    }

    /**
     * Constructs the test over an estimated covariance matrix.
     *
     * @param cov            the covariance (or correlation) matrix estimated from the incomplete data.
     * @param pairwiseCounts for each pair of columns, the number of rows on which both are observed.
     * @param numRows        the number of rows of the data.
     * @param essMode        how the sample size of a query is chosen from the pairwise counts of its columns.
     * @param nScaling       a multiplier on the sample size; 1.0 is the default.
     */
    public EmCovarianceRankTest(ICovarianceMatrix cov, int[][] pairwiseCounts, int numRows,
                                MissingDataSpec.EffectiveSampleSizeMode essMode, double nScaling) {
        if (cov == null) throw new NullPointerException("Covariance matrix is null.");
        if (pairwiseCounts == null) throw new NullPointerException("Pairwise counts are null.");
        if (pairwiseCounts.length != cov.getDimension()) {
            throw new IllegalArgumentException("Pairwise counts are " + pairwiseCounts.length + " by "
                                               + pairwiseCounts.length + " but the matrix is " + cov.getDimension()
                                               + " by " + cov.getDimension() + ".");
        }
        this.test = new Chi2RankTest(cov, 1.0);
        this.pairwiseCounts = pairwiseCounts;
        this.numRows = numRows;
        this.essMode = essMode;
        this.nScaling = nScaling;
    }

    @Override
    public boolean failToReject(int[] pcols, int[] qcols, int r, double alpha) {
        int p = pcols.length, q = qcols.length;
        int df = (p - r) * (q - r);
        if (p == 0 || q == 0 || r < 0 || r >= Math.min(p, q) || df <= 0) return false;
        double n = sampleSize(pcols, qcols);
        // A non-positive Lawley multiplier would make the statistic negative and the test accept for nothing.
        if (n - r - 0.5 * (p + q + 1) <= 0) return false;
        double stat = test.statistic(pcols, qcols, r, n);
        return stat <= new ChiSquaredDistribution(df).inverseCumulativeProbability(1 - alpha);
    }

    @Override
    public double pValue(int[] pcols, int[] qcols, int r) {
        int p = pcols.length, q = qcols.length;
        int df = (p - r) * (q - r);
        if (p == 0 || q == 0 || r < 0 || r >= Math.min(p, q) || df <= 0) return 0.0;
        double n = sampleSize(pcols, qcols);
        if (n - r - 0.5 * (p + q + 1) <= 0) return 0.0;
        double stat = test.statistic(pcols, qcols, r, n);
        return 1.0 - new ChiSquaredDistribution(df).cumulativeProbability(stat);
    }

    /**
     * The sample size this test uses for a query, by the configured mode over the pairs of distinct columns the
     * query mentions.
     *
     * @param pcols column indices of the first set.
     * @param qcols column indices of the second set.
     * @return the sample size, scaled.
     */
    public double sampleSize(int[] pcols, int[] qcols) {
        if (essMode == MissingDataSpec.EffectiveSampleSizeMode.FULL_N) return numRows * nScaling;

        int[] all = new int[pcols.length + qcols.length];
        System.arraycopy(pcols, 0, all, 0, pcols.length);
        System.arraycopy(qcols, 0, all, pcols.length, qcols.length);
        Arrays.sort(all);
        int u = 0;
        for (int i = 0; i < all.length; i++) if (i == 0 || all[i] != all[i - 1]) all[u++] = all[i];

        if (u < 2) return pairwiseCounts[all[0]][all[0]] * nScaling;

        double sum = 0.0;
        int min = Integer.MAX_VALUE, pairs = 0;
        for (int a = 0; a < u; a++) {
            for (int b = a + 1; b < u; b++) {
                int c = pairwiseCounts[all[a]][all[b]];
                sum += c;
                min = Math.min(min, c);
                pairs++;
            }
        }
        double n = essMode == MissingDataSpec.EffectiveSampleSizeMode.MIN_PAIRWISE ? min : sum / pairs;
        return n * nScaling;
    }

    /**
     * The underlying test over the estimated matrix, for inspection of canonical correlations.
     *
     * @return the test.
     */
    public Chi2RankTest getUnderlyingTest() {
        return test;
    }
}
