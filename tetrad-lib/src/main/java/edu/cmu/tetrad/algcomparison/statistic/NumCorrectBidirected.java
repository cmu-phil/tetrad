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
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.util.Parameters;

import java.io.Serial;

/**
 * Counts the number of X&lt;-&gt;Y edges for which a latent confounder of X and Y exists.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class NumCorrectBidirected implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Counts the number of bidirectional edges for which a latent confounder of X and Y exists.
     */
    public NumCorrectBidirected() {
    }

    /**
     * Retrieves the abbreviation for the statistic.
     *
     * @return The abbreviation for the statistic.
     */
    @Override
    public String getAbbreviation() {
        return "<-> Correct";
    }

    /**
     * Returns a short one-line description of this statistic. This will be printed at the beginning of the report.
     *
     * @return The description of the statistics as a String.
     */
    @Override
    public String getDescription() {
        return "Number of bidirected edges for which a latent confounder exists";
    }

    /**
     * Returns the number of bidirected edges for which a latent confounder exists.
     *
     * @param trueGraph  The true graph (DAG, CPDAG, PAG_of_the_true_DAG).
     * @param estGraph   The estimated graph (same type).
     * @param dataModel  The data model.
     * @param parameters The parameters
     * @return The number of bidirected edges with a latent confounder.
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters) {
        int tp = 0;

        estGraph = GraphUtils.replaceNodes(estGraph, trueGraph.getNodes());

        for (Edge edge : estGraph.getEdges()) {
            if (Edges.isBidirectedEdge(edge)) {
                if (GraphUtils.isCorrectBidirectedEdge(edge, trueGraph)) {
                    tp++;
                }
            }
        }

        return tp;
    }

    /**
     * Returns the normalized value of the given statistic.
     *
     * @param value The value of the statistic.
     * @return The normalized value.
     */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}

