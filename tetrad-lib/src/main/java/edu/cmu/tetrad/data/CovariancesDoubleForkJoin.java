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
 */
public class CovariancesDoubleForkJoin {
    static final long serialVersionUID = 23L;

    private final int numOfCols;
    private final double[][] covariances;

    public CovariancesDoubleForkJoin(double[][] data, boolean biasCorrected) {
        this.numOfCols = data[0].length;
        RealCovarianceMatrixForkJoin cov = new RealCovarianceMatrixForkJoin(data, 10 * Runtime.getRuntime().availableProcessors());
        this.covariances = cov.compute(biasCorrected);
    }

    public double covariance(int i, int j) {
        return covariances[i][j];
    }

    public int size() {
        return numOfCols;
    }

    public double[][] getMatrix() {
        int[] rows = new int[size()];
        for (int i = 0; i < rows.length; i++) rows[i] = i;
        return getSubMatrix(rows, rows);
    }

    public double[][] getSubMatrix(int[] rows, int[] cols) {
        double[][] submatrix = new double[rows.length][cols.length];

        for (int i = 0; i < rows.length; i++) {
            for (int j = 0; j < cols.length; j++) {
                submatrix[i][j] = covariances[rows[i]][cols[j]];
            }
        }

        return submatrix;
    }
}
