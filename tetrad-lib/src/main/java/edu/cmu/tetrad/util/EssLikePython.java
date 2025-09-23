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

package edu.cmu.tetrad.util;

import org.ejml.simple.SimpleMatrix;

import java.util.Random;

/**
 * The EssLikePython class provides methods for estimating the Effective Sample Size (ESS) of a dataset using procedures
 * that closely mimic functionality found my Python script. This includes column standardization, row sampling, and
 * computation of correlations to derive the ESS value.
 */
public final class EssLikePython {

    /**
     * Default constructor for the EssLikePython class. Initializes a new instance of the EssLikePython class.
     */
    public EssLikePython() {
    }

    /**
     * Estimates the effective sample size (ESS) and average row correlation (avgRowCorr) from a given dataset, by
     * column-standardizing, sampling rows, and computing row-wise correlations. Optionally clamps the correlation to
     * ensure non-negativity.
     *
     * @param X                the input data matrix where rows represent observations and columns represent variables
     * @param sampleSize       the number of rows to sample without replacement during the estimation process
     * @param clampNonnegative a flag indicating whether to clamp avgRowCorr to nonnegative values
     * @param rng              a Random object to control the random sampling of rows (if null, a default seed is used)
     * @return a Result object containing the estimated avgRowCorr, computed ESS, and the number of rows used
     */
    public static Result estimateLikePython(SimpleMatrix X, int sampleSize, boolean clampNonnegative, Random rng) {
        if (rng == null) rng = new Random(0);

        final int N = X.getNumRows();
        final int P = X.getNumCols();
        if (N < 2 || P < 1) return new Result(0.0, N, Math.max(0, N));

        // 1) Column-standardize with ddof=0 (population)
        SimpleMatrix Z = new SimpleMatrix(N, P);
        for (int j = 0; j < P; j++) {
            double mean = 0.0;
            for (int i = 0; i < N; i++) mean += X.get(i, j);
            mean /= N;

            double varPop = 0.0;
            for (int i = 0; i < N; i++) {
                double d = X.get(i, j) - mean;
                varPop += d * d;
            }
            varPop /= N; // ddof=0
            double sd = Math.sqrt(Math.max(varPop, 1e-24));

            for (int i = 0; i < N; i++) {
                Z.set(i, j, (X.get(i, j) - mean) / sd);
            }
        }


        // Row standardize Z
        for (int i = 0; i < N; i++) {
            double mean = 0.0;
            for (int j = 0; j < P; j++) mean += Z.get(i, j);
            mean /= P;

            double varPop = 0.0;
            for (int j = 0; j < P; j++) {
                double d = Z.get(i, j) - mean;
                varPop += d * d;
            }
            varPop /= P; // ddof=0
            double sd = Math.sqrt(Math.max(varPop, 1e-24));

            for (int j = 0; j < P; j++) {
                Z.set(i, j, (Z.get(i, j) - mean) / sd);
            }
        }

        // 2) Sample m rows without replacement (like df.sample(..., random_state=0))
        int m = Math.min(sampleSize, N);
        int[] rows = shuffledRange(N, rng);
        int[] pick = new int[m];
        System.arraycopy(rows, 0, pick, 0, m);

        // 3) Build Y = selected m rows; then row-standardize (ddof=0)
        SimpleMatrix Y = new SimpleMatrix(m, P);
        for (int t = 0; t < m; t++) {
            int i = pick[t];
            for (int j = 0; j < P; j++) Y.set(t, j, Z.get(i, j));
        }

        double[] rMean = new double[m];
        double[] rStd = new double[m];
        for (int i = 0; i < m; i++) {
            double mean = 0.0;
            for (int j = 0; j < P; j++) mean += Y.get(i, j);
            mean /= P;
            rMean[i] = mean;

            double varPop = 0.0;
            for (int j = 0; j < P; j++) {
                double d = Y.get(i, j) - mean;
                varPop += d * d;
            }
            varPop /= P; // ddof=0
            rStd[i] = Math.sqrt(Math.max(varPop, 1e-24));
        }

        for (int i = 0; i < m; i++) {
            double mi = rMean[i], si = rStd[i];
            for (int j = 0; j < P; j++) Y.set(i, j, (Y.get(i, j) - mi) / si);
        }

        // 4) Full rowârow correlation on Y (since each row is mean 0, sd 1 with ddof=0, the corr reduces to dot/P)
        double sumOffDiag = 0.0;
        long numPairs = 0L;
        for (int i = 0; i < m; i++) {
            for (int j = i + 1; j < m; j++) {
                double dot = 0.0;
                for (int k = 0; k < P; k++) dot += Y.get(i, k) * Y.get(j, k);
                // With ddof=0 row standardization, Pearson reduces to dot/P (not P-1)
                double rij = dot / P;
                sumOffDiag += rij;
                numPairs++;
            }
        }

        double avgCorr = (numPairs > 0) ? (sumOffDiag / numPairs) : 0.0;

        // 5) ESS with optional clamp to keep it in (0, N]
        if (clampNonnegative && avgCorr < 0.0) avgCorr = 0.0;
        if (clampNonnegative && avgCorr >= 0.999999) avgCorr = 0.999999;

        double ess = N / (1.0 + (N - 1.0) * avgCorr);
        return new Result(avgCorr, ess, m);
    }

