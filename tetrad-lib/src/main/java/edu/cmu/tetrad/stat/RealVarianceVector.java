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
 * Feb 9, 2016 3:14:08 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 * @version $Id: $Id
 */
public class RealVarianceVector implements RealVariance {

    private final double[][] data;

    private final int numOfRows;

    private final int numOfCols;

    /**
     * <p>Constructor for RealVarianceVector.</p>
     *
     * @param data an array of  objects
     */
    public RealVarianceVector(double[][] data) {
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
    public double[] compute(boolean biasCorrected) {
        double[] meanVariance = computeMeans();

        for (int col = 0; col < this.numOfCols; col++) {
            double mean = meanVariance[col];
            double value = 0;
            double squareValue = 0;
            for (int row = 0; row < this.numOfRows; row++) {
                double val = this.data[row][col] - mean;
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

