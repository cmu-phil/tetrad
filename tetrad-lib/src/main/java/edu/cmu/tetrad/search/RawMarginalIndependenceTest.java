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

package edu.cmu.tetrad.search;

/**
 * Functional interface for performing a raw marginal independence test.
 * <p>
 * This interface provides a method to compute the p-value for the statistical test of marginal independence between two
 * variables, represented as double arrays. The test evaluates the null hypothesis that the two variables are
 * statistically independent.
 */
@FunctionalInterface
public interface RawMarginalIndependenceTest {

    /**
     * Computes the p-value for the statistical test of marginal independence between the two given variables
     * represented by the input arrays.
     *
     * @param x the first variable, represented as an array of doubles
     * @param y the second variable, represented as an array of doubles
     * @return the computed p-value for the test of marginal independence
     * @throws InterruptedException if the computation is interrupted
     */
    double computePValue(double[] x, double[] y) throws InterruptedException;

    /**
     * Computes the p-value for the statistical test of marginal independence between a single variable and a
     * multivariate set of variables.
     * <p>
     * Default implementation: fall back to pairwise tests of (x, y_j) for each column y_j in Y, and combine the
     * resulting p-values with Fisherâs method. Implementations that support true multivariate tests (e.g. HSIC, KCI
     * with vector Y) should override this method.
     *
     * @param x the first variable (scalar), represented as an array of doubles of length n
     * @param Y the multivariate variable, represented as a 2D array of shape [n][m] (n samples, m variables)
     * @return the computed p-value for the test of independence
     * @throws InterruptedException if the computation is interrupted
     */
    default double computePValue(double[] x, double[][] Y) throws InterruptedException {
        // Fallback: Fisher combine
        if (Y == null || Y.length == 0) return 1.0;
        int m = Y[0].length;
        double stat = 0.0;
        int k = 0;
        for (int j = 0; j < m; j++) {
            double[] yj = new double[Y.length];
            for (int i = 0; i < Y.length; i++) yj[i] = Y[i][j];
            double pj = computePValue(x, yj);
            if (Double.isNaN(pj)) continue;
            double pc = Math.max(pj, 1e-300); // clamp low
            stat += -2.0 * Math.log(pc);
            k++;
        }
        if (k == 0) return 1.0;
        int df = 2 * k;
        org.apache.commons.math3.distribution.ChiSquaredDistribution chi2 =
                new org.apache.commons.math3.distribution.ChiSquaredDistribution(df);
        double cdf = chi2.cumulativeProbability(stat);
        return 1.0 - cdf; // upper-tail p-value
    }
}