    private static int[] shuffledRange(int n, Random rng) {
        int[] idx = new int[n];
        for (int i = 0; i < n; i++) idx[i] = i;
        for (int i = n - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int tmp = idx[i];
            idx[i] = idx[j];
            idx[j] = tmp;
        }
        return idx;
    }

    /**
     * Encapsulates the results of an estimation process, including average row correlation, effective sample size
     * (ESS), and the number of rows used in the computation.
     * <p>
     * avgRowCorr represents the average off-diagonal value of the row-row correlation matrix derived during the
     * estimation process.
     * <p>
     * ess is the effective sample size, calculated as N / (1 + (N-1)*avgRowCorr), which may optionally be clamped.
     * <p>
     * mRowsUsed indicates the number of rows sampled and used in the computation.
     */
    public static final class Result {
        /**
         * Represents the average off-diagonal value of the row-row correlation matrix computed during an estimation
         * process. This value is used to assess the interdependence between rows in the data and influences other
         * derived statistics such as the effective sample size (ESS).
         */
        public final double avgRowCorr; // same definition as Python function
        /**
         * Represents the effective sample size (ESS) calculated as N / (1 + (N-1)*avgRowCorr). This statistic is used
         * to adjust the sample size based on the average row correlation of the dataset. The value may optionally be
         * clamped to a specific range, depending on the application context.
         */
        public final double ess;        // N / (1 + (N-1)*avgRowCorr) (optionally clamped)
        /**
         * Represents the number of rows used in the computation.
         */
        public final int nRowsUsed;

        /**
         * Constructs a Result object encapsulating the average row correlation, effective sample size (ESS), and the
         * number of rows used in the computation.
         *
         * @param avgRowCorr the average off-diagonal value of the row-row correlation matrix computed during an
         *                   estimation process
         * @param ess        the effective sample size, calculated as N / (1 + (N-1)*avgRowCorr), potentially clamped
         *                   based on the application context
         * @param mRowsUsed  the number of rows sampled and used during the computation
         */
        Result(double avgRowCorr, double ess, int mRowsUsed) {
            this.avgRowCorr = avgRowCorr;
            this.ess = ess;
            this.nRowsUsed = mRowsUsed;
        }

        /**
         * Returns a string representation of the Result object, which includes the average row correlation
         * (avgRowCorr), effective sample size (ESS), and the number of rows used in the computation (m).
         *
         * @return a formatted string containing avgRowCorr, ESS, and m values
         */
        @Override
        public String toString() {
            return String.format("avgRowCorr=%.6f, ESS=%.2f, m=%d", avgRowCorr, ess, nRowsUsed);
        }
    }
}
