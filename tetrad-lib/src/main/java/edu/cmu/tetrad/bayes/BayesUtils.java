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

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Node;

import java.util.List;

/**
 * Static utility methods for Bayes nets.
 *
 * @author josephramsey
 */
final class BayesUtils {

    /**
     * Ensures that the discrete variables in the given list are compatible with the variables in the data set by the
     * same names. If a variable x in the list has a superlist of the categories for x in the data, the data variables
     * is changed.
     *
     * @param pmVars  a {@link java.util.List} object
     * @param dataSet a {@link edu.cmu.tetrad.data.DataSet} object
     * @throws java.lang.IllegalArgumentException if a variable exists in the list for which no variable occurs in the
     *                                            data by the same name or if the variable in the data by the same name
     *                                            does not have a subset of its categories.
     */
    public static void ensureVarsInData(List<Node> pmVars,
                                        DataSet dataSet) {
        for (Node pmVar1 : pmVars) {
            DiscreteVariable pmVar = (DiscreteVariable) pmVar1;
            String name = pmVar.getName();
            DiscreteVariable from =
                    (DiscreteVariable) dataSet.getVariable(name);

            if (from == null) {
                throw new IllegalArgumentException("Variable " + pmVar + " was not in the data.");
            }

            List<String> pmCategories = pmVar.getCategories();
            List<String> dataCategories = from.getCategories();

            if (!pmCategories.equals(dataCategories)) {
                if (pmCategories.containsAll(dataCategories)) {
                    DiscreteVariable to = new DiscreteVariable(pmVar);
                    dataSet.changeVariable(from, to);
                } else {
                    throw new IllegalArgumentException("Variable '" + name + "' " +
                                                       "has more categories in the data than in the model." +
                                                       "\n\tIn the model, the categories are: " + pmCategories + "." +
                                                       "\n\tIn the data, the categories are: " + dataCategories + ".");
                }
            }
        }
    }
}






