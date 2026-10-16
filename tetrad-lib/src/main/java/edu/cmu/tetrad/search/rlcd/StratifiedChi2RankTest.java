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

package edu.cmu.tetrad.search.rlcd;

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.StratifiedRankTests;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;

import java.util.ArrayList;
import java.util.List;

/**
 * The RLCD rank test pooled over the strata of a discrete domain (batch, site, cohort) variable, after Theorem 4 of
 * Dong, Dai, Ng, Spirtes, and Zhang (ICML 2026), the Phase 1 test of their LCD-NOD framework. Within each domain
 * the Lawley-corrected canonical-correlation statistic of {@link Chi2RankTest} is computed as usual; the statistics
 * and their degrees of freedom are then summed across domains, which is valid because the domains are disjoint,
 * independent samples. The paper's Theorem 4 states the result for the plain Bartlett multiplier; using Lawley's
 * correction within each domain keeps this class consistent with Tetrad's homogeneous RLCD test, and each
 * per-domain statistic has the same chi-square limit, so the pooled statistic does too.
 * <p>
 * This is the third member of a family and should not be confused with the other two: {@link Chi2RankTest} is the
 * homogeneous test on one sample, and {@link PooledRankTest} combines statistics over multiple imputations of one
 * sample, which share their observed rows and must not be treated as independent. Here the strata are independent
 * by construction, so the statistics simply add.
 * <p>
 * Domains too small for the statistic to discriminate (leading Lawley multiplier not positive) are skipped, along
 * with their degrees of freedom. If no domain is usable, the null is rejected, mirroring the degenerate-input
 * convention of {@link Chi2RankTest}; the chi-square approximation is rough when many small domains survive the
 * gate, so merge small domains first.
 *
 * @author josephramsey
 */
public final class StratifiedChi2RankTest implements RankTester {

    /**
     * The per-domain tests, in the order of the domains.
     */
    private final List<Chi2RankTest> perDomain;

    /**
     * The per-domain data sets, kept for the stage-1 covariance helper and the variable list.
     */
    private final List<DataSet> domains;

    /**
     * The per-domain sample sizes, parallel to perDomain.
     */
    private final int[] ns;

    /**
     * Constructs the test from one continuous data set per domain. The data sets must all have the same variables
     * in the same order.
     *
     * @param domains one continuous data set per domain.
     */
    public StratifiedChi2RankTest(List<DataSet> domains) {
        if (domains == null || domains.isEmpty()) throw new IllegalArgumentException("No domains given.");
        this.domains = new ArrayList<>(domains);
        this.perDomain = new ArrayList<>();
        this.ns = new int[domains.size()];

        List<String> names0 = domains.get(0).getVariableNames();
        for (int t = 0; t < domains.size(); t++) {
            DataSet d = domains.get(t);
            if (d == null) throw new NullPointerException("Domain " + t + " is null.");
            if (!d.isContinuous()) throw new IllegalArgumentException("Domain " + t + " is not continuous.");
            if (!d.getVariableNames().equals(names0)) {
                throw new IllegalArgumentException("Domain " + t + " does not have the same variables, in the same "
                                                   + "order, as domain 0.");
            }
            this.perDomain.add(new Chi2RankTest(d));
            this.ns[t] = d.getNumRows();
        }
    }

    /**
     * Constructs the test from a single data set containing a discrete domain column, which is removed from the
     * variable list and used to split the rows; see {@link StratifiedRankTests#split}.
     *
     * @param dataSet          the data set, containing the domain column.
     * @param domainColumnName the name of the domain column.
     * @return the stratified test over the remaining variables.
     */
    public static StratifiedChi2RankTest fromDataSet(DataSet dataSet, String domainColumnName) {
        Node domain = dataSet.getVariable(domainColumnName);
        if (domain == null) {
            throw new IllegalArgumentException("No column named " + domainColumnName + " in the data set.");
        }
        return new StratifiedChi2RankTest(StratifiedRankTests.split(dataSet, domain));
    }

    /**
     * Returns the per-domain data sets.
     *
     * @return the per-domain data sets.
     */
    public List<DataSet> getDomains() {
        return new ArrayList<>(domains);
    }

    /**
     * Returns the total sample size over all domains.
     *
     * @return the total sample size.
     */
    public int getTotalSampleSize() {
        int total = 0;
        for (int n : ns) total += n;
        return total;
    }

