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

package edu.cmu.tetrad.stat.correlation;

/**
 * Jan 25, 2016 2:13:26 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 * @version $Id: $Id
 */
public class CovarianceMatrix implements Covariance {

    private final float[][] data;

    private final int numOfRows;

    private final int numOfCols;

    /**
     * <p>Constructor for CovarianceMatrix.</p>
     *
     * @param data an array of  objects
     */
    public CovarianceMatrix(float[][] data) {
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
    public float[] computeLowerTriangle(boolean biasCorrected) {
        float[] covarianceMatrix = new float[(this.numOfCols * (this.numOfCols + 1)) / 2];

        float[] mean = computeMeans();

        int index = 0;
        for (int col = 0; col < this.numOfCols; col++) {
            for (int col2 = 0; col2 < col; col2++) {
                float variance = 0;
                for (int row = 0; row < this.numOfRows; row++) {
                    variance += ((this.data[row][col] - mean[col]) * (this.data[row][col2] - mean[col2]) - variance) / (row + 1);
                }
                covarianceMatrix[index++] = biasCorrected ? variance * ((float) this.numOfRows / (float) (this.numOfRows - 1)) : variance;
            }
            float variance = 0;
            for (int row = 0; row < this.numOfRows; row++) {
                variance += ((this.data[row][col] - mean[col]) * (this.data[row][col] - mean[col]) - variance) / (row + 1);
            }
            covarianceMatrix[index++] = biasCorrected ? variance * ((float) this.numOfRows / (float) (this.numOfRows - 1)) : variance;
        }

        return covarianceMatrix;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public float[][] compute(boolean biasCorrected) {
        float[][] covarianceMatrix = new float[this.numOfCols][this.numOfCols];

        float[] mean = computeMeans();

        for (int col = 0; col < this.numOfCols; col++) {
            for (int col2 = 0; col2 < col; col2++) {
                float variance = 0;
                for (int row = 0; row < this.numOfRows; row++) {
                    variance += ((this.data[row][col] - mean[col]) * (this.data[row][col2] - mean[col2]) - variance) / (row + 1);
                }
                variance = biasCorrected ? variance * ((float) this.numOfRows / (float) (this.numOfRows - 1)) : variance;
                covarianceMatrix[col][col2] = variance;
                covarianceMatrix[col2][col] = variance;
            }
            float variance = 0;
            for (int row = 0; row < this.numOfRows; row++) {
                variance += ((this.data[row][col] - mean[col]) * (this.data[row][col] - mean[col]) - variance) / (row + 1);
            }
            covarianceMatrix[col][col] = biasCorrected ? variance * ((float) this.numOfRows / (float) (this.numOfRows - 1)) : variance;
        }

        return covarianceMatrix;
    }

}

