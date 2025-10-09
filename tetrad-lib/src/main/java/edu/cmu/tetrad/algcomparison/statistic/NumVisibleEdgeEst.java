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
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.util.Parameters;
import org.apache.commons.math3.util.FastMath;

import java.io.Serial;
import java.util.ArrayList;

/**
 * NumVisibleEdgeEst is a class that implements the Statistic interface. It calculates the number of X-->Y edges that
 * are visible in the estimated PAG.
 */
public class NumVisibleEdgeEst implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs a new instance of the statistic.
     */
    public NumVisibleEdgeEst() {

    }

    /**
     * Returns the abbreviation for the statistic. This will be printed at the top of each column.
     *
     * @return The abbreviation for the statistic.
     */
    @Override
    public String getAbbreviation() {
        return "#X->Y visible (E)";
    }

    /**
     * Returns a short one-line description of this statistic. This will be printed at the beginning of the report.
     *
     * @return The description of the statistic.
     */
    @Override
    public String getDescription() {
        return "Number of X-->Y for which X-->Y visible in estimated PAG";
    }

    /**
     * Returns the number of X-->Y edges that are visible in the estimated PAG.
     *
     * @param trueDag
     * @param trueGraph  The true graph.
     * @param estGraph   The estimated graph.
     * @param dataModel  The data model.
     * @param parameters The parameters
     * @return The number of X-->Y edges that are visible in the estimated PAG.
     */
    @Override
    public double getValue(Graph trueDag, Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters) {
        int tp = 0;

        GraphUtils.addEdgeSpecializationMarkup(estGraph);

        for (Edge edge : new ArrayList<>(estGraph.getEdges())) {
            if (edge.getProperties().contains(Edge.Property.nl)) {
                tp++;
            }
        }

        return tp;
    }

    /**
     * Returns the normalized value of the given value.
     *
     * @param value The value to be normalized.
     * @return The normalized value.
     */
    @Override
    public double getNormValue(double value) {
        return FastMath.tan(value);
    }
}

