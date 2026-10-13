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

package edu.cmu.tetrad.data.missing;

import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.distribution.TDistribution;

/**
 * Rubin's rules for pooling a scalar estimate across the m completed data sets of a multiple imputation. Each data
 * set gives an estimate and its standard error, computed as if that data set were fully observed; the pooled
 * estimate is their mean, and its variance adds to the average of the squared standard errors (the within-imputation
 * variance) the variance of the estimates across data sets (the between-imputation variance), inflated by 1 + 1/m.
 * The second term is the uncertainty due to the missing data, which no single completed data set shows.
 * <p>
 * Degrees of freedom follow Barnard and Rubin (1999), which keeps them below the complete-data degrees of freedom.
 * <p>
 * These rules are for imputations of one data set. They are not valid for data sets that are independent samples,
 * different subjects, or different conditions. And if the imputations were made without drawing the imputation
 * model's parameters (improper imputation, as Tetrad's imputers are), the between-imputation variance is understated,
 * and so the pooled standard error is somewhat too small.
 *
 * @author josephramsey
 */
public final class RubinsRules {

    private RubinsRules() {
    }

    /**
     * Pools one parameter.
     *
     * @param estimates      the estimate from each completed data set; at least 2
     * @param standardErrors the standard error of each, in the same order
     * @param completeDataDf the degrees of freedom the estimate would have with complete data (for instance, the
     *                       sample size less the number of free parameters); positive, or NaN or infinite if
     *                       unknown, in which case no small-sample adjustment is made
     * @return the pooled result
     */
    public static Pooled pool(double[] estimates, double[] standardErrors, double completeDataDf) {
        int m = estimates.length;

        if (m < 2) throw new IllegalArgumentException("Pooling needs at least 2 data sets: " + m);
        if (standardErrors.length != m) throw new IllegalArgumentException("One standard error per estimate.");

        double mean = 0.0;
        double within = 0.0;
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;

        for (int i = 0; i < m; i++) {
            mean += estimates[i];
            within += standardErrors[i] * standardErrors[i];
            min = Math.min(min, estimates[i]);
            max = Math.max(max, estimates[i]);
        }

        mean /= m;
        within /= m;

        double between = 0.0;
        for (int i = 0; i < m; i++) between += (estimates[i] - mean) * (estimates[i] - mean);
        between /= (m - 1);

        double inflated = (1.0 + 1.0 / m) * between;
        double total = within + inflated;
        double se = Math.sqrt(total);

        // The share of the total variance that is due to the missing data.
        double lambda = total > 0 ? inflated / total : 0.0;

        double dfOld = lambda > 0 ? (m - 1) / (lambda * lambda) : Double.POSITIVE_INFINITY;
        double df;

        if (completeDataDf > 0 && !Double.isInfinite(completeDataDf)) {
            double dfObserved = (completeDataDf + 1.0) / (completeDataDf + 3.0) * completeDataDf * (1.0 - lambda);
            df = Double.isInfinite(dfOld) ? dfObserved : dfOld * dfObserved / (dfOld + dfObserved);
        } else {
            df = dfOld;
        }

        double fmi;

        if (within > 0 && !Double.isInfinite(df)) {
            double r = inflated / within;
            fmi = (r + 2.0 / (df + 3.0)) / (r + 1.0);
        } else {
            fmi = lambda;
        }

        double t = mean / se;
        double p;

        if (Double.isNaN(t) || Double.isNaN(df)) {
            p = Double.NaN;
        } else if (Double.isInfinite(t)) {
            p = 0.0;
        } else if (Double.isInfinite(df) || df > 1e6) {
            p = 2.0 * (1.0 - new NormalDistribution().cumulativeProbability(Math.abs(t)));
        } else if (df > 0) {
            p = 2.0 * (1.0 - new TDistribution(df).cumulativeProbability(Math.abs(t)));
        } else {
            p = Double.NaN;
        }

        if (p < 0.0) p = 0.0;
        if (p > 1.0) p = 1.0;

        return new Pooled(mean, se, df, t, p, fmi, within, between, min, max);
    }

    /**
     * A pooled parameter.
     *
     * @param estimate                   the mean of the estimates
     * @param standardError              the square root of the total variance
     * @param degreesOfFreedom           the degrees of freedom for the t reference distribution
     * @param t                          the estimate over its standard error
     * @param pValue                     the two-sided p-value for the estimate being zero
     * @param fractionMissingInformation the share of the information about the parameter lost to missing data
     * @param withinVariance             the average squared standard error
     * @param betweenVariance            the variance of the estimates across data sets
     * @param min                        the smallest estimate
     * @param max                        the largest estimate
     */
    public record Pooled(double estimate, double standardError, double degreesOfFreedom, double t, double pValue,
                         double fractionMissingInformation, double withinVariance, double betweenVariance,
                         double min, double max) {
    }
}
