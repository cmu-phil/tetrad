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

import edu.cmu.tetrad.algcomparison.statistic.utils.AdjacencyConfusion;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.util.Parameters;

import java.io.Serial;

/**
 * A class that implements the PagAdjacencyRecall statistic.
 * <p>
 * This statistic calculates the adjacency recall compared to the true PAG (Partial Ancestral Graph).
 */
public class PagAdjacencyRecall implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs a new instance of the statistic.
     */
    public PagAdjacencyRecall() {

    }

    /**
     * Retrieves the abbreviation for the given statistic.
     *
     * @return The abbreviation as a string.
     */
    @Override
    public String getAbbreviation() {
        return "PAR";
    }

    /**
     * Returns a short one-line description of this statistic. This will be printed at the beginning of the report.
     *
     * @return The description of the statistic.
     */
    @Override
    public String getDescription() {
        return "Adjacency Recall compared to true PAG";
    }

    /**
     * Calculates the adjacency recall compared to the true PAG (Partial Ancestral Graph).
     *
     * @param trueDag
     * @param trueGraph  The true graph (DAG, CPDAG, PAG of the true DAG).
     * @param estGraph   The estimated graph (same type as trueGraph).
     * @param dataModel  The data model.
     * @param parameters The parameters
     * @return The adjacency recall value as a double.
     */
    @Override
    public double getValue(Graph trueDag, Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters) {
        Graph pag = GraphTransforms.dagToPag(trueGraph);

        AdjacencyConfusion adjConfusion = new AdjacencyConfusion(pag, estGraph);
        int adjTp = adjConfusion.getTp();
        int adjFn = adjConfusion.getFn();
        return adjTp / (double) (adjTp + adjFn);
    }

    /**
     * Retrieves the normalized value of this statistic.
     *
     * @param value The value of the statistic.
     * @return The normalized value of the statistic.
     */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}

