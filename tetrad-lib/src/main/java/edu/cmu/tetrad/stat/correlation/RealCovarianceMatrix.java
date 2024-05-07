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
package edu.cmu.tetrad.stat.correlation;

/**
 * Jan 27, 2016 5:35:01 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 * @version $Id: $Id
 */
public class RealCovarianceMatrix implements RealCovariance {

    private final double[][] data;

    private final int numOfRows;

    private final int numOfCols;

    /**
     * <p>Constructor for RealCovarianceMatrix.</p>
     *
     * @param data an array of {@link double} objects
     */
    public RealCovarianceMatrix(double[][] data) {
        this.data = data;
        this.numOfRows = data.length;
        this.numOfCols = data[0].length;
    }

    private double[] computeMeans() {
        double[] mean = new double[this.numOfCols];
        for (int col = 0; col < this.numOfCols; col++) {
            double sum = 0;
            for (int row = 0; row < this.numOfRows; row++) {
                sum += this.data[row][col];
            }
            mean[col] = sum / this.numOfRows;
        }

        return mean;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double[] computeLowerTriangle(boolean biasCorrected) {
        double[] covarianceMatrix = new double[(this.numOfCols * (this.numOfCols + 1)) / 2];

        double[] mean = computeMeans();

        int index = 0;
        for (int col = 0; col < this.numOfCols; col++) {
            for (int col2 = 0; col2 < col; col2++) {
                double variance = 0;
                for (int row = 0; row < this.numOfRows; row++) {
                    variance += ((this.data[row][col] - mean[col]) * (this.data[row][col2] - mean[col2]) - variance) / (row + 1);
                }
                covarianceMatrix[index++] = biasCorrected ? variance * ((double) this.numOfRows / (double) (this.numOfRows - 1)) : variance;
            }
            double variance = 0;
            for (int row = 0; row < this.numOfRows; row++) {
                variance += ((this.data[row][col] - mean[col]) * (this.data[row][col] - mean[col]) - variance) / (row + 1);
            }
            covarianceMatrix[index++] = biasCorrected ? variance * ((double) this.numOfRows / (double) (this.numOfRows - 1)) : variance;
        }

        return covarianceMatrix;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double[][] compute(boolean biasCorrected) {
        double[][] covarianceMatrix = new double[this.numOfCols][this.numOfCols];

        double[] mean = computeMeans();

        for (int col = 0; col < this.numOfCols; col++) {
            for (int col2 = 0; col2 < col; col2++) {
                double variance = 0;
                for (int row = 0; row < this.numOfRows; row++) {
                    variance += ((this.data[row][col] - mean[col]) * (this.data[row][col2] - mean[col2]) - variance) / (row + 1);
                }
                variance = biasCorrected ? variance * ((double) this.numOfRows / (double) (this.numOfRows - 1)) : variance;
                covarianceMatrix[col][col2] = variance;
                covarianceMatrix[col2][col] = variance;
            }
            double variance = 0;
            for (int row = 0; row < this.numOfRows; row++) {
                variance += ((this.data[row][col] - mean[col]) * (this.data[row][col] - mean[col]) - variance) / (row + 1);
            }
            covarianceMatrix[col][col] = biasCorrected ? variance * ((double) this.numOfRows / (double) (this.numOfRows - 1)) : variance;
        }

        return covarianceMatrix;
    }

}
