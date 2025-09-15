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

package edu.cmu.tetrad.util;

import edu.cmu.tetrad.data.DataTransforms;
import org.ejml.simple.SimpleMatrix;

/**
 * Utility class for estimating the average pairwise row correlation of a data matrix and computing the effective sample
 * size (Neff) based on the correlations.
 * <p>
 * The core functionality involves estimating the Neff value as N / (1 + (N-1)*rhoHat), where rhoHat is the average
 * correlation between rows. The estimation process standardizes the input data matrix, samples a defined number of
 * random row pairs, calculates pairwise correlations, and adjusts the results to avoid negative or singular
 * computations.
 * <p>
 * This class is designed to handle computation over larger datasets by allowing a maximum number of row pairs to
 * sample, ensuring computational efficiency, and avoiding issues caused by excessively large row combinations.
 */
public final class RowCorrelationEffN {

    /**
     * Constructs a new instance of the RowCorrelationEffN class.
     */
    public RowCorrelationEffN() {

    }

    /**
     * Estimates average pairwise row correlation (by sampling pairs) and returns Neff = N / (1 + (N-1)*rhoHat). Columns
     * are standardized first.
     * <p>
     * If the sampled average correlation is &lt; 0, we clamp it to 0 so Neff = N. If itâs &ge; 1, we clamp slightly
     * below 1 to avoid division-by-zero.
     *
     * @param X                data matrix N x P (rows = samples, cols = features)
     * @param maxPairsToSample number of random row pairs to sample (cap at C(N,2))
     * @param N                the number of rows in the data matrix
     * @return the result of the estimation
     */
    public static Result estimate(SimpleMatrix X,
                                  int maxPairsToSample,
                                  int N) {
        X = DataTransforms.standardizeData(new Matrix(X)).getSimpleMatrix();

        double[][] _X = X.toArray2();
        double sumR = 0.0;
        int used = 0;

        int numTries = X.getNumRows() * (X.getNumRows() - 1) / 2;
        if (numTries > maxPairsToSample) {
            numTries = maxPairsToSample;
        }

        for (int i = 0; i < numTries; i++) {
            int j = RandomUtil.getInstance().nextInt(X.getNumRows());
            int k = RandomUtil.getInstance().nextInt(X.getNumRows());

            if (j <= k) {
                numTries--;
                continue;
            }

            double r = StatUtils.correlation(_X[j], _X[k]);
            sumR += r;
            used++;
        }

        double rhoHat = (used > 0) ? (sumR / used) : 0.0;

        System.out.println("Rho Hat: " + rhoHat);

        if (rhoHat < 0.0) rhoHat = 0.0;
        if (rhoHat >= 0.999999) rhoHat = 0.999999;

        double neff = N / (1.0 + (N - 1.0) * rhoHat);
        return new Result(rhoHat, neff, used);
    }

    private static double cosineAcrossFeatures(SimpleMatrix Z, int i, int j, double[] rowNorm) {
        double dot = 0.0;
        int P = Z.getNumCols();
        for (int k = 0; k < P; k++) dot += Z.get(i, k) * Z.get(j, k);
        return dot / (rowNorm[i] * rowNorm[j]);
    }

    // ----------- helpers -----------

    private static double pearsonAcrossFeatures(SimpleMatrix Z, int i, int j, double[] rowMean, double[] rowStd) {
        double num = 0.0;
        int P = Z.getNumCols();
        double mi = rowMean[i], mj = rowMean[j];
        double si = rowStd[i], sj = rowStd[j];
        for (int k = 0; k < P; k++) {
            double ai = Z.get(i, k) - mi;
            double bj = Z.get(j, k) - mj;
            num += ai * bj;
        }
        return num / ((P - 1.0) * si * sj);
    }

    /**
     * Represents the result of an average pairwise row correlation estimation, containing the adjusted average row
     * correlation value, the effective sample size, and the number of row pairs used in the computation.
     * <p>
     * This class is immutable and its fields are final to ensure thread safety.
     */
    public static final class Result {
        /**
         * The adjusted average pairwise row correlation value, computed as rho_hat and clamped to the range [0, 1).
         * This value is an estimate of the average linear correlation between pairs of rows within a dataset, after
         * applying adjustments to restrict it within the specified bounds for stability and interpretability.
         */
        public final double avgRowCorrelation; // rho_hat after clamping to [0,1)
        /**
         * The effective sample size, calculated as N / (1 + (N-1) * rho_hat).
         */
        public final double effN;              // N / (1 + (N-1)*rho_hat)
        /**
         * The number of row pairs used in the calculation.
         */
        public final int pairsUsed;

        /**
         * Constructs a Result instance with the specified adjusted average row correlation value, effective sample
         * size, and the number of row pairs used.
         *
         * @param avgRowCorrelation the adjusted average row correlation value (rho_hat), clamped to the range [0, 1)
         * @param effN              the effective sample size, calculated as N / (1 + (N-1) * rho_hat)
         * @param pairsUsed         the number of row pairs used in the calculation
         */
        private Result(double avgRowCorrelation, double effN, int pairsUsed) {
            this.avgRowCorrelation = avgRowCorrelation;
            this.effN = effN;
            this.pairsUsed = pairsUsed;
        }

        /**
         * Returns a string representation of the result, including the adjusted average row correlation value
         * (rho_hat), the effective sample size (Neff), and the number of row pairs used in the computation.
         *
         * @return a formatted string containing the rho_hat value (up to six decimal places), the Neff value (up to two
         * decimal places), and the number of pairs used.
         */
        @Override
        public String toString() {
            return String.format("rho_hat=%.6f, Neff=%.2f, pairs=%d", avgRowCorrelation, effN, pairsUsed);
        }
    }
}
