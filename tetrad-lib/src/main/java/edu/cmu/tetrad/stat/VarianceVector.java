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

package edu.cmu.tetrad.stat;

/**
 * Feb 9, 2016 3:18:44 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 * @version $Id: $Id
 */
public class VarianceVector implements Variance {

    private final float[][] data;

    private final int numOfRows;

    private final int numOfCols;

    /**
     * <p>Constructor for VarianceVector.</p>
     *
     * @param data an array of  objects
     */
    public VarianceVector(float[][] data) {
        this.data = data;
        this.numOfRows = data.length;
        this.numOfCols = data[0].length;
    }

    private float[] computeMeans() {
        float[] mean = new float[this.numOfCols];
        for (int col = 0; col < this.numOfCols; col++) {
            float sum = 0;
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
    public float[] compute(boolean biasCorrected) {
        float[] meanVariance = computeMeans();

        for (int col = 0; col < this.numOfCols; col++) {
            float mean = meanVariance[col];
            float value = 0;
            float squareValue = 0;
            for (int row = 0; row < this.numOfRows; row++) {
                float val = this.data[row][col] - mean;
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

