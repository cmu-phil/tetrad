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

import edu.cmu.tetrad.data.DataSet;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.apache.commons.math3.distribution.FDistribution;

import java.util.ArrayList;
import java.util.List;

/**
 * The RLCD rank test over several imputations of one data set with missing values: each question RLCD asks is
 * answered once, from all of the imputations, instead of once per imputation.
 * <p>
 * The statistic of {@link Chi2RankTest} is computed on each of the M imputed data sets, giving d_1, ..., d_M, each
 * referred by that test to a chi-square distribution with k degrees of freedom. These are combined by the rule of
 * Li, Meng, Raghunathan, and Rubin (1991, Statistica Sinica 1, 65-92) for chi-square statistics from multiply
 * imputed data, often called D2:
 * <pre>
 *     r  = (1 + 1/M) · Var(√d_1, ..., √d_M)                  (sample variance, divisor M − 1)
 *     D2 = ( mean(d)/k − ((M + 1)/(M − 1)) · r ) / (1 + r)
 *     ν  = k^(−3/M) · (M − 1) · (1 + 1/r)²
 * </pre>
 * with D2 referred to an F distribution with k and ν degrees of freedom. Here r estimates the relative increase in
 * variance due to the missing values: where the imputations agree, r is near zero and the rule reduces to the
 * ordinary chi-square test on the mean statistic; where they disagree, the statistic is shrunk and the reference
 * distribution widened, so the disagreement counts against rejecting.
 * <p>
 * This is not the same as pooling independent data sets. The imputations share their observed values, so combining
 * their p-values as if they were independent (Fisher's method), or adding their scores, would count the same rows M
 * times.
 * <p>
 * What is and is not established: the rule is a standard approximation, derived assuming proper imputations under
 * data missing at random and a common fraction of missing information across the parameters tested. It is known to
 * be rough, with few imputations especially, and no calibration result is claimed for its use with the Lawley
 * corrected rank statistic. Tetrad's imputers draw from a fitted model without redrawing the model's parameters
 * (improper imputation), which understates r somewhat.
 *
 * @author josephramsey
 */
public final class PooledRankTest implements RankTester {

    private final List<Chi2RankTest> tests = new ArrayList<>();

    /**
     * Constructs the test from the imputed data sets.
     *
     * @param imputations continuous data sets with the same variables in the same order and the same number of
     *                    rows; at least one.
     */
    public PooledRankTest(List<DataSet> imputations) {
        if (imputations == null || imputations.isEmpty()) {
            throw new IllegalArgumentException("At least one data set is required.");
        }

        for (DataSet dataSet : imputations) this.tests.add(new Chi2RankTest(dataSet));
    }

    /**
     * @return the number of imputations pooled.
     */
    public int getNumImputations() {
        return this.tests.size();
    }

    @Override
    public boolean failToReject(int[] pcols, int[] qcols, int r, double alpha) {
        int p = pcols.length, q = qcols.length;

        // As in Chi2RankTest: a test with no degrees of freedom rejects.
        if (p == 0 || q == 0 || r < 0 || r >= Math.min(p, q) || (p - r) * (q - r) <= 0) return false;

        return pValue(pcols, qcols, r) >= alpha;
    }

    /**
     * The pooled p-value of the rank ≤ r test.
     *
     * @param pcols column indices of the first set.
     * @param qcols column indices of the second set.
     * @param r     the hypothesized maximum rank.
     * @return the p-value, or 0 when the degrees of freedom are not positive.
     */
    @Override
    public double pValue(int[] pcols, int[] qcols, int r) {
        int p = pcols.length, q = qcols.length;
        int df = (p - r) * (q - r);
        if (p == 0 || q == 0 || r < 0 || r >= Math.min(p, q) || df <= 0) return 0.0;

        double[] d = new double[this.tests.size()];
        for (int i = 0; i < d.length; i++) d[i] = this.tests.get(i).statistic(pcols, qcols, r);

        return pooledPValue(d, df);
    }

    /**
     * The relative increase in variance due to the missing values, as estimated for one test: the r of the class
     * comment. Zero with a single imputation.
     *
     * @param pcols column indices of the first set.
     * @param qcols column indices of the second set.
     * @param r     the hypothesized maximum rank.
     * @return the estimate.
     */
    public double relativeIncreaseInVariance(int[] pcols, int[] qcols, int r) {
        double[] d = new double[this.tests.size()];
        for (int i = 0; i < d.length; i++) d[i] = this.tests.get(i).statistic(pcols, qcols, r);
        return relativeIncrease(d);
    }

    /**
     * Combines chi-square statistics from M imputations by the D2 rule; see the class comment.
     *
     * @param d  the statistics, one per imputation.
     * @param df their common degrees of freedom; positive.
     * @return the pooled p-value.
     */
    public static double pooledPValue(double[] d, int df) {
        int m = d.length;
        double mean = 0.0;
        for (double v : d) mean += Math.max(v, 0.0);
        mean /= m;

        double r = relativeIncrease(d);

        // One imputation, or imputations that agree: the ordinary chi-square test.
        if (m == 1 || r < 1e-12) {
            return 1.0 - new ChiSquaredDistribution(df).cumulativeProbability(mean);
        }

        double d2 = (mean / df - (m + 1.0) / (m - 1.0) * r) / (1.0 + r);
        if (d2 <= 0.0) return 1.0;

        double nu = Math.pow(df, -3.0 / m) * (m - 1.0) * (1.0 + 1.0 / r) * (1.0 + 1.0 / r);

        // With a very large denominator the F distribution is the chi-square distribution over its degrees of
        // freedom, and the F routine loses accuracy.
        if (nu > 1e7) {
            return 1.0 - new ChiSquaredDistribution(df).cumulativeProbability(df * d2);
        }

        return 1.0 - new FDistribution(df, nu).cumulativeProbability(d2);
    }

    private static double relativeIncrease(double[] d) {
        int m = d.length;
        if (m < 2) return 0.0;

        double mean = 0.0;
        for (double v : d) mean += Math.sqrt(Math.max(v, 0.0));
        mean /= m;

        double ss = 0.0;
        for (double v : d) {
            double dev = Math.sqrt(Math.max(v, 0.0)) - mean;
            ss += dev * dev;
        }

        return (1.0 + 1.0 / m) * ss / (m - 1.0);
    }
}
