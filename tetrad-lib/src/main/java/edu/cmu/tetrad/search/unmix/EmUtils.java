/// ////////////////////////////////////////////////////////////////////////////
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
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.unmix;

/**
 * A utility class for operations commonly used in the Expectation-Maximization (EM) algorithm. This class contains
 * methods to handle responsibilities, calculate metrics, and perform numerical operations in a numerically stable
 * manner.
 * <p>
 * This is a final class with a private constructor to indicate that it is a utility class and should not be
 * instantiated.
 */
public final class EmUtils {

    private EmUtils() {
    }

    /**
     * Maps the most likely label for each data point based on the given responsibility matrix.
     * For each row in the responsibility matrix, this method identifies the index of the maximum value
     * (maximum responsibility), which represents the most likely label for the corresponding data point.
     *
     * @param responsibilities a 2D array where each row represents the responsibilities (probabilities)
     *                         for a data point belonging to each cluster. The dimensions of the
     *                         array are n x K, where n is the number of data points, and K is the
     *                         number of clusters.
     * @return an array of integers containing the most likely label (index) for each data point.
     *         The length of the returned array is equal to the number of rows in the input matrix.
     */
    public static int[] mapLabels(double[][] responsibilities) {
        int n = responsibilities.length;
        if (n == 0) return new int[0];
        int K = responsibilities[0].length;
        int[] z = new int[n];
        for (int i = 0; i < n; i++) {
            int arg = 0;
            double best = responsibilities[i][0];
            for (int k = 1; k < K; k++) {
                double v = responsibilities[i][k];
                if (v > best) {
                    best = v;
                    arg = k;
                }
            }
            z[i] = arg;
        }
        return z;
    }

    /**
     * Computes the Bayesian Information Criterion (BIC) for the given Gaussian Mixture Model and the number of data points.
     * The BIC is a criterion for model selection among a finite set of models; the model with the lowest BIC is preferred.
     *
     * @param model the Gaussian Mixture Model used to compute the BIC value.
     * @param n the number of data points in the dataset.
     * @return the computed BIC value for the given model and dataset size.
     */
    public static double bic(GaussianMixtureEM.Model model, int n) {
        return model.bic(n);
    }

    /**
     * Computes the log-sum-exp of an array of doubles. This function is commonly used in numerical computations
     * to perform operations in the log space, which helps prevent underflow or overflow when dealing with very
     * large or very small numbers.
     *
     * @param a an array of doubles for which the log-sum-exp needs to be calculated.
     * @return the log-sum-exp of the input array.
     */
    public static double logSumExp(double[] a) {
        double m = Double.NEGATIVE_INFINITY;
        for (double v : a) if (v > m) m = v;
        double s = 0.0;
        for (double v : a) s += Math.exp(v - m);
        return m + Math.log(s);
    }

    /**
     * Normalizes the elements of the given array in-place so that their sum equals 1.
     * If the sum of the elements is less than or equal to 0, the array is set to a uniform distribution.
     *
     * @param p an array of doubles representing the values to be normalized. The array will be modified in-place.
     * @return the sum of the original elements in the input array before normalization.
     *         If the sum is less than or equal to 0, the method returns 0.0.
     */
    public static double normalizeInPlace(double[] p) {
        double s = 0.0;
        for (double v : p) s += v;
        if (s <= 0) {
            double u = 1.0 / p.length;
            for (int i = 0; i < p.length; i++) p[i] = u;
            return 0.0;
        }
        for (int i = 0; i < p.length; i++) p[i] /= s;
        return s;
    }
}
