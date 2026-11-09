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

package edu.cmu.tetrad.algcomparison.utils;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;

import java.util.List;

/**
 * An algorithm that can take several imputations of one data set with missing values and run ONE search over them,
 * with the uncertainty from the missing values carried into each of its statistical decisions. This is what the
 * poolImputations parameter asks for.
 * <p>
 * It is distinct from pooling independent data sets (the poolDataSets parameter, IMaGES-style): imputations share
 * their observed values, so adding their scores or combining their p-values as independent would count the same rows
 * once per imputation.
 *
 * @author josephramsey
 */
public interface PoolsImputations {

    /**
     * Runs one search over the imputations.
     *
     * @param imputations the imputed data sets: the same variables, in the same order, and the same number of rows.
     * @param parameters  the parameters.
     * @return the graph.
     * @throws InterruptedException if interrupted.
     */
    Graph searchImputations(List<DataModel> imputations, Parameters parameters) throws InterruptedException;
}
