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

package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;

import java.io.Serializable;

/**
 * The interface that each statistic needs to implement.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public interface Statistic extends Serializable {
    /**
     * Constant <code>serialVersionUID=23L</code>
     */
    long serialVersionUID = 23L;

    /**
     * The abbreviation for the statistic. This will be printed at the top of each column.
     *
     * @return This abbreviation.
     */
    String getAbbreviation();

    /**
     * Returns a short one-line description of this statistic. This will be printed at the beginning of the report.
     *
     * @return This description.
     */
    String getDescription();

    default double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters) {
        return getValue(null, trueGraph, estGraph, dataModel, parameters);
    }

    /**
     * Returns the value of this statistic, given the true graph and the estimated graph.
     *
     * @param trueDag
     * @param trueGraph  The true graph (DAG, CPDAG, PAG_of_the_true_DAG).
     * @param estGraph   The estimated graph (same type).
     * @param dataModel  The data model (can be null).
     * @param parameters The parameters (can be null).
     * @return The value of the statistic.
     */
    double getValue(Graph trueDag, Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters);

    /**
     * Returns the value of this statistic, given the true graph and the estimated graph.
     *
     * @param trueGraph The true graph (DAG, CPDAG, PAG_of_the_true_DAG).
     * @param estGraph  The estimated graph (same type).
     * @param dataModel The data model (can be null).
     * @return The value of the statistic.
     */
    default double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        return getValue(null, trueGraph, estGraph, dataModel, null);
    }

    /**
     * Returns the value of this statistic, given the true graph and the estimated graph.
     *
     * @param trueGraph  The true graph (DAG, CPDAG, PAG_of_the_true_DAG).
     * @param estGraph   The estimated graph (same type).
     * @param parameters The parameters (can be null).
     * @return The value of the statistic.
     */
    default double getValue(Graph trueGraph, Graph estGraph, Parameters parameters) {
        return getValue(null, trueGraph, estGraph, null, parameters);
    }

    /**
     * Returns the value of this statistic, given the true graph and the estimated graph.
     *
     * @param trueGraph The true graph (DAG, CPDAG, PAG_of_the_true_DAG).
     * @param estGraph  The estimated graph (same type).
     * @return The value of the statistic.
     */
    default double getValue(Graph trueGraph, Graph estGraph) {
        return getValue(null, trueGraph, estGraph, null, null);
    }

    /**
     * Returns a mapping of the statistic to the interval [0, 1], with higher being better. This is used for a
     * calculation of a utility for an algorithm. If the statistic is already between 0 and 1, you can just return the
     * statistic.
     *
     * @param value The value of the statistic.
     * @return The weight of the statistic, 0 to 1, higher is better.
     */
    double getNormValue(double value);
}

