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

package edu.cmu.tetrad.data;

/**
 * Returns a data set in which missing values in each column are filled using the mean of that column.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public final class MeanInterpolator implements DataFilter {

    /**
     * <p>Constructor for MeanInterpolator.</p>
     */
    public MeanInterpolator() {
    }

    /**
     * {@inheritDoc}
     */
    public DataSet filter(DataSet dataSet) {
        DataSet newDataSet = dataSet.copy();

        for (int j = 0; j < newDataSet.getNumColumns(); j++) {
            if (newDataSet.getVariable(j) instanceof ContinuousVariable) {
                double sum = 0.0;
                int count = 0;

                for (int i = 0; i < newDataSet.getNumRows(); i++) {
                    if (!Double.isNaN(newDataSet.getDouble(i, j))) {
                        sum += newDataSet.getDouble(i, j);
                        count++;
                    }
                }

                double mean = sum / count;

                for (int i = 0; i < newDataSet.getNumRows(); i++) {
                    if (Double.isNaN(newDataSet.getDouble(i, j))) {
                        newDataSet.setDouble(i, j, mean);
                    }
                }
            }
        }

        return newDataSet;
    }
}




