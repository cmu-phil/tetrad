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
package edu.cmu.tetrad.stat;

/**
 *
 * Feb 9, 2016 3:14:08 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class RealVarianceVector implements RealVariance {

    private final double[][] data;

    private final int numOfRows;

    private final int numOfCols;

    public RealVarianceVector(double[][] data) {
        this.data = data;
        this.numOfRows = data.length;
        this.numOfCols = data[0].length;
    }

    private double[] computeMeans() {
        double[] mean = new double[numOfCols];
        for (int col = 0; col < numOfCols; col++) {
            double sum = 0;
            for (int row = 0; row < numOfRows; row++) {
                sum += data[row][col];
            }
            mean[col] = sum / numOfRows;
        }

        return mean;
    }

    @Override
    public double[] compute(boolean biasCorrected) {
        double[] meanVariance = computeMeans();

        for (int col = 0; col < numOfCols; col++) {
            double mean = meanVariance[col];
            double value = 0;
            double squareValue = 0;
            for (int row = 0; row < numOfRows; row++) {
                double val = data[row][col] - mean;
                squareValue += val * val;
                value += val;
            }
            meanVariance[col] = (biasCorrected)
                    ? (squareValue - (value * value / numOfRows)) / (numOfRows - 1.0f)
                    : (squareValue - (value * value / numOfRows)) / numOfRows;
        }

        return meanVariance;
    }

}
