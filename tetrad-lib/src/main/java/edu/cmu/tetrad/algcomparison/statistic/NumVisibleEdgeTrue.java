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
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.util.Parameters;
import org.apache.commons.math3.util.FastMath;

import java.io.Serial;
import java.util.ArrayList;

/**
 * A class that implements the Statistic interface to calculate the number of visible edges in the true PAG.
 */
public class NumVisibleEdgeTrue implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * A class that calculates the number of visible edges in the true PAG.
     */
    public NumVisibleEdgeTrue() {

    }

    /**
     * Retrieves the abbreviation for the statistic. This will be printed at the top of each column. The abbreviation
     * format is "#X->Y visible (T)".
     *
     * @return The abbreviation string.
     */
    @Override
    public String getAbbreviation() {
        return "#X->Y visible (T)";
    }

    /**
     * Retrieves the description of the statistic. This method returns the number of X-->Y edges for which X-->Y is
     * visible in the true PAG.
     *
     * @return The description of the statistic.
     */
    @Override
    public String getDescription() {
        return "Number of X-->Y for which X-->Y visible in true PAG";
    }

    /**
     * Retrieves the number of X-->Y edges for which X-->Y is visible in the true PAG.
     *
     * @param trueDag
     * @param trueGraph  The true PAG graph.
     * @param estGraph   The estimated PAG graph.
     * @param dataModel  The data model.
     * @param parameters The parameters
     * @return The number of X-->Y edges that are visible in the true PAG.
     */
    @Override
    public double getValue(Graph trueDag, Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters) {
        int tp = 0;

        Graph pag = GraphTransforms.dagToPag(trueGraph);
        GraphUtils.addEdgeSpecializationMarkup(pag);

        for (Edge edge : new ArrayList<>(pag.getEdges())) {
            if (edge.getProperties().contains(Edge.Property.nl)) {
                tp++;
            }
        }

        return tp;
    }

    /**
     * Returns the normalized value of a given statistic.
     *
     * @param value The original value of the statistic.
     * @return The normalized value of the statistic.
     */
    @Override
    public double getNormValue(double value) {
        return FastMath.tan(value);
    }
}