    /**
     * The within-domain pooled covariance matrix: the per-domain covariance matrices averaged with weights
     * (n_t - 1), which equals the covariance of the data after each domain has been centered at its own means, at
     * sample size N - (number of domains) to account for the per-domain centering. RLCD's stage 1 scores this
     * instead of the raw covariance of the concatenated data, whose between-domain mean shifts would otherwise
     * dominate. This is exactly right when only the means shift by domain; under coefficient changes it is the
     * average of the per-domain covariances, and scoring it is a heuristic initialization of the observed skeleton
     * with no identifiability guarantee -- the guarantee of Dong et al.'s Theorem 1 attaches to the within-domain
     * rank tests, not to this average.
     *
     * @return the within-domain pooled covariance matrix.
     */
    public CovarianceMatrix withinDomainPooledCovariance() {
        CovarianceMatrix first = new CovarianceMatrix(domains.get(0));
        Matrix sum = first.getMatrix().scalarMult(ns[0] - 1.0);
        double weight = ns[0] - 1.0;

        for (int t = 1; t < domains.size(); t++) {
            sum = sum.plus(new CovarianceMatrix(domains.get(t)).getMatrix().scalarMult(ns[t] - 1.0));
            weight += ns[t] - 1.0;
        }

        int effectiveN = Math.max(2, getTotalSampleSize() - domains.size());
        return new CovarianceMatrix(first.getVariables(), sum.scalarMult(1.0 / weight), effectiveN);
    }

    /**
     * The pooled statistic for the rank &le; r test: the sum of the per-domain Lawley statistics over the usable
     * domains, to be referred to a chi-square distribution whose degrees of freedom are (|P| - r)(|Q| - r) times
     * the number of usable domains. A domain is usable if its leading Lawley multiplier N_t - r - (|P| + |Q| + 1)/2
     * is positive.
     *
     * @param pcols column indices of the first set.
     * @param qcols column indices of the second set.
     * @param r     the hypothesized maximum rank.
     * @return the pooled statistic and its degrees of freedom, as a two-element array {statistic, df}; df is 0 when
     * no domain is usable.
     */
    public double[] pooledStatistic(int[] pcols, int[] qcols, int r) {
        int p = pcols.length, q = qcols.length;
        double stat = 0.0;
        int df = 0;

        for (int t = 0; t < perDomain.size(); t++) {
            if (ns[t] - r - 0.5 * (p + q + 1) <= 0) continue; // too small to discriminate; skip it and its df
            stat += perDomain.get(t).statistic(pcols, qcols, r);
            df += (p - r) * (q - r);
        }

        return new double[]{stat, df};
    }

    /**
     * Tests the null hypothesis that the cross-covariance of the two column sets, within each domain, has rank at
     * most r, by comparing the pooled statistic with the chi-square critical value at the pooled degrees of
     * freedom. Degenerate inputs, and the case of no usable domain, reject the null, as in {@link Chi2RankTest}.
     *
     * @param pcols column indices of the first set.
     * @param qcols column indices of the second set.
     * @param r     the hypothesized maximum rank.
     * @param alpha the significance level.
     * @return true if the null is NOT rejected.
     */
    @Override
    public boolean failToReject(int[] pcols, int[] qcols, int r, double alpha) {
        int p = pcols.length, q = qcols.length;
        if (p == 0 || q == 0) return false;
        int m = Math.min(p, q);
        if (r < 0 || r >= m || (p - r) * (q - r) <= 0) return false;

        double[] sd = pooledStatistic(pcols, qcols, r);
        if (sd[1] <= 0) return false;

        double critical = new ChiSquaredDistribution(sd[1]).inverseCumulativeProbability(1 - alpha);
        return sd[0] <= critical;
    }

    /**
     * The p-value of the pooled rank &le; r test, or 0 for degenerate inputs or when no domain is usable.
     *
     * @param pcols column indices of the first set.
     * @param qcols column indices of the second set.
     * @param r     the hypothesized maximum rank.
     * @return the p-value.
     */
    @Override
    public double pValue(int[] pcols, int[] qcols, int r) {
        int p = pcols.length, q = qcols.length;
        int m = Math.min(p, q);
        if (p == 0 || q == 0 || r < 0 || r >= m || (p - r) * (q - r) <= 0) return 0.0;

        double[] sd = pooledStatistic(pcols, qcols, r);
        if (sd[1] <= 0) return 0.0;

        return 1.0 - new ChiSquaredDistribution(sd[1]).cumulativeProbability(sd[0]);
    }
}
