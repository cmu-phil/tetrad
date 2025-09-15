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

package edu.cmu.tetrad.bayes;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Node;

import java.util.LinkedList;
import java.util.List;

/**
 * Returns a data set in variables for columns with missing values are augmented with an extra category that represents
 * the missing values, with missing values being reported as belong this category.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public final class ExtraCategoryInterpolator implements DataFilter {

    /**
     * Constructs a new instance of the algorithm.
     */
    public ExtraCategoryInterpolator() {
    }

    /**
     * {@inheritDoc}
     */
    public DataSet filter(DataSet dataSet) {

        // Why does it have to be discrete? Why can't we simply expand
        // whatever discrete columns are there and leave the continuous
        // ones untouched? jdramsey 7/4/2005

        List<Node> variables = new LinkedList<>();

        // Add all of the variables to the new data set.
        for (int j = 0; j < dataSet.getNumColumns(); j++) {
            Node _var = dataSet.getVariable(j);

            if (!(_var instanceof DiscreteVariable variable)) {
                variables.add(_var);
                continue;
            }

            String oldName = variable.getName();
            List<String> oldCategories = variable.getCategories();
            List<String> newCategories = new LinkedList<>(oldCategories);

            String newCategory = "Missing";
            int _j = 0;

            while (oldCategories.contains(newCategory)) {
                newCategory = "Missing" + (++_j);
            }

            newCategories.add(newCategory);
            String newName = oldName + "+";
            DiscreteVariable newVariable = new DiscreteVariable(newName, newCategories);

            variables.add(newVariable);
        }

        DataSet newDataSet = new BoxDataSet(new DoubleDataBox(dataSet.getNumRows(), variables.size()), variables);

        // Copy old values to new data set, replacing missing values with new
        // "MissingValue" categories.
        for (int j = 0; j < dataSet.getNumColumns(); j++) {
            Node _var = dataSet.getVariable(j);

            if (_var instanceof ContinuousVariable) {
                for (int i = 0; i < dataSet.getNumRows(); i++) {
                    newDataSet.setDouble(i, j, dataSet.getDouble(i, j));
                }
            } else if (_var instanceof DiscreteVariable variable) {
                int numCategories = variable.getNumCategories();

                for (int i = 0; i < dataSet.getNumRows(); i++) {
                    int value = dataSet.getInt(i, j);

                    if (value == DiscreteVariable.MISSING_VALUE) {
                        newDataSet.setInt(i, j, numCategories);
                    } else {
                        newDataSet.setInt(i, j, value);
                    }
                }
            }
        }

        return newDataSet;
    }
}






