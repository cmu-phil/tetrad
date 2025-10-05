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
 * Makes a new data set in which cases in the given data set that have been assigned multiplicies other than n = 1 are
 * copied out n times. This increases the number of rows in the data set.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public final class CaseExpander implements DataFilter {

    /**
     * Initializes a new instance of the CaseExpander class.
     */
    public CaseExpander() {

    }

    private static DataSet expand(DataSet dataSet) {
        int rows = 0;

        for (int i = 0; i < dataSet.getNumRows(); i++) {
//            int caseMultiplier = dataSet.getMultiplier(i);

//            for (int k = 0; k < caseMultiplier; k++) {
            ++rows;
//            }
        }

        DataSet newDataSet =
                new BoxDataSet(new VerticalDoubleDataBox(rows, dataSet.getVariables().size()), dataSet.getVariables());
        int cols = dataSet.getNumColumns();
        int index = -1;

        for (int i = 0; i < dataSet.getNumRows(); i++) {
//            int caseMultiplier = dataSet.getMultiplier(i);

//            for (int k = 0; k < caseMultiplier; k++) {
            ++index;

            for (int j = 0; j < cols; j++) {
                if (dataSet.getVariable(j) instanceof ContinuousVariable) {
                    newDataSet.setDouble(index, j, dataSet.getDouble(i, j));
                } else if (dataSet.getVariable(j) instanceof DiscreteVariable) {
                    newDataSet.setInt(index, j, dataSet.getInt(i, j));
                } else {
                    throw new IllegalStateException("Expecting either a " +
                                                    "continuous or a discrete variable.");
                }

            }
//            }
        }

        return newDataSet;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Expands cases for the given dataset.
     */
    public DataSet filter(DataSet dataSet) {
        return CaseExpander.expand(dataSet);
    }
}






