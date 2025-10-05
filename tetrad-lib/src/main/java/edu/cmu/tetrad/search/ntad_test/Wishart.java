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

package edu.cmu.tetrad.search.ntad_test;

import edu.cmu.tetrad.util.StatUtils;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.ejml.dense.row.CommonOps_DDRM;
import org.ejml.simple.SimpleMatrix;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The Wishart class is a concrete implementation of the NtadTest abstract class, specifically for statistical tests
 * based on the Wishart distribution. It performs calculations for tetrads and their associated p-values using
 * correlation matrices derived from the input data.
 *
 * @author bryanandrews
 */
public class Wishart extends NtadTest {

    /**
     * Constructs a Wishart object using the provided data matrix, flag for correlation or covariance matrix, and the
     * effective sample size.
     *
     * @param df           the input data matrix as a SimpleMatrix object, where each row represents an observation and
     *                     each column represents a variable
     * @param correlations a boolean flag indicating whether the provided matrix is a covariance matrix (true) or raw
     *                     data requiring covariance computation (false)
     * @param ess          the effective sample size; must be -1 or greater than 1. If -1, the sample size is assumed to
     *                     be the number of rows in the data matrix
     */
    public Wishart(SimpleMatrix df, boolean correlations, int ess) {
        super(df, correlations, ess);
    }

    /**
     * Computes the p-value for testing the null hypothesis of tetrad constraints in a given correlation matrix. This
     * method leverages resampling (if specified) to calculate the required submatrices of the correlation matrix for
     * evaluating independence constraints between tetrad pairs.
     *
     * @param ntad     a 2D array where the first row represents the indices of the first group of variables (a) and the
     *                 second row represents the indices of the second group of variables (b)
     * @param resample a boolean flag indicating whether resampling should be applied to the input data
     * @param frac     a double value representing the fraction of rows to sample for resampling, where 0.0 &lt;= frac
     *                 &lt;= 1.0
     * @return a double value representing the two-tailed p-value for the tetrad hypothesis test
     */
    @Override
    public double ntad(int[][] ntad, boolean resample, double frac) {
        SimpleMatrix S = resample ? computeCorrelations(sampleRows(df, frac)) : this.S;
        int[] a = ntad[0];
        int[] b = ntad[1];
        int n = resample ? (int) (frac * this.ess) : this.ess;

        double sigma2 = (double) (n + 1) / (n - 1) * determinant(StatUtils.extractSubMatrix(S, a, a)) * determinant(StatUtils.extractSubMatrix(S, b, b))
                        - determinant(StatUtils.extractSubMatrix(S, concat(a, b), concat(a, b))) / (n - 2);

        double z_score = determinant(StatUtils.extractSubMatrix(S, a, b)) / Math.sqrt(sigma2);
        return 2 * new NormalDistribution().cumulativeProbability(-Math.abs(z_score));
    }

    private int[] concat(int[] a, int[] b) {
        int[] result = new int[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    private double determinant(SimpleMatrix matrix) {
        return CommonOps_DDRM.det(matrix.getDDRM());
    }

    @Override
    public double ntad(int[][] ntad) {
        return ntad(ntad, false, 1);
    }

    /**
     * Returns the p-value for the tetrad. This constructor is required by the interface, though in truth it will throw
     * and exception if more than one tetrad is provided.
     *
     * @param ntads A single tetrad.
     * @return The p-value for the tetrad.
     */
    @Override
    public double ntads(int[][]... ntads) {
        List<int[][]> tetList = new ArrayList<>();
        Collections.addAll(tetList, ntads);
        return ntads(tetList);
    }

    /**
     * Returns the p-value for the tetrad. This constructor is required by the interface, though in truth it will throw
     * and exception if more than one tetrad is provided.
     *
     * @param ntads A single tetrad.
     * @return The p-value for the tetrad.
     */
    @Override
    public double ntads(List<int[][]> ntads) {
        if (ntads.size() != 1) {
            throw new IllegalArgumentException("Only one tetrad is allowed for the Wishart test.");
        }

        return ntad(ntads.getFirst());
    }
}

