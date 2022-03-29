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
 * Feb 9, 2016 3:18:44 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class VarianceVector implements Variance {

    private final float[][] data;

    private final int numOfRows;

    private final int numOfCols;

    public VarianceVector(final float[][] data) {
        this.data = data;
        this.numOfRows = data.length;
        this.numOfCols = data[0].length;
    }

    private float[] computeMeans() {
        final float[] mean = new float[this.numOfCols];
        for (int col = 0; col < this.numOfCols; col++) {
            float sum = 0;
            for (int row = 0; row < this.numOfRows; row++) {
                sum += this.data[row][col];
            }
            mean[col] = sum / this.numOfRows;
        }

        return mean;
    }

    @Override
    public float[] compute(final boolean biasCorrected) {
        final float[] meanVariance = computeMeans();

        for (int col = 0; col < this.numOfCols; col++) {
            final float mean = meanVariance[col];
            float value = 0;
            float squareValue = 0;
            for (int row = 0; row < this.numOfRows; row++) {
                final float val = this.data[row][col] - mean;
                squareValue += val * val;
                value += val;
            }
            meanVariance[col] = (biasCorrected)
                    ? (squareValue - (value * value / this.numOfRows)) / (this.numOfRows - 1.0f)
                    : (squareValue - (value * value / this.numOfRows)) / this.numOfRows;
        }

        return meanVariance;
    }

}
