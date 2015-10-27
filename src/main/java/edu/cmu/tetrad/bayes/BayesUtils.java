///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.bayes;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Node;

import java.util.List;

/**
 * Static utility methods for Bayes nets.
 *
 * @author Joseph Ramsey
 */
final class BayesUtils {

    /**
     * Ensures that the discrete variables in the given list are compatible with
     * the variables in the data set by the same names. If a variable x in the
     * list has a superlist of the categories for x in the data, the data
     * variables is changed.
     *
     * @throws IllegalArgumentException if a variable exists in the list for
     *                                  which no variable occurs in the data by
     *                                  the same name or if the variable in the
     *                                  data by the same name does not have a
     *                                  subset of its categories.
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

            if (pmCategories.equals(dataCategories)) {
                // continue.
            }
            else if (pmCategories.containsAll(dataCategories)) {
                DiscreteVariable to = new DiscreteVariable(pmVar);
                dataSet.changeVariable(from, to);
            }
            else {
//                throw new IllegalArgumentException("The variable named " +
//                        name + " has more categories in the data than in " +
//                        "the model.");
                
                throw new IllegalArgumentException("Variable '" + name + "' " +
                        "has more categories in the data than in the model." +
                        "\n\tIn the model, the categories are: " + pmCategories + "." +
                        "\n\tIn the data, the categories are: " + dataCategories + ".");
            }
        }
    }
}





