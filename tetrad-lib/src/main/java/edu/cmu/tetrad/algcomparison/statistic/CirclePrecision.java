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

import edu.cmu.tetrad.algcomparison.statistic.utils.CircleConfusion;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;

import java.io.Serial;

/**
 * CirclePrecision is a class that implements the Statistic interface. It calculates the circle precision, which is the
 * ratio of true positive arrows to the sum of true positive arrows and false positive arrows.
 */
public class CirclePrecision implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs the statistic.
     */
    public CirclePrecision() {
    }

    /**
     * Retrieves the abbreviation for the statistic.
     *
     * @return The abbreviation.
     */
    @Override
    public String getAbbreviation() {
        return "CP";
    }

    /**
     * Returns a short one-line description of this statistic.
     *
     * @return The description of the statistic.
     */
    @Override
    public String getDescription() {
        return "Circle precision";
    }

    /**
     * Calculates the circle precision, which is the ratio of true positive arrows to the sum of true positive arrows
     * and false positive arrows.
     *
     * @param trueGraph  The true graph (DAG, CPDAG, PAG_of_the_true_DAG).
     * @param estGraph   The estimated graph (same type).
     * @param dataModel  The data model.
     * @param parameters The parameters
     * @return The calculated circle precision value.
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters) {
        CircleConfusion confusion = new CircleConfusion(trueGraph, estGraph);
        double tp = confusion.getTp();
        double fp = confusion.getFp();
        return tp / (tp + fp);
    }

    /**
     * Retrieves the normalized value of the statistic.
     *
     * @param value The value of the statistic.
     * @return The normalized value of the statistic.
     */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}

