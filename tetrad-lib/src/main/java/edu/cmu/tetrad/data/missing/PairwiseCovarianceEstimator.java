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

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;

/**
 * Estimates a covariance matrix from data with missing values by pairwise deletion, without a model of the data.
 *
 * <p>Each correlation is computed over the rows on which both of its variables are observed, using that pair's own
 * means and variances, so every entry is a valid correlation. It is then rescaled to a covariance by each variable's
 * standard deviation over all rows on which that variable is observed. Entries estimated on different rows need not
 * be mutually consistent, so the off-diagonal is shrunk toward zero by the least factor (found by bisection) that
 * leaves the correlation matrix positive definite with a small margin; usually the factor is 1 or close to it.</p>
 *
 * <p>Every score or test that reads the result sees one fixed matrix, so comparisons between parent sets share
 * their estimation error and covariance-based scores remain score-equivalent, which test-wise deletion does not.
 * Like test-wise deletion it is consistent only when values are missing completely at random (MCAR); unlike EM it
 * needs no Gaussian working model. The reported sample size is the number of rows; choose a pairwise effective
 * sample size mode to discount it.</p>
 *
 * @author josephramsey
 * @see MissingDataSpec#pairwiseCovariance()
 */
public final class PairwiseCovarianceEstimator {

    /** Smallest pivot the Cholesky test accepts; the margin by which the shrunk matrix is positive definite. */
    private static final double PD_MARGIN = 1e-3;

    /** Bisection steps for the shrinkage factor. */
    private static final int BISECTION_STEPS = 40;

    private PairwiseCovarianceEstimator() {
    }

    /**
     * Estimates the covariance matrix of the dataset's columns.
     *
     * @param data The data; missing values as defined by {@link MissingDataAudit#isMissing(DataSet, int, int)}.
     * @return The covariance matrix, with the dataset's variables and row count.
     * @throws IllegalArgumentException if a column has fewer than two observed values.
     */
    public static ICovarianceMatrix estimate(DataSet data) {
        int n = data.getNumRows();
        int p = data.getNumColumns();

        double[][] x = new double[n][p];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < p; j++) {
                x[i][j] = MissingDataAudit.isMissing(data, i, j) ? Double.NaN : data.getDouble(i, j);
            }
        }

        double[] sd = new double[p];
        for (int j = 0; j < p; j++) {
            double s = 0, ss = 0;
            int m = 0;
            for (int i = 0; i < n; i++) {
                if (Double.isNaN(x[i][j])) continue;
                s += x[i][j];
                ss += x[i][j] * x[i][j];
                m++;
            }
            if (m < 2) {
                throw new IllegalArgumentException("Pairwise covariance: column " + data.getVariable(j).getName()
                                                   + " has fewer than two observed values.");
            }
            double mean = s / m;
            sd[j] = Math.sqrt(Math.max(0.0, (ss - m * mean * mean) / (m - 1)));
        }

        double[][] corr = new double[p][p];
        for (int a = 0; a < p; a++) {
            corr[a][a] = 1.0;
            for (int b = a + 1; b < p; b++) {
                double sa = 0, sb = 0, saa = 0, sbb = 0, sab = 0;
                int m = 0;
                for (int i = 0; i < n; i++) {
                    double u = x[i][a], v = x[i][b];
                    if (Double.isNaN(u) || Double.isNaN(v)) continue;
                    sa += u;
                    sb += v;
                    saa += u * u;
                    sbb += v * v;
                    sab += u * v;
                    m++;
                }
                double r = 0.0;
                if (m > 2) {
                    double cab = sab - sa * sb / m, caa = saa - sa * sa / m, cbb = sbb - sb * sb / m;
                    if (caa > 0 && cbb > 0) r = cab / Math.sqrt(caa * cbb);
                }
                corr[a][b] = corr[b][a] = r;
            }
        }

        double keep = shrinkageToPositiveDefinite(corr);

        double[][] cov = new double[p][p];
        for (int a = 0; a < p; a++) {
            for (int b = 0; b < p; b++) {
                cov[a][b] = (a == b ? 1.0 : keep * corr[a][b]) * sd[a] * sd[b];
            }
        }

        return new CovarianceMatrix(data.getVariables(), cov, n);
    }

    /**
     * The largest factor in [0, 1] by which the off-diagonal can be scaled with the matrix still positive definite
     * with a margin: 1 if it already is.
     */
    private static double shrinkageToPositiveDefinite(double[][] corr) {
        if (positiveDefinite(corr, 1.0)) return 1.0;

        double lo = 0.0, hi = 1.0; // lo is known to work (the identity); hi is known not to.
        for (int step = 0; step < BISECTION_STEPS; step++) {
            double mid = 0.5 * (lo + hi);
            if (positiveDefinite(corr, mid)) lo = mid;
            else hi = mid;
        }
        return lo;
    }

    /** Whether the correlation matrix with off-diagonal scaled by {@code keep} has a Cholesky factor with margin. */
    private static boolean positiveDefinite(double[][] corr, double keep) {
        int p = corr.length;
        double[][] l = new double[p][p];
        for (int i = 0; i < p; i++) {
            for (int j = 0; j <= i; j++) {
                double s = i == j ? 1.0 : keep * corr[i][j];
                for (int k = 0; k < j; k++) s -= l[i][k] * l[j][k];
                if (i == j) {
                    if (s < PD_MARGIN) return false;
                    l[i][i] = Math.sqrt(s);
                } else {
                    l[i][j] = s / l[j][j];
                }
            }
        }
        return true;
    }
}
