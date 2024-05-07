/*
 * Copyright (C) 2016 University of Pittsburgh.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package edu.cmu.tetrad.data;

import edu.cmu.tetrad.stat.correlation.RealCovarianceMatrixForkJoin;

/**
 * Computes covariances using the standard calculation.
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 * @author Joseph D. Ramsey
 * @version $Id: $Id
 */
public class CovariancesDoubleForkJoin {
    private static final long serialVersionUID = 23L;

    private final int numOfCols;
    private final double[][] covariances;

    /**
     * <p>Constructor for CovariancesDoubleForkJoin.</p>
     *
     * @param data          an array of {@link double} objects
     * @param biasCorrected a boolean
     */
    public CovariancesDoubleForkJoin(double[][] data, boolean biasCorrected) {
        this.numOfCols = data[0].length;
        int numThreads = Runtime.getRuntime().availableProcessors();

        // On a small machine, we use fewer threads to avoid fork-join out of memory error.
        // josephramsey 2024-2-19
        if (Runtime.getRuntime().availableProcessors() <= 8) {
            numThreads /= 2;
        }

        RealCovarianceMatrixForkJoin cov = new RealCovarianceMatrixForkJoin(data, numThreads);
        this.covariances = cov.compute(biasCorrected);
    }

    /**
     * <p>covariance.</p>
     *
     * @param i a int
     * @param j a int
     * @return a double
     */
    public double covariance(int i, int j) {
        return this.covariances[i][j];
    }

    /**
     * <p>size.</p>
     *
     * @return a int
     */
    public int size() {
        return this.numOfCols;
    }

    /**
     * <p>getMatrix.</p>
     *
     * @return an array of {@link double} objects
     */
    public double[][] getMatrix() {
        int[] rows = new int[size()];
        for (int i = 0; i < rows.length; i++) rows[i] = i;
        return getSubMatrix(rows, rows);
    }

    /**
     * <p>getSubMatrix.</p>
     *
     * @param rows an array of {@link int} objects
     * @param cols an array of {@link int} objects
     * @return an array of {@link double} objects
     */
    public double[][] getSubMatrix(int[] rows, int[] cols) {
        double[][] submatrix = new double[rows.length][cols.length];

        for (int i = 0; i < rows.length; i++) {
            for (int j = 0; j < cols.length; j++) {
                submatrix[i][j] = this.covariances[rows[i]][cols[j]];
            }
        }

        return submatrix;
    }
}
